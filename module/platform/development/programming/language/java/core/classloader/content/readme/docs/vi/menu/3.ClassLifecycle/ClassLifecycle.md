<a id="back-to-top"></a>

# Vòng đời lớp

## Menu
- [Thời điểm JVM cần tạo và nạp một lớp hoặc interface](#class-lifecycle-001)
- [Tìm biểu diễn nhị phân và tạo Class trong JVM](#class-lifecycle-002)
- [Nạp các lớp cha và interface cha cần thiết](#class-lifecycle-003)
- [Verification: kiểm tra cấu trúc và tính hợp lệ của bytecode](#class-lifecycle-004)
- [Preparation: cấp phát và thiết lập giá trị mặc định cho static field](#class-lifecycle-005)
- [Resolution: phân giải tham chiếu ký hiệu](#class-lifecycle-006)
- [Phân giải sớm, phân giải muộn và thời điểm xuất hiện lỗi](#class-lifecycle-007)
- [Phân biệt liên kết lớp với khởi tạo lớp](#class-lifecycle-008)
- [Khởi tạo lớp trước khi thực thi các thao tác yêu cầu](#class-lifecycle-009)
- [Vòng đời lớp khác vòng đời đối tượng như thế nào](#class-lifecycle-010)

## <a id="class-lifecycle-001">Thời điểm JVM cần tạo và nạp một lớp hoặc interface</a>

<details>
<summary>Xem chi tiết</summary>

# Thời điểm JVM cần tạo và nạp một lớp hoặc interface

Khi đọc hoặc thực thi chương trình Java, JVM không nhất thiết nạp mọi lớp và interface xuất hiện trong mã nguồn ngay từ đầu. JVM tạo các **định nghĩa kiểu ở runtime** theo nhu cầu thực thi, theo quá trình liên kết, theo yêu cầu của thư viện như reflection, và có thể nạp một số kiểu sớm hơn để tối ưu. Điều thiết yếu là hiểu **khi nào JVM cần có một kiểu** và **khi nào kiểu đó thực sự được khởi tạo**.

Bài này giải thích chính xác hai từ trong đề:

- **Tạo một lớp/interface ở JVM** (*class/interface creation*, theo JVM Specification): dựng biểu diễn runtime của một **kiểu** đã được xác định bằng tên và defining class loader. Đây **không phải** việc gọi `new` để tạo một đối tượng ứng dụng.
- **Nạp lớp/interface** (*class/interface loading*): dùng cơ chế class loader tìm biểu diễn nhị phân (thường từ tệp `.class`, JAR, module hoặc nguồn bytecode khác), rồi nhờ JVM tạo định nghĩa runtime cho kiểu đó. Với **array class**, JVM có quy tắc tạo đặc biệt, không cần tệp `.class` của array.

Ví dụ, JVM có thể đã tạo định nghĩa kiểu `com.acme.Order` và trả về `Order.class` mà vẫn chưa chạy bất kỳ `static` initializer nào của `Order`, và chưa hề có một đối tượng `new Order()` nào.

## 1. Bốn thời điểm phải tách biệt

```text
Chương trình tham chiếu một kiểu T
          |
          v
    Loading / Creation
  tìm/định nghĩa T trong JVM
          |
          v
         Linking
  verification -> preparation -> resolution (*)
          |
          v
     Initialization (khi có trigger)
  chạy khởi tạo static của T
          |
          v
    Tạo instance (chỉ khi cần)
  new T(...) / cơ chế tạo đối tượng tương ứng

(*) Resolution có thể làm dần khi các tham chiếu được sử dụng.
    Đây là sơ đồ quan hệ, không phải lịch bắt buộc mọi bước đều
    xuất hiện liên tục ngay trong một lần thực thi.
```

| Khái niệm | JVM cần làm gì? | Kết quả quan sát được |
|---|---|---|
| **Loading / creation** | Nhận diện kiểu, tìm bytecode nếu là kiểu thông thường và dựng biểu diễn kiểu runtime | Có đại diện `Class<?>`; có thể xảy ra khi JVM giải quyết một tham chiếu kiểu |
| **Verification** | Kiểm tra ràng buộc hợp lệ của biểu diễn bytecode | Bytecode sai có thể dẫn đến `VerifyError` |
| **Preparation** | Chuẩn bị vùng chứa trường `static` với giá trị mặc định hoặc giá trị theo `ConstantValue` khi thích hợp | Chưa đồng nghĩa chạy mọi biểu thức khởi tạo `static` |
| **Resolution** | Chuyển symbolic reference thành tham chiếu runtime cụ thể, áp dụng kiểm tra kiểu/quyền truy cập | Lúc dùng tham chiếu có thể thấy `NoClassDefFoundError`, `NoSuchMethodError`, `IllegalAccessError`… |
| **Initialization** | Thực hiện logic khởi tạo `static` (phương thức khởi tạo lớp `<clinit>` nếu có) | Log từ `static { ... }` hoặc phương thức được gọi khi gán trường `static` |
| **Instance creation** | Cấp phát/khởi tạo một đối tượng của lớp cụ thể | Constructor, trạng thái mới của từng instance |

**Quy tắc cần nhớ:** *Đã nạp* không có nghĩa *đã khởi tạo lớp*; *đã khởi tạo lớp* không có nghĩa *đã tạo một instance*; và *tạo instance* không đồng nghĩa *nạp lại lớp từ bytecode*.

### Ngữ nghĩa “cần” khác với “chắc chắn mới bắt đầu nạp ngay lúc đó”

Có hai mức cần phân biệt:

1. **Yêu cầu của ngữ nghĩa chương trình:** Khi JVM phải sử dụng thông tin của kiểu T để thực thi hợp lệ (ví dụ giải quyết tham chiếu phương thức/trường hoặc kiểm tra kiểu), JVM phải có được định nghĩa kiểu T tương ứng trước khi thao tác đó hoàn tất.
2. **Lịch thực thi do JVM lựa chọn:** JVM có thể chuẩn bị trước, nạp sớm kiểu phụ thuộc, hoặc trì hoãn việc resolution. Vì thế không thể suy ngược từ một dòng Java rằng **chính xác** trong dòng đó JVM mới đọc `T.class` lần đầu.

Ngược lại, quy tắc **khởi tạo lớp/interface** chặt chẽ hơn: Java quy định rõ các trường hợp sử dụng chủ động dẫn tới initialization. Khi muốn chứng minh thứ tự, hãy quan sát `static` initializer; khi muốn xem hoạt động tải thực tế, hãy dùng công cụ log tải lớp của JVM.

## 2. JVM bắt đầu tạo/nạp lớp từ đâu?

Theo JVM Specification, khi khởi động JVM chọn một **initial class hoặc interface**, tạo và liên kết kiểu ban đầu đó, khởi tạo theo các quy tắc liên quan, rồi chạy entry point thích hợp (ví dụ `main`). Trong khi mã thực thi, những tham chiếu tới các kiểu khác tiếp tục dẫn tới công việc nạp hoặc liên kết.

Một định nghĩa kiểu T có thể được tạo từ các nguyên nhân chính sau:

- **Tham chiếu ký hiệu từ một lớp/interface khác:** Ví dụ bytecode của lớp D chứa symbolic reference tới T trong run-time constant pool. Khi JVM cần xử lý tham chiếu ấy trong quá trình thực thi hoặc linking/resolution, JVM có thể phải tạo/nạp T.
- **API thư viện yêu cầu tra cứu kiểu:** Ví dụ `Class.forName(...)`, `ClassLoader.loadClass(...)`, framework reflection, service/provider loading hoặc mã plugin tự xây dựng.
- **Tạo lớp mảng:** JVM tạo array class theo quy tắc riêng khi kiểu mảng tương ứng cần hiện diện; kiểu component có thể cần nạp, nhưng **không vì thế mà tự động initialize lớp component**.
- **Kiểu được định nghĩa từ nguồn bytecode động:** Custom loader dùng API `defineClass` hoặc cơ chế định nghĩa lớp hợp lệ khác. Đây vẫn là việc tạo **định nghĩa kiểu**, độc lập với việc gọi constructor instance.

Điểm xuất phát quan trọng không phải là “tệp nào đang nằm trên đĩa”, mà là **một symbolic reference hoặc lời gọi API yêu cầu kiểu nào trong không gian của loader nào**.

### Vai trò của symbolic reference và constant pool

Tệp `.class` không nhúng sẵn con trỏ đối tượng Java tới mọi lớp sử dụng. Nó chứa tên và symbolic references, ví dụ tham chiếu tới kiểu, field, method hoặc một entry liên quan đến lời gọi phương thức.

```text
Bytecode của Caller
  constant pool:
    #... Class com/acme/Service
    #... Methodref com/acme/Service.run:()V
               |
               | resolution khi cần
               v
  Kiểu com.acme.Service đã được định nghĩa bởi loader phù hợp
               |
               v
  Tham chiếu tới method/field runtime có thể sử dụng
```

JVM phải bảo đảm ràng buộc type safety, verification và access control. Khi không có định nghĩa đúng của T, thao tác liên quan có thể thất bại ở thời điểm JVM cố sử dụng kiểu đó. Không nên mặc định tất cả symbolic references đều được resolve tại một thời điểm duy nhất.

## 3. Những thao tác Java có thể dẫn tới tải hoặc giải quyết kiểu

Các ví dụ sau xem xét lớp `Service` là một kiểu ứng dụng bình thường và giả định nó chưa được nạp bởi loader cần thiết.

### 3.1. Sử dụng `new`

```java
Service service = new Service();
```

Lệnh `new` ở bytecode phải giải quyết kiểu `Service` và cần thông tin kiểu để tạo instance. Nếu chưa có định nghĩa kiểu này, JVM sẽ nạp/tạo nó qua cơ chế loader. Trước khi instance được khởi tạo xong, `Service` cần được initialized (nếu chưa); sau đó constructor được gọi.

**Không suy ra** mỗi lần `new Service()` đều nạp lại `Service`. Khi định nghĩa lớp đã tồn tại và initialized, các lần `new` kế tiếp tạo đối tượng mới dùng chung định nghĩa runtime đó.

### 3.2. Đọc/ghi trường `static` và gọi phương thức `static`

```java
int current = Service.counter;
Service.counter = 10;
Service.refresh();
```

Để giải quyết trường hoặc phương thức, JVM cần định nghĩa của lớp/interface thực sự khai báo thành viên đó. Khi việc sử dụng là một trigger initialization theo quy tắc Java, JVM phải khởi tạo kiểu khai báo thành viên trước thao tác. Với truy cập `static`, sự khởi tạo thuộc về **kiểu khai báo thành viên đã được resolve**, không nhất thiết là kiểu có tên ở vế trái trong mã nguồn.

**Ngoại lệ thường bị bỏ qua:** đọc *compile-time constant variable* không cần initialize kiểu khai báo. Ví dụ `static final int PORT = 8080;` có thể được trình biên dịch chèn thẳng giá trị vào bytecode của caller. Nhưng `static final int PORT = readPort();` **không** phải constant variable và việc đọc trực tiếp trường này là sử dụng chủ động.

### 3.3. Toán tử và kiểm tra kiểu

```java
boolean ok = value instanceof Service;
Service s = (Service) value;
Class<Service> type = Service.class;
```

Những thao tác này có thể đòi hỏi JVM có thông tin runtime về `Service` để thực hiện kiểm tra kiểu hoặc tạo biểu diễn `Class`. **Chúng không tự động yêu cầu initialize `Service`.**

Với `instanceof` và ép kiểu, một số trường hợp được tối ưu hoặc tham chiếu đã được giải quyết từ trước, nên không nên tuyên bố **mỗi lần thực thi** phép kiểm tra đều tạo/nạp lớp mới. Nếu lớp đích không hiện diện hoặc không giải quyết được, thao tác liên quan có thể gặp lỗi liên kết khi cần.

### 3.4. Reflection và yêu cầu nạp theo tên

```java
ClassLoader loader = Thread.currentThread().getContextClassLoader();

Class<?> a = loader.loadClass("com.acme.Service");
Class<?> b = Class.forName("com.acme.Service", false, loader);
Class<?> c = Class.forName("com.acme.Service");
```

Đây là **mảnh minh họa API**, không phải chương trình độc lập; nó chỉ thành công nếu loader tương ứng thực sự tìm được tên nhị phân đó.

- `loader.loadClass(name)` yêu cầu loader tìm/nạp kiểu; theo hành vi mặc định nó **không yêu cầu initialize** lớp. Gọi loader này cũng không bảo đảm nó là defining loader nếu có cơ chế ủy quyền.
- `Class.forName(name, false, loader)` yêu cầu tìm/nạp và liên kết kiểu nhưng **không yêu cầu initialize**.
- `Class.forName(name)` với phiên bản một tham số thường sử dụng loader của caller và **yêu cầu initialize** kiểu (theo tài liệu API), nếu chưa khởi tạo.

Không phương thức nào trong ba dòng trên tự tạo instance `Service`. Phải tách biệt `Class<?>` được trả về với `new Service()` hoặc thao tác constructor bằng reflection.

### 3.5. Loading bởi framework, plugin, dependency injection

Một framework có thể đọc tên lớp từ cấu hình, tra cứu annotation, mở metadata và gọi `loadClass` hoặc `Class.forName`. Một plugin loader cũng có thể định nghĩa lớp trực tiếp từ bytecode của JAR.

Điểm cần quan sát:

- **Metadata inspection** có thể chỉ yêu cầu định nghĩa kiểu mà chưa initialize nó.
- **Bean/service instantiation** có thể làm lớp được initialize và tạo instance.
- **Custom class loader** quyết định không gian tên: cùng binary name nhưng hai defining loader khác nhau có thể dẫn đến hai kiểu runtime không tương thích nhau.

Hãy đọc API cụ thể của framework để biết nó có chủ động initialize lớp hay không; không nên suy đoán chỉ vì framework đã biết tên lớp.

## 4. Array class là trường hợp tạo kiểu đặc biệt

Ví dụ:

```java
Service[] services = new Service[3];
Class<?> arrayType = Service[].class;
Class<?> componentType = arrayType.getComponentType();
```

JVM có định nghĩa runtime riêng cho kiểu mảng `Service[]`. **Array class do JVM tạo**, không có một tệp `Service[].class` trên đĩa để loader đọc.

Nếu component là kiểu tham chiếu, JVM cần có kiểu component tương ứng và gắn defining loader của array class với defining loader của component. Nếu component là primitive, array class gắn với bootstrap loader theo quy định JVM. Các mảng có số chiều khác nhau là các kiểu khác nhau: `Service[]` và `Service[][]` không có cùng `Class`.

**Tạo mảng đối tượng không tạo các phần tử đối tượng và không tự initialize component class.** `new Service[3]` tạo một đối tượng mảng có ba phần tử `null`; nó không tương đương chạy `new Service()` ba lần.

Đối với mảng, phân biệt:

| Thao tác | Tạo instance của `Service`? | Có thể tạo/đụng tới kiểu mảng? | Bắt buộc initialize `Service` chỉ vì thao tác đó? |
|---|---|---|---|
| `Service[].class` | Không | Có | Không |
| `new Service[3]` | Không | Có | Không |
| `services[0] = new Service()` | Có | Mảng đã tồn tại | Có, nếu chưa initialized |
| `int[].class` | Không áp dụng | Có, array class của primitive | Không áp dụng |

## 5. Thí nghiệm Java 17+: thấy rõ thời điểm initialization

Vì **loading có thể xảy ra sớm**, một log trong `static` initializer chỉ cho thấy **initialization**, không phải thời điểm chính xác JVM đọc tệp `.class`. Chương trình dưới đây cố ý tách các hành vi ấy.

Tạo tệp **`LoadingMomentsDemo.java`**:

```java
public class LoadingMomentsDemo {
    static class Service {
        static final int VERSION = 3; // compile-time constant
        static final int PORT = port();

        static {
            System.out.println("Service.<clinit> block");
        }

        static int port() {
            System.out.println("Service.port()");
            return 8080;
        }

        Service() {
            System.out.println("Service constructor");
        }
    }

    interface Feature {
        int VALUE = trace(); // không phải compile-time constant

        static int trace() {
            System.out.println("Feature initialized");
            return 7;
        }
    }

    public static void main(String[] args) throws ClassNotFoundException {
        Class<?> mirror = Service.class;
        System.out.println("Class literal ready");

        ClassLoader loader = LoadingMomentsDemo.class.getClassLoader();
        Class<?> loaded = loader.loadClass(mirror.getName());
        System.out.println("Same Class: " + (mirror == loaded));

        System.out.println("Constant: " + Service.VERSION);

        Service[] slots = new Service[2];
        System.out.println("Array length: " + slots.length);

        Class.forName(mirror.getName(), false, mirror.getClassLoader());
        System.out.println("forName(false) returned");

        System.out.println("Port: " + Service.PORT);
        new Service();

        System.out.println("Feature value: " + Feature.VALUE);
    }
}
```

Biên dịch/chạy:

```bash
javac LoadingMomentsDemo.java
java LoadingMomentsDemo
```

**Stdout chuẩn:**

```text
Class literal ready
Same Class: true
Constant: 3
Array length: 2
forName(false) returned
Service.port()
Service.<clinit> block
Port: 8080
Service constructor
Feature initialized
Feature value: 7
```

**Giải thích từng mốc:**

1. `Service.class` cho phép lấy `Class<?>`. Chưa có output của `Service`: literal không tự buộc chạy initializer.
2. `loader.loadClass(mirror.getName())` dùng loader thích hợp tìm cùng định nghĩa lớp; kết quả mirror bằng nhau. Mã này không yêu cầu initialize `Service`.
3. `Service.VERSION` là constant variable (`static final int` gán từ constant expression). Caller có thể dùng trực tiếp giá trị `3`, không kích hoạt initializer của `Service`.
4. `new Service[2]` tạo một **array instance** có hai slot `null`. JVM có kiểu mảng và thông tin component, nhưng vẫn chưa phải khởi tạo `Service`.
5. `Class.forName(name, false, loader)` trả về mà không yêu cầu initialize `Service`.
6. **Đọc `Service.PORT` lần đầu** là sử dụng chủ động một trường `static` không phải constant variable. JVM initialize `Service`: chạy `port()` trước rồi đến `static` block theo thứ tự mã nguồn; vì vậy hai dòng log xuất hiện trước `Port: 8080`.
7. `new Service()` sau đó chạy constructor và in `Service constructor`, nhưng không chạy lại initializer `static`.
8. `Feature.VALUE` cũng không phải constant variable. Lần đọc trường kích hoạt initialize interface `Feature`: `trace()` in một dòng rồi giá trị `7` được in.

**Vì sao “Same Class: true” đáng tin?** Cả `Service` và `LoadingMomentsDemo` được định nghĩa từ cùng đơn vị mã ứng dụng do cơ chế loader tương ứng, và loader này trả đúng cùng `Class<?>` mà `Service.class` biểu diễn. Chương trình không dựa vào tên class loader phụ thuộc JDK.

Chương trình minh họa **thời điểm JVM bắt buộc initialize** lớp/interface; nó không cố khẳng định bytecode `Service` được đọc lần đầu đúng tại `Service.class`.

### Quan sát loading thật trên HotSpot

Với HotSpot JVM/JDK 17+, có thể dùng Unified Logging:

```bash
java -Xlog:class+load=info LoadingMomentsDemo
```

Các dòng log gắn với tên kiểu và nguồn nạp giúp tìm lúc JVM **ghi nhận sự kiện nạp**. Output log này phụ thuộc JDK, JVM, cấu hình module/classpath và runtime; không thể coi một thứ tự nạp phụ thuộc triển khai là hợp đồng của chương trình.

Có thể dùng thêm `javap -c -v LoadingMomentsDemo` để tìm những symbolic references và bytecode như `ldc`, `getstatic`, `anewarray`, `new`, `invokestatic`. Đây là công cụ phân tích bytecode, không thay thế việc quan sát JVM runtime.

## 6. Khác biệt quan trọng: loading không phải initialization

Một bảng quyết định nhanh:

| Cú pháp/thao tác | Có thể cần nạp/liên kết kiểu T? | Có bắt buộc initialize T chỉ vì thao tác này? | Có tạo instance T? |
|---|---|---|---|
| `T.class` | Có | **Không** | Không |
| `Class.forName("pkg.T", false, loader)` | Có, chủ động yêu cầu lookup/loading/linking | **Không yêu cầu** | Không |
| `loader.loadClass("pkg.T")` | Có, chủ động yêu cầu lookup/loading | **Không yêu cầu** | Không |
| `Class.forName("pkg.T")` | Có | **Có**, theo overload này nếu chưa initialized | Không |
| `new T()` với T là class cụ thể | Có | **Có**, trước khi tạo đối tượng hoàn tất | **Có** |
| `T.staticMethod()` với method do T khai báo | Có | **Có**, nếu chưa initialized | Không |
| `T.nonConstantStaticField` đọc/ghi, khai báo ở T | Có | **Có**, nếu chưa initialized | Không |
| `T.COMPILE_TIME_CONSTANT` | Không nhất thiết | **Không** | Không |
| `new T[10]` | Có thể cần component type và array type | **Không** initialize component T | Không tạo T; tạo array instance |
| `value instanceof T` hoặc cast `(T) value` | Có thể cần resolve T | **Không** | Không |

**Lưu ý về các ô “Có”:** bảng diễn tả **yêu cầu khi thao tác cần kiểu**, không bảo đảm mỗi lần thực thi sẽ sinh một sự kiện đọc bytecode mới. Kiểu có thể đã nạp, liên kết hoặc khởi tạo trước đó.

**Lưu ý về `Class.forName`:** mô tả trên áp dụng cho các overload được ghi rõ trong bảng; API còn có overload dựa trên module với semantics riêng. Luôn nhìn đúng chữ ký phương thức.

## 7. Các ngộ nhận thường gặp

1. **“Java nạp mọi class trên classpath khi chạy `main`.”** Không đúng: classpath chứa các ứng viên có thể nạp; nhu cầu thực thi/liên kết, thư viện và chiến lược JVM quyết định định nghĩa nào xuất hiện.
2. **“Đọc một dòng `T.class` chắc chắn làm chạy `static` block của T.”** Sai: lấy class literal tự nó không kích hoạt initialization.
3. **“JVM chỉ có thể tải lớp đúng tại lần dùng đầu tiên.”** Sai: JVM được phép chọn tải/liên kết sớm nếu bảo toàn ngữ nghĩa quan sát được.
4. **“Nạp class luôn tạo object T.”** Sai: loading tạo biểu diễn kiểu runtime; instance của T là câu chuyện khác.
5. **“`ClassLoader.loadClass` và `Class.forName(String)` giống nhau.”** Sai về yêu cầu initialization theo các overload được đề cập.
6. **“Tạo `new T[5]` tương đương năm lần `new T()`.”** Sai: array có các slot mặc định `null`; chưa có instance T nào được tạo.
7. **“Interface không bao giờ initialize.”** Sai: truy cập trường không phải constant variable hoặc gọi static method có thể kích hoạt initialization của interface.
8. **“Implements một interface là luôn initialize interface ấy.”** Sai: loading/liên kết quan hệ kiểu và initialization của interface khác nhau; đặc biệt chú ý quy tắc superinterface có default method khi class initialize.
9. **“Một binary name chỉ có một kiểu trên cả JVM.”** Sai: defining loader là một phần của danh tính kiểu runtime.
10. **“Dòng log từ `static` block cho biết chính xác khi nào class file được đọc.”** Sai: log ấy chỉ phản ánh initialization.

## 8. Tự kiểm tra

**Câu 1.** JVM cần chạy `T.class` trong chương trình, nhưng `T` chưa được initialized. Có bắt buộc gọi `<clinit>` ngay không?

**Đáp án:** Không. JVM có thể phải định nghĩa/đã định nghĩa T để cung cấp `Class<?>`, nhưng chỉ `T.class` không phải trigger khởi tạo T.

**Câu 2.** Tại sao `Class.forName("pkg.Plugin", false, loader)` vẫn hữu ích nếu không tạo instance?

**Đáp án:** Vì nó yêu cầu lookup/loading/linking để biết kiểu `Plugin` tồn tại trong không gian của loader nào; ứng dụng sau đó có thể kiểm tra kiểu, metadata, constructor, hoặc quyết định tạo đối tượng về sau.

**Câu 3.** Nếu `interface I { int A = 10; int B = compute(); ... }`, chỉ đọc `I.A` và `I.B` có kết quả initialization giống nhau không?

**Đáp án:** Không. `I.A` là compile-time constant và có thể inline; `I.B` không phải constant variable nên truy cập trực tiếp nó là trigger initialize I.

**Câu 4.** Mã `new Widget[4]` có bắt buộc gọi constructor `Widget()` bốn lần không?

**Đáp án:** Không. Nó tạo mảng bốn phần tử tham chiếu, mỗi phần tử ban đầu là `null`.

**Câu 5.** Cùng `p.Widget` nhưng hai class loader thật sự định nghĩa hai bản riêng: có phải một kiểu duy nhất không?

**Đáp án:** Không. JVM xét tên nhị phân cùng defining loader để xác định kiểu runtime; hai định nghĩa là hai kiểu riêng.

## 9. Bản đồ quyết định khi đọc một thao tác Java

```text
JVM gặp một tham chiếu đến kiểu T
            |
            v
Kiểu T đã được định nghĩa trong loader cần dùng?
        /             \
      chưa             rồi
       |                |
Tìm biểu diễn nhị phân  Dùng định nghĩa đã có
và định nghĩa T             |
       \                   /
        v                 v
      Kiểm tra/link/resolve phù hợp
                    |
                    v
      Có trigger khởi tạo T không?
             /              \
           không             có
             |                |
     Dùng thông tin kiểu   Initialize T nếu cần
             |                |
             +--------+-------+
                      |
                      v
       Có thực sự tạo instance T không?
                 /          \
               không         có
                |            |
      Không có T object    Tạo đối tượng cụ thể
```

Ba câu hỏi hữu ích khi phân tích runtime là: **JVM cần kiểu nào? Loader nào định nghĩa kiểu đó? Thao tác nào buộc initialization hoặc tạo instance?** Trả lời tách biệt ba câu hỏi sẽ tránh nhầm thời điểm nạp class với thời điểm chạy static initializer hay constructor.

### Đọc thêm từ đặc tả chính thức

- [JVM Specification, 5.2–5.5: Startup; Creation and Loading; Linking; Initialization](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html)
- [Java Language Specification, 12.2–12.5: Loading; Linking; Initialization; Creation of New Class Instances](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-lifecycle-002">Tìm biểu diễn nhị phân và tạo Class trong JVM</a>

<details>
<summary>Xem chi tiết</summary>

# Tìm biểu diễn nhị phân và tạo `Class` trong JVM

## 1. Mục tiêu

Trong vòng đời lớp Java, **nạp lớp (loading)** trả lời hai câu hỏi thực tế:

1. JVM lấy **biểu diễn nhị phân** của lớp hoặc interface từ đâu?
2. Từ biểu diễn đó, JVM tạo **định nghĩa kiểu ở runtime** và đối tượng **java.lang.Class** đại diện cho kiểu như thế nào?

Đây là bước trọng tâm trước khi phân tích sâu các bước **linking** và **initialization**. Việc đã có một đối tượng **Class** **không đồng nghĩa** với việc mã khởi tạo **static** đã được thực thi.

Sau bài này, bạn có thể:
- Giải thích mối quan hệ giữa tên nhị phân, file class, nguồn bytecode và class loader.
- Mô tả cách JVM yêu cầu loader tìm hoặc định nghĩa lớp, cũng như cách nó tạo Class mirror.
- Phân biệt **initiating loader** với **defining loader** và giải thích danh tính của một lớp.
- Hiểu vì sao cùng tên lớp nhưng khác loader có thể không ép kiểu được cho nhau.
- Kiểm chứng bằng Java 17+ và các lệnh **javap**, **jar** và logging của JVM.

## 2. Từ mã nguồn đến biểu diễn nhị phân

Với lớp Java thông thường, chương trình đi qua đường đi sau:

~~~text
src/demo/Widget.java
         │ javac -d out ...
         ▼
out/demo/Widget.class          ┐
hoặc tệp trong một JAR         ├─ nguồn chứa class-file bytes
hoặc bytecode từ nguồn khác    ┘
         │
         │ JVM yêu cầu class loader tìm/định nghĩa lớp
         ▼
JVM đọc biểu diễn nhị phân được cung cấp
         │
         ├─ kiểm tra tên và định nghĩa cần thiết
         ├─ tạo biểu diễn kiểu nội bộ của JVM
         └─ gắn với đối tượng java.lang.Class
         │
         ▼
Class<?> đại diện cho demo.Widget
         │
         ├─ có thể được liên kết theo quy tắc JVM
         └─ được khởi tạo khi các điều kiện initialization xảy ra
~~~

**Biểu diễn nhị phân (binary representation)** là dữ liệu mô tả cấu trúc và mã thực thi của lớp/interface. Trong trường hợp quen thuộc, đó là **định dạng class file của JVM**. Trình biên dịch Java **javac** thường tạo **.class** từ **.java**, nhưng JVM không yêu cầu mọi lớp phải xuất phát từ một tệp **.class** riêng nằm trên ổ đĩa: bytecode cũng có thể được lấy từ JAR, sinh động trong bộ nhớ hoặc nguồn riêng của loader.

Một file **.jar** thông thường là **gói ZIP chứa nhiều entry**, trong đó các entry như **demo/Widget.class** chứa bytecode từng lớp. JAR **không phải** một biểu diễn nhị phân duy nhất của tất cả lớp bên trong.

### 2.1 Những thành phần có trong class file

Class file có cấu trúc theo Java Virtual Machine Specification, chẳng hạn:

| Phần | Vai trò |
| --- | --- |
| Magic number **0xCAFEBABE** | Dấu hiệu định dạng class file. |
| Phiên bản major/minor | Cho biết phiên bản định dạng class file cần hỗ trợ. |
| **constant_pool** | Chứa hằng, tên, kiểu và các **symbolic reference** cần thiết. |
| Access flags, this_class, super_class, interfaces | Mô tả lớp, superclass, interface, các thuộc tính truy cập. |
| Fields và methods | Mô tả thành viên, descriptor và thuộc tính; code của method phù hợp được lưu trong thuộc tính **Code**. |
| Attributes | Siêu dữ liệu như Code, SourceFile, LineNumberTable, annotations, bootstrap methods... tùy class. |

Ví dụ, **demo.Widget** có thể được tham chiếu trong constant pool bằng tên kiểu nội bộ **demo/Widget**. Khi chạy, các tham chiếu ký hiệu này sẽ được xử lý theo quy tắc liên kết; việc có constant pool **không có nghĩa** mọi tham chiếu đã được phân giải ngay khi lớp được nạp.

JVM định nghĩa định dạng class file và các ràng buộc để loader cung cấp bytes hợp lệ. Bytecode hỏng, có tên không khớp hoặc không được JVM đang chạy hỗ trợ có thể dẫn tới lỗi như **ClassFormatError**, **NoClassDefFoundError** hoặc **UnsupportedClassVersionError**, tùy nguyên nhân cụ thể. Những lỗi ấy khác với việc đơn thuần không tìm thấy tài nguyên trên classpath.

### 2.2 Ba dạng tên rất dễ nhầm

Xét lớp khai báo là **package demo; public class Widget**:

| Ngữ cảnh | Tên tương ứng | Công dụng |
| --- | --- | --- |
| **Binary name** | **demo.Widget** | Tên class được truyền cho **ClassLoader.loadClass** hay **Class.forName** với lớp thông thường. |
| **Internal name** trong class file | **demo/Widget** | Dạng dấu **/** trong nhiều cấu trúc class file. |
| **Resource path** thường gặp | **demo/Widget.class** | Đường dẫn entry tìm trong classpath/JAR khi triển khai loader kiểu thông thường. |
| **Descriptor** của tham chiếu tới kiểu | **Ldemo/Widget;** | Cú pháp field/method descriptor. |

Lớp lồng không phải top-level có thể có binary name như **demo.Outer$Inner**. **demo.Outer.Inner** là cách viết tên trong nhiều ngữ cảnh mã nguồn, nhưng không phải binary name của loại nested class thông thường đó.

**Lưu ý:** việc đổi dấu chấm thành dấu **/** rồi thêm **.class** là quy ước tra tài nguyên thường dùng, không phải thuật toán bắt buộc cho mọi loader. Một custom loader có thể đọc bytes từ bộ nhớ, cơ sở dữ liệu, mạng hoặc dữ liệu mã hóa.

## 3. Ai tìm lớp? Class loader và delegation

JVM sử dụng các class loader để đưa định nghĩa lớp vào runtime. Trong ứng dụng Java phổ biến có:

- **Bootstrap loader:** được JVM cung cấp để định nghĩa nhiều lớp nền tảng; **Class.getClassLoader()** thường trả **null** với lớp được bootstrap định nghĩa.
- **Platform loader:** phục vụ các module của nền tảng thích hợp.
- **Application/System loader:** thường nạp mã ứng dụng từ classpath/module path trong phạm vi phụ trách.
- **Custom loader:** do ứng dụng/thư viện xây dựng, ví dụ hệ plugin, engine hoặc container.

Đây là mô hình khái quát; không nên coi thứ tự này là một chuỗi thư mục cố định trên ổ đĩa hoặc một cam kết rằng mọi JVM/custom loader đều tìm lớp từ cùng các vị trí.

### 3.1 Cơ chế delegation phổ biến

Với cách cài đặt mặc định của **ClassLoader.loadClass(name, resolve)**, loader thường:

1. Kiểm tra lớp với tên đó đã được loader này tìm/nạp trước chưa.
2. Nếu chưa, yêu cầu parent loader (hoặc bootstrap nếu phù hợp).
3. Nếu parent không tìm được, gọi **findClass(name)** của chính nó.
4. Nếu tham số **resolve** yêu cầu, thực hiện bước resolveClass phù hợp trước khi trả về.

Ý tưởng là cho các loader liên quan chia sẻ lớp nền tảng và tránh định nghĩa trùng không cần thiết. Tuy nhiên, Java cho phép **custom class loader** có chính sách delegation riêng. Không thể áp quy tắc **parent-first** cho mọi loader mà không xem mã triển khai.

### 3.2 Initiating loader và defining loader khác nhau

Giả sử application loader **A** được yêu cầu tìm **java.lang.String**, rồi ủy quyền cho bootstrap loader **B** định nghĩa String:

~~~text
load request tới A
       │
       └─ A chuyển cho B
                  │
                  └─ B định nghĩa java.lang.String
                         │
                         └─ A nhận lại Class<?> tương ứng
~~~

Theo thuật ngữ JVM:
- **Initiating loader:** loader mà JVM ghi nhận đã khởi xướng việc loading của lớp đó (có thể bao gồm loader ủy quyền).
- **Defining loader:** loader **thực sự định nghĩa** class/interface từ biểu diễn nhị phân hoặc theo quy tắc tạo lớp thích hợp.

Trong ví dụ, **A** có thể là initiating loader, còn **B** là defining loader. Việc gọi **A.loadClass(...)** **không chứng minh** A là defining loader của kết quả.

Với một lớp thông thường, **clazz.getClassLoader()** cho biết loader định nghĩa lớp đó (hoặc **null** nếu lớp được bootstrap định nghĩa). Nó không phải API liệt kê tất cả initiating loader đã từng yêu cầu lớp.

## 4. JVM tạo đối tượng Class khi nào và dùng để làm gì?

Khi JVM tạo một class/interface runtime từ biểu diễn nhị phân, nó cũng có một đối tượng **java.lang.Class** tương ứng để Java code có thể mô tả và thao tác với kiểu. Đây thường được gọi là **Class mirror**.

Đối tượng Class có thể được lấy qua:

~~~java
Class<?> a = String.class;
Class<?> b = someObject.getClass();
Class<?> c = Class.forName("demo.Widget", false, loader);
Class<?> d = loader.loadClass("demo.Widget");
~~~

Các thao tác trên không tương đương hoàn toàn:

- **T.class** lấy Class mirror cho kiểu T, không tự yêu cầu chạy static initializer của T.
- **object.getClass()** lấy lớp runtime thực của đối tượng đã tồn tại.
- **Class.forName(name, false, loader)** yêu cầu load (và link theo hợp đồng API) lớp tìm được nhưng **không khởi tạo lớp theo lời gọi này**.
- **ClassLoader.loadClass(name)** tìm/nạp và trả đối tượng Class; cách gọi này **không chủ động khởi tạo** lớp. Việc phân giải tham chiếu phụ thuộc thời điểm/quy tắc JVM và overload được gọi.
- **Class.forName(name)** dạng một tham số mặc định yêu cầu initialization cho lớp tìm được, nên có thể làm static initializer chạy ngay.

**Class mirror không phải là instance của lớp được mô tả.** Ví dụ **Widget.class** có kiểu tĩnh **Class<Widget>**; đối tượng đó dùng cho metadata/reflection, còn **new Widget()** là cách tạo instance của Widget. **getDeclaredConstructor().newInstance()** cũng tạo instance và có thể kích hoạt initialization trước khi gọi constructor.

Thông tin mà Class mirror cho phép xem có tên lớp, superclass, interfaces, declared members, annotation, module và class loader. JVM có các cấu trúc dữ liệu runtime nội bộ phục vụ thực thi, nhưng đặc tả **không bắt buộc** phải có một bố cục bộ nhớ cố định cho mọi JVM; đối tượng **java.lang.Class** không phải bản sao trực tiếp từng byte của file **.class**.

## 5. Ví dụ Java 17+: tìm bytecode và quan sát Class mirror

Tạo hai file sau trong đúng thư mục:

~~~text
project/
└── src/
    └── demo/
        ├── Widget.java
        └── LoadingDemo.java
~~~

**src/demo/Widget.java**:

~~~java
package demo;

public class Widget {
    static {
        System.out.println("Widget initialized");
    }

    public String message() {
        return "Hello";
    }
}
~~~

**src/demo/LoadingDemo.java**:

~~~java
package demo;

public class LoadingDemo {
    public static void main(String[] args) throws Exception {
        ClassLoader loader = LoadingDemo.class.getClassLoader();

        String resource = "demo/Widget.class";
        System.out.println("resource found = "
                + (loader.getResource(resource) != null));

        Class<?> type = loader.loadClass("demo.Widget");
        System.out.println("binary name = " + type.getName());

        Class<?> again = Class.forName("demo.Widget", false, loader);
        System.out.println("same Class = " + (type == again));

        System.out.println("before instance creation");
        Object instance = type.getDeclaredConstructor().newInstance();
        System.out.println("instance type = "
                + instance.getClass().getSimpleName());
    }
}
~~~

Đứng tại thư mục **project**, chạy:

~~~bash
javac -d out src/demo/Widget.java src/demo/LoadingDemo.java
java -cp out demo.LoadingDemo
~~~

**Stdout kỳ vọng** khi dùng application classpath đúng như lệnh trên:

~~~text
resource found = true
binary name = demo.Widget
same Class = true
before instance creation
Widget initialized
instance type = Widget
~~~

Phân tích:

1. **javac -d out** tạo các file **out/demo/Widget.class** và **out/demo/LoadingDemo.class**. Đây là các biểu diễn nhị phân để application loader tìm.
2. **getResource("demo/Widget.class")** cho thấy bytecode dạng tài nguyên có thể được tìm trong ví dụ này. Chỉ thấy resource **chưa có nghĩa** class đã được JVM định nghĩa.
3. **loadClass("demo.Widget")** trả đối tượng Class mô tả Widget. Chưa có dòng **Widget initialized**: chương trình chưa kích hoạt initialization của Widget.
4. **Class.forName(..., false, ...)** trả cùng Class vì yêu cầu cùng binary name qua cùng hệ loader trong ví dụ; toán tử **==** đúng vì cùng một định nghĩa runtime.
5. **getDeclaredConstructor().newInstance()** kích hoạt initialization trước khi tạo instance; static block in ra đúng một lần rồi mới tới dòng cuối.

Ở những loader đặc biệt, **getResource** có thể bị chặn, ánh xạ khác hoặc tìm được tài nguyên mà **loadClass** lại không định nghĩa được lớp tương ứng. Vì vậy phương pháp dò resource hữu ích khi gỡ lỗi classpath thông thường, nhưng **không thay thế** kết quả thực tế của **loadClass**.

### 5.1 Xem biểu diễn nhị phân bằng javap

Sau khi biên dịch:

~~~bash
javap -classpath out -v demo.Widget
~~~

Một số thông tin nên tìm trong output (giá trị version và các index thay đổi theo JDK/compiler):

- **Classfile .../out/demo/Widget.class**, tên class, superclass;
- **major version** cùng constant pool;
- tên kiểu hoặc mô tả method/field;
- method **<clinit>** chứa mã cho static initializer;
- thuộc tính **Code** trong các method có bytecode.

Để chỉ xem lệnh bytecode, sử dụng:

~~~bash
javap -classpath out -c -p demo.Widget
~~~

Tùy ngôn ngữ shell, nội dung output có thể dài. **javap** giúp kiểm tra class file đã biên dịch; nó **không chứng minh** JVM runtime đã nạp class từ đúng tệp đó nếu ứng dụng chạy với classpath, module path hoặc loader khác.

Nếu đóng gói JAR:

~~~bash
jar --create --file example.jar -C out .
jar --list --file example.jar
~~~

Bạn sẽ thấy các entry kiểu **demo/Widget.class** và **demo/LoadingDemo.class**. Lệnh **jar --list** chỉ liệt kê dữ liệu trong archive, không nạp hay khởi tạo class vào JVM đang chạy.

## 6. Define class từ bytecode: điều gì tạo nên một định nghĩa mới?

Trong custom loader, phương thức **defineClass(...)** được hỗ trợ bởi ClassLoader cho phép chuyển mảng byte hợp lệ thành class do loader đó định nghĩa. Một luồng thường gặp là:

~~~text
ClassLoader.loadClass(name)
       │
       ├─ trả class đã biết / ủy quyền parent
       │
       └─ tìm bytecode (ví dụ trong findClass)
               │
               ▼
     defineClass(binaryName, bytes, ...)
               │
               ▼
       Class<?> mới của loader định nghĩa
~~~

Nếu gọi **defineClass** cho cùng binary name mà cùng loader đã định nghĩa một lần, không thể âm thầm thay Class hiện tại bằng nội dung khác: JVM có ràng buộc về định nghĩa trùng và có thể báo **LinkageError**. Cơ chế **redefine/retransform** do instrumentation cung cấp là vấn đề khác, không phải một lần defineClass mới để tạo Class thứ hai trong cùng loader.

Loader cần cung cấp binary name phù hợp, bytes hợp lệ và tuân thủ ràng buộc package/protection domain và loading constraints. **defineClass** là thao tác tạo định nghĩa runtime, không phải API bảo đảm class đã chạy các static initializer.

### 6.1 Danh tính class = (binary name, defining loader)

Trong JVM, danh tính của một class/interface được xác định bởi **binary name kết hợp defining class loader**. Hệ quả trực tiếp:

- Cùng tên và **cùng định nghĩa class**: tham chiếu Class giống nhau.
- Cùng tên nhưng **được định nghĩa bởi hai loader khác nhau**: hai kiểu runtime **khác nhau**.
- Việc in ra **Class.getName()** giống nhau chưa đủ để xác nhận hai object có thể ép kiểu với nhau.

Mô hình:

~~~text
                  bytecode cho isolated.Payload
                             │
                     ┌───────┴────────┐
                     ▼                ▼
              defining loader L1   defining loader L2
                     │                │
                     ▼                ▼
               Class #1           Class #2
                name =             name =
          isolated.Payload    isolated.Payload
                     │                │
                     └───────┬────────┘
                             ▼
                 Class #1 != Class #2
~~~

Trong các ứng dụng có plugin, modular runtime và container, đây là nguyên nhân phổ biến của lỗi **ClassCastException** nhìn bề ngoài có vẻ vô lý: hai lớp cùng tên nhưng thuộc hai định nghĩa runtime.

### 6.2 Ví dụ chạy được: hai loader tự định nghĩa cùng một binary name

Tạo cấu trúc:

~~~text
project/
└── src/
    └── isolated/
        ├── Payload.java
        └── TwinLoadersDemo.java
~~~

**src/isolated/Payload.java**:

~~~java
package isolated;

public class Payload {
    public String value() {
        return "payload";
    }
}
~~~

**src/isolated/TwinLoadersDemo.java**:

~~~java
package isolated;

import java.nio.file.Files;
import java.nio.file.Path;

public class TwinLoadersDemo {
    static final class ByteArrayLoader extends ClassLoader {
        Class<?> defineOwn(byte[] classBytes) {
            return defineClass(
                    "isolated.Payload",
                    classBytes,
                    0,
                    classBytes.length
            );
        }
    }

    public static void main(String[] args) throws Exception {
        byte[] bytes = Files.readAllBytes(
                Path.of("out/isolated/Payload.class")
        );

        ByteArrayLoader left = new ByteArrayLoader();
        ByteArrayLoader right = new ByteArrayLoader();

        Class<?> a = left.defineOwn(bytes);
        Class<?> b = right.defineOwn(bytes);

        System.out.println("same name = "
                + a.getName().equals(b.getName()));
        System.out.println("same Class = " + (a == b));
        System.out.println("different defining loader = "
                + (a.getClassLoader() != b.getClassLoader()));

        Object first = a.getDeclaredConstructor().newInstance();
        Object second = b.getDeclaredConstructor().newInstance();
        System.out.println("same runtime type = "
                + (first.getClass() == second.getClass()));

        try {
            a.cast(second);
        } catch (ClassCastException ex) {
            System.out.println("cross-loader cast = ClassCastException");
        }
    }
}
~~~

Tại thư mục **project**:

~~~bash
javac -d out src/isolated/Payload.java src/isolated/TwinLoadersDemo.java
java -cp out isolated.TwinLoadersDemo
~~~

Stdout:

~~~text
same name = true
same Class = false
different defining loader = true
same runtime type = false
cross-loader cast = ClassCastException
~~~

Cả hai ByteArrayLoader đều nhận **đúng cùng mảng bytes** nhưng gọi **defineClass** một cách riêng rẽ. Đây là ví dụ chủ động định nghĩa riêng để chứng minh danh tính lớp; phương thức **defineOwn** minh họa trực tiếp API defineClass, **không đi qua cơ chế parent delegation mặc định của loadClass**. Trong ứng dụng thực, bạn thường triển khai **findClass** có kiểm soát rồi để **loadClass** thực hiện quy trình delegation thích hợp.

Nếu sửa ví dụ để hai loader đều ủy quyền cho cùng parent định nghĩa Payload, kết quả có thể là **cùng** đối tượng Class; chỉ khởi tạo hai loader object không tự làm phát sinh hai định nghĩa của Payload.

## 7. Trường hợp đặc biệt: array, primitive, hidden class và module

Không nên áp mô hình **tìm tệp .class trên đĩa** cho mọi kiểu JVM hỗ trợ.

### 7.1 Array class

Với biểu thức như:

~~~java
String[] names = new String[2];
Class<?> arrayType = names.getClass();
~~~

JVM tạo array class theo quy tắc riêng, không tìm tệp **[Ljava/lang/String;.class**. Component type ảnh hưởng đến defining loader của array class; mảng có component type là primitive có bootstrap loader. Tạo mảng **Foo[]** không yêu cầu khởi tạo Foo chỉ vì mảng được cấp phát.

Tên runtime của class mảng được biểu diễn theo descriptor, ví dụ **[Ljava.lang.String;** trong **Class.getName()**, nên không thể coi đó là tên file thông thường.

### 7.2 Primitive và void

**int.class**, **boolean.class** và **void.class** có Class mirror, nhưng không tương ứng với class file kiểu **int.class** trên classpath. JVM tạo và quản lý các Class object này theo quy tắc đặc biệt.

### 7.3 Hidden classes

Các API như **MethodHandles.Lookup.defineHiddenClass** hỗ trợ những lớp dành cho mục đích runtime đặc biệt; không nên giả định rằng mọi lớp có thể được tìm lại bằng binary name qua **ClassLoader.loadClass**. Hidden classes cần được phân biệt với lớp/interface thông thường do loader định nghĩa bằng tên.

### 7.4 Module và phạm vi tra cứu

Khi dùng Java Module System, **module path**, module graph, readability, exports và chính sách loader/module ảnh hưởng đến việc lớp nào tìm được và ai có thể truy cập nó. Đường dẫn có file **.class** hoặc **.jar** **không đủ** để kết luận lớp được truy cập thành công ở runtime. **Class.getModule()** là một nguồn dữ liệu khi kiểm tra runtime, còn tên module và tên binary class vẫn là hai khái niệm khác nhau.

## 8. Phân biệt tìm thấy bytes, load, link và initialize

| Điều quan sát | Có thể kết luận | Chưa thể kết luận |
| --- | --- | --- |
| **jar --list** hiện ra **demo/Widget.class** | JAR có entry với tên đó. | JVM đã nạp Widget. |
| **getResource** trả URL/stream | Loader hoặc cơ chế resource tìm thấy tài nguyên. | Bytes đó nhất định đã được define thành Class. |
| **loadClass("demo.Widget")** trả Class | Lời gọi trả về Class runtime tương ứng. | Static initializer của Widget đã chạy. |
| **Class.forName(name, false, loader)** trả Class | Lớp được tìm/nạp và link theo hợp đồng API, không yêu cầu init ở lời gọi này. | Mọi symbolic reference đều đã resolution ngay thời điểm đó. |
| **Class.forName(name)** trả Class | Với class thông thường, lời gọi yêu cầu initialization. | Constructor instance đã chạy hay object Widget đã được tạo. |
| **new Widget()** thành công | Widget đã được khởi tạo theo quy tắc trước khi tạo instance. | Class sẽ được khởi tạo lại cho các đối tượng tiếp theo. |

Vì **linking** có verification/preparation/resolution với thời điểm chi tiết theo đặc tả, việc kết hợp **javap**, logging và chương trình có static block sẽ cho nhiều góc nhìn hơn so với dùng riêng một log.

## 9. Chẩn đoán lỗi tìm định nghĩa lớp

### 9.1 File/JAR có lớp nhưng ClassNotFoundException

Kiểm tra:

1. Tên truyền cho **Class.forName** hoặc **loadClass** có đúng **binary name** chưa? Với nested class cần kiểm tra ký tự **$**.
2. Runtime **-cp**, **--module-path** và cấu hình thư viện khác có chứa định nghĩa cần thiết không? Classpath dùng lúc **javac** không tự động bảo đảm classpath lúc **java** giống nhau.
3. Loader nào đang nhận yêu cầu? Nó có delegation/filter riêng không? Chẳng hạn code trong một plugin có thể dùng loader khác code ứng dụng.
4. Class có thực sự tồn tại dưới đúng package và tên sau khi build hoặc đóng JAR không?

**ClassNotFoundException** là checked exception thường gặp từ API tìm lớp theo tên. **NoClassDefFoundError** là LinkageError có thể xuất hiện khi JVM cần định nghĩa class không có sẵn hoặc class trước đó không khởi tạo được. Hai lỗi có thể liên quan nhưng **không đồng nghĩa**.

### 9.2 ClassFormatError hoặc UnsupportedClassVersionError

**ClassFormatError** chỉ dữ liệu class file không hợp lệ về định dạng; **UnsupportedClassVersionError** xảy ra khi VM không hỗ trợ phiên bản class file được cung cấp. Kiểm tra compiler **javac -version**, runtime **java -version**, hoặc **javap -verbose** để so sánh version. Không giải quyết lỗi này chỉ bằng thay class name.

### 9.3 NoSuchMethodError, IllegalAccessError, LoaderConstraintViolation

Đó có thể là lỗi ở quá trình resolution/linking hoặc không tương thích nhị phân, ví dụ có hai phiên bản thư viện trên đường nạp thực tế. Với các loader khác nhau, JVM còn áp ràng buộc nhất quán kiểu tại một số điểm sử dụng; lỗi **LinkageError** về loading constraint cần kiểm tra defining loader của những lớp cùng tên.

### 9.4 ClassCastException mặc dù hai tên giống nhau

Lấy đối tượng và xem:

~~~java
Class<?> actual = object.getClass();

System.out.println(actual.getName());
System.out.println(actual.getClassLoader());
System.out.println(actual.getModule());
~~~

Đối chiếu với Class của kiểu đích bằng **==**, **getClassLoader()**, **getModule()** và nơi bytecode thực sự xuất phát. Thông báo lỗi casting gần đây thường cho biết loader/module của các class liên quan. **Không khắc phục bằng ép kiểu cưỡng bức**; cần thống nhất contract API chung, loader visibility hoặc cách chia sẻ kiểu giữa host và plugin.

## 10. Công cụ quan sát class thực tế

Trong môi trường Java 17+, một lựa chọn tương đối hữu ích khi cần xem JVM đã load các lớp nào:

~~~bash
java -Xlog:class+load=info -cp out demo.LoadingDemo
~~~

Log thường thể hiện tên lớp và nguồn theo khả năng của VM đang dùng, nhưng format và số lượng dòng phụ thuộc JDK/VM. Để kiểm tra các tag và mức log được VM hỗ trợ:

~~~bash
java -Xlog:help
~~~

**Giới hạn:** log class loading giúp biết lớp đã được load, **không chứng minh thời điểm chạy <clinit>**. Với vấn đề danh tính class, hãy kiểm tra **Class.getClassLoader()** hoặc quan sát thực thể Class thực tế. Với class file trên đĩa, dùng **javap -v** để đọc cấu trúc. Với artifact đóng gói, dùng **jar --list** để xác nhận entry có tồn tại.

Trong các hệ thống lớn, ghi log đầy đủ cho mọi class có thể gây nhiễu. Hãy tạo ví dụ nhỏ tái hiện trước, dùng đúng classpath/module path, rồi mở rộng quan sát nếu cần.

## 11. Những hiểu nhầm phổ biến

- **“Tên class là đủ để xác định kiểu.”** Chưa đủ: defining loader là thành phần của danh tính runtime.
- **“Tôi tìm thấy .class trong JAR, vậy JVM chắc chắn đã load nó.”** Sai: JAR có entry không đồng nghĩa loader đang chạy đã dùng entry đó.
- **“loadClass luôn đọc lại file mỗi lần gọi.”** Sai: loader thường dùng cache/cơ chế tìm lớp đã được load; định nghĩa trùng trong cùng loader bị ràng buộc.
- **“Loader nhận yêu cầu chính là loader định nghĩa.”** Sai khi có delegation.
- **“Class<?> là bản bytecode còn nằm nguyên trong heap.”** Sai: Class là đối tượng phản chiếu kiểu runtime; bố cục nội bộ là chi tiết VM.
- **“Đã có Class thì static initializer đã chạy.”** Sai: loading và initialization khác nhau.
- **“Mọi kiểu đều có một tệp .class.”** Sai: primitive, void, array và một số dạng runtime đặc biệt được xử lý riêng.
- **“Custom loader nào cũng parent-first.”** Không nhất thiết; cần đọc chính sách delegation của loader.
- **“Dùng getResource là đủ để chứng minh class được define.”** Sai: tài nguyên và cơ chế define class là hai bước riêng.

## 12. Tự kiểm tra

**Câu 1.** Với package **demo**, tại sao **demo.Widget**, **demo/Widget** và **demo/Widget.class** là ba chuỗi khác nhau?

**Đáp án:** Chúng lần lượt là binary name, dạng internal name và đường dẫn tài nguyên phổ biến. API **loadClass** cho lớp thông thường nhận binary name, còn file/JAR thường chứa resource path.

**Câu 2.** Tại sao **loader.loadClass("demo.Widget")** có thể trả về Class mà chưa in dòng trong static block?

**Đáp án:** Nó nạp/trả Class nhưng không chủ động khởi tạo lớp; static initializer chạy khi gặp điều kiện initialization.

**Câu 3.** Hai ByteArrayLoader nhận cùng bytes và đều gọi defineClass cho cùng tên. Tại sao Class khác nhau?

**Đáp án:** Binary name giống, nhưng defining loader khác; JVM coi đó là hai định nghĩa class riêng.

**Câu 4.** Một custom loader A ủy quyền cho parent B và nhận lại Class do B định nghĩa. **getClassLoader()** của Class trả A hay B?

**Đáp án:** Trả về defining loader B (nếu không phải bootstrap). A có thể là initiating loader.

**Câu 5.** Có nên suy ra VM đã khởi tạo một lớp nếu chỉ thấy **class+load** log?

**Đáp án:** Không. Log class loading phản ánh loading; initialization phải được xác minh riêng.

**Câu 6.** Tại sao lớp primitive **int** vẫn có **int.class** dù không có class file trên đĩa?

**Đáp án:** JVM cung cấp Class mirror cho primitive theo quy tắc riêng; nó không cần lookup file class thông thường.

## 13. Ghi nhớ

Hãy phân tích đường đi thành các câu hỏi độc lập:

~~~text
Tên nhị phân
    ↓
Ai nhận yêu cầu load?
    ↓
Loader tìm bytes ở nguồn nào?
    ↓
Loader nào thực sự định nghĩa class?
    ↓
JVM tạo định nghĩa runtime + Class mirror
    ↓
Linking/initialization xảy ra theo các điều kiện riêng
~~~

**Binary representation** là dữ liệu mô tả lớp; **defining loader** cùng binary name quyết định danh tính của lớp runtime; **Class mirror** là đối tượng Java biểu diễn định nghĩa ấy. Ba khái niệm này giải thích đa số vấn đề classpath, plugin isolation và nhầm lẫn giữa “đã thấy bytecode” với “JVM đã định nghĩa lớp”.

**Đọc thêm từ đặc tả Java SE 21:**
- JLS §12.2 (*Loading of Classes and Interfaces*): https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html#jls-12.2
- JVMS §4 (*The class File Format*): https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html
- JVMS §5.3 (*Creation and Loading*): https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3
- ClassLoader API: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-lifecycle-003">Nạp các lớp cha và interface cha cần thiết</a>

<details>
<summary>Xem chi tiết</summary>

# Nạp các lớp cha và interface cha cần thiết trong vòng đời lớp Java

## 1. Mục tiêu: vì sao phải nạp kiểu cha trước khi lớp con hoạt động?

Khi JVM tạo một định nghĩa runtime cho lớp Java, việc tìm thấy file `.class` của lớp đó mới chỉ là bước đầu. JVM còn phải xác lập **quan hệ kế thừa**: lớp này có lớp cha trực tiếp nào (`extends`), có những interface cha trực tiếp nào (`implements` hoặc `extends` giữa các interface), và chúng có hợp lệ hay không. Những kiểu cha cần thiết phải được nạp theo các quy tắc của JVM trước khi định nghĩa lớp con được tạo thành công.

Ví dụ:

```java
interface Auditable {}
interface Identified extends Auditable {}
class Entity {}
class Customer extends Entity implements Identified {}
```

Khi nạp `Customer`, JVM phải xử lý lớp cha trực tiếp `Entity` và interface trực tiếp `Identified`. Để nạp `Identified`, JVM cũng phải xử lý interface cha `Auditable`. Các quan hệ này tạo thành một đồ thị phụ thuộc kiểu, không chỉ là quan hệ giữa các file nguồn.

Cần phân biệt từ đầu:

- **Nạp (loading):** JVM tìm/tạo biểu diễn runtime của một lớp hoặc interface từ định nghĩa nhị phân, thường thông qua `ClassLoader`.
- **Liên kết (linking):** kiểm tra định nghĩa, chuẩn bị các trường tĩnh và xử lý tham chiếu ký hiệu theo quy tắc JVM.
- **Khởi tạo (initialization):** thực thi khởi tạo tĩnh của lớp/interface (`<clinit>`, nếu có). **Nạp kiểu cha không có nghĩa là ngay lúc đó phải chạy static initializer của kiểu cha.**
- **Phân giải tham chiếu (resolution):** chuyển các tham chiếu ký hiệu trong constant pool thành tham chiếu runtime. Không phải mọi lớp được nhắc trong phương thức, field hoặc generic đều bắt buộc được nạp ngay cùng lúc với lớp con.

Trọng tâm của bài là những phụ thuộc trực tiếp từ **cấu trúc kế thừa**: superclass và superinterface.

## 2. Metadata nào cho JVM biết cần nạp ai?

File `.class` chứa thông tin cấu trúc, trong đó có:

| Thành phần class file | Ý nghĩa đối với quan hệ cha |
| --- | --- |
| `this_class` | Định danh nhị phân của kiểu đang được định nghĩa |
| `super_class` | Tham chiếu tới lớp cha trực tiếp của một lớp thông thường; với `java.lang.Object`, chỉ số là 0 |
| `interfaces[]` | Danh sách interface cha trực tiếp mà class/interface khai báo |
| `access_flags` | Cho biết định nghĩa là class/interface, có `final`, `abstract`, ... để kiểm tra tính hợp lệ |

Một lớp Java thông thường (ngoại trừ `java.lang.Object`) có một superclass; không ghi `extends` trong mã nguồn thì superclass là `java.lang.Object`. Một lớp có thể `implements` nhiều interface. Một interface có thể `extends` nhiều interface.

**Chi tiết đặc biệt về class file:** ở định dạng class file, `super_class` của một interface ghi `java/lang/Object`, nhưng điều đó không biến `Object` thành một “superinterface” được liệt kê trong `interfaces[]` và không thay đổi quy tắc kế thừa interface trong Java. Khi nói “superinterface trực tiếp”, ta chỉ các mục trong `interfaces[]`.

Định nghĩa một kiểu chứa nhiều tham chiếu khác ngoài quan hệ cha: kiểu tham số, kiểu trả về, field, local variable, annotation, thân phương thức. Các tham chiếu đó có thể cần resolution ở những thời điểm khác. Không được kết luận JVM **luôn nạp tất cả** các kiểu xuất hiện trong bytecode ngay khi nạp lớp.

## 3. Trình tự nạp superclass và superinterface

Theo mô hình tạo lớp/interface của JVM (JVMS §5.3), khi một định nghĩa từ bytecode được tạo ra, JVM phải thiết lập quan hệ trực tiếp trước khi hoàn tất định nghĩa mới. Có thể hình dung trình tự logic như sau:

1. Một loader nhận yêu cầu nạp tên nhị phân, ví dụ `com.example.Customer`.
2. Loader kiểm tra lớp đã được định nghĩa/chấp nhận trước đó hoặc tìm bytecode. Với parent delegation, nó có thể nhờ loader cha trước.
3. Khi JVM tạo định nghĩa `Customer`, nó sử dụng metadata để xác định `Entity` là superclass trực tiếp và `Identified` là superinterface trực tiếp.
4. JVM yêu cầu nạp các kiểu cha cần thiết thông qua quy tắc loader liên quan; thao tác này tiếp tục đệ quy cho các superclass và superinterface của chính những kiểu cha đó.
5. JVM kiểm tra quan hệ: superclass không thể là interface hay lớp `final`; superinterface được chỉ ra phải thật sự là interface; quan hệ kế thừa không được tạo vòng lặp.
6. Khi các điều kiện tương ứng thỏa mãn, JVM mới có thể hoàn tất việc tạo biểu diễn runtime của kiểu con. Linking và initialization vẫn có quy tắc/thời điểm riêng.

Ví dụ đồ thị:

```text
Customer (class)
├── extends Entity (class)
│   └── extends java.lang.Object
└── implements Identified (interface)
    └── extends Auditable (interface)
```

Các nút ở nhánh kế thừa trực tiếp và gián tiếp là phụ thuộc cần xử lý. Sơ đồ này **không phải cam kết về thứ tự log tuyệt đối** trong mọi JVM: class loading có thể diễn ra sớm do verifier, reflection, mã khác hoặc quá trình linking, và delegation có thể chuyển việc định nghĩa sang loader khác. Điều bắt buộc là những kiểu cha liên quan phải hợp lệ khi định nghĩa kiểu con thành công.

### 3.1. Superclass phải là lớp phù hợp

Với `class Customer extends Entity`, JVM cần tạo/lấy kiểu runtime tương ứng với `Entity`. `Entity` lại có superclass, thường kết thúc tại `java.lang.Object`. Nếu `Entity` không tìm thấy hoặc không thể định nghĩa, quá trình nạp `Customer` cũng có thể thất bại.

Lớp Java không thể kéo dài một lớp `final`. Trình biên dịch thường chặn trực tiếp, nhưng JVM vẫn phải bảo vệ tính hợp lệ khi gặp bytecode không hợp lệ hoặc nhị phân không tương thích, có thể báo `IncompatibleClassChangeError`.

### 3.2. Superinterface cũng là phụ thuộc của định nghĩa

Với `class Customer implements Identified`, `Identified` phải được nạp như một **interface**. Nếu `Identified extends Auditable`, việc nạp interface đó phải xử lý `Auditable`. Nếu một tên được liệt kê như superinterface lại phân giải ra class thường, JVM từ chối cấu trúc không hợp lệ, không “tự sửa” quan hệ.

Một interface có thể `extends` nhiều interface, và một class có thể `implements` nhiều interface. Vì vậy đồ thị là cấu trúc có nhánh, không phải chỉ một danh sách superclass tuyến tính.

### 3.3. Không có chuyện “khởi tạo tất cả cha” trong giai đoạn loading

Loading thiết lập định nghĩa kiểu, còn initialization chạy mã tĩnh. Vì vậy, nhìn thấy JVM đã nạp một superinterface không có nghĩa đã in ra nội dung static initializer của nó.

Phân biệt **lớp cha đã được nạp** với **lớp cha đã được khởi tạo** là kỹ năng quan trọng khi đọc log và xử lý lỗi.

## 4. Thí nghiệm Java 17+: loading khác initialization

Chương trình dưới đây cho thấy JVM có thể lấy `Class` của lớp con mà chưa chạy khởi tạo tĩnh; khi kích hoạt khởi tạo lớp con, superclass và **những superinterface khai báo default method** có quy tắc khởi tạo riêng. Interface chỉ khai báo hằng runtime mà không có default method chưa cần khởi tạo chỉ vì lớp con `implements` nó.

Lưu toàn bộ vào `ParentLoadDemo.java`:

```java
public class ParentLoadDemo {

    interface Marker {
        // Không phải compile-time constant: có lời gọi phương thức.
        int VALUE = mark("Marker");

        static int mark(String name) {
            System.out.println(name + " initialized");
            return 1;
        }
    }

    interface Behavior {
        int VALUE = mark("Behavior");

        static int mark(String name) {
            System.out.println(name + " initialized");
            return 2;
        }

        // Interface có default method.
        default String label() {
            return "demo";
        }
    }

    static class Parent {
        static {
            System.out.println("Parent initialized");
        }
    }

    static class Child extends Parent implements Marker, Behavior {
        static {
            System.out.println("Child initialized");
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("1. Before load");

        Class<?> type = Class.forName(
                "ParentLoadDemo$Child",
                false,
                ParentLoadDemo.class.getClassLoader());

        System.out.println("2. Loaded: " + type.getSimpleName());
        System.out.println("3. Trigger Child initialization");

        Object instance = type.getDeclaredConstructor().newInstance();

        System.out.println("4. Child instance: " + (instance instanceof Child));
        System.out.println("5. Marker value: " + Marker.VALUE);
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac ParentLoadDemo.java
java ParentLoadDemo
```

**Stdout mong đợi:**

```text
1. Before load
2. Loaded: Child
3. Trigger Child initialization
Parent initialized
Behavior initialized
Child initialized
4. Child instance: true
Marker initialized
5. Marker value: 1
```

Đọc kết quả:

- `Class.forName(name, false, loader)` lấy định nghĩa `Child` nhưng không yêu cầu khởi tạo `Child`. Việc lấy tên bằng `getSimpleName()` cũng không kích hoạt `<clinit>` của `Child`.
- Lệnh tạo instance bằng reflection là **active use** của `Child`; JVM phải khởi tạo kiểu này trước khi tạo đối tượng.
- Trước khởi tạo `Child`, JVM khởi tạo superclass `Parent` và những superinterface liên quan **khai báo default method** theo quy tắc JLS/JVMS; ở đây `Behavior` được khởi tạo.
- `Marker` không khai báo default method, nên không phải khởi tạo chỉ vì `Child implements Marker`.
- Khi biểu thức `Marker.VALUE` thực sự được đọc, `VALUE` là giá trị `static final` được tính bằng gọi phương thức (không phải constant expression), làm `Marker` khởi tạo ở bước cuối.
- `(instance instanceof Child)` chỉ kiểm tra kiểu object; nó không phải lý do gây ra các dòng khởi tạo trước đó.

**Lưu ý:** ví dụ này quan sát **thời điểm initialization**, không đo toàn bộ thời điểm loading. Việc không thấy dòng `Marker initialized` ở bước 2 không chứng minh `Marker` chưa được *loaded*; chỉ chứng minh mã khởi tạo tĩnh của nó chưa chạy.

## 5. Quy tắc khởi tạo theo quan hệ cha: class và interface khác nhau

Khi **khởi tạo một class** `C`, JVM khởi tạo các superclass còn chưa khởi tạo và các superinterface liên quan **có khai báo default method** theo quy tắc về thứ tự của JLS §12.4.2/JVMS §5.5, trước khi chạy mã khởi tạo của `C`. Các interface chỉ được kế thừa gián tiếp cũng có thể liên quan qua quy tắc đệ quy ấy. Cần phân biệt **khai báo default method** với việc chỉ kế thừa một default method từ interface cha; thứ tự cụ thể phải theo định nghĩa của đặc tả, không suy ra bằng thứ tự các file JAR.

Khi **khởi tạo một interface**, JVM không bắt buộc tự động khởi tạo tất cả superinterface của nó như cách khởi tạo superclass của class. Khởi tạo interface thường xảy ra khi có active use riêng của interface hoặc các điều kiện khác theo đặc tả.

| Hành động | Superclass/superinterface cần nạp? | Khởi tạo tĩnh ngay? |
| --- | --- | --- |
| Định nghĩa `Child extends Parent implements Marker` | Phải xử lý các phụ thuộc cha tương ứng để định nghĩa `Child` | Không đồng nghĩa với việc chạy `<clinit>` |
| `Class.forName("Child", false, loader)` | Yêu cầu tìm/nạp/liên kết `Child` cùng các phụ thuộc cấu trúc cần thiết | Không chủ động khởi tạo `Child` |
| `new Child()` (sử dụng lần đầu) | Các kiểu liên quan phải sẵn sàng | Khởi tạo `Parent`, các interface có default method theo quy tắc, rồi `Child` |
| Đọc `Marker.VALUE` không phải constant expression | `Marker` phải có định nghĩa hợp lệ | Khởi tạo `Marker` nếu chưa khởi tạo |
| Đọc `SomeInterface.CONST = 10` | Có thể được compiler inline như compile-time constant | Không đủ để kết luận interface sẽ được khởi tạo |

Không nên dùng `System.out.println` trong static initializer làm bằng chứng về **loading order**, vì nó cho thấy **initialization order**. Muốn quan sát loading, dùng log chuyên dụng hoặc công cụ chẩn đoán JVM.

## 6. ClassLoader nào nạp kiểu cha?

Một class được định nghĩa bởi loader `L`, nhưng superclass hoặc superinterface của nó **không nhất thiết có cùng defining loader**. Khi JVM phân giải phụ thuộc cấu trúc theo định nghĩa của `C`, nó dùng loader phù hợp với quy tắc phân giải của `C`, rồi loader đó có thể ủy quyền cho cha.

Ví dụ hệ thống plugin:

```text
ApplicationLoader
├── api.Plugin       (interface chia sẻ)
└── api.Result       (kiểu kết quả chia sẻ)
      ↑
      ├── PluginLoaderA ── impl.PluginA implements api.Plugin
      └── PluginLoaderB ── impl.PluginB implements api.Plugin
```

Nếu `impl.PluginA` được định nghĩa bởi `PluginLoaderA` và `api.Plugin` được định nghĩa bởi `ApplicationLoader`, thì quan hệ `implements` vẫn hợp lệ: loader của lớp con có thể yêu cầu kiểu cha và nhận `Class` do cha định nghĩa.

Nhưng nếu loader của plugin tự định nghĩa một bản `api.Plugin` khác với bản của host, hai interface có thể cùng tên nhưng **khác danh tính kiểu**. Khi host ép đối tượng `impl.PluginA` sang `api.Plugin` của host, cast có thể thất bại dù plugin “đã implements Plugin”.

Danh tính runtime của một kiểu được xác định bởi **(binary name, defining loader)**. Sự khác nhau giữa **initiating loader** (yêu cầu load) và **defining loader** (thực sự định nghĩa lớp) giải thích tại sao hai plugin khác nhau vẫn có thể chia sẻ một interface cha duy nhất.

### 6.1. Parent delegation không có nghĩa cha tự nhìn thấy lớp con

Loader con thường hỏi loader cha trước theo cơ chế parent-first, nhưng loader cha không tự tìm xuống mọi loader con. Khi host định nghĩa `api.Plugin`, plugin loader có thể dùng chung interface ấy; ngược lại, host không tự động nạp được mọi `impl.PluginA`.

Một class loader tùy biến có thể thiết kế child-first hoặc delegation chọn lọc. Dù chọn cách nào, các **API/SPI dùng chung** ở ranh giới host–plugin thường cần cùng một defining loader để giữ tương thích kiểu.

### 6.2. JVM kiểm tra ràng buộc loader khi liên kết kiểu

Không chỉ đơn giản so sánh tên và thử cast: JVM còn áp dụng **loading constraints** trong những tình huống liên kết cần bảo đảm tính nhất quán kiểu giữa các loader. Nếu cùng một tên xuất hiện trong chữ ký phương thức xuyên qua các kiểu đã được liên kết mà định nghĩa runtime mâu thuẫn, JVM có thể báo `LinkageError` (ví dụ `loader constraint violation`).

Không nên cố chữa lỗi bằng cách ép hai plugin cùng classpath một cách tùy tiện; cần xác định **kiểu nào phải dùng chung** và **kiểu nào thật sự được cô lập**.

## 7. Nếu không nạp được kiểu cha thì điều gì xảy ra?

Một điểm dễ bỏ sót là ứng dụng có thể tải được bytecode của `Child.class` nhưng vẫn **không thể định nghĩa `Child` hợp lệ** vì thiếu hoặc sai `Parent.class` hay interface cha.

### Tình huống 1: thiếu superclass ở runtime

```java
// Parent.java
public class Parent {}
```

```java
// Child.java
public class Child extends Parent {}
```

Hai file compile thành công nếu cùng classpath. Nhưng giả sử khi triển khai chỉ có `Child.class` mà `Parent.class` không còn trong đường dẫn mà class loader sử dụng: lần đầu nạp `Child` có thể báo `NoClassDefFoundError: Parent` (thường có `ClassNotFoundException` ở phần cause bên dưới).

Điểm cần kiểm tra: **thông báo nêu Parent** không nhất thiết có nghĩa lệnh trực tiếp của bạn yêu cầu nạp `Parent`; nó có thể là phụ thuộc kế thừa bắt buộc của `Child`.

### Tình huống 2: thiếu một superinterface

```java
// Auditable.java
public interface Auditable {}
```

```java
// Child.java
public class Child implements Auditable {}
```

Khi runtime không thể tìm `Auditable`, `Child` cũng không thể được tạo định nghĩa kiểu thành công theo quan hệ `implements`. Vấn đề phát sinh ngay cả khi ứng dụng **chưa gọi một phương thức nào** của `Auditable`.

### Tình huống 3: bytecode không hợp lệ hoặc đổi phiên bản không tương thích

Một số lỗi điển hình:

| Dấu hiệu | Cần nghĩ đến điều gì |
| --- | --- |
| `NoClassDefFoundError: Parent` | Superclass/superinterface hoặc phụ thuộc bắt buộc không khả dụng lúc JVM cần; cũng có thể liên quan đến initialization thất bại trước đó, cần đọc cause |
| `ClassNotFoundException` | API nạp bằng tên không tìm thấy yêu cầu; khác với `NoClassDefFoundError` do việc liên kết/định nghĩa phụ thuộc |
| `IncompatibleClassChangeError` | Bytecode mong đợi class nhưng runtime lại là interface, hoặc ngược lại; kế thừa class `final` không hợp lệ cũng thuộc nhóm lỗi này |
| `ClassCircularityError` | Quan hệ superclass/superinterface hình thành vòng tròn không hợp lệ khi loading |
| `VerifyError` | JVM verifier phát hiện bytecode không đáp ứng yêu cầu an toàn kiểu hoặc ràng buộc liên quan |
| `LinkageError` có `loader constraint violation` | Hai loader định nghĩa các kiểu không tương thích trong một quan hệ cần nhất quán |
| `ExceptionInInitializerError` | Đã bước sang **initialization**, một initializer tĩnh thất bại; không phải bằng chứng chắc chắn loading superclass thất bại |

**Cảnh báo phân loại:** `ClassNotFoundException` là checked exception có thể xuất hiện khi gọi `Class.forName` cho **tên được yêu cầu**; `NoClassDefFoundError` là một `LinkageError` thường cho thấy JVM không thể dùng một định nghĩa phụ thuộc đã được mong đợi. Hai loại này không được xử lý bằng cách chỉ catch một loại và đoán lớp nào hỏng.

### Ví dụ kiểm tra dependency tại runtime

Không cần viết lại class loader để quan sát tình huống thiếu superclass. Trên một môi trường thử nghiệm:

1. Biên dịch cả `Parent.java` và `Child.java`: `javac Parent.java Child.java`.
2. Di chuyển `Parent.class` ra khỏi **classpath của tiến trình thử nghiệm**, không đụng bản triển khai thật.
3. Yêu cầu tải `Child` qua `Class.forName("Child", false, loader)`.
4. Quan sát chuỗi `Throwable`, đặc biệt tên lớp bị thiếu và nguyên nhân lồng trong `Caused by`.
5. Khôi phục đúng file/lớp phụ thuộc trước khi kiểm tra tiếp.

Thao tác trên chỉ là kịch bản thử nghiệm được mô tả, không có stdout cố định áp dụng cho mọi cấu hình loader/JVM.

## 8. Thứ tự loading, linking, initialization và resolution: đừng trộn lẫn

Xét quan hệ:

```java
interface Audit {}
class Root {}
class Order extends Root implements Audit {
    static Helper helper;
}
class Helper {}
```

Các phân biệt cần giữ rõ:

1. Để tạo định nghĩa runtime của `Order`, JVM phải xử lý `Root` và `Audit` như những **kiểu cha trực tiếp**.
2. Việc `Order` có field `Helper helper` không cho phép suy luận `Helper` **bắt buộc phải được khởi tạo** cùng `Order`. Việc resolution của tham chiếu `Helper` có thể xảy ra theo thời điểm khác, tùy nhu cầu và JVM.
3. `Root` có thể đã được nạp trước đó bởi code khác; khi nạp `Order`, JVM tái sử dụng định nghĩa sẵn có từ loader phù hợp thay vì định nghĩa trùng một kiểu.
4. Nếu `Order` được khởi tạo, superclass `Root` phải được khởi tạo trước; `Audit` **không nhất thiết** được khởi tạo nếu không thỏa điều kiện khởi tạo interface.
5. Quá trình **verify** và **resolve** có thể yêu cầu JVM xử lý thêm kiểu ngoài các kiểu cha trực tiếp. Vì vậy, “superclass và superinterface là bắt buộc” không có nghĩa “chỉ chúng mới có thể được nạp lúc đó”.

### Hình dung theo hai trục

```text
Phụ thuộc cấu trúc:
Order ──extends──> Root ──extends──> Object
   └──implements──> Audit

Các giai đoạn đời sống của từng kiểu:
Loading ──> Linking [verify → prepare → resolve khi thích hợp] ──> Initialization
             (resolution có thể được thực hiện sớm hoặc muộn)
```

Quan hệ cấu trúc cho biết **cần có kiểu nào để định nghĩa lớp**; các giai đoạn đời sống cho biết **JVM làm những việc gì đối với từng kiểu**. Hai trục phải được phân tích riêng.

## 9. Cách chẩn đoán khi thiếu lớp cha hoặc interface cha

Khi gặp lỗi class loading ở một ứng dụng lớn, đi theo trình tự sau thay vì chỉ thêm JAR ngẫu nhiên:

1. **Xác định lớp đang được yêu cầu nạp.** Ghi binary name trong `Class.forName`, `ClassLoader.loadClass`, stack trace hoặc điểm tạo đối tượng.
2. **Đọc toàn bộ exception chain.** Phân biệt tên `Child` được yêu cầu với tên `Parent`/interface bị thiếu trong cause.
3. **Xác định định nghĩa `extends` và `implements`.** Có thể dùng `javap -p -verbose Child.class` để xem `super_class` và `interfaces` ở bytecode, kể cả khi code nguồn không còn.
4. **Kiểm tra classpath/module path thực sự của tiến trình.** Một file JAR tồn tại trên đĩa không chứng minh loader hiện tại có quyền truy cập tới nó.
5. **Kiểm tra loader và delegation.** In `clazz.getClassLoader()` với những lớp đã nạp được, xác định parent loader và tập URL/JAR của plugin loader tùy biến.
6. **Theo dõi log loading.** Với HotSpot/JDK 17+, có thể chạy `java -Xlog:class+load=info ...` để biết nhiều lớp được lấy từ nguồn nào. Log nạp lớp và dòng `<clinit>` không có cùng ý nghĩa.
7. **Nếu lỗi liên quan module**, kiểm tra readability, exports/opens theo tình huống. Đây là ràng buộc bổ sung; không làm mất các yêu cầu nạp kiểu cha.
8. **Nếu có version conflict**, đối chiếu JAR đã compile với JAR runtime; đặc biệt lưu ý việc class đổi từ interface sang class, đổi superclass hoặc đổi quan hệ implements.
9. **Kiểm tra trường hợp hot reload/plugin reload.** Một loader mới có thể định nghĩa `Child` mới, trong khi host còn giữ interface hoặc instance từ loader cũ; cần xem danh tính kiểu runtime và tham chiếu giữ loader cũ.

Với kiểu đã nạp được, đoạn hỗ trợ sau cung cấp manh mối:

```java
static void inspect(Class<?> type) {
    System.out.println("Type: " + type.getName());
    System.out.println("Loader: " + type.getClassLoader());
    Class<?> parent = type.getSuperclass();
    System.out.println("Superclass: " +
            (parent == null ? "<none>" : parent.getName()));
    for (Class<?> iface : type.getInterfaces()) {
        System.out.println("Direct interface: " + iface.getName());
    }
}
```

Đây là **phương thức hỗ trợ**, đưa vào một class đang chạy rồi gọi `inspect(SomeType.class)`. `getInterfaces()` trả về các interface **trực tiếp**; muốn xem transitive closure cần duyệt thêm interface cha của từng interface. `getSuperclass()` trả về `null` cho `Object`, interface và một số kiểu đặc biệt như primitive/array theo API; đó không phải bằng chứng tải class thất bại.

## 10. Những nhầm lẫn thường gặp

- **“File `Child.class` tồn tại là đủ để nạp được `Child`.”** Sai nếu superclass/superinterface trực tiếp hoặc gián tiếp không tải được.
- **“JVM chỉ nạp superclass, không cần superinterface.”** Sai: interface cha trực tiếp và chuỗi interface cha của chúng cũng là quan hệ cấu trúc cần xử lý.
- **“Superinterface nạp đồng nghĩa static field của nó đã khởi tạo.”** Sai: loading và initialization khác nhau; quy tắc default method chỉ chi phối một số tình huống khởi tạo class.
- **“Đối tượng chưa được tạo nghĩa là chưa load cha.”** Sai: JVM có thể load/link lớp trước active use, nhất là qua reflection hoặc verifier.
- **“Cứ interface có default method là lúc load `Child` nó lập tức chạy initializer.”** Sai: kích hoạt khởi tạo class mới liên quan đến quy tắc khởi tạo superclass/superinterface; loading riêng không kích hoạt.
- **“Cùng tên interface là đủ cho `implements` xuyên plugin.”** Sai: cần cùng danh tính kiểu ở runtime, bao gồm defining loader.
- **“Thiếu superclass phải ra `ClassNotFoundException`.”** Thường có thể xuất hiện `NoClassDefFoundError` cho phụ thuộc; phải xem toàn bộ cause.
- **“Mọi kiểu xuất hiện trong khai báo field/method đều bắt buộc load ngay cùng lớp con.”** Sai: phụ thuộc cấu trúc kế thừa và resolution tham chiếu khác có cơ chế, thời điểm riêng.

## 11. Câu hỏi tự kiểm tra

**1. `class Invoice extends Document implements Serializable`: khi nạp `Invoice`, kiểu nào là phụ thuộc cấu trúc?**

`Document` (và chuỗi superclass cần thiết), `Serializable` (cùng các superinterface liên quan, nếu có). `java.lang.Object` xuất hiện ở đỉnh chuỗi class trừ bản thân `Object`.

**2. `Class.forName("Invoice", false, loader)` thành công có bảo đảm `static { ... }` trong `Document` đã chạy?**

Không. Nạp và liên kết khác với khởi tạo. Tham số `false` yêu cầu không chủ động khởi tạo lớp được chọn; nếu một kiểu đã được khởi tạo do hành động trước đó, API không hoàn tác trạng thái ấy.

**3. Nếu `Invoice implements A, B`, `B extends C` và `C` biến mất khỏi runtime?**

Không thể coi `B` là interface hoàn chỉnh khi thiếu superinterface bắt buộc của nó, vì vậy việc nạp `Invoice` có thể thất bại khi giải quyết chuỗi phụ thuộc cấu trúc.

**4. Interface `Feature` đã được load, nhưng initializer của `Feature` chưa chạy. Có mâu thuẫn không?**

Không. Interface có thể được load trước khi được initialize. Việc khởi tạo một class implements `Feature` không nhất thiết khởi tạo `Feature` nếu `Feature` không khai báo default method và chưa được dùng theo cách khác kích hoạt initialization.

**5. Hai plugin đều `implements api.Plugin`, vì sao có plugin không cast được sang `api.Plugin` của host?**

Vì `api.Plugin` trong một plugin có thể do loader khác tự định nghĩa, tạo kiểu runtime riêng. Host và plugin cần thống nhất interface API/SPI do loader chia sẻ định nghĩa.

## 12. Kết luận và tài liệu đối chiếu

Để định nghĩa một class/interface hợp lệ, JVM phải xử lý những **superclass và superinterface mà metadata khai báo** cùng các phụ thuộc kế thừa đệ quy, kiểm tra tính hợp lệ của quan hệ đó trong môi trường class loader. Việc này thuộc **loading/creation** và liên hệ với **linking**; nó không tương đương việc chạy **initialization** của mọi kiểu cha.

Khi gặp lỗi nạp lớp con, hãy bắt đầu từ ba câu hỏi: **kiểu cha nào là bắt buộc, loader nào đang tìm/định nghĩa kiểu đó, và lỗi xảy ra trong loading, linking hay initialization?**

Tài liệu chuẩn để đọc sâu:

- *Java Virtual Machine Specification*, §5.3 “Creation of Classes and Interfaces”: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3
- *Java Virtual Machine Specification*, §5.4 “Linking”: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4
- *Java Virtual Machine Specification*, §5.5 “Initialization”: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5
- *Java Language Specification*, §12.4 “Initialization of Classes and Interfaces”: https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-lifecycle-004">Verification: kiểm tra cấu trúc và tính hợp lệ của bytecode</a>

<details>
<summary>Xem chi tiết</summary>

# Verification: kiểm tra cấu trúc và tính hợp lệ của bytecode Java

**Verification** (xác minh bytecode) là công đoạn trong **linking** của vòng đời lớp trên JVM. Nó kiểm tra rằng biểu diễn nhị phân của một lớp hoặc interface phù hợp các ràng buộc của Java Virtual Machine Specification (JVMS), đặc biệt là các quy tắc về **kiểu của giá trị, operand stack, biến cục bộ và luồng điều khiển**. Mục tiêu là ngăn bytecode không hợp lệ thực hiện những thao tác phá vỡ các giả định an toàn kiểu của JVM.

Điểm quan trọng: tệp `.class` *có thể được đọc và có cấu trúc nhận diện được* nhưng vẫn bị từ chối vì nội dung lệnh không hợp lệ. Ngược lại, một lớp qua được verification vẫn có thể ném exception khi chạy hoặc có lỗi nghiệp vụ.

## 1. Verification nằm ở đâu trong vòng đời lớp?

Sơ đồ khái quát:

```text
Loading (tìm/đọc dữ liệu lớp, tạo Class)
    |
    v
Linking
    |-- Verification  : kiểm tra ràng buộc cấu trúc và an toàn kiểu của bytecode
    |-- Preparation   : cấp và khởi tạo giá trị mặc định cho static fields
    '-- Resolution    : chuyển symbolic references thành liên kết trực tiếp (*)
    |
    v
Initialization        : chạy <clinit> khi điều kiện khởi tạo được kích hoạt
    |
    v
Sử dụng lớp             : gọi phương thức, tạo đối tượng, truy cập trường...
```

`(*)` **Resolution có thể diễn ra dần (lazy)** theo từng tham chiếu, tùy quy tắc của JVM; sơ đồ thể hiện các phần việc thuộc linking, không cam kết thứ tự thực hiện mọi thao tác hoặc thời điểm xuất hiện lỗi trong mọi triển khai.

Cần phân biệt:

- **Loading:** bộ nạp tìm bytecode và định nghĩa lớp. Lớp có thể đến từ directory, JAR, module, mạng hoặc mảng byte do custom `ClassLoader` cung cấp.
- **Verification:** JVM đánh giá lớp có đáp ứng các ràng buộc class file và bytecode hay không. Không cần thực thi logic nghiệp vụ để kiểm tra.
- **Preparation:** chuẩn bị `static` fields với giá trị mặc định (không đồng nghĩa chạy các phép gán trong mã `static` của lớp).
- **Resolution:** giải quyết symbolic references tới class, field, method. Một số lỗi liên kết khác có thể phát sinh tại đây hoặc khi sử dụng.
- **Initialization:** chạy bộ khởi tạo lớp `<clinit>` khi cần. Verification hoàn tất trước khi lớp được khởi tạo, nhưng không nên suy diễn rằng mọi lỗi liên quan đến một dependency sẽ xuất hiện ngay khi lớp đầu tiên được load.

Ví dụ, `Class.forName(name, false, loader)` yêu cầu tải và liên kết lớp mà **không yêu cầu khởi tạo lớp**. Thời điểm một verifier thực sự phân tích từng phương thức có thể phụ thuộc cách triển khai và thao tác đang thực hiện. Việc chỉ thấy đối tượng `Class<?>` không phải bằng chứng về thời điểm mọi bước kiểm tra đã diễn ra.

## 2. JVM kiểm tra những gì?

Verification không phải một lệnh duy nhất mà là tập kiểm tra trên thông tin của class file và bytecode. Có thể chia để học thành hai nhóm:

| Nhóm kiểm tra | Câu hỏi điển hình | Ví dụ bị từ chối |
|---|---|---|
| Định dạng/cấu trúc class file | Header, constant pool, bảng phương thức, thuộc tính, chỉ số và cấu trúc có hợp lệ? | Magic bytes `CAFEBABE` bị hỏng hoặc constant-pool entry sai định dạng |
| Ràng buộc ngữ nghĩa và an toàn kiểu | Các bytecode instructions, stack, locals, nhánh và kiểu trả về có nhất quán? | Phương thức khai báo trả `int` nhưng dùng `areturn` với giá trị `int` |

Một số chi tiết quan trọng:

1. **Định dạng lớp:** kiểm tra cấu trúc class file (magic, phiên bản hỗ trợ, constant pool, field/method metadata, Code attribute và các ràng buộc tương ứng). Không phải mọi lỗi định dạng đều được báo bằng `VerifyError`; lỗi cấu trúc thường dẫn đến `ClassFormatError` hoặc một lớp lỗi liên kết phù hợp.
2. **Operand stack:** xác định kiểu và số phần tử trên stack ở từng điểm chương trình; không được pop một giá trị chưa có hoặc dùng sai loại toán hạng.
3. **Local variables:** dùng biến local phải phù hợp kiểu đang lưu; đọc biến chưa được gán trạng thái hợp lệ hoặc sử dụng nhầm slot làm loại khác có thể bị từ chối.
4. **Return/argument typing:** `ireturn` chỉ dùng trong ngữ cảnh kiểu kết quả integer phù hợp; `areturn` dành cho giá trị tham chiếu; đối số của lời gọi method phải phù hợp descriptor của method.
5. **Luồng điều khiển:** nhảy tới offset lệnh hợp lệ; tại các điểm nhiều nhánh nhập lại, các kiểu và trạng thái stack/locals phải có quan hệ hợp lệ. Exception handlers cũng có điểm vào và trạng thái stack riêng.
6. **Đối tượng chưa khởi tạo:** verifier theo dõi các giá trị tạo bằng `new` trước lời gọi constructor `<init>`; không cho sử dụng đối tượng chưa khởi tạo như đối tượng bình thường theo cách trái quy tắc.
7. **Khả năng truy cập và tham chiếu:** class/interface, method/field reference và một số ràng buộc kế thừa phải hợp lệ; tuy nhiên **resolution và access checks tại runtime** là phần riêng của cơ chế liên kết/thực thi, nên không quy mọi `IllegalAccessError` hay `NoSuchMethodError` cho verification.

Verification kiểm tra **kiểu trừu tượng** của giá trị chứ không cần biết kết quả số học thực tế hoặc nội dung đối tượng tại runtime. Việc `int` có giá trị 42 hay 100 thường không liên quan đến việc `ireturn` có hợp kiểu hay không.

## 3. Theo dõi operand stack qua bytecode

Xét phương thức Java:

```java
static int add(int a, int b) {
    return a + b;
}
```

Trong bytecode thường xuất hiện trình tự:

```text
0: iload_0
1: iload_1
2: iadd
3: ireturn
```

Verifier suy luận trạng thái kiểu:

| Lệnh | Operand stack trước | Operand stack sau |
|---|---|---|
| `iload_0` | `[]` | `[int]` |
| `iload_1` | `[int]` | `[int, int]` |
| `iadd` | `[int, int]` | `[int]` |
| `ireturn` | `[int]` | Trả `int` ra khỏi phương thức |

Các local đầu vào tương ứng hai tham số `int`, với phương thức `static` không có slot `this`. `max_stack` và `max_locals` là metadata trong `Code` attribute mô tả giới hạn tài nguyên cho phương thức; verifier cũng kiểm tra việc sử dụng thực tế có nhất quán với chúng.

Nếu thay `ireturn` bằng `areturn`, JVM không thể chấp nhận một `int` trên stack như tham chiếu trả về. Đây là lỗi an toàn kiểu, dù chuỗi byte mã lệnh vẫn nằm trong một class file có định dạng hợp lệ.

### Khi có nhiều nhánh

Với `if`, `switch`, `goto` hoặc exception handler, các luồng điều khiển có thể hội tụ. Kiểu của local variables và operand stack tại điểm hội tụ phải hợp lệ cho **mọi nhánh có thể đi tới**. JVM dùng quy tắc phân tích luồng dữ liệu dựa trên trạng thái kiểu.

Trong class file hiện đại, thuộc tính **`StackMapTable`** cung cấp các *stack-map frames* mô tả trạng thái kiểm tra kiểu ở những offset quan trọng. Với các phiên bản class file áp dụng cơ chế type checking này, verifier dùng những frame và quy tắc chuyển trạng thái để kiểm tra tính nhất quán; không đơn thuần tin rằng compiler luôn phát đúng. Không phải mọi phương thức bắt buộc chứa một danh sách frame dài: phương thức tuyến tính đơn giản có thể không có entry tường minh trong `StackMapTable`.

Ví dụ bytecode giả định **sai kiểu** (đây không phải class file hoàn chỉnh):

```text
method: public static int answer()
    bipush 42   // stack: [int]
    areturn     // sai: areturn đòi hỏi reference và kiểu kết quả phù hợp
```

## 4. Thực hành Java 17+: làm hỏng bytecode để quan sát verifier

Ví dụ dưới đây tạo một class **hợp lệ** bằng `javac`, đọc chính tệp `.class` đã biên dịch, sau đó thử hai thay đổi **chỉ trên bản sao `byte[]` trong bộ nhớ**:

- Đổi một byte ở đầu class file để quan sát lỗi **`ClassFormatError`** (định dạng hỏng).
- Đổi opcode trả về `ireturn` thành `areturn` để quan sát **`VerifyError`** (mã có cấu trúc đọc được nhưng không an toàn kiểu).

Việc tạo class loader riêng cho phép `defineClass` cùng tên nhị phân trong một namespace lớp khác; JVM xác định danh tính lớp bằng **(binary name, defining class loader)**. Chương trình không ghi đè tệp `.class` trên đĩa và không phụ thuộc thư viện bytecode ngoài.

Tạo tệp **`VerificationExample.java`**:

```java
import java.io.InputStream;
import java.util.Objects;

public final class VerificationExample {
    private static final class ByteArrayLoader extends ClassLoader {
        ByteArrayLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String name, byte[] data) {
            return defineClass(name, data, 0, data.length);
        }
    }

    public static void main(String[] args) throws Exception {
        byte[] original;
        try (InputStream in = VerificationExample.class
                .getResourceAsStream("/ExampleTarget.class")) {
            original = Objects.requireNonNull(
                    in, "ExampleTarget.class").readAllBytes();
        }

        byte[] modified = original.clone();
        int found = 0;
        for (int i = 0; i < modified.length - 2; i++) {
            if ((modified[i] & 0xff) == 0x10
                    && (modified[i + 1] & 0xff) == 0x2a
                    && (modified[i + 2] & 0xff) == 0xac) {
                modified[i + 2] = (byte) 0xb0;
                found++;
            }
        }
        if (found != 1) {
            throw new IllegalStateException(
                    "Expected exactly one opcode sequence, got " + found);
        }

        System.out.println("Original: " + ExampleTarget.answer());

        byte[] malformed = original.clone();
        malformed[0] = 0;
        try {
            new ByteArrayLoader(VerificationExample.class.getClassLoader())
                    .define("ExampleTarget", malformed);
            System.out.println("Unexpected acceptance");
        } catch (ClassFormatError expected) {
            System.out.println("Malformed header: ClassFormatError");
        }

        try {
            Class<?> changed =
                    new ByteArrayLoader(VerificationExample.class.getClassLoader())
                            .define("ExampleTarget", modified);
            changed.getMethod("answer").invoke(null);
            System.out.println("Unexpected acceptance");
        } catch (VerifyError expected) {
            System.out.println("Modified: VerifyError");
        }
    }
}

class ExampleTarget {
    public static int answer() {
        return 42;
    }
}
```

Biên dịch bằng JDK 17+ (cờ `--release 17` khiến class file có phiên bản mục tiêu Java 17), rồi chạy từ thư mục chứa tệp nguồn:

```bash
javac --release 17 -d out VerificationExample.java
java -cp out VerificationExample
```

Với `javac` tiêu chuẩn trên JDK 17+ và HotSpot, đầu ra là:

```text
Original: 42
Malformed header: ClassFormatError
Modified: VerifyError
```

**Giải thích chính xác ví dụ:**

- `ExampleTarget.answer()` là phương thức `static int` trả 42. Trình biên dịch `javac` sinh chuỗi lệnh thường gặp `bipush 42` (`10 2a` theo mã byte hex) và `ireturn` (`ac`). Chương trình tìm đúng chuỗi `10 2a ac` trong bản class file thử nghiệm; nếu không tìm được **đúng một** vị trí, nó chủ động dừng để tránh minh họa sai.
- `areturn` có opcode `b0`; sau khi thay, stack vẫn chứa `int` do `bipush` sinh ra. Vì thế phương thức không tuân thủ yêu cầu kiểu trả về. JVM có thể phát hiện lỗi khi định nghĩa/liên kết lớp hoặc khi thao tác tiếp theo buộc kiểm tra, nhưng trong cả hai trường hợp phần `catch (VerifyError ...)` thể hiện lỗi cần quan sát.
- `malformed[0] = 0` phá magic `CA FE BA BE` tại đầu class file, nên báo lỗi định dạng. Không gọi lỗi này là “verifier phát hiện return type sai”.
- Một `ByteArrayLoader` khác được tạo cho mỗi lần `define`. Class đã nạp qua system loader và class định nghĩa bởi loader con có thể cùng binary name nhưng là **hai lớp khác nhau** đối với JVM.

Đây là **bài thử có chủ đích gắn với bytecode cụ thể mà `javac` sinh ra**, không phải bộ chỉnh sửa class file tổng quát: tìm chuỗi byte trên toàn tệp không phân tích `Code` attribute. Với compiler khác, bytecode khác hoặc tối ưu hóa khác, chuỗi có thể không xuất hiện/không duy nhất; khi đó bài thử dừng với `IllegalStateException`. Khi thao tác bytecode thực tế, nên dùng thư viện hiểu cấu trúc class file như ASM và kiểm tra kết quả bằng JVM.

### Xem bytecode và metadata trước khi sửa

Sau khi biên dịch, dùng:

```bash
javap -classpath out -c ExampleTarget
javap -classpath out -v ExampleTarget
```

`-c` hiển thị các instruction/offset, ví dụ thân method `answer` gồm `bipush 42` rồi `ireturn`. `-v` giúp xem version, constant pool, `Code`, `stack`/`locals` và các thuộc tính khác (nếu có). Toàn bộ đầu ra `javap -v` còn chứa thông tin compiler và môi trường nên không nên dùng làm stdout cố định trong kiểm thử.

## 5. `ClassFormatError`, `VerifyError` và các lỗi liên kết khác

**`VerifyError`** là subclass của `LinkageError`; nó biểu thị JVM đã thấy sự thiếu nhất quán trong class file về các ràng buộc xác minh thích hợp. Tuy nhiên không phải mọi lỗi xảy ra khi load/link một lớp đều là `VerifyError`.

| Lỗi | Ý nghĩa điển hình | Phân biệt |
|---|---|---|
| `ClassFormatError` | Class file sai định dạng hoặc vi phạm quy tắc cấu trúc | Có thể lỗi trước khi phân tích luồng kiểu của mã lệnh |
| `UnsupportedClassVersionError` | Class file dùng phiên bản không được JVM hiện tại hỗ trợ | Là một subtype của `ClassFormatError` |
| `VerifyError` | Bytecode/metadata không thỏa các ràng buộc kiểm chứng kiểu hoặc tính hợp lệ liên quan | Ví dụ dùng sai opcode return theo kiểu stack |
| `NoClassDefFoundError` | Không thể tìm/hoàn thành định nghĩa lớp phụ thuộc cần thiết trong hoàn cảnh sử dụng | Thường gắn với loading/linkage, không tự động là lỗi verifier |
| `NoSuchMethodError`, `NoSuchFieldError` | Symbolic reference không giải quyết được tới member được tham chiếu | Gắn với resolution/compatibility; thường do thay đổi binary dependency |
| `IncompatibleClassChangeError` | Thay đổi không tương thích ở mối quan hệ binary của các lớp/member | Lỗi liên kết, không đồng nghĩa mã bytecode sai kiểu stack |
| `ExceptionInInitializerError` | Initializer tĩnh gây exception theo quy tắc JVM | Thuộc initialization, sau giai đoạn verification cần thiết |

Sửa nhanh theo đúng nguyên nhân: nếu `ClassFormatError`, kiểm tra bytecode, tooling và file nguồn; nếu `VerifyError`, tìm đoạn bytecode hoặc transformation tạo trạng thái stack/locals không hợp lệ; nếu lỗi resolution, kiểm tra JAR/classpath/module và phiên bản dependency. Không “sửa” `VerifyError` bằng cách bắt nó rồi tiếp tục dùng lớp bị từ chối.

## 6. Trường hợp hay gặp trong dự án thực tế

### 6.1. Bytecode được tạo/chỉnh sửa bởi công cụ

Các thư viện proxy, instrumentation, weaving, code generation hoặc agent có thể sinh bytecode lúc build/runtime. Một thay đổi trong method descriptor, điều khiển nhánh, stack map frames hay thứ tự hướng constructor có thể làm mã biến đổi bị từ chối dù nguồn Java ban đầu biên dịch được.

Quy trình kiểm tra hữu ích:

1. Xác định binary name của lớp lỗi và **defining class loader** liên quan.
2. Lưu/thu thập chính **bytes sau transformation** nếu hệ thống cho phép, không chỉ file `.class` nguồn.
3. Dùng `javap -v -c` (hoặc công cụ class-file/ASM tương đương) trên class file tương ứng để soi descriptor, instruction, `Code` và `StackMapTable`.
4. Kiểm tra chỗ nhánh hội tụ, stack height, kiểu return, method calls và ràng buộc constructor.
5. Tái hiện dưới cùng Java version và bộ loader/agent, rồi sửa logic sinh bytecode. Không bỏ verification để che lỗi.

### 6.2. Class file phiên bản quá mới

Ví dụ class file target Java 21 chạy trên JVM Java 17 có thể gây `UnsupportedClassVersionError`; đó là vấn đề tương thích **class-file version**, không phải vì verifier chứng minh phép cộng kiểu `int` sai. Cần biên dịch lại với `--release` phù hợp hoặc nâng runtime.

### 6.3. Vấn đề chỉ xuất hiện sau một nâng cấp thư viện

Một class có thể đi qua những kiểm tra bytecode cơ bản nhưng fail khi resolution của phương thức/phụ thuộc diễn ra. Chẳng hạn class vẫn tham chiếu một method đã bị xóa trong phiên bản JAR mới và ném `NoSuchMethodError`. Điều này khác với code bị từ chối vì local/stack type. Thu thập đầy đủ exception chain, loader identity và dependency graph để phân loại đúng.

### 6.4. Cùng tên lớp, khác loader

JVM coi `(binary name, defining class loader)` là danh tính của một runtime class. Hai lớp có tên giống nhau do hai loader khác nhau định nghĩa **không** tự động thay thế được cho nhau. Lỗi `ClassCastException` hoặc `LinkageError` trong bối cảnh đó không đồng nghĩa bytecode mất tính hợp lệ; còn phải phân tích namespace lớp và loader constraints.

## 7. Verification đảm bảo điều gì, và không đảm bảo điều gì?

Verification là lớp bảo vệ thiết yếu cho runtime khi JVM tiếp nhận class file, nhất là bytecode ngoài hoặc bytecode đã biến đổi. Nó duy trì các quy tắc an toàn kiểu được nêu bởi mô hình JVM: method không thể tùy ý dùng một integer như object reference chỉ bằng cách phát lệnh sai kiểu.

Nhưng verification **không chứng minh** rằng:

- Code đáp ứng nghiệp vụ hoặc trả đúng kết quả mong muốn.
- Không thể có `NullPointerException`, `ClassCastException`, `ArrayIndexOutOfBoundsException`, `ArithmeticException` hoặc exception hợp lệ khác khi chạy. Những runtime checks vẫn cần thiết.
- Không có deadlock, data race, lỗi concurrency, resource leak hoặc vòng lặp vô hạn.
- Người dùng hoặc process có quyền truy cập dữ liệu: authorization, sandboxing và chính sách bảo mật ứng dụng là vấn đề khác.
- Native code, JNI, `Unsafe` hoặc cơ chế đặc quyền khác đều bị điều tiết chỉ bởi verifier theo cùng các giả định của bytecode Java thông thường.

**Không cần hiểu verification như một sandbox an ninh toàn diện.** Nó kiểm soát một tập thuộc tính ở cấp bytecode, còn runtime exception, bộ nạp, quyền truy cập và logic ứng dụng vận hành ở các tầng khác.

## 8. Kiểm tra hiểu bài

1. **Vì sao `bipush 42; areturn` sai?** Vì `bipush` đẩy `int` nhưng `areturn` cần toán hạng tham chiếu, đồng thời method khai báo trả về `int`.
2. **Một file có magic `CAFEBABE` đúng có chắc qua verification?** Không. Header đúng chưa chứng minh constant pool, code, stack maps và dataflow hợp lệ.
3. **`StackMapTable` có thay thế mọi kiểm tra runtime?** Không. Nó giúp chứng minh trạng thái kiểu bytecode; các kiểm tra giá trị/điều kiện lúc chạy vẫn diễn ra.
4. **`UnsupportedClassVersionError` có phải `VerifyError`?** Không; nó là một subclass của `ClassFormatError`.
5. **Verification có chạy phương thức `main` của lớp để xem kết quả?** Không. Nó kiểm tra class file và tính hợp lệ kiểu của bytecode, không chạy thuật toán nghiệp vụ để đoán kết quả.
6. **Vì sao một proxy/agent có thể gây `VerifyError` dù mã Java biên dịch được?** Vì bytecode sau transformation khác bytecode `javac` tạo ra và có thể chứa stack frames, descriptor hay instructions không nhất quán.

**Ghi nhớ:** Trong vòng đời lớp, **verification thuộc linking và kiểm tra tính hợp lệ bytecode trước khi lớp được sử dụng theo cách đòi hỏi liên kết hoàn tất**. Phân biệt lỗi định dạng `ClassFormatError` với lỗi xác minh `VerifyError` và lỗi resolution/initialization để tìm đúng nguyên nhân, nhất là khi làm việc với class loader, instrumented classes và bytecode được sinh động.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-lifecycle-005">Preparation: cấp phát và thiết lập giá trị mặc định cho static field</a>

<details>
<summary>Xem chi tiết</summary>

# Preparation: cấp phát và thiết lập giá trị mặc định cho static field

## 1. Preparation nằm ở đâu trong vòng đời lớp?

Khi JVM cần sử dụng một class hoặc interface, một phần công việc của nó gồm **Loading → Linking → Initialization**. **Preparation (chuẩn bị)** là một công đoạn thuộc **Linking**, sau khi JVM kiểm tra tính hợp lệ của class và trước khi class được khởi tạo.

~~~text
           Loading
     (tạo biểu diễn lớp từ bytecode)
                │
                ▼
           Linking
       ┌─────────────────┐
       │ Verification    │  Kiểm tra tính hợp lệ của bytecode
       │ Preparation     │  Tạo static field, đặt giá trị mặc định
       │ Resolution      │  Liên kết các tham chiếu ký hiệu khi cần
       └─────────────────┘
                │
                ▼
         Initialization
       (gán hằng ConstantValue theo quy tắc JVM;
        chạy <clinit> nếu có)
                │
                ▼
         Sử dụng lớp
~~~

Sơ đồ biểu diễn **các công việc về mặt khái niệm**: JVM có thể thực hiện *resolution* sớm hoặc trì hoãn theo từng tham chiếu; không phải mọi tham chiếu đều được resolve xong trước khi bắt đầu chạy chương trình. Điều bắt buộc là class/interface phải được **verify và prepare trước khi initialize**.

**Preparation làm gì?** JVM tạo nơi lưu trữ các field `static` do class/interface khai báo và gán **giá trị mặc định của kiểu** cho chúng. Phần này không chạy mã Java do lập trình viên viết, không gọi constructor và không thực thi những biểu thức phía sau dấu `=` trong khai báo field.

Ví dụ về các khai báo:

~~~java
static int count = 100;
static boolean ready = true;
static Object cache = new Object();
~~~

Sau Preparation, theo mô hình giá trị mặc định, các field lần lượt có giá trị **`0`, `false`, `null`**. Khi **Initialization** diễn ra, các biểu thức khai báo mới lần lượt gán `100`, `true`, và tham chiếu tới đối tượng mới.

Đó là sự phân biệt cốt lõi: **Preparation đặt trạng thái ban đầu theo kiểu; Initialization thực hiện mã tạo trạng thái mà ứng dụng mong muốn.**

## 2. JVM cấp phát static field và gán mặc định như thế nào?

Một field được khai báo `static` gắn với **class/interface đã được định nghĩa trong JVM**, không gắn với từng instance của class. Trong giai đoạn Preparation, JVM tạo các ô lưu trữ tương ứng và đặt mặc định theo kiểu:

| Loại field `static` | Giá trị mặc định sau Preparation | Ví dụ |
|---|---|---|
| `byte`, `short`, `int`, `long` | Số nguyên `0` tương ứng với kiểu | `static long timeout = 99L;` → trước initializer là `0L` |
| `float`, `double` | Số thực `+0.0` tương ứng với kiểu | `static double rate = 1.5;` → trước initializer là `0.0` |
| `char` | Ký tự null `'\u0000'` (mã Unicode U+0000) | `static char marker = 'A';` |
| `boolean` | `false` | `static boolean ready = true;` |
| Kiểu tham chiếu (ví dụ `String`, `Integer`, mảng, đối tượng) | `null` | `static String title = "Hello";` |
| Field `static` không khai báo initializer | Vẫn là giá trị mặc định sau cả Initialization, trừ khi có lệnh gán ở nơi khác | `static int attempts;` giữ `0` |

Lưu ý:

- Những giá trị này là **mặc định do ngữ nghĩa Java/JVM quy định**, không phải dữ liệu rác hay ngẫu nhiên.
- `static String text = "abc";` có giá trị `null` tại Preparation; gán tham chiếu chuỗi diễn ra ở Initialization. Tương tự `static Object value = new Object();` chưa gọi `new Object()` trong Preparation.
- `static int[] numbers = new int[3];`: **field tham chiếu mảng** mặc định là `null` tại Preparation. Mảng ba phần tử chỉ được tạo khi biểu thức `new int[3]` được thực thi ở Initialization; bản thân các phần tử của mảng mới có mặc định `0`.
- Field **không có `static`** thuộc mỗi đối tượng riêng. Nó nhận giá trị mặc định khi cấp phát instance theo quy trình tạo đối tượng, **không phải** khi JVM Preparation một class.
- Mô tả “static nằm chính xác ở Metaspace” hoặc “static nhất định nằm trong Method Area vật lý” dễ gây hiểu sai. Đặc tả định nghĩa **ngữ nghĩa lưu trữ và giá trị**, còn chi tiết bố trí bộ nhớ là quyết định triển khai JVM. Metaspace trong HotSpot chủ yếu chứa metadata của lớp; không đồng nhất mọi static field với vùng Metaspace.

Preparation còn có thể chuẩn bị một số cấu trúc nội bộ (chẳng hạn bảng hỗ trợ gọi phương thức). Đó là công việc của JVM, không phải việc thực thi `static { ... }` của chương trình.

## 3. Phân biệt Preparation, Initialization và thứ tự gán

Xem class sau dưới góc độ ba mốc thời gian:

~~~java
class Settings {
    static int attempts;               // Không có initializer
    static long timeout = 5000L;       // Có initializer
    static boolean enabled = true;     // Có initializer
    static String region = "ap-south"; // Có initializer

    static {
        System.out.println("Settings initialized");
    }
}
~~~

**Tại Preparation:**

~~~text
attempts = 0
timeout  = 0L
enabled  = false
region   = null
~~~

**Trong Initialization:**

1. Giá trị mặc định vẫn tồn tại khi bắt đầu khởi tạo.
2. JVM thực hiện các static field initializer và static block theo thứ tự xuất hiện trong mã nguồn sau khi đã xử lý các bước khởi tạo đặc biệt (nếu có).
3. `timeout` trở thành `5000L`, `enabled` thành `true`, `region` thành `"ap-south"`.
4. Static block in dòng `Settings initialized`; `attempts` vẫn là `0` vì không có mã nào gán giá trị khác.

**Sau Initialization thành công:** các field có trạng thái mà các initializer và static block đã xây dựng. Không nên hiểu Preparation là chạy `static { ... }`, cũng không nên cho rằng Java bỏ qua giá trị mặc định chỉ vì field có khai báo `= ...`.

Một chi tiết quan trọng: mã đang chạy **ngay trong quá trình khởi tạo của class** đôi khi có thể thấy các field chưa được gán bằng initializer và vẫn giữ giá trị mặc định. Đây là cơ chế sẽ được kiểm chứng trong ví dụ bên dưới, **không đồng nghĩa** với việc mã ứng dụng có thể tự do đọc static field trước khi class được khởi tạo.

## 4. Ví dụ Java 17+: thấy giá trị mặc định khi Initialization đang diễn ra

Ví dụ gồm một file nguồn độc lập **`PreparationDemo.java`**. Class lồng `Data` có các field được khai báo theo thứ tự có chủ ý.

~~~java
public class PreparationDemo {
    static class Data {
        // Không có initializer: sau Preparation là 0 và tiếp tục giữ 0.
        static int neverAssigned;

        // Hàm này chạy khi Initialization bắt đầu; các field bên dưới
        // chưa chạy initializer nên vẫn đang giữ giá trị mặc định.
        static int amount = calculate();

        static long timeout = 99L;
        static boolean enabled = true;
        static String label = "ready";

        // Hằng số nguyên thủy được biên dịch thành ConstantValue.
        static final int LIMIT = 42;

        // Integer không phải "constant variable" của Java.
        static final Integer WRAPPED = Integer.valueOf(42);

        static {
            System.out.println("Static block: initialization complete");
        }

        private static int calculate() {
            System.out.println(
                "While initializing: timeout=" + timeout
                + ", enabled=" + enabled
                + ", label=" + label
            );
            return 7;
        }
    }

    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName(
            "PreparationDemo$Data",
            false,
            PreparationDemo.class.getClassLoader()
        );

        System.out.println("Loaded type: " + type.getName());

        // Hằng compile-time được javac nhúng vào nơi sử dụng:
        // truy cập này không kích hoạt khởi tạo Data.
        System.out.println("Constant only: " + Data.LIMIT);

        // getstatic với field không phải constant variable:
        // điểm này làm Data phải được khởi tạo.
        System.out.println("Amount: " + Data.amount);

        System.out.println(
            "After initialization: timeout=" + Data.timeout
            + ", enabled=" + Data.enabled
            + ", label=" + Data.label
        );

        System.out.println("Unassigned: " + Data.neverAssigned);
        System.out.println("Wrapped: " + Data.WRAPPED);
    }
}
~~~

Từ thư mục chứa `PreparationDemo.java`, chạy trên JDK 17+:

~~~bash
javac -encoding UTF-8 PreparationDemo.java
java PreparationDemo
~~~

**Stdout:**

~~~text
Loaded type: PreparationDemo$Data
Constant only: 42
While initializing: timeout=0, enabled=false, label=null
Static block: initialization complete
Amount: 7
After initialization: timeout=99, enabled=true, label=ready
Unassigned: 0
Wrapped: 42
~~~

### Vì sao output theo đúng thứ tự này?

**Bước 1 — Nạp/liên kết mà chưa khởi tạo.** `Class.forName("PreparationDemo$Data", false, loader)` yêu cầu tìm, nạp và liên kết class mà không chủ động initialize class đó. Chỉ in tên lớp **không phải** hoạt động khởi tạo class `Data`. Vì thế không có dòng output từ `calculate()` hoặc static block trước dòng `Loaded type`.

**Bước 2 — Đọc hằng số compile-time.** `Data.LIMIT` là một *constant variable* (`static final int` có initializer là biểu thức hằng). Với mã nguồn này, `javac` thay biểu thức sử dụng bằng giá trị `42` trong bytecode của caller; câu lệnh in hằng số không làm `Data` initialize. Đừng suy ra rằng *mọi* field `static final` đều hoạt động như vậy: `WRAPPED` là `Integer` nên không phải constant variable.

**Bước 3 — Truy cập `Data.amount`.** Đây là field `static` bình thường, nên việc đọc gây **active use** và JVM phải initialize `Data` trước khi cung cấp giá trị. Ở Preparation trước đó, `amount` và các field khác đều đã có mặc định theo kiểu.

**Bước 4 — Chạy `calculate()`.** Initializer đầu tiên là `amount = calculate()`. Lúc hàm chạy, các initializer khai báo ở phía dưới cho `timeout`, `enabled` và `label` **chưa chạy**, nên phương thức nhìn thấy `0`, `false`, `null`. Gọi phương thức này là hợp lệ: trình biên dịch hạn chế một số tham chiếu trực tiếp tới static field khai báo ở phía sau trong initializer, còn đọc thông qua một phương thức được gọi ở thời điểm chạy có thể thấy mặc định.

**Bước 5 — Hoàn tất Initialization.** JVM gán `amount = 7`, tiếp tục gán `timeout = 99L`, `enabled = true`, `label = "ready"`, tạo giá trị cho `WRAPPED`, rồi chạy static block. Sau đó `Data.amount` trả về `7` cho câu lệnh đang chờ ở `main`.

**Bước 6 — Field không có initializer.** `neverAssigned` vẫn là `0`, chứng minh chương trình **không cần** tự viết `= 0` để có mặc định. Khi class đã khởi tạo xong, mọi lần truy cập tiếp theo trong ví dụ đều đọc trạng thái sau khởi tạo.

> **Giới hạn thí nghiệm:** Dòng `While initializing...` được in **trong Initialization**, khi vài initializer chưa chạy; nó không phải câu lệnh Java chạy *trong Preparation*. Theo cách sử dụng Java thông thường, một lệnh đọc `static` không phải hằng sẽ kích hoạt khởi tạo lớp trước khi trả kết quả, nên khó thể đọc trực tiếp và riêng biệt trạng thái Preparation từ mã ứng dụng.

## 5. Lưu ý về `static final` và `ConstantValue`

Đây là điểm dễ bị giải thích sai khi học Preparation. Hãy so sánh:

~~~java
static final int MAX_USERS = 100;
static final String NAME = "demo";
static final int COMPUTED = Integer.parseInt("100");
static final Integer BOXED = 100;
static final int[] VALUES = {1, 2, 3};
~~~

| Khai báo | Có phải Java *constant variable*? | Cách tạo giá trị |
|---|---|---|
| `MAX_USERS` | Có: `final`, kiểu nguyên thủy, biểu thức hằng | `javac` thường sinh thuộc tính `ConstantValue` |
| `NAME` | Có: `final String`, biểu thức hằng | `javac` thường sinh `ConstantValue` |
| `COMPUTED` | Không: biểu thức gọi phương thức | Gán khi thực thi mã khởi tạo lớp |
| `BOXED` | Không: `Integer` không thuộc nhóm kiểu constant variable | Autoboxing/gọi `Integer.valueOf` khi thực thi khởi tạo |
| `VALUES` | Không: kiểu mảng | Mảng được tạo khi thực thi khởi tạo |

**Phân biệt chính xác theo JVMS Java 17:**

- **Preparation (`5.4.2):** JVM tạo các `static` field và đặt **giá trị mặc định**; không thực thi explicit initializer của Java.
- **Initialization (`5.5, bước 6):** các field `static final` có thuộc tính `ConstantValue` được gán giá trị hằng **ngay trước khi thực thi `<clinit>`** (và trước một số bước khởi tạo tiếp theo). Đây không phải việc chạy mã trong `<clinit>`.
- **Initialization (`5.5, bước 9):** JVM thực thi `<clinit>`, tương ứng với các phép gán static cần chạy mã và static block. Với Java source thông thường, `<clinit>` theo thứ tự khai báo.

Vì vậy câu “tất cả `static final` được gán ngay trong Preparation” là **quá rộng và không phản ánh đúng thứ tự trong JVMS Java 17**. Tài liệu nhập môn đôi khi gộp phần hằng `ConstantValue` vào sơ đồ Preparation để đơn giản hóa; khi cần độ chính xác đặc tả, hãy tách **default values của Preparation** khỏi **gán `ConstantValue` lúc bắt đầu Initialization**.

Đặc biệt, việc caller in `MAX_USERS` mà không kích hoạt class initialization **không chứng minh rằng lệnh truy cập đã đọc trực tiếp một field đang ở trạng thái Preparation**. Trình biên dịch có thể đã nhúng `100` vào bytecode của caller.

## 6. Tự kiểm tra bytecode với `javap`

Ví dụ bên trên sinh ít nhất hai tệp class:

~~~text
PreparationDemo.class
PreparationDemo$Data.class
~~~

Dùng lệnh dưới đây trong **Bash** hoặc **PowerShell** (dùng dấu nháy đơn để giữ nguyên ký tự `$`):

~~~bash
javap -v 'PreparationDemo$Data'
javap -c -p 'PreparationDemo$Data'
javap -c PreparationDemo
~~~

Điều nên tìm trong output (không phụ thuộc các số index của constant pool do `javac` chọn):

1. Field `LIMIT` có thông tin tương đương `ConstantValue: int 42`.
2. Phương thức `static {};` trong phần `javap -c -p` là `<clinit>` được biểu diễn dưới dạng static initializer; nó chứa việc gọi `calculate()` và các lệnh `putstatic` để gán field cần khởi tạo tại runtime.
3. Trong `PreparationDemo.main`, việc in `Data.LIMIT` không cần `getstatic` tới field `LIMIT`; hằng đã có thể được nhúng vào chuỗi của lệnh in hoặc dùng opcode đẩy hằng, tùy cách `javac` biên dịch biểu thức nối chuỗi.
4. Việc truy cập `Data.amount` sử dụng `getstatic`, làm JVM kiểm tra và bảo đảm `Data` đã được initialize trước khi giá trị được trả về.

**Cách đọc:** `javap` hiển thị bytecode/metadata của `.class`, **không hiển thị một “phương thức preparation”**. Preparation là thao tác của JVM khi linking; `<clinit>` là mã khởi tạo lớp được biên dịch từ các static initializer.

## 7. Hỏi đáp ngắn về ranh giới của Preparation

**Preparation có tạo instance của class không?** Không. Việc đặt giá trị mặc định cho `static` field không gọi constructor của class đó. Biểu thức như `new Object()` đặt trong static initializer chỉ được thực thi khi Initialization chạy.

**Preparation có chạy static block không?** Không. `static { ... }` góp vào `<clinit>` và chạy ở Initialization.

**`static final` có luôn là giá trị được “gán sẵn” không?** Không. Chỉ các hằng `static final` thuộc kiểu nguyên thủy hoặc `String` với initializer là **constant expression** mới là constant variable trong ngữ nghĩa Java. `static final List<?>`, `static final Integer`, `static final int[]`, hay `static final int` tính từ phương thức cần runtime initialization.

**Nếu chưa gán initializer thì JVM có bỏ qua field không?** Không. Field `static` vẫn tồn tại và có mặc định. Ví dụ `static String message;` có `null`, `static boolean active;` có `false`.

**Có phải `Class.forName("X", false, loader)` tức class X mới ở Loading, chưa có Preparation?** Không. Overload này yêu cầu tìm, nạp và liên kết nhưng **không chủ động khởi tạo** class được chỉ định. Loading và Preparation là hai công việc khác nhau; “chưa Initialization” không đồng nghĩa “chưa Linking”.

**Mỗi ClassLoader có một bộ static field không?** Chính xác hơn: mỗi class được **định nghĩa riêng** bởi một loader có trạng thái static của class đó. Hai loader định nghĩa hai class cùng tên nhị phân có thể tạo ra hai runtime type và các static field độc lập. Hai loader chỉ cùng trỏ tới **một class do parent định nghĩa** thì không tự sinh hai bộ static field.

**Khi nào Preparation có thể lỗi?** Các bước linking có thể ném `LinkageError` khi phát hiện ràng buộc kiểu không hợp lệ; cấp phát cấu trúc runtime có thể gặp `OutOfMemoryError`. Còn lỗi do chạy biểu thức initializer (ví dụ phép tính/gọi phương thức thất bại) thuộc **Initialization**, không được quy nhầm về Preparation.

**Các static field luôn tồn tại mãi đến khi JVM kết thúc?** Chúng thuộc vòng đời của class đã được định nghĩa. Class có thể trở thành đối tượng được giải phóng theo quy tắc class unloading khi classloader tương ứng không còn reachable và các điều kiện khác được thỏa mãn; thời điểm và khả năng giải phóng phụ thuộc JVM. `static` không có nghĩa là một vùng nhớ “vĩnh viễn” độc lập với vòng đời class.

## 8. Ba câu tự kiểm tra

**Câu 1.** Với `static int x = 9; static boolean ok;`, ngay sau Preparation giá trị mô hình của hai field là gì? Sau Initialization khi không có mã gán thêm cho `ok` thì sao?

**Đáp án:** Sau Preparation: `x = 0` và `ok = false`. Sau Initialization: `x = 9` và `ok = false`.

**Câu 2.** Vì sao trong ví dụ `Data`, `calculate()` thấy `timeout = 0` dù mã nguồn có `timeout = 99L`?

**Đáp án:** Preparation đã đặt `timeout = 0L`. Khi Initialization gọi `calculate()` cho field `amount`, initializer của `timeout` đứng phía sau chưa được thực thi. Phải đợi phương thức quay lại rồi JVM tiếp tục gán `99L`.

**Câu 3.** Vì sao lệnh in `Data.LIMIT` không kích hoạt Initialization, còn đọc `Data.amount` lại có thể kích hoạt?

**Đáp án:** `LIMIT` là compile-time constant và có thể được nhúng vào bytecode caller, vì thế câu lệnh đó không cần truy cập field bằng `getstatic`. `amount` là field static thông thường; việc đọc nó yêu cầu class đã initialize. Đây là quy tắc **active use**, không phải dấu hiệu rằng caller đã trực tiếp đọc bộ nhớ sau Preparation.

## 9. Nguồn đặc tả để đối chiếu

- [Java Virtual Machine Specification 17 — 5.4.2 Preparation](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.2): tạo static field và đặt giá trị mặc định, không chạy explicit initializer.
- [Java Virtual Machine Specification 17 — 5.5 Initialization](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5): thứ tự gán `ConstantValue` và chạy `<clinit>`.
- [Java Language Specification 17 — 12.3.2 Preparation](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.3.2): liên hệ Preparation với class/interface.
- [Java Language Specification 17 — 12.4 Initialization](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4): hành vi active use và thứ tự static initializer.

**Điểm cần nhớ:** Preparation tạo và đặt **giá trị mặc định cho static field**, còn hành vi đặt giá trị do source Java mô tả diễn ra khi Initialization; hằng `ConstantValue` có bước gán riêng khi bắt đầu Initialization theo JVMS.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-lifecycle-006">Resolution: phân giải tham chiếu ký hiệu</a>

<details>
<summary>Xem chi tiết</summary>

# Resolution: phân giải tham chiếu ký hiệu trong vòng đời lớp Java

**Resolution (phân giải tham chiếu ký hiệu)** là công đoạn của **linking** trong vòng đời lớp trên JVM. Khi biên dịch mã nguồn Java, compiler thường chưa thể gắn các chỗ sử dụng lớp, trường, phương thức với đối tượng runtime cụ thể. Thay vào đó, class file lưu các **symbolic reference**: thông tin mô tả tên và kiểu của đích cần truy cập. Khi resolution diễn ra, JVM chuyển một tham chiếu như vậy thành thực thể runtime tương ứng, đồng thời thực hiện những kiểm tra liên quan như tìm thành viên, quyền truy cập và ràng buộc class loader.

Đây là một nguyên nhân quan trọng khiến **biên dịch thành công nhưng ứng dụng vẫn lỗi ở thời gian chạy**: tên phương thức còn trong bytecode của ứng dụng nhưng JAR được tải lúc chạy đã thay đổi API; lớp tồn tại nhưng không được phép truy cập; hoặc cùng tên class được định nghĩa bởi class loader không tương thích.

## 1. Đặt resolution vào đúng vị trí

```text
.java
  │ javac: phân tích nguồn, kiểm tra kiểu, sinh class file
  ▼
.class: bytecode + constant pool chứa symbolic references
  │
  ▼
Loading: loader tìm/định nghĩa lớp trong JVM
  │
  ▼
Linking
  ├─ Verification: bytecode/class file hợp lệ?
  ├─ Preparation: tạo và đặt giá trị mặc định cho static fields
  └─ Resolution: symbolic reference → đích runtime + kiểm tra liên kết
          │
          ├─ Có thể được thực hiện sớm trong linking
          └─ Có thể được trì hoãn đến lúc tham chiếu được sử dụng
  │
  ▼
Initialization khi Java yêu cầu active use
  │
  ▼
Thực thi các phương thức, tạo object theo nhu cầu
```

Sơ đồ thể hiện **quan hệ logic**, không ép resolution phải kết thúc toàn bộ trước khi initialization. Theo JVM Specification, verification và preparation phải hoàn thành trước initialization, nhưng JVM có thể phân giải các symbolic reference theo cách **eager** (sớm) hoặc **lazy** (theo nhu cầu). Một số resolution có thể tiếp tục sau khi lớp đã initialized. Nếu resolution phát hiện lỗi, JVM phải báo lỗi ở thời điểm chương trình trực tiếp hoặc gián tiếp sử dụng tham chiếu gây lỗi.

| Bước | Câu hỏi chính | Phân biệt với resolution |
| --- | --- | --- |
| Loading | Dữ liệu lớp nào được đưa vào JVM, class loader nào định nghĩa? | Có lớp runtime chưa có nghĩa mọi member reference đã phân giải. |
| Verification | Định dạng, cấu trúc và bytecode có hợp lệ không? | Kiểm tra bytecode khác với xác định đích cho từng symbolic reference. |
| Preparation | Static fields có giá trị mặc định nào? | Chưa thực thi phần lớn static initializer Java. |
| **Resolution** | Tên/descriptor này trỏ tới class, field, method hay call site runtime nào? | Có thể kéo theo loading và access checks; không đồng nghĩa gọi phương thức. |
| Initialization | Khối `static` và static initializer có được thực thi chưa? | Thường do `new`, `getstatic`, `putstatic`, `invokestatic` hoặc các active use khác kích hoạt. |
| Method selection | Với đối tượng nhận thực tế, implementation nào được chọn? | Thường xảy ra khi chạy `invokevirtual`/`invokeinterface` sau khi method reference đã được resolved. |

Không coi thứ tự trên là dấu vết thực thi chính xác của mọi JVM. Resolution là khái niệm được đặc tả ở cấp JVM; cách JVM cache thông tin hay tối ưu hóa trong bộ nhớ thuộc chi tiết triển khai.

## 2. Symbolic reference đến từ đâu?

Một tệp `.class` có **constant pool**, bao gồm string literal, giá trị hằng, tên/descriptor và các entry mô tả tham chiếu. JVM xây dựng **run-time constant pool** tương ứng cho lớp đã được tạo. Nhiều bytecode instructions trỏ vào đó thông qua **chỉ số**, thay vì mang địa chỉ máy của lớp hoặc phương thức.

Ví dụ Java:

```java
int n = Calculator.total;
String text = Calculator.format(n);
```

Compiler có thể tạo những tham chiếu tượng trưng tương tự:

```text
CONSTANT_Fieldref       demo/Calculator.total:I
CONSTANT_Methodref      demo/Calculator.format:(I)Ljava/lang/String;
```

Ở đây:

- `demo/Calculator` là tên internal của class trong class file; binary name Java tương ứng là `demo.Calculator`.
- `total` là tên field; descriptor `I` nghĩa là `int`.
- `format` là tên method; descriptor `(I)Ljava/lang/String;` nghĩa là nhận một `int` và trả về `java.lang.String`.
- Tên field/method và **descriptor** tạo nên định danh thành viên mà bytecode yêu cầu. JVM descriptor của method bao gồm cả return type; chỉ nhìn tên `format` là chưa đủ để hiểu một tham chiếu trong constant pool.

Đây là cú pháp mô tả giản lược để học, không phải đường dẫn bắt buộc của JAR. Các entry thật có chỉ số `#...`, liên kết `Class`/`NameAndType` và bytecode offset; dùng `javap -verbose` để xem cấu trúc chính xác.

Các dạng tham chiếu phổ biến:

| Constant-pool entry | Đại diện cho | Một bytecode liên quan |
| --- | --- | --- |
| `CONSTANT_Class` | Lớp/interface/array type | `new`, `checkcast`, `instanceof`, `anewarray`, `ldc` với class literal |
| `CONSTANT_Fieldref` | Field theo chủ thể, tên, field descriptor | `getfield`, `putfield`, `getstatic`, `putstatic` |
| `CONSTANT_Methodref` | Method của class theo tên, method descriptor | `invokevirtual`, `invokespecial`, `invokestatic` |
| `CONSTANT_InterfaceMethodref` | Method được tham chiếu qua interface | `invokeinterface`; một số lệnh gọi static/private interface methods tùy opcode |
| `CONSTANT_MethodType` | Kiểu lời gọi gồm tham số và kiểu trả về | `ldc` cho method-type constant |
| `CONSTANT_MethodHandle` | Hành vi truy cập field/gọi method theo reference kind | `ldc` và thành phần bootstrap |
| `CONSTANT_Dynamic` | Giá trị hằng được tính qua bootstrap method | `ldc`/`ldc_w`/`ldc2_w` theo loại |
| `CONSTANT_InvokeDynamic` | Mô tả call site được liên kết động | `invokedynamic` |

Các entry tên/descriptor như `CONSTANT_Utf8` và `CONSTANT_NameAndType` là thành phần cấu trúc của tham chiếu. Đừng hiểu mọi string hoặc số trong constant pool đều trải qua field/method resolution.

## 3. Cơ chế resolution: từ cái tên đến đích runtime

Xét một instruction như `getstatic demo/Calculator.total:I` nằm trong bytecode của `demo.Main`:

1. **Nhận symbolic reference.** Bytecode trỏ tới entry `Fieldref` trong run-time constant pool của lớp đang chứa instruction.
2. **Phân giải chủ thể class/interface.** JVM dùng defining loader của lớp đang chứa tham chiếu để tạo/tra cứu lớp chủ thể theo quy tắc loading và delegation; từ đó xác định runtime type đúng, thay vì tìm class chỉ bằng chuỗi tên.
3. **Tra cứu field/method phù hợp.** Với field, JVM dùng tên và descriptor, duyệt theo quy tắc lookup của JVM. Với method hoặc interface method, quy tắc tra cứu khác nhau và có thể xét superclass/superinterface.
4. **Kiểm tra access và loading constraints.** Thành viên có thực sự truy cập được từ lớp tham chiếu hay không? Các kiểu xuất hiện trong descriptor có thống nhất qua các defining loaders liên quan hay không?
5. **Hoàn thành hoặc thất bại.** Nếu thành công, JVM có kết quả runtime tương ứng cho reference; nếu thất bại, lỗi liên kết phù hợp được báo. Các điều kiện riêng của instruction như static/instance cũng có thể gây lỗi liên kết.
6. **Tiếp tục ngữ nghĩa instruction.** Ví dụ `getstatic` có thể làm class khai báo field được initialized trước khi đọc giá trị; `invokevirtual` sẽ còn chọn implementation dựa trên lớp runtime của receiver.

Tham chiếu `demo.Calculator` được resolve ở ngữ cảnh **lớp chứa constant-pool entry**, chứ không phải chọn ngẫu nhiên class có tên đó trong toàn bộ tiến trình. Điều này quyết định tính đúng đắn của kiến trúc plugin sử dụng nhiều class loader.

### Vì sao không phải "thay chuỗi tên bằng địa chỉ máy"?

Kết quả resolution về mặt đặc tả có thể là class/interface, field, method, `MethodType`, `MethodHandle` hoặc một hằng/call site runtime. JVM triển khai có thể lưu dữ liệu tra cứu trong cache hoặc cấu trúc tối ưu, nhưng **không có giao diện Java chuẩn trả về con trỏ native** cho một entry constant pool. Hãy dùng từ "**ràng buộc symbolic reference với thực thể runtime**" thay cho suy diễn mọi trường hợp đều trở thành một địa chỉ hàm.

## 4. Các trường hợp resolution cụ thể

### 4.1. Class và interface resolution

Với tham chiếu từ lớp `D` đến kiểu có tên `N`:

- JVM dùng **defining loader của `D`** để yêu cầu tạo/tra cứu kiểu có tên `N`. Loader có thể ủy quyền cho parent. Class được tạo thực tế có thể có **defining loader khác** với loader nhận yêu cầu ban đầu.
- Nếu tham chiếu là array class có phần tử kiểu tham chiếu, resolution liên quan đến kiểu phần tử cũng được xử lý theo quy tắc tương ứng.
- JVM kiểm tra quyền truy cập của `D` đến kiểu đích.

Nếu loại đó không thể được tìm/tạo, resolution có thể thất bại do lỗi loading. Nếu class tồn tại nhưng `D` không được phép truy cập, resolution có thể báo `IllegalAccessError`. Sự tồn tại của một `.class` trong JAR không tự bảo đảm JVM có thể resolve đến nó.

Ví dụ named module còn cần xét **module readability/exports**; public type trong package không export cho module yêu cầu vẫn có thể không truy cập hợp lệ từ module đó. Quy tắc access ở runtime xét package, module và định danh class loader theo Java Platform Module System, không chỉ từ khóa `public`.

### 4.2. Field resolution

Một `Fieldref` xác định **class/interface đích, field name, field descriptor**. Sau khi resolve class, JVM tra cứu field được khai báo ở class đó, tiếp tục theo các quy tắc lookup qua superinterfaces và superclass. Nếu không tìm thấy tên **và descriptor** phù hợp, JVM ném `NoSuchFieldError`; nếu thấy nhưng caller không có quyền truy cập, có thể là `IllegalAccessError`.

Cần tách **resolution của field** khỏi kiểm tra instruction: `getfield` yêu cầu instance field, còn `getstatic` yêu cầu static field. Nếu binary API đổi static thành instance mà bytecode caller vẫn sử dụng opcode cũ, lỗi có thể là `IncompatibleClassChangeError`, cho dù tên và descriptor field vẫn khớp.

Ví dụ các descriptor:

```text
int count                → count:I
long total               → total:J
String label             → label:Ljava/lang/String;
int[] numbers            → numbers:[I
String[] names           → names:[Ljava/lang/String;
```

### 4.3. Class method resolution

Với `Methodref`, JVM trước tiên resolve class chủ thể rồi tra cứu method phù hợp, bao gồm các quy tắc đặc biệt cho superclass và default interface methods. Nếu không tìm được method theo tên và descriptor, kết quả thường là `NoSuchMethodError`. Nếu entry `Methodref` lại trỏ đến interface ở nơi quy tắc yêu cầu class, JVM có thể báo `IncompatibleClassChangeError`.

Constructor có tên JVM đặc biệt `<init>`; class initializer là `<clinit>`. Không xem `<clinit>` là phương thức bình thường có thể gọi trực tiếp bằng source Java.

Một thay đổi source-level nhìn có vẻ nhỏ vẫn có thể phá compatibility ở bytecode:

```text
Trước: String version() → descriptor ()Ljava/lang/String;
Sau:   int version()    → descriptor ()I
```

Bên gọi cũ đã biên dịch vẫn đòi `()Ljava/lang/String;`. Nếu runtime chỉ có `()I`, Java không tự "chuyển đổi return type" để cứu link. Ví dụ chạy được ở phần thực hành B sẽ quan sát lỗi này.

### 4.4. Interface method resolution

Với `InterfaceMethodref`, JVM phải resolve một **interface**, tra cứu method có tên/descriptor yêu cầu, và tuân thủ quy tắc với method kế thừa (kể cả các tình huống default methods). Không thể thay thế máy móc bằng thuật toán lookup class. Nếu runtime class thực thi interface khác kỳ vọng của caller, việc resolve và/hoặc method selection có thể gặp lỗi.

Hai giai đoạn khác nhau:

- **Interface method resolution:** xác định tham chiếu từ bytecode `invokeinterface` là method nào ở cấp khai báo/hệ kiểu.
- **Method selection:** khi instruction thực sự gọi, xét **receiver runtime class** để chọn implementation thích hợp (ví dụ `EnglishGreeting` hay `VietnameseGreeting`).

Bởi vậy "resolve method" không nhất thiết có nghĩa cố định một implementation cho mọi instance nhận lời gọi.

### 4.5. Method type, method handle, dynamic constant và `invokedynamic`

Java hiện đại còn có các kiểu resolution:

- **Method type:** phân giải những class/interface xuất hiện trong descriptor; kết quả là một `java.lang.invoke.MethodType`.
- **Method handle:** phân giải hành vi truy cập field/method theo reference kind; kiểm tra các điều kiện phù hợp và tạo một `java.lang.invoke.MethodHandle`.
- **Dynamically-computed constant (`CONSTANT_Dynamic`):** bootstrap method tạo ra giá trị theo thông tin constant pool và tham số bootstrap. Sau khi resolve thành công, kết quả được giữ cho reference; bootstrap không tự chạy lại mỗi lần đọc constant đó.
- **Call site của `invokedynamic`:** JVM resolve bootstrap method, tham số và kết quả `CallSite`. Với loại này, resolution được gắn với **instruction `invokedynamic` cụ thể**, ngay cả khi nhiều instruction tham chiếu cùng entry constant pool.

Các ví dụ thực tế thường gặp là lambda expression và string concatenation hiện đại. Chi tiết phụ thuộc cách compiler sinh class file; source Java chứa `+` hoặc lambda **không đủ để khẳng định** lúc nào bootstrap được chạy. Dùng `javap -v` để xác định `InvokeDynamic` và `BootstrapMethods` của build thật.

Nếu bootstrap không hợp lệ hoặc thất bại, có thể gặp `BootstrapMethodError`. Những tình huống này là dynamic linking theo đặc tả JVM, không phải "reflection" thông thường.

## 5. Resolution diễn ra lúc nào? Một tham chiếu có phải được phân giải nhiều lần?

Trong một class đã được định nghĩa, JVM có thể phân giải symbolic reference trước khi lệnh dùng đến nó (eager) hoặc khi cần (lazy), trong giới hạn của JVMS. Một resolution thành công được ghi nhớ theo tham chiếu; nếu thất bại, lần sử dụng tham chiếu ấy tiếp tục gặp lỗi tương ứng. **Thời điểm phát hiện lỗi bên trong JVM có thể khác thời điểm lỗi trở nên quan sát được trong chương trình.** Thí nghiệm về thay phiên bản API ở trên minh họa hậu quả của binary incompatibility; bài kế tiếp đi sâu vào điểm xuất hiện lỗi khi eager/lazy resolution.

## 6. Thực hành A — xem symbolic references bằng `javap`

Tạo đúng cấu trúc tệp:

```text
resolution-lab/
└── src/
    └── demo/
        ├── Library.java
        └── Main.java
```

**Tệp `src/demo/Library.java`:**

```java
package demo;

public final class Library {
    public static int number = 7;

    static {
        System.out.println("Library initialized");
    }

    public static String greet(String name) {
        return "Hello, " + name;
    }
}
```

**Tệp `src/demo/Main.java`:**

```java
package demo;

public class Main {
    public static void main(String[] args) {
        System.out.println("start");

        Class<?> type = Library.class;
        System.out.println("literal=" + type.getName());

        System.out.println("before field");
        System.out.println("number=" + Library.number);
        System.out.println("call=" + Library.greet("Java"));
    }
}
```

Từ thư mục `resolution-lab`, biên dịch và chạy bằng JDK 17+:

```sh
javac --release 17 -d out src/demo/Library.java src/demo/Main.java
java -cp out demo.Main
```

**Stdout:**

```text
start
literal=demo.Library
before field
Library initialized
number=7
call=Hello, Java
```

Giải thích:

- `Library.class` là class literal: yêu cầu có biểu diễn `Class` tương ứng nhưng không phải active use kích hoạt `Library` initialization. Đừng suy ra từ đó thời điểm loading hoặc resolution bên trong JVM là chính xác tại dòng in nào.
- `Library.number` là **non-final static field**. `getstatic` cần tham chiếu field đúng và yêu cầu lớp khai báo field được initialized; vì vậy dòng `Library initialized` in **trước** `number=7`.
- `Library.greet("Java")` dùng `invokestatic`. Tại thời điểm này lớp đã khởi tạo, nên block static không chạy lại. Logic concat bên trong `greet` có thể dùng `invokedynamic` khi biên dịch bằng JDK 17.
- Thứ tự stdout phản ánh các **side effects quan sát được**; không chứng minh JVM thực hiện lazy resolution đúng ở một offset cụ thể.

Quan sát class file, không thay nội dung:

```sh
javap -classpath out -c -p demo.Main
javap -classpath out -v demo.Main
```

Trong `javap -c`, tìm lệnh tương ứng đọc field và gọi method:

```text
getstatic     ...  // Field demo/Library.number:I
invokestatic  ...  // Method demo/Library.greet:(Ljava/lang/String;)Ljava/lang/String;
```

Các dấu `...` đại diện cho chỉ số/offset do công cụ sinh ra, **không phải output nguyên văn đầy đủ**. Trong `javap -v`, mục `Constant pool` giải thích mỗi entry `Fieldref`, `Methodref`, `Class`, `NameAndType`. Các chỉ số `#n` có thể thay đổi khi sửa source, đổi compiler hoặc JDK. Bảng constant pool cho biết **điều gì cần được resolve**; nó không xác nhận thời điểm JVM đã thực hiện resolution khi chạy.

Để kiểm tra việc compiler loại bỏ một số tham chiếu, thử thay `Library.number` bằng `public static final int number = 7;` và biên dịch lại. Lúc này đây là **constant variable**, compiler thường inline giá trị ở nơi sử dụng, nên có thể không còn instruction `getstatic` cho lần đọc đó. Kiểm tra `javap` trước khi kết luận. Khi giá trị đã inline, đọc nó không yêu cầu khởi tạo `Library`, nhưng `greet` sau đó vẫn là active use.

## 7. Thực hành B — thay JAR runtime làm `NoSuchMethodError`

Đây là thí nghiệm về **binary compatibility**: compile dựa vào API phiên bản 1, rồi chạy với phiên bản 2 **không tương thích**. Không cần sửa bytecode thủ công.

Tạo ba source file riêng:

```text
resolution-compat/
└── src/
    ├── v1/
    │   └── api/
    │       └── Service.java
    ├── v2/
    │   └── api/
    │       └── Service.java
    └── client/
        └── demo/
            └── Client.java
```

**`src/v1/api/Service.java`:**

```java
package api;

public class Service {
    public static String version() {
        return "v1";
    }
}
```

**`src/v2/api/Service.java`:**

```java
package api;

public class Service {
    public static int version() {
        return 2;
    }
}
```

**`src/client/demo/Client.java`:**

```java
package demo;

import api.Service;

public class Client {
    public static void main(String[] args) {
        System.out.println("before");
        try {
            System.out.println(Service.version());
        } catch (NoSuchMethodError error) {
            System.out.println(error.getClass().getSimpleName());
        }
    }
}
```

Từ thư mục `resolution-compat`, dùng JDK 17+:

```sh
javac --release 17 -d out-v1 src/v1/api/Service.java
javac --release 17 -cp out-v1 -d out-client src/client/demo/Client.java
javac --release 17 -d out-v2 src/v2/api/Service.java
```

Chạy đối chứng với API v1 trên **Windows PowerShell/CMD**:

```powershell
java -cp "out-client;out-v1" demo.Client
```

**Stdout đúng:**

```text
before
v1
```

Sau đó chạy với API v2 trên **Windows** (không cần biên dịch lại Client):

```powershell
java -cp "out-client;out-v2" demo.Client
```

**Stdout đúng:**

```text
before
NoSuchMethodError
```

Trên Linux/macOS, thay dấu `;` phân tách classpath bằng `:`, ví dụ `-cp "out-client:out-v2"`. Khi làm thí nghiệm, không đưa `out-v1` và `out-v2` lên cùng classpath: ta chủ ý chọn **một** bản `api.Service` tại runtime.

**Giải thích ở cấp JVM:**

1. Trong lúc compile `Client`, compiler thấy method `api.Service.version:()Ljava/lang/String;`.
2. Bytecode `Client.class` giữ symbolic method reference với **return type String**.
3. Khi chạy với `out-v2`, JVM có thể tìm class `api.Service` nhưng trong đó chỉ có `version:()I`.
4. Method reference cũ không tìm được method với tên và descriptor tương ứng; JVM báo `NoSuchMethodError` tại điểm sử dụng. `try/catch` trong bài bắt đúng lỗi để stdout gọn.
5. Sửa đúng là cung cấp bản API binary-compatible hoặc biên dịch lại client và mọi thành phần liên quan theo hợp đồng mới; đổi classpath một cách tình cờ có thể che giấu lỗi nhưng không giải quyết quản lý version.

Dùng các lệnh đọc bytecode để tự đối chiếu:

```sh
javap -classpath out-client -v demo.Client
javap -classpath out-v1 -s api.Service
javap -classpath out-v2 -s api.Service
```

Với `javap -s`, v1 chứa descriptor `()Ljava/lang/String;`, v2 chứa `()I`. Java ở mức source không cho khai báo hai method có cùng tên và cùng danh sách tham số chỉ khác return type trong cùng class; ví dụ này sử dụng hai bản build thay thế nhau, không phải overload hợp lệ.

## 8. Thực hành C — resolution không phải dynamic dispatch

Một lời gọi `invokeinterface` có thể resolve reference đến một method khai báo trong interface, rồi **method selection** chọn code dựa vào kiểu runtime của receiver.

Tạo **một** tệp `DispatchDemo.java` (các kiểu phụ không phải `public`, nên được phép cùng tệp):

```java
package demo;

interface Greeting {
    String say();
}

class EnglishGreeting implements Greeting {
    @Override
    public String say() {
        return "Hello";
    }
}

class VietnameseGreeting implements Greeting {
    @Override
    public String say() {
        return "Xin chao";
    }
}

public class DispatchDemo {
    public static void main(String[] args) {
        Greeting greeting = new EnglishGreeting();
        System.out.println(greeting.say());

        greeting = new VietnameseGreeting();
        System.out.println(greeting.say());
    }
}
```

Lưu trong `src/demo/DispatchDemo.java` của một thư mục thực hành riêng, rồi chạy:

```sh
javac --release 17 -d out src/demo/DispatchDemo.java
java -cp out demo.DispatchDemo
javap -classpath out -c demo.DispatchDemo
```

**Hai dòng stdout của lệnh `java`:**

```text
Hello
Xin chao
```

Lệnh `javap` phía sau sẽ in tiếp disassembly, không nằm trong hai dòng stdout trên. Trong mã `main`, hai lần gọi qua biến kiểu `Greeting` thường hiện thành `invokeinterface demo/Greeting.say:()Ljava/lang/String;`. Reference cấp interface giữ nguyên, nhưng instance đầu là `EnglishGreeting` còn instance sau là `VietnameseGreeting`; việc chọn method thực sự gọi phụ thuộc receiver. Có thể không có một "địa chỉ method duy nhất" dùng cho mọi instance sau khi interface reference đã resolved.

Điểm phân biệt:

- **Resolution:** `Greeting.say:()Ljava/lang/String;` là tham chiếu hợp lệ đến method của interface?
- **Selection:** Với object đang nhận lệnh gọi, implementation nào đáp ứng contract này?
- **Invocation:** JVM thực hiện method đã được chọn và trả kết quả.
- **Initialization:** Khởi tạo các lớp cần thiết xảy ra theo những trigger riêng khi tạo instance và gọi static members.

## 9. Class loader, runtime identity và loading constraints

Trong JVM, định danh của class/interface runtime dựa trên **cặp (binary name, defining loader)**. Hai plugin loaders có thể định nghĩa hai class `api.Dto` cùng tên nhưng **khác kiểu**. Resolution thông qua các loaders có thể nhận những định nghĩa runtime không tương thích dù source package giống nhau.

Ví dụ ý niệm:

```text
AppLoader      defines api.Dto (type A)
PluginLoader   defines api.Dto (type B)

api.Dto của AppLoader != api.Dto của PluginLoader
```

Khi liên kết field hoặc method có descriptor chứa các kiểu nằm ở biên giới loaders, JVM có thể áp dụng **loading constraints** để bảo đảm rằng cùng tên kiểu trong chữ ký của một liên kết buộc phải quy về cùng class/interface runtime. Nếu constraint bị vi phạm, có thể xuất hiện `LinkageError`. Điều này bảo vệ type safety, đặc biệt trong hệ thống plugin hoặc application server.

Dùng các điểm kiểm tra:

```java
Class<?> type = object.getClass();
System.out.println(type.getName());
System.out.println(type.getClassLoader());
```

Đây là đoạn **minh họa**, cần có biến `object` trong chương trình thực tế. Chú ý `getClassLoader() == null` thường mang nghĩa lớp được định nghĩa bởi **bootstrap loader**, không phải lỗi. Để tìm class cụ thể từ đâu, cần xem runtime module/classpath hoặc logging class loading cùng với thông tin defining loader.

**Module access:** Nếu type public nằm trong named module nhưng package không export cho module caller, symbolic resolution vẫn có thể thất bại vì quyền truy cập. "Có class file trên module path" chưa đủ.

**Ràng buộc phạm vi:** Nội dung trên nói về runtime linkage, khác với type erasure của generics và dependency injection của Spring. Đừng dùng từ "resolution" trong mọi thư viện để giả định nó có nghĩa giống JVM `5.4.3`.

## 10. Chẩn đoán lỗi: lỗi phát sinh ở bước nào?

| Triệu chứng | Tình huống thường gặp | Hướng kiểm tra |
| --- | --- | --- |
| `ClassNotFoundException` | API `Class.forName`/`ClassLoader.loadClass` không tìm thấy tên yêu cầu | Tên đầy đủ, loader được dùng, JAR/classpath/module path; đây là checked exception của API tra cứu. |
| `NoClassDefFoundError` | Bytecode/runtime cần một class nhưng không thể định nghĩa/tìm thấy, hoặc lần initialization trước thất bại | Đọc thông điệp và `Caused by`; không đồng nhất mọi lỗi với class file thiếu. |
| `NoSuchFieldError` | Constant-pool `Fieldref` không khớp field runtime | Tên **và descriptor**; sự thay đổi kiểu field; phiên bản JAR thực tế. |
| `NoSuchMethodError` | `Methodref`/`InterfaceMethodref` không khớp runtime API | Tham số, return descriptor, binary incompatibility; thí nghiệm B. |
| `IllegalAccessError` | Truy cập kiểu/thành viên bị từ chối ở runtime | Quyền package/nest/module, dependency build khác runtime; khác `IllegalAccessException` của một số Reflection API. |
| `IncompatibleClassChangeError` | Dùng static/instance không tương thích hoặc thay đổi class/interface gây sai hợp đồng liên kết | Opcode caller, modifiers và bản API runtime. |
| `AbstractMethodError` | Lời gọi tới contract method nhưng không có implementation phù hợp tại runtime | Interface, implementation cũ, version lệch; có thể liên quan đến method selection/invocation. |
| `BootstrapMethodError` | Bootstrap cho dynamic constant/`invokedynamic` hỏng | `BootstrapMethods`, method handle, tham số bootstrap, root cause. |
| `VerifyError` | Bytecode không hợp lệ/không qua verification | Tool tạo/sửa class file, version bytecode, verification; khác trường hợp lookup thất bại. |
| `ExceptionInInitializerError` | Thực thi static initialization thất bại | Code `<clinit>`, giá trị static, nguyên nhân exception; khác resolution. |

`NoSuchMethodError`, `NoSuchFieldError` và một số lỗi trên thuộc họ **`LinkageError`** (nhiều lớp con cụ thể). Chúng thường xuất hiện sau khi **compile/runtime dependency lệch nhau**, nhưng không được suy ngược ngay rằng chỉ một nguyên nhân duy nhất. Kiểm tra stacktrace, nơi lỗi xảy ra và class loader thay vì thử thêm JAR không theo hệ thống.

Một lỗi như `NullPointerException` trong thân method là **lỗi khi thực thi chương trình**, không chứng minh resolution hỏng. Ngược lại, việc thấy `NoSuchMethodError` trước khi code method đích chạy là hợp lý: JVM chưa resolve được đích mà bytecode yêu cầu.

## 11. Quy trình debug resolution trong ứng dụng thực tế

1. **Xác định nơi gọi đang thất bại.** Lấy stacktrace đầu tiên và chain `Caused by`. Xác định bytecode của class caller (không chỉ nhìn source mới nhất).
2. **Đọc descriptor được yêu cầu.** Chạy `javap -classpath ... -v -c package.Caller`. Tìm `Fieldref`, `Methodref`, `InterfaceMethodref` gắn với opcode tại vị trí liên quan.
3. **Kiểm tra class runtime thật.** Dùng `javap -classpath ... -s package.Target` trên đúng JAR hoặc thư mục class mà ứng dụng đang chạy. So sánh descriptor, static/instance, access modifiers.
4. **Kiểm tra versions và đường nạp.** Xác minh thứ tự classpath, module path, fat JAR shading, plugin loaders, và package exports. Với Gradle/Maven, xem cây dependency runtime; tree compile không bảo đảm giống runtime.
5. **Tách loại lỗi.** `NoSuchMethodError` thường hướng vào binary mismatch; `IllegalAccessError` hướng vào access; `LinkageError` kèm loading-constraint violation hướng vào loader type identity; `ExceptionInInitializerError` hướng vào static initializer.
6. **Lặp lại với môi trường kiểm soát.** Tạo bản build sạch, xác định JDK, cùng classpath/module path; thêm log có chủ đích thay vì suy đoán thứ tự eager/lazy.
7. **Khắc phục ở nguồn.** Đồng bộ hợp đồng binary API/dependency, chỉnh exports/quyền truy cập hoặc hợp đồng plugin/shared loader; kiểm tra lại bằng cùng executable distribution dùng trong production.

Công cụ hữu ích trong JDK 17+:

```sh
javap -classpath out -v -c demo.Main
javap -classpath out -s demo.Library
java -Xlog:class+load=info -cp out demo.Main
```

`-Xlog:class+load=info` là Unified Logging của HotSpot/JDK hỗ trợ cờ này; log cho biết **loading events**, không phải danh sách chi tiết mọi lần resolution. JVM có tùy chọn logging khác tùy phiên bản/implementation; tránh tuyên bố rằng một dòng class-load chứng minh field/method đã resolved. `javap` đọc class file trên đĩa, không khẳng định chính tệp đó đã được class loader runtime lựa chọn nếu classpath thực tế khác.

Để so sánh caller và runtime target trong JAR:

```sh
javap -classpath app.jar -v -c com.example.app.Caller
javap -classpath dependency.jar -s com.example.lib.Target
```

Đây là **mẫu lệnh**; tên `app.jar` và class/package cần thay bằng artefact thực tế, không phải một ví dụ độc lập có output đã định.

## 12. Những nhầm lẫn cần tránh

- **"Loading hoàn tất thì tất cả symbolic references đã resolve."** Sai: các reference khác có thể vẫn chưa phân giải.
- **"Resolution luôn chạy sau preparation và trước mọi static initialization theo một đường thẳng duy nhất."** Không chính xác: cấu trúc linking có resolution, nhưng strategy eager/lazy khác nhau; JVM có thể tiếp tục phân giải sau initialization.
- **"Resolve method nghĩa là chọn method implementation cụ thể của object."** Sai với virtual/interface invocation: cần tách method resolution khỏi method selection.
- **"Class name đủ để tìm đúng type."** Sai với nhiều defining loaders; cặp tên/loader mới là định danh runtime.
- **"Đổi kiểu trả về method không ảnh hưởng caller nếu tên và tham số giữ nguyên."** Sai với bytecode: return type nằm trong JVM method descriptor.
- **"`javap` thấy `Methodref` thì method đã được execute."** Sai: `javap` chỉ cho biết metadata/static bytecode; resolution/execution là các sự kiện runtime.
- **"Mọi `LinkageError` đều do thiếu JAR."** Sai: có thể do method/field mismatch, access, loader constraints, class/interface mismatch, bootstrap error.
- **"Sửa file JAR tại chỗ đủ khiến class đang chạy tự relink."** Sai trong mô hình thông thường: class đã định nghĩa và kết quả resolution có quy tắc cache/thất bại; cần thay đúng runtime/deployment và thường khởi động lại hoặc tạo loader mới theo kiến trúc hợp lệ.

## 13. Câu hỏi tự kiểm tra

**1. Vì sao compiler có thể tạo `Methodref` thay vì địa chỉ method?** Vì class file là định dạng độc lập môi trường, còn class/method runtime và class loader được xác định khi chương trình chạy.

**2. `getstatic` cần những bước nào?** Resolve field reference và thực hiện các kiểm tra liên kết thích hợp; khi cần, khởi tạo lớp/interface **khai báo field** trước khi đọc giá trị. Không đồng nhất hai cơ chế.

**3. Khi nào `NoSuchMethodError` xuất hiện dù `javac` từng thành công?** Khi bytecode caller tham chiếu method descriptor không còn được runtime class/interface đáp ứng; thường do binary-incompatible dependency.

**4. Hai `Class` cùng binary name có nhất thiết bằng nhau không?** Không. Nếu defining loader khác nhau thì là hai runtime types khác nhau.

**5. Một reference đã resolve sai có tự được tìm lại sau khi chép thêm JAR không?** Không thể dựa vào điều đó: JVMS ràng buộc kết quả/thất bại của lần resolution trước trên reference đó.

**6. Vì sao hai lời gọi `Greeting.say()` có hai output khác nhau?** Cùng symbolic interface method có thể được resolved, nhưng method selection lựa chọn implementation theo receiver runtime.

**7. `CONSTANT_InvokeDynamic` khác `Methodref` ở điểm nào đáng nhớ?** Resolution của call site động dùng bootstrap method và gắn kết quả với instruction `invokedynamic` cụ thể.

**8. Trong debugging, vì sao phải xem descriptor ngoài method name?** Vì JVM cần signature binary chính xác; cùng method name nhưng descriptor khác là reference khác.

## 14. Tài liệu chuẩn để kiểm chứng

- [The Java Virtual Machine Specification, Java SE 17 — 4: Class File Format](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html): constant pool, `CONSTANT_*` và field/method descriptors.
- [The Java Virtual Machine Specification, Java SE 17 — 5.4.3 Resolution](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.4.3): thời điểm resolution, kết quả thành công/thất bại, các loại reference.
- [The Java Virtual Machine Specification, Java SE 17 — 5.4.4 Access Control và 5.4.6 Method Selection](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): kiểm tra truy cập và lựa chọn method.
- [The Java Virtual Machine Specification, Java SE 17 — 5.5 Initialization](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5): phân biệt resolution với class initialization.

**Nguyên tắc nhớ:** resolution là việc JVM **liên kết tham chiếu ký hiệu của bytecode với thực thể runtime đúng, dưới đúng class loader và các quy tắc kiểm tra**. Loading tạo lớp; resolution nhận diện đích mà một tham chiếu yêu cầu; initialization chạy logic static khi cần; selection/invocation quyết định và thực thi phương thức phù hợp. Việc tách bốn cơ chế này là nền tảng để hiểu lỗi liên kết class, xung đột dependency và cách ly plugin trong Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-lifecycle-007">Phân giải sớm, phân giải muộn và thời điểm xuất hiện lỗi</a>

<details>
<summary>Xem chi tiết</summary>

# Phân giải sớm, phân giải muộn và thời điểm xuất hiện lỗi trong JVM

> **Chủ đề:** Java Core → ClassLoader → Vòng đời lớp. Bài học phân biệt thời điểm JVM phân giải (*resolve*) tham chiếu ký hiệu với thời điểm lỗi liên kết (*linkage error*) được ném tới chương trình. Các thí nghiệm dùng Java 17+ và kiểm tra được trên OpenJDK 21.

## 1. Vấn đề: chương trình khởi chạy rồi mới báo thiếu method

Giả sử ta biên dịch `App.class` với phiên bản thư viện `Api` có phương thức `message()`. Sau khi biên dịch, thư viện được thay bằng bản không còn phương thức đó, nhưng `App.class` vẫn giữ nguyên.

Tại sao `main` có thể in một số dòng rồi đến lúc gọi `Api.message()` mới xuất hiện `NoSuchMethodError`? JVM có bắt buộc đợi đến chính lời gọi mới được tìm phương thức hay không?

Câu trả lời có hai phần:

- JVM có quyền **phân giải sớm (eager resolution)** nhiều tham chiếu ký hiệu hoặc **phân giải muộn (lazy resolution)** khi cần. Chiến lược nội bộ có thể khác nhau giữa các JVM và các tình huống.
- Dù đã phát hiện lỗi sớm bên trong, JVM vẫn phải bảo đảm lỗi chỉ trở nên **quan sát được tại điểm chương trình thực hiện hành động có thể, trực tiếp hoặc gián tiếp, cần liên kết tới đối tượng đang lỗi**. Không thể kết luận chiến lược eager hay lazy chỉ từ dòng output xuất hiện trước lỗi.

Đó là ranh giới giữa **thời điểm thực hiện resolution** và **thời điểm công khai lỗi ra chương trình**.

## 2. Đặt resolution vào vòng đời lớp

Một class file ghi các **symbolic reference** trong run-time constant pool, chẳng hạn `Methodref demo/Api.message:()Ljava/lang/String;`. **Resolution** dùng tên, descriptor và loader để tìm đúng lớp hoặc thành viên runtime. **Linking** gồm verification, preparation và resolution; verification và preparation phải hoàn tất trước initialization, còn resolution có thể đến sau. Từ nền tảng đó, bài này tập trung vào **lịch resolution và điểm chương trình quan sát lỗi**, không lặp lại thuật toán tra cứu thành viên.

## 3. Phân giải sớm và phân giải muộn

### Eager resolution

JVM có thể resolve nhiều symbolic reference khi verify/link class, trước lúc bytecode trực tiếp sử dụng chúng. Việc này có thể giúp chuẩn bị metadata và phát hiện dependency không phù hợp sớm ở nội bộ.

### Lazy resolution

JVM có thể để từng symbolic reference chưa resolve cho đến khi instruction sử dụng nó, chẳng hạn lần đầu thực thi `invokestatic` tới phương thức hoặc `getstatic` tới field. Cách này tránh làm công việc không cần thiết cho phần code không dùng đến.

### Chính xác JVM bảo đảm điều gì?

JVMS 5.4 và 5.4.3 cho phép các chiến lược trên **miễn không làm đổi ngữ nghĩa quan sát được**. Eager/lazy là chiến lược triển khai; Java không có yêu cầu rằng HotSpot hoặc mọi JVM phải resolve mọi reference ở cùng một thời điểm.

| Vấn đề | Có được bảo đảm không? |
| --- | --- |
| Class được nạp trước khi liên kết | Có. |
| Verification và preparation hoàn tất trước initialization | Có. |
| Tất cả symbolic reference được resolve trước initialization | Không. |
| JVM có thể resolve sớm các symbolic reference thông thường | Có, với giới hạn JVMS. |
| Lỗi linking được ném ở điểm hành động có thể trực tiếp/gián tiếp cần linking liên quan | Có. |
| Quan sát lỗi tại lời gọi chứng minh nội bộ JVM dùng lazy resolution | Không. |

**Ngoại lệ quan trọng:** Một số loại reference có quy tắc riêng. *Dynamically-computed constant* không được resolve tùy ý trước khi instruction `ldc`/`ldc_w`/`ldc2_w` cần dùng hoặc trước tình huống bootstrap sử dụng nó làm static argument như JVMS quy định; dynamic call site của `invokedynamic` cũng bị ràng buộc theo quy tắc bootstrap. Do đó không nên tuyên bố “JVM được resolve bất kỳ mục nào trong constant pool ngay lúc load”.

## 4. Hai mốc thời gian phải tách riêng

Xét `App.class` tham chiếu `Api.message()`, trong khi bản `Api.class` thực chạy không còn method tương ứng:

```text
App.class (compile với Api v1)
      |
      | contains Methodref demo/Api.message:()String
      v
JVM nạp App
      |
      +--> JVM có thể eager resolve và phát hiện thiếu method nội bộ
      |
      +--> main: println("started")
      |
      +--> nhánh không gọi method: có thể kết thúc bình thường
      |
      '--> nhánh thực thi invokestatic Api.message()
                      |
                      '--> NoSuchMethodError được quan sát
                           ở hành động sử dụng reference
```

Trên một JVM triển khai lazy, kiểm tra method có thể được thực hiện tại chính instruction cần dùng. Trên một JVM eager, vấn đề có thể được phát hiện trước nhưng việc ném ra chương trình vẫn phải nằm trong giới hạn JVMS.

**Đây là quy tắc về hành vi quan sát được**, không phải lời hứa mọi lỗi chỉ được ném đúng tại một dòng source. Liên kết có thể xảy ra gián tiếp; verification, loading của superclass/interface hoặc một thao tác chủ động khác có thể làm chương trình thấy lỗi tại điểm sớm hơn dự đoán từ source code.

### Kết quả resolution có được ghi nhớ?

Theo JVMS 5.4.3, với các loại reference thông thường:

- Nếu resolution thành công, các lần resolve sau đối với **cùng symbolic reference** tiếp tục thành công với cùng thực thể.
- Nếu lần đầu thất bại, các lần sau đối với **cùng reference** sẽ thất bại với cùng lỗi.
- Có các chi tiết riêng cho `invokedynamic`: call site resolution được gắn với từng instruction sử dụng reference, nên không nên suy rộng quy tắc bộ nhớ đệm của method reference thông thường cho mọi dynamic call site.

Từ đó, đổi JAR trên ổ đĩa trong lúc JVM đang chạy không phải cơ chế giúp tự sửa một symbolic reference đã resolution thất bại.

## 5. Thí nghiệm thực tế: Java biên dịch với API v1 nhưng chạy với API v2

Thí nghiệm này dùng một caller biên dịch trước, giữ nguyên caller và thay bytecode của thư viện lúc chạy.

Tạo các file theo cấu trúc:

```text
src-v1/demo/Api.java
src-v2/demo/Api.java
src-app/demo/App.java
```

**File `src-v1/demo/Api.java` — bản có method được dùng lúc biên dịch:**

```java
package demo;

public class Api {
    public static String message() {
        return "v1";
    }
}
```

**File `src-v2/demo/Api.java` — bản thay thế không có method `message`:**

```java
package demo;

public class Api {
    public static String other() {
        return "v2";
    }
}
```

**File `src-app/demo/App.java` — caller không được biên dịch lại:**

```java
package demo;

public class App {
    public static void main(String[] args) {
        System.out.println("1. App started");

        if (args.length == 0) {
            System.out.println("2. No API call");
            return;
        }

        System.out.println("2. About to invoke Api.message()");
        try {
            call();
        } catch (NoSuchMethodError e) {
            System.out.println("3. Caught NoSuchMethodError");
        }
    }

    static void call() {
        System.out.println(Api.message());
    }
}
```

Biên dịch theo thứ tự; `App` được compile với v1, sau đó runtime chỉ đặt v2 trước `App` trên classpath:

```bash
javac -d build/v1 src-v1/demo/Api.java
javac -cp build/v1 -d build/app src-app/demo/App.java
javac -d build/v2 src-v2/demo/Api.java
java -cp "build/v2:build/app" demo.App
java -cp "build/v2:build/app" demo.App go
```

Ở Linux/macOS, classpath separator là `:`. Trên Windows PowerShell hoặc CMD, hai lệnh chạy tương ứng là:

```powershell
java -cp "build\v2;build\app" demo.App
java -cp "build\v2;build\app" demo.App go
```

**Output lệnh không có tham số:**

```text
1. App started
2. No API call
```

**Output lệnh có tham số `go`:**

```text
1. App started
2. About to invoke Api.message()
3. Caught NoSuchMethodError
```

Đã đối chiếu kết quả bằng biên dịch và chạy trên OpenJDK 21. Hai nhánh cho thấy bytecode caller có thể tồn tại/chạy một phần dù một reference tới method bị thiếu. Việc bắt `NoSuchMethodError` trong thí nghiệm nhằm quan sát; **đây không phải cách sửa lỗi dependency trong ứng dụng thực tế**.

Để nhìn lại reference mà caller đã biên dịch:

```bash
javap -v -classpath build/app demo.App
```

Trong constant pool, tìm `Methodref` cho `demo/Api.message` và descriptor `()Ljava/lang/String;`. Chỉ số constant pool là kết quả của compiler nên không cần khớp một con số cụ thể.

Để so sánh:

1. Chạy cùng `App.class` với `Api v1`: `java -cp "build/v1:build/app" demo.App go` → in `v1` sau dòng `About to invoke...`, không báo `NoSuchMethodError`.
2. Bỏ cả `Api.class` khỏi runtime: khi cần symbolic reference đến `Api` có thể thấy `NoClassDefFoundError` do thiếu class.
3. Nếu sửa `message()` từ static thành instance method ở v2 trong khi caller vẫn dùng `invokestatic`, có thể gặp `IncompatibleClassChangeError`.
4. Nếu biên dịch lại caller với v2, `javac` báo không có `message()` ngay tại **compile time**. Thí nghiệm binary incompatibility chỉ có ý nghĩa khi caller giữ bytecode cũ.

## 6. Ví dụ riêng: class đã load/link nhưng chưa initialize

Tạo file độc lập `InitDemo.java`:

```java
public class InitDemo {
    static class Target {
        static {
            System.out.println("Target initialized");
        }
        static int value = 42;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("before lookup");
        Class.forName(
                "InitDemo$Target",
                false,
                InitDemo.class.getClassLoader()
        );
        System.out.println("after lookup; before active use");
        System.out.println("value=" + Target.value);
    }
}
```

Chạy:

```bash
javac InitDemo.java
java InitDemo
```

Output:

```text
before lookup
after lookup; before active use
Target initialized
value=42
```

`Class.forName(name, false, loader)` yêu cầu tìm/nạp và liên kết lớp nhưng không yêu cầu initialization qua lời gọi đó. Đến lúc thực hiện hành động đọc `Target.value`, JVM khởi tạo `Target` và in thông báo.

**Giới hạn của thí nghiệm:** Nó minh họa ranh giới loading/linking với initialization, **không chứng minh** mọi symbolic reference của `Target` đã resolve hoặc chưa resolve. Class đã link/initialize vẫn có thể mang symbolic references chưa được sử dụng.

Những API dễ nhầm:

| API | Ý nghĩa | Không bảo đảm |
| --- | --- | --- |
| `ClassLoader.loadClass(name)` | Yêu cầu loader tải class theo tên nhị phân; overload mặc định không yêu cầu init. | Toàn bộ constant pool của class đều resolved. |
| `Class.forName(name, false, loader)` | Tìm/nạp và liên kết lớp mà không yêu cầu init. | Chỉ đọc bytes, tuyệt đối không verify/link. |
| `Class.forName(name)` | Tìm và khởi tạo class theo quy tắc API. | Mọi tham chiếu trong class đều được eager resolve. |
| `ClassLoader.resolveClass(clazz)` | Cơ chế loader dùng để liên kết class đã load. | API public để ép resolve mọi reference trong mọi JVM. |

## 7. Những nhận định sai thường gặp

**“Thấy lỗi ở dòng gọi hàm nghĩa là JVM chắc chắn phân giải muộn.”** Sai. JVM có thể eager resolve và trì hoãn *việc ném lỗi ra chương trình* cho tới hành động sử dụng hợp lệ.

**“Không chạy nhánh chứa lời gọi lỗi thì toàn bộ quá trình chắc chắn thành công.”** Không bảo đảm chung: verification, superclass/interface loading, hoặc một hành động gián tiếp có thể phát hiện lỗi khác và yêu cầu linking. Với thí nghiệm `App` trên, nhánh không gọi method chạy bình thường; không được coi đó là luật cho mọi lỗi/class.

**“Đã `Class.forName` thành công là tất cả method và field dependencies đúng.”** Không chính xác. Việc tìm và linking class không là chứng nhận toàn bộ symbolic reference chưa dùng đã resolve thành công.

**“Lỗi xuất hiện sau startup thì phải là lỗi resolution.”** Không đúng: lỗi initialization, reflection, verifier, runtime dispatch, service loader hoặc logic ứng dụng có nguyên nhân độc lập.

**“Có thể xóa/sửa JAR đang chạy để JVM resolve lại.”** Không có bảo đảm đó. Các class đã định nghĩa và kết quả resolution có semantics gắn với runtime loader. Thay dependency cần redeploy, tạo class loader mới hoặc cơ chế instrumentation thích hợp; không sửa bằng thao tác tùy tiện lên JAR.

**“Source compile được tức runtime sẽ link được.”** Không đúng khi bytecode caller và version dependency ở runtime khác nhau. Lỗi binary compatibility thường chỉ phát sinh sau khi deploy.

## 8. Quy trình debug lỗi xuất hiện muộn

1. Ghi nhận **exception/error type, message, stack trace và mọi `Caused by`**; xác định instruction/operation đầu tiên có thể trực tiếp hoặc gián tiếp yêu cầu linking.
2. Đối chiếu **artifact biên dịch** và **artifact thực chạy**: classpath, module path, JAR version, duplicate classes và defining class loader.
3. Dùng `javap -v` cho **caller** để xem `Class`, `Methodref`, `Fieldref`, descriptor và opcode; dùng `javap` cho class runtime để xem member thật.
4. Nếu lỗi bị thiếu method/field, kiểm tra tên, descriptor, static/instance flag và access. Thay đổi return type hoặc parameters cũng thay descriptor, có thể gây linkage error dù source trông gần giống.
5. Tách loading, linking và initialization: nơi class được `loadClass`, `forName`, nơi `new`/`invokestatic`/`getstatic` chạy, và nơi static initializer phát sinh lỗi.
6. Tái tạo với hai nhánh có/không sử dụng reference như ví dụ. Quan sát thời điểm lỗi giúp xác định hành động triggering, **không là phép đo trực tiếp thời điểm JVM resolve nội bộ**.
7. Sửa dependency/version/build/deploy hoặc lỗi bytecode thực sự; không che lỗi linkage bằng `catch (LinkageError)` rồi tiếp tục phục vụ request.

Trên HotSpot, có thể bật log class loading để kiểm tra lớp đến từ đâu:

```bash
java -Xlog:class+load=info -cp "build/v2:build/app" demo.App go
```

Log tải lớp **không phải** log đầy đủ của symbolic reference resolution. Khi cần tìm chính xác chiến lược eager/lazy của một JVM, phải dựa trên công cụ chẩn đoán tương ứng phiên bản và vẫn phân biệt hoạt động nội bộ với semantics chuẩn.

## 9. Câu hỏi ôn tập

1. **Có bắt buộc mọi symbolic reference resolve trước initialization không?** Không. Verification và preparation phải hoàn thành trước initialization, còn resolution có thể tiếp diễn sau đó.
2. **Nếu JVM tìm ra method thiếu bằng eager resolution, liệu nó được ném lỗi ở bất kỳ thời điểm nào?** Không. JVMS ràng buộc điểm chương trình quan sát lỗi phải gắn với hành động trực tiếp/gián tiếp cần linkage của reference liên quan.
3. **Vì sao `App` dùng `Api v1` khi compile nhưng chạy với `Api v2` báo `NoSuchMethodError`?** Caller chứa symbolic method reference; ở runtime không tìm thấy method có tên/descriptor tương ứng.
4. **`Class.forName(name, false, loader)` thành công chứng minh điều gì?** Lớp được tìm/nạp/liên kết theo semantics API mà không yêu cầu init. Nó không chứng minh mọi symbolic reference khác sẽ resolve thành công.
5. **Thiếu file class khi `Class.forName` và thiếu class do bytecode tham chiếu có cùng loại lỗi không?** Không nhất thiết: trường hợp trước thường `ClassNotFoundException`, còn symbolic reference liên kết thất bại thường có thể dẫn tới `NoClassDefFoundError`.
6. **Vì sao không thể lấy vị trí `NoSuchMethodError` trên stack trace để kết luận JVM lazy resolve?** Vì eager resolution cũng có thể phát hiện lỗi trước, nhưng việc ném lỗi phải phù hợp điểm sử dụng theo JVMS.

## 10. Nguồn chuẩn

- [JVMS 5.3 — Creation of Classes and Interfaces](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.3): loading và định danh runtime class.
- [JVMS 5.4 — Linking](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.4): lựa chọn eager/lazy, lỗi linking, ràng buộc ném lỗi.
- [JVMS 5.4.3 — Resolution](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.4.3): các loại symbolic reference, hành vi khi resolution thành công/thất bại.
- [JVMS 5.5 — Initialization](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html#jvms-5.5): điều kiện và trình tự initialization.
- [JLS 12.2–12.4 — Execution](https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html): góc nhìn Java Language Specification về nạp, liên kết và khởi tạo lớp.
- [Java 17 Class.forName API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#forName(java.lang.String,boolean,java.lang.ClassLoader)): đối số `initialize` và class loader.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-lifecycle-008">Phân biệt liên kết lớp với khởi tạo lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt liên kết lớp với khởi tạo lớp trong Java

## 1. Vấn đề cần phân biệt

Khi một ứng dụng Java nhắc đến một lớp, JVM không nhất thiết chạy ngay các câu lệnh `static` của lớp đó. Hai giai đoạn thường bị nhầm là **liên kết lớp (linking)** và **khởi tạo lớp (initialization)**:

- **Liên kết**: JVM kiểm tra và chuẩn bị một lớp/interface đã được nạp để nó có thể tham gia thực thi. Ba hoạt động liên quan gồm **verification**, **preparation** và **resolution**.
- **Khởi tạo**: JVM thực thi logic khởi tạo biến `static` và khối `static { ... }` của lớp (với interface là các biểu thức khởi tạo trường), tương ứng phương thức khởi tạo đặc biệt `<clinit>` nếu class file có phương thức này.

Nhìn tổng quan:

```text
Tên lớp + ClassLoader
        │
        ▼
Loading (nạp): tìm/định nghĩa class từ bytecode
        │
        ▼
Linking (liên kết)
   ├─ Verification: kiểm tra class file và ràng buộc bytecode
   ├─ Preparation: tạo static fields, đặt giá trị mặc định
   └─ Resolution: xử lý symbolic references khi cần
        │
        ▼
Initialization (khởi tạo)
   ├─ đảm bảo superclass (và một số superinterface) cần thiết đã init
   ├─ chạy initializer của các trường static và khối static
   └─ hoàn tất hoặc chuyển sang trạng thái lỗi
        │
        ▼
Sử dụng lớp và các thành viên
```

**Sơ đồ chỉ thể hiện quan hệ logic, không phải lịch trình tuyệt đối của JVM.** Theo đặc tả, một lớp phải được nạp trước khi liên kết, và **verification + preparation phải hoàn tất trước khi khởi tạo**. Riêng **resolution được phép trì hoãn**: các tham chiếu có thể chỉ được resolve lúc thật sự sử dụng, kể cả sau khi lớp đã khởi tạo. Vì vậy, không suy ra rằng mọi dependency đã resolve xong trước dòng đầu tiên của `static`.

Cách nhớ thực dụng: **linking chuẩn bị một định nghĩa lớp để JVM sử dụng; initialization thực thi code khởi tạo của chính lớp đó**.

## 2. Linking gồm những hoạt động gì?

**Verification** kiểm tra class file và an toàn kiểu bytecode; lỗi điển hình là `ClassFormatError` hoặc `VerifyError`. **Preparation** tạo các field `static` với giá trị mặc định (`0`, `false`, `null`), chưa thực thi phép gán hay `static` block của ứng dụng. **Resolution** tra cứu class/field/method được mô tả bằng symbolic reference và kiểm tra điều kiện liên kết; nó có thể xảy ra sớm hoặc muộn. Các hằng có `ConstantValue` được gán ở bước riêng khi bắt đầu **initialization** theo JVMS §5.5, không được đồng nhất với việc chạy initializer Java trong preparation.

Ví dụ `static int count = 42;` có giá trị mặc định `0` sau preparation và nhận `42` khi initializer được thực thi. `static final int LIMIT = 42;` là constant variable, có thể được compiler nhúng vào nơi sử dụng; chỉ đọc nó không bắt buộc init kiểu khai báo. Hai công việc có thể đều hoàn thành khi chưa có object nào được tạo.

## 3. Initialization thực sự thực hiện gì?

Lúc khởi tạo lớp, JVM đảm bảo các điều kiện bắt buộc (như khởi tạo superclass trước), rồi thực thi các phép gán/biểu thức khởi tạo trường `static` và khối `static { ... }` **theo thứ tự xuất hiện trong mã nguồn**, tương ứng logic phương thức đặc biệt `<clinit>` được compiler tạo ra nếu cần.

```java
static int a = calculateA();    // bước khởi tạo do chương trình viết
static { log("block 1"); }      // khối khởi tạo
static int b = calculateB();    // bước khởi tạo tiếp theo
```

Những biểu thức này có thể thực hiện I/O, gọi phương thức, thay đổi trạng thái và ném exception. Do đó initialization có side effect có thể quan sát được; linking tự nó không phải là việc chạy static initializer.

**Đừng nhầm ba cơ chế:**

- `<clinit>`: phương thức khởi tạo **lớp/interface**; JVM thực thi ở giai đoạn initialization, không gọi trực tiếp từ Java source.
- `<init>`: constructor **đối tượng**; chạy khi tạo instance sau khi các điều kiện khởi tạo lớp đã được thỏa mãn.
- Khởi tạo field **instance**: một phần của việc khởi tạo đối tượng, không xảy ra chỉ vì lớp đã được liên kết/khởi tạo.

Một lớp không có logic khởi tạo `static` riêng có thể không có `<clinit>` trong class file; điều đó không có nghĩa JVM bỏ qua hoàn toàn quy tắc trạng thái khởi tạo cho lớp.

## 4. Khi nào JVM yêu cầu initialization?

Initialization được yêu cầu khi tạo instance `new T()`, gọi phương thức `static` do T khai báo, đọc/ghi `static` field được khai báo bởi T (riêng việc đọc constant variable đã inline không kích hoạt), hoặc dùng một số API reflection như `Class.forName(name)`.

Ngược lại, chỉ lấy `T.class`, tạo `new T[10]`, gọi `ClassLoader.loadClass` hay `Class.forName(name, false, loader)` **không tự** yêu cầu init T. Với field kế thừa, hãy xét **lớp thực sự khai báo field**, không chỉ tên type ở phía trái source. Bài tiếp theo phân tích chi tiết các trigger, thứ tự cha/con và trường hợp đa luồng.

## 5. Ví dụ chạy được: lớp được nạp nhưng chưa khởi tạo

Tạo tệp `LifecycleDemo.java` dưới đây (Java 17+):

```java
public class LifecycleDemo {
    static class Parent {
        static {
            System.out.println("Parent initialized");
        }

        static int count = 7;
    }

    static class Child extends Parent {
        static {
            System.out.println("Child initialized");
        }

        static void ping() {
            System.out.println("Child.ping");
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("A: class literal");
        Class<?> token = Child.class;

        System.out.println("B: load without initialization");
        Class<?> loaded = Class.forName(
                Child.class.getName(),
                false,
                LifecycleDemo.class.getClassLoader()
        );

        System.out.println("same Class object: " + (token == loaded));

        System.out.println("C: inherited static field");
        System.out.println("count=" + Child.count);

        System.out.println("D: Child static method");
        Child.ping();

        System.out.println("E: done");
    }
}
```

Chạy trong terminal:

```bash
javac LifecycleDemo.java
java LifecycleDemo
```

Stdout chính xác khi chạy lần đầu trong JVM mới:

```text
A: class literal
B: load without initialization
same Class object: true
C: inherited static field
Parent initialized
count=7
D: Child static method
Child initialized
Child.ping
E: done
```

**Giải thích thứ tự:**

1. Lấy `Child.class` và dùng `Class.forName(..., false, ...)` trả đối tượng `Class` cho cùng lớp; cả hai không yêu cầu chạy `Child.<clinit>`.
2. `Child.count` được khai báo trong `Parent`. Việc đọc trường đó kích hoạt initialization của **Parent**, do đó in `Parent initialized` trước `count=7`; không kích hoạt initialization của Child.
3. Gọi `Child.ping()` là active use của phương thức `static` khai báo tại Child. JVM khởi tạo Child (Parent đã xong) rồi mới chạy thân `ping()`.

Ví dụ giúp quan sát **thời điểm initialization**, không đo trực tiếp thời điểm các bước verification/preparation/resolution đã được JVM chọn thực hiện.

## 6. Quan hệ superclass, interface và ClassLoader

### 6.1. Superclass trước subclass

Khi khởi tạo một class, JVM cần khởi tạo các superclass chưa khởi tạo trước class đó. Vì thế lần đầu `new Child()` có thể chạy initializer của Parent rồi Child. Một **static field được khai báo tại Parent** chỉ khởi tạo Parent ngay cả khi source viết `Child.field` (như mục 6).

Khi khởi tạo class, đặc tả Java hiện đại còn quy định khởi tạo các **superinterface khai báo default method** thích hợp trước class đó. Không được nói rằng *mọi* interface mà class implements đều khởi tạo trước class; interface không có default method có thể chưa khởi tạo.

### 6.2. Interface khác class

Một interface được khởi tạo khi có tác nhân yêu cầu, chẳng hạn truy cập một trường `static` **không phải compile-time constant** do nó khai báo. Initialization của interface **không tự khởi tạo toàn bộ superinterface**.

Trong interface, các field mặc nhiên là `public static final`, nhưng không phải tất cả đều là compile-time constants. Ví dụ:

```java
interface Flag {
    int CONSTANT = 10;
    int RUNTIME_VALUE = Integer.parseInt("20");
}
```

Đọc `Flag.CONSTANT` không bắt buộc initialize Flag; đọc `Flag.RUNTIME_VALUE` yêu cầu initialize Flag.

### 6.3. Một binary name có thể có nhiều trạng thái init

Với cơ chế plugin hoặc container sử dụng nhiều class loader, cùng binary name `com.example.Plugin` được định nghĩa bởi hai defining loaders khác nhau tương ứng với **hai runtime classes**. Chúng có các `static` field và trạng thái initialization độc lập. JVM không dùng bảng "đã init" duy nhất theo **tên chữ** của lớp.

Tương tự, việc cùng một `Class<?>` đã initialization thành công được chia sẻ giữa các thread trong JVM; gọi lặp một `static` method không chạy `<clinit>` lại.

## 7. So sánh nhanh để tránh nhầm

| Câu hỏi | Linking | Initialization |
|---|---|---|
| Mục đích | Kiểm tra và chuẩn bị class để JVM sử dụng, resolve tham chiếu khi cần | Thực thi logic khởi tạo `static` của class/interface |
| Bắt buộc đã loading? | Có | Có |
| Chạy khối `static { ... }`? | Không | Có, khi class có khối này |
| Tạo và gán mặc định cho `static` fields? | Có, ở preparation | Giá trị có thể được thay bằng các initializer |
| Có thể tạo lỗi? | `VerifyError`, `LinkageError` như `NoSuchMethodError` | `ExceptionInInitializerError`, trạng thái init lỗi |
| Thời điểm | Verification/preparation trước initialization; resolution có thể trì hoãn | Theo các điều kiện active use |
| Có chạy lại khi gọi method static lần hai? | Thường không phải việc chạy lại toàn bộ linking cho mỗi call | Không chạy lại `<clinit>` đã hoàn tất |
| Có thể quan sát trực tiếp? | Qua bytecode, lớp được load, lỗi linkage, công cụ VM | Qua side effect của static initializer và trạng thái lỗi |

## 8. Câu hỏi tự kiểm tra

1. **`Class.forName("x.Y", false, loader)` có bắt buộc chạy `Y.<clinit>` không?** Không; tham số `false` không yêu cầu initialization.
2. **JVM có được khởi tạo class chưa verification/preparation không?** Không; cả hai phải hoàn tất trước initialization.
3. **Mọi symbolic reference phải resolve trước dòng đầu của `static` không?** Không; resolution có thể lazy và còn tiếp tục sau initialization.
4. **`static int n = 5` có giá trị gì tại thời điểm preparation?** Giá trị mặc định `0`; phép gán `5` thuộc initialization.
5. **`static final int N = 5` khi dùng ở lớp khác có bắt buộc init lớp khai báo không?** Không; đó là constant variable và có thể được inline.
6. **Đọc `Sub.field` sẽ init Sub hay Super nếu `field` khai báo trong Super?** Chỉ class thực sự khai báo field là Super phải init khi truy cập field không phải constant variable.
7. **Hai ClassLoader định nghĩa hai class cùng tên có dùng chung một lần `<clinit>` không?** Không; là hai runtime classes khác nhau.
8. **Lần đầu initialization thất bại, gọi lại có thực thi `<clinit>` lần nữa không?** Không; runtime class chuyển sang trạng thái erroneous và sử dụng tiếp sẽ thất bại.

## Tài liệu chuẩn để học sâu

- [Java Language Specification, Java SE 26, Chương 12 — Execution](https://docs.oracle.com/javase/specs/jls/se26/html/jls-12.html), đặc biệt `12.2 Loading, `12.3 Linking và `12.4 Initialization: định nghĩa và những sự kiện kích hoạt initialization.
- [Java Virtual Machine Specification, Java SE 25, Chương 5 — Loading, Linking, and Initializing](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html), đặc biệt `5.4 Linking và `5.5 Initialization: quy tắc verifier, preparation, thời điểm resolution, đồng bộ hóa initialization.
- [Java Class API — forName](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/Class.html): overload `initialize=true/false`.

Khi debug hành vi ClassLoader, hãy xác định **runtime class nào** đang được nói đến, **lớp đó đã load/link tới mức nào**, và **thao tác nào thực sự yêu cầu initialization**. Đây là cách giải thích đúng những trường hợp đã có `Class<?>` nhưng chưa thấy static initializer chạy, hoặc initialization ném lỗi dù bytecode trước đó đã được JVM chấp nhận.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-lifecycle-009">Khởi tạo lớp trước khi thực thi các thao tác yêu cầu</a>

<details>
<summary>Xem chi tiết</summary>

# Khởi tạo lớp trước khi thực thi các thao tác yêu cầu trong Java

Một lớp có thể đã được **nạp (loaded)** và **liên kết (linked)** nhưng khối `static` của nó vẫn chưa chạy. JVM chỉ **khởi tạo (initialize)** lớp hoặc interface khi một thao tác cụ thể đòi hỏi điều đó. Trước khi cho phép thao tác tiếp tục, JVM bảo đảm quá trình khởi tạo cần thiết đã hoàn tất, hoặc báo lỗi nếu khởi tạo thất bại. Cơ chế này giải thích vì sao `SomeType.class` thường không in log `static`, nhưng `new SomeType()` hoặc `SomeType.run()` lại có thể in log ngay lần đầu sử dụng.

Bài này tập trung vào **khi nào và theo thứ tự nào JVM khởi tạo lớp trước các thao tác yêu cầu**, thuộc nhóm **Vòng đời lớp** của Java core – classloader. Mục tiêu là dự đoán được thời điểm chạy `<clinit>`, nhận diện thao tác kích hoạt và không kích hoạt khởi tạo, hiểu bảo đảm trong môi trường nhiều luồng và phân tích được lỗi khi static initializer thất bại.

## 1. Phân biệt loading, linking và initialization

| Giai đoạn | Công việc chính | Có chạy các lệnh trong `static { ... }` không? |
| --- | --- | --- |
| **Loading** | Tìm/đọc định nghĩa nhị phân, tạo đối tượng `Class` tương ứng với tên và defining class loader | Không phải bước chạy static initializer |
| **Linking – verification** | Kiểm chứng tính hợp lệ và an toàn cấu trúc của bytecode | Không |
| **Linking – preparation** | Cấp phát/trang bị trạng thái cho các static field và đặt giá trị mặc định như `0`, `false`, `null` | Không chạy mã Java của static initializer |
| **Linking – resolution** | Giải quyết các symbolic reference tới kiểu, field, method theo quy tắc JVM; có thể sớm hoặc theo nhu cầu | Bản thân một thao tác resolution thông thường không đồng nghĩa khởi tạo lớp được tham chiếu |
| **Initialization** | Thiết lập các giá trị hằng qua metadata `ConstantValue` khi áp dụng, khởi tạo superclass/default-method superinterfaces cần thiết, thực thi logic khởi tạo lớp/interface | Có, khi class/interface có mã cần thực thi |

Trong JVM Java 17, phần **initialization** là lúc các `final static` có `ConstantValue` được gán giá trị đặc tả và phương thức khởi tạo `<clinit>` được thực thi. Trước đó, các static field đã được chuẩn bị với giá trị mặc định. Do vậy không nên đồng nhất việc “static field đã tồn tại” với việc “static field đã hoàn tất gán giá trị khởi tạo”.

Ví dụ:

```java
class Settings {
    static int port = loadPort();

    static int loadPort() {
        System.out.println("Loading port");
        return 8080;
    }
}
```

Mã `loadPort()` chạy **khi khởi tạo `Settings`**, không phải chỉ vì một công cụ đã load `Settings.class` vào JVM. Phân biệt này còn quan trọng khi dùng classpath scanning, reflection, plugin loader và công cụ kiểm tra metadata.

Một class đã hoàn thành initialization được xem là đã khởi tạo trong phạm vi **định nghĩa class đó**. Cùng một tên lớp nếu được hai defining loader khác nhau định nghĩa sẽ có hai thực thể `Class` và trạng thái khởi tạo riêng; không có quy tắc “một lần cho mọi lớp cùng tên trong toàn bộ ứng dụng”.

## 2. Những thao tác nào buộc khởi tạo?

Theo quy tắc Java/JVM, thời điểm kích hoạt phụ thuộc **thao tác được thực thi** và **lớp/interface thực sự khai báo thành viên đã được resolve**, chứ không chỉ chuỗi ký tự dùng để viết tên lớp trong mã nguồn.

| Thao tác / tình huống | Có yêu cầu khởi tạo? | Giải thích |
| --- | --- | --- |
| `new Worker()` | **Có** đối với `Worker` trước khi tạo instance | Bytecode `new` yêu cầu class được khởi tạo; superclass và một số superinterface cũng được khởi tạo theo quy tắc |
| `Worker.work()` với `static void work()` do `Worker` khai báo | **Có** | Lệnh `invokestatic` yêu cầu khởi tạo kiểu khai báo method được resolve |
| Đọc `Worker.count` với `static int count` không phải constant variable | **Có** | `getstatic` yêu cầu kiểu khai báo field đã được khởi tạo |
| Ghi `Worker.count = 5` | **Có** | `putstatic` yêu cầu kiểu khai báo field đã được khởi tạo |
| `Worker.COMPILE_TIME_CONST` với `static final int` gán constant expression | **Không chỉ vì việc đọc hằng này** | Compiler có thể thay giá trị trực tiếp vào bytecode; không cần thực hiện `getstatic` tới field |
| `Worker.class` | **Không do riêng class literal** | Có thể lấy đối tượng `Class` mà chưa khởi tạo `Worker` |
| `new Worker[10]` | **Không do riêng việc tạo mảng** | Tạo mảng đối tượng khác với tạo instance `Worker`; component class không bị buộc khởi tạo |
| `ClassLoader.loadClass("pkg.Worker")` | **Không do riêng lệnh loadClass** | Thường đưa class vào trạng thái đã load; chính việc load không đồng nghĩa initialization |
| `Class.forName("pkg.Worker")` | **Có** theo overload một tham số | Overload này yêu cầu initialization của kiểu tìm được |
| `Class.forName(name, false, loader)` | **Không yêu cầu initialization** | Tham số `initialize=false` chủ ý tránh kích hoạt |
| Lấy metadata qua nhiều API như `getName()`, `getDeclaredMethods()` | **Không do riêng thao tác tra cứu metadata thông thường** | Reflection tra cứu và reflection gọi thực thi là hai việc khác nhau |
| Gọi static method qua `Method.invoke` hoặc đọc static field qua `Field.get` | **Có khi việc truy cập thực sự cần** | Một số phương thức reflection kích hoạt initialization của declaring class |
| JVM chọn class khởi đầu chương trình | **Có** | Initial class/interface được khởi tạo theo quy tắc khởi động JVM |
| Khởi tạo một subclass | **Có** với các superclass chưa được khởi tạo | Bảo đảm các lớp cha cần thiết được khởi tạo trước lớp con |

Ở tầng bytecode, bốn lệnh quan trọng là **`new`, `getstatic`, `putstatic`, `invokestatic`**. Với `getstatic`, `putstatic` và `invokestatic`, kiểu cần khởi tạo là **declaring class/interface** của field hoặc method sau khi symbolic reference được resolve.

Các trường hợp đặc biệt hơn gồm lần gọi đầu tiên một `MethodHandle` đã được resolve thuộc dạng `REF_getStatic`, `REF_putStatic`, `REF_invokeStatic`, `REF_newInvokeSpecial`; bootstrap method của một số call site `invokedynamic` cũng có thể kích hoạt khởi tạo. Đây là mở rộng cùng nguyên tắc: phải hoàn tất trạng thái của kiểu trước khi thực thi thao tác tĩnh/tạo instance mà lời gọi thực sự yêu cầu.

**Cần phân biệt hai phát biểu:**

- “Java source nhắc tới tên `Worker`” **không đủ** để kết luận `Worker` khởi tạo.
- “JVM thực thi `getstatic` hoặc `invokestatic` tới thành viên khai báo bởi `Worker`” là một **điều kiện yêu cầu khởi tạo** của `Worker` nếu nó chưa khởi tạo.

## 3. Ví dụ chạy được: điều gì kích hoạt và không kích hoạt?

Tạo duy nhất file `InitializationDemo.java`:

```java
public class InitializationDemo {
    static class Parent {
        static final int LIMIT = 42; // constant variable: có thể được inline

        static int first = announce("Parent.first", 7);

        static {
            System.out.println("Parent.block");
        }

        static final Integer BOXED = createBoxed(); // không là constant variable

        static int announce(String label, int value) {
            System.out.println(label);
            return value;
        }

        static Integer createBoxed() {
            System.out.println("Parent.BOXED");
            return 99;
        }
    }

    static class Child extends Parent {
        static {
            System.out.println("Child.block");
        }

        static void ping() {
            System.out.println("Child.ping");
        }
    }

    static class Unused {
        static {
            System.out.println("Unused.block");
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("1 LIMIT=" + Child.LIMIT);
        System.out.println("2 literal=" + Unused.class.getSimpleName());
        System.out.println("3 array=" + new Unused[0].length);

        Class<?> type = Class.forName(
            "InitializationDemo$Unused",
            false,
            InitializationDemo.class.getClassLoader()
        );
        System.out.println("4 loaded=" + type.getSimpleName());

        System.out.println("5 call Child.ping()");
        Child.ping();

        System.out.println("6 BOXED=" + Child.BOXED);
        System.out.println("7 forName true");
        Class.forName("InitializationDemo$Unused");

        System.out.println("8 again");
        Child.ping();
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac --release 17 InitializationDemo.java
java InitializationDemo
```

Stdout:

```text
1 LIMIT=42
2 literal=Unused
3 array=0
4 loaded=Unused
5 call Child.ping()
Parent.first
Parent.block
Parent.BOXED
Child.block
Child.ping
6 BOXED=99
7 forName true
Unused.block
8 again
Child.ping
```

Đọc theo thứ tự:

1. `Child.LIMIT` là cách truy cập hằng compile time được **khai báo ở `Parent`**. Giá trị `42` có thể được nhúng vào mã client; trong ví dụ không có `Parent.first` hay `Child.block` ở bước 1.
2. `Unused.class` chỉ lấy `Class<?>`; `new Unused[0]` chỉ tạo mảng. Không bước nào buộc chạy `Unused.block`.
3. `Class.forName(name, false, loader)` chỉ yêu cầu tìm/nạp và liên kết theo nhu cầu, **không yêu cầu** chạy static block; vì vậy dòng `4 loaded=Unused` vẫn xuất hiện trước `Unused.block`.
4. Lần đầu gọi `Child.ping()`, JVM phải khởi tạo `Child` trước khi chạy method body. `Parent` là superclass chưa được khởi tạo, nên `Parent` chạy trước: gán `first`, chạy static block, tính `BOXED`; sau đó `Child.block` mới chạy, cuối cùng method in `Child.ping`.
5. `Child.BOXED` thực tế là static field do `Parent` khai báo. `Parent` đã khởi tạo, nên chỉ in giá trị `99`; đọc field qua tên subclass **không tự tạo thêm lần chạy `<clinit>`**.
6. `Class.forName("InitializationDemo$Unused")` với overload một đối số yêu cầu khởi tạo `Unused`, vì vậy `Unused.block` lúc này mới xuất hiện.
7. Gọi `Child.ping()` lần nữa chỉ chạy **method body**. Khởi tạo `Parent` và `Child` đã hoàn tất và không lặp lại.

Chú ý: code trên là **một chương trình chạy độc lập**. Nếu chỉ chạy `javap` mà không chạy `java InitializationDemo`, việc phân tích class file bằng công cụ không chứng minh rằng static initializer của chính ứng dụng đã chạy.

## 4. `<clinit>` được tạo và thực thi như thế nào?

`javac` thường tổng hợp static field initializer và các khối `static { ... }` của **cùng một class** thành phương thức khởi tạo class tên **`<clinit>`** trong bytecode. Chương trình Java không gọi trực tiếp phương thức này.

Với ví dụ `Parent`, thứ tự logic là:

```text
Giá trị static ban đầu sau preparation: first=0, BOXED=null
          |
JVM quyết định phải initialize Parent
          |
Áp dụng ConstantValue của LIMIT (=42) nếu cần
          |
first = announce("Parent.first", 7)  -> in "Parent.first"
          |
static { println("Parent.block"); } -> in "Parent.block"
          |
BOXED = createBoxed()               -> in "Parent.BOXED"
          |
Parent hoàn tất initialization
          |
JVM tiếp tục initialize Child
```

**Thứ tự trong cùng class:** các initializer của static field và static block chạy theo **thứ tự xuất hiện trong source**. Sau khi superclass và các superinterface thuộc diện bắt buộc được khởi tạo, JVM mới thực thi phần khởi tạo của class hiện tại.

Dùng:

```bash
javap -c -p 'InitializationDemo$Parent'
javap -c -p 'InitializationDemo$Child'
```

Trong PowerShell và các shell hỗ trợ dấu nháy đơn, cú pháp trên giữ nguyên ký tự `$` trong tên nested class. Trong Windows Command Prompt có thể dùng `javap -c -p "InitializationDemo$Parent"`. Tìm khối `static {};` do `javap` hiển thị: đó là phần biểu diễn bytecode của `<clinit>`, với những lệnh như `putstatic`, `invokestatic` và lệnh in output.

**Không nhất thiết class nào cũng có `<clinit>`.** Class không cần công việc khởi tạo tại runtime có thể không có phương thức ấy. Tuy vậy JVM vẫn theo dõi trạng thái “đã initialized” để đáp ứng quy tắc vòng đời.

### Trạng thái mặc định và đọc giá trị trước khi gán

Trong quá trình khởi tạo, một static field có thể đã tồn tại với giá trị mặc định trong khi initializer của nó chưa chạy đến. Để nhìn thấy điều này, tạo `InitializationOrder.java`:

```java
public class InitializationOrder {
    static int first = readSecond();
    static int second = 9;

    static int readSecond() {
        return second;
    }

    public static void main(String[] args) {
        System.out.println(first + "," + second);
    }
}
```

```bash
javac --release 17 InitializationOrder.java
java InitializationOrder
```

Stdout chính xác:

```text
0,9
```

Tại lúc gán `first`, `readSecond()` thấy `second` còn bằng giá trị mặc định `0`; lệnh `second = 9` chỉ xảy ra sau đó. Đọc trực tiếp một field được khai báo *sau* trong initializer có thể vướng quy tắc **illegal forward reference** của ngôn ngữ; qua một method như ví dụ thì hợp lệ. Không nên dựa vào thứ tự phức tạp như vậy để thiết kế trạng thái chia sẻ.

## 5. Superclass và superinterface: cái nào được khởi tạo trước?

Khi khởi tạo một **class**, JVM cần khởi tạo các superclass chưa được khởi tạo. Ngoài ra, những **superinterface trực tiếp hoặc gián tiếp khai báo default method** cũng thuộc diện khởi tạo bắt buộc theo quy tắc JVMS/JLS. Điều này tinh tế hơn câu “interface luôn khởi tạo sau class” hoặc “mọi interface đều khởi tạo trước class”: cả hai câu đều sai.

Trong ví dụ trước, `Child` buộc khởi tạo `Parent` trước, nên `Parent.first`, `Parent.block`, `Parent.BOXED` luôn đứng trước `Child.block`.

### Static field kế thừa: kiểu khai báo mới là điều quyết định

Tạo file `FieldOwnerDemo.java`:

```java
public class FieldOwnerDemo {
    static class Base {
        static int count = init();

        static int init() {
            System.out.println("Base.init");
            return 10;
        }
    }

    static class Derived extends Base {
        static {
            System.out.println("Derived.init");
        }
    }

    public static void main(String[] args) {
        System.out.println(Derived.count);
    }
}
```

```bash
javac --release 17 FieldOwnerDemo.java
java FieldOwnerDemo
```

Stdout:

```text
Base.init
10
```

`Derived.count` trong source chỉ ra field **được khai báo bởi `Base`**, do đó JVM yêu cầu khởi tạo `Base`, **không tự khởi tạo `Derived`**. Khác với `new Derived()` hoặc gọi static method thực sự được khai báo bởi `Derived`.

### Interface có default method

Tạo file `DefaultInterfaceDemo.java`:

```java
public class DefaultInterfaceDemo {
    interface Feature {
        Object MARKER = report();

        static Object report() {
            System.out.println("Feature.init");
            return new Object();
        }

        default void execute() { }
    }

    static class Service implements Feature {
        static {
            System.out.println("Service.init");
        }
    }

    public static void main(String[] args) {
        new Service();
    }
}
```

```bash
javac --release 17 DefaultInterfaceDemo.java
java DefaultInterfaceDemo
```

Stdout:

```text
Feature.init
Service.init
```

`Service` là class implement `Feature`; `Feature` khai báo một `default` method, nên initialization của `Service` dẫn đến khởi tạo `Feature` trước. `MARKER` được tính trong quá trình initialization của interface, dù code ở `main` không trực tiếp truy cập `MARKER`.

**Chiều ngược lại không đối xứng:** tự khởi tạo một interface không mặc nhiên khởi tạo mọi superinterface của nó. Một truy cập static field qua tên interface con cũng phải xét nơi field **thực sự được khai báo**. Nếu interface có field hằng compile time được inline, truy cập giá trị ấy cũng không nhất thiết khởi tạo interface khai báo.

## 6. Reflection và việc nạp class không giống initialization

Có hai overload dễ nhầm:

```java
Class<?> a = Class.forName("com.example.Plugin");
Class<?> b = Class.forName(
    "com.example.Plugin",
    false,
    Thread.currentThread().getContextClassLoader()
);
```

Đoạn trên là **minh họa API**, chỉ chạy nếu `com.example.Plugin` tồn tại và class loader phù hợp. Với overload một tham số, việc tìm class đi kèm yêu cầu khởi tạo; với overload ba tham số, `false` có nghĩa **không yêu cầu initialize lúc tra cứu**. Đến khi client thật sự `new`, gọi static method, truy cập non-constant static field hoặc reflection thực thi một thao tác cần initialization, quá trình đó vẫn có thể bắt đầu.

Các thao tác thường **không tự yêu cầu** khởi tạo class đích: lấy `T.class`, tạo `T[]`, xem tên từ `Class<?>`, tìm danh sách method/field, kiểm tra kiểu bằng `instanceof` hay `checkcast`. Một API metadata vẫn có thể cần load/resolve các kiểu liên quan hoặc sinh lỗi khác; “không khởi tạo class đích” không có nghĩa “không làm bất kỳ việc gì”.

Ngược lại, các thao tác thực sự **gọi** static method (`Method.invoke`), **đọc/ghi** static field (`Field.get`/`Field.set`) sẽ tuân thủ điều kiện khởi tạo của declaring class. Phân biệt tra cứu descriptor với sử dụng thành viên giúp tránh gây tác dụng phụ khi plugin scanner chỉ muốn đọc metadata.

## 7. JVM bảo đảm khởi tạo đúng một lần như thế nào khi có nhiều luồng?

Đối với một **định nghĩa class/interface**, quy trình initialization phối hợp các thread để tránh chạy hai lần static initializer. Có thể hình dung trạng thái:

```text
VERIFIED + PREPARED, CHƯA INITIALIZED
        |
        | thread đầu tiên thực hiện thao tác yêu cầu
        v
INITIALIZATION IN PROGRESS (một thread sở hữu)
        |               |
        |               +-- thread khác chờ kết quả
        |               +-- chính thread đó yêu cầu đệ quy: JVM xử lý riêng
        |
        +-- hoàn tất thành công --> INITIALIZED
        |
        +-- thất bại ------------> ERRONEOUS
                                    |
                                    +-- lần yêu cầu sau: NoClassDefFoundError
```

Quy tắc quan trọng:

- Nếu thread A đang khởi tạo `T`, thread B đồng thời thực hiện thao tác yêu cầu `T` phải chờ kết quả theo cơ chế JVM. Các thread sử dụng thành công sau đó nhìn thấy kết quả initialization theo bảo đảm của mô hình bộ nhớ Java.
- Nếu chính thread A vô tình yêu cầu khởi tạo lại `T` trong lúc đang thực hiện `T.<clinit>`, JVM nhận biết yêu cầu đệ quy từ cùng thread; không chạy lại toàn bộ initializer.
- Điều này **không loại bỏ deadlock** nếu hai thread đồng thời khởi tạo hai class khác nhau rồi mỗi class lại yêu cầu chờ class kia. Không thực hiện khóa chéo, I/O vô hạn hoặc thread join trong static initializer.
- Sau khi khởi tạo thành công, JVM **không chạy lại** `<clinit>` chỉ vì nhiều instance của class được tạo sau đó. Constructor (`<init>`) của từng instance là một cơ chế khác.

### Ứng dụng: Initialization-on-demand holder

Mẫu phổ biến dùng trạng thái initialization để lazy-create một singleton:

```java
public final class LazyService {
    private LazyService() { }

    private static final class Holder {
        static final LazyService INSTANCE = new LazyService();
    }

    public static LazyService getInstance() {
        return Holder.INSTANCE;
    }
}
```

Đây là **class hoàn chỉnh có thể biên dịch**; đoạn này không có `main`. Lần gọi `LazyService.getInstance()` phải khởi tạo lớp `LazyService` trước khi gọi method, nhưng **chưa bắt buộc khởi tạo nested `Holder` trước khi tới dòng truy cập `Holder.INSTANCE`**. Việc thực hiện `getstatic` cho `Holder.INSTANCE` kích hoạt khởi tạo `Holder` và tạo một `LazyService` duy nhất cho defining loader của `Holder`. Mẫu này dựa vào cơ chế đồng bộ initialization của JVM, không cần tự thêm `synchronized` vào getter.

Một static field được khai báo là `final` **không tự động** trở thành constant variable; `Holder.INSTANCE` là reference được tính bằng `new`, nên đọc nó phải tuân theo `getstatic`, khác với hằng nguyên `static final int LIMIT = 42`.

## 8. Điều gì xảy ra nếu static initializer thất bại?

Tạo file `FailedInitialization.java`:

```java
public class FailedInitialization {
    static class Broken {
        static int value = fail();

        static int fail() {
            throw new IllegalStateException("boom");
        }
    }

    public static void main(String[] args) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                System.out.println(Broken.value);
            } catch (LinkageError error) {
                System.out.println(
                    attempt + ": " + error.getClass().getSimpleName()
                );
            }
        }
    }
}
```

```bash
javac --release 17 FailedInitialization.java
java FailedInitialization
```

Stdout:

```text
1: ExceptionInInitializerError
2: NoClassDefFoundError
```

Lần đầu truy cập `Broken.value`, JVM bắt đầu initialization và gọi `fail()`. `IllegalStateException` là exception không thuộc `Error`, vì thế JVM bọc nó trong `ExceptionInInitializerError`, một subclass của `LinkageError`. Định nghĩa `Broken` chuyển sang trạng thái **erroneous**. Lần truy cập thứ hai không khởi tạo lại; JVM phát `NoClassDefFoundError` vì class đã thất bại trong quá trình initialization.

Nếu lỗi gốc trong `<clinit>` đã là một subclass của `Error`, JVM thường chuyển tiếp `Error` ấy trong lượt thất bại ban đầu, thay vì bọc bằng `ExceptionInInitializerError`. Nếu superclass thất bại khi đang khởi tạo subclass, subclass cũng có thể bị đánh dấu erroneous. Khi phân tích, hãy xem **exception đầu tiên và cause**; chỉ nhìn dòng `NoClassDefFoundError` sau đó dễ kết luận nhầm rằng file class “biến mất”.

Trong một class loader đang dùng, lỗi initialization thường không khắc phục bằng việc gọi lại cùng class. Cần sửa nguyên nhân gốc (cấu hình, dependency, exception trong static code), rồi tái khởi động hoặc sử dụng một định nghĩa mới qua class loader phù hợp khi kiến trúc thực sự hỗ trợ.

**Quy tắc thiết kế thực tế:** tránh network call, truy vấn DB, thao tác file dễ thất bại và phụ thuộc lẫn nhau giữa nhiều class trong static initializer. Khi cần retry một tài nguyên, dùng API khởi tạo có quản lý trạng thái và lỗi rõ ràng thay vì biến lỗi tạm thời thành class erroneous.

## 9. Dùng `javap` để nhận diện trigger trước khi chạy

Sau khi biên dịch `InitializationDemo.java`, thực hiện:

```bash
javap -c -p InitializationDemo
javap -c -p 'InitializationDemo$Parent'
javap -c -p 'InitializationDemo$Child'
javap -v -p InitializationDemo
```

Mục tiêu quan sát:

1. Tìm `invokestatic` tại call site `Child.ping()`. Lệnh này khiến JVM phải bảo đảm declaring class đã được initialize trước khi method chạy.
2. Tìm `getstatic` cho `Parent.BOXED` (có thể được tham chiếu qua tên `Child` ở source nhưng owner sau biên dịch là nơi field khai báo).
3. Với `Child.LIMIT`, compiler có thể tạo chuỗi literal `"1 LIMIT=42"` để in hoặc dùng một hằng trực tiếp, **không** cần lệnh đọc static field `LIMIT` từ `Parent`. Java có thể gộp constant expression ngay khi biên dịch; hãy kiểm tra bytecode thực tế.
4. Trong output `javap -c -p 'InitializationDemo$Parent'`, khối `static {};` mô tả trình tự của logic khởi tạo class, kể cả việc ghi các static field không phải constant variable.
5. Dùng `javap -v` để xem mục constant pool, lệnh tham chiếu trường/phương thức và metadata `ConstantValue` khi có. **Sự tồn tại của tên kiểu trong constant pool chưa chứng minh class đã được initialize.**

Thông điệp quan trọng là: **symbolic reference được ghi khi biên dịch, resolution xác định thực thể runtime, còn initialization bắt đầu khi điều kiện JVM yêu cầu**. Vì vậy chỉ nhìn mã source có dấu chấm `SomeClass.member` không đủ; phải xét member là static/instance, có phải compile-time constant, ai thực sự khai báo, và lệnh bytecode được phát.

Nếu cần logging lớp được nạp, có thể thử `java -Xlog:class+load=info InitializationDemo` trên JVM HotSpot hỗ trợ unified logging. Tuy nhiên **log load không phải log khởi tạo**. Để kiểm chứng initialization của ví dụ, quan sát các dòng do `static` in và thứ tự lệnh thực sự.

## 10. Các hiểu lầm thường gặp

| Nhận định sai | Cách hiểu đúng |
| --- | --- |
| “Chỉ cần import hoặc nhắc tới tên class là chạy static block.” | `import` là cú pháp compile time; class literal, metadata lookup và việc tạo mảng không tự gây initialization. |
| “Loading một class đồng nghĩa khởi tạo class đó.” | Loading/linking và initialization là các pha khác nhau; `Class.forName(name, false, loader)` minh họa sự tách biệt. |
| “Mọi static field access luôn chạy static block.” | Field hằng compile time có thể được inline; static field kế thừa chỉ yêu cầu initialize nơi khai báo field thực sự. |
| “Class con có thể chạy static initializer trước superclass.” | Khi bắt đầu initialization của subclass, superclass cần thiết phải hoàn thành initialization trước. |
| “Mọi interface cha luôn được khởi tạo trước interface con.” | Tự khởi tạo một interface không mặc nhiên khởi tạo các superinterface; default-method superinterfaces quan trọng khi khởi tạo một class. |
| “Mỗi `new` sẽ chạy lại static block.” | Static initialization cho một định nghĩa class chỉ chạy một lần thành công; constructor chạy cho từng instance. |
| “Một initializer lỗi sẽ được tự động thử lại ở lần truy cập tiếp.” | Định nghĩa class rơi vào trạng thái erroneous; các lần tiếp theo thường ném `NoClassDefFoundError`. |
| “`static final` nào cũng được inline.” | Chỉ **constant variable** với kiểu primitive/String và constant expression đáp ứng điều kiện; object được tạo bằng `new` không thuộc nhóm này. |
| “Hai loader nạp cùng tên class dùng chung static state.” | Danh tính class gắn với defining loader; mỗi định nghĩa có static state và trạng thái initialization riêng. |

## 11. Câu hỏi tự kiểm tra

**1.** `Report.class.getName()` và `new Report()` khác nhau như thế nào về initialization?

**Đáp án:** class literal và đọc tên không tự yêu cầu `Report` initialization; tạo instance `Report` bằng `new` là thao tác yêu cầu class đã initialize trước khi instance được tạo.

**2.** Vì sao `Derived.count` chỉ in `Base.init` ở ví dụ `FieldOwnerDemo`?

**Đáp án:** `count` là static field được khai báo trong `Base`. `getstatic` sau resolution áp dụng cho declaring class `Base`, không buộc initialize `Derived` chỉ vì cú pháp source dùng tên `Derived`.

**3.** `static final Integer ANSWER = 42` và `static final int ANSWER = 42` có giống nhau khi đọc qua class không?

**Đáp án:** Không. `Integer` không phải loại constant variable theo JLS nên initializer và `getstatic` liên quan có thể yêu cầu initialization. `int` với constant expression `42` là constant variable, có thể được inline vào class sử dụng.

**4.** Tại sao trong `InitializationDemo` cả `Parent.first` và `Parent.BOXED` in ra trước `Child.block`?

**Đáp án:** `Child` là subclass nên quá trình initialization yêu cầu superclass `Parent` hoàn thành trước. Trong `Parent`, static field initializer và static block thực thi theo thứ tự source.

**5.** Nếu thread A đang chạy `<clinit>` của một class và thread B cần sử dụng static method của class đó, B có thể chạy method trước khi initialization hoàn tất không?

**Đáp án:** Không. JVM phối hợp initialization giữa các thread; B chờ trạng thái thành công hoặc nhận lỗi. Điều này không có nghĩa mọi trường hợp static initializer phụ thuộc khóa giữa các class đều không thể deadlock.

**6.** Vì sao log lỗi có `NoClassDefFoundError` dù file `.class` vẫn có mặt?

**Đáp án:** Một khả năng là initialization trước đó thất bại, class bị đánh dấu erroneous và lần yêu cầu sau ném `NoClassDefFoundError`. Cần xem exception gốc ban đầu cùng cause, đồng thời vẫn kiểm tra các khả năng khác như dependency/classpath thiếu.

## Tóm tắt quy trình suy luận

Khi muốn biết “dòng này đã làm chạy `static` chưa?”, hãy xác định:

1. Bytecode đang thực hiện **`new`**, **`getstatic`**, **`putstatic`**, **`invokestatic`**, hay thao tác tương đương qua reflection/MethodHandle?
2. Kiểu nào **thực sự được tham chiếu và khai báo member** sau resolution? Có phải compile-time constant đã được inline không?
3. Kiểu ấy đã initialize thành công chưa? Nếu chưa, JVM phải xử lý superclass và các superinterface có default method cần thiết, rồi mới chạy `<clinit>` của chính kiểu đó.
4. Nếu đồng thời có thread khác khởi tạo hoặc initializer thất bại, thao tác đang xét sẽ chờ hoặc nhận lỗi theo trạng thái class.

Hai nguồn chuẩn để đối chiếu các trường hợp ranh giới và điều kiện lỗi:

- [Java Language Specification 17 – 12.4 Initialization of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.4).
- [Java Virtual Machine Specification 17 – 5.5 Initialization](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.5).

Áp dụng đúng các quy tắc này giúp dự đoán side effect của class loader/reflection, tổ chức lazy singleton an toàn và tìm được nguyên nhân của lỗi static initialization khi ứng dụng chạy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-lifecycle-010">Vòng đời lớp khác vòng đời đối tượng như thế nào</a>

<details>
<summary>Xem chi tiết</summary>

# Vòng đời lớp khác vòng đời đối tượng như thế nào trong Java?

## 1. Hai vòng đời khác nhau, nhưng có liên hệ

Trong Java, **lớp (class)** là định nghĩa kiểu được JVM nạp, liên kết và khởi tạo để chương trình có thể dùng field, method, constructor và tạo instance. **Đối tượng (object/instance)** là một thực thể cụ thể được tạo khi chương trình thực hiện `new` hoặc một cơ chế cấp phát thích hợp. Hai khái niệm có các mốc tồn tại và điều kiện kết thúc khác nhau.

Một lớp có thể đã được khởi tạo dù **chưa có instance nào**; cũng có thể có hàng triệu instance của cùng một lớp mà khối khởi tạo `static` của lớp chỉ chạy **một lần cho mỗi định danh lớp runtime**, nếu lần khởi tạo thành công. Ngược lại, constructor và các instance initializer chạy cho **mỗi lần tạo instance theo quá trình khởi tạo đối tượng thông thường**.

| Tiêu chí | Vòng đời lớp | Vòng đời đối tượng |
|---|---|---|
| Đối tượng quản lý | Định nghĩa kiểu ở runtime, gắn với class loader | Một instance cụ thể của kiểu |
| Điểm khởi đầu thường thấy | JVM nạp kiểu khi cần | Cấp phát đối tượng (thường bằng `new`) |
| Các giai đoạn cốt lõi | Loading → linking → initialization; sau đó có thể unloading | Allocation → giá trị mặc định → instance initialization/constructor → sử dụng → có thể được GC |
| Thực thi mã khởi tạo | `static` initializer/field initializer, một lần cho mỗi lần khởi tạo thành công của định danh lớp | Instance field initializer, instance initializer và constructor theo mỗi lần tạo |
| Trạng thái | Static field thuộc về lớp runtime, chia sẻ giữa các instance tương ứng | Instance field là trạng thái của từng object |
| Kết thúc | Class unloading khi đủ điều kiện và JVM chọn thực hiện | Khi object không còn reachable, JVM **có thể** thu gom |
| Phụ thuộc class loader? | Có: cùng tên nhưng defining loader khác nhau có thể là hai lớp runtime | Instance có kiểu runtime được xác định bởi lớp mà nó thuộc về |

> **Không được đồng nhất** “hết object của lớp” với “lớp được unload”. JVM không bắt buộc unload class ngay khi không còn instance, và garbage collection không chạy theo lịch cố định mà ứng dụng có thể suy ra.

## 2. Vòng đời đối tượng: tạo, dùng và có thể được thu gom

Sau khi kiểu được chuẩn bị/khởi tạo phù hợp, một lời gọi `new` thường dẫn đến:

```text
new Widget(...)
    |
    v
Bảo đảm lớp liên quan đã initialized theo quy tắc JVM
    |
    v
Cấp phát bộ nhớ và gán giá trị mặc định cho instance fields
    |
    v
Chạy chuỗi constructor: superclass -> subclass
    |    (instance field initializers và instance initializer blocks
    |     được thực thi ở từng lớp theo thứ tự cần thiết)
    v
Instance sử dụng được
    |
    v
Không còn strongly reachable từ GC roots
    |
    v
Đủ điều kiện được garbage collector thu gom, tùy JVM/GC
```

Các điểm cốt lõi:

- **Mỗi lần `new` thông thường tạo một object mới** (ngoại trừ JVM có thể tối ưu dựa trên ngữ nghĩa quan sát được; không được dựa vào vị trí cấp phát vật lý).
- **Instance field** thuộc từng object. Gán `a.name` không thay đổi `b.name` của object khác, trừ khi hai tham chiếu đang trỏ cùng object hoặc các field trỏ chung một object mutable khác.
- **Instance initializer** và constructor chạy khi tạo object thông thường; một object không tự chạy constructor lần nữa khi chỉ đọc/ghi field hoặc khi GC hoạt động.
- Constructor **không phải** method `<clinit>`. Trong class file, constructor thể hiện bằng `<init>`; `<clinit>` là phương thức khởi tạo lớp riêng.
- **Reachability** mới quan trọng cho GC: một local variable ra khỏi scope chưa có nghĩa object lập tức bị thu gom; ngược lại gán `null` cho một biến cũng chưa chắc làm object không còn tham chiếu mạnh khác.
- `System.gc()` chỉ là lời đề nghị thu gom, không đảm bảo một object cụ thể sẽ bị thu gom hay finalizer/cleaner chạy ngay.

Một object có thể có nhiều biến cùng giữ tham chiếu:

```java
Widget a = new Widget("first");
Widget b = a;        // hai reference, chỉ một object
a = null;            // b vẫn giữ object reachable
// b = null;          // có thể mất strong reference cuối cùng,
//                     // nhưng thời điểm GC vẫn không xác định
```

Đoạn trên chỉ **minh họa**, cần đặt trong chương trình có định nghĩa `Widget` để biên dịch.

### Có phải mọi object đều đi qua constructor?

Với `new` trong mã nguồn Java thông thường, constructor chain được thực thi. Nhưng không nên khái quát thành “mọi object JVM tạo đều trực tiếp gọi constructor Java”: ví dụ array được tạo bằng `new int[10]`, object tạo bởi một số cơ chế deserialization hoặc runtime nội bộ có ngữ nghĩa khởi tạo khác. Bài học chính là **vòng đời riêng của từng instance** không đồng nhất với initialization một lần của class.

## 3. Java 17 demo: class initialized một lần, object initialized nhiều lần

Tạo file `LifecycleDemo.java`:

```java
public class LifecycleDemo {
    static class Widget {
        static int created;

        static {
            System.out.println("Widget: static init");
        }

        final String name;

        {
            System.out.println("Widget: instance init");
        }

        Widget(String name) {
            this.name = name;
            created++;
            System.out.println("Widget: constructor " + name);
        }

        static void ping() {
            System.out.println("Widget: ping");
        }
    }

    public static void main(String[] args) {
        System.out.println("Start");

        Class<?> type = Widget.class;
        System.out.println("Class literal: " + type.getSimpleName());

        Widget.ping();

        Widget first = new Widget("A");
        Widget second = new Widget("B");

        System.out.println("Same Class: "
                + (first.getClass() == second.getClass()));
        System.out.println("Constructed: " + Widget.created);

        first = null;
        second = null;
        System.out.println("References cleared");
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac LifecycleDemo.java
java LifecycleDemo
```

Stdout:

```text
Start
Class literal: Widget
Widget: static init
Widget: ping
Widget: instance init
Widget: constructor A
Widget: instance init
Widget: constructor B
Same Class: true
Constructed: 2
References cleared
```

Đọc kết quả theo thứ tự:

1. `Widget.class` cho phép lấy đối tượng `Class` và `getSimpleName()` mà **không tự kích hoạt** static initialization. Vì vậy dòng `Class literal: Widget` xuất hiện trước `Widget: static init`.
2. `Widget.ping()` là một lời gọi static method; trước khi chạy method, class được initialization, nên static block in **một lần**.
3. Với mỗi lệnh `new Widget(...)`, instance initializer và constructor chạy **thêm một lần cho object mới**. Static block không chạy lại.
4. `first.getClass() == second.getClass()` in `true` vì cả hai instance có cùng runtime class identity.
5. `Widget.created` là **tổng số object được tạo bởi constructor đã viết**, không phải số object chắc chắn đang còn sống. Sau khi gán hai reference bằng `null`, biến đếm vẫn là `2` vì nó là static state.
6. Dòng `References cleared` không chứng minh GC đã thu gom hai object. GC không được gọi trực tiếp, thời điểm thu gom cũng không thể suy ra từ stdout.

Phân biệt ba thực thể trong ví dụ:

- **Lớp runtime `LifecycleDemo.Widget`**: nơi các quy tắc class loading/linking/initialization áp dụng.
- **`Class<?> type`**: một tham chiếu tới object `Class` phản ánh định nghĩa lớp runtime, không phải instance `Widget` do `new Widget` tạo.
- **`first` và `second`**: hai reference tới hai instance `Widget` riêng biệt.

## 4. Một lớp có thể tồn tại khi không có instance nào?

**Có.** Ví dụ `Math` có các static method, hoặc một utility class người dùng tự định nghĩa có thể hoạt động mà không cần instance của chính class ấy. Chỉ có class literal, reflection metadata hoặc static state cũng không đồng nghĩa với việc đã tạo object thuộc class đó.

Một lớp runtime thường tiếp tục hiện diện khi vẫn còn những thứ giữ nó/class loader của nó reachable. Với JVM hỗ trợ class unloading, điều kiện thu gom/class unloading có liên quan đến việc **defining class loader có thể được GC thu gom**. Ngoài class loader, các reference tới `Class<?>`, instance, method/field reflection objects, thread context class loader hoặc cấu trúc cache có thể kéo dài sự sống của loader và các class do loader định nghĩa.

Không nên hiểu “object hết vòng đời thì class hết vòng đời”:

```text
t0: Load + initialize class A
t1: Tạo object a1
t2: Tạo object a2
t3: a1 không còn reachable, có thể GC
t4: a2 không còn reachable, có thể GC
t5: Class A vẫn còn trong runtime vì loader và metadata liên quan
t6: Nếu loader thật sự có thể reclaim và JVM quyết định unload,
    các class do loader định nghĩa có thể được unload
```

Các mốc t3/t4/t6 là **khả năng**, không phải cam kết về thời điểm. Class do bootstrap class loader định nghĩa thường gắn với toàn bộ thời gian sống của JVM.

### Class unloading không giống garbage collection của instance

Garbage collector có thể thu hồi vùng nhớ của từng object khi không còn reachable, **mà không cần unload lớp của chúng**. Ngược lại, dỡ lớp khỏi JVM là thao tác ở cấp metadata/runtime class và class loader. Trong JVM chạy lâu, ví dụ server liên tục hot-reload plugin, classloader leak có thể khiến metadata cũ vẫn tồn tại dù logic tưởng rằng plugin đã được tháo.

Các nguồn tham chiếu kéo dài class loader thường gặp là thread còn chạy với thread context class loader cũ, `ThreadLocal` chưa dọn, static cache từ code sống lâu giữ `Class<?>` hoặc instance của plugin, hoặc callback/listener chưa hủy đăng ký. Không thể khắc phục chỉ bằng cách `someObject = null` nếu đường tham chiếu khác vẫn tồn tại.

Lưu ý thêm: `Class<?>` **bản thân cũng là một Java object**, nhưng nó đại diện cho một runtime type; việc thu gom object `Class` và unload kiểu được kiểm soát bởi quan hệ reachability với class loader/runtime metadata. Đừng coi mọi `Class<?>` như một instance do constructor của kiểu được đại diện tạo ra.

## 5. Kế thừa: thứ tự static initialization và constructor chain

Một câu hỏi phổ biến là “Nếu `new Child()`, JVM chạy static block, constructor của ai trước?”.

Quy tắc ý nghĩa:

- Đảm bảo khởi tạo các superclass cần thiết trước khi khởi tạo class con; static initializers thuộc từng class chạy một lần khi class đó được khởi tạo.
- Với **mỗi** instance `Child` mới, constructor của superclass được gọi trong constructor chain trước phần thân constructor subclass, theo cơ chế `super(...)`/`this(...)` và quy tắc instance initializers.
- Nếu gọi `new Child()` nhiều lần, constructor chain chạy nhiều lần; static initializers không chạy lại cho cùng các class đã initialized thành công.

Chương trình chạy được, tạo file `InheritanceLife.java`:

```java
public class InheritanceLife {
    static class Parent {
        static {
            System.out.println("Parent static");
        }

        {
            System.out.println("Parent instance");
        }

        Parent() {
            System.out.println("Parent constructor");
        }
    }

    static class Child extends Parent {
        static {
            System.out.println("Child static");
        }

        {
            System.out.println("Child instance");
        }

        Child() {
            System.out.println("Child constructor");
        }
    }

    public static void main(String[] args) {
        new Child();
        System.out.println("---");
        new Child();
    }
}
```

Chạy:

```bash
javac InheritanceLife.java
java InheritanceLife
```

Stdout:

```text
Parent static
Child static
Parent instance
Parent constructor
Child instance
Child constructor
---
Parent instance
Parent constructor
Child instance
Child constructor
```

Lần `new Child()` đầu tiên khiến cả class cha và con được khởi tạo theo quy tắc JVM; hai constructor chain sau đó diễn ra theo từng instance. Lần thứ hai chỉ còn các phần khởi tạo instance/constructor.

## 6. Ứng dụng trong thiết kế và chẩn đoán

- **Thiết kế static state:** nhớ rằng static cache/counter sống gắn với class identity, thường lâu hơn object sử dụng nó; cân nhắc tránh giữ reference không cần thiết đến class loader/module đã ngừng.
- **Reflection/framework:** phân biệt lấy metadata kiểu với thực sự initialization. Khởi tạo class chứa tác dụng phụ (kết nối, đăng ký callback, đọc cấu hình) khi plugin scanning có thể gây hành vi sớm hơn dự kiến; chọn API có cờ `initialize` khi cần kiểm soát.
- **Hiệu năng startup:** việc load, link và initialize class gây chi phí khác nhau; đo đúng mốc cần tối ưu, không giả định mọi class load đã chạy mọi static initializer.
- **Quản lý tài nguyên:** constructor có thể mở file/socket, nhưng GC không phải cơ chế đóng chắc chắn; dùng `AutoCloseable` và `try-with-resources` khi tài nguyên cần giải phóng sớm.
- **Hot redeploy và plugin:** kiểm tra thread, executor, listener, `ThreadLocal`, các cache ở phía host và reference phản chiếu giữ class/loader cũ trước khi kết luận JVM “không unload được class”.
- **Đọc lỗi:** `ClassNotFoundException` thường gắn với yêu cầu tìm class không thành công; `NoClassDefFoundError` còn có thể liên quan lỗi linking hoặc initialization đã xảy ra trước. Đừng suy từ tên exception rằng JVM “chưa bao giờ load” class.

Với HotSpot, có thể quan sát hoạt động nạp/dỡ lớp bằng Unified Logging (`-Xlog:class+load=info,class+unload=info`), hoặc dùng `jcmd <pid> VM.classloader_stats` để khảo sát thống kê loader trên JVM có hỗ trợ lệnh này. Kết quả phụ thuộc phiên bản, trạng thái tiến trình và quyền attach; những công cụ này hỗ trợ điều tra, không biến thời điểm unload thành một lời hứa.

## 7. Bài tập tự kiểm tra

**Câu 1.** Nếu gọi `A.class` rồi `new A()` hai lần, static block của `A` chạy mấy lần, còn constructor?

**Đáp án:** Nếu trước đó `A` chưa initialized và khởi tạo thành công, static block chạy một lần (khi active use làm class được initialized); constructor thông thường chạy hai lần. Riêng `A.class` không yêu cầu initialization.

**Câu 2.** Hai object `a` và `b` đều thuộc class `Foo`, sau khi cả hai đủ điều kiện GC, class `Foo` có chắc chắn bị unload?

**Đáp án:** Không. Class/loader có thể còn reachable từ nhiều nơi; JVM cũng không hứa unload tại thời điểm xác định.

**Câu 3.** Hai plugin có class cùng binary name `org.example.Task` do hai defining loaders khác nhau định nghĩa. `Task.counter` là static field. Chúng có dùng chung `counter` không?

**Đáp án:** Không. Hai runtime classes khác nhau nên có static state riêng, dù tên kiểu trong source giống nhau.

**Câu 4.** `Class.forName("com.example.Service", false, loader)` và `Class.forName("com.example.Service")` giống nhau ở điểm nào, khác nhau ở điểm nào?

**Đáp án:** Cả hai đều nhằm tìm lớp mang tên nhị phân được chỉ định và trả về `Class<?>` nếu thành công. Overload ba tham số với `false` không yêu cầu initialization; overload một tham số yêu cầu initialization và dùng loader của caller theo API. Việc hai lệnh thực sự tham chiếu cùng runtime class còn phụ thuộc loader.

**Câu 5.** `static final int CODE = 7` và `static final Integer CODE = 7` có chắc chắn kích hoạt initialization giống nhau khi đọc field từ class khác?

**Đáp án:** Không. Field `int` dạng compile-time constant có thể được inline mà không kích hoạt initialization; `Integer` không phải constant variable kiểu primitive hoặc `String` theo JLS, nên static field access thông thường có thể kích hoạt initialization class khai báo.

**Câu 6.** Tại sao `Widget.created` vẫn là `2` trong ví dụ `LifecycleDemo` sau khi `first` và `second` được gán `null`?

**Đáp án:** Đây là static counter số lần constructor đã tăng, không phải bộ đếm object đang sống. GC không tự trừ field đó, kể cả khi hai object thực sự được thu gom.

## 8. Ghi nhớ và tài liệu tham khảo

**Nhớ theo hai trục độc lập:**

- **Class:** load/link/initialize theo quy tắc JVM; initializing static state và `<clinit>`; danh tính gắn với defining class loader; có thể unload khi loader đủ điều kiện.
- **Object:** cấp phát, instance initialization và constructor; tiếp tục sống theo reachability; đủ điều kiện GC khi không còn reachable, nhưng thời điểm thu gom không bảo đảm.
- **Mối quan hệ:** một runtime class có thể tạo nhiều object; class initialized thành công không cần làm lại mỗi khi có object mới; việc instance bị thu gom không tự động kết thúc vòng đời class.

Tài liệu chuẩn để tra cứu chính xác quy tắc:

- [Java Language Specification 17, Chapter 12 — Execution](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html): loading, linking, initialization, tạo instance và kết thúc tồn tại class.
- [Java Virtual Machine Specification 17, Chapter 5 — Loading, Linking, and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): ngữ nghĩa loader, class identity, preparation, resolution, initialization và unloading.
- [Java SE 17 — Class.forName](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): overload liên quan cờ `initialize` và class loader.
- [Java SE 17 — System.gc()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html): lời đề nghị thu gom, không bảo đảm diễn ra ngay.

</details>

- [Quay lại đầu trang](#back-to-top)
