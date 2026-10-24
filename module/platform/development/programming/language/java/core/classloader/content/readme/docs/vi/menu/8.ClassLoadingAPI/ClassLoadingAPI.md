<a id="back-to-top"></a>

# API nạp lớp

## Menu
- [ClassLoader.loadClass và ý nghĩa của tham số resolve](#class-loading-api-001)
- [findClass: điểm mở rộng để tìm bytecode](#class-loading-api-002)
- [findLoadedClass và việc tái sử dụng lớp đã được loader ghi nhận](#class-loading-api-003)
- [defineClass: tạo lớp từ byte&#91;&#93; hoặc ByteBuffer](#class-loading-api-004)
- [resolveClass và khác biệt với khởi tạo lớp](#class-loading-api-005)
- [Class.forName(String) và lựa chọn loader mặc định](#class-loading-api-006)
- [Class.forName(name, initialize, loader)](#class-loading-api-007)
- [Class.forName(Module, name) và trường hợp không tìm thấy lớp](#class-loading-api-008)
- [Class.getClassLoader, getParent và đặt tên loader](#class-loading-api-009)
- [Phân biệt nạp lớp, tạo đối tượng và gọi constructor bằng reflection](#class-loading-api-010)
- [Điều khiển assertion theo lớp, package và giá trị mặc định](#class-loading-api-011)

## <a id="class-loading-api-001">ClassLoader.loadClass và ý nghĩa của tham số resolve</a>

<details>
<summary>Xem chi tiết</summary>

# `ClassLoader.loadClass` và ý nghĩa của tham số `resolve`

## 1. Mục tiêu bài học

Trong Java, phương thức `ClassLoader.loadClass` trả về đối tượng `Class<?>` tương ứng với một tên lớp hoặc interface. Tuy nhiên, cần phân biệt rõ:

- **Loading**: JVM tìm hoặc tạo định nghĩa kiểu runtime.
- **Linking**: JVM kiểm tra, chuẩn bị và phân giải các tham chiếu cần thiết của định nghĩa kiểu.
- **Initialization**: JVM chạy mã khởi tạo static theo quy tắc active use.

Đối với `ClassLoader`, có hai overload quan trọng:

~~~java
public Class<?> loadClass(String name) throws ClassNotFoundException;

protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException;
~~~

Overload **một tham số** hoạt động tương đương gọi **`loadClass(name, false)`**. Trong overload hai tham số, **`resolve == true`** yêu cầu bước **`resolveClass(c)`** trong thuật toán mặc định, tức yêu cầu liên kết đối tượng `Class` được tìm thấy; nó **không phải cờ khởi tạo lớp**.

Bài học trả lời những câu hỏi thực tế:

1. `loadClass` tìm lớp qua cơ chế parent delegation như thế nào?
2. Cờ `resolve` tác động ở bước nào?
3. Tại sao `resolve=true` vẫn có thể chưa chạy static block?
4. Khi nào nên dùng `Class.forName` với cờ `initialize` thay vì `loadClass`?
5. Làm sao viết và kiểm tra subclass `ClassLoader` mà không nhầm loading, linking, initialization?

## 2. Chữ ký và hợp đồng của hai overload

| API | Phạm vi truy cập | Hành vi quan trọng |
| --- | --- | --- |
| `loadClass(String name)` | `public` | Nạp/tìm class theo binary name; mặc định gọi overload hai tham số với `resolve=false`. |
| `loadClass(String name, boolean resolve)` | `protected` | Trong cài đặt mặc định, thực hiện delegation và khi `resolve=true` gọi `resolveClass(c)` trên kết quả. |
| `resolveClass(Class<?> c)` | `protected final` | Yêu cầu JVM liên kết class nếu chưa được liên kết; lớp có thể do loader khác định nghĩa. |
| `findLoadedClass(String name)` | `protected final` | Kiểm tra lớp JVM đã ghi nhận loader là initiating loader, giúp tránh làm lại việc tải cùng tên. |
| `findClass(String name)` | `protected` | Điểm mở rộng để loader tự tìm/định nghĩa khi delegation không tìm thấy. |

**Lưu ý về visibility:** code ứng dụng thông thường **không thể trực tiếp gọi** `loader.loadClass("a.B", true)` nếu không nằm trong phạm vi truy cập `protected`. Ví dụ biên dịch được là gọi **`loader.loadClass("a.B")`**, hoặc tạo subclass có wrapper public cho mục đích giảng dạy.

`name` thường là **binary name** như `java.lang.String` hoặc `demo.Widget`. Với member class lồng có tên nhị phân `example.Outer$Inner`, ký hiệu `$` là thành phần của binary name. Không nhầm tên binary này với resource path `example/Outer$Inner.class`.

## 3. Thuật toán mặc định của `loadClass(name, resolve)`

Tài liệu `ClassLoader` mô tả cài đặt mặc định theo hướng parent-first. Lược đồ:

~~~text
loadClass(name, resolve)
          │
          ▼
getClassLoadingLock(name)
          │
          ▼
findLoadedClass(name)
          │
          ├──── class đã được ghi nhận ───────────────┐
          │                                          │
          └─ null                                    │
               │                                     │
               ▼                                     │
       hỏi parent loader (hoặc bootstrap)            │
               │                                     │
         ┌─────┴─────┐                               │
         │ tìm được  │───────────────────────────────┤
         │           │                               │
         └─ không có ┘                               │
               │                                     │
               ▼                                     │
         findClass(name)                              │
               │                                     │
               └────────── Class<?> c ────────────────┘
                                                      │
                                                      ▼
                                         resolve == true?
                                           │        │
                                          Có       Không
                                           │        │
                                           ▼        │
                                      resolveClass(c)
                                           │        │
                                           └────┬───┘
                                                ▼
                                         return Class<?> c
~~~

Mã giả gần với ý nghĩa của JDK, nhưng không phải mã nguồn JDK nguyên văn:

~~~java
// Pseudocode: các bước JVM/parent được rút gọn cho dễ học.
synchronized (getClassLoadingLock(name)) {
    Class<?> c = findLoadedClass(name);
    if (c == null) {
        try {
            c = parentOrBootstrapLoad(name);
        } catch (ClassNotFoundException e) {
            c = findClass(name);
        }
    }
    if (resolve) {
        resolveClass(c);
    }
    return c;
}
~~~

Các điểm cần ghi nhớ:

1. **Cache/tracking trước:** `findLoadedClass(name)` giúp trả về định nghĩa mà JVM đã ghi nhận là được initiate bởi loader tương ứng.
2. **Delegation:** cài đặt mặc định trao cơ hội cho parent trước khi tự tìm.
3. **Fallback:** chỉ khi parent/bootstrap không tìm được mới cần `findClass(name)` ở loader đang xét.
4. **Cờ resolve được xử lý sau khi có `Class`:** chọn `true` không biến việc tìm bytecode thành một thuật toán tra cứu khác.
5. **Kết quả là `Class<?>`, không phải instance** của lớp đó. `loadClass` không gọi constructor để tạo object của lớp được nạp.

Một custom loader có thể override `loadClass` và dùng chính sách khác, ví dụ child-first có điều kiện. Bởi vậy đây là mô hình cho **implementation mặc định** và những subclass kế thừa nó, không là lời hứa về mọi ClassLoader tùy biến trên thế giới.

## 4. `resolve=true` thực sự có nghĩa gì?

Tên tham số dễ gây hiểu nhầm. Trong `loadClass(name, true)`, `resolve` mang nghĩa “**yêu cầu liên kết class được trả về**” trong thuật toán mặc định. Bước này gọi `resolveClass(c)`.

Ở mức JVM, liên kết gồm các hoạt động lớn:

| Giai đoạn linking | Mục đích |
| --- | --- |
| **Verification** | Xác minh định nghĩa/bytecode hợp lệ đối với các quy tắc JVM. |
| **Preparation** | Chuẩn bị lưu trữ cho static fields và thiết lập giá trị ban đầu theo đặc tả (không chạy tùy ý static initializer Java). |
| **Resolution** | Chuyển symbolic references thành các tham chiếu runtime tương ứng khi JVM thực hiện phân giải. |

**Không đồng nhất** tên `resolveClass` với lời cam kết “mọi symbolic reference trong constant pool đã được phân giải đầy đủ ngay bây giờ”. JVM Specification cho phép **resolution theo nhu cầu (lazy)**; nhiều lỗi resolution có thể xuất hiện khi một tham chiếu cụ thể lần đầu được sử dụng. `resolveClass` là yêu cầu linking đối với class theo hợp đồng ClassLoader, không phải API liệt kê hoặc ép phân giải toàn bộ dependencies của class.

Một số hệ quả:

- `resolve=false` **không bảo đảm** class chưa từng được link. JVM có thể đã thực hiện một phần hoặc toàn bộ linking vì nhu cầu khác.
- `resolve=true` **không chạy static initializer** chỉ do cờ đó được chọn.
- `resolve=true` **không bắt buộc nạp mọi class** xuất hiện trong mọi tên phương thức, field hoặc constant pool của lớp ngay lập tức.
- Nếu class đã được liên kết, gọi `resolveClass` không tạo định nghĩa class mới hay chạy mã static một lần nữa.
- Một lỗi xác minh hoặc liên kết có thể lộ ra trong quá trình này, nhưng **thời điểm chính xác của một số lỗi resolution** còn phụ thuộc quy tắc JVM và việc sử dụng thực tế.

### 4.1 Đọc `resolve` là “kết nối class” vẫn chưa đủ

Nói “`resolve=true` = resolve mọi reference” là quá mạnh. Nói “`resolve=true` chỉ đổi Boolean nhưng chẳng ảnh hưởng gì” lại bỏ sót phần liên kết mà loader yêu cầu.

Cách hiểu đúng: **sau khi tìm được `Class<?> c`, overload mặc định có thể yêu cầu JVM link `c` bằng `resolveClass(c)` khi cờ true**. Nó không thay đổi quy tắc khởi tạo và không hứa rằng mọi tham chiếu biểu tượng trong toàn bộ ứng dụng đều được xử lý ngay.

## 5. Khác biệt giữa `resolve` và `initialize`

Đây là điểm quan trọng nhất đối với người học Java ClassLoader.

- **Resolve / link:** liên quan đến tính hợp lệ và trạng thái liên kết kiểu.
- **Initialize:** thực thi mã khởi tạo static của class/interface theo JLS §12.4.

Hai thao tác có thời điểm và trigger khác nhau. Với class:

~~~java
class Target {
    static {
        System.out.println("Target initialized");
    }
}
~~~

Sau `loader.loadClass("Target")`, JVM đã có `Class` nhưng không nhất thiết chạy static block. Kể cả khi có lời gọi `loadClass("Target", true)` thông qua subclass, bản thân cờ này vẫn **không phải** trigger initialization.

Ngược lại, `Class.forName` cung cấp overload với tham số khác:

~~~java
Class<?> a = Class.forName("java.lang.String");
Class<?> b = Class.forName("java.lang.String", false, loader);
Class<?> c = Class.forName("java.lang.String", true, loader);
~~~

**`Class.forName(name, initialize, loader)`** có cờ **`initialize`**: khi `true`, yêu cầu initialization nếu class chưa initialized. Dạng một đối số `Class.forName(name)` mặc định yêu cầu initialization. Đó là khác biệt ngữ nghĩa cơ bản với cờ `resolve` của ClassLoader.

| Tác vụ | Tìm/nạp `Class` | Liên kết | Yêu cầu initialization |
| --- | --- | --- | --- |
| `loader.loadClass(name)` | Có | Khi JVM cần; không chủ động yêu cầu `resolveClass` trong overload mặc định. | **Không** |
| `loadClass(name, false)` trong subclass | Có | Tương tự trên. | **Không** |
| `loadClass(name, true)` trong subclass kế thừa thuật toán mặc định | Có | Gọi `resolveClass` trên Class được trả về. | **Không** |
| `Class.forName(name, false, loader)` | Có | API yêu cầu locate/load/link. | **Không** |
| `Class.forName(name, true, loader)` | Có | Có | **Có** |
| `new Target()` | Có kiểu đã sẵn sàng cho sử dụng | JVM làm linking cần thiết | **Có**, nếu chưa init |
| Đọc `Target.nonConstantStatic` | Có kiểu đã sẵn sàng cho sử dụng | JVM làm linking cần thiết | **Có**, nếu field do Target khai báo và chưa init |

**Đọc constant variables:** ví dụ `static final int MAX = 10` với compile-time constant expression là trường hợp riêng; phép đọc hằng có thể được compiler in-line và **không tự** gây initialization. Điều này không liên quan trực tiếp đến tham số resolve nhưng giúp tránh suy luận “cứ dùng tên class là init”.

## 6. Ví dụ chạy được số 1: so sánh `resolve=false` và `resolve=true`

Lưu file **ResolveFlagDemo.java**:

~~~java
public class ResolveFlagDemo {
    static class Target {
        static {
            System.out.println("Target: static block");
        }

        static int value = compute();

        static int compute() {
            System.out.println("Target: compute");
            return 42;
        }
    }

    static class InspectingLoader extends ClassLoader {
        InspectingLoader(ClassLoader parent) {
            super(parent);
        }

        public Class<?> loadWithResolve(String name, boolean resolve)
                throws ClassNotFoundException {
            System.out.println("request resolve = " + resolve);
            return super.loadClass(name, resolve);
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            System.out.println("child findClass called");
            throw new ClassNotFoundException(name);
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader app = ResolveFlagDemo.class.getClassLoader();
        InspectingLoader child = new InspectingLoader(app);

        String targetName = Target.class.getName();

        Class<?> first = child.loadWithResolve(targetName, false);
        System.out.println("false: same Class = " + (first == Target.class));

        Class<?> second = child.loadWithResolve(targetName, true);
        System.out.println("true: same Class = " + (second == Target.class));

        System.out.println("before active use");
        System.out.println("value = " + Target.value);
    }
}
~~~

Chạy trên JDK 17+:

~~~bash
javac ResolveFlagDemo.java
java ResolveFlagDemo
~~~

Stdout:

~~~text
request resolve = false
false: same Class = true
request resolve = true
true: same Class = true
before active use
Target: static block
Target: compute
value = 42
~~~

### 6.1 Giải thích từng dòng

1. `Target.class.getName()` lấy binary name của nested class, không chủ động initialization Target.
2. Cả hai lời gọi `child.loadWithResolve(...)` dùng **`super.loadClass(name, resolve)`**. Implementation mặc định tìm lớp qua application parent loader. Child không phải defining loader của Target.
3. Lần đầu `resolve=false` trả về `Class`; lần sau `resolve=true` yêu cầu `resolveClass` trên cùng Class nếu cần. Cả hai so sánh `==` với `Target.class` đều đúng.
4. Dòng **child findClass called** không xuất hiện vì parent tìm được Target. Điều này cho thấy cờ `resolve` không làm bỏ qua delegation.
5. Sau hai lời gọi loadClass vẫn chưa có dòng **Target: static block**. Initialization chỉ bắt đầu khi chương trình lần đầu đọc trường runtime `Target.value`.
6. Theo thứ tự khai báo, Target in static block rồi gọi `compute()` để gán 42 cho value, sau đó mới in dòng cuối.

**Giới hạn của ví dụ:** stdout chứng minh rằng cả `resolve=false` và `resolve=true` **không chủ động initialization**. Nó không có cách trực tiếp in thời điểm verification/resolution của từng symbolic reference. Vì thế không nên dùng sự vắng mặt của log để tuyên bố chắc chắn JVM chưa linking lúc `resolve=false`.

### 6.2 Tại sao phải viết wrapper `loadWithResolve`?

Overload hai tham số được khai báo **`protected`**. Code bên ngoài không thể viết tùy tiện:

~~~java
// KHÔNG biên dịch trong mã ứng dụng thông thường:
// new ClassLoader().loadClass("demo.Target", true);
~~~

Trong ví dụ runnable, subclass `InspectingLoader` truy cập method protected thông qua `super.loadClass(name, resolve)`, và cung cấp **public wrapper** chỉ để minh họa ý nghĩa cờ. Phương thức public `loadClass(name)` của ClassLoader vẫn là API thông thường mà code bên ngoài sử dụng.

## 7. Ví dụ chạy được số 2: parent không tìm thấy, `findClass` mới được gọi

Cờ `resolve` không thay đổi chuỗi tìm class, nhưng để thấy toàn bộ hành trình bạn có thể thử một loader đọc file `.class` riêng.

Tạo cấu trúc:

~~~text
project/
├── DirectoryLoaderDemo.java
└── src/
    └── plugin/
        └── Greeter.java
~~~

**src/plugin/Greeter.java**:

~~~java
package plugin;

public class Greeter {
    static {
        System.out.println("Greeter initialized");
    }

    public String message() {
        return "Hello";
    }
}
~~~

**DirectoryLoaderDemo.java**:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DirectoryLoaderDemo {
    static final class DirectoryLoader extends ClassLoader {
        private final Path root;

        DirectoryLoader(Path root) {
            super(ClassLoader.getPlatformClassLoader());
            this.root = root.toAbsolutePath().normalize();
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            Path file = root.resolve(
                    name.replace('.', '/') + ".class"
            ).normalize();

            if (!file.startsWith(root)) {
                throw new ClassNotFoundException(name);
            }

            byte[] bytes;
            try {
                bytes = Files.readAllBytes(file);
            } catch (IOException ex) {
                throw new ClassNotFoundException(name, ex);
            }

            System.out.println("findClass: " + name);
            return defineClass(name, bytes, 0, bytes.length);
        }

        public Class<?> loadWithResolve(String name, boolean resolve)
                throws ClassNotFoundException {
            return super.loadClass(name, resolve);
        }
    }

    public static void main(String[] args) throws Exception {
        DirectoryLoader loader =
                new DirectoryLoader(Path.of("plugin-classes"));

        Class<?> loaded = loader.loadWithResolve("plugin.Greeter", false);
        System.out.println("defined by child = "
                + (loaded.getClassLoader() == loader));

        Class<?> linked = loader.loadWithResolve("plugin.Greeter", true);
        System.out.println("same Class = " + (loaded == linked));

        System.out.println("before instance");
        Object object = linked.getDeclaredConstructor().newInstance();
        String message = (String) linked.getMethod("message")
                .invoke(object);
        System.out.println("message = " + message);
    }
}
~~~

Chạy từ thư mục **project**:

~~~bash
javac -d plugin-classes src/plugin/Greeter.java
javac DirectoryLoaderDemo.java
java -cp . DirectoryLoaderDemo
~~~

Stdout:

~~~text
findClass: plugin.Greeter
defined by child = true
same Class = true
before instance
Greeter initialized
message = Hello
~~~

### 7.1 Tại sao lần thứ nhất đi vào `findClass`?

`DirectoryLoader` có platform loader làm parent. Trong lệnh chạy này, `plugin-classes` **không nằm trong application classpath**, nên platform parent không tìm được `plugin.Greeter`. Loader riêng phải đọc file `plugin-classes/plugin/Greeter.class`, gọi `defineClass`, rồi trả Class.

### 7.2 Tại sao lần thứ hai không gọi `findClass`?

JVM đã có định nghĩa `plugin.Greeter` gắn với loader. Lời gọi `loadWithResolve(..., true)` trả về cùng đối tượng `Class`; phần `findLoadedClass` đã tìm được nên không cần đọc lại file. Sau khi có kết quả, cờ true khiến implementation mặc định yêu cầu `resolveClass`.

### 7.3 Khi nào static block chạy?

**Greeter initialized** chỉ xuất hiện khi reflection tạo instance bằng `getDeclaredConstructor().newInstance()`. Đây là active use khiến class được initialized. `resolve=false` và `resolve=true` ở hai lời gọi trước đều không tự thực hiện bước này.

### 7.4 Lưu ý đối với custom loader thực tế

Code này là ví dụ dành cho thư mục đã biết. Hệ thống nạp class từ dữ liệu không tin cậy cần kiểm soát nguồn bytes, tên hợp lệ, kích thước file, đường dẫn symlink, các quyền truy cập và ràng buộc bảo mật. Tránh mở rộng trực tiếp đoạn minh họa thành cơ chế nạp plugin từ nguồn tùy ý mà không có kiểm soát.

**Đối chiếu quan trọng:** nếu bạn thêm `plugin-classes` vào `-cp` hoặc dùng một parent loader cũng thấy `plugin.Greeter`, parent-first có thể trả class do parent định nghĩa. Khi đó **không** có dòng `findClass: plugin.Greeter`; stdout kỳ vọng ở trên chỉ đúng với chính các lệnh đã cho.

## 8. Những điều `resolve=true` KHÔNG bảo đảm

Đây là phần nên đọc trước khi cố sử dụng cờ resolve làm “kiểm tra trước mọi lỗi”:

| Kết luận sai | Thực tế |
| --- | --- |
| “`resolve=true` chạy mọi `static {}`.” | Initialization là bước khác, với trigger riêng. |
| “`resolve=false` có nghĩa class chưa liên kết.” | JVM có thể đã liên kết vì sử dụng trước đó hoặc quyết định implementation. |
| “`resolve=true` tải mọi lớp dependency.” | Không có cam kết toàn bộ symbolic references được eager resolve ngay. |
| “`resolve=true` luôn phát hiện mọi NoSuchMethodError trước khi chạy.” | Resolution có thể lazy; lỗi có thể lộ ra khi method/field được sử dụng. |
| “`resolve=true` tạo class object mới.” | Cùng định nghĩa runtime được trả về; linking không phải một lượt define mới. |
| “`resolve=true` làm child loader ưu tiên trước parent.” | Cờ chỉ liên quan bước sau khi đã tìm được Class. |
| “Nếu `loadClass` trả về Class thì constructor đã chạy.” | Chỉ có metadata runtime của kiểu; constructor chạy khi thực sự tạo instance. |

Một class có thể tham chiếu tới các kiểu hoặc member khác chưa được giải quyết hoàn toàn tại thời điểm `loadClass` trả về. JVM bảo đảm những ràng buộc và lỗi theo điểm sử dụng do đặc tả quy định; đó là lý do không thể dùng một lời gọi loadClass để “chứng nhận” mọi nhánh thực thi của toàn bộ dependency graph.

## 9. ClassLoader `resolve` và việc phân giải trong JVM là hai lớp khái niệm

Tên `resolveClass` do API ClassLoader cung cấp dễ dẫn tới phát biểu thiếu chính xác:

~~~text
resolveClass(c) == eagerly resolve all constant-pool entries
~~~

Phát biểu này **không đúng**.

Class-file constant pool có nhiều loại symbolic reference; nhiều mục chỉ được sử dụng khi bytecode tương ứng chạy. JVM có thể lựa chọn **lazy** hoặc **eager** resolution theo quy tắc và thời điểm lỗi cần quan sát theo JVMS.

Khi một symbolic reference cụ thể được resolution, JVM có thể tìm method/field/class và kiểm tra access hoặc loại kiểu. Các lỗi như `NoSuchMethodError`, `NoSuchFieldError` hay `IllegalAccessError` có thể lộ ra tại một điểm runtime; chúng không nhất thiết xuất hiện ngay ở lệnh `loadClass(name, true)`.

Do đó, hãy dùng tên “**liên kết class**” để diễn đạt ý nghĩa tham số resolve của ClassLoader, đồng thời gọi “**symbolic-reference resolution**” khi muốn bàn riêng từng tham chiếu bên trong constant pool.

## 10. Quan hệ với defining loader và initiating loader

Giả sử object loader A nhận yêu cầu `A.loadClass("demo.Shared")`, nhưng parent B đã định nghĩa `demo.Shared`:

~~~text
caller
  │
  └─ A.loadClass(name, resolve)
         │
         ├─ parent B.loadClass(name)
         │          │
         │          └─ Class<?> do B định nghĩa
         │
         ├─ nếu resolve=true, yêu cầu resolveClass(c)
         │
         └─ trả về Class<?> của B
~~~

`Class.getClassLoader()` của kết quả trả về **B**, không phải A. B là **defining loader**, còn A có thể là **initiating loader** theo mô hình JVM loading. Cờ `resolve` không chuyển quyền sở hữu định nghĩa từ B sang A.

Nhiều child loader khác nhau có thể cùng delegate tới một parent và nhận **cùng Class**. Ngược lại, nếu hai loader khác nhau thực sự `defineClass` cho cùng binary name, đó là hai kiểu runtime khác nhau. Danh tính class thông thường phụ thuộc cặp **(binary name, defining loader)**.

Điểm thực hành: trong log bạn thấy `myLoader.loadClass("x.Y")` thành công, hãy kiểm tra **`returnedClass.getClassLoader()`** trước khi kết luận loader nào đã thực sự định nghĩa `x.Y`.

## 11. Đồng bộ và khả năng chạy đồng thời

Implementation mặc định của `ClassLoader.loadClass(name, boolean)` đồng bộ theo **`getClassLoadingLock(name)`**:

- Với loader không được đăng ký **parallel capable**, lock có thể là chính đối tượng ClassLoader.
- Với loader parallel capable, lock có thể riêng theo từng binary name, cho phép load các class khác nhau song song.
- Trạng thái đã load được kiểm tra trong critical section để tránh cố define trùng trong tình huống concurrent loading.

Nếu tùy biến toàn bộ `loadClass`, bạn phải hiểu quan hệ giữa `getClassLoadingLock`, `findLoadedClass`, parent delegation và defineClass. Một implementation không an toàn có thể gây đua giữa hai thread, `LinkageError` hoặc deadlock giữa các loader.

**Với bài toán chỉ muốn thêm nguồn bytecode, thường nên override `findClass` và giữ thuật toán `loadClass` mặc định**, bởi nó đã xử lý delegation, khóa và việc dùng lại Class.

## 12. Xử lý lỗi: lỗi nào thuộc lúc nào?

| Lỗi hoặc hiện tượng | Gợi ý nguyên nhân | Đọc thế nào trong bối cảnh `loadClass` |
| --- | --- | --- |
| `ClassNotFoundException` | Loader không tìm/không nạp được class theo tên yêu cầu. | Checked exception của API load; kiểm tra tên, nguồn bytes, parent và custom findClass. |
| `ClassFormatError` | Binary representation không hợp lệ. | Có thể xuất hiện khi define class, không phải trạng thái “không tìm thấy file”. |
| `UnsupportedClassVersionError` | Class-file version không được runtime hỗ trợ. | Kiểm tra JDK biên dịch/JDK chạy và class-file major version. |
| `VerifyError` | Vi phạm các quy tắc verifier. | Liên quan linking; đừng gọi nó là lỗi static initializer. |
| `NoClassDefFoundError` | Thiếu định nghĩa cần thiết ở runtime hoặc class từng bị initialization thất bại. | Có thể khác với `ClassNotFoundException`; xem cause và thời điểm. |
| `NoSuchMethodError` / `NoSuchFieldError` | Tham chiếu nhị phân không còn tương thích. | Có thể lộ ra khi resolution/use; `resolve=true` không bảo đảm bắt mọi lỗi sớm. |
| `ExceptionInInitializerError` | Exception phát sinh trong static initialization. | Không phải lỗi mà cờ `resolve` chủ động tạo; điều tra cause của mã static. |
| `LinkageError` do define trùng | Loader cố định nghĩa một lớp cùng tên thêm lần nữa. | Kiểm tra logic tải/cache/thread safety và nguồn class. |

Với custom loader, đừng bắt mọi `Throwable` rồi đổi thành `ClassNotFoundException`; như vậy che mất lỗi format, verify, linkage hoặc bug của loader. Nếu **không tìm thấy bytes**, hãy ném **`ClassNotFoundException`**, lý tưởng kèm cause khi I/O lỗi. Nếu bytes đã được tìm nhưng invalid, để lỗi thích hợp của JVM bộc lộ.

## 13. Chẩn đoán sự cố theo từng bước

Khi gặp lỗi liên quan `loadClass`, hãy thực hiện các kiểm tra theo thứ tự:

1. **Tên đầu vào:** giá trị `name` đúng binary name chưa? Với nested type, kiểm tra `$`.
2. **Nguồn class:** bytes được tìm ở classpath/module path/JAR/thư mục loader mong đợi không?
3. **Loader thực tế:** object nào nhận yêu cầu, parent của nó là ai, có override `loadClass` không?
4. **Defining loader:** `clazz.getClassLoader()` trả loader nào sau khi thành công? Class có bị parent định nghĩa trước không?
5. **Lúc nào xảy ra exception:** trong load/define, linking, hay lần active use gây initialization?
6. **Có sự khác biệt `resolve=false` và `resolve=true` không?** Nếu có, quan sát lỗi và nguyên nhân thực; đừng đồng nhất với khởi tạo.
7. **Có loader khác định nghĩa cùng binary name không?** So sánh chính đối tượng `Class`, không chỉ so `getName()`.
8. **Có thay đổi classpath/JDK không?** Kiểm tra đúng phiên bản runtime khi tái hiện.

Có thể dùng:

~~~bash
javap -verbose ResolveFlagDemo
java -Xlog:class+load=info ResolveFlagDemo
java -Xlog:help
~~~

`javap` đọc class file/bytecode đã biên dịch. Unified Logging giúp quan sát loading trên các JVM hỗ trợ (như HotSpot hiện đại). Các log này **không trực tiếp chứng minh** mọi symbolic reference đã resolution hoặc static initializer đã chạy; hãy dùng những dòng in riêng trong ví dụ để xác định initialization.

## 14. Những lỗi sử dụng API phổ biến

### 14.1 Gọi overload protected như thể nó là public

Câu lệnh sau không biên dịch trong code ứng dụng thông thường:

~~~java
// Ví dụ KHÔNG hợp lệ từ một class không phải subclass:
ClassLoader.getSystemClassLoader().loadClass("java.lang.String", true);
~~~

Nguyên nhân là `loadClass(String, boolean)` là **protected**. Dùng API public `loadClass(name)`, hoặc wrapper trong subclass như ví dụ khi cần kiểm tra cờ.

### 14.2 Tự override loadClass chỉ để đọc file

Nếu nhu cầu đơn thuần là “tìm bytecode ở nơi khác”, override **`findClass`** thường đủ. Override **`loadClass`** toàn bộ có thể làm sai thứ tự parent delegation, bỏ qua `findLoadedClass`, dẫn tới define trùng hoặc phá quy tắc class identity.

### 14.3 Coi resolve=true là cơ chế khởi tạo sớm

Muốn yêu cầu initialization theo API, cân nhắc **`Class.forName(name, true, loader)`**, biết rằng static initializer có thể gây side effects và lỗi. Dùng `loadClass(name, true)` (trong subclass) không có ý nghĩa đó.

### 14.4 Coi resolve=false là cam kết “Class chưa được kiểm tra”

Java không cho caller dùng flag false để buộc JVM tránh mọi việc verification/linking. Class đã được trả về có thể được VM xử lý theo chiến lược eager/lazy và theo yêu cầu sử dụng.

### 14.5 Coi mọi error khi chạy là phát sinh tại loadClass

Một thư viện có thể load thành công nhưng thiếu method được dùng ở nhánh logic sau; symbolic-reference resolution lúc đó mới báo lỗi. Hãy xác định stack trace và tình huống tạo lỗi, tránh dựa vào thời điểm người dùng nhìn thấy lỗi.

## 15. Tự kiểm tra

**Câu 1.** `ClassLoader.loadClass("pkg.Type")` tương đương overload hai tham số với cờ nào?

**Đáp án:** `resolve=false`, theo implementation của ClassLoader.

**Câu 2.** `resolve=true` có đảm bảo chạy `static {}` của Type không?

**Đáp án:** Không. Nó yêu cầu linking qua `resolveClass` trong thuật toán mặc định; initialization là bước riêng.

**Câu 3.** Vì sao code bình thường không gọi trực tiếp `loader.loadClass(name, true)` được?

**Đáp án:** Overload này là **protected**, chỉ truy cập được theo quy tắc `protected` của Java.

**Câu 4.** Nếu class được parent loader định nghĩa, cờ `resolve=true` có làm child loader định nghĩa lại class không?

**Đáp án:** Không. Child vẫn trả Class của parent; linking không chuyển defining loader.

**Câu 5.** Có thể có `NoSuchMethodError` sau khi `loadClass(name, true)` đã thành công không?

**Đáp án:** Có. Không có cam kết toàn bộ symbolic references được eager resolution ngay; lỗi có thể bộc lộ khi reference cụ thể được sử dụng.

**Câu 6.** Khi một custom loader chỉ cần nạp bytes từ thư mục riêng, thường nên override method nào?

**Đáp án:** `findClass(name)` để tìm bytes và `defineClass`; giữ lại `loadClass` mặc định nếu chính sách delegation đáp ứng yêu cầu.

**Câu 7.** `Class.forName(name, false, loader)` và `Class.forName(name, true, loader)` khác nhau quan trọng ở đâu?

**Đáp án:** Tham số **initialize** điều khiển yêu cầu initialization của class, không phải cờ resolve của ClassLoader.

**Câu 8.** Class loader đã trả Class, liệu constructor instance của Type chắc chắn được gọi chưa?

**Đáp án:** Chưa. `loadClass` trả class metadata; muốn tạo instance phải dùng thao tác tạo đối tượng thích hợp.

**Câu 9.** `resolve=false` có đảm bảo verifier chưa chạy không?

**Đáp án:** Không. JVM có thể thực hiện verification/linking theo các quy tắc và nhu cầu hợp lệ khác; cờ false không phải một cơ chế trì hoãn tuyệt đối.

## 16. Tóm tắt

~~~text
loadClass(name, false)
  └─ tìm/trả Class<?> theo delegation, không tự yêu cầu resolveClass
       └─ KHÔNG có nghĩa sẽ initialize class

loadClass(name, true) (protected)
  └─ tìm/trả Class<?> rồi yêu cầu resolveClass(Class<?>)
       └─ link class theo JVM
       └─ KHÔNG có nghĩa toàn bộ constant pool eager resolution
       └─ KHÔNG có nghĩa sẽ initialize class

Class.forName(name, true, loader)
  └─ tìm/load/link và yêu cầu initialize class
       └─ có thể chạy mã static và gây side effects
~~~

Hãy nhớ ba trục độc lập: **tìm Class**, **link Class**, **initialize Class**. Tham số `resolve` của `ClassLoader.loadClass` liên quan đến bước **link**, không điều khiển việc thực thi `static {}`. Để biết một Class thực sự do loader nào định nghĩa, xem `Class.getClassLoader()`; để biết static initializer đã chạy hay chưa, quan sát đúng trigger initialization thay vì suy đoán từ tham số resolve.

### Tài liệu chuẩn

- Java SE 21 **ClassLoader API**: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 21 **Class.forName API**: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#forName(java.lang.String,boolean,java.lang.ClassLoader)
- **JVM Specification §5.4 – Linking**: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.4
- **JVM Specification §5.5 – Initialization**: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.5
- **Java Language Specification §12.2–12.4**: https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-api-002">findClass: điểm mở rộng để tìm bytecode</a>

<details>
<summary>Xem chi tiết</summary>

# findClass: điểm mở rộng để tìm bytecode trong Java ClassLoader

## 1. Vì sao Java cần `findClass`?

Khi JVM cần một lớp Java, một `ClassLoader` có thể được yêu cầu nạp lớp theo **tên nhị phân** (*binary name*), chẳng hạn `com.example.plugin.PaymentPlugin`. Nhưng bytecode của lớp có thể nằm trong một thư mục riêng, JAR plugin, kho lưu trữ, một nguồn do ứng dụng quản lý hoặc một cơ chế nạp chuyên biệt. Java cung cấp phương thức **`findClass(String name)`** để lớp con của `ClassLoader` tự định nghĩa cách **tìm và tạo định nghĩa lớp từ bytecode** khi thuật toán nạp chọn sử dụng nguồn cục bộ của loader.

Ba phương thức có vai trò khác nhau:

- **`loadClass(name)`**: cửa vào thông thường để yêu cầu một lớp; triển khai mặc định xử lý lớp đã được nạp, ủy quyền cho parent, rồi mới nhờ `findClass`.
- **`findClass(name)`**: *extension point* để tìm định nghĩa ở nguồn dữ liệu mà loader hiện tại quản lý. Lớp con thường override phương thức này.
- **`defineClass(name, bytes, ...)`**: chuyển một chuỗi byte chứa định nghĩa lớp hợp lệ thành đối tượng `Class<?>` runtime gắn với **defining loader hiện tại**.

Vì vậy **`findClass` không phải là tên gọi khác của toàn bộ thao tác load class**. Phương thức này thường được gọi khi parent delegation không tìm ra lớp. Nó cũng không đồng nghĩa với việc chỉ trả về file `.class`: nếu tự triển khai `findClass` để định nghĩa một class, bạn thường phải đọc bytecode và gọi `defineClass`.

### Câu hỏi dẫn đường

Giả sử ứng dụng viết:

```java
Class<?> c = pluginLoader.loadClass("plugins.Report");
```

Lúc này có ít nhất hai kịch bản:

1. **Parent có `plugins.Report`:** parent trả về đối tượng `Class`; loader con có thể **không hề gọi** `findClass`. Defining loader là parent.
2. **Parent không có, plugin loader có bytecode riêng:** `loadClass` gọi `findClass` tại loader con; `findClass` đọc bytecode và dùng `defineClass`. Defining loader là plugin loader.

Hai kịch bản đều là kết quả bình thường của parent-first delegation. Dòng gọi `loadClass` không tự tiết lộ nơi định nghĩa class.

## 2. Chữ ký API, mặc định và quyền override

Trong `java.lang.ClassLoader`:

```java
protected Class<?> findClass(String name)
        throws ClassNotFoundException
```

Đây là chữ ký API dùng để minh họa, không phải một chương trình chạy độc lập.

- **`protected`**: lớp con có thể override để triển khai nguồn bytecode. Code bất kỳ ở ngoài class/quan hệ truy cập thích hợp không gọi trực tiếp `findClass` của loader.
- **Trả về `Class<?>`**: trả về định nghĩa runtime mà loader tìm được. Phương thức không trả về `byte[]` hoặc `Path`.
- **`throws ClassNotFoundException`**: khi nguồn mà loader chịu trách nhiệm không có lớp được yêu cầu, thường ném ngoại lệ này. `ClassNotFoundException` là checked exception.
- **Cài đặt mặc định của `ClassLoader.findClass(String)`**: ném `ClassNotFoundException`. Vì vậy, một lớp con chỉ gọi `super(parent)` nhưng không override `findClass` sẽ không tự tìm class ở thư mục tùy ý.
- **Phương thức không nhận đối số parent**: việc hỏi cha thuộc luồng logic của `loadClass`; `findClass` có nhiệm vụ cục bộ sau khi được chọn.

Mẫu tối giản:

```java
public final class BytecodeLoader extends ClassLoader {
    public BytecodeLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    protected Class<?> findClass(String binaryName)
            throws ClassNotFoundException {
        byte[] bytes = readOwnBytecode(binaryName);
        return defineClass(binaryName, bytes, 0, bytes.length);
    }

    private byte[] readOwnBytecode(String binaryName)
            throws ClassNotFoundException {
        throw new ClassNotFoundException(binaryName);
    }
}
```

Đây là **phác thảo cấu trúc**. Phương thức `readOwnBytecode` cố ý chưa có triển khai nguồn dữ liệu, nên không phải loader chức năng hoàn chỉnh. Ở phần thực hành bên dưới sẽ có chương trình Java 17 hoàn chỉnh, biên dịch và chạy được.

**Quan trọng:** nếu chỉ cần nạp class từ các URL của JAR/thư mục, `URLClassLoader` đã cung cấp cơ chế phù hợp cho nhiều tình huống. Tự viết `findClass` khi cần cách đọc bytecode khác, hoặc cần điều khiển rõ nguồn và vòng đời.

## 3. findClass nằm ở đâu trong thuật toán parent-first?

Cách thực thi mặc định của `ClassLoader.loadClass(String, boolean)` có thể được diễn giải như sau:

1. Lấy khóa nạp lớp phù hợp cho tên được yêu cầu (cơ chế `getClassLoadingLock(name)`).
2. Gọi `findLoadedClass(name)` để xem loader đã được ghi nhận là initiating loader của lớp ấy chưa.
3. Nếu chưa tìm thấy, thử hỏi **parent**. Khi không có parent Java-level, có cơ chế tra lớp ở bootstrap loader.
4. Nếu đường parent không tìm được lớp và ném `ClassNotFoundException`, gọi **`findClass(name)`** của loader hiện tại.
5. Nếu yêu cầu có tham số `resolve = true`, JVM có thể thực hiện `resolveClass(c)`; trả đối tượng `Class<?>`.

Sơ đồ:

```text
Application / JVM yêu cầu: child.loadClass("plugin.Report")
                    |
                    v
        findLoadedClass("plugin.Report")
                    |
             có? --+--> trả về Class đã ghi nhận
                    |
                 chưa có
                    |
                    v
            parent.loadClass(...)
                    |
            thành công? --yes--> trả Class do parent/ancestor định nghĩa
                    |
             ClassNotFoundException
                    |
                    v
            child.findClass(name)
                    |
          tìm bytecode -> defineClass(...)
                    |
                    v
                trả Class
```

Cần tránh hai hiểu lầm:

- **`findClass` không phải lúc nào cũng chạy.** Nếu parent trả về lớp, đường local search không cần được thực hiện.
- **`findClass` không nên tự gọi `loadClass(name)` với chính tên đang xử lý** để “thử thêm lần nữa”; dễ dẫn tới đệ quy không kết thúc hoặc vòng tìm class. Nếu cần nạp lớp khác, chỉ dùng API phù hợp với quan hệ phụ thuộc và delegation cụ thể.

Sơ đồ mô tả **mặc định**, không phải luật bắt buộc cho mọi custom loader. Framework có thể override `loadClass` để ưu tiên child-first theo package. Khi đó điểm gọi `findClass` thay đổi theo thuật toán framework.

## 4. Từ tên lớp sang bytecode: đường dẫn và nguồn dữ liệu

Nếu binary name là `com.acme.plugin.Report`, file class tương ứng trong một thư mục lớp thông thường có đường dẫn tương đối:

```text
com/acme/plugin/Report.class
```

Một cách lấy đường dẫn trên nền tảng có `Path`:

```java
Path file = classesRoot.resolve(
        binaryName.replace('.', '/') + ".class");
```

Đây là **đoạn minh họa**; nó giả định có sẵn biến `classesRoot` và `binaryName`, không phải chương trình độc lập.

Những điểm cần nhớ:

- `binaryName` khác với file path: tham số phương thức là `com.acme.plugin.Report`, **không** phải `com/acme/plugin/Report.class`.
- Class lồng nhau có binary name chứa `$`, ví dụ `Outer$Inner`. Không tự đổi `$` thành dấu `.`.
- Không phải mọi class đều tương ứng một file dễ tìm trên disk: class trong JAR, module image, generated bytecode, hidden classes và array classes có cơ chế khác.
- Đường dẫn phải được giới hạn trong vùng root được phép. Nếu nguồn tên/path đến từ cấu hình hoặc người dùng, cần validate tên nhị phân, chuẩn hóa đường dẫn và ngăn thoát khỏi root.
- `findClass` cần đóng tài nguyên khi đọc dữ liệu; những lỗi I/O thích hợp có thể được bọc vào `ClassNotFoundException` để caller thấy tên lớp và nguyên nhân gốc.

Đối với JAR thực tế, nên cân nhắc `URLClassLoader`, `JarFile` hoặc thư viện được quản lý thay vì tự giải nén thủ công mà bỏ qua manifest, multi-release JAR, package sealing hay tài nguyên đi kèm.

## 5. Thực nghiệm đầy đủ Java 17+: tự viết DirectoryClassLoader

Chương trình sau cho thấy **toàn bộ chuỗi** từ yêu cầu nạp tới bytecode:

1. Tạo tạm source code của một plugin.
2. Dùng compiler có sẵn trong JDK để biên dịch class vào thư mục tạm.
3. Khởi tạo một class loader có `findClass` đọc file `.class` trong thư mục đó.
4. Gọi `loadClass` hai lần cùng tên để quan sát cùng một `Class`.
5. So sánh loader được gọi với defining loader; kiểm tra trường hợp lớp có sẵn ở parent.
6. Dùng reflection tạo instance và gọi phương thức, không cần link trước plugin class vào project.

Tạo file `DirectoryClassLoaderDemo.java`:

```java
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public class DirectoryClassLoaderDemo {

    static final class DirectoryLoader extends ClassLoader {
        private final Path root;
        int localSearches;

        DirectoryLoader(Path root, ClassLoader parent) {
            super(parent);
            this.root = root.toAbsolutePath().normalize();
        }

        @Override
        protected Class<?> findClass(String binaryName)
                throws ClassNotFoundException {
            localSearches++;

            Path file = root.resolve(
                    binaryName.replace('.', '/') + ".class").normalize();
            if (!file.startsWith(root)) {
                throw new ClassNotFoundException(binaryName);
            }

            byte[] bytes;
            try {
                bytes = Files.readAllBytes(file);
            } catch (IOException e) {
                throw new ClassNotFoundException(binaryName, e);
            }

            return defineClass(binaryName, bytes, 0, bytes.length);
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("find-class-demo-");
        Path source = root.resolve("plugin/Greeter.java");
        Path bytecode = root.resolve("plugin/Greeter.class");

        try {
            Files.createDirectories(source.getParent());
            Files.writeString(source, """
                    package plugin;
                    public class Greeter {
                        public String message() {
                            return "Hello from plugin";
                        }
                    }
                    """);

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new IllegalStateException("Run with a JDK");
            }
            int result = compiler.run(null, null, null,
                    "-d", root.toString(), source.toString());
            if (result != 0) {
                throw new IllegalStateException("Compilation failed");
            }

            DirectoryLoader child = new DirectoryLoader(
                    root, DirectoryClassLoaderDemo.class.getClassLoader());

            Class<?> first = child.loadClass("plugin.Greeter");
            Class<?> again = child.loadClass("plugin.Greeter");

            System.out.println("definedByChild="
                    + (first.getClassLoader() == child));
            System.out.println("sameClass=" + (first == again));
            System.out.println("localSearches=" + child.localSearches);

            Object instance = first.getDeclaredConstructor().newInstance();
            Method method = first.getMethod("message");
            System.out.println("message=" + method.invoke(instance));

            Class<?> fromParent = child.loadClass(
                    DirectoryClassLoaderDemo.class.getName());
            System.out.println("hostDefinedByParent="
                    + (fromParent.getClassLoader()
                       == DirectoryClassLoaderDemo.class.getClassLoader()));
            System.out.println("searchesAfterParent="
                    + child.localSearches);

            Class<?> stringType = child.loadClass("java.lang.String");
            System.out.println("stringFromBootstrap="
                    + (stringType.getClassLoader() == null));

            try {
                child.loadClass("plugin.Unknown");
            } catch (ClassNotFoundException e) {
                System.out.println("missing=ClassNotFoundException");
            }
            System.out.println("searchesAfterMissing="
                    + child.localSearches);
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(bytecode);
            Files.deleteIfExists(root.resolve("plugin"));
            Files.deleteIfExists(root);
        }
    }
}
```

Biên dịch/chạy bằng **JDK 17 trở lên**:

```bash
javac DirectoryClassLoaderDemo.java
java DirectoryClassLoaderDemo
```

Stdout dự kiến nếu không có class trùng tên trong classpath, compiler tạo thành công bytecode và không có lỗi môi trường:

```text
definedByChild=true
sameClass=true
localSearches=1
message=Hello from plugin
hostDefinedByParent=true
searchesAfterParent=1
stringFromBootstrap=true
missing=ClassNotFoundException
searchesAfterMissing=2
```

### Giải thích kết quả chi tiết

**`definedByChild=true`:** lớp `plugin.Greeter` chỉ được tạo trong thư mục tạm. Parent của child không được cấu hình tìm thư mục đó; nó thất bại, nên child chuyển sang `findClass`, đọc bytecode và gọi `defineClass`. Vì thế defining loader của `Greeter` là `child`.

**`sameClass=true` và `localSearches=1`:** lần gọi `loadClass` đầu tiên tạo định nghĩa lớp và ghi nhận trạng thái nạp. Lần thứ hai với cùng loader và cùng tên được thỏa mãn bằng class đã được ghi nhận (mặc định qua `findLoadedClass`); không đọc lại file, không gọi `defineClass` lần nữa.

**`message=Hello from plugin`:** code ứng dụng không import `plugin.Greeter`. Reflection trên đối tượng `Class<?>` mà loader trả về vẫn tạo instance và gọi được phương thức public.

**`hostDefinedByParent=true`, `searchesAfterParent=1`:** lần yêu cầu nạp `DirectoryClassLoaderDemo` được parent xử lý. Child không cần gọi `findClass`, nên bộ đếm không đổi. Cùng một API `loadClass` có thể trả class do loader khác định nghĩa.

**`stringFromBootstrap=true`:** yêu cầu `java.lang.String` đi qua cơ chế tìm lớp nền tảng; `String.class.getClassLoader() == null` là dấu hiệu bootstrap loader.

**`missing=ClassNotFoundException` và `searchesAfterMissing=2`:** `plugin.Unknown` không có ở parent cũng không có trong thư mục bytecode, nên `findClass` đọc thất bại và ném `ClassNotFoundException`. Bộ đếm tăng thêm một. Chương trình chỉ bắt lỗi để minh họa, không chuyển lỗi thành class giả.

**Điều kiện chạy:** dùng JDK có `javax.tools.JavaCompiler`. Thư mục được tạo bằng `Files.createTempDirectory` và xóa sau khi thử nghiệm; mã không can thiệp vào file dự án hiện có. Trong môi trường khác, nếu parent đã có `plugin.Greeter` hoặc hạn chế tạo file tạm, kết quả sẽ thay đổi hoặc chương trình báo lỗi.

## 6. defineClass là bước tạo định nghĩa, không phải chỉ đọc dữ liệu

Trong `findClass` vừa viết:

```java
return defineClass(binaryName, bytes, 0, bytes.length);
```

Đây là **đoạn trích** từ ví dụ chạy được ở trên. Lời gọi thực hiện việc định nghĩa lớp bằng các byte class file. Một số hệ quả:

- Tên nhị phân truyền vào phải tương ứng với định nghĩa thực tế trong bytecode. Không thể “đổi tên class tùy ý” bằng cách đưa `name` khác.
- Bytecode không hợp lệ có thể gây `ClassFormatError`, `VerifyError` hoặc lỗi liên kết khác; không nên bắt tất cả rồi giả vờ lớp không tồn tại.
- Định nghĩa trùng binary name trong **cùng một defining loader** có thể gây `LinkageError`. Không được bỏ qua cơ chế tái sử dụng và cố gọi `defineClass` nhiều lần.
- JVM áp đặt các quy tắc, gồm giới hạn việc một loader tùy biến định nghĩa lớp trong namespace `java.*` và kiểm tra liên quan package/certificate. Không dùng custom loader để cố thay thế lớp nền tảng của JDK.
- Các quá tải `defineClass` cho phép truyền `ProtectionDomain` khi cần. Việc lấy bytecode từ một nguồn khác **không tự biến class loader thành sandbox an toàn**; nguồn code không tin cậy cần một mô hình cô lập bảo mật riêng.
- Một class đã được định nghĩa có thể cần nạp superclass, superinterface và các phụ thuộc khác khi JVM liên kết hay thực thi. `defineClass` thành công chưa bảo đảm mọi hành vi của class sau đó sẽ thành công.
- `resolveClass(c)` (phương thức protected/final) liên quan linking/resolution, **khác với initialization**. `loadClass` cũng không có nghĩa static initializer của class đã chạy.

Nếu file `Greeter.class` có đúng tên nhưng tham chiếu tới `plugin.Helper` không thể tìm, `Greeter` có thể thất bại ở một bước liên kết/sử dụng khác tùy loại tham chiếu và thời điểm resolution. Vì vậy, custom loader phải biết cách tìm cả những phụ thuộc cần thiết hoặc thiết kế delegation cho chúng.

## 7. Liên hệ với findLoadedClass: vì sao không định nghĩa lại lớp mỗi lần?

`ClassLoader.findLoadedClass(String)` là phương thức **protected final**. Nó trả về một `Class<?>` nếu JVM đã ghi nhận loader tương ứng là initiating loader cho lớp có binary name ấy; nếu không, trả `null`.

Triển khai mặc định của `loadClass` hỏi `findLoadedClass` **trước** parent và `findClass`. Điều này mang lại hai điểm quan trọng:

1. Nếu đã có lớp thích hợp, `loadClass` có thể trả ngay cùng đối tượng `Class` mà không đọc bytecode thêm lần nữa.
2. Điều được tái sử dụng là **định nghĩa runtime**, không chỉ kết quả lưu trong `Map<String, byte[]>` do ứng dụng tự tạo.

Không nên biến mỗi yêu cầu `loadClass("plugin.Greeter")` thành một lần gọi `defineClass` mới; trong một loader, cùng binary name không thể được định nghĩa lặp như các phiên bản độc lập.

### findLoadedClass không đồng nghĩa defining loader

Nếu loader con từng được JVM ghi nhận là **initiating loader** của class do parent định nghĩa, `findLoadedClass` có thể trả về class ấy, nhưng `clazz.getClassLoader()` vẫn trả **parent**. Sự ghi nhận initiating loader là phần của mô hình JVM, khác với định danh lớp (binary name + defining loader).

Không có một API public trực tiếp từ `Class<?>` để truy xuất “mọi initiating loader từng yêu cầu class này”. Việc `clazz.getClassLoader() == child` là kiểm tra defining loader, không phải phép thử xem child từng gửi request hay chưa.

**Đừng tự cài một cache Map thay cho findLoadedClass mà không hiểu concurrency:** cache tự quản có thể sai khi nhiều thread nạp cùng tên, khi delegation đổi kết quả hoặc khi loader được JVM ghi nhận initiating class theo một đường khác. Nếu không cần thuật toán đặc biệt, giữ `loadClass` mặc định sẽ đơn giản và an toàn hơn.

## 8. Đồng thời nhiều luồng: đặt logic ở đâu?

Nhiều luồng có thể đồng thời gọi `loader.loadClass("plugin.Greeter")`. Mặc định `ClassLoader.loadClass(String, boolean)` sử dụng `getClassLoadingLock(name)` để phối hợp nạp lớp; một loader đã đăng ký là *parallel capable* có thể dùng khóa theo tên lớp thay vì một khóa chung.

Khi chỉ override **`findClass`**, ta thường tận dụng sẵn các bước của `loadClass`: kiểm tra lớp đã nạp, đồng bộ, delegation và resolve khi được yêu cầu. Đây là lý do nên override `findClass` nếu chỉ muốn thay nguồn bytecode.

Nếu quyết định override chính **`loadClass`** để triển khai child-first, phải tự bảo toàn những quy tắc cần thiết:

- Kiểm tra lớp đã được ghi nhận nạp trước khi định nghĩa mới.
- Đồng bộ phù hợp để hai thread không cùng cố `defineClass` cùng tên.
- Giữ vùng namespace bắt buộc dùng chung, đặc biệt class JDK và API/SPI chung.
- Quy định rõ lúc nào gọi parent, lúc nào tìm cục bộ và cách báo lỗi.
- Đảm bảo linking, method signatures và type identity không bị xung đột.

Trong một lớp con custom, `getClassLoadingLock(name)` là API để lấy khóa đồng bộ mà cơ chế của `ClassLoader` sử dụng. Nhưng viết được đoạn `synchronized` không đủ để chứng minh một custom child-first loader an toàn; cần kiểm thử tình huống cạnh tranh, class circularity và classloader constraints.

**Phân biệt:** đồng bộ **nạp/định nghĩa lớp** ở đây khác đồng bộ **khởi tạo tĩnh** `<clinit>`. JVM còn có quy tắc riêng để một runtime class được initialized đúng trước active use.

## 9. findClass so với findResource và tài nguyên khác

Override `findClass` để đọc `plugin/Greeter.class` **không tự động** làm các lời gọi sau tìm được mọi tài nguyên nằm trong thư mục riêng:

```java
loader.getResource("plugin/config.properties");
loader.getResources("META-INF/services/example.Service");
```

Các đoạn trên là minh họa. `ClassLoader` có cơ chế riêng gồm `getResource`, `findResource`, `findResources`, các biến thể stream và tương tác với module. Nếu plugin cần cấu hình, ảnh, file metadata hoặc ServiceLoader provider files, phải đảm bảo resource lookup cũng được cấu hình.

Với `URLClassLoader`, nhiều hành vi tìm class và resource đã được cung cấp. Nếu tự viết custom loader, cần tách nhiệm vụ:
- **Class loading:** ánh xạ binary name tới bytecode và định nghĩa class phù hợp.
- **Resource loading:** ánh xạ resource name tới URL/stream hợp lệ theo thứ tự ưu tiên được thiết kế.
- **Service discovery:** xác định loader dùng để tìm provider và giữ SPI dùng chung có cùng định danh runtime.
- **Package/module boundary:** bảo vệ quan hệ package, sealing và cấu hình module đúng.

Không chuyển lỗi resource lookup thành lỗi class loading một cách tùy tiện; hai luồng API có hợp đồng và loại kết quả khác nhau.

## 10. Khi nào nên sử dụng URLClassLoader thay vì tự override?

Nếu bytecode nằm trong **các thư mục class hoặc JAR chuẩn**, `URLClassLoader` thường là lựa chọn đơn giản hơn. Nó đã tích hợp cơ chế tìm lớp từ URL và có xử lý tài nguyên/JAR cần thiết.

Ví dụ minh họa cấu hình:

```java
try (java.net.URLClassLoader loader =
         new java.net.URLClassLoader(
             new java.net.URL[]{classesDir.toUri().toURL()},
             App.class.getClassLoader())) {

    Class<?> plugin = loader.loadClass("plugin.Greeter");
    System.out.println(plugin.getClassLoader() == loader);
}
```

**Đây là snippet, không phải chương trình độc lập**: `classesDir` và `App` phải được cung cấp bởi ứng dụng. Kết quả chỉ là `true` nếu parent không định nghĩa `plugin.Greeter` và loader này thực sự định nghĩa bản plugin; phụ thuộc thực tế vào classpath/URL và delegation.

Khi cần nguồn bytecode tùy biến như dữ liệu sinh trong bộ nhớ hoặc giao thức do ứng dụng quản lý, override `findClass` thường phù hợp, nhưng phải xem xét kiểm soát input, quản lý vòng đời, thực thi code có tin cậy không và kiểm thử concurrent loading.

### Khi đóng một loader, class có tự biến mất không?

`URLClassLoader.close()` giải phóng tài nguyên được quản lý, như file JAR đang mở. Nhưng đóng loader **không** bảo đảm unload ngay mọi class mà nó đã định nghĩa. Class unloading phụ thuộc reachability: còn instance, object `Class<?>`, thread, thread context class loader, callback, static registry hoặc cache giữ loader/class thì JVM có thể vẫn giữ định nghĩa ấy.

Nếu cần “reload plugin”, tạo định nghĩa class mới thường đòi hỏi một **class loader mới**, đồng thời phải giải phóng tham chiếu tới loader cũ khi ngừng plugin. Không cố sử dụng `defineClass` lần nữa cho cùng binary name trong loader cũ để thay bytecode.

## 11. Lỗi và dấu hiệu chẩn đoán

| Lỗi/triệu chứng | Giả thuyết cần kiểm tra | Hướng xử lý |
| --- | --- | --- |
| `ClassNotFoundException` từ `findClass` | Không tìm thấy bytecode ở nguồn local hoặc I/O thất bại | Kiểm tra binary name, root path, classpath, cause và permissions |
| `NoClassDefFoundError` sau khi tìm thấy lớp | Phụ thuộc bắt buộc thiếu, mismatch tên bytecode, hoặc class initialization lỗi trước đó | Đọc thông báo/cause; kiểm tra tên class và dependency thực tế |
| `ClassFormatError` / `VerifyError` | Bytecode không hợp lệ hoặc không phù hợp ràng buộc JVM | Kiểm tra compiler/class file format, tránh sửa bytecode tùy tiện |
| `UnsupportedClassVersionError` | Bytecode được compile cho JVM mới hơn JVM hiện tại | Chạy JVM phù hợp hoặc biên dịch target release tương thích |
| `LinkageError: duplicate class definition` | Cố `defineClass` lại cùng binary name trong một loader | Giữ default `loadClass`, dùng `findLoadedClass` và đồng bộ phù hợp |
| `SecurityException` khi định nghĩa lớp | Vi phạm giới hạn package/nguồn/điều kiện của `defineClass` | Không cố định nghĩa lớp trong namespace JDK; xem API contract |
| Không thấy `findClass` được gọi | Parent đã có class hoặc loader đã ghi nhận class trước đó | Log đường delegation và `clazz.getClassLoader()` |
| Có `findClass` nhưng API host không cast được | Plugin tự định nghĩa bản SPI cùng tên thay vì dùng parent | Đặt interface/DTO chung tại loader chia sẻ |
| `NoSuchMethodError` sau khi load class thành công | Phiên bản API thực tế không khớp bytecode đã biên dịch | Xem class được định nghĩa bởi loader nào và JAR thực tế |
| Resource trong plugin không tìm được | Custom loader chỉ override `findClass`, chưa có tìm resource | Kiểm tra `findResource`/`getResource` và cơ chế plugin |

### Quy trình điều tra cụ thể

1. **Ghi binary name đầy đủ** và loader được yêu cầu: `myLoader.loadClass(name)`.
2. **Quan sát `findClass`**: ghi log tên class, vị trí được kiểm tra, trạng thái file, nguyên nhân IOException khi có. Tránh dump toàn bộ bytecode ra log.
3. **Nếu nạp thành công, in defining loader** bằng `clazz.getClassLoader()`; parent-first có thể làm kết quả khác loader được yêu cầu.
4. **Kiểm tra số lần define**: một loader không nên tự định nghĩa lại cùng binary name ở lần yêu cầu thứ hai.
5. **Xem cause của ngoại lệ**, đặc biệt phân biệt missing bytecode, missing dependency, bytecode invalid và failed initialization.
6. **Kiểm tra runtime JDK**: class file version có phù hợp, môi trường có đầy đủ class/module cần thiết không.
7. **Nếu có nhiều plugin hoặc loader**, so sánh defining loader ở ranh giới SPI để tránh hai bản class cùng tên.
8. **Nếu lỗi xuất hiện sau reload**, kiểm tra các tham chiếu cũ và thiết kế lifecycle; class loader cũ có thể chưa được giải phóng.

Có thể dùng unified logging trên JVM hỗ trợ để xem sự kiện nạp class:

```bash
java -Xlog:class+load=info DirectoryClassLoaderDemo
```

Log này không thay thế việc ghi dấu parent delegation và `findClass` trong custom loader, vì chỉ nhìn đường dẫn class file không cho biết đầy đủ lý do loader cụ thể được chọn.

## 12. Các ngộ nhận thường gặp

**“Override `findClass` nghĩa là ứng dụng chắc chắn ưu tiên class của loader con.”** Sai. Parent-first của `loadClass` vẫn có thể chọn class ở cha trước.

**“`findClass` được gọi mỗi lần `loadClass` được gọi.”** Sai. Class đã được ghi nhận có thể được trả về từ `findLoadedClass`; parent có thể xử lý trước.

**“`findClass` phải tự duyệt cây parent.”** Không phải trách nhiệm mặc định của extension point; thuật toán `loadClass` thông thường đã làm việc đó.

**“Chỉ cần trả byte array là lớp đã được nạp.”** Sai. Phải có một định nghĩa runtime hợp lệ thông qua API thích hợp như `defineClass`.

**“Một loader có thể định nghĩa nhiều version cùng binary name.”** Không. Muốn hai định nghĩa độc lập cùng binary name thì cần defining loader khác nhau, theo giới hạn và quy tắc JVM.

**“`ClassNotFoundException` và `NoClassDefFoundError` luôn cùng một lỗi.”** Sai. Một bên là ngoại lệ checked khi tìm lớp bằng API, bên kia là `LinkageError` có nhiều nguyên nhân, kể cả thiếu phụ thuộc hoặc khởi tạo lỗi.

**“Override `findClass` tự động nạp resource và ServiceLoader provider metadata.”** Sai. Resource lookup có API/luồng riêng.

**“Custom ClassLoader là sandbox bảo mật.”** Sai. Một cách định nghĩa class từ bytecode không tự tạo cơ chế cô lập an toàn đối với code không tin cậy.

## 13. Câu hỏi tự kiểm tra

**Câu 1.** `child.loadClass("api.Service")` thành công nhưng `findClass` của child không được gọi. Điều này có thể hoàn toàn đúng không?

**Đáp án:** Có. Parent hoặc kết quả đã được ghi nhận có thể thỏa mãn yêu cầu trước khi nhánh local `findClass` chạy.

**Câu 2.** Nếu `child.findClass("plugin.Greeter")` đã đọc bytecode thành công thì nó phải làm gì để trả class runtime?

**Đáp án:** Với loader tự định nghĩa lớp theo cách thông thường, gọi `defineClass` bằng binary name tương ứng và bytecode hợp lệ, rồi trả `Class<?>`.

**Câu 3.** Vì sao ở ví dụ `DirectoryLoader`, nạp `plugin.Greeter` lần thứ hai không tăng `localSearches`?

**Đáp án:** Mặc định `loadClass` kiểm tra `findLoadedClass` trước; cùng runtime class đã được ghi nhận nên trả lại thay vì định nghĩa lần thứ hai.

**Câu 4.** Nếu custom loader lấy bytecode từ file `plugin/Greeter.class` nhưng gọi `defineClass("plugin.Other", ...)` thì sao?

**Đáp án:** Tên nhị phân được truyền không khớp định nghĩa; JVM từ chối thay vì đổi tên class, thường biểu hiện lỗi liên kết/tên sai.

**Câu 5.** Loader con có chứa `lib.Json` phiên bản mới, parent đã có `lib.Json` cũ, vì sao `findClass` của con không được gọi?

**Đáp án:** Parent-first delegation chọn định nghĩa của parent trước. Cần thiết kế ranh giới và chính sách delegation phù hợp thay vì chỉ thêm JAR vào loader con.

**Câu 6.** Một plugin nạp được `Greeter.class` nhưng không tìm thấy `plugin/config.properties`. Có nên kết luận `findClass` sai không?

**Đáp án:** Không đủ dữ kiện. `findClass` xử lý class; resource lookup cần `getResource`/`findResource` và cấu hình nguồn resource.

**Câu 7.** Nếu hai thread cùng yêu cầu tên lớp, chỉ ghi override `findClass` có lợi gì?

**Đáp án:** Có thể tiếp tục tận dụng khóa nạp lớp, kiểm tra lớp đã ghi nhận và delegation trong `loadClass` mặc định, giảm nguy cơ tự định nghĩa trùng. Vẫn cần bảo đảm chính triển khai `findClass` và nguồn dữ liệu an toàn khi dùng đồng thời.

## 14. Kết luận và tham khảo

`findClass(String)` là **điểm mở rộng tìm bytecode** để một `ClassLoader` biết định nghĩa class từ nguồn riêng của mình. Trong mô hình mặc định, `loadClass` quyết định **khi nào cần gọi** extension point này, còn `defineClass` thực hiện **định nghĩa runtime** từ bytecode. Việc hiểu rõ ba trách nhiệm giúp tránh định nghĩa trùng, sai loader, xung đột type identity và nhầm lẫn giữa class loading với resource loading.

Tài liệu chính thức:

- Java SE 17 `ClassLoader` API, bao gồm `loadClass`, `findClass`, `findLoadedClass` và `defineClass`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17 `URLClassLoader` API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html
- JVMS 17 §5.3, *Creation of Classes and Interfaces*: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-api-003">findLoadedClass và việc tái sử dụng lớp đã được loader ghi nhận</a>

<details>
<summary>Xem chi tiết</summary>

# findLoadedClass và việc tái sử dụng lớp đã được loader ghi nhận

## 1. Vì sao một ClassLoader cần kiểm tra lớp đã nạp?

Khi JVM hoặc ứng dụng yêu cầu nạp một class, bộ nạp lớp cần biết tên đó đã được xử lý trong phạm vi loader liên quan hay chưa. Nếu mỗi yêu cầu đều tạo một định nghĩa class mới có cùng binary name trong cùng defining loader, hệ thống kiểu sẽ thiếu nhất quán và có thể vi phạm quy tắc JVM.

Java cung cấp phương thức:

```java
protected final Class<?> findLoadedClass(String name)
```

Phương thức này trả lại `Class<?>` tương ứng với tên nhị phân `name` **nếu JVM đã ghi nhận loader hiện tại là một initiating loader của class mang tên đó**. Nếu chưa có ghi nhận phù hợp, phương thức trả `null`.

Điều quan trọng: `findLoadedClass` là **tra cứu trạng thái đã có**, không phải yêu cầu đọc một file `.class`, gọi loader cha, thực hiện `findClass`, khởi tạo lớp hay tạo đối tượng.

Một quy tắc thực hành:

- **`findLoadedClass(name)`:** “JVM đã ghi nhận loader này là initiating loader của class tên `name` chưa?”
- **`loadClass(name)`:** “Hãy tìm/lấy class mang tên `name`, thông thường kiểm tra lớp đã có, ủy quyền tới cha rồi mới tìm bằng loader hiện tại.”
- **`findClass(name)`:** “Nếu cơ chế tìm ở parent chưa cung cấp class, hãy để loader hiện tại thử tìm và định nghĩa class theo cách riêng.”
- **`defineClass(...)`:** “Định nghĩa runtime class từ bytes phù hợp, với loader hiện tại làm defining loader.”
- **`resolveClass(c)`:** “Yêu cầu JVM liên kết class theo API, nếu cần”; không đồng nghĩa thực thi static initializer.

Trong một loader được thiết kế đúng, `findLoadedClass` là bước đầu quan trọng để tránh cố định nghĩa lặp class và để những lần tra cứu tiếp theo sử dụng lại runtime type đã ghi nhận.

## 2. Chính xác phương thức trả về điều gì?

Theo Java SE 17 API, `findLoadedClass(String)`:

1. Nhận **binary name** như `com.example.Plugin` hoặc `java.lang.String`.
2. Xem loader object mà trên đó method được gọi có được JVM ghi nhận là **initiating loader** của class mang tên đó hay không.
3. Trả một `Class<?>` nếu đã có ghi nhận; nếu không trả `null`.

Phương thức là **`protected final`**:

- `protected`: code bên ngoài không được gọi tự do qua một `ClassLoader` bất kỳ; subclass có thể dùng trong cơ chế nạp của mình.
- `final`: subclass không thể override và tự thay đổi ý nghĩa việc tra cứu JVM đã ghi nhận.
- Không có checked `ClassNotFoundException`: nó chỉ tra cứu, không tự tìm file/module trên đường nạp.
- Giá trị `null` có nghĩa **loader này chưa được VM ghi nhận là initiating loader của tên đó**; không có nghĩa class ấy vắng mặt ở mọi loader trong JVM.

Ví dụ, nếu application loader đã biết `com.example.Service` nhưng một custom loader mới chưa từng tham gia nạp tên đó, `custom.findLoadedClass("com.example.Service")` có thể trả `null` dù class hoàn toàn tồn tại trong ứng dụng. Sau khi custom loader yêu cầu parent nạp, việc ghi nhận initiating loader chịu quy tắc VM và ngữ cảnh yêu cầu; không được suy rằng một lời gọi Java trực tiếp đã tạo bản sao class hoặc bắt buộc cache kết quả theo cách tùy ý.

### Hai từ “đã nạp” dễ gây hiểu lầm

`findLoadedClass` được đặt tên theo cách dùng thực tế trong thuật toán `loadClass`, nhưng contract chính xác đề cập tới **initiating loader được JVM ghi nhận**, không chỉ “class mà loader này tự `defineClass`”.

Một loader có thể:

- **Tự định nghĩa** một class: nó là defining loader và được VM ghi nhận liên quan tới việc nạp class.
- **Ủy quyền** cho loader cha: class trả về có thể do cha định nghĩa; loader khởi xướng và defining loader có thể khác nhau.
- Không hề biết class: `findLoadedClass` trả `null` trong phạm vi loader đó, dù parent hoặc loader khác có thể tìm được class.

Đặc tả JVMS mô tả sự khác biệt này bằng khái niệm **defining loader** và **initiating loader**.

## 3. Đặt findLoadedClass vào luồng loadClass mặc định

Với `ClassLoader.loadClass(String, boolean)` mặc định, logic chính có thể đọc như sau:

```text
loadClass(binaryName, resolve)
          |
          v
findLoadedClass(binaryName)
          |
          +-- Có Class<?> ----------> reuse Class<?> đã ghi nhận
          |
          +-- null
                |
                v
      Parent.loadClass(binaryName)
       (hoặc bootstrap nếu parent null)
                |
                +-- Thành công -----> dùng Class<?> được parent cung cấp
                |
                +-- Không tìm thấy
                        |
                        v
                   findClass(binaryName)
                        |
                        +-- Thành công -> dùng Class<?> tìm/định nghĩa được
                        |
                        +-- Thất bại -> ClassNotFoundException

Nếu resolve == true: gọi resolveClass(classFound).
Trả Class<?> cho bên yêu cầu.
```

Viết theo mã giả:

```text
Class c = findLoadedClass(name);
if (c == null) {
    try {
        c = parent != null
                ? parent.loadClass(name)
                : askBootstrapLoader(name);
    } catch (ClassNotFoundException ignored) {
        // tiếp tục cơ chế loader hiện tại
    }
    if (c == null) {
        c = findClass(name);
    }
}
if (resolve) {
    resolveClass(c);
}
return c;
```

**Đây là mã giả giải thích luồng, không phải code Java biên dịch độc lập**: `askBootstrapLoader` không phải method công khai trong Java API. Luồng thực tế còn bao gồm đồng bộ hóa bằng `getClassLoadingLock(name)` và chi tiết lookup của VM.

Ý nghĩa chính:

- `findLoadedClass` được gọi **trước khi** parent delegation hoặc `findClass` trong thuật toán mặc định.
- Nếu trả một `Class<?>`, loader không cần cố tìm/định nghĩa lại kiểu đó trong lần gọi ấy.
- Nếu trả `null`, cơ chế vẫn tiếp tục qua parent/bootstrap và/hoặc `findClass`.
- `loadClass(name)` tương đương `loadClass(name, false)`; giá trị `false` không làm mất bước `findLoadedClass`.

Một subclass có thể override `loadClass` để thực hiện child-first, selective delegation hoặc cơ chế riêng; trong trường hợp đó cần giữ việc tránh nạp trùng một cách chính xác.

## 4. Tên class, định danh kiểu và giới hạn vùng tra cứu

Java runtime class/interface identity dựa trên **binary name và defining loader**. Vì vậy:

```text
com.example.Widget + LoaderA  -> runtime Class A
com.example.Widget + LoaderB  -> runtime Class B
```

Hai `Class<?>` trên khác nhau nếu LoaderA và LoaderB thực sự định nghĩa hai class độc lập, dù tên string `com.example.Widget` giống nhau.

`findLoadedClass` **không tìm toàn JVM** để trả một class bất kỳ trùng tên. Nó kiểm tra class mà loader **hiện tại** đã được VM ghi nhận là initiating loader. Vì thế:

- LoaderA có thể tìm thấy bản `Widget` riêng của A.
- LoaderB có thể chưa ghi nhận `Widget`, nên trả `null`.
- Sau đó LoaderB có thể ủy quyền và nhận `Widget` của một parent, hoặc tự định nghĩa `Widget` của B, tùy chính sách và loading constraints liên quan.
- Không được suy từ `findLoadedClass(name) == null` rằng tên đó chưa xuất hiện ở class path hoặc chưa được loader khác định nghĩa.

### Binary name khác descriptor

| Loại | Ví dụ | Dùng với findLoadedClass? |
|---|---|---|
| Binary name class | `java.lang.String` | Có |
| Binary name nested class | `java.util.Map$Entry` | Có |
| Internal name bytecode | `java/lang/String` | Không |
| Field descriptor class | `Ljava/lang/String;` | Không |
| Source-style array | `String[]` | Không phải binary name của ordinary class |
| Primitive name | `int` | Không phải class được nạp từ ordinary class file |

Đối với array, JVM có quy tắc riêng để tạo array class khi cần; không dùng `findLoadedClass("[I")` như một API phổ quát tạo/truy vấn array classes bằng descriptor. Với kiểu mảng, dùng class literal (`int[].class`) hoặc `Class.forName` với array name được tài liệu hỗ trợ, nếu yêu cầu lookup bằng chuỗi.

## 5. Java 17: minh họa chính xác việc định nghĩa một lần và tái sử dụng

Ví dụ sau cố ý thiết kế **một custom loader tự định nghĩa một class cụ thể**. Điều này giúp chứng minh `findLoadedClass` tránh định nghĩa lại class mà không phụ thuộc vào cách ghi nhận một đường parent delegation.

Tạo file **`FindLoadedDemo.java`** chứa cả public class và package-private class ở cuối file:

```java
import java.io.IOException;
import java.io.InputStream;

public class FindLoadedDemo {
    static final String TARGET = "LocalPayload";

    static class CopyLoader extends ClassLoader {
        private int definitions;

        CopyLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> peek(String binaryName) {
            return findLoadedClass(binaryName);
        }

        int definitions() {
            return definitions;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!TARGET.equals(name)) {
                return super.loadClass(name, resolve);
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) {
                    type = findClass(name);
                }
                if (resolve) {
                    resolveClass(type);
                }
                return type;
            }
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            if (!TARGET.equals(name)) {
                throw new ClassNotFoundException(name);
            }

            String resource = name.replace('.', '/') + ".class";

            try (InputStream input =
                         getParent().getResourceAsStream(resource)) {
                if (input == null) {
                    throw new ClassNotFoundException(name);
                }

                byte[] bytes = input.readAllBytes();
                Class<?> type =
                        defineClass(name, bytes, 0, bytes.length);
                definitions++;
                return type;
            } catch (IOException error) {
                throw new ClassNotFoundException(name, error);
            }
        }
    }

    public static void main(String[] args)
            throws ClassNotFoundException {
        ClassLoader app = FindLoadedDemo.class.getClassLoader();
        CopyLoader custom = new CopyLoader(app);

        System.out.println("Before load: "
                + (custom.peek(TARGET) == null));

        Class<?> first = custom.loadClass(TARGET);
        Class<?> second = custom.loadClass(TARGET);

        System.out.println("Same Class object: " + (first == second));
        System.out.println("Found by peek: "
                + (custom.peek(TARGET) == first));
        System.out.println("Definitions: " + custom.definitions());
        System.out.println("Owned by custom: "
                + (first.getClassLoader() == custom));

        Class<?> appCopy = app.loadClass(TARGET);
        System.out.println("Different from app: "
                + (first != appCopy));
    }
}

class LocalPayload {
}
```

Trong thư mục chứa file này, chạy trên JDK 17 trở lên:

```bash
javac FindLoadedDemo.java
java FindLoadedDemo
```

Kết quả stdout:

```text
Before load: true
Same Class object: true
Found by peek: true
Definitions: 1
Owned by custom: true
Different from app: true
```

**Giải thích từng bước:**

1. `javac` tạo `FindLoadedDemo.class`, các class file phụ tương ứng và `LocalPayload.class`. Hai runtime class identities `LocalPayload` chỉ được tạo **lúc chương trình chủ động nạp**.
2. `custom.peek("LocalPayload")` gọi `findLoadedClass` từ trong subclass. Ban đầu custom loader chưa định nghĩa/được VM ghi nhận cho tên đó, nên trả `null`.
3. Lần `custom.loadClass(TARGET)` đầu tiên vào nhánh override chỉ dành cho `LocalPayload`; `findLoadedClass` trả `null` và `findClass` đọc bytes `LocalPayload.class`, gọi `defineClass`. Bộ đếm `definitions` tăng lên một.
4. Lần gọi thứ hai, `findLoadedClass` trả về đúng `Class<?>` đã được custom loader định nghĩa, nên không gọi `findClass` lần nữa.
5. `first == second` chứng minh cùng một runtime class object; `definitions == 1` chứng minh class đã được custom loader **định nghĩa một lần**.
6. `first.getClassLoader() == custom` cho biết custom loader là **defining loader** của bản class này.
7. `app.loadClass(TARGET)` cung cấp một runtime `LocalPayload` riêng do application loader định nghĩa. Hai class có cùng binary name nhưng khác defining loaders, nên `first != appCopy`.

**Phạm vi minh họa:** đoạn override thực hiện child-first **chỉ với `LocalPayload`**. Các tên khác đi qua `super.loadClass` theo cơ chế mặc định. Không áp dụng mẫu child-first không kiểm soát cho package lõi hoặc shared API của plugin.

Không có instance nào của `LocalPayload` được tạo trong ví dụ; nạp định nghĩa class và tạo object là hai hoạt động khác nhau.

## 6. Khi class được parent nạp: reuse và owner là hai câu hỏi khác nhau

Xét một loader con ủy quyền bình thường:

```java
ClassLoader app = MyMain.class.getClassLoader();
ClassLoader child = new ClassLoader(app) { };
Class<?> type = child.loadClass("java.lang.String");
```

Đoạn này là **mã minh họa**, cần có class `MyMain`. `type` thường chính là `String.class`, có defining loader là bootstrap. Loader con **không** tạo thêm một `String` mới.

Ở đây nên phân biệt:

- **Kết quả lookup:** `child.loadClass(...)` có thể trả về một `Class<?>` do parent/bootstrap định nghĩa.
- **Defining loader:** `type.getClassLoader()` mô tả loader sở hữu định nghĩa; với `String` chuẩn sẽ là `null`.
- **Initiating loader:** JVM có thể ghi nhận các loader tham gia quá trình nạp/resolve một class; `findLoadedClass` xét **ghi nhận của VM**, không xét đơn giản bằng việc “đã từng gọi Java method nào”.
- **Tái sử dụng:** khi nhận lại chính `Class<?>` của parent, không có bản sao class được tự động tạo thêm.

Điểm cần cẩn trọng với ví dụ trực tiếp `child.loadClass(...)`: **đừng khẳng định bất kể ngữ cảnh JVM nào, sau mỗi lần Java code trực tiếp gọi `loadClass` để lấy class từ parent, `child.findLoadedClass(name)` nhất định phải khác `null`.** Contract chính xác của `findLoadedClass` yêu cầu JVM đã ghi nhận loader là initiating loader; hoạt động JVM loading/resolution và một method call do Java code chủ động gọi không nên bị đồng nhất trong phần suy luận của chương trình.

### Ví dụ Java 17: xác nhận cùng runtime class sau delegation

Tạo file **`ParentReuseDemo.java`**:

```java
public class ParentReuseDemo {
    public static void main(String[] args)
            throws ClassNotFoundException {
        ClassLoader parent = ParentReuseDemo.class.getClassLoader();
        ClassLoader child = new ClassLoader(parent) { };

        Class<?> a = child.loadClass("ParentReuseDemo");
        Class<?> b = child.loadClass("ParentReuseDemo");

        System.out.println("Same Class returned: " + (a == b));
        System.out.println("Same as source class: "
                + (a == ParentReuseDemo.class));
        System.out.println("Owner is parent: "
                + (a.getClassLoader() == parent));
        System.out.println("Child does not define it: "
                + (a.getClassLoader() != child));

        Class<?> core = child.loadClass("java.lang.String");
        System.out.println("String from bootstrap: "
                + (core == String.class
                   && core.getClassLoader() == null));
    }
}
```

Chạy:

```bash
javac ParentReuseDemo.java
java ParentReuseDemo
```

Stdout trên JDK 17 trong cấu hình class path thông thường:

```text
Same Class returned: true
Same as source class: true
Owner is parent: true
Child does not define it: true
String from bootstrap: true
```

Hai lần `child.loadClass("ParentReuseDemo")` trả cùng class identity do **parent** định nghĩa. Chương trình không yêu cầu một đảm bảo không có trong API về số lần hàm parent được gọi bên trong hoặc trạng thái ghi nhận nội bộ của `child`; nó chỉ quan sát điều chắc chắn từ kết quả.

## 7. Khi findLoadedClass trả null: hiểu đúng khả năng xảy ra

`null` có thể xuất hiện khi:

1. Loader đó chưa tham gia nạp/được VM ghi nhận cho binary name yêu cầu.
2. Một class cùng tên có ở loader khác nhưng chưa được loader này ghi nhận là initiating loader.
3. Loader đã được tạo nhưng chương trình chưa yêu cầu nạp một class thuộc phạm vi của nó.
4. Tên truyền vào không phù hợp với contract của API, ví dụ dùng descriptor thay vì binary name; không nên dùng trả `null` để suy ra đó là cú pháp tên hợp lệ.

**Không thể suy ra chỉ từ `null` rằng:**

- Class không tồn tại trong JVM.
- Parent/bootstrap không thể tìm được class.
- Class file không nằm trên class path.
- Class chưa từng được load bởi bất cứ class loader nào.
- Phải gọi `defineClass` ngay lập tức, bỏ qua chính sách delegation của loader.

Nếu loader đang thực hiện thuật toán mặc định, kết quả `null` khiến nó tiếp tục parent delegation trước. Chỉ khi bước parent không tìm được, `findClass` mới được yêu cầu.

## 8. Nếu bỏ findLoadedClass hoặc định nghĩa trùng thì sao?

Giả sử một custom loader override `loadClass` và mỗi lần đều tự đọc bytes, gọi `defineClass` cho **cùng binary name**. Trong một defining loader, không được định nghĩa hai runtime classes có cùng tên; hành vi có thể dẫn tới `LinkageError` do nỗ lực định nghĩa lặp, thay vì cho ra “hai bản mới” có cùng defining loader.

Một ví dụ mã giả **không nên dùng trong ứng dụng**:

```text
// Sai: không kiểm tra class đã định nghĩa hoặc ghi nhận
loadClass(name):
    bytes = readClassFile(name)
    return defineClass(name, bytes, ...)
```

Khi cùng tên được yêu cầu lại, loader có thể tiếp tục gọi `defineClass` và thất bại. Ngược lại, phần 5 đã cho thấy một cách kiểm tra `findLoadedClass` trước khi định nghĩa.

**Quan hệ với identity:** hai class cùng binary name nhưng khác **defining loader** có thể tồn tại. Hai định nghĩa trùng tên bởi **cùng defining loader** không tạo thành hai class hợp lệ. Hai tình huống không mâu thuẫn nhau.

**Quan hệ với lỗi khác:** `ClassNotFoundException` nghĩa lookup không tìm thấy class; `LinkageError` có thể xảy ra khi class data/constraints/linking không hợp lệ. Không gọi mọi `LinkageError` là “class đã load hai lần” mà phải xét thông báo và stack trace cụ thể.

## 9. findLoadedClass không đồng nghĩa initialize hoặc tạo object

Mỗi một việc sau là khác nhau:

| Hoạt động | Ý nghĩa |
|---|---|
| `findLoadedClass(name)` | Tra cứu class đã được JVM ghi nhận cho initiating loader này |
| `loadClass(name)` | Yêu cầu nạp/lấy class, thường không yêu cầu initialization |
| `defineClass(...)` | Định nghĩa runtime class từ bytes |
| `resolveClass(c)` | Yêu cầu linking class theo API |
| `Class.forName(name)` | Tra cứu class; overload một tham số thường yêu cầu initialization |
| `new SomeClass()` | Tạo một instance mới, bảo đảm class đã initialized theo quy tắc |
| `getDeclaredConstructor().newInstance()` | Dùng reflection để tạo instance nếu truy cập/constructor phù hợp |

Vì `findLoadedClass` chỉ trả `Class<?>` đã ghi nhận:

- Không thực thi constructor.
- Không chủ động chạy static initializer `<clinit>`.
- Không chứng minh class đã initialized thành công; loading, linking và initialization là những giai đoạn khác nhau.
- Không đảm bảo class có thể được sử dụng với mọi phương thức hoặc dependency ở bất kỳ thời điểm nào; linking/resolution và access rules vẫn có thể gây lỗi.

Ví dụ:

```java
Class<?> existing = custom.peek("LocalPayload");
```

Nếu chương trình đã tạo/ghi nhận class bằng loader `custom` như phần 5, dòng trên chỉ lấy metadata class. Đó không phải một lần `new LocalPayload()`.

## 10. Thread-safety: vì sao phải gắn với getClassLoadingLock?

Trong môi trường nhiều thread, hai thread có thể đồng thời yêu cầu loader nạp cùng binary name:

```text
Thread A: findLoadedClass("Plugin") -> null
Thread B: findLoadedClass("Plugin") -> null
Thread A: defineClass("Plugin", ...) -> thành công
Thread B: defineClass("Plugin", ...) -> nguy cơ định nghĩa trùng/LinkageError
```

Nếu tự override `loadClass`, cần đồng bộ phần **kiểm tra đã nạp → nạp/định nghĩa → resolve** cho cùng tên. API `ClassLoader.getClassLoadingLock(name)` hỗ trợ lock phù hợp với cơ chế class loading.

Hình mẫu:

```java
@Override
protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
        Class<?> type = findLoadedClass(name);
        if (type == null) {
            // Áp dụng chính sách delegation của loader;
            // nếu parent không cung cấp, mới tìm/định nghĩa class.
            type = findClass(name);
        }
        if (resolve) {
            resolveClass(type);
        }
        return type;
    }
}
```

Đây là **khung minh họa**, không phải một implementation parent-first hoàn chỉnh vì nó không ủy quyền cho parent. Muốn dùng chính sách thông thường, hãy ưu tiên **chỉ override `findClass`** và để `loadClass` kế thừa thực hiện các bước kiểm tra, delegation và đồng bộ hóa.

### Parallel-capable class loaders

Các subclass có thể đăng ký `registerAsParallelCapable()` theo contract của Java để cho phép những class name khác nhau được nạp đồng thời theo cách thích hợp. Nếu không, implementation mặc định có thể dùng lock rộng hơn trên loader object. Không phải cứ gọi `getClassLoadingLock(name)` là loader tự động parallel-capable.

Khi tự override `loadClass`, việc dùng lock phải nhất quán với cấu hình parallel-capable và các shared mutable state trong `findClass`. Bộ đếm minh họa trong phần 5 chỉ được truy cập theo luồng tuần tự trong `main`; nếu đưa vào hệ thống nhiều thread thực tế, cần thiết kế đồng bộ/quan sát state riêng.

## 11. Vì sao không tự viết một cache Map<String, Class<?>> thay cho findLoadedClass?

Một số custom loader muốn lưu:

```java
Map<String, Class<?>> cache;
```

Cache ứng dụng có thể hữu ích để quản lý metadata hoặc tài nguyên phụ trợ, nhưng **không thay thế contract của JVM về class identity và initiating loader**.

Các rủi ro nếu chỉ dựa trên một map tự tạo:

- Map có thể sai lệch so với trạng thái class mà VM đã ghi nhận.
- Nếu class được giải quyết từ parent, việc tự bỏ/mất key trong map không làm định nghĩa parent biến mất.
- Hai thread không được đồng bộ đúng có thể vẫn đua nhau đọc bytes và `defineClass`.
- Nếu giữ reference mạnh tới `Class<?>` hoặc custom loader lâu hơn cần thiết, cache có thể cản class unloading trong mô hình plugin/hot-reload.
- Map không tự kiểm tra loading constraints hoặc package/module access rules mà VM phải áp dụng.

Vì vậy, sử dụng `findLoadedClass` làm tra cứu chuẩn trong logic nạp lớp. Chỉ thêm cache nếu có **mục tiêu tách biệt**, lifecycle rõ ràng và không gây sai khác với VM.

### findLoadedClass và findClass khác chỗ nào?

Hai tên dễ nhầm:

- `findLoadedClass`: **kiểm tra đã được ghi nhận**; `protected final`, trả `Class<?>` hoặc `null`.
- `findClass`: **móc tìm và định nghĩa class mới** do subclass triển khai; có thể đọc bytes/JAR/resource rồi gọi `defineClass` hoặc ném `ClassNotFoundException`.

Không thay `findClass` bằng lời gọi `findLoadedClass` rồi kỳ vọng một class chưa tồn tại sẽ tự được tạo ra.

## 12. Trường hợp thực tế: plugin loader và hot reload

Một ứng dụng host tải plugin bằng custom class loader. Trong vòng đời của **một loader object**:

1. Plugin `com.example.ReportPlugin` được yêu cầu lần đầu.
2. Parent không cung cấp class plugin; custom `findClass` định nghĩa nó.
3. Những yêu cầu về cùng tên qua loader đó cần tái sử dụng định nghĩa đã ghi nhận.
4. Khi hot reload phiên bản mới, thường cần **tạo một loader mới** cho phiên bản plugin mới, vì class đã được định nghĩa không được thay thế chỉ bằng cách gọi lại `defineClass` cùng tên ở loader cũ.

Thực tế, có thể thực hiện cơ chế redefinition/retransformation đặc biệt qua instrumentation, nhưng đó là một API khác có giới hạn riêng, không làm `findLoadedClass` trở thành cơ chế reload bytes.

Ngược lại, class loader mới cũng không đảm bảo loader cũ được GC/unload ngay: các thread, callbacks, `ThreadLocal`, caches, instances hoặc `Class<?>` references vẫn có thể giữ loader cũ reachable.

Khi debug lỗi plugin:

- Quan sát class loader object và `clazz.getClassLoader()`.
- So sánh các `Class<?>` bằng `==` khi muốn biết runtime type có cùng identity không.
- Kiểm tra liệu loader chủ động định nghĩa hay ủy quyền sang parent.
- Nếu xung đột, xem `ClassCastException` và `LinkageError` theo ngữ cảnh loader/descriptor.
- Đừng suy rằng việc có hai class cùng binary name trên hai loader khác nhau tự động là lỗi; thường đó là cơ chế cách ly mong muốn, miễn các shared API types được resolve thống nhất.

## 13. Các lỗi phổ biến và cách tránh

| Nhận định sai | Cách hiểu đúng |
|---|---|
| “`findLoadedClass` sẽ load class nếu chưa có.” | Nó chỉ tra cứu và trả `null` nếu không có ghi nhận phù hợp. |
| “`findLoadedClass` chỉ tìm class mà chính loader tự định nghĩa.” | API dựa trên **initiating loader** được JVM ghi nhận, không đơn giản là defining loader. |
| “`findLoadedClass(name) == null` chứng minh class chưa có ở đâu trong JVM.” | Nó chỉ phản ánh trạng thái VM liên quan đến **loader hiện tại**. |
| “Cùng binary name nghĩa là cùng class.” | Runtime identity còn phụ thuộc defining loader. |
| “`loadClass` mỗi lần tạo một định nghĩa class mới.” | Loader đúng cách tái sử dụng class đã ghi nhận hoặc định nghĩa do parent cung cấp. |
| “`findClass` và `findLoadedClass` là tên khác của cùng việc.” | Một bên tìm/định nghĩa mới, một bên tra cứu đã nạp. |
| “Lặp `defineClass` cùng tên trong một loader là cách cập nhật class.” | Có thể gây `LinkageError`; hot reload thường cần loader mới hoặc cơ chế instrumentation phù hợp. |
| “`findLoadedClass` làm chạy static initializer.” | Nó không yêu cầu initialization hay tạo instance. |
| “Chỉ dùng `findLoadedClass` đủ để tránh race.” | Cần đồng bộ đúng chuỗi kiểm tra-nạp/định nghĩa, thường qua `getClassLoadingLock`. |
| “Parent trả về class thì child chắc chắn là defining loader.” | Defining loader là nơi thực sự định nghĩa class, thường là parent hoặc bootstrap trong tình huống delegation. |
| “Cache `Map<String,Class<?>>` tự thay thế được VM state.” | Map riêng không điều khiển JVM initiating-loader records hoặc class identity. |

## 14. Tự kiểm tra

**Câu 1.** `findLoadedClass("com.example.Task")` trả `null`. Có thể kết luận file `Task.class` không nằm trên class path không?

**Đáp án:** Không. Chỉ biết loader hiện tại chưa được JVM ghi nhận làm initiating loader cho binary name ấy; parent/loader khác có thể thấy được class.

**Câu 2.** Tại sao `findLoadedClass` là `protected final`?

**Đáp án:** Subclass sử dụng được trong logic nạp class, nhưng không thể override thay đổi ý nghĩa tra cứu trạng thái được JVM ghi nhận.

**Câu 3.** Một class được custom loader định nghĩa lần đầu bằng `defineClass`. Lần sau cùng loader yêu cầu lại, phương thức nào nên được dùng để kiểm tra class đã có?

**Đáp án:** `findLoadedClass(binaryName)`, dưới đồng bộ hóa thích hợp trong `loadClass`, sẽ giúp tái sử dụng `Class<?>`.

**Câu 4.** Hai loader độc lập định nghĩa `com.example.Task` với cùng bytes. `Class<?>` của chúng có bằng nhau không?

**Đáp án:** Không. Cùng binary name nhưng khác defining loader tạo hai runtime identities khác nhau.

**Câu 5.** `child.loadClass("java.lang.String")` trả `String.class`. `String.class.getClassLoader() == null` nghĩa gì?

**Đáp án:** `String` có defining loader là bootstrap trong cấu hình JDK thông thường. Không có nghĩa child loader đã tự định nghĩa `String`.

**Câu 6.** Điều gì nguy hiểm nếu hai thread cùng thấy `findLoadedClass(name) == null` rồi cùng gọi `defineClass`?

**Đáp án:** Có thể cố định nghĩa lặp cùng binary name ở một loader, dẫn đến lỗi liên kết. Cần lock thích hợp quanh toàn bộ đoạn kiểm tra và định nghĩa, hoặc dùng implementation `loadClass` mặc định.

**Câu 7.** `findLoadedClass` có đảm bảo class đã initialized không?

**Đáp án:** Không. Loading/recording và initialization là các giai đoạn khác nhau; phương thức chỉ tra cứu.

**Câu 8.** Vì sao trong `FindLoadedDemo` hai lần gọi `custom.loadClass("LocalPayload")` chỉ làm `definitions` tăng một lần?

**Đáp án:** Sau lần `defineClass` đầu, JVM ghi nhận class với loader đó. Lần thứ hai, `findLoadedClass` trả `Class<?>` đã có nên không đi vào `findClass`.

## 15. Ghi nhớ và tham khảo

- **`findLoadedClass(String)` là tra cứu:** trả class đã được VM ghi nhận theo **initiating loader**, hoặc `null`.
- **`loadClass` mặc định kiểm tra lớp đã nạp trước** rồi mới ủy quyền và gọi `findClass` nếu cần.
- **Tái sử dụng `Class<?>` là điều kiện của một loader nhất quán**, giúp tránh `defineClass` trùng cùng tên trong một defining loader.
- **Defining loader khác initiating loader:** class lấy từ parent thường có owner là parent; cần phân biệt kết quả lookup và nơi định nghĩa.
- **Thread-safety và lifecycle:** dùng `getClassLoadingLock` khi override `loadClass`; không coi `findLoadedClass` là công cụ initialization, garbage collection hay hot reload.

Tài liệu chính thức:

1. [Java SE 17 — ClassLoader.findLoadedClass](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html#findLoadedClass(java.lang.String)): định nghĩa theo initiating loader và giá trị trả về.
2. [Java SE 17 — ClassLoader.loadClass](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html#loadClass(java.lang.String,boolean)): thuật toán mặc định, `findLoadedClass`, parent delegation, `findClass` và synchronization.
3. [Java SE 17 — ClassLoader.getClassLoadingLock](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html#getClassLoadingLock(java.lang.String)): khóa nạp lớp và parallel-capable loaders.
4. [JVMS 17 — 5.3 Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3): defining loader, initiating loader và class identity.
5. [JVMS 17 — 5.3.4 Loading Constraints](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3.4): ràng buộc tính nhất quán kiểu giữa loaders.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-api-004">defineClass: tạo lớp từ byte&#91;&#93; hoặc ByteBuffer</a>

<details>
<summary>Xem chi tiết</summary>

# defineClass: tạo lớp từ byte[] hoặc ByteBuffer

> **Phạm vi:** Java Core → ClassLoader → API nạp lớp. Bài này giải thích `ClassLoader.defineClass` dùng bytecode đã biên dịch để **định nghĩa một class mới** tại runtime, cách dùng hai dạng dữ liệu `byte[]` và `ByteBuffer`, tên nhị phân, protection domain, các lỗi thường gặp và cách kết hợp với `findClass`/`loadClass`. Ví dụ biên dịch trên Java 17+.

## 1. Bài toán: biến bytecode thành Class<?> trong JVM

Thông thường, `javac` biến source `demo.Gadget` thành file `demo/Gadget.class`; application class loader đọc class file từ classpath khi cần. Tuy nhiên, trong các hệ thống plugin, bộ sinh mã bytecode, loader đọc JAR ở vị trí riêng, hoặc framework nạp mã được tạo động, ta có thể **tự sở hữu một `ClassLoader`** và cho nó nhận bytes ở runtime.

`ClassLoader.defineClass` là thao tác lõi:

```text
Source .java
    |
    | javac / bytecode generator
    v
Valid JVM class-file bytes       <- byte[] / ByteBuffer
    |
    | ClassLoader.defineClass(...)
    v
Class<?> do loader này định nghĩa
    |
    | linking / initialization theo các quy tắc riêng
    v
Có thể tạo instance, gọi method, đọc field bằng Java/Reflection
```

Những điểm phải phân biệt:

1. `defineClass` **không biên dịch Java source**. Đầu vào phải là dữ liệu class-file hợp lệ theo định dạng JVM, thường bắt đầu bằng magic bytes `CA FE BA BE`.
2. Nó **định nghĩa class trong phạm vi một loader cụ thể** và trả `Class<?>` tương ứng.
3. `defineClass` chưa đồng nghĩa đã khởi tạo class, gọi `static` initializer hay thực thi constructor.
4. Việc nạp (`loading`), liên kết (`linking`), phân giải (`resolution`) và khởi tạo (`initialization`) là các giai đoạn khác nhau. Theo API, trước khi dùng class được định nghĩa, lớp cần được liên kết; `loadClass(name, true)` hoặc `resolveClass` có vai trò liên quan tới linking.
5. Không được gọi `defineClass` lặp lần thứ hai cùng tên nhị phân trên **cùng defining loader**, vì JVM không cho cùng loader định nghĩa lại một class theo tên đó.

## 2. Các overload quan trọng của ClassLoader.defineClass

`ClassLoader` công bố các overload **`protected final`**; mã Java ứng dụng bên ngoài subclass không được gọi trực tiếp tùy ý. Người viết custom loader thường tạo một subclass và gọi method từ bên trong nó.

| Chữ ký rút gọn | Nguồn bytecode | Ý nghĩa |
| --- | --- | --- |
| `defineClass(String name, byte[] b, int off, int len)` | Vùng của mảng byte | Tạo class với protection domain mặc định. |
| `defineClass(String name, byte[] b, int off, int len, ProtectionDomain pd)` | Vùng của mảng byte | Tạo class với protection domain được chỉ định, hoặc mặc định khi `pd == null`. |
| `defineClass(String name, ByteBuffer b, ProtectionDomain pd)` | Phần còn lại của buffer | Tạo class từ vùng `position()` tới `limit()`, cùng quy tắc tên/package/protection domain. |
| `defineClass(byte[] b, int off, int len)` | Mảng không có tên rõ ràng | **Đã deprecated**; tránh dùng, hãy truyền binary name dự kiến. |

Ví dụ signatures thực của API (đặt ở đây như **minh họa khai báo**, không thể copy thành một class độc lập):

```java
protected final Class<?> defineClass(
        String name, byte[] b, int off, int len);

protected final Class<?> defineClass(
        String name, byte[] b, int off, int len,
        java.security.ProtectionDomain protectionDomain);

protected final Class<?> defineClass(
        String name, java.nio.ByteBuffer b,
        java.security.ProtectionDomain protectionDomain);
```

Giá trị trả về là đối tượng `Class<?>` của **kiểu vừa được tạo**, chứ không phải instance `new Gadget()`. Để tạo instance, code có thể gọi constructor phù hợp sau khi class đã sẵn sàng.

### Tại sao protected final?

- **`protected`:** chỉ code được cấp quyền truy cập theo quy tắc Java (ví dụ subclass ở ngữ cảnh thích hợp) mới gọi method này. Không biến `defineClass` thành public API tùy ý cho mọi caller.
- **`final`:** subclass không override cơ chế JVM định nghĩa class; subclass có thể **override `findClass`** để quyết định cách tìm bytes, rồi gọi `defineClass`.
- Nếu ứng dụng cần API public trên loader tự viết, hãy đưa ra method wrapper có tên rõ ràng, kiểm soát nguồn bytes và lifetime. Không tìm cách bypass encapsulation JDK bằng reflection vào `ClassLoader#defineClass`.

## 3. Dữ liệu byte[]: offset và length là một đoạn cụ thể

Với overload dùng `byte[]`:

```java
defineClass("demo.Gadget", bytes, offset, length)
```

- `name`: **binary name kỳ vọng** của class, ví dụ `demo.Gadget`; với member class ví dụ `demo.Outer$Inner`.
- `bytes`: mảng chứa bytecode của **một class file hoàn chỉnh**.
- `offset`: index bắt đầu trong mảng.
- `length`: số byte class file cần đọc, **không phải index kết thúc**.

Điều kiện cơ bản:

```text
offset >= 0
length >= 0
offset + length <= bytes.length
```

Với một mảng chỉ chứa class file, truyền `0` và `bytes.length`. Nếu bytes được đặt bên trong buffer/mảng có header hoặc dữ liệu trước/sau, truyền đúng đoạn chứa class file; tuyệt đối không định nghĩa cả header tùy biến như thể nó là class file.

Ví dụ:

```java
byte[] classBytes = ...;         // có bytecode JVM hoàn chỉnh
byte[] envelope = new byte[classBytes.length + 4];
System.arraycopy(classBytes, 0, envelope, 2, classBytes.length);

// Bỏ qua 2 byte prefix và 2 byte suffix:
Class<?> type = defineClass(
        "demo.Gadget", envelope, 2, classBytes.length);
```

Đây là **snippet bên trong subclass của `ClassLoader`**; biến `classBytes` cần được cung cấp bởi mã tải class file. Một overload nhận `ProtectionDomain` khi cần gắn mã nguồn/chứng chỉ/chính sách phù hợp.

Nếu `off` hoặc `len` âm, hoặc vượt ra khỏi mảng, API ném `IndexOutOfBoundsException`. Nếu đoạn bytes không biểu diễn class file hợp lệ, có thể ném `ClassFormatError`. Một file có header `CAFEBABE` vẫn có thể sai cấu trúc ở phần khác, nên không đủ để chỉ kiểm tra bốn byte đầu.

## 4. ByteBuffer: dùng chính xác vùng position đến limit

Overload:

```java
defineClass("demo.Gadget", byteBuffer, protectionDomain)
```

sử dụng bytecode trong **vùng còn lại** của buffer, xác định bởi `position()` và `limit()`. Nó hỗ trợ heap buffer, direct buffer, buffer slice và read-only buffer (miễn dữ liệu hợp lệ).

Với cấu hình:

```text
capacity = 1200
position = 5
limit = 1105

Vùng bytes dùng cho defineClass: [5, 1105)
Số byte dùng: remaining() = 1100
```

Đừng dùng `capacity()` hay toàn bộ backing array để suy ra dữ liệu class file. Nếu buffer vừa được ghi bằng các lời gọi `put`, cần chuyển sang trạng thái đọc, thường bằng `flip()`; nếu có prefix phải tăng `position` đúng và chọn `limit` sau byte cuối.

Ví dụ đọc bytes chứa prefix/suffix:

```java
ByteBuffer buffer = ByteBuffer.allocateDirect(classBytes.length + 2);
buffer.put((byte) 1);
buffer.put(classBytes);
buffer.put((byte) 2);
buffer.flip();
buffer.position(1);
buffer.limit(1 + classBytes.length);

// ClassLoader subclass:
Class<?> clazz = defineClass("demo.Gadget", buffer, null);
```

`ByteBuffer` chỉ mô tả **nguồn bytes**, không cho phép JVM tự hiểu “một file Java” hay “nhiều class files” được nối chung. Vùng truyền vào vẫn phải chứa đúng một class file theo định dạng JVMS.

**Cẩn thận với vị trí buffer sau gọi:** tài liệu `ClassLoader#defineClass(String, ByteBuffer, ProtectionDomain)` mô tả kết quả tương đương việc lấy `remaining()` bytes rồi gọi overload `byte[]`. Không nên xây logic phụ thuộc vào **việc `position()` trên object buffer đầu vào có chắc chắn bị thay đổi hay không** giữa các implementation/đường đi thực thi. Trên OpenJDK 21 của ví dụ mục 6, vị trí buffer đầu vào vẫn giữ nguyên. Nếu caller cần dùng buffer sau đó, hãy tạo `duplicate()` hoặc `slice()`, định vị rõ giới hạn và tự quản lý vị trí.

**Không mặc định “direct ByteBuffer luôn zero-copy và nhanh hơn”:** API định nghĩa kết quả class loading, không hứa sẽ tránh copy bytecode hay ưu việt trong mọi trường hợp. Chọn `byte[]` hay `ByteBuffer` theo nguồn dữ liệu sẵn có, tính rõ ràng và phép đo thực tế.

## 5. Tên lớp: binary name phải khớp bytecode

Một class file chứa tên nội bộ của class do nó định nghĩa. Giá trị `name` trong `defineClass` là **binary name dự kiến** và khi không null phải phù hợp tên thật của class file.

```text
Source type:       demo.Gadget
Binary name:       demo.Gadget
JVM internal name: demo/Gadget
Resource path:     demo/Gadget.class
```

Đúng:

```java
defineClass("demo.Gadget", bytes, 0, bytes.length);
```

Sai:

```java
defineClass("demo.Other", bytes, 0, bytes.length);   // bytes thật là demo.Gadget
defineClass("demo/Gadget", bytes, 0, bytes.length);  // internal name
defineClass("demo/Gadget.class", bytes, 0, bytes.length); // resource path
```

Trong các trường hợp tên không khớp class file, API có thể ném **`NoClassDefFoundError`**. Một `NoClassDefFoundError` trong tình huống này **không có nghĩa rằng file class vắng mặt**: file có thể đọc được nhưng tên binary truyền vào sai so với metadata trong bytecode.

`name == null` được một số overload cho phép, nhưng **nên cung cấp tên dự kiến** để hỗ trợ kiểm tra nhất quán và thực thi quy tắc package/certificate chính xác. Đừng dùng tên null để lẩn tránh ràng buộc bảo mật hay cấu trúc loader.

## 6. Thực hành đầy đủ trên Java 17+: byte[] và ByteBuffer

Ví dụ này tạo **hai class runtime cùng binary name** bằng hai loader riêng, sử dụng đúng bytes của `demo.Gadget`. Nó đồng thời quan sát initialization, lần nạp lại, và ba dạng lỗi. Không yêu cầu thư viện ngoài JDK.

Cấu trúc:

```text
src/demo/Gadget.java
src/app/DefineDemo.java
model/              (output javac cho Gadget)
app/                (output javac cho DefineDemo)
```

### File src/demo/Gadget.java

```java
package demo;

public class Gadget {
    static {
        System.out.println("Gadget initialized");
    }

    public String ping() {
        return "pong";
    }
}
```

### File src/app/DefineDemo.java

```java
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public class DefineDemo {
    static class BytesLoader extends ClassLoader {
        BytesLoader() {
            super(ClassLoader.getPlatformClassLoader());
        }

        Class<?> fromArray(String name, byte[] bytes, int offset, int length) {
            return defineClass(name, bytes, offset, length);
        }

        Class<?> fromBuffer(String name, ByteBuffer bytes) {
            return defineClass(name, bytes, null);
        }
    }

    public static void main(String[] args) throws Exception {
        byte[] image = Files.readAllBytes(
                Path.of("model/demo/Gadget.class"));

        byte[] padded = new byte[image.length + 4];
        System.arraycopy(image, 0, padded, 2, image.length);

        BytesLoader arrayLoader = new BytesLoader();
        Class<?> first = arrayLoader.fromArray(
                "demo.Gadget", padded, 2, image.length);

        System.out.println("array defined: " + first.getName());
        System.out.println("array loader owned: "
                + (first.getClassLoader() == arrayLoader));
        System.out.println("loadClass returns same: "
                + (arrayLoader.loadClass("demo.Gadget") == first));
        System.out.println("initialize next:");
        Object gadget = first.getDeclaredConstructor().newInstance();
        System.out.println("method: "
                + first.getMethod("ping").invoke(gadget));

        ByteBuffer direct = ByteBuffer.allocateDirect(image.length + 2);
        direct.put((byte) 1).put(image).put((byte) 2).flip();
        direct.position(1);
        direct.limit(1 + image.length);

        BytesLoader bufferLoader = new BytesLoader();
        Class<?> second = bufferLoader.fromBuffer("demo.Gadget", direct);
        System.out.println("buffer defined: " + second.getName());
        System.out.println("buffer remaining preserved: "
                + (direct.position() == 1));
        System.out.println("same binary name: "
                + first.getName().equals(second.getName()));
        System.out.println("same runtime type: " + (first == second));

        try {
            arrayLoader.fromArray("demo.Gadget", image, 0, image.length);
        } catch (LinkageError expected) {
            System.out.println("duplicate define: LinkageError");
        }

        try {
            new BytesLoader().fromArray(
                    "demo.Other", image, 0, image.length);
        } catch (NoClassDefFoundError expected) {
            System.out.println("wrong expected name: NoClassDefFoundError");
        }

        try {
            new BytesLoader().fromArray(
                    "demo.Bad", new byte[]{1, 2, 3}, 0, 3);
        } catch (ClassFormatError expected) {
            System.out.println("invalid bytes: ClassFormatError");
        }
    }
}
```

**Biên dịch/chạy từ thư mục cha** chứa `src` (không đưa `model` vào classpath ứng dụng):

```bash
javac -d model src/demo/Gadget.java
javac -d app src/app/DefineDemo.java
java -cp app DefineDemo
```

**Đầu ra đã kiểm chứng trên OpenJDK 21:**

```text
array defined: demo.Gadget
array loader owned: true
loadClass returns same: true
initialize next:
Gadget initialized
method: pong
buffer defined: demo.Gadget
buffer remaining preserved: true
same binary name: true
same runtime type: false
duplicate define: LinkageError
wrong expected name: NoClassDefFoundError
invalid bytes: ClassFormatError
```

### Tại sao kết quả như vậy?

- `arrayLoader` định nghĩa `demo.Gadget` từ **đoạn** `padded[2 ... 2+image.length)`; `bufferLoader` định nghĩa class có cùng tên từ vùng `position..limit` trong một **direct ByteBuffer**.
- Hai `Class<?>` có cùng `getName()` nhưng **không cùng identity**, vì defining loaders khác nhau. JVM định danh một class/interface thông thường bằng cặp `(binary name, defining loader)`.
- Sau khi `defineClass` trả về, `first.getName()` và `loadClass("demo.Gadget")` không chạy static initializer. Lần `getDeclaredConstructor().newInstance()` tạo instance làm `Gadget initialized` xuất hiện.
- Overload `loadClass` của base class kiểm tra lớp đã tải trước, nên nạp lại bằng **cùng loader** trả cùng `Class<?>`.
- Tái `defineClass` trực tiếp một class cùng binary name trên `arrayLoader` vi phạm định danh và gây `LinkageError`. Mã thật không nên bắt lỗi này rồi thử define lại lần nữa.
- Truyền `demo.Other` cho bytecode thực tế của `demo.Gadget` gây `NoClassDefFoundError` do **wrong name**, không phải “không tìm thấy file”.
- Bytes `{1,2,3}` không phải class file, nên gây `ClassFormatError`.
- `buffer remaining preserved: true` là **quan sát của môi trường chạy đã thử**, không là lời hứa phổ quát về việc đối tượng `ByteBuffer` đầu vào sẽ giữ nguyên position trên mọi triển khai.

Ví dụ chủ động gọi `defineClass` thông qua wrapper nhằm làm rõ hành vi; trong loader thực tế nên kết hợp với `findClass` và đường đi `loadClass` để quản lý cache, delegation và concurrent loading.

## 7. Tích hợp đúng vào findClass và loadClass

Thông thường, application code yêu cầu:

```java
Class<?> type = customLoader.loadClass("demo.Gadget");
```

`ClassLoader.loadClass(String, boolean)` mặc định (khi subclass không override) thực hiện chu trình:

```text
loadClass(binaryName)
    |
    | synchronized(getClassLoadingLock(binaryName))
    v
findLoadedClass(binaryName)
    |
    +-- đã ghi nhận -> trả lại Class<?>
    |
    +-- chưa có -> thử parent.loadClass(binaryName)
                     |
                     +-- parent tìm được -> trả Class<?> của parent
                     |
                     '-- parent không tìm được -> findClass(binaryName)
                                                  |
                                                  '-- đọc class bytes
                                                      defineClass(...)
```

Nếu tham số `resolve` của overload protected là `true`, đường đi chuẩn còn gọi `resolveClass` sau khi có `Class<?>` phù hợp.

**Điểm thiết kế:** người viết custom loader thông thường override **`findClass`**, vì `loadClass` mặc định đã quản lý `findLoadedClass`, parent delegation và đồng bộ hóa. Bạn thường không cần tự override `loadClass` để giải quyết việc đọc bytes từ nguồn mới.

Đoạn skeleton sau minh họa tích hợp (không phải chương trình độc lập vì `readBytesFor` do ứng dụng phải cung cấp):

```java
public final class RepositoryClassLoader extends ClassLoader {
    public RepositoryClassLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    protected Class<?> findClass(String binaryName)
            throws ClassNotFoundException {
        byte[] bytes = readBytesFor(binaryName);
        if (bytes == null) {
            throw new ClassNotFoundException(binaryName);
        }
        return defineClass(binaryName, bytes, 0, bytes.length);
    }

    private byte[] readBytesFor(String binaryName) {
        // Ví dụ ứng dụng thật: đọc từ archive, database hoặc bytecode generator.
        throw new UnsupportedOperationException("implement source lookup");
    }
}
```

Method `readBytesFor` cố tình chưa được triển khai; đây là **mẫu kiến trúc**, không phải ví dụ có thể chạy ngay. Trong sản phẩm thực, cần xử lý I/O, giới hạn kích thước, checksum/chữ ký, package metadata và ownership của bytes.

Nếu override `loadClass` để áp dụng child-first có chủ đích, bạn phải giữ đúng luật tái sử dụng kiểu đã load, class-loading locks, thứ tự tìm/ủy quyền, quyết định resolve và loading constraints. Gọi `defineClass` cho cùng tên trên cùng loader nhiều lần không tạo phiên bản class mới mà gây lỗi.

### Nhiều luồng cùng yêu cầu một tên

Hai thread có thể đồng thời gọi `loadClass("demo.Gadget")`. Đường đi mặc định sử dụng `getClassLoadingLock(name)` để tránh race *double define*.

Đối với custom loader muốn hỗ trợ tải nhiều tên song song, Java cung cấp `registerAsParallelCapable()` với quy tắc đăng ký khi class loader subclass được khởi tạo. Không tự bật song song rồi thay synchronization bằng biến boolean không an toàn. Kiểu lớp do JVM định nghĩa phải được quản lý nhất quán khi nhiều thread cùng tìm.

## 8. ProtectionDomain, package và code source

Các overload có `ProtectionDomain` cho phép gắn thông tin miền bảo vệ với class runtime:

```java
defineClass(binaryName, bytes, 0, bytes.length, protectionDomain);
defineClass(binaryName, byteBuffer, protectionDomain);
```

`protectionDomain == null` có nghĩa dùng protection domain mặc định của overload tương ứng. `ProtectionDomain` có thể mang `CodeSource`, certificate và các thông tin truy xuất/chính sách liên quan. Đây là metadata và cơ chế bảo vệ nền tảng; nó không phải cờ đơn giản “bytes tin cậy” hay công cụ an toàn để chạy bytecode độc hại trong cùng JVM.

Quan hệ package:

- `defineClass` có thể định nghĩa `Package` trong loader nếu package tương ứng chưa được xác định ở loader đó.
- **Runtime package** được xác định bằng tên package kết hợp với defining loader; hai loader định nghĩa cùng chuỗi tên package vẫn là hai runtime packages khác nhau.
- Nếu dùng các overload có domain/certificate, **class đầu tiên trong package quyết định bộ certificates** mà các class tiếp theo trong cùng package/loader phải phù hợp. Vi phạm có thể gây `SecurityException`.
- Package sealing và thông tin version lấy từ manifest là trách nhiệm hệ thống loader/packaging phù hợp. `URLClassLoader` xử lý nhiều chi tiết này cho JAR chuẩn; một loader chỉ gọi `defineClass` từ raw bytes không tự động mô phỏng mọi quy tắc JAR manifest.
- Loader không được tùy ý định nghĩa các class có binary name bắt đầu bằng `java.` như class ứng dụng; API có ràng buộc bảo mật và có thể ném `SecurityException`.

Không sử dụng `defineClass(null, ...)` để tránh kiểm tra binary name, certificates hoặc namespace. Khi đã biết tên lớp, hãy luôn cung cấp tên đúng.

## 9. Lỗi và cách chẩn đoán

| Triệu chứng / lỗi | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| `ClassFormatError` | Bytes không phải class file hợp lệ, bị cắt hoặc hỏng cấu trúc. | Kiểm tra magic, class file length, nguồn compile/generator và cấu trúc JVM. |
| `UnsupportedClassVersionError` | Class file được biên dịch cho phiên bản JVM mới hơn runtime hỗ trợ. | So sánh `javac --release`, major version bytecode và JDK runtime. Đây là subclass của `ClassFormatError`. |
| `NoClassDefFoundError: wrong name` | `name` truyền cho `defineClass` không khớp binary name trong class file. | Dùng dấu chấm cho package và `$` cho member class, kiểm tra `javap -v`. |
| `IndexOutOfBoundsException` | `offset`/`length` không nằm trong `byte[]`. | Kiểm tra phạm vi, tránh nhầm length với end index. |
| `LinkageError` khi define trùng | Cùng defining loader đã có class cùng binary name. | Đi qua `loadClass`/`findLoadedClass`, không cố define lại. |
| `SecurityException` | Vi phạm namespace `java.` hoặc bộ certificates không nhất quán trong package. | Chọn tên hợp lệ, xử lý code source và package metadata đúng. |
| `ClassNotFoundException` | `findClass` không tìm được nguồn bytecode và loader chain không có class đó. | Kiểm tra source lookup, binary name và parent delegation. |
| `NoClassDefFoundError` ở lần sử dụng sau | Có thể class phụ thuộc bị thiếu hoặc initialization trước đó thất bại. | Xem message và `Caused by`, phân biệt wrong name với thiếu dependency. |
| `VerifyError` | Bytecode vi phạm ràng buộc verification. | Kiểm tra generator, stack map frames, typing và compiler target. |
| `ClassCastException` giữa hai lớp cùng tên | Hai defining loaders riêng sinh hai runtime types khác nhau. | Chia sẻ API qua parent, kiểm tra `clazz.getClassLoader()`. |

Một số lỗi liên kết có thể chỉ biểu hiện khi class được **liên kết hoặc sử dụng**, không nhất thiết ngay trong `defineClass`. Không dùng thành công của `defineClass` như chứng chỉ xác minh toàn bộ phụ thuộc, quyền truy cập, tính đúng đắn logic hay readiness của ứng dụng.

### Phân biệt tên và nguồn class

- `binary name`: `demo.Gadget` → tham số `defineClass`.
- `internal name`: `demo/Gadget` → bên trong class-file constant pool.
- `resource path`: `demo/Gadget.class` → đường dẫn dùng để tìm bytes.
- `Class.getName()` trả tên thích hợp để quan sát lớp thông thường; `Class.getClassLoader()` cho biết defining loader của lớp đó.

Đừng truyền resource path cho `defineClass`, cũng đừng lấy tên của file trên đĩa làm bằng chứng class file chứa cùng binary name.

## 10. Khi nào nên dùng hoặc tránh defineClass?

**Phù hợp khi** bạn đang hiện thực custom class loader tải bytecode từ repository, database, network, generator, plugin archive hoặc nguồn bytes rõ ràng khác; bạn kiểm soát việc định danh, parent, package, source và thời điểm nạp class.

**Không cần dùng khi** class đã có trong classpath/module path: `Class.forName`, `ClassLoader.loadClass` hoặc reflection thường đủ. Nếu chỉ muốn thêm một JAR/directory vào một namespace riêng, `URLClassLoader` với URL và parent phù hợp đã xử lý nguồn bytecode thay bạn.

**Không dùng để “ghi đè class đang tồn tại”**: cùng binary name trên cùng defining loader không thể định nghĩa lần hai. Nhu cầu hot reload thường tạo loader mới, làm sạch reference tới loader cũ hoặc dùng API instrumentation có giới hạn riêng. Bytecode từ nguồn không tin cậy có thể ảnh hưởng an toàn JVM/app; cần áp dụng kiểm soát nguồn và ranh giới thực thi phù hợp, không mặc định `ProtectionDomain` bảo đảm sandbox trong mọi Java hiện đại.

Một checklist triển khai thực tế:

1. Xác thực nguồn và độ dài class bytes; chỉ nhận class-file định dạng đúng.
2. Xác định **binary name** trước khi define, kiểm tra tên khớp class bytes.
3. Chọn parent loader phù hợp để tránh trùng API và sai class identity.
4. Dùng `findClass` và đường đi `loadClass` để tránh định nghĩa trùng/race.
5. Gắn package/protection domain/certificate/code source khi bài toán yêu cầu.
6. Phân biệt lỗi đọc bytecode với lỗi linkage, verification và initialization; giữ `cause` khi wrap `IOException` thành `ClassNotFoundException`.
7. Với nguồn plugin động, quản lý vòng đời loader, cache, reference và chiến lược unload/upgrade; không giả định `close()` hay bỏ bytes làm JVM lập tức thu hồi class.

## 11. Câu hỏi tự kiểm tra

1. **`defineClass` có nhận Java source từ chuỗi hay không?**  
   Không. Nó nhận bytes của JVM class file hợp lệ; việc biên dịch source cần compiler hoặc công cụ tạo bytecode riêng.
2. **Khác nhau giữa `off=2,len=100` và `off=2,len=102`?**  
   Giá trị `len` là số byte kể từ `off`. Nếu class file dài 100 byte trong envelope, dùng `2,100`; truyền `102` sẽ đọc thừa/ngoài phạm vi tùy độ dài mảng.
3. **`ByteBuffer` có 200 byte capacity nhưng `position=10,limit=110` thì bao nhiêu byte được dùng?**  
   100 byte, nằm trong khoảng `[10,110)`.
4. **Cùng `demo.Gadget` có thể được định nghĩa bởi hai loader khác nhau không?**  
   Có. Chúng là hai kiểu runtime riêng vì cặp `(binary name, defining loader)` khác nhau.
5. **Định nghĩa cùng tên hai lần ở cùng loader sẽ tạo bản cập nhật mới?**  
   Không. Nó vi phạm quy tắc class identity, thường ném `LinkageError`.
6. **Khi truyền `name="demo.Other"` nhưng bytes chứa `demo.Gadget`, lỗi nào phù hợp?**  
   `NoClassDefFoundError` vì tên dự kiến không khớp bytes.
7. **Chỉ gọi `defineClass` có chạy `static { ... }` trong class hay không?**  
   Không tự động thực hiện initialization; hành động active use (ví dụ gọi constructor) mới thường kích hoạt init theo quy tắc JVM.
8. **Có nên override `loadClass` để tải bytes từ file không?**  
   Thường chỉ cần override `findClass`; `loadClass` mặc định đã xử lý kiểm tra lớp ghi nhận, parent delegation và đồng bộ.
9. **Lợi ích của overload `ByteBuffer` là gì?**  
   Truyền trực tiếp nguồn dữ liệu đã biểu diễn bằng buffer; vùng đọc xác định bởi `position/limit`. Không có bảo đảm hiệu năng/zero-copy tổng quát.
10. **ProtectionDomain có biến bytecode lạ thành mã an toàn tuyệt đối không?**  
    Không. Nó gắn metadata bảo vệ và có các quy tắc package/certificate; thiết kế ranh giới an toàn cần cơ chế khác phù hợp.

## 12. Tài liệu chuẩn

- [Java SE 21 API — ClassLoader.defineClass](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): overload `byte[]`, `ByteBuffer`, `ProtectionDomain`, tên và exceptions.
- [Java SE 21 API — SecureClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/SecureClassLoader.html): overload dùng `CodeSource`.
- [Java SE 21 API — ByteBuffer](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/ByteBuffer.html): `position`, `limit`, `remaining`, `flip`, `slice`, `duplicate`.
- [JVMS 4 — Class File Format](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html): class-file structure và version.
- [JVMS 5.3 — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3): định danh class bằng binary name và defining loader.
- [JLS 12 — Execution](https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html): nạp, liên kết, khởi tạo class.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-api-005">resolveClass và khác biệt với khởi tạo lớp</a>

<details>
<summary>Xem chi tiết</summary>

# `resolveClass` và sự khác biệt với khởi tạo lớp (class initialization)

> **Ngữ cảnh:** Java Core → ClassLoader → API nạp lớp. Bài này giải thích chính xác `ClassLoader.resolveClass(Class<?>)` làm gì, nó liên hệ thế nào với `loadClass`/`defineClass`, và vì sao **liên kết (linking)** một lớp không đồng nghĩa với **khởi tạo (initialization)**. Nội dung theo hợp đồng Java SE và JVM Specification; ví dụ độc lập dùng Java 17+.

## 1. Câu trả lời trọng tâm

Trong một `ClassLoader` tùy biến, đoạn mã:

~~~java
Class<?> type = loadClass("example.Widget");
resolveClass(type);
~~~

có nghĩa **yêu cầu nạp `example.Widget` nếu chưa được nạp, rồi liên kết lớp được trả về nếu cần**. Nó **không** có nghĩa “gọi static initializer của `Widget`”. Vì `resolveClass` là `protected final`, đoạn mã này chỉ hợp lệ **trong thân một subclass của `ClassLoader`** (hoặc ngữ cảnh Java được phép truy cập protected member); đây là **snippet minh họa**, không phải chương trình độc lập.

Để yêu cầu **khởi tạo** một lớp bằng API rõ ràng, có thể sử dụng:

~~~java
Class.forName("example.Widget", true, suitableLoader);
~~~

với `suitableLoader` là loader phù hợp. Overload ba tham số với `initialize=true` yêu cầu khởi tạo class/interface; `false` không yêu cầu khởi tạo. Lúc khởi tạo, các giá trị `ConstantValue` được áp dụng và phương thức `<clinit>` (nếu có) được thực thi theo quy tắc JVM/JLS.

**Phân biệt ngay:**

| Khái niệm/API | Ý nghĩa | Chắc chắn chạy `static { ... }` của lớp đích? |
| --- | --- | --- |
| `ClassLoader.loadClass(String)` | Tìm/trả về `Class<?>` qua loader và delegation, mặc định không request resolution bằng boolean `true` | **Không** |
| `ClassLoader.loadClass(String, boolean)` | Phương thức protected: có thể yêu cầu `resolveClass` khi `resolve=true` | **Không** |
| `ClassLoader.resolveClass(Class<?>)` | **Links** class đã có `Class<?>`; API gọi tên hơi gây hiểu nhầm | **Không** |
| `Class.forName(name, false, loader)` | Tra cứu `Class<?>` qua loader mà **không yêu cầu initialize** | **Không** |
| `Class.forName(name, true, loader)` | Tra cứu và yêu cầu **initialize** class/interface tương ứng | **Có nếu cần**, nhưng `<clinit>` có thể không tồn tại, đã chạy trước đó hoặc không có side effect |
| `new Widget()` | Tạo instance; phải bảo đảm class liên quan đã được initialize trước khi dùng | **Chạy khi là lần active use yêu cầu initialization** |
| `Widget.someStaticMethod()` | Gọi static method; kích hoạt initialization của lớp/interface khai báo method | **Chạy khi cần** |
| Đọc `Widget.someStaticNonconstantField` | Đọc field `static` không phải constant variable | **Chạy khi cần** |
| Đọc `Widget.COMPILE_TIME_CONSTANT` từ mã đã compile | Có thể dùng giá trị đã inline, không cần initialize class khai báo | **Không chỉ vì thao tác này** |

“Chạy `<clinit>`” không nên hiểu là *luôn có dòng log*. Nhiều lớp không có `<clinit>`, một lớp đã initialized sẽ không chạy lại, và static initializer không buộc phải in ra màn hình.

## 2. Ba giai đoạn của một runtime class

JVM Specification `5` định nghĩa ba nhóm công việc: **loading**, **linking**, **initialization**.

~~~text
                  LOADING
         Đọc/tạo runtime class và Class<?> token
                       |
                       v
                  LINKING
         +-----------------------------+
         | Verification                | kiểm tra class file/type safety
         | Preparation                 | tạo static fields; giá trị mặc định
         | Resolution                  | giải symbolic references khi cần
         +-----------------------------+
                       |
                       v
                INITIALIZATION
        gán ConstantValue theo quy tắc JVMS
        chạy <clinit> nếu được định nghĩa
        ghi trạng thái initialized / erroneous
~~~

Đây là sơ đồ **logic**; JVM có thể resolve các symbolic references **lười hoặc sớm** trong phạm vi đặc tả. Không suy ra mọi reference đã được resolve ngay khi `resolveClass` trả về.

### 2.1. Loading

Loading tìm binary representation, tạo runtime class/interface hoặc array class và có thể yêu cầu thêm các class khác. Với class thông thường, runtime identity là **(binary name, defining class loader)**.

Ví dụ hai custom loaders có thể cùng trả về tên `org.example.Plugin` nhưng là hai runtime types khác nhau nếu **mỗi loader tự define** class đó. Ngược lại, khi hai loaders đều delegate tới cùng parent, kết quả có thể là đúng một `Class<?>`.

`ClassLoader.loadClass(name)` thường trả về kiểu đã tồn tại hoặc tìm kiểu mới, và **không được lấy thành công của lời gọi làm bằng chứng initializer đã chạy**.

### 2.2. Linking

Linking gồm:

1. **Verification:** bảo đảm dữ liệu class file hợp lệ và tuân thủ các ràng buộc type safety. Một class file sai có thể dẫn tới `VerifyError` hoặc các `LinkageError` phù hợp.
2. **Preparation:** tạo các static fields và đưa chúng về giá trị **mặc định** (`0`, `false`, `null`...), **chưa** thực hiện biểu thức khởi tạo Java.
3. **Resolution:** chuyển tham chiếu tượng trưng trong runtime constant pool thành tham chiếu thực; có thể gồm class, field, method, method handle, dynamic constant, call site và kiểm tra accessibility/loader constraints. JVM có quyền lựa chọn thời điểm giải quyết trong giới hạn quy định.

Tên API **`resolveClass`** dễ gây hiểu lầm vì nghe giống chỉ thực hiện phần *resolution*. Tài liệu `ClassLoader` nói rõ: **“Links the specified class.”** Nghĩa là mục đích của API này là **linking**, không phải “ép giải toàn bộ symbolic references ngay lập tức”.

### 2.3. Initialization

Initialization thực hiện công việc mang tính hành vi:

- Gán giá trị từ `ConstantValue` cho các field thích hợp trước khi chạy `<clinit>`.
- Chạy code của static field initializers/static initializer blocks mà compiler kết hợp vào `<clinit>` nếu có.
- Phối hợp thread khác đang chờ initialization của cùng runtime class.
- Đảm bảo các điều kiện liên quan đến superclass và những superinterfaces có default methods, tùy ngữ cảnh class/interface theo thuật toán JLS.
- Đánh dấu initialized khi thành công, hoặc **erroneous** nếu thất bại.

JVM bắt buộc class phải được verify/prepare trước khi initialize; tuy nhiên một JVM vẫn có thể resolve thêm reference sau khi class đã initialized. Vì vậy “đã initialize” cũng **không** chứng minh mọi symbolic reference của class đã được resolve từ trước.

## 3. Hợp đồng chính xác của `ClassLoader.resolveClass`

Chữ ký API Java SE:

~~~java
protected final void resolveClass(Class<?> c)
~~~

Các đặc điểm:

- **`protected`:** một đoạn `main` bình thường không thể tùy ý gọi `someLoader.resolveClass(c)` từ bên ngoài. Class loader tùy biến có thể gọi bên trong subclass; Java access rules vẫn áp dụng.
- **`final`:** subclass **không được override** hành vi `resolveClass`. Nếu muốn kiểm soát cách nạp, override điểm mở rộng thích hợp như `findClass`, hoặc `loadClass` khi thật sự cần chính sách delegation khác.
- **`void`:** phương thức không trả `Class`; kết quả class token vốn đã được truyền vào.
- **Đối số `null`:** ném `NullPointerException`, không biến thành `ClassNotFoundException`.
- **Gọi khi class đã linked:** chỉ trả về bình thường; không có nghĩa static initializer sẽ chạy lại.
- **Gọi với class chưa linked:** VM thực hiện liên kết theo quy định. Các lỗi verification/linkage vẫn có thể được phát hiện, nhưng thời điểm lỗi của một symbolic reference cụ thể phụ thuộc quy tắc resolution.
- **Không phải public API để initialize:** không có tham số `initialize` và cũng không có “`resolveClass(true)`”.

Lưu ý có **hai lớp tên gọi**:

- `ClassLoader.resolveClass(Class<?>)` = API từ loader để **link class**.
- **JVM resolution (`JVMS 5.4.3`)** = hoạt động xử lý một **symbolic reference** trong constant pool. Đây là một phần của linking, nhưng không đồng nghĩa việc gọi `resolveClass` buộc tất cả references được resolve lập tức.

### 3.1. API này thường được gọi từ đâu?

Trong triển khai mặc định của `ClassLoader.loadClass(String, boolean)`, thuật toán được mô tả theo dạng:

~~~java
// Pseudocode: rút gọn thuật toán API; KHÔNG phải implementation chuẩn.
synchronized (getClassLoadingLock(name)) {
    Class<?> c = findLoadedClass(name);
    if (c == null) {
        try {
            c = (parent != null)
                    ? parent.loadClass(name)
                    : findBootstrapClassOrNull(name);
        } catch (ClassNotFoundException ignored) {
            // Tiếp tục tìm trong loader hiện tại.
        }
        if (c == null) {
            c = findClass(name);
        }
    }
    if (resolve) {
        resolveClass(c);
    }
    return c;
}
~~~

Đoạn trên chỉ giải thích **tư tưởng delegation + link**, không phải mã copy để compile: các helper nội bộ như `findBootstrapClassOrNull`, quyền truy cập biến `parent` và xử lý đặc biệt tùy JDK không phải toàn bộ API công khai cho subclass.

Điểm then chốt: cờ `resolve` là **“link class nếu yêu cầu”**, **không phải “initialize class”**. Nó không phải tham số của overload public `loadClass(String)`. Muốn kiểm soát cờ từ mã ứng dụng thì thường tạo **một phương thức của subclass** gọi protected overload, như ở ví dụ hoàn chỉnh bên dưới.

### 3.2. `loadClass(String)` mặc định và `loadClass(name, resolve)`

Theo hợp đồng `ClassLoader`, overload một tham số được triển khai qua protected overload với `resolve=false`:

~~~java
public Class<?> loadClass(String name)
        throws ClassNotFoundException {
    return loadClass(name, false);
}
~~~

Vì thế gọi `loader.loadClass("pkg.Target")` **không yêu cầu** `resolveClass` theo luồng mặc định. Tuy nhiên JVM/loader có thể đã link class trước đó hoặc liên kết sớm theo implementation; không được suy luận class chắc chắn ở trạng thái “chưa link” chỉ vì cờ `false`.

Nội dung `resolve=false` mang ý nghĩa **không chủ động yêu cầu bước link qua API này**, chứ không phải một lệnh “cấm JVM link class đến hết chương trình”. Trong khi đó `resolve=true` cũng **không** biến lời gọi thành explicit class initialization.

## 4. Ví dụ độc lập: `resolveClass` không chạy static initializer

Tệp **`LinkVsInit.java`**:

~~~java
public class LinkVsInit {
    static class Target {
        static {
            System.out.println("Target initialized");
        }

        static int answer = 42;
    }

    static final class ProbeLoader extends ClassLoader {
        ProbeLoader() {
            super(LinkVsInit.class.getClassLoader());
        }

        Class<?> loadAndLink(String binaryName)
                throws ClassNotFoundException {
            Class<?> result = loadClass(binaryName, false);
            resolveClass(result);
            return result;
        }
    }

    public static void main(String[] args) throws Exception {
        ProbeLoader loader = new ProbeLoader();

        System.out.println("1. before load/link");
        Class<?> token = loader.loadAndLink("LinkVsInit$Target");
        System.out.println("2. after link: " + token.getName());

        Class.forName("LinkVsInit$Target", false, loader);
        System.out.println("3. after forName(false)");

        Class.forName("LinkVsInit$Target", true, loader);
        System.out.println("4. after forName(true)");

        System.out.println("5. answer=" + Target.answer);
    }
}
~~~

Biên dịch/chạy bằng JDK 17 trở lên:

~~~bash
javac --release 17 LinkVsInit.java
java -cp . LinkVsInit
~~~

**Stdout:**

~~~text
1. before load/link
2. after link: LinkVsInit$Target
3. after forName(false)
Target initialized
4. after forName(true)
5. answer=42
~~~

Diễn giải:

1. `LinkVsInit.Target` là lớp lồng tĩnh; binary name dùng trong API là `LinkVsInit$Target`.
2. `ProbeLoader` có parent là loader nạp class ứng dụng `LinkVsInit`. Khi gọi `loadClass`, nó **delegate** nên `Target` thường được **định nghĩa bởi application loader**, không phải bởi chính `ProbeLoader`.
3. `resolveClass(token)` yêu cầu link, không kích hoạt `static{}`: vì thế thông báo “Target initialized” **chưa xuất hiện** trước dòng số 3.
4. `Class.forName(..., false, loader)` không yêu cầu init; dòng số 3 vẫn in trước thông báo từ block static.
5. `Class.forName(..., true, loader)` yêu cầu initialization lần đầu; block static in “Target initialized”, initializer field đặt `answer=42`.
6. Lần truy cập `Target.answer` sau đó dùng runtime class đã initialized nên không in thông báo lần thứ hai.

**Điểm kiểm chứng:** Thành công của `resolveClass` **không làm class initialized**, kể cả khi class đã linked. Việc ba lệnh truy xuất dùng đúng cùng một `Class<?>` do parent định nghĩa giúp thí nghiệm tránh nhiễu do class identity khác.

Nếu chạy chương trình lại trong **một JVM mới**, block sẽ lại chạy ở runtime class mới của tiến trình mới. Nếu chạy nhiều thao tác trong **cùng JVM và cùng defining loader**, initialization hoàn tất sẽ không lặp lại.

## 5. Nhận diện thao tác kích hoạt khởi tạo

Một đối tượng `Class<?>` mô tả kiểu runtime; hành động dùng kiểu mới quyết định có yêu cầu khởi tạo hay không. Với lớp chưa được khởi tạo:

- `T.class`, `loader.loadClass(name)` và `Class.forName(name, false, loader)` có thể lấy `Class<?>` nhưng không chủ động chạy `<clinit>`.
- `resolveClass(type)` yêu cầu **linking**, vẫn không kích hoạt `<clinit>`. Thí nghiệm `LinkVsInit` ở mục 4 kiểm chứng trực tiếp.
- `Class.forName(name, true, loader)`, `new T()`, gọi static method của `T` và đọc static field không phải constant variable là những thao tác có thể đòi initialization.
- Đọc compile-time constant như `static final int VALUE = 7` có thể được compiler in-line nên không tự gây khởi tạo. Với tên array class, `Class.forName` không khởi tạo element class chỉ do phép tìm mảng.

Khi chẩn đoán, hãy kiểm tra **thao tác active use đầu tiên** của class; dòng log nạp class hoặc kết quả `resolveClass` thành công chưa đủ để kết luận initializer đã chạy.

## 6. Tại sao “đã link” không có nghĩa “đã resolve mọi reference”?

Giả sử class `A` có bytecode gọi `B.work()`. Runtime constant pool của `A` chứa symbolic reference đến class/method liên quan. JVM có thể:

- Xử lý một số symbolic reference ngay khi verify/link class.
- Hoãn việc resolve đến khi instruction liên quan thực sự được thực thi.
- Nạp class phụ trợ trong lúc verify hoặc liên kết, nhưng vẫn chưa initialize chúng.
- Áp dụng loader constraints và access checks khi resolution yêu cầu.

JVMS cho phép **eager hoặc lazy resolution**, nhưng vẫn có ràng buộc về vị trí có thể ném lỗi khi một reference được sử dụng và về một số loại dynamic constants/call sites. Do đó không thể đưa ra lời hứa chung:

> “`resolveClass(A.class)` thành công ⇒ mọi method/field/class được `A` tham chiếu đều đã tồn tại, kiểm tra quyền thành công và chắc chắn các câu lệnh dùng chúng sẽ không ném `LinkageError`.”

Lời hứa trên **sai**. `resolveClass` là API link class; không phải bộ quét dependency transitive hay validator tuyệt đối mọi call site. `NoClassDefFoundError`, `NoSuchMethodError`, `IllegalAccessError` hoặc lỗi linkage khác vẫn có thể xuất hiện về sau ở điểm sử dụng một reference.

### 6.1. Loading, verifying, preparing, resolving phụ thuộc nhau nhưng không nhất thiết đồng thời

Một JVM tuân thủ đặc tả đảm bảo:

- Class được load trước khi link.
- Class được verify và prepare hoàn tất trước khi initialize.
- Resolution có thể còn diễn ra **sau thời điểm initialization** đối với một số symbolic references theo chiến lược implementation và giới hạn JVMS.
- Nếu lỗi resolution được phát hiện, nó phải được ném ở điểm hành động của chương trình có thể trực tiếp hoặc gián tiếp cần linkage đó.
- Class có thể đã được load bởi loader A thông qua delegation nhưng **defining loader** thực tế là B. Loading constraints đảm bảo type safety trong những tương tác liên loader.

Hệ quả thực hành: một smoke test chỉ gọi `loadClass(..., true)` hoặc `resolveClass` không thay thế được **integration test thực thi** các API cần dùng. Việc tìm được class chưa chắc phù hợp ABI, module access hay cách sử dụng trong runtime của ứng dụng.

## 7. Khi tự viết `ClassLoader`: đặt `resolveClass` ở đâu?

### 7.1. Trường hợp thường gặp: override `findClass`, giữ `loadClass` mặc định

Nếu bạn chỉ cần bổ sung nguồn bytecode, hãy ưu tiên override `findClass(String)`. Logic parent-first, cache `findLoadedClass`, khóa nạp lớp và cờ `resolve` được `ClassLoader.loadClass` mặc định đảm nhận.

Ví dụ **minh họa phần thân subclass**:

~~~java
@Override
protected Class<?> findClass(String name)
        throws ClassNotFoundException {
    byte[] bytes = obtainClassBytes(name); // hàm riêng bạn phải cung cấp
    return defineClass(name, bytes, 0, bytes.length);
}
~~~

Đây **không phải chương trình Java chạy độc lập** vì `obtainClassBytes` là hàm giả định dùng để đọc bytes. Ý tưởng chính: `findClass` **định nghĩa** class rồi trả về. Nếu caller đi qua `loadClass(name, true)`, implementation mặc định sẽ gọi `resolveClass`. Không bắt buộc gọi `resolveClass` bên trong `findClass` để mọi thứ “đúng chuẩn”.

### 7.2. Khi override `loadClass(String, boolean)`

Một loader child-first hoặc có chính sách package riêng phải xử lý đúng cache, lock và resolution:

~~~java
@Override
protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
        Class<?> type = findLoadedClass(name);

        if (type == null) {
            if (isOwnedPackage(name)) {
                try {
                    type = findClass(name);
                } catch (ClassNotFoundException ex) {
                    type = super.loadClass(name, false);
                }
            } else {
                type = super.loadClass(name, false);
            }
        }

        if (resolve) {
            resolveClass(type);
        }
        return type;
    }
}
~~~

Đây là **pseudocode Java** trong subclass: `isOwnedPackage` và `findClass` phụ thuộc triển khai, nên không được copy nguyên vào dự án chưa định nghĩa hai phương thức ấy. Phần chính cần thấy là **`resolveClass(type)` chỉ thực hiện khi cờ `resolve` là `true`**, sau khi có `Class<?>`.

Các nguyên tắc để tránh hỏng loader:

1. Dùng `findLoadedClass(name)` để không cố `defineClass` lặp lại cùng binary name.
2. Đồng bộ theo `getClassLoadingLock(name)` như contract của `ClassLoader`; nếu muốn **parallel capable** phải đăng ký và bảo đảm loader hỗ trợ đúng cơ chế.
3. Giữ consistency: cùng một name đã được loader ghi nhận initiating loader phải trả về đúng `Class` đó trong những lần gọi tương ứng.
4. Đừng định nghĩa lại classes của core Java hoặc shared interfaces thuộc host/plugin boundary theo chính sách child-first không kiểm soát.
5. Hiểu `resolve=true` chỉ yêu cầu **link**, không chạy `<clinit>`.
6. Xử lý `ClassNotFoundException` (không tìm class) khác với `LinkageError` (lỗi binary/verification/constraints), và đừng nuốt lỗi linkage để “thử loader khác” một cách mù quáng.
7. Không gọi `Class.forName(..., true, ...)` tùy tiện trong code nạp plugin nếu chủ đích chỉ kiểm tra sự hiện diện của class; nó có thể chạy side effects static.

### 7.3. `defineClass` và `resolveClass` là hai việc riêng

`defineClass(String, byte[], int, int)` chuyển class bytes thành một runtime `Class<?>` được **defining loader** tạo. `resolveClass(clazz)` là lời gọi bổ sung để link class đó nếu cần. Một số custom loaders chủ động gọi cả hai:

~~~java
Class<?> defined = defineClass(name, bytes, 0, bytes.length);
resolveClass(defined);
return defined;
~~~

Snippet trên chỉ hợp lệ **trong một `ClassLoader` subclass** có sẵn `name` và `bytes`. Nó không tạo object instance và **không** gọi `<clinit>`. Nếu class có initializer thực hiện I/O, tạo thread hoặc gọi service, những hành vi ấy không tự bắt đầu chỉ vì bạn `defineClass` rồi `resolveClass`. Chúng có thể bắt đầu khi về sau gọi `new`, static method, static nonconstant field hoặc reflection yêu cầu initialization.

**Không nên coi `defineClass` tức “mã bytecode đã sẵn sàng cho mọi lời gọi”.** Trạng thái chuẩn bị và lỗi resolution có thể xuất hiện về sau. Hãy phân tách chính sách deploy/nạp với chính sách kích hoạt plugin.

## 8. Một ví dụ giải thích lỗi xảy ra ở initialization, không phải linking

Tệp **`InitFailureAfterLink.java`**:

~~~java
public class InitFailureAfterLink {
    static class Broken {
        static int value = compute();

        private static int compute() {
            System.out.println("running Broken.<clinit>");
            throw new IllegalStateException("missing configuration");
        }
    }

    static class ProbeLoader extends ClassLoader {
        ProbeLoader() {
            super(InitFailureAfterLink.class.getClassLoader());
        }

        Class<?> link(String name) throws ClassNotFoundException {
            Class<?> type = loadClass(name, false);
            resolveClass(type);
            return type;
        }
    }

    public static void main(String[] args) throws Exception {
        ProbeLoader probe = new ProbeLoader();
        Class<?> type = probe.link("InitFailureAfterLink$Broken");
        System.out.println("linked: " + type.getName());

        try {
            Class.forName("InitFailureAfterLink$Broken", true, probe);
        } catch (ExceptionInInitializerError error) {
            System.out.println("initialization failed: "
                    + error.getCause().getClass().getSimpleName());
        }

        try {
            Class.forName("InitFailureAfterLink$Broken", true, probe);
        } catch (NoClassDefFoundError error) {
            System.out.println("later use: "
                    + error.getClass().getSimpleName());
        }
    }
}
~~~

~~~bash
javac --release 17 InitFailureAfterLink.java
java -cp . InitFailureAfterLink
~~~

Stdout:

~~~text
linked: InitFailureAfterLink$Broken
running Broken.<clinit>
initialization failed: IllegalStateException
later use: NoClassDefFoundError
~~~

Phân tích:

- `probe.link` trả về thành công: class được load/link nhưng **chưa initialized**, nên method `compute()` chưa chạy.
- `Class.forName(..., true, probe)` yêu cầu initialization; compiler đã đặt lời gọi `compute()` vào `<clinit>`. `IllegalStateException` (không phải `Error`) được JVM bọc bằng `ExceptionInInitializerError`.
- Runtime class sau đó ở trạng thái **erroneous**; lần yêu cầu initialization kế tiếp của **cùng class identity** gặp `NoClassDefFoundError`. Điều đó không chứng minh file `.class` biến mất và không có tác dụng gọi lại `resolveClass` để sửa.
- Một bản class cùng tên do **loader khác tự định nghĩa** là runtime class identity khác, nhưng việc tạo loader mới không giải quyết nguyên nhân “missing configuration”; cần sửa nguồn lỗi.

**Lưu ý:** Cả `ExceptionInInitializerError` và `NoClassDefFoundError` là subclasses của `Error`, thuộc failure class initialization; với một class file hỏng hoặc truy cập module sai, lỗi có thể xảy ra sớm hơn trong loading/linking. Không nên gộp mọi failure của class loader thành một exception duy nhất.

## 9. Phân biệt ba nhóm lỗi khi debug

| Giai đoạn hoặc tình huống | Biểu hiện phổ biến | Điều nên kiểm tra |
| --- | --- | --- |
| Tìm class bằng tên thất bại | `ClassNotFoundException` từ `loadClass`/`Class.forName` | Loader, binary name, class path/module path, JAR, cơ chế delegation |
| Class file version VM không hỗ trợ | `UnsupportedClassVersionError` | `java -version`, `javap -verbose` major version, `javac --release` |
| Bytecode không hợp lệ | `ClassFormatError`, `VerifyError` | Bytecode generator/instrumentation, phiên bản class file |
| Binary contract khác lúc biên dịch | `NoSuchMethodError`, `NoSuchFieldError`, `IncompatibleClassChangeError` | Dependency version, classpath/module graph, loader chọn class nào |
| Quy tắc visibility/loading constraints | `IllegalAccessError` hoặc `LinkageError` phù hợp | JPMS readability/exports, runtime package, class identity |
| Tính toán static initializer thất bại | `ExceptionInInitializerError` hoặc `Error` gốc | `<clinit>`, static fields, cấu hình, side effects đầu tiên |
| Class đã erroneous sau lần initialize thất bại | `NoClassDefFoundError` | Lần lỗi initialization **trước** đó trong cùng runtime class |
| Cố dùng protected `resolveClass` ngoài subclass | **Compile-time access error** | Quyền truy cập Java, không phải lỗi loading của JVM |
| Gọi `resolveClass(null)` trong subclass | `NullPointerException` | Đối số null, không phải “class chưa load” |

**Cảnh báo quan trọng:** Bảng nêu triệu chứng *thường gặp*, không phải khẳng định JVM luôn ném mỗi loại lỗi tại một bước cố định. JVM cho phép eager/lazy resolution có kiểm soát, nên một lỗi binary dependency có thể chỉ xuất hiện khi thực thi instruction cụ thể.

### 9.1. Vì sao chỉ gọi `resolveClass` không đủ test toàn bộ dependency?

Mã plugin có thể chứa tham chiếu symbolic đến `LibraryV2.doWork()` nhưng runtime chỉ cung cấp `LibraryV1`. Nếu JVM chưa cần resolve method reference đó trong quá trình link ban đầu, `resolveClass` có thể trả về bình thường. Khi code thực sự gọi method, JVM có thể ném `NoSuchMethodError`.

Do vậy test phải **thực thi đường đi nghiệp vụ cần hỗ trợ**, không dừng ở `Class<?> c = loader.loadClass(name)` hay `resolveClass(c)`. Đồng thời cần test startup, static initialization, quyền module, và cả interaction giữa class loaders.

## 10. `resolveClass` có chủ động load superclass/dependency không?

Các giai đoạn class loading và verification/linking có thể yêu cầu tải thêm các class/interface như superclass hoặc những lớp cần để verify descriptor/hierarchy. Nhưng không nên suy luận từ đó rằng:

- **Mọi** class mà source import đều phải đã được nạp sau `resolveClass`.
- **Mọi** class xuất hiện trong method body đều đã initialized.
- Tất cả static fields của các class phụ trợ đã nhận giá trị từ initializer.
- Loader con chắc chắn đã tự define class nếu nó gọi `resolveClass`.

Một class có thể được loaded/linked mà các dependency ở đường code chưa chạy vẫn chưa được sử dụng; các reference có thể chờ resolve theo chính sách JVM. Import Java là khái niệm cú pháp compile-time, không phải danh sách phụ thuộc class loader phải nạp ngay.

**Điểm hay bị bỏ sót:** Khi `ClassLoader` subclass gọi `resolveClass(c)`, `c` có thể đến từ **parent delegation**. Lời gọi link không biến subclass thành **defining loader** của `c`. Quan sát đúng:

~~~java
Class<?> c = someLoader.loadClass("java.lang.String");
System.out.println(c.getClassLoader() == null);
~~~

Với runtime Java phổ biến nơi `String` thuộc bootstrap loader, chương trình in `true`. Nhưng đây chỉ là snippet nằm trong class có biến `someLoader` hợp lệ; không nên in tên implementation nội bộ để suy ra class identity.

## 11. Linking ≠ initialization trong ứng dụng thực

### 11.1. Plugin discovery

Một host muốn hiển thị danh sách plugins có thể chỉ **load class token** của provider để xác minh metadata, không muốn tạo database connection hoặc thread từ static initializer. Thông thường:

1. Dùng metadata/JAR descriptor để tìm candidate.
2. Dùng `Class.forName(binaryName, false, pluginLoader)` khi cần token.
3. Kiểm tra contract bằng `SharedApi.class.isAssignableFrom(candidate)` với **SharedApi do loader chung định nghĩa**.
4. Chỉ khi đã chọn provider và sẵn sàng start lifecycle, thực hiện reflection/constructor/static entry point theo API cần dùng.

**Đừng dùng `resolveClass` như “safe dry run” tuyệt đối:** việc link vẫn có thể nạp/verify class khác, tốn chi phí, gặp lỗi linkage hoặc kích hoạt hành vi do custom loader của bạn tự cài đặt. Bên cạnh đó, thao tác reflection tiếp theo có thể làm class initialize tùy method và API cụ thể.

### 11.2. Framework dependency injection và reflection

Việc framework đọc `Class<?>`, annotations, method/field metadata có thể xảy ra trước khi object được tạo. Nhưng không nên khẳng định mọi reflection API đều không kích hoạt initialization: phải đọc contract của API được gọi. Ví dụ `Class.forName(name,true,loader)` cố ý init, còn `Class.forName(name,false,loader)` không yêu cầu init.

Nếu cấu hình framework có lỗi `ExceptionInInitializerError`, trước hết xác định **thao tác active use nào xảy ra trước** thay vì mặc định quy lỗi cho bước “scan class”. Tương tự, lỗi `NoClassDefFoundError` ở lần truy cập sau có thể phản ánh một initialization failure từ startup.

### 11.3. Hot reload và class-loader isolation

Mỗi class thường được initialize tối đa một lần **theo runtime class identity**. Khi host tạo loader mới và **define** cùng binary name thành một class mới, class mới có trạng thái static riêng và có thể được initialized riêng. Nếu loader mới chỉ delegate về class cũ, `resolveClass` hay `Class.forName(...,true,loaderMoi)` không tạo một phiên bản kiểu mới.

Vì vậy, khi hot reload:

- Ghi rõ **defining loader** của API dùng chung và plugin classes.
- Đừng dùng `resolveClass` để mong “reset” static state.
- Dọn thread/callback/cache/`ThreadLocal` để loader cũ có thể được GC theo điều kiện của runtime; `URLClassLoader.close()` không đảm bảo unload tức thì.
- Xử lý plugin lifecycle bằng API rõ ràng, tránh đặt toàn bộ startup/teardown trong static initializer.

## 12. Những giới hạn và chi tiết triển khai cần nêu đúng

- **Không thể gọi trực tiếp `<clinit>` từ Java source** như method thông thường. Compiler tạo nội dung từ static initializers; VM quyết định thời điểm initialization theo quy tắc.
- **`resolveClass` không phải synonym của `Class.forName(..., true, ...)`**. Một API link; một API yêu cầu initialize.
- **`loadClass(name, false)` không cam kết class hoàn toàn chưa linked**. Nó chỉ không chủ động request `resolveClass` theo đường API này.
- **`resolveClass` không cam kết eager resolution toàn bộ runtime constant pool**; JVMS cho phép lazy resolution.
- **`resolveClass` không phải method public** và không dùng để thay Java module readability/access checks.
- **Không có guarantee về thứ tự log “class loaded”, “class linked”, “class initialized”** như một chuỗi duy nhất trên mọi JVM; log là quan sát của triển khai, không thay đặc tả.
- **Có thể gặp lỗi linker trước initializer**. Ví dụ class file sai major version hay bytecode verification failure. Nhưng không giả định một lỗi symbolic reference nhất định sẽ được phát hiện ngay lần gọi `resolveClass`.
- **Không dùng cờ `resolve=true` để ép reload class**. Cùng class token đã linked thì lời gọi no-op; reload thực sự phụ thuộc runtime class mới do defining loader khác tạo.
- **Initialization an toàn phối hợp giữa threads**, nhưng việc cập nhật mutable static state sau init vẫn cần đồng bộ riêng theo Java Memory Model.
- **Class path/module path có thể đổi runtime identity/access**, nhưng không đổi định nghĩa của `resolveClass` trong Java SE API.

## 13. Cách quan sát trên JDK 17+ mà không nhầm log với đặc tả

### 13.1. `javap` cho biết mã `<clinit>` có tồn tại không

Biên dịch `LinkVsInit.java` trước, rồi:

~~~bash
javap -c -p 'LinkVsInit$Target'
~~~

Trong PowerShell và Unix shell, dấu nháy đơn bảo vệ dấu `$` của binary name. `javap` thường hiển thị phần khởi tạo class dưới tiêu đề `static {};`. Phần đó chứa bytecode in “Target initialized” và gán `answer=42`. Việc tìm thấy đoạn bytecode **không** nói nó đã thực thi trong một process cụ thể.

### 13.2. Unified logging của HotSpot

Nếu dùng OpenJDK/HotSpot có hỗ trợ unified logging:

~~~bash
java -Xlog:class+load=info LinkVsInit
java -Xlog:class+init=info LinkVsInit
java -Xlog:help
~~~

- `class+load` cho thấy các sự kiện load; tải class **không đồng nghĩa** init.
- `class+init` cho phép quan sát một số sự kiện khởi tạo trên JVM/hệ thống hỗ trợ tag đó; đầu ra có thể rất dài.
- `-Xlog` là cơ chế logging của JVM triển khai, không phải API chung mọi Java Virtual Machine phải hỗ trợ. Kiểm tra `java -Xlog:help` khi chuyển JDK/vendor.

**Không parse message format của HotSpot để suy ra quy tắc ngôn ngữ.** Dùng chương trình in mốc “before/after” và đối chiếu JVMS/JLS để hiểu hành vi.

### 13.3. Quan sát trong debugger

Debugger có thể tự đánh giá expression hoặc gọi method qua reflection, từ đó **gây initialization ngoài ý định thí nghiệm**. Khi so sánh `resolveClass` với `Class.forName(true)`, chạy chương trình qua command line và không evaluate static fields trong debugger trước khi đọc stdout. Nếu lớp đã initialized do một thao tác khác, thí nghiệm sẽ không có dòng “Target initialized” ở vị trí kỳ vọng.

## 14. Checklist chọn API đúng

| Mục tiêu | API/kỹ thuật phù hợp | Vì sao |
| --- | --- | --- |
| Biết lớp có thể tìm theo tên qua loader không | `loader.loadClass(binaryName)` | Tra cứu class; không chủ động init theo hợp đồng mặc định |
| Có class token nhưng cần yêu cầu link trong loader tùy biến | `resolveClass(token)` bên trong subclass | Explicit link; không tự kích hoạt `<clinit>` |
| Lấy class token mà muốn trì hoãn init | `Class.forName(binaryName,false,loader)` | Không yêu cầu init |
| Muốn chắc class được initialize trước khi vào lifecycle tiếp theo | `Class.forName(binaryName,true,loader)` | Yêu cầu init theo runtime identity |
| Chạy ứng dụng bình thường | `new`/static method/field access | JVM tự link/init khi cần; không thêm `resolveClass` thủ công |
| Khẳng định class đã load đúng bản | `clazz.getClassLoader()`, `clazz.getModule()`, source/dependency | Kiểm tra class identity/nguồn class |
| Bắt vấn đề binary compatibility | Thực thi đường code thực + integration tests | `resolveClass` không bảo đảm mọi symbolic reference đã resolve |
| Debug lỗi init | Đọc `ExceptionInInitializerError.getCause()` và log đầu tiên | Phân biệt initialization failure và class not found |

### 14.1. Những câu hỏi “đúng/sai” có đáp án

1. “`resolveClass` gọi `<clinit>` của lớp được truyền vào.” — **Sai**.
2. “`resolveClass` là `protected final void` trong `ClassLoader`.” — **Đúng**.
3. “`resolveClass` buộc resolve mọi method/field reference trong constant pool trước khi trả về.” — **Sai**.
4. “`loadClass(name, false)` sẽ khiến class chắc chắn vẫn chưa link.” — **Sai**.
5. “`Class.forName(name, false, loader)` không yêu cầu initialize class.” — **Đúng**.
6. “`Class.forName(name, true, loader)` initialize lại mỗi lần gọi.” — **Sai**.
7. “Static initializer có thể ném lỗi sau khi `resolveClass` từng trả thành công.” — **Đúng**.
8. “Một class có thể load và link xong, rồi mới gặp `NoSuchMethodError` tại call site.” — **Đúng** nếu resolution method reference được thực hiện muộn theo JVMS.
9. “`resolveClass(c)` đổi defining loader của `c` thành loader đang gọi.” — **Sai**.
10. “Nếu code được định nghĩa bởi loader khác với tên class giống hệt, đó có thể là runtime class riêng và khởi tạo riêng.” — **Đúng**.

## 15. Tài liệu gốc để tự đối chiếu

1. [`ClassLoader.resolveClass` — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ClassLoader.html#resolveClass(java.lang.Class)) — `protected final`; tài liệu xác định chức năng chính là **link specified class**, đồng thời ghi tên method hơi gây nhầm.
2. [`ClassLoader.loadClass(String, boolean)` — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ClassLoader.html#loadClass(java.lang.String,boolean)) — trình tự `findLoadedClass` → delegation → `findClass` → `resolveClass` khi flag true.
3. [`Class.forName` — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Class.html#forName(java.lang.String,boolean,java.lang.ClassLoader)) — ý nghĩa của tham số `initialize`.
4. [JVMS 5.3 — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3) — class identity, defining/initiating class loaders.
5. [JVMS 5.4 — Linking](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.4) — verification, preparation, resolution và sự linh hoạt eager/lazy.
6. [JVMS 5.5 — Initialization](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.5) — `ConstantValue`, `<clinit>`, triggers và trạng thái erroneous.
7. [JLS 12.4 — Initialization of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.4) — active use, superclasses, superinterfaces và cơ chế phối hợp thread.

**Tóm ý:** `resolveClass` là công cụ **linking** của `ClassLoader`, không phải lời gọi chạy static initializer. Cờ `resolve` trong `loadClass` không đồng nghĩa `initialize` trong `Class.forName`. Muốn hiểu lỗi class loading chính xác, luôn tách **loading → linking (verification/preparation/resolution) → initialization** và nhớ JVM có thể giải symbolic references ở thời điểm khác nhau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-api-006">Class.forName(String) và lựa chọn loader mặc định</a>

<details>
<summary>Xem chi tiết</summary>

# Class.forName(String) và lựa chọn loader mặc định

`Class.forName(String)` là API quen thuộc khi một chương trình Java cần lấy `Class<?>` từ **tên class ở runtime**: tìm implementation từ cấu hình, đăng ký driver, khởi tạo plugin hoặc dùng reflection. Điểm dễ nhầm nhất là từ “mặc định”: overload một tham số **không tự chọn thread context class loader, không luôn dùng system loader, và cũng không định nghĩa class bằng bootstrap loader chỉ vì phương thức nằm trong `java.lang.Class`**.

Theo Java SE 17, lời gọi:

```java
Class<?> type = Class.forName("com.example.Service");
```

tương đương về ý nghĩa với:

```java
Class<?> type = Class.forName(
        "com.example.Service",
        true,
        currentLoader
);
```

trong đó **`currentLoader` là defining class loader của class đang gọi `Class.forName(String)`**. `true` yêu cầu **khởi tạo class/interface** nếu chưa khởi tạo trước đó. `currentLoader` là ký hiệu giải thích của đặc tả, không phải biến có sẵn trong Java.

Bài học thuộc nhóm **API nạp lớp** của Java core – classloader. Ta sẽ phân biệt loader mặc định với system loader và thread context loader; hiểu tên đầu vào, hành vi loading–linking–initialization, so sánh các overload; chạy chương trình thực nghiệm và xử lý các lỗi thường gặp trong plugin/module application.

## 1. `Class.forName(String)` làm gì?

Chữ ký trong Java 17:

```java
public static Class<?> forName(String className)
        throws ClassNotFoundException
```

`className` là tên dạng binary name của class hoặc interface cần tìm; với kiểu mảng, API chấp nhận tên mảng theo quy ước `Class.getName()`. Khi gọi:

1. **Chọn loader theo caller:** JVM xác định *class đang gọi trực tiếp phương thức này*, lấy **defining loader** của class đó.
2. **Tìm/nạp class:** yêu cầu loader đó tìm class đã có hoặc load class tương ứng, dùng cơ chế delegation/module mapping của loader.
3. **Liên kết khi cần:** JVM đảm bảo những điều kiện linking mà thao tác yêu cầu, gồm resolution thích hợp.
4. **Khởi tạo:** với class/interface thông thường, tham số khởi tạo hiệu dụng là `true`; nếu class chưa khởi tạo, JVM thực hiện initialization trước khi trả thành công.
5. **Trả `Class<?>`:** đó là đối tượng metadata/runtime identity của class được tìm thấy; hàm **không tạo instance** của class đích.

Nếu class đã khởi tạo thành công, lời gọi tiếp theo không chạy lại static initializer. Nếu quá trình tìm, liên kết hoặc khởi tạo thất bại, phương thức báo lỗi tương ứng.

### Ba loader cần gọi đúng tên

| Khái niệm | Cách lấy | Ý nghĩa trong `Class.forName(String)` |
| --- | --- | --- |
| **Loader của class đang gọi** | `Caller.class.getClassLoader()` | **Là lựa chọn mặc định** của overload một tham số |
| **System/application loader** | `ClassLoader.getSystemClassLoader()` | Chỉ trùng với loader mặc định khi class đang gọi thực sự do system loader định nghĩa |
| **Thread context class loader (TCCL)** | `Thread.currentThread().getContextClassLoader()` | **Không được overload một tham số tự động chọn**; muốn dùng thì truyền rõ qua overload ba tham số |
| **Bootstrap loader** | Thường biểu diễn bằng `null` trong `Class.getClassLoader()` | Có thể là loader mặc định khi caller do bootstrap định nghĩa; `null` còn có nghĩa chọn bootstrap khi truyền cho overload ba tham số |
| **Loader của class trả về** | `type.getClassLoader()` | Defining loader của **class kết quả**; có thể khác loader được dùng để bắt đầu tìm |

**Quan trọng:** “Loader được dùng để bắt đầu tìm” và “loader định nghĩa class đã tìm” khác nhau. Ví dụ system loader được yêu cầu tìm `java.lang.String` nhưng parent delegation có thể trả `Class<String>` do bootstrap định nghĩa; `String.class.getClassLoader()` thường là `null`.

## 2. Vì sao không thể mặc định hiểu là system loader?

Đối với chương trình đơn giản chạy bằng `java -cp . Main`, `Main` thường do system/application loader định nghĩa. Trong bối cảnh đó, `Class.forName("pkg.Tool")` nhìn giống `ClassLoader.getSystemClassLoader().loadClass("pkg.Tool")` vì hai lời gọi thường bắt đầu từ cùng một loader.

Sự giống nhau ấy **không phải quy tắc chung**:

- **Plugin loader:** class `PluginEntry` được loader riêng định nghĩa; khi code trong `PluginEntry` gọi `Class.forName("plugin.Helper")`, loader mặc định là loader đã định nghĩa `PluginEntry`. System loader có thể hoàn toàn không nhìn thấy `plugin.Helper`.
- **Thư viện dùng chung:** class `LibraryHelper` do parent/system loader định nghĩa; nếu chính `LibraryHelper` gọi `Class.forName(name)`, lời gọi mặc định dùng **loader của `LibraryHelper`**, ngay cả khi plugin ở loader con gọi vào method của helper.
- **Container/application server:** mỗi ứng dụng có thể có loader riêng. Hai ứng dụng gọi cùng tên `com.acme.Model` qua class caller ở hai loader khác nhau có thể nhận hai class khác nhau.
- **Module layer tự tạo:** một module có thể được gán cho loader riêng từ `ModuleLayer`; class trong module đó gọi overload một tham số theo loader đã định nghĩa class caller, không tự quay về system loader.
- **Bootstrap-defined caller:** nếu code caller thực sự ở lớp do bootstrap định nghĩa, defining loader được biểu diễn là `null`.

Vì vậy, cần hỏi **“class nào chứa câu lệnh `Class.forName(...)` và class đó do ai định nghĩa?”**, thay vì chỉ hỏi “chương trình được chạy từ đâu?”.

### Caller-sensitive: chính caller của `forName` quyết định

JDK đánh dấu một số API là *caller-sensitive*, vì hành vi của chúng phải phản ánh class đứng ở call site. Một method wrapper thường có hành vi khác so với việc plugin gọi trực tiếp:

```java
public final class LibraryHelper {
    private LibraryHelper() { }

    public static Class<?> load(String name)
            throws ClassNotFoundException {
        return Class.forName(name);
    }
}
```

Đây là **class hoàn chỉnh có thể biên dịch**, không có `main`. Khi plugin gọi `LibraryHelper.load("plugin.Widget")`, class thực hiện lời gọi `Class.forName` là **`LibraryHelper`**. Nếu `LibraryHelper` được định nghĩa bởi parent loader không thấy `plugin.Widget`, lời gọi có thể phát `ClassNotFoundException` dù plugin loader tìm được class.

Không có quy tắc “tự đi ngược call stack đến application/plugin gốc để mượn loader của họ”. Nếu helper muốn tải class theo miền của plugin, thiết kế API nên nhận `ClassLoader` hoặc `ModuleLayer`/`Module` một cách tường minh.

## 3. Ví dụ Java 17+: thay TCCL mà overload một tham số vẫn dùng caller loader

Tạo một file **`ForNameDemo.java`**:

```java
public class ForNameDemo {
    static class Target {
        static {
            System.out.println("Target initialized");
        }
    }

    static class Deferred {
        static {
            System.out.println("Deferred initialized");
        }
    }

    static class ArrayElement {
        static {
            System.out.println("ArrayElement initialized");
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader callerLoader = ForNameDemo.class.getClassLoader();
        ClassLoader isolated = new ClassLoader(null) { };

        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();

        thread.setContextClassLoader(isolated);
        try {
            System.out.println("caller loader is context: "
                    + (callerLoader == thread.getContextClassLoader()));

            System.out.println("one arg");
            Class<?> target = Class.forName("ForNameDemo$Target");
            System.out.println("found target: " + (target == Target.class));

            try {
                Class.forName("ForNameDemo$Target", false, isolated);
                System.out.println("unexpected");
            } catch (ClassNotFoundException expected) {
                System.out.println("explicit context: not found");
            }

            Class<?> deferred = Class.forName(
                    "ForNameDemo$Deferred", false, callerLoader);
            System.out.println("deferred loaded: "
                    + (deferred == Deferred.class));

            System.out.println("initialize deferred");
            Class.forName("ForNameDemo$Deferred", true, callerLoader);

            Class<?> array = Class.forName(
                    "[LForNameDemo$ArrayElement;");
            System.out.println("array found: " + array.isArray());

            System.out.println("initialize component");
            Class.forName("ForNameDemo$ArrayElement");
        } finally {
            thread.setContextClassLoader(previous);
        }
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac --release 17 ForNameDemo.java
java -cp . ForNameDemo
```

Stdout dự kiến trên Java SE thông thường, chạy bằng application class path:

```text
caller loader is context: false
one arg
Target initialized
found target: true
explicit context: not found
deferred loaded: true
initialize deferred
Deferred initialized
array found: true
initialize component
ArrayElement initialized
```

### Tại sao các dòng xuất hiện đúng thứ tự?

1. `ForNameDemo` do application loader định nghĩa khi chạy `java -cp .`. `isolated` là custom loader có parent `null` (delegate tới bootstrap), không có bytecode `ForNameDemo$Target`.
2. Sau khi gán `isolated` làm TCCL, `callerLoader == thread.getContextClassLoader()` trả `false`.
3. `Class.forName("ForNameDemo$Target")` chạy **ngay trong `ForNameDemo.main`**, do đó dùng loader của `ForNameDemo`, không dùng TCCL. Class được tìm thấy và initializer in `Target initialized` trước khi API trả về.
4. `Class.forName(name, false, isolated)` dùng đúng custom loader được truyền. Loader ấy không có class đích và parent bootstrap cũng không có, nên phát `ClassNotFoundException`.
5. `Class.forName("ForNameDemo$Deferred", false, callerLoader)` lấy `Class<?>` nhưng **chưa chạy** static block. Lời gọi với `true` sau đó mới in `Deferred initialized`.
6. `Class.forName("[LForNameDemo$ArrayElement;")` tìm class **mảng**; component type có thể được load, nhưng static initializer của `ArrayElement` **không bị buộc chạy chỉ vì array class được tìm**. Lời gọi một tham số theo tên component sau đó mới in `ArrayElement initialized`.
7. TCCL cũ được khôi phục trong `finally` để tránh làm ảnh hưởng thao tác khác cùng thread.

Ví dụ trên cố tình dùng **hai loader khác nhau** và **ba loại khởi tạo** để chứng minh hành vi API. Kết quả phụ thuộc bối cảnh chạy được nêu rõ: mặc định JDK 17+, trên application class path, không có hệ thống tùy biến loader bất thường.

## 4. So sánh các overload và `ClassLoader.loadClass`

| API | Chọn loader | Khởi tạo class thông thường? | Nếu không tìm được |
| --- | --- | --- | --- |
| `Class.forName(String name)` | **Defining loader của caller** | **Có**, nếu chưa khởi tạo | `ClassNotFoundException` |
| `Class.forName(String name, boolean initialize, ClassLoader loader)` | **Loader truyền vào**; `null` là bootstrap | Theo `initialize` | `ClassNotFoundException` |
| `Class.forName(Module module, String name)` | **Module truyền vào** và loader gắn với module đó | **Không yêu cầu link/init** theo hợp đồng overload này | Trả `null` nếu không tìm thấy class trong module |
| `ClassLoader.loadClass(String name)` | Loader được gọi; dùng chính sách `loadClass` | **Không tự yêu cầu initialize** | `ClassNotFoundException` |
| `T.class` | Kết quả được compiler/JVM resolve theo ngữ cảnh class caller | **Không do riêng class literal** | Không phải API nhận tên động; lỗi liên kết nếu class dependency không hợp lệ |

Đối với `Class.forName(String, boolean, ClassLoader)`, tham số `initialize = true` đồng nghĩa **khởi tạo class/interface** khi cần. Nếu class đã khởi tạo thành công, JVM không khởi tạo lại.

`ClassLoader.loadClass(name)` **không phải bản thay thế đồng nghĩa** cho `Class.forName(name)`: nó trả `Class<?>` mà không tự kích hoạt initialization. Một bước sử dụng sau đó như gọi static method, truy cập static field không phải compile-time constant hay tạo instance mới có thể buộc khởi tạo.

Overload `Class.forName(Module, String)` ra đời để xác định module đích rõ hơn. Nó nhận **binary name**, không dùng tên mảng theo cách overload chuỗi/loader; return `null` nếu module đích không chứa class được yêu cầu. Phương thức này không đảm bảo đã link hay initialize class; muốn sử dụng thành viên vẫn phải tuân theo quy tắc JVM/module access.

## 5. Một hay ba tham số: chọn API theo mục đích

### Trường hợp A – biết class đích ở cùng miền loader với caller

Dùng:

```java
Class<?> type = Class.forName("com.example.local.Handler");
```

Đây là **đoạn minh họa**, chỉ chạy nếu class tương ứng tồn tại và accessible theo loader của class caller. Phù hợp với chương trình có một loader ứng dụng hoặc code chủ động muốn dùng namespace của mình.

### Trường hợp B – cần chọn loader của plugin

Dùng overload ba tham số:

```java
public static Class<?> loadPluginType(
        String binaryName, ClassLoader pluginLoader)
        throws ClassNotFoundException {
    return Class.forName(binaryName, true, pluginLoader);
}
```

Đây là **method minh họa đầy đủ**, cần đặt vào một class. Để chỉ tìm type và tránh static side effect trước lúc cần thực thi, cân nhắc `false`:

```java
Class<?> pluginType =
        Class.forName(binaryName, false, pluginLoader);
```

`false` không đảm bảo “không xảy ra bất kỳ công việc nào”: JVM vẫn có thể load/link class và phụ thuộc cần thiết. Nó chỉ nói **không yêu cầu khởi tạo class đích** ngay trong lời gọi này.

### Trường hợp C – library/framework nhận plugin thông qua TCCL

Một số framework (ví dụ cơ chế khám phá provider) chủ động dùng **thread context class loader** để tìm implementation do child loader định nghĩa. Nếu đó là hợp đồng mong muốn:

```java
ClassLoader contextLoader = Thread.currentThread()
        .getContextClassLoader();

Class<?> implementation =
        Class.forName(binaryName, true, contextLoader);
```

Đoạn trên giả định `binaryName` được xác định, context loader phù hợp và code nằm trong method có xử lý `ClassNotFoundException`. Nếu TCCL bằng `null`, overload ba tham số dùng **bootstrap loader**, không tự chuyển sang system loader. Nếu framework cần fallback `system` khi TCCL null, phải **viết chính sách fallback rõ ràng** theo contract framework.

TCCL là trạng thái có thể thay đổi của thread. Khi tạm gán cho plugin request hoặc service discovery, luôn khôi phục loader cũ trong `finally`, nhất là khi thread được tái sử dụng bởi thread pool.

### Trường hợp D – lớp thuộc một named module cụ thể

Nếu đã biết module:

```java
Module module = /* module đích trong layer thích hợp */;
Class<?> type = Class.forName(module, "com.example.api.Service");
if (type == null) {
    throw new IllegalArgumentException("Class not in target module");
}
```

Đây là **mã minh họa**; `module` phải được lấy từ module thực tế. Với module layer do ứng dụng tạo, có thể lấy loader qua `layer.findLoader("m.plugin")` và dùng overload ba tham số nếu còn cần **initialize** lớp đó. Đừng tự cho rằng lớp trong custom `ModuleLayer` luôn hiện diện qua system loader.

## 6. Tên đầu vào: binary name và tên kiểu mảng

`Class.forName(String)` nhận **binary name** của class/interface; với lớp thành viên dùng dấu `$` thay vì cú pháp `Outer.Inner` của Java source. Ví dụ:

| Kiểu cần tìm | Tên hợp lệ |
| --- | --- |
| Class thông thường | `java.lang.String` |
| Member class | `java.util.Map$Entry` |
| `String[]` | `[Ljava.lang.String;` |
| `String[][]` | `[[Ljava.lang.String;` |
| `int[]` | `[I` |

Dùng `SomeType.class.getName()` để quan sát tên thực tế, đặc biệt với nested classes. Các chuỗi `java/util/Map$Entry`, `java.util.Map.Entry` và `java.lang.String[]` không phải tên tìm tương ứng của các kiểu trong bảng. `"int"` và `"void"` không trả primitive/void class; dùng `int.class` hoặc `void.class`.

Array class có quy tắc riêng: `Class.forName("[Lpkg.Component;")` có thể load component type nhưng không khởi tạo component chỉ vì phép tìm array. Những chi tiết về từng tham số, dấu hiệu lỗi và nhiều ví dụ lookup theo loader được triển khai sâu hơn trong bài overload ba tham số.

## 7. Tại sao `forName` có thể kích hoạt `<clinit>`?

Class initialization xảy ra trong giai đoạn JVM phải hoàn tất khởi tạo class trước khi thao tác yêu cầu tiếp tục. Overload một tham số của `Class.forName` có `initialize = true`, nên khi class chưa khởi tạo, static field initializers và các static blocks (được compiler thể hiện bằng `<clinit>` khi cần) sẽ chạy.

Ví dụ:

```java
public class InitOnlyDemo {
    static class Configuration {
        static final int VALUE = initialize();

        static int initialize() {
            System.out.println("Configuration initialized");
            return 10;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("before");
        Class<?> type = Class.forName(
                "InitOnlyDemo$Configuration");
        System.out.println("after");
        System.out.println("class: " + type.getSimpleName());
    }
}
```

```bash
javac --release 17 InitOnlyDemo.java
java -cp . InitOnlyDemo
```

Stdout chính xác:

```text
before
Configuration initialized
after
class: Configuration
```

Không có `new Configuration()` hay lời gọi method thủ công; chỉ `Class.forName` đã yêu cầu init. Với `Configuration.class`, bản thân class literal không buộc initializer chạy.

**Chú ý hằng:** nếu code chỉ đọc một `static final int X = 10` là *constant variable* trong source, compiler có thể inline giá trị nên không cần init class khai báo. Nhưng `Class.forName("SomeClass")` **vẫn yêu cầu initialization** của `SomeClass`, bất kể class có field hằng hay không; hai loại thao tác không được đánh đồng.

### `forName` với array khác class component

Nếu `name` là tên array class, API trả `Class<?>` của **mảng**; component type được load nhưng **không bị khởi tạo** chỉ bởi `forName` với tên array. Ví dụ phần `ForNameDemo` cho thấy `"[LForNameDemo$ArrayElement;"` không làm chạy `ArrayElement` static block. Đừng suy rằng `true` sẽ khởi tạo component class trong mọi trường hợp.

## 8. Xử lý lỗi chính xác

API có thể phát **`ClassNotFoundException`**, **`LinkageError`** và **`ExceptionInInitializerError`** trong những trường hợp khác nhau:

| Tình huống | Kết quả thường gặp | Ý nghĩa |
| --- | --- | --- |
| Loader được chọn không tìm được binary name | `ClassNotFoundException` | Không có class qua phạm vi loader/delegation ấy |
| Class file tồn tại nhưng phụ thuộc linkage bị lỗi hoặc class có bytecode không tương thích | `LinkageError` (hoặc subclass) | JVM không hoàn thành linking như yêu cầu |
| Static initializer ném exception không phải `Error` | `ExceptionInInitializerError` | `<clinit>` thất bại trong lần initialize đầu |
| Class đã từng thất bại initialization, được dùng lần nữa | Thường `NoClassDefFoundError` | Class ở trạng thái erroneous, không tự thử chạy lại initializer |
| Muốn tải tên primitive `"int"` | Thường `ClassNotFoundException` | API không dùng để lấy primitive class |
| Tìm thấy class nhưng code sau đó không được phép truy cập thành viên | `IllegalAccessException`, `IllegalAccessError` hoặc ngoại lệ reflection tương ứng | Tìm class không đồng nghĩa có quyền sử dụng mọi thành viên |

`ExceptionInInitializerError` là subclass của `LinkageError`. Nếu code muốn log lỗi riêng, đặt catch chuyên biệt trước catch `LinkageError`; không nhất thiết nên bắt các lỗi này để tiếp tục công việc, vì class có thể đã chuyển sang trạng thái erroneous.

### Lỗi khởi tạo ở lần đầu và lần sau

Khi `<clinit>` ném một exception không thuộc `Error`, JVM thường bọc nó trong `ExceptionInInitializerError` ở lần kích hoạt đầu tiên. Class trở thành **erroneous**, nên lần active use tiếp theo đối với **cùng runtime class** thường nhận `NoClassDefFoundError`, không chạy lại initializer. Vì vậy cần giữ stack trace và cause của lỗi đầu tiên. Bài về overload `Class.forName(name, initialize, loader)` thực hành riêng tình huống này với hai lần gọi `initialize=true`.

## 9. Vì sao tải được class chưa chắc sử dụng được?

`Class.forName` là API **tìm metadata của class**. Khả năng tìm một `Class<?>` chưa chứng minh:

- Có constructor không tham số để tạo instance.
- Class không abstract/interface.
- Caller có quyền gọi constructor hoặc private method.
- Named module đã `exports` hoặc `opens` package cho truy cập cần thiết.
- Client và class plugin dùng **cùng định nghĩa interface**; trong Java, danh tính kiểu là binary name + defining loader.
- Dependency của class sẽ liên kết thành công trong mọi đường code được gọi về sau.

Ví dụ code:

```java
Class<?> type = Class.forName(binaryName, false, pluginLoader);
Object instance = type.getDeclaredConstructor().newInstance();
```

Đây là **đoạn minh họa**, giả định `binaryName` và `pluginLoader` có sẵn, class có constructor truy cập được và các exception đã được xử lý. Dòng đầu `initialize=false` chỉ tìm class. Dòng thứ hai có thể yêu cầu initialization khi tạo instance, đồng thời phải vượt qua access/module checks và có thể phát exception từ constructor.

Nếu plugin implement một interface của host, nên kiểm tra:

```java
Class<?> type =
        Class.forName(binaryName, true, pluginLoader);
if (!PluginApi.class.isAssignableFrom(type)) {
    throw new IllegalArgumentException(
            "Not an implementation of PluginApi");
}
```

Đây là **mã minh họa**, giả định `PluginApi` thuộc API chung mà cả host và plugin resolve về **cùng `Class<?>`**. Khi mỗi loader tự định nghĩa một bản `PluginApi` trùng tên, kiểm tra assignability có thể thất bại dù tên interface giống nhau; sửa layout class loader/delegation, không chỉ sửa chuỗi `forName`.

## 10. Có nên dùng `Class.forName` để kích hoạt đăng ký driver?

Trong các phiên bản JDBC cũ, nhiều ví dụ gọi:

```java
Class.forName("com.example.jdbc.Driver");
```

để buộc static initializer của driver đăng ký với `DriverManager`. Đây là một **mẫu lịch sử** thể hiện side effect của `forName`; chuỗi `com.example.jdbc.Driver` ở trên chỉ là tên minh họa, không đại diện driver tồn tại.

Trong JDBC 4+ hiện đại, driver phù hợp thường được khám phá qua **service-provider mechanism**, khi JAR/module được đóng gói đúng. Do đó không nên mặc định thêm `Class.forName` cho mọi ứng dụng JDBC hoặc suy rằng nó là cách duy nhất đăng ký driver.

Nếu driver không được phát hiện, điều tra dependency, `META-INF/services/java.sql.Driver` hoặc khai báo module `provides`, TCCL, hệ module và driver manager. `Class.forName` có thể ép init class cụ thể nếu đúng loader, nhưng không sửa được mọi lỗi service discovery hay binary compatibility.

## 11. Vấn đề loader trong ứng dụng nhiều module hoặc plugin

Giả sử host dùng system loader, mỗi plugin có loader riêng:

```text
               [system loader]
                  /         \
          [plugin A]       [plugin B]
           demo.Impl        demo.Impl
```

Nếu code trong **plugin A** gọi `Class.forName("demo.Impl")`, API mặc định dùng defining loader của class caller thuộc plugin A (giả sử caller được plugin A định nghĩa); kết quả có thể là `demo.Impl` của A.

Nếu **host/helper** ở system loader gọi chính `Class.forName("demo.Impl")`, class này có thể không được tìm thấy do system loader không nhìn xuống child plugin loader. Nếu **plugin B** gọi, có thể nhận `demo.Impl` do plugin B định nghĩa — **hai `Class<?>` không đồng nhất**.

Điều này không có nghĩa `forName` vô tình “thay loader theo tên thread”. Nó chọn theo **định nghĩa của class caller tại call site**, còn mỗi loader thực hiện chính sách tìm riêng. Cùng một chuỗi `"demo.Impl"` có thể có các kết quả khác nhau mà vẫn đúng hợp đồng API.

### Thiết kế helper rõ ràng

Trong library phát triển cho nhiều loader, cung cấp overload nhận tham số loader:

```java
public final class PluginClasses {
    private PluginClasses() { }

    public static Class<?> find(
            String binaryName,
            boolean initialize,
            ClassLoader loader) throws ClassNotFoundException {
        return Class.forName(binaryName, initialize, loader);
    }
}
```

Class này **biên dịch được với Java 17+** và không yêu cầu một framework riêng. Thay vì phụ thuộc vào loader của chính `PluginClasses`, caller chọn loader phù hợp với plugin/layer được yêu cầu. Tuy nhiên, **không** tùy tiện truyền TCCL hoặc system loader: cần quyết định từ kiến trúc plugin về namespace, API dùng chung, vòng đời và quyền truy cập.

Nếu plugin loader cần được tháo bỏ, tránh giữ `Class<?>` hoặc instance class plugin trong static cache sống lâu hơn plugin, vì những tham chiếu đó có thể giữ loader và ngăn garbage collection.

## 12. Cách chẩn đoán một lời gọi `forName` đang dùng loader nào

Thêm log gần **đúng nơi gọi `Class.forName(String)`**:

```java
ClassLoader callerLoader = MyCaller.class.getClassLoader();
ClassLoader systemLoader = ClassLoader.getSystemClassLoader();
ClassLoader tccl = Thread.currentThread().getContextClassLoader();

System.out.println("caller=" + callerLoader);
System.out.println("system=" + systemLoader);
System.out.println("tccl=" + tccl);

Class<?> result = Class.forName(binaryName);
System.out.println("defined by=" + result.getClassLoader());
System.out.println("module=" + result.getModule());
```

Đây là **đoạn minh họa**, giả định `MyCaller` chính là class chứa đoạn mã tại call site, `binaryName` hợp lệ và đã xử lý `ClassNotFoundException`. Không dùng `MyCaller.class.getClassLoader()` trong một class không phải caller rồi nhầm đó là loader hiệu dụng.

Trình tự kiểm tra lỗi:

1. **Xác định call site trực tiếp:** câu lệnh ở class nào? Có đi qua wrapper/helper do loader khác định nghĩa không?
2. **So loader:** in defining loader của caller, system, TCCL; nhớ rằng `null` thường biểu diễn bootstrap.
3. **Kiểm tra đúng tên:** binary name của member/nested class, tên array hoặc package. Đừng đưa source canonical name sai cho nested class.
4. **Kiểm tra class path/module layer:** class thật sự được định nghĩa cho loader nào? Có lọt ở child loader nhưng lời gọi xuất phát từ parent không?
5. **Phân biệt giai đoạn thất bại:** `ClassNotFoundException` là tìm class thất bại; `ExceptionInInitializerError` là initialization thất bại; `NoClassDefFoundError` có thể là phụ thuộc thiếu hoặc class erroneous.
6. **Kiểm tra module access:** class có tìm được nhưng reflection/invocation bị chặn bởi `exports`/`opens` hoặc access Java không?
7. **Sửa lựa chọn loader tại ranh giới API:** truyền `ClassLoader`/`Module`/`ModuleLayer` khi bối cảnh cần rõ ràng; tránh thay TCCL toàn ứng dụng để vá tạm một lời gọi.

Trên HotSpot có thể dùng logging cho class loading:

```bash
java -Xlog:class+load=info -cp . ForNameDemo
```

Log này nói về **loading**, không phải bằng chứng duy nhất cho lựa chọn caller-sensitive hoặc thời điểm class initialization. Quan sát output từ static block và giá trị `getClassLoader()` ở đúng call site để phân tích chính xác hơn.

## 13. Những câu hỏi thường gặp

**Q1.** Vì sao `Class.forName("java.lang.String")` hoạt động dù caller ở application loader?

**Đáp án:** Application loader có thể delegate qua platform/bootstrap để lấy `String`. Loader mặc định bắt đầu yêu cầu là của caller, nhưng class trả về do bootstrap định nghĩa.

**Q2.** Nếu TCCL trỏ vào plugin loader thì `Class.forName("plugin.Driver")` tự dùng plugin loader không?

**Đáp án:** Không. Overload một tham số dùng defining loader của class caller. Muốn dùng TCCL thì lấy `Thread.currentThread().getContextClassLoader()` và truyền qua overload ba tham số theo chính sách API.

**Q3.** Hai method đều gọi `Class.forName("pkg.Type")` mà kết quả khác nhau có sai không?

**Đáp án:** Không nhất thiết. Nếu hai method nằm trong class do các defining loader khác nhau nạp, API có thể tìm hai định nghĩa `pkg.Type` khác nhau. Danh tính JVM của class phụ thuộc **binary name + defining loader**.

**Q4.** `Class.forName(name, false, loader)` có bảo đảm class chưa từng khởi tạo không?

**Đáp án:** Không. Nó chỉ **không yêu cầu** initialization trong lần gọi này. Class có thể đã khởi tạo do một thao tác khác, và lời gọi có thể load/link những gì JVM cần.

**Q5.** `Class.forName("pkg.Example")` đã gọi constructor chưa?

**Đáp án:** Chưa. Class initializer `<clinit>` có thể chạy, còn instance constructor `<init>` chỉ chạy khi thực sự tạo instance bằng `new` hoặc reflection/cơ chế tương đương.

**Q6.** Tại sao `Class.forName("int")` không trả `int.class`?

**Đáp án:** `int` không phải binary name của một class có thể được loader tìm như class thông thường. Lấy primitive class qua `int.class` hoặc `Integer.TYPE`. Với array primitive có thể dùng tên array như `"[I"`.

**Q7.** Vì sao `Class.forName("[Lpkg.Component;")` không chạy static initializer của `Component` dù overload một tham số có `initialize=true`?

**Đáp án:** Với tên array, component type được load nhưng **không tự init** chỉ vì tìm array class. Lần sử dụng component theo thao tác có yêu cầu initialization mới kích hoạt.

**Q8.** `Class.forName(Module, String)` có tự động init không?

**Đáp án:** Không. Overload này không link hoặc chạy class initializer theo hợp đồng API, và nếu class không nằm trong module đích có thể trả `null`. Nó khác overload ba tham số có `initialize=true`.

**Q9.** Vì sao host ở parent loader không tìm được class trong plugin child loader dù plugin gọi API host thành công?

**Đáp án:** Parent delegation chủ yếu cho child hỏi parent, không tự làm parent thấy mọi class do child định nghĩa. Khi host cần class plugin, hãy truyền loader/plugin layer có chủ đích hoặc dùng cơ chế discovery phù hợp.

**Q10.** Có thể bắt mọi lỗi của `forName` bằng `catch (ClassNotFoundException e)` không?

**Đáp án:** Không. Lỗi linking và initialization có thể là `LinkageError`, gồm `ExceptionInInitializerError` hoặc `NoClassDefFoundError` trong tình huống cụ thể.

## 14. Bài tập ngắn có đáp án

**Bài 1.** Trong `ForNameDemo`, giải thích vì sao `Class.forName("ForNameDemo$Target")` thành công khi TCCL đã gán một loader không tìm được `Target`.

*Đáp án:* Caller là `ForNameDemo`, nên overload một tham số dùng defining loader của `ForNameDemo`. Nó không đọc TCCL. Custom loader `isolated` chỉ được dùng trong lời gọi ba tham số rõ ràng.

**Bài 2.** Một thư viện `FrameworkHelper` do system loader định nghĩa nhận tên class plugin `com.acme.PluginImpl` và gọi `Class.forName(name)`, nhưng trả `ClassNotFoundException`. Cần thay đổi ranh giới API ở đâu?

*Đáp án:* Cho plugin/module layer truyền đúng loader hoặc module context vào helper, sau đó dùng `Class.forName(name, initialize, loader)` hoặc API module tương ứng. Thay đổi TCCL của thread chưa đủ nếu helper vẫn dùng overload một tham số.

**Bài 3.** Code sau có thể in gì?

```java
Class<?> c = Class.forName("java.lang.String");
System.out.println(c.getClassLoader() == null);
```

*Đáp án:* Trong Java SE thông thường, in `true` vì `String` do bootstrap định nghĩa. Class caller có thể nằm ở application loader, nhưng loader của class kết quả được quyết định qua delegation.

**Bài 4.** `Class<?> c = Class.forName("pkg.Type", false, loader);` có ngăn `pkg.Type` được initialize về sau không?

*Đáp án:* Không. Nó tránh yêu cầu initialization **trong lần gọi hiện tại**. Lần đầu gọi static method, tạo instance hay thao tác yêu cầu khác sẽ buộc khởi tạo nếu class chưa được khởi tạo.

**Bài 5.** Vì sao loader dùng để trả `Class<?>` và khả năng dùng được constructor qua reflection là hai câu hỏi khác nhau?

*Đáp án:* Loading/class lookup cung cấp một đối tượng `Class<?>`. Constructor invocation còn cần class đã sẵn sàng để khởi tạo, constructor phù hợp và điều kiện truy cập Java/module. Các bước sau có thể thất bại dù tìm class đã thành công.

## Ghi nhớ

Khi thấy `Class.forName(name)`, hãy đọc nó thành **“tìm và yêu cầu khởi tạo class theo defining loader của class chứa lời gọi này”**. Nếu hệ thống có plugin, application server, custom `ModuleLayer` hoặc TCCL, hãy xác định rõ **class caller**, **defining loader**, **tên binary**, **loader mong muốn** và **có cần initialization ngay không**. Chọn overload ba tham số hoặc overload theo `Module` khi cần diễn đạt lựa chọn ấy một cách rõ ràng.

Nguồn tham khảo chuẩn:

- [Class – Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): hợp đồng `Class.forName(String)`, `Class.forName(String, boolean, ClassLoader)` và `Class.forName(Module, String)`.
- [ClassLoader – Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): parent delegation, `loadClass` và loader tích hợp.
- [JLS 17, 12.4 – Initialization of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4): thời điểm initialize và hành vi lỗi.
- [JVMS 17, 5 – Loading, Linking and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): defining/initiating loader, danh tính kiểu và cơ chế linking.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-api-007">Class.forName(name, initialize, loader)</a>

<details>
<summary>Xem chi tiết</summary>

# Class.forName(name, initialize, loader) — nạp lớp, khởi tạo và chọn ClassLoader

## 1. Vì sao cần overload ba tham số?

Java cho phép tìm một lớp theo **tên dạng chuỗi** vào lúc chạy. Phương thức:

```java
public static Class<?> forName(
        String name,
        boolean initialize,
        ClassLoader loader
) throws ClassNotFoundException
```

nằm trong `java.lang.Class`. Nó giải quyết ba quyết định tách biệt:

1. **`name`:** tên binary của class/interface hoặc chuỗi mô tả một kiểu mảng cần tìm.
2. **`initialize`:** có yêu cầu JVM thực hiện **khởi tạo lớp/interface** sau khi tìm và nạp thành công hay không.
3. **`loader`:** ClassLoader nào được dùng để tìm lớp; nếu truyền `null`, yêu cầu dùng bootstrap class loader.

Kết quả là một `Class<?>` — **đối tượng mô tả runtime type**. `Class.forName` không tạo instance của lớp đích, không gọi constructor instance của nó và cũng không bảo đảm ta có quyền gọi mọi thành viên của lớp qua reflection.

Ví dụ sử dụng trong ứng dụng plugin:

```java
// Trích đoạn: pluginLoader được tạo và cấu hình ở nơi khác.
Class<?> pluginType =
        Class.forName("com.example.plugin.Handler", false, pluginLoader);

// Đã có Class<?> của kiểu, nhưng yêu cầu này chưa khởi tạo Handler.
```

Nếu đổi `false` thành `true`, phương thức sẽ **yêu cầu khởi tạo Handler nếu Handler chưa khởi tạo**. Điều này có thể chạy các phép gán field `static` và khối `static`, kéo theo code ứng dụng, I/O hoặc exception. Khác biệt này đặc biệt quan trọng với ứng dụng có plugin, service registry và các thư viện dùng reflection.

## 2. Luồng xử lý khái niệm

Với `Class.forName(name, initialize, loader)`, JVM phải tìm runtime type tương ứng và tuân theo các quy tắc loading/linking/initialization:

```text
Class.forName(name, initialize, loader)
       │
       ├─ Xác định dạng tên:
       │      ├─ Binary name của class/interface
       │      └─ Descriptor của array type
       │
       ├─ Sử dụng loader được chỉ định để tìm/nạp type
       │      └─ Chính sách ủy quyền tùy ClassLoader cụ thể
       │
       ├─ Liên kết/kiểm tra theo yêu cầu JVM
       │      └─ lỗi loading/linking vẫn có thể xảy ra
       │
       └─ Nếu là class/interface thông thường:
              ├─ initialize=false → không yêu cầu khởi tạo
              └─ initialize=true  → khởi tạo nếu chưa hoàn tất
                                      └─ static field initializers / blocks
          Nếu name là array descriptor:
              → JVM tạo/nhận array class;
                element class được nạp nhưng không
                bị khởi tạo chỉ vì lời gọi forName này
       │
       ▼
Trả Class<?> hoặc ném exception/error
```

Đây là **sơ đồ ngữ nghĩa**, không phải mô tả máy móc thứ tự mọi bước verification/resolution. JVM có thể trì hoãn việc phân giải một số symbolic references cho đến khi cần. Bảo đảm quan trọng là khi cần khởi tạo một class, nó phải được verification và preparation theo quy định trước.

`Class.forName` **không tự chọn ngẫu nhiên loader phù hợp**. Người gọi quyết định tham số `loader`; chính loader đó và chuỗi ủy quyền của nó quyết định có tìm được class nào.

## 3. Tham số name: binary name và descriptor của mảng

### 3.1. Binary name của lớp thông thường

Với class top-level:

```java
Class<?> c = Class.forName(
        "java.util.ArrayList",
        false,
        ClassLoader.getSystemClassLoader()
);
```

`name` là `"java.util.ArrayList"`, **không phải** đường dẫn `"java/util/ArrayList.class"`, chuỗi `"ArrayList.class"` hay source declaration.

Với nested/member class, binary name thường dùng ký tự `$`. Ví dụ trong file `Outer.java`:

```java
public class Outer {
    static class Inner {
    }
}
```

Binary name của class thành viên `Inner` là `Outer$Inner` (với package nếu có). Khi gọi:

```java
// Trích đoạn, giả sử class Outer đã nằm trong classpath.
Class<?> inner = Class.forName(
        "Outer$Inner",
        false,
        Outer.class.getClassLoader()
);
```

`"Outer.Inner"` là cách viết theo tên chuẩn nguồn, **không phải binary name** hợp lệ của member class trong tình huống này. Tên của anonymous/local class còn có đặc điểm do compiler sinh; nên lấy `SomeClass.class.getName()` khi đã có `Class<?>` thay vì đoán tên.

Đối với generic types, tên `"java.util.List<java.lang.String>"` cũng không hợp lệ cho `forName`; generic arguments không tạo runtime class riêng, do type erasure. Dùng `"java.util.List"`.

### 3.2. Tên kiểu mảng là descriptor

Đối với array class, `name` không viết bằng `"java.lang.String[]"`. Nó dùng cú pháp JVM descriptor:

| Kiểu Java | Chuỗi truyền `forName` |
|---|---|
| `int[]` | `"[I"` |
| `boolean[]` | `"[Z"` |
| `long[][]` | `"[[J"` |
| `String[]` | `"[Ljava.lang.String;"` |
| `String[][]` | `"[[Ljava.lang.String;"` |
| `mypackage.Widget[]` | `"[Lmypackage.Widget;"` |

Một dấu `[` biểu thị một chiều. `L...;` bao tên binary của reference component. Mã primitive thông dụng gồm `Z`, `B`, `C`, `D`, `F`, `I`, `J`, `S` cho boolean, byte, char, double, float, int, long, short.

**Quy tắc đặc biệt:** nếu `name` biểu diễn **array class**, class phần tử có thể được nạp nhưng **không khởi tạo**, **bất kể `initialize=true` hay `false`**. Lời gọi `Class.forName("[Lmypackage.Widget;", true, loader)` không phải một lối tắt để chạy `Widget.<clinit>`.

### 3.3. Primitive và void không được tìm bằng overload này

Các tên như `"int"`, `"boolean"`, `"void"` và descriptor chữ `"I"` khi dùng làm `name` đơn lẻ **không** trả `int.class` hoặc `void.class`. API sẽ cố tìm class theo tên đó; thông thường sẽ nhận `ClassNotFoundException`.

Muốn lấy đối tượng `Class` của primitive hoặc void, dùng:

```java
Class<?> integerType = int.class;
Class<?> voidType = void.class;
```

Nếu cần primitive array, dùng `Class.forName("[I", false, loader)` hoặc `int[].class`.

## 4. Tham số initialize: false và true khác nhau thế nào?

| `initialize` | Với class/interface thông thường | Với array descriptor |
|---|---|---|
| `false` | Yêu cầu tìm/nạp class, **không** yêu cầu chạy initializer | Tạo/nhận array class; không khởi tạo class phần tử |
| `true` | Sau khi tìm/nạp, yêu cầu initialization nếu chưa hoàn tất | Class phần tử vẫn **không** bị khởi tạo bởi lời gọi này |

### 4.1. Khi initialize=false

`Class.forName(name, false, loader)` có thể trả `Class<?>` để ta đọc metadata như `getName()`, `getDeclaredMethods()` hoặc kiểm tra type identity mà không chủ động khởi tạo lớp đích. Tuy nhiên:

- Lớp có thể **đã được khởi tạo trước đó** bởi lời gọi khác; `false` không “hoàn tác” trạng thái.
- JVM có thể vẫn phải **nạp/liên kết** lớp và các kiểu liên quan; `false` không phải cam kết “không có hoạt động nào khác” hoặc “không thể phát sinh lỗi”.
- Hoạt động reflection sau đó như gọi static method, đọc static field hoặc tạo instance có thể yêu cầu initialization.
- Custom loader có thể có **side effect riêng khi load**; `false` chỉ điều khiển yêu cầu khởi tạo của lớp đích theo ngữ nghĩa API, không vô hiệu hóa mọi code do loader tự viết.

### 4.2. Khi initialize=true

`Class.forName(name, true, loader)` yêu cầu JVM khởi tạo lớp/interface đích **nếu nó chưa được khởi tạo**. Với class, khởi tạo bao gồm các biểu thức khởi tạo static fields và `static { ... }` theo thứ tự nguồn; các superclass thích hợp được khởi tạo trước theo quy tắc JVM/JLS.

Không diễn giải `true` là "chạy lại static block mỗi lần gọi": initialization thành công được ghi nhận **một lần cho mỗi runtime class/interface**. Với cùng `Class<?>`, gọi lại `forName(..., true, ...)` không chạy lại `<clinit>`.

Nếu initializer có lỗi, `Class.forName` có thể ném `ExceptionInInitializerError` khi lần đầu khởi tạo (hoặc truyền lỗi thuộc họ `Error` theo quy tắc JLS). Runtime class thất bại trở thành erroneous; lần yêu cầu khởi tạo tiếp theo có thể gặp `NoClassDefFoundError` thay vì retry.

## 5. Ví dụ chạy được: quan sát rõ false → true → true

Tạo file `ForNameDemo.java`:

```java
public class ForNameDemo {
    static class Target {
        static int counter = initializeCounter();

        private static int initializeCounter() {
            System.out.println("Target initialized");
            return 7;
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader loader = ForNameDemo.class.getClassLoader();

        System.out.println("start");
        Class<?> type = Class.forName(
                "ForNameDemo$Target", false, loader
        );
        System.out.println("loaded: " + type.getSimpleName());

        System.out.println("before init");
        Class<?> again = Class.forName(
                "ForNameDemo$Target", true, loader
        );
        System.out.println("same Class: " + (type == again));

        Class.forName("ForNameDemo$Target", true, loader);
        System.out.println("counter=" + Target.counter);
    }
}
```

Dùng JDK 17+:

```bash
javac ForNameDemo.java
java ForNameDemo
```

Stdout:

```text
start
loaded: Target
before init
Target initialized
same Class: true
counter=7
```

**Diễn giải:**

1. Lời gọi đầu có `false`: JVM trả `Class<?>` của `Target`, nhưng không yêu cầu chạy `initializeCounter()`.
2. `type.getSimpleName()` đọc metadata của `Class<?>`, không chạy static initializer của Target.
3. Lời gọi thứ hai có `true`: yêu cầu khởi tạo Target, nên in `Target initialized` đúng một lần và gán `Target.counter=7`.
4. `type == again` là `true` vì cả hai biểu diễn cùng một runtime class.
5. Lời gọi `true` thứ ba không chạy lại initializer; đọc `Target.counter` in giá trị 7.

**Không phải** `initialize=false` nghĩa là "chỉ trả tên", hay `initialize=true` nghĩa là "tạo instance". Cả hai trả đối tượng `Class<?>`; khác nhau ở yêu cầu initialization.

## 6. Tham số loader: chọn không gian tên runtime

### 6.1. Tại sao phải truyền loader tường minh?

Một class được JVM nhận diện về cơ bản bởi **binary name cùng defining ClassLoader**. Hai loader độc lập có thể định nghĩa hai lớp cùng tên nhưng không phải cùng runtime class; ép kiểu hoặc liên kết sai giữa chúng có thể thất bại dù chuỗi tên giống nhau.

Trong `Class.forName(name, initialize, loader)`, tham số `loader` quyết định loader **bắt đầu quá trình tìm lớp**. Loader đó có thể áp dụng parent-first delegation và trả một lớp **do parent định nghĩa**. Vì vậy:

```java
Class<?> type = Class.forName(name, false, pluginLoader);
ClassLoader definingLoader = type.getClassLoader();
```

Đây là trích đoạn, cần có `name` và `pluginLoader` ở nơi gọi. `definingLoader` **không bắt buộc bằng** `pluginLoader`: có thể lớp được parent/bootstrap định nghĩa. Hãy kiểm tra `type.getClassLoader()` khi tìm nguyên nhân dùng nhầm thư viện.

Nếu truyền **`loader=null`**, API dùng **bootstrap class loader**. Đây không phải lựa chọn "mặc định linh hoạt" hay "dùng system loader". Chẳng hạn:

```java
Class<?> stringType =
        Class.forName("java.lang.String", false, null);
System.out.println(stringType == String.class);
```

Trong method Java có `throws ClassNotFoundException` hoặc bắt exception tương ứng, stdout là `true`. Nhưng một class plugin chỉ nằm trong directory của custom loader thông thường sẽ không được bootstrap tìm thấy.

### 6.2. So với overload một tham số

`Class.forName("mypackage.Handler")` về mặt ngữ nghĩa tương đương `Class.forName("mypackage.Handler", true, currentLoader)`, với `currentLoader` là **defining loader của lớp chứa lời gọi** theo định nghĩa của API. Nó không tự lấy `Thread.currentThread().getContextClassLoader()`.

So sánh:

```java
// Trích đoạn: class và loader phải tồn tại ở context phù hợp.

Class<?> a = Class.forName("mypackage.Handler");
// Yêu cầu initialize=true và dùng loader của lớp gọi.

Class<?> b = Class.forName(
        "mypackage.Handler", false, explicitlyChosenLoader
);
// Không yêu cầu initialize; dùng loader được truyền tường minh.
```

Một thread context class loader (TCCL) có thể được framework thiết lập để hỗ trợ SPI/plugin. Nếu muốn dùng nó, phải **chủ động truyền**:

```java
ClassLoader context =
        Thread.currentThread().getContextClassLoader();

Class<?> type = Class.forName(
        "mypackage.Handler", false, context
);
```

Đây là **trích đoạn**, không phải chương trình độc lập. Chú ý `context` có thể `null`; truyền `null` vào overload ba tham số có nghĩa là dùng bootstrap loader, nên ứng dụng plugin cần chọn/kiểm tra fallback rõ ràng trước khi gọi.

### 6.3. Không nhất thiết được truy cập class vừa tìm

`Class.forName` **không kiểm tra trước liệu caller có quyền truy cập class đích**. Việc có `Class<?>` không cấp quyền gọi constructor hoặc member `private`/package-private. Quyền truy cập Java, quy tắc module và ràng buộc reflection vẫn áp dụng về sau. Dùng `setAccessible(true)` không mặc nhiên vượt qua module encapsulation của JDK hiện đại.

## 7. Ví dụ chạy được: tìm class trong thư mục plugin qua URLClassLoader

Ví dụ này minh họa lựa chọn loader tường minh và tác dụng của cờ `initialize`. Ta biên dịch class plugin **ra thư mục không nằm trên host classpath**, rồi dùng `URLClassLoader` để đọc nó.

### 7.1. Tạo Plugin.java

```java
package demo;

public final class Plugin {
    static {
        System.out.println("Plugin initialized");
    }

    public static String greet() {
        return "Hello plugin";
    }
}
```

### 7.2. Tạo PluginHost.java

```java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class PluginHost {
    public static void main(String[] args) throws Exception {
        URL pluginDir = Path.of("plugin-classes").toUri().toURL();
        ClassLoader parent = PluginHost.class.getClassLoader();

        try (URLClassLoader pluginLoader =
                     new URLClassLoader(new URL[] { pluginDir }, parent)) {

            System.out.println("before loading");

            Class<?> type = Class.forName(
                    "demo.Plugin", false, pluginLoader
            );
            System.out.println("defined by plugin loader: "
                    + (type.getClassLoader() == pluginLoader));

            System.out.println("before initialization");
            Class<?> initialized = Class.forName(
                    "demo.Plugin", true, pluginLoader
            );
            System.out.println("same runtime class: "
                    + (type == initialized));

            Class.forName("demo.Plugin", true, pluginLoader);
            Object message = type.getMethod("greet").invoke(null);
            System.out.println(message);
        }
    }
}
```

### 7.3. Biên dịch và chạy

Trong thư mục chứa `Plugin.java` và `PluginHost.java`:

```bash
mkdir plugin-classes
javac -d plugin-classes Plugin.java
javac PluginHost.java
java -cp . PluginHost
```

Kết quả file:

```text
working-directory/
├── Plugin.java
├── PluginHost.java
├── PluginHost.class
└── plugin-classes/
    └── demo/
        └── Plugin.class
```

**Không thêm thư mục `plugin-classes` vào host classpath**. `java -cp . PluginHost` chỉ có host class trên classpath. Nếu thêm plugin class vào host classpath, parent có thể nạp nó trước và phép so sánh defining loader sẽ cho kết quả khác, vì `URLClassLoader` mặc định dùng parent-first.

Stdout khi chạy trong JVM mới với cấu trúc trên:

```text
before loading
defined by plugin loader: true
before initialization
Plugin initialized
same runtime class: true
Hello plugin
```

**Ý nghĩa kết quả:**

- `forName("demo.Plugin", false, pluginLoader)` khiến loader có thể tìm và định nghĩa Plugin, **chưa yêu cầu chạy** static block.
- `getClassLoader()==pluginLoader` là `true` vì parent không tìm thấy Plugin, và URLClassLoader tự định nghĩa class đó.
- `forName(..., true, pluginLoader)` yêu cầu khởi tạo lần đầu; static block in `Plugin initialized`.
- Lần gọi `true` tiếp theo trả cùng runtime class và không chạy lại static block.
- `getMethod("greet").invoke(null)` gọi static method và nhận chuỗi; Plugin đã initialized nên không có lần chạy initializer khác.
- Đóng `URLClassLoader` qua try-with-resources giải phóng tài nguyên URL/JAR mà loader nắm giữ; **không phải lệnh unload ngay lập tức** lớp đã định nghĩa. Unloading phụ thuộc khả năng JVM thu gom loader/class khi không còn reachable.

Nếu muốn có một bản `demo.Plugin` thứ hai cùng tên nhưng danh tính khác, dùng một defining loader **mới** không ủy quyền cho parent đã có Plugin. Không thể tạo định nghĩa khác trong cùng loader chỉ bằng cách gọi `forName` lại.

## 8. Ví dụ chạy được: tên array descriptor không khởi tạo element class

Tạo file `ForNameArrayDemo.java`:

```java
public class ForNameArrayDemo {
    static class Element {
        static {
            System.out.println("Element initialized");
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader loader = ForNameArrayDemo.class.getClassLoader();

        System.out.println("before array forName");
        Class<?> arrayType = Class.forName(
                "[LForNameArrayDemo$Element;",
                true,
                loader
        );
        System.out.println("array type: " + arrayType.getTypeName());
        System.out.println("component name: "
                + arrayType.getComponentType().getSimpleName());

        System.out.println("before component initialization");
        Class.forName(
                "ForNameArrayDemo$Element", true, loader
        );
        System.out.println("done");
    }
}
```

Chạy bằng JDK 17+:

```bash
javac ForNameArrayDemo.java
java ForNameArrayDemo
```

Stdout:

```text
before array forName
array type: ForNameArrayDemo$Element[]
component name: Element
before component initialization
Element initialized
done
```

Dù lời gọi đầu dùng `initialize=true`, tên truyền vào là **array descriptor**. Java tạo/nhận array class, tải element type khi cần nhưng **không chạy Element static initializer** từ thao tác này. Static block chỉ chạy khi lời gọi thứ hai yêu cầu **chính Element** khởi tạo.

Đối với primitive array:

```java
Class<?> primitiveArray = Class.forName(
        "[I", true, ForNameArrayDemo.class.getClassLoader()
);
System.out.println(primitiveArray == int[].class);
```

Đây là trích đoạn cho method đã khai báo `throws ClassNotFoundException`; dòng `println` cho `true` trên JVM Java SE chuẩn. Không có initializer của primitive `int`.

## 9. Trường hợp lỗi khi tìm/nạp/khởi tạo

| Lỗi hoặc exception | Thời điểm thường xuất hiện | Nguyên nhân cần kiểm tra |
|---|---|---|
| `ClassNotFoundException` | Không tìm được tên qua loader đã chỉ định | Binary name, plugin directory/JAR, parent delegation, module/classpath |
| `LinkageError` | Loading/linking không đáp ứng quy tắc JVM | Bytecode không tương thích, định nghĩa trùng, xung đột binary dependencies |
| `UnsupportedClassVersionError` | Bytecode mới hơn JVM hỗ trợ | Major/minor class file, Java runtime thực sự chạy |
| `ExceptionInInitializerError` | Static initializer ném exception không thuộc `Error` khi init | Cause gốc từ `<clinit>` hoặc biểu thức gán static |
| `NoClassDefFoundError` sau initialization lỗi | Class runtime ở trạng thái erroneous | Lỗi init đầu tiên, chứ không chỉ classpath |
| `SecurityException` | Khi các điều kiện kiểm tra quyền bảo mật phù hợp tồn tại | Security manager/permission, môi trường đang dùng |

`ClassNotFoundException` là **checked exception**; code gọi `forName` cần `throws` hoặc `catch`. Các lỗi thuộc họ `LinkageError` không được khai báo trong `throws` nhưng vẫn có thể xảy ra. Không bắt tất cả lỗi rồi coi là “không có class”.

### 9.1. Ví dụ khởi tạo thất bại

Tạo `ForNameFailureDemo.java`:

```java
public class ForNameFailureDemo {
    static class Broken {
        static int state = failure();

        static int failure() {
            throw new IllegalStateException("bad initialization");
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader loader = ForNameFailureDemo.class.getClassLoader();
        String name = "ForNameFailureDemo$Broken";

        System.out.println("loaded without initialization: "
                + (Class.forName(name, false, loader) != null));

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                Class.forName(name, true, loader);
            } catch (LinkageError error) {
                System.out.println(
                        attempt + ": " + error.getClass().getSimpleName()
                );
            }
        }
    }
}
```

Chạy:

```bash
javac ForNameFailureDemo.java
java ForNameFailureDemo
```

Stdout:

```text
loaded without initialization: true
1: ExceptionInInitializerError
2: NoClassDefFoundError
```

Vì `initialize=false` không yêu cầu chạy `failure()`, lời gọi đầu thành công. Lời gọi `true` lần đầu kích hoạt code và ném `IllegalStateException`; JVM bọc bằng `ExceptionInInitializerError`. Khi runtime class đã erroneous, lần gọi `true` kế tiếp không chạy initializer lại, thường ném `NoClassDefFoundError`.

Đây là lý do phải kiểm tra **cause của lần lỗi đầu**, không chỉ thông báo ở những lần truy cập sau. Nếu muốn thử lại trong học tập, chạy một process JVM mới; dùng lại cùng runtime class trong cùng process không tự phục hồi state.

## 10. Phân biệt với ClassLoader.loadClass

Hai API đều có thể trả `Class<?>`, nhưng đối tượng điều khiển khác nhau:

| Tiêu chí | `Class.forName(name, initialize, loader)` | `loader.loadClass(name)` |
|---|---|---|
| Gọi qua | Static method của `Class` | Method của `ClassLoader` |
| Chọn loader | Truyền tường minh, `null` nghĩa bootstrap | Dùng chính object loader gọi method |
| Mặc định yêu cầu initialization? | Phụ thuộc tham số `initialize` | Không; public overload tương đương `loadClass(name, false)` |
| Nhận array descriptor? | Có, cú pháp như `[Ljava.lang.String;` | Không phải con đường portable để yêu cầu array type từ custom loader |
| Kết quả | `Class<?>` | `Class<?>` |
| Lỗi khi class thiếu | `ClassNotFoundException` | `ClassNotFoundException` |
| Có tạo instance? | Không | Không |

**Không đánh đồng `initialize=true` với `resolve=true`** trong protected `ClassLoader.loadClass(name, resolve)`. `resolveClass(c)` yêu cầu liên kết, còn initialization có thêm việc chạy static initializer. Tương tự `getDeclaredMethods()`, `getAnnotations()` hoặc các thao tác phản chiếu khác có các điều kiện loading/linking riêng; đọc metadata không tự động đồng nghĩa gọi `<clinit>`.

### Khi nào ưu tiên API nào?

- Có **binary name** và muốn chắc chắn lớp được khởi tạo: `Class.forName(name, true, chosenLoader)`.
- Có tên và chỉ muốn khám phá metadata/chưa muốn static side effects: `Class.forName(name, false, chosenLoader)`.
- Đang triển khai một ClassLoader và chỉ cần tìm lớp theo quy tắc loader: `loader.loadClass(name)`.
- Biết chính xác class lúc compile: thường dùng `SomeType.class`, tránh tìm theo chuỗi nếu không cần động.
- Có array component `Class<?>` khi làm plugin: dùng `java.lang.reflect.Array.newInstance(componentClass, size)` để nhận array object/array class đúng defining loader.

## 11. Class.forName(Module, String) là API khác

Từ Java 9, có overload:

```java
public static Class<?> forName(Module module, String name)
```

Nó **không tương đương** với overload ba tham số:

- Tìm class/interface với binary name trong **module được chỉ định**.
- Không khởi tạo class; theo tài liệu API, nó không thực hiện linking theo cách overload ba tham số yêu cầu.
- Nếu không tìm thấy class đúng module, có thể trả `null` thay vì ném `ClassNotFoundException`.
- Nó không hỗ trợ array descriptor như `"[Ljava.lang.String;"`.
- Việc có `Class<?>` vẫn không trao quyền truy cập member bị giới hạn bởi module/package.

Do đó, khi làm hệ module, không nên thay thẳng `Class.forName(name, false, loader)` thành `Class.forName(module, name)` rồi giả định cùng contract.

## 12. Những vấn đề thực tế với plugin và framework

### 12.1. Tên đúng nhưng loader sai

Một plugin chứa `com.acme.Plugin` nhưng host gọi:

```java
Class.forName("com.acme.Plugin");
```

Nếu loader của host không tìm thấy lớp plugin, lời gọi thất bại với `ClassNotFoundException`, dù file plugin thực sự có trên máy. Giải pháp là xác định **ClassLoader phù hợp** của plugin và truyền cho overload ba tham số.

Một vấn đề ngược lại: cả host và plugin cùng chứa một lớp API chung. Nếu custom loader dùng child-first cho API đó, hai runtime classes cùng binary name có thể không cast qua lại; `getName()` bằng nhau không có nghĩa `Class<?>` bằng nhau. Nên giữ các API giao tiếp giữa host/plugin do loader chung phù hợp định nghĩa.

### 12.2. initialize=true có tác dụng phụ

Với plugin registry, gọi `forName(name, true, loader)` trong lúc *quét danh sách* có thể chạy static block ngay cả khi plugin chưa được chọn sử dụng. Nếu initializer tạo thread, kết nối database, ghi log hoặc cấu hình global state, việc quét đơn thuần có thể trở thành thao tác tốn kém hoặc gây lỗi.

Một cách phân tách trách nhiệm:

1. **Discovery:** tìm class bằng `initialize=false`, đọc metadata và kiểm tra điều kiện cần.
2. **Validation:** xác định class phù hợp API, module/loader và kiểu constructor/method cần thiết.
3. **Activation:** khi thực sự dùng plugin, thực hiện operation yêu cầu initialization và tạo instance phù hợp.
4. **Failure handling:** phân biệt class missing, linkage incompatible, initialization failed và lỗi application logic.

Dù `false` tránh yêu cầu init của **class đích**, việc khám phá bằng reflection vẫn có thể tải các kiểu liên quan, và một số framework method tự có tác dụng phụ. Không đặt niềm tin tuyệt đối rằng discovery là thao tác không thể ném lỗi.

### 12.3. Parent-first hay child-first là chuyện của loader

`Class.forName` **không thay đổi chính sách delegation**. Nếu custom loader kế thừa `ClassLoader.loadClass` mặc định, nó ưu tiên `findLoadedClass`, rồi loader cha và mới đến `findClass`. Nếu loader override `loadClass` với quy tắc child-first có điều kiện, `Class.forName` sử dụng behavior thực của loader đó.

Muốn chẩn đoán, log cả:

```java
// Trích đoạn sau khi đã có Class<?> type:
System.out.println(type.getName());
System.out.println(type.getClassLoader());
System.out.println(type.getModule());
```

Bộ ba tên/defining loader/module hỗ trợ xác định class runtime đã tìm được, nhất là khi nhiều JAR có cùng binary name.

## 13. Checklist chẩn đoán Class.forName

Khi một lệnh `Class.forName` có kết quả khác dự kiến, kiểm tra theo thứ tự:

1. **Chuỗi `name`** có đúng **binary name** không? Nếu nested class, có dùng `$`? Nếu mảng, có dùng descriptor với `[` và `L...;`? Có vô tình đưa `.class`, dấu `/` hoặc generic arguments vào tên?
2. **Loader truyền vào** là gì? Có phải `null`, loader của host, TCCL hay plugin-specific loader? Parent có tìm được lớp khác cùng tên trước không?
3. **`initialize`** là `true` hay `false`? Static block có thể chạy từ lần dùng trước đó không? Có đang gọi array descriptor khiến element vẫn chưa initialize không?
4. **Runtime type đã được xác định** bằng `type.getName()`, `type.getClassLoader()` và nếu phù hợp `type.getModule()` chưa?
5. **Lỗi thuộc nhóm nào**: `ClassNotFoundException`, `LinkageError`, `ExceptionInInitializerError` hay lỗi reflection/access? Đọc đầy đủ cause và lỗi đầu tiên, không chỉ lần gọi lại.
6. **JDK/runtime** đúng phiên bản cho bytecode và module access chưa? `ClassNotFoundException` không phải lời giải thích duy nhất cho mọi lỗi loading.
7. **Test trong JVM mới** nếu đang kiểm tra thứ tự khởi tạo: một class đã initialized sẽ không chạy static block lần nữa trong cùng runtime class.
8. **Kiểm tra dependency của plugin**: tìm được entry-point class chưa đảm bảo mọi class mà nó sử dụng sẽ liên kết và chạy thành công.
9. **Không dùng `Class.forName` để “hot reload”** một class đã định nghĩa; cần thiết kế vòng đời plugin/loader và cơ chế reload phù hợp.

## 14. Câu hỏi tự kiểm tra

1. **`Class.forName("p.Service", false, loader)` có chạy `Service.<clinit>` không?** Không chủ động yêu cầu; Service có thể đã được khởi tạo trước.
2. **`Class.forName("p.Service", true, loader)` trả đối tượng Service không?** Không; nó trả `Class<?>` và có thể chạy static initializer.
3. **Gọi `forName` hai lần với `true` có chạy `<clinit>` hai lần không?** Không, nếu cùng runtime class đã init thành công.
4. **Truyền `loader=null` có dùng system class loader không?** Không; overload này chọn bootstrap loader.
5. **`Class.forName("Outer.Inner")` có đúng để tìm member class `Outer.Inner` trong source không?** Thông thường không; binary name là `Outer$Inner`.
6. **`Class.forName("int")` có trả `int.class` không?** Không; dùng `int.class` hoặc API tra primitive riêng khi có hỗ trợ.
7. **Muốn tìm `String[][]` qua overload ba tham số thì name là gì?** `"[[Ljava.lang.String;"`.
8. **`Class.forName("[Lp.Widget;", true, loader)` có buộc khởi tạo Widget không?** Không; array component class không bị khởi tạo bởi lời gọi này.
9. **Dùng `Class.forName("p.Service")` có ngầm lấy TCCL không?** Không; nó dùng defining loader của caller theo contract overload một tham số.
10. **Một plugin class được parent định nghĩa thì `type.getClassLoader()` có luôn bằng loader được truyền vào forName không?** Không; parent/boot có thể là defining loader.
11. **Tìm được `Class<?>` là đã được phép invoke mọi private method không?** Không; Java access và module boundaries vẫn áp dụng.
12. **`Class.forName(Module, String)` không tìm thấy class thì ném CNFE giống overload ba tham số không?** Không; nó có thể trả `null` và có contract riêng.

## 15. Tài liệu chuẩn

- [Java SE 21 API — `java.lang.Class.forName(String, boolean, ClassLoader)`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html): hợp đồng từng tham số, array descriptors, exception và khác biệt overload.
- [Java SE 26 API — `java.lang.Class`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/Class.html): tài liệu API mới, bao gồm `forPrimitiveName(String)` ở những phiên bản hỗ trợ.
- [Java SE 21 API — `java.lang.ClassLoader`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): loading, parent-first, `findLoadedClass`, `findClass`, defining loader.
- [JLS — 12.2 Loading; 12.3 Linking; 12.4 Initialization](https://docs.oracle.com/javase/specs/jls/se26/html/jls-12.html): quy tắc khởi tạo static state, superclass, reflection và loading/linking.
- [JVMS — 5 Loading, Linking, and Initializing](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html): type identity, link/initialize và trạng thái lỗi.

Điểm cần nhớ khi thiết kế ứng dụng có ClassLoader động là **ba tham số kiểm soát ba thứ khác nhau**: `name` xác định kiểu được hỏi, `loader` xác định đường tìm và danh tính runtime có thể nhận được, `initialize` quyết định có yêu cầu chạy logic khởi tạo hay chưa. Phân biệt chúng sẽ tránh cả lỗi không tìm thấy plugin lẫn việc chạy initializer quá sớm.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-api-008">Class.forName(Module, name) và trường hợp không tìm thấy lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Class.forName(Module, name) và trường hợp không tìm thấy lớp

## 1. Vấn đề cần giải quyết

Trong Java 9+ (bao gồm Java 17 và Java 21), ngoài các overload quen thuộc của `Class.forName`, Java cung cấp:

~~~java
public static Class<?> forName(Module module, String name)
~~~

Đây là API **tìm lớp theo một `Module` cụ thể**, phù hợp khi ứng dụng cần làm việc với **Java Platform Module System (JPMS)**. Nó có hai khác biệt đặc biệt dễ nhầm:

- **Không tìm thấy lớp trong module chỉ định → trả về `null`.** Đây **không** phải đường đi thông thường ném `ClassNotFoundException` như `Class.forName(String)` hoặc `Class.forName(String, boolean, ClassLoader)`.
- **Không tự liên kết hoặc khởi tạo lớp.** Theo hợp đồng của overload này, phương thức cố gắng **locate/load** class hoặc interface, **không link class, không chạy class initializer**.

Ngoài trường hợp thực sự không có định nghĩa lớp, `null` cũng có thể là kết quả khi tên lớp thuộc **module khác**, kể cả trong trường hợp loader của module được yêu cầu có thể nạp lớp đó. Bởi vậy, từ `result == null` **không thể suy ra duy nhất** rằng bytecode hoặc JAR bị thiếu.

### Bạn sẽ học được

1. Hiểu chính xác ý nghĩa hai tham số `Module module` và `String name`.
2. Phân biệt **đúng module**, **sai module** và **không có lớp theo tên**.
3. Xử lý `null` an toàn, tránh `NullPointerException` do lập tức gọi method trên kết quả.
4. Phân biệt overload theo `Module` với overload theo tên và `ClassLoader`.
5. Giải thích việc tìm được `Class<?>` **không chứng minh** đã liên kết, khởi tạo hoặc được phép truy cập lớp.
6. Biên dịch và chạy ví dụ JPMS Java 17+ kiểm chứng đủ bốn trường hợp: tìm được, thiếu tên, sai module, và không kích hoạt initialization.

## 2. Ý nghĩa của API và các tham số

~~~java
Class<?> result = Class.forName(module, "com.example.MyService");
~~~

- `module` là **đối tượng `java.lang.Module`**, không phải tên module dạng `String`. Có thể lấy từ `SomeClass.class.getModule()` hoặc từ `ModuleLayer.findModule(...)` sau khi module đã được resolve.
- `name` là **binary name** của class/interface được tìm, ví dụ `"java.lang.String"` hoặc `"com.example.Outer$Inner"` đối với lớp lồng nhau.
- Kết quả kiểu `Class<?>` nếu tên thuộc lớp nằm trong module được yêu cầu và việc tìm/nạp thành công.
- Kết quả `null` nếu **không tìm được class trong module được chỉ định**.
- Phương thức được giới thiệu từ **Java 9**.

Cần hiểu “tìm trong module” là **giới hạn xác định kết quả trả về**; một loader có thể định nghĩa nhiều named modules hoặc một named và một unnamed module. Việc chỉ truyền đúng loader chưa đủ để biết class thuộc module nào.

### Module, package, ClassLoader và class là bốn thứ khác nhau

~~~text
ModuleLayer
  └── Module "shop.api"
       ├── package "shop.api"
       │    └── class "shop.api.Service"
       └── ánh xạ tới một defining ClassLoader

Class.forName(module, "shop.api.Service")
    │
    ├── định nghĩa lớp đúng thuộc module?  -> Class<?>
    └── không có lớp đúng module?          -> null
~~~

Một `Module` có tên là **named module**. Một class thông thường trên classpath thuộc **unnamed module** của defining loader. API nhận chính đối tượng `Module` và áp dụng cho module phù hợp, không chỉ nhận một tên module trên module path.

Với named module, hãy bắt đầu từ thông tin thực tế:

~~~java
Module actual = SomeType.class.getModule();
System.out.println(actual.getName());  // tên module, hoặc null nếu unnamed
System.out.println(actual.isNamed());   // true với named module
~~~

Hai dòng là **mã minh họa bên trong một phương thức**; chưa phải chương trình runnable độc lập.

## 3. Trường hợp trả về `null`

### 3.1. Tên lớp thực sự không tồn tại trong module

~~~java
Module base = String.class.getModule(); // java.base

Class<?> missing =
        Class.forName(base, "java.lang.NoSuchType");

System.out.println(missing == null);    // true
~~~

Điều này khác với:

~~~java
Class.forName("java.lang.NoSuchType");
~~~

Overload một tham số trên sẽ ném `ClassNotFoundException` (nếu không có lớp cùng tên và không phát sinh lỗi khác).

Khi dùng overload theo module, **`null` là giá trị kết quả hợp lệ cần xử lý**, không phải một exception cần catch.

### 3.2. Lớp tồn tại nhưng nằm trong module khác

Ví dụ:

~~~java
Module base = String.class.getModule(); // java.base
Class<?> other = Class.forName(
        base, "java.util.logging.Logger");
System.out.println(other == null); // true
~~~

`java.util.logging.Logger` là một class có thật, nhưng thuộc **module `java.logging`**, không thuộc `java.base`. Kết quả vì thế là `null` trong cấu hình Java tiêu chuẩn của ví dụ.

Nếu truyền đúng module:

~~~java
Module logging = java.util.logging.Logger.class.getModule();
Class<?> found = Class.forName(
        logging, "java.util.logging.Logger");
System.out.println(found == java.util.logging.Logger.class); // true
~~~

**Điểm tinh tế của hợp đồng:** nếu ClassLoader được gán cho module `M` cũng định nghĩa các module khác, phương thức có thể **load thành công** một class trong module khác rồi vẫn trả về `null` vì class đó **không thuộc `M`**. Kết quả `null` không bắt buộc có nghĩa loader không tìm được bytecode.

Vì vậy, không nên dùng:

~~~java
if (Class.forName(requestedModule, name) == null) {
    // KHÔNG đủ bằng chứng để khẳng định "JAR không tồn tại"
}
~~~

Đoạn trên là ví dụ về **suy luận không hợp lệ**, không phải chương trình độc lập. Cần kiểm tra đúng module, tên nhị phân, module resolution và các nguồn chứa class trước khi kết luận.

### 3.3. Tên không đúng định dạng kỳ vọng hoặc không phải class được hỗ trợ

API yêu cầu **binary name** của class/interface, không phải đường dẫn file, không phải tên package, và không hỗ trợ array types.

| `name` | Nhận xét |
|---|---|
| `"java.lang.String"` | Binary name hợp lệ cho lớp `String`; phải đi cùng module `java.base` |
| `"java.util.Map$Entry"` | Binary name của nested interface `Entry` |
| `"java.util.Map.Entry"` | Tên source/canonical dạng lồng, **không phải** binary name đúng của `Entry` |
| `"java/lang/String.class"` | Đường dẫn tài nguyên class, không phải binary name |
| `"[Ljava.lang.String;"` | Descriptor/array name; overload theo `Module` **không hỗ trợ array types** |
| `"int"` hoặc `"void"` | Không dùng API này để thu được primitive/void `Class` objects |

Để lấy `Class` của primitive/void, dùng `int.class` hoặc `void.class`. Để lấy class của array, dùng `String[].class` hoặc API theo loader phù hợp với array-name syntax. Không nên xem `Class.forName(module, "[Ljava.lang.String;")` là API thay thế hợp lệ cho array class.

### 3.4. Trường hợp module chưa được resolve

Một module JAR có mặt trên `--module-path` chưa chắc đã được **resolve** vào boot layer hoặc layer tùy chỉnh. Nếu bạn chưa có `Module` object tương ứng, vấn đề cần giải quyết trước tiên là **module resolution**, không phải gọi `Class.forName(module, name)`.

Ví dụ kiểm tra có chọn module hay không:

~~~java
Module module = ModuleLayer.boot()
        .findModule("shop.api")
        .orElse(null);
~~~

Nếu `module == null`, điều đó cho biết **không tìm thấy module theo tên trong phạm vi layer lookup này**. Nó chưa chứng minh JAR `shop.api` không tồn tại trên ổ đĩa. Cần kiểm tra module path, root modules, `requires` và `Configuration`.

**Quan trọng:** không được gọi `Class.forName(null, name)`. Overload theo module sẽ ném `NullPointerException` nếu `module` hoặc `name` là `null`.

## 4. Không liên kết và không khởi tạo: ý nghĩa chính xác

### 4.1. Phân biệt các giai đoạn

- **Loading:** tìm và định nghĩa biểu diễn runtime của một class/interface.
- **Linking:** verification, preparation và resolution theo quy định JVM.
- **Initialization:** chạy class initializer, bao gồm các biểu thức và khối `static` tương ứng.

Theo Java SE 17/21 `Class` Javadoc, overload:

~~~java
Class.forName(module, binaryName)
~~~

**cố gắng locate/load**, **không link** và **không chạy class initializer**. Đây là phát biểu về **hợp đồng phương thức**; không phải cam kết rằng class chưa bao giờ được JVM xử lý thêm vì các thao tác độc lập khác.

Đối chiếu các overload:

| API | Phạm vi yêu cầu | Khi không tìm thấy | Linking và initialization |
|---|---|---|---|
| `Class.forName(Module, String)` | Class **thuộc module chỉ định** | **Trả `null`** | Không tự link, không chạy initializer |
| `Class.forName(String)` | Theo defining loader của caller trong tình huống thông thường | **`ClassNotFoundException`** | Yêu cầu initialization nếu cần; kéo theo linking |
| `Class.forName(String, false, ClassLoader)` | Theo loader chỉ định | **`ClassNotFoundException`** | Không yêu cầu initialization; vẫn có thể load/link theo hợp đồng overload |
| `Class.forName(String, true, ClassLoader)` | Theo loader chỉ định | **`ClassNotFoundException`** | Yêu cầu initialization |
| `MyType.class` | Class literal của kiểu biết tại compile time | Không có đường “không tìm thấy” theo kiểu API nhận tên String | Không tự yêu cầu initialization của `MyType` |

**Không suy rộng:** “`Class.forName(Module,String)` không link” không có nghĩa mọi overload `Class.forName` đều không link. Cần đọc đúng phần tài liệu của overload đang dùng.

### 4.2. Vì sao không thấy `static` chạy?

~~~java
class InitMarker {
    static {
        System.out.println("INITIALIZED");
    }
}
~~~

Nếu một lời gọi `Class.forName(module, "some.package.InitMarker")` trả về `Class<?>`, riêng lời gọi đó **không** khiến dòng `INITIALIZED` được in. Nếu code sau đó **đọc một nonconstant static field**, **gọi static method**, hoặc **tạo instance** của lớp chưa khởi tạo, khối `static` có thể chạy tại thời điểm sau.

**Cảnh báo suy luận:** không thấy `INITIALIZED` không chứng minh lớp chưa load. Ngược lại, có `Class<?>` không đồng nghĩa initializer đã chạy. Ví dụ độc lập ở mục 6 được thiết kế để quan sát thứ tự này.

## 5. “Trả null” không có nghĩa mọi thất bại đều được biến thành null

Đây là điểm cần phân biệt để viết code production chính xác. `Class.forName(Module, String)` trả `null` **khi không tìm được lớp theo yêu cầu**. Tuy nhiên Javadoc còn quy định các ngoại lệ/lỗi riêng:

| Tình huống | Hành vi |
|---|---|
| Không tìm thấy class/interface đúng module | `null` |
| Class cùng tên được loader load nhưng thuộc module khác | `null` |
| Tham số `module == null` hoặc `name == null` | `NullPointerException` |
| Thất bại liên quan đến class loading/linkage theo hợp đồng API | Có thể ném `LinkageError` |
| Truy cập bị cơ chế kiểm tra bảo mật từ chối (khi áp dụng) | Có thể ném `SecurityException` |

Do đó, cách xử lý sau **sai** vì nhận định mọi thất bại đều là `null`:

~~~java
Class<?> result = Class.forName(module, name);
if (result == null) {
    System.out.println("Everything that can go wrong happened here");
}
~~~

Trong ứng dụng thật:

1. **Validate đầu vào** `module`, `name` theo hợp đồng ứng dụng.
2. **Xử lý `null`** như tình huống class không được tìm thấy trong module được chỉ định.
3. **Không nuốt `LinkageError`** hoặc các lỗi hệ thống một cách tùy tiện; ghi lại thông tin và xử lý theo policy riêng vì đó là loại lỗi khác.
4. Nếu có yêu cầu chính sách bảo mật, không gộp `SecurityException` vào “module không có class”.

Bảng trên nói về **hành vi được Javadoc định nghĩa**; một ứng dụng không nhất thiết gặp mọi trường hợp đó trong runtime thông thường.

## 6. Ví dụ Java 17+ chạy độc lập: tìm được, không tìm được, sai module, chưa initialize

Chúng ta tạo một **named module `lookup.demo`** có lớp `Target` với khối `static` in thông báo. Chương trình thử tìm `Target` trong đúng module, tìm một lớp không tồn tại, tìm một class thuộc module khác, và cuối cùng mới chủ động kích hoạt initialization của `Target`.

### 6.1. Cây thư mục

~~~text
project/
  src/
    lookup.demo/
      module-info.java
      lesson/
        lookup/
          Main.java
          Target.java
  mods/
    lookup.demo/           # thư mục output sau khi biên dịch
~~~

### 6.2. Tệp `src/lookup.demo/module-info.java`

~~~java
module lookup.demo {
    requires java.logging;
}
~~~

Module `lookup.demo` khai báo dependency `requires java.logging;` để trình biên dịch và runtime có thể tham chiếu tới `java.util.logging.Logger`. Module này **không exports** package `lesson.lookup`; thực hành vẫn có thể lấy `Class<?>` của một class trong module, vì overload `forName(Module,String)` không kiểm tra khả năng truy cập class của caller.

### 6.3. Tệp `src/lookup.demo/lesson/lookup/Target.java`

~~~java
package lesson.lookup;

final class Target {
    static {
        System.out.println("TARGET_INITIALIZED");
    }

    static int VALUE = 7;
}
~~~

`Target` là lớp **package-private**. Trường `VALUE` là `static` và không `final`, nên đọc lần đầu sẽ kích hoạt khởi tạo `Target` nếu chưa xảy ra. Khối `static` dùng để quan sát thời điểm khởi tạo.

### 6.4. Tệp `src/lookup.demo/lesson/lookup/Main.java`

~~~java
package lesson.lookup;

public class Main {
    public static void main(String[] args) {
        Module own = Main.class.getModule();
        Module logging = java.util.logging.Logger.class.getModule();

        System.out.println("module=" + own.getName());

        System.out.println("found="
                + (Class.forName(own, "lesson.lookup.Target") == Target.class));

        System.out.println("missing="
                + (Class.forName(own, "lesson.lookup.Missing") == null));

        System.out.println("wrongModule="
                + (Class.forName(own, "java.util.logging.Logger") == null));

        System.out.println("correctModule="
                + (Class.forName(logging, "java.util.logging.Logger")
                    == java.util.logging.Logger.class));

        System.out.println("arrayUnsupported="
                + (Class.forName(own, "[Llesson.lookup.Target;") == null));

        System.out.println("beforeInitializer");
        System.out.println("value=" + Target.VALUE);
    }
}
~~~

### 6.5. Biên dịch và chạy

Đứng tại thư mục `project`, sử dụng JDK 17 trở lên:

~~~sh
javac --release 17 -d mods/lookup.demo src/lookup.demo/module-info.java src/lookup.demo/lesson/lookup/Main.java src/lookup.demo/lesson/lookup/Target.java
java --module-path mods -m lookup.demo/lesson.lookup.Main
~~~

Các lệnh trên biên dịch thành module được unpack (exploded module) trong `mods/lookup.demo`, rồi khởi chạy class `lesson.lookup.Main` thuộc module `lookup.demo`. Không yêu cầu Gradle hoặc Maven.

**Stdout chính xác:**

~~~text
module=lookup.demo
found=true
missing=true
wrongModule=true
correctModule=true
arrayUnsupported=true
beforeInitializer
TARGET_INITIALIZED
value=7
~~~

### 6.6. Đọc kết quả theo thứ tự

| Dòng | Nguyên nhân | Kết luận |
|---|---|---|
| `module=lookup.demo` | `Main.class.getModule()` | Module hiện tại có tên `lookup.demo` |
| `found=true` | Tìm `Target` trong đúng module; so với `Target.class` | Có `Class<?>` đúng module nhưng `Target` chưa bị initializer chạy bởi thao tác này |
| `missing=true` | Không có `lesson.lookup.Missing` trong module | Kết quả là `null`, không phải `ClassNotFoundException` |
| `wrongModule=true` | `Logger` thuộc `java.logging`, không thuộc `lookup.demo` | Lớp **có thật** nhưng module yêu cầu sai, vẫn `null` |
| `correctModule=true` | Lấy module của `Logger` rồi tìm lại | Đúng module trả đúng `Class<?>` |
| `arrayUnsupported=true` | Truyền tên kiểu mảng `[Llesson.lookup.Target;` | Overload theo module không hỗ trợ array classes |
| `beforeInitializer` | Mốc quan sát | Chưa có dòng `TARGET_INITIALIZED` |
| `TARGET_INITIALIZED` | Đọc nonconstant `Target.VALUE` | Lúc này initialization của `Target` mới diễn ra |
| `value=7` | Giá trị của trường sau khởi tạo | Confirm giá trị truy cập hợp lệ |

**Đặc biệt:** lớp `Target` không `public`, nhưng phương thức vẫn trả `Class<?>` của nó. Điều này cho thấy việc **tìm thấy metadata** không tự quyết định khả năng truy cập member/class từ mọi caller. Reflection và truy cập qua ranh giới named module còn có quy tắc riêng.

### 6.7. Bài thử thay đổi

1. Đổi `"lesson.lookup.Target"` thành `"lesson.lookup.NoTarget"` ở phép tìm đầu: `Class.forName(own, ...)` trả `null`, nhưng phép so sánh với `Target.class` vẫn hợp lệ.
2. Đổi `own` ở phép tìm `Target` thành `logging`: nhận `null` dù tên `Target` và bytecode hợp lệ, do chỉ định sai module.
3. Bỏ dòng `System.out.println("value=" + Target.VALUE);`: nếu không có active use khác, dòng `TARGET_INITIALIZED` **không xuất hiện**.
4. Đổi overload sang `Class.forName("lesson.lookup.Target")` trước dòng `beforeInitializer`: overload một tham số sẽ yêu cầu initialization; dòng `TARGET_INITIALIZED` sẽ xuất hiện sớm hơn.
5. Chạy thử trong một **JVM mới** sau mỗi biến thể, bởi initializer chỉ chạy một lần cho mỗi định nghĩa lớp đã khởi tạo.

## 7. Đối chiếu với `Class.forName(String, boolean, ClassLoader)`

Nếu code chỉ có `ClassLoader` thay vì `Module`:

~~~java
ClassLoader loader = Main.class.getClassLoader();
Class<?> found = Class.forName(
        "lesson.lookup.Target", false, loader);
~~~

Đoạn trên là **mã minh họa** bên trong module `lookup.demo`, với `Main` thuộc module đó.

Khác biệt cần hiểu:

- **Overload theo loader** `Class.forName(name, false, loader)` tìm theo loader. Nếu không tìm được, nó ném **`ClassNotFoundException`**, cần catch hoặc khai báo `throws`.
- **Overload theo module** `Class.forName(module, name)` tìm class đúng module và trả **`null`** nếu không có class đó trong module. Nó không yêu cầu `ClassNotFoundException` trong signature.
- **Overload theo loader** có boolean `initialize` để chọn yêu cầu khởi tạo; **overload theo module không có boolean** và theo hợp đồng **không khởi tạo**.
- **Overload theo module** còn có giới hạn về array classes; overload theo loader hỗ trợ kiểu tên mảng được API tài liệu hóa.
- Loader định nghĩa nhiều named modules có thể trả một class được tìm bằng **overload theo loader** trong khi **overload theo module** trả `null` nếu class ở module khác.

### Mẫu code xử lý kết quả an toàn

Trong luồng cần tìm một lớp tùy chọn tại module đã được resolve:

~~~java
static Class<?> findOptionalClass(Module module, String binaryName) {
    java.util.Objects.requireNonNull(module, "module");
    java.util.Objects.requireNonNull(binaryName, "binaryName");
    return Class.forName(module, binaryName);
}
~~~

Đây là **method minh họa**, có thể đặt trong class tiện ích. Caller xử lý kết quả:

~~~java
Class<?> type = findOptionalClass(module, "com.example.Plugin");
if (type == null) {
    System.out.println("Module này không cung cấp lớp yêu cầu");
} else {
    System.out.println(type.getName());
}
~~~

Mẫu này không tự tạo instance và không tự đảm bảo quyền reflective access. Nếu cần khởi tạo/khởi chạy class, bước tiếp theo phải xét riêng: constructor, quyền truy cập JPMS, exception do initializer và tính hợp lệ của API dùng chung.

**Không coi `null` là bằng chứng “thiếu file”.** Trong công cụ debug, nên ghi thêm `module.getName()` hoặc định danh module phù hợp, `binaryName`, layer/loader liên quan và danh sách module đã resolve (nếu có).

## 8. Những lỗi và ngộ nhận thường gặp

### “`forName` không tìm thấy lớp thì luôn ném `ClassNotFoundException`.”

Sai. Chỉ các overload theo tên/loader có hợp đồng ném checked exception đó khi class không được tìm thấy. **Overload `(Module,String)` trả `null`** cho trường hợp không tìm thấy đúng module.

### “Có một lớp cùng tên đâu đó trong classpath/module path thì overload phải trả Class.”

Sai. Module được chỉ định là thành phần quan trọng của phép tìm. Class thuộc module khác có thể khiến kết quả trả `null` dù bytecode tồn tại.

### “Không ném `ClassNotFoundException` thì không có ngoại lệ nào khác.”

Sai. `NullPointerException` cho tham số null, `LinkageError` trong các trường hợp lỗi loading/linkage và `SecurityException` khi điều kiện bảo mật liên quan không được đáp ứng vẫn có thể xảy ra theo Javadoc.

### “Nhận được `Class<?>` là class đã chạy `static {}`.”

Sai. `Class.forName(Module,String)` không kích hoạt initializer. Để kiểm chứng, đặt khối `static` in log và dùng một thao tác active use sau đó.

### “API này tương tự `Class.forName(name, false, loader)` về mọi mặt.”

Sai. Khác **đơn vị phạm vi** (module so với loader), **kết quả khi thiếu lớp** (null so với checked exception), **liên kết**, và **hỗ trợ array types**.

### “Dùng `com.example.Outer.Inner` là đúng tên của nested class.”

Thông thường sai với lớp lồng thực sự. Binary name phải dùng `$`, ví dụ `com.example.Outer$Inner`. `.` phân cách các package và tên top-level class, không thay thế quy tắc binary name của nested class.

### “Nếu `Class.forName(module, name)` trả về Class thì caller chắc chắn được truy cập class/method.”

Sai. Javadoc nêu rõ overload này **không kiểm tra class accessibility đối với caller**. Quy tắc `exports`, `opens`, readability, visibility Java và kiểm tra reflection có thể ảnh hưởng cách sử dụng sau đó.

### “Chỉ định một `Module` có tên sẽ tự resolve mọi JAR ở module path.”

Sai. `Module` object gắn với module đã được biểu diễn trong cấu hình runtime tương ứng. Module resolution/layer xây dựng theo bước riêng; `Class.forName` không thay thế quá trình đó.

### “Trả `null` nghĩa là chắc chắn chưa từng load class nào.”

Sai. Loader của một module có thể load class thuộc module khác rồi overload vẫn trả `null` do lớp không thuộc module được yêu cầu. Cần phân biệt hiệu ứng tìm/nạp và điều kiện kết quả đúng module.

## 9. Quy trình chẩn đoán khi kết quả là `null`

Khi gặp `Class<?> candidate = Class.forName(module, name);` và `candidate == null`:

1. **Kiểm tra `name`:** có đúng binary name, đúng hoa/thường, đúng nested-class `$`? Có vô tình truyền array descriptor hoặc đường dẫn `.class`?
2. **Kiểm tra `module` thực tế:** in `module.getName()`, `module.isNamed()`; nếu module lấy từ `Class.getModule()` thì đảm bảo đó là lớp “mốc” đúng ngữ cảnh.
3. **Kiểm tra lớp thuộc module nào:** với class mà ứng dụng đã biết, quan sát `KnownType.class.getModule()`; với plugin/dynamic module thì kiểm tra descriptor, package và nguồn module.
4. **Kiểm tra resolution:** named module đã được resolve trong boot layer hay child `ModuleLayer` phù hợp chưa? File có trên module path chưa đủ.
5. **Kiểm tra loader mapping:** `module.getClassLoader()`, `ModuleLayer.findLoader(name)` nếu thích hợp; phân biệt module nào do loader định nghĩa.
6. **Nếu thành công nhưng không gọi được member:** kiểm tra visibility Java, readability, `exports`/`opens` và điều kiện reflection, thay vì thay đổi phép tìm đã thành công.
7. **Nếu thực sự có lỗi ném ra:** ghi nguyên exception/error/cause; đừng ép mọi thất bại thành nhánh `null`.

### So sánh triệu chứng

| Triệu chứng | Hướng kiểm tra đầu tiên |
|---|---|
| `Class.forName(module, name) == null` | Tên, đúng module, module resolution; không kết luận ngay thiếu JAR |
| `Class.forName(name)` ném `ClassNotFoundException` | Loader tìm lớp, binary name, classpath/module path |
| `NullPointerException` từ overload theo module | Có truyền `module` hoặc `name` bằng null không? |
| `LinkageError` | Class-file/definition/dependency và lỗi linkage; ghi cause thực tế |
| Tìm được `Class<?>` nhưng thao tác sau đó lỗi quyền truy cập | Quy tắc JPMS `requires`/`exports`/`opens` và access Java |
| Có `Class<?>` nhưng không thấy log `static` | Đây là bình thường: overload theo module không khởi tạo |
| Plugin tìm được lớp cùng tên nhưng sai kiểu/loader | Defining loader, module membership và runtime type identity |

## 10. Câu hỏi tự kiểm tra

1. `Class.forName(Module,String)` xuất hiện từ Java mấy? Hai tham số có ý nghĩa gì?
2. Nếu lớp không nằm trong module truyền vào, kết quả mặc định là gì?
3. `Class.forName(module, "foo.Bar") == null` có chứng minh JAR chứa `foo.Bar` không tồn tại không? Vì sao?
4. Phân biệt `ClassNotFoundException` và giá trị `null` ở hai overload `Class.forName`.
5. Vì sao `Class.forName(module, name)` trả về `Class<?>` chưa chứng minh class đã initialization?
6. Theo Javadoc, overload này có tự link class không? Overload `Class.forName(name, false, loader)` giống hệt về điều đó không?
7. Trong ví dụ `lookup.demo`, tại sao `wrongModule=true` nhưng `correctModule=true`?
8. Vì sao `Class.forName(own, "[Llesson.lookup.Target;")` không phải cách hỗ trợ để lấy array class?
9. Tại sao `Target` là package-private nhưng vẫn có thể nhận `Class<?>` từ phương thức theo module?
10. Nếu truyền `null` làm `Module` hoặc `name`, API trả null hay ném loại lỗi nào?
11. Khi nào `SecurityException` hoặc `LinkageError` có thể là hướng chẩn đoán riêng thay vì xử lý nhánh null?
12. Nếu lớp thuộc module khác do cùng loader định nghĩa, overload theo module có thể trả null không?

**Tiêu chí hoàn thành:** chạy đúng ví dụ Java 17+, giải thích đầy đủ tám dòng stdout, phân biệt trường hợp “thiếu class” với “class thuộc module khác”, và xử lý đúng hai nhóm kết quả **`Class<?>` hoặc `null`** cùng những lỗi riêng được tài liệu hóa.

## 11. Tài liệu chính thức

- [Java SE 21 — `Class.forName(Module, String)`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#forName(java.lang.Module,java.lang.String)): nguồn chính về module scope, `null`, linking, initialization, array types, exceptions.
- [Java SE 17 — `Class` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): hành vi overload đã tồn tại trong Java 17.
- [Java SE 21 — `Module`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Module.html): nhận diện named/unnamed module, loader và layer.
- [Java SE 21 — `ModuleLayer`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ModuleLayer.html): module resolution context, tìm module và mapping loader.
- [JVM Specification 21 — Chapter 5](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html): nền tảng loading/linking/initialization khi cần phân tích lỗi sâu.

Ví dụ sử dụng API phù hợp Java 17 trở lên và cố ý tách bước tìm lớp theo module khỏi bước chủ động initialization.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-api-009">Class.getClassLoader, getParent và đặt tên loader</a>

<details>
<summary>Xem chi tiết</summary>

# `Class.getClassLoader()`, `getParent()` và cách đặt tên class loader trong Java

Java cho phép nhiều bộ nạp lớp cùng tồn tại, ủy quyền cho nhau hoặc định nghĩa các kiểu riêng. Ba API thường được dùng để hiểu cấu trúc này:

- `Class.getClassLoader()`: xác định **loader đã định nghĩa kiểu** đang được đối tượng `Class<?>` đại diện.
- `ClassLoader.getParent()`: xác định **loader cha dùng để ủy quyền** của một instance `ClassLoader`.
- `ClassLoader.getName()`: lấy **tên chẩn đoán** của instance loader, nếu loader được đặt tên.

Ba API trả lời ba câu hỏi khác nhau. Đặc biệt, **loader cha không nhất thiết là loader định nghĩa một class**, và **tên loader không phải danh tính duy nhất của loader**.

Bài này thuộc chủ đề API nạp lớp; mục tiêu là đọc đúng runtime identity, quan hệ delegation, loader name và các trường hợp `null`. Các ví dụ áp dụng với Java 17+, đồng thời giải thích điểm khác biệt giữa Java platform, application/system loader và custom loader.

## 1. Mô hình ba lớp thông tin

```text
Class<?> type = Widget.class
       |
       | type.getClassLoader()
       v
Defining loader của Widget
       |
       | getParent()
       v
Parent loader dùng cho delegation
       |
       | getParent()
       v
... đến bootstrap loader (thường không có
     một object ClassLoader Java đại diện)

Mỗi ClassLoader instance:
    loader.getName() -> tên hoặc null
```

| API | Đối tượng gọi | Kiểu kết quả | Ý nghĩa |
|---|---|---|---|
| `Class.getClassLoader()` | `Widget.class`, `obj.getClass()` | `ClassLoader` hoặc `null` | Loader **đã định nghĩa** lớp/interface runtime |
| `ClassLoader.getParent()` | `someLoader` | `ClassLoader` hoặc `null` | Parent dùng cho delegation, không phải class loader của chính object `someLoader` |
| `ClassLoader.getName()` | `someLoader` | `String` hoặc `null` | Tên loader được đặt khi tạo, phục vụ nhận diện/chẩn đoán |
| `ClassLoader.getSystemClassLoader()` | ClassLoader API tĩnh | `ClassLoader` | System loader, thường là application loader |
| `ClassLoader.getPlatformClassLoader()` | ClassLoader API tĩnh | `ClassLoader` | Platform loader trong JVM hiện đại |

Một loader là **đối tượng Java**, và chính đối tượng ấy cũng có kiểu runtime. Do đó:

```java
ClassLoader loader = ClassLoader.getSystemClassLoader();
Class<?> loaderImplementationType = loader.getClass();

ClassLoader definingLoaderOfLoaderClass =
        loaderImplementationType.getClassLoader();
```

Dòng cuối hỏi **ai định nghĩa lớp triển khai loader**, khác với chính biến `loader` đang nắm giữ instance. Không được gộp hai ý nghĩa này.

## 2. `Class.getClassLoader()` trả về điều gì?

`java.lang.Class<T>` là biểu diễn một kiểu ở runtime. Với class hoặc interface thông thường, `getClassLoader()` cho biết **defining loader** của kiểu đó.

```java
Class<?> type = com.example.Widget.class;
ClassLoader definingLoader = type.getClassLoader();
```

Đây là đoạn minh họa, chỉ biên dịch độc lập khi `com.example.Widget` tồn tại và khả dụng. Nó **không** tìm tên lớp dưới dạng String và **không** tạo instance Widget. `Widget.class` cung cấp một `Class<?>` cho kiểu mà JVM đã nạp theo bối cảnh đang chạy.

### Defining loader khác initiating loader

Giả sử `PluginLoader` có parent là `AppLoader`:

```text
PluginLoader.loadClass("org.example.Api")
                    |
       parent-first delegation
                    v
AppLoader.loadClass("org.example.Api")
                    |
        AppLoader defines Api
                    |
                    v
          Class<org.example.Api>
```

Khi đó:

- Lời gọi bắt đầu ở `PluginLoader`.
- Loader thực sự **định nghĩa** `Api` là `AppLoader`.
- `apiClass.getClassLoader() == appLoader` là `true`.
- `apiClass.getClassLoader() == pluginLoader` là `false`.

Đó là hành vi bình thường của parent delegation. Không suy luận `getClassLoader()` từ biến mà ta đã gọi `loadClass(...)`. Nếu cần biết loader nào thực sự nhận yêu cầu, phải quan sát luồng gọi hoặc công cụ ghi log; `getClassLoader()` cho câu trả lời về **định nghĩa kiểu**.

### Vì sao defining loader quan trọng?

Trong JVM, danh tính của một class hoặc interface runtime phụ thuộc vào **tên nhị phân + defining loader**:

```text
Type identity = (binary name, defining class loader)
```

Hai loader khác nhau có thể tự định nghĩa cùng tên `org.example.Api`, tạo ra hai kiểu không tương thích dù in cùng `getName()`. Điều này giải thích những lỗi `ClassCastException` hoặc lỗi liên kết trong kiến trúc plugin/hot reload. Ngược lại, hai loader con cùng delegate về một defining loader ở cha sẽ cùng nhìn thấy một kiểu runtime.

Khi chẩn đoán:

```java
Class<?> actual = value.getClass(); // value khác null
System.out.println(actual.getName());
System.out.println(actual.getClassLoader());
```

Đây chỉ là mảnh minh họa; nếu `value == null` thì `value.getClass()` gây `NullPointerException`.

## 3. Vì sao `getClassLoader()` đôi khi trả `null`?

**`null` không có nghĩa lớp chưa được nạp.** Trong các JDK thông thường, bootstrap loader được JVM biểu diễn qua `null` thay vì một object `ClassLoader` Java.

```java
System.out.println(String.class.getClassLoader() == null);
System.out.println(Object.class.getClassLoader() == null);
```

Với Java 17+ chạy trên JDK thông thường, cả hai dòng in `true` vì `java.lang.String` và `java.lang.Object` là các lớp cốt lõi do bootstrap loader định nghĩa.

Tài liệu `Class.getClassLoader()` cũng nói rõ:

- Nếu `Class` biểu diễn **kiểu primitive** hoặc `void`, `getClassLoader()` trả `null`.
- Đối với một **array class**, Java runtime tự tạo array type khi cần. Loader của array class được báo cáo là loader của **kiểu phần tử cuối cùng** (*element type*); nếu phần tử là primitive, kết quả là `null`.
- `null` ở đây có thể biểu diễn bootstrap loader hoặc trường hợp kiểu primitive/`void` không có defining loader Java như class thông thường.

Ví dụ:

```java
String[].class.getClassLoader()        // thường null
int[][].class.getClassLoader()         // null
int.class.getClassLoader()             // null
void.class.getClassLoader()            // null
Widget[].class.getClassLoader()        // loader của Widget
Widget[][].class.getClassLoader()      // cùng loader của Widget
```

Đây là các biểu thức minh họa; hai dòng cuối yêu cầu có class `Widget` trong phạm vi truy cập. Đừng xem `Widget[].class` là một tệp `Widget[].class` được loader tự đọc từ đĩa; JVM tạo array class, còn loader trả về phản ánh element type.

### Phân biệt `Class.getClassLoader()` và `ClassLoader.getClass()`

Xét:

```java
Class<?> type = Widget.class;
ClassLoader a = type.getClassLoader();
ClassLoader b = type.getClass().getClassLoader();
```

- `a` là loader định nghĩa `Widget`.
- `type.getClass()` chính là `java.lang.Class.class`, bởi `type` bản thân nó là một object `Class`.
- `b` vì vậy hỏi loader định nghĩa **`java.lang.Class`**, thông thường là bootstrap loader (`null`).

Hai biểu thức này giải quyết hai câu hỏi khác nhau, và `a` có thể khác `b`.

## 4. `ClassLoader.getParent()`: mối quan hệ ủy quyền

`ClassLoader.getParent()` là instance method trả về **loader cha được sử dụng trong delegation**. Đối với `ClassLoader.loadClass(String, boolean)` mặc định, JVM/Java runtime thường tuân theo:

1. Kiểm tra một class đã được nạp theo thông tin loader hay chưa (`findLoadedClass`).
2. Thử ủy quyền cho parent; nếu parent là `null` thì dùng cơ chế bootstrap.
3. Nếu parent không tìm thấy, loader mới gọi `findClass` của mình.
4. Nếu `resolve=true` thì yêu cầu liên kết kiểu trả về.

Quy tắc trên mô tả **implementation mặc định** của `ClassLoader.loadClass`. Một subclass có thể tùy chỉnh `loadClass` hoặc các hook liên quan để hỗ trợ chiến lược khác. Không được suy luận rằng mọi loader của mọi framework đều phải parent-first tuyệt đối.

```text
NamedPluginLoader
     |
     | getParent()
     v
Application/System loader
     |
     v
Platform loader
     |
     v
Bootstrap (thường biểu diễn là null)
```

Sơ đồ trên là một kiến trúc phổ biến. Đừng xem nó như một định lý về mọi JVM hoặc mọi launcher: bộ nạp custom, classpath/module path và môi trường đóng gói có thể khác.

### `getParent() == null` nói gì?

`getParent()` có thể trả `null` khi parent là bootstrap loader trong cách biểu diễn của JVM. Nó **không** chứng minh current loader là bootstrap loader hoặc current loader không thể nạp lớp Java nền tảng.

Ví dụ `ClassLoader.getPlatformClassLoader().getParent()` thường là `null` trong kiến trúc JDK hiện đại, vì platform loader ủy quyền tới bootstrap. Bootstrap bản thân không phải một instance `ClassLoader` Java thông thường để gọi `getParent()`.

**Quan trọng:** Parent **không phải** superclass trong ngôn ngữ Java. Nếu `class NamedLoader extends ClassLoader`, parent của instance `NamedLoader` là tham số được truyền vào constructor, không phải `ClassLoader.class`. Một quan hệ là **instance delegation**, quan hệ còn lại là **class inheritance**.

### Parent không cho biết ai thực sự định nghĩa từng class

Khi gọi:

```java
ClassLoader loader = new MyLoader(ClassLoader.getSystemClassLoader());
Class<?> type = loader.loadClass("java.lang.String");
```

Đây là mảnh minh họa, cần định nghĩa `MyLoader` với constructor phù hợp. Với loader dùng delegation mặc định, `type.getClassLoader()` thường là `null` (bootstrap). `loader.getParent()` là system loader, không phải bootstrap. Cả hai kết quả đều đúng vì hỏi hai sự thật khác nhau.

## 5. Đặt tên class loader trong Java 9+

Từ Java 9, `ClassLoader` cung cấp protected constructor:

```java
protected ClassLoader(String name, ClassLoader parent)
```

Một subclass có thể gọi constructor này để tạo loader có tên:

```java
public final class NamedPluginLoader extends ClassLoader {
    public NamedPluginLoader(String name, ClassLoader parent) {
        super(name, parent);
    }
}
```

Đây là class hoàn chỉnh cần lưu thành `NamedPluginLoader.java` nếu biên dịch riêng, chưa phải một chương trình chạy có `main`. `name` có thể là `null` để tạo loader không đặt tên, nhưng **chuỗi rỗng** `""` không hợp lệ và gây `IllegalArgumentException`. Tên được truy xuất bằng `loader.getName()`.

Nếu muốn dùng default system loader làm parent:

```java
ClassLoader app = ClassLoader.getSystemClassLoader();
NamedPluginLoader plugin =
        new NamedPluginLoader("payments-plugin", app);
System.out.println(plugin.getName());          // payments-plugin
System.out.println(plugin.getParent() == app); // true
```

Code trên là mảnh minh họa có class `NamedPluginLoader` đã biên dịch.

### Tên loader dùng để làm gì?

Tên là metadata dành cho con người và công cụ chẩn đoán. Ví dụ `payments-plugin`, `script-runner`, `test-sandbox` có thể giúp log dễ đọc hơn, phân biệt loader trong dump hoặc quan sát vòng đời khi dự án có nhiều plugin.

Tuy nhiên:

- Tên **không bắt buộc duy nhất**; hai instance có thể cùng `getName()` nhưng vẫn là hai đối tượng loader khác nhau.
- Tên **không quyết định parent**; `getParent()` mới trả quan hệ delegation.
- Tên **không quyết định type identity**; JVM dùng chính **defining loader object**, không dùng chuỗi `getName()`.
- Đặt tên **không thay đổi** thuật toán `loadClass` hoặc khiến loader tìm thêm thư mục/JAR nào.
- `getName() == null` chỉ cho biết loader **không được đặt tên**; nó không chứng tỏ object là bootstrap loader.
- `getName()` không phải định danh bảo mật hoặc cơ chế cấp quyền; không nên dùng tên loader làm khóa phân quyền.

API `getName()` không phải `final` để tương thích ngược; nếu subclass override, theo tài liệu JDK nó **phải trả về đúng tên đã chỉ định khi loader được tạo**. Với chương trình thông thường, giữ implementation kế thừa đơn giản sẽ tránh sai lệch trong log.

### So sánh loader theo tên hay theo object?

```java
ClassLoader one = new NamedPluginLoader("plugin", parent);
ClassLoader two = new NamedPluginLoader("plugin", parent);

boolean sameName = one.getName().equals(two.getName()); // true
boolean sameObject = one == two;                        // false
```

Đây là mảnh minh họa, biến `parent` đã được cung cấp. Nếu xác định **hai class có defining loader giống nhau**, so sánh `classA.getClassLoader() == classB.getClassLoader()` (object identity), **không** so chuỗi tên loader.

Không cố ý override `equals()` để tạo cảm giác hai loader “bằng nhau”: JVM phân biệt defining loader bằng danh tính runtime thực của instance, không bằng một phép so sánh tên.

## 6. Ví dụ Java 17+ biên dịch và chạy được

Tạo một file **`LoaderApiDemo.java`** với mã nguồn dưới đây. Chương trình dùng các kiểm tra boolean để stdout không phụ thuộc tên class triển khai loader tích hợp trên từng JDK.

```java
public class LoaderApiDemo {
    static class Sample {
    }

    static final class NamedLoader extends ClassLoader {
        NamedLoader(String name, ClassLoader parent) {
            super(name, parent);
        }
    }

    public static void main(String[] args)
            throws ClassNotFoundException {
        ClassLoader app = LoaderApiDemo.class.getClassLoader();

        NamedLoader first = new NamedLoader("plugin-a", app);
        NamedLoader second = new NamedLoader("plugin-a", app);

        System.out.println("first name: " + first.getName());
        System.out.println("parent is app: " + (first.getParent() == app));
        System.out.println("same names: "
                + first.getName().equals(second.getName()));
        System.out.println("same loader instance: " + (first == second));

        Class<?> sample = first.loadClass(Sample.class.getName());

        System.out.println("delegated class identical: "
                + (sample == Sample.class));
        System.out.println("sample defined by app: "
                + (sample.getClassLoader() == app));
        System.out.println("sample defined by first: "
                + (sample.getClassLoader() == first));

        System.out.println("String bootstrap: "
                + (String.class.getClassLoader() == null));
        System.out.println("primitive null: "
                + (int.class.getClassLoader() == null));
        System.out.println("String array null: "
                + (String[].class.getClassLoader() == null));
        System.out.println("Sample array same defining loader: "
                + (Sample[].class.getClassLoader()
                   == Sample.class.getClassLoader()));

        System.out.println("Class mirror loader bootstrap: "
                + (Sample.class.getClass().getClassLoader() == null));
    }
}
```

Chạy từ thư mục chứa file với JDK 17+:

```bash
javac LoaderApiDemo.java
java LoaderApiDemo
```

**Stdout chuẩn khi chạy chương trình độc lập theo cách trên trên JDK thông thường:**

```text
first name: plugin-a
parent is app: true
same names: true
same loader instance: false
delegated class identical: true
sample defined by app: true
sample defined by first: false
String bootstrap: true
primitive null: true
String array null: true
Sample array same defining loader: true
Class mirror loader bootstrap: true
```

### Phân tích kết quả

1. `first.getName()` trả `plugin-a` vì constructor `NamedLoader(String, ClassLoader)` gọi `super(name, parent)`.
2. `first.getParent() == app` là `true` vì constructor sử dụng đúng parent đã truyền.
3. Hai loader cùng tên nhưng `first == second` là `false`. Chúng là **hai instance riêng biệt**.
4. `first.loadClass(Sample.class.getName())` sử dụng delegation mặc định. `Sample` thuộc mã ứng dụng hiện có nên parent `app` định nghĩa (hoặc đã định nghĩa) Sample và trả lại cho child.
5. Vì vậy `sample == Sample.class` là `true`, `sample.getClassLoader() == app` là `true`, còn `sample.getClassLoader() == first` là `false`.
6. `String.class` là class nền tảng của `java.base` được bootstrap loader phục vụ trong JDK thông thường; `int.class` biểu diễn primitive, nên cả hai cho `null` khi hỏi `getClassLoader()`.
7. `String[].class` có element type `String`, còn `Sample[].class` có element type `Sample`. Loader của array class được báo cáo theo element type.
8. `Sample.class.getClass()` là `java.lang.Class.class`. Lớp `java.lang.Class` do bootstrap định nghĩa nên biểu thức cuối là `true`.

Ví dụ **không nạp hai phiên bản riêng của `Sample`**. Nó minh họa parent delegation và so sánh bằng identity. Muốn child thực sự định nghĩa một bản riêng, phải có cơ chế `findClass`/`defineClass` hoặc loader thư viện được cấu hình bytecode riêng; điều này sẽ làm thay đổi đáp án identity.

### Một thử nghiệm bổ sung về loader không đặt tên

```java
NamedLoader unnamed = new NamedLoader(null, app);
System.out.println(unnamed.getName() == null);
```

Nếu thêm hai dòng này vào `main`, kết quả là `true`. Đây là biến thể **không thuộc stdout chuẩn bên trên**.

Ngược lại, `new NamedLoader("", app)` không phải cách tạo loader không tên: constructor cơ sở từ Java 9 sẽ ném `IllegalArgumentException` vì tên rỗng.

## 7. Các câu hỏi thường gặp khi thấy `null` hoặc loader lạ

### Vì sao `ClassLoader.getSystemClassLoader()` không bằng `SomeClass.class.getClassLoader()`?

System loader thường dùng để khởi động ứng dụng và là parent mặc định của nhiều custom loaders. Nhưng class có thể được định nghĩa bởi:

- bootstrap loader;
- platform loader;
- system/application loader;
- một loader riêng trong framework, plugin hoặc launcher.

Không thể lấy một system loader rồi cho rằng nó định nghĩa mọi class. Hãy kiểm tra trực tiếp `SomeClass.class.getClassLoader()`.

### Vì sao tên loader trùng nhau nhưng cast thất bại?

Vì hai instance có cùng tên vẫn là hai defining loader khác nhau. Nếu chúng tự định nghĩa cùng tên nhị phân của một interface/lớp, JVM coi hai định nghĩa là **hai kiểu khác nhau**. `ClassCastException` hoặc lỗi linkage có thể xuất hiện dù chuỗi class name và loader name trông giống nhau.

Log nên chứa cả thông tin class, class loader object/identity và module khi cần:

```java
Class<?> type = obj.getClass(); // obj != null
ClassLoader defining = type.getClassLoader();

System.out.println("class=" + type.getName());
System.out.println("loader=" + defining);
System.out.println("module=" + type.getModule());
```

Lưu ý: `toString()` của loader không phải một định danh duy nhất được bảo đảm qua các lần khởi động. Trong một tiến trình, hãy dùng identity/đối tượng `ClassLoader` thực tế để so sánh.

### `getParent()` có thể dùng để duyệt lên tới bootstrap không?

Có thể duyệt những object loader Java trong chuỗi parent:

```java
ClassLoader current = SomeClass.class.getClassLoader();

while (current != null) {
    System.out.println(current.getClass().getName()
            + " name=" + current.getName());
    current = current.getParent();
}
```

Đây là **mảnh minh họa**; cần `SomeClass` tồn tại. Vòng lặp kết thúc ở `null`, thường đại diện ranh giới bootstrap. Nó **không in một instance bootstrap loader Java**; bootstrap không nhất thiết là một object giống các subclass thông thường.

Nếu `SomeClass` do bootstrap định nghĩa, vòng lặp không chạy lần nào. Không dùng đoạn này để khẳng định có thể truy ngược tất cả loader tham gia bất kỳ thao tác tải lớp nào, nhất là khi implementation hỗ trợ ủy quyền không thuần chuỗi parent.

### `getParent()` có cho biết superclass của loader không?

Không. Phân biệt:

```java
Class<?> loaderClass = first.getClass();
Class<?> superclass = loaderClass.getSuperclass();
ClassLoader delegationParent = first.getParent();
```

- `superclass` là quan hệ **class inheritance**, ví dụ `ClassLoader.class`.
- `delegationParent` là **instance loader** nhận ủy quyền tìm class.
- `loaderClass.getClassLoader()` là loader **định nghĩa lớp triển khai NamedLoader**, một thông tin khác nữa.

Có thể trùng tên hoặc gần giống trong log nhưng không được coi các đại lượng này là một.

## 8. Tên loader và logging/diagnostics

Đặt tên có ý nghĩa giúp theo dõi multi-loader systems:

```text
PluginLoader("billing-plugin")
    parent -> AppLoader
    defines -> billing implementation classes

PluginLoader("reporting-plugin")
    parent -> AppLoader
    defines -> reporting implementation classes

AppLoader
    defines -> common public API
```

Giả định plugin API dùng chung được parent định nghĩa, hai plugin có thể giao tiếp qua API ấy mà không vô tình tạo hai bản của interface. Tên loader là thông tin **hỗ trợ theo dõi**; ranh giới định nghĩa thực phải kiểm tra bằng `Class.getClassLoader()`.

Khi phân tích `ClassCastException` trong plugin:

1. Thu thập tên binary của kiểu nguồn và kiểu đích.
2. Thu thập `getClassLoader()` của **từng** `Class` liên quan.
3. So loader object bằng `==`; xem cả parent chain bằng `getParent()` nếu cần.
4. Dùng `getName()` để log dễ hiểu hơn, không dùng nó làm tiêu chí cuối cùng cho type identity.
5. Kiểm tra delegation policy, JAR trùng, API chung bị plugin định nghĩa lại, hot reload hoặc việc giữ reference tới loader cũ.

Nếu đang làm việc với named modules (JPMS), thêm `Class.getModule()` vì module readability/exports cũng tác động tới quyền truy cập. Loader identity và module access giải quyết những vấn đề liên quan nhưng khác nhau.

### Quan sát tải lớp qua JVM

Ở HotSpot/JDK 17+:

```bash
java -Xlog:class+load=info LoaderApiDemo
```

Lệnh bật Unified Logging cho hoạt động nạp lớp. Dòng log và thời điểm hiển thị phụ thuộc implementation; không lấy nội dung log ấy làm stdout chuẩn ở phần demo.

Có thể sử dụng `jcmd` hoặc công cụ profiler để xem lớp/loader đã được JVM ghi nhận, tùy khả năng hỗ trợ của JDK/runtime và quyền truy cập tiến trình. Khi debug một môi trường thực, so sánh kết quả API trong tiến trình với log runtime, không suy đoán chỉ từ classpath.

## 9. Các bẫy thường gặp

**Bẫy 1 — `type.getClassLoader() == null` nghĩa là class chưa load.** Sai. Lớp bootstrap và các kiểu primitive/void có thể trả `null` theo API.

**Bẫy 2 — Một lớp có loader bằng loader đã gọi `loadClass`.** Sai khi delegation cho parent.

**Bẫy 3 — `loader.getParent()` là loader định nghĩa `loader.getClass()`.** Sai. Đó là parent delegation của **instance**; loader định nghĩa implementation class được lấy bằng `loader.getClass().getClassLoader()`.

**Bẫy 4 — `getParent() == null` nghĩa là loader không có cách nào nạp core Java classes.** Sai. Khi parent là `null`, implementation mặc định có thể dùng bootstrap lookup.

**Bẫy 5 — `getName()` chỉ có từ Java 17.** Sai. API đặt tên loader và `getName()` có từ **Java 9**, vì thế Java 17+ dùng được.

**Bẫy 6 — Đặt tên loader giống nhau tức cùng loader.** Sai. Danh tính runtime được xác định bởi đối tượng defining loader, không bởi label.

**Bẫy 7 — Một loader không tên là bootstrap loader.** Sai. `new NamedLoader(null, parent)` tạo một **instance loader thông thường** không tên.

**Bẫy 8 — `new ClassLoader(...)` tạo loader biết đọc thêm mọi JAR.** Sai. Một subclass mặc định chưa bổ sung cách tìm bytecode. Muốn tải từ nguồn mới cần triển khai `findClass`, dùng `URLClassLoader` đúng cấu hình, hoặc API tương ứng.

**Bẫy 9 — `Class.getClassLoader()` chính là `Class.class.getClassLoader()`.** Sai: một cái hỏi loader của **kiểu được đại diện**, cái kia hỏi loader của **java.lang.Class**.

**Bẫy 10 — Cứ dùng `loader.toString()` là có định danh toàn cục ổn định.** Sai. Log biểu diễn giúp chẩn đoán nhưng không phải hợp đồng ID xuyên tiến trình.

**Bẫy 11 — Parent chain luôn giải thích mọi đường nạp trong Java.** Sai. Custom loaders/framework có thể override delegation; tài liệu `loadClass` mô tả mặc định nhưng không cấm mọi chính sách khác.

**Bẫy 12 — Tên loader được quyết định từ tên class của loader.** Sai. `NamedLoader.class.getName()` và `instance.getName()` là hai chuỗi có chức năng khác nhau; tên instance do constructor loader nhận.

## 10. Tự kiểm tra

**Câu 1.** Loader A gọi `loadClass("p.Service")`, parent B định nghĩa `p.Service`. `Service.class.getClassLoader()` là A hay B?

**Đáp án:** B, vì API cho **defining loader** của kiểu. A có thể là initiating loader.

**Câu 2.** Hai loader đặt tên `"plugin"` và `"plugin"` có được coi là một loader không?

**Đáp án:** Không. Hai instance là hai đối tượng riêng trừ khi thực sự cùng tham chiếu một instance; so bằng `==` để kiểm tra identity.

**Câu 3.** `int.class.getClassLoader()` trả gì và vì sao?

**Đáp án:** `null`. Primitive type không được định nghĩa bởi một class loader Java theo cơ chế thông thường.

**Câu 4.** `Widget[][].class.getClassLoader()` khác `Widget.class.getClassLoader()` không?

**Đáp án:** Không, loader được báo cáo theo **element type Widget**. Cần phân biệt array class do JVM tự tạo và component/element class runtime.

**Câu 5.** `getParent()` trên instance loader tùy biến trả `null` có nghĩa instance ấy là bootstrap loader không?

**Đáp án:** Không. Một loader tùy biến vẫn có thể có parent `null` để hướng tới bootstrap delegation. Điều đó không biến nó thành bootstrap.

**Câu 6.** `ClassLoader(String name, ClassLoader parent)` nhận `name=""` có hợp lệ không?

**Đáp án:** Không, nó ném `IllegalArgumentException`. Dùng tên không rỗng hoặc `null` khi cố ý tạo loader không tên.

**Câu 7.** Khi debug `ClassCastException` do duplicate API giữa plugin loaders, `getName()` có đủ chứng minh hai kiểu cùng định nghĩa không?

**Đáp án:** Không. Cần so trực tiếp `Class<?>` và defining loader object, đồng thời kiểm tra binary name, delegation và module nếu phù hợp.

## 11. Quy tắc ghi nhớ

```text
Làm rõ kiểu runtime T:
  T.getClassLoader()       -> Ai định nghĩa T?

Làm rõ loader instance L:
  L.getParent()            -> L ủy quyền cho ai?
  L.getName()              -> L được đặt tên gì?

Kiểm tra hai kiểu có cùng defining loader:
  A.getClassLoader() == B.getClassLoader()

Kiểm tra một kiểu có thực sự là cùng runtime type:
  A == B (đối tượng Class<?>)

Không suy ra loader identity chỉ từ getName().
Không suy ra defining loader chỉ từ getParent().
Không coi null ở getClassLoader() là lớp chưa nạp.
```

Khi viết hoặc debug classloader, trình tự hữu ích là **kiểm tra `Class` của kiểu đích → lấy defining loader → xem parent chain → dùng tên loader làm nhãn trong log**. Trình tự này tránh nhầm lẫn giữa loại loader, instance loader và quá trình delegation.

### Tài liệu chuẩn

- [Java SE 17 — ClassLoader API: getName, getParent, constructors](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html)
- [Java SE 17 — Class.getClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getClassLoader())
- [JVMS 5.3 — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-api-010">Phân biệt nạp lớp, tạo đối tượng và gọi constructor bằng reflection</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt nạp lớp, tạo đối tượng và gọi constructor bằng reflection

Trong Java, những câu như “nạp class”, “khởi tạo class”, “tạo object” và “gọi constructor” diễn tả **những hành động khác nhau**. Chúng thường xuất hiện gần nhau khi chương trình dùng reflection, nên rất dễ hiểu nhầm rằng `Class.forName("...")` đã tạo một instance, hoặc `getDeclaredConstructor()` đã chạy constructor. Thực tế, một class có thể đã được JVM nạp và liên kết mà chưa khởi tạo; một đối tượng `Class<?>` có thể tồn tại trước bất kỳ instance nào; và việc **tra cứu constructor** khác với **thực thi constructor**.

Bài học tập trung vào **API nạp lớp và reflection** trong Java 17+: `ClassLoader.loadClass`, các overload `Class.forName`, `Class.getConstructor`/`getDeclaredConstructor`, `Constructor.newInstance`, cách dùng `new` thông thường và việc phân biệt các lỗi xảy ra ở mỗi bước. Các ví dụ cố tình in ra dấu mốc để thấy rõ **điều gì đã xảy ra** và **điều gì chưa xảy ra**.

## 1. Năm khái niệm cần tách biệt

| Hoạt động | Ý nghĩa | Ví dụ | Có tự tạo instance của lớp nghiệp vụ? |
| --- | --- | --- | --- |
| **Loading** (nạp lớp) | JVM xác định một class/interface runtime từ tên và loader, có thể cần đọc class-file bytes | `loader.loadClass("demo.Widget")` | **Không** |
| **Linking** (liên kết) | Verification, preparation và resolution theo quy tắc JVM | JVM thực hiện khi cần để có thể dùng class | **Không** |
| **Initialization** (khởi tạo *lớp*) | Thực thi static initializers/`<clinit>` khi có active use | `Class.forName(name, true, loader)` | **Không** |
| **Instantiation** (tạo *đối tượng*) | Có một object mới của lớp, cấp phát và chạy trình tự khởi tạo instance thích hợp | `new Widget()` hoặc `constructor.newInstance()` | **Có** |
| **Constructor lookup / invocation** | Tìm metadata constructor khác với gọi constructor để tạo instance | `getDeclaredConstructor()` / `newInstance()` | **Chỉ invocation tạo instance** |

**Lưu ý thuật ngữ:** trong tiếng Việt, cả *class initialization* và *object initialization* thường được gọi là “khởi tạo”, nhưng chúng không giống nhau:

- **Class initialization:** chạy các phép gán `static` và khối `static { ... }` theo quy tắc JVM, thường một lần cho mỗi định nghĩa class trong vòng đời loader. Chưa nhất thiết có object `Widget`.
- **Object initialization:** khi có instance mới, JVM thực hiện quá trình khởi tạo đối tượng, gồm constructor superclass, instance field initializers, instance initializer blocks và constructor body theo thứ tự được Java quy định.
- **Đối tượng `Class<?>`:** là đối tượng Java đại diện cho runtime type. Nó **không phải** instance của `Widget` mà ứng dụng dùng làm dữ liệu nghiệp vụ.

### Đọc luồng tổng quan

```text
Widget.java
    │ javac
    ▼
Widget.class
    │ ClassLoader.loadClass / Class.forName(..., false, ...)
    ▼
Class<?> của Widget  ── có thể đã loading/linking
    │
    ├─ getName(), getDeclaredConstructor()  → metadata, chưa gọi constructor
    │
    ├─ Class.forName(..., true, ...)         → yêu cầu class initialization
    │
    └─ Constructor.newInstance(args)
            │
            ├─ đảm bảo class initialization khi cần
            ├─ cấp phát instance mới
            ├─ thực hiện object initialization
            └─ trả về object mới hoặc exception
```

Sơ đồ là mô hình khái niệm, không phải cam kết JVM sẽ thực hiện resolution vào đúng một dòng source. JVM có thể nạp/liên kết sớm và phân giải tham chiếu theo nhu cầu, nhưng **side effect của class initialization** phải tuân thủ thời điểm được Java cho phép.

## 2. `ClassLoader.loadClass` làm được và không làm được gì?

Ví dụ:

```java
ClassLoader loader = MyApplication.class.getClassLoader();
Class<?> type = loader.loadClass("demo.Widget");
```

Đây là **snippet**, muốn biên dịch phải có `MyApplication` và `demo.Widget`. Ý nghĩa chính:

1. `loadClass` nhận **binary name** của class, không nhận đường dẫn `demo/Widget.class`.
2. Class loader tra cứu một định nghĩa đã biết hoặc tìm/ủy quyền nạp class qua parent và cơ chế `findClass`.
3. Kết quả trả về là `Class<?>`, một **đối tượng mô tả kiểu**. Phép gọi **không có lời gọi constructor `Widget()`**.
4. `ClassLoader.loadClass(String)` thông thường **không yêu cầu class initialization**. Những hành động liên quan tới linking có thể diễn ra theo lựa chọn của JVM.
5. Loader được gọi có thể **khác defining loader**. Nếu ủy quyền về parent, class được parent định nghĩa vẫn được trả về như bình thường.

Ví dụ `loader.loadClass("java.lang.String")` thường nhận về đúng `String.class` qua bootstrap, mặc dù loader nhận yêu cầu là application loader.

**Không thể** dựa trên kết quả `loadClass` để nói “đã chạy static block” hoặc “đã có một object nghiệp vụ”. Cách kiểm tra lớp đã initialized hay chưa trong ví dụ học tập là đặt side effect đơn giản trong static initializer và quan sát thứ tự, nhưng **đừng dùng side effect tốn kém để kiểm tra production**.

### Phân biệt lỗi tra cứu

`loadClass("demo.Missing")` có thể ném **checked exception** `ClassNotFoundException` nếu không tìm thấy class. Nếu tìm thấy bytes nhưng định dạng sai, phụ thuộc thiếu hoặc liên kết thất bại, lỗi có thể thuộc họ `LinkageError`. Việc loader không tìm thấy class khác với constructor của class ném exception.

## 3. `Class.forName`: overload quyết định có khởi tạo lớp ngay hay không

Hai dạng quan trọng:

```java
Class<?> a = Class.forName("demo.Widget");
Class<?> b = Class.forName("demo.Widget", false, loader);
Class<?> c = Class.forName("demo.Widget", true, loader);
```

Đoạn này là **snippet**, giả sử `loader` đã được khai báo. Theo API:

- `Class.forName(String className)` tìm, nạp/liên kết và **yêu cầu initialization** của class được đặt tên. Loader được lựa chọn theo ngữ cảnh của lớp gọi.
- `Class.forName(String name, boolean initialize, ClassLoader loader)` cho phép chỉ định **loader** và cờ `initialize`. Với `false`, method tìm/nạp/liên kết class nhưng **không yêu cầu initialization**; với `true`, yêu cầu initialization.
- `Class.forName(Module module, String name)` là overload khác trên Java 9+, phục vụ tìm class trong module và **không yêu cầu initialization**; kết quả có thể là `null` nếu không tìm được class đúng module. Nó **không giống** các overload ném `ClassNotFoundException`.

Điều cần nhớ: **`Class.forName(name)` không gọi constructor** của class được tìm. Nó có thể kích hoạt **static initializer** nhưng không tạo `new Widget()`.

### Class literal: `Widget.class`

```java
Class<Widget> type = Widget.class;
```

Đây là snippet cần định nghĩa `Widget`. Class literal cung cấp đối tượng `Class<Widget>` và **không tự yêu cầu initialize `Widget`**. JVM có thể tải hoặc liên kết class để xử lý biểu thức; không nên đồng nhất các bước nội bộ này với việc chạy khối `static`.

### Một chú ý về tên lớp lồng nhau

Trong ví dụ bên dưới, một `static nested class` tên `Target` trong `LifecycleDemo` có **binary name** là:

```text
demo.LifecycleDemo$Target
```

Không truyền tên dạng `demo.LifecycleDemo.Target` cho `Class.forName`/`loadClass`, bởi đó không phải binary name của member class. `$` là cách javac thường tạo tên binary cho nested classes; `Target.class.getName()` giúp lấy tên chuẩn thay vì tự nối chuỗi.

## 4. Thực hành A — loading, metadata, class initialization và instance initialization

Chạy chương trình độc lập với **Java 17+**.

Cây tệp:

```text
lifecycle-lab/
└── src/
    └── demo/
        └── LifecycleDemo.java
```

Tệp **`src/demo/LifecycleDemo.java`**:

```java
package demo;

import java.lang.reflect.Constructor;

public final class LifecycleDemo {
    public static class Target {
        static {
            System.out.println("Target <clinit>");
        }

        public Target() {
            System.out.println("Target <init>");
        }

        public void work() {
            System.out.println("Target.work()");
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("1. main started");

        ClassLoader loader = LifecycleDemo.class.getClassLoader();
        Class<?> fromLoader =
                loader.loadClass("demo.LifecycleDemo$Target");
        System.out.println("2. loaded: " + fromLoader.getSimpleName());

        Class<?> noInit = Class.forName(
                "demo.LifecycleDemo$Target", false, loader);
        System.out.println("3. same Class: " + (fromLoader == noInit));

        Constructor<?> ctor = noInit.getDeclaredConstructor();
        System.out.println("4. constructor found");

        Object first = ctor.newInstance();
        System.out.println("5. first instanceof Target: " +
                (first instanceof Target));

        ((Target) first).work();

        Object second = ctor.newInstance();
        System.out.println("6. different instances: " +
                (first != second));
    }
}
```

Từ thư mục `lifecycle-lab`, chạy:

```sh
javac --release 17 -d out src/demo/LifecycleDemo.java
java -cp out demo.LifecycleDemo
```

**Stdout chính xác trong một tiến trình mới:**

```text
1. main started
2. loaded: Target
3. same Class: true
4. constructor found
Target <clinit>
Target <init>
5. first instanceof Target: true
Target.work()
Target <init>
6. different instances: true
```

Phân tích theo từng mốc:

1. `main` thuộc `LifecycleDemo` đã được JVM chuẩn bị/khởi tạo trước khi bắt đầu chạy. Điều này **không tự buộc** static initializer của `LifecycleDemo.Target` phải chạy.
2. `loadClass` trả `Class<?>` của nested class `Target`; dòng thứ hai chỉ in tên kiểu. Không có object `Target` được tạo.
3. `Class.forName(name, false, loader)` trả đúng cùng `Class<?>`; không yêu cầu khởi tạo lớp.
4. `getDeclaredConstructor()` **tra cứu metadata constructor không tham số**; không cấp phát `Target` và không gọi body constructor. Việc lấy metadata có thể kéo theo một số bước resolution nhưng không phải active use làm `Target` phải initialized.
5. `ctor.newInstance()` yêu cầu có một instance mới. JVM thực hiện class initialization nếu chưa hoàn tất, nên `Target <clinit>` xuất hiện **trước** `Target <init>`.
6. `first instanceof Target` là kiểm tra type của object đã tạo. Nó không thực hiện constructor mới; ở đây kết quả là `true` vì class trong chương trình là cùng định nghĩa runtime.
7. `((Target) first).work()` gọi instance method và in thông báo riêng; không phải constructor call.
8. `ctor.newInstance()` lần hai tạo **một object khác**, nên constructor lại in `Target <init>`, nhưng static initializer **không chạy lại**.

Dấu `<clinit>` và `<init>` ở output được dùng như **chuỗi minh họa** do code tự in. Trên bytecode JVM, `<clinit>` là class initialization method đặc biệt và `<init>` là instance initialization method tên đặc biệt, song câu `System.out.println(...)` chỉ đang giúp quan sát thứ tự.

Nếu class initializer thất bại, lời gọi reflective construction có thể thất bại **trước khi constructor body bắt đầu**. Đọc nguyên nhân trên stacktrace để phân biệt lỗi initialization của class và lỗi trong constructor.

## 5. Thực hành B — cờ `initialize` của `Class.forName` và thời điểm `static` chạy

Một chương trình độc lập thứ hai giúp so sánh hai tiến trình JVM khác nhau. Tạo tệp **`src/demo/ForNameDemo.java`** trong thư mục riêng hoặc cùng `src/demo` của bài thực hành, sau đó biên dịch.

```java
package demo;

public final class ForNameDemo {
    public static class Ready {
        static {
            System.out.println("Ready static initialization");
        }

        public Ready() {
            System.out.println("Ready constructor");
        }
    }

    public static void main(String[] args) throws Exception {
        boolean eager = Boolean.parseBoolean(args[0]);

        System.out.println("initialize flag: " + eager);
        Class<?> type = Class.forName(
                "demo.ForNameDemo$Ready",
                eager,
                ForNameDemo.class.getClassLoader());

        System.out.println("Class returned");
        type.getDeclaredConstructor().newInstance();
        System.out.println("Object created");
    }
}
```

Biên dịch:

```sh
javac --release 17 -d out src/demo/ForNameDemo.java
```

**Lượt thứ nhất**, dùng `initialize=false`:

```sh
java -cp out demo.ForNameDemo false
```

**Stdout:**

```text
initialize flag: false
Class returned
Ready static initialization
Ready constructor
Object created
```

**Lượt thứ hai**, dùng `initialize=true`:

```sh
java -cp out demo.ForNameDemo true
```

**Stdout:**

```text
initialize flag: true
Ready static initialization
Class returned
Ready constructor
Object created
```

Kết luận quan sát: với `false`, class đã được tra cứu trước nhưng initializer chỉ chạy khi `Constructor.newInstance()` tạo object. Với `true`, `Class.forName` yêu cầu initialization trước lúc trả `Class<?>`. Cả hai trường hợp đều cần **một lần gọi constructor khi tạo object**. Mỗi lệnh `java` ở đây tạo tiến trình JVM riêng, tránh việc lần chạy trước đã initialized class làm sai bài đo.

**Không diễn giải sai:** cờ `initialize=true` không đồng nghĩa “khởi tạo một instance”. Nó chỉ nói về **class initialization**; chỉ câu `newInstance()` ở sau mới tạo object cụ thể.

## 6. Reflection constructor: tìm đúng constructor khác với thực thi nó

Lớp `java.lang.reflect.Constructor<T>` đại diện cho **khai báo constructor** của một class. Các API thường dùng:

| API | Tìm gì? | Có chạy constructor? |
| --- | --- | --- |
| `type.getConstructor(Class<?>... parameterTypes)` | **Public constructor** theo danh sách parameter types | Không |
| `type.getConstructors()` | Mọi public constructor | Không |
| `type.getDeclaredConstructor(Class<?>... parameterTypes)` | Constructor khai báo trực tiếp trong class, **kể cả non-public** | Không |
| `type.getDeclaredConstructors()` | Tất cả constructor khai báo trong class ấy | Không |
| `constructor.getParameterTypes()` | Thông tin kiểu tham số | Không |
| `constructor.canAccess(target)` | Kiểm tra khả năng truy cập hiện tại; target cho constructor là `null` | Không |
| `constructor.trySetAccessible()` | Thử bỏ các kiểm tra truy cập khi module rules cho phép | Không |
| **`constructor.newInstance(args...)`** | **Tạo instance và gọi constructor được chọn** | **Có** |

Với constructor, **không có kế thừa constructor** như method. `getDeclaredConstructor` chỉ tìm constructors được khai báo của chính class, không lấy constructors của superclass rồi cho rằng chúng có thể được gọi như constructor của subclass.

Hai điểm tinh tế:

- **`getDeclaredConstructor()` không tham số** tìm đúng **constructor có 0 tham số**. Nếu chỉ có `Widget(String)`, nó ném `NoSuchMethodException`; JVM **không tự tạo no-arg constructor** khi class đã khai báo constructor khác.
- `getDeclaredConstructor(String.class, int.class)` đòi **chữ ký parameter types chính xác**. Không truyền `Integer.class` thay `int.class` lúc **tra cứu**. Việc chuyển đổi đối số khi **invocation** có quy tắc riêng.

### Constructor lookup không phải method lookup

`getMethod("run")` tìm method công khai, có thể bao gồm method kế thừa. `getDeclaredConstructor()` tìm constructor với signature chỉ định. Đừng cố dùng `getMethod("<init>")`; `<init>` là tên đặc biệt trong class file JVM, không phải method Java bình thường có thể được gọi qua `getMethod`.

## 7. Thực hành C — constructor overload, argument matching và phản chiếu kiểu đối số

Tệp **`src/demo/ConstructorChoice.java`**:

```java
package demo;

import java.lang.reflect.Constructor;

public final class ConstructorChoice {
    public static class Job {
        private final String id;
        private final int attempts;

        public Job() {
            this("default", 1);
        }

        public Job(String id, int attempts) {
            this.id = id;
            this.attempts = attempts;
            System.out.println("constructor: " + id + "/" + attempts);
        }

        public String summary() {
            return id + ":" + attempts;
        }
    }

    public static void main(String[] args) throws Exception {
        Class<Job> type = Job.class;

        Constructor<Job> noArgs = type.getConstructor();
        Constructor<Job> withArgs =
                type.getConstructor(String.class, int.class);

        System.out.println("constructors found");
        Job first = noArgs.newInstance();
        Job second = withArgs.newInstance("batch", 3);

        System.out.println("first: " + first.summary());
        System.out.println("second: " + second.summary());
        System.out.println("distinct: " + (first != second));
    }
}
```

Biên dịch và chạy từ thư mục chứa `src`:

```sh
javac --release 17 -d out src/demo/ConstructorChoice.java
java -cp out demo.ConstructorChoice
```

**Stdout chính xác:**

```text
constructors found
constructor: default/1
constructor: batch/3
first: default:1
second: batch:3
distinct: true
```

Tại sao `"constructor: default/1"` chỉ in **một lần**, dù constructor mặc định gọi `this("default", 1)`? Vì thực tế nó ủy quyền tới body của overload hai tham số, nơi có đúng một lệnh in; khi dùng `this(...)`, constructor được gọi cùng thuộc một lần khởi tạo instance.

Bài học về reflection:

- `getConstructor` chỉ tìm **public constructors**. Ở đây cả hai constructor `public`.
- `getConstructor(String.class, int.class)` trả metadata constructor có parameter types **String, int**, không tự cấp phát object.
- `withArgs.newInstance("batch", 3)` tạo một `Job` và gọi constructor đã chọn. Khi truyền argument, reflection hỗ trợ boxing/unboxing và widening primitive conversions theo quy tắc; không nên dựa vào việc tìm overload tự động như compiler Java source.
- `Class<Job>` và `Constructor<Job>` giữ thông tin generic thuận tiện, nên biến trả về có kiểu `Job` mà không cần cast thủ công.
- `new Job()` trực tiếp và `noArgs.newInstance()` đều tạo object nhưng có khác nhau về thời điểm xác định constructor, kiểm tra access và cách ném exception.

### Tra cứu constructor với primitive và wrapper

`int.class` biểu diễn primitive type `int`. `Integer.class` là **reference type** khác. Với constructor `Job(String, int)`:

```java
type.getConstructor(String.class, int.class);      // tìm được
type.getConstructor(String.class, Integer.class);  // NoSuchMethodException
```

Đây là snippet để đặt vào method có `throws NoSuchMethodException` và biến `type` trong bài trên. **Signature lookup là so exact types**; khi gọi `newInstance("batch", Integer.valueOf(3))` thì reflection có thể unbox đối số thành `int` vì constructor đã được chọn.

Nếu class có nhiều overloaded constructors cùng nhận số, người viết code phải **chọn chính xác** overload qua `getConstructor(parameterTypes...)`. `newInstance` không chạy thuật toán overload resolution ở compile time giống cú pháp `new Job(...)` trong Java source.

## 8. `new` thông thường và `Constructor.newInstance` khác nhau ở đâu?

Hai cách có thể cùng gọi một constructor:

```java
Job direct = new Job();
Job reflective = Job.class.getConstructor().newInstance();
```

Đoạn này giả sử `Job` là public accessible type với public no-arg constructor, và method bao có xử lý `ReflectiveOperationException`.

| Tiêu chí | `new Job()` | `getConstructor().newInstance()` |
| --- | --- | --- |
| Chọn class/constructor | Compiler biết Java type và chọn overload từ source | Chương trình có thể chọn type/signature theo runtime metadata |
| Kiểm tra kiểu và access | Chủ yếu tại compile time, JVM còn runtime checks | Có thêm runtime lookup/access checks |
| Tạo instance | Có | Có |
| Yêu cầu class initialization | Có nếu chưa initialized | Có nếu chưa initialized |
| Constructor body | Chạy | Chạy |
| Type result | `Job` | `Job` với `Constructor<Job>`; `Object` nếu dùng `Constructor<?>` |
| Constructor ném checked exception | Java source phải tuân thủ `throws`/`catch` | Constructor-thrown exception được bọc trong `InvocationTargetException` |
| Sai signature | Compiler báo trước (thường) | `NoSuchMethodException` tại runtime |
| Sai arguments khi invocation | Compiler giúp phát hiện | `IllegalArgumentException` có thể xuất hiện khi chạy |

Reflection hữu ích khi class/constructor chỉ xác định ở runtime: DI container, serialization frameworks, plugin discovery, annotation processors chạy runtime, test frameworks. Tuy nhiên nếu type và constructor đã biết chắc lúc compile, cú pháp `new` hoặc factory thường rõ ràng, ít lỗi runtime và dễ bảo trì hơn.

### Reflection không tự giải quyết mọi vấn đề nạp lớp

Nếu chỉ có tên class nhưng không biết loader phù hợp, `Class.forName(...)` có thể nạp một bản class ở loader khác với bản ứng dụng muốn dùng. Sau khi có `Class<?>`, constructor được lấy từ **chính runtime type ấy**. Hai class cùng tên nhưng do hai defining loaders khác nhau định nghĩa sẽ có những `Constructor<?>` gắn với **hai runtime classes khác nhau**. Không thể tự cast object từ type của plugin loader thành type của host chỉ vì tên class giống.

## 9. Khởi tạo instance: thứ tự các bước dễ bị nhầm với `static`

Một lần gọi constructor không chỉ là thực hiện những dòng nằm trong thân constructor cuối cùng. Với đối tượng class thông thường, chương trình tuân theo trình tự khởi tạo instance:

1. JVM cần class hợp lệ, đã được initialized theo quy tắc (nếu chưa).
2. Cấp phát một object mới và đặt **giá trị mặc định** cho instance fields trước khi các initializer cụ thể chạy.
3. Thực hiện chuỗi constructor của superclass (`super(...)`) theo Java language rules.
4. Thực hiện các instance field initializers và instance initializer blocks của lớp tương ứng theo thứ tự nguồn.
5. Thực hiện phần thân constructor phù hợp, sau những lời gọi `this(...)`/`super(...)` bắt buộc trong quá trình khởi tạo.

Ví dụ độc lập, tệp **`src/demo/OrderDemo.java`**:

```java
package demo;

class Parent {
    static {
        System.out.println("Parent static");
    }

    {
        System.out.println("Parent instance block");
    }

    Parent() {
        System.out.println("Parent constructor");
    }
}

class Child extends Parent {
    static {
        System.out.println("Child static");
    }

    {
        System.out.println("Child instance block");
    }

    Child() {
        System.out.println("Child constructor");
    }
}

public final class OrderDemo {
    public static void main(String[] args) {
        System.out.println("before new");
        new Child();
        System.out.println("after first");
        new Child();
    }
}
```

Chạy:

```sh
javac --release 17 -d out src/demo/OrderDemo.java
java -cp out demo.OrderDemo
```

**Stdout chính xác:**

```text
before new
Parent static
Child static
Parent instance block
Parent constructor
Child instance block
Child constructor
after first
Parent instance block
Parent constructor
Child instance block
Child constructor
```

Khi `Child` phải được initialized, JVM khởi tạo superclass `Parent` trước khi khởi tạo chính `Child`; vì vậy hai khối static xuất hiện trước object constructor. Mỗi lần `new Child()` tiếp theo vẫn chạy **instance blocks và constructor chain** nhưng **không chạy lại static initializers**. Nếu thay hai biểu thức `new Child()` bằng một `Constructor<Child>.newInstance()` thích hợp, nguyên tắc thứ tự static và instance vẫn được bảo toàn.

Trình tự đầy đủ có thêm các quy tắc riêng cho interface khởi tạo liên quan và constructor chaining; ví dụ cố ý dùng các class thông thường để minh họa trục chính. Không dùng `ClassLoader.loadClass` như cơ chế "chạy constructor Parent trước" — đó là việc khác.

## 10. Một class có thể initialized mà không hề có instance

Đọc static field hoặc gọi static method thuộc một class có thể yêu cầu **class initialization**, dù không tạo object nào.

Ví dụ snippet minh họa:

```java
class Settings {
    static {
        System.out.println("Settings initialized");
    }

    static int getPort() {
        return 8080;
    }
}
```

Nếu một chương trình khác gọi `Settings.getPort()`, JVM có thể phải thực hiện static initializer nhưng không gọi `new Settings()`, không gọi constructor `Settings()`, và không cần bất kỳ Settings instance nào.

Ngược lại, một **constant variable** như `public static final int PORT = 8080` có thể được compiler inline vào caller. Đọc nó từ class khác không nhất thiết yêu cầu initialize class khai báo. Đó là lý do muốn kiểm tra chính xác thời điểm static initializer, không nên chỉ dựa vào việc đọc một compile-time constant.

Các thao tác như `SomeClass.class`, `someClass.getName()` và tra cứu constructor metadata thường **không phải** active use. Nhưng static method invocation, đọc static field không phải constant variable, tạo object và một số hoạt động reflection có thể là active use. Đặc biệt, `Class.forName(name, true, loader)` yêu cầu initialization ngay cả khi không gọi constructor.

## 11. Sai lầm phổ biến với `Class.newInstance()`

API cũ:

```java
Object instance = type.newInstance();
```

`Class.newInstance()` đã **deprecated từ Java 9**. Mã Java 17+ nên dùng:

```java
Object instance = type.getDeclaredConstructor().newInstance();
```

Cả hai là **snippets**; biến `type` có kiểu `Class<?>` và method phải xử lý exceptions phù hợp.

Lý do kỹ thuật đáng nhớ: `Class.newInstance()` có cơ chế exception handling kém trực quan, vì nó có thể **truyền ra checked exception do constructor ném** mà signature không thể hiện đầy đủ. Cách `getDeclaredConstructor().newInstance()` trả `InvocationTargetException` giúp caller phân biệt **lỗi gọi reflective API** với **exception phát sinh bên trong constructor**.

Ngoài ra, lựa chọn constructor qua `getDeclaredConstructor(Class<?>...)` giúp chỉ định chính xác overload; không tự hạn chế vào public zero-argument constructor mà `Class.newInstance()` vốn cố gắng dùng.

Không dùng `Class.newInstance()` trong ví dụ mới chỉ vì ngắn hơn một chút. Đặc biệt với code framework, việc truy xuất `InvocationTargetException.getCause()` quan trọng để giữ nguồn gốc lỗi.

## 12. Thực hành D — lỗi bên trong constructor và `InvocationTargetException`

Tạo tệp **`src/demo/ConstructorErrorDemo.java`**:

```java
package demo;

import java.lang.reflect.InvocationTargetException;

public final class ConstructorErrorDemo {
    public static class Broken {
        public Broken() {
            System.out.println("constructor entered");
            throw new IllegalStateException("bad config");
        }
    }

    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName(
                "demo.ConstructorErrorDemo$Broken",
                false,
                ConstructorErrorDemo.class.getClassLoader());

        System.out.println("Class found");
        try {
            type.getDeclaredConstructor().newInstance();
        } catch (InvocationTargetException e) {
            Throwable original = e.getCause();
            System.out.println("reflection wrapper: " +
                    e.getClass().getSimpleName());
            System.out.println("constructor cause: " +
                    original.getClass().getSimpleName());
            System.out.println("message: " + original.getMessage());
        }
    }
}
```

Chạy với JDK 17+:

```sh
javac --release 17 -d out src/demo/ConstructorErrorDemo.java
java -cp out demo.ConstructorErrorDemo
```

**Stdout chính xác:**

```text
Class found
constructor entered
reflection wrapper: InvocationTargetException
constructor cause: IllegalStateException
message: bad config
```

Điểm kiểm chứng:

- `Class.forName(..., false, ...)` tìm class mà chưa yêu cầu initialization; không có output từ constructor trước khi gọi `newInstance`.
- Constructor **đã bắt đầu thực thi**, vì đã in `constructor entered`, sau đó chính constructor ném `IllegalStateException`.
- `Constructor.newInstance` bọc exception do constructor ném trong `InvocationTargetException`; `getCause()` trả về exception gốc.
- Nếu class static initializer ném exception **trước khi constructor body chạy**, JVM có thể báo `ExceptionInInitializerError` hoặc lỗi initialization liên quan. Đây là **trường hợp khác**, không nên mặc định mọi lỗi construction đều là `InvocationTargetException`.

Trong ứng dụng thật, logging phải giữ cả stacktrace/cause, không chỉ gọi `getSimpleName()`. Ví dụ trên in tên exception để kiểm tra thứ tự và nội dung một cách xác định.

## 13. Ma trận lỗi theo từng thao tác

| Hành động | Lỗi có thể gặp | Ý nghĩa |
| --- | --- | --- |
| `loader.loadClass(name)` | `ClassNotFoundException` | Loader không tìm được class yêu cầu; không nói constructor có lỗi. |
| `Class.forName(name, ..., loader)` | `ClassNotFoundException`, `LinkageError`, lỗi initialization nếu `true` | Tên/loader không hợp lệ, liên kết không thành công hoặc static initialization có vấn đề. |
| `type.getConstructor(types...)` | `NoSuchMethodException` | Không có **public constructor** đúng parameter list. |
| `type.getDeclaredConstructor(types...)` | `NoSuchMethodException` | Class không khai báo constructor có signature ấy, kể cả sau khi xét non-public. |
| `ctor.newInstance(args...)` | `InstantiationException` | Declaring class là abstract hoặc không thể instantiated theo hợp đồng API. |
| `ctor.newInstance(args...)` | `IllegalAccessException` | Caller không có quyền truy cập constructor dưới access/module rules. |
| `ctor.newInstance(args...)` | `IllegalArgumentException` | Số lượng/kiểu arguments không hợp lệ hoặc chuyển đổi không được hỗ trợ. |
| `ctor.newInstance(args...)` | `InvocationTargetException` | Code **bên trong constructor** ném exception; đọc `getCause()`. |
| Reflection mở quyền private | `InaccessibleObjectException` hoặc `trySetAccessible() == false` | Module/package không mở đúng hoặc quyền reflection không được phép. |
| Class initialization khi construction | `ExceptionInInitializerError`/`NoClassDefFoundError` | Static initialization thất bại lần đầu hoặc class đã mang trạng thái initialization lỗi. |
| Cast object vừa tạo | `ClassCastException` | Runtime object không assignable với type đích, có thể do class loader identity. |

Lỗi liệt kê theo **ngữ cảnh chính**, không có nghĩa mỗi API chỉ có đúng loại lỗi đó. Một số lỗi như `LinkageError`, `SecurityException` hoặc `ExceptionInInitializerError` là unchecked và có thể phát sinh trong nhiều giai đoạn khác nhau. Nếu log chỉ in “Cannot instantiate”, cần truy lại **giai đoạn nào**: không tìm được class, không tìm được constructor, không thể truy cập, class initialized lỗi, hay body constructor đã ném exception.

### `InstantiationException` và abstract classes

Reflection không cho tạo instance trực tiếp của một **abstract class**, dù abstract class có constructor hợp lệ (constructor đó được subclass gọi khi khởi tạo subclass). Interface không có constructor Java để tra cứu qua `getDeclaredConstructor()` theo cách tạo object thông thường. Enum constructors không dùng để tạo tùy ý enum constants bằng reflection; `Constructor.newInstance` sẽ từ chối enum instantiation.

Một **record** có constructor (thường là canonical constructor) và vẫn có thể được tạo qua reflection nếu constructor/access/arguments hợp lệ; đừng áp đặt giả định “mọi class có no-arg constructor”. Một record khai báo components thường cần truyền đúng tham số canonical constructor.

## 14. Phân biệt access modifier với khả năng dùng reflection

Trong code bình thường, Java compiler kiểm tra `public`, `protected`, package-private và `private`. Reflection có thể tìm **private constructor** qua `getDeclaredConstructor` nhưng **tìm thấy không đồng nghĩa được phép gọi**.

Ví dụ minh họa:

```java
Constructor<?> ctor = type.getDeclaredConstructor();
boolean changed = ctor.trySetAccessible();
if (changed) {
    Object value = ctor.newInstance();
}
```

Đây là **snippet**, cần biến `type` và xử lý exceptions. `trySetAccessible()` cố bỏ Java language access checks trong giới hạn module rules; nếu không được, trả `false` (hoặc có thể phát sinh lỗi bảo mật tùy môi trường). `setAccessible(true)` có thể ném `InaccessibleObjectException` khi deep reflection vào package mà named module không mở.

**Không nên** mặc định mọi JDK-internal private constructor có thể được gọi bằng `setAccessible(true)`. Từ Java 9+, Java Platform Module System phân biệt **exports** (public API access) và **opens** (deep reflection), và từ các bản JDK mới mạnh hơn trong việc đóng gói nội bộ JDK. Khi thiết kế API cho framework/DI, nên công bố public constructor/factory hoặc khai báo `opens ... to ...` đúng nhu cầu thay vì phụ thuộc các mẹo phá đóng gói.

Nếu code caller và target là **nestmates** trong cùng nest Java, các quyền truy cập private có quy tắc riêng. Nhưng ví dụ học tập ở trên dùng public nested classes/constructors để tránh phụ thuộc thao tác mở quyền reflection.

### `getDeclaredConstructor` không tự động có quyền cao hơn `getConstructor`

- `getDeclaredConstructor` thấy constructors khai báo, **kể cả private**.
- `getConstructor` chỉ thấy public constructors.
- **Chính lệnh `newInstance`** còn kiểm tra quyền truy cập của caller và module. Vì vậy nhìn thấy metadata private không đủ để suy luận sẽ gọi được.

## 15. Reflection và classloader identity: tạo được object vẫn có thể cast sai

Khi class được nạp qua loader riêng, `type.getDeclaredConstructor().newInstance()` tạo object có **runtime class `type` đó**. Nó không biến object thành class cùng tên mà host đã biết.

Hình dung:

```text
HostLoader defines api.Task
PluginLoader defines api.Task

Class<?> hostType    → (api.Task, HostLoader)
Class<?> pluginType  → (api.Task, PluginLoader)

pluginType.getDeclaredConstructor().newInstance()
          → instance của pluginType
          → KHÔNG tự cast được về hostType
```

Hãy kiểm tra:

```java
Class<?> actualType = instance.getClass();
System.out.println(actualType.getName());
System.out.println(actualType.getClassLoader());
System.out.println(expectedType.isInstance(instance));
```

Snippet cần khai báo `instance` và `expectedType`. Nếu `isInstance` trả `false`, reflection constructor có thể hoàn toàn thành công, nhưng hai types ở ranh giới loaders vẫn không tương thích.

**Cách thiết kế đúng trong plugin system:** interface/service API cần được host và plugin resolve về **cùng `Class<?>`**, thường qua parent/shared-API loader, rồi implementation do plugin loader định nghĩa riêng. Khi ấy host có thể ép instance implementation thành interface chung mà không cần tự tải implementation trên application classpath.

### Nếu loader chưa biết class nhưng có bytes thì sao?

Custom loader có thể đọc bytes trong `findClass` và dùng `defineClass` để tạo runtime class. Đó là **phần loading**, không phải gọi constructor của class được nạp. Sau khi có `Class<?>`, chương trình mới tra cứu constructor và tạo instance. Không biến `findClass` hoặc `defineClass` thành một hàm “khởi tạo plugin object” mà không phân biệt lifecycle.

## 16. Khi nào nên dùng `getDeclaredConstructor().newInstance()`?

Các tình huống hợp lý:

1. **Plugin registry:** tên binary class được cấu hình hoặc đọc từ metadata, không biết concrete implementation lúc biên dịch.
2. **Dependency injection container:** framework xác định constructor theo annotations/metadata và cung cấp arguments tương ứng.
3. **Serialization/deserialization tools:** có cơ chế khởi tạo object phù hợp, dù không phải mọi serializer đều gọi Java constructor thông thường.
4. **Test framework:** tạo test class và gọi lifecycle hooks dựa trên runtime metadata.
5. **Factory động:** người dùng chọn implementation qua configuration nhưng hệ thống muốn kiểm soát type/constructor access.

Một chuỗi thực hiện an toàn và dễ debug:

```text
Tên class / metadata cấu hình
        ↓ validate tên và chọn đúng ClassLoader
Class<?> type = Class.forName(name, false, loader)
        ↓ validate subtype/interface mong muốn
expectedInterface.isAssignableFrom(type)
        ↓ chọn constructor theo parameter types
type.getDeclaredConstructor(...)
        ↓ kiểm tra quyền truy cập phù hợp
constructor.newInstance(args...)
        ↓ cast về interface chung / bắt lỗi có phân loại
host sử dụng object qua contract
```

**Chú ý:** check `isAssignableFrom` giúp tránh cast sai, nhưng **không tự biến việc chạy plugin không tin cậy thành an toàn**. Constructor có thể mở tệp, khởi chạy thread, truy cập network hoặc có side effects. Class loader chỉ giải quyết visibility/definition; sandboxing cần những biện pháp bảo mật độc lập.

### Khi nào `new` hoặc factory thường tốt hơn?

Nếu ứng dụng biết sẵn `new Service(config)` ở compile time, direct construction thường minh bạch, đảm bảo compiler kiểm tra signature, ít exception reflection và thuận tiện refactor. Reflection nên giải quyết **nhu cầu runtime discovery thật**; không cần nó chỉ để tránh viết một lệnh `new`.

Nếu API có nhiều constructor overload hoặc yêu cầu cấu hình phức tạp, một **public static factory** hoặc plugin-provided factory trong shared API có thể giúp giữ hợp đồng ổn định hơn so với cho host tìm constructor concrete bằng tên. Factory cũng là phương án cho class có private constructor mà framework không cần mở quyền truy cập sâu.

## 17. Kiểm soát side effects và vòng đời khi tạo plugin object

Việc `Class.forName(..., false, loader)` chỉ tra cứu class chưa có nghĩa toàn bộ quá trình “vô hại”: loading, verification/linking hoặc chạy agent/loader code có thể có chi phí và side effects theo implementation. Tuy nhiên static initialization của target class được JVM kiểm soát theo quy tắc, và constructor body chưa chạy cho đến khi instance được tạo.

Một constructor không nên làm quá nhiều việc khó kiểm soát, đặc biệt trong plugin runtime:

- Không tự đăng ký global listener hoặc tạo background thread nếu không có cơ chế huỷ đăng ký/dừng rõ ràng.
- Cân nhắc đưa việc kết nối network/file, nạp cấu hình hoặc mở resource sang `start()`/factory lifecycle có thể báo lỗi rõ và cleanup an toàn.
- Nếu constructor throw sau khi side effect một phần, caller có thể nhận `InvocationTargetException` mà không có object hợp lệ để gọi `close()`; vì vậy cần thiết kế rollback/tài nguyên cẩn thận.
- Với plugin reload, object và `Class<?>` có thể giữ class loader sống. `URLClassLoader.close()` giải phóng tài nguyên mở của loader nhưng không đảm bảo unload class/object ngay; GC còn phụ thuộc reachability.

Phân biệt ba thao tác trong log vận hành: **load type**, **instantiate service**, **start service**. Dù cả ba có thể nằm liên tiếp, chúng là ba trạng thái khác nhau và nên có thông tin lỗi riêng.

## 18. Một quy trình chẩn đoán theo triệu chứng

**Nếu nói “không load được class”:** kiểm tra binary name, classpath/module path, parent loader, class file tồn tại và runtime JDK có hỗ trợ bytecode version không. Bắt `ClassNotFoundException` ở đoạn loader/`forName` khi thích hợp.

**Nếu “load được class nhưng không tạo được object”:** xác minh class có phải interface/abstract/enum không; constructor nào tồn tại; có đúng parameter types và access không. Ghi log `getDeclaredConstructors()` trong công cụ debug, nhưng tránh đưa metadata của các class không tin cậy ra log public.

**Nếu “constructor đã chạy nhưng trả lỗi”:** bắt `InvocationTargetException`, xem `getCause()`; phân biệt exception do body constructor với lỗi initialization xảy ra trước body.

**Nếu “tạo được object nhưng cast thất bại”:** kiểm tra `type.getClassLoader()`, `expectedInterface.getClassLoader()`, `isAssignableFrom` và shared API packaging. Đừng xem hai class cùng binary name là đủ.

**Nếu “static block chạy quá sớm”:** xem nơi gọi `Class.forName(name)` (mặc định initialize), việc tạo instance, gọi static method hoặc đọc static field không phải compile-time constant. Có thể thay bằng `Class.forName(name, false, loader)` khi chỉ cần metadata và API semantics cho phép.

**Nếu “static block không chạy khi gọi `loadClass`”:** hành vi đó đúng theo mục đích `loadClass(String)` thông thường; cần active use hoặc `Class.forName(..., true, ...)` khi thật sự muốn initialize target.

**Nếu “chạy lại mà static block không in nữa”:** class initialization thường chỉ diễn ra một lần đối với một runtime class. Muốn quan sát từ đầu, chạy một JVM mới; việc `Constructor.newInstance` nhiều lần không tự reset static state.

### Công cụ bổ trợ

```sh
javap -classpath out -c -p demo.LifecycleDemo
javap -classpath out -v 'demo.LifecycleDemo$Target'
java -Xlog:class+load=info -cp out demo.LifecycleDemo
```

`javap` cho biết class file chứa constructor `<init>`, class initializer `<clinit>` và bytecode như `new`/`invokespecial`. Với nested class name có `$`, ở shell Unix hãy quote như ví dụ để tránh biến môi trường được thay thế; PowerShell cũng hiểu single quotes như literal. Unified class-load log trên HotSpot có output phụ thuộc runtime và **chỉ cung cấp tín hiệu loading**, không tự chứng minh constructor đã chạy; để biết constructor đã chạy thì quan sát output/stacktrace của chính ứng dụng.

## 19. Những câu khẳng định sai thường gặp

- **"`loadClass` chính là `new`."** Sai: nó trả về `Class<?>`, không tạo business instance.
- **"`Class.forName(name)` tự gọi constructor mặc định."** Sai: overload này yêu cầu **class initialization**, không tạo instance.
- **"`getDeclaredConstructor` chạy constructor."** Sai: nó chỉ trả về **constructor metadata**.
- **"Dùng `getDeclaredConstructor` thì constructor private gọi được luôn."** Sai: access và JPMS vẫn được kiểm tra lúc invocation.
- **"`getDeclaredConstructor(Integer.class)` sẽ tìm constructor nhận `int`."** Sai: lookup parameter types chính xác; chuyển đổi argument là bước khác.
- **"`Constructor.newInstance` chạy static block mỗi lần."** Sai: class initialization không lặp lại với mỗi object; constructor instance có thể chạy nhiều lần.
- **"Constructor throw sẽ luôn lộ trực tiếp `IllegalStateException`."** Sai: `Constructor.newInstance` thường bọc exception body trong `InvocationTargetException`; class initialization errors là nhóm khác.
- **"`Class.newInstance` là lựa chọn mới nhất."** Sai: đã deprecated; nên dùng `getDeclaredConstructor().newInstance()`.
- **"ClassLoader định nghĩa class thì tự có instance."** Sai: type metadata và object instances là hai khái niệm khác nhau.
- **"Reflection loại bỏ vấn đề identity giữa nhiều class loaders."** Sai: object vừa tạo mang đúng runtime type của class mà reflection dùng.
- **"Class đã load chắc chắn có constructor public không tham số."** Sai: có thể không có, hoặc là abstract/interface/enum/record với các ràng buộc riêng.
- **"Có thể chắc chắn thời điểm resolution chỉ bằng output của `javap`."** Sai: `javap` đọc file metadata; JVM được phép thực hiện resolution theo chiến lược runtime.

## 20. Tự kiểm tra: trả lời trước khi mở đáp án

**1. `ClassLoader.loadClass("demo.Target")` trả về loại đối tượng gì?** Một `Class<?>` đại diện cho target runtime class; nó không tự tạo target instance hoặc bắt buộc chạy target constructor.

**2. `Class.forName("demo.Target")` khác overload `Class.forName("demo.Target", false, loader)` ở đâu?** Dạng một yêu cầu class initialization; dạng có `false` tìm/nạp/liên kết mà không yêu cầu initialization, đồng thời chọn loader tường minh.

**3. Khi `getDeclaredConstructor()` thành công, có biết constructor body đã chạy không?** Không. Đây mới là tra cứu metadata.

**4. Tại sao `Constructor.newInstance()` in static block chỉ lần đầu, còn constructor in mọi lần?** Class initialization thông thường thực hiện một lần cho class đó, trong khi mỗi instance mới phải trải qua object initialization.

**5. Tại sao `getConstructor(int.class)` khác `getConstructor(Integer.class)`?** Primitive `int` và wrapper `Integer` là hai types khác nhau; constructor lookup yêu cầu parameter types chính xác.

**6. `Class.newInstance()` có còn được khuyến nghị cho Java 17+?** Không. API đã deprecated; dùng `getDeclaredConstructor().newInstance()` với exception handling rõ ràng.

**7. Nếu constructor ném `IllegalArgumentException` từ bên trong body, cần tìm nó ở đâu?** Khi gọi qua `Constructor.newInstance`, exception body thường là `cause` của `InvocationTargetException`. Nếu `newInstance` trực tiếp ném `IllegalArgumentException` do arguments sai, đó là vấn đề reflection invocation trước khi body constructor chạy.

**8. Class có constructor private được tìm bằng API nào?** `getDeclaredConstructor(...)`; việc có gọi được hay không phụ thuộc access rules, module openness và caller.

**9. Class có thể initialized nhưng chưa có instance nào không?** Có. Gọi một static method hoặc `Class.forName(..., true, ...)` có thể initialize class mà không tạo object.

**10. Vì sao reflection tạo đúng object tên `demo.Plugin` nhưng host cast sai?** Vì có thể object là instance của runtime class cùng binary name do một defining loader khác định nghĩa; cần dùng shared API identity.

## 21. Tài liệu chuẩn

- [Java SE 17 `ClassLoader` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, parent delegation và defining/initiating loaders.
- [Java SE 17 `Class` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): các overload `forName`, `getConstructor`, `getDeclaredConstructor`, class literal và `newInstance()` deprecated.
- [Java SE 17 `Constructor` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Constructor.html): `Constructor.newInstance`, argument/access checks và `InvocationTargetException`.
- [Java SE 17 `InvocationTargetException` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationTargetException.html): cách truy xuất target exception.
- [The Java Language Specification, Java SE 17, 12](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html): loading, linking, initialization; tạo class instance và trình tự instance initialization.
- [The Java Virtual Machine Specification, Java SE 17, 5](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): loading, linking, class initialization và ranh giới thực thi.

**Nguyên tắc nhớ:** **`loadClass` tìm một runtime *type*; `Class.forName` còn có lựa chọn yêu cầu *class initialization*; `getDeclaredConstructor` tìm *constructor metadata*; `Constructor.newInstance` mới tạo một *instance* và thực thi constructor.** Khi debug, xác định từng bước cùng class loader và runtime type cụ thể để tránh đánh đồng bốn hành động này.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-loading-api-011">Điều khiển assertion theo lớp, package và giá trị mặc định</a>

<details>
<summary>Xem chi tiết</summary>

# Điều khiển assertion theo lớp, package và giá trị mặc định

## 1. Assertion trong Java được bật hoặc tắt ở đâu?

Java có câu lệnh **assert** dùng để kiểm tra giả định nội bộ của chương trình:

~~~java
assert balance >= 0;
assert result != null : "Unexpected null result";
~~~

Khi assertion **được bật** cho class chứa câu lệnh, biểu thức điều kiện được đánh giá. Nếu điều kiện là false, JVM ném **AssertionError**; biểu thức thông báo sau dấu hai chấm, nếu có, được đánh giá khi cần tạo lỗi. Khi assertion **bị tắt**, toàn bộ câu lệnh assert không đánh giá những biểu thức này. Do đó, **không đặt side effect hoặc nghiệp vụ bắt buộc trong assert**.

Assertion thường bị tắt theo mặc định ở các class ứng dụng khi chạy lệnh `java` không cấu hình. Người chạy chương trình có thể bật/tắt chúng bằng tùy chọn **-ea/-da**, còn code điều khiển class loader có thể đặt chính sách bằng các API **ClassLoader.setDefaultAssertionStatus**, **setPackageAssertionStatus** và **setClassAssertionStatus**.

Bài học này giải thích ba cấp chính sách: **mặc định của loader, theo package và theo class**, quy tắc ưu tiên, thời điểm có hiệu lực, thao tác xóa chính sách, giới hạn với nested class và tương tác với tùy chọn JVM. Trọng tâm là class loader và cách chính sách ảnh hưởng những class **sẽ được khởi tạo**.

## 2. Bộ bốn API trên `java.lang.ClassLoader`

| API | Ý nghĩa | Phạm vi tác động |
| --- | --- | --- |
| **setDefaultAssertionStatus(boolean enabled)** | Đặt mặc định bật/tắt assertion cho loader. | Các class được loader quản lý và khởi tạo về sau, trừ trường hợp có override theo package/class. |
| **setPackageAssertionStatus(String packageName, boolean enabled)** | Đặt mặc định của package. | Package được nêu và mọi **subpackage** của nó, nếu không có chính sách cụ thể hơn. |
| **setClassAssertionStatus(String className, boolean enabled)** | Đặt chính sách theo một **top-level class**. | Top-level class được nêu cùng những nested class bên trong nó. |
| **clearAssertionStatus()** | Xóa các chỉ thị theo class/package và đặt mặc định của loader thành **false**. | Các class được khởi tạo về sau trong phạm vi loader. |

Cả bốn đều là phương thức **public** của ClassLoader, nên có thể gọi trên object loader khi có quyền truy cập tham chiếu đó:

~~~java
ClassLoader loader = MyApplication.class.getClassLoader();
loader.setDefaultAssertionStatus(true);
loader.setPackageAssertionStatus("com.example.service", false);
loader.setClassAssertionStatus("com.example.service.Checker", true);
~~~

Đoạn trên là code minh họa đặt vào một ứng dụng có class MyApplication và Checker; chưa phải một chương trình tự chạy độc lập. Phần sau có ví dụ hoàn chỉnh.

**Boolean có nghĩa trực tiếp:** true = bật assertion, false = tắt assertion. Không có tham số tên class dạng **`Class<?>`** trong những phương thức này: class được chỉ định bằng **chuỗi tên**. Đừng lẫn giá trị này với **Class.desiredAssertionStatus()**, vốn là API đọc trạng thái mong muốn.

## 3. Thứ tự ưu tiên: class > package cụ thể nhất > mặc định

Nếu một class có nhiều quy tắc phù hợp, sử dụng thứ tự sau:

1. **Quy tắc cho top-level class** nếu được chỉ định, có ưu tiên cao nhất, kể cả với nested class thuộc top-level đó.
2. **Quy tắc package cụ thể nhất** áp dụng cho class, tức package có tên dài nhất trong cây package áp dụng.
3. **Mặc định của chính class loader**, nếu không có override phù hợp.

~~~text
Cần quyết định assertion status của class C
                    |
                    v
    Có chỉ thị cho top-level class chứa C?
            |                     |
           Có                   Không
            |                     |
            v                     v
    Dùng class status      Có package status?
                                  |       |
                                 Có      Không
                                  |       |
                                  v       v
                        Chọn package   Dùng mặc định
                        cụ thể nhất    của loader
~~~

Ví dụ loader có chính sách:

~~~java
loader.setDefaultAssertionStatus(true);
loader.setPackageAssertionStatus("app", false);
loader.setPackageAssertionStatus("app.data", true);
loader.setClassAssertionStatus("app.data.Special", false);
~~~

Kết quả đối với những class khởi tạo sau khi thiết lập chính sách:

| Class | Chính sách được chọn | Assertion |
| --- | --- | --- |
| **other.Task** | Default của loader | Bật |
| **app.Main** | Package app | Tắt |
| **app.web.Controller** | Package app (bao gồm subpackage) | Tắt |
| **app.data.Repository** | Package app.data cụ thể hơn app | Bật |
| **app.data.deep.Parser** | Package app.data | Bật |
| **app.data.Special** | Class override | Tắt |
| **app.data.Special.Nested** | Chính sách top-level Special | Tắt |

Lưu ý tên trong bảng mô tả class theo cú pháp Java; binary name của nested class thông thường dùng ký tự **$**, nhưng API **setClassAssertionStatus** nhận **tên top-level class** khi muốn điều khiển cả lớp và nested classes của nó.

### 3.1 Package và subpackage được xác định bằng dấu chấm

Nếu đặt chính sách cho **app.data**, nó áp dụng cho chính **app.data** và các package bắt đầu bằng **app.data.**, chẳng hạn **app.data.deep**. Nó **không** áp dụng cho **app.database** vì tên này không bắt đầu bằng **app.data.**.

Java không có quan hệ kế thừa package trong ngôn ngữ: “subpackage” ở đây là cách API assertion dùng **tiền tố tên package** để chọn mặc định. Quy tắc package cụ thể hơn luôn thắng package cha, dù quy tắc cha được gọi trước hay sau.

### 3.2 Package không tên (unnamed package)

Có thể dùng:

~~~java
loader.setPackageAssertionStatus(null, true);
~~~

Giá trị **null** đại diện cho **unnamed package** hiện hành. Không dùng chuỗi rỗng thay cho null khi theo hợp đồng API. Ở code thực tế, nên tổ chức các class vào named package; nhưng tính năng này giúp kiểm soát ứng dụng hoặc ví dụ Java không khai báo package.

## 4. `setClassAssertionStatus` nhận tên top-level class

Đây là một chi tiết dễ bỏ sót trong Javadoc.

~~~java
loader.setClassAssertionStatus("com.example.Outer", true);
~~~

Thao tác này đặt chính sách cho top-level class **Outer** và các nested class trong Outer, như **Outer.Inner** hoặc **Outer.StaticNested**. Với nested class, giá trị mong muốn được lấy theo top-level enclosing class theo quy tắc assertion.

Ngược lại, dùng tên của nested class để gọi API:

~~~java
loader.setClassAssertionStatus("com.example.Outer$Inner", false);
~~~

**Không thay đổi assertion status thực tế của nested class** theo hợp đồng API. Tham số ở đây được thiết kế cho **top-level class**; nếu muốn điều khiển một nested class riêng biệt bằng string tên đó, phương thức này không cung cấp quyền chọn như vậy.

**Đừng nhầm với `ClassLoader.loadClass`:** loadClass thường nhận **binary name** của class đang tải, kể cả dấu **$** với nested class. Nhưng **setClassAssertionStatus** áp dụng chính sách assertion ở cấp **top-level** và nested classes bên trong.

Trong cùng một top-level class, nếu muốn có kiểm tra chỉ bật ở một số nơi, có thể tổ chức logic qua các top-level class riêng, hoặc sử dụng cơ chế kiểm soát/kiểm thử khác phù hợp; không lạm dụng chính sách assertion để điều khiển nghiệp vụ.

## 5. Thời điểm đặt chính sách: trước khi class được khởi tạo

Theo ClassLoader API, các setter ảnh hưởng tới **những class sẽ được khởi tạo trong tương lai**. Một class đã khởi tạo thì **không đổi trạng thái thực thi assertion** chỉ vì loader được cấu hình lại.

~~~text
Thời điểm A: loader.setClassAssertionStatus("app.Task", true)
                    |
                    v
Thời điểm B: app.Task initialized lần đầu
             → assertion status của Task được chốt
                    |
                    v
Thời điểm C: loader.setClassAssertionStatus("app.Task", false)
             → không tắt assertion đã được chốt cho Task
~~~

Bốn tình huống cần phân biệt:

- **Chỉ có đối tượng Class** (ví dụ lấy từ **Class.forName(name, false, loader)**) chưa đồng nghĩa class đã initialized.
- **Đã load/link nhưng chưa initialized:** thiết lập assertion status vẫn có thể ảnh hưởng lúc class được khởi tạo, theo hợp đồng API.
- **Đã initialized:** thay đổi cấu hình loader không đổi cách thực thi các assert đã được quyết định cho class đó.
- **Một class khác chưa initialized:** cấu hình mới có thể áp dụng cho class khác ấy ngay cả khi cùng package.

Vì vậy, nếu chương trình muốn điều khiển assertion của một nhóm class, nên đặt chính sách **trước khi có active use** của chúng (ví dụ trước khi new instance, gọi static method hoặc đọc static field không phải compile-time constant).

### 5.1 Assertion status không phải biến được đọc ở mỗi câu lệnh

Compiler có thể tạo trường tổng hợp như **$assertionsDisabled** trong bytecode class, với trạng thái dựa trên chính sách assertion khi class initialized. Một lần thay đổi loader về sau không khiến mọi assert trong class đã init tự đọc lại cấu hình.

Có thể quan sát bytecode bằng:

~~~bash
javap -c -p lab.rules.PackageProbe
~~~

Tùy compiler và class, output có thể có trường tổng hợp liên quan đến assertion; tên và bố cục là chi tiết biên dịch, không nên dùng làm public API.

## 6. Ví dụ chạy được số 1: cấp default, package, subpackage, class và clear

Ví dụ Java 17+ dưới đây kiểm tra toàn bộ thứ tự ưu tiên bằng những class chưa được khởi tạo. Mỗi **enabled()** cố ý dùng **assert false** và bắt AssertionError để xác định việc assert có được thực thi hay không. Đó là **test probe**, không phải khuyến nghị viết assertion kiểu này trong production.

Cấu trúc thư mục:

~~~text
project/
└── src/
    └── lab/
        ├── rules/
        │   ├── PolicyDemo.java
        │   └── deep/
        │       └── DeepProbe.java
        └── misc/
            └── DefaultProbe.java
~~~

**src/lab/rules/PolicyDemo.java**:

~~~java
package lab.rules;

import lab.misc.DefaultProbe;
import lab.rules.deep.DeepProbe;

public class PolicyDemo {
    public static void main(String[] args) {
        ClassLoader loader = PolicyDemo.class.getClassLoader();

        loader.clearAssertionStatus();
        loader.setDefaultAssertionStatus(true);
        loader.setPackageAssertionStatus("lab.rules", false);
        loader.setPackageAssertionStatus("lab.rules.deep", true);
        loader.setClassAssertionStatus("lab.rules.ClassProbe", true);

        System.out.println("package = " + PackageProbe.enabled());
        System.out.println("class = " + ClassProbe.enabled());
        System.out.println("nested = " + ClassProbe.Nested.enabled());
        System.out.println("subpackage = " + DeepProbe.enabled());
        System.out.println("default = " + DefaultProbe.enabled());

        loader.setPackageAssertionStatus("lab.rules", true);
        System.out.println("late package = " + LateProbe.enabled());

        loader.clearAssertionStatus();
        System.out.println("initialized class = " + ClassProbe.enabled());
        System.out.println("future class = " + FutureProbe.enabled());
    }
}

class PackageProbe {
    static boolean enabled() {
        try {
            assert false : "probe";
            return false;
        } catch (AssertionError expected) {
            return true;
        }
    }
}

class ClassProbe {
    static boolean enabled() {
        try {
            assert false : "probe";
            return false;
        } catch (AssertionError expected) {
            return true;
        }
    }

    static class Nested {
        static boolean enabled() {
            try {
                assert false : "probe";
                return false;
            } catch (AssertionError expected) {
                return true;
            }
        }
    }
}

class LateProbe {
    static boolean enabled() {
        try {
            assert false : "probe";
            return false;
        } catch (AssertionError expected) {
            return true;
        }
    }
}

class FutureProbe {
    static boolean enabled() {
        try {
            assert false : "probe";
            return false;
        } catch (AssertionError expected) {
            return true;
        }
    }
}
~~~

**src/lab/misc/DefaultProbe.java**:

~~~java
package lab.misc;

public class DefaultProbe {
    public static boolean enabled() {
        try {
            assert false : "probe";
            return false;
        } catch (AssertionError expected) {
            return true;
        }
    }
}
~~~

**src/lab/rules/deep/DeepProbe.java**:

~~~java
package lab.rules.deep;

public class DeepProbe {
    public static boolean enabled() {
        try {
            assert false : "probe";
            return false;
        } catch (AssertionError expected) {
            return true;
        }
    }
}
~~~

Đứng ở thư mục **project**, biên dịch và chạy:

~~~bash
javac -d out src/lab/rules/PolicyDemo.java src/lab/misc/DefaultProbe.java src/lab/rules/deep/DeepProbe.java
java -cp out lab.rules.PolicyDemo
~~~

Stdout đã được kiểm chứng trên JDK 21 (code dùng các API sẵn có từ Java 17):

~~~text
package = false
class = true
nested = true
subpackage = true
default = true
late package = true
initialized class = true
future class = false
~~~

### 6.1 Phân tích tám kết quả

**package = false:** **PackageProbe** ở package **lab.rules**. Quy tắc package false ưu tiên cao hơn default true.

**class = true:** **ClassProbe** cũng thuộc **lab.rules**, nhưng có override class true. Quy tắc class thắng package false.

**nested = true:** **ClassProbe.Nested** nằm bên trong top-level ClassProbe, nên kế thừa chính sách assert dành cho ClassProbe, dù có package rule false.

**subpackage = true:** **DeepProbe** thuộc **lab.rules.deep**. Quy tắc package cụ thể nhất **lab.rules.deep = true** thắng **lab.rules = false**.

**default = true:** **DefaultProbe** thuộc **lab.misc**, không có rule package/class riêng, nên dùng default true.

**late package = true:** Sau khi thay **lab.rules** thành true, **LateProbe** chưa initialized nhận chính sách mới.

**initialized class = true:** Trước đó ClassProbe đã initialized ở dòng 2. Lệnh **clearAssertionStatus()** không tắt assert đã được chốt trong ClassProbe.

**future class = false:** FutureProbe đến lúc này mới initialized. **clearAssertionStatus()** đã xóa các override và reset default về false, nên FutureProbe có assertion disabled.

### 6.2 Vì sao ví dụ chủ động gọi `clearAssertionStatus` ngay đầu?

Để kết quả độc lập với cấu hình assertion mà JVM hoặc người chạy đã truyền cho application loader, chương trình dọn trạng thái loader trước khi đặt các rule. **clearAssertionStatus** không mang nghĩa “trả về các thiết lập ban đầu từ dòng lệnh”; nó **đặt default=false và xóa tất cả override theo package/class trên loader này**. Nếu cần khôi phục một policy cụ thể, phải thiết lập lại các rule tương ứng bằng API.

Không áp dụng kỹ thuật reset assertion status một cách tùy tiện lên application loader trong sản phẩm lớn: nhiều thư viện có thể đã khởi tạo class hoặc phụ thuộc vào cấu hình assertion của môi trường. Ví dụ chỉ minh họa ý nghĩa API trong chương trình cô lập.

## 7. Điều khiển assertion bằng JVM options

Trước khi JVM khởi chạy ứng dụng, người chạy có thể truyền các tùy chọn:

| Tùy chọn | Ý nghĩa |
| --- | --- |
| **-ea** hoặc **-enableassertions** | Bật assertion mặc định cho class ứng dụng thích hợp. |
| **-da** hoặc **-disableassertions** | Tắt assertion mặc định. |
| **-ea:com.example...** | Bật assertion trong package com.example và subpackages. |
| **-da:com.example.internal...** | Tắt assertion trong package internal và các subpackages. |
| **-ea:com.example.Service** | Bật assertion cho top-level class được nêu (và nested classes của nó). |
| **-da:com.example.Service** | Tắt assertion cho class được nêu. |
| **-ea:...** | Bật assertion trong unnamed package. |
| **-esa** hoặc **-enablesystemassertions** | Bật assertion trong system classes. |
| **-dsa** hoặc **-disablesystemassertions** | Tắt assertion trong system classes. |

Các option có thể kết hợp; JVM xử lý các tùy chọn theo thứ tự trên command line trước khi loading những class liên quan. Trường hợp **-ea** không đối số có một ngoại lệ đối với system classes: dùng **-esa/-dsa** khi muốn điều khiển riêng những class hệ thống.

### 7.1 Ví dụ chạy được số 2: class option khác package option

Tạo file **src/lab/flags/FlagsDemo.java**:

~~~java
package lab.flags;

public class FlagsDemo {
    static class Nested {
        static boolean enabled() {
            try {
                assert false : "probe";
                return false;
            } catch (AssertionError expected) {
                return true;
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("nested = " + Nested.enabled());
        System.out.println("peer = " + Peer.enabled());
    }
}

class Peer {
    static boolean enabled() {
        try {
            assert false : "probe";
            return false;
        } catch (AssertionError expected) {
            return true;
        }
    }
}
~~~

Từ thư mục project:

~~~bash
javac -d out src/lab/flags/FlagsDemo.java
java -ea:lab.flags.FlagsDemo -cp out lab.flags.FlagsDemo
~~~

Stdout:

~~~text
nested = true
peer = false
~~~

Giải thích: option **-ea:lab.flags.FlagsDemo** bật assertion cho top-level class FlagsDemo và nested class **FlagsDemo.Nested**, nhưng **Peer** là top-level class khác trong cùng package nên vẫn theo mặc định (false).

Lệnh khác:

~~~bash
java -ea:lab.flags... -da:lab.flags.Peer -cp out lab.flags.FlagsDemo
~~~

Stdout:

~~~text
nested = true
peer = false
~~~

Ở đây package **lab.flags** được bật trước, nhưng option riêng cho class **Peer** được tắt. Kết quả tương tự, nhưng **nguồn quyết định** đã khác.

Nếu chỉ chạy:

~~~bash
java -ea:lab.flags... -cp out lab.flags.FlagsDemo
~~~

kết quả sẽ là:

~~~text
nested = true
peer = true
~~~

Vì cả FlagsDemo.Nested và Peer đều nằm trong package được bật.

### 7.2 So sánh dòng lệnh và API của ClassLoader

| Phương diện | Tùy chọn `java -ea/-da` | Phương thức ClassLoader |
| --- | --- | --- |
| Ai cấu hình? | Người chạy JVM / script chạy ứng dụng | Code đang có tham chiếu loader |
| Thời điểm điển hình | Trước khi chương trình chạy | Trước lúc những class tương ứng khởi tạo |
| Phạm vi | Có thể hướng đến nhiều loader theo option JVM | Áp dụng chính sách trên object loader được gọi |
| Cấp class/package/default | Có các lựa chọn tương ứng | Có ba setter cụ thể |
| Xóa toàn bộ override? | Đặt lại option lúc khởi động | **clearAssertionStatus()** trên loader |
| Tác động tới class đã initialized? | Không thể đổi bằng CLI trong cùng lần chạy | Setter sau đó cũng không đổi trạng thái đã chốt |

Các API ClassLoader cho phép một loader chủ động thay đổi thiết lập assertion liên quan đến loader ấy, kể cả bỏ qua cấu hình từ command line khi gọi clearAssertionStatus. Trong ứng dụng thực tế, cần kiểm tra loader **thực sự định nghĩa** class để tránh đặt chính sách lên child loader trong khi parent mới là nơi cung cấp Class.

## 8. ClassLoader nào phải nhận cấu hình?

Một ứng dụng có thể có bootstrap loader, platform loader, application loader, và nhiều loader cho plugin. Mỗi class thông thường có một **defining loader**; các loader khác có thể delegate để nhận cùng đối tượng Class.

Nếu gọi:

~~~java
pluginLoader.setPackageAssertionStatus("shared.api", true);
~~~

nhưng **shared.api** được parent loader định nghĩa thông qua delegation, không thể mặc nhiên kết luận rule trên pluginLoader sẽ điều khiển assertion của class do parent định nghĩa. Cấu hình phải tương ứng với **loader quản lý việc định nghĩa và chính sách assertion của class đó**.

Khi gỡ lỗi:

~~~java
Class<?> type = loader.loadClass("com.example.Service");
System.out.println(type.getClassLoader());
~~~

`type.getClassLoader()` cho biết **defining loader** của class (hoặc null nếu bootstrap). Lời gọi **loader.loadClass(name)** có thể trả về Class do loader khác định nghĩa. Muốn áp dụng rule chính xác, cần hiểu delegation và nơi lớp sẽ được initialized.

**Ví dụ dễ nhầm:** plugin loader child-first đối với package nội bộ, nhưng parent-first đối với các API chung. Chính sách assertion riêng ở plugin loader có thể phù hợp với implementation class của plugin; những class API từ parent phải theo policy của parent.

## 9. Thời điểm assertion khác với thời điểm lập trình viên gọi setter

Giả sử:

~~~java
Class<?> metadata = Class.forName("lab.rules.LateProbe", false, loader);
loader.setPackageAssertionStatus("lab.rules", true);
~~~

Nếu LateProbe **chưa initialized**, việc thay policy có thể ảnh hưởng khi nó được sử dụng lần đầu. **Class.forName(name, false, loader)** theo API lấy Class mà không chủ động initialize. Ngược lại:

~~~java
Class<?> metadata = Class.forName("lab.rules.LateProbe", true, loader);
loader.setPackageAssertionStatus("lab.rules", true);
~~~

Với initialize=true, LateProbe đã được yêu cầu khởi tạo trước khi setter chạy; nếu initialization thành công, đổi policy sau đó không thay assertion status đã chốt.

Hai đoạn là **minh họa ý nghĩa API**, không phải chương trình độc lập: cần có sẵn loader và class phù hợp. Ngoài ra JVM có thể đã khởi tạo class đó từ một active use khác trước khi gặp đoạn code. Không nên xem `Class.forName(..., false, ...)` như cách quay ngược trạng thái một class đã initialized.

## 10. `Class.desiredAssertionStatus()` dùng thế nào?

API:

~~~java
boolean desired = SomeClass.class.desiredAssertionStatus();
~~~

trả về trạng thái assertion **mong muốn** cho class theo cấu hình phù hợp. Từ “desired” rất quan trọng: nó giúp xem cấu hình ở góc độ Class API, nhưng không nên sử dụng thao tác gọi lại sau khi cấu hình thay đổi để kết luận rằng một class **đã initialized** sẽ đổi trạng thái thực thi các lệnh assert.

Để kiểm tra hành vi thực sự của một câu lệnh assert, hãy quan sát chính class chứa nó (như method **enabled()** trong ví dụ), hoặc dùng test cô lập với class chưa khởi tạo. Không dựa riêng trên tên package, tên loader hoặc giá trị một Boolean cấu hình để phán đoán mã đã initialized.

Nếu assert mang biểu thức có side effect, trạng thái enabled/disabled sẽ làm hành vi chương trình khác nhau, đây chính là lý do tránh dùng assert cho validation bắt buộc.

## 11. Khi nào nên dùng assertion, khi nào không?

Assertion thích hợp cho **invariant nội bộ** mà lập trình viên tin rằng phải đúng nếu code triển khai đúng, ví dụ:

~~~java
assert index >= 0 && index < buffer.length;
assert state != null : "Internal state must exist";
~~~

Với dữ liệu đầu vào từ người dùng, validation bắt buộc hoặc điều kiện an toàn của hệ thống, **không dùng assert làm cơ chế duy nhất**:

~~~java
// Validation bắt buộc, chạy cả khi assertions bị tắt:
if (amount < 0) {
    throw new IllegalArgumentException("amount must be >= 0");
}
~~~

Trong production, assertions có thể bị tắt theo mặc định hoặc được cấu hình chọn lọc. Một chương trình đúng phải tiếp tục thực hiện các thao tác cần thiết bất kể policy assertion.

Tránh:

~~~java
// SAI: khi assertions disabled, counter++ sẽ không chạy.
assert counter++ >= 0;
~~~

Ưu tiên tính toán tách biệt:

~~~java
counter++;
assert counter >= 0;
~~~

Hai đoạn cuối là snippet minh họa đặt trong method có biến counter phù hợp.

## 12. Những nhầm lẫn phổ biến

### 12.1 “Đặt default=true có thể bật lại tất cả class đã khởi tạo”

Không. Setter áp dụng cho những class **sẽ được initialized**; class đã initialized có trạng thái assertion cố định. Muốn test chính sách mới, hãy dùng class mới chưa initialized hoặc chạy lại JVM với cấu hình khác.

### 12.2 “Đặt package=false là đủ dù class đã được bật riêng”

Không. Class-specific override thắng package, kể cả package cụ thể nhất. Nếu cần thay đổi chính sách cho class chưa initialized, phải chỉnh chính rule class (hoặc xóa trạng thái trên loader rồi cấu hình lại, hiểu rõ hậu quả với class khác).

### 12.3 “Quy tắc package cha luôn ưu tiên hơn package con”

Sai. Package có tên **cụ thể nhất** thắng. Ví dụ **app.data=true** thắng **app=false** cho **app.data.deep.Model**, trừ khi có rule class cụ thể.

### 12.4 “Có thể set `Outer$Inner` để chỉ bật nested class”

Theo hợp đồng **setClassAssertionStatus**, lời gọi chỉ áp dụng cho tên **top-level class** và nested classes trong đó. Dùng tên không phải top-level không thay đổi trạng thái thực tế của class được nhắm. Dùng tên **Outer** để kiểm soát cả nhóm.

### 12.5 “clearAssertionStatus khôi phục mọi cài đặt từ lệnh java”

Không. Phương thức đặt default của loader thành **false** và xóa các rule package/class của loader. Nó không phải cơ chế hoàn nguyên giá trị trước đó.

### 12.6 “Đã có `Class` nghĩa là assertion status đã chốt”

Không nhất thiết. **Loading** và **initialization** là hai giai đoạn khác nhau; status được quyết định theo quy tắc initialization của class. Cẩn thận nếu một thao tác reflection hoặc active use khác đã khởi tạo class trước.

### 12.7 “Set rule trên child loader chắc chắn tác động class parent”

Không thể khẳng định. Delegation có thể trả Class do parent định nghĩa. Kiểm tra **getClassLoader()**, cách loader phân phối định nghĩa, và thời điểm class khởi tạo.

### 12.8 “AssertionError nên bị catch để chương trình chạy tiếp trong production”

Trong ví dụ, bắt AssertionError chỉ để **quan sát trạng thái assertion một cách quyết định**. Trong code ứng dụng, AssertionError thường biểu thị giả định nội bộ bị phá vỡ, nên cần điều tra sửa logic thay vì nuốt lỗi.

## 13. Quy trình kiểm tra khi assertion không chạy

1. Xác nhận code thực sự chứa **assert** và execution path đi qua câu lệnh đó.
2. Xác định class chứa assert đã **initialized** chưa; setter sau thời điểm đó sẽ không đổi hành vi.
3. Kiểm tra có option JVM **-ea/-da/-esa/-dsa** hay không.
4. Với loader được dùng, xem **default**, package rule cụ thể nhất và class rule.
5. Nếu class nested, xác định đúng **top-level class** chi phối policy.
6. Kiểm tra **defining loader** bằng **Class.getClassLoader()**, nhất là với plugin/classloader delegation.
7. Nếu dùng **clearAssertionStatus()**, nhớ nó đã reset default=false và xóa các override.
8. Viết test nhỏ chạy trong JVM mới để tránh class đã initialized và so sánh kết quả.

Đặc biệt, không kết luận class “chưa loaded” chỉ vì một assert không chạy: assert có thể đơn giản đã bị disable.

## 14. Bài tập tự kiểm tra

**Câu 1.** Default của loader là true; package **shop** được đặt false; class **shop.Cart** được đặt true. Assertion trong Cart bật hay tắt nếu Cart chưa initialized?

**Đáp án:** Bật. Rule theo class ưu tiên cao hơn package.

**Câu 2.** Default false; package **shop=true**, package **shop.internal=false**. Class **shop.internal.engine.Parser** dùng rule nào?

**Đáp án:** **shop.internal=false**, vì đây là package cụ thể nhất phù hợp.

**Câu 3.** Nếu class đã initialized với assertion disabled, gọi **setClassAssertionStatus(className, true)** có bật assert của class đó không?

**Đáp án:** Không; trạng thái thực thi của class đã initialized không thay đổi.

**Câu 4.** Muốn bật assertion trong top-level **sample.Outer** và nested **Outer.Nested**, gọi API nào?

**Đáp án:** **setClassAssertionStatus("sample.Outer", true)** trước initialization. Không dùng chuỗi **sample.Outer$Nested** để điều khiển riêng nested class.

**Câu 5.** **clearAssertionStatus()** có khôi phục trạng thái từ option **-ea** không?

**Đáp án:** Không; nó xóa rule package/class và đặt default=false trên loader.

**Câu 6.** Câu **assert x++ > 0;** gây lỗi logic gì khi assertion tắt?

**Đáp án:** Biểu thức không được đánh giá, nên **x++ không chạy**. Assertion không nên chứa side effect bắt buộc.

**Câu 7.** Vì sao **-ea:lab.flags.FlagsDemo** bật nested class nhưng không bật class ngang hàng **Peer**?

**Đáp án:** Option nhắm tới top-level FlagsDemo và nested classes thuộc nó; Peer là top-level class độc lập.

**Câu 8.** Đã set package rule trên pluginLoader nhưng class thực tế do parent định nghĩa. Cần kiểm tra gì?

**Đáp án:** Xem **Class.getClassLoader()**, delegation và nơi policy của defining loader được quản lý; không mặc nhiên áp dụng rule trên child cho class của parent.

## 15. Tổng kết

Ba cấp kiểm soát assertion của ClassLoader được áp dụng theo nguyên tắc:

~~~text
       class-specific rule
               ↓ thắng
       package-specific rule
       (package sâu nhất)
               ↓ thắng
       default rule của loader
~~~

- **setDefaultAssertionStatus** đặt giá trị mặc định.
- **setPackageAssertionStatus** áp dụng cho một package và các subpackages; package cụ thể hơn thắng.
- **setClassAssertionStatus** áp dụng cho top-level class và nested classes của nó; class rule thắng các rule khác.
- **clearAssertionStatus** xóa rule và đặt default về false.
- Các chỉnh sửa chính sách ảnh hưởng tới class **sẽ initialized**, không đảo ngược trạng thái class đã initialized.
- JVM options **-ea/-da** cho phép kiểm soát ngay lúc chạy; ứng dụng còn có thể chủ động đặt policy trên loader.
- Assertion dùng để kiểm tra giả định nội bộ, **không thay validation bắt buộc** và không nên có side effect.

### Tài liệu tham khảo

- **ClassLoader, Java SE 21** – bốn phương thức điều khiển assertion và quy tắc ưu tiên: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html
- **The java Command, Java 17** – tùy chọn -ea, -da, -esa, -dsa: https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- **JLS §14.10 (The assert Statement), Java SE 21** – quy tắc thực thi assert: https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.10
- **JLS §12.4 (Initialization), Java SE 21** – thời điểm khởi tạo class và interface: https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.4


</details>

- [Quay lại đầu trang](#back-to-top)
