<a id="back-to-top"></a>

# Mô hình tổng quan

## Menu
- [Class loader là gì và vì sao JVM cần nạp lớp động](#overview-001)
- [Từ mã nguồn, bytecode đến lớp đang tồn tại trong JVM](#overview-002)
- [Phân biệt ClassLoader, Class và đối tượng của lớp](#overview-003)
- [Phân biệt nạp lớp, liên kết và khởi tạo](#overview-004)
- [Không gian tên, khả năng nhìn thấy và cô lập phụ thuộc](#overview-005)
- [Một ví dụ xuyên suốt: ứng dụng chủ và plugin](#overview-006)
- [Kiến thức tiên quyết và trình tự học đề xuất](#overview-007)
- [Quy tắc đặc tả, chi tiết HotSpot và khác biệt theo phiên bản JDK](#overview-008)

## <a id="overview-001">Class loader là gì và vì sao JVM cần nạp lớp động</a>

<details>
<summary>Xem chi tiết</summary>

# Class loader là gì và vì sao JVM cần nạp lớp động?

Trong Java, mã nguồn được biên dịch thành bytecode, nhưng bytecode không tự trở thành mã đang chạy. **Class loader (bộ nạp lớp)** là thành phần tham gia tìm và đưa định nghĩa lớp từ bytecode vào JVM, để JVM có thể liên kết, khởi tạo và sử dụng lớp đó khi cần. Cơ chế này giúp một chương trình sử dụng thư viện, driver hoặc thành phần mở rộng tại thời điểm chạy mà không phải nạp trước mọi lớp có thể tồn tại.

## 1. Hiểu đúng khái niệm

Một lớp Java thường đi qua các hình thức sau:

~~~text
Mã nguồn .java
    │ javac
    ▼
Bytecode .class (có thể nằm trong JAR, module, thư mục hoặc nguồn sinh động)
    │ class loader tìm/đọc/định nghĩa lớp
    ▼
Class<?> trong JVM
    │ liên kết, rồi khởi tạo tại thời điểm thích hợp
    ▼
Đối tượng, phương thức, trường tĩnh và hành vi chương trình
~~~

- **Lớp trên đĩa** là một biểu diễn bytecode; một tệp JAR có thể chứa hàng trăm hoặc hàng nghìn tệp .class.
- **Lớp đã được nạp** là một định nghĩa được JVM nhận biết, biểu diễn trong Java bằng một đối tượng Class<?>. Nạp một lớp không đồng nghĩa tạo một instance của lớp ấy.
- **Class loader** là cơ chế tìm kiếm và định nghĩa lớp. Với các class loader dựa trên java.lang.ClassLoader, việc nạp có thể bắt đầu qua loadClass(...), hoặc gián tiếp khi JVM cần một loại chưa có.
- **Nạp động** nghĩa là JVM có thể phát hiện/nạp định nghĩa lớp trong quá trình thực thi. Điều đó không có nghĩa Java luôn biên dịch mã nguồn .java vào lúc chạy, cũng không có nghĩa mọi lớp đều được nạp theo cách lười biếng tuyệt đối.

Ví dụ: ứng dụng biết trước interface của một driver. Khi chạy, nó mới tìm được thư viện chứa lớp triển khai thực tế, nhờ cấu hình và cơ chế nạp lớp. Mã gọi sử dụng interface chung, không cần tự viết lại mỗi khi có nhà cung cấp mới.

## 2. Vì sao JVM cần cơ chế nạp lớp động?

**Thứ nhất, chương trình Java có hệ sinh thái thư viện thay đổi theo triển khai.** Cùng một ứng dụng có thể chạy với tập JAR/module khác nhau. JVM phải có cách tìm bytecode tương ứng với tên lớp tại môi trường chạy hiện tại.

**Thứ hai, ứng dụng có thể phát hiện thành phần lúc chạy.** Driver cơ sở dữ liệu, plugin, bộ mã hóa, bộ ghi log hoặc nhà cung cấp dịch vụ có thể được chọn qua cấu hình hay Service Provider Interface (SPI), thay vì cố định một lớp triển khai trong mã nguồn.

**Thứ ba, không phải mọi lớp đều cần được xử lý ngay từ đầu.** Một chương trình chỉ dùng một nhánh chức năng có thể không cần nạp các lớp ở nhánh khác. JVM được quyền chủ động nạp hoặc liên kết sớm hơn khi phù hợp, nhưng phải bảo đảm các quy tắc quan sát được về khởi tạo lớp.

**Thứ tư, các môi trường chạy cần tổ chức không gian tên.** Application server, hệ plugin hoặc công cụ triển khai có thể dùng nhiều class loader để phân tách các tập lớp, chẳng hạn phục vụ các ứng dụng hoặc phiên bản thư viện khác nhau. Phân tách này cần thiết nhưng không tự bảo đảm an ninh.

**Thứ năm, bytecode không nhất thiết có sẵn thành tệp .class trên đĩa.** Một framework hoặc thư viện có thể tạo bytecode trong bộ nhớ rồi nhờ class loader định nghĩa lớp. Khả năng đó hỗ trợ proxy, mã được tạo và các cơ chế mở rộng khác.

Nói cách khác, class loading tách **thời điểm biên dịch**, **nguồn chứa định nghĩa lớp**, **thời điểm JVM cần lớp** và **class loader sở hữu định nghĩa** thành các mối quan tâm riêng.

## 3. JVM làm gì khi cần một lớp?

Khi chương trình tham chiếu tới một kiểu, JVM phải bảo đảm có định nghĩa lớp phù hợp; sau đó kiểu có thể được liên kết và khởi tạo theo quy tắc Java. Ba mốc này khác nhau:

| Mốc | Câu hỏi cần trả lời |
| --- | --- |
| **Loading** | Bộ nạp nào tìm và định nghĩa lớp từ bytecode? |
| **Linking** | Bytecode đã được kiểm tra (*verification*), trường tĩnh được chuẩn bị (*preparation*) và những tham chiếu cần thiết được phân giải (*resolution*) ra sao? |
| **Initialization** | Khi nào JVM thực thi logic khởi tạo `static` của lớp? |

JVM có thể phân giải một số tham chiếu muộn; việc nạp lớp không tự chạy `static {}` hoặc tạo instance. Chẳng hạn, `Class.forName(name, false, loader)` yêu cầu tìm lớp mà không yêu cầu khởi tạo; gọi phương thức `static` của lớp chưa khởi tạo thường làm phát sinh bước initialization.

Để quan sát cụ thể, xem thí nghiệm ở mục 6. Khi phân tích edge case về `ConstantValue`, thứ tự khởi tạo superclass hoặc lỗi liên kết, cần xét chi tiết từng giai đoạn thay vì suy đoán từ một dòng log class loading.

## 4. Ai nạp lớp? Mô hình class loader của Java hiện đại

Trên JDK phổ biến từ Java 9 trở lên, ba nhóm bộ nạp thường gặp là:

| Bộ nạp | Phạm vi điển hình | Dấu hiệu nhận biết |
| --- | --- | --- |
| **Bootstrap loader** | Các lớp nền tảng thiết yếu, chẳng hạn java.lang.Object. | Java API thường biểu diễn loader này bằng null khi gọi Class.getClassLoader(). |
| **Platform class loader** | Nhiều module nền tảng của JDK. | Truy cập bằng ClassLoader.getPlatformClassLoader(). |
| **Application/System class loader** | Lớp ứng dụng và thư viện trên classpath/module path mà bộ nạp này phụ trách. | Truy cập bằng ClassLoader.getSystemClassLoader() trong ứng dụng thông thường. |

Mô hình ủy quyền mặc định thường được vẽ:

~~~text
Yêu cầu nạp một tên lớp
        │
        ▼
Application loader
        │ hỏi parent trước
        ▼
Platform loader
        │ hỏi parent trước
        ▼
Bootstrap loader
        │
        ├── tìm thấy → dùng định nghĩa lớp đã có/được nạp
        └── không thấy → trả quyền thử lại cho các loader bên dưới
~~~

Đây là **parent delegation**: class loader con kiểm tra lớp đã nạp, thường nhờ cha thử nạp trước, rồi mới tự tìm/định nghĩa nếu cha không tìm được. Nhờ đó, mã ứng dụng thông thường không thể thay thế java.lang.String bằng một lớp tùy ý có cùng tên trên classpath. Không phải mọi custom loader đều giữ chính sách parent-first: có thiết kế child-first có kiểm soát cho plugin hoặc container.

Trong mô hình module Java từ Java 9, cách tìm lớp còn chịu ảnh hưởng của module graph, khả năng đọc module, exports/opens và cấu hình loader. Sơ đồ trên là mô hình tư duy căn bản, không phải danh sách đầy đủ mọi luật tìm lớp của mọi JVM.

**Lưu ý quan trọng:** Parent của một ClassLoader có thể là null khi cha là bootstrap; điều đó không nói rằng lớp không có class loader theo nghĩa vận hành. Đừng giả định tên triển khai nội bộ của loader luôn giống nhau giữa các bản JDK.

## 5. Định danh lớp phụ thuộc cả tên lẫn class loader

Một tên nhị phân như `com.example.Plugin` **chưa đủ** để kết luận hai đối tượng thuộc cùng kiểu: identity của lớp tham chiếu trong JVM còn phụ thuộc **defining loader**. Nếu loader A và B tự định nghĩa hai bản `com.example.Plugin`, JVM coi đó là hai kiểu khác nhau; cast trực tiếp có thể thất bại.

Ngược lại, hai loader cùng ủy quyền cho một loader cha có thể nhận lại **cùng** `Class<?>`. Loader gọi `loadClass` (*initiating loader*) vì thế không nhất thiết là loader đã định nghĩa lớp mà `Class.getClassLoader()` trả về. Trong hệ plugin, host và plugin nên chia sẻ một định nghĩa của API/SPI, còn các implementation có thể do loader riêng quản lý.

Đây là nền tảng để hiểu class identity và khả năng trao đổi dữ liệu giữa các plugin; ví dụ ở phần tiếp theo tập trung vào **nạp so với khởi tạo**, không giả lập hai loader độc lập.

## 6. Ví dụ Java 17+: nạp lớp chưa đồng nghĩa khởi tạo

Tạo tệp **ClassLoaderOverview.java** với toàn bộ nội dung sau:

~~~java
public class ClassLoaderOverview {
    static class Greeter {
        static {
            System.out.println("[init] Greeter");
        }

        static void greet() {
            System.out.println("Hello from Greeter");
        }
    }

    public static void main(String[] args) throws ClassNotFoundException {
        ClassLoader loader = ClassLoaderOverview.class.getClassLoader();

        Class<?> type = Class.forName(
                "ClassLoaderOverview$Greeter",
                false,
                loader
        );

        System.out.println("Discovered: " + type.getName());
        System.out.println("Before method call");

        Greeter.greet();

        System.out.println("Same class: " + (type == Greeter.class));
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~bash
javac ClassLoaderOverview.java
java ClassLoaderOverview
~~~

Đầu ra:

~~~text
Discovered: ClassLoaderOverview$Greeter
Before method call
[init] Greeter
Hello from Greeter
Same class: true
~~~

**Giải thích theo thứ tự:**

1. Tên nhị phân của lớp lồng Greeter là ClassLoaderOverview$Greeter. Chuỗi được đưa vào Class.forName(..., false, loader), nên JVM thu được đối tượng Class mà chưa phải chạy bộ khởi tạo static của Greeter.
2. Lệnh in Discovered và Before method call chạy trước, chứng minh việc lấy Class<?> không tự kích hoạt đoạn in [init].
3. Greeter.greet() là một lần gọi phương thức static, nên JVM phải khởi tạo Greeter trước khi thực thi thân phương thức. Bộ khởi tạo chạy đúng một lần đối với định nghĩa lớp đó.
4. Biểu thức type == Greeter.class cho true vì cả hai biểu thức tham chiếu tới **cùng định nghĩa lớp**, do cùng loader phù hợp nạp. Kết luận này không mở rộng cho hai bản sao cùng tên do hai defining loader khác nhau định nghĩa.

Nếu đổi thành **Class.forName("ClassLoaderOverview$Greeter")**, lời gọi một tham số thường yêu cầu khởi tạo ngay; dòng [init] có thể xuất hiện trước dòng Discovered. Nếu dùng **loader.loadClass("ClassLoaderOverview$Greeter")**, hành vi loadClass mặc định chỉ yêu cầu nạp, không yêu cầu khởi tạo; class loader tùy biến vẫn phải tuân thủ hợp đồng JVM liên quan.

Không dùng một log duy nhất để suy đoán chính xác thời điểm JVM đọc từng byte .class: JVM có quyền tải, kiểm tra hoặc resolve ở thời điểm khác miễn bảo đảm ngữ nghĩa chương trình.

## 7. Nạp động giúp ứng dụng mở rộng như thế nào?

Một trường hợp thực tế là **SPI với ServiceLoader**:

1. Một thư viện chung khai báo interface, ví dụ Encoder.
2. Một hoặc nhiều thư viện nhà cung cấp có lớp triển khai Encoder, kèm metadata đăng ký provider (ví dụ META-INF/services với classpath, hoặc provides ... with ... trong module).
3. Ứng dụng gọi ServiceLoader.load(Encoder.class), rồi sử dụng các provider được phát hiện. Đây là cơ chế tích hợp được Java cung cấp; API chung và provider phải có khả năng truy cập/hiển thị phù hợp.

Đoạn dưới **chỉ là phác họa ý tưởng**, chưa phải chương trình độc lập vì interface Encoder và metadata provider không được khai báo tại đây:

~~~java
ServiceLoader<Encoder> providers = ServiceLoader.load(Encoder.class);
for (Encoder encoder : providers) {
    encoder.encode();
}
~~~

Ứng dụng có thể thêm một provider hợp lệ khi đóng gói/triển khai mà không sửa vòng lặp. Tuy nhiên, class loading **không tự quét toàn bộ JAR để tìm mọi class**; ServiceLoader tuân theo metadata và quy tắc khám phá của nó. Với tình huống thư viện gọi SPI nhưng provider thuộc loader khác, **thread context class loader** (Thread.currentThread().getContextClassLoader()) có thể giúp thư viện truy cập lớp của ứng dụng. Đây là một cơ chế phối hợp có chủ đích, không đồng nghĩa mọi thư viện nên thay đổi context loader tùy ý.

Ở các hệ plugin nâng cao, ứng dụng còn có thể tạo custom ClassLoader, định nghĩa lớp từ các byte bytecode hợp lệ, và quản lý vòng đời của loader. Các bước đó đi xa hơn câu hỏi tổng quan này; trước khi tự viết loader, nên cân nhắc ServiceLoader, module và cơ chế plugin hiện có.

## 8. Những lỗi dễ nhầm khi gặp class loading

Khi gặp lỗi, trước tiên phân biệt **không tìm được tên lớp**, **không thể sử dụng định nghĩa lớp**, và **hai định nghĩa cùng tên nhưng khác kiểu**:

- `ClassNotFoundException`: API như `loadClass` hoặc `Class.forName` được yêu cầu tìm tên lớp nhưng không tìm/nạp được. Kiểm tra tên nhị phân, loader và JAR/module có thể nhìn thấy.
- `NoClassDefFoundError`: JVM không thể có hoặc sử dụng một lớp đang cần, đôi khi là hậu quả của lần khởi tạo thất bại trước đó. Xem nguyên nhân gốc, không tự động kết luận thiếu JAR.
- `ClassCastException` với tên giống nhau: kiểm tra **hai đối tượng `Class<?>` và defining loader**, thay vì chỉ so sánh `getName()`.
- `LinkageError` hoặc `ExceptionInInitializerError`: lần lượt điều tra sự không tương thích khi định nghĩa/liên kết, hoặc exception từ logic khởi tạo tĩnh.

Có thể quan sát loader bằng `SomeType.class.getClassLoader()` và sự kiện nạp trên HotSpot hiện đại bằng `-Xlog:class+load=info`. Đây là dấu hiệu để điều tra; log loading không phải bằng chứng lớp đã initialized.

## 9. Giới hạn và lưu ý thiết kế

- **Nạp được lớp không có nghĩa được phép truy cập mọi thành viên.** Quy tắc Java access control, module exports/opens và kiểm tra khi liên kết/phản chiếu vẫn áp dụng.
- **Class loader không phải sandbox tin cậy để chạy mã độc.** Verification giúp bảo đảm tính hợp lệ của bytecode, nhưng không có nghĩa mã đã được nạp an toàn về quyền truy cập tài nguyên. Mã không tin cậy cần cơ chế cô lập và chính sách bảo mật phù hợp.
- **Thấy tên lớp trong JAR không bảo đảm lớp nạp thành công.** Có thể thiếu dependency, sai phiên bản bytecode so với JVM, mâu thuẫn module hoặc loader không nhìn thấy JAR.
- **Dỡ lớp không đơn giản là xóa tệp JAR hoặc đặt một biến bằng null.** JVM có thể thu hồi metadata lớp khi điều kiện unload đáp ứng, thường liên quan tới việc defining loader và các lớp của nó không còn được giữ sống; thời điểm thu hồi do JVM quyết định. Thread, cache, listener hoặc context class loader giữ tham chiếu có thể ngăn loader được thu gom.
- **Không dùng Class.forName tùy tiện với tên lấy từ input không tin cậy.** Việc nạp/khởi tạo có thể kích hoạt code không mong đợi. Nên giới hạn tên lớp/provider hợp lệ và dùng API công khai của thư viện khi có thể.

## 10. Tự kiểm tra hiểu bài

**Câu 1.** Chương trình có 200 lớp trong các JAR. Khi khởi động, JVM có bắt buộc khởi tạo cả 200 lớp không?

**Trả lời:** Không. Các lớp được khởi tạo theo quy tắc sử dụng chủ động và các quy tắc liên quan; JVM có quyền nạp/liên kết ở thời điểm thích hợp, nhưng việc JAR có lớp không tự buộc khởi tạo tất cả.

**Câu 2.** Hai class loader định nghĩa cùng một binary name com.example.Task. Có thể ép một object của Task bên loader A thành Task bên loader B không?

**Trả lời:** Không, nếu chúng thực sự là hai định nghĩa khác nhau. Trong JVM, identity của kiểu gắn với tên nhị phân **và defining loader**; cần chia sẻ một hợp đồng kiểu được nạp chung khi các module trao đổi object.

**Câu 3.** Vì sao Class.forName(name, false, loader) có thể trả Class<?> nhưng khối static vẫn chưa chạy?

**Trả lời:** Vì loading/linking và initialization là các giai đoạn khác nhau. Tham số false chỉ ra rằng lời gọi đó không yêu cầu initialization; lần sử dụng chủ động về sau mới buộc khởi tạo, nếu chưa khởi tạo.

## Ghi nhớ

**Class loader giúp JVM nhận biết các định nghĩa lớp tại thời điểm chạy.** Nhờ nạp lớp động, Java có thể dùng thư viện và provider được quyết định khi triển khai, triển khai plugin và phân tách một phần không gian kiểu. Để hiểu chính xác hành vi, luôn tách ba ý: **nạp lớp ≠ khởi tạo lớp**, **tên lớp ≠ định danh kiểu đầy đủ**, và **khả năng tìm thấy lớp ≠ khả năng dùng lớp an toàn/hợp lệ**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-002">Từ mã nguồn, bytecode đến lớp đang tồn tại trong JVM</a>

<details>
<summary>Xem chi tiết</summary>

# Từ mã nguồn, bytecode đến lớp đang tồn tại trong JVM

Một lớp Java không đi thẳng từ tệp `.java` thành đối tượng trong bộ nhớ. Trước tiên, mã nguồn được biên dịch thành định dạng `.class`; khi chương trình chạy, JVM sử dụng class loader để tìm và định nghĩa lớp, thực hiện các bước liên kết, rồi khởi tạo lớp khi điều kiện của Java yêu cầu. Sau đó, các đối tượng mới có thể được tạo và bytecode của các phương thức được thực thi.

Bài này tập trung vào **chuỗi chuyển đổi từ mã nguồn → class file → lớp do JVM quản lý** và vai trò cụ thể của class loader. Phân biệt được từng bước sẽ giúp giải thích vì sao lớp có trên đĩa nhưng JVM chưa nạp, vì sao hai lớp cùng tên không nhất thiết là một kiểu, và vì sao một lỗi có thể xuất hiện khi chạy dù biên dịch đã thành công.

## 1. Bức tranh tổng thể

```text
Mã nguồn: src/demo/Target.java
          |
          | javac --release 17
          v
Class file: out/demo/Target.class
  - tên nhị phân, constant pool, field/method, bytecode, metadata
          |
          | JVM cần tham chiếu đến lớp
          v
Class loader: tìm dữ liệu lớp + defineClass (hoặc cơ chế JVM tương ứng)
          |
          v
Loading → Linking [Verification → Preparation → Resolution*]
          |
          | sử dụng chủ động làm phát sinh yêu cầu khởi tạo
          v
Initialization: thực hiện <clinit> một lần cho mỗi lớp được định nghĩa
          |
          v
Class<?> / metadata lớp trong JVM
          |
          +--> new / reflection có thể tạo instance (đối tượng)
          +--> gọi phương thức: interpreter / JIT thực thi mã
```

Dấu `*`: **resolution** có thể được JVM thực hiện theo nhu cầu, không bắt buộc hoàn tất hết mọi tham chiếu trước khi initialization. Một biểu đồ tuyến tính mô tả quan hệ logic, không có nghĩa tất cả các bước chỉ xảy ra một lần tại một thời điểm cố định.

| Thành phần | Ở đâu/vai trò | Phân biệt |
| --- | --- | --- |
| `Target.java` | Tệp văn bản mã nguồn | JVM thông thường không chạy trực tiếp nội dung này như bytecode đã được biên dịch; lệnh `java Target.java` (source-file mode) có bước biên dịch do launcher thực hiện. |
| `Target.class` | Tệp hoặc tài nguyên chứa class-file bytes | Có thể nằm trong thư mục, JAR, module image hoặc được tạo trong bộ nhớ; không nhất thiết tồn tại như tệp riêng. |
| Bytecode | Chỉ thị trong `Code` attribute của phương thức cụ thể | Là đầu vào cho cơ chế thực thi của JVM, không phải mã máy CPU và không bao gồm toàn bộ nội dung class file. |
| Class loader | Thành phần xác định lớp từ dữ liệu nhị phân | Tham gia quyết định **định danh kiểu** cùng với tên nhị phân của lớp. |
| `java.lang.Class<?>` | Đối tượng phản chiếu đại diện cho lớp/kiểu ở thời gian chạy | Không phải bản sao tệp `.class` hay instance của chính lớp nghiệp vụ. |
| Instance | Đối tượng được tạo từ lớp, thường bằng `new` | Một lớp có thể đã được nạp mà chưa có instance nào. |

Lưu ý hai mốc độc lập: **`javac` thành công** chỉ chứng minh mã nguồn qua được các kiểm tra biên dịch; **JVM nạp/liên kết/khởi tạo thành công** còn phụ thuộc bytecode thực tế, phiên bản runtime, thư viện, loader và logic khởi tạo.

## 2. Từ `.java` sang `.class`: trình biên dịch làm gì?

`javac` đọc mã nguồn, phân tích token/cú pháp, kiểm tra kiểu, giải quyết tên dựa trên classpath/module path của **thời điểm biên dịch**, rồi sinh class file. Các lambda, generic, `switch`, `try`... được ánh xạ sang cấu trúc phù hợp trong class file. Generic thông thường dùng **type erasure**; các kiểu tham số hóa không tự động tạo một class file riêng cho mỗi `List<String>` và `List<Integer>`.

Một class file bắt đầu bằng magic number `0xCAFEBABE` và mang phiên bản định dạng (Java 17 tương ứng **major version 61**, Java 21 tương ứng 65). Trong đó có constant pool, thông tin tên lớp/cha/interface, modifiers, fields, methods, attributes và phần bytecode của các phương thức có mã. Constant pool chứa các hằng và các **symbolic references** như tên lớp, tên phương thức và descriptor; không có nghĩa mọi lớp được nhắc tới đều đã được nạp ngay khi file được đọc.

Tên nhị phân (binary name) của `demo.Target` là `demo.Target`; đường dẫn điển hình theo classpath là `demo/Target.class`. Đường dẫn tệp chỉ là một cách lưu trữ; cùng dữ liệu có thể nằm trong JAR hoặc đến từ loader tự viết.

### Bài thực hành A — quan sát biên dịch, nạp và khởi tạo

Tạo hai tệp chính xác theo cây thư mục:

```text
project/
└── src/
    └── demo/
        ├── Target.java
        └── App.java
```

**`src/demo/Target.java`**

```java
package demo;

public class Target {
    static {
        System.out.println("06 - Target initialized");
    }

    public static void hello() {
        System.out.println("07 - Target.hello()");
    }
}
```

**`src/demo/App.java`**

```java
package demo;

public class App {
    public static void main(String[] args) throws Exception {
        System.out.println("01 - main started");

        Class<?> byLiteral = Target.class;
        System.out.println("02 - " + byLiteral.getName());

        ClassLoader loader = App.class.getClassLoader();
        Class<?> byLoadClass = loader.loadClass("demo.Target");
        System.out.println("03 - same Class: " + (byLiteral == byLoadClass));

        Class<?> withoutInit = Class.forName("demo.Target", false, loader);
        System.out.println("04 - same Class: " + (byLiteral == withoutInit));

        System.out.println("05 - before active use");
        Target.hello();
        System.out.println("08 - main ended");
    }
}
```

Trong thư mục `project`, chạy bằng JDK 17 trở lên:

```sh
javac --release 17 -d out src/demo/Target.java src/demo/App.java
java -cp out demo.App
```

**Kết quả chuẩn:**

```text
01 - main started
02 - demo.Target
03 - same Class: true
04 - same Class: true
05 - before active use
06 - Target initialized
07 - Target.hello()
08 - main ended
```

Giải thích từng quan sát:

1. `Target.class` trả về đối tượng `Class` cho `demo.Target` nhưng **không bắt buộc khởi tạo** `Target`. VM có quyền nạp/liên kết trước thời điểm chương trình quan sát được.
2. `loader.loadClass("demo.Target")` tìm/trả về lớp qua loader, **thông thường không khởi tạo** nó. Do cả hai tham chiếu đến cùng lớp do cùng loader định nghĩa, phép so sánh `==` là `true`.
3. `Class.forName("demo.Target", false, loader)` yêu cầu tìm lớp nhưng **`initialize=false`**, nên cũng chưa chạy khối `static`.
4. Gọi phương thức static `Target.hello()` là **active use** làm JVM khởi tạo `Target` trước khi thân phương thức chạy: in `06` rồi `07`. Khởi tạo lớp chỉ diễn ra một lần đối với lớp đó trong loader đó.
5. `App` đã phải được JVM khởi tạo để chạy `main` của nó. Các thao tác với `Target` trong ví dụ không cho phép suy luận rằng mọi lớp được tham chiếu trong chương trình đều khởi tạo cùng lúc.

Đổi thử `Class.forName("demo.Target", false, loader)` thành `Class.forName("demo.Target", true, loader)`, rồi biên dịch/chạy lại trong tiến trình JVM mới: dòng `06` sẽ xuất hiện **trước dòng `04`**. Tham số thứ hai đã kích hoạt việc khởi tạo. Nếu gọi `Class.forName(String)`, mặc định nó yêu cầu khởi tạo và dùng loader của lớp gọi theo hợp đồng của API.

Để nhìn vào bytecode và metadata, chạy:

```sh
javap -classpath out -c -p demo.App
javap -classpath out -verbose demo.Target
```

Trong output `javap -c` của `App`, có thể tìm các lệnh như `ldc` (lấy class literal), `invokevirtual`, `invokestatic`; `-verbose` của `Target` cho biết `major version: 61` vì ta biên dịch với `--release 17`. `javap` **giải mã class file** để người đọc quan sát, không phải công cụ chứng minh một lớp đã được JVM nạp khi ứng dụng chạy.

> **Điểm mấu chốt:** Có `.class` không có nghĩa lớp đã được nạp; được nạp không đồng nghĩa đã khởi tạo; khởi tạo lớp cũng không đồng nghĩa có instance của lớp đó.

## 3. Loading: JVM lấy lớp từ đâu?

Khi JVM cần lớp `demo.Target`, một loader phù hợp được dùng để tìm dữ liệu nhị phân, kiểm tra yêu cầu định nghĩa lớp và tạo biểu diễn runtime của lớp. Với loader Java tự viết, đường đi thường là `loadClass` → ủy quyền cho parent → `findClass` → đọc bytes → `defineClass`. Đây là mẫu **parent delegation** phổ biến, không phải quy tắc rằng mọi loader phải tải tệp từ đĩa hay dùng đúng thuật toán đó.

Các loader cơ bản trong JDK 17+:

- **Bootstrap loader**: nạp các lớp cốt lõi của nền tảng; Java biểu diễn loader này bằng `null` trong `Class.getClassLoader()`, nhưng `null` không có nghĩa lớp không được nạp.
- **Platform loader**: phục vụ nhiều lớp/module của nền tảng; lấy qua `ClassLoader.getPlatformClassLoader()`.
- **Application/System loader**: thường nạp lớp của ứng dụng từ classpath và các module ứng dụng phù hợp; lấy qua `ClassLoader.getSystemClassLoader()`.
- **Custom loader**: có thể nạp plugin, class bytes do hệ thống phát sinh, hoặc classpath cách ly theo yêu cầu.

Phác họa cách ủy quyền thông thường:

```text
Application / Custom loader
         |
         | hỏi parent trước (nếu loader theo parent-first)
         v
     Platform loader
         |
         v
   Bootstrap loader
         |
    nếu parent không tìm thấy, loader con tự tìm
```

**Parent loader** không đồng nghĩa **parent Java class** (từ khóa `extends`); đó là hai quan hệ khác nhau. Một custom loader có thể dùng child-first theo thiết kế và các loader hỗ trợ Java Platform Module System có cơ chế tra cứu phức tạp hơn sơ đồ trên. Không nên giả định rằng mọi lớp đi qua đúng một chuỗi ủy quyền đơn giản.

### Classpath, JAR, module path có tác động thế nào?

Ở ví dụ A, `java -cp out demo.App` đặt `out` lên classpath. Application loader có thể tìm `demo/App.class` và `demo/Target.class` theo tên nhị phân. Với JAR, có thể dùng `java -cp app.jar demo.App` nếu JAR chứa các entry phù hợp. Với named module, `--module-path` và việc resolve module quyết định module nào có thể được nhìn thấy, cùng với các ràng buộc readability/exports; không thể quy giản toàn bộ thành việc thêm đường dẫn vào `-cp`.

Nếu file không tồn tại tại vị trí loader đang tìm, nếu một dependency vắng mặt, hoặc nếu module không đọc/export package cần thiết, mã nguồn biên dịch trước đó vẫn có thể gặp lỗi khi chạy trong môi trường khác.

## 4. Linking: biến bytes thành lớp có thể sử dụng an toàn

Theo Java Virtual Machine Specification, **linking** gồm ba phần:

**Verification — xác minh:** Kiểm tra class file có cấu trúc hợp lệ và các ràng buộc kiểu của bytecode được tôn trọng. Tệp `.class` bị sửa lỗi, bytecode không hợp lệ hoặc sự không nhất quán nhất định có thể gây `VerifyError` hoặc lỗi định dạng class.

**Preparation — chuẩn bị:** JVM cấp phát và đặt **giá trị mặc định** cho các `static` fields của lớp trong cấu trúc quản lý phù hợp. Ví dụ `static int count = 5`: tại preparation, giá trị khởi đầu mặc định là `0`; đoạn gán `5` thông thường được thực hiện lúc initialization. Trường `static final` là **constant variable** có `ConstantValue` attribute có thể được gán giá trị hằng ngay ở preparation, nên không dùng quy tắc `0` cho mọi trường hợp.

**Resolution — phân giải:** JVM thay các tham chiếu tượng trưng trong constant pool bằng các tham chiếu runtime thích hợp khi cần, kiểm tra sự tồn tại và khả năng truy cập của class, field, method liên quan. Resolution có thể xảy ra **theo nhu cầu**; lỗi thiếu dependency hoặc sai chữ ký API có thể xuất hiện muộn khi lệnh liên quan được thực thi.

Trình tự ràng buộc: lớp phải được **verified và prepared trước khi initialization**; resolution có thể được thực hiện linh hoạt. Một số lớp phụ thuộc có thể được nạp trước để phục vụ việc xác minh hoặc phân giải mà chưa nhất thiết đã initialized.

Các khái niệm này không thay thế cho **compile-time linking** của công cụ native. Java class file giữ nhiều symbolic references để JVM giải quyết ở runtime.

## 5. Initialization: khi nào `static` thực sự chạy?

Initialization thực thi phương thức đặc biệt `<clinit>` do compiler/JVM tổng hợp từ các initializer của trường static và khối `static { ... }` theo thứ tự nguồn, nếu lớp cần đoạn mã này. Trước khi khởi tạo lớp con, JVM phải khởi tạo lớp cha thích hợp; với interface, quy tắc khởi tạo liên quan khác với class.

Những thao tác thường làm phát sinh yêu cầu khởi tạo lớp:

- Tạo instance bằng `new Target()`.
- Gọi static method được khai báo bởi lớp, như `Target.hello()`.
- Đọc/ghi static field được khai báo bởi lớp, trừ một số trường hợp **constant variable** được compiler inlining.
- Gọi API reflection có cờ khởi tạo `true`, hoặc một số thao tác reflection thực hiện active use.
- JVM khởi tạo lớp chứa `main` trước khi chạy phương thức `main`.

Những thao tác **không tự chúng đòi hỏi khởi tạo** `Target`: lấy `Target.class`, đọc class name từ `Class`, `ClassLoader.loadClass("demo.Target")`, và `Class.forName("demo.Target", false, loader)`. JVM vẫn có thể chuẩn bị/nạp sớm để tối ưu nhưng không được làm lộ side effect initialization trước thời điểm ngôn ngữ cho phép.

Ví dụ về **hằng biên dịch**:

```java
package demo;

public class Constants {
    public static final int PORT = 8080; // constant variable
    public static final Integer BOXED_PORT = 8080; // không phải constant variable
    static {
        System.out.println("Constants initialized");
    }
}
```

Một class khác đọc `Constants.PORT` có thể nhận giá trị đã inline mà **không kích hoạt `Constants` initialization**. Ngược lại, đọc `Constants.BOXED_PORT` làm phát sinh active use và khởi tạo. Thí nghiệm này cần đặt phần đọc ở lớp khác và chạy một tiến trình JVM mới, vì một lớp chỉ initialized một lần trong phạm vi định nghĩa của nó. Việc thay đổi giá trị constant trong một thư viện mà không biên dịch lại consumer có thể khiến consumer giữ giá trị cũ.

Nếu `<clinit>` ném exception, lần đầu truy cập có thể nhận `ExceptionInInitializerError` (tùy loại lỗi); lớp đó có thể chuyển sang trạng thái khởi tạo thất bại và các lần sử dụng tiếp theo thường gặp `NoClassDefFoundError` với thông tin rằng lớp không thể được initialized. Nạp và liên kết thành công không bảo đảm logic khởi tạo thành công.

## 6. "Một lớp đang tồn tại trong JVM" chính xác là gì?

JVM định nghĩa danh tính runtime của một reference class/interface bằng cặp **(binary name, defining class loader)**. Hai loader khác nhau có thể định nghĩa hai lớp có **cùng tên**, từ những bytes giống hệt nhau, nhưng JVM coi là **hai kiểu khác nhau**. Vì vậy thông báo lỗi cast đôi khi trông kỳ lạ: `demo.Plugin cannot be cast to demo.Plugin`, do hai vế cùng tên nhưng do loader khác nhau định nghĩa.

Có hai vai trò phải tách bạch:

- **Initiating loader**: loader được yêu cầu tải lớp và có thể ủy quyền.
- **Defining loader**: loader thực sự định nghĩa lớp. Ví dụ loader con yêu cầu `java.lang.String` nhưng parent/bootstrap định nghĩa nó; defining loader của `String` là bootstrap.

Lớp runtime có metadata (tên, cha, interfaces, fields, methods, constant pool runtime...) và có đối tượng phản chiếu `Class<?>` tương ứng. **Method area** là vùng bộ nhớ logic được JVM Specification mô tả; **Metaspace** là chi tiết hiện thực của HotSpot cho phần lớn class metadata, còn đối tượng `java.lang.Class` là một Java object. Không đồng nhất mọi metadata với heap của instance, cũng không khẳng định mọi JVM dùng Metaspace.

**Loading ≠ initialization ≠ instantiation**:

- Loading: JVM có định nghĩa runtime của lớp.
- Initialization: static initialization của lớp hoàn thành (hoặc thất bại), theo quy tắc riêng.
- Instantiation: tạo **một** object cụ thể của lớp; có thể tạo nhiều object, hoặc không object nào.

Lớp có thể trở nên **có thể dỡ tải (unload)** khi defining loader và các class/object liên quan không còn được tham chiếu đủ để giữ sống. Việc dỡ tải do GC/runtime quyết định, không xảy ra bảo đảm ngay khi gọi `URLClassLoader.close()` hoặc gán biến `null`. JDK có thêm hidden classes và class generated at runtime; chúng có quy tắc riêng, vì thế đường đi từ file trên đĩa không phải con đường duy nhất.

### Bài thực hành B — cùng tên nhưng khác defining loader

Tạo thêm hai tệp cùng package `demo` trong `src/demo/` (giữ nguyên hai tệp của bài A):

**`src/demo/Plugin.java`**

```java
package demo;

public class Plugin {
    public String message() {
        return "hello plugin";
    }
}
```

**`src/demo/LoaderIdentityDemo.java`**

```java
package demo;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class LoaderIdentityDemo {
    public static void main(String[] args) throws Exception {
        URL classesDir = Path.of("out").toAbsolutePath().toUri().toURL();

        try (URLClassLoader left = new URLClassLoader(new URL[] {classesDir}, null);
             URLClassLoader right = new URLClassLoader(new URL[] {classesDir}, null)) {

            Class<?> a = left.loadClass("demo.Plugin");
            Class<?> b = right.loadClass("demo.Plugin");

            System.out.println("same name: " + a.getName().equals(b.getName()));
            System.out.println("same Class: " + (a == b));
            System.out.println("assignable: " + a.isAssignableFrom(b));
        }
    }
}
```

Biên dịch và chạy **từ thư mục `project`**:

```sh
javac --release 17 -d out src/demo/Plugin.java src/demo/LoaderIdentityDemo.java
java -cp out demo.LoaderIdentityDemo
```

**Stdout chính xác:**

```text
same name: true
same Class: false
assignable: false
```

Ở đây hai `URLClassLoader` khác nhau đều có `parent=null` (ủy quyền về bootstrap khi thích hợp), tự định nghĩa `demo.Plugin` từ cùng thư mục `out`. Dù tên và bytecode giống nhau, `a` khác `b` vì defining loader khác. Đường dẫn `Path.of("out")` cố ý phụ thuộc thư mục chạy lệnh; chạy từ thư mục khác phải điều chỉnh đường dẫn. Đây là minh họa về **type identity**, không phải lời khuyên dùng parent `null` cho plugin production: mô hình cách ly thực tế cần cân nhắc API chia sẻ, quyền truy cập module, tài nguyên và vòng đời loader.

Nếu chuyển thành hai loader có parent là application loader và vẫn giữ `Plugin` trên classpath, quy tắc parent-first có thể khiến chúng ủy quyền để nhận lại **cùng** `demo.Plugin`. Đây là lý do phải xác định **defining loader** trước khi kết luận lớp nào được nạp từ file nào.

## 7. JVM thực thi bytecode sau khi nạp ra sao?

`Class` metadata và bytecode hợp lệ cho phép JVM xác định phương thức khi được gọi. Runtime có thể dùng **interpreter** để diễn giải bytecode và **JIT compiler** để biên dịch các đoạn nóng thành machine code. Điều này diễn ra sau/đan xen với nhu cầu sử dụng lớp; không đồng nghĩa toàn bộ class file được JIT ngay lúc loading. Optimizer có thể inline hoặc thay đổi cách thực thi nhưng phải duy trì hành vi Java được yêu cầu.

Một phương thức được gọi từ ứng dụng vẫn có thể làm phát sinh việc nạp/liên kết các lớp khác trong lúc chạy, do tham chiếu tới chúng chỉ vừa được sử dụng. Vì vậy biểu đồ đầu bài chỉ là **khung khái niệm**, chứ không phải toàn bộ các class của một ứng dụng sẽ cùng đi qua loading rồi linking rồi initialization ngay tại `main`.

Để khảo sát các sự kiện loading trên HotSpot hiện đại, có thể chạy:

```sh
java -Xlog:class+load=info -cp out demo.App
```

Đây là flag logging của HotSpot/JDK hỗ trợ Unified Logging; kết quả dài và phụ thuộc phiên bản JDK, classpath và môi trường. Dùng log để xem nguồn lớp và thời điểm có sự kiện nạp, **không** suy ra thời điểm initialization chỉ từ một dòng log loading.

## 8. Phân biệt lỗi theo giai đoạn

| Triệu chứng | Thường gắn với | Cần kiểm tra |
| --- | --- | --- |
| `javac: cannot find symbol` / `package ... does not exist` | Biên dịch | Import, tên kiểu, compile classpath/module path. |
| `ClassNotFoundException` | Tra cứu qua `loadClass` / `Class.forName` không tìm được tên yêu cầu | Binary name, JAR/classpath/module path, initiating loader. |
| `NoClassDefFoundError` | JVM cần định nghĩa/sử dụng class nhưng không tìm được hoặc lớp đã thất bại initialization trước đó | Dependency runtime, nested cause, class initializer trước đó. |
| `UnsupportedClassVersionError` | Class file mới hơn khả năng hỗ trợ của JVM | Phiên bản JDK chạy so với `javap -verbose` major version. |
| `ClassFormatError` / `VerifyError` | Định dạng class/kiểm tra bytecode | Tool sinh bytecode, bản build, thao tác sửa class. |
| `NoSuchMethodError` / `NoSuchFieldError` | Runtime resolution không khớp API từng biên dịch | Version JAR thực tế, dependency conflict, binary compatibility. |
| `ExceptionInInitializerError` | Static initialization có lỗi | Stacktrace nguyên nhân ở `<clinit>`, thứ tự gán static, side effects. |
| `ClassCastException` với cùng tên lớp ở hai vế | Hai định nghĩa lớp với defining loader khác nhau | Log loader, `getClassLoader()`, phiên bản plugin và shared API. |

Đây là **dấu hiệu định hướng**, không phải bảng ánh xạ 1–1 cho mọi tình huống. Đọc cả `Caused by` và ngữ cảnh lỗi; các lỗi thuộc họ `LinkageError` có thể xảy ra khi tham chiếu được dùng lần đầu, thay vì ngay khi chạy lệnh `java`.

Một checklist chẩn đoán ngắn: (1) xem **binary name** và nơi chứa `.class`, (2) xác minh JDK phiên bản biên dịch/chạy, (3) xác minh runtime classpath/module path thực tế, (4) xác minh **initiating/defining loader**, (5) xác định lỗi trước hay trong initialization, (6) đối chiếu dependency versions và stacktrace. `getClassLoader()` trả `null` cho bootstrap là điều bình thường.

## 9. Tự kiểm tra hiểu bài

1. **Có `out/demo/Target.class` trên đĩa thì `Target` đã ở trong JVM chưa?** Chưa chắc. Class file là tài nguyên, phải có nhu cầu/hoạt động loading để lớp runtime được định nghĩa.
2. **`Target.class` và `new Target()` có giống nhau?** Không. Biểu thức thứ nhất lấy `Class<?>` và không tự yêu cầu initialization; biểu thức thứ hai tạo instance và đòi hỏi initialization của lớp phù hợp trước.
3. **Vì sao `loadClass` tìm thấy `Target` mà khối `static` chưa in?** Loading và initialization là hai giai đoạn khác nhau; `loadClass(String)` thông thường không yêu cầu initialization.
4. **Tại sao cùng `demo.Plugin` có thể không cast được?** Runtime type identity còn chứa defining loader, ngoài binary name.
5. **Resolution có buộc xảy ra tất cả trước khi chạy `main`?** Không. JVM có thể phân giải tham chiếu theo nhu cầu; kiểm tra được yêu cầu của từng giai đoạn phải vẫn đúng.
6. **JVM chỉ dùng interpreter?** Không. HotSpot và nhiều implementation kết hợp interpreter/JIT; cách tối ưu không đổi semantics.
7. **Đóng `URLClassLoader` có lập tức unload các lớp?** Không. Close giải phóng tài nguyên mở bởi loader; unload còn phụ thuộc reachability và GC.

## 10. Ghi nhớ để đi tiếp trong chủ đề ClassLoader

Hãy luôn trả lời riêng bốn câu khi phân tích một lớp Java: **(1) Dữ liệu class đến từ đâu? (2) Loader nào định nghĩa? (3) Lớp đã verified/prepared/resolved tới mức nào? (4) Lớp đã initialized hay chưa?** Sau đó mới hỏi có instance nào, phương thức nào được thực thi và có JIT hay không.

Chuỗi chuẩn để nhớ là **source → compilation → class-file bytes → loading → linking → initialization khi active use → thực thi/instance theo nhu cầu**. Trên thực tế loading và resolution của nhiều lớp có thể đan xen, và một binary name có thể tương ứng nhiều runtime types qua nhiều defining loaders. Đây chính là điểm nối từ kiến thức về class file sang kiến trúc parent delegation, custom loader, plugin isolation và các lỗi classpath trong ứng dụng Java.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-003">Phân biệt ClassLoader, Class và đối tượng của lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt `ClassLoader`, `Class` và đối tượng của lớp trong Java

Trong Java, một **bộ nạp lớp** (class loader), một **đối tượng `Class`** và một **đối tượng được tạo từ lớp** là ba thực thể khác nhau. Chúng liên quan chặt chẽ, nhưng mỗi thực thể giải quyết một câu hỏi riêng:

- **`ClassLoader`:** JVM tìm, nạp và định nghĩa kiểu này bằng cách nào, trong không gian nạp lớp nào?
- **`Class<?>`:** Kiểu nào đang tồn tại ở thời điểm chạy, và JVM có thể kiểm tra hoặc phản chiếu thông tin nào về kiểu đó?
- **Instance (đối tượng của lớp):** Đây là thực thể cụ thể nào, với dữ liệu trạng thái riêng và danh tính riêng?

Hiểu đúng ba tầng này giúp giải thích vì sao một lớp có thể tạo nhiều đối tượng, vì sao hai lớp cùng tên đôi khi không tương thích kiểu, và vì sao việc lấy được `Class` chưa đồng nghĩa với việc đã tạo instance.

## 1. Mô hình tổng quan

```text
Nguồn định nghĩa kiểu (.class / bytecode / nguồn do loader cung cấp)
                         |
                         v
              ClassLoader / cơ chế nạp
                         |
            nạp + định nghĩa kiểu trong JVM
                         |
                         v
           Class<Sample> (mô tả kiểu lúc chạy)
                   /             \
                  /               \
                 v                 v
         new Sample()         new Sample()
                 |                 |
                 v                 v
            instance A         instance B
           (trạng thái A)     (trạng thái B)
```

Đây là mô hình khái niệm, không có nghĩa mỗi lần `new` đều đọc lại tệp `.class`. Sau khi kiểu đã được nạp/định nghĩa, các lần tạo đối tượng thông thường sử dụng định nghĩa kiểu đã có trong JVM.

| Góc nhìn | `ClassLoader` | `Class<?>` | Instance của lớp |
|---|---|---|---|
| Vai trò chính | Tìm, nạp, phối hợp định nghĩa lớp | Đại diện cho kiểu tại thời điểm chạy | Thực thể cụ thể của kiểu |
| Ví dụ biến | `ClassLoader loader` | `Class<Cat> type` | `Cat cat` |
| Cách thường lấy | `Cat.class.getClassLoader()` | `Cat.class`, `cat.getClass()`, `Class.forName(...)` | `new Cat()`, factory, reflection, deserialization… |
| Quan hệ số lượng | Một loader có thể phục vụ nhiều kiểu | Thông thường một `Class` cho mỗi định nghĩa kiểu được nạp | Một kiểu có thể có 0, 1 hoặc nhiều instance |
| Điều cần nhớ | Phân biệt **không gian định nghĩa lớp** | Phân biệt **kiểu runtime** | Phân biệt **danh tính và trạng thái từng đối tượng** |

Cả `ClassLoader` lẫn `Class<?>` đều là các **đối tượng Java** khi được biểu diễn bằng tham chiếu Java. Khác biệt ở đây là *vai trò*: `ClassLoader` là cơ chế nạp kiểu; `Class<?>` đại diện cho một kiểu; instance là đối tượng ứng dụng có kiểu đó. Bản thân một `ClassLoader` cũng có lớp runtime của nó, ví dụ `loader.getClass()`.

## 2. `ClassLoader`: ai nạp và định nghĩa lớp?

`ClassLoader` là lớp trừu tượng `java.lang.ClassLoader`, cùng với cơ chế loader tích hợp của JVM, tham gia quá trình tìm bytecode và biến bytecode thành định nghĩa lớp dùng tại thời điểm chạy.

Trong một ứng dụng Java thông thường có các tầng loader quen thuộc:

1. **Bootstrap loader** nạp các lớp nền tảng cốt lõi của Java. Nó được JVM thực hiện và không nhất thiết là một đối tượng `ClassLoader` Java thông thường; vì vậy `String.class.getClassLoader()` thường trả `null`.
2. **Platform class loader** phục vụ các lớp nền tảng trong môi trường mô-đun Java.
3. **Application/System class loader** thường phục vụ mã ứng dụng và thư viện trên classpath/module path phù hợp.

Các loader tùy biến có thể được dùng để nạp plugin, tách biệt phiên bản thư viện hoặc quản lý vòng đời mô-đun. Tuy vậy, **đừng mặc định một tên lớp luôn thuộc application loader**; kết quả phụ thuộc cách ứng dụng được chạy, cách đóng gói và loader thực tế.

### Nạp lớp khác với tạo đối tượng

Với lời gọi:

```java
ClassLoader loader = Thread.currentThread().getContextClassLoader();
Class<?> type = loader.loadClass("com.example.Widget");
```

`loader.loadClass(...)` trả về một đối tượng `Class<?>` hoặc báo `ClassNotFoundException`. Nó **không tạo `new Widget()`**. `ClassLoader.loadClass(String)` cũng không mặc nhiên yêu cầu khởi tạo lớp (chạy static initializer). Đoạn mã trên là **mảnh minh họa**: nó chỉ thành công khi tên lớp tồn tại và loader nhìn thấy lớp đó.

Một loader có thể **ủy quyền** (delegate) cho loader cha. Cơ chế mặc định của `ClassLoader.loadClass` thường kiểm tra lớp đã nạp, nhờ cha nạp trước, rồi mới gọi `findClass` khi cần; loader tùy biến có thể có chính sách khác. Vì vậy loader mà bạn gọi `loadClass` có thể là *initiating loader*, trong khi định nghĩa lớp thực tế thuộc về một *defining loader* khác. Kết quả `type.getClassLoader()` biểu thị **defining loader**, không nhất thiết là biến `loader` được dùng để gọi.

### Tên lớp chưa đủ để xác định danh tính kiểu

Ở cấp JVM, danh tính của kiểu tham chiếu được xác định bởi **tên nhị phân của lớp + defining class loader**. Ví dụ:

```text
Loader A --defines--> com.example.Plugin  => Class<?> X
Loader B --defines--> com.example.Plugin  => Class<?> Y

Cùng tên "com.example.Plugin", nhưng X != Y nếu A và B
thực sự định nghĩa hai lớp riêng.
```

Hai instance sinh ra từ hai định nghĩa đó không tự động cast được cho nhau; có thể xuất hiện `ClassCastException` dù log in cùng tên lớp. Ngược lại, nếu hai loader con đều ủy quyền cho cùng một loader cha đã định nghĩa `com.example.Plugin`, chúng có thể cùng nhận **một** `Class`, không phải hai bản độc lập. Đây là lý do không nên kết luận chỉ dựa vào tên lớp hoặc số loader tham gia.

## 3. `Class<T>`: đại diện cho một kiểu tại runtime

`java.lang.Class<T>` là đối tượng phản chiếu đại diện cho **một kiểu trong JVM**. Nó cho phép truy vấn thông tin kiểu và thực hiện một số thao tác reflection.

```java
Class<String> stringType = String.class;
String text = "hello";
Class<? extends String> runtimeType = text.getClass();

System.out.println(stringType == runtimeType); // true
```

`String.class` và `text.getClass()` cùng trả về đại diện cho kiểu `java.lang.String`. Toán tử `==` ở đây so sánh chính **đối tượng Class**, không phải so sánh hai chuỗi hay hai instance.

Những cách lấy `Class` phổ biến:

| Cách | Ý nghĩa | Có tạo instance? |
|---|---|---|
| `SomeType.class` | Lấy `Class` của kiểu biết tại lúc viết code | Không |
| `someObject.getClass()` | Lấy kiểu runtime thực của đối tượng đang có | Không tạo instance mới |
| `Class.forName("pkg.SomeType")` | Tìm kiểu theo tên nhị phân qua cơ chế loader của caller; mặc định yêu cầu khởi tạo kiểu | Không |
| `Class.forName(name, false, loader)` | Tra cứu/nạp kiểu với loader chỉ định và không yêu cầu khởi tạo kiểu | Không |
| `loader.loadClass(name)` | Nhờ loader tìm/nạp kiểu | Không |

**Chú ý:** `Class` không chỉ đại diện cho những kiểu có thể `new`. Interface, abstract class, array, primitive và `void` cũng có đại diện `Class` (ví dụ `Runnable.class`, `int[].class`, `int.class`, `void.class`). Không thể suy ra chỉ vì tồn tại `Class<?>` thì luôn có thể gọi constructor để tạo đối tượng.

### Metadata của kiểu và trạng thái instance

Đối tượng `Class` cho phép truy vấn tên, modifier, superclass, interface, constructor, method, field, annotation… theo API reflection. Nó **không phải nơi giữ trạng thái instance riêng** của mỗi đối tượng.

Ví dụ nếu có hai `Person`, mỗi đối tượng có một trường `name` riêng, trong khi `first.getClass() == second.getClass()` vẫn đúng nếu cả hai được tạo từ cùng định nghĩa lớp. Các trường `static` thuộc về định nghĩa lớp tương ứng và được chia sẻ giữa các instance của chính định nghĩa lớp đó; không nên nhầm với trường instance hoặc coi `Class` là một instance `Person`.

### `Class` cũng là một đối tượng Java

`Class<?> type = Person.class;` có nghĩa **`type` tham chiếu đến một đối tượng của lớp `java.lang.Class`**, đại diện cho `Person`. Do đó:

- `type.getName()` cho tên kiểu đang được đại diện, ví dụ `com.example.Person`.
- `type.getClass()` trả về `Class` của bản thân đối tượng mirror, tức `java.lang.Class`.
- `type.getClassLoader()` trả về loader đã **định nghĩa `Person`**.
- `type.getClass().getClassLoader()` liên quan loader định nghĩa `java.lang.Class`, **không phải nhất thiết** loader của `Person`.

Đây là một trong những chỗ dễ nhầm nhất khi đọc log reflection.

## 4. Instance: một thực thể của kiểu

Với:

```java
Person first = new Person("An");
Person second = new Person("Binh");
```

`first` và `second` là **hai đối tượng riêng**, có thể chứa trạng thái khác nhau. Trong trường hợp cả hai được tạo từ cùng lớp runtime:

```java
first.getClass() == second.getClass()  // true
first == second                        // false
```

Hai biểu thức này trả lời hai câu hỏi khác nhau:

- **Có cùng kiểu runtime không?** So sánh `getClass()`.
- **Có trỏ cùng một đối tượng không?** So sánh tham chiếu `==`.

Cú pháp trên là **mảnh minh họa**, giả định `Person` có constructor nhận tên. `equals()` là quy tắc bình đẳng logic do lớp định nghĩa; nó không thay thế cho việc phân biệt danh tính object và danh tính `Class`.

`new Person(...)` đòi hỏi có một lớp cụ thể và constructor dùng được. JVM dùng định nghĩa kiểu tương ứng để cấp phát và khởi tạo instance. Một chương trình có thể tải hoặc tham chiếu `Person.class` mà chưa từng tạo bất kỳ `Person` nào.

## 5. Thí nghiệm Java 17+: ba thực thể trong cùng một chương trình

Tạo tệp **`LoaderClassObjectDemo.java`** với toàn bộ nội dung sau:

```java
public class LoaderClassObjectDemo {
    static class Sample {
        static {
            System.out.println("Sample initialized");
        }

        Sample() {
            System.out.println("Sample constructor");
        }
    }

    public static void main(String[] args) throws ClassNotFoundException {
        // Lấy Class mà không tạo một Sample.
        Class<Sample> type = Sample.class;
        System.out.println("class literal captured");

        // Tìm cùng định nghĩa lớp, nhưng không yêu cầu initialize.
        Class<?> lookedUp = Class.forName(
                type.getName(), false, type.getClassLoader());
        System.out.println("same Class mirror: " + (type == lookedUp));

        ClassLoader definingLoader = type.getClassLoader();
        System.out.println("same loader: " +
                (definingLoader == LoaderClassObjectDemo.class.getClassLoader()));

        // Lần đầu tạo Sample: khởi tạo lớp, sau đó gọi constructor.
        Sample first = new Sample();
        Sample second = new Sample();

        System.out.println("same runtime Class: " +
                (first.getClass() == second.getClass()));
        System.out.println("runtime Class equals literal: " +
                (first.getClass() == type));
        System.out.println("same object: " + (first == second));
        System.out.println("isInstance: " + type.isInstance(first));
    }
}
```

Chạy với JDK 17 hoặc mới hơn:

```bash
javac LoaderClassObjectDemo.java
java LoaderClassObjectDemo
```

Kết quả chuẩn:

```text
class literal captured
same Class mirror: true
same loader: true
Sample initialized
Sample constructor
Sample constructor
same runtime Class: true
runtime Class equals literal: true
same object: false
isInstance: true
```

**Giải thích theo thứ tự:**

1. `Sample.class` lấy được `Class<Sample>` mà **chưa kích hoạt static initializer** của `Sample`.
2. `Class.forName(type.getName(), false, type.getClassLoader())` trả lại cùng định nghĩa `Sample`, với cờ `initialize=false`. Hai biến `type` và `lookedUp` vì thế cùng trỏ một `Class`.
3. `Sample` và lớp bao `LoaderClassObjectDemo` ở đây đều được định nghĩa qua cùng cơ chế nạp mã ứng dụng, nên phép so sánh loader trả `true`. Chương trình không phụ thuộc vào tên cụ thể của loader.
4. Lệnh `new Sample()` đầu tiên kích hoạt khởi tạo lớp `Sample` (in một lần `Sample initialized`), rồi gọi constructor. Lệnh `new` thứ hai chỉ chạy constructor; khởi tạo lớp không lặp lại cho cùng định nghĩa kiểu.
5. Hai instance khác nhau (`first == second` là `false`), nhưng đều tham chiếu đến cùng đối tượng `Class<Sample>`. `type.isInstance(first)` kiểm tra kiểu phù hợp, nên trả `true`.

Thí nghiệm cũng chỉ ra ba việc **lấy `Class`**, **khởi tạo lớp** và **khởi tạo một instance** là các sự kiện khác nhau.

## 6. Từ định nghĩa kiểu đến tạo đối tượng: hai vòng đời khác nhau

Định nghĩa lớp có vòng đời **loading → linking → initialization**; một đối tượng cụ thể lại có vòng đời **cấp phát → khởi tạo trường instance → chạy constructor**. Chúng liên quan nhưng không đồng nhất: lấy `Class<?>` không tự tạo instance; gọi `new` cần lớp tương ứng đã được khởi tạo khi cần và tạo một đối tượng mới.

| Quan sát | Kết luận đúng |
| --- | --- |
| `Target.class` hay `loader.loadClass(name)` trả `Class<?>` | Đã có đối tượng đại diện cho kiểu; không suy ra constructor instance hay `static {}` đã chạy |
| `Class.forName(name, true, loader)` | Yêu cầu khởi tạo kiểu nếu chưa khởi tạo; **không** tự tạo instance |
| `new Target()` lần đầu | JVM bảo đảm khởi tạo lớp theo quy tắc rồi tạo/khởi tạo **một instance** |
| `new Target()` lần tiếp theo | Tạo instance khác và chạy constructor tương ứng; initialization của cùng định nghĩa lớp thành công không lặp lại |

Ví dụ ở mục 5 cho thấy `Sample initialized` chỉ xuất hiện một lần, còn `Sample constructor` xuất hiện **hai lần**. Với lớp abstract/interface hoặc chỉ được tra cứu qua reflection, có thể tồn tại `Class<?>` mà chưa có instance nào. Để phân tích sâu verification, preparation, resolution và những trigger đặc biệt, cần phân biệt theo quy tắc của JVM; ở đây câu hỏi chính là **kiểu runtime có phải chính đối tượng nghiệp vụ không?**

## 7. Vì sao phân biệt đúng lại hữu ích?

### Tình huống A — Classpath/plugin báo `ClassNotFoundException`

Nếu `loadClass("com.example.Plugin")` không tìm thấy kiểu, đó là vấn đề ở quá trình **tìm/nạp tên lớp** qua loader hiện hành. Kiểm tra tên nhị phân, đường dẫn phân phối bytecode, module/classpath và loader được sử dụng. Việc thêm `new` hay cast không giải quyết được một kiểu chưa thể tìm thấy.

### Tình huống B — `ClassCastException` dù hai bên in cùng tên

Khi hai plugin mỗi bên có một loader định nghĩa riêng lớp cùng tên, kiểu runtime có thể khác nhau. Đọc `value.getClass().getName()` chỉ cho thấy tên, chưa đủ. Khi chẩn đoán, xem thêm **`value.getClass().getClassLoader()`** và `expectedType.getClassLoader()`, xác định loader nào thực sự định nghĩa kiểu. Thiết kế API chung nên đặt các interface hoặc DTO chia sẻ dưới loader chung có thể nhìn thấy từ cả hai phía.

### Tình huống C — Lấy `Class` nhưng constructor chưa chạy

`Class<?> type = SomeType.class;` không tự có instance. Khi dùng reflection, có thể gọi `getDeclaredConstructor(...).newInstance(...)` nếu kiểu cụ thể, constructor phù hợp và quyền truy cập cho phép. Việc tạo instance bằng reflection vẫn khác với việc tìm/nạp lớp; constructor có thể ném ngoại lệ, và reflection còn chịu ràng buộc truy cập/module. Đừng xem `Class.newInstance()` cũ là cách tạo đối tượng nên ưu tiên.

### Tình huống D — Dò loader của một đối tượng

Ví dụ:

```java
ClassLoader loaderOfType = instance.getClass().getClassLoader();
```

Đây là loader **định nghĩa kiểu runtime của `instance`**. Nếu `instance` là đối tượng `Class<?>`, cần quyết định muốn hỏi loader của **`java.lang.Class`** hay của **kiểu được đại diện**:

```java
Class<?> type = SomeType.class;
ClassLoader loaderOfSomeType = type.getClassLoader();
ClassLoader loaderOfClassMirror = type.getClass().getClassLoader();
```

Hai kết quả có thể khác nhau; kiểu `java.lang.Class` thuộc Java nền tảng, còn `SomeType` có thể thuộc loader ứng dụng.

## 8. Những hiểu nhầm phổ biến

**“Một tệp `.class` tương ứng đúng một `Class` trên toàn JVM.”** Không chính xác: cùng tên và bytecode có thể được định nghĩa tách biệt bởi những defining loader khác nhau. Trong cùng một định nghĩa lớp còn có nhiều instance.

**“Mỗi lần `new` JVM lại nạp lớp.”** Không đúng trong cách sử dụng thông thường: lớp được định nghĩa/lưu giữ để sử dụng lại, còn việc `new` tạo instance mới. Khi có loader khác định nghĩa lớp mới, đó là một định nghĩa runtime khác.

**“`ClassLoader` chính là `Class`.”** Sai: `ClassLoader` làm nhiệm vụ tìm/nạp/định nghĩa; `Class` là đại diện cho kiểu đã có. `ClassLoader.loadClass` trả về `Class<?>`, không trả instance ứng dụng.

**“`Class` chính là instance của kiểu nó đại diện.”** Sai: `Person.class` là một đối tượng `Class`, không phải `Person`. Nó không có giá trị của trường instance `name` của một `Person` cụ thể.

**“`getClassLoader() == null` nghĩa là lớp chưa nạp hoặc loader bị lỗi.”** Sai: với lớp bootstrap như `String`, `null` là dấu hiệu thường gặp cho bootstrap loader. Các loại đặc biệt như primitive/`void` không có loader do người dùng định nghĩa theo cách lớp thông thường.

**“`getClass()` và `instanceof` luôn cho cùng kiểu kiểm tra.”** Khác nhau: `getClass() == SomeType.class` kiểm tra **đúng kiểu runtime**, còn `instanceof SomeType` chấp nhận đối tượng có kiểu con/triển khai phù hợp. Với custom loaders, cả hai còn tuân theo danh tính kiểu mà JVM đã định nghĩa.

**“Loader là ranh giới bảo mật tuyệt đối.”** Không đúng: loader phục vụ nạp và cô lập danh tính kiểu, nhưng bản thân nó không thay thế sandbox, quản lý quyền truy cập hoặc kiểm soát mã không tin cậy.

## 9. Tự kiểm tra

**Câu 1.** `A a1 = new A(); A a2 = new A();` với cùng định nghĩa lớp. Biểu thức nào thường đúng: `a1 == a2` hay `a1.getClass() == a2.getClass()`?

**Đáp án:** Biểu thức thứ hai. Mỗi lần `new` tạo một đối tượng riêng, nhưng cả hai dùng chung đại diện cho cùng kiểu runtime.

**Câu 2.** Lệnh `Class<?> t = Class.forName("pkg.A", false, loader);` có tự gọi constructor `A()` không?

**Đáp án:** Không. Nó tìm/nạp kiểu (không yêu cầu khởi tạo lớp) và trả `Class<?>`; constructor instance chỉ chạy khi thực sự tạo đối tượng bằng cơ chế tương ứng.

**Câu 3.** Tại sao `com.example.Plugin` ở plugin X không cast được sang `com.example.Plugin` ở plugin Y dù cùng tên?

**Đáp án:** Nếu hai defining loader khác nhau đã định nghĩa hai lớp riêng, JVM xem chúng là hai kiểu runtime khác nhau. Cần thống nhất API/type chung ở loader chung hoặc truyền dữ liệu qua giao diện/cơ chế chuyển đổi thích hợp.

**Câu 4.** Tại sao `String.class.getClassLoader()` thường là `null`?

**Đáp án:** `String` do bootstrap loader nạp/định nghĩa; bootstrap không được biểu diễn bằng một instance `ClassLoader` Java giống như loader ứng dụng.

## 10. Ghi nhớ mô hình ba tầng

```text
ClassLoader  --nạp/định nghĩa-->  Class<?>  --được dùng khi tạo-->  Object
  (ai nạp?)                    (kiểu nào?)                    (thực thể nào?)
```

Khi gặp một lỗi runtime liên quan đến lớp, hỏi theo thứ tự: **tên và loader có định nghĩa đúng kiểu không? → `Class` runtime thực là gì? → đối tượng cụ thể nào đang được tạo hoặc truyền qua API?** Ba câu hỏi tách bạch thường dẫn đến nguyên nhân nhanh hơn việc chỉ nhìn tên lớp hay tên file `.class`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-004">Phân biệt nạp lớp, liên kết và khởi tạo</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt nạp lớp, liên kết và khởi tạo trong Java

## 1. Mục tiêu và bức tranh tổng quan

Khi JVM sử dụng một lớp, ba khái niệm thường bị gọi chung là “load class”, nhưng thực chất chúng làm ba công việc khác nhau:

1. **Nạp lớp (loading):** tìm hoặc tạo biểu diễn của lớp/giao diện trong JVM từ tên và định nghĩa của nó.
2. **Liên kết (linking):** kiểm tra tính hợp lệ, chuẩn bị trạng thái tĩnh và xử lý các tham chiếu ký hiệu để lớp có thể được sử dụng an toàn.
3. **Khởi tạo (initialization):** thực thi logic khởi tạo biến tĩnh và khối static của lớp/giao diện vào thời điểm Java quy định.

Một mô hình đọc nhanh:

~~~text
Mã nguồn .java
  │ javac (biên dịch trước khi chạy)
  ▼
Bytecode .class
  │
  ▼
LOADING
  ├─ Tìm bytecode / định nghĩa lớp
  ├─ Tạo biểu diễn runtime và đối tượng java.lang.Class
  └─ Xác định lớp qua (tên nhị phân, defining class loader)
  │
  ▼
LINKING
  ├─ Verification: kiểm tra bytecode và các ràng buộc an toàn
  ├─ Preparation: cấp phát/trang bị giá trị ban đầu cho static fields
  └─ Resolution: đổi tham chiếu ký hiệu thành thực thể runtime
  │
  ▼
INITIALIZATION
  ├─ Khởi tạo những lớp/interface tiên quyết theo quy tắc JVM
  └─ Chạy mã khởi tạo static của chính lớp (nếu có)
  │
  ▼
Sử dụng lớp: tạo instance, gọi phương thức, đọc/ghi trạng thái...
~~~

**Điểm then chốt:** lớp đã được nạp không có nghĩa là nó đã được khởi tạo; lớp có thể đã được liên kết mà khối static vẫn chưa chạy. JVM có quyền giải quyết nhiều tham chiếu ký hiệu theo kiểu **lazy**, nên không nên giả định rằng toàn bộ công việc resolution đều hoàn tất ngay khi lần đầu thấy lớp.

## 2. Nạp lớp (loading) thực sự làm gì?

JVM cần một định nghĩa runtime cho kiểu được nhắc tới. Với lớp do lập trình viên khai báo, nó thường dùng một **class loader** để tìm bytecode và tạo định nghĩa lớp. Bộ nạp lớp có thể lấy bytecode từ classpath, module, JAR, mạng hoặc nguồn riêng theo thiết kế ứng dụng. Cơ chế delegation thường cho loader cha cơ hội xử lý trước; các custom loader có thể có chính sách khác.

Kết quả có đối tượng **java.lang.Class** đại diện cho kiểu. JVM dùng **tên nhị phân + defining class loader** để phân biệt các định nghĩa lớp: hai loader khác nhau có thể tạo ra hai lớp khác nhau dù cùng tên đầy đủ. Vì vậy, ở môi trường plugin/container, hai lớp cùng tên chưa chắc ép kiểu được cho nhau.

Loading **không chạy** static block, không gọi constructor và không tự tạo instance của lớp. Mảng có quy tắc tạo lớp mảng riêng do JVM thực hiện; tạo một mảng chứa phần tử kiểu Foo không đồng nghĩa với khởi tạo Foo.

Cần phân biệt thao tác yêu cầu loader và kết quả JVM có thể thực hiện: **ClassLoader.loadClass("pkg.Foo")** theo hợp đồng thông thường trả về Class mà không chủ động khởi tạo Foo; việc JVM nạp hoặc liên kết những kiểu liên quan có thể diễn ra khi cần. Đừng dùng các log nạp lớp để suy luận rằng logic static đã chạy.

## 3. Liên kết (linking): ba hoạt động với trách nhiệm riêng

| Hoạt động | Ý nghĩa | Điều không được suy diễn |
| --- | --- | --- |
| **Verification** (xác minh) | Kiểm tra cấu trúc và tính hợp lệ của bytecode/ràng buộc kiểu; có thể báo VerifyError khi định nghĩa không hợp lệ. | Không phải bước thực thi hàm static hay constructor. |
| **Preparation** (chuẩn bị) | Chuẩn bị lưu trữ cho static fields và giá trị mặc định của chúng; với constant variables thích hợp, giá trị từ ConstantValue có thể được thiết lập ngay trong bước này. | Không chạy biểu thức khởi tạo static tùy ý của chương trình. |
| **Resolution** (phân giải) | Biến các symbolic references trong constant pool thành tham chiếu runtime tương ứng: lớp, field, method...; kiểm tra khả năng truy cập/ràng buộc khi cần. | Không nhất thiết phân giải tất cả trong một lần hoặc đúng một thời điểm cố định. |

Ví dụ, khai báo:

~~~java
class Sample {
    static int counter = 42;
    static final int LIMIT = 10;
    static final String LABEL = "ready";
    static final Object TOKEN = new Object();
}
~~~

Trong **preparation**, các trường static được cấp trạng thái mặc định: kiểu int thông thường bắt đầu là 0, tham chiếu Object mặc định null. Hai **constant variables** LIMIT và LABEL có giá trị constant expression, được biểu diễn bằng ConstantValue và có thể được thiết lập theo quy tắc preparation. Ngược lại, **counter = 42** và **new Object()** là công việc của initialization: chúng không được thực thi chỉ vì class đã chuẩn bị xong.

Đừng xem bảng trên như một danh sách sự kiện có thể nhìn thấy đầy đủ qua log. Java Virtual Machine Specification cho phép **resolution xảy ra muộn**, thậm chí chỉ khi một tham chiếu được sử dụng. Sự xuất hiện của lỗi như NoSuchMethodError hoặc IllegalAccessError có thể phản ánh một tham chiếu vừa được phân giải trong lúc chạy, không nhất thiết là khi JVM lần đầu load lớp.

## 4. Khởi tạo (initialization): thời điểm mã static thực sự chạy

Initialization là giai đoạn JVM thực thi phương thức khởi tạo lớp đặc biệt **<clinit>** (nếu compiler tạo ra) từ các static field initializers và static initializer blocks, theo **thứ tự xuất hiện trong mã nguồn**. Đó không phải constructor của đối tượng. Constructor chạy mỗi khi tạo một instance thích hợp; initialization của một định nghĩa lớp thành công thông thường chỉ diễn ra **một lần trong vòng đời của Class tương ứng**.

Trước khi khởi tạo một class, JVM bảo đảm khởi tạo superclass cần thiết và các superinterface thuộc diện quy định (đáng chú ý superinterface khai báo default method). Việc khởi tạo một interface thông thường không mặc nhiên khởi tạo tất cả superinterface của nó. Trình tự và việc đồng bộ giữa các thread do JVM kiểm soát: thread khác cần sử dụng class có thể phải đợi quá trình khởi tạo đang diễn ra. Nếu initialization thất bại, class có thể bị đánh dấu trạng thái lỗi và các lần sử dụng sau gặp lỗi liên quan.

**Lưu ý về giá trị trong static initialization:** static fields đã có trạng thái mặc định từ preparation. Nếu một static initializer gọi phương thức đọc một field được gán ở phần mã nằm phía sau, phương thức có thể quan sát giá trị mặc định (ví dụ 0), không phải giá trị cuối cùng. Không đánh đồng cấp giá trị mặc định với việc thực thi các dòng gán.

## 5. Khi nào một thao tác làm class được khởi tạo?

Theo các điều kiện của Java Language Specification, các hành vi **active use** tiêu biểu dưới đây dẫn tới initialization của class tương ứng nếu class chưa được khởi tạo:

| Thao tác | Khởi tạo lớp đích? | Giải thích |
| --- | --- | --- |
| Tạo đối tượng bằng **new Foo()** | **Có** | Foo cần được khởi tạo trước khi tạo instance. |
| Đọc/ghi **Foo.staticField** không phải constant variable | **Có** | Khởi tạo **lớp khai báo field thực sự**, không nhất thiết là tên lớp viết bên trái dấu chấm. |
| Gọi **Foo.staticMethod()** | **Có** | Lớp khai báo phương thức static được khởi tạo trước lời gọi. |
| **Class.forName("pkg.Foo")** | **Có, theo mặc định** | Dạng một đối số yêu cầu load/link/initialize kiểu tìm được. |
| **Class.forName(name, false, loader)** | **Không chủ động khởi tạo** | Thích hợp để lấy Class và trì hoãn initialization. |
| **loader.loadClass(name)** | **Không chủ động khởi tạo** | Trả Class theo hợp đồng của phương thức; class có thể được link. |
| **Foo.class** | **Không** | Class literal không phải active use làm chạy static block. |
| **new Foo[3]** | **Không khởi tạo Foo** | Tạo class mảng/đối tượng mảng; chưa tạo instance Foo. |
| Đọc **Foo.CONSTANT** với compile-time constant primitive/String | **Thông thường không** | Compiler có thể thay bằng hằng ngay trong bytecode của caller, không phát sinh getstatic. |
| Truy cập **static final** khởi tạo bằng lời gọi hàm hoặc tạo object | **Có khi field được đọc trực tiếp** | final không đồng nghĩa với compile-time constant. |

Ví dụ **Parent.X** khi X thực ra được khai báo trong Parent và được truy cập qua tên Child sẽ khởi tạo **Parent**, không mặc nhiên khởi tạo Child. Một interface có quy tắc initialization riêng: việc gọi static method hoặc đọc static field không phải constant variable của interface cũng có thể kích hoạt khởi tạo interface đó.

Đối với reflection, việc chỉ lấy đối tượng Class hoặc xem metadata chưa cho phép kết luận static initializer chạy; những thao tác như đọc static field bằng reflection hay gọi static method bằng reflection có thể kích hoạt initialization. Tương tự, các cơ chế MethodHandle, lần khởi động JVM hay API đặc biệt có quy tắc liên quan: bảng trên trình bày **các trường hợp cốt lõi**, không phải danh sách đầy đủ mọi trigger của đặc tả.

## 6. Ví dụ chạy được: chứng minh “có Class” khác “đã chạy static”

Tạo tệp **LoadLinkInitDemo.java**:

~~~java
public class LoadLinkInitDemo {
    static class Target {
        static final int COMPILE_TIME_CONST = 7;

        static int value = initValue();

        static {
            System.out.println("Target: static block");
        }

        static int initValue() {
            System.out.println("Target: init value");
            return 42;
        }
    }

    public static void main(String[] args) throws ClassNotFoundException {
        System.out.println("1. constant = " + Target.COMPILE_TIME_CONST);

        Class<?> type = Target.class;
        System.out.println("2. class literal = " + type.getSimpleName());

        Class.forName(type.getName(), false, type.getClassLoader());
        System.out.println("3. forName(false) completed");

        System.out.println("4. value = " + Target.value);
        System.out.println("5. value again = " + Target.value);
    }
}
~~~

Chạy với Java 17 trở lên:

~~~bash
javac LoadLinkInitDemo.java
java LoadLinkInitDemo
~~~

Stdout kỳ vọng của chương trình:

~~~text
1. constant = 7
2. class literal = Target
3. forName(false) completed
Target: init value
Target: static block
4. value = 42
5. value again = 42
~~~

Giải thích từng mốc:

1. **COMPILE_TIME_CONST** là constant variable: compiler có thể in-line giá trị 7 vào caller, nên chưa cần initialization của Target.
2. **Target.class** tạo/lấy đối tượng Class mà không khởi tạo Target. Lớp có thể đã được nạp, và JVM có thể làm một số việc liên kết, nhưng không có dòng static nào được in.
3. **Class.forName(..., false, ...)** yêu cầu lấy Class nhưng tham số false ngăn initialization theo yêu cầu gọi API. Không được kết luận tại đây mọi resolution của Target đều đã hoàn tất.
4. Lần đầu **Target.value** là active use: trước khi in “4. value”, JVM thực thi **initValue()**, rồi static block theo thứ tự nguồn. Sau đó phép đọc trả về 42.
5. Lần đọc sau **không chạy lại** static block: cùng một định nghĩa class đã được khởi tạo thành công.

Đây là minh chứng cho **initialization**, không phải công cụ đo chính xác thời điểm verification hoặc resolution. Dựa vào riêng stdout không thể dựng lại toàn bộ vòng đời linking.

## 7. Ví dụ thứ hai: thứ tự superclass và số lần khởi tạo

Tạo **InitOrderDemo.java**:

~~~java
public class InitOrderDemo {
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

    public static void main(String[] args) {
        new Child();
        new Child();
    }
}
~~~

~~~bash
javac InitOrderDemo.java
java InitOrderDemo
~~~

Stdout:

~~~text
Parent initialized
Child initialized
~~~

Superclass được khởi tạo trước Child; hai biểu thức new tạo **hai đối tượng** nhưng không chạy static initializer hai lần. Nếu có constructor in ra màn hình, phần constructor sẽ chạy cho mỗi đối tượng, khác với hai dòng trên.

## 8. Lỗi thường gặp nằm ở giai đoạn nào?

| Hiện tượng hoặc lỗi | Cách hiểu phù hợp |
| --- | --- |
| **ClassNotFoundException** khi gọi Class.forName/loadClass | Loader được yêu cầu tìm lớp theo tên nhưng không tìm/không nạp được; đây thường là checked exception của API tìm lớp. |
| **NoClassDefFoundError** | JVM không thể có định nghĩa class cần dùng tại thời điểm liên kết/thực thi, hoặc class đã rơi vào trạng thái lỗi sau initialization thất bại; không hoàn toàn đồng nghĩa ClassNotFoundException. |
| **VerifyError** | Bytecode vi phạm quy tắc xác minh; thuộc nhóm vấn đề linking/verification. |
| **NoSuchMethodError / NoSuchFieldError / IllegalAccessError** | Mismatch của binary/API/truy cập bộc lộ khi tham chiếu được phân giải hoặc sử dụng; cần kiểm tra dependency/classpath/loader. |
| **ExceptionInInitializerError** | Initialization có exception không thuộc Error được ném ra khỏi logic khởi tạo static; JVM bọc exception gốc vào lỗi này. |
| **Exception hoặc Error trong <clinit>** | Không phải mọi lỗi initialization đều được bọc giống nhau: một Error có thể được ném trực tiếp; các lần dùng class sau đó có thể nhận NoClassDefFoundError. |

Một lỗi phát sinh “lúc khởi động ứng dụng” **không đủ** để xác định nó thuộc loading, linking hay initialization. Hãy xem **loại Throwable, chuỗi cause và stack trace**, xác định lớp nào được JVM yêu cầu, dependency nào đang có trên runtime classpath và class nào **định nghĩa** field/method đang được dùng.

Ví dụ, static initializer phụ thuộc vào cấu hình môi trường chưa sẵn sàng có thể làm toàn bộ class không khởi tạo được. Đưa tác vụ có thể thất bại (kết nối mạng, đọc secrets, I/O) ra khỏi static initializer thường giúp startup rõ ràng hơn, xử lý và thử lại lỗi dễ hơn.

## 9. Cách phân tích một tình huống thực tế

Khi thấy “lớp đã load nhưng chưa in dòng trong static block”, kiểm tra theo thứ tự:

1. **Tác vụ cụ thể là gì?** Chỉ gọi loadClass, lấy Foo.class, tra metadata hay đã dùng new/getstatic/invokestatic?
2. **Field có phải constant variable?** Một giá trị static final có kiểu primitive/String với constant expression có thể được compiler in-line. Kiểm tra bytecode caller bằng **javap -c** nếu cần.
3. **Thành viên thật sự được khai báo ở lớp nào?** Truy cập inherited static field qua subclass không làm subclass initialization chỉ vì cú pháp dùng tên subclass.
4. **Loader nào đã định nghĩa class?** Trong plugin server, nhiều loader có thể tạo Class cùng tên. Kiểm tra **type.getClassLoader()** và module/classpath thực tế.
5. **Có lỗi ở initialization trước đó không?** Tìm exception đầu tiên và cause gốc; NoClassDefFoundError xuất hiện sau chỉ có thể là hậu quả.
6. **Đừng đánh giá resolution qua một mốc duy nhất.** JVM được phép giải quyết các symbolic references theo yêu cầu trong quá trình chạy.

Có thể dùng **javap -c LoadLinkInitDemo** để quan sát những lệnh bytecode khác nhau (ví dụ in-line constant khác với **getstatic**); với HotSpot trên Java hiện đại, **java -Xlog:class+load=info LoadLinkInitDemo** hỗ trợ quan sát sự kiện nạp lớp. Log class loading không trực tiếp chứng minh thời điểm khởi tạo: để thấy điều đó, hãy dùng output có kiểm soát từ static initializer như ví dụ. Tùy phiên bản/VM, kiểm tra các tag logging thực tế bằng **java -Xlog:help**.

## 10. Những hiểu nhầm cần tránh

- **“Nạp lớp là chạy static block.”** Sai. Nạp lớp tạo định nghĩa runtime; static block thuộc initialization.
- **“Linking chỉ là kiểm tra bytecode.”** Thiếu. Linking gồm verification, preparation và resolution.
- **“Static field nhận giá trị gán trong mã ngay khi preparation.”** Sai với biểu thức khởi tạo thông thường: preparation thiết lập giá trị mặc định; mã gán chạy khi initialization. ConstantValue là trường hợp phải phân biệt.
- **“final nghĩa là hằng compile-time.”** Sai. **static final Object TOKEN = new Object()** và **static final int N = calculate()** không phải constant variables.
- **“Gọi class literal chắc chắn chạy <clinit>.”** Sai. **Foo.class** chưa phải active use kích hoạt initialization.
- **“Class.forName luôn chỉ load.”** Sai. Dạng một tham số thường khởi tạo class; overload có cờ initialize mới điều khiển được hành vi này.
- **“Một class được khởi tạo đúng một lần theo tên.”** Thiếu điều kiện: cùng một tên có thể được định nghĩa bởi những loader khác nhau, và mỗi **Class** có trạng thái initialization riêng.
- **“Nhìn thấy lỗi lúc đầu chương trình tức là lỗi loading.”** Sai. Linking và initialization cũng có thể gây lỗi sớm, và lazy resolution có thể gây lỗi muộn.

## 11. Tự kiểm tra kiến thức

**Câu 1.** Nếu class A có static block, lệnh **Class<?> x = A.class;** có bảo đảm static block chạy chưa?

**Đáp án:** Không. Lấy class literal không chủ động initialization.

**Câu 2.** Nếu **static final int MAX = 100;**, vì sao việc in **A.MAX** có thể không chạy static block của A?

**Đáp án:** MAX là constant variable có compile-time constant expression. Caller có thể chứa trực tiếp giá trị 100 mà không truy cập static field lúc chạy.

**Câu 3.** Nếu class đã được load và preparation xong, có thể còn symbolic reference chưa resolution không?

**Đáp án:** Có. Resolution có thể được thực hiện lazy khi tham chiếu cần thiết.

**Câu 4.** Hai plugin loader định nghĩa riêng **example.Task**. Tại sao cả hai đều có thể chạy logic static initializer của Task?

**Đáp án:** Chúng là hai định nghĩa runtime khác nhau vì defining loader khác nhau; initialization được theo dõi cho từng Class.

**Câu 5.** Code gọi static field lần đầu thì gặp **ExceptionInInitializerError**, lần sau gặp **NoClassDefFoundError**. Nên điều tra lỗi nào trước?

**Đáp án:** Điều tra exception/cause của lần initialization thất bại ban đầu. Lần sau class có thể đã ở trạng thái lỗi, khiến NoClassDefFoundError chỉ là triệu chứng.

## 12. Ghi nhớ

**Loading** trả lời: *JVM có định nghĩa của kiểu này chưa, và loader nào định nghĩa nó?*  
**Linking** trả lời: *Định nghĩa đó đã được kiểm tra/chuẩn bị, và những tham chiếu cần thiết đã được phân giải thế nào?*  
**Initialization** trả lời: *Khi nào mã static thực thi, theo thứ tự nào, và kết quả thành công hay thất bại?*

Khi gỡ lỗi, hãy tách ba câu hỏi này. Đừng suy ra thời điểm chạy static initializer chỉ từ việc JVM đã nhận diện được Class, và cũng đừng cho rằng mọi tham chiếu đều phải resolution ngay lúc load.

**Tài liệu gốc:** Java Language Specification §12.2–12.4 (loading, linking, initialization) và Java Virtual Machine Specification §5.3–5.5 (creation/loading, linking, initialization), bản Java SE 21:
- https://docs.oracle.com/javase/specs/jls/se21/html/jls-12.html
- https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-005">Không gian tên, khả năng nhìn thấy và cô lập phụ thuộc</a>

<details>
<summary>Xem chi tiết</summary>

# Không gian tên, khả năng nhìn thấy và cô lập phụ thuộc trong Java ClassLoader

## 1. Vì sao cùng tên lớp chưa chắc là cùng kiểu?

Trong Java, `ClassLoader` vừa đưa định nghĩa lớp vào JVM, vừa tạo ranh giới **danh tính kiểu** (type identity). Điều này đặc biệt quan trọng khi chạy ứng dụng có plugin, container, hệ thống module hoặc nhiều phiên bản thư viện.

Một lớp ở thời điểm chạy được nhận diện về cơ bản bằng **cặp (tên nhị phân của lớp, class loader định nghĩa lớp đó)**. Ví dụ: `(com.example.dto.Order, LoaderA)` và `(com.example.dto.Order, LoaderB)` là **hai kiểu khác nhau**, dù bytecode hoặc mã nguồn giống hệt nhau. Vì vậy, một đối tượng thuộc kiểu thứ nhất không tự động ép kiểu được sang kiểu thứ hai.

Cần phân biệt ba thuật ngữ:

- **Binary name**: tên JVM dùng để tham chiếu, chẳng hạn `com.example.dto.Order`.
- **Defining loader**: loader thực sự định nghĩa lớp từ bytecode, thường qua `defineClass`. Đây là thành phần quyết định danh tính kiểu.
- **Initiating loader**: loader đã yêu cầu tải lớp và có thể nhận kết quả từ loader cha. Một lớp có thể có nhiều initiating loader nhưng chỉ một defining loader cho một định nghĩa cụ thể.

Do đó, gọi `child.loadClass("java.lang.String")` không có nghĩa `child` định nghĩa `String`; trong cơ chế ủy quyền thông thường, lớp này được trả về từ bootstrap loader.

### Hệ quả thực tế

| Tình huống | Ý nghĩa |
| --- | --- |
| Hai loader khác nhau tự định nghĩa `pkg.Foo` | Có hai kiểu `pkg.Foo` riêng biệt |
| Hai loader con cùng nhận `pkg.Api` từ loader cha | Dùng chung **một** kiểu `pkg.Api` |
| Cùng tên package nhưng lớp được định nghĩa bởi hai loader | Không tự động thuộc cùng **runtime package** |
| Hai plugin dùng hai phiên bản thư viện được tải riêng | Có thể cùng tồn tại nếu không rò kiểu riêng qua API chung |

**Ghi nhớ:** “Có thể tìm thấy file `.class`” và “có thể dùng lớp như cùng một kiểu” là hai câu hỏi khác nhau.

## 2. Không gian tên khi JVM giải quyết tham chiếu giữa các lớp

Khả năng **tìm thấy** một lớp, quyền **truy cập** lớp ấy, và khả năng **dùng chung định danh kiểu** là ba phép kiểm tra khác nhau. Một loader có thể yêu cầu lớp từ parent mà bản thân không định nghĩa lớp đó; `clazz.getClassLoader()` cho biết **defining loader**, còn `Thread.currentThread().getContextClassLoader()` là lựa chọn loader theo ngữ cảnh của một số API khám phá service.

Trong quá trình liên kết, JVM còn áp đặt **loading constraints** tại những ranh giới kiểu như chữ ký phương thức giữa lớp do các loader khác nhau định nghĩa. Vì vậy, hai plugin có thể nạp những thư viện riêng thành công nhưng gặp `LinkageError` khi phải thống nhất danh tính một kiểu dùng chung. Lỗi này cần được điều tra ở quan hệ loader và kiểu trong method descriptor; đổi tên biến hay ép kiểu không giải quyết nguyên nhân.

Nạp lớp riêng cũng không bỏ qua verification, quy tắc khởi tạo hay quyền truy cập của JPMS. Phần tiếp theo tập trung vào **ai có thể thấy lớp của ai** và những điều kiện để việc trao đổi kiểu qua ranh giới loader hợp lệ.

## 3. Mô hình ủy quyền cha–con và khả năng nhìn thấy

Trong mô hình **parent delegation** mặc định của `ClassLoader.loadClass`, loader kiểm tra lớp đã được tải, sau đó thường nhờ cha tìm trước; nếu cha không tìm thấy, loader con mới gọi `findClass` để tự định nghĩa.

```text
Bootstrap loader              (java.base, ví dụ java.lang.String)
       ↑
Platform loader               (một số module nền tảng JDK)
       ↑
Application/System loader     (classpath/module path ứng dụng)
       ↑
PluginLoader-A           PluginLoader-B
   (plugin A)              (plugin B)
```

Mũi tên chỉ hướng **yêu cầu lên cha**, không có nghĩa loader cha tự động truy cập được mọi lớp riêng của con.

- **Con thường nhìn thấy lớp của cha** do yêu cầu được ủy quyền lên trên.
- **Cha không tự nhìn thấy lớp chỉ do con định nghĩa**, vì cơ chế mặc định không tìm ngược xuống.
- **Hai loader anh em không tự nhìn thấy lớp riêng của nhau**. Chúng chỉ chia sẻ các lớp mà cả hai được cha chung trả về, hoặc thông qua cơ chế kết nối được thiết kế rõ.
- **Không phải mọi loader đều parent-first**. Container và plugin framework có thể thực hiện child-first hoặc quy tắc theo package. Phải xác minh loader thực tế thay vì mặc định mọi lớp đều tuân theo sơ đồ trên.

### Phân biệt ba loại “nhìn thấy”

1. **Visibility khi tải lớp**: loader đang dùng có thể tìm được định nghĩa không?
2. **Accessibility theo ngôn ngữ/JVM**: sau khi tìm được, `public`, `protected`, package-private, quan hệ kế thừa và quy tắc module cho phép truy cập không?
3. **Khả năng tương thích kiểu**: kiểu do loader nào định nghĩa? Hai `Class<?>` có thực sự là cùng một đối tượng `Class` không?

Ví dụ, một loader con có thể tải được một lớp `public` từ cha nhưng vẫn bị JPMS chặn truy cập do module chưa `exports` package, hoặc bị `opens` hạn chế truy cập phản chiếu sâu.

## 4. Runtime package, quyền truy cập và module

Trong JVM, hai lớp chỉ được coi là thuộc cùng một **runtime package** nếu tên package trùng **và defining loader tương ứng giống nhau**. Vì vậy, package-private không phải “lối tắt” để hai plugin truy cập internals của nhau chỉ vì chúng dùng cùng câu lệnh `package org.example;`.

Ví dụ, `pluginA.internal.Helper` và `pluginA.internal.Entry` có cùng package text nhưng do hai loader khác nhau định nghĩa: JVM không xem chúng là cùng runtime package cho mục đích truy cập package-private. Các lỗi có thể biểu hiện dưới dạng `IllegalAccessError` khi bytecode đã liên kết tới thành viên không hợp lệ.

Java Platform Module System (JPMS, Java 9+) bổ sung quy tắc **readability, exports và opens**:

- **Reads**: module nào có quan hệ đọc module nào.
- **Exports**: package nào công khai cho truy cập ở mức biên dịch và runtime.
- **Opens**: package nào cho phép truy cập phản chiếu sâu trong phạm vi cấu hình mở.
- **Class loader**: vẫn đảm trách nạp lớp và tham gia xác định danh tính kiểu; `ModuleLayer` không đồng nghĩa với class loader, vì cách ánh xạ module → loader có thể khác nhau.

Quyền truy cập được quyết định bởi **cả** ranh giới loader, modifiers và quy tắc module có liên quan. Đừng cố “sửa” lỗi module bằng cách chỉ thêm đường dẫn JAR vào classpath, hoặc sửa lỗi `ClassCastException` giữa hai loader bằng `--add-opens`.

## 5. Thí nghiệm Java 17+: hai loader, một tên lớp, hai kiểu

Chương trình dưới đây tự tạo mã nguồn `example.Payload`, dùng compiler của **JDK** biên dịch, sau đó tạo hai `URLClassLoader` độc lập có cùng thư mục lớp. Nó minh họa chính xác sự khác biệt giữa “cùng binary name” và “cùng type identity”.

Lưu mã vào `LoaderNamespaceDemo.java`:

```java
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public class LoaderNamespaceDemo {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("loader-namespace-");
        Path source = dir.resolve("example/Payload.java");
        Path bytecode = dir.resolve("example/Payload.class");

        try {
            Files.createDirectories(source.getParent());
            Files.writeString(source,
                    "package example; public class Payload { }");

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new IllegalStateException("Run with a JDK, not a JRE");
            }
            int exit = compiler.run(
                    null, null, null,
                    "-d", dir.toString(), source.toString());
            if (exit != 0) {
                throw new IllegalStateException("Compilation failed: " + exit);
            }

            URL location = dir.toUri().toURL();
            ClassLoader platform = ClassLoader.getPlatformClassLoader();

            try (URLClassLoader left =
                         new URLClassLoader(new URL[]{location}, platform);
                 URLClassLoader right =
                         new URLClassLoader(new URL[]{location}, platform)) {

                Class<?> leftType = Class.forName(
                        "example.Payload", true, left);
                Class<?> rightType = Class.forName(
                        "example.Payload", true, right);
                Object leftObject =
                        leftType.getDeclaredConstructor().newInstance();

                System.out.println(leftType.getName()
                        .equals(rightType.getName()));
                System.out.println(leftType == rightType);
                System.out.println(rightType.isInstance(leftObject));
                System.out.println(leftType.getClassLoader() == left);
                System.out.println(String.class.getClassLoader() == null);
            }
        } finally {
            Files.deleteIfExists(source);
            Files.deleteIfExists(bytecode);
            Files.deleteIfExists(dir.resolve("example"));
            Files.deleteIfExists(dir);
        }
    }
}
```

Chạy trong thư mục chứa file bằng JDK 17 trở lên:

```bash
javac LoaderNamespaceDemo.java
java LoaderNamespaceDemo
```

Stdout khi biên dịch/chạy thành công:

```text
true
false
false
true
true
```

Giải thích theo thứ tự:

1. `true`: hai lớp có cùng **tên nhị phân**.
2. `false`: hai biến `Class<?>` không tham chiếu cùng định nghĩa lớp.
3. `false`: đối tượng do `leftType` tạo **không phải** thể hiện của `rightType`, nên `rightType.cast(leftObject)` sẽ ném `ClassCastException`.
4. `true`: `left` chính là defining loader của bản `Payload` bên trái.
5. `true`: `String` thuộc lớp nền JDK, được định nghĩa bởi bootstrap loader, nên API trả về `null`.

Chương trình dùng `platform` làm cha để lớp `example.Payload` không bị application loader “nhận trước”. Nếu hai `URLClassLoader` có chung cha **và cha đã định nghĩa `Payload`**, cơ chế parent-first mặc định sẽ khiến chúng cùng nhận một `Class` từ cha; kết quả so sánh có thể khác. Đó chính là lý do thiết kế vị trí đặt JAR quan trọng.

## 6. Cô lập phụ thuộc trong kiến trúc plugin

Giả sử ứng dụng host cần chạy Plugin A phụ thuộc `json-lib 1.x` và Plugin B phụ thuộc `json-lib 2.x`. Nếu đưa cả hai JAR vào một classpath phẳng mà chúng có cùng binary name, thứ tự classpath và cơ chế nạp có thể khiến chỉ một phiên bản được chọn, tạo lỗi `NoSuchMethodError` hoặc hành vi sai.

Một kiến trúc có chủ đích:

```text
Host / parent loader
  ├─ com.acme.spi.Plugin          (giao diện dùng chung)
  ├─ com.acme.spi.Request         (kiểu dữ liệu giao tiếp chung)
  └─ thư viện ổn định dùng chung
         ↑                       ↑
  Plugin A loader            Plugin B loader
    ├─ impl.APlugin            ├─ impl.BPlugin
    └─ json-lib 1.x            └─ json-lib 2.x
```

**Nguyên tắc thiết kế ranh giới:**

- **API/SPI dùng chung đặt ở loader cha**: host và các plugin phải tham chiếu **cùng một** `com.acme.spi.Plugin`. Nếu plugin tự đóng gói và định nghĩa lại interface đó, việc ép kiểu sang interface của host có thể thất bại dù tên giống nhau.
- **Thư viện cần cô lập đặt trong loader riêng**: chỉ phụ thuộc thật sự tách biệt mới định nghĩa ở loader riêng. Nếu cùng tên thư viện đã có trong cha và loader của plugin vẫn parent-first, plugin có thể vô tình dùng bản của cha.
- **Chiến lược child-first chỉ nên chọn lọc**: một số framework ưu tiên JAR của plugin cho package riêng, nhưng vẫn ủy quyền lớp JDK và API/SPI chung cho cha. Viết child-first tùy tiện có thể gây `LinkageError`, phá type identity hoặc làm sai giả định bảo mật.
- **Trao đổi qua hợp đồng ổn định**: interface chung, kiểu DTO chung, primitive, `String` hoặc dữ liệu JSON trung gian. Không truyền trực tiếp `json-lib.internal.Node` của plugin A sang plugin B nếu hai bên định nghĩa lớp đó độc lập.
- **Quản lý lifecycle**: đóng `URLClassLoader` giải phóng tài nguyên như JAR handle, nhưng không bảo đảm dỡ lớp ngay. Để GC thu hồi loader, phải loại bỏ tham chiếu còn giữ lớp, instance, thread, `ThreadLocal`, TCCL, cache và callback thuộc plugin.

### Parent-first và child-first khi phụ thuộc trùng tên

| Cách nạp | Lợi ích | Giới hạn |
| --- | --- | --- |
| Parent-first | Giữ nhất quán JDK và API dùng chung; dễ dự đoán | Không cô lập được một thư viện riêng nếu phiên bản cùng tên đã được cha nạp |
| Child-first chọn lọc | Cho plugin ưu tiên phiên bản thư viện nội bộ | Phải loại trừ các package API chia sẻ và bảo vệ những lớp thuộc JDK; khó chẩn đoán khi rò kiểu |
| Mỗi plugin có loader riêng, chỉ giao tiếp qua SPI cha | Tách thư viện triển khai và giữ hợp đồng chung | Phải quản lý tài nguyên, TCCL, serialization và chu kỳ sống plugin |

**Ví dụ qua ranh giới:** host gọi `Plugin.execute(Request)` thông qua một interface do cha định nghĩa. Bên trong plugin, nó có thể dùng một JSON parser riêng, nhưng trước khi trả kết quả cho host, dữ liệu phải được chuyển thành kiểu kết quả chung được cha định nghĩa. Khi thiết kế API, tránh để type riêng của plugin xuất hiện trong public method signature của hợp đồng chia sẻ.

## 7. Context ClassLoader và ServiceLoader

Một thư viện nằm ở loader cha có khi cần khám phá provider do loader con định nghĩa. Vì cha không tự tìm xuống con, Java có **Thread Context ClassLoader (TCCL)**:

```java
ClassLoader previous =
        Thread.currentThread().getContextClassLoader();
try {
    Thread.currentThread().setContextClassLoader(pluginLoader);
    // Gọi thư viện cần tìm provider trong ngữ cảnh plugin.
} finally {
    Thread.currentThread().setContextClassLoader(previous);
}
```

Đây là **đoạn minh họa**, giả định biến `pluginLoader` đã được tạo bởi framework; nó không phải chương trình độc lập. Phải khôi phục TCCL trong `finally`, đặc biệt với thread pool, nếu không plugin cũ có thể bị giữ lại hoặc ảnh hưởng các tác vụ sau.

`ServiceLoader` có thể khám phá provider qua loader chỉ định, ví dụ `ServiceLoader.load(Plugin.class, pluginLoader)`. Để thành công, provider phải triển khai **chính xác** interface `Plugin` mà phía host đang dùng; metadata provider (như `META-INF/services/...` trên classpath truyền thống hoặc `provides ... with ...` trong module) cũng phải đúng. Một loader tìm được provider vẫn chưa chắc provider có thể ép kiểu sang SPI của host nếu SPI bị định nghĩa lại.

Đừng nhầm `Thread.currentThread().getContextClassLoader()` với loader đã định nghĩa chính lớp đang chạy: chúng có thể khác nhau. TCCL là cơ chế lựa chọn loader theo ngữ cảnh luồng, không thay đổi danh tính của lớp đã được định nghĩa.

## 8. Những lỗi thường gặp và cách lần ra ranh giới

| Triệu chứng | Nguyên nhân cần kiểm tra | Cách điều tra |
| --- | --- | --- |
| `ClassNotFoundException` | Loader được yêu cầu không tìm thấy tên lớp | In tên lớp, loader đầu vào, URL/JAR và sơ đồ cha |
| `NoClassDefFoundError` | Thiếu phụ thuộc ở lúc resolution hoặc lỗi trạng thái khởi tạo sau lần thất bại trước | Đọc toàn bộ `Caused by`, xác minh phụ thuộc và lỗi khởi tạo sớm hơn |
| `ClassCastException` có cùng tên lớp ở hai vế | Hai loader định nghĩa cùng binary name | So sánh `a.getClass() == expectedType` và in defining loader của từng phía |
| `NoSuchMethodError` / `NoSuchFieldError` | Version/API thực tế được nạp không khớp bytecode bên gọi | Xác định nguồn JAR và phiên bản **được nạp thực tế** |
| `LinkageError: loader constraint violation` | JVM thấy các định nghĩa không tương thích tại ranh giới liên kết kiểu | Xem kiểu trong method descriptor và loader của mỗi thành phần |
| `IllegalAccessError` | Quyền truy cập bytecode/runtime package/module không hợp lệ | Kiểm tra modifier, defining loader, `exports` và cấu hình module |
| `ServiceConfigurationError` | Provider không tìm được, không khởi tạo được hoặc không phải subtype của service dùng chung | Kiểm tra metadata provider, classpath/module path và danh tính SPI |
| `InaccessibleObjectException` | Truy cập phản chiếu sâu bị module encapsulation chặn | Kiểm tra `opens`, hướng truy cập và API thay thế hợp lệ |

**Ghi nhớ phân biệt:** `ClassNotFoundException` là ngoại lệ checked thường gặp khi gọi API nạp lớp bằng tên; `NoClassDefFoundError` là lỗi liên kết/khởi tạo khi JVM không thể dùng một định nghĩa được kỳ vọng, không nên tự động coi chúng là cùng một vấn đề.

### Quy trình chẩn đoán ngắn gọn

1. Ghi lại **binary name**, class nào đang gọi, và toàn bộ exception chain.
2. In `SomeType.class.getClassLoader()`; với object động, in `obj.getClass().getClassLoader()`. Kiểm tra hai phía biên API.
3. Xem vị trí bytecode được nạp; với lớp có code source khả dụng, có thể dùng `clazz.getProtectionDomain().getCodeSource()` (giá trị có thể `null`).
4. Với JDK 17+, chạy `java -Xlog:class+load=info ...` để theo dõi hoạt động nạp lớp. Tùy cách ghi log, có thể cần kết hợp với thông tin loader trong ứng dụng để xác nhận danh tính.
5. Nếu tiến trình cho phép attach, `jcmd <pid> VM.classloaders` cung cấp cái nhìn về hệ thống loader; danh sách lệnh có thể kiểm tra bằng `jcmd <pid> help`.
6. Đối chiếu classpath/module path, quan hệ cha, quy tắc delegation và tập JAR thực sự được gắn vào từng loader.
7. Với sự cố chỉ xảy ra sau redeploy/plugin reload, rà soát tham chiếu giữ loader cũ: thread sống lâu, scheduler, callback, static registry, cache, TCCL hoặc `ThreadLocal`.

Một phương pháp kiểm tra ngay trong Java:

```java
static void describe(Class<?> type) {
    System.out.println("class=" + type.getName());
    System.out.println("loader=" + type.getClassLoader());
    System.out.println("module=" + type.getModule().getName());
}
```

Đoạn trên là **phương thức hỗ trợ**, đặt trong class của ứng dụng để gọi; `getModule().getName()` có thể trả `null` với unnamed module. `toString()` của loader hữu ích cho quan sát nhưng không nên coi là định danh bất biến hoặc duy nhất.

## 9. Những ngộ nhận dễ dẫn đến thiết kế sai

- **“Cùng FQN thì cast được.”** Sai khi defining loader khác nhau. FQN chỉ là một nửa danh tính kiểu.
- **“Loader con có thể tải mọi lớp của loader anh em.”** Không đúng với delegation mặc định; cần kênh tìm kiếm/layer/cầu nối riêng.
- **“Tạo URLClassLoader là cô lập mọi dependency.”** Không đúng nếu thư viện đã nằm ở cha và parent-first trả về bản của cha.
- **“Package name giống là có package-private access.”** Runtime package còn phụ thuộc defining loader.
- **“Đổi TCCL đổi class của object hiện tại.”** TCCL ảnh hưởng cách một số API tìm lớp/provider về sau; không định nghĩa lại lớp đã có.
- **“Dùng reflection là bỏ qua mọi ranh giới.”** Reflection vẫn chịu kiểm tra quyền truy cập và module encapsulation.
- **“Close loader nghĩa là unload toàn bộ class.”** JVM chỉ có thể thu hồi lớp/loader khi các điều kiện về reachability và lifecycle được thỏa mãn.
- **“JPMS thay thế ClassLoader.”** Module là cơ chế tổ chức/đóng gói, còn loader vẫn thực hiện loading và type identity.

## 10. Áp dụng: thiết kế hệ plugin không xung đột phiên bản

Trước khi triển khai, lần lượt trả lời:

1. **Thứ gì bắt buộc chia sẻ?** Xác định một bản duy nhất của các interface, DTO và exception trong API/SPI phía host.
2. **Thứ gì phải cô lập?** Xác định danh sách thư viện có thể khác phiên bản ở từng plugin; tránh cho thư viện đó lộ qua public API.
3. **Loader graph ra sao?** Vẽ rõ quan hệ cha–con, thư mục/JAR của mỗi loader, và package nào parent-first hay child-first.
4. **Ai chịu trách nhiệm khám phá provider?** Chỉ định loader cho `ServiceLoader` hoặc TCCL tại đúng điểm gọi và khôi phục TCCL.
5. **Khi plugin dừng thì điều gì được giải phóng?** Đóng tài nguyên, ngắt thread, unregister hook, xóa cache/ThreadLocal và tham chiếu đến object/class/loader.
6. **Kiểm thử những va chạm nào?** Hai plugin dùng cùng tên lớp với bytecode khác; hai phiên bản JSON lib; cast qua SPI; reload plugin; thiếu class ở thời điểm resolution.

### Tự kiểm tra kiến thức

**Câu 1.** Hai `URLClassLoader` khác nhau đều load cùng đường dẫn `example.Payload.class`. Có chắc hai `Class` giống nhau không?  
**Trả lời:** Không. Nếu mỗi loader tự định nghĩa, đó là hai kiểu; nếu cả hai ủy quyền cho cùng defining loader ở cha, có thể là một kiểu.

**Câu 2.** Plugin A báo `ClassCastException: com.acme.spi.Plugin cannot be cast to com.acme.spi.Plugin`. Nên kiểm tra gì đầu tiên?  
**Trả lời:** Danh tính `Plugin.class` ở host và phía plugin, đặc biệt việc plugin có đóng gói/định nghĩa lại SPI thay vì ủy quyền cho cha hay không.

**Câu 3.** Plugin B phải chạy phiên bản `json-lib` khác host, nhưng vẫn nạp bản host. Vì sao?  
**Trả lời:** Loader B dùng parent-first, nên cha đã tìm thấy lớp `json-lib` trước. Cần thiết kế tách phụ thuộc với delegation chọn lọc, tránh phá API/SPI dùng chung.

**Câu 4.** Một thư viện chung tìm không thấy provider đặt trong plugin, mặc dù plugin nhìn thấy thư viện đó. Vì sao?  
**Trả lời:** Quan hệ nhìn thấy một chiều: loader cha của thư viện không tự tìm xuống loader con của plugin. Kiểm tra loader được truyền cho `ServiceLoader` hoặc TCCL tại lúc discovery.

## Kết luận

Cách tư duy đúng là vẽ rõ **ai định nghĩa lớp nào**, **loader nào có thể yêu cầu lớp từ đâu**, và **kiểu nào phải chung một danh tính ở ranh giới giao tiếp**. Khi ba điều này nhất quán, Java ClassLoader có thể hỗ trợ plugin và nhiều phiên bản phụ thuộc an toàn hơn; khi chúng bị trộn lẫn, lỗi thường chỉ lộ ra ở runtime dưới dạng cast, linkage, access hoặc service discovery failures.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-006">Một ví dụ xuyên suốt: ứng dụng chủ và plugin</a>

<details>
<summary>Xem chi tiết</summary>

# Một ví dụ xuyên suốt: ứng dụng chủ và plugin trong Java ClassLoader

## 1. Bài toán và điều cần quan sát

Giả sử một ứng dụng **host** muốn hỗ trợ nhiều tính năng mở rộng (plugin). Host biết một **hợp đồng Java** chung, nhưng không cần biết trước tên lớp triển khai hoặc biên dịch lại mỗi khi có plugin mới. Mỗi plugin được đóng gói trong một tệp JAR độc lập và được nạp khi host chạy.

Bài này dựng một ví dụ Java 17+ có thể chạy từ đầu đến cuối, qua đó trả lời các câu hỏi:

- Host và plugin nhìn thấy nhau qua interface nào?
- Ai nạp lớp API, ai nạp lớp triển khai của plugin?
- Vì sao cùng tên lớp chưa chắc là cùng kiểu Java?
- `ServiceLoader` tìm plugin qua metadata của JAR như thế nào?
- Khi thay JAR hoặc đóng `URLClassLoader`, điều gì thực sự xảy ra?

Mục tiêu là hiểu **đường đi của lớp và hợp đồng giữa host với plugin**. Đây là ví dụ plugin chạy trong **cùng JVM**, không phải sandbox bảo mật và không tạo tiến trình riêng.

## 2. Sơ đồ kiến trúc và ranh giới nạp lớp

~~~text
JVM
├── Application ClassLoader (cha)
│   ├── demo.api.GreetingPlugin        ← API chung, host sở hữu
│   └── demo.host.HostApp              ← chương trình host
│
└── URLClassLoader (con, parent = loader của GreetingPlugin)
    └── hello-plugin.jar
        ├── demo.plugins.HelloPlugin   ← lớp triển khai
        └── META-INF/services/demo.api.GreetingPlugin
~~~

`HostApp` tạo một `URLClassLoader` với **parent** là ClassLoader đã nạp `GreetingPlugin`. Trong hành vi ủy quyền thông thường của `URLClassLoader`, khi plugin cần `demo.api.GreetingPlugin`, loader của plugin hỏi cha trước. Vì vậy cả host và plugin dùng chung **một** đối tượng `Class<?>` của interface.

Ngược lại, `HelloPlugin` chỉ có trong JAR của plugin, không có trên classpath khởi động của host. Khi cần triển khai này, ClassLoader con tìm được nó trong JAR.

Luồng phụ thuộc:

~~~text
host source ──depends on──> api source
plugin source ─depends on──> api source
host runtime ──loads───────> plugin JAR bằng URLClassLoader
~~~

**Quy tắc thiết kế:** API chung thuộc phía host/parent; plugin JAR chỉ mang phần triển khai và tệp khai báo dịch vụ. Nếu mỗi bên cố dùng một bản API do loader riêng định nghĩa, việc ép kiểu có thể thất bại, ngay cả khi hai class có cùng tên đầy đủ.

## 3. Cấu trúc thư mục

Tạo dự án nhỏ sau; các thư mục `build/` và `dist/` sẽ được sinh bằng lệnh biên dịch ở phần 5:

~~~text
host-plugin-demo/
├── api/
│   └── demo/api/GreetingPlugin.java
├── host/
│   └── demo/host/HostApp.java
├── plugin/
│   └── demo/plugins/HelloPlugin.java
└── plugin-resources/
    └── META-INF/services/demo.api.GreetingPlugin
~~~

### 3.1. API chung: `api/demo/api/GreetingPlugin.java`

~~~java
package demo.api;

public interface GreetingPlugin {
    String id();

    String greet(String name);
}
~~~

`GreetingPlugin` là hợp đồng ổn định. Host chỉ gọi `id()` và `greet(String)` thông qua interface này, không `import` hoặc khởi tạo trực tiếp `HelloPlugin`.

### 3.2. Plugin: `plugin/demo/plugins/HelloPlugin.java`

~~~java
package demo.plugins;

import demo.api.GreetingPlugin;

public final class HelloPlugin implements GreetingPlugin {
    @Override
    public String id() {
        return "hello";
    }

    @Override
    public String greet(String name) {
        return "Hello, " + name + "!";
    }
}
~~~

Lớp triển khai có `public` constructor không đối số mặc định (vì không tự khai báo constructor), đáp ứng cách khởi tạo provider dựa trên classpath của `ServiceLoader`. Nó tham chiếu interface API lúc biên dịch, nhưng mã bytecode của API **không cần được đóng gói lại** trong plugin JAR.

### 3.3. Đăng ký provider bằng tài nguyên của JAR

Tạo tệp văn bản **`plugin-resources/META-INF/services/demo.api.GreetingPlugin`** với đúng một dòng:

~~~text
demo.plugins.HelloPlugin
~~~

Tên tệp phải khớp **tên đầy đủ của interface**; mỗi dòng hữu hiệu trong tệp là tên đầy đủ của một provider. Đây **không phải** tệp Java, và phải được đưa vào JAR đúng vị trí `META-INF/services/`. Nếu thiếu hoặc viết sai đường dẫn, `ServiceLoader` thường không tìm thấy provider, dù `HelloPlugin.class` vẫn có trong JAR.

### 3.4. Host: `host/demo/host/HostApp.java`

~~~java
package demo.host;

import demo.api.GreetingPlugin;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceLoader;

public final class HostApp {
    private HostApp() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: HostApp <path-to-plugin-jar>");
        }

        Path pluginJar = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(pluginJar)) {
            throw new IllegalArgumentException("Plugin JAR not found: " + pluginJar);
        }

        // API chung đã được nạp từ classpath của ứng dụng chủ.
        ClassLoader parent = GreetingPlugin.class.getClassLoader();
        URL[] pluginUrls = {pluginJar.toUri().toURL()};

        // URLClassLoader chỉ sở hữu việc tìm bytecode/resources của plugin.
        try (URLClassLoader pluginLoader = new URLClassLoader(pluginUrls, parent)) {
            Class<?> apiSeenByPlugin =
                    pluginLoader.loadClass("demo.api.GreetingPlugin");

            System.out.println(
                    "Shared API: " + (apiSeenByPlugin == GreetingPlugin.class));

            // Chủ động truyền loader chứa provider và META-INF/services.
            ServiceLoader<GreetingPlugin> services =
                    ServiceLoader.load(GreetingPlugin.class, pluginLoader);

            int count = 0;
            for (GreetingPlugin plugin : services) {
                count++;
                System.out.println("Plugin ID: " + plugin.id());
                System.out.println("Greeting: " + plugin.greet("Java"));
                System.out.println(
                        "Implementation from child: "
                                + (plugin.getClass().getClassLoader() == pluginLoader));
            }
            System.out.println("Providers: " + count);
        }
    }
}
~~~

Ba điểm cốt lõi trong mã host:

1. `GreetingPlugin.class.getClassLoader()` xác định loader đã định nghĩa API. Truyền nó làm **parent** để plugin dùng cùng kiểu giao tiếp.
2. `ServiceLoader.load(GreetingPlugin.class, pluginLoader)` yêu cầu tìm khai báo provider bằng loader của plugin. `ServiceLoader` thường chỉ thực sự tra cứu và khởi tạo provider khi bắt đầu **duyệt**.
3. `try (URLClassLoader ...)` gọi `close()` sau khi dùng, giải phóng tài nguyên mà loader nắm giữ (như handle JAR); nó **không** bắt JVM lập tức dỡ các lớp đã nạp.

## 4. Phân biệt build-time classpath và runtime classpath

| Giai đoạn | Classpath | Mục đích |
|---|---|---|
| Biên dịch API | Không cần thư viện riêng | Tạo `GreetingPlugin.class` |
| Biên dịch host | `build/api` | Host nhận biết interface |
| Biên dịch plugin | `build/api` | Plugin implements đúng interface |
| Chạy host | `build/api` và `build/host` | **Không** đưa `hello-plugin.jar` lên classpath chính |
| Nạp plugin lúc chạy | URL của `hello-plugin.jar` | ClassLoader con tìm provider và class triển khai |

Host phải khởi chạy mà **không** cho plugin JAR vào classpath hệ thống. Nếu đặt JAR ở đó, Application ClassLoader có thể nạp luôn `HelloPlugin`, khiến thí nghiệm không còn chứng minh được ranh giới loader như thiết kế.

## 5. Biên dịch, đóng gói và chạy

Yêu cầu: **JDK 17 trở lên**, có `java`, `javac` và `jar` trên `PATH`. Chạy lệnh từ thư mục `host-plugin-demo/`. Dùng đúng các tệp đã tạo ở phần 3.

**Bash (Linux, macOS, Git Bash):**

~~~bash
mkdir -p build/api build/host build/plugin dist/plugins

javac -encoding UTF-8 -d build/api \
  api/demo/api/GreetingPlugin.java

javac -encoding UTF-8 -cp build/api -d build/host \
  host/demo/host/HostApp.java

javac -encoding UTF-8 -cp build/api -d build/plugin \
  plugin/demo/plugins/HelloPlugin.java

cp -R plugin-resources/META-INF build/plugin/

jar --create --file dist/plugins/hello-plugin.jar -C build/plugin .

java -cp "build/api:build/host" \
  demo.host.HostApp dist/plugins/hello-plugin.jar
~~~

**Windows PowerShell:**

~~~powershell
New-Item -ItemType Directory -Force build/api,build/host,build/plugin,dist/plugins | Out-Null

javac -encoding UTF-8 -d build/api api/demo/api/GreetingPlugin.java

javac -encoding UTF-8 -cp build/api -d build/host host/demo/host/HostApp.java

javac -encoding UTF-8 -cp build/api -d build/plugin plugin/demo/plugins/HelloPlugin.java

Copy-Item -Path plugin-resources/META-INF -Destination build/plugin -Recurse -Force

jar --create --file dist/plugins/hello-plugin.jar -C build/plugin .

java -cp "build/api;build/host" demo.host.HostApp dist/plugins/hello-plugin.jar
~~~

Ở bước đóng gói, `build/plugin/` chỉ chứa `demo/plugins/HelloPlugin.class` và `META-INF/services/demo.api.GreetingPlugin`. Có thể kiểm tra bằng:

~~~bash
jar --list --file dist/plugins/hello-plugin.jar
~~~

Đảm bảo có hai entry quan trọng: `demo/plugins/HelloPlugin.class` và `META-INF/services/demo.api.GreetingPlugin`. Lệnh `jar --list` có thể liệt kê thêm các thư mục và `META-INF/MANIFEST.MF`.

**Stdout chính xác của lệnh chạy host với đúng một plugin trên classpath đã nêu:**

~~~text
Shared API: true
Plugin ID: hello
Greeting: Hello, Java!
Implementation from child: true
Providers: 1
~~~

Không in định danh dạng `URLClassLoader@...` vì phần hậu tố phụ thuộc lần chạy. Các dòng `true` ở trên kiểm chứng trực tiếp điều cần học, thay vì dựa vào chuỗi `toString()` của ClassLoader.

## 6. Diễn tiến trong JVM, từ đầu đến cuối

1. JVM khởi động `demo.host.HostApp` bằng Application ClassLoader; `GreetingPlugin` cũng được định nghĩa từ `build/api` trên classpath này.
2. Host kiểm tra JAR tồn tại và tạo `URLClassLoader(pluginUrls, parent)`. Việc **khởi tạo loader không có nghĩa tất cả class trong JAR được nạp ngay**.
3. Host yêu cầu `pluginLoader.loadClass("demo.api.GreetingPlugin")`. Loader con ủy quyền cho parent. Parent đã có interface nên kết quả chính là `GreetingPlugin.class`: `Shared API: true`.
4. `ServiceLoader.load(...)` tạo đối tượng tra cứu dịch vụ. Khi vòng `for` bắt đầu, `ServiceLoader` tìm resource `META-INF/services/demo.api.GreetingPlugin` mà `pluginLoader` nhìn thấy.
5. Tệp dịch vụ chỉ tới `demo.plugins.HelloPlugin`. Loader con hỏi parent trước; parent không tìm thấy triển khai trên classpath host, nên loader con nạp `HelloPlugin.class` từ plugin JAR.
6. Khi liên kết lớp triển khai với interface, `demo.api.GreetingPlugin` được resolve qua cùng parent. Vì vậy `HelloPlugin` đúng là một `GreetingPlugin` mà host có thể gọi.
7. `ServiceLoader` khởi tạo provider và trả về đối tượng qua interface; host gọi `id()`, `greet("Java")`. `plugin.getClass().getClassLoader() == pluginLoader` là `true`.
8. Hết phạm vi `try`, loader được đóng. Việc tải lớp vào JVM, việc đóng nguồn JAR và việc dỡ lớp khi GC là **ba thời điểm khác nhau**.

Điểm thường gây nhầm lẫn: **parent delegation không có nghĩa mọi lớp đều do parent nạp**. Loader con nhờ parent tìm trước; khi parent không có `HelloPlugin`, loader con vẫn có thể tự định nghĩa nó từ JAR.

## 7. Bản sắc lớp: tên đầy đủ chưa đủ

Trong JVM, bản sắc kiểu tham chiếu được quyết định bởi **tên nhị phân của class/interface cùng với ClassLoader đã định nghĩa nó**. Chẳng hạn hai lớp đều có tên `demo.api.GreetingPlugin` nhưng được *hai loader độc lập định nghĩa* thì đó là hai kiểu khác nhau. Lỗi thường gặp là `ClassCastException` hoặc `ServiceConfigurationError: ... not a subtype` khi một bên cố dùng provider của bên kia.

Ví dụ hiện tại tránh xung đột nhờ:

- Chỉ **một bản API dùng ở runtime**: `GreetingPlugin.class` thuộc Application ClassLoader.
- Plugin loader sử dụng API này qua parent.
- Plugin JAR chứa triển khai và metadata; host có thể thao tác plugin dưới dạng interface chung.

Không nên kết luận rằng **cứ chép API vào hai JAR là lập tức có hai kiểu khác nhau**. Với `URLClassLoader` mặc định ủy quyền cho parent, ngay cả khi JAR con lỡ chứa API, parent vẫn thường được hỏi trước. Vấn đề xuất hiện khi **thực sự có hai loader tự định nghĩa API** (ví dụ hai classloader độc lập, hoặc một chiến lược child-first đặc biệt). Kiểu `String` hay kiểu thuộc `java.*` vẫn do cơ chế loader nền tảng quản lý theo cách riêng.

Một phép kiểm tra hữu ích là so sánh **bằng `==` trên `Class<?>`** giống `Shared API` trong chương trình; chỉ nhìn `getName()` không đủ.

## 8. Mở rộng ví dụ một cách có kiểm soát

**Thêm plugin thứ hai.** Tạo `ByePlugin implements GreetingPlugin`, đặt class của nó vào cùng plugin JAR và thêm tên đầy đủ `demo.plugins.ByePlugin` vào một dòng khác của tệp `META-INF/services/demo.api.GreetingPlugin`. Nếu cả hai provider có thể khởi tạo và tuân thủ interface, vòng lặp sẽ duyệt hai provider. Thứ tự phụ thuộc thứ tự khai báo/tìm kiếm provider; code nghiệp vụ không nên tự suy diễn ưu tiên từ thứ tự ngẫu nhiên của các JAR.

**Thay phiên bản plugin.** Biên dịch JAR mới, tạo **ClassLoader mới** trỏ vào JAR mới rồi tra cứu provider từ loader mới. Đối tượng và class cũ không tự biến thành phiên bản mới. Để có cơ hội thu hồi loader cũ, host phải thôi giữ các tham chiếu đến object/class/loader cũ; đồng thời plugin cần dừng thread, hủy đăng ký listener, đóng tài nguyên và dọn `ThreadLocal` hoặc cache có thể giữ các tham chiếu đó. `close()` đơn lẻ không bảo đảm class unloading.

**Plugin cần thư viện phụ thuộc.** Nếu thư viện không có trong parent, thêm JAR phụ thuộc vào `URL[]` của loader plugin (hoặc đóng gói đúng cách với kiểm soát xung đột). Đừng mặc định các plugin có thể tự chia sẻ mọi phiên bản thư viện: parent-first có thể chọn bản từ host; ngược lại các loader riêng có thể tạo ra những bản class không tương thích.

**Tài nguyên và metadata.** ClassLoader không chỉ tìm `.class` mà còn cung cấp resource qua `getResource(s)`. `ServiceLoader` trên classpath sử dụng tệp cấu hình `META-INF/services/...` để phát hiện provider. Vì vậy “class có trong JAR” và “service được phát hiện” là hai điều kiện khác nhau.

**Thread Context ClassLoader (TCCL).** Một số framework gọi `ServiceLoader.load(GreetingPlugin.class)` theo overload sử dụng context loader của thread. Trong tình huống đó provider có thể không được tìm thấy nếu context loader vẫn là loader của host. Ví dụ chủ động truyền `pluginLoader` để tránh phụ thuộc ngầm vào TCCL. Khi tích hợp thư viện thật có thể cần đặt lại context loader tạm thời, luôn khôi phục giá trị cũ trong `finally` và hiểu ảnh hưởng đến mã chạy trên cùng thread.

## 9. Lỗi phổ biến và cách xác minh

| Triệu chứng | Nguyên nhân thường gặp | Cách kiểm tra / xử lý |
|---|---|---|
| `Providers: 0` | Thiếu `META-INF/services/...`, sai FQCN, hoặc JAR không nằm trong loader đang truyền | `jar --list --file ...`; đọc đúng nội dung service descriptor; xác minh URL của loader |
| `ServiceConfigurationError` | Tên provider sai, class không public/không thể tạo instance, dependency thiếu, hoặc provider không đúng interface | Xem `cause` và stack trace, biên dịch lại với cùng API; kiểm tra constructor và classpath |
| `NoClassDefFoundError` | Provider tham chiếu class phụ thuộc không nhìn thấy từ loader plugin | Thêm dependency vào URL loader hoặc cung cấp qua parent theo thiết kế |
| `ClassCastException` hoặc “not a subtype” | Host/provider dùng hai `Class<?>` API do loader khác nhau định nghĩa | In `Class.getClassLoader()`; so sánh `Class<?>` bằng `==`; chia sẻ API qua parent |
| `Implementation from child: false` | Plugin class bị parent nạp trước, thường do plugin JAR ở classpath host | Chạy lại chỉ với `build/api;build/host` (Windows) hoặc `build/api:build/host` (Bash) |
| Đổi file JAR nhưng thấy hành vi cũ | Object/class vẫn thuộc loader cũ; plugin chưa được nạp lại bằng loader mới | Tạo loader/provider mới, thay tham chiếu nghiệp vụ có kiểm soát rồi dọn bản cũ |

**Hai lưu ý khi chẩn đoán:** `ClassNotFoundException` là ngoại lệ checked thường phát sinh từ yêu cầu tìm class; `NoClassDefFoundError` có thể xuất hiện khi JVM liên kết/khởi tạo class và không tìm được dependency dù class gốc đã được tìm thấy. Đồng thời, bản thân `ServiceLoader` có thể bọc lỗi provider thành `ServiceConfigurationError`; nên đọc stack trace gốc thay vì suy đoán chỉ từ số lượng provider.

## 10. Phạm vi an toàn và quyết định thiết kế

- Một JAR nạp bằng `URLClassLoader` chạy **mã thực trong cùng tiến trình và cùng quyền hệ điều hành của host**. ClassLoader là cơ chế phân giải và cách ly tên lớp ở mức JVM, **không phải ranh giới bảo mật** cho mã không tin cậy. Plugin chưa được tin cậy nên thực thi trong tiến trình/container tách biệt với cơ chế kiểm soát phù hợp.
- API nên ổn định: thay chữ ký của `GreetingPlugin` có thể làm plugin cũ không còn tương thích nhị phân. Có thể thêm version/khả năng tương thích của plugin và kiểm tra trước khi kích hoạt.
- Quản lý vòng đời nên là trách nhiệm của host: nạp, khởi tạo, kích hoạt, dừng, đóng tài nguyên và gỡ tham chiếu. Ví dụ tối giản chỉ minh họa **load → call → close**.
- Ví dụ dùng **classpath/unnamed modules**. Hệ thống plugin viết bằng JPMS named modules có quy tắc `module-info.java` và `ModuleLayer` riêng; không thể mặc nhiên coi `URLClassLoader` là cách xử lý đầy đủ mọi plugin JPMS.

## 11. Tự kiểm tra sau khi chạy

1. Bỏ file `META-INF/services/demo.api.GreetingPlugin` khỏi JAR, đóng gói lại rồi chạy: host vẫn tìm được API nhưng không discover provider; lý giải vì sao `Providers: 0`.
2. Khôi phục service descriptor, giữ nguyên lệnh chạy host không có plugin JAR trên classpath chính: xác minh `Shared API: true` và `Implementation from child: true`. Giải thích **hai kết quả khác nhau** bằng quy tắc ủy quyền cha.
3. Sửa `HelloPlugin.greet()` thành một chuỗi khác, chỉ biên dịch và đóng gói lại plugin, rồi chạy lại host: hợp đồng và mã host không đổi. Hãy chỉ ra phần nào được tái sử dụng và vì sao JVM mới hoặc ClassLoader mới nhìn thấy bytecode mới.

**Ghi nhớ:** Host và plugin giao tiếp qua **cùng một API do parent định nghĩa**, plugin triển khai được nạp qua **loader con**, `ServiceLoader` đọc metadata trong JAR để tạo provider; việc đóng loader và cập nhật phiên bản phải được quản lý như vòng đời của một thành phần động.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-007">Kiến thức tiên quyết và trình tự học đề xuất</a>

<details>
<summary>Xem chi tiết</summary>

# Kiến thức tiên quyết và trình tự học đề xuất — Java Core: ClassLoader

## Mục tiêu học tập

Trước khi học cơ chế `ClassLoader`, cần hiểu một lớp Java đi từ mã nguồn đến runtime như thế nào. `javac` biên dịch mã thành bytecode; JVM dùng các loader để tìm và định nghĩa lớp khi chạy; sau đó lớp có thể được liên kết và khởi tạo. Việc phân biệt các giai đoạn này giúp giải thích các lỗi khi chạy ứng dụng, thư viện, plugin và framework.

Hoàn thành lộ trình này, bạn cần có khả năng:

- Phân biệt mã nguồn `.java`, bytecode `.class`, JAR, classpath, module path, package và tên nhị phân của lớp.
- Giải thích bằng ví dụ sự khác nhau giữa **loading**, **linking** và **initialization**; nhận ra một `Class<?>` đã tìm được không có nghĩa khối `static` đã chạy.
- Nhận diện ba loader built-in trên Java 9+: **bootstrap**, **platform**, **application/system**, và mô tả thứ tự ủy quyền mặc định.
- Hiểu rằng một kiểu lúc chạy được phân biệt bằng **binary name + defining class loader**, nền tảng cho lỗi ép kiểu trong hệ thống plugin.
- Phân tích `ClassNotFoundException`, `NoClassDefFoundError` và lỗi khởi tạo trước khi quyết định sửa classpath hay thiết kế loader riêng.

Bài này tập trung vào **kiến thức cần học trước và thứ tự học**. Custom ClassLoader, module layer, cơ chế nạp song song và classloader của container được đặt sau phần nền tảng; chúng không phải điều kiện đầu vào.

## 1. Những kiến thức tiên quyết

### 1.1. Bắt buộc trước khi đi vào ClassLoader

| Kiến thức | Vì sao cần | Mức tự kiểm tra |
|---|---|---|
| Java: `class`, `interface`, `extends`, constructor, `static` | Đọc được cách lớp được định nghĩa và khi nào logic tĩnh chạy | Tạo được lớp có `main`, lớp lồng nhau và một `static` block |
| Package, `import`, tên lớp đầy đủ | Loader tìm theo **binary name**; `import` chỉ là tiện ích trong mã nguồn | Phân biệt `java.util.List` với `List`; nhận biết `Outer$Inner` là tên nhị phân của lớp lồng `Outer.Inner` |
| JDK, `javac`, `java` | Tránh nhầm nạp lớp với biên dịch | Tự biên dịch và chạy một chương trình ngoài IDE |
| Output `.class`, JAR và classpath | Hiểu JVM có thể tìm bytecode ở đâu | Biết truyền `-cp` khi chạy và chỉ ra classpath root cho lớp có package |
| JVM mức nhập môn, `Class<?>` và instance | Phân biệt metadata về kiểu với đối tượng của kiểu đó | Phân biệt `Demo.class` và `new Demo()` |
| `Exception`, `Error`, stack trace và `Caused by` | Đọc được dấu hiệu lỗi runtime | Chỉ ra được nguyên nhân sâu nhất của một stack trace đơn giản |

**Bài kiểm tra đầu vào:** tạo tệp `Hello.java` chứa `public class Hello` với `main`, chạy hai lệnh sau từ thư mục chứa tệp:

~~~sh
javac Hello.java
java Hello
~~~

Bạn phải biết `javac` tạo `Hello.class` và `java` khởi động JVM để thực thi lớp. Khi chuyển `Hello` vào `package example;`, bạn phải hiểu tên chạy là `example.Hello` và thư mục chứa `example/Hello.class` (theo cấu trúc package) phải thuộc classpath root, không chỉ là thư mục bất kỳ có tệp source.

### 1.2. Nên biết trước hoặc học ngay trong lộ trình

- **Reflection cơ bản:** `Class.forName`, `getClass()`, `getName()`, `getClassLoader()`. Chưa cần học hết reflection, chỉ cần hiểu `Class<?>` là đối tượng mô tả kiểu.
- **Phân biệt compile-time và runtime dependencies:** ứng dụng có thể biên dịch với một JAR nhưng chạy với JAR khác hoặc thiếu JAR.
- **Tính kế thừa và ép kiểu:** cần để hiểu tại sao các lớp cùng tên vẫn có thể không tương thích khi được định nghĩa bởi hai loader khác nhau.

### 1.3. Chưa cần học sâu ngay

`module-info.java`, `ModuleLayer`, thread context class loader, native JNI, bytecode instrumentation, hot reload, deadlock khi nạp lớp và `registerAsParallelCapable` là các phần nâng cao. Chúng có giá trị sau khi bạn hiểu loader mặc định và có nhu cầu cụ thể. ClassLoader không tự tạo sandbox bảo mật cho mã không tin cậy.

## 2. Sơ đồ thứ tự kiến thức

~~~text
Java class / package / static / error handling
                 |
                 v
javac / java / .class / JAR / classpath
                 |
                 v
JVM loading -> linking -> initialization
                 |
                 v
bootstrap / platform / application ClassLoader
                 |
                 v
parent delegation / Class.forName / Class<?>
                 |
                 v
class identity + lỗi liên quan đến class loading
                 |
                 v
custom loader / plugin / container / JPMS
~~~

Mỗi tầng giải thích vấn đề của tầng sau. Ví dụ, nếu chưa biết classpath trỏ đến đâu, bạn rất dễ nhầm một lỗi thiếu JAR với lỗi do implementation của `loadClass`.

## 3. Trình tự học đề xuất và điều kiện chuyển chặng

### Chặng 1 — Mã nguồn, bytecode và phạm vi tra cứu lớp

**Cần nắm:** `.java` là mã nguồn; `javac` tạo bytecode `.class`; `java` chạy ứng dụng trong JVM. Thư mục output, JAR và `-cp`/`--class-path` quyết định phạm vi lớp ứng dụng có thể tìm lúc chạy theo classpath. `import` không thêm dependency.

**Thực hành:** biên dịch một lớp đơn lẻ; sau đó tạo một lớp trong package. Dùng `javac -d out` (tạo thư mục `out` trước nếu cần) và chạy `java -cp out packageName.Main`. Quan sát cấu trúc `.class` tương ứng với package. Thử `javap -classpath out -c packageName.Main` để thấy file `.class` chứa thông tin bytecode mà JVM có thể xử lý.

**Tự kiểm tra:** vì sao có `import com.example.Service;` nhưng runtime vẫn có thể không tìm thấy `Service`? Vì sao `javac` thành công không bảo đảm `java` tìm được mọi dependency?

**Qua chặng khi:** tự xác định được binary name, classpath root và nơi tạo file `.class`.

### Chặng 2 — Phân biệt loading, linking và initialization

**Cần nắm:**

1. **Loading:** JVM dùng loader để tìm hoặc tạo định nghĩa lớp từ dữ liệu lớp; một đối tượng `Class` tương ứng có thể được sử dụng để biểu diễn kiểu.
2. **Linking:** bao gồm xác minh bytecode (`verification`), chuẩn bị (`preparation`) và giải quyết các tham chiếu biểu tượng (`resolution`). JVM có thể trì hoãn một phần resolution; sơ đồ thứ tự không có nghĩa toàn bộ thao tác diễn ra ngay lập tức.
3. **Initialization:** logic khởi tạo `static` của lớp được thực hiện theo các điều kiện do Java/JVM quy định. Nạp và liên kết một lớp không tự bảo đảm đã chạy khối `static`.

**Thực hành:** tạo một lớp có `static { System.out.println("initialized"); }`, sau đó thử lấy đối tượng `Class<?>` mà chưa yêu cầu initialization; dùng chương trình ở mục 4 để kiểm chứng. Thử nhận diện sự khác nhau giữa `X.class` và khởi tạo `X` bằng sử dụng tích cực.

**Tự kiểm tra:** tại sao “đã load” không đồng nghĩa “đã initialize”? Tại sao lỗi liên kết có thể chỉ xuất hiện khi một đường code thật sự được chạy?

**Qua chặng khi:** mô tả đúng ba giai đoạn và không khẳng định mọi resolution luôn xảy ra cùng một thời điểm.

### Chặng 3 — Các loader có sẵn và mô hình parent delegation

**Cần nắm:** từ Java 9 trở lên, runtime có ba lớp trách nhiệm loader chính:

- **Bootstrap class loader:** loader tích hợp trong JVM; API Java thường biểu diễn nó bằng `null`. Nó nạp những lớp cốt lõi như `java.lang.String`.
- **Platform class loader:** hỗ trợ nhiều lớp API nền tảng; lấy qua `ClassLoader.getPlatformClassLoader()`.
- **Application/System class loader:** thông thường nạp lớp ứng dụng trên classpath; lấy qua `ClassLoader.getSystemClassLoader()`. Môi trường nhúng có thể có cấu hình khác.

Phương thức `ClassLoader.loadClass(name, resolve)` **mặc định** kiểm tra lớp đã nạp, nhờ parent tìm, rồi mới gọi `findClass` nếu cần. Đây là hướng **parent-first**. Parent là quan hệ *ủy quyền nạp lớp*, khác quan hệ `extends` giữa các lớp Java. Custom loader hoặc môi trường plugin có thể đổi chiến lược; do đó parent-first không phải bất biến của mọi ClassLoader.

**Thực hành:** xem kết quả `String.class.getClassLoader()` và `Main.class.getClassLoader()`. Khi loader của `String` trả về `null`, kết luận đúng là API biểu diễn bootstrap, không phải “lớp chưa tồn tại”. Không so sánh chuỗi `toString()` của loader như một tiêu chuẩn cố định giữa các phiên bản JDK.

**Tự kiểm tra:** khi application loader tìm một lớp, trong implementation mặc định parent được hỏi lúc nào? Nếu parent không tìm thấy, phương thức nào thường đảm nhận việc định nghĩa lớp cục bộ?

**Qua chặng khi:** vẽ được luồng delegation và nêu đúng vai trò của bootstrap, platform, application.

### Chặng 4 — Quan sát ClassLoader và thời điểm khởi tạo

**Cần nắm:** `Class<?>` mô tả một kiểu runtime; `getClassLoader()` cho biết loader định nghĩa kiểu đó; `Class.forName(String, boolean, ClassLoader)` cho phép yêu cầu nạp theo binary name với cờ `initialize`. Ngược lại, overload `Class.forName(String)` yêu cầu khởi tạo lớp theo ngữ nghĩa của API. `ClassLoader.loadClass` mặc định không yêu cầu initialization.

**Thực hành:** chạy ví dụ Java hoàn chỉnh ở mục 4. Sau đó đổi `false` thành `true` trong lần gọi đầu và dự đoán stdout trước khi chạy.

**Tự kiểm tra:** dòng nào cho biết có đối tượng `Class`? Dòng nào cho biết `static` block đã chạy? Vì sao đây là hai quan sát khác nhau?

**Qua chặng khi:** dự đoán chính xác kết quả ví dụ và giải thích mỗi lời gọi.

### Chặng 5 — Định danh lớp runtime và ranh giới loader

**Cần nắm:** một lớp trong JVM được phân biệt bằng **cặp (binary name, defining loader)**. Hai loader khác nhau có thể định nghĩa hai lớp có cùng tên nhưng JVM vẫn coi đó là hai kiểu khác nhau. Loader *khởi xướng* yêu cầu tìm lớp có thể chỉ ủy quyền cho parent; nó không nhất thiết là *defining loader* của lớp được trả về.

**Bài tập tư duy:** hai plugin A và B đều có lớp tên `com.example.PluginApi` và mỗi plugin định nghĩa lớp bằng loader riêng. Vì sao một đối tượng được tạo theo `PluginApi` của A không bảo đảm ép kiểu được sang `PluginApi` của B? Một API chung do loader cha định nghĩa có thể giúp hai phía chia sẻ hợp đồng kiểu như thế nào?

**Tự kiểm tra:** chỉ nhìn `Class.getName()` giống nhau đã đủ biết hai `Class` đại diện cùng kiểu chưa? Phải kiểm tra thêm điều gì?

**Qua chặng khi:** giải thích được lỗi `ClassCastException` có liên quan đến loader mà không nhầm nó với cùng package hay cùng tên source.

### Chặng 6 — Chẩn đoán lỗi thật trong ứng dụng

| Dấu hiệu | Diễn giải phù hợp | Việc kiểm tra đầu tiên |
|---|---|---|
| `ClassNotFoundException` | API chủ động tìm lớp theo tên nhưng loader không tìm thấy; checked exception | Binary name, classpath/JAR, loader được truyền vào lời gọi |
| `NoClassDefFoundError` | JVM cần một định nghĩa lớp nhưng không tìm/không dùng được; cũng có thể xảy ra sau lần khởi tạo thất bại trước đó | Đọc `Caused by`, stack trace, classpath runtime và lỗi trước đó |
| `ExceptionInInitializerError` | Logic khởi tạo tĩnh xảy ra exception dẫn tới lỗi khởi tạo | Đọc cause trong `static` initializer, dependency liên quan |
| `ClassCastException` | Ép kiểu sai; trong hệ plugin có thể do các lớp trùng tên nhưng khác loader | Kiểm tra kiểu thực, defining loader và cây kế thừa |
| `NoSuchMethodError` / `NoSuchFieldError` | Thường là thay đổi binary/API version giữa compile và runtime | Kiểm tra version JAR được JVM thực sự dùng |

**Quy trình luyện chẩn đoán:** (1) đọc chính xác lớp và exception/error; (2) lần theo `Caused by`; (3) xác định classpath/module path và version thư viện runtime; (4) hỏi loader nào đang tra cứu/định nghĩa lớp; (5) thiết kế một phép thử nhỏ để xác minh giả thuyết. Khi phù hợp với HotSpot hiện đại, có thể dùng `-Xlog:class+load=info` để quan sát nạp lớp, nhưng không suy đoán nguyên nhân chỉ dựa trên số lượng log.

**Tự kiểm tra:** thiếu một JAR lúc chạy thường thể hiện thế nào so với một ngoại lệ do `static` initializer? Vì sao cần biết chính xác error/exception thay vì chỉ tìm từ “class” trong log?

**Qua chặng khi:** biết phân biệt lỗi tìm theo tên, lỗi lớp phụ thuộc và lỗi khởi tạo, đồng thời có phương pháp xác minh.

### Chặng 7 — Phần nâng cao sau khi đã vững nền tảng

Chỉ học sâu khi bạn có nhu cầu thực tế:

- **Custom ClassLoader:** `findClass`, `defineClass`, đọc bytecode và vòng đời của loader. Theo hướng dẫn của API, thường ưu tiên override `findClass` để giữ ủy quyền của `loadClass`; chỉ override `loadClass` khi chủ đích cần chiến lược khác.
- **Plugin và container:** loader riêng, API chung, cô lập dependency, chiến lược parent-first hoặc cách ưu tiên tìm kiếm khác; phân tích class identity trước khi sửa ép kiểu.
- **Thread context class loader:** cho framework và cơ chế service/provider cần tra cứu theo ngữ cảnh thread; phân biệt với `SomeType.class.getClassLoader()`.
- **Java Platform Module System (JPMS):** `module-info.java`, module path, readability, export/open, module layer. Không đồng nhất module path với classpath.
- **Resource lookup và môi trường thực:** `getResource`/`getResources`, JAR resources, dịch vụ tải động, custom launcher; đây là các trường hợp có thêm phạm vi và quy tắc tra cứu.
- **Concurrency, lỗi an toàn và thiết kế loader:** parallel-capable, locking/deadlock, kiểm tra dữ liệu bytecode và ranh giới bảo mật. Loader tùy chỉnh không tự biến mã không tin cậy thành an toàn.

## 4. Bài thực hành Java 17+ độc lập

Tạo tệp đúng tên **`ClassLoaderLearning.java`**:

~~~java
public class ClassLoaderLearning {
    static class Probe {
        static {
            System.out.println("Probe initialized");
        }
    }

    public static void main(String[] args)
            throws ClassNotFoundException {
        System.out.println("String uses bootstrap: "
                + (String.class.getClassLoader() == null));
        System.out.println("Main uses system loader: "
                + (ClassLoaderLearning.class.getClassLoader()
                        == ClassLoader.getSystemClassLoader()));

        ClassLoader loader = ClassLoaderLearning.class.getClassLoader();
        Class<?> probe = Class.forName(
                "ClassLoaderLearning$Probe", false, loader);
        System.out.println("Located: " + probe.getName());
        System.out.println("Before explicit initialization");

        Class.forName(probe.getName(), true, probe.getClassLoader());
        System.out.println("After explicit initialization");
    }
}
~~~

Biên dịch và chạy từ thư mục chứa tệp:

~~~sh
javac ClassLoaderLearning.java
java ClassLoaderLearning
~~~

Với lệnh gọi launcher JDK tiêu chuẩn ở trên, stdout là:

~~~text
String uses bootstrap: true
Main uses system loader: true
Located: ClassLoaderLearning$Probe
Before explicit initialization
Probe initialized
After explicit initialization
~~~

**Giải thích kết quả:**

1. `String` do bootstrap loader định nghĩa trong môi trường JDK thông thường, nên `getClassLoader() == null` là `true`; `null` không báo lỗi.
2. Với chương trình khởi chạy như trên, `ClassLoaderLearning` dùng system/application loader. Đừng áp đặt kết quả này cho mọi môi trường nhúng/loader được tùy chỉnh.
3. `Class.forName("ClassLoaderLearning$Probe", false, loader)` trả về `Class<?>`, nhưng không yêu cầu khởi tạo `Probe`. Vì vậy dòng `Probe initialized` chưa xuất hiện.
4. Lần gọi tiếp theo với `true` yêu cầu khởi tạo. Khối `static` chạy một lần và được quan sát ở giữa hai dòng “Before”/“After”.
5. Trong cùng định nghĩa lớp đã khởi tạo, lặp lại yêu cầu initialization không chạy lại `static` initializer.

**Bài tập mở rộng ngắn:** chuyển boolean của lần `Class.forName` đầu thành `true`, chạy trong một tiến trình Java mới và giải thích vì sao `Probe initialized` xuất hiện trước dòng `Located`. Đừng nhầm biên dịch lại tệp với làm mới các lớp đã nạp trong một JVM đang chạy.

Ví dụ cố ý không tự triển khai custom loader: người học cần kiểm chứng vòng đời lớp và loader có sẵn trước khi đi vào `defineClass`.

## 5. Chọn điểm bắt đầu theo nền tảng hiện có

| Tình trạng | Nên bắt đầu | Dấu hiệu đã đủ điều kiện chuyển tiếp |
|---|---|---|
| Chưa thành thạo `javac`, `java`, `-cp` | Chặng 1 | Chạy được một lớp có package và biết classpath root |
| Đã viết Java nhưng chưa hiểu vòng đời lớp | Chặng 2 | Phân biệt được load, link, initialize |
| Biết JVM cơ bản nhưng chưa học delegation | Chặng 3 | Nêu được ba built-in loaders và ủy quyền mặc định |
| Đã nắm lý thuyết, muốn có bằng chứng | Chặng 4 + ví dụ | Dự đoán đúng stdout và giải thích từng dòng |
| Đang giải quyết lỗi plugin/dependency | Chặng 5–6 | Xác định được loader, class identity, loại lỗi và giả thuyết |
| Muốn viết loader tùy biến | Chặng 7 | Có trường hợp sử dụng cụ thể và hiểu hệ quả của cách ủy quyền |

Đây là **thứ tự phụ thuộc kiến thức**, không phải kế hoạch có số giờ cứng. Người đã vững prerequisites có thể bắt đầu ở chặng 2–3, nhưng nên làm bài thực hành để xác minh hiểu biết.

## 6. Các hiểu lầm cần tránh

- **“`import` làm JVM tìm ra lớp.”** `import` chỉ hỗ trợ viết source; classpath/module path và loader mới liên quan đến tra cứu lúc chạy.
- **“Có `.class` trên ổ đĩa nghĩa là JVM đã load.”** File có thể chưa từng được yêu cầu trong tiến trình.
- **“Lấy được `Class<?>` tức là khối `static` đã chạy.”** Có thể đã load/link nhưng chưa initialize.
- **“`Class.forName` luôn chỉ load.”** Overload một tham số yêu cầu initialization; overload ba tham số có cờ điều khiển.
- **“`null` từ `getClassLoader` nghĩa là lỗi.”** Với bootstrap-defined classes, kết quả này là bình thường.
- **“Parent loader là superclass.”** Parent trong loader phục vụ ủy quyền, hoàn toàn khác `extends`.
- **“Tên lớp giống nhau chứng minh cùng một kiểu.”** Phải xét thêm defining loader.
- **“Tất cả ClassLoader luôn parent-first.”** Đây là hành vi mặc định; custom loader/framework có thể dùng cách khác.
- **“Mọi lỗi chứa chữ Class đều là thiếu JAR.”** Còn có thất bại khởi tạo, version mismatch, linking và ranh giới loader.
- **“Phải viết custom loader từ đầu.”** Học và quan sát loader có sẵn trước sẽ giúp hiểu vì sao và khi nào cần tùy biến.

## 7. Câu hỏi cuối bài và tiêu chí hoàn tất

1. `javac` và ClassLoader đảm nhận hai trách nhiệm khác nhau thế nào? Vì sao `import` không sửa được lỗi thiếu classpath?
2. Khi `Class.forName(name, false, loader)` trả về, có thể kết luận gì về việc tìm lớp và chưa thể kết luận gì về initialization?
3. Bootstrap, platform và application loader khác nhau ở đâu? Vì sao loader của `String` có thể là `null`?
4. Trong implementation mặc định của `loadClass`, thứ tự kiểm tra lớp đã nạp, parent, `findClass` ra sao?
5. Hai lớp có cùng binary name nhưng defining loader khác nhau có phải cùng một kiểu JVM không?
6. `ClassNotFoundException`, `NoClassDefFoundError` và `ExceptionInInitializerError` đặt ra ba hướng kiểm tra gì khác nhau?
7. Khi chạy trong IDE được nhưng terminal lỗi, bạn sẽ so sánh compiler output, classpath, JAR version và launcher như thế nào?
8. Trong plugin system, vì sao nên cân nhắc để API chung được định nghĩa bởi loader mà cả ứng dụng và plugin đều thấy?

**Tiêu chí đạt:** trả lời được tám câu, tự biên dịch/chạy ví dụ và giải thích đúng thứ tự dòng stdout, đồng thời có khả năng nêu một giả thuyết kiểm chứng được cho lỗi nạp lớp. Khi đó có thể học bài chuyên sâu về class identity, parent delegation, resource lookup và custom loaders mà không thiếu nền tảng.

## 8. Tài liệu chính thức để tra cứu tiếp

- [Java SE 21 — `ClassLoader` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/ClassLoader.html): built-in loader, delegation, `loadClass`, `findClass` và resources.
- [Java SE 21 — `Class` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html): `Class.forName` và cờ `initialize`.
- [JVM Specification, Chapter 5](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html): Loading, Linking, Initializing và các điều kiện chính thức của JVM.

Tài liệu tham chiếu chọn Java 21 để dễ tra cứu; chương trình minh họa chỉ dùng cú pháp và API có sẵn trên Java 17 trở lên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="overview-008">Quy tắc đặc tả, chi tiết HotSpot và khác biệt theo phiên bản JDK</a>

<details>
<summary>Xem chi tiết</summary>

# Quy tắc đặc tả, chi tiết HotSpot và khác biệt theo phiên bản JDK

> **Ngữ cảnh:** Java Core → ClassLoader → Mô hình tổng quan. Bài này phân biệt những điều **JVM/Java SE bắt buộc**, hành vi của **API `ClassLoader`**, những gì thường quan sát ở **OpenJDK/HotSpot**, và những điểm phải kiểm tra lại khi đổi phiên bản JDK.

## 1. Ba lớp quy tắc cần phân biệt

Một hiện tượng như “lớp A do loader nào nạp?”, “tại sao không ép kiểu được?” hay “vì sao JDK mới không tìm thấy class cũ?” có thể thuộc ba lớp:

| Lớp | Nguồn quy tắc | Có thể dựa vào điều gì? |
| --- | --- | --- |
| **JVM Specification (JVMS)** | Định nghĩa class, runtime identity, loading, linking, initialization, verification, access control, loading constraints | Quy tắc tương thích của một JVM tuân thủ đặc tả; không buộc JVM dùng cấu trúc dữ liệu nội bộ hay đúng thứ tự log của HotSpot |
| **Java SE API/JLS** | `java.lang.ClassLoader`, `Class`, `Module`; JLS §12 | Hợp đồng phương thức, điều kiện khởi tạo lớp, mô hình delegation mặc định của API và ngoại lệ được công bố |
| **Triển khai JDK/VM** | OpenJDK/HotSpot, launcher, module runtime image, diagnostic tools | Tên lớp loader cụ thể, tổ chức file JDK, cờ JVM, log, Metaspace/CDS, tốc độ và cấu trúc bộ nhớ; có thể đổi theo bản JDK/VM |

**Nguyên tắc:** Nếu muốn khẳng định “mọi JVM đều thế”, phải tìm căn cứ ở JVMS/JLS hoặc API chuẩn. Nếu khẳng định “trên HotSpot JDK 17/21/25 có thể quan sát”, hãy chỉ rõ phạm vi phiên bản và cách kiểm chứng.

## 2. Bất biến do JVMS quy định

### 2.1. Định danh của lớp không chỉ là tên

Với lớp thông thường, danh tính runtime được xác định bởi cặp:

`(binary name, defining class loader)`

Hai loader khác nhau cùng `defineClass` từ bytecode mang tên `example.Plugin` tạo ra **hai kiểu runtime khác nhau**, dù bytes giống nhau. Vì thế:

- `Class<?>` của hai kiểu không bằng nhau; không thể mặc nhiên cast đối tượng từ loader thứ nhất sang kiểu do loader thứ hai định nghĩa.
- “Cùng tên package” không tự động mang lại quyền truy cập package-private. Một **runtime package** được xác định bởi *tên package + defining loader*; modular access còn chịu sự kiểm tra module.
- Hai bản cùng tên có thể cùng tồn tại khi do hai defining loader khác nhau định nghĩa; một loader không được định nghĩa hai lần cùng binary name theo cùng cách đó. Việc định nghĩa trùng gây lỗi liên kết, ví dụ `LinkageError`.

**Defining loader** trực tiếp định nghĩa lớp; **initiating loader** là loader mà JVM ghi nhận đã khởi xướng thành công việc nạp lớp, có thể qua delegation. Hai vai trò không nhất thiết giống nhau. Không suy ra defining loader chỉ từ loader ban đầu nhận lời gọi `loadClass`.

### 2.2. Delegation mặc định không đồng nghĩa một luật parent-first bất biến

API `ClassLoader.loadClass(String, boolean)` mặc định làm theo thứ tự: kiểm tra `findLoadedClass` → thử parent (hoặc bootstrap khi không có parent) → gọi `findClass` nếu chưa tìm được → resolve khi được yêu cầu. Vì vậy sơ đồ parent-first rất hữu ích cho các loader dùng triển khai mặc định.

Tuy nhiên, JVMS cho phép loader do ứng dụng định nghĩa thực hiện việc tìm/ủy quyền theo cách thích hợp miễn tuân thủ ràng buộc nạp và liên kết. Một loader có thể **child-first có chọn lọc** để cô lập plugin. Không nên kết luận “mọi Java ClassLoader luôn parent-first”. Những tên class dùng chung cho giao diện xuyên ranh giới (API host/plugin, thư viện chia sẻ, lớp nền tảng) cần được thống nhất loader để tránh identity và linkage conflict.

Nếu viết loader tùy biến, cần:
1. Giữ tính nhất quán: mỗi tên đã nạp phải trả về đúng `Class` đó trong cùng loader.
2. Tuân thủ quy tắc khóa nạp đồng thời, `getClassLoadingLock` và `findLoadedClass`; chỉ đăng ký **parallel capable** nếu triển khai hỗ trợ.
3. Kiểm tra namespace được phép định nghĩa; JVM/API hạn chế định nghĩa lớp ở namespace như `java.*`.
4. Đừng đánh đồng “đọc được resource `.class`” với “có quyền định nghĩa hoặc truy cập kiểu” — module/export, chữ ký, package, bytecode verification và security/access rules vẫn được áp dụng.

### 2.3. Loading, linking và initialization là các giai đoạn khác nhau

| Giai đoạn | Ý nghĩa | Điều cần nhớ |
| --- | --- | --- |
| **Loading** | Tìm biểu diễn nhị phân và tạo lớp (hoặc JVM tạo array class) | Chưa mặc nhiên chạy `static{}` hay static field initializer |
| **Verification** (linking) | Kiểm tra tính hợp lệ/type-safety của bytecode | Dữ liệu class file sai có thể gây `VerifyError` hoặc dạng `LinkageError` |
| **Preparation** (linking) | Tạo static fields và gán giá trị mặc định | Không chạy các initializer Java; có ngoại lệ hằng `ConstantValue` do đặc tả quy định |
| **Resolution** (linking) | Biến symbolic references thành tham chiếu runtime kèm kiểm tra access/loading constraints | Có thể thực hiện lười hoặc sớm, trong giới hạn thời điểm lỗi theo JVMS |
| **Initialization** | Thực thi `<clinit>`, static initializer và các phép gán static theo thứ tự đặc tả | Kích hoạt bởi các hành động active use được JLS/JVMS liệt kê; mỗi lớp khởi tạo nhiều nhất một lần cho mỗi identity |

Câu “class được load” **không chứng minh class đã initialize**. `ClassLoader.loadClass(name)` thông thường chỉ yêu cầu loading; `Class.forName(name, true, loader)` yêu cầu khởi tạo. Ngược lại, `Class.forName(name, false, loader)` không yêu cầu khởi tạo. Sự thật class đã được initialize hay chưa phải xét theo identity chính xác (tên + defining loader), không chỉ theo tên.

### 2.4. Type safety, loading constraints và các lỗi liên quan

JVM áp đặt **loading constraints** khi các kiểu ở những loader khác nhau giao tiếp qua superclass, method/field descriptor, v.v. Nếu cùng tên kiểu buộc phải biểu thị cùng một runtime type mà hai loader lại dẫn tới các lớp khác nhau, JVM có thể ném `LinkageError`. Mục tiêu là tránh một lời gọi phương thức truyền đối tượng với kiểu thực khác kiểu mà bên nhận giả định.

Những lỗi thường bị nhầm:

| Tình huống | Biểu hiện điển hình | Cách hiểu đúng |
| --- | --- | --- |
| Gọi `loadClass`/`Class.forName` bằng tên không tìm thấy | `ClassNotFoundException` (checked) | Không tìm thấy lớp theo loader/đường nạp được yêu cầu |
| Bytecode đang thực thi tham chiếu lớp có lúc build nhưng runtime không giải quyết được | `NoClassDefFoundError` | Lỗi liên kết kiểu runtime; cũng có thể xảy ra sau lần khởi tạo lớp thất bại |
| Class file mới hơn mức VM hỗ trợ | `UnsupportedClassVersionError` | Không phải thiếu class; là **định dạng/version bytecode** không hỗ trợ |
| Thiếu method/field lúc runtime do lệch binary dependency | `NoSuchMethodError` / `NoSuchFieldError` | Binary incompatibility; kiểm tra classpath/module path và bản JAR thực được nạp |
| Vi phạm access khi resolution | `IllegalAccessError` | Khác với lỗi reflective access như `InaccessibleObjectException` |
| Hai loader định nghĩa cùng tên nhưng đối tượng bị ép sang kiểu phía kia | `ClassCastException`, có thể chứa mô tả loader | Hai `Class` có identity khác; tên trùng không làm chúng tương thích |
| `static{}` ném exception khi khởi tạo | Thường là `ExceptionInInitializerError`; lần dùng sau có thể `NoClassDefFoundError` | Phải tìm nguyên nhân lỗi *đầu tiên* trong log, không chỉ lỗi theo sau |

Đặc tả quy định loại điều kiện lỗi và những thời điểm hợp lệ; **thứ tự log, tên class nội bộ, stack trace chính xác, thông báo lỗi** không phải giao diện ổn định để mã nghiệp vụ phụ thuộc.

### 2.5. Array class và primitive

JVM tạo array class trực tiếp; không đọc một file `[Lexample/Foo;.class`. Array có component là reference type thì defining loader tương ứng defining loader của component type; array của primitive được gắn với bootstrap loader. Vì thế `int[].class.getClassLoader()` trả về `null`; `Foo[].class.getClassLoader()` đi theo loader định nghĩa `Foo`. Đây là quy tắc JVMS, không phải một tối ưu riêng HotSpot.

## 3. Hệ phân cấp loader: hợp đồng API và cách JDK triển khai

Với JDK 9+, mô hình phổ biến có thể biểu diễn:

~~~text
Bootstrap (VM built-in; Java reflection thường hiển thị null)
    ↑
Platform loader (ClassLoader.getPlatformClassLoader())
    ↑
Application/System loader (ClassLoader.getSystemClassLoader())
    ↑
Custom loaders (nếu ứng dụng/framework tạo)
~~~

Sơ đồ là **mô hình giảng dạy**, không phải khẳng định mọi class luôn đi qua toàn bộ các nút. API tài liệu hóa rằng platform loader có thể phải ủy quyền tới loader khác khi xử lý một số module được upgrade; lớp trong module có thể được gắn với những loader khác nhau. Loader hierarchy và JPMS readability/export/access là **hai trục kiểm soát khác nhau**.

- **Bootstrap:** loader nội tại VM; trong API `Class.getClassLoader()` thường biểu diễn bằng `null`. `null` không phải lỗi và không có nghĩa class chưa được nạp.
- **Platform:** tải nhiều lớp nền tảng/JDK; JDK 9+ cung cấp `getPlatformClassLoader()`. Không đồng nhất mọi class `java.*` với platform loader: nhiều lớp lõi thuộc bootstrap.
- **Application/System:** thường tải mã trên class path và một số module; giá trị cụ thể có thể bị tùy chỉnh qua cơ chế system loader. Trên JDK 9+, **đừng cast system loader sang `URLClassLoader`**; API không hứa kiểu triển khai đó.
- **Custom:** loader plugin, container, app server, hot-reload, module layer. Có parent nhưng parent không đồng nghĩa owner của mọi lớp và không tự giải quyết xung đột class/resource.

Mã kiểm chứng trong một ứng dụng đơn giản chạy trên **class path** của JDK 17+:

~~~java
public class LoaderFacts {
    public static void main(String[] args) {
        System.out.println(String.class.getClassLoader() == null);
        System.out.println(
                LoaderFacts.class.getClassLoader()
                        == ClassLoader.getSystemClassLoader());
        System.out.println(LoaderFacts.class.getModule().isNamed());
        System.out.println(Object.class.getModule().getName());
    }
}
~~~

~~~bash
javac LoaderFacts.java
java -cp . LoaderFacts
~~~

`stdout` trong điều kiện trên:

~~~text
true
true
false
java.base
~~~

Kết quả thứ hai/ba là của cách khởi động **class path cơ bản**, không phải một bất biến của mọi custom runtime hoặc module layer. `java.base` là tên module chuẩn chứa `java.lang.Object`.

## 4. Hai thí nghiệm phân biệt loading/initialization và class identity

### 4.1. Loading không tự chạy static initializer

Tệp **`InitBoundary.java`** (Java 17+):

~~~java
public class InitBoundary {
    static class Target {
        static {
            System.out.println("Target initialized");
        }
    }

    public static void main(String[] args) throws Exception {
        ClassLoader loader = InitBoundary.class.getClassLoader();
        System.out.println("1. before load");
        loader.loadClass(Target.class.getName());
        System.out.println("2. after load");
        Class.forName(Target.class.getName(), true, loader);
        System.out.println("3. after initialization");
    }
}
~~~

~~~bash
javac InitBoundary.java
java InitBoundary
~~~

`stdout`:

~~~text
1. before load
2. after load
Target initialized
3. after initialization
~~~

`Target.class` chỉ lấy đối tượng `Class` mà không kích hoạt initialization; `loadClass` không yêu cầu initialization; lời gọi `Class.forName` với tham số `initialize=true` buộc khởi tạo nên dòng trong `static{}` xuất hiện giữa hai dấu mốc. Thử chạy lại trong *một JVM mới* để lặp lại trạng thái ban đầu; gọi lần nữa trong cùng JVM sẽ không chạy lại `static{}`.

### 4.2. Cùng binary name qua hai defining loader vẫn là hai kiểu

Tệp **`IdentityBoundary.java`** (Java 17+, chỉ minh họa việc override đúng một tên lớp; không dùng nguyên loader này trong production):

~~~java
import java.io.IOException;
import java.io.InputStream;

public class IdentityBoundary {
    static class Token {}

    private static final String TARGET = Token.class.getName();

    static final class Isolated extends ClassLoader {
        Isolated() {
            super(IdentityBoundary.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!name.equals(TARGET)) {
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

        @Override
        protected Class<?> findClass(String name)
                throws ClassNotFoundException {
            if (!name.equals(TARGET)) {
                throw new ClassNotFoundException(name);
            }
            String resource = name.replace('.', '/') + ".class";
            try (InputStream in = IdentityBoundary.class
                    .getClassLoader().getResourceAsStream(resource)) {
                if (in == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] bytes = in.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException ex) {
                throw new ClassNotFoundException(name, ex);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Class<?> left = new Isolated().loadClass(TARGET);
        Class<?> right = new Isolated().loadClass(TARGET);

        System.out.println(left.getName().equals(right.getName()));
        System.out.println(left == right);
        System.out.println(
                left.getClassLoader() == right.getClassLoader());
        System.out.println(left == Token.class);
    }
}
~~~

~~~bash
javac IdentityBoundary.java
java IdentityBoundary
~~~

`stdout`:

~~~text
true
false
false
false
~~~

Giải thích: hai `Isolated` riêng biệt đọc cùng file class của `Token`, rồi mỗi loader gọi `defineClass`. Cùng tên trả về `true`; class identity và loader instance không giống nhau. `Token.class` từ loader ứng dụng là một kiểu thứ ba. Đoạn override chỉ xử lý lớp đích để giữ nguyên delegation cho JDK classes và các dependency khác; khi thiết kế plugin thực tế cần chính sách namespace, giải quyết dependency, bảo mật và vòng đời nghiêm ngặt.

**Không suy luận sai:** khác loader không tự động gây `ClassCastException`. Chỉ khi thực hiện một thao tác đòi hai đối tượng `Class` tương thích (cast, assign, reflection, method linkage...) mới có lỗi phù hợp. Và nếu custom loader **delegates** thì nhiều loader có thể trả về *cùng một* `Class` do parent định nghĩa.

## 5. ClassLoader gặp Java Platform Module System (JPMS)

Từ JDK 9, một module có thể đặt trong **module layer** và được gán loader; một loader có thể chứa nhiều module. Đây không phải ánh xạ module ↔ loader 1–1. Khả năng nạp được bytecode khác với khả năng **truy cập** type: JPMS còn xét readability, `exports` (truy cập API công khai) và `opens` (deep reflection theo quy tắc). Một class hiện diện trong runtime image nhưng không đọc/truy cập hợp lệ từ module gọi vẫn không thể được sử dụng như lớp công khai tùy ý.

Ví dụ tổng quát:

- `ClassLoader.loadClass("pkg.Hidden")` có thể trả về một `Class`, nhưng phép truy cập sau đó vẫn có thể thất bại vì module/package access.
- `--add-exports module/pkg=target` có thể mở quyền truy cập public type ở package không export; `--add-opens module/pkg=target` phục vụ deep reflection. Dùng có mục tiêu để tương thích/migrate, không xem là cách sửa dài hạn thay cho API công khai.
- `Thread.currentThread().getContextClassLoader()` (**TCCL**) cho thư viện/framework khả năng tra cứu provider trong ngữ cảnh caller, thường liên quan `ServiceLoader`, SPI, container và plugin. TCCL không phải “defining loader của thread”; cần đặt, khôi phục trong `finally` và tránh giữ reference đến plugin loader khi hot-reload.
- Resource lookup qua `getResource` / `getResources` có cơ chế delegation riêng và có thể chịu **encapsulation của module**; sự tồn tại của tài nguyên trên disk/JAR không bảo đảm mọi loader đều trả về URL của nó.
- Một multi-release JAR (`Multi-Release: true`) có thể có bytecode khác nhau dưới `META-INF/versions/N`. Runtime có thể chọn biến thể thích hợp cho phiên bản Java đang chạy. Vì vậy “cùng tên JAR” vẫn có thể cho class thực tế khác tùy JDK.

Không thay thế kiểm tra module graph bằng cây parent loader; đây là hai câu hỏi khác nhau: **ai định nghĩa kiểu?** và **ai được đọc/truy cập kiểu?**

## 6. HotSpot: những điều thường thấy nhưng không nên coi là luật JVMS

### 6.1. Nguồn class và cấu trúc runtime

- Trên **JDK 8**, các class JDK thường nằm trong `rt.jar` và extension mechanism; nhiều ứng dụng có thể gặp `ExtClassLoader` / `AppClassLoader` trong tên triển khai.
- Từ **JDK 9**, runtime image sử dụng module system và tài nguyên hệ thống có thể xuất hiện với scheme `jrt:/`. Extension loader trở thành **platform loader**, ứng dụng không được giả định system loader là `URLClassLoader` hoặc `java.home/lib/rt.jar` tồn tại.
- HotSpot lưu class metadata trong **Metaspace** (từ JDK 8; thay PermGen). Metaspace là chi tiết quản lý bộ nhớ của HotSpot; JVMS mô tả method area nhưng không bắt mọi JVM phải bố trí kiểu “Metaspace” giống HotSpot.
- HotSpot có thể sử dụng Class Data Sharing (CDS), archived classes, lazy resolution, tối ưu nạp/link tùy build và cấu hình; không suy ra từ log “source” rằng class luôn đọc từng byte trực tiếp từ đúng đường dẫn đó.
- Class unloading liên quan khả năng reclaim của loader/class và GC, không tự động xảy ra ngay khi gọi `URLClassLoader.close()`. `close()` giải phóng các handle/tài nguyên có thể đóng; class và loader còn bị tham chiếu thì chưa được giải phóng.

### 6.2. Chẩn đoán dựa trên công cụ, không dựa vào tên nội bộ

Các lệnh ví dụ dành cho **OpenJDK/HotSpot trên JDK 17+**; phiên bản khác có thể có tên option hoặc số lượng thông tin khác:

~~~bash
# In quá trình class loading (log có thể rất dài).
java -Xlog:class+load=info InitBoundary

# Thấy cả sự kiện class unloading (nếu GC thực sự unload).
java -Xlog:class+load=info,class+unload=info InitBoundary

# Cú pháp tương thích có từ JDK cũ, ít chi tiết/chỉnh lọc hơn.
java -verbose:class InitBoundary

# Chạy với PID của tiến trình Java còn sống, nếu jcmd attach được.
jcmd <pid> VM.classloaders
jcmd <pid> VM.classloader_stats
~~~

Các lệnh `jcmd` phụ thuộc JVM đích, quyền attach, môi trường container và danh sách diagnostic commands mà VM hỗ trợ; chạy `jcmd <pid> help` để xác nhận lệnh thực có. `VM.classloaders` hữu ích để thấy hierarchy, `VM.classloader_stats` để xem thống kê, nhưng **không** là hợp đồng đầu ra chuẩn của ngôn ngữ Java.

Khi điều tra sự cố, hãy ghi cùng lúc `java -version`, nhà phân phối JDK, cách chạy trên classpath/module path, tập JAR và dòng lệnh JVM. Đầu ra log thay đổi theo JDK, bật thêm log có thể ảnh hưởng performance; không nên parse bằng regex phụ thuộc tên `jdk.internal.loader.ClassLoaders$AppClassLoader`.

### 6.3. Vòng đời loader và lỗi memory leak ở container/plugin

Khả năng unload class phụ thuộc việc không còn các tham chiếu giữ loader/class có thể thu hồi; một GC cycle cũng không bảo đảm unload xảy ra đúng lúc. Nguồn giữ reference thường gặp: static registry của host, `ThreadLocal`, thread sống lâu, thread context class loader, executor/scheduler, listener, JMX, callback và cache giữ `Class`/`Method`. Khi reload plugin:

1. Dừng tác vụ và thread do plugin sở hữu; hủy listener/registration.
2. Khôi phục TCCL sau khi dùng và dọn `ThreadLocal`/cache chứa object hoặc class plugin.
3. Đóng `URLClassLoader`/resource nếu có; sau đó bỏ strong reference đến loader, instance và class.
4. Theo dõi loader/class count, GC và Metaspace sau nhiều chu kỳ reload; dùng công cụ phù hợp với phiên bản HotSpot.

Đừng dùng mỗi số MB Metaspace tăng trong một lần đo để kết luận leak: cần quan sát xu hướng qua nhiều lần GC/reload và phân biệt cơ chế cache/commit memory của VM.

## 7. Những khác biệt quan trọng theo JDK

| Phiên bản | Khác biệt cần nhớ khi học/triển khai ClassLoader | Class file major phổ biến |
| --- | --- | ---: |
| **JDK 8** | Trước JPMS; nhiều cấu hình classpath/`rt.jar`/extension dirs; PermGen đã thay bằng Metaspace trên HotSpot | **52** |
| **JDK 9** | JPMS (`module-info.java`, module path, layer); loader **platform** thay extension; runtime image modular, `jrt:/`; system loader không còn được giả định là `URLClassLoader`; unified logging `-Xlog` | **53** |
| **JDK 11** | Loại khỏi JDK các module Java EE/CORBA được liệt kê trong JEP 320; ứng dụng cũ có thể gặp class missing dù loader vẫn hoạt động đúng | **55** |
| **JDK 15** | **Hidden classes** (JEP 371), dùng cho runtime/framework động; tên `Class.getName()` của hidden class không nhất thiết là binary name thông thường; không suy ra có thể nạp lại bằng `Class.forName` | **59** |
| **JDK 17** | Strong encapsulation của JDK internals (JEP 403); `--illegal-access` không còn hiệu lực mở rộng như trước; reflection có thể gặp `InaccessibleObjectException` | **61** |
| **JDK 21** | Các quy tắc nòng cốt của JVMS về class identity/loading vẫn áp dụng; kiểm tra lại công cụ HotSpot, module configuration và version class file khi nâng cấp | **65** |
| **JDK 25** | Tiếp tục các ranh giới JVMS/JPMS; không dựa vào tên loader nội bộ, option VM hoặc log của JDK 8/11 làm hợp đồng lâu dài | **69** |
| **JDK 26** | Class file major tăng theo release; chương trình biên dịch bằng target mặc định của JDK mới có thể không chạy trên runtime cũ | **70** |

**Phân biệt version:** phiên bản **Java source**, class file target của `javac --release N` và **runtime JVM** là ba thông tin khác nhau. Ví dụ ứng dụng chạy bằng JDK 17 nhưng nạp file `major=65` được biên dịch cho Java 21 có thể nhận `UnsupportedClassVersionError`. Giảm `--release` chỉ giải quyết target bytecode và API được phép ở compile time; không sửa được những API bên thứ ba đòi JDK cao hơn hoặc semantic khác do JPMS, reflection, loader tùy biến.

Lệnh kiểm tra:

~~~bash
java -version
javac -version
javap -verbose LoaderFacts
~~~

Trong đầu ra `javap -verbose`, tìm dòng `major version`. Khi kiểm tra JAR chứa nhiều bản class, xác định entry thực được chọn (kể cả multi-release JAR) trước khi kết luận.

### 7.1. Ví dụ nâng cấp JDK 8 → 17/21/25: thay đổi thuộc lớp nào?

**Trường hợp A — mã cũ cast system loader:**

~~~java
// Dễ chạy trên một số môi trường JDK 8; không phải API contract.
java.net.URLClassLoader loader =
        (java.net.URLClassLoader) ClassLoader.getSystemClassLoader();
~~~

Trên nhiều JDK 9+ HotSpot, cast này dẫn tới `ClassCastException`. Sửa đúng là dùng API `ClassLoader`/`getResource` khi chỉ cần tải resource, hoặc tự quản lý một `URLClassLoader` do ứng dụng tạo nếu thật sự cần chỉnh URL. Không kiểm tra bằng việc ép kiểu loader nội bộ.

**Trường hợp B — truy cập `sun.*`/`jdk.internal.*` bằng reflection:** JDK 17+ có thể chặn theo module encapsulation mặc định. Đây chủ yếu là giới hạn JPMS/runtime access, không phải parent loader “quên tải” class. Ưu tiên API được hỗ trợ; chỉ dùng `--add-opens`/`--add-exports` cho gói cụ thể khi đang chuyển đổi dependency.

**Trường hợp C — thiếu `javax.xml.bind` khi lên JDK 11+:** Java EE/CORBA modules đã bị loại khỏi JDK. Phải thêm dependency tương thích và cân nhắc namespace/phiên bản đúng; tạo child-first loader không tự đưa API đã bị loại trở lại.

**Trường hợp D — plugin có hai bản thư viện:** một plugin load cùng FQCN nhưng loader khác với API host. Cast hoặc linkage có thể thất bại. Đặt interface được chia sẻ dưới parent/loader thống nhất; chọn rõ package nào delegated, package nào isolated; kiểm tra dependency transitive và TCCL.

## 8. Quy trình điều tra khi thấy lỗi liên quan ClassLoader

1. **Nhận diện lỗi đầu tiên:** lấy đầy đủ exception/cause/stack trace, phân biệt checked `ClassNotFoundException` với `LinkageError`, `ClassCastException`, `InaccessibleObjectException`.
2. **Xác định context runtime:** `java -version`, VM vendor, flags, launch classpath/module path, module layer, class file major.
3. **Kiểm tra nơi class được định nghĩa:** `clazz.getName()`, `clazz.getClassLoader()`, `clazz.getModule()`, đường resource/code source khi hợp lệ; tránh in thông tin nhạy cảm từ môi trường production.
4. **Kiểm tra bản dependency thực sự được chọn:** JAR trùng lặp, version mediation, shaded package, multi-release entries, classpath/module path và classload order.
5. **Tách vấn đề “tìm” khỏi vấn đề “truy cập”:** class không tồn tại/không giải được, module không readable/exported/open, package bị sealed, bytecode quá mới hay loader có identity khác?
6. **Tái hiện tối thiểu:** một file Java hoặc một plugin nhỏ với cùng kiểu loader; so sánh cùng workload trên phiên bản JDK đang cần hỗ trợ.
7. **Dùng diagnostics có giới hạn:** `-Xlog:class+load` hoặc `jcmd` nếu có; xác nhận lại bằng API và đặc tả, vì log/tên class HotSpot không phải API ổn định.

### Checklist tự kiểm chứng kiến thức

- Nếu hai class cùng FQCN nhưng defining loader khác nhau, chúng có nhất thiết là một kiểu? **Không.**
- Nếu `ClassLoader.loadClass` chạy thành công, `static{}` chắc chắn đã chạy? **Không.**
- Nếu class ở runtime image, mọi module khác đều có thể reflective access? **Không.**
- Sơ đồ parent-first là thuật toán duy nhất mọi loader bắt buộc dùng? **Không.**
- `String.class.getClassLoader() == null` trên HotSpot có phải lỗi? **Không**, đó là cách API biểu diễn bootstrap loader.
- `java -Xlog:class+load` và tên `AppClassLoader` là quy định JVMS? **Không**, đó là triển khai/công cụ của JDK/VM.
- `URLClassLoader.close()` có bảo đảm unload tức thì? **Không.**
- Một `NoClassDefFoundError` luôn nghĩa là file JAR bị thiếu? **Không**; cần truy nguyên linkage hoặc khởi tạo thất bại.

## 9. Tài liệu chuẩn để đối chiếu

1. [JVMS `5 — Loading, Linking, and Initializing (Java SE 25)](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-5.html) — defining/initiating loader, class identity, loading constraints, runtime package, linking và initialization.
2. [`ClassLoader` API (JDK 25)](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ClassLoader.html) — delegation mặc định, built-in loaders, parallel capable, resource lookup.
3. [JLS §12 — Execution](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html) — khởi tạo lớp và các tình huống active use.
4. [JEP 261 — Module System](https://openjdk.org/jeps/261) — module/loader changes từ JDK 9.
5. [JEP 320 — Remove the Java EE and CORBA Modules](https://openjdk.org/jeps/320) — lý do class cũ không còn nằm trong JDK 11.
6. [JEP 371 — Hidden Classes](https://openjdk.org/jeps/371) — cơ chế class động đặc biệt từ JDK 15.
7. [JEP 403 — Strongly Encapsulate JDK Internals](https://openjdk.org/jeps/403) — truy cập JDK internals từ JDK 17.
8. [`jcmd` tool (JDK 25)](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jcmd.html) — command `VM.classloaders` và `VM.classloader_stats`.
9. [JAR File Specification — Multi-release JAR](https://docs.oracle.com/en/java/javase/17/docs/specs/jar/jar.html) và [`ClassFile` versions (JDK 26)](https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/lang/classfile/ClassFile.html) — khác biệt bytecode theo phiên bản.

**Điều cần giữ sau bài:** mỗi khi nói về ClassLoader, trước tiên xác định phát biểu nằm ở *đặc tả JVMS/JLS*, *hợp đồng API Java SE* hay *quan sát HotSpot trên JDK cụ thể*. Nhờ đó ta giải thích đúng lỗi tương thích, chọn hướng sửa có thể duy trì khi nâng cấp JDK và tránh dựa vào chi tiết triển khai không được bảo đảm.


</details>

- [Quay lại đầu trang](#back-to-top)
