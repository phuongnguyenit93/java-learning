<a id="back-to-top"></a>

# Liên kết và khởi tạo

## Menu
- [Phân biệt nạp, liên kết và khởi tạo trong vòng đời một lớp](#linking-and-initialization-001)
- [Verification: kiểm tra định dạng, luồng điều khiển và an toàn kiểu](#linking-and-initialization-002)
- [Giới hạn của bytecode verifier so với tính đúng đắn của ứng dụng](#linking-and-initialization-003)
- [Preparation: tạo trường static và thiết lập giá trị mặc định](#linking-and-initialization-004)
- [Resolution: chuyển tham chiếu ký hiệu thành liên kết dùng khi chạy](#linking-and-initialization-005)
- [Phân giải sớm, phân giải muộn và thời điểm lỗi liên kết xuất hiện](#linking-and-initialization-006)
- [Các thao tác kích hoạt khởi tạo và các trường hợp chỉ nạp lớp](#linking-and-initialization-007)
- [Hằng số biên dịch, class literal và truy cập trường khai báo ở lớp cha](#linking-and-initialization-008)
- [Thứ tự khởi tạo lớp cha, giao diện liên quan và thành viên static](#linking-and-initialization-009)
- [Khóa khởi tạo lớp, khởi tạo đệ quy và nguy cơ chờ lẫn nhau](#linking-and-initialization-010)
- [Lỗi khởi tạo: ExceptionInInitializerError và trạng thái lớp lỗi](#linking-and-initialization-011)
- [Dự đoán kết quả bằng dấu vết nạp lớp và thứ tự chạy static initializer](#linking-and-initialization-012)

## <a id="linking-and-initialization-001">Phân biệt nạp, liên kết và khởi tạo trong vòng đời một lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt nạp, liên kết và khởi tạo trong vòng đời một lớp

## 1. Mục tiêu và mô hình tổng quan

Một lớp Java có thể đã tồn tại dưới dạng **Class object** trong JVM nhưng **chưa chạy static initializer**. Nó có thể đã được xác minh và chuẩn bị, trong khi một số symbolic references vẫn chưa được giải quyết. Ngược lại, khi ứng dụng thực sự dùng lớp, JVM phải đảm bảo các bước cần thiết đã hoàn tất đúng trình tự và đúng quy tắc an toàn kiểu.

Ba thuật ngữ chính của Java Virtual Machine Specification (JVMS) là:

1. **Loading (nạp):** tìm/thu nhận biểu diễn nhị phân của một class/interface và tạo lớp runtime tương ứng.
2. **Linking (liên kết):** đưa lớp vừa nạp vào trạng thái JVM có thể sử dụng, gồm **verification**, **preparation** và **resolution**; resolution có thể được thực hiện từng phần hoặc sớm tùy các điều kiện đặc tả.
3. **Initialization (khởi tạo):** thực thi công việc khởi tạo của class/interface, thường là những **static field initializers** và **static initializer blocks**, được compiler thể hiện trong phương thức đặc biệt **<clinit>** nếu cần.

Sau bài học, bạn cần:

- Phân biệt được ba giai đoạn, hiểu **verification**, **preparation**, **resolution** và mốc đảm bảo trước initialization.
- Nêu đúng các thao tác gây **active use**: tạo instance, gọi static method, đọc/ghi static field phù hợp, reflection yêu cầu khởi tạo.
- Giải thích vì sao **Class.forName(name, false, loader)**, **T.class**, hoặc một **compile-time constant** không nhất thiết kích hoạt initialization.
- Mô phỏng chính xác thứ tự static initializers, superclass và class con; phân biệt trường hợp interface.
- Phân biệt các lỗi **ClassNotFoundException**, **ClassFormatError**, **VerifyError**, **NoSuchMethodError**, **ExceptionInInitializerError** và **NoClassDefFoundError**.
- Dùng Java 17+, **javap** và class-loading logs để quan sát điều có thể quan sát, đồng thời không suy diễn quá mức về thời điểm verifier/resolver hoạt động.

**Ngữ cảnh:** Bài học nói về vòng đời một **class hoặc interface trong một JVM**; không đồng nghĩa vòng đời của một **đối tượng instance**, của **ClassLoader** hay toàn bộ tiến trình JVM.

## 2. Sơ đồ vòng đời và những mốc bắt buộc

Hãy đọc sơ đồ như một **mô hình phụ thuộc**, không phải một lịch trình đồng bộ tuyệt đối cho mọi lớp trong chương trình:

~~~text
Nguồn class bytes / module / classpath
               |
               v
        [1] LOADING
     - Tìm biểu diễn nhị phân
     - Tạo lớp runtime, Class object
     - Gắn defining class loader
               |
               v
        [2] LINKING
     +-------------------------------+
     | Verification                  |
     |   Kiểm tra tính hợp lệ/an toàn|
     |                               |
     | Preparation                   |
     |   Tạo static fields           |
     |   Gán giá trị mặc định        |
     |                               |
     | Resolution                    |
     |   Symbolic ref -> entity thật |
     |   Có thể xảy ra từng phần     |
     +-------------------------------+
               |
               v
        [3] INITIALIZATION
     - Gắn trạng thái đang khởi tạo
     - Khởi tạo superclass/phần
       superinterface thích hợp
     - Thực thi static initialization
       và các ConstantValue cần áp dụng
               |
               v
      SẴN SÀNG CHO ACTIVE USE
~~~

Các ràng buộc quan trọng theo JVMS:

- Một class/interface phải được **nạp hoàn chỉnh trước khi liên kết**.
- Nó phải được **xác minh và chuẩn bị hoàn chỉnh trước khi khởi tạo**.
- **Resolution** của từng symbolic reference có thể trì hoãn cho đến lúc tham chiếu đó thực sự được sử dụng, hoặc được thực hiện sớm khi JVM đủ điều kiện. Vì vậy không cần mọi dependency của lớp đã được resolve trước khi chạy phần static initializer đầu tiên.
- Các hoạt động có thể kích hoạt nạp/link gián tiếp lớp khác. Không thể liệt kê duy nhất một đường đi tuần tự từ lớp A tới lớp B mà mọi JVM đều phải thực hiện.
- **Khởi tạo** là sự kiện ở cấp lớp, được phối hợp khi nhiều thread cùng yêu cầu; **không chạy lại static initializer sau mỗi lần new**.

Bảng so sánh nhanh:

| Tiêu chí | Loading | Linking | Initialization |
| --- | --- | --- | --- |
| Kết quả chính | Lớp runtime được tạo và gắn loader | Metadata, tính hợp lệ, static storage, symbolic references được xử lý theo yêu cầu | Static initialization của lớp hoàn tất hoặc thất bại |
| Có thực thi code static initializer của lớp mục tiêu? | Không phải nội dung của giai đoạn loading | **Không** phải nhiệm vụ của verification/preparation/resolution | **Có**, nếu class/interface có code khởi tạo cần chạy |
| Liên quan class bytes? | Có | Có, nhất là verification/descriptors/constant pool | Dựa vào code và metadata đã được nạp/liên kết |
| Có thể gây ra nạp lớp khác? | Có, ví dụ superclass/superinterface | Có, khi xác minh/resolve type/reference | Có, do dùng class khác trong static code |
| Có hoàn tất tất cả symbolic references không? | Không | **Không bắt buộc** ngay lúc đầu | Không nhất thiết; resolution vẫn có thể diễn ra về sau |

## 3. Loading: class trở thành kiểu runtime như thế nào?

### 3.1. Định danh lớp không chỉ là tên

Với lớp thường, JVM nhận diện một kiểu qua **binary name** và **defining loader**:

~~~text
(com.example.Order, LoaderA)
(com.example.Order, LoaderB)
~~~

Nếu LoaderA và LoaderB định nghĩa hai lớp có cùng tên, đây vẫn là **hai runtime classes khác nhau**. Truyền một object của lớp thứ nhất sang nơi yêu cầu lớp thứ hai có thể thất bại khi cast, reflection, interface method invocation hoặc linking constraints.

Khái niệm cần phân biệt:

- **Defining loader:** loader thực sự định nghĩa class.
- **Initiating loader:** loader bắt đầu/yêu cầu quá trình nạp và có thể đã ủy quyền thành công cho một loader khác.
- **Bootstrap loader:** loader nền tảng của JVM; Java API thường biểu diễn nó bằng **null** khi đọc **Class.getClassLoader()** trên một class tương ứng.
- **Application/platform/custom loader:** các nguồn lookup khác nhau; cơ chế ủy quyền tùy loader và module configuration.

Nạp một class thông thường không đồng nghĩa tự động mở quyền truy cập reflection trong Java Platform Module System. Module readability/exports/opens còn chịu quy tắc riêng.

### 3.2. Một số API khiến người học tưởng là khởi tạo

~~~java
// Các dòng minh họa đặt trong một phương thức có throws Exception.
Class<?> a = SomeType.class;
Class<?> b = Class.forName("SomeType", false,
        SomeType.class.getClassLoader());
Class<?> c = SomeType.class.getClassLoader()
        .loadClass("SomeType");
~~~

Chúng đều có thể dẫn tới hoặc sử dụng Class object, nhưng **không yêu cầu thực thi static initializer của SomeType** chỉ vì lấy class literal, dùng overload forName với **initialize=false**, hay dùng **loadClass(name)** thông thường.

Ngược lại:

~~~java
// Mã minh họa bên trong một phương thức có throws Exception.
Class<?> c = Class.forName("SomeType"); // overload một tham số
~~~

**Class.forName(String)** dùng initiating class loader thích hợp và **yêu cầu initialization** theo contract của API. **Class.forName(name, true, loader)** cũng vậy. Nói “Class.forName chỉ nạp lớp” mà không phân biệt overload là không chính xác.

**Chú ý ngữ cảnh:** Trong ví dụ các dòng minh họa, **SomeType** phải là class thực sự có thể được tìm thấy bằng tên phù hợp. Tên nested class sử dụng ký tự **$** trong binary name, ví dụ **Example$Inner**, không phải **Example.Inner**.

### 3.3. Loading có thể là kết quả gián tiếp

JVM có thể phải nạp superclass, implemented interfaces hoặc một class được tham chiếu trong descriptor để xác minh/giải quyết ràng buộc. Việc bytecode chứa tên một class khác **không đảm bảo** class đó được nạp vào đúng một offset cố định trên mọi JVM.

Ví dụ, compiler có thể phát ra constant-pool entry trỏ tới **com.example.OptionalFeature** trong một method chỉ được gọi khi có cấu hình tương ứng. JVM có quyền dùng cách resolution eager hoặc lazy trong phạm vi đặc tả. Hãy tránh phụ thuộc vào suy luận “chưa gọi method đó nên class kia chắc chắn chưa được nạp” khi thiết kế hệ thống.

### 3.4. Array classes khác các class file thông thường

JVM tạo array classes theo cơ chế riêng. Không có quy ước tìm tệp **[I.class** cho kiểu **int[]**. Với **String[]**, array class có **Class** object nhưng không có mã **<clinit>** tự viết như class thường.

- **int[].class.getClassLoader()** thông thường trả **null** (primitive array).
- **UserType[].class.getClassLoader()** trả loader của reference component type theo API.
- Tạo **new UserType[10]** tạo mảng có phần tử null, **không tự khởi tạo UserType** chỉ vì array component có kiểu UserType.

## 4. Linking gồm chính xác ba công việc gì?

### 4.1. Verification: kiểm tra tính hợp lệ

**Verification** kiểm tra lớp đã nạp có cấu trúc và bytecode đáp ứng những quy tắc về type safety, operand stack, locals, control flow và constraints của JVM.

Những lỗi verifier có thể phát hiện hoặc liên quan:

- Một lệnh **ireturn** được dùng ở vị trí cần trả một reference không phù hợp.
- Một branch nhảy vào giữa operand bytes của instruction khác.
- Một instruction tiêu thụ giá trị khác kiểu được JVM cho phép từ operand stack.
- Độ sâu stack hoặc StackMapTable không nhất quán với các đường nhánh.
- Class file, inheritance hoặc descriptor vi phạm những ràng buộc JVM cần thiết.

Ví dụ bytecode ở mức khái niệm (không phải chương trình Java hợp lệ):

~~~text
iconst_1
areturn   // Không thể trả reference khi stack đang chứa int
~~~

**javac bình thường** không phát ra kiểu bytecode vi phạm này từ Java source hợp lệ. Người làm bytecode weaving/instrumentation hoặc generator cần dùng verifier, cập nhật stack map frames và tuân thủ class-file version/format.

Từ **ClassFormatError** (dữ liệu/định dạng class file không hợp lệ) đến **VerifyError** (bytecode không thỏa điều kiện xác minh) đều là lỗi liên quan class, nhưng **không nên đồng nhất nguyên nhân và giai đoạn**. Những loại lỗi này là các nhánh của **LinkageError**.

### 4.2. Preparation: chuẩn bị static storage và giá trị mặc định

Trong **preparation**, JVM tạo static fields cho class/interface và khởi tạo chúng về **default values** theo kiểu. Không cần thực thi mã Java để thực hiện công việc chuẩn bị này.

| Kiểu static field | Default value tại preparation |
| --- | --- |
| **int, short, byte, long** | 0 |
| **float, double** | 0.0 (kiểu tương ứng) |
| **boolean** | false |
| **char** | ký tự có giá trị 0 |
| **reference** | null |

Ví dụ:

~~~java
// Mã minh họa một phần class:
static int retries = 5;
static Object holder = new Object();
~~~

Trong preparation, hai field tồn tại với giá trị mặc định **0** và **null**. Việc thực hiện biểu thức **= 5** hay **new Object()** là công việc của **initialization** theo quy tắc class, không phải preparation.

**Ngoại lệ dễ nhầm:** Field **static final** có **ConstantValue** attribute (compile-time constant) được JVM gán giá trị constant trong **quy trình initialization**, trước bước khởi tạo superclass và chạy code <clinit> theo JVMS §5.5. Không nên khẳng định mọi static final constant được gán giá trị cuối cùng ngay ở preparation. Tuy nhiên, truy cập một Java constant variable từ client không bắt buộc kích hoạt initialization của lớp khai báo do compiler có thể dùng hằng đã biết; đây là một quy tắc khác.

### 4.3. Resolution: từ symbolic reference đến tham chiếu thật

Class file thường không chứa con trỏ trực tiếp tới method/field/class runtime khác. Nó giữ **symbolic references** trong constant pool, ví dụ:

~~~text
Owner internal name: java/lang/String
Method name:          length
Method descriptor:    ()I
~~~

**Resolution** tìm thực thể runtime phù hợp cho reference, kiểm tra khả năng truy cập, chữ ký, loại member và những constraints cần thiết. Nhiều bytecode instructions yêu cầu resolution, như **new**, **getstatic**, **putstatic**, **invokevirtual**, **invokestatic**, **getfield**, **checkcast**, **ldc** và **invokedynamic**.

Ví dụ:

~~~java
// Mã minh họa:
int size = someString.length();
~~~

Bytecode có thể chứa **invokevirtual** trỏ tới symbolic method reference **java/lang/String.length:()I**. Khi sử dụng reference này, JVM phải bảo đảm nó resolve thành method phù hợp.

Những lỗi liên quan resolution/linking gồm:

| Lỗi | Một nguyên nhân thường gặp |
| --- | --- |
| **NoSuchMethodError** | Binary class đang chạy không còn method với name+descriptor mà caller từng biên dịch dựa vào |
| **NoSuchFieldError** | Field tham chiếu không còn tồn tại như caller yêu cầu |
| **IllegalAccessError** | Truy cập member/class bị cấm theo access/module rules tại runtime |
| **IncompatibleClassChangeError** | Cấu trúc member/class khác giả định lúc biên dịch; ví dụ static/instance bị đổi không tương thích |
| **NoClassDefFoundError** | JVM cần một class dependency nhưng không thể tìm/nạp/khởi tạo theo điều kiện cụ thể |

**Quan trọng:** resolution không nhất thiết hoàn tất đồng loạt trước khi class bắt đầu initialization. JVM có thể resolve reference khi instruction sử dụng nó. Vì vậy lỗi liên kết đối với một method hiếm dùng có thể chỉ lộ ra khi method được thực hiện, tùy JVM và loại reference.

### 4.4. Eager và lazy resolution có nghĩa gì?

~~~text
Cách diễn giải A (chỉ mang tính minh họa):
Load C -> verify -> prepare -> resolve nhiều ref sớm
       -> init C -> gọi method cần dùng ref

Cách diễn giải B:
Load C -> verify -> prepare -> init C
       -> gọi method -> resolve đúng ref cần dùng
~~~

JVMS cho JVM quyền lựa chọn thời điểm resolution sớm hoặc muộn cho nhiều symbolic references, song phải đảm bảo các tính chất bắt buộc về kết quả liên kết và điểm phát sinh lỗi. Các **dynamically-computed constants/call sites** có quy tắc riêng, không được đem những sơ đồ trên áp dụng mù quáng cho mọi bootstrap resolution.

Kết luận: quan sát thấy **Class.forName(name,false,loader)** trả về không chứng minh mọi class mà target có thể dùng đã resolve.

## 5. Initialization: Java thực thi những gì?

Khi một class/interface cần được khởi tạo, JVM bảo đảm việc xác minh/chuẩn bị và các bước nền tảng cần thiết đã hoàn thành. Công việc khởi tạo thường bao gồm:

1. Chuyển trạng thái class sang **being initialized** và phối hợp với các thread khác.
2. Gán những **static final** có **ConstantValue** theo quy tắc JVM.
3. Với **class**, đảm bảo superclass và các superinterfaces thuộc diện cần khởi tạo được xử lý theo thứ tự quy định.
4. Thực thi code khởi tạo của class/interface theo thứ tự do compiler và đặc tả quy định.
5. Đánh dấu **fully initialized** nếu thành công, hoặc **erroneous** nếu quá trình thất bại.

### 5.1. <clinit> là gì?

Trong class file, mã khởi tạo static được compiler gộp vào phương thức đặc biệt **<clinit>** khi cần:

~~~java
class Configuration {
    static int port = loadPort();

    static {
        System.out.println("Configuration initialized");
    }

    static int loadPort() {
        return 8080;
    }
}
~~~

Về ngữ nghĩa, đoạn này chạy lần lượt lời gọi **loadPort()** rồi static block khi Configuration được khởi tạo; code tương ứng có thể xuất hiện trong **<clinit>**. <clinit> không phải constructor **<init>** và không được viết trực tiếp như một method Java bình thường.

Những lớp chỉ có static compile-time constants có thể **không cần** một <clinit> chứa code, nhưng vẫn có trạng thái lifecycle của class.

### 5.2. Static fields và static blocks chạy theo thứ tự văn bản

~~~java
class Steps {
    static int a = print("a", 1);
    static { System.out.println("block"); }
    static int b = print("b", 2);

    static int print(String label, int result) {
        System.out.println(label);
        return result;
    }
}
~~~

Khi Steps được khởi tạo lần đầu, trật tự lời in là:

~~~text
a
block
b
~~~

Không phải JVM chạy **mọi static field initializer trước**, rồi mới chạy **mọi static block**. Compiler phải bảo toàn thứ tự văn bản của các static initializers theo JLS.

### 5.3. Initialization không phải instance construction

~~~java
class Widget {
    static {
        System.out.println("class init");
    }

    {
        System.out.println("instance init");
    }

    Widget() {
        System.out.println("constructor");
    }
}
~~~

Khi **new Widget()** lần đầu:

~~~text
class init
instance init
constructor
~~~

Với **new Widget()** lần hai trong cùng runtime class:

~~~text
instance init
constructor
~~~

**Static initialization** xảy ra ở cấp class, còn **instance field initializers**, **instance initializer blocks** và constructor chạy theo quy tắc tạo từng object. Cả hai đều gọi là “khởi tạo” trong tiếng Việt, nhưng tuyệt đối không được trộn lại khi phân tích vòng đời class JVM.

## 6. Khi nào class/interface được khởi tạo?

Các trường hợp phổ biến theo JLS §12.4.1/JVMS §5.5:

- Tạo instance bằng **new C()** lần đầu làm C cần được khởi tạo (và lớp cha theo quy tắc).
- Gọi **static method thực sự được khai báo bởi C**.
- Ghi một **static field khai báo bởi C**.
- Đọc một **static field khai báo bởi C mà không phải constant variable**.
- Yêu cầu khởi tạo qua một số reflection APIs, chẳng hạn **Class.forName(name,true,loader)**.
- C được JVM chọn làm initial class khi bắt đầu tiến trình.
- Những tình huống đặc tả khác như method handles và việc khởi tạo subclass/superinterfaces thích hợp.

**Không coi mọi cú pháp nhắc tới tên C là một active use.**

### 6.1. Những thao tác thường không tự kích hoạt initialization

| Thao tác | Lưu ý |
| --- | --- |
| **C.class** | Class literal không tự chạy <clinit> của C |
| **obj instanceof C** | Không yêu cầu initialization của C chỉ vì kiểm tra kiểu |
| Cast **(C) obj** | Không tự khởi tạo C chỉ vì cast |
| Tạo **new C[10]** | Tạo mảng, không tạo C instances và không tự init C |
| Khai báo **C variable;** | Khai báo biến kiểu C không phải active use |
| **ClassLoader.loadClass(name)** | Thông thường không yêu cầu initialize |
| **Class.forName(name,false,loader)** | Yêu cầu load/link theo API, không initialize |
| Đọc compile-time constant **C.ANSWER** | Không khởi tạo C nếu field là constant variable theo quy tắc |
| Gọi **getDeclaredMethods()** trên Class | Không được suy rằng mọi reflection inspection đều init class; cần đọc contract của từng API |

**Lưu ý:** các thao tác không làm **initialize C** vẫn có thể khiến C hoặc lớp khác được **load**, **link**, **resolve** tùy trường hợp. Đây là lý do không in static log không thể được dùng làm bằng chứng rằng lớp chưa được nạp.

### 6.2. Static field khai báo ở lớp cha

Xét:

~~~java
class Base {
    static int number = 123;
}

class Child extends Base {
    static {
        System.out.println("Child init");
    }
}
~~~

Nếu client đọc:

~~~java
System.out.println(Child.number);
~~~

Field **number** thực sự được khai báo trong **Base**. Quy tắc Java chỉ yêu cầu khởi tạo lớp **Base** cho thao tác đó; không có yêu cầu khởi tạo **Child** vì tên Child xuất hiện bên trái dấu chấm.

Ngược lại, nếu đọc field không phải constant **Child.ownNumber** khai báo thực sự trong Child, class Child cần init và superclass Base cũng được init trước nếu chưa.

### 6.3. Compile-time constants và việc inlining

~~~java
class Constants {
    static final int LIMIT = 256;         // constant variable
    static final String MODE = "basic";    // constant variable
    static final int DYNAMIC = compute(); // không là constant variable

    static int compute() {
        return 7;
    }

    static {
        System.out.println("init Constants");
    }
}
~~~

Khi client viết **Constants.LIMIT** hoặc **Constants.MODE**, compiler có thể thay thành chính giá trị constant trong bytecode của client, nên việc đọc chúng **không làm Constants khởi tạo**.

Đọc **Constants.DYNAMIC** là một active use và phải kích hoạt initialization nếu chưa chạy.

Cẩn thận với **static final**: không phải field nào có cả hai từ khóa này cũng là constant variable. **static final Integer BOXED = 1**, **static final String TEXT = new String("x")**, và **static final int DYNAMIC = compute()** đều không phải Java constant variables theo quy tắc compile-time constant expression phù hợp.

**Hệ quả binary compatibility:** Khi thay đổi giá trị của một public static final compile-time constant trong thư viện, client cũ có thể tiếp tục dùng giá trị đã inlined cho đến khi được biên dịch lại. Không đánh đồng sự cố này với class loader cache hoặc lỗi initialization.

## 7. Ví dụ Java 17+ chạy được: nạp nhưng chưa khởi tạo

Tạo file **LifecycleObservation.java**:

~~~java
public class LifecycleObservation {
    static class Probe {
        static final int LIMIT = 42;
        static int current = initializeCurrent();

        static int initializeCurrent() {
            System.out.println("Probe:field");
            return 7;
        }

        static {
            System.out.println("Probe:block");
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("main:start");

        ClassLoader loader =
                LifecycleObservation.class.getClassLoader();
        Class<?> type = Class.forName(
                "LifecycleObservation$Probe", false, loader);

        System.out.println("sameClass=" + (type == Probe.class));
        System.out.println("constant=" + Probe.LIMIT);
        System.out.println("main:before-active-use");

        System.out.println("value=" + Probe.current);
        System.out.println("valueAgain=" + Probe.current);

        Class.forName("LifecycleObservation$Probe", true, loader);
        System.out.println("main:end");
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 LifecycleObservation.java
java LifecycleObservation
~~~

**Stdout chính xác trong một lần khởi chạy JVM mới:**

~~~text
main:start
sameClass=true
constant=42
main:before-active-use
Probe:field
Probe:block
value=7
valueAgain=7
main:end
~~~

Giải thích theo thứ tự:

1. Main class **LifecycleObservation** được khởi tạo trước khi gọi main.
2. **Class.forName(...,false,loader)** nạp/liên kết **Probe** theo contract nhưng chưa yêu cầu initialize.
3. **Probe.class** không phải active use, vì vậy phép so sánh Class objects không in thông điệp của Probe.
4. **Probe.LIMIT** là constant variable, client có thể dùng hằng 42 trực tiếp.
5. Dòng **Probe.current** lần đầu mới là active use: JVM chạy static initialization của Probe theo thứ tự **initializeCurrent()** rồi static block.
6. Đọc lại current trả 7 mà **không chạy lại** init.
7. Gọi **Class.forName(...,true,loader)** sau khi class đã khởi tạo thành công không khiến init chạy thêm lần nữa.

**Điều không thể suy từ stdout:** Không in gì khi forName(false) không chứng minh verifier chưa chạy hoặc pool chưa có symbolic references được resolve. Các bước linking không được thiết kế để in log từ Java source.

## 8. Preparation và giá trị mặc định quan sát được trong <clinit>

Tạo file **PreparationObservation.java**:

~~~java
public class PreparationObservation {
    static class State {
        static int first = readSecond();
        static int second = 9;

        static int readSecond() {
            return second;
        }

        static {
            System.out.println("State:ready");
        }
    }

    public static void main(String[] args) {
        System.out.println("first=" + State.first);
        System.out.println("second=" + State.second);
    }
}
~~~

Chạy:

~~~bash
javac --release 17 -encoding UTF-8 PreparationObservation.java
java PreparationObservation
~~~

**Stdout:**

~~~text
State:ready
first=0
second=9
~~~

Phân tích:

- Khi liên kết State, static fields được tạo và nhận default value **0**.
- Khi khởi tạo State, initializer của **first** gọi method **readSecond()** trước khi initializer của **second** chạy.
- **readSecond()** có thể đọc **second** đang ở default value 0 (tham chiếu gián tiếp qua method, không vướng quy tắc cấm forward reference trực tiếp của Java source).
- Sau đó **second = 9**, rồi static block in **State:ready**.
- Main đợi State khởi tạo hoàn tất để in **first=0** và **second=9**.

Đây là hiện tượng do **thứ tự static initializers** trong class. Không kết luận preparation “chạy code của readSecond”; readSecond thực thi trong initialization.

Nếu thử viết trực tiếp **static int first = second; static int second = 9;**, compiler Java có thể báo **illegal forward reference**. Ví dụ dùng method để cho thấy state default hợp lệ khi khởi tạo theo thứ tự.

### 8.1. Giới hạn của quan sát

JVM không cung cấp một điểm Java thông thường để bạn xen lệnh println vào giữa verification/preparation của chính lớp rồi quan sát tùy ý. Chương trình trên **gián tiếp** cho thấy một static field chưa được gán biểu thức initializer trong <clinit> có default value khi code khởi tạo bắt đầu chạy.

Đừng đoán kích thước bộ nhớ của static fields hoặc thứ tự các internal data structures của VM từ output này.

## 9. Khởi tạo superclass trước subclass

Tạo file **InheritanceInitialization.java**:

~~~java
public class InheritanceInitialization {
    static class Parent {
        static int base = trace("Parent:field", 10);

        static {
            System.out.println("Parent:block");
        }
    }

    static class Child extends Parent {
        static int own = trace("Child:field", 20);

        static {
            System.out.println("Child:block");
        }
    }

    static int trace(String message, int value) {
        System.out.println(message);
        return value;
    }

    public static void main(String[] args) {
        System.out.println("main:start");
        System.out.println("childOwn=" + Child.own);
        System.out.println("baseViaChild=" + Child.base);
        System.out.println("main:end");
    }
}
~~~

Chạy:

~~~bash
javac --release 17 -encoding UTF-8 InheritanceInitialization.java
java InheritanceInitialization
~~~

**Stdout:**

~~~text
main:start
Parent:field
Parent:block
Child:field
Child:block
childOwn=20
baseViaChild=10
main:end
~~~

Nguyên nhân:

1. Đọc **Child.own** kích hoạt initialization của Child.
2. Trước đó JVM phải đảm bảo superclass **Parent** được khởi tạo.
3. Parent chạy các initializer theo thứ tự field rồi block.
4. Child mới chạy field initializer và static block.
5. Lệnh **Child.base** tiếp theo thực chất truy cập field khai báo tại Parent và **không init lại** lớp nào.

Nếu chỉ đọc **Child.base** trước mà chưa từng truy cập **Child.own**, JVM có thể init Parent mà **chưa cần init Child**.

### 9.1. Interface có quy tắc khác class

- Khởi tạo một **class** kéo theo khởi tạo superclass và những superinterfaces thuộc diện khai báo default methods theo quy tắc JVMS/JLS.
- Khởi tạo một **interface** **không tự động** khởi tạo toàn bộ superinterfaces của nó.
- Truy cập static field của interface được khai báo ở interface nào thì quy tắc active use xét interface khai báo đó; một constant variable không nhất thiết kích hoạt initialization.
- Có những default methods sinh bởi compiler; phân tích interface initialization phức tạp cần đối chiếu class file, không chỉ tên method nhìn thấy trong source.

Ví dụ interface có field:

~~~java
interface Flags {
    int STATIC_CONSTANT = 5;
    int DYNAMIC = initialize();

    static int initialize() {
        System.out.println("Flags:init");
        return 8;
    }
}
~~~

Đọc **Flags.STATIC_CONSTANT** không yêu cầu init; đọc **Flags.DYNAMIC** yêu cầu init Flags và chạy code của **initialize()**. Đây là mã minh họa độc lập về nguyên tắc; chỉ khi đặt vào chương trình có main và thực hiện truy cập mới có output.

## 10. Vòng đời lớp khi nhiều thread cùng yêu cầu initialization

JVM phải điều phối class initialization khi nhiều threads cùng active use:

- Nếu một thread đang init C, thread khác cần init C sẽ chờ theo cơ chế initialization lock mà VM quy định.
- Nếu chính thread đang init C đệ quy yêu cầu init lại C, VM có quy tắc xử lý yêu cầu đệ quy; nó không đơn giản chạy một <clinit> thứ hai.
- Khi init thành công, class đạt trạng thái fully initialized; những lần yêu cầu sau không chạy lại static initialization.
- Nếu init thất bại, class chuyển sang **erroneous state**; những yêu cầu init tiếp theo trong cùng runtime class không tự chạy lại <clinit>.

**Không đồng nhất initialization lock với việc mọi static method bị synchronized.** Sau khi lớp đã khởi tạo, static methods vẫn có các quy tắc đồng thời riêng; viết/đọc mutable static fields giữa threads phải theo Java Memory Model. Class initialization cung cấp bảo đảm xuất bản trạng thái khởi tạo theo ngữ nghĩa JVM, không biến toàn bộ static state về sau thành thread-safe.

### 10.1. Một lớp có được init hai lần trong cùng JVM không?

**Cùng runtime class**, tức cùng danh tính **(binary name, defining loader)**, thực hiện initialization một lần nếu thành công.

Nhưng hai defining class loaders khác nhau có thể định nghĩa hai runtime classes cùng binary name; mỗi runtime class có vòng đời initialization riêng. Đây là lý do web container/plugin được redeploy có thể thấy một bản sao static initializer mới khi dùng loader mới, trong khi lớp cũ vẫn tồn tại nếu loader cũ còn được tham chiếu.

## 11. Khởi tạo thất bại: trạng thái erroneous và lỗi sau đó

Một lỗi xảy ra trong static field initializer hoặc static block có thể làm initialization của lớp thất bại.

- Nếu code <clinit> ném một exception không phải Error, JVM thường báo **ExceptionInInitializerError** bọc lỗi gốc theo quy tắc JVMS.
- Nếu nguyên nhân đã là một **Error**, JVM có thể ném chính Error đó.
- Lớp bị đánh dấu **erroneous** khi initialization thất bại, và những active uses sau trong cùng runtime class có thể nhận **NoClassDefFoundError** thay vì chạy lại initializer.
- Các lớp phụ thuộc (ví dụ subclass) có thể thất bại khi việc init superclass bị lỗi.

### 11.1. Ví dụ runnable: lần đầu và lần sau khác nhau

Tạo file **FailedInitialization.java**:

~~~java
public class FailedInitialization {
    static class Broken {
        static int number = fail();

        static int fail() {
            System.out.println("Broken:init");
            throw new IllegalStateException("boom");
        }
    }

    public static void main(String[] args) {
        try {
            System.out.println(Broken.number);
        } catch (Throwable problem) {
            System.out.println("first="
                    + problem.getClass().getSimpleName());
        }

        try {
            System.out.println(Broken.number);
        } catch (Throwable problem) {
            System.out.println("second="
                    + problem.getClass().getSimpleName());
        }
    }
}
~~~

Chạy:

~~~bash
javac --release 17 -encoding UTF-8 FailedInitialization.java
java FailedInitialization
~~~

**Stdout:**

~~~text
Broken:init
first=ExceptionInInitializerError
second=NoClassDefFoundError
~~~

- Lần truy cập đầu kích hoạt **Broken.<clinit>**. **fail()** in log rồi ném IllegalStateException; JVM bọc lỗi theo quy tắc nên caller nhìn thấy ExceptionInInitializerError.
- Lần truy cập tiếp theo không chạy lại **fail()**, do runtime class Broken đã ở **erroneous state**; JVM báo NoClassDefFoundError.
- Mẫu code bắt **Throwable** chỉ nhằm **quan sát lỗi trong bài thí nghiệm**. Production code không nên dùng catch(Throwable) chung để tiếp tục chạy sau các Error nghiêm trọng.

**Đừng vội giải thích mọi NoClassDefFoundError là “thiếu .class”**. Nó còn có thể xuất hiện do lớp đã thất bại khởi tạo trước đó. Luôn đọc **cause** và toàn bộ chuỗi lỗi lần đầu.

### 11.2. Có thể “thử lại” init bằng cách gọi Class.forName lần nữa không?

Với **cùng runtime class** đã erroneous, gọi lại **Class.forName(name,true,loader)** không hồi phục trạng thái; initialization vẫn thất bại. Muốn xử lý phải sửa nguyên nhân và thường cần tiến trình/cấu hình mới hoặc một runtime class **thực sự khác** do loader mới định nghĩa, nếu kiến trúc cho phép. Không dùng loader mới để che giấu lỗi và gây class-identity conflict.

## 12. Các nhóm lỗi theo vòng đời

| Lỗi | Nhóm vấn đề điển hình | Khi nào thường gặp? |
| --- | --- | --- |
| **ClassNotFoundException** | Yêu cầu lookup theo tên không tìm được class | **Class.forName**, **ClassLoader.loadClass** |
| **ClassFormatError** | Class file sai cấu trúc/tags/descriptors hoặc constraints định dạng | Nạp và kiểm tra dữ liệu class |
| **UnsupportedClassVersionError** | Runtime không hỗ trợ major/minor version class file | Nạp class version mới bằng JVM cũ |
| **VerifyError** | Bytecode/stack maps/control flow sai điều kiện xác minh | Linking — verification |
| **NoSuchFieldError** | Field từng được biên dịch dựa vào nhưng runtime không còn tương thích | Resolution/field access |
| **NoSuchMethodError** | Method đã đổi/xóa không tương thích tại runtime | Resolution/invocation |
| **IllegalAccessError** | Kiểm tra access/module permission không hợp lệ | Linking/resolution |
| **IncompatibleClassChangeError** | Kỳ vọng về class/member dạng instance/static/interface không khớp | Linking hoặc sử dụng reference |
| **ExceptionInInitializerError** | Static initializer ném exception không phải Error | Initialization lần đầu |
| **NoClassDefFoundError** | JVM không thể định nghĩa/nạp class theo nhu cầu hoặc class đã erroneous | Khi cần class, sau load/init failure |
| **ClassCircularityError** | Ràng buộc vòng kế thừa class không hợp lệ | Trong loading/linking |
| **OutOfMemoryError** | Tài nguyên JVM cạn, có thể xảy ra ở nhiều nơi | Không phải bằng chứng riêng của một phase |

**Một triệu chứng có thể có nhiều nguyên nhân.** Ví dụ NoClassDefFoundError không chỉ do classpath, và IllegalAccessError không giống lỗi biên dịch Java về private access vì class file runtime có thể khác các class lúc build.

**Kiểm tra root cause** luôn đáng giá hơn suy luận từ tên exception đơn lẻ.

## 13. Cách quan sát lifecycle bằng công cụ

### 13.1. javap: nhận diện <clinit>, không đo trực tiếp phase

Sau khi biên dịch:

~~~bash
javap -c -p LifecycleObservation
javap -c -p 'LifecycleObservation$Probe'
javap -v -p 'LifecycleObservation$Probe'
~~~

Lệnh có tên chứa **$** nên cần quote thích hợp với shell; các dòng trên phù hợp trong Bash/PowerShell có hỗ trợ dấu nháy đơn, còn với Command Prompt cần điều chỉnh cú pháp. **javap -p** hiển thị thành viên private; **-c** hiện bytecode; **-v** hiện thêm constant pool/attributes.

Bạn có thể tìm:

- Trong main: **ldc** chứa class literal, **getstatic** đối với Probe.current, và **invokestatic** cho Class.forName.
- Trong Probe: method đặc biệt **static {}** hoặc **<clinit>** theo cách javap trình bày, bao gồm lời gọi **initializeCurrent** và println.
- **ConstantValue** attribute hoặc cách compiler sử dụng hằng đối với **Probe.LIMIT**.
- Các class/method/field references trong constant pool.

**Giới hạn:** javap chỉ cho thấy **class file đã biên dịch**; nó không cho biết chính xác runtime ở thời điểm nào đã chạy verifier hoặc resolve từng reference trên mọi JVM.

### 13.2. Log nạp và khởi tạo lớp trên JVM hỗ trợ

Với HotSpot hỗ trợ Unified JVM Logging (Java 17):

~~~bash
java -Xlog:class+load=info LifecycleObservation
~~~

Dạng log này có thể ghi ra rất nhiều tên lớp, nguồn dữ liệu và thông tin khác; định dạng, cấp độ và thứ tự log tùy JVM. Log load **không phải** log đã chạy <clinit>.

Có thể tra các tag hỗ trợ bằng:

~~~bash
java -Xlog:help
~~~

Không khẳng định mọi VM/version có chung một tag riêng “init” ở cùng mức log hoặc cùng bố cục output. Dùng stdout có chủ đích từ static blocks kết hợp log runtime để phân biệt:

- **Lớp có thể được nạp trước** dòng println của initializer.
- **Một static initializer chạy một lần** khi active use.
- **Không có dòng initializer** chưa đủ kết luận lớp chưa load/link.

### 13.3. Quan sát class loader và module

~~~java
// Mã minh họa đặt trong một phương thức:
Class<?> type = LifecycleObservation.class;
System.out.println(type.getName());
System.out.println(type.getClassLoader());
System.out.println(type.getModule().getName());
~~~

Tùy môi trường, loader có thể được in bằng tên implementation, module name có thể null với unnamed module. Do đó đây là các giá trị chẩn đoán động, không phải stdout cố định để học thuộc.

Muốn tìm Class object **mà không init**, dùng overload **Class.forName(name,false,loader)**; nhưng ngay cả API này vẫn có những tác động loading/linking và có thể ném linkage errors.

## 14. Dòng thời gian thực tế của một lệnh new

Khi chương trình gặp:

~~~java
// Mã minh họa một expression:
Widget w = new Widget();
~~~

Với **Widget** chưa từng được khởi tạo, tóm tắt hành vi:

1. JVM phải bảo đảm định nghĩa class Widget cần thiết đã được nạp/tạo.
2. JVM đảm bảo verification và preparation hoàn tất; resolution phù hợp được xử lý theo nhu cầu.
3. Vì **new Widget()** là active use, JVM cần initialization của Widget, bao gồm superclass và những trường hợp superinterface liên quan.
4. Sau khi initialization thành công, JVM thực hiện việc tạo instance: cấp phát, các giá trị mặc định cho instance fields, xử lý constructor chain, instance field initializers và constructor body theo quy tắc.
5. Trả reference tới đối tượng nếu toàn bộ thao tác thành công.

**Không đồng nhất** việc static field nhận giá trị mặc định ở **preparation** với việc instance fields nhận default values khi **tạo object**. Chúng diễn ra ở cấp khác nhau.

Trong bytecode, **new** và **invokespecial <init>** là các instruction/hoạt động khác nhau. **<clinit>** không phải constructor, và câu lệnh source **new Widget()** có thể làm cả class initialization lẫn instance initialization khi được dùng lần đầu.

## 15. Resolution, class initialization và startup dependency: một tình huống

Giả sử:

~~~java
// Mã minh họa trong class Service:
static {
    System.out.println("Service init");
}

static String loadData(boolean enabled) {
    if (enabled) {
        return OptionalLibrary.read();
    }
    return "disabled";
}
~~~

Nếu **OptionalLibrary** không tồn tại tại runtime, không thể kết luận chỉ từ source rằng **loadData(false)** luôn chạy được hay JVM nhất thiết ném lỗi ngay khi Service được load. Thời điểm resolution của reference còn chịu chiến lược JVM, điều kiện linkage và Java/module semantics.

Cách thiết kế tin cậy hơn:

- Nếu thư viện là dependency bắt buộc, đóng gói và kiểm tra nó ở deployment/build, đừng dựa vào lazy resolution để “né” dependency.
- Nếu tính năng thật sự tùy chọn, dùng class loading/reflection hoặc ranh giới module/plugin được thiết kế rõ, xử lý lỗi vắng dependency có chủ đích.
- Không đặt logic có side effects khó phục hồi vào static initializer chỉ để tải thư viện động.
- Viết test integration chạy với classpath/module path thật, không chỉ unit tests có đầy đủ dependency.

## 16. Lớp interface, enum, record và nested class có cùng những giai đoạn không?

**Có:** class và interface đều có loading, linking và initialization theo đặc tả, nhưng quy tắc initialization có điểm khác nhau.

- **Interface:** static fields của interface thường là **public static final**; một field được gán kết quả của lời gọi method không phải compile-time constant vẫn có thể yêu cầu interface initialization. Interface initialization không tự init tất cả superinterfaces của nó.
- **Enum:** là class có những bước khởi tạo liên quan enum constants; thao tác sử dụng enum có thể kích hoạt initialization của enum class, tạo các instance enum theo quy tắc.
- **Record:** là class chuyên biệt, vẫn có class initialization cho static members; record canonical constructor điều khiển instance initialization, không thay thế <clinit>.
- **Nested/static member class:** có binary name riêng và có vòng đời class riêng. Việc class ngoài được init **không tự động** init mọi static nested classes chỉ vì chúng được khai báo bên trong.
- **Anonymous/local class:** cũng có runtime class identity; chi tiết tên nhị phân do compiler tạo, và tạo instance của nó là active use thích hợp.

Ví dụ Java thường gọi “outer class” và “nested class” trong một đơn vị source, nhưng JVM nhìn chúng như các định nghĩa class riêng biệt trong class files và loader context.

## 17. Những hiểu lầm phải tránh

1. **“Đã load là đã chạy static block.”** Sai; load có thể xảy ra sớm hơn initialization.
2. **“Class.forName luôn chỉ load.”** Sai; overload một tham số và initialize=true có thể kích hoạt initialization.
3. **“C.class bảo đảm static initializer chạy.”** Sai; class literal không phải active use theo quy tắc thông thường.
4. **“Linking là một thao tác duy nhất, phải resolve xong mọi reference.”** Sai; gồm verification, preparation, resolution và resolution có thể lazy.
5. **“Preparation chạy static int x = compute().”** Sai; đó là initialization.
6. **“Preparation gán mọi static final field giá trị cuối cùng ngay lập tức.”** Không chuẩn xác; ConstantValue được áp dụng theo quy trình initialization riêng.
7. **“Nếu static final thì chắc chắn không init class khi đọc.”** Sai với static final không phải constant variable.
8. **“Tạo new C[10] cũng giống new C().”** Sai; array allocation không tạo C instance và không yêu cầu init C chỉ vì component type.
9. **“Đọc Child.parentField sẽ init Child.”** Không nếu field thực sự khai báo tại Parent và không có active use khác của Child.
10. **“Mọi interface parent phải init trước subinterface.”** Sai; interface có quy tắc khác class.
11. **“Static initializer chạy một lần cho mỗi object.”** Sai; chạy ở cấp runtime class.
12. **“NoClassDefFoundError chỉ có nghĩa .class thiếu.”** Sai; một class khởi tạo thất bại trước đó cũng có thể cho lỗi này.
13. **“Hai lớp cùng tên luôn dùng chung static state.”** Sai nếu được định nghĩa bởi loaders khác nhau.
14. **“javap cho thấy chính xác thời điểm JVM link mọi reference.”** Sai; nó cho bytecode/metadata, không phải timing runtime toàn cục.
15. **“JIT được phép bỏ quy tắc class initialization để nhanh hơn.”** Sai; mọi tối ưu phải bảo toàn ngữ nghĩa và side effects có thể quan sát được theo đặc tả.

## 18. Bài tập kiểm tra có đáp án

**Bài 1 — Nhận diện phase**

Một loader đã tìm được bytes và tạo Class object; JVM kiểm tra stack typing; sau đó một static block mới in log. Gán ba hành động với phase.

**Đáp án:** Tạo Class object thuộc **loading**; stack typing thuộc **linking/verification**; in log static block thuộc **initialization**.

**Bài 2 — Default value**

~~~java
class C {
    static int a = read();
    static int b = 15;

    static int read() { return b; }
}
~~~

Lần đầu truy cập **C.a**, giá trị là bao nhiêu?

**Đáp án:** **0**. Khi initializer của a chạy, b vẫn có giá trị mặc định vì initializer của b nằm phía sau trong thứ tự văn bản.

**Bài 3 — Class literal**

~~~java
class C {
    static { System.out.println("C initialized"); }
}
Class<?> type = C.class;
~~~

Lấy class literal có bắt buộc in chữ **C initialized** không?

**Đáp án:** Không. Class literal có thể lấy/làm lớp được nạp/liên kết theo nhu cầu, nhưng không phải active use khởi tạo class.

**Bài 4 — Static constant**

~~~java
class C {
    static final int X = 9;
    static final Integer Y = 9;
    static { System.out.println("init"); }
}
~~~

Đọc **C.X** và **C.Y** có cùng hành vi initialization không?

**Đáp án:** Không. **X** là constant variable nên đọc nó không yêu cầu init C; **Y** là boxed Integer, không phải compile-time constant variable nên **getstatic** để đọc Y kích hoạt initialization.

**Bài 5 — Truy cập static field kế thừa**

Có **Base.count** và **Child extends Base**. Chỉ đọc **Child.count**, nếu count thực sự khai báo tại Base, cần init class nào?

**Đáp án:** Class **Base** khi field không phải constant variable; không bắt buộc init Child vì nó không khai báo field.

**Bài 6 — Resolution sớm hay muộn**

Một method chưa gọi chứa symbolic ref tới class OptionalFeature. Có thể khẳng định JVM tuyệt đối chưa resolve ref đó không?

**Đáp án:** Không. JVMS cho phép resolution sớm hoặc muộn trong các ràng buộc quy định. Không dựa vào điểm này để thiếu dependency bắt buộc.

**Bài 7 — Initialization thất bại**

Một static initializer ném IllegalStateException lần đầu. Lần đầu và lần sau có thể là lỗi gì?

**Đáp án:** Thường **ExceptionInInitializerError** lần đầu; class chuyển erroneous, những active uses tiếp sau thường nhận **NoClassDefFoundError**; không tự chạy lại initializer.

**Bài 8 — Nested class**

Outer đã được khởi tạo. Có chắc static nested class **Outer.Helper** cũng đã init?

**Đáp án:** Không. Nested member class có runtime class riêng; khai báo lồng nhau không đồng nghĩa cùng vòng đời initialization.

**Bài 9 — Constructor vs <clinit>**

Một chương trình tạo hai đối tượng C liên tiếp. Một static block và constructor chạy mấy lần?

**Đáp án:** Nếu C mới được khởi tạo và cả hai lần tạo thành công, static block chạy **một lần**, constructor chạy **hai lần**.

**Bài 10 — Loader identity**

Hai class loaders khác nhau định nghĩa hai lớp cùng tên **example.Counter**. Static initializer/state có dùng chung không?

**Đáp án:** Không. Đây là hai runtime classes; mỗi loại có initialization và static storage riêng.

**Bài 11 — Kiểm tra lỗi**

Bản thư viện runtime đã xóa method nhưng caller được build với bản cũ. Đây thường là lỗi phase nào?

**Đáp án:** Nhóm **linking/resolution**, có thể hiện thành **NoSuchMethodError** khi reference cần giải quyết/sử dụng.

**Bài 12 — Lịch trình bắt buộc**

Tại thời điểm bắt đầu chạy <clinit> của một class, JVM bắt buộc đã verify/prepare class đó chưa? Mọi reference đã resolve chưa?

**Đáp án:** **Đã verify và prepare**, nhưng **không bắt buộc resolve mọi symbolic reference**.

## 19. Checklist khi chẩn đoán sự cố và thiết kế ứng dụng

1. **Xác định class thực sự:** binary name + defining loader + module, tránh nhầm hai lớp cùng tên trong plugin/application server.
2. **Tách câu hỏi:** “đã load?”, “đã verify/prepare?”, “symbolic reference nào đã resolve?”, “đã initialize?”.
3. **Dùng đúng API:** forName(name,false,loader) cho lookup không yêu cầu initialization; forName(name,true,loader) khi thật sự muốn initialization.
4. **Kiểm tra active use:** new, static read/write, static invoke, reflection và ngoại lệ compile-time constant.
5. **Theo dõi thứ tự <clinit>:** static fields/static blocks theo thứ tự văn bản, superclass và default-method superinterfaces khi cần.
6. **Chẩn đoán nguyên nhân:** ClassFormatError, VerifyError, NoSuchMethodError, ExceptionInInitializerError, NoClassDefFoundError là các tín hiệu khác nhau.
7. **Tránh static init nặng:** hạn chế I/O, kết nối mạng, thread creation, loading động hoặc phụ thuộc dễ thất bại trong static initializers; nếu cần, thiết kế initialization có thông báo lỗi rõ và test đầy đủ.
8. **Không dựa vào lazy resolution:** dependency bắt buộc phải đóng gói đầy đủ; dependency tùy chọn cần contract và biện pháp xử lý riêng.
9. **Dùng javap và runtime logging có mục tiêu:** đọc Code/constant pool và kết hợp trace thực tế, không suy từ source/stack trace một mình.
10. **Cẩn trọng với concurrency:** static mutable state sau initialization vẫn cần synchronization/happens-before phù hợp.
11. **Thử đúng môi trường:** JDK version, --release, module path, classpath, container/IDE loader và các phiên bản thư viện.
12. **Đoạn code có side effect:** nếu thay đổi thời điểm initialization bằng reflection, instrumentation hoặc lazy holder, phải bảo toàn thứ tự và contract người dùng có thể quan sát.

### Tài liệu chính thức

- **JVMS Java SE 17, §5.3 — Creation and Loading:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- **JVMS Java SE 17, §5.4 — Linking (Verification, Preparation, Resolution):** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4
- **JVMS Java SE 17, §5.5 — Initialization:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5
- **JLS Java SE 17, §12.2–12.3 — Loading, Linking:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html
- **JLS Java SE 17, §12.4 — Initialization and active use:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4
- **Java 17 API, Class.forName:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- **Java 17 API, ClassLoader:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html

**Ghi nhớ cốt lõi:** **Loading** tạo lớp runtime; **Linking** xác minh, chuẩn bị static storage và giải quyết symbolic references theo nhu cầu; **Initialization** mới thực hiện mã khởi tạo của lớp theo các active uses và thứ tự quy định. **Đã có Class object không có nghĩa <clinit> đã chạy**; **đã bắt đầu <clinit> không có nghĩa mọi reference đã được resolve**. Khi chẩn đoán lỗi, hãy xác định đúng phase, đúng runtime class và đúng tác vụ gây ra lần sử dụng lớp đó.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="linking-and-initialization-002">Verification: kiểm tra định dạng, luồng điều khiển và an toàn kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Verification của JVM: kiểm tra định dạng, luồng điều khiển và an toàn kiểu

Trước khi một phương thức Java được thực thi, JVM phải có cơ sở tin rằng bytecode **hợp lệ về cấu trúc** và **không thể sử dụng operand stack, local variables hoặc object references sai kiểu theo những điều kiện tĩnh mà đặc tả quy định**. Đây là trách nhiệm của **class-file verification** — một phần quan trọng của giai đoạn **linking** trong vòng đời tải, liên kết và khởi tạo lớp.

Chẳng hạn, source Java hợp lệ không cho phép lấy một `int` đem dùng như một `String` tùy tiện. Nhưng JVM còn có thể nhận class files do trình tạo bytecode, thư viện instrumentation hoặc class loader cung cấp; nó **không thể mặc định tin tưởng rằng tệp `.class` đã qua `javac`**. Verifier kiểm tra các ràng buộc liên quan để phát hiện các lớp code lỗi, bị biến đổi sai hoặc có trạng thái không hợp lệ **trước khi** chúng được phép sử dụng theo ngữ nghĩa JVM.

Bài này giải thích đầy đủ ba lớp ý tưởng:

1. **Format checking:** tệp class có đúng cấu trúc nhị phân, constant pool, attributes và các giới hạn định dạng không?
2. **Static/structural constraints và control-flow checking:** bytecode có instruction, branch target, exception handler, đường đi và operand-stack shape hợp lệ không?
3. **Type safety:** trên **mọi đường đi khả dĩ** tới một instruction, verifier có thể chứng minh local variables, operand stack, tham số, receiver và giá trị trả về tương thích kiểu hay không?

Chúng ta còn xem **`StackMapTable`**, sự khác biệt `ClassFormatError` / `VerifyError`, ví dụ Java 17 chạy được và cách kiểm tra thực tế bằng `javap`.

## 1. Verification đứng ở đâu trong vòng đời JVM?

Mô hình khái niệm trong *Java Virtual Machine Specification* (JVMS):

```text
Bytecode / class representation
          │
          ▼
     Loading (tạo class/interface representation)
          │
          ▼
        Linking
          ├── Verification
          │      ├── kiểm tra định dạng class file
          │      ├── kiểm tra ràng buộc mã JVM
          │      └── kiểm tra kiểu và control flow
          ├── Preparation
          │      └── chuẩn bị storage và giá trị mặc định cho static fields
          └── Resolution
                 └── giải tham chiếu ký hiệu theo yêu cầu runtime
          │
          ▼
     Initialization
          └── thực thi class initialization method <clinit>
              khi có và khi JVM cần theo quy tắc
          │
          ▼
   Code được thực thi bởi interpreter/JIT
```

Đây là **trật tự khái niệm**, không phải yêu cầu mọi class đều được resolve toàn bộ trước khi bắt đầu chạy chương trình. JVMS cho phép **resolution có thể diễn ra theo nhu cầu**, còn quá trình tải/liên kết lớp liên quan khác có thể đan xen. Một implementation cũng có thể tổ chức/hoãn một số công việc kiểm tra theo các quy tắc đặc tả, nhưng **không được bỏ qua kết quả verification bắt buộc**.

### 1.1. Phân biệt verification, preparation, resolution, initialization

| Giai đoạn | Câu hỏi chính | Ví dụ |
| --- | --- | --- |
| **Loading** | JVM nhận diện và tạo biểu diễn của class/interface bằng class loader nào? | Loader tìm bytes của `app.Example` |
| **Verification** | Class file và mã bên trong có hợp lệ về dạng và type state? | `iload` không được đọc slot đang mang reference |
| **Preparation** | Static fields được chuẩn bị theo quy tắc VM ra sao? | `static int count` có giá trị chuẩn bị mặc định 0, trước initialization |
| **Resolution** | Một symbolic reference chỉ tới class/field/method thực nào? | `invokevirtual` có method reference cần giải |
| **Initialization** | Chạy code khởi tạo static của class khi bắt buộc? | `static { System.out.println("init"); }` |

**Không nhầm:** verifier **không chạy các nhánh `if` để thử xem chương trình cho ra kết quả gì**. Nó phân tích metadata và **trạng thái kiểu trừu tượng**. Việc lệnh gọi tìm được phương thức thực tại runtime còn liên quan tới resolution/linking; `NoSuchMethodError` vì thiếu method không đơn giản đồng nghĩa với một `VerifyError` do sai kiểu operand stack.

## 2. Lớp thứ nhất: format checking của tệp `.class`

Một class file có định dạng xác định, bắt đầu bằng `CAFEBABE` và có các trường `minor_version`, `major_version`, `constant_pool`, `access_flags`, `this_class`, `super_class`, danh sách field/method và các attributes.

```text
ClassFile
├── magic                  // phải là 0xCAFEBABE
├── minor_version
├── major_version
├── constant_pool_count
├── constant_pool[]
├── access_flags
├── this_class / super_class / interfaces[]
├── fields[]
├── methods[]
└── attributes[]
     └── Code attribute của phương thức có bytecode
          ├── max_stack
          ├── max_locals
          ├── code_length / code[]
          ├── exception_table[]
          └── attributes[]  (ví dụ StackMapTable)
```

Trong **format checking**, JVM phải nhận ra class-file structure hợp lệ: không thiếu bytes, không có dữ liệu rác bất hợp lệ ở cuối, magic đúng, các trường có index và độ dài hợp lệ, các predefined attributes phù hợp ràng buộc của chúng. Vấn đề class-file format cũng liên quan version tương thích của JVM.

Ví dụ lỗi định dạng cần nghĩ tới:

- Tệp `.class` bị cắt cụt khi copy, thiếu bytes của constant pool.
- `attribute_length` không khớp với kích thước thực của một attribute đã được định nghĩa.
- Một `constant_pool` entry chứa tag không hợp lệ cho định dạng/phiên bản được công nhận.
- Chỉ số tới constant pool chỉ đến loại mục không đúng tại vị trí bắt buộc.
- Cấu trúc `Code` attribute trái quy tắc định dạng của class-file version.

**Format checking và bytecode verification có liên quan nhưng không phải cùng khái niệm.** Dù cả hai đều góp phần kiểm tra tính toàn vẹn trước khi sử dụng class, một tệp có cấu trúc nhị phân hoàn chỉnh vẫn có thể chứa code **sai kiểu**.

### 2.1. `major.minor` không phải trạng thái kiểu

Class file `61.0` tương ứng Java 17 thông thường. Một JVM Java 11 có thể từ chối vì major của class quá mới — lỗi phiên bản, không phải bài toán xem `iadd` có đủ hai `int` trên stack hay không.

Class format preview dùng quy tắc minor đặc biệt `65535` ở những release phù hợp; cờ `--enable-preview` chỉ có ý nghĩa riêng với release tương ứng, **không** bỏ qua format checking/type verification và **không** cho phép chạy tùy tiện class files có version cao hơn.

### 2.2. Kết quả lỗi định dạng

`ClassFormatError` là lỗi tiêu biểu khi class-file representation bị hỏng về định dạng. `UnsupportedClassVersionError` báo class file có version không được JVM hỗ trợ hoặc tình huống version/preview không hợp lệ.

Đừng chỉ dựa vào một từ trong stack trace để “chữa” bằng `--enable-preview`, nâng heap hoặc thêm dependency. Phải nhìn **loại lỗi**, thông điệp version/constant-pool/attribute liên quan và artifact class thực sự được nạp.

## 3. Lớp thứ hai: cấu trúc instruction và luồng điều khiển

Mỗi method có bytecode mang các instruction, operands, nhánh và exception handlers. Verifier cùng các ràng buộc cấu trúc cần đảm bảo luồng thực thi không nhảy vào **giữa một instruction**, không đọc operand không tồn tại và không tạo đường đi trái quy tắc bytecode.

### 3.1. Control-flow graph (CFG)

Một method có thể được nhìn dưới dạng **basic blocks** — dãy instruction với điểm vào điều khiển xác định. Các cạnh đi theo:

- Đường tuần tự tới opcode tiếp theo.
- Các lệnh nhảy có điều kiện như `ifeq`, `if_icmpge`.
- `goto` và các target của `tableswitch` / `lookupswitch`.
- Các đường có thể chuyển sang **exception handler** khi instruction ở vùng protected phát sinh exception.
- Return hoặc throw để kết thúc đường bình thường/bất thường, không có successor tuần tự thông thường.

Ví dụ Java:

```java
static int choose(boolean yes, int value) {
    int result;
    if (yes) {
        result = value + 1;
    } else {
        result = value - 1;
    }
    return result;
}
```

CFG ý tưởng:

```text
                 [start]
                    │
                 test yes
                 /      \
              true      false
               │          │
         result = v+1  result = v-1
               │          │
               └────┬─────┘
                    ▼
            [join: result is int]
                    │
               return result
```

Cả hai đường dẫn đặt `result` thành **`int`** trước điểm nhập lại. Vì vậy instruction tải nó lên operand stack để `ireturn` có type state hợp lệ.

Đoạn này là **một phương thức minh họa**, không phải file tự chạy; ví dụ chương trình độc lập nằm ở mục 8.

### 3.2. Một branch target phải hợp lệ

Đối với bytecode branch:

- Target phải nằm trong `code[]` của chính phương thức.
- Target phải là **đầu của một instruction**, không phải byte operand hay padding.
- Switch instructions có encoding riêng (bao gồm alignment, default và jump offsets).
- Exception handler `handler_pc` cũng phải trỏ tới opcode đầu hợp lệ; các range bảo vệ trong `exception_table` phải thỏa quy tắc.
- Control flow và type state tại các join phải tương thích, không chỉ “địa chỉ có tồn tại”.

Ví dụ giả lập:

```text
0: bipush 100   // opcode ở 0, toán hạng immediate ở byte 1
2: istore_0

Sai: goto tới byte 1
Đúng về ranh giới opcode: goto tới byte 0 hoặc 2 (vẫn cần đúng kiểu/flow)
```

Đây là sơ đồ **bytecode không hợp lệ** để giải thích; không thể tạo “goto byte 1” bằng cú pháp Java source hợp lệ thông thường. Khi instrument class files, dùng thư viện bytecode xử lý đúng branch offsets thay vì tự sửa bytes bằng find/replace.

### 3.3. Độ sâu operand stack phải nhất quán ở điểm nhập

Giả sử bytecode trừu tượng có hai đường tới cùng nhãn `L`:

```text
path A: stack = [int]
path B: stack = []
L: ireturn
```

Không thể để `ireturn` nhận **hai trạng thái có số operand khác nhau** tùy đường đến; không có bảo đảm luôn có một `int` tại điểm `L`.

Một lỗi khác:

```text
path A: stack = [int]
path B: stack = [reference-to-String]
L: ireturn
```

Hai đường có cùng số **phần tử** nhưng **kiểu không nhất quán** cho `ireturn`. Verifier không được chấp nhận một branch state mà ở đó instruction yêu cầu `int` nhưng có thể nhận object reference.

**Không phải** mọi nhánh phải có cùng stack state ở tất cả instruction. Chỉ những nơi luồng điều khiển hội tụ/điều kiện typing yêu cầu tương thích mới cần trạng thái thích hợp; mỗi basic block có thể dùng operand stack theo cách riêng.

## 4. Lớp thứ ba: type safety trên operand stack và local variables

Verifier xem **kiểu trừu tượng** của từng giá trị. Nó không cần biết `x = 12` hay `x = 99` tại runtime; nó cần chứng minh `x` ở vị trí đó là giá trị có **verification type** tương ứng với opcode.

Ví dụ:

| Instruction | Tiền điều kiện kiểu chủ yếu | Hành động |
| --- | --- | --- |
| `iload n` | Local `n` chứa giá trị thuộc int-family phù hợp | Đẩy `int` lên operand stack |
| `aload n` | Local `n` chứa reference phù hợp | Đẩy reference lên stack |
| `iadd` | Hai giá trị `int` ở đỉnh stack | Pop hai `int`, push kết quả `int` |
| `ladd` | Hai giá trị `long` category 2 | Pop hai `long`, push một `long` |
| `getfield` | Objectref tương thích với field resolution/access; field có descriptor hợp lệ | Pop receiver và push field value |
| `putfield` | Receiver cùng field value có type tương thích | Ghi field với type hợp lệ |
| `invokestatic` | Đủ arguments, các kiểu tương ứng method descriptor | Tiêu thụ arguments, đưa return value lên stack nếu non-`void` |
| `invokevirtual` | Có objectref receiver và các arguments thích hợp | Thực hiện lời gọi instance |
| `ireturn` | Return descriptor cho phép int-family và stack có `int` | Kết thúc bình thường, trả int-family value |
| `areturn` | Return descriptor là reference type, reference ở stack tương thích | Trả reference bình thường |

Bảng trên chỉ là **mô tả điều kiện kiểu chính**; mỗi opcode trong JVMS có các quy tắc chi tiết hơn về access, uninitialized values, constant pool và trạng thái frame.

### 4.1. Vì sao không thể dùng `int` như `String`?

Hãy tưởng tượng bytecode giả:

```text
iconst_1
invokevirtual java/lang/String.length:()I
```

Lệnh `invokevirtual` cần **objectref receiver** tương thích, nhưng operand stack chỉ có `int`. Mã bytecode đó **không hợp lệ về type safety**; verifier có thể từ chối bằng `VerifyError`. Không có một “ép kiểu ngầm từ int sang String” chỉ vì hai giá trị có thể tình cờ cùng kích thước máy.

### 4.2. Local-variable array có type state theo đường đi

Một local slot có thể được tái sử dụng cho giá trị khác sau khi biến cũ không còn cần thiết, nhưng ở **mọi instruction sử dụng**, verifier phải chứng minh slot có type hợp lệ trên mọi đường tới đó.

Mô hình lỗi:

```text
if condition:
    istore_2       // slot 2 là int
else:
    astore_2       // slot 2 là reference

join:
    iload_2        // không thể chứng minh luôn là int
```

Mã giả này không phải Java source được phép. Compiler Java thông thường sinh code tránh trạng thái sai như vậy. Nhưng bytecode generator có thể tạo class không an toàn nếu không tính type state khi hợp nhất luồng.

### 4.3. `long` và `double`: category 2

`long` và `double` là giá trị **category 2**:

- Trong local-variable array, mỗi giá trị chiếm **hai slots liên tiếp**; `lload n`/`dload n` truy xuất từ index **thấp hơn**.
- Trên operand stack, một `long`/`double` chiếm **hai đơn vị độ sâu**, nhưng là một giá trị logic duy nhất.
- Không được dùng `pop` (vốn thao tác một category-1 value) để loại bỏ “một nửa” `long`; cần opcode phù hợp như `pop2`.
- Ghi vào slot thứ hai của cặp local có thể vô hiệu hóa giá trị category 2 bắt đầu ở slot đầu.

Ví dụ method `static long plusOne(long input)` nhận `input` ở local slots **0–1** và kết thúc bằng `lreturn`; không có hai tham số `int` chỉ vì dùng hai slots.

## 5. `StackMapTable` và verification bằng type checking

Từ class-file **version 50.0 (Java 6)** trở lên, JVMS quy định verifier sử dụng **verification by type checking** với thông tin stack-map frames. Class-file version thấp hơn 50.0 dùng cơ chế **verification by type inference** theo đặc tả legacy; với version đúng **50.0**, có ngoại lệ tương thích cho phép implementation fallback trong điều kiện được JVMS nêu. Đối với class files Java 17 (`61.0`), cần tư duy theo **type checking/`StackMapTable`**, không trông chờ JVM tự suy kiểu theo kiểu cũ cho bytecode sai metadata.

Một `StackMapTable` là **attribute nằm bên trong `Code` attribute** của method; nó giúp verifier kiểm tra các trạng thái kiểu ở các **điểm điều khiển quan trọng**, đặc biệt basic-block entries.

### 5.1. Stack-map frame ghi gì?

Với một bytecode offset, stack-map frame mô tả kiểu trừu tượng của:

- Các local-variable slots cần biết tại điểm đó.
- Các giá trị trên operand stack tại điểm đó.
- Thông tin đặc biệt liên quan đến object chưa khởi tạo, khi cần.

Các verification types có thể gồm:

| Verification type | Ý nghĩa |
| --- | --- |
| `Integer` | Dạng int-family trong type system của verifier |
| `Float` | Giá trị float |
| `Long` | Giá trị long, độ rộng category 2 |
| `Double` | Giá trị double, độ rộng category 2 |
| `Null` | Giá trị null reference |
| `Object(className)` | Reference có loại class/array thích hợp |
| `UninitializedThis` | Receiver của constructor chưa hoàn tất initialization |
| `Uninitialized(offset)` | Object chưa khởi tạo, gắn với instruction `new` tại offset đó |
| `Top` | Thông tin top/unusable theo quy tắc verification type system, **không** phải Java class tên `Top` |

Đây là **verification types**, không đồng nghĩa Java Reflection types hoặc runtime values. Ví dụ source `boolean`/`byte`/`char`/`short` thường được biểu diễn qua `Integer` verification type ở nhiều vị trí bytecode.

### 5.2. Không phải một stack-map frame cho từng instruction

Compiler không cần viết một `StackMapTable` entry cho **mọi opcode**. Ý đồ của format là các frames xuất hiện tại điểm đầu các basic blocks cần có trạng thái được xác nhận; verifier áp dụng **type transitions** để suy trạng thái sau từng instruction từ trạng thái đầu block.

Các dạng tiết kiệm dung lượng:

- `same_frame`: state cục bộ giữ nguyên, operand stack rỗng ở điểm áp dụng.
- `same_locals_1_stack_item_frame`: local state giữ nguyên, stack có một item.
- `append_frame` / `chop_frame`: thêm/bớt thông tin về locals.
- `full_frame`: mô tả toàn bộ locals và stack state cần thiết.

Đó là **các dạng encoding của stack-map entries**, không phải lệnh bytecode chạy ở runtime như `iadd` hoặc `goto`.

### 5.3. Nếu không thấy `StackMapTable` thì sao?

Theo JVMS, class file từ version 50.0 trở lên **có thể không có attribute `StackMapTable` được ghi tường minh**; khi đó nó được hiểu tương đương một **implicit StackMapTable có số entries bằng 0**. Điều đó **không** đồng nghĩa verifier bị vô hiệu hóa.

Một phương thức tuyến tính không có join phức tạp có thể hợp lệ với ít hoặc không có entries. Ngược lại, một phương thức có branch targets cần frame phù hợp mà class file bỏ thiếu thông tin bắt buộc có thể bị từ chối bằng `VerifyError`, ví dụ thông điệp mang ý nghĩa “Expecting a stackmap frame at branch target”.

### 5.4. Việc đọc StackMapTable từ `javap`

```bash
javac --release 17 VerificationDemo.java
javap -c -v -p VerificationDemo
```

Trong output, tìm các khối:

```text
Code:
  stack=..., locals=..., args_size=...
  ...
StackMapTable: number_of_entries = ...
  frame_type = ...
```

`number_of_entries`, `frame_type` và offsets thực tế phụ thuộc class đã biên dịch và compiler; **không được đoán chúng từ số dòng Java source**.

## 6. Bài toán join: “an toàn trên MỌI đường đi”

Verifier phải kiểm tra những state đi vào một block nhất định thỏa **kiểu và độ sâu operand stack** mà block đòi hỏi. Nó không chỉ chạy thử một nhánh “có vẻ đúng”.

Mô hình hợp lệ:

```text
                 test
               /      \
path A: stack=[int]   path B: stack=[int]
               \      /
                 join
                 ireturn
```

Hai state đều cho phép `ireturn` với `int`.

Mô hình không hợp lệ:

```text
                 test
               /      \
path A: stack=[int]   path B: stack=[String ref]
               \      /
                 join
                 ireturn    // không bảo đảm có int
```

Nếu source Java thực tế viết:

```java
static int f(boolean flag) {
    int x;
    if (flag) {
        x = 10;
    } else {
        x = 20;
    }
    return x;
}
```

`javac` sẽ tạo bytecode hợp lệ và stack-map metadata thích hợp. Nó không sửa sai bằng cách “tự ép String thành int” nếu bytecode generator cố tạo đường vào không nhất quán.

### 6.1. Join của reference types không nhất thiết giữ đúng class cụ thể

Ví dụ một nhánh cung cấp reference kiểu `String`, nhánh kia kiểu `StringBuilder`; chương trình có thể hợp lệ nếu code ở điểm join chỉ đòi **một common supertype** (chẳng hạn `Object`), theo quy tắc assignability của verification type system.

```java
static int describe(boolean asText) {
    Object value;
    if (asText) {
        value = "x";
    } else {
        value = new StringBuilder("y");
    }
    return value.toString().length();
}
```

Đây là **phương thức minh họa**. Source khai báo `Object` nên compiler tạo bytecode mà join không cần biết object cụ thể là `String` hay `StringBuilder`, chỉ cần một reference type tương thích để gọi `Object.toString()`.

**Không kết luận** rằng mọi đường branch phải có cùng runtime class chính xác. Cái cần chứng minh là **assignability và frame type state hợp lệ theo instruction ở đích**.

## 7. Constructor và tham chiếu chưa khởi tạo

Một trong những vai trò quan trọng của verifier là ngăn code dùng object **trước khi instance initialization hoàn tất**.

Ví dụ source:

```java
Object obj = new Object();
```

Bytecode minh họa:

```text
new java/lang/Object       // tạo uninitialized reference
dup
invokespecial java/lang/Object.<init>:()V
astore_1                   // lưu reference đã được khởi tạo
```

Trong verifier type system, reference ngay sau `new` có dạng **`Uninitialized(offset)`** liên hệ với bytecode offset của instruction tạo ra nó. Khi `<init>` được gọi hợp lệ và hoàn tất, trạng thái được cập nhật để có thể sử dụng như reference đã khởi tạo.

Với constructor của chính class, local slot 0 có thể bắt đầu bằng **`UninitializedThis`** và phải tuân thủ các quy tắc liên quan tới lời gọi `this(...)` hoặc `super(...)`.

**Sai về bytecode:** lấy uninitialized reference và gọi bừa một instance method như `toString()`, hoặc trả reference đó theo cách trái quy tắc, trước khi constructor hợp lệ hoàn tất. Loại lỗi này không thể được “hợp pháp hóa” chỉ bằng cast hoặc sửa tên constant pool.

### 7.1. An toàn kiểu không có nghĩa không thể gặp `NullPointerException`

Verifier chứng minh **hình thức và kiểu** chứ không phải mọi điều kiện runtime. Nếu receiver type là reference hợp lệ nhưng **giá trị là null**, `invokevirtual` vẫn có thể ném `NullPointerException` khi chạy.

Tương tự, verifier không đảm bảo:

- Mảng luôn có index trong giới hạn (`ArrayIndexOutOfBoundsException`).
- Phép chia nguyên không bao giờ dùng 0 (`ArithmeticException`).
- Chương trình không hết heap (`OutOfMemoryError`).
- Hai thread không xảy ra race condition hoặc deadlock.
- Cast từ `Object` sang `String` luôn thành công ở runtime (`ClassCastException`).
- SQL query hoặc logic nghiệp vụ cho kết quả đúng.

**“Type-safe bytecode” không đồng nghĩa “error-free application”.**

## 8. Ví dụ Java 17+ chạy được: theo dõi branch, handler và category 2

Tạo file **`VerificationDemo.java`**:

```java
public class VerificationDemo {

    static int choose(boolean positive, int value) {
        int result;
        if (positive) {
            result = value + 1;
        } else {
            result = value - 1;
        }
        return result;
    }

    static int safeDivide(int numerator, int denominator) {
        try {
            return numerator / denominator;
        } catch (ArithmeticException ex) {
            return -1;
        }
    }

    static long plusOne(long value) {
        return value + 1L;
    }

    public static void main(String[] args) {
        System.out.println("choose true = " + choose(true, 4));
        System.out.println("choose false = " + choose(false, 4));
        System.out.println("divide = " + safeDivide(10, 0));
        System.out.println("long = " + plusOne(41L));
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac --release 17 VerificationDemo.java
java VerificationDemo
```

**Stdout xác định:**

```text
choose true = 5
choose false = 3
divide = -1
long = 42
```

Tại `choose(true,4)`, `result=4+1=5`; tại `choose(false,4)`, `result=4-1=3`. `safeDivide(10,0)` phát sinh `ArithmeticException` tại `idiv` và catch trả `-1`; `plusOne(41L)` dùng arithmetic category-2 và trả `42L`.

### 8.1. Những gì bytecode thể hiện

```bash
javap -c -p VerificationDemo
javap -v -p VerificationDemo
```

Khi đọc **`choose(boolean,int)`**, dự kiến thấy logic như:

```text
iload_0                 // boolean được xử lý theo int-family
ifeq ELSE
  iload_1
  iconst_1
  iadd
  istore_2
  goto JOIN
ELSE:
  iload_1
  iconst_1
  isub
  istore_2
JOIN:
  iload_2
  ireturn
```

Đây là **sơ đồ opcode không kèm offset** để giải thích, không phải nguyên văn output `javap`; trong output thật nhãn `ELSE`/`JOIN` là số bytecode offsets. Cả hai nhánh đều đưa `int` vào local 2 trước `iload_2`.

Ở **`safeDivide(int,int)`**, tìm `idiv`, một **Exception table** với `Class java/lang/ArithmeticException` và đoạn handler nhận exception reference qua `astore` rồi `ireturn` giá trị `-1`. Khi vào handler, operand stack đã được reset thành **một reference đến ngoại lệ** — khác với flow bình thường trả một `int`.

Ở **`plusOne(long)`**, thường thấy:

```text
lload_0
lconst_1
ladd
lreturn
```

`long value` là một giá trị category 2 tại local slots 0–1. `ladd` và `lreturn` đòi state thuộc loại `long` thích hợp; không thể thay `lreturn` bằng `ireturn` rồi mong verifier tự chuyển kiểu.

### 8.2. Quan sát `StackMapTable` của các nhánh

Dùng:

```bash
javap -v -p VerificationDemo
```

Tìm `StackMapTable` trong phần của `choose` và `safeDivide`. Ở branch target và handler, các stack-map entries giúp mô tả trạng thái types:

- Ở điểm vào nhánh `else`: tham số `positive` và `value` có kiểu thích hợp, stack rỗng theo cách bố trí bytecode thường dùng.
- Ở `JOIN`: local `result` là `int` trên mọi đường hợp lệ, có thể được `iload` và `ireturn`.
- Ở handler `ArithmeticException`: operand stack tại handler có một exception reference, không phải hai toán hạng `int` còn sót từ `idiv`.

Số entry, dạng frame và offset cụ thể do compiler quyết định. `javap -v` thể hiện **class metadata**, không phải JVM đang tự ghi lại live stack của một lời gọi đã chạy.

## 9. Exception handler là một cạnh control-flow đặc biệt

`exception_table` nằm trong `Code` attribute, có các trường:

```text
start_pc, end_pc, handler_pc, catch_type
```

- Handler bảo vệ vùng **`[start_pc, end_pc)`** (start-inclusive, end-exclusive).
- `handler_pc` trỏ tới instruction đầu handler.
- `catch_type` trỏ tới exception class phù hợp hoặc bằng **0** để match mọi Throwable.
- Các entries được xem theo **thứ tự**; entry đầu tiên phù hợp với pc/type được dùng.

Ở điểm vào handler, JVM đặt operand stack thành **một exception object reference**, xóa những operands cũ trong frame đó. Đây là nguyên tắc quan trọng cho kiểm tra type state tại handler.

Ví dụ lỗi thiết kế bytecode:

```text
TRY:                  // đẩy hai int trước phép tính
  iload_0
  iload_1
  idiv                // có thể ném ArithmeticException

HANDLER:
  iadd                // SAI: không có sẵn hai int ở handler entry
```

Khi chuyển vào handler, stack là `[exceptionRef]`, vì vậy `iadd` ở đầu handler không có hai `int` tương thích. Một handler hợp lệ thường bắt đầu với `astore` để lấy reference, sau đó tự tính giá trị cần trả.

## 10. Các lỗi Verification thường gặp với bytecode generator

| Lỗi bytecode | Vì sao không an toàn/hợp lệ? |
| --- | --- |
| `iadd` chỉ có một `int` trên stack | Stack underflow / thiếu toán hạng |
| `iadd` gặp `String` reference | Sai operand types |
| `iload_2` khi local slot 2 không chắc có `int` trên mọi đường | Sử dụng local chưa có type state phù hợp |
| Branch vào byte operand của instruction khác | Target không phải ranh giới opcode |
| Hai đường join có stack heights không tương thích | Không có trạng thái đầu block hợp lệ |
| `ireturn` từ method trả `long` | Return opcode/type trái method descriptor |
| `invokevirtual` với receiver là `int` | Receiver phải là reference tương thích |
| Gọi method với số lượng/kiểu arguments khác descriptor | Operand stack không cung cấp đúng types |
| `lload` từ cặp slots đã bị ghi đè | Không còn giá trị `long` hợp lệ tại index |
| Dùng uninitialized object reference như object đã khởi tạo | Vi phạm quy tắc initialization của `new`/`<init>` |
| Handler bắt đầu bằng opcode yêu cầu hai `int` | Handler entry stack có một exception reference |
| Sửa branch offsets nhưng không cập nhật stack maps | StackMapTable không còn nhất quán với CFG mới |

**Đừng thử tạo các lỗi này bằng Java source bình thường:** `javac` chủ động tạo code hợp lệ hoặc từ chối source không đúng kiểu. Những lỗi này thường có ý nghĩa khi xem bytecode đã bị sửa, code do compiler/transformer khác tạo hoặc class file bị hỏng.

## 11. `VerifyError`, `ClassFormatError` và các lỗi liên kết khác

| Loại lỗi | Bối cảnh thường gặp | Cần điều tra |
| --- | --- | --- |
| **`ClassFormatError`** | Dữ liệu class file không đáp ứng định dạng nhị phân/attributes | Artifact có bị hỏng, constant pool hoặc lengths sai? |
| **`UnsupportedClassVersionError`** | Class-file version/preview không được runtime hỗ trợ | `java -version`, major/minor, flags preview |
| **`VerifyError`** | Bytecode hoặc type state không thỏa verification rules | Opcodes, branch targets, StackMapTable, exception table |
| **`NoClassDefFoundError`** | Class cần dùng không được định nghĩa/tìm thấy trong hoàn cảnh tương ứng | Classpath/module path, dependency, init error trước đó |
| **`NoSuchMethodError` / `NoSuchFieldError`** | Symbolic reference không tìm thấy member mong đợi khi resolution | Version JAR và binary compatibility |
| **`IllegalAccessError`** | Access check/linkage trái quyền truy cập runtime | Visibility, modules, nestmate, phiên bản artifact |
| **`ExceptionInInitializerError`** | Static initialization `<clinit>` thất bại theo những điều kiện tương ứng | Code khởi tạo static và nguyên nhân bên trong |
| **`ClassCastException` / `NullPointerException`** | Type cast hoặc reference value sai khi đang thực thi | Dữ liệu/runtime logic; không tự suy verifier thất bại |

Bảng trên là **định hướng chẩn đoán**; lỗi cụ thể còn phụ thuộc class loader, thời điểm phát hiện và implementation. `VerifyError`/`ClassFormatError` thuộc nhóm lỗi liên quan tính hợp lệ class file, nhưng không phải mọi lỗi chương trình đều do verifier.

### 11.1. Một thông báo `VerifyError` không có nghĩa JDK “không hỗ trợ Java source”

Nếu code Java 17 có thể biên dịch bằng `javac --release 17` nhưng ở runtime thấy `VerifyError` trong một lớp của thư viện, hãy kiểm tra:

1. Runtime thực sự đang nạp **file `.class` nào** (có bản copy trong nhiều JAR không?).
2. Lớp đó có bị Java agent/instrumentation transformer chỉnh lại không?
3. Transformer có cập nhật **branch offsets, `StackMapTable` và exception handler ranges** không?
4. `java -version` và build target có đúng không?
5. Lỗi chỉ xảy ra sau khi nâng JDK hay đổi phiên bản framework/bytecode library?

Không nên mặc định lỗi do số lượng heap nhỏ hoặc dùng `--enable-preview` để “né” verification. Bật preview không hợp pháp hóa bytecode sai.

## 12. Giới hạn của verifier: điều nó KHÔNG chứng minh

Verifier được thiết kế để bảo đảm **các ràng buộc type và structure của JVM**, nhưng không phải công cụ chứng minh chương trình đúng mọi mặt.

| Điều verifier có thể bảo vệ ở mức tĩnh | Điều vẫn phải xử lý tại runtime hoặc ở ứng dụng |
| --- | --- |
| Instruction nhận operand có loại hợp lệ | `NullPointerException` khi reference hợp lệ nhưng value null |
| Một local được đọc với type phù hợp | Logic nghiệp vụ tính sai dù types đúng |
| Branch target hợp lệ và frame join an toàn | Deadlock hoặc race condition giữa threads |
| Return opcode phù hợp method descriptor | Dữ liệu đầu vào vượt giới hạn business |
| Quy tắc object initialization hợp lệ | `ClassCastException` khi downcast thực tế thất bại |
| Operand stack không bị mất cân bằng sai quy tắc | `OutOfMemoryError` khi không đủ memory |
| Basic class-file structure đủ hợp lệ để dùng | SQL injection, phân quyền ứng dụng hoặc lỗ hổng logic |

Ví dụ `checkcast java/lang/String` là opcode có **type safety hợp lệ** khi đầu vào là object reference, nhưng nếu object thật là `Integer` thì **runtime** có thể ném `ClassCastException`. Verifier không được yêu cầu đoán mọi object thực sẽ có dynamic type gì.

Tương tự, array stores còn có runtime checks theo đặc tả với những trường hợp tương ứng; bytecode verification không thay thế mọi kiểm tra động.

## 13. Làm việc với ASM, Byte Buddy và Java agents

Khi viết bytecode generator hoặc transformer, vấn đề thực tế lớn nhất là **giữ tương thích giữa control-flow graph và stack-map frames**. Một phép chỉnh tưởng như đơn giản — chèn logging vào đầu method, thêm `try/catch`, thay opcode, mở rộng `switch` — có thể:

- Dịch bytecode offsets và branch targets.
- Đổi vị trí basic-block entry.
- Đổi trạng thái operand stack hoặc local-variable types tại điểm join.
- Làm `max_stack` hoặc `max_locals` không đủ.
- Làm exception table hoặc frame offsets không phù hợp code mới.
- Đụng tới quy tắc uninitialized reference quanh `new`/`<init>`.

Checklist để sửa an toàn:

1. Xác định class-file version và method descriptor trước khi biến đổi.
2. Duy trì type state chính xác của operand stack/locals khi chèn instruction.
3. Xây lại/cập nhật jump labels, try/catch ranges và stack-map entries theo tool được hỗ trợ.
4. Đảm bảo `max_stack` và `max_locals` đủ cho mọi đường đi có thể thực thi.
5. Không đụng trái quy tắc uninitialized object hoặc monitor.
6. Chạy tests trên đúng JDK target, bao gồm các nhánh và exception paths.
7. Dùng `javap -c -v` quan sát class file **sau biến đổi**, không chỉ bản `javac` ban đầu.

Một số thư viện như ASM có tính năng tự tính toán stack-map frames/max stack khi cấu hình phù hợp, nhưng phải tuân thủ hướng dẫn phiên bản cụ thể; không có lựa chọn “tự fix mọi bytecode sai” cho mọi biến đổi.

**Không nên** tắt verifier như một cách khắc phục sự cố production. Đây là một phần quan trọng trong các đảm bảo an toàn của mô hình JVM; thay vào đó hãy sửa class-file/transformer/toolchain không hợp lệ.

## 14. Ba bước đọc một lỗi thật

### Tình huống A: `VerifyError: Bad type on operand stack`

Hướng chẩn đoán:

- Tìm class và method mà lỗi nêu, xác định instruction/offset.
- Đọc `javap -c -v -p ClassName` của artifact đang được nạp.
- Kiểm tra opcode tại offset, `StackMapTable` type state và các branch dẫn tới đó.
- Đối chiếu method descriptor và type of receiver/arguments.

Không kết luận “JVM runtime data chứa sai kiểu vì race”; thường cần điều tra **bytecode/type state**.

### Tình huống B: `VerifyError: Expecting a stackmap frame at branch target`

Đặc biệt hay gặp khi tool chèn `goto` hoặc thêm một basic block mà không cập nhật `StackMapTable`. Cần kiểm tra offsets/frames của class-file version đang dùng. Thêm một frame tại vị trí dự đoán mà không phân tích state có thể tiếp tục lỗi; nên dùng generator có khả năng tính frame hợp lệ.

### Tình huống C: `ClassFormatError` vì malformed attribute

Lỗi có thể bắt nguồn từ công cụ đóng gói/transformer ghi sai attribute length hay constant pool. Kiểm tra artifact bytes đầu vào và chương trình sinh class, không chỉ `java -version`. Nếu class đã bị truncate, không thể xử lý bằng `catch` trong `main` vì lỗi có thể xuất hiện trước lúc method cần chạy được nạp.

## 15. Các nhầm lẫn hay gặp

1. **“`javac` biên dịch được là JVM không cần verification.”** Sai. JVM có thể nhận bytecode từ nhiều nguồn và phải thực hiện kiểm tra theo hợp đồng class-file/linking.
2. **“Verification và initialization là một.”** Sai. Verification kiểm tra hợp lệ, initialization chạy code `<clinit>` theo điều kiện riêng.
3. **“JVM verifier chạy mọi nhánh với dữ liệu mẫu.”** Sai. Nó phân tích cấu trúc và **trạng thái kiểu trừu tượng**, không chạy test cases.
4. **“Thấy `StackMapTable` thì đó là live Java stack trace.”** Sai. Đây là metadata phục vụ type checking tại các bytecode offsets.
5. **“Mỗi instruction bắt buộc phải có stack-map frame riêng.”** Sai. Frames thể hiện các điểm trạng thái cần thiết; nhiều instruction được kiểm tra bằng type transitions.
6. **“Không có `StackMapTable` tường minh thì chắc chắn lớp không được verify.”** Sai. Với version 50+ có quy tắc implicit zero-entry stack map.
7. **“Join chỉ cần cùng chiều dài operand stack, bất kể types.”** Sai. Types còn phải tương thích.
8. **“Mọi reference trên hai nhánh phải cùng runtime class.”** Sai. Assignability có thể cho phép common supertype thích hợp.
9. **“`iadd` có thể cộng hai `Object` vì chúng có địa chỉ máy.”** Sai. Opcode `iadd` yêu cầu `int`.
10. **“`long` dùng một local slot như `int`.”** Sai. `long`/`double` dùng hai local slots, là category 2.
11. **“`new` tạo object có thể gọi instance method bất kỳ ngay trước `<init>`.”** Sai. Uninitialized references bị ràng buộc.
12. **“`VerifyError` đồng nghĩa Java source syntax sai.”** Sai. Compiler có thể đã thành công, còn bytecode đã bị transform/malformed.
13. **“`--enable-preview` làm JVM bỏ qua kiểm tra type safety.”** Sai. Cờ chỉ bật preview features được hỗ trợ đúng release.
14. **“Verifier đảm bảo không bao giờ có `NullPointerException`.”** Sai. Null là điều kiện runtime.
15. **“Verifier sửa bytecode hỏng trong lúc load.”** Sai. Nếu bytecode không thỏa ràng buộc, class bị từ chối; không có cam kết JVM tự chỉnh source/offsets.
16. **“Lỗi JIT compilation là `VerifyError`.”** Không nhất thiết. Verification là một bước ngữ nghĩa riêng với JIT optimization/runtime execution.
17. **“Một lỗi metadata `StackMapTable` có thể giải bằng tăng `-Xmx`.”** Sai. Đây là vấn đề class-file/type-state, không phải thiếu heap.

## 16. Bài tập tự kiểm tra (có đáp án)

**Câu 1.** Với `static int f(boolean b, int n)`, hai nhánh đều gán `int result` rồi return. Vì sao join hợp lệ?

**Đáp án:** Tại join, local slot cho `result` có type `int` hợp lệ trên cả hai đường. `iload` và `ireturn` vì vậy có operand type đúng.

**Câu 2.** Nếu nhánh A để stack `[int]` và nhánh B để stack `[String ref]` rồi đều chạy `iadd`, vấn đề gì?

**Đáp án:** `iadd` yêu cầu **hai `int`**, trong khi cả hai đường đều không đảm bảo đủ kiểu/số toán hạng. State tại join và điều kiện instruction không hợp lệ; bytecode có thể bị từ chối bằng `VerifyError`.

**Câu 3.** `StackMapTable` dùng cho debug tên local variables hay phục vụ verifier?

**Đáp án:** Phục vụ **verification by type checking**, chứa thông tin type state tại những bytecode offsets; không thay thế `LocalVariableTable` cho debug tên biến.

**Câu 4.** Một class 61.0 không có `StackMapTable` hiển thị trong `javap`. Có chắc JVM bỏ qua verification không?

**Đáp án:** Không. JVM hiểu implicit zero-entry stack map khi thiếu attribute; phương thức chỉ hợp lệ nếu đáp ứng những điều kiện verification tương ứng.

**Câu 5.** Vì sao một reference từ `new` chưa được xem như object đã initialized?

**Đáp án:** Verifier theo dõi `Uninitialized(offset)` / `UninitializedThis` và yêu cầu constructor initialization qua `<init>` hợp lệ trước những cách sử dụng bị ràng buộc.

**Câu 6.** Khi exception handler bắt đầu, operand stack chứa những gì?

**Đáp án:** **Một exception reference**, không phải các toán hạng cũ của lệnh đã phát sinh exception.

**Câu 7.** Verifier đã chấp nhận method có `idiv`. Có bảo đảm divisor khác 0 ở runtime?

**Đáp án:** Không. Verifier xác minh kiểu `int` của operands; chia 0 vẫn có thể ném `ArithmeticException` trong lúc chạy.

**Câu 8.** `ClassFormatError` vì tệp class bị truncate và `VerifyError` vì thiếu stack-map frame là một loại lỗi giống nhau hoàn toàn?

**Đáp án:** Không. Một lỗi thuộc **format/data structure**, lỗi kia thuộc **bytecode verification/type-state**; thông điệp cụ thể và bước điều tra khác nhau.

**Câu 9.** Một Java agent vừa thêm `goto`, method không còn verify. Điều nên kiểm tra đầu tiên?

**Đáp án:** Instruction offsets, branch targets, basic-block entries, `StackMapTable` và type states, `max_stack`/`max_locals`; sửa transformer, không tắt verifier.

## 17. Tổng kết luồng phân tích

```text
Class bytes
    │
    ▼
Format check
  ├─ magic, version, constant pool, length, attributes
  └─ không hợp lệ → class bị từ chối
    │ hợp lệ
    ▼
Bytecode / control-flow structure
  ├─ opcode, operands, branch/handler targets
  └─ sai → class bị từ chối
    │ hợp lệ
    ▼
Type checking
  ├─ method descriptor → initial type state
  ├─ StackMapTable → state tại basic block entries
  ├─ instructions → state transitions
  ├─ joins → stack height và type assignability
  ├─ constructors → uninitialized reference constraints
  └─ sai → VerifyError
    │
    ▼
Code có thể được JVM dùng theo các quy tắc loading/linking
    │
    ▼
Runtime checks vẫn tồn tại (null, bounds, casts, memory, ...)
```

**Ghi nhớ:** Verification không chỉ hỏi “class file đọc được không”, mà còn phải xác định **control flow và type state an toàn trên mọi đường đi tới instruction**. `StackMapTable` cung cấp metadata trạng thái kiểu để JVM kiểm tra hiệu quả; `VerifyError` cảnh báo lỗi về code/type constraints, trong khi `ClassFormatError` thiên về cấu trúc class file. Sau khi verification thành công, ứng dụng **vẫn có thể** gặp runtime exception hoặc bug nghiệp vụ. Đó là ranh giới giữa **đảm bảo của VM** và **trách nhiệm của code ứng dụng**.

### Tài liệu tham khảo

- [JVMS Java SE 17 — ClassFile structure (4.1)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.1).
- [JVMS Java SE 17 — `StackMapTable` attribute (4.7.4)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.4).
- [JVMS Java SE 17 — Format Checking (4.8)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.8).
- [JVMS Java SE 17 — Constraints on JVM Code (4.9)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.9).
- [JVMS Java SE 17 — Verification of class Files (4.10)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.10).
- [JVMS Java SE 17 — Linking/Verification (5.4.1)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.1).
- [Oracle Java SE 17 — `javap` command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="linking-and-initialization-003">Giới hạn của bytecode verifier so với tính đúng đắn của ứng dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn của bytecode verifier so với tính đúng đắn của ứng dụng

## 1. Vấn đề trung tâm: “bytecode hợp lệ” không đồng nghĩa “chương trình đúng”

Một tệp `.class` có thể vượt qua bước **xác minh bytecode** (*bytecode verification*) của JVM nhưng chương trình vẫn:

- Ném `NullPointerException` vì sử dụng tham chiếu `null`.
- Chia một số nguyên cho 0 và ném `ArithmeticException`.
- Truy cập phần tử mảng vượt phạm vi và ném `ArrayIndexOutOfBoundsException`.
- Có `race condition`, `deadlock`, rò rỉ tài nguyên hoặc hết bộ nhớ.
- Tính sai tiền, áp dụng sai quy tắc nghiệp vụ hoặc trả kết quả sai mà **không phát sinh ngoại lệ**.
- Xử lý dữ liệu đầu vào không an toàn và tạo ra lỗ hổng bảo mật ở cấp ứng dụng.

Tất cả những vấn đề trên **có thể xảy ra trong bytecode hợp lệ**. Verifier giải quyết một câu hỏi hẹp hơn:

> **Chuỗi lệnh bytecode và trạng thái kiểu trừu tượng của nó có tuân thủ các ràng buộc an toàn kiểu/cấu trúc cần thiết để JVM thực thi theo đặc tả hay không?**

Trong khi đó, tính đúng đắn của ứng dụng đặt những câu hỏi rộng hơn: “Kết quả có đúng yêu cầu không?”, “Đầu vào xấu được xử lý đúng không?”, “Dữ liệu có nhất quán giữa các thread không?”, “Tài nguyên có được giải phóng không?”, “Có đáp ứng yêu cầu hiệu năng, bảo mật, độ tin cậy không?”.

```text
Mã nguồn Java / ngôn ngữ khác nhắm JVM
                   |
                   v
          Compiler / bytecode tool
                   |
                   v
                 .class
                   |
                   v
       JVM loading + linking + checks
          ├── Kiểm tra định dạng .class
          ├── Verification: cấu trúc/kiểu bytecode
          ├── Preparation
          └── Resolution khi phù hợp
                   |
                   v
          Bytecode đủ điều kiện chạy
                   |
                   v
            Thực thi ứng dụng
          ├── Runtime checks và exceptions
          ├── Logic nghiệp vụ
          ├── Đồng thời, I/O, tài nguyên
          └── Các đặc tính bảo mật/vận hành
```

Sơ đồ là **mô hình khái quát**, không khẳng định mọi resolution đều xảy ra trước khi lệnh đầu tiên chạy, hoặc JVM thực hiện một lượt kiểm tra không thể trì hoãn ở đúng cùng thời điểm cho mọi class. Phải phân biệt trách nhiệm của verifier với **class-format checking, resolution, initialization, runtime checks** và kiểm thử ứng dụng.

## 2. Vì sao JVM phải xác minh class file?

Compiler Java thông thường giúp ngăn nhiều lỗi kiểu **ở mức mã nguồn**, nhưng JVM không chỉ nhận bytecode do `javac` tạo ra. Class file có thể xuất phát từ:

- Compiler Kotlin, Scala hoặc ngôn ngữ khác trên JVM.
- Code generator, framework sinh class động.
- Build-time bytecode transformer, obfuscator.
- Java agent instrument class trong quá trình nạp.
- Tệp `.class` bị lỗi, không tương thích hoặc cố tình chế tạo bytecode sai.

Do vậy JVM **không thể tin tuyệt đối** rằng “có một tệp .class đọc được” nghĩa là opcode, locals, operand stack và control flow đều hợp lệ. Bộ xác minh là một phần quan trọng trong cơ chế thực thi an toàn kiểu của JVM.

Điều quan trọng là **bytecode verifier không phải code reviewer, unit-test runner hoặc bộ chứng minh chương trình đúng về mặt toán học**.

### 2.1. Verifier làm việc trên kiểu trừu tượng, không phải mọi giá trị thực tế

Ví dụ:

```java
static int divide(int numerator, int denominator) {
    return numerator / denominator;
}
```

Bytecode do `javac` thường có dạng:

```text
iload_0
iload_1
idiv
ireturn
```

Verifier có thể chứng minh theo quy tắc instruction rằng:

1. `iload_0` và `iload_1` nạp những giá trị thích hợp với kiểu `int`.
2. `idiv` nhận hai operand `int` và tạo một kết quả `int` khi hoàn tất bình thường.
3. `ireturn` trả giá trị `int`, phù hợp method descriptor `(II)I`.

Nhưng verifier **không chứng minh rằng `denominator != 0`** cho mọi lần gọi. `divide(8, 2)` trả 4; `divide(8, 0)` ném `ArithmeticException`. Cả hai đều là những cách thực thi hợp lệ của **một phương thức đã qua verification**.

### 2.2. “Verifier bảo đảm chương trình không bị crash” là kết luận sai

Ngay cả bytecode hợp lệ vẫn có thể ném `OutOfMemoryError`, `StackOverflowError`, `ArithmeticException` hoặc một lỗi khác được phép bởi JVM/Java API. Một số lỗi có thể chấm dứt thread hoặc tiến trình nếu không được xử lý.

Bộ xác minh góp phần ngăn **vi phạm ràng buộc bytecode** như dùng giá trị kiểu `int` làm reference cho `invokevirtual`, chứ không đảm bảo chương trình không bao giờ gặp exception.

## 3. JVM kiểm tra gì trong bước verification?

Theo **Java Virtual Machine Specification — JVMS `4.10**, xác minh class file/bytecode bao gồm những quy tắc về cấu trúc, kiểu và các bước chuyển trạng thái trừu tượng. Một số ràng buộc đặc biệt quan trọng khi học:

| Vùng kiểm tra | Ví dụ ràng buộc | Verifier có thể phát hiện gì? |
| --- | --- | --- |
| **Operand stack** | `iadd` cần hai `int`, `areturn` cần giá trị tham chiếu tương thích | Dùng sai loại giá trị hoặc trạng thái stack không hợp lệ |
| **Local variables** | `iload` đọc slot có kiểu xác minh phù hợp | Đọc local với kiểu sai tại một control-flow path |
| **Control flow** | Branch phải tới instruction hợp lệ với trạng thái kiểu nhất quán | Nhảy vào vị trí không hợp lệ hoặc trạng thái ở target không tương thích |
| **Method invocation** | Receiver/arguments/return phải tương thích với method descriptor | Truyền số lượng/loại giá trị không hợp lệ ở cấp bytecode |
| **Method return** | `ireturn` phải phù hợp kiểu trả về của phương thức | Trả kiểu sai so với descriptor |
| **Object initialization** | Reference sinh từ `new` phải được sử dụng đúng quy tắc trước/sau `<init>` | Dùng uninitialized reference không hợp lệ |
| **Stack map frames** | Trạng thái ở offset cần được các nhánh đi tới đáp ứng | StackMapTable thiếu/sai frame cần thiết hoặc khai báo kiểu không nhất quán |
| **Exception handlers** | Handler phải có đầu vào theo quy tắc khi exception được chuyển tới | Trạng thái kiểu không phù hợp ở handler |

Các kiểm tra **định dạng class file** và **kiểm tra quyền truy cập/liên kết trong resolution** có trách nhiệm riêng, dù tất cả đều tham gia quá trình chấp nhận class để thực thi. Không nên quy mọi `ClassFormatError`, `IllegalAccessError` hoặc `NoClassDefFoundError` thành “bytecode verifier bắt lỗi nghiệp vụ”.

### 3.1. Operand stack phải có đúng số lượng và loại giá trị

JVM dùng operand stack để truyền toán hạng cho các instruction. Ví dụ `iadd` lấy hai giá trị `int` ở đỉnh stack:

```text
iconst_2      stack: [] → [2]
iconst_3      stack: [2] → [2,3]
iadd          stack: [2,3] → [5]
ireturn       trả 5 cho caller
```

Một chuỗi giả định sai:

```text
iconst_2
iadd          // chỉ có một int để lấy, không đủ hai operand
ireturn
```

Dù các mnemonic trông hợp lệ từng cái, **luồng stack** sai và không được verifier chấp nhận như một phương thức hợp lệ.

Một chuỗi sai kiểu khác:

```text
iconst_1
invokevirtual #method   // giả định gọi instance method cần object receiver
```

Nếu không có reference receiver phù hợp và các operand đúng method descriptor, verifier không thể coi giá trị `int` trên stack là receiver hợp lệ.

**Hai ví dụ trên là bytecode giả định CỐ Ý SAI**, không phải chương trình Java độc lập để copy và chạy. Không nên thử tạo một class file như vậy trong ứng dụng production.

### 3.2. Kiểm tra nhánh và trạng thái tại điểm hội tụ

Với bytecode có hai nhánh:

```text
              [điều kiện]
               /      \
          Nhánh A     Nhánh B
               \      /
              [join point]
                   |
               instruction
```

Tại điểm hội tụ, verifier yêu cầu trạng thái kiểu của **local variables** và **operand stack** mà các đường đi hợp lệ đưa tới phải phù hợp với trạng thái được yêu cầu.

Ví dụ nếu một nhánh tạo `int` còn nhánh khác tạo reference và lệnh tiếp theo định đọc một reference theo quy tắc opcode, không thể coi hai trạng thái tùy ý là cùng một kiểu chỉ vì đều chiếm chỗ trong stack.

Class file hiện đại dùng **`StackMapTable`** để mã hóa các verification type states tại những bytecode offsets quan trọng. Verifier kiểm tra các frame này có tương thích với opcode và control flow thật hay không; **không tin mù quáng vào metadata**.

### 3.3. Constructor và uninitialized reference

Mã Java:

```java
Object value = new Object();
```

Một dạng bytecode phổ biến:

```text
new           #class
dup
invokespecial #constructor  // Object.<init>:()V
astore_1
```

Sau `new`, verifier có một trạng thái reference **chưa được khởi tạo hoàn chỉnh**. Nó áp dụng những quy tắc về gọi `<init>` và sử dụng object để không cho bytecode tùy ý coi reference này là instance đã hoàn tất constructor.

Điều này **không** bảo đảm constructor thiết lập giá trị nghiệp vụ đúng. Constructor vẫn có thể đặt field sai, ném exception hoặc gọi logic có bug mà bytecode hoàn toàn hợp lệ.

### 3.4. Verifier không biết mọi giá trị runtime

Một local slot được chứng minh có **reference type** không nhất thiết chứa reference khác `null`. Một giá trị đã được chứng minh có kiểu `int` không nhất thiết nằm trong miền nghiệp vụ mà ứng dụng muốn, ví dụ `0 < age <= 120`.

Cần phân biệt:

- **Kiểu xác minh hợp lệ:** một giá trị thuộc loại `int` hoặc reference phù hợp.
- **Điều kiện giá trị đúng:** số chia không phải 0; index nằm trong phạm vi; chuỗi không null; tuổi nằm trong khoảng cho phép; số tiền không âm.

Verifier chủ yếu giải quyết tầng thứ nhất. Tầng thứ hai cần những kiểm tra và quy tắc bổ sung.

## 4. Năm cấp “đúng” cần phân biệt

Một class có thể vượt qua một số cấp nhưng vẫn thất bại ở cấp khác.

| Cấp độ | Câu hỏi | Công cụ/cơ chế chính |
| --- | --- | --- |
| **1. Mã nguồn hợp lệ** | Java source đúng cú pháp và đúng kiểu tĩnh? | `javac`, compiler, annotation processor |
| **2. Class file hợp lệ** | Cấu trúc định dạng, constant pool và các thuộc tính có hợp lệ? | Class-file format checks của JVM và công cụ phân tích class |
| **3. Bytecode type-safe** | Opcode, stack/locals, nhánh, return, trạng thái khởi tạo có hợp lệ? | **Bytecode verifier** |
| **4. Thực thi hợp lệ khi chạy** | Class resolution có thành công, các kiểm tra runtime có được xử lý? | JVM runtime, exception handling, linkage checks |
| **5. Ứng dụng đúng yêu cầu** | Logic, dữ liệu, đồng thời, bảo mật, hiệu năng, độ tin cậy có đạt kỳ vọng? | Unit/integration/property tests, static analysis, code review, monitoring |

**Không xem bảng như một tuyến tính tuyệt đối:** JVM có thể trì hoãn một số hoạt động resolution/linking tới thời điểm sử dụng; một lỗi bảo mật nghiệp vụ có thể tồn tại ngay cả khi mọi lớp đã load và chạy. Bảng chỉ tách **trách nhiệm**.

**Sai lầm:** “`javac` biên dịch thành công + `java` chạy được = phần mềm đúng”. Hai điều kiện đó không đủ chứng minh tính đúng đắn. Ngay cả chạy thành công với một bộ input cũng không chứng minh các đầu vào/điều kiện khác.

## 5. Ví dụ Java 17+ độc lập: verified bytecode vẫn có nhiều loại lỗi

Tạo tệp **`VerifierLimitsDemo.java`**:

```java
public final class VerifierLimitsDemo {
    private VerifierLimitsDemo() {
    }

    static int divide(int numerator, int denominator) {
        return numerator / denominator;
    }

    static int length(String text) {
        return text.length();
    }

    static int element(int[] numbers, int index) {
        return numbers[index];
    }

    static int businessTotalWithBug(int price, int quantity) {
        return price + quantity; // CỐ Ý SAI: phải nhân
    }

    static String classify(int value) {
        return value >= 0 ? "nonnegative" : "negative";
    }

    public static void main(String[] args) {
        System.out.println("valid division: " + divide(8, 2));

        try {
            divide(8, 0);
        } catch (ArithmeticException ex) {
            System.out.println("division by zero: "
                    + ex.getClass().getSimpleName());
        }

        try {
            length(null);
        } catch (NullPointerException ex) {
            System.out.println("null dereference: "
                    + ex.getClass().getSimpleName());
        }

        try {
            element(new int[] {5, 7}, 2);
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.out.println("invalid index: "
                    + ex.getClass().getSimpleName());
        }

        Object value = "text";
        try {
            Integer number = (Integer) value;
            System.out.println(number);
        } catch (ClassCastException ex) {
            System.out.println("bad cast: "
                    + ex.getClass().getSimpleName());
        }

        Object[] objects = new String[1];
        try {
            objects[0] = Integer.valueOf(3);
        } catch (ArrayStoreException ex) {
            System.out.println("array store: "
                    + ex.getClass().getSimpleName());
        }

        System.out.println("business total (bug): "
                + businessTotalWithBug(10, 3));
        System.out.println("business total (expected): " + (10 * 3));
        System.out.println("classification: " + classify(-1));
    }
}
```

Biên dịch và chạy bằng **JDK 17 trở lên**:

```bash
javac --release 17 VerifierLimitsDemo.java
java VerifierLimitsDemo
```

**Stdout chính xác:**

```text
valid division: 4
division by zero: ArithmeticException
null dereference: NullPointerException
invalid index: ArrayIndexOutOfBoundsException
bad cast: ClassCastException
array store: ArrayStoreException
business total (bug): 13
business total (expected): 30
classification: negative
```

Ví dụ này không dựa vào thông báo exception chi tiết hay stack trace (những thứ có thể khác nhau tùy JVM); nó chỉ in **tên class exception** đã bắt, nên đầu ra xác định.

### 5.1. Vì sao chương trình vẫn vượt qua verification?

Mỗi instruction thuộc những lệnh hợp lệ với trạng thái kiểu phù hợp:

- `idiv` lấy hai `int`, dù mẫu số có thể bằng 0.
- `invokevirtual String.length` nhận một giá trị kiểu reference `String`, dù giá trị runtime có thể là null.
- `iaload` nhận array reference và index kiểu `int`, dù index có thể ngoài phạm vi.
- `checkcast` nhận reference và thực hiện kiểm tra tương thích runtime.
- `aastore` ghi một reference qua biến `Object[]`, nhưng array object thực là `String[]`; runtime thực hiện array store check.
- `iadd` trong tính tiền trả `int` hoàn toàn hợp lệ về kiểu, dù công thức nghiệp vụ đáng ra phải là phép nhân.

**Đây không phải lỗi verifier đã “bỏ sót” theo nghĩa không tuân thủ nhiệm vụ**. Chúng là những điều kiện giá trị/runtime/logic nằm ngoài phạm vi chứng minh kiểu của verifier.

### 5.2. Những runtime check khác nhau

| Tình huống | Runtime check/hành vi | Exception hoặc kết quả |
| --- | --- | --- |
| `8 / 0` với phép chia int | Kiểm tra chia cho 0 trong `idiv` | `ArithmeticException` |
| `text.length()` khi `text == null` | Receiver null check | `NullPointerException` |
| `numbers[2]` khi mảng length 2 | Bounds check | `ArrayIndexOutOfBoundsException` |
| `(Integer) "text"` (qua `Object`) | Kiểm tra `checkcast` | `ClassCastException` |
| Ghi `Integer` vào mảng thực `String[]` | Array store check | `ArrayStoreException` |
| `price + quantity` thay vì `price * quantity` | **Không có runtime check chung biết quy tắc nghiệp vụ** | Trả 13 thay vì 30 |

Các exception runtime hợp lệ có thể do JVM hoặc các thư viện phát sinh theo hợp đồng, không đương nhiên cho biết class file hỏng.

### 5.3. Vì sao lỗi logic đôi khi nguy hiểm hơn exception?

Nếu xử lý đơn giá 10 và số lượng 3, công thức đúng phải cho 30. Nhưng phép `price + quantity` vẫn trả 13 mà **không ném lỗi**. Hệ thống có thể ghi một kết quả sai vào cơ sở dữ liệu, tạo hóa đơn sai hoặc truyền dữ liệu sai cho hệ thống khác.

Verifier không biết **business invariant** như “tổng tiền = đơn giá × số lượng”. Đây là trách nhiệm của thiết kế, kiểm thử, ràng buộc dữ liệu và kiểm tra logic.

## 6. Thử quan sát bytecode: `javap` chứng minh điều gì?

Sau khi biên dịch:

```bash
javap -c -p VerifierLimitsDemo
javap -v -p VerifierLimitsDemo
```

Với `-c`, có thể quan sát các instruction như `iload`, `idiv`, `iaload`, `invokevirtual`, `checkcast`, `aastore` và `ireturn`. Với `-v`, xem được descriptors, `Code` attribute, các bytecode offsets, exception table và `StackMapTable` của các phương thức phù hợp.

Một ví dụ phương thức `divide` do `javac` thường sinh:

```text
static int divide(int, int);
  Code:
     0: iload_0
     1: iload_1
     2: idiv
     3: ireturn
```

**Không có lệnh “if denominator == 0” của Java source nằm ngoài `idiv`**: opcode `idiv` có hợp đồng riêng, trong đó chia số nguyên cho 0 ném `ArithmeticException`. Bytecode đủ hợp lệ về kiểu để gọi phép chia; lỗi chỉ phát sinh khi thực thi với giá trị thích hợp.

### 6.1. Đọc `StackMapTable` không giúp suy ra giá trị thật

`StackMapTable` lưu những **verification types** như Integer, Object, Null, Uninitialized, Top theo quy tắc class file. Nó không lưu “mẫu số chắc chắn khác 0”, “mảng có length >= 3”, “hóa đơn phải nhân” hoặc toàn bộ trạng thái giá trị tại runtime.

Ví dụ:

```text
locals = [Integer, Integer]
operandStack = [Integer, Integer]
```

Trạng thái này có thể đủ cho `idiv`, nhưng **không chứa thông tin một Integer có đang bằng 0 hay không**.

### 6.2. Disassembly không phải một lời hứa về JIT

`javap` đọc class file ở cấp bytecode. Runtime có thể dùng interpreter/JIT để chạy tương đương về ngữ nghĩa; JIT có thể tối ưu hoặc loại bỏ những kiểm tra redundant khi chứng minh hợp lệ, nhưng phải giữ các hiệu ứng quan sát được mà Java/JVM bảo đảm.

Không được kết luận “JIT đã xóa mọi null check” hoặc “có `idiv` nghĩa là CPU sẽ ném một lỗi máy cụ thể” chỉ từ `javap`. Điều cần kiểm tra là **ngữ nghĩa chương trình**.

## 7. Phân biệt `VerifyError` và exception phát sinh từ chương trình hợp lệ

`VerifyError` là một **`LinkageError`** báo rằng JVM phát hiện class file không thỏa ràng buộc bytecode verification khi cố định nghĩa/kiểm tra class theo cơ chế tương ứng.

Ví dụ các thông báo minh họa từ JVM:

```text
java.lang.VerifyError: Bad type on operand stack
java.lang.VerifyError: Expecting a stackmap frame at branch target ...
java.lang.VerifyError: Inconsistent stackmap frames at branch target ...
```

**Đây là các chuỗi lỗi minh họa, không phải stdout của `VerifierLimitsDemo`.** Nội dung thông báo cụ thể phụ thuộc JVM và lỗi thật.

So sánh:

| Loại lỗi | Vấn đề đại diện | Tầng cần điều tra |
| --- | --- | --- |
| `VerifyError` | Bytecode hoặc type states không hợp lệ theo verifier | Cấu trúc instruction, locals/stack, branch, StackMapTable |
| `ClassFormatError` | Cấu trúc/định dạng class file sai | Class file parser, constant pool, attributes |
| `UnsupportedClassVersionError` | Class file dùng phiên bản không được JVM này hỗ trợ | JDK runtime và target version của compiler |
| `NoClassDefFoundError` | Định nghĩa class cần thiết không thể sử dụng/được tìm thấy trong ngữ cảnh runtime | Classpath/module path, loading/linking/initialization |
| `ClassCastException` | Runtime cast sai kiểu reference | Kiểu object thực tế, class loaders, casts |
| `ArrayStoreException` | Ghi object không tương thích với component type thực tế của array | Array covariance và runtime store check |
| `NullPointerException` | Thao tác cần non-null reference nhận null | Luồng giá trị và hợp đồng null |
| `ArithmeticException` | Ví dụ integer division by zero | Giá trị runtime của operand |
| `AssertionError` | Assertion được kích hoạt và thất bại | Điều kiện assertion ở runtime (nếu bật) |
| `OutOfMemoryError` | Runtime không đủ tài nguyên cấp phát theo tình huống tương ứng | Heap, object retention, kích thước dữ liệu và cấu hình |

**Không phải mọi `LinkageError` đều là lỗi verifier.** Class format, version, resolution, access hoặc loading constraints cũng có thể dẫn đến những lỗi riêng. Phân loại chính xác giúp tránh sửa code nghiệp vụ khi class file thực sự hỏng, hoặc sửa StackMapTable khi nguyên nhân chỉ là dữ liệu runtime.

### 7.1. Ví dụ bytecode sai có chủ ý

```text
// CHỈ LÀ MINH HỌA, không phải phương thức có thể chạy:
iconst_1
invokevirtual #someMethod   // dùng int thay object receiver phù hợp
```

Bộ xác minh phải kiểm tra **kiểu trên operand stack** theo chữ ký/phương thức được tham chiếu. Một instruction `invokevirtual` đòi reference receiver thích hợp; bytecode có `int` ở vị trí bắt buộc là reference sẽ không hợp lệ.

Ngược lại:

```java
Object receiver = null;
// receiver.toString(); // sẽ ném NullPointerException nếu chạy
```

Code Java trên (với dòng gọi được bỏ chú thích để chạy) **vẫn có bytecode hợp lệ** vì receiver là một reference về kiểu, dù giá trị cụ thể là null.

## 8. Null-safety không được chứng minh chỉ bằng verification

### 8.1. Kiểu reference và giá trị null khác nhau

Verifier theo dõi những trạng thái **reference** cần thiết để dùng các opcode như `aload`, `invokevirtual`, `checkcast`. Nhưng reference hợp lệ về kiểu có thể là **null reference**. Do vậy:

```java
static int size(String value) {
    return value.length();
}
```

là một phương thức hợp lệ; caller truyền `null` có thể làm nó ném `NullPointerException`.

Nếu API yêu cầu non-null, xử lý ở ranh giới:

```java
import java.util.Objects;

static int checkedSize(String value) {
    Objects.requireNonNull(value, "value");
    return value.length();
}
```

Đoạn trên là **minh họa phương thức**, không phải một Java source file độc lập. Nó chuyển việc vi phạm hợp đồng thành exception rõ ràng tại một điểm đã chọn; nó **không biến verifier thành công cụ chứng minh mọi tham số luôn non-null**.

### 8.2. Annotation không tự trở thành quy tắc verifier

Các annotation như `@Nullable` hoặc `@NonNull` có thể giúp IDE, static analyzer hoặc framework đưa ra chẩn đoán. Nhưng không có quy tắc phổ quát rằng JVM verifier Java 17 sẽ đọc mọi annotation này và cấm truyền null.

Muốn đảm bảo hành vi, dùng một chiến lược phù hợp: API contracts, compile-time analysis, kiểm thử, validation và runtime checks cần thiết.

## 9. Bounds, arithmetic, cast và array store checks nằm ở runtime

Các instruction có hợp đồng về kiểu **và** hợp đồng về giá trị khi chạy. Verifier có thể kiểm tra phần kiểu, còn runtime cần xử lý những điều kiện phụ thuộc dữ liệu thật.

### 9.1. Array bounds

`iaload` yêu cầu reference đến mảng phù hợp và index kiểu `int`. Nhưng không có bằng chứng chung từ StackMapTable cho biết index của mọi lần gọi luôn thỏa `0 <= index < array.length`.

```java
int[] data = {10, 20};
int x = data[2]; // ArrayIndexOutOfBoundsException
```

Mã biên dịch được nhưng chỉ số 2 nằm ngoài khoảng hợp lệ 0–1. Các runtime/compiler có thể tối ưu bounds checks khi chứng minh an toàn nhưng vẫn phải giữ ngữ nghĩa ngoại lệ khi không an toàn.

### 9.2. Division by zero và số học tràn

`idiv` với mẫu số 0 ném `ArithmeticException`. Nhưng **tràn số nguyên trong Java** thường không tạo exception:

```java
int max = Integer.MAX_VALUE;
int overflow = max + 1;
System.out.println(overflow); // -2147483648
```

Đây là hành vi số nguyên 32-bit theo Java, không phải lỗi bytecode type checking. Nếu yêu cầu nghiệp vụ không cho phép tràn, có thể dùng `Math.addExact` hoặc kiểu dữ liệu phù hợp, đồng thời kiểm tra ràng buộc đầu vào.

### 9.3. Cast runtime và class identity

`checkcast` nhận reference hợp lệ về mặt verifier, nhưng có thể ném `ClassCastException` khi object thật không phù hợp target type. Với các class loaders khác nhau, **cùng binary name vẫn có thể là hai runtime classes khác nhau** nếu defining loaders khác nhau. Verifier không có nhiệm vụ “đổi các class trùng tên thành một kiểu chung”.

### 9.4. Array covariance

`String[]` có thể được dùng như `Object[]`. Compiler cho phép gán một `Integer` qua kiểu tĩnh `Object[]`, nhưng array runtime thực là `String[]` nên phép ghi thất bại với `ArrayStoreException`. Verification không thay thế runtime array store check.

## 10. Verifier không giải quyết tính đúng đắn khi chạy đồng thời

Java Memory Model, đồng bộ hóa và thiết kế concurrency giải quyết những vấn đề **khác với** tính hợp lệ của bytecode.

Xem ví dụ minh họa (không phải chương trình độc lập):

```java
final class Counter {
    int count;

    void increment() {
        count++; // đọc, tính, ghi; không phải một phép tăng atomic
    }
}
```

Nếu hai thread cùng gọi `increment()` mà không có đồng bộ hóa phù hợp, có thể xảy ra **lost update**. Bytecode chứa các instruction đọc/ghi field và số học hợp lệ; verifier không chứng minh các thao tác “atomic” hay không có data race.

### 10.1. `volatile` không làm mọi thao tác compound thành atomic

`volatile int count;` bổ sung những bảo đảm về visibility/ordering cho đọc/ghi volatile theo Java Memory Model, nhưng `count++` vẫn là một thao tác compound gồm đọc, cộng và ghi; không tự trở thành atomic read-modify-write cho nhiều thread.

Khi cần tăng đếm an toàn, có thể dùng `AtomicInteger.incrementAndGet()` hoặc một cơ chế đồng bộ phù hợp.

### 10.2. Deadlock không phải lỗi verifier

Hai thread khóa A rồi B, và B rồi A có thể tạo **deadlock** nếu mỗi thread giữ một khóa rồi chờ khóa còn lại. Bytecode của `synchronized`/monitor vẫn hợp lệ. Verifier không chứng minh chương trình luôn tiến triển hoặc mọi lock được lấy theo một trật tự toàn cục.

### 10.3. Không dựa vào thử một lần để phủ định race

Một chương trình chạy đúng 1.000 lần vẫn có thể có lỗi cạnh tranh chỉ xuất hiện trong lịch scheduling hiếm. Cần thiết kế thread-safe, tests chuyên biệt, phân tích concurrency và quan sát production khi phù hợp.

## 11. Verifier không chứng minh chương trình không rò rỉ tài nguyên

Các resource như file handles, sockets, database connections hoặc executor threads có vòng đời riêng. Một phương thức hợp lệ có thể mở tài nguyên nhưng không đóng khi xảy ra exception:

```java
// Minh họa ý tưởng, không phải chương trình độc lập:
java.io.InputStream in = java.nio.file.Files.newInputStream(path);
// Nếu code rời khỏi đoạn này mà không đóng stream, có thể rò rỉ resource.
```

Cách phổ biến để bảo đảm đóng tài nguyên có thể dùng **try-with-resources** khi đối tượng thực hiện `AutoCloseable`:

```java
try (java.io.InputStream in =
         java.nio.file.Files.newInputStream(path)) {
    // Xử lý stream.
}
```

Tất cả các instruction cho việc mở/đóng stream đều có thể hợp lệ về kiểu, nhưng verifier không suy luận được “mọi code path đã giải phóng mọi tài nguyên kịp thời”.

Tương tự, object graph lớn còn reachable có thể khiến GC không thu hồi dữ liệu, dẫn tới áp lực bộ nhớ hoặc `OutOfMemoryError`. Verifier không dự đoán kích thước dữ liệu thực tế hoặc bảo đảm application heap usage nằm dưới ngân sách.

## 12. Verifier không đồng nghĩa với bảo mật ứng dụng

Bytecode verification là **một lớp của cơ chế an toàn JVM**, không phải bằng chứng ứng dụng an toàn trước mọi kiểu tấn công.

Một chương trình Java hoàn toàn hợp lệ vẫn có thể:

- Xây dựng SQL bằng cách nối chuỗi không kiểm soát và mở đường cho SQL injection.
- Ghi file ra vị trí không mong muốn vì không kiểm tra/chuẩn hóa đường dẫn.
- Đăng nhập sai do triển khai authentication/authorization thiếu điều kiện.
- Để lộ secrets qua log hoặc exception messages.
- Cấp quyền quá rộng cho endpoint hoặc bỏ kiểm tra quyền trên một code path.
- Gây denial of service do cấp phát bộ nhớ hoặc tác vụ quá nặng từ dữ liệu nhập.

Những vấn đề này phải được giải quyết bằng **thiết kế bảo mật, validation, phân quyền, parameterized SQL, kiểm thử và quan sát vận hành**. Việc JVM chấp nhận bytecode không chứng minh code không làm những thao tác nguy hiểm nhưng **hợp lệ về kiểu**.

### 12.1. Native code, JNI, FFM và API đặc quyền

Một ứng dụng có thể tương tác với native code hoặc những API cho phép thao tác gần hệ thống. Những thành phần như JNI/native libraries có mô hình rủi ro khác với bytecode Java thuần.

Không suy diễn: “mọi thứ chạy trong tiến trình JVM đều được verifier bảo đảm memory-safe”. Verifier chỉ kiểm soát **bytecode thuộc phạm vi xác minh của JVM**; mã native và các giao tiếp hệ thống đòi những biện pháp an toàn khác.

### 12.2. Bytecode hợp lệ có thể cố ý làm điều sai

Nếu mã có đủ quyền gọi API xóa file, cập nhật cơ sở dữ liệu hoặc gửi dữ liệu ra mạng, verifier không quyết định thao tác đó có được **người dùng hoặc nghiệp vụ cho phép** hay không. Quyền truy cập và kiểm soát tác động ở tầng ứng dụng là phần riêng.

## 13. Type safety ở JVM khác với hợp đồng generics tại nguồn

Java generics được biên dịch phần lớn qua **type erasure**. Verifier kiểm tra class file theo descriptors/verification types đã được mã hóa, không chạy lại toàn bộ hệ thống kiểu generics giàu thông tin của mã nguồn.

Ví dụ:

```java
import java.util.ArrayList;
import java.util.List;

class GenericExample {
    static void demo() {
        List<String> names = new ArrayList<>();
        names.add("Lan");
        System.out.println(names.get(0));
    }
}
```

Đoạn trên **không phải chương trình độc lập** (không có `main`), chỉ minh họa generics. Runtime thường thao tác với các reference theo kiểu đã bị erasure và có thể cần `checkcast` khi đọc kết quả ở những vị trí thích hợp.

Nếu dùng raw types hoặc unchecked operations có thể tạo **heap pollution** mà compiler cảnh báo, rồi gây `ClassCastException` tại điểm đọc/ép kiểu. Class file vẫn có thể là bytecode hợp lệ.

**Lưu ý:** verifier không có nhiệm vụ xem lại mọi generic type argument của mã Java gốc như một theorem prover. `Signature` metadata hỗ trợ công cụ và reflection, nhưng không có nghĩa runtime tự ngăn mọi lỗi do unchecked generics.

## 14. Có phải verifier kiểm tra toàn bộ chương trình trước khi chạy?

Không nên hiểu cơ chế JVM một cách quá đơn giản.

### 14.1. Verification xảy ra trong bối cảnh linking

JVM Specification `5.4 mô tả **linking**, bao gồm verification, preparation và resolution. JVM có những quyền lựa chọn về thời điểm thực hiện một số hoạt động, đặc biệt resolution có thể **lazy**. Vì thế lỗi liên kết có thể chỉ xuất hiện khi một class hoặc symbolic reference cụ thể được sử dụng.

Một class chưa từng được ứng dụng nạp không tự động bị “một verifier toàn JVM” quét chỉ vì một file JAR tồn tại trên disk. Sự chấp nhận của JVM đối với class được thực sự nạp không chứng minh mọi JAR trên classpath đều hợp lệ.

### 14.2. Verification chỉ xem hợp đồng bytecode cần thiết

Verifier không kiểm thử mọi đường dữ liệu với mọi input, không giải toàn bộ giá trị có thể xảy ra và không mô phỏng nghiệp vụ. Với class file có cấu trúc hợp lệ và trạng thái kiểu tương thích, chương trình vẫn có thể kết thúc bởi lỗi logic và runtime.

### 14.3. Những thuộc tính phải tách biệt

- **Class loaded:** có biểu diễn runtime `Class` được tạo từ định nghĩa phù hợp.
- **Class verified:** những kiểm tra bytecode/kiểu liên quan đã được thực hiện thành công.
- **Class initialized:** các bộ khởi tạo tĩnh cần thiết đã hoàn tất theo quy tắc initialization.
- **Method invoked successfully:** invocation và resolution phù hợp đã diễn ra.
- **Business result correct:** logic đáp ứng yêu cầu của người dùng.

Không thể thay thế năm câu trên bằng một biến boolean tên `verified`.

## 15. Khi gặp `VerifyError`, nên điều tra theo hướng nào?

Một `VerifyError` thường gợi ý **class file hoặc bytecode sinh/biến đổi không hợp lệ**. Điều này đặc biệt đáng chú ý khi mã nguồn Java thuần vừa biên dịch bằng compiler chuẩn nhưng đã qua agent/obfuscator/instrumentation.

Quy trình thực hành:

1. Xác định **tên class và method** mà lỗi đề cập.
2. Kiểm tra phiên bản JDK và class format: `java -version`, `javap -v`, `major version`.
3. Xác định class thực tế đang được load từ đâu, nhất là khi classpath chứa nhiều JAR hoặc custom class loaders.
4. Đọc bytecode ở những offsets bị báo lỗi bằng `javap -c -v -p`.
5. So trạng thái **operand stack, local slots, method descriptor, branch targets và StackMapTable**.
6. Nếu class được sửa bằng ASM/Byte Buddy/agent, kiểm tra việc **tính lại stack map frames và max stack/locals** sau biến đổi.
7. Rebuild/test class trên **JDK mục tiêu** sau khi sửa.

Không “chữa” lỗi bằng cách tắt verifier trong hệ thống thật. Hướng đúng là tạo class file hợp lệ, sửa transformer hoặc cập nhật công cụ sinh bytecode.

### 15.1. Nếu lỗi là `NullPointerException` thì sao?

Đừng bắt đầu bằng việc sửa `StackMapTable`. Kiểm tra đầu vào, đối tượng nào null, nơi gán, luồng dữ liệu và contract không-null. Nếu lỗi là tính tiền sai mà không có exception, kiểm tra phép tính, invariant và tests. Dùng đúng công cụ cho đúng tầng lỗi sẽ tiết kiệm thời gian.

## 16. Ma trận kiểm tra theo tình huống

| Triệu chứng | Verifier có nhiệm vụ ngăn trực tiếp? | Hướng sửa/thử nghiệm |
| --- | --- | --- |
| `iadd` nhận reference thay vì hai `int` | **Có**, trong bytecode type checking | Sửa code generator/transformer, frame và kiểu stack |
| `ireturn` khi method trả object | **Có** | Kiểm tra method descriptor và return opcode |
| Branch target dẫn tới trạng thái stack không khớp | **Có** | Kiểm tra CFG và `StackMapTable` |
| Object reference chưa qua `<init>` bị dùng sai quy tắc | **Có**, theo verification rules | Sửa trình tự cấp phát/constructor |
| `10 / 0` với kiểu int | **Không**, đó là runtime arithmetic | Kiểm tra mẫu số hoặc xử lý exception |
| `String s = null; s.length()` | **Không** | Null contract/validation |
| `new int[2]` rồi đọc `a[2]` | **Không** | Bounds handling |
| `String[]` bị ghi `Integer` qua `Object[]` | **Không**, runtime array store check | Sửa API hoặc kiểu collection |
| Tăng biến đếm nhiều thread bị lost update | **Không** | Synchronization, atomic types, concurrency tests |
| Giao dịch tính tổng bằng phép cộng thay vì nhân | **Không** | Business invariants, unit/property tests |
| SQL injection từ chuỗi truy vấn | **Không** | Parameterized SQL, validation, authorization |
| Socket/file không đóng | **Không** | Try-with-resources, lifecycle/resource tests |
| Hết bộ nhớ do giữ quá nhiều object | **Không** | Heap profiling, memory budgets, leak investigations |
| Class format version mới hơn runtime hỗ trợ | Không phải lỗi business; **không đồng nhất với `VerifyError`** | Dùng JDK tương thích hoặc build đúng target |

Lưu ý: “Không” không có nghĩa JVM **không hề có runtime checks**. Nhiều rủi ro như null, bounds, cast, array stores vẫn có kiểm tra ở tầng thực thi và ném exception khi vi phạm. Bảng chỉ nói chúng **không được loại bỏ trước bằng bằng chứng của bytecode verifier**.

## 17. Thiết kế một chiến lược bảo đảm chất lượng nhiều lớp

Để tăng mức tin cậy, kết hợp các lớp kiểm tra:

### Lớp A — Compiler và static analysis

- Dùng compiler warnings và cấu hình build phù hợp.
- Đặt kiểu dữ liệu chính xác, tránh unchecked casts không cần thiết.
- Bổ sung nullness/static analysis khi dự án có quy ước.
- Kiểm tra các lời gọi dễ lỗi: file/network/database, ràng buộc đầu vào, unchecked generics.

Compiler không thể suy luận mọi business rule, nhưng có thể loại bỏ nhiều lỗi sớm.

### Lớp B — Bytecode verification và công cụ build

- Dùng compiler/bytecode tools tương thích target JVM.
- Với class instrumentation, sinh bytecode có frames, descriptors và branch targets hợp lệ.
- Chạy thử bằng JDK mục tiêu; dùng `javap -v` khi nghi ngờ class file.
- Không tắt verifier như giải pháp thường xuyên.

Đây là lớp bảo đảm cấu trúc/kiểu **đúng chuẩn JVM**, không thay thế application tests.

### Lớp C — Runtime validation và exception strategy

- Kiểm tra input, preconditions và nghiệp vụ tại boundary.
- Không bắt mọi exception rồi tiếp tục như thể giao dịch thành công.
- Phân biệt lỗi người dùng có thể sửa với lỗi nội bộ/hạ tầng.
- Dùng try-with-resources, timeout, cancellation và structured lifecycle khi thích hợp.

### Lớp D — Automated testing

- **Unit tests:** kiểm tra logic của hàm nhỏ, đặc biệt các phép tính.
- **Boundary tests:** null, 0, âm, chỉ số đầu/cuối, số lớn, overflow.
- **Integration tests:** kết nối database, file, network, cấu hình.
- **Concurrency tests:** nhiều thread, race conditions, lifecycle và shutdown.
- **Property-based tests:** kiểm tra invariants qua nhiều bộ dữ liệu sinh.
- **Security tests:** authentication, authorization, injection, input validation.

Một test suite tốt nên kiểm tra **kết quả nghiệp vụ** thay vì chỉ “không ném exception”.

### Lớp E — Quan sát và vận hành

- Logging có ngữ cảnh nhưng không làm lộ dữ liệu nhạy cảm.
- Metrics, tracing, error rates, latency, memory/GC telemetry.
- Cảnh báo khi invariants bị vi phạm hoặc hệ thống vượt ngưỡng.
- Regression tests từ lỗi production, review và cải tiến API.

**Không tầng nào một mình đủ để chứng minh một ứng dụng phức tạp luôn đúng.** Mục tiêu thực tế là giảm rủi ro bằng nhiều loại kiểm tra bổ sung nhau.

## 18. Bài tập tự kiểm tra có đáp án

**Câu 1.** `static int f(int a, int b) { return a / b; }` có thể qua verifier không nếu caller đôi khi truyền `b = 0`?

**Đáp án:** Có. Các opcode hợp lệ về kiểu; mẫu số bằng 0 gây `ArithmeticException` tại runtime, không phải verification failure.

**Câu 2.** Verifier có biết một biến `String` luôn khác `null` không?

**Đáp án:** Không có bảo đảm chung như vậy. Kiểu reference tương thích không đồng nghĩa giá trị runtime khác null.

**Câu 3.** Nếu bytecode dùng `iadd` với stack chỉ có một `int`, đây là lỗi nghiệp vụ hay lỗi kiểu/cấu trúc?

**Đáp án:** Lỗi **stack state của bytecode**; verifier có trách nhiệm từ chối phương thức không hợp lệ.

**Câu 4.** `ClassCastException` luôn chứng minh class file chưa qua verification?

**Đáp án:** Không. `checkcast` trên một reference hợp lệ có thể ném `ClassCastException` khi object runtime không tương thích kiểu đích.

**Câu 5.** Vì sao tính `price + quantity` thay vì `price * quantity` không bị verifier báo lỗi?

**Đáp án:** Cả cộng và nhân `int` đều là phép toán hợp lệ. Verifier không biết yêu cầu nghiệp vụ “tổng tiền = đơn giá × số lượng”.

**Câu 6.** Có thể cho rằng “bytecode verified” đồng nghĩa “không thể bị SQL injection” không?

**Đáp án:** Không. SQL injection là lỗi cách xây dựng/xử lý truy vấn và phân quyền ứng dụng; bytecode verification không kiểm tra nội dung câu SQL hoặc logic data flow bảo mật.

**Câu 7.** Vì sao `count++` nhiều thread có thể sai dù verifier chấp nhận?

**Đáp án:** Vì thao tác compound không tự atomic; các thread có thể interleave dẫn đến lost update. Kiểm tra bytecode type safety khác kiểm tra concurrency correctness.

**Câu 8.** `StackMapTable` cung cấp thông tin gì?

**Đáp án:** Trạng thái **verification types** của locals và operand stack tại những bytecode offsets quan trọng; không ghi mọi giá trị thực tế hoặc business invariants.

**Câu 9.** `VerifyError` xuất hiện sau khi bytecode được agent chỉnh sửa. Bước đầu tiên nên là gì?

**Đáp án:** Xác định class/method/offset thật, kiểm tra bytecode, descriptors, branch targets, StackMapTable và công cụ sinh/biến đổi class. Không sửa bằng cách thêm `try/catch (NullPointerException)` tùy tiện.

**Câu 10.** Chương trình tính đúng với 100 đầu vào nhưng sai ở số nguyên rất lớn. Verifier có nghĩa vụ phát hiện overflow theo yêu cầu nghiệp vụ không?

**Đáp án:** Không. `int` có ngữ nghĩa tràn số xác định; cần kiểm thử boundary, dùng `Math.addExact` hoặc kiểu thích hợp nếu overflow không được phép.

**Câu 11.** Có thể dùng `javap -c` để khẳng định không có bug nghiệp vụ không?

**Đáp án:** Không. `javap` giúp xem opcode thực và hỗ trợ chẩn đoán cấp class file; đánh giá nghiệp vụ cần đặc tả, tests và kiểm chứng kết quả.

**Câu 12.** Vì sao class file có `major version` không tương thích gây lỗi khác với bug trong `main`?

**Đáp án:** Runtime không hỗ trợ định dạng class trước khi thực thi nghiệp vụ, thường dẫn tới `UnsupportedClassVersionError`. Một bug trong `main` là lỗi thực thi/logic sau khi class đã được xử lý đủ để chạy.

## 19. Tổng kết

**Bytecode verifier bảo vệ các ràng buộc kiểu và cấu trúc mà JVM cần; nó không đánh giá tính đúng đắn theo mục tiêu của ứng dụng.**

```text
Câu hỏi verifier quan tâm:
  "Giá trị trên operand stack có đúng verification type?"
  "Nhánh nhảy đến trạng thái hợp lệ?"
  "Return opcode khớp method descriptor?"
  "Object chưa khởi tạo được dùng đúng quy tắc?"

Câu hỏi verifier KHÔNG chứng minh:
  "Mẫu số luôn khác 0?"
  "Reference luôn khác null?"
  "Mọi chỉ số mảng đều hợp lệ?"
  "Nhiều thread không race/deadlock?"
  "Giá trị hóa đơn đúng nghiệp vụ?"
  "Ứng dụng không rò rỉ dữ liệu hoặc tài nguyên?"
```

Vì vậy một thiết kế Java/JVM đáng tin cậy cần **compiler + class format/linking/verification + runtime checks + tests + code review + security/concurrency/resource management**. Những tầng này bổ sung, không thay thế nhau.

### Tài liệu đặc tả chính thức

- [JVMS 4.10 — Verification of class Files (Java SE 17)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.10): trách nhiệm và quy tắc verification.
- [JVMS 4.10.1 — Verification by Type Checking](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.10.1): verification types, type transitions, stack map frames.
- [JVMS 4.7.4 — StackMapTable Attribute](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.4): metadata trạng thái kiểu của phương thức.
- [JVMS 5.4 — Linking](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4): verification, preparation, resolution.
- [JVMS 6.5 — JVM Instruction Set](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html#jvms-6.5): hành vi và ngoại lệ của opcode như `idiv`, `iaload`, `aastore` và `checkcast`.
- [Java Language Specification 17 — Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): Java Memory Model, synchronization và data race.
- [`javap` command (JDK 17)](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): đọc bytecode và cấu trúc class file.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="linking-and-initialization-004">Preparation: tạo trường static và thiết lập giá trị mặc định</a>

<details>
<summary>Xem chi tiết</summary>

# Preparation: tạo trường static và thiết lập giá trị mặc định trong JVM

## Mục tiêu

Trong vòng đời một lớp hoặc interface, **preparation** là giai đoạn thuộc **linking**. Ở bước này, JVM tạo trạng thái cho các **static fields** của lớp/interface và đặt chúng về **giá trị mặc định theo kiểu**. Preparation không thực thi các lệnh Java dùng để khởi tạo trường thành giá trị mà lập trình viên viết trong mã nguồn.

Bài học giúp bạn:
- Phân biệt **loading, verification, preparation, resolution và initialization**.
- Liệt kê giá trị mặc định của mọi kiểu trường tĩnh primitive/reference, kể cả số không dấu phẩy động và `char`.
- Giải thích vì sao `static int x = 10` vẫn có giá trị mặc định **0** khi preparation hoàn tất.
- Phân biệt `<clinit>`, `ConstantValue`, inline compile-time constants và các thao tác kích hoạt initialization.
- Phân tích trường hợp có thể thấy giá trị 0/null trong khi static initializer đang chạy, mà không ngộ nhận rằng mã Java chạy trong preparation.
- Thử ví dụ Java 17+, dùng `javap -c -v -p` quan sát class file và nhận diện code initialization.
- Tránh nhầm static state với instance fields, array elements và local variables.

## 1. Vị trí của preparation trong vòng đời class

Một sơ đồ khái quát theo JVMS:

~~~text
             Biểu diễn nhị phân của class / interface
                           |
                           v
                         LOADING
                 Tạo class/interface runtime
                           |
                           v
                         LINKING
                           |
                 +---------+---------+
                 |         |         |
           Verification  Preparation  Resolution
                           |
                 Static fields nhận
                 giá trị MẶC ĐỊNH
                           |
                           v
                     INITIALIZATION
             Giá trị ConstantValue nếu có
             và chạy <clinit> theo quy tắc
                           |
                           v
                        EXECUTION
~~~

Sơ đồ mô tả các khái niệm, **không** yêu cầu resolution của mọi symbolic reference phải hoàn tất trước lúc initialization. JVM có thể resolve sớm hoặc theo nhu cầu, trong các giới hạn mà đặc tả cho phép. Điều được bảo đảm rõ ràng: **preparation phải hoàn thành trước initialization** của class/interface.

| Giai đoạn | Công việc tiêu biểu | Không đồng nghĩa |
| --- | --- | --- |
| Loading | Từ dữ liệu class/JAR hoặc nguồn khác tạo class/interface runtime | Mọi lệnh trong static block đã chạy |
| Verification | Xác minh bytecode và ràng buộc kiểu hợp lệ | Chạy nghiệp vụ Java |
| **Preparation** | Tạo các static fields, đặt default values, kiểm tra một số loading constraints | Chạy biểu thức `= 5` hay constructor |
| Resolution | Giải quyết symbolic references tới class/field/method... | Tự động gọi mọi static initializer liên quan |
| Initialization | Thực thi quy trình khởi tạo lớp, kể cả `ConstantValue` và `<clinit>` nếu có | Chỉ đơn giản đặt 0 cho các static fields |

Theo **JVMS `5.4.2**, preparation có thể xảy ra sau khi class/interface đã được tạo và phải xong trước initialization. Từ mã Java thông thường, không có API phổ quát để dừng quá trình chuẩn bị ở một điểm chính xác, sau đó tùy ý đọc static fields mà không chịu các quy tắc khởi tạo class.

## 2. Preparation thực hiện điều gì?

Giả sử:

~~~java
class Profile {
    static int visits = 10;
    static boolean ready;
    static String title = "Java";
    static Object resource = new Object();
}
~~~

Đây là **class minh họa**, chưa có phương thức `main`. Khi preparation của `Profile` hoàn tất, semantics của static fields là:

| Static field | Giá trị sau preparation | Mã ở initialization sẽ làm gì? |
| --- | --- | --- |
| `visits` | `0` | Gán thành `10` |
| `ready` | `false` | Không có initializer, nên tiếp tục là false nếu không bị gán sau đó |
| `title` | `null` | Gán tham chiếu String tương ứng với `"Java"` |
| `resource` | `null` | Chạy biểu thức `new Object()`, sau đó gán tham chiếu |

**Không** có đối tượng `Object` mới được tạo chỉ vì `resource` có kiểu reference. Trong preparation, trường tham chiếu được mặc định là `null`. Việc chạy `new Object()` thuộc **initialization**, không thuộc preparation.

JVM không cần chạy một đoạn bytecode như `putstatic Profile.visits` với giá trị 0 cho mỗi field ở preparation. Đây là hoạt động liên kết của VM; vì thế không thấy “code gán mặc định” trong `javap -c` cũng là điều bình thường.

### Cụm từ “tạo trường static” có nghĩa gì?

Nó có nghĩa VM chuẩn bị **trạng thái field tĩnh của class/interface runtime**. JVMS quy định kết quả ngữ nghĩa, **không bắt buộc** vị trí vật lý hay layout bộ nhớ phải đồng nhất trên mọi JVM. Tránh các nhận định không có căn cứ như “mọi static fields của mọi phiên bản JVM luôn nằm đúng tại một địa chỉ cố định trong Metaspace”.

Các field được tạo theo **declaring class/interface**. Hai class loader có thể định nghĩa hai kiểu runtime khác nhau dù có cùng tên nhị phân, và mỗi kiểu runtime đó có **bản static state riêng**. Static không mang nghĩa “một biến duy nhất cho mọi class loader trong tiến trình”.

## 3. Bảng giá trị mặc định đầy đủ

| Loại field | Ví dụ khai báo | Giá trị mặc định |
| --- | --- | --- |
| `byte` | `static byte b;` | `0` |
| `short` | `static short s;` | `0` |
| `int` | `static int i;` | `0` |
| `long` | `static long l;` | `0L` |
| `float` | `static float f;` | `+0.0f` |
| `double` | `static double d;` | `+0.0d` |
| `char` | `static char ch;` | NUL, mã số `0` (`'\u0000'`) |
| `boolean` | `static boolean ok;` | `false` |
| Class/interface reference | `static String str;` | `null` |
| Array reference | `static int[] data;` | `null` |
| Reference đến object tự định nghĩa | `static Worker worker;` | `null` |

Điểm dễ nhầm:

- `char` nhận **code unit 0**, khác ký tự chữ số `'0'` (code unit 48) và khác khoảng trắng.
- `float` và `double` mặc định là **dương 0**, không phải NaN, vô cực hoặc âm 0.
- `String` mặc định **null**, không phải chuỗi rỗng `""`.
- Array reference mặc định **null**, không phải mảng độ dài 0.
- Các field số nguyên đều có giá trị số học 0; `boolean` là false, không phải một object `Boolean.FALSE` được tạo.
- Bảng trên nói về trạng thái **sau preparation**; field có thể tiếp tục giữ giá trị đó sau initialization nếu không có phép gán thay thế.

### Static fields không phải local variables

Ví dụ:

~~~java
class Example {
    static int shared;

    void work() {
        int local;
        // System.out.println(local); // Không hợp lệ: local chưa được gán.
        System.out.println(shared);    // Hợp lệ: static field có default 0.
    }
}
~~~

Đây là code class minh họa, không phải chương trình độc lập. Java yêu cầu **definite assignment** đối với local variables trước khi đọc chúng. Field là phần trạng thái được VM gán default values theo quy tắc của field; không được lẫn hai cơ chế.

## 4. Preparation khác hẳn initialization

So sánh:

~~~java
class ClockState {
    static int ticks = 15;

    static {
        System.out.println("static block");
    }
}
~~~

Trong preparation, `ticks` có default `0`. Khi initialization thực thi, biểu thức `ticks = 15` và static block chạy theo thứ tự mã nguồn thích hợp. Compiler Java thường phát bytecode trong phương thức **`<clinit>`**:

~~~text
bipush        15
putstatic     #N    // ClockState.ticks:I
getstatic     #M    // java/lang/System.out
ldc           #K    // "static block"
invokevirtual #P    // java/io/PrintStream.println
return
~~~

Đây là **bytecode minh họa**, không phải output đảm bảo cùng chỉ số `#N`/`#M` trên mọi compiler. Thấy `putstatic` dùng để gán 15 nghĩa là đã có **explicit initialization logic**. Preparation đã tạo field và default value từ trước; nó không cần chạy code này.

| Câu hỏi | Preparation | Initialization |
| --- | --- | --- |
| Field `static int x = 15` có trạng thái chưa? | Có, default 0 | Gán x thành 15 |
| JVM chạy phương thức `initializeX()` không? | Không | Có nếu explicit initializer cần |
| Static block chạy không? | Không | Có |
| `<clinit>` chạy không? | Không | Có nếu class có và cần chạy |
| Field tham chiếu `= new Object()` đã có object chưa? | Chưa, default null | Constructor/new chạy theo initializer |
| Có liên quan `ConstantValue` không? | Không gán giá trị đó ở preparation theo JVMS 21 | Gán theo quy trình initialization, **trước** `<clinit>` |

Phải phân biệt **thứ tự thành phần trong đặc tả** với thời điểm một thread ứng dụng có thể quan sát. Không có nghĩa một câu lệnh ngoài lớp có thể chủ động đọc static field giữa hai giai đoạn mà không kích hoạt initialization.

## 5. Ví dụ Java 17+ quan sát tác dụng của default value

Ví dụ hoàn chỉnh sau giúp quan sát giá trị mặc định của field trong **quá trình chạy initializer**, trước khi một initializer phía sau được thực hiện. Nó không làm JVM thực thi Java code trong preparation.

Tạo tệp **`PreparationDemo.java`**:

~~~java
public class PreparationDemo {
    static class State {
        static boolean enabled;
        static long requests;
        static double ratio;
        static char letter;
        static Object object;

        static int first = readLater();
        static int later = 21;
        static final int CONSTANT = 7;

        static int readLater() {
            System.out.println("during init: later=" + later);
            return later + 1;
        }
    }

    public static void main(String[] args) {
        System.out.println("start");
        System.out.println("first=" + State.first);
        System.out.println("enabled=" + State.enabled);
        System.out.println("requests=" + State.requests);
        System.out.println("ratio=" + State.ratio);
        System.out.println("letterCode=" + (int) State.letter);
        System.out.println("object=" + State.object);
        System.out.println("later=" + State.later);
        System.out.println("constant=" + State.CONSTANT);
    }
}
~~~

Biên dịch và chạy với **JDK 17+**:

~~~bash
javac --release 17 PreparationDemo.java
java PreparationDemo
~~~

**Stdout chính xác:**

~~~text
start
during init: later=0
first=1
enabled=false
requests=0
ratio=0.0
letterCode=0
object=null
later=21
constant=7
~~~

Giải thích chi tiết:

1. Lớp outer `PreparationDemo` được khởi tạo khi launcher gọi `main`. Nested class `State` có vòng đời khởi tạo riêng.
2. Dòng `start` được in trước khi program cần giá trị của `State.first`.
3. Lần đọc `State.first` bằng static field access kích hoạt initialization của `State` theo quy tắc JVM, sau khi preparation của `State` đã hoàn tất.
4. Sau preparation, `State.first = 0` và `State.later = 0`. Các field khác có false/0/+0.0/NUL/null như bảng.
5. Trong `<clinit>`, initializer **`first = readLater()`** chạy **trước** initializer `later = 21`. Phương thức `readLater()` đọc `later` đang là 0, in `during init: later=0`, trả 1.
6. Lúc đó `first` được gán thành 1. Tiếp theo `later` được gán thành 21.
7. Sau khi initialization hoàn tất, các lần đọc trong `main` cho `first=1` và `later=21`.
8. `CONSTANT = 7` là compile-time constant variable. Compiler có thể **inline giá trị 7** vào caller; dòng in constant không chứng minh có `getstatic State.CONSTANT` tại runtime.

**Tại sao dùng phương thức `readLater()`?** Java cấm một số **illegal forward reference** trong field initializers khi truy cập trực tiếp field được khai báo ở phía sau bằng simple name. Đặt việc đọc trong phương thức hợp lệ là cách minh họa có thể compile. Dù JVMS đảm bảo default value, **Java compiler vẫn có quy tắc về tham chiếu trước khai báo** cần tuân thủ.

### Cách diễn đạt chính xác

Không nói “preparation chạy `readLater()`”. Đúng phải là: **preparation đặt `later` về 0; sau đó initialization chạy `readLater()` trước khi gán 21, nên method thấy 0**.

Điều này cũng giải thích tại sao việc thấy 0 không đủ để biết class *đang* ở preparation: field có thể có giá trị 0 trước khi initializer gán hoặc vì không có initializer nào, kể cả sau khi class đã khởi tạo xong.

## 6. ConstantValue: phân biệt với default value

Một bẫy kiến thức phổ biến là nói “`static final` có giá trị ngay trong preparation”. Để chính xác theo **JVMS Java SE 17 §5.4.2, §5.5 và §4.7.2**:

- **Preparation** tạo static fields và gán default values.
- **Initialization** gán giá trị của attribute **`ConstantValue`** cho những `static final` fields thích hợp, **trước khi gọi phương thức `<clinit>`**.
- Sau đó phần static initialization code trong `<clinit>` (nếu có) thực hiện các initializer còn lại.

Ví dụ:

~~~java
class Sample {
    static final int LIMIT = 10;
    static final String LABEL = "ok";
    static int counter = 5;
    static final Integer BOXED = Integer.valueOf(6);
}
~~~

Đây là class minh họa. Ý nghĩa từng field:

| Field | Default từ preparation | Có thể có `ConstantValue`? | Cách đạt giá trị khai báo |
| --- | --- | --- | --- |
| `LIMIT` | 0 | Có, `int 10` | Gán ConstantValue trong initialization |
| `LABEL` | null | Có, `String ok` | Gán ConstantValue trong initialization |
| `counter` | 0 | Không chỉ vì initializer literal, do không final | `<clinit>` gán 5 bằng `putstatic` |
| `BOXED` | null | Không theo quy tắc constant variable primitive/String | `<clinit>` gọi `Integer.valueOf` rồi gán |

### Ví dụ runnable để xem attribute

Tạo **`ConstantValueDemo.java`**:

~~~java
public class ConstantValueDemo {
    static final int LIMIT = 100;
    static final String LABEL = "ready";

    static int dynamic = 23;
    static final Integer BOXED = Integer.valueOf(7);

    static {
        System.out.println("initialized");
    }

    public static void main(String[] args) {
        System.out.println("limit=" + LIMIT);
        System.out.println("label=" + LABEL);
        System.out.println("dynamic=" + dynamic);
        System.out.println("boxed=" + BOXED);
    }
}
~~~

Lệnh:

~~~bash
javac --release 17 ConstantValueDemo.java
java ConstantValueDemo
javap -v -p ConstantValueDemo
javap -c -p ConstantValueDemo
~~~

**Stdout của chương trình:**

~~~text
initialized
limit=100
label=ready
dynamic=23
boxed=7
~~~

Khi đọc `javap -v`, tìm:
- `LIMIT`: flag `ACC_STATIC, ACC_FINAL`, descriptor `I`, attribute **`ConstantValue: int 100`**.
- `LABEL`: descriptor `Ljava/lang/String;` và attribute **`ConstantValue: String ready`**.
- `dynamic`: không có `ConstantValue` chỉ vì initializer là 23; thường được gán bằng `putstatic` trong `<clinit>`.
- `BOXED`: khởi tạo bằng lời gọi `Integer.valueOf(7)` trong `<clinit>`, không tự tạo object Integer ở preparation.

**Lưu ý:** các hằng compile-time có thể được compiler **inline vào caller**. Một class bên ngoài chỉ đọc `Sample.LIMIT` có thể không phát `getstatic` và không kích hoạt initialization của `Sample` vì giá trị đã ở chính caller. Nếu đổi giá trị constant trong thư viện nhưng không biên dịch lại caller, caller có thể còn giữ giá trị cũ.

Không dùng việc in `LIMIT=100` để suy diễn nó được gán lúc preparation; đặc tả quy định ranh giới gán ConstantValue rõ ràng.

## 7. Mã trong `<clinit>` và thứ tự chạy

Java compiler có thể tổng hợp các **static field initializers** và **static initialization blocks** thành method đặc biệt `<clinit>` theo thứ tự source. Ví dụ:

~~~java
class Steps {
    static int first = log("first", 1);

    static {
        System.out.println("block");
    }

    static int second = log("second", 2);

    static int log(String label, int result) {
        System.out.println(label);
        return result;
    }
}
~~~

Đây là class minh họa chưa có `main()`. Khi initialization của `Steps` được kích hoạt và diễn ra bình thường, side effects xuất hiện:

~~~text
first
block
second
~~~

Preparation đã tạo cả `first` và `second` với default 0 **trước** những dòng in này; nó không thực thi `log` hoặc `println`.

Không phải class nào cũng có `<clinit>` trong file biên dịch. Nếu không có phần static initialization bytecode cần chạy, compiler có thể không phát method đó. **Không thấy `<clinit>` không chứng minh đã bỏ qua preparation**.

### Class và interface

Interface cũng có static fields. Trong Java source, field của interface mặc nhiên là **`public static final`**, nhưng initializer có thể là một phép gọi method và **không phải compile-time constant**:

~~~java
interface FeatureFlags {
    int FAST_LIMIT = 64;
    String JAVA_VERSION = System.getProperty("java.version");
}
~~~

Đây là interface minh họa. Các static fields được preparation trước initialization. `FAST_LIMIT` có thể dùng `ConstantValue`, còn `JAVA_VERSION` cần code thực thi trong interface initialization. Interface có quy tắc kích hoạt initialization riêng; tạo object implementing interface không đơn giản đồng nghĩa “khởi tạo mọi interface liên quan ngay lập tức”.

## 8. Static field, instance field, array element và local variable

**Preparation là thao tác đối với static fields của class/interface**, không phải cơ chế cấp phát/đặt mặc định cho mọi dữ liệu trong chương trình.

| Loại dữ liệu | Nguồn của default value | Ví dụ |
| --- | --- | --- |
| **Static field** | Preparation của class/interface khai báo | `static int total;` → 0 |
| **Instance field** | Tạo object, trước constructor/instance initialization | `int age;` ở mỗi object mới → 0 |
| **Array element** | Tạo mảng, mỗi phần tử được gán default | `new int[3]` → 0,0,0 |
| **Local variable** | Java yêu cầu gán chắc chắn trước khi đọc; không được tự đọc một giá trị mặc định tùy ý | `int x; println(x);` lỗi compile-time |

Ví dụ hoàn chỉnh **`DefaultKindsDemo.java`**:

~~~java
import java.util.Arrays;

public class DefaultKindsDemo {
    static int shared;
    int perObject;

    public static void main(String[] args) {
        DefaultKindsDemo first = new DefaultKindsDemo();
        DefaultKindsDemo second = new DefaultKindsDemo();
        int[] numbers = new int[3];

        first.perObject = 5;

        System.out.println("shared=" + shared);
        System.out.println("first=" + first.perObject);
        System.out.println("second=" + second.perObject);
        System.out.println("array=" + Arrays.toString(numbers));
    }
}
~~~

Chạy:

~~~bash
javac --release 17 DefaultKindsDemo.java
java DefaultKindsDemo
~~~

**Stdout chính xác:**

~~~text
shared=0
first=5
second=0
array=[0, 0, 0]
~~~

`shared` là một static field được preparation, hai field `perObject` thuộc hai object khác nhau, còn ba phần tử array được mặc định khi cấp phát array. Tất cả có thể thấy số 0, nhưng **không phải đều là hậu quả của class preparation**.

## 9. JVM liên kết preparation với loại class nào?

Danh tính runtime của class/interface không chỉ dựa vào chữ tên lớp trong source: JVM phân biệt bằng **binary name + defining class loader**. Hai defining loaders khác nhau có thể tạo hai class runtime cùng binary name, với trạng thái static khác nhau:

~~~text
com.example.Config defined by Loader A
  -> static counter A
  -> preparation A / initialization A

com.example.Config defined by Loader B
  -> static counter B
  -> preparation B / initialization B
~~~

Đây là sơ đồ khái niệm. Do đó nói “static variable luôn chỉ có một bản trên toàn JVM” không chính xác đối với ứng dụng dùng plugin class loaders hoặc module layers.

### Loading constraints trong preparation

Ngoài việc gán default values, **JVMS `5.4.2** áp đặt những **loading constraints** khi xử lý một số method overriding/selection có type signature liên quan đến các class loaders khác nhau.

Mục tiêu là duy trì type identity và liên kết method an toàn. Nếu constraint bị vi phạm, JVM có thể ném **`LinkageError`**, khiến preparation/linking thất bại. Không nên coi preparation chỉ là một vòng đặt 0 mà hoàn toàn không thể liên quan đến lỗi kiểu/liên kết.

**Không đồng nhất với resolution**: resolution giải quyết symbolic references đến các class/field/method và thực thể khác theo quy tắc riêng. Nó có thể thực hiện sớm hoặc theo nhu cầu; preparation phải hoàn thành trước initialization.

## 10. Những thao tác kích hoạt initialization

Mã Java thường không được quan sát field ở trạng thái giữa preparation và initialization vì các thao tác dùng class hay static field có thể **kích hoạt initialization**. Một số nguyên nhân thường gặp:
- `new SomeClass()` (tạo instance).
- Đọc static field qua `getstatic` hoặc ghi qua `putstatic` theo các quy tắc của JVM.
- Gọi static method qua `invokestatic`.
- Một số hành động reflection hoặc method-handle được đặc tả kích hoạt initialization.
- Class được khởi tạo có thể đòi hỏi superclass hoặc superinterfaces liên quan được khởi tạo theo quy tắc.

Không phải mọi thao tác “nhắc tên lớp” đều kích hoạt initialization:
- Lấy class literal `SomeClass.class` không đơn thuần chạy static block chỉ vì tên lớp được nhắc.
- Compile-time constants có thể được inline, không cần đọc field runtime.
- `Class.forName(className, false, loader)` cho phép lấy Class mà không yêu cầu initialization do lời gọi ấy; nhưng điều đó **không có nghĩa bạn được đọc static field bằng reflection mà bỏ qua các quy tắc initialization**.

**Quan trọng:** một class có thể được loaded/linked trước khi initializer chạy; không được đồng nhất “JVM biết class rồi” với “mọi static initializer đã hoàn tất”.

## 11. Điều gì xảy ra khi nhiều thread cùng dùng class?

JVM có quy tắc đồng bộ hóa quá trình initialization để xử lý nhiều thread đồng thời. Một thread có thể đang chạy `<clinit>`, thread khác yêu cầu initialization của cùng class phải tuân thủ cơ chế chờ/thứ tự của JVM. Nếu chính thread đang khởi tạo lại gián tiếp yêu cầu cùng class, có quy tắc nhận diện **initialization in progress**.

Việc một initializer đọc field khác quá sớm có thể nhìn thấy giá trị **default** hoặc giá trị mới được gán một phần trong chuỗi initialization. Không nên dùng “thời gian ở giữa preparation và initialization” như một cơ chế giao tiếp thread. Thay vào đó, tổ chức khởi tạo không có vòng phụ thuộc hoặc có phương án điều phối rõ ràng.

Ví dụ **minh họa** vòng phụ thuộc:

~~~java
class A {
    static int x = B.y + 1;
}

class B {
    static int y = A.x + 1;
}
~~~

Đây là hai class declarations, không có main. Nếu A và B phụ thuộc vòng khi được initialized, việc một bên đọc static field của bên kia trước khi initializer tương ứng chạy có thể khiến nó thấy default 0. Tùy thứ tự kích hoạt và thực thi, kết quả cuối có thể khác mong đợi nếu giả định tất cả fields đã có giá trị cuối.

### Preparation không thay thế thread safety

Preparation đặt `static int counter` về 0, **không** làm `counter++` trở thành atomic. Về sau, nhiều thread cùng read-modify-write phải dùng công cụ synchronization phù hợp nếu cần tính đúng đắn, ví dụ `AtomicInteger` hoặc synchronized block theo thiết kế ứng dụng.

## 12. Sử dụng javap để thấy khác biệt

Đối với file đã biên dịch từ ví dụ `PreparationDemo.java`:

~~~bash
javap -v -p 'PreparationDemo$State'
javap -c -p 'PreparationDemo$State'
~~~

**Trong Windows cmd.exe**, có thể dùng biểu thức `PreparationDemo$State` theo cú pháp shell thích hợp; trong PowerShell, single quotes như trên ngăn shell can thiệp vào dấu `$`. Các ví dụ trên giả định file class đã được biên dịch trong thư mục hiện hành.

Khi đọc output:
1. Field declaration của nested class chứa `ACC_STATIC`; xác định tên, descriptor và các flags.
2. `first` và `later` có descriptor `I` (int); các field khác có các descriptor `Z`, `J`, `D`, `C` hoặc kiểu reference.
3. Trong `<clinit>`, tìm instruction gọi `readLater()`, `putstatic ...first:I`, nạp giá trị 21 rồi `putstatic ...later:I`.
4. Field `CONSTANT` có thể có attribute `ConstantValue` vì là compile-time constant.
5. Không có lệnh `putstatic` gán 0 ban đầu cho **mọi** field; default values thuộc preparation, không phải bytecode Java phải có trong `<clinit>`.
6. Bytecode index `#N` có thể khác giữa compiler và phiên bản JDK. Đừng hardcode chúng trong diễn giải.

Một sơ đồ class file:

~~~text
field_info later:
  access_flags: ACC_STATIC
  descriptor:   I

Preparation:
  later = 0          (hoạt động VM)

Initialization:
  <clinit>:
    bipush 21
    putstatic #N      // later:I
~~~

Đây là sơ đồ kết hợp thông tin class file và hoạt động runtime, **không** phải một dump nhị phân exact của toàn bộ class.

## 13. Lỗi và ngộ nhận thường gặp

| Nhận định hoặc triệu chứng | Phân tích đúng |
| --- | --- |
| “`static int x = 10` được gán 10 trong preparation.” | Preparation đặt 0; explicit initializer 10 thuộc initialization. |
| “Mọi field reference có object được tạo sẵn.” | Default là `null`; constructor/mảng chỉ được cấp phát khi code thực thi. |
| “`static final int X=10` nhất định được gán trong preparation.” | Theo JVMS 21, `ConstantValue` được gán trong quy trình initialization trước `<clinit>`. |
| “Giá trị field bằng 0 chứng minh JVM đang ở preparation.” | Không. Field có thể giữ 0 cả sau initialization hoặc được đọc trước initializer của nó trong `<clinit>`. |
| “Không thấy `<clinit>` nghĩa là preparation không xảy ra.” | Sai. Preparation là việc VM làm, không phải method Java bắt buộc xuất hiện trong class file. |
| “Static object field luôn có một bản duy nhất trong toàn JVM.” | Mỗi **class runtime** có static state riêng; class loaders khác nhau có thể định nghĩa class khác nhau. |
| “`Class.forName(name,false,loader)` cho phép đọc static values trước initialization.” | Không tự cung cấp khả năng bypass quy tắc initialization khi đọc static field. |
| “Default values của instance/array cũng do class preparation.” | Sai: instance/array nhận defaults lúc object/array creation. |
| “Preparation không bao giờ có lỗi.” | Loading constraints trong preparation có thể thất bại, gây `LinkageError`. |
| “Initialization thất bại có nghĩa preparation chưa làm.” | Sai: preparation phải hoàn tất trước initialization. |
| “Field `volatile` được chuẩn bị nên `x++` atomic.” | Sai: volatile là semantics về memory visibility/order, không biến read-modify-write thành atomic. |
| “Class đã loaded tức là đã initialized.” | Sai: loading/linking và initialization là các bước riêng. |

### Khi xảy ra lỗi

**`VerifyError`** thường liên quan verification, **`ClassFormatError`** liên quan định dạng class file, **`NoSuchFieldError`** liên quan field resolution, còn **`ExceptionInInitializerError`** thường bắt nguồn từ mã chạy trong initialization của class. Không nên quy toàn bộ lỗi vòng đời lớp về preparation.

Khi chẩn đoán, xem:
1. Mã đang load, link hay chạy static initializer?
2. Lỗi có cause từ `<clinit>` hay không?
3. Class và dependency/JAR nào thực sự được loader tải?
4. Có initializer đọc field khai báo phía sau hoặc có vòng phụ thuộc giữa các class?
5. Field bị thay descriptor/static modifier giữa build và runtime không?
6. Compiler có thể đã inline một compile-time constant không?

## 14. Câu hỏi tự kiểm tra

**1.** Preparation nằm trong loading, linking hay initialization?

**Đáp án:** Nó là một bước của **linking**; phải hoàn thành trước initialization.

**2.** Sau preparation, `static int count = 5;` có giá trị nào?

**Đáp án:** **0**. Lệnh gán 5 thuộc initialization.

**3.** `static String name;` mặc định có phải chuỗi rỗng không?

**Đáp án:** Không. Là `null`.

**4.** `static char letter;` là ký tự chữ số 0 hay code unit 0?

**Đáp án:** Code unit **0** (NUL, `'\u0000'`), không phải `'0'`.

**5.** Tại sao `State.readLater()` trong ví dụ lại nhìn thấy `later=0`?

**Đáp án:** Preparation đã đặt default 0. Phương thức được chạy **trong initialization**, trước câu lệnh gán `later = 21`.

**6.** `ConstantValue` của `static final int LIMIT` được gán ở preparation hay initialization theo JVMS 21?

**Đáp án:** Trong **initialization**, trước invocation của `<clinit>`.

**7.** Đọc một constant variable từ caller có chắc làm class khai báo initialized không?

**Đáp án:** Không. Compiler có thể inline giá trị, không cần `getstatic`.

**8.** Static field và instance field khác nhau thế nào ở thời điểm nhận default value?

**Đáp án:** Static field nhận default trong class/interface preparation; instance field nhận default khi object được cấp phát, trước constructor logic.

**9.** Hai class loader định nghĩa cùng tên class có bắt buộc chia sẻ static field không?

**Đáp án:** Không. Chúng có thể tạo các class runtime khác nhau, mỗi bên có state riêng.

**10.** Tại sao không nên coi preparation là chạy một loạt `putstatic 0` do compiler tạo?

**Đáp án:** Preparation được JVM làm như một bước liên kết, không yêu cầu chạy explicit Java bytecode.

**11.** Preparation có liên quan loading constraints không?

**Đáp án:** Có. JVMS `5.4.2 quy định các ràng buộc liên quan overriding/selection; vi phạm có thể gây `LinkageError`.

**12.** Không thấy static initializer trong class file thì class không cần preparation?

**Đáp án:** Sai. Preparation vẫn xảy ra theo cơ chế lifecycle, dù không có mã `<clinit>` cần chạy.

## 15. Ghi nhớ

~~~text
Class / interface đã được load
                |
                v
              LINKING
  +-------------+-------------+
  |             |             |
Verify      PREPARATION     Resolution
                |
                +-- tạo các static fields
                +-- gán DEFAULT VALUES
                |    primitive numeric 0
                |    boolean false
                |    char NUL
                |    references null
                +-- loading constraints cần thiết
                |
                v
           INITIALIZATION
     ConstantValue (nếu có)
     rồi <clinit> (nếu có)
     static initializers / static blocks
                |
                v
       Class initialized

Instance field default: khi tạo object
Array element default: khi tạo array
Local variable: definite assignment
~~~

**Kết luận:** Preparation **không phải** giai đoạn thực thi initializer của Java source. Nó tạo trạng thái static cho class/interface với giá trị **mặc định**, trước initialization. Tách bạch quá trình này với `ConstantValue`, `<clinit>`, object creation và constant inlining là nền tảng để hiểu vòng đời JVM đúng theo đặc tả.

## Tài liệu chính thức

- [JVMS Java SE 17 — 5.4.2 Preparation](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.2).
- [JVMS Java SE 17 — 5.5 Initialization](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5).
- [JVMS Java SE 17 — 4.7.2 ConstantValue Attribute](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.2).
- [JVMS Java SE 17 — 2.3 Primitive Types and Values](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.3).
- [JVMS Java SE 17 — 2.4 Reference Types and Values](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.4).
- [JLS Java SE 17 — 12.4 Initialization of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4).
- [JLS Java SE 17 — 8.3.3 Restrictions on Field References in Initializers](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.3.3).
- [JDK 17 — javap Command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="linking-and-initialization-005">Resolution: chuyển tham chiếu ký hiệu thành liên kết dùng khi chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Resolution: chuyển tham chiếu ký hiệu thành liên kết dùng khi chạy

Trong **Java Virtual Machine (JVM)**, mã bytecode trong một `.class` thường không chỉ thẳng tới địa chỉ bộ nhớ của một class, field hay method. Thay vào đó, ClassFile có **constant pool** chứa các **symbolic references (tham chiếu ký hiệu)**: tên class/interface, tên thành viên và **descriptor** mô tả kiểu hoặc chữ ký JVM. Khi cần sử dụng chúng, JVM thực hiện **resolution**: xác định thực thể runtime mà tham chiếu chỉ tới, đồng thời áp dụng các quy tắc về định danh lớp, tra cứu thành viên, kiểm soát truy cập và tính tương thích.

Resolution là một phần của **linking**, nhưng không phải toàn bộ linking. Nó cũng **không đồng nghĩa** với chạy static initializer (`<clinit>`), không phải việc gọi method từ Java source và không phải cứ mỗi lần opcode chạy thì JVM lại dò lại tên từ đầu.

Mục tiêu học tập:

1. Đọc được tham chiếu ký hiệu từ constant pool bằng `javap -v`.
2. Hiểu điều kiện nào làm một tham chiếu có thể trở thành liên kết runtime hợp lệ.
3. Phân biệt **loading**, **verification**, **preparation**, **resolution**, **initialization** và **execution/dispatch**.
4. Lý giải các lỗi như `NoSuchMethodError`, `NoSuchFieldError`, `IllegalAccessError` và `IncompatibleClassChangeError`.
5. Hiểu việc resolution có thể diễn ra sớm hoặc muộn, tùy cơ chế VM và loại tham chiếu; không áp đặt thứ tự chạy giả tạo cho tất cả JVM.
6. Biết ranh giới của resolution khi gặp **`invokevirtual`**, **`invokeinterface`**, **`invokedynamic`** và **constant dynamic**.

## 1. Constant pool chứa “tên cần giải nghĩa”, không phải địa chỉ native

Một class được `javac` biên dịch có thể mang những mục như:

```text
CONSTANT_Class_info
CONSTANT_Fieldref_info
CONSTANT_Methodref_info
CONSTANT_InterfaceMethodref_info
CONSTANT_NameAndType_info
CONSTANT_Utf8_info
```

Các mục tham chiếu **không lưu cố định địa chỉ RAM** nơi field/method sẽ ở trên một JVM cụ thể. Chúng biểu diễn các mối liên hệ tượng trưng, để class file có thể được mang sang JVM khác, kiến trúc CPU khác và môi trường nạp lớp khác mà vẫn sử dụng cùng định dạng.

Ví dụ Java:

```java
class Account {
    int balance;

    int add(int amount) {
        balance += amount;
        return balance;
    }
}
```

Đây là class minh họa, có thể lưu riêng thành `Account.java`. Một method gọi `Account.add(int)` có thể tham chiếu tới method dựa trên thông tin dạng:

```text
Class:       Account
Method name: add
Descriptor:  (I)I
```

`(I)I` nghĩa là method nhận một `int` và trả về `int`. Bộ ba này là **dữ liệu logic của symbolic method reference**, không phải “con trỏ hàm” đóng băng vào bytecode.

Sơ đồ khái niệm:

```text
ClassFile của Client
┌────────────────────────────────────────────────┐
│ constant_pool                                  │
│  #N  Methodref → Account.add:(I)I               │
└────────────────────────────────────────────────┘
                    │
                    │ resolution
                    ▼
    JVM tìm runtime class Account phù hợp
    + tìm method có tên/descriptor cần thiết
    + kiểm tra quy tắc truy cập/tương thích
                    │
                    ▼
       một tham chiếu đã được phân giải
                    │
                    │ invocation, dispatch
                    ▼
        mã method thích hợp được thực thi
```

Đây là **mô hình diễn giải**, không yêu cầu mọi JVM lưu một ô địa chỉ có cùng cấu trúc. JVM có thể dùng runtime constant pool, cache, metadata pointers, inline caches hoặc cấu trúc nội bộ khác để hiện thực hóa liên kết.

## 2. ClassFile constant pool và runtime constant pool

Theo *Java Virtual Machine Specification* (JVMS), ClassFile chứa `constant_pool` — cấu trúc nhị phân lưu các symbolic references và hằng số. Khi JVM tạo class/interface từ class file, nó có **runtime constant pool** tương ứng với các thông tin này.

| Nơi | Vai trò |
| --- | --- |
| `constant_pool` trong `.class` | Dữ liệu tuần tự hóa trong file, dùng các chỉ mục và thẻ `CONSTANT_*` |
| Runtime constant pool của class/interface | Cấu trúc runtime mà JVM dùng để thao tác với các hằng và symbolic references |
| Thông tin resolution | Kết quả nội bộ khi JVM đã phân giải một reference theo luật liên kết |
| Object/field/method lúc chạy | Thực thể runtime, có ngữ nghĩa thực thi tương ứng; không phải chuỗi UTF-8 trong file |

Ví dụ khi xem `javap -v`:

```text
#12 = Methodref          #18.#19  // Account.add:(I)I
#18 = Class              #20      // Account
#19 = NameAndType        #21:#22  // add:(I)I
#20 = Utf8                         Account
#21 = Utf8                         add
#22 = Utf8                         (I)I
```

Đây là **bản trích minh họa**, chỉ số `#12`, `#18`… là ví dụ, không phải output cam kết từ một lần biên dịch cụ thể. Kết quả thật phụ thuộc thứ tự constant pool của `javac` và code được biên dịch.

**NameAndType** kết hợp **tên** và **descriptor**. Class symbolic reference trỏ tới tên class/interface phù hợp; Fieldref/Methodref/InterfaceMethodref liên kết tới class/interface và NameAndType. Mục `Utf8` lưu chuỗi định dạng mà JVM dùng để xác định tên, chữ ký hoặc metadata.

### 2.1. Descriptor quan trọng như tên

```text
foo:()I                 → không tham số, trả int
foo:(I)I                → một int, trả int
foo:(Ljava/lang/String;)V → một String, trả void
```

Nếu class mới vẫn có method tên `foo` nhưng đổi descriptor, một binary client được biên dịch theo descriptor cũ có thể **không resolution thành công**. Vì vậy không thể chỉ tìm “một method có cùng tên” rồi coi là tương thích.

Đối với field, descriptor cũng xác định loại field, ví dụ `I` cho `int`, `Ljava/lang/String;` cho `String`. Generic `List<String>` và `List<Integer>` thường có descriptor sau **type erasure** là `Ljava/util/List;`; thông tin kiểu generic có thể nằm trong thuộc tính `Signature` nhưng không thay thế JVM field descriptor.

## 3. Phân biệt loading, linking và initialization

Vòng đời cần đọc đúng thứ tự khái niệm:

```text
              Loading
                │
                ▼
              Linking
          ┌─────┼───────┐
          │     │       │
       Verify  Prepare  Resolve
                │       │
                │       └── có thể thực hiện theo nhu cầu
                ▼
           Initialization
       (<clinit> nếu có, theo
       điều kiện active use)
                │
                ▼
             Execution
```

Sơ đồ gom các giai đoạn thành một hình để so sánh; **không khẳng định resolution bắt buộc được hoàn tất toàn bộ trước initialization hoặc mọi symbol đều được giải cùng lúc**.

- **Loading:** JVM sử dụng class loader thích hợp để tìm/lấy định nghĩa class/interface và tạo biểu diễn runtime. Việc tìm một lớp không đảm bảo lớp đã initialized.
- **Verification:** kiểm tra cấu trúc và các điều kiện hợp lệ của class file/bytecode theo quy tắc JVM.
- **Preparation:** tạo/chuẩn bị static fields và gán giá trị mặc định hoặc giá trị hằng theo luật định, **không phải chạy toàn bộ static initializer Java**.
- **Resolution:** chuyển symbolic references thành các thực thể runtime cụ thể theo luật JVMS.
- **Initialization:** thực hiện quy trình khởi tạo class/interface, bao gồm `<clinit>` nếu có và các yêu cầu về superclass/superinterfaces liên quan.
- **Execution/dispatch:** thực hiện bytecode và chọn implementation của invocation thích hợp tại thời điểm gọi.

**Điểm khác biệt:** resolution một reference tới class **không mặc nhiên gọi `<clinit>`** của class đó. Một class có thể đã được nạp/được dùng để phân giải reference nhưng vẫn chưa chạy static initializer. Ngược lại, một active use có thể yêu cầu initialization và JVM sẽ bảo đảm các tiền điều kiện liên kết cần thiết cho thao tác đó.

## 4. Resolution diễn ra khi nào? Eager và lazy resolution

JVM Specification cho phép sự linh hoạt về chiến lược **khi nào** giải các symbolic references. Một JVM có thể chọn giải sớm một số reference sau khi class được nạp, hoặc **giải muộn khi lệnh cần đến**, miễn là tuân thủ hành vi quan sát được và các ràng buộc báo lỗi của đặc tả.

### 4.1. Không phải toàn bộ constant pool được giải ngay khi load

Giả sử một class có method `unused()` tham chiếu tới một dependency không có trong runtime classpath, nhưng đường chạy hiện tại không dùng tới method đó. Trong một số tình huống và JVM, class có thể nạp hoặc bắt đầu chạy mà chưa cần resolution của symbol trong method không dùng tới. **Không được biến quan sát đó thành lời cam kết cho mọi JVM**, vì VM có thể làm thêm thao tác liên kết/kiểm tra khi thích hợp.

Việc resolution xảy ra sớm/muộn vẫn phải tuân thủ yêu cầu **báo lỗi linking tại một điểm mà việc sử dụng chương trình có thể phát sinh lỗi theo quy tắc JVMS**, thay vì tự ý thay đổi ngữ nghĩa chương trình.

### 4.2. Một symbolic reference được phân giải có cần dò từ đầu mỗi lần dùng?

Về mặt ngữ nghĩa, JVM thực hiện resolution của một symbolic reference theo một định nghĩa runtime cụ thể và ghi nhớ kết quả thành công hoặc lỗi theo các quy tắc tương ứng; **không có yêu cầu mỗi lần thực thi bytecode đều chạy lại thuật toán tìm tên**. Cách cache cụ thể do bản triển khai quyết định.

**Không nhầm cache resolution với cache kết quả method call:** một reference `Animal.speak:()V` có thể được phân giải thành một method đại diện phù hợp, nhưng `invokevirtual` ở những lần gọi khác nhau vẫn cần tìm/chọn implementation theo **runtime class của receiver**.

## 5. Resolution của class và interface symbolic references

Một `CONSTANT_Class` chứa symbolic name biểu thị class/interface hoặc array class. Khi resolution cần xác định runtime type, JVM sử dụng **class loader** thích hợp và thực hiện các ràng buộc liên quan.

### 5.1. Định danh class không chỉ là tên

Với class/interface thông thường, **runtime identity** gắn với cặp **(binary name, defining class loader)**. Hai class cùng tên do **hai defining loaders khác nhau** định nghĩa không tự động là cùng một runtime type.

```text
"org.example.Plugin" + Loader-A  → Runtime type A
"org.example.Plugin" + Loader-B  → Runtime type B

A.getName().equals(B.getName())    → true
A == B                             → false
```

Sơ đồ giả định loader A và B thực sự tự định nghĩa cùng tên. Nếu loader B chỉ *delegate* và lấy chính type của loader A, thì chúng không phải hai runtime definitions độc lập.

**Initiating loader** (loader khởi xướng việc nạp) và **defining loader** (loader thực sự định nghĩa class) có thể khác nhau. Trong resolution, JVM còn có các **loader constraints** để bảo đảm những types xuất hiện trong descriptors tương thích qua ranh giới loaders khi cần bảo toàn tính an toàn kiểu.

### 5.2. Kiểm tra accessibility của class

Một type được tìm thấy chưa chắc **được phép truy cập** từ class đang mang symbolic reference. JVM phải tôn trọng các quy tắc accessibility, module access và mối quan hệ runtime package thích hợp. Trong JVM, một runtime package không chỉ xác định bằng tên package mà còn phụ thuộc defining loader theo quy tắc JVMS.

Do đó resolution có thể phát hiện những vấn đề như:

- Class tồn tại nhưng không accessible đối với bên tham chiếu.
- Class được định nghĩa ở loader khác với kỳ vọng của dependency, làm vỡ định danh hoặc loader constraint.
- Cần một class/interface nhưng không tìm hoặc định nghĩa được theo cơ chế loader hiện tại.

**Không gọi mọi trường hợp trên là `ClassNotFoundException`**: đây là checked exception phổ biến của các API như `Class.forName` và `ClassLoader.loadClass`, trong khi các thao tác bytecode được JVM liên kết thường báo nhóm `LinkageError` hoặc các lỗi tương ứng.

## 6. Resolution của field symbolic references

Tham chiếu tới field thường dựa trên:

```text
CONSTANT_Fieldref
  ├── class/interface chứa symbolic owner
  └── NameAndType:
         fieldName
         fieldDescriptor
```

Khi giải Fieldref, JVM phải tìm field phù hợp qua thuật toán **field lookup** theo quy tắc class/interface và kiểm tra accessibility. Việc tìm field không đơn thuần là lấy public field trên đúng class mang tên ở source; **field có thể được tìm thông qua hierarchy** theo luật JVM.

### 6.1. Ví dụ

```java
class Config {
    static int port = 8080;
}

class Reader {
    static int read() {
        return Config.port;
    }
}
```

Đây là hai class minh họa; cần đặt vào các file thích hợp hoặc một compilation unit hợp lệ để chạy. Bytecode của `Reader.read()` có thể chứa:

```text
getstatic Config.port:I
ireturn
```

Resolution của `Config.port:I` liên quan tới một **field cụ thể**. Nhưng `getstatic` còn yêu cầu field thực sự là **static** và việc truy cập static field có thể kích hoạt **initialization của class/interface khai báo field** theo quy tắc phù hợp. Đây là những kiểm tra/hành vi **khác** với việc tìm field bằng tên/descriptor.

### 6.2. Field resolution khác method resolution

- Field lookup dựa trên tên, descriptor và quy tắc hierarchy của field.
- Method resolution có các thuật toán khác nhau tùy Methodref, InterfaceMethodref và các quan hệ override/default method.
- Trường instance dùng `getfield`/`putfield`, trường static dùng `getstatic`/`putstatic`. Nếu một binary client kỳ vọng field static nhưng bản class runtime đổi thành instance field, có thể xuất hiện **`IncompatibleClassChangeError`**.
- Nếu field đã bị đổi tên, xóa hoặc đổi descriptor, có thể xuất hiện **`NoSuchFieldError`**.

Các lỗi này thường do **mất binary compatibility** khi deploy thư viện khác bản dùng lúc compile, không phải lúc nào cũng có thể phát hiện khi chỉ biên dịch riêng module client.

### 6.3. Compile-time constants và inlining

```java
class Flags {
    static final int LIMIT = 100;
    static int dynamic = compute();

    static int compute() {
        return 25;
    }
}
```

Khi `Flags.LIMIT` là constant variable được compiler inline vào client, bytecode của client có thể **không chứa Fieldref** tới `LIMIT` tại điểm sử dụng: nó chỉ chứa literal 100. Do đó việc sửa giá trị `LIMIT` trong thư viện rồi không biên dịch lại client có thể làm client tiếp tục nhìn thấy **giá trị cũ đã inline**, không phải vấn đề “JVM resolution vẫn cache Fieldref cũ”.

Ngược lại, `Flags.dynamic` cần đọc giá trị static lúc runtime và thường có `getstatic`. Khi chẩn đoán, hãy dùng **`javap -c -v` trên binary client** để xem thực sự có symbolic reference đến field hay không.

## 7. Method resolution: giải symbol chưa phải chọn implementation cuối cùng

Một symbolic method reference chứa **tên class/interface chủ**, **method name** và **method descriptor**.

| Constant-pool entry | Tình huống điển hình |
| --- | --- |
| `CONSTANT_Methodref` | Method của class, dùng với `invokevirtual`, `invokespecial`, `invokestatic` tùy loại |
| `CONSTANT_InterfaceMethodref` | Method của interface, dùng với `invokeinterface` và một số tình huống invocation hợp lệ khác |
| `CONSTANT_NameAndType` | Chứa tên và descriptor phục vụ cả các method/field references |

Ví dụ:

```java
interface Printer {
    String print();
}

class ConsolePrinter implements Printer {
    public String print() {
        return "console";
    }
}
```

Trong một method:

```java
Printer printer = new ConsolePrinter();
System.out.println(printer.print());
```

Bytecode thường chứa:

```text
invokeinterface Printer.print:()Ljava/lang/String;
```

**Resolution** làm rõ symbolic reference tới method của interface theo thuật toán JVMS. Nhưng **lúc gọi**, `invokeinterface` còn phải chọn method triển khai phù hợp với **runtime class của object receiver**, ở đây là `ConsolePrinter`.

### 7.1. Resolution và dynamic dispatch là hai bước logic

```text
Constant pool:
  InterfaceMethodref Printer.print:()Ljava/lang/String;
                   │
                   ▼
      Resolution (interface method reference)
                   │
                   ▼
         invokeinterface thực thi
                   │
              receiver?
                   │
              ConsolePrinter
                   │
                   ▼
      ConsolePrinter.print() → "console"
```

Một method reference đã resolution **không làm cho mọi lệnh gọi virtual/interface về sau bắt buộc trỏ một implementation duy nhất**. Nếu receiver thuộc một subtype khác override method, dispatch có thể chọn implementation khác.

### 7.2. Các opcode invocation

| Instruction | Cơ chế sử dụng thường gặp | Quan hệ với resolution |
| --- | --- | --- |
| `invokestatic` | Gọi static method của class/interface | Cần method reference phù hợp, staticness check và initialization của declaring type khi áp dụng |
| `invokespecial` | Constructor `<init>`, private method, gọi superclass/interface theo ngữ nghĩa đặc biệt | Có các luật tra cứu/chọn method đặc biệt, không dispatch virtual bình thường |
| `invokevirtual` | Gọi instance method class có khả năng override | Resolution symbolic method rồi chọn implementation theo runtime class của receiver |
| `invokeinterface` | Gọi method thông qua interface | Resolution interface method rồi chọn implementation thích hợp ở runtime |
| `invokedynamic` | Liên kết call site bằng bootstrap method | Có cơ chế resolution/liên kết riêng; xem mục 11 |

Nếu source viết `Child c = new Child(); c.run();`, không thể chỉ nhìn chữ `run` để đoán opcode. Trình biên dịch xét kiểu khai báo, static/private/super, interface, overload và các luật ngôn ngữ; kết quả thực tế nên kiểm chứng bằng `javap`.

### 7.3. Return type cũng nằm trong JVM method descriptor

```text
method():Ljava/lang/Object;
method():Ljava/lang/String;
```

Ở **mức ClassFile**, hai descriptor trên khác nhau, dù source Java không cho phép chỉ overload theo kiểu trả về. Các **bridge methods** được compiler tạo để duy trì override/dispatch khi generics bị erase hoặc khi covariant return cần hỗ trợ có thể mang cùng tên, cùng tham số, nhưng **khác return descriptor**. Không được đồng nhất “method cùng tên” với “method reference nhất định resolution được”.

## 8. Resolution có liên quan tới access control và binary compatibility

Giả sử client `C` được compile khi library `L` có method công khai `run()`. Sau đó runtime nạp một bản library đã sửa:

- Method `run()` bị xóa/đổi descriptor → symbolic method reference cũ có thể gặp **`NoSuchMethodError`**.
- Method trở thành không accessible từ client → có thể gặp **`IllegalAccessError`**.
- Kiểu member static/instance không còn phù hợp với instruction trong client → có thể gặp **`IncompatibleClassChangeError`**.
- Library đổi hierarchy, biến một class thành interface hoặc có các ràng buộc cấu trúc khác không tương thích → có thể gặp lỗi linkage thích hợp.
- Lúc thực thi virtual invocation, nếu không thể tìm implementation concrete phù hợp, có thể gặp **`AbstractMethodError`** ở giai đoạn invocation dù các bước resolution liên quan trước đó đã xảy ra.

**Quan trọng:** lỗi ở **resolution**, **invocation**, **loading**, **initialization** có thể gần nhau trong stack trace nhưng không đồng nhất. Một chương trình in ra `NoSuchMethodError` không có nghĩa tự động “JVM tìm nhầm overload ở Java source”; thường là binary đang chạy không khớp ABI/chữ ký mà client đã biên dịch.

## 9. Ví dụ Java 17+ chạy được: symbol, dispatch và initialization

Tạo file **`ResolutionDemo.java`**:

```java
public class ResolutionDemo {
    interface Greeter {
        String greet();
    }

    static class Human implements Greeter {
        static {
            System.out.println("Human initialized");
        }

        @Override
        public String greet() {
            return "hello";
        }
    }

    static class Machine implements Greeter {
        @Override
        public String greet() {
            return "beep";
        }
    }

    static class Service {
        static {
            System.out.println("Service initialized");
        }

        static int count = 5;

        static String message() {
            return "ready";
        }
    }

    static String use(Greeter greeter) {
        return greeter.greet();
    }

    public static void main(String[] args) {
        System.out.println("start");
        System.out.println("class literal: "
                + Service.class.getSimpleName());

        Greeter first = new Human();
        System.out.println("first: " + use(first));

        Greeter second = new Machine();
        System.out.println("second: " + use(second));

        System.out.println("static field: " + Service.count);
        System.out.println("static method: " + Service.message());
        System.out.println("static method again: " + Service.message());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 ResolutionDemo.java
java ResolutionDemo
```

**Standard output chính xác:**

```text
start
class literal: Service
Human initialized
first: hello
second: beep
Service initialized
static field: 5
static method: ready
static method again: ready
```

### Giải thích chi tiết

1. `Service.class.getSimpleName()` sử dụng Class mirror nhưng **không yêu cầu class initialization**, nên không in `Service initialized` trước dòng `class literal: Service`.
2. `new Human()` tạo instance và yêu cầu `Human` được initialized; static initializer của Human in đúng một lần.
3. Lần gọi `use(first)` và `use(second)` đều đi qua symbolic interface method `Greeter.greet:()Ljava/lang/String;` trong bytecode của `use`. Tuy vậy, **receiver runtime class** khác nhau, nên dispatch lần lượt đến `Human.greet()` và `Machine.greet()`.
4. Lần đọc `Service.count` là active use thích hợp và kích hoạt **class initialization** của Service, in `Service initialized` trước khi đọc giá trị 5.
5. Các lời gọi `Service.message()` sau đó không chạy lại `<clinit>`; resolution hoặc việc JVM cache liên kết không có nghĩa phải initialize class lần nữa.
6. Output không phụ thuộc thời gian thực, thread scheduling hay thông báo lỗi cụ thể của JVM.

**Điểm mấu chốt:** bài này dùng source dễ đọc để nhìn được hệ quả của các ranh giới **resolution / initialization / dispatch**. Không thể chỉ dựa vào thứ tự stdout để biết chính xác VM đã resolution từng constant pool entry lúc nào; dùng `javap` để thấy symbols và quy tắc JVMS để suy luận.

### 9.1. Đọc constant pool và bytecode

```bash
javap -c -p ResolutionDemo
javap -c -p 'ResolutionDemo$Human'
javap -c -p 'ResolutionDemo$Service'
javap -v -p ResolutionDemo
```

Nếu dùng Bash hoặc PowerShell, nháy đơn giúp bảo vệ ký tự `$` trong tên nested class; với `cmd.exe` của Windows, dùng nháy kép.

Tìm trong `javap -c -p ResolutionDemo` những mẫu điển hình:

```text
// Trong use(Greeter), mô tả rút gọn:
aload_0
invokeinterface ResolutionDemo$Greeter.greet:()Ljava/lang/String;
areturn

// Trong main, mô tả rút gọn:
ldc           ... ResolutionDemo$Service
invokevirtual ... Class.getSimpleName:()Ljava/lang/String;
...
getstatic     ... ResolutionDemo$Service.count:I
...
invokestatic  ... ResolutionDemo$Service.message:()Ljava/lang/String;
```

Đây là **minh họa**, không phải listing chứa offset/constant pool index chính xác. `javap -v` sẽ giúp quan sát các entry như `Class`, `Fieldref`, `Methodref`, `InterfaceMethodref` và `NameAndType` từ những code paths này.

Bạn có thể tự trả lời:

- `Service.class` có class literal nhưng đã làm `Service` initialized chưa?
- Symbolic owner/method descriptor của `invokeinterface` là gì?
- Có phải mỗi lần `use` đều chọn implementation giống nhau không?
- `Service.count` đọc bằng `getstatic` có hành vi active use gì?
- `Service.message()` có cần resolution và vì sao `<clinit>` không chạy lại?

## 10. Ví dụ binary incompatibility: cùng source client, đổi thư viện runtime

Resolution có giá trị thực tiễn lớn khi **compile một phiên bản, deploy một phiên bản khác**. Ví dụ sau sử dụng **hai bản độc lập** của một class `Api`, để thấy rằng client đã biên dịch vẫn giữ symbolic reference tới descriptor cũ.

**Dùng terminal Bash** (Linux, macOS hoặc Git Bash); trên Windows native, thay dấu `:` của classpath thành `;`.

Tạo cấu trúc thư mục:

```text
resolution-lab/
  api-v1/Api.java
  api-v2/Api.java
  client/Client.java
```

File **`api-v1/Api.java`**:

```java
public class Api {
    public static String message() {
        return "v1";
    }
}
```

File **`api-v2/Api.java`**:

```java
public class Api {
    public static String renamed() {
        return "v2";
    }
}
```

File **`client/Client.java`**:

```java
public class Client {
    public static void main(String[] args) {
        System.out.println(Api.message());
    }
}
```

Tại thư mục `resolution-lab/`:

```bash
mkdir -p out-api1 out-api2 out-client
javac --release 17 -d out-api1 api-v1/Api.java
javac --release 17 -cp out-api1 -d out-client client/Client.java
javac --release 17 -d out-api2 api-v2/Api.java
```

**Chạy với phiên bản thư viện client đã được biên dịch cùng:**

```bash
java -cp out-client:out-api1 Client
```

**Standard output:**

```text
v1
```

Bây giờ giữ nguyên **`Client.class`** nhưng đổi runtime classpath để chỉ nạp Api v2:

```bash
java -cp out-client:out-api2 Client
```

Lúc chạy, lời gọi tới `Api.message:()Ljava/lang/String;` không còn method phù hợp trong bản class `Api` đang được nạp. Kết quả là chương trình **thất bại với `java.lang.NoSuchMethodError`**. Không chép nguyên stack trace ở đây vì cách viết thông báo, dòng trace và chi tiết runtime khác nhau theo JDK.

### 10.1. Vì sao vẫn biên dịch được?

`Client.java` được compile **khi** `Api.message()` còn tồn tại trong `out-api1`. `Client.class` chứa symbolic reference kiểu:

```text
Methodref Api.message:()Ljava/lang/String;
```

Thay runtime library bằng một `Api.class` thiếu method này không sửa lại bytecode của client. Lúc JVM cần liên kết lời gọi, nó tìm method theo **tên và descriptor** trong bản Api đang hiện diện nhưng không thành công.

Quan sát:

```bash
javap -v -p -classpath out-client Client
```

Tìm `Methodref` hoặc lời gọi `invokestatic Api.message:()Ljava/lang/String;`. Dù library v2 chỉ còn `renamed()`, **symbolic reference bên trong client không tự biến thành `renamed()`**.

### 10.2. Nếu biên dịch lại client thì sao?

Giữ `Client.java` cũ và biên dịch với `-cp out-api2`, `javac` sẽ báo lỗi **không tìm thấy symbol `Api.message()`** tại thời điểm biên dịch. Đây là lỗi nguồn-API compile-time, khác với **`NoSuchMethodError`** tại runtime của binary client cũ.

Không cần xoá toàn bộ ứng dụng để chẩn đoán: hãy kiểm tra **bytecode của client**, **bản JAR/class thực sự có trong runtime classpath**, **binary name**, **loader** và **descriptor**.

### 10.3. Biến thể: thay đổi field

Nếu library v1 có `public static int count;` rồi library v2 xóa hoặc đổi field, client cũ có `getstatic Api.count:I` có thể gặp `NoSuchFieldError`. Nếu giữ tên/descriptor nhưng đổi `static` thành instance field, loại lỗi cần xét là **`IncompatibleClassChangeError`**. Đây là các tình huống khác nhau; không nên gộp thành `NoSuchMethodError`.

## 11. Error taxonomy: lỗi xảy ra ở bước nào?

| Triệu chứng/lỗi | Cần kiểm tra điều gì? | Nhóm vấn đề |
| --- | --- | --- |
| `ClassNotFoundException` khi gọi `Class.forName` | Loader/API không tìm thấy class theo tên/đường nạp | Explicit reflective loading/lookup API |
| `NoClassDefFoundError` | Class được JVM cần đến nhưng không lấy/khởi tạo được trong trạng thái hiện tại | Loading/linking/initialization; xem cause |
| `NoSuchFieldError` | Field name/descriptor trong binary client không khớp thư viện runtime | Field resolution |
| `NoSuchMethodError` | Method name/descriptor của symbolic reference không có đích phù hợp | Method resolution/binary compatibility |
| `IllegalAccessError` | Quyền truy cập runtime không phù hợp với reference hiện có | Access check lúc linking |
| `IncompatibleClassChangeError` | Thay đổi dạng class/interface hoặc static/instance hay tương thích nhị phân khác | Linking/invocation |
| `AbstractMethodError` | Không thể tìm concrete implementation thích hợp cho lời gọi | Invocation/dispatch |
| `ExceptionInInitializerError` | Static initializer thất bại theo trường hợp JVM bọc exception | Class initialization, **khác resolution** |
| `BootstrapMethodError` | Bootstrap method cho dynamic linkage/constant thất bại trong trường hợp tương ứng | Dynamic linkage / bootstrap |

Một `NoClassDefFoundError` **không nhất thiết** là không tìm thấy file; class có thể đã lỗi initialization trước đó. Đọc **cause chain, tên class, loader, đường nạp và stack trace**.

**`LinkageError`** là nhóm lỗi rộng; những subtype nêu trên có vị trí ngữ nghĩa khác nhau. `ClassNotFoundException` là checked exception, không phải subtype của `LinkageError`. Giữ phân biệt này khi viết catch clauses hoặc đọc logs.

## 12. `invokedynamic`, method handles và dynamic constants: resolution nâng cao

Từ Java 7 trở lên, định dạng ClassFile và JVM đã có thêm cơ chế liên kết động. Không nên giả định mọi entry constant pool đều là `Fieldref`/`Methodref` truyền thống.

### 12.1. `invokedynamic` và bootstrap method

Instruction **`invokedynamic`** liên quan tới `CONSTANT_InvokeDynamic` và một **bootstrap method**. Khi call site cần liên kết, JVM sử dụng bootstrap information, method types/handles và static arguments thích hợp để tạo ra **call site linkage** theo JVMS.

```text
invokedynamic instruction
    │
    ├── name + method type
    ├── bootstrap method reference
    └── static bootstrap arguments
                │
                ▼
         Bootstrap invocation
                │
                ▼
             CallSite
                │
                ▼
        Invocation target handle
```

Sơ đồ là mô hình logic, không bắt một implementation JVM hiện thực đúng số bước vật lý như hình. `CallSite` và `MethodHandle` thuộc API `java.lang.invoke`; chúng không đơn thuần là `java.lang.reflect.Method`.

Compiler hiện đại có thể dùng `invokedynamic` cho **lambda expressions** và **string concatenation**, tùy phiên bản/chiến lược biên dịch. Ví dụ:

```java
Runnable action = () -> System.out.println("run");
action.run();
```

Đây là **đoạn minh họa trong một method**, có thể tương ứng với `invokedynamic` dùng lambda metafactory, thay vì JVM có instruction đặc biệt tên `lambda`.

### 12.2. `CONSTANT_MethodHandle` và `CONSTANT_MethodType`

- **`CONSTANT_MethodHandle`** mô tả một tham chiếu symbolic tới một trường/phương thức/cơ chế gọi thích hợp; resolution có các quy tắc riêng, bao gồm reference kind và kiểm tra access.
- **`CONSTANT_MethodType`** mô tả kiểu của phương thức ở mức tham số và kết quả; khi giải thành công, JVM có đối tượng kiểu phương thức tương ứng.
- Khi resolution hoặc bootstrap liên quan tới loader/access sai, lỗi sẽ phụ thuộc loại symbolic reference và bước thực tế. Đừng gộp tất cả thành `NoSuchMethodError`.

### 12.3. `CONSTANT_Dynamic` (condy)

**Constant dynamic** cho phép một hằng trong constant pool được tính qua bootstrap mechanism khi liên kết giá trị. Nó khác với static final constant variable inlined đơn giản và khác với việc gọi Java method mỗi lần muốn đọc một hằng.

Không nhất thiết có `ConstantDynamic` trong class file thông thường: tùy JDK/compiler/library sinh bytecode. `javap -v` có thể hiển thị các constant pool entries và `BootstrapMethods` khi class file thực tế chứa cơ chế đó.

**Giới hạn trong bài:** các cách giải `CONSTANT_Dynamic`, `CONSTANT_InvokeDynamic` và method handles có quy tắc đặc biệt về bootstrap, accessibility, type consistency, vòng lặp resolution và lỗi. Để phân tích chính xác một trường hợp, cần đọc đúng mục tương ứng của JVMS §5.4.3 và §6.5 thay vì áp dụng máy móc thuật toán Fieldref/Methodref thông thường.

## 13. Resolution, method selection và JIT optimization: tránh nhầm tầng

Ba bước khái niệm có thể xảy ra quanh một lời gọi:

```text
Source Java            JVM semantics                  Implementation
-----------            -------------                  --------------
a.run()                symbolic method resolution     runtime pool/cache
                       + method selection             dispatch/vtable...
                       + invocation                   interpreter/JIT code
```

- **Resolution** xác định đối tượng mà một symbolic reference biểu thị theo các quy tắc của JVM.
- **Selection/dispatch** xác định method implementation cụ thể trong một lời gọi có tính đa hình. Cùng một resolved symbolic reference có thể dẫn đến implementation khác nhau với receiver khác nhau.
- **JIT** có thể inline, devirtualize hoặc dùng inline cache, nhưng chỉ khi giữ đúng ngữ nghĩa Java/JVM; một optimization không biến một symbolic reference sai thành hợp lệ hay được phép bỏ qua lỗi liên kết bắt buộc.

Địa chỉ máy thật và cấu trúc metadata nội bộ **không thuộc định dạng chuẩn của `CONSTANT_Methodref`**. Class file đem sang CPU khác vẫn chứa symbolic reference đã biên dịch, còn JVM bên đích làm nhiệm vụ nạp và liên kết theo runtime của nó.

### 13.1. Resolution không phải object construction

Giải `CONSTANT_Class` về runtime class không phải tạo instance của class đó. `new SomeClass()` có thể khiến JVM phải load/link/initialize class phù hợp, rồi mới cấp phát đối tượng và chạy constructor `<init>` trong trình tự đúng. Đừng nói “resolution gọi `new`” hoặc “resolution tự chạy constructor”.

### 13.2. Resolution không phải reflection

`Class.forName`, `Method.invoke`, `getDeclaredMethod` là **Java APIs** cho runtime type lookup và reflective access. Chúng có thể dẫn tới loading/linking/initialization/invocation bên dưới, nhưng **không đồng nhất** với instruction-level resolution của một constant pool symbolic reference. Ví dụ lỗi `ClassNotFoundException` của `Class.forName` khác với `NoSuchMethodError` của một binary Methodref.

### 13.3. Resolution không phụ thuộc chỉ duy nhất vào classpath

Classpath/module path ảnh hưởng lớp nào được tìm/định nghĩa, nhưng **class loader hierarchy, module boundaries, package access, descriptors và runtime class identity** cũng rất quan trọng. Hai ứng dụng có cùng file `.class` trên disk có thể hành xử khác khi loader/module arrangement khác.

## 14. Hướng chẩn đoán từng bước khi nghi lỗi resolution

1. **Ghi lại lỗi đầy đủ:** loại `Error`/exception, tên class, method/field, descriptor nếu được in, cause chain và thời điểm phát sinh.
2. **Xác nhận runtime:** `java --version`, classpath/module path, JARs thực tế, version của dependency. Không chỉ nhìn cấu hình IDE hoặc build file.
3. **Đọc ClassFile client:** `javap -v -p -classpath <client-path> Client`; tìm `Methodref`, `InterfaceMethodref`, `Fieldref`, `Class` liên quan.
4. **Đọc ClassFile dependency:** `javap -p -s -classpath <runtime-lib-path> Api`; so tên, descriptor và static/instance status với kỳ vọng client.
5. **Xác nhận class loader khi cần:** log `SomeType.class.getClassLoader()` và `getProtectionDomain().getCodeSource()` khi API/permissions phù hợp; xem quan hệ delegation và module.
6. **Tách resolution khỏi initialization:** lỗi do `<clinit>` không được “sửa” bằng cách đổi descriptor, và lỗi `NoSuchMethodError` không tự hết bằng cách gọi static initializer.
7. **Khôi phục binary compatibility:** dùng bản thư viện phù hợp hoặc biên dịch lại/deploy nhất quán các bên sau khi thay đổi public API/ABI.

Các dấu `<client-path>` và `<runtime-lib-path>` ở mục này là **ký hiệu vị trí đường dẫn**, không phải tên file thật để chép nguyên xi. Với ví dụ hoàn chỉnh mục 10, thay bằng `out-client` và `out-api1` hoặc `out-api2`.

### Các lỗi dễ đưa ra kết luận sai

| Kết luận vội | Sự thật cần kiểm tra |
| --- | --- |
| “Lỗi `NoSuchMethodError` nghĩa là source client không biên dịch.” | Client có thể đã biên dịch với API cũ; lỗi xảy ra khi **binary được liên kết với runtime API khác** |
| “Mỗi method call phải đi tìm lại tên đầy đủ từ disk.” | JVM có thể giữ kết quả resolution; gọi đa hình vẫn có method selection riêng |
| “Resolution xong thì `<clinit>` chắc chắn đã chạy.” | Resolution không mặc nhiên kích hoạt class initialization |
| “`Class.forName` và `CONSTANT_Class` resolution là một hàm API.” | Reflection và JVM symbolic resolution là hai tầng khác nhau |
| “Cùng class name tức cùng runtime type.” | **Defining loader** là một phần định danh class/interface |
| “`Methodref` chỉ cần name, không cần descriptor.” | Descriptor gồm tham số **và return type** của method |
| “JVM phải resolve toàn bộ constant pool ngay trước `main`.” | VM được phép dùng chiến lược eager/lazy theo luật JVMS |
| “JIT inline một lời gọi thì không còn lỗi binary compatibility.” | Optimization phải giữ nguyên các hiệu ứng và lỗi theo ngữ nghĩa |
| “Đổi static field thành instance field không ảnh hưởng binary client.” | Client có opcode `getstatic` sẽ không tương thích với field instance |
| “`invokedynamic` là gọi Methodref class bình thường.” | Nó có **bootstrap/call-site linkage** riêng |

## 15. Bài tập tự kiểm tra

### Bài tập 1 — Đọc một symbolic method reference

Một constant pool entry tương đương:

```text
Methodref: pkg/MathUtil.calc:(II)I
```

**Hỏi:** JVM cần những thành phần gì để resolution?  
**Đáp án:** Runtime class `pkg.MathUtil` phù hợp, method name `calc`, descriptor nhận **hai `int`** và trả **`int`**, cùng các yêu cầu về loader, lookup, accessibility và tính tương thích áp dụng. Không thể chỉ tìm một method tên `calc` bất kỳ.

### Bài tập 2 — Vì sao overload không gây lẫn?

Class có:

```java
static int parse(String text) { return 1; }
static int parse(int number)  { return 2; }
```

Đây là **hai method minh họa** trong cùng class. Cặp descriptors khác nhau: `(Ljava/lang/String;)I` và `(I)I`; JVM method references phân biệt được. Lựa chọn overload ở **compile time của Java source**, còn resolution giải symbol mang descriptor đã được ghi trong binary.

### Bài tập 3 — Resolution hay initialization?

Source Java đọc:

```java
int x = Settings.value;
```

nơi `Settings.value` là một static field **không phải compile-time constant variable**.  
**Đáp án:** JVM cần liên kết Fieldref `Settings.value` theo luật, và việc `getstatic` có thể yêu cầu **class initialization** trước khi đọc. Đây là hai trách nhiệm khác nhau.

### Bài tập 4 — Một method symbol, hai receiver

```java
Printer a = new ConsolePrinter();
Printer b = new NetworkPrinter();
a.print();
b.print();
```

Giả sử cả hai classes triển khai `Printer`.  
**Hỏi:** `invokeinterface` có nhất thiết gọi cùng implementation không?  
**Đáp án:** Không. Cùng symbolic interface method có thể dispatch khác theo runtime class của `a` và `b`.

### Bài tập 5 — Phiên bản library sai

Client compiled với `Api.message:()Ljava/lang/String;`, nhưng runtime `Api` chỉ có `Api.renamed:()Ljava/lang/String;`.  
**Đáp án:** Khi JVM cần liên kết lời gọi, có thể gặp **`NoSuchMethodError`**. Đổi classpath để dùng binary tương thích hoặc biên dịch lại client sau khi cập nhật source.

### Bài tập 6 — Hai loaders

Loader A và Loader B định nghĩa cùng binary name `example.Model` độc lập.  
**Hỏi:** JVM có coi hai runtime classes là một chỉ vì tên giống nhau?  
**Đáp án:** Không. Runtime identity của class/interface phụ thuộc **binary name + defining class loader**.

### Bài tập 7 — Compile-time constant

`static final int LIMIT = 9;` là compile-time constant variable. Source client in `Config.LIMIT`.  
**Hỏi:** Bytecode client luôn chứa `getstatic Config.LIMIT:I`?  
**Đáp án:** Không. Compiler có thể **inline số 9**; phải dùng `javap` để biết binary thực sự chứa instruction gì.

### Bài tập 8 — Dynamic call sites

**Hỏi:** `invokedynamic` có cần symbolic class method reference thông thường mang đủ method implementation cuối cùng?  
**Đáp án:** Không theo mô hình đó. Nó sử dụng **bootstrap method linkage** để liên kết call site và method type; cơ chế khác với giải một `Methodref` rồi gọi static method.

## 16. Tóm tắt và tài liệu tra cứu

- **Symbolic reference** cho biết một class, field, method hoặc kiểu động được nói đến theo **tên, descriptor và constant-pool structures**.
- **Resolution** xác định runtime target theo các quy tắc của JVM, gồm loading khi cần, lookup, access/loader constraints và báo lỗi thích hợp.
- **Timing** có thể eager hoặc lazy; đừng dựa vào một thứ tự resolve duy nhất cho mọi JVM.
- **Method resolution ≠ dynamic dispatch**; virtual/interface invocation còn chọn implementation dựa trên receiver.
- **Resolution ≠ initialization**; `<clinit>` và active use là một trách nhiệm khác của JVM.
- **Binary compatibility** phụ thuộc đúng tên, descriptor, staticness, accessibility và các ràng buộc runtime; đổi thư viện mà không cập nhật client có thể gây `LinkageError`.
- **`invokedynamic`/`CONSTANT_Dynamic`** có bootstrap linkage đặc thù, không thể quy hết thành Fieldref/Methodref cổ điển.

Tài liệu chuẩn:

- *The Java Virtual Machine Specification*, **§5.3 Loading, §5.4 Linking, §5.4.3 Resolution, §5.5 Initialization**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html
- *JVMS* **§4.4 Constant Pool** và các loại `CONSTANT_*`: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html
- *JVMS* **§6.5 Instructions** — `getstatic`, `getfield`, `invokevirtual`, `invokeinterface`, `invokestatic`, `invokespecial`, `invokedynamic`: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html

**Kết luận:** Khi bytecode của một class nói “gọi `Api.message:()Ljava/lang/String;`”, nó đang mang **một hợp đồng ký hiệu**, không phải địa chỉ method được đóng vào class file. **Resolution** là cầu nối từ hợp đồng ấy đến thế giới runtime với tên class, defining loader, quyền truy cập và descriptors cụ thể. Khi đã tách được resolution khỏi initialization và dispatch, việc đọc `javap` và xử lý các lỗi binary compatibility trở nên rõ ràng, có thể kiểm chứng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="linking-and-initialization-006">Phân giải sớm, phân giải muộn và thời điểm lỗi liên kết xuất hiện</a>

<details>
<summary>Xem chi tiết</summary>

# Phân giải sớm, phân giải muộn và thời điểm lỗi liên kết xuất hiện

## 1. Mục tiêu: JVM được phép giải quyết tham chiếu lúc nào?

Khi Java source gọi `lib.Library.version()`, class file của chương trình gọi thường không chứa một địa chỉ native cố định trỏ thẳng tới code của phương thức đó. Thay vào đó, các bytecode như `invokestatic`, `getfield`, `new`, `checkcast`… có thể sử dụng **symbolic references** trong run-time constant pool. JVM phải **resolve** những tham chiếu này thành class, field, method hoặc giá trị runtime phù hợp trước khi thực hiện thao tác cần đến chúng.

**Câu hỏi chính của bài** không phải “JVM có phân giải tham chiếu không?” mà là:

- JVM có thể **phân giải sớm (eager resolution)** trong lúc liên kết, trước khi chương trình thực sự gọi một method?
- JVM có thể **phân giải muộn (lazy resolution)** ngay khi bytecode liên quan được sử dụng?
- Nếu một tham chiếu không hợp lệ, ví dụ method bị xóa khỏi thư viện sau khi client đã biên dịch, **lỗi được phép xuất hiện ở thời điểm nào**?
- Vì sao thấy ứng dụng đã in ra một số dòng hoặc `main` chạy được **không chứng minh** mọi symbolic reference của ứng dụng đã được resolve thành công?

Theo **JVMS `5.4** và **`5.4.3**, JVM được quyền chọn một số thời điểm thực hiện resolution, nhưng **không được tùy ý ném lỗi liên kết tại điểm hoàn toàn không liên quan với hành động của chương trình có thể cần liên kết**. Có những quy tắc chặt hơn cho **dynamically-computed constants** và **dynamically-computed call sites**, sẽ bàn riêng.

### Một bản đồ tổng quan

~~~text
Java source
   |
   | javac
   v
ClassFile
   |    constant pool: Methodref, Fieldref, Class, NameAndType, ...
   |    Code: invokestatic #N, getstatic #M, new #K ...
   v
JVM class loading
   v
Linking
   +-- Verification (kiểm tra hợp lệ)
   +-- Preparation (chuẩn bị static storage/default values)
   +-- Resolution (phân giải symbolic refs) <-- linh hoạt về thời điểm*
   v
Initialization (static field initializers / <clinit> khi cần)
   v
Execution of methods / instructions
      \-- có thể tiếp tục resolve những refs chưa sử dụng*

* Có giới hạn và trường hợp đặc biệt theo JVMS.
* Lỗi phát hiện trong linkage phải được ném ở điểm chương trình có hành động
  có thể trực tiếp hoặc gián tiếp yêu cầu linkage tương ứng.
~~~

Đừng đọc sơ đồ như một thứ tự cứng “phải resolve toàn bộ trước initialize”. **Load xong, verify và prepare xong trước initialization**, nhưng một số symbolic references có thể chỉ được resolve **sau khi class đã initialized và khi method liên quan chạy**.

## 2. Phân biệt loading, linking, resolution và initialization

| Khái niệm | Điều VM làm | Không đồng nghĩa |
| --- | --- | --- |
| **Loading** | Tìm/tạo runtime class hoặc interface từ định nghĩa nhị phân và loader | Chạy `static {}` |
| **Verification** | Kiểm tra binary structure, bytecode constraints và type-safety rules | Thực thi từng nhánh Java code |
| **Preparation** | Tạo storage và giá trị mặc định cho static fields; xử lý quy tắc constant phù hợp | Thực thi mọi biểu thức `static int x = f()` |
| **Resolution** | Chuyển **symbolic reference** thành đối tượng/giá trị runtime cụ thể, kiểm tra liên quan | Gọi thân method được resolve |
| **Initialization** | Thực thi initialization logic của class/interface khi được yêu cầu | Resolve tất cả constant-pool entries |
| **Execution** | Thực thi bytecode, tạo object, gọi method, đọc field… | Chỉ đơn thuần đọc `.class` |

Ở cấp class file, các tham chiếu có thể trông như:

~~~text
#11 = Class        #12                // lib/Library
#12 = Utf8         lib/Library
#13 = NameAndType  #14:#15            // version:()Ljava/lang/String;
#16 = Methodref    #11.#13            // lib/Library.version:()Ljava/lang/String;

Code:
   ... invokestatic #16              // Method lib/Library.version:()Ljava/lang/String;
~~~

Đây là **sơ đồ minh họa constant pool**: các chỉ số `#11`, `#16` có thể khác ở class thực do compiler tạo. **Methodref** chứa owner, method name và descriptor, **không phải địa chỉ máy đã cố định lúc biên dịch source**. Resolution cần xác định xem ở runtime có method thích hợp và các yêu cầu truy cập, loại lệnh gọi có hợp lệ không.

Một phương thức có symbolic reference tới `lib.Library.version()` **không cần thực sự invoke method đó** chỉ vì VM đã nhận diện class file chứa lời gọi. Ngược lại, khi thực thi bytecode invoke, các yêu cầu resolution và instruction-specific linking checks phải được thỏa.

## 3. Early/eager resolution là gì?

**Eager resolution** nghĩa là JVM chọn **phân giải trước** một hoặc nhiều symbolic references của class đang được liên kết, chẳng hạn trong quá trình verification, thay vì đợi lần đầu lệnh bytecode trực tiếp dùng tham chiếu đó.

Ví dụ: một class có method `run()` chứa symbolic reference tới `A.m:()V` nhưng nhánh gọi `A.m()` có thể chưa được chương trình đi qua. Một implementation có thể phân giải reference liên quan sớm theo giới hạn đặc tả.

Lợi ích tiềm năng:

- Phát hiện một số vấn đề liên kết trước khi đường lệnh thực sự chạy.
- Chuẩn bị/caching kết quả resolution, khiến lần dùng sau có ít việc phải làm hơn.
- Phù hợp chiến lược implementation/verifier hoặc tối ưu runtime cụ thể.

Giới hạn:

- **Không thể tùy tiện ném lỗi đã phát hiện sớm tại một chỗ vô can.** JVMS ràng buộc điểm **throw** cho lỗi linkage.
- Không phải mọi reference đều được phép eager theo cùng cách; **`CONSTANT_Dynamic` và `invokedynamic`** có hạn chế riêng.
- “Eager” ở đây nói về **thời điểm resolve symbolic references**, không đồng nghĩa **eager loading tất cả class của classpath**, chạy mọi `static initializer` hay gọi các method sớm.
- Không nên gắn nhãn “HotSpot luôn eager” hoặc “mọi JVM luôn eager” chỉ dựa trên một log ngắn; một JVM có thể dùng nhiều chiến lược theo loại reference và hoàn cảnh.

## 4. Late/lazy resolution là gì?

**Lazy resolution** nghĩa là VM để symbolic reference ở trạng thái chưa resolve cho đến khi một hành động cụ thể thực sự cần nó, ví dụ khi một bytecode `invokestatic`, `getstatic`, `new` hoặc `checkcast` có tham chiếu tương ứng được sử dụng.

~~~text
Constant pool của Caller:
  #A = Methodref lib/Library.version:()Ljava/lang/String;

Code của Caller:
  if (someCondition) {
      invokestatic #A
  }

Nếu không đi vào nhánh:
  Có JVM có thể chưa resolve #A (lazy).
  Có JVM có thể đã thử resolve sớm #A (eager),
  nhưng phải tuân thủ quy tắc ném lỗi ở điểm sử dụng có liên quan.

Nếu đi vào nhánh:
  Lệnh invoke phải có resolution thành công và checks hợp lệ,
  hoặc lời gọi sẽ thất bại theo quy tắc liên kết.
~~~

**Không đồng nhất “lười phân giải” với “không cần phân giải”.** Những bytecode dùng symbolic reference không thể bỏ qua các kiểm tra type, access và tính đúng đắn. Một số hành vi thực tế có thể được VM tối ưu và cache, nhưng kết quả quan sát phải giữ đúng contract.

### Tham chiếu nào thường liên quan resolution?

JVMS `5.4.3 liệt kê các instruction như:

- **Class/interface**: `new`, `anewarray`, `checkcast`, `instanceof`, `multianewarray`…
- **Field**: `getfield`, `putfield`, `getstatic`, `putstatic`.
- **Methods**: `invokevirtual`, `invokespecial`, `invokestatic`, `invokeinterface`.
- **Constants and dynamic linkage**: `ldc`, `ldc_w`, `ldc2_w` và `invokedynamic` trong những trường hợp thích hợp.

Ngoài ra, một symbolic reference trong constant pool được dùng bởi method handle, method type hoặc bootstrap machinery có quy tắc resolution riêng.

Cần phân biệt **resolution của symbolic method reference** và **dynamic method dispatch**: sau khi một method reference được resolve, `invokevirtual` vẫn cần tìm method body thích hợp theo runtime class của receiver và các quy tắc lookup/selection. Còn `invokedynamic` tạo và liên kết call site theo cơ chế bootstrap riêng. Ba hoạt động không phải một.

## 5. Quy tắc then chốt: lỗi liên kết được ném khi nào?

JVMS `5.4 nêu các điều kiện triển khai phải giữ khi chọn thời điểm các hoạt động linking:

1. Một class/interface phải được **load hoàn chỉnh** trước khi được link.
2. Class/interface phải được **verify và prepare hoàn chỉnh** trước khi được initialize.
3. **Lỗi phát hiện trong linkage** phải được ném tại một **điểm chương trình thực hiện hành động** có thể trực tiếp hoặc gián tiếp cần linkage với class/interface liên quan đến lỗi.
4. Với một số dynamic constant/call-site references, đặc tả hạn chế **thời điểm được phép bắt đầu resolution**.

Đối với resolution, JVMS `5.4.3 còn khẳng định: dù JVM resolve references sớm hay muộn theo chiến lược nào, lỗi resolution phải xuất hiện **tại điểm chương trình có thể trực tiếp hoặc gián tiếp sử dụng symbolic reference gây lỗi**.

### “Phát hiện sớm” không có nghĩa “ném bất kỳ lúc nào”

Giả sử bytecode có một Methodref không tồn tại trong bản thư viện runtime:

- VM có thể **biết reference sai** trước khi dòng code ứng dụng thực sự gọi phương thức đó.
- Nhưng VM phải bảo toàn điều kiện ném lỗi khi chương trình thực hiện hành động **có liên quan và có thể cần** resolution.
- Không được kết luận “nếu eager thì ngay khi JVM process vừa start phải ném `NoSuchMethodError` cho mọi method chưa dùng”.
- Cũng không được kết luận “nếu lazy thì lỗi luôn xuất hiện chính xác ở dòng Java viết lời gọi”. JVM có loading/verification/inlining, các yêu cầu liên kết gián tiếp và startup activities; vị trí quan sát có thể phức tạp hơn source line tưởng tượng.

Vì vậy từ ngữ cần dùng là **“điểm hành động của chương trình có thể trực tiếp hoặc gián tiếp cần liên kết”**, không phải “chắc chắn đến khi opcode cụ thể được thực thi mới có thể báo lỗi” cho mọi hình thức liên kết.

### Ví dụ đối lập

| Kết luận sai | Cách hiểu theo JVMS |
| --- | --- |
| “Eager resolution bắt buộc throw lỗi ở class load.” | VM có thể phát hiện sớm nhưng phải trì hoãn điểm **throw** đúng quy tắc. |
| “Lazy resolution nghĩa không có lỗi trong linking.” | Lỗi vẫn có thể xuất hiện khi reference được sử dụng. |
| “Đã chạy được `main` nên mọi constant-pool method/field references đều hợp lệ.” | Có refs có thể chưa được dùng; lỗi có thể xuất hiện ở đường chạy về sau. |
| “Lỗi Methodref chỉ xảy ra khi method body bên đích bắt đầu chạy.” | Resolution/instruction checks có thể thất bại **trước khi** method đích được invoke. |
| “Line number của stack trace là thời điểm duy nhất JVM thực sự resolve.” | Stack trace giúp định vị error exposure; không chắc phản ánh thời điểm VM phát hiện/resolve bên trong. |

## 6. Ví dụ runnable Java 17+: quan sát đường có dùng và không dùng tham chiếu

Tạo file **`ResolutionBasics.java`**:

~~~java
public class ResolutionBasics {
    static class Helper {
        static int twice(int n) {
            return n * 2;
        }
    }

    static int execute(boolean useHelper) {
        if (!useHelper) {
            return -1;
        }
        return Helper.twice(21);
    }

    public static void main(String[] args) {
        System.out.println("skip=" + execute(false));
        System.out.println("call=" + execute(true));
    }
}
~~~

Lệnh:

~~~bash
javac -g ResolutionBasics.java
java ResolutionBasics
javap -c -v -p ResolutionBasics
~~~

**Stdout do chương trình Java in trước khi lệnh `javap` chạy:**

~~~text
skip=-1
call=42
~~~

Tại sao? `execute(false)` trả `-1` mà không đi qua nhánh gọi `Helper.twice`; `execute(true)` gọi `Helper.twice(21)` và nhận 42. Trong `javap -c -v -p`, tìm Methodref hoặc liên kết tới `ResolutionBasics$Helper.twice:(I)I` và opcode `invokestatic` trong method `execute`.

**Giới hạn của ví dụ:** đây là **hành vi điều khiển của source và bytecode**, không phải thí nghiệm đủ để biết JVM đã resolve Methodref này **lúc nào**. Output không chứa “timestamp resolution” và không buộc JVM lazy thay vì eager. Kết luận đúng là chương trình **chỉ gọi thân `twice` trên nhánh true**; resolution nội bộ vẫn tuân thủ JVMS.

## 7. Ví dụ trọng tâm: thay đổi binary API sau khi client được biên dịch

Đây là cách trực quan chứng minh một class client có thể chứa symbolic reference hợp lệ **lúc compile** nhưng không còn giải được **trên runtime thư viện khác**. Bài dùng Java 17+ và JDK tools chuẩn; tạo ba file có đường dẫn đúng như cấu trúc:

~~~text
resolution-lab/
  src/
    v1/
      lib/
        Library.java
    v2/
      lib/
        Library.java
    client/
      LinkageDemo.java
  out/                 (thư mục output, ban đầu rỗng)
~~~

### 7.1. Thư viện phiên bản 1: có method `version()`

File **`src/v1/lib/Library.java`**:

~~~java
package lib;

public class Library {
    public static String version() {
        return "v1";
    }
}
~~~

File **`src/client/LinkageDemo.java`**:

~~~java
public class LinkageDemo {
    public static void main(String[] args) {
        System.out.println("start");

        if (args.length == 0) {
            System.out.println("skipped");
            return;
        }

        try {
            System.out.println("value=" + lib.Library.version());
        } catch (NoSuchMethodError error) {
            System.out.println("linkage=NoSuchMethodError");
        }
    }
}
~~~

Biên dịch **v1 và client cùng lúc** (đứng ở thư mục `resolution-lab`):

~~~bash
mkdir -p out
javac -g -d out src/v1/lib/Library.java src/client/LinkageDemo.java

java -cp out LinkageDemo
java -cp out LinkageDemo call
~~~

**Stdout của lần chạy thứ nhất, không có argument:**

~~~text
start
skipped
~~~

**Stdout của lần chạy thứ hai:**

~~~text
start
value=v1
~~~

Câu lệnh `LinkageDemo` đã được biên dịch dựa vào `lib.Library.version:()Ljava/lang/String;`. Khi v1 hiện diện trên classpath, method resolution thành công và invocation trả chuỗi `v1`.

**Windows PowerShell:** có thể thay `mkdir -p out` bằng `New-Item -ItemType Directory -Force out`; ba lệnh `javac`/`java` còn lại có cùng ý nghĩa. Không dùng hai phiên bản Library cùng lúc trong một output directory khi đang so sánh; bước sau **cố tình thay thế** `Library.class`.

### 7.2. Thư viện phiên bản 2: xóa `version()` khỏi API

File **`src/v2/lib/Library.java`**:

~~~java
package lib;

public class Library {
    public static String release() {
        return "v2";
    }
}
~~~

Bây giờ **chỉ biên dịch v2 vào cùng thư mục output**, không biên dịch lại client:

~~~bash
javac -g -d out src/v2/lib/Library.java

java -cp out LinkageDemo
java -cp out LinkageDemo call
~~~

`out/lib/Library.class` được ghi đè thành bản v2, nhưng `out/LinkageDemo.class` vẫn chứa Methodref **`lib/Library.version:()Ljava/lang/String;`** từ lúc compile với v1.

**Stdout trong môi trường JDK 17+ thông thường (với đúng file và classpath như mô tả), lần chạy không argument:**

~~~text
start
skipped
~~~

**Stdout khi đi vào nhánh gọi method:**

~~~text
start
linkage=NoSuchMethodError
~~~

Diễn giải:

1. **`start`** được in bởi code client không cần lời gọi Library.
2. Nhánh không argument **không sử dụng `Library.version()`**; không có lời gọi method này.
3. Nhánh có argument cố invoke Methodref của v1. Ở runtime, class `lib.Library` phiên bản 2 **không khai báo** method `version()` có descriptor phù hợp. Method resolution thất bại bằng **`NoSuchMethodError`**.
4. `catch (NoSuchMethodError error)` bắt lỗi và chủ động in dòng cố định, vì vậy ví dụ tránh phụ thuộc thông báo stack trace vốn thay đổi theo JDK.

**Quy tắc quan trọng:** ví dụ chỉ nhằm mô tả một **đường sử dụng tham chiếu gây lỗi** và kết quả thực hành điển hình; không chứng minh HotSpot thực hiện “mọi Methodref resolution đều lazy”. JVMS có quyền lựa chọn eager/lazy cho những references phù hợp nhưng phải ném lỗi tại điểm hành động có thể cần resolution. Đừng suy từ việc in dòng `start` rằng JVM “chưa hề nhìn thấy” constant-pool reference.

### 7.3. Vì sao biên dịch lại client có thể đổi loại lỗi?

Nếu chạy:

~~~bash
javac -g -cp out -d out src/client/LinkageDemo.java
~~~

khi trong `out` chỉ còn Library v2 (không có `version()`), compiler Java sẽ nhận biết không tìm được `version()` trong `lib.Library` và báo **compile-time error**. Đây **không phải** `NoSuchMethodError` ở runtime.

Hai trường hợp khác nhau:

- **Compile-time error:** compiler có thông tin API mới và phát hiện source client gọi method không tồn tại.
- **Runtime linkage error:** client **đã biên dịch** theo API cũ, còn runtime class file của thư viện không còn đáp ứng tham chiếu đã ghi trong client binary.

Trong production, kiểu lỗi này thường do dependency/JAR version mismatch hoặc deployment classpath có phiên bản thư viện không đồng nhất.

### 7.4. Đọc bằng `javap` để xác minh

~~~bash
javap -v -p -classpath out LinkageDemo
javap -c -p -classpath out LinkageDemo
javap -p -classpath out lib.Library
~~~

Điều cần tìm:

- `LinkageDemo.class` vẫn có Methodref dạng `lib/Library.version:()Ljava/lang/String;`.
- Ở nhánh dùng thư viện, bytecode client chứa `invokestatic` tham chiếu Methodref ấy.
- `lib.Library` của v2 chỉ khai báo `release()` mà **không có `version()`**.
- Những chỉ số constant-pool `#N`, offsets BCI và dòng mã trong `javap` phụ thuộc compiler; **đọc nội dung biểu tượng**, không học thuộc con số `#17`.

## 8. Vì sao không nên đo thời điểm resolution bằng stdout đơn lẻ?

Mọi dòng `System.out.println` chỉ chứng minh **hành động Java quan sát được** đã diễn ra. Nó không ghi được tất cả event nội bộ của VM. Để nghiên cứu early vs late resolution, cần phân biệt:

| Câu hỏi | Có thể rút ra từ stdout ví dụ? |
| --- | --- |
| Nhánh có `Library.version()` đã được thực thi chưa? | Có, nhờ output và code |
| Với v2, lời gọi method đó còn hợp lệ không? | Không; `NoSuchMethodError` khi nhánh dùng reference |
| JVM đã scan constant pool lúc load chưa? | Không thể kết luận |
| Symbolic reference đã được eager-resolve rồi giữ error để ném lúc dùng? | Không thể kết luận chỉ từ stdout |
| JVM có cache kết quả resolution? | Đây là cơ chế theo quy tắc JVMS; không đo bằng stdout đơn giản |
| Cấu hình/module/loader khác có làm fail ở hành động khác không? | Có thể, phải điều tra ngữ cảnh runtime |

Không cần dựng một microbenchmark “đếm dòng trước lỗi” rồi khẳng định đặc tả yêu cầu VM phải lazy. Điểm cần nắm là **quyền linh hoạt về thời điểm xử lý và ràng buộc về thời điểm biểu hiện lỗi**.

## 9. Những loại lỗi resolution/linkage đáng nhận biết

| Error / exception | Phân loại | Tình huống tiêu biểu |
| --- | --- | --- |
| **`NoSuchMethodError`** | `IncompatibleClassChangeError` → `LinkageError` | Client đã compile gọi method nhưng runtime class thiếu method tương ứng |
| **`NoSuchFieldError`** | `IncompatibleClassChangeError` → `LinkageError` | Field name/descriptor được tham chiếu không tìm thấy |
| **`IllegalAccessError`** | `IncompatibleClassChangeError` → `LinkageError` | Access check thất bại khi liên kết/thực thi |
| **`IncompatibleClassChangeError`** | `LinkageError` | Runtime class/interface/member shape không phù hợp kỳ vọng bytecode, gồm mismatch static/instance |
| **`AbstractMethodError`** | `IncompatibleClassChangeError` → `LinkageError` | Method được invoke nhưng không có implementation phù hợp tại điểm selection/invocation theo quy tắc |
| **`NoClassDefFoundError`** | `LinkageError` | VM không thể tìm/định nghĩa class cần thiết ở runtime hoặc class đã bị đánh dấu initialization erroneous |
| **`VerifyError`** | `LinkageError` | Bytecode vi phạm yêu cầu verifier |
| **`ClassFormatError`** | `LinkageError` | Binary class file có định dạng không hợp lệ |
| **`BootstrapMethodError`** | `LinkageError` | Lỗi liên quan quá trình bootstrap dynamic constants/`invokedynamic` |
| **`ExceptionInInitializerError`** | `LinkageError` | Static initializer thất bại trong quá trình **initialization**, không phải là lỗi method resolution thông thường |

Cũng cần biết:

- **`ClassNotFoundException`** là **checked exception** từ một số API tải class tường minh như `Class.forName`/`ClassLoader.loadClass`, **không phải** subclass của `LinkageError`.
- **`ClassCastException`** là runtime type-check failure, có thể liên quan hai class loaders định nghĩa cùng tên nhưng **không tự chứng minh** loader constraint violation.
- **`OutOfMemoryError`** trong quá trình liên kết/cấp phát là khả năng riêng, không mặc định là “thiếu method trong constant pool”.

### Lỗi method vs class initialization

Nếu method **có tồn tại** và resolution thành công, nhưng khi JVM cần initialize owner class thì một `static { ... }` ném exception, caller có thể thấy **`ExceptionInInitializerError`**. Đây là lỗi **giai đoạn initialization**, khác với `NoSuchMethodError` do resolution không tìm ra method.

Đừng gom “mọi lỗi xuất hiện khi gọi phương thức” thành “resolution failure”. Giao dịch invoke có thể gồm resolution, access checks, class initialization, dynamic method selection và thực thi body; lỗi có thể đến từ các tầng khác nhau.

## 10. Sticky resolution failures: cùng tham chiếu, kết quả có được thử lại không?

Theo **JVMS `5.4.3**, với các symbolic references thông thường được mô tả ở mục này:

- Nếu **resolution thành công**, những lần resolve sau của **cùng symbolic reference** thành công theo kết quả đã ghi nhận; không yêu cầu tìm lại mọi thứ từ đầu.
- Nếu **resolution thất bại**, những lần resolve sau **cùng symbolic reference** tiếp tục thất bại với **cùng error** theo quy tắc đặc tả.
- Đừng suy rằng ứng dụng có thể “sửa classpath” hoặc thay đổi access configuration rồi JVM sẽ tự động liên kết lại một symbolic reference đã thất bại trong class hiện hữu.

Đây là đặc điểm quan trọng của **binary linkage stability**. Trong nhiều hệ plugin/hot reload, muốn đổi thế hệ type có thể phải dùng **class loader mới** hoặc khởi động lại vùng runtime phù hợp thay vì hy vọng JVM tự làm mới toàn bộ liên kết của class đã được load.

**Ngoại lệ cần nêu rõ:** `invokedynamic` có quy tắc kết quả **gắn với instruction site** riêng, dù các instructions có thể chỉ cùng một constant-pool entry. Dynamic constants và dynamic call sites cũng có semantics bootstrap/caching riêng. Không lấy quy tắc caching của một loại symbolic reference rồi áp máy móc cho mọi loại reference.

## 11. Giới hạn riêng: dynamic constants và `invokedynamic`

Không phải mọi symbolic reference có thể được eager-resolve tùy ý. Theo JVMS `5.4:

1. **Dynamically-computed constant** (`CONSTANT_Dynamic`): không được resolve trước khi một lệnh `ldc`/`ldc_w`/`ldc2_w` sử dụng nó được thực thi, **hoặc** nó được dùng như static argument cho một bootstrap method đang được invoke.
2. **Dynamically-computed call site** (`CONSTANT_InvokeDynamic`): không được resolve trước khi một bootstrap method cần đến call site ấy dưới vai trò static argument được invoke (theo diễn đạt điều kiện đặc tả). Đối với chính opcode `invokedynamic`, resolution của call site diễn ra theo cơ chế bootstrap khi instruction yêu cầu.
3. Một bootstrap method có thể liên quan `MethodHandle`, `MethodType`, static bootstrap arguments và tạo một `CallSite` hay constant value.
4. Lỗi bootstrap có thể được bọc thành **`BootstrapMethodError`** trong những điều kiện quy định; không nên cho rằng cứ có `invokedynamic` thì mọi lỗi đều là `NoSuchMethodError`.

**Ví dụ Java source:** phép nối chuỗi trên JDK hiện đại thường được `javac` biên dịch qua `invokedynamic` và `StringConcatFactory`. Lambda cũng thường dùng cơ chế bootstrap của invokedynamic, nhưng cách compiler sinh bytecode là chi tiết phụ thuộc bản JDK.

~~~java
public class DynamicExample {
    public static void main(String[] args) {
        int count = 3;
        System.out.println("count=" + count);
    }
}
~~~

~~~bash
javac DynamicExample.java
java DynamicExample
javap -v -c -p DynamicExample
~~~

**Stdout của chương trình:**

~~~text
count=3
~~~

Trong disassembly của `javac` Java 17+ thông thường, bạn có thể thấy `invokedynamic` cho phép nối chuỗi và `BootstrapMethods` attribute. Không dùng output này để suy rằng tất cả constant-pool entries được eager resolved ở startup; cách bootstrap được gọi phải theo quy tắc specific call site.

## 12. Phân giải, quyền truy cập và class loader: lỗi có thể xuất hiện muộn

Symbolic references không chỉ lưu tên method. Resolution phải xét **đúng runtime class identity**, loader và quyền truy cập. Một class `api.Message` do loader A định nghĩa **không tự động giống** class cùng tên do loader B định nghĩa.

Khi hai class thuộc loaders khác nhau tương tác qua method/field signatures, **loading constraints** có thể ràng buộc các cách hiểu tên class/interface để bảo vệ type safety. Nếu ràng buộc thất bại, JVM có thể ném **`LinkageError`** trong quá trình linking. Không nhầm:

- **`NoSuchMethodError`**: tên và descriptor method client mong đợi không còn có trong runtime type cần được resolve.
- **`IllegalAccessError`**: symbolic reference có thể xác định được thành viên nhưng access check không hợp lệ.
- **Loader constraint violation**: type names xuyên loader phải đồng nhất nhưng lại dẫn đến những runtime class identities không tương thích.
- **`ClassCastException`**: runtime object type không tương thích với type cast, có thể xảy ra sau khi việc load hai lớp đã thành công hợp lệ.

**Thời điểm lỗi phụ thuộc hành động resolution/linkage và deployment.** Từ thông báo lỗi nên bắt đầu điều tra **owner class, loader, name, descriptor và phiên bản binary**, chứ không phỏng đoán “chắc chắn do lazy resolution” chỉ vì lỗi phát sinh sau vài phút chạy.

### Tình huống plugin thực tế

Host compile với `api.Service.send(Message)` v1. Plugin runtime dùng `api.Service` v2 thay đổi descriptor hoặc vô tình tự định nghĩa `api.Message` bằng loader riêng. Các lớp có thể load nhưng một đường invoke/interface-resolution hoặc override checks sẽ thất bại.

Cách phân tích:

1. Đọc `Class.getClassLoader()` và `Class.getModule()` của các runtime classes liên quan.
2. Kiểm tra `javap -s -p` để so method descriptors giữa client/library.
3. Kiểm tra JAR version trên runtime classpath/module path và chính sách delegation.
4. Chọn shared loader cho API types có mặt trong signatures, tránh bundle trùng API trong child-first plugin.
5. Thử lại ở môi trường kiểm thử có thể tái tạo, không “fix” bằng cách cast cưỡng ép.

## 13. Quy trình chẩn đoán lỗi liên kết: từ stack trace tới bytecode

Một workflow có thể áp dụng khi thấy `NoSuchMethodError` hoặc một lỗi linking muộn:

### Bước A — Thu thập môi trường

~~~bash
java -version
javac -version
java -cp out LinkageDemo call
~~~

Ghi lại Java **compile version**, Java **runtime version**, classpath/module path, class loader context và stack trace/cause đầu tiên. Compiler mới và runtime cũ có thể gây `UnsupportedClassVersionError`; đó là câu chuyện **class-file version compatibility**, không phải Methodref resolution thiếu method.

### Bước B — Xác định caller và tham chiếu bị lỗi

~~~bash
javap -s -p -classpath out LinkageDemo
javap -c -v -p -classpath out LinkageDemo
~~~

Tìm instruction `invokestatic #N` hoặc opcode khác tại method gây lỗi, rồi tra `#N` trong constant pool. Đọc **owner/name/descriptor**, ví dụ:

~~~text
Methodref lib/Library.version:()Ljava/lang/String;
~~~

`#N` là constant-pool index, **không** là địa chỉ heap hay line number. Bytecode index BCI khác với constant-pool index. Stack trace line numbers được ánh xạ qua `LineNumberTable` nếu class giữ debug metadata.

### Bước C — Kiểm tra binary thực được tải

~~~bash
javap -s -p -classpath out lib.Library
javap -v -p -classpath out lib.Library
~~~

So sánh method list và descriptor thực. Nếu client đòi `version:()Ljava/lang/String;` nhưng v2 chỉ có `release:()Ljava/lang/String;`, đây là **binary API mismatch** có thể giải thích `NoSuchMethodError`.

### Bước D — Phân biệt giai đoạn lỗi

| Câu hỏi | Cách xác định |
| --- | --- |
| Class file không đọc được hoặc không hỗ trợ? | Xem `ClassFormatError`, `UnsupportedClassVersionError` |
| Bytecode không hợp lệ? | Xem `VerifyError` |
| Owner class không tìm thấy? | Xem `NoClassDefFoundError` và cause; xác định loader |
| Field/method không tồn tại đúng descriptor? | Xem `NoSuchFieldError` / `NoSuchMethodError` |
| Field/method sai static/instance hoặc owner shape? | Xem `IncompatibleClassChangeError` và instruction |
| Không đủ quyền? | Xem `IllegalAccessError`, module exports/runtime package |
| Static initializer ném exception? | Xem `ExceptionInInitializerError` và `Caused by` |
| `invokedynamic`/constant bootstrap thất bại? | Xem `BootstrapMethodError` và cause chain |

### Bước E — Chứng minh đường sử dụng

Ghi lại input/đường code khiến method reference được **sử dụng**, ví dụ `LinkageDemo call` so với `LinkageDemo` không tham số. Khi báo cáo, diễn đạt:

> “Client binary chứa tham chiếu tới method cũ; đường gọi method ấy không còn resolve được trên library v2, nên lỗi xuất hiện khi chương trình đi tới hành động cần liên kết.”

**Không tự khẳng định** “JVM chắc chắn chỉ bắt đầu resolution tại instruction ấy” nếu chưa đo được internal resolution behavior của JVM cụ thể.

### Logging bổ sung (HotSpot)

Trên HotSpot hiện đại, có thể thử:

~~~bash
java -Xlog:class+load=info -cp out LinkageDemo call
java -Xlog:help
~~~

Logging giúp quan sát class loading, nhưng **class loaded ≠ symbolic reference resolved**. Không suy rằng dòng log load của `lib.Library` là thời điểm Methodref `version` đã được phân giải. Các tag của unified logging thay đổi theo bản JVM; tham khảo `-Xlog:help` và chỉ bật mức log đủ dùng.

## 14. Câu hỏi phỏng vấn và các hiểu nhầm cần tránh

| Nhận định | Đúng hay sai, vì sao? |
| --- | --- |
| “JVM luôn resolve toàn bộ constant pool trước `main`.” | **Sai.** JVMS cho phép eager/lazy với những references thích hợp; còn ngoại lệ động có giới hạn riêng. |
| “JVM bắt buộc chỉ resolve method đúng lúc opcode `invoke` được thực thi.” | **Không tổng quát.** Có thể eager, nhưng error phải được ném ở điểm action liên quan. |
| “Eager resolution có thể ném mọi lỗi thiếu method ngay khi JVM bật.” | **Sai.** Không được tùy ý throw tại điểm không thể cần linkage/reference tương ứng. |
| “Lazy resolution là bỏ qua kiểm tra quyền truy cập.” | **Sai.** Khi resolution/instruction checks cần thiết, access control vẫn phải được thỏa. |
| “Đã initialize class nghĩa mọi reference của nó thành công.” | **Sai.** Resolution có thể còn tiếp tục về sau. |
| “`NoSuchMethodError` giống lỗi compiler `cannot find symbol`.” | **Sai.** Một cái runtime binary linkage error, một cái compile-time source error. |
| “Thay JAR dưới đường dẫn mà không tải class mới sẽ cập nhật method references đã resolved.” | **Không thể giả định.** Class identities và kết quả resolution đã được VM giữ theo quy tắc. |
| “Một lỗi linkage xuất hiện muộn chứng minh JVM dùng lazy resolution.” | **Sai.** JVM có thể phát hiện sớm rồi trì hoãn điểm throw theo JVMS. |
| “Mỗi lần invoke một method cần resolve lại reference từ đầu.” | **Sai.** Resolution outcomes có quy tắc caching/stability. |
| “`invokedynamic` cho phép tùy ý bootstrap mọi call site tại class loading.” | **Sai.** Có các giới hạn về thời điểm resolution/bootstrap. |
| “Một class có thể load thành công nhưng vỡ khi dùng một method của library.” | **Đúng,** như ví dụ client compiled v1 và chạy với library v2. |
| “`ExceptionInInitializerError` là `NoSuchMethodError` loại khác.” | **Sai.** Nó thuộc lỗi initialization, không phải lỗi tìm method thông thường. |

## 15. Tự kiểm tra có đáp án

**Câu 1.** `ClassFile` chứa `Methodref lib/Library.version:()Ljava/lang/String;` có nghĩa `Library.version` đã được invoke chưa?  
**Đáp án:** Không. Đó là symbolic reference mô tả owner/name/descriptor; invocation còn phụ thuộc đường thực thi.

**Câu 2.** JVM được phép resolve reference thông thường sớm trước khi đường gọi chạy không?  
**Đáp án:** Có, trong giới hạn của JVMS; nhưng lỗi resolution phải được ném ở điểm hành động có thể dùng reference tương ứng.

**Câu 3.** JVM được phép resolve muộn sau khi class đã initialize không?  
**Đáp án:** Có. Linking và resolution không bắt buộc giải quyết hết trước initialization.

**Câu 4.** Trong ví dụ Library v1/v2, tại sao compile lại `LinkageDemo` dựa trên v2 có thể báo compiler error, còn chạy binary cũ với v2 gây `NoSuchMethodError`?  
**Đáp án:** Compiler mới nhận diện API không còn method; binary cũ vẫn chứa Methodref v1 và resolution runtime không thể tìm method trên Library v2.

**Câu 5.** Một lần `NoSuchMethodError` xảy ra khi dùng reference; JVM có bắt buộc tự thử tải lại JAR mới ở lần dùng thứ hai không?  
**Đáp án:** Không. `JVMS `5.4.3` quy định kết quả resolution failure được giữ cho cùng symbolic reference theo điều kiện áp dụng.

**Câu 6.** `NoClassDefFoundError` và `ExceptionInInitializerError` có luôn chỉ ra cùng một pha không?  
**Đáp án:** Không. Một lỗi có thể liên quan loading/linking hoặc class ở erroneous state, còn lỗi kia thường do static initialization thất bại.

**Câu 7.** Từ `java -Xlog:class+load=info` có thể khẳng định Methodref nào đã resolve tại timestamp nào không?  
**Đáp án:** Không. Class loading log không phải per-symbolic-reference resolution log.

**Câu 8.** Vì sao `CONSTANT_Dynamic` cần quy tắc timing riêng?  
**Đáp án:** Nó có bootstrap computation và đặc tả không cho resolve quá sớm trước các hành động `ldc` liên quan hoặc bootstrap sử dụng như static argument.

**Câu 9.** Loading constraint violation có thể xuất hiện khi hai loaders không nhất trí về một tên kiểu trong method descriptor không?  
**Đáp án:** Có, nếu các constraints bắt buộc yêu cầu cùng runtime class identity mà hai loaders không thể đáp ứng.

**Câu 10.** Khi một linking error xuất hiện “muộn”, nên mô tả kết luận chắc chắn nhất là gì?  
**Đáp án:** Chương trình đã đến **hành động có liên quan tới linkage** bị lỗi; chưa thể kết luận chính xác JVM bắt đầu resolution tại thời điểm ấy hay phát hiện trước đó.

## 16. Tài liệu chính thức

- [JVMS Java SE 17 — 5.4 Linking](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4): loading trước linking, verification/preparation trước initialization; eager vs lazy và giới hạn thời điểm báo lỗi.
- [JVMS Java SE 17 — 5.4.3 Resolution](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.3): symbolic references, thời điểm lỗi, caching kết quả resolution.
- [JVMS Java SE 17 — 5.4.3.1 Class and Interface Resolution](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.3.1): phân giải các class/interface symbolic refs.
- [JVMS Java SE 17 — 5.4.3.2 Field Resolution](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.3.2): `NoSuchFieldError`, field lookup và loading constraints.
- [JVMS Java SE 17 — 5.4.3.3 Method Resolution](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.3.3): method lookup, lỗi `NoSuchMethodError` và liên kết class methods.
- [JVMS Java SE 17 — 5.4.3.4 Interface Method Resolution](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.3.4): interface methods.
- [JVMS Java SE 17 — 5.4.4 Access Control](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.4): runtime access checks và khả năng lỗi.
- [JVMS Java SE 17 — 5.5 Initialization](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5): thời điểm class initialization.
- [JVMS Java SE 17 — 4.4 The Constant Pool](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.4): Methodref, Fieldref, InvokeDynamic, Dynamic, NameAndType.
- [JDK 17 — `javap` manual](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): xem constant pool/bytecode bằng `-v`/`-c`.
- [JLS Java SE 17 — 13 Binary Compatibility](https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html): thay đổi binary APIs và tương thích nhị phân Java.

**Kết luận cần nhớ:** **Early/lazy resolution** là sự linh hoạt của VM về lúc chuyển symbolic references sang runtime entities; **error timing** lại bị JVMS giới hạn bởi những **hành động của chương trình có thể cần liên kết**. Để xác định một lỗi xuất hiện muộn, hãy tìm đúng **constant-pool reference, bytecode sử dụng, binary version và class loader**, không suy từ số dòng đã in ra rằng VM chắc chắn resolve sớm hay muộn.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="linking-and-initialization-007">Các thao tác kích hoạt khởi tạo và các trường hợp chỉ nạp lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Các thao tác kích hoạt khởi tạo và các trường hợp chỉ nạp lớp trong JVM

Một lớp Java có thể **đã được JVM nạp và có đối tượng `Class<?>`**, nhưng **chưa chạy static initializer**. Đến khi chương trình thực hiện một thao tác yêu cầu khởi tạo (initialization), JVM mới thực hiện thủ tục khởi tạo lớp/interface đó. Nhầm hai thời điểm sẽ gây lỗi khó tìm: log static xuất hiện ngoài dự đoán, plugin tự khởi động quá sớm, đọc field trả giá trị chưa như kỳ vọng, hoặc một lớp thất bại ở `<clinit>` rồi không thể khởi tạo lại.

Bài tập trung vào câu hỏi: **thao tác nào bắt buộc kích hoạt initialization và thao tác nào có thể chỉ loading/linking mà chưa chạy `<clinit>`?** Phần giải thích dựa trên **JLS Java SE 17 `12.4** và **JVMS Java SE 17 `5.5**, có ví dụ Java 17 chạy độc lập, đối chiếu bytecode và checklist điều tra lỗi.

**Quy tắc mở đầu:** “Lớp được nhắc đến” không đủ để kết luận “lớp đã khởi tạo”. Cần biết **loại thao tác**, **lớp/interface thực sự khai báo thành viên**, trạng thái initialization trước đó, và những phụ thuộc như superclass hoặc superinterface có default methods.

## 1. Ba giai đoạn không được đồng nhất

JVM phân biệt các khái niệm sau:

| Giai đoạn | JVM thực hiện | Có nhất thiết chạy static initializer của lớp đích? |
| --- | --- | --- |
| **Loading** | Tìm/định nghĩa biểu diễn runtime của class/interface từ tên và dữ liệu nhị phân; tạo đối tượng `Class` tương ứng | **Không** |
| **Linking: verification** | Kiểm tra định dạng, bytecode, kiểu và các ràng buộc hợp lệ | **Không** |
| **Linking: preparation** | Cấp vùng lưu static fields và thiết lập giá trị ban đầu theo quy tắc VM; có xử lý đặc biệt cho `ConstantValue` attributes | **Không chạy `<clinit>`** |
| **Linking: resolution** | Phân giải symbolic references (class, field, method...) khi JVM quyết định thực hiện theo đặc tả | **Không nhất thiết** |
| **Initialization** | Thực thi thủ tục khởi tạo class/interface, bao gồm code static field initializers và static blocks do compiler tạo | **Có**, nếu có công việc khởi tạo |

Đối với một lớp bình thường, compiler thường gom các lệnh khởi tạo static vào một **class initialization method `<clinit>`**. Nếu lớp không có code cần chạy thì không nhất thiết có method `<clinit>` trong class file, nhưng JVM vẫn có **trạng thái khởi tạo** cho lớp.

Sơ đồ khái niệm:

```text
chưa được nạp
     |
     | ClassLoader / JVM yêu cầu lớp
     v
LOADING --------> đã có Class<?> và định danh runtime
     |
     v
LINKING --------> verification / preparation / resolution
     |             (một phần resolution có thể diễn ra khi cần)
     |
     | thao tác active use yêu cầu khởi tạo
     v
INITIALIZATION --> khởi tạo lớp cha / một số superinterface nếu phải làm
     |             --> thực hiện static initializers / <clinit>
     v
đã khởi tạo thành công; các active uses tiếp tục
```

Không xem sơ đồ như timeline bắt buộc mọi bước resolution hoàn tất tại đúng một điểm duy nhất: JLS/JVMS cho phép JVM giải quyết symbolic references ở những thời điểm phù hợp trong phạm vi ràng buộc của đặc tả.

### Initialization khác construction

- **Initialization của class** là thủ tục ở cấp kiểu, thường xảy ra **một lần** đối với mỗi runtime class đã được định nghĩa bởi một defining class loader.
- **Instance construction** là tạo và khởi tạo **từng object**, có thể thực hiện nhiều lần; constructor `<init>` khác method `<clinit>`.
- Lời gọi `new Example()` thường dẫn tới class initialization nếu class chưa initialized, rồi mới tiếp tục tạo và xây dựng object theo quy tắc Java.
- Gọi `Class.forName("Example")` có thể khởi tạo class nhưng **không tạo bất kỳ instance Example nào**.

## 2. Bốn thao tác active use tiêu biểu trong JLS `12.4.1

Đối với một class `T`, JLS xác định việc khởi tạo diễn ra **ngay trước lần đầu** một số thao tác được thực hiện, trong đó các trường hợp quan trọng nhất là:

1. **Tạo instance của `T`**, chẳng hạn `new T()`; cũng có thể thực hiện qua reflection.
2. **Gọi một static method được khai báo bởi `T`**, chẳng hạn `T.perform()`.
3. **Gán giá trị cho một static field được khai báo bởi `T`**, chẳng hạn `T.count = 3`.
4. **Đọc một static field được khai báo bởi `T` mà field ấy không phải constant variable**, chẳng hạn `System.out.println(T.dynamicValue)`.

Các thao tác cụ thể khác như một số phương thức reflection hoặc lần gọi đầu của các method handles thích hợp cũng có thể buộc initialization. Việc khởi tạo superclass và một số superinterfaces của một class còn được kích hoạt **gián tiếp** bởi initialization của class ấy.

### Bảng quyết định nhanh

| Thao tác | Có chủ động yêu cầu initialize lớp liên quan? | Điều kiện cần nhớ |
| --- | --- | --- |
| `new T()` | **Có** | Trước khi tạo và xây dựng object của `T` nếu chưa khởi tạo |
| `T.staticMethod()` | **Có** | Khởi tạo **lớp/interface khai báo method** được gọi, không nhất thiết class viết ở bên trái dấu chấm |
| `T.nonConstantStaticField` | **Có** | Đọc field không phải constant variable |
| `T.staticField = value` | **Có** | Gán field static của lớp/interface khai báo |
| `T.COMPILE_TIME_CONSTANT` | **Không vì riêng phép đọc này** | Nếu là **constant variable** được khai báo phù hợp và truy cập theo quy tắc constant |
| `T.class` | **Không** | Lấy class literal không tự khởi tạo `T` |
| `new T[10]` | **Không cho component `T`** | Tạo array class/object; không tạo một instance `T` |
| `obj instanceof T` | **Không vì riêng phép kiểm tra này** | Có thể phải resolution kiểu nhưng không chủ động chạy `T.<clinit>` |
| `(T) obj` | **Không vì riêng cast này** | Checkcast có thể lỗi cast; không phải trigger initialize |
| `ClassLoader.loadClass("p.T")` | **Không theo contract mặc định** | Nạp lớp, không yêu cầu initialize `T` |
| `Class.forName("p.T", false, loader)` | **Không** | Yêu cầu nạp/liên kết nhưng không chủ động initialize |
| `Class.forName("p.T")` | **Có** | Overload một tham số mặc định yêu cầu initialization |
| `Class.forName("p.T", true, loader)` | **Có** | Có thể chọn loader và chủ động yêu cầu initialization |
| `Class.forName(module, "p.T")` | **Không** | Overload theo `Module` có contract khác và có thể trả `null` |
| `T[].class` | **Không cho `T`** | Lấy class literal của array type |
| Reflection: gọi static method qua `Method.invoke` | **Có** | Nếu cần, initialize declaring class trước invocation |
| Reflection: tạo instance qua `Constructor.newInstance` | **Có** | Nếu cần, initialize declaring class trước khi tạo |
| Reflection: đọc/ghi static field qua `Field.get`/`set` | **Có** | Kiểm tra access và trạng thái field có thể phát sinh lỗi |
| `Class.getDeclaredMethods()` | **Không do riêng metadata lookup** | Có thể nạp/resolve kiểu phụ thuộc; không đồng nghĩa gọi method |

Đây là bảng cho **nguyên nhân trực tiếp**. Nếu một thao tác thụ động gọi tới thư viện có side effects, hoặc thread khác đồng thời active-use cùng lớp, bạn vẫn có thể quan sát thấy class đã được initialized; không được kết luận rằng API thụ động là trigger chỉ vì log xuất hiện gần đó.

## 3. Chương trình Java 17: nạp, lấy `Class<?>`, rồi mới khởi tạo

Tạo file **`ActiveUseMatrix.java`**:

```java
public class ActiveUseMatrix {
    public static class Target {
        public static final int CONSTANT = 7;

        static {
            System.out.println("Target.<clinit>");
        }

        public static int dynamic = compute();

        private static int compute() {
            System.out.println("Target.compute");
            return 42;
        }

        public static void ping() {
            System.out.println("Target.ping");
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader loader = ActiveUseMatrix.class.getClassLoader();
        String name = ActiveUseMatrix.class.getName() + "$Target";

        System.out.println("01 constant=" + Target.CONSTANT);

        Class<?> literal = Target.class;
        System.out.println("02 literal=" + literal.getSimpleName());

        Class<?> viaLoader = loader.loadClass(name);
        System.out.println("03 loadClass same=" + (viaLoader == literal));

        Class<?> viaForName = Class.forName(name, false, loader);
        System.out.println("04 forName(false) same=" + (viaForName == literal));

        Class<?> arrayType = Class.forName("[L" + name + ";", false, loader);
        System.out.println("05 array=" + arrayType.isArray());

        Class.forName(name, true, loader);
        System.out.println("06 after forName(true)");

        Target.ping();
        System.out.println("07 done");
    }
}
```

Chạy với JDK có `javac` hỗ trợ Java 17:

```bash
javac --release 17 ActiveUseMatrix.java
java ActiveUseMatrix
```

**Stdout chính xác**:

```text
01 constant=7
02 literal=Target
03 loadClass same=true
04 forName(false) same=true
05 array=true
Target.<clinit>
Target.compute
06 after forName(true)
Target.ping
07 done
```

Diễn giải theo từng mốc:

- `Target.CONSTANT` là **constant variable** vì `static final int` được gán constant expression. Việc dùng giá trị 7 có thể được compiler nhúng vào caller; dòng 01 không khiến `Target` phải initialize.
- `Target.class` lấy một `Class<Target>` nhưng **không** chủ động chạy static block; dòng 02 đến trước `Target.<clinit>`.
- `loader.loadClass(name)` và `Class.forName(name, false, loader)` cùng xác định runtime class do cùng defining loader; hai kết quả so sánh identity với class literal là `true`, nhưng `Target` vẫn chưa initialize.
- Lệnh `Class.forName("[L" + name + ";", false, loader)` lấy **array class** có component `Target`; nó không tạo instance Target hoặc khởi tạo component class. Dòng 05 vẫn đến trước static block.
- `forName(name, true, loader)` yêu cầu initialization. Static block được thực hiện trước rồi static field `dynamic` gọi `compute()`. Thứ tự này xuất phát từ **thứ tự textual của static initializers** trong `Target`.
- `Target.ping()` chạy sau khi lớp đã initialized nên không tái thực thi `<clinit>`. Dòng `Target.ping` xuất hiện đúng một lần từ method `ping`.

**Điểm tinh tế:** JVM có thể đã **loading** `Target` từ trước, kể cả do verifier cần tới kiểu ấy, nhưng điều quan sát được ở đây là **chưa có yêu cầu initialization** trước khi gọi `forName(true)`. Không được lấy dòng đầu log `Target.<clinit>` để suy ra chính xác thời điểm đầu tiên class file được đọc từ disk.

## 4. Static field: vì sao `static final` chưa chắc “không kích hoạt”?

Cần phân biệt **constant variable** trong JLS với một field chỉ mang modifiers `static final`.

Theo JLS `4.12.4, một constant variable phải là `final`, có kiểu **primitive hoặc `String`**, và được khởi tạo bằng **constant expression** theo JLS `15.29. Ví dụ:

```java
static final int LIMIT = 100;            // constant variable
static final String TITLE = "JVM";       // constant variable
static final int SUM = 2 + 3;            // constant variable

static final int RUNTIME = calculate();  // KHÔNG là constant variable
static final Integer WRAPPED = 100;      // KHÔNG là constant variable
static final Object OBJECT = new Object(); // KHÔNG là constant variable
```

Đây là **fragment trong một class**, không phải chương trình độc lập. Cần phân biệt:

- `LIMIT`, `TITLE`, `SUM` đáp ứng điều kiện constant variable.
- `RUNTIME` dùng method call nên không phải constant expression.
- `WRAPPED` có kiểu `Integer` reference, không phải primitive hay `String`.
- `OBJECT` là reference tới object được tạo ở runtime.
- Khi code **đọc** một trong các nonconstant static fields này, trường hợp active use thông thường khiến **declaring class** được initialize.
- `ConstantValue` attribute trong class file là một phần cơ chế cấp giá trị cho constant variable ở thời điểm preparation; không suy rằng việc đọc constant ấy buộc JVM chạy `<clinit>`.

### Một chương trình để quan sát constant và nonconstant field

Tạo **`ConstantVsRuntime.java`**:

```java
public class ConstantVsRuntime {
    static class Holder {
        static final String LABEL = "ready";
        static final int FIXED = 5;
        static final Integer BOXED = Integer.valueOf(8);

        static {
            System.out.println("Holder.<clinit>");
        }

        static final int COMPUTED = 10 + runtimeValue();

        private static int runtimeValue() {
            System.out.println("Holder.runtimeValue");
            return 2;
        }
    }

    public static void main(String[] args) {
        System.out.println("label=" + Holder.LABEL);
        System.out.println("fixed=" + Holder.FIXED);

        System.out.println("boxed=" + Holder.BOXED);
        System.out.println("computed=" + Holder.COMPUTED);
    }
}
```

```bash
javac --release 17 ConstantVsRuntime.java
java ConstantVsRuntime
```

**Stdout**:

```text
label=ready
fixed=5
Holder.<clinit>
Holder.runtimeValue
boxed=8
computed=12
```

Khi `Holder.BOXED` được đọc, JVM phải khởi tạo `Holder`. Trong quá trình `<clinit>`, các field initializers và static blocks chạy theo thứ tự source: `BOXED` nhận 8, rồi in `Holder.<clinit>`, sau đó khởi tạo `COMPUTED` bằng `runtimeValue()` và in `Holder.runtimeValue`. Khi tiếp tục, việc đọc `Holder.COMPUTED` không tái thực hiện initialization.

### Có cách nào đọc field mà không khởi tạo class không?

Lấy **metadata** của field bằng `Holder.class.getDeclaredField("BOXED")` không đồng nghĩa **đọc giá trị** field. Nhưng `Field.get(null)` của field static sẽ yêu cầu initialization khi cần. Đừng dùng metadata lookup và value access như hai hành vi đồng nhất.

## 5. Lớp khai báo static member mới là lớp quan trọng

Một điều đặc biệt dễ gây nhầm: biểu thức `Subclass.staticField` **không nhất thiết** khởi tạo `Subclass` nếu field ấy thực sự được khai báo ở `Superclass`.

Tạo **`DeclaringClassRules.java`**:

```java
public class DeclaringClassRules {
    static class Parent {
        static {
            System.out.println("Parent.<clinit>");
        }

        static int value = 10;

        static void show() {
            System.out.println("Parent.show");
        }
    }

    static class Child extends Parent {
        static {
            System.out.println("Child.<clinit>");
        }
    }

    public static void main(String[] args) {
        System.out.println("field=" + Child.value);
        Child.show();
        System.out.println("after inherited members");

        new Child();
        System.out.println("after instance");
    }
}
```

```bash
javac --release 17 DeclaringClassRules.java
java DeclaringClassRules
```

**Stdout**:

```text
Parent.<clinit>
field=10
Parent.show
after inherited members
Child.<clinit>
after instance
```

Giải thích:

1. `Child.value` có cú pháp nhắc tới Child nhưng field `value` do **Parent** khai báo. Phép đọc yêu cầu initialize **Parent**, không trực tiếp buộc Child initialize.
2. `Child.show()` cũng trỏ tới static method do Parent khai báo, nên gọi `Parent.show`. Parent đã initialized; Child vẫn chưa.
3. `new Child()` là active use của Child. Superclass Parent đã initialized, vì vậy lần này chỉ thấy log `Child.<clinit>`.

Đừng nhầm **static hiding** với **instance overriding**. Tên lớp ở bên trái dấu chấm trong source có thể khác với **declaring class** cuối cùng mà compiler/VM liên kết tới. Khi không rõ, dùng `javap -c -v` xem `getstatic` hoặc `invokestatic` đang tham chiếu tới field/method nào.

## 6. Những thao tác không tự khởi tạo component class

### Class literal và kiểm tra kiểu

- `SomeType.class` là **class literal**; việc đánh giá biểu thức này không tự kích hoạt initialization của `SomeType`.
- `obj instanceof SomeType` và cast `(SomeType) obj` có thể đòi hỏi VM kiểm tra/resolve kiểu; chúng **không** phải active-use trigger để chạy `SomeType.<clinit>`.
- `someClass.getName()`, `getSimpleName()` hay các thao tác đọc metadata tương đương không có nghĩa phải chạy static initializer của target class.
- Các metadata APIs **có thể** liên quan tới resolution và loading những kiểu khác. Không đồng nhất “không initialize target” với “hoàn toàn không có công việc VM”.

### Mảng `new T[n]`

`new T[3]` **tạo object array**, không tạo ba object `T` và không buộc `T` initialize. Những phần tử reference ban đầu là `null`. Ngược lại, code `new T[] {new T()}` **có** tạo một instance `T` bên trong array initializer, nên nó sẽ kích hoạt initialization khi cần.

### Ví dụ Java 17

Tạo **`PassiveOperations.java`**:

```java
public class PassiveOperations {
    static class Element {
        static {
            System.out.println("Element.<clinit>");
        }

        static int DATA = 5;
    }

    public static void main(String[] args) {
        System.out.println("class=" + Element.class.getSimpleName());

        Element[] elements = new Element[2];
        System.out.println("arrayLength=" + elements.length);
        System.out.println("firstIsNull=" + (elements[0] == null));

        Object item = "not an Element";
        System.out.println("instanceof=" + (item instanceof Element));

        System.out.println("before active read");
        System.out.println("data=" + Element.DATA);
    }
}
```

```bash
javac --release 17 PassiveOperations.java
java PassiveOperations
```

**Stdout**:

```text
class=Element
arrayLength=2
firstIsNull=true
instanceof=false
before active read
Element.<clinit>
data=5
```

Không có hoạt động nào trước `Element.DATA` chủ động yêu cầu initialization của Element. Phép đọc static field không phải constant variable mới khiến `Element.<clinit>` được thực hiện.

### Vì sao `Class.forName("[Lpkg.Type;")` cần phân biệt?

Với dạng **array descriptor-like name**, `Class.forName` nhận biết một **array class**. Ngay cả overload yêu cầu initialize, **component class vẫn không được initialize chỉ vì nạp array class**. Nếu sau đó chương trình `new pkg.Type()` thì đó là một active use khác, cần xét riêng.

Đừng thay tùy ý `pkg.Type[]` (cú pháp source) bằng string nạp class; đối với mảng reference, định dạng tên dùng kiểu `[Lpkg.Type;`; đối với mảng primitive `int[]` dùng `[I`. Tên primitive `int` tự nó không phải một binary class name để nạp như class thông thường.

## 7. `Class.forName` và `ClassLoader.loadClass`: chọn đúng overload

| Cách gọi | Loader được chọn | Có chủ động yêu cầu initialize? |
| --- | --- | --- |
| `Class.forName(name)` | Loader của **lớp gọi** `Class.forName` (caller-sensitive) | **Có** |
| `Class.forName(name, true, loader)` | Loader truyền vào, `null` là bootstrap loader | **Có** |
| `Class.forName(name, false, loader)` | Loader truyền vào | **Không** |
| `loader.loadClass(name)` | Chính đối tượng loader được gọi | **Không** theo triển khai/contract mặc định |
| `Class.forName(module, name)` | Tìm trong module chỉ định | **Không**, có thể trả `null` nếu không tìm thấy |

Một số điểm quan trọng:

- `ClassLoader.loadClass(String name)` thông thường ủy quyền tới `loadClass(name, false)`; tham số `resolve` của overload protected liên quan tới **linking/resolution**, **không phải** cờ initialize.
- `Class.forName(name, false, loader)` yêu cầu nạp và liên kết nhưng không chủ động initialize. Một luồng khác có thể đồng thời initialize cùng class, vì vậy `initialize=false` **không phải một cam kết trạng thái lớp vẫn “chưa khởi tạo” mãi**.
- `Class.forName(name)` **không tự sử dụng Thread Context ClassLoader**. Framework có thể lấy `Thread.currentThread().getContextClassLoader()` rồi truyền loader đó vào overload ba tham số nếu cần tìm provider theo bối cảnh.
- Với cùng **runtime class** và defining loader, initializer chạy theo quy tắc trạng thái initialization, không chạy lại mỗi lần gọi `forName(true)`. Cùng tên class nhưng **định nghĩa bởi hai defining loaders khác nhau** là hai kiểu runtime riêng, mỗi kiểu có trạng thái initialization riêng.
- `Class.forName(module, name)` là API khác, không thay thế cơ học `forName(name, true, loader)` khi cần chạy static initializer.

### Mẫu plugin: tra metadata trước, chủ động initialize sau

```java
ClassLoader loader = Thread.currentThread().getContextClassLoader();
Class<?> candidate = Class.forName("com.example.Plugin", false, loader);

// Kiểm tra các metadata/quan hệ kiểu theo contract của ứng dụng...
// Khi quyết định bắt đầu sử dụng lớp:
Class.forName("com.example.Plugin", true, loader);
```

Đây là **đoạn minh họa**, class `com.example.Plugin` và loader phải tồn tại trong ứng dụng thật; cần kiểm tra `loader` có thể null hay không. Chủ động tách bước tìm lớp với bước khởi tạo giúp giảm side effects khi quét nhiều plugin, nhưng không loại bỏ tất cả việc loading/linking metadata dependencies.

## 8. Reflection: đọc metadata không giống gọi method/tạo instance

Reflection dùng `Class<?>` làm “đối tượng mô tả kiểu”, nhưng mỗi thao tác có một mức active use khác nhau.

- `type.getDeclaredMethods()`, `getDeclaredFields()`, `getDeclaredConstructor(...)` thường là **metadata lookup**, không tự yêu cầu chạy `<clinit>` của lớp đích.
- `method.invoke(null, ...)` với **static method** yêu cầu JVM initialize declaring class trước invocation nếu cần.
- `constructor.newInstance(...)` yêu cầu class được khởi tạo trước khi tạo instance.
- `field.get(null)` hoặc `field.set(null, value)` trên **static field** yêu cầu initialization theo contract của reflection trước khi đọc/ghi giá trị, khác với chỉ lấy metadata của `Field`.
- Reflection còn kiểm tra **accessibility** và module boundaries. Một method tìm được có thể vẫn không gọi được vì `IllegalAccessException` hoặc chính sách module; các lỗi đó không thể sửa bằng cách cố gọi `Class.forName(true)`.

### Ví dụ metadata trước, active use sau

Tạo file **`ReflectionInitialization.java`**:

```java
import java.lang.reflect.Method;

public class ReflectionInitialization {
    public static class Service {
        static {
            System.out.println("Service.<clinit>");
        }

        public static void hello() {
            System.out.println("Service.hello");
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader loader = ReflectionInitialization.class.getClassLoader();
        String name = ReflectionInitialization.class.getName() + "$Service";

        Class<?> type = Class.forName(name, false, loader);
        Method hello = type.getDeclaredMethod("hello");
        System.out.println("metadata acquired");

        hello.invoke(null);
        System.out.println("invocation completed");
    }
}
```

```bash
javac --release 17 ReflectionInitialization.java
java ReflectionInitialization
```

**Stdout**:

```text
metadata acquired
Service.<clinit>
Service.hello
invocation completed
```

`Class.forName(false)` và `getDeclaredMethod` cho phép lấy metadata trước; `Method.invoke` với static method khiến Service được initialize nếu chưa initialized. `Method.invoke` có thể gói lỗi **bên trong body method** thành `InvocationTargetException`, trong khi **initialization failure** có thể biểu hiện bằng `ExceptionInInitializerError`; cần đọc cause/stack trace thật, không gộp mọi lỗi reflection thành một loại.

## 9. Khởi tạo lớp con kéo theo superclass và một số superinterfaces

Không chỉ những thao tác nhắm trực tiếp `T` mới làm lớp/interface khác được initialized. Khi JVM initialization một **class**:

1. Nó thực hiện thủ tục bảo đảm superclass cần thiết đã được initialized.
2. Nó cũng initialization các **superinterfaces khai báo default methods** theo các quy tắc trong JVMS `5.5, kể cả một số superinterfaces gián tiếp theo thứ tự xác định.
3. Sau đó lớp đích mới hoàn tất phần khởi tạo của chính nó.

Không được diễn dịch thành “khởi tạo class luôn khởi tạo mọi interface được implements”: một interface **không có default methods** không nhất thiết được initialized chỉ vì có class implements nó. Những quy tắc interface còn khác với thứ tự superclass của class.

### Ví dụ minh họa có default method

Tạo **`DefaultInterfaceInitialization.java`**:

```java
public class DefaultInterfaceInitialization {

    static int announce(String message) {
        System.out.println(message);
        return 1;
    }

    static class Base {
        static {
            System.out.println("Base.<clinit>");
        }
    }

    interface WithDefault {
        int FLAG = announce("WithDefault.<clinit>");

        default void action() {
            // Không cần được gọi để WithDefault phải initialize khi Derived initialize.
        }
    }

    static class Derived extends Base implements WithDefault {
        static {
            System.out.println("Derived.<clinit>");
        }
    }

    public static void main(String[] args) {
        System.out.println("before new");
        new Derived();
        System.out.println("after new");
    }
}
```

```bash
javac --release 17 DefaultInterfaceInitialization.java
java DefaultInterfaceInitialization
```

**Stdout**:

```text
before new
Base.<clinit>
WithDefault.<clinit>
Derived.<clinit>
after new
```

Giải thích: `new Derived()` yêu cầu initialization của Derived. Base là superclass và phải được initialize trước; `WithDefault` khai báo một default method, vì vậy initialization của Derived còn kéo theo interface thích hợp; cuối cùng mới in `Derived.<clinit>`.

Một interface vẫn có thể được initialized **trực tiếp** bằng cách đọc một field static **không phải constant variable** của interface hoặc gọi một static method của interface. Trạng thái initialization của interface **không** nên suy ra chỉ từ việc code đã khai báo `implements`.

### Gọi default method và việc nạp interface

Tuy interface default method có body, lời gọi `interfaceRef.action()` có dispatch theo receiver và các quy tắc JVMS về method selection. Nó không có nghĩa mọi interface đã được load ở startup đều chạy initializer. Sự có mặt của default method trong chuỗi superinterface **liên quan đến** quy tắc khởi tạo khi khởi tạo class thực hiện, không phải lời gọi `action()` lần đầu mới luôn là trigger.

## 10. Method handles cũng có thể là active use

Một điểm nâng cao từ JVMS `5.5: **lần invocation đầu** của `MethodHandle` được phân giải từ một số **symbolic method handle kinds** có thể kích hoạt initialization của class/interface khai báo member tương ứng. Các reference kinds đáng nhớ:

- `REF_getStatic`
- `REF_putStatic`
- `REF_invokeStatic`
- `REF_newInvokeSpecial`

Không kết luận “lấy một `MethodHandle`” luôn chạy static initializer. Cần xem **loại symbolic reference, thời điểm resolution và invocation**, cùng contract của API liên quan. Lấy/tra `MethodHandle` và **thực thi handle** là hai sự kiện cần phân biệt.

Ngược lại, chỉ tạo một `MethodType` hay lấy `Class` metadata không phải “gọi static method” chỉ vì tên method nằm trong chuỗi lookup.

### Vì sao không nên học cơ chế này bằng ví dụ `staticMethod()` thường?

Compiler có thể trực tiếp dùng `invokestatic` và `getstatic` cho những lời gọi thông thường. Method handles là một lớp biểu diễn lời gọi khác, có cơ chế lookup và adaptation riêng. Nếu đang phân tích class file có `invokedynamic`, bootstrap method có thể gián tiếp truy cập method handles; luôn xem bootstrap owner, constant pool và semantics cụ thể thay vì giả định mọi `invokedynamic` có cùng điều kiện khởi tạo.

## 11. Bytecode cho biết trigger nào, nhưng không buộc đã khởi tạo ngay lúc resolve

Một bảng liên hệ source–bytecode:

| Java source | Opcode/biểu diễn thường thấy | Tại sao liên quan initialization |
| --- | --- | --- |
| `new Target()` | `new` + `dup` + `invokespecial <init>` | `new` tạo instance, yêu cầu initialization của Target khi cần |
| `Target.staticMethod()` | `invokestatic` | Target **declaring class** phải initialized theo quy tắc |
| `Target.dynamicField` | `getstatic` | Static field không phải constant variable đòi active use |
| `Target.dynamicField = x` | `putstatic` | Ghi static field đòi active use |
| `Target.class` | `ldc` class constant hoặc cách biểu diễn hợp lệ | Class literal không tự initialize Target |
| `Target.CONSTANT` | Có thể thành hằng literal trực tiếp, `bipush`/`ldc`... | Constant variable có thể được inline, không cần `getstatic` |
| `new Target[3]` | `anewarray` (thường) | Tạo array, **không** tạo instance Target |
| `obj instanceof Target` | `instanceof` | Kiểm tra kiểu, không phải active use của Target |
| `(Target) obj` | `checkcast` | Type check, không phải initialization trigger |
| `loader.loadClass(name)` | Gọi API thư viện qua `invokevirtual` | API loader không trực tiếp đòi init target |
| `Class.forName(name)` | `invokestatic Class.forName` | Hành vi init do API `Class.forName` yêu cầu đối với target |

**Đặc biệt:** một opcode `ldc` cho class constant, và một opcode `invokestatic` gọi method static của class **khác nhau về ngữ nghĩa**. Không suy ra “nếu lớp có mặt trong constant pool thì đã chạy static block”.

Lệnh `javap` để soi ví dụ:

```bash
javap -c -v -p ActiveUseMatrix
javap -c -v -p ConstantVsRuntime
javap -c -v -p DeclaringClassRules
```

Trong output, tìm `ldc ... class`, `getstatic`, `invokestatic`, `new`, `anewarray` cùng các constant-pool references. BCI và constant-pool indices do compiler sinh, không được chuẩn JLS cố định theo số thứ tự.

### Mẫu bytecode minh họa, không phải nguyên văn output

```text
ldc #ClassTarget     // lấy Class<Target>, không tự yêu cầu <clinit>
anewarray #Target    // tạo mảng reference Target, không initialize component
getstatic #Target.x  // đọc nonconstant static field, có thể kích hoạt <clinit>
invokestatic #Target.m // gọi static method, có thể kích hoạt <clinit>
new #Target          // tạo instance, có thể kích hoạt <clinit>
```

Các nhãn `#ClassTarget`, `#Target` là **chỗ đứng cho index constant pool**, không phải cú pháp output `javap` chính xác. Kết luận “có thể kích hoạt” là vì nếu lớp **đã initialized thành công** trước đó, cùng active use **không chạy lại initializer**.

## 12. Class initialization thất bại: trạng thái erroneous và đường lỗi

Một initialization có thể thất bại, ví dụ static field initializer gọi code ném exception. Khi JVM đánh dấu class là **erroneous** trong loader ấy, lần yêu cầu initialization tiếp theo không thực hiện lại `<clinit>` như thể class hoàn toàn mới.

Tạo **`InitializationFailure.java`**:

```java
public class InitializationFailure {
    static class Broken {
        static final int VALUE = compute();

        private static int compute() {
            throw new IllegalStateException("boom");
        }
    }

    public static void main(String[] args) throws Exception {
        String name = InitializationFailure.class.getName() + "$Broken";

        try {
            Class.forName(name);
        } catch (ExceptionInInitializerError error) {
            System.out.println("first=ExceptionInInitializerError");
        }

        try {
            Class.forName(name);
        } catch (NoClassDefFoundError error) {
            System.out.println("second=NoClassDefFoundError");
        }
    }
}
```

```bash
javac --release 17 InitializationFailure.java
java InitializationFailure
```

**Stdout**:

```text
first=ExceptionInInitializerError
second=NoClassDefFoundError
```

- Lần đầu, `Class.forName(name)` yêu cầu initialization. `compute()` ném `IllegalStateException`, làm initialization không hoàn thành; runtime báo `ExceptionInInitializerError` đối với exception được bọc theo quy tắc.
- Lần tiếp theo, cùng lớp trong cùng defining loader đang erroneous, nên nỗ lực khởi tạo lại thường nhận `NoClassDefFoundError` (“Could not initialize class ...”).
- `ClassNotFoundException` là checked exception cho việc **không tìm được class theo tên**; nó không đồng nghĩa `NoClassDefFoundError` do **class initialization thất bại trước đó** hoặc do lỗi liên kết/dependency.

**Không dùng “thử gọi lại `forName`” làm cơ chế khôi phục mặc định**. Nếu startup logic có nguy cơ thất bại, thiết kế việc retry ở tầng service hoặc tài nguyên ứng dụng, không giấu quá nhiều side effects vào static initializers.

## 13. Khởi tạo trong môi trường đa luồng và class loader riêng

JVMS `5.5 quy định thủ tục đồng bộ hóa để chỉ một thread thực hiện initialization của mỗi class/interface tại một thời điểm. Nếu thread A đang initialize T:

- Thread B yêu cầu active use của T có thể phải **đợi** T initialization hoàn thành.
- Nếu code `<clinit>` có khóa, I/O hoặc chờ lẫn nhau, startup có thể bị **block hoặc deadlock**. Stack trace/`jcmd Thread.print` có thể giúp điều tra nhưng không thay thế đọc logic.
- Nếu initialization thành công, các thread khác có thể sử dụng T theo các bảo đảm đồng bộ và memory model tương ứng.
- Nếu initialization thất bại, các thread khác có thể nhận lỗi khởi tạo/liên kết thích hợp chứ không tự chạy lại static initializer.

Class identity gồm **binary name + defining class loader**. Hai defining loaders có thể tạo hai runtime classes cùng tên, nhưng trạng thái initialization **riêng biệt**. Một plugin được load lại bằng loader mới có thể chạy static initializers riêng của lớp mới, nhưng đây **không** phải “reset class” trong loader cũ.

Đặc biệt trong kiến trúc plugin, `Thread.currentThread().getContextClassLoader()` là loader theo **ngữ cảnh thread**, không tự động thay defining loader của class đã nạp. `Class.forName(name)` không mặc định dùng TCCL; nếu muốn plugin loader thì truyền tường minh vào `Class.forName(name, initialize, pluginLoader)`.

## 14. Các lỗi suy luận phổ biến

| Ngộ nhận | Giải thích đúng |
| --- | --- |
| “Lấy `Class<?>` nghĩa là static block đã chạy” | Loading/metadata và initialization tách biệt |
| “`SomeClass.class` là active use giống `new SomeClass()`” | Class literal không buộc initialize component class |
| “`new SomeClass[10]` tạo 10 instances SomeClass” | Chỉ tạo array, mỗi phần tử reference khởi đầu là `null` |
| “`instanceof` hoặc cast kích hoạt `<clinit>` của target” | Đây là kiểm tra kiểu, không phải trigger initialize |
| “`ClassLoader.loadClass` luôn gọi static initializer” | Contract mặc định không yêu cầu initialization |
| “`Class.forName(name)` và `Class.forName(name,false,loader)` giống nhau” | Overload một tham số yêu cầu init, overload false không |
| “`Class.forName(...,false,...)` bảo đảm lớp vẫn chưa init khi method trả về” | Một luồng hoặc active use khác có thể init đồng thời |
| “Mọi `static final` đều là compile-time constant” | Chỉ field primitive/`String` final với constant expression |
| “`Child.staticField` luôn initialize Child” | JVM xét **declaring class** của member đã resolve |
| “Một class implements interface là interface đã initialize” | Phụ thuộc default methods và active uses, không đúng tổng quát |
| “Lấy reflection `Method` tương đương gọi method đó” | Metadata lookup không bằng invocation |
| “`getstatic` và `ldc` hằng số class tương đương nhau” | Một cái truy cập static field, cái kia tải constant/class metadata |
| “Class chỉ chạy `<clinit>` một lần toàn JVM dù có nhiều loaders” | Trạng thái theo runtime class và defining loader |
| “Init lỗi thì lần sau làm lại `<clinit>`” | Lớp có thể erroneous và ném lỗi khác lần sau |
| “Không thấy log static block tức lớp chưa load” | Loading có thể đã xảy ra từ trước mà chưa init |
| “`Class.forName(module,name)` cũng initialize” | Overload theo `Module` không có cùng contract đó |

## 15. Checklist khi thiết kế hoặc debug

1. **Lớp đích có được initialize trước chưa?** Đã có active use hoặc initialization gián tiếp qua superclass/default superinterface chưa?
2. **Thao tác trực tiếp là gì?** `new`, `invokestatic`, `getstatic`, `putstatic`, reflection invocation, hay chỉ `ldc` class literal, `loadClass`, `instanceof`, `anewarray`?
3. **Static field là constant variable thật hay chỉ `static final`?** Kiểm tra kiểu và constant expression; không dựa vào tên `FINAL`.
4. **Thành viên thực sự do lớp nào khai báo?** `Child.value` có thể liên kết tới `Parent.value`.
5. **Tên lớp và loader đúng chưa?** Binary name, nested class dấu `$`, module/classpath, defining loader và TCCL có thể khác nhau.
6. **Đang quét metadata hay bắt đầu chạy plugin?** Có cần `Class.forName(name,false,loader)` trước rồi mới tạo object/initialize tường minh không?
7. **Có đọc static field qua reflection hoặc gọi static method handle không?** Những thao tác này có semantics khác với chỉ lấy metadata.
8. **Class có static initializer chậm hoặc gây side effect không?** Tránh network/blocking lock/đăng ký tài nguyên không kiểm soát trong `<clinit>`.
9. **Nếu lỗi xảy ra lần thứ hai, lỗi đầu tiên là gì?** Lưu cause của `ExceptionInInitializerError`; đừng chỉ thấy `NoClassDefFoundError` rồi kết luận thiếu JAR.
10. **Mình đang đọc contract hay chi tiết compiler?** Dùng JLS/JVMS làm nguồn; `javap` hỗ trợ minh họa, không quyết định chính sách initialization.

## 16. Bài tập tự đánh giá

**Câu 1.** `Class<?> c = Widget.class;` có làm `Widget.<clinit>` chạy không?  
**Không**, không do riêng class literal.

**Câu 2.** `Widget[] xs = new Widget[100];` có làm `Widget` initialize không?  
**Không**, nếu chỉ tạo array of references; khi code thực sự tạo `new Widget()` thì khác.

**Câu 3.** `Class.forName("p.Widget", false, loader)` trả về `Class<?>`. Lớp đã chắc chắn khởi tạo chưa?  
**Không**. Lời gọi này không yêu cầu init; trạng thái thực tế còn phụ thuộc active uses khác.

**Câu 4.** `static final int X = 5;` và `static final int Y = compute();`, đọc field nào có thể kích hoạt initialization?  
**Y** là nonconstant static field; đọc Y yêu cầu init nếu chưa có. X là constant variable, phép đọc bình thường không.

**Câu 5.** `Sub extends Base`, `Sub.counter` tham chiếu field static khai báo trong Base. Lớp nào được initialize trực tiếp bởi phép đọc?  
**Base**, không nhất thiết Sub.

**Câu 6.** `Constructor.newInstance` tạo object qua reflection khi class chưa initialized. Có chạy initializer trước?  
**Có**, class cần được initialized theo thủ tục JVM trước khi instance construction.

**Câu 7.** `getDeclaredMethod` và `Method.invoke` có cùng điều kiện initialization?  
**Không**. Lấy metadata không giống thực thi static method qua reflection.

**Câu 8.** Nếu class vừa fail ở `<clinit>`, lần gọi `Class.forName(name)` tiếp theo trong cùng loader có khởi tạo lại từ đầu?  
**Không**; class erroneous, có thể báo `NoClassDefFoundError`.

**Câu 9.** Có thể lấy `Class<?>` của một interface mà chưa initialize interface đó?  
**Có**. Loading/metadata tách biệt với initialization; tuy nhiên đọc một static field khôngconstant hoặc các active uses khác có thể trigger.

## Nguồn chuẩn Java SE 17

- [JLS 12.2 — Loading of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.2): loading và class loader.
- [JLS 12.3 — Linking of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.3): verification, preparation, resolution.
- [JLS 12.4.1 — When Initialization Occurs](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.1): các trigger bắt buộc và ví dụ superclass/static fields.
- [JLS 12.4.2 — Detailed Initialization Procedure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.2): đồng bộ, initialization trạng thái thành công/thất bại.
- [JLS 4.12.4 — final Variables](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.12.4) và [15.29 — Constant Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.29): định nghĩa constant variable.
- [JVMS 5.3 — Creation and Loading](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3): defining loader/initiating loader.
- [JVMS 5.4 — Linking](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4): verification, preparation và resolution.
- [JVMS 5.5 — Initialization](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5): chính xác các instruction active use, method handles, superclasses/default-method superinterfaces, initialization states.
- [`Class` API Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `forName`, class literal, reflection metadata.
- [`ClassLoader` API Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `loadClass` và `resolveClass`.
- [Java Reflection `Method`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html), [`Constructor`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Constructor.html) và [`Field`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html): metadata vs invocation/access.
- [`javap` Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): quan sát mã bytecode `new`/`getstatic`/`ldc`/`invokestatic`.

**Ghi nhớ:** **Loading giúp JVM biết một kiểu tồn tại; linking kiểm tra và chuẩn bị kiểu; initialization mới là bước kích hoạt static initialization code.** Muốn dự đoán `<clinit>`, hãy xác định **active use**, **declaring class**, **constant variable**, **superclass/default interface dependencies**, trạng thái initialization và loader — không chỉ nhìn xem tên lớp đã xuất hiện trong source hoặc constant pool hay chưa.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="linking-and-initialization-008">Hằng số biên dịch, class literal và truy cập trường khai báo ở lớp cha</a>

<details>
<summary>Xem chi tiết</summary>

# Hằng số biên dịch, class literal và truy cập trường khai báo ở lớp cha

## 1. Vấn đề cốt lõi: nhắc tới một kiểu không nhất thiết khởi tạo kiểu đó

Khi Java source viết **T.CONSTANT**, **T.class**, **Child.someField** hoặc **T.staticMethod()**, tên kiểu đứng trong biểu thức có thể giống nhau, nhưng **quy tắc khởi tạo lớp (class initialization)** không giống nhau. JVM phân biệt **lớp chứa mã nguồn tham chiếu**, **lớp thực sự khai báo thành viên**, **trường có phải constant variable hay không**, và **thao tác có yêu cầu active use**.

Ba hiện tượng cần nắm vững:

1. **Compile-time constants:** Một constant variable có kiểu nguyên thủy hoặc String, được khai báo final và khởi tạo bằng constant expression, thường được biên dịch thành **giá trị hằng tại nơi dùng**. Đọc field này **không buộc lớp khai báo phải initialization**.
2. **Class literal:** Biểu thức **SomeType.class** nhận **Class<?>** tương ứng mà **không tự buộc chạy static initializer** của SomeType. Lấy Class object khác với gọi **Class.forName(name)** yêu cầu initialization.
3. **Inherited static field:** Nếu **Child.VALUE** thực chất tham chiếu field **VALUE khai báo trong Parent**, thì quy tắc initialization xét **Parent**, không phải tự động xét Child. Dấu tên **Child** trong source không biến field trở thành field do Child khai báo.

Mục tiêu của bài học là dự đoán chính xác **khi nào một lớp được initialized, static code nào chạy, giá trị nào được in**, rồi đối chiếu bằng **javap** và Java 17+. Đồng thời bạn cần hiểu ảnh hưởng tới binary compatibility, JVM class loading và reflection.

## 2. Đặt vấn đề trong vòng đời JVM

Một class/interface thông thường có ba nhóm công việc liên quan:

~~~text
Class bytes
    |
    v
LOADING
  - Tìm/đọc định nghĩa
  - Tạo runtime class và Class object
  - Gắn defining class loader
    |
    v
LINKING
  - Verification: kiểm tra cấu trúc và bytecode
  - Preparation: tạo static fields và giá trị mặc định
  - Resolution: giải quyết symbolic references khi cần
    |
    v
INITIALIZATION khi có yêu cầu
  - Đảm bảo superclass và các superinterface
    thuộc diện cần khởi tạo được xử lý
  - Thi hành quy tắc ConstantValue và code <clinit>
  - Chạy static field initializers / static blocks
    |
    v
Class sẵn sàng cho active use
~~~

**Loading không đồng nghĩa Initialization.** Việc biểu diễn một class tồn tại ở runtime, lớp được verify/prepare hay một symbolic reference đã được resolve **không tự chứng minh static block của class đã chạy**.

**Initialization** xảy ra theo những điều kiện cụ thể trong JLS §12.4.1 và JVMS §5.5. Việc một dòng Java ghi tên lớp chưa đủ để quyết định.

### 2.1. Bảng kiểm tra nhanh

| Thao tác Java | Có yêu cầu initialization của T không? |
| --- | --- |
| **new T()** | **Có**, nếu T là class và chưa được initialized |
| **T.someStaticMethod()** khi method thực sự khai báo tại T | **Có** |
| Ghi **T.someStaticField = value** khi field do T khai báo | **Có** theo quy tắc dùng field |
| Đọc **T.mutableOrRuntimeStaticField** do T khai báo | **Có** nếu field không là constant variable |
| Đọc **T.COMPILE_TIME_CONSTANT** | **Không** kích hoạt initialization chỉ bởi thao tác đọc constant variable |
| **T.class** | **Không** kích hoạt initialization của T chỉ bởi class literal |
| **Class.forName("pkg.T")** | **Có** yêu cầu initialization theo overload một tham số |
| **Class.forName("pkg.T", false, loader)** | **Không** yêu cầu initialization, nhưng vẫn có thể load/link |
| **ClassLoader.loadClass("pkg.T")** thông thường | Không tự yêu cầu initialization |
| **new T[10]** | Không init component class T chỉ vì tạo array |
| **Child.inheritedStaticField** | Xét lớp/interface **thực sự khai báo field**, không suy từ chữ Child |

Bảng nói về **lớp mục tiêu**. Một biểu thức có thể làm **lớp khác** khởi tạo, chẳng hạn code ở biểu thức receiver hoặc một static method được gọi phụ trợ. Cần xác định operation hoàn chỉnh nếu có side effects.

## 3. Constant variable là gì, chính xác theo JLS?

JLS §4.12.4 định nghĩa **constant variable**: một biến **final**, có kiểu **primitive** hoặc **String**, được **khởi tạo bằng một constant expression** (JLS §15.29).

Ba điều kiện đều cần:

1. **final:** giá trị biến không được gán lại sau khi khởi tạo hợp lệ.
2. **Kiểu primitive hoặc String:** ví dụ int, long, boolean, char, double, String; **không** bao gồm Integer, Long, Object, List.
3. **Initializer là constant expression:** biểu thức có thể được compiler tính theo ngữ nghĩa compile time, ví dụ **10 + 20**, **"Hello" + "!"**, **5 > 3**.

**static không nằm trong định nghĩa chung** của constant variable. Tuy nhiên bài này chú trọng **static final fields** vì quy tắc class initialization trực tiếp liên quan tới truy cập static field. Một local variable hoặc instance final field cũng có thể thỏa định nghĩa constant variable theo JLS trong ngữ cảnh của nó.

### 3.1. Ví dụ là compile-time constants

~~~java
// Mã minh họa khai báo trong một class Java:
static final int MAX_USERS = 10 * 4;
static final long TIMEOUT = 2_000L + 500L;
static final String VERSION = "v" + "1";
static final boolean ENABLED = 3 < 5;
static final char LETTER = 'A';
static final double RATIO = 0.5;
~~~

Mỗi field trên có kiểu primitive hoặc String, final, và initializer là một constant expression hợp lệ.

Đặc biệt **VERSION** là String constant expression dù có phép **+**; compiler có thể gộp thành **"v1"** ngay khi biên dịch client.

### 3.2. Những field static final nhưng **không** là constant variable

~~~java
// Mã minh họa trong một class:
static final Integer BOXED = 10;
static final int PARSED = Integer.parseInt("10");
static final String CREATED = new String("hello");
static final String SYSTEM_VALUE = System.getProperty("app.mode");
static final java.util.List<String> NAMES = java.util.List.of("a");
static final int COMPUTED = compute();

static int compute() {
    return 42;
}
~~~

Lý do:

| Field | Vì sao không phải constant variable? |
| --- | --- |
| **BOXED** | Kiểu **Integer** là reference wrapper, không phải primitive hay String |
| **PARSED** | Có lời gọi method **Integer.parseInt**, không là constant expression |
| **CREATED** | Có **new String(...)**, không là constant expression |
| **SYSTEM_VALUE** | Giá trị đến từ runtime system property |
| **NAMES** | Kiểu List không nằm trong nhóm kiểu được JLS cho constant variable |
| **COMPUTED** | Có lời gọi method **compute()**, dù method luôn trả 42 |

Vì vậy **final** không đồng nghĩa “compiler đã biết giá trị”; **static final** cũng không đồng nghĩa “đọc vào không init lớp”. Phân biệt này là trọng tâm khi quan sát output của static blocks.

### 3.3. Constant expression có thể chứa gì?

Các dạng phổ biến theo JLS §15.29:

- Primitive literals, string literals và text blocks khi phù hợp.
- Unary operators **+**, **-**, **~**, **!**; không gồm **++/--**.
- Số học **+**, **-**, **\***, **/**, **%**; các phép dịch bit và một số phép so sánh.
- Logical/bitwise operators, **&&**, **||** và conditional operator **?:**.
- Cast hợp lệ và biểu thức trong dấu ngoặc.
- Tên đơn/qualified names tham chiếu những **constant variables** khác.

Không bao gồm method invocation, **new**, field đọc không phải constant variable, đọc file, đồng hồ, property runtime hoặc phép tính phụ thuộc trạng thái ứng dụng.

Ví dụ:

~~~java
// Mã minh họa:
static final int BASE = 40;
static final int ANSWER = BASE + 2;     // constant variable
static final int DELAY = loadDelay();   // không phải constant variable

static int loadDelay() {
    return 100;
}
~~~

**ANSWER** được tính từ constant variable **BASE** và literal 2 nên là compile-time constant; **DELAY** phụ thuộc lời gọi method.

## 4. Vì sao đọc compile-time constant không kích hoạt <clinit>?

Khi client biên dịch:

~~~java
int max = Config.MAX_USERS;
~~~

với **MAX_USERS** là một **static final int** constant variable, Java compiler phải dùng giá trị hằng của field tại compile time theo quy tắc binary form. Bytecode của client có thể dùng một instruction đẩy hằng như **bipush/sipush/ldc** thay vì **getstatic Config.MAX_USERS**.

Nếu không thực thi **getstatic** để đọc field runtime, thao tác ấy không cần khởi tạo Config chỉ để lấy MAX_USERS.

Ví dụ minh họa:

~~~text
Java source:
    System.out.println(Settings.PORT);

Với Settings.PORT là constant variable int = 8080:

Bytecode client về mặt khái niệm:
    getstatic java/lang/System.out:Ljava/io/PrintStream;
    sipush 8080
    invokevirtual java/io/PrintStream.println:(I)V

Không cần:
    getstatic Settings.PORT:I
~~~

**Dãy lệnh mang tính minh họa**; bytecode chính xác phụ thuộc compiler. Với số 8080, **sipush** là một cách biểu diễn phù hợp vì 8080 nằm trong miền signed short. Điều quan trọng là client sử dụng **giá trị hằng** chứ không bắt buộc đọc static field tại runtime.

**Lưu ý:** Không được suy rằng **Settings chưa được load** chỉ vì static block không in log. JVM có thể đã nạp/liên kết lớp vì lý do khác, nhưng chưa initialization. “Không init” và “chưa load” là hai kết luận khác nhau.

### 4.1. ConstantValue trong class file

Các static final constant fields có thể mang **ConstantValue attribute**. JVMS quy định cách gán giá trị ConstantValue như một phần của quá trình khởi tạo lớp, với thứ tự được đặc tả. Đây là metadata để runtime biết giá trị constant field **nếu/ khi class được khởi tạo**.

Hãy phân biệt hai góc nhìn:

- **Client compile-time:** đọc một constant variable theo Java source có thể dùng giá trị đã inlined, không cần active use khởi tạo lớp khai báo.
- **Class runtime:** class file có khai báo field và thông tin ConstantValue của nó; khi initialization class thực sự diễn ra, VM đảm bảo static constant fields có giá trị đúng theo đặc tả.

Do đó, không có mâu thuẫn khi một client in đúng giá trị 8080 dù static block trong Settings chưa chạy.

### 4.2. Giá trị constant có thể bị nhúng vào client cũ

Nếu thư viện phiên bản 1 có:

~~~java
public class LibraryConfig {
    public static final int LIMIT = 5;
}
~~~

Client đã được biên dịch với phiên bản 1 có thể mang hằng **5** ngay trong bytecode.

Nếu thay thư viện thành:

~~~java
public class LibraryConfig {
    public static final int LIMIT = 7;
}
~~~

mà **không biên dịch lại client**, client cũ có thể tiếp tục dùng **5**. Khi recompile client với phiên bản mới, nó có thể dùng **7**. Đây là một rủi ro **binary compatibility** khi publish public compile-time constants làm configuration thay đổi giữa các bản phát hành.

**Đừng chẩn đoán nhầm là classloader cache:** class runtime mới có thể được nạp đúng nhưng client bytecode vẫn chứa số cũ từ compile time. Muốn giá trị có thể đổi độc lập với client artifact, dùng API truy cập runtime phù hợp (ví dụ static method, cấu hình ngoài hoặc field không phải constant variable) và thiết kế compatibility rõ ràng.

## 5. Ví dụ Java 17+ chạy được: constant, class literal và active use

Tạo file **ConstantVsInitialization.java**:

~~~java
public class ConstantVsInitialization {
    static class Target {
        static final int PORT = 8080;
        static final String LABEL = "api" + "-v1";
        static final Integer BOXED = 12;
        static final int DYNAMIC = Integer.parseInt("21");

        static {
            System.out.println("Target:<clinit>");
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("main:start");

        Class<?> type = Target.class;
        System.out.println("className=" + type.getSimpleName());

        Class.forName("ConstantVsInitialization$Target", false,
                ConstantVsInitialization.class.getClassLoader());

        System.out.println("port=" + Target.PORT);
        System.out.println("label=" + Target.LABEL);
        System.out.println("main:before-boxed");

        System.out.println("boxed=" + Target.BOXED);
        System.out.println("dynamic=" + Target.DYNAMIC);
        System.out.println("main:end");
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac --release 17 -encoding UTF-8 ConstantVsInitialization.java
java ConstantVsInitialization
~~~

**Stdout chính xác cho một lần chạy mới:**

~~~text
main:start
className=Target
port=8080
label=api-v1
main:before-boxed
Target:<clinit>
boxed=12
dynamic=21
main:end
~~~

Phân tích theo các mốc:

1. **Target.class** lấy Class object của Target và **getSimpleName()** truy xuất tên; hai thao tác này không tự yêu cầu khởi tạo Target.
2. **Class.forName(...,false,loader)** không yêu cầu initialization, dù nó có thể thực hiện loading/linking.
3. **Target.PORT** và **Target.LABEL** đều là constant variables. Client đã biết giá trị nên không phải active use khởi tạo Target.
4. Đọc **Target.BOXED** là đọc static field **không phải constant variable**. JVM phải khởi tạo Target trước khi trả field; static initializers chạy theo thứ tự văn bản và in **Target:<clinit>**.
5. Target.BOXED trả Integer 12; khi nối string, giá trị được hiển thị **12**.
6. DYNAMIC được tính trong initialization nên đã có giá trị 21. Lần đọc sau không chạy static block lần nữa.

Ví dụ cố ý đặt **Class.forName(false)** và các constant reads trước nonconstant read. Nếu thay bằng **Class.forName("ConstantVsInitialization$Target")** (overload một tham số), Target có thể khởi tạo ngay tại lời gọi forName đó và thứ tự stdout sẽ khác.

## 6. Không phải mọi static final đều có cách đọc giống nhau

Xem các trường hợp:

| Khai báo | Constant variable? | Đọc lần đầu có yêu cầu class initialization không? |
| --- | --- | --- |
| **static final int A = 1;** | Có | Không chỉ vì đọc A |
| **static final String B = "ab" + "c";** | Có | Không chỉ vì đọc B |
| **static final boolean C = true;** | Có | Không chỉ vì đọc C |
| **static final Integer D = 1;** | Không | Có |
| **static final int E = Math.abs(-1);** | Không | Có |
| **static final String F = new String("abc");** | Không | Có |
| **static int G = 1;** | Không | Có |
| **static final int H = Other.CONST;** với Other.CONST là constant variable | Có nếu initializer là constant expression hợp lệ | Không chỉ vì đọc H |
| **static final int I = Other.runtimeValue;** | Không | Có |

Hãy dùng định nghĩa JLS để suy luận, không đoán bằng chữ **final** hay chỉ dựa vào việc giá trị “trông có vẻ đã biết”.

### 6.1. Compile-time constants không phải một cơ chế cấu hình runtime

Nếu chương trình phải đọc giá trị từ environment, system property, tệp cấu hình hoặc database, đó là runtime value. Không nên viết một public constant variable rồi kỳ vọng đổi JAR là tất cả client thấy giá trị mới ngay.

Lựa chọn thiết kế:

- Giá trị cố định theo API/protocol và không dự kiến thay đổi: **public static final primitive/String constant** có thể phù hợp.
- Giá trị có thể thay đổi theo môi trường: cung cấp method đọc configuration hoặc dependency injection, xác định caching/reload.
- Giá trị runtime bất biến sau bootstrap: dùng **static final** được tính lúc initialization (không phải compile-time constant) hoặc instance immutable config, cân nhắc thời điểm load/init và lỗi.
- Thông tin nhạy cảm: tránh hardcode thành public compile-time constant vì nó có thể xuất hiện trong bytecode client và công cụ phân tích.

## 7. Class literal: T.class cung cấp Class mà không gọi <clinit>

Trong Java, **class literal** là biểu thức như:

~~~java
String.class
int.class
int[].class
java.util.List.class
SomeType.class
~~~

Nó trả về **Class<?>** đại diện cho kiểu tương ứng; không phải thao tác tạo một instance của kiểu ấy.

Ví dụ:

~~~java
// Mã minh họa nằm trong một method:
Class<?> c = Heavy.class;
System.out.println(c.getName());
~~~

Nếu Heavy có static block in **"Heavy initialized"**, chỉ viết **Heavy.class** và gọi **getName()** **không bắt buộc** in thông điệp đó.

Các sự kiện cần phân biệt:

- Có **Class object** không có nghĩa **class đã được initialization**.
- Việc tạo/lấy Class object có thể liên quan loading/linking của kiểu, nhưng **không kích hoạt khởi tạo chỉ do class literal**.
- **Class.getName()**, **getSimpleName()** để nhận tên không tương đương lời gọi static method của chính Heavy.
- **Class.forName(name)** có behavior khác: overload một tham số yêu cầu initialization.
- **Class.forName(name,false,loader)** cho phép lookup không yêu cầu initialization; dùng đúng binary name, ví dụ nested member class có dấu **$**.

### 7.1. Class literal không chạy constructor

So sánh:

~~~java
Class<?> type = Widget.class;  // lấy mô tả kiểu
Widget instance = new Widget(); // tạo instance, active use
~~~

**Widget.class** không tạo Widget object và không gọi constructor. **new Widget()** yêu cầu class Widget được khởi tạo trước, rồi JVM cấp phát object và chạy constructor chain theo quy tắc.

Cũng cần phân biệt:

~~~java
Widget[] array = new Widget[3];
~~~

Dòng trên tạo array chứa ba tham chiếu ban đầu đều null. Nó không tạo ba Widget instances và không yêu cầu initialization của Widget chỉ vì component type là Widget.

### 7.2. Primitive và array class literals

**int.class**, **boolean.class**, **String[].class** đều có Class objects tương ứng; primitive/array types có quy tắc class loading riêng. Ví dụ **int.class.getName()** trả **int**, **int[].class.getName()** trả **[I**, **String[].class.getName()** trả **[Ljava.lang.String;**.

Đừng suy rằng có tệp nhị phân **int.class** hoặc **[I.class** trên classpath. Primitive và array runtime types được JVM xử lý theo cơ chế đặc biệt.

## 8. Ba cách lấy Class và khác biệt initialization

| Cách | Yêu cầu initialization? | Lưu ý |
| --- | --- | --- |
| **T.class** | Không tự yêu cầu | Type được biết tại compile time |
| **Class.forName("pkg.T")** | Có | Dùng overload gọi theo name, yêu cầu initialization |
| **Class.forName("pkg.T", true, loader)** | Có | Chọn loader và yêu cầu init |
| **Class.forName("pkg.T", false, loader)** | Không | Chọn loader, không yêu cầu init |
| **loader.loadClass("pkg.T")** | Thông thường không | Giao ước mặc định loadClass(name) không initialize |
| **obj.getClass()** | Không tự init kiểu mới chỉ từ Class object | Object đã tồn tại; lớp đã được init nếu việc tạo object từng cần |
| **T.class.getDeclaredFields()** | Không nhất thiết init T | Reflection metadata inspection không giống Field.get/Field.set |

Nếu dùng API Reflection **Field.get(null)** để đọc một **static field**, contract của Reflection có thể **khởi tạo class khai báo field trước khi lấy giá trị**, kể cả khi field tương ứng trong Java source là một compile-time constant. Vì vậy không được áp dụng quy tắc **source read of constant variable** một cách máy móc cho mọi reflective access.

### 8.1. Lựa chọn API khi viết framework

Với thư viện muốn **quan sát metadata mà chưa thực thi static code**:

~~~java
// Mã minh họa:
Class<?> cls = Class.forName(binaryName, false, loader);
var fields = cls.getDeclaredFields();
~~~

Đây là hướng tiếp cận thường gặp, nhưng vẫn có thể có tác động loading/linking và exception. Không dùng nó để cam kết “hoàn toàn không thực thi code nào trong hệ thống” — các thành phần liên quan khác có thể đã được khởi tạo trước hoặc có hoạt động riêng.

Khi framework cần tạo instance hoặc gọi static initialization, phải công khai thời điểm active use để người sử dụng hiểu side effects và dependency requirements.

## 9. Static field khai báo ở lớp cha: dấu Child không quyết định initialization

JLS §12.4.1 nói rõ: tham chiếu đến một **static field** chỉ gây initialization của **class/interface thực sự khai báo field** theo điều kiện của field, dù cú pháp có thể dùng tên subclass, subinterface hoặc class implement interface.

Xét:

~~~java
class Parent {
    static int count = 10;
}

class Child extends Parent {
    static {
        System.out.println("Child initialized");
    }
}
~~~

Khi viết:

~~~java
System.out.println(Child.count);
~~~

**count** được khai báo ở **Parent**, không phải Child. Nếu chưa được initialized, JVM phải khởi tạo Parent trước khi đọc nonconstant static field. Nhưng chỉ vì source viết **Child.count**, **không bắt buộc** chạy static initializer của Child.

So sánh hai quy tắc:

- **Child.count**: xác định field declaration thực là Parent.count → nonconstant static read → init Parent.
- **Child.ownCount** nếu field thực sự khai báo trong Child: nonconstant static read → init Child, kéo theo init Parent trước đó nếu chưa.

**Đây là quy tắc theo declaring type**, không phải quy tắc “lấy tên lớp gần dấu chấm nhất”.

### 9.1. Khi field của Parent là compile-time constant

~~~java
class Parent {
    static final int LIMIT = 10;
}

class Child extends Parent {}
~~~

**Child.LIMIT** tham chiếu tới **Parent.LIMIT**, một constant variable. Đọc nó **không yêu cầu initialization** của Parent hay Child chỉ từ thao tác đó. Compiler có thể nhúng literal 10 tại nơi dùng.

Như vậy có hai chiều độc lập:

1. **Ai là declaring class?** — Parent.
2. **Field có phải constant variable?** — Có hoặc không.

Cần trả lời cả hai, không áp dụng một quy tắc chung kiểu “static field luôn init lớp trước”.

### 9.2. Trường bị che khuất (field hiding) thì sao?

~~~java
class Parent {
    static int value = 1;
}

class Child extends Parent {
    static int value = 2;
}
~~~

Ở đây **Child.value** là field thực sự **khai báo trong Child**, không phải field được thừa kế. Vì vậy đọc **Child.value** là active use của Child; JVM phải init superclass Parent trước khi init Child.

Ngược lại, đọc **Parent.value** chỉ cần init Parent. Cùng identifier **value** nhưng là **hai fields khác nhau**, có thể có storage và lifecycle khác nhau.

**Không nhầm field hiding với overriding:** static fields không được override theo dispatch đa hình như instance methods. Compiler giải quyết field reference theo kiểu và declaration thích hợp.

## 10. Ví dụ Java 17+ chạy được: Parent field, Child field, compile-time constant

Tạo file **InheritedStaticDemo.java**:

~~~java
public class InheritedStaticDemo {
    static class Parent {
        static final int LIMIT = 3;

        static int count = startCount();

        static int startCount() {
            System.out.println("Parent:<clinit>");
            return 10;
        }
    }

    static class Child extends Parent {
        static int own = initializeOwn();

        static int initializeOwn() {
            System.out.println("Child:<clinit>");
            return 20;
        }
    }

    public static void main(String[] args) {
        System.out.println("begin");
        System.out.println("constant=" + Child.LIMIT);
        System.out.println("inherited=" + Child.count);
        System.out.println("after-parent");
        System.out.println("own=" + Child.own);
        System.out.println("end");
    }
}
~~~

Chạy bằng JDK 17+:

~~~bash
javac --release 17 -encoding UTF-8 InheritedStaticDemo.java
java InheritedStaticDemo
~~~

**Stdout chính xác:**

~~~text
begin
constant=3
Parent:<clinit>
inherited=10
after-parent
Child:<clinit>
own=20
end
~~~

Phân tích từng dòng:

1. **Child.LIMIT** là field constant khai báo ở Parent. Client dùng giá trị hằng 3; không yêu cầu Parent hay Child initialize.
2. **Child.count** được compiler xác định là **Parent.count**. Đây là nonconstant static field; JVM init Parent, gọi startCount() và in **Parent:<clinit>**.
3. **Child** vẫn chưa cần initialized sau lần đọc **Child.count**.
4. **Child.own** là nonconstant static field khai báo trong Child. JVM init Child; Parent đã init nên không chạy lại.
5. Parent và Child là hai runtime classes có trạng thái initialization riêng; thứ tự source qualifier không làm chúng nhập làm một.

Lưu ý chương trình **chỉ dùng static field reads** để minh họa; không cần tạo instance Child bằng **new Child()**.

## 11. Một trường static được truy cập qua một interface con

Quy tắc “khởi tạo lớp/interface khai báo field” cũng áp dụng với interfaces, nhưng **interface initialization** không giống class inheritance: initialization của subinterface **không tự động** làm tất cả superinterfaces initialize.

Xét chương trình Java 17+ độc lập **InterfaceFieldDemo.java**:

~~~java
public class InterfaceFieldDemo {
    interface Root {
        int CONST = 11;
        int RUNTIME = initRoot();

        static int initRoot() {
            System.out.println("Root:init");
            return 21;
        }
    }

    interface Branch extends Root {
        int OWN = initBranch();

        static int initBranch() {
            System.out.println("Branch:init");
            return 31;
        }
    }

    public static void main(String[] args) {
        System.out.println("begin");
        System.out.println("constant=" + Branch.CONST);
        System.out.println("own=" + Branch.OWN);
        System.out.println("inherited=" + Branch.RUNTIME);
        System.out.println("end");
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 -encoding UTF-8 InterfaceFieldDemo.java
java InterfaceFieldDemo
~~~

**Stdout:**

~~~text
begin
constant=11
Branch:init
own=31
Root:init
inherited=21
end
~~~

Tại sao không in **Root:init** trước **Branch:init**?

- **Root.CONST** là constant variable (interface fields được khai báo ngầm là public static final). Đọc **Branch.CONST** không init Root.
- **Branch.OWN** là field khai báo trong Branch và được tính bởi static method **initBranch()**, không phải constant variable. Đọc nó init Branch.
- **Khởi tạo một interface không tự động khởi tạo superinterfaces**; vì vậy Root chưa cần init vào thời điểm Branch.OWN được đọc.
- **Branch.RUNTIME** tham chiếu field thực sự khai báo trong Root; field không constant → init Root lúc đó.
- Không có nguyên tắc “interface con luôn init interface cha trước” giống superclass initialization của một class.

Đây là ví dụ rõ cho quy tắc **declaring interface** và sự khác biệt giữa kế thừa **class** với kế thừa **interface**.

## 12. Static method cũng xét nơi thực sự khai báo

Quy tắc active use còn liên quan đến static methods. Ví dụ:

~~~java
class Base {
    static void ping() {
        System.out.println("ping");
    }
}

class Derived extends Base {
    static {
        System.out.println("Derived:init");
    }
}
~~~

Nếu một method client viết **Derived.ping()**, Java giải quyết invocation theo declaration thực sự của method (ở đây **Base.ping**). Thao tác static invocation yêu cầu init **Base**, không chỉ vì dùng tên Derived mà làm **Derived** init.

**Tuy nhiên static methods không override đa hình:** Chúng có thể bị hide bởi static method cùng signature trong subclass; khi đó **Derived.ping()** có thể thực sự tham chiếu method khai báo trong Derived và kéo theo initialization của Derived.

Phân biệt các trường hợp:

| Biểu thức | Thành viên thực sự được chọn | Initialization liên quan |
| --- | --- | --- |
| **Derived.inherited** | Static field khai báo trong Base | Base nếu nonconstant |
| **Derived.CONSTANT** | Constant variable khai báo trong Base | Không active use chỉ bởi đọc hằng |
| **Derived.own** | Static field khai báo trong Derived | Derived, rồi superclass nếu cần |
| **Derived.ping()** nếu ping chỉ ở Base | Static method khai báo trong Base | Base |
| **Derived.ping()** nếu Derived khai báo method ẩn Base.ping | Method khai báo trong Derived | Derived |

Khi method hoặc field bị hide, cần xem compiler đã bind tên đến declaration nào. Không thể kết luận bằng cách nhìn identifier đơn lẻ.

## 13. Dùng javap để chứng minh thay vì chỉ đoán từ source

Sau khi tạo hai ví dụ trên, dùng:

~~~bash
javap -c -p ConstantVsInitialization
javap -c -p InheritedStaticDemo
javap -v -p 'InheritedStaticDemo$Parent'
javap -v -p 'InheritedStaticDemo$Child'
~~~

**javap -c** in bytecode, **javap -v** cho constant pool, method descriptors và attributes. Tên nested class có ký tự **$**; nên quote thích hợp trong shell có variable expansion.

### 13.1. Hằng được nạp như thế nào?

Khi in **Child.LIMIT**, compiler có thể sử dụng integer literal trực tiếp, như:

~~~text
iconst_3
~~~

hoặc đưa hằng đã gộp vào một phép nối chuỗi phù hợp. Với chương trình có lời gọi **println("constant=" + Child.LIMIT)**, compiler Java 17 có thể fold cả phần concatenation thành một literal **"constant=3"**. Điều đáng quan sát là việc **không cần getstatic Parent.LIMIT** để lấy giá trị runtime trong client được biên dịch theo JLS.

**Không ép compiler phải sinh một opcode cụ thể**, vì với cùng ngữ nghĩa có thể có tối ưu hằng, **invokedynamic** cho string concatenation và cách khác theo phiên bản.

### 13.2. Field khai báo ở Parent sẽ có owner nào?

Trong bytecode của **InheritedStaticDemo**, thao tác đọc **Child.count** thường chứa symbolic reference đến:

~~~text
getstatic InheritedStaticDemo$Parent.count:I
~~~

không phải **InheritedStaticDemo$Child.count:I**. Compiler xác định field declaration thực là Parent. Bạn có thể dùng **javap -c -p** để xem owner và **getstatic**.

Khi đọc **Child.own**, reference sẽ trỏ tới **InheritedStaticDemo$Child.own:I**; active use của field này khiến Child init.

### 13.3. Class literal thường sử dụng ldc Class

Với **Target.class**, bytecode thường có lệnh kiểu:

~~~text
ldc   #n      // class ConstantVsInitialization$Target
~~~

Đây là cách lấy **Class object**, không phải **invokestatic Target.method** hay **new Target**. Compiler có thể tối ưu tổ chức locals, nhưng semantics class literal không yêu cầu static initialization của Target.

### 13.4. Vì sao không thể dựa toàn bộ vào stdout?

Lớp có thể được **load, verify, prepare** trước khi stdout hiển thị static initializer. Bytecode còn có thể bị JVM resolve tham chiếu theo chiến lược sớm hoặc muộn. Từ **không thấy log "Target:<clinit>"** chỉ nên kết luận **đoạn code log đó chưa chạy**, không phải mọi trạng thái loading/linking đều chưa xảy ra.

## 14. Class literal của lớp cha hoặc lớp con: ví dụ đối chiếu

Tạo **ClassLiteralDemo.java**:

~~~java
public class ClassLiteralDemo {
    static class Parent {
        static {
            System.out.println("Parent initialized");
        }
    }

    static class Child extends Parent {
        static {
            System.out.println("Child initialized");
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("begin");

        System.out.println("name=" + Child.class.getSimpleName());
        System.out.println("parent=" + Child.class.getSuperclass().getSimpleName());
        System.out.println("after-literals");

        Class.forName("ClassLiteralDemo$Child", true,
                ClassLiteralDemo.class.getClassLoader());
        System.out.println("after-forName");
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 -encoding UTF-8 ClassLiteralDemo.java
java ClassLiteralDemo
~~~

**Stdout:**

~~~text
begin
name=Child
parent=Parent
after-literals
Parent initialized
Child initialized
after-forName
~~~

**getSuperclass()** nhận metadata về Parent nhưng không cần chạy Parent static initializer chỉ từ thao tác hỏi superclass. **Class.forName(...,true,loader)** sau đó là active request khởi tạo Child, kéo theo khởi tạo Parent trước Child theo quy tắc class hierarchy.

Điểm đáng chú ý: **Child.class** không init Child; **Child.class.getSuperclass()** không khiến Parent init; nhưng **forName(true)** của Child cần hai lớp trong thứ tự Parent → Child khi chưa khởi tạo.

### 14.1. Lớp con static nested có phụ thuộc khởi tạo lớp ngoài không?

~~~java
class Outer {
    static { System.out.println("Outer initialized"); }

    static class Nested {
        static { System.out.println("Nested initialized"); }
    }
}
~~~

**Outer.Nested.class** không init Nested; active use **new Outer.Nested()** init Nested theo quy tắc riêng của runtime class này. Static nested class **không tự init Outer** chỉ vì về mặt source được khai báo bên trong Outer. Quan hệ tên lồng nhau khác với quan hệ superclass.

## 15. Compile-time inlining và binary compatibility

Xét quá trình build ứng dụng:

~~~text
Lần build 1:
  LibraryConfig.LIMIT = 5
  javac LibraryConfig.java Client.java
  Client.class có thể chứa literal 5

Lần deploy 2:
  Chỉ thay LibraryConfig.class (LIMIT = 7)
  Không biên dịch lại Client.class

Runtime:
  Client.class cũ vẫn có thể in 5
~~~

Các bước này mô tả một tình huống build/deploy, không phải script terminal tự chạy được. Khi class file của client đã lưu giá trị hằng được thay thế ở compile time, runtime **không cần hỏi LibraryConfig.LIMIT**.

### 15.1. Ví dụ hai phiên bản lớp thư viện

Phiên bản 1, tệp **LibraryConfig.java**:

~~~java
public class LibraryConfig {
    public static final int LIMIT = 5;
}
~~~

Tệp client:

~~~java
public class Client {
    public static void main(String[] args) {
        System.out.println("limit=" + LibraryConfig.LIMIT);
    }
}
~~~

Biên dịch cả hai bằng Java 17:

~~~bash
javac --release 17 -encoding UTF-8 LibraryConfig.java Client.java
java Client
~~~

**Stdout phiên bản đầu:**

~~~text
limit=5
~~~

Sau đó thay **LibraryConfig.java** bằng:

~~~java
public class LibraryConfig {
    public static final int LIMIT = 7;
}
~~~

**Chỉ biên dịch lại thư viện**, không biên dịch Client:

~~~bash
javac --release 17 -encoding UTF-8 LibraryConfig.java
java Client
~~~

**Stdout có thể vẫn là:**

~~~text
limit=5
~~~

Cuối cùng biên dịch lại Client với bản mới:

~~~bash
javac --release 17 -encoding UTF-8 Client.java
java Client
~~~

**Stdout sau khi biên dịch lại client:**

~~~text
limit=7
~~~

Các bước chạy giả định thực hiện trong cùng thư mục chứa đúng các tệp và không có một **LibraryConfig.class** khác đứng trước trên classpath. Đây là ví dụ điển hình tại sao public compile-time constants thay đổi có thể gây bất ngờ dù library JAR đã được thay.

### 15.2. Thay hằng bằng method có giải quyết hết không?

Một method như **LibraryConfig.limit()** được gọi tại runtime có thể tránh việc client nhúng hằng giá trị, nhưng phải cân nhắc:

- Thay field bằng method là **thay đổi binary API**: client cũ đang truy cập field có thể không tương thích và cần build lại.
- Runtime method vẫn có vấn đề versioning, class loader identity và config caching riêng.
- Nếu giá trị được đọc từ config, cần định nghĩa khi nào reload, validation và error handling.
- Với constant mang ý nghĩa cố định của protocol, việc inlining có thể là đặc tính được mong muốn.

Giải pháp hợp lý phụ thuộc vào **hợp đồng thay đổi giá trị** chứ không đơn thuần một quy tắc “luôn dùng method”.

## 16. Hằng ở interface và việc kế thừa

Interface fields mặc định là **public static final**, nhưng initializer có thể là runtime expression; vì thế không phải field nào trong interface cũng là constant variable.

~~~java
interface Options {
    int PORT = 8080;                    // constant variable
    int REAL_PORT = readPort();          // không phải constant variable

    static int readPort() {
        return 9090;
    }
}
~~~

Đọc **Options.PORT** không init Options chỉ bởi thao tác ấy. Đọc **Options.REAL_PORT** yêu cầu initialization Options nếu chưa.

Khi interface con kế thừa field:

~~~java
interface ParentInterface {
    int LIMIT = 10;
    int DYNAMIC = calculate();

    static int calculate() { return 20; }
}

interface ChildInterface extends ParentInterface {
    int OWN = 30;
}
~~~

**ChildInterface.LIMIT** và **ChildInterface.DYNAMIC** đều đề cập declarations thực trong ParentInterface. LIMIT là constant variable; DYNAMIC không constant nên khi đọc sẽ init ParentInterface, **không bắt buộc init ChildInterface** chỉ vì cú pháp dùng ChildInterface.

Ngược lại, đọc nonconstant field do ChildInterface tự khai báo sẽ init ChildInterface; interface initialization không tự kéo theo mọi superinterface như superclass initialization của class.

## 17. Một số tình huống nâng cao dễ nhầm

### 17.1. Field hiding khiến declaring type thay đổi

~~~java
class Parent {
    static final int CODE = 1;
}

class Child extends Parent {
    static final int CODE = 2;
}
~~~

**Child.CODE** tham chiếu **Child.CODE**, không phải Parent.CODE. Cả hai vẫn là constant variables nên source reads không tự init lớp tương ứng.

Nếu đổi **Child.CODE** thành **static int CODE = 2;**, đọc **Child.CODE** là active use của Child, kéo theo superclass initialization nếu cần. **Tên field giống nhau** không quyết định; **declaration và constant-variable status** mới quyết định.

### 17.2. Static field access qua expression có side effect

Trong Java, static field có thể được truy cập bằng một expression là tên tham chiếu:

~~~java
// Mã minh họa, giả định Base.number là static int:
Base ref = null;
int value = ref.number;
~~~

Dù ref là null, thao tác đọc **static field** không tự dereference ref như instance field nên không ném NullPointerException chỉ vì ref null. Truy cập static field vẫn xét declaring class, không dựa vào giá trị object reference.

Khi qualifier là một **expression có side effect**, Java có thể phải đánh giá expression theo quy tắc ngôn ngữ rồi bỏ giá trị đi trước khi thao tác field. Vì vậy để phân tích **mọi stdout và exceptions**, phải xét cả expression qualifier, không chỉ initialization của declaring class.

**Khuyến nghị:** Luôn viết **Base.number** thay vì **ref.number** với static field để tránh gây hiểu lầm.

### 17.3. Class literal và reflection không hoàn toàn tương đương

**T.class** không init T. Nhưng các reflection operations như **Field.get(null)**, **Field.set(null, value)** cho static field có thể yêu cầu initializing declaring class theo API. Do đó:

~~~java
// Minh họa trong method có throws Exception:
Class<?> type = T.class;                  // không yêu cầu init T
var field = type.getDeclaredField("X");   // xem metadata
Object value = field.get(null);           // có thể init declaring class
~~~

Code trên không độc lập vì **T** phải được khai báo và **X** phải tồn tại, accessible. Việc gọi getDeclaredField/getDeclaredFields có thể liên quan loading/linking metadata; không được suy thành một blanket promise “reflection không init”.

### 17.4. Đọc constant variable trong chính class khai báo

Nếu class có:

~~~java
static final int CONSTANT = 8;
static int VALUE = CONSTANT * 2;
~~~

**VALUE** được khởi tạo trong quá trình initialization, còn compiler có thể dùng constant expression **16** cho initializer. Điều này không có nghĩa class không bao giờ init: đọc **VALUE** từ bên ngoài vẫn là nonconstant static field access và yêu cầu class initialization.

Nếu class chỉ khai báo compile-time constants, compiler có thể không tạo một **<clinit>** chứa instructions. Dù vậy Java vẫn xác định lifecycle class; không có <clinit> output không cho phép kết luận “kiểu này hoàn toàn không có bước initialization trong mô hình JVM”.

### 17.5. Class loaders và hai runtime copies

Hai class loaders khác nhau có thể define hai classes cùng binary name. Mỗi class runtime có trạng thái initialization riêng và static fields riêng. Một client đã nhúng compile-time constant thậm chí không cần load class nơi field ban đầu được khai báo trong quá trình đọc hằng.

Ngược lại, khi dùng class literal hoặc forName qua loader khác, bạn có thể nhận **Class object khác**, dù tên giống nhau. Đây là lý do cần xác định **(binary name, defining loader)** khi điều tra sự cố class initialization trong plugin/application server.

## 18. Lỗi thường gặp và hướng sửa

| Nhận định sai | Vì sao sai | Cách kiểm tra |
| --- | --- | --- |
| “Tất cả static final đều không init class.” | Wrapper, new String, method calls... không là constant variables | Kiểm tra JLS 4.12.4 và initializer |
| “Chỉ cần source viết Child.X thì Child init.” | X có thể do Parent khai báo | Tìm declaring field; xem owner của getstatic trong javap |
| “Lấy Child.class sẽ in static block.” | Class literal không phải active use | Test static block và class literal |
| “Class.forName luôn chỉ load.” | forName(name) yêu cầu init; overload false không | Chọn overload đúng |
| “String constant có + thì không là hằng.” | Nối các compile-time string constants vẫn có thể là constant expression | Xem constant-expression grammar |
| “Client tự đọc giá trị constant mới khi đổi JAR.” | Giá trị có thể đã nhúng trong Client.class | Rebuild/recompile client; xem javap -c |
| “Có Class object chứng minh <clinit> đã chạy.” | Loading/linking khác initialization | Quan sát side effects của initializer và active use |
| “Static final Integer giống static final int.” | Integer là reference wrapper, không là constant variable | Xác định kiểu khai báo |
| “Truy cập Child.parentField gây cả Parent và Child init.” | Chỉ declaring field bị active use, nếu nonconstant | Quan sát hai static initializers độc lập |
| “Mọi reflection access đều không init.” | Field.get và một số operations có yêu cầu init | Đọc contract API tương ứng |
| “Tạo new T[10] làm T init.” | Array creation khác tạo T instance | Phân biệt anewarray với new |
| “Source chỉ có một class nên chỉ có một lifecycle.” | Nested/static member classes có runtime class riêng | Javap từng class, quan sát init riêng |

## 19. Quy trình dự đoán stdout cho một chương trình

Khi gặp câu hỏi có **static blocks, constants, class literals, Parent/Child**, đừng đoán bằng trực giác. Hãy thực hiện:

1. **Lập danh sách runtime classes** và cấu trúc kế thừa/interface; phân biệt nested class và superclass.
2. **Ghi nơi khai báo thật** của từng field/method được dùng, không suy theo tên qualifier đứng trước dấu chấm.
3. **Phân loại static fields:** constant variable nếu final + primitive/String + constant expression, hoặc runtime field nếu không.
4. **Đánh dấu tác vụ active use**: new instance, static method invocation, nonconstant static field read/write, reflective initialization; bỏ qua class literal hoặc constant read nếu chỉ có chúng.
5. **Khi class T init lần đầu**, thêm superclass initialization và các superinterfaces thích hợp theo quy tắc, rồi chạy static initializers của T theo thứ tự văn bản; class đã init thành công không chạy lại.
6. **Với interface**, nhớ initialization của interface không tự init tất cả superinterfaces.
7. **Đọc side effects** của những initializer thực sự được gọi, ghi stdout theo thứ tự.
8. **Xem class literal/forName(false)** như những thao tác có thể load/link nhưng chưa yêu cầu init.
9. **Đối chiếu javap**: constants xuất hiện dưới dạng literal, runtime static fields dùng getstatic với owner declaration; string concatenation có thể bị compiler fold.
10. **Nếu có nhiều class loaders**, tách trạng thái của từng runtime class theo defining loader trước khi lập timeline.

Bảng tự luyện:

| Bước | Biểu thức source | Declaring type | Constant variable? | Class nào phải init? | Output/side effect |
| --- | --- | --- | --- | --- | --- |
| 1 | **Child.CONST** | Parent | Có | Không do thao tác này | In hằng |
| 2 | **Child.runtimeValue** | Parent | Không | Parent | Parent static initializer |
| 3 | **Child.class** | Không phải field | Không áp dụng | Không do class literal | Nhận Class object |
| 4 | **Child.ownRuntime** | Child | Không | Child (và superclass chưa init nếu cần) | Child static initializer |

## 20. Bài tập tự kiểm tra

**Bài 1.** Field nào là constant variable?

~~~java
static final int A = 3 * 7;
static final Integer B = 21;
static final String C = "ja" + "va";
static final int D = Math.max(20, 21);
~~~

**Đáp án:** **A** và **C**. B có kiểu Integer; D có method call.

**Bài 2.** Đoạn sau có in **"init"** khi chỉ thực hiện dòng client không?

~~~java
class Settings {
    static final int PORT = 8080;
    static { System.out.println("init"); }
}
int p = Settings.PORT;
~~~

**Đáp án:** Không do thao tác đọc constant variable; compiler có thể dùng trực tiếp literal 8080. Phải đặt dòng client vào một phương thức hợp lệ để chạy chương trình.

**Bài 3.** Class literal của lớp có static block có init nó ngay không?

~~~java
Class<?> c = Settings.class;
~~~

**Đáp án:** Không. Class literal tạo/lấy Class object, không phải active use khởi tạo Settings.

**Bài 4.** Hai phương thức forName có tác dụng initialization giống nhau không?

~~~java
Class.forName("pkg.Settings");
Class.forName("pkg.Settings", false, loader);
~~~

**Đáp án:** Không. Overload một tham số yêu cầu initialization; overload **false** không yêu cầu. Ví dụ chỉ minh họa, cần lớp và loader hợp lệ.

**Bài 5.** Nếu **Parent.value** là nonconstant static field, còn **Child extends Parent** không khai báo value, đọc **Child.value** làm class nào init?

**Đáp án:** Parent; Child không init chỉ vì được dùng làm qualifier.

**Bài 6.** Nếu Child **khai báo** một field value mới có cùng tên với Parent.value, đọc **Child.value** thì sao?

**Đáp án:** Field được chọn là Child.value; nếu nonconstant, Child phải initialize (và superclass Parent được initialize trước nếu cần).

**Bài 7.** Interface Root khai báo **int C = 1** và **int R = compute()**, Branch extends Root. Đọc **Branch.C** và **Branch.R** có gì khác?

**Đáp án:** C là constant variable nên đọc không init Root; R là runtime field nên đọc gây init Root. Branch không tự init chỉ vì qualifier.

**Bài 8.** Thư viện thay **public static final String VERSION = "v1"** thành **"v2"**, nhưng client cũ vẫn in v1. Vì sao?

**Đáp án:** VERSION là constant variable, client có thể chứa hằng đã nhúng lúc biên dịch. Biên dịch lại client với thư viện mới; nếu cần runtime-configurable value, thiết kế API khác.

**Bài 9.** **static final String NAME = new String("A")** có phải compile-time constant không?

**Đáp án:** Không. Có **new**, không phải constant expression; đọc static field NAME yêu cầu init class.

**Bài 10.** Một chương trình chỉ làm **new Widget[3]** và chưa từng dùng Widget. Static block trong Widget có chạy bắt buộc không?

**Đáp án:** Không. Tạo array không tạo Widget instance, không phải active use khởi tạo Widget.

**Bài 11.** **Field.get(null)** đối với static final int constant có thể làm class khai báo init, dù đọc trực tiếp bằng Java source không làm không?

**Đáp án:** Có. Reflection Field.get với static field có hợp đồng riêng về initialization; không áp dụng quy tắc compiler inlining cho lời gọi reflection.

**Bài 12.** Vì sao **javap** có thể không có **getstatic Parent.LIMIT** cho dòng source **Child.LIMIT**?

**Đáp án:** LIMIT là constant variable, giá trị được giải quyết ở compile time và được sử dụng trực tiếp trong client bytecode. Child là qualifier source, không phải declaring type và không cần init để đọc hằng.

**Bài 13.** Đặt **Class.forName("ClassLiteralDemo$Child", true, loader)** sau một **Child.class.getName()** trong một JVM mới: lớp nào init trước nếu chưa init và Child extends Parent?

**Đáp án:** Lớp Parent được init trước Child khi JVM init Child; class literal/getName không buộc init cả hai trước lời gọi forName(true).

## 21. Checklist khi viết code và khi làm phỏng vấn JVM

- Trước tiên xác định **compile-time constant** hay **runtime static field**.
- Định nghĩa đúng: **final + primitive/String + constant expression**, không mặc định mọi **static final** là hằng biên dịch.
- Với **T.class**, không suy ra static code đã chạy; với **Class.forName**, đọc **đúng overload** và tham số initialize.
- Khi source ghi **Child.X**, tìm **lớp/interface thực sự khai báo X**; không tự cho Child initialize.
- Với field bị hide, xác định declaration được compiler chọn theo kiểu của expression.
- Với **subinterface**, nhớ init interface không tự init superinterfaces.
- Với client build lâu đời, nghi ngờ **inlined public constants** nếu runtime thấy giá trị cũ.
- Khi dùng Reflection, không áp dụng máy móc quy tắc constant read trong Java source; đọc API contract.
- Nếu cần giải thích chính xác, biên dịch và đọc **javap -c/-v**, quan sát hằng, **getstatic owner** và Class literal.
- Dùng static initializer có side effect một cách thận trọng: chúng chỉ chạy khi có initialization request và có thể ném lỗi khiến class ở trạng thái erroneous.
- Khi debugging với plugin, luôn xét **defining class loader**, vì cùng tên lớp có thể là hai runtime classes.
- Tránh phụ thuộc vào thời điểm loading/linking chi tiết mà JVM được phép quyết định linh hoạt.

### Tài liệu chuẩn

- **JLS Java SE 17, §4.12.4 — final Variables / Constant Variables:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.12.4
- **JLS Java SE 17, §15.29 — Constant Expressions:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.29
- **JLS Java SE 17, §12.4.1 — When Initialization Occurs:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.1
- **JLS Java SE 17, §12.4.2 — Detailed Initialization Procedure:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.2
- **JLS Java SE 17, §13.4.9 — final Fields and Constants / Binary Compatibility:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html#jls-13.4.9
- **JVMS Java SE 17, §5.5 — Initialization:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5
- **JVMS Java SE 17, §4.7.2 — ConstantValue Attribute:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.2
- **Java SE 17 API — Class.forName:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- **Java SE 17 API — java.lang.reflect.Field:** https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html

**Ghi nhớ:** **Constant variable** cho phép client dùng hằng compile time mà không buộc lớp khai báo init; **class literal** trả Class object nhưng không tự chạy **<clinit>**; **Child.field** phải được giải quyết về **declaring class/interface thực sự** rồi mới áp dụng quy tắc active use. Khi phân tích initializer, câu hỏi chính xác không phải “source có nhắc tới tên lớp nào?”, mà là **“thao tác runtime nào xảy ra, field/method khai báo ở đâu, và đó có phải constant variable không?”**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="linking-and-initialization-009">Thứ tự khởi tạo lớp cha, giao diện liên quan và thành viên static</a>

<details>
<summary>Xem chi tiết</summary>

# Thứ tự khởi tạo lớp cha, giao diện liên quan và thành viên `static` trong JVM

Khi lần đầu tạo một đối tượng của class con, JVM không thực thi ngay các câu lệnh `static` của class con. Nó phải đảm bảo những kiểu liên quan được khởi tạo theo đúng **quy tắc của Java Language Specification (JLS) và Java Virtual Machine Specification (JVMS)**. Thông thường, **superclass khởi tạo trước subclass**. Nhưng còn một chi tiết quan trọng dễ bị bỏ sót: khi khởi tạo một **class**, JVM cũng khởi tạo những **superinterface trực tiếp hoặc gián tiếp có khai báo default method** theo thứ tự đệ quy được quy định. Những interface không khai báo default method **không tự động bị khởi tạo chỉ vì class đó `implements` chúng**.

Trong mỗi class hoặc interface, các initializer của thành viên `static` và các static initialization blocks hợp lệ được thực thi **theo thứ tự xuất hiện trong source** khi đến lượt kiểu đó. Ngoài ra, `static final` là **compile-time constant** có cơ chế `ConstantValue` riêng và việc đọc constant trong source thường **không kích hoạt khởi tạo** của class/interface khai báo.

Các quy tắc này giải thích thứ tự log gây ngạc nhiên trong hệ thống plugin, singleton, cache tĩnh, inheritance và code có default methods. Bài học xây dựng mô hình chính xác rồi dùng các chương trình **Java 17+ độc lập, runnable, stdout xác định** để kiểm tra.

## 1. Loading, linking, preparation và initialization không phải một

JVM quản lý vòng đời class và interface theo những khái niệm khác nhau:

| Hoạt động | Mục đích | Có chạy các câu lệnh `static { ... }` của class không? |
| --- | --- | --- |
| **Loading** | Tìm hoặc tạo biểu diễn của class/interface qua class loader | **Không** chỉ vì loading |
| **Verification** | Kiểm tra cấu trúc và các ràng buộc bytecode/type | **Không** chỉ vì verification |
| **Preparation** | Chuẩn bị static fields và giá trị mặc định theo quy tắc JVM | **Không** chạy initializer biểu thức người viết |
| **Resolution** | Liên kết các symbolic references đến class/field/method thật | **Không nhất thiết** kích hoạt initializer của kiểu liên quan |
| **Initialization** | Thực hiện thủ tục khởi tạo, đặt constant values thích hợp và chạy `<clinit>` nếu có | **Có**, ở đúng thời điểm theo thủ tục |

Mô hình:

```text
class bytes
    │
    ▼
Loading
    │
    ▼
Linking
    ├─ Verification
    ├─ Preparation: static fields có default values
    └─ Resolution (có thể xảy ra theo nhu cầu)
    │
    ▼
Initialization
    ├─ xử lý ConstantValue theo quy tắc
    ├─ nếu là class: khởi tạo superclass rồi những superinterface phù hợp
    ├─ chạy mã <clinit> của chính class/interface nếu có
    └─ ghi nhận trạng thái fully initialized
    │
    ▼
Tiếp tục thao tác đã yêu cầu khởi tạo
```

**Đừng đồng nhất “đã được nạp” với “đã chạy static initializer”.** Một class có thể được load, verify, prepare hoặc resolve một số references mà chưa thực hiện initialization.

### 1.1. Các giá trị mặc định xuất hiện trước code initializer

Khi preparation, một static field kiểu `int` có giá trị mặc định `0`, `boolean` là `false`, reference là `null`. Sau đó khi khởi tạo kiểu, những initializer biểu thức mới đặt các giá trị do lập trình viên tính toán.

```java
static int count = 7;
static Object cache = new Object();
```

Không nên suy rằng `count` đã là 7 và `cache` đã trỏ tới object mới ngay khi class vừa được load. Việc chạy biểu thức `new Object()` là hành vi của initialization, không phải một bước mặc định trong preparation.

**Ngoại lệ cần nhớ:** trường `static final` là compile-time constant có thể có `ConstantValue` attribute. JVMS xử lý giá trị constant theo bước riêng trong thủ tục khởi tạo, **trước bước khởi tạo superclass**. Điều này không có nghĩa mọi `static final` đều được gán theo cách ấy: `static final Integer BOXED = 42;` chẳng hạn không phải constant variable theo JLS.

## 2. Khi nào một class hoặc interface thật sự khởi tạo?

JLS `12.4.1 mô tả các tình huống kích hoạt initialization. Những trường hợp thường gặp:

1. **Tạo instance của class `C`**: `new C()` cần khởi tạo `C` trước khi tạo instance theo điều kiện tương ứng.
2. **Gọi static method được khai báo bởi `C`**: `C.work()` dẫn đến initialization của kiểu **khai báo method được resolve**, nếu chưa khởi tạo.
3. **Gán static field**: `C.value = 1` kích hoạt initialization của kiểu khai báo field tương ứng.
4. **Đọc static field không phải constant variable**: `System.out.println(C.value)` kích hoạt initialization của kiểu khai báo field.
5. **Một số thao tác reflection và method handles** có thể yêu cầu initialization, theo API/loại tham chiếu cụ thể.
6. **Class khởi đầu chương trình** được khởi tạo trước khi chạy `main` tương ứng.

Ở mức JVM, các instruction thường liên quan là `new`, `getstatic`, `putstatic` và `invokestatic`. Nhưng để biết chính xác **kiểu nào** phải initialize, cần xem **declaring class/interface của field hoặc method sau resolution**, không chỉ đọc tên viết ở source.

### 2.1. Những thao tác KHÔNG đủ để kết luận đã khởi tạo

| Thao tác | Tự nó có bắt buộc khởi tạo class được nhắc tới? |
| --- | --- |
| `C.class` | **Không**, class literal không phải active use buộc chạy `<clinit>` |
| `new C[5]` | **Không** buộc initialize component class `C` chỉ vì tạo array |
| Khai báo `C variable;` hoặc method signature dùng `C` | **Không** chỉ vì đề cập kiểu |
| `C.CONSTANT` với constant variable theo JLS | **Không**; compiler có thể inline constant |
| `Class.forName("pkg.C", false, loader)` | **Không yêu cầu** initialize `C` |
| `Class.forName("pkg.C")` | **Có**, overload phổ biến này yêu cầu initialization (với loader theo quy tắc API) |
| `loader.loadClass("pkg.C")` | Không nên coi đây là thao tác bắt buộc thực thi initializer |
| `obj instanceof C` | Không tự buộc initialization của `C` chỉ do phép kiểm tra này |

**Lưu ý:** class có thể đã được initialized trước đó bởi thao tác khác, hoặc việc resolution/loading có thể xảy ra sớm. Bảng nói rằng một thao tác **không tự nó đòi khởi tạo**, không hứa class tuyệt đối vẫn chưa được khởi tạo trong toàn tiến trình.

### 2.2. Inherited static field: tên class con không quyết định kiểu khởi tạo

```java
class Parent {
    static int size = 5;
}
class Child extends Parent {}
```

Với `Child.size`, field thực sự **được khai báo bởi Parent**. Theo quy tắc truy cập static field, đọc `Child.size` kích hoạt initialization của **Parent**, **không tự bắt Child khởi tạo** chỉ vì source viết tiền tố `Child`. Kết quả này trái với trực giác “chạm Child thì chạy static của Child”.

Điều tương tự áp dụng cho static field thừa kế từ interface/superinterface: cần quan sát nơi **khai báo field** sau resolution.

## 3. Quy tắc superclass trước subclass

Đối với một class `Sub extends Base`, khi JVM phải initialize `Sub`, nó bảo đảm `Base` đã được initialized trước khi chạy các initializer biểu thức và static blocks của `Sub`. Nếu `Base` còn có superclass chưa initialized, chuỗi tiếp tục lên trên.

Ví dụ đơn giản:

```java
class Grandparent {
    static { System.out.println("Grandparent"); }
}
class Parent extends Grandparent {
    static { System.out.println("Parent"); }
}
class Child extends Parent {
    static { System.out.println("Child"); }
}
```

Nếu trong một chương trình độc lập các class này chưa khởi tạo mà lần đầu thực hiện `new Child()`, thứ tự log dự kiến:

```text
Grandparent
Parent
Child
```

Đây là **ví dụ rút gọn về quan hệ khởi tạo**, không phải chương trình có method `main`. Constructor và instance field initialization là bước khác xảy ra **sau khi class initialization liên quan được hoàn tất**.

### 3.1. Không phải “đợi tới khi gọi constructor của Base mới chạy static”

Java object creation có liên quan constructor chaining `super(...)`, nhưng **static initialization** và **instance initialization** là hai quá trình khác nhau.

```text
new Child()
  ├─ class initialization (nếu cần)
  │    ├─ Grandparent <clinit>
  │    ├─ Parent <clinit>
  │    └─ Child <clinit>
  └─ tạo/khởi tạo instance
       ├─ chạy constructor chain và instance field initializers theo JLS
       └─ trả reference tới instance hoàn tất
```

Không nhầm `<clinit>` (class/interface initialization) với `<init>` (instance constructor). Một class có thể khởi tạo thành công đúng một lần theo định danh class và class loader nhưng tạo **nhiều object instances** bằng constructor.

### 3.2. Parent đã initialized thì không in lại

Nếu một thao tác trước đó đã khởi tạo `Parent`, rồi mới `new Child()`, JVM **không chạy lại** static code `Parent` chỉ vì class con đang được initialized. “Super trước sub” là **thứ tự đảm bảo trạng thái**, không bắt buộc mọi trigger đều in cùng toàn bộ chuỗi log mới.

## 4. Giao diện (`interface`): không thể áp dụng máy móc quy tắc class inheritance

Java hỗ trợ interface inheritance và default methods. Điều kiện khởi tạo interface có **hai trường hợp khác nhau**.

### 4.1. Khởi tạo riêng một interface không tự khởi tạo các superinterface của nó

Ví dụ:

```java
interface Root {
    int X = Helper.computeRoot();
}
interface Child extends Root {
    int Y = Helper.computeChild();
}
```

Nếu code truy cập **`Child.Y`** (non-constant expression) và chưa có loại nào được khởi tạo, điều này yêu cầu initialize **Child interface**. Nó **không tự động initialize Root** chỉ vì `Child extends Root`.

Ngược lại, `Root.X` sẽ yêu cầu initialize `Root` vì `Root` khai báo field `X`. Đây là khác biệt quan trọng so với quy tắc bắt buộc khởi tạo superclass khi initialize một **class**.

Hai dòng trên là **mã minh họa cần cung cấp `Helper`**, không phải một file độc lập. Ví dụ runnable minh họa interface rules nằm ở mục 6.

### 4.2. Nhưng khi initialize một CLASS: default-method superinterfaces có vai trò đặc biệt

Trong Java 17, khi class `C` đang initialize, JVMS `5.5 / JLS `12.4.2 yêu cầu xử lý:

1. Superclass của `C` (nếu có) trước.
2. Những superinterfaces trực tiếp hoặc gián tiếp **khai báo ít nhất một default method** — chính xác hơn ở mức JVMS là các superinterface khai báo phương thức instance **không abstract, không static** theo điều kiện của đặc tả.
3. Sau đó mới chạy mã initialization của bản thân `C`.

Các superinterfaces được xác định theo cách **duyệt đệ quy có thứ tự**: duyệt từng interface trong danh sách `implements` từ trái sang phải, truy sâu các superinterfaces của interface ấy theo thứ tự khai báo **trước khi** quay lại interface hiện tại. Trong các interface tìm được, chỉ những interface đáp ứng điều kiện default-method ở trên mới phải initialize vì quy tắc này.

```text
class Leaf extends Parent implements ChildInterface, Marker
  │
  ├─ initialize Parent trước
  │
  ├─ duyệt hierarchy interfaces của Leaf
  │    ├─ ChildInterface
  │    │     └─ RootInterface  [có default method] → init RootInterface
  │    │  ChildInterface      [có default method] → init ChildInterface
  │    └─ Marker              [không default method] → không auto-init
  │
  └─ initialize Leaf
```

**Chú ý từ ngữ:** một interface chỉ **thừa kế** default method từ parent nhưng **không tự khai báo** default method không nhất thiết được initialize do chính điều kiện “declares default methods”. Khi đọc code sâu, phải nhìn phương thức **được khai báo** trong từng interface, không chỉ xem nó có thừa hưởng phương thức nào.

### 4.3. Static methods trong interface không phải default methods

```java
interface Utilities {
    static void run() {}
}
```

Static method trong interface **không khiến interface đó trở thành default-method interface** cho quy tắc auto-initialization của một class implements nó. Phương thức `default void run() {...}` mới là trường hợp khác. Mặc dù lời gọi `Utilities.run()` tự nó có thể kích hoạt initialization của `Utilities`, việc một class chỉ `implements Utilities` không buộc interface đó phải init trước class.

### 4.4. Không thể khai báo `static { ... }` block trong interface

Java interface fields mặc nhiên là `public static final`, nhưng không được có static initializer block `static { ... }` giống class. Interface vẫn có thể làm công việc initialization nhờ **biểu thức initializer của một field non-constant**:

```java
interface Marker {
    int FLAG = Logger.log("Marker initialized");
}
```

Nếu field initializer gọi một method như trên, giá trị `FLAG` **không phải compile-time constant**. Lần đọc `Marker.FLAG` có thể kích hoạt initialization và chạy biểu thức, dù không có static block trong source interface.

## 5. Static fields và static blocks: thứ tự văn bản

Trong một class, các static variable initializers và static initializer blocks được xử lý **theo thứ tự xuất hiện trong code** khi class đó initialize. Đây là một chuỗi có side effects; đổi thứ tự khai báo có thể đổi stdout, dữ liệu hoặc gây lỗi.

```java
class Order {
    static int first = trace("first");
    static { trace("block 1"); }
    static int second = trace("second");
    static { trace("block 2"); }
    static int trace(String value) {
        System.out.println(value);
        return 1;
    }
}
```

Khi `Order` được initialization lần đầu, code tương ứng sẽ in:

```text
first
block 1
second
block 2
```

Đây là đoạn class minh họa. Không phải JVM sẽ chạy lần lượt mọi method trong class: method `trace` chỉ được gọi vì các initializer đã gọi nó.

### 5.1. Compiler đưa code vào `<clinit>`

`javac` thường tổng hợp các static field initializers có runtime operations và static blocks vào class initialization method tên **`<clinit>`**, descriptor **`()V`**.

```text
source:
  static int n = compute();
  static { log(); }

bytecode logic:
  <clinit>:
    invokestatic compute:()I
    putstatic n:I
    invokestatic log:()V
    return
```

`<clinit>` là phương thức JVM dành cho class/interface initialization, **không** được gọi trực tiếp bằng Java như `MyClass.<clinit>()` và không phải constructor `<init>`.

Không phải mọi class có static fields đều cần có `<clinit>` trong class file. Những class chỉ có compile-time constants hoặc không có code initialization có thể **không chứa `<clinit>`**. JVM vẫn duy trì trạng thái initialization của class/interface theo quy tắc.

### 5.2. Nguyên tắc “thứ tự trong từng lớp”, không gộp source cha/con

Nếu `Parent` khai báo:

```java
static int p = initP();
static { logP(); }
```

và `Child` khai báo:

```java
static int c = initC();
static { logC(); }
```

thì JVM thực thi thứ tự **theo từng kiểu**:

```text
Parent.initP → Parent.logP
Child.initC  → Child.logC
```

Nếu Child có default-method superinterfaces chưa initialized, các interface đó có thể **chen giữa hai nhóm** theo thủ tục của `5.5`; không được kết luận Parent static xong là Child static chạy ngay.

### 5.3. Constant variables được xử lý đặc biệt

Một **constant variable** theo JLS là `final` variable kiểu primitive hoặc `String` được khởi tạo bằng **constant expression**:

```java
static final int MAX = 10;
static final String NAME = "service";
```

Các reference trong source có thể được compiler **nhúng giá trị vào bytecode người dùng**, không dùng `getstatic` để đọc field khi đã được fold, nên **không khởi tạo kiểu khai báo** chỉ vì đọc constant.

Ngược lại:

```java
static final Integer BOXED = 10;
static final int LENGTH = new String("abc").length();
static final String TEXT = new String("ready");
```

Không phải constant variables theo định nghĩa trên: `Integer` là reference wrapper; lời gọi constructor/method là runtime expressions. Truy cập các static fields này theo cách thông thường có thể kích hoạt initialization của declaring type.

**Đừng đồng nhất từ khóa `final` với constant-foldable.** `static final` object và collection không tự trở thành compile-time constants.

## 6. Ví dụ chạy được: superclass, default-method interfaces, marker interface

Tạo file **`InitHierarchyDemo.java`** trên JDK 17 trở lên:

```java
public class InitHierarchyDemo {
    static int trace(String message) {
        System.out.println(message);
        return 1;
    }

    interface Root {
        int VALUE = trace("Root.field");
        default void rootMethod() {}
    }

    interface Branch extends Root {
        int VALUE = trace("Branch.field");
        default void branchMethod() {}
    }

    interface Marker {
        int VALUE = trace("Marker.field");
    }

    static class Parent {
        static int parentValue = trace("Parent.field");
        static { trace("Parent.block"); }
    }

    static class Leaf extends Parent implements Branch, Marker {
        static int leafValue = trace("Leaf.field");
        static { trace("Leaf.block"); }
    }

    public static void main(String[] args) {
        System.out.println("before new");
        new Leaf();
        System.out.println("after new");
        System.out.println("markerValue=" + Marker.VALUE);
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 InitHierarchyDemo.java
java InitHierarchyDemo
```

**Stdout chính xác:**

```text
before new
Parent.field
Parent.block
Root.field
Branch.field
Leaf.field
Leaf.block
after new
Marker.field
markerValue=1
```

### 6.1. Lý giải từng dòng

1. Khi chương trình vào `main`, class ngoài `InitHierarchyDemo` đã được initialized. Nhưng các **nested types** `Parent`, `Leaf` và interfaces `Root`, `Branch`, `Marker` chưa bắt buộc phải initialize chỉ vì class ngoài có tên của chúng.
2. `System.out.println("before new")` in trước khi tạo object.
3. `new Leaf()` yêu cầu initialize `Leaf`. JVM kiểm tra superclass `Parent` trước: field initializer in `Parent.field`, rồi static block in `Parent.block`.
4. `Leaf` trực tiếp `implements Branch, Marker`. Việc duyệt interface `Branch` truy về `Root`: cả hai **tự khai báo default method**, nên init `Root` rồi `Branch`. Field initializer non-constant tương ứng in `Root.field` và `Branch.field`.
5. `Marker` không khai báo default method. Do đó **chưa** bắt buộc initialize trong bước xử lý default-method superinterfaces của `Leaf`.
6. Sau các kiểu cần trước, mã static của `Leaf` chạy theo thứ tự: field rồi block.
7. `after new` in sau khi thao tác tạo `Leaf` hoàn tất.
8. `Marker.VALUE` là field `int` với initializer qua lời gọi `trace(...)` nên **không phải constant variable**. Lệnh truy cập gây initialization của `Marker`, in `Marker.field` trước khi in `markerValue=1`.

**Nếu xóa `default void rootMethod() {}` khỏi `Root`:** ở một class-file mới được biên dịch, `Root` không còn thuộc nhóm tự khai báo default method, nên một `new Leaf()` đơn thuần không tự yêu cầu `Root` initialize vì nhánh này. Đây là cách tốt để tự kiểm nghiệm vì sao interface graph và quy tắc “có khai báo default method” quan trọng.

### 6.2. Từ đâu ra thứ tự Root rồi Branch?

Cơ chế duyệt interface hierarchy là **đệ quy trước khi quay lại interface trực tiếp**. `Leaf` duyệt `Branch`, rồi `Root` là superinterface của `Branch`; sau đó quay lại `Branch`, cuối cùng tới `Marker`.

Chỉ các interface phù hợp điều kiện được initialize. Vì vậy kết quả là `Root` rồi `Branch`. `Marker` dù nằm trong `implements` vẫn chưa initialized ở thời điểm `Leaf` chạy static block.

Nếu có nhiều superinterfaces hình kim cương, thuật toán bảo đảm không chạy lại initialization đã hoàn tất; cùng một interface có thể được gặp qua nhiều nhánh nhưng code `<clinit>` của nó không phải được thực thi lặp lại mỗi lần duyệt.

### 6.3. Vì sao static fields của Root/Branch được in?

Trong interface Java, `int VALUE = trace("...")` tương đương field `public static final int VALUE` nhưng expression **không phải compile-time constant**. Trong quá trình initialization của interface do default methods, biểu thức `trace` được gọi và tạo side effect. Nếu thay bằng `int VALUE = 1`, việc lấy constant value không gọi `trace` và không có dòng log.

## 7. Ví dụ chạy được: thứ tự static field và giá trị mặc định

Khi một static initializer gọi method đọc một static field **chưa được gán bằng initializer văn bản**, method có thể quan sát giá trị mặc định từ preparation. Đây là nguyên nhân phổ biến của output khó đoán.

Tạo file **`StaticSequenceDemo.java`**:

```java
public class StaticSequenceDemo {
    static int first = readSecond();
    static int second = 7;

    static {
        System.out.println("static block second=" + second);
    }

    static int readSecond() {
        System.out.println("observed second=" + second);
        return second + 1;
    }

    public static void main(String[] args) {
        System.out.println("first=" + first);
        System.out.println("second=" + second);
    }
}
```

Chạy:

```bash
javac --release 17 StaticSequenceDemo.java
java StaticSequenceDemo
```

**Stdout:**

```text
observed second=0
static block second=7
first=1
second=7
```

Diễn tiến:

1. Khi `StaticSequenceDemo` initialize, `first` và `second` đã có giá trị mặc định **0** theo preparation.
2. Initializer của `first` gọi `readSecond()`. Lúc này `second = 7` **chưa chạy**, nên method in `observed second=0` và trả `1`. JVM gán `first=1`.
3. Tiếp theo biểu thức initializer `second=7` chạy.
4. Static block in `static block second=7`.
5. Khi class initialize xong, launcher mới gọi `main`, in `first=1` và `second=7`.

### 7.1. Sao không viết thẳng `static int first = second;`?

Với quy tắc **illegal forward reference** của Java source trong những ngữ cảnh nhất định, code:

```java
static int first = second;
static int second = 7;
```

có thể **bị `javac` từ chối** vì tham chiếu bằng tên đơn giản tới field static khai báo phía sau trong initializer. Ví dụ runnable đã gọi **method `readSecond()`**, và body method đọc field — trường hợp có quy tắc khác, nên nó có thể quan sát giá trị preparation **0**.

**Không biến ví dụ `first = second;` thành chương trình chạy với expected stdout**, vì nó nhằm minh họa compile error, không phải hành vi runtime.

### 7.2. Không có cơ chế “sắp xếp lại theo dependency”

JVM không đọc source và tự topologically sort static initializers. Nếu field A phụ thuộc B ở bên dưới, người viết cần tổ chức dependency/initialization rõ ràng. Java vẫn bảo toàn thứ tự thực thi phù hợp với source trong class body; JVM không đoán ý định nghiệp vụ và chạy `second=7` trước `first=readSecond()`.

## 8. Ví dụ chạy được: compile-time constant, inherited field và non-constant static

Tạo file **`StaticTriggerDemo.java`**:

```java
public class StaticTriggerDemo {

    static int report(String message, int result) {
        System.out.println(message);
        return result;
    }

    static class Settings {
        static final int LIMIT = 42;
        static final String LABEL = "stable";
        static final Integer BOXED = 42;

        static {
            System.out.println("Settings initialized");
        }
    }

    static class Parent {
        static int inherited = report("Parent initialized", 7);
    }

    static class Child extends Parent {
        static int own = report("Child initialized", 9);
    }

    public static void main(String[] args) {
        System.out.println("limit=" + Settings.LIMIT);
        System.out.println("label=" + Settings.LABEL);
        System.out.println("inherited=" + Child.inherited);
        System.out.println("boxed=" + Settings.BOXED);
        System.out.println("own=" + Child.own);
    }
}
```

Lệnh:

```bash
javac --release 17 StaticTriggerDemo.java
java StaticTriggerDemo
```

**Stdout chính xác:**

```text
limit=42
label=stable
Parent initialized
inherited=7
Settings initialized
boxed=42
Child initialized
own=9
```

Giải thích:

- `Settings.LIMIT` và `Settings.LABEL` là compile-time constants. Compiler Java có thể ghi hằng `42` và `"stable"` trực tiếp ở nơi sử dụng. Hai truy cập đầu không buộc initialize `Settings`, nên chưa thấy `Settings initialized`.
- `Child.inherited` truy cập field **khai báo trong `Parent`**. Kết quả yêu cầu initialize `Parent`, in `Parent initialized`; **Child chưa phải initialize** chỉ do truy cập field này.
- `Settings.BOXED` có kiểu `Integer`, không phải constant variable. Đọc nó bằng `getstatic` làm `Settings` initialize, chạy static block in `Settings initialized`, sau đó in giá trị `42`.
- `Child.own` thuộc field **do Child khai báo**. Khi cần init Child, Parent đã initialized nên không in lại Parent; chỉ in `Child initialized`.

### 8.1. Điều gì xảy ra nếu thay `Child.inherited` bằng `Child.own` ở dòng đầu?

Nếu dòng truy cập đầu vào class hierarchy là `Child.own`, JVM phải initialize Child và do đó đảm bảo Parent initialized trước. Khi ấy Parent và Child in liền nhau theo thứ tự. Nhưng ví dụ ban đầu cố ý tách hai trigger để chứng minh **kiểu khai báo field** mới quyết định việc initialization qua static field access.

### 8.2. “Compile-time constant” được quyết định lúc compile

Nếu thư viện thay `Settings.LIMIT` từ 42 thành 43 và chỉ build lại JAR chứa `Settings` nhưng **không biên dịch lại code client**, client cũ có thể vẫn dùng giá trị hằng **42 đã inline**. Đây là hệ quả về **binary compatibility**: thay constant variable không giống đổi non-constant static field.

Khi thiết kế library API, đừng dùng `public static final int` làm “giá trị cấu hình động” mà mong mọi client tự cập nhật sau khi thay JAR. Với giá trị runtime/configuration, nên đưa qua method hoặc cơ chế truy cập phù hợp.

## 9. Thứ tự khởi tạo static và khởi tạo instance

Một class có thể chứa:

- Static field initializer: `static int x = compute();`.
- Static initialization block: `static { ... }`.
- Instance field initializer: `int y = computeInstance();`.
- Instance initializer block: `{ ... }`.
- Constructor: `MyClass() { ... }`.

Hai nhóm này **khác nhau về thời điểm**:

| Thành phần | Khi nào chạy theo mô hình Java? |
| --- | --- |
| Static field initializer có runtime code | Khi initialize **class**, một lần cho trạng thái thành công của class đó theo loader |
| Static block | Khi initialize **class**, theo thứ tự cùng các field initializers |
| Instance field initializer | Khi tạo **mỗi object**, theo trình tự instance initialization |
| Instance initializer block | Khi tạo **mỗi object**, theo trình tự instance initialization |
| Constructor body | Trong quá trình tạo **mỗi object**, sau các bước thích hợp của superclass/instance initialization |

Ví dụ khái niệm:

```text
new Child() lần đầu
  → initialize Parent/related interfaces/Child (nếu chưa)
  → instance initialization + constructor chain
new Child() lần hai
  → không cần chạy lại <clinit> của những kiểu đã thành công
  → vẫn chạy instance initialization + constructor chain cho object mới
```

Không được kết luận “static luôn chạy ngay trước constructor” ở **mọi lần** tạo object: nó chỉ cần thực thi trước lần active use đầu nếu kiểu chưa initialized.

## 10. Reentrant initialization và concurrency

JVM phải phối hợp nhiều thread cùng muốn initialize một class/interface. Quy tắc `5.5` dùng **initialization lock** để bảo đảm trạng thái của một class được xử lý nhất quán.

Các trạng thái ý tưởng:

```text
verified/prepared, not initialized
       │
       ├─ thread A bắt đầu initialize
       │      ├─ ghi nhận "initialization in progress by A"
       │      ├─ khởi tạo superclass/interfaces phù hợp
       │      └─ chạy <clinit>, hoàn tất thành công hoặc thất bại
       │
       └─ thread B cùng cần class
              → chờ initialization của A theo cơ chế JVM
              → tiếp tục khi class initialized hoặc nhận lỗi
```

- Nếu **cùng thread A** đang initialize class và trong quá trình ấy lại yêu cầu initialize **chính class đó**, JVM nhận diện recursive initialization và **không chạy lại `<clinit>`** từ đầu. Đây là lý do method `readSecond()` trong `StaticSequenceDemo` có thể được gọi khi class đang initializing.
- Nếu **thread B** cần class đó, B có thể phải đợi A hoàn thành.
- Nếu initialization của class **thất bại**, class có thể chuyển sang **erroneous state**; những lần thử tiếp theo không đơn giản chạy lại static block như chưa có chuyện gì.
- Synchronization của class initialization **không** thay thế việc đồng bộ thread-safe cho các static mutable fields **sau khi** initialization hoàn tất.

### 10.1. Đừng tạo vòng phụ thuộc init giữa hai classes/threads

Nếu static initializer của class A yêu cầu B còn static initializer của B lại yêu cầu A, đặc biệt khi **hai threads** đang initialize các class khác nhau, có thể gặp chờ vòng hoặc deadlock initialization. Các JVM phải giữ quy tắc đồng bộ; người thiết kế cần tránh side effects phức tạp, I/O blocking hoặc chạy worker tasks trong static initializer khi có dependency chưa rõ.

**Thực hành:** để static initialization càng đơn giản/deterministic càng tốt; đưa việc kết nối mạng, khởi động thread pool, đăng ký plugin và dependency injection phức tạp vào các bước startup được kiểm soát rõ ràng nếu có thể.

## 11. Khi static initialization ném exception

Class initialization có thể thất bại:

```java
class Broken {
    static final Object CONFIG = loadConfiguration();

    static Object loadConfiguration() {
        throw new IllegalStateException("bad config");
    }
}
```

Đoạn class này **minh họa**. Khi lần đầu active use của `Broken`:

1. JVM bắt đầu initialization.
2. `<clinit>` (nếu có) thực thi initializer biểu thức và gặp exception.
3. Nếu throwable làm initialization thất bại **không thuộc `Error`**, JVM áp dụng quy tắc bao bọc thích hợp trong **`ExceptionInInitializerError`** (trừ chi tiết khi không thể tạo wrapper).
4. Class có thể bị ghi nhận ở **erroneous state**.
5. Những lần active use về sau có thể thất bại bằng **`NoClassDefFoundError`** nêu rằng class không thể khởi tạo, thay vì chạy initializer lần nữa.

Nếu exception của initializer vốn là một `Error`, nó không nhất thiết bị bọc thành `ExceptionInInitializerError` theo cùng quy tắc. Vì thế đừng viết monitoring chỉ đếm `ExceptionInInitializerError` mà bỏ qua lỗi initialization loại khác.

### 11.1. Cách xử lý trong ứng dụng

- Đọc **cause/stack trace** đầu tiên để biết initializer nào đã lỗi.
- Xem cấu hình, dependency, file path, environment và code có side effects trong static blocks.
- Kiểm tra xem lớp có bị khởi tạo sớm bởi library, reflection hoặc framework không.
- Đừng “retry” bằng cách lặp `new Broken()` trong **cùng class loader** rồi kỳ vọng JVM tự chạy lại initialization.
- Nếu yêu cầu recovery động, thiết kế một service/configuration lifecycle có thể retry **ngoài** `<clinit>`, thay vì lệ thuộc class initialization.

## 12. `javap`: nhìn thấy `<clinit>` và constant folding

Dùng JDK 17:

```bash
# Compile cả ba ví dụ độc lập:
javac --release 17 InitHierarchyDemo.java
javac --release 17 StaticSequenceDemo.java
javac --release 17 StaticTriggerDemo.java

# Xem các lệnh, method/field descriptors:
javap -c -p StaticSequenceDemo
javap -c -p StaticTriggerDemo

# Xem nested classes, giữ dấu $ trong dấu nháy:
javap -c -p 'InitHierarchyDemo$Parent'
javap -c -p 'InitHierarchyDemo$Leaf'
javap -c -p 'InitHierarchyDemo$Root'
javap -c -p 'InitHierarchyDemo$Branch'
javap -c -p 'InitHierarchyDemo$Marker'

# Hiển thị ConstantValue và thông tin sâu:
javap -v -p 'StaticTriggerDemo$Settings'
```

Với `StaticSequenceDemo`, thường thấy:

```text
static {};
   invokestatic readSecond:()I
   putstatic first:I
   bipush 7
   putstatic second:I
   getstatic java/lang/System.out
   ...
   return
```

Đây là **trích đoạn logic của `<clinit>`**, không phải output chuẩn từng byte/offset; mã in chuỗi của `System.out.println` còn có instructions/constant-pool operands khác. Điều cần xác minh là lệnh gọi `readSecond()` **đứng trước** lệnh ghi `second=7`.

Với `Settings`, chú ý:

- `LIMIT` và `LABEL` có thể có **`ConstantValue` attributes**.
- `BOXED` cần gán giá trị bằng code runtime trong `<clinit>`.
- Static block in `Settings initialized` cũng nằm trong `<clinit>` sau bước gán `BOXED` ở ví dụ.
- Ở **client `StaticTriggerDemo`**, các lần dùng `LIMIT`/`LABEL` có thể chỉ đẩy literal hoặc constant lên stack, không có `getstatic Settings.LIMIT` bắt buộc.

### 12.1. `-Xlog:class+init` (HotSpot) dùng ra sao?

Trên **HotSpot JVM** có Unified Logging, có thể xem log class initialization theo cấu hình tương thích, ví dụ:

```bash
java -Xlog:class+init=info InitHierarchyDemo
```

Đây là **tùy chọn diagnostic đặc thù runtime**, không phải cú pháp Java/JVMS bắt buộc mọi implementation phải hỗ trợ. Log có thể nhiều dòng, gồm các JDK classes và thứ tự thông tin khác nhau giữa bản HotSpot; do đó **không đưa một stdout cố định cho diagnostic này**.

Để xác nhận hành vi trong học tập, hãy ưu tiên **stdout chủ động từ phương thức `trace()`** của ví dụ `InitHierarchyDemo`.

## 13. Những sai lầm phổ biến

1. **“`extends` chỉ ảnh hưởng method lookup, không ảnh hưởng thứ tự static init.”** Sai. Superclass phải initialize trước subclass.
2. **“Class implements interface nào cũng initialize interface đó trước.”** Sai. Với class initialization, quy tắc liên quan đặc biệt tới superinterfaces **khai báo default methods**; interface khác không tự init chỉ vì implements.
3. **“Khởi tạo một interface luôn khởi tạo interface cha.”** Sai. Initialization của interface không tự bắt buộc initialization superinterfaces.
4. **“Default method phải được gọi thì interface mới init.”** Sai khi class initialization yêu cầu initialize superinterface có khai báo default method, dù method ấy chưa được gọi.
5. **“Static methods của interface tương đương default methods.”** Sai. Static method không phải instance default method.
6. **“Static fields chạy theo tên chữ cái hoặc dependency.”** Sai. Runtime initializer expressions và static blocks của cùng kiểu chạy theo thứ tự code được biên dịch theo Java source.
7. **“`static final` nào cũng là constant và không trigger init.”** Sai. Chỉ **constant variable** theo JLS mới có tính chất compile-time constant; `Integer` hoặc biểu thức runtime khác.
8. **“Đọc `Child.parentField` chắc chắn init Child.”** Sai. Đọc field `static` kích hoạt loại **khai báo field**, thường là Parent.
9. **“`C.class` và `new C()` đều chạy static block.”** Sai. Class literal không tự kích hoạt initialization; tạo instance cần class initialization.
10. **“Preparation đặt `static int n = 7` thành 7 ngay.”** Sai với biểu thức initializer thông thường. Preparation đặt giá trị mặc định, runtime assignment xảy ra khi khởi tạo.
11. **“Không thể thấy giá trị 0 từ static field có initializer 7.”** Có thể thấy 0 nếu một initializer trước gọi method đọc field chưa được gán, như `StaticSequenceDemo`.
12. **“Constructor `<init>` và class initialization `<clinit>` là một.”** Sai; constructor là quy trình tạo từng object.
13. **“Mỗi lần tạo object thì class static block chạy lại.”** Sai nếu class đã initialized thành công.
14. **“`<clinit>` luôn tồn tại ở mọi class.”** Sai. Class chỉ chứa compile-time constants có thể không có code initialization method.
15. **“Một static initializer lỗi lần đầu sẽ retry vô hạn khi dùng lại class.”** Sai. Initialization failure dẫn tới trạng thái lỗi theo class loader.
16. **“Cùng tên class trên toàn JVM nghĩa là chỉ một init duy nhất.”** Không hoàn toàn: identity của class còn gắn với **defining class loader**; các loaders có thể định nghĩa các classes khác nhau cùng binary name.
17. **“Chỉ thay JAR chứa static final constant là client đã dùng giá trị mới.”** Không chắc, vì client có thể đã inline compile-time constant.

## 14. Tự kiểm tra (có đáp án)

**Câu 1.** `class C extends B` với `B` chưa initialized. Trước khi chạy static block của `C`, điều gì phải xảy ra?

**Đáp án:** `B` và superclass chain chưa initialized của `B` cần initialize theo đúng quy tắc; còn có thể có default-method superinterfaces liên quan trước khi chạy static body của `C`.

**Câu 2.** Interface `I` có field `int A = compute()` nhưng không khai báo default methods. `new C()` với `class C implements I` có bắt buộc chạy `I.A` initializer không?

**Đáp án:** **Không** chỉ vì `C implements I`. Khi active use đọc `I.A`, `I` có thể được initialize; `compute()` lúc ấy mới chạy.

**Câu 3.** `interface B extends A`. Chỉ truy cập non-constant static field khai báo bởi `B` thì `A` có nhất thiết initialize không?

**Đáp án:** **Không**. Initialize một interface không tự khởi tạo các superinterfaces. Nếu expression initializer của `B` tự gọi vào `A` thì đó là một trigger riêng.

**Câu 4.** `static final int N = 5` và `static final Integer M = 5` có giống nhau về trigger initialization không?

**Đáp án:** Không. `N` là constant variable (compile-time constant) nếu đúng các điều kiện của JLS; `M` là reference kiểu `Integer` không phải constant variable và đọc qua `getstatic` có thể trigger initialization.

**Câu 5.** Vì sao `StaticSequenceDemo` in `observed second=0` trước khi có `second=7`?

**Đáp án:** `second` đã có default value 0 sau preparation nhưng initializer biểu thức `second=7` nằm **sau** initializer của `first`. Method `readSecond()` đọc nó trước khi dòng gán 7 chạy.

**Câu 6.** Tại sao `InitHierarchyDemo` in `Root.field` và `Branch.field` trước `Leaf.field` nhưng không in `Marker.field` ngay?

**Đáp án:** `Root` và `Branch` khai báo default methods nên được initialize theo traversal phù hợp khi class `Leaf` initialize. `Marker` không khai báo default methods; nó chỉ init sau khi source truy cập non-constant `Marker.VALUE`.

**Câu 7.** `static int p` khai báo trong Parent nhưng được truy cập bằng `Child.p`. Kiểu nào phải initialize do truy cập đó?

**Đáp án:** Parent là kiểu **khai báo field**. Tham chiếu tên Child ở source không tự buộc initialize Child.

**Câu 8.** Nếu class initialization ném `IllegalStateException` không được xử lý, lỗi ngoài cùng lần đầu thường là gì? Lần sau thì sao?

**Đáp án:** Lần đầu thường là **`ExceptionInInitializerError`** bọc exception không phải Error; class chuyển sang erroneous state. Lần sau dùng lại cùng class/loader thường gặp **`NoClassDefFoundError`**, không chạy lại initialization body.

**Câu 9.** Thao tác `new SomeClass[2]` có tự yêu cầu initialize `SomeClass` không?

**Đáp án:** Không. Đây là tạo array chứa references; component class không tự phải initialize chỉ do allocation array.

**Câu 10.** Nếu class đã được initialized ở thread A, thread B gọi static method sau đó thì có chạy lại `<clinit>` không?

**Đáp án:** Không. B sử dụng trạng thái đã initialized của class theo đồng bộ JVM, không chạy lại static body.

## 15. Tổng kết

```text
Trigger yêu cầu initialize class C?
      │
      ▼
C đã initialized? ───── Có ───► tiếp tục dùng C
      │ Không
      ▼
xử lý initialization lock/trạng thái C
      │
      ▼
ConstantValue fields của C theo quy tắc JVMS
      │
      ▼
Nếu C là CLASS:
  superclass SC chưa initialized → initialize SC
  superinterfaces có khai báo default methods (thứ tự recursive) → initialize
      │
      ▼
chạy <clinit> của C nếu có
  - static field initializers (runtime)
  - static initializer blocks
  - thứ tự textual của C
      │
      ▼
đánh dấu initialized thành công
      │
      ▼
tiếp tục thao tác new/getstatic/putstatic/invokestatic...
```

**Điểm cốt lõi:** Thứ tự khởi tạo không phải “mọi thứ có chữ `static` chạy từ trên xuống trên toàn bộ cây thừa kế”. JVM thực hiện initialization **theo từng class/interface**, với **superclass trước subclass**, các **default-method superinterfaces** liên quan theo thứ tự đệ quy, rồi mới tới initializer code của class đó. Trong một kiểu, static runtime initializers chạy **theo thứ tự source**, còn constant variables có quy tắc riêng. Phân biệt **declaring type**, **compile-time constant** và **class vs interface initialization** là chìa khóa để dự đoán output và tránh initialization bugs.

### Tài liệu tham khảo chuẩn

- [JLS Java SE 17 — 12.4.1 When Initialization Occurs](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.1).
- [JLS Java SE 17 — 12.4.2 Detailed Initialization Procedure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.2).
- [JVMS Java SE 17 — 5.5 Initialization](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5).
- [JVMS Java SE 17 — 5.4.2 Preparation](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.2).
- [JVMS Java SE 17 — 2.9.2 Class Initialization Methods](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.9.2).
- [JVMS Java SE 17 — 4.7.2 ConstantValue Attribute](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.2).
- [JLS Java SE 17 — 8.3.2.1 Static Field Initializers](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.3.2.1).
- [JLS Java SE 17 — 9.3.1 Interface Field Initializers](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.3.1).
- [JDK 17 `javap` command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="linking-and-initialization-010">Khóa khởi tạo lớp, khởi tạo đệ quy và nguy cơ chờ lẫn nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Khóa khởi tạo lớp, khởi tạo đệ quy và nguy cơ chờ lẫn nhau trong JVM

## 1. Khái niệm: vì sao khởi tạo lớp cần đồng bộ?

Khi chương trình Java lần đầu **chủ động sử dụng** một lớp hoặc giao diện theo những quy tắc của ngôn ngữ/JVM, runtime có thể cần thực hiện **class initialization**: chạy các biểu thức khởi tạo trường `static` và các khối `static { ... }`. Đây là giai đoạn khác với tải lớp (*loading*), xác minh và chuẩn bị (*verification/preparation*) hay giải quyết tham chiếu (*resolution*).

Vì Java cho phép nhiều thread thực thi đồng thời, hai thread có thể cùng muốn sử dụng một lớp **chưa khởi tạo**. JVM phải bảo đảm rằng quá trình khởi tạo được quản lý đúng: một thread đang thực hiện initialization, thread khác không được tự ý chạy lại phần `static` của cùng lớp trong khi nó chưa hoàn tất.

Theo **JLS `12.4.2 — Detailed Initialization Procedure**, mỗi class/interface `C` có một **initialization lock `LC`**. Việc JVM ánh xạ `C` tới lock thực tế như thế nào là chi tiết triển khai; không nên tự động đồng nhất `LC` với monitor của `C.class`.

Ba tình huống quyết định hành vi:

1. **Chưa khởi tạo:** một thread được ghi nhận là thread thực hiện initialization và chạy chuỗi khởi tạo.
2. **Đang được cùng thread khởi tạo:** nếu code trong `<clinit>` lại yêu cầu khởi tạo chính lớp đó, đây là **khởi tạo đệ quy trên cùng thread**; yêu cầu lặp **hoàn tất bình thường**, không chờ chính mình.
3. **Đang được thread khác khởi tạo:** thread yêu cầu phải **chờ** quá trình hiện tại kết thúc, sau đó kiểm tra lại trạng thái lớp. Nếu nhiều lớp khởi tạo phụ thuộc lẫn nhau giữa nhiều thread, một chu trình chờ có thể tạo **deadlock**.

```text
      Class C: chưa khởi tạo
                |
                | Thread T1 yêu cầu active use
                v
    C đang được khởi tạo bởi T1
         /              \
        /                \
 T1 yêu cầu C lần nữa    T2 yêu cầu C
        |                     |
  Trả về bình thường       Chờ T1
 (không chạy <clinit>       hoàn tất
  lần thứ hai)
        |
        v
 T1 tiếp tục chạy initializer bên ngoài
        |
        +-- thành công ---> C initialized, đánh thức thread chờ
        |
        +-- thất bại ----> C erroneous, đánh thức thread chờ
```

Điểm mấu chốt: **cơ chế khởi tạo tự bảo vệ khỏi yêu cầu đệ quy cùng thread**, nhưng **không có bảo đảm tự động ngăn mọi chu trình phụ thuộc giữa các thread khác nhau**.

## 2. Phân biệt Loading, Linking, Initialization và instance construction

Một class có thể đã được biết đến bởi runtime mà khối `static` của nó chưa từng chạy.

| Giai đoạn | Ý nghĩa khái quát | Có chạy `static` của class chưa? |
| --- | --- | --- |
| **Loading** | Tìm/định nghĩa biểu diễn class/interface runtime qua class loader | Không nhất thiết |
| **Linking: verification** | Kiểm tra cấu trúc và an toàn kiểu của class file | Không phải thực thi `static` |
| **Linking: preparation** | Chuẩn bị biến `static` và đặt giá trị mặc định theo quy tắc JVM | Không chạy biểu thức khởi tạo tùy ý |
| **Linking: resolution** | Xử lý symbolic references theo quy tắc liên kết | Không đồng nghĩa class initializer hoàn tất |
| **Initialization** | Thực thi các biểu thức khởi tạo `static` và khối `static { }` theo quy tắc JLS | Có |
| **Instance construction** | Cấp phát object và chạy `<init>` cho instance | Khác với `<clinit>` |

Class initialization thường liên quan phương thức đặc biệt **`<clinit>`** trong class file: compiler có thể gộp mã khởi tạo các static fields và static blocks thành một phương thức được JVM thực hiện theo quy tắc initialization. Không được hiểu `<clinit>` là constructor của từng instance.

```java
class Config {
    static int PORT = loadPort();

    static int loadPort() {
        return 8080;
    }

    Config() {
        // Constructor instance
    }
}
```

Đây là **mã minh họa class**, không phải một chương trình có `main`. Giá trị mặc định của `PORT` trong preparation là `0`; khi initialization thực thi biểu thức `loadPort()`, giá trị được gán thành `8080`. Việc tạo nhiều `new Config()` sau đó **không chạy lại class initializer**.

### Khi nào class initialization thường xảy ra?

Các thao tác chủ động sử dụng (*active use*) như:

- Tạo một instance của lớp bằng `new`.
- Gọi phương thức `static` khai báo bởi lớp.
- Đọc/ghi một trường `static` được khai báo bởi lớp, **trừ một số ngoại lệ về constant variables** theo JLS.
- Một số thao tác reflection có yêu cầu initialization.

Ngược lại, việc tham chiếu `SomeClass.class`, dùng `Class.forName(name, false, loader)`, hoặc tạo `new SomeClass[10]` **không nhất thiết khởi tạo `SomeClass`**. Trường `static final` là **compile-time constant variable** có thể được inline tại nơi dùng và không kích hoạt initialization của lớp khai báo khi đọc theo các quy tắc tương ứng.

**Cẩn thận:** khi một class được khởi tạo, superclass và những superinterfaces có khai báo default methods được khởi tạo theo trình tự mà JLS quy định nếu chưa được khởi tạo. Không đúng nếu nói “mọi interface mà class implements đều bắt buộc chạy initializer” hoặc “interface initialization luôn kéo theo mọi superinterface”.

## 3. Trạng thái khởi tạo và initialization lock `LC`

Mô hình trong JLS `12.4.2 giả định mỗi class/interface có trạng thái thể hiện một trong bốn tình huống:

1. **Verified and prepared, not initialized.**
2. **Initialization in progress** bởi một thread cụ thể.
3. **Fully initialized.**
4. **Erroneous state** vì quá trình khởi tạo không thể hoàn tất hợp lệ.

Những trạng thái này giúp phân biệt:

- Một class đã nạp nhưng vẫn chưa init.
- Một class đang init và có thread sở hữu hoạt động.
- Một class init thành công, có thể dùng theo quy tắc đồng bộ.
- Một class init lỗi và **không thể thử init lại một cách bình thường** trong cùng class identity.

### 3.1. Tóm tắt thuật toán theo JLS (giản lược có chủ đích)

Với class/interface `C` và thread yêu cầu `T`:

1. **Đồng bộ trên initialization lock `LC`**.
2. Nếu `C` đang init bởi **thread khác**, bỏ khóa và **chờ** thông báo quá trình đó kết thúc; sau đó kiểm tra lại trạng thái.
3. Nếu `C` đang init bởi **chính `T`**, đây là yêu cầu đệ quy: nhả khóa, **return bình thường** khỏi yêu cầu initialization lặp.
4. Nếu `C` **đã initialized**, nhả khóa và return bình thường.
5. Nếu `C` ở **erroneous state**, nhả khóa và ném `NoClassDefFoundError`.
6. Nếu chưa init, ghi nhận **`T` đang init `C`** và **nhả `LC`** trước khi chạy phần initialization còn lại.
7. Thực hiện các bước tiền khởi tạo liên quan constant variables, superclass và superinterfaces có default methods theo quy tắc, assertion status, sau đó chạy class/interface variable initializers và static initializer blocks theo thứ tự văn bản phù hợp.
8. Khi thành công, đồng bộ lại `LC`, chuyển trạng thái thành **initialized**, thông báo thread chờ và return bình thường.
9. Khi thất bại, xử lý throwable theo quy tắc (có thể bọc bởi `ExceptionInInitializerError`), gắn class thành **erroneous**, thông báo thread chờ và kết thúc bất thường.

Đây là **tóm lược phục vụ học tập**, không thay thế toàn văn thuật toán gồm các ngoại lệ và ràng buộc cụ thể của JLS.

### 3.2. Lock không nhất thiết bị giữ suốt lúc chạy khối `static`

Một chi tiết rất quan trọng: trong thuật toán JLS, thread **đánh dấu** mình đang khởi tạo lớp khi giữ `LC`, rồi **nhả lock** trước khi chạy superclass/default-interface initialization và body của static initializers.

Vậy vì sao thread khác vẫn phải chờ? Bởi JVM kiểm tra **trạng thái “initialization in progress by another thread”**; khi gặp trạng thái đó, thread đợi đến khi initialization hoàn tất.

Do đó cần phân biệt:

- **Initialization lock `LC`:** được dùng để điều phối chuyển trạng thái và thông báo.
- **Trạng thái “being initialized by T”:** cho JVM biết thread nào hiện chịu trách nhiệm.
- **Thời gian thực thi code initializer:** có thể chạy sau khi `LC` đã được nhả.

Không nên giảng rằng “Thread A giữ monitor `SomeClass.class` từ lúc vào đến lúc rời khối `static`” là nghĩa bắt buộc của đặc tả. JVM có quyền tối ưu/biến đổi cơ chế lock nếu giữ được tính đồng bộ và quan sát được mà đặc tả yêu cầu.

### 3.3. Tại sao không dùng `synchronized(SomeClass.class)` thay cho quy tắc init?

`SomeClass.class` là một `Class` object có thể được ứng dụng dùng làm monitor, nhưng JLS **không yêu cầu** initialization lock `LC` phải chính là monitor object đó. Đồng bộ trên `SomeClass.class` **không** là API quản lý trạng thái class initialization.

Ngoài ra, khi truy cập `SomeClass.class`, class literal không tự động buộc static initializer chạy. Vì vậy viết:

```java
synchronized (SomeClass.class) {
    // Đây là monitor do code ứng dụng dùng.
}
```

là **minh họa cú pháp synchronized**; nó không thay thế chính sách của JVM đối với `<clinit>`, cũng không là cách giải quyết deadlock phụ thuộc lớp một cách tổng quát.

## 4. Một thread khởi tạo, thread khác chờ — không chạy `<clinit>` hai lần

Giả sử hai thread gần như đồng thời gọi `Cache.get()` và `Cache` chưa init:

```text
Thread T1                            Thread T2
---------                            ---------
get Cache.value                     get Cache.value
    |                                    |
C chưa initialized                     C đang init bởi T1
    |                                    |
mark C initializing by T1              chờ trạng thái C thay đổi
    |
chạy Cache.<clinit>
    |
đặt static fields
    |
mark C initialized, notify
    |                                    |
đọc Cache.value                         tiếp tục, đọc Cache.value
```

Sau khi initialization hoàn tất thành công, cả hai thread có thể tiếp tục sử dụng lớp. JVM phải tuân thủ các quan hệ đồng bộ cần thiết của initialization; không được để thread chờ tiếp tục như thể class đã init thành công khi initializer vẫn đang thực thi dở.

### 4.1. Ví dụ Java 17+: chỉ một lần chạy initializer dù có hai thread

Tạo tệp **`ClassInitWaitDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class ClassInitWaitDemo {
    private static final CountDownLatch entered = new CountDownLatch(1);
    private static final CountDownLatch release = new CountDownLatch(1);
    private static final AtomicInteger runs = new AtomicInteger();

    static class Slow {
        static int value = initialize();

        static int initialize() {
            runs.incrementAndGet();
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
            return 42;
        }
    }

    public static void main(String[] args) throws Exception {
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();

        Thread t1 = new Thread(() -> first.set(Slow.value), "reader-1");
        t1.start();
        entered.await(); // Đợi t1 thực sự đi vào initializer.

        Thread t2 = new Thread(() -> second.set(Slow.value), "reader-2");
        t2.start();

        release.countDown(); // Cho phép initializer kết thúc.
        t1.join();
        t2.join();

        System.out.println("initializer runs: " + runs.get());
        System.out.println("first: " + first.get());
        System.out.println("second: " + second.get());
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 ClassInitWaitDemo.java
java ClassInitWaitDemo
```

**Stdout chính xác khi chương trình hoàn tất bình thường:**

```text
initializer runs: 1
first: 42
second: 42
```

Giải thích:

- Thread `t1` trở thành thread chịu trách nhiệm initialization của `Slow`.
- Nó tăng `runs` đúng một lần và chờ latch `release` trong initializer.
- Thread `t2` cùng truy cập `Slow.value`. Nếu tới trong lúc `Slow` chưa initialized, nó phải đợi JVM hoàn tất initialization; nếu tới sau khi `t1` hoàn tất, nó đọc giá trị sẵn có.
- Dù thứ tự scheduling giữa `t2` và việc release không cố định, **kết quả vẫn là một lần chạy initializer** và cả hai đọc 42 khi bình thường.

**Giới hạn minh họa:** latch đồng bộ trong chương trình là công cụ để dễ quan sát, **không phải initialization lock `LC` của JVM**. Không được suy luận JVM triển khai class initialization bằng `CountDownLatch`.

## 5. Khởi tạo đệ quy trên cùng thread: có thể nhìn thấy giá trị mặc định

Một static initializer có thể gián tiếp tham chiếu trở lại chính lớp đang được khởi tạo:

```text
T1 bắt đầu khởi tạo A
    |
    +→ A initializer cần B.value
                      |
                      +→ T1 bắt đầu khởi tạo B
                                         |
                                         +→ B initializer đọc A.value
                                                                    |
                                                                    +→ JVM thấy A đang init bởi T1
                                                                        → yêu cầu init A đệ quy return
                                                                        → B đọc A.value hiện thời
```

JLS quy định việc cùng thread yêu cầu khởi tạo lại class **đang được nó khởi tạo** thì yêu cầu đó hoàn tất bình thường. Nó **không** chạy lại `A.<clinit>` từ đầu và **không** tự chờ một việc mà chính thread ấy đang làm.

Hệ quả quan trọng: static field có thể được đọc khi nó vẫn giữ **giá trị mặc định từ preparation**, trước khi biểu thức khởi tạo trong source hoàn tất.

### 5.1. Ví dụ Java 17+ độc lập: vòng phụ thuộc A → B → A

Tạo **`RecursiveInitDemo.java`**:

```java
public class RecursiveInitDemo {
    static class A {
        static int value = calculate();

        static int calculate() {
            return B.value + 1;
        }
    }

    static class B {
        static int value = A.value + 10;
    }

    public static void main(String[] args) {
        System.out.println("A.value = " + A.value);
        System.out.println("B.value = " + B.value);
    }
}
```

Chạy:

```bash
javac --release 17 RecursiveInitDemo.java
java RecursiveInitDemo
```

**Stdout chính xác:**

```text
A.value = 11
B.value = 10
```

### 5.2. Diễn giải từng bước

Ban đầu, trong preparation, các `static int` mang giá trị mặc định **0**:

```text
A.value = 0   (chưa chạy biểu thức initializer)
B.value = 0   (chưa chạy biểu thức initializer)
```

Khi `main` đọc `A.value`:

1. Thread chính bắt đầu `A.<clinit>`.
2. `A.value = calculate()` cần `B.value`.
3. JVM bắt đầu `B.<clinit>` **trên cùng thread**.
4. `B.value = A.value + 10` đọc `A.value`, trong lúc `A` vẫn đang init bởi **thread chính**.
5. Yêu cầu init `A` lặp được JVM coi là recursive request cùng thread và **return bình thường**; trường `A.value` lúc này vẫn là **0**.
6. `B.value` nhận `0 + 10 = 10`; `B` hoàn tất initialization.
7. `A.calculate()` lấy `B.value = 10`, trả 11; `A.value` nhận 11; `A` hoàn tất.
8. `main` in `A.value = 11` và `B.value = 10`.

Điều này **không** phải race condition vì ví dụ chỉ dùng một thread cho chuỗi khởi tạo. Cũng không phải JVM “bỏ sót phép gán”: JVM cố ý xử lý yêu cầu init đệ quy cùng thread bằng cách không chờ, và giá trị mặc định có thể được đọc trước thời điểm biểu thức gán chạy xong.

### 5.3. Có luôn tránh được deadlock nếu tham chiếu vòng?

Không. Chỉ trường hợp **cùng thread** yêu cầu khởi tạo lại **cùng class đang được nó khởi tạo** mới có nhánh xử lý đệ quy như trên.

Nếu vòng phụ thuộc phân bố qua **hai thread**, thread này có thể cần một class đang được thread kia init, và ngược lại. Khi ấy mỗi thread phải chờ **thread khác**; JVM không thể chỉ dùng quy tắc “same-thread recursion” để giải quyết.

### 5.4. Đừng biến vòng phụ thuộc thành hợp đồng API

Nếu sửa code hoặc thay thứ tự gọi lần đầu, thời điểm initialization của A/B có thể đổi. Ví dụ việc đọc `B.value` trước `A.value` có thể tạo chuỗi kết quả khác với việc đọc A trước. Vì vậy code sản xuất không nên dựa vào “giá trị mặc định nhìn thấy giữa vòng init” như một hành vi nghiệp vụ mong muốn.

Để giảm rủi ro, tránh các initializer tĩnh truy cập qua lại giữa nhiều class khi có thể; xem xét khởi tạo sau bằng explicit initialization, dependency injection hoặc tạo đối tượng config độc lập với thứ tự tải lớp.

## 6. Deadlock khởi tạo lớp: hai thread chờ hai class khác nhau

Bây giờ xét hai class `Alpha` và `Beta` có static initializers tham chiếu qua lại:

```text
Thread T1                              Thread T2
---------                              ---------
bắt đầu Alpha.<clinit>                 bắt đầu Beta.<clinit>
Alpha: initializing by T1             Beta: initializing by T2
      |                                      |
      +→ cần Beta.value                      +→ cần Alpha.value
                |                                      |
                v                                      v
    Beta đang init bởi T2                  Alpha đang init bởi T1
                |                                      |
           T1 đợi T2                                 T2 đợi T1
                |                                      |
                +--------------- chu trình -------------+
```

Không thread nào có thể hoàn tất initializer của chính mình bởi cả hai đều đang chờ class của đối phương.

**Đây là class initialization deadlock**, không nhất thiết là deadlock thuần túy do hai ứng dụng cùng giữ các monitor `synchronized`. Việc initializer chạy sau khi JVM nhả `LC` không loại bỏ vấn đề, bởi trạng thái **“class đang được thread kia khởi tạo”** vẫn buộc thread còn lại phải chờ.

### 6.1. Vì sao JVM không tự cho thread T1 đọc dữ liệu Beta dở dang?

Cơ chế same-thread recursion chỉ cho phép **thread đang chịu trách nhiệm** tiếp tục khi nó yêu cầu init lại lớp của chính mình. Một thread khác không có cùng quyền đó: nó phải đợi quá trình init của class kết thúc theo quy tắc JLS.

Nếu JVM tùy ý cho T1 đọc `Beta.value` trước khi `Beta` init xong, nó phá hỏng hợp đồng đồng bộ hóa khởi tạo với các thread khác. Vì vậy giải pháp đúng là **tránh tạo chu trình phụ thuộc chéo giữa các initializer**, không ép JVM bỏ chờ.

### 6.2. Ví dụ Java 17+ tái hiện chu trình chờ với daemon threads

Thí nghiệm sau **cố tình tạo deadlock**. Không dùng mẫu này trong ứng dụng thật. Hai thread được đặt là **daemon** để khi thread `main` in kết quả và kết thúc, chương trình demo không bị giữ sống bởi chính hai thread bị kẹt. Mỗi class dùng `CountDownLatch` để bảo đảm **cả hai initializer đã bắt đầu** trước khi chúng truy cập static field của nhau.

Tạo tệp **`InitDeadlockDemo.java`**:

```java
import java.util.concurrent.CountDownLatch;

public class InitDeadlockDemo {
    private static final CountDownLatch bothStarted = new CountDownLatch(2);

    static void rendezvous() {
        bothStarted.countDown();
        try {
            bothStarted.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    static class Alpha {
        static int value = initialize();

        static int initialize() {
            rendezvous();
            return Beta.value + 1;
        }
    }

    static class Beta {
        static int value = initialize();

        static int initialize() {
            rendezvous();
            return Alpha.value + 1;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread t1 = new Thread(() -> {
            int unused = Alpha.value;
        }, "alpha-initializer");

        Thread t2 = new Thread(() -> {
            int unused = Beta.value;
        }, "beta-initializer");

        t1.setDaemon(true);
        t2.setDaemon(true);

        t1.start();
        t2.start();

        bothStarted.await();
        t1.join(300);
        t2.join(300);

        System.out.println("both initializers started: true");
        System.out.println("both threads alive: "
                + (t1.isAlive() && t2.isAlive()));
    }
}
```

Chạy bằng JDK 17+:

```bash
javac --release 17 InitDeadlockDemo.java
java InitDeadlockDemo
```

**Stdout mong đợi trong môi trường thực thi thông thường:**

```text
both initializers started: true
both threads alive: true
```

Thời điểm hai thread được lập lịch không cố định, nên **không dùng số mili giây ở `join(300)` làm phép chứng minh deadlock tổng quát**. Tuy nhiên latch bảo đảm cả `Alpha` và `Beta` đều đã đi vào initializer trước khi chúng tham chiếu tới giá trị của lớp còn lại. Với tình huống này, mỗi bên chờ class do bên kia đang init.

Không nên chuyển `t1`/`t2` thành non-daemon nếu chỉ đang thử nghiệm: khi đó tiến trình có thể không kết thúc. Chỉ dùng demo trong môi trường học tập cô lập; sau khi thread chính kết thúc, runtime có thể kết thúc các daemon threads cùng tiến trình.

### 6.3. Vì sao `CountDownLatch` không phải nguồn gốc deadlock?

Hai lời gọi `rendezvous()` làm nhiệm vụ **đồng bộ điểm bắt đầu**: chúng chỉ chờ cho đến khi cả hai class initializers đã đến barrier, rồi cả hai được thả ra. Sau đó:

- `Alpha.initialize()` cần đọc `Beta.value`, trong khi `Beta` đang được T2 init.
- `Beta.initialize()` cần đọc `Alpha.value`, trong khi `Alpha` đang được T1 init.

Vòng chờ thực sự nằm ở **class initialization dependencies**. Bỏ latch ra có thể khiến một thread init cả hai lớp theo thứ tự và tránh deadlock tình cờ, làm test không tái hiện ổn định.

### 6.4. Có phải `ClassCircularityError` sẽ tự động xuất hiện?

**Không.** `ClassCircularityError` liên quan phát hiện vòng không hợp lệ trong **cấu trúc kế thừa/triển khai class hoặc interface** khi loading theo các quy tắc riêng. Vòng trong **static initializer execution** ở hai thread không mặc nhiên được chuyển thành `ClassCircularityError`.

Một JVM có thể hỗ trợ nhiều công cụ chẩn đoán thread và lock, nhưng **không nên giả định class initialization deadlock luôn được runtime tự phá vỡ hoặc luôn hiện trong mọi công cụ monitor-deadlock detection**. Khi cần điều tra, kết hợp thread dumps, các vị trí `<clinit>`, stack traces và trạng thái những classes phụ thuộc.

## 7. Vòng phụ thuộc cùng thread khác vòng chờ nhiều thread thế nào?

| Thuộc tính | Same-thread recursive initialization | Cross-thread initialization deadlock |
| --- | --- | --- |
| Ai đang init A? | T1 | T1 |
| Ai đang init B? | T1 | T2 |
| B cần A khi A chưa xong | T1 yêu cầu A đang init bởi chính T1 | T2 yêu cầu A đang init bởi T1 |
| Quy tắc JLS áp dụng | Recursive request cùng thread **return bình thường** | Yêu cầu từ thread khác **phải chờ** |
| Có nhất thiết bế tắc? | Không theo cơ chế chờ riêng này | Có thể bế tắc nếu tạo vòng T1 → T2 → T1 |
| Có thể thấy giá trị static mặc định? | Có, nếu field chưa được gán trong initializer | Thread khác không được tùy ý coi class đang init dở là fully initialized |
| Rủi ro chính | Trạng thái khởi tạo một phần; thứ tự truy cập gây kết quả bất ngờ | Thread không tiến triển, ứng dụng treo |
| Cách tránh | Giảm static dependencies đệ quy; tránh đọc field khi chưa set | Không tạo dependency cycle giữa initializers, đặc biệt khi chạy đa thread |

Một vòng tham chiếu trong **code** không nhất thiết gây deadlock. Kết quả phụ thuộc **thread nào khởi tạo class nào trước**, **tại lúc nào** và **đường gọi của initializer**.

## 8. Khi initialization thất bại: class có thể ở trạng thái erroneous

Không phải mọi initializer kết thúc thành công. Xét static field initializer gọi một phương thức ném exception:

```java
static int value = fail();
```

Khi một exception thông thường làm initialization hoàn tất bất thường, JLS yêu cầu xử lý throwable phù hợp, thường **bọc trong `ExceptionInInitializerError`**. Class/interface được đánh dấu ở **erroneous state**.

Những lần active-use tiếp theo của **chính runtime class đó** không đơn giản chạy lại static initializer; chúng thường dẫn tới **`NoClassDefFoundError`** liên quan việc không thể khởi tạo class.

### 8.1. Ví dụ Java 17+ chạy được: lần đầu lỗi, lần sau không thử lại

Tạo tệp **`FailedInitDemo.java`**:

```java
public class FailedInitDemo {
    static class Broken {
        static int value = initialize();

        static int initialize() {
            throw new IllegalStateException("bad configuration");
        }
    }

    public static void main(String[] args) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                System.out.println(Broken.value);
            } catch (LinkageError error) {
                System.out.println("attempt " + attempt + ": "
                        + error.getClass().getSimpleName());
            }
        }
    }
}
```

Chạy:

```bash
javac --release 17 FailedInitDemo.java
java FailedInitDemo
```

**Stdout chính xác:**

```text
attempt 1: ExceptionInInitializerError
attempt 2: NoClassDefFoundError
```

Giải thích:

1. Lần đầu đọc `Broken.value` khiến `Broken` được khởi tạo. `initialize()` ném `IllegalStateException`, được bọc thành `ExceptionInInitializerError` theo quy tắc vì throwable ban đầu không phải `Error`.
2. `Broken` được đánh dấu erroneous.
3. Lần thứ hai, JVM không chạy `initialize()` lại như thể chưa có gì xảy ra; active use dẫn đến `NoClassDefFoundError`.

**Lưu ý kỹ:** Nếu nguyên nhân initializer đã là một subtype của `Error`, cách truyền lỗi ở lần đầu có thể khác; không phải mọi initialization failure đều bắt buộc xuất hiện `ExceptionInInitializerError`. Nếu initialization của superclass thất bại, việc lan truyền cũng tuân theo các nhánh riêng trong JLS.

### 8.2. Khi nhiều thread đang chờ class lỗi

Nếu T1 đang init class C, T2/T3 đang đợi, rồi initializer của C kết thúc bất thường:

- JVM đánh dấu C **erroneous**.
- Những thread chờ được thông báo để kiểm tra lại trạng thái.
- Các yêu cầu sử dụng sau đó không biến C trở lại trạng thái “chưa init”; chúng gặp quy tắc lỗi phù hợp, thường `NoClassDefFoundError`.

Nên phân biệt **nguyên nhân gốc** ở lần khởi tạo đầu với **triệu chứng tiếp theo** trong log ở các thread khác.

### 8.3. Có thể “reset static initializer” bằng reflection không?

Không có API Java chuẩn để đặt một `Class` đang erroneous quay lại trạng thái “uninitialized” và chạy lại `<clinit>` theo ý muốn. Tạo **định nghĩa class mới bằng class loader khác** có thể dẫn đến class identity khác, nhưng đó là một runtime class khác với vòng đời/định danh riêng, không phải “repair” class cũ tại chỗ.

Đối với config có thể thay đổi sau startup, tránh đặt mọi thao tác kết nối bên ngoài và validate bất ổn vào `static initializer`. Thiết kế explicit initialization có kiểm soát thường dễ retry và giám sát lỗi hơn.

## 9. Các cơ chế liên quan: superclass, interface default methods và constant variables

### 9.1. Superclass initialization

Khi JVM khởi tạo một class, các superclass cần thiết được khởi tạo theo đúng trình tự (nếu chưa). Ví dụ:

```java
class Base {
    static {
        System.out.println("Base initialized");
    }
}

class Derived extends Base {
    static {
        System.out.println("Derived initialized");
    }
}
```

Nếu active-use `Derived` trước và `Base` chưa init, thông thường `Base` được khởi tạo trước `Derived`. Đoạn này là **mã minh họa class**, chưa có `main` độc lập.

### 9.2. Interface có default methods

Trong Java hiện đại, initialization của một class cũng liên quan những **superinterfaces khai báo default methods** theo thuật toán JLS, không chỉ trực tiếp superclass. Đây là chi tiết thường bị bỏ qua khi debug trình tự `<clinit>`.

Ngược lại, **khởi tạo một interface** không tự động bắt mọi superinterface của nó phải được initialized chỉ vì có quan hệ kế thừa interface.

### 9.3. Compile-time constants

Ví dụ:

```java
class Constants {
    static final int LIMIT = 100;
    static final Integer BOXED = Integer.valueOf(100);
}
```

`LIMIT` là compile-time constant variable trong điều kiện thông thường và có thể được compiler inline. `BOXED` **không** phải constant variable theo cùng nghĩa, vì initializer không là constant expression của kiểu primitive/String theo JLS.

Đọc `Constants.LIMIT` từ nơi khác có thể không kích hoạt class initialization của `Constants`; đọc `Constants.BOXED` là một tình huống active use thích hợp. Vì vậy dùng compile-time constant để “kích hoạt static initialization” là cách làm không đáng tin.

## 10. Làm sao class loader ảnh hưởng phạm vi initialization?

Trong JVM, định danh runtime của class/interface gắn với **binary name + defining class loader**. Hai loader có thể định nghĩa hai class cùng binary name nhưng là **hai runtime types khác nhau**, mỗi type có trạng thái initialization riêng.

```text
("demo.Cache", LoaderA) → class A, init state A, lock LA
("demo.Cache", LoaderB) → class B, init state B, lock LB
```

Do đó:

- Mỗi định nghĩa class được JVM quản lý initialization của chính nó.
- Một application server/plugin host có thể có nhiều bản `demo.Cache` được định nghĩa bởi các loaders khác nhau.
- Static fields của hai runtime classes đó **không tự chia sẻ** dù binary name giống nhau.
- “Static field là global toàn JVM theo tên class” là một hiểu lầm.

Đây là lý do các vấn đề liên quan hot reload, plugin loaders và static singletons cần xét thêm class-loader boundaries.

**Không dựa vào việc dùng loader mới như giải pháp thông thường cho deadlock**: nó có thể tạo các type identity mới và nhiều ràng buộc liên kết/API phức tạp hơn. Tốt nhất là sửa thiết kế dependency của initializer.

## 11. Chẩn đoán tiến trình dường như bị treo trong `<clinit>`

Khi ứng dụng ngừng tiến triển lúc startup, không có exception rõ ràng, có thể nghi ngờ:

- Một static initializer đang thực hiện thao tác chậm/chờ I/O.
- T1 đang init class A, T2 yêu cầu A nên đợi hợp lệ.
- T1 và T2 tạo vòng chờ A ↔ B.
- Initializer giữ application lock rồi cần class khác, kết hợp với thread khác tạo vòng chờ hỗn hợp (monitor + class initialization).
- Static initializer gọi `Future.get()`, `join()`, `CountDownLatch.await()` hoặc API chờ tác vụ mà tác vụ đó lại yêu cầu class đang init.

### 11.1. Thu thập thread dump

Với HotSpot JDK có thể thử:

```bash
jcmd <pid> Thread.print
```

Trong nhiều môi trường cũng có thể dùng:

```bash
jstack <pid>
```

Các lệnh trên là **ví dụ thao tác chẩn đoán**, thay `<pid>` bằng PID thực tế của tiến trình Java. Lệnh `jcmd`/`jstack` thường thuộc bộ công cụ JDK; output và hỗ trợ có thể khác theo runtime/phiên bản.

Để điều tra:

1. Tìm thread đang chạy trong `<clinit>` hoặc phương thức do initializer gọi.
2. Tìm thread đang chờ class initialization, monitor, latch hoặc join.
3. Xác định quan hệ **class nào đang được thread nào init**.
4. Vẽ **wait-for graph**, ví dụ `T1 → Class B (T2)` và `T2 → Class A (T1)`.
5. Kiểm tra static field initializers, static blocks, dependency cycles, logging và I/O bên trong initializer.
6. Xác định cả class-loader identity nếu có nhiều loaders tạo classes trùng binary name.

### 11.2. Deadlock detector có thể không hiển thị hết vòng chờ

Một số công cụ tìm deadlock qua **monitor/ownable synchronizer**. Class initialization waiting có thể không xuất hiện giống một monitor lock thông thường trong mọi thread dump hoặc công cụ. Đừng kết luận “không deadlock” chỉ vì một báo cáo `findDeadlockedThreads()` trả null.

Sử dụng cả stack traces và thông tin class initialization. Trong trường hợp cần thiết, bổ sung logging trước/sau static initialization (trong môi trường kiểm thử) để xác định class nào không hoàn tất.

### 11.3. Ghi log class initialization đúng cách

Trên một số JVM HotSpot hiện đại, **Unified Logging** có các tag về class loading/initialization; chọn mức và tag được JVM thực tế hỗ trợ, kiểm tra bằng:

```bash
java -Xlog:help
```

Không áp đặt một tổ hợp tag/mức cố định cho mọi bản triển khai Java. Để điều tra, còn có thể log vòng đời của các thành phần ứng dụng **trước khi** active-use class thay vì nhét thêm logging phức tạp vào chính `<clinit>` và vô tình thay đổi timing của deadlock.

## 12. Cách thiết kế tránh phụ thuộc vòng trong static initializer

### 12.1. Tránh blocking operations trong `<clinit>`

Những hoạt động rủi ro khi đặt trực tiếp trong static initialization:

- Chờ network/database.
- Chờ một thread/task khác bằng `join()`, `Future.get()`, `await()`.
- Lấy nhiều locks theo thứ tự không thống nhất.
- Khởi tạo plugin/module khác vốn có thể tham chiếu ngược về lớp hiện tại.
- Đọc cấu hình có thể lỗi và khiến class chuyển erroneous, khó retry.

Không phải mọi tác vụ như vậy đều bị JVM cấm. Rủi ro nằm ở **cơ chế initialization được đồng bộ và có thể bị chờ bởi nhiều thread**.

### 12.2. Đồ thị phụ thuộc không vòng

Thay vì:

```text
A.<clinit> → B.<clinit> → A.<clinit>
```

hãy tách trách nhiệm:

```text
Config values
      |
      v
Service A        Service B
      \            /
       \          /
        explicit startup coordinator
```

Có thể xây các object trong một bước khởi tạo rõ ràng, có thứ tự, timeout và recovery policy. Các lớp không cần kéo nhau vào vòng static initializer trong quá trình tải.

### 12.3. Initialization-on-demand holder: dùng đúng phạm vi

Một idiom Java thường gặp:

```java
public final class Service {
    private Service() {}

    private static class Holder {
        static final Service INSTANCE = new Service();
    }

    public static Service getInstance() {
        return Holder.INSTANCE;
    }
}
```

Đây là **class minh họa**, không phải chương trình độc lập. Nó dựa vào class initialization để tạo singleton khi `Holder` được active-use lần đầu, theo cơ chế đồng bộ JVM.

Ưu điểm: khởi tạo trễ, không cần synchronized thủ công trong `getInstance()`, thread khác chờ đúng khi init đang tiến hành.

**Giới hạn:** Holder idiom **không** tự giải quyết vòng dependency giữa Holder và các classes khác; nếu constructor của `Service` gọi code khởi tạo lớp đang chờ ngược lại, vẫn có thể gặp bế tắc.

### 12.4. Explicit lifecycle và lỗi có thể thử lại

Với tài nguyên bên ngoài (database connections, clients, remote configuration), dùng startup lifecycle có timeout và trạng thái có thể retry, thay vì để toàn bộ kết nối nằm trong `static final`:

```java
final class ServiceManager {
    private volatile boolean ready;

    public synchronized void initialize() {
        // Nạp config, kết nối, kiểm tra dependency theo thứ tự rõ ràng.
        ready = true;
    }

    public boolean isReady() {
        return ready;
    }
}
```

Đoạn trên **chỉ minh họa hình dạng API**, chưa xử lý lỗi, nhiều lần gọi, idempotency, shutdown hoặc resource cleanup đầy đủ. Điểm học tập là: **quy trình explicit initialization có thể điều khiển** dễ quan sát và tái thử hơn class initializer thất bại khiến class thành erroneous.

### 12.5. Tránh xuất bản đối tượng chưa hoàn chỉnh từ initializer

Class initialization có bảo đảm đồng bộ riêng, nhưng **vòng cùng-thread** vẫn có thể nhìn thấy static field chưa được gán. Đừng xuất bản reference tới static state đang xây dựng dở qua các callback hoặc class phụ thuộc.

Nếu nhiều thành phần cần tham chiếu nhau, cân nhắc:

- Tách bước tạo đối tượng và bước nối dependencies.
- Dùng dependency injection hoặc factory.
- Thiết kế invariant được thiết lập hoàn chỉnh trước khi object được công bố.
- Viết tests cho thứ tự active-use khác nhau và các trường hợp concurrent startup.

## 13. Sai lầm thường gặp

| Nhận định sai | Cách hiểu đúng |
| --- | --- |
| “JVM giữ `SomeClass.class` monitor suốt `<clinit>`.” | `LC` là lock khởi tạo theo đặc tả, ánh xạ tùy JVM; thủ tục đánh dấu trạng thái rồi nhả lock trước khi chạy phần khởi tạo còn lại. |
| “Thread khác có thể đọc static fields khi class đang init dở.” | Thread khác phải tuân theo quy tắc **chờ** class đang init bởi thread khác trước khi hoàn tất active-use. |
| “Tham chiếu vòng A → B → A luôn deadlock.” | Cùng thread xử lý recursive request bằng return bình thường; có thể thấy default values. |
| “Vòng A → B → A luôn an toàn.” | Nhiều thread có thể tạo chu trình chờ thật. |
| “Initialization lock là `synchronized(SomeClass.class)` của ứng dụng.” | Không có bảo đảm đó; đừng dùng monitor class literal như API thay thế. |
| “Static initializer luôn chỉ chạy khi `new` object.” | Static method/field active use và reflection có thể kích hoạt; tạo array của type không tự tạo instance. |
| “Truy cập `static final` luôn khởi tạo class.” | Compile-time constant variables có ngoại lệ. |
| “`ClassCircularityError` sẽ giải quyết static initialization deadlock.” | Không phải cơ chế chung cho vòng chờ giữa các static initializers. |
| “Một lần init lỗi, lần sau JVM tự thử lại.” | Runtime class ở erroneous state; các lần dùng sau thường gặp `NoClassDefFoundError`. |
| “`ExceptionInInitializerError` luôn là lỗi đầu tiên.” | Nếu throwable gốc là subtype của `Error` hoặc lỗi phát sinh ở superclass, đường lan truyền có thể khác. |
| “Static singleton mặc định shared toàn JVM bất kể class loader.” | Cùng binary name dưới loaders khác có thể là những runtime classes khác, mỗi class có static state riêng. |
| “Thread dump không ghi monitor deadlock thì không có initialization deadlock.” | Class-init waiting không luôn được công cụ phát hiện giống monitor deadlock. |
| “Holder idiom xử lý mọi vòng phụ thuộc.” | Nó chỉ giúp lazy init một trường hợp đơn giản, không tự loại bỏ dependency cycles. |
| “Thêm `Thread.sleep` trong `<clinit>` là cách ổn định concurrency.” | Sleep làm thay đổi scheduling, không thiết kế lại dependency và có thể che khuất vấn đề. |

## 14. Bài tập tự kiểm tra kèm đáp án

**Câu 1.** Hai thread cùng truy cập một trường `static` không phải compile-time constant của class chưa khởi tạo. JVM có chạy initializer hai lần song song không?

**Đáp án:** Không. Một thread chịu trách nhiệm khởi tạo runtime class; thread khác chờ hoặc nhận kết quả sau khi class đã initialized.

**Câu 2.** Vì sao thread hiện đang init A lại không tự deadlock khi trong initializer của A nó gián tiếp gọi A?

**Đáp án:** Theo JLS `12.4.2, nếu A đang được chính thread đó khởi tạo, yêu cầu initialization đệ quy **return bình thường**.

**Câu 3.** Trong `RecursiveInitDemo`, vì sao B nhận `A.value == 0`?

**Đáp án:** `A.value` đã có default value 0 từ preparation nhưng biểu thức `A.value = calculate()` chưa hoàn tất. Cùng thread được phép tiếp tục yêu cầu init đệ quy, nên B đọc giá trị hiện tại là 0.

**Câu 4.** Hai thread T1/T2 lần lượt init A/B; A cần B còn B cần A. Có thể xảy ra điều gì?

**Đáp án:** T1 chờ B do T2 đang init; T2 chờ A do T1 đang init. Đây là một **cross-thread class initialization deadlock**.

**Câu 5.** Khóa `LC` có bắt buộc chính là monitor của object `A.class`?

**Đáp án:** Không. Đặc tả cho phép JVM quyết định ánh xạ initialization lock. Không dùng `synchronized(A.class)` làm thay thế cơ chế init.

**Câu 6.** Thứ gì xảy ra khi class init bằng exception thường như `IllegalStateException`?

**Đáp án:** Lần đầu thường ném `ExceptionInInitializerError` bọc exception gốc và class bị đánh dấu erroneous; active-use sau thường dẫn đến `NoClassDefFoundError`.

**Câu 7.** Tạo `new SomeClass[10]` có chạy constructor của mười `SomeClass` không?

**Đáp án:** Không. Một array object được tạo, các phần tử tham chiếu ban đầu null.

**Câu 8.** Hai loaders riêng định nghĩa class cùng binary name. Chúng có chung initialization lock và static fields không?

**Đáp án:** Không mặc nhiên. Đây là hai runtime classes khác nhau, có trạng thái static và quá trình initialization riêng.

**Câu 9.** Holder idiom bảo đảm không deadlock dù singleton constructor gọi ngược lại nhiều lớp tĩnh khác?

**Đáp án:** Không. Nó chỉ là một hình thức lazy initialization dựa vào cơ chế JVM; dependency cycles vẫn có thể gây lỗi/bế tắc.

**Câu 10.** Vì sao class literal `SomeClass.class` không nên được dùng để ép static initializer chạy?

**Đáp án:** Class literal chỉ tạo/cung cấp `Class` object; không phải active-use bắt buộc chạy initializer của SomeClass theo các quy tắc tương ứng.

**Câu 11.** Tại sao class initialization lock quan trọng dù JVM vẫn có `synchronized` và `volatile` trong Java source?

**Đáp án:** JVM phải có cơ chế điều phối **khởi tạo class trước lần sử dụng cần thiết**, cả cho code sinh từ bytecode và nhiều thread. Đó là trách nhiệm runtime riêng, không yêu cầu mọi class phải tự khai báo monitor hay volatile field.

**Câu 12.** Chỉ thấy app startup treo mà không có `VerifyError`. Có thể là vấn đề initialization không?

**Đáp án:** Có. Bytecode vẫn có thể hợp lệ trong khi nhiều static initializers chờ lẫn nhau. Kiểm tra thread dumps, `<clinit>`, init dependencies và chỗ blocking.

## 15. Checklist vận hành và tổng kết

Khi viết hoặc kiểm tra class initialization:

1. Xác định các static field initializers và static blocks có thể kích hoạt class khác.
2. Vẽ quan hệ phụ thuộc A → B → C; chú ý chu trình quay về lớp ban đầu.
3. Đánh dấu các code path có thể chạy trên nhiều threads khi startup.
4. Tránh blocking I/O, `join`, `await` và đồng bộ khóa phức tạp trong `<clinit>`.
5. Đừng dựa vào thứ tự “vô tình” của lần active-use để đạt invariant.
6. Nếu init có thể lỗi vì yếu tố bên ngoài, cân nhắc explicit lifecycle với timeout/retry.
7. Khi thấy `ExceptionInInitializerError` hoặc `NoClassDefFoundError`, tìm **lần khởi tạo đầu tiên** và nguyên nhân gốc.
8. Khi thấy startup treo, thu thread dumps, lập wait-for graph, kiểm tra `<clinit>`.
9. Với plugin/application servers, xác định **defining class loader**, vì cùng tên class chưa chắc cùng class identity.
10. Kiểm thử cả thứ tự init, startup đồng thời và đường lỗi.

**Ba kết luận quan trọng nhất:**

- JVM đồng bộ hóa class initialization bằng trạng thái và initialization lock `LC`; thread khác có thể phải chờ lớp đang được thread hiện tại khởi tạo.
- Khởi tạo đệ quy **trong cùng thread** không tự chờ chính mình; có thể làm code quan sát default value của static field chưa được gán xong.
- Khởi tạo phụ thuộc vòng **giữa nhiều thread** có thể dẫn tới **deadlock thực sự**. Giải pháp bền vững nằm ở thiết kế dependency và lifecycle, không phải bỏ qua cơ chế đồng bộ của JVM.

### Tài liệu đặc tả chính thức

- [JLS 12.4.1 — When Initialization Occurs (Java SE 17)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.1): active use, superclass/default-interface initialization và constant variables.
- [JLS 12.4.2 — Detailed Initialization Procedure (Java SE 17)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.2): initialization lock `LC`, trạng thái, same-thread recursion, wait/notify và failure.
- [JVMS 5.5 — Initialization (Java SE 17)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5): initialization của class/interface ở cấp JVM.
- [JVMS 5.4 — Linking (Java SE 17)](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4): phân biệt verification, preparation, resolution.
- [JLS 17 — Threads and Locks (Java SE 17)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html): đồng bộ, quan hệ visibility và Java Memory Model.
- [CountDownLatch — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CountDownLatch.html): barrier dùng trong các thí nghiệm thread ở bài.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="linking-and-initialization-011">Lỗi khởi tạo: ExceptionInInitializerError và trạng thái lớp lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Lỗi khởi tạo: `ExceptionInInitializerError` và trạng thái lớp lỗi trong JVM

Một class Java có thể **nạp thành công**, **liên kết hợp lệ**, nhưng **thất bại khi khởi tạo tĩnh**. Khi công việc khởi tạo một class hoặc interface kết thúc bất thường, JVM ghi nhận định nghĩa runtime đó ở **trạng thái lỗi (erroneous state)**. Từ đây, những lần yêu cầu khởi tạo tiếp theo **không tự chạy lại static initializer**. Kết quả thường gây ngạc nhiên: lần đầu có thể thấy `ExceptionInInitializerError`, còn lần sử dụng sau nhận `NoClassDefFoundError`.

Đây không phải hai tên gọi thay thế cho cùng một lỗi. Chúng phản ánh **hai thời điểm và hai trạng thái runtime khác nhau**:

- **Lần đầu:** mã trong hoặc gắn với class initialization phát sinh exception; nếu throwable ban đầu **không phải `Error`**, JVM thường bọc nó trong **`ExceptionInInitializerError`** và đánh dấu class ở trạng thái lỗi.
- **Lần sau:** JVM thấy class đã ở **erroneous state** nên không khởi tạo lại và ném **`NoClassDefFoundError`** theo quy tắc initialization.
- Nếu công việc khởi tạo ban đầu ném một đối tượng **`Error`**, JVM có thể truyền tiếp chính `Error` đó thay vì bọc trong `ExceptionInInitializerError`; class vẫn thất bại khởi tạo và bị đánh dấu lỗi.
- Thông báo lỗi, chuỗi `cause` và stack trace cụ thể có thể khác theo JVM/version; khi phân tích phải giữ **toàn bộ chuỗi lỗi từ lần đầu**, không chỉ nhìn lỗi cuối cùng.

Bài học này giải thích vòng đời khởi tạo, cơ chế lỗi, các trường hợp kích hoạt, cách tái hiện bằng Java 17+, nguyên nhân thường gặp và hướng xử lý an toàn.

## 1. Loading, linking và initialization là những việc khác nhau

Trong JVM, một class/interface đi qua các giai đoạn logic:

```text
Định nghĩa class được JVM yêu cầu
             │
             ▼
        Loading
             │
             ▼
        Linking
   ┌─────────┼─────────┐
 Verification Preparation Resolution
             │
             ▼
    Initialization khi cần
      (chạy <clinit>
       theo quy tắc)
             │
     ┌───────┴────────┐
     ▼                ▼
 Thành công          Thất bại
     │                │
 initialized        erroneous
     │                │
 dùng tiếp        không tự retry
```

Sơ đồ có mục đích **phân biệt trách nhiệm**, không khẳng định JVM phải resolve toàn bộ constant pool trước khi chạy `<clinit>`. Một số reference có thể được giải quyết muộn theo yêu cầu. Việc nạp một class không đồng nghĩa nó đã được initialized.

| Giai đoạn | Ý nghĩa | Có tự động chạy static initializer của class không? |
| --- | --- | --- |
| Loading | Tạo biểu diễn runtime từ định nghĩa do loader cung cấp | Không đồng nghĩa |
| Verification | Xác minh class file/bytecode thỏa các luật JVM | Không |
| Preparation | Chuẩn bị static fields, thiết lập giá trị mặc định và các `ConstantValue` theo luật | Không phải chạy toàn bộ static initializer |
| Resolution | Chuyển symbolic references thành liên kết runtime phù hợp | Không mặc nhiên |
| Initialization | Thực hiện công việc khởi tạo class/interface cần thiết | **Có**, nếu có code `<clinit>` và các bước bắt buộc khác |

Trong Java source:

```java
class Config {
    static int port = parsePort();

    static int parsePort() {
        return Integer.parseInt("not-a-number");
    }
}
```

Đây là **class minh họa** có thể biên dịch nhưng khởi tạo tĩnh sẽ lỗi khi một active use yêu cầu `Config` initialization. Điều đó không có nghĩa ClassFile sai cấu trúc hay verifier không chấp nhận bytecode.

## 2. `<clinit>` được sinh từ đâu?

Tên **`<clinit>`** là phương thức khởi tạo class/interface đặc biệt trong mô hình JVM. Với class có công việc static cần chạy, `javac` thường tổng hợp `<clinit>:()V` từ **static field initializers** và **static initializer blocks**, thực hiện theo thứ tự phù hợp với khai báo nguồn.

Ví dụ:

```java
class ServerSettings {
    static int base = loadBase();

    static {
        base += 2;
    }

    static int loadBase() {
        return 8000;
    }
}
```

Nội dung tương ứng `<clinit>` có thể làm:

```text
base = loadBase();
base = base + 2;
return;
```

Đây là **pseudocode mô tả hoạt động**, không phải bytecode đầy đủ. Khi initialization thành công, `ServerSettings.base == 8002`.

Nếu thay `loadBase()` bằng phương thức ném exception, `<clinit>` có thể dừng giữa chừng: những field được khởi tạo trước đó có thể đã bị ghi giá trị, trong khi những static initializer sau **chưa chạy**. Nhưng sau thất bại, class không tự trở về trạng thái “chưa initialized” để thực hiện lại từ đầu.

### `<init>` không phải `<clinit>`

- `<init>` là constructor/instance initialization method, gắn với **quá trình tạo từng object**.
- `<clinit>` gắn với **class/interface initialization**, thường chỉ một lần cho từng định nghĩa runtime nếu thành công.
- `new Example()` có thể **kích hoạt khởi tạo class trước**, rồi mới có thể chạy constructor body. Nếu class initialization thất bại, constructor body **không đạt tới**.
- Exception ném **trong constructor body `<init>`** không tự động biến class thành erroneous. Có thể tạo instance khác bằng constructor đó sau khi bắt exception, nếu logic cho phép.

Phân biệt này đặc biệt quan trọng khi nhìn stack trace: `ExceptionInInitializerError` phản ánh lỗi ở **class initialization**, không phải cứ có exception khi `new` thì đều là lỗi `<clinit>`.

## 3. Khi nào JVM yêu cầu initialization?

Class initialization thường được kích hoạt trước các **active uses**, trong đó có:

1. `new T()` khi `T` chưa initialized.
2. Lần thực thi `getstatic`/`putstatic` liên quan static field **được khai báo trong class/interface phải khởi tạo**, theo quy tắc tương ứng.
3. `invokestatic` tới static method của kiểu cần khởi tạo.
4. Một số thao tác reflection, method handles hoặc cơ chế bootstrap có yêu cầu initialization.
5. Trước khi class chính khởi chạy theo quy tắc JVM; và những trường hợp khác được JVMS quy định.

Ví dụ:

```java
class LazyStore {
    static int value = init();

    static int init() {
        System.out.println("LazyStore initialized");
        return 5;
    }
}
```

Một lệnh `System.out.println(LazyStore.value)` thông thường yêu cầu initialization trước khi đọc kết quả. Nhưng **không phải mọi lần nhắc tên class đều là active use**.

### 3.1. Những thao tác không tự buộc `<clinit>` chạy

```java
Class<?> clazz = LazyStore.class;
```

Class literal cho phép lấy `Class` object mà không tự yêu cầu `LazyStore` initialization. Tương tự, tạo **mảng tham chiếu** `new LazyStore[3]` không tự tạo ba `LazyStore` instances và không tự gọi constructor hay static initializer của component class.

Một overload hữu ích của reflection:

```java
Class.forName("LazyStore", false, someClassLoader);
```

Với `initialize=false`, API yêu cầu không initialize target class chỉ vì lookup này. Đoạn trên **chỉ minh họa cú pháp** và cần có biến `someClassLoader` phù hợp, code xử lý `ClassNotFoundException`, và binary name chính xác.

### 3.2. Compile-time constant khác static value tính lúc chạy

```java
class Constants {
    static final int LIMIT = 100;
    static int runtimeValue = compute();

    static int compute() {
        System.out.println("Constants initializing");
        return 3;
    }
}
```

`Constants.LIMIT` là constant variable có thể được compiler **inline** vào caller, vì vậy việc chỉ dùng hằng số này không tự làm class initialized. `Constants.runtimeValue` cần công việc static initialization, nên truy cập thông thường kích hoạt nó.

Không đánh đồng mọi `static final` với compile-time constant. Ví dụ `static final Object HOLDER = new Object();` không phải hằng compile-time; cần thực thi khởi tạo object.

**Hệ quả thực tế:** ứng dụng có thể dùng một số constants hoặc Class literals bình thường dù sau đó active-use class lại dẫn đến lỗi initialization.

## 4. Bốn trạng thái JVM dùng khi khởi tạo một class/interface

*JVMS §5.5* mô tả cơ chế initialization bằng bốn trạng thái logic:

| Trạng thái | Diễn giải | Khi có yêu cầu initialization |
| --- | --- | --- |
| Verified/prepared, chưa initialized | Chưa bắt đầu công việc initialization | JVM bắt đầu quy trình |
| Initializing bởi một thread cụ thể | Một thread đã nhận trách nhiệm initialization | Thread khác chờ; chính thread đó yêu cầu đệ quy được xử lý đặc biệt |
| Fully initialized | Initialization đã thành công | Không chạy lại `<clinit>`; tiếp tục |
| **Erroneous** | Initialization trước đó thất bại | **Không retry**; JVM ném `NoClassDefFoundError` |

JVM có **initialization lock** riêng theo runtime class/interface để phối hợp concurrency. Khi một thread bắt đầu initialization, thread khác yêu cầu initialization cùng class sẽ không tự thực thi `<clinit>` song song. JVM cho phép một trường hợp **yêu cầu khởi tạo đệ quy trong chính thread đang initializing** hoàn thành phần yêu cầu lặp mà không đợi chính nó.

Không có Java API public tiêu chuẩn để đặt Class object từ `erroneous` trở về `uninitialized`. Trạng thái này gắn với **một runtime class definition**, không đơn thuần với chuỗi tên hay một object instance riêng.

### 4.1. Class được định danh theo defining loader

Hai runtime classes cùng binary name nhưng do hai **defining class loaders** khác nhau định nghĩa là hai loại runtime khác nhau. Mỗi định nghĩa có **trạng thái initialization riêng**, static fields riêng và lỗi riêng.

Do đó phát biểu chính xác là:

> Nếu initialization của **một runtime class/interface** thất bại, các yêu cầu initialization tiếp theo với **chính runtime type đó** không tự thử lại.

Tạo một class loader mới và nạp **một định nghĩa class mới** có thể có trạng thái initialization mới; đó không phải “reset” định nghĩa cũ. Trong ứng dụng thông thường, quá trình sửa lỗi thường cần deploy binary phù hợp hoặc khởi động lại quy trình/ứng dụng theo kiến trúc đang dùng, thay vì cố thao tác trạng thái JVM bằng reflection.

## 5. `ExceptionInInitializerError`: lỗi lần khởi tạo đầu tiên

Khi JVM thực thi class initialization, công việc này có thể thất bại do:

- Static field initializer đọc cấu hình không hợp lệ.
- Static block thao tác với tệp, môi trường hoặc tài nguyên thiếu.
- `Integer.parseInt`, chuyển kiểu, `null` dereference hay index sai trong đoạn code static.
- Một dependency được active-use trong lúc khởi tạo class lại thất bại initialization.
- Có lỗi nạp/liên kết/phân quyền hoặc lỗi runtime xảy ra trong chuỗi khởi tạo.
- Vòng phụ thuộc hoặc logic static quá phức tạp dẫn đến exception.

Ví dụ:

```java
class Broken {
    static int threshold = parse();

    static int parse() {
        return Integer.parseInt("invalid");
    }
}
```

Đây là **class minh họa**. Source biên dịch được, nhưng `new Broken()` lần đầu có thể gây:

```text
ExceptionInInitializerError
  caused by NumberFormatException
```

**Tại sao bọc?** Khi initialization thất bại do throwable **không phải `Error`**, quy trình JVM nỗ lực tạo `ExceptionInInitializerError` với nguyên nhân ban đầu, đánh dấu class erroneous và ném lỗi thích hợp. Mục tiêu là biểu thị rằng một **quá trình khởi tạo class** đã thất bại, không chỉ đơn thuần method `parse()` trả exception.

### 5.1. `ExceptionInInitializerError` thuộc nhóm nào?

```text
Throwable
└── Error
    └── LinkageError
        └── ExceptionInInitializerError
```

Nó là **`Error`**, không phải `Exception`. Vì vậy:

```java
try {
    new Broken();
} catch (Exception e) {
    // Không bắt được ExceptionInInitializerError
}
```

Đây là **mã minh họa**, không phải lớp chạy hoàn chỉnh. `catch (Exception)` không bắt được `Error` nói chung. Khi cần thể hiện bài lab, có thể bắt chính xác `ExceptionInInitializerError` hoặc `LinkageError` đã biết để đọc cause. Trong code production, **không nên nuốt lỗi initialization để tiếp tục sử dụng class lỗi**.

### 5.2. Xem cause gốc

`ExceptionInInitializerError` hỗ trợ:

```java
Throwable original = error.getCause();
Throwable legacy = error.getException();
```

Trong đó `error` là biến có kiểu `ExceptionInInitializerError` và có giá trị phù hợp. `getException()` là API lịch sử; `getCause()` thường quen thuộc hơn trong phân tích chuỗi lỗi. Cần lưu cả **thông báo gốc, stack trace gốc và frame đầu tiên thuộc source của mình**.

Lưu ý constructor/overload của `ExceptionInInitializerError` có các trường hợp không gắn cause; đừng giả định `getCause()` luôn khác `null` nếu chính ứng dụng hoặc thư viện **tự tạo** một instance lỗi. Trong tình huống VM tự bọc throwable thích hợp, cause thường rất hữu ích.

### 5.3. Nếu `<clinit>` ném `Error` thì sao?

Quy tắc JVM phân biệt throwable là subtype `Error` với throwable không phải `Error`. Nếu code initialization ném một `AssertionError` hoặc một `Error` khác, JVM **không bắt buộc bọc lại thành `ExceptionInInitializerError`**; lỗi gốc có thể được ném trực tiếp. **Runtime class vẫn vào erroneous state** sau kết thúc bất thường.

Vì vậy định nghĩa “mọi lỗi static init đều là `ExceptionInInitializerError`” là **sai**. Phần lab sẽ kiểm chứng cả nhánh exception bị bọc và nhánh `Error` được giữ nguyên.

## 6. `NoClassDefFoundError` ở lần dùng sau: class đã lỗi, không phải luôn mất tệp

Khi một runtime class đã ở erroneous state, một yêu cầu initialization tiếp theo không chạy lại toàn bộ static initializer mà gây **`NoClassDefFoundError`**.

```text
Lần đầu active use
  → bắt đầu <clinit>
  → IllegalStateException
  → JVM bọc ExceptionInInitializerError
  → class bị đánh dấu erroneous

Lần thứ hai active use cùng runtime class
  → kiểm tra trạng thái
  → erroneous
  → NoClassDefFoundError
  → KHÔNG chạy lại <clinit>
```

Không nên đọc mỗi chữ “NoClassDefFound” rồi lập tức kết luận thiếu JAR/file. `NoClassDefFoundError` có thể xuất hiện bởi nhiều nguyên nhân khác nhau, trong đó có:

1. JVM cần định nghĩa một class nhưng không lấy được nó trong quá trình loading/linking.
2. Một runtime class đã **khởi tạo thất bại** trước đó.
3. Một lỗi linkage/initialization trong dependency khiến một class liên quan không thể được dùng như kỳ vọng.

Trong trường hợp class erroneous, thông báo thường có dạng liên quan **“Could not initialize class ...”**; thông báo chi tiết **không ổn định giữa JVM/version**. Tìm lỗi **đầu tiên** trong logs, nhất là `ExceptionInInitializerError` và cause của nó.

### 6.1. Vì sao bắt lỗi đầu tiên rồi retry không sửa được?

Một đoạn như:

```java
for (int i = 0; i < 3; i++) {
    try {
        useBrokenClass();
    } catch (LinkageError e) {
        // Ghi nhận lỗi, nhưng không "reset" class
    }
}
```

là **pseudocode dạng Java**: cần có method `useBrokenClass()` trong ngữ cảnh phù hợp mới biên dịch. Nếu lần đầu class đã erroneous, những lần lặp không trở lại `<clinit>`. Việc dùng `Thread.sleep(...)`, thử lại ngay, hoặc gọi `System.gc()` không làm JVM xóa trạng thái erroneous.

Cách sửa phải hướng tới **nguyên nhân ban đầu và vòng đời deployment/runtime class**.

## 7. Lab Java 17+ chạy được: quan sát hai kiểu thất bại

Tạo file duy nhất **`InitializerFailureDemo.java`**:

```java
public class InitializerFailureDemo {
    static class Broken {
        static int configuration = load();

        static int load() {
            System.out.println("Broken initializing");
            throw new IllegalStateException("bad configuration");
        }

        Broken() {
            System.out.println("Broken constructor");
        }
    }

    static class Fatal {
        static int flag = fail();

        static int fail() {
            System.out.println("Fatal initializing");
            throw new AssertionError("fatal problem");
        }

        Fatal() {
            System.out.println("Fatal constructor");
        }
    }

    static class Healthy {
        static int count = init();

        static int init() {
            System.out.println("Healthy initializing");
            return 12;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("start");

        Class<?> mirror = Broken.class;
        System.out.println("literal: " + mirror.getSimpleName());
        Class.forName(mirror.getName(), false, mirror.getClassLoader());
        System.out.println("lookup without initialization");

        try {
            new Broken();
        } catch (ExceptionInInitializerError e) {
            System.out.println("broken first: "
                    + e.getClass().getSimpleName());
            System.out.println("broken cause: "
                    + e.getCause().getClass().getSimpleName());
        }

        try {
            new Broken();
        } catch (NoClassDefFoundError e) {
            System.out.println("broken second: "
                    + e.getClass().getSimpleName());
        }

        try {
            new Fatal();
        } catch (AssertionError e) {
            System.out.println("fatal first: "
                    + e.getClass().getSimpleName());
        }

        try {
            new Fatal();
        } catch (NoClassDefFoundError e) {
            System.out.println("fatal second: "
                    + e.getClass().getSimpleName());
        }

        System.out.println("healthy first: " + Healthy.count);
        System.out.println("healthy second: " + Healthy.count);
        System.out.println("end");
    }
}
```

Chạy với JDK 17 hoặc mới hơn trong thư mục chứa file:

```bash
javac -encoding UTF-8 InitializerFailureDemo.java
java InitializerFailureDemo
```

**Standard output chính xác:**

```text
start
literal: Broken
lookup without initialization
Broken initializing
broken first: ExceptionInInitializerError
broken cause: IllegalStateException
broken second: NoClassDefFoundError
Fatal initializing
fatal first: AssertionError
fatal second: NoClassDefFoundError
Healthy initializing
healthy first: 12
healthy second: 12
end
```

### 7.1. Vì sao output có thứ tự như vậy?

1. `Broken.class` tạo/lấy Class mirror mà **không bắt buộc initialize `Broken`**; vì vậy chưa có dòng `Broken initializing`.
2. `Class.forName(name, false, loader)` cũng không yêu cầu initialization; in `lookup without initialization` như thường.
3. `new Broken()` lần đầu yêu cầu class initialization, gọi `Broken.load()` từ code static. Method in `Broken initializing` rồi ném `IllegalStateException`.
4. Throwable ban đầu **không phải `Error`**, nên JVM bọc bằng `ExceptionInInitializerError`; cause được in là `IllegalStateException`. Constructor body chưa được thực thi, vì vậy **không có** `Broken constructor`.
5. `new Broken()` lần thứ hai gặp cùng runtime class **erroneous**, ném `NoClassDefFoundError` và **không in** `Broken initializing` lần nữa.
6. `new Fatal()` lần đầu đi qua static initialization và `Fatal.fail()` ném **`AssertionError`**. JVM chuyển tiếp `Error` này theo nhánh tương ứng, nên dòng đầu ghi `AssertionError` thay vì `ExceptionInInitializerError`.
7. `Fatal` vẫn erroneous. Lần thứ hai ném `NoClassDefFoundError`, và constructor body cũng chưa thực thi.
8. `Healthy.count` được dùng lần đầu khiến initialization thành công, in một lần `Healthy initializing`; lần đọc thứ hai không chạy lại initializer.

Chương trình cố ý bắt đúng loại `Error` cần quan sát và in tên lớp của lỗi thay vì `getMessage()`; điều này giữ stdout ổn định trước khác biệt về stack trace/message giữa các JVM. **Trong production, tránh bắt lỗi liên kết/initialization rồi coi hệ thống đã hồi phục.**

### 7.2. Tự xem ClassFile bằng `javap`

```bash
javap -c -p 'InitializerFailureDemo$Broken'
javap -c -p 'InitializerFailureDemo$Fatal'
javap -c -p 'InitializerFailureDemo$Healthy'
javap -c -p InitializerFailureDemo
```

Nháy đơn bảo vệ ký tự `$` của nested class trong Bash hoặc PowerShell; với Windows `cmd.exe` có thể dùng nháy kép.

Trong output lớp `Broken`, tìm phần **`static {}`** mà `javap` hiển thị cho `<clinit>`:

```text
invokestatic ... load:()I
putstatic    ... configuration:I
return
```

Đây là **mô tả mẫu instruction**, không phải listing gồm offset/constant-pool indexes cố định. Vì `load()` ném exception, `putstatic` và `return` theo luồng bình thường không đạt tới trong lần chạy lỗi. JVM đánh dấu class erroneous.

Trong method `main`, quan sát các instruction `new` và các nhánh catch trong exception table. **Không có opcode tên `retry_clinit`** mà `javac` tự thêm sau khi bắt lỗi.

## 8. Đối chiếu: constructor exception không làm class erroneous

Một class có thể initialize thành công nhưng **constructor body của instance** ném exception. Chẳng hạn:

```java
class ConstructorFails {
    static {
        System.out.println("Class initialized");
    }

    ConstructorFails() {
        throw new IllegalArgumentException("bad instance");
    }
}
```

Đây là **class minh họa**, không phải chương trình có `main`. Nếu caller bắt `IllegalArgumentException` khi thực hiện `new ConstructorFails()`, rồi sau đó gọi `new ConstructorFails()` lần nữa, class vẫn initialized thành công; constructor có thể lại ném exception. Không có quy tắc class-erroneous chỉ vì constructor của một instance thất bại.

Tương tự, một **static method bình thường**, được gọi **sau khi class initialized thành công**, có thể ném `IllegalStateException` mà không làm class bị đánh dấu erroneous. Class initialization được coi là một **giai đoạn riêng**, không phải mọi code chạy “trong một class”.

### Bảng đối chiếu ba tình huống

| Nơi xảy ra exception | Class initialization đã thành công? | Hệ quả điển hình |
| --- | --- | --- |
| Trong `<clinit>` hoặc chuỗi công việc initialization | **Chưa** | Throwable/error ban đầu theo quy tắc JVM, class có thể vào erroneous state |
| Trong constructor `<init>` sau class initialization | **Rồi** | Object creation thất bại; class vẫn usable, constructor sau vẫn có thể được thử |
| Trong static method thông thường sau initialization | **Rồi** | Method invocation thất bại; class không bị erroneous chỉ vì exception này |

**Cẩn trọng:** nếu static method được **gọi bởi `<clinit>`**, exception thoát khỏi method đó sẽ làm công việc class initialization thất bại. Cùng một method có thể không làm class erroneous khi được gọi ở thời điểm khác, nhưng khi nó là một mắt xích trong `<clinit>` chưa hoàn thành thì hậu quả khác.

## 9. Lỗi khi khởi tạo superclass hoặc dependency

Khi JVM khởi tạo một subclass, superclass liên quan phải được initialized theo thứ tự quy định. Nếu **superclass initialization** thất bại, subclass không thể tiếp tục initialization bình thường.

```text
Active use of Child
       │
       ▼
Initialize Parent
       │
       ▼
Parent <clinit> fails
       │
       ├── Parent becomes erroneous
       │
       ▼
Child initialization cannot proceed
       │
       └── Child also cannot be used normally
```

Trong trường hợp phụ thuộc kiểu này, error thấy ở lời gọi tạo **Child** có thể có nguyên nhân nằm trong static initializer của **Parent** hoặc interface được yêu cầu khởi tạo theo quy tắc JVM. Xem **cause chain** và các frame khởi tạo thực tế, không chỉ tên class được nêu ở dòng đầu của lệnh `new Child()`.

### Các static dependencies có thể kéo lỗi lan truyền

```java
class DatabaseConfig {
    static final String URL = loadUrl();

    static String loadUrl() {
        throw new IllegalStateException("missing URL");
    }
}

class Repository {
    static String connectionUrl = DatabaseConfig.URL;
}
```

Đây là hai class minh họa. Khi `Repository` initialization cần đọc `DatabaseConfig.URL`, JVM có thể phải initialize `DatabaseConfig`. Nếu lớp phụ thuộc lỗi, đường khởi tạo của `Repository` cũng không hoàn thành bình thường.

**Đừng dùng suy đoán rằng exception gốc luôn được bọc thêm một lớp `ExceptionInInitializerError` cho mỗi dependency.** JVM có luật cụ thể với `Error` và trạng thái runtime; hình dạng cause chain phải quan sát từ lỗi thực tế.

### Static initialization và class loader

Nếu ứng dụng có nhiều class loaders, lỗi trong định nghĩa `Repository` thuộc loader A không mặc nhiên khiến một class cùng binary name nhưng **được định nghĩa độc lập bởi loader B** trở thành erroneous. Dù cùng source bytes, đây là hai runtime classes khác nhau. Tuy nhiên tính độc lập này **không phải giải pháp hồi phục tùy tiện**: thực tế còn có resource leaks, class loader constraints và những phụ thuộc chung.

## 10. Concurrency: tại sao JVM không cho hai thread “sửa lỗi bằng cách thử song song”?

JVM cung cấp cơ chế **initialization lock** cho mỗi runtime class/interface:

1. Thread đầu tiên cần initialize class nhận trách nhiệm và bắt đầu quy trình.
2. Nếu thread khác cần initialization của cùng class khi việc này đang diễn ra, nó chờ trạng thái có kết quả theo quy tắc JVM.
3. Nếu initialization hoàn thành thành công, các thread tiếp tục sử dụng class đã initialized.
4. Nếu initialization thất bại, class chuyển erroneous; các yêu cầu sau **không được chạy lại `<clinit>` đồng thời hoặc tuần tự**.
5. Nếu chính thread đang initializing yêu cầu recursive initialization của class đó, JVM có quy tắc xử lý đặc biệt để không tự chặn chính nó.

Từ đó suy ra: **chạy thêm 10 thread khởi tạo cùng một class không tạo 10 lần retry hợp lệ**. Một vài thread có thể nhìn thấy các kiểu lỗi khác nhau vì thời điểm chúng chạm tới trạng thái class khác nhau; không dự đoán thứ tự console output trong ví dụ đa luồng nếu chưa đồng bộ cụ thể.

### Cảnh báo về circular initialization

Các vòng phụ thuộc static là vùng có rủi ro:

- **Cùng thread:** recursive initialization request của một class đang khởi tạo có thể được JVM cho qua theo quy tắc, khiến code đọc **default values** hoặc giá trị static mới được gán một phần; không nhất thiết ném exception.
- **Khác thread:** các class initialization lock và phụ thuộc chéo có thể tạo tình huống chờ lẫn nhau, thậm chí deadlock nếu thiết kế bất cẩn.
- **Trạng thái không hoàn chỉnh:** các static fields đã gán trước lỗi không biến class lỗi thành một API an toàn; tránh cố khai thác trạng thái trung gian.

Thiết kế static initialization tối giản và ít tác dụng phụ thường an toàn hơn so với init gọi mạng, khóa ngoài, tác vụ bất đồng bộ chờ nhau hoặc registry phụ thuộc vòng.

## 11. Static field có thể được đọc khi còn ở giá trị mặc định

Một vòng tham chiếu nội bộ **không nhất thiết gây lỗi initialization**; nó có thể tạo kết quả bất ngờ:

Tạo file **`PartialInitializationDemo.java`**:

```java
public class PartialInitializationDemo {
    static class Order {
        static int early = readLater();
        static int later = 7;

        static int readLater() {
            return later;
        }
    }

    public static void main(String[] args) {
        System.out.println("early: " + Order.early);
        System.out.println("later: " + Order.later);
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 PartialInitializationDemo.java
java PartialInitializationDemo
```

**Standard output:**

```text
early: 0
later: 7
```

Giải thích:

- **Preparation** cung cấp giá trị mặc định `0` cho static `int` fields theo mô hình JVM.
- Khi `Order.<clinit>` xử lý `early = readLater()`, trường `later` chưa được gán `7` từ initializer nằm **sau**.
- `readLater()` vì vậy đọc giá trị hiện tại `0`.
- Sau đó `later = 7` chạy, nhưng `early` không được tự tính lại.
- Class initialization hoàn thành thành công: **không có `ExceptionInInitializerError`**.

**Đừng hiểu mọi static field có thể đọc bất kỳ đâu trước khai báo:** Java source có những quy tắc về **illegal forward reference**; trong ví dụ, phép đọc thông qua method `readLater()` hợp lệ. Vòng khởi tạo không chỉ có hai kết quả “thành công đúng” hoặc “ném lỗi”: **thành công với state không như mong đợi** cũng đáng chú ý.

## 12. Chẩn đoán `ExceptionInInitializerError` và `NoClassDefFoundError`

Khi nhận báo lỗi, thực hiện tuần tự:

### Bước 1 — Tìm lần lỗi đầu tiên

Ưu tiên log đầu tiên chứa tên class trong chuỗi initialization, có thể là:

```text
ExceptionInInitializerError
Caused by: IllegalStateException: bad configuration
...
```

Đây là **trích cấu trúc minh họa**, không phải stack trace đầy đủ từ một JVM cụ thể. Nếu chỉ thấy `NoClassDefFoundError: Could not initialize class ...`, tìm về log **sớm hơn trong cùng tiến trình và class loader**.

### Bước 2 — Xem cause và static initializer nào chạy

Tìm method `<clinit>` hoặc method được static initializer gọi: `loadConfig`, `registerDriver`, `connect`, `parse`, `initializeCache`, v.v. Những method này là ví dụ chức năng có thể gây lỗi, không phải danh sách những method JVM tự gọi.

### Bước 3 — Phân biệt đường dẫn lỗi

| Triệu chứng | Câu hỏi chẩn đoán |
| --- | --- |
| `ClassNotFoundException` từ `Class.forName` | Tên, loader, module path, classpath đã đúng chưa? |
| `NoClassDefFoundError` khi tải dependency | Class file/dependency có thiếu, phiên bản binary có không tương thích không? |
| `NoClassDefFoundError` sau lỗi static init | Có `ExceptionInInitializerError` hoặc `Error` ban đầu trước đó không? |
| `ExceptionInInitializerError` | Throwable **gốc** trong cause là gì, xuất phát ở initializer nào? |
| `NoSuchMethodError` hoặc `IllegalAccessError` | Đây có phải lỗi linkage của dependency trong đường initialization? |
| `OutOfMemoryError` hoặc `StackOverflowError` | Lỗi tài nguyên/đệ quy xảy ra khi khởi tạo hay ở code sau? |

**Không phải mọi `NoClassDefFoundError` đều do initialization thất bại**. Dùng toàn bộ bối cảnh thay vì suy từ riêng tên exception.

### Bước 4 — Kiểm tra file/class thật sự đang chạy

```bash
java --version
javac --version
javap -c -p 'InitializerFailureDemo$Broken'
```

Với dự án lớn, xác minh phiên bản runtime của JAR/dependency, classpath/module path, class loader, environment variables và cấu hình được đọc trong static initializer. `javap` giúp xem có `static {}` tương ứng `<clinit>` và thứ tự bytecode nào; nó không tự chạy class initialization.

### Bước 5 — Sửa nguyên nhân và restart/khởi tạo lại theo thiết kế

Khi runtime class đã erroneous, chỉ sửa một biến cấu hình bên ngoài rồi tiếp tục truy cập cùng class **không khiến JVM tự chạy lại `<clinit>`**. Thường cần khởi động lại tiến trình/ứng dụng hoặc cơ chế reload có kiểm soát tạo một **runtime class definition mới** sau khi nguyên nhân đã sửa.

Không dùng `System.gc()`, `Thread.sleep()`, thay đổi quyền file giữa chừng hoặc lặp `Class.forName(name, true, sameLoader)` như cách “xóa erroneous state”.

## 13. Giảm rủi ro bằng thiết kế và xử lý lỗi hợp lý

### 13.1. Giữ static initialization tối giản

Ưu tiên static initialization của dữ liệu thuần túy, bất biến, tính được chắc chắn; hạn chế thực hiện tác vụ có thể thất bại vì môi trường:

```java
class SafeConstants {
    static final int MAX_RETRIES = 3;
    static final String PREFIX = "app";
}
```

Với những công việc có thể thất bại (đọc file cấu hình, truy cập mạng, tạo connection), cân nhắc **khởi tạo tường minh trong lifecycle ứng dụng**, trả lỗi rõ ràng cho caller, cho phép retry có kiểm soát và không gắn retry vào `<clinit>` của một class đã hỏng.

### 13.2. Tách tài nguyên khởi tạo động khỏi static initializer

Đoạn **minh họa thiết kế**, chưa là chương trình chạy độc lập:

```java
final class ConnectionManager {
    private ConnectionManager() {}

    static Connection open(String endpoint) {
        return connectWithTimeout(endpoint);
    }
}
```

Ở đây `Connection` và `connectWithTimeout` là **ký hiệu phụ thuộc ứng dụng** chưa được định nghĩa trong ví dụ, chỉ diễn tả việc chuyển thao tác có thể lỗi khỏi `<clinit>`. Trong ứng dụng thực tế, cần cung cấp kiểu/API cụ thể, close resource đúng cách và quyết định retry/backoff dựa trên yêu cầu nghiệp vụ.

### 13.3. Initialization-on-demand holder vẫn có thể thất bại

Mẫu holder:

```java
class ServiceHolder {
    static class Holder {
        static final Service INSTANCE = createService();
    }

    static Service get() {
        return Holder.INSTANCE;
    }
}
```

Đây là **pseudocode Java yêu cầu khai báo thêm `Service` và `createService()`**. Holder giúp trì hoãn initialization cho đến lúc active use, nhưng **không tự mang khả năng retry nếu `Holder.<clinit>` thất bại**. Sau lỗi, `Holder` cũng có thể erroneous. Do đó holder thích hợp cho singleton khởi tạo chắc chắn, không phải cơ chế phục hồi phép kết nối thiếu ổn định.

### 13.4. Không nuốt `Error` một cách mù quáng

`LinkageError`, `ExceptionInInitializerError`, `OutOfMemoryError` có ý nghĩa nghiêm trọng. Một số bài lab bắt chúng để kiểm tra hành vi, nhưng trong production, dùng `catch (Throwable)` chỉ nhằm “giữ ứng dụng sống” có thể che giấu trạng thái runtime không còn đáng tin.

Nếu cần ranh giới xử lý ở container/test runner, log đầy đủ, đánh dấu ứng dụng unhealthy và quyết định isolate/restart phù hợp thay vì tiếp tục gọi vào class erroneous.

### 13.5. Tránh khởi tạo tĩnh theo vòng phụ thuộc

Một thiết kế có:

```text
ServiceA.<clinit>
    ↳ ServiceB.staticMethod()
         ↳ ServiceC.INSTANCE
              ↳ ServiceA.otherStaticField
```

có thể dẫn tới đọc partial state, exception hoặc chờ chéo. Đây là **sơ đồ phụ thuộc**, không phải một thứ tự luôn gây lỗi. Tách dependency graph, dùng dependency injection lúc runtime, đặt initialization order rõ ràng và tránh khóa/chờ I/O kéo dài bên trong static initializer.

## 14. Kiểm tra nhanh các nhận định dễ nhầm

| Nhận định | Đúng hay sai? | Giải thích |
| --- | --- | --- |
| “Đọc `SomeClass.class` luôn thực thi `<clinit>`.” | **Sai** | Class literal không tự kích hoạt class initialization |
| “Static initializer lỗi lần đầu thì `NoClassDefFoundError` chắc chắn xuất hiện ngay.” | **Sai** | Lần đầu thường là `ExceptionInInitializerError` hoặc `Error` gốc; lần sau mới gặp erroneous state |
| “`ExceptionInInitializerError` là một `Exception`.” | **Sai** | Nó là `Error`, thuộc `LinkageError` |
| “Mọi Throwable từ `<clinit>` đều bị bọc một lớp `ExceptionInInitializerError`.” | **Sai** | Throwable thuộc `Error` có thể được giữ nguyên |
| “Lần sau chỉ việc `new` lại class là JVM chạy lại static block.” | **Sai** | Erroneous state không tự reset |
| “Mọi `NoClassDefFoundError` đều do thiếu JAR.” | **Sai** | Có thể do class initialization thất bại trước đó |
| “Constructor `<init>` ném exception sẽ làm class erroneous.” | **Sai**, nếu `<clinit>` đã thành công | Lỗi constructor của instance khác lỗi khởi tạo runtime class |
| “Dùng class loader mới có thể tạo một runtime type khác dù tên trùng.” | **Đúng** | Class identity gắn với defining loader |
| “Một thread thất bại trong `<clinit>` thì thread khác tự khởi tạo lại cùng class.” | **Sai** | JVM dùng initialization lock/state; class lỗi không được retry |
| “Static initialization có thể đọc các field còn ở default value.” | **Đúng** | Khi code dùng thứ tự init/đệ quy nhất định, partial state có thể được quan sát |

## 15. Bài tập thực hành và câu hỏi tự kiểm tra

**1. Lần dùng đầu ném `IllegalArgumentException` trong static initializer, caller thấy gì?**  
**Đáp án:** JVM thường bọc thành **`ExceptionInInitializerError`** vì `IllegalArgumentException` không phải `Error`. Runtime class trở thành erroneous khi initialization thất bại.

**2. Sau khi bắt lỗi trên, gọi `new` lần nữa với cùng runtime class sẽ khởi tạo lại?**  
**Đáp án:** Không. JVM nhận thấy erroneous state và ném **`NoClassDefFoundError`** theo quy tắc initialization.

**3. Nếu `<clinit>` ném `AssertionError` trực tiếp?**  
**Đáp án:** JVM không cần bọc Error đó thành `ExceptionInInitializerError`; lần đầu có thể thấy `AssertionError`, và những lần dùng sau của class erroneous gặp `NoClassDefFoundError`.

**4. Vì sao `new Demo[5]` không chạy static block của `Demo`?**  
**Đáp án:** Nó tạo **một array object của kiểu `Demo[]`** với năm reference mặc định `null`, không tạo năm Demo instances và không tự kích hoạt class initialization của component.

**5. `static final int MAX = 8;` có buộc static block chạy khi caller đọc MAX?**  
**Đáp án:** Không, khi đây là compile-time constant variable được inline theo quy tắc Java. Truy cập một non-constant static field là trường hợp khác.

**6. `Class.forName(name, false, loader)` và `Class.forName(name, true, loader)` có điểm khác gì liên quan tới initialization?**  
**Đáp án:** Overload với `false` không yêu cầu initialization; với `true` yêu cầu initialize class nếu phù hợp và chưa initialized. Nếu class erroneous, yêu cầu initialization với cùng runtime definition không thành công.

**7. `new Demo()` ném `IllegalStateException` từ constructor body sau khi static initializer thành công; Demo có erroneous không?**  
**Đáp án:** Không chỉ vì lỗi constructor. Runtime class có thể đã initialized; lần tạo object tiếp theo vẫn có thể gọi constructor đó.

**8. Có thể sửa cấu hình rồi gọi `Class.forName(name, true, sameLoader)` để thử lại `<clinit>` của class đã lỗi?**  
**Đáp án:** Không. JVM không reset erroneous state cho cùng runtime class chỉ bằng một lời gọi reflection tiếp theo.

**9. Vì sao lần đầu `new Child()` báo lỗi từ `Parent`?**  
**Đáp án:** JVM khởi tạo các superclass cần thiết trước subclass. Nếu `Parent` static initialization thất bại, Child không thể hoàn thành initialization bình thường.

**10. Nếu hai class loaders định nghĩa cùng binary name độc lập thì lỗi init bên loader A có khiến class loader B erroneous không?**  
**Đáp án:** Không mặc nhiên. Hai defining loaders tạo hai runtime classes khác nhau, với trạng thái initialization riêng; nhưng các tài nguyên/dependencies chung có thể vẫn gây lỗi khi loader B tự khởi tạo.

**11. Vì sao chỉ `NoClassDefFoundError` ở cuối log chưa đủ để kết luận nguyên nhân?**  
**Đáp án:** Nó có thể phát sinh do thiếu class khi loading/linking hoặc do runtime class đã erroneous. Cần lỗi **đầu tiên**, cause chain, tên class và loader.

**12. JVM có thể cho static initializer chạy cùng lúc ở hai thread trên cùng một runtime class để tăng tốc?**  
**Đáp án:** Không theo nghĩa thực thi hai initialization riêng của cùng runtime class. JVM phối hợp initialization bằng lock/state; thread khác phải tuân theo trạng thái của class đang initializing.

## 16. Tổng kết và tài liệu gốc

Hãy xem lỗi khởi tạo như **một chuyển trạng thái**, không chỉ là exception bị bắt:

```text
not initialized
       │ active use
       ▼
  initializing
       │
       ├── thành công ──► initialized ──► dùng tiếp
       │
       └── lỗi ────────► erroneous
                            │
                       active use sau
                            │
                            ▼
                    NoClassDefFoundError
```

- **`ExceptionInInitializerError`** thường là bề mặt của **lần khởi tạo thất bại ban đầu** do exception không thuộc `Error`.
- **`NoClassDefFoundError`** ở lần sau có thể là **hệ quả của class erroneous**, không phải bằng chứng thiếu file.
- **`Error` gốc** trong `<clinit>` có thể được JVM truyền tiếp thay vì bọc.
- **Constructor lỗi ≠ class initialization lỗi**.
- **Runtime class identity** gồm tên và defining class loader; cùng định nghĩa đã erroneous **không thể tự retry**.
- Cần xử lý **root cause ban đầu** và thiết kế static initialization an toàn, thay vì cố làm JVM gọi lại `<clinit>`.

Tài liệu chuẩn để kiểm chứng:

- *The Java Virtual Machine Specification*, chương **§5.5 Initialization**, đặc biệt phần thuật toán trạng thái initialization, lỗi và chuyển sang erroneous: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5
- *The Java Language Specification*, **§12.4 Initialization of Classes and Interfaces**: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4
- Java API **`ExceptionInInitializerError`** và **`NoClassDefFoundError`** trong `java.lang`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ExceptionInInitializerError.html và https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/NoClassDefFoundError.html

**Kết luận thực hành:** khi chương trình gặp `NoClassDefFoundError: Could not initialize class ...`, hãy tìm **lần đầu class thất bại khởi tạo**. Cause của `ExceptionInInitializerError` hoặc `Error` gốc thường chỉ tới lỗi thật. Sau khi đã erroneous, retry trong cùng runtime class không chữa được nguyên nhân — phải sửa dữ liệu/phụ thuộc/thiết kế và khởi tạo một định nghĩa runtime phù hợp qua vòng đời ứng dụng hợp lệ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="linking-and-initialization-012">Dự đoán kết quả bằng dấu vết nạp lớp và thứ tự chạy static initializer</a>

<details>
<summary>Xem chi tiết</summary>

# Dự đoán kết quả bằng dấu vết nạp lớp và thứ tự chạy static initializer

## 1. Mục tiêu: nhìn code Java và dự đoán chính xác dòng nào xuất hiện trước

Khi đọc một chương trình có nhiều lớp, kế thừa, interface, static field và static block, thứ tự in log thường bị đoán sai vì người đọc gộp ba việc khác nhau:

1. **Loading:** JVM có biểu diễn runtime của class/interface từ một nguồn class bytes thích hợp.
2. **Linking:** class được kiểm chứng (*verification*), chuẩn bị (*preparation*) và resolve các symbolic references theo quy tắc VM.
3. **Initialization:** JVM thực thi các static field initializers và static blocks thuộc class/interface được yêu cầu khởi tạo (thường qua phương thức đặc biệt `<clinit>`).

**Dấu vết nạp lớp (class-load trace)** và **dấu vết in từ static initializer** đo những sự kiện khác nhau. Một dòng log báo class đã loaded **không có nghĩa** static block của nó đã chạy. Ngược lại, khi một static initializer đã thực thi thành công, lớp đó phải được load, verify/prepare trước, nhưng việc tải lớp có thể diễn ra từ trước mốc in rất lâu.

Mục tiêu của bài là xây dựng **quy trình dự đoán kết quả có thể lặp lại**, không dựa vào thứ tự nạp chủ quan của một phiên bản HotSpot.

### Một ví dụ tư duy

~~~text
Trong JAR có: Main.class, Parent.class, Child.class, Helper.class.

Log class+load:
  Parent loaded
  Child loaded

Stdout từ Java:
  PARENT_INIT
  CHILD_INIT

Không thể từ hai dòng "loaded" suy ra:
- mỗi lớp được initialized ngay khi loaded;
- JVM đã load mọi class trong JAR;
- Helper chắc chắn chưa được load;
- Parent và Child in ngay sau nhau, không có sự kiện runtime khác.
~~~

**Quy tắc vàng khi làm bài dự đoán stdout:** chỉ liệt kê dòng do **mã Java được yêu cầu thực thi** tạo ra theo quan hệ thứ tự được Java/JVM bảo đảm. Nếu bật VM logging, giữ một cột/dòng phân tích riêng cho log của runtime; không trộn log JVM phụ thuộc cấu hình với stdout cố định của chương trình.

## 2. Mô hình class initialization cần thuộc

Class initialization có hai câu hỏi:

- **Ai kích hoạt?** Những hành động được xem là *active use* theo JLS/JVMS.
- **Ai phải chạy trước?** Superclass, một số superinterface, rồi static initialization logic của class được yêu cầu.

Sơ đồ khái quát cho **lần active use đầu tiên của một class C**:

~~~text
Code thực hiện active use C
         |
         v
C đã initialized thành công chưa?
  +------+- Có ----> dùng ngay, không chạy <clinit> lần nữa
  |
  Không
  |
  v
Đảm bảo loading, verification, preparation phù hợp
         |
         v
Phối hợp initialization giữa các thread
         |
         v
Initialize superclass và các superinterface có
default method theo thứ tự mà đặc tả quy định
         |
         v
Thực thi static field initializers / static blocks
của chính C theo thứ tự văn bản
         |
         v
Đánh dấu initialized thành công, tiếp tục active use
~~~

**Đây là mô hình ngữ nghĩa**, không cam kết tất cả substep của linking chỉ diễn ra sau khi active use bắt đầu: JVM có thể tải/resolve sớm trong phạm vi đặc tả. Nếu class đang được initialization bởi **chính thread hiện tại** và đụng lại nó theo đường khởi tạo vòng, VM có quy tắc xử lý riêng; phần giá trị mặc định ở mục 9 là điểm dễ gây bất ngờ.

## 3. Khi nào một class/interface được initialized?

Các tình huống điển hình với Java 17:

| Hành động nguồn hoặc runtime | Kích hoạt initialization của kiểu được chỉ định? | Cần chú ý |
| --- | --- | --- |
| `new Widget()` | **Có** với `Widget` khi chưa initialized | Initialization hoàn tất theo quy tắc trước khi constructor body thực thi |
| `Widget.staticMethod()` | **Có** đối với class/interface thực sự khai báo static method | Static method không cần instance |
| Đọc `Widget.nonConstantStatic` | **Có** đối với class/interface thực sự khai báo field | Tên kiểu trong cú pháp chưa chắc là nơi **khai báo field** |
| Gán `Widget.staticField = value` | **Có** theo quy tắc active use | Trước lần ghi đầu tiên cần trạng thái class phù hợp |
| `Widget.class` | **Không chỉ do class literal** | Có thể cần load/resolve kiểu nhưng không bắt buộc initialize |
| `Class.forName(name)` | **Có** theo overload một tham số khi class tìm thấy | Yêu cầu initialization |
| `Class.forName(name, false, loader)` | **Không yêu cầu initialization** | Có thể tải/liên kết class; `false` không cấm loading |
| `loader.loadClass(name)` | Thông thường **không** tự yêu cầu initialization | Custom loader có thể có side effects riêng |
| `new Widget[3]` | **Không** initialize component class chỉ vì tạo array | Tạo **mảng**, không tạo ba instance `Widget` |
| Đọc `static final int X = 7` là compile-time constant variable | **Không chỉ vì lần đọc này** | Compiler có thể inline literal sang caller |
| Đọc `static final Integer X = 7` | **Có** (không phải primitive/String constant variable theo JLS) | `Integer` là reference; không phải compile-time constant expression của kiểu cho phép |
| Chạy `main` class khởi đầu | **Có** đối với class khởi đầu | Các class JDK cần cho startup cũng đã/đang được init |

**Lưu ý:** việc **load hoặc link** một class có thể chủ động đến sớm hơn trong implementation; bảng trên chỉ phân loại **yêu cầu initialization** của hành động đang xét. Nếu một class đã initialized do thao tác trước, lần dùng mới **không in lại static block**.

### 3.1. Phân biệt compile-time constant và giá trị final thông thường

~~~java
class Config {
    static {
        System.out.println("CONFIG_INIT");
    }

    static final int LIMIT = 7;
    static final String NAME = "JAVA";
    static final Integer BOXED = Integer.valueOf(7);
    static final int COMPUTED = Integer.parseInt("7");
}
~~~

- `Config.LIMIT` và `Config.NAME` có thể được inline vào caller như compile-time constants, nên một lần đọc chúng **không** tự khởi tạo Config.
- `Config.BOXED` và `Config.COMPUTED` không phải *constant variables* theo định nghĩa JLS dù khai báo `static final`. Đọc chúng đòi hỏi truy cập static field tại runtime, nên có thể kích hoạt Config initialization nếu chưa xảy ra.
- **Final không đồng nghĩa constant variable.** Điều kiện quan trọng là kiểu primitive hoặc String, `final`, và initializer là **constant expression** hợp lệ.
- Nếu một caller đã được biên dịch với `LIMIT = 7`, thay binary Config thành `LIMIT = 9` mà không rebuild caller có thể làm caller vẫn sử dụng literal cũ do constant inlining.

Ví dụ Config phía trên chỉ là đoạn minh họa, không có `main`; hãy xem chương trình đầy đủ ở mục 7 để có kết quả in chính xác.

## 4. Dự đoán thứ tự static fields và static blocks trong một class

Các **static variable initializers** và **static initializer blocks** được thực thi theo thứ tự văn bản ở cấp khai báo khi class được initialized. JVM thực hiện preparation trước, khiến một số static fields bắt đầu ở **giá trị mặc định** (ví dụ `int` là 0; reference là null), rồi initialization logic gán giá trị theo source semantics.

~~~java
class Order {
    static int first = print("FIRST_FIELD", 10);

    static {
        System.out.println("FIRST_BLOCK");
    }

    static int second = print("SECOND_FIELD", first + 1);

    static {
        System.out.println("SECOND_BLOCK");
    }

    static int print(String label, int value) {
        System.out.println(label + "=" + value);
        return value;
    }
}
~~~

Đây là đoạn minh họa **không có main**. Lần đầu thực sự initialize `Order`, thứ tự dòng do code Order phát sinh là:

~~~text
FIRST_FIELD=10
FIRST_BLOCK
SECOND_FIELD=11
SECOND_BLOCK
~~~

Không phải static initializer nào cũng có println, nên một lớp có thể initialized mà không xuất hiện dòng nào trên stdout. Khi dự đoán, đừng dùng “không in” làm bằng chứng chưa initialized.

### Static initialization khác constructor

- Static field initializer và static block chạy theo lifecycle **class**.
- **Constructor** và instance field initializer chạy khi một **object** được tạo; chúng có thể chạy nhiều lần cho nhiều instance.
- Khi tạo object của class con, superclass phải initialized theo quy tắc trước class con, nhưng **constructor của superclass** là một giai đoạn khác xảy ra khi instance construction được thực thi.
- Nếu class đã initialized trước, tạo instance thứ hai **không chạy lại `<clinit>`**; các constructor liên quan vẫn thực hiện cho instance mới.

Cách dự đoán: viết hai bảng tách biệt, một bảng **class initialization**, một bảng **object construction**. Sau đó ghép theo các active-use points.

## 5. Thừa kế: superclass được initialized trước subclass

Với:

~~~java
class Parent {
    static { System.out.println("PARENT_INIT"); }
    Parent() { System.out.println("PARENT_CTOR"); }
}
class Child extends Parent {
    static { System.out.println("CHILD_INIT"); }
    Child() { System.out.println("CHILD_CTOR"); }
}
~~~

Khi mã thực hiện lần đầu `new Child()`, trình tự ngữ nghĩa do chương trình tạo ra sẽ là:

~~~text
PARENT_INIT
CHILD_INIT
PARENT_CTOR
CHILD_CTOR
~~~

Giải thích:

1. Khởi tạo **class** Parent trước Child.
2. Khởi tạo **class** Child sau Parent.
3. Bắt đầu **instance initialization/constructor** cho Child, trước hết các phần của superclass object/constructor Parent theo quy tắc.
4. Sau Parent constructor, Child constructor hoàn tất.

**Đừng đánh đồng** `PARENT_INIT` với `PARENT_CTOR`. Một là static initializer có vòng đời class; một là constructor có vòng đời instance. Nếu bạn tạo Child lần thứ hai sau khi cả class đã initialized, lần thứ hai thông thường chỉ in:

~~~text
PARENT_CTOR
CHILD_CTOR
~~~

Hai đoạn trên là mô hình theo source minh họa; chương trình ở mục 6 có đủ code runnable và kết quả đã kiểm chứng.

### Field khai báo ở superclass dù được viết qua Subclass

~~~java
class Base {
    static int value = trace("BASE", 123);
}
class Sub extends Base {
    static { System.out.println("SUB"); }
}
~~~

Đoạn này là minh họa ý tưởng. Nếu biểu thức Java dùng `Sub.value`, **field thực sự được khai báo trong Base**, nên quy tắc active use kích hoạt initialization **Base**, không nhất thiết initialize Sub. Compiler có thể sinh `getstatic` tham chiếu field đã resolve tới Base. Muốn Sub chắc chắn initialized, cần một active use thực sự của Sub như `new Sub()` hoặc gọi static method do Sub khai báo.

Điều này giải thích những bài trắc nghiệm mà tên `Sub` xuất hiện rõ trong source nhưng stdout lại không có `"SUB"`.

## 6. Bài thực hành Java 17: một lần chạy có superclass, interface, hằng và constructor

Tạo tệp **`InitOrderDemo.java`** với toàn bộ mã sau:

~~~java
public final class InitOrderDemo {
    static int mark(String label, int result) {
        System.out.println(label);
        return result;
    }

    static class Parent {
        static int number = mark("PARENT_FIELD", 10);
        static { System.out.println("PARENT_BLOCK"); }

        Parent() {
            System.out.println("PARENT_CONSTRUCTOR");
        }
    }

    interface WithDefault {
        int TOKEN = InitOrderDemo.mark("DEFAULT_INTERFACE_FIELD", 11);
        default void unused() { }
    }

    interface Plain {
        int TOKEN = InitOrderDemo.mark("PLAIN_INTERFACE_FIELD", 22);
    }

    static class Child extends Parent implements WithDefault, Plain {
        static int number = mark("CHILD_FIELD", 20);
        static { System.out.println("CHILD_BLOCK"); }

        Child() {
            System.out.println("CHILD_CONSTRUCTOR");
        }

        static void ping() {
            System.out.println("CHILD_STATIC_METHOD");
        }
    }

    static class Constants {
        static { System.out.println("CONSTANTS_BLOCK"); }

        static final int MAGIC = 7;
        static final int NON_CONSTANT =
                mark("CONSTANTS_COMPUTED", 8);
    }

    public static void main(String[] args) {
        System.out.println("START");

        System.out.println("CLASS=" + Child.class.getSimpleName());
        System.out.println("CONST=" + Constants.MAGIC);
        System.out.println("BEFORE_NEW");

        Child child = new Child();

        System.out.println("AFTER_NEW");
        System.out.println("PLAIN=" + Plain.TOKEN);
        Child.ping();
        System.out.println("NON_CONST=" + Constants.NON_CONSTANT);
    }
}
~~~

Biên dịch và chạy với **JDK 17 trở lên**:

~~~bash
javac --release 17 InitOrderDemo.java
java InitOrderDemo
~~~

### Kết quả stdout đã kiểm chứng

~~~text
START
CLASS=Child
CONST=7
BEFORE_NEW
PARENT_FIELD
PARENT_BLOCK
DEFAULT_INTERFACE_FIELD
CHILD_FIELD
CHILD_BLOCK
PARENT_CONSTRUCTOR
CHILD_CONSTRUCTOR
AFTER_NEW
PLAIN_INTERFACE_FIELD
PLAIN=22
CHILD_STATIC_METHOD
CONSTANTS_BLOCK
CONSTANTS_COMPUTED
NON_CONST=8
~~~

Các dòng trên là **kết quả dự đoán theo Java 17**; hãy biên dịch bằng `javac --release 17` và chạy bằng JDK 17 để tự kiểm chứng thứ tự. Không cần đoán vị trí class load của JVM để có kết quả stdout này.

### 6.1. Giải từng mốc như một bài thi trace

**Mốc 1 — `START`:** `main` đã bắt đầu. Class `InitOrderDemo` cần được initialized để chạy `main`, nhưng chương trình không định nghĩa static initializer nào trong outer class nên không in thêm dấu vết ở đây.

**Mốc 2 — `Child.class.getSimpleName()`:** class literal tạo/lấy một `Class<?>` object cho Child và `getSimpleName()` trả `"Child"`. Thao tác này **không yêu cầu Child initialization** nên chưa có `CHILD_FIELD` hoặc `CHILD_BLOCK`. Nó có thể đi kèm class loading/linking, nhưng việc đó không hiện qua static initializer.

**Mốc 3 — `Constants.MAGIC`:** trường `MAGIC` là compile-time constant kiểu `int`, nên caller in `7` mà chưa kích hoạt Constants `<clinit>`. Sau `CONST=7` vẫn **không có `CONSTANTS_BLOCK`**.

**Mốc 4 — `new Child()`:** đây là lần active use đầu tiên yêu cầu Child initialized. JVM initialize superclass Parent trước; do đó `PARENT_FIELD` rồi `PARENT_BLOCK`. Sau đó **superinterface trực tiếp `WithDefault` khai báo default method** và thuộc tập cần initialized trong quy trình class initialization của Child, nên `DEFAULT_INTERFACE_FIELD` in trước các dòng static của Child.

**Mốc 5 — interface Plain:** Plain không khai báo default method, nên **không bị initialize chỉ vì Child implements Plain** trong tình huống này. `PLAIN_INTERFACE_FIELD` chưa in ở thời điểm Child initialization. Điều này khác với suy nghĩ “initialize class thì phải initialize tất cả interfaces của nó”.

**Mốc 6 — chính Child:** hai static declarations của Child theo thứ tự nguồn, in `CHILD_FIELD`, `CHILD_BLOCK`.

**Mốc 7 — tạo instance:** sau khi phần class initialization đã hoàn tất, phần object construction chạy constructor Parent rồi Child; in `PARENT_CONSTRUCTOR`, `CHILD_CONSTRUCTOR`.

**Mốc 8 — đọc `Plain.TOKEN`:** `TOKEN` là `int` nhưng được tạo bởi **method invocation**, nên nó không phải compile-time constant variable. `getstatic` cho field đã được khai báo trong Plain yêu cầu Plain initialization; in `PLAIN_INTERFACE_FIELD` trước khi `println` in `PLAIN=22`.

**Mốc 9 — `Child.ping()`:** Child đã initialized từ lần `new Child`, vì vậy không chạy lại `CHILD_BLOCK`; chỉ in `CHILD_STATIC_METHOD`.

**Mốc 10 — `Constants.NON_CONSTANT`:** đây là lần active use thực sự của Constants nên `CONSTANTS_BLOCK`, sau đó lời gọi `mark` của field initializer in `CONSTANTS_COMPUTED`; cuối cùng `println` in `NON_CONST=8`.

### Câu hỏi cải biến

- Nếu đảo hai declaration `static int number` và `static { ... }` trong Parent thì hai dòng Parent có đổi không? **Có**, theo thứ tự văn bản.
- Nếu bỏ `default void unused() {}` khỏi WithDefault, có còn được **bảo đảm** in `DEFAULT_INTERFACE_FIELD` khi `new Child()` không? **Không**, vì superinterface không có default method theo quy tắc thường không bắt buộc initialized cùng subclass.
- Nếu thêm `new Child()` lần nữa, static initializer nào chắc chắn chạy lại? **Không có**, nếu tất cả lần khởi tạo trước đã thành công; constructor của Parent và Child vẫn chạy cho object mới.
- Nếu thay `Plain.TOKEN` bằng `Plain.class`, có bảo đảm chạy `PLAIN_INTERFACE_FIELD` không? **Không**.
- Nếu `Constants.MAGIC` đổi từ `int` literal thành `Integer.valueOf(7)`, nó còn là compile-time constant variable không? **Không**.

## 7. Bài thực hành Java 17 thứ hai: loaded/linked nhưng chưa initialized

Tạo file **`TriggerMatrixDemo.java`**:

~~~java
public final class TriggerMatrixDemo {
    static class Target {
        static { System.out.println("TARGET_INIT"); }

        static final int MAGIC = 40;
        static final Integer BOXED = Integer.valueOf(40);

        static void ping() {
            System.out.println("PING");
        }
    }

    public static void main(String[] args)
            throws ClassNotFoundException {
        System.out.println("BEGIN");
        System.out.println("CONST=" + Target.MAGIC);

        Class<?> token = Target.class;
        System.out.println("LITERAL=" + token.getSimpleName());

        Class.forName(token.getName(), false,
                TriggerMatrixDemo.class.getClassLoader());
        System.out.println("LOADED_NO_INIT");

        System.out.println("BOXED=" + Target.BOXED);
        Target.ping();
        System.out.println("FINISH");
    }
}
~~~

Chạy:

~~~bash
javac --release 17 TriggerMatrixDemo.java
java TriggerMatrixDemo
~~~

**Stdout dự đoán theo Java 17 (hãy tự chạy để kiểm chứng):**

~~~text
BEGIN
CONST=40
LITERAL=Target
LOADED_NO_INIT
TARGET_INIT
BOXED=40
PING
FINISH
~~~

### Tại sao thứ tự này quan trọng?

1. `CONST=40`: `static final int MAGIC = 40` là constant variable; lần đọc không phải active use yêu cầu Target initialization.
2. `Target.class` và `getSimpleName()`: có thể khiến JVM biết class nhưng không bắt buộc chạy `<clinit>`.
3. `Class.forName(name, false, loader)`: **có thể load/link** Target nhưng tham số `false` **không yêu cầu initialization**, nên in `LOADED_NO_INIT` trước `TARGET_INIT`.
4. `Target.BOXED`: là static final **reference** `Integer`, không phải constant variable; lần đọc kích hoạt initialization của Target. JVM chạy static block, in `TARGET_INIT`, rồi xử lý giá trị BOXED để `println` in `BOXED=40`.
5. `Target.ping()`: Target đã initialized, nên không có dòng `TARGET_INIT` thứ hai.

Đổi biến thể `false` thành `true` trong lời gọi `Class.forName` để kiểm nghiệm: `TARGET_INIT` sẽ xuất hiện **trước** `LOADED_NO_INIT`, còn lần đọc BOXED sau đó không khởi tạo lại Target. Đây là **biến thể**, không phải kết quả stdout của source gốc.

**Phân biệt:** `Class.forName(name)` overload một tham số mặc định **yêu cầu initialization**. `ClassLoader.loadClass(name)` thông thường chỉ load, không tự động initialize. Với class loader tùy biến, code của loader có thể có side effect riêng nên không nên dự đoán những log tùy biến nếu không có source loader đó.

## 8. Trường hợp đặc biệt: interface có default method và interface không có

Đối với interface, quy tắc initialization khác cách nhiều người đoán:

- **Initialize một interface** không mặc nhiên initialize toàn bộ superinterfaces của nó chỉ vì quan hệ extends.
- **Initialize một class** có superclass phải initialize superclass trước. Theo JLS/JVMS hiện đại, một số **superinterface khai báo default method** cũng cần initialization trong quá trình initialize class ấy.
- Superinterface **không có default method** không nhất thiết initialized vào thời điểm class implementation được initialized; nó có thể chờ active use riêng như đọc static field không phải constant variable.
- “Interface field” là `public static final` theo ngôn ngữ, nhưng điều đó **không có nghĩa tất cả field đều là compile-time constants**. Một field được khởi tạo bằng lời gọi method không là compile-time constant.

Trong ví dụ mục 6, `WithDefault` chứa `int TOKEN = mark(...)` và default method `unused()`: việc Child initialization in `DEFAULT_INTERFACE_FIELD` là điều cần nhớ. `Plain` có `TOKEN = mark(...)` nhưng **không khai báo default method**; initialization của nó được trì hoãn tới lần đọc `Plain.TOKEN`.

Không suy ra Java source chỉ có cú pháp `interface` thì sẽ in initializer khi được nhắc tên. **Dấu vết phụ thuộc quy tắc active use và quá trình initialize class liên quan**, không phải mức độ gần nhau của khai báo trong file.

### Một caveat cấp compiler

Compiler có thể tạo **synthetic default methods** trong những trường hợp nhất định. JLS có ghi chú rằng default-method superinterface initialization có thể không hoàn toàn hiển nhiên từ source code. Khi làm bài yêu cầu kết quả **exact bytecode cụ thể**, cần biết compiler/version và class file thực. Với ví dụ Java 17 tự viết ở mục 6, cấu trúc `default void unused(){}` là rõ ràng.

## 9. Bài thực hành Java 17 thứ ba: circular class initialization

Điều khiến nhiều người ngạc nhiên là **preparation** đặt static fields về giá trị mặc định trước khi code `<clinit>` chạy. Nếu hai class lần lượt khởi tạo nhau trong cùng thread, một class đang initialization có thể bị đọc khi field chưa nhận initializer value.

Tạo file **`CircularInitDemo.java`**:

~~~java
public final class CircularInitDemo {
    static class A {
        static int value = B.value + 1;
        static { System.out.println("A_INIT=" + value); }
    }

    static class B {
        static int value = A.value + 1;
        static { System.out.println("B_INIT=" + value); }
    }

    public static void main(String[] args) {
        System.out.println("RESULT=" + A.value);
        System.out.println("AGAIN=" + B.value);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 CircularInitDemo.java
java CircularInitDemo
~~~

**Stdout đã kiểm chứng:**

~~~text
B_INIT=1
A_INIT=2
RESULT=2
AGAIN=1
~~~

Giải:

1. Main đọc `A.value`. JVM chuẩn bị A với `A.value` mặc định bằng **0**, bắt đầu initialize A.
2. Initializer của A cần `B.value`, nên JVM bắt đầu initialize B; B cũng đã được chuẩn bị nên `B.value` mặc định **0**.
3. Initializer của B đọc `A.value` khi A đang được initialization bởi **chính thread này**. Quy tắc cho phép tiếp tục sử dụng trạng thái hiện có thay vì chạy A initializer lần thứ hai. `A.value` lúc ấy vẫn bằng **0**.
4. B gán `B.value = 0 + 1 = 1` rồi in `B_INIT=1`; B initialization hoàn tất.
5. A tiếp tục, gán `A.value = 1 + 1 = 2`, in `A_INIT=2`; A initialization hoàn tất.
6. Caller in `RESULT=2`; lần đọc B sau đó chỉ lấy `B.value = 1`, in `AGAIN=1`. Không có static initializer nào lặp lại.

**Không suy diễn:** circular initialization trên **nhiều thread** phức tạp hơn, có thể dẫn tới class initialization deadlock khi hai thread chờ nhau; chương trình này chỉ có một main thread truy cập A trước. Đừng nghĩ “mọi circular init luôn trả 0 rồi chạy bình thường” hoặc mọi trường hợp đều có thứ tự in như ví dụ.

### Nếu đổi mốc đầu tiên từ A sang B?

Nếu `main` đọc `B.value` trước, vòng khởi tạo bắt đầu ở B; với logic hai field đối xứng, các giá trị và thứ tự in có thể đổi. Muốn dự đoán, **vẽ đường truy cập chính xác**; không nhìn thứ tự class được khai báo trong source để quyết định class nào khởi tạo trước.

## 10. Cách đọc class-load và class-init traces của JVM

Ở HotSpot/OpenJDK/JDK 17+, Unified Logging có thể ghi riêng sự kiện nạp và khởi tạo:

~~~bash
java '-Xlog:class+load=info,class+init=info' InitOrderDemo
~~~

Hoặc chỉ xem class load:

~~~bash
java -Xlog:class+load=info InitOrderDemo
~~~

Hoặc class initialization:

~~~bash
java -Xlog:class+init=info InitOrderDemo
~~~

**Chú ý:** nhiều trăm sự kiện liên quan thư viện nền JVM có thể xuất hiện, đôi khi **trước khi main bắt đầu**. Thứ tự/số lượng và định dạng log phụ thuộc JVM, JDK build, class data sharing, collector, agents, classpath và cấu hình. Do đó **đầu ra logging không được xem là stdout cố định** của ví dụ.

Có thể ghi VM logging vào file và giữ stdout chương trình dễ đọc hơn:

~~~bash
java '-Xlog:class+load=info,class+init=info:file=class-trace.log:uptime,level,tags' InitOrderDemo
~~~

Phân biệt rõ ba loại thông tin:

| Thông tin | Cho biết gì? | Không cho biết gì? |
| --- | --- | --- |
| `[class,load]` event | VM ghi nhận một class được load, thường có tên/nguồn | Không chứng minh static block đã thực thi |
| `[class,init]` event | VM bắt đầu hoặc thực hiện thao tác initialization của kiểu theo cơ chế logging | Không phải câu lệnh `println` của initializer; có thể class không có `<clinit>` |
| Dòng `PARENT_FIELD`/`CHILD_BLOCK` từ Java | Một câu lệnh trong static initializer đã chạy tới vị trí in | Không mô tả tất cả class đã được load |

**Quan trọng khi lọc log:** nếu chỉ tìm tên `Child` trong file class-load log, bạn có thể thấy cả `InitOrderDemo$Child` lẫn những class JDK có chữ `Child`. Lọc tên đầy đủ và tag log, kiểm tra loader/source khi có. Dấu `$` trong binary name của nested class là ký tự thực, không phải biểu thị nested scope của shell.

### `javap -c -p` giúp gì?

~~~bash
javap -c -p InitOrderDemo
javap -c -p 'InitOrderDemo$Child'
javap -c -v -p 'InitOrderDemo$Constants'
~~~

Ở Bash, nháy đơn giữ ký tự `$` trong nested class name. PowerShell cũng có thể dùng nháy đơn. `javap` là **disassembly tĩnh**, không phải log lớp đã nạp trong JVM đang chạy.

Quan sát:

- Method `<clinit>` nếu class có logic static initialization cần thực thi.
- `getstatic`/`putstatic` trong các method/initializer.
- `invokestatic` cho static method calls.
- `new`, `dup`, `invokespecial <init>` khi tạo instance.
- `ConstantValue` attribute của compile-time constant field.
- Các method constructor `<init>` khác hoàn toàn `<clinit>`.

**Dùng `javap` để đối chiếu tại sao một dòng Java được biên dịch ra hành vi ấy**, không dùng constant-pool reference như chứng cứ tự động class đã initialized.

## 11. Quy trình chuẩn để giải bài dự đoán output

Khi gặp một đoạn code 10–100 dòng gồm nhiều lớp, hãy làm theo **từng sự kiện runtime**:

### Bước 1 — Liệt kê những lớp/interface liên quan và dòng in

Viết một bảng:

| Runtime type | Static fields/blocks in gì? | Constructor in gì? |
| --- | --- | --- |
| Parent | `PARENT_FIELD`, `PARENT_BLOCK` | `PARENT_CONSTRUCTOR` |
| WithDefault | `DEFAULT_INTERFACE_FIELD` | Interface không có constructor instance |
| Plain | `PLAIN_INTERFACE_FIELD` | Interface không có constructor |
| Child | `CHILD_FIELD`, `CHILD_BLOCK` | `CHILD_CONSTRUCTOR` |
| Constants | `CONSTANTS_BLOCK`, `CONSTANTS_COMPUTED` | Không có instance trong bài |

Không dùng thứ tự khai báo file làm thứ tự initialization toàn cục. Các kiểu được initialized theo **hành động thực thi**, với ngoại lệ về superclass/default method superinterfaces.

### Bước 2 — Đánh dấu active-use points

Ví dụ:

~~~text
Child.class.getSimpleName() → class literal, NO forced Child init
Constants.MAGIC            → compile-time constant, NO forced Constants init
new Child()                → initialize Parent / WithDefault / Child
Plain.TOKEN                → initialize Plain
Child.ping()               → Child already initialized
Constants.NON_CONSTANT     → initialize Constants
~~~

Với mỗi active use, kiểm tra loại đã initialized chưa.

### Bước 3 — Dùng trạng thái mỗi runtime class

Mô hình thực dụng:

~~~text
UNINITIALIZED
    |
    v
INITIALIZATION_IN_PROGRESS(thread X)
    |
    +--- success ---> INITIALIZED
    |
    +--- abrupt ---> ERRONEOUS
~~~

Nghĩa là **một lần initialization thành công**; các active uses sau không chạy lại initializer. Khi initialization lỗi, class có thể ở trạng thái erroneous và không thể tự retry initialization trong cùng runtime identity.

### Bước 4 — Triển khai dependency order

Trước khi chạy static logic của Child:

1. Khởi tạo superclass Parent nếu chưa initialized.
2. Theo quy tắc hiện đại, khởi tạo các superinterfaces cần thiết có default methods.
3. Thực thi static fields và static blocks của Child theo thứ tự source.
4. Khi `new Child()`, sau class initialization còn tới **constructor chain**.

Nếu superclass hoặc superinterface initialization thất bại, có thể không chạy được static logic của subclass, và stack trace/exception thay thế đường output bình thường.

### Bước 5 — Tính giá trị field cẩn thận

- Primitive fields có default 0/false; reference fields có default null **trong preparation**.
- Các compile-time constants có quy tắc `ConstantValue`/inline khác.
- Khi circular initialization, truy cập field chưa gán có thể thấy default.
- Static field initializer có side effect được thực thi theo thứ tự source.
- Một field mutable có thể bị thay đổi sau initialization; không mặc định giá trị mãi như ban đầu.

### Bước 6 — In kết quả theo đường thực thi, không đoán từ load log

Đối với mỗi `println`, điền chính xác giá trị biểu thức và các side effects xảy ra **trước khi println hoàn tất**. Ví dụ:

~~~java
System.out.println("RESULT=" + Feature.compute());
~~~

Java phải đánh giá biểu thức nối chuỗi, trong đó **gọi `Feature.compute()`** trước khi `println` in dòng kết quả. Nếu Feature initialization in thông báo, dòng ấy sẽ xuất hiện **trước** `RESULT=...`.

Đây là nguyên nhân của các dòng init log “chen vào” giữa hai dòng in của main.

### Bước 7 — Phân biệt stdout/stderr, threads và VM logging

Nếu static initializer chạy trên nhiều thread hoặc có `System.err.println`, thứ tự hiển thị khi gộp streams có thể phụ thuộc scheduling và logger. Trong các bài ví dụ trên, chỉ có main thread và tất cả log ứng dụng đều dùng `System.out.println`, nên thứ tự là cố định.

Class-load logging thường ghi rất nhiều dòng độc lập; muốn lấy “stdout chính xác của source” hãy chạy **không bật VM log** hoặc tách VM log vào file.

## 12. Một số bẫy thường gặp

### 12.1. “JVM load rồi nên static initializer đã chạy”

**Sai.** Loading và initialization là những pha khác nhau. `Feature.class` hoặc `Class.forName(name,false,loader)` minh họa rõ: JVM có thể biết class trước khi static block in.

### 12.2. “Thấy Sub.FIELD thì Sub chắc chắn initialized”

**Sai.** Field có thể được **khai báo trong superclass**, hoặc là compile-time constant được inline. Xác định declaring class/interface và cách field được sử dụng trước khi dự đoán.

### 12.3. “Mọi interface được initialized trước implementing class”

**Sai.** Có quy tắc đặc biệt dành cho superinterfaces khai báo **default methods**, không phải khẳng định mọi interface đều initialized như superclass.

### 12.4. “Cứ có `static final` là không in initializer”

**Sai.** Chỉ những *constant variables* theo định nghĩa Java mới có ngoại lệ đọc không kích hoạt initialization. `static final Integer` hoặc `static final int` gán từ lời gọi method không phải trường hợp đó.

### 12.5. “Static initializer luôn chạy đúng một lần trong cả hệ điều hành”

**Sai.** Trạng thái initialization gắn với **runtime class identity**, vốn chịu ảnh hưởng defining class loader. Hai loader độc lập có thể define cùng binary name và initialize mỗi lớp một lần. Khởi chạy process JVM khác cũng có lifecycle riêng.

### 12.6. “Một lần `<clinit>` lỗi, lần sau JVM tự retry”

**Sai.** Nếu initialization thất bại, class đi vào trạng thái erroneous theo quy tắc; lần active use sau có thể nhận `NoClassDefFoundError` (với cause liên quan) thay vì thử chạy static initializer mới.

### 12.7. “Class loader log hiển thị cùng thứ tự trên mọi máy”

**Sai.** Tải/resolve có thể sớm, phụ thuộc VM, shared class data, framework, agents và scheduling. Hãy ưu tiên quan hệ **active use/initialization theo đặc tả** để dự đoán những dòng code ứng dụng.

### 12.8. “Constructor chạy trước static block của subclass”

**Sai** với lần tạo instance đầu tiên thông thường: class cần initialized trước; constructor chain diễn ra trong quá trình tạo object sau đó.

## 13. Khi initialization thất bại thì log và lần sử dụng sau ra sao?

Xét code minh họa:

~~~java
class Unstable {
    static int port = Integer.parseInt("not-a-number");
    static {
        System.out.println("NEVER_REACHED");
    }
}
~~~

Đây là một lớp minh họa **không có main**. Nếu chương trình active-use Unstable và bước `parseInt` ném `NumberFormatException`, static block ở phía sau **không chạy**, nên `NEVER_REACHED` không được in. JVM hoàn tất class initialization một cách bất thường, thường bao bọc exception phù hợp vào `ExceptionInInitializerError` khi cần theo JVMS. Khi tiếp tục cố sử dụng **cùng runtime class** lần sau, có thể nhận `NoClassDefFoundError: Could not initialize class ...`.

Lưu ý:

- Lỗi ném từ static initializer không tự có nghĩa JVM kết thúc ngay; thread và caller có thể catch Throwable phù hợp, nhưng đây là lỗi nghiêm trọng không nên che giấu.
- Một `Error` phát sinh trong initializer có thể được xử lý theo quy tắc khác với exception thông thường; không khẳng định mọi Throwable luôn được bọc giống nhau.
- Không gọi thủ công `<clinit>` để “thử lại”. Không có API Java hợp lệ nhằm reset initialization state của cùng class.
- Để tái thử logic nghiệp vụ có thể lỗi, hãy chuyển nó thành **method initialization chủ động** có trạng thái/retry rõ ràng thay vì phụ thuộc static initializer tự chạy lại.
- Nếu tạo một class mới cùng binary name bằng **loader phiên khác**, đó là một runtime identity mới, nhưng lại kéo theo bài toán dependency/class-loader boundaries riêng.

Đối với môi trường production, hạn chế I/O không kiểm soát, network call blocking và dependency vòng ở static initialization. Dùng logging và chẩn đoán cause đầu tiên; thông báo cuối của `NoClassDefFoundError` đôi khi không chỉ ra nguyên nhân sâu nhất.

## 14. Class initialization và nhiều thread

JVMS quy định quá trình class/interface initialization được **đồng bộ** cho mỗi runtime class. Khi thread T1 đang initialize A và T2 cùng cần A, T2 có thể phải chờ A hoàn tất; không có hai thread cùng thực thi initializer của **chính cùng runtime class** một cách độc lập.

Tuy nhiên:

- Các initializer của **hai class khác nhau** có thể chạy trên các thread khác nhau và interleave dòng log.
- Nếu A initializer chờ B và B initializer chờ A qua các thread, **class initialization deadlock** có thể xảy ra.
- `System.out.println` trên nhiều thread không cung cấp quy tắc “theo thứ tự source toàn chương trình”; chỉ có thứ tự theo từng thread và các quan hệ đồng bộ cụ thể.
- Trong bài dự đoán stdout có threads, phải xem happens-before, locks, join, thread scheduling và mối quan hệ giữa các initializer; không gán một thứ tự tuyệt đối khi đặc tả không bảo đảm.

Ví dụ:

~~~text
Thread 1: cần initialize A → trong A.<clinit> chờ B
Thread 2: cần initialize B → trong B.<clinit> chờ A

Hai thread có thể bị mắc kẹt nếu giữ initialization locks
và mỗi bên cần class mà bên kia đang initialize.
~~~

Đây là sơ đồ điều kiện, **không phải một output Java cố định**. Dự đoán chính xác cần mã Java đầy đủ, cách đồng bộ và giả định scheduler.

## 15. Loading trace, class identity và “một lần” khi có nhiều loaders

Hai class có **cùng binary name** nhưng được **hai defining class loaders khác nhau** định nghĩa là hai runtime classes. Mỗi class có initialization state và static fields riêng. Vì vậy VM log có thể ghi cùng tên class xuất hiện hai lần với loader metadata khác nhau; hai static initializers có thể in cùng nhãn nhưng mỗi cái thuộc một runtime class.

Điều này thường xuất hiện ở:

- Plugin/hot-reload với child loader mỗi phiên.
- Application servers có deployment classloader riêng.
- Test runners hoặc framework cô lập dependencies.
- Runtime-generated classes/proxies.

Khi muốn dự đoán stdout của app có nhiều loader, thêm **cột defining loader** vào bảng theo dõi. “Tên class đã initialized” không đủ nếu có hai runtime identities.

Cũng đừng tưởng `Class.getClassLoader()` trả một object Java cho bootstrap loader trong mọi trường hợp: đối với class bootstrap, API thường trả `null` theo hợp đồng.

## 16. Luyện tập dự đoán nhanh

### Câu 1 — Class literal

~~~java
Class<?> c = Unused.class;
System.out.println("KNOWN");
~~~

Giả sử `Unused` có `static { System.out.println("INIT"); }`. Chỉ hai câu trên có đảm bảo in `INIT` không?

**Không.** Class literal không tự yêu cầu initialization; dòng có thể được đảm bảo từ code hiển thị là `KNOWN` (bỏ qua log JVM riêng và tác động ngoài ví dụ).

### Câu 2 — Static final

~~~java
static final int A = 3;
static final Integer B = 3;
~~~

Đọc trường nào có thể không initialize owner? **A** là constant variable nếu declaration phù hợp; B là reference và không phải compile-time constant variable.

### Câu 3 — Parent/Child

Gọi `new Child()` lần đầu với Parent static block in `P`, Child static block in `C`, constructor Parent in `p`, constructor Child in `c`. Thứ tự?

~~~text
P
C
p
c
~~~

Nếu gọi lần hai sau khi cả hai initialized thành công, chỉ thêm `p`, `c`.

### Câu 4 — Interface field

`interface I { int X = compute(); }`, trong đó compute in `I_INIT` và trả 5. Nếu chỉ tham chiếu `I.class`, có đảm bảo `I_INIT` không? **Không.** Nếu đọc `I.X` khi I chưa initialized, **có**.

### Câu 5 — Circular fields

Trong bài CircularInitDemo, vì sao `B_INIT=1` in trước `A_INIT=2`? Vì A initializer **đọc B.value** trước khi A field được gán, kéo B initialization; B đọc A default 0.

### Câu 6 — Logging

`[class,load] demo.Feature` có đủ chứng minh đã chạy Feature static block không? **Không**. Muốn chứng minh thực thi block, xem log/dấu vết bên trong initializer hoặc class-init event theo đúng ý nghĩa của nó.

### Câu 7 — Read before println

Vì sao trong `System.out.println("VALUE=" + Target.NON_CONSTANT)` dòng `TARGET_INIT` có thể xuất hiện **trước** dòng `VALUE=...`? Vì Java phải **đánh giá biểu thức đối số println** (gồm getstatic và class initialization nếu cần) trước khi gọi println để in chuỗi.

### Câu 8 — Hai class loaders

Một plugin class cùng binary name được defining loader A và B định nghĩa. Static initializer của chúng có bị giới hạn chỉ chạy một lần tổng cộng không? **Không**; mỗi runtime class có initialization state riêng.

### Câu 9 — Initialization lỗi

Một class bị lỗi trong `<clinit>`. Lần active use sau có bảo đảm chạy lại `<clinit>` không? **Không**; trạng thái erroneous thường khiến JVM phát lỗi khi dùng tiếp.

### Câu 10 — JVM log và stdout

Có thể khẳng định thứ tự **mọi** dòng `[class,load]` trên JDK 17 và 21 sẽ hoàn toàn giống nhau không? **Không**. Chỉ dự đoán những quan hệ đã được spec hoặc source bảo đảm.

## 17. Nguồn đọc sâu

- **Java Language Specification 17, §12.4.1 – When Initialization Occurs:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.1
- **JLS 17, §12.4.2 – Detailed Initialization Procedure:** https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4.2
- **Java Virtual Machine Specification 17, §5.3 – Loading:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- **JVMS 17, §5.4 – Linking:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4
- **JVMS 17, §5.5 – Initialization:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5
- **JVMS 17, §4.7.2 – ConstantValue:** https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.2
- **JDK 17 java launcher tool:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- **JDK 17 javap:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html

**Kết luận:** muốn dự đoán output trong bài JVM class initialization, hãy tách **class-load log** khỏi **initializer stdout**; đánh dấu chính xác **active use**, xét **superclass và superinterfaces khai báo default methods**, thực thi **static fields/blocks theo thứ tự văn bản**, phân biệt **constructor với `<clinit>`**, và nhớ mỗi runtime class chỉ initialized thành công một lần. Với **constant variables**, **class literal**, **`Class.forName(false)`** và **circular initialization**, không được áp dụng máy móc nguyên tắc “nhắc đến tên class là in static block”.


</details>

- [Quay lại đầu trang](#back-to-top)
