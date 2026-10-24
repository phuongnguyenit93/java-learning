<a id="back-to-top"></a>

# Package và nguồn mã

## Menu
- [definePackage, getDefinedPackage và getDefinedPackages](#packages-and-code-sources-001)
- [Package cùng tên trong nhiều loader](#packages-and-code-sources-002)
- [Package sealing và thông tin manifest](#packages-and-code-sources-003)
- [CodeSource và vị trí nguồn bytecode](#packages-and-code-sources-004)
- [ProtectionDomain trong các biến thể defineClass](#packages-and-code-sources-005)
- [SecureClassLoader và vai trò lịch sử của quyền truy cập](#packages-and-code-sources-006)
- [Chữ ký JAR, chứng thư và tính nhất quán trong cùng package](#packages-and-code-sources-007)
- [Giới hạn định nghĩa lớp trong package java.*](#packages-and-code-sources-008)

## <a id="packages-and-code-sources-001">definePackage, getDefinedPackage và getDefinedPackages</a>

<details>
<summary>Xem chi tiết</summary>

# definePackage, getDefinedPackage và getDefinedPackages trong Java ClassLoader

## 1. Ba API này giải quyết điều gì?

Khi Java nạp các class, một tên như **com.example.plugin.Widget** có hai phần thường dễ nhận thấy:

- **com.example.plugin** là tên package của class.
- **Widget** là tên class bên trong package đó.

Tuy nhiên, package tại runtime **không đơn thuần chỉ là phần đầu của tên class**. JVM và Java API còn gắn package với **class loader đã định nghĩa class**, thông tin phiên bản, nhà cung cấp, package annotations và quy tắc **sealing** nếu có.

Ba API của **java.lang.ClassLoader** giúp làm việc với metadata package do loader định nghĩa:

- **definePackage(...)**: khai báo một đối tượng **java.lang.Package** với tên và metadata cụ thể trong loader đó.
- **getDefinedPackage(name)**: tìm package **được chính loader này định nghĩa**, không tìm sang parent.
- **getDefinedPackages()**: liệt kê các package **do chính loader này định nghĩa**, không gộp package của các ancestor.

Đây là một phần quan trọng khi xây dựng custom ClassLoader, loader đọc JAR/manifest hoặc runtime plugin, đặc biệt khi bạn cần giữ metadata và package identity nhất quán.

**Câu hỏi cốt lõi:** “Package có tên p trong loader A” có phải là “package p trong loader B” không? Câu trả lời là **không nhất thiết**, vì runtime package gắn với **tên package và defining loader**.

## 2. API và visibility chính xác

Các chữ ký liên quan trong Java 17+:

~~~java
// Thuộc java.lang.ClassLoader:
protected Package definePackage(
        String name,
        String specTitle,
        String specVersion,
        String specVendor,
        String implTitle,
        String implVersion,
        String implVendor,
        java.net.URL sealBase);

public final Package getDefinedPackage(String name);

public final Package[] getDefinedPackages();
~~~

Các đoạn trên là **chữ ký API minh họa**, không phải một class chạy độc lập.

| API | Visibility | Kết quả | Ý nghĩa |
| --- | --- | --- | --- |
| **definePackage(...)** | **protected** | Package mới được định nghĩa | Cần gọi từ ClassLoader subclass hoặc nơi có quyền truy cập protected. |
| **getDefinedPackage(name)** | **public final** | Package hoặc null | Chỉ kiểm tra metadata package đã được định nghĩa **ở loader đang gọi**. |
| **getDefinedPackages()** | **public final** | Package[] | Chỉ lấy các packages **ở loader đang gọi**; mảng rỗng nếu chưa định nghĩa package nào. |
| **getPackage(name)** | **protected, deprecated từ Java 9** | Package hoặc null | Có thể tìm package của loader và những ancestor; dễ nhầm ownership. |
| **getPackages()** | **protected** | Package[] | Có thể gồm packages do loader và các ancestor định nghĩa, thậm chí trùng tên. |

**Lưu ý:** **final** ở các phương thức getDefined... có nghĩa subclass không thể override chúng để làm lệch hợp đồng. **definePackage** là protected nên ứng dụng muốn đặt metadata tùy chỉnh thường tạo subclass và gọi nó qua một wrapper.

### 2.1 Chuỗi name là gì?

Với **definePackage/getDefinedPackage**, đối số name là **tên package dạng dấu chấm**, ví dụ:

~~~text
com.example.plugin
org.acme.services
io.sample.internal
~~~

Không dùng đường dẫn resource **com/example/plugin**, không thêm đuôi **.class**, không truyền toàn bộ binary name của một class **com.example.plugin.Widget** khi muốn tìm package **com.example.plugin**.

Đối với ba phương thức này, tên package phải được hiểu tách biệt với **class binary name** mà **loadClass/defineClass** nhận.

## 3. Package nguồn Java và runtime package không hoàn toàn đồng nhất

Trong source:

~~~java
package shop.billing;

public class Invoice {
}
~~~

Khai báo **package shop.billing** giúp compiler tổ chức namespace và kiểm tra quy tắc truy cập, nhưng khi class được JVM định nghĩa, thông tin runtime còn gắn với loader.

~~~text
Source:
  package shop.billing;

Binary class:
  shop.billing.Invoice

Runtime khi định nghĩa:
  (tên package = shop.billing, defining ClassLoader = LoaderA)
~~~

Nếu một **LoaderB** khác định nghĩa class khác cũng thuộc **shop.billing**, các class của hai loader **không tự nằm trong cùng một runtime package** cho kiểm tra quyền truy cập package-private.

Theo JVM Specification, runtime package được nhận diện bằng **tên package và defining loader**. Vì vậy:

~~~text
("shop.billing", LoaderA) != ("shop.billing", LoaderB)
~~~

Dù chuỗi tên package giống nhau, metadata Package có thể khác: phiên bản, vendor, sealing base và annotations.

**Đừng suy luận rằng getDefinedPackage(name) sẽ đi tìm trong classpath hoặc nạp mọi class thuộc package.** Nó chỉ tra các package đã được loader định nghĩa tới thời điểm gọi.

## 4. Khi nào một Package được tạo?

Có hai con đường quan trọng.

### 4.1 Định nghĩa chủ động qua definePackage

Một custom loader có thể **definePackage** trước khi định nghĩa class đầu tiên trong package, nhằm gắn metadata như specification version hoặc code source seal base.

~~~text
Loader riêng
    |
    +-- definePackage("shop.billing", spec/impl/seal metadata)
    |
    +-- findClass("shop.billing.Invoice")
           |
           +-- read bytes
           +-- defineClass(...)
~~~

**Package đã có thể tồn tại trong loader trước cả khi loader định nghĩa một class thuộc package đó.** Định nghĩa Package không đồng nghĩa đã load hoặc initialize class.

### 4.2 Tự động định nghĩa khi defineClass

Javadoc **ClassLoader.defineClass** nêu rằng nếu class mới thuộc một package chưa được loader định nghĩa, **defineClass** sẽ định nghĩa package tương ứng trong loader đó.

Khi package được tạo tự động, metadata được xác định theo quy tắc của **java.lang.Package**. Ví dụ với class trong **unnamed module**, package tự tạo thông thường **không sealed** và các trường specification/implementation title, version, vendor **không được chỉ định**, nên getter có thể trả null.

Điểm quan trọng:

- Nếu muốn **metadata tùy chỉnh**, loader nên gọi **definePackage trước defineClass** cho class đầu tiên trong package.
- Nếu package đã được định nghĩa tự động, một lời gọi definePackage với cùng tên trong cùng loader **không thể dùng để bổ sung hoặc sửa metadata**.
- Khi package đã được tạo, metadata Package **không thể bị định nghĩa lại hoặc chỉnh sửa** qua definePackage.

### 4.3 Package không tự xuất hiện chỉ vì một dòng source

Có thể project chứa hàng trăm file **package shop.billing** nhưng loader cụ thể chưa define bất kỳ class nào thuộc package ấy, cũng chưa gọi definePackage. Khi đó:

~~~java
loader.getDefinedPackage("shop.billing")
~~~

có thể trả **null**, dù bạn nhìn thấy class files trong JAR hoặc IDE.

Ngược lại, **definePackage** có thể làm getDefinedPackage trả non-null dù chưa có class thuộc package đó được nạp.

## 5. definePackage: tám tham số không được nhầm lẫn

Chữ ký đầy đủ:

~~~java
definePackage(
    name,
    specTitle,
    specVersion,
    specVendor,
    implTitle,
    implVersion,
    implVendor,
    sealBase
);
~~~

| Tham số | Vai trò | Ví dụ |
| --- | --- | --- |
| **name** | Tên package có dấu chấm. | **shop.billing** |
| **specTitle** | Tên specification mà package triển khai. | **Billing API** |
| **specVersion** | Phiên bản specification. | **1.2.3** |
| **specVendor** | Tổ chức sở hữu specification. | **Acme Standards** |
| **implTitle** | Tên implementation cụ thể. | **Billing Engine** |
| **implVersion** | Phiên bản implementation. | **build-7** |
| **implVendor** | Tổ chức cung cấp implementation. | **Acme Software** |
| **sealBase** | URL code source mà package được sealed theo đó; null tức không sealed. | **file:/opt/plugins/a.jar** |

Tất cả các thuộc tính specification/implementation kể trên là metadata. Các giá trị như vendor/title thường có thể null khi không cung cấp.

**specVersion và implVersion không cùng mục đích:** một package có thể tuân theo specification **1.2** nhưng implementation được phát hành dưới version **2026.10**. API **Package.isCompatibleWith(version)** liên quan **specification version**, không phải implementation version.

### 5.1 Các getter ở java.lang.Package

Một Package đã được định nghĩa hỗ trợ:

~~~java
p.getName();
p.getSpecificationTitle();
p.getSpecificationVersion();
p.getSpecificationVendor();
p.getImplementationTitle();
p.getImplementationVersion();
p.getImplementationVendor();
p.isSealed();
p.isSealed(url);
p.isCompatibleWith("1.2");
~~~

Đây là trích đoạn lệnh minh họa, không phải các expression statements hợp lệ đứng độc lập trong một method Java. Khi chạy, hãy dùng chúng trong println/assignment hoặc method khác.

**isCompatibleWith** so sánh version specification theo các thành phần số ngăn bởi dấu chấm. Nếu specification version chưa có hoặc chuỗi không hợp lệ, có thể phát sinh **NumberFormatException**. Không dùng phiên bản dạng **build-7** để kiểm tra API này nếu format không thỏa.

## 6. Ví dụ Java 17+ số 1: definePackage thủ công, đọc metadata, ngăn định nghĩa trùng

Tạo file **PackageMetadataDemo.java**:

~~~java
import java.net.URI;
import java.net.URL;

public class PackageMetadataDemo {
    static final class DemoLoader extends ClassLoader {
        DemoLoader(ClassLoader parent) {
            super(parent);
        }

        Package register(String name, URL sealBase) {
            return definePackage(
                    name,
                    "Billing API", "1.2.3", "Acme Standards",
                    "Billing Engine", "build-7", "Acme Software",
                    sealBase);
        }
    }

    public static void main(String[] args) throws Exception {
        DemoLoader loader = new DemoLoader(
                ClassLoader.getPlatformClassLoader());

        System.out.println("count before = "
                + loader.getDefinedPackages().length);

        URL jarA = URI.create("file:/opt/plugins/a.jar").toURL();
        URL jarB = URI.create("file:/opt/plugins/b.jar").toURL();

        Package p = loader.register("shop.billing", jarA);

        System.out.println("name = " + p.getName());
        System.out.println("spec version = "
                + p.getSpecificationVersion());
        System.out.println("implementation = "
                + p.getImplementationTitle());
        System.out.println("sealed = " + p.isSealed());
        System.out.println("sealed at jar A = " + p.isSealed(jarA));
        System.out.println("sealed at jar B = " + p.isSealed(jarB));
        System.out.println("compatible with 1.2 = "
                + p.isCompatibleWith("1.2"));
        System.out.println("same Package = "
                + (p == loader.getDefinedPackage("shop.billing")));
        System.out.println("missing Package = "
                + (loader.getDefinedPackage("shop.missing") == null));
        System.out.println("count after = "
                + loader.getDefinedPackages().length);

        try {
            loader.register("shop.billing", jarA);
            System.out.println("duplicate allowed");
        } catch (IllegalArgumentException expected) {
            System.out.println("duplicate = IllegalArgumentException");
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac PackageMetadataDemo.java
java PackageMetadataDemo
~~~

Stdout dự kiến trên Java 17+:

~~~text
count before = 0
name = shop.billing
spec version = 1.2.3
implementation = Billing Engine
sealed = true
sealed at jar A = true
sealed at jar B = false
compatible with 1.2 = true
same Package = true
missing Package = true
count after = 1
duplicate = IllegalArgumentException
~~~

### 6.1 Giải thích

**DemoLoader** là loader mới chưa tự define class/package nào. Class chứa mã DemoLoader thuộc loader của chương trình gọi, không phải do object DemoLoader vừa tạo định nghĩa. Vì vậy trước lời register đầu tiên, **getDefinedPackages().length** ở loader đó là 0.

**register** gọi protected definePackage từ subclass, cho phép code main thêm package với metadata.

**isSealed** chỉ kiểm tra metadata Package đã lưu. Đối tượng URL jarA là seal base nên isSealed(jarA) true; URL khác jarB false.

**getDefinedPackage("shop.billing")** trả đúng object Package được tạo trước đó. Đọc getter không làm loader nạp class Invoice hoặc tự động mở JAR.

**register cùng tên lần nữa** ném **IllegalArgumentException**, vì một loader không thể definePackage trùng tên, bất kể metadata mới giống hay khác. Muốn thay metadata trong cùng loader sau khi package đã được tạo là quá muộn.

### 6.2 Cảnh báo: metadata sealing không tự làm loader an toàn

Bài demo chỉ gọi **definePackage**, chưa define class từ JAR. **Package.isSealed() == true** cho biết metadata nói package sealed. Nó **không chứng minh mọi custom ClassLoader sẽ tự động kiểm tra URL nguồn JAR** với sealBase trong mọi đường defineClass. Loader triển khai việc đọc JAR/manifest và định nghĩa class phải tôn trọng kiểm tra sealing thích hợp. Các loader JAR tiêu chuẩn có logic tương ứng.

Đừng sử dụng definePackage như công cụ sandbox đối với bytecode không tin cậy.

## 7. getDefinedPackage(name): chỉ nhìn package của chính loader

Chữ ký:

~~~java
public final Package getDefinedPackage(String name);
~~~

Đặc điểm quan trọng:

- Trả **Package** do **loader đang gọi** đã định nghĩa.
- Trả **null** nếu loader này chưa định nghĩa package có tên tương ứng.
- Truyền **null** làm name gây **NullPointerException**.
- **Không** tự nạp class, tìm resource hay hỏi parent để lấy Package.
- Không cần package đó hiện có instance object hay class đang initialized.
- Nếu package đã được define trước đó, trả metadata hiện có của loader.

Ví dụ:

~~~java
Package own = childLoader.getDefinedPackage("api.common");
~~~

Nếu class **api.common.Service** được parent loader định nghĩa và child chỉ delegate để dùng class ấy, phương thức trên vẫn có thể trả **null**: package thuộc **parent**, không thuộc child.

**Đừng đánh đồng initiating loader với defining loader.** Child có thể gọi loadClass và nhận Class do parent định nghĩa; điều đó không tạo Package tương ứng trong child.

## 8. getDefinedPackages(): liệt kê chính xác local definitions

Chữ ký:

~~~java
public final Package[] getDefinedPackages();
~~~

Hợp đồng:

- Trả về **một mảng** các đối tượng Package do loader này định nghĩa.
- **Không** tự gộp package của parent hoặc ancestor.
- Không có **hai Package cùng tên** trong mảng trả về của một loader.
- Trả **mảng độ dài 0** nếu loader chưa define package nào.
- Không nên dựa vào **thứ tự** của các phần tử vì API không cam kết ordering phù hợp để sắp xếp nghiệp vụ.

Ví dụ xử lý kết quả:

~~~java
Package[] ownPackages = loader.getDefinedPackages();

for (Package p : ownPackages) {
    System.out.println(p.getName()
            + " : " + p.getSpecificationVersion());
}
~~~

Đây là snippet đặt trong method có biến loader. Nếu cần xuất báo cáo ổn định, hãy **sort bằng getName()** trước khi in; không suy diễn rằng thứ tự được trả bằng thứ tự đã load hoặc thứ tự file JAR.

### 8.1 getDefinedPackages không có nghĩa là toàn bộ packages tồn tại trong JVM

Một Java process có thể chứa hàng trăm loader, các modules và các JAR khác nhau. **getDefinedPackages trên một loader chỉ là góc nhìn local**, không phải registry toàn JVM. Để kiểm tra class thực sự đến từ loader nào, dùng **clazz.getClassLoader()** kết hợp metadata/package của Class phù hợp.

## 9. Đối chiếu với getPackage và getPackages cũ

Hai API khác có thể gây nhầm:

~~~java
// Trong ClassLoader subclass:
protected Package getPackage(String name);
protected Package[] getPackages();
~~~

**getPackage(name)** được **deprecated từ Java 9**. Nó tìm trong chính loader và sau đó tìm ancestor nếu chưa thấy. Trong các loader có thể định nghĩa cùng tên package, hành vi trả package từ parent có thể làm metadata/annotations không phản ánh package do child sẽ định nghĩa.

**getPackages()** cũng lấy packages được định nghĩa bởi loader và ancestors. Mảng kết quả **có thể có hai Package cùng tên** nếu parent và child mỗi loader tự define tên đó.

Điều này khác với getDefinedPackages: mảng local không chứa tên trùng.

| API | Phạm vi | Có thể thấy package parent? | Ghi chú |
| --- | --- | --- | --- |
| **getDefinedPackage("p")** | Chỉ loader hiện tại | Không | Public final; ưu tiên khi cần package ownership chính xác. |
| **getDefinedPackages()** | Chỉ loader hiện tại | Không | Public final; không trùng tên trong một loader. |
| **getPackage("p")** | Loader và ancestors | Có | Protected và deprecated từ Java 9. |
| **getPackages()** | Loader và ancestors | Có | Protected; có thể trả nhiều Package tên giống nhưng khác owner. |

**Không nên dùng getPackage để quyết định rằng “package đã được child định nghĩa”.** Nó có thể chỉ phát hiện package ở parent.

## 10. Ví dụ Java 17+ số 2: package cùng tên ở parent và child

Ví dụ tự định nghĩa metadata Package ở hai loader khác nhau, không cần tạo class files, nhằm chứng minh phạm vi các API.

Tạo file **PackageScopeDemo.java**:

~~~java
public class PackageScopeDemo {
    static final class PackageLoader extends ClassLoader {
        PackageLoader(ClassLoader parent) {
            super(parent);
        }

        Package add(String name, String version) {
            return definePackage(
                    name, "Shared API", version, "Acme",
                    "Runtime", "1", "Acme", null);
        }

        @SuppressWarnings("deprecation")
        Package legacyLookup(String name) {
            return getPackage(name);
        }

        int visiblePackageMatches(String name) {
            int count = 0;
            for (Package p : getPackages()) {
                if (name.equals(p.getName())) {
                    count++;
                }
            }
            return count;
        }
    }

    public static void main(String[] args) {
        PackageLoader parent = new PackageLoader(
                ClassLoader.getPlatformClassLoader());
        PackageLoader child = new PackageLoader(parent);

        Package parentPackage = parent.add("demo.shared", "1.0");

        System.out.println("child local before = "
                + (child.getDefinedPackage("demo.shared") == null));
        System.out.println("legacy found parent = "
                + (child.legacyLookup("demo.shared") == parentPackage));
        System.out.println("visible matches before = "
                + child.visiblePackageMatches("demo.shared"));

        Package childPackage = child.add("demo.shared", "2.0");

        System.out.println("child version = "
                + child.getDefinedPackage("demo.shared")
                        .getSpecificationVersion());
        System.out.println("same Package object = "
                + (childPackage == parentPackage));
        System.out.println("visible matches after = "
                + child.visiblePackageMatches("demo.shared"));
        System.out.println("child local count = "
                + child.getDefinedPackages().length);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac PackageScopeDemo.java
java PackageScopeDemo
~~~

Stdout dự kiến:

~~~text
child local before = true
legacy found parent = true
visible matches before = 1
child version = 2.0
same Package object = false
visible matches after = 2
child local count = 1
~~~

### 10.1 Vì sao cùng package name không gây IllegalArgumentException?

Quy tắc không cho definePackage trùng tên áp dụng **trong cùng một loader**. Parent và child là **hai đối tượng ClassLoader khác nhau**, vì vậy mỗi loader có thể định nghĩa Package cùng tên **demo.shared**.

Trong ví dụ:

~~~text
PackageLoader parent
    +-- demo.shared, specVersion 1.0

PackageLoader child (parent là loader phía trên)
    +-- demo.shared, specVersion 2.0
~~~

**child.getDefinedPackage("demo.shared")** ban đầu null dù parent đã có package. **legacyLookup** tìm lên parent nên thấy Package cũ. Sau khi child tự define, local Package được trả về, và getPackages có thể đưa ra cả hai Package cùng tên.

**Thực tế quan trọng:** demo chỉ định nghĩa Package metadata, chưa định nghĩa class nào dưới hai package đó. Việc Java cho hai loader giữ Package cùng tên **không có nghĩa bạn nên chia shared interface/DTO qua hai defining loaders**. Runtime package identity và class identity có thể trở thành vấn đề khi code cần tương tác qua loader boundary.

## 11. Ví dụ Java 17+ số 3: defineClass tự tạo Package khi chưa được define

Bây giờ quan sát một package xuất hiện **tự động**, không gọi definePackage bằng tay.

Cấu trúc:

~~~text
project/
├── AutoPackageDemo.java
└── plugin-src/
    └── lab/
        └── auto/
            └── Marker.java
~~~

**plugin-src/lab/auto/Marker.java**:

~~~java
package lab.auto;

public class Marker {
    public String value() {
        return "ready";
    }
}
~~~

**AutoPackageDemo.java**:

~~~java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AutoPackageDemo {
    static final class ByteLoader extends ClassLoader {
        private final Path root;

        ByteLoader(Path root) {
            super(ClassLoader.getPlatformClassLoader());
            this.root = root;
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            try {
                byte[] bytes = Files.readAllBytes(
                        root.resolve(name.replace('.', '/') + ".class"));
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException ex) {
                throw new ClassNotFoundException(name, ex);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        ByteLoader loader = new ByteLoader(Path.of("plugin-out"));

        System.out.println("before = "
                + (loader.getDefinedPackage("lab.auto") == null));

        Class<?> type = loader.loadClass("lab.auto.Marker");
        Package p = loader.getDefinedPackage("lab.auto");

        System.out.println("after name = " + p.getName());
        System.out.println("spec version null = "
                + (p.getSpecificationVersion() == null));
        System.out.println("sealed = " + p.isSealed());
        System.out.println("defined by loader = "
                + (type.getClassLoader() == loader));
        System.out.println("defined count = "
                + loader.getDefinedPackages().length);

        Class<?> base = loader.loadClass("java.lang.String");
        System.out.println("bootstrap class = "
                + (base.getClassLoader() == null));
        System.out.println("java.lang locally defined = "
                + (loader.getDefinedPackage("java.lang") != null));
    }
}
~~~

Biên dịch từ thư mục **project**:

~~~bash
javac -d plugin-out plugin-src/lab/auto/Marker.java
javac AutoPackageDemo.java
java AutoPackageDemo
~~~

Stdout dự kiến trong classpath và loader setup này:

~~~text
before = true
after name = lab.auto
spec version null = true
sealed = false
defined by loader = true
defined count = 1
bootstrap class = true
java.lang locally defined = false
~~~

### 11.1 Phân tích

- Lúc đầu ByteLoader chưa define class hoặc package lab.auto, nên getDefinedPackage trả null.
- Parent (platform loader) không thấy lab.auto.Marker trong plugin-out; loader riêng gọi findClass, đọc bytes rồi defineClass.
- Vì chưa có package lab.auto trong loader, **defineClass tự tạo Package** với metadata mặc định cho unnamed module.
- **getDefinedPackages().length == 1** phản ánh package local mới được tạo.
- Loader có thể initiate class java.lang.String bằng delegation nhưng **không định nghĩa** package java.lang. Vì vậy getDefinedPackage("java.lang") vẫn null.

**Lưu ý môi trường:** Không đưa plugin-out lên một classpath mà parent trong ví dụ thấy được lab.auto.Marker; nếu không, lớp có thể do parent định nghĩa và output khác.

### 11.2 Sửa metadata sau khi tự động định nghĩa có được không?

Nếu sau **loader.loadClass("lab.auto.Marker")** bạn gọi **definePackage("lab.auto", ...)** qua subclass, thao tác sẽ ném IllegalArgumentException vì package local đã có.

Muốn set version/seal metadata của package trong custom loader, hãy thực hiện trước class đầu tiên được define ở package đó. Nếu nạp từ JAR tiêu chuẩn, sử dụng loader hiểu manifest thay vì tự làm sai metadata.

## 12. Package sealing là gì?

**Package sealing** là quy tắc package/JAR có thể giới hạn nguồn code cho các class thuộc cùng package. Khi một package sealed với một code source URL, các class được loader nạp vào package đó phải tuân thủ nguồn phù hợp theo hợp đồng loader/JAR.

~~~text
Package demo.internal
    sealed với URL: file:/plugins/a.jar
            |
            +-- class từ a.jar: phù hợp
            |
            +-- class từ b.jar: sealing violation nếu loader enforce
~~~

JAR Manifest có thể chứa:

~~~text
Manifest-Version: 1.0
Sealed: true
~~~

Hoặc override sealing cho package qua manifest entry, ví dụ:

~~~text
Name: demo/internal/
Sealed: true
~~~

**Package.isSealed()** cho biết metadata package sealed hay không; **Package.isSealed(URL)** kiểm tra có sealed đối với code source URL được truyền không.

### 12.1 Phân biệt package sealing và sealed classes của Java 17

Đây là hai cơ chế khác nhau:

| Package sealing | Sealed class/interface |
| --- | --- |
| Thuộc package/JAR và code source, thể hiện ở Package metadata. | Thuộc hệ kiểu Java, dùng **sealed**, **permits** và subclass restrictions. |
| Gắn với nguồn định nghĩa các class trong package. | Kiểm soát những class được phép extends/implements class/interface. |
| Thông thường liên quan loader đọc JAR manifest. | Được compiler/JVM kiểm tra qua định nghĩa kiểu. |
| API tiêu biểu: Package.isSealed(). | Cú pháp sealed class, Class.isSealed(), Class.getPermittedSubclasses(). |

Một package sealed không có nghĩa mọi class trong nó là sealed class; một sealed class có thể nằm trong package không sealed.

### 12.2 Cảnh báo khi tự xây custom loader

**definePackage** gắn metadata sealing, nhưng việc **enforce sealing trên nhiều code sources** cần loader nạp JAR áp dụng đúng kiểm tra. **URLClassLoader/JAR class loaders tiêu chuẩn** có logic đọc manifest và kiểm tra sealing. Đừng chỉ gọi definePackage rồi kết luận mọi nguồn bytes đã được xác minh.

Nếu bạn tự đọc bytes và gọi defineClass, cần xem xét code source, protection domain, chứng chỉ, package rules và kiểm tra sealing phù hợp. Đừng bắt SecurityException rồi coi như ClassNotFoundException.

## 13. Metadata manifest và thứ tự tải class

Trong JAR, metadata package thường được đọc từ **META-INF/MANIFEST.MF**. Các thuộc tính như Specification-Title, Specification-Version, Implementation-Version và Sealed có thể xuất hiện ở main attributes hoặc sections cho từng package.

Ví dụ mô tả manifest:

~~~text
Manifest-Version: 1.0
Implementation-Title: Billing Engine
Implementation-Version: 3.4.1

Name: shop/billing/
Specification-Title: Billing API
Specification-Version: 1.2.3
Sealed: true
~~~

Nếu nhiều JAR chứa class trong cùng package do một loader define, **thứ tự class/package được define đầu tiên** có thể ảnh hưởng metadata package được ghi nhận. Đó là lý do các plugin cần tránh split packages ngoài ý muốn và không trông cậy getDefinedPackage như bảng hợp nhất metadata nhiều JAR.

**Package metadata không được tự động cập nhật** khi class thứ hai trong cùng package đến từ JAR khác. Nếu metadata đã tạo trước đó, loader phải tôn trọng trạng thái package hiện hữu và kiểm tra sealing phù hợp.

### 13.1 Đừng suy từ Package ra chính xác JAR chứa mọi class

Package là metadata của runtime package, **không phải bản đồ cho từng class file** hoặc một lệnh quét JAR. Để điều tra Class được định nghĩa từ đâu, kiểm tra defining loader, **ProtectionDomain/CodeSource** khi có và các công cụ logging/JAR listing phù hợp.

## 14. Named modules có khác unnamed module không?

Từ Java 9, module system ảnh hưởng cách package được tạo:

- Với **classes trong unnamed module**, một Package tự động được define thường không sealed và không có specification/implementation metadata trừ khi loader đặt chủ động.
- Với **classes trong named module**, Package có quy tắc tự động riêng; package có thể được sealed theo module location nếu biết được, metadata specification/implementation title/version/vendor thường unspecified.
- Javadoc **khuyến nghị mạnh không gọi definePackage thủ công cho packages của named modules**, mà để việc defineClass tạo package theo module rules.
- Nếu vẫn có lý do thực sự phải define thủ công, phải bảo đảm các Package objects trong module có properties nhất quán với quy tắc Package; ví dụ không được gán sai seal base.

**Đừng coi tên module thay thế defining loader trong mọi khái niệm.** Module membership còn ảnh hưởng exports/opens/readability, trong khi package identity runtime vẫn liên hệ với loader, và Package API diễn tả metadata theo quy tắc của class/module tương ứng.

## 15. Package.getPackage và Package.getPackages có nên dùng không?

Một số tài liệu cũ còn đề cập static methods trên java.lang.Package:

~~~java
Package.getPackage("shop.billing");
Package.getPackages();
~~~

Các API legacy này có ngữ nghĩa theo loader của caller và có thể gây nhầm trong môi trường nhiều loaders. Từ Java 9 chúng được **deprecated** để hướng sang những API phản ánh owner rõ hơn.

Khi đã biết loader mà bạn đang khảo sát, ưu tiên:

~~~java
ClassLoader loader = SomeClass.class.getClassLoader();
Package p = loader.getDefinedPackage("shop.billing");
Package[] own = loader.getDefinedPackages();
~~~

Đây là snippet; cần có SomeClass cụ thể và lưu ý **Class.getClassLoader() có thể trả null** với bootstrap-defined class, nên phải xử lý null trước khi gọi loader methods.

Khi có một Class cụ thể, cũng có thể dùng **SomeClass.class.getPackage()** để nhận Package liên quan tới class đó. Cách này thường sát ý định hơn khi hỏi “package runtime của chính class này có metadata gì?”, thay vì hỏi “loader A đã define package cùng tên chưa?”.

## 16. Quy trình chẩn đoán lỗi

### 16.1 getDefinedPackage(name) trả null dù source có package

Kiểm tra:

1. Bạn đang gọi đúng **defining loader** của class chưa?
2. Package đã được loader này define qua definePackage hoặc defineClass chưa?
3. Class được parent định nghĩa vì parent-first delegation phải không?
4. Chuỗi package name có dùng dấu chấm và đúng case không?
5. Code có nhầm getDefinedPackage với lệnh tìm package trong classpath không?

Nếu class được parent define, getDefinedPackage trên child không tự thấy package của parent.

### 16.2 definePackage ném IllegalArgumentException

Package cùng tên **đã được định nghĩa trong chính loader đó**. Có thể defineClass trước đó đã tự tạo package, hoặc code đã definePackage thủ công.

**Cách khắc phục:** đưa việc đăng ký metadata lên trước defineClass, kiểm tra getDefinedPackage nếu cần, đồng bộ lifecycle khi nhiều threads nạp class. Không retry vô hạn với metadata mới vì package không thể định nghĩa lại.

### 16.3 SecurityException liên quan sealing/certificates

Kiểm tra nguồn code, JAR manifest, sealBase và package đã được định nghĩa trước. Một loader đọc nhiều JAR có split package sealed có thể gặp sealing violation. Đồng thời, ClassLoader.defineClass có ràng buộc certificate set giữa các class trong cùng package.

Đừng xử lý mọi lỗi này như chỉ là ClassNotFoundException; lỗi package integrity cần được điều tra riêng.

### 16.4 Metadata Package khác với manifest bạn vừa xem

Có thể loader đã định nghĩa package từ một JAR hoặc class trước đó. Package không tự viết lại metadata mỗi khi nạp class mới.

Kiểm tra classpath/JAR order, parent delegation, named/unnamed module và loader thực sự định nghĩa class. Không mặc định Manifest-Version là specification version: đó là hai khái niệm riêng.

### 16.5 ClassCastException hoặc IllegalAccessError giữa các loader

Nếu hai loaders định nghĩa class cùng name hoặc package cùng name, chúng có thể không cùng runtime type/package. Để kiểm tra:

~~~java
System.out.println(a.getName().equals(b.getName()));
System.out.println(a == b);
System.out.println(a.getClassLoader());
System.out.println(b.getClassLoader());
~~~

Đoạn trên là snippet có sẵn hai Class objects a và b. Cùng string name nhưng Class khác có thể giải thích lỗi cast. Package-private access còn bị chi phối bởi runtime package identity, không chỉ source package statement.

## 17. Những hiểu nhầm cần tránh

- **“definePackage tạo mọi class của package.”** Sai: nó tạo Package metadata; defineClass mới tạo runtime class.
- **“getDefinedPackages quét hết classpath.”** Sai: chỉ trả các package loader này đã định nghĩa.
- **“Child.getDefinedPackage thấy package của parent.”** Sai: không delegation trong API này.
- **“getPackage và getDefinedPackage giống nhau.”** Sai: getPackage legacy có thể tìm ancestor.
- **“Hai loaders cùng tên package phải dùng chung một Package.”** Sai: mỗi defining loader có thể định nghĩa package cùng tên riêng.
- **“Có Package object nghĩa là đã initialized class nào đó.”** Sai: có thể definePackage trước khi có class.
- **“Package metadata sửa được bằng definePackage gọi lại.”** Sai: trùng tên trong cùng loader ném IllegalArgumentException.
- **“isSealed=true làm custom loader luôn chống mọi nguồn bytecode khác.”** Không đủ: loader phải thực thi quy tắc integrity/sealing đúng.
- **“Sealed package là sealed class.”** Sai: hai cơ chế độc lập.
- **“Một JAR khác vào sau cập nhật version của Package.”** Không: metadata đã xác lập không được tự động ghi đè.
- **“Package owner chỉ là tên package.”** Sai: runtime package còn gắn defining loader.
- **“Named module nên luôn definePackage thủ công.”** Sai: Javadoc khuyên để tự động định nghĩa.

## 18. Bài tập tự kiểm tra

**Câu 1.** Custom loader chưa định nghĩa class nào; gọi definePackage("app.core", ...) có làm getDefinedPackage("app.core") trả non-null không?

**Trả lời:** Có. definePackage tạo Package metadata riêng trong loader trước khi defineClass.

**Câu 2.** Sau khi defineClass tự tạo package app.core, có thể gọi definePackage cùng tên để thêm version không?

**Trả lời:** Không. Package đã được định nghĩa; definePackage trùng tên trong cùng loader ném IllegalArgumentException.

**Câu 3.** Child loader delegate loadClass("api.Service") cho parent và nhận Class của parent. Child.getDefinedPackage("api") có bắt buộc non-null không?

**Trả lời:** Không. Child chỉ initiate class qua delegation, còn parent define class/package.

**Câu 4.** getDefinedPackages và getPackages khác gì trong loader có parent?

**Trả lời:** getDefinedPackages chỉ liệt kê packages do loader hiện tại định nghĩa; getPackages còn có thể liệt kê packages của ancestors và tên trùng.

**Câu 5.** Hai loader A và B cùng definePackage("p.q", ...). Đây có phải hành vi không hợp lệ chỉ vì trùng tên?

**Trả lời:** Không. Quy tắc unique áp dụng trong mỗi loader. Hai runtime packages có thể cùng tên nhưng owner khác nhau.

**Câu 6.** isSealed(url) trả true có nghĩa gì?

**Trả lời:** Package metadata ghi package sealed với sealBase URL tương ứng; việc loader enforce sealing khi nạp code sources cần đúng hợp đồng của implementation.

**Câu 7.** Package.getSpecificationVersion() khác getImplementationVersion() như thế nào?

**Trả lời:** Một cái ghi version của specification, cái còn lại ghi phiên bản implementation cụ thể.

**Câu 8.** getDefinedPackages có đảm bảo thứ tự tương ứng JAR classpath không?

**Trả lời:** Không. Nếu muốn output ổn định, sort theo getName.

**Câu 9.** Vì sao Javadoc không khuyến khích definePackage thủ công trong named modules?

**Trả lời:** Module có quy tắc định nghĩa Package tự động riêng, kể cả sealing base/metadata; tự đặt tay có thể tạo thông tin không nhất quán.

**Câu 10.** Có thể dùng definePackage để sửa quyền package-private giữa hai ClassLoaders có cùng tên package không?

**Trả lời:** Không. Runtime package identity còn dựa trên defining loader; metadata không biến hai runtime packages thành một.

## 19. Ghi nhớ

~~~text
ClassLoader L
    |
    +-- definePackage("p.q", metadata)
    |      -> tạo Package local, trước khi defineClass
    |
    +-- defineClass("p.q.Foo", bytes)
    |      -> tự define Package local nếu chưa có
    |
    +-- getDefinedPackage("p.q")
    |      -> chỉ lấy Package do L định nghĩa
    |
    +-- getDefinedPackages()
           -> toàn bộ Package local của L

Parent packages KHÔNG tự nằm trong getDefinedPackages(L).
Runtime package identity còn gắn với defining loader.
~~~

**Nguyên tắc quan trọng nhất:** dùng **definePackage** để thiết lập metadata trước khi class đầu tiên trong package được define; dùng **getDefinedPackage/getDefinedPackages** để kiểm tra đúng ownership local của loader. Không dùng các API này như trình quét classpath, không đánh đồng package name với runtime package identity, và lưu ý sealing/metadata/module khi thiết kế class loader đọc JAR.

### Nguồn tham khảo chính thức

- **ClassLoader API, Java SE 21** — definePackage/getDefinedPackage/getDefinedPackages/getPackage/getPackages: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html
- **Package API, Java SE 21** — metadata, sealing, versioning và module behavior: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Package.html
- **JAR File Specification, Java SE 21 — Package Sealing:** https://docs.oracle.com/en/java/javase/21/docs/specs/jar/jar.html
- **JVMS §5.3, Creation and Loading:** https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="packages-and-code-sources-002">Package cùng tên trong nhiều loader</a>

<details>
<summary>Xem chi tiết</summary>

# Package cùng tên trong nhiều ClassLoader: danh tính runtime và giới hạn truy cập

## 1. Vấn đề: cùng tên package chưa chắc cùng một package ở JVM

Trong mã nguồn Java, hai class đều khai báo:

```java
package demo.shared;
```

Ta thường gọi chúng là “cùng package”. Điều này đúng khi nói về **tên package ở mức cú pháp nguồn**. Tuy nhiên, ở runtime, Java còn xét **ClassLoader đã định nghĩa** từng class. Nếu `demo.shared.A` được loader L1 định nghĩa còn `demo.shared.B` được loader L2 định nghĩa, chúng **không nằm trong cùng một runtime package**, dù phần chuỗi `demo.shared` hoàn toàn giống nhau.

Đây là nền tảng để hiểu nhiều vấn đề thực tế:

- Vì sao mã **biên dịch được** với truy cập `package-private` nhưng khi chạy lại gặp `IllegalAccessError`?
- Vì sao hai thư viện ở hai plugin có cùng package vẫn không “chia sẻ nội bộ” như hai class do một loader định nghĩa?
- Vì sao hai class cùng **binary name** nhưng khác loader không ép kiểu qua nhau?
- Vì sao `Package.getName()` hoặc `Class.getPackageName()` không đủ để kết luận hai class có cùng quyền truy cập?
- Làm thế nào parent delegation, package sealing, signer và JPMS tác động đến việc phân chia package?

Quy tắc cốt lõi theo Java Virtual Machine Specification (JVMS):

> **Runtime package được xác định bởi cặp (tên package, defining class loader).**

Vì vậy, cùng tên package nhưng khác defining loader là **hai runtime packages khác nhau**. Ngược lại, hai class có tên package giống nhau và được **cùng một defining loader** định nghĩa thì thuộc cùng runtime package theo định nghĩa JVMS; vẫn phải xét thêm các ràng buộc module, bytecode verification và quyền truy cập liên quan.

## 2. Source package, binary name và runtime package

Xem hai class ví dụ (đây là **hai file riêng**, không phải một file chạy độc lập):

```java
// src/alpha/demo/shared/Alpha.java
package demo.shared;

public class Alpha {
    static String internal() {
        return "alpha";
    }
}
```

```java
// src/beta/demo/shared/Beta.java
package demo.shared;

public class Beta {
    public String message() {
        return "beta";
    }
}
```

Cả hai đều có phần khai báo `package demo.shared;`. Định danh tên nhị phân của chúng là:

- `demo.shared.Alpha`
- `demo.shared.Beta`

Nếu được định nghĩa bởi:

```text
L1 định nghĩa: demo.shared.Alpha
L2 định nghĩa: demo.shared.Beta
```

thì JVM xét:

```text
Runtime package của Alpha = ("demo.shared", L1)
Runtime package của Beta  = ("demo.shared", L2)
```

Nếu `L1 != L2` theo danh tính loader thì **hai cặp khác nhau**.

Lưu ý quan trọng: `Thread.currentThread().getContextClassLoader()`, loader ban đầu được gọi qua `loadClass` và **defining loader** không luôn trùng nhau. Khi tính runtime package, phải dùng **loader thực sự định nghĩa class**, lấy bằng `SomeClass.class.getClassLoader()`, không phải bất kỳ loader nào có mặt trong call stack.

### Ba cấp độ danh tính cần phân biệt

| Cấp độ | Dựa vào đâu? | Ví dụ |
| --- | --- | --- |
| Source package | Chuỗi trong khai báo `package` | `demo.shared` |
| Runtime package | **Package name + defining loader** | `("demo.shared", PluginLoaderA)` |
| Runtime class/interface | **Binary name + defining loader** | `("demo.shared.Service", PluginLoaderA)` |

Hai class có thể **khác runtime class** nhưng **cùng runtime package** nếu cùng loader và cùng package name. Hai class cũng có thể **cùng package name** nhưng **khác runtime package** nếu loader khác nhau.

## 3. Package-private và protected: hệ quả nhìn thấy ngay

Java có các mức truy cập: `public`, `protected`, package-private (không khai báo modifier), `private`.

Khi trình biên dịch thấy hai source class cùng khai báo `package demo.shared;`, nó có thể cho phép một class gọi member **package-private** của class kia, nếu các quy tắc compile-time khác được đáp ứng. Nhưng runtime phải bảo vệ quyền truy cập dựa trên **runtime package**.

Giả sử:

```java
// Được biên dịch cho package demo.shared
package demo.shared;

class Secret {
    static String reveal() {
        return "secret";
    }
}
```

```java
package demo.shared;

public class Caller {
    public static String read() {
        return Secret.reveal();
    }
}
```

Nếu `Caller` và `Secret` cùng được định nghĩa bởi một loader thích hợp, lời gọi tới method package-private có thể hợp lệ. Nhưng nếu:

- Loader **Parent** định nghĩa `demo.shared.Secret`;
- Loader **Child** định nghĩa `demo.shared.Caller`;
- Child delegate việc tìm `Secret` cho Parent;

thì tại runtime, caller và target **khác runtime package**, nên việc truy cập class `Secret` vốn package-private bị cấm. JVM có thể báo:

```text
java.lang.IllegalAccessError
```

Điểm mấu chốt: **đường dẫn trong source và việc compiler kiểm tra package name không quyết định cuối cùng quyền truy cập runtime** khi class loader split package thành những runtime packages khác nhau.

**Đối với `protected`:** ngoài những trường hợp truy cập hợp lệ qua quan hệ kế thừa và receiver theo Java/JVM, quyền truy cập “cùng package” cũng đòi hỏi cùng runtime package, không chỉ cùng chuỗi package. Không kết luận mọi lời gọi `protected` qua hai loader đều bị cấm: phải xét đầy đủ quan hệ subclass, điểm gọi và loại member.

**Đối với `public`:** accessibility của một public class/member có thể cho phép truy cập xuyên package và loader, nhưng vẫn còn module exports/readability, type identity, linkage, method signature và các quy tắc khác.

## 4. Parent delegation không “gộp” hai runtime packages

Một mô hình phổ biến:

```text
Host/Application ClassLoader
       ├── demo.shared.Secret
       └── demo.api.Plugin          (SPI dùng chung)
                 ▲
                 │ parent delegation
       Plugin ClassLoader
       ├── demo.shared.Caller
       └── plugin.impl.MyPlugin
```

Khi plugin loader nhận yêu cầu `loadClass("demo.shared.Secret")`, nó có thể hỏi parent; parent trả class đã định nghĩa. Nhưng khi plugin loader tự định nghĩa `demo.shared.Caller` bằng bytecode ở JAR riêng, hai class không trở thành cùng runtime package chỉ vì parent loader và child loader **có quan hệ cha–con**.

Không có quy tắc “hai loader liên quan họ hàng được xem như một package”. Điều kiện là **defining loader phải đúng cùng một đối tượng**.

Tương tự, nếu parent đã định nghĩa cả `Secret` và `Caller`, child chỉ delegate cả hai thì cả hai **vẫn cùng runtime package ở Parent**, vì child không tạo định nghĩa mới. Hãy kiểm tra `Class.getClassLoader()` của kết quả thực tế thay vì suy luận từ object được gọi.

| Tình huống | Defining loader của class A và B | Có cùng runtime package nếu package name giống? |
| --- | --- | --- |
| Hai class đều do parent định nghĩa, child chỉ delegate | Parent + Parent | Có |
| Một class ở parent, một class do child định nghĩa | Parent + Child | Không |
| Hai class đều do một plugin loader định nghĩa | Plugin + Plugin | Có |
| Hai plugin loader độc lập cùng định nghĩa package `com.foo` | Plugin A + Plugin B | Không |
| Hai loader cùng nạp lớp `java.lang.String` qua delegation | Bootstrap + Bootstrap | Có đối với lớp được bootstrap định nghĩa |
| Hai class trong những named modules | Phải xét defining loader và mô hình JPMS | Không suy chỉ từ tên package; named module có ràng buộc riêng |

## 5. Ví dụ Java 17 hoàn chỉnh: compile hợp lệ, runtime không được truy cập

Thí nghiệm bên dưới **tự sinh** bytecode ở hai thư mục riêng, tạo loader Parent và Child rồi thử truy cập một class package-private. Chương trình cũng dùng một loader thứ ba để chỉ ra **cùng binary name, cùng bytecode nhưng hai runtime classes độc lập**.

Thí nghiệm không cần Maven, Gradle, JAR ngoài hoặc truy cập mạng; chỉ cần **JDK 17+** có `javax.tools.JavaCompiler`.

### 5.1. Chương trình RuntimePackageDemo.java

Lưu chính xác mã sau vào file `RuntimePackageDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public class RuntimePackageDemo {
    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Run with a full JDK");
        }
        Path root = Files.createTempDirectory("runtime-package-");
        try {
            Path parentSource = root.resolve("srcParent/demo/shared/Secret.java");
            Path callerSource = root.resolve("srcChild/demo/shared/Caller.java");
            Path tokenSource = root.resolve("srcChild/demo/shared/Token.java");
            Path parentClasses = root.resolve("parentClasses");
            Path childClasses = root.resolve("childClasses");
            Files.createDirectories(parentSource.getParent());
            Files.createDirectories(callerSource.getParent());
            Files.createDirectories(parentClasses);
            Files.createDirectories(childClasses);

            Files.writeString(parentSource, """
                    package demo.shared;
                    class Secret {
                        static String reveal() {
                            return "secret";
                        }
                    }
                    """);
            Files.writeString(callerSource, """
                    package demo.shared;
                    public class Caller {
                        public static String read() {
                            return Secret.reveal();
                        }
                    }
                    """);
            Files.writeString(tokenSource, """
                    package demo.shared;
                    public final class Token {}
                    """);

            int parentExit = compiler.run(null, null, null,
                    "--release", "17", "-d", parentClasses.toString(),
                    parentSource.toString());
            int childExit = compiler.run(null, null, null,
                    "--release", "17", "-classpath", parentClasses.toString(),
                    "-d", childClasses.toString(),
                    callerSource.toString(), tokenSource.toString());
            if (parentExit != 0 || childExit != 0) {
                throw new IllegalStateException("Compilation failed");
            }

            URL parentUrl = parentClasses.toUri().toURL();
            URL childUrl = childClasses.toUri().toURL();
            ClassLoader platform = ClassLoader.getPlatformClassLoader();
            try (URLClassLoader parent = new URLClassLoader(
                        new URL[]{parentUrl}, platform);
                 URLClassLoader child = new URLClassLoader(
                        new URL[]{childUrl}, parent);
                 URLClassLoader sibling = new URLClassLoader(
                        new URL[]{childUrl}, platform)) {

                Class<?> secret = parent.loadClass("demo.shared.Secret");
                Class<?> caller = child.loadClass("demo.shared.Caller");
                Class<?> token1 = child.loadClass("demo.shared.Token");
                Class<?> token2 = sibling.loadClass("demo.shared.Token");

                System.out.println("samePackageName="
                        + secret.getPackageName().equals(caller.getPackageName()));
                System.out.println("differentDefiningLoaders="
                        + (secret.getClassLoader() != caller.getClassLoader()));
                System.out.println("callerDefinedByChild="
                        + (caller.getClassLoader() == child));
                System.out.println("sameBinaryName="
                        + token1.getName().equals(token2.getName()));
                System.out.println("sameRuntimeClass=" + (token1 == token2));

                try {
                    caller.getMethod("read").invoke(null);
                    System.out.println("access=allowed");
                } catch (InvocationTargetException error) {
                    System.out.println("access="
                            + error.getCause().getClass().getSimpleName());
                }
                System.out.println("bootstrap="
                        + (child.loadClass("java.lang.String")
                                .getClassLoader() == null));
            }
        } finally {
            try (Stream<Path> paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
```

Lệnh biên dịch và thực thi:

```bash
javac RuntimePackageDemo.java
java RuntimePackageDemo
```

Stdout xác định với JDK 17+ có compiler đầy đủ, không có can thiệp bất thường từ môi trường:

```text
samePackageName=true
differentDefiningLoaders=true
callerDefinedByChild=true
sameBinaryName=true
sameRuntimeClass=false
access=IllegalAccessError
bootstrap=true
```

### 5.2. Từng dòng nói lên điều gì?

**`samePackageName=true`:** class `Secret` và `Caller` đều tự mô tả tên package là `demo.shared`. Đây là so sánh **chuỗi**, chưa phải so sánh runtime package.

**`differentDefiningLoaders=true`:** `Secret` do loader `parent` định nghĩa; `Caller` do loader `child` định nghĩa. Hai lớp khác runtime package mặc dù có cùng phần tên.

**`callerDefinedByChild=true`:** loader child hỏi parent theo chính sách parent-first; parent không có `Caller` nên child tìm `Caller.class` trong URL riêng và tự định nghĩa.

**`sameBinaryName=true` và `sameRuntimeClass=false`:** hai loader child/sibling đều tự định nghĩa `demo.shared.Token` từ cùng một thư mục chứa bytecode. Không phải cùng runtime class vì **defining loader khác nhau**.

**`access=IllegalAccessError`:** `Caller.read()` được compiler chấp nhận khi compile với `Secret.class` trong compile classpath. Nhưng ở runtime, method này cố truy cập `Secret` package-private do loader parent định nghĩa, trong khi `Caller` do child định nghĩa. JVM từ chối quyền truy cập. Reflection chỉ là cách chương trình chủ động gọi public method `Caller.read()` và quan sát exception gốc qua `InvocationTargetException.getCause()`; nguyên nhân vẫn là **quy tắc JVM về runtime package**, không phải vì dùng reflection khiến quyền truy cập thay đổi.

**`bootstrap=true`:** mặc dù yêu cầu qua child loader, `java.lang.String` do bootstrap loader định nghĩa, nên `getClassLoader() == null`.

### 5.3. Vì sao phải biên dịch Parent trước?

Khi biên dịch `Caller.java`, compiler được cấp:

```text
-classpath <parentClasses>
```

Nhờ đó `javac` nhìn thấy `Secret.class` trong cùng **source package name** và chấp nhận lời gọi package-private. Nhưng compiler chưa quyết định runtime sẽ đặt mỗi class ở defining loader nào. Tại runtime, mảng URL/quan hệ parent–child của thí nghiệm tạo ranh giới khác.

Đây là ví dụ điển hình của quy tắc: **source-level accessibility và runtime accessibility phải cùng nhất quán với cách phân bổ class cho loader**.

### 5.4. Nếu cả hai class do cùng loader định nghĩa thì sao?

Nếu đưa `Secret.class` và `Caller.class` vào **cùng một classpath root** của một loader và để chính loader đó định nghĩa cả hai, thì điều kiện “cùng runtime package” được đáp ứng. Khi không có các lỗi khác, lời gọi package-private có thể thành công.

Nếu parent đã cung cấp **cả** `Secret` và `Caller` thì child delegate cả hai và vẫn dùng chung runtime package của parent.

Không nên sửa lỗi bằng cách “thêm một bản Secret khác vào Child” mà không hiểu binary identity: parent-first có thể vẫn chọn bản ở Parent trước; child-first có thể làm hai bản `Secret` độc lập xuất hiện, gây lỗi khi truyền dữ liệu qua API chung.

## 6. Tên package giống nhau không có nghĩa có thể ép kiểu qua lại

Quy tắc **runtime class identity** còn mạnh hơn quy tắc runtime package: một runtime class được nhận diện bởi cặp **(binary name, defining loader)**.

```text
("demo.shared.Token", ChildLoader)   !=
("demo.shared.Token", SiblingLoader)
```

Ngay cả khi hai class được tạo từ **cùng mảng byte**:

- `token1.getName().equals(token2.getName())` là `true`;
- `token1 == token2` là `false`;
- Hai instance từ hai runtime classes này không thể coi là cùng class bằng phép cast tới class kia;
- `Class.isInstance` hoặc `isAssignableFrom` trên từng định nghĩa giúp kiểm tra quan hệ kiểu runtime thật;
- Những interface cha/API dùng chung có thể tạo cầu nối hợp lệ nếu **cả hai** implementation đều thực thi **chính cùng một định nghĩa interface của host**.

Một thông báo lỗi dạng “`demo.shared.Token cannot be cast to demo.shared.Token`” có thể là hệ quả của hai loader độc lập định nghĩa cùng binary name. Nhìn tên chuỗi trong stack trace không đủ; phải kiểm tra class loader.

### Runtime package khác với “vùng nhìn thấy” của class loader

- Một loader có thể nạp được class của parent nhờ delegation nhưng **không trở thành defining loader** của class đó.
- Một class có thể nhìn thấy API công khai từ parent dù khác runtime package.
- Quyền gọi package-private **không** được cấp chỉ vì child “nhìn thấy” class của parent.
- Hai loader cùng tải một file JAR nhưng định nghĩa class riêng thì vẫn có các runtime types và runtime packages riêng.

## 7. Kiểm tra package bằng Class API: dùng đúng thông tin

Một số API dễ tạo nhầm lẫn:

| API | Nó nói gì? | Nó không bảo đảm điều gì? |
| --- | --- | --- |
| `clazz.getPackageName()` | Tên package dạng chuỗi của class | Không xác nhận hai class ở cùng runtime package |
| `clazz.getPackage()` | Đối tượng `Package` mô tả package liên quan đến class | Không thay thế quy tắc runtime package trong JVMS |
| `clazz.getClassLoader()` | **Defining loader** của class; `null` với bootstrap | Không cho biết mọi initiating loader từng yêu cầu class |
| `loader.getDefinedPackage(name)` | `Package` được **loader đó** định nghĩa cho tên package khi có | Không định nghĩa lại lớp, không nói toàn bộ package của mọi loader |
| `loader.getDefinedPackages()` | Các `Package` do loader đó định nghĩa | Không tự quét tất cả parent/sibling loader |
| `clazz.getModule()` | Module runtime của class | Không thay thế defining-loader identity hay mọi access check |
| `clazz.getProtectionDomain()` | Protection domain nếu có thể truy vấn | Không quyết định hai class có cùng runtime package |
| `clazz.getSigners()` | Thông tin signer của class khi có | Không cho phép “chia sẻ” package-private across loaders |

Không viết:

```java
boolean sameRuntimePackage =
        a.getPackageName().equals(b.getPackageName());
```

rồi xem kết quả `true` là bằng chứng đủ. Đây là **đoạn sai về mặt lập luận** nếu `a` và `b` có defining loader khác nhau.

Với hai class không thuộc các trường hợp đặc biệt như array/primitive, quy tắc nhận diện runtime package theo JVMS là xét **package name** và **defining loader**. Ví dụ snippet khi đã có hai đối tượng `Class<?> a,b`:

```java
boolean sameRuntimePackage =
        a.getPackageName().equals(b.getPackageName())
        && a.getClassLoader() == b.getClassLoader();
```

Đây là snippet, không phải chương trình độc lập. Nếu dùng để suy ra **quyền truy cập một member cụ thể**, cần tiếp tục xét modifier, access context, subclass rules và module access, không chỉ hai điều kiện này.

Đặc biệt, `getDefinedPackage` của loader con **không liệt kê tất cả** package của parent. Việc không thấy một package trong danh sách “defined here” không có nghĩa loader con không thể nạp class tương ứng nhờ delegation.

## 8. Package sealing và các JAR khác nhau

Một số JAR khai báo metadata package trong `META-INF/MANIFEST.MF`. Thuộc tính **Sealed** cho phép quy định package được seal theo một code source URL. Khi loader cố định nghĩa các class vào cùng package nhưng từ những nguồn không phù hợp với sealing policy, runtime/loader có thể từ chối.

Ví dụ manifest minh họa:

```text
Manifest-Version: 1.0
Name: demo/shared/
Sealed: true
```

Trong phạm vi loader xử lý manifest sealing, điều này có nghĩa các class của package được seal phải tuân theo nguồn code phù hợp. Nếu loader tìm `demo.shared.A` từ JAR1, rồi tìm `demo.shared.B` từ JAR2 khác, một **sealing violation** có thể xuất hiện.

Nhưng cần phân biệt:

1. **Runtime package identity:** do package name và defining loader quyết định.
2. **Sealing policy:** kiểm soát tập nguồn URL/code source được phép đóng góp các class vào một package trong cùng loader; phụ thuộc implementation của loader/JAR manifest.
3. **Module encapsulation:** kiểm soát đọc/export/open package trong JPMS; không phải một dạng của sealing.

Hai loader độc lập có thể **định nghĩa package cùng tên** mà không tự hợp thành một sealed package chung. Sealing không làm hai defining loaders tự nhận cùng quyền package-private.

### Dùng definePackage với custom ClassLoader

`ClassLoader.definePackage(...)` cho phép định nghĩa một đối tượng `Package` chứa metadata specification/implementation và URL sealing tùy API overload. Nó **không** tạo class mới, không cấp quyền package-private giữa loader, và không tự merge package của loader khác.

Nếu custom loader tải classes từ nhiều nguồn, cần thiết kế nhất quán package metadata, signer/certificate và protection domain. Trong một defining loader, các class cùng package phải tuân thủ ràng buộc signer/certificate khi áp dụng; vi phạm có thể gây `SecurityException` hoặc lỗi tương ứng.

**Không coi** việc gọi `definePackage("demo.shared", ...)` trên L2 là cách “nhập” runtime package của L1 vào L2. Hai loader vẫn độc lập.

## 9. Java Platform Module System (JPMS): split package và module

Từ Java 9, một lớp còn thuộc về một runtime module. Khi dùng **named modules**, JVM/module layer và loader còn áp dụng:

- **Readability:** module gọi có đọc module chứa kiểu đích hay không.
- **Exports:** package công khai có được module chứa nó export tới module dùng hay không.
- **Opens:** quyền reflective access tới package theo điều kiện module.
- **Package/module mapping:** hạn chế các package trùng nhau giữa named modules, đặc biệt nếu cùng một class loader cần định nghĩa chúng.
- **Unnamed module:** class được định nghĩa trên classpath thông thường thuộc unnamed module tương ứng với loader; nó không tương đương một named module được khai báo trong `module-info.java`.

Một class có access modifier `public` vẫn có thể không truy cập được từ module khác nếu package của named module chưa export phù hợp. Ngược lại, hai class có cùng chuỗi package name nhưng do hai loader khác nhau định nghĩa vẫn **không** được xem như một runtime package chỉ vì cấu hình module của chúng có quan hệ.

### Không lấy module path làm “cách sửa” ranh giới ClassLoader

Nếu dự án plugin bị `IllegalAccessError` do package-private giữa parent và child, di chuyển JAR lên module path không tự làm hai class thành cùng defining loader. Thậm chí named modules có thể tăng thêm các kiểm tra encapsulation và phát hiện split package.

Khi quyết định có nên dùng JPMS, cần xác định rõ ai sở hữu package, module nào định nghĩa API/SPI, module nào cần `requires` và package nào cần `exports`/`opens`.

### Runtime package và tổ chức module có cùng mục đích không?

Chúng khác vai trò:

| Cơ chế | Phạm vi |
| --- | --- |
| Source package | Sắp xếp source/binary names |
| Runtime package | Quyền truy cập/package identity dựa trên name + defining loader |
| Named module | Ranh giới module resolution, readability và encapsulation |
| ClassLoader | Nơi class được tìm/nạp/định nghĩa và danh tính kiểu runtime |

Thiết kế thành công thường **thống nhất** bốn cấp độ, thay vì coi một cấp độ tự động thay thế cấp độ còn lại.

## 10. Plugin, container và nhiều phiên bản thư viện

Một plugin architecture thường có:

```text
Host ClassLoader
   ├── com.company.spi.Plugin
   ├── com.company.spi.Request
   └── com.company.spi.Response
           ^
           |
     PluginLoader A
       ├── plugin.alpha.Handler
       └── thirdparty.lib.Utility (bản A)

     PluginLoader B
       ├── plugin.beta.Handler
       └── thirdparty.lib.Utility (bản B)
```

Cách tổ chức hợp lý:

1. **Đặt API/SPI và DTO dùng chung ở host/parent**, rồi plugin loader delegate để nhận chính cùng runtime types.
2. **Cô lập implementation và dependency không cần chia sẻ** theo plugin loader, nếu kiến trúc yêu cầu nhiều phiên bản thư viện cùng tồn tại.
3. **Không phụ thuộc quyền package-private xuyên loader**, kể cả package string giống nhau.
4. **Không truyền class nội bộ qua boundary** nếu host không có khả năng dùng đúng class identity; sử dụng public interface/SPI, adapter hoặc serialization contract.
5. **Tránh split package giữa JAR host và plugin** nếu các class bên trong gọi package-private trực tiếp với nhau.
6. **Kiểm soát delegation cho package API quan trọng**, tránh custom child-first tạo một bản `com.company.spi.Plugin` riêng rồi gây `ClassCastException` khi host cast.
7. **Phân biệt ClassLoader và classpath:** cùng đường dẫn JAR không có nghĩa cùng runtime type nếu được hai loader riêng biệt định nghĩa.

Khi cần “chia sẻ code nội bộ” giữa hai plugin, các lựa chọn bền vững hơn là đưa một API public thích hợp vào loader chung, trích thư viện chung có ranh giới công khai, hoặc để cùng một loader thật sự định nghĩa những class cần truy cập package-private (sau khi đánh giá ràng buộc namespace và bảo trì).

### Cùng package nhưng hai dependency versions

Ví dụ có hai bản `com.vendor.json.Parser` tại Plugin A và Plugin B. Nếu mỗi plugin tự định nghĩa phiên bản riêng thì chúng không phải cùng runtime class. Nếu parent đã nạp một bản Parser và plugin vẫn parent-first, cả hai có thể vô tình dùng **cùng** Parser của parent thay vì bản riêng.

Cần kiểm tra:

```java
System.out.println(parserType.getName());
System.out.println(parserType.getClassLoader());
```

Đây là snippet, giả định `parserType` là `Class<?>` đã được ứng dụng lấy. Nếu cần nguồn code, có thể kiểm tra `ProtectionDomain/CodeSource` khi giá trị tồn tại. Không chỉ so sánh version ghi trên tên JAR.

## 11. Những lỗi dễ nhầm khi hai loader có package trùng tên

| Triệu chứng | Lý do cần xét | Kiểm tra đầu tiên |
| --- | --- | --- |
| `IllegalAccessError` dù code biên dịch được | Package-private/protected qua hai runtime packages khác nhau | Loader thực sự định nghĩa caller và target |
| `ClassCastException` với hai tên lớp giống hệt | Cùng binary name nhưng defining loaders khác nhau | `Class<?> ==`, loader identity |
| `LinkageError` về loading constraints | JVM cần một tên kiểu nhất quán xuyên loader ở điểm liên kết | Class trong method descriptors, loader graph |
| `SecurityException` về signers/certificates | Classes trong cùng package và loader có signer policy không nhất quán | Source JAR, signatures và package metadata |
| `SecurityException: sealing violation` | Sealed package bị trộn các nguồn URL/JAR | Manifest sealing và code source |
| `ClassNotFoundException` khi load ở child | Child/parent không nhìn thấy định nghĩa cần thiết | Binary name, parent chain, nguồn bytecode |
| `NoClassDefFoundError` khi chạy | Phụ thuộc thiếu, wrong name hoặc trạng thái initialization lỗi | Thông báo nguyên nhân, classpath/dependency graph |
| `NoSuchMethodError` | Runtime nạp API phiên bản khác compile-time | Signatures và định nghĩa class đã được chọn |
| `InaccessibleObjectException` khi reflection | Quy tắc `opens` của JPMS hoặc access context | Module chứa class, caller module |
| `IllegalAccessException` từ reflection | Reflection API từ chối truy cập theo access checks | Member modifier, caller/module, access mode |

**Đặc biệt:** `IllegalAccessError` là một dạng `IncompatibleClassChangeError` (thuộc `LinkageError`), biểu thị access runtime không hợp lệ. Khác với `IllegalAccessException` là checked exception mà reflection có thể ném khi API phản chiếu từ chối truy cập. Thí nghiệm phần 5 bắt `InvocationTargetException` vì public method được invoke **đã bắt đầu chạy**, rồi JVM phát hiện vấn đề tại lời gọi tới class khác.

Một chương trình có thể gặp lỗi ngay trong quá trình liên kết tùy class verification/resolution; đừng khẳng định mọi lỗi package-private sẽ xuất hiện chính xác ở một dòng method call giống nhau trên mọi JVM. Mục tiêu là hiểu ranh giới loader, không dựa vào vị trí stack trace tuyệt đối.

## 12. Cách debug đúng: ghi name, defining loader, module và code source

Khi gặp sự cố package trùng tên, nên so sánh **hai đối tượng Class cụ thể**: caller và target.

Đặt hàm hỗ trợ sau trong một class Java:

```java
static void inspect(Class<?> type) {
    System.out.println("name=" + type.getName());
    System.out.println("package=" + type.getPackageName());
    System.out.println("loader=" + type.getClassLoader());
    System.out.println("module=" + type.getModule());
    java.security.ProtectionDomain domain = type.getProtectionDomain();
    java.security.CodeSource source =
            domain == null ? null : domain.getCodeSource();
    System.out.println("codeSource="
            + (source == null ? "<unknown>" : source.getLocation()));
}
```

Đây là **phương thức minh họa**, không phải chương trình có `main` độc lập. Trong những môi trường bị giới hạn, một số thông tin security/protection có thể không sẵn có; `CodeSource == null` không chứng minh class đã bị nạp sai.

Quy trình:

1. Xác định **binary name** của caller và target liên quan trong lỗi.
2. Lấy **Class<?> của từng bên** nếu có thể; không dùng `Class.forName` với loader bất kỳ để rồi tưởng đó là class đã gây lỗi.
3. In `getPackageName()` và **`getClassLoader()` của cả hai**.
4. Nếu binary names hoặc package names giống nhưng loaders khác nhau, xét split runtime package/type identity.
5. Nếu cùng loader nhưng vẫn `IllegalAccessError`, kiểm tra modifier, static/instance, superclass, interface, module và binary incompatibility.
6. Nếu JAR khác nhau, kiểm tra manifest sealing, certificates/signers và hai bản class trùng binary name.
7. Nếu hệ dùng named modules, kiểm tra exports/opens/readability và nguồn module của class.
8. Nếu liên quan plugin, xác định parent-first/child-first policy và đường mà class đã thực sự đi qua.

### Công cụ JDK hỗ trợ

```bash
jar tf plugins/plugin-a.jar
jar tf plugins/plugin-b.jar
javap -classpath plugins/plugin-a.jar -p demo.shared.Secret
java -Xlog:class+load=info -cp "app/classes:lib/*" com.example.Main
```

Đây là lệnh **minh họa**, tên file/class cần thay bằng giá trị thật. Dòng `-cp` dùng dấu `:` của Linux/macOS; Windows dùng `;`. `jar tf` xác nhận entry có trong JAR; `javap -p` cho biết access modifiers/method signatures; unified class-load log giúp thấy bytecode đến từ đâu.

Với class do custom loader định nghĩa trực tiếp từ memory/database, nguồn `CodeSource` có thể không phải URL file/JAR; cần log loader identity và nguồn bytecode đáng tin cậy tại nơi `defineClass` xảy ra.

## 13. Những nhầm lẫn thường gặp

**“Hai file Java có `package demo.shared;` nên luôn truy cập package-private được.”** Sai. Runtime package phụ thuộc defining loader.

**“Parent và child là họ hàng nên được coi cùng package.”** Sai. Khác defining loader là khác runtime package, dù có delegation giữa chúng.

**“Package.getName() giống nhau thì quyền truy cập giống nhau.”** Sai. Nó chưa xét loader và các access rules khác.

**“Khi child gọi `parent.loadClass` thì lớp trả về thuộc package của child.”** Sai. Defining loader vẫn là parent.

**“Hai lớp cùng binary name ở hai loader là một type vì bytecode giống nhau.”** Sai. Runtime class identity còn chứa defining loader.

**“Chỉ cần dùng `public` cho class là mọi thành viên package-private đều được gọi.”** Sai. Class public và member access là hai kiểm tra khác nhau; named modules còn có ranh giới encapsulation.

**“Sealed package làm hai loader cùng tên chia sẻ quyền nội bộ.”** Sai. Sealing là ràng buộc source/package, không nhập defining loaders.

**“Gọi `definePackage` trên hai loader có cùng tên package sẽ merge class.”** Sai. API quản lý metadata package của loader tương ứng, không thay class identity.

**“JPMS có thể bỏ qua quy tắc runtime package.”** Sai. Module rules bổ sung quyền truy cập, không tự cấp package-private qua defining loaders.

**“Có `ClassCastException` thì chắc do package-private.”** Không đủ dữ kiện. Type identity lỗi là vấn đề class identity, không phải access modifier package-private trực tiếp.

## 14. Bài tập tự kiểm tra

**Câu 1.** `demo.shared.A` do L1 định nghĩa và `demo.shared.B` do L2 định nghĩa. Hai class có cùng runtime package không?

**Trả lời:** Không nếu L1 và L2 là hai loader object khác nhau, dù chuỗi package name giống.

**Câu 2.** Child gọi `loadClass("demo.shared.A")` và nhận class do Parent đã định nghĩa. Defining loader là ai?

**Trả lời:** Parent. Việc Child khởi xướng yêu cầu không đổi runtime package của class.

**Câu 3.** Tại sao compiler cho `Caller` gọi package-private `Secret.reveal()`, nhưng runtime báo `IllegalAccessError`?

**Trả lời:** Compiler thấy hai source types cùng tên package trong compile graph; runtime lại có `Caller` do Child định nghĩa, `Secret` do Parent định nghĩa. Chúng khác runtime package và JVM từ chối quyền package-private.

**Câu 4.** Hai `URLClassLoader` độc lập đọc cùng `demo.shared.Token.class`. So sánh `Class<?>` có bằng nhau không?

**Trả lời:** Không nếu mỗi loader tự định nghĩa class. Cùng binary name/bytecode nhưng khác defining loader nghĩa là hai runtime classes.

**Câu 5.** Khi module A `exports demo.shared`, một class cùng package name ở loader khác có đương nhiên gọi được `private` hoặc package-private member không?

**Trả lời:** Không. `exports` liên quan khả năng truy cập public types/members theo module rules; không cấp quyền private/package-private xuyên loader.

**Câu 6.** Tại sao một package sealed có thể gây lỗi khi JAR1 và JAR2 cung cấp hai class khác nhau trong cùng package?

**Trả lời:** Sealing đặt ràng buộc nguồn của các class trong package do loader quản lý. Nếu loader trộn các nguồn không thỏa seal base URL, có thể xảy ra sealing violation.

**Câu 7.** Cần log thông tin tối thiểu nào để biết hai class có cùng runtime package hay không?

**Trả lời:** Package name và defining loader của **từng** đối tượng `Class<?>` thực tế. Để chẩn đoán đầy đủ có thể thêm module, code source, method modifiers và dependency version.

**Câu 8.** Hệ plugin cần trao đổi thông điệp giữa A và B, nhưng mỗi plugin có loader riêng. Nên chọn thiết kế nào?

**Trả lời:** Đặt public SPI/DTO thích hợp tại loader host chia sẻ, để plugin delegate tới đó; tránh dựa vào truy cập package-private giữa hai loader.

## 15. Checklist triển khai an toàn về package identity

- [ ] Xác định defining loader thực tế của các class dùng trong cùng package name.
- [ ] Không trộn class cần truy cập package-private qua các loader khác nhau.
- [ ] API/SPI chung phải có **một định nghĩa runtime chia sẻ**, thường từ host/parent.
- [ ] Khi cô lập dependency, xem class cùng binary name có thể xuất hiện ở nhiều defining loaders.
- [ ] Ghi rõ namespace package nào thuộc host và package nào thuộc từng plugin.
- [ ] Kiểm tra parent-first/child-first policy trước khi kết luận JAR nào thắng.
- [ ] Cân nhắc sealing, signers, protection domains nếu nạp nhiều JAR vào một loader.
- [ ] Với named modules, kiểm tra module graph và `exports`/`opens`.
- [ ] Kiểm tra binary compatibility và quyền truy cập khi build bằng một bố cục loader nhưng chạy bằng bố cục khác.
- [ ] Test thực tế cùng artifact, loader tree và runtime JDK của môi trường triển khai.
- [ ] Khi có lỗi, in package name, loader, module và code source của **cả caller và target**.
- [ ] Không dùng `Class.getPackageName()` hoặc JAR path một mình để suy ra runtime package identity.

## 16. Kết luận và nguồn chuẩn

**Cùng tên package ở source code không bảo đảm cùng runtime package.** JVM xác định runtime package bằng **tên package cùng defining ClassLoader**. Quy tắc này giải thích vì sao package-private có thể bị chặn dù compiler đã chấp nhận, tại sao hai runtime classes cùng tên không cast được khi được định nghĩa bởi hai loader khác nhau, và vì sao plugin architecture cần một ranh giới API/SPI chung rõ ràng.

Khi điều tra lỗi, đừng hỏi riêng “hai class khai báo package gì?”; hãy hỏi **“mỗi class do loader nào thực sự định nghĩa?”**, rồi kiểm tra tiếp quyền member, quan hệ kiểu, module visibility, sealing và dependency compatibility.

Tài liệu tham khảo chính thức:

- JVMS 17 §5.3, *Creation of Classes and Interfaces* (defining loader và runtime class identity): https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- JVMS 17 §5.4.4, *Access Control*: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.4
- JLS 17 §6.6, *Access Control*: https://docs.oracle.com/javase/specs/jls/se17/html/jls-6.html#jls-6.6
- Java SE 17 `Class` API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- Java SE 17 `ClassLoader` API, `definePackage` và `getDefinedPackage`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17 `Package` API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Package.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="packages-and-code-sources-003">Package sealing và thông tin manifest</a>

<details>
<summary>Xem chi tiết</summary>

# Package sealing và thông tin manifest

## 1. Package sealing giải quyết vấn đề gì?

Một ứng dụng Java có thể đưa nhiều file JAR vào cùng đường tìm kiếm của ClassLoader. Nếu hai JAR chứa các class có **cùng package name**, loader đôi khi có thể nạp một class từ JAR A và class khác từ JAR B. Điều này thường được gọi là **split package trên classpath**.

Ví dụ:

~~~text
a.jar
└── demo/locked/Anchor.class

b.jar
└── demo/locked/Other.class
~~~

Nếu không có quy tắc bổ sung, một `URLClassLoader` dùng cả hai JAR có thể nạp `demo.locked.Anchor` từ A và `demo.locked.Other` từ B; cả hai có chung tên package và cùng defining loader.

**Package sealing** là cơ chế của định dạng JAR và loader hỗ trợ manifest nhằm ràng buộc: **các class thuộc cùng một sealed package phải đến từ cùng nguồn JAR/code source**. Khi một loader cố định nghĩa class cùng sealed package từ nguồn không khớp, implementation như `URLClassLoader` sẽ báo `SecurityException` với nội dung liên quan đến *sealing violation*.

Cần phân biệt ngay:

- **Package sealing không phải class visibility**: `public`, `private`, package-private, JPMS `exports`/`opens` vẫn được kiểm tra theo quy tắc riêng.
- **Package sealing không phải chữ ký số JAR**: `Sealed: true` không ký bytecode, không xác thực người phát hành.
- **Package sealing không phải công cụ khóa version dependency tự động**: nó phát hiện một số trường hợp class cùng package đến từ các nguồn khác nhau.
- **Package sealing được xét theo loader định nghĩa package**. Hai ClassLoader độc lập vẫn có thể định nghĩa các package cùng tên riêng biệt, không bị gộp thành một package trên toàn JVM.

Trong Java 17, `URLClassLoader` sử dụng thông tin `Manifest` khi tạo `Package` và khi kiểm tra việc nạp các class tiếp theo. Đây là bối cảnh của bài.

## 2. Tên package, runtime package và nơi xuất phát bytecode

Ba khái niệm có liên quan nhưng không đồng nhất:

| Khái niệm | Ví dụ | Ý nghĩa |
|---|---|---|
| Package name | `demo.locked` | Tên package viết trong source Java |
| Runtime package | `demo.locked` trong defining loader cụ thể (và module liên quan) | Quan trọng cho định danh và quyền truy cập package-private |
| Code source URL | `file:/.../a.jar` | Nơi loader lấy bytecode, dùng trong kiểm tra sealing |

Hai class có cùng package name nhưng được **hai loader khác nhau** định nghĩa không tự động nằm trong cùng runtime package. Ngược lại, nếu cùng `URLClassLoader` định nghĩa chúng từ hai JAR khác nhau, sealing của package có thể ngăn sự pha trộn bytecode từ hai code source.

**Thứ tự quan trọng:** `Package` metadata thường được xác định tại thời điểm package được định nghĩa lần đầu trong loader. Một class được nạp sớm từ JAR có thể thiết lập package sealed/unsealed; class sau phải tương thích với tình trạng đã thiết lập.

Tài liệu JAR nêu rằng sealed package buộc class trong package đó xuất phát từ cùng JAR. Trong triển khai `URLClassLoader`, so sánh gắn với **URL code source** của package.

## 3. Manifest là gì và lưu ở đâu?

Một JAR thông thường có tệp metadata:

~~~text
META-INF/MANIFEST.MF
~~~

Đây là file văn bản có **main section** và có thể có các **named sections**. Thông tin quan trọng trong bài:

| Attribute | Mức áp dụng | Tác dụng |
|---|---|---|
| `Manifest-Version` | Main | Phiên bản định dạng manifest, thường `1.0` |
| `Sealed` | Main hoặc section package | Quy định package sealed hay không |
| `Specification-Title` | Main hoặc package | Tên đặc tả mà package triển khai |
| `Specification-Version` | Main hoặc package | Phiên bản đặc tả |
| `Specification-Vendor` | Main hoặc package | Bên cung cấp đặc tả |
| `Implementation-Title` | Main hoặc package | Tên implementation |
| `Implementation-Version` | Main hoặc package | Phiên bản implementation |
| `Implementation-Vendor` | Main hoặc package | Nhà cung cấp implementation |
| `Main-Class` | Main | Entry point cho JAR chạy với launcher thích hợp; **không phải sealing** |
| `Class-Path` | Main | Có thể tham gia mở rộng đường tìm class trong một số cách chạy/loader; **không phải sealing** |

Ví dụ main section:

~~~text
Manifest-Version: 1.0
Sealed: true

~~~

Với manifest này, **các package của JAR được sealed mặc định**, trừ package có named section ghi đè. Để chỉ sealed một package, đặt `Sealed: true` trong section riêng:

~~~text
Manifest-Version: 1.0

Name: demo/locked/
Sealed: true

~~~

**Chú ý đường dẫn:** `Name: demo/locked/` có dấu `/` cuối, đại diện cho package `demo.locked`. Nó không phải `Name: demo.locked` và không phải tên file `Anchor.class`.

Giữa main section và named section phải có **dòng trống**. Mỗi section cần kết thúc đúng cú pháp manifest; khi tự tạo file nên có newline cuối file. File manifest tuân quy tắc dòng/continuation của đặc tả JAR, không nên tự nối thành một dòng rất dài.

## 4. Quy tắc ưu tiên `Sealed`: toàn JAR và từng package

Các trường hợp:

**A. Không khai báo `Sealed`:**

~~~text
Manifest-Version: 1.0

~~~

Mặc định package của JAR **không sealed** nếu không có section riêng quy định.

**B. Khai báo ở main section:**

~~~text
Manifest-Version: 1.0
Sealed: true

~~~

Tất cả package trong JAR mặc định sealed.

**C. Main sealed, một package ghi đè unsealed:**

~~~text
Manifest-Version: 1.0
Sealed: true

Name: demo/open/
Sealed: false

~~~

Package `demo.open` không sealed, còn các package khác vẫn sealed theo main section.

**D. Main không sealed, một package ghi đè sealed:**

~~~text
Manifest-Version: 1.0
Sealed: false

Name: demo/locked/
Sealed: true

~~~

Chỉ `demo.locked` được sealed theo section này; các package khác vẫn unsealed nếu không có override khác.

Giá trị `Sealed` là `true` hoặc `false`, không phân biệt chữ hoa/thường. Một named section cụ thể **ưu tiên hơn** giá trị ở main section. Các thuộc tính phiên bản và vendor cũng có thể có giá trị riêng theo package.

**Lưu ý:** Sealing không áp dụng cho unnamed package; class muốn thuộc package sealed cần được khai báo trong một package có tên.

## 5. Bài thực hành Java 17+: A sealed, B khác nguồn, package open vẫn hoạt động

Ta sẽ tạo hai JAR từ bốn class:

~~~text
sealing-demo/
├── a-src/
│   └── demo/
│       ├── locked/Anchor.java
│       └── open/One.java
├── b-src/
│   └── demo/
│       ├── locked/Other.java
│       └── open/Two.java
├── host-src/
│   └── SealingDemo.java
└── manifests/
    ├── a.mf
    └── b.mf
~~~

`a.jar` sealed **chỉ** package `demo.locked` và gắn thông tin phiên bản. `b.jar` không sealed. Khi một `URLClassLoader` nhìn cả A và B:

- Nạp `Anchor` từ A trước → package `demo.locked` được tạo trong trạng thái sealed tại A.
- Nạp `Other` từ B sau → `SecurityException` do sealing violation.
- Package khác tên `demo.open` không sealed nên `One` của A và `Two` của B có thể cùng được loader đó định nghĩa.
- Nếu nạp `Other` từ B trước, package `demo.locked` bắt đầu ở trạng thái unsealed; sau đó cố nạp `Anchor` từ JAR A tuyên bố sealing → cũng là sealing violation.

Ta dùng **các class có tên khác nhau** trong cùng package để loại bỏ sự nhầm lẫn với tình huống hai JAR có đúng cùng FQCN và chỉ một phiên bản được tìm thấy trước.

### 5.1. Class của JAR A: `Anchor`

Tệp **`a-src/demo/locked/Anchor.java`**:

~~~java
package demo.locked;

public final class Anchor {
    private Anchor() {
    }

    public static String origin() {
        return "ANCHOR_A";
    }
}
~~~

Tệp **`a-src/demo/open/One.java`**:

~~~java
package demo.open;

public final class One {
    private One() {
    }

    public static String origin() {
        return "ONE_A";
    }
}
~~~

### 5.2. Class của JAR B: `Other`

Tệp **`b-src/demo/locked/Other.java`**:

~~~java
package demo.locked;

public final class Other {
    private Other() {
    }

    public static String origin() {
        return "OTHER_B";
    }
}
~~~

Tệp **`b-src/demo/open/Two.java`**:

~~~java
package demo.open;

public final class Two {
    private Two() {
    }

    public static String origin() {
        return "TWO_B";
    }
}
~~~

Bốn class chỉ phụ thuộc Java chuẩn. Host sẽ nạp bằng tên chuỗi và reflection; **không import** bất kỳ class `demo.locked.*` hay `demo.open.*` để tránh ứng dụng chủ vô tình đưa chúng vào defining loader khác.

### 5.3. Manifest của A với metadata riêng cho package

Tệp **`manifests/a.mf`** (tạo với newline cuối):

~~~text
Manifest-Version: 1.0
Implementation-Title: Example A Archive

Name: demo/locked/
Sealed: true
Specification-Title: Demo Package Contract
Specification-Version: 1.0
Implementation-Title: Locked Package A
Implementation-Version: 2.1.0

~~~

Giải thích:

- Không có `Sealed: true` ở main section, vì ta không muốn sealed `demo.open`.
- Section `Name: demo/locked/` sealed **chính package đó**.
- `Specification-Title` và `Specification-Version` mô tả đặc tả của package.
- `Implementation-Title` và `Implementation-Version` ở section package cung cấp metadata khi loader định nghĩa `demo.locked` từ A.
- `Implementation-Title: Example A Archive` ở main section có thể áp dụng cho package khác, nhưng bản `Locked Package A` trong named section **ghi đè cho `demo.locked`**.

Tệp **`manifests/b.mf`**:

~~~text
Manifest-Version: 1.0

~~~

Không có `Sealed` hoặc version metadata cho `demo.locked` trong B, nên khi B là nguồn định nghĩa package trước, package được xác lập là **unsealed**.

### 5.4. Host kiểm tra `Package` và nạp class trùng package

Tệp **`host-src/SealingDemo.java`**:

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SealingDemo {
    private SealingDemo() {
    }

    private static boolean sealingViolation(
            ClassLoader loader, String name) {
        try {
            loader.loadClass(name);
            return false;
        } catch (SecurityException expected) {
            return true;
        } catch (ClassNotFoundException unexpected) {
            throw new IllegalStateException(
                    "Required class missing: " + name, unexpected);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: SealingDemo <a.jar> <b.jar>");
        }

        Path a = Path.of(args[0]).toAbsolutePath().normalize();
        Path b = Path.of(args[1]).toAbsolutePath().normalize();

        if (!Files.isRegularFile(a) || !Files.isRegularFile(b)) {
            throw new IllegalArgumentException("Both JARs must exist");
        }

        URL aUrl = a.toUri().toURL();
        URL bUrl = b.toUri().toURL();

        // Các class trong A/B chỉ phụ thuộc java.base.
        // Platform loader không giữ demo.locked/demo.open của host.
        ClassLoader parent = ClassLoader.getPlatformClassLoader();

        try (URLClassLoader combined = new URLClassLoader(
                    new URL[] {aUrl, bUrl}, parent);
             URLClassLoader reverse = new URLClassLoader(
                    new URL[] {bUrl, aUrl}, parent);
             URLClassLoader aOnly = new URLClassLoader(
                    new URL[] {aUrl}, parent);
             URLClassLoader bOnly = new URLClassLoader(
                    new URL[] {bUrl}, parent)) {

            Class<?> anchor = combined.loadClass(
                    "demo.locked.Anchor");
            Package pkg = anchor.getPackage();

            System.out.println("Anchor sealed: " + pkg.isSealed());
            System.out.println("Sealed at A URL: "
                    + pkg.isSealed(aUrl));
            System.out.println("Sealed at B URL: "
                    + pkg.isSealed(bUrl));
            System.out.println("Spec title: "
                    + pkg.getSpecificationTitle());
            System.out.println("Spec version: "
                    + pkg.getSpecificationVersion());
            System.out.println("Impl title: "
                    + pkg.getImplementationTitle());
            System.out.println("Impl version: "
                    + pkg.getImplementationVersion());

            System.out.println("A then B violation: "
                    + sealingViolation(combined,
                            "demo.locked.Other"));

            Class<?> one = combined.loadClass("demo.open.One");
            Class<?> two = combined.loadClass("demo.open.Two");
            System.out.println("Open package unsealed: "
                    + !one.getPackage().isSealed());
            System.out.println("Open from same loader: "
                    + (one.getClassLoader()
                    == two.getClassLoader()));

            Class<?> firstB = reverse.loadClass(
                    "demo.locked.Other");
            System.out.println("B first unsealed: "
                    + !firstB.getPackage().isSealed());
            System.out.println("B then A violation: "
                    + sealingViolation(reverse,
                            "demo.locked.Anchor"));

            Class<?> onlyA = aOnly.loadClass(
                    "demo.locked.Anchor");
            Class<?> onlyB = bOnly.loadClass(
                    "demo.locked.Other");
            System.out.println("Separate loaders succeed: "
                    + (onlyA != null && onlyB != null));
            System.out.println("Same name package object: "
                    + (onlyA.getPackage() == onlyB.getPackage()));
        }
    }
}
~~~

Chương trình tạo **bốn `URLClassLoader` riêng** để mỗi tình huống có trạng thái package độc lập. Điều này tránh việc case nạp A trước vô tình ảnh hưởng case B trước.

**Không dùng application/system loader làm parent trong ví dụ** vì host không cần API dùng chung từ JAR; chọn platform loader giúp đảm bảo việc thử nghiệm không bị class `demo.locked.*` ở application classpath che trước. Trong hệ plugin thực tế cần interface host, parent phải là loader nhìn thấy đúng shared API — không tự động chọn platform loader cho mọi trường hợp.

### 5.5. Biên dịch và đóng gói

Yêu cầu **JDK 17+**, chạy từ thư mục `sealing-demo/`.

**Bash (Linux, macOS hoặc Git Bash):**

~~~bash
mkdir -p build/a build/b build/host dist

javac -encoding UTF-8 -d build/a \
  a-src/demo/locked/Anchor.java \
  a-src/demo/open/One.java

javac -encoding UTF-8 -d build/b \
  b-src/demo/locked/Other.java \
  b-src/demo/open/Two.java

javac -encoding UTF-8 -d build/host \
  host-src/SealingDemo.java

jar --create --file dist/a.jar \
  --manifest manifests/a.mf -C build/a .

jar --create --file dist/b.jar \
  --manifest manifests/b.mf -C build/b .

java -cp build/host SealingDemo dist/a.jar dist/b.jar
~~~

**Windows PowerShell:**

~~~powershell
New-Item -ItemType Directory -Force build/a,build/b,build/host,dist | Out-Null

javac -encoding UTF-8 -d build/a a-src/demo/locked/Anchor.java a-src/demo/open/One.java

javac -encoding UTF-8 -d build/b b-src/demo/locked/Other.java b-src/demo/open/Two.java

javac -encoding UTF-8 -d build/host host-src/SealingDemo.java

jar --create --file dist/a.jar --manifest manifests/a.mf -C build/a .

jar --create --file dist/b.jar --manifest manifests/b.mf -C build/b .

java -cp build/host SealingDemo dist/a.jar dist/b.jar
~~~

Trước khi chạy, kiểm tra JAR:

~~~bash
jar --list --file dist/a.jar
jar --list --file dist/b.jar
jar --extract --file dist/a.jar META-INF/MANIFEST.MF
~~~

Lệnh `jar --extract` ghi `META-INF/MANIFEST.MF` vào thư mục hiện tại, nên **không bắt buộc chạy lệnh extract** nếu chỉ cần kiểm tra logic; có thể kiểm tra manifest bằng công cụ xem nội dung ZIP/JAR. Nếu chạy extract, hãy thực hiện trong thư mục kiểm tra riêng, tránh đè lên file cùng tên của dự án.

Các entry chính cần có:

~~~text
a.jar:
  demo/locked/Anchor.class
  demo/open/One.class
  META-INF/MANIFEST.MF

b.jar:
  demo/locked/Other.class
  demo/open/Two.class
  META-INF/MANIFEST.MF
~~~

Việc dùng `jar --manifest` bảo đảm metadata được đóng trong JAR. Nếu tự tạo JAR không dùng manifest mong muốn, `Package.getImplementationVersion()` có thể là `null` và sealing không có hiệu lực như ví dụ.

### 5.6. Kết quả đầu ra

Với cấu trúc trên, không có class trùng từ parent và JDK 17 `URLClassLoader` chuẩn:

~~~text
Anchor sealed: true
Sealed at A URL: true
Sealed at B URL: false
Spec title: Demo Package Contract
Spec version: 1.0
Impl title: Locked Package A
Impl version: 2.1.0
A then B violation: true
Open package unsealed: true
Open from same loader: true
B first unsealed: true
B then A violation: true
Separate loaders succeed: true
Same name package object: false
~~~

**Đọc kết quả:**

1. `Anchor` đến từ A, manifest A sealed `demo.locked`. `pkg.isSealed()` là `true`.
2. `pkg.isSealed(aUrl)` là `true`, trong khi `pkg.isSealed(bUrl)` là `false` vì hai URL code source khác nhau.
3. `Package` phản ánh đúng `Specification-Title`, `Specification-Version`, `Implementation-Title` và `Implementation-Version` của package section trong A.
4. `combined` thử nạp `Other` từ B vào package đã sealed ở A → `SecurityException` được bắt, in `true`.
5. Package `demo.open` không sealed, nên `One` từ A và `Two` từ B có thể cùng nằm trong **một defining loader**.
6. `reverse` nạp class B trước, định nghĩa `demo.locked` là unsealed; nạp class A có `Sealed: true` sau đó cũng gây vi phạm vì không thể sealed một package đã được định nghĩa unsealed.
7. Hai loader riêng `aOnly`/`bOnly` có **Package object riêng**, dù chuỗi package name giống nhau; không có vi phạm sealing xuyên hai loader độc lập.

Thông điệp quan trọng: **thứ tự class loading**, không chỉ thứ tự URL trên classpath, quyết định class nào tạo package metadata đầu tiên. Class `Anchor` chỉ ở A, `Other` chỉ ở B: khi hỏi `Other` trước, loader có thể đi qua URL A rồi B và lấy class từ B.

## 6. Package metadata được lấy và sử dụng thế nào?

`java.lang.Package` có các phương thức hữu ích:

| API | Ý nghĩa |
|---|---|
| `Class.getPackage()` | Lấy `Package` gắn với runtime class |
| `Package.getName()` | Tên package |
| `Package.isSealed()` | Package có bị sealing không? |
| `Package.isSealed(URL)` | Package có sealed **về URL code source này** không? |
| `Package.getSpecificationTitle()` | Tên đặc tả từ manifest |
| `Package.getSpecificationVersion()` | Phiên bản đặc tả |
| `Package.getSpecificationVendor()` | Nhà cung cấp đặc tả |
| `Package.getImplementationTitle()` | Tên implementation |
| `Package.getImplementationVersion()` | Phiên bản implementation |
| `Package.getImplementationVendor()` | Nhà cung cấp implementation |
| `Package.isCompatibleWith(String)` | Kiểm tra phiên bản đặc tả có đạt mức yêu cầu theo quy tắc số chấm phân tách |

**Không xem `Implementation-Version` là cơ chế enforcement dependency version.** Nó là metadata, có thể vắng mặt hoặc do build pipeline gán. JVM không tự từ chối JAR chỉ vì `Implementation-Version` của package không khớp kỳ vọng ứng dụng.

Với `Package.isCompatibleWith("1.2")`, version specification được diễn giải theo thành phần số không âm ngăn bởi dấu chấm; format không hợp lệ có thể gây `NumberFormatException`. Đây không phải bộ so sánh SemVer đầy đủ như npm/Maven và không nên dùng cho các version string kiểu `"1.2.3-beta"` nếu chưa xử lý riêng.

### Package metadata không đồng nghĩa JAR metadata cho mọi class

Trong một loader, `Package` là metadata gắn với **định nghĩa package**, không phải bản sao độc lập trên mỗi class. Nếu một package unsealed được định nghĩa trước từ JAR A và sau đó class từ JAR B được nạp cùng package, `classB.getPackage()` vẫn có thể trỏ tới `Package` đã được định nghĩa từ A.

Vì vậy **không dùng `getImplementationVersion()` của package làm bằng chứng rằng mọi class trong package đều xuất phát từ cùng JAR**, nhất là khi package unsealed trải trên nhiều nguồn.

Muốn truy vết class cụ thể, xem `Class.getProtectionDomain().getCodeSource().getLocation()` **khi khả dụng**; giá trị có thể null hoặc không có quyền truy cập trong một số môi trường. Đó là thông tin **nguồn bytecode của class**, khác với `Package` metadata.

## 7. Phân biệt package sealing với chữ ký JAR

Một file JAR có thể:

- **Sealed nhưng không signed:** `Sealed: true` chỉ ràng buộc consistency nguồn class trong package, không xác thực nhà phát hành.
- **Signed nhưng không sealed:** Chữ ký dùng để kiểm tra tính toàn vẹn/chứng thư theo cơ chế JAR signing, nhưng package có thể không dùng sealing.
- **Vừa signed vừa sealed:** Hai cơ chế hoạt động với mục đích khác nhau.

Trong cùng một runtime package, các class còn có thể chịu ràng buộc nhất quán về signer/certificate khi định nghĩa class; đây là vấn đề khác với `Sealed`. Đừng sửa lỗi sealing bằng cách tự ý bỏ xác minh chữ ký hoặc chỉnh manifest trong JAR đã ký, vì điều đó có thể làm chữ ký không còn hợp lệ.

**Sealing không phải sandbox.** Một plugin chạy bytecode trong cùng tiến trình vẫn có thể có các quyền của tiến trình. Dùng ranh giới tiến trình/container nếu cần chạy mã không tin cậy.

## 8. Sealing và classloader identity

Điểm dễ nhầm: “Một JVM chỉ được có đúng một class `demo.locked.Anchor` nếu package sealed.” Điều này **sai**.

JVM xác định runtime class dựa trên tên binary **và defining loader**. Hai loader độc lập có thể nạp cùng JAR:

~~~text
(loader A, demo.locked.Anchor)  ≠  (loader B, demo.locked.Anchor)
~~~

Mỗi loader có namespace của mình và có thể định nghĩa `Package` riêng. Sealing bảo đảm tính nhất quán của **một package được loader định nghĩa** với code source tương ứng, chứ không buộc toàn bộ JVM chỉ có một bản class/package cùng tên.

Ngược lại, ngay cả khi **không sealed**, việc một host và plugin tự định nghĩa hai class cùng FQCN có thể gây `ClassCastException`. Vì thế không dùng sealing để giải quyết mọi vấn đề class identity. API dùng chung giữa host và plugin cần được nạp từ một loader chung, còn implementation có thể ở child loader riêng.

## 9. Sealing và thứ tự tìm JAR

Một loader parent-first thường hỏi parent trước. Nếu parent đã có class được yêu cầu, `URLClassLoader` con có thể trả class của parent mà **không tự định nghĩa class đó**; khi ấy trạng thái package sealing của child không quyết định kết quả cho class được parent định nghĩa.

Trong chính URL path của một `URLClassLoader`, thứ tự URL xác định nơi tìm class khi có nhiều nguồn. Nhưng cần phân biệt hai tình huống:

**Cùng một FQCN xuất hiện trong A và B:** URL được tìm thấy trước có thể cung cấp bytecode; class trong URL phía sau bị che. Chỉ riêng sự trùng tên không bắt loader định nghĩa cả hai phiên bản.

**Hai FQCN khác nhau cùng package:** Class A và class B có thể được chọn từ hai JAR khác nhau. Nếu package đã sealed ở một nguồn, việc cố thêm class từ nguồn còn lại sẽ gặp `SecurityException`. Đây là case runnable của bài.

Để kiểm tra vấn đề trong hệ thống thật, đừng chỉ đổi thứ tự `-cp` rồi phỏng đoán. Hãy kiểm tra **defining loader**, **CodeSource URL** nếu có, **manifest sealing attributes**, và **class nào được load trước**.

## 10. Sealing với JPMS, JAR thường và custom loader

**Classpath/unnamed modules:** Tình huống ví dụ sử dụng hai JAR thông thường và `URLClassLoader`, là bối cảnh tiêu biểu cho package sealing trong manifest.

**JPMS named modules:** Việc định nghĩa module/package, readability, `exports`, `opens` và split-package constraints do hệ module quyết định. Không đơn giản áp dụng quy tắc “thêm `Sealed: true` là giải quyết mọi split package trong module path”. Với named modules, thiết kế module/package rõ ràng và tuân module resolution là cách chính.

**Custom ClassLoader:** Nếu bạn tự đọc bytecode qua `Files.readAllBytes` rồi gọi `ClassLoader.defineClass(...)`, Java không tự đọc `META-INF/MANIFEST.MF` của một kho tùy chỉnh để áp dụng chính sách sealing giống `URLClassLoader`. Bạn cần tự định nghĩa `Package` và xử lý metadata/code source/kiểm tra nguồn phù hợp khi triển khai loại loader đó.

`URLClassLoader.definePackage(String name, Manifest man, URL url)` cung cấp API để thiết lập metadata và sealing từ manifest; không nên tự bỏ qua tính nhất quán package khi viết custom loader cho JAR có sealing.

## 11. Các lỗi thường gặp và cách khắc phục

| Hiện tượng | Nguyên nhân có khả năng | Hướng xử lý |
|---|---|---|
| `SecurityException: sealing violation` | Loader định nghĩa class cùng package từ code source khác với package sealed | Kiểm tra manifest, vị trí JAR và class loading order |
| “Can't seal package: already loaded” | Package được xác lập unsealed trước, JAR sau muốn sealed | Loại split package hoặc bảo đảm package đến từ một nguồn |
| `Package.isSealed() == false` dù file manifest có `Sealed: true` | Sai section, sai path `Name`, hoặc package đã được định nghĩa trước từ nguồn khác | Kiểm tra `META-INF/MANIFEST.MF` và package owner |
| `getImplementationVersion() == null` | Không khai báo metadata hoặc loader không chuyển metadata khi define package | Kiểm tra manifest và loader implementation |
| Manifest main sealed làm package khác cũng sealed | `Sealed: true` ở main áp dụng mặc định cho toàn JAR | Dùng per-package section hoặc `Sealed: false` để override |
| Hai loader cùng nạp package sealed mà không lỗi | Hai namespace package/runtime riêng | Đây có thể là hành vi hợp lệ; kiểm tra identity |
| Cùng package unsealed nhưng cast vẫn lỗi | Trùng FQCN ở defining loader khác nhau | Chia sẻ API type qua parent loader |
| Đổi tên JAR nhưng sealing vẫn lỗi | Hai code source URL khác nhau/đường tìm vẫn tách bytecode | Gộp package vào một JAR hoặc sửa dependency topology |
| File class có mặt nhưng loader từ chối định nghĩa | Sealing, signer consistency hoặc lỗi bytecode | Xem đầy đủ cause và nguồn class đã chọn |
| Metadata không phản ánh version mọi class | Package được định nghĩa một lần, không theo mỗi class | Kiểm tra CodeSource của class riêng |

## 12. Checklist để thiết kế JAR không lỗi sealing

1. **Biết những package nào xuất hiện trong các dependency JAR.** Kiểm tra entry `demo/locked/*.class` bằng `jar --list --file ...`.
2. **Đọc manifest thật trong JAR runtime.** Xác minh main/per-entry `Sealed`, tên package path, dòng trống và cấu trúc manifest.
3. **Không rải một sealed package qua nhiều JAR trong cùng defining loader.** Nếu cần split, cân nhắc tổ chức lại module/package hoặc bỏ sealing có chủ đích sau khi xem tác động.
4. **Không nhầm parent loader với superclass của ClassLoader.** Sealing kiểm soát package class được một loader định nghĩa từ code source; parent delegation có thể đưa class từ loader khác.
5. **Nhất quán build và runtime classpath.** Maven/Gradle shading, fat JAR hoặc relocation có thể thay đổi package layout và manifest; kiểm tra artifact cuối sau build.
6. **Không copy `Sealed: true` từ JAR khác vô thức.** Nếu đóng nhiều thư viện vào một JAR, cần thiết kế metadata và nguồn code phù hợp.
7. **Xử lý package metadata/version theo đúng nghĩa.** Không xem `Implementation-Version` là contract kiểm soát tương thích.
8. **Có test cả thứ tự load thuận/ngược khi split package.** Lỗi sealing có thể xuất hiện ở bước nạp class thứ hai, không nhất thiết ngay khi tạo loader.
9. **Phân biệt classpath và module path.** Với JPMS, kiểm tra module resolution và package encapsulation riêng.
10. **Giữ quyền truy cập file và nguồn code đáng tin cậy.** Sealing không thay thế xác minh integrity hoặc sandbox.

## 13. Câu hỏi tự kiểm tra

**Câu 1.** `a.jar` khai báo `Name: demo/locked/`, `Sealed: true`. `b.jar` chứa class khác trong `demo.locked`. Một `URLClassLoader` đã nạp Anchor từ A, rồi nạp Other từ B. Kết quả?

**Đáp án:** Vi phạm sealing, thường `SecurityException`, vì package đã sealed tại code source A.

**Câu 2.** Nếu class B được nạp trước khi class A, lỗi sealing có chắc biến mất không?

**Đáp án:** Không. B có thể tạo `demo.locked` dạng unsealed, sau đó A muốn sealed cùng package trong loader nên cũng gây sealing violation.

**Câu 3.** `Sealed: true` ở main section khác gì ở `Name: demo/locked/`?

**Đáp án:** Main section đặt default cho tất cả package của JAR. Named section đặt/ghi đè giá trị cho package cụ thể.

**Câu 4.** Hai ClassLoader độc lập nạp hai class cùng `demo.locked` từ hai JAR khác nhau có nhất thiết vi phạm sealing không?

**Đáp án:** Không. Mỗi loader có namespace package riêng; việc sealing được kiểm tra trong lúc một loader định nghĩa class thuộc package của chính nó.

**Câu 5.** `Package.getImplementationVersion()` có tự ngăn ứng dụng chạy sai phiên bản dependency không?

**Đáp án:** Không. Đây là metadata, không phải dependency solver; ứng dụng/build tool phải tự kiểm tra compatibility.

**Câu 6.** Package sealed có đồng nghĩa JAR đã được ký số và đáng tin cậy không?

**Đáp án:** Không. Sealing nhằm nhất quán nguồn class trong package; signing và kiểm soát quyền chạy là các vấn đề khác.

**Câu 7.** Nếu hai JAR chứa **cùng binary class name** nhưng loader chỉ nạp class từ JAR A, có tự động sealing violation không?

**Đáp án:** Không. Lỗi sealing liên quan đến cố định nghĩa nhiều class thuộc cùng package từ những code source không tương thích; chỉ có file trùng tên nằm trên disk chưa đồng nghĩa loader đã định nghĩa cả hai.

## 14. Tài liệu chính thức

- [Java SE 17 — JAR File Specification, Package Sealing](https://docs.oracle.com/en/java/javase/17/docs/specs/jar/jar.html): ngữ nghĩa `Sealed` ở main/per-package section và code source.
- [Java SE 17 — URLClassLoader](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html): URL search, `definePackage(Manifest, URL)`, package metadata.
- [Java SE 17 — Package](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Package.html): `isSealed`, `isSealed(URL)`, specification/implementation metadata và compatibility.
- [Java SE 17 — JarFile](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/jar/JarFile.html): đọc JAR và manifest.
- [Java SE 17 — Manifest](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/jar/Manifest.html): main attributes và named entries.
- [Java Virtual Machine Specification 17 — Loading and Linking](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): runtime class identity và quy trình class loading.

**Ghi nhớ:** `Sealed: true` trong manifest ràng buộc class của **một package** phải xuất phát từ **một code source JAR nhất quán trong cùng defining loader**. Manifest có thể đặt default toàn JAR hoặc override ở từng package. Metadata `Specification-*`/`Implementation-*` hỗ trợ mô tả version, nhưng không tự đảm bảo compatibility hay bảo mật.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="packages-and-code-sources-004">CodeSource và vị trí nguồn bytecode</a>

<details>
<summary>Xem chi tiết</summary>

# CodeSource và vị trí nguồn bytecode trong Java ClassLoader

## 1. Vấn đề: class này “đến từ JAR nào”?

Khi gặp `ClassNotFoundException`, `NoClassDefFoundError`, `NoSuchMethodError`, xung đột nhiều phiên bản thư viện hoặc nghi ngờ plugin bị nạp sai, một câu hỏi chẩn đoán rất hữu ích là: **JVM đã nhận định nghĩa class đang chạy từ nguồn nào?**

Một cách thường thấy:

~~~java
Class<?> type = SomeLibraryClass.class;
java.security.CodeSource source =
        type.getProtectionDomain().getCodeSource();
java.net.URL location = source == null ? null : source.getLocation();
System.out.println(location);
~~~

Nhưng phép in này cần được hiểu đúng:

- **`CodeSource` là metadata về nguồn mã gắn với `ProtectionDomain` của class**; nó không phải một API bắt buộc trả **đường dẫn file `.class` cụ thể đã được đọc**.
- **`CodeSource.getLocation()` là một `URL`**, có thể trỏ đến **JAR**, **directory**, một địa chỉ nguồn khác, hoặc **`null`**.
- **`Class.getProtectionDomain()`** là bước lấy protection domain gắn với runtime class; CodeSource có thể `null`, và nếu có CodeSource thì `location` vẫn có thể `null`.
- **Custom ClassLoader có thể chủ động gán CodeSource** khi define class; URL được khai báo không tự trở thành bằng chứng rằng bytes thực sự được lấy từ URL đó.
- **Defining loader, code source, resource URL và nguồn class file trong build** là những khái niệm khác nhau.

Bài thuộc nhóm **Package và nguồn mã**, đi sâu vào **CodeSource và vị trí nguồn bytecode**: ý nghĩa, API liên quan, các trường hợp dễ hiểu sai và cách chẩn đoán đáng tin cậy. Ví dụ Java 17+ phía dưới kiểm chứng thực tế cả trường hợp nạp từ JAR, nạp bytes tự viết và gán URL nguồn mô tả.

## 2. Các đối tượng tham gia

### 2.1. `Class<?>`: class thực sự mà chương trình đang dùng

~~~java
Class<?> type = java.util.ArrayList.class;
~~~

`type` đại diện cho **runtime class** cụ thể. Hai class có cùng binary name nhưng được định nghĩa bởi hai ClassLoader khác nhau có thể là **hai `Class<?>` objects khác nhau**.

~~~java
System.out.println(type.getName());
System.out.println(type.getClassLoader());
System.out.println(type.getModule());
~~~

Ba biểu thức là **snippet minh họa**, đặt trong một phương thức của bạn; chúng giúp xác định **tên**, **defining loader** và **module**. Chúng không tự cho biết toàn bộ lịch sử đọc từng byte từ disk/network.

### 2.2. `ProtectionDomain`: miền bảo vệ được gắn với class

~~~java
java.security.ProtectionDomain domain = type.getProtectionDomain();
~~~

`ProtectionDomain` mô tả context bảo vệ của một class, có liên hệ lịch sử với cơ chế cấp permissions và code source của Java security architecture.

Về cấu trúc, protection domain có thể liên hệ:

- `CodeSource` — thông tin về nguồn mã.
- `ClassLoader` — loader liên quan.
- `PermissionCollection` — dữ liệu permission theo mô hình security cũ hoặc API liên quan.
- `Principal[]` trong những dạng protection domain tương ứng.

**Không dùng `ProtectionDomain` như một hệ thống sandbox hiện đại chỉ bằng cách đặt metadata.** `SecurityManager` đã lỗi thời trong Java 17 và không còn là cơ chế được khuyến nghị cho phần mềm mới; hành vi còn phụ thuộc JDK đang chạy. Giá trị `ProtectionDomain` hữu ích cho **chẩn đoán provenance**, nhưng không phải “hộ chiếu bảo mật đã xác thực đường file”.

### 2.3. `CodeSource`: codebase URL và chứng cứ ký mã khi có

~~~java
java.security.CodeSource source = domain.getCodeSource();
~~~

`java.security.CodeSource` lưu:

- **`URL location`**: codebase/origin được gán cho code.
- Thông tin về **certificates** hoặc **CodeSigners**, nếu có và phù hợp.

Hợp đồng của [CodeSource Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/CodeSource.html) cho phép URL location `null`; certificate/signers cũng có thể không có. **Không suy rằng class luôn được ký số, hoặc location URL luôn chỉ thẳng file `.class`**.

### 2.4. `ClassLoader`: cơ chế nạp lớp, không phải chính CodeSource

~~~java
ClassLoader definingLoader = type.getClassLoader();
~~~

Defining loader ảnh hưởng danh tính runtime class. Trong khi đó `CodeSource` là thông tin đi cùng `ProtectionDomain` của class; một loader có thể định nghĩa **nhiều class từ các code sources khác nhau**.

Một `URLClassLoader` dùng JAR URLs thường biết nguồn của class nó nạp từ JAR/directory và gắn thông tin nguồn phù hợp. Một custom subclass của `ClassLoader` dùng `defineClass(String, byte[], ...)` có thể được JVM gắn default protection domain, trong đó location không xác định.

## 3. Hợp đồng của `CodeSource.getLocation()`

~~~java
URL origin = codeSource.getLocation();
~~~

Ý nghĩa trả về là **URL của codebase/source được ghi nhận trong CodeSource**. Nó không đồng nghĩa với:

1. Đường dẫn **`.class` entry** ở trong JAR.
2. Đường dẫn file **`.java` source** đã biên dịch.
3. Đường dẫn JAR **mà Maven/Gradle tải ban đầu**.
4. Mỗi URL từng được `ClassLoader` kiểm tra trước khi tìm được class.
5. Một file đang còn tồn tại tại thời điểm bạn gọi method.
6. Bản ghi checksum/chứng cứ cryptographic rằng bytes đã được đọc từ URL đó.

Các tình huống điển hình:

| Class được nạp như thế nào? | `CodeSource.getLocation()` có thể cho thấy | Diễn giải |
|---|---|---|
| Application classes từ directory `build/classes` | URL directory `file:/.../build/classes/` | **Classpath root**, không phải từng file `.class` |
| Dependency từ `helper.jar` bằng `URLClassLoader` | URL `file:/.../helper.jar` | JAR chứa class, không kèm `!/pkg/Helper.class` |
| Custom loader đọc `byte[]` rồi `defineClass` không gắn nguồn | `null` location hoặc không có CodeSource | **Thiếu thông tin nguồn** không đồng nghĩa bytes không tồn tại |
| Custom `SecureClassLoader` truyền một `CodeSource` tường minh | URL do loader khai báo | **Metadata do loader cung cấp**, phải kiểm chứng nếu cần tính xác thực |
| Lớp nền tảng JDK | Có thể không có CodeSource phù hợp để in | Không nên coi `null` là lỗi; tra runtime module nếu cần |
| Framework sử dụng nested JAR hoặc memory/generated classes | URL theo loader/framework, hoặc `null` | Có thể không trùng đường file vật lý; xem loader/launcher riêng |

Không lập một luật cứng rằng mọi URL bắt đầu bằng `file:` đều là JAR, hoặc `null` luôn mang nghĩa bootstrap. Thực tế phụ thuộc loader và cách class được định nghĩa.

## 4. Cách lấy nguồn an toàn, có xử lý null

Một helper đơn giản:

~~~java
static java.net.URL codeSourceLocation(Class<?> type) {
    java.security.ProtectionDomain domain = type.getProtectionDomain();
    if (domain == null) {
        return null;
    }
    java.security.CodeSource source = domain.getCodeSource();
    return source == null ? null : source.getLocation();
}
~~~

**Đây là method minh họa**, cần đặt vào một class. Không có thao tác chuyển URL sang `Path` một cách mù quáng.

Sử dụng:

~~~java
URL location = codeSourceLocation(SomeType.class);
System.out.println(location == null ? "(unknown)" : location);
~~~

**Chú ý**: khi dùng `getProtectionDomain()` trong môi trường SecurityManager cũ, caller có thể bị kiểm tra permission; đây không phải một API guaranteed không lỗi trong mọi môi trường JDK lịch sử.

### 4.1. Vì sao phải kiểm tra hai cấp `null`?

Không nên làm thẳng:

~~~java
String p = type.getProtectionDomain()
               .getCodeSource()
               .getLocation()
               .getPath();
~~~

Dòng trên có thể ném `NullPointerException` khi CodeSource hoặc URL location vắng mặt. Ngay cả khi non-null, `URL.getPath()` **chưa phải một filesystem path đáng tin cậy** vì percent encoding, protocol khác `file:`, dạng `jar:`, nested JAR, remote URLs hoặc encoding trên Windows.

Với URL `file:` trỏ đến filesystem path, cách chuyển đúng khi cần `Path` là:

~~~java
Path path = Path.of(location.toURI());
~~~

Đoạn này là snippet minh họa, cần `import java.nio.file.Path;` và phải xử lý `URISyntaxException`. Trước khi chuyển hãy xác định `location != null` và protocol tương thích filesystem. Không dùng `Path.of` cho URL HTTP, `jrt:`, hay các schemes do framework quy định.

### 4.2. Không được hiểu mọi `CodeSource` là URLClassLoader

Những trường hợp class được nạp từ byte arrays, JDK image, instrumentation, proxy/generated classes, network/custom protocol và framework packaging có thể có nguồn khác hoặc không có URL location hữu ích.

Muốn biết loader nào định nghĩa:

~~~java
ClassLoader loader = type.getClassLoader();
System.out.println(loader == null ? "bootstrap" : loader.getClass().getName());
~~~

Khi biết loader, có thể tiếp tục đọc configuration/URLs của loader đó nếu API cho phép. Với `URLClassLoader`, `getURLs()` trả **danh sách URL search path của loader**, nhưng **đó không phải bằng chứng URL nào đã được dùng cho riêng class đang xét**.

## 5. CodeSource khác class resource URL như thế nào?

Nhiều lập trình viên dùng:

~~~java
URL resource =
        SomeType.class.getResource("SomeType.class");
~~~

Hoặc:

~~~java
String entry = SomeType.class.getName().replace('.', '/') + ".class";
ClassLoader loader = SomeType.class.getClassLoader();
URL resource = loader == null
        ? ClassLoader.getSystemResource(entry)
        : loader.getResource(entry);
~~~

Đây là **hai cách tra resource** (snippets; yêu cầu `SomeType` hợp lệ). Chúng có thể cho URL như:

~~~text
jar:file:/libs/helper.jar!/demo/lib/Helper.class
~~~

trong khi CodeSource có thể là:

~~~text
file:/libs/helper.jar
~~~

Lý do: resource URL đi đến **entry cụ thể** được tìm, còn CodeSource URL mô tả **codebase**.

Tuy nhiên:

- Resource lookup có delegation riêng, vì thế URL tìm được **không nhất thiết là bytes đã tạo `Class<?>`**, đặc biệt khi class đã được instrument/redefine hoặc loader tùy chỉnh có mapping khác.
- Một class được define từ `byte[]` có thể **không có `.class` resource** được công bố.
- Với modules, lớp nền tảng, hidden classes, nested JAR và custom class loaders, hình dạng resource URL có thể thay đổi.
- Nên xem **`CodeSource` + `ClassLoader` + `Module` + class/resource URL** như **các manh mối bổ trợ**, không đồng nhất chúng.

### 5.1. Không nhầm `getResource` với `getProtectionDomain`

| Phép gọi | Đối tượng trả về | Nội dung |
|---|---|---|
| `type.getProtectionDomain()` | `ProtectionDomain` | Metadata domain gắn với runtime class |
| `domain.getCodeSource()` | `CodeSource` hoặc `null` | Codebase/certificates/signers khi có |
| `source.getLocation()` | `URL` hoặc `null` | URL codebase nguồn mã được ghi nhận |
| `type.getClassLoader()` | `ClassLoader` hoặc `null` | Defining loader (null thường tương ứng bootstrap) |
| `type.getModule()` | `Module` | Runtime module, tên có thể null với unnamed module |
| `type.getResource("X.class")` | `URL` hoặc `null` | Tra resource liên quan tới class theo quy tắc API |
| `urlLoader.getURLs()` | `URL[]` | Search path của loader, không phải một URL cho từng class |

## 6. Thực hành Java 17+: CodeSource có URL của JAR, URL null và nguồn tổng hợp

Ví dụ sau chỉ cần JDK 17+, không cần Maven/Gradle. Chúng ta chuẩn bị **một class library**, đọc nó bằng ba con đường khác nhau:

1. `URLClassLoader` nhận URL của `helper.jar` → `CodeSource.location` ghi nhận URL JAR.
2. Custom `ClassLoader` đọc `Helper.class` từ một directory rồi dùng `defineClass` mặc định → **location không xác định**.
3. Custom `SecureClassLoader` vẫn đọc **chính file class đó** từ directory nhưng gắn một CodeSource URL **tổng hợp** không phải vị trí đọc bytes → minh họa lý do **không thể coi source URL là chứng cứ mạnh**.

### 6.1. Cấu trúc thư mục

~~~text
demo/
├── src/
│   ├── app/
│   │   └── OriginDemo.java
│   └── lib/
│       └── demo/
│           └── lib/
│               └── Helper.java
└── build/
    ├── app/
    ├── lib/
    │   └── demo/lib/Helper.class
    └── helper.jar
~~~

**Điều kiện tái hiện:** khi chạy, `-cp` chỉ chứa `build/app`, **không chứa `build/lib` hoặc `build/helper.jar`**. Nếu đưa chúng lên application classpath, parent-first delegation có thể định nghĩa `demo.lib.Helper` trước, làm sai ý nghĩa của so sánh loaders.

### 6.2. Tệp `src/lib/demo/lib/Helper.java`

~~~java
package demo.lib;

public final class Helper {
    public static String value() {
        return "HELPER_OK";
    }
}
~~~

Class này dùng một tên nhị phân duy nhất. Chương trình sẽ tạo các **runtime class khác nhau có cùng tên** dưới các defining loaders khác nhau; đây là hành vi hợp lệ trong mô hình class loading khi không có loading constraints xung đột.

### 6.3. Tệp `src/app/OriginDemo.java`

~~~java
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.SecureClassLoader;
import java.security.cert.Certificate;

public class OriginDemo {
    static final String NAME = "demo.lib.Helper";

    static final class PlainBytesLoader extends ClassLoader {
        private final Path root;

        PlainBytesLoader(Path root) {
            super(OriginDemo.class.getClassLoader());
            this.root = root;
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            if (!NAME.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            try {
                byte[] bytes = Files.readAllBytes(
                        root.resolve(name.replace('.', '/') + ".class"));
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException ex) {
                throw new ClassNotFoundException(name, ex);
            }
        }
    }

    static final class LabelLoader extends SecureClassLoader {
        private final Path root;
        private final URL label;

        LabelLoader(Path root, URL label) {
            super(OriginDemo.class.getClassLoader());
            this.root = root;
            this.label = label;
        }

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            if (!NAME.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            try {
                byte[] bytes = Files.readAllBytes(
                        root.resolve(name.replace('.', '/') + ".class"));
                CodeSource declared =
                        new CodeSource(label, (Certificate[]) null);
                return defineClass(
                        name, bytes, 0, bytes.length, declared);
            } catch (IOException ex) {
                throw new ClassNotFoundException(name, ex);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Path jar = Path.of("build/helper.jar").toAbsolutePath();
        Path root = Path.of("build/lib");

        CodeSource appSource =
                OriginDemo.class.getProtectionDomain().getCodeSource();
        System.out.println("appSourceIsDirectory="
                + Files.isDirectory(
                        Path.of(appSource.getLocation().toURI())));

        try (URLClassLoader loader = new URLClassLoader(
                new URL[] { jar.toUri().toURL() },
                OriginDemo.class.getClassLoader())) {

            Class<?> cls = loader.loadClass(NAME);
            CodeSource source = cls.getProtectionDomain().getCodeSource();
            System.out.println("jarCodeSourceMatches="
                    + jar.toRealPath().equals(
                            Path.of(source.getLocation().toURI())
                                    .toRealPath()));
            System.out.println("jarDefiningLoaderMatches="
                    + (cls.getClassLoader() == loader));
            System.out.println("classResourceAvailable="
                    + (loader.getResource(
                            "demo/lib/Helper.class") != null));
        }

        Class<?> plain = new PlainBytesLoader(root).loadClass(NAME);
        CodeSource plainSource =
                plain.getProtectionDomain().getCodeSource();
        System.out.println("plainByteLocationNull="
                + (plainSource == null
                    || plainSource.getLocation() == null));

        URL label = new URL("file:/synthetic/location.jar");
        Class<?> tagged = new LabelLoader(root, label).loadClass(NAME);
        System.out.println("labeledCodeSourceSynthetic="
                + label.equals(tagged.getProtectionDomain()
                        .getCodeSource().getLocation()));
        System.out.println("sameNameDifferentClass="
                + plain.getName().equals(tagged.getName()) + ","
                + (plain != tagged));
    }
}
~~~

#### Ý đồ từng loader

- `PlainBytesLoader` đọc bytes trực tiếp bằng `Files.readAllBytes` và sử dụng `ClassLoader.defineClass(name, bytes, ...)`. Với default protection domain của JDK trong ví dụ, **không có URL location được gán**. **Không nhất thiết `CodeSource` object phải null; chính `getLocation()` có thể null**.
- `LabelLoader` extends `SecureClassLoader`, sử dụng protected overload `defineClass(name, bytes, offset, length, CodeSource)`. Nó **chủ động khai báo** một URL `file:/synthetic/location.jar` mặc dù bytes đã được đọc từ `build/lib`. Đường `/synthetic/location.jar` **không cần tồn tại** để metadata này được đặt.
- `URLClassLoader` dùng URL trỏ đến JAR thật. Nó kế thừa `SecureClassLoader` và cung cấp `CodeSource` từ nguồn được nạp khi áp dụng theo API.
- Ba loaders dùng parent application loader, nhưng `demo.lib.Helper` **không** nằm trên app runtime classpath. Vì vậy, mỗi loader có thể định nghĩa bản `Helper` riêng.

Cố ý dùng **`NAME`** và file nguồn rõ ràng để tách **định danh class** khỏi **location metadata**.

### 6.4. Biên dịch/chạy từ thư mục `demo`

Linux/macOS:

~~~sh
mkdir -p build/app build/lib
javac --release 17 -d build/lib src/lib/demo/lib/Helper.java
jar --create --file build/helper.jar -C build/lib .
javac --release 17 -d build/app src/app/OriginDemo.java
java -cp build/app OriginDemo
~~~

Windows PowerShell:

~~~powershell
New-Item -ItemType Directory -Force build/app, build/lib | Out-Null
javac --release 17 -d build/lib src/lib/demo/lib/Helper.java
jar --create --file build/helper.jar -C build/lib .
javac --release 17 -d build/app src/app/OriginDemo.java
java -cp build/app OriginDemo
~~~

**Stdout chính xác**, kiểm chứng bằng JDK 21 và `javac --release 17` sau khi dựng cây `demo`:

~~~text
appSourceIsDirectory=true
jarCodeSourceMatches=true
jarDefiningLoaderMatches=true
classResourceAvailable=true
plainByteLocationNull=true
labeledCodeSourceSynthetic=true
sameNameDifferentClass=true,true
~~~

Tất cả phép kiểm tra đều cố ý in **boolean** thay vì đường tuyệt đối phụ thuộc máy. Hai giá trị trên dòng cuối là:
1. `plain.getName().equals(tagged.getName()) == true`;
2. `plain != tagged == true`.

### 6.5. Đọc kết quả đúng

| Output | Điều chứng minh |
|---|---|
| `appSourceIsDirectory=true` | `OriginDemo` chạy từ directory `build/app` có CodeSource URL trỏ tới classpath root dạng directory |
| `jarCodeSourceMatches=true` | CodeSource location của `Helper` do `URLClassLoader` định nghĩa trùng JAR URL thực tế của demo |
| `jarDefiningLoaderMatches=true` | `Helper` trong nhánh đầu thực sự do loader được chỉ định định nghĩa |
| `classResourceAvailable=true` | Resource `demo/lib/Helper.class` tồn tại trong JAR theo đường tra của loader |
| `plainByteLocationNull=true` | Custom `defineClass` không buộc có location URL; **không đồng nghĩa bytecode chưa được đọc** |
| `labeledCodeSourceSynthetic=true` | Custom `SecureClassLoader` có thể gắn URL CodeSource do chính nó chọn |
| `sameNameDifferentClass=true,true` | Hai class cùng binary name nhưng khác defining loaders có thể là hai runtime types khác nhau |

**Giới hạn ngữ nghĩa:** ví dụ chứng minh `CodeSource` là dữ liệu được gắn khi define class, không phải phép xác minh supply chain. Nếu cần biết bytes đã được tải từ đâu thực sự, phải đối chiếu triển khai loader, artifact, cryptographic hashes và quá trình deploy.

### 6.6. Các biến thể tự thực hành

**Biến thể A — In URL thực tế:** thay các dòng boolean bằng `System.out.println(source.getLocation())`. Kết quả sẽ chứa đường dẫn theo máy hiện tại; vì vậy không đưa nó làm stdout cố định.

**Biến thể B — Thay file nguồn:** cho `PlainBytesLoader` đọc `build/lib` như cũ nhưng đổi URL `label`. Kết quả `labeledCodeSourceSynthetic` vẫn true nếu so với URL vừa gán, dù không có thay đổi về bytes. Điều đó chứng minh vai trò metadata.

**Biến thể C — Chạy với sai classpath:** thêm `build/helper.jar` vào `-cp` khi chạy. Application loader có thể định nghĩa `Helper` trước child loaders, khiến `jarDefiningLoaderMatches` không còn true. Đây là tác động của **parent-first delegation**, không phải `CodeSource` bị lỗi.

**Biến thể D — Resource lookup:** gọi `loader.getResource("demo/lib/Helper.class")` rồi in URL. Nó có thể có dạng `jar:file:...!/demo/lib/Helper.class`, khác URL CodeSource `file:.../helper.jar`.

## 7. Vì sao CodeSource không phải phép kiểm tra an toàn của artifact?

### 7.1. URL location không xác thực bytes

Hợp đồng `CodeSource(URL, certificates)` cho phép ứng dụng/loader đặt **CodeSource location** từ dữ liệu nó lựa chọn. Nhánh `LabelLoader` trong demo dùng URL không phải nơi đã đọc bytes. Bởi vậy:

~~~text
CodeSource.getLocation() != chứng cứ cryptographic về source bytes
~~~

Nếu bạn kiểm tra “plugin này có phải chính JAR đã được ký và phê duyệt không?”, **chỉ so sánh URL string là không đủ**. Hãy dùng quy trình xác minh integrity/signature hoặc hashes của artifact theo kênh tin cậy, cùng chính sách loader được kiểm soát.

### 7.2. Certificates và CodeSigners không đồng nghĩa class chắc chắn đáng tin

`CodeSource.getCertificates()` hoặc `getCodeSigners()` có thể trả null/không có dữ liệu nếu unsigned hoặc loader không cung cấp thông tin ký mã. Nếu có dữ liệu, cách tạo và xác thực dữ liệu còn phụ thuộc cơ chế xác minh JAR, trust stores, trust policy và loader đang dùng.

Việc **gắn** một `CodeSource` không tự làm ứng dụng trở thành một hệ thống xác thực chữ ký chuẩn. Với `URLClassLoader` đọc signed JARs, có cơ chế kiểm tra liên quan đến JAR verification và signer consistency; muốn xây chính sách tin cậy vẫn cần xác định đầu vào được xác minh bằng cách nào.

### 7.3. Một ProtectionDomain có thể được tái sử dụng

Khi dùng `ClassLoader.defineClass` với overload không cung cấp protection domain, JDK gắn **default protection domain** theo hợp đồng. Không nên mặc định mỗi `Class<?>` có một CodeSource URL riêng biệt hoặc một ProtectionDomain object riêng.

Mặt khác `SecureClassLoader` có thể tạo/cache protection domain dựa trên CodeSource; hai lớp từ một JAR có thể chia sẻ cùng codebase. CodeSource không phải bản ghi “file read event” cho từng class.

### 7.4. Runtime module và source location không tương đương

Với JPMS:

~~~java
Module module = SomeType.class.getModule();
System.out.println(module.isNamed());
System.out.println(module.getName());
~~~

Đây là snippet minh họa. `module.getName()` cho biết module name (có thể null với unnamed module), không phải URL của JAR. Trong module layers tùy chỉnh, module có thể đến từ runtime image, directory hoặc modular JAR; `Module` và `CodeSource` trả lời những câu hỏi khác nhau.

## 8. Chẩn đoán vị trí class trong ứng dụng thực tế

Một quy trình đáng tin cậy khi nghi ngờ sai phiên bản JAR:

1. **Xác định đúng runtime `Class<?>`:** thông qua object/stack trace hoặc class cần kiểm tra.
2. **In binary name, defining ClassLoader và module:** phân biệt hai class cùng tên do hai loaders định nghĩa.
3. **Đọc `ProtectionDomain`/`CodeSource` có null guards:** lấy location URL nếu có; nếu null, báo “không có location”, không bịa origin.
4. **Kiểm tra resource URL và class loading log:** `SomeType.class.getResource("SomeType.class")`, cùng các phương tiện riêng của loader/framework nếu cần.
5. **So runtime JAR với build dependency graph:** trong Maven/Gradle, version resolve là một chuyện; file được copy vào container, JAR xuất hiện trên launcher classpath và loader thực tế là chuyện khác.
6. **Xem actual launcher:** `java -cp`, `java -jar`, module path, IDE, Spring Boot launcher, application server hoặc plugin container có thể dựng classpath khác nhau.
7. **Nếu URL trỏ tới JAR:** dùng `jar --list --file path/to/foo.jar` và công cụ checksum để đối chiếu artifact, không chỉ tin URL.
8. **Nếu URL null hoặc tổng hợp:** đọc implementation custom loader, nguồn bytes/caches, resource mapping, protection domain được gán ở đâu.
9. **Nếu class tồn tại nhưng vẫn lỗi:** xem lỗi linkage cụ thể, `NoSuchMethodError`, `IllegalAccessError`, `ClassCastException`, version và module access.
10. **Nếu cần bằng chứng bảo mật:** xác minh cryptographic hashes hoặc JAR signatures theo chính sách tin cậy riêng; CodeSource metadata chỉ là manh mối.

### 8.1. Log class loading ở HotSpot

Có thể chạy chương trình với:

~~~sh
java -Xlog:class+load=info -cp build/app OriginDemo
~~~

Log thường bao gồm thông tin tên class và nguồn mà VM/loader báo cáo, nhưng định dạng và độ đầy đủ phụ thuộc runtime/JDK. Không coi `-Xlog` là API thay thế một phép xác minh bytecode độc lập.

### 8.2. So source location với bytes thật

Nếu cần kết luận “class được định nghĩa bằng những byte nào”, thông tin CodeSource **chưa đủ**. Cần:

- Thu đúng artifact JAR/version trong môi trường runtime và hash nó.
- Hoặc với custom loader, instrument/log tại vị trí `readAllBytes`/`defineClass` và hash đúng mảng bytes trước khi define.
- Với agent redefinition/instrumentation, chú ý class bytes đã chạy có thể được biến đổi so với bytes trong file ban đầu.
- Giữ log có timestamp/version/loader identity và tránh ghi đường dẫn/bí mật nhạy cảm trong production logs.

**Giới hạn của tài liệu:** Java core không cung cấp một API phổ dụng `Class.getOriginalBytecodePath()` trả chính xác nguồn gốc của mọi class trong mọi ClassLoader. `CodeSource` là một mảnh thông tin, không phải lời giải xác thực toàn bộ.

## 9. Những nhầm lẫn cần tránh

1. **“`getCodeSource().getLocation()` luôn là file JAR.”** Sai: có thể là directory, URL khác hoặc null.
2. **“Nếu CodeSource null thì JVM không load được class.”** Sai: class đã tồn tại; chỉ thiếu metadata nguồn.
3. **“CodeSource object luôn null khi custom loader dùng `defineClass(byte[])`.”** Sai: nó có thể tồn tại nhưng location null như trường hợp đã kiểm chứng.
4. **“`getLocation()` trả đường dẫn file `.class`.”** Sai với JAR source: thường là URL của **archive/codebase**, không phải entry riêng.
5. **“Có class resource URL thì bytes đang chạy chắc chắn khớp resource hiện tại.”** Sai với custom loaders, instrumentation, modified archives và các cơ chế caching khác.
6. **“Tìm thấy một `.class` resource chứng minh loader đó đã định nghĩa runtime class.”** Sai: resource lookup và defining loader/class identity là các bước riêng.
7. **“CodeSource URL là bằng chứng chữ ký số đã được kiểm chứng.”** Sai: URL không xác minh cryptographic provenance.
8. **“Hai class cùng tên cùng CodeSource URL thì luôn cùng runtime type.”** Sai nếu defining loaders khác nhau.
9. **“Maven/Gradle cache path luôn chính là CodeSource location trong runtime.”** Sai với shaded JAR, repackaging, container và custom loader.
10. **“`URL.getPath()` là filesystem Path có thể dùng trực tiếp.”** Sai nếu URL encoded/protocol khác file hoặc đường platform-specific.
11. **“Module name cho biết JAR URL.”** Sai: module identity và code source origin khác nhau.
12. **“Classpath system liệt kê đủ mọi nguồn class.”** Sai với bootstrap/platform modules và plugin/framework loaders.
13. **“Nếu location trỏ đến file không tồn tại thì class chưa từng được load.”** Sai: URL có thể là metadata tổng hợp, file đã bị di chuyển hoặc loader đã đọc bytes vào memory.
14. **“Có thể đổi CodeSource để an toàn hoá plugin mà không cần thêm gì.”** Sai: an toàn cần trust policy và cơ chế xác minh riêng.

## 10. Câu hỏi tự kiểm tra

1. `CodeSource` lưu những loại thông tin nào ngoài URL?
2. `Class.getProtectionDomain()` giúp truy cập metadata nào?
3. Vì sao nên có null guards cho cả `CodeSource` lẫn `getLocation()`?
4. Class nạp từ directory và class nạp từ JAR thường khác hình dạng location URL thế nào?
5. Với `jar:file:/libs/x.jar!/p/X.class`, CodeSource location thường trỏ tới URL nào?
6. Vì sao `CodeSource.getLocation()` không chứng minh bytes thực sự được đọc từ URL đó?
7. Custom `ClassLoader.defineClass(name, bytes, ...)` có buộc location URL không null không?
8. `SecureClassLoader.defineClass(name, bytes, ..., CodeSource)` cho phép loader kiểm soát metadata nào?
9. Hai classes cùng binary name cùng source URL nhưng loader khác nhau có buộc cùng runtime identity không?
10. Đọc `SomeType.class.getResource("SomeType.class")` có luôn cung cấp exact loaded bytes không? Vì sao?
11. Tại sao dùng `Path.of(location.toURI())` chỉ phù hợp khi URL thể hiện filesystem resource?
12. Khi gặp `NoSuchMethodError`, bạn nên xác minh artifact/version nào đang chạy bằng cách nào?
13. `Module.getName()` và `CodeSource.getLocation()` trả lời những câu hỏi khác nhau ra sao?
14. Khi custom loader dùng bytes từ memory/network, CodeSource có thể có trạng thái nào?
15. Trong ví dụ, vì sao `labeledCodeSourceSynthetic=true` mà bytes vẫn được đọc từ `build/lib`?
16. Nếu một class bị agent instrument sau khi đã nạp, CodeSource có chứng minh bytes đang chạy không bị biến đổi không?
17. Khi CodeSource location có giá trị `null`, những nguồn chẩn đoán bổ sung nào có ích?
18. Vì sao nên dùng checksums/signature verification thay vì chỉ so URL nếu yêu cầu xác thực nguồn của plugin?

**Tiêu chí hoàn thành:** chạy được ví dụ Java 17+ và giải thích đủ bảy dòng stdout; phân biệt **CodeSource URL**, **class resource**, **defining loader**, **Module** và **bytes đã định nghĩa class**; biết xử lý null và lập một quy trình chẩn đoán không kết luận vượt quá những gì API cung cấp.

## 11. Tài liệu chính thức

- [Java SE 21 — `CodeSource`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/CodeSource.html): URL location, certificates, code signers, `getLocation()`.
- [Java SE 21 — `ProtectionDomain`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/ProtectionDomain.html): miền bảo vệ của class và metadata CodeSource.
- [Java SE 21 — `Class.getProtectionDomain()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#getProtectionDomain()): lấy protection domain gắn với runtime class.
- [Java SE 21 — `SecureClassLoader`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/SecureClassLoader.html): `defineClass` overload có `CodeSource`.
- [Java SE 21 — `URLClassLoader`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/net/URLClassLoader.html): tìm class từ URL JAR/directory và thông tin source liên quan.
- [Java SE 21 — `ClassLoader`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): `defineClass`, default protection domain, delegation và defining loader.

Trong ví dụ, **CodeSource metadata được tạo bởi loader**, nên bài không đưa ra lời hứa rằng URL tự xác thực một artifact. Java 17+ đủ để biên dịch và chạy; stdout được kiểm chứng với JDK 21 và `javac --release 17`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="packages-and-code-sources-005">ProtectionDomain trong các biến thể defineClass</a>

<details>
<summary>Xem chi tiết</summary>

# ProtectionDomain trong các biến thể `defineClass`

> **Java Core → ClassLoader → Package và nguồn mã.** `ProtectionDomain` là metadata gắn với runtime class khi classloader định nghĩa class từ bytecode. Nó kết nối **`CodeSource` (nguồn mã/chứng chỉ)**, một **`ClassLoader`**, **`Principal`** và thông tin `PermissionCollection` của kiến trúc bảo mật Java. Bài này so sánh đúng các overload `defineClass`, chỉ ra cách truyền domain tường minh hoặc nhận domain mặc định, quy tắc certificate consistency trong cùng package, cách dùng `SecureClassLoader`, và đặc biệt phân biệt **metadata nguồn mã** với **sandbox thực sự** trên các phiên bản JDK hiện đại.

## 1. Câu trả lời ngắn: `ProtectionDomain` liên quan gì đến `defineClass`?

Một custom loader thường gọi `defineClass` từ `findClass` sau khi lấy bytes:

~~~java
Class<?> type = defineClass(name, classBytes, 0, classBytes.length);
~~~

Lời gọi này định nghĩa class với một **ProtectionDomain mặc định**. Nếu muốn gắn domain do chương trình cung cấp, sử dụng overload:

~~~java
Class<?> type = defineClass(
        name, classBytes, 0, classBytes.length, explicitDomain);
~~~

Đây đều là **snippet trong subclass của `ClassLoader`**, không phải code hợp lệ để gọi từ một class ứng dụng bất kỳ: `defineClass` là `protected final`. Một cách khác là dùng overload `ByteBuffer` hoặc lớp con `SecureClassLoader` nhận `CodeSource`.

**Điều quan trọng nhất:**

- `defineClass` tạo **runtime `Class<?>`** từ bytecode; `ProtectionDomain` là domain **được gắn với class được định nghĩa**.
- `CodeSource` trong domain giúp mô tả **vị trí và thông tin signer/certificate** được cung cấp qua quá trình nạp đã xác thực; nó **không bảo đảm** tự đi đọc class file từ URL ấy.
- Nếu truyền `null` cho `ProtectionDomain`, VM/JDK dùng **domain mặc định** theo hợp đồng `ClassLoader`.
- Với JDK 24 trở lên, **Security Manager đã bị vô hiệu hóa vĩnh viễn**. Gắn `ProtectionDomain` hay cấp `Permission` không biến plugin thành một sandbox có thể giới hạn truy cập tài nguyên. Tuy nhiên metadata CodeSource, một số kiểm tra về tên package/chứng chỉ và quy tắc định nghĩa class vẫn liên quan.

### Bảng overload cần nhớ

| API trong subclass | Domain được gắn | Ghi chú |
| --- | --- | --- |
| `defineClass(byte[] b, int off, int len)` | Không có đối số domain; cơ chế mặc định | **Deprecated** từ Java 1.1; không truyền binary name, không nên dùng |
| `defineClass(String name, byte[] b, int off, int len)` | **Domain mặc định** | Đề xuất cơ bản khi không có metadata nguồn riêng |
| `defineClass(String name, byte[] b, int off, int len, ProtectionDomain pd)` | `pd` nếu không-null; nếu null thì default | Kiểm tra package certificates, tên và class-file bytes |
| `defineClass(String name, ByteBuffer b, ProtectionDomain pd)` | `pd` hoặc default | Tương đương về ý nghĩa định nghĩa class từ bytes còn lại của buffer |
| `SecureClassLoader.defineClass(String name, byte[] b, int off, int len, CodeSource cs)` | Tạo `ProtectionDomain` từ `cs` khi `cs != null` | Convenience API của `SecureClassLoader` |
| `SecureClassLoader.defineClass(String name, ByteBuffer b, CodeSource cs)` | Tương tự overload bytes | Dùng `CodeSource` trực tiếp, không truyền một `ProtectionDomain` |

**Cẩn thận:** Java cũng có các API đặc biệt như `MethodHandles.Lookup.defineClass` và `Lookup.defineHiddenClass`. Chúng có semantics lookup/loader/package/hidden class riêng, **không phải các overload của `ClassLoader.defineClass`** được giải thích ở đây.

## 2. Cấu trúc dữ liệu: `ProtectionDomain` chứa những gì?

Các thành phần chính:

~~~text
Class<?> runtimeType
       |
       v
ProtectionDomain
       +-- CodeSource
       |      +-- URL/location (có thể null)
       |      +-- Certificates / CodeSigners (có thể null)
       +-- PermissionCollection (metadata/legacy policy)
       +-- ClassLoader (có thể null)
       +-- Principal[] (thường có thể rỗng)
~~~

Các API thường dùng:

~~~java
ProtectionDomain domain = SomeClass.class.getProtectionDomain();
CodeSource source = domain.getCodeSource();
ClassLoader defining = SomeClass.class.getClassLoader();

if (source != null && source.getLocation() != null) {
    System.out.println(source.getLocation());
}
~~~

Đây là **snippet**, cần import `java.security.ProtectionDomain` và `java.security.CodeSource`. `getProtectionDomain()` trả domain liên kết với runtime class, nhưng mã chẩn đoán không nên mặc định mọi `Class` hay mọi runtime image đều có `CodeSource` khác null.

### 2.1. `CodeSource` không phải “nguồn bytes” do VM tự kiểm chứng

`new CodeSource(URL, Certificate[])` mô tả URL và certificates được truyền vào. Nếu bạn ghi `file:/example/plugins/a.jar` vào `CodeSource` nhưng lại định nghĩa bytes đọc từ nơi khác, chỉ riêng `defineClass` **không tự so khớp URL với nguồn thực tế** và không xác thực archive dựa trên tên URL.

Vì vậy:

- URL có thể chỉ là **metadata** do custom loader quyết định.
- Không dùng một `CodeSource` tự chế làm bằng chứng chữ ký số hợp lệ.
- Khi dùng JAR signed, code nạp phải thực sự **verify JAR/signature** bằng đường đọc/JAR verification thích hợp rồi mới tin certificates được trả.
- `CodeSource.getCertificates()` và `CodeSource.getCodeSigners()` phản ánh thông tin đã được cung cấp/được chuyển đổi theo API; `null`/rỗng không tự suy ra code được chứng thực.

**Khác biệt quan trọng:** `ProtectionDomain` là thuộc tính của một **runtime class**; `CodeSource` chỉ là một phần metadata của domain. Cùng một physical JAR có thể được định nghĩa bởi nhiều loader thành các runtime types khác nhau.

### 2.2. Hai kiểu constructor chính của `ProtectionDomain`

~~~java
ProtectionDomain staticDomain =
        new ProtectionDomain(codeSource, permissions);

ProtectionDomain domainWithContext =
        new ProtectionDomain(
                codeSource, permissions, definingLoader, principals);
~~~

Đây là **snippet cần biến phù hợp**. Bản hai tham số biểu diễn một domain với permissions được truyền; bản bốn tham số còn lưu loader và principals, phục vụ mô hình policy có ngữ cảnh của Security Manager trước đây. Trên JDK 24+ **không thể biến những permissions này thành ranh giới chặn file/network** chỉ bằng cách truyền domain cho `defineClass`.

Đừng suy diễn `domain.getPermissions() == null` là bằng chứng class “được phép tất cả”, cũng không suy diễn `getPermissions()` có một `FilePermission` là tiến trình sẽ bị chính sách Java mới chặn thao tác khác. Cần xét phiên bản JDK và hệ enforcement thực sự.

## 3. Domain mặc định: không truyền gì hoặc truyền `null`

Javadoc của `ClassLoader.defineClass(String, byte[], int, int)` xác nhận: khi không nêu domain, class nhận **ProtectionDomain mặc định**. JDK tạo default domain khi lần đầu cần và **tái sử dụng** cho những lần gọi tiếp theo của cơ chế mặc định.

Với overload năm tham số:

~~~java
defineClass(name, bytes, 0, bytes.length, null);
~~~

`null` ở vị trí `protectionDomain` có nghĩa dùng đúng **default domain**, không có nghĩa class không có domain.

**Đừng đoán cấu trúc bên trong default domain từ một JDK cụ thể.** Trên một số bản runtime, `getCodeSource()` có thể là đối tượng khác null nhưng URL của nó null; trên JDK 24+, `getPermissions()` của default domain theo tài liệu JDK là `null`. Do đó code chẩn đoán nên kiểm tra riêng:

~~~java
CodeSource cs = clazz.getProtectionDomain().getCodeSource();
boolean hasSourceLocation =
        cs != null && cs.getLocation() != null;
~~~

Snippet này cần biến `clazz` hợp lệ. `hasSourceLocation == false` không có nghĩa Java class file chưa từng được đọc; chỉ là domain không mang URL nguồn mã ở trường đang kiểm tra.

### 3.1. Tại sao không nên lạm dụng default domain?

Khi load một lượng lớn plugins/dependencies từ nhiều nơi, để tất cả nhận default domain khiến metadata không cho biết chúng đến từ artifact nào. Nếu bạn cần audit/provenance thực tế, có thể gắn một `CodeSource` được xác nhận, nhưng điều này phải đi kèm **xác thực nguồn bytes**, versioning, logging và quản lý loader.

Domain mặc định đủ cho nhiều loader nội bộ không dùng legacy permissions, miễn là bạn không dựa vào nó để thay cơ chế kiểm tra danh tính, đường dẫn hoặc nguồn tin cậy.

## 4. Overload nhận `ProtectionDomain` tường minh

Chữ ký chuẩn:

~~~java
protected final Class<?> defineClass(
    String name, byte[] b, int off, int len,
    ProtectionDomain protectionDomain);
~~~

Đặc điểm:

1. **`name`** là binary name, ví dụ `org.example.Plugin` — không phải path `org/example/Plugin.class`. Có thể truyền null theo API nhưng **nên truyền tên cụ thể**.
2. **`b, off, len`** giới hạn vùng byte chứa một class file hợp lệ. Offset/length sai có thể dẫn tới `IndexOutOfBoundsException`.
3. **`protectionDomain`** nếu khác null sẽ được gắn với class; nếu null dùng domain mặc định.
4. Khi bytes không phải class file hợp lệ, `ClassFormatError` (hoặc lỗi subtype/liên quan) có thể xảy ra.
5. Nếu `name` khác binary name khai báo trong bytes, JVM có thể ném **`NoClassDefFoundError`**.
6. Nếu tên bắt đầu `java.` và defining loader không thuộc loại được phép theo API, có **`SecurityException`**.
7. Trong cùng package của một loader, những classes được định nghĩa phải có **cùng bộ signing certificates**, nếu không sẽ gặp **`SecurityException`**.
8. Định nghĩa trùng binary name trong **cùng loader** là một vấn đề khác, có thể ném **`LinkageError`**; thêm domain khác không làm nó hợp lệ.

**Bảo mật thực sự không tự xuất hiện:** overload này gắn metadata domain; nó không tự cách ly code plugin khỏi Java API hay quyền truy cập của process.

### 4.1. `name == null` làm suy yếu kiểm tra nào?

Javadoc nêu rằng bước đối chiếu certificate set của package **không được thực hiện theo cùng cách khi `name` null**. Vì vậy overload cũ `defineClass(byte[],off,len)`, vốn không nhận tên, bị deprecated và không được khuyến khích dùng. Hãy cung cấp **binary name đáng tin** và xác nhận bytecode đúng tên ấy.

Không dùng `name=null` để “vượt lỗi ký JAR”: điều đó làm yếu kiểm tra consistency và tạo rủi ro thiết kế. Nếu package signer mismatch xảy ra, giải quyết bằng artifact/certificate đúng, tránh trộn signed và unsigned classes trong cùng package của loader.

## 5. Ví dụ Java 17+: gắn `ProtectionDomain`, đọc metadata, thử default và `ByteBuffer`

Thí nghiệm có **hai file Java**: một class plugin và host loader. Để không bị parent loader nạp class plugin trước, hãy **biên dịch plugin ra thư mục tách riêng**, còn khi chạy chỉ đặt host classes trên application classpath.

Cấu trúc:

~~~text
pd-lab/
├── plugin-src/
│   └── demo/plugin/Greeter.java
├── host-src/
│   └── ProtectionDomainDemo.java
└── build/
    ├── plugin/   (sinh ra)
    └── host/     (sinh ra)
~~~

Tệp **`plugin-src/demo/plugin/Greeter.java`**:

~~~java
package demo.plugin;

public class Greeter {
    static {
        System.out.println("Greeter initialized");
    }

    public Greeter() { }
}
~~~

Tệp **`host-src/ProtectionDomainDemo.java`**:

~~~java
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.Permissions;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;

public class ProtectionDomainDemo {
    static final class ByteLoader extends ClassLoader {
        ByteLoader() {
            super(ProtectionDomainDemo.class.getClassLoader());
        }

        Class<?> withDomain(
                String name, byte[] bytes, ProtectionDomain pd) {
            return defineClass(name, bytes, 0, bytes.length, pd);
        }

        Class<?> withDefault(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }

        Class<?> withBuffer(
                String name, ByteBuffer bytes, ProtectionDomain pd) {
            return defineClass(name, bytes, pd);
        }
    }

    public static void main(String[] args) throws Exception {
        byte[] bytes = Files.readAllBytes(
                Path.of("build/plugin/demo/plugin/Greeter.class"));
        String name = "demo.plugin.Greeter";

        ByteLoader specific = new ByteLoader();

        // URL dưới đây là metadata, KHÔNG cần file JAR tồn tại.
        CodeSource source = new CodeSource(
                Path.of("provenance/plugin-origin.jar").toUri().toURL(),
                (Certificate[]) null);

        Permissions permissions = new Permissions();
        permissions.add(new RuntimePermission("lesson.marker"));
        permissions.setReadOnly();

        ProtectionDomain domain = new ProtectionDomain(
                source, permissions, specific, new Principal[0]);

        Class<?> type = specific.withDomain(name, bytes, domain);
        ProtectionDomain actual = type.getProtectionDomain();

        System.out.println("custom loader="
                + (type.getClassLoader() == specific));
        System.out.println("source marker="
                + actual.getCodeSource().getLocation().getPath()
                    .endsWith("plugin-origin.jar"));
        System.out.println("domain loader="
                + (actual.getClassLoader() == specific));
        System.out.println("permission metadata="
                + actual.getPermissions().implies(
                    new RuntimePermission("lesson.marker")));

        System.out.println("defined, not initialized");
        type.getDeclaredConstructor().newInstance();
        System.out.println("instance created");

        ByteLoader defaultLoader = new ByteLoader();
        Class<?> defaultClass =
                defaultLoader.withDefault(name, bytes);
        CodeSource defaultSource =
                defaultClass.getProtectionDomain().getCodeSource();
        System.out.println("default source has location="
                + (defaultSource != null
                    && defaultSource.getLocation() != null));

        ByteLoader bufferLoader = new ByteLoader();
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        Class<?> fromBuffer =
                bufferLoader.withBuffer(name, buffer, null);
        System.out.println("buffer class=" + fromBuffer.getName());

        try {
            new ByteLoader().withDomain(
                    "demo.plugin.Other", bytes, domain);
            System.out.println("wrong name rejected=false");
        } catch (NoClassDefFoundError expected) {
            System.out.println("wrong name rejected=true");
        }
    }
}
~~~

Từ thư mục `pd-lab`, với JDK 17+ (POSIX shell):

~~~bash
mkdir -p build/plugin build/host
javac --release 17 -d build/plugin \
    plugin-src/demo/plugin/Greeter.java
javac --release 17 -d build/host \
    host-src/ProtectionDomainDemo.java
java -cp build/host ProtectionDomainDemo
~~~

**Stdout kỳ vọng trên OpenJDK 21 thông thường:**

~~~text
custom loader=true
source marker=true
domain loader=true
permission metadata=true
defined, not initialized
Greeter initialized
instance created
default source has location=false
buffer class=demo.plugin.Greeter
wrong name rejected=true
~~~

Giải thích:

1. `withDomain` gắn một domain được tạo tường minh vào class của loader `specific`. `getClassLoader()` của class là loader ấy.
2. `CodeSource` có URL được chương trình truyền vào; dòng “source marker” **chỉ kiểm tra metadata**, không chứng minh file `plugin-origin.jar` tồn tại hay đã được verify.
3. `permission metadata=true` phản ánh `PermissionCollection` có một marker do chúng ta **tự đặt**, không phải quyền truy cập mà hệ điều hành/JDK24+ đang cưỡng chế.
4. `defineClass` chưa kích hoạt initializer, nên `Greeter initialized` xuất hiện khi gọi constructor bằng reflection, **sau** dòng `defined, not initialized`.
5. Loader `defaultLoader` dùng overload không có domain nên không có URL source được khai báo tường minh; ví dụ kiểm tra **location** thay vì giả định cả đối tượng `CodeSource` bằng `null`.
6. `ByteBuffer` định nghĩa một bản class khác dưới **loader mới** với domain mặc định. Không dựa vào việc original buffer sẽ thay đổi `position()`/`limit()` sau lời gọi; nếu cần tái sử dụng, dùng `duplicate()`/`slice()` và quản lý state tường minh.
7. Tên `demo.plugin.Other` không khớp tên trong class bytes `demo.plugin.Greeter`, nên JVM từ chối bằng `NoClassDefFoundError`.

**Giới hạn ví dụ:** Mỗi lần định nghĩa cùng binary name sử dụng **ByteLoader instance mới**. Nếu cố định nghĩa lại `demo.plugin.Greeter` trong chính `specific`, bạn sẽ gặp duplicate-definition error. Không thêm thư mục `build/plugin` vào `java -cp` của host khi muốn giữ thí nghiệm tách loader.

## 6. `ByteBuffer` overload: cùng quy tắc domain, khác hình thức bytes

Overload:

~~~java
protected final Class<?> defineClass(
    String name, ByteBuffer data, ProtectionDomain pd);
~~~

Nó sử dụng class bytes từ buffer theo bounds/remaining đã cấu hình, áp dụng **cùng quy tắc binary name, package signer, `java.*` và domain** như overload mảng byte. Có thể dùng `ByteBuffer.wrap(bytes)` hoặc buffer lấy từ I/O/memory-mapped file theo nhu cầu.

Điều không đổi:

- `pd == null` vẫn chọn default ProtectionDomain.
- Buffer chứa class data sai có thể ném `ClassFormatError`.
- Tên không khớp class file bị từ chối.
- Cùng một loader không thể định nghĩa trùng runtime binary name bằng cách “đổi overload” từ byte array sang ByteBuffer.

**Lời khuyên thực hành:** Đừng phụ thuộc side effect vào vị trí/limit của chính buffer object sau `defineClass` trên mọi implementation. Nếu code cần đọc lại bytes, chuẩn bị `duplicate()`/`asReadOnlyBuffer()` hoặc giữ nguồn bytes riêng. Đánh giá cả lifetime và ownership khi buffer liên quan memory-mapped file/resource.

## 7. `SecureClassLoader`: nhận `CodeSource` thay vì tự tạo domain

`SecureClassLoader` là subclass của `ClassLoader` có các overload sau:

~~~java
protected final Class<?> defineClass(
        String name, byte[] b, int off, int len,
        CodeSource cs);

protected final Class<?> defineClass(
        String name, ByteBuffer b, CodeSource cs);
~~~

Khi cung cấp `cs != null`, `SecureClassLoader` tạo/tra `ProtectionDomain` phù hợp và liên kết domain này với class. Có một protected hook:

~~~java
protected PermissionCollection getPermissions(CodeSource cs);
~~~

Trước đây, subclass có thể override hook để gắn permission metadata theo CodeSource, phục vụ Java Security Manager. **JDK 25 Javadoc nói rõ:** permissions không thể dùng để kiểm soát truy cập tài nguyên nữa do Security Manager không còn được hỗ trợ.

### 7.1. So sánh hai phong cách

| Điều bạn có | Lựa chọn |
| --- | --- |
| Đã có `ProtectionDomain` cụ thể, cần truyền nguyên domain | `ClassLoader.defineClass(..., pd)` |
| Có CodeSource và muốn `SecureClassLoader` xây domain | `SecureClassLoader.defineClass(..., cs)` |
| Không có metadata nguồn mã riêng | `ClassLoader.defineClass(... )` không có pd, hoặc pd=null |
| Muốn plugin không đọc file/network trên JDK 24+ | **Không dùng** ProtectionDomain/Permission như sandbox; cần isolation ở process/OS/container |
| Muốn audit artifact và signer provenance | Xác minh nguồn bytes/chữ ký thực, rồi gắn CodeSource phù hợp để giữ metadata |

### 7.2. Ví dụ `SecureClassLoader` dùng CodeSource

Tệp **`SecureSourceDemo.java`**, sử dụng bytes của `Greeter.class` đã biên dịch ở mục 5:

~~~java
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.SecureClassLoader;
import java.security.cert.Certificate;

public class SecureSourceDemo {
    static final class OriginLoader extends SecureClassLoader {
        OriginLoader() {
            super(SecureSourceDemo.class.getClassLoader());
        }

        Class<?> fromSource(String name, byte[] bytes, CodeSource cs) {
            return defineClass(name, bytes, 0, bytes.length, cs);
        }
    }

    public static void main(String[] args) throws Exception {
        byte[] bytes = Files.readAllBytes(
                Path.of("build/plugin/demo/plugin/Greeter.class"));

        CodeSource source = new CodeSource(
                Path.of("metadata/source.jar").toUri().toURL(),
                (Certificate[]) null);

        OriginLoader loader = new OriginLoader();
        Class<?> type = loader.fromSource(
                "demo.plugin.Greeter", bytes, source);

        CodeSource recorded =
                type.getProtectionDomain().getCodeSource();

        System.out.println("from origin loader="
                + (type.getClassLoader() == loader));
        System.out.println("source has marker="
                + (recorded != null
                    && recorded.getLocation() != null
                    && recorded.getLocation().getPath()
                        .endsWith("source.jar")));
    }
}
~~~

Biên dịch/chạy từ `pd-lab`:

~~~bash
javac --release 17 -d build/host host-src/SecureSourceDemo.java
java -cp build/host SecureSourceDemo
~~~

Stdout:

~~~text
from origin loader=true
source has marker=true
~~~

**Giải thích:** `SecureClassLoader` giúp chuyển CodeSource sang ProtectionDomain; `Source.jar` trên đường metadata **không được chương trình tải hoặc xác minh**. Thí nghiệm vẫn đọc bytes từ file `build/plugin/demo/plugin/Greeter.class`. Đặt URL tùy ý không chứng minh provenance.

`URLClassLoader` là subclass phổ biến của `SecureClassLoader`, có cơ chế lấy classes/resources từ URL search path và gắn metadata code source liên quan. Dùng đúng loader thư viện này thường đơn giản hơn tự viết `defineClass` nếu nhu cầu chỉ là nạp JAR động; vẫn phải quản lý `URLClassLoader.close()` và TCCL/resources.

## 8. Certificate/signers của package: bất biến phải giữ

Một điểm quan trọng trong Javadoc của `ClassLoader.defineClass`: **class đầu tiên được định nghĩa trong một package** xác định bộ certificates mà những class tiếp theo trong package ấy phải nhất quán. Certificate set được lấy từ `CodeSource` trong `ProtectionDomain`.

Ví dụ ý niệm:

~~~text
Loader L
  package example.lib
    A.class -- CodeSource certificates = {Signer-X}
    B.class -- CodeSource certificates = {Signer-X}  → cùng set: hợp lệ
    C.class -- CodeSource certificates = {Signer-Y}  → khác set: SecurityException
~~~

Điều này áp dụng ở phạm vi package của **cùng loader** theo hợp đồng định nghĩa class. Nó **không** có nghĩa hai class ở các package khác nhau buộc dùng chung signer, hoặc mọi JAR cùng tên package ở hai loader khác nhau đều chia sẻ một bộ certificates.

**Phân biệt hai yếu tố dễ lẫn:**

- `CodeSource.getLocation()` khác nhau **không tự động đồng nghĩa** signer mismatch. Java kiểm tra **bộ certificates**, không so bằng URL của JAR.
- Nếu một class unsigned và class khác signed trong cùng package của loader, sự khác biệt certificate set có thể bị từ chối. Lỗi cũng có thể xảy ra khi trộn signed JARs với signer sets khác.
- `Package sealing` là quy tắc khác: metadata manifest/`definePackage` có thể yêu cầu classes của package đến từ một seal base nhất quán. Đừng gọi mọi lỗi sealing là certificate mismatch.
- Đặt `name=null` có thể tránh bước so certificate package theo mô tả Javadoc, **không phải cách sửa đúng**. Hãy cung cấp binary name và tổ chức JARs/signer nhất quán.

### 8.1. Không “tự ký” bằng cách nhét certificates vào `CodeSource`

Tự tạo một `CodeSource(URL, Certificate[])` bằng certificates tự thu thập không biến bytes thành code signed đã xác thực. Muốn chứng minh JAR signed, phải có quy trình xác minh signature/entry bytes theo JAR verifier trước khi tin certificate metadata.

Một nguồn đáng kiểm tra:

~~~bash
jarsigner -verify -verbose -certs plugin.jar
~~~

Đây là mẫu lệnh kiểm tra **JAR thực**; thay `plugin.jar` bằng artifact của bạn. `jarsigner` thuộc JDK toolchain (kiểm tra availability trong JDK đang dùng). Với custom `JarFile` loader, lưu ý thông tin signer của entry có thể chỉ có ý nghĩa sau khi verifier đọc/xác thực đủ nội dung entry; đừng lấy signer metadata từ một entry chưa được xác minh rồi gán cho class.

## 9. Vì sao `ProtectionDomain` không còn là sandbox trên JDK hiện đại?

### 9.1. Lịch sử cần phân biệt theo phiên bản

| Phiên bản | Tình trạng |
| --- | --- |
| Java 8, nhiều JDK trước 17 | Security Manager/Policy từng là cơ chế có thể kiểm soát permissions cho code theo ProtectionDomain khi được kích hoạt và có security checks |
| Java 17–23 | Security Manager đã **deprecated for removal**; hành vi kích hoạt phụ thuộc release và cấu hình, không nên khởi tạo dự án sandbox mới dựa vào nó |
| Java 24 trở lên | **Security Manager permanently disabled** (JEP 486); không thể bật lại ở startup hoặc bằng `System.setSecurityManager` |
| JDK 25/26 | `ProtectionDomain`/`CodeSource` vẫn tồn tại cho API compatibility/metadata, nhưng không dùng `PermissionCollection` để chặn tài nguyên theo Security Manager |

JDK 24+ có các đặc điểm quan trọng theo hướng dẫn Oracle:

- `System.getSecurityManager()` trả `null`.
- `System.setSecurityManager(...)` ném `UnsupportedOperationException`.
- Cố bật bằng các cờ `-Djava.security.manager=...` được JDK từ chối khi khởi động.
- Java Policy API bị vô hiệu hóa/thu hẹp; domain không được cấp permissions từ policy hoạt động kiểu Security Manager cũ.
- Một số security-related methods đổi thành no-op, trả giá trị cố định, hoặc ném exception theo API migration.
- Các kiểm tra class definition **như tên `java.*`, class-file name mismatch và signer consistency** vẫn phải được xử lý theo Javadoc `defineClass`; đừng suy ra “bỏ Security Manager” đồng nghĩa “mọi bytecode được định nghĩa vô điều kiện”.

**Kết luận kỹ thuật:** `new ProtectionDomain(cs, permissions)` có thể vẫn mô tả `permissions` đã được truyền, nhưng **không có cơ chế Security Manager để cưỡng chế** rằng plugin không được mở socket/file/process. Muốn bảo vệ hệ thống khỏi plugin không tin cậy, dùng **OS process boundary**, containers, filesystem/network permissions, sandbox OS/VM phù hợp, signature verification, và hạn chế khả năng gọi APIs ở mức kiến trúc.

### 9.2. `ProtectionDomain.implies` và `PermissionCollection` không nên là quyết định phân quyền của hệ thống

Trong các JDK cũ, `ProtectionDomain.implies(permission)` có ý nghĩa trong mô hình policy/permission. Sau JDK 24, semantics policy bị giảm cấp; kết quả trên metadata không tương đương một bước OS/VM enforcement. Hệ thống hiện đại không nên dựa vào phép thử domain permissions để xác định caller có được đọc secrets hay truy cập tài nguyên không.

Nếu cần authorization trong ứng dụng, dùng cơ chế **principal/identity validation** và permission checks ở business/API boundary (ví dụ authenticated caller + role/capability check), không dùng classloader domain làm thay actor identity.

## 10. Những khác biệt không thuộc `ProtectionDomain`

Đừng nhầm các tầng:

1. **Runtime class identity:** binary name + defining loader. Domain khác không cho phép define trùng class name trong cùng loader; domain giống không khiến class ở hai loader trở thành một type.
2. **Package certificate consistency:** dựa CodeSource certificates trong quá trình `defineClass`; không phải `JPMS requires`.
3. **Package sealing:** quy tắc khai báo seal base/manifest, khác với signer certificates.
4. **JPMS module access:** `requires`/readability, `exports`, `opens`, module layer. Gắn domain không tự sửa `IllegalAccessError` hay `InaccessibleObjectException`.
5. **Resource loading:** `ClassLoader.getResource` tìm resource, không dùng `ProtectionDomain.getCodeSource()` làm resolver.
6. **Native-access controls:** cờ `--enable-native-access` trên JDK mới là cơ chế riêng; không dùng ProtectionDomain để bật JNI/FFM.
7. **Class linking và initialization:** `defineClass` tạo runtime class; `resolveClass` link; `<clinit>` chạy theo trigger initialization, không vì thêm domain.
8. **Jar URL và file handles:** domain chỉ metadata; việc mở/đóng JAR là nhiệm vụ của loader/resource owner như `URLClassLoader` và `JarFile`.

## 11. Lỗi thường gặp và hướng sửa

| Triệu chứng | Nguyên nhân có thể | Hướng xử lý |
| --- | --- | --- |
| `NoClassDefFoundError: wrong name` lúc `defineClass` | `name` khác binary name trong bytes | Đổi mapping class name ↔ class file; không đổi tên tùy ý |
| `SecurityException: Prohibited package name java...` | Loader không được phép define package `java.*` | Không dùng tên `java.*` cho plugin |
| `SecurityException` về signer mismatch | Trộn classes cùng package nhưng khác signing certificate set | Giữ signer/package nhất quán; xác minh JARs |
| `ClassFormatError` hoặc `VerifyError` | Bytes hỏng/incompatible | Kiểm tra classfile, Java version, bytecode toolchain |
| `IndexOutOfBoundsException` | Sai `off`/`len` | Tính bounds đúng; dùng đúng buffer |
| `LinkageError` khi define trùng tên | Loader định nghĩa class cùng name nhiều lần | `findLoadedClass` + `getClassLoadingLock`; không redefine |
| Plugin vẫn mở được file dù domain có empty Permissions | Không có Security Manager enforcing domain | Dùng OS/process isolation và authorization rõ ràng |
| `ClassCastException` với cùng tên class | Hai defining loaders khác nhau | Shared API qua parent, kiểm tra type identity |
| `IllegalAccessError`/`InaccessibleObjectException` | JPMS access/exports/opens | Sửa module graph/access; domain không cấp mở package |
| Audit source URL sai | CodeSource do loader tự tạo không khớp nguồn bytes | Theo dõi artifact thực và xác minh trước khi gắn metadata |
| `getProtectionDomain().getCodeSource()` null/URL null | Domain mặc định hoặc thiếu metadata nguồn | Null-check; không kết luận class chưa tồn tại |
| ByteBuffer reuse gặp lỗi ngẫu nhiên | State/contents của buffer bị tái sử dụng sai | Giữ bytes bất biến, duplicate/slice và quản lý I/O lifetime |

### 11.1. Checklist khi tự viết `findClass`

- [ ] Nhận đúng **binary name** và ánh xạ đến classfile entry hợp lệ.
- [ ] Đọc bytes từ nguồn tin cậy, đóng `InputStream`/`JarFile` khi phù hợp.
- [ ] Nếu cần CodeSource, lấy từ **artifact thực** và verify signatures trước khi tin certificate metadata.
- [ ] Chọn default PD, explicit PD hay `SecureClassLoader` overload theo nhu cầu metadata, không dùng để hứa sandbox.
- [ ] Duy trì certificate consistency trong cùng package/loader và package sealing khi có khai báo.
- [ ] Không định nghĩa classes trong `java.*` bằng plugin/application loader.
- [ ] Giữ `findLoadedClass`/locking do `loadClass` mặc định xử lý, tránh duplicate define.
- [ ] Kiểm tra cả byte[] overload và ByteBuffer overload nếu library của bạn hỗ trợ hai nguồn.
- [ ] Log binary name, defining loader, CodeSource location, module và phiên bản artifact **không tiết lộ thông tin nhạy cảm**.
- [ ] Khi reload plugin, dùng loader thế hệ mới và dọn loader/JAR handles cũ; đổi domain không reset runtime class.
- [ ] Với JDK 24+, dùng process/OS sandbox cho code không tin cậy thay vì Security Manager/PD.

## 12. Câu hỏi tự kiểm tra

1. Không truyền `ProtectionDomain` vào `defineClass` thì class có domain không? **Có, domain mặc định.**
2. Truyền `null` cho PD overload có khác default overload không? **Về lựa chọn domain, không: đều dùng default.**
3. `CodeSource.getLocation()` có chứng minh bytecode thực sự đến từ URL ấy không? **Không nếu loader tự đặt metadata mà không xác minh nguồn.**
4. `ProtectionDomain` có quyết định một class ở loader A cast được sang class cùng tên loader B không? **Không; runtime type identity phụ thuộc defining loader.**
5. Overload nào của `SecureClassLoader` nhận `CodeSource`? **Byte array và ByteBuffer variants của chính `SecureClassLoader`.**
6. Vì sao `name=null` không nên dùng cho `defineClass`? **Làm mất kiểm tra expected binary name và suy yếu certificate/package consistency check.**
7. Khác URL CodeSource nhưng certificate sets giống có tự bị từ chối signer check không? **Không chỉ vì khác URL; signer check dựa certificate sets.**
8. `ProtectionDomain` có tự kiểm tra chữ ký số JAR từ một URL giả định không? **Không.**
9. JDK 25 còn bật Java Security Manager để sandbox plugin không? **Không; đã bị disable vĩnh viễn từ JDK 24.**
10. `defineClass` nhận ByteBuffer có cho phép định nghĩa trùng tên trong cùng loader không? **Không.**
11. Mẫu test nhận `wrong name` chứng minh điều gì? **Class file có một binary name thật; tham số name khác sẽ bị từ chối.**
12. `ProtectionDomain.getPermissions()` khác null có chắc VM sẽ cấm operation không thuộc permissions không? **Không trên JDK 24+; không có Security Manager enforcement.**
13. Có nên tin `Signer-X` chỉ do custom loader tự nhét vào `CodeSource`? **Không; phải xác minh JAR signature/entry bytes.**

## 13. Tài liệu chuẩn

1. [`ClassLoader` — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ClassLoader.html): mọi overload `defineClass`, domain mặc định, package signer constraints, `name` validation, `ByteBuffer`.
2. [`SecureClassLoader` — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/SecureClassLoader.html): `defineClass(..., CodeSource)` và `getPermissions` hook; API note về Security Manager.
3. [`ProtectionDomain` — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/ProtectionDomain.html): constructors, `getCodeSource`, `getPermissions`, `getClassLoader`, `getPrincipals`.
4. [`CodeSource` — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/CodeSource.html): location, certificate/signer metadata.
5. [Security Manager Permanently Disabled — JDK 25](https://docs.oracle.com/en/java/javase/25/security/security-manager-is-permanently-disabled.html): thay đổi API/policy từ JDK 24, lý do permissions không còn tạo sandbox.
6. [JEP 486 — Permanently Disable the Security Manager](https://openjdk.org/jeps/486): quyết định thiết kế chính thức.
7. [JVMS §5.3 — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html#jvms-5.3): runtime class identity, defining/initiating loader.
8. [`JarFile` — Java SE 25 API](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/jar/JarFile.html): JAR entries, verifier metadata và code signer information.

**Kết luận:** Các biến thể `defineClass` khác nhau ở **hình thức bytes** (`byte[]` hay `ByteBuffer`) và **cách chọn domain** (mặc định, truyền `ProtectionDomain` hoặc để `SecureClassLoader` tạo từ `CodeSource`). Domain gắn thông tin về nguồn mã/chứng chỉ và permission metadata vào runtime class, nhưng **không làm thay class identity** và **không thay sandbox** trên JDK 24+. Luôn bảo toàn expected binary name, package signer consistency và kiểm chứng artifact thực trước khi tin metadata.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="packages-and-code-sources-006">SecureClassLoader và vai trò lịch sử của quyền truy cập</a>

<details>
<summary>Xem chi tiết</summary>

# SecureClassLoader và vai trò lịch sử của quyền truy cập

> **Java Core → ClassLoader → Package và nguồn mã.** `java.security.SecureClassLoader` mở rộng `ClassLoader` để gắn `CodeSource` và `ProtectionDomain` cho lớp được định nghĩa, đồng thời cung cấp hook `getPermissions(CodeSource)`. Nó ra đời trong mô hình bảo mật Java có `SecurityManager` và các file policy. **Từ JDK 24, Security Manager bị vô hiệu hóa vĩnh viễn:** giữ lại `SecureClassLoader` không làm plugin trở thành code bị sandbox. Bài học phân biệt API còn hữu ích, cơ chế cấp quyền mang tính lịch sử, kết quả thực tế và phương án bảo vệ phù hợp với JVM hiện đại.

## 1. Tóm tắt đúng trước khi viết code

Ba khẳng định cần tách riêng:

1. **SecureClassLoader là một ClassLoader thật.** Nó có thể đọc class-file bytes, định nghĩa class runtime, giao tiếp với parent, duy trì namespace của lớp và gắn `CodeSource`/`ProtectionDomain` như API quy định.
2. **PermissionCollection là metadata/cơ chế thuộc mô hình cũ.** Trước đây, khi một `SecurityManager` đang được bật, các permission checks có thể dựa trên `ProtectionDomain` trong access-control context. `getPermissions(CodeSource)` giúp một subclass góp phần tính permissions cho lớp mới.
3. **Trong JDK 24+, không có Security Manager để thực thi mô hình policy-based sandbox đó.** Tự trả về tập permissions rỗng từ `getPermissions` **không ngăn** class đọc file, môi trường hay mở kết nối mạng nhờ cơ chế `SecurityManager` nữa. Việc hệ điều hành/container và các API chuyên biệt kiểm soát tài nguyên là chuyện riêng.

Có thể nhớ:

```text
SecureClassLoader
  = ClassLoader + hỗ trợ CodeSource / ProtectionDomain / permissions metadata

SecureClassLoader != "untrusted-code sandbox" trên Java hiện đại

Class identity: (binary name, defining loader)
Source metadata: CodeSource(location, certificates/signers)
Legacy authorization: SecurityManager + AccessController + Policy + Permissions
Modern JDK 24+: legacy authorization cơ bản không còn hoạt động
```

`SecureClassLoader` **không bị xóa chỉ vì Security Manager không còn**. Nó vẫn là superclass công khai của `URLClassLoader` và vẫn có overload `defineClass` nhận `CodeSource`. Tuy vậy, không nên hiểu tên class chứa chữ “Secure” như một lời hứa an toàn.

## 2. SecureClassLoader đứng ở đâu trong cây ClassLoader?

Hệ kế thừa cơ bản:

```text
java.lang.Object
  └── java.lang.ClassLoader
        └── java.security.SecureClassLoader
              └── java.net.URLClassLoader
```

Một custom loader có thể extends trực tiếp `ClassLoader` hoặc `SecureClassLoader`:

- **Extends `ClassLoader`:** thường override `findClass` rồi gọi `defineClass(String, byte[], int, int, ProtectionDomain)` khi muốn gắn domain tự tạo.
- **Extends `SecureClassLoader`:** thêm các overload `defineClass(..., CodeSource)`; implementation của nó xây `ProtectionDomain` thích hợp từ `CodeSource` và permission hook.
- **Dùng `URLClassLoader`:** nếu nguồn chính là directory/JAR URLs, class loader chuẩn đã xử lý phần lớn công việc tìm JAR class/resources và code source. Không nhất thiết cần tự viết lại `findClass`.

Ví dụ tạo subclass:

```java
import java.security.SecureClassLoader;

public final class MyLoader extends SecureClassLoader {
    public MyLoader(ClassLoader parent) {
        super(parent);
    }

    // Override findClass khi cần đọc bytes từ nguồn riêng.
}
```

Đây là một class **biên dịch được**, không có `main`. Nó không nạp class từ nguồn mới cho đến khi bạn thực sự cài đặt `findClass` hoặc cấu hình một cơ chế tìm source.

Cũng như các loader khác, việc gọi `loadClass("demo.Task")` không luôn khiến `MyLoader` là defining loader: parent-first delegation có thể trả về class do parent định nghĩa. Phân biệt **loader được yêu cầu tìm** với **loader thực sự định nghĩa**.

## 3. API trọng tâm của SecureClassLoader

Các phương thức chuyên biệt:

```java
protected final Class<?> defineClass(
        String name,
        byte[] b, int off, int len,
        java.security.CodeSource cs);

protected final Class<?> defineClass(
        String name,
        java.nio.ByteBuffer b,
        java.security.CodeSource cs);

protected java.security.PermissionCollection getPermissions(
        java.security.CodeSource codesource);
```

Đây là **signatures minh họa**, không thể copy thành một file Java độc lập. Chúng là `protected` nên thường được gọi/override từ subclass phù hợp.

Ý nghĩa:

| Thành phần | Vai trò |
| --- | --- |
| `name` | Binary name kỳ vọng, ví dụ `demo.Task`, không phải `demo/Task.class`. |
| `b` | JVM class-file bytes hợp lệ của một lớp/interface. Không nhận Java source. |
| `off`, `len` | Đoạn dữ liệu hợp lệ trong `byte[]`; `len` là số byte, không phải end index. |
| `ByteBuffer` | Đầu vào bytes trong vùng được xác định bởi `position()/limit()` theo API. |
| `CodeSource cs` | Mô tả nguồn code của class, có thể `null`. |
| `getPermissions(cs)` | Hook tạo `PermissionCollection` gắn vào protection domain trong đường define có CodeSource. |

Nếu `cs` khác `null`, overload `SecureClassLoader#defineClass` tạo protection domain gắn với class. Các method `defineClass` là `final`: không override chúng; thay đổi policy tìm bytes ở `findClass`, permission metadata ở `getPermissions` hoặc cấu hình của loader.

Không nhầm `ClassLoader.defineClass(..., ProtectionDomain)` với `SecureClassLoader.defineClass(..., CodeSource)`: loại tham số cuối khác nhau. `SecureClassLoader` giúp từ code source đi tới domain, còn `ClassLoader` cho phép truyền domain đã được chuẩn bị.

## 4. CodeSource: mô tả nguồn code, không tự xác minh nguồn

`java.security.CodeSource` biểu diễn thông tin về **location URL** và certificates/signers liên quan.

Tạo nguồn mã cho bytes đọc từ một thư mục:

```java
// Trong method có import java.nio.file.Path, java.net.URL,
// java.security.CodeSource và java.security.cert.Certificate:
URL location = Path.of("plugin-classes")
        .toAbsolutePath().toUri().toURL();
CodeSource cs = new CodeSource(
        location, (java.security.cert.Certificate[]) null);
```

Trong JAR loader, location thường trỏ tới nguồn archive thực tế thay vì trỏ tùy tiện đến entry `.class`. Tuy nhiên:

- `new CodeSource(location, null)` chỉ gắn **metadata mà bạn cung cấp**. Nó không kiểm tra bytes thật được đọc từ URL ấy.
- Location không bắt buộc luôn khác `null` trong mọi domain; không viết `pd.getCodeSource().getLocation()` mà không kiểm tra nullable trong code sản phẩm.
- `null` certificates nghĩa là **không có certificate metadata được cung cấp**; không đồng nghĩa “JAR đã ký và xác minh”.
- Nếu nguồn là signed JAR, việc xác thực chữ ký cần đi qua API và quy trình JAR verification phù hợp, đọc/kiểm tra entries đúng cách; chỉ dựng thủ công một `CodeSource` không thay cho bước đó.
- Hai loader độc lập có thể định nghĩa class cùng binary name với cùng `CodeSource.location` mà vẫn tạo **hai runtime types khác nhau**. Type identity được quyết định bởi tên và defining loader, không phải nguồn URL.

Để chẩn đoán class thật:

```java
Class<?> clazz = /* lấy từ loader cần kiểm tra */;
ClassLoader definingLoader = clazz.getClassLoader();
java.security.ProtectionDomain pd = clazz.getProtectionDomain();
java.security.CodeSource source =
        pd == null ? null : pd.getCodeSource();
java.net.URL url = source == null ? null : source.getLocation();
```

Đây là snippet có `clazz` đã được cung cấp; comment placeholder chỉ mô tả biến cần có, không là một chương trình chạy. `url` giúp xác định metadata nguồn, nhưng không phải chứng thực mật mã hay bằng chứng duy nhất về đường đi nạp class.

## 5. ProtectionDomain: từ CodeSource tới bối cảnh quyền hạn lịch sử

`ProtectionDomain` gắn metadata domain với một class. Trước đây, các domain trên call stack và access-control context đóng vai trò trong quyết định quyền truy cập khi `SecurityManager` kiểm tra permission.

Mô hình cổ điển:

```text
class bytes -> SecureClassLoader.defineClass(..., CodeSource)
                       |
                       v
           getPermissions(CodeSource)
                       |
                       v
           ProtectionDomain(CodeSource,
                            PermissionCollection,
                            ClassLoader, ...)
                       |
                       v
                    Class<?>
                       |
                       v
          Legacy SecurityManager permission checks
                       |
                       +--> grant/deny theo domain/context/policy
```

Đây là sơ đồ lịch sử, **không phải cơ chế sandbox đang hoạt động trên JDK 24+**.

Các getter thường hữu ích:

```java
ProtectionDomain pd = clazz.getProtectionDomain();
CodeSource cs = pd.getCodeSource();
PermissionCollection permissions = pd.getPermissions();
```

Cần import đúng `java.security.*` hoặc tên đầy đủ; đây là snippet, `clazz` phải có trước. `getPermissions()` có thể trả `null` tùy đường định nghĩa class. Một `PermissionCollection` rỗng đơn giản là không liệt kê static permission tương ứng, **không chứng minh** một I/O operation sẽ bị từ chối trên JVM hiện đại.

### Những permission kiểu cũ

Các loại `Permission` lịch sử thường gặp:

| Permission | Mục đích cũ |
| --- | --- |
| `java.io.FilePermission` | Quyền đọc/ghi/xóa file theo đường dẫn. |
| `java.net.SocketPermission` | Quyền kết nối/nghe/resolve theo host/port. |
| `java.util.PropertyPermission` | Quyền đọc/ghi system property. |
| `java.lang.RuntimePermission` | Quyền cho các thao tác JVM nhất định. |
| `java.security.AllPermission` | Nhận mọi permission trong mô hình Security Manager; đặc biệt nguy hiểm nếu policy cấp nhầm. |

Khi Security Manager còn bật, các phép kiểm tra quyền phù hợp có thể dẫn đến `AccessControlException`. Khi nó không bật, nhiều operations không hỏi permission policy kiểu này. Từ JDK 24, **không thể bật lại** Security Manager để dựa vào chúng.

### getPermissions không phải quy tắc truy cập tùy ý cho mọi API

`SecureClassLoader#getPermissions(CodeSource)` là hook trả về permissions được gắn khi tạo protection domain. Nó **không** tự chặn `Files.readString`, `System.getProperty` hoặc `Socket.connect` bằng cách throw/return bộ permissions rỗng trong một JVM không có Security Manager. Nếu muốn áp dụng kiểm soát quyền ở tầng business, bạn phải đặt kiểm tra vào **chính API/service boundary do mình sở hữu** hoặc dùng isolation của OS/process. Kiểm tra nội bộ ấy không chặn code Java tùy ý gọi các thư viện JDK khác trong cùng process.

## 6. Lịch sử SecurityManager, Policy và AccessController

### Trước khi bị loại khỏi đường thực thi

Java từng có mô hình an ninh dựa trên khả năng kiểm tra permissions trong một process:

1. Class được loader định nghĩa cùng một `ProtectionDomain`.
2. `CodeSource` mô tả vị trí code và metadata certificates.
3. Chính sách `java.security.Policy` có thể cấp quyền theo nguồn/signers.
4. `SecurityManager` khi được bật, cùng các điểm kiểm tra do API nền tảng thực hiện, sẽ hỏi quyền.
5. `AccessController.checkPermission(...)`/`doPrivileged(...)` tham gia mô hình stack-based access control.

Ví dụ policy file **chỉ để nhận diện ứng dụng cũ**:

```text
grant codeBase "file:/opt/legacy/plugins/-" {
    permission java.util.PropertyPermission "app.config", "read";
    permission java.io.FilePermission "/opt/legacy/plugins/-", "read";
};
```

Đây không phải cấu hình nên dùng để bảo vệ plugin trên JDK hiện đại. `-Djava.security.policy` không còn có tác dụng bảo mật như trước ở JDK 24+.

`AccessController.doPrivileged` từng dùng để thực hiện thao tác trong scope xác định với semantics stack-inspection riêng. Nó **không** nên được hiểu đơn giản là “bật chế độ admin” hay “cho phép đoạn code vô hạn quyền” trong mọi trường hợp. Trước đây kết quả còn phụ thuộc policy, protection domains và ngữ cảnh gọi.

### Dòng thời gian cần nhớ

| Phiên bản Java | Tình trạng |
| --- | --- |
| Java 1.2 trở đi | Security architecture có `ProtectionDomain`, `CodeSource`, `SecureClassLoader`, `Policy` và `SecurityManager`. |
| Java 8 và các phiên bản cũ | Nhiều applet/Web Start/container có mô hình cấp quyền và kiểm tra security policy; không có nghĩa mọi app bật Security Manager. |
| Java 17 | **JEP 411** deprecate Security Manager **for removal**; vẫn có khả năng tương thích, nhưng nhà phát triển được khuyên chuyển đổi. |
| Java 17–23 | Các lựa chọn bật/dùng Security Manager cũ còn tồn tại với giới hạn/cảnh báo theo bản phát hành. |
| **JDK 24 trở lên** | **JEP 486** vô hiệu hóa Security Manager **vĩnh viễn**; không thể cài hoặc kích hoạt theo cách cũ. API liên quan vẫn còn một thời gian để hỗ trợ chuyển đổi nhưng có hành vi bị giới hạn/thay đổi. |

Bài học này bàn về **vai trò lịch sử** đúng nghĩa: tìm hiểu vì sao API được thiết kế như vậy, nhưng không áp dụng một security model đã vô hiệu hóa cho một JVM đang chạy JDK 24+.

## 7. Điều gì thay đổi khi Security Manager bị vô hiệu hóa từ JDK 24?

Theo tài liệu chính thức của Oracle và **JEP 486**:

- Không thể bật Security Manager bằng `-Djava.security.manager=...` ở lúc khởi động: JVM báo lỗi.
- `System.setSecurityManager(...)` ném `UnsupportedOperationException`; `System.getSecurityManager()` luôn trả `null`.
- `AccessController.doPrivileged(...)` thực thi action trực tiếp theo semantics không có Security Manager; không còn là cách tạo ranh giới cấp quyền theo call stack.
- `AccessController.checkPermission(...)` **luôn ném `AccessControlException`** trong JDK 24+, nên đừng dùng nó làm phép “test có quyền hay không” cho app hiện đại.
- `Policy.getPolicy()` trả policy không cấp quyền; các phương thức thiết lập policy và API liên quan có hành vi bị ràng buộc. Chính sách từ `java.policy` không còn là cơ chế enforcement.
- `ProtectionDomain` có thể tiếp tục tồn tại như object và giữ metadata, nhưng **permission policy kiểu cũ không dùng để giới hạn quyền đọc file/network của class**.
- Các API nền tảng trước đây chỉ ném `SecurityException` khi Security Manager chặn thao tác thường không còn thực hiện kiểm tra ấy.

Cần phân biệt hai hiện tượng trái ngược:

1. **Tài nguyên được truy cập dù collection không liệt kê quyền:** trên JDK 24+ điều này không nghịch lý vì mô hình kiểm soát quyền đã ngưng.
2. **Một lời gọi trực tiếp vào API `AccessController.checkPermission` vẫn ném:** đó là hành vi tương thích được quy định cho API lỗi thời, không có nghĩa kiểm tra quyền đang bảo vệ file/network thật.

Không dựa vào câu “`checkPermission` ném lỗi nên file vẫn an toàn”: code có thể gọi thẳng filesystem/network APIs mà không đi qua check ấy.

Tài liệu tương ứng: [Oracle — The Security Manager Is Permanently Disabled](https://docs.oracle.com/en/java/javase/24/security/security-manager-is-permanently-disabled.html) và [JEP 486](https://openjdk.org/jeps/486).

## 8. Ví dụ thực hành: CodeSource + getPermissions nhưng không tạo sandbox

Bài thực hành dưới đây dùng **Java 17+**, biên dịch ứng dụng và plugin vào **hai thư mục khác nhau**, rồi nạp bytecode qua subclass của `SecureClassLoader`. Hook `getPermissions` trả collection rỗng, tức **không khai báo** `PropertyPermission("java.version","read")`; plugin vẫn gọi `System.getProperty("java.version")` thành công khi JVM không bật Security Manager.

**Quan trọng về điều kiện:** kết quả được xác minh trên **OpenJDK 21 với Security Manager không bật**. Trên JDK 24+ Security Manager luôn tắt, nên logic về không áp permission sandbox còn được xác nhận bằng đặc tả/JEP 486; không suy diễn kết quả này cho một JVM cũ đang bật Security Manager.

### Cấu trúc thư mục

```text
src/
  plugin/
    sandboxdemo/
      Worker.java
  app/
    SecureLoaderDemo.java
plugin-classes/     (javac tạo)
app-classes/        (javac tạo)
```

**File `src/plugin/sandboxdemo/Worker.java`:**

```java
package sandboxdemo;

public class Worker {
    public String message() {
        return "plugin-ok";
    }

    public boolean canReadVersion() {
        return System.getProperty("java.version") != null;
    }
}
```

**File `src/app/SecureLoaderDemo.java`:**

```java
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.ProtectionDomain;
import java.security.SecureClassLoader;
import java.security.cert.Certificate;
import java.util.PropertyPermission;
import java.util.concurrent.atomic.AtomicInteger;

public class SecureLoaderDemo {
    static final class PluginLoader extends SecureClassLoader {
        private final Path root;
        private final URL sourceUrl;
        final AtomicInteger permissionCalls = new AtomicInteger();

        PluginLoader(Path root) throws IOException {
            super(ClassLoader.getPlatformClassLoader());
            this.root = root.toAbsolutePath();
            this.sourceUrl = this.root.toUri().toURL();
        }

        @Override
        protected PermissionCollection getPermissions(CodeSource cs) {
            permissionCalls.incrementAndGet();
            return new Permissions();
        }

        @Override
        protected Class<?> findClass(String binaryName)
                throws ClassNotFoundException {
            Path path = root.resolve(
                    binaryName.replace('.', '/') + ".class");
            try {
                byte[] data = Files.readAllBytes(path);
                CodeSource source = new CodeSource(
                        sourceUrl, (Certificate[]) null);
                return defineClass(
                        binaryName, data, 0, data.length, source);
            } catch (IOException e) {
                throw new ClassNotFoundException(binaryName, e);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        PluginLoader loader = new PluginLoader(
                Path.of("plugin-classes"));
        Class<?> c = Class.forName(
                "sandboxdemo.Worker", false, loader);
        ProtectionDomain pd = c.getProtectionDomain();
        Object plugin = c.getDeclaredConstructor().newInstance();

        System.out.println("defined by plugin loader: "
                + (c.getClassLoader() == loader));
        System.out.println("source URL matches: "
                + (pd.getCodeSource().getLocation().equals(
                        Path.of("plugin-classes")
                            .toAbsolutePath().toUri().toURL())));
        System.out.println("permissions callback invoked: "
                + (loader.permissionCalls.get() == 1));
        System.out.println("explicit property permission: "
                + pd.getPermissions().implies(
                        new PropertyPermission("java.version", "read")));
        System.out.println("class code runs: "
                + c.getMethod("message").invoke(plugin));
        System.out.println("property read at runtime: "
                + c.getMethod("canReadVersion").invoke(plugin));
        System.out.println("same class on repeat: "
                + (Class.forName("sandboxdemo.Worker", false, loader) == c));
    }
}
```

### Biên dịch và chạy

Đứng từ thư mục gốc chứa `src`:

```bash
javac --release 17 -d plugin-classes src/plugin/sandboxdemo/Worker.java
javac --release 17 -d app-classes src/app/SecureLoaderDemo.java
java -cp app-classes SecureLoaderDemo
```

Stdout **đã biên dịch, chạy và đối chiếu** trên OpenJDK 21 với Security Manager không bật:

```text
defined by plugin loader: true
source URL matches: true
permissions callback invoked: true
explicit property permission: false
class code runs: plugin-ok
property read at runtime: true
same class on repeat: true
```

### Giải thích từng kết quả

1. `plugin-classes` không nằm trong `-cp app-classes`; parent platform loader không nạp `sandboxdemo.Worker` từ thư mục ấy, nên `PluginLoader` trực tiếp định nghĩa class.
2. `CodeSource.location` được đặt từ đúng directory mà demo đọc bytecode và đi vào `ProtectionDomain` của class.
3. Hook `getPermissions(cs)` được gọi **một lần** khi domain được dựng để định nghĩa class đầu tiên từ `CodeSource` ấy. Con số là kết quả demo, không phải hợp đồng mọi loader/nhịp gọi trong mọi tình huống.
4. `getPermissions()` trả một `Permissions` collection rỗng. `PermissionCollection.implies(PropertyPermission(...))` trả `false`; tức collection ấy không liệt kê quyền.
5. **Plugin vẫn đọc được `java.version`** do JVM test không có Security Manager đang thực thi các kiểm tra kiểu cũ. Đây là minh họa mấu chốt: collection rỗng **không tự ngăn** các lời gọi `System.getProperty`.
6. Gọi `Class.forName` lần nữa với cùng name/loader trả đúng một `Class<?>`, không định nghĩa lại.
7. Reflection trong demo chỉ kích hoạt method của class được custom loader định nghĩa; nó **không** chứng minh class ấy bị cô lập hoặc được xác minh đáng tin cậy.

**Không dùng mẫu này để chạy untrusted plugin trên cùng JVM.** Trái lại, mục tiêu là minh họa rõ ràng sự khác biệt giữa metadata permissions và enforcement thực tế.

## 9. Vì sao CodeSource và permissions vẫn còn tồn tại trong API?

Có ba nhóm nhu cầu riêng:

**A. Tương thích mã cũ.** Rất nhiều loader/framework được xây khi security policy và `SecurityManager` còn là một phần của JVM. Xóa ngay các class/API sẽ phá binary compatibility. JDK có lộ trình vô hiệu hóa và sau đó mới có thể loại bỏ các phần API khác.

**B. Thông tin nguồn và kiểm tra consistency.** `CodeSource` vẫn có thể được đọc từ `ProtectionDomain` để chẩn đoán class hoặc phục vụ logic metadata; quy tắc nhất quán certificates trong một runtime package vẫn có ý nghĩa. Nhưng thông tin nguồn do loader cung cấp có thể bị đặt sai, nên phải hiểu provenance thực.

**C. Cấu trúc kế thừa.** `URLClassLoader` vốn extends `SecureClassLoader`, và một số custom loader dùng `defineClass(..., CodeSource)`. Việc tiếp tục cung cấp overload giúp các ứng dụng làm việc với class files và source metadata mà không bắt chúng tự dựng `ProtectionDomain` từ đầu.

Vì vậy không nên nói “`SecureClassLoader` vô dụng hoàn toàn”; đúng hơn là **vai trò sandbox bằng permissions lịch sử không còn hoạt động trên JDK 24+**, trong khi class-loading và source-domain metadata vẫn là những chức năng riêng.

### getPermissions không quyết định code nào được nạp

`getPermissions` được gọi trong đường dựng domain của `defineClass` nhận CodeSource; nó không tự kiểm tra tính hợp lệ của class-file bytes. Nếu muốn chặn plugin ở tầng quản lý, cần kiểm tra package allowlist, version, chữ ký được xác minh, nguồn thật, dependencies và chính sách trước khi gọi `defineClass` hoặc thực thi plugin.

Quá trình định nghĩa class vẫn chịu những quy tắc JVM riêng như `ClassFormatError`, `NoClassDefFoundError` (wrong name), `SecurityException` với namespace `java.` hoặc certificate set không nhất quán, và `LinkageError` khi định nghĩa trùng class. **Những checks còn tồn tại không đồng nghĩa với SecurityManager sandbox.**

## 10. Một số phân biệt bảo mật quan trọng

### Sandbox, class isolation và module encapsulation là ba chuyện khác nhau

| Cơ chế | Nó giải quyết điều gì? | Nó có chặn mọi quyền OS của plugin không? |
| --- | --- | --- |
| Custom `ClassLoader` | Tổ chức đường tìm class, delegation và runtime class identity. | Không. |
| `SecureClassLoader` + `CodeSource` | Gắn source/domain metadata; cơ chế permission checks từng có ý nghĩa với Security Manager. | Không trên JDK 24+. |
| JPMS (`module-info.java`, `exports`, `opens`) | Readability, strong encapsulation và reflective access trong module graph. | Không phải sandbox file/network/process. |
| JAR signing | Cung cấp khả năng kiểm tra tính toàn vẹn/xuất xứ theo chuỗi tin cậy phù hợp. | Không tự giới hạn hành vi code đã được chạy. |
| OS user permissions / container isolation | Giới hạn tài nguyên/process theo OS, với cấu hình phù hợp. | Có thể tạo ranh giới thực tế, phụ thuộc cấu hình và threat model. |
| Process/service boundary riêng | Tách plugin thành process khác, giao tiếp IPC và kiểm soát khả năng truy cập. | Có ranh giới rõ hơn nếu phối hợp OS controls. |

Một JAR **được ký hợp lệ** vẫn có thể chứa logic nguy hiểm hoặc lỗi; chữ ký không biến nó thành code có quyền hạn giới hạn. Một class được module system đóng gói kỹ vẫn chạy trong JVM process với các quyền OS của process nếu không có chính sách bên ngoài chặn.

### Có thể dùng Plugin API giới hạn không?

Có thể thiết kế `PluginContext` chỉ cung cấp method như `readPublicConfig()` hoặc `publishEvent()` và kiểm tra quyền của caller ở tầng business. Đây là biện pháp thiết kế tốt cho **extension đáng tin/được kiểm duyệt**, nhưng **không là sandbox đầy đủ** nếu code plugin tùy ý vẫn có thể gọi thẳng `Files`, `Socket`, reflection hoặc API khác trong cùng process. Muốn đối phó bytecode độc hại, cần ranh giới mạnh hơn.

## 11. Thiết kế plugin trên JVM hiện đại: nên chọn gì?

Nếu plugin đáng tin nhưng cần nạp động:

1. Tạo loader với parent phù hợp để chia sẻ API của host (ví dụ interface `Plugin` nằm ở parent).
2. Chọn `URLClassLoader` cho JAR/directory thông thường, hoặc `SecureClassLoader`/`ClassLoader` subclass khi thật sự cần nguồn bytecode tùy chỉnh hay source metadata.
3. Kiểm tra class-file bytes, binary name và dependencies; quản lý namespace, package sealing/certificates nếu framework chịu trách nhiệm.
4. Tách loader theo vòng đời plugin để có thể unload khi không còn reachable; đóng `URLClassLoader` để giải phóng tài nguyên nhưng không coi `close()` là lệnh unload ngay.
5. Ghi provenance thực khi cần chẩn đoán; đừng tự biến `CodeSource` thành phép xác minh đáng tin.

Nếu plugin **không đáng tin** hoặc bạn muốn kiểm soát filesystem/network:

1. Chạy code trong **process riêng** với user/quyền OS hạn chế; cân nhắc container, seccomp/App Sandbox, file-system mount, network policy theo môi trường.
2. Thiết kế IPC/API rõ ràng, phân quyền theo operation và validate dữ liệu từ plugin.
3. Phân biệt isolation tài nguyên với xác thực plugin. Hãy kiểm tra chữ ký/nguồn cung cấp qua cơ chế tin cậy phù hợp.
4. Chặn những khả năng không cần thiết ở cả tiến trình/hạ tầng, không chỉ đưa collection rỗng vào `ProtectionDomain`.
5. Lập threat model rõ ràng: “tránh plugin vô tình làm hỏng config” khác “chống mã độc chủ động tìm cách thoát sandbox”.

Không hứa rằng một container cấu hình mặc định tự chống mọi loại tấn công; khả năng cô lập thực tế phụ thuộc quyền OS, mount, network, syscall policy và cập nhật nền tảng.

## 12. Bẫy kỹ thuật thường gặp

| Quan niệm / lỗi | Vì sao không đúng | Cách xử lý |
| --- | --- | --- |
| “Tên `SecureClassLoader` nghĩa là an toàn.” | Nó là subclass loader có hỗ trợ source/permissions, không phải security boundary hiện đại. | Xác định threat model và ranh giới process/OS. |
| “Tôi trả `new Permissions()` nên plugin không đọc được file.” | Không có Security Manager thực thi collection ấy trên JDK 24+. | Không dùng `getPermissions` làm sandbox. |
| “`CodeSource` trỏ URL hợp lệ nên bytes đã được xác minh.” | URL có thể do custom loader tự gán. | Kiểm tra nguồn đọc thực, chữ ký/hash/chain nếu cần. |
| “Security Manager còn dùng được với `-Djava.security.manager=allow`.” | JDK 24+ từ chối bật Security Manager. | Di chuyển khỏi mô hình policy cũ. |
| “`AccessController.checkPermission` ném nên file/network cũng bị chặn.” | Trong JDK 24+, method lỗi thời này luôn ném dù operations khác có thể vẫn chạy. | Đánh giá quyền qua OS/process API phù hợp. |
| “Policy cấp quyền từ `java.policy` trong JDK 24.” | Security Manager policy mechanism đã bị vô hiệu hóa. | Migrate cấu hình thành controls thật. |
| “`getProtectionDomain().getPermissions() == null` thì lỗi loader.” | Domain có thể không có static permissions theo đường định nghĩa class. | Kiểm tra `null`, không suy ra enforcement. |
| “Plugin interface cùng tên có thể cast giữa hai loaders.” | Binary name trùng chưa đủ; defining loaders khác nhau tạo hai runtime types. | Chia sẻ host API qua parent loader. |
| “Thêm `CodeSource` sau khi class đã define là thay được domain.” | Domain được gắn lúc class được định nghĩa; không phải property cập nhật tùy ý. | Quyết định metadata trước khi `defineClass`. |
| “Đóng class loader lập tức xóa mọi class.” | Class unloading phụ thuộc reachable references/GC, không phải `close()`. | Quản lý lifecycle/caches/threads. |
| “`SecureClassLoader` tự parse manifest và verify signed JAR.” | Các trách nhiệm khác nằm ở loader/JAR verification pipeline. | Dùng URLClassLoader/JarFile phù hợp, kiểm tra provenance. |
| “Module encapsulation tương đương chặn filesystem.” | JPMS quản lý visibility của Java packages, không cấp quyền OS theo domain. | Phân quyền ở API/process/OS. |

## 13. Cách đọc code cũ có getPermissions hoặc doPrivileged

Khi bảo trì ứng dụng legacy, nên tách câu hỏi:

- `getPermissions(CodeSource)` được override ở đâu? Nó trả một collection tĩnh, cấp quyền theo source, hay gọi `super.getPermissions`?
- Loader đang định nghĩa lớp nào, thuộc protection domain nào, và class nào chỉ được parent delegate?
- Ứng dụng từng bật Security Manager ở startup hay cài tại runtime?
- Có policy file/`java.security.policy` hoặc assumptions về `AllPermission`, `FilePermission` và `SocketPermission`?
- Có đoạn `AccessController.doPrivileged` để thu hẹp phạm vi đặc quyền hay để né kiểm tra trên call stack?
- Có `AccessController.checkPermission`/`Policy.setPolicy` cần được thay bằng kiểm soát thật trước khi nâng cấp JDK 24+?
- Các library có đang **bắt mọi `SecurityException` như thể chắc chắn tài nguyên bị cấm**? Một số exception đó vẫn có thể tới từ các cơ chế khác; phân loại theo API và nguyên nhân thực.
- Sự an toàn trước đây dựa vào việc applet/container chặn code độc hại, hay vào các quy tắc domain metadata không thực thi?

### Quy trình migration thực tế

1. Xác định phiên bản JDK đang chạy và có bật `SecurityManager` trên môi trường cũ hay không. Trong Java 17–23, chính sách/cảnh báo cụ thể tùy phiên bản.
2. Tìm các API `SecurityManager`, `AccessController`, `Policy`, `Permission` (đặc biệt khi dùng trực tiếp) và Java launcher options.
3. Viết test mô tả ranh giới bảo mật **thực sự cần**: không đọc thư mục nào, không gọi mạng nào, không được ghi đè config nào.
4. Chọn cơ chế thay thế có thể test được: process/OS sandbox, network namespace, container profiles, kiểm soát IPC, authz ở server.
5. Đánh giá source metadata có cần giữ để audit/debug hay không; có thể tiếp tục dùng CodeSource nhưng không coi là authorization gate.
6. Chạy test trên JDK 24+; không kỳ vọng khởi động với `-Djava.security.manager` hoặc kiểm tra `AccessController.checkPermission` theo semantics cũ.
7. Nếu thư viện third-party phụ thuộc Security Manager quá sâu, lên kế hoạch thay hoặc nâng phiên bản tương thích; không lén downgrade bảo mật bằng cách bỏ qua lỗi.

Trên môi trường Java 17–23 khi rà soát source legacy, `jdeprscan` có thể hỗ trợ tìm API deprecated của các class files, nhưng không thay phân tích cấu hình runtime/policy. Kết quả từ static scanning không tự xác nhận việc enforcement từng chạy đúng.

## 14. Tự kiểm tra kiến thức

1. **`SecureClassLoader` có khác `ClassLoader` chủ yếu ở đâu?**  
   Nó cung cấp overload `defineClass` nhận `CodeSource` và hook `getPermissions` để xây protection domain trong mô hình API của Java.
2. **Từ JDK 24, đặt `FilePermission` rỗng có tự ngăn plugin gọi `Files.readString` không?**  
   Không. Security Manager đã bị vô hiệu hóa, nên collection permission không còn là sandbox filesystem.
3. **Nếu một class có `CodeSource.location`, có chứng minh bytes đến từ URL đó không?**  
   Không; custom loader có thể đặt metadata sai. Cần xác minh nguồn thực.
4. **`ProtectionDomain.getPermissions()` có thể `null`?**  
   Có. Trạng thái này không nên được dùng để kết luận hệ thống đang thực thi hay từ chối operations.
5. **`getPermissions` trả `false` khi kiểm tra PropertyPermission, nhưng `System.getProperty` vẫn thành công trong demo. Vì sao?**  
   Demo không bật Security Manager. Metadata permission không được runtime dùng để từ chối lời gọi property.
6. **JDK 24 có thể bật Security Manager với `-Djava.security.manager=allow`?**  
   Không. JVM từ chối thao tác bật cơ chế đã bị vô hiệu hóa.
7. **`AccessController.checkPermission` trên JDK 24+ có đáng dùng để kiểm tra access thật không?**  
   Không; nó luôn ném exception theo API thay đổi.
8. **`URLClassLoader` có quan hệ gì với `SecureClassLoader`?**  
   `URLClassLoader` kế thừa từ `SecureClassLoader`, nhưng điều đó không biến URLClassLoader thành sandbox.
9. **Hai loaders cùng gắn URL source giống nhau và định nghĩa `demo.Task` có cùng runtime type không?**  
   Không; defining loader là thành phần của type identity.
10. **Muốn chạy bytecode thực sự không tin cậy nên dùng biện pháp nào?**  
    Ưu tiên process boundary và giới hạn OS/container phù hợp, IPC có kiểm soát, không chỉ dựa vào class-loader permissions.
11. **Có nên dùng `java.security.Policy` mới để quản lý quyền plugin trên Java hiện đại?**  
    Không như cách Security Manager từng dùng; policy mechanism bị vô hiệu hóa JDK 24+.
12. **SecureClassLoader vẫn có giá trị gì khi Security Manager không còn?**  
    Class loading và metadata `CodeSource`/`ProtectionDomain`, trong giới hạn API, vẫn có thể hữu ích cho tương thích và chẩn đoán.

## 15. Nguồn tham khảo chính thức

- [Java SE 25 — SecureClassLoader](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/SecureClassLoader.html): các overload `defineClass`, `getPermissions`; API Note xác nhận permissions không còn dùng kiểm soát quyền vì Security Manager không hỗ trợ.
- [Java SE 21 — SecureClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/SecureClassLoader.html): bối cảnh phiên bản trước thay đổi JDK 24.
- [Java SE 21 — CodeSource](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/CodeSource.html): source location/certificates.
- [Java SE 21 — ProtectionDomain](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/ProtectionDomain.html): domain metadata và static/dynamic permissions.
- [Java SE 21 — ClassLoader](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): class loading, defining loader, protection domain, class-file name checks.
- [JEP 411 — Deprecate the Security Manager for Removal](https://openjdk.org/jeps/411): bối cảnh Java 17.
- [JEP 486 — Permanently Disable the Security Manager](https://openjdk.org/jeps/486): quyết định loại bỏ cơ chế kích hoạt từ JDK 24.
- [Oracle Java SE 24 — The Security Manager Is Permanently Disabled](https://docs.oracle.com/en/java/javase/24/security/security-manager-is-permanently-disabled.html): danh sách thay đổi `System`/`Policy`/`AccessController`/`ProtectionDomain` và hướng migration.
- [JVM Specification §5.3](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3): lớp runtime được định danh bằng binary name và defining loader.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="packages-and-code-sources-007">Chữ ký JAR, chứng thư và tính nhất quán trong cùng package</a>

<details>
<summary>Xem chi tiết</summary>

# Chữ ký JAR, chứng thư và tính nhất quán trong cùng package

## 1. Vấn đề: cùng package nhưng có thể không được nạp chung

Khi một class loader lấy các class từ nhiều JAR, JVM và class loader không chỉ quan tâm **tên class** và **bytecode**. Với JAR được ký, còn có thông tin **chữ ký số (digital signature)** và **chứng thư (certificate)** gắn với các entry đã được xác minh. Với JAR có package sealing, còn có ràng buộc **các class trong cùng package phải đến từ một nguồn JAR nhất quán** theo cơ chế sealing.

Ví dụ:

~~~text
plugin-a.jar (đã ký bằng signer A)
  com/example/shared/Alpha.class

plugin-b.jar (không ký)
  com/example/shared/Beta.class

                 URLClassLoader L
                      |
             define com.example.shared.Alpha
                      |
          package certificate set = {A}
                      |
             define com.example.shared.Beta
                      |
            certificate set = {} -> không khớp
                      |
                SecurityException
~~~

Đây là **kịch bản nguyên tắc**: hai class đều do **cùng một defining loader L** cố định nghĩa trong **cùng package**, nhưng certificate sets không giống nhau. Theo Javadoc Java 17 của `ClassLoader.defineClass`, **class đầu tiên được định nghĩa trong một package xác lập tập certificates chính xác**; các class được thêm vào package đó trong cùng loader phải có **cùng tập** hoặc có thể gặp `SecurityException`.

Một cơ chế khác là **package sealing**: khi package được khai báo `Sealed: true` trong manifest JAR, loader xử lý manifest/sealing sẽ không cho lấy thêm class của package đã sealed từ một code source JAR khác. Vi phạm có thể gây `SecurityException`, ngay cả khi hai JAR được ký bằng cùng certificate set.

**Đừng đồng nhất ba việc:**

1. **Xác minh chữ ký JAR (integrity verification):** bytes của các entry được ký có còn nhất quán với thông tin chữ ký?
2. **Quyết định tin cậy signer (trust validation):** signer/certificate có được tổ chức tin tưởng theo trust policy, chain, thời hạn, key usage và các điều kiện phù hợp không?
3. **Tính nhất quán package trong một loader:** certificates của các class cùng package có trùng nhau không, package sealing có bị vi phạm không?

Cả ba có quan hệ nhưng là **những phép kiểm tra khác nhau**. Chữ ký hợp lệ về mặt mật mã không tự làm JAR trở thành “an toàn để thực thi”, và không tự biến certificate self-signed thành một identity đáng tin.

## 2. Bốn khái niệm nền tảng

### 2.1. Binary class name và runtime package

Với class:

~~~java
package com.example.shared;

public final class Alpha {}
~~~

`com.example.shared.Alpha` là binary name; `com.example.shared` là package name. Ở runtime, JVM còn xét defining loader để xác định **runtime package**. Hai class trùng package name nhưng được định nghĩa bởi hai loader khác nhau **không tự thuộc một runtime package duy nhất**.

Điều này ảnh hưởng tới package-private access, type identity và khi phân tích package consistency. Quy tắc certificate set khi `ClassLoader.defineClass` được áp dụng trong **phạm vi loader/package mà loader định nghĩa**; nó không phải một bảng certificate toàn JVM chung cho mọi loader.

### 2.2. Chữ ký JAR và certificate

Một JAR được ký theo cơ chế chuẩn JAR signing có thể chứa dữ liệu liên quan trong `META-INF/`, chẳng hạn manifest `MANIFEST.MF`, signature file `.SF` và signature block `.RSA`, `.DSA` hoặc `.EC` tùy thuật toán và công cụ.

`jarsigner` sử dụng private key để ký; signature verification kiểm tra các bytes/manifest digests theo cơ chế JAR. Certificate gắn với signer giúp người xác minh đánh giá identity/public key của bên ký theo chính sách riêng.

Không đánh đồng:

- **Digest:** dùng để phát hiện thay đổi nội dung được bảo vệ.
- **Digital signature:** dùng private key ký dữ liệu liên quan; verification sử dụng public key.
- **Certificate:** liên kết public key với thông tin định danh, có thể nằm trong certificate chain.
- **Trust:** kết luận một chain/signer được chấp nhận dựa vào trust store, nguồn CA, thời hạn, revocation, thuật toán, timestamp và policy của ứng dụng.

Một JAR có signature hợp lệ vẫn có thể là mã độc nếu signer không đáng tin hoặc mã hợp lệ nhưng nguy hiểm. Một package consistency check chỉ so **certificate sets**, không thay thế quá trình xác minh chính sách trust.

### 2.3. CodeSource

`java.security.CodeSource` đại diện cho **vị trí nguồn code** (thường là URL) và thông tin certificates hoặc code signers liên quan. Nó có thể được liên kết với `ProtectionDomain` khi class được định nghĩa.

Ví dụ minh họa về quan sát:

~~~java
java.security.ProtectionDomain pd = SomeClass.class.getProtectionDomain();
java.security.CodeSource source =
        pd == null ? null : pd.getCodeSource();
System.out.println(source == null ? null : source.getLocation());
~~~

Đây là **đoạn trong một phương thức**, với `SomeClass` là class có thật. Location URL dùng để hỗ trợ quan sát xuất xứ, nhưng **không đủ** để chứng minh signature hợp lệ hoặc publisher được tin cậy. Có runtime/protection domain mà `CodeSource` không cung cấp thông tin đầy đủ.

### 2.4. Package sealing

**Sealing** là thuộc tính của **package**, thường được đọc từ JAR manifest khi loader hỗ trợ cơ chế này. Nếu package được sealed, các class của package đó phải có nguồn phù hợp với **seal base URL**, thường là cùng JAR. Đây là ràng buộc nguồn, không phải ràng buộc về sự bằng nhau của certificate sets.

Có thể dùng `Package.isSealed()` và `Package.isSealed(URL)` để kiểm tra thông tin package mà loader đã định nghĩa, nhưng cần nhớ object `Package` và sealing có phạm vi theo loader và cơ chế define package cụ thể.

## 3. ClassLoader.defineClass yêu cầu certificate set nhất quán

Trong Java SE 17, overload quan trọng là:

~~~java
protected final Class<?> defineClass(
        String name,
        byte[] bytes,
        int offset,
        int length,
        java.security.ProtectionDomain domain)
~~~

`name` phải là **binary name** của class file; nếu không trùng với internal name của bytes, JVM có thể ném `NoClassDefFoundError`. Với `name` không null, Javadoc nêu quy tắc:

- **Class đầu tiên** được định nghĩa trong một package xác định **tập certificates** mà các class tiếp theo trong package ấy phải sử dụng.
- Certificates được lấy từ `CodeSource` nằm trong `ProtectionDomain` của class.
- Nếu một class khác của cùng package trong loader ấy có certificate set khác, quá trình define có thể ném **`SecurityException`**.
- Certificate set có thể là **tập rỗng** (unsigned), và unsigned so với signed là không nhất quán.
- Tên bắt đầu `java.` có giới hạn đặc biệt về loader được phép định nghĩa; quy tắc này **độc lập** với chữ ký JAR.
- Khi tự gọi `defineClass`, nên cung cấp **binary name không null** để những kiểm tra liên quan được thực hiện đầy đủ.

Sơ đồ:

~~~text
Defining Loader L
   |
   +-- class p.First    CodeSource certificates = [cert A]
   |                    -> ghi nhận signer set cho p
   |
   +-- class p.Second   CodeSource certificates = [cert A]
   |                    -> nhất quán
   |
   +-- class p.Third    CodeSource certificates = []
                        -> SecurityException
~~~

**Trật tự nạp có thể làm tên class “bị từ chối” khác nhau**: bản đầu tiên định nghĩa trong package xác lập certificate set; class tiếp theo không phù hợp mới bị từ chối. Điều đó không có nghĩa class được nạp đầu tiên luôn được tin cậy: ràng buộc chỉ giữ **tính đồng nhất** trong cùng loader.

`defineClass(String, byte[], int, int)` (không có `ProtectionDomain` tường minh) sử dụng default protection domain. Đừng dùng overload đó khi đang có **verified signer metadata** của các JAR khác nhau rồi mong JVM tự biết signer của từng entry chỉ từ mảng bytes.

## 4. Package sealing: cùng signer vẫn có thể bị từ chối

Manifest có thể khai báo sealed cho toàn JAR:

~~~text
Manifest-Version: 1.0
Sealed: true
~~~

Hoặc chỉ cho một package cụ thể:

~~~text
Manifest-Version: 1.0

Name: com/example/shared/
Sealed: true
~~~

Lưu ý manifest có định dạng và quy tắc **dòng trống** rõ ràng: main section đứng trước, package section bắt đầu bằng `Name: .../`, tên path sử dụng `/`. Việc chỉnh manifest thủ công cần tôn trọng encoding/line-folding của chuẩn JAR.

Nếu một `URLClassLoader` nạp `com.example.shared.Alpha` từ `sealed-a.jar` và define package với seal base URL của `sealed-a.jar`, việc sau đó cố nạp `com.example.shared.Beta` trong **cùng loader/package** từ `other.jar` có thể gây **sealing violation**.

Điểm phân biệt:

| Tình huống | Certificate consistency | Package sealing |
| --- | --- | --- |
| Cùng signer certificates, khác JAR, package không sealed | Có thể hợp lệ về chứng thư | Không bị chặn bởi sealing |
| Khác signer/unsigned, cùng package và loader | Có thể `SecurityException` | Có thể không sealed |
| Cùng signer, hai JAR, package sealed ở JAR đầu | Certificate sets có thể khớp | Có thể `SecurityException` vì khác seal base |
| Hai loaders riêng, mỗi loader tự định nghĩa package | Mỗi loader kiểm tra tập riêng | Mỗi loader quản lý package/seal của mình |
| Một JAR được ký nhưng chứa unsigned entry cùng package | Có thể mismatch khi define | Sealing là kiểm tra tách biệt |

**Sealing không tự thực hiện xác minh trust**, và **signer equality không thay thế sealing**. Không nên xử lý lỗi sealing bằng cách bỏ metadata chữ ký hoặc sửa manifest của thư viện bên thứ ba tùy tiện.

## 5. Ví dụ Java 17+: ký một JAR, nạp chung với JAR unsigned

Ví dụ sau chạy trên **JDK 17+** với công cụ `javac`, `jar`, `keytool` và `jarsigner`. Nó minh họa **certificate consistency** thật khi một `URLClassLoader` thử định nghĩa hai class **cùng package** từ hai JAR có trạng thái ký khác nhau.

Đây là ví dụ học tập dùng **keystore và mật khẩu minh họa công khai**, **không sử dụng key này trong hệ thống thực**. Chứng thư self-signed chỉ để tạo chữ ký thử nghiệm; nó không đại diện một certificate chain được tổ chức tin cậy.

### 5.1. Cấu trúc source

Tạo dự án:

~~~text
jar-signing-lab/
├── src/
│   ├── pack/
│   │   ├── A.java
│   │   └── B.java
│   └── SignatureBoundaryDemo.java
└── (các thư mục output sẽ tạo ở các bước sau)
~~~

File **src/pack/A.java**:

~~~java
package pack;

public final class A {
    public String name() {
        return "A";
    }
}
~~~

File **src/pack/B.java**:

~~~java
package pack;

public final class B {
    public String name() {
        return "B";
    }
}
~~~

Hai class nằm trong cùng Java package `pack` nhưng được đóng trong **hai JAR khác nhau**. Chúng không phụ thuộc vào nhau.

### 5.2. File host: src/SignatureBoundaryDemo.java

~~~java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class SignatureBoundaryDemo {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: SignatureBoundaryDemo <signed.jar> <unsigned.jar>");
        }

        URL signed = Path.of(args[0]).toAbsolutePath().toUri().toURL();
        URL unsigned = Path.of(args[1]).toAbsolutePath().toUri().toURL();

        try (URLClassLoader loader = new URLClassLoader(
                new URL[] { signed, unsigned },
                ClassLoader.getPlatformClassLoader())) {

            Class<?> a = Class.forName("pack.A", false, loader);
            Object[] signers = a.getSigners();
            System.out.println("A loaded: " + (a != null));
            System.out.println("A signed: "
                    + (signers != null && signers.length > 0));

            try {
                Class.forName("pack.B", false, loader);
                System.out.println("B loaded: true");
            } catch (SecurityException mismatch) {
                System.out.println("B rejected: SecurityException");
            }
        }
    }
}
~~~

Vì host được chạy chỉ với `host-out` trên classpath và loader có parent là **platform loader**, bản `pack.A` và `pack.B` được tải từ hai URL theo phạm vi của `URLClassLoader` trong bài. `Class.forName(name, false, loader)` yêu cầu load/link theo API mà không chủ động chạy static initializers.

Hãy đọc ý nghĩa `a.getSigners()` đúng phạm vi: nó quan sát thông tin signers gắn với **Class đã được định nghĩa**; nó không thay thế việc đánh giá policy tin cậy của CA/chứng thư. Chương trình cố ý chỉ in boolean, không in chuỗi chứng thư khác nhau theo environment.

### 5.3. Tạo các thư mục output

Trên Linux/macOS với shell tương thích:

~~~bash
mkdir -p signed-out unsigned-out host-out
~~~

Trên Windows PowerShell:

~~~powershell
New-Item -ItemType Directory -Force signed-out, unsigned-out, host-out | Out-Null
~~~

Các lệnh còn lại sử dụng tool CLI của JDK và có thể chạy dưới terminal phù hợp sau khi đứng tại thư mục `jar-signing-lab`.

### 5.4. Compile và tạo hai JAR

~~~bash
javac --release 17 -d signed-out src/pack/A.java
javac --release 17 -d unsigned-out src/pack/B.java
javac --release 17 -d host-out src/SignatureBoundaryDemo.java

jar --create --file a-signed.jar -C signed-out .
jar --create --file b-unsigned.jar -C unsigned-out .
~~~

Tên `a-signed.jar` ở giai đoạn này **chỉ là tên file**; nó **chưa có chữ ký**. Chúng ta ký nó ở bước kế tiếp. `b-unsigned.jar` được giữ unsigned.

### 5.5. Tạo test key/certificate

Chạy lệnh **một dòng** sau để hạn chế khác biệt cú pháp nối dòng giữa shell:

~~~bash
keytool -genkeypair -alias demo -keyalg RSA -keysize 2048 -validity 365 -dname "CN=Training, OU=Demo, O=Example, L=Hanoi, ST=Hanoi, C=VN" -keystore training.p12 -storetype PKCS12 -storepass changeit -keypass changeit -noprompt
~~~

Keytool tạo key pair và certificate self-signed cho mục đích thử nghiệm. Password `changeit` xuất hiện ngay trong ví dụ **không phải cách quản lý secret cho production**. Đừng commit keystore/private key của hệ thống thật vào repository hay command history có khả năng bị chia sẻ.

### 5.6. Ký JAR A

~~~bash
jarsigner -keystore training.p12 -storetype PKCS12 -storepass changeit -keypass changeit a-signed.jar demo
~~~

Công cụ có thể in cảnh báo rằng certificate được self-signed, không có timestamp hoặc không được trust; đó là **giới hạn cố ý của demo**, không phải kỳ vọng mọi chính sách security đều chấp nhận signer này.

Để xem verification từ công cụ:

~~~bash
jarsigner -verify -verbose -certs a-signed.jar
~~~

Output `jarsigner` có thể gồm thông tin thuật toán, tên signer, warnings, timestamp và trạng thái khác nhau tùy JDK. **Không sử dụng stdout của lệnh này làm snapshot cố định**. Chế độ `-strict` có thể trả nonzero vì cảnh báo certificate của bài thử self-signed, dù dữ liệu JAR không bị chỉnh sửa.

Quan trọng hơn: `jarsigner -verify` kiểm tra chữ ký theo thuật toán/quy tắc công cụ, **không có nghĩa tổ chức của bạn đã trust certificate hay audit code trong JAR**.

### 5.7. Chạy host demo

~~~bash
java -cp host-out SignatureBoundaryDemo a-signed.jar b-unsigned.jar
~~~

Với JDK 17+ tiêu chuẩn, hai JAR được tạo đúng và signer metadata được xác minh, **stdout chính xác của chương trình** là:

~~~text
A loaded: true
A signed: true
B rejected: SecurityException
~~~

Giải thích:

1. `pack.A` đến từ `a-signed.jar`, có code signers được liên kết với class sau khi JAR entry được xác minh.
2. `URLClassLoader` define `pack.A` trong package `pack` với tập certificate của signer thử nghiệm.
3. Khi loader này cố define `pack.B` từ `b-unsigned.jar`, cùng package `pack` nhưng certificate set rỗng, không còn khớp với tập đã xác lập.
4. `ClassLoader.defineClass` phát hiện package certificate mismatch, ném `SecurityException`; demo bắt đúng loại lỗi để in dòng cuối.
5. Java source `B` hợp lệ, JAR `b-unsigned.jar` cũng có thể mở/read bình thường. Vấn đề là **không thể bổ sung Class B có certificate khác vào cùng package trong cùng defining loader**.

**Đây không phải demonstration package sealing.** Hai JAR tạo bằng `jar --create` trong ví dụ không cố ý khai báo `Sealed: true`; lỗi do sự khác biệt certificates, không phải seal base.

### 5.8. Biến thể: tách class loaders

Nếu `pack.A` được định nghĩa bởi loader L1, còn `pack.B` bởi loader L2 độc lập, certificate consistency được kiểm tra riêng theo phạm vi mỗi loader. Chỉ xét quy tắc certificate cùng package, L1 và L2 **không cần có signer sets bằng nhau**, vì mỗi loader có package definitions riêng.

Nhưng điều đó **không biến hai loader thành môi trường có thể trao đổi mọi class tùy ý**: package-private access và runtime type identity khác nhau, và host/plugin có thể gặp lỗi kiểu khi dùng chung API. Không sửa một lỗi certificate mismatch bằng cách tách loader nếu kiến trúc thực sự cần hai class có cùng runtime package để truy cập package-private members.

## 6. Vì sao signed JAR có thể có entry unsigned?

Ký JAR không phải lúc nào cũng đồng nghĩa **mọi entry đang có trong archive** đều được ký. Người ta có thể thêm entry mới sau khi ký, hoặc một manifest/signature chỉ bảo vệ những entries cụ thể; khi đó có thể tồn tại hỗn hợp signed và unsigned entries.

Khi `JarFile` được mở ở chế độ verification và entry bytes đã được đọc/kiểm tra, `JarEntry.getCodeSigners()` hoặc `getCertificates()` cung cấp thông tin liên quan **entry đó** nếu có. Nếu entry không được ký, giá trị có thể là null. Nếu lấy metadata trước khi đọc xong verified entry, chưa thể kết luận một cách chắc chắn chỉ từ `null` đó.

Đối với **multi-release JAR**, entry dành cho Java version cụ thể có thể có chữ ký riêng; `JarFile` Javadoc Java 17 nói tới việc xác minh nội dung versioned entry với chính signature của entry ấy.

Hệ quả đối với class loading:

- Kiểm tra **từng class entry** khi xác định signer/certificate consistency, thay vì gắn nhãn “JAR ký” cho tất cả entries.
- Nếu package đã có class mang signer set A, một class unsigned cùng package trong cùng loader có thể gây mismatch.
- Build/repackaging fat JAR có thể thay đổi danh sách entries và metadata chữ ký; không được sao chép thư mục `META-INF` signature cũ sang bytes mới rồi giả định chữ ký vẫn hợp lệ.
- **Đừng tự động xóa `.SF`/`.RSA`** để “sửa” verification hoặc package certificate error. Việc đó làm mất bằng chứng nguồn gốc và có thể vi phạm chính sách deployment.

## 7. Dùng JarFile để xem chữ ký entry: đọc hết trước khi kiểm tra

Ví dụ Java 17+ có thể được dùng như một utility phụ cho demo, nhưng không dùng nó để kết luận signer được tin cậy theo chính sách doanh nghiệp.

Tạo file **InspectEntry.java**:

~~~java
import java.io.InputStream;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class InspectEntry {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: InspectEntry <jar-file> <entry-name>");
        }

        try (JarFile jar = new JarFile(Path.of(args[0]).toFile(), true)) {
            JarEntry entry = jar.getJarEntry(args[1]);
            if (entry == null) {
                throw new IllegalArgumentException(
                        "Entry missing: " + args[1]);
            }

            try (InputStream in = jar.getInputStream(entry)) {
                in.transferTo(java.io.OutputStream.nullOutputStream());
            }

            CodeSigner[] signers = entry.getCodeSigners();
            System.out.println("signed entry: "
                    + (signers != null && signers.length > 0));
        }
    }
}
~~~

Biên dịch:

~~~bash
javac --release 17 InspectEntry.java
~~~

Sau khi đã tạo/ký hai JAR trong mục 5:

~~~bash
java InspectEntry a-signed.jar pack/A.class
java InspectEntry b-unsigned.jar pack/B.class
~~~

**Stdout của từng lần gọi** theo đúng thứ tự trên:

~~~text
signed entry: true
~~~

~~~text
signed entry: false
~~~

Lưu ý:

- Constructor `JarFile(file, true)` bật verification; quá trình đọc hết entry stream mới cung cấp đầy đủ điều kiện kiểm tra digest/signature cho entry đã ký.
- `getCodeSigners()` là metadata của JAR entry sau verification, **không tự xác nhận trusted CA**.
- Nếu JAR bị sửa khiến signature validation fail, có thể phát sinh `SecurityException` khi đọc entry; helper không nuốt lỗi và không in một kết quả “false” đánh lừa.
- Với multi-release JAR cần xác định đúng entry version đang được xem; không khái quát mọi versioned class từ một entry ở root.

## 8. CodeSource, ProtectionDomain và custom ClassLoader

Một custom loader có thể gọi overload:

~~~java
Class<?> type = defineClass(
        binaryName, classBytes, 0, classBytes.length,
        protectionDomain);
~~~

**Đây là đoạn minh họa trong subclass `ClassLoader`**, chưa có khai báo các biến hay mã chạy độc lập.

Tại đây:

- `binaryName` cần phản ánh tên thật trong classfile; không nên truyền `null` để né các kiểm tra tên/certificate.
- `classBytes` cần đến từ nguồn đã được phê duyệt; việc có bytes không chứng minh source đáng tin.
- `protectionDomain` có thể có `CodeSource`, trong đó chứa URL và signer/certificate information.
- Nếu đã có verification đúng của signed JAR, cần **bảo toàn signer metadata của từng entry** khi định nghĩa Class; không tự tuyên bố bytecode unsigned là signed.
- Nếu tự dùng overload `defineClass(name, bytes, off, len)` không có `ProtectionDomain` thích hợp, bạn có thể làm mất dấu vết CodeSource/signers như những loader chuyên dùng JAR vẫn duy trì.

Một số ứng dụng custom loader từ filesystem hoặc storage riêng cần định nghĩa rõ source provenance, signer verification, package sealing, classpath sharing và cách xử lý errors. **Không nên làm một “security shim” bằng cách tự tạo `CodeSource` với certificate tự gán mà chưa hề xác minh bytes.**

`ProtectionDomain` và Java `SecurityManager` là những thành phần lịch sử của mô hình Java security. Ở JDK 17, Security Manager đã được đánh dấu deprecated for removal; những permission models cũ không nên được coi là một sandbox hiện đại mặc định cho mã không tin cậy. Tuy nhiên **package certificate consistency** trong `defineClass` vẫn là ràng buộc JVM/JDK liên quan tới hành vi class loading và signer sets.

## 9. Certificate equality không giống trust validation

Đặt hai câu hỏi riêng:

**Câu hỏi A: “Các class của package này có cùng signer certificate set không?”**

Đây là vấn đề Javadoc của `ClassLoader.defineClass`. Nếu `pack.A` có set A và `pack.B` có set B, so sánh **bộ certificates tương ứng**, không phải chỉ so text alias tên `demo`, đường dẫn JAR hoặc organization name.

**Câu hỏi B: “Signer này có được tin cậy để chạy code không?”**

Đây là trust/policy decision: certificate chain có được xác thực đến trust anchor được chấp nhận, thuật toán có đủ mạnh, chữ ký và timestamp có hợp lệ, certificates có đúng phạm vi mục đích, tổ chức có approve publisher/artifact/hash hay không.

Hai JAR cùng certificate set **có thể** thỏa package signer consistency mà vẫn là code không được phép chạy theo policy doanh nghiệp. Ngược lại, hai JAR từ **hai CA có uy tín khác nhau** vẫn có thể không có **cùng exact signer set**, nên không thể trộn cùng package trong một loader.

Không nhầm thứ tự certificate chain hiển thị với quy tắc equality của Java API; không viết chính sách business dựa vào string repr của `Certificate.toString()`. Nếu cần pinning, dùng các API mã hóa, fingerprint và trust stores do thư viện security phù hợp cung cấp.

## 10. Sealing và package-info trong môi trường nhiều JAR

`ClassLoader.definePackage` cho phép xác định metadata như specification/implementation title, version, vendor và seal base. Khi loader đọc JAR manifest, một số loader như `URLClassLoader` dùng dữ liệu đó để thiết lập package properties.

Cùng package có thể xuất hiện trong nhiều JAR, nhưng kết quả tùy theo:

- **Package sealing:** có sealed không, seal base là URL nào, package đã được define hay chưa.
- **Package certificate consistency:** mỗi Class được define bởi loader có certificate set nào.
- **Thứ tự class được nạp:** class đầu tiên có thể quyết định certificate set và metadata/seal của package.
- **Package identity:** các classes có cùng defining loader không, hay đang ở loader khác nhau?
- **Named modules:** package/module rules của JPMS có thể bổ sung ràng buộc; không giả định classpath/JAR sealing vận hành y hệt với mọi custom named module layer.

Javadoc Java 17 của `ClassLoader.definePackage` lưu ý rằng loader mong metadata chính xác từ JAR nên gọi `definePackage` **trước `defineClass`**, nếu cần truyền manifest/seal info. Nếu `defineClass` tự tạo Package trước với metadata mặc định, cố định nghĩa lại package properties sau đó không phải cách thiết kế đáng tin cậy.

Đừng dùng `getPackage(String)` (đã deprecated từ Java 9) để kết luận package do chính loader hiện tại define: API cũ có thể tìm package từ ancestors. `getDefinedPackage(String)` rõ hơn khi muốn xem package do **chính loader đó** định nghĩa.

## 11. Gợi ý tạo JAR sealing riêng để nghiên cứu

Để nghiên cứu sealing **tách biệt** khỏi chữ ký, có thể tạo hai JAR **unsigned** với cùng package `pack`:

~~~text
a-sealed.jar   : chứa pack/A.class và manifest Sealed: true
b-unsigned.jar : chứa pack/B.class
~~~

Manifest mẫu trong file `sealed.mf`:

~~~text
Manifest-Version: 1.0
Sealed: true
~~~

Sau khi đã compile `pack.A` và `pack.B`:

~~~bash
jar --create --file a-sealed.jar --manifest sealed.mf -C signed-out .
~~~

`jar` có thể bổ sung/chỉnh các metadata manifest phù hợp. Khi một `URLClassLoader` nạp A từ `a-sealed.jar` trước, nó định nghĩa package `pack` là sealed đối với code source của JAR đó. Nếu sau đó cùng loader nạp B từ `b-unsigned.jar`, class B có thể bị từ chối vì **sealing violation**, dù cả hai class đều unsigned và certificate sets bằng nhau.

Đây là **bài tập quan sát dựa trên ví dụ `SignatureBoundaryDemo`**, cần đổi đối số sang `a-sealed.jar b-unsigned.jar` rồi xác định loại lỗi/stack trace của runtime. Lúc đó dòng `A signed: ...` có thể là false, còn dòng cuối báo `SecurityException`; nguyên nhân là package sealing, không phải mismatch certificate.

**Không đặt `Sealed: true` vào ví dụ signer ở mục 5**, vì như vậy hai kiểm tra cùng lúc có thể che nguyên nhân cần học.

## 12. Khi nào cùng tên package vẫn load được từ hai JAR?

Trong cơ chế classpath truyền thống, một loader như `URLClassLoader` thường có thể nạp class `p.A` từ JAR 1 và `p.B` từ JAR 2 nếu:

1. Các JAR đều nhìn thấy qua search URLs.
2. Package không bị sealing policy ngăn nguồn thứ hai.
3. Certificate sets của class entries tương thích theo `defineClass`.
4. Class name/bytecode hợp lệ và không xung đột định nghĩa trùng cùng binary name trong loader.
5. Các dependency và loader constraints liên quan có thể được giải quyết.

Ví dụ cả hai JAR **unsigned**, package unsealed, thường có thể nạp chung. Nhưng điều này vẫn có rủi ro về split package/versioning và build reproducibility. Đừng xem “nạp được” là bằng chứng mọi API hoặc security policy đều đúng.

Nếu **khác defining loaders**, việc trộn cùng package name ở source không đồng nghĩa chúng ở cùng runtime package: package-private access và type identity có thể không tương thích. “Dùng loader khác để hết SecurityException” có thể thay một lỗi bằng nhiều lỗi class isolation khác.

Trong JPMS, **named modules** có những kiểm tra package/module ownership và readability khác. Split packages giữa named modules có thể bị module resolution từ chối dù classpath tương tự trước đây từng chạy được. Không dùng kết luận “unsigned/unsealed => luôn chạy được” cho JPMS.

## 13. Lỗi thường gặp khi làm fat JAR, shading, repackaging

### 13.1. Trộn classes signed và unsigned vào cùng package

Một fat JAR builder có thể trộn nhiều JAR phụ thuộc, trong đó có signed JAR, thành một JAR mới. Nếu giữ các signature metadata `META-INF` nguyên vẹn nhưng thay nội dung/manifest, quá trình verification có thể thất bại. Nếu xóa chữ ký mà không kiểm soát provenance/trust policy, artifact mất thông tin signer; đó không phải “sửa” integrity.

**Hướng đúng:** build pipeline phải có chính sách rõ về signed input JARs, stripping/re-signing trong hoàn cảnh được phép, artifact attestation và kiểm tra kết quả. Nếu thay bytes, chữ ký gốc không còn xác nhận đúng sản phẩm mới.

### 13.2. Hai versions của cùng thư viện cùng package

Nếu một class `com.example.Util` tồn tại trong hai JAR, parent-first/wildcard classpath order có thể khiến loader lấy version không mong muốn. Sealing và signer checks có thể phát hiện một số tình huống trộn, nhưng không phải hệ thống giải quyết dependency version.

### 13.3. Package split trên classpath

Hai JAR cùng đặt class vào `com.example.shared` là split package theo nghĩa đóng gói nguồn. Classpath truyền thống có thể xử lý được nếu signer/sealing nhất quán, nhưng vẫn dễ bị xung đột. Named modules có ràng buộc mạnh hơn.

### 13.4. Annotation hoặc API bị nhân đôi qua loaders

Ngay cả nếu mỗi loader không vi phạm certificate set/sealing của chính nó, host và plugin có thể định nghĩa hai bản `shared.Api` cùng tên. Điều này gây lỗi kiểu runtime và annotation mismatch. **Signer/sealing checks không làm hai Class từ hai defining loaders trở thành cùng kiểu**.

## 14. Chẩn đoán SecurityException đúng cách

Một `SecurityException` khi load class có thể liên quan package certificate mismatch, sealing violation, hạn chế định nghĩa package `java.*` hoặc quy định runtime khác.

Đừng đoán chỉ từ chữ “security” trong exception. Thu thập:

1. **Stack trace và message** đầy đủ, xác định lỗi ở `URLClassLoader`, `ClassLoader.defineClass`, manifest/seal hay JAR verification.
2. **Binary name** đang load và package name, ví dụ `pack.B` / `pack`.
3. **Defining loader identity**, có phải cùng loader đã định nghĩa class package trước đó không?
4. **Code source/JAR URL** của class đã load thành công, signer certificates nếu được cung cấp.
5. **Manifest**: có `Sealed: true` ở main hoặc per-package section không?
6. **JAR signature metadata**: `jarsigner -verify -verbose -certs` cho mỗi input artifact; có warnings unsigned entries hay self-signed certificate không?
7. **Classpath/URL order**: JAR nào cung cấp class đầu tiên; có JAR/version trùng không?
8. **Build/repackage step**: có merge/shade/re-sign làm thay đổi bytes hay signature metadata không?
9. **Runtime JPMS/module layer**: package này có thuộc named module không, có split package?
10. **Trust policy**: signer có được tổ chức tin cậy không, tách riêng khỏi kiểm tra signer equality.

Mẫu quan sát `CodeSource` của class đã nạp:

~~~java
static void inspect(Class<?> clazz) {
    java.security.ProtectionDomain pd = clazz.getProtectionDomain();
    java.security.CodeSource cs = pd == null ? null : pd.getCodeSource();
    System.out.println("class: " + clazz.getName());
    System.out.println("loader: " + clazz.getClassLoader());
    System.out.println("source: " +
            (cs == null ? "unknown" : cs.getLocation()));
    Object[] signers = clazz.getSigners();
    System.out.println("has signers: " +
            (signers != null && signers.length > 0));
}
~~~

Đây là **đoạn minh họa đặt trong class khác**, không phải một chương trình có `main`. Với `CodeSource` null hoặc `getSigners()` null, cần diễn giải theo loader/cơ chế định nghĩa class thay vì tự kết luận “artifact không đáng tin”.

## 15. Công cụ kiểm tra phù hợp

| Công cụ/API | Dùng để làm gì | Không thay thế điều gì |
| --- | --- | --- |
| `jar --list --file x.jar` | Liệt kê entries, tìm class/manifest/signature files | Xác minh trust signer |
| `jar --describe-module --file x.jar` | Xem descriptor/module info liên quan | Certificate consistency |
| `jarsigner -verify -verbose -certs x.jar` | Kiểm tra signature và thông tin signer/warnings | Xác nhận code vô hại |
| `keytool -list -v -keystore store.p12` | Quan sát certificate/alias trong keystore | Kiểm tra mọi JAR entry |
| `JarFile(file, true)` + đọc hết entry | Kích hoạt signature verification cho signed entry được đọc | Trust policy của CA tổ chức |
| `JarEntry.getCodeSigners()` | Quan sát signer sau verification | Chứng minh artifact an toàn |
| `Class.getSigners()` | Quan sát signers của runtime Class | Hoàn chỉnh chain/trust validation |
| `Package.isSealed()` | Kiểm tra package metadata sealing | Xác minh certificate/trust |
| `Class.getProtectionDomain().getCodeSource()` | Nguồn Class và signer metadata nếu có | Bằng chứng mọi permission/certificate hợp lệ |

Để tránh lọt lỗi, CI/CD có thể ghi nhận checksum artifact và xác thực chúng theo release policy, kiểm tra JAR signing, kiểm tra package layout và nạp thử những class quan trọng trong môi trường gần giống production.

## 16. Không nên nhầm certificate consistency với quyền truy cập code

Ở Java hiện đại, runtime code có thể chịu nhiều cơ chế phân quyền khác nhau:

- **OS/process/container security**: quyền file/network và process isolation.
- **JPMS**: module readability, exports, opens và package ownership.
- **ClassLoader**: định danh kiểu, delegation và một số kiểm tra certificate consistency.
- **JAR signature**: integrity và signer metadata của archive/entry.
- **Application trust policy**: publisher/artifact allowlist, certificate validation, artifact attestation, sandbox process khi cần.

Một lớp được define từ signed JAR **không tự động có quyền đặc biệt**, cũng không bị chặn đọc file chỉ vì thiếu signature. Không dùng JAR signing hay package sealing làm sandbox chung để chạy plugin không tin cậy.

Nếu cần chạy third-party code chưa được trust, cách ly bằng tiến trình/VM/container cùng chính sách hệ điều hành và kiểm soát supply chain. `ClassLoader` và signer metadata là một phần của bức tranh, không phải toàn bộ mô hình phòng thủ.

## 17. Những sai lầm phổ biến

| Sai lầm | Điều cần nhớ |
| --- | --- |
| “Có file .RSA nghĩa toàn JAR chắc chắn được ký hoàn toàn” | Cần xác minh các entries thực sự được ký và bytes hiện tại |
| “jarsigner verify thành công => signer đáng tin” | Integrity verification khác trust validation |
| “Hai JAR cùng CA đều có cùng signer set” | Exact certificate sets có thể khác dù cùng CA phát hành |
| “Cùng signer => có thể trộn package sealed từ hai JAR” | Sealing kiểm tra seal base/JAR source |
| “Không ký JAR => không thể chứa Java class” | Unsigned classes hợp lệ trong nhiều ứng dụng |
| “Package certificate check áp dụng toàn JVM” | Kiểm tra trong phạm vi defining loader/package |
| “Hai class cùng package name luôn là một runtime package” | Defining loader là phần danh tính runtime package |
| “Xóa signature files là sửa được mọi lỗi” | Có thể phá integrity/provenance và vi phạm policy |
| “CodeSource URL tự chứng minh file được trust” | URL là metadata về nguồn, không phải trust decision |
| “Nếu package certificate mismatch thì chỉ việc bắt Exception” | `SecurityException` là runtime error cần sửa packaging/policy |
| “Sealing và signing là một thứ” | Sealing kiểm tra package source; signing kiểm tra integrity/signer metadata |
| “Custom defineClass bytes tự suy ra signer” | Phải giữ verified metadata qua CodeSource/ProtectionDomain phù hợp |

## 18. Bài tập tự kiểm tra

**1. Nếu trong cùng loader, package `p` đã định nghĩa class có certificate set A, class tiếp theo cùng package unsigned có hợp lệ không?**  
Không theo quy tắc `defineClass` khi set không khớp; có thể ném `SecurityException`.

**2. Hai class cùng package source nhưng do hai defining loaders riêng định nghĩa có chịu một package certificate set toàn JVM không?**  
Không. Mỗi loader có namespace/package definition riêng, dù vẫn cần xét loader constraints và quyền truy cập.

**3. Package sealed ở JAR A; JAR B cùng signer nhưng cũng chứa package đó. Có chắc cùng loader nạp được cả hai không?**  
Không. Nếu nguồn không phù hợp seal base, sealing violation có thể xảy ra.

**4. Vì sao `JarEntry.getCodeSigners()` đôi khi null trước khi đọc entry bytes?**  
Vì signature verification cho entry được thực hiện trong quá trình đọc; cần đọc hoàn chỉnh entry qua verified JarFile để có signer metadata.

**5. `jarsigner -verify` có tự quyết định certificate self-signed của bài demo được tổ chức của bạn tin cậy không?**  
Không. Trust policy là bước riêng, cần đánh giá chain/anchors và yêu cầu của hệ thống.

**6. Một fat JAR mới có thể dùng nguyên signature metadata của JAR đã bị repackage không?**  
Không nên; thay đổi bytes/manifest thường làm invalid các signature cũ hoặc khiến signer metadata không còn đúng.

**7. `Package.isSealed()` kiểm tra chữ ký số hay seal base?**  
Nó quan sát thuộc tính sealing của package, không xác minh trust signer.

**8. Hai JAR unsigned và unsealed cùng package chắc chắn luôn load được không?**  
Không tuyệt đối; vẫn còn duplicate binary names, bytecode/linkage, class loaders, dependency và module rules.

**9. Có nên truyền `null` làm binary name khi tự `defineClass` để tránh signer consistency check?**  
Không. Nên truyền tên đúng để JVM kiểm tra nhất quán tên và certificate.

**10. Nếu log chỉ có `SecurityException` mà không rõ nguyên nhân, bước đầu tiên là gì?**  
Đọc toàn stack trace/root cause, xác định class/package, loader, code source và manifest; sau đó phân biệt signer mismatch với sealing violation.

## 19. Nguồn tham khảo chính thức

- Java SE 17 — **ClassLoader** (`defineClass` và quy tắc exact certificate sets trong cùng package): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- Java SE 17 — **JarFile** (verified signed entries, signer metadata và giới hạn về certificate trust): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/jar/JarFile.html
- Java SE 17 — **JarEntry** (`getCodeSigners`/`getCertificates`): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/jar/JarEntry.html
- Java SE 17 — **Package** (`isSealed`): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Package.html
- Java SE 17 — **CodeSource**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/CodeSource.html
- Java SE 17 — **ProtectionDomain**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/security/ProtectionDomain.html
- Java SE 17 — **URLClassLoader**: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html
- **JAR File Specification** (package sealing, signatures và manifest): https://docs.oracle.com/en/java/javase/17/docs/specs/jar/jar.html
- Java SE 17 — **jarsigner command**: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jarsigner.html
- Java SE 17 — **keytool command**: https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html
- JVM Specification Java 17 §5.3 — Creation and Loading (runtime package/defining loader): https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="packages-and-code-sources-008">Giới hạn định nghĩa lớp trong package java.*</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn định nghĩa lớp trong package java.* — Java ClassLoader

Java cho phép ứng dụng tự viết `ClassLoader` để tải bytecode từ JAR, thư mục, bộ nhớ hoặc nguồn tùy biến. Nhưng quyền **định nghĩa lớp mới** không phải là quyền vô hạn: một custom/application class loader thông thường **không được định nghĩa class có binary name bắt đầu bằng `java.`**. Nếu gọi `ClassLoader.defineClass(...)` với tên như `java.demo.Reserved`, JVM/JDK áp dụng giới hạn đặc biệt và ném **`SecurityException`**.

Quy tắc trong **Java SE 17 API, `ClassLoader#defineClass`** được phát biểu chính xác hơn: tên bắt đầu bằng `java.` chỉ có thể được định nghĩa bởi **platform class loader hoặc các loader tổ tiên của nó**. Đây **không phải** quyền mà bất kỳ subclass `ClassLoader` nào cũng được cấp chỉ vì nó ủy quyền cho platform loader.

Bài học tập trung vào sự khác nhau giữa **load/ủy quyền một lớp nền tảng đã tồn tại** và **tự định nghĩa lớp mới trong namespace `java.*`**, vì sao JVM bảo vệ namespace đó, cách nhận diện lỗi, các giới hạn liên quan và thiết kế package/class loader an toàn.

## 1. Hai hành động dễ bị nhầm: loadClass và defineClass

Giả sử một custom loader:

```java
ClassLoader child = new ClassLoader(
        ClassLoader.getSystemClassLoader()) {
};
Class<?> stringType = child.loadClass("java.lang.String");
```

Điều này **hợp lệ**. Child yêu cầu `java.lang.String`, mặc định ủy quyền lên parent rồi tới các loader nền tảng phù hợp. Nó nhận lại đối tượng `Class<?>` tương ứng với lớp `String` vốn đã được runtime định nghĩa, **không tự tạo một bản `java.lang.String` thứ hai**.

Ngược lại, code trong subclass:

```java
return defineClass("java.demo.Reserved", bytes, 0, bytes.length);
```

đang yêu cầu **chính subclass đó định nghĩa class** thuộc namespace `java.`. Trên application/custom loader bình thường, yêu cầu bị từ chối bằng `SecurityException` cho dù bytes là class file hợp lệ.

| Hành động | Ý nghĩa | Kết quả thường gặp với class JDK |
|---|---|---|
| `child.loadClass("java.lang.String")` | Yêu cầu tìm class, có thể ủy quyền parent | Thành công, trả lại class nền tảng |
| `Class.forName("java.lang.String")` | Tra class theo defining loader của caller và yêu cầu initialization | Thành công trong runtime phù hợp |
| `String.class` | Class literal tham chiếu class đã được JVM biết đến | Hợp lệ |
| `child.defineClass("java.demo.Reserved", bytes, ...)` trong subclass | Child **tự định nghĩa** class trong namespace `java.` | `SecurityException` |
| `child.getResource("java/lang/String.class")` | Tra resource/class bytes, không phải định nghĩa runtime class | Có thể trả URL nếu resource được cung cấp/quy tắc module cho phép |

**Nguyên tắc:** `loadClass` có thể trả class do **loader khác** định nghĩa. Chỉ việc gọi `loadClass` trên child không chứng minh child là *defining loader*. Quan sát bằng `clazz.getClassLoader()` và hiểu cơ chế parent delegation.

## 2. Quy tắc bảo vệ chính xác của defineClass

Các overload thường gặp:

```java
protected final Class<?> defineClass(
        String name, byte[] b, int off, int len);

protected final Class<?> defineClass(
        String name, byte[] b, int off, int len,
        java.security.ProtectionDomain domain);
```

Đây là **chữ ký API minh họa**, không phải một chương trình riêng. Khi custom loader dùng `defineClass` để định nghĩa class thường, Java SE 17 đặt các điều kiện:

1. `name` là **binary name** dự kiến, ví dụ `com.example.Widget`; không phải `com/example/Widget.class`.
2. Bytes phải có định dạng class file hợp lệ, offset/length trong giới hạn.
3. Khi `name` không `null`, tên khai báo trong bytecode phải khớp tên được truyền, nếu không có thể phát sinh `NoClassDefFoundError`.
4. Package có thể chịu quy tắc nhất quán **chứng chỉ người ký (signer certificates)** giữa những classes được cùng loader định nghĩa.
5. **Đặc biệt với `java.`:** một loader **không phải platform loader hoặc tổ tiên của nó** không được định nghĩa class mang tên bắt đầu bằng `java.`; có thể nhận `SecurityException`.

Quy tắc này được công bố trực tiếp trong JavaDoc, **không chỉ là một quy ước ngầm “đừng dùng tên đó”**.

### Quy tắc kiểm tra phần đầu tên

Dạng cấm là tên bắt đầu bằng **`java.`** có dấu chấm ngay sau `java`:

```text
java.lang.String         -> namespace được bảo vệ
java.util.List           -> namespace được bảo vệ
java.demo.Reserved       -> namespace được bảo vệ

com.example.MyClass      -> không rơi vào quy tắc java.*
org.acme.plugin.Tool     -> không rơi vào quy tắc java.*
myjava.demo.Widget       -> không rơi vào quy tắc java.*
javax.example.Widget     -> không tự động bị quy tắc java.* này chi phối
jdk.example.Widget       -> không tự động bị quy tắc java.* này chi phối
```

Những dòng `javax.*` hoặc `jdk.*` cuối **không phải khuyến nghị đặt tên ứng dụng ở đó**. Các package/JDK modules khác vẫn có thể được bảo vệ bởi **các cơ chế riêng** (module/package conflicts, accessibility, class identity, vendor policies). Với ứng dụng mới, nên chọn package thuộc miền/tổ chức của mình, như `com.acme.project`.

**Đừng đánh đồng** giới hạn `java.*` với việc Java cấm mọi class có tiền tố chữ `java` trong bất kỳ ngữ cảnh nào; vấn đề là namespace được quy định và cách runtime định nghĩa class.

## 3. Vì sao Java cần hạn chế namespace java.*?

Một mục tiêu quan trọng là bảo vệ **tính toàn vẹn của các kiểu/API nền tảng** mà toàn bộ Java runtime dựa vào.

Ví dụ, nếu một plugin có thể bí mật định nghĩa `java.lang.String` riêng dưới cùng tên:

- `Object`, `String`, `Class`, `Throwable` hoặc các API tiêu chuẩn có thể bị hiểu sai namespace và tính tương thích kiểu.
- Code có thể nhầm một lớp giả mạo với một lớp nền tảng chỉ vì chúng có **cùng binary name**.
- Các giả định nội bộ của bytecode, linking, serialization, reflection hoặc framework có thể trở nên không đáng tin.
- Hệ thống/plugin có nguy cơ đánh lừa code khác bằng tên namespace vốn mang ý nghĩa platform.

Java dùng nhiều biện pháp đồng thời: namespace restriction tại `defineClass`, cơ chế loader hierarchy, type identity, verifier, linking constraints, module ownership/access và các kiểm tra khác. **Giới hạn `java.*` là một biện pháp riêng** — không phải mô tả đầy đủ mô hình bảo mật của JVM.

### Cùng tên không đồng nghĩa cùng runtime type

Đối với class thông thường:

```text
Runtime class identity =
    (binary name, defining class loader)
```

Nếu hai loaders khác nhau cùng định nghĩa `com.acme.User`, chúng tạo hai runtime classes khác nhau dù source/bytecode giống nhau. Với `java.lang.String`, việc cho phép ứng dụng tự định nghĩa thêm bản trùng tên có thể tạo nguy cơ đặc biệt nghiêm trọng. Bởi vậy JDK quy định phạm vi loader rất hẹp đối với tên bắt đầu `java.`.

**Phân biệt:** Parent-first delegation giúp child **dùng chung các class nền tảng**; giới hạn `defineClass` ngăn child tùy ý tạo **class mới mạo danh namespace nền tảng**. Hai cơ chế bổ sung cho nhau nhưng **không giống nhau**.

## 4. Java 17+ demo: class thường định nghĩa được; java.* bị từ chối

Demo dưới đây biên dịch ra **hai class files hợp lệ**:

- `sample.Allowed`: class tên thông thường để custom loader định nghĩa thành công.
- `java.demo.Reserved`: một class test **nằm trong namespace bị bảo vệ**, chỉ nhằm chứng minh custom loader không được định nghĩa nó. **Không triển khai code ứng dụng thực tế trong package này.**

Chương trình chính tải bytes từ disk, tự gọi `defineClass` cho hai tên khác nhau, đồng thời đối chiếu `loadClass("java.lang.String")`.

Cấu trúc:

```text
guard-demo/
├── src/
│   ├── sample/
│   │   └── Allowed.java
│   ├── java/
│   │   └── demo/
│   │       └── Reserved.java
│   └── GuardDemo.java
└── out/
```

### 4.1. Class bình thường

Tệp **`src/sample/Allowed.java`**:

```java
package sample;

public final class Allowed {
    public static String value() {
        return "allowed";
    }
}
```

### 4.2. Class thử nghiệm mang tên java.*

Tệp **`src/java/demo/Reserved.java`**:

```java
package java.demo;

public final class Reserved {
    public static String value() {
        return "reserved";
    }
}
```

Tệp này phục vụ **bài thử biên dịch bytecode**; không được dùng làm thư viện chạy thực tế. Trên một số cấu hình compiler/modules, khai báo những package trùng với các JDK modules có thể bị chặn ngay khi biên dịch. Trong bài này `java.demo` là tên thử nghiệm không thuộc module nền tảng chuẩn, và lệnh bên dưới đã được kiểm tra với `javac --release 17` trên JDK 21.

### 4.3. Loader chính và kiểm tra exception

Tệp **`src/GuardDemo.java`**:

```java
import java.nio.file.Files;
import java.nio.file.Path;

public final class GuardDemo {
    static final class ByteLoader extends ClassLoader {
        ByteLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    public static void main(String[] args) throws Exception {
        ByteLoader loader = new ByteLoader(
                GuardDemo.class.getClassLoader());

        byte[] allowed = Files.readAllBytes(
                Path.of(args[0], "sample", "Allowed.class"));

        Class<?> ordinary =
                loader.define("sample.Allowed", allowed);

        System.out.println("Normal definition: "
                + ordinary.getName());
        System.out.println("Custom defining loader: "
                + (ordinary.getClassLoader() == loader));

        Class<?> core = loader.loadClass("java.lang.String");
        System.out.println("Delegated String: "
                + (core == String.class));
        System.out.println("String bootstrap: "
                + (core.getClassLoader() == null));

        byte[] reserved = Files.readAllBytes(
                Path.of(args[0], "java", "demo", "Reserved.class"));

        try {
            loader.define("java.demo.Reserved", reserved);
            System.out.println("Unexpected reserved definition");
        } catch (SecurityException expected) {
            System.out.println(
                    "Reserved definition: SecurityException");
        }
    }
}
```

`ByteLoader` subclass `ClassLoader` chỉ để có quyền gọi phương thức protected `defineClass`. Method `define` có quyền truy cập package-private và được `GuardDemo` gọi từ cùng top-level/nest context; chúng cùng file nguồn, nên ví dụ hợp lệ.

### 4.4. Chạy trên Java 17+

Từ thư mục `guard-demo`, dùng JDK 17+:

```bash
mkdir -p out

javac --release 17 -d out \
  src/sample/Allowed.java \
  src/java/demo/Reserved.java \
  src/GuardDemo.java

java -cp out GuardDemo out
```

Trong **PowerShell**, tạo thư mục `out` và dùng các lệnh một dòng:

```powershell
New-Item -ItemType Directory -Force -Path out | Out-Null
javac --release 17 -d out src/sample/Allowed.java src/java/demo/Reserved.java src/GuardDemo.java
java -cp out GuardDemo out
```

**Đầu ra đã kiểm thử bằng OpenJDK 21 với mục tiêu `--release 17`:**

```text
Normal definition: sample.Allowed
Custom defining loader: true
Delegated String: true
String bootstrap: true
Reserved definition: SecurityException
```

### 4.5. Giải thích chính xác kết quả

1. **Normal definition:** `ByteLoader#define("sample.Allowed", bytes)` được phép vì tên thuộc namespace của ứng dụng, class file hợp lệ và cùng loader chưa định nghĩa tên đó.
2. **Custom defining loader:** `ordinary.getClassLoader() == loader` là `true`. Đây là bằng chứng class `sample.Allowed` được loader tự định nghĩa, không chỉ khởi phát việc nạp rồi ủy quyền.
3. **Delegated String:** `loader.loadClass("java.lang.String")` kế thừa phương thức mặc định với parent delegation. Loader tìm `String` ở đường parent/bootstrap thay vì gọi `defineClass` để tạo bản riêng.
4. **String bootstrap:** trong JDK tiêu chuẩn, defining loader của `java.lang.String` là bootstrap; API `getClassLoader()` trả `null` cho trường hợp này.
5. **Reserved definition:** bytes của `java.demo.Reserved` đã tồn tại và tên truyền vào **khớp binary name** trong class file, nhưng loader ứng dụng tùy chỉnh vẫn bị chặn bởi quy tắc `java.*`; `defineClass` ném `SecurityException`.

Tất cả dòng stdout cố định ở trên phù hợp với môi trường JDK chuẩn nêu trong bài thử. Những runtime Java không chuẩn hoặc custom VM có thể có cơ chế triển khai riêng; khi viết code production, vẫn dựa trên hợp đồng Java SE thay vì so sánh nguyên văn thông điệp exception.

**Không dùng lệnh `java -cp out java.demo.Reserved` để “chạy” class bị bảo vệ**: ngoài vấn đề launcher/class loading, class test cũng **không có `main`**. Mục tiêu duy nhất là minh họa `defineClass` trên custom loader.

## 5. Không phải compile thành công là class có thể chạy

Đây là khác biệt thường gây nhầm:

```text
.java source
    |
    v
javac --release 17
    |
    +--> tạo .class có binary name java.demo.Reserved
    |
    v
runtime loader của ứng dụng
    |
    +--> defineClass("java.demo.Reserved", bytes, ...)
                |
                v
         SecurityException
```

`javac` là bước **biên dịch source thành class file**, còn `defineClass` là bước runtime **định nghĩa class trong một loader**. Compiler có thể nhận một source hợp lệ về mặt cú pháp/tên class, nhưng JVM/JDK vẫn áp dụng runtime restrictions độc lập. Những package đang thuộc một named module cũng có thể bị compiler/module tools ngăn từ trước; không nên suy diễn rằng **mọi** `package java.*` đều compile được trên mọi cấu hình.

### Vì sao cứ chuyển class file vào JAR khác không đủ?

Đưa `java/demo/Reserved.class` vào một JAR được thêm trên classpath **không cấp thêm quyền defining loader**:

```text
app.jar / plugin.jar
    └── java/demo/Reserved.class

Application/Custom ClassLoader
    └── attempt define java.demo.Reserved
            └── SecurityException
```

Resource có thể tồn tại trong JAR, nhưng khả năng tìm thấy bytes và quyền định nghĩa runtime class là hai điều kiện khác nhau.

## 6. Phân biệt loader được phép và loader của ứng dụng

Java SE 17 JavaDoc mô tả: nếu binary name bắt đầu `java.`, định nghĩa class chỉ được thực hiện bởi **platform class loader hoặc các loader tổ tiên của nó**.

Trong cấu trúc JDK 17 thường gặp:

```text
Application/System loader
       |
       v parent
Platform loader
       |
       v parent
Bootstrap loader (getClassLoader() thường là null)
```

- **Bootstrap loader** xử lý nhiều class thiết yếu như `java.lang.String`, `java.lang.Object`.
- **Platform loader** phụ trách tập class/module nền tảng được JDK bố trí cho loader này.
- **Application loader** nạp code ứng dụng trong classpath/module configuration thông thường.

Nói **“chỉ bootstrap mới có quyền với toàn bộ `java.*`”** là **không chính xác** theo phát biểu JavaDoc Java 17, vì còn có **platform loader hoặc ancestors** trong quy tắc cho phép. Nhưng điều đó **không cho phép một ứng dụng tự tạo subclass có parent là platform loader rồi được hưởng quyền định nghĩa `java.*`**. Loader vừa tạo là **child**, không phải platform loader hay một ancestor của nó.

Một class được **định nghĩa bởi platform loader** không đồng nghĩa ứng dụng có quyền gọi `protected defineClass` trên đối tượng platform loader. Quyền truy cập protected, module/package access, và cơ chế runtime đều vẫn áp dụng.

### Calling loader khác defining loader

Giả sử:

```java
ClassLoader app = ClassLoader.getSystemClassLoader();
Class<?> stringType = app.loadClass("java.lang.String");
```

Đây là snippet hoàn chỉnh về cú pháp trong một phương thức. `app` bắt đầu yêu cầu class, nhưng `stringType.getClassLoader()` trên JDK tiêu chuẩn trả `null` (bootstrap). Danh tính runtime class gắn với loader đã **định nghĩa**, không phải biến `app`.

Đừng kết luận `String` đã được ứng dụng định nghĩa vì lệnh `app.loadClass` trả thành công.

## 7. Giới hạn java.* không phải toàn bộ bảo vệ package

Ngoài kiểm tra tên `java.`, việc định nghĩa class chịu nhiều ràng buộc khác:

| Cơ chế | Nó bảo vệ điều gì? | Lỗi/biểu hiện có thể gặp |
|---|---|---|
| **Reserved `java.*` namespace** | Loader ngoài platform/ancestors không tự định nghĩa class `java.*` | `SecurityException` |
| **Class binary name match** | Tên truyền vào `defineClass` phù hợp với bytes | `NoClassDefFoundError` |
| **Class-file validation** | Dữ liệu đúng định dạng class file và version | `ClassFormatError`, `UnsupportedClassVersionError` |
| **Bytecode verification** | Quy tắc stack, types, control flow, v.v. | `VerifyError` |
| **Package signer consistency** | Các class trong cùng package của loader có tập chứng chỉ ký phù hợp | `SecurityException` |
| **Runtime package identity** | Quyền package-private dựa trên package name **và defining loader** | Có thể `IllegalAccessError` |
| **Loading constraints** | Nhất quán type identity ở những chữ ký/quan hệ liên loader | `LinkageError` |
| **JPMS module rules** | Module membership, readability, exports/opens | Lỗi access/linking hoặc lỗi build/module resolution |
| **Package sealing khi loader/JAR áp dụng** | Tính nhất quán nguồn code đối với sealed package | Lỗi sealing riêng theo loader/tool |

Không nên phân loại mọi exception `SecurityException` thành “đụng `java.*`”. Ví dụ mismatch signer certificates có thể dùng **cùng exception class** nhưng root cause khác.

Cũng không nên coi `ClassNotFoundException` đồng nghĩa `SecurityException`: thứ nhất nói về **không tìm được** định nghĩa qua đường lookup, thứ hai trong bài liên quan **không được phép tự định nghĩa** class trong namespace dành riêng.

## 8. Vì sao definePackage không giúp thay đổi giới hạn?

`ClassLoader#definePackage` quản lý **metadata package** của loader, như specification title, implementation version, vendor, sealing URL và các thuộc tính liên quan.

Ví dụ abstract:

```java
definePackage(
        "com.acme.plugin",
        "Plugin API", "1.0", "Acme",
        "Plugin Runtime", "1.0", "Acme",
        null);
```

Đây là đoạn minh họa bên trong subclass `ClassLoader`, không phải một chương trình độc lập.

**Định nghĩa metadata package** là hoạt động khác với **định nghĩa runtime class**. Không thể cho rằng gọi `definePackage("java.demo", ...)` sẽ cấp cho custom loader quyền gọi `defineClass("java.demo.Reserved", ...)`. `ClassLoader#defineClass` vẫn phải thực hiện các kiểm tra namespace của nó.

Package Java cũng không phải một “folder toàn cục”. Trong JVM, **runtime package** của class thường xác định bởi:

```text
(package name, defining class loader)
```

Hai class cùng `package com.acme;` nhưng được các loader khác nhau định nghĩa không tự động dùng chung quyền package-private. Đây là một lý do bổ sung để tránh tùy tiện đóng gói lại core/sharing classes ở nhiều loader.

## 9. Named modules, JDK runtime image và package java.*

Từ Java 9, JDK dùng **Java Platform Module System (JPMS)**, với những module như `java.base`, `java.logging`, `java.sql`. Một số API có package `java.*` được bố trí ở các module và loader nền tảng khác nhau theo cấu hình runtime.

Điểm cần phân biệt:

- **`package java.*`**: tên namespace ở mức Java binary name.
- **`module java.base`**: một named module trong hệ thống module của Java.
- **`java.*` class-definition restriction**: kiểm tra của `defineClass` theo loader được phép.
- **Module accessibility**: lớp/module có được đọc/truy cập hay không qua `requires`, `exports`, `opens` và các quy tắc khác.
- **Runtime image resource `jrt:`**: cách JDK hiện đại cung cấp một số tài nguyên module, không phải `File` thông thường.

Các cơ chế này có liên quan nhưng **không thay thế cho nhau**. Ví dụ dùng một lựa chọn launcher để mở package cho reflection (khi hợp lệ) không có nghĩa ứng dụng tự dưng được quyền **định nghĩa class mới trong `java.*` bằng loader riêng**.

Nếu cần mở rộng chức năng Java, chọn package ứng dụng riêng và API/plugin extension points được tài liệu hóa. Nếu cần điều chỉnh hành vi JDK trong thử nghiệm được kiểm soát, đó là một chủ đề chuyên biệt về tools/modules/instrumentation với giới hạn riêng; **không nên lấy việc định nghĩa thêm class trong `java.*` bằng application loader làm giải pháp**.

### Không đồng nhất tên java.*, javax.*, jdk.*

Một số APIs tiêu chuẩn có package `javax.*`, một số JDK implementation APIs dùng `jdk.*` hoặc `sun.*`. Quy tắc `defineClass` dành riêng cho tên bắt đầu **`java.`** được mô tả rõ ràng; không được tự mở rộng cùng chính sách cụ thể ấy sang mọi package khác chỉ dựa trên tiền tố.

Tuy nhiên, **không nên đặt mã ứng dụng mới dưới `javax.*`, `jdk.*`, `sun.*`** để “tránh rule java.*”: các namespace đó có thể do nền tảng/framework sở hữu, bị ràng buộc bởi modules, tránh được một kiểm tra cũng không tạo ra contract tương thích hay ổn định.

## 10. Parent delegation và bảo vệ các lớp lõi

`ClassLoader.loadClass` mặc định hoạt động theo nguyên tắc parent-first:

```text
ChildLoader.loadClass("java.lang.String")
    |
    +-- kiểm tra class đã được loader ghi nhận
    |
    +-- hỏi parent loader
          |
          +-- tiếp tục lên platform/bootstrap
          |
          +-- String đã tồn tại trong runtime
    |
    +-- trả Class<?> của String từ runtime
```

Đối với lớp JDK, đây là **mục tiêu**: tất cả modules, application components và plugins dùng chung những runtime types nền tảng mà chúng cần giao tiếp.

Một loader **child-first** được viết cho plugin có thể cố ý ưu tiên tự nạp `com.acme.private.*`, nhưng nên **giữ parent-first** cho các runtime/platform types và shared API.

Ví dụ chính sách khái quát:

```text
java.*                         -> parent/platform only
javax.* / jdk.* / sun.*        -> theo contract/runtime/module phù hợp
shared.api.*                   -> parent first
plugins.acme.internal.*        -> child first nếu thật sự cần isolation
other names                    -> parent first mặc định
```

Đây là **chính sách kiến trúc minh họa**, không phải đoạn code hoàn chỉnh hay bảng quy định tất cả packages. Đối với Java 17+, không nên tự định nghĩa `java.*` từ child ngay cả khi policy child-first; tốt hơn phân vùng trách nhiệm rõ ngay từ thiết kế.

## 11. Điều gì xảy ra khi cố phá quy tắc bằng custom loader?

Một loader có thể override `loadClass` hoặc `findClass` và đọc bytes từ bất kỳ nguồn nào. Tuy nhiên, cuối cùng nếu muốn biến bytes thành runtime class thông thường, nó vẫn phải dùng một cơ chế **định nghĩa class** chịu quy tắc JVM/JDK, trong đó có namespace restriction.

Ví dụ giả mã:

```text
findClass("java.demo.Reserved"):
    bytes = read("java/demo/Reserved.class")
    return defineClass("java.demo.Reserved", bytes, 0, bytes.length)

Kết quả với ordinary application-defined loader:
    SecurityException
```

Không nên bắt `SecurityException` rồi giả vờ rằng đó chỉ là “class không có trong file”. Xử lý và log nguyên nhân thật sự. Nếu một loader đặc biệt cần fallback parent, fallback chỉ đúng khi parent thực sự cung cấp **class tiêu chuẩn đã biết** — không phải tìm một bản `java.*` tùy biến để thay thế.

Các đường vòng thiếu kiểm soát còn có thể tạo hệ thống khó kiểm tra, không tương thích giữa JDK releases hoặc vi phạm những giả định an toàn của platform. Thiết kế vững chắc là **đổi tên package của class ứng dụng** và dùng API Java chuẩn, thay vì cố làm cho loader ứng dụng sở hữu namespace nền tảng.

### Vì sao exception không phụ thuộc SecurityManager?

Tên `SecurityException` có thể khiến người học nghĩ lỗi chỉ xảy ra khi `SecurityManager` được bật. Trong trường hợp `ClassLoader#defineClass`, **quy tắc package `java.*` được áp dụng trực tiếp** bởi API/implementation. Không cần một SecurityManager do ứng dụng cài riêng mới có exception này.

Đây là phân biệt quan trọng trên Java hiện đại, khi mô hình SecurityManager truyền thống đã bị giới hạn/deprecated và không nên xem là công cụ bảo vệ plugin thông thường.

## 12. Lỗi phổ biến và cách chẩn đoán

### 12.1. “Prohibited package name: java.demo”

Một exception có thể có thông báo tương tự:

```text
java.lang.SecurityException: Prohibited package name: java.demo
```

Đây là **mẫu thông điệp thường gặp**, không phải contract để parse chuỗi; nội dung có thể khác theo JDK. Kiểm tra:

1. `name` truyền vào `defineClass` là gì?
2. Có bắt đầu bằng `java.` không?
3. Loader thực sự gọi `defineClass` là loader nào?
4. Bạn đang **tự định nghĩa class** hay chỉ gọi `loadClass` để dùng class nền tảng?
5. Class file có bị đưa nhầm vào package `java.*` do code generator, bytecode rewriting hoặc cấu hình shading/relocation không?

**Cách sửa:** dùng package thuộc ứng dụng, chẳng hạn `com.example.plugin`. Không dùng loader tùy biến để định nghĩa class với namespace dành riêng.

### 12.2. “Class file có sẵn nhưng vẫn không load được”

Tồn tại `java/demo/Reserved.class` trên classpath/JAR không có nghĩa application loader được định nghĩa nó. Tìm resource bằng `getResource` và định nghĩa class bằng `defineClass` là hai hoạt động khác nhau.

Nếu class nằm trong một named module của JDK hoặc runtime image, hãy sử dụng module/public API phù hợp thay vì sao chép file rồi định nghĩa lại từ application loader.

### 12.3. “Mình override loadClass rồi, sao vẫn bị chặn?”

Bởi `loadClass` là chiến lược **tìm và lựa chọn**, còn `defineClass` có **ràng buộc namespace**. Thay thứ tự parent/child không tạo thêm quyền định nghĩa những tên bị hạn chế.

Nếu chỉ muốn dùng `java.lang.String`, hãy ủy quyền parent. Nếu muốn triển khai logic ứng dụng tùy biến, đặt class ở package riêng.

### 12.4. “SecurityException” nhưng không có tên java.*

Một nguyên nhân khác của `defineClass` có thể là signer certificate set trong một package không nhất quán, hoặc chính sách khác của môi trường. Kiểm tra toàn bộ stack trace và nguyên nhân; không đổi package tùy tiện khi nguyên nhân thật là ký số hoặc sealing.

### 12.5. “java.lang.String cannot be cast to java.lang.String”

Thông điệp cùng tên/cast sai (thường với các class ứng dụng, không phải String chuẩn) có thể do **hai defining loaders** tạo hai runtime types khác nhau. Nhưng trường hợp namespace `java.*` được bảo vệ thường chặn kiểu giả mạo trước khi tạo class, nên cần phân biệt lỗi class identity của thư viện với lỗi cấm package `java.*`.

### 12.6. Có được dùng tên javafoo.* không?

Riêng phép kiểm tra `name.startsWith("java.")` sẽ không coi `javafoo.*` là `java.*`. Tuy nhiên, việc đặt tên package kiểu dễ gây nhầm với JDK không tốt cho maintainability. Package ứng dụng nên dùng tên miền đảo ngược, ví dụ `org.example.learning`.

## 13. So sánh với các ràng buộc bảo vệ khác

| Trường hợp | Vấn đề chính | Điều chỉnh phù hợp |
|---|---|---|
| Plugin định nghĩa `java.demo.Plugin` qua custom loader | Reserved namespace `java.*` | Đổi thành `com.acme.plugin.Plugin` |
| Hai plugin tự định nghĩa hai bản `shared.api.Plugin` | Runtime type identity khác nhau | Đặt shared API ở parent và delegate |
| Plugin dùng `package demo;` nhưng không truy cập được package-private method ở app | Runtime package gồm package name + defining loader | Dùng API public/chia sẻ đúng loader |
| Class hợp lệ nhưng bytes khác tên `defineClass(name, ...)` | Binary name mismatch | Sửa name/source bytes đồng nhất |
| Class file từ JDK mới hơn runtime | Version mismatch | Dùng JDK phù hợp hoặc `javac --release` |
| Provider class không thấy do loader/plugin classpath | Lookup/source configuration | Chọn loader/classpath/module layer đúng |
| Module không `exports`/`opens` package cần dùng | JPMS access rule | Thiết kế module descriptors và access hợp lệ |
| Khởi tạo static class ném lỗi | Initialization failure | Sửa nguyên nhân initializer, không chỉ đổi loader |

Lưu ý: **chuyển package để thoát giới hạn `java.*` chỉ giải quyết vấn đề đặt tên bị cấm**, không sửa các lỗi kiểu, dependency, module hoặc bytecode khác nếu chúng cùng tồn tại.

## 14. Quy tắc thiết kế thư viện và plugin tránh xung đột namespace

1. **Dùng namespace bạn sở hữu:** `com.yourcompany.project` hoặc `org.yourteam.module`.
2. **Không tạo class ứng dụng trong `java.*`**, kể cả class thử nghiệm được code generator sinh ra hoặc source chuyển đổi.
3. **Dùng public JDK APIs** để tương tác với nền tảng; tránh bắt chước class tiêu chuẩn bằng tên nhị phân trùng.
4. **Giữ parent delegation** với platform types và shared contracts nếu không có lý do kiến trúc được kiểm tra nghiêm túc.
5. **Override `findClass` trước**, chỉ override `loadClass` khi cần namespace isolation đặc biệt và hiểu đầy đủ locking/linking.
6. **Đưa shared API vào loader chung**, để host/plugin giao tiếp qua một runtime interface.
7. **Không nhầm compile-time với runtime:** JVM loader có thể áp dụng kiểm tra mà `javac` không gặp ở một cấu hình source cụ thể.
8. **Kiểm tra generated/relocated bytecode names** trong build output/JAR; không chỉ nhìn tên source.
9. **Không coi ClassLoader là sandbox:** mã không đáng tin nên được cô lập bằng những cơ chế bảo mật phù hợp hơn ở hệ điều hành/process/container.
10. **Kiểm thử trên JDK thực tế** mà sản phẩm dùng, kể cả Java version, module path, custom loaders và các bytecode generators.

### Khi xây loader riêng, nên lưu những log gì?

Đối với các lỗi dạng này, log nên có:

```text
requested binary name
loader implementation/class
defining/parent loader identity
bytecode source (JAR/URI/provider)
root exception class and cause
JDK version and module context (nếu liên quan)
```

Tránh log toàn bộ bytecode hoặc đường dẫn có dữ liệu nhạy cảm. Thông báo `SecurityException` và tên package thường đủ để chỉ ra lỗi `java.*`, nhưng vẫn cần trace khi có nhiều loader.

## 15. Câu hỏi tự kiểm tra

**1. `child.loadClass("java.lang.String")` có bị cấm như `child.defineClass("java.lang.String", ...)` không?** Không. `loadClass` mặc định ủy quyền parent và có thể nhận class có sẵn; `defineClass` đòi tự tạo runtime class.

**2. `java.demo.Reserved` không nằm trong JDK tiêu chuẩn thì application loader có thể định nghĩa không?** Không theo quy tắc `defineClass`: tên bắt đầu bằng `java.` là yếu tố giới hạn, không chỉ việc tên đã được JDK dùng hay chưa.

**3. Java SE 17 JavaDoc nói “chỉ bootstrap loader” đúng không?** Không hoàn toàn. Nó nêu **platform class loader hoặc các ancestors**. Application/custom child loader không được hưởng quyền đó.

**4. Đổi parent của custom loader thành platform loader có khiến child được phép `defineClass("java.demo.Reserved", ...)` không?** Không. Child không phải platform loader hoặc ancestor của nó.

**5. Compile class source trong `java.demo` thành công có đảm bảo chạy được từ classpath không?** Không. Biên dịch và runtime definition là hai bước với kiểm tra khác nhau.

**6. `getResource("java/demo/Reserved.class")` thấy bytecode có nghĩa là loader được định nghĩa class không?** Không. URL/resource lookup không cấp quyền định nghĩa runtime class.

**7. Vì sao `SecurityException` chưa chắc liên quan `java.*`?** `defineClass` còn có kiểm tra signer certificates cùng package và môi trường có thể có quy tắc khác.

**8. `definePackage("java.demo", ...)` có cấp quyền để `defineClass` tên `java.demo.Reserved` không?** Không. Package metadata và class definition restriction là hai vấn đề độc lập.

**9. Có nên đổi `java.demo.Widget` thành `javax.demo.Widget` để né `java.*` không?** Không nên. Hãy đặt code trong namespace riêng, vì các package platform khác cũng có ownership và constraints.

**10. Nếu muốn nạp plugin implementation từ bytes, giải pháp cơ bản là gì?** Đặt class ở package của ứng dụng, giữ shared API ở parent, custom loader override `findClass` và dùng `defineClass` đúng binary name/valid bytes, đồng thời tuân thủ module/package constraints.

## 16. Nguồn tham khảo chính thức

- **Java SE 17 API — `ClassLoader`**, đặc biệt `defineClass(String, byte[], int, int)` và overload nhận `ProtectionDomain` (định nghĩa `java.*` và `SecurityException`): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html
- **Java SE 17 API — `Class#getClassLoader`** (defining loader và bootstrap `null`): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- **JVMS §5.3 — Creation of Classes and Interfaces** (class identity: binary name + defining loader): https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- **JVMS §5.4 — Linking, Verification and Access Control** (các kiểm tra runtime bổ sung): https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4
- **JLS §7 — Packages and Modules** (khái niệm Java packages và JPMS): https://docs.oracle.com/javase/specs/jls/se17/html/jls-7.html

**Ghi nhớ:** **Ứng dụng được dùng các lớp `java.*` bằng cách để loader phù hợp của nền tảng cung cấp chúng, nhưng custom/application loader không được tự định nghĩa lớp `java.*`.** Hãy phân biệt `loadClass` với `defineClass`, dùng package sở hữu bởi ứng dụng và thiết kế parent delegation/shared API rõ ràng thay vì cố tự tạo bản sao của lớp/namespaces nền tảng.


</details>

- [Quay lại đầu trang](#back-to-top)
