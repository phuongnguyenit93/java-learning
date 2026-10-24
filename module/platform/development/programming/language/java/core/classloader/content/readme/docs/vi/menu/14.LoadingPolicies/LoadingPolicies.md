<a id="back-to-top"></a>

# Chính sách nạp

## Menu
- [So sánh parent-first và child-first](#loading-policies-001)
- [Khi nào việc ghi đè loadClass là cần thiết](#loading-policies-002)
- [Thiết kế chính sách chọn lọc theo package](#loading-policies-003)
- [Ủy quyền bắt buộc cho kiểu nền tảng và API dùng chung](#loading-policies-004)
- [Cô lập nhiều phiên bản thư viện giữa các plugin](#loading-policies-005)
- [Ngăn lớp và tài nguyên dùng các thứ tự ưu tiên khác nhau](#loading-policies-006)
- [Rủi ro nạp trùng API, annotation hoặc kiểu exception](#loading-policies-007)
- [Phát hiện chu trình ủy quyền và tránh gọi lặp vô hạn](#loading-policies-008)
- [Đánh đổi giữa cô lập, tương thích và khả năng chẩn đoán](#loading-policies-009)

## <a id="loading-policies-001">So sánh parent-first và child-first</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh parent-first và child-first trong Java ClassLoader

**Parent-first** và **child-first** là hai **chính sách ưu tiên tìm lớp** khi một class loader cần xử lý tên class chưa được nạp. Chúng quyết định **hỏi parent trước hay thử nguồn class của chính loader trước**, từ đó ảnh hưởng tới phiên bản thư viện được sử dụng, runtime type identity, khả năng tích hợp plugin và cách xử lý xung đột dependency.

Trong Java, `ClassLoader.loadClass` mặc định triển khai **parent-first delegation**. **Child-first** là chiến lược tùy biến, thường gặp trong hệ plugin, application server hoặc môi trường cô lập phiên bản thư viện. Nó **không phải một cờ có sẵn** trong constructor `URLClassLoader`. Muốn dùng child-first cần một loader triển khai chính sách riêng, đồng thời phải giữ những ngoại lệ cần thiết cho các lớp nền tảng và API dùng chung.

Bài học thuộc nhóm **Chính sách nạp** của **Java core – classloader**. Nội dung tập trung vào thứ tự lookup, ví dụ Java 17+ có thể chạy, ảnh hưởng tới **defining loader**, SPI/DTO dùng chung, resources, các lỗi thường gặp và cách chọn chính sách có căn cứ.

## 1. “First” là ưu tiên lookup, không phải thứ tự kế thừa Java

Giả sử host và plugin cùng có một class tên `lib.Version`:

```text
HOST / parent loader                 PLUGIN / child loader
lib.Version  ->  "host"              lib.Version  ->  "plugin"
                  ▲                                  │
                  └──── parent của plugin loader ────┘
```

Khi code yêu cầu `pluginLoader.loadClass("lib.Version")`:

- **Parent-first:** thử parent trước. Nếu parent tìm được `lib.Version`, child trả đúng class đó; bản plugin có thể **không bao giờ được định nghĩa**.
- **Child-first:** thử nguồn local của plugin trước. Nếu child tự tìm/định nghĩa được `lib.Version`, bản plugin được chọn; nếu không có local class, tùy chính sách mới fallback sang parent.

Đây là **hai chính sách lựa chọn lớp**. Chúng **không** thay đổi:

- Java superclass của `PluginLoader` (từ khóa `extends`);
- parent object trả về bởi `pluginLoader.getParent()`;
- những class đã được định nghĩa trước đó;
- classpath dùng lúc **biên dịch**;
- ngữ nghĩa tạo object hay gọi constructor của class đã nạp.

**Điểm cốt lõi:** cùng một binary name có thể dẫn tới **hai runtime types khác nhau** nếu hai class loaders riêng biệt tự định nghĩa nó. JVM định danh kiểu bởi **(binary name, defining loader)**, không chỉ bằng chuỗi tên class.

## 2. Luồng mặc định: parent-first

Java 17 `ClassLoader.loadClass(String, boolean)` có thuật toán khái niệm:

```text
loadClass(name)
      │
      ▼
findLoadedClass(name)
      ├── đã nạp → trả Class<?> được ghi nhận
      │
      └── chưa
            │
            ▼
      hỏi parent.loadClass(name)
            │
            ├── thành công → trả class do parent/ancestor tìm được
            │
            └── ClassNotFoundException
                       │
                       ▼
                 findClass(name)
                       │
                       ├── thành công → class do child định nghĩa
                       └── thất bại → ClassNotFoundException
```

Ba điều cần nhớ:

1. **Kiểm tra đã nạp trước:** JVM không tạo thêm một định nghĩa class khác cùng binary name trong cùng defining loader chỉ vì `loadClass` được gọi lại.
2. **Parent có cơ hội trước:** nếu `parent.loadClass` thành công, child không cần dùng `findClass` cho tên ấy.
3. **Child là phương án sau:** chỉ khi parent không tìm thấy theo logic mặc định, child mới thử nguồn class của mình.

`ClassLoader.loadClass(String)` thông thường gọi overload có `resolve=false` và không tự yêu cầu static initialization. Loading, linking, initialization là các khái niệm khác với chính sách ưu tiên parent/child.

Lưu ý sơ đồ thể hiện **hợp đồng `ClassLoader` mặc định**. Built-in loaders trong JDK 9+ có logic module-aware, custom loaders có thể override `loadClass`. Không mô tả sơ đồ tuyến tính này như quy tắc tuyệt đối của mọi loader hiện đại.

## 3. Luồng child-first: ưu tiên nguồn riêng, nhưng phải có giới hạn

Một loader child-first phổ biến triển khai ý tưởng:

```text
loadClass(name)
      │
      ▼
findLoadedClass(name)
      ├── đã có → trả Class<?>
      └── chưa
           │
           ▼
     Đây có phải lớp nền tảng / API bắt buộc chia sẻ?
           ├── có → dùng parent-first cho namespace bảo vệ
           │
           └── không
                │
                ▼
          thử child.findClass(name)
                ├── tìm thấy → trả class child định nghĩa
                └── ClassNotFoundException
                          │
                          ▼
                    thử parent.loadClass(name)
```

**Quan trọng:** chỉ riêng ý tưởng “child trước” chưa đủ để có implementation đúng. Một custom loader thực tế còn phải:

- Dùng `findLoadedClass` để tránh định nghĩa trùng class.
- Đồng bộ lookup đúng cách, ví dụ `synchronized (getClassLoadingLock(name))`.
- Không nuốt mọi `LinkageError` hoặc `SecurityException` để fallback tùy tiện.
- Giữ một **shared API namespace** luôn resolve về đúng runtime type của host.
- Không cố tự định nghĩa những lớp thuộc namespace bảo vệ như `java.*`.
- Cân nhắc `registerAsParallelCapable()` nếu định triển khai concurrent loading phức tạp; không mặc nhiên loader nào cũng parallel-capable.
- Duy trì tính nhất quán của package sealing, module access và resource/service discovery.

**Không phải mọi package nên child-first.** Hệ plugin thông thường cần **parent-first cho contract**, **child-first có chọn lọc cho thư viện private**.

## 4. Bảng so sánh quyết định

| Tiêu chí | Parent-first | Child-first |
| --- | --- | --- |
| Nguồn được thử đầu tiên | Parent/ancestor | Nguồn riêng của child (trừ namespace bảo vệ) |
| Triển khai trong `ClassLoader` mặc định | Có | Không, thường cần override |
| Với class trùng tên ở host và plugin | Bản host thường thắng | Bản plugin có thể thắng |
| Chia sẻ host-defined SPI/DTO | Thuận tiện nếu API ở parent | Phải bảo vệ API package qua delegation |
| Cô lập phiên bản dependency private | Khó nếu parent đã có bản cùng tên | Thuận lợi hơn khi child chứa bản phù hợp |
| Nguy cơ | Host library shadow plugin library; version skew | Tạo bản sao API gây `ClassCastException`, `LinkageError` |
| JDK classes | Thường ủy quyền từ child qua parent | Vẫn cần bảo đảm lớp nền tảng có đường nạp hợp lệ |
| Sự phức tạp | Thấp hơn, hợp đồng quen thuộc | Cao hơn: đồng bộ, fallback, constraints, module/resource |
| Có thay parent object? | Không bắt buộc | Không bắt buộc |
| Có bảo đảm sandbox an toàn? | Không | Không |

Không có lựa chọn “tốt nhất tuyệt đối”. **Parent-first** phù hợp nếu hệ thống có dependency graph thống nhất và muốn dùng chung nhiều API; **child-first chọn lọc** hữu ích nếu các plugin phải dùng những versions thư viện khác nhau nhưng vẫn cần một contract chung.

## 5. Chuẩn bị thực hành A — cùng tên class ở host và plugin

Thực hành này dùng `URLClassLoader` để tạo **parent-first loader** và một **child-first loader tùy biến**. Chúng cùng nhìn **một thư mục plugin**, nhưng khác thứ tự ưu tiên.

Cây thư mục:

```text
policy-lab/
├── src/
│   ├── hostlib/
│   │   └── lib/Version.java
│   ├── pluginlib/
│   │   └── lib/Version.java
│   └── app/
│       └── demo/PolicyDemo.java
├── hostlib-out/      (sinh bởi javac)
├── pluginlib-out/    (sinh bởi javac)
└── app-out/          (sinh bởi javac)
```

**`src/hostlib/lib/Version.java`:**

```java
package lib;

public final class Version {
    public static String origin() {
        return "host";
    }
}
```

**`src/pluginlib/lib/Version.java`:**

```java
package lib;

public final class Version {
    public static String origin() {
        return "plugin";
    }
}
```

Hai source file khai báo **cùng binary name `lib.Version`**, nhưng tạo ra **hai thư mục binary khác nhau**. Không compile cả hai cùng một output folder; mục đích là quan sát việc **chọn class runtime** chứ không phải khai báo hai lớp trùng trong một Java package của cùng build.

### Child-first implementation có kiểm soát

Tệp **`src/app/demo/PolicyDemo.java`**:

```java
package demo;

import lib.Version;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class PolicyDemo {
    static final class ChildFirstLoader extends URLClassLoader {
        ChildFirstLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> found = findLoadedClass(name);

                if (found == null) {
                    if (name.startsWith("java.")) {
                        // Namespace nền tảng: để parent/ancestor xử lý.
                        found = super.loadClass(name, false);
                    } else {
                        try {
                            // Ưu tiên URL của chính child.
                            found = findClass(name);
                        } catch (ClassNotFoundException notLocal) {
                            // Chỉ fallback khi thực sự không có local class.
                            found = super.loadClass(name, false);
                        }
                    }
                }

                if (resolve) {
                    resolveClass(found);
                }
                return found;
            }
        }
    }

    private static String origin(ClassLoader loader) throws Exception {
        Class<?> type = loader.loadClass("lib.Version");
        return (String) type.getMethod("origin").invoke(null);
    }

    public static void main(String[] args) throws Exception {
        URL pluginClasses = Path.of("pluginlib-out")
                .toAbsolutePath().toUri().toURL();
        URL[] urls = { pluginClasses };
        ClassLoader host = PolicyDemo.class.getClassLoader();

        System.out.println("host=" + Version.origin());

        try (URLClassLoader parentFirst =
                     new URLClassLoader(urls, host);
             ChildFirstLoader childFirst =
                     new ChildFirstLoader(urls, host)) {

            System.out.println("parent-first=" + origin(parentFirst));
            System.out.println("child-first=" + origin(childFirst));

            Class<?> fromParent = parentFirst.loadClass("lib.Version");
            Class<?> fromChild = childFirst.loadClass("lib.Version");

            System.out.println("parent shares host=" +
                    (fromParent == Version.class));
            System.out.println("child differs from host=" +
                    (fromChild != Version.class));
            System.out.println("child defined locally=" +
                    (fromChild.getClassLoader() == childFirst));
        }
    }
}
```

**Biên dịch** trong `policy-lab` bằng JDK 17+:

```sh
javac --release 17 -d hostlib-out src/hostlib/lib/Version.java
javac --release 17 -d pluginlib-out src/pluginlib/lib/Version.java
javac --release 17 -cp hostlib-out -d app-out src/app/demo/PolicyDemo.java
```

Chạy **Windows PowerShell/CMD**, classpath phân tách bằng dấu `;`:

```powershell
java -cp "app-out;hostlib-out" demo.PolicyDemo
```

Chạy **Linux/macOS**, phân tách bằng dấu `:`:

```sh
java -cp "app-out:hostlib-out" demo.PolicyDemo
```

**Stdout của lệnh `java` với layout này:**

```text
host=host
parent-first=host
child-first=plugin
parent shares host=true
child differs from host=true
child defined locally=true
```

### Giải thích từng dòng

- **`host=host`:** `demo.PolicyDemo` sử dụng `lib.Version` từ `hostlib-out` trong application classpath.
- **`parent-first=host`:** `URLClassLoader` mặc định hỏi host loader trước. Vì parent đã có `lib.Version`, nó trả bản `host` và không tìm bản plugin.
- **`child-first=plugin`:** subclass `ChildFirstLoader` thử `findClass("lib.Version")` trước. Nó đọc `pluginlib-out/lib/Version.class` và tự định nghĩa runtime type riêng.
- **`parent shares host=true`:** cùng `Class<?>` với `Version.class` của host; vì vậy hai lời gọi dùng chung class identity.
- **`child differs from host=true`:** tên `lib.Version` giống, nhưng defining loaders khác nhau.
- **`child defined locally=true`:** `getClassLoader()` của `Class<?>` trả đúng `childFirst`, minh họa kết quả lookup là bản local.

**Giới hạn ví dụ:** đây là một loader đơn giản để giải thích chính sách. Nó dùng parent fallback cho `java.*`, chưa xây dựng đầy đủ package/module policy cho framework production, cũng chưa xử lý mọi loại tài nguyên hoặc phiên bản plugin. Không copy nguyên xi làm class loader cho mã bên thứ ba không tin cậy.

## 6. Vì sao ví dụ A không cần đổi parent?

Trong ví dụ, **cả parent-first và child-first đều có cùng parent object**:

```java
ClassLoader host = PolicyDemo.class.getClassLoader();
URLClassLoader parentFirst = new URLClassLoader(urls, host);
ChildFirstLoader childFirst = new ChildFirstLoader(urls, host);
```

Đây là **snippet minh họa lại** có thể nằm trong method nếu các biến và imports có sẵn; khi dùng thực tế, phải đóng hai loaders đúng lúc.

Sự khác nhau ở `loadClass`:

- Với parent-first, kế hoạch mặc định là hỏi `host.loadClass`, rồi mới gọi `findClass` của child.
- Với child-first, `findClass` của child được gọi sớm cho những tên không thuộc namespace bảo vệ. Chỉ khi không tìm được local class mới hỏi parent.

**Parent là quan hệ giữa loader objects**, không phải cú pháp `extends`. Dùng cùng object parent **không khiến hai policies cho cùng kết quả**, vì mỗi loader có thể chọn thứ tự gọi parent khác nhau.

Ngược lại, chỉ đổi parent trong constructor `URLClassLoader` thông thường cũng có thể thay đổi class được tìm, nhưng **không có nghĩa đã triển khai child-first**. Hãy phân biệt **chọn nguồn có thể thấy** với **thứ tự ưu tiên giữa các nguồn đó**.

## 7. Cùng tên không phải cùng kiểu: runtime identity và casting

Một type trong JVM được xác định bằng cặp:

```text
(binary name, defining loader)
```

Trong ví dụ A:

```text
"lib.Version" + host loader       → host Class
"lib.Version" + childFirst loader → plugin Class
```

Do đó:

```java
fromParent.getName().equals(fromChild.getName()); // true
fromParent == fromChild;                          // false
```

Đây là snippet minh họa khi hai biến đã được khai báo từ ví dụ A. Nếu `lib.Version` là class nghiệp vụ dùng để truyền object giữa host và plugin, bản child-first **có thể không cast được** sang bản host, dù method signatures nhìn giống nhau.

Nếu chỉ dùng internal library `lib.Version` để tính toán và trả `String`, hai definitions riêng có thể không gây vấn đề. Nhưng nếu bản library này lộ ra trong public API (parameter/return type/exception/annotation), vấn đề identity trở thành lỗi boundary.

**Không dùng tên class làm tiêu chuẩn tương đương runtime.** Muốn kiểm tra một plugin implements đúng interface của host, dùng `HostApi.class.isAssignableFrom(pluginClass)` và so sánh `Class<?>` từ hai bên khi cần.

## 8. Ranh giới thiết kế: API phải chia sẻ, dependency private có thể cô lập

Giả sử host có interface:

```java
package api;

public interface Greeter {
    String greet(String name);
}
```

Nếu plugin implements **đúng runtime `api.Greeter`** của host, host có thể cast instance sang `Greeter`. Nếu child-first loader **tự định nghĩa một bản sao `api.Greeter`**, kết quả có thể là:

```text
HostLoader defines api.Greeter    → interface A
PluginLoader defines api.Greeter  → interface B

PluginImpl implements B
Host cần interface A

A.getName() == B.getName()       → cùng binary name
A == B                           → false
A.isAssignableFrom(PluginImpl)   → false
```

Mã source interface **giống từng ký tự** cũng không gộp hai types thành một. Đây là lý do **child-first thuần túy** thường không phù hợp cho toàn bộ package.

Mô hình thực tế hay dùng là **delegation chọn lọc**:

| Namespace | Chính sách đáng cân nhắc | Lý do |
| --- | --- | --- |
| `java.*` và các platform APIs phù hợp | Parent/platform delegation | Không cố tự định nghĩa lớp nền tảng |
| `api.*` (host SPI/DTO/callback) | Parent-first bắt buộc cho API chung | Bảo vệ type identity giữa host và plugin |
| `impl.*` | Child-first | Plugin implementation có thể nằm trong JAR riêng |
| `vendor.*` private | Child-first nếu host cũng có phiên bản khác | Cho phép plugin tự quản dependency version |
| Unknown name | Fallback theo chính sách đã định | Cần không nuốt lỗi liên kết và không tạo vòng lặp |

Namespace `api.*` là **tên ví dụ**, không phải package chuẩn của JDK. Trong production, hãy liệt kê chính xác các contract packages và types lộ qua method descriptors, không chỉ dựa vào một prefix đơn giản nếu API phân bố nhiều package.

## 9. Thực hành B — child-first có ngoại lệ để chia sẻ API của host

Ví dụ sau cố ý đóng gói **hai bản bytes của cùng `api.Greeter`** ở hai nguồn: `api-out` của host và `plugin-out` của plugin. Điều này giúp thấy vì sao phải có quy tắc **parent-first riêng cho `api.*`**, dù implementation dùng child-first.

Cây tệp:

```text
shared-api-lab/
└── src/
    ├── api/api/Greeter.java
    ├── plugin/impl/PluginGreeter.java
    └── host/demo/SelectiveDemo.java
```

**`src/api/api/Greeter.java`:**

```java
package api;

public interface Greeter {
    String greet(String name);
}
```

**`src/plugin/impl/PluginGreeter.java`:**

```java
package impl;

import api.Greeter;

public final class PluginGreeter implements Greeter {
    public PluginGreeter() {
    }

    @Override
    public String greet(String name) {
        return "Hello, " + name;
    }
}
```

**`src/host/demo/SelectiveDemo.java`:**

```java
package demo;

import api.Greeter;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class SelectiveDemo {
    static final class SelectiveLoader extends URLClassLoader {
        private final boolean protectSharedApi;

        SelectiveLoader(URL[] urls, ClassLoader parent,
                        boolean protectSharedApi) {
            super(urls, parent);
            this.protectSharedApi = protectSharedApi;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> clazz = findLoadedClass(name);

                if (clazz == null) {
                    boolean delegate = name.startsWith("java.")
                            || (protectSharedApi && name.startsWith("api."));

                    if (delegate) {
                        clazz = super.loadClass(name, false);
                    } else {
                        try {
                            clazz = findClass(name);
                        } catch (ClassNotFoundException missingLocal) {
                            clazz = super.loadClass(name, false);
                        }
                    }
                }

                if (resolve) {
                    resolveClass(clazz);
                }
                return clazz;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        URL pluginUrl = Path.of("plugin-out")
                .toAbsolutePath().toUri().toURL();
        URL[] urls = { pluginUrl };
        ClassLoader sharedParent = Greeter.class.getClassLoader();

        try (SelectiveLoader protectedLoader =
                    new SelectiveLoader(urls, sharedParent, true);
             SelectiveLoader naiveLoader =
                    new SelectiveLoader(urls, sharedParent, false)) {

            Class<?> good =
                    protectedLoader.loadClass("impl.PluginGreeter");
            Class<?> goodApi = good.getInterfaces()[0];

            System.out.println("protected API shared=" +
                    (goodApi == Greeter.class));
            System.out.println("protected implementation local=" +
                    (good.getClassLoader() == protectedLoader));
            System.out.println("protected assignable=" +
                    Greeter.class.isAssignableFrom(good));

            Greeter service = (Greeter)
                    good.getDeclaredConstructor().newInstance();
            System.out.println("protected result=" +
                    service.greet("Java"));

            Class<?> bad = naiveLoader.loadClass("impl.PluginGreeter");
            Class<?> badApi = bad.getInterfaces()[0];

            System.out.println("naive same name=" +
                    badApi.getName().equals(Greeter.class.getName()));
            System.out.println("naive same Class=" +
                    (badApi == Greeter.class));
            System.out.println("naive assignable=" +
                    Greeter.class.isAssignableFrom(bad));
        }
    }
}
```

**Biên dịch** từ thư mục `shared-api-lab`:

```sh
javac --release 17 -d api-out src/api/api/Greeter.java
javac --release 17 -cp api-out -d plugin-out src/plugin/impl/PluginGreeter.java
javac --release 17 -d plugin-out src/api/api/Greeter.java
javac --release 17 -cp api-out -d host-out src/host/demo/SelectiveDemo.java
```

Lệnh thứ ba cố ý sao chép **binary API** vào output của plugin bằng cách compile cùng file source sang `plugin-out`. Trong môi trường production, **không nên đóng gói shared API duplicate** nếu tránh được; thao tác này chỉ nhằm mô phỏng lỗi một cách có kiểm soát.

Chạy **Windows**:

```powershell
java -cp "host-out;api-out" demo.SelectiveDemo
```

Chạy **Linux/macOS**:

```sh
java -cp "host-out:api-out" demo.SelectiveDemo
```

**Stdout của lệnh `java`:**

```text
protected API shared=true
protected implementation local=true
protected assignable=true
protected result=Hello, Java
naive same name=true
naive same Class=false
naive assignable=false
```

### Lý giải đường đi của hai loaders

**Loader có bảo vệ API:**

1. `impl.PluginGreeter` không thuộc namespace bảo vệ; child ưu tiên `findClass` và tự define implementation.
2. Khi JVM cần `api.Greeter` cho mệnh đề `implements`, `SelectiveLoader` phát hiện prefix `api.` và gọi `super.loadClass` theo parent-first.
3. Host parent nhìn thấy `api.Greeter` từ `api-out`, nên trả **đúng runtime interface** của host, dù trong `plugin-out` có bản sao.
4. `Greeter.class.isAssignableFrom(good)` đúng; ép kiểu và gọi method thành công.

**Loader ngây thơ không bảo vệ API:**

1. Cả `impl.PluginGreeter` và `api.Greeter` được child tìm trong `plugin-out`.
2. `api.Greeter` của child có cùng binary name với bản host nhưng **khác defining loader**.
3. Plugin class implements **bản interface child**, không phải interface host.
4. `Greeter.class.isAssignableFrom(bad)` trả `false`. Nếu thực hiện cast `(Greeter) bad.getDeclaredConstructor().newInstance()`, chương trình có thể ném `ClassCastException`.

**Hạn chế thiết kế trong bài:** kiểm tra prefix `api.` chỉ là bài tập. Một hệ thống sản xuất phải xác định **chính xác** những packages cần chia sẻ và loader nào sở hữu chúng, xét additional platform namespaces, JPMS, package sealing, class-loading concurrency và resource lookup. Việc gọi `super.loadClass` cho `api.*` sẽ vẫn fallback về local nếu parent **không có API**; do đó điều kiện tiên quyết là **shared parent thực sự định nghĩa/nhìn thấy đúng contract**.

## 10. Vì sao `java.*` cần ngoại lệ đặc biệt?

Java runtime có những namespace và module thuộc nền tảng mà custom loader không được tùy tiện tự định nghĩa. Trong loader child-first:

```java
if (name.startsWith("java.")) {
    return super.loadClass(name, false);
}
```

Đây là **snippet minh họa logic**, không phải override hoàn chỉnh (nó chưa có đồng bộ, loaded-cache hay resolve handling). Nó thể hiện ý định không tự tìm local `java.*` trước.

Lưu ý:

- Kiểm tra `java.` **không thay thế** mọi quy tắc module/JDK. Các platform APIs có thể nằm trong namespaces khác; implementation production nên có **policy đầy đủ** và hiểu JPMS.
- `ClassLoader.defineClass` có những kiểm tra riêng cấm định nghĩa tên class bắt đầu bằng `java.` trong loader ứng dụng thông thường.
- Chọn parent là `ClassLoader.getPlatformClassLoader()` có thể giúp child nhìn thấy toàn bộ **platform classes**, trong khi parent `null` chỉ dựa vào bootstrap mechanism, không bảo đảm mọi Java SE/JDK class đều ở bootstrap trên Java 9+.
- Không nhầm câu “tránh define `java.*`” với câu “mọi `java.*` do platform loader trực tiếp define”: nhiều class cốt lõi do bootstrap define, platform vẫn nhìn thấy qua delegation.

Một child-first loader an toàn về **đúng đắn của nạp lớp** vẫn **không** biến plugin code thành sandbox bảo mật. Class loader giải quyết namespace/visibility; code không tin cậy cần boundary bảo mật độc lập.

## 11. Vì sao không được bắt mọi lỗi để fallback parent?

Một mẫu sai:

```java
try {
    return findClass(name);
} catch (Throwable ignored) {
    return getParent().loadClass(name);
}
```

Đây là **mẫu sai**, không có đủ context để chạy độc lập. `Throwable` bao gồm nhiều lỗi không có nghĩa là “không tìm thấy class ở child”:

- `LinkageError` có thể biểu thị bytecode/API không tương thích, vi phạm loader constraint hoặc lỗi định nghĩa class.
- `ExceptionInInitializerError`/`NoClassDefFoundError` có thể cho thấy initialization trước đó hỏng hoặc dependency thiếu.
- `SecurityException` có thể phản ánh vi phạm quy tắc namespace/permissions.
- `OutOfMemoryError` không phải một tín hiệu lookup để âm thầm chuyển nguồn.
- `ClassFormatError` và `UnsupportedClassVersionError` cho thấy class bytes không phù hợp, không nên bị che bằng một bản class ngẫu nhiên của parent.

Fallback trong ví dụ chỉ diễn ra khi `findClass` ném **`ClassNotFoundException`**. Đây là tín hiệu “local không có định nghĩa class với tên này” trong API loader; khi đó thử parent là logic hợp lý. Mọi lỗi khác cần xử lý theo contract, log có ngữ cảnh và **không tráo sang version khác mà không được phép**.

Thậm chí `ClassNotFoundException` có thể phát sinh do custom loader logic nội bộ; hệ thống production cần phân biệt missing local class, missing dependency, access/JPMS failure và các nguyên nhân khác khi debug.

## 12. Đồng bộ, `findLoadedClass` và quy tắc chỉ define một lần

Khi nhiều threads cùng yêu cầu class `lib.Version`, một custom child-first loader không được để cả hai tự `defineClass` cùng binary name trong một loader instance. JVM có thể báo `LinkageError: attempted duplicate class definition` hoặc các lỗi liên quan.

Phần quan trọng của override ở hai ví dụ:

```java
synchronized (getClassLoadingLock(name)) {
    Class<?> existing = findLoadedClass(name);
    if (existing != null) {
        return existing;
    }

    // Thử local hoặc parent theo policy.
}
```

Đây là **snippet minh họa**, chưa có logic tìm/lưu kết quả đầy đủ. Điểm chính:

- `getClassLoadingLock(name)` cung cấp lock mà `ClassLoader` dùng cho yêu cầu nạp tên class. Với loader không được đăng ký parallel-capable, lock có thể là chính loader object; parallel-capable loaders có thể có lock theo class name.
- `findLoadedClass(name)` tra cứu class đã được ghi nhận do loader đó khởi xướng nạp. Nó không chỉ cho biết class tự child define; lớp từ parent cũng có thể được ghi nhận là đã load qua loader.
- Phải kiểm tra loaded class **trước** mỗi đường định nghĩa local.
- `resolveClass(clazz)` chỉ thực hiện khi `resolve` được yêu cầu, sau khi chọn được `Class<?>` thích hợp.
- Không dùng khóa toàn cục tùy tiện nếu cần performance, nhưng cũng không bỏ đồng bộ chỉ vì test đơn luồng chạy đúng.

### Parallel-capable loader

`ClassLoader.registerAsParallelCapable()` là API `protected static`. Khi tạo subclass có nhu cầu concurrency và muốn dùng locks theo từng binary name, cần đăng ký đúng thời điểm trong static initializer của subclass, hiểu quy tắc về superclass và registration. Không tự động thêm nó vào mọi snippet vì sẽ làm ví dụ so sánh chính sách khó đọc.

Một override sai trong loader phức tạp còn có nguy cơ **deadlock** nếu loaders có quan hệ delegation vòng tròn hoặc đồng bộ không nhất quán. Parent chain của `ClassLoader` chuẩn tránh một số dạng vòng lặp nhờ cách khởi tạo, nhưng custom graph/delegation ngoài parent có thể tạo thêm phụ thuộc giữa loaders. Thiết kế production phải kiểm tra concurrency và cycle detection.

## 13. Method linking và binary compatibility sau khi chọn class

Lookup ưu tiên chỉ quyết định **runtime class nào được dùng**. Sau đó JVM còn phải liên kết những method/field references trong bytecode:

```text
Plugin compile-time:
    lib.Parser.parse(String):Result  (v2)

Runtime parent-first:
    lib.Parser v1 chỉ có parse(byte[]):Result

Kết quả:
    runtime class được tìm nhưng method descriptor không tồn tại
    → có thể ném NoSuchMethodError tại điểm sử dụng
```

Vấn đề này không nhất thiết xuất hiện ngay khi `pluginLoader.loadClass("impl.Plugin")` trả về `Class<?>`; JVM có thể trì hoãn resolution đến lúc instruction cần method. Vì vậy việc “đã load thành công” **không bảo đảm** phiên bản dependency tương thích.

Các lỗi thường gặp:

| Lỗi | Điều có thể đã xảy ra |
| --- | --- |
| `ClassNotFoundException` | Loader được hỏi không tìm được class theo tên |
| `NoClassDefFoundError` | Dependency không được định nghĩa/sử dụng khi JVM cần hoặc class đã từng initialization lỗi |
| `NoSuchMethodError` | Runtime method reference không có method khớp descriptor |
| `NoSuchFieldError` | Runtime field reference không có field khớp name/descriptor |
| `IncompatibleClassChangeError` | Chênh lệch class/interface, static/instance hoặc hợp đồng liên kết khác |
| `IllegalAccessError` | Runtime access/module constraints không cho phép liên kết |
| `ClassCastException` | Runtime type không assignable với type host mong muốn |
| `LinkageError` loading constraint | Hai loader definitions không đáp ứng những ràng buộc type safety |
| `ExceptionInInitializerError` | Static initializer thất bại, không chỉ đơn thuần là chọn loader sai |

Để sửa, cần tìm **defining loader và artifact của đúng class runtime**, so bytecode descriptor và dependency graph. Không đổi child-first để che mọi `LinkageError` nếu chưa hiểu nguyên nhân.

## 14. Resources có nhất thiết child-first cùng class không?

**Không.** Một custom loader chỉ override `loadClass` để child-first vẫn có thể **giữ `getResource`/`getResources` parent-first mặc định**.

Ví dụ:

```text
Host classpath:
    config/theme.txt = host-theme

Plugin URL:
    config/theme.txt = plugin-theme
```

Dù `ChildFirstLoader` của ví dụ A định nghĩa `lib.Version` từ child URL, `child.getResource("config/theme.txt")` có thể trả **bản host** do resource lookup hỏi parent trước. Nếu plugin cần tài nguyên riêng, điều này phải được thiết kế/kiểm tra.

Các API:

- `ClassLoader.getResource(name)`: URL tài nguyên được tìm theo policy của loader.
- `ClassLoader.getResourceAsStream(name)`: InputStream hoặc `null` nếu không tìm thấy/mở không được; caller phải kiểm tra và đóng.
- `ClassLoader.getResources(name)`: Enumeration các URL có thể thấy, phù hợp điều tra resource trùng tên.
- `URLClassLoader.findResource(name)`: tìm trong các URL riêng của child, **không phải thay đổi class loading policy**.
- `getURLs()`: URL roots riêng của URLClassLoader, **không** bao gồm URL của parent.

Với hệ plugin cần isolation mạnh, phải xác định chính sách **class**, **resource** và **ServiceLoader discovery** riêng. Không suy từ “child-first classes” sang “child-first resources”.

## 15. Tự kiểm tra ưu tiên resource khi class dùng child-first
Trước khi đổi resource policy, dùng chính loader của thực hành A và đặt `config/theme.txt` ở cả host và plugin. Cho hai tệp nội dung khác nhau, rồi kiểm tra ba thao tác:

```java
String path = "config/theme.txt";
System.out.println(childFirst.getResource(path));  // parent-first mặc định
System.out.println(childFirst.findResource(path)); // chỉ nguồn URL của child
var urls = childFirst.getResources(path);
while (urls.hasMoreElements()) {
    System.out.println(urls.nextElement());       // thứ tự tất cả URL
}
```

Đoạn mã nằm trong phạm vi `try` đã khai báo `ChildFirstLoader childFirst`; cần xử lý `IOException` từ `getResources`. Các API trên trả URL hoặc enumeration, **không** trả nội dung trực tiếp. Hãy đọc từng URL để xác nhận thứ tự nguồn và lưu ý `ClassLoader.getResource` mặc định vẫn có thể chọn tài nguyên parent dù `loadClass` đã ưu tiên child. Nếu resource là schema/config bắt buộc khớp với implementation, hãy ghi rõ quy tắc override hoặc fail-fast. Với `META-INF/services`, cần xét đủ mọi descriptor thay vì chỉ URL đầu tiên.

## 16. Child-first trong mô hình plugin: thiết kế boundary rõ ràng

Hệ plugin thường có ba nhóm:

```text
                        SHARED HOST API
                         api.Plugin
                         api.Request
                         api.Response
                              ▲
                     parent-first cho api.*
                              │
                 ┌────────────┴────────────┐
                 │                         │
             PLUGIN A                   PLUGIN B
             impl.a.*                   impl.b.*
             vendor.json v1             vendor.json v2
                 │                         │
              child-first              child-first
              private libs              private libs
```

Đây là một **mô hình khái niệm**, không tự tạo module/loader graph khi chưa triển khai. Các contract classes cần dùng chung identity; private dependencies có thể khác version.

### Phân biệt shared types và private types

Ví dụ API:

```java
package api;

public interface Processor {
    Result process(Request input) throws PluginException;
}
```

Đây là **khai báo minh họa**, cần thêm `Request`, `Result` và `PluginException` để compile. Tất cả các kiểu xuất hiện ở boundary có thể cần cùng runtime identity:

- `api.Processor` là SPI để host cast plugin implementation.
- `api.Request` và `api.Result` xuất hiện trong JVM method descriptor.
- `api.PluginException` có thể được host catch theo runtime type.
- Callback interface, enum, annotation và những type lồng bên trong payload cũng có thể rò ra biên giới.
- Một `vendor.JsonNode` xuất hiện ở public method signature có thể khiến thư viện tưởng như private trở thành **shared contract dependency**.

Nếu plugin định nghĩa bản sao `api.Request`, reflective lookup như `getMethod("process", HostRequest.class)` có thể không tìm đúng method, hoặc JVM có thể báo loading-constraint errors tùy ngữ cảnh.

**Nguyên tắc thiết kế:** parent-first cho những package/types phải chia sẻ; child-first cho những code/dependencies có thể độc lập, với sự kiểm soát chính sách fallback và module access.

## 17. JPMS và Java 9+: module readability không bằng loader priority

Trong Java Platform Module System (JPMS), một named module có:

- **module resolution**: module nào được chọn vào layer;
- **readability** qua `requires` và các quy tắc module;
- **exports/opens**: package nào accessible hoặc mở cho reflection;
- **defining loader**: loader nào định nghĩa class;
- **delegation policy**: thứ tự loader tìm class.

Năm trục này **không phải một thứ**. Một module `app.module` đọc `api.module` không có nghĩa application loader phải là child của API loader, hoặc chọn child-first cho một package sẽ tự bỏ qua được `exports`.

`URLClassLoader` thường nạp JAR theo **classpath-style** từ các URL. Đưa một modular JAR vào URL array **không tự tạo `ModuleLayer` mới** với `requires`/`exports` hợp lệ. Muốn module isolation, phải thiết kế layer và loaders phù hợp, không chỉ đổi `loadClass`.

Trên Java 9+, các built-in loaders (bootstrap/platform/application) có cơ chế module-aware, nên quan hệ thực tế giữa loader và module có thể phức tạp hơn một parent chain. Những ví dụ ở trên chạy trên **unnamed application classpath** để kiểm soát chính sách dễ quan sát.

## 18. Khi nào chọn policy nào?

**Chọn parent-first mặc định nếu:**

- Plugin là extension đáng tin trong một ứng dụng có dependency versions đã được đồng bộ;
- mọi plugin dùng chung host SDK/SPI/DTO và ít cần library versions riêng;
- mục tiêu là dễ bảo trì, giảm nhầm lẫn type identity và tránh tự override `loadClass`;
- runtime không đòi isolation theo package.

**Cân nhắc child-first có ngoại lệ nếu:**

- Nhiều plugin phải dùng **những versions khác nhau** của cùng thư viện;
- host có library trùng binary names, parent-first sẽ chọn sai phiên bản mà plugin đã compile against;
- shared API đã được thiết kế và nạp bởi một loader chung;
- hệ thống sẵn sàng kiểm thử đồng bộ, package sealing, JPMS và resource loading theo loader graph thực tế.

**Không chọn child-first nếu mục tiêu duy nhất là “an toàn hơn”.** Class loader policy không thay thế sandbox; plugin code không tin cậy có thể thực hiện các hành động mà process có quyền. Hãy sử dụng ranh giới tiến trình/container và các kiểm soát quyền truy cập riêng khi threat model đòi hỏi.

### Quy trình ra quyết định

1. Xác định **shared API type identity** mà host và plugin phải cùng dùng.
2. Tập hợp **dependency collisions**: tên class nào tồn tại ở parent và child, version nào đang được compile against?
3. Kiểm tra parent-first có thực sự gây lỗi version hay không bằng ví dụ nhỏ và binary signatures.
4. Nếu cần child-first, viết policy **theo package**, không đổi toàn bộ tên class vô điều kiện.
5. Định nghĩa chính sách resource lookup và service discovery tương ứng.
6. Thử plugin bằng **runtime loader graph thật**, không chỉ compile cả host/plugin chung một classpath.
7. Kiểm tra unloading/lifecycle khi thay plugin, không tái sử dụng loaded class như thể nó sẽ relink với JAR mới.

## 19. Công cụ chẩn đoán trong Java 17+

### Xác định defining loader

```java
static void describe(Class<?> type) {
    System.out.println("name=" + type.getName());
    System.out.println("defined by=" + type.getClassLoader());
    System.out.println("module=" + type.getModule().getName());
}
```

Đây là một **method minh họa**, đặt trong class và gọi với type cần kiểm tra. `getModule().getName()` trả `null` khi class ở unnamed module — đó là tình huống bình thường.

Với class loaders có thể lấy được:

```java
System.out.println(pluginLoader.getParent());
System.out.println(java.util.Arrays.toString(pluginLoader.getURLs()));
```

Snippets cần `pluginLoader` kiểu `URLClassLoader`. Nhớ rằng `getURLs()` chỉ cho biết **URLs riêng của child**, không phải effective set sau delegation.

### Đọc bytecode và descriptor

Với ví dụ A:

```sh
javap -classpath hostlib-out -s lib.Version
javap -classpath pluginlib-out -s lib.Version
javap -classpath app-out -c -v demo.PolicyDemo
```

Những lệnh này đọc class files tĩnh. `javap` có thể xác nhận method descriptors mà compiler sinh, nhưng **không chứng minh runtime chọn parent hay child**. Cần kết hợp với `Class.getClassLoader()` hoặc source logging.

### Class-load log

Trên HotSpot:

```sh
java -Xlog:class+load=info -cp "app-out:hostlib-out" demo.PolicyDemo
```

Lệnh dùng classpath separator **Linux/macOS**; Windows thay `:` bằng `;`. Unified Logging có nhiều dòng, thứ tự và vị trí phụ thuộc JVM/build, nên không có stdout chính xác cố định. Tìm các sự kiện `lib.Version` và nguồn class files, sau đó đối chiếu với thông tin defining loader trong chương trình.

## 20. Checklist debug lỗi thực tế

| Triệu chứng | Câu hỏi cần hỏi | Hướng xử lý |
| --- | --- | --- |
| Plugin thấy library phiên bản cũ | Parent có class cùng binary name không? | Kiểm tra parent-first, chọn dependency strategy rõ |
| `ClassCastException` với class cùng tên | Host/plugin có hai `Class<?>` từ hai defining loaders? | Bảo vệ shared API, inspect loaders |
| `NoSuchMethodError` | Caller compile với descriptor nào, runtime class có method ấy? | So bytecode/JAR versions, policy ưu tiên |
| `NoClassDefFoundError` | Dependency nằm ở URL nào hoặc class initializer lỗi? | Đọc cause và runtime lookup graph |
| `IllegalAccessError` | Package/module/caller có quyền access không? | Kiểm tra JPMS exports, visibility |
| `LinkageError` loader constraint violation | Types trong public signatures có bị định nghĩa kép? | Hợp nhất contract identity |
| `ClassNotFoundException` | Local và parent đều không tìm được? | Kiểm tra URL root, JAR entries, parent |
| `ServiceConfigurationError` | Service interface/provider có cùng identity, discovery loader đúng? | TCCL, ServiceLoader, shared SPI |
| Resource configuration sai | `getResource` vẫn parent-first? | Debug `getResources`/`findResource` |
| Trường hợp chỉ lỗi khi nhiều threads | `loadClass` override có bảo vệ `findLoadedClass` và lock? | Test concurrency, locks, parallel capability |

**Đừng nuốt `Throwable` và fallback.** Lỗi liên kết, bảo mật hoặc VM không phải là một lựa chọn class source bình thường. Giữ stacktrace và root cause khi debug.

## 21. Vòng đời loaders và thay version

`URLClassLoader` triển khai `Closeable`. Nếu code sở hữu loader, có thể:

```java
try (URLClassLoader plugin = new URLClassLoader(urls, parent)) {
    Class<?> implementation =
            plugin.loadClass("impl.PluginGreeter");
}
```

Đây là **snippet**, cần biến `urls`, `parent` và import `URLClassLoader`. Khi kết thúc, `close()` đóng những JAR/file handles mà loader quản lý. Nhưng:

- `close()` **không lập tức unload** mọi class/object plugin;
- object/Class/Method/ThreadLocal/threads hoặc TCCL còn được tham chiếu có thể giữ loader sống;
- class đã được định nghĩa không tự đổi bytecode khi một JAR trên đĩa bị thay;
- sau close, code có thể cố lazy-load dependency/resource và thất bại;
- một phiên bản plugin mới thường cần **loader instance mới**, ngắt callbacks/threads và giải phóng references cũ;
- shared API vẫn cần giữ runtime identity ổn định trong vòng đời plugin versions.

Child-first không tự giải quyết hot reload; nó chỉ là một thành phần của kiến trúc nạp lớp.

## 22. Những ngộ nhận cần tránh

- **“Java mặc định child-first.”** Không. `ClassLoader.loadClass` chuẩn ưu tiên parent.
- **“Đặt URL plugin trước URL host trong một child là đã override parent-first.”** Không: parent vẫn được hỏi trước local URLs.
- **“Chỉ cần chọn parent platform là thành child-first.”** Không: đó là thay **tập nhìn thấy qua parent**, không đổi thứ tự lookup.
- **“Child-first luôn tốt hơn vì plugin dùng thư viện mới.”** Không: có thể tự định nghĩa bản sao shared APIs.
- **“Tên interface giống nhau thì cast được.”** Không: JVM type identity còn có defining loader.
- **“Override `loadClass` chỉ cần gọi `findClass` trước.”** Chưa đủ: cần loaded-cache, synchronization, fallback, module/API constraints.
- **“Bắt mọi `Throwable` rồi thử parent là an toàn.”** Sai: như vậy che lỗi JVM/linkage/permission.
- **“Child-first có nghĩa resources cũng child-first.”** Không: `getResource` có policy riêng.
- **“Plugin có duplicate API trong JAR nên JVM tự chọn đúng một bản.”** Không: phụ thuộc delegation và defining loader.
- **“Parent-first sao chép mọi parent JAR vào child.”** Không: chỉ là runtime lookup/delegation.
- **“Hai URLClassLoader cùng class Java là cùng class namespace.”** Không: **hai instances** có thể tự định nghĩa runtime classes khác nhau.
- **“Chạy được trong IDE có nghĩa plugin JAR chắc chắn chạy trong loader runtime.”** Không: IDE/test classpath có thể che lỗi boundary.
- **“Parent-first/child-first là lựa chọn bảo mật đủ cho plugin không tin cậy.”** Không.
- **“Đóng URLClassLoader thì mọi classes unload tức thì.”** Không; còn phụ thuộc GC và references.

## 23. Câu hỏi tự kiểm tra

**1. Parent-first hỏi ai trước?** Sau kiểm tra loaded class, loader hỏi parent; chỉ khi parent không tìm được mới thử `findClass` của child.

**2. Child-first có sẵn trong constructor `URLClassLoader` không?** Không. `URLClassLoader` mặc định dùng `loadClass` parent-first; child-first phải được triển khai có chủ đích.

**3. Cùng binary name ở parent và child, parent-first thường dùng bản nào?** Bản parent nếu parent tìm được trước.

**4. Cùng tình huống, child-first có thể dùng bản nào?** Bản child nếu local lookup thành công và namespace không được bảo vệ.

**5. Hai definitions cùng tên có là cùng Java type runtime không?** Không nếu defining loaders khác nhau.

**6. Vì sao nên parent-first cho API của host?** Để host và plugin dùng cùng runtime interface/DTO/callback identities, tránh cast/linkage lỗi.

**7. Vì sao `findLoadedClass` quan trọng?** Tránh duplicate definitions và đảm bảo loaded classes được sử dụng nhất quán.

**8. Vì sao chỉ fallback khi `ClassNotFoundException`?** Vì các errors khác có thể phản ánh bytecode không hợp lệ, thiếu quyền hoặc lỗi linking, không phải “không có local class”.

**9. Nếu chỉ override `loadClass`, `getResource` có tự child-first không?** Không. Resource lookup cần policy riêng nếu muốn đổi.

**10. `URLClassLoader.getURLs()` có bao gồm URLs của parent không?** Không; chỉ các URL riêng của loader ấy.

**11. Tại sao `NoSuchMethodError` có thể xuất hiện dù class đã load thành công?** Method resolution trong bytecode đòi exact descriptor, nhưng runtime version được chọn không có method ấy.

**12. Parent-first có thể là lựa chọn hợp lý khi plugin cần các SDK/DTO chung?** Có, đặc biệt khi tất cả plugins dùng một binary API có version quản lý thống nhất.

**13. Child-first có phải luôn chọn riêng mọi Java SE class không?** Không; những nền tảng/namespace bảo vệ cần delegation theo quy tắc runtime.

**14. Chọn strategy dựa trên tiêu chí gì?** Những types **phải chung identity**, những dependencies **phải có version riêng**, module/resource policy và chi phí vận hành.

## 24. Nguồn chuẩn

- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): thuật toán `loadClass`, `findLoadedClass`, `findClass`, `getClassLoadingLock`, parallel capability.
- [Java SE 17 — URLClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): constructor, URLs, lookup và lifecycle.
- [Java SE 17 — Class](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): defining loader và `isAssignableFrom` khi kiểm tra API identity.
- [JVMS Java SE 17, Chapter 5](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): runtime class identity, loading, linking và loading constraints.
- [Java SE 17 — ServiceLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html): provider discovery và tác động của loader/context.

**Tóm tắt cần nhớ:** **Parent-first** ưu tiên reuse class từ parent, tốt cho shared APIs và cấu trúc runtime đơn giản, nhưng có thể khiến dependency host shadow bản plugin. **Child-first** ưu tiên định nghĩa từ child, hữu ích khi plugin cần dependency versions riêng, nhưng phải **cố ý parent-first cho các shared boundary types**, đồng bộ đúng, xử lý lỗi chính xác và kiểm tra resource/JPMS. Câu hỏi không phải “chính sách nào tốt hơn?” mà là **“class nào phải chung runtime identity, và class nào phải được phép tách riêng?”**


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="loading-policies-002">Khi nào việc ghi đè loadClass là cần thiết</a>

<details>
<summary>Xem chi tiết</summary>

# Khi nào việc ghi đè loadClass là cần thiết?

## 1. Nguyên tắc lựa chọn

**Chỉ nên override ClassLoader.loadClass khi cần thay đổi chính sách chọn class loader hoặc thứ tự delegation.** Nếu mục tiêu chỉ là **tìm class bytes từ một nguồn mới**, chẳng hạn thư mục, JAR, mạng, bộ nhớ hoặc bytecode được tạo lúc runtime, hãy ưu tiên **override findClass**. Đây là khuyến nghị ngay trong Javadoc ClassLoader của Java.

Lý do: loadClass mặc định đã có đầy đủ logic **findLoadedClass → parent delegation → findClass → resolveClass nếu được yêu cầu**, kèm locking. Ghi đè loadClass toàn bộ chuyển trách nhiệm bảo đảm cache, synchronization, class identity và error handling cho chính bạn.

Override loadClass là lựa chọn có căn cứ khi:
- Một plugin cần **child-first có chọn lọc**, tức bản implementation riêng phải thắng bản cùng tên của parent.
- Runtime bắt buộc **routing class theo namespace/tenant** thay vì nhờ parent thông thường.
- Có chính sách **không cho delegate một số class name tới parent**; phải kiểm tra chính sách ngay tại entry point.
- Delegation đặc biệt qua nhiều loader ngang hàng cần điều phối thứ tự tùy chỉnh.

Nếu những tình huống trên không tồn tại, bạn thường không cần override. **Không nên đổi thuật toán nạp lớp chỉ vì muốn thay vị trí đọc file class.**

## 2. Ba phương thức có vai trò khác nhau

~~~java
public Class<?> loadClass(String name)
        throws ClassNotFoundException;

protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException;

protected Class<?> findClass(String name)
        throws ClassNotFoundException;
~~~

Đây là chữ ký API để tham khảo, không phải chương trình Java độc lập.

| Thành phần | Vai trò |
| --- | --- |
| **loadClass(String)** | Entry point public, tương đương loadClass(name, false). |
| **loadClass(String, boolean)** | Entry point protected có thuật toán tìm class và tùy chọn yêu cầu linking. |
| **findClass(String)** | Hook để tìm/định nghĩa class khi thuật toán mặc định cần loader hiện tại tự tìm. |
| **findLoadedClass(String)** | Kiểm tra class mà JVM đã ghi nhận loader là initiating loader. |
| **defineClass** | Định nghĩa runtime class từ bytecode và gắn defining loader. |
| **resolveClass(Class)** | Yêu cầu liên kết class; không khởi tạo static fields. |
| **getClassLoadingLock(String)** | Lock dành cho một tên class khi loading, cần khi tự viết loadClass. |

Vì overload hai tham số là **protected**, code ứng dụng thông thường gọi loader.loadClass(name). Nếu subclass ghi đè protected overload thì lời gọi public loadClass(name) kế thừa từ ClassLoader vẫn đi qua đường xử lý ấy.

**Tên name** thường là binary name, ví dụ **demo.Widget**, không phải đường dẫn file **demo/Widget.class**. Nested class thường có binary name với dấu **$**.

## 3. Đường nạp parent-first mặc định

ClassLoader.loadClass(name, resolve) được JDK mô tả theo thứ tự:

1. Đồng bộ trên đối tượng do **getClassLoadingLock(name)** trả về.
2. Gọi **findLoadedClass(name)** để tái sử dụng Class đã load.
3. Nếu chưa có, nhờ **parent.loadClass(name)**; nếu không có parent, dùng bootstrap loader.
4. Nếu parent không tìm được, gọi **findClass(name)** để loader hiện tại tự định nghĩa class.
5. Nếu **resolve=true**, gọi **resolveClass(c)** trên kết quả.
6. Trả **Class<?>**; không tự tạo instance hoặc chạy static initializer.

~~~text
loadClass(name, resolve)
       |
       v
getClassLoadingLock(name)
       |
       v
findLoadedClass(name)
       |
       +-- found ------------------+
       |                           |
       v                           |
parent.loadClass(name)             |
       |                           |
       +-- found ------------------+
       |                           |
       v                           |
findClass(name)                    |
       |                           |
       +---------------------------+
       |
       v
resolve=true? -> resolveClass(c)
       |
       v
return Class<?> c
~~~

**Override findClass chỉ thay một nút của luồng trên.** Bạn thêm cách lấy bytes mà vẫn kế thừa logic dùng lại Class, thứ tự parent-first, khóa loading và resolve.

Đó là lý do Javadoc khuyến nghị subclass override **findClass**, không override **loadClass** một cách mặc định.

### 3.1 Vì sao parent-first thường hữu ích?

- Các class nền tảng được chia sẻ thay vì bị child tự định nghĩa bản riêng.
- Shared interfaces/DTOs của host và plugin có thể có cùng Class identity.
- Giảm các tình huống ClassCastException nhìn bề ngoài khó hiểu.
- Ít code tự quản lý concurrent loading và tránh define trùng.
- Dễ hiểu hơn khi cần kiểm tra security, module access, package identity và loader constraints.

Parent-first không phải quy tắc bắt buộc cho mọi custom loader, nhưng đây là mặc định tốt khi hệ thống không đòi isolation ưu tiên child.

## 4. Những nhu cầu không cần override loadClass

| Nhu cầu | Điểm mở rộng nên cân nhắc |
| --- | --- |
| Nạp từ thư mục class riêng | findClass hoặc URLClassLoader. |
| Đọc bytes từ database, mạng hoặc memory | findClass + defineClass. |
| Giải mã/giải nén bytes trước khi định nghĩa | Tiền xử lý bytes bên trong findClass. |
| Sinh bytecode runtime theo tên class | Cơ chế defineClass/lookup thích hợp, có thể từ findClass. |
| Đọc tài nguyên *.properties hoặc templates | findResource/getResource; không phải loadClass. |
| Muốn class được initialized | Active use hoặc Class.forName(name, true, loader). |
| Muốn nạp class theo binary name | Gọi loadClass public hiện có. |
| Muốn xem loader nào định nghĩa class | Class.getClassLoader() trên kết quả. |
| Muốn thêm logging class loading | Ưu tiên JVM logging/đo tại hook thích hợp, không đổi thuật toán khi không cần. |

Với những trường hợp này, thay loadClass có thể khiến hệ thống phức tạp hơn mà không có ích lợi thực tế.

## 5. Ví dụ Java 17+ số 1: chỉ cần override findClass

Mục tiêu: loader tự tìm một class trong thư mục **plugin-out** khi parent không nhìn thấy class ấy.

Cấu trúc:

~~~text
project/
├── FinderDemo.java
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
        return "hello";
    }
}
~~~

**FinderDemo.java**:

~~~java
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

public class FinderDemo {
    static final class DirectoryLoader extends ClassLoader {
        private final Path root;

        DirectoryLoader(Path root) {
            super(ClassLoader.getPlatformClassLoader());
            this.root = root;
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {

            if (!name.startsWith("plugin.")) {
                throw new ClassNotFoundException(name);
            }

            try {
                byte[] bytes = Files.readAllBytes(
                        root.resolve(name.replace('.', '/') + ".class")
                );
                System.out.println("findClass: " + name);
                return defineClass(name, bytes, 0, bytes.length);

            } catch (IOException error) {
                throw new ClassNotFoundException(name, error);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        DirectoryLoader loader =
                new DirectoryLoader(Path.of("plugin-out"));

        Class<?> type = loader.loadClass("plugin.Greeter");

        System.out.println("defined by child = "
                + (type.getClassLoader() == loader));

        System.out.println("before instance");
        Object obj = type.getConstructor().newInstance();
        System.out.println("message = "
                + type.getMethod("message").invoke(obj));
    }
}
~~~

Từ thư mục **project**:

~~~bash
javac -d plugin-out src/plugin/Greeter.java
javac FinderDemo.java
java FinderDemo
~~~

Stdout đã kiểm tra trên JDK 21 (ví dụ tương thích Java 17+):

~~~text
findClass: plugin.Greeter
defined by child = true
before instance
Greeter initialized
message = hello
~~~

**Giải thích:**

1. **DirectoryLoader** không override loadClass; nó thừa hưởng parent-first.
2. **ClassLoader.getPlatformClassLoader()** làm parent trong ví dụ. Parent này không tìm thấy plugin.Greeter nằm ở plugin-out.
3. Thuật toán mặc định gọi **DirectoryLoader.findClass**, đọc bytes rồi defineClass.
4. **type.getClassLoader() == loader** là true vì DirectoryLoader định nghĩa class.
5. Dòng **Greeter initialized** chỉ xuất hiện khi reflection tạo instance; loadClass không tự khởi tạo class.

Nếu đưa **plugin-out** vào đường tìm của parent thích hợp, parent có thể tìm class trước và hook findClass của child sẽ không chạy. Đó là parent-first đúng theo thiết kế, không phải bug.

**An toàn nguồn class:** ví dụ dùng đường dẫn do code kiểm soát. Custom loader production phải kiểm soát binary name, path traversal/symlink, input bytes, package/protection domain, kích thước và nguồn tin cậy. Không dùng ví dụ này làm loader tải dữ liệu tùy ý từ người dùng.

## 6. Trường hợp thực sự cần override: selective child-first

Giả sử:
- Host chứa **demo.message.Version** với method source() trả **parent**.
- Plugin chứa một class **cùng binary name** nhưng source() trả **plugin**.
- Yêu cầu kiến trúc: implementation thuộc **demo.message.*** phải ưu tiên bản plugin.
- Những package khác, bao gồm Java platform và các API dùng chung, vẫn phải giữ parent-first.

Nếu chỉ override findClass, thuật toán mặc định sẽ để parent tìm Version trước. Parent đã có class cùng tên, nên child.findClass không được gọi. **Đây chính là lúc override loadClass có thể cần thiết** để đổi thứ tự trong một namespace đã chọn:

~~~text
Nếu name thuộc demo.message.*
    findLoadedClass
         |
         v
    thử findClass(child) trước
         |
         +-- không tìm được -> fallback parent
         |
         v
    resolve nếu được yêu cầu

Nếu name thuộc namespace khác
    super.loadClass -> parent-first mặc định
~~~

Vì thay thứ tự tìm class, bạn phải giữ cache, locking, resolve flag và xử lý exception đúng.

## 7. Ví dụ Java 17+ số 2: override loadClass có chọn lọc

Cấu trúc thư mục:

~~~text
project/
├── host-src/
│   ├── SelectivePolicyDemo.java
│   └── demo/message/Version.java
└── plugin-src/
    └── demo/message/Version.java
~~~

**host-src/demo/message/Version.java**:

~~~java
package demo.message;

public class Version {
    public String source() {
        return "parent";
    }
}
~~~

**plugin-src/demo/message/Version.java**:

~~~java
package demo.message;

public class Version {
    public String source() {
        return "plugin";
    }
}
~~~

**host-src/SelectivePolicyDemo.java**:

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class SelectivePolicyDemo {
    static final class PluginFirstLoader extends URLClassLoader {
        PluginFirstLoader(URL url, ClassLoader parent) {
            super(new URL[]{url}, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {

            if (!name.startsWith("demo.message.")) {
                return super.loadClass(name, resolve);
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> found = findLoadedClass(name);

                if (found == null) {
                    try {
                        found = findClass(name);
                    } catch (ClassNotFoundException missing) {
                        found = super.loadClass(name, false);
                    }
                }

                if (resolve) {
                    resolveClass(found);
                }

                return found;
            }
        }
    }

    static String source(Class<?> clazz) throws Exception {
        return (String) clazz.getMethod("source")
                .invoke(clazz.getConstructor().newInstance());
    }

    public static void main(String[] args) throws Exception {
        ClassLoader parent = ClassLoader.getSystemClassLoader();
        URL pluginUrl = Path.of("plugin-out").toUri().toURL();

        try (URLClassLoader defaultLoader =
                     new URLClassLoader(new URL[]{pluginUrl}, parent);
             PluginFirstLoader selective =
                     new PluginFirstLoader(pluginUrl, parent)) {

            Class<?> parentClass = Class.forName(
                    "demo.message.Version", false, parent);

            Class<?> normal =
                    defaultLoader.loadClass("demo.message.Version");

            Class<?> plugin =
                    selective.loadClass("demo.message.Version");

            System.out.println("default = " + source(normal));
            System.out.println("selective = " + source(plugin));
            System.out.println("default same parent = "
                    + (normal == parentClass));
            System.out.println("selective same parent = "
                    + (plugin == parentClass));
            System.out.println("selective defined by child = "
                    + (plugin.getClassLoader() == selective));
        }
    }
}
~~~

Biên dịch **hai Version.java ở hai output roots khác nhau**, tránh trộn class files:

~~~bash
javac -d host-out host-src/demo/message/Version.java host-src/SelectivePolicyDemo.java
javac -d plugin-out plugin-src/demo/message/Version.java
java -cp host-out SelectivePolicyDemo
~~~

Stdout đã chạy kiểm chứng trên JDK 21:

~~~text
default = parent
selective = plugin
default same parent = true
selective same parent = false
selective defined by child = true
~~~

### 7.1 Vì sao bản default lấy từ parent?

URLClassLoader mặc định hỏi parent trước. Host-out nằm trên application classpath và có **demo.message.Version**, nên defaultLoader nhận Class do application loader định nghĩa; method trả **parent**.

### 7.2 Vì sao bản selective lấy từ plugin?

Với name bắt đầu bằng **demo.message.**, PluginFirstLoader gọi **findClass** trực tiếp trước khi hỏi parent. Nó tìm class trong plugin-out và tự định nghĩa kiểu. Kết quả method trả **plugin**.

Với những package khác, phương thức gọi super.loadClass để duy trì chính sách parent-first. Cách này tránh child-first vô tình lan tới toàn bộ các loại dùng chung.

### 7.3 Hai Class khác nhau dù tên giống

**normal** và **plugin** đều có binary name demo.message.Version, nhưng defining loader khác nhau. Vì class identity dựa trên **binary name + defining loader**, toán tử **normal == plugin** sẽ false.

Không tự ép instance plugin thành host Version chỉ vì tên giống nhau. Với host-plugin boundary, interface/DTO dùng chung thường phải đến từ một defining loader chung.

### 7.4 Vì sao code dùng findLoadedClass?

Khi một loader đã initiate class theo tên, các lần load kế tiếp phải nhất quán. **findLoadedClass** tránh cố gắng định nghĩa lặp lại Class cùng name trong cùng loader, điều có thể dẫn tới **LinkageError**.

### 7.5 Vì sao dùng getClassLoadingLock?

Nếu hai threads đồng thời cùng load demo.message.Version, cả hai có thể thử findClass và defineClass nếu không có locking. **synchronized(getClassLoadingLock(name))** giúp bảo đảm kiểm tra cache và quá trình định nghĩa diễn ra an toàn.

Trong ví dụ, subclass không tự đăng ký parallel capable, nên cách khóa hiện tại đủ cho minh họa. Với hệ thống parallel loading phức tạp, cần đánh giá thêm việc đăng ký parallel-capable trước khi tạo instance.

### 7.6 Vì sao giữ resolveClass khi resolve=true?

Overload protected có cờ **resolve**. Nếu true, custom implementation nên gọi **resolveClass(found)** để giữ hợp đồng linking. **Resolve không đồng nghĩa initialize**; static initializer vẫn theo quy tắc active use riêng.

### 7.7 Vì sao chỉ bắt ClassNotFoundException để fallback?

Nếu child không tìm thấy class, fallback qua parent có ý nghĩa. Nhưng nếu child có bytes sai và defineClass ném **ClassFormatError**, **VerifyError** hoặc **LinkageError**, không nên nuốt mọi Throwable và âm thầm dùng bản parent. Làm vậy che lỗi artifact của plugin và khiến chính sách chọn class không đáng tin.

Trong ví dụ, fallback super.loadClass(name,false) có thể thử lại findClass sau khi parent không tìm thấy; đây là hành vi có thể chấp nhận cho code minh họa. Loader production có thể tối ưu, nhưng vẫn phải giữ đúng cache, locking và bootstrap/parent semantics.

## 8. Những trường hợp override khác có thể hợp lý

### 8.1 Multi-tenant hoặc routing theo namespace

Một runtime có nhiều miền class riêng có thể quyết định rằng **tenant.alpha.impl** do loader Alpha định nghĩa, còn **tenant.beta.impl** do loader Beta. Nếu quyết định routing không thực hiện được dưới parent-first mặc định, custom loadClass có thể điều phối namespace.

Tuy nhiên, khi chỉ muốn mỗi loader tìm nguồn bytes của chính nó và parent không có bản cạnh tranh, override findClass vẫn đơn giản hơn.

### 8.2 Chủ động từ chối một package trước delegation

Nếu policy yêu cầu plugin **không được thấy** package nội bộ của host dù parent có class ấy, điểm kiểm tra phải nằm **trước parent delegation**, nên override loadClass có thể được cân nhắc.

Nhưng **ClassLoader không phải security sandbox hoàn chỉnh**. Từ chối một class name không thay thế process isolation, kiểm soát API khả dụng, module access hoặc cơ chế bảo mật khác. Không tuyên bố rằng chặn một package đủ để chạy code không tin cậy an toàn.

### 8.3 Delegation không thuần cây

Hệ module/plugin phức tạp có thể tìm class qua nhiều loader ngang hàng thay vì một parent duy nhất. Khi override loadClass để làm vậy, nguy cơ **deadlock** cao hơn do có thể hình thành chuỗi loader A chờ B, B chờ A.

Cần kiến trúc thứ tự lookup, caching, locking, parallel loading và giới hạn cycle rõ. Trước khi tự viết từ đầu, đánh giá framework/module system có chính sách phù hợp đã được kiểm chứng.

## 9. Concurrency: đừng phá tính nhất quán của class loading

### 9.1 getClassLoadingLock là công cụ nền tảng

ClassLoader.loadClass mặc định đồng bộ toàn bộ quá trình trên **getClassLoadingLock(name)**.

- Loader không parallel capable thường dùng khóa là object ClassLoader.
- Loader parallel capable có thể dùng khóa riêng theo binary name.
- Mục tiêu là tránh hai thread cùng define class một tên và kiểm soát tương tác với delegate loaders.

Một bản override bỏ toàn bộ synchronization dễ tạo race. Không phải mọi race đều lộ ngay; nó có thể chỉ xuất hiện khi server tải lớn.

### 9.2 Parallel-capable class loaders

Java cung cấp:

~~~java
protected static boolean registerAsParallelCapable();
protected Object getClassLoadingLock(String className);
~~~

Đây là chữ ký API. Khi subclass muốn được coi là parallel capable, việc đăng ký cần được thực hiện đúng quy tắc lúc class loader subclass được initialized và phụ thuộc tính parallel-capable của superclass. Không phải cứ superclass ClassLoader được đăng ký thì mọi subclass tự được đăng ký.

Mô hình delegation không phân cấp cần đặc biệt quan tâm parallel capability để tránh deadlock. Nếu loader đơn giản không có yêu cầu throughput lớn, ưu tiên thuật toán mặc định thay vì tự phức tạp hóa locking.

### 9.3 Không nên thay Class đã nạp trong cùng loader

Sau khi loader đã initiate class với một binary name, findLoadedClass có thể trả lại đúng Class đó. Không thể âm thầm thay toàn bộ Class bằng file bytes phiên bản mới chỉ bằng đổi policy sau đó.

Nếu kiến trúc phải hot-reload implementation, thường tạo **ClassLoader mới cho mỗi deployment** và quản lý việc đóng tài nguyên, hủy task, registry và tham chiếu loader cũ. Redefinition qua instrumentation là vấn đề khác, không phải thêm một class definition cùng name trong cùng loader.

## 10. Kiểm tra contract trước khi đổi thứ tự
Nếu đổi sang child-first, hãy xác định **type nào thực sự đi qua boundary host–plugin** trước khi chọn prefix. Ví dụ, hai implementation riêng có thể dùng thư viện private khác phiên bản; nhưng `api.Task` dùng làm interface host phải được cùng một defining loader cung cấp. Kiểm tra cụ thể:

```java
Class<?> pluginType = selective.loadClass("demo.message.Version");
System.out.println(pluginType.getClassLoader() == selective);
```

Đây là snippet đặt trong phạm vi ví dụ 7. Kết quả xác nhận implementation được định nghĩa bởi child; nó **không** chứng minh mọi DTO/annotation/exception contract đều được chia sẻ. Khi thêm namespace child-first, kiểm tra riêng `sharedApiFromChild == sharedApiFromHost` cho những API thực tế và giữ quy tắc package/platform tương ứng.

## 11. Những sai lầm thiết kế hay gặp

### 11.1 Override public loadClass và return findClass vô điều kiện

Đây là ví dụ **sai**, không sử dụng:

~~~java
@Override
public Class<?> loadClass(String name)
        throws ClassNotFoundException {
    return findClass(name);
}
~~~

Nó bỏ qua findLoadedClass, parent delegation, resolve path và locking. Kết quả có thể không nạp được class Java chuẩn hoặc cố define trùng class đã tồn tại.

### 11.2 Child-first cho mọi package

Nếu plugin-first áp dụng quá rộng, loader có thể tự định nghĩa shared APIs hoặc cố xử lý namespace nền tảng. Hậu quả là cast failure, LinkageError hoặc lỗi namespace protection.

**Filter namespace chặt chẽ.** Prefix **demo.message.** khác **demo.messages**; đừng dùng startsWith("demo.message") nếu không muốn match package cùng tiền tố nhưng khác.

### 11.3 Bắt Throwable rồi fallback parent

Một lỗi VerifyError hoặc ClassFormatError cho thấy bytes/định nghĩa không hợp lệ, không phải chỉ “không tìm thấy class”. Nếu nuốt và fallback tùy tiện, ứng dụng có thể lặng lẽ chọn nhầm phiên bản implementation.

### 11.4 Tự define class nhiều lần

Không bỏ qua **findLoadedClass**. Một defineClass thứ hai cho cùng binary name trong cùng loader có thể ném LinkageError. Đồng thời cần đồng bộ hóa.

### 11.5 Dùng loadClass để thay đổi initialization

**loadClass không tự chạy static initializer.** Cờ resolve là yêu cầu linking, không phải initialization. Nếu muốn chủ động initialize theo API phù hợp, cân nhắc **Class.forName(name, true, loader)**, hiểu rằng nó có thể gây side effects.

### 11.6 Cho rằng resource lookup tự đổi theo class lookup

**getResource/getResources** có chính sách resource riêng. Override loadClass để child-first class không tự làm resources child-first. Nếu plugin cần cùng độ ưu tiên cho template/config, cần thiết kế resource lookup phù hợp.

### 11.7 Quên vòng đời của ClassLoader

ClassLoader mới cho mỗi plugin giúp isolation nhưng không tự làm loader cũ collectible. Long-lived thread với context ClassLoader, ThreadLocal values, callback registry, executor queue hoặc static cache có thể tiếp tục giữ loader cũ. Thiết kế unload/cleanup là một phần của plugin policy.

## 12. Chẩn đoán khi custom loader hoạt động sai

### findClass không bao giờ được gọi

Kiểm tra parent có thể tìm class name hay không. Với parent-first, đây là hành vi bình thường. Xem **returnedClass.getClassLoader()** để biết defining loader.

### ClassCastException dù thông báo có hai tên giống nhau

So sánh **a == b** và **a.getClassLoader()**, **b.getClassLoader()**. String name giống không có nghĩa runtime type giống. Tìm shared API có bị mỗi loader tự define không.

### ClassNotFoundException

Kiểm tra binary name, bytecode source, parent chain và fallback. Nested class có ký tự **$** trong binary name. ClassNotFoundException từ findClass là tín hiệu không tìm được class; nên giữ cause khi I/O lỗi.

### NoClassDefFoundError

Có thể xuất hiện khi JVM cần type cho resolution/initialization mà class không thể sử dụng. Xem cause/stack trace và phân biệt với checked ClassNotFoundException của API lookup.

### LinkageError hoặc loader constraint violation

Kiểm tra định nghĩa trùng trong cùng loader, những shared types bị định nghĩa bởi nhiều loader, chữ ký method giữa host/plugin, package restrictions và module access.

### Deadlock khi concurrent loading

Lấy thread dump, tìm locks trên loader A/B và call chains. Đánh giá getClassLoadingLock, parallel capable và delegation cycles. Không bỏ synchronized chỉ để tránh bị chặn; race có thể gây sai class identity.

### Plugin JAR đã thay nhưng class cũ vẫn chạy

Kiểm tra ClassLoader có đang trả Class đã loaded hay không, source URL, parent-first và loader của deployment. Có thể phải tạo loader mới và unload loader cũ thay vì chỉ thay file trên đĩa.

## 13. Công cụ quan sát Java 17+

Trong môi trường JVM hỗ trợ Unified Logging:

~~~bash
java -Xlog:class+load=info -cp host-out SelectivePolicyDemo
java -Xlog:help
~~~

Logging có thể cho biết class được load và nguồn, nhưng format phụ thuộc JVM. Với class-file bytes:

~~~bash
javap -verbose -classpath host-out demo.message.Version
javap -verbose -classpath plugin-out demo.message.Version
~~~

Hai lệnh javap kiểm tra nội dung artifact, không chứng minh loader runtime nào đã define class. Vì thế kết hợp logs với **Class.getClassLoader()** và **Class object identity** trong chương trình.

Nếu dùng JAR:

~~~bash
jar --list --file plugin.jar
~~~

Một entry tồn tại trong JAR chưa có nghĩa runtime chọn nó. Parent delegation vẫn có thể trả class từ nơi khác.

## 14. Checklist trước khi quyết định override

Trước khi viết custom loadClass, hãy tự hỏi:

1. Parent-first có thực sự sai cho yêu cầu không?
2. Chỉ cần thêm nguồn class bytes thì findClass có đủ chưa?
3. Các package nào được child-first? Filter có chính xác và hẹp không?
4. Shared interface/DTO/JDK classes nào bắt buộc dùng từ parent?
5. Đã kiểm tra findLoadedClass và khóa trên getClassLoadingLock chưa?
6. Resolve flag có được bảo toàn không?
7. Chỉ fallback khi ClassNotFoundException hay đã lỡ nuốt LinkageError?
8. Khi parent cũng có class cùng name, kết quả mong đợi được test chưa?
9. Có hỗ trợ concurrent loading và đánh giá deadlock chưa?
10. Có module/package/security constraints không?
11. Resource lookup và class lookup có yêu cầu policy tương thích không?
12. Reload/unload có cleanup reference loader cũ không?
13. Đã có integration test với nhiều ClassLoaders và host API boundary chưa?

Nếu hầu hết câu trả lời đều là “không cần thay”, giữ ClassLoader mặc định và chỉ override findClass.

## 15. Tự kiểm tra

**1.** Muốn nạp class từ thư mục plugin-out mà parent không thấy nó. Override gì?

**Đáp án:** findClass (hoặc dùng loader dựng sẵn); không cần override loadClass.

**2.** Parent và child cùng có p.Version, cần ưu tiên bản child. Override gì có thể cần?

**Đáp án:** loadClass để thay parent-first thành selective child-first.

**3.** Vì sao phải dùng findLoadedClass trước findClass khi custom loadClass?

**Đáp án:** Tái sử dụng Class đã được JVM ghi nhận cho loader/name, tránh define trùng và bảo toàn type identity.

**4.** resolve=true có nghĩa static initializer phải chạy?

**Đáp án:** Không. Nó yêu cầu resolveClass/linking theo hợp đồng; initialization là bước khác.

**5.** Cùng binary name, cùng bytes nhưng khác defining loaders có phải cùng Class?

**Đáp án:** Không. Defining loader là thành phần của runtime class identity.

**6.** Vì sao shared interface api.Task thường nên do parent định nghĩa?

**Đáp án:** Host và plugin cần cùng một Class api.Task để cast và liên kết method signatures tương thích.

**7.** Có nên bắt Throwable từ child.findClass rồi fallback parent?

**Đáp án:** Không. Lỗi format, verification, linkage phải được phơi bày; ClassNotFoundException mới thể hiện child không tìm thấy.

**8.** Hai threads định nghĩa cùng name nếu không khóa có thể bị gì?

**Đáp án:** Race, duplicate definition, LinkageError hoặc trạng thái không nhất quán.

**9.** Override loadClass có tự làm getResource child-first không?

**Đáp án:** Không. Resource lookup có các API và policy riêng.

**10.** Muốn thay triển khai class đã load trong cùng loader bằng bytes mới có được không?

**Đáp án:** Không thể âm thầm redefine như một class mới cùng name; thường cần loader mới cho deployment mới hoặc cơ chế instrumentation riêng.

## 16. Ghi nhớ

~~~text
Chỉ thay nguồn bytecode?
        |
        +-- Có -> ưu tiên findClass
        |
        +-- Không, cần thay thứ tự/chính sách lookup?
                       |
                       +-- Có thể override loadClass
                                |
                                +-- findLoadedClass
                                +-- getClassLoadingLock
                                +-- package filters rõ ràng
                                +-- fallback có kiểm soát
                                +-- resolveClass khi resolve
                                +-- shared API identity
                                +-- concurrency, lỗi và lifecycle
~~~

**findClass** là điểm mở rộng mặc định cho nguồn bytecode; **loadClass** là điểm can thiệp vào chính sách chọn loader. Override loadClass chỉ khi nhu cầu chính sách thực sự đòi hỏi, và luôn bảo toàn cache, concurrency, resolution, type identity và ranh giới host-plugin.

### Tài liệu tham khảo

- ClassLoader Java SE 17: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- ClassLoader Java SE 21: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html
- JVMS §5.3 Creation and Loading: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3
- JVMS §5.3.4 Loading Constraints: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3.4
- JLS §12.2 Loading of Classes and Interfaces: https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.2


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="loading-policies-003">Thiết kế chính sách chọn lọc theo package</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế chính sách chọn lọc theo package trong Java ClassLoader

## 1. Vấn đề cần giải quyết

Trong hệ thống host–plugin, không phải mọi lớp đều nên đi theo cùng một thứ tự tìm kiếm. **Parent-first** thích hợp với các kiểu JDK và hợp đồng API dùng chung. Ngược lại, một plugin đôi khi cần phiên bản class hoặc thư viện riêng để không bị bản trên application classpath của host lấn át.

**Chính sách chọn lọc theo package** (*selective package-based class loading*) nghĩa là phân loại **binary name** của lớp trước khi quyết định thứ tự lookup:

- `java.lang.String` và các class nền tảng: dùng cơ chế parent/Bootstrap/Platform phù hợp.
- `api.Service`: dùng API chung do parent định nghĩa để host và plugin thấy cùng một kiểu.
- `plugin.impl.Version` và `plugin.impl.ServiceImpl`: ưu tiên bytecode trong nguồn child.
- Những package không được chọn: giữ **parent-first delegation** bình thường.

Ví dụ:

~~~text
Application ClassLoader (parent)
├── api.Service                  [API chung]
├── plugin.impl.Version          [phiên bản host]
└── PolicyDemo
          ^
          | delegate với tên chia sẻ
          |
Selective URLClassLoader (child)
├── plugin.impl.Version          [phiên bản plugin]
└── plugin.impl.ServiceImpl      [implementation plugin]
~~~

Mục tiêu là **cô lập implementation**, không phá sự thống nhất của API contract. Một lớp có thể có cùng binary name nhưng do hai loader khác nhau định nghĩa, khiến nó là **hai kiểu runtime khác nhau**.

## 2. Phân biệt parent-first, child-first và child-only

**Parent-first mặc định** của `ClassLoader.loadClass(String, boolean)` hoạt động theo trình tự:

1. `findLoadedClass(name)`: đã được ghi nhận nạp tên này chưa?
2. Hỏi parent loader (hoặc cơ chế bootstrap khi parent là `null`).
3. Khi parent không tìm được, gọi `findClass(name)` của loader hiện tại.
4. Nếu `resolve=true`, gọi `resolveClass(result)` để link nếu cần.
5. Trả `Class<?>`, hoặc ném `ClassNotFoundException`.

Khi chỉ muốn đọc bytes từ nguồn mới mà vẫn parent-first, nên override **`findClass`**, không cần viết lại `loadClass`.

**Child-first với parent fallback** đảo thứ tự bước 2 và 3 **chỉ cho tên trong tập package được chọn**. Khi child không có lớp ấy, loader vẫn hỏi parent. Điều này linh hoạt, nhưng có thể vô tình nhận class phiên bản cũ từ host.

**Child-only/fail closed** với một namespace riêng sẽ ném `ClassNotFoundException` khi child không có tên đó, thay vì hỏi parent. Cách này cô lập chặt hơn nhưng yêu cầu plugin đóng gói đầy đủ mọi class thuộc namespace mà bạn sở hữu.

| Chính sách | Lần tìm đầu tiên | Khi không thấy | Phù hợp |
| --- | --- | --- | --- |
| Parent-first | Parent | Child `findClass` | JDK, API dùng chung, class mặc định |
| Child-first + fallback | Child `findClass` | Parent-first | Plugin implementation cần version riêng nhưng vẫn cho phép fallback |
| Child-only | Child `findClass` | Báo không tìm thấy | Namespace buộc phải riêng, không được lấy bản host |

Không có chính sách đúng cho mọi dự án. Việc chọn policy là quyết định ở **ranh giới hợp đồng và dependency**, không phải chỉ vì tên package trông quen thuộc.

## 3. Chọn package bằng prefix chính xác

Một bộ lọc đơn giản:

~~~java
private static boolean childFirst(String binaryName) {
    return binaryName.startsWith("plugin.impl.");
}
~~~

Hãy giữ **dấu chấm cuối** để phân biệt ranh giới package:

| Binary name | Khớp `plugin.impl.`? |
| --- | --- |
| `plugin.impl.Version` | Có |
| `plugin.impl.ServiceImpl` | Có |
| `plugin.impl.internal.Helper` | Có |
| `plugin.impl.Version$Nested` | Có |
| `plugin.implx.Version` | Không |
| `plugin.implementation.Service` | Không |
| `api.Service` | Không |

Nếu dùng `startsWith("plugin.impl")` (thiếu dấu chấm cuối), bạn có thể vô tình bắt cả `plugin.implx`. Nếu dùng `startsWith("plugin.")` quá rộng, các class contract như `plugin.api.Contract` cũng có thể bị chọn vào child-first và xuất hiện bản sao API.

Với nhiều prefix, nên biểu diễn chính sách thành **danh sách package được duyệt**, có test cho tên gần giống và tên nested. Phân biệt package được host sở hữu, package được plugin sở hữu, package chia sẻ và package nền tảng. Không coi toàn bộ `javax.*` hoặc `jakarta.*` là JDK class trong mọi môi trường: một số API có thể đến từ dependency ngoài JDK.

**Ưu tiên chắc chắn:** các kiểu dùng trong chữ ký phương thức hoặc interface giữa host và plugin cần được định nghĩa nhất quán bởi loader chung. Đừng cho plugin tùy tiện định nghĩa lại những API đó.

## 4. Khi nào cần override loadClass?

Chỉ override `findClass` thì vẫn **parent-first**. Để ưu tiên child cho một số package, ta phải kiểm soát thứ tự lookup, nên có thể override:

~~~java
@Override
protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException {
    synchronized (getClassLoadingLock(name)) {
        Class<?> found = findLoadedClass(name);

        if (found == null && childFirst(name)) {
            try {
                found = findClass(name);
            } catch (ClassNotFoundException missingLocally) {
                // Cho phép fallback sang thuật toán bình thường.
            }
        }

        if (found == null) {
            found = super.loadClass(name, false);
        }
        if (resolve) {
            resolveClass(found);
        }
        return found;
    }
}
~~~

Đây là **một phương thức bên trong subclass**, chưa phải chương trình độc lập. Phần 5 có code chạy hoàn chỉnh dùng `URLClassLoader`.

Giải thích những thành phần không được bỏ:

1. **`getClassLoadingLock(name)`:** giữ khóa thích hợp trong toàn bộ quá trình class loading. Tránh hai luồng cùng cố define một binary name.
2. **`findLoadedClass(name)`:** kiểm tra lớp đã được nạp/ghi nhận bởi loader, tránh define lại cùng tên.
3. **`childFirst(name)`:** áp dụng policy chỉ cho namespace chọn lọc.
4. **`findClass(name)`:** tìm bytecode ở nguồn child. Với subclass của `URLClassLoader`, phương thức này đã biết đọc những URL directory/JAR được cung cấp.
5. **Bắt riêng `ClassNotFoundException`:** cho phép fallback khi file class không có trong child. Không nuốt `ClassFormatError`, `LinkageError`, `SecurityException` như thể chúng chỉ là thiếu file.
6. **`super.loadClass(name, false)`:** thực hiện parent-first mặc định ở những trường hợp chưa có class. Gọi `false` vì chính override sẽ xử lý `resolveClass` ở cuối.
7. **`resolveClass(found)` nếu cờ `resolve=true`:** bảo toàn hợp đồng của overload hai tham số. Cờ này **không yêu cầu initialization**.

Đoạn code giữ khóa rồi gọi lại `super.loadClass`: khóa monitor Java là *reentrant* nên cùng luồng có thể vào lại khóa. Tuy nhiên, với các loader có đồ thị ủy quyền phức tạp (sibling hoặc circular delegation), vẫn phải kiểm tra deadlock và quy tắc *parallel capable*.

**Quan trọng:** code trên là **child-first với fallback**, không phải child-only. Nếu namespace `plugin.impl.*` phải tuyệt đối không dùng class host, đừng bỏ qua failure từ `findClass`: hãy chuyển tiếp `ClassNotFoundException` cho tên thuộc namespace ấy.

## 5. Ví dụ đầy đủ Java 17+: cùng binary name, hai phiên bản

Mục tiêu thử nghiệm: parent có `plugin.impl.Version` trả `"parent"`, child có `plugin.impl.Version` trả `"child"`. Plugin class `ServiceImpl` triển khai interface chung `api.Service` do parent định nghĩa.

### Cấu trúc nguồn

~~~text
example/
└── src/
    ├── parent/
    │   ├── api/
    │   │   └── Service.java
    │   ├── plugin/
    │   │   └── impl/
    │   │       └── Version.java
    │   └── PolicyDemo.java
    └── child/
        └── plugin/
            └── impl/
                ├── Version.java
                └── ServiceImpl.java
~~~

Hai tệp `Version.java` cùng tên và package nhưng thuộc hai vùng biên dịch/output khác nhau. **Không** biên dịch chúng vào cùng một thư mục.

### File `src/parent/api/Service.java`

~~~java
package api;

public interface Service {
    String execute();
}
~~~

### File `src/parent/plugin/impl/Version.java`

~~~java
package plugin.impl;

public class Version {
    public static String who() {
        return "parent";
    }
}
~~~

### File `src/child/plugin/impl/Version.java`

~~~java
package plugin.impl;

public class Version {
    public static String who() {
        return "child";
    }
}
~~~

### File `src/child/plugin/impl/ServiceImpl.java`

~~~java
package plugin.impl;

import api.Service;

public class ServiceImpl implements Service {
    @Override
    public String execute() {
        return Version.who();
    }
}
~~~

### File `src/parent/PolicyDemo.java`

~~~java
import api.Service;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import plugin.impl.Version;

public class PolicyDemo {
    static final class PackagePolicyLoader extends URLClassLoader {
        PackagePolicyLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        private static boolean childFirst(String name) {
            return name.startsWith("plugin.impl.");
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> found = findLoadedClass(name);

                if (found == null && childFirst(name)) {
                    try {
                        found = findClass(name);
                    } catch (ClassNotFoundException missingLocally) {
                        // Explicit fallback to normal parent-first lookup.
                    }
                }

                if (found == null) {
                    found = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(found);
                }
                return found;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        URL plugin = Path.of(args[0]).toAbsolutePath().toUri().toURL();

        try (PackagePolicyLoader loader = new PackagePolicyLoader(
                new URL[]{plugin}, PolicyDemo.class.getClassLoader())) {
            System.out.println("parent version: " + Version.who());

            Class<?> childVersion = loader.loadClass("plugin.impl.Version");
            System.out.println("child version: "
                    + childVersion.getMethod("who").invoke(null));
            System.out.println("same runtime class: "
                    + (Version.class == childVersion));
            System.out.println("child owns version: "
                    + (childVersion.getClassLoader() == loader));

            Class<?> implementation =
                    loader.loadClass("plugin.impl.ServiceImpl");
            Service service = (Service) implementation
                    .getDeclaredConstructor().newInstance();

            System.out.println("service: " + service.execute());
            System.out.println("api shared: "
                    + (loader.loadClass("api.Service") == Service.class));
            System.out.println("jdk shared: "
                    + (loader.loadClass("java.lang.String") == String.class));
        }
    }
}
~~~

Mã host dùng `URLClassLoader` để không phải tự cài đặt đọc bytecode/đọc JAR. Override `loadClass` chỉ thay chính sách chọn package và giữ phần còn lại của API chuẩn.

### Biên dịch và chạy Linux/macOS

Đứng trong `example`:

~~~bash
mkdir -p out/parent out/child

javac --release 17 -d out/parent \
  src/parent/api/Service.java \
  src/parent/plugin/impl/Version.java \
  src/parent/PolicyDemo.java

javac --release 17 -cp out/parent -d out/child \
  src/child/plugin/impl/Version.java \
  src/child/plugin/impl/ServiceImpl.java

java -cp out/parent PolicyDemo out/child
~~~

**Chú ý:** khi chạy, chỉ đưa `out/parent` vào application `-cp`. `out/child` được truyền như tham số của chương trình, để `PolicyDemo` tạo URLClassLoader. Nếu thêm thư mục child vào classpath host, bản class host/child có thể xung đột theo thứ tự classpath và phá điều kiện thí nghiệm.

### Biên dịch và chạy Windows PowerShell

Trong thư mục `example`:

~~~powershell
New-Item -ItemType Directory -Force out\parent, out\child | Out-Null
javac --release 17 -d out\parent src\parent\api\Service.java src\parent\plugin\impl\Version.java src\parent\PolicyDemo.java
javac --release 17 -cp out\parent -d out\child src\child\plugin\impl\Version.java src\child\plugin\impl\ServiceImpl.java
java -cp out\parent PolicyDemo out\child
~~~

### Stdout đã kiểm chứng trên JDK

~~~text
parent version: parent
child version: child
same runtime class: false
child owns version: true
service: child
api shared: true
jdk shared: true
~~~

Giải thích theo từng dòng:

1. **parent version: parent:** `PolicyDemo` do application loader định nghĩa, nên truy cập `Version.who()` trực tiếp trong host lấy bản Version của parent.
2. **child version: child:** child loader ưu tiên `findClass` với package `plugin.impl.*` nên nhận class Version từ `out/child`.
3. **same runtime class: false:** cặp binary name + defining loader khác nhau tạo hai class runtime khác nhau.
4. **child owns version: true:** kiểm tra định nghĩa class bằng `getClassLoader() == loader`.
5. **service: child:** `ServiceImpl` do child định nghĩa resolve `Version` qua loader của mình, do đó dùng phiên bản child.
6. **api shared: true:** `api.Service` không nằm trong namespace child-first nên được parent cung cấp, cho phép cast triển khai child sang interface host.
7. **jdk shared: true:** `java.lang.String` được loader nền tảng/JVM cung cấp qua delegation, không bị child tái định nghĩa.

Mã được kiểm thử bằng `javac --release 17` và `java` với đúng hai classpath/output riêng; kết quả khớp hoàn toàn.

## 6. Tại sao package API chung cần parent-first?

Giả sử child cũng đóng gói riêng `api.Service` rồi chọn child-first cho `api.*`. Mặc dù class file mang tên giống nhau, Java có thể tạo:

~~~text
api.Service @ ParentLoader   !=   api.Service @ ChildLoader
~~~

Hai class đó không cùng danh tính JVM. Một object `plugin.impl.ServiceImpl` triển khai bản API child có thể **không ép kiểu** sang `api.Service` phía host, gây `ClassCastException`. Khi method signatures hoặc field types bắt buộc đồng nhất kiểu qua loader, JVM cũng có thể áp dụng *loading constraints* và phát sinh `LinkageError`.

Vì vậy, hãy đặt các interface và types xuất hiện ở **ranh giới giao tiếp** (tham số, return type, exception contract, DTO chia sẻ) vào namespace parent/shared loader. Chỉ cô lập implementation/private dependencies. Nếu một thư viện phiên bản riêng rò rỉ kiểu qua API công khai của plugin, policy theo package có thể không đủ; cân nhắc adapter/serialization hoặc một contract riêng.

## 7. Fallback có thể che giấu xung đột phiên bản

Giả sử child thiếu `plugin.impl.Helper` nhưng parent có bản cũ. Code child-first + fallback sẽ hỏi parent và nhận class cũ, có thể gây `NoSuchMethodError` khi plugin đã compile dựa trên bản mới.

Một số cách lựa chọn:

- **Cho fallback** khi tên đó thật sự có thể chia sẻ. Cần test tên có ở parent mà child không có.
- **Fail closed** đối với namespace riêng tuyệt đối: nếu child `findClass` không thấy thì ném `ClassNotFoundException`, không dùng parent.
- **Tách namespace** bằng package khác hoặc shading/relocation cho dependency tư nhân để giảm nguy cơ trùng.
- **Thiết kế API bridge** ở parent cho các lớp phải truyền qua ranh giới.

Đừng triển khai catch tổng quát kiểu `catch (Throwable ignored)` rồi fallback: nó che giấu lỗi bytecode hỏng, lỗi security, linkage và có thể khiến ứng dụng âm thầm chạy với phiên bản khác.

## 8. Đồng bộ, caching, linking và deadlock

Một override `loadClass` có trách nhiệm lớn hơn override `findClass` đơn thuần:

| Thành phần | Vì sao quan trọng? |
| --- | --- |
| `getClassLoadingLock(name)` | Khóa nạp theo tên đúng với loader; nhiều luồng không cùng define class một cách bất hợp lệ. |
| `findLoadedClass(name)` | Không lặp `defineClass` cho tên đã ghi nhận. |
| `super.loadClass(name, false)` | Cho parent-first hoạt động khi namespace không child-first hoặc khi fallback. |
| `resolveClass(found)` | Giữ hợp đồng khi `resolve=true`. |
| Phân biệt exception | `ClassNotFoundException` khác `LinkageError` và lỗi khởi tạo. |

`loadClass(name)` tương đương `loadClass(name,false)`; `resolveClass` thực hiện linking nếu cần, **không** gọi static initializer. Java có khái niệm *parallel capable class loader* và đăng ký thích hợp để hỗ trợ khóa khác nhau theo tên. Đừng mặc định mọi subclass có chính sách phức tạp đều parallel capable; phải kiểm tra đặc tả và cấu hình inheritance/registration.

Với đồ thị delegation không phân cấp hoặc có lời gọi giữa nhiều loader, deadlock có thể xuất hiện nếu các luồng giữ khóa theo thứ tự khác nhau. Đoạn ví dụ chỉ có một parent và một child, không tạo vòng delegation.

Việc đóng `URLClassLoader` bằng *try-with-resources* giải phóng tài nguyên I/O; **không** ngay lập tức unload class đã định nghĩa hay xóa mọi object đang dùng. Đừng dùng `close()` như phép thay class cùng tên trong một loader đang tồn tại.

## 9. Resource policy không tự đi theo class policy

Override `loadClass` chỉ thay đổi thứ tự **tìm class**. Tài nguyên được tìm qua API khác:

~~~java
URL url = loader.getResource("plugin/impl/config.properties");
InputStream stream =
        loader.getResourceAsStream("plugin/impl/config.properties");
~~~

Đây là mã minh họa, giả định loader có sẵn. `URLClassLoader.getResource` bình thường có chính sách parent-first riêng, nên có thể có tình huống class từ plugin child nhưng resource cùng tên lại từ parent. Nếu resource chứa cấu hình tương ứng chính xác với class phiên bản đó, việc chọn khác nguồn gây bug khó phát hiện.

Có thể phải định nghĩa chính sách riêng cho `getResource`/`getResources`/`findResource`, hoặc đổi tên/path resource để tránh trùng. `META-INF/services/...` và `ServiceLoader` cũng phải được kiểm tra: một số cách dùng `ServiceLoader` lấy loader từ thread context class loader, khác với cơ chế JVM resolve bytecode thông thường.

**Tương tự**, package policy không tự tạo JPMS ModuleLayer hoặc xử lý exports/opens của named modules. `URLClassLoader` là công cụ tìm lớp/resource trên URL classpath, không thay thế module resolver.

## 10. Rủi ro thực tế và cách xử lý

| Triệu chứng | Nguyên nhân khả dĩ | Hướng kiểm tra |
| --- | --- | --- |
| Child vẫn dùng phiên bản host | Policy không match tên, hoặc parent được hỏi trước khi `findClass`. | Kiểm tra `childFirst(name)` và `clazz.getClassLoader()`. |
| `ClassCastException` dù tên interface giống nhau | Host/child định nghĩa trùng API contract. | So sánh `Service.class` và class interface thực tế phía plugin. |
| `NoSuchMethodError` | Fallback sang dependency sai version. | Kiểm tra class nguồn và dependency graph. |
| `LinkageError: duplicate class definition` | Bỏ cache/khóa; định nghĩa lại cùng binary name. | Dùng `findLoadedClass` và khóa chuẩn. |
| `ClassFormatError` hoặc `VerifyError` | Bytecode nguồn hỏng hoặc không tương thích. | Đọc cause; đừng nuốt thành `ClassNotFoundException`. |
| Deadlock khi nạp nhiều loader | Có vòng ủy quyền hoặc khóa chồng chéo. | Rà lại delegation graph, locking và parallel capability. |
| Lỗi loader constraint violation | Hai loader có signature dùng cùng binary name nhưng class runtime khác. | Đưa types giao tiếp lên shared loader. |
| Config sai version | Class child-first, resource vẫn parent-first. | Kiểm tra policy resource tương ứng. |
| Package gần giống bị bắt nhầm | Prefix thiếu dấu `.` ở cuối. | Test `plugin.implx.*` và subpackage. |
| Plugin chạy được nhưng không cô lập bảo mật | Class loader không phải sandbox. | Xây chính sách bảo mật/cách ly tiến trình riêng. |

Với những plugin không tin cậy, đừng xem class loader là biện pháp bảo mật cho filesystem, network, CPU hay quyền hệ điều hành. Đây chủ yếu là cơ chế namespace, delegation và lifecycle class.

## 11. Checklist và bài tự kiểm tra

Trước khi đưa policy vào production:

1. Xác định rõ **API chung**, **implementation riêng**, **dependency tư nhân**, **lớp nền tảng**.
2. Dùng binary name và kiểm tra ranh giới prefix package: `"plugin.impl."`, không phải `"plugin.impl"`.
3. Chỉ override `loadClass` khi thật sự cần đổi **thứ tự delegation**; nếu giữ parent-first, ưu tiên override `findClass`.
4. Trong override, giữ khóa `getClassLoadingLock`, cache `findLoadedClass`, fallback đúng quyết định và `resolveClass`.
5. Xem xét rõ **child-first + fallback** hay **child-only** cho từng namespace; test tên thiếu trong child nhưng có ở parent.
6. Kiểm tra cùng binary name ở hai loader: object `Class<?>` phải khác khi mong cô lập.
7. Kiểm tra shared API: `loader.loadClass("api.Service") == Service.class` và ép kiểu qua contract thành công.
8. Kiểm tra các thư viện phiên bản khác nhau, các method signatures, exceptions, DTO và resource names.
9. Kiểm tra nạp lặp và nạp đa luồng, đặc biệt với custom graph hoặc nhiều loaders.
10. Kiểm tra resource/provider, vòng đời URLClassLoader, và giới hạn bảo mật của plugin.

**Câu hỏi 1.** Vì sao prefix `plugin.impl.` tốt hơn `plugin.impl`?

**Đáp:** Nó match đúng ranh giới package, tránh tên gần giống như `plugin.implx.Version`.

**Câu hỏi 2.** Child có bản `plugin.impl.Version` và parent cũng có bản cùng tên. Child-first sẽ lấy bản nào?

**Đáp:** Bản child nếu `findClass` của child thành công, dù parent cũng có. Nếu child không có, kết quả phụ thuộc policy fallback hay fail closed.

**Câu hỏi 3.** Vì sao `api.Service` ở parent và phải giữ cùng identity?

**Đáp:** Host và plugin phải chia sẻ một contract runtime; hai interface cùng binary name nhưng khác defining loader có thể không cast/linkage được.

**Câu hỏi 4.** Đã override `loadClass` child-first thì `getResource` cũng tự child-first phải không?

**Đáp:** Không. Resource lookup có API và chính sách độc lập; cần xử lý hoặc kiểm thử riêng.

**Câu hỏi 5.** Cờ `resolve=true` làm chạy `static { ... }`?

**Đáp:** Không. Nó yêu cầu linking khi cần; initialization được kích hoạt bởi active use hoặc API có hợp đồng initialize riêng.

## Tài liệu chính thức

- [Java SE 21 – ClassLoader.loadClass và getClassLoadingLock](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html).
- [Java SE 21 – URLClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URLClassLoader.html).
- [JVMS Java SE 21 §5.3 – Loading](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3).
- [JVMS Java SE 21 §5.4 – Linking](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.4).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="loading-policies-004">Ủy quyền bắt buộc cho kiểu nền tảng và API dùng chung</a>

<details>
<summary>Xem chi tiết</summary>

# Ủy quyền bắt buộc cho kiểu nền tảng và API dùng chung

Khi xây dựng **class loader tùy chỉnh** cho plugin, application server hay môi trường chạy nhiều phiên bản thư viện, không thể chỉ quyết định “mọi lớp đều child-first để plugin tự chủ”. Có những **ranh giới phải được tôn trọng**: các kiểu nền tảng do Java runtime cung cấp không được tùy tiện định nghĩa lại; các **kiểu API dùng chung giữa host và plugin** phải có **cùng định nghĩa JVM** nếu muốn truyền object, ép kiểu, gọi interface hoặc trao đổi callback an toàn.

Trong bài này, “ủy quyền bắt buộc” có **hai tầng ý nghĩa cần phân biệt**:

1. **Ràng buộc được runtime/API thực thi:** ví dụ các subclass ClassLoader thông thường **không được định nghĩa class trong package bắt đầu bằng `java.`** qua `defineClass`; vi phạm có thể ném `SecurityException`. Không thể dùng child-first như cách hợp pháp để thay thế `java.lang.String` bằng bản do plugin tự tạo.
2. **Bất biến kiến trúc cần thực thi trong ứng dụng:** một interface hoặc DTO dùng chung như `com.acme.api.Greeter` cần được host và plugin tham chiếu tới **cùng đối tượng `Class<?>`** từ một defining loader chung. Java không tự áp đặt “mọi package `com.acme.api.*` phải parent-first”; **nhà thiết kế loader phải thực hiện chính sách ấy**.

Chiến lược thực tế thường là **parent-first cho lớp nền tảng và API contract dùng chung; child-first có chọn lọc cho implementation/dependency riêng cần cô lập**. Điều này bảo vệ type identity, nhưng phải thực hiện cẩn thận để không vô tình cho plugin ghi đè API hoặc để host nạp bản implementation sai.

## 1. Defining loader khác initiating loader
Một plugin có thể gọi `loadClass("com.acme.api.Greeter")` nhưng nhận chính `Class<?>` do parent định nghĩa. Loader gọi API là **initiating loader**, còn `Greeter.class.getClassLoader()` cho biết **defining loader**. Nếu child tự định nghĩa bản `Greeter` thứ hai, JVM nhận diện đó là kiểu khác, dù binary name và bytes giống nhau.

```text
Host loader defines com.acme.api.Greeter  ──┐
                                           ├── Host và plugin dùng chung Class<?> nếu delegate
Plugin loader delegates shared API ────────┘

Plugin loader defines duplicate Greeter  ───── Kiểu khác với bản host
```

Hãy xác nhận `apiFromPlugin == apiFromHost` thay vì chỉ so chuỗi `getName()`. Điều này phân biệt **ràng buộc kiến trúc** cần shared identity với quy tắc thực thi của JVM về namespace được bảo vệ như `java.*` ở phần tiếp theo.

## 2. Tại sao phải ưu tiên loader nền tảng?

Java có các built-in class loader, thường được mô tả theo vai trò:

| Loader | Vai trò chung |
| --- | --- |
| **Bootstrap** | Cơ chế nạp các lớp nền tảng cốt lõi, thường thấy `String.class.getClassLoader() == null` trên API. |
| **Platform** | Nạp những class/module nền tảng được gán cho platform loader của runtime. |
| **Application/system** | Nạp nhiều class ứng dụng, classpath và module ứng dụng tùy cấu hình. |
| **Custom/plugin** | Có thể nạp implementation và thư viện cô lập, thường có parent là loader host/shared API. |

Từ Java 9, lớp nền tảng nằm trong **runtime image theo module**, không phải một `rt.jar` cũ. Phân bổ class/module cho bootstrap/platform/application **không có nghĩa mọi class có tiền tố `javax.`, `jdk.` hoặc mọi module đều được bootstrap nạp**. Vì thế, không nên xây danh sách “mọi `javax.*` là lõi Java” mà không xét **module, ownership, runtime và dependency thực tế**: một số API `javax.*` do JDK cung cấp, nhưng tên `javax.*` khác có thể đến từ thư viện bên ngoài.

### Điều gì thực sự bị cấm ở mức API?

Một class loader tùy chỉnh không được gọi `defineClass` theo cách định nghĩa các class có binary name trong **`java.*`**. API `ClassLoader#defineClass` quy định `SecurityException` cho trường hợp cố định nghĩa class thuộc package bắt đầu bằng `java.` từ loader không được phép. Đây là ràng buộc của Java runtime, **khác** với một convention parent-first do đội phát triển tự tạo.

Ngược lại, Java **không có quy tắc phổ quát** rằng mọi tên `com.company.api.*` hay mọi tên `javax.*` bắt buộc phải được parent định nghĩa. Nhà thiết kế loader phải xác định đúng các API contract cần chia sẻ và bắt buộc delegation cho chúng.

**Không coi việc cấm định nghĩa `java.*` là cơ chế sandbox đầy đủ.** Class loader không tự cung cấp một ranh giới bảo mật toàn diện cho code không tin cậy; còn phải xét module access, quyền và cơ chế isolation của hệ thống.

## 3. API dùng chung: quan trọng hơn việc class “load được”

Ví dụ host và plugin giao tiếp qua:

~~~java
package com.acme.api;
public interface Greeter {
    String greet();
}
~~~

Đoạn này là **fragment hợp đồng**, có thể biên dịch nếu lưu trong tệp `Greeter.java` thuộc package đúng.

Nếu mỗi plugin chứa bản copy của `Greeter.class` và dùng child-first cho cả `com.acme.api.*`:

~~~text
HostLoader
└── com.acme.api.Greeter (Greeter-H)
                 |
                 | host mong object implements Greeter-H
                 v
PluginLoader
├── com.acme.api.Greeter (Greeter-P)       <-- định nghĩa lại
└── com.acme.impl.GreeterImpl implements Greeter-P

Greeter-H != Greeter-P
hostGreeterClass.isInstance(pluginObject) == false
~~~

Hệ quả có thể gồm:

- Host ép kiểu `GreeterImpl` sang `Greeter-H` và nhận `ClassCastException`.
- `ServiceLoader` tìm thấy provider nhưng kết luận provider không phải subtype của service type, gây `ServiceConfigurationError`.
- Một số chữ ký phương thức giữa các class do loader khác nhau định nghĩa bị vi phạm loader constraint, dẫn tới `LinkageError` ở giai đoạn linking.
- Generic erasure/reflective invocation có thể che sai sót đến khi cast hoặc phương thức được gọi.

**Chính sách đúng:** `com.acme.api.*` phải được tải qua **một loader chung ổn định** mà cả host và plugin thực sự nhìn thấy. Các lớp implementation `com.acme.impl.*` hoặc `plugin.impl.*` có thể được plugin loader nạp theo child-first khi thật sự cần cô lập.

### “API chung” gồm những loại kiểu nào?

Không chỉ interface chính. Hãy xét tất cả class mà các phía **truyền trực tiếp qua boundary**:

- API interface và abstract base class.
- DTO, request/response, enum, exception type trong method signatures.
- Callback, event/listener interface, service/provider contract.
- Annotation hoặc marker type mà framework/host đọc theo `Class<?>` identity.
- Kiểu trong parameter và return type của method, bao gồm các tham chiếu còn được runtime kiểm tra.

Một dependency “chỉ là internal implementation” có thể vẫn cần riêng nếu plugin phụ thuộc phiên bản khác; ngược lại, một DTO vô tình bundle trong plugin mà host cũng dùng có thể phá contract. Vì vậy, hãy xác định **ranh giới chia sẻ theo kiến trúc**, không chỉ dựa vào tên package có chữ `api`.

## 4. Parent-first cho API, child-first cho implementation: chính sách chọn lọc

Một chính sách thường dùng:

| Namespace / loại | Hướng tìm đề xuất | Lý do |
| --- | --- | --- |
| `java.*` và class/module nền tảng của runtime | Ưu tiên runtime/parent; không tự định nghĩa lại class cốt lõi | Tránh vi phạm bảo vệ namespace và phá runtime assumptions. |
| `com.acme.api.*` do host sở hữu | **Parent-first bắt buộc theo thiết kế** | Host/plugin phải nhìn cùng một định nghĩa `Class<?>`. |
| `plugin.impl.*` do plugin sở hữu | **Child-first có chọn lọc** | Host có thể có bản khác; cần dùng implementation của plugin hiện tại. |
| Thư viện implementation riêng của plugin | Child-first khi có nhu cầu isolation rõ ràng | Cho phép dependency version khác mà không làm mất hợp đồng chung. |
| Các package không được plugin cho phép override | Parent-first hoặc chính sách rõ theo ownership | Tránh nạp nhầm class ngoài phạm vi plugin. |

Không nên triển khai đơn giản theo kiểu “mọi thứ không phải `java.*` đều child-first” vì sẽ nhân bản API chia sẻ. Ngược lại, “mọi thứ parent-first” có thể khiến host version của một implementation được dùng thay vì bản child mà plugin cần.

Đặc tả JVM không áp đặt child-first/parent-first duy nhất cho mọi custom loader. **Bất biến cần duy trì là những kiểu phải giao tiếp chung được định nghĩa chung, và những lớp private được cô lập đúng chủ đích.**

## 5. Thực hành Java 17: loader an toàn và loader tạo bản sao API

Ví dụ độc lập này **tự biên dịch** một API interface cho host và một bản sao interface cùng implementation cho plugin, sau đó thử hai chính sách:

- **Safe loader:** child-first riêng cho `plugin.impl.*`, parent-first cho `api.shared.*` và các tên còn lại.
- **Unsafe loader:** child-first cho `plugin.impl.*` **và** `api.shared.*`, tạo định nghĩa trùng của interface contract.

Nhờ biên dịch nguồn giống nhau, phép thử chứng minh rằng **bytecode giống nhau không thay thế việc chia sẻ defining loader**.

Tạo một tệp **`DelegationBoundaryDemo.java`**:

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public class DelegationBoundaryDemo {
    private static final String API = "api.shared.Greeter";
    private static final String IMPL = "plugin.impl.GreeterImpl";

    static final class PluginLoader extends URLClassLoader {
        private final boolean shareApi;

        PluginLoader(URL[] urls, ClassLoader parent, boolean shareApi) {
            super(urls, parent);
            this.shareApi = shareApi;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {

            boolean pluginImplementation = name.startsWith("plugin.impl.");
            boolean privateApiCopy =
                    !shareApi && name.startsWith("api.shared.");

            if (!pluginImplementation && !privateApiCopy) {
                // JVM/platform + host API: parent-first.
                return super.loadClass(name, resolve);
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);

                if (type == null) {
                    try {
                        type = findClass(name);
                    } catch (ClassNotFoundException absent) {
                        type = super.loadClass(name, false);
                    }
                }

                if (resolve) {
                    resolveClass(type);
                }
                return type;
            }
        }
    }

    private static void write(Path file, String content)
            throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static void compile(JavaCompiler compiler, Path output,
                                Path... sources) {
        String[] args = new String[5 + sources.length];
        args[0] = "--release";
        args[1] = "17";
        args[2] = "-d";
        args[3] = output.toString();
        args[4] = "-g:none";

        for (int i = 0; i < sources.length; i++) {
            args[5 + i] = sources[i].toString();
        }

        int status = compiler.run(null, null, null, args);
        if (status != 0) {
            throw new IllegalStateException("Compiler failed: " + status);
        }
    }

    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("A full JDK is required");
        }

        Path root = Files.createTempDirectory("delegation-boundary-");
        Path hostDir = Files.createDirectories(root.resolve("host"));
        Path childDir = Files.createDirectories(root.resolve("child"));

        Path hostApiSource =
                root.resolve("host-src/api/shared/Greeter.java");
        Path childApiSource =
                root.resolve("child-src/api/shared/Greeter.java");
        Path childImplSource =
                root.resolve("child-src/plugin/impl/GreeterImpl.java");

        String apiSource =
                "package api.shared; "
                + "public interface Greeter { String greet(); }";

        write(hostApiSource, apiSource);
        write(childApiSource, apiSource);
        write(childImplSource,
                "package plugin.impl; "
                + "public final class GreeterImpl "
                + "implements api.shared.Greeter { "
                + "public GreeterImpl() {} "
                + "public String greet() { return \"hello-plugin\"; } "
                + "}");

        compile(compiler, hostDir, hostApiSource);
        compile(compiler, childDir, childApiSource, childImplSource);

        try (URLClassLoader host = new URLClassLoader(
                    new URL[] { hostDir.toUri().toURL() }, null);
             PluginLoader safe = new PluginLoader(
                    new URL[] { childDir.toUri().toURL() }, host, true);
             PluginLoader unsafe = new PluginLoader(
                    new URL[] { childDir.toUri().toURL() }, host, false)) {

            Class<?> hostApi = host.loadClass(API);

            Object good =
                    safe.loadClass(IMPL).getDeclaredConstructor().newInstance();

            System.out.println("Safe API shared: "
                    + (safe.loadClass(API) == hostApi));
            System.out.println("Safe implementation local: "
                    + (good.getClass().getClassLoader() == safe));
            System.out.println("Safe implements host API: "
                    + hostApi.isInstance(good));
            System.out.println("Safe greeting: "
                    + hostApi.getMethod("greet").invoke(good));

            Object bad = unsafe.loadClass(IMPL)
                    .getDeclaredConstructor().newInstance();

            System.out.println("Unsafe API duplicated: "
                    + (unsafe.loadClass(API) != hostApi));
            System.out.println("Unsafe implements host API: "
                    + hostApi.isInstance(bad));

            try {
                hostApi.cast(bad);
                System.out.println("Unexpected cast success");
            } catch (ClassCastException e) {
                System.out.println("Unsafe cast: ClassCastException");
            }

            System.out.println("Platform String delegated: "
                    + (safe.loadClass("java.lang.String") == String.class
                       && unsafe.loadClass("java.lang.String") == String.class));
        }
    }
}
~~~

Biên dịch/chạy từ thư mục chứa tệp bằng **JDK 17+**:

~~~bash
javac --release 17 DelegationBoundaryDemo.java
java DelegationBoundaryDemo
~~~

**Stdout chính xác** với JDK tiêu chuẩn có module compiler `jdk.compiler`:

~~~text
Safe API shared: true
Safe implementation local: true
Safe implements host API: true
Safe greeting: hello-plugin
Unsafe API duplicated: true
Unsafe implements host API: false
Unsafe cast: ClassCastException
Platform String delegated: true
~~~

### Giải thích vì sao kết quả này quan trọng

1. Chương trình sinh **hai nguồn API có nội dung giống hệt** và biên dịch vào hai thư mục khác nhau, cùng với implementation ở thư mục child. Đây là trường hợp plugin có bản sao API dù host đã có.
2. `host` (một `URLClassLoader` có parent `null`) định nghĩa `api.shared.Greeter`; bootstrap loader vẫn có thể cung cấp các lớp nền tảng.
3. `safe` nhận class implementation từ `childDir` bằng child-first cho `plugin.impl.*` nhưng mọi yêu cầu `api.shared.*` dùng `super.loadClass`, qua đó **ủy quyền parent host**. `GreeterImpl` vì vậy implements **chính interface host**.
4. `unsafe` tự gọi `findClass` với cả `plugin.impl.*` và `api.shared.*`, khiến interface API bản child được **định nghĩa lại** bởi unsafe loader. Object này implements bản API riêng, không phải API host.
5. `hostApi.isInstance(good)` bằng `true`; `hostApi.isInstance(bad)` bằng `false` và `hostApi.cast(bad)` ném `ClassCastException` dù tên `api.shared.Greeter` giống nhau.
6. `safe.loadClass("java.lang.String")` và `unsafe.loadClass("java.lang.String")` vẫn qua đường `super.loadClass`, trả `String.class` của nền tảng chứ không cố tự định nghĩa một bản.
7. Hai loader `safe` và `unsafe` là hai đối tượng riêng, nên cùng tên implementation `plugin.impl.GreeterImpl` ở mỗi loader cũng là hai định nghĩa khác nhau. Điều đó **được phép** vì implementation là chi tiết riêng; điều không nên là làm mất identity của **contract chia sẻ**.
8. `getClassLoadingLock(name)`, `findLoadedClass(name)` và `resolveClass(type)` được dùng để tránh nạp trùng trong loader và tôn trọng cơ chế resolve. Mọi fallback sang `super.loadClass` vẫn tuân theo chính sách parent.
9. `try-with-resources` đóng các URLClassLoader sau ví dụ; không có lời hứa rằng đóng loader sẽ unload class ngay. Các tệp sinh trong thư mục tạm có thể được dọn sau khi chạy.

**Lưu ý giới hạn:** Loader trong ví dụ chỉ có hai prefix thử nghiệm, không phải một plugin sandbox hoặc class loader production hoàn chỉnh. Nó không xử lý dependency tree phức tạp, module layers, package sealing, metadata `ProtectionDomain`, xung đột resource hay lifecycle của server. Chính sách parent-first cho API là lựa chọn **bắt buộc bởi hợp đồng của ví dụ**, không phải quy tắc tự động cho mọi package có tên `api.shared`.

## 6. Thiết kế package policy không làm mất quyền kiểm soát

Một loader child-first sản xuất thực tế nên phân loại theo **ownership** thay vì theo tên lớp dự đoán tùy tiện:

~~~text
Request: com.acme.api.PaymentService
  → parent/shared loader (API chung)
  → không cho plugin định nghĩa lại

Request: plugin.payment.impl.CardProcessor
  → plugin loader trước (implementation riêng)
  → fallback có điều kiện nếu không có

Request: java.lang.String
  → delegation về runtime/parent
  → không tự cố định nghĩa

Request: some.shared.dto.PaymentRequest
  → loader định nghĩa DTO contract chung

Request: vendor.private.parser.ParserImpl
  → phiên bản plugin riêng nếu cần isolation
~~~

Mấu chốt là phân biệt **API được host và plugin cùng dùng** với **implementation/private dependency**. Nếu cùng một thư viện cần chia sẻ một phần và cô lập phần khác, cần thiết kế rõ type boundary, module graph, relocation hoặc công cụ đóng gói; không chỉ thêm vài điều kiện `startsWith` một cách tình cờ.

### Những API nào phải xuất hiện trên parent/shared loader?

- Interface/base class trong chữ ký host–plugin.
- DTO, enum, exception, annotation và listener type được trao đổi trực tiếp.
- Service/provider contract khi dùng `ServiceLoader` hoặc framework discovery.
- Kiểu được dùng làm khóa trong registry hoặc được kiểm tra bằng `instanceof` / `Class<?>` identity.
- Các loại payload do callback host nhận ở runtime.

Điều này không có nghĩa đưa toàn bộ ứng dụng lên parent loader. Chia sẻ **đúng phần hợp đồng**, giữ lại isolation cho implementation riêng và resource/dependency cần cô lập.

### Loader được chọn phải thực sự thấy API

Chọn `parent-first` không tự tạo class bị thiếu. Nếu parent **không có** API, đường fallback child có thể vẫn định nghĩa bản API riêng. Với contract bắt buộc, cần **đảm bảo parent sở hữu API** lúc triển khai và có thể chọn **fail fast** thay vì âm thầm fallback để tránh split identity.

Một loader production có thể áp dụng rule mạnh hơn:

~~~text
Nếu name thuộc shared API:
    chỉ cho parent tải; nếu parent không tìm thấy → báo lỗi ngay
Nếu name thuộc plugin implementation:
    tìm local trước, fallback có kiểm soát
Các namespace khác:
    dùng chính sách đã cấu hình
~~~

Đây là **pseudocode**, không phải lời gọi Java đầy đủ. Ví dụ runnable dùng `super.loadClass` cho API để minh họa delegation mặc định, nên nếu API hoàn toàn vắng mặt ở parent thì fallback của parent-first vẫn có thể tìm local. **Đừng coi điều đó đã thực thi fail-fast contract**.

## 7. Resource và service provider phải đồng bộ với chính sách lớp

Không chỉ class có vấn đề. Với plugin, `getResource`, `getResources` và `getResourceAsStream` có thể tìm file cấu hình, `META-INF/services/...` hay metadata trong JAR của parent/child.

- API contract nên được định nghĩa chung; metadata/provider discovery cần tìm đúng provider có thể triển khai **cùng interface API**.
- Nếu implementation được tải child-first nhưng `getResource` vẫn parent-first, plugin có thể nhận **cấu hình hoặc schema của phiên bản host**.
- `getResources` thường được dùng để **tổng hợp nhiều resource**, nhất là SPI; không nên tự thay bằng “chỉ lấy entry đầu” mà bỏ provider hợp lệ.
- `ServiceLoader.load(Service.class)` thường sử dụng **thread context class loader (TCCL)**; loader này phải nhìn thấy provider phù hợp với interface chung.
- Cần xem module encapsulation trên Java 9+, vì named module có quy tắc truy cập resource và service riêng.

**Đừng cố chữa lỗi interface bị định nghĩa trùng chỉ bằng đảo thứ tự resource.** Nếu provider thực sự implements bản interface khác defining loader, `ServiceConfigurationError` hay `ClassCastException` vẫn có thể xảy ra.

## 8. Module system (JPMS) có làm parent delegation hết cần thiết?

Không. Từ Java 9, module system thêm **module identity, readability, exports, opens, services** và khả năng xây module layer tùy biến; nó không biến mọi loại class cùng tên ở mọi loader thành một type chung.

Cần phân biệt:

| Khái niệm | Trả lời câu hỏi |
| --- | --- |
| **Class loader** | Ai định nghĩa `Class<?>` này? |
| **Module** | Class thuộc module nào, module đọc và truy cập package nào? |
| **Module layer** | Những module được giải quyết và tổ chức vào layer nào? |
| **Delegation policy** | Khi tìm class theo tên, loader ưu tiên parent hay local? |

Nếu plugin sống trong named module hoặc custom layer, phải xác định class loader được gán cho module và các cạnh `requires`/`exports`/`opens` phù hợp. **Cho phép đọc một module không tự hợp nhất hai định nghĩa class do hai loader khác nhau tạo**; ngược lại, một loader có thể bị module visibility/access chặn dù parent delegation tìm được class.

Trong Java hiện đại, cũng không nên suy rằng mọi module JDK được bootstrap loader định nghĩa; phân phối class giữa bootstrap/platform/application phụ thuộc runtime/module. Quy tắc bền vững là **đừng tự thay thế namespace nền tảng, giữ một định nghĩa API contract chia sẻ và tuân theo cơ chế module hợp lệ**.

## 9. Lỗi thường gặp và dấu hiệu chẩn đoán

| Triệu chứng | Căn nguyên khả dĩ | Hướng kiểm tra |
| --- | --- | --- |
| `ClassCastException: A cannot be cast to A` | Hai định nghĩa class/interface cùng tên nhưng khác defining loader. | So `Class<?>` bằng `==`, `getClassLoader()` ở cả hai phía. |
| `ServiceConfigurationError: ... not a subtype` | Provider không thực sự implements **interface do consumer sử dụng**. | Xác minh service type, provider type và loader/TCCL của discovery. |
| `LinkageError` / loader constraint violation | Chữ ký/phụ thuộc qua boundary liên kết các kiểu có identity mâu thuẫn. | Xem thông báo đầy đủ, method descriptor và API duplicate. |
| `SecurityException` khi cố define `java.*` | Loader custom tự định nghĩa class trong namespace được bảo vệ. | Không override platform class; delegation về loader runtime. |
| `ClassNotFoundException` cho API chung | Parent không nhìn thấy contract hoặc cấu hình classpath/module path sai. | Đảm bảo một nguồn API trên shared loader và kiểm tra visibility. |
| `NoClassDefFoundError` khi sử dụng implementation | Dependency thiếu, không link được hoặc class đã khởi tạo lỗi trước đó. | Kiểm tra cause/stack trace và defining loader các dependency. |
| Resource plugin bị lấy từ host | Resource delegation vẫn parent-first dù implementation child-first. | Log `getResource` và `getResources`, thống nhất chính sách resource. |
| Sai hành vi sau hot redeploy | Object/cache/thread từ loader cũ được chuyển sang loader mới. | Dọn lifecycle, TCCL, registry và singleton chứa reference cũ. |

### Đo identity thay vì chỉ in tên

Fragment sau giả sử `pluginType` là `Class<?>` của implementation, `hostApi` là `Class<?>` của API và `pluginObject` là một instance:

~~~java
System.out.println("Impl loader: "
        + pluginType.getClassLoader());
System.out.println("Host API loader: "
        + hostApi.getClassLoader());
System.out.println("Implements host API: "
        + hostApi.isInstance(pluginObject));
System.out.println("Actual type: "
        + pluginObject.getClass().getName());
~~~

Đây là **fragment**, không phải chương trình độc lập; cần khai báo biến đúng. Hãy đối chiếu thêm:

~~~java
Class<?> apiSeenByPlugin =
        pluginType.getClassLoader()
                  .loadClass(hostApi.getName());

System.out.println("Same API Class: "
        + (apiSeenByPlugin == hostApi));
~~~

Nếu biểu thức cuối là `false`, đã có **split API identity**. Không nên chỉ so chuỗi `getName()` vì chúng có thể bằng nhau ngay cả khi Class khác.

## 10. Những anti-pattern cần tránh

### 10.1. Child-first mọi thứ ngoại trừ java.*

~~~java
// Pseudocode sai về mặt chính sách chia sẻ:
if (!name.startsWith("java.")) {
    return findClass(name);
}
return getParent().loadClass(name);
~~~

Ngoài vấn đề snippet không xử lý cache/fallback/resolve, quy tắc này **không bảo vệ shared API** như `com.acme.api.*`. Nó có thể định nghĩa lại contract và gây cast/linkage errors.

### 10.2. Chỉ cần “bytecode bằng nhau” là cùng kiểu

Không đúng. JVM dùng **binary name + defining loader**; các lớp được định nghĩa từ cùng một API JAR nhưng bởi hai loader khác nhau **không tự trở thành cùng kiểu**.

### 10.3. Parent-first toàn bộ mà không xét implementation riêng

Nếu host và plugin cùng có `vendor.parser.Parser` nhưng cần hai phiên bản khác nhau, parent-first có thể cho plugin dùng bản host ngoài ý muốn. Chỉ chia sẻ những contract cần chia sẻ; implementation có thể child-first theo policy cụ thể.

### 10.4. Dùng TCCL như công cụ sửa định danh kiểu

`Thread.currentThread().setContextClassLoader(loader)` thay đổi loader mà những API dựa trên TCCL sẽ dùng, **không thay class đã được loader khác định nghĩa**. Nó không tự làm `ClassCastException` biến mất; đồng thời nếu quên restore trong worker pool có thể giữ loader plugin cũ.

### 10.5. Mang cả API JAR trong plugin rồi tin parent-first là đủ

Nếu parent chứa API và plugin dùng parent-first, bản API trong plugin có thể không được định nghĩa khi runtime, nhưng vẫn tạo rủi ro cấu hình khi đổi loader policy hoặc packaging. Tốt nhất **không bundle lặp API contract** nếu không cần; kiểm tra dependency exclusion, scope và artefact phân phối.

### 10.6. Quên đồng bộ, cache và fallback khi override loadClass

Một implementation custom phải tránh định nghĩa trùng và race; xử lý `findLoadedClass`, `getClassLoadingLock`, `resolveClass` phù hợp và duy trì fallback parent chính xác. Không gọi `findClass` bừa với mọi class; không bắt `LinkageError` rồi tự “thử lại” bằng cách định nghĩa lớp khác cùng tên.

### 10.7. Nghĩ class loader là sandbox đầy đủ

Parent delegation và cấm định nghĩa `java.*` giúp bảo vệ một phần namespace và type consistency, **không phải mô hình cách ly bảo mật đầy đủ**. Với plugin không tin cậy, cần thiết kế kiểm soát quyền, hệ module, boundary tiến trình/container và kiểm tra nguồn code theo threat model thực.

## 11. Checklist thiết kế cho loader/plugin

1. **Liệt kê namespace nền tảng** mà plugin không được tự định nghĩa; giữ cơ chế runtime/parent thích hợp.
2. **Liệt kê API contract chung**, bao gồm interface, DTO, enum, exception, annotation, callback và SPI service type.
3. **Đảm bảo shared loader thật sự có những contract đó**; nên fail fast nếu thiếu API bắt buộc.
4. **Tách implementation/dependency riêng** của plugin; chỉ dùng child-first có chọn lọc khi có lý do rõ.
5. **Kiểm tra class identity** với `==` và `isInstance` khi object đi qua host–plugin boundary.
6. **Đảm bảo `loadClass` có cơ chế cache/lock/resolve/fallback đúng**, không định nghĩa lại class trong cùng loader.
7. **Xem chính sách resource** (`getResource`, `getResources`, `META-INF/services`) tương thích với class policy.
8. **Xem TCCL/thread pool/lifecycle**, đặc biệt khi framework tìm provider bằng TCCL hoặc có hot redeploy.
9. **Với named modules**, kiểm tra module readability, exports/opens và loader–module assignment.
10. **Viết test có duplicate API trong child** để xác nhận loader an toàn vẫn trả cùng `Class<?>` của parent. Tương tự, test rằng plugin implementation thật sự đến từ child và không bị host “nuốt mất”.

## 12. Tự kiểm tra

**Câu 1.** JVM có bắt mọi `com.example.api.*` phải parent-first không?

**Đáp án:** Không. Đây là **chính sách ứng dụng** cần thực thi nếu các class đó là hợp đồng dùng chung. `java.*` lại có quy tắc bảo vệ namespace đặc biệt khi loader tùy chỉnh dùng `defineClass`.

**Câu 2.** Plugin và host cùng đọc một JAR chứa interface `Greeter` nhưng mỗi bên tự định nghĩa qua loader riêng. Hai `Greeter` có cùng identity không?

**Đáp án:** Không. Dù cùng binary name và bytes, defining loader khác nhau nên là hai kiểu.

**Câu 3.** Vì sao plugin có thể child-first với `plugin.impl.*` nhưng phải parent-first với `api.shared.*`?

**Đáp án:** Implementation là chi tiết riêng cần cô lập; API contract là loại kiểu mà host/plugin phải cùng nhìn thấy để truyền object và cast an toàn.

**Câu 4.** Hai child loader có cùng parent thì tự bảo đảm API cùng một kiểu chưa?

**Đáp án:** Chưa. Parent phải **thực sự định nghĩa/cung cấp API**; nếu thiếu và mỗi child fallback tự định nghĩa, identity vẫn khác. Với contract bắt buộc nên kiểm tra/fail fast.

**Câu 5.** Chỉ set TCCL sang host loader có giải quyết được provider implements interface trùng tên nhưng khác defining loader không?

**Đáp án:** Không. TCCL ảnh hưởng đường tìm lớp/resource của các API sử dụng nó, nhưng không đổi identity của class đã được định nghĩa.

**Câu 6.** Kiểu `java.lang.String` trong chương trình Java hiện đại có nên được loader plugin định nghĩa lại không?

**Đáp án:** Không. Dùng class nền tảng do runtime cung cấp; cố định nghĩa `java.*` bằng custom ClassLoader thông thường vi phạm quy tắc bảo vệ package.

## Ghi nhớ

**Ủy quyền cho lớp nền tảng** bảo đảm plugin không tự thay thế các kiểu mà runtime quản lý. **Ủy quyền cho API contract dùng chung** bảo đảm host và plugin sử dụng **cùng một định nghĩa class/interface**, không chỉ cùng tên. Chiến lược thường phù hợp là **parent-first đối với platform + shared API**, **child-first có giới hạn đối với implementation riêng**. Với API contract quan trọng, parent phải thực sự sở hữu class đó; nếu không, hãy báo lỗi cấu hình thay vì âm thầm tạo bản sao. Khi debug, xác nhận bằng `Class<?> ==`, `getClassLoader()` và `isInstance(...)`.

**Tài liệu chuẩn:**

- Java SE 17 API, `ClassLoader` (delegation, `loadClass`, `defineClass`): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17 API, `URLClassLoader`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html
- *Java Virtual Machine Specification* §5.3, class identity/creation: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- *Java Language Specification* §12.2, loading: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.2
- Java SE 17 API, `ServiceLoader`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="loading-policies-005">Cô lập nhiều phiên bản thư viện giữa các plugin</a>

<details>
<summary>Xem chi tiết</summary>

# Cô lập nhiều phiên bản thư viện giữa các plugin bằng ClassLoader

## 1. Bài toán: hai plugin cần hai bản thư viện có cùng tên lớp

Giả sử ứng dụng host chạy hai plugin:

- **Plugin A** được biên dịch với thư viện `lib.Versioned` bản 1, method `value()` trả `"v1:legacy"`.
- **Plugin B** được biên dịch với thư viện `lib.Versioned` bản 2, method `value()` trả `"v2:modern"`.
- Cả hai đều triển khai interface `api.Plugin` do host cung cấp.

Nếu toàn bộ class được nạp bởi một application class loader duy nhất, cùng binary name `lib.Versioned` không thể đồng thời đại diện cho **hai định nghĩa class khác nhau trong chính loader đó**. Một bản có thể bị dùng thay bản kia; các plugin có thể cho ra kết quả sai hoặc gặp `NoSuchMethodError`/`LinkageError` khi API không tương thích.

Java cho phép cô lập các runtime classes nhờ class loaders riêng:

```text
                       Host / Application loader
                       └── api.Plugin (shared API)
                          /                    \
                         /                      \
                Plugin loader A            Plugin loader B
                ├── plugin.Implementation   ├── plugin.Implementation
                └── lib.Versioned (v1)      └── lib.Versioned (v2)
```

Ở ví dụ này, hai class `lib.Versioned` có **cùng binary name nhưng khác defining class loader**, nên là hai runtime types khác nhau. Ngược lại, cả hai implementation đều dùng **cùng** `api.Plugin` được định nghĩa ở loader chung. Đây là mô hình cốt lõi của **version isolation** trong hệ plugin.

**Mục tiêu không phải ép mọi thứ tách biệt.** Cần cô lập *implementation và thư viện phụ thuộc có phiên bản khác nhau*, nhưng **chia sẻ ổn định interface/DTO/contract đi qua ranh giới host–plugin**.

## 2. Cơ sở JVM: cùng tên lớp chưa đủ tạo cùng kiểu

Theo JVMS, một runtime class/interface được nhận diện bởi:

**Binary name + defining class loader**

Ví dụ:

```text
("lib.Versioned", loaderA) != ("lib.Versioned", loaderB)
("api.Plugin",    appLoader) == ("api.Plugin",    appLoader)
```

Điều này có nghĩa:

- `Class<?> versionA = loaderA.loadClass("lib.Versioned")` và `versionB = loaderB.loadClass("lib.Versioned")` có thể có `getName()` giống nhau.
- Nếu thực sự do loaderA và loaderB định nghĩa riêng, `versionA == versionB` là `false`.
- Một object thuộc `lib.Versioned` của loaderA không tự nhiên cast được thành `lib.Versioned` của loaderB.
- Static fields của hai runtime classes này độc lập; một singleton bằng static field trong thư viện có thể là **một singleton cho mỗi defining loader**, không phải một singleton toàn JVM.
- Một loader thứ ba có thể ủy quyền tới một trong hai loader và nhận chính `Class<?>` của parent, chứ không tự động tạo bản sao.

**Defining loader** là nơi thực sự định nghĩa class; **initiating loader** là loader được JVM ghi nhận đã khởi xướng yêu cầu nạp/resolve class ấy. `clazz.getClassLoader()` phản ánh defining loader đối với class thông thường, không đơn giản trả về object đã gọi `loadClass` gần nhất.

### Tại sao cùng bytes vẫn có thể là hai kiểu?

Ngay cả nếu hai plugin chứa class files byte-for-byte giống nhau, khi hai loaders khác nhau thực sự định nghĩa cùng binary name, JVM vẫn tạo **hai class identities**. Class loading là một phần của hệ thống kiểu Java, không chỉ là cơ chế tìm file.

Ngược lại, hai loaders có tên giống nhau (`getName()` bằng nhau) vẫn có thể là hai objects/defining loaders khác nhau. Loader name là nhãn chẩn đoán, không là khóa định danh runtime class.

## 3. Đâu là shared API, đâu là private dependency?

Hãy quyết định kiến trúc trước khi chọn parent delegation:

| Nhóm class | Nơi nên định nghĩa thông thường | Lý do |
|---|---|---|
| `java.lang.*`, thư viện lõi JVM | Built-in loaders phù hợp | Các plugin dùng chung nền tảng runtime |
| `api.Plugin` và DTO/interface dùng qua host boundary | Shared API loader của host | Host và plugin cần **cùng runtime type identity** |
| `plugin.Implementation` | Plugin loader tương ứng | Có thể có cùng binary name giữa nhiều plugin mà vẫn tách biệt |
| `lib.Versioned` bản 1 | Plugin A loader | Độc lập bản 2 |
| `lib.Versioned` bản 2 | Plugin B loader | Độc lập bản 1 |
| Thư viện thật sự dùng chung và tương thích | Common parent loader nếu đã thống nhất version | Giảm bản sao và tránh classloader leak |
| Thư viện riêng hoặc có nguy cơ xung đột version | Loader riêng của từng plugin | Cho phép version khác nhau cùng tồn tại |

Ví dụ API an toàn qua boundary:

```java
package api;

public interface Plugin {
    String run();
}
```

Chữ ký `String run()` dùng kiểu nền tảng `java.lang.String` do runtime thống nhất, không ép host phải hiểu `lib.Versioned` thuộc plugin A hay B.

**Không nên để shared API của host có kiểu trả về/tham số là thư viện private của plugin**, chẳng hạn:

```java
// Tránh thiết kế kiểu này ở shared API:
lib.Versioned run();
```

Đây là khai báo minh họa, không phải chương trình độc lập. Nếu host và plugin resolve `lib.Versioned` qua hai loaders có định nghĩa khác nhau, JVM có thể gặp **loading constraint violation** ở field/method descriptor khi liên kết, hoặc host không có đúng class cần để biên dịch/chạy.

Thay vào đó, truyền qua boundary các **shared interfaces/DTO**, primitive/String, hoặc dữ liệu được chuyển đổi ở ranh giới như JSON/byte arrays với schema/versioning rõ ràng. Chuyển dữ liệu không đồng nghĩa tự động làm mọi version API tương thích: vẫn cần quản lý schema và hành vi.

## 4. Parent-first có khi đã đủ để cô lập hai thư viện

`URLClassLoader` kế thừa chiến lược `loadClass` mặc định: thông thường **parent-first**. Khi loader con cần một tên class, nó kiểm tra class đã có, hỏi parent, rồi mới tìm trong URLs của chính nó nếu parent không tìm được.

Có thể cô lập các thư viện khác phiên bản bằng **hai URLClassLoader objects riêng** mà **không cần override `loadClass`**, miễn:

1. Class path của **parent không chứa** những class private như `lib.Versioned`.
2. Mỗi plugin loader có đường dẫn riêng chứa phiên bản thư viện tương ứng.
3. Shared API `api.Plugin` được định nghĩa bởi parent và được plugin loaders ủy quyền về cùng định nghĩa.

```text
Parent (classpath: out/api + out/host)
     không có lib.Versioned
          |                         |
          v                         v
Plugin A URLClassLoader      Plugin B URLClassLoader
  out/plugin-a/                out/plugin-b/
  lib.Versioned v1             lib.Versioned v2

Yêu cầu lib.Versioned:
  A -> parent không có -> A tự định nghĩa v1
  B -> parent không có -> B tự định nghĩa v2

Yêu cầu api.Plugin:
  A -> parent trả API chung
  B -> parent trả API chung
```

**Đây thường là điểm bắt đầu dễ bảo trì nhất**: cô lập class path riêng cho mỗi plugin, không phức tạp hóa thuật toán tải lớp nếu không cần.

### Khi nào parent-first không đủ?

Nếu host application class path đã chứa `lib.Versioned` bản 1, parent của cả hai plugin có thể trả về bản 1 **trước khi** plugin B có cơ hội tìm bản 2 trong thư mục riêng.

Hệ quả:

- Plugin B có thể chạy với behavior của v1 thay vì v2.
- Nếu B được biên dịch để gọi method chỉ có ở v2 nhưng runtime nhận v1, có thể gặp `NoSuchMethodError` khi symbolic reference được resolve.
- Lỗi không phụ thuộc việc plugin B có file v2 trong JAR: **parent đã trả class cùng tên trước**.

Cách giải quyết ưu tiên: **không đặt private, versioned library trên class path của parent**. Nếu kiến trúc bắt buộc parent chứa thư viện xung đột, có thể cần **selective child-first** cho namespace private; không nên đảo thứ tự cho tất cả package một cách tùy tiện.

## 5. Demo Java 17 chạy được: hai phiên bản, hai URLClassLoader, một shared API

Bài demo sử dụng hai bản class **cùng binary name `lib.Versioned`** nhưng source/bytecode khác nhau. Chúng được biên dịch vào **hai output directories riêng**, sau đó nạp bởi hai loader objects. Host chỉ thấy shared API.

### 5.1. Cấu trúc file nguồn

Tạo cây thư mục:

```text
src/
  api/
    Plugin.java
  plugin-a/
    lib/
      Versioned.java
    plugin/
      Implementation.java
  plugin-b/
    lib/
      Versioned.java
    plugin/
      Implementation.java
  host/
    PluginHost.java
```

**File `src/api/Plugin.java`:**

```java
package api;

public interface Plugin {
    String run();
}
```

**File `src/plugin-a/lib/Versioned.java`:**

```java
package lib;

public final class Versioned {
    public String value() {
        return "v1:legacy";
    }
}
```

**File `src/plugin-a/plugin/Implementation.java`:**

```java
package plugin;

import api.Plugin;
import lib.Versioned;

public final class Implementation implements Plugin {
    @Override
    public String run() {
        return new Versioned().value();
    }
}
```

**File `src/plugin-b/lib/Versioned.java`:**

```java
package lib;

public final class Versioned {
    public String value() {
        return "v2:modern";
    }
}
```

**File `src/plugin-b/plugin/Implementation.java`:**

```java
package plugin;

import api.Plugin;
import lib.Versioned;

public final class Implementation implements Plugin {
    @Override
    public String run() {
        return new Versioned().value();
    }
}
```

Lưu ý hai cặp `lib.Versioned` và `plugin.Implementation` đều **cùng package + class name**, nhưng được biên dịch riêng cho hai plugin. Shared interface `api.Plugin` chỉ có **một** source/output chung.

### 5.2. Host chọn loader rõ ràng

**File `src/host/PluginHost.java`:**

```java
package host;

import api.Plugin;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public final class PluginHost {
    private static URL pluginUrl(String directory)
            throws Exception {
        return Path.of(directory).toUri().toURL();
    }

    private static Plugin create(URLClassLoader loader)
            throws Exception {
        Class<?> implementation =
                Class.forName("plugin.Implementation", true, loader);
        Object instance =
                implementation.getDeclaredConstructor().newInstance();
        return (Plugin) instance;
    }

    public static void main(String[] args) throws Exception {
        ClassLoader shared = Plugin.class.getClassLoader();

        try (URLClassLoader loaderA = new URLClassLoader(
                        new URL[]{pluginUrl("out/plugin-a")}, shared);
             URLClassLoader loaderB = new URLClassLoader(
                        new URL[]{pluginUrl("out/plugin-b")}, shared)) {

            Plugin a = create(loaderA);
            Plugin b = create(loaderB);

            Class<?> libA = loaderA.loadClass("lib.Versioned");
            Class<?> libB = loaderB.loadClass("lib.Versioned");

            System.out.println("Plugin A: " + a.run());
            System.out.println("Plugin B: " + b.run());

            System.out.println("Libraries have same name: "
                    + libA.getName().equals(libB.getName()));
            System.out.println("Libraries have different identity: "
                    + (libA != libB));
            System.out.println("Plugin types have different identity: "
                    + (a.getClass() != b.getClass()));

            System.out.println("A library defined by A: "
                    + (libA.getClassLoader() == loaderA));
            System.out.println("B library defined by B: "
                    + (libB.getClassLoader() == loaderB));

            System.out.println("Both share API: "
                    + (a.getClass().getInterfaces()[0] == Plugin.class
                       && b.getClass().getInterfaces()[0] == Plugin.class));
        }
    }
}
```

Giải thích thiết kế:

- `shared` là defining loader của `api.Plugin` trên class path thông thường. Hai plugin loaders nhận nó làm **parent**.
- `URLClassLoader` được cấu hình bằng thư mục riêng đã chứa class files của plugin. URL trỏ vào **thư mục**, không phải mọi file `.class` riêng lẻ.
- Host **không đưa `out/plugin-a` hoặc `out/plugin-b` lên chính application class path**, nên parent không tự thấy private `lib.Versioned` và `plugin.Implementation`.
- `Class.forName(..., true, loader)` chọn đúng plugin loader và yêu cầu initialization của implementation, rồi reflection tạo object qua public zero-arg constructor ngầm định.
- Hai objects đều implement interface `api.Plugin` của parent; phép cast trong `create` hợp lệ. Nếu vô tình đóng gói API copy và child-first cho namespace `api.*`, phép cast này có thể không hợp lệ.
- Khối `try-with-resources` đóng `URLClassLoader` khi kết thúc phạm vi; việc đóng **không tự làm JVM unload mọi class ngay lập tức**.

### 5.3. Biên dịch hai phiên bản tách biệt

Tại thư mục gốc chứa `src`, chạy lần lượt:

```bash
mkdir -p out/api out/plugin-a out/plugin-b out/host

javac -d out/api src/api/Plugin.java

javac -cp out/api -d out/plugin-a \
  src/plugin-a/lib/Versioned.java \
  src/plugin-a/plugin/Implementation.java

javac -cp out/api -d out/plugin-b \
  src/plugin-b/lib/Versioned.java \
  src/plugin-b/plugin/Implementation.java

javac -cp out/api -d out/host src/host/PluginHost.java

java -cp "out/api:out/host" host.PluginHost
```

Đối với **Windows PowerShell**, dùng class path separator `;`, và trình bày các lệnh trên một dòng:

```powershell
New-Item -ItemType Directory -Force out/api,out/plugin-a,out/plugin-b,out/host

javac -d out/api src/api/Plugin.java

javac -cp out/api -d out/plugin-a src/plugin-a/lib/Versioned.java src/plugin-a/plugin/Implementation.java

javac -cp out/api -d out/plugin-b src/plugin-b/lib/Versioned.java src/plugin-b/plugin/Implementation.java

javac -cp out/api -d out/host src/host/PluginHost.java

java -cp "out/api;out/host" host.PluginHost
```

**Quan trọng:** Lệnh chạy `java` **chỉ** đưa `out/api` và `out/host` vào application class path. Nếu thêm `out/plugin-a` vào class path của host, parent có thể tìm thấy v1 sớm và làm sai mục tiêu kiểm thử.

### 5.4. Stdout dự kiến

Với JDK 17+ và cấu trúc sạch như trên:

```text
Plugin A: v1:legacy
Plugin B: v2:modern
Libraries have same name: true
Libraries have different identity: true
Plugin types have different identity: true
A library defined by A: true
B library defined by B: true
Both share API: true
```

**Phân tích:** 

1. Cả hai `Versioned.value()` tồn tại với cùng tên class, nhưng trả hai phiên bản hành vi khác nhau. Đây là bằng chứng mỗi plugin dùng class của riêng nó.
2. `libA.getName().equals(libB.getName())` là `true` vì hai class có binary name bằng nhau.
3. `libA != libB` là `true` vì runtime identity khác theo defining loader. Chính đó là isolation mà ứng dụng cần.
4. `a.getClass() != b.getClass()` là `true`: implementation cùng binary name cũng được nạp riêng.
5. `libA.getClassLoader() == loaderA` và `libB.getClassLoader() == loaderB` chứng minh loader nào **định nghĩa** hai thư viện.
6. Hai class implementation đều triển khai chính xác `api.Plugin` từ loader chung; kết quả `Both share API: true` cho thấy boundary hợp lệ.

Ví dụ cố ý **không** dùng static final compile-time constant để đại diện version, vì Java compiler có thể inline những constant values và khiến output không còn chứng minh lớp `lib.Versioned` được nạp từ đâu.

## 6. Khi cần selective child-first cho thư viện private

Trên nhiều platform, host hoặc một dependency của host đã đặt `lib.Versioned` vào parent class path. Parent-first lúc này làm plugin B nhận version từ parent, dù đã có JAR riêng.

Một phương án là tạo **child-first chỉ với package private**. Với các package như `java.*`, shared API `api.*`, logging contract và các kiểu giao tiếp host, vẫn ưu tiên parent.

Khung code minh họa:

```java
import java.net.URL;
import java.net.URLClassLoader;

public class SelectivePluginLoader extends URLClassLoader {
    public SelectivePluginLoader(String name, URL[] urls,
                                 ClassLoader parent) {
        super(name, urls, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException {
        if (!name.startsWith("lib.privatecopy.")) {
            return super.loadClass(name, resolve);
        }

        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                try {
                    c = findClass(name); // ưu tiên plugin URL
                } catch (ClassNotFoundException missingLocally) {
                    c = super.loadClass(name, false); // có thể fallback
                }
            }

            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }
}
```

Đây là **một class có thể biên dịch trong `SelectivePluginLoader.java`**, nhưng chỉ là mẫu chính sách, chưa thay thế ví dụ phần 5. Prefix `lib.privatecopy.` cần đổi cho đúng namespace được plugin sở hữu; ví dụ demo dùng `lib.Versioned`, nên nếu muốn áp dụng vào demo phải đổi điều kiện package cho phù hợp và quản lý cả các class phụ thuộc liên quan.

Các lưu ý rất quan trọng:

- Luôn dùng `findLoadedClass` dưới `getClassLoadingLock(name)` trước khi tự tìm/định nghĩa; tránh nỗ lực `defineClass` lặp trong cùng loader.
- Đừng áp dụng child-first bừa bãi cho `java.*`, `javax.*` hoặc shared API `api.*`.
- **Fallback parent** có thể đánh mất tính cô lập nghiêm ngặt nếu private dependency không có ở plugin nhưng parent có version khác. Trong kiến trúc đòi version isolation tuyệt đối, có thể **không fallback** cho namespace private.
- Một package prefix quá rộng có thể vô tình cô lập cả SPI interface/DTO mà host cần dùng chung.
- Nhiều class trong cùng thư viện có references lẫn nhau; chỉ isolate một class trong một thư viện nhiều class thường **không đủ**. Hãy xét cả dependency graph/transitive dependencies.
- Class-loader constraints, package sealing, JPMS boundaries và access rules vẫn áp dụng.
- Nếu library dùng resource lookup, native code hoặc context class loader, việc đổi class lookup order chưa chắc làm mọi dependency/resource cũng đi đúng phiên bản.

**Khuyến nghị:** trước khi override `loadClass`, thử cách đơn giản hơn: giữ private versioned libs **ngoài parent class path** và cung cấp riêng cho từng `URLClassLoader`. Override chỉ khi có lý do rõ ràng và bộ test class identity.

## 7. Khi nào cô lập phiên bản không còn là ranh giới an toàn?

Version isolation hoạt động tốt khi thư viện private **ở bên trong plugin**, không lộ những object thuộc runtime class riêng của plugin qua shared host boundary.

### Lỗi: trả trực tiếp object của private library

Xét host API:

```java
// Minh họa một API gây lệ thuộc vào private lib:
package api;

public interface Converter {
    lib.Versioned convert(lib.Versioned input);
}
```

Đoạn này không phải thiết kế plugin an toàn. Khi host định nghĩa `Converter`, nó cần biết `lib.Versioned` trong chữ ký. Nếu plugin lại dùng bản khác của `lib.Versioned`, việc resolve các field/method descriptors hoặc method override có thể gây **loading constraint violation** và `LinkageError`.

### Mô hình tốt hơn

```java
package api;

public interface Converter {
    String convert(String input);
}
```

Giữ private library bên trong implementation:

```java
package plugin;

public class ConverterImpl implements api.Converter {
    @Override
    public String convert(String input) {
        // Chuyển đổi bằng versioned library chỉ plugin này nhìn thấy.
        return input.trim();
    }
}
```

Hai khối này là **các file riêng cần đặt theo package**, không phải một chương trình đơn file. `ConverterImpl` chỉ minh họa boundary, không tự chứng minh đã dùng một thư viện cụ thể. Ý tưởng là contract dùng những shared types được tất cả bên hiểu thống nhất.

Với payload phức tạp, có thể dùng DTO do loader chung định nghĩa, một API serialization có schema rõ ràng, hoặc interface adapter do host cung cấp. Nhưng JSON/serialization cũng có vấn đề version/compatibility riêng; không phải giải pháp miễn kiểm thử.

## 8. Parent-first, child-first và class identity trong bảng so sánh

| Thiết kế | Thư viện private có thể dùng version riêng? | Rủi ro |
|---|---|---|
| Một application class loader cho mọi plugin | Thường **không**, nếu cùng binary name với các bản khác nhau | Version conflict, `NoSuchMethodError` |
| Mỗi plugin `URLClassLoader`, parent không có private lib | **Có**, thường đủ với parent-first | Đường tìm class/resource phải đúng |
| Mỗi plugin loader, parent đã có private lib, parent-first | Thường bị version ở parent che khuất | Silent wrong version hoặc linkage error |
| Selective child-first cho private namespace | **Có thể**, nếu prefix và transitive deps được kiểm soát | Duplicate shared types, package/resource bugs |
| Child-first với mọi package, kể cả shared API | Không phải thiết kế an toàn | `ClassCastException`/`LinkageError`, vi phạm package rules |
| Shading/relocation đổi package/binary name cho dependency | Có thể chạy nhiều version cùng một loader | Quy trình build phức tạp, resource/reflection/service metadata cần cập nhật |
| Chạy mỗi plugin ở process/JVM riêng | Cách ly mạnh hơn class loader | Chi phí IPC/deployment và protocol compatibility |

**Shading/relocation** là lựa chọn khác: đổi binary name (ví dụ từ `lib.Versioned` sang `pluginA.shaded.lib.Versioned`) trong build artifact để loại bỏ collision tên. Nó không đồng nghĩa với classloader isolation và cần công cụ build xử lý reflection, serialized names, service files/resource references tương ứng.

**Nhiều process/JVM** thường phù hợp hơn khi yêu cầu isolation về an toàn hoặc fault containment. Classloader isolation **không phải security sandbox** và không cô lập hoàn toàn native state, process-wide system properties, static state của shared classes hoặc threads dùng chung.

## 9. ServiceLoader, TCCL và provider của từng plugin

Trong hệ plugin có nhiều versions, code host có thể dùng `ServiceLoader` để tìm implementations của shared interface `api.Plugin`.

API:

```java
java.util.ServiceLoader<api.Plugin> providers =
        java.util.ServiceLoader.load(api.Plugin.class, pluginLoader);
```

Trong snippet này, `pluginLoader` cần là loader của plugin mà host muốn tìm. Đây là **explicit loader**, tránh phụ thuộc vào `Thread.currentThread().getContextClassLoader()` khi host chạy trên worker pool.

Có thể bổ sung file resource trong **mỗi thư mục plugin**:

```text
out/plugin-a/META-INF/services/api.Plugin
out/plugin-b/META-INF/services/api.Plugin
```

Mỗi file chứa một dòng:

```text
plugin.Implementation
```

Khi đó `ServiceLoader.load(api.Plugin.class, loaderA)` có thể tìm provider của A; tương tự loaderB cho B. Shared `api.Plugin` phải do parent cung cấp để provider assignable với host.

**Cảnh báo:** `ServiceLoader.load(api.Plugin.class)` overload một tham số dùng **TCCL của thread hiện tại**. Trong thread pool, TCCL có thể trỏ vào plugin khác hoặc loader do container đặt. Nếu đã biết loader của plugin cần truy vấn, truyền nó tường minh.

Không tự động suy rằng `ServiceLoader` chỉ quét resource của child: cơ chế resource lookup có thể tuân parent và layer behavior. Nếu parent cũng quảng bá provider, có thể thấy cả providers do parent biết. Muốn kiểm soát chặt phạm vi provider, thiết kế discovery riêng hoặc classloader/resource policy phù hợp, test thực tế.

### TCCL dễ gây classloader leak

Nếu host thread pool giữ `pluginLoader` qua `Thread.setContextClassLoader(pluginLoader)` rồi không phục hồi, loader đó có thể không được GC ngay cả sau khi plugin unload logic.

Mẫu an toàn khi framework bắt buộc dùng TCCL:

```java
Thread worker = Thread.currentThread();
ClassLoader before = worker.getContextClassLoader();
try {
    worker.setContextClassLoader(pluginLoader);
    // Thực hiện lời gọi SPI/framework dùng TCCL.
} finally {
    worker.setContextClassLoader(before);
}
```

Đây là mã minh họa cần `pluginLoader` trong scope. Thay đổi/khôi phục phải xảy ra trên **thread thực sự chạy lookup**, không chỉ trên thread submit task.

## 10. Resources, JNI, module system và giới hạn cô lập

### 10.1. Resources không phải class

Nhiều thư viện dùng tài nguyên như `config.properties`, `META-INF/services/...`, XML hoặc native libraries. Class identity cô lập không đảm bảo resource lookup tự động dùng cùng loader:

- `SomeClass.class.getResourceAsStream(...)` liên quan tới class owner và resource path.
- `pluginLoader.getResourceAsStream(...)` dùng loader do caller chọn và các quy tắc tra cứu resource.
- `Thread.currentThread().getContextClassLoader().getResource(...)` phụ thuộc context thread, có thể trỏ nhầm plugin.

Khi debug “class đúng version nhưng cấu hình sai”, xem cả **resource lookup path**. Với URLClassLoader, resource URLs có thể bao gồm directories hoặc JARs; thứ tự URLs và parent delegation ảnh hưởng kết quả.

### 10.2. Native libraries không cô lập giống bytecode

Nếu hai Java libraries phụ thuộc chung JNI native library, khả năng nạp và chia sẻ native code chịu quy tắc `System.loadLibrary`/JVM và hệ điều hành. Không thể suy rằng hai `URLClassLoader` tự cung cấp hai namespace native độc lập như với Java class files.

Trên JVM, việc cố nạp cùng một native library bởi hai class loaders có thể gây `UnsatisfiedLinkError` trong các trường hợp tương ứng. Nếu thư viện có JNI hoặc singleton state ở mức process, cần xem xét cách thiết kế native dependency và khả năng tách process.

### 10.3. JPMS và ModuleLayer

Ví dụ `URLClassLoader` phần 5 sử dụng **class path**, nơi thư viện là unnamed module của loader tương ứng. Với **named modules**, loader parent không tự bỏ qua module readability/exports/opens; cần cấu hình module graph và có thể cân nhắc `ModuleLayer` riêng.

Có thể tạo module layers khác nhau cho plugin khi hệ thống được thiết kế modular, nhưng vẫn phải phân biệt class identity, API chia sẻ, split packages, service binding và module access.

### 10.4. Không coi class loader là security sandbox

Class loader tùy chỉnh không ngăn tuyệt đối plugin sửa system properties, tạo threads, tiêu thụ CPU/memory hay gọi API nền tảng mà code có quyền sử dụng. Để chạy code không tin cậy, cân nhắc cách ly bằng process/container với chính sách hệ thống phù hợp, không chỉ bằng child-first delegation.

## 11. Lifecycle: nạp, chạy, dừng và nạp lại plugin

Để hỗ trợ nhiều phiên bản song song hoặc reload:

1. **Tạo loader riêng** cho phiên bản plugin với class/resource sources cụ thể.
2. **Nạp implementation** và đưa object qua shared API.
3. **Dùng plugin** trong phạm vi đã xác định; không rò rỉ private `Class<?>` vào registry dài hạn.
4. **Dừng plugin**: hủy listeners/callbacks, shutdown executors, bỏ thread locals, đóng resources.
5. **Đóng URLClassLoader** khi không còn tác vụ dùng nguồn class/resource và bỏ references tới loader cùng các object/class của plugin.
6. **Nếu cần phiên bản mới**, thường tạo loader mới với class bytes/URLs của version mới.

Một object `ClassLoader` đã định nghĩa `lib.Versioned` không thể biến định nghĩa runtime cũ thành version mới bằng cách sửa file JAR phía dưới rồi gọi `loadClass` lại. Class đã được ghi nhận cho loader đó thường được tái sử dụng; nỗ lực `defineClass` lặp cùng binary name có thể gây `LinkageError`.

`URLClassLoader.close()` đóng các resources có thể đóng do loader quản lý và ngăn một số hoạt động lookup tiếp theo, nhưng **không tự unload class đã load** hoặc làm object đã tạo biến mất. Class unloading phụ thuộc reachability/classloader và chính sách VM.

### Những references thường cản unloading

- Thread còn chạy với TCCL của plugin.
- ThreadLocal chứa object/plugin class.
- Host static cache giữ `Class<?>`, `Method`, provider instance hoặc class loader.
- Event listener/callback chưa unregister.
- Executor, timer, scheduled task chưa shutdown.
- ServiceLoader/provider cache hoặc registry còn giữ plugin instances.

Quan sát JVM:

```bash
java -Xlog:class+load=info,class+unload=info -cp "out/api:out/host" host.PluginHost
```

Lệnh minh họa cho HotSpot/JDK 17 trên Linux/macOS. Nó in nhiều events class loading/unloading, **không bảo đảm** thấy một dòng unload ngay khi `try-with-resources` đóng loader.

## 12. Chẩn đoán lỗi khi hai phiên bản không hoạt động như dự kiến

### Lỗi A: Hai plugin đều in giá trị của bản 1

Khả năng thường gặp:

- Parent class path đã có v1 và nạp trước hai children.
- Cả hai plugin loaders cùng trỏ vào một directory/JAR.
- Plugin B dùng TCCL hoặc class loader của A khi reflection/resource lookup.
- Library được **shaded** hoặc copied nhầm khi đóng gói.

Cách kiểm tra:

```java
System.out.println(libA.getName());
System.out.println(libA.getClassLoader());
System.out.println(libB.getClassLoader());
System.out.println(libA == libB);
```

Đoạn này minh họa cần biến `libA` và `libB` đã được lấy. Nếu `libA == libB` là true, cùng class runtime đang được dùng; vấn đề là **lookup/delegation**, không phải sự tồn tại của hai file riêng.

### Lỗi B: NoSuchMethodError hoặc NoSuchFieldError

Plugin được biên dịch dựa trên API của v2 nhưng runtime resolve về v1, nơi thiếu method/field tương ứng. Cần so sánh **runtime class source và defining loader** với class path lúc compile.

Nếu cần quan sát code thực tế trong class file:

```bash
javap -classpath out/plugin-a -p lib.Versioned
javap -classpath out/plugin-b -p lib.Versioned
```

Trong ví dụ demo, hai bản có cùng method signature nhưng khác behavior; với API thực tế, method descriptors có thể khác nhau. `javap` cho biết bytecode từ đường dẫn được chỉ định, **không tự chứng minh** runtime đã chọn bản đó.

### Lỗi C: ClassCastException giữa hai class có cùng tên

Có thể xảy ra khi host/plugin cố ép kiểu một object do loader A định nghĩa thành class cùng tên do loader B định nghĩa. Kiểm tra:

```java
System.out.println(typeA.getName().equals(typeB.getName()));
System.out.println(typeA == typeB);
```

Nếu tên giống nhau nhưng `Class<?>` khác nhau, vấn đề nằm ở runtime class identity. Không sửa bằng cách đổi loader name hoặc cast qua `Object`; cần thống nhất shared contract và adapter thích hợp.

### Lỗi D: LinkageError do loader constraint violation

JVM áp đặt loading constraints khi các types trong field/method descriptors ở ranh giới loader cần được hiểu nhất quán. Nếu host API và plugin tự định nghĩa hai bản `api.Message` có cùng tên, JVM có thể từ chối liên kết bằng `LinkageError` khi constraint không thỏa.

Giải pháp: để `api.Message` và các shared DTO được định nghĩa tại **common API loader**, tránh để private library types len vào API signature.

### Lỗi E: ClassNotFoundException hoặc NoClassDefFoundError

Không phải chỉ có vấn đề version. Plugin loader có thể thiếu transitive dependency, resource URL không hợp lệ hoặc parent không nhìn thấy shared contract.

- `ClassNotFoundException` thường xuất hiện khi lookup theo yêu cầu không thấy binary name.
- `NoClassDefFoundError` có thể phát sinh khi resolution/runtime cần class nhưng không tìm được hoặc class initialization trước đó thất bại.
- Không gộp hai loại lỗi thành “loader không chạy” mà bỏ qua stack trace.

### Lỗi F: ServiceLoader tìm sai provider

Kiểm tra xem overload sử dụng **explicit loader** hay **TCCL**, resource `META-INF/services/...` nằm ở plugin nào, provider có implement **cùng runtime shared API type** hay không, và có providers do parent hiển thị thêm không.

### Quy trình kiểm tra có hệ thống

1. Ghi binary name của class bị lỗi, compile-time dependency version và plugin owner.
2. Ghi `Class.getClassLoader()` của runtime class, đừng chỉ in `getName()`.
3. Kiểm tra URL/class path của parent và từng child.
4. Với shared API, so sánh trực tiếp `Class<?>` ở host và plugin.
5. Kiểm tra class bytes bằng `javap -classpath` và artifact inventory.
6. Nếu cần, bật `-Xlog:class+load=info` trên HotSpot để quan sát lookup; log format tùy VM và không chứng minh toàn bộ nguyên nhân nếu thiếu thêm phân tích.
7. Kiểm tra TCCL, SPI, resources và transitive dependencies.
8. Sửa ở **dependency ownership và delegation policy**, không chỉ xóa cache hoặc đổi tên file JAR.

## 13. Các sai lầm phổ biến

| Nhầm lẫn | Sự thật |
|---|---|
| “Hai file JAR chứa cùng class name thì JVM luôn nạp cả hai.” | Một loader thường chỉ định nghĩa một runtime class cho một binary name; cần các defining loaders khác nhau hoặc relocation. |
| “Tạo hai URLClassLoader là chắc chắn có hai version.” | Nếu parent đã có class private, cả hai children parent-first có thể dùng chung version của parent. |
| “Child-first cho toàn bộ package là cách an toàn nhất.” | Có thể nhân bản shared API types và gây ClassCastException/LinkageError. |
| “Cùng bytes là cùng runtime type.” | Defining loader cũng quyết định class identity. |
| “Chỉ cần khác getName() của loader là đã cô lập.” | Loader name là nhãn, không quyết định lookup hay identity. |
| “Chữ ký host có thể thoải mái chứa plugin-private types.” | Đó là nơi loading constraints và incompatibility có thể phát sinh. |
| “Đóng URLClassLoader là class của nó unload ngay.” | Close không tự làm mọi Class<?>/object unreachable. |
| “TCCL luôn bằng loader của plugin đang chạy.” | TCCL thuộc thread, có thể bị framework/thread pool thay đổi. |
| “Classloader isolation cô lập cả JNI/process-wide state.” | Native/process-wide resources có lifecycle và hạn chế khác. |
| “NoSuchMethodError nghĩa class chưa được nạp.” | Class có thể đã nạp **sai phiên bản**, dẫn đến thiếu method theo descriptor kỳ vọng. |
| “ServiceLoader.load(service) luôn chỉ tìm provider của plugin đang xét.” | Nó dùng TCCL; muốn chọn plugin cụ thể, truyền loader tường minh và xét parent visibility. |

## 14. Bài tập tự kiểm tra

**Câu 1.** Hai loaders định nghĩa cùng `lib.Versioned` nhưng từ hai JAR khác nhau. Kết quả `classA.getName().equals(classB.getName())` và `classA == classB` thế nào?

**Đáp án:** Tên có thể bằng nhau, nhưng `Class<?>` khác nhau nếu defining loader khác nhau.

**Câu 2.** Plugin A và B đều parent-first. Parent không thấy `lib.Versioned`. Mỗi plugin URLClassLoader có version riêng. Có cần custom child-first mới dùng được cả hai version không?

**Đáp án:** Không. Khi parent không có class private, mỗi child tự tìm/định nghĩa phiên bản của mình là đủ.

**Câu 3.** Parent chứa `lib.Versioned` v1, plugin B chứa v2. Điều gì có thể xảy ra?

**Đáp án:** Parent-first có thể chọn v1 cho B, gây behavior sai hoặc `NoSuchMethodError` nếu v2 có method mới. Cần di chuyển private lib khỏi parent hoặc thiết kế selective child-first thích hợp.

**Câu 4.** Vì sao cả hai plugin trong ví dụ đều cast được sang `api.Plugin` của host?

**Đáp án:** Cả hai loader ủy quyền interface `api.Plugin` tới cùng defining loader của shared API; implementation classes khác nhau nhưng contract runtime identity được chia sẻ.

**Câu 5.** Host API khai `lib.Versioned convert(lib.Versioned)` có nguy cơ gì?

**Đáp án:** Private lib type bị rò qua boundary. Host/plugin có thể resolve thành runtime classes khác nhau, gây loading constraint violation hoặc lỗi cast/lookup. Nên dùng shared DTO/interface hoặc adapter.

**Câu 6.** `URLClassLoader.close()` có bảo đảm JVM thu hồi class metadata ngay không?

**Đáp án:** Không. Cần xét object/classloader reachability và GC/unloading; `close()` chủ yếu quản lý tài nguyên của loader.

**Câu 7.** Vì sao không dùng `static final String VERSION = "v2";` làm bằng chứng chắc chắn bản v2 được nạp?

**Đáp án:** Compile-time constants có thể bị inline tại nơi tham chiếu. Gọi instance method `value()` và kiểm tra defining loader rõ hơn.

**Câu 8.** ServiceLoader tìm provider sai plugin trong thread pool. Nên kiểm tra gì?

**Đáp án:** Overload dùng TCCL hay explicit loader, loader thực tế của worker, resource provider file, API type identity và parent visibility.

## 15. Ghi nhớ và tài liệu chính thức

**Tóm tắt ba nguyên tắc:**

1. **Cô lập thứ cần khác:** cho mỗi plugin một defining loader riêng đối với private dependencies có version khác nhau.
2. **Chia sẻ thứ cần tương thích:** để interface và DTO của host/plugin boundary được định nghĩa ở loader chung; tránh để private types đi qua method/field descriptors của shared API.
3. **Kiểm soát đường nạp và lifecycle:** ưu tiên parent-first với parent không chứa private lib; chỉ dùng selective child-first khi cần, truyền loader tường minh khi tìm provider, đóng tài nguyên và dọn references khi plugin dừng.

**Tài liệu tham khảo:**

- [JVMS 17 — Loading, Linking, and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): defining/initiating loaders, runtime class identity, loading constraints và resolution.
- [Java SE 17 — ClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): parent delegation, `loadClass`, `findClass`, `defineClass`.
- [Java SE 17 — URLClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): class/resource lookup qua URLs và `close()`.
- [Java SE 17 — ServiceLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html): provider discovery, explicit loader/TCCL và service configuration.
- [Java SE 17 — Class](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `getName()`, `getClassLoader()` và runtime type metadata.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="loading-policies-006">Ngăn lớp và tài nguyên dùng các thứ tự ưu tiên khác nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Ngăn lớp và tài nguyên dùng các thứ tự ưu tiên khác nhau

Trong Java, một **class loader tùy chỉnh** có thể chọn **lớp (`.class`) từ một nguồn**, nhưng lại trả **tài nguyên (`.properties`, `.xml`, `META-INF/services/...`, template...) từ nguồn khác** nếu chính sách `loadClass` và chính sách `getResource`/`getResources` không thống nhất.

Ví dụ điển hình: ứng dụng dùng **child-first cho `demo.Feature`** để chạy phiên bản plugin, nhưng vẫn giữ `ClassLoader.getResource` mặc định **parent-first**. Kết quả có thể là:

```text
demo.Feature             -> plugin/child (version B)
demo/config.properties   -> application/parent (version A)
```

Code của phiên bản B đọc cấu hình A, dù hai mục tưởng chừng được đóng chung trong plugin. Đây là một loại lỗi **khác nguồn lớp–tài nguyên (class/resource provenance mismatch)**: khó phát hiện vì cả việc nạp lớp và đọc file đều có thể thành công, nhưng chương trình chạy sai logic.

**Mục tiêu của bài:** Thiết kế và kiểm thử một chính sách ưu tiên nhất quán giữa việc nạp lớp, tra một tài nguyên và tra **tất cả** tài nguyên; đồng thời hiểu khi nào cố ý dùng chung tài nguyên của parent thay vì áp dụng child-first cho mọi thứ.

## 1. Lớp và tài nguyên dùng các API khác nhau

Class loader có nhiều đường tìm kiếm độc lập:

| Thao tác | API điển hình | Tên đầu vào | Kết quả |
|---|---|---|---|
| Nạp lớp | `loadClass("demo.Feature")` | **Binary name** với dấu `.` | `Class<?>` |
| Tìm nơi định nghĩa bytecode | `findClass("demo.Feature")` | Binary name | `Class<?>` do loader riêng cung cấp nếu có |
| Tìm một tài nguyên | `getResource("demo/config.properties")` | **Resource name** với dấu `/` | `URL` hoặc `null` |
| Tìm tất cả tài nguyên cùng tên | `getResources("META-INF/services/spi.Plugin")` | Resource name | `Enumeration<URL>` |
| Đọc nội dung | `getResourceAsStream(name)` | Resource name | `InputStream` hoặc `null` |

Tên lớp `demo.Feature` và resource path `demo/Feature.class` **không phải cùng loại đầu vào**. Không truyền `demo/Feature.class` vào `loadClass` và không truyền `demo.Feature` vào `getResource` để kỳ vọng cùng ý nghĩa.

Các phương thức mở rộng thường gặp:

- `loadClass(String, boolean)`: điều phối class loading, cache, delegation và resolve.
- `findClass(String)`: tìm/định nghĩa lớp từ nguồn của loader.
- `getResource(String)`: cung cấp **một** URL tài nguyên.
- `getResources(String)`: cung cấp **các** URL tài nguyên, với thứ tự có thể quan trọng.
- `findResource(String)` và `findResources(String)`: tìm tài nguyên ở phần nguồn riêng của loader.
- `getResourceAsStream(String)`: đọc tài nguyên qua stream; trên `ClassLoader`, implementation mặc định dựa trên đường tìm resource.

**Vấn đề:** Override `loadClass` không tự động thay đổi thứ tự của `getResource` và `getResources`. Override `findClass` cũng không tự tạo quy tắc tìm tài nguyên.

## 2. Chính sách mặc định: parent-first cho cả lớp và tài nguyên

Với triển khai chuẩn của `ClassLoader`, cách hiểu khái quát là:

```text
loadClass("demo.Feature")
    1. findLoadedClass
    2. parent.loadClass hoặc bootstrap
    3. findClass của loader hiện tại khi parent không nạp được

getResource("demo/config.properties")
    1. parent.getResource hoặc bootstrap resource lookup
    2. findResource của loader hiện tại nếu parent không có

getResources("demo/config.properties")
    1. tài nguyên của parent/bootstrap
    2. tài nguyên của loader hiện tại (findResources)
```

Điểm quan trọng: **`getResource` chỉ chọn một URL**, còn **`getResources` trả nhiều URL**. Với loader mặc định, parent được ưu tiên trong cả hai, nhưng API danh sách còn có thể chứa hai bản cùng tên từ các nguồn khác nhau.

Ví dụ parent và child đều cung cấp `demo/config.properties`:

```text
parent/classes/demo/config.properties  -> mode=parent
child/classes/demo/config.properties   -> mode=child

getResource(name)        -> URL của parent
getResources(name)       -> [URL parent, URL child]
```

Đây là thứ tự thông thường khi cả hai nằm trong phạm vi lookup của các loader theo cấu hình trên. Không suy ra rằng **mọi custom loader** hoặc **mọi runtime module/JAR** buộc giữ đúng thứ tự này: implementation và các hạn chế module/resource có thể thay đổi tập kết quả thực tế.

### Vì sao xung đột xuất hiện khi chọn child-first?

Một plugin loader có thể override `loadClass` theo chủ đích:

```text
loadClass("demo.Feature")
    1. child.findClass
    2. parent.loadClass nếu child không có
```

Nhưng nếu không override phương thức resource:

```text
getResource("demo/config.properties")
    1. parent.getResource
    2. child.findResource
```

Khi parent/child cùng có cả class và resource, class sẽ đến từ **child**, resource lại đến từ **parent**. Việc khai báo một chính sách class child-first **không bảo đảm** toàn bộ resources đi kèm cũng child-first.

## 3. Mô hình nguồn gốc phải xét theo namespace và loại tài nguyên

Một chính sách hợp lý thường tách ba phạm vi:

| Phạm vi | Lớp nên lấy từ | Tài nguyên nên lấy từ | Vì sao? |
|---|---|---|---|
| **Lớp nền tảng/JDK** | Bootstrap/platform/parent | Các resource nền tảng theo API thích hợp | Giữ type identity và runtime rules |
| **Shared contracts** như `spi.Plugin` | Parent/shared loader | Shared schemas/config mặc định theo thiết kế chung | Host và plugin phải dùng một runtime interface |
| **Private implementation** như `plugins.alpha.*` | Plugin/child (khi có yêu cầu isolation) | Resource được đóng gói với phiên bản plugin trước, nếu đó là contract | Tránh implementation phiên bản B đọc tài nguyên phiên bản A |

Không phải **tất cả** tài nguyên nằm cùng package đều cần child-first. Ví dụ một cấu hình tổ chức cấp application có thể được thiết kế **chủ ý lấy từ parent** để nhiều plugin cùng dùng. Mục tiêu là **quy tắc được thiết kế rõ và kiểm thử được**, không phải “mọi lookup bắt buộc cùng thứ tự toàn cục”.

Tương tự, `META-INF/services/...` thường là **danh sách đóng góp từ nhiều JAR**, không phải tài nguyên đơn nên chọn duy nhất một bản. Khi xử lý loại này, cần xem `getResources` và contract của `ServiceLoader` thay vì chỉ đổi `getResource`.

## 4. Ví dụ Java 17+ chứng minh lỗi và cách chỉnh chính sách

Ta xây hai bản `demo.Feature` **cùng binary name nhưng nội dung khác nhau**, và hai tệp `demo/label.txt`. App loader thấy bản parent; loader con có directory riêng của child.

Sau đó so sánh hai loader:

- **`ChildFirstClassOnly`**: child-first đúng lớp `demo.Feature` nhưng giữ resource lookup parent-first.
- **`CoherentLoader`**: giữ cùng class policy, đồng thời ưu tiên child cho đúng `demo/label.txt` ở cả `getResource` lẫn `getResources`.

Cấu trúc:

```text
resource-order-demo/
├── src-parent/demo/Feature.java
├── src-child/demo/Feature.java
├── src-app/app/OrderDemo.java
└── out/
    ├── parent/demo/label.txt
    ├── child/demo/label.txt
    └── app/
```

### 4.1. Bản class ở parent

Tệp **`src-parent/demo/Feature.java`**:

```java
package demo;

public final class Feature {
    public static String origin() {
        return "parent-class";
    }
}
```

### 4.2. Bản class ở child

Tệp **`src-child/demo/Feature.java`**:

```java
package demo;

public final class Feature {
    public static String origin() {
        return "child-class";
    }
}
```

Hai class có cùng binary name nhưng **sẽ được định nghĩa bởi loaders khác nhau**, nên là **hai runtime classes khác nhau**. Demo không cố ép hai lớp đó về cùng kiểu; nó dùng reflection để gọi method public trả `String` (kiểu nền tảng chung).

### 4.3. Ứng dụng kiểm tra class và resource

Tệp **`src-app/app/OrderDemo.java`**:

```java
package app;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class OrderDemo {
    private static final String CLASS_NAME = "demo.Feature";
    private static final String RESOURCE_NAME = "demo/label.txt";

    static class ChildFirstClassOnly extends URLClassLoader {
        ChildFirstClassOnly(URL[] urls, ClassLoader parent) {
            super(urls, Objects.requireNonNull(parent));
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!CLASS_NAME.equals(name)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> result = findLoadedClass(name);
                if (result == null) {
                    try {
                        result = findClass(name);
                    } catch (ClassNotFoundException missing) {
                        result = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(result);
                }
                return result;
            }
        }
    }

    static final class CoherentLoader extends ChildFirstClassOnly {
        CoherentLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        public URL getResource(String name) {
            if (RESOURCE_NAME.equals(name)) {
                URL local = findResource(name);
                if (local != null) {
                    return local;
                }
            }
            return super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name)
                throws IOException {
            if (!RESOURCE_NAME.equals(name)) {
                return super.getResources(name);
            }

            Map<String, URL> ordered = new LinkedHashMap<>();
            Enumeration<URL> own = findResources(name);
            while (own.hasMoreElements()) {
                URL next = own.nextElement();
                ordered.putIfAbsent(next.toExternalForm(), next);
            }

            Enumeration<URL> parent = getParent().getResources(name);
            while (parent.hasMoreElements()) {
                URL next = parent.nextElement();
                ordered.putIfAbsent(next.toExternalForm(), next);
            }
            return Collections.enumeration(ordered.values());
        }
    }

    static String classValue(ClassLoader loader) throws Exception {
        Class<?> type = Class.forName(CLASS_NAME, true, loader);
        return (String) type.getMethod("origin").invoke(null);
    }

    static String resourceValue(URL url) throws IOException {
        try (InputStream stream = url.openStream()) {
            return new String(stream.readAllBytes(),
                    StandardCharsets.UTF_8).trim();
        }
    }

    public static void main(String[] args) throws Exception {
        URL childRoot = Path.of(args[0]).toUri().toURL();
        ClassLoader app = OrderDemo.class.getClassLoader();

        try (var naive = new ChildFirstClassOnly(
                    new URL[]{childRoot}, app);
             var aligned = new CoherentLoader(
                    new URL[]{childRoot}, app)) {

            System.out.println("Naive class: " + classValue(naive));
            System.out.println("Naive resource: "
                    + resourceValue(
                            naive.getResource(RESOURCE_NAME)));

            System.out.println("Aligned class: " + classValue(aligned));
            System.out.println("Aligned resource: "
                    + resourceValue(
                            aligned.getResource(RESOURCE_NAME)));

            List<String> items = new ArrayList<>();
            Enumeration<URL> urls =
                    aligned.getResources(RESOURCE_NAME);
            while (urls.hasMoreElements()) {
                items.add(resourceValue(urls.nextElement()));
            }
            System.out.println("Aligned all resources: "
                    + String.join(",", items));
        }
    }
}
```

Đây là chương trình hoàn chỉnh trong Java 17+. Một số chi tiết:

- `URLClassLoader` đã có `findClass`, `findResource`, `findResources` cho URL directories/JARs. Ta chỉ thay thứ tự chọn có chủ đích, không tự viết parser bytecode.
- `ChildFirstClassOnly` child-first **chỉ cho `demo.Feature`**, còn các class khác (đặc biệt Java/JDK classes) giữ parent-first.
- `getClassLoadingLock` và `findLoadedClass` bảo đảm yêu cầu đồng thời/repeat lookup không vô tình tạo class trùng trong một loader.
- `CoherentLoader.getResource` ưu tiên `findResource` của child cho `demo/label.txt` rồi mới fallback.
- `CoherentLoader.getResources` trả URL child trước, URL parent sau, có **khử trùng lặp theo chuỗi `toExternalForm()`** trong phạm vi demo. Đây không phải khẳng định mọi URL biểu diễn cùng một tài nguyên vật lý đều được chuẩn hóa/canonicalize như nhau.
- `Objects.requireNonNull(parent)` khiến `getParent()` luôn khác `null` trong demo. Loader tổng quát cần cân nhắc cả trường hợp bootstrap parent và Java platform resource lookup.
- Khi code gọi `Class.forName(CLASS_NAME, true, loader)`, loader được **truyền tường minh**; vì thế ta kiểm soát việc tìm đúng lớp từ namespace muốn thử.

### 4.4. Biên dịch và chạy

Bash (Linux/macOS/Git Bash), từ thư mục `resource-order-demo`:

```bash
mkdir -p out/parent/demo out/child/demo out/app

printf 'parent-resource\n' > out/parent/demo/label.txt
printf 'child-resource\n' > out/child/demo/label.txt

javac --release 17 -d out/parent src-parent/demo/Feature.java
javac --release 17 -d out/child src-child/demo/Feature.java
javac --release 17 -d out/app src-app/app/OrderDemo.java

java -cp "out/app:out/parent" app.OrderDemo out/child
```

Trên Windows PowerShell, tạo thư mục và resource như sau:

```powershell
New-Item -ItemType Directory -Force -Path out/parent/demo, out/child/demo, out/app | Out-Null
[System.IO.File]::WriteAllText("out/parent/demo/label.txt", "parent-resource" + [Environment]::NewLine)
[System.IO.File]::WriteAllText("out/child/demo/label.txt", "child-resource" + [Environment]::NewLine)
javac --release 17 -d out/parent src-parent/demo/Feature.java
javac --release 17 -d out/child src-child/demo/Feature.java
javac --release 17 -d out/app src-app/app/OrderDemo.java
java -cp "out/app;out/parent" app.OrderDemo out/child
```

Chú ý: Linux/macOS dùng dấu `:` phân cách các phần tử `java -cp`; Windows dùng `;`. Đây là quy tắc command-line classpath, không thay đổi tên resource `demo/label.txt`.

**Đầu ra đã kiểm tra** bằng `javac --release 17` và chạy trên JDK 21:

```text
Naive class: child-class
Naive resource: parent-resource
Aligned class: child-class
Aligned resource: child-resource
Aligned all resources: child-resource,parent-resource
```

### 4.5. Diễn giải kết quả

**Trước khi sửa resource policy:**

```text
ChildFirstClassOnly
  loadClass("demo.Feature")           -> child  -> "child-class"
  getResource("demo/label.txt")       -> parent -> "parent-resource"
```

Code đang chạy class phiên bản child nhưng đọc resource phiên bản parent. Nếu thay `label.txt` bằng template/schema/database mapping của hai phiên bản thư viện, mismatch có thể làm chương trình xử lý sai mà không ném exception.

**Sau khi đồng bộ chính sách:**

```text
CoherentLoader
  loadClass("demo.Feature")           -> child  -> "child-class"
  getResource("demo/label.txt")       -> child  -> "child-resource"
  getResources("demo/label.txt")      -> [child, parent]
```

Đối với tài nguyên được đánh dấu **private/versioned của plugin**, child-first là chính sách mong muốn. Với tài nguyên **shared/global** khác, loader này vẫn dùng chính sách parent-first mặc định.

**Giới hạn thử nghiệm:** Demo minh họa hai directories và một class/một resource để nhìn rõ vấn đề. Hệ thống production cần quy tắc cho **toàn namespace**, dependencies, `META-INF/services`, package/module constraints, URL/JAR provider và vòng đời loader; không nên sao chép điều kiện so sánh đúng một tên vào một hệ thống lớn.

## 5. Tại sao sửa getResource vẫn chưa đủ?

Có ít nhất ba đường đọc phổ biến:

```java
URL one = loader.getResource("demo/label.txt");

Enumeration<URL> all =
        loader.getResources("demo/label.txt");

try (InputStream stream =
        loader.getResourceAsStream("demo/label.txt")) {
    // Đọc byte nếu stream khác null.
}
```

Đây là đoạn mã minh họa đặt trong method với biến `loader` đã khai báo và có xử lý ngoại lệ khi cần.

Nếu bạn chỉ override `getResource` mà không override `getResources`:

- Đọc đơn lẻ có thể thấy child trước.
- API quét tất cả URL vẫn có thể liệt kê **parent trước**.
- Một framework dùng `getResources` có thể chọn cấu hình A thay vì B, tạo kết quả khác với code trực tiếp gọi `getResource`.

Ngược lại, nếu chỉ override `getResources` mà bỏ `getResource`, việc đọc file đơn lẻ có thể vẫn parent-first. Đừng cho rằng một phương thức mặc định luôn được implement bằng phương thức kia.

`getResourceAsStream` trên `ClassLoader` thường mở stream qua `getResource` của loader tương ứng; nếu dùng custom subclass override `getResourceAsStream` thì phải bảo đảm quy tắc mới không đi lệch chính sách đã chọn.

Cần xác minh **API thực tế** framework gọi. Một framework có thể dùng `Class.getResource`, `ClassLoader.getResource`, `Thread.currentThread().getContextClassLoader().getResources` hoặc một custom resource resolver với ordering riêng.

## 6. Cẩn thận: Class.getResource khác ClassLoader.getResource

Hai lệnh sau thường cùng chỉ một resource nhưng diễn giải tên khác:

```java
URL fromClass = demo.Feature.class.getResource("label.txt");

URL fromLoader = demo.Feature.class.getClassLoader()
        .getResource("demo/label.txt");
```

- `Class.getResource("label.txt")` dùng tên tương đối với **package của class**: `demo/label.txt`.
- `Class.getResource("/demo/label.txt")` dùng tên từ gốc namespace (dấu `/` đầu được bỏ khi tra).
- `ClassLoader.getResource("demo/label.txt")` yêu cầu tên resource với `/` phân tách package, **không có dấu `/` đầu**.

Nếu caller `demo.Feature` được định nghĩa ở plugin loader thì `Feature.class.getClassLoader()` trả defining loader đó. Vì thế một resource lookup dựa trên **đúng class liên quan** thường tốt hơn việc tùy tiện dùng `ClassLoader.getSystemResource`.

Tuy nhiên, với **named modules**, resource encapsulation còn tác động đến việc resource có được tìm thấy hay không. Một số resource trong package của module không được lộ ra qua `ClassLoader.getResource` nếu package không mở theo quy tắc API. `Class.getResource` và `Module.getResourceAsStream` cũng có hợp đồng cụ thể. Không kết luận rằng một `null` luôn do **thứ tự ưu tiên**; có thể là vấn đề module access.

### Class resource cũng cần được xét

Một mismatch sâu hơn xuất hiện khi `loadClass("demo.Feature")` định nghĩa class từ child, nhưng `getResource("demo/Feature.class")` trả **bytecode của parent**. Nếu tool inspection, scanner hoặc bytecode transformer dùng resource lookup để lấy class file rồi giả định bytes thuộc runtime class đã load, nó sẽ có thể phân tích nhầm phiên bản.

Không nên coi `getResource("...class")` là bằng chứng tuyệt đối về nguồn bytecode đã dùng: custom loader có thể giải mã, sinh hoặc biến đổi bytes trước `defineClass`; resource URL có thể không phản ánh những bước này. Nếu yêu cầu đồng bộ chặt, thiết kế một **provenance API** riêng và giữ manifest/hash/metadata của bytes mà loader thực sự định nghĩa.

## 7. Không đổi thứ tự của mọi resource một cách mù quáng

Có hai loại tài nguyên cần xử lý khác nhau:

### 7.1. Resource **exclusive** hoặc versioned

Ví dụ:

```text
plugins/acme/schema.json
plugins/acme/templates/view.html
plugins/acme/config/default.properties
```

Nếu những file này gắn trực tiếp với implementation của plugin đang child-first, ưu tiên **child** trước parent có thể phù hợp. Khi resource không có ở child, có thể fallback parent nếu contract cho phép; nếu fallback bị coi là nguy hiểm, **fail fast** để tránh vô tình dùng schema phiên bản khác.

Đây là quyết định chính sách:

| Tài nguyên thiếu ở child | Hành vi có thể chọn | Khi phù hợp |
|---|---|---|
| Có default shared hợp lệ | Fallback parent | Compatibility/default settings đã được thiết kế |
| Bắt buộc đồng phiên bản | Không fallback, báo lỗi | Schema, migration scripts hoặc template bắt buộc khớp implementation |
| Có nhiều lớp override | Dùng ordered overlay | Hệ thống cấu hình có precedence rõ ràng |
| Có nhiều contributors | Trả `getResources` đầy đủ | SPI, plugin registry, extension descriptors |

Đừng dựa vào “tìm thấy cái đầu tiên” nếu contract thực sự đòi **trộn nhiều nguồn**.

### 7.2. Resource **aggregation/multi-source**

Ví dụ:

```text
META-INF/services/com.example.spi.Plugin
META-INF/spring.factories                 (một số hệ sinh thái/phiên bản)
META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

Các file này thường mô tả đóng góp từ **nhiều JAR**. Nếu đổi `getResources` thành chỉ trả **một** resource child, framework có thể mất toàn bộ provider hoặc cấu hình parent.

Đối với metadata aggregation, chính sách hợp lý có thể là:

1. Tìm tất cả resources từ các nguồn được cho phép.
2. Quy định thứ tự nếu framework có contract liên quan.
3. Bảo đảm không làm mất entry hợp lệ.
4. Chỉ khử trùng lặp khi biết định nghĩa trùng lặp của API/frame­work; **URL trùng** không phải lúc nào cũng tương đương **nội dung/provider trùng**.
5. Kiểm thử startup, discovery, cache và reloading với nhiều JAR.

**Đặc biệt với ServiceLoader:** `ServiceLoader.load(service)` theo Java SE 17 dùng TCCL để khám phá providers; `ServiceLoader.load(service, loader)` dùng loader tường minh. Trong chế độ classpath, provider configuration nằm dưới `META-INF/services/<binary-name-of-service>`. Hành vi sai của `getResources` có thể khiến ServiceLoader bỏ sót descriptors hoặc chọn provider không mong muốn. Service type và provider class phải tương thích trong **cùng runtime type namespace**.

## 8. Parent-first cho shared API, child-first cho private implementation

Với plugin:

```text
ApplicationClassLoader
    ├── shared.api.Plugin
    └── shared.api.Message
           ^
           | parent delegation
           |
PluginClassLoader
    ├── plugins.acme.AcmePlugin
    ├── plugins.acme.schema.json
    └── META-INF/services/shared.api.Plugin
```

Một thiết kế điển hình:

- **`shared.api.*`**: luôn ưu tiên parent để app và plugin dùng cùng `Class<?>` của contract.
- **`plugins.acme.*`**: có thể ưu tiên child cho implementation nếu thật sự cần isolation.
- **`plugins/acme/...` resources**: nếu versioned cùng implementation, ưu tiên nguồn plugin hoặc đọc qua class liên quan.
- **`META-INF/services/...`**: có thể phải tổng hợp nhiều descriptors từ parent và child, không đơn giản lấy resource đầu.
- **JDK/platform classes**: không tự định nghĩa lại để tránh phá type identity và các quy tắc bảo vệ của JVM.

Nếu plugin tự định nghĩa lại `shared.api.Plugin` bằng child-first, code ở app không nhất thiết cast được plugin object về interface cùng tên. Khi đó lỗi không còn chỉ là resource mismatch mà là **class identity mismatch**.

### Một bản đồ chính sách nên được viết rõ

Thay vì những câu chung chung “child-first cho tất cả”, có thể xây một ma trận:

| Nhóm | Class lookup | Single resource lookup | Multiple resource lookup |
|---|---|---|---|
| `java.*`, runtime/platform | Parent/bootstrap | Theo platform/module APIs | Theo platform/module APIs |
| `shared.api.*` | Parent-first | Shared resources theo parent | Theo contract shared |
| `plugins.alpha.*` | Child-first (nếu cần) | Child-first với resources tương ứng | Child-first rồi parent, hoặc chỉ child |
| `META-INF/services/*` | Không có class cùng tên | Không phải cơ chế discovery đầy đủ | Tổng hợp entries phù hợp |
| `app/config/global.properties` | Không áp dụng | Parent-first, hoặc nguồn config tường minh | Theo contract application |

Bảng chỉ là **thiết kế mẫu**, không phải các quy tắc JVM tự áp dụng. Những namespace khác cần được phân loại dựa trên nhiệm vụ thực tế.

## 9. Cấu trúc custom loader production an toàn hơn

Có thể tập trung chính sách vào một bộ phân loại theo name (minh họa bằng giả mã):

```text
classPolicy(binaryName):
    if JDK hoặc shared contract:
        return PARENT_FIRST
    if plugin-private package:
        return CHILD_FIRST
    return PARENT_FIRST

resourcePolicy(resourceName):
    if plugin-private resource:
        return CHILD_FIRST
    if service/provider descriptor:
        return AGGREGATE
    return PARENT_FIRST
```

Sau đó ba đường nạp **đều** hỏi bộ phân loại:

```text
loadClass(name):
    quyết định classPolicy(name)
    kiểm tra findLoadedClass, khóa theo name, parent/child
    resolve khi cần

getResource(name):
    quyết định resourcePolicy(name)
    trả URL đầu phù hợp chính sách hoặc null

getResources(name):
    quyết định resourcePolicy(name)
    hợp nhất enumeration phù hợp contract
```

Dù có bộ phân loại chung, **không được ép mọi resource name thành một binary class name**. Class và resource có quy tắc đặt tên khác nhau; một namespace class có thể đi kèm nhiều loại tài nguyên không tương ứng trực tiếp 1:1.

Các điểm phải kiểm tra khi tự cài đặt:

1. `loadClass` vẫn kiểm tra `findLoadedClass` và dùng `getClassLoadingLock(name)` để tránh race/duplicate definitions.
2. Không bắt hoặc nuốt `VerifyError`, `LinkageError`, `ClassFormatError` hay `SecurityException` như thể là `ClassNotFoundException` để lặng lẽ fallback sang bản khác.
3. `getResources` tuân thủ `IOException` và hợp đồng enumeration; không tạo ra danh sách bị cắt nếu thư viện cần toàn bộ descriptors.
4. Giữ thứ tự có tính quyết định trong điều kiện input ổn định, và ghi rõ khi thứ tự bị chi phối bởi classpath order, module/provider semantics hoặc plugin priority.
5. Đóng streams, JAR handles và resource-specific file systems đúng vòng đời.
6. Không cho path/resource names không tin cậy dùng `../` hoặc URL ngoài phạm vi cho phép để thoát nguồn resource của plugin.
7. Không biến class loader thành security sandbox cho plugin không tin cậy: JVM class loading và OS isolation là các vấn đề khác nhau.
8. Thiết kế un/reload mà không giữ references vô hạn qua caches, thread context loader hoặc provider instances.

## 10. TCCL có thể làm đổi nguồn resource ở call site

Một chương trình có thể dùng:

```java
ClassLoader tccl =
        Thread.currentThread().getContextClassLoader();

URL url = tccl == null
        ? null
        : tccl.getResource("plugins/acme/config.properties");
```

Đây là đoạn minh họa trong method có import `java.net.URL`; nếu TCCL là child thì resource lookup có thể theo policy của child. Nhưng:

```java
URL url = SomeLibrary.class.getClassLoader()
        .getResource("plugins/acme/config.properties");
```

dùng **defining loader của `SomeLibrary`**, có thể là parent và có policy khác. Cùng resource name không bảo đảm cùng kết quả nếu lookup bắt đầu từ **hai loaders khác nhau**.

Khi tạm đổi TCCL, luôn khôi phục:

```java
Thread thread = Thread.currentThread();
ClassLoader previous = thread.getContextClassLoader();
try {
    thread.setContextClassLoader(pluginLoader);
    // Gọi framework/SPI có contract sử dụng TCCL.
} finally {
    thread.setContextClassLoader(previous);
}
```

Đây là snippet minh họa với `pluginLoader` có sẵn. Trong thread pools, không khôi phục TCCL có thể khiến request sau đọc resource/plugin provider của request trước; đồng thời TCCL giữ một strong reference tới loader cũ khiến plugin khó được unload.

**Quan trọng:** `Class.forName(String)` **không tự dùng TCCL**; overload một tham số dùng defining loader của calling class. Đừng sửa vấn đề resource bằng cách thay TCCL rồi cho rằng tất cả class lookups tự động chuyển sang child loader.

## 11. Manifest Class-Path, URLClassLoader và JAR resources

Nếu child có URL tới `plugin.jar`, `URLClassLoader` có thể tìm `.class` và resources trong JAR đó. Manifest `Class-Path` của JAR có thể mở rộng các URL phụ thuộc theo quy tắc của JAR specification; điều này làm tập nguồn được tìm thấy rộng hơn nhưng **không tự thay chính sách parent/child** bạn cài đặt.

Ví dụ:

```text
app.jar                       (parent)
  demo/Feature.class
  demo/label.txt

plugin.jar                    (child URLClassLoader)
  demo/Feature.class
  demo/label.txt
```

Nếu class policy child-first mà resource policy parent-first, vẫn gặp mismatch như demo dù nguồn là JAR thay vì directory.

Tài nguyên JAR thường có URL dạng:

```text
jar:file:/opt/plugins/plugin.jar!/demo/label.txt
```

URL này chỉ **entry trong archive**, không phải một `java.io.File` độc lập. Dùng `URL.openStream()`/`getResourceAsStream` để đọc thay vì `new File(url.toURI())`. Tránh parse bằng `substring` quanh `!/` nếu loader/framework có thể trả nested JAR URLs hoặc protocol đặc biệt.

**Classpath order không phải cùng khái niệm với loader delegation order.** Một URLClassLoader có thể có nhiều JAR theo thứ tự URLs, nhưng trước khi kiểm tra những URLs đó, `getResource` mặc định vẫn có thể nhờ parent tìm trước. Điều chỉnh thứ tự của `URL[]` ở child chưa đủ để child thắng resource parent khi parent có cùng tên.

## 12. Module system và những giới hạn của đồng bộ thứ tự

Trên Java 9+, **JPMS named modules** có module readability, exports/opens và chính sách resource encapsulation. Vì vậy:

- Một class có thể tồn tại trong module nhưng không thể được dùng ở nơi gọi do module/access rules.
- `ClassLoader.getResource` có thể không lộ non-`.class` resources thuộc package trong named module khi package chưa được `opens` theo quy tắc API.
- `Class.getResource`, `Module.getResourceAsStream` và `ClassLoader.getResource` có khác biệt hợp đồng; một URL `null` không đủ để kết luận chỉ do priority.
- Service discovery của named modules có thể dựa vào `provides ... with ...` và module layers, không nhất thiết vào `META-INF/services`.
- Custom loader không tự giải quyết module readability/exports/opens nếu code đã vi phạm các ràng buộc đó.

Khi hệ thống dùng JPMS, hãy quyết định liệu lớp và resource nên được truy cập **theo module ownership** thay vì giả định một parent/child URL search chain đơn giản. Với code gắn chặt với một module, `Module.getResourceAsStream` có thể là lựa chọn phù hợp hơn nếu access contract cho phép.

## 13. Kiểm thử để phát hiện mismatch trước production

### Kiểm thử phải kiểm tra **cả giá trị và nguồn**

Chỉ kiểm tra:

```java
assertNotNull(loader.getResource("demo/label.txt"));
```

là chưa đủ. Parent có thể trả một resource trùng tên, khiến test pass trong khi dùng sai phiên bản.

Hãy kiểm tra:

- `loadedClass.getClassLoader()` là defining loader mong đợi hay không.
- Bytecode/class behavior thuộc đúng phiên bản (ví dụ method trả `child-class`).
- URL resource đến từ archive/directory/module dự kiến.
- **Nội dung** resource có version/marker đồng bộ với implementation.
- `getResource` chọn đúng một nguồn; `getResources` có thứ tự và tập URL đúng theo contract.
- Caller dùng `Class.getResource`, loader tường minh hay TCCL thực tế.
- Trường hợp không có child resource có được fallback parent hay phải fail fast.
- Khi có hai JAR trùng tên resource hoặc provider descriptor, có bao nhiêu bản được xử lý?

**Lưu ý:** Việc suy ra class origin từ `getResource("...class")` chỉ là heuristic. Trong custom loader có transformation hoặc bytecode từ bộ nhớ, nguồn `.class` mà `getResource` trả có thể không phải bytes đã `defineClass`. Nếu cần bằng chứng mạnh, lưu provenance/hash tại thời điểm định nghĩa class.

### Ma trận test hữu ích

| Class từ parent? | Class từ child? | Resource parent? | Resource child? | Kỳ vọng |
|---|---|---|---|---|
| Có | Có | Có | Có | Child-class + child-resource với policy child-first private |
| Có | Không | Có | Không | Fallback parent nếu được phép |
| Không | Có | Không | Có | Child cung cấp cả hai |
| Có | Có | Có | Không | Fail-fast hoặc fallback theo contract, không im lặng |
| Có | Có | Không | Có | Child resource; class theo policy định sẵn |
| Nhiều provider JAR | Nhiều provider classes | Nhiều descriptors | Nhiều descriptors | `getResources`/SPI không bỏ sót contributions hợp lệ |

Đừng chỉ test trường hợp child và parent không trùng tên — khi đó hai chính sách khác nhau có thể trông giống nhau. **Phải tạo một fixture cố ý trùng binary name và resource name** như demo ở phần 4.

## 14. Dấu hiệu trong ứng dụng thực tế

### A. Class của plugin B nhưng cấu hình phiên bản A

Triệu chứng: tính năng plugin mới đã được nạp nhưng ứng dụng dùng một schema cũ, một enum mapping cũ hoặc template lỗi thời.

Kiểm tra: `Class.getClassLoader`, nơi class được định nghĩa, `getResource` URL, nội dung resource và thứ tự `getResources`. Nếu dùng child-first class mà parent-first resource, sửa chính sách hoặc dùng API đọc resource gắn với class tương ứng.

### B. SPI provider không được phát hiện dù class tồn tại

Triệu chứng: `Class.forName("plugins.X", false, pluginLoader)` hoạt động, nhưng `ServiceLoader.load(Service.class, pluginLoader)` không tìm được provider.

Kiểm tra: tệp `META-INF/services/<service-binary-name>` có được đóng gói, `getResources` có trả đủ descriptors, provider type có dùng **đúng shared service interface**, và module/TCCL context có phù hợp không.

### C. Framework lấy cấu hình khác với chương trình tự đọc

Triệu chứng: `loader.getResource(name)` trả child URL, nhưng framework vẫn đọc parent resource.

Kiểm tra: framework có dùng `getResources` không? Có dùng system loader, TCCL, `Class.getResource` hay module resource API khác không? Có cache resource list hoặc config tại lần khởi động trước không?

### D. “Chạy IDE đúng, đóng JAR sai”

IDE có thể đưa thư mục resources và classes vào classpath theo một thứ tự khác với executable JAR/container. Khi JAR deployment thêm parent libraries hoặc plugin loader, tên tài nguyên trùng có thể bắt đầu gây shadowing.

Kiểm tra build output/JAR entries bằng `jar --list --file ...` và đối chiếu loader/url thực tế khi chạy, không chỉ dựa trên source directory.

### E. Hot reload vẫn dùng resource cũ

Nếu loader thế hệ mới tải class B nhưng một singleton ở parent đã cache URL/resource A, đổi loader không tự vô hiệu hóa cache đó. Cần thiết kế cache key/lifecycle theo plugin generation hoặc loại bỏ cache cũ.

## 15. Các lỗi thiết kế cần tránh

1. **Override `loadClass` child-first cho mọi lớp** mà không giữ shared APIs ở parent: dẫn tới trùng class identity và `ClassCastException`.
2. **Override chỉ `getResource`**, quên `getResources`: framework aggregation vẫn dùng thứ tự cũ.
3. **Override `getResources` chỉ trả một URL đầu tiên**, làm mất service descriptors và plugin contributions.
4. **Đồng bộ bằng cách đổi tất cả resources sang child-first** bất chấp config shared/global hoặc module rules.
5. **Dùng `getResource("...class")` làm bằng chứng tuyệt đối về bytes đã load** dù code dùng transformations/custom sources.
6. **Bắt và che `LinkageError`, `VerifyError` như thể chỉ là tài nguyên không thấy**; cần phân biệt lỗi class loading và mismatch resource.
7. **Nhầm resource name với class name**; `getResource("a.B")` và `loadClass("a/B.class")` không phải hai cách gọi tương đương.
8. **Đọc JAR resource bằng `new File(url.toURI())`**; URL `jar:` không phải filesystem file.
9. **Quên kiểm tra `null`/`IOException`**, dựa vào giả định mọi resource hiện diện.
10. **Quên reset TCCL hoặc giải phóng URLClassLoader/caches** sau plugin lifecycle.
11. **Không kiểm thử trường hợp parent và child cùng có tên**, nên bỏ sót mismatch cho đến deployment thực.
12. **Giả định thay classpath order đủ sửa policy**, khi bản thân parent delegation đã quyết định ưu tiên trước classpath URLs của child.

## 16. Câu hỏi tự kiểm tra

**1. Child override `loadClass` thành child-first. `getResource` có tự child-first không?** Không. `getResource` là đường lookup khác và mặc định của `ClassLoader` thường ưu tiên parent.

**2. Vì sao class child và resource parent có thể cùng được tìm thấy mà ứng dụng vẫn sai?** Hai phiên bản không nhất quán; class B có thể đọc schema/config/template của A.

**3. Tại sao không chỉ sửa `getResource`?** Vì một số framework/API dùng `getResources` để lấy nhiều resources. Không chỉnh phương thức đó thì thứ tự/tập kết quả có thể vẫn khác.

**4. Trong ví dụ, vì sao `CoherentLoader.getResources` trả child rồi parent?** Resource `demo/label.txt` được phân loại private/versioned theo chính sách demo; child được ưu tiên nhưng ta vẫn cho phép liệt kê bản parent.

**5. Có nên trả child-only cho mọi `META-INF/services/*`?** Không. SPI thường cần phát hiện providers từ nhiều nguồn; phải giữ đúng aggregation contract.

**6. Nếu plugin dùng interface cùng tên nhưng do loader khác định nghĩa, thứ tự resource đúng đã đủ chưa?** Chưa. Shared interface cần cùng runtime identity; class identity phụ thuộc defining loader.

**7. `Class.getResource("label.txt")` và `ClassLoader.getResource("label.txt")` có nhất thiết tìm cùng entry không?** Không. API `Class` giải quyết tên tương đối với package; `ClassLoader` dùng resource name từ gốc loader namespace.

**8. Nếu resource lookup trả `null` trong named module, có chắc priority sai không?** Không. Module encapsulation/opens cũng có thể ngăn resource hiển thị.

**9. Đổi TCCL có tự làm `Class.forName(String)` dùng loader mới không?** Không. `Class.forName(String)` dùng defining loader của caller; chỉ những API có chủ đích đọc TCCL mới bị ảnh hưởng.

**10. Kiểm thử nào bắt lỗi nhanh nhất?** Tạo parent và child có **trùng class name lẫn resource name** nhưng giá trị khác, rồi assert class behavior, resource content và thứ tự `getResources`.

## 17. Tài liệu chuẩn

- Java SE 17 API, `ClassLoader`: `loadClass`, `getResource`, `getResources`, `findResource`, `findResources` và resource-stream methods: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17 API, `URLClassLoader`: class/resource lookup từ URLs và `close()`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html
- Java SE 17 API, `Class.getResource` và `Class.getClassLoader`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- Java SE 17 API, `ServiceLoader`: provider discovery từ resource descriptors/modules: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html
- JVMS §5.3, *Creation of Classes and Interfaces*, về defining/initiating loaders và runtime class identity: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3

**Ghi nhớ:** Một plugin có thể chạy **class từ child** mà lại đọc **resource từ parent** nếu chỉ thay `loadClass`. Muốn tránh lệch phiên bản, hãy xây **ma trận chính sách theo namespace và loại tài nguyên**, đồng bộ `getResource` với `getResources` khi cần, bảo vệ shared API, và kiểm thử bằng các lớp/tài nguyên trùng tên ở hai nguồn. Với service descriptors hay tài nguyên tổng hợp, “nhất quán” thường có nghĩa **tổng hợp đúng** chứ không phải luôn chỉ lấy child.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="loading-policies-007">Rủi ro nạp trùng API, annotation hoặc kiểu exception</a>

<details>
<summary>Xem chi tiết</summary>

# Rủi ro nạp trùng API, annotation hoặc kiểu exception trong ClassLoader Java

## 1. Khái niệm cốt lõi: tên giống nhau chưa đủ để thành cùng kiểu

Trong JVM, danh tính runtime của một class/interface thông thường dựa trên **cặp (binary name, defining class loader)**. Vì vậy nếu hai loader thực sự tự định nghĩa lớp cùng tên `shared.Api` từ cùng bytecode, JVM vẫn có thể coi chúng là **hai kiểu khác nhau**.

~~~text
HostLoader    defines shared.Api -> Class A
PluginLoader  defines shared.Api -> Class B

A.getName().equals(B.getName()) = true
A == B                          = false
~~~

Rủi ro này xuất hiện khi host và plugin đóng gói bản sao của cùng API JAR, hoặc container sử dụng class loader cô lập với chính sách child-first quá rộng. Ba nhóm dễ bị ảnh hưởng nhất:

- **Interface/API:** Plugin tuy khai báo `implements shared.Api` nhưng thực hiện với bản API của loader plugin, không phải bản host dùng. Cast hoặc `isAssignableFrom` có thể thất bại.
- **Annotation:** Class được gắn annotation `shared.Marker` của plugin nhưng host kiểm tra bằng `Marker.class` của host. Phép `isAnnotationPresent` có thể trả false dù tên annotation giống nhau.
- **Exception:** Plugin ném `shared.Problem` thuộc namespace của plugin; `catch (shared.Problem e)` của host dùng một runtime Class khác nên không bắt trúng kiểu ấy.

Các hệ quả khác bao gồm `ClassCastException`, `ServiceConfigurationError` (provider không phải subtype), `LinkageError` vì loading constraints, static state bị nhân đôi, lỗi callback/DTO, và metadata framework không được nhận dạng.

**Không kết luận hai kiểu khác nhau chỉ vì có hai đối tượng ClassLoader.** Nếu cả hai loader ủy quyền tìm `shared.Api` cho cùng parent và parent định nghĩa nó, chúng có thể nhận về **chính cùng một Class runtime**. Điều cần xem là **defining loader** của Class kết quả.

## 2. API/interface bị nhân đôi gây lỗi như thế nào?

Host có một interface:

~~~java
package shared;

public interface Api {
    String message();
}
~~~

Plugin:

~~~java
package plugin;

import shared.Api;

public final class PluginImpl implements Api {
    @Override
    public String message() {
        return "hello";
    }
}
~~~

Đây là hai đoạn minh họa; bài thực hành ở phần sau cung cấp toàn bộ file Java.

Hai mô hình:

~~~text
ĐÚNG: chia sẻ API
HostLoader -- defines shared.Api
      ^
      | delegation shared.*
PluginLoader -- defines plugin.PluginImpl
      implements shared.Api (của parent)

SAI: nạp trùng API
HostLoader -- defines shared.Api [H]
PluginLoader -- defines shared.Api [P]
PluginLoader -- defines plugin.PluginImpl
      implements shared.Api [P] != [H]
~~~

Trong mô hình sai, một object được tạo từ `plugin.PluginImpl` không nhất thiết cast được sang `shared.Api` mà host đã resolve. `Api.class.getName().equals(pluginApi.getName())` vẫn true, nhưng `Api.class == pluginApi` false.

Một thông báo dạng “`shared.Api cannot be cast to shared.Api`” không phải mâu thuẫn; nó đang nói tới hai kiểu cùng binary name do hai defining loader khác nhau định nghĩa.

## 3. Annotation bị nhân đôi khiến reflection hoặc framework bỏ sót

Annotation cũng là một kiểu Java runtime. Ví dụ:

~~~java
package shared;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface Marker {}
~~~

Nếu plugin gắn `@Marker` nhưng chính annotation Class ấy do plugin loader định nghĩa, host gọi:

~~~java
boolean found = pluginType.isAnnotationPresent(Marker.class);
~~~

với `Marker.class` từ host sẽ không tự động match bản Marker của plugin. `found` có thể là false ngay cả khi `pluginType.getDeclaredAnnotations()` cho thấy annotation có tên `shared.Marker`.

Cần phân biệt **ba lỗi khác nhau**:

1. Không khai báo `RetentionPolicy.RUNTIME`: annotation có thể không hiện trong Java runtime reflection.
2. Annotation runtime có tồn tại, nhưng **Class identity không trùng**: host dùng Class của loader khác.
3. Framework scan nhầm scope, module package chưa `opens` khi cần deep reflection, hoặc đang scan proxy khác class thực tế.

Lỗi annotation có thể làm host bỏ qua controller/route, validator, transaction marker, dependency injection component hoặc authorization metadata. Với annotation mang ý nghĩa chính sách, hậu quả không chỉ là một phép kiểm tra trả false.

**Chính sách đúng:** những annotation mà host/framework cần đọc từ plugin phải được định nghĩa trong **shared contract loader**. Đừng chữa lỗi bằng cách so chuỗi tên annotation rồi chấp nhận mọi type cùng tên.

## 4. Exception bị nhân đôi khiến catch không trúng

Nếu host và plugin đều định nghĩa `shared.Problem extends Exception` dưới hai loader khác nhau, chúng vẫn là hai exception types độc lập. Một typed handler như:

~~~java
try {
    pluginCall();
} catch (Problem e) {
    handle(e);
}
~~~

là **đoạn minh họa** có biến, phương thức và kiểu `Problem` được khai báo thích hợp. Nó dựa trên type compatibility runtime, không dựa vào `getClass().getName()`. Object do `PluginProblem` tạo có thể thoát khỏi `catch (HostProblem)` dù tên nhị phân giống nhau.

Khi host gọi plugin qua reflection, cần nhớ Java reflection **bọc exception do method ném**:

~~~java
try {
    method.invoke(null);
} catch (java.lang.reflect.InvocationTargetException wrapper) {
    Throwable cause = wrapper.getCause();
    // Xem defining loader và Class của cause thay vì wrapper.
}
~~~

Đây là **mã minh họa**, không phải chương trình có main. `wrapper` là `InvocationTargetException`, còn `cause` có thể là bản `shared.Problem` do plugin loader định nghĩa.

Giải pháp: cho exception types trong hợp đồng host-plugin nằm ở shared parent loader; hoặc dùng adapter để chuyển exception riêng của plugin thành **shared exception type** hay error DTO trước khi trả qua boundary. Không bắt mọi `Throwable` rồi xóa toàn bộ root cause.

## 5. Quy tắc định danh kiểu và những API kiểm tra hữu ích

| Kiểm tra | Trả lời câu hỏi |
| --- | --- |
| `a.getName().equals(b.getName())` | Binary names giống nhau không? |
| `a == b` với `Class<?>` | Có là cùng runtime Class không? |
| `a.getClassLoader()` | Defining loader của a là ai? |
| `a.isAssignableFrom(b)` | Đối tượng kiểu b có thể gán cho a theo quy tắc kiểu không? |
| `a.isInstance(object)` | Object có tương thích với a không? |
| `annotation.annotationType()` | Annotation instance thuộc đúng runtime annotation Class nào? |
| `cause instanceof HostProblem` | Exception thực sự tương thích với kiểu host không? |
| `clazz.getModule()` | Runtime module của class (Java 9+) là gì? |

Đừng chỉ so `toString()` của Class hoặc loader, vì các chuỗi mô tả không phải cơ chế kiểm tra type identity chuẩn. Khi in loader của class lõi như `java.lang.String`, `getClassLoader()` có thể là `null`, đại diện bootstrap loader.

## 6. Vì sao duplicate contract lọt qua loader policy?
Với `shared.Api`, `shared.Marker` và `shared.Problem`, cùng một lỗi thiết kế là để child định nghĩa lại **contract của host**. Parent-first chỉ giúp khi parent thực sự có các class ấy; child-first có chọn lọc phải loại trừ contract package.

| Nhóm | Kiểm tra bắt buộc |
| --- | --- |
| Service/API | `HostApi.class.isAssignableFrom(pluginImpl)` |
| Annotation host đọc | `impl.isAnnotationPresent(HostMarker.class)` và retention `RUNTIME` |
| Exception host bắt | Class của nguyên nhân gốc có assignable vào `HostProblem.class` |
| Implementation/private dependency | Nguồn class và defining loader đúng plugin mong đợi |

Các phép kiểm tra trên trả lời **những câu hỏi khác nhau**; không thể chỉ kiểm tra `getName()` một lần rồi coi toàn bộ contract đã đúng. Bài thực hành tiếp theo cố ý nạp trùng cả ba loại để quan sát từng lỗi.

## 7. Thí nghiệm Java 17+: nạp trùng rồi sửa cùng một plugin

Ví dụ sau cố tình compile `shared.*` vào **cả host-classes và plugin-classes**. Host sẽ chạy hai cấu hình:

- `isolated`: child-first cho `plugin.*` và `shared.*`, làm trùng interface, annotation, exception.
- `shared`: child-first chỉ cho `plugin.*`; còn `shared.*` ủy quyền parent, làm contract thống nhất.

### 7.1. Cấu trúc file

~~~text
duplicate-types-demo/
├── src-shared/
│   └── shared/
│       ├── Api.java
│       ├── Marker.java
│       └── Problem.java
├── src-plugin/
│   └── plugin/
│       └── PluginImpl.java
└── src-host/
    └── HostMain.java
~~~

### 7.2. src-shared/shared/Api.java

~~~java
package shared;

public interface Api {
    String message();
}
~~~

### 7.3. src-shared/shared/Marker.java

~~~java
package shared;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Marker {
}
~~~

`@Retention(RUNTIME)` đảm bảo annotation có thể được reflection quan sát. `@Target(TYPE)` ghi rõ nó được dùng cho class/interface declarations.

### 7.4. src-shared/shared/Problem.java

~~~java
package shared;

public class Problem extends Exception {
    public Problem(String message) {
        super(message);
    }
}
~~~

### 7.5. src-plugin/plugin/PluginImpl.java

~~~java
package plugin;

import shared.Api;
import shared.Marker;
import shared.Problem;

@Marker
public final class PluginImpl implements Api {
    @Override
    public String message() {
        return "hello";
    }

    public static void fail() throws Problem {
        throw new Problem("plugin problem");
    }
}
~~~

Plugin implementation implement API, có annotation runtime và method ném checked exception.

### 7.6. src-host/HostMain.java

~~~java
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;

import shared.Api;
import shared.Marker;
import shared.Problem;

public class HostMain {
    static final class PluginLoader extends ClassLoader {
        private final Path root;
        private final boolean duplicateShared;

        PluginLoader(Path root, boolean duplicateShared) {
            super(HostMain.class.getClassLoader());
            this.root = root;
            this.duplicateShared = duplicateShared;
        }

        private boolean local(String name) {
            return name.startsWith("plugin.")
                    || (duplicateShared && name.startsWith("shared."));
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!local(name)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> result = findLoadedClass(name);
                if (result == null) {
                    result = findClass(name);
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

    static void demonstrate(String label, Path root,
                            boolean duplicateShared) throws Exception {
        PluginLoader loader = new PluginLoader(root, duplicateShared);
        Class<?> impl = loader.loadClass("plugin.PluginImpl");
        Object object = impl.getConstructor().newInstance();

        Class<?> pluginApi = loader.loadClass("shared.Api");
        Class<?> pluginMarkerType = loader.loadClass("shared.Marker");
        Class<?> pluginProblem = loader.loadClass("shared.Problem");
        Class<? extends Annotation> pluginMarker =
                pluginMarkerType.asSubclass(Annotation.class);

        System.out.println(label + " API name equal: "
                + Api.class.getName().equals(pluginApi.getName()));
        System.out.println(label + " API same Class: "
                + (Api.class == pluginApi));
        System.out.println(label + " host Api instance: "
                + (object instanceof Api));
        System.out.println(label + " host Marker present: "
                + impl.isAnnotationPresent(Marker.class));
        System.out.println(label + " plugin Marker present: "
                + impl.isAnnotationPresent(pluginMarker));

        try {
            impl.getMethod("fail").invoke(null);
        } catch (InvocationTargetException wrapper) {
            Throwable cause = wrapper.getCause();
            System.out.println(label + " exception same name: "
                    + cause.getClass().getName()
                           .equals(Problem.class.getName()));
            System.out.println(label + " exception same Class: "
                    + (cause.getClass() == Problem.class));
            System.out.println(label + " exception is host Problem: "
                    + (cause instanceof Problem));
            System.out.println(label + " exception is plugin Problem: "
                    + pluginProblem.isInstance(cause));
        }

        try {
            Api api = (Api) object;
            System.out.println(label + " message: " + api.message());
        } catch (ClassCastException wrongApi) {
            System.out.println(label + " cast: ClassCastException");
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: HostMain <plugin-classes-directory>");
        }
        Path root = Path.of(args[0]);
        demonstrate("isolated", root, true);
        demonstrate("shared", root, false);
    }
}
~~~

Vì `HostMain` không import hay tham chiếu trực tiếp `plugin.PluginImpl` khi compile, loader parent không tự được cung cấp plugin implementation nếu classpath chỉ chứa `host-classes`.

`PluginLoader` cho tên `plugin.*` đi local-first trong cả hai trường hợp; với `duplicateShared=true` mới cho `shared.*` đi local-first. Những tên khác (như `java.lang.String`) vẫn đi qua parent. Trong loader, `getClassLoadingLock` và `findLoadedClass` ngăn định nghĩa trùng tên khi có nhiều lời gọi.

**Giới hạn của demo:** `findClass` đọc classfile từ thư mục đã chuẩn bị sẵn mà không kiểm tra symlink, file size, allowlist hay thư mục tin cậy. Chỉ dùng nó để quan sát type identity trong môi trường học tập, **không dùng nguyên trạng làm plugin loader cho code không tin cậy**.

### 7.7. Biên dịch và chạy

Tại thư mục `duplicate-types-demo`, dùng JDK 17+:

~~~bash
javac --release 17 -d host-classes src-shared/shared/Api.java src-shared/shared/Marker.java src-shared/shared/Problem.java src-host/HostMain.java
javac --release 17 -d plugin-classes src-shared/shared/Api.java src-shared/shared/Marker.java src-shared/shared/Problem.java src-plugin/plugin/PluginImpl.java
java -cp host-classes HostMain plugin-classes
~~~

**Stdout dự kiến** khi chạy đúng file và lệnh:

~~~text
isolated API name equal: true
isolated API same Class: false
isolated host Api instance: false
isolated host Marker present: false
isolated plugin Marker present: true
isolated exception same name: true
isolated exception same Class: false
isolated exception is host Problem: false
isolated exception is plugin Problem: true
isolated cast: ClassCastException
shared API name equal: true
shared API same Class: true
shared host Api instance: true
shared host Marker present: true
shared plugin Marker present: true
shared exception same name: true
shared exception same Class: true
shared exception is host Problem: true
shared exception is plugin Problem: true
shared message: hello
~~~

### 7.8. Vì sao chỉ đổi policy đã sửa được?

Trong cấu hình `isolated`, loader plugin tự define `Api`, `Marker` và `Problem`. Tên và bytes có thể giống host nhưng Class runtime khác. Plugin thực thi theo các phiên bản của nó; host cast và annotation lookup dùng phiên bản host, nên thất bại.

Trong cấu hình `shared`, package `shared.*` được ủy quyền cho loader host. Plugin implementation được định nghĩa riêng, nhưng các shared contract types trong bytecode được resolve tới Class của parent. Do đó cast sang host `Api` được; host đọc annotation `Marker` được; và exception `Problem` là đúng runtime type host chờ đợi.

Reflection ở ví dụ này không bỏ qua type identity. Nó chỉ cho phép host quan sát Class/annotation/exception của plugin khi chưa thể cast object sang interface host.

## 8. Exception matching và reflection wrappers

Một điều dễ bị che khuất: method plugin ném `Problem`, nhưng `Method.invoke` ném ra `InvocationTargetException` cho caller. Vì vậy muốn biết **kiểu exception thật** cần lấy `getCause()`.

Nếu host code trong đường gọi trực tiếp có typed catch `catch (Problem e)`, JVM sẽ kiểm tra kiểu tương thích với **host Problem** đã liên kết. Một object của **plugin Problem** khác defining loader không tự trở thành host Problem chỉ vì có cùng tên.

Trong kiến trúc plugin, nên phân loại rõ:

- **Contract exception:** một phần API chung và được parent loader định nghĩa.
- **Plugin-private exception:** chỉ tồn tại trong plugin và nên được adapter chuyển sang contract exception hoặc error DTO.
- **Framework wrapper:** `InvocationTargetException`, `ExecutionException`, `CompletionException` v.v.; cần unwrap theo hợp đồng API tương ứng.
- **VM/linkage errors:** không tự động chuyển tất cả thành `ClassNotFoundException` hay domain error, vì làm mất root cause.

Trong log, in cả `exception.getClass()` và `exception.getClass().getClassLoader()` để phân biệt hai exception cùng tên.

## 9. Annotation scanning và ServiceLoader bị ảnh hưởng

Các framework (DI, web routing, ORM, validation, AOP, testing) có thể tìm annotation bằng `getAnnotation`, `getAnnotationsByType` hoặc `isAnnotationPresent`. Nếu annotation type nhân đôi, host scanner có thể không nhận dạng marker, dù plugin metadata có annotation cùng tên.

Đừng chẩn đoán nhầm thành “quên `@Retention(RUNTIME)`” mà không in `annotation.annotationType()` và defining loader.

`ServiceLoader` hoạt động trên **đúng service type Class**. Provider implement bản interface từ plugin loader có thể không là subtype của service interface host sử dụng, dẫn đến `ServiceConfigurationError`, ví dụ provider không phải subtype. Thay đổi Thread Context ClassLoader có thể ảnh hưởng nơi tìm provider, **không tự hợp nhất hai runtime types** đã được định nghĩa.

Cách sửa là để provider và consumer cùng tham chiếu một **shared service API Class**; khi dùng named modules, kiểm tra thêm `uses`, `provides...with` và khả năng đọc module.

## 10. Loading constraints và lỗi không chỉ xảy ra lúc cast

JVM áp đặt **loading constraints** nhằm đảm bảo những reference types được sử dụng qua method descriptors, inheritance và những điểm liên kết khác có ngữ nghĩa kiểu nhất quán.

Hai Class cùng tên do hai loader định nghĩa **có thể** cùng tồn tại độc lập. Nhưng nếu JVM cần cùng một kiểu `shared.Dto` trong một quan hệ override/interface hoặc descriptor giao tiếp mà hai loader lại resolve thành Class khác nhau, một **`LinkageError` vì loader constraint violation** có thể xuất hiện ở giai đoạn liên kết/resolve, trước khi ứng dụng tới phép cast.

Phân biệt:

| Lỗi | Dấu hiệu |
| --- | --- |
| `ClassCastException` | Object không tương thích với Class của phép cast |
| `LinkageError` / loading constraint | JVM không thể bảo đảm tính nhất quán kiểu khi liên kết những class liên quan |
| `ServiceConfigurationError` | Provider không tương thích hoặc cấu hình service sai |
| `InvocationTargetException` | Reflection wrapper của exception do method ném |
| `NoSuchMethodError` / `NoSuchFieldError` | Symbol/version mismatch, không nhất thiết chỉ do loader duplication |
| `ClassNotFoundException` | Loader không tìm thấy class theo tên trong phạm vi tìm kiếm |

Không nói “nhân đôi API thì luôn có LinkageError”, cũng không nói “mọi vấn đề chỉ là ClassCastException”: loại lỗi phụ thuộc cách những class được sử dụng.

## 11. Phân biệt API contract và dependency private

Tập **shared contract** thường gồm:

- Service interfaces, abstract base types và những superinterfaces chung.
- Parameter/return types xuất hiện ở method signatures, DTO/record/enum dùng xuyên ranh giới.
- Checked/unchecked exception types mà host xử lý theo Class.
- Annotation types mà host cần scan.
- Listener/callback types và service provider contracts.

Plugin có thể có private implementation classes và những thư viện phiên bản riêng **không lộ ra ở boundary**. Nếu một thư viện private xuất hiện trong public API, ranh giới không còn tách biệt sạch; cần xây adapter hoặc chuẩn hóa contract.

Ví dụ nếu plugin dùng phiên bản JSON library khác host, nên chuyển qua DTO chung hoặc chuỗi/byte với schema rõ, thay vì trả một object parser implementation từ plugin rồi cast sang implementation phiên bản host.

## 12. Đóng gói và build configuration

Nguyên nhân duplication thường nằm ở artifact hơn là chính Java source:

- Plugin fat JAR/uber JAR có bản copy `shared/Api.class` và child-first loader định nghĩa bản ấy.
- Container cung cấp API sẵn nhưng plugin vẫn đóng kèm bản API.
- Maven/Gradle dependency scopes khiến API được ship hai lần khi deployment.
- Shading/relocation thay package names của một dependency được host dự kiến chia sẻ.
- App server isolation, test class loader hoặc hot reload tạo loader mới với bản contract riêng.

Trong các hệ thống host cung cấp contract JAR ở runtime, plugin thường compile against API nhưng không tự đóng gói bản duplicate; Maven `provided` hoặc Gradle `compileOnly` **có thể** phù hợp tùy môi trường build/deploy. Phải kiểm tra artifact và class loader runtime thực tế.

Có thể kiểm tra entries:

~~~bash
jar --list --file plugin.jar
~~~

Khi `plugin.jar` tồn tại, tìm `shared/Api.class`, `shared/Marker.class`, `shared/Problem.class`. **Có file trùng chưa chứng minh Class bị define trùng**, vì parent-first có thể vẫn dùng bản của parent. Muốn kết luận, so `Class<?>` và defining loader runtime.

## 13. Debug có hệ thống

Một helper đơn giản:

~~~java
static void describe(Class<?> type) {
    System.out.println(type.getName());
    System.out.println(type.getClassLoader());
    System.out.println(type.getModule());
}
~~~

Đây là mã minh họa đặt trong class bất kỳ, không phải file chạy độc lập.

Quy trình khi thấy “X cannot be cast to X”, annotation biến mất hoặc exception handler không chạy:

1. Lấy **hai Class cụ thể** đang đối chiếu, ví dụ `Api.class` của host và `pluginLoader.loadClass("shared.Api")`.
2. In tên, defining loader, module; so `Class<?>` bằng `==`.
3. Kiểm tra parent chain và chính sách parent-first/child-first theo **từng package**.
4. Kiểm tra các artifact có bản sao `shared.*` và deployment runtime có đúng loại loader không.
5. Với annotation, xác nhận `RetentionPolicy.RUNTIME`, kiểm tra `annotation.annotationType()` và loader.
6. Với exception, unwrap wrapper, in `cause.getClass()` và loader, so `instanceof HostProblem`.
7. Với ServiceLoader, kiểm tra Class của service, Class provider và `isAssignableFrom`.
8. Với LinkageError, kiểm tra method descriptors, những kiểu chung trong signatures, versions và loader constraints.
9. Chuyển shared contract sang common parent, giữ plugin-private implementation ở child, rồi chạy lại test.
10. Test trong đúng môi trường container/plugin/module layer; IDE chạy thành công chưa chứng minh packaged artifact đã đúng.

Có thể bật class loading logs trong OpenJDK/HotSpot:

~~~bash
java -Xlog:class+load=info -cp host-classes HostMain plugin-classes
~~~

Log chi tiết thay đổi theo JDK và môi trường, vì vậy không có transcript cố định. Kết hợp log với `getClassLoader()`, không suy toàn bộ identity chỉ từ JAR path.

## 14. Những sai lầm cần tránh

| Sai lầm | Cách hiểu/sửa |
| --- | --- |
| “Cùng tên đầy đủ là cùng Class” | Runtime identity có defining loader |
| “Hai loader khác nhau chắc chắn nạp trùng” | Delegation có thể trả cùng Class parent |
| “Bytecode giống thì cast được” | Bytes giống không xóa namespace loader |
| “Annotation tên đúng thì reflection nhận được” | Phải cùng annotation Class, retention phù hợp |
| “catch cùng tên sẽ bắt mọi exception cùng tên” | Typed catch so tương thích runtime Class |
| “Bật child-first cho mọi package để isolate” | Chỉ cô lập plugin implementation/private dependencies |
| “TCCL mới sửa mọi duplication” | TCCL thay lookup context, không hợp nhất Class |
| “ServiceLoader không phải subtype chỉ do provider sai” | Có thể do service interface copy ở child |
| “LinkageError xuất hiện ở mọi duplicate” | Chỉ xuất hiện khi constraints/linkage cụ thể bị vi phạm |
| “Reflection bỏ qua type safety” | Reflection vẫn có kiểm tra receiver/argument/type; exception được bọc |
| “Chuyển JAR sang module path là tự sửa” | JPMS có quy tắc riêng, class identity vẫn quan trọng |
| “Cứ gộp mọi dependency vào parent” | Chỉ contract cần shared; private libs cần chính sách rõ |

## 15. Bộ test khuyến nghị cho plugin boundary

1. `hostApiClass.isAssignableFrom(pluginImplClass)` đúng khi plugin khai báo implement API chung.
2. Annotation `shared.Marker` có thể được host tìm bằng `isAnnotationPresent`.
3. Plugin ném shared exception thì handler/type check của host nhận đúng Class.
4. ServiceLoader provider là subtype của service interface host cung cấp.
5. Method parameters/return DTO classes ở boundary do common loader định nghĩa.
6. Plugin A và Plugin B có thể giữ dependency private khác versions mà không làm trùng API.
7. API/annotation/exception classes không bị copy định nghĩa qua child-first.
8. Hot reload tạo implementation Class mới nhưng shared API Class vẫn ổn định.
9. Test các trường hợp thiếu dependency và loader constraints, không chỉ tìm được class.
10. Kiểm tra JAR/WAR đóng gói thật và loader policy của application server.
11. Khi dùng annotations với named modules, kiểm tra exports/opens theo API scanner.
12. Đảm bảo logs khi lỗi có binary name **và** defining loader, tránh chẩn đoán sai.

## 16. Tự kiểm tra kiến thức

**Câu 1.** Hai interface cùng `getName()` nhưng khác defining loader có tương đương không?  
Không. JVM có thể coi đó là hai runtime types độc lập.

**Câu 2.** Hai plugin loader cùng delegate `shared.Api` về parent thì sao?  
Chúng có thể dùng cùng `Class<?>` do parent định nghĩa, không nhân đôi API.

**Câu 3.** Vì sao host `(Api) plugin` lỗi dù plugin code có `implements Api`?  
Vì `Api` trong plugin có thể là bản do loader khác define.

**Câu 4.** Annotation host không thấy có thể do những nguyên nhân nào?  
Retention không phải RUNTIME, annotation Class từ loader khác, sai class được scan hoặc module access.

**Câu 5.** Object `Problem` của plugin cùng tên có tự bị `catch(Problem)` của host bắt không?  
Không nếu hai Class runtime khác và không có quan hệ gán tương thích phù hợp.

**Câu 6.** Khi Method.invoke ném exception của plugin, tìm exception gốc ở đâu?  
Trong `InvocationTargetException.getCause()`.

**Câu 7.** ServiceLoader báo provider not a subtype thường nên kiểm tra gì trước?  
Provider có implement đúng service `Class<S>` của host không, cùng defining loader hay không.

**Câu 8.** Có nên để shared annotations và exceptions local-first với plugin implementation không?  
Không nếu host cần nhận diện chúng bằng typed contract; delegate các types này về shared parent.

**Câu 9.** Lỗi loader constraint violation có luôn xuất hiện ngay khi loader định nghĩa hai class cùng tên không?  
Không; phụ thuộc quan hệ liên kết cụ thể giữa những classes liên quan.

**Câu 10.** Module path có làm hai Class từ hai defining loader khác nhau thành một kiểu không?  
Không. Module configuration không xóa class identity.

## 17. Tài liệu tham chiếu

- JVM Specification Java 17 `5.3, runtime class identity và defining loader: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- JVM Specification Java 17 `5.3.4, Loading Constraints: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3.4
- Java SE 17, ClassLoader API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17, Class API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- Java SE 17, Annotation API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Annotation.html
- Java SE 17, ServiceLoader API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html
- Java SE 17, InvocationTargetException: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationTargetException.html
- JLS Java 17 `12.2, Loading of Classes and Interfaces: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.2


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="loading-policies-008">Phát hiện chu trình ủy quyền và tránh gọi lặp vô hạn</a>

<details>
<summary>Xem chi tiết</summary>

# Phát hiện chu trình ủy quyền và tránh gọi lặp vô hạn trong Java ClassLoader

## 1. Vấn đề: một yêu cầu nạp lớp đi vòng rồi quay lại điểm xuất phát

`ClassLoader` thường phối hợp theo cơ chế **delegation**: một loader nhận tên lớp, hỏi parent và chỉ tự tìm class khi parent không tìm được. Quy trình này giúp chia sẻ các lớp nền tảng, API/SPI chung và hạn chế tạo trùng runtime type. Tuy nhiên, nếu ứng dụng tự xây dựng quan hệ ủy quyền giữa nhiều loader mà **không kiểm soát hướng đi**, một yêu cầu có thể vòng lại loader ban đầu.

Ví dụ về đồ thị được cấu hình sai:

```text
                loadClass("plugin.Report")
                      |
                      v
                PluginLoader A
                      |
                 delegate to
                      v
                PluginLoader B
                      |
                 delegate to
                      v
                PluginLoader A
                      |
                 delegate to B
                      v
                     ...
```

Nếu mỗi loader chỉ chuyển tiếp yêu cầu mà không nhận ra nó đã xuất hiện trên cùng đường xử lý, lời gọi Java có thể lặp tới khi stack cạn, gây `StackOverflowError`. Trong những cài đặt giữ khóa nạp lớp xuyên nhiều loader, một vòng yêu cầu còn có thể dẫn tới **deadlock giữa các luồng**, tùy thứ tự lock và thuật toán delegation.

Chủ đề cần giải quyết gồm:

1. Khi nào một chu trình ủy quyền xuất hiện?
2. Cách phân biệt chu trình **giữa các loader** với đệ quy hợp lệ và với chu trình **kế thừa lớp**.
3. Cách thiết kế một **đồ thị ủy quyền không có chu trình**.
4. Cách phát hiện đường gọi lặp bằng khóa nhận diện **(loader, binary name)**.
5. Vì sao guard theo luồng chỉ giải quyết **đệ quy trên cùng luồng**, không đủ cho mọi bài toán đa luồng.
6. Cách kiểm thử và ghi log mà không làm hỏng nguyên tắc nạp lớp, khóa, parent delegation và type identity.

## 2. Phải phân biệt bốn loại “chu trình”

| Tình huống | Ví dụ | Hậu quả / cách hiểu |
| --- | --- | --- |
| Chu trình **delegation** tự thiết kế | A hỏi B, B hỏi A về cùng `com.acme.Widget` | Có thể lặp lời gọi `loadClass` vô hạn nếu không có guard hoặc hướng thoát |
| Chu trình **quan hệ lớp/interface trong class file** | X extends Y, Y extends X ở bytecode không hợp lệ | JVM có thể ném `ClassCircularityError`; đây không đơn thuần là lỗi gọi lặp giữa hai custom loader |
| Khởi tạo tĩnh đệ quy **cùng luồng** | Trong `<clinit>` của X, một method lại yêu cầu initialization của X | JVM có quy tắc reentrant initialization; không thể tự suy ra `loadClass` của custom loader đã tạo chu trình |
| Deadlock khởi tạo/nạp giữa **các luồng** | Thread T1 giữ một lock/đợi B, T2 giữ lock/đợi A | Có thể bị treo chéo; phát hiện recursion bằng `ThreadLocal` trên từng thread không tự giải quyết được |

Hai loại lỗi Java hay bị nhầm:

- **`StackOverflowError`:** thường là hệ quả của chuỗi lời gọi Java đệ quy quá sâu. Một custom `loadClass` sai có thể gây lỗi này, nhưng không phải chỉ riêng ClassLoader mới gây ra.
- **`ClassCircularityError`:** thuộc họ `LinkageError`, báo chu trình không hợp lệ trong hệ phân cấp class/interface khi JVM kiểm tra định nghĩa. **Không có bảo đảm** JVM sẽ biến mọi vòng `A.loadClass → B.loadClass → A.loadClass` thành lỗi này.

Không nên xử lý chu trình bằng cách `catch (StackOverflowError)` rồi tiếp tục nạp: đến lúc đó tài nguyên stack đã bị dùng quá mức. **Phòng ngừa trước lời gọi đệ quy** là hướng đúng.

## 3. Parent chain thông thường không phải đồ thị ngang tùy ý

Trong ví dụ đơn giản:

```text
Bootstrap
    ^
Platform
    ^
Application
    ^
Plugin A
    ^
Plugin B
```

Mũi tên biểu diễn hướng ủy quyền về phía parent. Một `ClassLoader` thường nhận parent tại lúc khởi tạo qua `super(parent)`; API public thông thường không cung cấp setter để thay parent tùy ý về sau. Với chuỗi parent được tạo theo cách thông thường, loader mới trỏ tới loader đã tồn tại, nên ứng dụng không dễ tạo vòng **bằng riêng quan hệ parent được lưu trong constructor**.

Ngược lại, chu trình hay đến từ **chính sách tùy biến bên ngoài parent chain**, chẳng hạn:

- A override `loadClass` và gửi một số namespace cho B; B lại gửi namespace ấy cho A.
- Plugin A dùng TCCL hoặc service registry để tìm plugin B; một callback của B quay về A và lặp lại yêu cầu nạp cùng class.
- Một router chứa bản đồ `package → delegate` có cấu hình mâu thuẫn: A chuyển `shared.*` cho B và B lại chuyển `shared.*` cho A.
- Một custom loader override `findClass(name)` nhưng thay vì tìm bytecode cục bộ lại gọi `this.loadClass(name)` hoặc vòng qua service khác rồi quay lại cùng yêu cầu.

**Lưu ý:** `getParent()` giúp quan sát parent chain, nhưng nó **không thể hiện hết** các cạnh ủy quyền do custom code tạo. Một chuỗi parent bình thường vẫn có thể đi cùng đồ thị callback/delegation vòng.

## 4. Quy trình loadClass mặc định và nơi dễ phá vỡ

`ClassLoader.loadClass(String, boolean)` trong mô hình mặc định về cơ bản:

1. Sử dụng khóa nạp phù hợp từ `getClassLoadingLock(name)`.
2. Gọi `findLoadedClass(name)` để tránh tìm/định nghĩa lại lớp đã được JVM ghi nhận.
3. Nếu chưa có, hỏi parent hoặc cơ chế bootstrap tương ứng.
4. Nếu parent không tìm được, gọi `findClass(name)` để tìm bytecode riêng.
5. Tùy tham số, thực hiện resolution rồi trả về `Class<?>`.

```text
loadClass("plugin.Report")
   |
   +-- findLoadedClass("plugin.Report") --- thấy ---> trả Class hiện có
   |
   +-- parent.loadClass("plugin.Report") -- thấy ---> trả Class của parent
   |
   +-- findClass("plugin.Report") ------- đọc bytecode -> defineClass
```

Override **chỉ `findClass`** để thay nguồn bytecode thường an toàn hơn override toàn bộ `loadClass`, vì giữ được cơ chế kiểm tra lớp đã nạp, parent-first và khóa nạp. Nếu muốn **child-first có chọn lọc**, việc override `loadClass` có thể cần thiết nhưng phải chủ động duy trì các hợp đồng quan trọng này.

Một anti-pattern dễ tạo recursion:

```java
@Override
protected Class<?> findClass(String name)
        throws ClassNotFoundException {
    return loadClass(name); // Sai: vòng về luồng loadClass hiện tại.
}
```

Đây là **mã lỗi để phân tích**, không phải triển khai sử dụng. Nếu parent không tìm ra lớp, `loadClass` gọi `findClass`; đoạn `findClass` này lại gọi `loadClass` với cùng tên, có thể dẫn đến vòng lặp:

```text
loadClass -> parent không tìm thấy -> findClass
              ^                        |
              |________________________|
```

Cách đúng là `findClass` **tìm bytecode từ nguồn cục bộ rồi `defineClass`**, hoặc ném `ClassNotFoundException` nếu không tìm ra nguồn hợp lệ; không chạy lại toàn bộ thuật toán nạp chính tên ấy.

## 5. Phát hiện đệ quy cùng luồng bằng cặp (loader, binary name)

Một guard điển hình theo dõi các yêu cầu đang nằm trên **đường gọi hiện tại**. Mỗi node được xác định bằng:

```text
(defining/visited loader object identity, requested binary name)
```

Chính xác hơn, ở giai đoạn chưa định nghĩa class thì node là **đối tượng loader đang xử lý request** cùng với tên lớp yêu cầu; chưa thể khẳng định nó sẽ là defining loader. Dùng danh tính đối tượng loader chứ không chỉ chuỗi tên loader, vì hai loader khác nhau có thể cùng hiển thị tên hoặc `toString()`.

Ý tưởng:

1. Trước khi router xử lý `(A, "plugin.Report")`, thêm cặp này vào một tập *in-progress* của thread.
2. Khi A chuyển cho B, thêm `(B, "plugin.Report")`.
3. Nếu B quay lại A với **cùng tên**, cặp `(A, "plugin.Report")` đã tồn tại; đây là tín hiệu đường gọi lặp ngay trên thread đó.
4. Báo lỗi có giải thích chu trình, hoặc thoát theo một chiến lược fallback **đã được thiết kế từ trước**; không tiếp tục chuyển tiếp vô hạn.
5. Trong `finally`, loại bỏ cặp vừa thêm. Khi đường xử lý kết thúc, dọn ThreadLocal để tránh giữ tham chiếu loader trong thread pool.

**Vì sao phải có binary name?** Việc xử lý class A có thể hợp lệ khi cần nạp **class B khác tên**; không phải mọi lần cùng loader xuất hiện trong một call stack đều là chu trình sai. Theo dõi ít nhất `(loader, name)` giúp tránh chặn nhầm phần lớn phụ thuộc hợp lệ. Tuy nhiên, một chu trình có thể đi qua nhiều tên lớp trước khi quay lại; nếu không bao giờ lặp đúng một cặp mà vẫn tạo đồ thị vô hạn theo quy tắc ứng dụng, cần giới hạn độ sâu/độ dài đường và chính sách nạp tổng thể.

**Vì sao không chỉ dùng một biến boolean toàn cục?** Hai thread khác nhau có thể nạp các class khác nhau một cách hợp lệ; một cờ chung dễ chặn nhầm. Đối với deadlock xuyên thread thì cần **phân tích đồ thị wait-for/lock**, không thể lấy một boolean chung làm giải pháp thay thế.

## 6. Ví dụ Java 17 chạy được: A → B → A, rồi chuyển sang đường không có vòng

Chương trình sau cố ý tạo một đồ thị ủy quyền **sai** giữa hai loader để quan sát guard chống recursion. Nó **không định nghĩa class mới**, mà minh họa cách nhận ra một đường gọi lặp của custom routing policy. Vì vậy kết quả độc lập với JAR ngoài và không cần tự tạo class file.

Một điểm thiết kế cần công khai: ví dụ override `loadClass(String)` **chỉ cho namespace kiểm thử `demo.*`** để quan sát guard; những tên khác vẫn dùng triển khai parent-first chuẩn. Đây là thí nghiệm về phát hiện chu trình, **không phải** bộ nạp plugin đa luồng sẵn sàng cho production. Thiết kế production cần các ràng buộc trong phần sau.

Lưu file `DelegationCycleDemo.java`:

```java
import java.util.HashSet;
import java.util.Set;

public class DelegationCycleDemo {

    static final class RoutingLoader extends ClassLoader {
        private static final ThreadLocal<Set<Key>> ACTIVE =
                ThreadLocal.withInitial(HashSet::new);

        private record Key(RoutingLoader loader, String name) {}

        private final String id;
        private RoutingLoader next;

        RoutingLoader(String id) {
            super(ClassLoader.getPlatformClassLoader());
            this.id = id;
        }

        void routeTo(RoutingLoader other) {
            this.next = other;
        }

        @Override
        public Class<?> loadClass(String name)
                throws ClassNotFoundException {

            if (!name.startsWith("demo.")) {
                return super.loadClass(name);
            }

            Set<Key> active = ACTIVE.get();
            Key current = new Key(this, name);

            if (!active.add(current)) {
                throw new ClassNotFoundException(
                        "Delegation cycle at " + id + ": " + name);
            }

            try {
                if (next != null) {
                    return next.loadClass(name);
                }
                return super.loadClass(name);
            } finally {
                active.remove(current);
                if (active.isEmpty()) {
                    ACTIVE.remove();
                }
            }
        }
    }

    public static void main(String[] args) throws Exception {
        RoutingLoader a = new RoutingLoader("A");
        RoutingLoader b = new RoutingLoader("B");

        a.routeTo(b);
        b.routeTo(a); // Đồ thị sai: A -> B -> A.

        try {
            a.loadClass("demo.Missing");
        } catch (ClassNotFoundException exception) {
            System.out.println(exception.getMessage());
        }

        b.routeTo(null); // A -> B -> platform; không còn vòng.

        try {
            a.loadClass("demo.Missing");
        } catch (ClassNotFoundException exception) {
            System.out.println("acyclic="
                    + exception.getClass().getSimpleName());
        }

        System.out.println("bootstrapString="
                + (a.loadClass("java.lang.String")
                        .getClassLoader() == null));
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac DelegationCycleDemo.java
java DelegationCycleDemo
```

Stdout xác định trong môi trường Java 17 thông thường:

```text
Delegation cycle at A: demo.Missing
acyclic=ClassNotFoundException
bootstrapString=true
```

**Diễn giải từng bước:**

1. `a.loadClass("demo.Missing")` thêm cặp `(A, demo.Missing)` vào tập của thread hiện tại.
2. A chuyển cho B, B thêm cặp `(B, demo.Missing)`.
3. B chuyển về A; lúc A thử thêm cặp cũ, `Set.add` trả `false`. Ví dụ chủ động ném `ClassNotFoundException` có thông điệp `Delegation cycle at A: demo.Missing` **trước khi stack tăng vô hạn**.
4. Các khối `finally` trên B và A dọn đường truy vết; sau đó đổi B thành điểm cuối có parent platform.
5. Lần yêu cầu thứ hai đi A → B → parent; không có vòng, nhưng `demo.Missing` thực sự vắng mặt nên kết thúc bằng `ClassNotFoundException` bình thường. `acyclic=...` cho biết **không tìm thấy class** khác với **đã phát hiện vòng**.
6. Với `java.lang.String`, thí nghiệm không áp dụng routing riêng mà dùng `super.loadClass`. Lớp JDK nền tảng được bootstrap định nghĩa, nên dòng cuối là `true`.

Chương trình dùng `record` và `ThreadLocal` có mặt trong Java 17; bản thân record `Key` so sánh bằng cả tham chiếu đối tượng loader và tên lớp. `ClassLoader` không override `equals` theo danh tính logic khác, nên phép so sánh loader trong record dùng identity của object theo mặc định.

### Điểm hạn chế quan trọng của ví dụ

- Custom `loadClass(String)` trong thí nghiệm là **router mô phỏng** cho `demo.*`; nó không xử lý đầy đủ trường hợp class đã nạp, resolve, namespace policy và đồng bộ như một loader production.
- `next` được thay đổi tuần tự trên một thread trước mỗi lần nạp. Nếu vừa nạp vừa thay đường routing ở nhiều thread, cần cơ chế công bố cấu hình an toàn hoặc tốt hơn là **cấu hình bất biến**.
- Guard `ACTIVE` của một thread không phát hiện chu trình chờ khi hai thread khác nhau đã nắm khóa/tài nguyên của hai loader.
- Khi một class tên `demo.*` tồn tại trong parent nhưng custom router vẫn ép qua peer trước, hành vi khác parent-first và phải được mô tả như policy cụ thể, không xem là hành vi mặc định của `URLClassLoader`.
- Ném `ClassNotFoundException` có thông điệp chu trình giúp ví dụ chạy dễ hiểu; ứng dụng thực tế có thể dùng một exception riêng ở **lớp cấu hình/router** để phân biệt cấu hình vòng với lớp thực sự vắng mặt. Không nên bắt mọi `LinkageError` hay `Throwable` rồi biến thành class-not-found.

## 7. Chiến lược tốt nhất: ngăn chu trình ngay khi cấu hình

Guard runtime giúp chẩn đoán và fail fast; giải pháp bền vững hơn là **không chấp nhận một đồ thị delegation có chu trình**.

Ví dụ một quan hệ policy:

```text
Host / Shared API loader
       ^
       |
Plugin API loader
    ^       ^
    |       |
Plugin A   Plugin B

Mọi plugin -> Shared API theo hướng cố định.
Không đặt cạnh Plugin A -> B và B -> A cho cùng namespace.
```

Đồ thị delegation theo **namespace** mới có ý nghĩa chính xác. Có thể cho A gửi namespace `shared.*` tới host và `pluginB.public.*` tới B, nhưng phải kiểm tra đường đi trên từng tập tên để không tạo vòng cho cùng yêu cầu.

### Kiểm tra đồ thị bằng DFS

Một thuật toán nhận một directed graph của các cạnh router:

1. Mỗi node là một loader hoặc cặp `(loader, namespace)` nếu routing phụ thuộc package/name.
2. Duyệt DFS từ mỗi node.
3. Đánh dấu node là **GRAY** khi đang nằm trên DFS stack; **BLACK** khi mọi cạnh đã duyệt xong.
4. Nếu gặp cạnh tới node GRAY, phát hiện **back edge** và có chu trình.
5. Từ chối cấu hình hoặc báo chính xác đường đi, ví dụ `A → B → C → A`.

```text
DFS(A):
  mark A = GRAY
  visit B:
    mark B = GRAY
    visit C:
      mark C = GRAY
      edge C -> A where A = GRAY   => CYCLE A -> B -> C -> A
```

**Độ phức tạp:** với đồ thị tường minh có V node và E cạnh, DFS phát hiện chu trình trong `O(V + E)` thời gian, bộ nhớ `O(V)`, không tính chi phí biểu diễn chính sách namespace.

Nhưng nếu quyết định route phụ thuộc vào **tên class cụ thể, thời điểm, phiên bản hay trạng thái runtime**, một DAG tĩnh của các loader có thể không phản ánh hết đồ thị gọi thật. Khi ấy nên kết hợp **kiểm tra cấu hình** với guard runtime trên đường thực thi, test mô phỏng và ràng buộc số lần chuyển tiếp.

### Thiết kế trách nhiệm một chiều

- API/SPI dùng chung do host/parent định nghĩa; loader plugin chỉ delegate lên đó.
- Implementation riêng do loader plugin tìm trong nguồn cục bộ (khi parent không có hoặc theo policy chọn lọc).
- Không sử dụng vòng callback “khi không tìm được class thì hỏi plugin khác, plugin khác lại hỏi plugin trước” mà thiếu *visited set*.
- Không định tuyến mọi namespace qua mọi plugin để “tăng khả năng tìm thấy”; cách đó làm type identity và lỗi phụ thuộc khó đoán.
- Cố định route map trước khi phục vụ request; nếu cần update, áp dụng snapshot bất biến hoặc versioned routing.

## 8. Chu trình giữa nhiều thread: ThreadLocal không đủ

Giả sử custom loader giữ lock riêng trong khi chuyển tiếp:

```text
Thread T1:
  giữ lock(A, "plugin.X")
  gọi B.loadClass("plugin.X")
                  \
                   \ chờ lock(B, "plugin.X")

Thread T2:
  giữ lock(B, "plugin.X")
  gọi A.loadClass("plugin.X")
                   \
                    \ chờ lock(A, "plugin.X")
```

Trong một thứ tự xen kẽ bất lợi, T1 chờ T2 và T2 chờ T1. Mỗi thread có **ThreadLocal riêng**, và chưa chắc thread nào từng xử lý cặp cùng tên hai lần. Do đó guard như ở ví dụ không bắt được **cross-thread deadlock**.

Không được suy diễn rằng JVM luôn phát hiện và tự hủy vòng chờ do mọi custom ClassLoader tạo ra. Cách giảm rủi ro:

1. **Tránh đồ thị ủy quyền có chu trình ngay từ kiến trúc.** Đây là biện pháp chính.
2. **Đừng giữ lock dài khi chuyển tiếp sang loader khác** nếu có thể thiết kế để việc tìm nguồn/định nghĩa class nằm trong quyền sở hữu một loader duy nhất. Tuy nhiên đừng tự ý bỏ khóa nạp làm hai thread định nghĩa trùng.
3. Nếu có nhiều khóa, thiết kế thứ tự lấy khóa nhất quán và tránh callback quay về loader trước.
4. Phân biệt khóa nạp class của JVM/loader với khóa ứng dụng trên cache, kho artifact, class loading callback, service registry.
5. Test song song cùng tên và nhiều tên class, dùng timeout trong test để phát hiện tình huống treo; timeout test **không** phải bằng chứng một runtime deadlock đã được “sửa”.
6. Khi nghi deadlock, lấy **thread dump** và đối chiếu các stack/monitor/lock chờ nhau; một số vòng chờ không hiện dưới dạng deadlock monitor chuẩn mà cần phân tích ngữ cảnh loader.

### Parallel capable ClassLoader không sửa chu trình

`ClassLoader.registerAsParallelCapable()` và `getClassLoadingLock(name)` hỗ trợ thiết kế loader nạp song song theo tên lớp khi đáp ứng điều kiện API. Chúng không tự biến đồ thị A→B→A thành DAG, cũng không thay thế logic tìm/định nghĩa một lớp đúng chuẩn.

Một loader override `loadClass` cần hiểu phương thức đồng bộ và những ràng buộc liên quan `findLoadedClass`, `defineClass`, `resolveClass`; lựa chọn khóa sai có thể gây race hoặc deadlock ngay cả khi đã có kiểm tra ThreadLocal.

## 9. Ghi log để tái dựng vòng ủy quyền

Một log đủ hữu ích nên ghi những trường sau:

| Trường | Ví dụ | Mục đích |
| --- | --- | --- |
| Request ID | `req-42` | Liên kết đường đi của một yêu cầu |
| Binary name | `plugin.Report` | Tên class đang được tìm |
| Loader identity | `A#1`, `B#2` | Phân biệt hai loader có thể cùng tên hiển thị |
| Delegate edge | `A → B` | Xác định chính sách chuyển tiếp |
| Thread | `worker-7` | Phân biệt recursion một thread và chờ giữa nhiều thread |
| Stage | `delegating / defined / not-found` | Biết đang tìm ở đâu và điều gì xảy ra |
| Duration/depth | `depth=3` | Nhận ra chuỗi bất thường sớm |
| Defining loader cuối | `HostLoader` hoặc `PluginLoader` | Xác nhận type identity của class trả về |

Ví dụ log minh họa (không phải stdout của chương trình Java ở phần 6):

```text
req=42 thread=main class=plugin.Report loader=A action=delegate target=B
req=42 thread=main class=plugin.Report loader=B action=delegate target=A
req=42 thread=main class=plugin.Report loader=A action=cycle-detected
```

Không log toàn bộ bytecode, token repository, URL bí mật hay đường dẫn nhạy cảm. Trong môi trường nhiều plugin, nên dùng một ID loader ổn định **trong tiến trình hiện tại** và xem `System.identityHashCode(loader)` chỉ là chỉ dấu hỗ trợ, không phải định danh toàn cục tuyệt đối (hash có thể trùng).

Nếu class đã nạp thành công, dùng `clazz.getClassLoader()` để xác minh **defining loader thực sự**; không nhầm với loader ban đầu nhận request hoặc TCCL của thread.

### Khi ứng dụng treo

Trên JVM hỗ trợ các công cụ attach và có quyền tương ứng:

```bash
jcmd <pid> Thread.print
```

Đây là lệnh tham khảo; phải thay `<pid>` bằng PID thật. Stack có thể cho thấy đường `A.loadClass → B.loadClass` hoặc các thread chờ monitor/lock liên quan. Cần đọc nhiều thread cùng lúc thay vì chỉ stack của thread đầu tiên gặp vấn đề.

Có thể kết hợp unified logging:

```bash
java -Xlog:class+load=info -cp app/classes com.acme.Main
```

Những tên classpath/class ở đây là ví dụ. Class-loading log phản ánh các sự kiện JVM nạp lớp; nó **không tự thay thế** log các lời gọi delegation tùy chỉnh. Nếu một chu trình nổ trước lúc định nghĩa class thành công, chỉ tìm log “class loaded” có thể không thấy đường vòng nào.

## 10. ClassCircularityError: lúc nào mới liên quan?

`ClassCircularityError` thuộc `LinkageError` và liên quan một chu trình trong quan hệ định nghĩa lớp/interface mà JVM phát hiện khi nạp/kiểm tra. Một đồ thị quan hệ bất hợp lệ có thể hình dung:

```text
class A extends B
class B extends A
```

Đây là **sơ đồ quan hệ không hợp lệ**, không phải code Java để biên dịch. `javac` thông thường đã từ chối inheritance cycle trong source; bytecode được sinh/biến đổi sai có thể khiến JVM gặp ràng buộc circularity lúc nạp.

So sánh:

- **Delegation cycle:** A-loader hỏi B-loader, B-loader hỏi A-loader về tên class; cốt lõi là **đồ thị lời gọi custom loader**. Nếu không guard, có thể là `StackOverflowError` hoặc deadlock.
- **Class circularity:** định nghĩa lớp/interface tạo **vòng kế thừa/phụ thuộc cấu trúc bị cấm** theo JVMS; có thể gây `ClassCircularityError`.
- **Static initialization cycle:** có thể có reference vòng trong static initializer; JVM có quy tắc initialization riêng, và một vòng chờ giữa hai thread là vấn đề khác.

Không nên viết:

```java
try {
    loader.loadClass(name);
} catch (ClassCircularityError error) {
    // Xem như mọi vòng loader đã được xử lý.
}
```

Đây là **mã sai để phân tích**, giả định có `loader` và `name`; nó vừa không phát hiện các vòng custom delegation thông thường, vừa có nguy cơ nuốt `Error` mang ý nghĩa lỗi bytecode/class graph thực sự.

## 11. Các lỗi thiết kế thường gặp

| Thiết kế sai | Hậu quả | Cách sửa |
| --- | --- | --- |
| Trong `findClass(name)` gọi lại `loadClass(name)` chính loader đó | Recursion khi parent không có class | Tìm bytes local rồi `defineClass` hoặc ném `ClassNotFoundException` |
| A delegate mọi `plugin.*` cho B và B delegate lại cho A | Vòng policy cho cùng namespace | Sửa graph thành hướng một chiều, hoặc trả not-found khi nguồn local không chứa |
| Chỉ dùng boolean `isLoading` toàn cục | False positive giữa nhiều thread/tên class | Theo dõi request theo thread và cặp loader–binary name; vẫn cần concurrency design |
| Chỉ track tên class, không track loader | Có thể chặn nhầm các lượt nạp cùng tên hợp lệ qua nhiều loader | Dùng danh tính loader và tên; xem ngữ cảnh path |
| Không dọn ThreadLocal khi có exception | Giữ lại loader, memory leak, false cycle về sau | Dọn trong `finally` và `ThreadLocal.remove()` khi trống |
| Bắt `StackOverflowError` để fallback | Hệ thống đã cạn stack; trạng thái không đáng tin | Phát hiện trước khi đệ quy bằng graph/visited guard |
| Route map thay đổi lúc nhiều thread load | Một yêu cầu đi theo cấu hình không nhất quán | Snapshot bất biến hoặc synchronization/đồng bộ publication |
| Custom `loadClass` bỏ `findLoadedClass`/locking | Trùng `defineClass`, `LinkageError`, race | Giữ mặc định khi có thể hoặc cài đầy đủ hợp đồng API |
| Cho phép “hỏi tất cả plugin” không giới hạn | Khó dự đoán kiểu, vòng phụ thuộc, chi phí cao | Chỉ định loader sở hữu namespace, giới hạn depth/budget |
| Chỉ kiểm tra thread-local rồi tuyên bố hết deadlock | Bỏ sót vòng chờ giữa các thread | Loại bỏ cạnh vòng, kiểm thử song song, theo dõi locks |
| Nhầm `ClassCircularityError` với vòng delegation | Chẩn đoán sai nguyên nhân | Xem stack, class hierarchy và đường custom loader |

## 12. Kiểm thử chính sách ủy quyền

Một bộ test tối thiểu cho loader/router nên gồm:

1. **Đường hợp lệ:** một tên class thuộc source local được định nghĩa đúng, lớp API chung được parent trả về.
2. **Lớp không tồn tại:** không có chu trình thì nhận `ClassNotFoundException` hữu hạn thời gian, không lặp vô hạn.
3. **Vòng 2 node:** A→B→A phải bị từ chối ở configuration validation hoặc bị runtime guard phát hiện sớm.
4. **Vòng 3 node:** A→B→C→A phải có kết quả tương tự, log chỉ rõ cạnh và class name.
5. **Không chặn nhầm:** A→B để nạp `util.Helper` từ A đang xử lý `plugin.Service` có thể là phụ thuộc hợp lệ nếu không quay lại cùng cặp hoặc vi phạm cấu hình.
6. **Lỗi giữa đường:** dù `ClassNotFoundException`, `IOException` hoặc một exception runtime được ném, state theo thread phải được dọn; lượt sau không bị nhận nhầm là còn nạp dở.
7. **Song song:** hai thread cùng tên và nhiều tên class khác nhau phải không định nghĩa trùng, không gặp deadlock; dùng timeout, thread dump khi test fail.
8. **Loader lifecycle:** sau khi plugin unload, registry/router, TCCL và ThreadLocal không giữ tham chiếu mạnh tới loader cũ ngoài ý muốn.
9. **Parent/bootstrap:** lớp nền tảng không bị định nghĩa lại, API/SPI host được chia sẻ đúng, type identity không sai.
10. **Class initialization:** static initializer không gọi vòng vào plugin router theo cách chờ chéo mà thiết kế chưa cho phép.

### Phân biệt fail-fast và fallback

Khi phát hiện chu trình, một số framework muốn bỏ qua nhánh bị lặp rồi thử **nguồn khác**. Điều này chỉ an toàn nếu:

- Đường tìm nguồn khác đã được mô tả rõ trong policy;
- Không âm thầm chọn bản cùng binary name khác API/ABI khiến kiểu runtime biến đổi khó dự đoán;
- Ghi lại sự kiện cycle và nguồn cuối được chọn;
- Không nuốt các lỗi bytecode/linkage nghiêm trọng để giả vờ chỉ là “class not found”.

Trong nhiều hệ thống, **từ chối cấu hình có chu trình ngay khi khởi động** đơn giản hơn và dễ debug hơn fallback động.

## 13. Bài tập tự kiểm tra

**Câu 1.** Nếu A gọi B để tìm `plugin.Item` và B lại gọi A để tìm cùng tên, JVM có bắt buộc ném `ClassCircularityError` không?

**Đáp án:** Không. Đó là chu trình lời gọi delegation tùy chỉnh, có thể dẫn tới recursion/stack overflow hoặc deadlock; `ClassCircularityError` liên quan đến circularity trong cấu trúc class/interface theo JVM.

**Câu 2.** Vì sao track riêng `binaryName` trong một `ThreadLocal Set<String>` có thể chặn nhầm?

**Đáp án:** Hai loader khác nhau có thể hợp lệ cùng đi qua một binary name trong delegation; cần theo dõi cặp loader object identity + tên, và đánh giá đường request.

**Câu 3.** Vì sao ví dụ dọn `ACTIVE` trong `finally` chứ không ngay sau `next.loadClass`?

**Đáp án:** Vì downstream có thể ném exception. `finally` đảm bảo state in-progress được gỡ ngay cả khi không tìm thấy lớp hoặc xảy ra lỗi.

**Câu 4.** Nếu hai thread T1 và T2 đợi lock của nhau trong hai custom loader, một ThreadLocal guard có giải quyết được không?

**Đáp án:** Không. Hai thread có path riêng; cần thiết kế graph không có vòng, lock ordering và concurrency tests.

**Câu 5.** Với loader chỉ cần đổi nơi đọc bytecode, nên ưu tiên override phương thức nào?

**Đáp án:** `findClass(String)`, để giữ mặc định `loadClass` xử lý parent delegation, lớp đã nạp và locking.

**Câu 6.** Một `findClass` không tìm thấy bytecode local. Có nên gọi `this.loadClass(name)` để “thử lại” không?

**Đáp án:** Không cho cùng tên khi `findClass` đã được `loadClass` gọi sau khi parent fail; nó có thể tạo đệ quy. Nên báo `ClassNotFoundException` hoặc tuân theo một fallback khác được thiết kế tránh vòng.

**Câu 7.** Điều gì cần kiểm tra khi `jcmd Thread.print` cho thấy hai thread kẹt trong hai custom loader?

**Đáp án:** Khóa mỗi thread đang giữ, nơi chờ, class name, các cạnh delegation, callback, static initialization và liệu một request đã được forward quay lại loader trước qua thread khác hay không.

**Câu 8.** Vì sao chuyển mọi package sang child-first để “tránh gọi parent” không phải giải pháp tổng quát?

**Đáp án:** Có thể làm các loader tự định nghĩa bản riêng của API/SPI và gây xung đột type identity. Cần thiết kế namespace rõ và định nghĩa hướng ủy quyền có kiểm soát.

## 14. Checklist thiết kế và vận hành

- [ ] Biết loader nào **sở hữu** từng namespace và loader nào chỉ delegate.
- [ ] Parent chain thông thường và các **cạnh chuyển tiếp custom** đều được mô hình hóa, không chỉ nhìn `getParent()`.
- [ ] Có validation DAG hoặc nguyên tắc routing một chiều trước khi phục vụ request.
- [ ] Với router động, có visited guard theo `(loader identity, binary name)` và bảo đảm dọn state trong `finally`.
- [ ] Không áp dụng guard cùng-thread như giải pháp toàn diện cho deadlock xuyên thread.
- [ ] Không override `loadClass` khi override `findClass` đủ đáp ứng yêu cầu.
- [ ] Không gọi lại `loadClass(name)` từ `findClass(name)` cho cùng tên.
- [ ] Giữ khóa nạp, `findLoadedClass` và chính sách `defineClass` chính xác nếu phải override.
- [ ] Không dùng `catch (StackOverflowError)` làm phương án phát hiện chu trình.
- [ ] Có log đường delegation với loader identity, class name, thread/request ID nhưng không lộ thông tin nhạy cảm.
- [ ] Kiểm thử vòng 2/3 node, đường hợp lệ, class missing và nhiều thread tranh chấp.
- [ ] Có chiến lược cleanup loader, ThreadLocal và routing registry khi unload/redeploy.

## 15. Kết luận và tài liệu tham khảo

Để tránh gọi lặp vô hạn khi tùy chỉnh ClassLoader, giải pháp đầu tiên là **thiết kế đồ thị ủy quyền một chiều hoặc không có chu trình** cho mỗi namespace. Khi chính sách đủ phức tạp để phát sinh vòng runtime, dùng guard theo **(loader object identity, binary name)** trên đường xử lý và dọn state bằng `finally` nhằm fail fast. Tuy nhiên, guard này chủ yếu phát hiện **recursion trong cùng thread**; vòng chờ nhiều thread vẫn đòi hỏi kiểm soát graph và locks.

Cần tiếp tục phân biệt vòng delegation với `ClassCircularityError` và với vòng initialization, giữ quy tắc `loadClass`/`findClass`/`findLoadedClass` đúng chuẩn, và không coi lỗi class-not-found là lý do nạp tùy tiện class cùng tên từ nguồn khác.

Tài liệu chính thức:

- Java SE 17 `ClassLoader` API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17 `ThreadLocal` API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html
- Java SE 17 `ClassCircularityError`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassCircularityError.html
- JVMS 17 §5.3, *Creation of Classes and Interfaces*: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- JLS 17 §12.4.2, *Detailed Initialization Procedure*: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.2


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="loading-policies-009">Đánh đổi giữa cô lập, tương thích và khả năng chẩn đoán</a>

<details>
<summary>Xem chi tiết</summary>

# Đánh đổi giữa cô lập, tương thích và khả năng chẩn đoán trong chính sách nạp lớp Java

Khi triển khai một hệ thống plugin, application server, test runner, bộ script động hoặc ứng dụng cho phép nhiều phiên bản thư viện, ta phải chọn **chính sách class loading**. Lựa chọn quan trọng không đơn thuần là viết `new ClassLoader(...)`, mà là quyết định **class nào do loader nào định nghĩa, lớp nào được chia sẻ và hoạt động nạp được quan sát bằng cách nào**.

Ba mục tiêu thường kéo nhau theo các hướng khác nhau:

- **Cô lập (isolation):** hạn chế các phiên bản/cấu phần nhìn thấy hoặc can thiệp vào nhau; cho phép cùng tên binary của class được định nghĩa trong những loader khác nhau.
- **Tương thích (compatibility/interoperability):** bảo đảm host và plugin cùng dùng **một runtime type** cho API giao tiếp, đồng thời không phá vỡ resolution, visibility, method linkage và hợp đồng của framework.
- **Khả năng chẩn đoán (diagnosability/observability):** khi xảy ra `ClassCastException`, `NoSuchMethodError`, `ClassNotFoundException` hoặc resource lookup sai, có thể xác định chính xác **ai yêu cầu nạp, ai định nghĩa, nguồn bytecode là gì, chính sách ủy quyền ra sao**.

Không có một chính sách duy nhất tối ưu cả ba. **Parent-first** thường ưu tiên chia sẻ và tương thích; **child-first có chọn lọc** tăng cô lập dependency riêng; **ghi nhận loader identity và quá trình resolution** giúp chẩn đoán nhưng thêm độ phức tạp/vận hành.

## 1. Mô hình nền tảng: runtime type không chỉ có tên lớp

Java coi một runtime class/interface được xác định bởi hai yếu tố:

~~~text
Runtime type identity = (binary name, defining class loader)
~~~

Ví dụ:

~~~text
plugin.impl.Greeting do loader A định nghĩa
plugin.impl.Greeting do loader B định nghĩa
~~~

Hai kiểu này có cùng tên `plugin.impl.Greeting` nhưng là **hai kiểu runtime khác nhau** nếu A và B là hai defining loader khác nhau.

Ngược lại, hai loader con A/B có thể gọi `loadClass("api.Plugin")` rồi cùng được parent trả về **chính một** `Class<?>` do parent định nghĩa. Trong trường hợp đó, API `api.Plugin` được chia sẻ.

Sơ đồ thường dùng:

~~~text
                     Bootstrap / platform
                             |
                         App loader
                       defines api.Plugin
                        /            \
                       /              \
            Plugin loader A       Plugin loader B
            defines ImplA         defines ImplB
                       \              /
                        \            /
                  Host sử dụng api.Plugin
~~~

Nếu plugin loader định nghĩa lại `api.Plugin` thay vì delegate cho app loader, thì một class `implements api.Plugin` trong plugin có thể implement **một interface runtime khác** so với `api.Plugin` của host, dù package và class name hoàn toàn giống nhau.

### Defining loader và initiating loader

**Defining loader** là loader thực sự tạo định nghĩa class (thường qua `defineClass`). `Class.getClassLoader()` cho biết defining loader.

**Initiating loader** có thể là loader bắt đầu yêu cầu `loadClass` và sau đó delegate cho parent; nó không nhất thiết là defining loader.

~~~java
Class<?> target = pluginLoader.loadClass("api.Plugin");
ClassLoader defining = target.getClassLoader();

System.out.println(defining == pluginLoader);
~~~

Đây là **mảnh minh họa**, giả định `pluginLoader` đã tạo và API có thể tìm. Kết quả `false` hoàn toàn bình thường nếu API được parent định nghĩa.

**Không** suy ra defining loader từ tên JAR, tên loader, classpath hay việc một method `loadClass` có log nhận yêu cầu.

## 2. Parent-first: tương thích trước, phiên bản phụ thuộc vào parent
`ClassLoader.loadClass` mặc định hỏi parent trước rồi mới đến `findClass` của child nếu chưa có định nghĩa. Điều này giúp các plugin dùng chung API và thư viện do host quản lý. Đổi lại, `lib.Parser` ở parent có thể **che** một phiên bản trong plugin; class nạp được nhưng phương thức mà plugin đã biên dịch để gọi có thể thiếu, dẫn đến `NoSuchMethodError`.

Lợi ích vận hành là ít loader-specific overrides, nhưng chỉ có ý nghĩa khi owner của dependency và **binary compatibility** được kiểm soát. Parent-first không bảo đảm mọi plugin tương thích với mọi thư viện ở parent.

## 3. Child-first: cô lập có chọn lọc, phải bảo vệ boundary
Với những dependency private bị host shadow, child-first có thể chọn bytecode thuộc plugin trước. Điều này cho phép hai plugins dùng cùng binary name với hai versions khác nhau, nhưng chỉ hiệu quả nếu chúng không trao đổi trực tiếp những implementation types ấy qua API host.

Cần giữ `java.*`/platform classes và shared interface, DTO, annotation, exception types ở loader chung. Child-first không tự thay `getResource`/ServiceLoader hay bypass JPMS, không phải sandbox bảo mật, và tăng chi phí chẩn đoán/metadata nếu tạo nhiều class definitions. Câu hỏi là **namespace nào thật sự phải độc lập**, chứ không phải đổi toàn bộ ứng dụng sang child-first.

## 4. So sánh các chính sách

| Tiêu chí | Parent-first | Child-first toàn phần | Child-first chọn lọc |
|---|---|---|---|
| Chia sẻ API host/plugin | Thuận lợi | Dễ vô tình nhân bản kiểu | Thuận lợi nếu giữ API ở parent |
| Tách phiên bản library trùng tên | Hạn chế | Cao hơn | Cao cho namespace được chọn |
| Tính dự đoán mặc định | Dễ hơn | Phụ thuộc override | Phụ thuộc danh sách prefix/quy tắc |
| Rủi ro `ClassCastException` do duplicate API | Thấp hơn | Cao | Thấp nếu boundary thiết kế đúng |
| Rủi ro method/linkage incompatibility | Có nếu parent version sai | Có ở ranh giới chia sẻ | Có, cần kiểm thử |
| Resource lookup tương ứng class | Dễ hiểu hơn khi default delegation | **Không tự động đồng bộ** | Phải thiết kế riêng |
| Gỡ lỗi loader identity | Thường đơn giản hơn | Phức tạp hơn | Cần log chính sách cụ thể |
| Chi phí quản trị cấu hình | Thấp hơn | Cao hơn | Trung bình–cao |
| Phù hợp plugin nhiều version | Khi API/dependencies đồng nhất | Chỉ trong thiết kế được kiểm soát | Thường là điểm cân bằng |

Không có cột nào “bảo mật hơn” theo nghĩa có thể chạy mã không tin cậy một cách an toàn. Cả ba phương án vẫn chạy bên trong cùng JVM/process trừ khi bạn thiết kế thêm ranh giới cách ly thực sự.

## 5. Thiết kế ranh giới chia sẻ trước khi chọn policy

Một cách tổ chức hợp lý cho plugin runtime:

~~~text
Application classloader
  api.Plugin
  api.PluginContext
  shared DTO / public contracts
          |
          +-- Child loader A
          |    plugin.impl.Greeting
          |    plugin.internal.*
          |    private dependencies v1
          |
          +-- Child loader B
               plugin.impl.Greeting
               plugin.internal.*
               private dependencies v2
~~~

Các nguyên tắc:

1. **API chung phải có một defining loader thống nhất**. Đặt `api.Plugin`, các interface callback và DTO thực sự được chia sẻ ở parent.
2. **Implementation của mỗi plugin có thể là child-first** nếu host không cần liên kết trực tiếp đến class triển khai theo tên.
3. **Hạn chế kiểu implementation riêng vượt ranh giới**. Giao tiếp qua `api.Plugin` hoặc DTO chung do parent định nghĩa.
4. **Kiểm tra transitive dependencies**, không chỉ plugin top-level class. Một dependency của plugin có thể tham chiếu class mà parent đã cung cấp phiên bản không tương thích.
5. **Có chiến lược resources/services**. Child-first cho class **không tự đổi** `getResource` thành child-first; resource cùng tên có thể đến từ parent.
6. **Thiết kế lifecycle**. Loader không tự unload class khi plugin bị “disable”; phải giải phóng references và tài nguyên được giữ.
7. **Chấp nhận ranh giới JPMS** nếu dùng named modules. Module readability/exports/opens khác với policy của custom URLClassLoader.

## 6. Case study: chọn policy cho hai plugin mà host đã có dependency cũ
Giả sử host chứa `lib.Parser` v1 và public SPI `api.Plugin`; plugin A dùng Parser v1, plugin B được biên dịch với Parser v2 có thêm `parse(byte[])`. Cả hai plugin phải implements **chính runtime `api.Plugin`** của host.

| Phương án | Hành vi đối với plugin B | Hợp đồng API và vận hành |
| --- | --- | --- |
| Giữ parent-first với v1 trên parent | B có thể nhận Parser v1 rồi phát sinh `NoSuchMethodError` khi gọi method chỉ có ở v2 | Dễ quản lý loader nhưng không đạt yêu cầu chức năng |
| Loại Parser khỏi parent, đặt bản tương ứng ở mỗi plugin URL | Cả A/B tự định nghĩa Parser phiên bản riêng ngay với parent-first thông thường | Ưu tiên khi dependency này thực sự private; API vẫn do parent định nghĩa |
| Parent còn Parser v1, dùng selective child-first cho `lib.*` private | B ưu tiên Parser v2 nếu JAR child chứa đủ phụ thuộc | Cần audit transitive dependencies, fallback, resource, sealed packages |
| Shading/relocation Parser v2 | Đổi binary name tránh trùng với host | Build phải cập nhật reflection, resource và service descriptors khi cần |
| Plugin B ở process/JVM riêng | Không có class identity trực tiếp với host | Cần protocol/schema IPC, chi phí deploy/vận hành lớn hơn |

### 6.1. Kết quả chẩn đoán theo từng trường hợp

```text
B gọi lib.Parser.parse(byte[])
  ├─ Parser được parent định nghĩa, không có signature   → NoSuchMethodError
  ├─ Parser v2 được child định nghĩa, signature tồn tại  → lời gọi có thể thành công
  ├─ child định nghĩa lại api.Plugin                    → host có thể không cast được
  └─ class v2 + config từ parent v1                     → có thể chạy sai dù không lỗi linkage
```

Trước khi quyết định, ghi lại class đang lỗi, `getClassLoader()`, `CodeSource` khi có, JAR class thật, tên/descriptor method bằng `javap -s`, URL resource và loader được `ServiceLoader` dùng. Một class tìm được không chứng minh runtime đã dùng đúng version.

### 6.2. Ma trận kiểm chứng sau thay đổi

1. **Host API:** `api.Plugin` từ loader plugin phải là chính `api.Plugin.class` của host; thử cast một instance implementation.
2. **Dependency ownership:** Parser A và B phải được định nghĩa bởi loader dự kiến; so hai `Class<?>` cùng tên bằng `==` và gọi method v2 thật.
3. **Missing local class:** thử tên chỉ có ở parent. Chọn fallback nếu đã phê duyệt, hoặc fail closed cho private namespace phải cô lập tuyệt đối.
4. **Resource/SPI:** đặt tên resource trùng giữa parent và child, kiểm tra cả `getResource` lẫn `getResources`; với service descriptor, giữ đủ provider hợp lệ.
5. **Lifecycle:** reload B bằng loader mới, dừng threads/callbacks, dọn TCCL và cache; `close()` không bảo đảm unload tức thì.
6. **Quan sát:** cung cấp loader ID ổn định, class/package rule được chọn và nguồn thực tế; không log nội dung bí mật.

Case study này không đòi một custom classloader duy nhất. Nó buộc chọn **tập dependencies thuộc parent**, **đường giao tiếp dùng chung**, và **cách chứng minh version thật khi chạy** trước khi triển khai.

## 7. Tại sao child-first toàn phần dễ phá tương thích?

Giả sử host compile dựa vào:

~~~java
package api;
public interface Plugin {
    String message();
}
~~~

Nếu child-first định nghĩa lại `api.Plugin` từ bản copy trong plugin JAR, thì class trong plugin implement **một `api.Plugin` khác**. Phía host không tự cast object được về interface của nó.

~~~text
Host:  (api.Plugin, AppLoader)
Plugin:(api.Plugin, ChildLoader)

Tên giống nhau, runtime type khác nhau.
~~~

Trường hợp này thường dẫn đến `ClassCastException` khi host cast object trực tiếp. Ngoài ra, JVM có thể gặp loading constraints hoặc lỗi liên kết khác nếu hai định nghĩa kiểu bị kết hợp trong signature. Không phải mọi lỗi đều có cùng exception; phải đọc stack trace và xác định **thời điểm load/resolve/initialize/cast**.

**Phương án cân bằng:**

- Parent-first: `java.*`, `javax.*` khi thích hợp, public API host/plugin, DTO/exceptions chung, logging facade hoặc những contract dùng qua boundary.
- Child-first chọn lọc: `plugin.impl.*` và những namespace implementation riêng thật sự cần cách ly.
- Với library bị shade/relocate, kiểm tra binary names đã thay đổi và tránh để plugin export những class library riêng trực tiếp qua API chung.
- Các namespace chia sẻ nên được quy hoạch thành một danh sách có chủ sở hữu rõ ràng; đừng thêm “ngoại lệ” không kiểm soát.

## 8. Tương thích bao gồm API, ABI, module và vòng đời

### 8.1. Binary compatibility

Chia sẻ một class từ parent không bảo đảm plugin compile trên v2 sẽ chạy với v1. Ví dụ method `foo(int)` chỉ có ở bản mới: plugin có thể nạp được class `lib.Util` cũ nhưng khi resolve lời gọi `foo(int)` JVM báo `NoSuchMethodError`.

Do đó cần kiểm thử:

- Signature các method/field và inheritance mà plugin sử dụng.
- Phiên bản bytecode `--release` và runtime JDK.
- Transitive dependencies và version được JVM thực sự nạp.
- Những lời gọi reflection, method handles và service provider discovery.

### 8.2. Package-private và runtime package

JVM xác định runtime package từ **package name + defining loader**. Hai class cùng `package p;` nhưng ở loader khác nhau **không đương nhiên có quyền package-private** lẫn nhau. Có thể gặp `IllegalAccessError` dù javac cho phép cùng package khi compile.

Không phân chia các class cần dùng package-private thành các defining loaders khác nhau trừ khi đã chủ động thiết kế lại boundary sang public API thích hợp.

### 8.3. JPMS không thay thế chính sách classloader

Named module có `requires`, `exports`, `opens`, readability và encapsulation. Loader hierarchy không tự vượt qua những ranh giới đó.

Tạo `new URLClassLoader(urls, parent)` không tự resolve JAR thành named module chỉ vì JAR có `module-info.class`. Đối với chương trình modular, xét `ModuleLayer`, module configuration và module loader đúng cơ chế.

### 8.4. Serialization và giao tiếp qua loader boundary

Nếu host/plugin cùng dùng một DTO hoặc exception, nên đặt loại đó ở loader chung. Java serialization với class do loader riêng định nghĩa có thể gặp vấn đề giải quyết kiểu tại bên nhận. Không dựa vào việc tên class trùng nhau để coi chúng là interchangeable.

Đối với boundary dài hạn, nên dùng API versioned rõ ràng hoặc schema/format ổn định (JSON, protocol messages, v.v.) khi cần tách deployment hay tách process.

### 8.5. Unloading và leaks

Đóng `URLClassLoader` giúp đóng file handles, nhưng class không được unload ngay. Reference tới `Class<?>`, instance plugin, thread context loader, static cache, `ThreadLocal`, thread đang chạy hoặc callback có thể giữ loader sống lâu.

Nếu ưu tiên hot redeploy, cần thiết kế **plugin lifecycle** từ đầu: dừng threads, đóng resources, hủy callback, dọn caches, reset TCCL và loại bỏ references. Isolation hỗ trợ việc tái triển khai nhưng không tự làm thay các bước đó.

## 9. Khả năng chẩn đoán: cần log gì và không nên log gì?

### 9.1. Minimum diagnostic context

Khi nhìn thấy một lỗi classloading, log các thông tin sau:

| Trường | Ví dụ / ý nghĩa |
|---|---|
| Binary name | `plugin.impl.Greeting` |
| Defining loader class | `PolicyLoader` hay built-in app loader |
| Loader name | `"child-first"`, chỉ là nhãn |
| Loader object identity | So bằng `==`; `identityHashCode` chỉ giúp log trong process và **không tuyệt đối duy nhất** |
| Parent chain | Loader nào là parent ở mỗi bước |
| CodeSource / source URL | Nguồn JAR/file nếu JVM có metadata này |
| Resource URL | Resource cụ thể được chọn |
| TCCL | `Thread.currentThread().getContextClassLoader()` |
| Runtime module | `type.getModule()` |
| Error chain | Class/linkage exception và nested causes |

Không log bí mật, API keys hoặc nội dung đầy đủ của file cấu hình nhạy cảm chỉ để chẩn đoán. Với policy động, nên log **prefix rule nào đã được áp dụng** và loader nào thực sự định nghĩa class.

### 9.2. Mảnh code chẩn đoán class

~~~java
static void explain(Class<?> type) {
    ClassLoader loader = type.getClassLoader();

    System.out.println("name=" + type.getName());
    System.out.println("module=" + type.getModule().getName());
    System.out.println("loader-name="
            + (loader == null ? "<bootstrap>" : loader.getName()));
    System.out.println("loader-class="
            + (loader == null ? "<bootstrap>"
                : loader.getClass().getName()));
    System.out.println("loader-id="
            + (loader == null ? 0
                : System.identityHashCode(loader)));
}
~~~

Đây là **method minh họa**, không có `main`. `Module.getName()` có thể trả `null` cho unnamed module. `identityHashCode` có thể trùng giữa hai object trong một JVM, nên chỉ sử dụng làm manh mối log, **không làm khóa nhận dạng bất biến duy nhất**; nếu có thể, ghi ID logic tự cấp cho mỗi loader từ registry của ứng dụng.

Nếu muốn truy nguồn class:

~~~java
Class<?> type = obj.getClass(); // obj phải khác null

var domain = type.getProtectionDomain();
var codeSource = domain == null ? null : domain.getCodeSource();

System.out.println(codeSource == null
        ? "source unavailable"
        : codeSource.getLocation());
~~~

Đây là **mảnh minh họa**, không phải mọi class đều có CodeSource hữu ích; giá trị có thể `null`. Không suy đoán URL nguồn từ chuỗi `type.getName()`.

### 9.3. Logging của HotSpot

Trên HotSpot/JDK 17+ có thể bật Unified Logging:

~~~bash
java -Xlog:class+load=info,class+unload=info -jar app.jar
~~~

Log phụ thuộc JDK và có thể gồm thông tin rất nhiều class; đây là **lệnh chẩn đoán**, không thuộc stdout dự kiến của demo. Để điều tra lỗi ở production, có thể bật log tập trung trên khoảng thời gian ngắn, lọc theo class/plugin và giữ đủ context của defining loader.

Các công cụ như `jcmd`, Java Flight Recorder (JFR) hoặc profiler có thể bổ sung số lượng classloader, classloading/unloading events và allocations khi runtime hỗ trợ. Không mặc định một event chứa đủ mọi thông tin về mọi cuộc delegation.

### 9.4. Khi resource cùng tên nhưng sai phiên bản

Luôn kiểm tra URL thật sự được trả:

~~~java
URL found = loader.getResource("plugin/config.txt");
System.out.println(found);
~~~

Đây là mảnh minh họa, có thể trả `null`. Với `URLClassLoader`, có thể so `getResource` (bao gồm parent delegation) và `findResource` (tìm trong nguồn của chính loader) để xác định xung đột.

## 10. Quy trình xử lý lỗi theo từng hiện tượng

### `ClassCastException: X cannot be cast to X`

1. Xác định `Class<?>` nguồn và `Class<?>` đích; không chỉ đọc `getName()`.
2. In `getClassLoader()` của cả hai và so bằng `==`.
3. Kiểm tra interface/DTO nào đã bị child định nghĩa trùng với parent.
4. Chuyển shared API về parent hoặc thay đổi boundary thành contract rõ ràng.
5. Nếu cần chạy hai phiên bản độc lập, không ép kiểu trực tiếp qua class implementation riêng.

### `NoSuchMethodError` / `NoSuchFieldError`

1. Xác định tên binary của class chứa method/field đang fail.
2. Kiểm tra defining loader và CodeSource của class thực sự được nạp.
3. So signature thực với phiên bản plugin đã compile.
4. Nếu parent-first che bản dependency mới, cân nhắc child-first **cho thư viện implementation phù hợp**, không thay toàn bộ policy.
5. Chạy integration tests với classpath/deployment giống production.

### `ClassNotFoundException`

1. Kiểm tra binary name đúng với package.
2. Kiểm tra URL roots/JAR entries và quyền truy cập file.
3. Xác định loader nào nhận yêu cầu: host, plugin loader, TCCL hay framework loader.
4. Kiểm tra policy prefix và fallback parent.
5. Không chữa bằng thêm mọi JAR vào global classpath nếu điều đó phá isolation.

### `NoClassDefFoundError`

Đây không phải lúc nào cũng là “file `.class` mất”. Nó còn có thể phát sinh do dependencies thiếu, lỗi liên kết hoặc một kiểu đã initialization thất bại. Phải đọc phần `Caused by` và thời điểm lỗi, thay vì ngay lập tức tạo loader mới.

### `IllegalAccessError`

Kiểm tra runtime package identity và quyền truy cập module/class. Cùng chuỗi package không đủ nếu defining loaders khác nhau. Nếu dùng JPMS, `exports`/`opens` có tác dụng khác nhau; không sửa tùy tiện bằng “mở tất cả package”.

### `ServiceLoader` không thấy provider

`ServiceLoader.load(PluginService.class)` thường sử dụng **thread context class loader (TCCL)** theo API overload tương ứng. Nếu TCCL không phải loader có provider, lookup có thể thất bại dù `pluginLoader.loadClass` thấy lớp.

Trong workflow cho plugin, có thể tạm thiết lập TCCL trong phạm vi có kiểm soát và **phải khôi phục trong `finally`**:

~~~java
Thread thread = Thread.currentThread();
ClassLoader original = thread.getContextClassLoader();

try {
    thread.setContextClassLoader(pluginLoader);
    // Gọi framework / ServiceLoader khi contract cho phép.
} finally {
    thread.setContextClassLoader(original);
}
~~~

Đây là **mảnh minh họa**, cần biến `pluginLoader` hợp lệ. Không thay TCCL của thread pool rồi quên khôi phục; điều đó có thể giữ plugin loader cũ sống lâu và gây cross-plugin visibility sai.

## 11. Tác động hiệu năng và vận hành

| Yếu tố | Cô lập mạnh (nhiều loaders) | Chia sẻ mạnh (ít loaders) |
|---|---|---|
| Số runtime `Class<?>` của thư viện trùng | Có thể tăng | Ít bản trùng hơn |
| Metaspace và metadata overhead | Có thể tăng do duplicate definitions | Thường giảm |
| Khả năng chạy dependency version riêng | Cao hơn | Hạn chế hơn |
| CPU cho class loading và resource lookup | Có thể tăng theo số loader/URL/delegation | Có thể đơn giản hơn |
| Thay plugin mà không thay host | Thuận lợi khi lifecycle đúng | Khó nếu class ở loader sống suốt đời |
| Chẩn đoán lỗi duplicate class | Phức tạp hơn | Ít trường hợp hơn, nhưng version conflicts vẫn có |
| Tác động thay đổi API chung | Cần contract versioned | Có thể ảnh hưởng toàn hệ thống |

Không coi “nhiều loaders” chắc chắn chậm hơn với một con số cố định: hiệu năng phụ thuộc số class, kích thước JAR, caching, I/O, JPMS/framework, pattern tải class và tần suất tạo/hủy loader. Đo trong workload thực tế bằng JFR, JVM metrics và các phép benchmark có kiểm soát.

Tránh tạo một URLClassLoader mới cho **mỗi request**. Một plugin có vòng đời rõ ràng thường cần một loader tồn tại tương ứng lifecycle đó, được đóng sau khi tất cả công việc của plugin kết thúc.

## 12. Một chiến lược chọn policy có kiểm chứng

**Bước 1 — Vẽ boundary type.** Liệt kê API, DTO, exception, callback, service interfaces dùng qua host/plugin. Chúng cần được định nghĩa ở đâu?

**Bước 2 — Phân loại package.** Xác định những namespace là API chung và những namespace chỉ chứa implementation/dependency riêng.

**Bước 3 — Chọn mặc định parent-first.** Giữ delegation chuẩn cho Java core, platform và shared API.

**Bước 4 — Chỉ mở child-first cho nhu cầu có chứng cứ.** Nếu plugin thật sự cần dependency khác version với host, chọn prefix/namespace càng nhỏ càng tốt; cân nhắc shading/relocation nếu phù hợp.

**Bước 5 — Đồng bộ chính sách resource.** Kiểm tra `getResource`, `getResources`, ServiceLoader, TCCL và config source; không mặc định class policy tự kéo resource policy theo.

**Bước 6 — Kiểm thử cả đường thành công lẫn lỗi.** Có ít nhất các test parent has class / parent misses / duplicate API / duplicate resource / missing dependency / wrong bytecode version / plugin redeploy.

**Bước 7 — Thiết kế chẩn đoán.** Log class name, defining loader, parent chain, CodeSource, module, policy branch và resource URL (không log secrets).

**Bước 8 — Thiết kế vòng đời.** Đóng stream/JAR handles, dừng plugin tasks, reset TCCL, loại bỏ references để JVM có thể unload khi thích hợp.

## 13. Checklist kiểm thử

- [ ] API chung được một defining loader sở hữu và cả host/plugin nhìn đúng runtime type.
- [ ] Child-first chỉ áp dụng những namespace có chủ đích, không áp dụng mù quáng cho `java.*`.
- [ ] Plugin có dependency riêng version khác vẫn dùng đúng bytecode cần thiết.
- [ ] Có test trùng tên class giữa parent/child để bảo đảm thứ tự nạp mong muốn.
- [ ] Có test trùng resource giữa parent/child để kiểm tra thứ tự và resource URL thực tế.
- [ ] `Class.getClassLoader()` được dùng để xác nhận defining loader, không chỉ dựa vào loader name.
- [ ] Có test casting qua public interface chung và khi interface bị copy trùng.
- [ ] Có test `ServiceLoader` / TCCL nếu framework sử dụng.
- [ ] Có test `ClassNotFoundException`, `NoClassDefFoundError`, `NoSuchMethodError` theo những tình huống có ý nghĩa.
- [ ] Plugin không còn giữ thread, callback, static cache hay file handles sau khi dừng.
- [ ] Classloader diagnostics không ghi nội dung secrets hoặc gây logging overload.
- [ ] Nếu dùng JPMS, module layer/readability/exports/opens được đánh giá riêng.

## 14. Những ngộ nhận cần tránh

1. **“Child-first luôn tốt hơn vì cô lập mạnh hơn.”** Sai: cô lập trùng API làm cast/linkage hỏng và có chi phí vận hành.
2. **“Parent-first luôn bảo đảm tương thích.”** Sai: parent có thể trả dependency version không đủ binary API mà plugin cần.
3. **“Cùng tên class tức cùng type.”** Sai: cần cùng defining loader.
4. **“Một loader gọi `loadClass` thì nó phải là defining loader.”** Sai: delegation có thể trả class của parent.
5. **“Child-first cho class tự làm resource child-first.”** Sai: override `loadClass` không tự thay `getResource`.
6. **“Đặt cùng tên loader làm hai loader giống nhau.”** Sai: tên là nhãn, không phải runtime identity.
7. **“Nhiều classloaders tạo sandbox bảo mật.”** Sai: JVM process vẫn có quyền truy cập chung nếu không có cách ly riêng.
8. **“Chỉ cần đóng URLClassLoader là class unload ngay.”** Sai: còn references và GC.
9. **“Một JAR có `module-info.class` sẽ tự thành named module trong URLClassLoader.”** Sai: module resolution là cơ chế khác.
10. **“`ClassCastException` chỉ có thể do hai tên class khác nhau.”** Sai: duplicate defining loaders có thể gây thông báo cast X sang X.
11. **“`NoClassDefFoundError` luôn đồng nghĩa không tìm thấy file.”** Sai: còn class initialization thất bại và những nguyên nhân linkage khác.
12. **“TCCL luôn giống system class loader.”** Sai: TCCL thay đổi theo thread/framework.
13. **“`identityHashCode` là ID loader duy nhất toàn hệ thống.”** Sai: có thể trùng và không ổn định giữa tiến trình.
14. **“Cứ tắt logging sẽ cải thiện chẩn đoán.”** Sai: cần giữ khả năng truy vết ở mức phù hợp với overhead và bảo mật.
15. **“Shading chỉ đổi tên file JAR là đủ.”** Sai: relocation thực sự thay đổi package/binary names; phải kiểm tra service/resource metadata đi kèm.

## 15. Câu hỏi tự kiểm tra

**Câu 1.** Plugin A muốn dùng bản `lib.Parser` mới, host đã có `lib.Parser` cũ. Parent-first sẽ chọn bản nào nếu parent tìm được?

**Đáp án:** Bản do parent định nghĩa, có nguy cơ thiếu method mà plugin cần. Có thể xem xét child-first có chọn lọc cho `lib.Parser`, shading/relocation hoặc đồng bộ dependency version.

**Câu 2.** Vì sao child-first toàn phần có thể gây `ClassCastException` khi ép plugin về interface `api.Plugin` của host?

**Đáp án:** Vì child có thể tự định nghĩa thêm một `api.Plugin` với defining loader khác; runtime interface đó không giống interface do host dùng.

**Câu 3.** Hai child loaders có tên cùng `"plugin"` và cùng bytecode. Chúng có một runtime class chung không?

**Đáp án:** Không nếu từng loader tự định nghĩa kiểu; tên loader và bytecode giống không làm runtime identity trùng nhau.

**Câu 4.** Class policy đã child-first cho `plugin.impl.`. Tại sao `loader.getResource("plugin/config.txt")` vẫn có thể trả resource từ parent?

**Đáp án:** Vì `getResource` của ClassLoader có delegation policy riêng, mặc định tìm parent trước. Override `loadClass` không thay đổi nó.

**Câu 5.** Khi điều tra `NoSuchMethodError`, vì sao phải xem defining loader và CodeSource?

**Đáp án:** Để biết class thực tế được nạp từ version/JAR nào, không chỉ dựa vào dependency tree lúc build hoặc vị trí URL dự kiến.

**Câu 6.** `URLClassLoader.close()` đủ để unload một plugin không?

**Đáp án:** Không. Phải dừng plugin tasks, giải phóng references, reset TCCL, đóng handles; JVM chỉ unload khi điều kiện cho phép.

**Câu 7.** Nếu cần kiểm thử hai phiên bản implementation khác nhau nhưng vẫn cast object về chung `Plugin`, đặt `Plugin` ở đâu?

**Đáp án:** Trong loader cha dùng chung, và cấu hình child loaders delegate việc nạp `api.Plugin` về parent.

## 16. Khung ghi nhớ

~~~text
                    Chọn chính sách class loading
                                |
                    Shared API, DTO, callbacks?
                         /               \
                       Có                 Không
                        |                   |
                Định nghĩa ở parent   Có cần dependency
                        |              khác version?
                 Parent-first cho API     /       \
                                        Không       Có
                                         |          |
                                   Parent-first   Child-first
                                   thường đủ      chọn lọc
                                                      |
                                            kiểm tra type identity
                                                      |
                                            kiểm tra resource policy
                                                      |
                                            log defining loader / URL
                                                      |
                                            quản lý lifecycle / TCCL
~~~

**Kết luận:** Hãy **chia sẻ những kiểu thật sự là hợp đồng giao tiếp**, **cô lập những chi tiết triển khai thật sự cần khác version**, và **lưu đủ dấu vết để chứng minh JVM đã định nghĩa class nào từ đâu**. Đó là điểm cân bằng có thể bảo trì giữa isolation, compatibility và diagnosability trong Java class loading.

### Tài liệu chính thức

- [Java SE 17 — ClassLoader: loadClass, getResource, getResources](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html)
- [Java SE 17 — URLClassLoader: findClass, findResource, close](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html)
- [JVMS §5.3 — Creation and Loading; defining/initiating loaders](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3)
- [JLS §13 — Binary Compatibility](https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html)
- [Java SE 17 — ServiceLoader API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ServiceLoader.html)


</details>

- [Quay lại đầu trang](#back-to-top)
