# ClassLoader là gì? Từ bytecode đến class có thể sử dụng

Khi viết Java, ta thường nhìn thấy một chuỗi rất ngắn:

```text
Main.java
→ javac
→ Main.class
→ java Main
→ chương trình chạy
```

Nhưng tệp `.class` chỉ là **bytecode nằm ở đâu đó**: trong thư mục build, JAR, runtime image của module, thư mục plugin hoặc một nguồn byte khác. JVM không thể thực thi một kiểu chỉ vì một tệp mang tên đó tồn tại. Trước hết JVM phải biết **tìm byte ở đâu, byte đó đại diện cho class nào, các tham chiếu bên trong trỏ tới đâu, trạng thái `static` được chuẩn bị thế nào và khi nào mã khởi tạo được chạy**.

Ở đây **JAR** chỉ là một tệp đóng gói thường dùng để chứa `.class` và tài nguyên của Java. Bạn chưa cần biết chi tiết định dạng JAR để học ClassLoader; chỉ cần hiểu một class có thể nằm trong thư mục hoặc bên trong một JAR thay vì tồn tại như một tệp độc lập.

Đây là chỗ `ClassLoader` xuất hiện.

> **ClassLoader là thành phần tham gia cơ chế nạp class: nó có thể tự tìm/cung cấp biểu diễn nhị phân để JVM tạo `Class` tương ứng, hoặc ủy quyền yêu cầu nạp cho ClassLoader khác.**

Nếu Java không có cơ chế này, môi trường chạy sẽ phải giả định mọi class đều nằm ở một vị trí cố định và thuộc một không gian tên duy nhất. Khi đó các phụ thuộc trong JAR, classpath của ứng dụng, plugin, máy chủ ứng dụng (application server), runtime image của module hay các không gian tên tách biệt gần như không thể hoạt động theo mô hình Java hiện nay.

Trong module này, **không gian tên khi chạy** có thể hiểu là vùng ánh xạ từ tên nhị phân của class tới kiểu khi chạy mà một ClassLoader nhìn thấy hoặc định nghĩa. Hai ClassLoader khác nhau có thể cùng biết tên `com.example.Plugin` nhưng ánh xạ tên đó tới hai `Class<?>` khác nhau.

> **Kiến thức nối từ module trước:** phần này giả định bạn đã biết một class Java có thể được biểu diễn khi chạy bằng `Class<?>`. Nếu `Class<?>`, metadata của kiểu khi chạy hoặc reflection vẫn còn lạ, hãy xem lại module `class-object` và `reflection`. Ở đây ta không học lại Reflection; ta chỉ dùng `Class<?>` như “đại diện khi chạy của một kiểu” để tập trung vào cách kiểu đó được đưa vào JVM.

Một phân biệt phải giữ từ đầu:

```text
plugin/PaymentPlugin.class tồn tại
≠
JVM đã load PaymentPlugin
≠
PaymentPlugin đã được initialize
```

Module này sẽ dùng một plugin rất nhỏ làm ví dụ xuyên suốt:

```text
host
→ ứng dụng chính đang chạy

plugin
→ phần mở rộng được host nạp thêm

Plugin API / SPI
→ interface/contract dùng chung giữa host và plugin

provider
→ class triển khai SPI đó

framework/library
→ mã hạ tầng dùng API/SPI để tìm và gọi provider
```

```text
Plugin API dùng chung
        ↓
PaymentPlugin.class
        ↓
ClassLoader tìm byte và định nghĩa kiểu
        ↓
quan sát định danh, tài nguyên, TCCL, khởi tạo
        ↓
tháo plugin và kiểm tra tham chiếu nào cản việc gỡ nạp
```

Lộ trình của toàn module:

```text
Bytecode trở thành kiểu khi chạy thế nào?
→ Lifecycle
        ↓
JVM có sẵn những ClassLoader nào?
→ Bootstrap / Platform / Application
        ↓
Vì sao ClassLoader thường hỏi ClassLoader cha trước?
→ Parent Delegation
        ↓
Muốn nạp plugin từ nguồn riêng thì sao?
→ Custom ClassLoader
        ↓
Vì sao cùng tên class vẫn có thể là hai kiểu khác nhau?
→ Class Identity
        ↓
Khung phần mềm ở tầng cha tìm provider ở tầng con thế nào?
→ Thread Context ClassLoader
        ↓
Tài nguyên trong classpath được tìm thế nào?
→ Resource Loading
        ↓
Khi nào khởi tạo `static` thật sự chạy?
→ Initialization
        ↓
Vì sao triển khai lại plugin dễ giữ lại cả đồ thị ClassLoader?
→ Gỡ nạp / Rò rỉ
```

## <a id="loading-linking-initialization">ClassLoader là gì và class đi vào runtime như thế nào?</a>

### KHÁI NIỆM — ClassLoader tham gia vào đoạn nào?

Ở mức cơ bản, **`ClassLoader` tham gia biến một tên nhị phân (binary name) thành `Class<?>` khi chạy bằng cách tự tìm/cung cấp bytecode phù hợp hoặc ủy quyền việc nạp cho ClassLoader khác**. JVM dùng kết quả đó để tạo kiểu khi chạy rồi tiếp tục liên kết (linking) và khởi tạo (initialization). Nó giải quyết khoảng cách giữa “bytecode đang tồn tại ở đâu đó” và “JVM đã có một kiểu với định danh rõ ràng để liên kết, khởi tạo và sử dụng”.

Nếu môi trường chạy chỉ biết một vị trí cố định và một không gian tên duy nhất, Java sẽ rất khó hỗ trợ classpath của ứng dụng, phụ thuộc trong JAR, plugin, container, bytecode được sinh động hoặc các vùng nạp class tách biệt. Vì vậy ClassLoader không phải một API phụ để “đọc tệp `.class`”; nó là một phần của cơ chế đưa kiểu vào JVM theo đúng không gian tên và vòng đời khi chạy.

### Tên nhị phân (binary name), đường dẫn tệp .class và classpath khác nhau như thế nào?

Giả sử mã nguồn là:

```java
package com.example.plugins;

public final class PaymentPlugin {
}
```

Với class cấp cao nhất này:

```text
binary name
→ com.example.plugins.PaymentPlugin

đường dẫn tệp .class thông thường tính từ một gốc classpath
→ com/example/plugins/PaymentPlugin.class
```

`ClassLoader.loadClass(...)` làm việc với **tên nhị phân**, không nhận một đường dẫn hệ thống tệp kiểu `C:\\...\\PaymentPlugin.class`.

**Classpath** có thể hiểu đơn giản là tập các vị trí mà Application/System ClassLoader được cấu hình để tìm class và tài nguyên, ví dụ:

```text
build/classes/java/main/
libs/payment-plugin.jar
libs/common-api.jar
```

Nếu một gốc classpath là `build/classes/java/main/`, ClassLoader có thể ánh xạ:

```text
com.example.plugins.PaymentPlugin
        ↓
com/example/plugins/PaymentPlugin.class
```

rồi tìm đường dẫn tương đối đó bên dưới gốc classpath. Nếu gốc là JAR, mục tương ứng nằm **bên trong JAR**, không phải là một tệp `.class` độc lập trên hệ thống tệp.

Vì vậy cần tách bốn khái niệm. Với class cấp cao nhất, một số tên có thể nhìn giống nhau về mặt chữ, nhưng **vai trò của chúng khác nhau**:

```text
package/source naming
binary class name
class-file/resource path
classpath location
```

> **Nâng cao — có thể bỏ qua ở lượt học đầu:** module path và named module bổ sung thêm quy tắc về khả năng nhìn thấy và đóng gói. Mô hình nền vẫn là: **ClassLoader nhận tên khi chạy và tìm biểu diễn phù hợp trong những vị trí mà nó nhìn thấy**.

### Khi nào JVM bắt đầu nạp một class?

Không nên hình dung JVM đọc toàn bộ mọi `.class` ngay lúc ứng dụng khởi động. Java thường nạp class **theo nhu cầu (demand-driven)**: một kiểu được nạp/liên kết khi JVM cần nó để thực hiện một thao tác, phân giải phụ thuộc, reflection/nạp động hoặc phục vụ một kiểu khác đang được xử lý.

Ví dụ:

```java
import com.example.plugins.PaymentPlugin;

public class Demo {
    public static void main(String[] args) {
        System.out.println("start");
    }
}
```

Dòng `import` chỉ giúp **compiler** hiểu tên trong mã nguồn. Bản thân `import` không phải lệnh khi chạy và không có nghĩa `PaymentPlugin` đã được nạp hoặc khởi tạo khi `Demo` chạy.

Ngược lại, các thao tác như sau có thể làm JVM cần một kiểu:

```text
new PaymentPlugin()
gọi/đọc member cần resolve type liên quan
Class.forName("com.example.plugins.PaymentPlugin")
loader.loadClass("com.example.plugins.PaymentPlugin")
JVM resolve symbolic reference từ class khác
```

Không nên dựa vào một thời điểm nạp tuyệt đối cho mọi JVM. JVM có thể thực hiện một số bước nạp/liên kết sớm hơn khi đặc tả cho phép, miễn là không làm thay đổi ngữ nghĩa quan sát được. Điều cần giữ là: **không phải mọi class đều được nạp ngay lúc khởi động, và nạp vẫn khác khởi tạo**.

JVM Specification mô tả quá trình tạo class/interface khi chạy theo ba giai đoạn lớn:

```text
Loading
→ Linking
→ Initialization
```

- **Loading (nạp)** tìm biểu diễn nhị phân của kiểu và tạo `Class` khi chạy.
- **Linking (liên kết)** kiểm tra và chuẩn bị kiểu để có thể dùng an toàn trong JVM.
- **Initialization (khởi tạo)** chạy phần khởi tạo `static` mà chương trình đã khai báo.

`ClassLoader` gắn trực tiếp nhất với **loading (nạp)**. Tuy vậy, khi gọi các API như `loadClass`, `Class.forName` hoặc dùng kiểu lần đầu, người học thường quan sát cả chuỗi vòng đời nên cần hiểu ranh giới giữa ba giai đoạn.

### VÌ SAO — vì sao không chạy bytecode ngay khi tìm thấy tệp?

Một tệp có đuôi `.class` chưa chứng minh rằng nó:

- có định dạng class-file hợp lệ;
- thật sự mang tên nhị phân mà JVM đang yêu cầu;
- tham chiếu đến những kiểu/thành viên hợp lệ;
- tương thích với các ràng buộc của JVM;
- đã có trạng thái `static` sẵn sàng;
- đã chạy bộ khởi tạo `static`.

JVM tách vòng đời thành nhiều bước để có thể kiểm tra tính đúng đắn, trì hoãn công việc chưa cần thiết và chỉ chạy tác dụng phụ của khởi tạo khi ngữ nghĩa yêu cầu.

### MỐI LIÊN HỆ — mã nguồn, bytecode, ClassLoader và `Class<?>`

```text
mã nguồn
  ↓ javac
bytecode của class
  ↓ ClassLoader/JVM nạp class
đối tượng Class<?> khi chạy
  ↓ liên kết
kiểu đã sẵn sàng về mặt cấu trúc
  ↓ khởi tạo khi có active use
khởi tạo static đã hoàn tất
```

`Class<?> pluginType` là metadata của kiểu khi chạy đã được JVM tạo. Nó không phải tệp `.class`, dù hai thứ liên quan trực tiếp.

### CƠ CHẾ — quan sát ClassLoader của một kiểu

```java
public final class PluginLifecycleDemo {
    public static void main(String[] args) {
        Class<?> type = PluginLifecycleDemo.class;

        System.out.println(type.getName());
        System.out.println(type.getClassLoader());
    }
}
```

Class của ứng dụng thông thường sẽ gắn với Application/System ClassLoader. Các ClassLoader cụ thể sẽ được tách riêng ở chương tiếp theo.

### MINH CHỨNG — điều cần đọc từ ví dụ

Khi `PluginLifecycleDemo.class` đã tồn tại trên classpath, JVM vẫn phải tạo biểu diễn khi chạy cho nó trước khi mã có thể chạy. Từ đây hãy luôn phân biệt:

```text
artifact chứa bytecode
→ đầu vào

ClassLoader + vòng đời JVM
→ cơ chế biến đầu vào thành kiểu khi chạy

Class<?>
→ định danh/metadata khi chạy đã được tạo
```

## <a id="linking-phases">Liên kết (linking): kiểm tra, chuẩn bị và phân giải</a>

Linking nằm giữa loading và initialization:

```text
load bytes
   ↓
verify
   ↓
prepare
   ↓
resolve
   ↓
initialize khi cần
```

### Kiểm tra (verification)

Verification kiểm tra class-file và các ràng buộc (constraint) quan trọng khi chạy. Mục tiêu là không để JVM thực thi bytecode phá vỡ những bất biến (invariant) mà JVM dựa vào.

Không nên hiểu verification như “compiler chạy lại”. Compiler kiểm tra quy tắc ở mức mã nguồn; verifier làm việc với ràng buộc ở mức class-file/bytecode.

### Chuẩn bị (preparation)

Preparation tạo vùng lưu trữ cho các trường `static` và đặt chúng về giá trị chuẩn bị ban đầu theo quy tắc của JVM.

Ví dụ:

```java
final class PluginState {
    static int loadedPlugins = 10;
    static final int MAX_PLUGINS = 20;
}
```

Cách hình dung hữu ích:

```text
preparation
→ static storage được chuẩn bị
→ static field nhận giá trị mặc định của JVM

initialization
→ constant field có `ConstantValue` được gán giá trị constant theo quy trình initialization
→ executable static field initializer / static block chạy theo semantics của Java
```

Vì vậy không nên đồng nhất “static field đã có storage và default value sau preparation” với “class đã được initialize”. Ngay cả giá trị từ `ConstantValue` cũng thuộc quy trình initialization chứ không phải preparation.

### Phân giải (resolution)

Class file chứa nhiều **tham chiếu ký hiệu (symbolic reference)** như tên class, field và method. Resolution biến các tham chiếu ký hiệu cần thiết thành tham chiếu khi chạy mà JVM có thể dùng trực tiếp.

Ví dụ bytecode của `PaymentPlugin` có thể tham chiếu:

```text
com.example.api.Plugin
java.lang.String
java.util.List
```

JVM phải phân giải chúng theo các quy tắc khi chạy. Resolution có thể được thực hiện ở các thời điểm khác nhau tùy cách triển khai JVM; người học không nên giả định mọi tham chiếu ký hiệu đều được phân giải ngay tại đúng một thời điểm cố định trước khi chương trình bắt đầu.

### Khi class A cần class B, ClassLoader nào được dùng?

Đây là mắt xích nối vòng đời với cơ chế ủy quyền cho ClassLoader cha. Theo quy tắc phân giải của JVM, khi class `A` có tham chiếu ký hiệu tới class/interface `B`, **ClassLoader định nghĩa (defining ClassLoader) của `A` được dùng để khởi xướng việc nạp `B`**. ClassLoader đó có thể tự định nghĩa `B` hoặc tiếp tục ủy quyền cho ClassLoader cha; ClassLoader cuối cùng chịu trách nhiệm định nghĩa `B` mới là defining ClassLoader của `B`.

Với plugin ví dụ:

```text
PaymentPlugin
→ do PluginClassLoader định nghĩa
→ bytecode tham chiếu com.example.api.Plugin
→ JVM cần resolve Plugin
→ yêu cầu loading bắt đầu qua PluginClassLoader
→ PluginClassLoader áp dụng parent delegation
→ parent Application ClassLoader đã có Plugin API
→ trả về cùng Plugin.class dùng chung
```

Điểm quan trọng không phải thuộc lòng từng bước nội bộ của JVM, mà là hiểu rằng phụ thuộc của một class **không được tìm bằng một “ClassLoader toàn cục” duy nhất cho toàn JVM**. ClassLoader định nghĩa của class đang tham chiếu tạo điểm bắt đầu tự nhiên cho việc nạp phụ thuộc. Sau này TCCL xuất hiện chính vì khung phần mềm ở tầng cha đôi lúc cần một ngữ cảnh ClassLoader khác để nhìn thấy thành phần cung cấp ở tầng con.

### Mô hình lỗi — lỗi xảy ra ở giai đoạn nào?

Các lỗi khi nạp class dễ gây nhầm vì nhiều exception/error có tên gần nhau. Hãy quy chúng về câu hỏi JVM đang làm gì:

| Dấu hiệu | Cách hiểu |
| --- | --- |
| `ClassNotFoundException` | Mã chủ động yêu cầu `ClassLoader`/`Class.forName` tìm một tên nhị phân nhưng ClassLoader không tìm được class đó. Đây thường là checked exception của API nạp động. |
| `NoClassDefFoundError` | JVM cần một định nghĩa class trong lúc thực thi/liên kết nhưng định nghĩa đó không còn khả dụng hoặc không thể hoàn tất. Lỗi này cũng có thể xuất hiện ở lần sử dụng chủ động sau khi khởi tạo trước đó đã thất bại. |
| `VerifyError` | Bytecode đã được nạp nhưng verifier phát hiện class-file vi phạm ràng buộc JVM nên class không thể vượt qua bước verification. |
| `ClassFormatError` | Byte đã được tìm thấy nhưng không tạo thành class-file hợp lệ theo định dạng JVM mong đợi. |
| `UnsupportedClassVersionError` | Class-file được biên dịch cho phiên bản class-file mới hơn JVM hiện tại hỗ trợ. |
| `LinkageError` | Nhóm lỗi cho thấy class đã đi tới vùng định nghĩa/liên kết nhưng JVM không thể tạo một hệ kiểu nhất quán; ví dụ `NoClassDefFoundError`, `ClassFormatError`, `NoSuchMethodError` hoặc `IncompatibleClassChangeError`. |

Một luồng chẩn đoán hữu ích:

```text
API động không tìm được tên được yêu cầu?
→ ClassNotFoundException

JVM đang cần phụ thuộc nhưng định nghĩa class không khả dụng?
→ NoClassDefFoundError / LinkageError

đã tìm được bytecode nhưng định dạng/phiên bản sai?
→ ClassFormatError / UnsupportedClassVersionError

khởi tạo static đã thất bại trước đó?
→ lần đầu thường ExceptionInInitializerError
→ lần dùng sau có thể NoClassDefFoundError: Could not initialize class
```

Không nên học các error này như danh sách thuộc lòng. Luôn quay lại ba câu hỏi: **ClassLoader có tìm thấy bytecode không, JVM có định nghĩa/liên kết được không, và khởi tạo đã thành công chưa?**

## <a id="load-vs-initialize">Nạp class không đồng nghĩa với khởi tạo</a>

Đây là thử nghiệm quan trọng nhất của chương.

```java
final class PluginWithSideEffect {
    static {
        System.out.println("STATIC INITIALIZER RAN");
    }
}

public class LoadWithoutInitializeDemo {
    public static void main(String[] args) throws Exception {
        ClassLoader loader = LoadWithoutInitializeDemo.class.getClassLoader();

        Class<?> loaded = Class.forName(
                "com.example.PluginWithSideEffect",
                false,
                loader
        );

        System.out.println("loaded = " + loaded.getName());
        System.out.println("before initialization");

        Class.forName(
                "com.example.PluginWithSideEffect",
                true,
                loader
        );
    }
}
```

Với binary name đúng và class nằm trên classpath, kết quả quan sát mong đợi có dạng:

```text
loaded = com.example.PluginWithSideEffect
before initialization
STATIC INITIALIZER RAN
```

`Class.forName(name, false, loader)` yêu cầu load/link khi cần nhưng không chủ động initialize class. Lần gọi với `true` yêu cầu initialization.

`ClassLoader.loadClass(name)` cũng thường được dùng để lấy `Class<?>` mà chưa kích hoạt initialization. Tác dụng phụ chỉ xuất hiện khi có initialization trigger phù hợp.

Cách hình dung khi kết thúc chương:

```text
bytes có tồn tại
    ↓ chưa đủ
loading tạo runtime Class identity
    ↓
linking verify + prepare + resolve
    ↓
active use
    ↓
initialization chạy static initialization
```

Chương tiếp theo sẽ trả lời câu hỏi tự nhiên: **loader nào đang làm các việc này trong một JVM Java 21 thông thường?**
