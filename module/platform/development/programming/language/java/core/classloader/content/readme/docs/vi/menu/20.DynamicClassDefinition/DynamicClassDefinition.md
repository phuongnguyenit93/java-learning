<a id="back-to-top"></a>

# Định nghĩa lớp động

## Menu
- [Reflection và việc chọn loader cho tên lớp](#dynamic-class-definition-001)
- [Dynamic Proxy và khả năng nhìn thấy các interface](#dynamic-class-definition-002)
- [MethodHandles.Lookup.defineClass và ngữ cảnh định nghĩa lớp](#dynamic-class-definition-003)
- [Hidden class và giới hạn khám phá bằng tên](#dynamic-class-definition-004)
- [Phân biệt bootstrap class loader với bootstrap method của invokedynamic](#dynamic-class-definition-005)
- [Sinh bytecode, biên dịch động và trách nhiệm quản lý loader](#dynamic-class-definition-006)
- [Instrumentation: transform trước khi định nghĩa lớp](#dynamic-class-definition-007)
- [Redefine, retransform và khác biệt với nạp lớp bằng loader mới](#dynamic-class-definition-008)
- [Ràng buộc phiên bản, cấu trúc lớp và quyền truy cập khi tạo lớp động](#dynamic-class-definition-009)

## <a id="dynamic-class-definition-001">Reflection và việc chọn loader cho tên lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Reflection và việc chọn loader cho tên lớp

## 1. Vấn đề cốt lõi: tên lớp không tự quyết định lớp runtime

Trong Java, reflection cho phép tìm một lớp qua tên dạng chuỗi, xem constructor/method/field và tạo instance hoặc gọi method khi chương trình đang chạy. Nhưng câu hỏi **“tìm lớp bằng tên nào?”** luôn phải đi kèm câu hỏi **“dùng class loader nào để tìm?”**.

Ví dụ, chuỗi `"plugin.OnlyChild"` chỉ là **binary name**. Nếu class đó nằm trong plugin JAR/thư mục chỉ được một loader con nhìn thấy, thì application loader của host chưa chắc tìm được. Thậm chí hai loader khác nhau có thể định nghĩa **hai class runtime khác nhau cùng binary name**.

Theo mô hình JVM, danh tính của class thông thường dựa trên **binary name + defining class loader**, không chỉ chuỗi tên.

~~~text
                    Host / Application Loader
                    ├── ReflectionLoaderDemo
                    └── API dùng chung
                               |
              +----------------+----------------+
              |                                 |
       URLClassLoader A                   URLClassLoader B
       plugin.OnlyChild                   plugin.OnlyChild
         (Class A)                           (Class B)
              |                                 |
           cùng tên và cùng bytes, nhưng Class A != Class B
~~~

Vì vậy, lựa chọn loader ảnh hưởng đến:

- **ClassNotFoundException**: một loader không thấy class dù class có ở một nguồn khác.
- **ClassCastException**: instance từ class do loader B định nghĩa không thể ép thành class cùng tên do loader A định nghĩa.
- **Liên kết phụ thuộc**: khi class được định nghĩa bởi loader nào, symbolic references trong bytecode của nó được JVM giải qua defining loader thích hợp, không tự động qua loader của code đang dùng reflection.
- **Initialization**: một số API tìm class chỉ nạp class, API khác chủ động khởi tạo nó, làm khối `static { ... }` chạy.
- **Module access**: tìm được đối tượng `Class<?>` không bảo đảm được phép gọi constructor hoặc truy cập member private.

Bài này tập trung vào **chọn loader cho một binary name khi dùng reflection**, phân biệt `Class.forName`, `ClassLoader.loadClass`, TCCL và class identity.

## 2. Những API tìm lớp theo tên cần phân biệt

| API | Loader dùng để tìm | Có yêu cầu initialization? | Khi không thấy |
| --- | --- | --- | --- |
| `Class.forName("pkg.Type")` | Defining loader của **class đang thực hiện lời gọi** | **Có** | `ClassNotFoundException` |
| `Class.forName("pkg.Type", false, loader)` | `loader` tường minh | **Không** chủ động initialize | `ClassNotFoundException` |
| `Class.forName("pkg.Type", true, loader)` | `loader` tường minh | **Có** nếu chưa initialized | `ClassNotFoundException` |
| `loader.loadClass("pkg.Type")` | `loader` được gọi, theo delegation policy của nó | **Không** chủ động initialize | `ClassNotFoundException` |
| `Class.forName(module, "pkg.Type")` | Module và cơ chế loader gắn với module đó | **Không** link hay initialize theo hợp đồng overload này | **`null`** khi không tìm đúng class trong module |
| `SomeType.class` | Class literal được compiler/JVM biết qua bytecode hiện tại | Không phải một phép tìm bằng chuỗi; không chủ động initialize chỉ do dùng literal | Lỗi compile/linkage phù hợp nếu type không sẵn |

**Đừng suy diễn** rằng `Class.forName(name)` tương đương `Class.forName(name, true, Thread.currentThread().getContextClassLoader())`. Theo Java API, overload một tham số dùng **defining class loader của class thực hiện lời gọi**, **không tự lấy TCCL**.

Tương tự, `ClassLoader.loadClass(name)` tương đương overload `loadClass(name, false)` theo API mặc định: nó không chủ động chạy static initializer. Từ `resolve` trong overload protected `loadClass(name, boolean resolve)` nói về **linking**, không phải cờ `initialize` của `Class.forName`.

### Khi chọn loader=null

~~~java
Class<?> jdk = Class.forName("java.lang.String", false, null);
~~~

Đoạn trên minh họa overload ba tham số: `loader == null` ở đây nghĩa là yêu cầu **bootstrap class loader**, không phải lấy TCCL hoặc tự động chọn application loader. Với class của ứng dụng/plugin không do bootstrap nhìn thấy, truyền `null` thường không tìm được.

Cũng vì thế, `String.class.getClassLoader()` thường trả `null`: đó là đại diện Java API cho bootstrap loader, **không có nghĩa String chưa được nạp**.

## 3. Chọn loader thế nào trong host và plugin?

Giả sử host được chạy với:

~~~text
java -cp out/host ReflectionLoaderDemo out/plugin
~~~

Các file plugin nằm tại:

~~~text
out/plugin/plugin/OnlyChild.class
~~~

Ứng dụng host có thể tạo một `URLClassLoader` riêng với root `out/plugin`:

~~~java
URL source = Path.of("out/plugin").toAbsolutePath().toUri().toURL();

try (URLClassLoader pluginLoader = new URLClassLoader(
        new URL[]{source}, Host.class.getClassLoader())) {
    Class<?> pluginClass = Class.forName(
            "plugin.OnlyChild", false, pluginLoader);
}
~~~

Đây là đoạn **minh họa**, giả định `Host` và các imports tương ứng tồn tại; chương trình hoàn chỉnh nằm ở phần 6. `URLClassLoader` mặc định dùng parent-first delegation:

~~~text
Class.forName(name, false, pluginLoader)
                   |
                   v
          pluginLoader.loadClass(name)
                   |
           đã nạp tên này chưa?
                   |
             hỏi loader cha
               /       \
           có class    không
              |           |
           trả cha    tìm từ URL plugin
                          |
                        defineClass
~~~

Điều cần nhớ:

- **Chỉ định `pluginLoader` không bảo đảm chính loader này định nghĩa class.** Nếu parent đã nhìn thấy một class cùng binary name, thuật toán parent-first có thể trả class của parent.
- `clazz.getClassLoader()` cho biết **defining loader** của đối tượng `Class<?>`, không nhất thiết là loader được truyền vào ban đầu.
- `clazz.getName()` chỉ cho binary name; hai class từ hai loader có thể có `getName()` bằng nhau nhưng `clazzA != clazzB`.
- Nếu cần child-first, phải thiết kế chính sách nạp riêng và quản lý ranh giới API/loader identity. Không thể đảo thứ tự delegation bằng cách đổi tham số `initialize`.

### Source và runtime classpath không giống nhau

Khi biên dịch host, nếu host chỉ dùng reflection bằng tên chuỗi, nó **không cần compile-time import** implementation plugin. Ở runtime, không nên đưa plugin vào `-cp` của host nếu mục tiêu là chứng minh plugin chỉ nhìn thấy qua child loader. Nếu cùng lúc đưa plugin vào application classpath, parent sẽ có thể nhìn thấy class và thử nghiệm loader con không còn thể hiện đúng sự khác nhau.

## 4. Class.forName, loading, linking và initialization

Các giai đoạn cần phân biệt:

| Giai đoạn | Diễn giải | Có chạy khối `static`? |
| --- | --- | --- |
| Loading | Tạo hoặc tìm đối tượng `Class<?>` qua cơ chế loader. | Không tự động chỉ vì vừa load. |
| Linking | Verification, preparation, resolution theo quy tắc JVM. | Không phải initialization. |
| Initialization | Thực thi static initializer của class khi chưa được initialize. | **Có** khi initializer tồn tại. |

Trong API `Class.forName(name, boolean initialize, loader)`:

- `initialize=false`: yêu cầu load/link theo hợp đồng phù hợp nhưng **không chủ động initialize**. JVM có thể làm một số linking theo thời điểm hợp lệ; đừng diễn giải thành “chưa link bất cứ thứ gì”.
- `initialize=true`: sau bước tìm và link theo yêu cầu, **chủ động initialize** class nếu chưa initialized.
- `Class.forName(name)` tương đương `Class.forName(name, true, callerDefiningLoader)` theo Javadoc. Caller ở đây là class chứa lời gọi, không phải bất kỳ object/plugin class nào có liên quan về mặt logic.
- `loader.loadClass(name)` chỉ trả `Class<?>` qua loader, không yêu cầu chạy initializer.

Ví dụ định nghĩa plugin:

~~~java
package plugin;

public final class OnlyChild {
    static {
        System.out.println("OnlyChild initialized");
    }

    public String message() {
        return "plugin says hello";
    }
}
~~~

Khi chạy:

~~~java
Class<?> type = Class.forName("plugin.OnlyChild", false, pluginLoader);
System.out.println(type.getName()); // chưa chủ động initialize
Class.forName("plugin.OnlyChild", true, pluginLoader); // active initialization
~~~

Đây chỉ là **đoạn minh họa** nằm trong ngữ cảnh có `pluginLoader`; chương trình độc lập sẽ chứng minh lần khởi tạo ở phần 6. Tên class literal, truy vấn metadata và load bình thường không tương đương việc gọi `new`, gọi static method hay yêu cầu initialization bằng `Class.forName(..., true, ...)`.

**Lưu ý:** nếu class đã initialized trước đó, gọi `forName(..., true, ...)` lần nữa **không** chạy lại `static`. Nhưng hai loader khác nhau định nghĩa hai class runtime cùng tên, nên mỗi bản có quá trình initialization riêng.

## 5. Thread context class loader (TCCL) không phải “loader toàn cục”

Java hỗ trợ:

~~~java
ClassLoader context =
        Thread.currentThread().getContextClassLoader();
~~~

TCCL là **một thuộc tính của thread**. Nó thường được framework, API dịch vụ, container hoặc thư viện dùng để tìm providers/resources theo ngữ cảnh xử lý. Ví dụ:

~~~java
ClassLoader old = Thread.currentThread().getContextClassLoader();
try {
    Thread.currentThread().setContextClassLoader(pluginLoader);
    Class<?> plugin = Thread.currentThread()
            .getContextClassLoader().loadClass("plugin.OnlyChild");
} finally {
    Thread.currentThread().setContextClassLoader(old);
}
~~~

Đoạn trên giả định `pluginLoader` đã tạo. Cần **khôi phục TCCL cũ trong finally**, nhất là với thread pool, để không làm tác vụ sau vô tình dùng loader plugin và không giữ loader sống ngoài mong muốn.

**Nhưng:** riêng `Class.forName("plugin.OnlyChild")` trong code host **không tự dùng TCCL** vừa được gán. Nó vẫn dựa vào defining loader của class chứa lời gọi; chỉ phiên bản truyền `loader` tường minh mới cho bạn kiểm soát loader ngay trong API này.

TCCL cũng không thay đổi quy tắc JVM khi giải symbolic reference của bytecode: theo JVMS, khi class D có symbolic reference tới class C, bước resolve class reference sử dụng **defining loader của D**. Không phải JVM nhìn TCCL mỗi lần cần một class phụ thuộc.

### Khi nào nên dùng TCCL?

- Khi API/framework **được tài liệu quy định rõ là dùng TCCL**, ví dụ overload `ServiceLoader.load(ServiceType.class)` thường chọn context class loader.
- Khi host chủ động muốn code/library tìm implementation theo loader plugin mà framework cho phép.
- Khi cần giữ tương thích với SPI/container đã dựa trên TCCL.

Nếu đang tự làm reflection bằng tên class, cách dễ đọc và dễ kiểm thử hơn thường là **truyền loader tường minh cho `Class.forName(name,false,loader)`** thay vì thay đổi trạng thái thread để hy vọng một API chọn đúng nguồn.

## 6. Thực hành Java 17+ chạy độc lập: hai loader và reflection

Mục tiêu: chương trình host không có plugin trên application classpath; hai URLClassLoader đều nhìn thấy cùng file plugin; mỗi loader định nghĩa một runtime class riêng.

### Cây source

~~~text
example/
├── src/
│   ├── host/
│   │   └── ReflectionLoaderDemo.java
│   └── child/
│       └── plugin/
│           └── OnlyChild.java
└── out/
    ├── host/               (tạo sau biên dịch)
    └── plugin/
        └── plugin/
            └── OnlyChild.class
~~~

### File src/child/plugin/OnlyChild.java

~~~java
package plugin;

public final class OnlyChild {
    static { System.out.println("OnlyChild initialized"); }

    public String message() {
        return "plugin says hello";
    }
}
~~~

### File src/host/ReflectionLoaderDemo.java

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class ReflectionLoaderDemo {
    public static void main(String[] args) throws Exception {
        URL source = Path.of(args[0])
                .toAbsolutePath().toUri().toURL();

        try (URLClassLoader first = new URLClassLoader(
                     new URL[]{source},
                     ReflectionLoaderDemo.class.getClassLoader());
             URLClassLoader second = new URLClassLoader(
                     new URL[]{source},
                     ReflectionLoaderDemo.class.getClassLoader())) {

            try {
                Class.forName("plugin.OnlyChild");
                System.out.println("caller unexpectedly found plugin");
            } catch (ClassNotFoundException expected) {
                System.out.println(
                        "caller loader: ClassNotFoundException");
            }

            Class<?> typeA =
                    Class.forName("plugin.OnlyChild", false, first);
            System.out.println("first defines plugin: "
                    + (typeA.getClassLoader() == first));
            System.out.println("loadClass same Class: "
                    + (typeA == first.loadClass("plugin.OnlyChild")));

            Thread current = Thread.currentThread();
            ClassLoader oldContext = current.getContextClassLoader();
            try {
                current.setContextClassLoader(first);
                try {
                    Class.forName("plugin.OnlyChild");
                    System.out.println("TCCL unexpectedly used");
                } catch (ClassNotFoundException expected) {
                    System.out.println("forName ignores TCCL: true");
                }
                System.out.println("explicit TCCL works: "
                        + (current.getContextClassLoader()
                            .loadClass("plugin.OnlyChild") == typeA));
            } finally {
                current.setContextClassLoader(oldContext);
            }

            System.out.println("request initialization");
            Class.forName("plugin.OnlyChild", true, first);

            Object instanceA =
                    typeA.getDeclaredConstructor().newInstance();
            System.out.println("reflection: "
                    + typeA.getMethod("message").invoke(instanceA));

            Class<?> typeB =
                    Class.forName("plugin.OnlyChild", false, second);
            System.out.println("same name: "
                    + typeA.getName().equals(typeB.getName()));
            System.out.println("same runtime Class: "
                    + (typeA == typeB));

            Object instanceB =
                    typeB.getDeclaredConstructor().newInstance();

            try {
                typeA.cast(instanceB);
                System.out.println("unexpected cast success");
            } catch (ClassCastException expected) {
                System.out.println(
                        "cross-loader cast: ClassCastException");
            }

            System.out.println("bootstrap represented as null: "
                    + (String.class.getClassLoader() == null));
        }
    }
}
~~~

**Giải thích thiết kế:**

- `first` và `second` là **hai loader instance khác nhau** cùng nhận URL của thư mục plugin.
- Parent đều là loader của `ReflectionLoaderDemo`, nhưng host không đưa thư mục plugin lên `-cp`, nên parent không có `plugin.OnlyChild`.
- `Class.forName(name)` ở trong host tìm bằng caller defining loader, vì vậy ném `ClassNotFoundException`.
- Overload `Class.forName(name,false,first)` tìm thành công nhưng chưa chủ động khởi tạo plugin.
- Đổi TCCL sang first **không** khiến overload `Class.forName(name)` tự dùng first.
- `Class.forName(name,true,first)` chủ động khởi tạo class do first định nghĩa.
- Instance từ `second` không cast thành class của `first` dù binary name giống hệt.
- `try-with-resources` đóng các URLClassLoader sau khi thí nghiệm kết thúc; đóng loader giải phóng nguồn I/O, không đảm bảo unload class ngay lập tức.

### Biên dịch và chạy Linux/macOS

Trong thư mục `example` có hai source:

~~~bash
mkdir -p out/plugin out/host

javac --release 17 -d out/plugin src/child/plugin/OnlyChild.java
javac --release 17 -d out/host src/host/ReflectionLoaderDemo.java

java -cp out/host ReflectionLoaderDemo out/plugin
~~~

### Biên dịch và chạy Windows PowerShell

~~~powershell
New-Item -ItemType Directory -Force out\plugin, out\host | Out-Null
javac --release 17 -d out\plugin src\child\plugin\OnlyChild.java
javac --release 17 -d out\host src\host\ReflectionLoaderDemo.java
java -cp out\host ReflectionLoaderDemo out\plugin
~~~

**Không** thêm `out/plugin` vào `java -cp`; điều đó sẽ khiến parent loader cũng tìm được plugin, làm thay đổi kết quả kiểm chứng.

### Stdout đã kiểm chứng

~~~text
caller loader: ClassNotFoundException
first defines plugin: true
loadClass same Class: true
forName ignores TCCL: true
explicit TCCL works: true
request initialization
OnlyChild initialized
reflection: plugin says hello
same name: true
same runtime Class: false
OnlyChild initialized
cross-loader cast: ClassCastException
bootstrap represented as null: true
~~~

Mã đã được **biên dịch với `javac --release 17` và chạy trên OpenJDK 21** với thư mục plugin tách khỏi host classpath; stdout khớp nguyên văn.

### Đọc từng điểm của output

1. **Caller loader thất bại** vì `ReflectionLoaderDemo` được định nghĩa ở host loader, không nhìn thấy thư mục plugin.
2. **first defines plugin: true** xác nhận plugin thực sự do first định nghĩa, không chỉ first đóng vai initiating loader.
3. **loadClass same Class: true** cho thấy lookup lặp của cùng loader không tự define lại class.
4. **forName ignores TCCL: true** chứng minh overload một tham số không tự chọn context loader.
5. **explicit TCCL works: true** chứng minh khi bạn **chủ động gọi** `contextLoader.loadClass`, lookup mới dùng context loader đã chọn.
6. **request initialization**, sau đó **OnlyChild initialized**: class first chỉ chạy initializer khi yêu cầu initialize=true.
7. **reflection: plugin says hello**: reflection đã tạo instance rồi gọi public instance method trên đúng `Class<?>`.
8. **same name: true / same runtime Class: false**: binary name giống nhưng defining loaders khác.
9. Dòng **OnlyChild initialized** thứ hai xuất hiện khi tạo instance từ class second; đó là **một class runtime khác**, không phải JVM khởi tạo lại class first.
10. **cross-loader cast: ClassCastException** là hậu quả của khác danh tính kiểu.
11. **bootstrap represented as null: true** minh họa cách `Class.getClassLoader()` biểu diễn bootstrap loader với `java.lang.String` trên JVM chuẩn.

Nếu chạy bài với một build tool hoặc classpath khác, trước tiên kiểm tra không vô tình đưa plugin vào source lookup của parent, và không tạo sẵn class/plugin trong host output.

## 7. Reflection sau khi đã chọn đúng Class<?>: quyền truy cập vẫn phải kiểm tra

Có đối tượng `Class<?>` chưa đủ để mọi thao tác reflection thành công. Một số API phổ biến:

| API | Ý nghĩa | Lỗi thường gặp |
| --- | --- | --- |
| `clazz.getConstructor(parameterTypes...)` | Tìm public constructor phù hợp | `NoSuchMethodException` |
| `clazz.getDeclaredConstructor(parameterTypes...)` | Tìm constructor được khai báo trực tiếp, kể cả không public | Có thể cần mở quyền khi gọi |
| `clazz.getMethod("run", ...)` | Tìm public method phù hợp, kể cả inherited public methods | `NoSuchMethodException` |
| `clazz.getDeclaredMethod("run", ...)` | Tìm method được khai báo trực tiếp, kể cả private | Không tự cho quyền `invoke` |
| `constructor.newInstance(args...)` | Tạo instance (kích hoạt initialization nếu cần) | `InvocationTargetException`, `IllegalAccessException`, v.v. |
| `method.invoke(instance,args...)` | Gọi method qua reflection; static method dùng target `null` | Sai receiver/args, quyền truy cập, exception trong method |
| `clazz.cast(object)` | Dynamic type cast theo chính đối tượng `Class<?>` | `ClassCastException` nếu object thuộc class runtime khác |
| `clazz.isInstance(object)` | Test tương thích kiểu tại runtime | `false` với bản class trùng tên khác loader |

Trong ví dụ, `OnlyChild` có public no-arg constructor và public method nên không cần `setAccessible(true)`. Nếu class/method private hoặc package không mở, phải tuân các điều kiện Java access và JPMS.

### Modules: load được vẫn có thể không được reflect sâu

Ở Java 9+, class trong **named module** chịu readability, exports/opens và access rules riêng. `Class.forName` hoặc `ClassLoader.loadClass` có thể trả về `Class<?>`, nhưng code ở module khác vẫn có thể không được gọi member nếu package không exported. Với reflection vào private members, có thể cần `opens p to caller.module;`; nếu thiếu, `setAccessible(true)` có thể ném `InaccessibleObjectException`.

Không nên sửa bằng cách mở mọi package hoặc dùng `--add-opens` vô điều kiện. Ưu tiên một **SPI/interface chung ở loader/module được chia sẻ**, expose public API đúng nghĩa và chỉ mở package khi thực sự cần deep reflection.

### Vì sao không dùng Class.newInstance?

`Class.newInstance()` đã deprecated từ Java 9; API đó xử lý và biểu diễn exception kém rõ ràng. Nên dùng:

~~~java
Object instance = clazz.getDeclaredConstructor().newInstance();
~~~

Đây là đoạn minh họa, giả định class có constructor không tham số truy cập được và biến `clazz` đã được chọn đúng loader. Constructor có thể ném exception được phản ánh qua `InvocationTargetException`; xử lý nguyên nhân gốc thích hợp thay vì nuốt lỗi.

## 8. Class.forName(Module, String): chọn module, không chỉ loader

Từ Java 9 có overload:

~~~java
Class<?> type = Class.forName(module, "com.acme.plugin.Impl");
~~~

Trong đó `module` là đối tượng `java.lang.Module` cụ thể. Sự khác biệt theo tài liệu Java SE:

- Tìm class với **binary name ở đúng module được chỉ định**.
- **Không link hoặc chạy initializer** theo hợp đồng overload này.
- Nếu class không thuộc module đó hoặc không được tìm thấy, trả **`null`** thay vì `ClassNotFoundException`.
- Nếu cùng loader định nghĩa nhiều modules mà tên class thuộc module khác, overload vẫn có thể trả `null` vì yêu cầu là **đúng module**.
- Việc tìm được `Class<?>` không tự kiểm tra class có accessible với caller hay không. Khi truy cập member vẫn phải tuân module/access checks.
- Không hỗ trợ tìm array class bằng tên array descriptor như overload `forName(String,boolean,ClassLoader)`.

Đoạn trên chỉ minh họa, giả định có biến `module`. Khi dùng `ModuleLayer`, có thể lấy module từ layer đã resolve và dùng `Class.forName(module, binaryName)` để kiểm tra một class thuộc **module cụ thể**; nhưng nếu mục tiêu là service provider discovery, hãy dùng `ServiceLoader.load(layer, service)` thay cho tự duyệt class bằng chuỗi tên.

**Phân biệt rõ:** `Class.forName(Module,String)` không phải dạng rút gọn của `Class.forName(name, false, module.getClassLoader())`, vì nó còn ràng buộc tên class vào **đúng named module** và có hợp đồng riêng về link/error/null.

## 9. Class literals, arrays, nested classes và binary name

Tên chuỗi cho `loadClass` và `Class.forName` cần đúng cú pháp phù hợp:

| Kiểu Java | Binary name thông thường | Lưu ý |
| --- | --- | --- |
| `com.example.Widget` | `com.example.Widget` | Dấu chấm giữa package |
| Member class `com.example.Outer.Inner` | `com.example.Outer$Inner` | Dấu `$` trong binary name của member class |
| Array của reference type | `[Lcom.example.Widget;` | Dạng JVM array descriptor cho `Class.forName` overload theo loader |
| Array của `int` | `[I` | Dạng descriptor; class loader của mảng primitive là bootstrap/null |
| Primitive `int` | Không dùng `Class.forName("int")` để lấy primitive `Class` | Dùng `int.class` |
| Kiểu ở module | Phải có module phù hợp khi gọi overload module | Module overload không hỗ trợ array types |

Các cú pháp trên không phải đường dẫn file `com/example/Widget.class` và không phải tên URL JAR. Reflection nhận **binary name**, loader sẽ tự áp dụng chính sách tìm nguồn bytes.

Ngoài ra, type parameters của generics bị **type erasure** ở runtime; `List<String>` và `List<Integer>` không tự trở thành hai runtime classes riêng chỉ vì chuỗi kiểu generic khác. Nếu cần phân tích signatures/generic types, dùng các API `java.lang.reflect.Type` phù hợp thay vì coi chuỗi tên đơn giản là đầy đủ thông tin kiểu.

## 10. Loader được dùng để resolve dependency có thể khác loader chọn từ code host

Một hiểu lầm nguy hiểm: host dùng `child.loadClass("plugin.Entry")` để lấy entry, vậy tất cả dependency của entry sẽ được chọn bởi **host loader** hoặc **TCCL hiện hành**. Không đúng theo quy tắc JVM.

Ví dụ:

~~~text
HostClass (defined by AppLoader)
    |
    | reflection: child.loadClass("plugin.Entry")
    v
Entry (defined by ChildLoader)
    |
    | bytecode references plugin.Helper
    v
Helper được yêu cầu qua defining loader của Entry
(theo quy tắc symbolic reference resolution)
~~~

Khi JVM giải một symbolic reference trong runtime constant pool của class D, **defining loader của D** được dùng để load class tương ứng. Điều này giúp lý giải:

- Plugin có thể đọc dependency do loader con hoặc parent của nó nhìn thấy.
- Host class do parent định nghĩa **không tự tìm** dependency chỉ có ở child khi bytecode của host có symbolic reference đó.
- Chuyển TCCL không tự sửa mọi lỗi `NoClassDefFoundError` của bytecode đã định nghĩa.
- Reflection chọn đúng một `Class<?>` không bảo đảm mọi dependency của class đều hợp lệ trong môi trường runtime.

Với plugin thực tế, nên đặt **shared API ở parent**, implementation/dependencies riêng ở child và tạo API bridge. Nếu host cần một contract như `api.Plugin`, cả hai bên phải dùng cùng đối tượng `Class<api.Plugin>`, thay vì dùng reflection để trao đổi object không cùng loader identity.

## 11. Quy trình chọn loader theo tình huống

| Tình huống | Lựa chọn hợp lý |
| --- | --- |
| Class trên application classpath, host dùng class đó trực tiếp | `SomeClass.class` hoặc `Class.forName(name)` khi thật sự cần tên động |
| Class ở plugin URL riêng | `Class.forName(name, false, pluginLoader)`, sau đó reflection qua `Class<?>` trả về |
| Cần chủ động chạy static initializer của plugin | `Class.forName(name, true, pluginLoader)` nếu tên/loader được xác định rõ |
| Chỉ cần metadata mà chưa muốn initialize | `Class.forName(name, false, loader)` hoặc `loader.loadClass(name)` |
| Framework document rõ là dùng TCCL | Gán TCCL tạm thời và **khôi phục trong finally** |
| Cần provider của SPI | `ServiceLoader` với đúng ClassLoader hoặc ModuleLayer |
| Cần class của một named module cụ thể | `Class.forName(module, binaryName)`, kiểm tra kết quả có thể null |
| Cần nạp từ JAR/thư mục | `URLClassLoader`, truyền package root/JAR URL chính xác, parent phù hợp |
| Cần cùng interface giữa host/plugin | Đặt interface ở loader/module cha được chia sẻ, để child ủy quyền |
| Cần version plugin riêng dù parent có cùng class name | Class loading policy child-first có chọn lọc, có test identity và dependency |
| Muốn chạy code không tin cậy | Không coi class loader là security sandbox; cần isolation/security model riêng |

### Checklist debug từng bước

1. Binary name chính xác không? Với nested member class, đã dùng dấu `$` khi cần chưa?
2. Lời gọi là `Class.forName(name)`, overload có loader, hay `loader.loadClass(name)`?
3. **Caller** của overload một tham số được định nghĩa bởi loader nào?
4. Class có nằm trong URL/plugin root hoặc module path đã resolve đúng chưa?
5. Parent-first có đang trả phiên bản của parent không? In `clazz.getClassLoader()` để biết defining loader.
6. TCCL đang là gì? API sử dụng có **thực sự chủ động đọc TCCL** không?
7. Có đang kỳ vọng `loadClass` chạy initializer, hay nhầm `resolve=true` với `initialize=true` không?
8. Hai đối tượng `Class<?>` có cùng tên nhưng khác loader không? Dùng `a == b`, `isInstance` hoặc `cast` để xác định.
9. Exception thuộc nhóm nào: `ClassNotFoundException`, `NoClassDefFoundError`, `ClassCastException`, `IllegalAccessException`, `InaccessibleObjectException` hay `ExceptionInInitializerError`?
10. Nếu dùng JPMS, package có `exports`/`opens` phù hợp với module caller không?
11. Có sửa TCCL nhưng quên khôi phục hoặc đóng loader quá sớm không?
12. Host và plugin có chia sẻ **cùng defining loader cho SPI interface** không?

### Những lỗi phổ biến

| Biểu hiện | Nguyên nhân khả dĩ | Cách giải quyết |
| --- | --- | --- |
| `Class.forName(name)` báo không tìm thấy nhưng `pluginLoader.loadClass(name)` tìm được | Caller loader không nhìn thấy nguồn plugin | Chọn plugin loader tường minh |
| Đổi TCCL vẫn không sửa `Class.forName(name)` | Overload một tham số không lấy TCCL | Gọi overload có loader |
| Cả hai loader nhận class cùng tên nhưng cast lỗi | Khác defining loaders tạo type identity khác | Chia sẻ API contract qua parent |
| Khối `static` chạy sớm ngoài mong muốn | Dùng `Class.forName(name)` hoặc `initialize=true` | Chọn `initialize=false` để chỉ tìm metadata |
| Class được load nhưng gọi method báo `NoClassDefFoundError` | Dependency không thấy qua defining loader, hoặc lớp trước đó init thất bại | Xem cause/stack trace và loader của class chứa reference |
| `getDeclaredMethod` tìm ra private method nhưng `invoke` lỗi | Java access/module opens chưa cho phép | Dùng public API hoặc mở package có chọn lọc |
| `Class.forName(module,name)` trả null | Tên không thuộc đúng module, dù loader có thể định nghĩa module khác | Xác minh module identity và binary name |
| Đóng URLClassLoader rồi plugin ngừng tìm class mới | `close()` giải phóng nguồn tìm mới | Quản lý loader lifecycle và streams phù hợp |
| Log `clazz.getClassLoader() == null` | Class do bootstrap loader định nghĩa | Đây không phải trạng thái “chưa load” |

## 12. Câu hỏi tự kiểm tra

**Câu 1.** Plugin `plugin.OnlyChild` chỉ nằm trong `out/plugin`. Tại sao `Class.forName("plugin.OnlyChild")` từ host thất bại nhưng overload truyền `pluginLoader` lại thành công?

**Đáp:** Overload một tham số dùng defining loader của class caller (host); overload ba tham số dùng loader được chỉ định. Parent host không biết nguồn plugin, trong khi child có URL tương ứng.

**Câu 2.** Gán TCCL bằng pluginLoader, gọi `Class.forName(name)` có tự chọn TCCL không?

**Đáp:** Không. Nếu muốn chọn TCCL phải **gọi API dùng nó** hoặc truyền `Thread.currentThread().getContextClassLoader()` cho overload ba tham số.

**Câu 3.** Hai URLClassLoader khác nhau đều đọc cùng bytecode `plugin.OnlyChild`, tại sao `clazzA == clazzB` là false?

**Đáp:** JVM phân biệt class runtime bằng binary name **và defining loader**. Cùng tên cùng bytes chưa đủ làm cùng Class object.

**Câu 4.** `loadClass(name)` đã thành công nhưng `static { ... }` không in gì; có phải lỗi loader?

**Đáp:** Không. Lookup không tự yêu cầu initialization. `Class.forName(name,true,loader)` hoặc active use phù hợp mới khởi tạo class nếu cần.

**Câu 5.** `Class.forName(module,name)` khác overload ba tham số ở điểm nào?

**Đáp:** Nó yêu cầu class trong **module cụ thể**, không link/initialize theo hợp đồng, và trả null khi không tìm đúng class trong module, thay vì checked `ClassNotFoundException`.

**Câu 6.** Vì sao chỉ chỉnh TCCL của thread host chưa chắc sửa lỗi dependency trong class plugin?

**Đáp:** JVM giải symbolic references theo defining loader của class chứa reference; TCCL chỉ có tác dụng khi API/framework chủ động dùng nó.

## Tài liệu tham khảo chính thức

- [Class.forName và Class.getClassLoader, Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html): overload theo name, loader, module; initialization; kiểm tra defining loader.
- [ClassLoader, Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): parent-first delegation, loadClass, findClass và loader identity.
- [Thread.getContextClassLoader, Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#getContextClassLoader()): quản lý context class loader của thread.
- [java.lang.reflect.Method, Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Method.html): gọi method qua reflection và quyền truy cập.
- [JVMS Java SE 21 §5.3 và §5.4.3.1](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html): defining loader, class identity và symbolic reference resolution.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-class-definition-002">Dynamic Proxy và khả năng nhìn thấy các interface</a>

<details>
<summary>Xem chi tiết</summary>

# Dynamic Proxy và khả năng nhìn thấy các interface trong Java ClassLoader

## 1. Bài toán: vì sao một interface tồn tại nhưng không thể tạo Dynamic Proxy?

Java Dynamic Proxy là cơ chế tạo **một class triển khai interface ngay khi chạy**, không cần biên dịch trước một implementation cố định. API cốt lõi là `java.lang.reflect.Proxy.newProxyInstance`:

~~~java
Object proxy = Proxy.newProxyInstance(
        chosenLoader,
        new Class<?>[] { SomeService.class },
        invocationHandler
);
~~~

Trong chương trình đơn giản, chọn `SomeService.class.getClassLoader()` hoặc một loader tương thích thường hoạt động. Nhưng trong plugin system, application server, JPMS layer hoặc hot reload, một đối tượng `Class<?>` của interface có thể **không nhìn thấy được theo tên** từ ClassLoader được chọn để định nghĩa proxy. Khi đó Java có thể ném **`IllegalArgumentException`** trong chính lời gọi tạo proxy.

**Khái niệm then chốt:** Không đủ để loader “tìm thấy một interface cùng tên”. Nó phải tìm được **đúng cùng đối tượng `Class<?>` của interface**.

Java 17 định nghĩa điều kiện cần kiểm tra đối với từng interface `i` và loader `cl`:

~~~java
Class.forName(i.getName(), false, cl) == i
~~~

Dấu `==` là **đồng nhất Class runtime**, không phải so tên chuỗi. Nếu biểu thức trả false, `i` **không visible by name** qua loader được yêu cầu cho proxy. Nếu `Class.forName` ném `ClassNotFoundException`, interface còn chưa tìm được bằng loader ấy.

Trong nhiều trường hợp lỗi phát sinh vì:

- Chọn **bootstrap/platform loader** cho một interface chỉ có ở application loader.
- Chọn **sibling loader** không thể delegate sang nơi đã định nghĩa interface.
- Plugin loader child-first tự định nghĩa một bản **interface cùng binary name** với bản host.
- Các **kiểu nằm trong public method signatures** của interface không thấy được qua loader proxy.
- Interface **non-public** nằm khác package/module, khác defining loader hoặc không thể truy cập theo quy tắc của `Proxy`.
- Interface **sealed** (Java 17) hoặc **hidden** không hợp lệ để Dynamic Proxy implement.
- Một nhóm interface có các phương thức trùng signature nhưng return types không tương thích.

Bài này giải thích điều kiện Java 17, cách chọn loader, thí nghiệm “không nhìn thấy / nhìn thấy bản sao / nhìn thấy đúng” có thể chạy được, các giới hạn với JPMS, xử lý `InvocationHandler`, lỗi phổ biến và cách thiết kế plugin boundary.

## 2. Dynamic Proxy hoạt động theo sơ đồ nào?

Một object proxy có hai phần chính:

1. **Proxy class runtime** do JDK sinh ra, implements các interface được chỉ định, extends `java.lang.reflect.Proxy`.
2. **InvocationHandler** chứa logic xử lý những lời gọi qua interface methods.

~~~text
Consumer code
   |
   v
SomeService service = (SomeService) proxy;
   |
   v
service.request("x")
   |
   v
Generated proxy class
   |
   v
InvocationHandler.invoke(proxy, Method request, ["x"])
   |
   v
Handler logic -> return value / throw exception
   |
   v
Consumer receives result
~~~

Với proxy được tạo đúng, `proxy instanceof SomeService` là true; cast `(SomeService) proxy` thành công. Khi gọi method qua proxy, JDK truyền tới handler ba thông tin:

- `proxy`: chính instance proxy.
- `method`: một `java.lang.reflect.Method` mô tả interface method được gọi; có thể thuộc inherited superinterface.
- `args`: mảng tham số; nếu method không có tham số thì có thể là **`null`**, không được mặc định là mảng rỗng.

Handler trả một `Object`. Proxy phải chuyển giá trị trả về phù hợp với kiểu khai báo: primitive yêu cầu wrapper đúng để unbox, kiểu reference phải assignable; sai kiểu có thể ném `ClassCastException` hoặc `NullPointerException`.

Dynamic Proxy **không tự thực thi body của một concrete implementation**. Nếu handler muốn forward tới target object, handler phải chủ động gọi target; nếu muốn tính giá trị, nó tự tính. Với **default methods** của interface, `InvocationHandler.invokeDefault` (Java 16+) là lựa chọn khi muốn gọi triển khai default thực sự.

## 3. Interface visibility là identity, không phải tên

Theo đặc tả JVM, class/interface được nhận diện bằng **(binary name, defining ClassLoader)**. Hai loaders có thể định nghĩa hai interface tên `api.Greeter` riêng:

~~~text
Host application loader:
    api.Greeter  -> Class H

Plugin child-first loader:
    api.Greeter  -> Class P

H.getName().equals(P.getName()) == true
H == P                              == false
~~~

Nếu host truyền `H` cho `Proxy.newProxyInstance(pluginLoader, new Class<?>[]{H}, h)`, nhưng `pluginLoader.loadClass("api.Greeter")` trả `P`, proxy **không được tạo**. Loader ấy nhìn thấy **P**, không nhìn thấy **H** bằng binary name.

Hai biến thể cần phân biệt:

| Hiện tượng | Kết quả khi hỏi interface bằng proxy loader |
| --- | --- |
| Loader không thể tìm interface | `ClassNotFoundException` khi thử `Class.forName` |
| Loader tìm interface cùng tên nhưng bản riêng | `Class.forName(..., loader) != expectedInterface` |
| Loader delegate đúng parent và nhận cùng Class | `Class.forName(..., loader) == expectedInterface` |

Trường hợp thứ hai rất nguy hiểm khi chỉ xem log: tên interface hoàn toàn đúng nhưng identity sai. Đây cũng là nguồn lỗi `ClassCastException` “`api.Greeter cannot be cast to api.Greeter`” và provider **not a subtype** trong các kiến trúc plugin khác.

**Điều kiện proxy không đòi tất cả interface phải được định nghĩa bởi chính loader truyền vào.** Một child loader **có thể delegate** các shared interface cho parent và vẫn tạo proxy hợp lệ, miễn kết quả lookup đúng chính Class runtime.

## 4. Chữ ký API, ý nghĩa từng tham số

Chữ ký Java 17:

~~~java
public static Object newProxyInstance(
        ClassLoader loader,
        Class<?>[] interfaces,
        InvocationHandler h
)
~~~

- `loader`: loader được JDK dùng để **định nghĩa proxy class** và kiểm tra visibility.
- `interfaces`: tập các **Class objects đại diện interface** mà proxy class sẽ implement, theo thứ tự chỉ định.
- `h`: InvocationHandler được gọi khi một interface method được thực thi.

Nên dùng:

~~~java
Greeter proxy = (Greeter) Proxy.newProxyInstance(
        Greeter.class.getClassLoader(),
        new Class<?>[] { Greeter.class },
        (p, method, arguments) -> "Hello"
);
~~~

Đây là **đoạn minh họa** giả định `Greeter` có đúng method signature tương ứng, không phải một file runnable độc lập. Để chạy ổn định và có câu trả lời chính xác, xem chương trình đầy đủ ở mục 7.

### 4.1. Có thể truyền null loader không?

`loader == null` nghĩa sử dụng bootstrap class loader trong những hoàn cảnh API cho phép. Nó **không** phải ký hiệu cho “auto choose suitable loader”. Với interface của ứng dụng định nghĩa bởi application loader, bootstrap loader thông thường **không thể nhìn thấy interface ấy**, nên truyền null không giải quyết được vấn đề.

### 4.2. Interface array cần Class<?> thật

`interfaces` không nhận các tên chuỗi; nó nhận đúng `Class<?>` của các interface. Nếu cần load theo tên từ plugin, phải kiểm tra rõ provenance và identity của từng Class. Đừng biến danh sách tên lấy từ input ngoài thành các interfaces tùy ý rồi mong proxy là một security boundary.

### 4.3. Vì sao handler không quyết định visibility?

`InvocationHandler` được truyền **sau** phần lựa chọn loader và interface Class objects. Handler có thể là lambda của host, nhưng điều đó không cho phép một proxy Class thiếu access hay không nhìn thấy interface tự nhiên được sinh ra. Visibility check diễn ra trong quá trình tạo proxy, trước khi handler được gọi.

## 5. Những ràng buộc trong Proxy.newProxyInstance trên Java 17

Để proxy class được tạo hợp lệ, Java 17 yêu cầu nhiều điều kiện, không chỉ visibility:

| Điều kiện | Lý do |
| --- | --- |
| Mỗi phần tử `interfaces` là một interface | Proxy tạo implementation của interfaces, không kế thừa arbitrary class |
| Interface không phải `hidden` | Hidden class không hỗ trợ lookup bằng tên như yêu cầu |
| Interface không phải `sealed` | Dynamic proxy không phải một permitted implementation tĩnh trong sealed hierarchy |
| Không lặp cùng một `Class<?>` trong mảng | Danh sách interfaces cần không duplicate |
| Mọi interface visible by name qua loader | `Class.forName(i.getName(), false, loader) == i` |
| Mọi kiểu trong **public method signatures** của interfaces cũng visible by name | Proxy class phải khai báo hợp lệ method descriptors |
| Mọi **non-public interfaces** phải cùng package và module, được loader chỉ định định nghĩa | Đảm bảo một proxy class có thể truy cập toàn bộ interfaces |
| Trùng method signature phải có return types tương thích | Proxy chỉ sinh một implementation cho một signature |
| Không vượt VM class/interface limits | Class file/VM có giới hạn cấu trúc |

Vi phạm các restrictions này thường khiến `newProxyInstance` ném **`IllegalArgumentException`**. Null array, phần tử null hoặc handler null có thể khiến `NullPointerException`; một số policy runtime cũ có thể báo `SecurityException`, phụ thuộc JVM và security manager configuration.

**Trường hợp visibility cần nhớ nguyên văn ý tưởng:**

~~~java
boolean visible =
        Class.forName(iface.getName(), false, chosenLoader) == iface;
~~~

Trong mã thực tế, câu lệnh cần `throws ClassNotFoundException` hoặc `try/catch`. Nếu `visible` false dù tên trùng, đổi loader hoặc sửa cách nạp shared API; không “sửa” bằng cách so String.

## 6. Chọn ClassLoader nào khi tạo proxy?

### 6.1. Một interface public thuộc application classpath

Cách phổ biến:

~~~java
ClassLoader loader = ServiceApi.class.getClassLoader();
Object proxy = Proxy.newProxyInstance(
        loader, new Class<?>[] { ServiceApi.class }, handler);
~~~

**Mã minh họa**, giả sử `ServiceApi` và `handler` hợp lệ. Chọn defining loader của API có lợi vì loader ấy phải nhìn thấy chính interface, nhưng vẫn cần xét **signature types** và những interface bổ sung khi tạo proxy đa interface.

### 6.2. Nhiều interfaces từ nhiều loaders

Nếu một proxy phải implement cả `ApiA.class` và `ApiB.class`, loader cần nhìn thấy **cả hai Class runtime** bằng tên, bao gồm những types dùng trong public method signatures. Một loader child có parent chung và biết về cả interfaces có thể phù hợp; không có công thức “lấy loader của interface đầu tiên” luôn đúng.

Sơ đồ:

~~~text
         Common parent loader
        /                    \
  Interface A               Interface B
        \                    /
         \                  /
         chosen proxy loader
             sees A and B?
                 |
            yes -> proxy
            no  -> IllegalArgumentException
~~~

Nếu A và B nằm trong **sibling loaders** không có delegation phù hợp, bạn cần kiến trúc loader chung hoặc adapter/boundary khác. Không thể ép hai Class trùng tên/khác loader trở thành một.

### 6.3. API host và implementation plugin

Thiết kế thường dùng:

~~~text
      Host/CommonLoader
         shared.Api
         shared.Dto
             ^
             | parent delegation
             |
         PluginLoader
       plugin.Implementation
       plugin.PrivateDependency
~~~

Nếu host cần cast proxy sang `shared.Api`, hãy bảo đảm `shared.Api` là **một Class runtime chung**, thường do common parent định nghĩa. Plugin loader có thể tìm implementation/private dependencies riêng; Dynamic Proxy không phải lý do để copy service interface vào mỗi plugin.

### 6.4. Thread Context ClassLoader

`Thread.currentThread().getContextClassLoader()` (TCCL) hữu ích cho discovery frameworks, nhưng **không luôn là lựa chọn đúng** cho `Proxy.newProxyInstance`. TCCL có thể là plugin loader đang child-first cho bản API khác, hoặc một loader không nhìn thấy API của host.

Đừng mặc định:

~~~java
Proxy.newProxyInstance(
        Thread.currentThread().getContextClassLoader(),
        new Class<?>[] { HostApi.class },
        handler
);
~~~

Nếu TCCL không nhìn thấy **chính HostApi Class**, lời gọi thất bại. Dùng explicit visibility check và chọn loader dựa trên ownership của contract.

## 7. Thí nghiệm Java 17+ hoàn chỉnh: không thấy, thấy bản sao, thấy đúng

Thí nghiệm sử dụng:

- Một interface public `api.Greeter` trong **host classpath**.
- Một bản bytecode của **cùng interface** trong thư mục riêng `shadow-classes`.
- Một `URLClassLoader` cô lập không nhìn thấy host API.
- Một custom `URLClassLoader` child-first cho chính tên `api.Greeter` nhằm tạo **bản sao runtime**.
- Một proxy được tạo từ loader của host API, dùng chung Class đúng.

### 7.1. Cây thư mục

~~~text
proxy-visibility-demo/
├── src-api/
│   └── api/
│       └── Greeter.java
└── src-host/
    └── ProxyVisibilityDemo.java
~~~

Sau compile:

~~~text
host-classes/
├── ProxyVisibilityDemo.class
├── ProxyVisibilityDemo$ShadowLoader.class
└── api/
    └── Greeter.class

shadow-classes/
└── api/
    └── Greeter.class
~~~

**Không đưa `shadow-classes` vào `java -cp` của host**, để parent application class loader chỉ biết bản host API. `ShadowLoader` nhận đường dẫn đó như một `URLClassLoader` root riêng.

### 7.2. File src-api/api/Greeter.java

~~~java
package api;

public interface Greeter {
    String greet(String name);
}
~~~

Interface public, không sealed, chỉ nhắc tới Java core `String` trong method signature. Vì vậy ví dụ tập trung vào visibility của **chính interface**, không bị ảnh hưởng bởi một DTO khác khó tìm.

### 7.3. File src-host/ProxyVisibilityDemo.java

~~~java
import api.Greeter;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

public class ProxyVisibilityDemo {
    static final class ShadowLoader extends URLClassLoader {
        ShadowLoader(Path classes) throws Exception {
            super(
                    new URL[] { classes.toAbsolutePath().toUri().toURL() },
                    ProxyVisibilityDemo.class.getClassLoader()
            );
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!name.equals("api.Greeter")) {
                return super.loadClass(name, resolve);
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> found = findLoadedClass(name);
                if (found == null) {
                    found = findClass(name);
                }
                if (resolve) {
                    resolveClass(found);
                }
                return found;
            }
        }
    }

    static boolean visible(ClassLoader loader, Class<?> type) {
        try {
            return Class.forName(type.getName(), false, loader) == type;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    static InvocationHandler handler() {
        return (proxy, method, args) -> {
            if (method.getName().equals("greet")) {
                return "Hello, " + args[0];
            }
            throw new UnsupportedOperationException(method.getName());
        };
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: ProxyVisibilityDemo <shadow-classes-dir>");
        }

        ClassLoader hostLoader = Greeter.class.getClassLoader();

        try (URLClassLoader isolated = new URLClassLoader(
                        new URL[0],
                        ClassLoader.getPlatformClassLoader());
             ShadowLoader shadow = new ShadowLoader(Path.of(args[0]))) {

            System.out.println("isolated sees host interface: "
                    + visible(isolated, Greeter.class));

            try {
                Proxy.newProxyInstance(
                        isolated,
                        new Class<?>[] { Greeter.class },
                        handler());
                System.out.println("isolated rejected: false");
            } catch (IllegalArgumentException expected) {
                System.out.println("isolated rejected: true");
            }

            Class<?> foreign = shadow.loadClass("api.Greeter");
            System.out.println("shadow same name: "
                    + foreign.getName().equals(Greeter.class.getName()));
            System.out.println("shadow same Class: "
                    + (foreign == Greeter.class));
            System.out.println("shadow sees host interface: "
                    + visible(shadow, Greeter.class));

            try {
                Proxy.newProxyInstance(
                        shadow,
                        new Class<?>[] { Greeter.class },
                        handler());
                System.out.println("shadow host proxy rejected: false");
            } catch (IllegalArgumentException expected) {
                System.out.println("shadow host proxy rejected: true");
            }

            Object foreignProxy = Proxy.newProxyInstance(
                    shadow,
                    new Class<?>[] { foreign },
                    handler());

            System.out.println("foreign proxy is host Greeter: "
                    + (foreignProxy instanceof Greeter));
            System.out.println("foreign proxy call: "
                    + foreign.getMethod("greet", String.class)
                             .invoke(foreignProxy, "Ada"));

            Greeter good = (Greeter) Proxy.newProxyInstance(
                    hostLoader,
                    new Class<?>[] { Greeter.class },
                    handler());

            System.out.println("shared proxy is host Greeter: "
                    + (good instanceof Greeter));
            System.out.println("shared proxy call: "
                    + good.greet("Ada"));
        }
    }
}
~~~

### 7.4. Biên dịch và chạy

Đứng ở thư mục `proxy-visibility-demo` với JDK 17+:

~~~bash
javac --release 17 -d host-classes src-api/api/Greeter.java src-host/ProxyVisibilityDemo.java
javac --release 17 -d shadow-classes src-api/api/Greeter.java
java -cp host-classes ProxyVisibilityDemo shadow-classes
~~~

Ba lệnh này có cùng cú pháp cơ bản trên PowerShell, CMD, bash, zsh vì không cần separator liệt kê nhiều classpath entries.

**Stdout dự kiến chính xác** với mã và cấu trúc thư mục trên:

~~~text
isolated sees host interface: false
isolated rejected: true
shadow same name: true
shadow same Class: false
shadow sees host interface: false
shadow host proxy rejected: true
foreign proxy is host Greeter: false
foreign proxy call: Hello, Ada
shared proxy is host Greeter: true
shared proxy call: Hello, Ada
~~~

### 7.5. Giải thích từng trường hợp

**Loader không thấy interface:**

- `isolated` có parent là platform loader, **không phải application loader** chứa `api.Greeter`.
- URL list rỗng, nên `Class.forName("api.Greeter", false, isolated)` không tìm được API host.
- Lời gọi `newProxyInstance(isolated, [Greeter.class], handler)` bị từ chối bằng `IllegalArgumentException`.

**Loader thấy bản riêng cùng tên:**

- `shadow` kế thừa `URLClassLoader` nhưng override `loadClass` **chỉ cho `api.Greeter`** thành child-first, lấy bytes từ `shadow-classes`.
- Class do shadow tự define khác `Greeter.class` của host, dù cả hai có `getName() == "api.Greeter"`.
- Truyền **host Greeter.class** với `shadow` thất bại visibility condition vì shadow sẽ tìm thấy Class bản sao.
- Truyền **foreign Class** cho shadow lại hợp lệ: shadow nhìn thấy đúng Class của nó, proxy được tạo.
- Tuy nhiên foreign proxy implements foreign `api.Greeter`, không implements host `api.Greeter`. Do đó `instanceof Greeter` false. Reflection theo foreign interface vẫn gọi được `greet("Ada")`.

**Chọn shared/host loader đúng:**

- `hostLoader` nhìn thấy đúng `Greeter.class`.
- Proxy được định nghĩa để implement interface Class của host, cast thành `Greeter` hợp lệ và gọi method trả chuỗi dự kiến.

**Kết luận thực nghiệm:** Tạo được một proxy trong loader plugin chưa đồng nghĩa host cast được nó sang một interface cùng tên. Cần đồng nhất **contract Class identity** ở ranh giới trao đổi.

## 8. Chọn đúng interface Class khi plugin có API trùng

Có hai quyết định khác nhau:

1. **Tôi muốn proxy implement interface của ai?** Nếu host sẽ dùng proxy qua `HostApi`, interface array **phải chứa `HostApi.class` của host**.
2. **Loader nào có thể nhìn thấy chính Class ấy?** Chọn một loader mà `Class.forName(HostApi.class.getName(), false, loader) == HostApi.class`, đồng thời nhìn thấy các kiểu signature và các interfaces khác.

Nếu chỉ đổi interface array sang bản `PluginApi` để làm `newProxyInstance` hết lỗi, bạn có thể tạo một proxy **chạy được trong plugin** nhưng **không dùng được qua host contract**. Lựa chọn sửa đúng thường là đưa API types vào **parent/common loader**, bắt plugin delegate `shared.api.*` về đó.

Một adapter có thể chuyển data giữa hai contract không tương thích khi không thể thống nhất loader, nhưng không nên ép cast Class runtime sai.

## 9. Kiểm tra kiểu trong public method signatures

Java 17 còn yêu cầu **các loại dữ liệu tham chiếu xuất hiện trong mọi public method signatures** của proxy interfaces và inherited superinterfaces nhìn thấy được theo tên qua loader đã chọn.

Ví dụ interface:

~~~java
package api;

public interface Processor {
    api.dto.Result process(api.dto.Request input);
}
~~~

Đây là **mã minh họa**, giả sử `api.dto.Request` và `api.dto.Result` đã có. Dù `Processor.class` visible qua loader, proxy vẫn có thể không được định nghĩa nếu loader không nhìn thấy chính các Class runtime tương ứng với `Request` và `Result`.

Điều này có thể xảy ra khi:

- Host API interface nằm trong parent loader nhưng DTO của cùng contract vô tình được loader con định nghĩa riêng.
- Interface public method dùng exception/return/parameter types nằm trong module/plugin loader khác không được chọn loader proxy nhìn thấy phù hợp.
- Một inherited superinterface đưa vào type tham chiếu mà người viết chỉ xem methods khai báo trực tiếp đã bỏ sót.

**Đừng chỉ kiểm tra visibility của interface bề mặt.** Cần thiết kế toàn bộ **API surface** thống nhất: parameter types, return types, interfaces liên quan và public method signatures. Các types private implementation không lộ ra ở signature có thể tiếp tục cô lập.

Một checker cơ bản minh họa:

~~~java
static boolean visibleByName(ClassLoader loader, Class<?> type) {
    try {
        return Class.forName(type.getName(), false, loader) == type;
    } catch (ClassNotFoundException e) {
        return false;
    }
}
~~~

Đây là **đoạn minh họa**, không phải test tổng quát đủ mọi kiểu vì array/primitive types và method signature traversal có những chi tiết riêng. Khi làm framework, cần xử lý `void`, primitives, array component types và all inherited methods đúng quy tắc.

## 10. Non-public interfaces: cùng package/module và defining loader

Một interface chỉ có package-private access, ví dụ:

~~~java
package internal;

interface HiddenContract {
    String value();
}
~~~

không giống interface `public`. Dynamic Proxy vẫn có thể implement non-public interfaces trong những điều kiện thích hợp, nhưng Java 17 yêu cầu **tất cả non-public proxy interfaces**:

- Nằm **cùng package và cùng module**.
- Được **loader chỉ định cho proxy định nghĩa**.
- Module chứa non-public interfaces có thể truy cập toàn bộ interface types được yêu cầu.

Một proxy class có non-public interface thường không public và được đặt trong package/module của non-public interfaces. Vì vậy chỉ hỏi “loader nhìn thấy interface” là **chưa đủ** để chứng minh tổ hợp interfaces hợp lệ.

Đây là lý do không thể tùy tiện trộn:

~~~text
package a; interface A { ... }  // non-public
package b; interface B { ... }  // non-public
~~~

thành một proxy triển khai cả hai nếu chúng ở các package/module khác nhau. Lỗi dự kiến là `IllegalArgumentException` trong lời gọi tạo proxy.

Nếu plugin API cần chia sẻ cho code ngoài package/module, cân nhắc một interface **public, nằm trong package được export phù hợp**, thay vì dùng package-private interface làm contract xuyên loader. Không cố khắc phục bằng `setAccessible(true)`; JPMS và Dynamic Proxy có ràng buộc structural riêng.

## 11. JPMS: proxy class được đặt trong module nào?

Từ Java 9+, Dynamic Proxy còn phải tương thích Java Platform Module System (**JPMS**). Tên/package/module của proxy class **không được giả định cố định**. Java 17 chọn vị trí sinh proxy sao cho mức độ truy cập tương thích với các proxy interfaces.

Các nhánh phổ biến trong Javadoc:

| Proxy interfaces | Proxy class ở đâu? |
| --- | --- |
| Tất cả public, thuộc các package được export/open phù hợp | Public proxy trong một package được export vô điều kiện nhưng không open; tên package/module không được bảo đảm |
| Có non-public interfaces | Proxy non-public trong package/module của các non-public interfaces, với điều kiện cùng package/module và loader |
| Có public interface thuộc package không export và không open rộng rãi | Proxy có thể là public class trong **dynamic module**, ở package không export và không open |
| Hỗn hợp accessibilities | Mức truy cập thấp nhất của interfaces chi phối khả năng truy cập proxy class |

**Dynamic module** ở đây là một named module được tạo bởi cơ chế proxy tại runtime, không phải tên module bạn tự đặt trong `module-info.java`. Proxy trong dynamic module có thể bị encapsulation che khỏi constructor reflection ở ngoài module.

Ví dụ:

~~~java
Object p = Proxy.newProxyInstance(loader, interfaces, handler);
~~~

được khuyến nghị thay cho pattern tạo proxy class rồi gọi constructor reflection bằng tay. API `Proxy.getProxyClass` đã **deprecated** trong Java 17, vì proxy classes thuộc named/dynamic modules có thể không cho caller ngoài module gọi constructor qua reflection. `Proxy.newProxyInstance` xử lý các điều kiện hợp đồng cần thiết.

### Module exports/opens và interface visibility khác nhau

- **Visible by name** hỏi loader có resolve ra **đúng Class object** không.
- **Readability/exports/opens** hỏi code/module nào có quyền truy cập types/packages ở runtime.
- Hai điều kiện có liên hệ nhưng **không tương đương**. Một loader có thể thấy Class bằng tên mà một module/caller vẫn chưa được quyền truy cập trực tiếp nó.
- Không nên dùng `--add-opens`/`--add-exports` ngẫu nhiên để chữa lỗi duplication do class loader. Nếu Class identity khác nhau, đổi access flags không làm hai Class hợp nhất.

## 12. Dynamic Proxy không hỗ trợ sealed/hidden interface theo Java 17

Java 17 có sealed types, ví dụ:

~~~java
public sealed interface Operation permits FixedOperation {
    int run();
}

final class FixedOperation implements Operation {
    @Override
    public int run() {
        return 1;
    }
}
~~~

**Mã minh họa**, không phải một file runnable có main. `Operation` là sealed interface; một Dynamic Proxy class không nằm trong permits clause được compiler khai báo nên Java 17 `Proxy.newProxyInstance` **từ chối** loại interface này theo restriction **non-sealed interface**. Không phải lỗi do loader visibility.

Tương tự, một **hidden interface** không đáp ứng yêu cầu interface visible by name. Không cố dùng `Lookup.defineHiddenClass` rồi bắt proxy generator tự implements hidden interface; API Java 17 yêu cầu non-hidden interfaces.

Một class bất kỳ cũng không được cung cấp vào `interfaces` array; mỗi phần tử phải là **interface type**. Với interface marker thông thường hoặc interface có default methods, proxy có thể tạo được nếu các điều kiện khác được đáp ứng.

## 13. Nhiều interfaces có methods trùng nhau

Dynamic Proxy có thể implements hơn một interface:

~~~java
interface A {
    Number get();
}

interface B {
    Integer get();
}
~~~

**Đoạn minh họa**. Hai methods có cùng name và parameters, nhưng return types covariant: `Integer` assignable vào `Number`. Java có thể tạo proxy nếu điều kiện loader/access phù hợp.

Ngược lại:

~~~java
interface Left {
    String value();
}

interface Right {
    Integer value();
}
~~~

**Đoạn minh họa**. Hai return types không tương thích, nên một proxy không thể triển khai cả hai cùng method signature và đáp ứng rules; `newProxyInstance` có thể ném `IllegalArgumentException`.

Khi cả A và B cùng khai báo method name/parameter signature:

- **Thứ tự interfaces** trong array có ý nghĩa. Method object đưa đến handler có thể thuộc **interface xuất hiện trước** chứa method ấy, dù caller gọi qua reference kiểu interface khác.
- Nếu method là `equals`, `hashCode` hoặc `toString` của `Object`, handler nhận Method có declaring class là `java.lang.Object`.
- Checked exceptions ném từ handler phải tương thích với **throws clauses** của các method signatures trùng nhau. Nếu không, proxy có thể bọc trong `UndeclaredThrowableException`.

Đừng dùng `method.getDeclaringClass()` như phép đo duy nhất để biết caller đã gọi qua interface reference nào; với methods trùng nhau, Javadoc nêu rõ nó không đủ.

## 14. Handler: default methods, Object methods và exceptions

### 14.1. Object methods cũng đi qua handler

Proxy chuyển các lời gọi `equals`, `hashCode`, `toString` public từ `Object` vào InvocationHandler. Nếu handler chỉ xử lý một method nghiệp vụ rồi ném `UnsupportedOperationException` cho tất cả trường hợp khác, code framework/logging gọi `proxy.toString()` có thể thất bại.

Một handler đủ rõ để minh họa:

~~~java
InvocationHandler h = (proxy, method, args) -> {
    switch (method.getName()) {
        case "toString":
            return "GreeterProxy";
        case "hashCode":
            return System.identityHashCode(proxy);
        case "equals":
            return proxy == args[0];
        case "greet":
            return "Hello, " + args[0];
        default:
            throw new UnsupportedOperationException(method.toString());
    }
};
~~~

**Đoạn code minh họa** cần biến và interface phù hợp. Trong handler, đừng gọi `proxy.toString()` để triển khai `toString`, vì nó sẽ quay lại chính handler gây recursion. Với `equals`, cần quyết định semantics rõ thay vì mặc nhiên delegate tới một target không cùng identity.

### 14.2. Gọi default method

Java 17 hỗ trợ:

~~~java
if (method.isDefault()) {
    return InvocationHandler.invokeDefault(
            proxy, method, args == null ? new Object[0] : args);
}
~~~

Đây là **đoạn minh họa** dùng bên trong InvocationHandler. `InvocationHandler.invokeDefault` (Java 16+) thực hiện default implementation theo rules của proxy interface; chỉ dùng khi `method` thực sự là default method được phép. Nếu handler tự trả kết quả thì default method không tự chạy.

### 14.3. Checked exception không khai báo

Nếu handler ném checked exception không được cho phép bởi interface method's `throws` clause, lời gọi qua proxy có thể ném `UndeclaredThrowableException`. Nó là `RuntimeException` wrapper, không phải bằng chứng rằng proxy loader nhìn sai interface.

Lỗi theo các tầng:

| Giai đoạn | Lỗi điển hình |
| --- | --- |
| Tạo proxy; interface không nhìn thấy/không hợp lệ | `IllegalArgumentException` |
| Tạo proxy; interface array, phần tử, handler null | `NullPointerException` |
| Cast proxy vào interface Class khác runtime | `ClassCastException` |
| Handler trả null cho primitive | `NullPointerException` khi unbox |
| Handler trả giá trị không tương thích declared return | `ClassCastException` |
| Handler ném checked exception ngoài `throws` | `UndeclaredThrowableException` |
| JPMS reflection tới proxy constructor bị encapsulate | `IllegalAccessException` / access error phù hợp |
| Class initialization/linkage/dependency bị lỗi | `LinkageError` hoặc error phù hợp hoàn cảnh |

**Không gom mọi lỗi ở ứng dụng dùng Dynamic Proxy thành lỗi ClassLoader.** Cần tách lỗi lúc generate proxy, lúc handler dispatch và lúc caller cast/consume result.

## 15. Thiết kế proxy trong hệ thống plugin

Một kiến trúc thông thường:

~~~text
                         Host process
             +--------------------------------+
             | Shared API (loader H)          |
             | api.Service                    |
             | api.Request / api.Response     |
             +----------------+---------------+
                              ^
                 parent delegation
                              |
          +-------------------+------------------+
          |                                      |
       Plugin A                                 Plugin B
       loader A                                 loader B
       impl.*                                   impl.*
       private.lib.*                            private.lib.*
          |                                      |
      handler A                              handler B
          |                                      |
          +-------- proxy as api.Service --------+
                              |
                           Host caller
~~~

Các nguyên tắc:

1. **Đặt service API trong common parent loader/module** mà host và plugins đều nhìn thấy cùng Class runtime.
2. **Không đóng gói bản API bị child-first define lại** chỉ để plugin compile. Có thể đưa API dependency vào compile classpath mà không đưa bản duplicate vào packaged plugin, tùy Maven/Gradle setup.
3. **DTO, callbacks, exception types** trong public method signatures cũng là phần của shared contract; không chỉ interface chính.
4. **Dùng interface array của host** nếu proxy cần trả về cho host. Không dùng Class cùng tên do plugin tự define.
5. **Chọn một proxy loader nhìn thấy toàn bộ interfaces** và signature types, không mặc định sử dụng TCCL.
6. **Tách implementation logic khỏi API identity.** InvocationHandler có thể gọi private target của plugin; kết quả/exception cần được chuyển về shared contract hợp lệ.
7. **Không dựa vào proxy để sandbox code plugin.** JDK proxy chỉ điều phối method invocations; code của handler/target vẫn có quyền của process/container tương ứng.
8. **Quản lý lifecycle/hot reload.** Các caches của proxy Classes và references từ InvocationHandler có thể giữ plugin ClassLoader cũ sống lâu hơn mong đợi. Khi reload cần release references và tạo loader/target mới có chủ đích.

Nếu dùng `ServiceLoader` để tìm implementations rồi bọc proxy, ServiceLoader provider cũng phải implement đúng shared service Class. Proxy không phải biện pháp bỏ qua lỗi “provider not a subtype”.

## 16. Ví dụ kiểm tra trước khi tạo proxy

Đây là một **mẫu helper** (không phải validator tổng quát hết mọi điều kiện):

~~~java
static void requireVisible(
        ClassLoader chosen, Class<?> iface)
        throws ClassNotFoundException {
    if (!iface.isInterface()) {
        throw new IllegalArgumentException(
                "Not an interface: " + iface.getName());
    }
    Class<?> fromChosen = Class.forName(
            iface.getName(), false, chosen);
    if (fromChosen != iface) {
        throw new IllegalArgumentException(
                "Same binary name, different runtime Class: "
                + iface.getName());
    }
}
~~~

**Điểm mạnh:** helper cung cấp thông báo rõ hơn về tên trùng nhưng loader khác, có ích cho log.

**Giới hạn:** helper chưa kiểm tra interfaces trùng trong array, sealed/hidden, non-public rules, module access, return-type compatibility hay types được tham chiếu trong public signatures. Với production framework, nên để `Proxy.newProxyInstance` thực hiện các checks đầy đủ và dùng helper chỉ để chẩn đoán.

Dùng `Class.forName(name, false, chosen)` **không yêu cầu chạy static initializer** của interface chỉ vì kiểm tra visibility, tránh tác dụng phụ không cần thiết trong preflight.

## 17. Chẩn đoán một lỗi IllegalArgumentException khi tạo proxy

Cách tiếp cận có hệ thống:

1. **Lấy chính xác loader truyền vào `newProxyInstance`.** Đừng chỉ nhìn `Thread.currentThread().getContextClassLoader()` nếu code đã truyền một loader khác.
2. **Lấy các `Class<?>` thật trong interface array.** Với mỗi phần tử: `isInterface()`, `getName()`, `getClassLoader()`, `getModule()`.
3. **Thử visibility predicate**: `Class.forName(iface.getName(), false, chosenLoader) == iface` và ghi rõ `ClassNotFoundException` hay cùng tên khác Class.
4. **Xem loader hierarchy**: parent-first, child-first theo package, sibling loader hoặc layer-specific loader.
5. **Kiểm tra API JAR duplicate** trong plugin classpath/fat JAR, application server hoặc hot reload build.
6. **Kiểm tra public method signatures** và inherited interfaces cho DTO/exception/parameter/return type mà proxy loader không nhìn thấy.
7. **Kiểm tra non-public interfaces**: cùng package, module và loader theo JDK rules?
8. **Kiểm tra sealed/hidden interfaces**, duplicate Class objects và return signatures xung đột.
9. **Với JPMS**, xem module membership và package exports/opens; không đánh đồng access issue với type identity.
10. **Sau khi sửa**, test cả tạo proxy, cast vào host interface và gọi methods; chỉ việc tạo thành công chưa bảo đảm cast qua boundary đúng.

Một helper log:

~~~java
static void describe(Class<?> c) {
    System.out.println("name=" + c.getName());
    System.out.println("loader=" + c.getClassLoader());
    System.out.println("module=" + c.getModule());
}
~~~

Đây là **đoạn code minh họa**, không có main. Khi thấy hai types cùng binary name nhưng khác loader, in thêm `(a == b)`; tên string không kết luận được identity.

Có thể bật class loading logging trong OpenJDK/HotSpot:

~~~bash
java -Xlog:class+load=info -cp host-classes ProxyVisibilityDemo shadow-classes
~~~

Log JVM không có stdout cố định và thay đổi theo JDK. Nếu cần phân tích proxy class sinh ra, một số JVM cung cấp diagnostic settings, nhưng **đừng phụ thuộc vào tên `$Proxy0` hoặc package sinh ra** như hợp đồng lâu dài.

## 18. Sai lầm phổ biến

| Sai lầm | Điều đúng |
| --- | --- |
| “Tên interface giống nhau là proxy được” | Loader phải thấy **chính Class runtime** |
| “Tôi có Class<?> rồi nên loader nào cũng tạo proxy được” | Loader truyền vào phải lookup được interface theo tên với identity đúng |
| “Dùng bootstrap loader là auto” | Bootstrap thường không thấy app interface |
| “Child loader khác parent thì chắc chắn không thấy API parent” | Delegation có thể trả đúng API Class |
| “Plugin loader tự define API copy vẫn dùng được với HostApi” | Proxy có thể hoạt động trong plugin nhưng host cast thất bại |
| “Chỉ interface chính cần nhìn thấy” | Public method signature types và inherited interfaces cũng phải visible |
| “Dynamic Proxy có thể implement concrete class” | JDK Dynamic Proxy implements interfaces, không extends arbitrary class |
| “Sealed interface được proxy tự do” | Java 17 yêu cầu non-sealed interfaces |
| “Non-public interface từ mọi package có thể gom một proxy” | Phải tuân thủ same-package/module/loader conditions |
| “Tên/package `$ProxyN` là cố định” | Proxy class names và dynamic modules không được cam kết cố định |
| “`getProxyClass` là cách tạo instance được ưu tiên” | Java 17 API này deprecated; dùng `newProxyInstance` |
| “InvocationHandler tự gọi default methods” | Handler cần dispatch, có thể dùng `InvocationHandler.invokeDefault` |
| “`equals`/`hashCode`/`toString` không qua handler” | Các lời gọi Object methods này cũng được dispatch |
| “Handler checked exception nào cũng ném thẳng ra caller” | Checked exception không nằm trong `throws` có thể bị wrap |
| “TCCL luôn đúng” | TCCL có thể không nhìn thấy host Class hoặc thấy bản sao |
| “Proxy sửa được SPI type duplication” | API/shared contract vẫn cần một runtime Class |
| “Dùng ClassCastException để chẩn đoán lỗi proxy constructor” | Constructor proxy thường IllegalArgumentException; cast mismatch xảy ra sau đó |

## 19. Bài tập và câu hỏi tự kiểm tra

**1. Vì sao JDK kiểm tra `Class.forName(i.getName(), false, loader) == i` thay vì `getName().equals`?**  
Vì cùng binary name có thể được các defining loaders khác nhau định nghĩa thành các runtime types khác nhau.

**2. Một child loader delegate đúng shared API về parent. Nó có thể tạo proxy cho parent API không?**  
Có, nếu mọi interface/signature type nhìn thấy theo đúng identity và các restrictions khác cũng thỏa.

**3. Loader không tìm ra interface Class. Lỗi từ `newProxyInstance` thường là gì?**  
`IllegalArgumentException` cho vi phạm interface visibility, không nhất thiết là `ClassNotFoundException` thoát ra ngoài.

**4. Loader tìm được interface cùng tên nhưng khác Class. Sửa bằng cách truyền bản interface đó vào array có đủ để host dùng proxy không?**  
Không; proxy sẽ implements interface của plugin, host có thể không cast được. Cần chia sẻ đúng contract Class.

**5. Nếu `Greeter.class` visible nhưng method có tham số `Request` từ loader khác thì sao?**  
Proxy vẫn có thể không được tạo vì public method signature types cũng phải visible.

**6. Hai non-public interfaces thuộc hai packages khác nhau có thể dùng chung một proxy không?**  
Không theo constraints Java 17 đối với non-public proxy interfaces.

**7. Vì sao sealed interface không hợp lệ làm proxy interface trong Java 17?**  
Vì dynamic proxy class không thuộc permitted implementations tĩnh và `Proxy` API yêu cầu interface không sealed.

**8. `InvocationHandler.invoke` nhận args thế nào khi method không có tham số?**  
`args` có thể là `null`.

**9. Handler trả null cho method trả `int` thì sao?**  
Lời gọi proxy có thể ném `NullPointerException` khi unbox.

**10. Handler ném checked exception không có trong `throws` thì sao?**  
Có thể bị bọc trong `UndeclaredThrowableException`.

**11. Có thể dựa vào tên `$Proxy0` để xác định đúng module không?**  
Không. Tên/package/module của proxy class không phải giao diện ổn định để logic nghiệp vụ dựa vào.

**12. Cần test những gì sau khi sửa loader?**  
Tạo proxy thành công, cast sang host API, xử lý methods, kết quả/exception, và tương thích các types trong signatures.

## 20. Tài liệu chính thức

- **Java SE 17 — `java.lang.reflect.Proxy` API**: restrictions, interface visibility, JPMS dynamic modules, duplicated method signatures, `newProxyInstance`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Proxy.html
- **Java SE 17 — `InvocationHandler` API**: `invoke`, object/primitive returns, checked exceptions, `invokeDefault`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationHandler.html
- **Java SE 17 — `ClassLoader` API**: `loadClass`, parent delegation và defining loaders: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- **Java SE 17 — `Class` API**: `forName`, `getClassLoader`, `getModule`, `isAssignableFrom`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- **Java SE 17 — `UndeclaredThrowableException`**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/UndeclaredThrowableException.html
- **JVM Specification 17, §5.3 — Creation and Loading**: runtime class identity: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- **JLS 17 §12.2 — Loading of Classes and Interfaces**: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.2


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-class-definition-003">MethodHandles.Lookup.defineClass và ngữ cảnh định nghĩa lớp</a>

<details>
<summary>Xem chi tiết</summary>

# MethodHandles.Lookup.defineClass và ngữ cảnh định nghĩa lớp trong Java

## 1. Tại sao định nghĩa lớp động cần một “ngữ cảnh” đáng tin cậy?

Java không chỉ nạp bytecode từ classpath hay JAR có sẵn. Framework, công cụ sinh mã, proxy engine và plugin runtime có thể **sinh class file bytes khi chương trình đang chạy**, sau đó yêu cầu JVM định nghĩa một kiểu mới. Nhưng một mảng `byte[]` không tự trả lời các câu hỏi quan trọng:

- Class mới sẽ thuộc **ClassLoader nào**?
- Nó có tên package và **runtime package** nào?
- Nó có quyền truy cập package-private vào các class xung quanh không?
- Nó thuộc module nào, có thể đọc và dùng những API nào?
- Nó nhận **protection domain** nào?
- Ai được quyền tạo class mới vào không gian tên đang tồn tại?
- Nó đã được **khởi tạo** chưa, và có trùng định nghĩa đã có không?

Từ Java 9, API `java.lang.invoke.MethodHandles.Lookup.defineClass(byte[])` giải quyết một trường hợp cụ thể: **định nghĩa một class bình thường, có tên, trong cùng ngữ cảnh runtime của lookup class**.

Chữ ký:

```java
public Class<?> defineClass(byte[] bytes)
        throws IllegalAccessException
```

Đây là chữ ký phương thức của `MethodHandles.Lookup`, không phải chương trình độc lập.

**Tóm tắt chính xác:** `Lookup.defineClass` tạo và liên kết class từ bytecode hợp lệ **với cùng defining ClassLoader, cùng runtime package và cùng protection domain với lookup class**. Nó **đòi hỏi mode `PACKAGE`** trên `Lookup` và **không chạy class initializer** ngay khi định nghĩa. Phương thức không nhận argument chọn ClassLoader tùy ý, cũng không tạo một class mới ở package khác do caller thích.

Điều này khác đáng kể với một custom `ClassLoader` tự đọc bytecode trong `findClass` rồi gọi `ClassLoader.defineClass`: bên dùng `Lookup` phải chứng minh mình có quyền ngữ cảnh package tương ứng, thay vì tùy chọn một loader/nguồn từ API đó.

## 2. Các thuật ngữ cần phân biệt

| Thuật ngữ | Ý nghĩa trong bài | Ví dụ |
| --- | --- | --- |
| **Class file bytes** | Dãy byte tuân theo JVMS class file format, thường bắt đầu bằng magic `CA FE BA BE` | Nội dung `Generated.class` |
| **Binary name** | Tên nhị phân của kiểu trong class file | `demo.lookup.Generated` |
| **Lookup class** | Class xác định ngữ cảnh và quyền của đối tượng `Lookup` | `demo.lookup.LookupDefineDemo` |
| **Lookup modes** | Các cờ quyền như `PUBLIC`, `MODULE`, `PACKAGE`, `PRIVATE` | `lookup.lookupModes()` |
| **Defining ClassLoader** | Loader định nghĩa runtime class, góp phần tạo danh tính kiểu | Application loader của lookup class |
| **Runtime package** | Cặp (tên package, defining loader) | `("demo.lookup", appLoader)` |
| **Module** | JPMS module chứa lookup class và class được định nghĩa trong ngữ cảnh ấy | Unnamed module trong demo |
| **Protection domain** | Thông tin miền bảo vệ gắn với class, do runtime dùng cho các cơ chế bảo mật tương ứng | `lookupClass.getProtectionDomain()` |
| **Linking** | Xác minh/chuẩn bị/phân giải theo quy tắc JVMS | Sau khi tạo định nghĩa, có thể còn resolution về sau |
| **Initialization** | Thực thi `<clinit>` theo quy tắc active use | Khi gọi static method của class mới |

Một runtime class/interface thông thường được nhận diện bằng **(binary name, defining loader)**. Cùng tên, cùng bytecode nhưng được hai loader độc lập định nghĩa thì là hai kiểu runtime khác nhau. Ngược lại, `Lookup.defineClass` **không tạo loader riêng**: nó định nghĩa class trong loader của lookup class.

### Mối liên hệ với module

`Lookup.defineClass` không phải cơ chế tùy ý “chèn bytecode vào mọi module”. Với một lookup hợp lệ, class mới được định nghĩa trong ngữ cảnh của lookup class, bao gồm module tương ứng. Khi source chạy trên classpath thông thường, lookup class thuộc **unnamed module** của loader; khi chạy trong named module, cần kiểm tra các quy tắc JPMS, ranh giới package và quyền tạo lookup.

**Có cùng module không tự động cấp mọi quyền private:** các thành viên private thuộc ranh giới class/nest, không phải mọi class nằm trong module. Class mới có thể dùng package-private khi hợp lệ, nhưng không mặc nhiên là nestmate của lookup class chỉ vì được định nghĩa bằng `defineClass`.

## 3. Tại sao phải dùng MethodHandles.Lookup?

Đối tượng `Lookup` là ngữ cảnh được JVM dùng để kiểm tra quyền khi tạo method handle và thực hiện một số thao tác đặc quyền. Một `Lookup` mang thông tin về:

- `lookup.lookupClass()`: class được xem là chủ thể tra cứu.
- `lookup.lookupModes()`: quyền truy cập hiện có.
- Khi phù hợp, ngữ cảnh module và các hạn chế khi lookup đã được chuyển đổi hoặc giảm quyền.

Ví dụ:

```java
MethodHandles.Lookup lookup = MethodHandles.lookup();
Class<?> callerClass = lookup.lookupClass();
boolean canDefine = (lookup.lookupModes()
        & MethodHandles.Lookup.PACKAGE) != 0;
```

Đây là snippet, cần import `java.lang.invoke.MethodHandles` và chạy ở method thuộc class có quyền phù hợp.

`MethodHandles.lookup()` trả lookup gắn với **caller thật tại vị trí gọi**. Nếu method gọi `lookup()` nằm trong `demo.lookup.Host`, lookup class là `demo.lookup.Host`, không phải class nào mà ứng dụng đang muốn định nghĩa.

**Điều kiện quan trọng:** `defineClass` yêu cầu `Lookup.PACKAGE` access. `MethodHandles.publicLookup()` chỉ có quyền public/unconditional tương ứng, không thể được sử dụng để định nghĩa một class mới vào package cụ thể.

Nếu ứng dụng nhận một `Lookup` từ thư viện khác, **không nên đoán** nó còn quyền nguyên vẹn: các thao tác như `lookup.in(target)` hoặc `dropLookupMode(...)` có thể làm giảm mode. Kiểm tra `lookupClass()`, `lookupModes()` và nguồn cấp lookup khi thiết kế API.

## 4. defineClass(byte[]) làm gì và không làm gì?

Theo Java SE 17 API, `Lookup.defineClass(byte[])`:

1. Nhận bytecode của một class file hợp lệ.
2. Xác định ngữ cảnh từ lookup class hiện tại.
3. Bắt buộc có quyền `PACKAGE`.
4. Yêu cầu class được định nghĩa có **tên package giống lookup class**.
5. Tạo class với cùng **ClassLoader, runtime package và protection domain** của lookup class, tương đương về mặt định nghĩa với quy tắc `ClassLoader.defineClass` thích hợp.
6. **Tạo và liên kết** class, trả về đối tượng `Class<?>`.
7. **Không chạy static initializer** của class mới ngay tại lời gọi `defineClass`.

Thứ tự nội bộ, kiểm tra và thời điểm resolution của JVM có quy tắc riêng. Không suy luận rằng mọi phụ thuộc của class mới đều đã được khởi tạo hay rằng mọi lời gọi sau đó chắc chắn thành công.

### Không có các khả năng sau

- Không truyền tên binary name riêng như `defineClass("x.y.Z", bytes, ...)` của một số overload `ClassLoader`. Tên nằm trong bytes.
- Không cho caller chọn class loader bằng argument.
- Không cho định nghĩa class ở package khác lookup class.
- Không tự mở package của named module cho những module khác.
- Không đổi `exports`, `opens` hay `requires` của module descriptor.
- Không tự thêm class vào một nest để có quyền private; đó là chủ đề khác.
- Không tạo **hidden class**; API đó là `defineHiddenClass`.
- Không re-define một binary name đã tồn tại trong cùng defining loader như phiên bản mới.
- Không tạo sandbox an toàn cho bytecode từ nguồn không tin cậy.

### Vì sao `byte[]` phải đúng package?

Giả sử lookup class là `demo.lookup.Host`, còn mảng bytes định nghĩa `external.plugin.Generated`. Dù các bytes là một class file hợp lệ, lời gọi:

```java
lookup.defineClass(externalBytes);
```

sẽ vi phạm quy tắc **same package**, thường được API báo bằng `IllegalArgumentException` theo hợp đồng `Lookup.defineClass`. Đây không phải tình huống cần thử thêm dấu `/` vào tên: tên lớp thật nằm trong constant pool của class file.

## 5. Ví dụ Java 17 đã kiểm chứng: định nghĩa lớp từ bytecode đang ở ngoài classpath

Bài thực hành sau tạo bytecode động bằng `JavaCompiler` và nạp class không nằm trên application classpath. Tên class mới được chọn là `demo.lookup.Generated`, **cùng package** với chương trình chủ:

```text
Mã host lúc biên dịch:
    demo.lookup.LookupDefineDemo  -> out/demo/lookup/LookupDefineDemo.class

File Generated.java tạo lúc runtime trong thư mục tạm:
    demo.lookup.Generated         -> temp/classes/demo/lookup/Generated.class

Host đọc Generated.class thành byte[]
   -> MethodHandles.lookup().defineClass(bytes)
   -> Class<?> của demo.lookup.Generated
```

Không cần chạy `java -cp ...:temp/classes`. Chính lookup định nghĩa kiểu mới ngay trong loader của host. Đây là đặc tính khác với tìm class qua path bằng `ClassLoader.loadClass`.

### 5.1. Lưu chương trình đầy đủ

Tạo file `src/demo/lookup/LookupDefineDemo.java`:

```java
package demo.lookup;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public final class LookupDefineDemo {
    private static final String INIT_KEY =
            "example.lookup.defineClass.initialized";

    public static void main(String[] args) throws Throwable {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("A full JDK is required");
        }

        System.clearProperty(INIT_KEY);
        Path work = Files.createTempDirectory("lookup-define-");

        try {
            Path source = work.resolve(
                    "src/demo/lookup/Generated.java");
            Path foreign = work.resolve(
                    "src/demo/other/Foreign.java");
            Path output = work.resolve("classes");

            Files.createDirectories(source.getParent());
            Files.createDirectories(foreign.getParent());
            Files.createDirectories(output);

            Files.writeString(source, """
                    package demo.lookup;

                    public final class Generated {
                        static {
                            System.setProperty(
                                "example.lookup.defineClass.initialized", "yes");
                        }

                        static String say() {
                            return "Hello, dynamically defined";
                        }
                    }
                    """);

            Files.writeString(foreign, """
                    package demo.other;
                    public final class Foreign { }
                    """);

            int code = compiler.run(null, null, null,
                    "--release", "17", "-encoding", "UTF-8",
                    "-d", output.toString(),
                    source.toString(), foreign.toString());
            if (code != 0) {
                throw new IllegalStateException(
                        "Dynamic compilation failed");
            }

            byte[] localBytes = Files.readAllBytes(
                    output.resolve("demo/lookup/Generated.class"));
            byte[] foreignBytes = Files.readAllBytes(
                    output.resolve("demo/other/Foreign.class"));

            MethodHandles.Lookup lookup = MethodHandles.lookup();

            System.out.println("lookupClass="
                    + (lookup.lookupClass() == LookupDefineDemo.class));
            System.out.println("hasPackageMode="
                    + ((lookup.lookupModes()
                    & MethodHandles.Lookup.PACKAGE) != 0));

            Class<?> generated = lookup.defineClass(localBytes);

            System.out.println("samePackage="
                    + generated.getPackageName().equals(
                            LookupDefineDemo.class.getPackageName()));
            System.out.println("sameLoader="
                    + (generated.getClassLoader()
                            == LookupDefineDemo.class.getClassLoader()));
            System.out.println("sameModule="
                    + (generated.getModule()
                            == LookupDefineDemo.class.getModule()));
            System.out.println("sameProtectionDomain="
                    + (generated.getProtectionDomain()
                            == LookupDefineDemo.class.getProtectionDomain()));
            System.out.println("notInitializedAfterDefine="
                    + (System.getProperty(INIT_KEY) == null));

            MethodHandle handle = lookup.findStatic(
                    generated, "say", MethodType.methodType(String.class));

            System.out.println("call="
                    + (String) handle.invokeExact());
            System.out.println("initializedAfterCall="
                    + "yes".equals(System.getProperty(INIT_KEY)));

            try {
                MethodHandles.publicLookup().defineClass(localBytes);
                System.out.println("publicLookup=unexpected");
            } catch (IllegalAccessException expected) {
                System.out.println("publicLookup=IllegalAccessException");
            }

            try {
                lookup.defineClass(foreignBytes);
                System.out.println("otherPackage=unexpected");
            } catch (IllegalArgumentException expected) {
                System.out.println("otherPackage=IllegalArgumentException");
            }

            try {
                lookup.defineClass(localBytes);
                System.out.println("duplicate=unexpected");
            } catch (LinkageError expected) {
                System.out.println("duplicate=LinkageError");
            }
        } finally {
            System.clearProperty(INIT_KEY);
            try (Stream<Path> stream = Files.walk(work)) {
                for (Path p :
                        stream.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }
}
```

**Lệnh chạy**, từ thư mục chứa `src`:

```bash
javac --release 17 -d out src/demo/lookup/LookupDefineDemo.java
java -cp out demo.lookup.LookupDefineDemo
```

Không cần biên dịch thủ công `Generated.java` hoặc `Foreign.java`: hai file này được chương trình tạo tạm và biên dịch ở runtime qua `JavaCompiler`. Cần **JDK đầy đủ** (có module `jdk.compiler`), không chỉ runtime image tối giản không chứa compiler.

### 5.2. Stdout chính xác

Chương trình trên được biên dịch bằng JDK 21 với mục tiêu `--release 17` và đã chạy thành công. Stdout thu được:

```text
lookupClass=true
hasPackageMode=true
samePackage=true
sameLoader=true
sameModule=true
sameProtectionDomain=true
notInitializedAfterDefine=true
call=Hello, dynamically defined
initializedAfterCall=true
publicLookup=IllegalAccessException
otherPackage=IllegalArgumentException
duplicate=LinkageError
```

Kết quả được xây dựng tránh phụ thuộc vào thứ tự liệt kê class/JAR hoặc vào GC. Nếu filesystem không cho tạo tệp tạm hay compiler không có, chương trình báo lỗi rõ thay vì giả vờ có cùng output.

### 5.3. Đọc kết quả theo ngữ cảnh

**`lookupClass=true`:** `MethodHandles.lookup()` được gọi bên trong `LookupDefineDemo.main`, nên class của lookup là `LookupDefineDemo`, không phải `Generated`.

**`hasPackageMode=true`:** lookup do chính class gọi tạo có quyền `PACKAGE`, là điều kiện cần của `defineClass`.

**`samePackage=true`:** bytes định nghĩa class trong `demo.lookup`, đúng package của lookup class. Nếu bytes mang tên `demo.other.Foreign`, không đạt điều kiện này.

**`sameLoader=true`:** class mới được định nghĩa bởi cùng loader với `LookupDefineDemo`. Không có custom ClassLoader thứ hai được tạo.

**`sameModule=true`:** cả host và class mới thuộc cùng ngữ cảnh module. Trong demo chạy qua classpath, cả hai thuộc unnamed module của application loader.

**`sameProtectionDomain=true`:** class mới có cùng protection domain với lookup class, không phải một domain do dữ liệu từ filesystem tự quyết định. Điều này cũng là lý do không được lấy lookup quyền cao để định nghĩa bytecode chưa xác minh.

**`notInitializedAfterDefine=true`:** khi `defineClass(localBytes)` trả về, static block của `Generated` chưa chạy nên system property chưa được đặt.

**`call=Hello, dynamically defined` và `initializedAfterCall=true`:** gọi static method `say()` qua method handle là active use; JVM khởi tạo class trước lời gọi, static block đặt system property.

**`publicLookup=IllegalAccessException`:** lookup public không có `PACKAGE` mode. Dù bytes đúng package, thiếu quyền lookup khiến định nghĩa bị từ chối.

**`otherPackage=IllegalArgumentException`:** bytes của `demo.other.Foreign` không cùng package với `LookupDefineDemo`. API từ chối class file này.

**`duplicate=LinkageError`:** đã có định nghĩa `demo.lookup.Generated` bởi loader đó. Gọi `defineClass` với cùng binary name lần nữa không tạo ra “version 2” của class trong cùng loader.

### 5.4. Vì sao dùng MethodHandle để gọi static method?

Phương thức `Generated.say()` có mức **package-private** (không khai báo `public`). Lookup chủ thuộc cùng runtime package có quyền thích hợp nên có thể lấy handle:

```java
MethodHandle handle = lookup.findStatic(
        generated, "say", MethodType.methodType(String.class));
```

Đây là snippet thuộc chương trình đầy đủ. Phần `invokeExact()` gọi method đúng chữ ký `()String` và gây initialization trước khi thực thi.

Nó đồng thời minh họa một lợi ích thực tế: class được định nghĩa **trong package/loader của host lookup**, cho phép tích hợp những API package-private có chủ đích, thay vì buộc công khai mọi API nội bộ. Nhưng vẫn phải kiểm soát ai được nhận Lookup có quyền `PACKAGE`.

## 6. Quy tắc “same package” nghiêm ngặt tới mức nào?

Ví dụ host nằm trong `demo.lookup`:

| Tên class thực trong bytes | Kết quả đối với lookup của `demo.lookup.LookupDefineDemo` |
| --- | --- |
| `demo.lookup.Generated` | Hợp lệ về **điều kiện package** nếu bytes chuẩn, chưa định nghĩa trùng, quyền lookup đủ |
| `demo.lookup.Helper$Nested` | Thuộc package `demo.lookup`; vẫn cần kiểm tra bytes/điều kiện khác |
| `demo.other.Foreign` | Khác package, `IllegalArgumentException` theo API |
| `java.lang.FakeString` | Khác package; không thể dùng lookup này để xâm nhập package Java nền tảng |
| Class file hỏng hoặc không phải class/interface | Không hợp lệ; có thể `ClassFormatError`/`IllegalArgumentException` tùy lỗi |
| Class cùng binary name đã được loader định nghĩa | Xung đột linkage; không phải tự ghi đè class cũ |

**Đừng tự kiểm tra package chỉ bằng đường dẫn file**. Package của một class được xác định từ tên nhị phân mã hóa trong bytecode, không phải do file được đặt ở thư mục nào. Đổi tên file `Foreign.class` thành `Generated.class` không làm đổi package chứa trong class file.

### Runtime package phụ thuộc defining loader

Hai class cùng package string nhưng khác loader là hai runtime packages khác nhau. Nếu ứng dụng thật cần định nghĩa class trong runtime package của một class ở plugin loader, hãy lấy **Lookup hợp lệ của chính class thuộc loader/plugin ấy** qua cơ chế được ủy quyền, không truyền bytecode vào lookup của host rồi kỳ vọng class tự “chuyển” sang plugin loader.

Ví dụ:

```text
HostLoader định nghĩa demo.lookup.Host
PluginLoader định nghĩa plugin.internal.Anchor

Host lookup -> định nghĩa class thuộc demo.lookup + HostLoader
Plugin anchor lookup -> định nghĩa class thuộc plugin.internal + PluginLoader
```

Đây là mô hình khái niệm; việc lấy plugin anchor lookup còn cần điều kiện quyền/module phù hợp. Có hai class loader không có nghĩa bạn tự động được quyền lấy lookup PACKAGE của cả hai.

## 7. Quyền Lookup và các cách tạo ngữ cảnh

### 7.1. MethodHandles.lookup()

`MethodHandles.lookup()` tạo Lookup từ caller class thực. Nếu gọi bên trong class `pkg.Anchor`, nó có thể có quyền đầy đủ trong ngữ cảnh phù hợp của Anchor. Đây là lựa chọn đơn giản nhất khi code **trong package đó** chủ động quyết định sinh class:

```java
final class Anchor {
    static Class<?> defineTrusted(byte[] bytes)
            throws IllegalAccessException {
        return MethodHandles.lookup().defineClass(bytes);
    }
}
```

Đây là **snippet minh họa**, cần import `MethodHandles`, được biên dịch trong package hiện tại. Một API như `defineTrusted` chỉ nên nhận bytes từ nguồn tin cậy và kiểm tra tính hợp lệ cần thiết.

### 7.2. MethodHandles.publicLookup()

`publicLookup()` được thiết kế cho các thành viên public thích hợp, **không có PACKAGE mode**, nên không thể gọi `defineClass` thành công. Nó không phải một “Lookup tự do nạp class” chỉ vì có tên public.

### 7.3. privateLookupIn(targetClass, callerLookup)

`MethodHandles.privateLookupIn(targetClass, callerLookup)` có thể tạo Lookup tới target class khi caller có **đủ quyền** và quan hệ truy cập/module cho phép. Với named modules, điều kiện module/package được mở phù hợp (*opens*) rất quan trọng. Không phải chỉ có target Class object là có thể lấy được mọi quyền của nó.

Một cách dùng kiến trúc:

1. Host định nghĩa API public để yêu cầu plugin tự sinh class.
2. Plugin có anchor class trong đúng package và loader mà class mới cần ở.
3. Code được cấp quyền sử dụng lookup PACKAGE một cách có kiểm soát.
4. Lookup mới chỉ định nghĩa **class cùng package** từ bytes được xác minh.
5. Host không cần cố mở `java.base` hay dùng reflective access trái chính sách JPMS.

**Nguyên tắc capability:** Lookup có `PACKAGE` mode là một dạng quyền có thể ủy nhiệm. Không phát tán Lookup đó cho mọi plugin hay cho dữ liệu từ mạng; người nhận có thể dùng nó để định nghĩa class mới trong không gian tên hợp lệ của lookup class.

### 7.4. Lookup.in và dropLookupMode

Các phương thức này có thể tạo một Lookup có quyền thấp hơn. Nếu quyền `PACKAGE` bị mất, `defineClass` sẽ không còn được phép:

```java
MethodHandles.Lookup restricted =
        MethodHandles.lookup().dropLookupMode(
                MethodHandles.Lookup.PACKAGE);
```

Đây là snippet; gọi `restricted.defineClass(bytes)` sẽ không có đủ quyền PACKAGE và có thể ném `IllegalAccessException`. Không được gắn nhãn lookup là “full privilege” chỉ vì object ban đầu đến từ code trusted.

## 8. So sánh Lookup.defineClass với ClassLoader.defineClass

Có hai nhóm cách định nghĩa class khác nhau về quyền và khả năng cấu hình.

| Tiêu chí | `Lookup.defineClass(bytes)` | `ClassLoader.defineClass(name, bytes, ...)` |
| --- | --- | --- |
| Chủ thể gọi | Code có một `Lookup` phù hợp | Subclass/logic của `ClassLoader` có quyền gọi protected API |
| Chọn loader | **Loader của lookup class** | Loader nhận lệnh `defineClass` |
| Chọn package | **Phải cùng package với lookup class** | Dựa vào bytes/name và policy/namespace của loader |
| Protection domain | **Cùng domain với lookup class** | Có overload cho phép chỉ định `ProtectionDomain` |
| Quyền bắt buộc | `Lookup.PACKAGE` | Quy tắc bảo mật/JVM của ClassLoader và protected method |
| Tích hợp package-private | Có thể trong cùng runtime package | Nếu class được định nghĩa bởi loader khác thì không thành cùng runtime package |
| Class file bytes | Yêu cầu bytes hợp lệ | Yêu cầu bytes hợp lệ |
| Khởi tạo static ngay khi định nghĩa? | **Không** | Không tự coi `defineClass` là một lệnh gọi `<clinit>` |
| Định nghĩa trùng binary name | Không cho tự ghi đè class | Không cho tự ghi đè class |
| Hidden class? | Không | Không; cần API riêng cho hidden class |

### Khi nào nên dùng Lookup.defineClass?

Phù hợp khi:

- Sinh một helper class mới để **cộng tác với code đã tồn tại trong chính package đó**.
- Muốn dùng package-private members trong một thư viện/runtime đã chủ động cấp lookup hợp lệ.
- Không cần tạo ClassLoader mới và không cần đưa class sang package khác.
- Bytecode do pipeline đáng tin cậy tạo ra, có quy tắc naming/version rõ ràng.
- Muốn định nghĩa một **named, ordinary class** có thể được xử lý bởi các API phản chiếu thông thường.

### Khi nào nên dùng custom ClassLoader?

Phù hợp hơn khi:

- Cần định nghĩa class ở **nhiều package** từ thư mục, JAR, database hoặc bytecode repository riêng.
- Cần **cô lập phiên bản thư viện**, unload một nhóm class theo vòng đời loader riêng.
- Cần chính sách parent/child delegation, package ownership và resource lookup riêng.
- Cần quản lý plugin loading một cách độc lập với loader/module của host.
- Cần kiểm soát protection domain/code source theo cơ chế được thiết kế trong custom loader.

**Không thay custom ClassLoader bằng một lookup của host rồi mong mọi plugin được cô lập.** Nếu tất cả class mới được định nghĩa chung ở host loader, chúng gắn với vòng đời host, không có isolation theo loader riêng cho từng phiên bản.

## 9. Định nghĩa lớp, linking và class initialization là ba việc khác nhau

Mối quan hệ:

```text
byte[] hợp lệ
    |
    v
Lookup.defineClass(bytes)
    |
    +-- tạo runtime class
    +-- linking theo hợp đồng API/JVMS
    +-- trả Class<?>
    |
    +-- chưa tự chạy static initializer
            |
            v
sau đó active use (ví dụ gọi static method)
    |
    +-- class initialization xảy ra theo quy tắc JLS
```

JVM có thể thực hiện một phần verification/resolution ở những thời điểm khác nhau, nhưng **lời gọi defineClass không tự chạy static initializer**. Đây là lý do ví dụ có:

```text
notInitializedAfterDefine=true
...
initializedAfterCall=true
```

Không phải mọi thao tác cầm `Class<?>` đều là active use: xem tên class, defining loader hoặc module thông thường không tương đương gọi constructor/static method. Nhưng một số thao tác reflective/API khác có thể yêu cầu initialization theo hợp đồng riêng; phải xét method cụ thể.

### Nếu static initializer ném lỗi?

Nếu `<clinit>` ném exception, các lời gọi sau có thể báo `ExceptionInInitializerError` hoặc `NoClassDefFoundError` liên quan class bị lỗi initialization, chứ không có nghĩa `Lookup.defineClass` đã “không tạo class”. Khi debug, tách lỗi **definition/linkage** khỏi lỗi **initialization**.

## 10. Những ngoại lệ cần biết

| Lỗi / exception | Khi nào cần xét? | Hướng xử lý |
| --- | --- | --- |
| `IllegalAccessException` | Lookup không có `PACKAGE` mode | Tạo/nhận Lookup đúng quyền tại anchor package; không dùng publicLookup |
| `IllegalArgumentException` | Bytes định nghĩa class khác package hoặc không phải class/interface theo quy tắc API | Kiểm tra binary name thật trong class file và loại class |
| `ClassFormatError` | Không phải class file cấu trúc hợp lệ | Kiểm tra nguồn bytes, format, transform, compiler |
| `VerifyError` | JVM không xác minh được bytecode | Kiểm tra generator/transformer, descriptors, stack map frames |
| `UnsupportedClassVersionError` | Class file yêu cầu JVM version mới hơn | Kiểm tra `javac --release`, version runtime |
| `LinkageError` | Trùng định nghĩa, ràng buộc linking hoặc dependency khác không hợp lệ | Kiểm tra naming/loader/dependency và nguyên nhân đầy đủ |
| `NullPointerException` | `bytes == null` | Không truyền dữ liệu thiếu |
| `SecurityException` | Có SecurityManager legacy và permission check phù hợp bị từ chối | Xem chính sách runtime, đừng nuốt exception |
| `ExceptionInInitializerError` | Class initializer ném lỗi khi active use | Tách khỏi thời điểm `defineClass` |
| `NoClassDefFoundError` | Dependency không khả dụng, class state lỗi trước đó hoặc wrong name trong tình huống liên quan | Đọc chuỗi cause và thời điểm lỗi |

**Không catch chung mọi `Throwable` rồi đổi sang `ClassNotFoundException`.** `LinkageError`, `VerifyError` hoặc `ClassFormatError` phản ánh vấn đề khác “không có class để tìm”. Che lỗi sẽ làm debugging và xử lý trạng thái JVM không an toàn.

### Duplicate definition trong cùng loader

Mỗi runtime class identity thông thường là (binary name, defining loader). Nếu `demo.lookup.Generated` đã được định nghĩa, gọi `lookup.defineClass(localBytes)` lần nữa cho cùng tên, cùng loader sẽ không tạo bản thứ hai. Ví dụ phát hiện `LinkageError` và in `duplicate=LinkageError`.

Cần cách khác cho nhu cầu **hot reload**: thiết kế ClassLoader mới hoặc cơ chế JVM instrumentation phù hợp, chứ không gọi lại `defineClass` vào cùng loader. Không đánh đồng các API redefine/retransform của instrumentation với `Lookup.defineClass`.

## 11. So sánh với defineHiddenClass

Từ Java 15, `Lookup.defineHiddenClass(bytes, initialize, options...)` phục vụ các class đặc biệt không thể được tìm theo binary name thông thường của named class. Đây là hướng khác với `defineClass`.

| Đặc tính | `defineClass` | `defineHiddenClass` |
| --- | --- | --- |
| Class kind | Named, ordinary class | Hidden class với quy tắc đặc biệt |
| Trả về | `Class<?>` | `MethodHandles.Lookup` trên hidden class |
| Yêu cầu lookup | `PACKAGE` mode | Full-privilege access theo API |
| Initialization parameter | Không; define không chạy initializer | Có tham số boolean `initialize` |
| Naming/lookup | Có binary name thông thường | Không tìm bằng cơ chế `ClassLoader.loadClass(name)` thông thường |
| Nestmate tùy chọn | Không có class option tương ứng | `NESTMATE` option có thể tạo quan hệ nest theo API |
| Strength/lifetime option | Theo class/loader thường | `STRONG` option cho hidden class theo quy tắc riêng |
| Mục đích | Named helper class trong runtime package | Runtime-generated implementation chi tiết, lambda-like internals |

**Đừng gọi `defineClass` với bytes của một class thông thường rồi gọi nó là hidden class.** Hai API có hợp đồng khác nhau về naming, initialization, privilege và lifecycle. Một số use case generated implementation không muốn chiếm binary name lâu dài có thể phù hợp hơn với hidden class, nhưng phải hiểu nest/module access và class data APIs.

### Hidden class có nghĩa “không thể rò rỉ bộ nhớ” không?

Không. Hidden class vẫn có thể bị giữ bởi object, method handle, `Class<?>`, registry hoặc reference khác. Điều kiện thu hồi phụ thuộc reachability/cơ chế JVM và tùy chọn liên quan; không có API “tự biến mất ngay”. Dù class loader có thể sống lâu, hidden class thường được thiết kế cho những tình huống vòng đời linh hoạt hơn named classes thông thường, nhưng không thay thế cleanup các tài nguyên bên ngoài.

## 12. Bảo mật và tính toàn vẹn của bytecode

Một lookup có quyền PACKAGE là một **capability** mạnh: nó cho phép định nghĩa class mới vào namespace runtime của lookup class với protection domain tương ứng. Vì vậy phải kiểm soát **nguồn bytes** và **người giữ lookup**.

### Quy trình kiểm tra bytecode đầu vào

1. **Ràng buộc nguồn tin cậy:** compiler nội bộ hoặc artifact ký/được build từ pipeline đã xác minh.
2. **Ràng buộc namespace:** class mới thuộc đúng package dự kiến. API có kiểm tra cuối nhưng ứng dụng nên có validation và naming policy rõ.
3. **Giới hạn kích thước:** không đọc byte array cực lớn hoặc số lượng class không giới hạn.
4. **Kiểm tra class file header:** magic `CA FE BA BE` và major version là điều kiện sơ bộ, **không chứng minh tính an toàn**.
5. **Xác thực integrity:** kiểm tra SHA-256 hoặc chữ ký so với metadata từ nguồn tin cậy. Digest lấy từ cùng nguồn không tin cậy không xác minh publisher.
6. **Bytecode verification:** dựa vào JVM verifier và công cụ sinh/biến đổi bytecode phù hợp, không tự tin chỉ vì class có magic header.
7. **Dependency graph:** class mới có thể tham chiếu superclass/interface hoặc library ngoài loader/graph của host; cần nạp/link đúng.
8. **Naming collision:** dùng naming scheme đảm bảo không trùng các class đã định nghĩa; không cố re-define cùng binary name trong một loader.
9. **Audit:** log binary name, fingerprint artifact, lookup class, loader identity và module; tránh log raw secrets/bytecode.
10. **Quyền thực thi:** class hợp lệ vẫn có thể gọi API mà tiến trình được phép; xác minh logic hoặc dùng process isolation khi chạy mã không tin cậy.

**Bytecode verifier không phải sandbox.** Việc class được định nghĩa cùng protection domain với host có thể làm các rủi ro nghiêm trọng hơn nếu host trao quyền `Lookup.defineClass` cho code hoặc bytes không đáng tin cậy.

### Điều không nên làm

```java
// Minh họa chống chỉ định: không dùng như API công khai
public Class<?> defineAnyUserBytes(byte[] userBytes)
        throws IllegalAccessException {
    return MethodHandles.lookup().defineClass(userBytes);
}
```

Đây là **đoạn chống chỉ định**, không phải code mẫu production. Dù phương thức chưa vượt ranh giới package, nó biến ngữ cảnh lookup của class có quyền thành cổng định nghĩa class từ input không tin cậy. Nếu cần mở rộng plugin, hãy tạo kiến trúc phân quyền riêng và kiểm tra artifact kỹ hơn.

## 13. Giải quyết vấn đề trong named module

Với JPMS, class của một named module không chỉ có class loader và runtime package mà còn thuộc module trong runtime graph. Khi muốn định nghĩa class mới cùng package của một named module, nên để **anchor class của chính module/package đó** cung cấp lookup theo cơ chế hợp lệ.

Ví dụ khái niệm:

```java
module app.codegen {
    exports app.codegen.api;
    // app.codegen.internal không tự động export hoặc opens.
}
```

Nếu `app.codegen.internal.Anchor` muốn sinh class trong `app.codegen.internal`, có thể dùng một lookup được tạo đúng trong Anchor. Module khác không tự có quyền PACKAGE vào package này chỉ nhờ `requires app.codegen;` hoặc bằng cách biết chuỗi tên package.

### privateLookupIn và opens

Để lấy private lookup sang một class thuộc module khác, `MethodHandles.privateLookupIn` phải tuân thủ khả năng đọc module và việc package đích **mở** phù hợp với module của caller. Một package chỉ `exports` public API chưa đồng nghĩa code ngoại module được tạo lookup đặc quyền vào mọi class trong package.

Thực hành nên ưu tiên module chủ sở hữu **chủ động cung cấp capability hẹp** cho công cụ sinh mã đáng tin, thay vì sửa nhiều `--add-opens` chỉ để “vượt” ngăn cách JPMS.

### Cùng module không thay thế PACKAGE access

Ngay cả khi lookup class và class bytecode có thể thuộc cùng module, `defineClass` vẫn yêu cầu:
- Lookup có `PACKAGE` mode;
- Binary name trong bytes có package giống lookup class;
- Class file hợp lệ;
- Không có xung đột definition/linkage.

Không suy luận rằng module được resolve tức lookup được phép định nghĩa lớp ở mọi package trong module.

## 14. Chẩn đoán khi defineClass thất bại

Quy trình thực hành:

1. **Xác định lookup class thực tế.** Log `lookup.lookupClass().getName()` và `lookup.lookupClass().getClassLoader()`.
2. **Đọc lookup modes.** Kiểm tra quyền `PACKAGE` bằng mask; không dựa vào tên biến `trustedLookup`.
3. **Xác minh bytes.** Class file version, binary name, magic, checksum, source artifact và biến đổi bytecode.
4. **So package** trong class bytes với `lookup.lookupClass().getPackageName()`.
5. **Kiểm tra class đã được định nghĩa chưa.** Một loader không thể tạo hai named classes cùng binary name bằng cách lặp defineClass.
6. **Phân biệt lỗi access, format, verify, linking và initialization.** Mỗi loại cần giải pháp khác nhau.
7. **Nếu JPMS:** xem module của lookup class, caller và quy tắc mở package khi lấy Lookup.
8. **Nếu plugin nhiều loader:** xác định loader thực sự định nghĩa lookup class, không nhầm initiating loader/TCCL.
9. **Nếu gặp memory pressure:** named class đã định nghĩa bởi loader sống lâu không được “xóa” chỉ nhờ bỏ Class<?> local. Đánh giá loader lifecycle và code generation volume.
10. **Không dùng catch Throwable** để che các lỗi nghiêm trọng rồi tạo một class fallback sai ABI.

### Công cụ JDK hữu ích

Với bytecode có trên đĩa:

```bash
javap -verbose -classpath generated/classes demo.lookup.Generated
```

Đây là **lệnh minh họa** cho artifact thật: thay classpath root và tên class theo thực tế. `javap -verbose` cho biết major version, constant pool, flags, phương thức và tên nhị phân được lưu trong class file. Nó không xác minh toàn bộ chính sách bảo mật của generator.

Với ứng dụng có log class loading phù hợp:

```bash
java -Xlog:class+load=info -cp out demo.lookup.LookupDefineDemo
```

Các event class-load có thể giúp thấy class nào được JVM định nghĩa/nạp, nhưng không luôn hiện nguồn file theo cách giống URLClassLoader khi bytes được cung cấp trực tiếp cho Lookup. Kết hợp log ứng dụng về fingerprint/lookup class để chẩn đoán.

### Snippet in ngữ cảnh sau khi define

```java
static void printType(Class<?> type) {
    System.out.println("name=" + type.getName());
    System.out.println("loader=" + type.getClassLoader());
    System.out.println("module=" + type.getModule());
    System.out.println("package=" + type.getPackageName());
    System.out.println("domain=" + type.getProtectionDomain());
}
```

Đây là **helper method**, không phải chương trình standalone. Khi in protection domain hay code source, hãy tránh để lộ thông tin nhạy cảm trong production logs.

## 15. Lỗi hiểu nhầm thường gặp

| Hiểu nhầm | Thực tế |
| --- | --- |
| “Có bất kỳ Lookup object nào đều gọi defineClass được.” | Phải có `PACKAGE` mode và đúng package của lookup class. |
| “publicLookup có thể sinh class public trong mọi package.” | Không; publicLookup không có PACKAGE access. |
| “defineClass(bytes) tự chọn tên class từ biến filename.” | Tên nằm trong class file bytes; file name không quyết định. |
| “Lookup.defineClass tạo một ClassLoader mới.” | Không; dùng loader của lookup class. |
| “Cùng package string ở hai loader là cùng runtime package.” | Không; runtime package còn xét defining loader. |
| “Class mới được định nghĩa sẽ chạy static block ngay.” | Không; defineClass không tự chạy initializer. |
| “Một class đã tồn tại có thể được cập nhật bằng defineClass lần hai.” | Không; duplicate binary name trong cùng loader gây linkage conflict. |
| “Được quyền PACKAGE nghĩa là truy cập mọi private member của mọi class cùng package.” | Không; private/nest access có ràng buộc riêng. |
| “Một lookup của host có thể định nghĩa tùy ý trong loader của plugin.” | Không; loader gắn với lookup class. |
| “Named module đã exports thì code ngoài module có toàn quyền privateLookupIn.” | Không; cần xem opens/readability và điều kiện API. |
| “ClassFormatError nghĩa bytes độc hại.” | Chỉ có nghĩa cấu trúc class không hợp lệ; không đánh giá ý định của code. |
| “Class hợp lệ qua verifier là an toàn để chạy.” | Không; phải kiểm soát quyền, trust và side effects. |
| “Lookup.defineClass là defineHiddenClass.” | Khác nhau về naming, privilege, initialization và lifecycle. |
| “JVM tự dọn mọi named class khi local Class<?> về null.” | Không; class unloading phụ thuộc defining loader reachability và GC. |

## 16. Bài tập tự kiểm tra

**Câu 1.** Lookup được tạo bên trong `demo.codegen.Anchor`, bytes chứa `demo.codegen.Generated`. Nếu Lookup có PACKAGE mode và bytecode hợp lệ, class mới dùng loader nào?

**Trả lời:** Defining loader của `demo.codegen.Anchor`; cùng runtime package và protection domain với lookup class.

**Câu 2.** Bytes chứa `other.pkg.Generated`, còn lookup class ở `demo.codegen`. Điều gì xảy ra?

**Trả lời:** Khác package nên `Lookup.defineClass` ném `IllegalArgumentException` theo hợp đồng API, thay vì âm thầm đổi package.

**Câu 3.** Vì sao `MethodHandles.publicLookup().defineClass(bytes)` thất bại?

**Trả lời:** Public lookup không có `PACKAGE` access; API ném `IllegalAccessException`.

**Câu 4.** `defineClass` trả Class<?> thành công. Static block trong class mới đã chạy chưa?

**Trả lời:** Chưa chỉ vì defineClass. Initialization diễn ra sau đó theo quy tắc active use, ví dụ lần đầu gọi một static method thích hợp.

**Câu 5.** Cùng bytes được dùng với hai Lookup mà lookup classes do hai defining loaders khác nhau định nghĩa. Hai Class<?> kết quả có là cùng runtime class không?

**Trả lời:** Không nếu mỗi loader định nghĩa độc lập. Runtime class identity là (binary name, defining loader).

**Câu 6.** Có thể dùng `Lookup.defineClass` thay cho custom URLClassLoader để cô lập hai phiên bản thư viện trùng binary name trong cùng host loader không?

**Trả lời:** Không. Lookup cùng host loader không tạo loader isolation; hai định nghĩa cùng tên trong một defining loader là xung đột.

**Câu 7.** Muốn tạo class ẩn không được tìm bằng tên thông thường, API nào thích hợp hơn?

**Trả lời:** `Lookup.defineHiddenClass(...)`, với điều kiện full privilege, cùng package và quy tắc hidden class tương ứng.

**Câu 8.** Trong named module, việc chỉ `exports` package đích có bảo đảm `privateLookupIn` từ mọi module khác được không?

**Trả lời:** Không. Cần xét `opens`, quyền caller và module readability theo MethodHandles API.

**Câu 9.** Nếu `Generated.say()` gây lỗi do dependency thiếu sau khi class đã được define, có nghĩa `defineClass` không hề tạo class không?

**Trả lời:** Không. Có thể định nghĩa class thành công nhưng resolution/initialization hoặc lời gọi sau đó gặp lỗi.

**Câu 10.** Tại sao không nên expose một Lookup có PACKAGE mode cho mọi plugin chưa xác thực?

**Trả lời:** Nó cho phép định nghĩa class mới trong runtime package/protection domain của lookup class. Đây là capability mạnh, không phải bộ lọc an toàn của bytecode.

## 17. Checklist khi triển khai sinh class động

- [ ] Xác định rõ lookup class và loader/module chủ sở hữu namespace.
- [ ] Chỉ tạo/ủy nhiệm Lookup có `PACKAGE` access cho thành phần đáng tin cậy.
- [ ] Kiểm tra binary name/package trong bytes; tránh xung đột tên đã định nghĩa.
- [ ] Xác minh nguồn artifact/generator, kích thước, class version và integrity phù hợp.
- [ ] Không coi magic header hoặc verifier là sandbox.
- [ ] Dùng classloader riêng nếu yêu cầu isolation, reload hoặc package khác.
- [ ] Phân biệt `defineClass`, `defineHiddenClass` và `ClassLoader.defineClass`.
- [ ] Trên JPMS, kiểm tra readability/exports/opens khi lấy Lookup vào package mục tiêu.
- [ ] Biết rằng defineClass không tự chạy static initializer; kiểm soát active use.
- [ ] Bắt/ghi nhận lỗi theo đúng nhóm: access, format, verification, linkage, initialization.
- [ ] Giới hạn số lượng class được sinh trong loader sống lâu, theo dõi Metaspace và cache.
- [ ] Có log lookup class, loader identity, module, binary name và lỗi phù hợp.
- [ ] Không cố redefine named class trùng tên bằng lookup cũ.
- [ ] Với bytecode không tin cậy, dùng mô hình phân quyền hoặc process isolation thích hợp.

## 18. Kết luận và tài liệu chính thức

`MethodHandles.Lookup.defineClass(byte[])` không đơn thuần là “đọc byte[] rồi tạo class”. Đây là API **định nghĩa class theo ngữ cảnh được ủy quyền**: lớp mới phải có tên trong **cùng package** với lookup class; dùng **cùng defining ClassLoader, runtime package và protection domain**; lookup phải có `PACKAGE` access; và **class initializer chưa chạy** sau lời gọi defineClass.

Nó phù hợp để sinh named helper class cộng tác với package hiện có. Khi cần một namespace riêng, nhiều package, isolation hoặc hot reload, hãy xem xét custom ClassLoader. Khi cần hidden class, hãy dùng API hidden-class tương ứng. Dù chọn cách nào, JVM vẫn cần class file hợp lệ và việc chạy code phải tuân theo trust model của ứng dụng.

### Tài liệu đối chiếu

- Java SE 17 `MethodHandles.Lookup` — `defineClass`, `defineHiddenClass`, lookup modes: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandles.Lookup.html
- Java SE 17 `MethodHandles` — `lookup()` và `privateLookupIn(...)`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandles.html
- Java SE 17 `ClassLoader` — `defineClass` và delegation: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- JVMS 17 §4 — The class File Format: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html
- JVMS 17 §5 — Loading, Linking and Initializing: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html
- JLS 17 §12.4 — Initialization of Classes and Interfaces: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4
- JLS 17 §12.7 — Unloading of Classes and Interfaces: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.7


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-class-definition-004">Hidden class và giới hạn khám phá bằng tên</a>

<details>
<summary>Xem chi tiết</summary>

# Hidden class và giới hạn khám phá bằng tên trong Java

## 1. Vấn đề: tạo được lớp tại runtime nhưng không thể gọi lại bằng tên?

Một số thư viện Java cần **tạo lớp động** để thực thi code được biên dịch, triển khai lambda, proxy, bộ chuyển đổi dữ liệu hoặc một đoạn logic có vòng đời ngắn. Những lớp này không nhất thiết cần được **công bố bằng tên** cho tất cả code khác trong JVM. Nếu đăng ký mọi lớp sinh ra vào namespace nạp lớp thông thường, việc nạp lại nhiều bản cùng tên, giữ metadata hoặc quản lý bộ nhớ có thể trở nên khó khăn.

Từ **Java 15**, nền tảng Java có cơ chế **hidden class** (JEP 371). API chủ đạo là:

~~~java
MethodHandles.Lookup hiddenLookup =
        MethodHandles.lookup().defineHiddenClass(classBytes, false);
Class<?> hiddenType = hiddenLookup.lookupClass();
~~~

**Hidden class vẫn là một class thật do JVM tạo và liên kết (link), nhưng không thể được tìm lại bằng các API tra lớp dựa trên tên như `Class.forName(String)` hoặc `ClassLoader.loadClass(String)`.** Bởi vậy, “ẩn” trong tên gọi không có nghĩa không thể lấy `Class<?>` hoặc gọi method của nó; nó có nghĩa class đó **không tham gia cơ chế khám phá bằng binary name thông thường**.

Đây là điểm khác biệt của chủ đề **Định nghĩa lớp động**:

- Ordinary class thường có **binary name** để loader lookup có thể tìm lại.
- Hidden class có **runtime name mang hậu tố định danh**, nhưng **không có binary name** dùng được như nominal reference trong constant pool của class khác.
- JVM có thể đặt hidden class vào **cùng loader, runtime package và protection domain** với lookup class, nhưng vẫn **không đăng ký tên đó như một ordinary class có thể `loadClass`**.
- Với hidden class, code tạo nên giữ `MethodHandles.Lookup`, `Class<?>` hoặc `MethodHandle`/instance để sử dụng thay vì lưu chuỗi name và cố tìm lại.
- Hidden class được tạo và **linked ngay**, còn việc **initialized** hay không phụ thuộc cờ `initialize` hoặc lần active use sau đó.
- Mặc định hidden class có quan hệ “weak” với defining loader để có thể được unload độc lập khi không còn tham chiếu cần thiết; `STRONG` thay đổi quy tắc giữ lớp.

### Mục tiêu của bài

Bạn sẽ hiểu khái niệm, API `defineHiddenClass`, lý do `getName()` không giúp tra lại class, khác biệt giữa hidden class và ordinary dynamic class, cách định nghĩa một hidden class từ bytecode Java 17, cách dùng method handle, các tùy chọn `NESTMATE`/`STRONG`, vòng đời, tính an toàn và giới hạn chẩn đoán.

## 2. Hai cách định nghĩa lớp động: ordinary và hidden

### 2.1. Ordinary class qua ClassLoader

Một loader tùy chỉnh có thể tạo ordinary class từ bytes bằng protected API:

~~~java
protected final Class<?> defineClass(
        String name, byte[] bytes, int offset, int length);
~~~

Lời gọi thật cần nằm **trong subclass của `ClassLoader`** có bytecode hợp lệ, đúng namespace và các điều kiện JVM. Với ordinary class:

- Class được định nghĩa với **binary name**, ví dụ `demo.dynamic.Task`.
- Việc load theo `ClassLoader.loadClass("demo.dynamic.Task")` có thể trả class đã được loader này hoặc parent định nghĩa, tùy loader policy.
- Một loader không thể định nghĩa **lại** class khác cùng tên đã được ghi nhận cho loader đó: cố define trùng có thể gây `LinkageError`.
- Ordinary class có quan hệ tồn tại mạnh với defining loader: class có thể được unload khi đủ điều kiện JVM và loader không còn reachable.

### 2.2. Hidden class qua MethodHandles.Lookup

Phương thức:

~~~java
MethodHandles.Lookup defineHiddenClass(
        byte[] bytes,
        boolean initialize,
        MethodHandles.Lookup.ClassOption... options)
        throws IllegalAccessException;
~~~

Khác biệt:

- Lời gọi nhận một class-file representation, **không** gọi `ClassLoader.defineClass` thông thường.
- Hidden class có **cùng defining loader**, **runtime package** và **protection domain** với **lookup class**.
- Lookup phải có **full privilege access** và bytecode class cần thuộc **cùng package** với lookup class. Không dùng một lookup ngẫu nhiên để inject class vào package tùy ý.
- Lớp được **tạo và liên kết**; cờ `initialize=true` yêu cầu initialization ngay sau đó, còn `false` thì không yêu cầu initializer chạy ngay.
- Phương thức trả **`Lookup` của chính hidden class**, không trực tiếp trả `Class<?>`. Muốn có class object dùng `hiddenLookup.lookupClass()`.
- Kết quả không được khai báo làm class mang binary name có thể khám phá bằng `loadClass`/`Class.forName`.
- Khi gọi `defineHiddenClass` nhiều lần với cùng bytes, bạn có thể thu được **nhiều hidden classes riêng**; không xảy ra cùng loại duplicate-definition theo binary name như ordinary class.

Bảng đối chiếu:

| Tiêu chí | Ordinary dynamic class | Hidden class |
|---|---|---|
| API điển hình | `ClassLoader.defineClass` / `Lookup.defineClass` | `Lookup.defineHiddenClass` |
| Có binary name dùng cho symbolic lookup | **Có** | **Không** |
| Có object `Class<?>` | Có | **Có** |
| `Class.isHidden()` | `false` | `true` |
| Gọi `Class.forName(name)` để tìm đúng class | Có thể, nếu loader thấy ordinary class | **Không thể** tìm hidden class theo name |
| `ClassLoader.loadClass(name)` | Có thể trả ordinary class phù hợp | Không tìm hidden class |
| Được gán defining loader | Có | Có, dựa trên lookup class |
| Có thể khởi tạo đối tượng/gọi method | Có | Có qua handle/`Class<?>` thích hợp |
| Có thể tạo nhiều class từ cùng bytes trong cùng loader | Tên trùng thường bị cấm | Có thể tạo nhiều hidden class độc lập |
| Unloading | Gắn với lifetime của defining loader | Mặc định có thể unload độc lập; `STRONG` thay đổi |
| Dùng làm loại được nhắc từ constant pool của class khác | Có thể khi accessible | Không thể tham chiếu hidden class bằng nominal type name |

**Không nhầm “hidden” với “private”.** `private` là modifier về quyền truy cập Java, còn `hidden class` là loại class đặc biệt về **cách định nghĩa và khả năng được tham chiếu bằng tên**. Hidden class không biến toàn bộ method của nó thành private, cũng không tự cho caller quyền đọc private member bất kỳ.

## 3. Hidden class có tên, nhưng không có binary name

### 3.1. Tại sao `getName()` vẫn trả String?

Một hidden class có thể tạo từ bytecode ban đầu mang tên `demo.HiddenTask`. JVM gắn hậu tố độc nhất vào runtime name, ví dụ **hình thức minh họa**:

~~~text
demo.HiddenTask/0x00007fxxxxxxxxxx
~~~

Hậu tố thực tế thay đổi theo phiên bản JVM/lượt chạy. Không được hardcode, serialize rồi coi nó là một tên có thể tra lại.

Các API liên quan:

~~~java
Class<?> hidden = hiddenLookup.lookupClass();

System.out.println(hidden.isHidden());          // true
System.out.println(hidden.getName());           // chứa runtime suffix
System.out.println(hidden.descriptorString());  // có dạng riêng cho hidden type
System.out.println(hidden.describeConstable()); // Optional.empty()
~~~

**Điểm dễ nhầm:** `Class.getName()` trả một **chuỗi mô tả runtime class**, không phải lời hứa mọi chuỗi trả về đều là **binary name hợp lệ** để dùng với `Class.forName`. Với hidden class, chuỗi `getName()` chứa dấu `/` ngăn original name và suffix, nên không phải binary name thông thường.

`Class.descriptorString()` của hidden class cũng có dạng đặc biệt chứa suffix, **không phải nominal field descriptor** có thể ghi trong constant pool của class khác. `Class.describeConstable()` trả `Optional.empty()` vì hidden class không có mô tả danh nghĩa để được resolve bằng tên trong constant pool.

### 3.2. Những API tìm bằng tên không tìm hidden class

Theo Javadoc của `Lookup.defineHiddenClass`, hidden class không được khám phá bằng:

- `Class.forName(String)`, cũng như `Class.forName(String, boolean, ClassLoader)`.
- `ClassLoader.loadClass(String)` / `loadClass(String, boolean)`.
- `MethodHandles.Lookup.findClass(String)`.

Lý do **không phải** loader không có reference đến hidden class, hay class chưa initialized. JVM cố ý **không đặt hidden class trong bảng tìm ordinary classes bằng binary name**.

Sơ đồ:

~~~text
bytes từ demo/HiddenTask.class
   |
   v
hostLookup.defineHiddenClass(bytes, false)
   |
   +--> hiddenLookup.lookupClass() -> Class<?> (isHidden=true)
   |         |
   |         +--> findStatic / findVirtual / findConstructor
   |         +--> reflection khi có Class<?> và quyền phù hợp
   |
   +--> Class.forName(hidden.getName()) -----------> KHÔNG TÌM THẤY
   +--> hostLoader.loadClass(hidden.getName()) ----> KHÔNG TÌM THẤY
~~~

**Không nên dùng `ClassNotFoundException` làm bằng chứng hidden class chưa từng tồn tại.** Class có thể đã được tạo, được linked và đã được một `MethodHandle` gọi thành công — chỉ là không có đường tra từ tên.

### 3.3. Cũng không phải superclass/type trong chữ ký danh nghĩa của class khác

Vì hidden class không có binary name có thể ghi vào constant pool, class khác **không thể nhắc trực tiếp hidden class** bằng symbolic reference như một superclass, field type, parameter type hoặc return type. Đây là giới hạn cố ý của hidden class, không phải lỗi Java reflection.

Code có `Object obj` trỏ đến instance của hidden class vẫn hợp lệ. Code cũng có thể dùng **interface/ordinary type** làm contract, miễn bytecode và access rules cho phép. Điều bị giới hạn là **tham chiếu danh nghĩa đến hidden type**, chứ không phải mọi cách trao đổi object.

### 3.4. Không nhầm với anonymous class, local class và lambda

| Loại | Có thể là hidden class? | Khám phá bằng tên |
|---|---|---|
| Nested class biên dịch thông thường `Outer$Inner` | Không chỉ vì nó nested | Ordinary binary name thường dùng `$` |
| Anonymous/local class do `javac` tạo | Thường là ordinary class trong bytecode | Có binary name theo quy tắc compiler; tên không nên coi là API ổn định |
| Lambda implementation | JVM/compiler có thể dùng hidden classes như chi tiết triển khai | Không nên phụ thuộc tên/số lượng lớp phát sinh |
| Hidden class do `defineHiddenClass` | **Có** theo định nghĩa | Không thể tra lại qua `Class.forName`/`loadClass` |

Không lấy cách một bản JDK hiện thực lambda làm hợp đồng bảo đảm mọi Java runtime/phiên bản dùng đúng cùng tên hay class shape. `Hidden class` là cơ chế JVM/API, không phải đồng nghĩa mọi nested/anonymous types đều hidden.

## 4. Điều kiện để tạo hidden class đúng

### 4.1. Dùng Lookup có đủ đặc quyền

~~~java
MethodHandles.Lookup lookup = MethodHandles.lookup();
MethodHandles.Lookup generated =
        lookup.defineHiddenClass(bytes, false);
~~~

Nếu gọi `MethodHandles.lookup()` từ một method của class bình thường, kết quả thường có full privilege access với chính lookup class đó. Nhưng một `publicLookup()` không có toàn bộ quyền tương ứng để dùng `defineHiddenClass`.

Nếu `Lookup` thiếu full privilege access, phương thức có thể ném **`IllegalAccessException`**. Việc cố dùng `privateLookupIn` qua module boundaries cũng chịu kiểm tra `opens`/access và không tự cấp đặc quyền không được phép.

### 4.2. Bytecode phải thuộc cùng package với lookup class

Nếu lookup class là `demo.HiddenHost`, class bytes đầu vào phải khai báo class trong package `demo` (ví dụ `demo.HiddenTask`). Đừng truyền classfile mang tên `other.pkg.Task` và mong `defineHiddenClass` tạo hidden class ở package khác: API sẽ từ chối theo điều kiện package.

Hidden class nhận:

- **Defining ClassLoader** của lookup class.
- **Runtime package** phù hợp lookup class.
- **ProtectionDomain** của lookup class.
- Runtime **module** tương ứng; nó không tự tạo named module mới.

Tất cả vẫn phải tuân theo kiểm tra class-file format, package/access/module và các quy tắc verification/linking.

### 4.3. Class bytes thật, không phải tên/đường dẫn

`defineHiddenClass` nhận **`byte[]` biểu diễn một class file**, không nhận:

~~~text
"demo.HiddenTask"
"src/demo/HiddenTask.java"
"demo/HiddenTask.class"
~~~

Các chuỗi trên là tên/path, không phải nội dung classfile. Bạn có thể đọc từ file đã biên dịch hoặc tạo bytes bằng bytecode generator phù hợp. Khi tự sinh bytecode, phải bảo đảm **classfile version**, format và các constant pool entries hợp lệ.

### 4.4. Link và initialize là hai thời điểm khác nhau

Hai lời gọi:

~~~java
lookup.defineHiddenClass(bytes, false);
lookup.defineHiddenClass(bytes, true);
~~~

đều **tạo và link** hidden classes (hai definition riêng nếu gọi hai lần). Cờ boolean:

- `false`: không yêu cầu chạy `<clinit>` ngay. Hidden class sẽ initialize khi có active use sau đó.
- `true`: JVM initialize hidden class sau khi link, theo hợp đồng phương thức.

Phân biệt với `Class.forName(Module, String)` vốn có quy tắc **không tự link hoặc initialize** trong overload theo module. Đừng suy từ một API sang mọi cách định nghĩa lớp động.

## 5. Ví dụ Java 17+ độc lập: định nghĩa được nhưng tra theo tên thất bại

Thực hành được thiết kế để **loại hoàn toàn class template khỏi runtime application classpath**. `HiddenHost` sẽ đọc bytes của `HiddenTask.class` như dữ liệu, gọi `defineHiddenClass`, giữ kết quả và gọi method bằng `MethodHandle`.

### 5.1. Cây thư mục

~~~text
demo/
  src/
    template/
      demo/
        HiddenTask.java
    app/
      demo/
        HiddenHost.java
  build/
    template/
      demo/
        HiddenTask.class
    app/
      demo/
        HiddenHost.class
~~~

**Quy tắc để demo chính xác:** lệnh `java` chỉ dùng `-cp build/app`. Không thêm `build/template` vào classpath; nếu có, `Class.forName("demo.HiddenTask")` có thể tìm được **ordinary template class**, gây hiểu nhầm rằng nó đã tìm thấy hidden class.

### 5.2. Tệp `src/template/demo/HiddenTask.java`

~~~java
package demo;

class HiddenTask {
    static {
        System.out.println("TASK_INITIALIZED");
    }

    static String message() {
        return "HELLO_HIDDEN";
    }
}
~~~

Lớp template là package-private. `static {}` giúp quan sát thời điểm initialization; `message()` trả kết quả dễ kiểm tra.

Dù source ở một cây khác, package vẫn là `demo` để thỏa điều kiện cùng package với lookup class của `HiddenHost`.

### 5.3. Tệp `src/app/demo/HiddenHost.java`

~~~java
package demo;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HiddenHost {
    public static void main(String[] args) throws Throwable {
        byte[] bytes = Files.readAllBytes(
                Path.of("build/template/demo/HiddenTask.class"));
        MethodHandles.Lookup hostLookup = MethodHandles.lookup();

        MethodHandles.Lookup hiddenLookup =
                hostLookup.defineHiddenClass(bytes, false);
        Class<?> hidden = hiddenLookup.lookupClass();

        System.out.println("hidden=" + hidden.isHidden());
        System.out.println("sameLoader="
                + (hidden.getClassLoader()
                    == HiddenHost.class.getClassLoader()));
        System.out.println("samePackage="
                + hidden.getPackageName().equals(
                    HiddenHost.class.getPackageName()));
        System.out.println("nameHasSlash="
                + hidden.getName().contains("/"));
        System.out.println("descriptorHasSlash="
                + hidden.descriptorString().contains("/"));
        System.out.println("noNominalDescriptor="
                + hidden.describeConstable().isEmpty());

        try {
            Class.forName(hidden.getName());
            System.out.println("forNameFound=true");
        } catch (ClassNotFoundException expected) {
            System.out.println("forNameFound=false");
        }

        try {
            HiddenHost.class.getClassLoader().loadClass(hidden.getName());
            System.out.println("loadClassFound=true");
        } catch (ClassNotFoundException expected) {
            System.out.println("loadClassFound=false");
        }

        Class<?> another =
                hostLookup.defineHiddenClass(bytes, false).lookupClass();
        System.out.println("twoDefinitionsDistinct="
                + (another != hidden));

        MethodHandle mh = hiddenLookup.findStatic(
                hidden, "message", MethodType.methodType(String.class));

        System.out.println("beforeInvoke");
        String value = (String) mh.invokeExact();
        System.out.println("result=" + value);
    }
}
~~~

**Vì sao có `throws Throwable`?** `MethodHandle.invokeExact` có thể ném `Throwable` theo signature. Việc khai báo ở `main` nhằm giữ demo rõ, không phải khuyến nghị mọi application entry point production đều `throws Throwable`.

### 5.4. Biên dịch/chạy trên Linux và macOS

Đứng tại thư mục `demo`:

~~~sh
mkdir -p build/template build/app

javac --release 17 -d build/template \
    src/template/demo/HiddenTask.java

javac --release 17 -d build/app \
    src/app/demo/HiddenHost.java

java -cp build/app demo.HiddenHost
~~~

Trên Windows PowerShell:

~~~powershell
New-Item -ItemType Directory -Force build/template, build/app | Out-Null
javac --release 17 -d build/template src/template/demo/HiddenTask.java
javac --release 17 -d build/app src/app/demo/HiddenHost.java
java -cp build/app demo.HiddenHost
~~~

Không dùng cùng một output folder cho template và app: hai cây biên dịch khác nhau giúp chứng minh **ordinary class không xuất hiện trên runtime classpath**.

### 5.5. Stdout chính xác đã kiểm chứng

~~~text
hidden=true
sameLoader=true
samePackage=true
nameHasSlash=true
descriptorHasSlash=true
noNominalDescriptor=true
forNameFound=false
loadClassFound=false
twoDefinitionsDistinct=true
beforeInvoke
TASK_INITIALIZED
result=HELLO_HIDDEN
~~~

Đây là stdout của chương trình được biên dịch với `javac --release 17` và chạy trên một JDK có hỗ trợ Java 17.

### 5.6. Ý nghĩa từng kết quả

| Output | Diễn giải |
|---|---|
| `hidden=true` | `Class.isHidden()` nhận diện class do `defineHiddenClass` tạo |
| `sameLoader=true` | Hidden class gắn với defining loader của `HiddenHost` |
| `samePackage=true` | Hidden class thuộc package `demo` của lookup class |
| `nameHasSlash=true` | `getName()` có hậu tố runtime, **không phải binary name** |
| `descriptorHasSlash=true` | Descriptor của hidden class có dấu phân tách đặc biệt, không dùng như nominal descriptor |
| `noNominalDescriptor=true` | `describeConstable()` trống, không có nominal constant description |
| `forNameFound=false` | Không thể tìm lại hidden class bằng `Class.forName(hidden.getName())` |
| `loadClassFound=false` | `ClassLoader.loadClass(hidden.getName())` cũng không tìm được nó |
| `twoDefinitionsDistinct=true` | Cùng byte array tạo hai hidden classes **khác identity** |
| `beforeInvoke` | Mốc thời gian sau create/link với `initialize=false` |
| `TASK_INITIALIZED` | Method handle invoke static method đã tạo active use, kích hoạt initialization |
| `result=HELLO_HIDDEN` | Hidden class vẫn thực thi code bình thường khi có handle phù hợp |

**Điều không nên suy luận:** `forNameFound=false` không có nghĩa `HiddenTask.class` bytes không được đọc hoặc class chưa linked. Chúng ta đã có `Class<?>`, tìm được method handle và chạy method.

### 5.7. Tại sao `Class.forName("demo.HiddenTask")` có thể gây hiểu sai?

Nếu bạn vô tình để `build/template` trong application classpath:

~~~sh
java -cp "build/app:build/template" demo.HiddenHost
~~~

trên Linux/macOS (Windows dùng `;`), JVM có thể nạp **ordinary class** `demo.HiddenTask` bằng tên. Tuy nhiên ordinary class đó **khác** hidden class được `defineHiddenClass` tạo từ cùng bytes. Đừng dùng phép thử `Class.forName("demo.HiddenTask")` để khẳng định hidden class đã trở nên khám phá được.

Phép thử đúng nhất là sử dụng chính `hidden.getName()` rồi chứng minh API tra tên thông thường vẫn thất bại; hoặc so trực tiếp `ordinaryClass != hiddenClass` nếu cả hai tồn tại.

### 5.8. Các biến thể để tự kiểm chứng

**Biến thể A — `initialize=true`:** thay `defineHiddenClass(bytes, false)` đầu tiên thành `defineHiddenClass(bytes, true)`. Dòng `TASK_INITIALIZED` xuất hiện trong lời gọi define, **trước** chuỗi output `hidden=true`; sau đó invocation không initialize lại class.

**Biến thể B — Không invoke method handle:** bỏ dòng `mh.invokeExact()`. Với `initialize=false`, nếu không có active use khác, `TASK_INITIALIZED` không xuất hiện trong demo.

**Biến thể C — Thêm nhiều lượt define:** tạo thêm nhiều hidden classes từ cùng bytes, so `Class<?>` identities. Chúng độc lập ngay cả khi cùng lookup class, original classfile name và defining loader.

**Biến thể D — Sai package:** biên dịch template thuộc `foreign.HiddenTask` nhưng giữ `HiddenHost` ở `demo`. `defineHiddenClass` sẽ từ chối input không cùng package; không dùng tên động để đi vòng điều kiện access.

**Biến thể E — Không có full privilege lookup:** thử `MethodHandles.publicLookup().defineHiddenClass(bytes, false)`. Đây là ví dụ về lookup thiếu đặc quyền, dự kiến thất bại bằng `IllegalAccessException` theo hợp đồng API.

## 6. Các tùy chọn ClassOption: NESTMATE và STRONG

API nhận varargs của enum `MethodHandles.Lookup.ClassOption`:

~~~java
import static java.lang.invoke.MethodHandles.Lookup.ClassOption.NESTMATE;
import static java.lang.invoke.MethodHandles.Lookup.ClassOption.STRONG;

MethodHandles.Lookup hiddenNestmate =
        MethodHandles.lookup().defineHiddenClass(bytes, false, NESTMATE);
MethodHandles.Lookup hiddenStrong =
        MethodHandles.lookup().defineHiddenClass(bytes, false, STRONG);
~~~

Đây là **snippet minh họa trong class/method phù hợp**, cần `bytes` có sẵn và điều kiện access/package hợp lệ. Hai lượt define tạo hai hidden classes riêng, không biến class đầu thành nestmate/strong sau khi tạo.

### 6.1. `NESTMATE`: được đưa vào nest của lookup class

Java nestmates là các class/interface cùng một **nest** được phép truy cập private members theo quy tắc JVM.

Nếu truyền `NESTMATE`, hidden class được thêm vào nest của lookup class. Vì vậy hidden class có thể truy cập private members của các class/interface cùng nest **theo access rules của nestmate**.

Nếu **không** chọn `NESTMATE`, hidden class mặc định có nest của riêng nó dù cùng package/loader. Do đó:

~~~text
Same package + same defining loader
    ≠ tự động được truy cập private member của HiddenHost.

NESTMATE
    => hidden class tham gia nest phù hợp để áp dụng nestmate private access.
~~~

Đừng hiểu `NESTMATE` cho phép mở tùy ý private members của module/lớp khác. Nest membership và lookup privileges vẫn bị kiểm tra.

### 6.2. `STRONG`: liên kết vòng đời mạnh với defining loader

Thông thường ordinary class giữ quan hệ mạnh với defining loader; class chỉ được unload khi loader có thể bị thu gom theo điều kiện JVM.

Hidden class **mặc định** có thể được unload ngay cả khi defining loader vẫn reachable, **nếu không còn tham chiếu giữ hidden class đó sống**. Lý do: các framework có thể tạo nhiều lớp tạm trong một loader sống lâu.

Nếu chọn `STRONG`:

- Hidden class được gắn quan hệ mạnh với defining loader **giống ordinary class**.
- Nó thường không được unload độc lập khi loader vẫn reachable.
- Điều này hữu ích với hidden class cần cùng vòng đời với lớp/loader host, nhưng có thể làm **metadata retention** cao hơn nếu tạo rất nhiều definitions.
- Chọn lúc define, không thể đổi chế độ sau khi đã tạo class.

**Không có bảo đảm GC sẽ unload một hidden class ngay khi bạn bỏ `Class<?>` reference.** Cơ chế class unloading phụ thuộc reachability, handles, instances, threads, JVM và các tham chiếu khác. `STRONG` điều chỉnh khả năng unload chứ không phải là lệnh trực tiếp gọi GC.

### 6.3. Kết hợp options

~~~java
MethodHandles.Lookup lk = MethodHandles.lookup().defineHiddenClass(
        bytes, false, NESTMATE, STRONG);
~~~

Có thể kết hợp để hidden class vừa tham gia nest của lookup class, vừa có strong relationship với loader. Chỉ dùng khi cần đúng hai thuộc tính đó; không bật mặc định chỉ vì muốn “khỏi lỗi không tìm thấy theo tên” — `STRONG` và `NESTMATE` **không** làm hidden class trở thành ordinary class khám phá được.

## 7. Sử dụng hidden class mà không cần tên

### 7.1. Giữ `Lookup` do API trả về

Đây là con đường thường rõ nhất:

~~~java
MethodHandles.Lookup hiddenLookup =
        hostLookup.defineHiddenClass(bytes, false);
Class<?> hiddenClass = hiddenLookup.lookupClass();

MethodHandle run = hiddenLookup.findStatic(
        hiddenClass, "message", MethodType.methodType(String.class));
String value = (String) run.invokeExact();
~~~

Đây là snippet cần nằm trong method có xử lý checked exceptions và `Throwable` phù hợp. `hiddenLookup` là lookup trên chính hidden class, nên có khả năng thực hiện các `find...` operations theo quyền của lookup đó.

Không lập “registry bằng `getName()` rồi `Class.forName`” để truy xuất hidden class. Nếu framework cần registry, lưu **tham chiếu class/lookup/method handle hoặc object** với lifecycle được quản lý. Chú ý rằng giữ tham chiếu mạnh cũng ngăn class được unload theo kỳ vọng.

### 7.2. Truyền object qua ordinary interface

Hidden class có thể triển khai một interface thông thường được host biết. Host nhận và dùng object qua interface đó mà **không cần biết tên hidden class**.

~~~java
public interface Operation {
    String run(String input);
}
~~~

Snippet trên là khai báo interface minh họa. Để triển khai bằng hidden class phải có bytecode thỏa contract, lookup/module/package/access thích hợp và logic khởi tạo instance. Đây là hướng phù hợp cho generated adapters hoặc lambda-like runtime objects.

Khi dùng module/classloader boundaries, interface contract vẫn cần giữ đúng runtime type identity; hidden class không loại bỏ các quy tắc định danh kiểu của JVM.

### 7.3. Reflection vẫn có thể dùng nếu có Class object

`Class.isHidden() == true` không có nghĩa Java reflection bị cấm hoàn toàn. Nếu code đang giữ `Class<?> hidden`, nó có thể gọi:

~~~java
java.lang.reflect.Method method = hidden.getDeclaredMethod("message");
~~~

và dùng reflection khi Java/JPMS access checks cho phép. Tuy nhiên `getDeclaredMethod` lấy metadata khác với khả năng setAccessible/invoke. Việc cố tìm `hidden` từ một string name bằng `Class.forName` mới là điều không được hỗ trợ.

Nói ngắn: **hidden class là non-discoverable by name, không phải invisible to all reflection**.

### 7.4. Class data khi cần state dành riêng cho class

Java còn có:

~~~java
MethodHandles.Lookup defineHiddenClassWithClassData(
        byte[] bytes, Object classData, boolean initialize,
        MethodHandles.Lookup.ClassOption... options)
~~~

API này tạo hidden class kèm class data, hữu ích cho frameworks cần truyền metadata/state để bytecode sinh ra truy cập theo cơ chế method handles/class data. Nó khác với việc gắn tên/JAR path cho class, và không biến hidden class thành ordinary class. Cần đọc hợp đồng của `MethodHandles.classData`/`classDataAt` trước khi thiết kế, nhất là khi state mutable.

Phạm vi bài chính là **khám phá bằng tên**, nên API class data được nhắc như một khả năng mở rộng chứ không thay ví dụ cơ bản.

## 8. Vòng đời, memory và cách tổ chức framework

### 8.1. Tại sao JVM thiết kế hidden class có khả năng unload riêng?

Các framework có thể sinh rất nhiều implementations rất nhỏ: adapters cho từng kiểu call site, serializer/deserializer theo schema, proxies hoặc lambda implementations. Nếu mỗi class phải tồn tại tới khi application ClassLoader dài hạn được thu hồi, metaspace có thể giữ nhiều class không còn dùng nữa.

Với hidden class mặc định, JVM được phép unload khi không còn reference cần thiết dù loader vẫn sống. Đây là lợi thế trong môi trường nạp lớp động quy mô lớn. Nhưng nếu framework cache mọi `Lookup`/`Class<?>` trong static map vĩnh viễn, lợi thế ấy gần như mất đi.

### 8.2. Những tham chiếu giữ class sống

Một hidden class có thể vẫn reachable qua:

- `Class<?>` object hoặc `MethodHandles.Lookup` giữ reference.
- `MethodHandle`, `MethodType`, `CallSite` và các cấu trúc liên quan.
- Instance của hidden class.
- Callback/caches/static registries trong framework.
- Thread đang thực thi code hoặc đang giữ object liên quan.
- Các internal references còn được JVM duy trì theo hoạt động runtime.

**Đừng cố dự đoán thời điểm unload chỉ dựa vào lời gọi `System.gc()`**. Không có output cố định đáng tin cho phép thử unload bằng GC ở một chương trình nhỏ.

### 8.3. Hidden class không thay thế custom ClassLoader trong mọi bài toán

Hidden class phù hợp với implementation **nội bộ**, cần giữ handle và không cần external symbolic discoverability. Nếu bạn muốn:

- Plugin class được nạp từ JAR bằng binary name.
- Framework tìm class từ classpath/service descriptor.
- Class được export, tham chiếu trong bytecode của class khác như type thông thường.
- Quản lý nhiều module/plugin boundaries với đầy đủ classpath/module path.

thì ordinary class, URLClassLoader, ServiceLoader hoặc module layer có thể thích hợp hơn. Hidden class không thay thế SPI/plugin architecture tiêu chuẩn chỉ bằng đổi API define.

### 8.4. Bảo mật và mã không tin cậy

`defineHiddenClass` yêu cầu `Lookup` đủ quyền và ràng buộc package/module. Tuy nhiên, “hidden” **không có nghĩa sandbox** cho bytecode không tin cậy. Code vẫn có thể chạy trong process với quyền của ứng dụng, tạo tác dụng phụ khi initialize hoặc invoke, dùng CPU/memory, gọi API được phép.

Nếu bytecode đến từ nguồn bên ngoài, phải xác thực/protect inputs, giới hạn artifact provenance và cân nhắc process isolation; không giao privileged lookup tùy ý cho plugin không tin cậy.

## 9. Chẩn đoán lỗi thường gặp

| Hiện tượng | Nguyên nhân hay gặp | Hướng kiểm tra |
|---|---|---|
| `Class.forName(hidden.getName())` ném `ClassNotFoundException` | **Đúng bản chất hidden class**: không discoverable by name | Giữ `Lookup`/`Class<?>`/MethodHandle, đừng cố tra lại |
| `ClassLoader.loadClass(hidden.getName())` thất bại | Hidden class không có binary name để loader lookup | Không coi việc có loader giống nhau là đủ |
| `defineHiddenClass` ném `IllegalAccessException` | Lookup không có full privilege access | Kiểm tra `lookupModes`, nguồn tạo `Lookup`, JPMS permissions |
| `defineHiddenClass` ném `IllegalArgumentException` | Bytecode class không cùng package hoặc argument/option không hợp lệ | Kiểm tra internal classfile name và lookup package |
| `ClassFormatError` hoặc `VerifyError` | Bytecode classfile không hợp lệ hoặc sai verify rules | Kiểm tra bytes, constant pool, classfile version và generator |
| `UnsupportedClassVersionError` | Bytes được biên dịch cho JVM mới hơn runtime | `java -version`, `javac --release` |
| `LinkageError` khi liên kết | Dependency/loading constraints/verification linkage | Ghi cause; không mặc định do “tên hidden trùng” |
| `ExceptionInInitializerError` khi `initialize=true` hoặc active use | `<clinit>` thực thi và thất bại | Kiểm tra static initializer và stack trace gốc |
| Method handle lookup bị `NoSuchMethodException` | Method name/signature không khớp | Kiểm tra `MethodType` và declared method |
| Method handle access bị `IllegalAccessException` | Lookup không có quyền tương ứng | Dùng lookup hợp lệ, không tự mở quyền |
| `ClassCastException` khi truyền qua boundary | Sai interface identity/loader hoặc cast logic | So `Class<?>` của API chung |
| Metaspace/class retention tăng | Cache vô hạn, `STRONG` không phù hợp hoặc objects giữ class | Review references, handles và lifecycle |
| Reflection thấy class nhưng `Class.forName` vẫn fail | Reflection có sẵn `Class<?>`; name lookup không thể tìm hidden | Đây là hai cơ chế khác nhau |
| Code phụ thuộc suffix `/0x...` bị hỏng khi đổi JDK | Suffix runtime không phải format persistence chuẩn | Không dùng suffix làm stable identifier |

### 9.1. Phân biệt three layers of failure

~~~text
(1) File/bytes:
    source .java -> compiled .class byte[]
    có thể IOException / ClassFormatError / UnsupportedClassVersionError

(2) Define/link:
    MethodHandles.Lookup.defineHiddenClass(...)
    có thể IllegalAccessException / IllegalArgumentException / LinkageError

(3) Sử dụng:
    Lookup/Class<?>/MethodHandle/instance
    có thể NoSuchMethodException / IllegalAccessException
    hoặc lỗi khi initialize/invoke
~~~

**Không tạo thêm bước bắt buộc `Class.forName` ở giữa (2) và (3).** Hidden class được trả trực tiếp bằng lookup — name-based lookup không phải cách để lấy reference.

### 9.2. Cách ghi log phù hợp

Có thể ghi:

~~~java
System.out.println("hidden=" + type.isHidden());
System.out.println("runtimeName=" + type.getName());
System.out.println("loader=" + type.getClassLoader());
System.out.println("module=" + type.getModule());
~~~

Đây là snippet minh họa, cần `type` có sẵn. Log về runtime name hữu ích để chẩn đoán nhưng không phải identifier bền vững. Khi cần chứng minh tính tương thích hoặc danh tính runtime, so `Class<?>` reference, defining loader, module và thông tin nguồn bytecode tin cậy.

## 10. Những ngộ nhận cần tránh

1. **“Hidden class không có `Class<?>` object.”** Sai: `hiddenLookup.lookupClass()` trả về class.
2. **“Vì có `getName()` nên có thể `Class.forName`.”** Sai: runtime name không phải binary name.
3. **“Cứ thêm classfile vào classpath thì `Class.forName(hidden.getName())` sẽ tìm được hidden class.”** Sai: nếu có ordinary class template, nó là class khác.
4. **“Hidden class không được khởi tạo/chạy code.”** Sai: có thể initialize và invoke bình thường.
5. **“`initialize=false` nghĩa là không link class.”** Sai: defineHiddenClass tạo và link; chỉ hoãn initialization.
6. **“Hidden class có thể dùng trực tiếp làm superclass được class khác tham chiếu bằng tên.”** Sai: không có binary name cho nominal symbolic reference.
7. **“Cùng defining loader nghĩa là cùng class identity.”** Sai: hidden classes được tạo riêng bằng cùng bytes vẫn là các `Class<?>` riêng.
8. **“Nếu không chọn NESTMATE thì hidden class vẫn tự truy cập private fields của host.”** Sai: cùng package không đủ quyền private.
9. **“Chọn STRONG sẽ làm `Class.forName` tìm hidden class.”** Sai: STRONG ảnh hưởng lifecycle, không ảnh hưởng discoverability.
10. **“Hidden class luôn bị unload ngay khi code thoát khỏi method.”** Sai: các references và GC quyết định.
11. **“Hidden class có thể được dùng thay mọi plugin class bằng tên.”** Sai: plugin discovery/SPI có contract riêng.
12. **“`isHidden()` sẽ true với mọi anonymous/local/nested class.”** Sai: những classes thường là ordinary types; phải xét API JVM tạo class.
13. **“Hidden class là biện pháp bảo mật code untrusted.”** Sai: lookup privilege và name hiding không tạo security sandbox.
14. **“Đọc một chuỗi suffix duy nhất là cách lưu identity lâu dài.”** Sai: runtime name không có ngữ nghĩa persistence ổn định.
15. **“Reflection tuyệt đối không thể xem hidden class.”** Sai nếu code đang có `Class<?>` và đủ quyền.
16. **“`Class.forName(Module, name)` có thể tìm hidden class vì đã truyền đúng module.”** Sai: named-module lookup không phá giới hạn non-discoverable by name của hidden class.

## 11. Câu hỏi và bài tập tự kiểm tra

1. `Lookup.defineHiddenClass` được đưa vào Java từ phiên bản nào? Nó trả `Lookup` hay `Class<?>`?
2. Tại sao `Class.getName()` của hidden class có giá trị nhưng `Class.forName` vẫn không tìm được?
3. Sự khác nhau giữa **runtime name** và **binary name** trong trường hợp hidden class là gì?
4. `Class.isHidden()` trả gì cho ordinary class và hidden class?
5. Tại sao `Class.describeConstable()` của hidden class là `Optional.empty()`?
6. `Class.descriptorString()` của hidden class có thể dùng làm nominal descriptor được ghi vào constant pool của class khác không?
7. Hidden class có defining ClassLoader không? Nó lấy loader và runtime package từ đâu?
8. Vì sao bytecode đưa vào `defineHiddenClass` phải khai báo cùng package với lookup class?
9. `MethodHandles.publicLookup()` có đủ quyền định nghĩa hidden class không?
10. `defineHiddenClass(bytes, false)` có tự link class không? Nó có chạy `<clinit>` ngay không?
11. Tại sao dòng `TASK_INITIALIZED` trong demo xuất hiện sau `beforeInvoke`?
12. Vì sao `Class.forName("demo.HiddenTask")` đôi lúc vẫn có thể thành công nếu vô tình để template trên app classpath, nhưng không chứng minh hidden class được tìm?
13. Hai lần defineHiddenClass với cùng bytes có tạo cùng object `Class<?>` không?
14. `NESTMATE` làm thay đổi điều gì về quyền private access?
15. `STRONG` ảnh hưởng discoverability hay vòng đời class?
16. Vì sao hidden class mặc định có thể unload khi defining loader vẫn sống?
17. Làm sao gọi static method của hidden class nếu không thể tìm class lại bằng string name?
18. Phân biệt dùng `MethodHandle` và dùng Java reflection khi đã có `Class<?>` của hidden class.
19. Khi thiết kế generated adapters, vì sao cần giữ interface API chung thay vì cố xuất hidden type ra toàn hệ?
20. Một plugin loader cần tìm class theo binary name để nạp extension từ JAR: hidden class có phải lựa chọn mặc định phù hợp không? Vì sao?
21. Nếu `defineHiddenClass` ném `IllegalAccessException`, vấn đề thường nằm ở lookup privileges hay ở tên class không tồn tại trong classpath?
22. Tại sao `ClassNotFoundException` từ `loadClass(hidden.getName())` không chứng minh hidden class chưa từng chạy?
23. Việc giữ mọi `MethodHandle` trong static cache vĩnh viễn có thể ảnh hưởng gì tới unloading?
24. Cần làm gì nếu bytecode do bên thứ ba không tin cậy cung cấp?

**Tiêu chí hoàn thành:** chạy đúng ví dụ Java 17 và giải thích đủ 12 dòng stdout; nắm rõ giới hạn tra cứu bằng tên, biết giữ `Lookup`/`Class<?>`/`MethodHandle`; phân biệt ordinary/hidden classes, initialize/link, NESTMATE/STRONG và vòng đời class.

## 12. Tài liệu chính thức

- [Java SE 21 — `MethodHandles.Lookup#defineHiddenClass`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/invoke/MethodHandles.Lookup.html#defineHiddenClass(byte%5B%5D,boolean,java.lang.invoke.MethodHandles.Lookup.ClassOption...)): nguồn chuẩn về cách định nghĩa hidden class, name lookup, initialize, access, unload.
- [Java SE 21 — `MethodHandles.Lookup.ClassOption`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/invoke/MethodHandles.Lookup.ClassOption.html): `NESTMATE` và `STRONG`.
- [Java SE 21 — `Class.isHidden()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#isHidden()): phát hiện hidden class.
- [Java SE 21 — `Class.descriptorString()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#descriptorString()): descriptor string dành cho runtime type.
- [OpenJDK JEP 371 — Hidden Classes](https://openjdk.org/jeps/371): động lực thiết kế và giới hạn non-discoverable classes.
- [JVMS Java SE 21 — Chapter 5](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html): creation, loading, linking, initialization và runtime identity.

**Kết luận:** hidden class không biến mất khỏi JVM và cũng không đồng nghĩa “private class”. Nó là một class động **không có binary name để khám phá theo tên**. Hãy giữ một handle/reference để sử dụng, thiết kế lifetime hợp lý và dùng ordinary class khi hệ thống thực sự cần nạp hoặc tham chiếu class bằng binary name.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-class-definition-005">Phân biệt bootstrap class loader với bootstrap method của invokedynamic</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt bootstrap class loader với bootstrap method của invokedynamic

Trong Java có hai thuật ngữ đều chứa chữ **bootstrap**, nhưng nói về **hai cơ chế JVM hoàn toàn khác nhau**:

1. **Bootstrap class loader** (bộ nạp lớp bootstrap) là thành phần của cơ chế **nạp và định nghĩa class**: nó chịu trách nhiệm cho nhiều lớp nền tảng của Java runtime, chẳng hạn `java.lang.String` và `java.lang.invoke.LambdaMetafactory` trên JDK 17 thông thường. Trong API `Class.getClassLoader()`, lớp do bootstrap class loader định nghĩa được biểu diễn bởi `null`.
2. **Bootstrap method (BSM) của `invokedynamic`** là một **phương thức hoặc method handle dùng để liên kết một dynamic call site**. JVM gọi nó trong quá trình resolve/liên kết một lệnh `invokedynamic`, mong nhận một `java.lang.invoke.CallSite`. BSM có thể do thư viện/JDK hoặc **chính ứng dụng** định nghĩa; không có yêu cầu rằng class chứa BSM phải được bootstrap class loader nạp.

**Câu nhớ nhanh:** *Bootstrap class loader trả lời “ai định nghĩa class này?”; bootstrap method của `invokedynamic` trả lời “liên kết điểm gọi động này với target nào?”*

Sự trùng chữ *bootstrap* mang ý nghĩa “thiết lập thứ ban đầu” trong hai ngữ cảnh khác nhau, **không có quan hệ đồng nhất**. Dù BSM là method của một class được bootstrap loader nạp (ví dụ `LambdaMetafactory`), điều đó là **đặc điểm của ví dụ**, không phải quy tắc định nghĩa BSM.

Bài học này giới thiệu cơ chế ở mức class loading và bytecode, các mục `CONSTANT_InvokeDynamic` / `BootstrapMethods` trong ClassFile, vai trò `MethodHandles.Lookup` và `CallSite`, lỗi thường gặp, cùng một **chương trình Java 17 biên dịch/chạy được** và lệnh `javap` để tự quan sát.

## 1. Bảng so sánh trực tiếp

| Tiêu chí | Bootstrap class loader | Bootstrap method của invokedynamic |
| --- | --- | --- |
| Bản chất | Thành phần của cơ chế tải và định nghĩa class trong JVM | Method handle/phương thức JVM sử dụng để link một dynamic call site |
| Đơn vị xử lý | Class/interface được JVM định nghĩa từ bytecode/runtime image | Một call-site specifier của bytecode `invokedynamic` |
| Mục đích | Làm cho một `Class<?>` tồn tại với đúng defining loader/type identity | Tạo kết quả liên kết dưới dạng `CallSite` với target `MethodHandle` phù hợp |
| Đầu vào đặc trưng | Tên class, bytecode/module/source tùy loader/runtime | `Lookup` của caller, tên điểm gọi, `MethodType`, static bootstrap arguments |
| Kết quả | Một định nghĩa `Class<?>` có thể được JVM sử dụng | Một `CallSite`; lần gọi động dùng target của call site |
| API quan sát | `Class.getClassLoader()`; `ClassLoader`; `jcmd`/class loading logs | `javap -v -p`: `InvokeDynamic`, `BootstrapMethods`; `java.lang.invoke` |
| Có nhất thiết nằm trong Java code ứng dụng? | Bootstrap loader là cơ chế runtime, không nhất thiết là đối tượng Java `ClassLoader` bình thường | Custom bootstrap method có thể viết bằng Java; thường khai báo public static và trả `CallSite` |
| Có nhất thiết liên quan tới nhau? | Không | Không; việc BSM được định nghĩa trong class bootstrap-loaded chỉ là một khả năng |
| Lỗi đặc trưng | `ClassNotFoundException`, `NoClassDefFoundError`, `LinkageError` liên quan loading/linking | `BootstrapMethodError` nếu liên kết BSM/call site không hợp lệ hoặc BSM thất bại |

Cả hai cùng tham gia hoạt động chung của JVM, nhưng **điểm bắt đầu, mục tiêu, điều kiện lỗi và vòng đời khác nhau**.

## 2. Bootstrap class loader: định nghĩa class nền tảng

Một kiểu tham chiếu trong JVM được định danh bằng cặp:

~~~text
Type identity = (binary name, defining class loader)
~~~

Bootstrap class loader là loader nền tảng cốt lõi. Java API dùng `null` để đại diện cho nó tại `Class.getClassLoader()`; đây **không phải NullPointerException, không phải “class chưa được nạp”**, mà là cách biểu diễn có chủ đích.

Ví dụ Java 17 thông thường:

~~~java
System.out.println(String.class.getClassLoader() == null);
System.out.println(
        java.lang.invoke.LambdaMetafactory.class.getClassLoader() == null);
~~~

Đây là **fragment** cần đặt trong method; hai dòng in `true` trên JDK 17 tiêu chuẩn.

### Vai trò trong mô hình delegation

Một lộ trình nạp class điển hình:

~~~text
Custom/plugin class loader
          |
          v
Application/system class loader
          |
          v
Platform class loader
          |
          v
Bootstrap class loader
          |
          v
Các class nền tảng thích hợp, ví dụ java.lang.String
~~~

Đây là mô hình khái quát parent delegation, **không bảo đảm mọi custom loader đều tuân thủ một cây duy nhất**. Java 9+ có runtime image theo module; không nên tưởng rằng bootstrap loader chỉ đọc từ `rt.jar` (file đó đã bị loại bỏ từ Java 9) hay rằng mọi class `jdk.*`/`javax.*` đều do bootstrap nạp.

Một custom loader thông thường không được tự định nghĩa lớp trong package bắt đầu bằng `java.` bằng `defineClass`; API có các ràng buộc và có thể ném `SecurityException`. Một số module nền tảng được gán cho **platform loader**, nên với class cụ thể hãy kiểm tra `Class.getClassLoader()`/`Class.getModule()` thay vì đoán chỉ từ tên package.

### Khi nào hoạt động?

Bootstrap class loader tham gia mỗi khi JVM cần nạp những lớp nền tảng phù hợp: khởi động JVM, resolve dependency, dùng API Java, link một method hoặc quá trình khởi tạo khác. **Nó không được gọi theo nghĩa “một phương thức BSM chạy mỗi khi gặp lambda”**. Nó là cơ chế **định nghĩa kiểu**, không phải factory cho dynamic call site.

Một class ứng dụng bình thường khi chạy bằng:

~~~bash
java BootstrapTerminologyDemo
~~~

thường do **system/application class loader** định nghĩa, trong khi các lớp `java.lang.*`/`java.lang.invoke.*` nền tảng của ví dụ do bootstrap loader định nghĩa. Cả hai có thể cùng được sử dụng trong quá trình liên kết một lệnh `invokedynamic`.

## 3. Bootstrap method của invokedynamic là gì?

`invokedynamic` là một opcode trong JVM instruction set, lần đầu được giới thiệu từ Java 7 (JSR 292). Khác lời gọi cố định như `invokestatic` hay `invokevirtual` vốn có symbolic reference đến method/class theo cơ chế riêng, `invokedynamic` dùng một **call-site specifier** trong constant pool để yêu cầu JVM **liên kết một điểm gọi động**.

Mỗi `invokedynamic` được biểu diễn bằng một entry kiểu `CONSTANT_InvokeDynamic_info` trong constant pool và tham chiếu tới một entry thuộc class-file attribute **`BootstrapMethods`**. Entry này mô tả BSM thông qua một `CONSTANT_MethodHandle` cùng các tham số bootstrap tĩnh nếu có.

~~~text
ClassFile chứa phương thức của caller
  |
  +-- bytecode: invokedynamic #N
  |                   |
  |                   v
  +-- constant_pool[N]: CONSTANT_InvokeDynamic_info
  |           +-- tên điểm gọi + method descriptor
  |           +-- bootstrap_method_attr_index
  |                                 |
  |                                 v
  +-- BootstrapMethods attribute
              +-- bootstrap_method_ref
              |       (CONSTANT_MethodHandle)
              +-- bootstrap_arguments[]
                      |
                      v
         JVM resolve/liên kết dynamic call site
                      |
                      v
         Invoke bootstrap method handle
                      |
                      v
              CallSite / target
                      |
                      v
           lệnh invokedynamic gọi target
~~~

BSM không có nhiệm vụ “nạp toàn bộ ứng dụng”. Vai trò của nó là giúp JVM quyết định **MethodHandle target cho một dynamic call site cụ thể**. Chính JVM chịu trách nhiệm quản lý resolution/linkage, kiểm tra method type và lựa chọn kết quả liên kết theo quy tắc JVMS.

### Dạng chữ ký BSM dễ gặp

Một BSM điển hình có ba đối số đầu do JVM cung cấp:

~~~java
public static CallSite bootstrap(
        MethodHandles.Lookup caller,
        String invokedName,
        MethodType invokedType,
        Object... staticArguments) throws Throwable
~~~

Đây là **chữ ký khái quát**, không phải method runnable độc lập. BSM thực tế có thể dùng các kiểu cố định khác cho static arguments. Những loại method handle được phép làm bootstrap method còn chịu quy tắc của JVMS; **không nên khẳng định mọi BSM bắt buộc là một public static Java method y hệt chữ ký trên**. Trên thực tế nhiều BSM hữu ích là static method.

Ý nghĩa:

- `caller` / `Lookup`: bối cảnh class caller và khả năng lookup/access tương ứng; không nên nhầm với class loader mặc định toàn JVM.
- `invokedName`: tên của dynamic call site theo constant pool, **không nhất thiết là tên một Java method thật trên class được gọi**.
- `invokedType` / `MethodType`: kiểu tham số/return mà lệnh `invokedynamic` yêu cầu.
- `staticArguments`: tham số được biên dịch ghi trong entry `BootstrapMethods`; ví dụ các `MethodType`, `MethodHandle`, chuỗi recipe cho nối chuỗi.
- `CallSite`: đối tượng nắm một **target `MethodHandle`** tương thích với descriptor điểm gọi.

Sau khi liên kết thành công, việc thực thi tiếp theo của dynamic call site **không cần gọi lại BSM mỗi lần** để nhận cùng kết quả liên kết. JVM gọi target đã liên kết theo quy tắc `CallSite`. Một số điều kiện như **resolution đồng thời** có thể khiến BSM được thực thi nhiều lần trong quá trình tranh chấp liên kết; không được xem BSM là constructor bảo đảm đúng **một lần toàn cục**.

## 4. Vì sao BSM không bắt buộc được bootstrap class loader nạp?

Xét hai trường hợp:

**Trường hợp A — thư viện nền tảng:**

~~~text
Lớp application: Demo (application loader)
    |
    +-- bytecode invokedynamic (lambda)
           |
           +-- BSM handle trỏ LambdaMetafactory.metafactory
                    |
                    +-- LambdaMetafactory thuộc java.base,
                        thường bootstrap-loaded
                    |
                    +-- trả CallSite hợp lệ cho lambda
~~~

**Trường hợp B — bootstrap method tự viết:**

~~~text
Lớp application: App (application loader)
    |
    +-- invokedynamic có BSM handle tới com.acme.runtime.DynamicLinker
           |
           +-- DynamicLinker cũng có thể application/plugin-loaded
           |
           +-- DynamicLinker.bootstrap(...) trả CallSite
~~~

Đối với trường hợp B, JVM vẫn phải **resolve class chứa BSM** và method handle theo quy tắc class loading/access. Nhưng việc class ấy thuộc application loader **không làm BSM mất tư cách bootstrap method**. Từ *bootstrap* ở đây nghĩa là **khởi tạo thông tin liên kết cho call site**, không phải phải chạy bên trong bootstrap class loader.

Một phương thức Java public static do ứng dụng viết chỉ trở thành **BSM của một call site** khi **ClassFile thực sự có bootstrap method reference thích hợp** và JVM resolve call site bằng nó. Chỉ đặt tên method là `bootstrap`, chỉ gọi nó từ source Java, hoặc chỉ import `java.lang.invoke.*` **không tự sinh lệnh `invokedynamic` trỏ tới method ấy**.

Trong Java source thông thường, tạo custom `invokedynamic` call-site specifier thường cần compiler/runtime tạo bytecode phù hợp hoặc công cụ sinh/chỉnh sửa ClassFile (ASM, Byte Buddy, compiler của một ngôn ngữ JVM, v.v.). Ví dụ phần tiếp theo dùng `javac` Java 17 sinh `invokedynamic` chuẩn cho lambda và nối chuỗi, **không tự tạo BSM tùy biến bằng source thuần**.

## 5. Ví dụ Java 17 độc lập: phân biệt hai khái niệm ngay khi chạy

Tạo **một file** `BootstrapTerminologyDemo.java`:

~~~java
import java.lang.invoke.LambdaMetafactory;

public class BootstrapTerminologyDemo {
    @FunctionalInterface
    interface TextTask {
        String run();
    }

    static String makeMessage(String name) {
        return "Hello, " + name;
    }

    public static void main(String[] args) {
        System.out.println("String bootstrap-loaded: " +
                (String.class.getClassLoader() == null));

        System.out.println("LambdaMetafactory bootstrap-loaded: " +
                (LambdaMetafactory.class.getClassLoader() == null));

        System.out.println("Demo application-loaded: " +
                (BootstrapTerminologyDemo.class.getClassLoader()
                        == ClassLoader.getSystemClassLoader()));

        TextTask task = () -> "lambda target";
        System.out.println("Lambda result: " + task.run());

        String name = args.length == 0 ? "Java" : args[0];
        System.out.println("Concat result: " + makeMessage(name));
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên, trong thư mục chứa file:

~~~bash
javac --release 17 BootstrapTerminologyDemo.java
java BootstrapTerminologyDemo
~~~

**Stdout chính xác** với lệnh trên trong môi trường JDK 17 tiêu chuẩn:

~~~text
String bootstrap-loaded: true
LambdaMetafactory bootstrap-loaded: true
Demo application-loaded: true
Lambda result: lambda target
Concat result: Hello, Java
~~~

### Mỗi dòng đang đo điều gì?

1. `String.class.getClassLoader() == null` chứng tỏ `String` là class do **bootstrap class loader** định nghĩa theo quy ước API.
2. `LambdaMetafactory.class.getClassLoader() == null` minh họa BSM thư viện JDK cũng được chứa trong một **class bootstrap-loaded**. Điều này **không đồng nhất** class loader với bootstrap method.
3. `BootstrapTerminologyDemo.class.getClassLoader() == ClassLoader.getSystemClassLoader()` cho biết trong cách khởi chạy classpath tiêu chuẩn này, demo là class thuộc **application/system loader**.
4. Biểu thức lambda `() -> "lambda target"` được `javac` Java 17 thông thường biên dịch thành dynamic call site sử dụng `LambdaMetafactory.metafactory`; gọi `task.run()` thực thi implementation trả chuỗi.
5. Nối chuỗi động `"Hello, " + name` trong `makeMessage` được compiler Java 17 thông thường biên dịch thông qua cơ chế **`invokedynamic` với `StringConcatFactory.makeConcatWithConstants`**. Đầu ra là `Hello, Java`.

**Chú ý độ chắc chắn của ví dụ:** Các dòng in được xác định bởi code và cách chạy classpath tiêu chuẩn. Chi tiết về **số lượng lệnh `invokedynamic`, thứ tự constant-pool entries và chỉ số bootstrap method** trong `javap` có thể khác giữa bản JDK/compiler hoặc tùy chọn biên dịch; chúng **không được dùng làm giá trị stdout cố định**.

### 5.1. Kiểm tra bytecode bằng javap -v -p

Chạy:

~~~bash
javap -v -p BootstrapTerminologyDemo
~~~

Lệnh trên in nhiều thông tin ClassFile. Hãy tìm các cụm:

~~~text
InvokeDynamic
BootstrapMethods:
LambdaMetafactory.metafactory
StringConcatFactory.makeConcatWithConstants
~~~

Đây là **các chuỗi cần tìm trong bản disassembly**, không phải bản sao đầy đủ của output `javap` (các chỉ số constant pool khác nhau). Với `javac --release 17` mặc định, nên quan sát:

- Một `invokedynamic` liên quan đến **tạo lambda** `TextTask`, có BSM là `java/lang/invoke/LambdaMetafactory.metafactory`.
- Ít nhất một `invokedynamic` liên quan đến **nối chuỗi**, có BSM là `java/lang/invoke/StringConcatFactory.makeConcatWithConstants`.
- Một **`BootstrapMethods` attribute** trong ClassFile liệt kê method handles và bootstrap arguments.
- Một hoặc nhiều `InvokeDynamic` constant-pool entries chỉ về thông tin bootstrap và tên/type của call site.

Một đoạn **minh họa rút gọn**, không phải toàn bộ kết quả của compiler:

~~~text
BootstrapMethods:
  ... REF_invokeStatic java/lang/invoke/StringConcatFactory.makeConcatWithConstants
  ... REF_invokeStatic java/lang/invoke/LambdaMetafactory.metafactory
~~~

Trong bản in thật, `javap` kèm method descriptor, constant-pool indices và `Method arguments:`. Mã `() -> "lambda target"` còn có thể xuất hiện dưới dạng method tổng hợp `lambda$main$0` trong class chứa lambda. Tên/chỉ số và cách tổ chức implementation có thể thay đổi; điều quan trọng là **call-site reference và BSM handle được compiler ghi vào ClassFile**.

### 5.2. Câu hỏi đọc ngược kết quả

Khi `LambdaMetafactory` được nạp bởi bootstrap class loader nhưng lambda nằm trong `BootstrapTerminologyDemo`:

- **Ai định nghĩa `LambdaMetafactory.class`?** Bootstrap class loader, theo `getClassLoader() == null`.
- **Ai định nghĩa `BootstrapTerminologyDemo.class`?** Application/system class loader trong demo.
- **Ai quyết định cách liên kết `invokedynamic` của lambda?** JVM sử dụng **bootstrap method handle** trỏ `LambdaMetafactory.metafactory` để nhận một `CallSite` phù hợp.
- **Tên “bootstrap” nào có ý nghĩa khi đọc `BootstrapMethods` attribute?** **Bootstrap method của call-site linkage**, không phải danh sách class do bootstrap class loader nạp.

Nếu đổi BSM sang một method phù hợp được ứng dụng định nghĩa trong ClassFile khác, loader chứa BSM có thể là application/plugin loader nhưng cơ chế `invokedynamic` vẫn đúng nguyên tắc.

## 6. Chu trình liên kết invokedynamic chi tiết

Có thể hình dung theo các bước:

### Bước A — class chứa invokedynamic được định nghĩa

Một defining class loader (application loader, plugin loader hoặc loader khác) định nghĩa class chứa `invokedynamic`. JVM đọc ClassFile và thông tin constant pool/attributes của class.

**Điều này chưa đồng nghĩa mỗi dynamic call site đã link ngay.** JVM quyết định thời điểm resolution phù hợp; không nên gán cứng rằng BSM luôn chạy đúng lúc `ClassLoader#defineClass` hoặc chỉ khi lệnh được thực thi lần đầu.

### Bước B — JVM resolve call-site specifier

Khi cần liên kết một `invokedynamic`, JVM resolve thông tin tương ứng:

~~~text
CONSTANT_InvokeDynamic_info
  bootstrap_method_attr_index
  name_and_type_index
       |
       v
BootstrapMethods[bootstrap_method_attr_index]
  bootstrap_method_ref
  bootstrap_arguments[]
~~~

BSM method handle có thể trỏ tới một method trong class do **một class loader bất kỳ phù hợp** định nghĩa. Quá trình resolve method handle này có thể dẫn đến nạp những class liên quan, nhưng **không đổi identity của các class đã định nghĩa**.

### Bước C — JVM truyền arguments tới BSM

JVM cung cấp bối cảnh caller (`Lookup`), tên `invokedynamic`, descriptor (`MethodType`) và các bootstrap arguments tĩnh. Ví dụ `LambdaMetafactory.metafactory` sử dụng các thông tin phương thức SAM, target implementation method và instantiated method type để trả một call site cho lambda.

Đối với string concatenation, `StringConcatFactory` có thể sử dụng recipe và các constants để xây một target làm phép nối chuỗi phù hợp.

### Bước D — BSM trả CallSite

`java.lang.invoke.CallSite` chứa target `MethodHandle`. Tùy mục tiêu liên kết, có các implementation như:

- **`ConstantCallSite`:** target không thay đổi sau khi được thiết lập; phù hợp nhiều dynamic call sites ổn định.
- **`MutableCallSite`:** target có thể được đổi theo cơ chế của API, cần chú ý visibility giữa threads.
- **`VolatileCallSite`:** mục đích cung cấp ngữ nghĩa quan sát update target theo điều kiện giống volatile phù hợp API.

`invokedynamic` yêu cầu method type của call site/target tương thích với descriptor được dùng. BSM không thể trả `null` hoặc một CallSite có type sai rồi mong JVM tự ép một cách tùy tiện; resolution sẽ thất bại theo quy tắc liên kết.

### Bước E — lệnh invokedynamic gọi target đã link

Sau khi call site được liên kết hợp lệ, `invokedynamic` dùng target `MethodHandle`. JVM có thể tối ưu hóa và biên dịch JIT đường đi; nhưng về mặt ngữ nghĩa, **BSM thiết lập đường liên kết**, còn **target thực thi logic của lời gọi**.

### Cẩn thận với phát biểu “bootstrap method chỉ chạy một lần”

Với một call-site specifier đã liên kết, BSM không chạy lại trên mỗi lần thực thi target. Tuy nhiên, khi **nhiều thread cùng đua liên kết** một điểm gọi chưa được resolve, JVMS cho phép thực hiện BSM đồng thời nhiều lần rồi chọn một kết quả để liên kết. Vì vậy:

- Đừng đặt **side effect quan trọng cần đúng-một-lần** trong BSM mà không có thiết kế đồng bộ/idempotency thích hợp.
- Không coi BSM như constructor singleton global của JVM.
- Có thể có nhiều `invokedynamic` call sites sử dụng cùng BSM mà vẫn có quá trình liên kết riêng.
- Liên kết có thể có điều kiện fail/cache lỗi theo quy tắc JVM, không phải luôn thử lại mỗi lần gọi.

## 7. BSM tự viết khác với phương thức bình thường ở đâu?

Về **mã Java**, BSM có thể là một method tĩnh trả `CallSite`. Ví dụ **fragment minh họa**, không phải chương trình chạy trực tiếp và **chưa có lệnh `invokedynamic` tham chiếu tới nó**:

~~~java
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class DynamicLinker {
    public static String hello() {
        return "hello from target";
    }

    public static CallSite bootstrap(
            MethodHandles.Lookup caller,
            String name,
            MethodType invokedType) throws Throwable {

        MethodHandle target = caller.findStatic(
                DynamicLinker.class, "hello", invokedType);

        return new ConstantCallSite(target);
    }
}
~~~

Điều kiện để đoạn BSM này được sử dụng đúng:

1. Một ClassFile phải chứa **`CONSTANT_InvokeDynamic_info` / `BootstrapMethods`** tham chiếu `DynamicLinker.bootstrap` bằng method handle hợp lệ.
2. Descriptor `invokedType` của call site phải phù hợp target `DynamicLinker.hello`: trong ví dụ, không có tham số, trả `String`.
3. `Lookup` phải có quyền truy cập target method theo ngữ cảnh caller (ở ví dụ này `hello` public), cùng những điều kiện liên kết khác.
4. JVM resolve và gọi BSM, BSM trả `ConstantCallSite`. Sau đó target mới được `invokedynamic` gọi.

Nếu chỉ viết:

~~~java
DynamicLinker.bootstrap(lookup, "hello", methodType);
~~~

đây chỉ là một **lời gọi method Java thông thường** trong source (với biến `lookup`, `methodType` phù hợp). Nó không tự tạo một bytecode `invokedynamic` để JVM liên kết bằng BSM đó. **Sự khác nhau nằm ở cách ClassFile trỏ đến BSM trong một call-site specifier**, không nằm ở việc method có tên `bootstrap` hay được gọi từ đâu.

### BSM có thể là code ứng dụng/plugin

`DynamicLinker` hoàn toàn có thể được nạp bởi một custom/plugin class loader hợp lệ. Nó vẫn là BSM nếu được JVM resolve từ `BootstrapMethods` attribute của một class caller. Trái lại, `LambdaMetafactory` ở JDK 17 là class nền tảng nhưng **không phải chính bootstrap class loader**; nó là một class chứa các phương thức factory có thể được dùng làm BSM.

## 8. invokedynamic, LambdaMetafactory và nối chuỗi

Hai trường hợp `javac` Java 17 thường tự sinh `invokedynamic` mà người viết source không phải trực tiếp viết opcode:

### 8.1. Lambda expression

~~~java
Runnable task = () -> System.out.println("work");
task.run();
~~~

Đây là fragment hợp lệ trong method. Compiler thường mã hóa **tạo instance functional interface** bằng `invokedynamic`. BSM điển hình là `LambdaMetafactory.metafactory` hoặc một biến thể khác như `altMetafactory` khi cần thuộc tính bổ sung.

**Không nhầm hai thời điểm:**

- Khi `invokedynamic` tạo lambda được liên kết, BSM cung cấp call site/target cho cơ chế tạo functional-interface instance.
- Khi code gọi `task.run()`, đó là **lời gọi interface lên lambda object** được tạo, không có nghĩa JVM gọi `LambdaMetafactory.metafactory` lại ở mỗi lần `run()`.

Một lambda có thể được JVM triển khai qua class sinh động hoặc hidden class ở những implementation hiện đại, nhưng **chi tiết lớp hỗ trợ là nội bộ runtime**; không nên viết logic nghiệp vụ giả định tên class lambda, tên module hoặc bytecode được sinh luôn bất biến.

### 8.2. Nối chuỗi bằng dấu +

~~~java
String who = "Java";
String greeting = "Hello, " + who;
~~~

Nếu compiler xác định biểu thức có phần phải động, `javac` Java 17 mặc định thường sinh `invokedynamic` liên quan `StringConcatFactory.makeConcatWithConstants`. Những phép nối **toàn hằng** có thể được constant-fold ngay tại compile time và **không cần invokedynamic**.

Trong demo phần 5, `makeMessage(String name)` nhận một tham số runtime, nên đường nối chuỗi động có thể nhìn thấy qua `javap -v -p`.

### 8.3. Về ngôn ngữ JVM khác

`invokedynamic` được thiết kế để hỗ trợ liên kết động do JVM quản lý, hữu ích cho compiler và runtime của những ngôn ngữ JVM khác hoặc các cơ chế call-site linh hoạt. Nó không chỉ phục vụ lambda/nối chuỗi. BSM có thể cung cấp target được tùy biến theo call-site name, signature và static args, miễn tuân thủ hợp đồng liên kết.

## 9. Phân biệt với ConstantDynamic (condy)

Trong ClassFile Java hiện đại còn có `CONSTANT_Dynamic_info`, thường gọi **dynamic constant** hoặc *condy*. Nó cũng sử dụng **`BootstrapMethods` attribute** để mô tả bootstrap method, nhưng mục tiêu khác:

| Cơ chế | Bootstrap method dùng để tạo ra gì? |
| --- | --- |
| **`CONSTANT_InvokeDynamic_info` / invokedynamic** | Một **`CallSite`** để liên kết điểm gọi động. |
| **`CONSTANT_Dynamic_info` / dynamic constant** | Một **giá trị constant** đã được resolve theo loại yêu cầu, không phải CallSite bắt buộc. |

BSM của condy có các đối số đầu tương tự nhưng đối số kiểu đích là **`Class<?>`** của constant chứ không phải `MethodType` của call site; kết quả là giá trị constant theo quy tắc JVM. Không nên thấy chữ `BootstrapMethods` trong ClassFile rồi mặc định đó luôn là metadata riêng của `invokedynamic`.

Cả hai vẫn khác **bootstrap class loader**: condy BSM cũng không bắt buộc sống trong class do bootstrap class loader định nghĩa.

## 10. Lỗi thường gặp: loading failure khác bootstrap linking failure

| Triệu chứng | Cơ chế cần điều tra | Gợi ý |
| --- | --- | --- |
| `ClassNotFoundException` khi custom loader tìm class | **Class loading** | Classpath/module path, tên nhị phân, parent delegation, nguồn bytes. |
| `NoClassDefFoundError` khi cần định nghĩa phụ thuộc | **Loading/linking của class** | Thiếu class, initialization failure trước đó, loader visibility. |
| `ClassCastException: A cannot be cast to A` | **Type identity** | Hai defining loader khác nhau định nghĩa cùng tên; không phải bản thân BSM bắt buộc sai. |
| `SecurityException` khi cố định nghĩa `java.*` | **Protected platform namespace** | Không tự override class nền tảng bằng loader ứng dụng. |
| `BootstrapMethodError` khi sử dụng invokedynamic | **Bootstrap linkage** | BSM method handle sai, BSM ném exception, trả `null`/`CallSite` sai type, quyền truy cập phụ thuộc. |
| `WrongMethodTypeException` ở MethodHandle invocation | **Signature/type của method handle** | `MethodType`, `invokeExact`/`invoke` và khả năng adaptation. |
| `IllegalAccessError`/lỗi truy cập | **Resolution/module access** | Lookup caller, `exports`/`opens`, member access và defining loader. |
| `ExceptionInInitializerError` từ static initializer | **Class initialization** | `<clinit>` ném exception; đây là pha khác với BSM liên kết call site. |

`BootstrapMethodError` thuộc họ `LinkageError`. Nó thường báo thất bại trong resolve/bootstrap dynamic call site hoặc dynamic constant; **không có nghĩa bootstrap class loader đã hỏng**. Khi thấy lỗi, hãy đọc toàn bộ stack trace và **cause**, tìm method handle/BSM đang được dùng và type mà site mong đợi.

Một BSM có thể thất bại bởi method handle target sai descriptor, không đủ quyền access, exception trong user code hoặc cấu hình phụ thuộc thiếu. Cần kiểm tra cả **ClassFile** và **loader đã định nghĩa BSM class**, nhưng không tự sửa bằng cách ép BSM về bootstrap loader.

### Những điều kiện không thể bỏ qua

- `CallSite` trả về phải không null và có target `MethodHandle` với type phù hợp với invokedynamic descriptor.
- Nếu dùng `ConstantCallSite`, target ổn định; `MutableCallSite`/`VolatileCallSite` có ngữ nghĩa cập nhật riêng, không phải “mọi lần gọi đều link lại”.
- BSM được JVM gọi **trong quá trình linking**; lỗi có thể phát sinh lười khi call site được resolve, không nhất thiết ngay lúc nạp class chứa bytecode.
- BSM method handle/caller context chịu kiểm tra resolution/access như những symbolic references liên quan khác. Tên class loader không tự cấp quyền bỏ qua JPMS.
- Đừng nuốt `BootstrapMethodError` rồi thử chạy một method tùy tiện khác nếu không có fallback được thiết kế riêng; có thể che lỗi ABI/linkage nghiêm trọng.

## 11. Chẩn đoán thực tế: nhìn class loader và bytecode theo hai hướng

Khi gặp một class dùng lambda/chuỗi hoặc một framework có dynamic bytecode, nên điều tra theo thứ tự:

### Bước 1 — class nào đang chứa invokedynamic?

Chạy:

~~~bash
javap -v -p -classpath . BootstrapTerminologyDemo
~~~

Hãy xác định method có `invokedynamic`, constant-pool entry `InvokeDynamic` và phần `BootstrapMethods`. Nếu class nằm trong JAR, dùng classpath phù hợp thay `.` và tên binary class tương ứng.

### Bước 2 — method handle bootstrap trỏ đi đâu?

Trong `BootstrapMethods`, tìm tên chủ sở hữu/BSM:

~~~text
java/lang/invoke/LambdaMetafactory.metafactory
java/lang/invoke/StringConcatFactory.makeConcatWithConstants
com/acme/runtime/DynamicLinker.bootstrap
~~~

Đây là các **ví dụ tên symbolic**, không mặc định cùng tồn tại trong một ClassFile. Với chương trình ở phần 5 chỉ có hai nhóm đầu.

### Bước 3 — loader nào định nghĩa caller, BSM owner và các API types?

Ví dụ **fragment**:

~~~java
System.out.println(callerClass.getClassLoader());
System.out.println(bsmOwnerClass.getClassLoader());
System.out.println(callerClass.getModule());
System.out.println(bsmOwnerClass.getModule());
~~~

Đoạn này đòi có hai biến `Class<?>` `callerClass` và `bsmOwnerClass` hợp lệ. Không thay `bsmOwnerClass` bằng `BootstrapMethods`: **attribute không phải class Java**. Khi thấy `null` ở `getClassLoader()`, đó thường là dấu hiệu defining loader là bootstrap, **không chứng minh method đang được JVM dùng làm BSM**.

### Bước 4 — kiểm tra signature và bootstrap arguments

BSM `invokedynamic` phải nhận arguments theo JVMS và trả `CallSite` đúng signature. Với `LambdaMetafactory`, kiểm tra functional interface, implementation method handle và instantiated method type. Với `StringConcatFactory`, kiểm tra recipe/constants và dynamic argument count phù hợp.

### Bước 5 — tách ba nguyên nhân thường bị gộp

1. **Loader không tìm được BSM owner hoặc dependency**: lỗi loading/visibility ban đầu.
2. **BSM owner được nạp nhưng BSM invocation thất bại**: lỗi bootstrap linkage.
3. **BSM liên kết thành công nhưng target/user logic lỗi khi chạy**: có thể là exception từ target, **không nhất thiết `BootstrapMethodError`**.

Các công cụ quan sát có thể hỗ trợ nhưng không nên đồng nhất:

- `-Xlog:class+load=info` (HotSpot Java 17) có thể giúp quan sát nguồn/class được nạp.
- `javap -v -p` giúp xem `invokedynamic`/`BootstrapMethods` trong file đã biên dịch.
- Stack trace/cause và thread dump giúp phân biệt lỗi BSM, initialization và code target.
- Thông tin `Class.getClassLoader()` / `Class.getModule()` giúp theo dõi type identity/module.

Ví dụ chạy có logging:

~~~bash
java -Xlog:class+load=info BootstrapTerminologyDemo
~~~

**Không có stdout cố định** cho lệnh này vì logging có nhiều dòng phụ thuộc JDK/runtime và môi trường; hãy tập trung vào class nguồn và loader, không coi log là bản thay thế `javap`.

## 12. Những hiểu lầm phổ biến

**“Bootstrap method phải được bootstrap class loader nạp.”** Sai. BSM là một method handle để link call site; BSM owner có thể là class ứng dụng/plugin nếu JVM có thể resolve và truy cập hợp lệ.

**“`LambdaMetafactory` là bootstrap class loader.”** Sai. `LambdaMetafactory` là **một class** cung cấp các method factory như `metafactory`; bootstrap class loader là thành phần nạp class đó. Hai đối tượng có bản chất hoàn toàn khác.

**“Nếu `SomeClass.class.getClassLoader() == null` thì phương thức trong class đều là bootstrap methods.”** Sai. `null` nói về loader đã **định nghĩa Class**, không nói method nào xuất hiện trong `BootstrapMethods` attribute của caller.

**“Mọi lệnh nối chuỗi Java 17 đều cần BSM.”** Sai. Compiler có thể gộp các hằng tại compile time. Nối chuỗi có giá trị động mới thường được `javac` mã hóa qua StringConcatFactory.

**“invokedynamic có nghĩa JVM bỏ qua type checking.”** Sai. Call-site descriptor, MethodHandle target, verifier và access rules vẫn được kiểm tra; dynamic linkage không phải cơ chế tắt an toàn kiểu.

**“BSM chạy lại mỗi khi gọi lambda hoặc gọi target.”** Sai. BSM tham gia liên kết call site; target chạy khi invokedynamic được thực thi sau khi đã link. Có ngoại lệ về race resolution, nhưng không phải gọi lại BSM cho mỗi invocation.

**“BSM luôn được gọi đúng một lần.”** Cũng sai. JVM có thể thực hiện bootstrap method hơn một lần trong điều kiện resolution đồng thời cho cùng site rồi chọn một kết quả phù hợp. Không dựa vào BSM như once-only hook cho side effect.

**“Đổi class loader của thread là cách sửa BootstrapMethodError.”** Không có quy tắc như vậy. TCCL chỉ ảnh hưởng những API dùng context loader; linking một symbolic BSM/call site còn phụ thuộc ClassFile owner, access, method type, module và defining loader phù hợp.

**“Chữ BootstrapMethods chỉ xuất hiện cho invokedynamic.”** Không hoàn toàn: dynamic constants (`CONSTANT_Dynamic`) cũng dùng bootstrap methods attribute.

**“Bấm vào lambda expression trong source là thấy BSM.”** Không hẳn. Lambda source không khai báo BSM trực tiếp; compiler ghi `invokedynamic`/`BootstrapMethods` vào ClassFile. Dùng `javap -v -p` để chứng minh.

## 13. Tự kiểm tra kiến thức

**Câu 1.** `String.class.getClassLoader() == null` cho biết điều gì?

**Đáp án:** `String` được bootstrap class loader định nghĩa theo cách biểu diễn Java API. Nó **không** nói rằng constructor hay mọi method của `String` là BSM.

**Câu 2.** `LambdaMetafactory.metafactory` được `javap` liệt kê trong `BootstrapMethods`. Đây là class loader hay method dùng liên kết call site?

**Đáp án:** Một **bootstrap method** dùng trong `invokedynamic` linking; class `LambdaMetafactory` được bootstrap loader nạp là một chuyện khác.

**Câu 3.** Custom BSM trong `com.acme.DynamicLinker` (application loader) có hợp lệ không?

**Đáp án:** Có thể hợp lệ nếu ClassFile có method handle BSM đúng, JVM resolve/access được class/method và BSM trả `CallSite` đúng method type. Không bắt buộc thuộc bootstrap loader.

**Câu 4.** BSM cho invokedynamic thường trả về gì?

**Đáp án:** Một `java.lang.invoke.CallSite` (ví dụ `ConstantCallSite`) với target method handle phù hợp.

**Câu 5.** Ai cung cấp `Lookup`, `name`, `MethodType` cho BSM?

**Đáp án:** JVM theo thông tin call-site specifier/constant pool và bối cảnh caller khi link `invokedynamic`.

**Câu 6.** Nếu method target đã được link rồi, JVM có phải gọi lại BSM mỗi lần thực thi target không?

**Đáp án:** Không. Call site giữ target theo ngữ nghĩa loại `CallSite`; BSM thuộc pha link, còn target thuộc pha chạy.

**Câu 7.** Một `BootstrapMethodError` có chứng minh bootstrap class loader bị lỗi không?

**Đáp án:** Không. Nó báo vấn đề bootstrap linking (hoặc dynamic constant resolution) và cần kiểm tra BSM handle, arguments, method types và cause.

**Câu 8.** Tại sao `javac --release 17` thường sinh `invokedynamic` cho lambda mà không cần user viết BSM?

**Đáp án:** Java compiler tự tạo `CONSTANT_InvokeDynamic_info` và `BootstrapMethods` tham chiếu thư viện như `LambdaMetafactory`. JVM làm linking theo metadata đó khi chạy.

## Ghi nhớ

- **Bootstrap class loader** = bộ nạp lớp nền tảng, giúp **định nghĩa Class<?>**; những class do nó định nghĩa trả `null` từ `Class.getClassLoader()`.
- **Bootstrap method của invokedynamic** = method handle dùng để **link một call site**, thường nhận `Lookup` + tên + `MethodType` + static args và trả `CallSite`.
- Hai khái niệm có chữ *bootstrap* nhưng **khác nhiệm vụ**. BSM có thể nằm trong JDK hoặc class ứng dụng; nó **không bị ràng buộc phải chạy trong một class bootstrap-loaded**.
- Quan sát class loading bằng `Class.getClassLoader()`; quan sát dynamic linking bằng `javap -v -p`, `InvokeDynamic` và `BootstrapMethods`.
- Khi debug, phân biệt **class loading failure, bootstrap-linking failure và lỗi target execution**; đừng suy từ `BootstrapMethodError` rằng bootstrap class loader hỏng.

**Tài liệu chuẩn Java SE 17:**

- *Java Virtual Machine Specification*, §5.3 (Creation of Classes and Interfaces), §5.4.3.6 (Dynamically-Computed Call Site and Constant Resolution): https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html
- *Java Virtual Machine Specification*, §4.4.10 (`CONSTANT_Dynamic`/`CONSTANT_InvokeDynamic`), §4.7.23 (`BootstrapMethods`): https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html
- *Java Virtual Machine Specification*, §6.5, `invokedynamic`: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html
- Java SE 17 API, `ClassLoader`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17 API, `java.lang.invoke.LambdaMetafactory`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/LambdaMetafactory.html
- Java SE 17 API, `StringConcatFactory`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/StringConcatFactory.html
- Java SE 17 API, `CallSite`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/CallSite.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-class-definition-006">Sinh bytecode, biên dịch động và trách nhiệm quản lý loader</a>

<details>
<summary>Xem chi tiết</summary>

# Sinh bytecode, biên dịch động và trách nhiệm quản lý ClassLoader trong Java

Java có thể tạo thêm class **khi chương trình đang chạy**, thay vì chỉ sử dụng những tệp `.class` được đóng gói từ trước. Một ứng dụng có thể sinh mã nguồn từ template rồi biên dịch bằng `javax.tools.JavaCompiler`, hoặc sinh trực tiếp bytecode qua thư viện chuyên dụng và đưa bytes vào JVM. Sau đó **ClassLoader** (hoặc cơ chế định nghĩa class phù hợp) làm nhiệm vụ chuyển định dạng class-file thành **runtime `Class<?>`**.

Các bước này giải quyết **ba việc khác nhau**:

1. **Sinh mã / sinh bytecode:** tạo dữ liệu biểu diễn class, thường là source `.java` hoặc `byte[]` class-file hợp lệ.
2. **Biên dịch / biến đổi:** dùng Java compiler, code generator hoặc bytecode transformer để tạo bytes đúng định dạng, đúng Java version, dependency và method signatures.
3. **Định nghĩa / nạp kiểu:** JVM cần một **defining loader** và thực hiện các kiểm tra tên, package, loading constraints, verification/linking, bảo mật tương ứng trước khi runtime class được sử dụng.

**Không có phép đồng nhất** giữa “tôi đã tạo được bytecode”, “class đã được JVM nạp” và “class đã được initialize/tạo instance”. Bài này giải thích luồng xử lý từ đầu tới cuối, những trách nhiệm không thể đẩy hết cho JavaCompiler hoặc JVM, đồng thời có chương trình Java 17+ **biên dịch source trong bộ nhớ, tự định nghĩa class, tạo object và so sánh hai thế hệ loader**.

## 1. Mô hình tổng quan

~~~text
                         Host application
                               |
                +--------------+--------------+
                |                             |
        Source generation               Bytecode generation
        String / .java                  ASM, Byte Buddy, v.v.
                |                             |
        JavaCompiler API                     byte[]
                |                             |
       class-file byte[]                     |
                +--------------+--------------+
                               |
                         Loader generation
                               |
                 findClass -> defineClass
                               |
                          JVM Class<?>
                     (tên, defining loader)
                               |
                   Linking / Initialization
                               |
                     Reflection / Interface
                               |
                         Object instance
~~~

Đây là **hai nhánh đầu vào khác nhau** để có bytes, nhưng đầu ra cuối cùng đều chịu các ràng buộc class loading của JVM. Nếu code generator đã tạo bytecode hợp lệ, không cần chạy `javac` lần nữa. Nếu mới tạo chuỗi source, cần một compiler phù hợp để có class-file bytes.

| Khái niệm | Dữ liệu/API | Điều không nên nhầm |
|---|---|---|
| Java source | `String`, `.java`, `JavaFileObject.Kind.SOURCE` | Chưa phải runtime class |
| Java compilation | `JavaCompiler.getTask(...).call()` | Trả kết quả biên dịch; không tự instantiate |
| Class-file bytes | `byte[]`, `JavaFileObject.Kind.CLASS` | Chưa bảo đảm đã verify/link/initialize |
| Custom loader | `ClassLoader.findClass` và `defineClass` | Định nghĩa kiểu theo loader identity |
| Runtime class | `Class<?>` | Là đại diện kiểu, không phải object của class |
| Instance | `Constructor.newInstance()` hoặc gọi `new` trong mã đã liên kết | Có hành vi constructor/initialization riêng |
| Reload thế hệ mới | **Loader instance mới** | Không thay class đang tồn tại trong loader cũ |

### Danh tính kiểu là câu hỏi nền tảng

Theo JVM, **runtime class/interface identity** gắn với:

~~~text
(binary name, defining class loader)
~~~

Ví dụ hai generation đều có `generated.Hello`:

~~~text
("generated.Hello", LoaderA) -> Class A
("generated.Hello", LoaderB) -> Class B

ClassA.getName().equals(ClassB.getName()) == true
ClassA == ClassB                       == false
~~~

Vì vậy một chương trình **không thể “ghi đè” cùng class name trong cùng loader** bằng cách gọi `defineClass` lần hai. Nếu có nhu cầu thêm phiên bản mới, thường tạo loader mới và chuyển traffic/reference theo vòng đời.

## 2. Cách 1: Sinh mã nguồn và biên dịch động bằng `javax.tools`

Package `javax.tools` thuộc module chuẩn **`java.compiler`**; nó có các interface hỗ trợ đưa nguồn vào, lấy output ra, thu thập lỗi và tùy biến nơi lưu file. Một JDK đầy đủ thường cung cấp compiler hệ thống qua:

~~~java
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
if (compiler == null) {
    throw new IllegalStateException("No system Java compiler");
}
~~~

Đây là đoạn minh họa, không phải chương trình độc lập. **`compiler == null` là tình huống phải xử lý**, ví dụ runtime image được tạo bằng `jlink` không chứa module/compiler provider cần thiết hoặc môi trường chỉ có runtime tối giản. Không mặc định bất kỳ JVM nào cũng có compiler để sinh source code lúc chạy.

### Các thành phần của pipeline

| API | Vai trò |
|---|---|
| `ToolProvider.getSystemJavaCompiler()` | Lấy trình biên dịch Java hệ thống nếu có |
| `JavaFileObject` | Biểu diễn đơn vị source hoặc class-file |
| `SimpleJavaFileObject` | Lớp tiện ích để triển khai file object trong memory |
| `StandardJavaFileManager` | Quản lý locations/classpath/output theo hợp đồng compiler |
| `ForwardingJavaFileManager` | Chặn hoặc chuyển hướng những lệnh file-manager tới implementation tùy chỉnh |
| `JavaFileManager.getJavaFileForOutput` | Quyết định nơi compiler ghi class-file output |
| `DiagnosticCollector<JavaFileObject>` | Thu thập lỗi/cảnh báo với vị trí source |
| `JavaCompiler.getTask(...).call()` | Chạy compilation task, trả `Boolean` thành công/thất bại |

**Quan trọng:** `JavaCompiler.getTask` tạo task, nhưng **`call()` mới thực hiện biên dịch**. Biên dịch thành công cũng chỉ cho thấy compiler tạo output hợp lệ theo compiler rules; khi JVM nạp, vẫn có thể phát hiện lỗi ở dependency/linkage/runtime.

### Source trong bộ nhớ

Thay vì tạo file `Hello.java` trên ổ đĩa, ta có thể dùng:

~~~java
final class StringSource extends SimpleJavaFileObject {
    private final String code;

    StringSource(String binaryName, String code) {
        super(URI.create("string:///"
                + binaryName.replace('.', '/')
                + Kind.SOURCE.extension), Kind.SOURCE);
        this.code = code;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return code;
    }
}
~~~

Đây là **mảnh class minh họa**, cần import `java.net.URI` và `javax.tools.SimpleJavaFileObject`; không có `main`. URI dạng `string:///generated/Hello.java` giúp compiler thấy tên file hợp với `public class Hello` mà không cần file system path thật.

### Output `.class` trong bộ nhớ

Một cách tạo output riêng:

~~~java
final class BytesFile extends SimpleJavaFileObject {
    private final ByteArrayOutputStream buffer =
            new ByteArrayOutputStream();

    BytesFile(String binaryName, Kind kind) {
        super(URI.create("mem:///"
                + binaryName.replace('.', '/')
                + kind.extension), kind);
    }

    @Override
    public OutputStream openOutputStream() {
        return buffer;
    }

    byte[] snapshot() {
        return buffer.toByteArray();
    }
}
~~~

Đây là **mảnh minh họa** với imports `ByteArrayOutputStream`, `OutputStream`, `URI`, `SimpleJavaFileObject`. Compiler ghi bytes qua `openOutputStream()`; sau khi `call()` thành công, code lấy snapshot `buffer.toByteArray()`. Một nguồn có thể sinh **nhiều class files**, ví dụ inner class, local class hoặc class phụ; cần lưu output theo **binary class name**, không chỉ một `byte[]` duy nhất.

## 3. Cách 2: Sinh bytecode trực tiếp

Thư viện chuyên dụng (ví dụ ASM hoặc Byte Buddy) có thể tạo `byte[]` mang cấu trúc một class file mà không cần viết source rồi chạy `JavaCompiler`.

~~~text
DSL / metadata / schema
           |
           v
 bytecode generator
           |
           v
   JVM class-file bytes
           |
           v
 defineClass / Lookup.defineClass
           |
           v
        Class<?>
~~~

Điểm lợi:

- Tránh vòng source-text parse/compile khi generator chỉ cần cấu trúc bytecode cụ thể.
- Tạo proxy, adapter, accessor, framework-generated subclasses, instrumentation hoặc method bodies theo nhu cầu.
- Có thể thao tác cấu trúc JVM bytecode khó biểu đạt bằng Java source thông thường.

Điểm phải chịu trách nhiệm:

1. **Class-file format hợp lệ**, constant pool, stack-map frames, access flags, signatures, version hỗ trợ bởi JVM đích.
2. **Định danh và package** khớp tên binary, loader, package/module context.
3. **Bytecode verification và linkage**; có thể gặp `ClassFormatError`, `VerifyError` hoặc `LinkageError`.
4. **Source provenance**, integrity và giới hạn đầu vào; một generator không an toàn có thể tạo class sai hoặc cho phép thực thi mã không mong muốn.
5. **Caching và loader lifecycle**; không tạo vô hạn class độc nhất hoặc loader vô thời hạn.

Không nên tự ghép mảng byte bằng tay để tạo class-file phức tạp trong production khi có thư viện đã hiểu JVM specification; ví dụ sau dùng **JavaCompiler tích hợp**, không phụ thuộc thư viện bytecode ngoài.

### `ClassLoader.defineClass` và `MethodHandles.Lookup.defineClass`

Hai API có scope khác:

- `ClassLoader.defineClass(String, byte[], int, int)` là **protected final**. Mã ứng dụng không gọi tùy ý trên một ClassLoader bất kỳ; custom subclass thường expose gián tiếp qua `findClass`.
- `MethodHandles.Lookup.defineClass(byte[])` là cơ chế có từ Java 9, dùng **Lookup có đủ quyền** để định nghĩa class trong **cùng package và defining loader** với lookup class. Nó **không phải** API tạo bất kỳ class name/package/loader nào.
- `Lookup.defineHiddenClass(...)` hỗ trợ **hidden classes** cho một số cơ chế runtime/framework. Hidden class có đặc tính về discoverability/lifecycle riêng, không phải class tên thông thường để mọi caller tìm bằng `Class.forName(binaryName)`.

Chọn giải pháp theo mục tiêu: loader riêng cho **plugin/generation có thể unload**, hoặc `Lookup` khi sinh thêm kiểu trong package/loader của một host class có quyền phù hợp. Đừng chọn `Lookup` chỉ để né các ràng buộc classloader/package.

## 4. Giao ước của `defineClass`: tên, package và class loading

Custom loader có thể nhận map các class bytes:

~~~java
final class ByteArrayLoader extends ClassLoader {
    private final Map<String, byte[]> definitions;

    ByteArrayLoader(Map<String, byte[]> definitions,
                    ClassLoader parent) {
        super(parent);
        this.definitions = definitions;
    }

    @Override
    protected Class<?> findClass(String name)
            throws ClassNotFoundException {
        byte[] bytes = definitions.get(name);
        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, bytes, 0, bytes.length);
    }
}
~~~

Đây là **khung API minh họa**, cần import `Map` và bổ sung chính sách snapshot/immutability nếu map/bytes có thể bị thread khác sửa. Trong ví dụ đầy đủ, ta thực hiện snapshot và sao chép bytes.

### Vì sao override `findClass` thay vì viết lại `loadClass`?

Implementation mặc định của `ClassLoader.loadClass`:

1. Dùng `getClassLoadingLock(name)` để điều phối các yêu cầu theo chính sách parallel-capable.
2. Kiểm tra `findLoadedClass(name)`.
3. Hỏi parent/bootstrapping trước.
4. Gọi `findClass(name)` khi parent không tìm ra class.
5. `resolveClass` khi được yêu cầu.

Nếu nhu cầu chỉ là đổi **nguồn tìm bytecode**, override `findClass` là điểm mở rộng hợp lý, kế thừa delegation và classloading lock chuẩn. Tự override `loadClass` mà không duy trì `findLoadedClass`/lock có thể khiến hai thread cùng `defineClass` một binary name, dẫn tới `LinkageError`.

### `defineClass` không đồng nghĩa initialization

~~~java
Class<?> type = loader.loadClass("generated.Hello");
Object object = type.getConstructor().newInstance();
~~~

Lời gọi thứ nhất yêu cầu loader tìm class, **không tự yêu cầu chạy static initializer**. Lời gọi reflection tiếp theo tạo instance và sẽ yêu cầu initialization theo quy tắc Java nếu kiểu chưa được khởi tạo.

Các API liên quan:

| API | Trạng thái mong đợi |
|---|---|
| `loader.loadClass(name)` | Yêu cầu load; không tự yêu cầu initialize |
| `Class.forName(name, false, loader)` | Tìm/nạp/liên kết nhưng không yêu cầu initialize |
| `Class.forName(name, true, loader)` | Tìm kiểu và yêu cầu initialize |
| `defineClass(...)` | Định nghĩa runtime class từ class-file bytes; không gọi constructor |
| `resolveClass(type)` | Yêu cầu liên kết (resolution); không có nghĩa tạo instance |
| `getConstructor().newInstance()` | Tạo instance và thực thi constructor |

**Một binary class name chỉ được định nghĩa một lần trong cùng defining loader**. Nếu cố gọi `defineClass` lại với cùng tên trong cùng loader, việc định nghĩa trùng không hợp lệ. Muốn đổi bytecode sau deployment, tạo **generation loader mới** hoặc sử dụng instrumentation/redefinition có những giới hạn riêng.

### Tên binary khớp class-file thực

`defineClass("generated.Hello", bytes, ...)` đòi class-file bytes thực sự mô tả đúng tên ấy. Sai tên có thể gây `NoClassDefFoundError` hoặc lỗi class format/linkage tương ứng.

Không tự tạo class bằng prefix `java.` từ custom loader: JDK có ràng buộc đặc biệt với package nền tảng, có thể ném `SecurityException`. Ngoài ra, nếu các class trong một package dùng package-private access, các class ấy phải ở đúng **runtime package**, vốn phụ thuộc **package name + defining loader**.

## 5. Ví dụ đầy đủ Java 17+: biên dịch trong RAM, nạp hai phiên bản

Chương trình **`DynamicCompilationDemo.java`** dưới đây:

1. Tạo chuỗi source cho class `generated.Hello` với hai bản `dynamic-v1` và `dynamic-v2`.
2. Gọi `ToolProvider.getSystemJavaCompiler()` và `JavaCompiler.getTask(...)` để sinh class bytes.
3. Sử dụng `SimpleJavaFileObject` và `ForwardingJavaFileManager` để **không ghi file `.class` động ra đĩa**.
4. Tạo **hai custom ClassLoader instances**, mỗi loader giữ bytecode của một version.
5. Dùng reflection tạo object, gọi method và kiểm tra class identity, defining loader.
6. Chứng minh cùng binary name nhưng khác loader tạo **hai runtime types khác nhau**; trong cùng loader, gọi `loadClass` lần hai nhận lại cùng `Class<?>`.

### 5.1. File `DynamicCompilationDemo.java`

~~~java
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public final class DynamicCompilationDemo {

    private static final String CLASS_NAME = "generated.Hello";

    // 1. Source nằm trong bộ nhớ.
    static final class Source extends SimpleJavaFileObject {
        private final String code;

        Source(String binaryName, String code) {
            super(URI.create("string:///"
                    + binaryName.replace('.', '/')
                    + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    // 2. File output .class nằm trong bộ nhớ.
    static final class Bytecode extends SimpleJavaFileObject {
        private final ByteArrayOutputStream buffer =
                new ByteArrayOutputStream();

        Bytecode(String binaryName, Kind kind) {
            super(URI.create("mem:///"
                    + binaryName.replace('.', '/')
                    + kind.extension), kind);
        }

        @Override
        public OutputStream openOutputStream() {
            return buffer;
        }

        byte[] snapshot() {
            return buffer.toByteArray();
        }
    }

    // 3. Compiler ghi class files vào một map thay vì ra đĩa.
    static final class MemoryFiles
            extends ForwardingJavaFileManager<StandardJavaFileManager> {

        private final Map<String, Bytecode> outputs =
                new LinkedHashMap<>();

        MemoryFiles(StandardJavaFileManager standard) {
            super(standard);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(
                JavaFileManager.Location location,
                String className,
                JavaFileObject.Kind kind,
                FileObject sibling) {
            Bytecode output = new Bytecode(className, kind);
            outputs.put(className, output);
            return output;
        }

        Map<String, byte[]> snapshot() {
            Map<String, byte[]> result = new HashMap<>();
            for (var entry : outputs.entrySet()) {
                result.put(entry.getKey(), entry.getValue().snapshot());
            }
            return result;
        }
    }

    // 4. Loader tự định nghĩa những class có bytes trong map.
    static final class MemoryLoader extends ClassLoader {
        private final Map<String, byte[]> definitions = new HashMap<>();

        MemoryLoader(Map<String, byte[]> bytes, ClassLoader parent) {
            super(parent);
            bytes.forEach((name, data) ->
                    definitions.put(name, data.clone()));
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            byte[] data = definitions.get(name);
            if (data == null) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, data, 0, data.length);
        }
    }

    private static String sourceFor(String label) {
        return "package generated;\n"
                + "public final class Hello {\n"
                + "  public String message() {\n"
                + "    return \"" + label + "\";\n"
                + "  }\n"
                + "}\n";
    }

    private static Map<String, byte[]> compile(
            JavaCompiler compiler, String code) throws IOException {

        DiagnosticCollector<JavaFileObject> diagnostics =
                new DiagnosticCollector<>();

        try (StandardJavaFileManager standard =
                compiler.getStandardFileManager(
                        diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {

            MemoryFiles files = new MemoryFiles(standard);
            JavaFileObject source = new Source(CLASS_NAME, code);

            Boolean success = compiler.getTask(
                    null,
                    files,
                    diagnostics,
                    List.of("--release", "17", "-proc:none"),
                    null,
                    List.of(source)
            ).call();

            if (!Boolean.TRUE.equals(success)) {
                for (Diagnostic<? extends JavaFileObject> d
                        : diagnostics.getDiagnostics()) {
                    System.err.println(
                            d.getKind() + " at line "
                            + d.getLineNumber() + ": " + d.getMessage(
                                    Locale.ROOT));
                }
                throw new IllegalStateException("Compilation failed");
            }

            return files.snapshot();
        }
    }

    private static String message(Class<?> type) throws Exception {
        Object instance = type.getConstructor().newInstance();
        return (String) type.getMethod("message").invoke(instance);
    }

    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException(
                    "A full JDK compiler is required");
        }

        Map<String, byte[]> v1 =
                compile(compiler, sourceFor("dynamic-v1"));
        Map<String, byte[]> v2 =
                compile(compiler, sourceFor("dynamic-v2"));

        ClassLoader parent = DynamicCompilationDemo.class.getClassLoader();

        MemoryLoader loader1 = new MemoryLoader(v1, parent);
        MemoryLoader loader2 = new MemoryLoader(v2, parent);

        Class<?> first = loader1.loadClass(CLASS_NAME);
        Class<?> second = loader2.loadClass(CLASS_NAME);

        System.out.println("compiler available: true");
        System.out.println("v1: " + message(first));
        System.out.println("v2: " + message(second));
        System.out.println("same binary name: "
                + first.getName().equals(second.getName()));
        System.out.println("same runtime type: " + (first == second));
        System.out.println("v1 defined by loader1: "
                + (first.getClassLoader() == loader1));
        System.out.println("v2 defined by loader2: "
                + (second.getClassLoader() == loader2));
        System.out.println("repeat load same class: "
                + (loader1.loadClass(CLASS_NAME) == first));
        System.out.println("generated in unnamed module: "
                + !first.getModule().isNamed());
    }
}
~~~

### 5.2. Biên dịch và chạy

Lưu file với tên **`DynamicCompilationDemo.java`** trong thư mục riêng. Dùng JDK 17+:

~~~bash
javac -encoding UTF-8 DynamicCompilationDemo.java
java DynamicCompilationDemo
~~~

Lệnh `javac` ban đầu biên dịch **host utility**; còn class `generated.Hello` được biên dịch bằng **JavaCompiler bên trong ứng dụng**, từ chuỗi source lúc runtime. Không có bước `javac generated/Hello.java` và cũng không cần ghi file `generated/Hello.class` ra đĩa.

**Stdout dự kiến khi chạy trên JDK đầy đủ có JavaCompiler:**

~~~text
compiler available: true
v1: dynamic-v1
v2: dynamic-v2
same binary name: true
same runtime type: false
v1 defined by loader1: true
v2 defined by loader2: true
repeat load same class: true
generated in unnamed module: true
~~~

### 5.3. Vì sao kết quả như vậy?

- Cả hai compilation tasks nhận source tên `generated.Hello`, nhưng source method `message()` khác nhau.
- `JavaCompiler` sinh bytes vào các `Bytecode` objects do `MemoryFiles.getJavaFileForOutput` cung cấp; các maps v1/v2 không chia sẻ class objects.
- `MemoryLoader.findClass` nhận bytes từ map và gọi `defineClass`. Vì app loader không có `generated.Hello` trên classpath của ví dụ, parent-first delegation không che class cần định nghĩa.
- `first.getClassLoader() == loader1` và `second.getClassLoader() == loader2` là `true`: hai defining loaders khác nhau.
- `first.getName().equals(second.getName())` là `true` nhưng `first == second` là `false`, thể hiện type identity.
- Trong **cùng loader1**, lời gọi `loadClass(CLASS_NAME)` tiếp theo tìm class đã nạp và trả lại object `Class<?>` cũ; không gọi `defineClass` lần hai.
- Cả hai generated classes thuộc **unnamed module của defining loaders** khi định nghĩa bằng cơ chế ClassLoader thường. Một map bytes tự tạo không tự resolve thành named module JPMS.
- `message(first)` dùng constructor và public method qua reflection. Cách này cần class được định nghĩa thành công và có access phù hợp; trong ví dụ generated class/method là `public`.

### 5.4. Phân tích vai trò của các class helper

**`Source`** không tạo file trên hệ thống: `URI` chỉ cung cấp tên giả tương thích compiler. `getCharContent` là source thực tế.

**`Bytecode`** nhận dữ liệu compiler ghi qua `openOutputStream`. Snapshot tạo một byte[] độc lập từ output stream sau khi biên dịch xong.

**`MemoryFiles`** kế thừa `ForwardingJavaFileManager` để giữ phần lớn logic input/classpath của standard file manager nhưng chuyển hướng **class outputs** sang bộ nhớ. Nó lưu theo tên class, cho phép compiler sinh nhiều output khác nhau nếu nguồn chứa lớp con/anonymous classes.

**`MemoryLoader`** không biết cách tự biên dịch; nó chỉ định nghĩa bytes đã tạo. Bản sao `data.clone()` giảm nguy cơ mã bên ngoài mutate dữ liệu sau khi loader đã nhận.

**`compile(...)`** tạo diagnostics/file manager **mới cho mỗi lần biên dịch**, rồi đóng standard file manager qua try-with-resources. Không chia sẻ file manager giữa nhiều compilation tasks đồng thời nếu chưa có thiết kế synchronization rõ ràng.

**`--release 17`** yêu cầu compiler sinh bytecode/API phù hợp Java 17 dù chương trình chạy trên JDK mới hơn (nếu JDK đó hỗ trợ target 17). **`-proc:none`** tắt annotation processing trong demo để giảm phụ thuộc vào processors tình cờ có trên classpath.

### 5.5. Những giới hạn có chủ ý của ví dụ

1. Mỗi lần compile chỉ có một Java source với class binary name `generated.Hello`. Production có thể cần nhiều nguồn và output files.
2. Source được sinh từ **nhãn có kiểm soát**. Đừng ghép source tùy ý từ dữ liệu người dùng rồi coi kết quả là an toàn.
3. Parent là application loader; nếu class `generated.Hello` đã có ở parent, parent-first sẽ thắng. Muốn cách ly tên trùng, cần namespace riêng hoặc loader policy phù hợp.
4. `MemoryLoader` giữ các byte[] trong map trong suốt vòng đời để ví dụ đơn giản. Production có thể evict bytes sau khi định nghĩa nếu không cần cache.
5. `MemoryLoader` không triển khai `Closeable` vì không mở URL/JAR handles. Nó cũng không có method `unload()`; thời điểm class unloading do JVM/GC quyết định.
6. Không cung cấp resource names `getResource`/`getResourceAsStream` từ memory map. Đây là cache **class-file bytes**, không phải cơ chế resource lookup tổng quát.
7. Chỉ in kết quả chính từ `main`; khi input/compiler lỗi, diagnostic message và exception sẽ phụ thuộc lỗi cụ thể và không thuộc stdout chuẩn trên.

## 6. Biên dịch source phụ thuộc API của host

Ví dụ `generated.Hello` vừa rồi chỉ dùng `String` và các API từ `java.base`. Plugin thực tế thường implement một **shared interface** từ host, ví dụ:

~~~java
package api;

public interface Greeting {
    String message();
}
~~~

Nếu source generated dùng:

~~~java
package generated;

public final class Hello implements api.Greeting {
    @Override
    public String message() {
        return "hello";
    }
}
~~~

Đây là **hai tệp Java minh họa** cho API và implementation, không phải tệp thay thế tự động cho demo độc lập ở phần 5.

### Compile-time classpath khác runtime defining loader

Khi dùng `JavaCompiler`, compiler phải nhìn thấy `api.Greeting` trong input classpath/module path:

~~~java
List<String> options = List.of(
        "--release", "17",
        "-proc:none",
        "-classpath", System.getProperty("java.class.path")
);
~~~

Đây là **mảnh minh họa tùy cấu hình**; giả định API đã có trên classpath ứng dụng và property tương ứng được thiết lập. Với ứng dụng named-module hoặc custom plugin layers, phải thiết kế **module path, classpath và file manager** phù hợp; không coi `java.class.path` là đại diện mọi thứ mà JVM đã nạp bằng custom loaders.

Sau biên dịch, loader định nghĩa generated class cũng phải **nhìn đúng `api.Greeting` runtime** từ parent:

~~~text
Application loader
  defines api.Greeting
          |
          v
MemoryLoader (parent = application loader)
  defines generated.Hello
  generated.Hello implements api.Greeting from parent
~~~

Nếu custom loader tự định nghĩa một bản `api.Greeting` khác bytecode nhưng cùng tên, JVM coi đó là **interface khác**, và cast từ generated object về `api.Greeting` phía host có thể thất bại. Không đủ để hai lớp API có cùng source/bytecode; chúng phải chia sẻ **defining loader identity**.

### Ranh giới khi compile với dependency riêng

Một plugin có thể compile dựa vào thư viện v2 nhưng runtime parent-first lại nạp v1. Nếu method chữ ký không tồn tại ở v1, có thể gặp `NoSuchMethodError` khi liên kết/gọi. Vì vậy:

- Chọn compile classpath/module path **phù hợp** với dependency generation sẽ dùng khi chạy.
- Đặt shared APIs ở parent.
- Cô lập private dependencies đúng namespace nếu cần version khác.
- Đừng cho private dependency types rò rỉ vào public signatures qua boundary.
- Kiểm tra code generation dưới nhiều runtime configurations, không chỉ “compile succeeded”.

## 7. Biên dịch động với file trên đĩa khác gì trong RAM?

Một cách đơn giản để thử biên dịch động là ghi source ra thư mục tạm rồi gọi compiler:

~~~text
generated-src/generated/Hello.java
             |
          javac
             |
          generated-out/generated/Hello.class
             |
       URLClassLoader
             |
        generated.Hello
~~~

Cách này dễ debug: mở được `.java`, `.class` và phân tích bằng `javap`. Nhưng nó tạo file tạm, cần dọn dẹp an toàn, giới hạn quyền truy cập và quản lý URLClassLoader/file handles.

Dùng bộ nhớ:

~~~text
String source
     |
JavaCompiler / JavaFileObject
     |
byte[] map
     |
MemoryLoader
     |
Class<?> / object
~~~

Giảm nhu cầu I/O trung gian và thuận tiện cho runtime template/generation. Đổi lại, việc debug stack trace/class-file metadata và resource lookup cần được thiết kế rõ.

| Tiêu chí | Disk output + URLClassLoader | In-memory JavaFileObject + MemoryLoader |
|---|---|---|
| Xem class files khi debug | Dễ hơn | Phải chủ động xuất bytes/log |
| Cần tệp tạm | Có | Không cho generated output |
| Resource/JAR integration | URLClassLoader có sẵn cơ chế URL | Phải triển khai riêng nếu cần |
| Cleanup | File/JAR handles, temp directory, stream | Map bytes và references/lifecycle |
| Loader ownership | URLClassLoader instance | MemoryLoader instance |
| Bytecode verification | JVM vẫn thực hiện | JVM vẫn thực hiện |
| Reload class cùng binary name | Thường loader generation mới | Loader generation mới |
| Support compiler diagnostics | Compiler API | Compiler API |

**Không phải** “in-memory luôn an toàn và nhanh hơn”. Source templates phức tạp, số lượng class lớn, annotation processors hoặc constant pools lớn đều có chi phí biên dịch/caching/metaspace đáng kể.

## 8. Lỗi biên dịch: đừng chuyển mọi thứ thành `ClassNotFoundException`

Nếu `getTask(...).call()` trả `false`, đây trước hết là **compilation failure**, ví dụ syntax error, missing imported type, incompatible method signature hoặc unsupported option.

`DiagnosticCollector` có thể cung cấp:

~~~java
for (Diagnostic<? extends JavaFileObject> d
        : diagnostics.getDiagnostics()) {
    System.out.println(d.getKind());
    System.out.println(d.getLineNumber());
    System.out.println(d.getColumnNumber());
    System.out.println(d.getMessage(Locale.ROOT));
}
~~~

Đây là **mảnh code minh họa**, không phải ví dụ chạy độc lập; cần biến `diagnostics` và imports thích hợp.

Nên giữ các thông tin:

- Generated source identifier, ví dụ `string:///generated/Hello.java`.
- Compiler options và target `--release`.
- Compilation diagnostics (kind, line/column, message) có giới hạn độ dài.
- Artifact/template version và dependency classpath/module path.
- Số lượng class files đã sinh và hash của bytes.
- Không đưa source code chứa secrets hoặc dữ liệu người dùng nhạy cảm vào log công khai.

**Phân biệt lỗi theo giai đoạn:**

| Giai đoạn | Ví dụ lỗi | Hướng điều tra |
|---|---|---|
| Sinh source | Template tạo dấu ngoặc sai | Log template/generator version và source an toàn |
| Biên dịch | `error: cannot find symbol` | Compile classpath/module path và source |
| Xuất class-file | `IOException` từ file manager | OutputStream/location/capacity/lifecycle |
| Nạp class | `ClassNotFoundException` | Bytes map, tên binary, parent delegation |
| Định nghĩa class | `ClassFormatError` | Class-file bytes không hợp lệ |
| Verification/linkage | `VerifyError`, `NoClassDefFoundError`, `NoSuchMethodError` | Stack maps, dependency, method signature/version |
| Access/package | `IllegalAccessError`, `SecurityException` | Runtime package, module exports/opens, package ownership |
| Initialization | `ExceptionInInitializerError`, `NoClassDefFoundError` sau init fail | Code `static` và nguyên nhân gốc |
| Reflection/instance | `NoSuchMethodException`, `InvocationTargetException` | Constructor/method thực và cause của target |

**Không bắt `LinkageError` rồi tùy tiện gọi lại `defineClass`**, vì lỗi có thể đến từ việc class name đã được định nghĩa trong loader. Cần hiểu nguyên nhân và quyết định generation strategy thích hợp.

## 9. Các ràng buộc package, protection domain và module

### Package và loading constraints

`defineClass` nhận **binary name** và bytes. JVM yêu cầu class-file phù hợp, kiểm tra package/security/linkage. Một loader không thể định nghĩa hai class cùng tên trong chính nó để “cập nhật phiên bản”.

Nếu hai class cùng package cần package-private access, JVM xem chúng cùng runtime package chỉ khi **package name và defining loader** khớp. Dùng hai loader khác nhau có thể gây `IllegalAccessError` dù hai source đều khai báo `package generated;`.

### ProtectionDomain và CodeSource

`ClassLoader.defineClass` có overload nhận `ProtectionDomain`:

~~~java
Class<?> defineClass(
    String name, byte[] data, int offset, int length,
    ProtectionDomain protectionDomain)
~~~

Đây là **chữ ký minh họa** của overload `protected final`, không phải lời gọi trên ClassLoader từ mã bên ngoài.

- Protection domain, CodeSource, certificate/signers và policy package sealing có những ý nghĩa runtime riêng.
- Nếu custom loader phân phối class từ JAR đã ký, phải duy trì **tính nhất quán của certificates trong package** theo hợp đồng ClassLoader.
- Source URL/code origin cho class in-memory không tự xuất hiện; nếu cần audit, ghi origin/generation/hash rõ ràng trong loader metadata.
- **Không hiểu ProtectionDomain là sandbox sẵn có** có thể chạy mã chưa tin cậy an toàn. Trong Java hiện đại, cần giải pháp an toàn mạnh hơn ở biên process/OS.

### Named modules

Custom `MemoryLoader` tự định nghĩa classes thông thường trong **unnamed module** gắn với loader ấy. Việc source hoặc bytes có `module-info.class` không đồng nghĩa mọi class lập tức trở thành một named module đã được JPMS resolve.

Nếu yêu cầu thật sự là named modules, dùng module path, `ModuleFinder`, `Configuration`, `ModuleLayer` và loader mappings thích hợp. Các khai báo `exports`/`opens`/`requires` quyết định những mặt khác của accessibility; loader visibility không tự bỏ qua JPMS.

### Class trong `java.*`

JDK không cho một custom class loader thông thường tùy ý định nghĩa class thuộc `java.*`. `defineClass` có thể ném `SecurityException` cho những tên như vậy. Không sử dụng namespace `java.` để thử nghiệm generation.

## 10. Quản lý tài nguyên và resource lookup

Mã runtime có thể cần cả dữ liệu ngoài class-file: file cấu hình, template, `META-INF/services` hoặc resource của thư viện.

**Class bytes map không tự là resource registry.** `MemoryLoader.findClass` trong demo chỉ dùng map để tìm class bytes; `ClassLoader.getResource(...)`/`getResourceAsStream(...)` có policy và hook riêng. Nếu muốn serve resources từ memory, có thể:

1. Xây **API resource riêng** trả stream mới từ immutable bytes cache.
2. Override resource lookup đúng hợp đồng, cung cấp URL/URL handler khi thực sự cần `URL` semantics, quản lý lifetime của dữ liệu.
3. Đóng gói plugin thành JAR và dùng `URLClassLoader` nếu cần cơ chế resource/JAR/manifest tích hợp.
4. Nếu dùng named modules, áp dụng quy tắc module resource encapsulation và `opens`.

**Không cache chung một `InputStream` cho nhiều caller**, vì stream có con trỏ đọc và trạng thái close. Cache byte[] immutable hoặc metadata nguồn rồi trả **một stream mới mỗi lần**.

Với `ServiceLoader`, provider files trong `META-INF/services` hoặc `provides` của JPMS cần nằm đúng vùng tìm kiếm. Một custom in-memory loader không tự làm provider discovery khi bạn chỉ cung cấp `findClass`.

### Resource và class loading policy là hai nhánh

~~~text
Class loading:
  loadClass("generated.Hello")
       -> parent / findClass -> defineClass

Resource lookup:
  getResource("generated/config.txt")
       -> parent / findResource -> URL / stream
~~~

Nếu override `loadClass` để child-first, không có nghĩa `getResource` trở thành child-first. Sự khác biệt này đặc biệt nguy hiểm khi host/plugin chứa resource cùng tên. Cần test cả hai thứ riêng.

## 11. Concurrency và caching khi nhiều thread cùng yêu cầu class

Với custom `ClassLoader`, class loading mặc định dùng `getClassLoadingLock(name)` để điều phối cạnh tranh. Nếu chỉ override `findClass` như demo, framework loader tiếp tục kiểm tra class đã được load và định nghĩa lớp theo đồng bộ thích hợp.

Tuy nhiên, **compiler/file manager tự viết vẫn là shared mutable state** nếu được dùng chung. Không suy ra có class-loading lock thì toàn bộ compiler pipeline thread-safe.

Một thiết kế production có thể:

~~~text
Concurrent requests for template T, version V
                      |
                      v
       Cache key = (T, V, compiler options, deps hash)
                      |
                 single-flight
                      |
                 compile once
                      |
             immutable bytes snapshot
                      |
         +------------+-------------+
         |                          |
  Loader Generation A        Loader Generation B
         |                          |
   own Class<?> A              own Class<?> B
~~~

Key cache cần gồm:

- Template/source hash.
- `--release` / compiler version/options (nếu ảnh hưởng bytes).
- Classpath/module-path/dependency artifact versions.
- Annotation processing hoặc bytecode transformer version.
- Plugin generation/security policy nếu có.

Có thể chia sẻ **immutable bytecode bytes** giữa loaders khi generation/source tương đương, nhưng **không chia sẻ `Class<?>` runtime object** như thể nó được định nghĩa ở mọi loader.

### `ConcurrentHashMap.computeIfAbsent` không thay khóa classloading

`computeIfAbsent` giúp tạo cache entry atomically nhưng **không tự thay** `ClassLoader.getClassLoadingLock(name)`, `findLoadedClass` hoặc quy tắc `defineClass`. Không gọi một callback mapping có I/O rất lâu và update đệ quy cùng map rồi kỳ vọng mọi thứ không bị block/deadlock.

Nếu compile chậm, dùng single-flight placeholder như `FutureTask<byte[]>` hoặc `CompletableFuture` với lifecycle/retry rõ ràng. Khi compile lỗi:

- Không cache `null` trong ConcurrentHashMap.
- Không giữ failed future mãi trừ khi chủ động thiết kế negative cache/TTL.
- Phân biệt lỗi source vĩnh viễn và lỗi I/O/cache tạm thời.
- Không trả partial output map nếu compiler chưa báo thành công.
- Không đưa cùng mutable byte[] cho code có thể sửa trong lúc một loader gọi `defineClass`.

### Parallel-capable loader

Nếu thực sự nạp nhiều **binary class names khác nhau** song song, subclass có thể đăng ký parallel-capable bằng `registerAsParallelCapable()` khi class được initialize; tất cả superclass trong chuỗi (ngoại trừ `Object`) cần thỏa điều kiện đăng ký. Điều này cho phép loader dùng lock theo tên class thay vì khóa toàn loader trong `loadClass` mặc định.

Không bắt buộc demo nhỏ có ít class name phải đăng ký parallel-capable, và việc đăng ký **không tự làm compiler/file manager/resource cache thread-safe**.

## 12. Loader lifecycle, class unloading và hot reload

Điểm dễ mắc sai lầm là viết:

~~~java
loader = new MemoryLoader(newBytes, parent);
~~~

rồi nghĩ class cũ đã được unload. Thực tế:

- Class cũ vẫn tồn tại nếu có `Class<?>`, object instance, static cache, MethodHandle, Method/Constructor reflection object, running thread hay reference khác giữ nó/defining loader sống.
- Một loader không có `close()` không có nghĩa có thể quên lifecycle. MemoryLoader của demo giữ map bytes và Java runtime objects liên quan.
- `URLClassLoader.close()` đóng file/JAR handles, **không bắt JVM unload class ngay lập tức**.
- JVM có thể unload classes khi defining loader và những đối tượng liên quan đủ điều kiện thu gom; thời điểm không được bảo đảm theo lệnh ứng dụng.
- Nếu source/code gen thay đổi, một **loader generation mới** thường là đường thông thường để tạo runtime class mới cùng binary name.

Một lifecycle thực tế:

~~~text
Generation 1 (ACTIVE)
       |
       +--> stop accepting new plugin work
       |
       v
     DRAINING
       |
       +--> finish ongoing requests
       +--> stop plugin threads/scheduled tasks
       +--> close streams/JAR handles
       +--> unregister callbacks/services/listeners
       +--> clear caches holding Class/instance/loader
       +--> restore thread context loader
       |
       v
     RETIRED (eligible for GC when references disappear)

Generation 2 (ACTIVE)
       |
       +--> new loader + new generated classes
~~~

**Lưu ý:** để có hot reload không gián đoạn, host có thể cần **immutable shared API** ở parent và một **atomic generation handle** trỏ về tập provider mới. Request cũ tiếp tục dùng generation cũ đến khi hoàn tất, request mới dùng generation mới; sau đó gỡ references cũ. Không swap từng class name trong cùng loader rồi trông đợi JVM cập nhật ngay.

## 13. Khi nào dùng compiler, bytecode generator hoặc proxy?

| Nhu cầu | Hướng phù hợp | Cân nhắc |
|---|---|---|
| Sinh class từ Java source/template mà người phát triển kiểm soát | `JavaCompiler` API | Compiler phải có, chi phí compile và diagnostics |
| Sinh nhiều adapter/proxy hiệu năng cao | Byte Buddy/ASM hoặc runtime framework thích hợp | Hiểu bytecode verification, package, loader lifecycle |
| Tạo implementation của interface bằng handler | `java.lang.reflect.Proxy` | Giới hạn theo interface, method invocation handler |
| Tạo class cùng loader/package với một lookup class | `MethodHandles.Lookup.defineClass` khi hợp đồng cho phép | Lookup privileges, same package/loader |
| Class ngắn hạn phục vụ framework runtime | Hidden class APIs khi đúng use case | Không dùng như class tìm được bình thường bằng binary name |
| Plugin/JAR có nhiều resources/phụ thuộc | `URLClassLoader` + policy phù hợp | Quản lý URL/JAR handles, resources và parent delegation |
| Runtime Java 17+ không có compiler | Precompile trước, dùng compiled artifact hoặc công nghệ khác | Không mặc định `ToolProvider` luôn trả compiler |
| Plugin không tin cậy | Process/container isolation | Classloader không phải sandbox |

Đôi khi **không cần sinh class**: dùng lambda, strategy pattern, reflection, `MethodHandle` hoặc cấu hình dữ liệu có thể đơn giản hơn. Dynamic compilation nên được chọn khi sự linh hoạt thực sự bù được chi phí bộ nhớ, CPU và sự phức tạp của type/lifecycle.

## 14. An toàn: không biên dịch và chạy source tùy ý trong JVM host

**Cảnh báo thiết kế:** nhận source Java từ người dùng chưa tin cậy, gọi `JavaCompiler` và sau đó load/execute trong cùng JVM **không phải cách ly an toàn**.

Lý do:

- Compiler có thể tiêu thụ CPU/heap/disk và xử lý classpath/module path ngoài dự kiến.
- Annotation processors có thể chạy code trong quá trình compile nếu được cấu hình/cho phép. Demo sử dụng `-proc:none` để tắt processor, nhưng **điều này không tạo sandbox cho code được compile**.
- Bytecode đã load có thể gọi API file system, network, reflection hoặc tài nguyên tiến trình mà Java/OS cho phép.
- Một class có static initializer, thread vô hạn, recursion sâu hoặc giữ reference tới loader có thể phá vòng đời host.
- Dynamic generation có thể tạo vô hạn class unique, gây áp lực metaspace/heap và class unloading khó.

Nếu phải phục vụ mã chưa tin cậy:

1. Dùng process/container riêng với permissions tối thiểu và giới hạn CPU/memory/time.
2. Không dùng application classpath chứa secrets/internal APIs cho compiler không tin cậy.
3. Giới hạn kích thước source, số lượng classes, dependencies và thời gian compile.
4. Kiểm soát annotation processing, plugins/compiler options và artifact provenance.
5. Kiểm tra chữ ký/hash khi nhận bytecode sẵn có từ nguồn ngoài.
6. Giao tiếp qua RPC/schema thay vì cast trực tiếp các class không tin cậy vào object graph của host.
7. Không coi SecurityManager hoặc ClassLoader riêng là lớp sandbox bảo mật mặc định trên Java 17+.

## 15. Debug từ compiler output tới defining loader

Nếu class mới “không hoạt động”, chẩn đoán theo **giai đoạn** thay vì thay classpath ngẫu nhiên.

### Bước 1 — Compiler có sẵn không?

~~~java
JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
System.out.println(compiler != null);
~~~

Nếu `false`, xác minh runtime đang chạy có compiler provider và module `java.compiler` hay không; có thể cần chạy với JDK đầy đủ hoặc cấu hình runtime image lại.

### Bước 2 — Compiler diagnostics nói gì?

Đọc `DiagnosticCollector`: loại lỗi, dòng, cột, message, source URI. Nếu compile task trả `false`, chưa đến bước class loading.

### Bước 3 — Có đúng class-file output không?

~~~java
Map<String, byte[]> generated = files.snapshot();
System.out.println(generated.keySet());
~~~

Đây là **mảnh minh họa**, cần đối tượng file manager `files` từ pipeline. Nếu class cần là `generated.Hello`, phải có entry theo binary name ấy. Nếu compiler sinh nested classes, nhiều entries khác cũng cần được lưu.

### Bước 4 — Parent có che class mới không?

~~~java
Class<?> actual = loader.loadClass("generated.Hello");
System.out.println(actual.getClassLoader() == loader);
~~~

Nếu `false`, parent-first delegation có thể đã trả một class cùng binary name từ parent. Tên class đúng không chứng minh bytes vừa sinh đã được dùng.

### Bước 5 — Nhìn metadata và nguồn

~~~java
Class<?> type = loader.loadClass("generated.Hello");
System.out.println("type=" + type.getName());
System.out.println("loader=" + type.getClassLoader());
System.out.println("module=" + type.getModule());
~~~

Các mảnh code chẩn đoán cần biến `loader` và imports thích hợp.

Khi dùng class từ source/JAR, có thể xem `type.getProtectionDomain().getCodeSource()` nếu tồn tại; **generated in-memory classes không tự có CodeSource URL tin cậy**. Nên tự ghi logical generation ID, source hash, compiler options và bytecode hash trong metadata/log do host quản lý.

### Bước 6 — Nếu lỗi xuất hiện ở runtime

Phân biệt:

- `ClassNotFoundException`: loader không tìm được binary name.
- `NoClassDefFoundError`: dependency không tìm được lúc resolve, tên class mismatch hoặc init trước đó thất bại.
- `ClassFormatError`/`VerifyError`: bytes/generator không thỏa JVM class format/verifier.
- `NoSuchMethodError`: runtime dependency signature không khớp bản compile.
- `IllegalAccessError`: runtime package/module access không phù hợp.
- `ExceptionInInitializerError`: code `static` chạy rồi lỗi.
- `ClassCastException`: thường liên quan runtime type identity khi host/plugin có duplicate type definitions.

Không coi mọi lỗi trên là compiler lỗi. Cần stack trace, cause chain, defining loader, source bytes metadata và thời điểm load/link/initialize.

## 16. Những sai lầm phổ biến

1. **“Sinh được `byte[]` nghĩa là đã có runtime class.”** Sai: cần class definition trong JVM.
2. **“`JavaCompiler.getTask` tự biên dịch ngay.”** Sai: `call()` mới thực hiện task.
3. **“Biên dịch thành công nghĩa là mọi dependency runtime đều đúng.”** Sai: runtime loader có thể nhìn phiên bản khác.
4. **“`defineClass` tạo object và chạy constructor.”** Sai: nó định nghĩa Class, còn khởi tạo instance là bước khác.
5. **“Một loader có thể định nghĩa lại cùng tên để reload.”** Sai: cần generation loader mới hoặc redefinition API chuyên dụng.
6. **“Cùng binary name và bytecode là cùng runtime type.”** Sai nếu defining loader khác.
7. **“Có thể gọi `defineClass` trực tiếp trên system loader từ mọi class.”** Sai: protected final, phải dùng cơ chế hợp lệ.
8. **“`Lookup.defineClass` định nghĩa bất kỳ package/classloader nào.”** Sai: Lookup gắn với quyền, package và defining loader.
9. **“Các class do custom loader định nghĩa tự thành named JPMS modules.”** Sai: thông thường thuộc unnamed module của loader.
10. **“Resource lookup tự đọc map class bytes.”** Sai: `findClass` và `findResource` là đường API khác nhau.
11. **“Compiler file manager thread-safe vì ClassLoader thread-safe.”** Sai: các đối tượng/state có hợp đồng đồng thời riêng.
12. **“Cache `Class<?>` toàn cục theo tên là tốt.”** Sai: phá loader identity và giữ loader cũ sống.
13. **“`URLClassLoader.close()` lập tức unload mọi class.”** Sai: chỉ đóng tài nguyên loader; unloading phụ thuộc GC/references.
14. **“`-proc:none` làm source user nhập trở nên an toàn.”** Sai: chỉ tắt annotation processing trong compilation, không sandbox code runtime.
15. **“`ToolProvider.getSystemJavaCompiler()` chắc chắn không null.”** Sai với runtime image thiếu compiler.
16. **“`ClassNotFoundException` và `NoClassDefFoundError` hoàn toàn giống nhau.”** Sai: khác giai đoạn/nguyên nhân.
17. **“Thêm `-classpath` cho compiler là đủ cho named module runtime.”** Sai: còn module resolution, layer, readability/exports.
18. **“File `generated.Hello.class` là tên class để `loadClass`.”** Sai: gọi bằng `generated.Hello`; đường dẫn entry dùng dấu `/`.
19. **“Class loader là sandbox để chạy code khách.”** Sai: cần ranh process/OS nếu không tin cậy.
20. **“Giữ generated classes trong static map mãi không có hậu quả.”** Sai: có thể gây metaspace/heap pressure và plugin classloader leak.

## 17. Checklist production

- [ ] Chọn rõ cơ chế **source compiler**, **bytecode generator** hay **proxy**, không dùng generation khi một strategy object đơn giản đủ đáp ứng.
- [ ] Compiler tồn tại ở runtime image mục tiêu; `--release` tương thích JVM đích.
- [ ] Source name và public class/binary name khớp; compiler output map chứa đủ nested/helper classes.
- [ ] Diagnostics có line/column/message và không lộ dữ liệu nhạy cảm.
- [ ] File manager/streams được đóng đúng cách, không dùng chung mutable state không kiểm soát.
- [ ] Parent loader định nghĩa các shared interfaces/DTOs theo hợp đồng.
- [ ] Compilation classpath và runtime loader có dependency versions tương thích.
- [ ] Generated bytes là snapshot bất biến trước khi đưa cho loader.
- [ ] `loadClass`/`getClassLoadingLock`/`findLoadedClass` không bị bypass sai cách.
- [ ] `defineClass` dùng binary name đúng, không lạm dụng protected API.
- [ ] Runtime module/package/access constraints được kiểm tra.
- [ ] Có test generated bytecode khác version, corrupted class-file, missing dependencies và duplicate definition.
- [ ] Có bound/single-flight cho compilation cache nếu nhiều request cùng source.
- [ ] Có giới hạn CPU/heap/metaspace, số classes, source size, compile timeout.
- [ ] Không dùng compile/load trong cùng process cho code chưa tin cậy.
- [ ] Có generation/version ID và cơ chế chuyển thế hệ không làm request đang chạy lỗi.
- [ ] Có shutdown/drain để ngừng thread/plugin tasks và loại references tới loader cũ.
- [ ] Resource, SPI, TCCL và caches được kiểm tra riêng với class loading.
- [ ] JFR/JVM classloading metrics hoặc log có thể giúp chẩn đoán leaks khi cần.

## 18. Bài tập tự kiểm tra

**Câu 1.** Java source `generated.Hello` đã có trong một String. API nào biến source thành bytes mà không cần tự viết class-file format?

**Đáp án:** `javax.tools.JavaCompiler` với một `JavaFileObject` và file manager phù hợp. Gọi `getTask(...).call()`, thu output `.class`.

**Câu 2.** Cần đưa bytes vào JVM thành runtime class. Có thể gọi `defineClass` trực tiếp trên một ClassLoader bất kỳ không?

**Đáp án:** Không từ mã không có quyền truy cập, vì `defineClass` là protected final. Thường tạo custom ClassLoader và override `findClass`, để `loadClass` chuẩn điều phối.

**Câu 3.** Cùng binary name `generated.Hello` nhưng compile hai phiên bản rồi định nghĩa trong hai loaders. Hai `Class<?>` có bằng nhau theo `==` không?

**Đáp án:** Không. JVM xét cả defining loader trong runtime type identity.

**Câu 4.** Compile class mới thành công nhưng lúc dùng gặp `NoSuchMethodError`. Nên điều tra gì?

**Đáp án:** Runtime dependency defining loader/version, method descriptor được compile và binary compatibility, không chỉ compiler diagnostics.

**Câu 5.** Thay `byte[]` trong map của loader cũ có cập nhật `Class<?>` đã định nghĩa không?

**Đáp án:** Không. Có thể tạo loader generation mới; instrumentation/redefinition có quy tắc riêng.

**Câu 6.** Một generated class implement SPI của host nhưng host cast thất bại dù tên interface giống nhau. Khả năng gì?

**Đáp án:** Hai bản SPI được định nghĩa bởi loaders khác nhau. Shared API nên do parent chung định nghĩa.

**Câu 7.** Vì sao `ToolProvider.getSystemJavaCompiler()` có thể trả `null`?

**Đáp án:** Compiler provider/module không có trong runtime image hoặc môi trường không cung cấp system compiler; cần full JDK hoặc cấu hình khác.

**Câu 8.** Đặt `-proc:none` trong demo có biến việc thực thi generated code thành an toàn không?

**Đáp án:** Không. Nó chỉ tắt annotation processing ở compilation; runtime code vẫn có quyền của process.

**Câu 9.** `ClassLoader.close()` có phải API của mọi ClassLoader không?

**Đáp án:** Không. `ClassLoader` cơ sở không có `close()`; `URLClassLoader` triển khai `Closeable`. Loader custom lưu bytes trong RAM cần quản lý references/lifecycle phù hợp.

**Câu 10.** Khi compiled source chứa cả inner class `Hello$Inner`, cần lưu những output nào?

**Đáp án:** Tất cả class files mà compiler sinh ra, gồm `generated.Hello` và `generated.Hello$Inner` khi có, keyed theo binary names, để loader tìm được khi JVM cần.

## 19. Mô hình quyết định

~~~text
Cần sinh behavior động?
          |
          v
Có cần một Class mới không?
      /             \
    Không            Có
      |               |
Lambda/strategy   Source Java hay bytecode trực tiếp?
                      /                   \
                   Source              Bytecode
                      |                   |
                JavaCompiler         generator ASM/...
                      |                   |
               Diagnostics +          validate class
               classfile bytes        file version
                      +---------+---------+
                                |
                         Loader generation
                                |
                    shared API từ parent?
                                |
                    defineClass đúng name
                                |
                      Class<?> và instance
                                |
                 verify type identity/linkage
                                |
                metadata/cache/resource/lifecycle
                                |
                retire loader generation khi xong
~~~

**Tổng kết:** Sinh source/bytecode là bước **tạo dữ liệu**; JavaCompiler là bước **tạo class-file bytes**; ClassLoader là bước **xác lập runtime type identity**. Một hệ thống dynamic class generation đúng cần đồng thời thiết kế compiler diagnostics, classpath/version, package/module access, loader concurrency, nguồn resource và cơ chế thu hồi generation. Hai class cùng tên có thể cùng tồn tại nhờ hai loaders, nhưng không thể “ghi đè” class cũ trong cùng loader như thay file văn bản.

### Tài liệu chính thức

- [Java SE 17 — JavaCompiler API](https://docs.oracle.com/en/java/javase/17/docs/api/java.compiler/javax/tools/JavaCompiler.html)
- [Java SE 17 — JavaFileManager API](https://docs.oracle.com/en/java/javase/17/docs/api/java.compiler/javax/tools/JavaFileManager.html)
- [Java SE 17 — SimpleJavaFileObject API](https://docs.oracle.com/en/java/javase/17/docs/api/java.compiler/javax/tools/SimpleJavaFileObject.html)
- [Java SE 17 — ClassLoader / defineClass](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html)
- [Java SE 17 — MethodHandles.Lookup.defineClass](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandles.Lookup.html#defineClass(byte%5B%5D))
- [JVMS 17 §5.3 — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-class-definition-007">Instrumentation: transform trước khi định nghĩa lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Instrumentation: transform trước khi định nghĩa lớp

Trong Java, **instrumentation** là cách quan sát hoặc điều chỉnh **bytecode của class** để bổ sung chức năng như tracing, đo thời gian, thu thập coverage hoặc thay đổi một phần hành vi mà không cần sửa trực tiếp mã nguồn ứng dụng. Với **load-time instrumentation**, JVM cho một `ClassFileTransformer` cơ hội nhận bytes ClassFile, biến đổi chúng và trả bytes mới **trước khi class được định nghĩa** bởi JVM.

Chủ đề quan trọng ở đây là **thời điểm transform**, không chỉ thuật ngữ “agent”. Một transform được đăng ký sớm có thể thay đổi bytes mà JVM chuẩn bị sử dụng trong `defineClass`; trái lại, khi class đã được định nghĩa, tạo thêm transformer **không tự chạy lại quá trình định nghĩa đó**. Trường hợp sau cần cơ chế riêng như `retransformClasses` hoặc `redefineClasses` nếu JVM/class hỗ trợ và các ràng buộc được thỏa mãn.

**Ý tưởng cốt lõi:**

~~~text
Agent được khởi động bằng -javaagent
         |
         v
premain(..., Instrumentation)
         |
         v
instrumentation.addTransformer(transformer)
         |
         v
ClassLoader yêu cầu định nghĩa một class mới
         |
         v
JVM/Instrumentation gọi ClassFileTransformer.transform(...)
         |
         +--- trả null ---> giữ bytecode hiện tại
         |
         +--- trả byte[] --> dùng bytes đã biến đổi cho bước tiếp
         |
         v
các transformer tiếp theo (nếu có)
         |
         v
JVM kiểm tra, liên kết, định nghĩa Class<?> theo quy tắc
         |
         v
class được sử dụng/khởi tạo khi thích hợp
~~~

Đây là đường đi **khái quát**. Transformer có thể được gọi ở những ngữ cảnh ngoài new class loading, tùy registration/capabilities; JVM cũng thực hiện nhiều bước kiểm tra và liên kết khác không thể giản lược thành một lời gọi `defineClass` duy nhất. Quan trọng nhất: **trả bytecode đã biến đổi không có nghĩa bypass verifier, access control, type identity hoặc module rules**.

Bài này trình bày `java.lang.instrument` trong Java 17, Java agent, `ClassFileTransformer`, `Instrumentation.addTransformer`, thứ tự transform, các cơ chế load/retransform/redefine, lỗi thường gặp, và một **demo Java 17 tự chạy** thay giá trị chuỗi trong constant pool trước khi JVM định nghĩa class mục tiêu.

## 1. Phân biệt instrumentation và class loading thông thường

**Class loading** giải quyết câu hỏi: “Ai tìm, nạp và định nghĩa ClassFile này?” Với class loader tùy chỉnh, câu trả lời thường xuất hiện quanh `loadClass`, `findClass` và `defineClass`. JVM dùng **binary name + defining class loader** làm một phần định danh runtime của class/interface.

**Instrumentation** giải quyết câu hỏi khác: “Trước khi định nghĩa/áp dụng class bytes, có được quan sát hoặc thay đổi biểu diễn ClassFile không?”

| Thành phần | Vai trò | Điều không tự thực hiện |
| --- | --- | --- |
| `ClassLoader.loadClass` | Tìm Class theo tên, dùng cache/delegation theo chính sách | Không tự instrument mọi ClassFile |
| `ClassLoader.findClass` | Subclass tìm nguồn bytecode rồi có thể `defineClass` | Không phải hook instrumentation chuẩn toàn JVM |
| `ClassLoader.defineClass` | Đề nghị JVM định nghĩa class từ bytes theo loader/name | Không bảo đảm bytes được nhận chính xác như ban đầu nếu có transformer |
| `Instrumentation` | API do JVM cấp cho agent để đăng ký transformers, truy vấn và yêu cầu một số thao tác | Không phải ClassLoader và không tự bảo đảm mọi class đều modifiable |
| `ClassFileTransformer` | Nhận ClassFile bytes để biến đổi/quan sát ở giai đoạn JVM xử lý định nghĩa hoặc cập nhật | Không tự nạp một class hay tạo một defining loader mới |
| `-javaagent` + `premain` | Đưa agent vào JVM khi khởi động, trước application `main` | Không hứa mọi class nền tảng còn chưa được load khi agent đăng ký |

Cùng một binary name `com.example.Service` mà hai defining loader khác nhau định nghĩa thì vẫn là **hai class JVM khác nhau**, dù instrumentation áp dụng cùng phép biến đổi. Transformer **không hợp nhất type identity** của chúng.

### Cơ chế tải lớp tùy chỉnh và agent có thể cùng tồn tại

~~~text
Host / plugin yêu cầu loadClass("plugin.impl.Task")
                    |
                    v
          PluginClassLoader.findClass
                    |
                    v
                 byte[] ClassFile
                    |
                    v
       defineClass(name, bytes, off, len)
                    |
                    v
        JVM transformer chain có thể chạy
                    |
                    v
          Class<?> của PluginClassLoader
~~~

Nếu dùng `Instrumentation` được JVM quản lý, transformer sẽ có cơ hội xử lý **class definition mới** theo registration của agent, kể cả class được custom loader định nghĩa. Điều này **không đồng nghĩa mọi class chắc chắn được chỉnh**: transformer thường lọc theo `className`, loader, module, class redefinition state hoặc chính sách ứng dụng.

Một loader còn có thể **tự thay bytes** trước `defineClass`, chẳng hạn thực hiện một bước preprocess bytecode của riêng nó. Khi đó phải phân biệt **bytes mà loader chủ động đổi** với **transformer do JVM gọi** trong đường instrumentation. Cả hai diễn ra trước khi class tương ứng hoàn tất định nghĩa nhưng thuộc các tầng API khác nhau.

## 2. Java agent được kích hoạt như thế nào?

Một Java agent thông thường được đóng gói thành **JAR riêng** có main manifest attribute:

~~~text
Premain-Class: Agent
~~~

Trong đó `Agent` là **binary name của agent class**, không phải tên file `Agent.java` hay đường dẫn `Agent.class`. Agent class cung cấp method public static:

~~~java
public static void premain(
        String agentArgs, java.lang.instrument.Instrumentation inst) {
    // đăng ký transformer trước khi application main chạy
}
~~~

Đây là **fragment chữ ký**, không phải chương trình độc lập. Trường hợp khởi động JVM với:

~~~bash
java -javaagent:transform-agent.jar -cp app-classes demo.Main
~~~

JVM khởi tạo agent, gọi `premain`, sau khi `premain` hoàn tất mới gọi `demo.Main.main`. Nếu thiếu `Premain-Class`/method phù hợp hoặc `premain` ném lỗi chưa xử lý, việc khởi động agent có thể thất bại và JVM chấm dứt trước khi application chạy.

### Phân biệt premain, agentmain và main

- `premain`: đường agent khởi động bằng `-javaagent`, được gọi **trước application `main`**.
- `agentmain`: đường agent được khởi động **sau khi JVM đã chạy** thông qua cơ chế attach được runtime hỗ trợ; thường dùng manifest attribute `Agent-Class`. Không phải JVM nào hoặc cấu hình nào cũng cho phép dynamic attach.
- `main`: entry point ứng dụng thông thường; không phải callback instrumentation.

Bài này dùng **`premain`**, vì muốn chứng minh một transform diễn ra **trước khi class `demo.Greeter` được định nghĩa lần đầu**. Không dùng attach/retransform để cố chỉnh class đã load.

**Chú ý phạm vi JVM:** `premain` chạy trước application `main` nhưng JVM đã khởi tạo và có thể đã nạp một số class nội bộ/JDK để gọi agent. Không thể suy “agent bắt đầu trước mọi class loading của JVM”.

### Multiple agents có tương tác thế nào?

JVM có thể khởi động nhiều agent thông qua nhiều tùy chọn `-javaagent`. Khi nhiều transformer tồn tại, các kết quả được **xâu chuỗi** theo nhóm/thứ tự registration phù hợp hợp đồng `ClassFileTransformer`. Một transformer có thể nhận **bytes đã được transformer trước thay đổi**, nên tính tương thích và idempotency rất quan trọng. Không viết code dựa vào giả định “transformer của tôi luôn nhận đúng file gốc trên disk”.

## 3. ClassFileTransformer.transform nhận và trả dữ liệu gì?

Một trong hai overload của `ClassFileTransformer` (overload lịch sử vẫn dùng được ở Java 17):

~~~java
@Override
public byte[] transform(
        ClassLoader loader,
        String className,
        Class<?> classBeingRedefined,
        java.security.ProtectionDomain protectionDomain,
        byte[] classfileBuffer) {
    // trả null nếu không đổi bytes
    return null;
}
~~~

Đây là **fragment** dùng bên trong một implementation của `ClassFileTransformer`. Ý nghĩa các tham số:

| Tham số | Ý nghĩa |
| --- | --- |
| `loader` | ClassLoader tham gia định nghĩa class; có thể là `null` cho bootstrap loader theo API. |
| `className` | Tên class theo dạng **internal name** với dấu `/`, ví dụ `demo/Greeter`. Có ngữ cảnh mà giá trị có thể null; không dereference mù quáng. |
| `classBeingRedefined` | Thường `null` khi đang định nghĩa class lần đầu; có Class tương ứng khi redefinition/retransformation theo trường hợp. |
| `protectionDomain` | Miền bảo vệ liên quan class, có thể cần cho chính sách/diagnostics. |
| `classfileBuffer` | Bytes ClassFile hiện tại mà transformer được yêu cầu xử lý; **không được sửa trực tiếp array đầu vào**. |

Java 9+ còn có overload:

~~~java
public byte[] transform(
        Module module,
        ClassLoader loader,
        String className,
        Class<?> classBeingRedefined,
        java.security.ProtectionDomain protectionDomain,
        byte[] classfileBuffer)
~~~

Đây là **chữ ký fragment**, không phải method body; `module` giúp biết module liên quan khi transform named-module classes. Java 17 cho phép override overload 5 đối số; overload 6 đối số có default implementation chuyển tiếp vào overload cũ theo API. Nếu cần filter theo JPMS module, có thể override overload chứa `Module`.

**Kết quả:**

- Trả **`null`** khi không sửa bytes: JVM tiếp tục dùng bytes hiện tại của transformer chain.
- Trả **mảng `byte[]` mới** chứa một biểu diễn ClassFile phù hợp khi muốn biến đổi. Cần bảo toàn định dạng ClassFile hợp lệ, không hỏng constant pool/method descriptors.
- Không sửa tại chỗ `classfileBuffer`: clone/copy hoặc thư viện bytecode có output buffer riêng. API cho phép JVM dùng buffer gốc ở những bước khác; sửa tại chỗ là sai hợp đồng.
- Nếu class bị JVM từ chối vì định dạng, kiểm tra hoặc type constraints thì transformer **không thể ép class ấy trở thành hợp lệ** chỉ bằng việc return một mảng tùy tiện.

### Tại sao className có dấu slash?

`className` trong callback dùng **internal form**:

~~~text
Binary name:       demo.Greeter
Transformer name:  demo/Greeter
Class file path:   demo/Greeter.class
~~~

Nếu viết `if ("demo.Greeter".equals(className))` trong transformer thì callback sẽ **không** bắt mục tiêu dự kiến. Trong ví dụ bên dưới dùng:

~~~java
if (!"demo/Greeter".equals(name)) {
    return null;
}
~~~

Đây là fragment đúng theo signature callback. Nên thêm điều kiện về **loader identity** nếu chương trình có nhiều bản cùng tên mà chỉ muốn biến đổi một defining loader cụ thể; lọc tên đơn thuần sẽ tác động mọi class mới thỏa tên ở nhiều loader.

## 4. Ví dụ Java 17 hoàn chỉnh: thay một UTF8 constant trước khi define class

Ví dụ tự chứa này có ba source files:

- `demo.Greeter` trả chuỗi literal `ORIGINAL`.
- `demo.Main` in kết quả gọi `Greeter.message()`.
- `Agent` đăng ký `ClassFileTransformer`. Khi JVM sắp định nghĩa `demo/Greeter`, transformer tìm một entry **`CONSTANT_Utf8_info`** trong constant pool chứa đúng tám byte ASCII `ORIGINAL`, tạo **bản sao byte array**, đổi sang tám byte `PATCHED!`, rồi trả array mới.

Hai chuỗi **cùng số byte (8)** để ví dụ tránh phải cập nhật cấu trúc/độ dài constant pool entry. Khác với thay byte tùy tiện ở mọi vị trí trong ClassFile, parser minh họa chỉ xét **các entry constant pool có tag UTF8 và đúng độ dài**. Đây là kỹ thuật học tập có scope rất hẹp; trong sản phẩm thực tế nên dùng thư viện bytecode chuyên dụng thay vì tự viết parser/transformer dạng byte patch.

### 4.1. Cấu trúc project

~~~text
instrumentation-demo/
├── src/
│   ├── Agent.java
│   └── demo/
│       ├── Greeter.java
│       └── Main.java
├── agent.mf
├── agent-classes/       (javac tạo)
├── app-classes/         (javac tạo)
└── transform-agent.jar  (jar tạo)
~~~

### 4.2. File src/demo/Greeter.java

~~~java
package demo;

public final class Greeter {
    public static String message() {
        return "ORIGINAL";
    }
}
~~~

Chuỗi `ORIGINAL` xuất hiện trong constant pool của ClassFile `demo.Greeter` do `javac --release 17` tạo. `message()` là method được app gọi, nên việc đổi literal trong ClassFile làm kết quả runtime thay đổi. Đây không phải phép sửa Java source trong file `Greeter.java`.

### 4.3. File src/demo/Main.java

~~~java
package demo;

public final class Main {
    public static void main(String[] args) {
        System.out.println("Message: " + Greeter.message());
    }
}
~~~

Code gọi method `Greeter.message()`, không dùng `public static final String` được compile-time constant-fold vào `Main`. Cách viết này giúp quan sát trực tiếp biến đổi bytes của `Greeter`, không vướng chuyện hằng số đã được inline ở caller.

### 4.4. File src/Agent.java

~~~java
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.Arrays;

public final class Agent {
    public static void premain(String args, Instrumentation inst) {
        System.out.println("Agent ready");
        inst.addTransformer(new PatchTransformer(), false);
    }

    private static final class PatchTransformer
            implements ClassFileTransformer {

        @Override
        public byte[] transform(ClassLoader loader, String name,
                Class<?> redefining, ProtectionDomain domain,
                byte[] bytes) {

            if (!"demo/Greeter".equals(name)
                    || redefining != null) {
                return null;
            }

            byte[] replacement =
                    patchConstantUtf8(bytes, "ORIGINAL", "PATCHED!");

            if (replacement != null) {
                System.out.println("Transformed: demo.Greeter");
            }

            return replacement;
        }
    }

    private static byte[] patchConstantUtf8(
            byte[] bytes, String original, String replacement) {

        byte[] from = original.getBytes(StandardCharsets.US_ASCII);
        byte[] to = replacement.getBytes(StandardCharsets.US_ASCII);

        if (from.length != to.length) {
            throw new IllegalArgumentException("length");
        }

        byte[] copy = bytes.clone();
        ByteBuffer cp = ByteBuffer.wrap(copy)
                .order(ByteOrder.BIG_ENDIAN);

        if (cp.getInt() != 0xCAFEBABE) {
            return null;
        }

        cp.getShort(); // minor version
        cp.getShort(); // major version
        int cpCount = Short.toUnsignedInt(cp.getShort());
        int patches = 0;

        for (int i = 1; i < cpCount; i++) {
            int tag = Byte.toUnsignedInt(cp.get());

            switch (tag) {
                case 1: {
                    int size = Short.toUnsignedInt(cp.getShort());
                    int start = cp.position();

                    if (size == from.length
                            && Arrays.equals(
                                    copy, start, start + size,
                                    from, 0, from.length)) {

                        System.arraycopy(
                                to, 0, copy, start, to.length);
                        patches++;
                    }

                    cp.position(start + size);
                    break;
                }

                case 3: case 4: case 9: case 10:
                case 11: case 12: case 17: case 18:
                    cp.position(cp.position() + 4);
                    break;

                case 5: case 6:
                    cp.position(cp.position() + 8);
                    i++;
                    break;

                case 7: case 8: case 16:
                case 19: case 20:
                    cp.position(cp.position() + 2);
                    break;

                case 15:
                    cp.position(cp.position() + 3);
                    break;

                default:
                    throw new IllegalArgumentException("tag: " + tag);
            }
        }

        return patches == 1 ? copy : null;
    }
}
~~~

**Giải thích code Agent:**

1. `premain` được JVM gọi trước application `main` khi lệnh chạy có `-javaagent`. Nó in `Agent ready` và đăng ký transformer bằng `inst.addTransformer(..., false)`. Giá trị `false` nghĩa transformer **không được đăng ký là retransformation-capable**; demo chỉ quan tâm initial class definition.
2. `transform` được JVM gọi khi định nghĩa class mới và trong những tình huống redefinition phù hợp API. Nó trả `null` cho mọi tên khác `demo/Greeter` hoặc khi đang xử lý lại một Class đã định nghĩa. Trong demo chuẩn chỉ có một class mới phù hợp.
3. `patchConstantUtf8` đọc **magic `CAFEBABE`**, minor/major version, số entries trong constant pool rồi lần lượt bỏ qua những entry theo độ rộng tương ứng. Tag `1` là `CONSTANT_Utf8_info`.
4. Với tag `1`, code lấy độ dài entry từ `u2` và so đúng tám byte ASCII. Nếu bằng `ORIGINAL`, nó copy tám byte `PATCHED!` **vào mảng clone**, không sửa mảng gốc của JVM.
5. Tag `5`/`6` (long/double) chiếm hai vị trí constant-pool index nên code tăng `i` thêm một. Các tag còn lại được skip theo định dạng ClassFile hỗ trợ đến Java 17.
6. Chỉ khi **chính xác một entry được sửa**, transformer trả byte array mới. Nếu không tìm được hoặc gặp hai entry phù hợp, nó trả `null` để demo không dùng một phép thay không rõ kết quả. Đây là cách tối thiểu hóa thay đổi trong ví dụ.
7. Khi transformer trả mảng đã thay, JVM tiếp tục các bước định nghĩa/kiểm tra class; sau đó `Greeter.message()` trả `PATCHED!`.

**Giới hạn parser:** Đây là parser tối giản để học, không có các kiểm tra biên đầy đủ như một công cụ đọc ClassFile không tin cậy. Nó chỉ thay ASCII trong một `CONSTANT_Utf8_info` có cùng byte length. Java ClassFile dùng **modified UTF-8** cho `CONSTANT_Utf8_info`, nên phương pháp mã hóa ASCII đơn giản **không áp dụng tổng quát** cho Unicode tùy ý, chuỗi dài khác kích thước, đổi tên class, đổi method descriptor, đổi stack map frames hoặc sửa control flow.

Nếu muốn thêm advice vào method, thay đổi instruction list hoặc instrument ứng dụng thật, nên dùng **ASM, Byte Buddy hoặc công cụ bytecode hiểu ClassFile**, cập nhật constant pool, offsets, StackMapTable, exception tables và các metadata liên quan một cách hợp lệ. Không tìm-thay bytes thô trên toàn JAR rồi hy vọng verifier chấp nhận.

### 4.5. File agent.mf

Nội dung file `agent.mf`:

~~~text
Premain-Class: Agent
~~~

Lưu file ở root project. Công cụ `jar --manifest agent.mf` sẽ tạo main manifest trong agent JAR; nó có thể bổ sung các thuộc tính manifest thông thường theo hành vi `jar`. Demo **không** cần `Agent-Class` vì không dùng dynamic attach và không cần `Can-Retransform-Classes: true` vì đăng ký transformer với `false`.

### 4.6. Build, đóng gói và chạy trên Java 17

Từ thư mục `instrumentation-demo`, trước hết tạo hai output directories.

Trên Bash/Linux/macOS:

~~~bash
mkdir -p agent-classes app-classes
javac --release 17 -d agent-classes src/Agent.java
javac --release 17 -d app-classes src/demo/Greeter.java src/demo/Main.java
jar --create --file transform-agent.jar --manifest agent.mf -C agent-classes .
java -cp app-classes demo.Main
java -javaagent:transform-agent.jar -cp app-classes demo.Main
~~~

Trên Windows PowerShell, phần build tương đương:

~~~powershell
New-Item -ItemType Directory -Force agent-classes, app-classes | Out-Null
javac --release 17 -d agent-classes src/Agent.java
javac --release 17 -d app-classes src/demo/Greeter.java src/demo/Main.java
jar --create --file transform-agent.jar --manifest agent.mf -C agent-classes .
java -cp app-classes demo.Main
java -javaagent:transform-agent.jar -cp app-classes demo.Main
~~~

**Kết quả đã biên dịch bằng `javac --release 17` và chạy thử trên OpenJDK:**

Khi chạy **không có agent**:

~~~text
Message: ORIGINAL
~~~

Khi chạy **có `-javaagent`**:

~~~text
Agent ready
Transformed: demo.Greeter
Message: PATCHED!
~~~

Những dòng này là **stdout của hai lệnh chạy Java khác nhau**, không phải một lần chạy in tất cả trong một process.

### 4.7. Vì sao kết quả chứng minh transform trước định nghĩa?

- Khi không có agent, JVM load classfile gốc `demo/Greeter.class` và method trả literal `ORIGINAL`.
- Khi có agent, JVM gọi `premain` rồi đăng ký transformer trước `demo.Main.main`.
- Lúc app lần đầu cần `demo.Greeter`, class bytes được transformer kiểm tra, bản clone đã đổi constant pool được trả về **trước khi `Greeter` được định nghĩa và dùng**.
- JVM định nghĩa và sau đó gọi static method của **class đã được instrument**, nên string trả về là `PATCHED!`.
- Không cần xóa hoặc sửa file `src/demo/Greeter.java`, cũng không sửa `app-classes/demo/Greeter.class` trên đĩa. Nếu chạy lại **không có agent**, kết quả là `ORIGINAL`.
- Nếu `Greeter` đã được định nghĩa trước khi agent đăng ký, đường load-time transform **không tự làm lại**; cần xem cơ chế retransform/redefine và khả năng JVM hỗ trợ.

**Không suy ra** loader đã bị thay bằng agent: `demo.Greeter` vẫn được application/system loader định nghĩa trong cách chạy này. Instrumentation thay **class bytes**, không tự tạo một loader mới.

## 5. Mối quan hệ giữa transform và defineClass

Có ba khái niệm thời gian cần tách:

### 5.1. Load-time transform (class chưa định nghĩa)

~~~text
ClassFile source
    |
    v
ClassLoader đọc bytes
    |
    v
defineClass(...)/JVM define request
    |
    v
ClassFileTransformer chain
    |
    v
JVM verify/link/apply ClassFile mới theo quy tắc
    |
    v
Class<?> xuất hiện với defining loader đã chọn
~~~

Điểm quan trọng là **transformer được gọi trước class definition**. Các bước nội bộ khác có thể diễn ra ở thứ tự/pha thích hợp của JVM, vì thế không nên diễn giải sơ đồ thành “tất cả linking/verification chỉ diễn ra đúng một lần và hoàn toàn sau transform” trong mọi JVM.

Một class loader tự xây có thể **chủ động tiền xử lý** ClassFile trước `defineClass`:

~~~java
@Override
protected Class<?> findClass(String name)
        throws ClassNotFoundException {

    byte[] original = readBytesFromApprovedSource(name);
    byte[] changed = preprocessBytes(original);

    return defineClass(name, changed, 0, changed.length);
}
~~~

Đây là **fragment triển khai**, hai hàm `readBytesFromApprovedSource` và `preprocessBytes` là helper **chưa định nghĩa**; không phải chương trình độc lập. Nó minh họa đường biến đổi bytes *do loader sở hữu*, khác với callback `ClassFileTransformer` do JVM điều phối.

Một hệ thống có thể có **cả hai**: loader tự preprocess rồi gọi `defineClass`; sau đó JVM gọi transformer đã đăng ký trước khi hoàn tất định nghĩa. Khi debug, phải xác định bytecode tại mỗi giai đoạn.

### 5.2. Retransformation (class đã load)

`Instrumentation.retransformClasses(Class<?>...)` yêu cầu xử lý lại các class **đã tồn tại**, nếu `Instrumentation.isRetransformClassesSupported()` và `isModifiableClass(type)` cho phép. Đây **không phải** nạp một type mới với defining loader mới.

Điểm quan trọng:

- Transformer đăng ký với `canRetransform=true` có thể được gọi lại khi retransform.
- Với transformer **không retransformation-capable** như demo, JVM có thể **tái sử dụng kết quả transform trước** thay vì gọi lại chính method transform trên retransformation.
- Những phép đổi schema như tùy ý thêm/xóa field, method, thay inheritance hoặc một số thuộc tính cấu trúc **bị ràng buộc** trong retransform/redefine; không thể hiểu retransform như đổi ClassFile tự do giống lần định nghĩa đầu.
- JVM vẫn áp dụng method verification, access/module constraints và khả năng hỗ trợ cụ thể.
- Thay bytes của method sau khi class đã load **không làm static fields reset và không tạo `Class<?>` mới**.
- Active stack frames có thể tiếp tục thực thi bytecode cũ theo quy tắc instrumentation; đừng giả định mọi method đang chạy lập tức đổi hành vi tại giữa một frame.

Muốn dùng retransform, agent manifest/capabilities phải phù hợp. Với `-javaagent` có nhu cầu này, `Can-Retransform-Classes: true` thường được khai báo; agent vẫn phải kiểm tra API runtime trước khi gọi `retransformClasses`.

### 5.3. Redefinition (class đã load, bytes do caller cung cấp)

`Instrumentation.redefineClasses(ClassDefinition...)` nhận một hoặc nhiều **ClassDefinition** chứa Class hiện tại và ClassFile bytes thay thế. Đây cũng là cơ chế cho class đã load và tuân theo những giới hạn JVM về các loại biến đổi cấu trúc.

| Cơ chế | Đầu vào và thời điểm | Type identity |
| --- | --- | --- |
| **Load-time transform** | Class mới sắp được định nghĩa, transformer xử lý `classfileBuffer` | Tạo định nghĩa Class theo defining loader hiện tại |
| **Retransform** | Class đã nạp, JVM chạy lại pipeline thích hợp | **Giữ Class<?> identity**; không tạo defining loader mới |
| **Redefine** | Class đã nạp, agent cung cấp `ClassDefinition` mới | **Giữ Class<?> identity**; không định nghĩa class hoàn toàn mới theo tên khác |
| **New loader / new class definition** | Một defining loader khác định nghĩa ClassFile khác hoặc cùng tên | **Type khác** nếu defining loader khác |

Không dùng retransform để “reset” một class đã initialize lỗi hoặc bắt nó chạy lại `<clinit>`. Nếu muốn thử initialization độc lập, cần class definition mới trong loader mới hoặc JVM process mới theo phạm vi state.

## 6. Thứ tự transformer và hợp đồng bytes

Khi JVM có nhiều transformer, kết quả được **xâu chuỗi**: transformer sau có thể nhìn bytes đã thay đổi bởi transformer trước.

Ở mức khái quát, ClassFileTransformer API phân nhóm theo khả năng retransform và theo native/Java transformer; trong mỗi nhóm áp dụng thứ tự đăng ký. Vì vậy:

- Đừng giả định ClassFile bytes luôn trùng với bản trên đĩa tại thời điểm callback.
- Transform cần **idempotent** nếu có thể được áp lại trong những kịch bản hỗ trợ; hoặc phải có detection marker rõ và tránh vá đè khiến ClassFile hỏng.
- Nếu transformer A thay constant pool và transformer B dựa vào offset/hình dạng trước đó, kết quả có thể thất bại. Dùng công cụ ClassFile-aware, thực hiện QA với nhiều agent/framework cùng lúc.
- Chỉ filter theo class name có thể gây xung đột khi nhiều loader định nghĩa cùng internal name. Filter theo `loader` và `module`/code source nếu cần.
- Mỗi transformer nên xử lý input **như immutable**, cấp byte array mới khi sửa.
- Nếu không thay đổi, **return `null`** là rõ ràng và tiết kiệm.

**Về exception:** ClassFileTransformer API có cách xử lý ngoại lệ từ `transform` theo hợp đồng (nhiều exception được JVM bỏ qua và coi như không transform, thay vì làm hỏng ngay ứng dụng). Vì vậy **không dùng `throw RuntimeException` từ transformer như một chính sách “chặn mọi class xấu” chắc chắn**. Nếu cần fail-closed vì lý do bảo mật, phải xây đường kiểm tra artifact/loader policy **có khả năng từ chối class definition một cách rõ ràng**, và kiểm thử behavior trên JVM đích.

Một instrumentation agent có thể chạy trên nhiều class rất sớm trong startup. Log không hạn chế từ `transform` có thể sinh ra lượng dữ liệu khổng lồ hoặc gây reentrancy khi logging framework tự nạp thêm class. Nên filter trước, hạn chế allocation/I/O trong callback và tránh gọi framework/plugin không kiểm soát khi JVM đang trong quá trình định nghĩa class.

## 7. Những vấn đề ClassFile thường làm bytecode instrument hỏng

Một patch trông đơn giản có thể làm ClassFile không hợp lệ. Ví dụ:

| Thay đổi | Rủi ro |
| --- | --- |
| Đổi `CONSTANT_Utf8` sang chuỗi dài hơn mà không cập nhật `u2 length` và offsets | Hỏng parser constant pool, `ClassFormatError` |
| Chèn instruction nhưng không cập nhật branch offsets và exception table | Nhảy sai bytecode, verification/linking error |
| Thêm code làm stack heights thay đổi nhưng không cập nhật stack map frames | `VerifyError` trên class version hiện đại |
| Đổi method descriptor nhưng không cập nhật call sites/constants | Lỗi resolution/`NoSuchMethodError` hoặc invalid bytecode |
| Đổi binary class name không phù hợp request `defineClass(name, ...)` | `NoClassDefFoundError`, thường kèm wrong name |
| Biến đổi class trong signed JAR hoặc sealed package mà không xét packaging policy | Rủi ro integrity, signer/package policy, code source |
| Cố dùng retransform để thay inheritance hoặc tùy ý thêm field | Giới hạn schema, `UnmodifiableClassException` hoặc lỗi khác tùy runtime |
| Biến đổi nhiều class tương tác nhưng dùng bản API do loader khác định nghĩa | `ClassCastException`/`LinkageError` do type identity khác |

Trong demo, ta tránh đa số nguy cơ nhờ chỉ thay **một entry UTF8 ASCII cùng độ dài**; JVM vẫn kiểm tra class sau transform. Ví dụ không làm JVM verifier “bỏ qua” điều kiện nào.

Để biến đổi bytecode thông thường, nên dùng framework hiểu cấu trúc ClassFile:

- **ASM:** đọc ClassFile, thay method instructions/visitors, hỗ trợ tính toán lại stack map frames/max stack khi cấu hình đúng.
- **Byte Buddy:** xây dựng instrumentation ở mức mô hình Java/agent, giảm lượng xử lý bytecode thủ công.
- **Javassist hoặc tool tương đương:** lựa chọn khác tùy yêu cầu và tương thích JDK.

Dù dùng thư viện, vẫn phải kiểm thử class loading trên phiên bản JVM mục tiêu, xử lý module access/exports/opens, loader dependencies và nguồn bytecode; thư viện bytecode không tự cung cấp security boundary.

## 8. Module (JPMS), ProtectionDomain và loader identity

Kể từ Java 9, `ClassFileTransformer` có overload nhận `Module`. Một class mới có thể được định nghĩa trong:

- **Named module** thuộc boot/dynamic `ModuleLayer`;
- **Unnamed module** của class loader tương ứng;
- Package/classloader đặc biệt theo cơ chế JVM.

Các ràng buộc JPMS vẫn tồn tại. Instrumentation có các cơ chế hỗ trợ module access phù hợp API, và `Instrumentation.redefineModule` cho phép agent điều chỉnh **một số quan hệ module** khi được JVM cho phép; nó không phải “tắt JPMS toàn process”.

### ClassLoader và ProtectionDomain có thay đổi không?

Load-time transformer **không tự đổi defining loader**. Kể cả khi bytes được sửa, `Class<?>.getClassLoader()` vẫn phản ánh loader định nghĩa class theo request. `ProtectionDomain`/`CodeSource` cần được xem riêng nếu agent dựa vào nguồn code hoặc chính sách security.

Trong demo:

~~~text
Agent class             → system/application loader
demo.Main               → system/application loader
demo.Greeter            → system/application loader
java.lang.instrument.*  → runtime loader phù hợp module Java
~~~

Agent có `premain` chạy trên system loader là behavior theo package specification cho startup agents; nhưng không nên dựa vào một chuỗi toString cụ thể của loader để viết assertion. Trong hệ plugin, transformer có thể tác động class được **custom loader** định nghĩa và Class identity vẫn đi cùng custom defining loader đó.

**Không nhầm `-javaagent` với `ClassLoader` tự xây:** agent đặt một hook biến đổi ở mức JVM instrument; custom loader quyết định nguồn bytes/namespace/delegation. Có thể phối hợp nhưng không thay thế nhau.

## 9. Instrumentation được dùng vào việc gì?

### 9.1. Tracing, metrics, coverage

Agent có thể thêm bytecode tại method entry/exit để đo thời gian, trace hoặc phủ code. Cần:

- Tránh tạo vòng recursion do tracer lại gọi method được instrument.
- Không bỏ qua `finally`, exceptions, synchronized methods hoặc asynchronous execution.
- Có policy exclude cho JDK/agent/helper classes cần thiết.
- Chú ý performance overhead, class loading time, allocations và startup cost.
- Có fallback/rollout/canary khi agent gặp class version hoặc bytecode shape chưa hỗ trợ.

### 9.2. Testing và fault injection

Agent có thể ghi instrumented probes, thay target branch cho test hoặc mô phỏng fault theo scenario. Nhưng nên **tách kịch bản bằng loader/JVM mới** khi cần reset static state, class initialization hoặc transformers đã có từ trước; việc remove transformer không làm các class đã biến đổi tự quay về bytecode gốc.

### 9.3. Plugin framework và dynamic classes

Một plugin loader có thể nạp một implementation do JAR ngoài cung cấp. Agent có thể log/tạo span cho class được custom loader định nghĩa, nhưng phải phân biệt:

- Plugin **được phép** đến từ nguồn nào (JAR checksum/signature/policy);
- Class **do loader nào định nghĩa**;
- Transformer **có hay không áp vào class ấy**;
- Những Class khác cùng tên trong parent/child có được biến đổi tương đương không.

Class loader và instrumentation **không tự sandbox** code không tin cậy. Bytecode transformer có quyền và tầm ảnh hưởng lớn đối với JVM: một agent lỗi có thể phá class loading trên diện rộng. Hãy xác minh nguồn agent JAR, config và phiên bản của nó như một thành phần có đặc quyền đáng kể.

## 10. Chẩn đoán khi agent không transform

### Trường hợp 1 — chạy không có output "Agent ready"

Kiểm tra:

- Thực sự truyền `-javaagent:transform-agent.jar` vào **lệnh `java` dùng chạy app**?
- Manifest trong agent JAR có `Premain-Class: Agent`?
- `Agent.class` và nested `Agent$PatchTransformer.class` có nằm trong JAR?
- `premain(String, Instrumentation)` có đúng `public static` signature?
- Agent có bị lỗi từ startup không? Đọc **stderr** và JVM exit code.

Có thể xem nội dung agent JAR:

~~~bash
jar --list --file transform-agent.jar
~~~

Tên nested class sẽ được tạo bởi `javac` và có trong `agent-classes` được đưa vào JAR theo lệnh `-C agent-classes .`.

### Trường hợp 2 — có "Agent ready", không có "Transformed"

Kiểm tra:

1. `demo.Greeter` có bị **load trước khi transformer đăng ký**? Với demo chuẩn thì không.
2. `className` có là `demo/Greeter` hay bạn đang so nhầm `demo.Greeter`?
3. Có một class cùng tên được **parent loader** cung cấp và class bạn định instrument không phải bản đang được dùng?
4. `PatchTransformer` có tìm thấy chính xác một `CONSTANT_Utf8_info` mang tám byte `ORIGINAL` không?
5. Compiler có thay đổi literal hoặc artifact trên classpath không giống file source mà bạn tưởng?
6. Có agent khác đã biến đổi constant pool trước đó không?
7. Agent có register với `false` và bạn đang mong nó chạy lại trên class đã load khi `retransformClasses` được gọi không?

### Trường hợp 3 — class load thất bại sau transform

Đọc exception thật:

- `ClassFormatError`: cấu trúc ClassFile có thể không hợp lệ, sai constant pool length/tag/offset.
- `VerifyError`: bytecode không thỏa type/stack/local/stack-map constraints.
- `NoClassDefFoundError`: class dependencies thiếu, tên khai báo sai hoặc class initialization từng lỗi.
- `UnsupportedClassVersionError`: phiên bản ClassFile không được JVM hỗ trợ.
- `LinkageError`: định nghĩa trùng hoặc loader constraints; không tự xem đây là lỗi transformer.
- `IllegalAccessError`/module-related access errors: JPMS/member visibility hoặc những dependency thêm bởi transformer không được phép.

Để phân biệt lỗi từ bytes gốc với lỗi transformer: chạy **không có agent**, rồi so kết quả với **có agent**. Có thể lưu instrumented bytes trong một môi trường test có kiểm soát để dùng `javap -v -p`/verifier kiểm tra, nhưng không xuất bytes đến nơi có thể lộ code/secrets và không ghi file tùy tiện trong callback startup.

### Trường hợp 4 — println output đúng nhưng không chắc loader nào định nghĩa

Trong app, fragment:

~~~java
System.out.println(demo.Greeter.class.getName());
System.out.println(demo.Greeter.class.getClassLoader());
System.out.println(demo.Greeter.class.getModule());
~~~

Đây là **fragment**; loader/module details phụ thuộc môi trường. Dòng thay đổi `Message: PATCHED!` chứng minh behavior được instrument trong demo, nhưng muốn chứng minh **loader-specific transformation** cần log/đối chiếu `loader` của callback và `Class.getClassLoader()`.

### Bắt đầu bằng cặp đối chứng

~~~text
No agent:   bytecode gốc       → Message: ORIGINAL
With agent: bytecode biến đổi → Message: PATCHED!
~~~

Đây là test đối chứng sạch vì mỗi `java` invocation tạo **JVM process mới**, tránh chuyện class đã nạp trong process trước đó. Cũng có thể dùng hai custom loaders mới cho cùng một JVM, nhưng phải xác nhận parent không trả Class đã load và transformer không phụ thuộc state toàn cục của agent.

## 11. Ranh giới, hiệu năng và bảo mật

**Agent là thành phần có đặc quyền và tầm ảnh hưởng lớn.** Nó có thể nhìn/biến đổi code của nhiều class nếu không filter; do đó:

1. **Tin cậy artifact agent:** kiểm tra provenance, pin checksum/signature và quản lý config giống những thành phần release khác.
2. **Filter tối thiểu:** chỉ instrument packages/classes thực sự cần, có allowlist loader/module khi ứng dụng có plugin.
3. **Tránh deadlock hoặc reentrancy:** callback class loading có thể chạy trong ngữ cảnh liên quan khóa của loader; không gọi ngược custom loader hoặc logger/framework không kiểm soát trong lúc đang transform.
4. **Giảm overhead:** parse ClassFile, reflection, allocation và I/O từ mọi callback rất tốn chi phí; cần đo startup/runtime.
5. **Không sửa mảng gốc:** trả null hoặc bản byte[] mới. Xử lý ClassFile hợp lệ và có test verifier.
6. **Chú ý thứ tự transformer:** nhiều agent cùng chỉnh một method/class có thể tương tác bất ngờ.
7. **Không dùng transformer exception để chặn code bất tín:** API không bảo đảm exception từ callback tương đương deny policy. Với nguồn plugin ngoài, gate artifact và parent/child loader policy phải được thực thi riêng.
8. **Kiểm tra target JDK/module:** class file version, named modules và internal packages có thể thay giữa bản JDK.
9. **Đừng xem biến đổi bytes là isolation:** code sau instrumentation vẫn chạy trong JVM với quyền process, tùy giới hạn OS/module/runtime.
10. **Vòng đời:** `removeTransformer` dừng callback cho những class tiếp theo phù hợp registration; nó không tự hoàn tác những Class đã được định nghĩa từ bytes đã biến đổi.

## 12. Tự kiểm tra kiến thức

**Câu 1.** `ClassFileTransformer` load-time được gọi trước hay sau khi JVM hoàn tất định nghĩa Class mới?

**Đáp án:** **Trước** khi class mới được định nghĩa, trong pipeline xử lý ClassFile bytes của JVM.

**Câu 2.** Nếu `transform` trả `null`, JVM dùng gì?

**Đáp án:** Tiếp tục với bytes hiện tại của transformer chain, tức transformer ấy **không cung cấp thay đổi**.

**Câu 3.** Vì sao ví dụ so `"demo/Greeter"` thay vì `"demo.Greeter"`?

**Đáp án:** Tham số `className` của `ClassFileTransformer` dùng **internal name** với slash; `demo.Greeter` là binary name thông thường của Java.

**Câu 4.** Trong ví dụ, source `Greeter.java` vẫn chứa `ORIGINAL` nhưng app in `PATCHED!`. Điều đó chứng tỏ gì?

**Đáp án:** Agent đã thay constant pool bytes trước khi class được định nghĩa; file source/ClassFile lưu trên disk không bị sửa bởi transformer trong demo.

**Câu 5.** Một class đã được system loader định nghĩa, sau đó mới đăng ký transformer. Tạo transformer có tự đổi class đã load không?

**Đáp án:** Không. Muốn thay class đã load cần xem `retransformClasses`/`redefineClasses` nếu runtime hỗ trợ và class modifiable.

**Câu 6.** Trong demo, vì sao truyền `false` vào `addTransformer`?

**Đáp án:** Đăng ký một transformer **không retransformation-capable**; bài chỉ minh họa load-time transformation của một class mới.

**Câu 7.** Có thể return một `byte[]` tùy ý, sai cấu trúc rồi JVM vẫn tạo Class không?

**Đáp án:** Không. JVM vẫn cần ClassFile hợp lệ và tuân thủ verifier/linker/module constraints, có thể ném `ClassFormatError`, `VerifyError` hoặc lỗi khác.

**Câu 8.** Java agent có thay thế security checks cho plugin JAR không?

**Đáp án:** Không. Instrumentation không tự xác minh nguồn/trust của code, không phải sandbox và có thể làm thay đổi hành vi JVM trên diện rộng.

**Câu 9.** Hai classloader định nghĩa cùng binary name sau transform với bytes giống nhau có trở thành một Class không?

**Đáp án:** Không. Defining loader khác nhau thì identity khác nhau.

**Câu 10.** Nếu muốn chèn nhiều instruction và đổi stack/control flow, có nên dùng parser constant pool tối giản này không?

**Đáp án:** Không. Dùng thư viện bytecode hiểu đầy đủ ClassFile như ASM/Byte Buddy, cập nhật stack map frames/offsets và kiểm thử bằng JVM verifier.

## Ghi nhớ

`-javaagent` cho phép một agent đăng ký `ClassFileTransformer` qua `Instrumentation`. Khi một class mới được định nghĩa, JVM có thể gọi transformer **trước bước định nghĩa**: trả `null` để giữ bytes hiện tại hoặc trả **byte[] ClassFile mới** để JVM tiếp tục kiểm tra/định nghĩa. Đây là **load-time instrumentation**, khác với `retransformClasses` và `redefineClasses` cho class đã load; nó cũng khác một custom loader tự chỉnh bytes trước `defineClass`. Instrumentation không thay defining loader, không reset static state và không bỏ qua verifier/module rules.

**Tài liệu chuẩn Java SE 17:**

- `java.lang.instrument` package, agent deployment/manifest/premain: https://docs.oracle.com/en/java/javase/17/docs/api/java.instrument/java/lang/instrument/package-summary.html
- `Instrumentation`, `addTransformer`, `retransformClasses`, `redefineClasses`: https://docs.oracle.com/en/java/javase/17/docs/api/java.instrument/java/lang/instrument/Instrumentation.html
- `ClassFileTransformer`, signatures và thứ tự chaining: https://docs.oracle.com/en/java/javase/17/docs/api/java.instrument/java/lang/instrument/ClassFileTransformer.html
- `ClassLoader`, `defineClass`/parent delegation: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- JVMS Java 17, ClassFile format/constant pool (§4): https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html
- JVMS Java 17, loading/linking/initializing (§5): https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-class-definition-008">Redefine, retransform và khác biệt với nạp lớp bằng loader mới</a>

<details>
<summary>Xem chi tiết</summary>

# Redefine, retransform và khác biệt với nạp lớp bằng loader mới

## 1. Ba cách thay đổi mã Java lúc JVM đang chạy

Khi ứng dụng cần sửa hành vi method, gắn profiler, cập nhật plugin hoặc chạy hai phiên bản song song, có ba kỹ thuật dễ bị gọi chung là hot reload nhưng có ý nghĩa hoàn toàn khác nhau:

1. **Redefine** dùng **Instrumentation.redefineClasses(ClassDefinition...)** để cung cấp class-file bytes thay thế cho **Class đã tồn tại**.
2. **Retransform** dùng **Instrumentation.retransformClasses(Class<?>...)** để JVM chạy lại chuỗi **ClassFileTransformer** phù hợp trên **Class đã tồn tại**, không truyền replacement bytes trực tiếp vào lệnh này.
3. **Nạp bằng ClassLoader mới** dùng loadClass/findClass/defineClass theo cách thông thường để tạo **runtime Class khác**, ngay cả khi binary name vẫn giống bản cũ.

**Nếu chỉ sửa method body và muốn các instance cũ dùng code mới trên lần gọi tiếp theo, xem redefine/retransform. Nếu phải thay class schema, chạy hai phiên bản đồng thời hoặc thay module/plugin toàn diện, hãy xét ClassLoader mới.**

Các thuật ngữ cần nhớ:

- **Loading**: JVM tìm và định nghĩa class.
- **Linking**: verification/preparation/resolution theo đặc tả.
- **Initialization**: thực hiện static initializers khi active use theo quy tắc Java.
- **Class identity**: với class thông thường, dựa trên **binary name cùng defining ClassLoader**.
- **Instrumentation**: API agent làm việc trên bytecode/classes mà JVM đã hoặc sắp nạp.

Không nhầm **ClassLoader.defineClass** với **Instrumentation.redefineClasses** chỉ vì tên giống nhau: một bên tạo định nghĩa runtime từ loader, bên kia cập nhật định nghĩa hiện có theo những giới hạn đặc biệt.

## 2. Bảng so sánh

| Tiêu chí | redefineClasses | retransformClasses | Loader mới |
| --- | --- | --- | --- |
| Đầu vào | ClassDefinition(Class c, byte[] bytes) | Class<?> targets | Class name và nguồn bytes |
| Có cần Java agent? | Có, Instrumentation | Có, Instrumentation | Không |
| Có truyền class-file bytes thay thế trực tiếp? | Có | Không, transformer pipeline xử lý | Có thể, qua defineClass |
| Class object mục tiêu có thay thành object khác không? | Không | Không | Có Class mới do loader mới định nghĩa |
| Existing instances có dùng method body mới không? | Có, trên invocation mới | Có, trên invocation mới | Không, instances cũ vẫn thuộc Class cũ |
| Static field values có reset không? | Không | Không | Class mới có static state riêng |
| Chạy lại static initializer của class cũ? | Không | Không | Class mới khởi tạo riêng khi được active use |
| Thêm field hoặc method mới? | Không theo giới hạn JVMTI Java 17 | Không theo giới hạn JVMTI Java 17 | Có thể trong class mới |
| Có thể chạy hai phiên bản cùng binary name? | Không thành hai runtime types | Không thành hai runtime types | Có, nếu loaders khác |
| Ví dụ sử dụng | Hot-fix method body | Coverage/APM instrumentation | Plugin reload, isolation, API evolution |
| Rủi ro | Capability, schema, active frames | Transformer ordering, replay | Type identity, delegation, leaks |

**Kết luận quan trọng:** Redefine và retransform không phải cách thay Class object. Loader mới không sửa Class object cũ. Ba hướng giải quyết những vấn đề khác nhau.

## 3. Class đã load có thể được định nghĩa lại bằng defineClass không?

**Không.** Khi một loader đã define class với binary name ví dụ **lesson.hot.Feature**, loader ấy không thể gọi defineClass một lần nữa với cùng name như cách ghi đè bản cũ. JVM có thể ném **LinkageError** do duplicate definition. loadClass có thể tiếp tục trả cùng Class đã được JVM ghi nhận.

Để thay hành vi của đúng Class đang chạy, dùng cơ chế instrumentation nếu hỗ trợ; để có một class cùng tên nhưng mới hoàn toàn, tạo loader khác:

~~~text
Ban đầu:
  Feature Class object
       |
       +-- binary name = lesson.hot.Feature
       +-- defining loader = L1
       +-- method body V1

Redefine / retransform:
  Feature Class object (vẫn là object đó)
       |
       +-- defining loader = L1 (không đổi)
       +-- method body V2 được cài hợp lệ

Loader khác:
  Feature class A = (lesson.hot.Feature, L1)
  Feature class B = (lesson.hot.Feature, L2)

  A != B dù tên hoàn toàn giống nhau
~~~

Các instances thuộc A không tự biến thành instances của B. Cần quản lý traffic/migration nếu chuyển plugin sang B.

## 4. Instrumentation, agent và capability

### 4.1 Java agent cung cấp Instrumentation

Một ứng dụng thường không thể tự gọi constructor để tạo **Instrumentation**. JVM truyền object này vào entry point của Java agent:

~~~java
import java.lang.instrument.Instrumentation;

public class Agent {
    public static void premain(
            String args, Instrumentation instrumentation) {
        // Được JVM gọi khi ứng dụng khởi động với -javaagent
    }

    public static void agentmain(
            String args, Instrumentation instrumentation) {
        // Có thể được gọi khi agent được attach về sau,
        // nếu JVM và chính sách runtime cho phép
    }
}
~~~

Đây là chữ ký minh họa, chưa phải chương trình chạy độc lập.

Cách đơn giản và có thể tái hiện trong bài học là chạy **-javaagent:agent.jar**. JVM đọc **Premain-Class** trong JAR manifest, gọi premain trước application main rồi application mới thực hiện công việc.

Dynamic attach/agentmain là khả năng có điều kiện, có thể bị chính sách JDK hoặc môi trường triển khai hạn chế. Không giả định luôn được attach vào production JVM.

### 4.2 Manifest capabilities

~~~text
Premain-Class: Agent
Can-Redefine-Classes: true
Can-Retransform-Classes: true
~~~

Đây là nội dung file **agent.mf**, không phải Java source. Capability phải được yêu cầu phù hợp bằng manifest và JVM thực sự hỗ trợ.

Kiểm tra trong code agent:

~~~java
boolean redefine = inst.isRedefineClassesSupported();
boolean retransform = inst.isRetransformClassesSupported();
boolean targetModifiable = inst.isModifiableClass(Target.class);
~~~

Đây là snippet đặt trong method có biến inst và Target phù hợp.

- **isRedefineClassesSupported** và **isRetransformClassesSupported** là hai khả năng độc lập.
- **isModifiableClass** kiểm tra class cụ thể có sửa được không; kết quả true không thay thế việc kiểm tra capability tương ứng.
- Primitive classes và array classes không thể redefine/retransform.
- Một số VM-defined classes cũng có thể không modifiable.
- Manifest xin capability không ép JVM phải cấp khả năng mà VM không hỗ trợ.

## 5. Dữ liệu class-file hợp lệ và giới hạn thay đổi

Với Java 17/JVMTI chuẩn, redefine/retransform chủ yếu cho phép **sửa method bodies, constant pool và những attributes được phép** của class đã tồn tại. Các thay đổi sau nhìn chung bị cấm:

| Thay đổi | Redefine/retransform Java 17 |
| --- | --- |
| Sửa instructions trong method đang tồn tại | Có thể, nếu bytes hợp lệ |
| Thay constant pool/attributes được phép | Có thể |
| Thêm hoặc bỏ field | Không |
| Thêm hoặc bỏ method | Không |
| Đổi tên hay descriptor của method | Không |
| Đổi field schema | Không |
| Đổi modifiers bị đặc tả hạn chế | Không |
| Đổi direct superclass hoặc implemented interfaces | Không |
| Thay NestHost/NestMembers | Không |
| Thay Record/PermittedSubclasses attributes | Không |
| Bytes khai báo binary name khác target Class | Không |
| Class-file version mới hơn VM hỗ trợ | Không |

Không nên tin rằng một class mới đã **compile thành công** thì chắc chắn có thể redefine class cũ. Hai bản có thể khác schema, annotations/attributes thuộc loại bị cấm, method modifiers hoặc hierarchy.

**Loader mới khác ở đây:** Một loader L2 có thể define type mới có thêm/bớt fields/methods hoặc API khác, miễn là nó là class hợp lệ theo JVM và đáp ứng các ràng buộc loading/module/package tương ứng. Nhưng bạn phải chấp nhận **Class identity mới**.

Các VM/tools đặc biệt có thể cung cấp enhanced hot-swap ngoài chuẩn; không xem đó là cam kết chung của Java 17.

## 6. Môi trường ví dụ Java 17+

Ba ví dụ hoàn chỉnh bên dưới dùng một source tree thống nhất. Hai bản Feature có cùng binary name nhưng khác behavior. TraceTarget dùng để thử transformer. Các demo đã được biên dịch/chạy trên OpenJDK 21 và chỉ sử dụng API Java 17+.

~~~text
project/
├── agent.mf
├── versions/
│   ├── v1/lesson/hot/Feature.java
│   └── v2/lesson/hot/Feature.java
├── retrans-src/lesson/hot/TraceTarget.java
└── host-src/
    ├── Agent.java
    ├── RedefineDemo.java
    ├── RetransformDemo.java
    └── NewLoaderDemo.java
~~~

Output folders khi compile: **v1**, **v2**, **retrans**, **host**. Chạy mọi lệnh từ thư mục **project**. Nếu bạn dùng Windows PowerShell, dấu ngăn nhiều classpath entries là **;** thay vì dấu **:** trên Linux/macOS.

### 6.1 Feature phiên bản 1

**versions/v1/lesson/hot/Feature.java**:

~~~java
package lesson.hot;

public class Feature {
    public static int state = 7;

    static {
        System.out.println("Feature V1 initialized");
    }

    public String label() {
        return "v1";
    }
}
~~~

### 6.2 Feature phiên bản 2

**versions/v2/lesson/hot/Feature.java**:

~~~java
package lesson.hot;

public class Feature {
    public static int state = 999;

    static {
        System.out.println("Feature V2 initialized");
    }

    public String label() {
        return "v2";
    }
}
~~~

Hai bản **giữ đúng cùng schema**: cùng public field state và method label. Chỉ thay method bodies và code của static initializer.

Biên dịch vào hai thư mục riêng:

~~~bash
javac -d v1 versions/v1/lesson/hot/Feature.java
javac -d v2 versions/v2/lesson/hot/Feature.java
~~~

Không đặt hai Feature.java này trong cùng compilation unit hoặc cùng output folder, vì một bản sẽ bị ghi đè hoặc compiler báo trùng class.

### 6.3 Agent dùng chung

**host-src/Agent.java**:

~~~java
import java.lang.instrument.Instrumentation;

public class Agent {
    static volatile Instrumentation instrumentation;

    public static void premain(
            String args, Instrumentation inst) {
        instrumentation = inst;
    }
}
~~~

**agent.mf** (file text manifest, cần có newline cuối file):

~~~text
Premain-Class: Agent
Can-Redefine-Classes: true
Can-Retransform-Classes: true
~~~

Sau khi compile Agent.class vào host, đóng gói:

~~~bash
jar --create --file agent.jar --manifest agent.mf -C host Agent.class
~~~

Trong hai demo instrumentation, **-javaagent:agent.jar** bảo đảm premain đã thiết lập Instrumentation khi main chạy.

## 7. Ví dụ runnable 1 — redefineClasses

**host-src/RedefineDemo.java**:

~~~java
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import lesson.hot.Feature;

public class RedefineDemo {
    public static void main(String[] args) throws Exception {
        Instrumentation inst = Agent.instrumentation;

        System.out.println("redefine supported = "
                + inst.isRedefineClassesSupported());

        Feature object = new Feature();
        Class<?> type = object.getClass();

        System.out.println("before = " + object.label());
        System.out.println("state before = " + Feature.state);

        byte[] replacement = Files.readAllBytes(
                Path.of(args[0]));

        inst.redefineClasses(
                new ClassDefinition(Feature.class, replacement));

        System.out.println("same Class = "
                + (type == object.getClass()));
        System.out.println("same object = " + object.label());
        System.out.println("new object = "
                + new Feature().label());
        System.out.println("state after = " + Feature.state);
    }
}
~~~

Biên dịch sau khi đã có v1/v2 và Agent.java:

~~~bash
javac -cp v1 -d host host-src/Agent.java host-src/RedefineDemo.java
jar --create --file agent.jar --manifest agent.mf -C host Agent.class
java -javaagent:agent.jar -cp host:v1 RedefineDemo v2/lesson/hot/Feature.class
~~~

Windows PowerShell:

~~~powershell
javac -cp v1 -d host host-src/Agent.java host-src/RedefineDemo.java
jar --create --file agent.jar --manifest agent.mf -C host Agent.class
java -javaagent:agent.jar -cp "host;v1" RedefineDemo v2/lesson/hot/Feature.class
~~~

**Stdout thực tế trên OpenJDK 21:**

~~~text
redefine supported = true
Feature V1 initialized
before = v1
state before = 7
same Class = true
same object = v2
new object = v2
state after = 7
~~~

### 7.1 Phân tích chi tiết

1. **Feature** lần đầu khởi tạo từ v1, in **Feature V1 initialized**.
2. JVM giữ static field **state = 7**.
3. **ClassDefinition(Feature.class, replacement)** cung cấp bytes của v2 cho đúng Class mục tiêu.
4. **Instrumentation.redefineClasses** cập nhật method code hợp lệ. Không có Class object mới.
5. **same Class = true** vì existing object vẫn có runtime type cũ về identity.
6. **same object = v2** vì một lần gọi method mới trên existing instance sử dụng method body v2.
7. Instance mới cũng gọi method body v2.
8. **state after = 7**, không phải 999. **Feature V2 initialized** cũng không xuất hiện.

Redefinition **không chạy lại static initializer** và không tự reset static fields hay recreate instances.

### 7.2 Khi demo không thể chạy nguyên dạng

Capability redefine là tùy JVM, nên output “supported=true” áp dụng cho OpenJDK test có manifest phù hợp, không phải mọi VM. Nếu không hỗ trợ, gọi redefineClasses có thể ném UnsupportedOperationException. Nếu replacement bytes đổi field/method schema hoặc có binary name khác, JVM từ chối.

**Nếu chỉ muốn thử trên JVM có capability**, demo này là đủ. Production code nên kiểm tra thêm **isModifiableClass(Feature.class)** trước khi can thiệp.

## 8. Ví dụ runnable 2 — retransformClasses

### 8.1 Khác biệt về đầu vào

**retransformClasses(TraceTarget.class)** chỉ nhận Class target. JVM sử dụng **ClassFileTransformer pipeline** phù hợp để tính lại bytes cho định nghĩa hiện hữu, thay vì caller gửi một class-file array cho ClassDefinition.

**retrans-src/lesson/hot/TraceTarget.java**:

~~~java
package lesson.hot;

public class TraceTarget {
    public String label() {
        return "OLD-CODE";
    }
}
~~~

Để không cần phụ thuộc thư viện ngoài, ví dụ chỉ thay một UTF-8 constant có cùng độ dài: **OLD-CODE → NEW-CODE**. Đây là một thủ thuật minh họa rất hẹp, không phải transformer production.

### 8.2 Source RetransformDemo.java

**host-src/RetransformDemo.java**:

~~~java
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;
import java.util.Arrays;
import lesson.hot.TraceTarget;

public class RetransformDemo {
    static byte[] replaceMarker(byte[] original) {
        byte[] search =
                "OLD-CODE".getBytes(StandardCharsets.US_ASCII);
        byte[] replace =
                "NEW-CODE".getBytes(StandardCharsets.US_ASCII);

        byte[] result = Arrays.copyOf(
                original, original.length);

        int matches = 0;

        for (int i = 0;
             i <= result.length - search.length; i++) {
            boolean match = true;

            for (int j = 0; j < search.length; j++) {
                if (result[i + j] != search[j]) {
                    match = false;
                    break;
                }
            }

            if (match) {
                matches++;
                System.arraycopy(
                        replace, 0, result, i, replace.length);
            }
        }

        if (matches != 1) {
            throw new IllegalStateException(
                    "Expected one marker, found " + matches);
        }

        return result;
    }

    public static void main(String[] args) throws Exception {
        Instrumentation inst = Agent.instrumentation;

        System.out.println("retransform supported = "
                + inst.isRetransformClassesSupported());

        TraceTarget existing = new TraceTarget();
        System.out.println("before = " + existing.label());

        ClassFileTransformer transformer =
                new ClassFileTransformer() {
            @Override
            public byte[] transform(
                    ClassLoader loader,
                    String name,
                    Class<?> redef,
                    ProtectionDomain domain,
                    byte[] bytes) {

                if (!"lesson/hot/TraceTarget".equals(name)
                        || redef == null) {
                    return null;
                }

                return replaceMarker(bytes);
            }
        };

        inst.addTransformer(transformer, true);

        try {
            inst.retransformClasses(TraceTarget.class);
        } finally {
            inst.removeTransformer(transformer);
        }

        System.out.println("after = " + existing.label());
        System.out.println("same class = "
                + (existing.getClass() == TraceTarget.class));
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac -d retrans retrans-src/lesson/hot/TraceTarget.java
javac -cp retrans -d host host-src/Agent.java host-src/RetransformDemo.java
jar --create --file agent.jar --manifest agent.mf -C host Agent.class
java -javaagent:agent.jar -cp host:retrans RetransformDemo
~~~

Windows PowerShell:

~~~powershell
javac -d retrans retrans-src/lesson/hot/TraceTarget.java
javac -cp retrans -d host host-src/Agent.java host-src/RetransformDemo.java
jar --create --file agent.jar --manifest agent.mf -C host Agent.class
java -javaagent:agent.jar -cp "host;retrans" RetransformDemo
~~~

**Stdout thực tế trên OpenJDK 21:**

~~~text
retransform supported = true
before = OLD-CODE
after = NEW-CODE
same class = true
~~~

### 8.3 Điểm cần hiểu để không triển khai sai

- **addTransformer(transformer, true)** yêu cầu transformer đủ khả năng retransform.
- **ClassFileTransformer** trong Java 17 có hai default transform overloads, **không phải functional interface**. Vì thế ví dụ dùng anonymous class, không dùng lambda.
- Transformer nhận **internal class name** kiểu **lesson/hot/TraceTarget**, không phải tên Java lesson.hot.TraceTarget.
- **redef != null** ở lần retransform; trong lượt load class ban đầu nó thường null.
- Trả **null** khi không sửa bytes.
- Không được tự thay đổi **classfileBuffer** input; ví dụ dùng **Arrays.copyOf**.
- Marker old/new đều 8 ASCII bytes nên chiều dài một constant pool UTF-8 entry không đổi. Code kiểm tra đúng một marker trong file mục tiêu.
- **removeTransformer** chỉ gỡ registration cho những lần biến đổi sau; nó **không tự hoàn nguyên bytecode đã được cài**.
- Existing instance vẫn là cùng object và same class identity, nhưng invocation mới chạy method code mới.

**Giới hạn:** sửa raw bytes bằng tìm chuỗi có thể phá class file khác. Chỉ áp dụng cho fixture đã biên dịch được kiểm soát. Dự án thật cần ASM/Byte Buddy hoặc API class-file phù hợp để thay đổi có cấu trúc, giữ stack map frames và constant pool hợp lệ.

## 9. Ví dụ runnable 3 — hai ClassLoaders mới

### 9.1 Không cần Java agent

Trong ví dụ này ta sẽ **không redefine hoặc retransform**. Ta định nghĩa **hai Class khác nhau** từ đúng hai file Feature.class v1/v2 bằng hai loader riêng.

**host-src/NewLoaderDemo.java**:

~~~java
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

public class NewLoaderDemo {
    static final class VersionLoader extends ClassLoader {
        private final Path classFile;

        VersionLoader(Path classFile) {
            super(ClassLoader.getPlatformClassLoader());
            this.classFile = classFile;
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            if (!name.equals("lesson.hot.Feature")) {
                throw new ClassNotFoundException(name);
            }

            try {
                byte[] bytes = Files.readAllBytes(classFile);
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException ex) {
                throw new ClassNotFoundException(name, ex);
            }
        }
    }

    static String label(Class<?> type) throws Exception {
        Object object = type.getConstructor().newInstance();
        return (String) type.getMethod("label").invoke(object);
    }

    public static void main(String[] args) throws Exception {
        VersionLoader first = new VersionLoader(
                Path.of("v1/lesson/hot/Feature.class"));

        VersionLoader second = new VersionLoader(
                Path.of("v2/lesson/hot/Feature.class"));

        Class<?> a = first.loadClass("lesson.hot.Feature");
        Class<?> b = second.loadClass("lesson.hot.Feature");

        System.out.println("same name = "
                + a.getName().equals(b.getName()));
        System.out.println("same Class = " + (a == b));
        System.out.println("first loader = "
                + (a.getClassLoader() == first));
        System.out.println("second loader = "
                + (b.getClassLoader() == second));
        System.out.println("first value = " + label(a));
        System.out.println("second value = " + label(b));
    }
}
~~~

Biên dịch/chạy sau khi có v1, v2:

~~~bash
javac -d host host-src/NewLoaderDemo.java
java -cp host NewLoaderDemo
~~~

**Stdout thực tế trên OpenJDK 21:**

~~~text
same name = true
same Class = false
first loader = true
second loader = true
Feature V1 initialized
first value = v1
Feature V2 initialized
second value = v2
~~~

### 9.2 Giải thích

- Hai Class đều có name **lesson.hot.Feature**.
- Defining loaders là hai VersionLoader objects khác nhau, nên **a != b**.
- Mỗi Class có static state riêng, được initialized lần đầu theo lifecycle của nó.
- Existing instances thuộc bản V1 vẫn dùng V1; những objects tạo từ B dùng V2.
- Không có lệnh Instrumentation; đây là class loading bình thường theo hai loaders.

**Vì sao đặt platform loader làm parent?** Ví dụ không đưa v1/v2 lên application classpath. Platform parent không nhìn thấy lớp plugin do test cung cấp, nên findClass riêng được gọi. Nếu dùng parent có sẵn cùng class name, cơ chế **parent-first** có thể trả Class của parent và không tạo bản riêng mong đợi.

Ví dụ tối giản chỉ hỗ trợ một binary name; loader production cần kiểm soát paths, packages, protection domains, modules, resources và trust boundary.

## 10. Instance cũ, static state và active frames

### 10.1 Khi redefine/retransform đã thành công

Theo Instrumentation/JVMTI:

- Một **Class object đang tồn tại** vẫn đại diện class ấy.
- **Existing objects** không được tạo mới. Instance fields giữ giá trị đang có.
- **Static values** giữ trạng thái, dù file replacement có code khởi tạo khác.
- **Static initializer không tự chạy lại** vì redefine/retransform.
- **New method invocation** dùng method body mới.
- **Active frames** đang thực thi method body cũ có thể tiếp tục chạy bytecode cũ tới khi frame hoàn tất.

~~~text
Thread A đã bước vào method V1
          |
JVM cập nhật Class thành V2
          |
          +-- frame A vẫn dùng code V1
          |
Thread B bắt đầu một lần gọi method mới
          |
          +-- dùng code V2
~~~

Do đó, sau khi agent báo cập nhật thành công, không thể kết luận mọi request trong mọi thread ngay lập tức chuyển sang logic mới. Nếu thay behavior liên quan state, giao dịch hoặc security, hãy cân nhắc quiescence, in-flight requests và rollback.

### 10.2 Khi dùng loader mới

Class V2 khác Class V1, nên V2 có static initialization riêng khi được active use. Objects V1 không bị JVM migrate tự động sang V2. Nếu muốn chuyển dữ liệu, ứng dụng cần cơ chế chuyển đổi/serialization/DTO hoặc recreation explicit, đồng thời chuẩn bị giải phóng references của phiên bản cũ.

Không gọi **new loader** là “đổi method body của Class hiện tại”. Đây là **chuyển hệ thống sang type identity mới**.

## 11. Retransformation pipeline chính xác

Retransform không đơn giản là “gọi lại mọi transformer”:

~~~text
Initial class file bytes
          |
          v
Transformer A: canRetransform = false
          |
          +-- reapply previous transform result
          |   KHÔNG gọi lại method transform của A
          |
          v
Transformer B: canRetransform = true
          |
          +-- gọi lại B.transform(...)
          |
          v
Transformer C: canRetransform = true
          |
          +-- gọi lại C.transform(...)
          |
          v
Verify và install bytes hợp lệ cho cùng Class
~~~

**Initial class file bytes** theo API có thể không byte-for-byte trùng với những bytes ban đầu được đưa vào defineClass, vì JVM có thể khác nhau ở constant-pool layout hoặc một số attributes. Đừng viết transformer dựa vào offsets cố định trong class file thực tế.

**Transformer ordering** được ClassFileTransformer quy định. Trong môi trường nhiều agents, những transformations tương tác nhau: cần kiểm tra idempotence, class schema, hiệu năng và thứ tự. RedefineClasses cũng có thể kích hoạt transformers đã đăng ký trong quá trình xử lý replacement bytes, nên agent không nên giả định redefine bỏ qua hoàn toàn chain.

**Transformer được add sau khi Class load không tự sửa Class đã nạp.** Ví dụ 2 phải gọi retransformClasses cụ thể để transformer tác động retroactively.

## 12. Điều gì xảy ra nếu thao tác thất bại?

| Tình huống | Lỗi hoặc biểu hiện | Hướng xử lý |
| --- | --- | --- |
| Capability không hỗ trợ | UnsupportedOperationException | Kiểm tra manifest và is*Supported |
| Class không modifiable | UnmodifiableClassException | Kiểm tra isModifiableClass |
| Thêm field/method hoặc đổi hierarchy | UnsupportedOperationException/VM error liên quan | So sánh schema, chọn loader mới nếu cần |
| Bytes sai định dạng | ClassFormatError | Kiểm tra generator, input bytes |
| Binary name trong bytes không khớp target | NoClassDefFoundError | Kiểm tra tên nội bộ của class file |
| Bytecode verifier từ chối | VerifyError/LinkageError | Xem bytecode và dependencies |
| Classfile version quá mới | UnsupportedClassVersionError | Dùng --release và JDK phù hợp |
| Transformer không được đăng ký capable | Không được gọi lại khi retransform | addTransformer(..., true), capabilities |
| JAR đổi trên đĩa nhưng Class vẫn bản cũ | Không tự reload | Dùng instrumentation hoặc loader mới |
| Loader child trả Class từ parent | Không có version riêng | Hiểu parent delegation và defining loader |

Các thao tác trên **một tập Class** có ngữ nghĩa theo tài liệu: nếu redefinition/retransformation báo exception trong việc áp dụng, **không class nào trong tập đó được cài bản mới**. Không suy rộng điều đó thành rollback toàn bộ side effects của ứng dụng hoặc của transformers bên ngoài.

Một lỗi transformer riêng không nhất thiết ngăn toàn bộ pipeline: Javadoc nêu JVM có thể tiếp tục các transformers khác. Không dùng exception trong transformer như cách rollback chắc chắn; nên validate bytes và log theo hợp đồng phù hợp.

## 13. Khả năng hot-reload và hạn chế của trạng thái ứng dụng

### 13.1 Redefine để vá method

Phù hợp cho một bản sửa logic với schema không đổi. Ưu điểm là existing objects dùng code mới trên invocation mới, không cần thay references toàn hệ thống.

Nhưng nếu logic mới mong đợi static fields mới hoặc invariants mới, JVM không tự chuyển đổi state. Bạn phải thiết kế migration/feature flags/compatibility riêng.

### 13.2 Retransform cho monitoring

Profiler và coverage agent thường muốn thêm một số instructions vào methods hiện có mà không viết lại toàn bộ classes bằng tay. Retransform cho phép thực hiện lại pipeline khi cần enable/disable instrumentation hoặc thay strategy (trong giới hạn API/VM).

Không bảo đảm mọi class đều instrumentable; kiểm tra capabilities và modifiability.

### 13.3 New loader cho plugin evolution

Plugin manager thường nạp implementation V2 trong loader/layer mới, chuyển request tới service V2, rồi dừng V1. V2 có thể có schema khác, nhưng cần:

- Shared interface/DTO/API có cùng Class identity ở host/plugin boundary.
- Parent delegation policy không khiến child vô tình nạp bản cũ từ parent.
- In-flight requests đang dùng V1 có đường kết thúc.
- Plugin V1 được unregister listeners/services/caches.
- Threads/executors/ThreadLocal/TCCL không giữ loader V1 mãi.
- JVM chỉ có thể unload class cũ khi defining loader không còn strong references, không bảo đảm ngay sau stop.

Đừng dùng ClassLoader như sandbox bảo mật cho code không tin cậy; class-loading isolation không đồng nghĩa process isolation.

## 14. Những nhầm lẫn phổ biến

**“Redefine tạo một Class object mới.”** Sai: vẫn là target Class hiện có.

**“Retransform giống defineClass lần hai.”** Sai: retransform vận hành trên Class đã load qua transformer pipeline.

**“Static block bản mới luôn chạy khi redefine.”** Sai: <clinit> không được chủ động chạy lại.

**“Thay source/JAR rồi class tự cập nhật.”** Sai: JVM không tự ghi đè Class đang sống khi file bytes trên đĩa đổi.

**“Redefine có thể thêm method/field tùy ý.”** Sai theo giới hạn Java 17/JVMTI chuẩn.

**“Tất cả threads đang chạy lập tức dùng bytecode mới.”** Sai: active stack frames có thể tiếp tục bytecode cũ.

**“Class mới có cùng tên luôn cast sang Class cũ được.”** Sai nếu defining loaders khác.

**“Transformer canRetransform=false được gọi lại ở mỗi retransform.”** Sai: kết quả cũ được replay theo quy tắc.

**“removeTransformer hoàn nguyên changes đã cài.”** Sai: nó gỡ registration tương lai.

**“ClassFileTransformer dùng lambda Java 17 như Runnable.”** Sai: không phải functional interface; ví dụ 2 dùng anonymous class.

**“Khi isModifiableClass true thì chắc chắn capability được cấp.”** Sai: phải kiểm tra capability riêng.

**“ClassLoader mới luôn giúp unload loader cũ.”** Sai: nếu old threads/callbacks giữ strong references, loader cũ vẫn sống.

## 15. Quy trình quyết định kỹ thuật

~~~text
Muốn sửa hành vi code Java runtime?
          |
          v
Class mục tiêu đã được JVM define chưa?
          |
          +-- Chưa: bình thường dùng ClassLoader/findClass/defineClass
          |
          +-- Rồi
                |
                v
          Chỉ sửa method body / instrumentation hợp lệ?
                |
          +-----+-----+
          |           |
         Có          Không
          |           |
          v           v
    Có agent và      Cần schema mới,
    capability?      API mới, dual versions?
          |           |
       +--+--+        v
       |     |       Loader mới + lifecycle
      Có    Không
       |     |
       v     v
   Redefine/ cần công cụ khác,
   Retransform restart hoặc loader mới
~~~

**Dùng redefine** nếu bạn có replacement class-file bytes và muốn thay definition của Class đang tồn tại.

**Dùng retransform** nếu bạn muốn áp dụng lại transformer chain trên Class đã được nạp.

**Dùng loader mới** nếu cần phiên bản runtime class mới tách biệt, đặc biệt khi yêu cầu schema khác hoặc coexistence.

## 16. Tự kiểm tra

**Câu 1.** Feature v1 đã initialized state=7. Redefine từ bytes có state=999, state có đổi tự động thành 999 không?

**Đáp án:** Không. Static values được giữ; <clinit> không tự chạy lại.

**Câu 2.** Existing object gọi method sau redefine có dùng body mới không?

**Đáp án:** Có trên new invocation, với những ngoại lệ về active frames đang thực thi code cũ.

**Câu 3.** Caller của retransformClasses truyền ClassDefinition hay Class<?>?

**Đáp án:** Class<?> targets; JVM áp dụng transformer pipeline.

**Câu 4.** Transformer không-capable có được gọi lại khi retransform không?

**Đáp án:** Không; kết quả transform cũ được replay theo API.

**Câu 5.** Hai loader định nghĩa class có cùng binary name, liệu Class objects có bằng nhau?

**Đáp án:** Không; defining loader là thành phần class identity.

**Câu 6.** Muốn thêm một field mới vào bản Feature đang sống trên Java 17 chuẩn, có nên redefine không?

**Đáp án:** Không; đó là schema change không được hỗ trợ, cân nhắc loader mới và migration.

**Câu 7.** Vì sao cần Can-Retransform-Classes và addTransformer(..., true)?

**Đáp án:** Capability và đăng ký transformer phải cho phép retransformation; nếu không, lần retransform không thể gọi lại transformer theo mong đợi.

**Câu 8.** Agent mới đăng ký transformer sau khi TraceTarget đã được load. Class có tự thay đổi ngay không?

**Đáp án:** Không; ví dụ phải chủ động gọi retransformClasses.

**Câu 9.** ClassLoader.loadClass có chắc thấy file .class đã thay trên đĩa không?

**Đáp án:** Không. Loader có thể trả Class đã nạp; parent delegation cũng có thể trả Class từ nguồn khác.

**Câu 10.** Dùng loader mới để deploy plugin V2 có tự xóa instance V1 khỏi host registry không?

**Đáp án:** Không. Phải unregister và cleanup references/callbacks/threads để V1 có thể collectible.

## 17. Ghi nhớ

~~~text
Redefine:
  Class C đã tồn tại
      + replacement bytes qua ClassDefinition
      -> method body đổi hợp lệ
      -> same C, same objects, static state giữ nguyên

Retransform:
  Class C đã tồn tại
      + transformer chain được áp dụng lại
      -> method body đổi hợp lệ
      -> same C, same objects, static state giữ nguyên

New ClassLoader:
  Binary name + loader mới
      -> một Class C2 khác, C2 != C
      -> static initialization/state riêng
      -> thích hợp new schema/dual versions
~~~

**Gói gọn một câu:** Redefine **thay definition của cùng Class** từ bytes mới; retransform **tái áp dụng bytecode transformers** trên cùng Class; ClassLoader mới **tạo một type identity mới** và cần quản lý migration, boundary và lifecycle.

### Tài liệu tham khảo chính thức

- Java SE 17 Instrumentation API: https://docs.oracle.com/en/java/javase/17/docs/api/java.instrument/java/lang/instrument/Instrumentation.html
- Java SE 17 ClassDefinition API: https://docs.oracle.com/en/java/javase/17/docs/api/java.instrument/java/lang/instrument/ClassDefinition.html
- Java SE 17 ClassFileTransformer API: https://docs.oracle.com/en/java/javase/17/docs/api/java.instrument/java/lang/instrument/ClassFileTransformer.html
- Java SE 17 java.lang.instrument agent package: https://docs.oracle.com/en/java/javase/17/docs/api/java.instrument/java/lang/instrument/package-summary.html
- Java SE 17 JVM Tool Interface specification: https://docs.oracle.com/en/java/javase/17/docs/specs/jvmti.html
- Java SE 17 ClassLoader API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17 JLS §12: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="dynamic-class-definition-009">Ràng buộc phiên bản, cấu trúc lớp và quyền truy cập khi tạo lớp động</a>

<details>
<summary>Xem chi tiết</summary>

# Ràng buộc phiên bản, cấu trúc lớp và quyền truy cập khi tạo lớp động trong Java

**Tạo lớp động** (*dynamic class definition*) là việc JVM nhận một chuỗi bytecode class file tại runtime và tạo ra đối tượng `Class<?>` đại diện cho một kiểu mới trong namespace của class loader. Các API liên quan gồm `ClassLoader.defineClass(...)`, `ClassLoader.findClass(...)` và `MethodHandles.Lookup.defineClass(...)`. Bước này khác hẳn với `new SomeClass()`: **định nghĩa class tạo runtime type, không tự tạo instance của class ấy và không mặc định chạy static initializer**.

Một mảng byte có vẻ hợp lệ chưa đủ để JVM chấp nhận định nghĩa. JVM kiểm tra **phiên bản class file**, cấu trúc/bytecode, binary name, package, quan hệ với superclass/interfaces, quyền truy cập và các ràng buộc linking. Một số lỗi xảy ra ngay ở `defineClass`; lỗi khác có thể chỉ xuất hiện lúc `resolveClass`, reflective lookup, class initialization hoặc lần đầu gọi method.

Bài học này tập trung vào **“Ràng buộc phiên bản, cấu trúc lớp và quyền truy cập khi tạo lớp động”** thuộc nhóm **Định nghĩa lớp động** của **Java core – classloader**, sử dụng JDK 17+ làm nền tảng và ví dụ có thể biên dịch/chạy.

## 1. Năm tầng ràng buộc cần phân biệt

| Tầng | Câu hỏi chính | Ví dụ |
| --- | --- | --- |
| **Class-file version** | JVM hiện tại có hiểu format/feature của bytecode này không? | Major 65 của Java 21 trên JVM chỉ hỗ trợ đến 61 của Java 17 |
| **Class-file format / verification** | Bytes có cấu trúc `ClassFile`, constant pool, methods, bytecode, stack-map và invariants hợp lệ không? | Magic sai, constant pool hỏng, verification type mismatch |
| **Identity / name / linkage** | Binary name truyền vào có khớp class file? Loader có định nghĩa trùng? References có resolve hợp lệ? | `NoClassDefFoundError` do wrong name, duplicate-definition `LinkageError` |
| **Runtime package / module / access** | Class mới có quyền dùng target types/members hoặc module packages không? | Package-private member cùng tên package nhưng khác defining loader |
| **Protection domain / code source / package consistency** | Class thuộc security domain nào? Package có certificates/sealing rules tương thích không? | `SecurityException` khi chữ ký package không thống nhất |

**Không dồn mọi lỗi vào một `catch (Exception)`.** Phần lớn lỗi xác minh/liên kết thuộc họ `Error` (`LinkageError` và subclasses). Khi debug, phải phân biệt **không tìm ra bytes**, **bytes sai**, **bytes có thể định nghĩa nhưng truy cập sai**, và **class có thể nạp nhưng hoạt động sau đó lỗi**.

## 2. Runtime type identity: binary name chưa đủ

Trong JVM, hai classes có thể cùng `Class.getName()` nhưng khác runtime type nếu được **hai defining loaders khác nhau** định nghĩa:

```text
name = "plugins.Task"

(hostLoader defines plugins.Task)   → runtime Class A
(pluginLoader defines plugins.Task) → runtime Class B

A.getName().equals(B.getName()) → true
A == B                           → false
```

Một cách diễn giải thực dụng: runtime class identity phụ thuộc **binary name + defining class loader**. Điều này tác động trực tiếp tới:

- `instanceof`, ép kiểu và `Class.isAssignableFrom`;
- reflective lookup trên tham số/return types;
- package-private access;
- interfaces chia sẻ giữa host và plugin;
- static state và class initialization của mỗi runtime definition;
- loader constraints khi method descriptors đi qua nhiều loaders.

Khi triển khai plugin, nên để **host API/SPI/DTO classes** được định nghĩa một lần bởi loader chung, còn implementation/private dependencies có thể do loader riêng định nghĩa. Nếu cả hai bên tự định nghĩa `api.Plugin` có cùng source code, object của plugin vẫn có thể không cast được về API host.

## 3. `ClassLoader.defineClass` nhận gì, trả gì?

Overload thông dụng:

```java
protected final Class<?> defineClass(
        String name, byte[] bytes, int offset, int length);

protected final Class<?> defineClass(
        String name, byte[] bytes, int offset, int length,
        java.security.ProtectionDomain domain);
```

Đây là **chữ ký API rút gọn để đọc**, không phải các method phải tự khai báo (chúng đã có trong `ClassLoader`). `defineClass` là `protected final`: caller không phải subclass không gọi nó trực tiếp trên loader bất kỳ; subclass thường cung cấp nó qua `findClass`.

Các tham số:

- `name` là **binary name** được mong đợi, chẳng hạn `"demo.generated.DynamicHello"`; không truyền `"demo/generated/DynamicHello.class"`.
- `bytes` là toàn bộ hoặc một đoạn của **class-file bytes**, không phải Java source UTF-8.
- `offset` và `length` xác định đoạn hợp lệ trong mảng; offset âm hoặc vượt giới hạn có thể gây `IndexOutOfBoundsException`.
- `domain` xác định `ProtectionDomain` nếu dùng overload tường minh; `null` khiến default domain theo Javadoc.
- Kết quả là `Class<?>` mới được định nghĩa (nếu thành công), **chưa phải object instance**.

Javadoc khuyến nghị truyền **binary name không null** để đối chiếu với binary name thực sự có trong bytes và thực thi những ràng buộc liên quan. Overload cũ `defineClass(byte[], int, int)` không có tham số tên đã deprecated, không nên dùng cho code mới.

### `findClass` khác `defineClass`

`findClass(name)` là hook để **tìm bytes rồi gọi `defineClass`** nếu cần. `loadClass(name)` thông thường kiểm tra lớp đã có và thử parent trước rồi mới đến `findClass`.

```text
loadClass("demo.generated.DynamicHello")
      ├─ findLoadedClass(...)
      ├─ parent delegation
      └─ findClass(...)
            ├─ đọc /generated-out/demo/generated/DynamicHello.class
            └─ defineClass("demo.generated.DynamicHello", bytes, ...)
                       ↓
                    Class<?>
```

Nếu chỉ muốn thêm nguồn bytecode, thường **override `findClass` thay vì tự override `loadClass`** để giữ đúng synchronization, parent delegation và tránh duplicate definition.

## 4. Thực hành A — định nghĩa một class Java 17 từ file bytecode

Tạo hai source files **khác output folders** để host không vô tình thấy class động trên application classpath.

```text
define-lab/
├── src/
│   ├── generated/demo/generated/DynamicHello.java
│   └── host/demo/DefineDemo.java
├── generated-out/      (sinh bởi javac)
└── host-out/           (sinh bởi javac)
```

**`src/generated/demo/generated/DynamicHello.java`:**

```java
package demo.generated;

public final class DynamicHello {
    static {
        System.out.println("DynamicHello initialized");
    }

    public DynamicHello() {
        System.out.println("DynamicHello constructed");
    }

    public String say() {
        return "Hello from dynamically defined class";
    }
}
```

**`src/host/demo/DefineDemo.java`:**

```java
package demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DefineDemo {
    static final class ByteFileLoader extends ClassLoader {
        ByteFileLoader() {
            super(ClassLoader.getPlatformClassLoader());
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            Path file = Path.of("generated-out",
                    name.replace('.', '/') + ".class");
            try {
                byte[] bytes = Files.readAllBytes(file);
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException cause) {
                throw new ClassNotFoundException(name, cause);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ByteFileLoader loader = new ByteFileLoader();

        Class<?> type =
                loader.loadClass("demo.generated.DynamicHello");

        System.out.println("defined by child=" +
                (type.getClassLoader() == loader));
        System.out.println("type=" + type.getName());
        System.out.println("before constructor");

        Object instance = type.getDeclaredConstructor().newInstance();
        String greeting =
                (String) type.getMethod("say").invoke(instance);

        System.out.println("result=" + greeting);
    }
}
```

Từ thư mục `define-lab`, biên dịch và chạy bằng JDK 17+:

```sh
javac --release 17 -d generated-out src/generated/demo/generated/DynamicHello.java
javac --release 17 -d host-out src/host/demo/DefineDemo.java
java -cp host-out demo.DefineDemo
```

**Stdout với cấu trúc đã nêu:**

```text
defined by child=true
type=demo.generated.DynamicHello
before constructor
DynamicHello initialized
DynamicHello constructed
result=Hello from dynamically defined class
```

**Các dấu mốc quan trọng:**

1. `ByteFileLoader` có parent là platform loader, không tự nhìn thấy classes trong `generated-out`.
2. `loader.loadClass` gọi `findClass` của child vì parent không tìm được binary name.
3. `Files.readAllBytes` lấy bytecode do `javac` đã sinh; `defineClass` tạo runtime class và trả `Class<?>`.
4. Lúc in `defined by child` và `type`, static initializer **chưa chạy**.
5. `getDeclaredConstructor().newInstance()` tạo object và yêu cầu class initialization nếu chưa xong: static block in trước constructor.
6. `getMethod("say").invoke(instance)` gọi instance method, không phải định nghĩa class lần nữa.

**Cực kỳ quan trọng:** lệnh `java` **không** có `generated-out` trên `-cp`. Nếu bạn thêm thư mục ấy vào application classpath rồi dùng system loader làm parent, parent-first có thể định nghĩa `DynamicHello` trước; `type.getClassLoader() == loader` khi đó không còn chắc `true`. Đây là lý do thực hành chủ động tách output directories.

`Path.of("generated-out", ...)` dùng đường dẫn tương đối working directory; các lệnh phải chạy từ `define-lab`. Ví dụ đọc file để học về bytecode, không phải một loader dùng trực tiếp cho nguồn untrusted.

## 5. Giới hạn class-file version: major/minor

Đầu mỗi file class có cấu trúc cơ bản:

```text
u4 magic            = 0xCAFEBABE
u2 minor_version
u2 major_version
u2 constant_pool_count
... constant_pool
... access_flags, this_class, super_class
... interfaces, fields, methods, attributes
```

**Major version** cho biết định dạng class file thuộc thời Java version nào. Những mốc thường gặp:

| Java release | Major version |
| --- | ---: |
| Java 8 | 52 |
| Java 9 | 53 |
| Java 11 | 55 |
| Java 17 | 61 |
| Java 21 | 65 |

Một JVM chỉ hỗ trợ những class-file versions nằm trong phạm vi mà nó nhận biết. Với **Java 17**, một class có major version **65** (Java 21) hoặc cố tình đặt **100** không thể được định nghĩa bình thường: thường báo **`UnsupportedClassVersionError`**.

**Đừng suy từ tên file `.class`, tên JAR hoặc property `sourceCompatibility`** rằng bytes đã có đúng version. Hãy kiểm tra thực tế:

```sh
javac --release 17 -d generated-out src/generated/demo/generated/DynamicHello.java
javap -verbose -classpath generated-out demo.generated.DynamicHello
```

Trong output `javap -verbose`, tìm dòng:

```text
major version: 61
```

`javac --release 17` vừa nhắm ngôn ngữ/bytecode Java 17 vừa giới hạn public APIs thích hợp với release đã chọn. Chỉ bật `-source` mà quên target/release không phải lúc nào cũng đủ để tránh phụ thuộc nhầm API phiên bản mới.

### Preview features cũng có điều kiện

Không chỉ major version: có các quy tắc riêng với **preview class files** dùng minor version đặc biệt. Những bytes được build bằng preview features có thể chỉ chạy khi JVM hỗ trợ đúng release với `--enable-preview`, không phải cứ major version nằm trong khoảng là hợp lệ. Khi gặp `UnsupportedClassVersionError`, đọc cả **major/minor**, compiler flags và JVM version.

## 6. Thực hành B — wrong binary name, bytes sai và version quá mới

Dùng lại `generated-out/demo/generated/DynamicHello.class` từ thực hành A, không cần chạy `DefineDemo`. Tạo tệp **`src/host/demo/ClassBytesCheckDemo.java`**:

```java
package demo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

public final class ClassBytesCheckDemo {
    static final class Definer extends ClassLoader {
        Definer() {
            super(ClassLoader.getPlatformClassLoader());
        }

        Class<?> put(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    public static void main(String[] args) throws Exception {
        byte[] correct = Files.readAllBytes(Path.of(
                "generated-out/demo/generated/DynamicHello.class"));

        try {
            new Definer().put("demo.generated.WrongName", correct);
        } catch (NoClassDefFoundError error) {
            System.out.println("wrong name: " +
                    error.getClass().getSimpleName());
        }

        try {
            new Definer().put("demo.generated.Bad", 
                    "not a class".getBytes(StandardCharsets.UTF_8));
        } catch (ClassFormatError error) {
            System.out.println("bad bytes: " +
                    error.getClass().getSimpleName());
        }

        byte[] tooNew = correct.clone();
        tooNew[6] = 0;
        tooNew[7] = 100; // major = 100, không được Java 17 hỗ trợ

        try {
            new Definer().put("demo.generated.DynamicHello", tooNew);
        } catch (UnsupportedClassVersionError error) {
            System.out.println("too new: " +
                    error.getClass().getSimpleName());
        }
    }
}
```

Biên dịch/chạy với **JDK 17**:

```sh
javac --release 17 -d host-out src/host/demo/ClassBytesCheckDemo.java
java -cp host-out demo.ClassBytesCheckDemo
```

**Stdout dự kiến trên Java 17/HotSpot với bytes đã biên dịch như trên:**

```text
wrong name: NoClassDefFoundError
bad bytes: ClassFormatError
too new: UnsupportedClassVersionError
```

**Phân tích ba trường hợp:**

- **Wrong name:** `defineClass("demo.generated.WrongName", correctBytes, ...)` nhưng `this_class` thực tế của file là `demo.generated.DynamicHello`. Javadoc `ClassLoader.defineClass` nêu rõ mismatch dẫn tới `NoClassDefFoundError`. Đó không đơn thuần là `ClassNotFoundException`.
- **Bad bytes:** chuỗi `"not a class"` không phải một `ClassFile` hợp lệ (magic/cấu trúc sai), nên ném `ClassFormatError`.
- **Too new:** byte thứ 6–7 biểu diễn **major version** trong header class file (đếm từ 0). Ta sửa từ 61 lên 100; Java 17 không hiểu version này nên `UnsupportedClassVersionError`.
- Mỗi phép thử dùng một **Definer instance mới** để ranh giới identity/duplicate-definition không ảnh hưởng lẫn nhau.
- `UnsupportedClassVersionError` kế thừa `ClassFormatError`, vì vậy **catch subclass trước superclass** nếu cùng một try-block.

**Không bao giờ patch class bytes tùy tiện trong sản phẩm thật.** Sửa version header không “chuyển đổi” các bytecode instructions/attributes của class file đời mới thành code có thể chạy trên JVM cũ. Muốn chạy trên Java 17, phải compile/transform bằng tool hỗ trợ và loại bỏ những features/APIs không tương thích.

## 7. Class-file structure không chỉ có version

Cấu trúc `ClassFile` theo JVMS `4.1` gồm:

- **`magic`**: nhận dạng file class.
- **`minor_version` và `major_version`**: phiên bản định dạng.
- **`constant_pool`**: các symbol references, UTF-8 strings, type/method/field descriptors, method handles và metadata liên quan.
- **`access_flags`**: modifiers như public, final, interface, abstract, enum, annotation, module.
- **`this_class`/`super_class`**: tên class đang định nghĩa và superclass.
- **`interfaces`**: những interface mà class khai báo.
- **`fields`** và **`methods`**: signatures và metadata.
- **`attributes`**: `Code`, `StackMapTable`, annotations, `NestHost`/`NestMembers`, `Record`, `PermittedSubclasses`, v.v.

JVM phải đảm bảo **format checking** và **verification** theo JVMS. Một transformer/sinh bytecode tự động có thể tạo:

- constant-pool index vượt giới hạn hoặc tag sai;
- descriptor sai cú pháp;
- mã lệnh không hợp lệ, stack types không nhất quán;
- class khai báo mâu thuẫn như final nhưng bị extends;
- `StackMapTable` không khớp bytecode;
- names/inheritance cycles không hợp lệ;
- attribute không phù hợp version hoặc class kind.

Một số vấn đề gây **`ClassFormatError`**, một số gây **`VerifyError`**, **`ClassCircularityError`** hoặc các `LinkageError` khác theo giai đoạn. **Không dự đoán mọi lỗi bytecode đều ném tại đúng lệnh `defineClass`.** JVM có thể thực hiện một số resolution lazily; lỗi có thể xuất hiện lúc `resolveClass` hoặc khi instruction thực sự cần reference.

### `javap` là công cụ kiểm tra, không phải verifier thay JVM

```sh
javap -verbose -classpath generated-out demo.generated.DynamicHello
javap -c -p -classpath generated-out demo.generated.DynamicHello
```

`-verbose` hiển thị header/constant pool/attributes; `-c -p` hiển thị bytecode kể cả members non-public. Lệnh `javap` đọc class file và **không chứng minh** JVM runtime đã nạp class qua đúng loader, liên kết thành công với mọi dependency hay chạy initializer.

## 8. Binary name, package name và path phải nhất quán

Với source:

```java
package demo.generated;

public class DynamicHello { }
```

Các tên khác nhau là:

| Khái niệm | Giá trị |
| --- | --- |
| Java source name | `demo.generated.DynamicHello` |
| Binary name | `demo.generated.DynamicHello` |
| Class-file relative path | `demo/generated/DynamicHello.class` |
| Internal JVM class name | `demo/generated/DynamicHello` |
| Descriptor dùng khi tham chiếu type | `Ldemo/generated/DynamicHello;` |

Khi gọi `loader.loadClass` hoặc `defineClass(name, ...)`, truyền **binary name** có dấu chấm. Khi xác định file bytes từ thư mục, có thể chuyển dấu `.` sang `/`. Không chuyển mọi dấu `$` thành `/`: tên binary của nested class như `demo.Outer$Inner` chứa dấu `$` là một phần tên binary.

**Lưu ý về mảng:** `ClassLoader.defineClass` không dùng để định nghĩa array class bằng một class-file `ClassFile` mang tên kiểu `[L...;`. JVM tạo array classes theo cơ chế riêng; `Class.getClassLoader()` của array class phản ánh element-type loader (hoặc bootstrap cho primitive arrays).

### Điều gì xảy ra nếu name không khớp bytes?

`defineClass("demo.generated.WrongName", correctBytes, ...)` báo `NoClassDefFoundError` (đã kiểm chứng trong ví dụ B), thay vì tự sửa tên bytecode. Không nên truyền `name=null` chỉ để bỏ qua vấn đề: làm mất một số kiểm tra và làm code khó đảm bảo định danh, source và package chính xác.

## 9. Một runtime package phụ thuộc defining loader

Trong Java source, hai class ghi cùng:

```java
package shared;
```

Nhưng package-private access ở runtime không chỉ phụ thuộc dòng `package`. JVM xác định **runtime package** bằng package name cùng **defining loader**. Vì vậy:

```text
AppLoader defines shared.Token           → runtime package P1
PluginLoader defines shared.DynamicAccess → runtime package P2

Text package name: shared == shared
Runtime package:    P1 != P2
```

Kết quả: `DynamicAccess` có thể không được phép truy cập package-private `Token` hay package-private method `Token.value()` của parent, dù chúng cùng viết `package shared;` trong source.

Đây là điểm quan trọng khi class sinh động dùng **ByteBuddy/ASM/JavaCompiler/custom ClassLoader** để “inject” class vào package có sẵn. **Cùng tên package không đủ để có quyền private/package access**.

Trong named modules, JPMS còn có các quy tắc readability/exports/opens; không xem defining-loader identity là cách tự vượt module restrictions.

## 10. Thực hành C — cùng tên package nhưng bị từ chối package-private access

Cấu trúc project:

```text
access-lab/
├── src/
│   ├── host/shared/Token.java
│   ├── host/shared/AccessDemo.java
│   └── plugin/shared/DynamicAccess.java
├── host-out/
└── plugin-out/
```

**`src/host/shared/Token.java`:**

```java
package shared;

final class Token {
    static String value() {
        return "host-private-package";
    }
}
```

**`src/plugin/shared/DynamicAccess.java`:**

```java
package shared;

public final class DynamicAccess {
    public static String call() {
        return Token.value();
    }
}
```

Source trên hợp lệ khi compiler được cấp `host-out` làm compile classpath, vì Java compiler nhìn hai source types trong cùng package `shared`. Nhưng ở runtime chúng có **defining loaders khác nhau**.

**`src/host/shared/AccessDemo.java`:**

```java
package shared;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AccessDemo {
    static final class PluginLoader extends ClassLoader {
        PluginLoader() {
            super(AccessDemo.class.getClassLoader());
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            if (!name.equals("shared.DynamicAccess")) {
                throw new ClassNotFoundException(name);
            }
            try {
                byte[] bytes = Files.readAllBytes(Path.of(
                        "plugin-out/shared/DynamicAccess.class"));
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException cause) {
                throw new ClassNotFoundException(name, cause);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("host call=" + Token.value());

        PluginLoader child = new PluginLoader();
        Class<?> dynamic = child.loadClass("shared.DynamicAccess");

        System.out.println("same textual package=" +
                dynamic.getPackageName().equals(
                        Token.class.getPackageName()));
        System.out.println("different defining loaders=" +
                (dynamic.getClassLoader()
                        != Token.class.getClassLoader()));

        try {
            dynamic.getMethod("call").invoke(null);
            System.out.println("unexpected success");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            System.out.println("cause=" +
                    cause.getClass().getSimpleName());
        }
    }
}
```

Biên dịch từ `access-lab`:

```sh
javac --release 17 -d host-out src/host/shared/Token.java src/host/shared/AccessDemo.java
javac --release 17 -cp host-out -d plugin-out src/plugin/shared/DynamicAccess.java
java -cp host-out shared.AccessDemo
```

**Stdout trên OpenJDK/HotSpot 17 theo cấu hình này:**

```text
host call=host-private-package
same textual package=true
different defining loaders=true
cause=IllegalAccessError
```

**Lý giải:** `Token` là package-private do app loader định nghĩa. Class `DynamicAccess` được plugin loader tự define dù cùng `package shared`. Khi bytecode `DynamicAccess.call()` cố gọi `Token.value()`, JVM kiểm tra runtime accessibility; hai types không thuộc cùng runtime package, gây `IllegalAccessError`. Reflection ở đây chỉ gọi một public static method `call`; lỗi thực sự sinh **bên trong method body**, vì vậy `Method.invoke` bọc thành `InvocationTargetException` và `getCause()` trả `IllegalAccessError`.

Nếu JVM thực hiện resolution sớm, thời điểm báo một lỗi liên kết có thể thay đổi; thông điệp/stacktrace chi tiết cũng phụ thuộc implementation. **Nguyên tắc access không thay đổi:** hai defining loaders riêng không cấp quyền package-private chỉ dựa vào tên package.

**Cách sửa kiến trúc:** chuyển API cần dùng sang một **public shared contract**, hoặc dùng cơ chế được ủy quyền như `MethodHandles.Lookup.defineClass` với lookup hợp lệ trong **cùng runtime package** khi thật sự cần tạo class được truy cập package-private. Không thêm `setAccessible(true)` một cách tùy tiện để bỏ qua JVM linking/access rules.

## 11. `MethodHandles.Lookup.defineClass`: một lựa chọn có giới hạn cụ thể

Từ Java 9, `MethodHandles.Lookup.defineClass(byte[])` có thể định nghĩa class mới bằng **cùng defining loader, cùng runtime package và cùng protection domain** với **lookup class**.

```java
MethodHandles.Lookup lookup = MethodHandles.lookup();
Class<?> generated = lookup.defineClass(bytes);
```

Đây là **snippet**: cần import `java.lang.invoke.MethodHandles`, biến `bytes` chứa class-file bytes hợp lệ, và method phải xử lý `IllegalAccessException`/linkage errors. Khi gọi `MethodHandles.lookup()` từ `demo.Host`, class bytes phải khai báo một class có **cùng package** với `demo.Host`; không thể dùng lookup đó để tùy ý định nghĩa `another.package.Generated`.

Điều kiện chính:

- Lookup phải có **`PACKAGE` access mode**, chứng tỏ caller có quyền thao tác trong runtime package tương ứng.
- Bytes phải mang **class name thuộc cùng package** với lookup class, nếu không sẽ thất bại theo contract.
- Runtime class mới được định nghĩa trong loader/module/package mà lookup cho phép, không phải tùy ý gắn vào một loader object khác.
- Lookup.defineClass **không chạy class initializer** ngay; initialization có thể xảy ra khi class được active-use sau đó.
- Class name mới không được trùng với class đã định nghĩa trong cùng defining loader.
- Không tự cấp quyền truy cập private members của unrelated classes; private access/nestmates phải tuân thủ quy tắc riêng.

### Phân biệt `Lookup.defineClass` và `Lookup.defineHiddenClass`

`defineHiddenClass` (Java 15+) tạo **hidden classes** phục vụ framework/runtime code generation. Hidden class không phải class bình thường mà `Class.forName(binaryName)` có thể khám phá theo cách thông dụng; tên/identity và lifecycle khác. API có các options như `NESTMATE` và có tham số yêu cầu initialization.

Không dùng hidden class như drop-in replacement cho plugin public API cần discover bằng binary name. Chọn `Lookup.defineClass` khi cần **class có tên, cùng package/loader với lookup**, và `defineHiddenClass` khi thực sự cần một runtime implementation class không dựa trên name-based discovery.

## 12. Thực hành D — `Lookup.defineClass` trong cùng runtime package

Ví dụ sau tạo một file class đã biên dịch trong package `lookupdemo`, rồi nạp bytes đó bằng lookup của host class **cùng package**.

Cấu trúc:

```text
lookup-lab/
├── src/
│   ├── generated/lookupdemo/GeneratedGreeter.java
│   └── host/lookupdemo/LookupDemo.java
├── generated-out/
└── host-out/
```

**`src/generated/lookupdemo/GeneratedGreeter.java`:**

```java
package lookupdemo;

public final class GeneratedGreeter {
    public String message() {
        return "defined with Lookup";
    }
}
```

**`src/host/lookupdemo/LookupDemo.java`:**

```java
package lookupdemo;

import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;

public final class LookupDemo {
    public static void main(String[] args) throws Exception {
        byte[] bytes = Files.readAllBytes(Path.of(
                "generated-out/lookupdemo/GeneratedGreeter.class"));

        Class<?> type = MethodHandles.lookup().defineClass(bytes);

        System.out.println("same defining loader=" +
                (type.getClassLoader()
                        == LookupDemo.class.getClassLoader()));
        System.out.println("same package=" +
                type.getPackageName()
                        .equals(LookupDemo.class.getPackageName()));

        Object object = type.getDeclaredConstructor().newInstance();
        System.out.println("message=" +
                type.getMethod("message").invoke(object));
    }
}
```

Biên dịch/chạy trên JDK 17:

```sh
javac --release 17 -d generated-out src/generated/lookupdemo/GeneratedGreeter.java
javac --release 17 -d host-out src/host/lookupdemo/LookupDemo.java
java -cp host-out lookupdemo.LookupDemo
```

**Stdout:**

```text
same defining loader=true
same package=true
message=defined with Lookup
```

Lệnh `java` chỉ cho application loader thấy `host-out`; `GeneratedGreeter.class` chỉ ở thư mục riêng, do đó `Lookup.defineClass(bytes)` mới là hành động định nghĩa class trong app loader. Nếu bạn đưa `generated-out` lên application classpath rồi một code path khác đã nạp `lookupdemo.GeneratedGreeter`, `defineClass` sẽ không được dùng để định nghĩa trùng class.

**Lưu ý access:** ví dụ này chỉ dùng public class/method. Để thực hành package-private access, bạn có thể thêm class/member package-private cùng package và kiểm tra khi chúng thực sự được định nghĩa bởi **cùng loader**; không suy từ việc có cùng package name trong source.

## 13. Java access modifiers và JVM linking checks

Có ít nhất ba dạng access phải phân biệt:

1. **Java language access** khi biên dịch source: `public`, `protected`, package-private, `private`; compiler dùng packages và module visibility.
2. **Runtime access checking** khi liên kết hoặc invoke references: JVM xét **runtime package identity**, class/interface access, member accessibility và receiver rules phù hợp.
3. **Reflection/MethodHandles access** khi caller tra cứu/invoke members: phụ thuộc caller class, lookup modes, `AccessibleObject` và JPMS `exports/opens`.

Một class định nghĩa động không có “quyền truy cập mọi thứ” chỉ vì bytes do host sinh ra. Nếu runtime class nằm trong loader khác, nó không có package-private privileges của host. Nếu nằm trong named module không đọc được module khác, bytecode references có thể gặp access/linking errors.

### `public` không luôn đủ

Một public class thuộc package **không exported** của named module không tự trở thành accessible với arbitrary modules bên ngoài. `public` là điều kiện của Java type access, còn JPMS module readability/exports là điều kiện khác.

`opens` cho phép những dạng deep reflection nhất định; nó không có nghĩa compile-time/public access qua mọi modules đều hợp lệ. Named modules quản lý bằng module graph, không tự được “mở rộng” chỉ vì class loader tự định nghĩa thêm class.

**Không đánh đồng** `IllegalAccessError` (lỗi linking/runtime access trong bytecode) với `IllegalAccessException` (checked exception từ reflection/Lookup) hoặc `InaccessibleObjectException` (deep reflection bị JPMS ngăn). Chúng xuất hiện ở những APIs/giai đoạn khác nhau.

## 14. JPMS: module ownership của class sinh động

Khi dùng `ClassLoader.defineClass` với loader trên classpath thông thường, class được định nghĩa thường thuộc **unnamed module** gắn với defining loader. Việc đặt tên JAR hoặc nhúng `module-info.class` vào URL không tự biến class thành một named module có `requires`/`exports` được resolve hợp lệ.

Muốn tạo named-module plugin ecosystem, cần xem:

- `ModuleFinder` để xác định modular artifacts;
- `Configuration.resolve(...)` để resolve module graph;
- `ModuleLayer.defineModulesWithOneLoader(...)` hoặc các API tương ứng để thiết lập layer/loader;
- `uses`, `provides`, `requires`, `exports`, `opens` của module descriptors;
- package uniqueness/visibility và loader ownership trong layer.

`MethodHandles.Lookup.defineClass` có phạm vi loader/package/module từ lookup class; nó không phải API “inject class tùy ý vào mọi named module”. Không dùng reflective access để cố bypass module boundaries một cách ngẫu nhiên; module openness và lookup privileges phải được cấp hợp lệ.

### Không nhầm `module-info.class` với Java class thường

Class file mang `ACC_MODULE` có cấu trúc đặc biệt của module descriptor theo JVMS `4`. Nó không phải Java class để tạo instance bằng `new` hoặc `ClassLoader.defineClass("module-info", bytes,...)` theo cách plugin implementation thông thường. Module resolution là một cơ chế riêng.

## 15. Bảo vệ package `java.*` và tên namespace nhạy cảm

Javadoc `ClassLoader.defineClass` xác định restriction cho các names bắt đầu bằng `"java."`. Custom application loader thông thường không được `defineClass("java.lang.MyString", ...)` để giả mạo namespace nền tảng: hệ thống có thể ném **`SecurityException`**.

Tương tự, khi class đã nằm trong package có một bộ **signer certificates** nhất định, việc define class mới với certificates khác có thể gây `SecurityException`. Rule này ngăn kết hợp tùy tiện bytes có trạng thái ký khác nhau trong cùng package của một defining loader.

**Các ràng buộc này không phải “gợi ý” cho transformer**. Khi xây code generation library, hãy:

- chọn namespace hợp pháp và riêng của ứng dụng;
- không tạo class vào package `java.*`;
- giữ nhất quán CodeSource/ProtectionDomain khi cần;
- kiểm tra package sealing khi dùng `URLClassLoader`/JAR manifests;
- tránh định nghĩa thêm class vào package do dependency signed/sealed quản lý nếu không thật sự hiểu hậu quả.

## 16. ProtectionDomain và CodeSource: nói đúng về quyền

Overload `defineClass(name, bytes, off, len, ProtectionDomain)` cho caller tường minh một **protection domain**. `ProtectionDomain` có thể gắn với `CodeSource`, certificates và các thành phần security policy lịch sử.

Trên JDK 17, **Security Manager** đã bị deprecated for removal, và không nên coi việc chọn domain là một sandbox đầy đủ cho code không tin cậy. Ranh giới class loader/module visibility khác với **quyền truy cập file/network/process**.

Ví dụ snippet tường minh nguồn bytecode:

```java
CodeSource source = new CodeSource(
        new URL("file:/plugins/example.jar"),
        (java.security.cert.Certificate[]) null);

ProtectionDomain domain = new ProtectionDomain(source, null);
Class<?> type = defineClass(name, bytes, 0, bytes.length, domain);
```

Đây là **snippet bên trong subclass `ClassLoader`**, cần imports `java.net.URL`, `java.security.CodeSource`, `java.security.ProtectionDomain`, biến `name` và `bytes`; không phải Java standalone program. Thực tế protection domain/permissions/certificates cần được cấu hình đúng theo runtime. Đừng copy đoạn này rồi nghĩ mọi permissions đều bị vô hiệu hóa hoặc đã an toàn trước malicious bytecode.

**Nếu đầu vào không tin cậy**, cân nhắc isolation ở cấp **process/container** và kiểm soát nguồn bytecode, signature/allowlist, API/IO permissions và resource quotas; `defineClass` chỉ định nghĩa JVM type chứ không cung cấp isolation đầy đủ.

## 17. Package signing và sealing: hai cơ chế khác nhau

**Signer certificate consistency:**

- Khi một defining loader define class trong package, chứng chỉ ký có thể đặt ràng buộc nhất quán cho những classes khác trong package.
- Nếu bytes/class definition tiếp theo dùng certificate set khác, `defineClass` có thể ném `SecurityException`.
- `ProtectionDomain.getCodeSource()` có thể cung cấp code source/certificates trong những điều kiện phù hợp.

**Package sealing:**

- JAR manifest có thể khai báo package `Sealed: true`; một package sealed được gắn với nguồn code theo rules của loader.
- `URLClassLoader` và package metadata hỗ trợ kiểm tra sealing khi định nghĩa package.
- Việc tự dùng `ClassLoader.defineClass` trong loader tùy biến không có nghĩa hệ thống tự động áp y hệt chính sách sealing của `URLClassLoader` nếu loader không quản lý `definePackage`, code source và nguồn JAR như URLClassLoader.
- Đừng giả định “cùng package name thì có thể ghép các JAR bất kỳ”, nhất là signed/sealed packages.

Những ràng buộc signing/sealing là **consistency và integrity mechanisms**, không phải bằng chứng class file có thể chạy hay có business-level authorization.

## 18. Quan hệ superclass/interfaces và loading constraints

Một class được define thành công có thể vẫn chưa dùng được nếu **superclass/interface** hoặc các symbolic references vi phạm quy tắc JVM.

Ví dụ tình huống:

```text
Host loader defines api.Message → runtime type M1
Plugin loader defines api.Message → runtime type M2

HostInterface.accept(api.Message) expects M1
Plugin implementation uses M2

JVM method descriptors có thể dẫn tới
loader constraint violation / LinkageError
```

Không thể “ép” type M2 thành M1 bằng cách chỉ đổi tên package/class trên source hoặc reflection. Cần bảo đảm các shared API types được parent/delegation về **một defining loader chung**, hoặc dùng serialization/protocol DTO qua ranh giới.

Một số ràng buộc khác:

- Class không được mở rộng một `final class`.
- Một class không thể trực tiếp implement một type không phải interface.
- Superclass/interface references phải truy cập được và conform với JVM inheritance constraints.
- Sealed types giới hạn permitted direct subclasses/implementations; check có thể dẫn tới `IncompatibleClassChangeError` nếu class động vi phạm hierarchy.
- Bytecode tham chiếu method/field có descriptor khác runtime API có thể dẫn tới `NoSuchMethodError` / `NoSuchFieldError`.

**Đừng khẳng định mọi lỗi xuất hiện ngay tại `defineClass`:** một số checks xảy ra trong loading/linking, những checks khác được JVM trì hoãn đến lần đầu resolve/invoke references.

## 19. Sealed classes, records và nestmates trong Java 17+

### Sealed classes

Java 17 hỗ trợ `sealed` và `permits`. Bytecode class file lưu danh sách permitted subclasses qua `PermittedSubclasses` attribute. Một class động **không thể tùy ý `extends` sealed superclass** chỉ bằng cách đặt đúng tên trong source. JVM phải kiểm tra permitted direct subclasses phù hợp, module/package constraints và type identity.

Một `sealed` superclass do parent loader định nghĩa không nhất thiết cho child loader định nghĩa subclass trực tiếp từ một package nhìn như cùng package. Hãy xem cả constraints của sealed hierarchy và defining loaders; đừng dựa vào tên type hay attribute được copy thủ công.

### Records

`record` sinh methods/fields/construction contract riêng, với `Record` attribute. Nếu biến đổi bytecode của record, cần giữ đúng `java.lang.Record` superclass, component metadata, canonical constructor semantics và verifier rules. Một class file có attribute lạ không tự thành record hợp lệ.

### Nests và private access

`NestHost` / `NestMembers` attributes biểu thị **nestmates**, cho phép một số private accesses giữa các classes trong cùng nest. JVM xác thực những ràng buộc này; không thể chèn attribute giả để cấp private access từ arbitrary loader. `Lookup.defineHiddenClass(..., NESTMATE)` là API có kiểm soát quyền truy cập; không tương đương gắn binary name tùy ý vào một nest.

## 20. Định nghĩa class thành công chưa đảm bảo constructor/method chạy thành công

Xem lại các giai đoạn:

```text
đọc/generate bytes
        │
        ▼
defineClass(name, bytes, ...)
        │
        ├─ class-file format + identity checks
        ▼
Class<?> type
        │
        ├─ resolveClass(type) / JVM linking
        │     └─ có thể phát hiện verifier/loader errors
        │
        ├─ getDeclaredConstructor(), getMethod()
        │     └─ reflection metadata/access checks
        │
        ├─ newInstance / static method active use
        │     └─ JVM class initialization; <clinit> có thể lỗi
        │
        └─ invoke/field access
              └─ method/field resolution và access checks
```

**Một số việc có thể được JVM làm sớm hơn** sơ đồ, nhưng những observable errors tuân thủ các điểm chương trình cần reference theo đặc tả.

Không được coi `getDeclaredConstructor()` đã chạy constructor. `Constructor.newInstance()` mới tạo object và thực hiện constructor body. Khi body constructor ném exception, reflective call thường bọc trong `InvocationTargetException`; còn `ClassFormatError` hay `IllegalAccessError` là lỗi khác, không nhất thiết do constructor body ném.

### Lỗi static initialization

Nếu `<clinit>` ném exception, lần active use đầu có thể nhận `ExceptionInInitializerError`, và những lần sau với **cùng runtime class** có thể nhận `NoClassDefFoundError` do class erroneous. Gọi lại `loadClass` không tự reset initialization state. Nếu thật sự cần định nghĩa class version khác, thường cần một loader instance mới và lifecycle phù hợp.

## 21. Duplicate definition và an toàn đồng thời khi sinh class

Một custom loader không thể define cùng binary name hai lần trong cùng defining loader. Nếu two threads cùng chạy:

```java
byte[] bytes = /* class-file bytes của generated.Task */;
defineClass("generated.Task", bytes, 0, bytes.length);
```

Đây là **snippet minh họa** trong subclass, không phải Java standalone code. Nếu không phối hợp đúng, một lần define có thể thất bại với `LinkageError` duplicate definition.

Dùng `ClassLoader.loadClass` mặc định giúp bảo vệ thông qua `getClassLoadingLock(name)` và `findLoadedClass(name)`. Nếu override `loadClass` vì child-first, phải xử lý loaded-cache và synchronization chính xác.

### Cần tạo nhiều phiên bản cùng tên?

Không thể “ghi đè Class<?> cũ” bằng `defineClass` trong **cùng loader instance**. Thay vì vậy:

- Tạo **new defining loader** cho generation mới của plugin.
- Giữ shared API ở parent ổn định để cast/contract đúng.
- Ngắt hoạt động cũ, unregister callbacks, dọn threads/`ThreadLocal` và đóng resources.
- Bỏ strong references tới provider instances/`Class<?>`/loader cũ khi muốn JVM có cơ hội unload.
- Không kỳ vọng `URLClassLoader.close()` hoặc GC diễn ra tức thì hay giúp rewrite class đã định nghĩa.

Nếu cần thay code trong cùng JVM mà không đổi loader, có những cơ chế instrumentation/redefinition với giới hạn nghiêm ngặt; **không** giả định chúng có thể tùy ý sửa hierarchy, fields và method signatures.

## 22. Bảng lỗi — phân loại theo thời điểm và nguyên nhân

| Lỗi | Tình huống điển hình | Gợi ý chẩn đoán |
| --- | --- | --- |
| `ClassNotFoundException` | Loader không tìm được bytes cho binary name | Kiểm tra lookup path, parent, JAR/classpath |
| `NoClassDefFoundError` | `name` truyền vào không khớp `this_class`, hoặc dependency/class init thất bại | Phân biệt wrong name với class erroneous/missing dependency |
| `ClassFormatError` | Bytes không tuân `ClassFile` format | Kiểm tra magic, pool, attributes, generator |
| `UnsupportedClassVersionError` | Major/minor version không được JVM hỗ trợ | `javap -verbose`, `java -version`, `javac --release` |
| `VerifyError` | Bytecode vi phạm verification/type-flow constraints | Kiểm tra stack, locals, `StackMapTable`, generator |
| `ClassCircularityError` | Hierarchy có vòng bất hợp lệ | Kiểm tra superclass/interface references |
| `LinkageError` duplicate class | Define cùng name/loader lần thứ hai | `findLoadedClass`, synchronization, loader identity |
| `SecurityException` | Define `java.*` sai loader, package signing/certificates không đồng nhất | Binary name, `ProtectionDomain`, signer set |
| `IllegalAccessError` | Runtime class/member access hoặc package/module restrictions không phù hợp | Defining loaders, runtime package, JPMS |
| `IncompatibleClassChangeError` | Không đúng class/interface/final/sealed/inheritance/linkage contract | Compare binaries, hierarchy, class file version |
| `NoSuchMethodError` / `NoSuchFieldError` | Caller bytecode references không khớp runtime library | Method descriptors, classpath order, version skew |
| `ClassCastException` | Hai runtime types cùng tên nhưng khác defining loader | `getClassLoader()`, `isAssignableFrom` |
| `ExceptionInInitializerError` | Static initializer ném exception | Cause/stacktrace từ lần active use đầu |
| `IllegalAccessException` | Reflection hoặc Lookup không có quyền truy cập | Caller, lookup modes, JPMS exports/opens |
| `InaccessibleObjectException` | Deep reflection bị module encapsulation từ chối | `opens`, target module và caller module |

Một số lỗi có thể xuất hiện nhiều giai đoạn và có dạng phụ thuộc JVM; bảng mô tả **nguyên nhân thường gặp**, không hứa “mỗi lỗi chỉ xảy ra tại một dòng cố định”.

## 23. Chẩn đoán đúng bytecode, đúng loader và đúng runtime

### Công cụ kiểm tra class file

```sh
javap -verbose -classpath generated-out demo.generated.DynamicHello
javap -c -p -classpath generated-out demo.generated.DynamicHello
java -version
```

Quan sát `major version`, `this_class`, superclass/interfaces, method descriptors và attributes. Nếu có Java 21 class mà deployment là Java 17, build phải dùng `--release 17` hoặc chạy trên runtime mới tương thích với artifact đó — **không chỉ đổi header bytes**.

### Quan sát runtime defining loader

Snippet hợp lệ khi đặt trong một class và gọi:

```java
static void describe(Class<?> type) {
    System.out.println("name=" + type.getName());
    System.out.println("loader=" + type.getClassLoader());
    System.out.println("module=" + type.getModule().getName());
    System.out.println("package=" + type.getPackageName());
}
```

`getClassLoader() == null` thường biểu diễn bootstrap loader. `type.getModule().getName() == null` thường là unnamed module, không phải class không thuộc module object nào.

### HotSpot Unified Logging

```sh
java -Xlog:class+load=info -cp host-out demo.DefineDemo
java -Xlog:help
```

Class loading log giúp thấy nguồn bytecode theo runtime, nhưng có nhiều dòng phụ thuộc implementation; không có stdout chuẩn cố định cho log JVM. `-Xlog` không chứng minh class đã initialized hay constructor đã chạy. Để kiểm tra `<clinit>`, quan sát hành vi program hoặc log class initialization nếu JDK/runtime có hỗ trợ.

### Khi lỗi phát sinh sau defineClass

In **exception type**, **cause chain**, **target binary name**, **defining loader identity**, **module/package** và **class-file version**. Đừng log toàn bộ bytes hoặc path chứa secrets vào log public. Với nhiều plugins, thêm plugin ID/generation để phân biệt hai loaders có cùng class name.

## 24. Quy trình phát hành class động an toàn hơn

1. **Chọn namespace và hợp đồng:** class mới cần public API nào, package nào? Những types nào phải được host/plugin dùng chung?
2. **Quyết định defining loader hoặc Lookup:** chọn `ClassLoader.defineClass` cho namespace riêng hoặc `Lookup.defineClass` khi cần đúng same-package/same-loader privileges.
3. **Chốt Java target runtime:** dùng `javac --release 17` nếu production chạy Java 17; kiểm tra major/minor version khi build.
4. **Sinh/đọc bytes chuẩn:** dùng compiler/bytecode generator có hỗ trợ class-file version, stack maps và attributes liên quan; không chỉnh byte tùy tiện.
5. **Đối chiếu binary name:** `defineClass(name, bytes,...)` dùng tên đúng với `this_class`.
6. **Kiểm tra structural constraints:** superclass, interfaces, final/sealed/record attributes, nest metadata.
7. **Kiểm tra runtime access:** runtime package + defining loader, module readability/exports/opens, package-private/nestmate rules.
8. **Kiểm tra security metadata:** package signer consistency, code source/protection domain, sealing nếu loader áp dụng.
9. **Đồng bộ hóa:** tránh duplicate definitions, dùng loader locking nếu nhiều threads tạo class cùng tên.
10. **Validate ứng dụng:** resolve, instantiate/invoke trong test, kiểm tra missing dependencies, binary signatures và static initializer errors.
11. **Đóng vòng đời:** dùng loader mới cho generation khác, quản lý references và callbacks khi hot-reload.
12. **Bảo vệ môi trường:** nếu bytes đến từ bên thứ ba không tin cậy, dùng isolation/security controls độc lập; class loader không phải sandbox.

### Điều nên kiểm thử tự động

- Version lớn hơn runtime target bị từ chối đúng loại lỗi.
- Corrupt class-file bytes bị từ chối.
- Tên truyền `defineClass` không khớp `this_class` bị báo wrong-name error.
- Trùng class cùng loader không được redefine.
- Cùng Java package text nhưng khác defining loaders không tự có quyền package-private.
- Shared API type identity đúng khi provider/plugin được nạp.
- Class mới ở module/package được quyền truy cập những APIs cần dùng.
- Không có static constructor side effects trước active use nếu thiết kế không yêu cầu.
- Hai generations của plugin có lifecycle/reference cleanup đúng, không giữ classloader cũ không cần thiết.

## 25. Các nhầm lẫn thường gặp

- **“`defineClass` là `new`.”** Sai: nó tạo runtime `Class<?>`, không tạo instance.
- **“Chỉ cần mảng byte bắt đầu bằng CAFEBABE.”** Sai: cả format, pool, descriptors, attributes và verification phải hợp lệ.
- **“Có thể chạy class Java 21 trên JVM Java 17 bằng cách sửa major 65 thành 61.”** Sai: các instructions/APIs/attributes có thể không tương thích.
- **“`name` của `defineClass` chỉ để log.”** Sai: API đối chiếu binary name với bytes và áp ràng buộc namespace.
- **“Hai class cùng `package shared` luôn truy cập package-private của nhau.”** Sai: runtime package còn có defining loader.
- **“`Lookup.defineClass` có thể inject class vào bất cứ loader/module nào.”** Sai: quyền được ràng buộc bởi lookup class và PACKAGE mode.
- **“`setAccessible(true)` sửa được mọi `IllegalAccessError`.”** Sai: runtime bytecode linking checks và module restrictions là chuyện khác.
- **“`ClassFormatError` và `VerifyError` đều là `ClassNotFoundException`.”** Sai: chúng là những lỗi JVM khác nhau.
- **“Class define thành công tức là tất cả method references đã resolve.”** Không chắc; một số resolution có thể lazy.
- **“Đã nạp class, constructor chắc chắn chạy.”** Sai: class initialization và object creation là bước sau.
- **“Có thể define lại cùng class name trong cùng loader khi sửa JAR.”** Không; cần loader mới hoặc một cơ chế redefinition chuyên biệt với giới hạn.
- **“ProtectionDomain làm plugin sandbox tuyệt đối.”** Sai; không thay thế process isolation và security model.
- **“Một JAR chứa `module-info.class` tự trở thành named module khi URLClassLoader đọc.”** Không; JPMS resolution/layers là cơ chế khác.
- **“`IllegalAccessError` luôn xuất hiện ngay ở `defineClass`.”** Không bảo đảm thời điểm nếu symbolic references được resolve về sau.

## 26. Câu hỏi tự kiểm tra

**1. `defineClass` trả gì và chưa làm gì?** Nó trả `Class<?>` của runtime type mới; không tự trả instance, không bắt buộc chạy class initializer.

**2. Với `--release 17`, major version class file là bao nhiêu?** 61.

**3. Class major 65 có chạy được trên Java 17 không?** Không; phiên bản vượt mức hỗ trợ, thường báo `UnsupportedClassVersionError`.

**4. Tại sao `defineClass("a.Other", bytes của a.Widget)` lỗi?** Vì binary name không khớp `this_class`; có thể nhận `NoClassDefFoundError`.

**5. Magic CAFEBABE có đủ để xác nhận class hợp lệ không?** Không. Cần structure/constant-pool/method/attribute/bytecode verification hợp lệ.

**6. `UnsupportedClassVersionError` thuộc họ nào?** Nó kế thừa `ClassFormatError`, thuộc `LinkageError`.

**7. Cùng `package shared` trong source có cho phép package-private access giữa hai defining loaders khác nhau không?** Không; runtime package phụ thuộc defining loader.

**8. Vì sao `Lookup.defineClass` chỉ dùng bytes cùng package với lookup class?** Vì API xác thực PACKAGE lookup privileges và đặt class trong cùng runtime package/loader.

**9. `defineClass` có tự chạy `<clinit>` không?** Không theo mặc định; class initialization được yêu cầu ở active use thích hợp.

**10. Parent-first giúp gì khi plugin dùng host API?** Giúp provider/host dùng cùng `Class<?>` của interface/DTO do parent định nghĩa.

**11. Cùng tên class có thể tồn tại hai phiên bản trong một JVM không?** Có nếu khác defining loaders; không thể định nghĩa cùng binary name hai lần trong cùng defining loader.

**12. `IllegalAccessError` khác `IllegalAccessException` ở đâu?** `IllegalAccessError` thường là linking/runtime access error, còn `IllegalAccessException` là checked exception từ reflection/Lookup API.

**13. `ClassLoader.defineClass` có thể tự inject class vào named module bất kỳ không?** Không; loader/module/Lookup access và JPMS restrictions vẫn áp dụng.

**14. Vì sao `NoClassDefFoundError` không nhất thiết có nghĩa file `.class` mất?** Nó có thể do wrong binary name, lỗi resolution hoặc class đã initialization thất bại.

**15. Cách an toàn để cập nhật implementation class đã được định nghĩa trong plugin loader?** Tạo plugin class loader/generation mới, dùng shared API identity và quản lý vòng đời cũ; không gọi define trùng.

## 27. Tài liệu chuẩn

- [Java SE 17 `ClassLoader` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `defineClass` overloads, `findClass`, parent delegation, name/package/certificate checks, ProtectionDomain và linking errors.
- [Java SE 17 `MethodHandles.Lookup` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandles.Lookup.html): `defineClass`/`defineHiddenClass`, lookup modes, same-package/loader restrictions.
- [JVMS Java SE 17 — Chapter 4, The class File Format](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html): version numbers, `ClassFile` layout, verification, `Record` và `PermittedSubclasses` attributes.
- [JVMS Java SE 17 — Chapter 5, Loading, Linking, and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): runtime identity, class loader constraints, access/linking/initialization.
- [JLS Java SE 17 — Chapter 6, Names](https://docs.oracle.com/javase/specs/jls/se17/html/jls-6.html): binary names, packages và access rules.
- [Java SE 17 `ProtectionDomain` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/ProtectionDomain.html): protection domains và code source.
- [JDK 17 `javap` tool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): kiểm tra major version, pool và bytecode.

**Kết luận:** Khi tạo class động, JVM không chỉ “đọc được bytecode là chạy”. Class file phải có **version và cấu trúc hợp lệ**, binary name/loader identity phải nhất quán, superclass/interfaces/attributes đáp ứng quy tắc linking, và mọi truy cập vẫn chịu **runtime package, JPMS và ProtectionDomain**. Hãy dùng đúng `ClassLoader.defineClass` hoặc `Lookup.defineClass`, kiểm chứng class-file bytes và runtime access ở từng bước, thay vì xử lý mọi thất bại như một lỗi classpath chung.


</details>

- [Quay lại đầu trang](#back-to-top)
