# Custom ClassLoader

Các loader có sẵn đủ cho phần lớn ứng dụng. Nhưng hệ thống plugin đặt ra một bài toán khác:

```text
classpath của host application
→ không chứa implementation của plugin

plugin bytes
→ có thể nằm trong thư mục riêng, JAR được tải lên, database, network artifact
  hoặc một nguồn byte do ứng dụng quản lý
```

JVM vẫn cần một `Class<?>` để thực thi plugin. **Custom ClassLoader** là điểm mở rộng cho phép ứng dụng cung cấp cách tìm class bytes hoặc tạo một namespace loading riêng.

Mục tiêu của custom loader không phải “viết lại JVM”. Ta vẫn để JVM verify, link và initialize class. Custom loader chủ yếu quyết định **khi parent không đáp ứng yêu cầu thì bytes của class sẽ đến từ đâu và loader nào sẽ trở thành defining loader**.

## <a id="classloader-contract">Quan hệ giữa loadClass và findClass</a>

Đây là phân biệt quan trọng nhất khi tự viết loader:

```text
loadClass(...)
→ điều phối: đã load chưa? delegate parent? fallback?

findClass(...)
→ điểm mở rộng: loader hiện tại tự tìm/định nghĩa class thế nào?
```

`ClassLoader` mặc định đã cài hành vi parent-first trong `loadClass`. Vì vậy custom loader thông thường chỉ override `findClass`:

```java
public final class PluginClassLoader extends ClassLoader {
    public PluginClassLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = findPluginBytes(name);
        return defineClass(name, bytes, 0, bytes.length);
    }

    private byte[] findPluginBytes(String name) throws ClassNotFoundException {
        throw new ClassNotFoundException(name);
    }
}
```

Khi bên gọi dùng:

```java
Class<?> type = loader.loadClass("com.example.plugins.PaymentPlugin");
```

Luồng mặc định gần như:

```text
findLoadedClass
    ↓
delegate parent
    ↓ parent không tìm thấy
findClass của PluginClassLoader
    ↓
defineClass
```

### Khi nào mới override loadClass?

Chỉ khi loader thật sự cần chính sách lookup khác, ví dụ child-first có tách biệt chọn lọc. Lúc đó implementation phải tự giữ các invariant như:

- không định nghĩa cùng class hai lần trong cùng loader;
- locking đúng khi nhiều thread load cùng binary name;
- parent/shared API boundary rõ ràng;
- optional resolution đúng semantics.

Nếu bài toán chỉ là “class bytes nằm ở nguồn khác”, override `findClass` thường là lựa chọn đúng.

## <a id="define-class">defineClass biến byte[] thành Class như thế nào?</a>

`defineClass` là cầu nối từ binary bytes tới runtime class do loader hiện tại định nghĩa:

```java
byte[] bytes = ...;
Class<?> type = defineClass(
        "com.example.plugins.PaymentPlugin",
        bytes,
        0,
        bytes.length
);
```

Khi lời gọi thành công:

```text
binary name
  +
class bytes
  +
PluginClassLoader instance
        ↓ defineClass
Class<?> tại runtime có defining loader = instance PluginClassLoader
```

`defineClass` không bỏ qua bước kiểm tra của JVM. Nếu bytes sai format, name không phù hợp hoặc vi phạm loading constraint, runtime có thể ném các lỗi như `ClassFormatError`, `NoClassDefFoundError`, `LinkageError` hoặc `SecurityException` tùy nguyên nhân.

Một điểm rất quan trọng:

> **Defining loader là một phần của runtime type identity.**

Nếu hai instance `PluginClassLoader` khác nhau cùng gọi `defineClass` với cùng binary name và cùng byte content, JVM vẫn có thể tạo hai type khác nhau. Chương Class Identity sẽ chứng minh điều đó.

### defineClass chưa đồng nghĩa với initialization

Sau khi `defineClass` tạo `Class<?>`, class chưa nhất thiết đã chạy static initializer. Initialization vẫn theo các rule lifecycle đã học.

```text
defineClass
→ runtime Class được tạo
→ linking khi JVM cần
→ initialization chỉ khi trigger phù hợp
```

## <a id="custom-source">Nạp class bytes từ nguồn tùy chỉnh</a>

Nguồn tùy chỉnh có thể là bất kỳ nơi nào ứng dụng có thể lấy đúng class-file bytes. Để tập trung vào ClassLoader thay vì I/O, ta có thể dùng in-memory map:

```java
import java.util.Map;

public final class InMemoryPluginClassLoader extends ClassLoader {
    private final Map<String, byte[]> classes;

    public InMemoryPluginClassLoader(
            ClassLoader parent,
            Map<String, byte[]> classes
    ) {
        super(parent);
        this.classes = Map.copyOf(classes);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] bytes = classes.get(name);
        if (bytes == null) {
            throw new ClassNotFoundException(name);
        }
        return defineClass(name, bytes, 0, bytes.length);
    }
}
```

Cách dùng:

```java
Map<String, byte[]> pluginBytes = Map.of(
        "com.example.plugins.PaymentPlugin",
        compiledPaymentPluginBytes
);

ClassLoader loader = new InMemoryPluginClassLoader(
        Plugin.class.getClassLoader(),
        pluginBytes
);

Class<?> implementation =
        loader.loadClass("com.example.plugins.PaymentPlugin");
```

Ở đây `compiledPaymentPluginBytes` chỉ là **`byte[]` chứa class-file bytes đã được compile/read sẵn**. Ta cố ý bỏ qua phần I/O/compile để tập trung vào ClassLoader contract; chương này không yêu cầu bạn tự viết compiler hay tự đọc JAR bằng tay.

Điểm thiết kế đáng chú ý là parent:

```java
Plugin.class.getClassLoader()
```

Nếu `Plugin` là API dùng chung của host, đặt loader của API làm parent giúp plugin implementation resolve về **cùng một `Plugin` type** mà host đang dùng.

Trong môi trường thực tế, nguồn có thể là URL hoặc thư mục JAR. `URLClassLoader` vẫn là một implementation có thể dùng khi phù hợp; custom loader chỉ cần khi contract lookup/tách biệt của ứng dụng khác với loader có sẵn.

### Đừng gắn custom loader với một mô hình lưu trữ cụ thể

ClassLoader contract quan tâm tới binary name và bytes. Cách lấy bytes thuộc về ứng dụng:

```text
binary name
→ bộ phân giải nguồn
→ byte[]
→ defineClass
```

Tách bộ phân giải nguồn khỏi logic loader giúp test, cache và dọn dẹp lifecycle dễ hơn.

## <a id="custom-loader-safety">Đồng bộ, package và ranh giới an toàn</a>

Custom loading dễ tạo lỗi vì class definition có state. Một loader phải tránh race:

```text
Thread A load PaymentPlugin
Thread B load PaymentPlugin cùng lúc
→ không được define cùng binary name hai lần trong cùng loader
```

`ClassLoader.loadClass` mặc định sử dụng class-loading lock phù hợp. Đây là thêm một lý do để giữ `loadClass` mặc định khi chỉ cần custom `findClass`.

> **Nâng cao — có thể bỏ qua ở lượt học đầu:** nếu xây loader có parallel loading phức tạp, `ClassLoader.registerAsParallelCapable()` tồn tại cho subclass hierarchy đáp ứng contract tương ứng. Đây là tối ưu và thiết kế nâng cao, không phải bước bắt buộc cho custom loader cơ bản.

Package cũng có constraint. Các class trong cùng package runtime có liên hệ với package metadata, signer/certificate constraints và module/package access rules. Custom loader không nên coi việc ghép một binary name tùy ý với byte array là hoàn toàn tự do.

> **Nâng cao — production loader:** `defineClass` còn có overload nhận `ProtectionDomain`; signed JAR/package certificate/sealing rules có thể ảnh hưởng cách class được định nghĩa an toàn. Module này chỉ cần biết ranh giới đó tồn tại, không học sâu security domain/sealing.

Đặc biệt, **cùng tên package trong source chưa đủ để hai class thuộc cùng runtime package**. Runtime package identity còn phụ thuộc defining ClassLoader. Vì vậy:

```text
demo.internal.First  — defined by loader A
demo.internal.Second — defined by loader B

cùng textual package name
+ defining loader khác
→ runtime package khác
→ không được package-private access lẫn nhau chỉ vì cùng tên package
```

Đây là lý do các class cần cộng tác qua package-private member thường phải nằm trong cùng loader/package boundary đã được thiết kế, thay vì bị chia tùy ý qua nhiều custom loader.

Một ranh giới khác là namespace nền tảng:

```text
java.*
→ custom ClassLoader.defineClass không được phép chiếm
```

### Dọn dẹp là một phần của thiết kế

Nếu loader đọc JAR/resource có handle cần đóng, lifecycle plugin phải có bước dọn dẹp tương ứng. Ví dụ khi dùng `URLClassLoader`:

```java
try (var loader = new java.net.URLClassLoader(urls, parent)) {
    Class<?> pluginType = loader.loadClass(pluginName);
    // dùng plugin
}
```

`close()` giải phóng resource mà loader đang giữ; nó **không ép class unload ngay**. Unloading còn phụ thuộc reachability và GC, sẽ được học ở chương cuối.

Danh sách kiểm tra thực tế cho custom loader:

```text
1. Có thật sự cần nguồn tùy chỉnh hoặc tách biệt namespace?
2. API dùng chung nằm ở parent nào?
3. Có thể chỉ override findClass không?
4. Loader có define cùng name an toàn khi concurrent không?
5. Resource/JAR handle được đóng lúc plugin dừng chưa?
6. Có reference dài hạn giữ loader sau khi dừng không?
```

Sau khi một custom loader định nghĩa class, câu hỏi khó tiếp theo xuất hiện: **hai class cùng tên nhưng do hai loader khác nhau định nghĩa có phải cùng type không?**
