<a id="back-to-top"></a>

# Cơ chế ủy quyền

## Menu
- [Parent delegation và mục đích dùng chung kiểu nền tảng](#parent-delegation-001)
- [Trình tự mặc định: findLoadedClass, loader cha và findClass](#parent-delegation-002)
- [Vai trò của parent null khi ủy quyền tới bootstrap](#parent-delegation-003)
- [Quan hệ parent là quan hệ đối tượng, không phải kế thừa lớp Java](#parent-delegation-004)
- [Defining loader và initiating loader](#parent-delegation-005)
- [Phân biệt loader được yêu cầu với loader thực sự định nghĩa lớp](#parent-delegation-006)
- [Khả năng nhìn thấy từ loader con tới loader cha](#parent-delegation-007)
- [Vì sao loader cha không tự tìm phụ thuộc của loader con](#parent-delegation-008)
- [Ủy quyền trong mô hình đồ thị và ngoại lệ với hệ module](#parent-delegation-009)

## <a id="parent-delegation-001">Parent delegation và mục đích dùng chung kiểu nền tảng</a>

<details>
<summary>Xem chi tiết</summary>

# Parent delegation và mục đích dùng chung kiểu nền tảng trong Java

**Parent delegation** (ủy quyền cho class loader cha) là cơ chế mặc định của `java.lang.ClassLoader`: khi một loader nhận yêu cầu nạp một lớp, nó **ưu tiên để loader cha tìm lớp trước**, chỉ tự tìm/định nghĩa lớp nếu đường ủy quyền không nạp được. Cách tổ chức này giúp ứng dụng và các loader con dùng **chung định nghĩa của các kiểu nền tảng và kiểu API đã được loader cha cung cấp**, tránh tình trạng nhiều bản sao runtime của cùng một tên lớp làm hỏng việc gán kiểu, ép kiểu hoặc giao tiếp giữa plugin và ứng dụng.

Hai nguyên tắc cần nhớ:

1. **Một tên lớp chưa đủ xác định một kiểu Java lúc chạy.** Danh tính runtime class phụ thuộc vào cả *binary name* và *defining class loader*.
2. **Parent-first là chính sách mặc định của `ClassLoader.loadClass`**, không phải mệnh lệnh buộc mọi class loader tùy biến đều phải hoạt động hệt nhau. Một số loader chuyên dụng cố tình dùng thứ tự khác để cô lập dependency.

## 1. Vì sao Java có nhiều class loader?

Ứng dụng không nhất thiết có một bảng tên lớp duy nhất. Class loader cho phép JVM tìm, định nghĩa và tổ chức các lớp đến từ những nguồn khác nhau, ví dụ JDK modules, classpath, plugin, mã được sinh trong runtime hoặc các vùng triển khai độc lập.

Với Java 17+ thông thường, chuỗi loader có thể hình dung như sau:

```text
Application / System ClassLoader
        |
        | parent
        v
Platform ClassLoader
        |
        | parent
        v
Bootstrap loader (do JVM cung cấp, không phải một ClassLoader Java thường)
```

- **Bootstrap loader** định nghĩa nhiều lớp cốt lõi như `java.lang.Object`, `java.lang.String`; `String.class.getClassLoader()` trả `null`, theo quy ước API đại diện cho bootstrap loader.
- **Platform class loader** có nhiệm vụ nạp các lớp trong tập platform modules theo cơ chế JDK hiện đại.
- **Application/System class loader** thường nạp mã ứng dụng trên classpath và các thành phần tương ứng.

Sơ đồ là mô hình phổ biến giúp hiểu parent delegation, **không phải bản đồ đầy đủ của mọi đường nạp lớp**: Java Platform Module System (JPMS), module reader, class loader tùy biến và một số cách triển khai JDK có thể khiến việc phân công module hoặc định tuyến class phức tạp hơn chuỗi minh họa. Đặc biệt, `getParent() == null` không có nghĩa “không hề có code nền tảng nào để ủy quyền”; đối với API `ClassLoader`, nó biểu diễn đường tới bootstrap loader.

Một ứng dụng cũng có thể tạo các loader khác:

```text
                  Application Loader (chứa shared-api)
                  /                  \
                 v                    v
           Plugin A Loader      Plugin B Loader
           (plugin-a.jar)      (plugin-b.jar)
```

Hai plugin có thể có các implementation riêng nhưng cùng nhìn thấy `shared.api.Plugin` từ application loader. Chính **định nghĩa API được chia sẻ** này làm các đối tượng plugin có thể được kiểm tra bằng `instanceof`, ép về interface và truyền cho ứng dụng.

## 2. Parent delegation hoạt động như thế nào?

Phương thức trọng tâm là `ClassLoader.loadClass(String name)` hoặc biến thể protected `loadClass(String name, boolean resolve)`. Với **triển khai mặc định của `ClassLoader`**, thuật toán có thể mô tả:

1. Nhận **binary name** như `com.example.service.OrderService`, không phải đường dẫn `com/example/service/OrderService.class`.
2. Dùng `findLoadedClass(name)` để xem loader hiện tại đã là loader khởi phát (*initiating loader*) của lớp đó chưa; nếu có, trả về `Class<?>` đã biết.
3. Nếu chưa, **ủy quyền cho parent loader**, hoặc thử bootstrap loader khi không có Java parent.
4. Chỉ khi đường parent/bootstrap không tìm được lớp, gọi `findClass(name)` của loader hiện tại.
5. Nếu yêu cầu resolve, gọi `resolveClass(...)`; trả về đối tượng `Class<?>`.

Minh họa bằng giả mã (không phải nguyên văn triển khai JDK):

```text
loadClass(binaryName, resolve):
    lock phù hợp cho yêu cầu tải lớp

    c = findLoadedClass(binaryName)

    if c == null:
        try:
            c = parent != null
                ? parent.loadClass(binaryName)
                : bootstrapFind(binaryName)
        catch ClassNotFoundException:
            c = null

        if c == null:
            c = findClass(binaryName)

    if resolve:
        resolveClass(c)

    return c
```

Khi tự viết loader, thường override `findClass` để đọc byte và gọi `defineClass`; kế thừa `loadClass` giữ nguyên thứ tự parent-first. Việc override `loadClass` để ép child-first là lựa chọn có hậu quả về tính tương thích và nên được giới hạn rất cụ thể.

**Điều kiện lỗi:** nếu parent không tìm thấy lớp, nó báo `ClassNotFoundException` để loader con có cơ hội tự tìm. Nhưng nếu class file có vấn đề thật sự, chẳng hạn `ClassFormatError`, `VerifyError` hoặc lỗi linking khác, không được mặc định coi đó là “không tìm thấy” để âm thầm thay phiên bản khác. Đây là những lỗi khác bản chất.

### Parent-first không nhất thiết luôn gọi `findClass`

Nếu parent đã tìm thấy `java.util.List` hoặc `com.example.SharedApi`, class loader con thường **không phải định nghĩa lại** tên đó. Nó nhận cùng tham chiếu `Class<?>` mà đường cha cung cấp.

Ngược lại, khi parent không chứa `com.plugin.Feature` nhưng child loader có bytecode của lớp này, child gọi `findClass`, sau đó `defineClass` để tạo runtime class được định nghĩa bởi chính child.

`resolveClass` thuộc quá trình linking, không đồng nghĩa với việc chạy khối khởi tạo `static`. Gọi `loadClass` thông thường **không bắt buộc khởi tạo lớp**.

## 3. “Dùng chung kiểu nền tảng” nghĩa là gì?

Đây là trọng tâm của parent delegation. Java kiểm tra tương thích kiểu theo **runtime type identity**. Đối với lớp/interface có cùng binary name, loader định nghĩa khác nhau thường tạo **hai kiểu khác nhau**.

Ta có thể biểu diễn danh tính của một kiểu lớp thường bằng cặp:

```text
Runtime type identity =
    (binary name, defining class loader)
```

Ví dụ:

```text
("shared.api.Message", ApplicationLoader)  ≠
("shared.api.Message", PluginLoader)
```

Dù tên lớp và bytecode giống nhau, đây vẫn là hai định nghĩa runtime khác nhau. Nếu một plugin trả về đối tượng triển khai phiên bản `shared.api.Message` tự định nghĩa, ứng dụng đang sử dụng phiên bản từ parent có thể không chấp nhận phép cast hoặc chữ ký API như mong đợi.

### 3.1. Các kiểu JDK cốt lõi

Những kiểu như `Object`, `String`, `Throwable`, `Class`, `List` và `Map` cung cấp ngôn ngữ kiểu chung để mọi phần ứng dụng giao tiếp. Đường parent delegation giúp loader con dùng cùng những định nghĩa đó từ bootstrap/platform theo kiến trúc runtime.

Ví dụ:

- Một plugin tạo chuỗi `String` và trả cho ứng dụng. Cả hai sử dụng cùng lớp nền tảng `java.lang.String`.
- Một plugin trả `java.util.List<String>`. Kiểu thô `java.util.List` và `String` được chia sẻ ở runtime; tính đúng đắn của từng phần tử và generic contract vẫn cần tuân thủ (generic type parameters bị xóa phần lớn ở runtime).
- Một plugin ném `IllegalArgumentException`. Ứng dụng nhận diện được kiểu exception thuộc runtime nền tảng.

Parent delegation là một phần giúp tránh “hai `java.lang.String` không tương thích”, nhưng **không tự động đảm bảo mọi API của plugin đều tương thích**: application-specific API và dependency còn phải được định nghĩa từ loader chung hoặc tương thích qua cơ chế cầu nối phù hợp.

### 3.2. Các interface và model dùng chung

Giả sử có API:

```java
package shared.api;

public interface Plugin {
    String execute();
}
```

Application nạp `shared.api.Plugin` từ loader cha. Mỗi plugin loader nên nạp **chính interface đó** qua delegation, còn `plugins.alpha.AlphaPlugin` và `plugins.beta.BetaPlugin` có thể do loader con tương ứng định nghĩa. Ứng dụng khi đó có thể nhận và gọi object bằng kiểu `shared.api.Plugin`.

Tuy nhiên, nếu mỗi plugin mang riêng một bản `shared.api.Plugin` và loader tùy biến child-first tự định nghĩa nó, việc tên package và class giống nhau không làm hai interface trở thành cùng kiểu. Các thao tác như cast và đăng ký service có thể thất bại.

Đoạn interface trên là **một phần API minh họa**, không có `main` và cần được biên dịch/triển khai như dependency chia sẻ của hệ thống plugin.

## 4. Ví dụ Java 17+: parent-first chia sẻ Class object, child-first tạo kiểu khác

Thực hành với hai tệp Java trong **cùng thư mục nguồn**. Mục đích là chứng minh parent delegation mặc định và đối chiếu với một loader cố tình đi ngược chính sách.

Tạo tệp **`SharedApi.java`**:

```java
public final class SharedApi {
    public SharedApi() {
    }

    public String message() {
        return "hello";
    }
}
```

Tạo tệp **`ParentDelegationDemo.java`**:

```java
import java.io.IOException;
import java.io.InputStream;

public class ParentDelegationDemo {
    static final class SelectiveChildFirstLoader extends ClassLoader {
        private final String nameToIsolate;

        SelectiveChildFirstLoader(ClassLoader parent, String nameToIsolate) {
            super(parent);
            this.nameToIsolate = nameToIsolate;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!name.equals(nameToIsolate)) {
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
            String resourceName = name.replace('.', '/') + ".class";

            try (InputStream stream =
                    getParent().getResourceAsStream(resourceName)) {
                if (stream == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] bytes = stream.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException error) {
                throw new ClassNotFoundException(name, error);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader appLoader =
                ParentDelegationDemo.class.getClassLoader();

        ClassLoader defaultChild = new ClassLoader(appLoader) {
        };
        Class<?> delegated = defaultChild.loadClass("SharedApi");

        System.out.println("Parent-first shares class: "
                + (delegated == SharedApi.class));

        ClassLoader isolatedLoader =
                new SelectiveChildFirstLoader(appLoader, "SharedApi");
        Class<?> isolated = isolatedLoader.loadClass("SharedApi");

        System.out.println("Same binary name: "
                + isolated.getName().equals(SharedApi.class.getName()));
        System.out.println("Same runtime class: "
                + (isolated == SharedApi.class));
        System.out.println("Same defining loader: "
                + (isolated.getClassLoader()
                        == SharedApi.class.getClassLoader()));

        Object object = isolated.getDeclaredConstructor().newInstance();
        System.out.println("Can cast to parent's SharedApi: "
                + (object instanceof SharedApi));

        try {
            SharedApi converted = (SharedApi) object;
            System.out.println(converted.message());
        } catch (ClassCastException expected) {
            System.out.println("Cast result: ClassCastException");
        }

        System.out.println("String uses bootstrap loader: "
                + (String.class.getClassLoader() == null));
    }
}
```

Biên dịch và chạy bằng JDK 17+ từ thư mục chứa hai tệp:

```bash
javac --release 17 -d out SharedApi.java ParentDelegationDemo.java
java -cp out ParentDelegationDemo
```

Kết quả dự kiến với runtime Java chuẩn:

```text
Parent-first shares class: true
Same binary name: true
Same runtime class: false
Same defining loader: false
Can cast to parent's SharedApi: false
Cast result: ClassCastException
String uses bootstrap loader: true
```

**Tại sao có kết quả này?**

1. `defaultChild` là loader con chỉ kế thừa `ClassLoader.loadClass`. Khi nó yêu cầu `SharedApi`, application loader đã có thể tìm class file trên `out` và cung cấp cùng runtime class với `SharedApi.class`, do đó phép so sánh đầu trả `true`.
2. `SelectiveChildFirstLoader` được viết chỉ để **ưu tiên định nghĩa riêng đúng tên `SharedApi`**. Nó lấy bytecode `SharedApi.class` làm dữ liệu, gọi `defineClass` và tạo một `Class<?>` mới có **defining loader khác**.
3. `isolated.getName()` và `SharedApi.class.getName()` giống nhau, nhưng hai đối tượng `Class` khác nhau. Đối tượng được tạo bằng reflection từ bản isolated không phải instance của kiểu `SharedApi` phía application loader.
4. Vì vậy `instanceof` trả `false` và cast ném `ClassCastException`. Đây là điều xảy ra **ngay cả khi bytecode của hai bản lớp là cùng một tệp**.
5. `java.lang.String` vẫn có defining loader bootstrap; loader con không tự định nghĩa lại `String`.

**Cảnh báo về phạm vi ví dụ:** Đây là loader dùng để minh họa khác biệt danh tính kiểu. Nó cố tình phá thứ tự parent-first đối với đúng một tên, không phải trình quản lý plugin hoàn chỉnh. Việc lấy byte từ `getParent().getResourceAsStream(...)` chỉ đơn giản hóa cách lấy mẫu bytecode; trong plugin thật phải quản lý nguồn bytecode, lớp API chung, package sealing, quyền truy cập, dependency và vòng đời loader.

### Quan sát bằng Class#getClassLoader()

Từ ví dụ:

```java
ClassLoader definingLoader = isolated.getClassLoader();
ClassLoader parentDefiningLoader = SharedApi.class.getClassLoader();
boolean sameDefinition = isolated == SharedApi.class;
```

Đây là **đoạn minh họa dùng các biến đã khai báo trong `main`**, không phải chương trình độc lập. Nó nhấn mạnh rằng `getClassLoader()` trả loader *định nghĩa* class, trong khi một loader khác có thể là *initiating loader* do đã ủy quyền tìm thành công. Không kết luận “child định nghĩa lớp” chỉ vì code gọi `child.loadClass(...)`.

## 5. Parent-first mang lại lợi ích gì?

| Mục tiêu | Parent delegation hỗ trợ như thế nào? | Giới hạn |
|---|---|---|
| **Chung danh tính kiểu nền tảng** | Child dùng `String`, `Object`, `Throwable`... do runtime nền tảng định nghĩa | Không xác minh mọi logic sử dụng kiểu đó |
| **Chung API giữa app và plugin** | Cùng interface, DTO hoặc contract từ loader cha | Cần bố trí shared API ở parent thực sự |
| **Tránh định nghĩa trùng lặp** | Child không cần tải/định nghĩa một class mà parent đã nạp được | Parent có thể cung cấp phiên bản khác phiên bản plugin mong đợi |
| **Giảm rủi ro spoofing lớp cốt lõi** | Loader bình thường ưu tiên lớp JDK từ parent/bootstrap | Đây không phải sandbox toàn diện; class loader tùy biến có thể thay chiến lược trong phần phạm vi được phép |
| **Đơn giản hóa dependency chung** | Nhiều child dùng chung core library và class identity | Không tự động giải quyết dependency version conflict hoặc module readability |
| **Duy trì một bản static state của shared class** | Các child nhìn cùng runtime class từ parent và static fields của nó | Có thể tạo coupling hoặc xung đột cấu hình dùng chung |

Có một **đánh đổi về isolation**: khi parent đã cung cấp tên `com.lib.JsonParser` phiên bản A, child có thể không được dùng phiên bản B có cùng tên trên đường parent-first của nó. Đây là lý do một số plugin container, application server hoặc module framework thiết kế namespace/loader policy khác cho phần implementation và thư viện riêng.

## 6. Parent-first không có nghĩa “tất cả class đều nạp bởi parent”

Cần phân biệt ba tình huống:

### A. Parent có lớp

```text
child.loadClass("shared.api.Plugin")
    -> parent.loadClass(...)
    -> thành công
    -> child nhận lại chính Class<?> từ parent
```

Trong một kiến trúc plugin, đây là kết quả mong đợi với `shared.api.Plugin`.

### B. Parent không có lớp; child có lớp

```text
child.loadClass("plugins.alpha.AlphaPlugin")
    -> parent: ClassNotFoundException
    -> child.findClass(...)
    -> child.defineClass(...)
    -> child định nghĩa plugin implementation
```

Đây cũng là parent delegation đúng chuẩn: loader con chỉ tìm sau khi parent bỏ lỡ.

### C. Parent có lớp nhưng child muốn bản riêng

```text
child-first cho một namespace cụ thể:
    -> child.findClass(...)
    -> child.defineClass(...)
    -> có khả năng khác runtime type với lớp cùng tên ở parent
```

Chính sách này có thể phù hợp với plugin isolation, nhưng phải **loại trừ các API xuyên biên giới** khỏi vùng nạp riêng. Nếu interface mà app và plugin giao tiếp bị định nghĩa hai lần, hai bên có thể không ép kiểu qua lại.

**Quy tắc thiết kế phổ biến:** Đặt `java.*` và shared contracts dưới parent delegation; dùng child-specific namespace cho phần triển khai riêng. Một loader chuyên dụng có thể còn có chính sách chi tiết hơn, nhưng không nên sao chép tất cả dependency sang child theo kiểu thiếu kiểm soát.

## 7. Class identity và loader constraints sâu hơn một bước

Một JVM class không được nhận diện chỉ bằng tên của file, URL nguồn hay source code. Nó được định nghĩa bởi *binary name* cùng *defining loader*. Vì vậy:

```text
Loader P defines shared.api.Event
Loader A delegates shared.api.Event to P
Loader B delegates shared.api.Event to P

A's Event == B's Event == P's Event
```

Ngược lại:

```text
Loader A defines shared.api.Event
Loader B defines shared.api.Event

A's Event != B's Event
```

Khi classes từ những loader khác nhau giao tiếp qua method/field descriptors, JVM có các **loading constraints** để giữ nhất quán kiểu trong những ngữ cảnh tương ứng. Xung đột có thể dẫn đến `LinkageError`, trong khi thao tác ép kiểu không hợp lệ ở runtime có thể dẫn đến `ClassCastException`.

Ví dụ thực tế: `com.example.User cannot be cast to com.example.User` nghe vô lý nếu chỉ nhìn tên lớp, nhưng có thể chính xác khi hai `User` có defining loaders khác nhau. Một số stack trace JDK hiện đại còn ghi module/loader liên quan, giúp phát hiện nguyên nhân.

Cũng cần phân biệt:

- `ClassNotFoundException` thường được API tra cứu tường minh như `loadClass` hoặc `Class.forName` báo khi không tìm thấy lớp.
- `NoClassDefFoundError` thường xuất hiện trong lúc JVM cần liên kết/sử dụng một lớp mà định nghĩa không còn giải quyết được; nó cũng có thể xảy ra khi class từng **khởi tạo thất bại**.
- `ClassCastException` có thể do hai runtime types không tương thích, kể cả khi cùng tên.
- `LinkageError` còn có thể phát sinh do loader constraints, duplicate definition, version hoặc các lỗi liên kết khác.

Không kết luận tất cả lỗi trên đều do “parent delegation sai”. Hãy tìm class name, *defining loader*, classpath/module path và phiên bản thực tế.

## 8. Vì sao parent nhìn không thấy lớp con?

Mối quan hệ **ủy quyền từ child lên parent**, không mặc nhiên cho phép parent tự tìm thấy class của child.

```text
Parent loader: chứa shared.api.Plugin
       ^
       | parent-first delegation
       |
Child loader: chứa plugins.alpha.AlphaPlugin
```

Child có thể load interface từ parent. Nhưng nếu parent gọi `parent.loadClass("plugins.alpha.AlphaPlugin")`, nó không tự động duyệt danh sách mọi child loader để tìm lớp này.

Cách giao tiếp là **child tạo instance của implementation rồi đưa ra như shared interface**, hoặc hệ thống chủ động giữ một registry/loader reference để tìm plugin. Không nên kỳ vọng Java tự có “reverse delegation”.

### Thread context class loader (TCCL)

Một số SPI/framework phải tìm provider do child chứa ngay cả khi code tra cứu chạy trong thư viện do parent định nghĩa. Khi đó, **thread context class loader** có thể đóng vai trò chỉ dẫn:

```java
ClassLoader loader = Thread.currentThread().getContextClassLoader();
Class<?> provider = loader.loadClass("plugins.alpha.AlphaPlugin");
```

Đây là **đoạn minh họa** đòi hỏi loader phù hợp đã được đặt vào thread; `getContextClassLoader()` có thể trả `null` và `loadClass` có thể ném `ClassNotFoundException`. Ở ứng dụng thực tế cần xử lý hai trường hợp đó.

TCCL không tự thay đổi cách `ClassLoader.loadClass` phân giải mọi class. Nó chỉ là một tham chiếu loader mà code/framework có thể chủ động sử dụng; ví dụ `ServiceLoader` có các cách chọn loader phù hợp để tìm provider. Đặc biệt trong thread pool, phải khôi phục context loader sau tác vụ nếu đã tạm thay đổi nó, tránh rò rỉ ngữ cảnh plugin giữa các request.

## 9. Phân biệt parent delegation với những khái niệm dễ nhầm

### Classpath và module path

**Classpath** quyết định nguồn có thể tìm lớp; **parent delegation** quyết định loader nào được hỏi và theo thứ tự nào. Có file `.class` trong JAR của child không có nghĩa child luôn định nghĩa lớp: nếu parent đã có class cùng tên, mặc định parent thắng.

Trong **JPMS** (Java 9+), module readability, exports/opens và module-to-loader mapping đặt thêm giới hạn/quan hệ. Một loader có thể nạp lớp nhưng code vẫn không được truy cập một package do quy tắc module/access. Không đồng nhất cơ chế parent delegation với module access control.

### Loading, linking và initialization

- `loadClass("pkg.Type")` nhận binary name và trả `Class<?>` sau quá trình tìm/nạp thích hợp, có thể kèm resolve nếu đường gọi yêu cầu.
- `Class.forName("pkg.Type", false, loader)` cho phép lấy `Class<?>` mà không yêu cầu khởi tạo.
- `Class.forName("pkg.Type")` thường yêu cầu initialization.
- `String.class.getClassLoader() == null` là cách quan sát defining loader bootstrap, **không phải** bằng chứng về thứ tự gọi các API nạp lớp.

### Class và resource

`ClassLoader.getResource("pkg/Type.class")` tìm **tài nguyên** với dấu `/`, còn `loadClass("pkg.Type")` nạp **lớp** bằng binary name. Cả hai có thể được implement/delegate theo chính sách riêng; tìm thấy một file resource không nhất thiết đồng nghĩa class được định nghĩa bởi loader truy vấn đó. Không dựa chỉ vào đường dẫn `.class` để khẳng định identity.

### “Parent-first = bảo mật đầy đủ”

Parent-first làm khó việc thay thế ngẫu nhiên các class quan trọng được parent tìm thấy và việc định nghĩa package `java.*` còn bị Java runtime hạn chế. Tuy vậy, **class loader không phải sandbox an ninh chung**: nó không thay thế kiểm soát quyền truy cập file, network, process, native code hoặc đánh giá độ tin cậy của plugin.

## 10. Những lỗi thiết kế thường gặp

| Lỗi | Hậu quả | Cách xử lý |
|---|---|---|
| Đóng gói shared API vào mỗi plugin rồi child-first cho API đó | `instanceof`/cast sai, loader constraint conflicts | Định nghĩa shared API ở parent; plugin delegate namespace API |
| Dùng `child.loadClass` rồi tưởng child luôn định nghĩa class | Chẩn đoán sai loader sở hữu class | Xem `clazz.getClassLoader()` và so sánh `Class<?>` |
| Override toàn bộ `loadClass` để child-first mọi tên | Đụng core/third-party/shared types | Chỉ áp dụng child-first cho namespace riêng thực sự cần |
| Đổi binary name thành `pkg/Type.class` khi gọi `loadClass` | `ClassNotFoundException` | Dùng `pkg.Type`; path chỉ dành cho resource lookup |
| Giả định parent có thể nạp implementation chỉ nằm ở child | Plugin không được tìm thấy | Dùng loader của plugin, registry hoặc context loader phù hợp |
| Giả định cùng FQCN thì ép kiểu được | `ClassCastException` hoặc `LinkageError` | Kiểm tra defining loaders và nơi đặt shared contracts |
| Cho rằng parent-first tự giải quyết mọi khác biệt version | Plugin dùng sai API/`NoSuchMethodError` | Thiết kế classpath và vùng dependency isolation đúng |
| Giữ tham chiếu đến child loader, `Class`, thread hoặc object mãi mãi | Loader khó được thu gom khi plugin unload | Giải phóng registry, task, thread/TCCL và resource theo lifecycle |

Một trường hợp quan trọng là **static singleton**. Nếu cùng một class bị định nghĩa riêng trong hai loader, mỗi runtime class có các static fields riêng. Khi class được chia sẻ qua parent, các child cùng truy cập trạng thái static của **cùng một runtime class**. Điều này có thể là mục tiêu (shared registry) hoặc rủi ro (coupling và khó cô lập cấu hình); cần chọn phạm vi loader chủ động.

## 11. Cách chẩn đoán sự cố class loading

Khi gặp `ClassCastException` khó hiểu hoặc xung đột dependency, thực hiện theo thứ tự:

1. **Lấy binary name của cả hai kiểu** từ exception hoặc code liên quan.
2. **In defining loader**: `someObject.getClass().getClassLoader()`, `ExpectedApi.class.getClassLoader()`, kể cả khi một bên là `null` (bootstrap).
3. **So sánh `Class<?>` trực tiếp**: `obj.getClass() == ExpectedApi.class` khi điều kiện có ý nghĩa; hoặc `ExpectedApi.class.isAssignableFrom(obj.getClass())` khi muốn kiểm tra quan hệ subtype.
4. **Xem parent chain** và chính sách override `loadClass`, `findClass`, `getResource`. Parent-first mặc định khác custom child-first.
5. **Kiểm tra placement của API/dependency**: shared contract nằm trong app/platform parent hay bị sao chép vào các plugin?
6. **Xác minh nguồn class thực tế** bằng cách kiểm tra code source/resource khi có thể; lưu ý đường URL không thay thế thông tin defining loader.
7. **Xem bối cảnh module/service discovery** nếu dùng JPMS, `ServiceLoader` hoặc thread context class loader.
8. **Sửa classpath và loader policy có chủ đích**. Tránh giải quyết bằng cách đổi tên class, bắt bỏ `ClassCastException` hoặc ép reflection truy cập ngoài contract.

Đoạn hỗ trợ chẩn đoán có thể đặt trong phương thức Java:

```java
static void describe(Class<?> type) {
    System.out.println("Name: " + type.getName());
    System.out.println("Defining loader: " + type.getClassLoader());
}
```

Đây là method có thể dùng trong một class, **không phải chương trình độc lập**. Kết quả dòng loader phụ thuộc JVM, deployment và cách loader override `toString()`, nên không có stdout cố định cho ví dụ này.

## 12. Tự kiểm tra kiến thức

1. **Child gọi `loadClass("java.lang.String")` có nhất thiết tự định nghĩa String?** Không. Trong trường hợp chuẩn, class nền tảng được tìm theo delegation và cùng định nghĩa từ bootstrap.
2. **Parent-first có nghĩa child không bao giờ định nghĩa class?** Không. Nếu parent không tìm được, child có thể `findClass`/`defineClass`.
3. **Hai `com.acme.User` bytecode giống hệt nhưng khác defining loader có là cùng kiểu không?** Không. Danh tính runtime khác nhau nên phép cast có thể thất bại.
4. **Tại sao shared interface phải nằm ở loader chung?** Để ứng dụng và plugin dùng cùng runtime type làm contract, nhờ đó giá trị từ plugin có thể gán cho interface mà ứng dụng đã biết.
5. **Khi nào child-first có thể có ích?** Khi plugin cần phiên bản dependency riêng mà parent không nên áp đặt; phải có quy tắc bảo vệ shared API.
6. **Parent có thể tự động tìm mọi class chỉ nằm trong child loader?** Không; cần loader reference, registry, service mechanism hoặc context loader.
7. **Gọi `child.loadClass("pkg.Type")` rồi thấy `clazz.getClassLoader() == parent` là lỗi?** Không, đó có thể chính là parent delegation hoạt động đúng.
8. **Parent delegation có thay thế được quyền truy cập module và bảo mật ứng dụng?** Không. Đó là các cơ chế độc lập với những mục tiêu riêng.

## 13. Nguồn tham khảo chính thức

- Java SE 17 API, `ClassLoader` và hành vi mặc định của `loadClass`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java Virtual Machine Specification, `5.3 “Creation of Classes and Interfaces”` (defining/initiating loaders, danh tính lớp): https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- Java Virtual Machine Specification, `5.3.4 “Loading Constraints”`: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3.4
- Java SE 17 API, `Thread.getContextClassLoader`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Thread.html#getContextClassLoader()

**Ghi nhớ:** Parent delegation mặc định ưu tiên tìm lớp ở loader cha; nhờ vậy nhiều child loader có thể dùng **cùng các kiểu nền tảng và contract chung**. Khi cùng binary name bị định nghĩa bởi các loader khác nhau, chúng là các runtime types khác nhau. Đặt shared API ở loader cha, tách implementation trong child và chỉ dùng chính sách child-first khi thực sự cần vùng phụ thuộc riêng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="parent-delegation-002">Trình tự mặc định: findLoadedClass, loader cha và findClass</a>

<details>
<summary>Xem chi tiết</summary>

# Trình tự mặc định: findLoadedClass, loader cha và findClass

## 1. Câu hỏi cốt lõi: khi yêu cầu nạp một lớp, ClassLoader tìm ở đâu trước?

Trong Java, lệnh yêu cầu nạp một lớp, chẳng hạn `loader.loadClass("example.Plugin")`, **không mặc nhiên đọc ngay tệp `Plugin.class` từ nơi loader đó quản lý**. Với triển khai mặc định của `java.lang.ClassLoader.loadClass(String, boolean)`, JVM/loader thực hiện trình tự:

1. **`findLoadedClass(name)`**: kiểm tra lớp đã được ghi nhận là đã nạp với loader đang được hỏi hay chưa.
2. **Ủy quyền cho loader cha (parent)**: nếu bước 1 chưa tìm được, hỏi parent trước. Nếu `parent == null`, dùng bộ nạp bootstrap tích hợp trong JVM.
3. **`findClass(name)`**: chỉ khi bên cha/ bootstrap không tìm thấy thì loader hiện tại mới thử tự tìm và định nghĩa lớp.
4. **`resolveClass(c)` nếu `resolve == true`**: yêu cầu liên kết lớp đã tìm được; trả đối tượng `Class<?>`.

Đây là **parent-first delegation** (ưu tiên loader cha), hay còn gọi là delegation model mặc định.

```text
loadClass("example.Plugin", resolve)
│
└─ synchronized(getClassLoadingLock(name))
   │
   ├─ 1. findLoadedClass(name)
   │    └─ tìm thấy? ── Có ───────────────┐
   │                                     │
   ├─ 2. parent.loadClass(name)          │
   │       hoặc bootstrap khi parent=null│
   │    └─ tìm thấy? ── Có ───────────────┤
   │                                     │
   ├─ 3. findClass(name)                 │
   │    ├─ tìm thấy → define/load ────────┤
   │    └─ không thấy → ClassNotFoundException
   │                                     │
   └─────────────────────────────────────┘
                                         │
                               resolve? ─┤
                                  Có → resolveClass(c)
                                  Không → bỏ qua bước này
                                         │
                                      return c
```

**Chốt ý:** thứ tự ba bước tìm lớp là **đã nạp → cha → tự tìm**. `findClass` không phải bước đầu tiên của mô hình mặc định. Thứ tự này là **hành vi của triển khai mặc định**; ClassLoader tùy chỉnh có thể override `loadClass` và thay đổi chính sách, nên không áp quy tắc parent-first tuyệt đối cho mọi class loader.

## 2. Từ API đến luồng thực thi

Hai overload của `ClassLoader` có vai trò khác nhau:

```java
public Class<?> loadClass(String name)
        throws ClassNotFoundException

protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException
```

- `loadClass(String name)` là API công khai; đối với triển khai `ClassLoader` thông thường, nó gọi `loadClass(name, false)`.
- `loadClass(String name, boolean resolve)` là API được bảo vệ; **triển khai mặc định** chứa thuật toán kiểm tra cache/ủy quyền/tự tìm/resolve.
- `name` là **binary name**, ví dụ `com.acme.PluginService`, không phải đường dẫn `com/acme/PluginService.class` hoặc tên tệp JAR.
- Cờ `resolve` chỉ điều khiển việc gọi `resolveClass` sau khi có lớp; **không có nghĩa** phải khởi tạo lớp hay gọi static initializer.

Mã giả sau minh họa **logic** của triển khai mặc định trong JDK hiện đại. `bootstrapLookup` bên dưới là tên giả để giải thích, **không phải phương thức API công khai**:

```java
// PSEUDOCODE — không phải định nghĩa ClassLoader dùng để biên dịch
protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
        Class<?> found = findLoadedClass(name);

        if (found == null) {
            try {
                found = (parent != null)
                        ? parent.loadClass(name)
                        : bootstrapLookup(name);
            } catch (ClassNotFoundException notFoundInParent) {
                // Cha không tìm được; thử chính loader hiện tại.
            }

            if (found == null) {
                found = findClass(name);
            }
        }

        if (resolve) {
            resolveClass(found);
        }
        return found;
    }
}
```

Đối với loader không override `loadClass`, việc đồng bộ qua `getClassLoadingLock(name)` bảo vệ trình tự kiểm tra/tạo lớp trước các yêu cầu đồng thời. `getClassLoadingLock` có thể trả về khóa chung theo instance loader hoặc khóa riêng theo tên class nếu loader đã đăng ký parallel capable; không giả định mọi loader chỉ dùng một monitor duy nhất.

**Cần đọc luồng đúng:** `findLoadedClass` là kiểm tra trạng thái đã ghi nhận; parent delegation là một yêu cầu nạp khác đi lên chuỗi loader; `findClass` là điểm mở rộng để **tự định nghĩa** lớp từ nguồn riêng. Không có lời gọi tự động `findClass` sau khi parent đã trả kết quả thành công.

## 3. Bước 1 — findLoadedClass: lớp này đã từng được nạp chưa?

Khai báo API:

```java
protected final Class<?> findLoadedClass(String name)
```

Phương thức trả về:

- `Class<?>` nếu JVM đã ghi nhận loader hiện tại là **initiating loader** của class với binary name đó.
- `null` nếu chưa có bản ghi phù hợp.

Điểm dễ nhầm là **initiating loader** và **defining loader**:

- **Defining loader**: loader đã thực sự gọi `defineClass` (hoặc thực hiện cơ chế định nghĩa tương đương) để tạo runtime class.
- **Initiating loader**: loader được JVM ghi nhận là đã khởi xướng việc nạp class theo tên đó. Nó có thể **ủy quyền thành công** cho defining loader khác.

Vì vậy `findLoadedClass(name) != null` **không chứng minh loader hiện tại đã đọc bytes hoặc tự định nghĩa class**; lớp đó có thể do parent hoặc bootstrap định nghĩa. `Class<?>.getClassLoader()` cho biết defining loader trong trường hợp thông thường (với bootstrap được biểu diễn bằng `null`).

### Vì sao kiểm tra trước?

- Tránh lặp công việc tìm class và tạo định nghĩa mới cho những lần gọi sau.
- Bảo đảm các lần nạp thành công cùng binary name qua cùng initiating loader trở lại đối tượng `Class<?>` đã được ghi nhận, không định nghĩa thêm một class trùng danh tính.
- Hạn chế lỗi `LinkageError` dạng thử `defineClass` trùng binary name trong cùng defining loader.

Ví dụ, nếu plugin loader đã định nghĩa `plugin.Greeter` một lần, gọi `loadClass("plugin.Greeter")` lần nữa theo trình tự mặc định sẽ tìm lại bản đã nạp ở bước 1. Lần thứ hai **không gọi `findClass`** cho tên đó và không cần đọc lại file bytecode.

**Lưu ý:** `findLoadedClass` kiểm tra lớp đã ghi nhận, không hỏi xem source `.class` mới được sửa trên ổ đĩa hay không. Với cùng runtime class, thay file bytecode sau lần load **không tự cập nhật** class đã định nghĩa. Hot reload thực sự cần cơ chế phù hợp (chẳng hạn runtime class mới qua defining loader mới hoặc instrumentation theo giới hạn JVM), không phải chỉ gọi `loadClass` lần nữa.

## 4. Bước 2 — ủy quyền lên loader cha

Nếu chưa tìm thấy ở bước 1, ClassLoader mặc định gọi `parent.loadClass(name)` trước. Mỗi parent lại áp dụng chính sách của chính nó; với chuỗi parent-first, yêu cầu thường đi từ loader con lên các loader cao hơn cho đến khi một loader tìm thấy class.

```text
CustomPluginLoader
       │ hỏi parent trước
       ▼
Application ClassLoader
       │ hỏi parent trước
       ▼
Platform ClassLoader
       │ cơ chế truy cập parent/boot
       ▼
Bootstrap / built-in JVM loader
       │
       └─ nếu không ai tìm được:
          quay lại thử findClass ở loader phù hợp
```

Sơ đồ là một **mô hình khái niệm**. Trên JDK 9+, loader mặc định và hệ module có các chi tiết tìm kiếm riêng; không nên giả định classpath và module graph luôn biểu diễn bằng một chuỗi đường dẫn vật lý thuần túy. Với bộ nạp ứng dụng/tùy chỉnh kế thừa triển khai mặc định, ý quan trọng vẫn là **ưu tiên parent trước khi loader con định nghĩa class mới**.

### Vì sao ưu tiên cha?

1. **Tái sử dụng định nghĩa chung:** ví dụ một plugin tham chiếu `java.lang.String` phải dùng cùng định nghĩa runtime chuẩn thay vì tự dựng một bản khác.
2. **Giữ danh tính kiểu nhất quán** cho những thư viện/API chung giữa chương trình chính và plugin khi parent cung cấp chúng.
3. **Giảm vô tình shadow** một class mà parent đã tìm được: nếu cả parent và child cùng có file `com.acme.Api.class`, mặc định parent thắng.
4. **Giúp phân tách trách nhiệm:** parent cung cấp nền tảng/thư viện chia sẻ; child tìm class riêng khi parent không có.

Đây là quy tắc tìm kiếm, **không phải** lời bảo đảm tuyệt đối chống code độc hại hoặc xung đột phiên bản. ClassLoader tùy chỉnh có thể đổi chính sách; JVM còn có các quy tắc về package/module, truy cập, package sealing, bytecode và kiểu runtime.

### Nếu parent không tìm được thì điều gì xảy ra?

Với parent khác `null`, mặc định một `ClassNotFoundException` báo không tìm thấy từ parent được dùng làm tín hiệu để tiếp tục thử `findClass` của child. Nếu parent **tìm thấy**, child trả luôn class đó. Nếu parent gây lỗi khác như `LinkageError` hoặc `SecurityException`, không được suy ra rằng triển khai mặc định sẽ nuốt mọi lỗi và âm thầm tìm ở child: fallback bình thường dành cho trường hợp **không tìm thấy**.

Nếu `parent == null`, ClassLoader mặc định dùng cơ chế bootstrap tích hợp của máy ảo để thử trước, **không dùng `ClassLoader.getSystemClassLoader()` làm parent thay thế**.

## 5. Bước 3 — findClass: loader hiện tại tự tìm bytecode

Khai báo API:

```java
protected Class<?> findClass(String name)
        throws ClassNotFoundException
```

Đây là **điểm mở rộng chính** khi viết một custom loader giữ nguyên parent-first. Triển khai `ClassLoader` gốc của `findClass(String)` chỉ ném `ClassNotFoundException`; muốn tự đọc class từ directory, ZIP/JAR, network, mã đã sinh hoặc kho dữ liệu riêng, subclass cần override phương thức này.

Các bước trong một `findClass` tùy chỉnh thường là:

1. Chuyển binary name thành vị trí nguồn tương ứng, ví dụ `plugin.Greeter` → `plugin/Greeter.class`.
2. Đọc **đúng bytes** của class file.
3. Gọi `defineClass(name, bytes, 0, bytes.length)` để JVM định nghĩa runtime class với tên tương ứng.
4. Trả `Class<?>` nhận được; nếu không tìm được dữ liệu, ném `ClassNotFoundException` (có thể kèm nguyên nhân từ I/O).

`defineClass` chỉ định nghĩa lớp từ bytes; nó **không đồng nghĩa chạy `<clinit>`** của lớp đó. Các ràng buộc bytecode, module/package, quyền truy cập và binary compatibility vẫn do JVM thực thi.

### Vì sao thường override findClass thay vì loadClass?

Khi chỉ override `findClass` và kế thừa `loadClass`, ta có ngay:

- Thứ tự parent-first đúng triển khai mặc định.
- Kiểm tra `findLoadedClass` trước, tránh nạp trùng.
- Khóa đồng bộ `getClassLoadingLock(name)` trong quá trình loading.
- Gọi `resolveClass` khi protected overload được yêu cầu với `resolve=true`.
- Quy tắc lỗi và fallback đã được Java quy định.

Override `loadClass` chỉ phù hợp khi **thật sự cần chính sách khác** (chẳng hạn child-first cho nhóm namespace plugin) và khi đó người viết phải chủ động xử lý việc kiểm tra đã nạp, bảo vệ lớp nền tảng, khóa đồng bộ, fallback và việc resolve.

## 6. Ví dụ chạy được 1: parent thắng trước findClass

Ví dụ ngắn sau giúp quan sát fallback mà không phải tự tạo tệp bytecode.

Tạo file `ParentFirstDemo.java`:

```java
public class ParentFirstDemo {
    static final class TraceLoader extends ClassLoader {
        TraceLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            System.out.println("findClass: " + name);
            throw new ClassNotFoundException(name);
        }
    }

    public static void main(String[] args) throws Exception {
        TraceLoader loader = new TraceLoader(
                ParentFirstDemo.class.getClassLoader()
        );

        Class<?> core = loader.loadClass("java.lang.String");
        System.out.println("String defined by bootstrap: "
                + (core.getClassLoader() == null));

        try {
            loader.loadClass("my.example.DoesNotExist");
        } catch (ClassNotFoundException ex) {
            System.out.println("No such class");
        }
    }
}
```

Chạy với JDK 17+:

```bash
javac ParentFirstDemo.java
java ParentFirstDemo
```

Stdout dự kiến chính xác trên JVM Java SE chuẩn:

```text
String defined by bootstrap: true
findClass: my.example.DoesNotExist
No such class
```

**Diễn giải:**

- Với `java.lang.String`, parent/chuỗi loader nền tảng tìm được class. `TraceLoader.findClass("java.lang.String")` **không chạy**, nên không có dòng `findClass: java.lang.String`.
- Với `my.example.DoesNotExist`, parent không tìm thấy; thuật toán mới gọi `TraceLoader.findClass`, và ta nhìn thấy thông báo trước khi `ClassNotFoundException` được chuyển về chương trình.
- `String.class.getClassLoader() == null` được JVM dùng để biểu diễn lớp do bootstrap định nghĩa. Giá trị `null` ở đây **không có nghĩa** `String` thiếu loader hoặc nạp thất bại.
- Trong ví dụ này, `findClass` cố ý không định nghĩa lớp nào; ví dụ tiếp theo sẽ tải bytes thật.

## 7. Ví dụ chạy được 2: tự định nghĩa class, rồi dùng lại từ findLoadedClass

Ta tạo một class plugin **không nằm trên classpath của chương trình chính** và cho loader tùy chỉnh tìm từ directory riêng. Đây là tình huống tiêu biểu của parent-first: parent không có plugin nên child gọi `findClass`.

### 7.1. File plugin

Trong một thư mục làm việc trống, tạo file `Greeter.java`:

```java
package plugin;

public final class Greeter {
    static {
        System.out.println("Greeter initialized");
    }

    public static String greet() {
        return "Hello from plugin";
    }
}
```

### 7.2. File chương trình host

Cũng trong thư mục làm việc đó, tạo `PluginHost.java`:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PluginHost {
    static final class DirectoryLoader extends ClassLoader {
        private final Path root;

        DirectoryLoader(Path root, ClassLoader parent) {
            super(parent);
            this.root = root;
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            Path file = root.resolve(
                    name.replace('.', '/') + ".class"
            );
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(file);
            } catch (IOException ex) {
                throw new ClassNotFoundException(name, ex);
            }

            System.out.println("findClass: " + name);
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    public static void main(String[] args) throws Exception {
        DirectoryLoader loader = new DirectoryLoader(
                Path.of("plugin-classes"),
                PluginHost.class.getClassLoader()
        );

        Class<?> first = loader.loadClass("plugin.Greeter");
        System.out.println("defined by child: "
                + (first.getClassLoader() == loader));

        Class<?> second = loader.loadClass("plugin.Greeter");
        System.out.println("same Class object: " + (first == second));

        System.out.println("before first active use");
        Object result = first.getMethod("greet").invoke(null);
        System.out.println(result);
    }
}
```

### 7.3. Biên dịch và chạy

Tạo thư mục kết quả riêng và biên dịch:

```bash
mkdir plugin-classes
javac -d plugin-classes Greeter.java
javac PluginHost.java
java -cp . PluginHost
```

Sau bước build, hai phần tách biệt như sau:

```text
working-directory/
├── Greeter.java
├── PluginHost.java
├── PluginHost.class
├── PluginHost$DirectoryLoader.class
└── plugin-classes/
    └── plugin/
        └── Greeter.class
```

**Quan trọng:** Khi chạy host, classpath là `.`; **không** thêm `plugin-classes` vào classpath host. Nếu thêm đường dẫn này vào classpath và parent có thể tìm `plugin.Greeter`, parent-first sẽ trả class của parent, và `DirectoryLoader.findClass` có thể không chạy.

Stdout chính xác với cấu trúc build ở trên:

```text
findClass: plugin.Greeter
defined by child: true
same Class object: true
before first active use
Greeter initialized
Hello from plugin
```

### 7.4. Vì sao các dòng xuất hiện đúng thứ tự này?

1. `PluginHost.class` do application loader nạp. `DirectoryLoader` nhận loader của host làm parent.
2. Lần gọi `loader.loadClass("plugin.Greeter")`: bước `findLoadedClass` chưa có class; parent tìm không thấy vì `plugin-classes` không trên classpath; `findClass` đọc bytes và gọi `defineClass`.
3. Kết quả `first.getClassLoader() == loader` là `true` vì **DirectoryLoader đã định nghĩa** class plugin.
4. Lần `loadClass` thứ hai trả lại cùng `Class<?>`. Không in `findClass` lần nữa: class này đã được JVM ghi nhận với loader đó.
5. `loadClass(name)` chưa bắt buộc chạy static block. Dòng `Greeter initialized` chỉ in khi reflection `Method.invoke` gọi `greet()`, làm JVM yêu cầu **initialization** của Greeter. Sau đó mới có chuỗi trả về.

Ví dụ chứng minh cả ba khía cạnh: **parent-first fallback**, **caching qua `findLoadedClass`**, và sự khác nhau giữa **load** với **initialize**.

Trong mã sản xuất, một loader lấy tên class từ input ngoài phải kiểm soát namespace, đường dẫn, mã nguồn, quyền truy cập, package/module và nguồn bytecode; ví dụ học tập chỉ minh họa cách nạp từ directory đã tin cậy.

## 8. Phân biệt findLoadedClass, findClass và defineClass

| API | Nhiệm vụ | Trả về / lỗi | Điểm cần nhớ |
|---|---|---|---|
| `findLoadedClass(name)` | Tra lớp đã ghi nhận với initiating loader | `Class<?>` hoặc `null` | Không đọc lại bytecode, không tự gọi parent |
| `parent.loadClass(name)` | Nhờ loader cha tìm lớp | `Class<?>` hoặc lỗi | Ưu tiên trước tìm cục bộ trong parent-first |
| `findClass(name)` | Hook tìm/định nghĩa lớp từ nguồn của child | `Class<?>` hoặc `ClassNotFoundException` | Chỉ được gọi trong thuật toán mặc định khi cha không tìm thấy |
| `defineClass(...)` | Định nghĩa runtime class từ bytecode | `Class<?>` hoặc lỗi định dạng/liên kết | Không tự thực thi initializer của class |
| `resolveClass(c)` | Yêu cầu liên kết runtime class | `void` | Không phải `Class.forName(..., true, ...)` và không đồng nghĩa initialization |
| `loadClass(name)` | Gọi luồng tìm class công khai | `Class<?>` hoặc lỗi | Tương đương yêu cầu `loadClass(name, false)` của API |

Một chi tiết hay bị bỏ sót: `findLoadedClass` là `final`; bạn **không override** nó để thay cache. Thông thường cũng **không gọi `findClass` trực tiếp từ code ứng dụng**, vì cách đó bỏ qua chuỗi parent-first và cơ chế khóa/tra class đã nạp của `loadClass`.

## 9. Nếu có cùng tên class ở parent và child?

Giả sử có hai nguồn cùng chứa binary name `com.example.Engine`:

```text
Application ClassLoader:
  com/example/Engine.class    (version A)

Custom Child ClassLoader:
  com/example/Engine.class    (version B)
```

Với `loadClass` mặc định, child luôn hỏi parent trước. Nếu parent tìm được `com.example.Engine`, child nhận **version A**, dù thư mục của child có **version B**. `findClass` của child không được gọi cho tên đó.

Đây là **parent-first shadowing**: parent giữ quyền ưu tiên tên. Khi làm plugin, cần thiết kế rõ namespace nào là API dùng chung và namespace nào riêng cho plugin. Nếu thực sự cần child-first trong một tập package, phải viết chính sách nạp có điều kiện và bảo đảm không vô tình nạp lại lớp nền tảng/chung một cách gây `ClassCastException`, `LinkageError` hoặc phá type identity.

**Danh tính runtime của class không chỉ là chuỗi tên:** hai class cùng binary name được định nghĩa bởi hai defining loaders khác nhau là hai runtime classes khác nhau. Parent-first giúp những class dùng chung được định nghĩa từ một parent, nhưng plugin class riêng do child định nghĩa vẫn có danh tính gắn với child.

Đừng sửa xung đột này bằng cách thay bytecode file rồi gọi `loadClass` lặp lại: sau lần nạp thành công, nhánh `findLoadedClass` ưu tiên kết quả đã ghi nhận.

## 10. Thứ tự tìm không đồng nghĩa thứ tự khởi tạo

`ClassLoader.loadClass("package.Type")` giúp lấy `Class<?>` và có thể liên kết lớp theo chính sách JVM, nhưng **không tự yêu cầu thực thi `Type.<clinit>`**.

Ví dụ với `Greeter` ở mục 7:

```java
Class<?> type = loader.loadClass("plugin.Greeter");
// Chưa yêu cầu chạy static initializer của Greeter.

Object greeting = type.getMethod("greet").invoke(null);
// Gọi static method ⇒ yêu cầu initialize Greeter.
```

Mã trên là **trích đoạn** từ ví dụ đầy đủ chứ không phải tệp Java độc lập.

Ngược lại:

```java
Class<?> type = Class.forName(
        "plugin.Greeter",
        true,
        loader
);
```

Với `initialize=true`, `Class.forName` yêu cầu khởi tạo class được chỉ định nếu chưa được khởi tạo. `Class.forName(name, false, loader)` không yêu cầu điều đó. Không nên dùng thông báo từ `static { System.out.println(...) }` để suy ra chính xác lúc nào `findLoadedClass`, verification hoặc resolution chạy; log static chỉ chứng minh **initialization** đã xảy ra.

Cờ `resolve=true` của protected `loadClass(name, resolve)` cũng **không phải** cờ `initialize`. `resolveClass` liên kết lớp, còn JVM có thể trì hoãn một số resolution của symbolic references đến lúc thực sự dùng, theo quy tắc đặc tả.

## 11. Phân biệt lỗi “không tìm thấy” với lỗi định nghĩa/liên kết

Khi thử nạp lớp theo parent-first, không phải lỗi nào cũng nói lên rằng bước `findClass` nên tiếp tục:

| Triệu chứng | Ý nghĩa thường gặp | Hành động kiểm tra |
|---|---|---|
| `ClassNotFoundException` | Parent và child không tìm được binary name qua đường nạp hiện tại | Tên nhị phân, classpath, thư mục plugin, nguồn loader |
| `NoClassDefFoundError` | JVM cần một class ở liên kết/thực thi nhưng định nghĩa thiếu hoặc lớp không thể khởi tạo | Cause, dependency, class bị lỗi init trước đó |
| `UnsupportedClassVersionError` | Bytecode yêu cầu JVM hỗ trợ major/minor mới hơn | `javap -verbose`, JDK runtime thực tế |
| `ClassFormatError` / `VerifyError` | Dữ liệu class file hoặc bytecode không hợp lệ | File nguồn, transformer, compiler |
| `LinkageError` do định nghĩa trùng | Có đường nạp tùy chỉnh gọi `defineClass` nhiều lần cho cùng class trong một loader | Tìm nơi override `loadClass`, đồng bộ, duplicate define |
| `ClassCastException` dù hai tên class giống nhau | Hai runtime classes cùng tên nhưng defining loaders khác nhau | Kiểm tra `getClassLoader()` hai phía |
| `SecurityException` | Vi phạm kiểm tra quyền, package/module hoặc quy tắc định nghĩa nhất định | Chính sách JVM, namespace, source bytecode |

**Điểm chốt của fallback:** mặc định **chỉ chuyển từ parent sang `findClass` khi parent không tìm được class**. `ClassNotFoundException` là dấu hiệu điển hình. Một `LinkageError` hoặc lỗi do cấu trúc bytecode của parent có thể đi thẳng ra ngoài; không hiểu đó là lý do để loader con thử “phiên bản khác”.

Nếu `findClass` đọc một đường dẫn không tồn tại, nên chuyển `IOException` thích hợp thành `ClassNotFoundException(name, cause)` để giữ nguyên ngữ nghĩa tìm kiếm. Nếu bytes có sẵn nhưng không phải class hợp lệ, việc định nghĩa có thể ném lỗi khác; không che mất nguyên nhân đó bằng một thông báo “file not found” chung.

## 12. Đồng bộ và yêu cầu nạp cùng tên từ nhiều thread

Đoạn `synchronized(getClassLoadingLock(name))` trong triển khai mặc định tồn tại vì hai thread có thể cùng gọi `loadClass("plugin.Greeter")`. Nếu cả hai cùng thấy chưa nạp rồi đồng thời gọi `defineClass` cùng binary name trên cùng loader, ít nhất một đường có thể thất bại vì định nghĩa trùng hoặc tạo trạng thái không mong muốn.

Mặc định, việc tìm loaded class, ủy quyền, fallback và `resolveClass` được đặt dưới khóa mà `getClassLoadingLock` cung cấp. Với class loader **parallel capable** đã được đăng ký thích hợp, khóa có thể là theo tên class để nạp những tên độc lập đồng thời; nếu không, thường đồng bộ trên đối tượng loader.

Khi custom loader override `loadClass`, người viết cần tự xem lại các quy tắc đồng bộ và delegation. Dùng tùy tiện nhiều loader gọi lẫn nhau còn có thể dẫn đến deadlock; không nên chỉ “bỏ synchronized để nhanh hơn”.

**Không nhầm hai lần nạp:**

- Nhiều thread cùng yêu cầu **cùng binary name + cùng loader**: cần phối hợp để không định nghĩa trùng.
- Hai **defining loaders khác nhau** định nghĩa cùng binary name: là hai runtime classes khác nhau và có thể hợp lệ cho mô hình plugin, nhưng không hoán đổi được như cùng kiểu Java.

## 13. Một số giới hạn của mô hình mặc định

- **ClassLoader tùy chỉnh có thể override `loadClass`:** những framework/container cần isolation hoặc child-first có thể chọn chính sách khác. Muốn biết thực sự dùng thứ tự nào, kiểm tra implementation của loader cụ thể.
- **Module-aware loading:** JDK 9+ có `findClass(String moduleName, String name)` và những loader phục vụ named modules; cách tìm class của loader xây dựng sẵn có thể xét module graph, không chỉ đường dẫn tệp đơn giản. Bài học đang mô tả phương thức `loadClass(String, boolean)` mặc định của `java.lang.ClassLoader`.
- **Class resource khác Class loading:** `getResource`/`getResources` có quy tắc tìm tài nguyên liên quan nhưng không suy diễn bytecode class chắc chắn được định nghĩa bằng việc tìm thấy một URL tài nguyên cùng tên.
- **Có bytecode không có nghĩa có quyền định nghĩa:** `defineClass` có thể bị từ chối bởi định dạng class, package name, module/package sealing và những quy tắc bảo vệ khác.
- **Không nhầm namespace của app với bootstrap:** `getClassLoader()==null` ở class nền tảng Java là cách biểu diễn bootstrap defining loader, không phải một lỗi giá trị null cần khắc phục.

## 14. Quy trình debug lớp được lấy từ loader nào

Khi một plugin “đang dùng nhầm class” hoặc loader con không đọc file plugin của nó, hãy làm theo thứ tự:

1. Ghi nhận **binary name** được đưa vào `loadClass` và **object loader** nào nhận yêu cầu đầu tiên.
2. Kiểm tra class đã được loader đó **initiated/loaded** trước đó chưa; nếu đã có, lần gọi sau trả class cũ thay vì đọc file mới.
3. Kiểm tra **parent**: `loader.getParent()` và chain loader của class caller, JVM/module path/classpath thực tế. Nếu parent có thể tìm tên đó, `findClass` của child có thể không chạy.
4. Nếu parent không tìm thấy, kiểm tra `findClass`: cách map tên thành đường dẫn, file bytes, `defineClass` và các lỗi bị bắt/chuyển đổi.
5. Với class trả về, log `type.getName()` và `type.getClassLoader()`. Đây là cách xác định tên và **defining loader**; không dùng mỗi tên class để chứng minh hai đối tượng `Class<?>` có cùng danh tính.
6. Nếu có lỗi ở runtime sau khi nạp xong, xác định đó là **linking**, **initialization** hay lỗi logic chương trình trước khi sửa thứ tự class loading.

Ví dụ lệnh chẩn đoán JDK trên JVM HotSpot hỗ trợ unified logging:

```bash
java -Xlog:class+load=info -cp . PluginHost
```

Log có thể rất dài và định dạng thay đổi theo JDK/VM. Nó giúp quan sát class được **nạp** từ đâu, nhưng không được coi là log bảo đảm mọi thao tác `findLoadedClass`, `findClass` hoặc static initializer đều sẽ được thể hiện bằng một dòng tương ứng.

## 15. Tự kiểm tra

1. **Đã từng gọi `loadClass("x.A")` thành công qua cùng loader, lần sau mặc định kiểm tra gì đầu tiên?** `findLoadedClass("x.A")`; nếu đã ghi nhận, trả class đó.
2. **Child có bản `x.A` riêng nhưng parent cũng tìm được `x.A`, mặc định dùng bản nào?** Bản parent đã nạp/trả về.
3. **Khi nào `findClass` được gọi?** Khi chưa tìm thấy ở `findLoadedClass` và parent/bootstrap không tìm được tên đó.
4. **Nếu không override `findClass` mà parent cũng không tìm thấy thì sao?** Triển khai `ClassLoader.findClass` mặc định ném `ClassNotFoundException`.
5. **`findLoadedClass(name) != null` chứng minh loader đó là defining loader không?** Không; initiating loader có thể ủy quyền cho loader khác định nghĩa.
6. **`loadClass(name)` có chạy static initializer không?** Không bắt buộc; bản public tương đương `loadClass(name, false)`, không yêu cầu khởi tạo.
7. **`resolve=true` có tương đương `initialize=true` của `Class.forName` không?** Không; `resolveClass` liên kết, còn initialization là bước khác.
8. **Tại sao lần gọi `loadClass("plugin.Greeter")` thứ hai không in `findClass` trong ví dụ?** `findLoadedClass` đã tìm được class do loader đó định nghĩa ở lần trước.
9. **Nếu parent ném `LinkageError` thay vì `ClassNotFoundException`, child có chắc được gọi `findClass` không?** Không; fallback của triển khai mặc định là đường không tìm thấy class.
10. **Muốn custom loader parent-first và đọc bytes từ thư mục riêng, nên override gì?** `findClass(String)`; kế thừa `loadClass` để dùng luồng kiểm tra cache, ủy quyền và khóa có sẵn.

## Tài liệu tham khảo chuẩn

- [Java SE 21 API — `java.lang.ClassLoader`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): mô tả thứ tự mặc định của `loadClass(String, boolean)`, `findLoadedClass`, `findClass`, `defineClass` và `resolveClass`.
- [Java SE 26 API — `java.lang.ClassLoader`](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/ClassLoader.html): hợp đồng API hiện đại, bao gồm các khác biệt liên quan module và parallel-capable loader.
- [Java Virtual Machine Specification — §5.3 Creation of Classes and Interfaces](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3): ý nghĩa defining/initiating loader và class identity.
- [Java Language Specification — §§12.2–12.4](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html): loading, linking và initialization là các bước có ý nghĩa khác nhau.

**Ghi nhớ trình tự mặc định:** `findLoadedClass` → **parent** (hoặc bootstrap khi không có parent) → `findClass` → `resolveClass` nếu được yêu cầu → trả `Class<?>`. Khi viết loader tùy chỉnh tuân theo mô hình ủy quyền, override **`findClass`** là điểm bắt đầu hợp lý nhất.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="parent-delegation-003">Vai trò của parent null khi ủy quyền tới bootstrap</a>

<details>
<summary>Xem chi tiết</summary>

# Vai trò của parent null khi ủy quyền tới bootstrap class loader

## 1. Vấn đề cần hiểu

Khi tự tạo `ClassLoader` trong Java, có thể truyền `null` làm parent:

```java
ClassLoader loader = new ClassLoader(null) { };
```

Nhiều người đọc đoạn này và kết luận “loader không có parent nên không ủy quyền cho ai”. Kết luận đó **sai đối với cơ chế mặc định của `java.lang.ClassLoader`**.

Trong hợp đồng của `ClassLoader`, **parent bằng `null` là cách API biểu diễn việc ủy quyền tới bootstrap class loader**. Bootstrap là tầng nạp những lớp lõi của JVM; nó không được biểu diễn trực tiếp bằng một instance `ClassLoader` thông thường. Vì vậy:

- `new ClassLoader(null) { }` tạo một **custom loader object mới**.
- `loader.getParent() == null` báo không có **parent object Java**; khi dùng thuật toán `loadClass` mặc định, nó sẽ thử dùng **bootstrap class loader** cho bước delegation.
- Nó **không biến loader mới thành bootstrap class loader**, không làm bootstrap bị vô hiệu, và cũng không trao cho loader mới khả năng tìm các class ứng dụng trên class path.
- Sau khi bootstrap không tìm được class, loader mới có thể tự tìm class qua `findClass` nếu đã được triển khai; bản `findClass` kế thừa từ `ClassLoader` mặc định ném `ClassNotFoundException`.

Điểm phân biệt trọng tâm: `null` là **ký hiệu đặc biệt trong API Java**, còn bootstrap class loader là **cơ chế runtime có thật** để nạp lớp nền tảng.

## 2. Parent là gì và tại sao bootstrap được biểu diễn bằng null?

Một class loader có thể yêu cầu một loader cấp trên thử tìm class trước, theo **parent delegation**. Cơ chế này giúp các class ở nhiều loader khác nhau dùng chung các loại nền tảng, chẳng hạn `java.lang.Object` và `java.lang.String`.

Trong JDK 17 có ba tầng built-in thường gặp:

```text
Bootstrap class loader
    (không có đối tượng ClassLoader Java công khai)
                   ^
                   |  getParent() == null ở loader dưới
                   |
Platform class loader
                   ^
                   |
Application / system class loader
                   ^
                   |
Custom loader (nếu chọn app làm parent)
```

Ví dụ thông thường:

```java
ClassLoader platform = ClassLoader.getPlatformClassLoader();
ClassLoader system = ClassLoader.getSystemClassLoader();

System.out.println(platform.getParent() == null);
System.out.println(system.getParent() == platform);
```

Trong cấu hình mặc định của OpenJDK/HotSpot 17, hai dòng thường in `true`, `true`. Các chi tiết về system loader có thể được cấu hình hoặc thay đổi; không suy ra chain thực tế của mọi runtime chỉ từ sơ đồ.

**Tại sao không tạo `BootstrapClassLoader extends ClassLoader`?** Ở các JVM thông dụng, bootstrap loader được thực hiện ở tầng JVM/native và không được expose như một instance `ClassLoader` thông thường. Vì thế API dùng `null` ở những chỗ như `getClassLoader()` và `getParent()` để đại diện cho tầng đó.

Không nên nhầm **quan hệ parent trong cơ chế nạp lớp** với `extends` của Java: hai class loader object không cần kế thừa nhau để một loader được đặt làm parent của loader khác.

## 3. Thuật toán `ClassLoader.loadClass` mặc định khi parent null

Với `ClassLoader.loadClass(String name, boolean resolve)` mặc định, cơ chế chính có thể diễn đạt như sau:

1. Kiểm tra class đã được loader hiện tại ghi nhận là loaded hay chưa, bằng `findLoadedClass(name)`.
2. Nếu chưa, thử nhờ **parent** load class. Nếu parent bằng `null`, thực hiện nhánh nạp thông qua **bootstrap class loader**.
3. Nếu parent/bootstrap không tìm được class, gọi `findClass(name)` của loader hiện tại để nó có cơ hội tự định nghĩa class.
4. Nếu được yêu cầu `resolve=true`, thực hiện resolution theo API; trả về `Class<?>` đã tìm thấy. Nếu tất cả bước đều thất bại, ném `ClassNotFoundException`.

Mã giả sau chỉ minh họa cơ chế, **không phải một bản override có thể copy nguyên xi vào Java**:

```text
loadClass(name, resolve):
    c = findLoadedClass(name)

    if c == null:
        if parent != null:
            thử c = parent.loadClass(name)
        else:
            thử c = bootstrap.loadClass(name)  // mã giả:
                                               // không có biến API "bootstrap"
        nếu c vẫn == null:
            c = findClass(name)

    nếu resolve:
        resolveClass(c)
    return c
```

Trong triển khai Java thực tế, `loadClass` còn có quy tắc synchronization/parallel-capable loaders và các điều kiện nội bộ. Mã giả nhằm làm rõ **đường ủy quyền khi parent null**, không nhằm thay thế Java API documentation.

Một ví dụ để tự diễn giải:

```text
customLoader = new ClassLoader(null) { }

customLoader.loadClass("java.lang.String")
    -> chưa do customLoader tự nạp
    -> parent == null -> hỏi bootstrap
    -> bootstrap đã biết/định nghĩa String
    -> trả về String.class

customLoader.loadClass("com.example.App")
    -> chưa do customLoader tự nạp
    -> parent == null -> hỏi bootstrap
    -> bootstrap không tìm thấy app class này
    -> gọi customLoader.findClass("com.example.App")
    -> bản findClass mặc định không có cách tìm class này
    -> ClassNotFoundException
```

Ví dụ kết quả trên phụ thuộc giả định `com.example.App` là class ứng dụng không thuộc bootstrap và loader không được cấp logic `findClass` riêng. Nếu bạn triển khai `findClass` hoặc chọn parent khác, kết quả có thể thay đổi.

### Delegation có luôn tuyệt đối là parent-first?

Không. `ClassLoader.loadClass` cung cấp thuật toán parent-first mặc định. Một subclass có thể override `loadClass` để đưa ra chính sách lookup khác, miễn tuân thủ contract JVM/Java liên quan. Vì vậy parent `null` **không bảo đảm** mọi loader tùy biến đều đi theo đúng nhánh mã giả trên nếu chính nó ghi đè thuật toán nạp.

Tuy nhiên, với `new ClassLoader(null) { }` **không override** `loadClass` và `findClass`, hành vi nêu trên là điều cần hiểu.

## 4. Chương trình Java 17: parent null vẫn nạp được lớp bootstrap

Tạo file **`NullParentDemo.java`**:

```java
public class NullParentDemo {
    public static void main(String[] args)
            throws ClassNotFoundException {

        ClassLoader isolated = new ClassLoader(null) { };
        ClassLoader app = NullParentDemo.class.getClassLoader();
        ClassLoader appChild = new ClassLoader(app) { };

        System.out.println("Parent is null: "
                + (isolated.getParent() == null));

        Class<?> str = isolated.loadClass("java.lang.String");
        Class<?> obj = isolated.loadClass("java.lang.Object");

        System.out.println("Delegated String: "
                + (str == String.class));
        System.out.println("Delegated Object: "
                + (obj == Object.class));

        System.out.println("String defining loader is bootstrap: "
                + (str.getClassLoader() == null));

        try {
            isolated.loadClass("NullParentDemo");
            System.out.println("Application class found");
        } catch (ClassNotFoundException expected) {
            System.out.println("Application class not visible");
        }

        System.out.println("App-parent finds application class: "
                + (appChild.loadClass("NullParentDemo")
                        == NullParentDemo.class));
    }
}
```

Biên dịch và chạy với **JDK 17 từ class path thông thường**:

```bash
javac NullParentDemo.java
java NullParentDemo
```

Stdout đúng trong môi trường đã nêu:

```text
Parent is null: true
Delegated String: true
Delegated Object: true
String defining loader is bootstrap: true
Application class not visible
App-parent finds application class: true
```

Giải thích theo từng kết quả:

1. `isolated.getParent() == null` xác nhận loader mới có parent API bằng `null`.
2. `isolated.loadClass("java.lang.String")` và `loadClass("java.lang.Object")` thành công nhờ nhánh bootstrap của thuật toán mặc định.
3. `str == String.class` và `obj == Object.class` chứng tỏ loader nhận về **cùng định danh runtime class** với các loại lõi đã có; nó không tạo một bản `String` hoặc `Object` mới.
4. `str.getClassLoader() == null` biểu thị **defining class loader** là bootstrap. Đừng nhầm điều này với loader object `isolated` vốn đã **khởi xướng** lệnh `loadClass`.
5. `isolated.loadClass("NullParentDemo")` thất bại vì bootstrap không tìm class ứng dụng này, còn custom loader không có cách `findClass` bổ sung.
6. `appChild` dùng application loader làm parent nên có thể ủy quyền để tìm chính class ứng dụng; kết quả `Class<?>` bằng `NullParentDemo.class`.

Ví dụ phân biệt rõ hai tính chất:

- **Có thể gọi loader để yêu cầu nạp một class** không có nghĩa chính loader đó **định nghĩa** class được trả về.
- **Không thấy class ứng dụng** không có nghĩa bootstrap loader bị hỏng. Loader chỉ không có đúng đường tìm kiếm cho class được hỏi.

## 5. Bốn chỗ xuất hiện null dễ gây nhầm

Cùng là ký hiệu `null` nhưng ngữ cảnh API quyết định ý nghĩa.

| Biểu thức | Ý nghĩa cần hiểu |
|---|---|
| `new ClassLoader(null) { }` | Tạo custom loader có parent ở cấp API là bootstrap; đây vẫn là một object loader riêng. |
| `loader.getParent() == null` | Không có parent `ClassLoader` object được trả về; trong mô hình loader API, null biểu thị bootstrap. |
| `String.class.getClassLoader() == null` | Class `String` do bootstrap định nghĩa. Không suy ra String chưa load. |
| `Class.forName("java.lang.String", false, null)` | Truyền loader là null: dùng bootstrap loader để tìm/nạp kiểu; `false` không yêu cầu initialization. |

### Null ở `Class.getClassLoader()` không phải null ở `getParent()`

`Class.getClassLoader()` báo loader đã **định nghĩa kiểu runtime** được đại diện (đối với array, theo quy tắc loader của component). `ClassLoader.getParent()` báo parent của **một object loader cụ thể**.

Do vậy:

```java
ClassLoader custom = new ClassLoader(null) { };
Class<?> type = custom.loadClass("java.lang.String");
```

Ở đây:

- `custom` **không null**: một loader object mới đã được tạo.
- `custom.getParent()` **null**: nó delegating lên tầng bootstrap qua thuật toán mặc định.
- `type.getClassLoader()` **null**: định nghĩa `String` do bootstrap cung cấp.
- `custom == type.getClassLoader()` là `false`, vì bên trái là object còn bên phải là `null`.

Đây là cách đọc quan trọng khi debug class loader: phân biệt **initiating loader** (nơi yêu cầu bắt đầu/được ghi nhận) với **defining loader** (nơi thực sự định nghĩa class).

### Null trong `Class.forName`

Với overload ba tham số:

```java
Class<?> c = Class.forName("java.lang.String", false, null);
```

Tham số cuối `null` yêu cầu lookup qua bootstrap. Còn `false` là cờ **không yêu cầu initialize** class; điều đó khác với “không loading”.

Với class chỉ tồn tại ở application class path, chẳng hạn `NullParentDemo`, gọi `Class.forName("NullParentDemo", false, null)` thường ném `ClassNotFoundException`, vì bootstrap không có nghĩa vụ tra cứu class path ứng dụng.

## 6. Bootstrap loader và system loader khác nhau

Trong cấu hình JDK 17 thường gặp:

- **Bootstrap loader** đảm nhận các lớp nền tảng như `java.lang.Object` và `java.lang.String`; không có object `ClassLoader` công khai đại diện nó.
- **Platform loader** có thể lấy qua `ClassLoader.getPlatformClassLoader()`; nó nạp một phần lớp thuộc các platform modules của JDK.
- **System/application loader** lấy qua `ClassLoader.getSystemClassLoader()`; thường xử lý class path ứng dụng và module thuộc phạm vi nó được giao.

Giữa các loader built-in, **bootstrap đứng ở trên cùng** trong mô hình delegation. Nhưng đừng khái quát rằng mọi package bắt đầu `java.` luôn được bootstrap định nghĩa: các lớp JDK thuộc những module khác nhau có thể có defining loader khác nhau. Hãy kiểm tra class thực tế nếu muốn kết luận.

Bảng phân biệt:

| Cách cấu hình custom loader | Điều gì xảy ra với default `loadClass`? | Tình huống dùng |
|---|---|---|
| `new ClassLoader(null) { }` | Bootstrap trước; rồi `findClass` custom (nếu có). Không tự thấy mọi class ứng dụng. | Loader biệt lập cần chủ động định nghĩa class riêng và dùng chung lớp lõi. |
| `new ClassLoader(ClassLoader.getPlatformClassLoader()) { }` | Platform parent sẽ tham gia delegation, có thể tiếp tục tới bootstrap. | Muốn truy cập tập nền tảng qua platform mà không dùng application loader. |
| `new ClassLoader(ClassLoader.getSystemClassLoader()) { }` | Thường thấy các class mà system loader có thể cung cấp, rồi mới tới logic riêng. | Plugin hoặc loader bổ sung cần kế thừa visibility của ứng dụng. |
| `new ClassLoader() { }` | Constructor không tham số thường chọn system class loader làm parent theo API. | Loader custom thông thường dùng default parent. |

**Cẩn thận:** “thấy class” ở bảng là nói về **lookup/visibility theo loader**, không đảm bảo vượt được ràng buộc access, module exports/opens, package integrity hoặc các quy tắc JVM khác. Đặc biệt, cơ chế loader không phải sandbox bảo mật toàn diện.

## 7. Parent null không cô lập mọi thứ

Một loader parent-null có thể không nhìn thấy class ứng dụng khác theo cơ chế mặc định, nhưng vẫn sử dụng được `java.lang.Object`, `java.lang.String` và nhiều kiểu bootstrap khác. Điều đó là chủ đích của Java: mọi loại ứng dụng cần chia sẻ nền tảng runtime.

Hệ quả:

- **Không phải môi trường “không có Java core”.** Class được custom loader định nghĩa vẫn phải được kiểm tra/liên kết theo ngữ nghĩa JVM và có thể tham chiếu những kiểu lõi cần thiết.
- **Không tự động được load JAR.** `new ClassLoader(null) { }` không tự có cơ chế scan mọi thư mục/JAR; cần parent phù hợp hoặc định nghĩa cách tìm class như `URLClassLoader`/`findClass` khi có nhu cầu.
- **Không tạo không gian tên hoàn toàn độc lập.** Hai runtime classes có cùng binary name chỉ được xem là cùng kiểu nếu danh tính runtime phù hợp (đặc biệt defining loader); chia sẻ `Object`/`String` không biến các lớp ứng dụng do hai loader khác nhau định nghĩa thành một kiểu.
- **Không phải ranh giới an toàn để chạy mã không tin cậy.** Code chịu kiểm tra bytecode, access rules, module boundaries và các cơ chế kiểm soát khác; parent null không thay thế sandbox chuyên dụng.

Ví dụ tình huống plugin:

Ứng dụng host cung cấp interface `com.example.api.Plugin` từ loader ứng dụng; mỗi plugin được nạp bằng custom loader parent-null. Khi plugin có class `MyPlugin implements Plugin`, JVM phải resolve tên `com.example.api.Plugin` cho class plugin. Nếu loader parent-null không tìm interface API này, linking/lookup có thể thất bại dù bootstrap đã tìm được `java.lang.Object`.

Một cách thiết kế thường gặp là cho loader plugin **ủy quyền tới loader chứa shared API** hoặc cho phép lookup/shared package có kiểm soát. Cần đảm bảo cả host và plugin sử dụng **cùng một định nghĩa runtime** của interface `Plugin`. Nếu plugin tự định nghĩa một bản interface cùng tên dưới loader khác, sẽ phát sinh vấn đề tương thích kiểu.

Không nên đặt parent null chỉ vì muốn “plugin tách biệt” mà bỏ qua dependency visibility và runtime type identity.

## 8. Ví dụ bổ sung: xem tầng bootstrap và parent chain

Tạo file **`LoaderChainDemo.java`**:

```java
public class LoaderChainDemo {
    public static void main(String[] args) {
        ClassLoader platform = ClassLoader.getPlatformClassLoader();
        ClassLoader system = ClassLoader.getSystemClassLoader();

        System.out.println("Object bootstrap: "
                + (Object.class.getClassLoader() == null));
        System.out.println("String[] bootstrap: "
                + (String[].class.getClassLoader() == null));
        System.out.println("Platform parent bootstrap: "
                + (platform.getParent() == null));

        System.out.println("System parent platform: "
                + (system.getParent() == platform));
        System.out.println("Main defined by system: "
                + (LoaderChainDemo.class.getClassLoader() == system));
    }
}
```

Chạy từ class path mặc định bằng JDK 17 HotSpot/OpenJDK, không cấu hình system loader riêng:

```bash
javac LoaderChainDemo.java
java LoaderChainDemo
```

Stdout:

```text
Object bootstrap: true
String[] bootstrap: true
Platform parent bootstrap: true
System parent platform: true
Main defined by system: true
```

Vì `String` thuộc bootstrap, array class `String[]` cũng có defining loader tương ứng; `String[].class.getClassLoader()` trả `null`. Với array, JVM tạo kiểu mảng runtime theo quy tắc riêng; đây không phải là một class file `String[].class` nằm trong class path.

Ví dụ dùng `==` để kiểm tra quan hệ loader thay vì in `toString()` hoặc class name của loader, bởi tên implementation như `jdk.internal.loader.ClassLoaders$AppClassLoader` có thể thay đổi và không phải contract công khai ổn định.

**Giới hạn ví dụ:** custom system class loader, môi trường chạy module khác hoặc JVM implementation khác có thể làm một vài dòng kiểm tra tầng application trả kết quả khác. Bài học cần giữ nguyên là quy ước null cho bootstrap và ý nghĩa của delegation.

## 9. Parent null và quá trình loading, linking, initialization

`loadClass` tìm/lấy runtime class; nó không đồng nghĩa với việc đã tạo instance hay đã chạy static initializer.

Ba hoạt động khác nhau:

1. **Loading:** tìm/định nghĩa runtime class qua loader thích hợp.
2. **Linking:** verification, preparation và resolution theo quy tắc JVM.
3. **Initialization:** chạy `<clinit>` của class khi có active use thích hợp.

Lệnh:

```java
Class<?> type = isolated.loadClass("java.lang.String");
```

không tạo một đối tượng `String` mới. Nó lấy `Class<?>` tương ứng kiểu `String`.

Tương tự, `Class.forName("java.lang.String", false, null)` yêu cầu lookup qua bootstrap nhưng không yêu cầu initialization vì cờ là `false`. Đừng nhầm cờ initialization với tham số loader: chúng kiểm soát **hai chiều khác nhau**.

Nếu `findClass` của loader mới định nghĩa một class ứng dụng, JVM vẫn phải kiểm tra/liên kết/khởi tạo class theo các quy tắc riêng; việc parent null không làm bỏ qua verification hoặc cấp quyền truy cập đặc biệt.

## 10. Những lỗi hay gặp và cách sửa

### Lỗi: “parent null = không ủy quyền”

**Thực tế:** Default `ClassLoader.loadClass` ủy quyền qua bootstrap khi parent null.

**Cách kiểm tra:** `new ClassLoader(null) {}.loadClass("java.lang.String") == String.class` trong chương trình có cú pháp hợp lệ; xem ví dụ `NullParentDemo`.

### Lỗi: “`getParent() == null` tức custom loader chính là bootstrap”

**Thực tế:** `getParent()` trả quan hệ parent, không trả defining-loader identity của chính object loader. Custom loader vẫn là một instance `ClassLoader` riêng.

**Cách kiểm tra:** gọi `isolated.getClass()` hoặc so sánh `isolated != null`; nó vẫn là một object đang tồn tại, có phương thức `loadClass` riêng.

### Lỗi: “Tạo `ClassLoader(null)` là đủ để load class file của dự án”

**Thực tế:** Bản mặc định `findClass` ném `ClassNotFoundException`. Bootstrap không duyệt tùy ý class path của ứng dụng.

**Cách sửa:** chọn parent nhìn thấy lớp cần dùng, hoặc viết loader biết tìm/định nghĩa bytes từ nguồn thích hợp; bảo đảm mọi shared types dùng đúng runtime identity.

### Lỗi: “Không nạp được class ứng dụng là do bootstrap không hoạt động”

**Thực tế:** Bootstrap vốn được giao nạp tập lớp nền tảng của runtime. `ClassNotFoundException` khi yêu cầu `NullParentDemo` từ loader parent-null chưa triển khai finder là điều dự kiến.

**Cách kiểm tra:** tách câu hỏi “bootstrap tìm được `String` không?” khỏi “custom loader tìm được class ứng dụng không?”. Hai bước cho biết thành phần nào thiếu visibility.

### Lỗi: “Hai class trùng binary name ở hai loader chắc chắn tương thích”

**Thực tế:** JVM xác định runtime identity bằng **binary name + defining class loader**. Lỗi `ClassCastException` hoặc `LinkageError` có thể xảy ra khi các loader định nghĩa bản không tương thích.

**Cách sửa:** xác định loader sở hữu shared API, để plugin và host thống nhất cách resolve các kiểu dùng chung; không vô tình nhân bản thư viện API ở nhiều loader.

### Lỗi: “Parent-first là quy luật cưỡng chế cho mọi custom loader”

**Thực tế:** Nó là cơ chế mặc định; subclass có thể override `loadClass`. Việc tùy biến phải xét constraints của VM và tránh phá các kiểu dùng chung.

**Cách sửa:** đọc code `loadClass` thật của custom loader, không phỏng đoán chỉ từ `getParent()`. Nếu override, ghi rõ trường hợp nào ưu tiên parent, trường hợp nào ưu tiên child và cách bảo vệ package nền tảng.

### Lỗi: “`Class.getClassLoader() == null` nghĩa object Class bị null”

**Thực tế:** `String.class` là một `Class<?>` object có thật; chỉ thuộc tính defining loader được API biểu diễn bằng `null`. Đừng suy từ nullable loader sang việc class/object không tồn tại.

## 11. Cách lựa chọn parent trong thiết kế thực tế

Trước khi chọn `new ClassLoader(null)`, trả lời ba câu hỏi:

1. **Class loader này cần nhìn thấy những class nào?** Nếu cần shared API của ứng dụng, chọn một parent thích hợp (ví dụ loader định nghĩa shared API); parent null thường không đủ.
2. **Các kiểu nào phải có chung runtime identity?** Những interface/DTO truyền qua boundary giữa host và plugin nên được cung cấp nhất quán từ loader được chia sẻ.
3. **Các class riêng được tìm bằng cách nào?** Nếu không có finder phù hợp, loader mới chỉ nhận những class từ parent/bootstrap, không tự nạp một JAR mới.

Bảng ra quyết định:

| Mục tiêu | Thiết lập có thể phù hợp | Điểm cần kiểm tra |
|---|---|---|
| Dùng class lõi của JVM | Parent null + default delegation có thể đủ | Không đòi class ứng dụng từ bootstrap. |
| Plugin dùng shared API của host | Parent là loader nhìn thấy shared API | Shared API phải dùng cùng defining loader. |
| Nạp class từ JAR riêng | Custom loader biết tìm bytes/JAR, parent được chọn theo API cần chia sẻ | Tránh định nghĩa trùng các kiểu dùng chung. |
| Chỉ lấy metadata của class lõi | Dùng `Class.forName(name, false, null)` nếu tên thuộc bootstrap | Cờ `false` liên quan initialization; kiểm tra class name chính xác. |
| Điều tra `ClassNotFoundException` | Kiểm tra loader được gọi, parent chain, `findClass` và class/module path | Tìm failure ở tầng lookup thực tế. |

**Lưu ý kỹ thuật:** Nạp lớp bằng `URLClassLoader` có thể hữu ích trong một số ứng dụng Java truyền thống, nhưng với hệ module hoặc plugin loader phức tạp, cần xét module boundaries và lifecycle (ví dụ đóng tài nguyên của URLClassLoader khi phù hợp). Parent null không tự thiết kế hộ một module/plugin system an toàn và nhất quán.

## 12. Bài tập tự kiểm tra

**Câu 1.** Vì sao `new ClassLoader(null) { }` vẫn nạp được `java.lang.String`?

**Đáp án:** `loadClass` mặc định dùng bootstrap loader khi parent bằng `null`. Bootstrap cung cấp định nghĩa `String` của runtime; custom loader trả `Class<?>` đó.

**Câu 2.** Gọi `loader.getParent() == null` có chứng minh `loader` chính là bootstrap loader không?

**Đáp án:** Không. Nó chỉ phản ánh parent trong API của một loader object. Bootstrap không được biểu diễn bằng object `ClassLoader` thường như custom loader.

**Câu 3.** Tại sao `new ClassLoader(null) { }.loadClass("com.example.App")` thường thất bại với class ứng dụng trên class path?

**Đáp án:** Bootstrap không được giao tìm class ứng dụng ấy; custom loader không có finder nếu không override `findClass`. Đây là lỗi lookup/visibility theo loader, không phải bằng chứng bytecode của `App` sai.

**Câu 4.** `ClassLoader.getSystemClassLoader()` và bootstrap có giống nhau không?

**Đáp án:** Không. System loader là loader API xử lý lớp ứng dụng trong cấu hình phổ biến; bootstrap là tầng nền tảng biểu diễn bằng `null` ở các API liên quan.

**Câu 5.** Một custom loader parent-null gọi `loadClass("java.lang.Object")` thành công. Defining loader của `Object` là custom loader hay bootstrap?

**Đáp án:** Bootstrap. Custom loader khởi xướng lookup và có thể được xem là initiating loader; defining loader của `Object` là bootstrap.

**Câu 6.** Nếu override `loadClass`, parent-null có buộc loader hỏi bootstrap đầu tiên không?

**Đáp án:** Không thể khẳng định chỉ từ thuộc tính parent. Thuật toán mặc định parent-first làm như vậy; override có thể thay lookup order, phải kiểm tra code và các ràng buộc JVM.

**Câu 7.** Cần gọi một interface `com.example.api.Plugin` thuộc class path host từ plugin class loader. Nên kiểm tra gì trước khi chọn parent null?

**Đáp án:** Loader phải resolve được interface từ định nghĩa dùng chung để class plugin có thể liên kết và có runtime type identity tương thích với host. Parent null chỉ thấy nền tảng nếu không có finder/shared lookup bổ sung.

## 13. Ghi nhớ và tài liệu tham khảo

- **`parent == null` trong `ClassLoader`:** default delegation tới **bootstrap**, không phải chặn mọi delegation.
- **`getParent() == null`:** biểu diễn quan hệ loader; **`SomeClass.class.getClassLoader() == null`:** biểu diễn class do bootstrap định nghĩa.
- **`new ClassLoader(null) { }`:** tạo một custom loader object bình thường; nếu không có `findClass` custom thì không tự tải được class app không có ở bootstrap.
- **Có parent khác null:** default loader có thể thấy tập class mà parent nhìn thấy. Chọn parent dựa trên shared API và type identity.
- **Class identity:** binary name **cùng** defining loader; requesting loader có thể không phải defining loader.
- **Delegation ≠ initialization:** `loadClass` không đồng nghĩa chạy `<clinit>` hoặc tạo instance.

Đọc thêm theo tài liệu chính thức:

1. [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): constructor với parent, `getParent`, `loadClass`, `findClass`, platform/system loaders.
2. [Java SE 17 — Class](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `getClassLoader` và `forName`, biểu diễn bootstrap bằng null.
3. [JVM Specification 17 — Chapter 5](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): loading, linking, initialization, class loader identity và các ràng buộc liên kết.
4. [JEP 261 — Module System](https://openjdk.org/jeps/261): mô hình class loader built-in và các module runtime từ Java 9.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="parent-delegation-004">Quan hệ parent là quan hệ đối tượng, không phải kế thừa lớp Java</a>

<details>
<summary>Xem chi tiết</summary>

# Quan hệ parent là quan hệ đối tượng, không phải kế thừa lớp Java

Khi học cơ chế ủy quyền của **Java ClassLoader**, một nhầm lẫn rất dễ gặp là xem *parent class loader* như một lớp cha trong Java. Hai khái niệm này hoàn toàn khác nhau:

- **Parent của một class loader** là **một đối tượng `ClassLoader` khác** được tham chiếu để phục vụ cơ chế ủy quyền tìm lớp/tài nguyên. Có thể kiểm tra bằng `loader.getParent()`.
- **Superclass của một lớp Java** là **một kiểu Java** mà lớp đó khai báo kế thừa qua `extends` (hoặc kế thừa ngầm), được kiểm tra bằng `SomeLoader.class.getSuperclass()`.

Một đối tượng của lớp `PluginLoader` hoàn toàn có thể lấy **một đối tượng** của lớp `HostLoader` làm parent, **dù `PluginLoader` không kế thừa `HostLoader`**. Ngược lại, `ChildLoader extends BaseLoader` không tự động làm cho mọi instance của `BaseLoader` trở thành parent của instance `ChildLoader`.

Bài học này dùng thuật ngữ **quan hệ parent của loader instance** và **quan hệ kế thừa giữa Java classes** nhất quán, giải thích cách chúng ảnh hưởng đến `loadClass`, và đưa ra các chương trình Java 17+ có thể chạy để kiểm chứng.

## 1. Hai sơ đồ khác nhau

### 1.1. Cây kiểu Java (inheritance / type hierarchy)

```text
java.lang.Object
      │
      └── java.lang.ClassLoader
                 ├── HostLoader
                 └── PluginLoader
```

Ví dụ: `HostLoader extends ClassLoader` và `PluginLoader extends ClassLoader`. Hai lớp này cùng kế thừa `ClassLoader`; **không** có quan hệ `PluginLoader extends HostLoader`.

Sơ đồ trên mô tả:

- `PluginLoader` có thể thừa hưởng các API từ `ClassLoader` như `loadClass`, `getParent` và những phương thức `protected` liên quan.
- Một instance `PluginLoader` có kiểu `PluginLoader`, đồng thời là `ClassLoader` và `Object`.
- Tính hợp lệ của phép gán/ép kiểu trong Java phụ thuộc type hierarchy này.
- Thay đổi tham chiếu `parent` của loader **không thể thay đổi superclass** của Java class đang được định nghĩa.

### 1.2. Chuỗi các đối tượng class loader (delegation / object graph)

```text
pluginLoader (instance của PluginLoader)
      │ getParent()
      ▼
hostLoader   (instance của HostLoader)
      │ getParent()
      ▼
platformLoader (instance của built-in platform class loader)
      │ getParent()
      ▼
bootstrap loader (thường được biểu diễn bằng null trong Java)
```

Sơ đồ này mô tả những **đối tượng đang tồn tại tại runtime**. Kết nối `pluginLoader.getParent() == hostLoader` được thiết lập bằng constructor của `ClassLoader`, không bằng từ khóa `extends`.

Hai sơ đồ có thể tồn tại đồng thời nhưng trả lời **hai câu hỏi khác nhau**:

| Câu hỏi | Hỏi về | API/biểu thức |
| --- | --- | --- |
| "`PluginLoader` kế thừa lớp nào?" | Kiểu Java, lúc viết/biên dịch chương trình | `PluginLoader.class.getSuperclass()` |
| "Đối tượng `pluginLoader` ủy quyền cho loader nào?" | Quan hệ giữa các instance, lúc chạy | `pluginLoader.getParent()` |
| "Đối tượng `pluginLoader` thuộc kiểu nào?" | Runtime class của chính loader object | `pluginLoader.getClass()` |
| "`Class<?>` của `demo.Item` được loader nào định nghĩa?" | Định danh runtime của **lớp được nạp** | `itemClass.getClassLoader()` |
| "Một loader có thể tìm thấy lớp này không?" | Class lookup/delegation | `loader.loadClass("demo.Item")` |

**Đặc biệt tránh nhầm:** `ClassLoader.getParent()` gọi trên **loader object**, còn `Class.getSuperclass()` gọi trên **Class object**. Trùng từ "parent/cha" trong cách giải thích đời thường không làm chúng trở thành một quan hệ.

## 2. `extends` xác định lớp cha, còn constructor xác định parent object

Hãy so sánh ba đoạn mã:

```java
class HostLoader extends ClassLoader {
    HostLoader(ClassLoader parent) {
        super(parent);
    }
}

class PluginLoader extends ClassLoader {
    PluginLoader(ClassLoader parent) {
        super(parent);
    }
}
```

Đây là **các khai báo minh họa**; để chạy độc lập xem ví dụ đầy đủ ở phần 4. Chúng có hai dạng quan hệ:

1. `extends ClassLoader` xác định **superclass của kiểu** `HostLoader` và `PluginLoader`. Quyết định này nằm trong khai báo class, được compiler ghi vào class file và là một phần của cấu trúc kiểu Java.
2. `super(parent)` gọi **constructor của `ClassLoader`** và chuyển **tham chiếu đến một loader instance** làm parent cho instance đang được tạo. Không phải "chọn superclass" bằng tham số.

Ví dụ về cách tạo instance:

```java
ClassLoader platform = ClassLoader.getPlatformClassLoader();
HostLoader host = new HostLoader(platform);
PluginLoader plugin = new PluginLoader(host);
```

Kết quả về quan hệ:

```text
host.getParent()   == platform     → true
plugin.getParent() == host         → true

host.getClass().getSuperclass()    → ClassLoader.class
plugin.getClass().getSuperclass()  → ClassLoader.class
```

`plugin` không có kiểu `HostLoader`. **`HostLoader.class.isInstance(plugin)` trả `false`**, dù đối tượng `host` là parent của `plugin`.

Một constructor chứa tham số `ClassLoader parent` cho phép parent có **bất kỳ subclass `ClassLoader` thích hợp** nào — thường là built-in system/platform loader hoặc một loader tùy biến. Java kiểm tra kiểu tham số `ClassLoader`; nó không yêu cầu "instance parent có class là superclass của class con".

## 3. Ba constructor của `ClassLoader` và ý nghĩa tham số parent

Trên Java 17+, `java.lang.ClassLoader` có các constructor `protected` thường dùng:

| Constructor | Parent được chọn | Khi nào dùng |
| --- | --- | --- |
| `ClassLoader()` | `ClassLoader.getSystemClassLoader()` | Loader tùy biến dùng parent mặc định là system loader. |
| `ClassLoader(ClassLoader parent)` | Đúng đối tượng `parent` truyền vào | Cần điều khiển rõ chuỗi delegation. |
| `ClassLoader(String name, ClassLoader parent)` | Parent truyền vào; đồng thời đặt **tên loader** | Muốn đặt tên hỗ trợ chẩn đoán trên Java 9+. |

**`parent == null`** thường biểu diễn **bootstrap loader** làm parent trong cơ chế `ClassLoader` chuẩn, chứ không có nghĩa chọn một lớp cha Java là `null`. Từ Java 9 trở đi, không bảo đảm mọi platform class đều nhìn thấy qua bootstrap; nếu cần Java platform APIs rộng hơn, cân nhắc `ClassLoader.getPlatformClassLoader()`.

Một số chi tiết cần nhớ:

- `ClassLoader.getParent()` là phương thức **`final`** trong JDK API: subclass không override phương thức này để giả mạo parent được trả về.
- Không có API công khai `setParent(...)` để đổi parent tùy ý sau khi tạo loader. Hãy thiết kế quan hệ khi xây dựng instance.
- `ClassLoader` vẫn là **lớp có thể kế thừa**: chương trình viết một subclass rồi override `findClass` để hỗ trợ nguồn class bytes riêng.
- `ClassLoader(String name, ClassLoader parent)` đặt **tên của loader instance**; tên này không phải tên Java class và không phải binary name của lớp nó nạp.
- Một số JVM có security-manager checks trong API tạo/kiểm tra loader; điều này không làm thay đổi sự phân biệt giữa hai quan hệ.

### `super(parent)` không đồng nghĩa parent là superclass

Trong constructor:

```java
PluginLoader(ClassLoader parent) {
    super(parent);
}
```

`super(...)` về mặt **ngôn ngữ Java** luôn gọi một constructor của **superclass** `ClassLoader`, nên từ `super` ở đây là cú pháp kế thừa. Nhưng **đối số `parent`** chính là **loader object** sẽ được `ClassLoader` giữ để ủy quyền. Vì cùng dòng có cả hai khái niệm, nó rất dễ gây nhầm.

Đọc câu này đúng là: "Gọi constructor của Java superclass `ClassLoader`, truyền vào tham chiếu parent loader mà ta muốn instance mới sử dụng." Không phải: "biến đối tượng được truyền thành superclass của `PluginLoader`."

## 4. Thực hành A — hai lớp loader không kế thừa nhau vẫn tạo quan hệ parent

Tạo một tệp Java duy nhất:

```text
parent-object-lab/
└── src/
    └── demo/
        └── ParentVsInheritance.java
```

**`src/demo/ParentVsInheritance.java`:**

```java
package demo;

public class ParentVsInheritance {

    static final class HostLoader extends ClassLoader {
        HostLoader(ClassLoader parent) {
            super(parent);
        }
    }

    static final class PluginLoader extends ClassLoader {
        PluginLoader(ClassLoader parent) {
            super(parent);
        }
    }

    public static void main(String[] args) {
        ClassLoader platform = ClassLoader.getPlatformClassLoader();
        HostLoader host = new HostLoader(platform);
        PluginLoader plugin = new PluginLoader(host);

        System.out.println("plugin parent is host: " +
                (plugin.getParent() == host));
        System.out.println("host parent is platform: " +
                (host.getParent() == platform));
        System.out.println("plugin is HostLoader: " +
                HostLoader.class.isInstance(plugin));
        System.out.println("same Java superclass: " +
                (host.getClass().getSuperclass()
                        == plugin.getClass().getSuperclass()));
        System.out.println("superclass is ClassLoader: " +
                (PluginLoader.class.getSuperclass() == ClassLoader.class));
    }
}
```

Dùng JDK 17+ từ thư mục `parent-object-lab`:

```sh
javac --release 17 -d out src/demo/ParentVsInheritance.java
java -cp out demo.ParentVsInheritance
```

**Stdout chính xác:**

```text
plugin parent is host: true
host parent is platform: true
plugin is HostLoader: false
same Java superclass: true
superclass is ClassLoader: true
```

**Tại sao dòng thứ ba là `false`?** `plugin` là instance của `PluginLoader`, vốn chỉ `extends ClassLoader`. Quan hệ `plugin.getParent() == host` không tạo thêm quan hệ `PluginLoader extends HostLoader`.

**Tại sao dòng thứ tư là `true`?** Cả hai Java classes kế thừa trực tiếp `ClassLoader`. So sánh `getSuperclass()` so sánh các đối tượng `Class<?>` biểu diễn **kiểu cha**, chứ không so sánh loader parent.

**Vì sao dùng `HostLoader.class.isInstance(plugin)`?** Nó minh họa kiểm tra kiểu tại runtime an toàn cho hai lớp anh em. Không cần viết một phép cast bất hợp lệ từ `PluginLoader` sang `HostLoader`.

**Lưu ý:** Hai subclasses chưa override `findClass` nên chỉ giúp minh họa *parent object*, chưa tự tải bytecode đặc biệt. Khả năng ủy quyền vốn có được thừa hưởng từ `ClassLoader`.

## 5. Thực hành B — hai đối tượng cùng một Java class vẫn là parent và child

Một nhầm lẫn ngược lại là cho rằng phải có **hai Java subclasses khác nhau** để có quan hệ parent/child. Không cần: chỉ cần **hai instance** có liên kết phù hợp.

Tạo `src/demo/SameTypeChain.java`:

```java
package demo;

public class SameTypeChain {

    static final class NamedLoader extends ClassLoader {
        NamedLoader(String name, ClassLoader parent) {
            super(name, parent);
        }
    }

    public static void main(String[] args) {
        ClassLoader platform = ClassLoader.getPlatformClassLoader();

        NamedLoader first = new NamedLoader("first", platform);
        NamedLoader second = new NamedLoader("second", first);

        System.out.println("same class: " +
                (first.getClass() == second.getClass()));
        System.out.println("different objects: " + (first != second));
        System.out.println("second parent is first: " +
                (second.getParent() == first));
        System.out.println("first parent is platform: " +
                (first.getParent() == platform));
        System.out.println("names: " +
                first.getName() + ", " + second.getName());
    }
}
```

Từ thư mục thực hành chứa `src`:

```sh
javac --release 17 -d out src/demo/SameTypeChain.java
java -cp out demo.SameTypeChain
```

**Stdout chính xác:**

```text
same class: true
different objects: true
second parent is first: true
first parent is platform: true
names: first, second
```

`first` và `second` đều có Java class là `NamedLoader`; **không có khái niệm "`second` extends `first`"**, vì `extends` áp dụng cho kiểu chứ không áp dụng cho hai object.

Hai đối tượng cùng class cũng có thể là **hai sibling loaders** nếu cùng dùng platform làm parent. Quan hệ object parent được lựa chọn **cho từng instance**, không tự sinh từ tên class hay cách khai báo class.

Sự khác nhau này là thiết yếu khi một ứng dụng tạo một loader instance cho **mỗi plugin/version/tenant**. Một subclass loader duy nhất có thể tạo nhiều namespace độc lập vì mỗi instance định nghĩa các class khác nhau.

## 6. Parent được dùng thế nào trong cơ chế ủy quyền nạp lớp?

Đối với các subclass dùng triển khai mặc định của `ClassLoader.loadClass(String, boolean)`, hành vi tổng quát gồm ba bước:

1. Kiểm tra `findLoadedClass(name)` để xem lớp theo tên ấy đã được loader ghi nhận là loaded trước đó chưa.
2. Nếu chưa, thử `parent.loadClass(name)`; khi parent là `null`, cơ chế của VM được sử dụng để tra cứu qua bootstrap.
3. Nếu parent không tìm được, gọi `findClass(name)` của chính loader. Nếu tìm thấy và tham số `resolve` là `true`, yêu cầu liên kết bằng `resolveClass`.

Ví dụ luồng điển hình:

```text
plugin.loadClass("demo.Feature")
          │
          ├── findLoadedClass("demo.Feature") ?
          │       └── có → trả về Class<?> đã biết
          │
          └── plugin.getParent() là host
                     │
                     └── host.loadClass("demo.Feature")
                               ├── host/ancestor tìm thấy → trả về
                               └── không tìm thấy → thử
                                   plugin.findClass("demo.Feature")
                                               │
                                       tự định nghĩa hoặc
                                       ClassNotFoundException
```

**Điểm cốt lõi:** Việc tìm **parent để gọi `loadClass`** dựa vào **trường tham chiếu của loader object**, không dùng `getSuperclass()` để tìm Java class của parent. Mã triển khai inherited `loadClass` trong `ClassLoader` vẫn có thể gọi một **đối tượng** `HostLoader` làm parent của instance `PluginLoader`.

Quan hệ `extends` chỉ quyết định **mã Java được thừa hưởng/override**. Ví dụ subclass `PluginLoader` có thể override `findClass` để đọc bytecode từ plugin JAR, còn parent object `host` có thể là subclass hoàn toàn khác dùng cơ chế tra cứu riêng.

**Không đồng nhất:** "parent không tìm thấy" chỉ là kết quả lookup phù hợp để fallback `findClass`. Một lỗi `LinkageError`, `SecurityException` hoặc bytecode không hợp lệ không phải lúc nào cũng là `ClassNotFoundException` được phép bỏ qua rồi thử vô điều kiện.

### Parent-first là hành vi mặc định, không phải định nghĩa của `extends`

Triển khai mặc định của `ClassLoader` là **parent-first delegation**. Subclass có thể override `loadClass` để triển khai chiến lược khác, nhưng phải kiểm soát concurrency, type identity, package/module constraints, protected JDK classes và tính nhất quán với resource loading. Javadoc khuyến nghị subclass thường **override `findClass`** thay vì tự viết lại toàn bộ `loadClass`.

Trên JDK 9+, các built-in loaders còn có cơ chế tương tác với **JPMS**; một vài trường hợp upgraded module khiến việc tìm lớp không khớp với một sơ đồ parent chain tuyến tính tuyệt đối. Ví dụ trong bài nhằm giải thích **quan hệ object parent của custom `ClassLoader` sử dụng mặc định**, không giả định mọi class loader trong thế giới Java đều tuân thủ đúng một thuật toán.

## 7. Thực hành C — quan sát parent-first và `findClass` bằng log có kiểm soát

Tạo `src/demo/DelegationTrace.java`:

```java
package demo;

public class DelegationTrace {

    static final class TracingLoader extends ClassLoader {
        TracingLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            System.out.println("findClass called: " + name);
            throw new ClassNotFoundException(name);
        }
    }

    public static void main(String[] args)
            throws ClassNotFoundException {
        TracingLoader child =
                new TracingLoader(ClassLoader.getPlatformClassLoader());

        Class<?> stringClass = child.loadClass("java.lang.String");
        System.out.println("String reused: " +
                (stringClass == String.class));

        try {
            child.loadClass("demo.MissingForLesson");
        } catch (ClassNotFoundException expected) {
            System.out.println("missing class: " +
                    expected.getClass().getSimpleName());
        }
    }
}
```

Từ thư mục bài thực hành:

```sh
javac --release 17 -d out src/demo/DelegationTrace.java
java -cp out demo.DelegationTrace
```

**Stdout chính xác trên runtime Java 17+ thông thường:**

```text
String reused: true
findClass called: demo.MissingForLesson
missing class: ClassNotFoundException
```

Vì sao `findClass` **không in** khi nạp `java.lang.String`?

- `TracingLoader` dùng triển khai mặc định của `loadClass`.
- Parent được truyền là platform loader, vốn nhìn thấy `java.lang.String` qua bootstrap.
- Parent trả về lớp `String`. Loader con **không cần** gọi `findClass` cho tên này.
- `String.class.getClassLoader()` thường là `null`, do bootstrap định nghĩa; không có bản sao `String` tự định nghĩa trong `TracingLoader`.

Vì sao `findClass` **có in** cho `demo.MissingForLesson`?

- Không có class với tên ấy trên platform/ancestor trong cấu hình bài thực hành.
- Bước hỏi parent không tìm thấy, nên `loadClass` mặc định gọi `child.findClass(name)`.
- `findClass` chỉ in thông báo rồi ném `ClassNotFoundException`. Do đó đây là demo **quan sát fallback**, không phải custom bytecode loader hoàn chỉnh.

**Nội dung đã được JVM nạp ở bước nào?** Không thể suy ra một class được định nghĩa bởi loader nào chỉ vì có log "`findClass called`": `findClass` có thể thất bại. Khi thực sự nhận được `Class<?>`, kiểm tra `result.getClassLoader()` để xem **defining loader**.

## 8. Ba "cha/con" dễ bị lẫn trong một ứng dụng

Khi đọc code framework, thường có ít nhất ba quan hệ khác nhau:

### 8.1. Java class inheritance

```java
class BaseLoader extends ClassLoader { }

class SpecializedLoader extends BaseLoader { }
```

Đây là **hai class declarations minh họa**, không phải chương trình độc lập. Quan hệ kiểu là:

```text
SpecializedLoader.class.getSuperclass() == BaseLoader.class
```

Kế thừa cho phép `SpecializedLoader` tái sử dụng hoặc override hành vi của `BaseLoader`. **Không tự động tạo một object `BaseLoader` làm parent** khi tạo `SpecializedLoader`. Nếu constructor không gọi `super(parent)` theo thiết kế riêng và cuối cùng dùng constructor không tham số của `ClassLoader`, parent có thể là system loader mặc định.

### 8.2. Parent delegation giữa loader instances

```java
ClassLoader root = ClassLoader.getPlatformClassLoader();
ClassLoader child = new ClassLoader(root) { };
```

Đây là đoạn code hợp lệ có thể đặt trong `main`. `child.getParent() == root`, dù kiểu runtime của `child` là một anonymous subclass `ClassLoader`. Parent object là kết nối được chỉ định ở constructor, không phải superclass.

### 8.3. Java object được nạp và Java superclass của nó

Giả sử có class nghiệp vụ:

```java
class Animal { }
class Dog extends Animal { }
```

Việc `Dog.class.getSuperclass() == Animal.class` nói về **kế thừa của domain classes `Dog`/`Animal`**. Nó không cho biết `Dog.class.getClassLoader().getParent()` là gì.

Nói cách khác:

- `Dog.class.getSuperclass()` trả về `Class<Animal>`.
- `Dog.class.getClassLoader()` trả về **loader đã định nghĩa `Dog`**, hoặc `null` nếu bootstrap.
- `Dog.class.getClassLoader().getParent()` (khi loader khác `null`) trả về **parent của defining loader**, không phải loader định nghĩa `Animal` nhất thiết.

Hai class có quan hệ `extends` hoàn toàn có thể được **cùng một class loader** định nghĩa, hoặc được định nghĩa bởi các loaders khác nhau nhưng vẫn đáp ứng các quy tắc type safety và loader constraints. Đừng suy luận rằng "superclass của `Dog`" phải do "parent loader của loader nạp `Dog`" định nghĩa.

## 9. Định danh lớp runtime cũng phụ thuộc loader instance, không phụ thuộc subclass name

JVM xem một class/interface runtime được xác định bởi **(binary name, defining loader)**. Điều này kết nối trực tiếp với bài học:

- Hai **loader instances cùng một Java class** vẫn có thể định nghĩa **hai lớp runtime khác nhau** có cùng binary name.
- Hai loader instances **khác Java subclass** vẫn có thể cùng truy cập **một lớp runtime** nếu chúng ủy quyền và nhận về cùng `Class<?>` đã được parent định nghĩa.
- Chỉ so `loaderA.getClass() == loaderB.getClass()` **không đủ** để xác định hai loader instances là cùng một namespace.
- Chỉ so `classA.getName().equals(classB.getName())` **không đủ** để xác định hai runtime types là một; nên so `classA == classB` hoặc dùng `isAssignableFrom` phù hợp.

Ví dụ minh họa bằng sơ đồ:

```text
NamedLoader #1                   NamedLoader #2
(Java class giống nhau)          (Java class giống nhau)
    │ define "plugin.Task"           │ define "plugin.Task"
    ▼                                ▼
Class<?> task1                   Class<?> task2

task1.getName() == task2.getName()    → cùng tên
task1 == task2                        → false (nếu cả hai tự định nghĩa)
```

Đây là **mô hình khái niệm**: hai loader trong ví dụ B chưa tự thực hiện `defineClass`. Để minh họa cách ly thật cần triển khai `findClass`/`defineClass` hoặc dùng các loaders biết đọc JAR, xử lý dependency, package constraints và vòng đời. Không coi sơ đồ là stdout từ ví dụ B.

**Cảnh báo thuật ngữ:** *initiating loader* là loader được dùng để yêu cầu nạp; *defining loader* là loader đã định nghĩa class. Nếu `child.loadClass("java.lang.String")` trả về `String.class` qua parent, defining loader vẫn có thể là bootstrap. `child` là điểm gọi API không tự nhiên trở thành defining loader.

## 10. Hậu quả thực tế khi thiết kế plugin và application server

### 10.1. Chọn parent theo API cần chia sẻ

Trong plugin system, giả sử host định nghĩa interface `com.example.spi.Plugin`. Một plugin implementation cần được nhận diện là subtype của interface **mà host đang dùng**.

Nếu plugin dùng một loader với parent là application/system loader (hoặc một shared-API loader khác), cả host và plugin có thể nhìn cùng `Class<?>` cho interface SPI. Nếu plugin tự định nghĩa bản sao của interface cùng tên, cast sang interface host có thể thất bại.

Điều quan trọng là lựa chọn **parent object và class lookup policy** để duy trì **cùng định danh interface runtime**. Việc tạo một subclass loader tên `PluginClassLoader` hay `HostClassLoader` không tự xử lý vấn đề đó.

### 10.2. Vì sao không tạo parent relationship dựa vào Java superclass?

Giả sử viết:

```java
class PluginLoader extends HostLoader { }
```

Khi ấy `PluginLoader` kế thừa code của `HostLoader`. Điều này **không tự bảo đảm** nó hỏi một **instance `HostLoader` riêng** trước khi tìm class. Muốn có quan hệ ấy phải truyền/thiết lập đúng object parent qua constructor.

Ngược lại, nếu muốn plugin có hành vi khác host, việc `extends HostLoader` có thể kéo theo code và state không mong muốn. Một lựa chọn khác là:

```java
class PluginLoader extends ClassLoader {
    PluginLoader(ClassLoader hostLoader) {
        super(hostLoader);
    }
}
```

Đây là lựa chọn về **thiết kế API và composition**, còn cơ chế ủy quyền được quyết định bởi object parent và hành vi `loadClass`. Đoạn class ở đây chỉ minh họa constructor, chưa đầy đủ mã nạp plugin.

### 10.3. Chọn platform, system hay custom loader làm parent?

| Parent object | Tác dụng thường thấy | Giới hạn cần nhớ |
| --- | --- | --- |
| `ClassLoader.getPlatformClassLoader()` | Có thể nhìn thấy các lớp nền tảng, ít phụ thuộc ứng dụng hơn | Không mặc nhiên thấy mọi class trong application classpath, đặc biệt shared SPI. |
| `ClassLoader.getSystemClassLoader()` | Thường nhìn thấy classpath của ứng dụng và nền tảng | Có thể ưu tiên dependency của host thay vì bản plugin cần cách ly. |
| Một shared-API loader tùy biến | Chia sẻ các kiểu giao tiếp đã chủ định | Phải thiết kế chính xác loader nào nạp API nào. |
| `null` trong constructor loader | Sử dụng bootstrap cho delegation mặc định | Từ Java 9 không bảo đảm thấy toàn bộ platform APIs. |

Không có lựa chọn tuyệt đối cho mọi plugin. Phải xác định trước những class nào **bắt buộc dùng chung runtime identity** và những class nào được phép cô lập.

### 10.4. Không nên thay parent động

`getParent()` là `final`, và parent được thiết lập trong quá trình tạo loader. Thiết kế đổi parent bất ngờ có thể vi phạm giả định đã dùng để resolve classes, nhất quán resource lookup và loading constraints. Nếu cần lớp dependency mới hoặc vòng đời plugin mới, cân nhắc tạo loader instance mới theo kiến trúc và quản lý tham chiếu/vòng đời cẩn thận.

Việc tạo loader instance mới **không có nghĩa Java class của loader được định nghĩa lại**. `PluginLoader.class` có thể vẫn là một `Class<?>` duy nhất, trong khi có hàng chục instance của nó đang nạp các tập plugin khác nhau.

## 11. Phân biệt parent loader với superclass loader và context loader khi debug

Một đoạn kiểm tra hữu ích trong một chương trình đã có `loader` và `loadedType`:

```java
System.out.println(loader.getClass().getName());
System.out.println(loader.getClass().getSuperclass());
System.out.println(loader.getParent());
System.out.println(loadedType.getClassLoader());
System.out.println(Thread.currentThread().getContextClassLoader());
```

Đây là **snippet minh họa**, chỉ biên dịch sau khi khai báo `ClassLoader loader` và `Class<?> loadedType`. Các dòng lần lượt hỏi:

1. **Java type cụ thể** của loader object.
2. **Java superclass** của kiểu loader.
3. **Parent object** cho delegation.
4. **Defining loader của một class đã nạp** (không nhất thiết bằng `loader` đang thử load).
5. **Thread Context Class Loader (TCCL)** của thread hiện tại — một tham chiếu loader phục vụ nhiều API discovery, không nhất thiết là parent của `loader`.

TCCL có thể được framework thay đổi theo thread; ví dụ một số overloads của `ServiceLoader.load(...)` sử dụng context loader để tìm providers. Nó **không tự thay đổi `getParent()`** của bất kỳ loader instance nào.

**Không nhầm `getClassLoader()` với `getClass().getClassLoader()`:**

- Với một đối tượng loader `myLoader`, `myLoader.getClass().getClassLoader()` trả về **loader đã định nghĩa Java class `MyLoader`**.
- Với một class nghiệp vụ `MyFeature.class`, `MyFeature.class.getClassLoader()` trả về loader định nghĩa **`MyFeature`**.
- `myLoader.getParent()` trả về object parent dùng cho **delegation từ `myLoader`**.

Tất cả cùng dùng từ "loader" nhưng nói về **ba đối tượng/quan hệ** khác nhau.

## 12. Các lỗi lập trình phổ biến

| Sai lầm | Vì sao sai | Cách suy nghĩ đúng |
| --- | --- | --- |
| "`PluginLoader extends ClassLoader` nên parent của nó là `ClassLoader.class`" | Parent là một **object**, `ClassLoader.class` là đối tượng mô tả kiểu | Parent được constructor chọn, kiểm tra qua `getParent()`. |
| "`PluginLoader extends HostLoader`, vậy parent của plugin instance là host instance" | Kế thừa chỉ xác định superclass/các method nhận được | Phải truyền host object nếu muốn delegation qua nó. |
| "`loader.getParent()` trả `null` thì loader không thể nạp JDK classes" | `null` thường biểu diễn bootstrap lookup | Nhưng có thể thiếu platform-defined classes trên JDK hiện đại. |
| "Hai loader cùng Java class nên có cùng loaded classes" | Mỗi instance có trạng thái/namespaces nạp lớp riêng | So `Class<?>` và defining loader thực tế. |
| "Loader `L` trả về `Foo.class` nên `L` đã define `Foo`" | Parent có thể định nghĩa và trả về | Xem `Foo.class.getClassLoader()`. |
| "Parent-first đi tìm Java superclass của loader rồi gọi `loadClass`" | Không phải lookup theo type hierarchy | Tìm **parent loader object**, gọi phương thức trên object ấy. |
| "`getParent() == null` nghĩa là root của toàn JVM không còn tìm nơi nào khác" | Bootstrap là cơ chế VM built-in, và built-in/module loaders có quy tắc riêng | Xem đặc tả loader và phiên bản JDK. |
| "Có thể sửa `parent` tùy lúc để thay JAR runtime" | Không có setter công khai, runtime linkage/type identity đã được tạo | Thiết kế lifecycle loader/module layer riêng. |
| "Cứ override `loadClass` để sửa thứ tự" | Dễ làm sai đồng bộ, cache, delegation và package/JDK class rules | Thường override `findClass` và dùng mặc định `loadClass`, trừ khi có yêu cầu rõ. |
| "`Thread.currentThread().getContextClassLoader()` chính là `getParent()`" | TCCL và parent phục vụ mục đích khác | Đọc riêng cả hai trong debug. |

### Lỗi dễ gây hiểu nhầm khi đọc stacktrace

- `ClassNotFoundException`: một loader không tìm thấy lớp với tên yêu cầu. Kiểm tra **loader instance thực sự được hỏi** và parent chain của nó; class có trong JAR của ứng dụng không có nghĩa platform parent nhìn thấy.
- `NoClassDefFoundError`: JVM cần class nhưng không thể định nghĩa/sử dụng; có thể do dependency hoặc lỗi initialization trước đó, nên cần đọc `Caused by`.
- `ClassCastException` giữa hai lớp **cùng tên**: có thể do hai defining loader instances khác nhau đã tạo hai kiểu runtime. Không chữa bằng cách thay `extends` của loader.
- `LinkageError`: có thể xuất phát từ loading constraints, binary API mismatch hoặc lỗi liên kết khác. Cần kiểm tra loader identity, classpath/module path và descriptor trước khi đoán nguyên nhân.

Việc nhận biết đúng **loại quan hệ** thường rút ngắn quá trình debug hơn việc chỉ nhìn tên class loader in trong log.

## 13. Một sơ đồ tổng hợp áp dụng cho hệ plugin

Giả sử có hai đối tượng của hai Java classes khác nhau:

```text
                    JAVA TYPE HIERARCHY
                              Object
                                 │
                            ClassLoader
                              /     \
                     HostLoader   PluginLoader

                    RUNTIME OBJECT GRAPH
              platformLoader
                    ▲
                 parent
                    │
              hostInstance : HostLoader
                    ▲
                 parent
                    │
              pluginInstance : PluginLoader

                    LOADING EXAMPLE
pluginInstance.loadClass("java.lang.String")
       → hostInstance
       → platformLoader
       → bootstrap finds/defines String
       → trả về cùng String.class
```

Trong sơ đồ:

- Dấu nối giữa `HostLoader` và `PluginLoader` ở phía type hierarchy đi qua **`ClassLoader`**: đây là hai Java subclasses anh em.
- Mũi tên trong object graph là kết quả của **tham chiếu parent được truyền vào constructor**.
- `pluginInstance` gọi `loadClass` không khiến `String` do `PluginLoader` định nghĩa.
- Trong một lần lookup khác, nếu parent không tìm được một plugin class, một `PluginLoader.findClass` có triển khai thật mới có thể tự định nghĩa class ấy.
- Nếu custom loader muốn dùng parent khác, chỉ việc chọn một object parent khác theo hợp đồng API; không cần thay `extends`.

## 14. Câu hỏi tự kiểm tra kèm đáp án

**1. Có thể tạo `PluginLoader` parent là `HostLoader` dù hai lớp không kế thừa nhau?** Có. Chúng chỉ cần cùng là `ClassLoader` hợp lệ; truyền instance `HostLoader` vào constructor `PluginLoader(ClassLoader parent)`.

**2. Hai instance của **cùng Java class** `NamedLoader` có tạo thành chuỗi parent được không?** Có. Ví dụ `new NamedLoader("second", first)` tạo quan hệ `second.getParent() == first`.

**3. `PluginLoader.class.getSuperclass()` có đổi khi truyền parent khác?** Không. Superclass do khai báo `extends` quyết định.

**4. Nếu `PluginLoader extends HostLoader` nhưng gọi constructor không tham số của `ClassLoader` qua các cấp, parent có mặc nhiên là một `HostLoader` object không?** Không. Constructor không tham số của `ClassLoader` đặt system loader làm parent theo API.

**5. `plugin.loadClass("java.lang.String")` trả về `String.class` có chứng minh `plugin` định nghĩa `String`?** Không. Parent/bootstrap có thể đã định nghĩa `String` và trả về.

**6. Muốn biết defining loader của `Foo.class` thì gọi gì?** `Foo.class.getClassLoader()`; giá trị `null` thường biểu diễn bootstrap.

**7. Muốn biết superclass của Java class chứa implementation loader thì gọi gì?** `loader.getClass().getSuperclass()`; không phải `loader.getParent()`.

**8. Vì sao override `findClass` thường an toàn hơn override `loadClass` để bổ sung khả năng đọc bytecode?** Nó giữ được cơ chế kiểm tra loaded class, parent-first delegation và phần đồng bộ của triển khai `loadClass` mặc định.

**9. Parent loader có phải là superclass của domain class mà loader đang nạp?** Không. Loader parent là quan hệ giữa **loader objects**; superclass của domain class là quan hệ giữa **Java types**.

**10. Hai loader cùng subclass nhưng khác instance có thể tạo hai `plugin.Task` không tương thích không?** Có, nếu mỗi loader tự định nghĩa một runtime class `plugin.Task` riêng; class identity phụ thuộc binary name và defining loader.

## 15. Nguồn chuẩn và nguyên tắc ghi nhớ

- [Java SE 17 `ClassLoader` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): các constructor, `getParent()`, `loadClass`, `findClass` và built-in loaders. Javadoc mô tả rõ "**Each instance of ClassLoader has an associated parent class loader**".
- [Java SE 17 `Class` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `getSuperclass()`, `getClassLoader()`, `isInstance()`.
- [Java Virtual Machine Specification 17, Chapter 5: Loading, Linking, and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): vai trò loader, type identity, initiating/defining loader và linkage.
- [JDK 17 `ServiceLoader` Javadoc](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html): ví dụ API có thể sử dụng thread context class loader, phân biệt với parent delegation.

**Cách nhớ chắc chắn nhất:**

> **`extends` nối các *kiểu Java*; `getParent()` nối các *đối tượng class loader*.** Constructor quyết định object parent; type declaration quyết định superclass. Cơ chế ủy quyền tra cứu dùng quan hệ object, còn kế thừa Java quyết định hành vi mà loader subclass nhận được từ superclass.

Khi gặp một bug classloading, luôn hỏi riêng ba câu: **"Instance loader nào đang được dùng?", "Parent *object* của nó là ai?", "Loader nào đã định nghĩa `Class<?>` được trả về?"** Trả lời xong mới dùng Java inheritance hierarchy để giải thích vì sao một loader có phương thức hoặc cách tra cứu cụ thể.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="parent-delegation-005">Defining loader và initiating loader</a>

<details>
<summary>Xem chi tiết</summary>

# Defining loader và initiating loader trong JVM

## 1. Vì sao có hai khái niệm “loader”?

Khi đọc mã Java, bạn thường gặp biểu thức:

~~~java
Class<?> type = loader.loadClass("demo.Widget");
~~~

Dễ kết luận rằng đối tượng **loader** chính là bộ nạp đã **định nghĩa** class **demo.Widget**. Kết luận đó có thể sai: **loader** có thể nhận yêu cầu rồi **ủy quyền (delegate)** cho loader khác. JVM phân biệt hai vai trò:

- **Defining loader**: loader thực sự tạo/định nghĩa class hoặc interface runtime từ biểu diễn của kiểu; với class thông thường, đó là loader trực tiếp định nghĩa lớp.
- **Initiating loader**: loader tham gia **khởi xướng loading** của một class hoặc interface, dù có thể trả về định nghĩa được tạo bởi loader khác.

Một kiểu runtime có **một defining loader**, nhưng có thể được nhiều loader khác nhau **initiate**. Đó là lý do “loader được yêu cầu tìm lớp” và “loader sở hữu định nghĩa runtime của lớp” là hai câu hỏi riêng.

Bài này tập trung vào **cơ chế ủy quyền** và sự khác biệt giữa hai vai trò theo JVM Specification (JVMS §5.3, §5.3.4), sau đó liên hệ với đối tượng `Class`, danh tính kiểu, các API và tình huống lỗi thực tế.

## 2. Ví dụ khái niệm: A yêu cầu, B định nghĩa

Giả sử application có loader **ChildLoader A** và parent **AppLoader B**. B đã tìm và định nghĩa class **demo.Shared**.

~~~text
                 JVM / mã Java
                       |
                       | yêu cầu A tìm demo.Shared
                       v
              ChildLoader A
              (initiating)
                       |
                       | delegate theo parent-first
                       v
              AppLoader B
              (defining)
                       |
                       | tìm/định nghĩa demo.Shared
                       v
           Class<?> của demo.Shared
                       |
                       | trả cùng đối tượng Class
                       v
                  ChildLoader A
                       |
                       v
                  mã yêu cầu
~~~

Trong tình huống delegation này:

1. **A** tiếp nhận yêu cầu load. A có thể được ghi nhận là initiating loader của kiểu được trả về.
2. **B** cung cấp định nghĩa runtime thực tế; **B** là defining loader.
3. Kết quả trả về từ **A.loadClass("demo.Shared")** vẫn có **getClassLoader() == B**.
4. Nếu nhiều child loader cùng delegate tới B, chúng có thể nhận **cùng một đối tượng `Class`**.
5. Không xuất hiện một class mới “do A định nghĩa” chỉ vì A nhận yêu cầu.

**Chú ý thuật ngữ:** Các bước bên dưới mô tả việc loader nhận và delegate yêu cầu. JVMS là cơ sở chính xác để xác định *initiating loader* trong bối cảnh JVM ghi nhận loading; đừng chỉ đếm mọi object ClassLoader xuất hiện trong một chuỗi gọi Java rồi khẳng định tất cả đều được JVM ghi nhận như initiating loader. Đặc biệt, định nghĩa initiating loader gắn với các sự kiện loading của JVM, không phải một tập do ứng dụng tự đặt tên.

## 3. Định nghĩa chính xác theo JVM Specification

JVMS §5.3 mô tả loading là quá trình tìm một biểu diễn nhị phân của class/interface và tạo kiểu runtime. Với **non-array class/interface**:

- Loader **L** có thể tự lấy bytecode và yêu cầu JVM tạo class từ biểu diễn đó: **L trực tiếp định nghĩa class**.
- Loader **L** cũng có thể **ủy quyền** công việc cho loader khác, rồi trả về class mà loader kia đã định nghĩa: **L nạp gián tiếp**.
- Nếu JVM yêu cầu **L** khởi xướng việc loading và L trả về class được loader khác tạo, **initiating loader và defining loader có thể khác nhau**.
- Defining loader cũng đóng vai trò initiating loader của class nó định nghĩa. Một class có thể được ghi nhận với thêm initiating loader khác qua delegation.
- Danh tính runtime của non-array class/interface là cặp **(binary name, defining loader)**, không bao gồm tên của tất cả initiating loader.

| Câu hỏi | Defining loader | Initiating loader |
| --- | --- | --- |
| Ai thực sự định nghĩa kiểu runtime? | Chính loader này. | Có thể chính nó hoặc loader khác. |
| Một kiểu runtime có thể có bao nhiêu? | Một defining loader. | Có thể nhiều initiating loader. |
| Thành phần danh tính của class? | **Có**: kết hợp binary name. | **Không**: không tạo danh tính mới chỉ bởi việc initiate. |
| `Class.getClassLoader()` trả về? | Defining loader (hoặc null với bootstrap). | Không phải API liệt kê initiating loader. |
| Delegation có thể làm hai vai trò khác nhau? | Có. | Có. |
| Hai loader chỉ initiate cùng class thì có hai bản class? | Không. | Không. |
| Hai loader trực tiếp define cùng binary name riêng rẽ? | Tạo hai runtime class khác nhau. | Mỗi loader cũng initiate lớp mình define. |

### 3.1 “Định nghĩa một class” có nghĩa là gì?

Một class loader trực tiếp định nghĩa lớp khi JVM tạo biểu diễn runtime từ một binary representation gắn với loader đó. Với custom Java loader, hoạt động này thường dùng các overload của **ClassLoader.defineClass**.

~~~text
bytes cho demo.Widget
         |
         v
ClassLoader L -- defineClass(...) --> JVM
                                      |
                                      v
                            runtime class demo.Widget
                            defining loader = L
                                      |
                                      v
                            java.lang.Class mirror
~~~

Một lớp được định nghĩa một lần bởi L không thể bị thay lén thành một class thứ hai với **cùng binary name và cùng defining loader L** bằng việc gọi defineClass lại. Việc redefine bằng instrumentation là một cơ chế khác, không nên đánh đồng với định nghĩa thêm một class riêng.

Defining loader không chỉ ảnh hưởng đến danh tính kiểu mà còn tham gia vào xác định **runtime package** (tên package và defining loader), loading constraints và các quy tắc bảo đảm type safety.

### 3.2 “Khởi xướng loading” không đồng nghĩa “đã chạy static block”

Tên **initiating loader** nói về **loading**, không phải **class initialization**. Một lời gọi **loadClass** thường chưa làm static initializer chạy.

Ba bước khác nhau:

~~~text
LOADING
  tìm/định nghĩa kiểu; xác định initiating và defining loader
       |
       v
LINKING
  verification, preparation, resolution theo quy tắc JVM
       |
       v
INITIALIZATION
  thực thi logic static khi có active use và các trigger tương ứng
~~~

Từ “initiating” trong tên initiating loader **không có nghĩa** là “loader khởi tạo static fields”. Đây là nhầm lẫn phổ biến vì hai từ *initiate* và *initialize* có vẻ gần nghĩa.

## 4. Java ClassLoader thường ủy quyền như thế nào?

Với implementation mặc định của **ClassLoader.loadClass(name, resolve)**, quá trình thường gồm:

1. **findLoadedClass(name)** kiểm tra xem JVM đã ghi nhận class tương ứng đối với loader hiện tại chưa.
2. Nếu chưa, thử **parent.loadClass(name)**, hoặc bootstrap theo điều kiện của loader.
3. Nếu parent không tìm được, gọi **findClass(name)** do loader hiện tại cài đặt.
4. Nếu cần, thực hiện **resolveClass** rồi trả về đối tượng `Class`.

Mã giả thể hiện ý tưởng:

~~~java
// Pseudo-code minh họa, không phải mã nguồn JDK nguyên văn
Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    Class<?> clazz = findLoadedClass(name);

    if (clazz == null) {
        try {
            clazz = delegateToParentOrBootstrap(name);
        } catch (ClassNotFoundException ignored) {
            clazz = findClass(name);
        }
    }

    if (resolve) {
        resolveClass(clazz);
    }

    return clazz;
}
~~~

Đây là cơ chế **parent-first phổ biến**, không phải quy tắc mọi ClassLoader tùy biến đều phải theo. Các plugin container, application server hay loader tự viết có thể thay đổi chính sách tìm lớp (cần bảo đảm ràng buộc JVM).

### 4.1 Vì sao một class có nhiều initiating loader?

Giả sử **Loader A** và **Loader C** là hai child loader khác nhau, cùng delegate tới **Loader B**:

~~~text
      Loader A                 Loader C
    (requester)              (requester)
        |                        |
        +-----------+------------+
                    |
                    v
                Loader B
           định nghĩa demo.Shared
                    |
                    v
        MỘT java.lang.Class duy nhất
              cho demo.Shared
~~~

Nếu JVM loading ghi nhận A và C là những initiating loader thích hợp, vẫn **chỉ có một class** vì defining loader là B.

Điều cần kiểm tra là **định nghĩa cuối cùng của class thuộc loader nào**. Việc tạo nhiều object ClassLoader không tự nhân bản những class được parent định nghĩa.

## 5. Ví dụ Java 17+ số 1: child loader initiate, parent define

Tạo tệp **DelegationRolesDemo.java** với một nested class và một child loader:

~~~java
public class DelegationRolesDemo {
    static class Shared {
        static {
            System.out.println("Shared initialized");
        }
    }

    static class ChildLoader extends ClassLoader {
        int ownFindClassCalls;

        ChildLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            ownFindClassCalls++;
            throw new ClassNotFoundException(name);
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader appLoader = DelegationRolesDemo.class.getClassLoader();
        ChildLoader child = new ChildLoader(appLoader);

        Class<?> first = child.loadClass(Shared.class.getName());
        System.out.println("same Class = " + (first == Shared.class));
        System.out.println("child defines = "
                + (first.getClassLoader() == child));
        System.out.println("app defines = "
                + (first.getClassLoader() == appLoader));
        System.out.println("child findClass calls = "
                + child.ownFindClassCalls);

        Class<?> again = child.loadClass(Shared.class.getName());
        System.out.println("same repeated result = " + (first == again));

        System.out.println("before active use");
        new Shared();
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac DelegationRolesDemo.java
java DelegationRolesDemo
~~~

Stdout:

~~~text
same Class = true
child defines = false
app defines = true
child findClass calls = 0
same repeated result = true
before active use
Shared initialized
~~~

### Phân tích kết quả

**child.loadClass(...)** nhận yêu cầu đầu tiên nhưng dùng parent-first; application loader đã có thể tìm nested class `DelegationRolesDemo$Shared` nên **ChildLoader.findClass** không được gọi.

**first == Shared.class** đúng vì cả hai là **cùng một class runtime**, do application loader định nghĩa.

**first.getClassLoader() == child** là false; **first.getClassLoader() == appLoader** là true. Đây là chứng cứ trực tiếp xác nhận defining loader, bất kể child loader nào được yêu cầu load.

Gọi **child.loadClass(...)** lần nữa trả về cùng một `Class`; chương trình không định nghĩa bản Shared riêng.

Dòng **Shared initialized** chỉ xuất hiện khi **new Shared()** tạo active use. Các lần gọi **loadClass** trước đó không tự khởi tạo Shared.

**Cách kiểm tra thực tế:** nếu class được bootstrap định nghĩa như **java.lang.String**, **String.class.getClassLoader()** trả về **null** trong Java. Null thể hiện bootstrap loader, không có nghĩa kiểu “không có defining loader”.

## 6. Ví dụ Java 17+ số 2: hai defining loader tạo hai kiểu khác nhau

Ví dụ trước cho thấy nhiều loader delegate vẫn nhận **một** class. Bây giờ xét trường hợp hai loader thực sự tự định nghĩa cùng binary name từ cùng tệp bytecode.

Tạo cấu trúc:

~~~text
project/
├── IsolationDemo.java
└── src/
    └── sample/
        └── Payload.java
~~~

**src/sample/Payload.java**:

~~~java
package sample;

public class Payload {
    public Payload() {
    }

    public String message() {
        return "hello";
    }
}
~~~

**IsolationDemo.java**:

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class IsolationDemo {
    public static void main(String[] args) throws Exception {
        URL classes = Path.of("out").toUri().toURL();

        try (URLClassLoader left = new URLClassLoader(
                     new URL[]{classes},
                     ClassLoader.getPlatformClassLoader());
             URLClassLoader right = new URLClassLoader(
                     new URL[]{classes},
                     ClassLoader.getPlatformClassLoader())) {

            Class<?> a = left.loadClass("sample.Payload");
            Class<?> b = right.loadClass("sample.Payload");

            System.out.println("same name = "
                    + a.getName().equals(b.getName()));
            System.out.println("same Class = " + (a == b));
            System.out.println("left defines a = "
                    + (a.getClassLoader() == left));
            System.out.println("right defines b = "
                    + (b.getClassLoader() == right));

            Object fromRight = b.getDeclaredConstructor().newInstance();
            try {
                a.cast(fromRight);
                System.out.println("cast succeeded");
            } catch (ClassCastException expected) {
                System.out.println("cross-loader cast = ClassCastException");
            }
        }
    }
}
~~~

Đứng trong thư mục **project**, chạy:

~~~bash
javac -d out src/sample/Payload.java IsolationDemo.java
java -cp out IsolationDemo
~~~

Stdout:

~~~text
same name = true
same Class = false
left defines a = true
right defines b = true
cross-loader cast = ClassCastException
~~~

### Tại sao hai loader đều define Payload?

Ở đây cả hai **URLClassLoader** có **platform class loader** làm parent. Platform loader trong lệnh chạy ví dụ này không tìm lớp ứng dụng `sample.Payload` ở thư mục `out`, vì vậy mỗi URLClassLoader tự tìm file ở URL riêng và tự định nghĩa lớp.

Cả **a** và **b** đều có tên nhị phân `sample.Payload`, bytecode cũng đến từ cùng file, nhưng **defining loader khác nhau**. Do đó:

~~~text
(sample.Payload, left)  !=  (sample.Payload, right)
~~~

Hai object của các lớp này không tự tương thích kiểu. Phép **a.cast(fromRight)** ném ClassCastException, dù tên class trông giống nhau.

**Điều kiện quan trọng để tái hiện:** parent của hai URLClassLoader phải **không nạp được sample.Payload**. Nếu thay parent bằng application loader (nơi `out` nằm trong classpath) và parent-first delegation thành công, cả hai có thể trả về cùng Class do application loader định nghĩa, khiến kết quả khác.

Không nên tự ý đổi parent trong ví dụ rồi vẫn mong có hai defining loader.

## 7. Danh tính kiểu: binary name + defining loader

Đối với class/interface runtime thông thường, JVMS xác định danh tính bằng cặp:

~~~text
(binary name, defining class loader)
~~~

Ví dụ:

~~~text
("com.example.Plugin", LoaderA) → Class A
("com.example.Plugin", LoaderB) → Class B
("com.example.Plugin", LoaderA) → vẫn Class A nếu đã được define
~~~

Hai class cùng binary name nhưng khác defining loader là **hai kiểu riêng biệt**. Ngược lại, hai initiating loader khác nhau nhưng cùng delegate tới một defining loader có thể sử dụng **chính cùng một Class**.

Điều này ảnh hưởng tới:

- **Ép kiểu (cast)** và toán tử `instanceof`.
- **Chữ ký method/field** chứa tên class của dependency.
- **Reflection** và so sánh `Class`.
- **Runtime package** và kiểm tra quyền truy cập package-private.
- **Nạp plugin** khi host và plugin có các bản thư viện riêng.
- **Serialization, proxy, service loading** và framework dùng reflection khi classpath/classloader bị chia tách.

### 7.1 So sánh Class chứ không chỉ tên String

~~~java
boolean sameName = first.getName().equals(second.getName());
boolean sameRuntimeType = first == second;
ClassLoader firstDefiner = first.getClassLoader();
ClassLoader secondDefiner = second.getClassLoader();
~~~

`sameName` đúng **không kéo theo** `sameRuntimeType` đúng.

Nếu **getClassLoader()** trả null, class thường do bootstrap loader định nghĩa; primitive Class mirrors cũng có cách biểu diễn đặc biệt. Không suy ra rằng một class bị lỗi chỉ vì getClassLoader trả null.

### 7.2 Class loading và runtime package

JVM xác định runtime package của class theo **tên package + defining loader**. Vì vậy hai lớp cùng khai báo `package demo;` nhưng do hai defining loader khác nhau tạo ra có thể thuộc **hai runtime package khác nhau** về kiểm tra quyền truy cập.

Đừng cho rằng cùng dòng `package demo;` trong source code tự cho phép truy cập package-private giữa các class do những loader độc lập định nghĩa. Ở ứng dụng modular, module và các quy tắc truy cập tương ứng cũng cần được xét.

## 8. Một tên lớp, nhiều loader nhưng vẫn một Class: lý do delegation hữu ích

Delegation thường được dùng để chia sẻ các kiểu chung và tránh những phiên bản class trùng không tương thích.

Hình dung một host application có interface chung:

~~~java
package api;

public interface Task {
    void execute();
}
~~~

Host và plugin đều cần **api.Task** có **cùng định nghĩa runtime** để object plugin có thể được sử dụng như Task của host.

Nếu mỗi plugin loader tự định nghĩa một bản `api.Task` riêng, chỉ giống tên và bytecode thôi chưa đủ để phép cast sang `api.Task` của host thành công. Bởi vậy nhiều plugin system chọn để API chung được định nghĩa bởi parent/shared loader, rồi child loader định nghĩa phần implementation của plugin.

~~~text
Shared API loader
   └─ defines api.Task
           ▲
           │ delegated
     +-----+-----+
     |           |
PluginLoader1  PluginLoader2
defines Impl1  defines Impl2

Impl1 / Impl2 đều dùng chung Class api.Task
~~~

Cấu hình thực tế còn phụ thuộc dependency versions, package ownership và chính sách child-first/parent-first. Quy tắc kiến trúc hữu ích là xác định rõ **API nào phải dùng chung một defining loader**, còn implementation nào được phép cô lập.

## 9. Khi nào initiating loader quan trọng hơn một “chi tiết nội bộ”?

Defining loader xác định class identity; initiating loader giúp JVM theo dõi quá trình giải quyết tên lớp một cách nhất quán qua những loader có thể ủy quyền.

### 9.1 Sự nhất quán khi nạp lại cùng binary name

JLS §12.2.2 mô tả tính chất mà những class loader vận hành tốt cần bảo đảm: khi nhận cùng tên, loader nên trả về cùng đối tượng `Class`. Nếu loader từng initiate một class và lại được yêu cầu cùng tên đó, JVM có cơ chế theo dõi để tránh thay thế tùy tiện bằng class không nhất quán.

Đó là lý do loader không được tùy ý “hôm nay trả Class A, ngày mai trả Class B” cho cùng yêu cầu và cùng ngữ cảnh; JVM thực thi những ràng buộc cần thiết bảo vệ hệ kiểu.

### 9.2 Loading constraints

JVM áp đặt **loading constraints** để các loader có cái nhìn tương thích về các kiểu xuất hiện trong quan hệ giữa method/field và những chỗ liên kết tương ứng. Ví dụ, nếu hai class khác defining loader cùng tham gia liên kết method có parameter kiểu `api.Message`, các ràng buộc có thể buộc hai phía thống nhất về danh tính của `api.Message`.

Khi các loader trả về các Class khác nhau cho kiểu buộc phải đồng nhất, linking có thể thất bại bằng **LinkageError** (ví dụ thông báo **loader constraint violation** trên một JVM cụ thể).

Không nên suy ra rằng mọi trường hợp hai loader định nghĩa cùng binary name đều lập tức là lỗi. Ví dụ IsolationDemo vẫn tạo được hai class khác nhau. Lỗi xảy ra khi JVM gặp **những ràng buộc cụ thể** đòi hỏi chúng nhất quán.

### 9.3 Gọi loadClass không phải thao tác introspection đầy đủ về initiating state

Java công khai **Class.getClassLoader()** để xem defining loader, nhưng không cung cấp một API phổ thông trên Class để liệt kê tất cả initiating loader từng liên quan.

**ClassLoader.findLoadedClass(name)** là phương thức protected, kiểm tra lớp đã được JVM ghi nhận là initiated bởi loader đó theo hợp đồng API; không nên diễn giải nó như một cách liệt kê mọi class mà loader trực tiếp define.

Vì các đường đi loading và việc JVM ghi nhận initiating loader có chi tiết đặc tả riêng, hãy dùng **kết quả loadClass, defining loader và class identity** để kiểm chứng các ví dụ; đừng suy đoán toàn bộ trạng thái nội bộ chỉ từ một lời gọi API đơn lẻ.

## 10. Các API liên quan và điều chúng nói lên

| API | Cách dùng phù hợp | Không nên suy ra |
| --- | --- | --- |
| `loader.loadClass(binaryName)` | Yêu cầu loader trả về Class theo tên. | Loader được gọi là defining loader. |
| `clazz.getClassLoader()` | Xem defining loader của class; null có thể là bootstrap. | Danh sách initiating loader. |
| `ClassLoader.getParent()` | Xem parent theo cấu trúc API. | Luôn có delegation parent-first hoặc toàn bộ class đều do parent định nghĩa. |
| `findClass(name)` | Hook thường được custom loader cài đặt để tìm/define class khi delegation không thành. | Mặc định JVM phải gọi hook này cho mọi loadClass. |
| `defineClass(...)` | Tạo class từ binary representation với loader đang define. | Được phép define lại cùng class name trong cùng loader tùy ý. |
| `findLoadedClass(name)` | Kiểm tra class được ghi nhận initiated bởi loader khi gọi trong subclass. | Chỉ phản ánh class do loader trực tiếp define. |
| `Class.forName(name, false, loader)` | Lấy Class theo loader được chỉ định, không chủ động initialization. | Loader tham số chắc chắn là defining loader. |
| `Class.forName(name)` | Tìm class bằng loader của caller thích hợp và yêu cầu initialization. | Chỉ loading mà không chạy static initializer. |

Trong các ví dụ, **Class.getName()** trả binary name của class thông thường, nhưng không đủ để xác định class identity. Thao tác trực tiếp nhất để so hai Class là **a == b**.

**Phân biệt loader của một class và context class loader của thread:** `Thread.currentThread().getContextClassLoader()` là ngữ cảnh cho một số framework/API tra class/service. Nó có thể được đặt thành một loader khác và **không phải** định nghĩa claiming loader của Class bất kỳ. Chỉ dựa trên thread context loader có thể dẫn đến kết luận sai.

## 11. Quan sát loader và class ở runtime

### 11.1 Quan sát bằng Java

Tại điểm cần điều tra một Class:

~~~java
Class<?> c = object.getClass();
System.out.println("name = " + c.getName());
System.out.println("defining loader = " + c.getClassLoader());
System.out.println("module = " + c.getModule());
System.out.println("same expected Class = " + (c == Expected.class));
~~~

Code trên là đoạn minh họa đặt vào method khi có sẵn `object` và `Expected`; nó không phải một class standalone. Thông tin `getModule()` hữu ích khi module và classloader tương tác.

Một số class do bootstrap định nghĩa trả null từ getClassLoader; đừng gọi method trên giá trị ấy mà không kiểm tra null.

### 11.2 Quan sát class loading trên JVM hỗ trợ Unified Logging

Với HotSpot / JDK hiện đại, có thể bật log:

~~~bash
java -Xlog:class+load=info DelegationRolesDemo
~~~

Số dòng và format log phụ thuộc JDK/JVM. Xem tag được hỗ trợ:

~~~bash
java -Xlog:help
~~~

Log loading có thể cho biết nguồn class; nó không phải cơ chế tuyệt đối để suy ra initiating loader list, cũng không chứng minh static initializer đã chạy. Khi cần chứng minh static initialization, tạo một ví dụ có static block in thông báo có kiểm soát như phần 5.

### 11.3 Đọc class file để kiểm tra binary name

Nếu nghi ngờ lớp trong JAR sai package/tên, dùng:

~~~bash
javap -verbose -classpath out sample.Payload
jar --list --file example.jar
~~~

`javap` xem class file/constant pool; `jar --list` kiểm tra archive entries. Hai lệnh này **không cho biết loader runtime nào đã define class** trong ứng dụng đang chạy. Việc tìm thấy `sample/Payload.class` không bảo đảm JVM đã dùng chính bytes đó, vì loader khác có thể tìm bản định nghĩa khác.

## 12. Tình huống lỗi: khi nào cần xem defining loader?

### 12.1 ClassCastException nhưng hai tên đều giống nhau

Ví dụ exception có hai class cùng `com.example.Dto`. Trước khi nghĩ rằng JVM ép kiểu sai, hãy kiểm tra **a.getClassLoader()** và **b.getClassLoader()**. Nếu defining loader khác nhau, đây là hai runtime type; cần làm rõ nơi API shared được định nghĩa và policy delegation của các plugin.

Thường nên đảm bảo DTO/interface dùng để giao tiếp được định nghĩa bởi một shared loader, trong khi code plugin cụ thể có thể cô lập ở child loader.

### 12.2 NoClassDefFoundError hoặc ClassNotFoundException

Nếu custom loader yêu cầu parent tìm class nhưng parent không thấy và child cũng không tìm được, **ClassNotFoundException** có thể phát sinh từ API loadClass. Khi JVM cần một type để resolution, lỗi nạp tương ứng có thể biểu hiện là **NoClassDefFoundError**.

Kiểm tra binary name, nguồn artifact, classpath/module path, parent loader, logic findClass và nguyên nhân gốc trong stack trace. Không giả định ngay rằng loader trong exception chính là defining loader vì có thể việc định nghĩa đã không thành công.

### 12.3 LinkageError: loader constraint violation

Nếu một interface/class cần chia sẻ qua ranh giới loader nhưng hai phía cùng trả về những bản type không tương thích trong chữ ký method, JVM có thể báo lỗi constraint. Cần đối chiếu cấu hình module, phiên bản thư viện, kiểu API truyền qua boundary và loader nào định nghĩa từng loại.

“Class có cùng tên” chỉ là một manh mối; **loader khác nhau không nhất thiết luôn sai**. Trong plugin isolation, đó có thể là thiết kế chủ đích. Lỗi là khi ranh giới cần dùng chung type mà lại không thống nhất class identity.

### 12.4 Đã tạo child loader nhưng class vẫn dùng implementation cũ

Nếu child loader parent-first và parent đã định nghĩa class, child.loadClass có thể trả Class của parent mà không gọi child.findClass. Vì vậy, việc đặt một phiên bản mới của class trong child URL **không bảo đảm** nó được dùng.

Có thể cần điều chỉnh rõ **parent visibility/delegation rules** cho những package được phép isolation. Tránh đổi sang child-first một cách tràn lan, đặc biệt với JDK classes, shared APIs hoặc packages mà VM đòi thống nhất định nghĩa.

## 13. Những trường hợp đặc biệt cần biết

### 13.1 Array classes

JVM tạo array class trực tiếp, không bằng cách tìm một file `[Lsample/Payload;.class` từ classpath. Với array có component là reference type, defining loader của array class gắn với defining loader của component type theo quy tắc JVMS. Với primitive-array class, JVM áp dụng quy tắc bootstrap.

Vì vậy, khi so sánh `Payload[].class` từ hai định nghĩa Payload khác loader, array runtime types cũng khác. Đây không phải hai bản class file của mảng do hai loader đọc được.

### 13.2 Primitive types và void

Các `Class` mirror như `int.class` và `void.class` không tương ứng với một class file thường trên classpath. Không nên diễn giải getClassLoader của primitive giống việc một custom loader đọc bytes của `int.class`.

### 13.3 Modules và custom loaders

Java Platform Module System ảnh hưởng đến nơi class được xác định, khả năng truy cập và module membership. `clazz.getModule()` cung cấp ngữ cảnh hữu ích bên cạnh defining loader. Tuy nhiên, **tên module không thay thế defining loader trong cặp class identity**; module là một chiều bổ sung về organization/accessibility.

### 13.4 Class unloading

Định nghĩa class gắn với defining loader. Việc class có thể được unload phụ thuộc các tham chiếu còn giữ class, loader và những điều kiện JVM khác. Không suy ra rằng class bị unload chỉ vì một initiating loader object không còn được dùng. Đây là phần liên quan đến GC/class lifecycle và nên phân tích theo công cụ đo thực tế khi cần.

## 14. Các hiểu nhầm nên tránh

- **“Bất kỳ loader nào gọi loadClass cũng là defining loader.”** Sai: loader có thể delegate.
- **“Nhiều initiating loader nghĩa là nhiều bản Class.”** Sai: nhiều loader có thể nhận cùng Class từ một defining loader.
- **“getClassLoader() cho biết initiating loader.”** Sai: đây là defining loader hoặc null với bootstrap.
- **“Parent loader luôn được dùng trước.”** Đúng với nhiều implementation mặc định, nhưng custom loader có thể thay đổi delegation.
- **“Tên class giống nhau thì cast được.”** Sai khi defining loader khác nhau.
- **“Bytes giống nhau thì runtime type giống nhau.”** Sai; còn phải giống defining loader và binary name.
- **“Gọi loadClass đã khởi tạo mọi static field.”** Sai; loading và initialization khác nhau.
- **“Tất cả loader nằm giữa trong Java call stack luôn được JVM ghi nhận là initiating.”** Không suy luận được; tham chiếu JVMS về JVM-initiated loading và delegation.
- **“Một field/method API dùng chung hai plugin tự động tương thích vì cùng package.”** Sai nếu shared types bị định nghĩa riêng và vi phạm class identity/loading constraints.

## 15. Câu hỏi tự kiểm tra

**Câu 1.** Loader A nhận yêu cầu rồi delegate cho B định nghĩa `pkg.Service`. `Service.class.getClassLoader()` là A hay B?

**Đáp án:** B, defining loader. A có thể đóng vai trò initiating loader trong quá trình JVM yêu cầu nạp qua delegation.

**Câu 2.** Loader A và C cùng delegate tới B cho `pkg.Service`. Có bắt buộc hai Class khác nhau không?

**Đáp án:** Không. Chúng có thể cùng nhận một đối tượng Class do B định nghĩa.

**Câu 3.** Loader A và C mỗi loader trực tiếp define `pkg.Service` bằng cùng bytes. Hai đối tượng Class có bằng nhau không?

**Đáp án:** Không, vì defining loader khác nhau. Class identity gồm binary name và defining loader.

**Câu 4.** Vì sao gọi `child.loadClass(...)` có thể không bao giờ chạy `child.findClass(...)`?

**Đáp án:** Implementation mặc định dùng parent-first delegation. Nếu parent tìm được Class, child trả ngay kết quả ấy.

**Câu 5.** `findLoadedClass` chỉ tìm những class tự define bởi loader đó?

**Đáp án:** Không; hợp đồng API dựa trên các class mà JVM đã ghi nhận loader là initiating loader, có thể gồm class được trả về qua delegation.

**Câu 6.** Đã gọi loadClass thành công, liệu static initializer của class chắc chắn chạy chưa?

**Đáp án:** Chưa. Initialization thường đợi active use riêng; `Class.forName(name)` dạng một tham số có quy tắc khởi tạo khác.

**Câu 7.** Vì sao hai plugin cần dùng chung Class object của một interface giao tiếp?

**Đáp án:** Nếu mỗi plugin định nghĩa riêng interface, runtime type identity khác nhau, dẫn đến lỗi cast hoặc linkage khi API boundary cần thống nhất kiểu.

**Câu 8.** `String.class.getClassLoader()` trả null có nghĩa String không có defining loader?

**Đáp án:** Không. Null biểu thị bootstrap loader theo hợp đồng Class API trong trường hợp này.

## 16. Tóm tắt kiến thức

Để gỡ lỗi classloader, hãy tách ba bước:

~~~text
(1) JVM / chương trình yêu cầu loader nào tìm class?
           |
           v
     initiating loader
           |
           | có thể delegate
           v
(2) Loader nào thực sự định nghĩa class?
           |
           v
      defining loader
           |
           v
(3) Runtime type được xác định bằng:
    (binary name, defining loader)
~~~

**Defining loader** xác định danh tính của class runtime; **initiating loader** thể hiện vai trò khởi xướng loading, có thể thông qua delegation. Nếu chỉ nhớ một quy tắc thực hành: **`loader.loadClass(name)` không chứng minh loader ấy đã define class; hãy xem `Class.getClassLoader()` và so sánh chính các đối tượng `Class`.**

### Tài liệu tham khảo

- **JVMS Java SE 17 §5.3 – Creation and Loading:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- **JVMS Java SE 17 §5.3.4 – Loading Constraints:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3.4
- **JLS Java SE 17 §12.2.2 – Class Loader Consistency:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.2.2
- **ClassLoader API, Java 17:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- **Class API, Java 17:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="parent-delegation-006">Phân biệt loader được yêu cầu với loader thực sự định nghĩa lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt loader được yêu cầu với loader thực sự định nghĩa lớp

## 1. Tình huống dễ gây nhầm lẫn

Giả sử ứng dụng có một `ClassLoader` con tên `PluginLoader`:

```java
Class<?> type = pluginLoader.loadClass("com.example.api.Service");
```

Một cách hiểu sai thường gặp là: “Vì đã gọi `pluginLoader.loadClass(...)` nên `Service` được định nghĩa bởi `pluginLoader`.” Trên thực tế, `PluginLoader` có thể **ủy quyền** yêu cầu cho loader cha; loader cha tìm thấy hoặc đã nạp `Service` và trả về **chính định nghĩa lớp của cha**. Loader con chỉ nhận kết quả trả về.

Do đó cần tách ba khái niệm:

1. **Loader được yêu cầu** (*requested loader*): đối tượng `ClassLoader` được gọi để xử lý yêu cầu, chẳng hạn `pluginLoader` ở ví dụ trên.
2. **Loader khởi xướng việc nạp** (*initiating loader*, thuật ngữ JVMS): loader được JVM ghi nhận là bên khởi xướng việc nạp một class/interface cụ thể, có thể được thỏa mãn nhờ ủy quyền cho loader khác. Thuật ngữ này liên quan đến trạng thái nạp và các ràng buộc liên kết trong JVM.
3. **Loader định nghĩa lớp** (*defining loader*): loader thực sự xác lập định nghĩa runtime của class từ dữ liệu nhị phân (thường đi qua `defineClass`), hoặc bootstrap loader do JVM đảm nhiệm đối với các lớp nền tảng. **Đây là loader tham gia định danh kiểu runtime.**

Trong luồng thao tác phổ biến, loader ban đầu được gọi là loader yêu cầu, và việc ủy quyền có thể khiến defining loader nằm ở cha. Không nên đồng nhất khái niệm *initiating loader* trong đặc tả với “bất cứ đối tượng nào có phương thức `loadClass` được gọi trong stack trace”: cần dùng đúng thuật ngữ khi phân tích cơ chế JVM.

### Một quy tắc quan sát đáng nhớ

```java
Class<?> c = requestedLoader.loadClass("some.package.Type");
ClassLoader defining = c.getClassLoader();
```

- `requestedLoader` là nơi lập trình viên gửi yêu cầu.
- `c.getClassLoader()` trả về **defining loader** theo Java API; `null` nghĩa là lớp được bootstrap loader định nghĩa.
- Hai giá trị **có thể khác nhau** mà chương trình vẫn hoàn toàn đúng.
- Với class do bootstrap định nghĩa, không có đối tượng Java `ClassLoader` thông thường để so sánh với `null`.

Các đoạn trên là minh họa, giả định các biến tương ứng đã tồn tại.

## 2. Vì sao requested loader có thể khác defining loader?
Khi kế thừa `ClassLoader.loadClass` mặc định, child kiểm tra `findLoadedClass(name)`, hỏi parent (hoặc bootstrap nếu parent là `null`), rồi mới gọi `findClass(name)` nếu parent không tìm thấy. Vì vậy một lời gọi `child.loadClass("api.Service")` có thể trả **chính `Class<?>` do parent định nghĩa**, không tạo lớp mới ở child.
```text
requested child → parent tìm được api.Service → trả Class của parent
requested child → parent không tìm thấy → child.findClass → có thể define tại child
```
Đường đi này là mặc định; custom loader hoặc JPMS có thể thay đổi chính sách. Khi debug, phân biệt đường gọi `loadClass` với `result.getClassLoader()` (defining loader). `loadClass` không tự yêu cầu initialization.

## 3. Bảng so sánh: nơi nhận yêu cầu, nơi tìm lớp và nơi định nghĩa

| Tình huống | Loader nhận yêu cầu đầu tiên | Loader thực sự định nghĩa | Kết quả |
| --- | --- | --- | --- |
| Child yêu cầu một class API đã có ở application loader | Child | Application loader | Nhận lại cùng `Class` do parent định nghĩa |
| Child yêu cầu `java.lang.String` | Child | Bootstrap loader | `String.class.getClassLoader() == null` |
| Parent không có plugin class, child có JAR chứa class đó | Child | Child, nếu `findClass` định nghĩa thành công | Child thực sự sở hữu định nghĩa plugin class |
| Hai child cùng hỏi parent về một class | Child A và Child B | Parent | Hai kết quả có thể là cùng đối tượng `Class<?>` |
| Hai child độc lập tự định nghĩa cùng binary name | Child A và Child B | A và B tương ứng | Hai kiểu runtime khác nhau |
| Child-first loader ưu tiên bản thư viện riêng | Child | Thường child đối với package được ưu tiên | Có thể tồn tại song song với bản ở parent; cần kiểm soát ranh giới API |

**Không thể suy luận defining loader từ:** vị trí đặt JAR, tên biến `pluginLoader`, giá trị `Thread.currentThread().getContextClassLoader()`, hay một lời gọi `ClassLoader.getSystemClassLoader()`. Phải xem class thực tế trả về và cách nó được định nghĩa.

## 4. Java 17+: thực nghiệm yêu cầu ở child nhưng định nghĩa tại parent

Ví dụ đầy đủ sử dụng một class `SharedApi` được tải trong ứng dụng và một `ProbeLoader` làm loader con. `ProbeLoader` không tự có đường tìm class; nó thử `findClass` chỉ khi parent không thể thỏa mãn yêu cầu. Đây là cách làm rõ parent-first bằng số lần gọi `findClass`.

Lưu `RequestedVsDefiningLoaderDemo.java`:

```java
public class RequestedVsDefiningLoaderDemo {

    public static final class SharedApi {
        public String message() {
            return "shared";
        }
    }

    static final class ProbeLoader extends ClassLoader {
        int localFindCalls;

        ProbeLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            localFindCalls++;
            throw new ClassNotFoundException(name);
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader appLoader = SharedApi.class.getClassLoader();
        ProbeLoader child = new ProbeLoader(appLoader);

        Class<?> result = child.loadClass(SharedApi.class.getName());

        System.out.println("requestedIsDefining="
                + (child == result.getClassLoader()));
        System.out.println("sameRuntimeClass="
                + (result == SharedApi.class));
        System.out.println("findClassCalls=" + child.localFindCalls);
        System.out.println("stringFromBootstrap="
                + (child.loadClass("java.lang.String")
                        .getClassLoader() == null));

        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(child);
            System.out.println("tcclIsChild="
                    + (thread.getContextClassLoader() == child));
            System.out.println("tcclDefinesSharedApi="
                    + (result.getClassLoader() == child));
        } finally {
            thread.setContextClassLoader(previous);
        }
    }
}
```

Chạy bằng **JDK 17 trở lên**:

```bash
javac RequestedVsDefiningLoaderDemo.java
java RequestedVsDefiningLoaderDemo
```

Stdout của chương trình khi chạy bình thường:

```text
requestedIsDefining=false
sameRuntimeClass=true
findClassCalls=0
stringFromBootstrap=true
tcclIsChild=true
tcclDefinesSharedApi=false
```

Giải thích từng kết quả:

1. `requestedIsDefining=false`: mặc dù gọi `child.loadClass(...)`, `SharedApi` vẫn do loader ứng dụng (parent trong ví dụ) định nghĩa.
2. `sameRuntimeClass=true`: giá trị trả về chính là `RequestedVsDefiningLoaderDemo.SharedApi.class`, không phải “bản copy” của class.
3. `findClassCalls=0`: parent đã xử lý thành công nên loader con không cần thử định nghĩa ở `findClass`.
4. `stringFromBootstrap=true`: ngay cả khi child nhận yêu cầu, `java.lang.String` được bootstrap loader định nghĩa.
5. `tcclIsChild=true` cho biết **thread context class loader** đã được đặt thành child, nhưng `tcclDefinesSharedApi=false` vì đổi TCCL không làm thay đổi defining loader của class đã nạp.

Phần `finally` khôi phục TCCL để tránh rò ngữ cảnh sang những thao tác khác trên cùng thread, nhất là khi dùng thread pool.

### Điều gì xảy ra nếu gọi một tên không có ở parent?

Vì `ProbeLoader.findClass` chỉ tăng bộ đếm rồi ném `ClassNotFoundException`, yêu cầu như `child.loadClass("example.Missing")` sẽ đi hết parent, thử `findClass` ở child, rồi thất bại. Khi đó `localFindCalls` tăng lên. Với tên `SharedApi`, parent tìm thấy trước nên nhánh `findClass` **không** chạy.

Vì vậy, **có gọi `loadClass` không đồng nghĩa có gọi `defineClass`**; hoạt động cục bộ chỉ xảy ra khi thuật toán nạp chọn tới nơi định nghĩa lớp.

## 5. Khi nào loader được yêu cầu cũng chính là defining loader?

Để thấy trường hợp ngược lại, ta có thể tạo một class chỉ tồn tại trong thư mục JAR/classes riêng của plugin, rồi yêu cầu `URLClassLoader` nạp nó. Khi parent không tìm được, `URLClassLoader` tìm bytecode qua URL và tự định nghĩa class.

Chương trình sau dùng `JavaCompiler` của JDK để tạo class thử nghiệm tạm trong một thư mục riêng, sau đó dùng `URLClassLoader` với parent là platform loader. Không cần tải thư viện bên ngoài.

Lưu `LocalDefinitionDemo.java`:

```java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public class LocalDefinitionDemo {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("loader-definition-");
        Path source = directory.resolve("sample/PluginType.java");
        Path compiled = directory.resolve("sample/PluginType.class");

        try {
            Files.createDirectories(source.getParent());
            Files.writeString(source,
                    "package sample; public class PluginType {}");

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new IllegalStateException("A JDK is required");
            }
            int exit = compiler.run(
                    null, null, null,
                    "-d", directory.toString(), source.toString());
            if (exit != 0) {
                throw new IllegalStateException("Compilation failed");
            }

            URL url = directory.toUri().toURL();
            try (URLClassLoader pluginLoader = new URLClassLoader(
                    new URL[]{url},
                    ClassLoader.getPlatformClassLoader())) {

                Class<?> pluginType = pluginLoader.loadClass(
                        "sample.PluginType");
                Class<?> again = pluginLoader.loadClass(
                        "sample.PluginType");

                System.out.println("requestedIsDefining="
                        + (pluginType.getClassLoader() == pluginLoader));
                System.out.println("sameClassOnSecondRequest="
                        + (pluginType == again));
                System.out.println("parentDefinesPlugin="
                        + (pluginType.getClassLoader()
                                == ClassLoader.getPlatformClassLoader()));
            }
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(compiled);
            Files.deleteIfExists(directory.resolve("sample"));
            Files.deleteIfExists(directory);
        }
    }
}
```

Chạy:

```bash
javac LocalDefinitionDemo.java
java LocalDefinitionDemo
```

Stdout khi biên dịch thử nghiệm thành công:

```text
requestedIsDefining=true
sameClassOnSecondRequest=true
parentDefinesPlugin=false
```

Giải thích:

- Platform loader không có class `sample.PluginType` sinh ra trong thư mục tạm.
- `URLClassLoader` tiếp tục tìm trong URL của nó, đọc class file và định nghĩa lớp: lần này **requested loader = defining loader**.
- Gọi `loadClass` lần nữa với cùng loader và cùng binary name trả về cùng định nghĩa `Class`; không tạo một class thứ hai trong cùng loader.
- `close()` trên `URLClassLoader` chỉ giải phóng tài nguyên như JAR/file handle; không làm cho class đã định nghĩa lập tức biến mất. JVM chỉ có thể unload khi điều kiện reachability và lifecycle phù hợp.

**Điều kiện chạy:** phải dùng JDK có hệ thống compiler, không chỉ runtime không có `ToolProvider.getSystemJavaCompiler()`. Chương trình sử dụng API Java 17+ và dọn các file thử nghiệm sau khi đóng loader.

## 6. Đừng suy initiating loader chỉ từ lời gọi Java
Trong JVMS, *initiating loader* là vai trò được JVM ghi nhận trong quá trình loading; nó có thể khác defining loader khi delegation thành công. Nhưng không thể đếm mọi object `ClassLoader` xuất hiện trên call stack rồi tuyên bố tất cả đều là initiating loader. `Class.getClassLoader()` chỉ cho biết **defining loader**; không có API trên `Class` liệt kê toàn bộ initiating loaders.
`findLoadedClass(name)` trong subclass kiểm tra những lớp mà JVM đã ghi nhận đối với loader này, bao gồm những lớp nó initiate thông qua delegation. Đừng xem kết quả ấy như danh sách các lớp child đã tự đọc bytecode và define. Đối với ví dụ ở §4–5, bằng chứng quan sát chắc chắn là **đối tượng `Class<?>` trả về và defining loader của nó**.

## 7. Hậu quả ở ranh giới API plugin
Danh tính class runtime phụ thuộc **(binary name, defining loader)**. Nếu hai child tự định nghĩa `api.Event` từ cùng bytecode, chúng tạo hai runtime types; cast hoặc `instanceof` xuyên ranh giới có thể thất bại. Nếu cả hai nhờ parent tải cùng `api.Event`, chúng nhận một kiểu chung.
Khi thiết kế plugin, đặt API/SPI chung ở loader mà host và child đều có thể sử dụng; triển khai/plugin dependencies được nạp riêng khi cần. Đặc biệt kiểm tra các kiểu trong tham số và giá trị trả về: JVM có thể áp đặt *loading constraints* lúc linking. Hai lớp cùng chuỗi `package` nhưng khác defining loader cũng không mặc nhiên thuộc cùng runtime package, nên package-private access không tự được chia sẻ.
Trong debug, so `Class<?> == Class<?>` và `getClassLoader()` ở hai phía; việc tên class hay đường dẫn JAR giống nhau chưa đủ chứng minh type identity.

## 8. Các API dễ bị nhầm là defining loader

| API/thuộc tính | Thực sự cho biết điều gì |
| --- | --- |
| `requestedLoader.loadClass(name)` | Gửi yêu cầu tới loader này, có thể delegate; kết quả là `Class<?>` |
| `Class.forName(name, false, requestedLoader)` | Yêu cầu tìm lớp bằng loader được chỉ định mà không chủ động initialize; defining loader có thể khác |
| `clazz.getClassLoader()` | **Defining loader** của runtime `clazz`; `null` với bootstrap |
| `clazz.getModule()` | Module runtime của class; module không thay thế câu hỏi defining loader |
| `ClassLoader.getSystemClassLoader()` | System/application loader theo môi trường; không phải “loader của mọi class” |
| `Thread.currentThread().getContextClassLoader()` | Context loader do thread mang theo; hữu ích cho discovery SPI/resources nhưng không tự nói class hiện tại do ai định nghĩa |
| `ClassLoader.getParent()` | Parent được cấu hình cho delegation theo API; không phải bằng chứng parent đã định nghĩa class cụ thể |
| `clazz.getProtectionDomain().getCodeSource()` | Thông tin nguồn code khi có sẵn, có thể `null`; không phải danh tính loader |

Đặc biệt, việc đổi **thread context class loader (TCCL)** không đổi defining loader của bất kỳ class đã định nghĩa trước đó. TCCL chỉ ảnh hưởng các API hoặc thư viện **chủ động chọn** context loader khi tìm lớp, resource hoặc service provider.

Ví dụ minh họa ngắn:

```java
ClassLoader previous = Thread.currentThread().getContextClassLoader();
try {
    Thread.currentThread().setContextClassLoader(pluginLoader);
    // API discovery có thể chọn TCCL để tra provider trong plugin.
} finally {
    Thread.currentThread().setContextClassLoader(previous);
}
```

Biến `pluginLoader` và các API discovery cần được chuẩn bị bởi ứng dụng; đoạn này chỉ minh họa mẫu khôi phục TCCL, không phải chương trình độc lập.

## 9. Khi chính sách lookup bị thay đổi
Một custom **child-first** loader có thể ưu tiên bytes riêng cho namespace plugin, nhưng kết quả vẫn phải được kiểm tra qua `result.getClassLoader()`: một số tên có thể delegate về parent, số khác do child định nghĩa. Chỉ chọn child-first cho namespace cần cô lập, bảo toàn core JDK types và shared API. Việc override `loadClass` còn đòi hỏi kiểm soát cache, khóa `getClassLoadingLock`, resolution và các ràng buộc JVM.

## 10. Chẩn đoán thực tế khi loader được yêu cầu khác loader định nghĩa

Khi thấy `ClassCastException`, `NoSuchMethodError`, `ServiceConfigurationError` hoặc behavior lạ trong plugin/container, có thể đi theo trình tự:

1. **Ghi chính xác binary name** đang được yêu cầu, đối tượng loader mà code gọi và đường dẫn code gây lỗi.
2. **Lấy đối tượng Class trả về** nếu quá trình nạp thành công. In `clazz.getName()` và `clazz.getClassLoader()`; nếu so sánh với một kiểu kỳ vọng, in loader của **cả hai bên**.
3. **Kiểm tra quan hệ parent và chính sách delegation.** Một `ChildLoader` có thể đang nhận bản API/lib của cha; loader cha không tự đọc được toàn bộ class riêng của con.
4. **Xem JAR/module thực sự chứa bytecode** tại runtime. `CodeSource` khi không `null` có thể hỗ trợ xác minh; một class cũng có thể đến từ module runtime không biểu diễn thành URL JAR.
5. **Log việc chọn lớp ở custom loader**, chú ý thời điểm `loadClass` nhận yêu cầu, khi nào delegate lên parent, khi nào `findClass` được gọi và khi nào `defineClass` thành công.
6. **Đọc toàn bộ cause của lỗi.** `ClassNotFoundException` thường gắn với API tìm lớp theo tên; `NoClassDefFoundError` có thể do thiếu phụ thuộc lúc liên kết/khởi tạo hoặc class đã khởi tạo lỗi; `LinkageError` có thể chỉ ra xung đột quan hệ loader.
7. **Nếu vấn đề xảy ra khi reload plugin**, so sánh defining loader cũ/mới và các tham chiếu còn giữ class/instance của loader cũ.
8. **Nếu ServiceLoader không tìm được provider**, phân biệt loader đang tìm provider, loader định nghĩa SPI và loader định nghĩa provider. Provider phải thực thi **chính** interface SPI host dùng, không phải một bản cùng tên được plugin tự định nghĩa.

Mẫu phương thức hỗ trợ có thể đặt trong class ứng dụng:

```java
static void describe(Class<?> type) {
    ClassLoader defining = type.getClassLoader();
    System.out.println("name=" + type.getName());
    System.out.println("definingLoader=" + defining);
    System.out.println("module=" + type.getModule().getName());

    java.security.ProtectionDomain pd = type.getProtectionDomain();
    java.security.CodeSource src = pd == null ? null : pd.getCodeSource();
    System.out.println("codeSource="
            + (src == null ? "<unknown>" : src.getLocation()));
}
```

Đây là **phương thức hỗ trợ**, không phải chương trình có `main`. `getModule().getName()` có thể trả về `null` cho unnamed module; `CodeSource` cũng có thể không tồn tại. String của class loader trong log hữu ích nhưng không bảo đảm là định danh duy nhất, ổn định qua các lần chạy.

Có thể kết hợp với công cụ JVM khi môi trường cho phép:

```bash
java -Xlog:class+load=info RequestedVsDefiningLoaderDemo
```

Tùy JVM và mức log, nguồn bytecode có thể hiện trong log; **đừng đồng nhất “source: file X” với defining loader**. Với tiến trình hỗ trợ attach, `jcmd <pid> VM.classloaders` giúp xem cấu trúc class loader; tham số và khả năng hỗ trợ phụ thuộc phiên bản JVM.

## 11. Những nhầm lẫn thường gặp

**“Gọi `child.loadClass` tức `child` định nghĩa class.”** Sai khi class do parent tìm thấy và trả về.

**“`getClassLoader()` trả về loader gần nhất trong call stack.”** Sai: nó trả về defining loader của runtime class.

**“Mọi class đều có ClassLoader object.”** Sai: `String.class.getClassLoader() == null` vì bootstrap loader không biểu diễn bằng một đối tượng Java như các loader thông thường.

**“Hai loader hỏi cùng tên sẽ nhận hai bản copy.”** Sai nếu cả hai delegate tới cùng một định nghĩa ở cha.

**“Hai loader đọc cùng một `.class` từ đĩa thì phải cùng kiểu.”** Sai nếu mỗi loader tự định nghĩa bytecode; runtime type identity còn xét defining loader.

**“TCCL bằng plugin loader tức SPI do plugin loader định nghĩa.”** Sai: TCCL có thể được dùng để tìm provider; SPI thường cần do loader chung định nghĩa.

**“Nhìn `getParent()` đủ để suy ra nơi định nghĩa mọi class.”** Sai: delegation có thể được tùy biến; phải xác minh kết quả thực tế.

**“Đổi loader sau đó có thể đổi defining loader của Class đang tồn tại.”** Sai: một `Class<?>` đã có defining loader gắn với định nghĩa runtime. Muốn định nghĩa mới, phải tạo được định nghĩa mới qua loader phù hợp.

## 12. Bài tập tự kiểm tra

**Câu 1.** `PluginLoader` gọi `loadClass("api.Plugin")`, parent đã định nghĩa `api.Plugin` và trả về. Ai là requested loader, ai là defining loader?

**Trả lời:** Requested loader là `PluginLoader`; defining loader là parent. `clazz.getClassLoader()` cho biết parent.

**Câu 2.** Hai plugin loader cùng delegate `api.Plugin` lên parent. Hai kết quả có phải cùng type identity không?

**Trả lời:** Có, nếu parent trả về cùng định nghĩa runtime `Class`. Khi ấy hai plugin có thể dùng chung SPI.

**Câu 3.** Hai loader tự định nghĩa `internal.Node` từ cùng file `.class`. Có thể cast instance của bản A sang bản B không?

**Trả lời:** Không. Runtime type identity khác vì defining loader khác dù binary name và bytecode giống nhau.

**Câu 4.** `Thread.currentThread().setContextClassLoader(pluginLoader)` có khiến một lớp đã được app loader định nghĩa chuyển sang plugin loader không?

**Trả lời:** Không. TCCL ảnh hưởng việc chọn loader ở một số API từ thời điểm đó; nó không thay đổi defining loader của class đã định nghĩa.

**Câu 5.** Khi gọi `child.loadClass("java.lang.String")`, `getClassLoader()` trả `null`. Có nghĩa việc loadClass thất bại không?

**Trả lời:** Không. `null` biểu thị bootstrap loader đã định nghĩa `String`.

**Câu 6.** App báo dùng phiên bản thư viện khác dự kiến dù plugin đã tạo `URLClassLoader` riêng. Kiểm tra đầu tiên là gì?

**Trả lời:** In defining loader và vị trí bytecode của class thư viện được nạp thực tế; kiểm tra có phải parent-first đã chọn bản ở loader cha trước hay không.

## 13. Kết luận

Để hiểu đúng một lời gọi nạp lớp, luôn tách hai câu hỏi: **“Loader nào được yêu cầu xử lý tên lớp?”** và **“Loader nào thực sự định nghĩa class trả về?”**. Parent delegation cho phép hai đáp án khác nhau một cách bình thường. Danh tính kiểu runtime, khả năng ép kiểu qua plugin và việc cô lập phụ thuộc được quyết định bởi **defining loader**, không đơn thuần bởi đối tượng mà ứng dụng đã gọi `loadClass`.

### Tài liệu chuẩn để đối chiếu

- Java SE 17 `ClassLoader` API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17 `Class` API (`getClassLoader`): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getClassLoader()
- Java Virtual Machine Specification 17, §5.3 *Creation of Classes and Interfaces*: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- Java Virtual Machine Specification 17, §5.3.4 *Loading Constraints*: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3.4


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="parent-delegation-007">Khả năng nhìn thấy từ loader con tới loader cha</a>

<details>
<summary>Xem chi tiết</summary>

# Khả năng nhìn thấy từ loader con tới loader cha

## 1. Ý nghĩa của “nhìn thấy” trong cơ chế ủy quyền

Trong Java, ClassLoader không chỉ có nhiệm vụ đọc file `.class`. Nó quyết định **loader nào được phép tham gia tìm một lớp hoặc tài nguyên** và **loader nào thực sự định nghĩa runtime class**. Với mô hình **parent-first delegation** thông thường, một loader con thử nhờ loader cha tìm lớp trước. Nếu cha không tìm được, loader con mới tìm trong nguồn riêng của nó.

Điều quan trọng nhất của bài học là tính **bất đối xứng**:

- **Con → cha:** loader con thường nhìn thấy các lớp mà loader cha hoặc tổ tiên của cha tìm được thông qua cơ chế ủy quyền.
- **Cha → con:** loader cha **không tự động tìm** trong JAR, thư mục hoặc các lớp mà riêng loader con quản lý.
- “Nhìn thấy” không nhất thiết là “tự định nghĩa”: loader con có thể **trả về đúng class do cha định nghĩa**, không tạo bản sao.
- Cơ chế ủy quyền về việc tìm class **không bỏ qua** Java access control, tính toàn vẹn runtime type hoặc quy tắc module.

Đây là mô hình của `ClassLoader.loadClass` mặc định và các subclass tuân theo delegation. Một ClassLoader tùy biến có thể override `loadClass` để thay đổi quy tắc tìm kiếm; JPMS còn có những đường liên kết module đặc biệt. Vì vậy đừng coi quan hệ cha–con là một luật về tầm nhìn bất biến trong mọi ClassLoader của mọi framework.

## 2. Mô hình trực quan

Ví dụ JVM có loader ứng dụng (cha) và một `URLClassLoader` tải JAR plugin (con):

~~~text
                     Bootstrap ClassLoader
                               ▲
                               │
                     Platform ClassLoader
                               ▲
                               │
                     Application Loader
                     (cha / parent)
                       ├── demo.api.Greeter
                       └── demo.host.Host
                               ▲
                  loadClass()   │ ủy quyền lên cha
                               │
                     URLClassLoader
                     (con / child)
                       └── hello-plugin.jar
                             ├── demo.plugin.HelloGreeter
                             └── plugin-message.txt
~~~

Giả sử chỉ parent có `demo.api.Greeter` và chỉ child có `demo.plugin.HelloGreeter`:

| Yêu cầu | Kết quả theo parent-first thông thường | Lớp do ai định nghĩa? |
|---|---|---|
| `child.loadClass("demo.api.Greeter")` | Child nhờ parent, parent tìm thấy | **Parent** |
| `child.loadClass("demo.plugin.HelloGreeter")` | Parent không thấy; child tìm trong JAR | **Child** |
| `parent.loadClass("demo.api.Greeter")` | Parent tìm lớp thuộc classpath của mình | **Parent** |
| `parent.loadClass("demo.plugin.HelloGreeter")` | Không thấy nếu JAR plugin chỉ thuộc child | Không có kết quả, ném `ClassNotFoundException` |
| `child.getResource("plugin-message.txt")` | Child tìm thấy resource riêng sau khi parent không thấy | URL tài nguyên thuộc JAR plugin |
| `parent.getResource("plugin-message.txt")` | Không tìm thấy resource chỉ có trong child | Trả về `null` |

**Chiều của mũi tên là chiều ủy quyền truy vấn từ con lên cha.** Java không có cơ chế mặc định yêu cầu cha tự duyệt tất cả các loader con đang tồn tại. Parent còn có thể không giữ bất cứ danh sách child nào.

“Cha không thấy con” **không** có nghĩa là mã được parent định nghĩa không bao giờ sử dụng class từ child. Host hoàn toàn có thể giữ một tham chiếu tới child loader, gọi `child.loadClass(...)` hoặc nhận instance plugin qua interface chung. Khi đó host **chủ động gọi tới loader con**, chứ parent không tự tìm ngược bằng `parent.loadClass(...)`.

## 3. Parent-first tìm class ra sao?

Về mặt ý tưởng, cài đặt mặc định của `ClassLoader.loadClass(String, boolean)` thực hiện:

1. Kiểm tra class đã được loader nhận biết qua `findLoadedClass(name)`.
2. Nếu chưa, **ủy quyền cho parent** bằng `parent.loadClass(name, false)`; nếu không có parent thì thử cơ chế bootstrap.
3. Chỉ khi cha/bộ nạp bootstrap báo không tìm thấy thì gọi `findClass(name)` của chính loader.
4. Nếu caller yêu cầu `resolve == true`, thực hiện resolution cần thiết cho kết quả.

Một cách mô tả giả mã:

~~~java
// GIẢ MÃ: không phải một method chạy độc lập.
Class<?> loadClassConceptually(String name) throws ClassNotFoundException {
    Class<?> already = findLoadedClass(name);
    if (already != null) {
        return already;
    }

    try {
        return delegateToParentOrBootstrap(name);
    } catch (ClassNotFoundException notFoundByParent) {
        return findClass(name);
    }
}
~~~

Đây là **mô hình logic**, không chép lại nguyên văn implementation thật. Bản thật có xử lý đồng bộ hóa, exception, resolution và nhiều chi tiết khác.

Các điểm dễ nhầm:

- `loadClass(...)` có thể trả về class do **parent định nghĩa**, dù chính child là object nhận lời gọi.
- Mỗi lần gọi `loadClass` **không nhất thiết** đọc lại bytecode; loader và JVM ghi nhớ những class đã được nạp.
- Phép kiểm tra parent-first áp dụng cho class **được yêu cầu tìm**; nó không bắt JVM khởi tạo tất cả class của parent trước.
- `loadClass(String)` thường **không kích hoạt class initialization**. Muốn chủ động initialize có thể dùng `Class.forName(name, true, loader)` sau khi hiểu nhu cầu.
- `ClassLoader` mặc định tuân parent-first; một framework có thể dùng child-first hoặc quy tắc dành riêng cho package/module.

## 4. Ví dụ Java 17+ có thể chạy: host, API và plugin

Ta sẽ tạo chương trình thực nghiệm chứng minh bốn kết quả: **child nhìn thấy API từ parent**, **parent không nhìn thấy implementation chỉ thuộc child**, **child nạp implementation và resource từ plugin JAR**, và **hai child riêng định nghĩa hai class cùng tên nhưng khác runtime type**.

### 4.1. Cấu trúc dự án

Tạo các thư mục và file theo đúng đường dẫn:

~~~text
visibility-demo/
├── host-src/
│   └── demo/
│       ├── api/
│       │   └── Greeter.java
│       └── host/
│           └── Host.java
├── plugin-src/
│   └── demo/
│       └── plugin/
│           └── HelloGreeter.java
└── plugin-resources/
    └── plugin-message.txt
~~~

Các thư mục `build/host`, `build/plugin` và file `build/hello-plugin.jar` sẽ được tạo ở bước biên dịch. **Không đưa plugin JAR vào classpath của lệnh chạy host**, vì mục tiêu là cho host và plugin ở hai loader khác nhau.

### 4.2. Hợp đồng chung trong parent

File **`host-src/demo/api/Greeter.java`**:

~~~java
package demo.api;

public interface Greeter {
    String greet(String name);
}
~~~

Interface này được biên dịch vào classpath của host. Plugin được phép phụ thuộc `Greeter` lúc **biên dịch**, nhưng lúc **chạy** sẽ resolve interface qua parent loader.

### 4.3. Lớp triển khai chỉ thuộc child

File **`plugin-src/demo/plugin/HelloGreeter.java`**:

~~~java
package demo.plugin;

import demo.api.Greeter;

public final class HelloGreeter implements Greeter {
    public HelloGreeter() {
    }

    @Override
    public String greet(String name) {
        return "Hello, " + name + "!";
    }
}
~~~

Đây là `public` class với `public` constructor không tham số, nên host có thể tạo instance bằng reflection. Plugin có thể gọi phương thức interface do parent định nghĩa vì plugin loader có thể nhờ parent tải `demo.api.Greeter`.

File văn bản **`plugin-resources/plugin-message.txt`**:

~~~text
Only the plugin loader sees this resource by default.
~~~

Tệp này chỉ nằm trong plugin JAR, dùng để kiểm tra rằng tìm **resource** cũng có chiều ủy quyền và không tự quay ngược từ parent xuống child.

### 4.4. Host chủ động tạo loader con

File **`host-src/demo/host/Host.java`**:

~~~java
package demo.host;

import demo.api.Greeter;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Host {
    private Host() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: Host <path-to-plugin-jar>");
        }

        Path jar = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(jar)) {
            throw new IllegalArgumentException("Missing JAR: " + jar);
        }

        ClassLoader parent = Greeter.class.getClassLoader();
        URL[] urls = {jar.toUri().toURL()};

        try (URLClassLoader child = new URLClassLoader(urls, parent);
             URLClassLoader anotherChild = new URLClassLoader(urls, parent)) {

            Class<?> apiViaChild =
                    child.loadClass("demo.api.Greeter");
            System.out.println("Child sees parent API: "
                    + (apiViaChild == Greeter.class));

            boolean parentSeesPlugin;
            try {
                parent.loadClass("demo.plugin.HelloGreeter");
                parentSeesPlugin = true;
            } catch (ClassNotFoundException ignored) {
                parentSeesPlugin = false;
            }
            System.out.println("Parent finds child-only class: "
                    + parentSeesPlugin);

            Class<?> implementation =
                    child.loadClass("demo.plugin.HelloGreeter");
            System.out.println("Plugin defined by child: "
                    + (implementation.getClassLoader() == child));

            Greeter instance = (Greeter) implementation
                    .getConstructor()
                    .newInstance();
            System.out.println("Call through shared API: "
                    + instance.greet("Java"));

            System.out.println("Child finds plugin resource: "
                    + (child.getResource("plugin-message.txt") != null));
            System.out.println("Parent finds plugin resource: "
                    + (parent.getResource("plugin-message.txt") != null));

            Class<?> implementationTwo =
                    anotherChild.loadClass("demo.plugin.HelloGreeter");
            System.out.println("Two child classes identical: "
                    + (implementation == implementationTwo));

            System.out.println("Both implement shared API: "
                    + (Greeter.class.isAssignableFrom(implementation)
                    && Greeter.class.isAssignableFrom(implementationTwo)));
        }
    }
}
~~~

**Vì sao chọn `Greeter.class.getClassLoader()` làm parent?** Vì đây là loader **định nghĩa interface mà host và plugin cần chia sẻ**. Trong chương trình classpath mẫu, nó chính là application/system loader; trong framework phức tạp, loader của interface có thể khác system loader. Lấy parent theo interface giúp thiết kế đúng với mục tiêu chia sẻ runtime type.

Ta tạo **hai** `URLClassLoader` với cùng một đường dẫn JAR nhằm chứng minh hai loader con độc lập vẫn có thể định nghĩa hai class triển khai mang **cùng tên nhị phân** nhưng không phải cùng runtime class.

## 5. Biên dịch và chạy thực nghiệm

Yêu cầu JDK **17 trở lên** với `javac`, `jar` và `java`. Mở terminal tại thư mục `visibility-demo/` đã chứa đủ bốn file nguồn/tài nguyên.

**Bash trên Linux/macOS hoặc Git Bash:**

~~~bash
mkdir -p build/host build/plugin

javac -encoding UTF-8 \
  -d build/host \
  host-src/demo/api/Greeter.java \
  host-src/demo/host/Host.java

javac -encoding UTF-8 \
  -cp build/host \
  -d build/plugin \
  plugin-src/demo/plugin/HelloGreeter.java

jar --create --file build/hello-plugin.jar \
  -C build/plugin . \
  -C plugin-resources .

java -cp build/host demo.host.Host build/hello-plugin.jar
~~~

**Windows PowerShell:**

~~~powershell
New-Item -ItemType Directory -Force build/host,build/plugin | Out-Null

javac -encoding UTF-8 -d build/host host-src/demo/api/Greeter.java host-src/demo/host/Host.java

javac -encoding UTF-8 -cp build/host -d build/plugin plugin-src/demo/plugin/HelloGreeter.java

jar --create --file build/hello-plugin.jar -C build/plugin . -C plugin-resources .

java -cp build/host demo.host.Host build/hello-plugin.jar
~~~

Lệnh đóng gói giữ `demo/plugin/HelloGreeter.class` và `plugin-message.txt` trong plugin JAR, còn `Greeter.class` ở `build/host`. Kiểm tra nếu cần:

~~~bash
jar --list --file build/hello-plugin.jar
~~~

Trong output lệnh liệt kê, đảm bảo có ít nhất hai entry `demo/plugin/HelloGreeter.class` và `plugin-message.txt`; JAR có thể có thêm `META-INF/MANIFEST.MF` và các entry thư mục.

**Stdout của chương trình trên classpath chuẩn đã chỉ định:**

~~~text
Child sees parent API: true
Parent finds child-only class: false
Plugin defined by child: true
Call through shared API: Hello, Java!
Child finds plugin resource: true
Parent finds plugin resource: false
Two child classes identical: false
Both implement shared API: true
~~~

Không in tên class loader nội bộ kiểu `jdk.internal.loader.ClassLoaders$AppClassLoader@...` vì string này không ổn định giữa các JVM hay các lần chạy. Các phép so sánh `==` tạo ra kết quả cụ thể, có thể kiểm tra.

## 6. Phân tích đường đi cho từng lời gọi

### 6.1. Child yêu cầu class của parent

~~~java
Class<?> apiViaChild = child.loadClass("demo.api.Greeter");
~~~

Child kiểm tra `demo.api.Greeter` rồi hỏi parent. Parent đã nhìn thấy `Greeter` từ `build/host`, nên trả class do chính nó định nghĩa. Child nhận và trả **cùng đối tượng `Class<?>`**:

~~~java
apiViaChild == Greeter.class  // true
~~~

Không có bản sao `Greeter` được định nghĩa bởi child. Nếu khai báo rằng “gọi `child.loadClass` tức class thuộc child” thì sẽ giải thích sai cả mã host/plugin và khả năng ép kiểu qua API.

### 6.2. Parent yêu cầu class chỉ nằm trong child

~~~java
parent.loadClass("demo.plugin.HelloGreeter");
~~~

JAR plugin **không có trong classpath của parent**, nên parent và tổ tiên của nó không tìm thấy `HelloGreeter`. Parent không có bước chuẩn “thử mọi child của tôi” và ném `ClassNotFoundException`.

Kết luận chỉ áp dụng cho cấu hình ví dụ. Nếu cùng JAR plugin được đặt thêm vào application classpath, hoặc parent được cấu hình thêm nguồn tìm class, parent **có thể tìm thấy** plugin. Tức là chiều nhìn thấy phụ thuộc **cả quan hệ loader lẫn vị trí bytecode**.

### 6.3. Child yêu cầu class triển khai từ JAR riêng

~~~java
Class<?> implementation =
        child.loadClass("demo.plugin.HelloGreeter");
~~~

Child nhờ parent; parent không tìm thấy, child tiếp tục dùng chức năng `findClass` của `URLClassLoader` đọc class từ JAR. Vì thế:

~~~java
implementation.getClassLoader() == child  // true
~~~

Trong quá trình JVM liên kết class này, interface `demo.api.Greeter` được tìm theo parent; do đó object plugin có thể được ép kiểu sang `Greeter` và host gọi `greet("Java")`. Hai bên không cần cùng loader **cho mọi class**, mà cần chia sẻ đúng **cùng runtime interface** tại ranh giới giao tiếp.

### 6.4. Resource có cùng chiều tra cứu mặc định

~~~java
child.getResource("plugin-message.txt")   // URL khác null
parent.getResource("plugin-message.txt")  // null trong ví dụ
~~~

`URLClassLoader` triển khai `getResource` theo parent-first: hỏi parent trước, rồi tìm URL của mình. Nhưng parent tự truy vấn **không biết các URL của child** nên không tìm được resource của plugin. Tương tự, `getResources` có thể tập hợp resource từ parent và child theo quy tắc triển khai, và `ServiceLoader` dùng resource `META-INF/services/...` để tìm provider khi chạy theo cơ chế classpath.

Đối với tài nguyên của **named modules**, còn có quy tắc đóng/mở package và cơ chế module; không giả định mọi resource có thể đọc được từ mọi loader.

### 6.5. Hai child, hai class triển khai độc lập

~~~java
Class<?> implementationTwo =
        anotherChild.loadClass("demo.plugin.HelloGreeter");
~~~

`child` và `anotherChild` có cùng JAR URL và parent, nhưng mỗi loader **tự định nghĩa** `demo.plugin.HelloGreeter`. Với JVM, runtime class identity phụ thuộc vào **tên nhị phân cộng với defining loader**:

~~~text
(demo.plugin.HelloGreeter, child)
    !=
(demo.plugin.HelloGreeter, anotherChild)
~~~

Vì vậy `implementation == implementationTwo` trả `false`. Dù vậy cả hai implementation đều sử dụng `demo.api.Greeter` do **cùng parent** định nghĩa, nên `Greeter.class.isAssignableFrom(...)` cho cả hai trả `true`.

Đây là một kiểu cách ly hữu ích cho plugin: implementation giữa hai phiên bản có thể là các runtime type khác nhau, trong khi host vẫn giao tiếp qua cùng API.

## 7. Nhìn thấy class khác với được phép truy cập thành viên

Ngay cả khi child tìm được class của parent, Java vẫn kiểm tra quyền truy cập tới class, constructor, method và field:

- Thành viên `public` của một public API có thể được sử dụng nếu quy tắc module tương ứng cho phép.
- Thành viên `private` không trở thành truy cập được chỉ nhờ quan hệ loader cha–con.
- **Runtime package** không chỉ được xác định bởi tên package mà còn bởi **defining loader** (và trong hệ module còn có module). Hai class cùng chuỗi package nhưng được hai loader khác nhau định nghĩa **không đương nhiên có package-private access** vào nhau.
- Reflection cũng chịu kiểm soát quyền truy cập và JPMS `exports`/`opens`. Quan hệ parent không tự vượt qua `InaccessibleObjectException` hay `IllegalAccessException`.

Ví dụ: đặt class `demo.api.Helper` trong parent với package-private method, rồi thêm class cùng package `demo.api.PluginCode` vào child **không làm** hai class trở thành cùng runtime package. Cố truy cập trái phép có thể gây `IllegalAccessError` trong quá trình liên kết/thực thi, tùy trường hợp.

Khi xây dựng plugin, đặt hợp đồng chung ở **public exported API** theo thiết kế phù hợp; không trông chờ “đều nằm trong package `demo.api`” để chia sẻ thành viên mặc định.

## 8. Tại sao đôi khi parent vẫn có thể gọi mã ở child?

Một chương trình host vốn được parent nạp vẫn có thể làm việc với object plugin thông qua hai cơ chế:

**Cách 1 — Chủ động dùng loader con.** Ví dụ `Host` gọi `child.loadClass(...)`, phản xạ khởi tạo và chuyển đối tượng về `Greeter`. Đây chính là ví dụ runnable đã thực hiện. Class mà host trực tiếp tham chiếu trong source không nhất thiết phải import implementation plugin.

**Cách 2 — Truyền đối tượng qua giao diện chung.** Loader con tạo instance implement `Greeter`, rồi host nhận instance qua interface. Sau đó host không cần biết tên lớp triển khai. Một framework có thể kết hợp thêm `ServiceLoader.load(Greeter.class, child)` để tìm provider trong JAR plugin có service descriptor phù hợp.

**Thread Context ClassLoader (TCCL)** cũng là một phương thức *chọn loader tra cứu* theo ngữ cảnh. Một thư viện được loader cha định nghĩa có thể hỏi:

~~~java
ClassLoader lookup = Thread.currentThread().getContextClassLoader();
~~~

Nếu framework đặt TCCL thành child loader, thư viện có thể dùng loader đó để tìm class/resource thuộc plugin. Tuy nhiên, **TCCL không sửa quan hệ parent** và không biến `parent.loadClass(...)` thành việc tìm child. Nó chỉ cho phép code chủ động chọn một loader khác. Khi đặt TCCL tạm thời, phải khôi phục trong `finally` để tránh ảnh hưởng các tác vụ khác trên cùng thread.

## 9. Những trường hợp ngoại lệ và giới hạn của mô hình

### 9.1. Child-first loader

Một số plugin framework hoặc application server cài chiến lược **child-first**: thử nguồn riêng của child trước rồi mới hỏi parent (thường vẫn ngoại lệ cho các class quan trọng của JDK). Điều này thay đổi **ưu tiên** khi parent và child cùng có một tên class, không loại bỏ yêu cầu runtime type identity.

Child-first có thể khiến plugin dùng thư viện phụ thuộc phiên bản riêng nhưng cũng dễ dẫn tới `ClassCastException`, `LinkageError` hoặc `ServiceConfigurationError` nếu plugin tự định nghĩa cả API mà host đang dùng.

### 9.2. JPMS và module layers

Các module có quy tắc **readability**, `exports` và `opens` ngoài quan hệ loader. ClassLoader có thể được liên kết với named modules; việc resolve class giữa module không luôn tuân một sơ đồ “chỉ parent trước rồi mới child” đơn giản.

Đặc biệt, trong `ModuleLayer`, cùng tên class trong các module hoặc loader khác nhau có thể chịu các constraint riêng. Muốn biết code có thể truy cập class hay không, xem cả loader **và** khả năng đọc/export của module.

### 9.3. Bootstrap và loader `null`

`Class.getClassLoader() == null` thường có nghĩa class được **bootstrap loader** định nghĩa, không có nghĩa lớp “không được nạp”. Bootstrap loader thường là ancestor cuối cùng khi child loader sử dụng parent-first. Một loader có parent `null` không mặc nhiên thấy toàn bộ application classpath.

### 9.4. Class visibility không phải security boundary

ClassLoader phân chia namespace và nguồn tìm class; nó **không phải sandbox** cho plugin không tin cậy. Một plugin chạy cùng JVM có thể thực thi mã với quyền của tiến trình host. Nếu cần cách ly quyền truy cập hệ thống, sử dụng ranh giới tiến trình/container và cơ chế kiểm soát thích hợp.

## 10. Lỗi thường gặp khi suy luận về khả năng nhìn thấy

| Hiện tượng | Hiểu nhầm phổ biến | Phân tích đúng |
|---|---|---|
| `child.loadClass("demo.api.Greeter")` thành công | “Class này do child định nghĩa” | Có thể chính parent đã định nghĩa class, child chỉ ủy quyền |
| `parent.loadClass("demo.plugin.HelloGreeter")` thất bại | “Host không thể dùng plugin” | Host có thể giữ tham chiếu child và gọi `child.loadClass`, hoặc nhận plugin qua API |
| Child có một file `Greeter.class` riêng nhưng host cast thất bại | “Cùng FQCN thì là cùng class” | Nếu hai loader thực sự tự định nghĩa hai bản, runtime type khác nhau |
| `child.getResource(...)` trả resource của parent | “Resource đã được copy vào child” | Parent-first resource lookup có thể trả URL thuộc parent |
| Hai child cùng trỏ một JAR mà class khác nhau | “JVM nạp sai cùng một file” | Identity gồm tên class **và defining loader** |
| Class tìm được nhưng truy cập member lỗi | “Tìm thấy class nghĩa là có mọi quyền” | Java access control, runtime package và JPMS vẫn áp dụng |
| `ServiceLoader` không tìm được plugin | “Parent nên tự tìm provider bên child” | Truyền loader plugin rõ ràng hoặc cấu hình TCCL đúng cách |
| `ClassNotFoundException` và `NoClassDefFoundError` bị đánh đồng | “Đều chỉ là file class không tồn tại” | `ClassNotFoundException` thường từ yêu cầu tìm tên; `NoClassDefFoundError` có thể xảy ra khi liên kết/phụ thuộc ở runtime |
| Plugin dependency cùng tên thư viện với host | “Child luôn lấy thư viện của mình” | Parent-first thường ưu tiên bản ở parent; child-first có thể đổi ưu tiên và tạo xung đột |

## 11. Quy tắc thiết kế plugin theo visibility

1. **Định vị hợp đồng API:** Interface/DTO dùng để trao đổi giữa host và plugin nên được định nghĩa bởi loader có thể dùng chung, thường là loader của host.
2. **Chọn parent có chủ đích:** Nếu plugin cần gọi API host, dùng `SomeHostApi.class.getClassLoader()` làm parent (hoặc loader được chứng minh nhìn thấy API đó). Chọn platform loader sẽ thường **không** thấy classpath ứng dụng host.
3. **Giữ implementation ở nguồn riêng:** Plugin implementation và dependency riêng nên nằm trong child JAR URL; tránh vô tình đưa chúng lên classpath host khi muốn child định nghĩa.
4. **Xác minh bằng định danh thật:** So sánh `SomeClass.class.getClassLoader()`, `loaderA == loaderB`, `Class<?> == Class<?>` thay vì chỉ nhìn tên package hoặc in hash code.
5. **Quản lý vòng đời:** `URLClassLoader.close()` đóng nguồn tài nguyên, nhưng không buộc JVM lập tức unload class. Muốn giải phóng loader cũ phải dọn các tham chiếu tới class/object/loader, thread, callback, cache và tài nguyên plugin liên quan.
6. **Chẩn đoán cả resource:** Khi dùng `ServiceLoader`, kiểm tra `META-INF/services/...` và loader lookup. Khi dùng JPMS, kiểm tra cả cấu hình module.

## 12. Câu hỏi thực hành

**Câu 1.** Parent định nghĩa `com.app.Api`, child định nghĩa `com.plugin.Impl implements Api`. Lệnh `child.loadClass("com.app.Api")` trả về class do loader nào định nghĩa?

**Đáp án:** Thông thường là class do **parent** định nghĩa, nếu child tuân parent-first và parent nhìn thấy API. Việc dùng `child.loadClass` không thay đổi defining loader.

**Câu 2.** Tại sao `parent.loadClass("com.plugin.Impl")` ném `ClassNotFoundException` nhưng host vẫn tạo được instance `Impl`?

**Đáp án:** Parent không tìm ngược các class riêng của child. Host có thể gọi **`child.loadClass("com.plugin.Impl")`** rồi reflection tạo instance và ép sang interface chung do parent định nghĩa.

**Câu 3.** Hai `URLClassLoader` khác nhau cùng đọc một JAR, cùng nạp class có FQCN `com.plugin.Impl`. Hai kết quả `Class<?>` có bằng `==` không?

**Đáp án:** **Không** nếu mỗi loader thật sự tự định nghĩa class. Chúng là hai runtime type khác nhau. Trường hợp class được parent định nghĩa và hai child chỉ ủy quyền thì có thể **bằng nhau**.

**Câu 4.** Đặt TCCL bằng child có thay đổi `parent.loadClass(...)` để nó tự tìm từ child không?

**Đáp án:** **Không.** TCCL chỉ là tham chiếu loader do thread giữ để mã gọi chủ động dùng. Nó không đảo hướng ủy quyền của `parent.loadClass`.

**Câu 5.** Plugin cùng package name với class host có thể tự đọc package-private member của class host không?

**Đáp án:** Không mặc nhiên. Hai class được các loader khác nhau định nghĩa thuộc **runtime package khác nhau**; còn phải xét thêm ràng buộc module và quyền truy cập.

## 13. Tài liệu chính thức

- [Java SE 17 — ClassLoader: delegation, loadClass, getResource](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html)
- [Java SE 17 — URLClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html)
- [Java SE 17 — Class.getClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getClassLoader())
- [Java SE 17 — ServiceLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html)
- [Java Virtual Machine Specification 17 — Loading, Linking, and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html)

**Ghi nhớ:** Với parent-first delegation, **loader con có thể nhờ cha tìm lớp, nhưng cha không tự tìm xuống con**. Child dùng chung API do cha định nghĩa và vẫn tự định nghĩa implementation từ JAR riêng; bản sắc runtime class phụ thuộc **tên lớp + defining loader**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="parent-delegation-008">Vì sao loader cha không tự tìm phụ thuộc của loader con</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao loader cha không tự tìm phụ thuộc của loader con?

## 1. Vấn đề cốt lõi: quan hệ cha–con không tạo quyền nhìn hai chiều

Trong cơ chế nạp lớp Java thông thường, khi một **child class loader** cần một lớp, nó thường hỏi **parent class loader** trước. Nếu parent không tìm được, child mới thử tìm trong nguồn bytecode riêng của mình. Đây là **parent-first delegation**.

Chiều ngược lại **không tự xảy ra**: khi một lớp do parent định nghĩa cần một kiểu phụ thuộc, JVM dùng ngữ cảnh nạp lớp của chính lớp parent đó để resolve kiểu. JVM không đi tìm trong tất cả những child loader có thể tồn tại. Vì vậy:

> **Child thường nhìn được lớp của parent qua delegation; parent không mặc nhiên nhìn được lớp chỉ do child cung cấp.**

Một ví dụ phổ biến là framework nằm trên application classpath, còn plugin được mở từ thư mục/JAR riêng bằng `URLClassLoader` con. Framework có thể biết interface chung `PluginApi`, nhưng nếu từ framework gọi trực tiếp một class cụ thể chỉ nằm trong plugin JAR, parent loader thường không tìm thấy nó. Điều này vẫn đúng khi plugin đã được child loader nạp thành công.

**Không nhầm hai vấn đề:**

1. **Visibility / lookup:** loader đang được hỏi có tìm và định nghĩa/nhận được lớp theo binary name không?
2. **Type identity / linkage:** nếu lớp có thể được nạp, lớp nào thực sự được định nghĩa bởi loader nào, và kiểu đó có tương thích ở các ranh giới lời gọi không?

Để giải thích vì sao parent không tự tìm dependency của child, trước hết phải hiểu **hướng tìm kiếm** và **loader được JVM dùng khi resolve tham chiếu từ bytecode**.

## 2. Hướng ủy quyền và giới hạn của parent
`ClassLoader.loadClass` mặc định theo trình tự `findLoadedClass` → parent/bootstrap → `findClass` của child. Child nhờ parent tìm API chung; parent không tự có danh sách mọi child, nên `parent.loadClass("plugin.Helper")` không thể nhìn vào nguồn riêng của child trừ khi parent đã được cấu hình một đường lookup khác.
```text
Child → Parent: có thể trả Class của parent
Parent → Child: không có cạnh reverse-delegation mặc định
```
Lưu ý parent-first là chính sách mặc định, không bắt buộc mọi custom loader hay loader gắn với JPMS phải dùng đúng cùng thuật toán. Quan trọng hơn, như §3 giải thích, **bytecode của class parent khi resolve phụ thuộc vẫn dùng loader định nghĩa class parent**, kể cả khi child là caller.

## 3. Điểm quan trọng nhất: phụ thuộc của một lớp được resolve theo defining loader

Giả sử bytecode của lớp `Host` có câu lệnh:

~~~java
return plugin.Helper.message();
~~~

Tham chiếu biểu tượng trong constant pool của `Host` chứa tên kiểu `plugin.Helper` và thông tin thành viên `message`. Khi JVM cần resolve tham chiếu đó, nó sử dụng **defining loader của lớp chứa tham chiếu**, tức loader đã định nghĩa `Host`, để bắt đầu việc nạp kiểu cần tham chiếu. Nó không tự dùng:

- Loader của bất kỳ đối tượng nào tình cờ gọi phương thức `Host`.
- Thread context class loader, trừ khi mã Java/framework chủ động dùng API đọc giá trị đó.
- Loader con đã nạp một lớp cùng tên ở nơi khác.
- “Loader mới nhất” hoặc “loader gần nhất” xuất hiện trong stack trace.

Đây là quy tắc quan trọng trong **JVMS §5.3 và §5.4.3**: resolution gắn với lớp đang chứa symbolic reference. Nếu `Host` thuộc parent thì phép resolve `plugin.Helper` do parent khởi xướng; khi parent không có nguồn này và không có đường ủy quyền phù hợp, nó thất bại. Child có thể đã nạp `plugin.Helper` nhưng điều đó **không bổ sung tự động lớp vào không gian tìm kiếm của parent**.

Đổi hướng gọi thành `child.loadClass("Host")` cũng không giúp: nếu delegation trả về bản `Host` do parent định nghĩa, các dependency trong bytecode `Host` vẫn dùng loader của `Host`.

### Defining loader khác initiating loader

- **Defining loader:** loader thực sự định nghĩa class/interface từ biểu diễn của nó. Danh tính kiểu runtime của class/interface phụ thuộc **binary name + defining loader**.
- **Initiating loader:** loader được JVM ghi nhận là tham gia khởi đầu nạp kiểu. Một child có thể là initiating loader cho lớp do parent định nghĩa khi delegation thành công.
- `SomeType.class.getClassLoader()` báo **defining loader** của class/interface thông thường, không phải nhất thiết loader mà người gọi trực tiếp sử dụng.
- Việc `parent.loadClass("plugin.Helper")` thất bại không chứng minh `plugin.Helper` không tồn tại trong toàn JVM; nó chỉ chứng minh loader và chính sách tra cứu ấy không tìm được kiểu theo yêu cầu.

Điều này cũng giải thích vì sao một lớp Java có thể đã được nạp bởi child nhưng lời gọi bên parent vẫn gặp `ClassNotFoundException` hoặc `NoClassDefFoundError`.

## 4. Thí nghiệm chạy được: parent không tìm thấy child-only dependency

Thí nghiệm dùng hai thư mục output để tạo **hai nguồn classpath tách biệt**. `Host` và `Demo` chạy từ application classpath. `Plugin` và `Helper` chỉ nằm trong thư mục được child `URLClassLoader` đọc. Trong bước **biên dịch**, `Host` được cấp `Helper` trên compile classpath; trong bước **chạy**, thư mục plugin không nằm trong application classpath. Đây là chủ ý để mô phỏng tình huống compile thấy dependency nhưng runtime parent không thấy.

Cấu trúc file nguồn:

~~~text
example/
├── app/
│   ├── Host.java
│   └── Demo.java
└── plugin/
    └── child/
        ├── Helper.java
        └── Plugin.java
~~~

### File `plugin/child/Helper.java`

~~~java
package child;

public class Helper {
    public static String message() {
        return "helper ready";
    }
}
~~~

### File `plugin/child/Plugin.java`

~~~java
package child;

public class Plugin {
    public static String run() {
        return Helper.message();
    }
}
~~~

`Plugin` và `Helper` cùng thuộc nguồn bytecode riêng của child.

### File `app/Host.java`

~~~java
public class Host {
    public static String callChildDependency() {
        return child.Helper.message();
    }
}
~~~

`Host` được định nghĩa bởi application/system loader khi chạy. Dù compile thành công, nó vẫn cần resolve `child.Helper` khi gọi `callChildDependency()`.

### File `app/Demo.java`

~~~java
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class Demo {
    public static void main(String[] args) throws Exception {
        ClassLoader parent = Demo.class.getClassLoader();

        try {
            parent.loadClass("child.Helper");
            System.out.println("parent unexpectedly found Helper");
        } catch (ClassNotFoundException expected) {
            System.out.println("parent: ClassNotFoundException");
        }

        try {
            System.out.println("Host: " + Host.callChildDependency());
        } catch (NoClassDefFoundError expected) {
            System.out.println("Host: NoClassDefFoundError");
        }

        URL pluginUrl = Path.of(args[0]).toUri().toURL();
        try (URLClassLoader child =
                     new URLClassLoader(new URL[]{pluginUrl}, parent)) {
            Class<?> plugin = child.loadClass("child.Plugin");
            System.out.println("Plugin defined by child: "
                    + (plugin.getClassLoader() == child));

            Method run = plugin.getMethod("run");
            System.out.println("Plugin: " + run.invoke(null));

            System.out.println("Host via child is parent class: "
                    + (child.loadClass("Host") == Host.class));

            try {
                Host.callChildDependency();
                System.out.println("Host unexpectedly resolved Helper");
            } catch (NoClassDefFoundError expected) {
                System.out.println("Host still: NoClassDefFoundError");
            }
        }
    }
}
~~~

### Biên dịch và chạy trên Linux/macOS (Bash)

Đứng trong thư mục `example/`, sử dụng JDK 17 trở lên:

~~~bash
mkdir -p out/plugin out/app
javac -d out/plugin plugin/child/Helper.java plugin/child/Plugin.java
javac -cp out/plugin -d out/app app/Host.java app/Demo.java
java -cp out/app Demo out/plugin
~~~

Các lệnh này **cố ý không thêm** `out/plugin` vào runtime `-cp`. Nó chỉ được dùng làm compile classpath của `Host` hoặc làm URL riêng cho child loader. Trên Windows, có thể tạo hai thư mục bằng `mkdir out\plugin` và `mkdir out\app` trong PowerShell, sau đó chạy cùng các lệnh `javac`/`java` với dấu phân cách đường dẫn thích hợp.

### Stdout đã kiểm chứng

~~~text
parent: ClassNotFoundException
Host: NoClassDefFoundError
Plugin defined by child: true
Plugin: helper ready
Host via child is parent class: true
Host still: NoClassDefFoundError
~~~

### Phân tích từng dòng

1. `parent.loadClass("child.Helper")` kiểm tra các nguồn parent nhìn thấy; `out/plugin` không nằm trên runtime classpath của parent, nên ném `ClassNotFoundException`.
2. `Host.callChildDependency()` thực thi bytecode tham chiếu `child.Helper`. JVM resolve dependency trong ngữ cảnh defining loader của `Host`, tức parent; khi thất bại, quá trình liên kết cần lớp này tạo `NoClassDefFoundError` (với nguyên nhân liên quan không tìm thấy class).
3. Child `URLClassLoader` tìm `child.Plugin` từ `out/plugin`, vì parent không có nó, nên `plugin.getClassLoader() == child` là `true`.
4. `Plugin.run()` gọi `Helper.message()`. `Plugin` do child định nghĩa; khi resolve `Helper`, child tìm được tệp class trong cùng nguồn, nên in `helper ready`.
5. Khi gọi `child.loadClass("Host")`, child hỏi parent và nhận `Host.class`; nó không tạo bản sao `Host`. Bởi vậy kết quả so sánh là `true`.
6. Gọi `Host.callChildDependency()` thêm một lần vẫn không khiến parent đổi hướng sang child. Lỗi còn đó; việc plugin đã được child nạp không chuyển quyền định nghĩa/resolve phụ thuộc của `Host`.

**Điều thí nghiệm chứng minh:** parent không tự tìm class riêng của child, và dependency lookup của mã parent không đổi theo loader của caller. Nếu chạy sai lệnh và thêm `out/plugin` vào `java -cp`, parent sẽ tìm được `Helper` ngay từ đầu, làm mất điều kiện của thí nghiệm.

## 5. Vì sao JVM không tự quét mọi loader con?

### 5.1. Không có đồ thị children chuẩn để duyệt

Một `ClassLoader` được khởi tạo với tham chiếu đến parent. Chiều ngược lại không phải một registry chuẩn bắt buộc. Ứng dụng có thể tạo, bỏ, thay thế nhiều child loader để cách ly plugin, test, hot reload hoặc nhiều phiên bản thư viện.

Parent không biết phải chọn child nào nếu có:

~~~text
           Parent
        /    |     \
   Child A Child B Child C
   Helper v1 Helper v2 (không có Helper)
~~~

Nếu parent tự quét, câu hỏi “`Helper` của phiên bản nào?” trở thành mơ hồ. Chính sách parent-first và ngữ cảnh defining loader giúp hành vi có thể giải thích bằng một đường tra cứu xác định, thay vì phụ thuộc thứ tự chạy hoặc danh sách loader tình cờ còn sống.

### 5.2. Bảo toàn isolation và danh tính kiểu

Hai child khác nhau có thể cùng định nghĩa `plugin.Helper` từ bytecode khác nhau. JVM coi chúng là **hai kiểu runtime khác nhau** vì defining loader khác nhau. Parent không thể tùy tiện chọn một trong hai mà vẫn bảo đảm mọi lời gọi được liên kết kiểu an toàn.

Ngay cả khi tên và source nhìn giống nhau, casting giữa hai lớp cùng binary name do loader khác nhau định nghĩa có thể thất bại. Với API giao tiếp giữa parent và child, các class/interface thuộc hợp đồng chung nên được định nghĩa ở một loader chung (thường là parent) để cả hai phía dùng cùng định danh kiểu.

### 5.3. Không làm lớp thư viện cha phụ thuộc tùy tiện vào plugin con

Nếu parent tự nạp mọi thứ từ child, framework và thư viện dùng chung sẽ khó đảm bảo độc lập: một plugin có thể làm đổi kết quả tìm lớp của plugin khác, hoặc khiến framework vô tình nhận lớp không thuộc phạm vi thiết kế. Parent-first mặc định hỗ trợ chia sẻ API ổn định và đóng gói phụ thuộc riêng trong từng child.

Đây là **cơ chế tổ chức không gian tên và phân giải kiểu**, không phải ranh giới sandbox bảo mật tuyệt đối. Việc dùng custom class loader không tự khiến bytecode không đáng tin trở nên an toàn.

## 6. Phân biệt các tình huống thường nhầm

| Tình huống | Parent thấy lớp child? | Lý do |
| --- | --- | --- |
| `child.loadClass("api.Contract")`, `Contract` nằm ở parent | Không cần parent thấy child | Child ủy quyền lên parent và nhận class chung. |
| `parent.loadClass("plugin.Helper")`, `Helper` chỉ ở child | Thường không | Parent không tự hỏi các child. |
| Lớp `Host` do parent định nghĩa tham chiếu trực tiếp `plugin.Helper` | Thường không | Resolution từ `Host` bắt đầu bằng defining loader của `Host`. |
| Child gọi phương thức `Host.doWork()` của parent, và `Host` tham chiếu tới plugin | Không tự động | Loader của caller không thay loader gắn với `Host`. |
| `child.loadClass("Host")` trả class của parent | Vẫn là class parent | Delegation không tạo bản class mới. |
| App đặt plugin JAR lên chính runtime classpath của parent | Có thể | Bấy giờ dependency đã ở nguồn parent; ví dụ isolation child không còn đúng. |
| Parent chủ động gọi `Class.forName("plugin.Helper", false, child)` | Có thể tìm được | Đây là **chủ động chỉ định child** trong API, khác với tự tìm thông qua parent-first. |
| Framework dùng thread context class loader để tìm plugin | Có thể | Chỉ khi mã/framework **thực sự dùng** context loader; JVM không tự dùng TCCL cho mọi resolution. |
| Một custom parent được lập trình riêng để hỏi child | Có thể, theo thiết kế | Đây là custom lookup/reverse delegation, không phải mặc định của `ClassLoader`. |

**Không đồng nhất `ClassNotFoundException` và `NoClassDefFoundError`:** gọi `ClassLoader.loadClass` trực tiếp thường báo `ClassNotFoundException` khi không tìm được lớp. Khi JVM resolve một symbolic reference cần class không thể nạp, code sử dụng class có thể thấy `NoClassDefFoundError`. Thông điệp và cause giúp xác định nơi lỗi xảy ra. Ngoài ra, `NoClassDefFoundError` có thể có nguyên nhân khác (ví dụ khởi tạo lớp đã thất bại).

## 7. Cách thiết kế đúng khi framework ở parent, plugin ở child

### 7.1. Đặt hợp đồng giao tiếp ở parent

Giả sử framework định nghĩa:

~~~java
package api;

public interface Plugin {
    String execute();
}
~~~

Plugin do child định nghĩa có thể viết:

~~~java
package plugin;

import api.Plugin;

public class GreetingPlugin implements Plugin {
    @Override
    public String execute() {
        return "Hello from plugin";
    }
}
~~~

Hai đoạn mã là **hai tệp riêng**, lần lượt có tên `api/Plugin.java` và `plugin/GreetingPlugin.java`. Chúng chỉ minh họa giao diện đặt ở parent và triển khai trong child; không phải một chương trình độc lập như ví dụ ở phần 4.

Loader con ủy quyền nạp `api.Plugin` cho parent. Framework có thể nạp class triển khai bằng child loader và ép về `api.Plugin` chung:

~~~java
Class<?> implementation = Class.forName("plugin.GreetingPlugin", true, childLoader);
api.Plugin plugin =
        (api.Plugin) implementation.getDeclaredConstructor().newInstance();
String result = plugin.execute();
~~~

Đoạn mã này nằm trong framework đã có biến `childLoader` và API chung. Khi runtime thực thi, class triển khai tồn tại trong nguồn riêng child, constructor có thể truy cập được, và `api.Plugin` chỉ có một defining loader dùng chung. Đây là cách kết nối qua interface thay vì để parent hard-code một class chỉ nằm trong child.

**Sai lầm dễ gặp:** đưa một bản sao `api.Plugin` vào plugin JAR rồi dùng child-first để định nghĩa bản thứ hai. Dù tên `api.Plugin` giống nhau, hai `Class` khác nhau có thể gây `ClassCastException` hoặc lỗi loading constraint. Với API chung, cố gắng thiết kế sao cho plugin **ủy quyền về một bản API duy nhất**.

### 7.2. Dùng loader được truyền tường minh để nạp plugin

Khi cần lớp cụ thể ở child, parent có thể nhận một `ClassLoader` từ thành phần quản lý plugin, rồi gọi:

~~~java
Class<?> pluginType = Class.forName(
        "plugin.GreetingPlugin", true, pluginLoader);
~~~

Đây là **mã ứng dụng chủ động** chọn loader; nó không thay đổi nguyên tắc bytecode trong một class parent vốn tham chiếu tĩnh tới `plugin.Helper`. Chỉ việc gọi `Class.forName` ở một chỗ không khiến mọi tham chiếu khác trong parent tự chuyển sang child.

Khi làm plugin manager, quản lý vòng đời `URLClassLoader`, đóng loader khi không còn dùng, và tránh giữ tham chiếu tới `Class`/object/loader plugin nếu muốn có thể giải phóng bộ nhớ. Việc đóng URLClassLoader chỉ giải phóng tài nguyên của nó; nó không trực tiếp “unload” mọi lớp nếu còn tham chiếu sống.

### 7.3. Service Provider Interface và Thread Context ClassLoader

Một thư viện trong parent đôi khi cần tìm implementation nằm trong ứng dụng/plugin child. Thư viện có thể **cố ý dùng** *thread context class loader* (TCCL):

~~~java
ClassLoader contextLoader =
        Thread.currentThread().getContextClassLoader();
~~~

Các API như `ServiceLoader.load(service)` mặc định dùng thread context class loader của luồng đang chạy. Với API service ở parent và provider ở child, caller/framework có thể cấu hình TCCL hoặc gọi `ServiceLoader.load(service, childLoader)` để chọn phạm vi khám phá provider. Điều này còn phụ thuộc cấu hình service metadata, module/provider accessibility và cùng danh tính interface giữa hai phía.

**TCCL không phải sửa đổi toàn cục quy tắc parent-first:** JVM không tự lấy TCCL khi resolve mọi symbolic reference. Chỉ những API/library chủ động truy xuất và dùng nó mới có tác dụng đổi nơi tìm lớp. TCCL cũng là trạng thái theo luồng, nên nếu tạm đổi cần khôi phục trong `finally`:

~~~java
Thread current = Thread.currentThread();
ClassLoader previous = current.getContextClassLoader();
try {
    current.setContextClassLoader(pluginLoader);
    // Gọi framework/API có hợp đồng sử dụng context loader.
} finally {
    current.setContextClassLoader(previous);
}
~~~

Đoạn này giả định `pluginLoader` đã được tạo ở nơi khác; nó minh họa cách giữ nguyên trạng thái luồng sau lời gọi.

### 7.4. Khi nào cần child-first hoặc reverse delegation?

Một số container/module/plugin system tự định nghĩa chính sách class loading, thí dụ ưu tiên thư viện của plugin thay vì phiên bản ở host. Khi đó có thể override `loadClass`, nhưng phải giữ ràng buộc đồng bộ `getClassLoadingLock(name)`, kiểm tra `findLoadedClass`, dùng `resolveClass` đúng lúc và bảo vệ API nền tảng/kiểu chia sẻ khỏi việc định nghĩa bản trùng.

Child-first cho phép **child ưu tiên tự tìm** trước parent; nó **không làm parent tự biết children**. Nếu cần parent tìm implementation của child, phải thiết kế một registry, SPI, API nạp qua loader được chọn hoặc cơ chế giao tiếp tường minh. Không sửa class loader cha của JDK theo kiểu quét mọi class loader con.

## 8. Những bẫy thường gặp khi debug

1. **Compile thành công rồi runtime lỗi:** compiler có thể thấy dependency qua compile classpath, nhưng loader của class chứa tham chiếu không thấy nó tại runtime. Hãy kiểm tra *compile classpath*, *runtime classpath* và URL riêng của child; chúng không nhất thiết trùng nhau.
2. **Nhầm classpath của parent với nguồn child:** đặt JAR trong thư mục plugin không tự thêm nó vào system loader. Nếu child giữ URL tới JAR, chỉ child có cơ chế tìm trong URL đó.
3. **Nhìn stack trace rồi đoán loader:** một hàm parent được gọi từ plugin không đổi defining loader của hàm đó. Kiểm tra `Host.class.getClassLoader()` và `PluginImpl.class.getClassLoader()`.
4. **Nhầm “đã load ở đâu đó” với “mọi loader đều thấy”:** không có bảng toàn JVM bảo đảm mọi loader trả cùng `Class<?>` chỉ vì tên giống nhau.
5. **Tưởng `Thread.currentThread().setContextClassLoader(child)` sửa mọi lỗi:** resolution bytecode của `Host` vẫn dùng loader gắn với `Host`. TCCL chỉ giúp ở API/framework có dùng nó.
6. **Nạp bản sao shared API ở child:** hai bản `api.Plugin` cùng tên nhưng khác defining loader không đảm bảo cast được. Chia sẻ API qua parent.
7. **Bắt nhầm exception:** `ClassNotFoundException` là checked exception từ tra cứu tường minh; lỗi khi JVM resolve có thể là `NoClassDefFoundError`. Đọc `getCause()` và chain nguyên nhân.
8. **Cho rằng mọi JDK loader đều là cây parent-first tuyệt đối:** từ Java 9, module system có các ngoại lệ và cơ chế delegation phức tạp hơn. Phân tích từng loader và policy thay vì khái quát quá mức.

### Checklist chẩn đoán ngắn

Khi một class chỉ nằm ở plugin child mà parent không tìm thấy, hãy trả lời lần lượt:

- Class nào đang chứa symbolic reference tới phụ thuộc, và `ThatClass.class.getClassLoader()` là gì?
- Dependency có mặt trong nguồn/JAR mà **loader đó** có thể tra cứu không?
- Loader con nào đang giữ dependency? Có bị chồng tên/phiên bản với parent hoặc sibling không?
- Framework có hợp đồng SPI/loader argument/TCCL để tìm implementation mà không cần static reference trực tiếp không?
- Shared API có do parent định nghĩa duy nhất không?
- Exception xảy ra trong `loadClass` trực tiếp hay trong JVM linking/resolution/initialization?

## 9. Câu hỏi tự kiểm tra

**Tình huống A:** `Plugin` do child định nghĩa gọi `PluginHelper` do child định nghĩa. Child hỏi parent, parent không có, child `findClass` thành công. Vì sao chạy được?

**Đáp:** Vì resolution của `Plugin` do chính defining loader của `Plugin` khởi xướng; child là loader có nguồn `PluginHelper` nên tìm được sau khi parent không tìm thấy.

**Tình huống B:** `Host` do parent định nghĩa, một child gọi `Host.invoke()`. Trong `Host.invoke()` có tham chiếu trực tiếp `PluginHelper` chỉ nằm ở child. JVM có dùng child để resolve phụ thuộc của `Host` không?

**Đáp:** Không theo class-loading mặc định. Dependency trong bytecode của `Host` được resolve với defining loader của `Host`, tức parent. Hướng gọi phương thức không đổi loader định nghĩa `Host`.

**Tình huống C:** Có thể làm framework tìm provider của plugin mà không thêm toàn bộ plugin JAR vào parent không?

**Đáp:** Có. Đặt interface chung ở parent, cho child nạp implementation, và dùng loader tường minh/SPI/TCCL trong các API có thiết kế hỗ trợ. Child chỉ cần thấy parent API và nguồn implementation của mình.

## Tài liệu chuẩn

- [Java SE 21 – ClassLoader: parent delegation, loadClass và findClass](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html).
- [JVM Specification Java SE 21, §5.3 – Loading, Defining và Initiating Loader](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3).
- [JVM Specification Java SE 21, §5.4.3 – Resolution](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.4.3).
- [Java SE 21 – ServiceLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ServiceLoader.html): lựa chọn class loader để tìm provider.
- [Java SE 21 – Thread.getContextClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#getContextClassLoader()): thread context loader cho framework/tooling.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="parent-delegation-009">Ủy quyền trong mô hình đồ thị và ngoại lệ với hệ module</a>

<details>
<summary>Xem chi tiết</summary>

# Ủy quyền trong mô hình đồ thị và ngoại lệ với hệ module

Khi học `ClassLoader`, ta thường gặp sơ đồ **bootstrap ← platform ← application ← custom loader** và quy tắc *parent-first*: loader hỏi cha trước, rồi mới thử tự tìm class. Sơ đồ này hữu ích nhưng **không mô tả hết mọi đường ủy quyền trong Java 9+**, đặc biệt khi **Java Platform Module System (JPMS)** và các `ModuleLayer` mới được dùng.

Một loader vẫn có **một parent** theo `getParent()`, nhưng quá trình tìm class còn có thể có **cạnh ủy quyền trực tiếp sang loader khác**, kể cả loader ngang hàng (*peer*) hoặc loader không phải tổ tiên trong parent chain. Điều này tạo thành **đồ thị delegation**, thay vì chỉ một cây. JVM và module system dùng thông tin module/package/exports để xác định nơi nên tìm class; loader tích hợp còn có ngoại lệ phục vụ module được nâng cấp (*upgraded module*).

Bài học thuộc nhóm **Cơ chế ủy quyền** của Java core – classloader. Sau bài này, bạn cần phân biệt được **cây parent**, **đồ thị phụ thuộc module** và **đồ thị ủy quyền khi load class**; hiểu cơ chế đặc biệt của loader tích hợp; tự chạy được ví dụ hai module có loader ngang hàng; và biết các giới hạn về type identity, quyền truy cập, deadlock, module layers.

## 1. Mô hình thông thường: cây parent-first

Trong cơ chế mặc định của `ClassLoader.loadClass(String, boolean)`, quy trình tìm class gần như sau:

1. Kiểm tra tên class đã được loader này khởi đầu nạp chưa qua `findLoadedClass`.
2. Nếu chưa, thử gọi `parent.loadClass(...)`; nếu parent được biểu diễn bằng `null`, dùng cơ chế bootstrap.
3. Khi parent không tìm thấy, gọi `findClass(...)` của loader hiện tại.
4. Nếu tìm được class và tham số `resolve` yêu cầu, liên kết qua `resolveClass(...)`.
5. Nếu tất cả đều không tìm thấy, ném `ClassNotFoundException`.

Cây parent thường gặp trên JDK 17:

```text
[Bootstrap]                    (biểu diễn bằng null)
     ^
     | parent
[Platform]
     ^
     | parent
[Application/System]
     ^
     | parent
[PluginLoader]
```

**Cạnh parent** là một quan hệ được lưu/biểu diễn bằng `ClassLoader.getParent()`. Đó **không phải** thông tin class thực sự do ai định nghĩa.

Ví dụ, lời gọi `PluginLoader.loadClass("java.lang.String")` có thể ủy quyền lên bootstrap để nhận lại `java.lang.String`. `PluginLoader` là *requesting/initiating loader* nhưng **defining loader** của `String` vẫn được biểu diễn là `null`:

```java
ClassLoader child = ClassLoader.getSystemClassLoader();
Class<?> type = child.loadClass("java.lang.String");
System.out.println(type == String.class);
System.out.println(type.getClassLoader() == null);
```

Đây là **đoạn minh họa cần đặt trong method có `throws ClassNotFoundException`**; trên JDK Java SE thông thường, hai dòng in `true`. `loadClass` tự nó không bắt buộc chạy static initializer của class được tìm thấy.

**Giới hạn của cây parent:** một loader có thể biết thêm loader khác thông qua module, framework hoặc policy tùy biến. `getParent()` chỉ trả về **một cạnh parent**, không hứa liệt kê toàn bộ loader có thể được hỏi trong mọi lần load class.

## 2. Ba loại “đồ thị” cần tách riêng

Hãy hình dung ứng dụng có module `m.api` và `m.client`. `m.client` đọc `m.api`, còn mỗi module được gán cho một class loader khác nhau.

### 2.1 Đồ thị parent loader

```text
                  [System parent]
                   ^           ^
                   |           |
              [Loader A]   [Loader B]
                 m.api       m.client
```

Cả A và B đều có cùng parent. **A không phải parent của B** và ngược lại. Đây là đồ thị các cạnh parent (thường có dạng cây).

### 2.2 Đồ thị module readability

```text
[m.client] --requires/reads--> [m.api]
```

Cạnh này mô tả **module nào đọc module nào**. Source `module-info.java` có thể khai báo `requires m.api;`; sau khi module resolution, runtime có quan hệ `Module.canRead` tương ứng. Đọc được module chưa đủ để truy cập tùy ý mọi package: cần xét `exports` hoặc `opens` phù hợp.

### 2.3 Đồ thị class-loader delegation

```text
[Loader B] --tìm p.api.Service--> [Loader A]
                 |
                 +-- package p.api thuộc module m.api
                     và được xuất/hiển thị cho m.client
```

Trong cơ chế loader của `ModuleLayer.defineModulesWithManyLoaders`, loader B có thể **đi thẳng sang A** khi tên class nằm trong một package mà module đọc được và được export phù hợp. Cạnh này **không xuất hiện trong `getParent()` của B**, vì A là loader ngang hàng.

Như vậy:

| Quan hệ | Hỏi điều gì? | API hoặc dữ liệu quan sát |
| --- | --- | --- |
| Parent | Loader nào là parent của loader hiện tại? | `ClassLoader.getParent()` |
| Module readability | Module nào được module hiện tại đọc? | `module-info.java`, resolved configuration, `Module.canRead(...)` |
| Module export/open | Package nào được phép truy cập compile/runtime hoặc reflection? | `exports`, `opens`, `Module.isExported/isOpen` |
| Delegation thực tế | Khi nạp tên class này, loader sẽ hỏi ai? | Chính sách `loadClass` + ánh xạ package/module/loader |
| Defining loader | Class được trả về do ai định nghĩa? | `Class.getClassLoader()` |

**Không đồng nhất ba đồ thị.** Một cạnh `requires` là thông tin module, không tự động là một lệnh `getParent()`. Một cạnh delegation cho phép hỏi loader để tìm class, nhưng không tự cấp quyền Java access vượt qua ràng buộc module.

## 3. Vì sao JPMS cần cơ chế delegation theo đồ thị?

Trước Java 9, mô hình học tập classpath chủ yếu dựa trên thứ tự tra cứu trong parent chain và `findClass`. Từ Java 9, **named modules** cho phép gắn các package vào các module được resolve và gắn module vào các class loader. Với một loader quản lý nhiều module, việc tìm class có thể cần biết chính xác:

- Tên class có **package** nào?
- Package ấy thuộc module đã được resolve nào?
- Module đó gắn với **loader hiện tại hay một loader khác**?
- Package có được **export** cho module yêu cầu không?
- Nếu không có ánh xạ package/module thích hợp, có nên fallback sang parent/classpath không?

Một số loader do JPMS tạo dùng **direct delegation**: tìm module theo package và gửi yêu cầu trực tiếp tới loader phụ trách module, thay vì bắt buộc thử parent trước ở mọi trường hợp.

### Hai chiến lược tạo module layer

| API của `ModuleLayer` | Cách ánh xạ module → loader | Hệ quả chính |
| --- | --- | --- |
| `defineModulesWithOneLoader(cf, parentLoader)` | Các module trong layer mới dùng **một loader** | Module trong cùng layer chia sẻ loader; vẫn có thể direct-delegate tới loader của module ở parent layers |
| `defineModulesWithManyLoaders(cf, parentLoader)` | **Mỗi module** trong layer mới có loader riêng | Loader của module B có thể direct-delegate tới loader của module A ngay trong layer nếu có quan hệ phù hợp |
| `defineModules(cf, moduleNameToLoader)` | Caller cung cấp hàm ánh xạ module → loader | Dành cho chiến lược nâng cao; caller chịu trách nhiệm quản lý loader và điều kiện hợp lệ |

**Parent layer** và **parent class loader** là hai khái niệm khác nhau. `ModuleLayer.boot()` có thể là layer cha của layer mới; `ClassLoader.getSystemClassLoader()` có thể là parent của các loader mới. Điều này không khiến hai loader ngang hàng tự nhiên trở thành cha–con.

Khi chọn `defineModulesWithManyLoaders`, việc **mỗi module một loader** là một đặc điểm của API; không có nghĩa mọi named module ở JVM luôn có loader riêng. Boot layer có thể phân nhiều module cho cùng bootstrap/platform/application loader.

### Quy trình direct delegation theo package

```text
Loader B cần p.api.Service
        |
        v
Package = p.api
        |
        v
Tra module/package đã được giải quyết trong layer
        |
        +-- p.api thuộc m.api, được export tới m.client
        |            |
        |            v
        |       Đưa yêu cầu trực tiếp tới Loader A
        |            |
        |            v
        |       Trả cùng định nghĩa Class<?> của Loader A
        |
        +-- Không ánh xạ tới module phù hợp
                     |
                     v
            Thử parent loader theo contract của API
```

Sơ đồ mô tả cơ chế của loader JPMS được tạo bởi các phương thức trên. Đây **không phải** thuật toán bắt buộc cho mọi custom `ClassLoader`; custom loader có thể áp dụng policy khác và phải tự chịu trách nhiệm tương thích.

## 4. Ví dụ Java 17+ chạy được: hai loader ngang hàng, một cạnh delegation trực tiếp

Ta sẽ tạo hai named modules:

- **`m.alpha`** chứa `p.alpha.Greeter` và export package `p.alpha`.
- **`m.beta`** khai báo `requires m.alpha;`, chứa `p.beta.UseAlpha` sử dụng `Greeter`.
- Một chương trình `Launcher` trên classpath sẽ resolve hai module rồi dùng `defineModulesWithManyLoaders` để tạo mỗi module một loader. Hai loader có cùng parent là system loader.

### 4.1 Cấu trúc thư mục

```text
project/
├── src/
│   ├── m.alpha/
│   │   ├── module-info.java
│   │   └── p/alpha/Greeter.java
│   └── m.beta/
│       ├── module-info.java
│       └── p/beta/UseAlpha.java
└── Launcher.java
```

Tạo đủ các thư mục trên; chạy những lệnh `javac` phía dưới từ thư mục `project/`.

### 4.2 Nội dung module `m.alpha`

File `src/m.alpha/module-info.java`:

```java
module m.alpha {
    exports p.alpha;
}
```

File `src/m.alpha/p/alpha/Greeter.java`:

```java
package p.alpha;

public class Greeter {
    public static String message() {
        return "Hello from alpha";
    }
}
```

`exports p.alpha;` cho phép module khác đọc `m.alpha` và truy cập public API của package này theo điều kiện module system.

### 4.3 Nội dung module `m.beta`

File `src/m.beta/module-info.java`:

```java
module m.beta {
    requires m.alpha;
    exports p.beta;
}
```

File `src/m.beta/p/beta/UseAlpha.java`:

```java
package p.beta;

public class UseAlpha {
    public static String call() {
        return p.alpha.Greeter.message();
    }
}
```

`requires m.alpha;` làm module `m.beta` đọc `m.alpha`. `exports p.beta;` cho phép `Launcher` gọi public method qua reflection hợp lệ từ unnamed module, sau khi tìm được class.

### 4.4 Chương trình tạo module layer mới

File `Launcher.java`:

```java
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Path;
import java.util.Set;

public class Launcher {
    public static void main(String[] args) throws Exception {
        ModuleFinder finder = ModuleFinder.of(Path.of("mods"));

        Configuration cf = ModuleLayer.boot()
                .configuration()
                .resolve(finder, ModuleFinder.of(), Set.of("m.beta"));

        ModuleLayer layer = ModuleLayer.boot()
                .defineModulesWithManyLoaders(
                        cf, ClassLoader.getSystemClassLoader());

        ClassLoader a = layer.findLoader("m.alpha");
        ClassLoader b = layer.findLoader("m.beta");

        System.out.println("different loaders: " + (a != b));
        System.out.println("same parent: "
                + (a.getParent() == b.getParent()));
        System.out.println("peer is not parent: "
                + (b.getParent() != a));

        Class<?> viaB = b.loadClass("p.alpha.Greeter");
        System.out.println("B can load A: "
                + (viaB == a.loadClass("p.alpha.Greeter")));

        Class<?> use = b.loadClass("p.beta.UseAlpha");
        System.out.println("call: "
                + use.getMethod("call").invoke(null));

        try {
            a.loadClass("p.beta.UseAlpha");
            System.out.println("reverse: found");
        } catch (ClassNotFoundException expected) {
            System.out.println("reverse: not found");
        }
    }
}
```

Các điểm cần lưu ý:

- `ModuleFinder.of(Path.of("mods"))` lấy module đã biên dịch từ thư mục `mods`.
- `resolve(... Set.of("m.beta"))` cũng resolve `m.alpha` do `m.beta` yêu cầu module ấy.
- `defineModulesWithManyLoaders` tạo loader riêng cho mỗi module và đặt cùng parent system loader.
- `layer.findLoader(name)` lấy loader được gán cho module trong layer.
- `b.loadClass("p.alpha.Greeter")` tìm class do loader A định nghĩa dù A **không phải** parent của B.
- `a.loadClass("p.beta.UseAlpha")` không có cạnh ngược hợp lệ trong ví dụ. Parent system loader cũng không chứa hai module chỉ mới đưa vào layer này.

### 4.5 Biên dịch và chạy

Yêu cầu **JDK 17 trở lên**. Từ `project/`:

```bash
javac --release 17 -d mods --module-source-path src -m m.alpha,m.beta
javac --release 17 Launcher.java
java -cp . Launcher
```

Bản ví dụ này đã được đối chiếu bằng `javac --release 17` và chạy trên JDK 21 tương thích Java 17. Stdout chính xác:

```text
different loaders: true
same parent: true
peer is not parent: true
B can load A: true
call: Hello from alpha
reverse: not found
```

**Vì sao đây là bằng chứng của đồ thị delegation?**

`same parent: true` chứng minh A/B cùng parent. `peer is not parent: true` xác nhận A không phải parent của B. Nhưng `B can load A: true` vẫn xuất hiện: loader B tìm `p.alpha.Greeter` bằng **direct delegation** dựa trên package/module và export, nhận lại đúng `Class<?>` do A định nghĩa.

Cạnh này có hướng: `m.beta` đọc `m.alpha` và module API được export cho B; ngược lại `m.alpha` không có quan hệ đọc/export tương ứng để B trở thành nguồn class của A. Vì thế `reverse: not found` là kết quả hợp lý trong ví dụ.

**Chú ý khi tái hiện:** đừng đồng thời đưa `mods` vào `--module-path` của `java` và tự resolve cùng chúng trong `Launcher` nếu mục tiêu là quan sát các module do layer mới định nghĩa. Lệnh chạy chỉ đặt `Launcher` trên classpath, để tránh lẫn boot layer với custom layer.

## 5. Điểm đặc biệt của loader tích hợp: platform có thể delegate sang application

Java SE 17 công bố rõ một ngoại lệ quan trọng đối với cây parent:

> Khi module được nâng cấp hoặc ghi đè theo cơ chế module system, platform class loader có thể cần delegate sang loader khác, ví dụ application class loader, mặc dù application **không phải tổ tiên** của platform.

Điều này liên quan trường hợp **upgraded module** được xác định cho platform loader nhưng đọc các module được định nghĩa cho loader khác. Để thỏa mãn các tham chiếu package/module đó, platform loader có thể hỏi loader phụ trách module đích theo cách **trực tiếp**, không chỉ đi dọc parent chain.

Sơ đồ ý tưởng:

```text
        [Bootstrap]
             ^
             | parent
        [Platform] -----------------+
             ^                      |
             | parent               | có thể direct-delegate
        [Application] <-------------+
        
      Parent edge: Application → Platform → Bootstrap
      Extra lookup edge: Platform → Application (khi module yêu cầu)
```

Trong sơ đồ, mũi tên parent hướng lên và cạnh ngoại lệ được ghi rõ. Đường platform → application **không phải parent mới**, không biến hệ loader mặc định thành vòng parent, và cũng **không có nghĩa** platform luôn nhìn thấy mọi class trên application classpath.

**Điểm cần nhớ:**

1. `ClassLoader.getPlatformClassLoader().getParent()` không thay đổi chỉ vì có một cạnh delegation sang app.
2. Cạnh này xuất hiện **theo nhu cầu module/package cụ thể**, nổi bật trong trường hợp upgraded modules; nó không phải policy “platform fallback qua mọi class của application”.
3. Hành vi Java SE 17 ở mức API cho phép/giải thích cơ chế này. Chi tiết ánh xạ và kiểm tra package của runtime cụ thể có thể thay đổi theo phiên bản/triển khai.
4. Parent-first vẫn là mô hình mặc định được `ClassLoader` khuyến nghị cho loader tùy biến thông thường, nhưng **loader built-in phục vụ JPMS** có thể hoạt động theo quy tắc riêng.

Không nên dựng giả một kết quả “platform luôn load được class `com.myapp.X`” trên JDK bình thường. Để minh họa ngoại lệ thật sự cần cấu hình module nâng cấp/module read phù hợp. Ví dụ `ModuleLayer` ở mục 4 cung cấp bằng chứng **runnable** về cùng nguyên lý direct delegation giữa các loader ngang hàng mà không phụ thuộc cấu hình nâng cấp JDK đặc thù.

## 6. Parent, readability, exports và opens phối hợp ra sao?

Một câu hỏi hay gặp: **Nếu B load được `p.alpha.Greeter` từ A thì code trong B chắc chắn gọi được mọi thành viên của Greeter không?**

**Không.** Đồ thị delegation chỉ giải thích cách tìm class. Quy tắc module còn chi phối **quyền truy cập**.

| Khái niệm | Chức năng | Ví dụ |
| --- | --- | --- |
| `requires m.alpha;` | Module B có quan hệ đọc A | `m.beta` dùng API của `m.alpha` |
| `requires transitive m.alpha;` | Quan hệ đọc được truyền tiếp cho module đọc module khai báo theo quy tắc JPMS | Một API module re-export phụ thuộc |
| `exports p.alpha;` | Cho phép module khác truy cập public/protected API đủ điều kiện của package | `UseAlpha` gọi `Greeter.message()` |
| `exports p.alpha to m.beta;` | Chỉ export package cho module được nêu | Giới hạn compile/runtime access theo module |
| `opens p.alpha;` | Cho phép deep reflection tới package trong phạm vi được mở | Framework phản chiếu private members khi cần |
| `opens p.alpha to m.beta;` | Chỉ mở deep reflection cho module được chỉ định | Giới hạn quyền reflection |
| `Module.canRead(...)` | Xem quan hệ đọc runtime giữa module | Debug readability |
| `Module.isExported(...)` / `isOpen(...)` | Xem quyền export/open runtime | Debug access/encapsulation |

`requires` không có nghĩa package sẽ tự động được export. `exports` không có nghĩa mọi module tự động đọc module nguồn. `opens` chủ yếu dành cho reflection sâu, không biến private method thành một public method có thể gọi trực tiếp từ source Java.

Một lỗi `IllegalAccessError` hoặc `InaccessibleObjectException` có thể xuất hiện **sau khi class đã được tìm thấy**. Đừng sửa sai bằng cách đổi parent loader khi nguyên nhân nằm ở `exports`/`opens` hoặc quyền truy cập Java.

### Liên hệ từ graph module tới class-loader graph

Trong loader của module layer, **gói được export và quan hệ module** được dùng để xác định các tuyến direct-delegation. Tuy nhiên **không phải mọi cạnh `requires` sẽ trở thành một cạnh parent**, và **không phải mọi loại lookup class đều lặp lại thuật toán đọc module của Java compiler**.

Đặc biệt, khi một API loader nhận trực tiếp một tên binary class từ bên ngoài, cơ chế ánh xạ package/module của loader có thể là đầu mối để tìm class; kiểm tra có được **dùng** class đó từ code của một module cụ thể là câu hỏi khác với việc lấy một `Class<?>` qua API.

## 7. `ModuleLayer` có thể tạo ra các kiến trúc nào?

### 7.1 Một loader chứa nhiều module

`defineModulesWithOneLoader` gán mọi module trong layer mới cho **một loader**. Điều này giúp các module chia sẻ một namespace loader, nhưng **không gộp các module thành một module duy nhất**. Các quan hệ `requires`, `exports` và `opens` vẫn tồn tại.

Nếu hai module trong cùng layer khai báo **trùng package** khi dùng một loader, việc tạo layer có thể thất bại với `LayerInstantiationException`. Quy tắc này giúp tránh tình huống một package nằm trong nhiều named module cùng loader và không có owner rõ ràng.

### 7.2 Mỗi module một loader

`defineModulesWithManyLoaders` tạo một loader cho mỗi module, với parentLoader được chỉ định. Những loader ấy có thể là **siblings** nhưng sử dụng direct delegation theo package export để phục vụ module readability. Bài chạy ở mục 4 minh họa trường hợp này.

### 7.3 Hàm ánh xạ module → custom loader

`defineModules` cho phép truyền hàm xác định loader tương ứng. Đây là phạm vi nâng cao cho container/plugin engine cần điều khiển namespace. Cần bảo đảm mỗi module/package có loader hợp lệ, không xung đột package và không sinh tuyến delegation không được kiểm soát.

### 7.4 Nhiều parent layers

API tĩnh của `ModuleLayer` nhận danh sách parent layers và `Configuration` có quan hệ parent tương ứng. Một module ở layer con có thể đọc module ở một trong các parent layers phù hợp. **Quan hệ layer parent** là quan hệ resolution giữa các layer; không trùng với kết quả `ClassLoader.getParent()` trên từng loader.

`ModuleLayer.findLoader(name)` hỗ trợ lấy loader gắn với module trong layer. `ModuleLayer.findModule(name)` có thể tìm cả parent layers. Một loader không “trở thành con của mọi loader trong mọi parent layer”; direct-delegation đến đúng module phụ thuộc routing ở runtime.

## 8. Ranh giới của đồ thị delegation

### 8.1 Đường tìm class có hướng

Nếu B hỏi A, không suy ra A hỏi được B. Tính bất đối xứng giúp giới hạn phụ thuộc. Ví dụ `m.beta requires m.alpha` không tạo cạnh tự động ngược lại.

### 8.2 Tên class chưa bảo đảm danh tính kiểu

JVM xác định class/interface bằng cặp **binary name + defining loader**. Nếu A và B mỗi bên định nghĩa `p.api.Contract` của riêng mình, đó là hai kiểu khác nhau dù tên và bytecode giống. Mục tiêu của direct delegation đúng cách là cho bên sử dụng **nhận cùng Class object** do loader sở hữu module định nghĩa.

Nếu một object của plugin không cast được sang interface của host, cần kiểm tra:

```java
System.out.println(object.getClass().getName());
System.out.println(object.getClass().getClassLoader());
System.out.println(object.getClass().getModule());
```

Đây là **đoạn minh họa trong method**, với `object` là instance đang điều tra. In thêm class loader của interface dự kiến và so identity của `Class<?>`, không chỉ so tên.

### 8.3 Không phải đồ thị nào cũng an toàn

Custom loader có thể override `loadClass` và tạo cạnh sang loader bất kỳ. Nếu thiết kế A → B và B → A, thiếu bộ nhớ đệm/kiểm tra và thứ tự khóa an toàn có thể gây vòng truy vấn hoặc deadlock. Chỉ thêm cạnh khi có ràng buộc rõ ràng về package, owner và chặn vòng.

### 8.4 Không thay thế security boundary

JPMS giới hạn readability và package accessibility; class loader isolation giới hạn kiểu nào được định nghĩa/nhìn thấy. Cả hai **không phải môi trường sandbox hoàn chỉnh** để thực thi bytecode không đáng tin. Đừng dựa vào “parent = null” hoặc “plugin có loader riêng” như giải pháp bảo mật duy nhất.

### 8.5 Resource lookup có luật khác

`getResource` và `getResources` cũng có cơ chế delegation và chịu quy tắc đóng gói của named modules. Một số API của `ModuleLayer` quy định tìm resource trong các module của layer trước parent; resource nằm trong package không được `opens` công khai có thể bị ẩn. Vì thế “class B tìm được từ loader A” không suy ra mọi resource `p/alpha/config.properties` đều tra được qua B.

## 9. Nhiều luồng: lý do tài liệu ClassLoader nhắc parallel capable

JavaDoc `ClassLoader` cảnh báo rằng khi delegation **không phân cấp nghiêm ngặt**, class loader cần hỗ trợ **parallel-capable loading** để tránh deadlock do giữ lock trên loader trong khi yêu cầu loader khác.

Trong cơ chế cổ điển, `loadClass` có thể được đồng bộ hóa theo một khóa trên loader. Nếu:

```text
Thread 1: giữ lock Loader A → chờ Loader B
Thread 2: giữ lock Loader B → chờ Loader A
```

thì hai thread có thể đứng chờ nhau. Khi loader **parallel capable**, việc đồng bộ có thể dựa trên `getClassLoadingLock(name)` cho mỗi tên class, giúp giảm việc khóa cả loader trong một số tình huống. Tuy nhiên parallel capable **không chứng minh mọi đồ thị có vòng đều an toàn**: deadlock vẫn có thể xảy ra nếu thiết kế routing/khóa sai.

Một custom subclass dự định tham gia graph delegation phải hiểu `registerAsParallelCapable`. Đăng ký cần gọi từ quá trình khởi tạo class của subclass, và điều kiện về các superclass trong chuỗi class loader cũng phải phù hợp. Không gọi hàm ấy bừa trong mỗi `loadClass` hay coi nó như công tắc chữa mọi deadlock.

Đây là lý do loader của module system có cơ chế routing và đồng bộ chuyên biệt. Ở tầng ứng dụng, ưu tiên API `ModuleLayer` được chuẩn hóa thay vì tự tạo nhiều override `loadClass` có vòng tham chiếu giữa chúng.

## 10. Một cách kiểm tra graph qua dữ liệu runtime

Khi sự cố liên quan tới class loader, có thể kiểm tra ba lớp thông tin:

**A. Parent chain**

```java
static void printParents(ClassLoader loader) {
    while (loader != null) {
        System.out.println(loader);
        loader = loader.getParent();
    }
    System.out.println("bootstrap (null)");
}
```

Đây là **method minh họa**, thêm vào một class trước khi gọi. Chuỗi parent chỉ trả lời “cha của ai”, **không biểu diễn cạnh direct delegation** như B → A trong ví dụ.

**B. Module và defining loader của class thực tế**

```java
static void inspect(Class<?> type) {
    Module module = type.getModule();
    System.out.println("class=" + type.getName());
    System.out.println("module=" + module.getName());
    System.out.println("loader=" + type.getClassLoader());
}
```

`module.getName()` có thể là `null` với unnamed module; `getClassLoader()` là `null` với class bootstrap. Cần in loader cả **ở source của request và ở class kết quả** để phân biệt initiating và defining.

**C. Graph module đã resolve**

```java
Module client = layer.findModule("m.beta").orElseThrow();
Module api = layer.findModule("m.alpha").orElseThrow();

System.out.println("reads: " + client.canRead(api));
System.out.println("exports: " + api.isExported("p.alpha", client));
System.out.println("different loaders: "
        + (layer.findLoader("m.beta") != layer.findLoader("m.alpha")));
```

Đây là **đoạn minh họa thêm vào `Launcher.main` của mục 4**, sau khi biến `layer` đã được tạo. Trong ví dụ, ba dòng in `true`. `Module.canRead` và `isExported` chứng minh module-level visibility; kết quả hai loader khác nhau chứng minh class không bắt buộc nằm chung loader. Cạnh direct delegation được quan sát qua `b.loadClass(...)` rồi so `Class<?>` identity.

**D. Class loading logs**

Trên HotSpot hỗ trợ unified logging, thử:

```bash
java -Xlog:class+load=info -cp . Launcher
```

Log có thể cho biết class được nạp từ nguồn nào nhưng thường khá dài; định dạng không cố định giữa các JVM. Không sử dụng log ấy làm bằng chứng duy nhất cho toàn bộ graph. Nếu cần, tạm thêm log trong custom `loadClass`/`findClass` để quan sát tuyến request, đồng thời tránh logging gây recursion.

## 11. Lỗi và cách chẩn đoán

| Triệu chứng | Câu hỏi cần kiểm tra |
| --- | --- |
| `ClassNotFoundException` khi B tìm class A | Package có trong module nào? Module có được resolve vào layer? Có export/đường delegation phù hợp? |
| `NoClassDefFoundError` lúc liên kết/thực thi | Dependency runtime bị thiếu, lỗi linking khác, hoặc initialization trước đó thất bại? Xem `cause` |
| `IllegalAccessError` | Class được tìm thấy nhưng quyền Java access/module exports có cho phép gọi? |
| `InaccessibleObjectException` khi deep reflection | Package có `opens` phù hợp cho caller không? |
| `ClassCastException` giữa hai tên class giống nhau | Có hai defining loader định nghĩa trùng tên? Có cùng API class được chia sẻ đúng nơi không? |
| `LayerInstantiationException` khi tạo module layer | Có package trùng/split delegation/module mapping không hợp lệ không? |
| Lúc chạy bị treo khi hai loader nạp chéo | Có vòng delegation/khóa và custom loader không parallel capable không? |
| `ServiceLoader` không thấy provider | Module `uses`/`provides`, layer, loader, TCCL, package exports/visibility đã cấu hình đúng chưa? |

**Quy trình thực tế:**

1. Thu thập tên class cần load, caller và thao tác (bytecode, reflection, `Class.forName`, service discovery).
2. Xác định **định nghĩa class thực tế** bằng `getClassLoader()` và `getModule()`.
3. Lấy **parent chain** riêng, không nhầm nó với toàn bộ delegation.
4. Với named modules, kiểm tra module trong đúng `ModuleLayer`, `requires`/readability và `exports`/`opens`.
5. Nếu có nhiều loader ngang hàng, kiểm tra **package → module → loader mapping** và chiều cạnh direct-delegation.
6. Khi có trùng tên, so `Class<?>` identity và defining loader; khi có treo, kiểm tra các lock và vòng nạp chéo.
7. Khắc phục dependency/module mapping, kiến trúc API chia sẻ hoặc chính sách loader rồi chạy lại cùng đường gọi lỗi. Không dùng `--add-opens`/`--add-exports` như một bản vá tùy tiện khi nguyên nhân thật là class identity.

## 12. Các nhận định dễ sai

| Nhận định | Đính chính |
| --- | --- |
| “Mỗi loader chỉ có thể hỏi parent.” | Đúng với thuật toán parent-first mặc định; loader JPMS và custom loader có thể direct-delegate sang loader khác. |
| “Hai loader có cùng parent thì không nhìn thấy class của nhau.” | Module layer có thể tạo cạnh package/module từ B sang loader ngang hàng A. |
| “`getParent()` sẽ liệt kê mọi cạnh của graph.” | Nó chỉ cho biết parent; cạnh graph cần kiểm tra policy loader và module mapping. |
| “`requires` làm loader A thành parent của B.” | `requires` tạo module readability, không biến đổi cấu trúc parent. |
| “B load được class là B đã định nghĩa class đó.” | B có thể trả `Class<?>` do A hoặc bootstrap định nghĩa. |
| “Platform luôn delegate sang application.” | Chỉ có thể xuất hiện khi điều kiện module nâng cấp/đọc module phù hợp; không phải đường fallback vô điều kiện. |
| “Named module nào cũng có loader riêng.” | Boot layer có thể gán nhiều module cho cùng built-in loader; layer mới được tạo theo API one/many/custom. |
| “Có readability tức là reflection private được.” | Còn cần exports/opens, quyền truy cập Java và ràng buộc reflection. |
| “Graph loader an toàn nếu chỉ override `loadClass`.” | Có nguy cơ vòng delegation và deadlock; cần thiết kế khóa và parallel-capable phù hợp. |
| “Class loader isolation tự bảo vệ khỏi mọi bytecode độc hại.” | Loader/module là cơ chế namespace và access-control, không đủ làm sandbox toàn diện. |

## 13. Câu hỏi tự kiểm tra

**Câu 1.** Loader A và B đều có parent là system loader. Trong ví dụ, tại sao B vẫn load được `p.alpha.Greeter` do A định nghĩa?

**Đáp án:** `defineModulesWithManyLoaders` tạo direct delegation dựa vào package thuộc module `m.alpha` được export phù hợp và được module `m.beta` đọc. A là loader ngang hàng của B, không cần là parent.

**Câu 2.** `b.getParent() != a` nhưng `b.loadClass("p.alpha.Greeter") == a.loadClass("p.alpha.Greeter")` có mâu thuẫn không?

**Đáp án:** Không. `getParent()` chỉ trả quan hệ cha; `loadClass` có thể delegate trực tiếp sang loader khác theo module mapping và nhận cùng `Class<?>`.

**Câu 3.** Tại sao việc `m.beta requires m.alpha` không đồng nghĩa mọi private method trong `m.alpha` đều gọi được từ `m.beta`?

**Đáp án:** Module readability không tự xóa quyền Java access. Public API còn cần package export phù hợp; deep reflection có thể cần `opens` và các điều kiện khác.

**Câu 4.** Vì sao platform loader đôi khi có thể delegate sang application loader dù application là con trong parent chain?

**Đáp án:** Trong một số cấu hình upgraded modules, platform module có thể đọc module được loader khác, chẳng hạn app loader, định nghĩa. Direct delegation theo module/package giải quyết tham chiếu đó mà không đổi `getParent()`.

**Câu 5.** Chạy `defineModulesWithOneLoader` thay vì `defineModulesWithManyLoaders` có xóa các quan hệ `requires` không?

**Đáp án:** Không. Chỉ đổi cách ánh xạ module vào class loader; module readability và exports/opens vẫn có hiệu lực.

**Câu 6.** Nếu class có cùng tên được định nghĩa độc lập ở A và B, JVM có xem là cùng kiểu không?

**Đáp án:** Không. Danh tính kiểu cần cả binary name và defining loader. Chia sẻ một class qua delegation trả về cùng `Class<?>` là cách tránh lỗi này khi thiết kế API giữa module/plugin.

**Câu 7.** Vì sao tạo hai custom loader delegate chéo không cẩn thận có thể deadlock?

**Đáp án:** Mỗi thread có thể giữ lock ở một loader rồi chờ loader còn lại. Với graph không phân cấp nghiêm ngặt cần thiết kế routing và concurrency, bao gồm parallel-capable loader khi phù hợp.

**Câu 8.** Để biết “class do ai định nghĩa”, dùng `getParent()` hay `Class.getClassLoader()`?

**Đáp án:** Dùng `Class.getClassLoader()` trên class đã nhận. `getParent()` chỉ cho biết parent của một loader; nó không xác định defining loader cho từng kết quả load class.

## Kết luận học tập

Quan hệ **parent** là một nhánh cấu trúc của class loader; quan hệ **module reads/exports** quyết định phụ thuộc và khả năng truy cập giữa named modules; quan hệ **delegation khi load class** có thể là một **đồ thị có hướng** với cạnh sang loader ngang hàng hoặc loader không phải tổ tiên. Vì vậy, để giải thích một class được tìm thấy từ đâu, hãy xem **package, module, module layer, loader được gán, đường ủy quyền và defining loader** thay vì chỉ lần theo `getParent()`.

Tài liệu chuẩn nên đối chiếu:

- [ClassLoader – Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html) (delegation, parallel-capable và ngoại lệ platform/application với upgraded modules).
- [ModuleLayer – Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ModuleLayer.html) (`defineModulesWithOneLoader`, `defineModulesWithManyLoaders`, ánh xạ package/module và direct delegation).
- [Module – Java SE 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html) (readability, exports, opens).
- [JVM Specification 17 – Loading, Linking and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html) (defining loader, initiating loader, danh tính kiểu và loading constraints).


</details>

- [Quay lại đầu trang](#back-to-top)
