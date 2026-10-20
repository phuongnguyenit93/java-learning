<a id="back-to-top"></a>

# Các thời điểm dệt

## Menu
- [Dệt khi biên dịch và yêu cầu đối với chuỗi xây dựng](#weaving-lifecycle-001)
- [Dệt sau biên dịch trên mã trung gian hoặc thư viện có sẵn](#weaving-lifecycle-002)
- [Dệt khi nạp và quan hệ với cơ chế nạp lớp hoặc mô-đun](#weaving-lifecycle-003)
- [Can thiệp lúc chạy và giới hạn của nền tảng](#weaving-lifecycle-004)
- [Thay đổi aspect và nhu cầu xây dựng hoặc khởi động lại](#weaving-lifecycle-005)
- [Ảnh hưởng đến đóng gói, triển khai và tính lặp lại của bản dựng](#weaving-lifecycle-006)
- [Khả năng can thiệp thư viện ngoài và giới hạn sở hữu mã](#weaving-lifecycle-007)
- [So sánh độ bao phủ, chi phí và độ phức tạp vận hành](#weaving-lifecycle-008)
- [Chọn thời điểm dệt theo yêu cầu hệ thống](#weaving-lifecycle-009)

## <a id="weaving-lifecycle-001">Dệt khi biên dịch và yêu cầu đối với chuỗi xây dựng</a>

<details>
<summary>Xem chi tiết</summary>

# Dệt khi biên dịch và yêu cầu đối với chuỗi xây dựng trong AOP

**Dệt khi biên dịch** (*compile-time weaving*, CTW) là cách kết hợp **aspect** với chương trình trong giai đoạn tạo mã thực thi. Công cụ biên dịch hoặc bộ dệt (*weaver*) sử dụng mã chương trình cơ sở, các định nghĩa aspect, pointcut, advice và thông tin kiểu để tạo **bytecode đã kết hợp hành vi bổ sung** ở những điểm can thiệp được hỗ trợ.

Điểm quan trọng nhất của bài: **weaving xảy ra ở thời điểm build; advice chạy về sau, khi chương trình thực thi một join point phù hợp**. Một aspect được khai báo trong nguồn không tự chạy. Việc biên dịch thành công chưa chứng minh artifact triển khai đã chứa bytecode được dệt; người xây dựng hệ thống còn phải kiểm soát chuỗi build, kiểm thử và đóng gói.

Bài trình bày **AOP cơ bản và nâng cao nói chung**, không gán chức năng của một công cụ riêng cho mọi hệ AOP. Những khả năng như field get/set, constructor, method call, method execution, binding và thứ tự advice phụ thuộc mô hình join point cùng công cụ dệt cụ thể.

## 1. Sau bài học cần hiểu gì?

- Đầu vào của compile-time weaving gồm những gì; thông tin nào là bắt buộc và thông tin nào phụ thuộc công cụ.
- Bộ dệt tạo ra gì: file class, bytecode, artifact; vì sao không nhất thiết phải sửa file Java gốc.
- Sự khác nhau giữa **compile-time**, **post-compile/binary**, **load-time** và **runtime proxy composition**.
- Cách thiết kế pipeline để chỉ sử dụng **output đã weave** trong integration test, packaging và triển khai.
- Cách cấu hình phạm vi lớp, dependency, classpath/module path, thứ tự aspect và mức JDK mục tiêu.
- Cách quản lý incremental build, cache, generated code, double weaving, dependency runtime và bản phát hành.
- Cách chứng minh pointcut khớp đúng nơi và advice thực sự được kích hoạt mà không đổi hợp đồng nghiệp vụ.
- Những lỗi phổ biến khi build báo thành công nhưng chương trình không có advice.

## 2. Bản đồ kiến trúc weaving

```text
    Base source / bytecode        Aspect definitions
              |                   Pointcut + Advice
              |                          |
              +-------------+------------+
                            |
               Dependencies / type model
               Compiler, weaver, configuration
                            |
                            v
                   COMPILE + WEAVE
                            |
                            v
                     WOVEN CLASSES
                            |
                  Test -> Package -> Deploy
                            |
                            v
                        JVM RUNS
                            |
                 Matching join point?
                         /     \
                       No       Yes
                       |         |
                 No advice   Advice executes
```

Đây là sơ đồ **trách nhiệm logic**, không phải thuật toán cố định của một weaver. Khi công cụ tích hợp trực tiếp vào compiler, compile và weave có thể nằm trong một bước. Khi xử lý bytecode sau biên dịch, compiler và weaver là hai bước khác nhau.

| Thành phần | Trách nhiệm |
| --- | --- |
| **Base program** | Mã có chức năng nghiệp vụ cần giữ nguyên hợp đồng |
| **Aspect** | Khía cạnh biểu diễn chính sách cắt ngang |
| **Pointcut** | Chọn join point theo loại, chữ ký, kiểu, phạm vi, bối cảnh |
| **Advice** | Hành vi trước/sau/bao quanh điểm được chọn |
| **Join point model** | Quy định những điểm nào thực sự có thể can thiệp |
| **Weaver** | Kết hợp hành vi aspect vào kết quả biên dịch |
| **Weaving configuration** | Xác định đầu vào, include/exclude, thứ tự, tùy chọn |
| **Woven output** | Mã chạy đã có cơ chế để kích hoạt advice |
| **Runtime** | Nơi lời gọi xảy ra và advice thực sự được chạy |

Một lớp có thể được dệt thành công nhưng không có lời gọi nào chạm join point. Vì vậy **số lớp được weave khác số lần advice chạy**.

## 3. Những đầu vào của compile-time weaving

### 3.1. Chương trình cơ sở

Một lớp Java thường:

```java
package example.order;

public final class OrderService {
    public String submit(String id) {
        return "ORDER:" + id;
    }
}
```

Đây là **một tệp lớp độc lập** đặt ở `example/order/OrderService.java`, chưa có `main`. Một pointcut có thể muốn chọn thực thi `submit(String)`; một pointcut khác có thể chọn các **lời gọi** đến phương thức đó.

Điểm **method execution** có vị trí ở phương thức đích, còn điểm **method call** có thể nằm ở mã chứa lời gọi phía người gọi. Nếu chỉ đưa một phía vào tập mã được weave, tập điểm can thiệp thực sự có thể khác nhau. Không nên xem hai loại điểm là tương đương.

### 3.2. Định nghĩa aspect

Aspect thường chứa bộ lọc pointcut, một hoặc nhiều advice và đôi khi có trạng thái/precedence. Ví dụ mục tiêu kiến trúc:

```text
Chọn: thực thi example.order.OrderService.submit(String)
Trước: ghi thời điểm bắt đầu
Sau trả về: ghi thời lượng thành công
Sau lỗi: ghi loại ngoại lệ
Luôn dọn: khôi phục bối cảnh đo lường đã mở
```

Đây là **đặc tả ý tưởng**, không phải cú pháp AOP hay Java trực tiếp. Một công cụ thật phải cung cấp cú pháp có khả năng mô tả các điểm đó và cách binding dữ liệu.

### 3.3. Thông tin kiểu và thư viện phụ thuộc

Để biết pointcut có khớp không, weaver có thể cần:

- Định nghĩa lớp, superclass, interface và chữ ký;
- Modifier, annotation và kiểu tham số/return;
- Mã hoặc thư viện mà advice gọi tới;
- Classpath hoặc module path để phân giải kiểu;
- Phiên bản compiler, weaver và mục tiêu bytecode/JVM.

Nếu thiếu classpath, áp sai phiên bản dependency hoặc dùng phiên bản weaver không hỗ trợ mức bytecode đầu vào, build có thể lỗi hoặc tạo artifact không phù hợp. Tên cờ cấu hình rất khác nhau; **`javac` thông thường không tự hiểu một ngôn ngữ pointcut chuyên biệt chỉ vì có file aspect trong thư mục nguồn**.

### 3.4. Cấu hình include/exclude và precedence

Một chính sách chỉ dành cho cổng xử lý đơn hàng không nên tác động toàn bộ mã logging, serialization hoặc thư viện ngoài. Build cần biết tập lớp/bytecode nằm trong phạm vi công cụ xử lý. Bộ lọc pointcut lại quyết định **join point nào trong phạm vi ấy được chọn**.

Nếu một class không nằm trong tập đầu vào weave, pointcut đúng tên của nó cũng chưa có hiệu lực. Nếu một lớp có nhiều aspect khớp, thứ tự có thể đổi ý nghĩa, chẳng hạn:

```text
Timer(Retry(Operation))  -> đo cả chuỗi retry
Retry(Timer(Operation))  -> đo mỗi lần thử nếu Timer chạy trong retry
```

Thứ tự không nên được suy từ tên file; phải dùng quy tắc/cấu hình precedence của công nghệ và kiểm thử.

## 4. Đầu ra thực sự của compile-time weaving

Đầu ra thường là các file `.class` đã mang logic bổ sung, có thể được gói vào `.jar` hoặc artifact khác. Mã nguồn nghiệp vụ có thể **không bị sửa**, và cách weaver tổ chức bytecode không nhất thiết giống việc chèn thẳng câu `println` vào thân phương thức.

```text
Source + Aspect + Config
          |
          v
 AOP-aware compiler/weaver
          |
          +--> Woven .class files
                         |
                         v
                     Application JAR
                         |
                         v
                        JVM
```

Cần tách hai khái niệm:

- **Đầu ra trung gian**: thư mục class hoặc bytecode ngay sau bước weaving.
- **Đầu ra triển khai**: JAR, image hoặc gói ứng dụng cuối cùng có những class sẽ được JVM nạp.

Dự án có thể chạy bước weaving thành công nhưng đóng gói nhầm thư mục class gốc. Khi đó application JAR vẫn chạy được nghiệp vụ, song thiếu advice.

### 4.1. Không nhất thiết phải tồn tại lớp proxy

Khi CTW thực sự biến đổi bytecode ở method execution, một lời gọi tới đối tượng đích có thể đi qua logic đã dệt ngay tại phương thức đó theo mô hình công cụ. Điều này khác JDK dynamic proxy, nơi người gọi phải giữ đối tượng đại diện thay vì đối tượng gốc.

Tuy nhiên, **CTW không tự bảo đảm mọi điểm can thiệp**. Mô hình AOP, input class và loại join point vẫn giới hạn tập tác động.

### 4.2. Đã weave khác đã kích hoạt advice

Có thể xảy ra:

```text
Aspect defined:        YES
Pointcut resolved:     YES
Woven class generated: YES
Join point executed:   NO
Advice invoked:        NO
```

Hoặc join point đã xảy ra nhưng một điều kiện runtime trong pointcut không thỏa. Việc bytecode đã chứa logic kiểm tra động không có nghĩa advice phải chạy cho mọi lượt thực thi.

## 5. Các thời điểm dệt dễ nhầm với nhau

| Kiểu kết hợp | Thời điểm | Đầu vào chính | Đầu ra chủ yếu |
| --- | --- | --- | --- |
| **Compile-time weaving** | Trong bước compiler/weaver hiểu mã nguồn/aspect | Base source, aspect, thông tin kiểu | Class/artifact đã weave |
| **Post-compile/binary weaving** | Sau compiler thường, trước sử dụng artifact | Class đã biên dịch, aspect | Binary đã biến đổi |
| **Load-time weaving** | Trước khi JVM định nghĩa lớp | Bytecode lúc nạp, transformer/config | Class definition đã biến đổi trong JVM |
| **Runtime proxy/interceptor** | Lúc tạo/lắp đại diện | Đối tượng, giao diện/lớp, interceptor | Proxy/đường gọi được bao quanh |

Điểm chung của compile-time và binary weaving là **có thể chuẩn bị artifact trước runtime**. Điểm khác: binary weaving xử lý bytecode sau một bước biên dịch trước đó, còn CTW tích hợp thao tác weave trong quá trình biên dịch.

Load-time weaving có vấn đề riêng về class loader, thời điểm lớp được định nghĩa và khả năng biến đổi lớp đã nạp. Runtime proxy có vấn đề về **ai giữ tham chiếu proxy**. Không dùng những giới hạn của proxy để kết luận giới hạn cho tất cả hình thức CTW.

## 6. Chuỗi xây dựng đúng: đầu vào tới triển khai

Một pipeline có thể được mô hình hóa:

```text
1. Resolve toolchain/dependencies
2. Compile and weave the designated inputs
3. Verify woven output and run woven tests
4. Package ONLY the intended woven classes
5. Verify packaged artifact
6. Publish, deploy, run smoke tests
```

Đây là **giả mã quy trình**, không phải lệnh của một hệ build cụ thể. Nếu công cụ dùng binary weaving tách riêng, bước thứ 2 thành:

```text
compile base -> create original classes -> binary weave -> woven classes
```

Với CTW tích hợp, bước `compileAndWeave` có thể là một task. Điều kiện quan trọng: **task kiểm thử AOP và task đóng gói phải phụ thuộc vào đầu ra đã weave**, không phụ thuộc song song vào original classes.

### 6.1. Sai đồ thị phụ thuộc

Đồ thị nguy hiểm:

```text
compile -----+----> package
             |
             +----> weave
```

Ở đây `package` có thể chạy mà không đợi `weave`. Cấu trúc mong muốn:

```text
compile / weave ---> woven tests ---> package ---> artifact checks
```

Hoặc trong build hai bước:

```text
compile ---> weave ---> woven tests ---> package
```

Một bước test nghiệp vụ thuần có thể chạy sớm; nhưng không thể thay cho **integration test trên lớp đã weave**.

### 6.2. Ví dụ cấu trúc thư mục, không phải quy ước bắt buộc

```text
project/
  src/main/java/
  src/main/aspects/
  build/classes-original/
  build/classes-woven/
  build/dist/application.jar
```

Các đường dẫn này chỉ để hình dung đường dữ liệu. Không phải công cụ nào cũng dùng tệp aspect riêng hoặc hai thư mục class. Khi có cả bản gốc và bản woven, cần đánh dấu **chính xác task đọc/ghi từng thư mục** và bảo đảm packaging lấy đúng nguồn.

### 6.3. Test nên chạy ở đâu?

Một tầng unit test có thể kiểm tra business logic chưa weave để chẩn đoán dễ hơn. Nhưng phải có test dùng **class sau weave** để kiểm tra advice; khi đóng gói xong nên có smoke/integration test chạy **artifact cuối** trong môi trường runtime tương thích.

Chỉ thấy unit test của base class pass không có nghĩa một around advice mới sẽ giữ nguyên kết quả và ngoại lệ.

## 7. Ví dụ Java 17: mô hình hành vi đầu ra đã dệt

**Lưu ý:** JDK 17 `javac` thông thường không cung cấp một trình biên dịch pointcut tổng quát. Ví dụ sau **viết tay mô hình kết quả logic sau weaving**, để học về điều kiện bảo toàn và thứ tự advice. Nó không tự thực hiện CTW và không chứng minh weaver thực nào tạo cùng cấu trúc wrapper.

Lưu nội dung thành `CompileWeavingIllustration.java`:

```java
public class CompileWeavingIllustration {
    static final class OrderService {
        String submit(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("invalid id");
            }
            return "ORDER:" + id;
        }
    }

    // Mô hình hóa hành vi mà đầu ra đã kết hợp có thể thể hiện.
    static final class WovenOrderService {
        private final OrderService target = new OrderService();

        String submit(String id) {
            String shown = id == null ? "<null>"
                    : id.isBlank() ? "<blank>" : id;
            System.out.println("[BEFORE] " + shown);
            try {
                String result = target.submit(id);
                System.out.println("[RETURN] " + result);
                return result;
            } catch (RuntimeException error) {
                System.out.println("[THROW] "
                        + error.getClass().getSimpleName());
                throw error;
            } finally {
                System.out.println("[FINALLY]");
            }
        }
    }

    public static void main(String[] args) {
        OrderService original = new OrderService();
        WovenOrderService woven = new WovenOrderService();

        System.out.println("BASE " + original.submit("A"));
        System.out.println("WOVEN " + woven.submit("B"));

        try {
            woven.submit(" ");
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER_ERROR " + error.getMessage());
        }

        System.out.println("END");
    }
}
```

Chạy với Java 17+:

```bash
javac -encoding UTF-8 CompileWeavingIllustration.java
java CompileWeavingIllustration
```

Kết quả:

```text
BASE ORDER:A
[BEFORE] B
[RETURN] ORDER:B
[FINALLY]
WOVEN ORDER:B
[BEFORE] <blank>
[THROW] IllegalArgumentException
[FINALLY]
CALLER_ERROR invalid id
END
```

**Phân tích:** Trong đoạn mô hình hóa, `OrderService` là hành vi gốc, còn `WovenOrderService` biểu diễn một kết quả có before, after-returning, after-throwing và finally. Khi tác vụ thành công, người gọi vẫn nhận `ORDER:B`. Khi đầu vào là khoảng trắng, người gọi vẫn nhận ngoại lệ `IllegalArgumentException`; nhánh `[RETURN]` không chạy, nhưng cleanup kiểu `finally` vẫn diễn ra.

Một weaver CTW thật có thể chèn bytecode trực tiếp vào lớp, không tạo `WovenOrderService` như ví dụ. **Dùng chương trình này để học hợp đồng đầu ra**, rồi kiểm chứng một công cụ thật bằng artifact và integration test của chính nó.

### 7.1. Test nào chứng minh CTW thật?

Không phải `javac` thành công với mã minh họa ở trên. Với một bộ dệt thật, cần:

1. Xác minh nó nhận định nghĩa aspect.
2. Xác minh điểm thực thi cần chọn nằm trong phạm vi weaving.
3. Xác minh output class/JAR sau weaving được tạo.
4. Chạy bản output ấy và khẳng định advice chạy ở điểm đúng.
5. Kiểm thử âm những điểm nằm ngoài pointcut.
6. Xác minh kết quả và ngoại lệ nghiệp vụ được giữ nếu advice chỉ quan sát.

Có thể dùng bytecode disassembler như `javap -c` để hỗ trợ tìm hiểu một class, nhưng hình dạng mã biến đổi phụ thuộc công cụ và bytecode tối ưu. Không nên dùng một mẫu lệnh máy duy nhất làm tiêu chí thành công cho mọi weaver.

## 8. Incremental build và bộ nhớ đệm

Ở những dự án lớn, build thường tái sử dụng output cũ. Với CTW, **định nghĩa aspect và cấu hình weaving phải được tính là đầu vào của task tạo class**.

Ví dụ: source của `OrderService` không đổi nhưng pointcut chuyển từ `submit(String)` sang mọi overload `submit(..)`. Nếu cache chỉ theo dõi thay đổi source nghiệp vụ, class được tái sử dụng có thể vẫn chứa logic advice cũ.

Các thay đổi có thể làm output không còn hợp lệ:

- Sửa thân advice;
- Thêm, xóa hoặc thu hẹp pointcut;
- Thay include/exclude, precedence;
- Đổi phiên bản weaver hoặc mức bytecode;
- Đổi dependency ảnh hưởng phân giải kiểu;
- Thêm overload hoặc lớp mới;
- Thay cấu trúc module/classpath.

### 8.1. Kiểm thử build sạch và build gia tăng

Một ma trận nhỏ:

| Thao tác | Kỳ vọng |
| --- | --- |
| Sửa advice, không sửa business class | Build tạo output chứa advice mới |
| Xóa aspect | Output mới không còn hành vi aspect bị xóa |
| Thêm overload | Pointcut khớp đúng overload theo chính sách |
| Đổi cấu hình exclude | Tập class được weave cập nhật |
| Clean build so với incremental cùng input | Hành vi quan sát tương đương |
| Đổi dependency/bytecode target | Toolchain phân giải lại tương thích |

Không cần tắt toàn bộ cache; cần khai báo đúng **input và output của bước weave**, cùng phép test xác minh.

## 9. Tránh double weaving và output hỗn hợp

Nếu một class đã được dệt lại bị đưa qua cùng công cụ lần nữa, nó có thể:

- Chứa logic advice trùng lặp;
- Có chuỗi chặn lồng nhau không chủ ý;
- Tăng kích thước bytecode hoặc chi phí;
- Hoặc bị công cụ phát hiện/từ chối.

Kết quả cụ thể phụ thuộc công cụ; **không mặc định mọi weaver có tính idempotent**. Thiết kế build nên có **một đường tạo output đã weave rõ ràng**, không để cả original và woven class cạnh tranh nhau trong classpath/packaging.

Một lỗi khác là trộn class từ build cũ và build mới vào cùng thư mục. Cần đảm bảo task dọn output hay kiểm tra tính đầy đủ không làm xóa nhầm dữ liệu ngoài phạm vi nhưng cũng không mang file class lỗi thời vào artifact.

## 10. Runtime dependencies và compatibility

Output đã weave có thể tham chiếu đến code của aspect, helper hoặc thư viện runtime của công nghệ AOP. Dependency có ở **compile/weaving classpath** chưa chắc đã có trong **runtime classpath**.

Dấu hiệu lỗi:

- Build và weaving đều thành công;
- JAR được tạo;
- Khi JVM nạp lớp hoặc chạy join point, xuất hiện `NoClassDefFoundError`, lỗi linkage hoặc lỗi cấu hình.

Cách kiểm tra là phân biệt:

- **Compiler/weaver tools** chỉ cần ở build;
- **Runtime support classes** có thể cần lúc chạy output đã weave;
- **Test helpers** chỉ cần ở test;
- **Dependencies của advice** có thể khác dependencies trực tiếp của base class.

Ngoài ra cần đảm bảo bytecode phù hợp mức JVM đích. Nếu xuất bytecode cho mức cao hơn JVM triển khai, ứng dụng có thể lỗi khi tải lớp dù bước weaving đã thành công.

### 10.1. Module và class loader

JPMS định nghĩa các quan hệ `requires`, `exports`, `opens`, nhưng không tự thực hiện weaving. Các hạn chế reflection, quyền truy cập/module, class loader và bytecode transformation phải được kiểm chứng theo JVM/công cụ thật.

Hai class cùng tên nhưng do hai loader khác nhau định nghĩa có thể là hai kiểu runtime riêng, nên tập lớp được weave và tập lớp chạy có thể không giống điều người thiết kế suy ra từ tên file.

## 11. Pointcut tĩnh, điều kiện động và độ phủ thực thi

Một weaver có thể phân tích chữ ký, package và kiểu tại build. Nhưng giá trị đối số, target instance và ngữ cảnh chạy chỉ có ở runtime. Công cụ có thể chèn bộ kiểm tra điều kiện động trước khi advice chạy.

```text
Build:  nhận diện điểm method execution phù hợp
        -> chèn logic cần thiết vào woven output

Runtime: phương thức được gọi
        -> kiểm tra điều kiện runtime (nếu có)
        -> advice chạy nếu đúng
```

Đừng kết luận “lớp đã weave” nghĩa là advice luôn chạy trong mọi lần gọi. Một điểm có thể:

- Không bao giờ được thực thi;
- Có điều kiện runtime không thỏa;
- Thuộc loại join point không được công cụ hỗ trợ thật;
- Không nằm trong artifact đã triển khai;
- Hoặc được chọn theo kiểu tĩnh khác dự kiến.

**Matched scope, woven output và effective advice invocation** là ba điều phải kiểm chứng riêng.

### 11.1. Kế thừa và overload

Nếu lớp có `submit(String)` và `submit(String,int)`, pointcut theo tên chung có thể khớp cả hai tùy cú pháp. Chọn chữ ký hẹp khi chính sách chỉ dành cho một overload.

Với lớp con, bridge method do compiler sinh hoặc generic erasure, tên/kiểu nhìn ở source có thể khác tập phương thức mức bytecode. Cần dùng diagnostics của công cụ và test trên đường thực thi thật, không kết luận từ một biểu thức wildcard.

## 12. QA sau weaving: bảo toàn hợp đồng

Bộ dệt có thể tạo bytecode hợp lệ nhưng advice vẫn làm sai chương trình. Với aspect chỉ quan sát, cần kiểm tra:

- **Return:** giá trị trả về và `void`/`null`/primitive không bị thay sai.
- **Throw:** lỗi nghiệp vụ gốc đi tới người gọi; logging/cleanup không vô tình che lỗi.
- **Count:** gọi tiếp một lần nếu không có retry; không nhân đôi side effect.
- **Order:** before/after/around, precedence và finally đúng.
- **Scope:** điểm mong muốn được áp, điểm ngoài phạm vi không bị áp.
- **Concurrency:** dữ liệu từng lần advice không bị lưu vào trạng thái dùng chung không an toàn.
- **Async:** phương thức trả future chưa chắc future đã hoàn tất.
- **Security:** những đường bắt buộc kiểm soát quyền không được bypass chỉ vì một class không được weave.

Đây là yêu cầu **chất lượng sản phẩm**, không chỉ kiểm tra cú pháp AOP.

## 13. Ma trận kiểm thử pipeline

| Tình huống | Điều cần chứng minh |
| --- | --- |
| Build sạch với aspect hợp lệ | Tạo woven output đúng |
| Build thiếu aspect bắt buộc | Bước kiểm chứng thất bại rõ ràng |
| Package sau weave | Artifact chứa bytecode mong muốn |
| Artifact cuối khi chạy | Advice được gọi tại điểm khớp |
| Method không khớp | Không chạy advice |
| Overload tương tự | Khớp chính xác theo chính sách |
| Trả về bình thường | Không đổi kết quả nếu chỉ theo dõi |
| Ném ngoại lệ | Không nuốt/che lỗi |
| Hai aspect cùng áp | Thứ tự đúng |
| Source không đổi, aspect đổi | Cache invalidation đúng |
| Weave hai lần ngoài ý muốn | Phát hiện hoặc ngăn cấu hình lỗi |
| Thiếu runtime dependency | Smoke test phát hiện lỗi |
| Thay toolchain/JDK | Kiểm tra tương thích bytecode |
| Lớp ngoài phạm vi weaving | Không tuyên bố đã được can thiệp |
| Giao dịch hoặc retry | Không tạo tác dụng phụ lặp ngoài hợp đồng |

## 14. Những lỗi thường gặp

| Sai lầm | Hậu quả | Cách xử lý |
| --- | --- | --- |
| Tưởng có file aspect là đủ | Không có bytecode đã kết hợp | Gọi đúng compiler/weaver |
| Xem build success như advice success | Không biết advice chạy thật không | Test trên artifact cuối |
| Package original class thay vì woven | Chạy thiếu chính sách | Ràng buộc đường input/output |
| Unit test trước weaving thay integration test | Không kiểm tra advice | Thêm woven integration test |
| Cache không theo dõi aspect/config | Hành vi cũ sau khi sửa | Khai báo đủ task inputs |
| Weave lại output ngoài ý định | Có thể gây advice trùng | Kiểm soát một nguồn woven output |
| Quên thư viện runtime của advice | Lỗi nạp lớp/linkage | Rà runtime dependencies |
| Pointcut rộng theo tên | Khớp nhầm overload/phần nội bộ | Lọc chữ ký và phạm vi |
| Không xét thứ tự aspect | Đo/retry/transaction sai | Xác minh precedence |
| Đồng nhất CTW với proxy | Hiểu sai đường bypass | Phân biệt công nghệ và join point |
| Chỉ test đường thành công | Không thấy ngoại lệ bị che | Test throw, finally, retry |
| Dùng CTW cho chính sách bảo mật nhưng không đo coverage | Có thể bỏ sót đường truy cập | Kiểm tra điểm bắt buộc và đường đi vòng |

## 15. Checklist áp dụng vào một dự án

1. Xác định loại **join point** cần can thiệp và xác minh tool hỗ trợ.
2. Đặt **aspect, pointcut, advice** trong bộ input chính thức của task CTW.
3. Khóa phiên bản compiler/weaver, target JVM và dependency quan trọng.
4. Nêu rõ **phạm vi lớp được weave**, include/exclude và precedence.
5. Khai báo phụ thuộc build để output woven là điều kiện cho test/package.
6. Không trộn original và woven classes trong artifact.
7. Đảm bảo thay aspect/config làm build gia tăng cập nhật output.
8. Chạy test dương/âm, normal/throw, thứ tự và tác dụng phụ.
9. Chạy smoke test trên đúng JAR/image dự kiến triển khai.
10. Kiểm tra runtime library của bytecode đã weave.
11. Lưu định danh artifact và cấu hình build để tái lập/chẩn đoán.
12. Có cách triển khai/rollback phiên bản khi advice mới gây lỗi.

## 16. Bài tập tự kiểm tra

**Bài 1.** Một bộ dệt chạy lúc 10:00 trong CI, phương thức khớp pointcut chạy lúc 10:30. Khi nào weaving và advice xảy ra?

**Đáp án:** Weaving diễn ra lúc build 10:00; advice chạy lúc 10:30 khi join point xuất hiện và điều kiện được thỏa.

**Bài 2.** Compiler tạo `classes-original`, weaver tạo `classes-woven`, nhưng packaging lấy bản original. Vì sao không thấy advice?

**Đáp án:** Artifact triển khai không chứa output đã kết hợp. Cần ràng buộc task packaging lấy woven classes và test artifact cuối.

**Bài 3.** Mã nghiệp vụ giữ nguyên, pointcut được mở rộng; incremental build lại giữ output cũ. Nguyên nhân?

**Đáp án:** Bước weaving có thể thiếu định nghĩa aspect/config trong tập input dùng để invalidation cache. Phải khai báo đúng phụ thuộc.

**Bài 4.** Bộ test trên base class pass trước weaving. Có thể khẳng định advice không làm đổi ngoại lệ hay không?

**Đáp án:** Không. Cần test trên output đã weave, gồm đường trả về, ném lỗi và những điểm ngoài pointcut.

**Bài 5.** Vì sao method call join point trong mã `OrderService` không đồng nghĩa với method execution join point ở `PaymentService`?

**Đáp án:** Hai điểm ở hai vị trí/loại thao tác khác nhau. Weaver phải xử lý đúng mã chứa loại điểm mong muốn; chỉ tên phương thức không đủ.

**Bài 6.** Vì sao `Timer(Retry(Operation))` có thể cho kết quả khác `Retry(Timer(Operation))`?

**Đáp án:** Với Timer ngoài, có thể đo toàn chuỗi retry; với Timer trong, có thể đo mỗi attempt. Cần xác minh thứ tự aspect thực tế.

**Bài 7.** Một class đã weave, nhưng advice chưa chạy. Có phải build sai?

**Đáp án:** Chưa chắc. Có thể join point chưa được thực thi hoặc điều kiện runtime không thỏa. Cần test đúng đường gọi trước khi kết luận.

**Bài 8.** CTW có bảo đảm tự chặn mọi lệnh ghi field trực tiếp không?

**Đáp án:** Không. Khả năng ấy phụ thuộc mô hình field-set join point, phạm vi mã được xử lý và công cụ dệt thật.

## 17. Ghi nhớ

- **Compile-time weaving** kết hợp aspect khi tạo bytecode; **advice thực thi khi join point xảy ra ở runtime**.
- Đầu vào gồm **base code, aspect, pointcut/advice, thông tin kiểu, dependency, compiler/weaver và cấu hình**.
- Đầu ra là **woven class/artifact**, không nhất thiết sửa Java source.
- **Compile-time** khác **post-compile/binary**, **load-time** và **runtime proxy**; mỗi cơ chế có đầu vào, đầu ra và phạm vi riêng.
- Pipeline phải bảo đảm **đúng output woven được kiểm thử, đóng gói và triển khai**.
- Cache/incremental build phải phụ thuộc aspect/config, không chỉ mã nền; tránh double weaving và thiếu runtime dependency.
- Không công nhận AOP đã hoạt động đúng chỉ vì build xanh: cần xác minh **defined → selected → woven → executed**, gồm test dương/âm và bảo toàn hợp đồng nghiệp vụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="weaving-lifecycle-002">Dệt sau biên dịch trên mã trung gian hoặc thư viện có sẵn</a>

<details>
<summary>Xem chi tiết</summary>

# Dệt sau biên dịch trên mã trung gian hoặc thư viện có sẵn

**Post-compile weaving (dệt sau biên dịch, binary weaving)** là kỹ thuật trong **lập trình hướng khía cạnh (Aspect-Oriented Programming – AOP)**: một bộ dệt (*weaver*) nhận **artifact đã được biên dịch**, xác định các **join point** mà **pointcut** lựa chọn, rồi tạo artifact mới có hành vi **advice** tại những điểm phù hợp. Với Java, đầu vào tiêu biểu là thư mục chứa tệp `.class` hoặc tệp thư viện `.jar`; các nền tảng khác có thể sử dụng mã trung gian (IL/IR) riêng.

Ý nghĩa “sau biên dịch” chỉ **thời điểm biến đổi artifact**. Nó **không** có nghĩa advice bắt buộc chạy sau thân nghiệp vụ: khi chương trình chạy, bộ weaver có thể đã bổ sung mã cho **before**, **after**, **around** hoặc loại advice tương ứng mà công cụ hỗ trợ.

Ưu điểm đáng chú ý là có thể dệt trên **thư viện đã tồn tại** mà không phải sửa từng tệp nguồn. Tuy nhiên, kỹ thuật này phải bảo đảm bytecode hợp lệ, hợp đồng phương thức không bị biến đổi trái ý, artifact được nạp đúng, và các quy tắc về chữ ký, quyền thay đổi thư viện, license và bảo mật được tuân thủ.

## 1. Phân biệt các thời điểm weaving

| Hình thức | Đầu vào | Thời điểm kết hợp advice | Lưu ý |
| --- | --- | --- | --- |
| **Compile-time weaving** | Nguồn chương trình/aspect và compiler được tích hợp | Trong quá trình biên dịch | Có thể sử dụng nhiều thông tin nguồn; cấu hình compiler là một phần build |
| **Post-compile weaving** | Class file, JAR hoặc biểu diễn đã biên dịch | Sau compiler, thường trước khi triển khai | Dùng binary có sẵn, không nhất thiết cần mã nguồn lớp đích |
| **Load-time weaving** | Class file lúc loader chuẩn bị định nghĩa lớp | Trong quá trình nạp lớp | Phụ thuộc agent/loader, cấu hình runtime và lớp có được nạp qua đường can thiệp hay không |
| **Proxy/wrapper runtime** | Đối tượng và lời gọi đi qua lớp đại diện | Khi tạo và sử dụng lớp bọc | Có thể không biến đổi binary của target; lời gọi bypass proxy thường không bị chặn |

Một post-compile pipeline khái niệm:

~~~text
Sources / precompiled library
             |
             v
      Compilation (if needed)
             |
             v
       UNWOVEN .class / .jar
             |
             +---------- Aspect definitions / compiled aspects
             |                       |
             v                       v
             [POST-COMPILE BINARY WEAVER]
                           |
                           v
                WOVEN .class / .jar
                           |
                           v
                Verify / test / publish
                           |
                           v
                JVM runs woven classes
~~~

Nếu thư viện bên thứ ba đã phát hành `payment-library.jar`, có thể bắt đầu ngay tại bước **unwoven JAR**, sau đó tạo **woven JAR** riêng. Dệt vào file binary **không đồng nghĩa** được phép phân phối lại hoặc chỉnh sửa thư viện bất kỳ; phải có quyền và tuân thủ license.

### 1.1 Weaving khác với advice execution

Trong build, weaver **thêm hoặc tổ chức mã**. Trong runtime, advice **được thực thi khi join point xảy ra**.

Ví dụ một advice `before` được dệt vào class hôm nay sẽ chạy **trước phương thức đích** mỗi khi phương thức đó được thực thi trong môi trường có tải bản class đã dệt. Việc dệt diễn ra từ trước không làm advice chạy một lần tại thời điểm build.

Không nhầm giữa:
- **Build event:** weaver phát hiện một execution join point.
- **Runtime event:** phương thức được gọi và advice thật sự chạy.
- **Telemetry event:** bản ghi mà advice gửi ra console/collector; không nhất thiết cùng số lần nếu có cache, retry, sampling hoặc lỗi.

## 2. Join point và pointcut sau biên dịch

Một class file không giữ toàn bộ nội dung văn bản nguồn. Do đó, pointcut của binary weaver phải dựa vào **dữ liệu nó có thể nhìn thấy** và quy tắc của mô hình AOP.

Các nguồn thông tin thường gồm:
- Class hierarchy, interface, tên lớp/phương thức và kiểu khai báo.
- JVM method/field **descriptor** đã được compiler tạo.
- Attributes có trong class file, ví dụ thông tin source line, generic signature hoặc annotation phù hợp.
- Các vị trí **call**, **execution**, field access hay điểm khác **nếu** mô hình cho phép.
- Một số điều kiện runtime về đối tượng và tham số, nếu weaver có thể tạo mã kiểm tra tương ứng.

Các khái niệm cần phân biệt:

**Join point** là loại điểm chương trình có thể can thiệp; **pointcut** chọn subset của những điểm đó. **Advice** là xử lý chạy ở điểm được chọn. **Weaving** đưa cách gọi/chạy advice vào biểu diễn thực thi. Không phải mọi instruction trong bytecode đều trở thành join point, và không phải mọi dạng join point đều được mọi bộ AOP hỗ trợ.

Ví dụ pointcut “chọn execution của `BillingService.pay`” khác pointcut “chọn call tới `pay`”:
- **Execution** liên quan đến việc thực thi phần thân được công cụ hỗ trợ.
- **Call** liên quan đến vị trí bên gọi phát sinh lời gọi; chữ ký/ngữ cảnh caller có thể khác execution.
- Nếu một công cụ chỉ chặn lời gọi qua proxy, không thể suy rằng nó đã sửa execution bytecode của mọi lớp.
- Nếu một công cụ chọn cả hai loại điểm, một thao tác có thể làm xuất hiện nhiều sự kiện can thiệp ở các vị trí khác nhau.

## 3. Mã trung gian sau compiler không giống hoàn toàn mã nguồn

### 3.1 Class file Java và JAR

Tệp `.class` theo đặc tả JVM gồm cấu trúc lớp, constant pool, phương thức, trường, descriptors, attributes và bytecode. Tệp `.jar` là gói lưu trữ thường chứa **nhiều class file và tài nguyên**. Khi binary weaver nhận JAR, nó phải quyết định những entries nào biến đổi và những entries nào bảo toàn.

Các khác biệt đáng chú ý:
- Comment/formatting nguồn không còn hữu dụng cho weaving trên class file.
- Tên biến cục bộ hoặc source-line có thể vắng nếu compiler không xuất thông tin debug.
- Annotation chỉ sẵn có theo retention và vị trí lưu metadata.
- **Generic type erasure** làm descriptor JVM khác cách đọc generic ở nguồn.
- Compiler có thể tạo **bridge/synthetic methods**, adapter hoặc cấu trúc phụ trợ khác.
- Lambda và các cấu trúc ngôn ngữ mới có biểu diễn đặc biệt, cần toolchain tương thích.

Vì vậy, một pointcut được mô tả bằng tham số generic `T` không tự bảo đảm có thể khớp trực tiếp với descriptor Java đã xóa kiểu.

### 3.2 Generic và bridge method

Ví dụ mảnh khai báo Java:

~~~java
interface Handler<T> {
    T handle(T value);
}

final class TextHandler implements Handler<String> {
    @Override
    public String handle(String value) {
        return value.toUpperCase();
    }
}
~~~

Đây là ví dụ khai báo **chưa phải chương trình độc lập có `main`**. Với generic `T` không ràng buộc, JVM có thể thấy descriptor hợp đồng dựa trên `Object` sau erasure. Compiler có thể sinh phương thức cầu nối (**bridge**) để bảo toàn dispatch từ giao diện tới `handle(String)`.

**Có bridge trong Reflection hoặc bytecode không có nghĩa mô hình AOP buộc phải coi bridge là một execution join point riêng.** Một số công cụ cố ý loại bridge/synthetic khỏi tập điểm cần weave, hoặc chuẩn hóa chúng theo nghiệp vụ. Vì vậy không kết luận một lời gọi sẽ kích hoạt hai advice chỉ dựa trên việc thấy hai method.

### 3.3 IR ở nền tảng khác

Một nền tảng dùng IL hoặc LLVM IR có metadata, kiểu, cách tối ưu và verifier riêng. Post-compile weaving ở đó vẫn mang nghĩa **biến đổi artifact trung gian sau một bước biên dịch**, nhưng không thể dùng công cụ `javap` hay quy tắc JVM descriptor để kết luận cho mọi hệ thống.

**Nguyên lý AOP** có tính tổng quát; công cụ và cấu trúc binary thì phụ thuộc nền tảng.

## 4. Các bước của binary weaver

Một pipeline có thể được phân tích thành các trách nhiệm sau:

1. **Nhận đầu vào**: class directory/JAR hoặc IR đã biên dịch, phiên bản compiler và artifact provenance.
2. **Phân tích kiểu**: classpath/module path, hierarchy, annotations, descriptors và metadata cần để so khớp.
3. **Tìm tập join point được hỗ trợ**: method execution, call, field access hoặc điểm khác trong mô hình.
4. **Đánh giá pointcut**: điều kiện chữ ký/cấu trúc và điều kiện runtime cần chèn mã kiểm tra.
5. **Tổng hợp advice**: đưa before/after/around, precedence, exception paths và helper vào chương trình.
6. **Phát ra artifact mới**: lớp đã biến đổi, tài nguyên và metadata đi kèm.
7. **Xác minh**: bytecode verifier, bài test nghiệp vụ, log weave, khả năng rollback và phân phối.

Các bước có thể được tối ưu hoặc kết hợp khác nhau, nhưng những trách nhiệm ấy là điều cần xem xét khi thiết kế pipeline.

~~~text
Read binary
    |
    v
Resolve hierarchy / signatures
    |
    v
Select supported join points
    |
    v
Match pointcuts
    |
    v
Compose advice and preserve control flow
    |
    v
Emit woven artifact
    |
    v
Verify bytecode + runtime behavior
~~~

Một weaver không đủ thông tin để giải quyết kiểu có thể bỏ sót pointcut hoặc báo lỗi. Vì thế **đúng classpath và dependencies** là yêu cầu quan trọng, không phải chỉ cần đưa file JAR đích vào công cụ.

## 5. Thực hành Java 17: trước và sau khi dệt

Để tránh nhầm lẫn về khả năng của JDK, phần này tách:
1. **Chương trình Java 17 thuần, chạy được bằng JDK**.
2. **Bước binary weaving bằng công cụ AOP bổ sung**. Đây là ví dụ minh họa một triển khai cụ thể; cú pháp và setup khác nhau với các weaver khác.

### 5.1 Tạo binary nghiệp vụ chưa dệt

Tạo file `LibraryService.java`:

~~~java
public final class LibraryService {
    public String greet(String name) {
        System.out.println("LIB greet=" + name);
        return "Hi " + name;
    }
}
~~~

Tạo file `DemoApp.java`:

~~~java
public final class DemoApp {
    public static void main(String[] args) {
        LibraryService service = new LibraryService();
        String result = service.greet("An");
        System.out.println("APP " + result);
    }
}
~~~

Trên shell kiểu POSIX:

~~~bash
mkdir -p original woven
javac --release 17 -d original LibraryService.java DemoApp.java
java -cp original DemoApp
~~~

Trên Windows PowerShell có thể tạo thư mục bằng `New-Item -ItemType Directory -Force original,woven`; phần còn lại dùng lệnh JDK. Lệnh `--release 17` yêu cầu compiler có hỗ trợ target Java 17.

Stdout của **bản gốc**:

~~~text
LIB greet=An
APP Hi An
~~~

Không có dòng advice vì chưa hề thực hiện weaving. Nếu muốn xem lớp đã biên dịch:

~~~bash
javap -classpath original -c LibraryService
~~~

Lệnh `javap -c` đọc bytecode từ class file; danh sách opcode chi tiết tùy compiler và không cần giống hệt giữa mọi JDK.

### 5.2 Định nghĩa aspect minh họa

Ví dụ công cụ cụ thể sau sử dụng **AspectJ `ajc`** – một bộ compiler/weaver hỗ trợ binary weaving. Đây là **một hiện thực AOP**, không phải cú pháp dùng chung cho mọi công cụ. Không cần sửa `LibraryService.java`.

Tạo file `TraceAspect.aj`:

~~~java
public aspect TraceAspect {
    before(): execution(String LibraryService.greet(String)) {
        System.out.println("AOP BEFORE");
    }

    after() returning: execution(String LibraryService.greet(String)) {
        System.out.println("AOP AFTER");
    }
}
~~~

Aspect chọn method **execution** `LibraryService.greet(String)`, in một dòng trước và một dòng khi return bình thường. Nếu phương thức ném ngoại lệ, `after() returning` không phải advice của nhánh lỗi.

### 5.3 Dệt trên thư mục class đã biên dịch

Cần có `ajc` phù hợp phiên bản Java 17 cùng thư viện runtime `aspectjrt.jar`. Chúng **không có sẵn trong JDK**. Giả sử đường dẫn runtime JAR đã được thiết lập trong biến môi trường `AJ_RT`, lệnh `ajc -version` chạy thành công và bộ công cụ hỗ trợ mức `-17`:

~~~bash
# Thiết lập AJ_RT theo nơi cài thực tế trước khi chạy.
ajc -17 -classpath "original:$AJ_RT"     -inpath original     -d woven     TraceAspect.aj

java -cp "woven:$AJ_RT" DemoApp
~~~

Trên Windows, dùng dấu `;` thay dấu `:` ở classpath và viết dòng lệnh theo cú pháp PowerShell. `-inpath original` là điều mấu chốt: bộ dệt nhận **.class đã có sẵn** thay vì biên dịch lại source nghiệp vụ. Output được phát riêng vào `woven`.

Stdout **kỳ vọng khi weaving thành công**:

~~~text
AOP BEFORE
LIB greet=An
AOP AFTER
APP Hi An
~~~

Hai dòng AOP là kết quả advice thực thi ở runtime. Hai dòng nghiệp vụ gốc giữ nguyên giá trị trong ví dụ. Các bước JDK thuần đã có stdout xác định; riêng đầu ra đã dệt là **kết quả được mô tả theo cấu hình `ajc` nêu trên**, không được xem là đã kiểm nghiệm ở máy khi chưa chạy bộ công cụ bổ sung.

### 5.4 Dệt từ JAR đã phát hành

Đóng gói binary gốc (bước JDK):

~~~bash
jar --create --file library-app-unwoven.jar -C original .
~~~

Sau đó đưa JAR vào cùng bộ weaver:

~~~bash
ajc -17 -classpath "original:$AJ_RT"     -inpath library-app-unwoven.jar     -outjar library-app-woven.jar     TraceAspect.aj

java -cp "library-app-woven.jar:$AJ_RT" DemoApp
~~~

`-inpath` nhận thư mục hoặc JAR, `-outjar` ghi một JAR mới. Tùy mô hình, có thể sử dụng **binary aspect** qua một đường aspect riêng thay vì cung cấp source aspect. Lệnh `-injars` đã lỗi thời trong tài liệu công cụ này, nên dùng `-inpath`.

**Không ghi đè JAR gốc** chỉ để “tiết kiệm tên file”. Giữ hai artifact riêng, biết chính xác bản nào đã weave, bản nào dùng để đối chiếu.

## 6. Khả năng làm việc với thư viện có sẵn và giới hạn

### 6.1 Điều có thể làm

- Dệt các join point thuộc phạm vi của thư viện **khi tool hỗ trợ và binary tương thích**.
- Áp dụng advice chung mà không sửa từng phương thức nghiệp vụ.
- Tạo bản thư viện mới cho môi trường cần logging/metrics/audit đặc biệt.
- Kiểm thử hành vi mới dựa trên class/JAR đầu vào được version hóa.
- Tổ chức step weaving trong CI/build pipeline để runtime tải artifact đã được kiểm chứng.

### 6.2 Điều không thể tự suy

- Không có source **không** nghĩa công cụ khôi phục được mọi tên tham số, generic metadata hoặc annotation ở cấp nguồn.
- Dệt vào file trên đĩa **không** khiến class đã nạp sẵn trong JVM đang chạy tự thay đổi.
- Mã do runtime sinh **sau pass dệt** không tự chịu post-compile weaving của pass đó.
- Dệt được một method execution **không** có nghĩa công cụ cũng dệt được mọi field access, native method hoặc constructor.
- Nếu thư viện dùng class loader riêng, cần xác minh runtime đang nạp **bản woven**, không phải bản gốc cùng tên.
- Tồn tại `Class.forName`, Reflection hoặc proxy trong ứng dụng không chứng minh binary weaver sẽ quan sát được mọi đường gọi ấy.
- Tool biến đổi được bytecode **không** chứng minh được phép đổi hoặc phát hành lại thư viện thương mại/bên thứ ba.

### 6.3 Binary weaving và proxy-based AOP

Với proxy, lời gọi trực tiếp tới target có thể bỏ qua lớp đại diện. Với weaving trực tiếp method execution trong lớp đã biên dịch, execution đó có thể được instrument tại lớp đích, nên đường gọi khác proxy **có thể** vẫn đi qua advice.

Nhưng phạm vi thật còn phụ thuộc vào **lớp nào đã được weave**, pointcut chọn execution hay call, và biến thể binary mà runtime nạp. Một phương thức ở thư viện chưa được weave sẽ không tự được instrument chỉ vì ứng dụng có một aspect được khai báo.

## 7. Hợp đồng phương thức sau khi dệt

Advice được thêm vào không chỉ ảnh hưởng log. Nó có thể tác động tới:

| Hạng mục | Nguy cơ nếu weaving/advice sai |
| --- | --- |
| Giá trị trả về | Around advice trả sai kiểu hoặc sửa kết quả không được phép |
| Ngoại lệ | Lỗi gốc bị nuốt, bọc sai hoặc bị lỗi log che mất |
| Tác dụng phụ | Gửi sự kiện, ghi dữ liệu hoặc transaction xảy ra quá nhiều/ít lần |
| Bảo mật | Advice kiểm tra quyền không bao phủ mọi đường vào, hoặc bị cache ngoài short-circuit |
| Tài nguyên | `finally`/cleanup không được bảo toàn trên mọi nhánh |
| Đồng thời | I/O trong advice gây contention, deadlock hoặc race condition |
| Hiệu năng | Instrument quá nhiều join point nóng gây overhead |
| Thông tin nhạy cảm | Advice ghi toàn bộ arguments/return values vào log |
| Tracing và audit | Hai công cụ cùng instrument tạo sự kiện trùng hoặc thiếu |
| Tương thích | Thay đổi binary làm thiếu phương thức/metadata mà bên gọi kỳ vọng |

Nếu một around advice có quyền không gọi continuation, các advice và phương thức nằm bên trong nó **có thể không chạy**. Cần kiểm tra thứ tự precedence khi nhiều aspect cùng chọn một join point. Thứ tự xử lý tệp lúc build **không thay thế** quy tắc precedence ở runtime.

### 7.1 Kiểm tra quyền ở vị trí không thể bị bỏ qua

Giả sử hai chuỗi advice được ghép khác nhau:

~~~text
Authorize(Cache(Business))
Cache(Authorize(Business))
~~~

Trong trường hợp thứ hai, một cache hit ở lớp ngoài có thể trả kết quả **trước khi** advice phân quyền bên trong chạy. Nếu mọi lần truy cập phải được bảo vệ, đây là lỗi nghiêm trọng. Không đủ chỉ nói “đã dệt Authorization vào chương trình”; phải chứng minh thứ tự và phạm vi can thiệp đúng với mọi nhánh.

### 7.2 Đo thời gian có phạm vi nào?

~~~text
Timing(Transaction(Business))
Transaction(Timing(Business))
~~~

Hai vị trí đặt timer có thể cho hai định nghĩa latency khác nhau. Nếu timer bao ngoài transaction, số đo có thể gồm phần xử lý giao dịch; nếu nằm bên trong, số đo hẹp hơn. Tên metric phải mô tả đúng phạm vi, không lấy thứ tự dệt làm một chi tiết vô hại.

## 8. Thứ tự nhiều phép biến đổi

Một ứng dụng có thể dùng:
- Bộ dệt AOP.
- Bộ biến đổi bytecode để tracing/profiling.
- Công cụ coverage.
- Công cụ obfuscation.
- Bộ tạo mã hoặc bộ tối ưu hóa.

Khi đó, thứ tự xử lý ảnh hưởng **artifact nào được nhìn thấy bởi bước tiếp theo**.

~~~text
javac → generated bytecode → AOP weaver
      → coverage/profiler transform → packaging
~~~

Nếu obfuscator đổi tên phương thức trước khi pointcut theo tên được áp dụng, pointcut ấy có thể không còn khớp như lúc đọc source. Nếu một bộ transform tạo helper mới trước weaving, helper ấy **có thể** trở thành một ứng viên join point khi công cụ có hỗ trợ, nhưng không được mặc định mọi helper đều được weave.

Từ góc độ mô hình, với hai phép biến đổi `W` và `O`:

~~~text
W(O(P)) có thể khác O(W(P))
~~~

Chúng **không nhất thiết giao hoán**. Thứ tự biến đổi cũng không đồng nhất với thứ tự `before`/`after` giữa các advice sau khi chương trình chạy. Phải xác định thứ tự pipeline và precedence một cách độc lập.

### 8.1 Dệt lặp (reweaving) và idempotency

Nếu đầu ra của một pass weaving lại trở thành đầu vào của pass tiếp theo, không được mặc định:

~~~text
W(W(P)) == W(P)
~~~

Một số công cụ hỗ trợ reweaving bằng metadata hoặc giao thức riêng; một số có thể tạo mã chèn lặp hoặc báo lỗi. Không khẳng định “dệt hai lần chắc chắn chạy advice hai lần” cho mọi công cụ, nhưng phải kiểm chứng trước khi cho phép pipeline làm vậy.

### 8.2 Incremental build

Thay aspect hoặc pointcut có thể tác động nhiều lớp đã dệt, dù nguồn của lớp nghiệp vụ không đổi. Một build cache cần xét **hash input artifact, phiên bản aspect, weaver, pointcut và cấu hình** để tái tạo đúng các lớp chịu ảnh hưởng.

Nếu clean build và incremental build không tương đương về **join point được chọn và hành vi**, cần kiểm tra invalidation, classpath, nguồn input của pass sau và artifact woven cũ bị dùng lại.

## 9. Tương thích bytecode và JVM

Một weaver sửa class file phải tạo kết quả hợp lệ theo JVM verifier và runtime mục tiêu:

- Bytecode đúng kiểu toán hạng và thứ tự stack.
- Control flow hợp lệ ở nhánh thành công và lỗi.
- `try/catch/finally` và các bảng exception được cập nhật đúng.
- Stack-map frames/attributes phù hợp phiên bản class file nơi cần thiết.
- Method descriptor, visibility và interface contract không bị phá.
- Metadata cần cho Reflection/serialization hoặc các công cụ khác được xử lý có chủ đích.
- Runtime dependencies của advice hiện diện trên classpath hoặc module path.

Nếu không, chương trình có thể bị `VerifyError`, `ClassFormatError`, `NoSuchMethodError`, `IllegalAccessError` hoặc lỗi runtime khác dù tệp JAR đã được ghi thành công.

Đừng xem việc chèn vài opcode thủ công vào class file mà không hiểu **stack frames, constant pool và verifier** là kỹ thuật production an toàn. Nên dùng bộ weaver tương thích với phiên bản class file hiện tại và kiểm thử trên JVM thực sự sẽ triển khai.

## 10. JAR đã ký, giấy phép và nguồn gốc artifact

**Dệt vào thư viện có sẵn là thay đổi nội dung binary.** Một JAR đã có chữ ký số hoặc digest có thể không còn xác minh đúng sau khi class file bị sửa.

Quy trình tốt:

1. Xác minh **quyền thay đổi/phân phối** theo license và chính sách tổ chức.
2. Lưu artifact **chưa dệt** cùng thông tin phiên bản và hash.
3. Tạo bản **đã dệt** ở tên/đường dẫn khác, có build record rõ ràng.
4. Kiểm tra metadata chữ ký/digest. Nếu quy trình yêu cầu ký artifact, **ký mới bằng quy trình được ủy quyền**, không giữ nhãn gây hiểu nhầm rằng bản đã sửa vẫn là file nguyên gốc.
5. Ghi lại phiên bản compiler, JDK, weaver, aspect, classpath và cấu hình pointcut.
6. Duy trì artifact provenance/SBOM, quét lỗ hổng và kế hoạch rollback khi áp dụng.
7. Kiểm tra thư viện được phân phối đến runtime thực sự là bản woven đã được xác minh.

Một JAR còn có manifest, service provider metadata, tài nguyên, multi-release entries và có thể các tệp ngoài class file. **Không được chỉ kiểm tra vài class rồi mặc định toàn bộ JAR đã được bảo toàn**.

### 10.1 Multi-release JAR

Một multi-release JAR có thể chứa các biến thể class cho nhiều phiên bản Java. Nếu weaver chỉ biến đổi class ở đường cơ bản nhưng runtime thực tế chọn entry `META-INF/versions/17`, advice mong đợi có thể không chạy. Cần xác minh công cụ hiểu cấu trúc JAR này và những class **runtime thực sự chọn** đã được biến đổi đúng.

### 10.2 Module system và class loader

Binary weaving không tự cấp quyền reflective access hoặc mở các module không cho truy cập. Nếu aspect cần truy cập member không công khai hoặc metadata nội bộ, phải xem quy tắc visibility, module boundaries và công cụ weaving hỗ trợ những gì.

Hai lớp cùng tên được load bởi các class loader khác nhau có thể là những runtime types khác nhau. Không thể bảo đảm advice được áp dụng chỉ vì nhìn thấy đúng tên class trong thư mục build.

## 11. Kiểm thử trước khi phát hành

Một test suite nên kiểm chứng **cấu trúc lẫn hành vi**.

| Tình huống | Điều cần kiểm tra |
| --- | --- |
| Bản chưa dệt | Kết quả nghiệp vụ baseline và ngoại lệ gốc |
| Bản đã dệt | Advice chỉ xuất hiện ở join point được chọn |
| Lời gọi thành công | Giá trị trả về và tác dụng phụ theo hợp đồng |
| Lời gọi ném lỗi | Exception propagation và cleanup không đổi trái ý |
| Many-advice precedence | Đúng thứ tự vào/ra, không short-circuit lớp bắt buộc |
| Direct calls, proxies, self-calls | Biết đường nào được weaver bao phủ |
| Generic/bridge/synthetic | Không đếm trùng hoặc bỏ sót do chữ ký khác |
| Tải từ JAR | Runtime chọn woven artifact, không phải bản gốc |
| Chạy nhiều luồng | Không sinh race, deadlock, contention không chấp nhận |
| Metrics/logging | Không in args, response hoặc thông tin nhạy cảm tùy tiện |
| Trước/sau obfuscation | Pointcut match đúng trên biểu diễn thực tế |
| Clean vs incremental | Kết quả weaving và hành vi tương đương |
| JAR metadata/signature | Các tài nguyên, manifest và tính toàn vẹn được quản lý |
| Performance | Overhead đo được và đáp ứng ngân sách |

Trong ví dụ Java 17, có thể kiểm tra artifact woven:

~~~bash
jar --list --file library-app-woven.jar
javap -classpath library-app-woven.jar -p -c LibraryService
java -cp "library-app-woven.jar:$AJ_RT" DemoApp
~~~

Các lệnh giúp thấy danh sách file, bytecode và output runtime, nhưng **không có một lệnh đơn lẻ đủ để chứng minh toàn bộ ngữ nghĩa AOP đúng**. Nếu classpath còn thư viện khác, cần bổ sung đầy đủ các dependency và dùng phép thử tích hợp thực.

## 12. Những sai lầm thường gặp

1. **“Post-compile nghĩa advice chạy sau method.”** Không: đây là thời điểm dệt, không phải loại advice.
2. **“Phải có source code của lớp đích.”** Binary weaving có thể dùng class/JAR đã biên dịch.
3. **“JDK tự weave aspect khi chạy javac.”** Không: ví dụ dùng riêng `ajc`; `javac` chỉ tạo bản gốc.
4. **“Mọi method trong Reflection đều có execution join point.”** Không: mô hình AOP có giới hạn.
5. **“Bridge tồn tại tức sẽ có hai lần advice.”** Không suy được chỉ từ danh sách phương thức.
6. **“Dệt JAR rồi có thể tiếp tục dùng chữ ký số cũ như chưa sửa.”** Không: nội dung đã biến đổi cần kiểm tra integrity/ký lại được ủy quyền.
7. **“Dệt lên file dependency trong cache là tiện nhất.”** Sai: dễ gây mất provenance và làm bẩn build input.
8. **“Chỉ cần output JAR tồn tại là hoàn thành.”** Chưa đủ; cần verifier/linkage, pointcut và business tests.
9. **“Runtime chắc chắn nạp JAR woven vì tên file gần giống.”** Chưa đủ; kiểm tra classpath/class loader thực tế.
10. **“Cứ dệt lại để chắc chắn, không sao.”** Phải hiểu reweaving/idempotency.
11. **“Binary weaver luôn có mọi thông tin nguồn.”** Không: metadata có thể thiếu hoặc bị obfuscate.
12. **“Weaver postcompile nhìn thấy code runtime mới sinh sau build.”** Không trong một pass đã kết thúc.
13. **“Đổi thứ tự các bytecode tools không ảnh hưởng kết quả.”** Có thể ảnh hưởng signatures, pointcut và mã được chèn.
14. **“License không liên quan nếu chỉ sửa bytecode.”** Sai: quyền sửa/phân phối thư viện cần được tôn trọng.

## 13. Checklist triển khai

- [ ] Xác định bản **unwoven** làm đầu vào và lưu riêng.
- [ ] Kiểm tra license/quyền sửa binary, class/JAR format và compiler target.
- [ ] Chọn bộ weaver tương thích phiên bản JVM và đúng loại join point.
- [ ] Xác nhận **inpath**, classpath/module path, runtime library và aspect definitions.
- [ ] Hiểu pointcut match static signatures, generic erasure, bridge và mã sinh thế nào.
- [ ] Hạn chế phạm vi advice và quản lý precedence.
- [ ] Xác minh các công cụ obfuscation, coverage, tracing và weaving không xung đột.
- [ ] Xây **woven artifact riêng**, không ghi đè dependency nguyên gốc.
- [ ] Kiểm tra metadata, chữ ký số, multi-release JAR và service files.
- [ ] Chạy lại business tests, nhánh lỗi, cleanup, bảo mật, hiệu năng.
- [ ] Kiểm tra runtime dùng bản woven, không phải class chưa dệt ở nơi khác.
- [ ] Có build provenance, clean/incremental comparison và rollback.

## 14. Câu hỏi tự luyện

**Câu 1.** Có một JAR chỉ chứa compiled classes, không có source trong môi trường build. Có thể dùng post-compile AOP không?

**Đáp:** có thể nếu bộ weaver hỗ trợ bytecode ấy, có classpath đủ, có quyền thay đổi/phân phối và pointcut match join point được hỗ trợ. Không bảo đảm mọi loại thao tác đều weave được.

**Câu 2.** Advice `before` đã được dệt vào method execution. Nó chạy lúc biên dịch hay lúc method được thực thi?

**Đáp:** việc **đưa mã advice vào artifact** xảy ra ở bước dệt; hành vi before thực sự **chạy lúc join point xảy ra** trong JVM nạp bản đã dệt.

**Câu 3.** Tại sao thêm advice vào thư viện đã biên dịch có thể thất bại dù bytecode tồn tại?

**Đáp:** không tương thích phiên bản class file, thiếu type/dependency, pointcut không match, loại join point không hỗ trợ, metadata/annotation cần thiết thiếu hoặc code được load từ bản khác.

**Câu 4.** Chỉ thay JAR trên đĩa khi JVM đang chạy có làm class đã nạp dùng bytecode mới ngay không?

**Đáp:** không. Cần cơ chế triển khai/nạp lại hoặc cơ chế cập nhật runtime được hỗ trợ, không thể suy từ thao tác thay file.

**Câu 5.** Vì sao cần giữ cả artifact trước và sau weaving?

**Đáp:** để đối chiếu join points, kiểm chứng bytecode, hỗ trợ rollback, phân biệt bug weaving với bug nghiệp vụ và kiểm soát artifact provenance.

**Câu 6.** Nếu pointcut dựa vào tên phương thức nguồn mà obfuscator đổi tên trước khi weave, điều gì có thể xảy ra?

**Đáp:** pointcut có thể không còn khớp; cần hiểu thứ tự biến đổi, metadata và mapping của toolchain.

**Câu 7.** Vì sao không nên log toàn bộ `args` trong advice mới dệt vào thư viện bên thứ ba?

**Đáp:** có thể chứa password, token hoặc dữ liệu cá nhân và phát tán tới các hệ thống log; cần allowlist, data minimization và kiểm thử bằng dữ liệu giả.

## 15. Tham khảo

- [AspectJ — Load-Time Weaving Guide](https://eclipse.dev/aspectj/doc/latest/devguide/ltw.html): phân biệt các thời điểm compile-time, post-compile và load-time weaving.
- [AspectJ — ajc Compiler/Weaver](https://eclipse.dev/aspectj/doc/latest/devguide/ajc.html): công cụ minh họa binary weaving `-inpath`, `-aspectpath`, `-d`, `-outjar`.
- [JVM Specification, Java 17 — Class File Format](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html): cấu trúc class file.
- [Java SE 17 — javap](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): kiểm tra bytecode và method descriptors.
- [Java SE 17 — JAR File Specification](https://docs.oracle.com/en/java/javase/17/docs/specs/jar/jar.html): cấu trúc, chữ ký và multi-release JAR.

**Kết luận:** Post-compile weaving cho phép **dệt vào mã trung gian hoặc thư viện đã biên dịch**, tạo artifact mới có advice tại các join point được hỗ trợ mà không bắt buộc sửa mã nguồn nghiệp vụ. Độ chính xác của kỹ thuật phụ thuộc vào **cấu trúc binary, classpath, pointcut, khả năng weaver, thứ tự biến đổi và artifact mà runtime nạp**. Hãy kiểm chứng bằng bytecode inspection, business tests và quy trình phát hành tin cậy; không đánh đồng khả năng sửa binary với sự an toàn hay quyền được phân phối binary đã sửa.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="weaving-lifecycle-003">Dệt khi nạp và quan hệ với cơ chế nạp lớp hoặc mô-đun</a>

<details>
<summary>Xem chi tiết</summary>

# Dệt khi nạp và quan hệ với cơ chế nạp lớp hoặc mô-đun trong AOP

**Dệt khi nạp (load-time weaving, LTW)** là cách đưa xử lý của **aspect/advice** vào mã chương trình khi các lớp được JVM nạp/định nghĩa, thay vì yêu cầu toàn bộ bytecode đích đã được biến đổi từ bước biên dịch. Cách tiếp cận này đặc biệt hữu ích khi một mối quan tâm cắt ngang phải áp dụng cho nhiều lớp hoặc thư viện, nhưng nó gắn chặt với **vòng đời nạp lớp, class loader, Java module, thời điểm gắn transformer và phạm vi class thật sự được xử lý**.

Một hiểu lầm dễ gặp là: “Chỉ cần khai báo aspect hoặc bật agent thì mọi phương thức được dệt.” Trên thực tế, một advice chỉ hoạt động tại join point nếu **mã chứa điểm đó đã được can thiệp theo cơ chế được hỗ trợ**, kết quả dệt phù hợp với JVM, và runtime dùng đúng phiên bản lớp được xử lý.

Đối với một class chưa được nạp, transformer có thể được gọi trước khi class được JVM định nghĩa. Đối với một class **đã được định nghĩa**, câu chuyện khác: cơ chế nạp thông thường không chạy lại chỉ vì ta thay cấu hình. Nếu muốn can thiệp về sau, cần xét **retransformation/redefinition hoặc một đường interception khác**, với nhiều giới hạn riêng.

> **Trọng tâm:** Phân biệt *load-time weaving* với *load-time class observation*, hiểu trình tự nạp–liên kết–khởi tạo lớp, xác định class loader và Java module ảnh hưởng tới weaving như thế nào, và biết cách kiểm chứng một class thực sự đã được dệt. Bài trình bày AOP cơ bản và nâng cao nói chung, không phụ thuộc framework cụ thể.

## 1. Ôn lại mô hình AOP: từ pointcut đến mã chạy

Trong AOP:

- **Join point:** vị trí/sự kiện mà công cụ có thể can thiệp, chẳng hạn method call hoặc method execution nếu mô hình AOP hỗ trợ.
- **Pointcut:** biểu thức/quy tắc chọn những join point mong muốn.
- **Advice:** hành vi trước, sau, khi lỗi hoặc bao quanh một join point.
- **Aspect:** thành phần đóng gói pointcut và advice cho một mối quan tâm cắt ngang.
- **Weaving:** quá trình kết hợp hành vi aspect với chương trình để advice thực sự được áp dụng.

```text
Aspect / advice / pointcut       Class bytecode chưa dệt
             |                          |
             +------------+-------------+
                          |
                       Weaver
                          |
                          v
              Class bytecode sau dệt
                          |
                      JVM thực thi
                          |
                Advice + hành vi gốc
```

Điều kiện pointcut **đúng** chưa đủ để chứng minh advice sẽ chạy. Phải trả lời thêm: **loại join point được hỗ trợ không, class chứa điểm đó có nằm trong phạm vi biến đổi không, bytecode đã được biến đổi chưa, và JVM có đang dùng đúng class được dệt không?**

Một số AOP được thực hiện bằng **proxy/interceptor** tại ranh giới lời gọi thay vì sửa bytecode của class đích. Đó là cách can thiệp hợp lệ ở mức ý tưởng AOP, nhưng **không nên tự gọi nó là load-time weaving của method body**. Bài này đặt trọng tâm ở quá trình **biến đổi hoặc chuẩn bị bytecode khi class được nạp**, sau đó so sánh với proxy để tránh nhầm phạm vi.

## 2. Các thời điểm dệt: LTW nằm ở đâu?

| Thời điểm | Điều được xử lý | Ưu điểm điển hình | Ràng buộc chính |
| --- | --- | --- | --- |
| **Compile-time weaving** | Source/class trong lúc biên dịch | Có thể phát hiện nhiều lỗi lúc build | Build cần đúng compiler/toolchain và aspect |
| **Post-compile / binary weaving** | `.class` hoặc JAR đã được biên dịch | Không nhất thiết phải sửa source target | Artifact sau dệt phải được đóng gói và chạy thật |
| **Load-time weaving** | Class bytes khi runtime định nghĩa lớp | Có thể can thiệp theo tập class được nạp | Transformer phải được cài đúng lúc; phụ thuộc loader/module/JVM |
| **Runtime proxy/interceptor** | Lời gọi đi qua proxy/hook | Không nhất thiết đổi bytecode của target | Direct call, self-invocation hoặc phương thức ngoài hook có thể bỏ qua |

Bảng mô tả các nhóm cơ chế, **không bảo đảm mọi engine có đầy đủ khả năng tương đương**. Ví dụ, một LTW engine có thể hỗ trợ cả call lẫn execution join point, trong khi engine khác chỉ hỗ trợ một phần. Việc phương thức là `private`, `static`, `final`, synthetic hay nằm ở thư viện khác cũng phải xem theo giới hạn engine.

### Sơ đồ thời điểm

```text
Source code
    |
    +-- compile-time weaving --> .class đã dệt
    |
    +-- javac -----------------> .class gốc
                                  |
                                  +-- post-compile weaving --> .class đã dệt
                                  |
                                  +-- JVM / loader -----------+
                                                              |
                                                         LTW transform
                                                              |
                                                         define class
                                                              |
                                                           execution

Proxy/interceptor: bao quanh đường gọi ở runtime, không tự sửa .class gốc.
```

**Điểm khác biệt thiết yếu của LTW:** việc kết hợp advice xảy ra trên đường chuẩn bị bytecode để JVM **định nghĩa class**. Nó không có nghĩa mỗi lần method được gọi, bytecode lại được weave từ đầu.

## 3. “Nạp class” là một quá trình có nhiều giai đoạn

Trong JVM, nên phân biệt các khái niệm:

1. **Loading:** JVM tìm/thu nhận biểu diễn nhị phân và tạo lớp tương ứng; class loader tham gia quá trình này.
2. **Verification:** kiểm tra các ràng buộc của class file và bytecode.
3. **Preparation:** chuẩn bị các biến static theo quy tắc JVM.
4. **Resolution:** chuyển các symbolic reference thành tham chiếu runtime; thời điểm thực hiện từng resolution có thể khác nhau theo JVM và tình huống.
5. **Initialization:** thực thi các static initializer/class initialization logic khi điều kiện khởi tạo xảy ra.

Các bước liên kết và khởi tạo không nhất thiết xảy ra cùng lúc với câu lệnh `new` mà ta nhìn thấy trong source. Java có **nạp và resolution theo nhu cầu**, còn JVM được phép có những lựa chọn thời điểm hợp lệ theo đặc tả.

Với một transformer kiểu `ClassFileTransformer` của Java Instrumentation, việc can thiệp xảy ra trong đường tạo/định nghĩa class, **trước khi bytecode đã biến đổi được sử dụng như class runtime**. Do đó, một transformer cần trả bytecode **hợp lệ**, với cấu trúc và tham chiếu phù hợp để bước xác minh/liên kết sau đó có thể thành công.

### Loading khác initialization

Ví dụ:

```java
// Mã minh họa, không phải chương trình độc lập.
Class<?> type = Class.forName(
        "example.business.InvoiceService",
        false,
        loader
);
```

Tham số `false` của overload `Class.forName(name, initialize, loader)` yêu cầu **không khởi tạo class chỉ vì lời gọi này**, nhưng vẫn yêu cầu tìm/nạp class theo contract API. Lệnh này hữu ích khi muốn khảo sát “đã load” mà chưa chạy static initializer.

Ngược lại, `Class.forName("...")` thông thường sử dụng semantics khiến class được khởi tạo nếu cần. Dù vậy, **thời điểm định nghĩa class không luôn trùng thời điểm initializer chạy**; một advice vào static initialization join point (nếu engine có) không giống transformation callback khi class được định nghĩa.

### Transformer không phải advice invocation

- **Transformer:** chạy khi class được xử lý; có thể nhận bytes và trả bytes mới.
- **Advice:** chạy mỗi khi một join point đã được kết hợp xuất hiện, theo cơ chế được triển khai.
- **Method invocation:** có thể xảy ra rất nhiều lần sau khi class được nạp một lần.

Một class được transform **một lần** vẫn có thể thực thi method **một triệu lần**; advice được chèn vào method execution có thể chạy một triệu lần tùy pointcut/luồng.

## 4. Kiến trúc LTW tổng quát

Một hệ thống LTW dựa trên instrumentation thường có ba nhóm thành phần:

**(1) Runtime hook:** một agent hoặc khả năng tương đương đăng ký transformer để nhận các class bytes trên đường định nghĩa class.

**(2) Weaver:** phân tích class/method metadata, đối chiếu pointcut, chỉnh sửa bytecode để gọi advice hoặc tạo cấu trúc trung gian phù hợp.

**(3) Runtime support:** các type và hàm mà bytecode đã biến đổi cần gọi khi thực thi, kèm cấu hình, khởi tạo aspect, quản lý context và quan hệ class loader/module.

```text
ClassLoader.loadClass(...)
          |
          v
  Tìm bytes của lớp
          |
          v
  Transformer được gọi
          |
          +-- Không thuộc phạm vi -> giữ nguyên bytes
          |
          +-- Có join point hợp lệ
                      |
                   Weaver
                      |
              Bytecode đã dệt
                      |
                  defineClass
                      |
              JVM verify / link
                      |
              Class có thể chạy
                      |
          Advice được gọi khi join point xảy ra
```

Đây là **sơ đồ nguyên lý**. Trật tự chi tiết, cách nhiều transformer được áp dụng và khả năng biến đổi class đã nạp phụ thuộc JVM/API instrumentation và công cụ weaving cụ thể.

### Phạm vi chọn class và phạm vi match pointcut

Một LTW engine thường cần hai lớp lọc khác nhau:

- **Class filter:** những class/package/module/loader nào được phép đi qua chi phí phân tích/dệt?
- **Join-point matcher:** trong class ấy, call site hoặc method execution nào thỏa pointcut?

Nếu class bị loại ngay ở filter, pointcut bên trong nó không được xét. Ngược lại, transformer có thể quan sát một class nhưng không tìm được join point phù hợp; trường hợp đó **không đồng nghĩa dệt thất bại**.

## 5. Java Instrumentation và `ClassFileTransformer`

Java SE cung cấp API `java.lang.instrument` để tạo agent và đăng ký transformer. Đây là **cơ chế nền tảng** cho một số giải pháp instrumentation/LTW trên JVM, không phải một ngôn ngữ AOP hoàn chỉnh.

Một agent khởi động cùng JVM có thể khai báo phương thức `premain`. Khi JVM chạy với tùy chọn `-javaagent:path/to/agent.jar`, agent được khởi tạo trước khi gọi ứng dụng `main` theo contract của Java Instrumentation.

Một transformer có thể nhận:

- `ClassLoader loader`: loader định nghĩa class, có thể `null` với bootstrap loader.
- `String className`: tên nội bộ kiểu `com/example/Service`, có thể có trường hợp đặc biệt theo API.
- `Class<?> classBeingRedefined`: thường `null` khi class **được load lần đầu**; có giá trị tương ứng khi redefinition/retransformation.
- `ProtectionDomain protectionDomain`: thông tin domain bảo vệ theo runtime.
- `byte[] classfileBuffer`: biểu diễn class file ban đầu hoặc đã được các transformer trước xử lý.
- Ở API hỗ trợ module: `Module module` cung cấp thông tin module liên quan tới class.

Một transformer thường **trả `null` để không thay đổi bytes**, hoặc trả **mảng byte mới** hợp lệ nếu nó thực hiện biến đổi. Việc in log trong transform là **quan sát quá trình load**, không phải dệt advice vào method.

### Ví dụ sơ đồ transformer thật

```java
// Pseudocode: weaveMethods không phải API có sẵn của Java SE.
byte[] transformClass(
        ClassLoader loader,
        String internalClassName,
        byte[] originalBytes) {

    if (!isAllowedByWeavingScope(loader, internalClassName)) {
        return null; // Không biến đổi.
    }

    return weaveMethods(originalBytes, configuredPointcuts);
}
```

Mã này mô tả trách nhiệm, **không phải chương trình Java chạy độc lập**. Để biến đổi bytecode an toàn, một engine cần giải quyết instruction offsets, stack map frames, constant pool, exception tables, verifier constraints và các tham chiếu runtime. Không nên chỉnh bytecode tùy ý theo chuỗi byte mà không hiểu class-file format.

## 6. Ví dụ Java 17+ chạy được: quan sát class lúc được nạp

Ví dụ này có một **agent** và một **ứng dụng**. Agent đăng ký `ClassFileTransformer` để phát hiện đúng lúc class `demo.app.Worker` được định nghĩa. Transformer **không sửa bytecode**: nó trả `null`.

**Tại sao dùng ví dụ “quan sát” thay vì giả vờ đã weave advice?** Java SE cung cấp hook instrumentation nhưng **không cung cấp sẵn một DSL pointcut hay một bộ dệt phương thức đầy đủ**. Ví dụ tập trung vào việc xác minh **thời điểm và phạm vi hook LTW**. Bước kế tiếp trong một engine AOP thật là dùng một bytecode weaver hợp lệ tại hook đó để chèn advice, được giải thích ở những mục sau.

### 6.1. Cấu trúc thư mục

```text
demo/
  src/
    demo/
      agent/
        LoadProbeAgent.java
      app/
        Main.java
        Worker.java
  agent.mf
```

### 6.2. `src/demo/agent/LoadProbeAgent.java`

```java
package demo.agent;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public final class LoadProbeAgent {
    private LoadProbeAgent() { }

    public static void premain(String options, Instrumentation instrumentation) {
        System.out.println("agent.premain");
        instrumentation.addTransformer(new Probe(), false);
    }

    static final class Probe implements ClassFileTransformer {
        @Override
        public byte[] transform(ClassLoader loader, String internalName,
                                Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain,
                                byte[] classfileBuffer) {
            if ("demo/app/Worker".equals(internalName)) {
                System.out.println("agent.transform demo/app/Worker");
            }
            return null; // Quan sát, không sửa bytecode.
        }
    }
}
```

`premain` chạy khi agent được kích hoạt tại JVM startup. `addTransformer(new Probe(), false)` đăng ký transformer với ý định chỉ quan sát đường load và **không khai báo transformer này hỗ trợ retransform**. Tham số `false` **không có nghĩa** toàn bộ JVM không hỗ trợ retransformation; nó chỉ cho biết transformer được đăng ký ở chế độ không hỗ trợ việc gọi lại kiểu đó theo API.

Matcher `"demo/app/Worker"` sử dụng **internal class name có dấu `/`**, khác với tên fully qualified dùng trong `Class.forName` là `"demo.app.Worker"`.

### 6.3. `src/demo/app/Main.java`

```java
package demo.app;

public final class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("app.main");

        Class<?> workerType = Class.forName("demo.app.Worker");
        Object worker = workerType.getConstructor().newInstance();
        workerType.getMethod("execute").invoke(worker);

        System.out.println("app.done");
    }
}
```

Ứng dụng cố ý dùng `Class.forName` **sau khi đã in `app.main`** để buộc yêu cầu nạp `Worker` tại một vị trí rõ ràng, tránh phụ thuộc vào việc JVM chủ động resolve một class được tham chiếu trực tiếp trong bytecode của `Main` vào lúc nào.

### 6.4. `src/demo/app/Worker.java`

```java
package demo.app;

public final class Worker {
    public Worker() { }

    public void execute() {
        System.out.println("worker.execute");
    }
}
```

### 6.5. `agent.mf`

```text
Premain-Class: demo.agent.LoadProbeAgent
```

Tệp manifest cần có một dòng kết thúc hợp lệ. Công cụ `jar` sẽ kết hợp nội dung manifest được cung cấp với metadata JAR cần thiết. `Premain-Class` là entry point cho agent được tải khi khởi động, **không phải Main-Class của ứng dụng**.

### 6.6. Biên dịch, tạo agent JAR và chạy

Tại thư mục `demo` chứa `src`, dùng các lệnh sau (chạy lần lượt):

```bash
mkdir -p classes
javac --release 17 -d classes \
  src/demo/agent/LoadProbeAgent.java \
  src/demo/app/Main.java \
  src/demo/app/Worker.java

jar --create --file load-probe-agent.jar \
  --manifest agent.mf -C classes demo/agent

java -cp classes demo.app.Main

java -javaagent:load-probe-agent.jar -cp classes demo.app.Main
```

Khối lệnh trên dùng cú pháp shell có ký tự nối dòng `\`, phù hợp môi trường Unix-like shell. Trên Windows PowerShell, có thể gõ mỗi lệnh trên một dòng để tránh khác biệt ký tự nối dòng; tùy chọn `javac`/`jar`/`java` vẫn giữ nguyên.

**Đầu ra khi chạy KHÔNG có agent:**

```text
app.main
worker.execute
app.done
```

**Đầu ra khi chạy CÓ agent:**

```text
agent.premain
app.main
agent.transform demo/app/Worker
worker.execute
app.done
```

Ví dụ đã được biên dịch với `javac --release 17` và chạy bằng JDK tương thích để đối chiếu hai đầu ra. Mã không yêu cầu thư viện AOP bên ngoài.

### 6.7. Đọc đúng ý nghĩa kết quả

1. `agent.premain` xuất hiện trước `app.main`: agent startup đã được kích hoạt.
2. `app.main` in trước khi gọi `Class.forName("demo.app.Worker")`.
3. Transformer thấy bytes của `demo/app/Worker` trên đường load, in `agent.transform ...`.
4. Transformer trả `null` nên **class vẫn dùng bytes không đổi**.
5. `worker.execute` chạy và chỉ in `worker.execute`; không có advice nào được chèn.
6. Khi không dùng `-javaagent`, `premain` và transformer không được đăng ký, nên không có log của agent.

**Quan trọng:** Dòng `agent.transform` chứng minh transformer **được gọi**, **không chứng minh weaving đã xảy ra**. Một bài kiểm thử weaving thật phải xác nhận mã đã biến đổi và advice thực sự chạy tại join point mong muốn.

### 6.8. Nếu muốn từ “quan sát” tiến tới “weaving” thật

Một weaver có thể thay nhánh:

```java
return null;
```

bằng logic sinh ra **bytecode hợp lệ có thêm lời gọi tới advice**, sau đó trả `byte[]` mới. Chẳng hạn về ý tưởng, thân `execute` có thể được biến đổi thành:

```java
// Pseudocode kết quả mong muốn, không phải mã mà demo agent tự sinh.
void execute() {
    TraceAdvice.before("demo.app.Worker.execute");
    try {
        originalExecuteBody();
    } finally {
        TraceAdvice.after("demo.app.Worker.execute");
    }
}
```

Một bytecode weaver thực tế phải bảo tồn semantics Java, return values, exception flow, verifier rules và đường gọi advice. Nó không thể đơn giản gọi `originalExecuteBody()` nếu method đó không tồn tại; engine phải tạo wrapper/di chuyển/chỉnh sửa instruction flow một cách hợp lệ. Sơ đồ chỉ nhằm giải thích **hiệu ứng cần đạt**, không phải khẳng định agent demo đã thực hiện nó.

## 7. Vì sao cần gắn transformer trước khi class đích được định nghĩa?

Giả sử:

```text
t0  JVM start
t1  Load class A
t2  Agent/transformer được đăng ký
t3  Load class B
t4  A.execute()
t5  B.execute()
```

Nếu A đã được định nghĩa ở `t1` mà transformer mới được đăng ký ở `t2`, **load-time callback thông thường không tự chạy lại cho A**. B được định nghĩa sau `t2` có thể đi qua transformer.

Từ đó:

- Một startup agent thường phải được cung cấp bằng `-javaagent` để được khởi tạo đủ sớm cho các class ứng dụng cần dệt.
- Cấu hình weaving có thể được nạp sớm, trước khi nhiều class mục tiêu được định nghĩa.
- Một đoạn code gọi class mục tiêu khi khởi tạo agent có thể **vô tình nạp target trước khi transformer sẵn sàng**.
- Class được load theo nhu cầu nên cần test ở cả startup path và những đường load muộn.
- Nếu class đã nạp, việc đổi file JAR hoặc đăng ký transformer mới **không tự biến đổi Class object đang sống**.

### Nạp quá sớm trong chính agent

Giả sử agent khởi tạo một helper, helper tham chiếu trực tiếp target business class, và JVM phải load target để giải quyết dependency. Nếu điều này xảy ra **trước `addTransformer`**, cơ hội dệt lần load đầu của class đó có thể đã mất.

Một phương án thiết kế là giữ logic `premain` tối giản:

1. Đọc cấu hình tối thiểu mà không chủ động chạm target.
2. Đăng ký transformer phù hợp.
3. Hoãn khởi tạo những helper có dependency rộng nếu việc ấy có nguy cơ load target.
4. Kiểm tra class thực tế được load sau khi transformer đã sẵn sàng.

Không phải mọi load muộn đều có vấn đề; vấn đề là **thứ tự tương đối giữa thời điểm định nghĩa class và thời điểm hook có hiệu lực**.

## 8. Đã nạp rồi thì sao: retransformation khác LTW thông thường

Java Instrumentation có khả năng liên quan đến:

- `Instrumentation.isRetransformClassesSupported()`: runtime có hỗ trợ cơ chế retransformation không.
- `Instrumentation.addTransformer(transformer, true)`: đăng ký transformer có khả năng tham gia retransformation khi được hỗ trợ.
- `Instrumentation.retransformClasses(...)`: yêu cầu xử lý lại một số class đã nạp, nếu được hỗ trợ và class phù hợp.
- `Instrumentation.isModifiableClass(...)`: kiểm tra một class có thể được biến đổi theo API tương ứng không.
- `Instrumentation.redefineClasses(...)`: cơ chế redefinition khác có tập ràng buộc riêng.

**Retransformation không phải “lần nạp class mới”.** Class object và các instance hiện có không đơn giản bị thay bằng một class vừa được define. JVM áp đặt các giới hạn thay đổi cấu trúc trong quá trình redefinition/retransformation; tùy API/JVM, không thể giả định được thêm/xóa method, field, thay class hierarchy hoặc đổi signature tùy ý.

### Hai phương án cần phân biệt

**Dệt lúc load lần đầu:** transformer tạo bytes phù hợp trước `defineClass`, khi class chưa được sử dụng như type runtime. Đây là load-time weaving theo nghĩa trực tiếp.

**Biến đổi class đã load:** dùng API bổ sung nếu môi trường hỗ trợ; cần kiểm tra modifiability và structural restrictions. Nó không phải một chiến lược đáng tin nếu ứng dụng tùy ý muốn đổi mọi đặc điểm của class giữa lúc đang chạy.

Nếu mục tiêu yêu cầu một cấu trúc bytecode không thể bổ sung sau load, hãy thiết kế agent và weaver để can thiệp **trước định nghĩa class**, hoặc dùng một ranh giới interception khác được hỗ trợ.

## 9. Class loader quyết định class được định nghĩa ở đâu

Một **class loader** tham gia việc tìm và định nghĩa lớp. Trong nhiều ứng dụng JVM có:

- **Bootstrap loader** cho các class nền tảng.
- **Platform loader** cho một tập module nền tảng.
- **Application/system loader** cho mã ứng dụng thông thường.
- **Custom loaders** dùng trong plugin architecture, container hoặc khi cần cô lập phiên bản thư viện.

Không nên mặc định mọi custom loader đều theo một chính sách parent-first giống nhau; điều quan trọng là **lớp đích được định nghĩa bởi loader nào** và transformer/runtime support có quan sát/truy cập được đường load đó không.

### Class identity: tên không đủ

Trong JVM, class có thể được phân biệt theo **tên nhị phân và defining class loader**. Hai class có tên `example.Service` nhưng do hai loader khác nhau định nghĩa có thể là **hai runtime types khác nhau**.

```text
PluginLoader A --defines--> example.Service  [Type A]
PluginLoader B --defines--> example.Service  [Type B]

Tên binary giống nhau, nhưng type identity không nhất thiết giống nhau.
```

Điều đó ảnh hưởng:

- Type matching của pointcut khi class cùng tên xuất hiện ở loader khác.
- Cache metadata theo `Class<?>` hoặc key class name.
- Các cast/reflection callback giữa advice và target.
- Khả năng nạp helper/advice class mà bytes đã dệt sẽ tham chiếu.
- Quy trình reload plugin: loader mới có thể tạo một thế hệ class khác.

### Class loader filter và pointcut filter

Một engine có thể cho phép chỉ weave class do loader cụ thể định nghĩa. Nhờ đó, nó tránh dệt nhầm thư viện framework/runtime hoặc plugin không thuộc chính sách. Nhưng nếu loader filter quá hẹp, target hợp lệ có thể không được instrument.

Đặt điều kiện chỉ theo **class name** cũng chưa chắc đủ khi nhiều loader có class trùng tên; thêm thông tin loader/module/artifact nếu công cụ hỗ trợ và yêu cầu có giá trị.

## 10. Vì sao bytecode đã dệt phải truy cập được advice runtime?

Giả sử weaver chèn vào `app.Service` một instruction tương đương:

```java
TraceRuntime.before("Service.run");
```

Khi JVM xác minh/liên kết/thực thi class đã dệt, nó phải giải quyết tham chiếu tới `TraceRuntime` theo quy tắc nạp lớp và truy cập phù hợp.

Một transformer có thể **nhìn thấy class bytes** nhưng bytecode nó tạo ra lại **không nạp hoặc gọi được advice runtime**. Kết quả có thể là lỗi liên kết/lỗi truy cập hoặc hành vi không mong đợi.

Vì thế phải bảo đảm:

1. Helper/advice class ở đường nạp phù hợp cho target loader.
2. Các type mà helper cần dùng có thể được resolve theo module graph.
3. Mã đã dệt không tham chiếu trái phép tới package/member không được truy cập.
4. Cơ chế khởi tạo helper không tạo vòng nạp hoặc recursion vào cùng transformer.
5. Nếu hỗ trợ nhiều class loader, mỗi loader có thể có **phiên bản helper riêng** hoặc phải chia sẻ một bản được thiết kế cẩn thận.

### Bootstrap classes là trường hợp đặc biệt

Không nên suy luận rằng một agent quan sát được application classes sẽ tự instrument an toàn mọi class lõi do bootstrap loader quản lý. Việc dệt các class nền tảng có thể làm vòng nạp phụ thuộc, thay đổi hiệu năng quan trọng, hoặc chạm tới giới hạn bảo mật/runtime. Các agent có thể cần cung cấp helper qua bootstrap search path hay chiến lược đặc biệt, nhưng đó là bài toán nâng cao phải kiểm thử rất kỹ.

**Giải pháp mặc định nên là phạm vi hẹp đúng nhu cầu**, không phải biến đổi toàn bộ JDK.

## 11. Class loading và JPMS: hai khái niệm liên quan nhưng khác nhau

**Java Platform Module System (JPMS)** là cơ chế tổ chức named modules. Một module có `module-info.java` khai báo quan hệ như:

```java
module com.example.business {
    exports com.example.business.api;
}
```

Một module sử dụng nó:

```java
module com.example.app {
    requires com.example.business;
}
```

Các khái niệm quan trọng:

- **Readability:** module nào được quyền đọc module nào.
- **Exports:** package public API nào cho phép truy cập biên dịch/runtime theo quy tắc JPMS.
- **Opens:** package nào cho phép deep reflective access ở runtime đối với module được chỉ định hoặc rộng hơn.
- **Module layer:** tập module đã resolve trong một cấu trúc runtime, có thể có nhiều layer.
- **Class loader:** cơ chế định nghĩa class; nhiều module có thể được định nghĩa bởi một loader, và một ứng dụng có thể dùng nhiều loader/layer.

**JPMS không phải weaver.** `requires` hoặc `exports` không tự gắn advice vào method. Chúng ảnh hưởng khả năng **code đã dệt** hoặc **helper phản chiếu** truy cập các type/member cần thiết.

### `exports` và `opens` không tương đương

| JPMS declaration | Mục đích chính | Không bảo đảm |
| --- | --- | --- |
| `requires other.module;` | Khai báo dependency/readability | Cho phép truy cập mọi package private trong module kia |
| `exports p;` | Công khai public API package `p` cho các module đọc được | Deep reflection tùy ý lên private member |
| `exports p to x;` | Export public API chỉ cho những module được nêu | Quyền của mọi module khác |
| `opens p;` | Cho phép reflective access sâu với package `p` ở runtime | Compile-time access tương đương `exports` |
| `opens p to x;` | Cho phép reflective access sâu có chọn lọc | Tự động dệt code trong package `p` |

Một agent hoặc weaver chỉ cần đọc **bytes** của class có thể có nhu cầu quyền khác với advice runtime sử dụng **Reflection** để gọi private method. Cần xác định lỗi thực tế thuộc **bytecode transformation**, **module readability**, **Java access control** hay **Reflection access**, thay vì tùy tiện mở mọi package.

## 12. Java 9+ cung cấp thông tin Module cho transformer

Trong Java Instrumentation hiện đại có phiên bản `transform` bổ sung tham số `Module`. Điều này hữu ích khi nhiều named module chứa các class có tên/package tương tự hoặc cần chọn phạm vi theo module.

Ví dụ **đoạn code minh họa thay thế hàm `transform` trong một transformer**, không phải file Java độc lập:

```java
@Override
public byte[] transform(
        Module module,
        ClassLoader loader,
        String internalName,
        Class<?> classBeingRedefined,
        java.security.ProtectionDomain protectionDomain,
        byte[] classfileBuffer) {

    if ("demo/app/Worker".equals(internalName)) {
        String moduleName = module != null && module.isNamed()
                ? module.getName()
                : "<unnamed>";
        System.out.println("module=" + moduleName);
    }

    return null;
}
```

Khi triển khai cụ thể, cần đảm bảo transformer tương thích với version Java mục tiêu và cách override API. `Module.getName()` trả `null` cho unnamed module; kiểm tra `isNamed()` giúp đặt nhãn có ý nghĩa.

### Module name chưa phải điều kiện pointcut đủ

Một class trong module `com.example.business` chưa chắc chứa join point phù hợp. Vẫn cần kết hợp:

- Module/loader/class filter.
- Type/method metadata.
- Loại join point (call hay execution).
- Điều kiện pointcut.
- Cấu hình runtime, nếu có.

Nếu chỉ log `moduleName` trong transform, bạn chỉ biết module nào đang được định nghĩa class, **chưa biết advice có được dệt vào từng method hay chưa**.

## 13. `Instrumentation.redefineModule` và quyền truy cập

Java Instrumentation cung cấp API để điều chỉnh một số quan hệ module đã nạp, chẳng hạn **bổ sung read edges, exports hoặc opens** cho các mục đích phù hợp trong runtime instrumentation.

Nhưng cần hết sức thận trọng:

- Không dùng API này như lời giải chung cho mọi lỗi weaving.
- Chỉ thêm quan hệ/module access thật sự cần cho code đã biến đổi hoặc helper.
- Không mặc định một lần sửa module graph tạo được khả năng instrument những class đã load trước đó.
- Thay module access không tự biến một method thành join point được dệt.
- Một bộ công cụ có thể cần xử lý lại class hoặc lập kế hoạch transformer riêng.

Ví dụ, nếu bytecode đã dệt trong module business tham chiếu `TraceRuntime` ở module agent, phải đảm bảo JVM có thể resolve và truy cập tham chiếu đó theo module graph. Nếu một reflection helper gọi private method, có thể phát sinh thêm nhu cầu opens. **Thêm reads không thay thế opens**, và opens không tự thêm mọi điều kiện matching hoặc weaving.

## 14. Named module và unnamed module

Class chạy trên **classpath** thường thuộc **unnamed module** của class loader tương ứng. Class chạy trên **module path** có thể thuộc **named module** được resolve.

Một agent được cấp bằng JAR không có module descriptor có thể chạy theo cơ chế module/classloader của Java agent, trong khi target lại thuộc named module. Điều này tạo ra những câu hỏi mà một ứng dụng classpath nhỏ không có:

- Target module có đọc được module chứa advice helper không?
- Helper package có cần được export tới target module không?
- Target có gọi được public/static entry point mà weaver chèn vào không?
- Annotation resolver có deep reflection vào package target không?
- Runtime agent có cách chuẩn để điều chỉnh module graph khi cần không?
- Class được nạp bởi loader của module layer nào?

Đừng chuyển một ứng dụng sang module path rồi suy luận **agent nhất định ngừng chạy**; cũng đừng cho rằng **agent chắc chắn có đủ quyền cho mọi reference đã dệt**. Phải kiểm chứng đúng mô hình runtime.

### Module layer và plugin động

Một ứng dụng có thể tạo module layer riêng để nạp plugin sau khi `main` đã bắt đầu. Nếu transformer vẫn được đăng ký và đường class-definition tương ứng được runtime instrumentation quan sát, các class được định nghĩa sau đó **có thể** được xử lý. Nhưng cần lưu ý:

- Class loader của layer mới có thể khác.
- Module graph/readability riêng.
- Cùng tên class ở layer/loader khác có thể là type khác.
- Helper mà class đã dệt gọi cần khả dụng trong layer/loader thích hợp.
- Cache matcher theo class name có thể sai khi plugin reload.

**Không coi module được resolve là mọi class của module đã được load.** JPMS resolve module graph và loading từng class là hai cấp khác nhau.

## 15. Phân biệt quan hệ Java module với quan hệ build module

“Dệt khi nạp module B” có thể bị hiểu nhầm là JVM định nghĩa toàn bộ class của B cùng lúc. Với JPMS, một named module có thể được resolve từ đầu nhưng **từng class vẫn được nạp khi cần**. Transformer làm việc với **class bytes**, không có bước mặc định “transform toàn bộ module một lần” chỉ vì module xuất hiện trong graph.

```text
Module graph đã resolve:
  app ---> business ---> storage

Class loading theo nhu cầu:
  app.Main
  business.OrderService
  storage.ConnectionFactory
  business.Helper
  ...
```

Một LTW engine có thể **lọc theo module** nhưng vẫn nhận callback cho **từng class** khi class được định nghĩa. Điều này khác **post-compile weaving** nơi một tool có thể mở cả JAR chứa nhiều class trước khi chạy chương trình.

### Nếu một module không được sử dụng

Nếu class của module B không bao giờ được nạp, transformer có thể không nhìn thấy những class đó. Điều đó không nhất thiết là lỗi: **không có class-definition event thì không có điểm để LTW xử lý class ấy**.

Một test chỉ kiểm tra startup path có thể bỏ sót class chỉ xuất hiện khi chạy tính năng ít dùng. Cần test cả những đường load muộn nếu coverage quan trọng.

## 16. Method call và method execution: loader của bên nào quan trọng?

Giả sử module `checkout` gọi method `invoice.create()` của module `billing`:

```text
Module checkout                      Module billing
  Checkout.submit()
        |
        +----- call site -----------> InvoiceService.create()
                                            |
                                         execution
```

Nếu pointcut chọn **method call join point** phát sinh từ `Checkout.submit`, bytecode cần dệt thường là **caller class trong checkout**, vì đó là nơi chứa instruction phát lời gọi.

Nếu pointcut chọn **method execution join point** của `InvoiceService.create`, bytecode cần dệt thường là **callee class trong billing**, vì đó là nơi chứa method body.

Với LTW:

- Transformer quan sát checkout class khi nó được nạp thì có thể instrument call site checkout nếu engine hỗ trợ call join point.
- Transformer quan sát billing class khi nó được nạp thì có thể instrument method execution billing nếu engine hỗ trợ execution join point.
- Chỉ nhìn thấy class của một phía **không tự instrument mọi điểm thuộc phía kia**.
- Việc aspect được định nghĩa trong module thứ ba `observability` **không quyết định** nơi bytecode phải được chỉnh.

### Quan sát ở caller chưa chắc tương đương execution ở callee

Một call site có thể được gọi nhiều lần nhưng không dẫn tới target execution vì lời gọi bị chặn hoặc argument evaluation ném lỗi trước khi vào method body. Ngược lại, một target execution có thể được gọi từ nhiều caller và có self-invocation. **Hai loại điểm có thể quan sát tập sự kiện khác nhau**, nên metric và coverage cần xác định ranh giới từ đầu.

## 17. Điểm khác giữa LTW và proxy đối với self-invocation

Xét:

```java
// Đoạn khai báo minh họa.
class PaymentService {
    void run() {
        validate(); // gọi nội bộ trên this
    }

    private void validate() { }
}
```

Một **proxy bên ngoài target** có thể intercept `proxy.run()`, nhưng lời gọi `this.validate()` bên trong target không quay ra proxy. Nếu `validate` là private, proxy dựa trên override/interface cũng không thể intercept trực tiếp như một public method.

Nếu **bytecode của `PaymentService` được weave** tại execution `validate` hoặc call site trong `run` và engine hỗ trợ loại điểm ấy, nó có thể áp dụng advice cho lời gọi nội bộ. Nhưng điều kiện đó **không tự có chỉ vì đã bật agent**:

1. Class phải nằm trong weaving scope.
2. Transformer phải áp dụng đúng lúc và tạo bytes hợp lệ.
3. Pointcut phải khớp join point.
4. Engine phải hỗ trợ can thiệp ở private method/call site tương ứng.
5. JVM phải dùng bản class đã được dệt.

Do đó **LTW có thể giải quyết những lỗ coverage của proxy trong một số mô hình**, nhưng không nên phát biểu “LTW luôn bắt mọi self-invocation” nếu chưa xác minh công cụ.

## 18. Nhiều transformer và thứ tự biến đổi

Một JVM có thể có **nhiều java agents** hoặc nhiều `ClassFileTransformer` được đăng ký. Mỗi transformer có thể quan sát class bytes ở một giai đoạn phù hợp của chuỗi transformation.

Điều này tạo ra các câu hỏi:

- Transformer A nhận bytes gốc hay bytes đã qua B?
- Hai transformer có cùng sửa một method không?
- Một transformer có thể làm mất thông tin mà transformer sau cần để match pointcut không?
- Có một transformer tạo bytecode mà JVM không verify được không?
- Nếu cả hai chèn advice vào cùng method, thứ tự advice thực tế thế nào?
- Một class có thể bị weave hai lần do agent hoặc bước build khác không?

Java Instrumentation có các quy tắc cụ thể về thứ tự transformer dựa trên việc đăng ký và khả năng retransformation; cần xem contract API và cấu hình triển khai. Không nên dựa vào một thứ tự tùy ý trừ khi runtime/tool cam kết.

### Dệt trùng và reentrancy

Nếu method `save` bị dệt hai lần, một advice ghi audit có thể chạy hai lần cho một execution. Nếu advice tự gọi code cũng thỏa pointcut, có thể xảy ra recursion. Giải pháp thường liên quan **matching scope**, nhận diện bytecode đã dệt, loại trừ lớp runtime support hoặc thiết kế idempotency phù hợp, **không phải chỉ xóa log thừa**.

Đừng áp đặt một transformer “bao tất cả” mà không tính tới các transformer quan sát, profiler và công cụ instrumentation cùng chạy.

## 19. Bytecode hợp lệ: giới hạn của class transformation

Dệt advice không chỉ là “chèn một dòng Java” vào file class. Engine phải tạo ra cấu trúc class file đúng:

- Constant pool entries và symbolic references hợp lệ.
- Method descriptors và access flags phù hợp.
- Control-flow graph vẫn có nhánh/return/throw đúng.
- Exception handlers và `finally` tương đương phải bảo toàn semantics.
- Stack map frames, operand stack và local variables phải hợp lệ.
- Bytecode version phù hợp với JDK runtime.
- Không phá invariant của constructor/class initialization.
- Các type helper được resolve theo class loader/module access.
- Không làm sai đồng bộ hóa, exception propagation hay giá trị trả về.

Một lỗi ở một điểm có thể khiến class không load được với `VerifyError`, `ClassFormatError`, `NoClassDefFoundError`, `IllegalAccessError` hoặc lỗi liên kết khác tùy nguyên nhân. **Không phải lỗi nào trong nhóm này cũng bắt nguồn từ pointcut sai**.

### Lỗi ở lớp nào thì sửa lớp ấy

| Triệu chứng | Nhóm nguyên nhân cần kiểm tra |
| --- | --- |
| `premain` không in gì | Agent không được nạp, manifest/đường dẫn sai hoặc startup lỗi |
| Có `premain` nhưng không có transform target | Class chưa load, load quá sớm, filter loại, module/loader khác |
| Có transform nhưng không có advice | Transformer chỉ quan sát, pointcut không khớp, bytes trả `null`, hoặc engine bỏ qua method |
| Class load thất bại sau transform | Bytecode không hợp lệ, helper không resolve, quyền truy cập |
| Advice chỉ chạy trên một số đường gọi | Call vs execution, proxy bypass, variant implementation/loader |
| Advice chạy trùng | Dệt nhiều lượt, nhiều aspect, retry/proceed hoặc matcher quá rộng |
| Advice thấy context sai trên worker | Context propagation/scope, không nhất thiết do load-time weaving |

**Đầu tiên cần phân biệt “transform callback được gọi” với “bytes thực sự thay đổi” và “advice chạy tại join point”.**

## 20. Khởi tạo class và side effect do instrumentation

Khi transformer và helper được load, chúng có thể kích hoạt thêm các class khác. Nếu helper lại tham chiếu những class đang thuộc weaving scope, có thể gây chuỗi nạp phức tạp, recursion hoặc thứ tự khởi tạo khó đoán.

Nên giữ:

- Code bootstrap của agent tối giản.
- Transformer lọc sớm theo class name/package/module.
- Helper của weaver tách rõ khỏi đối tượng nghiệp vụ.
- Logging trong transformer đơn giản, tránh phụ thuộc vào chính code đang được weave.
- Không làm network I/O hoặc blocking nặng trên đường nạp class nếu không thật sự cần.
- Tính tới khả năng nhiều thread có thể yêu cầu nạp class và callback xảy ra ở các thread khác nhau.

Một aspect runtime có thể cần instance state, nhưng **khởi tạo aspect** và **biến đổi class** là các vòng đời khác nhau. Không nên suy từ việc transformer đã chạy rằng instance aspect đã được tạo và sẵn sàng cho mọi lời gọi.

## 21. Cache khi module hoặc class loader thay đổi

Một weaver có thể cache kết quả phân tích:

```text
(classLoader, className, module, configVersion) -> weave decision
```

Đây chỉ là **thiết kế key khái niệm**. Mức cache thực tế còn phụ thuộc bytecode digest, class version, pointcut set và các trạng thái nội bộ.

Nếu chỉ cache theo `className`, hai plugin loader cùng có `com.example.Service` nhưng khác bytes có thể bị dùng nhầm quyết định. Nếu cache giữ reference mạnh tới `ClassLoader` trong hệ thống plugin có unload/reload, nó có thể gây rò bộ nhớ.

Cần xem xét:

- Identity của loader, không chỉ tên.
- Module layer/version.
- Hash/fingerprint của bytes nếu nhiều biến thể cùng tên.
- Phiên bản pointcut và cấu hình.
- Chính sách invalidation khi plugin unload/reload.
- Giữ cache phù hợp với vòng đời loader.

**Một class loader có thể không được thu gom nếu vẫn có reference mạnh không cần thiết từ cache toàn cục**. Công cụ instrumentation có thể có chiến lược cache riêng; không bắt buộc mọi ứng dụng phải tự triển khai.

## 22. Cấu hình LTW có thể thay đổi sau startup không?

Có thể thay đổi cấu hình của một engine đang chạy, nhưng cần phân biệt:

**Quy tắc cho class sẽ load về sau:** nếu transformer đọc cấu hình mới lúc được gọi, class load sau đó có thể nhận cách biến đổi mới.

**Những class đã được định nghĩa:** không tự đổi chỉ vì pointcut được cập nhật. Muốn can thiệp vào chúng, engine cần cơ chế phù hợp (retransformation/redefinition, proxy reconfiguration hoặc restart/redeploy).

**Advice runtime:** một class đã dệt có thể gọi helper đọc cấu hình runtime động ở mỗi invocation. Khi đó **hành vi advice** có thể đổi mà **bytecode không đổi**, nhưng việc thêm một join point trước đây chưa được instrument không tự xảy ra.

Vì vậy có ba kiểu cập nhật khác nhau:

1. **Đổi điều kiện runtime** mà bytecode đã có sẵn khả năng kiểm tra.
2. **Đổi phạm vi weaving cho class sắp nạp**.
3. **Đổi bytecode của class đã nạp** bằng cơ chế động được phép.

Không gom cả ba thành một câu “LTW có hot reload”.

## 23. Phiên bản Java, module và agent compatibility

Một LTW engine có thể tạo bytecode không phù hợp JDK mục tiêu, hoặc dùng API/feature không tồn tại ở runtime cũ. Với Java 17+:

- Class file version phải được runtime hỗ trợ.
- Agent JAR manifest và `premain` phải hợp lệ.
- API `Instrumentation` có thể có khả năng optional; phải kiểm tra support thay vì chỉ gọi.
- Runtime module graph phải cho phép các reference/helper đã được weave.
- Class loader có thể khác giữa unit test và deployment.
- Build tool có thể chạy test bằng một JVM có `-javaagent` nhưng production không có, hoặc ngược lại.

Ví dụ demo đã biên dịch với `--release 17` để tránh phụ thuộc vào API mới hơn 17. Đối với bytecode weaver thực tế, cũng cần kiểm tra nó **hỗ trợ class files của JDK đang chạy** và không làm hỏng những class có đặc điểm compiler mới.

### Dynamic agent attachment không đồng nghĩa startup LTW

Một số Java runtime/công cụ cho phép agent được gắn vào JVM đang chạy và gọi entry point kiểu `agentmain`, nếu việc attach được hỗ trợ/cho phép. Khi ấy nhiều class mục tiêu có thể **đã được định nghĩa**. Phải xác định ranh giới hỗ trợ retransform/redefine; không suy luận dynamic attach có toàn bộ quyền của một startup agent trước class loading.

Các chính sách attach và cảnh báo JVM có thể thay đổi theo phiên bản và môi trường. Với một quy trình cần coverage tin cậy, thường phải **kiểm thử chính xác cách agent được triển khai ở môi trường chạy**.

## 24. AOP theo module: chọn nơi dệt theo join point

Một ứng dụng nhiều module:

```text
module checkout
    CheckoutFlow.submit()  ---- call ---->
module billing
    InvoiceService.create() ---- execution
module tracing
    TraceAdvice
```

Với **execution pointcut** của `InvoiceService.create`, transformer cần xử lý **class định nghĩa execution ở billing** nếu muốn bytecode weaving tại method này.

Với **call pointcut** phát ra từ `CheckoutFlow.submit`, transformer cần xử lý **class caller ở checkout**.

Với một proxy API ở checkout, code tracing có thể intercept lời gọi đi qua proxy mà không sửa bytecode billing; nhưng đường gọi khác vào billing có thể bypass.

Điều quan trọng không phải advice nằm ở module nào, mà là **join point được đặt ở đâu trong bytecode/đường gọi**, và module chứa điểm ấy có được runtime instrumentation quan sát không.

### Module được resolve nhưng class chưa load

Một module `billing` có thể đã được resolve trên module path. `InvoiceService` của nó chưa chắc đã được nạp. Khi class được sử dụng lần đầu, transformer có thể có cơ hội xử lý bytes. **Module resolution và class definition là hai sự kiện khác nhau**.

### Loader của advice khác loader của target

Weaver có thể hoạt động từ agent loader, nhưng code đã dệt trong billing chạy dưới target loader/module. Vì vậy phải kiểm chứng **caller của helper là class đã dệt**, chứ không chỉ agent tự gọi được helper. Không phải mọi cấu hình giúp agent nhìn thấy `TraceRuntime` đều giúp billing resolve được tham chiếu đến nó.

## 25. Security boundary: LTW không thay thế kiểm tra bắt buộc

Một aspect audit có thể được dệt trên nhiều class. Nhưng nếu engine loại trừ một module/plugin hoặc không xử lý class load sớm, một số đường thực thi có thể thiếu audit.

Với chính sách bảo mật:

- Không coi việc có agent là bằng chứng **mọi phương thức nhạy cảm** được can thiệp.
- Xác định điểm thực thi đáng tin cậy không có đường bypass.
- Có test cho class load sớm/muộn, multi-loader, module path và deployment.
- Xác minh đúng policy và thứ tự các advice nếu nhiều aspect cùng match.
- Không đưa bí mật, token hoặc dữ liệu cá nhân vào log transformer chỉ để debug.
- Không mở toàn bộ module cho reflection nếu một thay đổi truy cập hẹp hơn đủ giải quyết vấn đề.

Nếu security check là bất biến phải luôn đúng, việc thiết kế boundary bảo vệ cần độc lập với giả định “weaver chắc sẽ chạy”. AOP có thể là một cách triển khai hoặc lớp bổ sung, nhưng phải có chứng cứ coverage thích hợp.

## 26. Tương tác với asynchronous tasks và thread context

LTW xảy ra khi **class được nạp**, nhưng advice đã weave chạy tại **join point runtime**. Nếu một method được gọi từ worker thread hoặc callback:

- Class có thể đã được weave từ lâu, trước khi worker được tạo.
- Advice vẫn chạy ở worker nếu execution đi qua join point đã dệt.
- Dữ liệu `ThreadLocal` của caller không tự chuyển sang worker.
- Một pointcut dựa trên ngữ cảnh control-flow đồng bộ không tự nối qua executor/task.
- Method trả `CompletableFuture` có thể kết thúc trước khi công việc bất đồng bộ hoàn tất.

**Giới hạn class-loading coverage** và **giới hạn context propagation** là hai vấn đề riêng. Weaving đầy đủ vào method body không tự làm trace ID của thread A xuất hiện trên thread B.

Nếu cần đo end-to-end async, phải xác định join point lúc submit, lúc thực thi, lúc hoàn tất và cách liên kết chúng bằng context phù hợp.

## 27. Kiểm thử LTW: cần quan sát ba lớp chứng cứ

Một quy trình QA tốt phân biệt:

### (1) Agent/transformer đã được kích hoạt?

- `premain` có chạy không?
- Transformer có được đăng ký không?
- Runtime có gọi transformer cho class cụ thể không?
- Class được load ở module/loader nào?

**Chứng cứ:** log callback, diagnostics của instrumentation, số class đi qua filter.

### (2) Bytecode class đích có thực sự được biến đổi đúng?

- Transformer có trả bytes mới thay vì `null` không?
- Pointcut có tìm được join point không?
- Class sau biến đổi có thể verify/link không?
- Reference tới helper có resolve và truy cập được không?

**Chứng cứ:** diagnostics của weaver, snapshot bytes có kiểm soát, kiểm tra bytecode, test class loading. Không lưu/ghi thông tin nhạy cảm từ class/argument vào log.

### (3) Advice có chạy đúng tại đường nghiệp vụ?

- Method execution hoặc call site mong muốn thực sự xảy ra không?
- Advice chạy bao nhiêu lần so với target?
- Kết quả/ngoại lệ có giữ contract?
- Có đường bypass hoặc implementation khác chưa được weave?

**Chứng cứ:** test hành vi từ caller/target, test positive/negative, concurrency và failure paths.

Ba lớp này **không thể thay thế nhau**. Ví dụ demo ở trên chứng minh (1), cố ý **không thực hiện** (2) hoặc (3).

## 28. Ma trận kiểm thử class loading và module

| Trường hợp | Điều cần xác minh |
| --- | --- |
| Agent bật từ `-javaagent` | `premain` chạy trước ứng dụng |
| Không bật agent | Không có transformation callback |
| Target class load sau khi đăng ký | Transformer quan sát đúng class |
| Target class load trước khi đăng ký | Không tự nhận callback load lần đầu sau đó |
| Target class đã load, yêu cầu retransform | Runtime cho phép không; class có modifiable không |
| Một class không nằm trong scope | Không bị weave ngoài ý định |
| Một class đúng scope nhưng pointcut không khớp | Không chèn advice sai |
| Class nằm trong named JPMS module | Module identity và helper access chính xác |
| Class nằm trong unnamed module | Xử lý đúng module không tên |
| Custom class loader | Matching/cache theo loader phù hợp |
| Hai loader nạp cùng tên class | Không dùng nhầm type/bytecode/policy |
| Plugin/module layer load muộn | Transformer bao phủ class ở layer mới nếu được hỗ trợ |
| Helper nằm ở module khác | Class đã dệt có thể resolve/access helper |
| Package không `opens` | Phân biệt lỗi Reflection với lỗi weaving |
| JAR đã biến đổi nhưng runtime dùng bản gốc | Test phát hiện sai artifact/loader |
| Hai transformer cùng áp dụng | Không sinh bytes hỏng hoặc advice trùng |
| Một method private/static/final | Engine có thực sự hỗ trợ join point cần chọn? |
| Self-invocation | Test call site/execution theo cơ chế đã dùng |
| Caller ở module A, callee ở B | Phân biệt nơi có call và execution join point |
| Callback chạy ở worker thread | Advice coverage và context propagation là hai test riêng |
| Target ném exception | Advice giữ/thay outcome đúng contract |
| Thay cấu hình sau load | Behavior nào đổi mà không retransform? |

### Kiểm thử đúng với production configuration

Một test JVM có `-javaagent` không bảo đảm production startup cũng có flag đó. Một ứng dụng chạy từ IDE/classpath có thể khác khi deploy trên module path/container. Cần kiểm tra:

- JVM startup arguments đã thực dùng.
- Đường dẫn và manifest của agent JAR.
- Module path/classpath, loader/layer thực tế.
- Version JDK, bytecode weaver và class-file format.
- Class nào load sớm trong production nhưng muộn ở test.
- Việc bật/tắt các filter và cấu hình dệt.

## 29. Quy trình chẩn đoán khi advice không chạy

**Bước 1 — Kiểm tra agent.** Có đúng `-javaagent`? Manifest có `Premain-Class`? Agent startup có lỗi?

**Bước 2 — Kiểm tra transformer.** Đã đăng ký thành công? Có thấy class đích? Tên nội bộ đúng với `/` hay đang so chuỗi có dấu `.`?

**Bước 3 — Kiểm tra thời điểm.** Class đã được load trước khi transformer đăng ký chưa? Có dynamic attach nhưng thiếu retransform không?

**Bước 4 — Kiểm tra class loader/module.** Target thuộc loader và JPMS module nào? Có plugin layer hoặc nhiều class trùng tên không?

**Bước 5 — Kiểm tra scope.** Transformer có loại class do filter package/module/loader không? Điểm cần chọn nằm ở bytecode của caller hay callee?

**Bước 6 — Kiểm tra pointcut.** Join point là call, execution hay loại khác? Chữ ký, overriding, inherited method, annotation/generic bridge có khớp không?

**Bước 7 — Kiểm tra bytes.** Weaver có thật sự trả bytes đã biến đổi hay `null`? Có lỗi verify/link hoặc helper access không?

**Bước 8 — Kiểm tra thứ tự và các aspect khác.** Around advice ở ngoài có bỏ qua `proceed`, khiến target/inner advice không chạy không?

**Bước 9 — Kiểm tra đường nghiệp vụ.** Lời gọi có xảy ra không, bị proxy bypass, được gọi qua reflection, hay nằm trong worker/process khác?

**Bước 10 — Kiểm tra observability.** Advice đã chạy nhưng log/metric bị tắt hoặc sampling không? Tách “advice entered” khỏi “có stdout”.

Không nên thay `opens` hay thêm wildcard weave toàn bộ module ngay khi chưa phân loại lỗi.

## 30. Những sai lầm thường gặp

1. **“Agent in `premain` nghĩa là mọi class đều đã dệt.”** Chỉ là agent có cơ hội đăng ký transformer.
2. **“Transformer in tên class là advice đã chạy.”** Callback load khác runtime join point.
3. **“Trả `null` từ transformer là dệt thành công.”** `null` nghĩa là không thay bytes trong callback đó.
4. **“Class đã nạp sẽ được dệt khi đổi config.”** Không tự xảy ra nếu không có cơ chế động khác.
5. **“Retransformation như compile lại source.”** API có giới hạn cấu trúc/class modifiability.
6. **“Có named module tức mọi class đã load.”** Module resolution khác class loading.
7. **“`requires`/`exports` tự làm AOP hoạt động.”** JPMS quản lý quyền truy cập, không tự weave.
8. **“Thêm `opens` sửa được mọi vấn đề.”** Chỉ giúp một lớp vấn đề Reflection/access.
9. **“Agent thấy bytes nên target chắc gọi được advice helper.”** Vẫn cần class resolution và module access.
10. **“Cùng class name nghĩa là cùng runtime class.”** Defining loader cũng thuộc identity.
11. **“Một transformer luôn được gọi đúng một lần với mọi class.”** Ràng buộc retransform, loaders và pipeline có thể tạo nhiều sự kiện; cần xem contract.
12. **“LTW cho mọi method kể cả private/final.”** Phải kiểm tra engine và loại join point.
13. **“Call pointcut ở module A có thể thấy mọi call site trong B.”** Không nếu bytecode B không được instrument.
14. **“Execution pointcut ở B chỉ cần weave interface của B.”** Implementation method body có thể ở class khác.
15. **“Weaving đồng nghĩa với proxy.”** Proxy có đường interception riêng và có thể bypass.
16. **“Weave đầy đủ là ThreadLocal tự lan qua executor.”** Class transformation không tự truyền ngữ cảnh.
17. **“Hai agents độc lập sẽ không xung đột.”** Thứ tự, bytes và helper dependencies có thể tương tác.
18. **“Một test startup bao phủ cả plugin load muộn.”** Loader/module layer mới phải được kiểm thử.
19. **“Có advice rồi thì thay được mọi result/argument.”** Quyền thay dữ liệu còn tùy advice, join point và type contract.
20. **“Mất log đồng nghĩa thiếu weaving.”** Có thể advice chạy nhưng sink/metric bị tắt; phải có chứng cứ theo tầng.

## 31. Bài tập thực hành

1. Tạo đúng ba file Java và manifest trong ví dụ, chạy không có agent và có agent; giải thích hai output.
2. Thay matcher class name thành `"demo/app/Main"`. Có thể log transform của Main xuất hiện trước `app.main` không? Vì sao?
3. Thêm class `demo.app.Helper` và gọi `Class.forName` cho nó trước `Worker`; tạo bộ lọc quan sát cả hai class và phân tích thứ tự log.
4. Đổi `Class.forName` thành overload có `initialize=false`. Phân biệt load và initialization; thêm static initializer vào Worker để kiểm tra.
5. Cho agent tạo `new Worker()` **trước `addTransformer`** (đảm bảo app class trên agent classpath phù hợp). Dự đoán việc load class sớm ảnh hưởng hook ra sao và giải thích rủi ro của thiết kế này.
6. Sửa matcher để in thêm thông tin `loader == null`, class loader class/name nếu không null. Đừng dùng thông tin ấy như dữ liệu bảo mật.
7. Thêm phiên bản `transform(Module, ...)` hỗ trợ Java 9+, in `module.isNamed()` và `getName()` cho Worker.
8. Chạy ứng dụng classpath, sau đó tổ chức Main/Worker trong một named JPMS module, điều chỉnh biên dịch và quan sát module name.
9. Thiết kế mô hình hai module `checkout` và `billing`; xác định nơi bytecode cần được dệt nếu chọn call site ở checkout.
10. Với cùng hai module, xác định nơi cần dệt nếu chọn execution của `BillingService.create`, bất kể caller nào.
11. Vẽ timeline: transformer đăng ký ở t2, class A load t1, class B load t3. Có những class nào được can thiệp ở first-load callback?
12. Tìm contract của `Instrumentation.isRetransformClassesSupported()` và `isModifiableClass` trong JDK bạn đang dùng; vì sao kết quả này cần kiểm tra trước khi retransform?
13. Viết test cho hai class cùng tên qua hai custom loader để minh họa class identity và nguy cơ cache chỉ theo tên.
14. Tạo một helper class ở module khác; phân tích những read/export/open edge thực sự cần khi bytecode đã dệt tham chiếu helper.
15. Mô phỏng hai transformer: một chỉ log, một giả định sinh advice. Nêu ít nhất ba lỗi tiềm ẩn nếu cả hai cùng chỉnh một method.
16. Thiết kế test phân biệt “transform callback được gọi” với “bytecode đã sửa” và “advice thực thi”.
17. Một method được gọi từ worker trong executor: LTW có tự truyền trace ID từ `ThreadLocal` của caller không? Nếu không, phần nào phải xử lý riêng?
18. Một module plugin được load sau startup: liệt kê các giả định cần xác minh về transformer registration, class loader, module layer và advice helper.
19. Xây coverage matrix có ít nhất năm đường **phải có advice** và năm đường **không được có advice**, kiểm tra từng đường bằng runtime test.
20. Đưa ra quy trình phát hành nhằm đảm bảo production thực sự chạy với đúng agent JAR, config, module path và JDK version.

**Tự kiểm tra:** Khi thấy `agent.transform`, đã đủ kết luận code được dệt chưa? Vì sao `return null` không thay đổi bytecode? Module đã resolve có nghĩa toàn bộ class đã được load không? Tại sao class loader identity làm matching khác nhau? Nếu pointcut chọn call từ A và execution ở B, loại bytecode nào cần can thiệp? Vì sao retransform khác first-load weaving? `opens` có tự tạo advice không?

## Kết luận

**Dệt khi nạp là sự kết hợp AOP tại ranh giới định nghĩa class, không phải một lần gọi method hoặc một cờ bật toàn cục.** Để hiểu và triển khai đúng, cần nắm **loading–linking–initialization**, thời điểm đăng ký transformer, sự khác biệt giữa first-load và retransformation, phạm vi class loader, Java module readability/exports/opens, và khả năng resolve runtime helper. Một thiết kế LTW đáng tin cậy phải phân biệt **transform callback được gọi**, **bytecode thật sự được dệt** và **advice thực sự chạy tại join point**, đồng thời kiểm thử cả module/class loader, caller/callee, class load sớm/muộn, các đường bypass và cấu hình triển khai. Khi những ranh giới này rõ ràng, AOP mới có thể áp dụng nhất quán mà không đánh đổi sự chính xác của chương trình chỉ để đạt cảm giác “dệt được mọi nơi”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="weaving-lifecycle-004">Can thiệp lúc chạy và giới hạn của nền tảng</a>

<details>
<summary>Xem chi tiết</summary>

# Can thiệp lúc chạy và giới hạn của nền tảng trong AOP

> **Mục tiêu học tập:** Hiểu “can thiệp lúc chạy” trong lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP) có thể chỉ **lời gọi được chuyển qua một interceptor/proxy ở runtime**, **weaving khi lớp được nạp**, hoặc **instrumentation/biến đổi lớp trong JVM đang hoạt động**. Phân biệt các cơ chế đó với nhau và với weaving khi biên dịch; đánh giá giới hạn về join point, class loading, bytecode, module, phương thức, constructor, hành vi native, thread và deployment. Biết thiết kế ca kiểm thử chứng minh chính xác **đường nào được can thiệp, đường nào không**.

## 1. “Can thiệp lúc chạy” không phải một cơ chế duy nhất

Một chương trình Java có thể áp dụng mối quan tâm xuyên suốt — ghi log, đo thời gian, kiểm toán kỹ thuật, truy vết hoặc kiểm soát một ranh giới — bằng nhiều cách. Có người gọi tất cả chúng là “runtime AOP”, nhưng cách gọi đó che khuất khác biệt quan trọng.

Ba nhóm điển hình:

1. **Runtime interception bằng proxy/decorator/interceptor:** caller gọi một đối tượng trung gian. Đối tượng này chạy logic bổ sung, rồi có thể chuyển tiếp tới đối tượng đích. Không nhất thiết sửa bytecode của lớp đích.
2. **Load-time weaving (LTW):** một cơ chế xử lý bytecode **trước hoặc trong lúc lớp được định nghĩa/nạp** vào JVM; advice được dệt vào những vị trí phù hợp của lớp.
3. **Instrumentation đối với JVM đang chạy:** một agent hoặc cơ chế tương đương có thể quan sát lớp được nạp, biến đổi bytecode được phép, và trong một số điều kiện yêu cầu **retransformation/redefinition** các lớp đã nạp.

Ba nhóm có thể phối hợp, nhưng không có quyền năng giống nhau. Một proxy thấy **lời gọi đi qua proxy**; một bộ dệt bytecode cần **nhìn thấy và được phép xử lý lớp/mã liên quan**; instrumentation đối với lớp đã nạp chịu **ràng buộc VM, class file và chính API biến đổi**.

Câu hỏi đầu tiên không phải “có bật runtime AOP không?”, mà là:

> **Cơ chế nào thực sự nhận quyền điều khiển, tại ranh giới nào, và từ thời điểm nào?**

## 2. Nhắc lại các thành phần AOP liên quan

| Khái niệm | Câu hỏi nó giải quyết | Ví dụ |
|---|---|---|
| **Join point** | Loại điểm/sự kiện nào trong chương trình có thể được can thiệp? | Method execution, method call, field access, constructor execution nếu mô hình hỗ trợ |
| **Pointcut** | Những điểm nào thuộc tập cần áp dụng? | Chọn các method xử lý đơn hàng, trừ thao tác nội bộ |
| **Advice** | Hành vi nào được kết hợp và chạy ở thời điểm nào? | Ghi trước, sau, sau ngoại lệ, bao quanh |
| **Aspect** | Mối quan tâm xuyên suốt được tổ chức ở đâu? | Module quan sát thời gian xử lý |
| **Weaving** | Advice được kết hợp vào các điểm tương ứng thế nào? | Sinh/biến đổi mã hoặc cơ chế thích hợp khác |
| **Runtime path** | Khi chạy thật, chương trình có đi qua điểm can thiệp? | Gọi qua proxy thay vì gọi trực tiếp target |

Không có một **join point model** thống nhất cho mọi công cụ AOP. Một công cụ chỉ chặn các lời gọi method qua giao diện khác với công cụ có thể dệt vào **method call**, **method execution**, **field get/set** hoặc **constructor**.

Cần tránh hai suy luận sai:

- **“Pointcut đúng” không đồng nghĩa “advice sẽ chạy”:** lớp có thể chưa được dệt, lời gọi không đi qua proxy hoặc điểm chưa xảy ra.
- **“Advice không chạy” không đồng nghĩa “nghiệp vụ không xảy ra”:** caller có thể đã gọi trực tiếp đối tượng đích.

## 3. Phân biệt thời điểm dệt và thời điểm advice chạy

Trong mọi cơ chế AOP có advice, **advice có thể chạy ở runtime** khi join point xuất hiện. Điều đó không có nghĩa **weaving nhất thiết diễn ra ở runtime**.

~~~text
Compile-time weaving:
  source + aspects → weaving / build → class đã dệt → JVM chạy advice

Binary/post-compile weaving:
  class có sẵn + aspects → biến đổi bytecode → JVM chạy advice

Load-time weaving:
  JVM cần định nghĩa class → transformer/weaver → class đã dệt
                                            → JVM chạy advice

Runtime proxy/interceptor:
  tạo/chọn proxy → caller đi qua proxy → handler chạy
                                     → có thể gọi target

Runtime retransform/redefine (khi được hỗ trợ):
  JVM đã có lớp → yêu cầu biến đổi lại phù hợp → các lần chạy tương lai
                      có thể đi qua bytecode mới theo hợp đồng VM
~~~

Điều này tạo hai trục độc lập:

- **Khi nào kết hợp/chốt cơ chế can thiệp?** Compile, sau compile, class loading, sau khi lớp đã được định nghĩa, hoặc lúc tạo/chọn đối tượng trung gian.
- **Khi nào advice thực thi?** Khi một lần join point thực tế được quan sát và điều kiện tương ứng khớp.

Một method đã được dệt có thể không được gọi trong ca thử; không có advice chạy nhưng không phải lỗi weaving. Một proxy có thể được tạo thành công nhưng caller lại giữ tham chiếu target; handler vẫn không chạy.

## 4. Bảng so sánh các kỹ thuật can thiệp lúc chạy

| Thuộc tính | Proxy/interceptor | Load-time bytecode weaving | Retransform/redefine lớp đã nạp |
|---|---|---|---|
| **Ranh giới chính** | Lời gọi đi qua lớp trung gian | Điểm trong mã lớp được xử lý trước khi định nghĩa | Điểm trong phiên bản lớp có thể được cập nhật theo khả năng VM |
| **Cần sửa bytecode lớp đích?** | Không nhất thiết | Thường có | Có thể, nếu yêu cầu biến đổi thực sự |
| **Đối tượng gốc gọi trực tiếp** | Thường bỏ qua proxy | Có thể quan sát nếu mã tương ứng đã được dệt | Tùy phần mã đã thay đổi và join point model |
| **Class đã nạp** | Có thể tạo proxy mới nhưng caller phải dùng nó | Thường không tự động dệt lại class đã nạp | Chỉ khi VM, agent, class và phép biến đổi hỗ trợ |
| **Private/static/constructor/field** | Tùy loại proxy; nhiều loại không được bao phủ | Tùy mô hình join point và giới hạn weaving | Tùy join point model, bytecode và giới hạn VM |
| **Ràng buộc đáng chú ý** | Đường tham chiếu, dispatch, kiểu proxy | Class loader, transformer, class format, module | Quyền instrumentation, modifiability, schema class, trạng thái đang chạy |
| **Kiểu sự cố thường gặp** | Bỏ qua proxy, self-invocation, sai dispatch | Không xử lý đúng lớp, class version, transformer lỗi | Không được retransform, schema thay đổi, linkage/verification |

Bảng không phải đặc tả của một framework cụ thể. Nó giúp chọn **câu hỏi xác minh** trước khi thiết kế giải pháp.

## 5. Runtime proxy: nơi can thiệp là đường lời gọi

Một proxy là đối tượng nhận lời gọi ở phía trước đối tượng đích. Với Java, `java.lang.reflect.Proxy` cung cấp cơ chế tạo đối tượng triển khai các **giao diện** được chỉ định và chuyển lời gọi method giao diện tới `InvocationHandler`.

~~~text
client
  |
  | call service.execute()
  v
proxy implements Service
  |
  | InvocationHandler.invoke(...)
  v
target ServiceImpl.execute()
~~~

Nếu client giữ trực tiếp target:

~~~text
client ----------------------> target.execute()
                     (không đi qua handler của proxy)
~~~

Vì vậy **đường đi của tham chiếu** là một phần hợp đồng runtime interception. Một đối tượng `ServiceImpl` không tự biến mọi lời gọi lên nó thành lời gọi qua proxy chỉ vì một proxy cùng trỏ tới nó đã được tạo ra.

### 5.1. Self-invocation và lời gọi bên trong target

Một phương thức trên target có thể gọi tiếp `this.internal()`, `internal()` hoặc phương thức khác cùng đối tượng. Những lời gọi này **không tự quay ra proxy**.

Một handler bao ngoài phương thức `execute()` có thể ghi một sự kiện cho `execute()`, nhưng không có nghĩa mỗi method call bên trong target lại đi qua handler.

Đây là giới hạn của **kiểu can thiệp bằng đối tượng trung gian**. Một cơ chế weaving trực tiếp mã ở method execution hoặc call site có thể có độ bao phủ khác. Không nói “AOP luôn không chặn được self-invocation”.

### 5.2. Static, private, final không thể gộp thành một quy tắc

- **Static method** không được dispatch như method instance của giao diện proxy.
- **Private method** không phải một method mà proxy giao diện đại diện thông thường.
- **Final method** không thể override bằng subclass, nhưng điều đó không tự ngăn **proxy giao diện** chặn lời gọi ở ranh giới ngoài trước khi chuyển tới implementation.
- **Final class** không thể bị kế thừa; giới hạn này ảnh hưởng cách tạo subclass, nhưng không phải lập luận phủ định mọi loại weaving bytecode.

Kết luận luôn phải có dạng **“cơ chế X bị giới hạn bởi Y”**, không phải “mọi AOP đều không thể can thiệp loại method Z”.

## 6. Ví dụ Java 17 chạy được: proxy nhìn thấy gì và bỏ sót gì?

Chương trình dưới đây dùng Java Dynamic Proxy để minh họa:

1. Constructor của target chạy trước khi proxy được tạo.
2. Lời gọi qua proxy kích hoạt handler.
3. Lời gọi `this.finish()` từ nội bộ target **không tạo một lần intercept mới**.
4. Lời gọi trực tiếp target bỏ qua handler.
5. Static method không đi qua đường method giao diện của proxy.

**Đây là mô phỏng runtime interception bằng Java chuẩn, không phải chương trình biến đổi bytecode hoặc công cụ AOP tổng quát.**

Lưu file `RuntimeProxyDemo.java`:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class RuntimeProxyDemo {
    interface Service {
        void execute(String job);
        void ping();
    }

    static final class ServiceImpl implements Service {
        private int completed;

        ServiceImpl() {
            System.out.println("target constructor");
        }

        @Override
        public void execute(String job) {
            System.out.println("target execute " + job);
            finish();
            completed++;
        }

        @Override
        public void ping() {
            System.out.println("target ping");
        }

        private void finish() {
            System.out.println("target internal finish");
        }

        static void staticCheck() {
            System.out.println("target static");
        }
    }

    public static void main(String[] args) {
        ServiceImpl target = new ServiceImpl();

        Service proxy = (Service) Proxy.newProxyInstance(
                Service.class.getClassLoader(),
                new Class<?>[] {Service.class},
                (proxyObject, method, values) -> {
                    System.out.println("intercept " + method.getName());
                    try {
                        return method.invoke(target, values);
                    } catch (InvocationTargetException wrapped) {
                        throw wrapped.getCause();
                    }
                }
        );

        System.out.println("-- via proxy --");
        proxy.execute("A");
        proxy.ping();

        System.out.println("-- direct --");
        target.execute("B");
        ServiceImpl.staticCheck();

        System.out.println("completed " + target.completed);
    }
}
~~~

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac RuntimeProxyDemo.java
java RuntimeProxyDemo
~~~

**Stdout chính xác:**

~~~text
target constructor
-- via proxy --
intercept execute
target execute A
target internal finish
intercept ping
target ping
-- direct --
target execute B
target internal finish
target static
completed 2
~~~

### 6.1. Tại sao lần gọi `execute("B")` không có `intercept execute`?

Vì caller dùng `target`, không dùng `proxy`. Điều này không liên quan điều kiện pointcut phức tạp: handler **không nằm trên đường lời gọi đó**.

### 6.2. Tại sao `finish()` không có dòng intercept riêng?

`finish()` là method private được gọi trong target. Nó không phải một lời gọi method giao diện đi qua đối tượng proxy. Handler chỉ quan sát lời gọi `execute()` từ bên ngoài, còn code bên trong vẫn chạy.

### 6.3. Tại sao số lần nghiệp vụ là 2?

`completed` tăng trong **cả hai lần** `execute`. Nhờ vậy chương trình chứng minh “không có log intercept” khác với “nghiệp vụ không thực thi”.

### 6.4. Ngoại lệ qua reflection

`Method.invoke` có thể bọc ngoại lệ do method đích ném trong `InvocationTargetException`. Handler giải lớp bọc và ném lại nguyên nhân gốc. Đây là đặc điểm của adapter reflection dùng trong ví dụ, không phải cơ chế xử lý ngoại lệ bắt buộc của mọi weaver.

## 7. Load-time weaving: can thiệp vào lúc lớp được định nghĩa

**Load-time weaving** xử lý mã lớp trong quá trình JVM nạp/định nghĩa class theo cơ chế phù hợp. Một class transformer được gọi với dữ liệu class trước khi class được định nghĩa hoàn tất và có thể trả bytecode đã biến đổi.

~~~text
Application/class loader
         |
         v
Request: define/load Class X
         |
         v
Transformer/weaver receives class bytes
         |
         +---- no change → class bytes gốc
         |
         +---- transform → class bytes đã chỉnh hợp lệ
         |
         v
JVM defines/verifies/links class according to runtime rules
         |
         v
Execution of join points that were instrumented
~~~

Một lớp **không được nạp trong giai đoạn cơ chế còn có cơ hội transform**, hoặc bị loại khỏi phạm vi, có thể không nhận advice mà người viết mong đợi.

Vấn đề cần xác định:

- Cơ chế weaving được kích hoạt **trước hay sau** khi lớp quan trọng được định nghĩa?
- Class nào đi qua transformer nào?
- Class loader nào định nghĩa class đó?
- Pointcut có chọn những join point nằm trong mã thực sự đã biến đổi?
- Có lỗi class file hoặc quyền truy cập khi transformer chạy không?

### 7.1. Load-time không đồng nghĩa “mọi method được kiểm tra pointcut mỗi lần gọi”

Một weaver có thể quyết định theo cấu trúc lúc class loading và **chèn mã** cần thiết. Khi method chạy, mã đã biến đổi thực hiện kiểm tra còn lại, nếu có.

Ngược lại, một proxy có thể đọc tên và đối số tại mỗi lần gọi. Hai cơ chế cùng dẫn tới advice chạy ở runtime nhưng thời điểm phân tích và chi phí khác nhau.

### 7.2. Trường hợp một lớp được nạp từ trước

Nếu một lớp đã được JVM định nghĩa trước khi transformer được đăng ký, việc đăng ký transformer **không tự dệt lại class đã nạp**. Cần một khả năng retransformation/redefinition riêng, nếu công cụ và JVM hỗ trợ; việc “bật AOP sau” không bảo đảm mọi class cũ sẽ đổi.

## 8. Instrumentation agent trong Java là gì?

JDK cung cấp API `java.lang.instrument` cho Java agent quan sát hoặc biến đổi các class ở những ranh giới VM cho phép. Hai hình thức entry point hay được nhắc tới:

- **`premain`**: agent được khởi tạo từ tùy chọn `-javaagent:...` khi JVM khởi động, trước khi phương thức main của ứng dụng chạy theo cơ chế đó.
- **`agentmain`**: entry point của một agent được nạp vào JVM đang chạy theo cơ chế attach tương ứng **nếu môi trường/JVM cho phép**.

Đây là **các entry point instrumentation**, không tự cung cấp pointcut/advice hoàn chỉnh. Muốn dệt các hành vi AOP thực sự, phải xây dựng hoặc dùng một bộ xử lý bytecode/pointcut có mô hình đúng.

### 8.1. Transformer khác với weaver hoàn chỉnh

Một `ClassFileTransformer` nhận bytecode class và có thể trả:

- Một mảng byte mới biểu thị class hợp lệ đã biến đổi.
- `null` để báo **không biến đổi** class.

Việc transformer được gọi hoặc in log **không chứng minh** method đã có advice. Nếu transformer luôn trả `null`, nó chỉ quan sát đường class loading (trong ví dụ này), chưa dệt thêm hành vi method.

## 9. Ví dụ Java 17: Java agent quan sát class loading mà không sửa bytecode

Ví dụ này cho thấy một ranh giới **runtime instrumentation thực sự** bằng JDK thuần. Nó **chỉ in tên một class được nạp**, không tạo pointcut method, không chèn before/after advice.

Ta cần ba tệp: `TraceAgent.java`, `RuntimeSample.java`, và `MANIFEST.MF`.

### 9.1. File `TraceAgent.java`

~~~java
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

public class TraceAgent implements ClassFileTransformer {
    public static void premain(String arguments, Instrumentation instrumentation) {
        System.out.println("[agent] ready");
        instrumentation.addTransformer(new TraceAgent());
    }

    @Override
    public byte[] transform(
            Module module,
            ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) {
        if ("RuntimeSample$Worker".equals(className)) {
            System.out.println("[agent] noticed RuntimeSample$Worker");
        }
        return null; // Không biến đổi bytecode.
    }
}
~~~

Java 17 có overload `transform` nhận `Module` và các thông tin lớp. `return null` ở đây có nghĩa không thay đổi mã class. Ký hiệu `$` thuộc tên nhị phân của lớp lồng, không phải cơ chế của AOP.

### 9.2. File `RuntimeSample.java`

~~~java
public class RuntimeSample {
    static final class Worker {
        Worker() {
            System.out.println("[app] Worker constructed");
        }

        void work() {
            System.out.println("[app] hello");
        }
    }

    public static void main(String[] args) {
        System.out.println("[app] before new");
        Worker worker = new Worker();
        worker.work();
    }
}
~~~

### 9.3. File `MANIFEST.MF`

Lưu **manifest dưới dạng văn bản có dòng kết thúc**:

~~~text
Manifest-Version: 1.0
Premain-Class: TraceAgent

~~~

Dòng trống cuối là cách đơn giản bảo đảm phần main section của manifest kết thúc đúng. Khi tạo tệp thủ công, phải tránh vô tình nối các header vào cùng một dòng.

### 9.4. Biên dịch, đóng gói và chạy

Đặt ba tệp trong cùng thư mục rồi chạy:

~~~bash
javac TraceAgent.java RuntimeSample.java
jar cfm trace-agent.jar MANIFEST.MF TraceAgent.class
java -javaagent:trace-agent.jar RuntimeSample
~~~

Đầu ra dự kiến trong môi trường JDK 17 thông thường:

~~~text
[agent] ready
[app] before new
[agent] noticed RuntimeSample$Worker
[app] Worker constructed
[app] hello
~~~

**Giới hạn của cam kết stdout:** ví dụ chỉ in class `RuntimeSample$Worker`, nên tránh nhiễu từ những class khác. Nhưng những môi trường JVM đặc biệt, công cụ launch, agent bổ sung hoặc các biến đổi startup khác có thể thay đổi trình tự tải lớp hoặc thêm thông báo riêng. Vì vậy đây là output của kịch bản chạy chuẩn, không phải hợp đồng tổng quát của mọi JVM.

### 9.5. Bài học quan trọng nhất

- `[agent] ready` chứng minh `premain` của agent đã khởi động.
- `[agent] noticed ...` chứng minh transformer được gọi khi class mục tiêu được tải/định nghĩa theo đường quan sát được.
- **Không có before/after advice nào được chèn vào `Worker.work()`.**
- `return null` chứng minh ví dụ **không dệt bytecode của method**.

Để từ đây đi tới weaving AOP, cần một cơ chế biến đổi bytecode **hợp lệ**, xác định join point, chèn mã tương ứng và giữ bytecode đúng theo quy tắc xác minh. Không được coi thao tác đăng ký transformer đơn thuần là aspect weaving hoàn chỉnh.

## 10. Khác biệt giữa premain, agentmain và khả năng attach

Agent lúc khởi động và agent được nạp khi JVM đang chạy khác nhau về **thời điểm có quyền can thiệp**.

| Vấn đề | Agent lúc khởi động | Agent được attach sau khi chạy |
|---|---|---|
| **Class đã được nạp trước agent?** | Ít hơn đối với class ứng dụng, nhưng một số class hệ thống vẫn có thể được nạp từ sớm | Có thể rất nhiều |
| **Mục tiêu chính** | Đăng ký transformer trước các class quan trọng | Quan sát/biến đổi sau khi JVM đã hoạt động nếu được hỗ trợ |
| **Khả năng can thiệp vào class đã nạp** | Không tự có chỉ từ đăng ký transformer | Cũng không tự có; cần retransform/redefine và điều kiện tương ứng |
| **Yêu cầu triển khai** | Java agent JAR và cấu hình khởi động | Khả năng attach của VM, quyền truy cập, chính sách vận hành và agent |
| **Độ tin cậy phạm vi** | Dễ kiểm soát hơn khi được cấu hình trước startup | Khó hơn vì nhiều class và call path đã hoạt động |

Không nên mặc định một ứng dụng luôn cho phép **dynamic attach**. JVM, phiên bản runtime, chính sách bảo mật và tùy chọn cấu hình có thể giới hạn hoặc vô hiệu hóa khả năng này. Ở một số môi trường đóng gói đặc biệt, API attach hoặc khả năng instrumentation không hiện diện như ở một JDK đầy đủ.

Từ góc nhìn triển khai, agent là mã có quyền ảnh hưởng trực tiếp JVM. Chỉ nạp mã tin cậy, kiểm tra nguồn/phân phối và có chính sách quản trị phù hợp.

## 11. Re-transformation và redefinition: thay class đã nạp có giới hạn

Hai khái niệm thường xuất hiện:

- **Retransformation:** yêu cầu JVM chạy lại chuỗi transformer phù hợp trên class đã nạp, nếu hỗ trợ và class có thể biến đổi.
- **Redefinition:** cung cấp định nghĩa bytecode mới cho những class đã nạp, nếu khả năng đó được JVM hỗ trợ và được cấu hình cho agent.

Cần kiểm tra các khả năng cụ thể như `Instrumentation.isRetransformClassesSupported()`, `Instrumentation.isRedefineClassesSupported()` và `Instrumentation.isModifiableClass(...)` khi áp dụng Java instrumentation thật.

**Không phải class nào cũng modifiable.** Không phải mọi phép thay bytecode đều hợp lệ. Việc VM hỗ trợ một chức năng không đồng nghĩa agent đã khai báo quyền sử dụng nó hoặc một class mục tiêu thỏa điều kiện.

### 11.1. Giới hạn thay đổi cấu trúc class

Với cơ chế standard JVM class redefinition/retransformation trong những điều kiện thông thường, không được tùy tiện **thêm/xóa method**, **thêm/xóa field**, đổi cấu trúc quan hệ kế thừa hoặc thay đổi một số đặc điểm schema lớp. Những phép biến đổi kiểu này có thể bị từ chối.

Khả năng thay đổi phần thân method cũng có giới hạn xác minh, liên kết và trạng thái JVM. Vì vậy không nên dùng “dệt lại” như lời hứa có thể tái cấu trúc class sống theo bất kỳ cách nào.

### 11.2. Các stack frame đang hoạt động

Một giới hạn quan trọng: sau khi class được định nghĩa lại, các **lời gọi đã đang thực thi** có thể tiếp tục với phiên bản mã cũ theo semantics của VM, trong khi những lần vào sau mới dùng mã mới.

Đó là lý do không thể kết luận “đổi class lúc 12:00 thì mọi lời gọi đang chạy từ 11:59 lập tức chịu advice mới”. Cần kiểm thử trường hợp method đang chạy lâu khi yêu cầu biến đổi.

### 11.3. Đổi bytecode không đồng nghĩa đổi mọi điểm đã quan sát

Một weaver có thể chèn hook tại **execution** của phương thức, nhưng không chèn ở **mọi call site**. Retransform lớp method đích cũng không tự biến đổi bytecode của tất cả caller khác.

Hãy hỏi điểm nào bị thay: method body, call site, constructor hay field access. **Địa điểm dệt** quyết định phạm vi can thiệp.

## 12. Class loader: cùng tên lớp chưa chắc cùng class

Trong Java, danh tính của một class không chỉ là tên nhị phân. **Class loader định nghĩa** cũng có vai trò quan trọng.

Hai module hoặc hệ plugin có thể nạp những lớp cùng tên nhưng dưới các class loader khác nhau. Một cấu hình transformer chọn tên `com.example.Service` có thể bắt gặp nhiều class không cùng danh tính runtime.

Những câu hỏi cần xác minh:

- Class mục tiêu thật sự được định nghĩa bởi loader nào?
- Transformer có được gọi cho loader đó?
- Có loader nào loại trừ instrumentation hay không?
- Code ứng dụng đang gọi tới **bản class nào**?
- Có bao nhiêu phiên bản class được nạp trong cùng tiến trình?
- Có xảy ra class loader isolation khiến một lời gọi proxy/cast lỗi không?

Một advice log tên method `Service.execute` không đủ để chứng minh nó thuộc đúng module/runtime class instance mà người triển khai quan tâm.

### 12.1. Bootstrap classes và class hệ thống

Các class cốt lõi của JVM có thể chịu chính sách đặc biệt về class loading và transformation. Việc instrument chúng cần cân nhắc khả năng tiếp cận, chuỗi phụ thuộc và nguy cơ phá các cơ chế mà chính agent dựa vào.

Một transformer ghi log bằng các API Java tại mọi lần class loading có thể tự kích hoạt thêm việc nạp lớp. Nếu áp dụng phạm vi quá rộng, có nguy cơ **đệ quy, vòng phụ thuộc hoặc làm startup bất ổn**.

Cách tiếp cận thực tế: chọn lớp mục tiêu hẹp, tránh can thiệp các lớp hạ tầng cốt lõi khi chưa có thiết kế và kiểm thử phù hợp.

## 13. Module system và quyền truy cập trên JVM hiện đại

Từ Java 9, **Java Platform Module System (JPMS)** ảnh hưởng đến khả năng đọc và truy cập package, method và field giữa các module.

Cần phân biệt:

- **Exports:** một module công bố package cho việc sử dụng API theo cơ chế module.
- **Opens:** cho phép một số hình thức reflective access tới thành phần bên trong theo điều kiện module.
- **Class loading:** định nghĩa class nào, từ module/loader nào.
- **Instrumentation:** khả năng quan sát và biến đổi class theo hợp đồng VM/agent.

Một agent có khả năng nhận bytecode **không mặc nhiên có quyền reflective access vô hạn** tới mọi private member. Ngược lại, mở package cho reflection không tự cung cấp join point field-write mà công cụ AOP chưa hỗ trợ.

Có các API/module configuration giúp điều chỉnh quan hệ đọc/mở package trong phạm vi được phép, nhưng cần đánh giá rủi ro trước khi mở rộng truy cập.

### 13.1. Sai lầm thường gặp

Một advice muốn đọc private field và gặp lỗi truy cập. Người triển khai kết luận “weaving hỏng”. Nhưng nguyên nhân có thể chỉ ở **runtime reflection/module access** của chính mã advice; bytecode weave vẫn có thể đã được áp dụng đúng.

Phải xác định lỗi phát sinh khi **transform class**, **verify/link class** hay **advice gọi reflective access**.

## 14. Bytecode format, verifier và version compatibility

Khi weaving can thiệp vào bytecode, mã tạo ra phải thỏa quy tắc JVM: cấu trúc class file hợp lệ, kiểu trên operand stack phù hợp, stack map frames khi cần, access hợp lệ, tham chiếu constant pool và ràng buộc của các method.

Những hiện tượng có thể gặp:

- `ClassFormatError`: class file không có cấu trúc được chấp nhận.
- `VerifyError`: bytecode không thỏa quy tắc xác minh.
- `UnsupportedClassVersionError`: class file yêu cầu một phiên bản JVM không tương thích.
- `NoSuchMethodError` hoặc `IllegalAccessError`: một số lỗi liên kết khi mã thực thi tham chiếu thành phần không phù hợp.

**Không phải hễ nhìn thấy các lỗi đó thì weaving là thủ phạm.** Lỗi có thể do artifact build/deploy không khớp, thư viện không tương thích hoặc mã sinh từ công cụ khác.

Phương pháp điều tra tốt:

1. Xác định file class thực tế được nạp.
2. So sánh artifact **trước và sau** biến đổi nếu có.
3. Xác minh JDK, class file version, classpath/module path.
4. Kiểm tra cause và stack trace/diagnostic của transformer hoặc verifier.
5. Tạo một ca tái hiện tối thiểu để cô lập lỗi.

Không nên sửa logic nghiệp vụ khi bằng chứng đang chỉ vào vấn đề bytecode compatibility.

## 15. Những giới hạn về loại join point

Một yêu cầu “ghi log mọi thao tác” rất dễ vượt ngoài mô hình can thiệp của một công cụ.

| Hoạt động | Proxy method-interface | Bytecode weaving/agent có mô hình phù hợp |
|---|---|---|
| Lời gọi method giao diện qua proxy | Có thể chặn | Có thể nếu cơ chế và phạm vi hỗ trợ |
| Gọi trực tiếp vào target | Không qua handler proxy | Có thể ở execution/call site đã được dệt |
| Private method trong target | Không tự có interception riêng | Có thể nếu loại điểm và phạm vi hỗ trợ |
| Static method | Không đi qua instance interface proxy | Có thể theo cơ chế weaving phù hợp |
| Constructor call/execution | Proxy target thông thường không tự chặn | Một số mô hình có khả năng; cần xét semantics |
| Field get/set trực tiếp | Không phải method giao diện | Có thể nếu mô hình có field join point và weaving đúng mã |
| Native method body | Không phải mã bytecode Java thông thường để chèn vào thân native | Cần cơ chế khác hoặc can thiệp ở ranh giới Java/nền tảng |
| Hoạt động ở tiến trình khác | Không nằm trong proxy này | Không tự nằm trong JVM đang instrument |

Từ “có thể” **không phải** lời hứa mọi công cụ bytecode sẽ làm được. Công cụ cần định nghĩa **loại join point**, một vị trí trong mã có thể can thiệp, phạm vi weaving và semantics advice hợp lệ.

### 15.1. Truy cập trường qua reflection hoặc cơ chế bộ nhớ khác

Một công cụ hỗ trợ field get/set tại bytecode truy cập trường thông thường **không tự bao phủ tất cả** các thay đổi trạng thái thông qua reflection, method handle, native memory hoặc cơ chế đặc biệt.

Muốn quan sát mọi thay đổi bất biến nghiệp vụ, thường cần đặt chính sách ở ranh giới sở hữu trạng thái, không chỉ tin vào một nhóm instruction.

### 15.2. Constructor và quá trình khởi tạo

“Có constructor execution join point” không đồng nghĩa “đếm được mọi object allocation”. Các đường tạo đối tượng khác nhau, quá trình constructor delegation và tình trạng object chưa khởi tạo đầy đủ cần được xem xét.

Một advice làm lộ `this` trong constructor, đọc trạng thái chưa sẵn sàng hoặc gọi virtual method quá sớm có thể gây lỗi khó chẩn đoán. Không áp dụng advice constructor như wrapper method thông thường mà bỏ qua quy tắc khởi tạo của Java.

## 16. Dynamic dispatch, reflection, method handle và mã sinh

Java có nhiều đường tạo lời gọi:

- Lời gọi thông thường trong source.
- Gọi qua interface hoặc virtual dispatch.
- Gọi qua `Method.invoke` (reflection).
- Gọi qua method handle và `invokedynamic` trong những trường hợp phù hợp.
- Lambda, class sinh động hoặc proxy do runtime tạo.
- Lời gọi tới native method qua JNI hoặc ranh giới khác.

Độ bao phủ phụ thuộc **điểm dệt**:

- Nếu weaving ở **method execution**, phần thân của phương thức đã được dệt có thể được quan sát dù caller dùng reflection, nếu runtime thực sự vào phần thân đó.
- Nếu weaving ở **call site tĩnh**, lời gọi phát sinh bằng reflection hoặc mã sinh khác có thể không đi qua những call site đã được dệt trong source ban đầu.
- Nếu can thiệp bằng **proxy**, chỉ các lời gọi đi qua đối tượng trung gian được bao phủ.
- Nếu class được sinh/nạp sau khi bộ dệt kích hoạt, cần xác minh class đó có đi qua transformer và phù hợp các bộ lọc.

Không nên xem “gọi cùng tên method” là bảo đảm đi qua cùng loại join point.

## 17. Native code và giới hạn của nền tảng JVM

Bytecode weaving chuyên xử lý những phần của chương trình thể hiện bằng bytecode tương ứng. Mã native nằm bên ngoài thân bytecode của phương thức Java.

Một lời gọi Java **đi vào ranh giới native** có thể được quan sát bằng một số phương thức can thiệp ở phía Java, nếu mô hình hỗ trợ. Nhưng một bộ dệt bytecode Java không tự chèn advice vào từng instruction máy bên trong thư viện native.

Các trường hợp cần giải pháp khác có thể bao gồm:

- Thao tác bộ nhớ native.
- Tương tác driver hoặc runtime nền tảng.
- Sự kiện hệ điều hành hoặc file system do tiến trình khác thực hiện.
- Thay đổi ở hệ thống bên ngoài sau một lời gọi mạng.
- Hoạt động ở database sau khi Java đã gửi yêu cầu.

**AOP trong một JVM không phải hệ thống observability toàn vũ trụ.** Một advice bao quanh `sendMessage()` chỉ biết phương thức Java đó rời phạm vi theo hợp đồng; nó không tự xác nhận message đã được một tiến trình khác xử lý xong.

## 18. AOT, native image và môi trường bị giới hạn

Một số hệ thống chạy dưới môi trường **ahead-of-time compilation (AOT)**, native image hoặc nền tảng có giới hạn động đối với class loading, reflection, attach và instrumentation.

Khi đó cần đánh giá riêng:

- Có JVM với API `java.lang.instrument` giống triển khai thông thường không?
- Cơ chế runtime class loading/bytecode transform có tồn tại không?
- Có thể dùng dynamic proxy/reflection ở mức nào?
- Những metadata nào còn được giữ?
- Có quy tắc khai báo trước hoặc build-time configuration không?
- Chính sách nền tảng có cho phép agent attach không?

Không được kết luận “nếu Java 17 source biên dịch được thì mọi runtime đều cho chạy Java agent theo cách đó”. JDK chuẩn trên JVM và bản build AOT có thể có những năng lực rất khác nhau.

## 19. Giới hạn của attach và bảo mật vận hành

Can thiệp vào JVM đang chạy là khả năng nhạy cảm. Một agent đủ quyền có thể quan sát hoặc thay đổi hành vi sâu bên trong ứng dụng.

Những câu hỏi vận hành:

- Ai được phép khởi động JVM kèm `-javaagent`?
- Ai được phép attach agent khi chạy?
- Agent JAR được lấy từ đâu, kiểm tra và cập nhật thế nào?
- Môi trường production có tắt dynamic attach hoặc hạn chế API này không?
- Có chính sách audit với những thay đổi instrumentation không?
- Có thể rollback mà không làm mất trạng thái hoặc gây ngắt quãng không?
- Có đo chi phí và tác động bảo mật của agent không?

**Không dùng agent như cách lách kiểm soát truy cập.** Cần tuân thủ chính sách của môi trường và xem agent như thành phần mã có mức tin cậy cao.

### 19.1. Quyền instrumentation không phải quyền đọc mọi dữ liệu

Một agent có thể nhận bytecode ở một giai đoạn phù hợp, nhưng điều đó không đồng nghĩa có thể đọc trực tiếp mọi dữ liệu nghiệp vụ hoặc các thành phần private qua Java reflection mà không có quy tắc module/access tương ứng.

Việc một phép biến đổi bytecode được phép cũng không đồng nghĩa một pointcut được phép **thay đối số hoặc short-circuit** trên mọi loại join point. Đây là các mức quyền năng khác nhau.

## 20. Thứ tự transformer và tương tác giữa nhiều công cụ

Một JVM có thể có nhiều agent/transformer cùng tham gia. Mã cuối cùng có thể chịu ảnh hưởng của **thứ tự áp dụng biến đổi**, khả năng retransformation và sự tương thích của các thay đổi.

Các rủi ro:

- Hai transformer cùng dựa vào cấu trúc bytecode ban đầu nhưng một transformer chạy sau đã thấy mã biến đổi.
- Một công cụ chèn một lời gọi helper mà class loader khác không tìm thấy.
- Một transformer thay đổi tên/trạng thái phương thức và ảnh hưởng tới phép nhận diện của công cụ khác.
- Mã mới tạo ra không còn thỏa stack map frame/verification.
- Khi retransformation, các quy tắc có thể khác lúc load lần đầu.
- Hai aspect cùng chặn một join point nhưng dùng thứ tự advice không được hiểu đúng.

Nếu lỗi chỉ xuất hiện khi chạy **kết hợp** nhiều công cụ, cần thử tối thiểu từng công cụ rồi kiểm chứng tương tác. Không nên sửa pointcut một cách mù quáng khi nguyên nhân thật là bytecode đã bị biến đổi bởi một công cụ khác.

## 21. Class initialization và thứ tự khởi động

Một số mã chạy rất sớm trong vòng đời JVM: static initializer, code của class loader, code của agent và những thành phần logging hoặc cấu hình thiết yếu.

Nếu advice hoặc transformer **dùng cùng những class đang được định nghĩa**, có thể phát sinh phụ thuộc vòng:

~~~text
Transformer đang nạp helper A
        |
        v
Helper A lại phụ thuộc class B
        |
        v
Transformer tiếp tục chặn class B
        |
        v
B cố dùng lại A trước khi A khởi tạo xong
~~~

Không phải mọi lần class được nạp bởi agent đều gặp lỗi này; sơ đồ là tình huống rủi ro. Nhưng đây là lý do nên giữ transformer/agent bootstrap **đơn giản, có bộ lọc, ít phụ thuộc** và không thực hiện logging phức tạp tại mọi class load.

### 21.1. Điều kiện đặt breakpoints/debug

Khi instrumentation chạy tại class loading, lỗi có thể xuất hiện trước khi logger ứng dụng được cấu hình. Cần chuẩn bị kênh chẩn đoán và cách giảm phạm vi class mục tiêu.

Không đánh giá toàn bộ kết quả chỉ bằng một dòng log vắng mặt ở giai đoạn rất sớm.

## 22. JIT và việc thay đổi mã khi JVM đã tối ưu

JVM có thể tối ưu bytecode bằng Just-In-Time compilation khi chương trình chạy. Điều đó không biến bytecode weaving thành “chỉ dệt khi interpreter chạy”.

Nếu một class được redefine/retransform thành công theo hợp đồng VM, JVM phải quản lý tính đúng đắn của mã đang chạy và các bản tối ưu theo cơ chế của chính VM. Nhưng:

- **Không đảm bảo mọi frame đang hoạt động lập tức dùng phiên bản mới**.
- Chi phí có thể tăng do hủy/tạo lại một số tối ưu.
- Không nên đưa ra lời hứa về throughput hoặc latency khi chưa đo.
- Phân tích profiler cần phân biệt chi phí advice, dispatch, instrumentation và nghiệp vụ.

**Khả năng hot-swap một số mã** không phải cơ chế triển khai thay đổi không gián đoạn tuyệt đối, càng không phải giấy phép thay đổi mọi schema class.

## 23. Runtime AOP và tính đúng đắn nghiệp vụ

Một aspect có thể chạy ở vùng điều khiển quan trọng, không chỉ in log. Nếu around advice có quyền quyết định có gọi continuation hay không, các rủi ro gồm:

- **Bỏ qua phần gốc:** không gọi `proceed` làm nghiệp vụ không chạy.
- **Gọi nhiều lần:** retry không kiểm soát dẫn tới thao tác lặp.
- **Đổi giá trị trả về:** caller nhận kết quả không phù hợp hợp đồng.
- **Che ngoại lệ gốc:** mã logger/cleanup tự ném lỗi khác trong `finally`.
- **Thay đối số không hợp lệ:** làm sai validation hoặc invariant.
- **Tương tác nhiều aspect:** thứ tự advice làm thay đổi tập thao tác được quan sát.

Một advice đo thời gian thường muốn **không thay đổi hành vi nghiệp vụ**. Điều đó nên được kiểm thử: cùng đầu vào, cùng kết quả/ngoại lệ, cùng số lần thực thi phần gốc, trừ phần overhead được cho phép.

### 23.1. Một lần proceed chưa chắc phần gốc chạy một lần

Trong chuỗi nhiều around advice, `proceed` có thể chuyển sang advice kế tiếp chứ không đi thẳng phần gốc. Advice phía trong có thể short-circuit hoặc, nếu công cụ cho phép, gọi tiếp nhiều lần.

Không suy từ số log của advice bên ngoài thành số lần side effect nghiệp vụ.

## 24. Concurrency, context và dữ liệu riêng tư

Một aspect có thể được kích hoạt đồng thời trên nhiều thread. Nếu trạng thái quan sát được lưu ở một biến static dùng chung, dữ liệu của các lời gọi có thể bị ghi đè.

Những nguyên tắc quan trọng:

- Giữ thời điểm bắt đầu, số lần retry và context liên quan trong **phạm vi invocation**.
- Khi dùng ngữ cảnh theo thread, cần lưu và phục hồi đúng cho lời gọi lồng nhau.
- Không mặc định `ThreadLocal` lan truyền sang tác vụ bất đồng bộ hoặc thread khác.
- Không đưa đối tượng join point thô và mảng đối số mutable sang queue xử lý về sau nếu hợp đồng không cho phép.
- Chỉ ghi dữ liệu tối thiểu; che token, account ID và những trường nhạy cảm.
- Không gọi `toString()` trên mọi đối số một cách tự động.

Can thiệp sâu làm tăng khả năng quan sát, nhưng cũng tăng **bề mặt dữ liệu mà mã instrumentation tiếp cận**. Số lượng dữ liệu có thể lấy không phải lý do để lưu tất cả.

## 25. Vấn đề hiệu năng: chi phí không chỉ nằm ở handler

Tùy cơ chế, overhead có thể đến từ:

1. Dispatch qua proxy/interceptor.
2. Kiểm tra pointcut động tại từng lần gọi.
3. Tạo object/record chứa join point context.
4. Boxing/unboxing, reflection hoặc chuyển đổi đối số.
5. Logging, serialization và gửi sự kiện đi xa.
6. Việc chèn hook vào những method tần suất cao.
7. Chi phí class loading và transformer.
8. Retransformation, verification và tối ưu JIT bị ảnh hưởng.
9. Đồng bộ/lock trong advice hoặc global metrics.
10. Vòng lặp recursion hoặc retry gây thực thi thừa.

Một ví dụ:

~~~text
method A được gọi 5 lần/giây → instrumentation có thể chấp nhận
method B được gọi 2 triệu lần/giây → cùng advice có thể là hot-path bottleneck
~~~

Số liệu ở đây **chỉ minh họa tần suất**, không phải benchmark đo được. Chi phí thật phụ thuộc workload/JVM/bytecode và chính sách quan sát.

### 25.1. Chọn điểm cần quan sát, không phải chọn mọi điểm có thể

Pointcut bao phủ “mọi method” làm nguy cơ overhead, vòng lặp logging và dữ liệu quá tải tăng. Ưu tiên ranh giới có nghĩa: API quan trọng, service operation hoặc tập nhỏ được kiểm chứng.

Nếu công cụ có static matching, loại sớm những điểm không thể khớp giúp giảm kiểm tra động. Nhưng việc tối ưu thực tế vẫn phụ thuộc engine.

## 26. Những lỗi triển khai thường gặp

### 26.1. “Bật agent là mọi method được intercept”

**Sai:** transformer có thể chỉ quan sát class loading, hoặc phạm vi biến đổi chỉ gồm một số class.

**Đúng:** kiểm tra những class/điểm thực sự được biến đổi và ca thử chứng minh advice chạy.

### 26.2. “Class đã được nạp vẫn tự dệt lại sau khi bật transformer”

**Sai:** đăng ký transformer sau class loading không tự thay class đã định nghĩa.

**Đúng:** chỉ retransform/redefine nếu VM/công cụ/class hỗ trợ, và kiểm chứng giới hạn.

### 26.3. “Đọc được Method/Field qua reflection nghĩa là chặn được mọi truy cập”

**Sai:** metadata access và join point interception là hai khả năng khác nhau.

**Đúng:** xác minh loại điểm và đường weave.

### 26.4. “Chỉ cần thêm wildcard vào pointcut để chặn constructor/field”

**Sai:** pointcut chỉ chọn trong miền join point model hiện có.

**Đúng:** muốn loại điểm mới phải có cơ chế hỗ trợ.

### 26.5. “Một proxy được tạo thì caller nào cũng đi qua nó”

**Sai:** caller có thể giữ target và gọi trực tiếp.

**Đúng:** đảm bảo các entry point mong muốn dùng đúng ranh giới.

### 26.6. “Static/final/private luôn không thể can thiệp bằng mọi AOP”

**Sai:** đó là những giới hạn phụ thuộc kiểu proxy/subclass và mô hình weaving.

**Đúng:** mô tả ràng buộc cụ thể thay vì phủ định phổ quát.

### 26.7. “Lỗi khi startup luôn là lỗi logic runtime”

**Sai:** load-time transformer hoặc JVM verifier có thể phát hiện lỗi lúc startup.

**Đúng:** phân biệt nguyên nhân, pha phát hiện, class loader, bytecode và mã advice.

### 26.8. “Advice log RETURNED tức nghiệp vụ đã thành công”

**Sai:** method có thể trả một trạng thái từ chối hoặc một future chưa hoàn tất.

**Đúng:** phân biệt normal return và business outcome/async completion.

### 26.9. “Runtime instrumentation không cần kiểm thử production-like”

**Sai:** class loader, module, JVM và profile triển khai có thể khác máy phát triển.

**Đúng:** thử cùng JDK, launch options, classpath/module layout và tải điển hình.

### 26.10. “Không có exception thì không có vấn đề weaving”

**Sai:** một pointcut không khớp hoặc lớp ngoài weaving scope có thể khiến lời gọi chạy bình thường nhưng không có advice.

**Đúng:** kiểm thử độ bao phủ bằng ca dương/âm.

## 27. Quy trình chẩn đoán runtime interception theo tầng

Khi một thao tác không được quan sát, đi theo thứ tự:

1. **Xác định hành vi:** cần method call, method execution, constructor, field access, exception handler hay sự kiện ngoài JVM?
2. **Kiểm tra mô hình:** công cụ có hỗ trợ loại join point đó không?
3. **Xác định kỹ thuật:** proxy, load-time transform, class redefinition hay kỹ thuật khác?
4. **Xác nhận class và loader:** tên chính xác, class được nạp ở đâu, bởi loader nào?
5. **Kiểm tra thời điểm kích hoạt:** agent/transformer có hoạt động trước khi class được định nghĩa không?
6. **Kiểm tra điểm được dệt:** có bytecode/hook thực sự ở phương thức hoặc call site cần quan sát?
7. **Kiểm tra pointcut:** cú pháp, chữ ký, overload, annotation, điều kiện động và ngoại lệ loại trừ.
8. **Kiểm tra runtime path:** lời gọi đi qua proxy hay target; caller có phát sinh điểm không?
9. **Kiểm tra advice:** có chạy nhưng log bị lọc hoặc chính logger gặp lỗi không?
10. **Kiểm tra nhiều aspect:** ordering, short-circuit, recursion và số lần proceed.
11. **Kiểm tra môi trường:** JPMS, permissions, class file version, agent attach, JDK/JVM.
12. **Tái hiện tối thiểu:** một class, một advice, một ca dương, một ca âm.

Không nên sửa cùng lúc pointcut, cấu hình class loading và logic advice; nếu nhiều biến đổi cùng lúc, rất khó xác định nguyên nhân.

## 28. Ma trận kiểm thử giới hạn nền tảng

Một bộ kiểm thử nên xác định trước **phạm vi được cam kết**, thay vì chỉ xem có một dòng log hay không.

| Ca kiểm thử | Câu hỏi xác minh |
|---|---|
| Gọi interface method **qua proxy** | Handler có chạy và chuyển kết quả đúng không? |
| Gọi trực tiếp **target** | Có bị bỏ qua như thiết kế proxy không? |
| Self-invocation | Có nằm trong ranh giới chặn hay không? |
| Static/private/final method | Công cụ và kỹ thuật đang dùng có hỗ trợ không? |
| Constructor và field access | Loại join point có tồn tại trong mô hình không? |
| Class nạp **trước agent** | Có được retransform hợp lệ không hay giữ nguyên? |
| Class nạp **sau agent** | Transformer có nhận và xử lý đúng class không? |
| Hai class loader với cùng tên | Định danh class/loader có đúng mục tiêu không? |
| Java module giới hạn reflective access | Advice có xử lý thiếu quyền/truy cập không? |
| Runtime dùng phiên bản JDK khác | Class file và agent có tương thích không? |
| Method trả/throw | Advice giữ return value và lỗi theo hợp đồng không? |
| Method đang chạy khi redefine | Các lần vào mới/cũ có được hiểu đúng không? |
| Hoạt động bất đồng bộ | Đo hoàn tất method hay thực sự hoàn tất task? |
| Agent bị tắt hoặc không hỗ trợ | Ứng dụng có phản ứng đúng chính sách không? |

Một kết quả “không chặn được” có thể hoàn toàn đúng nếu đó là **giới hạn công bố**. Chỉ là lỗi khi hệ thống yêu cầu/khẳng định ranh giới ấy được bao phủ nhưng không có bằng chứng thực tế.

## 29. Vòng đời triển khai và khả năng quan sát an toàn

Có ba câu hỏi phải trả lời trước khi đưa runtime instrumentation vào môi trường quan trọng.

**Khởi động và phạm vi:** agent cần được kích hoạt khi nào, class nào cần transform, module/loader nào liên quan? Nếu đòi can thiệp từ trước khi class ứng dụng được tải, thường cần kế hoạch startup phù hợp.

**Thay đổi và rollback:** nếu instrument một class rồi muốn thay đổi/hoàn tác, có thể retransform/redefine theo phạm vi được VM hỗ trợ không? Những frame đang hoạt động, schema class và chuỗi nhiều transformer có thể ảnh hưởng tới tính đúng đắn của thay đổi.

**Lỗi của chính hệ quan sát:** nếu agent, advice hoặc sink hỏng, ứng dụng có tiếp tục không? Với telemetry best-effort, thường muốn hạn chế tác động nghiệp vụ; với kiểm soát bắt buộc cần một hợp đồng fail-open/fail-closed được thiết kế cẩn thận.

Một cơ chế quan sát hữu ích không chỉ có nhiều join point, mà còn phải **giải thích được khi nào nó không quan sát, giữ được hành vi gốc và có thể vận hành an toàn**.

## 30. Bài tập tự kiểm tra

### Bài 1 — Runtime AOP có nghĩa luôn sửa bytecode?

Một nhóm dùng Java Dynamic Proxy để chặn lời gọi qua interface. Họ nói “mọi runtime AOP đều biến đổi bytecode target”.

**Đáp án:** sai. Proxy/interceptor có thể đứng trước target mà không cần sửa bytecode target. Một weaver bytecode lại có cách can thiệp khác.

### Bài 2 — Class được nạp trước khi agent bật

Một transformer được đăng ký sau khi lớp `Ledger` đã được định nghĩa. Có thể kết luận method của `Ledger` đã được dệt lại không?

**Đáp án:** không. Cần cơ chế retransformation/redefinition nếu hỗ trợ và class modifiable. Đăng ký transformer một mình không tự thay class cũ.

### Bài 3 — Java agent in class-name

Một agent in “saw class X”, sau đó trả `null` trong `transform`. Đã dệt before advice vào mọi method của X chưa?

**Đáp án:** chưa. `null` có nghĩa agent không cung cấp bytecode thay thế; in tên lớp chỉ chứng minh nó thấy event class loading.

### Bài 4 — Proxy và self-invocation

`proxy.process()` có log intercept, còn `this.verify()` gọi từ bên trong target không có intercept riêng. Đây có phải lỗi của JVM?

**Đáp án:** không. Đó là đường gọi nội bộ không đi qua proxy. Một cơ chế weaving execution khác có thể có phạm vi khác.

### Bài 5 — Final class

Một cơ chế tạo subclass proxy không thể kế thừa lớp `final`. Có thể suy luận mọi bytecode weaving đều bó tay?

**Đáp án:** không. Ràng buộc của subclass proxy khác với khả năng biến đổi bytecode ở những điểm được mô hình hỗ trợ.

### Bài 6 — Module access

Advice dùng reflection để đọc private field trong module khác rồi gặp lỗi truy cập. Có được kết luận pointcut sai không?

**Đáp án:** không. Vấn đề có thể thuộc quyền reflective access/JPMS của runtime, không phải biểu thức pointcut.

### Bài 7 — Constructor

Một cơ chế chỉ chặn method interface qua proxy. Nó có thể tự động ghi mọi lần `new ServiceImpl()` không?

**Đáp án:** không. Việc target constructor chạy không phải lời gọi method giao diện qua proxy.

### Bài 8 — Đổi bytecode khi method đang chạy

Sau khi redefine class thành công, có chắc mọi stack frame của method vốn đang chạy lập tức dùng phần thân mới không?

**Đáp án:** không. Semantics của VM có thể giữ phiên bản cũ cho frame đang hoạt động; các lần vào sau có thể dùng định nghĩa mới.

### Bài 9 — Observability xuyên tiến trình

Một advice quanh `sendMessage()` chạy sau khi method trả về. Điều đó có chứng minh bên nhận đã xử lý message không?

**Đáp án:** không. Kết thúc lời gọi Java và hoàn tất công việc ở tiến trình bên ngoài là hai sự kiện khác nhau.

### Bài 10 — Thiếu advice nhưng không có lỗi

Một ứng dụng có `submit()` chạy đúng nhưng không ghi sự kiện aspect. Hãy nêu ít nhất bốn nguyên nhân khác nhau.

**Đáp án gợi ý:** pointcut không khớp; class ngoài weaving scope; class được nạp trước lúc transformer hoạt động; caller gọi target trực tiếp; loại join point không được hỗ trợ; advice chạy nhưng logger/sink bị lọc; một advice bao ngoài short-circuit; ca thử không phát sinh join point.

### Bài 11 — Tại sao instrumentation ở startup có thể khó hơn runtime method call?

**Đáp án:** transformer xử lý những class đang trong quá trình định nghĩa; nó có thể chạy trước khi logging được thiết lập, gây nạp thêm class phụ thuộc và vướng class loader/module/bytecode version. Cần bộ lọc hẹp và mã bootstrap đơn giản.

### Bài 12 — Retry bằng multiple proceed

Around advice gọi tiếp hai lần để “an toàn hơn”. Nếu mỗi lần trừ tiền rồi gửi yêu cầu mạng, rủi ro nào?

**Đáp án:** tác dụng phụ có thể lặp; cần kiểm soát idempotency, transaction, lỗi tạm thời, giới hạn retry và bảo đảm công cụ cho phép multiple proceed. Đây là quyết định nghiệp vụ/kiến trúc, không chỉ là cú pháp advice.

## 31. Checklist trước khi tuyên bố “can thiệp lúc chạy bao phủ hệ thống”

1. Tôi đã gọi đúng tên cơ chế: **proxy, LTW, class instrumentation, retransform/redefine** hay một dạng khác?
2. Tôi có biết **điểm nào** được công cụ xem là join point hợp lệ không?
3. Pointcut có xác định đúng phạm vi, bao gồm/loại trừ và điều kiện động không?
4. Với proxy, mọi caller quan trọng có sử dụng đối tượng proxy không?
5. Với LTW, transformer có được kích hoạt trước khi class mục tiêu nạp không?
6. Với class đã nạp, VM/agent/class có đủ điều kiện retransform/redefine không?
7. Class loader, module, package access và classpath có được xác minh?
8. Tôi đã phân biệt lời gọi method, thực thi method, constructor và field access chưa?
9. Tôi có kiểm thử cho self-invocation, direct call và đường ngoài weaving?
10. Tôi có bằng chứng mã **được biến đổi thật**, nếu đang tuyên bố bytecode weaving?
11. Tôi có kiểm thử bảo toàn return value, exception và số lần nghiệp vụ chạy?
12. Môi trường AOT/native/giới hạn attach có ảnh hưởng khả năng triển khai không?
13. Có kiểm soát dữ liệu nhạy cảm, CPU, allocation, logging và reentrancy?
14. Có quy trình rollback, bật/tắt và xử lý lỗi của chính agent?
15. Tôi có ghi rõ **những gì không được bao phủ** để người khác không đặt chính sách bắt buộc ở một ranh giới thiếu chứng minh?

## 32. Tổng kết

**Can thiệp lúc chạy** là một nhóm kỹ thuật, không phải lời hứa rằng mọi câu lệnh Java đều trở thành join point. Proxy quan sát lời gọi qua đối tượng trung gian; load-time weaving có cơ hội biến đổi mã khi class được định nghĩa; Java instrumentation có thể quan sát class loading và, trong các giới hạn của JVM, biến đổi hoặc yêu cầu biến đổi lại những class phù hợp.

**Giới hạn nền tảng** đến từ join point model, class loader, thời điểm định nghĩa class, ràng buộc bytecode/verifier, module/access, khả năng attach, khả năng thay đổi class đã nạp, native/AOT runtime và chính đường thực thi của ứng dụng.

Cách đánh giá đúng không dựa vào từ “runtime” hay một dòng log, mà dựa vào bốn câu hỏi: **(1) mô hình có hỗ trợ loại điểm cần can thiệp? (2) pointcut có chọn điểm đó? (3) mã/đường gọi đã thực sự đi qua cơ chế can thiệp? (4) advice có bảo toàn hợp đồng nghiệp vụ và vận hành được trên nền tảng cụ thể?**

Một thiết kế AOP đáng tin cậy công bố cả **khả năng** lẫn **giới hạn**, kèm thử nghiệm dương/âm tái hiện được trong cùng môi trường triển khai.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="weaving-lifecycle-005">Thay đổi aspect và nhu cầu xây dựng hoặc khởi động lại</a>

<details>
<summary>Xem chi tiết</summary>

# Thay đổi aspect và nhu cầu xây dựng hoặc khởi động lại

Khi sửa một **aspect** trong lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**), hệ thống có cần **biên dịch lại, dệt lại, đóng gói lại, triển khai lại hoặc khởi động lại** hay không? Câu trả lời phụ thuộc trước hết vào **thời điểm weaving (dệt)** và **loại thay đổi**. Không có quy tắc “sửa aspect luôn phải restart” hoặc “sửa pointcut là tự cập nhật ngay” đúng cho mọi hệ thống.

Cần phân biệt những việc sau:

- **Sửa định nghĩa:** mã advice, biểu thức pointcut, khai báo ưu tiên, cấu hình bật/tắt hoặc thành viên được thêm vào lớp.
- **Tạo artifact mới:** biên dịch aspect, biên dịch lại lớp liên quan, dệt lại mã nguồn/bytecode và đóng gói.
- **Đưa artifact vào môi trường thực thi:** cập nhật class/JAR, bộ nạp lớp hoặc chuỗi proxy.
- **Làm thay đổi chương trình đang chạy:** restart tiến trình, tạo class loader mới, xây lại proxy hoặc dùng cơ chế instrumentation hỗ trợ biến đổi lớp đã nạp.

**Mấu chốt:** thay tệp `.class`/`.jar` trên đĩa **không tự thay thế định nghĩa class đã được JVM nạp**. Ngược lại, nếu một advice **đã được lắp vào đường gọi** chỉ cần kiểm tra một cờ cấu hình động, người vận hành có thể thay hành vi advice **mà không thay pointcut hoặc dệt lại bytecode**. Hai tình huống này khác nhau về cấu trúc và rủi ro.

Bài tập trung vào **AOP tổng quát**, lấy Java 17 để minh họa các ranh giới về class loading và một mô hình wrapper runtime. Cú pháp cấu hình, khả năng reweaving và hot reload phải được xác minh theo công cụ cụ thể.

## 1. Từ aspect đến hành vi đang chạy

Một **aspect** nhóm các mối quan tâm xuyên suốt, có thể gồm:

- **Pointcut:** lựa chọn những join point (điểm nối) mà mô hình AOP hỗ trợ.
- **Advice:** hành vi chạy trước, sau hoặc bao quanh các điểm được chọn.
- **Precedence/ordering:** thứ tự kết hợp khi nhiều advice cùng khớp.
- **State/lifecycle:** trạng thái của aspect, phạm vi instance và cách lưu trữ nếu có.
- **Inter-type declarations hoặc cơ chế mở rộng kiểu:** trong những hệ thống AOP hỗ trợ thêm thành viên/quan hệ kiểu.

Một thay đổi có thể xảy ra ở **tập điểm**, **nội dung xử lý** hoặc **cách điều phối**:

| Thứ được sửa | Ví dụ | Phần có thể cần cập nhật |
| --- | --- | --- |
| Thân advice | Đổi cách đo thời gian, thay logic audit | Mã advice hoặc interceptor đang được chạy |
| Pointcut | Từ chọn `pay()` sang chọn cả `refund()` | Tập join point được weave/chặn |
| Precedence | Đưa Authorization ra ngoài Cache | Chuỗi advice và hành vi short-circuit |
| Cấu hình runtime | Bật log bổ sung ở advice đã có sẵn | Giá trị cấu hình nếu advice đọc động |
| Kiểu hoặc chữ ký | Advice phụ thuộc method signature mới | Biên dịch, weaving và kiểm tra tương thích |
| Thành viên được sinh vào class | Thêm field/method mới khi nền tảng hỗ trợ | Cấu trúc class sau dệt, có thể vượt khả năng hot redefine |
| Thư viện phụ thuộc | Nâng version JAR chứa aspect | Classpath, artifact, loader, tương thích runtime |

Không được lấy thay đổi một **cờ bật/tắt** làm bằng chứng rằng có thể thêm một **join point mới** vào những class đã nạp. Bật/tắt chỉ thay đổi hành vi của phần đã có thể được gọi tới.

## 2. Bốn thời điểm/cách kết hợp AOP và hệ quả khi sửa aspect

| Cơ chế | Sửa advice/pointcut thường yêu cầu | Đưa thay đổi vào tiến trình đang chạy |
| --- | --- | --- |
| **Compile-time weaving** | Biên dịch/dệt lại những nguồn và lớp bị ảnh hưởng; đóng gói lại | Thường triển khai artifact mới và restart hoặc chuyển sang phiên bản được loader mới nạp |
| **Post-compile/binary weaving** | Dệt lại class/JAR đã biên dịch với aspect mới; tạo artifact mới | Phải bảo đảm runtime nạp bản woven mới; thay JAR trên đĩa không tự đổi lớp đang chạy |
| **Load-time weaving** | Chuẩn bị aspect/cấu hình mới cho loader/agent; đôi khi cần compile aspect | Chủ yếu tác động **lớp được nạp sau đó**; lớp đã nạp thường cần restart/reload hoặc cơ chế redefine/retransform riêng |
| **Runtime proxy/wrapper** | Cấu hình/build lại chuỗi chặn tùy cách thiết kế | Có thể đổi hành vi động nếu proxy đọc cấu hình/handler thay được; proxy tĩnh hoặc điểm chưa được bọc có thể cần tái tạo/redeploy |

Bảng này nêu **khuynh hướng kiến trúc**, không phải cam kết cho mọi triển khai. Có hệ thống hỗ trợ deployment không cần dừng toàn tiến trình bằng cách nạp phiên bản mới trong **class loader khác**; có hệ thống chỉ cho restart. Có công cụ weaving hỗ trợ reweaving hoặc runtime instrumentation; cần phân biệt tính năng đó với **load-time weaving thông thường**.

### 2.1 Vì sao “biên dịch lại” không bằng “khởi động lại”?

- **Recompile:** tạo lại mã trung gian hoặc bytecode từ nguồn.
- **Reweave:** chạy bộ dệt để tạo artifact chứa advice theo pointcut mới.
- **Repackage/redeploy:** phát hành và đặt artifact mới vào môi trường.
- **Reload:** nạp định nghĩa/phiên bản mới qua một ranh giới loader phù hợp.
- **Restart:** kết thúc tiến trình hoặc instance và bắt đầu lại với artifact/cấu hình mới.
- **Runtime redefinition/retransformation:** dùng khả năng đặc biệt của JVM/agent để sửa định nghĩa lớp **đã nạp**, trong giới hạn hỗ trợ.

Một thay đổi có thể hoàn tất ở bước build nhưng **chưa được JVM đang chạy sử dụng**. Ngược lại, đổi cấu hình động trong một advice đã được triển khai có thể không tạo artifact mới nào.

## 3. Compile-time weaving: đổi aspect thường phải xây dựng lại

Trong **dệt ở thời điểm biên dịch**, compiler/weaver tạo ra bytecode đã có advice:

~~~text
Source business + Aspect source/config
                  |
                  v
               Compile/weave
                  |
                  v
             Woven classes/JAR
                  |
                  v
           Deploy + start runtime
~~~

Khi đổi **thân advice**, phải đưa mã advice mới vào những artifact chạy nó. Khi đổi **pointcut**, một lớp từng **không** được weave có thể phải chịu advice và một lớp từng khớp có thể phải được loại bỏ instrumentation cũ; do đó chỉ compile lại riêng class chứa aspect **có thể không đủ**.

Ví dụ trước đây pointcut chọn execution của `BillingService.pay`. Sau khi đổi để chọn thêm `BillingService.refund`, lớp chứa `refund` cần xuất hiện trong **tập weaving mới**. Nếu một incremental build chỉ biên dịch lại source của aspect rồi dùng class nghiệp vụ woven cũ, kết quả có thể thiếu advice tại `refund`.

Nguyên tắc:

1. Phân tích **phụ thuộc giữa aspect, pointcut và class ứng viên**.
2. Đảm bảo incremental build invalidates tất cả artifact chịu tác động.
3. Chạy lại weaving hoặc build đầy đủ theo quy trình được công cụ hỗ trợ.
4. Kiểm tra class/JAR đầu ra và test runtime.
5. Triển khai bản mới để tiến trình thực thi nó.

### 3.1 Thay đổi kiểu mà advice thêm vào chương trình

Một số hệ thống AOP cho phép khai báo thêm **method, field hoặc quan hệ kiểu** trong quá trình dệt. Nếu mã nghiệp vụ cần biên dịch dựa trên thành viên được thêm, phần weaving phải xảy ra đủ sớm để compiler nhận biết chúng.

Sửa loại khai báo này thường ảnh hưởng **cấu trúc lớp** chứ không chỉ thân phương thức. Không nên coi nó như bật một cờ log động. Hot redefine trên JVM tiêu chuẩn còn có các giới hạn riêng đối với thay đổi cấu trúc.

### 3.2 Có phải restart cả máy chủ?

Không nhất thiết **cả máy hoặc toàn cụm**. Thực tế có thể deploy nhiều instance song song rồi chuyển lưu lượng sang instance mới (rolling/canary deployment). Nhưng mỗi JVM instance chạy bản cũ thường phải **ngừng dùng định nghĩa lớp cũ** theo cách hợp lệ: restart process, thay application class loader hoặc một cơ chế cập nhật được hỗ trợ. Triển khai một JAR mới vào thư mục không tự nâng cấp những instance cũ.

## 4. Post-compile weaving: sửa aspect làm thay đổi artifact nhị phân

**Binary weaving** nhận class/JAR đã biên dịch rồi tạo bản woven. Khi thay advice hoặc pointcut:

~~~text
UNWOVEN library/app binaries
             +
UPDATED aspect definitions
             |
             v
         Binary weaver
             |
             v
       NEW woven artifact
             |
             v
         Test / publish
             |
             v
    Runtime loads new artifact
~~~

Các điểm cần lưu ý:

- Nếu **input unwoven** vẫn như cũ, thường không cần biên dịch lại source nghiệp vụ; cần **dệt lại binary với định nghĩa aspect mới**.
- Nếu **aspect được lưu dưới dạng source**, nó có thể phải compile như một phần bước weave.
- Nếu input là JAR bên thứ ba, phải giữ **JAR gốc** để tái tạo bản woven khi pointcut thay đổi.
- **Không weave lại lên output cũ** một cách tùy tiện: reweaving/idempotency phụ thuộc công cụ, có thể báo lỗi hoặc tạo kết quả khác.
- Sau khi tạo woven JAR mới, phải kiểm tra **runtime thực sự nạp** bản mới, không phải artifact cũ trong classpath/container.
- Các class đã được nạp trong JVM không tự đổi khi JAR được thay trên đĩa.

### 4.1 Build cache rất dễ gây bản woven không đồng nhất

Một cache key dành cho bước dệt nên phản ánh ít nhất:

- Hash/version của **input class/JAR**.
- Phiên bản và nội dung aspect hoặc aspect JAR.
- Cấu hình pointcut, precedence, include/exclude filters.
- Phiên bản weaver, JDK/class-file target.
- Classpath/module path và các dependency ảnh hưởng đến match.
- Tùy tool, metadata, generated sources hoặc weaving options.

Nếu cache chỉ nhìn timestamp của source nghiệp vụ, sửa aspect có thể không invalidates lớp cần weave. Dẫn tới môi trường có phần artifact mang advice cũ, phần mang advice mới dù build vẫn báo thành công.

### 4.2 Những thay đổi cần cập nhật chữ ký số và nguồn gốc

Dệt một JAR đã ký làm thay đổi nội dung binary. Phát hành bản woven mới cần quản lý license/quyền sửa, integrity, metadata chữ ký, provenance và rollback; không giữ nhãn gây hiểu nhầm rằng JAR mới là bản gốc chưa đổi.

## 5. Load-time weaving: “lúc nạp” là ranh giới chính

Với **load-time weaving (LTW)**, bộ dệt can thiệp khi class loader định nghĩa lớp theo cấu hình phù hợp:

~~~text
Unwoven class bytes
        |
        v
Loader/agent with weaving configuration
        |
        v
Woven class definition installed in JVM
        |
        v
Object instances and method executions
~~~

Sửa pointcut hoặc cấu hình sau khi nhiều lớp đã được **defined/loaded** đặt ra câu hỏi: các lớp cũ có được dệt lại không? Trong load-time weaving thông thường, **không tự động**.

Ví dụ:

1. JVM khởi động, class `A` được nạp với cấu hình aspect phiên bản 1.
2. Sau đó cập nhật aspect phiên bản 2 để chọn thêm method trong `A`.
3. Nếu không có cơ chế redefine/retransform dành cho lớp đã nạp, **`A` vẫn mang định nghĩa đã cài tại thời điểm nạp**.
4. Một class `B` được nạp sau đó **có thể** được weave theo cấu hình mới nếu loader/agent thật sự sử dụng phiên bản mới, và nếu quy tắc nền tảng cho phép.
5. Kết quả có thể là **hai tập lớp dùng chính sách khác nhau**, điều nguy hiểm khi aspect kiểm soát quyền hoặc audit bắt buộc.

Trong một mô hình LTW có cấu hình aspect được chốt **trước khi loader nạp các kiểu cần dệt**, sửa cấu hình không thể chỉ “reload XML” rồi giả định aspect mới áp dụng cho mọi lớp cũ. Khi cần độ nhất quán trên toàn ứng dụng, restart hoặc redeploy dưới class loader mới thường dễ kiểm soát hơn.

### 5.1 Class loader quyết định phạm vi dệt

Các thành phần có thể được nạp qua loader khác nhau:

- Loader của ứng dụng.
- Loader dành cho plug-in hoặc mô-đun.
- Loader cha chia sẻ thư viện.
- Loader riêng của môi trường thử nghiệm.

Một weaver có thể chỉ can thiệp vào những class loader mà nó kiểm soát hoặc đã được đăng ký. Khi restart một module bằng loader mới, lớp thuộc loader cha có thể **vẫn là phiên bản cũ**. Phải xác định đúng **class nào được định nghĩa bởi loader nào**, và không chỉ dựa vào tên package.

### 5.2 Restart và reloading có phải cùng một điều?

Không. **Reload bằng class loader mới** có thể nạp một bản class mới trong cùng process, nhưng object instance cũ, static state và tham chiếu từ loader cũ vẫn có thể tồn tại. Nếu những tham chiếu đó không được dọn, dễ có memory leak hoặc hành vi pha trộn giữa nhiều phiên bản.

**Restart process** cho một JVM mới với cấu hình và artifact được kiểm soát, nhưng cũng kéo theo vấn đề availability, session, state và rollout; không đồng nghĩa dừng toàn hệ thống khi triển khai nhiều instance.

## 6. Đừng nhầm LTW với runtime instrumentation

Java có gói `java.lang.instrument` cung cấp API cho các **agent** biến đổi mã lớp. Trong một số điều kiện, agent có thể hỗ trợ:

- `Instrumentation.isRetransformClassesSupported()` và `retransformClasses(...)`: yêu cầu biến đổi lại những class đã nạp.
- `Instrumentation.isRedefineClassesSupported()` và `redefineClasses(...)`: thay định nghĩa class bằng class-file bytes mới.
- `Instrumentation.isModifiableClass(...)`: kiểm tra class cụ thể có thể bị thay hay không.

Nhưng **đây là các khả năng tùy chọn**, cần manifest/cấu hình agent thích hợp và được JVM thực tế hỗ trợ. Không thể mặc định một bộ LTW hoặc một agent AOP bất kỳ cung cấp cơ chế hot reweave.

Các giới hạn quan trọng của cơ chế JVM phổ biến:

- Redefine/retransform có các hạn chế về **thay đổi cấu trúc**, ví dụ không tự do thêm/bỏ field hoặc method, đổi method signature hay cấu trúc kế thừa trong mô hình JVM TI tương ứng.
- Không phải mọi class đều modifiable.
- Các **stack frame đang hoạt động** có thể tiếp tục chạy bytecode cũ; các lần invoke mới có thể dùng định nghĩa mới theo hợp đồng instrumentation.
- Cập nhật định nghĩa lớp **không tự chạy lại class initializer** và không tự reset giá trị static field hiện có.
- Bytecode mới phải hợp lệ với verifier/runtime và tương thích việc liên kết tới lớp khác.
- Trạng thái aspect/proxy đang sống có thể không đổi nếu agent chỉ sửa code của một lớp nhưng không thay object/state khác.

**Kết luận của phần này:** runtime redefinition có thể giảm nhu cầu restart trong một số trường hợp, nhưng không phải công tắc thần kỳ để thay mọi pointcut, sinh bất kỳ thành viên mới hoặc áp dụng đồng nhất lên mọi frame/class ngay lập tức.

## 7. Runtime proxy/wrapper: có thể đổi hành vi mà không đổi bytecode

Một kiến trúc **runtime wrapper/proxy** có thể đọc cấu hình động ở mỗi lần gọi. Khi đó có thể thay đổi hành vi advice đã cài đặt bằng cách cập nhật một **state holder** thread-safe.

Sơ đồ:

~~~text
Caller
   |
   v
Stable wrapper/proxy
   |         |
   |         +--> Current policy snapshot
   |                 trace on/off
   |                 validation on/off
   v
Target business method
~~~

Nếu wrapper được thiết kế đọc cấu hình theo **một snapshot trên mỗi lời gọi**, nó có thể bật/tắt tracing hoặc thêm validation đã viết sẵn **không cần rebuild class hoặc restart process**. Tuy nhiên:

- Wrapper chỉ bao phủ các lời gọi đi **qua nó**.
- Nếu pointcut ban đầu không chọn một method nào, **đổi cờ logic** không tự tạo wrapper tại method ấy.
- Nếu cần thêm kiểu join point không được mô hình proxy hỗ trợ, phải dùng kỹ thuật can thiệp thích hợp.
- Nếu không thiết kế để thay handler/interceptor chain động, việc sửa lớp implementation của proxy có thể vẫn cần rebuild/redeploy.
- Bật/tắt một policy được thiết kế sẵn khác với thay source của advice bằng một source mới và mong nó xuất hiện ngay ở JVM cũ.

### 7.1 Phân biệt ba dạng thay đổi

| Tình huống | Có thể đổi động trong wrapper thiết kế phù hợp? |
| --- | --- |
| Bật/tắt logging code **đã tồn tại** | Có thể: đổi cờ cấu hình |
| Thay ngưỡng cảnh báo đã được advice đọc động | Có thể: cập nhật cấu hình |
| Đổi `execution(pay)` thành `execution(refund)` ở class không hề được chặn | Không tự động chỉ bằng cờ; cần cập nhật phạm vi can thiệp |
| Thay bytecode của một phương thức nghiệp vụ đã nạp | Không chỉ bằng cờ; cần deploy/reload/instrumentation phù hợp |
| Thêm field/method vào class đã định nghĩa | Không chỉ bằng cờ hoặc hot redefine tùy ý |
| Đổi thứ tự interceptor chain | Có thể nếu chuỗi được thiết kế thay động và cập nhật nhất quán; không bảo đảm ở mọi proxy |

Khi đổi handler/chain lúc chạy, cần tránh lỗi **trộn cấu hình nửa cũ/nửa mới** trong một request. Một kỹ thuật là lưu một đối tượng cấu hình **bất biến** và thay tham chiếu nguyên tử, để từng invocation lấy một snapshot nhất quán.

## 8. Ví dụ Java 17 chạy độc lập: cập nhật chính sách khi tiến trình đang chạy

Chương trình sau minh họa **một lớp bọc thủ công mang ngữ nghĩa advice**, không phải cú pháp aspect của bộ AOP nào. Mục tiêu là thấy rõ: thay đổi **cờ xử lý trong advice đã được lắp sẵn** có thể có hiệu lực ở lần gọi sau **mà không rebuild hoặc restart**.

Ta dùng `AtomicReference<Policy>` và `record Policy` bất biến. Wrapper chụp một policy duy nhất khi bắt đầu mỗi lời gọi để xử lý đồng nhất trong suốt lần đó.

Lưu toàn bộ mã dưới tên `DynamicAdvicePolicyDemo.java`:

~~~java
import java.util.concurrent.atomic.AtomicReference;

public class DynamicAdvicePolicyDemo {
    interface Task {
        String execute(String id);
    }

    record Policy(boolean trace, boolean validate) {}

    static final class BusinessTask implements Task {
        @Override
        public String execute(String id) {
            System.out.println("BUSINESS " + id);
            return "done:" + id;
        }
    }

    static final class AdviceWrapper implements Task {
        private final Task target;
        private final AtomicReference<Policy> policy;

        AdviceWrapper(Task target, AtomicReference<Policy> policy) {
            this.target = target;
            this.policy = policy;
        }

        @Override
        public String execute(String id) {
            Policy current = policy.get(); // Một snapshot cho lần gọi.
            if (current.trace()) {
                System.out.println("ADVICE BEFORE");
            }

            try {
                if (current.validate()
                        && (id == null || id.isBlank())) {
                    throw new IllegalArgumentException("blank id");
                }

                String result = target.execute(id);

                if (current.trace()) {
                    System.out.println("ADVICE AFTER " + result);
                }
                return result;
            } catch (RuntimeException error) {
                if (current.trace()) {
                    System.out.println("ADVICE ERROR "
                            + error.getClass().getSimpleName());
                }
                throw error;
            }
        }
    }

    public static void main(String[] args) {
        AtomicReference<Policy> setting =
                new AtomicReference<>(new Policy(false, false));

        Task task = new AdviceWrapper(new BusinessTask(), setting);

        System.out.println("CASE original");
        System.out.println("RESULT " + task.execute("A"));

        // Thay cấu hình; không tạo lớp/proxy mới và không restart.
        setting.set(new Policy(true, true));
        System.out.println("CASE live-update");
        System.out.println("RESULT " + task.execute("B"));

        System.out.println("CASE invalid");
        try {
            task.execute("");
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER " + error.getMessage());
        }

        setting.set(new Policy(false, true));
        System.out.println("CASE trace-off");
        System.out.println("RESULT " + task.execute("C"));
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac DynamicAdvicePolicyDemo.java
java DynamicAdvicePolicyDemo
~~~

Stdout:

~~~text
CASE original
BUSINESS A
RESULT done:A
CASE live-update
ADVICE BEFORE
BUSINESS B
ADVICE AFTER done:B
RESULT done:B
CASE invalid
ADVICE BEFORE
ADVICE ERROR IllegalArgumentException
CALLER blank id
CASE trace-off
BUSINESS C
RESULT done:C
~~~

### 8.1 Kết quả minh họa điều gì?

**Lần A:** wrapper tồn tại nhưng `trace=false`, `validate=false`. Nó gọi nghiệp vụ và trả `done:A` mà không in advice.

**Cập nhật cấu hình:** `setting.set(new Policy(true, true))` thay object Policy bằng thao tác nguyên tử. Không có bước compile/weave/reload class; chỉ thay state được wrapper đọc ở lần gọi sau.

**Lần B:** wrapper đọc snapshot mới, in BEFORE, gọi nghiệp vụ và in AFTER với kết quả.

**Lần ID rỗng:** cấu hình `validate=true` làm wrapper ném `IllegalArgumentException` **trước khi gọi** BusinessTask; vẫn in nhánh ADVICE ERROR vì trace đang bật. Không có dòng `BUSINESS` cho request này.

**Lần C:** trace được tắt lại nhưng validation vẫn bật; nghiệp vụ nhận ID hợp lệ, kết quả trả bình thường không có dòng tracing.

### 8.2 Những điều ví dụ KHÔNG chứng minh

- Không có một bộ weaver tự chèn advice hoặc một ngôn ngữ pointcut thực sự. Đây là **wrapper Java thủ công** mô phỏng hành vi AOP ở một điểm đã được bọc.
- Không thêm join point mới; `Task.execute` đã có wrapper từ đầu.
- Không thay bytecode của `BusinessTask`, không redefine class, không cài agent và không động đến class loader.
- Nếu gọi trực tiếp `new BusinessTask().execute("X")`, wrapper không nhìn thấy; do đó không thể lấy ví dụ này làm bằng chứng về phạm vi phủ của một chính sách an ninh bắt buộc.
- `AtomicReference` giúp thay tham chiếu policy an toàn, nhưng **không tự đồng bộ rollout trên nhiều JVM** hoặc đảm bảo yêu cầu đang chạy đổi policy giữa chừng; mỗi invocation trong ví dụ chụp một snapshot.
- Mã cố ý không tích hợp nguồn xác thực. Nếu validation ở production liên quan bảo mật, mọi đường vào phải được kiểm soát ở ranh giới đáng tin, không phụ thuộc duy nhất vào một cờ có thể bị tắt sai.

## 9. Ví dụ luồng xây dựng lại ở compile-time/post-compile

Đối với hệ thống compile-time hoặc binary weaving, khi sửa pointcut nên áp dụng pipeline:

~~~text
1. Edit aspect source / pointcut / precedence
2. Compile aspect (when required)
3. Reweave all impacted sources/classes/JARs
4. Test woven artifact and compare join point coverage
5. Publish artifact version
6. Deploy new version (restart/reload as supported)
7. Verify runtime loaded the intended version
~~~

Sơ đồ ở trên là **quy trình khái niệm**, không phải cú pháp lệnh chung cho mọi weaver.

### 9.1 Ví dụ logic trước/sau thay pointcut

Trước thay đổi:

~~~text
P_v1 = execution(payment.process(..))
Advice applies to:
    payment.process
~~~

Sau thay đổi:

~~~text
P_v2 = execution(payment.process(..))
       OR execution(refund.process(..))
Advice should apply to:
    payment.process
    refund.process
~~~

Đây là **giả mã mô tả tập điểm**, không phải cú pháp AOP dùng chung. Với weaver tạo mã lúc build, phần `refund.process` phải thực sự xuất hiện trong bản woven mới. Nếu runtime vẫn dùng class chưa dệt hoặc chỉ có `P_v1`, việc sửa source của aspect không thể làm advice tự xuất hiện ở `refund`.

### 9.2 Khi nào chỉ cần sửa cấu hình?

Nếu một aspect đã có điều kiện động:

~~~text
selected join point = all operations needed
within advice:
    if currentConfig.traceEnabled:
        emit event
    proceed
~~~

thay `traceEnabled` có thể chỉ là thao tác cập nhật cấu hình, **nếu advice đã được weave/chặn ở đúng phạm vi**, cấu hình được đọc động và bộ phận quản lý cấu hình bảo đảm tính nhất quán. Việc đặt pointcut quá rộng để cho phép đổi tùy ý về sau lại có **chi phí và rủi ro**: mọi join point trong phạm vi vẫn có thể chịu overhead của phần kiểm tra động.

## 10. Sửa aspect ảnh hưởng tới thứ tự advice và trạng thái

### 10.1 Precedence có thể thay đổi ranh giới an ninh

Giả sử:

~~~text
Authorize(Cache(Business))
~~~

Authorization bao ngoài Cache: khi cache hit, quyền vẫn được kiểm tra trước. Nếu thay cấu hình/precedence thành:

~~~text
Cache(Authorize(Business))
~~~

một cache hit ở ngoài có thể **không gọi tiếp** và bỏ qua kiểm tra bên trong. Vì vậy thay precedence **không chỉ là sửa thứ tự log**, và phải được triển khai/test như một thay đổi ngữ nghĩa có ảnh hưởng tới bảo mật.

### 10.2 Thay trạng thái aspect có thể khác thay mã advice

Một aspect có thể chứa bộ đếm, cache, dữ liệu tạm, cấu hình theo instance hoặc theo thread. Khi triển khai mã aspect mới, phải xác định:

- Trạng thái cũ có còn tồn tại trong object cũ không?
- Có tạo aspect instance mới, hay giữ instance cũ và chỉ đổi code?
- Có cần migrate hoặc reset counters/caches/registrations?
- Thread-local values có được cleanup đúng không?
- Có nhiều class loader/phiên bản aspect cùng hiện diện không?
- Các request đang chạy sẽ dùng rule cũ hay mới?

Nếu chỉ đổi một cờ trong state holder, mã class không đổi. Nếu thêm field vào class đã nạp, hot redefine có thể không hỗ trợ. Nếu chuyển cả loader, object cũ còn tham chiếu có thể vẫn sử dụng aspect cũ, gây hành vi pha trộn.

### 10.3 Những điểm mới sinh ở runtime

Nếu một plugin hoặc generator nạp class mới sau lúc cấu hình weaving thay đổi, tùy load-time weaver, class ấy **có thể** được dệt theo phiên bản mới. Nhưng điều đó **không** có nghĩa class trước đó đã được cập nhật. Nguy cơ mixed-version là đáng kể khi cùng một chính sách audit hay authorization phải áp dụng nhất quán.

## 11. Triển khai thực tế: có cần downtime không?

Một hệ thống nhiều instance có thể thực hiện **rolling deployment**:

~~~text
Instance A: old woven version
Instance B: old woven version
Instance C: old woven version

Deploy new version to A and verify
Deploy new version to B and verify
Deploy new version to C and verify
~~~

Trong giai đoạn chuyển đổi có thể đồng thời tồn tại **hai phiên bản aspect**. Nếu aspect ảnh hưởng bảo mật, serialization, transaction hoặc giao thức liên hệ giữa instance, phải xác định có chấp nhận giai đoạn này không.

Các chiến lược thường cần cân nhắc:

- **Rolling update:** thay dần các instance; bảo đảm policy giữa phiên bản cũ/mới có thể cùng tồn tại an toàn.
- **Blue–green:** chuẩn bị nhóm instance mới, test rồi chuyển lưu lượng; cần kiểm tra trạng thái/dữ liệu chia sẻ.
- **Canary:** áp dụng cho tỷ lệ lưu lượng nhỏ và đo hành vi trước mở rộng.
- **Restart có kế hoạch:** khi trạng thái hoặc class loader khiến hot swap không an toàn.
- **Dynamic toggle:** dành cho hành vi đã được thiết kế bật/tắt an toàn, không dùng để thay thế mọi bước kiểm thử.

“Không phải restart toàn cụm” khác với “không instance nào phải restart”. Việc cần dừng một JVM để dùng class woven mới có thể vẫn được che giấu khỏi người dùng bằng kiến trúc triển khai nhiều bản sao và chuyển lưu lượng thích hợp.

## 12. Kiểm thử trước khi quyết định không restart

| Thay đổi | Kiểm tra bắt buộc |
| --- | --- |
| Advice log đổi cách ghi | Mã mới thật sự được gọi? Có lộ dữ liệu nhạy cảm? Có lỗi khi log? |
| Pointcut được mở rộng | Join point mới xuất hiện trong artifact/trace đúng như ý định? |
| Pointcut thu hẹp | Các lớp cũ có còn chứa advice trước đây không? |
| Precedence đổi | Authorization, transaction, audit, cache và retry còn đúng thứ tự không? |
| LTW config đổi | Những class đã nạp có được cập nhật hay chỉ những class nạp sau? |
| Proxy handler đổi động | Mọi proxy thực sự nhìn state/chain mới? Lời gọi đang chạy có snapshot ổn định? |
| Agent retransform | JVM/agent cho phép? Class modifiable? Thay đổi có nằm trong giới hạn? |
| Code thêm field/method | Có thuộc thay đổi cấu trúc không được hot redefine hỗ trợ? |
| Build cache/incremental | Clean build và incremental tạo tập join point/behavior tương đương? |
| Library dependency đổi | Classpath/module path và binary compatibility còn đúng? |
| Rolling deployment | Hai phiên bản policy có thể cùng tồn tại an toàn? |
| Rollback | Có artifact/config cũ và cơ chế trở lại trạng thái ổn định? |

### 12.1 Ba lớp xác minh

**Xác minh artifact:** hash/version, bytecode được dệt, danh sách join point, đúng JAR trên classpath, không instrument trùng.

**Xác minh runtime:** advice thực sự chạy trên những đường gọi cần thiết, thứ tự before/after, kết quả và ngoại lệ được bảo toàn, lỗi và cleanup đúng.

**Xác minh vận hành:** rollout và rollback, nhiều instance, thời điểm áp dụng cấu hình, metrics/audit, hiệu năng, dữ liệu nhạy cảm và policy consistency.

Chỉ thấy file build mới hoặc log “configuration reloaded” không đủ chứng minh toàn bộ hệ thống đã chuyển sang aspect phiên bản mới.

## 13. Những ngộ nhận cần tránh

1. **“Sửa source aspect là JVM tự nhận.”** Sai khi class/advice đã compile/weave và nạp mà không có cơ chế cập nhật.
2. **“Post-compile weaving phải biên dịch lại mọi source nghiệp vụ.”** Không nhất thiết; có thể dệt lại từ binary chưa dệt, nhưng phải cập nhật mọi artifact bị ảnh hưởng.
3. **“Đổi pointcut giống đổi ngưỡng log.”** Không: pointcut có thể đổi tập join point cần can thiệp.
4. **“Load-time weaving luôn áp dụng ngay cả class đã nạp.”** Không; thường phải reload/restart hoặc dùng instrumentation hỗ trợ riêng.
5. **“Thay JAR trên đĩa làm JVM đang chạy dùng code mới.”** Không tự động thay định nghĩa class đã nạp.
6. **“Mọi JVM đều hỗ trợ retransform/redefine mọi class.”** Sai; năng lực tùy JVM/agent và từng class.
7. **“Đổi mọi cấu trúc class đều có thể hot swap.”** Không; có giới hạn field/method/inheritance trong các cơ chế chuẩn tương ứng.
8. **“Tất cả stack frame lập tức chạy code mới.”** Sai với các phương thức đang active theo ngữ nghĩa instrumentation.
9. **“Bật/tắt tracing không cần restart nên thêm execution join point cũng không cần.”** Không suy được; thay cờ không làm xuất hiện hook mới.
10. **“Đổi cấu hình LTW rồi class cũ và mới chắc chắn đồng bộ.”** Không; thời điểm nạp và loader có thể gây mixed-version.
11. **“Một proxy được tạo một lần luôn đọc chain mới.”** Chỉ đúng khi được thiết kế thay chain/config động.
12. **“Reweaving luôn idempotent.”** Không có bảo đảm chung, phải theo hợp đồng công cụ.
13. **“Thứ tự tệp aspect quyết định advice precedence.”** Không thể dựa vào thứ tự file nếu mô hình không định nghĩa.
14. **“Restart toàn cụm là cách duy nhất.”** Không; có triển khai từng instance, nhưng tính nhất quán policy phải kiểm tra.
15. **“Có thể bỏ kiểm thử vì chỉ đổi log.”** Sai: advice có thể gây lỗi, tăng overhead hoặc rò dữ liệu.

## 14. Checklist ra quyết định

Khi nhận yêu cầu thay một aspect đang dùng trong production, hãy đi theo thứ tự:

1. **Định danh thay đổi:** sửa source advice, pointcut, precedence, inter-type, state hay chỉ config?
2. **Xác nhận mô hình dệt:** compile-time, post-compile, LTW, runtime proxy, hay hỗn hợp?
3. **Phân tích phạm vi:** những join point/class nào đã hoặc sẽ chịu ảnh hưởng?
4. **Xác minh thời điểm nạp:** class mục tiêu đã được định nghĩa trong JVM chưa? Qua class loader nào?
5. **Chọn cơ chế cập nhật:** rebuild/reweave, new class loader, process restart, dynamic chain/config, hay runtime instrumentation được hỗ trợ?
6. **Kiểm tra tính năng:** nếu hot redefine/retransform, JVM và agent có thực sự hỗ trợ class ấy và kiểu thay đổi này không?
7. **Bảo toàn trạng thái:** aspect instances, caches, counters, thread context, transactions và active frames sẽ ra sao?
8. **Đóng gói:** artifact/version/manifest/dependencies/provenance đúng chưa? Có bản unwoven để tái tạo?
9. **Kiểm thử:** success/error, call/execution, proxy bypass, security, audit, retry, short-circuit, overhead.
10. **Rollout và rollback:** thay từng instance hay toàn bộ, có giai đoạn mixed-version không?
11. **Quan sát:** kiểm chứng bằng trace và kết quả thực tế rằng **phiên bản mới đang có hiệu lực**.
12. **Tài liệu hóa:** ghi rõ điều kiện nào không restart, điều kiện nào bắt buộc restart hoặc reload.

## 15. Bài tập tự kiểm tra

**Bài 1.** Aspect đang được compile-time weave chọn `execution(pay)`. Đổi pointcut để chọn thêm `execution(refund)`, rồi chỉ thay tệp cấu hình mà không xây lại lớp `refund`. Có chắc advice sẽ xuất hiện tại `refund`?

**Đáp:** không. Tập join point đã dệt thường được quyết định khi build; cần weave lại các class chịu ảnh hưởng và chạy artifact mới.

**Bài 2.** Một JAR được binary weave hôm qua. Hôm nay thay source aspect, nhưng JVM vẫn chạy class cũ. Chỉ thay JAR woven trên đĩa có đủ?

**Đáp:** không. Phải bảo đảm runtime **nạp định nghĩa mới** qua restart/reload hoặc khả năng redefine/retransform phù hợp.

**Bài 3.** LTW agent bắt đầu với pointcut phiên bản 1 và đã nạp class A. Sau đó cấu hình được sửa để chọn thêm method của A. Class A tự cập nhật không?

**Đáp:** không thể giả định. Load-time weaving bình thường dệt lúc nạp class; muốn cập nhật class đã định nghĩa cần cơ chế hỗ trợ riêng hoặc nạp lại trong môi trường mới.

**Bài 4.** Advice đã được cài sẵn quanh `execute` và đọc `AtomicReference<Policy>`. Thay Policy có bắt buộc rebuild/restart không?

**Đáp:** trong mô hình ví dụ Java, không. Wrapper đã tồn tại và đọc snapshot mới mỗi invocation. Nhưng điều đó **không thêm join point mới** và không đảm bảo cập nhật đồng thời mọi JVM.

**Bài 5.** Cần thêm field vào class nghiệp vụ đã nạp vì aspect mới khai báo trạng thái. Có thể mặc định Java Instrumentation redefine class không restart được không?

**Đáp:** không. Thêm field là thay đổi cấu trúc bị hạn chế trong khả năng redefine/retransform tiêu chuẩn; phải xác minh phương án khác, thường cần build/redeploy và class definition mới.

**Bài 6.** Agent có hỗ trợ retransformation, nhưng lời gọi hiện tại đang chạy bytecode cũ. Việc retransformation thành công có tự thay những active frames đó không?

**Đáp:** không theo hợp đồng Java Instrumentation thông thường: active frames có thể tiếp tục chạy định nghĩa cũ, còn các lời gọi mới dùng định nghĩa cập nhật.

**Bài 7.** Tại sao một thay đổi security aspect cần cân nhắc giai đoạn rolling deployment?

**Đáp:** vì các instance cũ và mới có thể cùng phục vụ request với các quy tắc kiểm tra quyền khác nhau. Phải thiết kế policy versioning và chiến lược rollout tương thích, tránh khoảng trống bảo mật.

## 16. Tham khảo

- [AspectJ Developer Guide – Load-Time Weaving](https://eclipse.dev/aspectj/doc/latest/devguide/ltw.html): mô tả ba thời điểm compile-time, post-compile và load-time weaving trong một hệ thống AOP trên JVM, cùng điều kiện class-loader và giới hạn lớp đã nạp.
- [Java SE 17 – Instrumentation API](https://docs.oracle.com/en/java/javase/17/docs/api/java.instrument/java/lang/instrument/Instrumentation.html): `isRetransformClassesSupported`, `retransformClasses`, `redefineClasses` và ảnh hưởng lên các stack frames/class đã định nghĩa.
- [Java SE 17 – ClassFileTransformer](https://docs.oracle.com/en/java/javase/17/docs/api/java.instrument/java/lang/instrument/ClassFileTransformer.html): hợp đồng transformer, thời điểm áp dụng và thứ tự biến đổi.
- [Java SE 17 – AtomicReference](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicReference.html): ví dụ một nơi lưu cấu hình có thể cập nhật an toàn ở runtime.
- [JVM Specification, Java 17 – Class Loading and Linking](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): khái niệm nạp, liên kết và định nghĩa class.

**Ghi nhớ:** việc cần **rebuild**, **reweave**, **redeploy** hay **restart** phụ thuộc vào **lúc aspect được kết hợp với chương trình** và **cấu trúc nào thay đổi**. Compile-time và post-compile weaving thường yêu cầu tạo artifact mới; LTW bình thường chỉ áp dụng khi lớp được nạp; dynamic wrapper có thể cho thay đổi hành vi đã được cài sẵn; runtime instrumentation là một khả năng riêng có giới hạn. Luôn xác minh **tập join point hiệu lực, artifact đã nạp, trạng thái đang chạy và kết quả nghiệp vụ**, thay vì dựa vào một khẳng định chung rằng “đã reload aspect”.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="weaving-lifecycle-006">Ảnh hưởng đến đóng gói, triển khai và tính lặp lại của bản dựng</a>

<details>
<summary>Xem chi tiết</summary>

# Ảnh hưởng đến đóng gói, triển khai và tính lặp lại của bản dựng

## 1. Vì sao thời điểm weaving ảnh hưởng cả quá trình phát hành?

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP)**, **aspect** đóng gói mối quan tâm cắt ngang; **pointcut** lựa chọn các *join point* có thể can thiệp; **advice** định nghĩa hành vi chạy tại chúng. **Weaving (dệt)** là cách kết hợp aspect với chương trình mục tiêu để advice được thực thi ở các join point phù hợp.

Thời điểm weaving không chỉ quyết định “gắn advice lúc nào”, mà còn tác động trực tiếp đến:

- **Artifact phát hành:** class/JAR đã được dệt hay JAR gốc đi kèm runtime weaver, agent hoặc proxy?
- **Dependency và đóng gói:** công cụ weaving, thư viện runtime, cấu hình pointcut, phiên bản JDK và classloader nào bắt buộc hiện diện?
- **Thời điểm kiểm tra:** lỗi pointcut/bytecode được phát hiện lúc build, nạp class hay khi một lời gọi thật đi qua interceptor?
- **Cách triển khai và rollback:** có cần rebuild artifact, đổi agent/flags hoặc tái tạo chuỗi proxy không?
- **Tính tái lập:** cần cố định những inputs nào để hai lần build cho cùng bytes và hai môi trường chạy cho cùng hành vi advice?

Một phân biệt then chốt:

> **Reproducible build**: cùng những đầu vào được kiểm soát tạo artifact cuối giống nhau theo tiêu chí đã công bố. **Reproducible deployment**: cùng artifact cùng môi trường/cấu hình can thiệp tạo phạm vi và hành vi advice tương đương.

Hai mục tiêu liên quan, nhưng **một JAR có cùng SHA-256 không tự bảo đảm advice chạy giống nhau** khi agent, proxy wiring hoặc runtime config khác.

Bài học chỉ trình bày **AOP/Aspect tổng quát và nâng cao**, tập trung vào các thời điểm weaving và hậu quả kiến trúc. Mọi ví dụ Java chạy độc lập bằng JDK 17+, không cần một hệ AOP chuyên biệt.

## 2. Bốn mô hình cần phân biệt

| Mô hình | Khi nào kết hợp/can thiệp | Thứ được triển khai |
| --- | --- | --- |
| **Compile-time weaving (CTW)** | Trong bước biên dịch/build | Artifact thường đã chứa mã được dệt |
| **Post-compile/binary weaving** | Sau compile, trước runtime | Artifact raw và artifact woven là hai trạng thái phải quản lý |
| **Load-time weaving (LTW)** | Khi JVM nạp hoặc định nghĩa class | Có thể là raw bytecode cùng runtime weaver/agent/config |
| **Runtime proxy/interceptor** | Khi lời gọi thực đi qua wrapper | Target bytecode có thể nguyên trạng, hành vi nằm ở chuỗi proxy |

Các công nghệ JVM còn có thể cung cấp **retransformation/redefinition** đối với class đã được định nghĩa, tùy khả năng agent/JVM. Đây là một cơ chế khác LTW thông thường và khác proxy.

Sơ đồ tổng quan:

~~~text
CTW:
source + aspects -> compile/weave -> woven classes -> package -> JVM

Binary:
source -> javac -> raw classes -> weaver -> woven classes -> package -> JVM

LTW:
source -> javac -> raw JAR -----------+
weaver agent + policy ---------------+-> JVM class loading -> transformed class

Runtime proxy:
source -> javac -> app.jar -> create proxy -> intercept -> target
~~~

Hai điều dễ nhầm:

1. **Dệt tĩnh không có nghĩa advice chạy lúc build.** Việc kết hợp diễn ra sớm; advice vẫn chạy khi join point xuất hiện trong chương trình đang thực thi.
2. **LTW không đồng nghĩa proxy.** LTW thường biến đổi class bytecode trong quá trình nạp, còn proxy định tuyến lời gọi qua một đối tượng trung gian và không nhất thiết sửa bytecode target.

## 3. Compile-time weaving: mọi thứ quan trọng diễn ra trong build

Với CTW, compiler/weaver kết hợp mã nghiệp vụ và aspect rồi tạo ra class hoặc artifact mang hành vi AOP. Khi phát hành, bản build đã chứa logic can thiệp ở những nơi công cụ thực sự hỗ trợ và đã dệt.

~~~text
pinned source + pinned dependencies + aspect definitions
                         |
                compiler/weaver
                         |
                    woven classes
                         |
                  package / test
                         |
                    release.jar
~~~

**Ảnh hưởng đến đóng gói:**

- Artifact cuối là sản phẩm của **compiler cộng weaver**, không chỉ riêng `javac`.
- Runtime có thể vẫn cần thư viện hỗ trợ advice do công cụ sử dụng, dù quá trình dệt đã xong.
- Ký hoặc hash JAR cần thực hiện **sau** mọi bước biến đổi bytecode và packaging cuối.
- Có thể test ngay trên class/JAR đã dệt, tạo chứng cứ cụ thể cho bản phát hành.

**Ảnh hưởng đến triển khai:**

- Thay đổi định nghĩa aspect hoặc pointcut thường dẫn đến **rebuild/redeploy** các artifact liên quan.
- Nếu build đã dệt sai, đổi cấu hình runtime thường không thể tự sửa phần bytecode đã kết hợp, trừ khi công nghệ còn cung cấp cơ chế khác.
- Stack trace và debugging có thể phản ánh mã đã biến đổi; cần source mappings và symbol phù hợp.
- Đã dệt một số join point **không bảo đảm** các loại join point khác được can thiệp.

**Ảnh hưởng đến reproducibility:**

- Weaver version, transformation order, input classpath, generated metadata và output packaging đều là đầu vào cần kiểm soát.
- Cùng source nhưng khác weaver hoặc cờ weaving có thể tạo class khác.
- Một công cụ không tất định có thể tạo bytecode khác ngay khi source không đổi.
- Unit test chỉ chạy raw code chưa đủ: **integration test trên woven artifact cuối** mới chứng minh advice được kích hoạt.

Không nói CTW “luôn chạy nhanh hơn” những mô hình khác: advice vẫn gây runtime overhead; JIT, logging, I/O, số join point và cách tổ chức đều ảnh hưởng hiệu năng.

## 4. Binary weaving: bảo vệ ranh giới raw và woven

Trong **post-compile weaving**, class đã biên dịch được đưa qua một bước biến đổi khác:

~~~text
source -> javac -> build/raw-classes/
                         |
                       weave
                         |
                         v
                 build/woven-classes/
                         |
                       package
                         |
                         v
                    release.jar
~~~

Giữ riêng hai loại output mang lại lợi ích lớn:

- Biết chính xác **đầu vào dệt** và **kết quả dệt**, thuận lợi khi so bytecode.
- Không vô tình dùng class đã woven làm raw input ở lần build sau.
- Có thể chạy test với raw classes và woven classes để cô lập bug do instrumentation.
- Dễ truy nguyên class nào được dệt, công cụ nào dệt và version nào.
- Giảm nguy cơ **dệt lặp** nếu một pipeline package tiếp tục instrument bytecode đã biến đổi.

Một pipeline rủi ro:

~~~text
compile -> output/
weave output/ -> output/
package output/
lần build sau dùng lại output/ như raw input -> weave lại
~~~

Lần weave thứ hai có thể bị từ chối, bỏ qua hoặc chèn trùng, **phụ thuộc công cụ**. Không mặc định “dệt lần hai luôn gây advice chạy gấp đôi” hay “weaver luôn idempotent”.

### Vị trí signing và shading

Nếu JAR có chữ ký integrity, sửa class entries **sau khi ký** có thể làm chữ ký không hợp lệ. Cần hoàn tất weaving, shading, relocation và archive packaging trước khi ký artifact phát hành cuối.

Các công cụ đổi tên package/class hoặc gom thư viện thành uber-JAR có thể thay signature mà pointcut phụ thuộc. Thứ tự “weave rồi relocate” hay “relocate rồi weave” phải do pipeline quyết định và được test trên artifact thực, không có một thứ tự chung phù hợp mọi công cụ.

## 5. Load-time weaving: chính deployment trở thành một phần weaving

**Load-time weaving (LTW)** thường biến đổi class bytecode trước hoặc trong khi JVM định nghĩa class. Ứng dụng có thể phát hành bytecode gốc nhưng runtime cần đủ thành phần để weaving xảy ra.

| Đầu vào runtime | Câu hỏi kiểm soát |
| --- | --- |
| Weaver/agent binary | Có đúng version và checksum đã duyệt? |
| Aspect/pointcut config | Có cùng policy và phạm vi class? |
| JVM version và options | Có hỗ trợ bytecode và khả năng instrumentation cần thiết? |
| Classloader | Class mục tiêu do loader nào định nghĩa? |
| Load order | Transformer có được đăng ký **trước** lúc class mục tiêu load? |
| Module system | Có hạn chế quyền truy cập/biến đổi class? |
| Các agent khác | Có policy chồng lấn hay transformer chain khác? |
| Startup lifecycle | Class nào load sớm, class nào load lazy? |
| Error policy | Khi weaving thất bại, ứng dụng fail-open hay fail-closed? |

**Cùng app.jar SHA-256 nhưng agent version khác** có thể tạo behavior khác. Vì vậy, bộ manifest triển khai cần ghi không chỉ app artifact hash mà còn **weaver/agent/config và những JVM flags liên quan**.

Nếu agent được bật sau khi class mục tiêu đã định nghĩa, LTW thông thường có thể bỏ lỡ class ấy. Một số cơ chế instrumentation hỗ trợ retransformation/redefinition của class đã load, nhưng có quyền và hạn chế riêng; **không coi chúng là LTW tự động**.

Một vấn đề khác là **classloader identity**. Class cùng tên từ hai loaders có thể là hai runtime classes độc lập. Nếu một máy load qua loader A còn máy khác qua loader B, weaving scope và kiểm tra điểm can thiệp có thể khác nhau dù artifact file có cùng nội dung.

## 6. Runtime proxy/interceptor: JAR không đổi vẫn có thể đổi advice

Một hệ thống có thể tạo proxy ở runtime và chuyển lời gọi qua handler:

~~~text
Caller -> Proxy / Interceptor -> Target method
             |
        match policy?
        before / after / around behavior
~~~

**Điều kiện tiên quyết:** caller phải giữ **tham chiếu proxy** ở ranh giới muốn can thiệp. Caller gọi thẳng target sẽ bỏ qua proxy đó; lời gọi nội bộ qua `this.otherMethod()` có thể cũng không đi qua wrapper.

Những yếu tố làm behavior khác dù JAR không đổi:

- Runtime flag cho phép hoặc vô hiệu advice.
- Đường tạo proxy khác nhau giữa hai môi trường.
- Cấu hình interceptor chain và thứ tự ưu tiên khác.
- Caller vô tình giữ target thay vì proxy.
- Hai proxy cùng chức năng bọc nhau, làm advice chạy lặp.
- Pointcut runtime có điều kiện theo metadata hoặc trạng thái.
- Phiên bản thư viện tạo proxy hoặc policy config được nạp từ bên ngoài khác nhau.

Không phải mọi proxy được bật/tắt động tùy ý: nếu handler đọc config một lần lúc startup, đổi config ở runtime có thể chưa có tác dụng.

**Proxy và weaving không cùng độ phủ.** Một engine bytecode có thể can thiệp method execution bên trong target khi class được dệt, còn proxy thông thường chỉ thấy lời gọi đi qua wrapper. Việc có `private`, `final`, `static`, constructor, field access hoặc exception-handler join point phụ thuộc mô hình công cụ cụ thể. Không kết luận rằng “mọi AOP đều nhìn được mọi method”.

## 7. Reproducible build và reproducible deployment khác nhau ra sao?

### 7.1. Tái lập inputs

Hai bản build dùng cùng source commit, dependencies và công cụ. Đây chỉ là **điều kiện đầu vào**; output vẫn có thể khác vì timestamp, file ordering, nondeterministic bytecode generation hoặc build metadata.

### 7.2. Tái lập từng byte

Hai artifact độc lập có cùng chuỗi byte; do đó SHA-256 giống hệt. Muốn đạt được phải kiểm soát:

1. Tên và phiên bản compiler/JDK.
2. Cờ biên dịch như `--release` và debug info.
3. Encoding, line endings, generated source, generated resources.
4. Dependency versions/checksums và classpath/module path.
5. Weaver version, cấu hình, thứ tự transformation.
6. Các file class/asset được đưa vào archive.
7. Thứ tự ZIP/JAR entries và timestamps.
8. Manifest, module metadata, version string, host path và build time.
9. Công cụ shading/relocation, compression và signing.
10. Build cache và độ sạch của thư mục output.

**Hash bằng nhau** chứng minh bytes của hai artifact được so là giống nhau, không chứng minh runtime config giống nhau. **Hash khác nhau** không tự khẳng định behavior khác; khác timestamp cũng có thể đổi hash.

### 7.3. Tái lập behavior khi triển khai

Giả sử app JAR hash H xuất hiện trên cả hai máy:

~~~text
Environment A:
  app.jar hash = H
  auditProxy.enabled = false

Environment B:
  app.jar hash = H
  auditProxy.enabled = true

Build bytes: giống
Audit behavior: khác
~~~

Tương tự, LTW với agent version khác hoặc class loading order khác có thể thay đổi tập join point thực sự được instrument. Vì vậy, deployment release nên gắn một **bộ thông tin gồm app hash + runtime toolchain + AOP policy/configuration**, thay vì chỉ app hash.

## 8. Kiểm soát determinism của bản dựng

| Nhóm input | Cần cố định/kiểm tra |
| --- | --- |
| Source | Commit, source generator, encoding và line ending |
| Dependency | Phiên bản/checksum cụ thể, không dùng phiên bản trôi nổi |
| JDK/compiler | Phiên bản exact, target bytecode, compilation flags |
| Weaver | Binary, policy, transformation order, module filters |
| Raw/woven boundary | Không trộn output giữa các bước |
| Archive | Stable entry order, timestamp, manifest, resource processing |
| Generated metadata | Tránh current time, machine name, username, random UUID |
| Code signing | Ký artifact cuối, ghi nhận signing process và timestamping |
| Build runner | Locale, OS/tool behavior khi có ảnh hưởng |
| Tests | So artifact cuối và chạy integration tests thật |

Có thể hướng tới **hermetic build**: mọi input ảnh hưởng output đều khai báo, không tự lấy latest dependency hoặc đọc file ngẫu nhiên ở máy builder.

### Determinism khác idempotence

- **Deterministic weaving:** cùng input và điều kiện có kết quả tương đương theo tiêu chí xác định.
- **Idempotent weaving:** áp dụng lại không tạo thêm side effect/instrumentation ngoài hợp đồng.
- **Byte-for-byte reproducible:** bytes của artifact cuối giống nhau.
- **Behavior reproducible:** cùng điều kiện triển khai tạo behavior advice tương đương.

Một weaver deterministic chưa chắc idempotent khi feed output đã dệt làm input; một weaver idempotent về behavior chưa chắc tạo ZIP bytes giống nhau nếu metadata archive khác.

## 9. Ví dụ Java 17: cùng JAR nhưng hai chế độ AOP qua proxy

Ví dụ độc lập dùng JDK dynamic proxy để minh họa **ảnh hưởng của runtime config tới behavior**, không giả làm ví dụ bytecode weaving.

- `LedgerCore` là mã nghiệp vụ.
- `auditProxy(...)` chạy audit trước/sau, giữ nguyên kết quả và ngoại lệ gốc.
- `--audit` bật proxy cho caller.
- Cùng source biên dịch hai lần, đóng JAR với timestamps được cố định, rồi so SHA-256.
- Dùng cùng artifact chạy một lần ở chế độ thường, một lần ở chế độ audit.

Tạo file `PackagingAopDemo.java`:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class PackagingAopDemo {
    interface Ledger {
        String book(int units);
    }

    static final class LedgerCore implements Ledger {
        @Override
        public String book(int units) {
            if (units <= 0) {
                throw new IllegalArgumentException("units must be positive");
            }
            System.out.println("BUSINESS book");
            return "OK:" + units;
        }
    }

    static Ledger auditProxy(Ledger target) {
        return (Ledger) Proxy.newProxyInstance(
                Ledger.class.getClassLoader(),
                new Class<?>[]{Ledger.class},
                (proxy, method, args) -> {
                    System.out.println("AUDIT before " + method.getName());
                    try {
                        Object result = method.invoke(target, args);
                        System.out.println("AUDIT after " + method.getName());
                        return result;
                    } catch (InvocationTargetException wrapped) {
                        Throwable original = wrapped.getCause();
                        System.out.println("AUDIT throwing "
                                + original.getClass().getSimpleName());
                        throw original;
                    }
                });
    }

    public static void main(String[] args) {
        boolean audit = args.length > 0 && "--audit".equals(args[0]);
        Ledger service = new LedgerCore();
        if (audit) {
            service = auditProxy(service);
        }
        System.out.println("MODE " + (audit ? "AUDIT" : "PLAIN"));
        System.out.println("RESULT " + service.book(2));
        try {
            service.book(0);
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER " + error.getMessage());
        }
    }
}
~~~

### 9.1. Lệnh biên dịch và đóng JAR

Trong **Windows PowerShell**, từ thư mục chứa `PackagingAopDemo.java`, chạy các lệnh sau với JDK 17+:

~~~powershell
New-Item -ItemType Directory -Force out-a,out-b | Out-Null

javac --release 17 -encoding UTF-8 -g:none -d out-a PackagingAopDemo.java
if ($LASTEXITCODE -ne 0) { throw "Compile A failed" }

javac --release 17 -encoding UTF-8 -g:none -d out-b PackagingAopDemo.java
if ($LASTEXITCODE -ne 0) { throw "Compile B failed" }

jar --create --file app-a.jar --date=2022-01-01T00:00:00Z --main-class PackagingAopDemo -C out-a .
if ($LASTEXITCODE -ne 0) { throw "JAR A failed" }

jar --create --file app-b.jar --date=2022-01-01T00:00:00Z --main-class PackagingAopDemo -C out-b .
if ($LASTEXITCODE -ne 0) { throw "JAR B failed" }

$hashA = (Get-FileHash -Algorithm SHA256 app-a.jar).Hash
$hashB = (Get-FileHash -Algorithm SHA256 app-b.jar).Hash
if ($hashA -ne $hashB) { throw "JAR HASH MISMATCH" }
Write-Host "SHA256_EQUAL true"

java -jar app-a.jar
java -jar app-b.jar --audit
~~~

Đây là ví dụ với source được giữ cố định và hai thư mục output mới, cùng công cụ build. Tùy chọn `jar --date` đã được kiểm tra trực tiếp trong môi trường chạy thử; không nên suy ra chỉ dùng tùy chọn này thì mọi dự án hoặc phiên bản JDK đều có reproducible JAR. Với dự án thật, còn phải cố định các inputs đã nêu trong mục 8.

Đầu ra đã được kiểm chứng khi chạy thử:

~~~text
SHA256_EQUAL true
MODE PLAIN
BUSINESS book
RESULT OK:2
CALLER units must be positive
MODE AUDIT
AUDIT before book
BUSINESS book
AUDIT after book
RESULT OK:2
AUDIT before book
AUDIT throwing IllegalArgumentException
CALLER units must be positive
~~~

### 9.2. Phân tích kết quả

**`SHA256_EQUAL true`:** trong thí nghiệm cụ thể, hai JAR có cùng bytes. Điều này kiểm tra khả năng tái lập của recipe này với cùng môi trường, **không chứng minh mọi weaver hoặc build pipeline khác đều deterministic**.

**`MODE PLAIN`:** caller giữ target gốc, không có audit wrapper. `book(2)` trả `OK:2`; `book(0)` ném `IllegalArgumentException` theo hợp đồng.

**`MODE AUDIT`:** caller giữ proxy, nên advice chạy trước/sau ở nhánh thành công và ghi lỗi ở nhánh exceptional. Handler bóc `InvocationTargetException` để caller vẫn nhận exception gốc. Logic nghiệp vụ không bị đổi.

**Kết luận:** hai artifact được build cùng bytes, nhưng runtime arguments tạo **hai chế độ advice**. Đây là demo **proxy runtime**, không phải bytecode weaving; nó chứng minh rằng reproducible build và reproducible aspect behavior không đồng nhất.

## 10. Khi dùng weaving thật, pipeline cần bổ sung gì?

Nếu sử dụng CTW/binary weaving thật, một pipeline hợp lý về ý niệm:

~~~text
pinned source / dependencies / toolchain
             |
      compile to raw
             |
       weave with exact tool + policy
             |
    inspect/verify transformed class
             |
       package with normalized metadata
             |
       test on final woven artifact
             |
       hash / sign / publish
~~~

Đây là **pseudocode quy trình**, không phải cú pháp của một weaver cụ thể. Các nguyên tắc:

- **Không ký artifact rồi tiếp tục sửa bytecode** mà không cập nhật xác thực integrity.
- **Không test chỉ trên raw classes** rồi kết luận woven artifact tương đương.
- **Không đưa woven output trở lại bước weaving khác** nếu thiếu hợp đồng dệt lặp rõ.
- **Không ghi hash raw.jar** như thể đó là hash bản đã instrument.
- **Không bỏ qua runtime support dependencies** mà advice đã được dệt cần đến.

Với LTW, phần artifact có thể không chứa mã chèn nhưng deployment phải kèm agent/config và test classloading; với proxy, deployment phải kèm interceptor/wiring policy.

## 11. Uber-JAR, shading, ký JAR và multi-release JAR

### 11.1. Dependency và uber-JAR

Khi gom các dependencies thành một JAR lớn, class files và resources từ nhiều nguồn có thể bị trộn. Nếu một thư viện đã được woven trước đó, bước weaving tổng có thể xử lý lại phần ấy hoặc áp thêm policy chồng lấn.

Vì vậy, phải biết **thư viện đầu vào raw hay woven**, và một chủ sở hữu rõ cho từng bước instrument. Nếu có nhiều version cùng class, cần quy tắc resolution rõ ràng.

### 11.2. Shading/relocation

Một công cụ relocation có thể thay tên package/class mà pointcut dùng để match. Việc weaving trước hay sau relocation có thể chọn tập join point khác nhau.

Kiểm thử phải chạy trên **artifact cuối sau relocation**, không chỉ dựa vào pointcut từng đúng ở source tree.

### 11.3. Ký và checksum

Nếu archive được ký trước khi một công cụ viết lại class entries, chữ ký của entry đã thay có thể không còn hợp lệ. Mọi bước biến đổi cần hoàn tất trước khi ký bản phát hành cuối. Với LTW, JAR trên disk có thể không đổi nhưng **bytecode được định nghĩa trong runtime** đã qua transformation; nên cần chính sách tin cậy cho cả artifact lẫn agent.

### 11.4. Multi-release JAR

JAR có thể chứa các class variants ở `META-INF/versions/...`. Một phiên bản JVM có thể chọn implementation khác phiên bản khác. Nếu chỉ weaving class ở root mà bỏ qua versioned entries được dùng trên JDK khác, AOP coverage có thể khác giữa các môi trường. Phải test những JDK được hỗ trợ với artifact cuối.

## 12. Runtime classloader, module và agent lifecycle

Với LTW/instrumentation, hai máy có thể khác:

- Runtime JDK/JVM version.
- Classloader định nghĩa class.
- Module exports/opens hoặc chính sách truy cập.
- Thứ tự đăng ký transformer.
- Thứ tự class được nạp.
- Những agent khác cùng chạy.
- Trạng thái class đã được định nghĩa trước khi agent cài.
- Quy tắc lỗi khi bytecode transformation không thành công.

Nếu lỗi chỉ xuất hiện trên production, đừng vội kết luận source khác. So **hash app artifact** và **launch flags + agent/version/config** trước, rồi quan sát load order và classloader identity.

Đối với agent runtime, không được giả định `-javaagent` đã được bật thì **mọi class** đều được weaving. Phải xác minh đúng target class nằm trong scope, classloading/transformer chạy ở thời điểm phù hợp, và join point thật tạo advice event đúng kỳ vọng.

## 13. Reproducible release cần lưu thông tin gì?

Một release manifest hữu ích:

| Trường | Lý do |
| --- | --- |
| `applicationArtifactSha256` | Xác thực bytes của JAR phát hành |
| `sourceRevision` | Xác định nguồn build |
| `compilerJdkVersion` | Tái tạo bytecode input |
| `dependencyLockAndChecksums` | Loại trừ thay dependency ngầm |
| `weaverVersion` / `agentVersion` | Tái hiện instrumentation |
| `aspectPolicyVersion` | Khóa tập pointcut/advice |
| `rawAndWovenArtifactIdentity` | Truy vết transformation |
| `jvmOptions` | Tái hiện quyền/module/classloading setup |
| `runtimeWiringVersion` | Tái hiện proxy/interceptor graph |
| `integrationTestResult` | Chứng minh behavior trên release artifact |
| `packageAndSigningMetadata` | Đối chiếu integrity và nguồn phát hành |

Một **SBOM** hoặc provenance record hỗ trợ theo dõi công cụ/dependencies, nhưng **không tự chứng minh các pointcut hoạt động**. Cần test integration hoặc sentinel join point trong môi trường tương đương thật.

### Release identity không chỉ là JAR hash

Có thể mô hình hóa release như một bộ giá trị:

~~~text
DeploymentIdentity = (
  applicationArtifactHash,
  runtimeJdk,
  weaverOrAgentVersion,
  pointcutPolicyVersion,
  jvmFlags,
  classloaderLayout,
  proxyRegistrationConfig
)
~~~

Danh sách là mô hình kiểm soát AOP, chưa bao gồm mọi input nghiệp vụ ngoài như database, external APIs, secrets và network. Nhưng nó tốt hơn hẳn cách coi “rollback app.jar” là rollback đầy đủ.

## 14. Rollback, canary và drift giữa các môi trường

### CTW/binary weaving

Rollback thường cần đúng **woven artifact** của release cũ. Nếu bản cũ được biên dịch từ raw source nhưng dùng một weaver mới, nó có thể không phải bản cũ về mặt bytecode; đừng gọi đây là rollback nguyên trạng.

### LTW

Rollback app JAR nhưng không rollback agent/policy có thể để lại hành vi mới. Một class đã load dưới transformer cũ có thể không tự phục hồi chỉ vì file config thay đổi; lifecycle thường cần restart hoặc cơ chế redefinition được hỗ trợ.

### Runtime proxy

Rollback runtime config/handler và bảo đảm caller vẫn nhận proxy đúng. Cùng application JAR nhưng policy `auditEnabled` khác có thể có AOP behavior khác; không coi checksum ứng dụng giống là đã xác nhận rollout hoàn tất.

### Canary/staging

Canary chỉ có ý nghĩa kiểm tra AOP khi **agent, flags, config, đường gọi và version artifact** phù hợp mục tiêu. Tránh staging không bật weaving trong khi production có weaving rồi dùng staging làm bằng chứng mọi join point đều đúng.

### Các dấu hiệu drift

| Triệu chứng | Cần kiểm tra |
| --- | --- |
| Audit thiếu trên một node | Proxy bypass, agent không khởi động, pointcut không match |
| Audit xuất hiện hai lần | Double weaving, proxy lồng, pointcut chồng lấn |
| JAR khác hash dù source không đổi | Timestamp, metadata, file order, compiler/weaver |
| JAR giống hash nhưng trace khác | Agent, policy, classloader, runtime flag |
| Startup lỗi ở một JVM | Bytecode verifier, module/classfile compatibility |
| Rollback không khôi phục behavior | Runtime config/agent/proxy graph chưa rollback |
| Thêm shading làm advice biến mất | Pointcut theo tên không còn match |
| Advice chỉ chạy sau restart | Class load timing hoặc config chỉ áp dụng ở startup |

## 15. Chiến lược kiểm thử ba lớp

### Lớp A — kiểm thử tính tái lập của build

1. Build sạch cùng nguồn trong **hai workspace độc lập**.
2. Cố định compiler, dependencies, weaver và cờ cấu hình.
3. Chuẩn hóa metadata JAR/ZIP, resource order và generated files.
4. So SHA-256 của **artifact cuối**.
5. Nếu khác, so archive entries, manifest, class bytes và transform logs để tìm nguyên nhân.
6. Không sửa artifact đầu ra thủ công cho “khớp hash” mà bỏ qua nguyên nhân.

### Lớp B — kiểm thử hành vi AOP

1. Một join point được chọn chạy advice đúng số lần.
2. Join point không được chọn không tạo event.
3. Advice quan sát bảo toàn return value và exception gốc.
4. Kiểm thứ tự nhiều advice.
5. Kiểm overload, generic/bridge nếu liên quan, call vs execution.
6. Kiểm self-invocation và đường gọi trực tiếp bỏ qua proxy.
7. Nếu có retry/cache, đếm số lần target thật chạy.
8. Nếu có LTW, kiểm class loading và agent startup.

### Lớp C — kiểm thử deployment

1. Chạy artifact cuối dưới JVM/agent/config release thật.
2. Test canary, restart, rollback với toàn bộ deployment tuple.
3. Kiểm đường thất bại khi agent thiếu, class không dệt hoặc pointcut không match.
4. Đánh giá fail-open/fail-closed nếu instrumentation liên quan chính sách bắt buộc.
5. Đo performance và overhead dưới tải đại diện.
6. Xác minh không ghi thông tin nhạy cảm vào logs/metrics khi thêm advice.

**Một unit test gọi trực tiếp method advice không chứng minh weaver/proxy đã kích hoạt advice ở join point thật.** Tương tự, hash JAR trùng không chứng minh object graph runtime tạo proxy đúng.

## 16. Những ví dụ chẩn đoán

**Tình huống A — cùng source, khác JAR hash:** source không đổi nhưng hai máy có JDK/manifest timestamps khác. Cần pin toolchain và chuẩn hóa packaging. Nếu còn bytecode weaving, phải kiểm cả weaver version và transformation order. Hash khác không tự nói rằng advice sai.

**Tình huống B — cùng JAR, khác audit log:** một máy truyền `--audit`, máy khác không. Đây là runtime policy drift, không phải lỗi reproducible build. Demo Java 17 chứng minh điều này.

**Tình huống C — một JAR woven được dệt lại sau package:** cần kiểm soát raw/woven boundary và chỉ có một chủ sở hữu weaving. Không mặc định mọi weaver luôn từ chối dệt lặp.

**Tình huống D — LTW bỏ sót class:** class đã load trước khi transformer được đăng ký, hoặc classloader/module ngoài scope. Chỉnh startup/agent configuration và test trên deployment đúng, không chỉ copy lại JAR.

**Tình huống E — signer chạy trước weaver:** JAR cuối có class entries đã sửa sau ký. Cần ký sau mọi transformation và xác minh integrity bản phát hành.

**Tình huống F — cùng woven artifact nhưng một node chạy JDK khác:** cần kiểm module/classfile compatibility, multi-release JAR variants và runtime dependencies. Artifact identity không loại bỏ các điều kiện runtime.

## 17. Những sai lầm thường gặp

1. **“CTW nghĩa là advice chạy khi compile.”** Không: chỉ việc kết hợp diễn ra ở build, advice vẫn thực thi tại join point runtime.
2. **“LTW là proxy.”** Không: một bên biến đổi class lúc nạp, bên kia có thể chỉ định tuyến qua wrapper.
3. **“JAR hash giống thì AOP behavior cũng giống.”** Không nếu runtime agent, flags, classloader hoặc proxy policy khác.
4. **“Source giống thì chắc chắn bit-for-bit reproducible.”** Không nếu timestamp, metadata, generated files hoặc toolchain khác.
5. **“Đổi hash chứng minh nghiệp vụ đổi.”** Không nhất thiết; thay metadata đóng gói cũng ảnh hưởng checksum.
6. **“Raw classes và woven classes có thể lưu chung không cần phân biệt.”** Dễ gây dệt lại và build không tái lập.
7. **“Ký JAR rồi thay bytecode không sao.”** Class entry thay đổi có thể phá signature.
8. **“Có agent thì mọi class đều được instrument.”** Phải xác minh scope, classloader, load order và capability.
9. **“Tắt agent là class đã load tự quay về bytecode ban đầu.”** Không có bảo đảm chung.
10. **“Proxy thấy mọi self-invocation và private method.”** Không đúng trong nhiều mô hình proxy thông thường.
11. **“Shading không ảnh hưởng pointcut.”** Đổi tên class có thể đổi matching.
12. **“Một test advice pass là đủ.”** Phải test artifact và đường gọi thật.
13. **“Chỉ cần pin `javac`.”** Weaver, dependencies, config, packaging/signing cũng ảnh hưởng bytes.
14. **“Weaver chạy hai lần luôn tạo double advice.”** Tùy công cụ và cách xử lý input đã dệt.
15. **“Benchmark raw JAR đại diện cho woven JAR.”** Không khi advice gây chi phí ở runtime.
16. **“Rollback JAR là rollback cả AOP.”** Runtime agent/proxy policy có thể vẫn ở version khác.

## 18. Câu hỏi tự kiểm tra

**Câu 1.** Dệt tĩnh xong trước khi ứng dụng chạy. Advice có còn chạy ở runtime không?  
**Trả lời:** Có. Dệt là công việc kết hợp; advice chạy lúc join point thực thi.

**Câu 2.** Vì sao binary weaving nên tách hai folder raw/woven?  
**Trả lời:** Để truy vết input/output, tránh dệt lặp ngoài ý định và tạo build sạch, dễ tái lập.

**Câu 3.** Tại sao hai máy chạy cùng `app.jar` vẫn có audit khác nhau?  
**Trả lời:** Runtime flags, proxy registration, agent, config hoặc classloading có thể khác.

**Câu 4.** Hai JAR khác hash có chắc behavior khác nhau?  
**Trả lời:** Không; metadata hoặc timestamps khác cũng thay bytes. Cần test hành vi.

**Câu 5.** Đổi chữ ký JAR sau weaving có được không?  
**Trả lời:** Nên ký artifact cuối sau tất cả bước biến đổi; không nên sửa entry đã ký rồi tin integrity vẫn hợp lệ.

**Câu 6.** Vì sao `jar --date` chưa đủ để bảo đảm reproducible build toàn dự án?  
**Trả lời:** Còn JDK/compiler, dependencies, file order, generated metadata, weaver và nhiều yếu tố khác.

**Câu 7.** Nếu LTW agent chạy sau khi class được định nghĩa thì điều gì có thể xảy ra?  
**Trả lời:** Class có thể không được dệt bởi cơ chế load-time thông thường; cần đánh giá riêng retransformation hoặc startup order.

**Câu 8.** Khi rollback LTW deployment, chỉ rollback JAR có đủ không?  
**Trả lời:** Không. Phải đồng bộ agent, aspect config, JVM options và classload lifecycle liên quan.

**Câu 9.** Demo Java chạy `PLAIN` và `AUDIT` từ hai JAR cùng checksum chứng minh điều gì?  
**Trả lời:** Artifact bytes giống nhau nhưng runtime proxy policy tạo khác biệt về hành vi AOP.

**Câu 10.** Điều gì bổ sung cho bằng chứng hash khi xác minh AOP?  
**Trả lời:** Integration tests trên artifact cuối, xác minh pointcut khớp đúng, advice chạy đúng số lần và không đổi result/exception ngoài hợp đồng.

## 19. Tổng kết

**Các thời điểm weaving khác nhau tạo những yêu cầu đóng gói, triển khai và tái lập khác nhau.** CTW và binary weaving thường đưa behavior vào artifact đã biến đổi; LTW chuyển phần kết hợp sang lifecycle class loading; runtime proxy/interceptor áp dụng advice trên các đường gọi qua wrapper mà không cần sửa target bytecode.

Để **bản dựng tái lập**, phải pin source, JDK, dependencies, weaver, policy, transformation order và metadata đóng gói rồi hash/ký artifact cuối. Để **triển khai tái lập**, còn cần đồng bộ agent/runtime config, classloader, flags, proxy registration và đường gọi thật. Hai chứng cứ bổ sung cho nhau là **artifact provenance/checksum** và **integration test hành vi advice**. Khi quản lý đầy đủ cả hai, có thể phát hành, đối soát, canary và rollback AOP một cách dự đoán được, thay vì chỉ dựa vào việc source code không đổi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="weaving-lifecycle-007">Khả năng can thiệp thư viện ngoài và giới hạn sở hữu mã</a>

<details>
<summary>Xem chi tiết</summary>

# Khả năng can thiệp thư viện ngoài và giới hạn sở hữu mã trong các thời điểm weaving AOP

## 1. Đặt vấn đề: AOP có can thiệp được vào mã không do mình viết không?

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP)**, một **aspect** chứa logic xử lý mối quan tâm xuyên suốt (ghi log, đo thời gian, kiểm toán, giám sát lỗi, kiểm tra chính sách...). Một **pointcut** lựa chọn các **join point** thuộc mô hình can thiệp của công cụ; **advice** được kết hợp vào đường thực thi tương ứng thông qua **weaving** hoặc interception.

Một vấn đề thực tế xuất hiện ngay khi ứng dụng gọi thư viện từ bên thứ ba:

> Ta không có mã nguồn và không thể sửa class của thư viện. Vậy có thể thêm logging, tracing hoặc kiểm tra điều kiện vào các lời gọi của thư viện bằng AOP không?

**Câu trả lời là: có thể trong nhiều trường hợp, nhưng không phải luôn luôn.** Khả năng phụ thuộc đồng thời vào:

1. **Loại join point cần quan sát**: vị trí gọi thư viện ở mã của ta, hay phần thân phương thức nằm trong thư viện?
2. **Thời điểm weaving**: lúc biên dịch, lúc nạp lớp, hay trong đường gọi runtime.
3. **Phạm vi mã có thể xử lý**: chỉ mã ứng dụng hay bao gồm thư viện đã biên dịch.
4. **Quyền truy cập và tính tương thích kỹ thuật**: class loader, module, quyền biến đổi, bytecode, native code, cấu hình agent.
5. **Quyền sở hữu và quyền sử dụng**: ta được phép sửa/phân phối lại artifact hay chỉ được phép sử dụng thông qua API?
6. **Tác động đến hợp đồng thư viện**: return value, exception, thread safety, hiệu năng, nâng cấp phiên bản và tính an toàn.

Mục tiêu bài là xây cách đánh giá **có căn cứ** khi một aspect cần chạm tới hành vi thư viện ngoài, không mặc định rằng “có pointcut là can thiệp được mọi class”.

Bài trình bày **AOP tổng quát từ cơ bản đến nâng cao**; những ví dụ Java 17 mô tả một số giới hạn và công cụ ở tầng JVM, không xem một cách triển khai cụ thể là định nghĩa chung của AOP.

## 2. “Sở hữu mã” có nhiều nghĩa khác nhau

Khi nói “không sở hữu mã nguồn thư viện”, cần phân biệt các tầng:

| Tầng | Ta có thể có | Điều chưa chắc có |
| --- | --- | --- |
| **Mã nguồn** | Chỉ JAR/.class đã biên dịch | Quyền sửa source hoặc xem triển khai nội bộ |
| **Artifact** | Quyền tải, chạy, lưu cache | Quyền sửa tệp gốc và phân phối bản sửa |
| **Môi trường chạy** | Quyền cấu hình ứng dụng/JVM của mình | Quyền instrument tiến trình do bên khác quản lý |
| **Đường gọi** | Có thể đổi chỗ ứng dụng tạo đối tượng và gọi API | Không kiểm soát lời gọi nội bộ trong thư viện |
| **Class loader/module** | Có thể cấu hình một số lớp nạp | Không mở được mọi ranh giới module hoặc loader |
| **Hợp đồng API** | Dùng public API/documented extension | Không được phụ thuộc private internals không ổn định |
| **Giấy phép & chính sách** | Có quyền sử dụng theo license | Không mặc nhiên có quyền biến đổi, đóng gói lại hoặc phân phối |

**Không có source không đồng nghĩa không can thiệp được**, vì nhiều kỹ thuật hoạt động trên bytecode hoặc lời gọi. Nhưng **có thể sửa bytecode về mặt kỹ thuật không đồng nghĩa được quyền làm vậy về mặt pháp lý hoặc chính sách**.

Nếu thư viện chứa mã do tổ chức khác quản lý, cần kiểm tra hợp đồng cấp phép, điều khoản phân phối, kiểm soát an ninh và trách nhiệm hỗ trợ. Bài không thay thế tư vấn pháp lý; mục tiêu là xác định những rủi ro phải đánh giá.

## 3. Ba câu hỏi trước khi chọn kỹ thuật

Trước khi quyết định phải “dệt” thư viện ngoài, hãy tách ba mục tiêu:

**A. Muốn quan sát lời gọi từ mã mình tới thư viện?**

```text
Application -> library.execute()
       ^
       |
Điểm gọi thuộc mã ứng dụng
```

Ta có thể quan sát ở **ranh giới của ứng dụng**, ví dụ qua wrapper/proxy hoặc weaving vào call site của ứng dụng, mà **không cần** thay phần thân `library.execute()`.

**B. Muốn can thiệp mọi lần thực thi của phương thức bên trong thư viện?**

```text
Application ---> Library.publicMethod()
                     |
                     +-- Library.privateHelper()
                     +-- Library.internalCall()
```

Nếu muốn advice chạy cả khi thư viện tự gọi method của nó, nhiều cơ chế chỉ chặn qua proxy ở ứng dụng sẽ **không đủ**. Có thể cần weaving/instrumentation ở lớp thư viện, nếu kỹ thuật và quyền sử dụng cho phép.

**C. Muốn quan sát một tác vụ ngoài JVM hoặc ngoài tiến trình?**

```text
Application JVM -> HTTP/IPC -> Remote Vendor Service
```

Weaving class Java trong ứng dụng **không tự can thiệp** vào mã chạy ở máy chủ bên thứ ba. Ta chỉ có thể quan sát request/response tại ranh giới client của mình, trừ khi có quyền triển khai công cụ trên hệ thống đích.

Ba mục tiêu khác nhau về phạm vi kiểm soát, chứng cứ và kỹ thuật.

## 4. Join point nằm ở phía gọi hay phía được gọi?

Đây là nền tảng của bài.

**Method-call join point** mô tả một vị trí gọi phương thức. Nếu vị trí gọi nằm trong mã ứng dụng do ta build, bộ dệt hỗ trợ call join point có thể can thiệp ngay tại đó ngay cả khi **đích là thư viện ngoài**.

**Method-execution join point** mô tả ranh giới phần thân phương thức được chạy. Nếu phần thân thuộc class thư viện, muốn chèn advice vào execution đó có thể cần có bytecode lớp thư viện trong phạm vi weaving.

```text
Ứng dụng của ta                     JAR bên thứ ba
+------------------------+          +------------------------+
| Checkout.placeOrder()  |          | Gateway.send()         |
|                        |          |                        |
|  gateway.send(data) ---+--------->| thực thi send(data)    |
|          ^             |          |       ^                |
|       call site        |          |    execution           |
+------------------------+          +------------------------+
```

Trong công cụ hỗ trợ cả hai loại, pointcut chọn **call** có thể hoạt động ở bên trái còn chọn **execution** cần đánh giá phạm vi biến đổi bên phải.

Đừng suy diễn rằng mọi ngôn ngữ/công cụ AOP có cả call và execution join point. Có công cụ chỉ hỗ trợ những lời gọi đi qua interface/proxy hoặc một tập phương thức cụ thể.

### 4.1. Lời gọi nội bộ là ca dễ bỏ sót

Nếu thư viện gọi `this.internalMethod()` trong cùng đối tượng, một proxy đặt **bên ngoài** thư viện có thể chỉ thấy lời gọi entry từ ứng dụng, không thấy từng lời gọi bên trong.

Ngược lại, weaving trực tiếp tại execution của method nội bộ có thể quan sát được nếu loại điểm đó được hỗ trợ và class tương ứng đã được xử lý.

Vì vậy phải xác định **điều muốn bao phủ là entry boundary hay toàn bộ internal execution** trước khi viết pointcut.

## 5. So sánh theo các thời điểm weaving

| Thời điểm/cách can thiệp | Có cần source thư viện? | Có cần sửa bytecode thư viện? | Khả năng và giới hạn tiêu biểu |
| --- | --- | --- | --- |
| **Compile-time weaving từ source** | Thường cần source hoặc biểu diễn đầu vào thích hợp | Có thể sinh artifact đã dệt | Hạn chế nếu không có quyền build lại thư viện |
| **Binary/post-compile weaving** | Không nhất thiết | Có, nếu dệt trực tiếp artifact thư viện | Phải kiểm tra license, chữ ký số, tương thích bytecode, phân phối |
| **Load-time weaving** | Không nhất thiết | Biến đổi bytes khi nạp, có thể không sửa JAR trên đĩa | Phụ thuộc agent/class loader/module, thời điểm và phạm vi lớp |
| **Runtime proxy/interception** | Không | Thường không | Chỉ chặn lời gọi đi qua proxy/interceptor; không tự thấy mọi lời gọi nội bộ |
| **Runtime class retransformation** | Không nhất thiết | Thay định nghĩa trong VM khi được hỗ trợ | Có giới hạn khả năng, loại lớp và thay đổi cấu trúc |
| **API hook/callback do thư viện cung cấp** | Không | Không | Ổn định hơn nếu API thiết kế cho mở rộng, nhưng chỉ trong phạm vi hook |
| **Quan sát tại network/IPC boundary** | Không | Không | Thấy tương tác client, không thấy internals dịch vụ từ xa |

Đây là bảng **năng lực khái quát**, không phải cam kết một công cụ cụ thể thực hiện được mọi ô. Một giải pháp compile-time có thể hỗ trợ đầu vào bytecode; một công cụ runtime có thể tạo subclass proxy; khả năng thật phải đối chiếu tài liệu và thử nghiệm.

### 5.1. Từ “không được sửa source” đến “không được sửa artifact”

Có source hay không khác với có quyền phân phối bản JAR đã thay đổi. Một tổ chức có thể cho phép chạy agent/instrumentation trong môi trường kiểm thử của mình, nhưng cấm xuất bản lại thư viện dưới dạng đã sửa. Không được đồng nhất “không cần source” với “không có ràng buộc bản quyền”.

## 6. Compile-time weaving khi thư viện không do ta build

Nếu chỉ build **mã ứng dụng**, trình biên dịch có thể nhìn các call site của ứng dụng nhưng không nhất thiết có quyền biến đổi phần thân bên trong JAR của nhà cung cấp.

Có ba tình huống:

1. **Chỉ dệt mã ứng dụng:** có thể chèn logic ở những join point thuộc mã của ta, như lời gọi tới API vendor nếu mô hình hỗ trợ.
2. **Đưa bytecode thư viện vào bước binary weaving:** có thể thay class thư viện mà không có source nếu công cụ và quyền sử dụng cho phép.
3. **Thư viện đã được nhà cung cấp dệt trước:** ta nhận artifact đã dệt sẵn, lúc đó cần xem rõ những advice nào đã có và có xung đột với instrumentation khác không.

Các hệ quả cần kiểm tra:

- Bản thư viện sau biến đổi có thể không khớp checksum/chữ ký số của bản phát hành gốc.
- Cần bảo đảm classpath/modulepath chứa **bản đã dệt** chứ không lẫn bản gốc.
- Phải giữ tài liệu phiên bản và quy trình tái lập build.
- Khi nhà cung cấp cập nhật JAR, có thể phải dệt lại và chạy lại kiểm thử.
- Một transform làm thay đổi ABI hoặc hành vi có thể làm mất khả năng hỗ trợ từ nhà cung cấp.

### 6.1. Rủi ro signed JAR

Một JAR có chữ ký số có thể chứa metadata xác minh nội dung. Sửa các entry đã ký mà không xử lý quy trình ký phù hợp có thể làm xác minh chữ ký thất bại hoặc vi phạm chính sách phân phối. Không nên tự coi “đã zip/JAR lại được” là artifact còn hợp lệ.

Nếu cần biến đổi trực tiếp, hãy quản lý provenance: bản gốc, hash, bước transform, bản sau transform, bản ký/phê duyệt phù hợp và quyền được làm việc đó.

### 6.2. Không chỉ là vấn đề cú pháp pointcut

Một pointcut đúng vẫn vô tác dụng nếu **lớp thư viện không nằm trong tập đầu vào weaving**. Ngược lại, dệt quá rộng có thể chạm vào nhiều class của thư viện mà người phát triển không chủ ý, làm tăng rủi ro về hiệu năng và tương thích.

## 7. Load-time weaving: can thiệp tại lúc nạp lớp

Trên JVM, cơ chế instrumentation có thể đăng ký transformer và nhận bytecode của class trước khi class được định nghĩa. Đây là một con đường để áp dụng hành vi vào bytecode thư viện **mà không sửa JAR trên đĩa**.

Pipeline khái niệm:

```text
Vendor library JAR (bytes gốc)
             |
             v
        Class loader
             |
             v
    Class-file transformer
             |
     +-------+--------+
     |                |
 Không chọn       Chọn class
     |                |
     v                v
 Giữ bytes       Sinh bytes đã biến đổi
     |                |
     +-------+--------+
             |
             v
     JVM define class
             |
             v
   Đường thực thi có hiệu lực
```

Đây là mô hình khái niệm. Một số công cụ thay đổi cấu trúc bytecode ngay tại điểm nạp; công cụ khác dùng phương án dispatch/tracking riêng.

### 7.1. Khả năng của Java instrumentation

Java 17 cung cấp API `java.lang.instrument.Instrumentation` và `ClassFileTransformer`. Agent có thể đăng ký transformer để xử lý class definition; một số môi trường hỗ trợ tái biến đổi hoặc định nghĩa lại class.

**Không phải mọi class đang chạy đều có thể sửa**, và không phải mọi thay đổi class đều hợp lệ. API cung cấp các phép kiểm tra khả năng như `isModifiableClass`, `isRetransformClassesSupported`; khi thao tác không được hỗ trợ có thể phát sinh lỗi phù hợp.

Trong những giới hạn tái biến đổi, thay đổi cấu trúc class như thêm/xóa trường hay phương thức có thể bị cấm dù thay đổi thân method được cho phép theo các ràng buộc cụ thể. Không suy rằng “agent attach thành công” nghĩa là có thể thực hiện mọi transform.

### 7.2. Thời điểm nạp có thể quyết định kết quả

Một class đã được nạp trước khi agent/transformer được kích hoạt có thể không nhận advice giống một class nạp sau. Nếu muốn tác động lên lớp đã nạp, cần xác minh **có hỗ trợ retransformation/redefinition** và cấu hình yêu cầu khả năng đó.

Không nên khẳng định hệ thống luôn có thể retroactively sửa mọi class. Điều kiện thực tế phụ thuộc JVM, agent manifest, loại class, trạng thái và loại thay đổi bytecode.

### 7.3. Tệp JAR gốc không nhất thiết là bytecode runtime

Khi dùng load-time weaving, `javap` trên thư viện gốc có thể không cho thấy lệnh advice vì transform chỉ được áp dụng khi class được nạp. Muốn kiểm tra mã thực, cần log transformer, bytecode dump hoặc công cụ chẩn đoán được môi trường hỗ trợ.

## 8. Runtime proxy: can thiệp khi kiểm soát đường gọi

Proxy phù hợp khi ứng dụng **có quyền thay đổi cách lấy và gọi đối tượng thư viện**, đặc biệt nếu thư viện công bố giao diện rõ ràng.

```text
App -> VendorInterface proxy -> interceptor -> VendorImplementation
```

**Ưu điểm:**

- Không đụng source hay class file của thư viện.
- Có thể bao quanh lời gọi của ứng dụng để đo thời gian, log, audit hoặc chuyển lỗi.
- Dễ tách logic quan sát khỏi đối tượng vendor và kiểm thử với fake.
- Thường ít rủi ro hơn biến đổi bytecode nội bộ.

**Giới hạn:**

- Caller phải thực sự sử dụng **proxy**.
- Lời gọi trực tiếp tới target không đi qua proxy sẽ bị bỏ qua.
- Lời gọi nội bộ từ target tới method khác trên `this` thường không đi qua proxy.
- Một proxy chỉ hiện thực giao diện được chọn không phải subclass của class vendor.
- Không tự chặn mọi constructor, static method, final method hay field access.
- Một số API không thiết kế bằng giao diện hoặc không cho thay thế instance theo cách ứng dụng cần.

Trong Java, **JDK dynamic proxy tạo instance của lớp thực thi các interface đã khai báo**; không thể dùng nó như một giải pháp chung để bao mọi class không có giao diện. Các công cụ khác có thể tạo subclass proxy, nhưng lại có giới hạn riêng khi class/method không thể override, constructor phức tạp hoặc cấu trúc class không cho subclass.

## 9. Ví dụ Java 17 đầy đủ: can thiệp thư viện ngoài mà không sửa mã vendor

Ta mô phỏng một thư viện của nhà cung cấp bằng **hai tệp riêng**, sau đó một ứng dụng dùng JDK dynamic proxy để chặn những lời gọi đi qua giao diện. Đây là ví dụ **chạy được bằng Java 17**, không phải một engine AOP tổng quát.

Mục tiêu quan sát:

- Lời gọi qua proxy có các bước trước/sau.
- Lời gọi trực tiếp tới vendor không có advice.
- Lời gọi nội bộ trên `this` của vendor không được proxy nhìn thấy.
- Ngoại lệ do vendor ném có thể được handler gỡ lớp bọc reflection và truyền về caller.

### 9.1. Mã “thư viện ngoài” — tệp `VendorGateway.java`

```java
public interface VendorGateway {
    String send(String id, int amount);
}
```

### 9.2. Triển khai “thư viện ngoài” — tệp `VendorClient.java`

```java
public final class VendorClient implements VendorGateway {
    @Override
    public String send(String id, int amount) {
        System.out.println("vendor:send:" + id + ":" + amount);
        if (amount < 0) {
            throw new IllegalArgumentException("negative amount");
        }
        return "ACCEPTED:" + id;
    }

    public String internal(String id, int amount) {
        return send(id, amount);
    }
}
```

`VendorClient` ở đây được đánh dấu `final` để minh họa rằng ứng dụng **không cần subclass** khi dùng proxy giao diện. Lời gọi `internal` là lời gọi trực tiếp trên đối tượng vendor và `send` bên trong cũng chạy trên cùng đối tượng đó.

### 9.3. Mã ứng dụng — tệp `ThirdPartyInterceptionDemo.java`

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class ThirdPartyInterceptionDemo {
    public static void main(String[] args) {
        VendorClient vendor = new VendorClient();

        VendorGateway intercepted = (VendorGateway) Proxy.newProxyInstance(
                VendorGateway.class.getClassLoader(),
                new Class<?>[] { VendorGateway.class },
                (proxy, method, arguments) -> {
                    String id = (String) arguments[0];
                    System.out.println("advice:before:" + id);
                    try {
                        Object result = method.invoke(vendor, arguments);
                        System.out.println("advice:after:" + id);
                        return result;
                    } catch (InvocationTargetException wrapper) {
                        Throwable original = wrapper.getCause();
                        System.out.println("advice:error:" + id + ":"
                                + original.getClass().getSimpleName());
                        throw original;
                    }
                }
        );

        System.out.println("caller:proxy:" + intercepted.send("A", 20));
        System.out.println("caller:direct:" + vendor.send("B", 30));
        System.out.println("caller:internal:" + vendor.internal("C", 40));

        try {
            intercepted.send("D", -1);
        } catch (IllegalArgumentException original) {
            System.out.println("caller:caught:" + original.getMessage());
        }
    }
}
```

### 9.4. Cách biên dịch và chạy

Đặt cả ba tệp trong một thư mục, dùng JDK 17 hoặc cao hơn:

```bash
javac VendorGateway.java VendorClient.java ThirdPartyInterceptionDemo.java
java ThirdPartyInterceptionDemo
```

Đầu ra xác định:

```text
advice:before:A
vendor:send:A:20
advice:after:A
caller:proxy:ACCEPTED:A
vendor:send:B:30
caller:direct:ACCEPTED:B
vendor:send:C:40
caller:internal:ACCEPTED:C
advice:before:D
vendor:send:D:-1
advice:error:D:IllegalArgumentException
caller:caught:negative amount
```

### 9.5. Giải thích các đường gọi

1. `intercepted.send("A",20)`: caller đi qua proxy, nên có `advice:before` và `advice:after`.
2. `vendor.send("B",30)`: caller gọi **trực tiếp** target, không qua proxy nên không có advice.
3. `vendor.internal("C",40)`: ứng dụng gọi helper trực tiếp; từ bên trong target có `this.send(...)`, vẫn không đi qua proxy, nên không có advice.
4. `intercepted.send("D",-1)`: có before advice; vendor ném `IllegalArgumentException`; `Method.invoke` bọc trong `InvocationTargetException`; handler lấy cause và ném lại lỗi gốc để caller bắt.

**Điểm giới hạn cần hiểu:** ở ca (3), ứng dụng có thể chủ động tạo wrapper cho `internal` nếu cần quan sát lời gọi **entry** của ứng dụng. Nhưng điều đó không tự tạo interception cho mọi lời gọi nội bộ trong thư viện.

### 9.6. Vì sao đây không phải weaving bytecode?

- Không thay đổi `VendorClient.class`.
- Không biên dịch pointcut DSL.
- Không chặn mọi cuộc gọi tới `VendorClient.send` trên VM.
- Không xem được mọi constructor/field/private method của vendor.
- Chỉ những caller dùng `intercepted` mới được chuyển qua handler.

Ví dụ thể hiện **quyền kiểm soát đường gọi**, chứ không thể chứng minh một công cụ AOP có thể can thiệp vào bytecode thư viện ngoài.

## 10. Mô phỏng thư viện được đóng gói thành JAR

Trong dự án thực tế, mã vendor thường được phân phối dưới dạng JAR. Ví dụ trên có thể được chia ranh giới build:

```text
VendorGateway.java + VendorClient.java -> vendor.jar
ThirdPartyInterceptionDemo.java        -> app dùng vendor.jar
```

Các lệnh mang tính minh họa trên JDK 17, chạy từ thư mục có đủ hai tệp vendor:

```bash
javac VendorGateway.java VendorClient.java
jar --create --file vendor.jar VendorGateway.class VendorClient.class
javac -cp vendor.jar ThirdPartyInterceptionDemo.java
```

Chạy trên hệ điều hành dùng dấu hai chấm phân cách classpath:

```bash
java -cp ".:vendor.jar" ThirdPartyInterceptionDemo
```

Trên Windows, dấu phân cách classpath thường là dấu chấm phẩy:

```text
java -cp ".;vendor.jar" ThirdPartyInterceptionDemo
```

Ở ví dụ mô phỏng, có thể còn class file vendor trong thư mục làm việc sau bước build; điều này không thay đổi việc proxy **không sửa** vendor. Để bảo đảm phép thử đóng gói thuần túy, hãy thực hiện với một thư mục ứng dụng sạch chỉ chứa JAR và mã app. **Không dùng ví dụ này làm hướng dẫn repack thư viện thương mại khi chưa kiểm tra quyền sử dụng.**

## 11. Khi thư viện không cung cấp interface

Giả sử chỉ có:

```java
public final class VendorUtility {
    public static String normalize(String value) {
        return value.trim();
    }
}
```

Đây là mảnh code minh họa, không có `main`.

Với JDK dynamic proxy, ta **không thể trực tiếp biến `VendorUtility` thành một proxy cùng kiểu class** theo cách như ví dụ giao diện vì proxy JDK hiện thực interface.

Những lựa chọn thay thế ở mức thiết kế:

1. **Application-owned adapter:** định nghĩa interface của mình và triển khai bằng một adapter gọi `VendorUtility.normalize`. Advice có thể quan sát lời gọi qua adapter.
2. **Wrapper/facade:** chuyển mọi nơi trong ứng dụng gọi thư viện về một đầu mối.
3. **Hook/callback chính thức:** nếu vendor có API extension được hỗ trợ.
4. **Bytecode weaving/instrumentation:** chỉ khi khả năng kỹ thuật và quyền sử dụng phù hợp.
5. **Boundary observation:** quan sát đầu vào/đầu ra ở nơi ứng dụng tiếp xúc với thư viện.

Adapter không can thiệp vào lời gọi mà code khác vẫn gọi trực tiếp `VendorUtility`. Để áp dụng chính sách nhất quán, kiến trúc ứng dụng phải thực sự chuyển những đường gọi quan trọng qua adapter.

### 11.1. Final class và final method

Vì class `final` không thể được subclass thông thường, các proxy dựa trên subclass sẽ gặp giới hạn. Tương tự, phương thức `final` không thể override trong subclass.

Tuy nhiên, **đây không phải hạn chế chung của tất cả bytecode instrumentation**: một bộ biến đổi class có quyền và năng lực phù hợp có thể dùng đường khác không phụ thuộc override. Đừng kết luận “final method thì mọi AOP đều bất lực”.

### 11.2. Constructor và static method

JDK dynamic proxy không biến constructor hoặc static method trên class vendor thành lời gọi giao diện thông thường qua proxy. Nếu cần quan sát các điểm này, phải xét mô hình join point và khả năng weaving khác, hoặc đặt một factory/adapter thuộc quyền ứng dụng.

## 12. Lớp và thành viên không công khai: ranh giới truy cập

Các thành viên `private`, package-private, protected và internal implementation làm việc can thiệp khó hơn so với API public.

Có những vấn đề khác nhau cần tránh trộn lẫn:

- **Java access control:** mã ứng dụng có quyền gọi trực tiếp thành viên hay không?
- **Reflection access:** có được phép truy cập sâu vào thành viên không?
- **JPMS/module encapsulation:** package có được export/open cho module liên quan không?
- **Instrumentation permission/capability:** class có modifiable và transform hợp lệ không?
- **Runtime calling model:** lời gọi thực tế có qua một đường interception được quản lý không?

Một engine biến đổi bytecode không nhất thiết dùng reflection để gọi private method; ngược lại, việc reflection báo `InaccessibleObjectException` không tự chứng minh mọi kỹ thuật can thiệp khác đều bất khả thi.

**Cách đúng là kiểm tra từng ranh giới theo công cụ đang dùng**, không suy kết luận từ một từ khóa `private` duy nhất.

### 12.1. Java module system (JPMS)

Một thư viện có thể nằm trong named module với quy tắc `exports` và `opens` rõ ràng. Package được export cho phép truy cập public API theo quan hệ module tương ứng; mở package phục vụ deep reflection theo phạm vi xác định. Hai khái niệm không giống nhau.

Trên môi trường có module encapsulation, một giải pháp AOP dùng reflection sâu có thể cần cấu hình mở package hợp lệ hoặc cơ chế instrumentation tương ứng. Việc thay đổi chính sách module có tác động bảo mật và bảo trì, không nên tự ý mở toàn bộ module.

Một Java agent có thể sử dụng một số API instrumentation liên quan module, nhưng không phải có agent là “vượt qua mọi rào cản” tự động. Phải kiểm tra quyền, module readability, access và hạn chế của VM.

## 13. Class loader và phạm vi khả kiến của thư viện ngoài

Một lớp Java không chỉ được định danh bởi tên đầy đủ; **class loader định nghĩa** cũng quan trọng đối với identity của class trong JVM.

Hai bản class cùng tên do hai loader khác nhau định nghĩa có thể không tương đương ở runtime. Điều này tác động tới:

- Loader nhìn thấy interface nào để tạo proxy.
- Loader có tải được advice/helper class không.
- Một agent có thấy và biến đổi mọi loader thích hợp không.
- Một plugin được nạp động có mang một bản vendor library riêng không.
- Nguy cơ `ClassCastException`, `LinkageError` hoặc xung đột phiên bản.

### 13.1. Thư viện ở plugin hoặc container

Một ứng dụng plugin có thể dùng loader riêng cho mỗi plugin. Nếu advice helper chỉ có ở class loader ứng dụng chính, bytecode đã biến đổi trong plugin có thể tham chiếu tới loại helper mà plugin không nhìn thấy, gây lỗi liên kết.

Đây là lý do **weaving được một class không đồng nghĩa class đó sẽ chạy thành công**: các tham chiếu do bộ dệt chèn thêm cũng phải thỏa ràng buộc truy cập và nạp lớp.

### 13.2. Lớp nạp bởi bootstrap loader

Một số lớp nền tảng Java được nạp bởi bootstrap loader hoặc cơ chế đặc biệt. Instrumentation có thể có hỗ trợ và đường khai báo phụ trợ riêng, nhưng không thể mặc định tất cả lớp nền tảng đều được phép biến đổi hoặc mọi helper class có thể được tìm thấy từ bootstrap context.

Với các lớp lõi, rủi ro tác động lan rộng tới toàn JVM đặc biệt lớn. Chỉ instrument khi có mục tiêu rõ, công cụ được hỗ trợ và kiểm thử tương thích nghiêm ngặt.

## 14. Thư viện native/JNI và mã ngoài JVM

Nếu thư viện Java gọi native method hoặc chuyển sang mã C/C++ thông qua JNI/FFI, AOP Java có thể can thiệp tại **ranh giới Java** nhưng không tự chèn advice vào từng instruction native.

```text
Java application
    |
    v
Java wrapper (có thể can thiệp)
    |
    v
JNI/native bridge
    |
    v
Native library (không tự động nằm trong join-point model Java)
```

Để quan sát chi tiết bên trong mã native cần công cụ phù hợp với runtime/native layer và quyền triển khai tương ứng. Tương tự, một lời gọi HTTP tới dịch vụ vendor bên ngoài chỉ cung cấp điểm đo ở phía client khi ta không kiểm soát tiến trình server.

Không nên mô tả một Java pointcut là “chặn được mọi hàm của thư viện ngoài” khi một phần quan trọng nằm ngoài mô hình join point hỗ trợ.

## 15. Quản trị rủi ro khi biến đổi artifact do bên thứ ba sở hữu

Đụng trực tiếp bytecode vendor có rủi ro cao hơn việc dùng public API qua wrapper.

### 15.1. Giấy phép, chữ ký số và provenance

Các câu hỏi phải có đáp án:

1. Quyền sử dụng có cho phép **sửa bản cài** trong môi trường của mình không?
2. Có được **phân phối lại** một JAR đã biến đổi không?
3. Nếu artifact có chữ ký số, bước biến đổi ảnh hưởng xác minh như thế nào?
4. Ai chịu trách nhiệm cung cấp bản vá bảo mật/phiên bản mới?
5. Có thể truy được hash của bản gốc và bản đã dệt không?
6. Có quy trình kiểm duyệt các thay đổi ở bytecode hoặc cấu hình agent không?

Không cần biến mọi lựa chọn thành tranh luận pháp lý phức tạp: **nếu wrapper theo public API đã đáp ứng đầy đủ yêu cầu, không nên biến đổi artifact bên thứ ba chỉ vì có thể làm được**.

### 15.2. Hỗ trợ của nhà cung cấp và nâng cấp phiên bản

Một pointcut vào private method `processInternal` có thể hoạt động trên một bản thư viện rồi bị hỏng khi nhà cung cấp refactor. Phần API public có versioning thường ổn định hơn các chi tiết nội bộ.

Với mỗi lần nâng cấp vendor:

- So sánh chữ ký và cấu trúc bytecode nếu đang instrument nội bộ.
- Kiểm tra những shadow đã được weaving còn tồn tại không.
- Kiểm thử tính đúng đắn, hiệu năng và ngoại lệ.
- Xác minh các quyền truy cập module/class loader.
- Lưu báo cáo thay đổi tập join point.
- Cập nhật cấu hình hoặc bỏ instrumentation không cần thiết.

## 16. Chi phí của các phương án: không chỉ runtime overhead

| Phương án | Chi phí chạy | Chi phí build/deploy | Chi phí bảo trì |
| --- | --- | --- | --- |
| Wrapper/adapter | Một tầng gọi/logic phụ trợ | Thường thấp | Phải chuyển mọi caller cần thiết |
| Interface proxy | Dispatcher và advice theo invocation | Cấu hình tạo/thay thế instance | Quản lý việc bypass và hợp đồng interface |
| Compile/binary weaving | Có thể tối ưu tốt hoặc có guard runtime | Build/artifact phức tạp hơn | Reweave khi nâng cấp vendor |
| Load-time weaving | Chi phí transform lúc load + runtime advice | Agent/loader/module/config | Chẩn đoán khi lớp không được dệt |
| Retransformation | Chi phí/giới hạn VM và chuyển đổi | Quản trị agent attach/lifecycle | Khó bảo đảm mọi class/mọi trạng thái |
| Network boundary tracing | Phụ thuộc handler/client | Cấu hình client/transport | Không quan sát được internals |

Chi phí thực tế phải **đo** chứ không suy từ nhãn kỹ thuật. Một proxy cho điểm nóng có thể tạo overhead đáng kể; một transformer chèn quá nhiều advice cũng có thể gây chi phí lớn. Ngược lại, wrapper đơn giản có thể giúp tách điểm can thiệp rõ và dễ kiểm thử.

### 16.1. Static match và dynamic guard

Nếu pointcut chọn thư viện theo tên method nhưng còn điều kiện theo tham số/đối tượng runtime, một phần logic có thể được xác định trước, phần còn lại phải kiểm tra trên mỗi invocation phù hợp.

Đừng dùng `cached=true` theo chữ ký cho mọi lần chạy khi kết quả phụ thuộc giá trị tham số. Ví dụ số tiền 20 và 20.000 trên cùng method phải được phân biệt nếu chính sách yêu cầu ngưỡng.

## 17. Lỗi thường thấy khi áp AOP vào thư viện ngoài

**Lỗi 1 — “Không có source thì AOP không làm được gì.”** Sai. Call-site weaving, binary/load-time instrumentation, proxy hoặc wrapper có thể phù hợp mà không sửa source vendor.

**Lỗi 2 — “Có pointcut thì chạm được mọi method trong JAR.”** Sai. Phải xét mô hình join point, phạm vi weaving, loader, module và quyền truy cập.

**Lỗi 3 — “Proxy bao được mọi lời gọi nội bộ.”** Không. Proxy giao diện chỉ thấy các lời gọi đi qua đối tượng proxy; target tự gọi trên `this` có thể không đi qua nó.

**Lỗi 4 — “Final class nghĩa mọi cơ chế AOP đều không can thiệp.”** Không. Final cản subclass/override nhưng không tự loại bỏ mọi kỹ thuật bytecode instrumentation.

**Lỗi 5 — “Agent đã chạy thì mọi class đã nạp đều có advice.”** Không. Phải xét lúc agent/transformer kích hoạt và khả năng retransformation.

**Lỗi 6 — “Sửa signed JAR rồi đóng gói lại là ổn.”** Chưa chắc. Chữ ký và chính sách phân phối có thể không còn hợp lệ.

**Lỗi 7 — “Dùng reflection là truy cập được mọi private method.”** Không. Access rules, JPMS và cấu hình runtime có thể hạn chế.

**Lỗi 8 — “Chặn wrapper Java nghĩa thấy được mọi hoạt động native.”** Không. Native code nằm ngoài mô hình Java join point thông thường.

**Lỗi 9 — “Biến đổi vendor method không ảnh hưởng hỗ trợ/bảo hành.”** Không thể khẳng định. Cần đọc điều khoản và đánh giá trách nhiệm khi xảy ra sự cố.

**Lỗi 10 — “Nhìn thấy log của một lần gọi chứng minh đầy đủ coverage.”** Không. Cần kiểm tra cả các đường gọi trực tiếp, nội bộ, static, async và các phiên bản thư viện khác.

## 18. Kiểm thử trước khi tuyên bố can thiệp thành công

Một bộ kiểm thử nên bao gồm **những nơi advice phải chạy** và **những nơi không được chạy**.

| Ca kiểm thử | Điều cần xác minh |
| --- | --- |
| Lời gọi ứng dụng qua proxy | Advice chạy đúng số lần |
| Lời gọi trực tiếp target | Không bị nhầm là đi qua proxy |
| Lời gọi nội bộ `this.method()` | Hành vi đúng giới hạn công cụ |
| Method overload | Không chọn thừa hoặc bỏ sót chữ ký |
| Final/static/constructor | Có/không theo khả năng từng kỹ thuật |
| Phương thức private | Không mặc định có thể truy cập |
| Class nạp trước transformer | Có cần/cho phép retransformation không |
| Class loader khác nhau | Helper/advice có thể được liên kết đúng không |
| Named module | Quyền export/open/readability phù hợp không |
| JAR signed | Artifact có còn hợp lệ theo chính sách không |
| Method ném ngoại lệ | Caller thấy lỗi/cause đúng hợp đồng |
| Advice hậu xử lý ném lỗi | Không nhầm business thất bại khi đã có side effect |
| Nhiều aspect cùng khớp | Thứ tự có hiệu lực đúng |
| Hot path đồng thời | Không trộn context, không chi phí quá mức |
| Nâng cấp vendor JAR | Báo cáo tập điểm khớp thay đổi |
| Thư viện native hoặc remote | Ranh giới Java/client được hiểu đúng |

### 18.1. Chứng cứ ba tầng

Muốn chắc chắn không bỏ sót, hãy thu thập:

1. **Tầng cấu trúc:** pointcut chọn shadow nào, class nào được đưa vào weaving.
2. **Tầng triển khai:** transform/proxy/adapter có được cài đặt và kết nối đúng không.
3. **Tầng runtime:** các invocation thực tế có đi qua advice, guard và continuation đúng như chính sách không.

Một báo cáo pointcut khớp tĩnh không đủ nếu lời gọi bypass proxy. Một log runtime từ một đường gọi cũng không chứng minh tất cả phương thức trong JAR đã được weaving.

## 19. Chẩn đoán khi advice không chạy ở thư viện vendor

Áp dụng quy trình theo thứ tự:

1. **Tái hiện chính xác đường gọi:** client đi qua proxy hay gọi trực tiếp?
2. **Xác định điểm muốn can thiệp:** call site ở app, execution ở vendor, hay native/remote boundary?
3. **Kiểm tra cú pháp pointcut và chữ ký:** declaring type, overload, modifier, subtype và annotation.
4. **Xác minh phạm vi weaving:** app-only, JAR bên ngoài, package/class cụ thể.
5. **Kiểm tra class loader/module:** class đang chạy có đến từ loader khác, module không mở, hay class bị trùng không?
6. **Kiểm tra thời điểm nạp:** class đã nạp trước transformer hoặc không thuộc tập retransformation?
7. **Kiểm tra quyền/kỹ thuật sửa class:** class có modifiable, bytecode hợp lệ, classpath helper đầy đủ không?
8. **Kiểm tra guard động:** tham số, runtime type hoặc ngữ cảnh có làm pointcut trượt không?
9. **Kiểm tra advice khác:** outer around advice có thể ngăn `proceed` đến pointcut trong.
10. **Đọc log và report của weaver:** không nhầm “không có log” với “không weaving”.
11. **Giảm thành ví dụ tối thiểu:** kiểm tra một lần qua proxy và một lần bypass.
12. **Đối chiếu build/version:** đúng JAR vendor và đúng cấu hình agent chưa?

**Không chữa lỗi bằng cách mở rộng wildcard vô hạn.** Nếu vấn đề là class loader hoặc đường gọi không qua trung gian, pointcut rộng hơn thường không giải quyết được và còn gây can thiệp thừa.

## 20. Ma trận lựa chọn theo nhu cầu và quyền kiểm soát

Một bảng quyết định thực tế:

| Mục tiêu | Kiểm soát được gì? | Lựa chọn nên xem trước | Khi cần phương án mạnh hơn |
| --- | --- | --- | --- |
| Đo thời gian mọi lần app gọi vendor API | Chỗ app tạo/gọi client | Wrapper, interface proxy | Call-site weaving nếu nhiều đường khó thay |
| Quan sát private helper của vendor | Chỉ có JAR | Kiểm tra public API/hook trước | Bytecode weaving nếu thật sự cần và được phép |
| Chặn external SDK static utility | Mã gọi trong app | Application-owned facade | Call-site/bytecode weaving có kiểm chứng |
| Theo dõi thư viện được nạp khi chạy | Quyền JVM/agent | Load-time instrumentation phù hợp | Retransform nếu cần và được hỗ trợ |
| Áp policy trên microservice vendor ở máy khác | Chỉ kiểm soát client | Client interceptor/network telemetry | Cần quyền ở server mới quan sát internals |
| Can thiệp class final | Có interface public | JDK interface proxy | Bytecode instrumentation hoặc adapter khi phù hợp |
| Can thiệp trên module đóng | Quyền ứng dụng hạn chế | Public API/adapter | Cấu hình module/agent được phê duyệt |
| Duy trì qua nâng cấp vendor thường xuyên | Chỉ API ổn định | Facade/adapter có kiểm thử | Instrument internals chỉ khi có năng lực bảo trì |

**Lựa chọn ưu tiên thường là ít xâm lấn nhất nhưng vẫn đủ phạm vi chính sách.** Nếu public API/hook đáp ứng yêu cầu, đó thường là phương án dễ nâng cấp hơn việc dệt vào private implementation.

## 21. Một số trường hợp nâng cao cần cân nhắc

### 21.1. Multi-release JAR và biến thể runtime

Một số artifact Java có các phiên bản class khác nhau cho những mức Java khác nhau. Khi kiểm tra bytecode và xây matcher, cần xác định **class thực sự được JVM chọn**. Dệt nhầm biến thể hoặc kiểm thử trên JDK khác production có thể tạo kết luận sai.

### 21.2. Method bridge/synthetic do compiler sinh

Generic types và đa hình có thể làm Java compiler sinh các method bridge/synthetic. Weaver nhìn vào bytecode có thể thấy nhiều method hơn người đọc source dự đoán. Nếu advice áp vào cả bridge và target một cách không phù hợp, có thể quan sát log thừa hoặc thời gian bị đo lặp.

Không có quy tắc chung rằng mọi weaver đều tự loại những method đó; phải xem model/matcher của công cụ và kiểm thử.

### 21.3. Thư viện dùng callback

Một vendor library có thể gọi callback do ứng dụng cung cấp. Khi đó điểm callback nằm trong mã **do ta sở hữu**, nên đôi khi có thể áp advice ngay trên callback để quan sát sự kiện mà không sửa JAR vendor.

Nhưng callback chỉ cho thông tin mà API cung cấp. Nó không biến mọi thao tác nội bộ thành sự kiện có thể nhìn thấy.

### 21.4. Thư viện hoạt động bất đồng bộ

Khi API trả `Future` hoặc `CompletableFuture`, method return không đồng nghĩa công việc bên trong đã hoàn tất. Proxy chỉ đo thời gian gọi `submit()` có thể bỏ qua thời gian task chạy về sau. Nếu cần đo completion, phải gắn logic vào stage phù hợp hoặc dùng hook của API, giữ ngữ cảnh và lỗi đúng vòng đời.

### 21.5. Hot instrumentation và thay đổi đang chạy

Định nghĩa lại hoặc retransformation class lúc JVM đang chạy có những hạn chế ngữ nghĩa và tác động đến frame đang hoạt động. Không mặc định mọi lời gọi đang chạy lập tức đổi sang mã mới; cần hiểu hợp đồng runtime đối với active frames và xác minh khả năng trên JVM đích.

### 21.6. Nhiều bộ biến đổi cùng tác động một lớp

Profiler, coverage agent, security instrumentation và AOP weaver có thể cùng xử lý một class. **Thứ tự transformer** và tính tương thích của bytecode sau từng bước có thể ảnh hưởng kết quả. Cần quan sát thứ tự áp dụng, kiểm tra có nhân đôi instrumentation hay làm hỏng stack map/verification không, và tránh giả định một transformer luôn hoạt động độc lập.

## 22. Bảo mật và trách nhiệm khi can thiệp code không thuộc mình

Instrumenting thư viện không chỉ là vấn đề kỹ thuật. Một advice có thể:

- Truy cập dữ liệu trước khi thư viện bảo vệ/che đi.
- Ghi log tham số nhạy cảm hoặc token.
- Làm thay đổi luồng xác thực/ủy quyền.
- Nuốt ngoại lệ của cơ chế bảo mật.
- Thay đổi kết quả phương thức theo cách caller không mong đợi.
- Thêm dependency dễ xung đột hoặc tạo cơ chế nạp code mới.
- Làm sai thông tin kiểm toán hoặc ảnh hưởng hiệu năng toàn ứng dụng.

Vì vậy cần quy định **ai được sửa cấu hình weaving**, **những module/class nào được phép can thiệp**, **review/test ra sao**, **log được phép lưu gì** và **cách rollback/disable** khi gặp sự cố.

Một pointcut rộng vào thư viện mật mã hoặc bảo mật có thể tác động tới nhiều luồng trọng yếu. Không nên can thiệp các thành phần nhạy cảm chỉ vì engine cho phép; phải có mục đích chính đáng, phạm vi rõ và kiểm chứng an toàn.

## 23. Bài tập tự luyện

**1.** Ứng dụng gọi thư viện ngoài qua một interface public, chỉ cần log các lời gọi do ứng dụng phát ra. Có cần sửa JAR không?

Không nhất thiết. Có thể dùng adapter/proxy hoặc cơ chế can thiệp vào call site do ứng dụng sở hữu, miễn tất cả đường gọi mong muốn thực sự đi qua ranh giới đó.

**2.** Vendor method tự gọi `this.internalMethod()`. Vì sao JDK dynamic proxy không tự thấy lời gọi nội bộ?

Vì lời gọi ấy thực hiện trên đối tượng target, không chuyển qua proxy mà ứng dụng tạo ở bên ngoài.

**3.** Một class `final` có thể được quan sát qua JDK interface proxy không?

Có thể, nếu class triển khai interface phù hợp và ứng dụng sử dụng proxy của interface đó. Final class không thể subclass theo Java thông thường, nhưng proxy giao diện không cần subclass target.

**4.** Một Java agent được kích hoạt sau khi class vendor đã nạp. Có thể khẳng định class đó sẽ được weave không?

Không. Phải kiểm tra khả năng retransformation/redefinition, cấu hình agent và class có modifiable hay không.

**5.** Tệp JAR gốc chưa có lệnh advice khi xem bằng `javap`. Điều đó chứng minh load-time weaving không chạy không?

Không. Class có thể được biến đổi khi nạp. Cần quan sát bytecode/runtime report đúng thời điểm.

**6.** Một thư viện nằm trong named module không mở private package. Có thể mặc định reflection vào mọi private field không?

Không. Module encapsulation và access rules có thể ngăn deep reflection. Cần dùng public API hoặc cấu hình được cho phép; bytecode instrumentation là một vấn đề năng lực khác, không phải “tự động vượt quyền”.

**7.** Ứng dụng Java gọi một dịch vụ vendor qua HTTP. AOP trong ứng dụng có nhìn thấy private method ở server không?

Không, vì mã ấy chạy ở tiến trình bên ngoài. Ta có thể quan sát client request/response; muốn instrument server cần quyền trên hệ thống server và công cụ tương ứng.

**8.** Vì sao cần xem license trước khi phân phối JAR vendor đã dệt?

Vì việc có thể biến đổi về kỹ thuật không xác nhận quyền sửa hoặc phân phối lại. Artifact signed, hỗ trợ nhà cung cấp và trách nhiệm cập nhật cũng có thể bị ảnh hưởng.

**9.** Khi nâng cấp thư viện, pointcut vẫn hợp cú pháp nhưng advice biến mất. Những nguyên nhân nào nên kiểm tra?

Đổi method signature, đổi loader/module, class khác được runtime chọn, thay đổi phạm vi weaving, điều kiện động, việc gọi không đi qua proxy, hoặc private implementation đã refactor.

**10.** Nếu muốn ghi trace một lần cho mỗi thao tác vendor, tại sao không tự động dệt cả mọi private helper?

Có thể tạo log thừa và chi phí không cần thiết; một thao tác public gọi nhiều helper, dẫn tới đếm sai số sự kiện. Cần chọn ranh giới nghiệp vụ phù hợp và kiểm thử số lần advice chạy.

## 24. Checklist áp dụng trước khi can thiệp thư viện ngoài

- [ ] Xác định chính xác **điểm call hay execution** cần can thiệp.
- [ ] Biết thư viện chạy trong cùng JVM hay tiến trình ngoài.
- [ ] Xác định chỉ cần quan sát lời gọi từ app hay cả hoạt động nội bộ.
- [ ] Đánh giá quyền sở hữu source/artifact/runtime và điều khoản cấp phép.
- [ ] Ưu tiên public hook, adapter hoặc proxy khi đủ yêu cầu.
- [ ] Đã kiểm tra công cụ hỗ trợ loại join point cần thiết.
- [ ] Đã kiểm tra phạm vi weaving của các class thư viện.
- [ ] Với load-time weaving, xác minh thời điểm class được nạp và transformer.
- [ ] Với retransformation, kiểm tra capability và giới hạn thay đổi class.
- [ ] Kiểm tra Java module, class loader và khả năng nạp helper.
- [ ] Kiểm tra final/private/static/constructor theo cơ chế thực tế, không suy chung.
- [ ] Xác minh signed JAR, hash, phiên bản và provenance nếu đổi artifact.
- [ ] Có kiểm thử qua proxy và trực tiếp target.
- [ ] Có kiểm thử self-invocation và internal call nếu thuộc phạm vi mong muốn.
- [ ] Có test overload, override, bridge/synthetic và phiên bản vendor mới khi liên quan.
- [ ] Kiểm tra return, exception, cause và side effect vẫn đúng hợp đồng.
- [ ] Không log dữ liệu nhạy cảm từ vendor args/result.
- [ ] Có số đo overhead cho hot path và tải đồng thời.
- [ ] Có rollback hoặc cách tắt instrumentation khi phát sinh lỗi.
- [ ] Có báo cáo những class/join point đã được xử lý và những đường không bao phủ.

## 25. Tài liệu tham khảo cho các khả năng JVM cụ thể

Các nguồn dưới đây mô tả **API Java SE 17**, không phải một đặc tả chung của mọi AOP engine:

- [Java 17 — `Proxy`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Proxy.html): dynamic proxy theo interface và các điều kiện module/loader.
- [Java 17 — `ClassFileTransformer`](https://docs.oracle.com/en/java/javase/17/docs/api/java.instrument/java/lang/instrument/ClassFileTransformer.html): bytecode transform khi định nghĩa/nạp, redefine, retransform.
- [Java 17 — `Instrumentation`](https://docs.oracle.com/en/java/javase/17/docs/api/java.instrument/java/lang/instrument/Instrumentation.html): agent instrumentation, capability và giới hạn retransformation.
- [Java 17 — Gói `java.lang.instrument`](https://docs.oracle.com/en/java/javase/17/docs/api/java.instrument/java/lang/instrument/package-summary.html): cơ chế agent và cấu hình liên quan.

Khi áp dụng với một công cụ AOP thật, cần đối chiếu thêm tài liệu chính thức của **chính công cụ ấy** về pointcut, loại join point, thứ tự advice, phạm vi weaving và tương thích bytecode.

## 26. Kết luận

**Giới hạn sở hữu mã không đồng nhất với giới hạn khả năng can thiệp.** Ta có thể quan sát hành vi của thư viện ngoài bằng wrapper, proxy, call-site weaving, biến đổi bytecode hoặc instrumentation — nhưng mỗi lựa chọn yêu cầu một mức kiểm soát khác nhau đối với đường gọi, lớp được nạp, artifact hoặc runtime.

Để chọn đúng phương án, hãy luôn làm rõ ba ranh giới:

1. **Ranh giới kỹ thuật:** điểm nào thuộc mô hình join point và phạm vi weaving có thật.
2. **Ranh giới kiểm soát:** ứng dụng sở hữu call site, class loader, JVM, artifact hay chỉ có public API.
3. **Ranh giới trách nhiệm:** giấy phép, bảo mật, tương thích, hỗ trợ nhà cung cấp và hành vi nghiệp vụ khi advice hoạt động.

Phương án tốt là phương án **ít xâm lấn nhất nhưng đủ phạm vi yêu cầu**, có thể chứng minh bằng kiểm thử dương/âm, có khả năng truy vết phiên bản và không dựa vào giả định rằng một pointcut có thể tự động kiểm soát toàn bộ mã bên ngoài.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="weaving-lifecycle-008">So sánh độ bao phủ, chi phí và độ phức tạp vận hành</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh độ bao phủ, chi phí và độ phức tạp vận hành theo thời điểm dệt AOP

## 1. Vì sao thời điểm dệt ảnh hưởng đến thiết kế?

Trong **Aspect-Oriented Programming (AOP)**, việc dùng **pointcut** để chọn **join point** và dùng **advice** để bổ sung hành vi mới chỉ là phần mô tả logic. Một phần quan trọng không kém là **cách và thời điểm** hệ thống gắn hành vi đó vào chương trình: lúc biên dịch, sau biên dịch, lúc class được nạp, hay khi một lời gọi đi qua proxy/interceptor ở runtime.

Các chiến lược này có thể tạo ra cùng một trace đơn giản:

~~~text
before
core: execute
after
~~~

Nhưng chúng **không tương đương** về các đặc tính quan trọng:

- **Độ bao phủ:** có chặn được những lời gọi nào, method body nào, code thư viện nào, class loader nào; có theo được gọi nội bộ, constructor và field access không?
- **Chi phí:** phải trả bao nhiêu cho việc dệt lúc build, biến đổi class khi load, tạo proxy, kiểm tra điều kiện động, dispatch lời gọi, lưu bytecode mới và ghi log?
- **Độ phức tạp vận hành:** cần build/deploy gì, có dễ tìm lỗi thiếu advice không, có kiểm soát class loader hoặc agent không, cập nhật/rollback có an toàn không?
- **Tính đúng đắn:** có bảo toàn return value, exception, locking, thứ tự advice và số lần target chạy không?

**Đề bài ở đây không phải tìm ra một kỹ thuật “nhanh nhất” hay “mạnh nhất” tuyệt đối.** Mục tiêu là biết đo và cân nhắc các đánh đổi theo nhu cầu cụ thể. Độ bao phủ phụ thuộc **join point model** và phạm vi instrument của **công cụ AOP được chọn**, không chỉ nhãn “compile-time” hay “runtime”.

## 2. Phân loại cho đúng trước khi so sánh

Có hai trục phân loại khác nhau:

**Trục A — Thời điểm liên kết hành vi**

1. **Compile-time weaving (CTW):** gắn logic AOP trong hoặc trước quá trình biên dịch chương trình.
2. **Post-compile/binary weaving (PCW):** nhận artifact đã biên dịch, biến đổi thành artifact có hành vi AOP trước khi chạy.
3. **Load-time weaving (LTW):** biến đổi bytecode khi class được định nghĩa trong runtime, dựa trên bộ dệt hoặc transformer đã đăng ký.
4. **Runtime interception/wiring:** tạo proxy/decorator hoặc dispatcher/interceptor chain để lời gọi đi qua nó lúc runtime.

**Trục B — Lớp biểu diễn chịu tác động**

- Source code hoặc abstract syntax tree (AST).
- Intermediate representation (IR) của compiler.
- Bytecode/class file hoặc artifact biên dịch tương đương.
- Object graph, proxy reference, call route hoặc invocation chain.

**Quan trọng:** load-time weaving thường vẫn biến đổi **bytecode**, còn runtime proxy có thể **không sửa bytecode của target**. Vì thế không nên định nghĩa hai thuật ngữ ấy như hai nơi chèn mã hoàn toàn ngang hàng.

Một ngôn ngữ AOP cụ thể có thể hỗ trợ nhiều thời điểm. Hơn nữa, một dự án có thể cùng lúc dùng binary weaving cho một concern và proxy cho concern khác. Sự kết hợp đó là hợp lệ nhưng gây rủi ro advice bị áp dụng chồng nhau.

## 3. Cùng một yêu cầu, bốn con đường áp dụng

Giả sử cần ghi nhận trước/sau mọi lần thực thi của một thao tác nghiệp vụ.

~~~text
                        ASPECT
              pointcut + before/after advice
                          │
             ┌────────────┼─────────────┬─────────────┐
             ▼            ▼             ▼             ▼
        COMPILE TIME   POST COMPILE   LOAD TIME    RUNTIME ROUTE
             │            │             │             │
        Compiler tạo   Weaver sửa    Transformer   Proxy / decorator
        mã đã dệt      class/JAR     sửa class khi intercept lời gọi
             │            │          define         │
             └────────────┴─────────────┴─────────────┘
                          │
                          ▼
                  Chạy chương trình thật
                          │
                     Join point khớp?
                          │
                  ┌───────┴───────┐
                 Không            Có
                  │                │
                  ▼                ▼
             Không chạy         Advice chạy
             advice            ở lượt đó
~~~

**Weave xong không có nghĩa advice đã chạy.** Compile có thể kết thúc hôm nay, class được nạp ngày mai, và join point chỉ được thực thi khi một request xuất hiện. Tương tự, proxy có thể được tạo mà không hề có một cuộc gọi nào đi qua nó.

Khi đối chiếu các thời điểm dệt, phải đánh giá riêng **thời điểm tạo khả năng can thiệp** và **thời điểm trả chi phí của mỗi invocation**.

## 4. Định nghĩa “độ bao phủ” một cách có thể kiểm thử

Độ bao phủ AOP không chỉ là tỷ lệ method có log. Nên tách ít nhất năm chiều:

| Chiều | Câu hỏi |
|---|---|
| **Loại join point** | Method call, method execution, constructor, field get/set, static initialization có được hỗ trợ không? |
| **Phạm vi code** | Chỉ module mình build, cả thư viện binary, code từ nhiều loader, code sinh động? |
| **Đường lời gọi** | Caller gọi qua proxy hay trực tiếp target? Self-invocation có được nhận diện? |
| **Quan hệ kiểu/đa hình** | Interface, superclass, subclass override, method kế thừa và bridge method có được chọn đúng? |
| **Điều kiện runtime** | Target subtype, arguments, annotation, control-flow có khiến lần này khớp nhưng lần khác không? |

Một cách đo hữu ích:

~~~text
Coverage theo yêu cầu =
    số tình huống bắt buộc áp dụng advice đã được xác nhận
    -----------------------------------------------------
    tổng số tình huống bắt buộc theo đặc tả
~~~

Đây là **chỉ số kiểm thử do dự án định nghĩa**, không phải một tỷ lệ có sẵn mà engine AOP tự cung cấp. Mẫu số phải nêu rõ các **trường hợp cần được bảo vệ/quan sát**. Đừng gộp test case vào một phép đếm method rồi tuyên bố đạt đủ mọi đường gọi.

### 4.1. Độ bao phủ “phải chạy” và “không được chạy”

Một pointcut quá rộng cũng là lỗi. Có thể dùng hai phép kiểm:

- **Recall có ý nghĩa nghiệp vụ:** mọi đường bắt buộc đều được advice áp dụng.
- **Precision có ý nghĩa nghiệp vụ:** các đường ngoài phạm vi không bị advice áp dụng.

Ví dụ một policy chỉ dành cho public API nhưng vô tình log mọi helper nội bộ có thể làm tăng chi phí, lộ dữ liệu hoặc tạo advice recursion. Vì vậy nên có cả **positive tests** và **negative tests**.

### 4.2. Bản đồ bao phủ mới có giá trị

Một danh sách “100% methods woven” không đủ nếu caller đang chạy các class được nạp bởi loader khác, hoặc một proxy chỉ được dùng ở một nhánh. Cần mô tả **những join point thực tế** và **đường code đi đến chúng**.

## 5. Bảng so sánh tổng quan bốn chiến lược

Bảng dưới thể hiện **xu hướng thường gặp, có điều kiện**, không phải xếp hạng tuyệt đối theo một sản phẩm.

| Tiêu chí | Compile-time | Post-compile binary | Load-time | Runtime proxy/interceptor |
|---|---|---|---|---|
| **Đầu vào chính** | Source/AST/IR + aspect | Class/JAR + aspect | Class bytes + transformer | Target/interface + handler |
| **Đầu ra chính** | Binary sinh từ mã đã dệt | Binary woven | Class definition đã biến đổi | Proxy/reference/route |
| **Chi phí setup** | Lúc build | Lúc build/release | Lúc class loading | Khi tạo proxy/chain |
| **Chi phí runtime** | Code advice và điều kiện động nếu có | Tương tự binary đã dệt | Code advice sau biến đổi | Handler/delegation và code advice |
| **Sửa target body được không?** | Tùy compiler/weaver | Có nếu engine hỗ trợ | Có nếu transform hợp lệ | Thường không cần sửa body |
| **Self-invocation** | Có thể thấy nếu weave execution liên quan | Có thể nếu weave execution | Có thể nếu weave execution | Có thể bypass proxy |
| **Thư viện chỉ có binary** | Không tự có source; tùy pipeline | Có thể weave binary | Có thể transform khi load | Có thể bọc public boundary |
| **Constructor/field/static** | Tùy join point model | Tùy engine | Tùy engine | Proxy interface không tự hỗ trợ |
| **Artifact dễ kiểm tra trước deploy** | Thường có | Có woven binary cụ thể | Không nhất thiết có file woven trên đĩa | Cần kiểm tra object wiring |
| **Vận hành phức tạp nổi bật** | Compiler integration, rebuild | Artifact provenance/reweave | Agent, loader, startup, module | Reference routing, bypass, chain order |
| **Rollback điển hình** | Redeploy bản build trước | Redeploy woven artifact trước | Thay config/agent rồi restart hoặc cơ chế hợp lệ | Cấp lại reference/chain theo lifecycle |

Thứ tự nào “tốt hơn” phụ thuộc loại join point cần chọn, ràng buộc triển khai và mức rủi ro chấp nhận được. Không chọn chỉ theo số cột “Có”.

## 6. Compile-time weaving: độ bao phủ, chi phí và vận hành

### 6.1. Độ bao phủ

Một compiler-integrated weaver có thể dùng source/AST/IR và type information để nhận diện join point trong **tập code đi qua compiler pipeline đó**. Nếu compiler và ngôn ngữ AOP hỗ trợ method execution hoặc call-site instrumentation, điểm trong tập compile có thể được dệt trước khi deploy.

Thế mạnh:

- Rõ **đầu vào được build** và artifact đầu ra; phù hợp với dự án có quản lý nguồn.
- Có thể tận dụng semantic information mà compiler cung cấp.
- Có thể tạo artifact đã có hành vi, ít phụ thuộc vào agent runtime.

Giới hạn:

- Code trong thư viện **chỉ có binary** thường không tự nằm trong source transform; một compiler có hỗ trợ binary weaving riêng mới có thể xử lý kiểu đó.
- Class sinh ra hoặc được nạp theo đường nằm ngoài pipeline có thể không được bao phủ.
- Chữ ký thay đổi, class chuyển package hoặc thêm override có thể làm pointcut match khác sau rebuild.
- Khả năng chặn private/final/static/constructor tùy compiler-weaver; **không thể suy từ thời điểm compile**.

### 6.2. Chi phí

Một phần chi phí dịch chuyển sang build:

- Phân tích type hierarchy, matching pointcuts.
- Biến đổi source/IR và sinh helper code.
- Chạy compile, tạo artifact và verify.
- Build cache/rebuild khi code hoặc aspect thay đổi.

Ở runtime **vẫn có chi phí advice và điều kiện runtime**. Nếu advice làm I/O hoặc một pointcut có dynamic predicate, code đã dệt không làm tác vụ ấy trở nên miễn phí.

### 6.3. Độ phức tạp vận hành

- Cần toolchain compiler/weaver version tương thích.
- Quy trình kiểm tra generated source, warnings, build cache và source mapping.
- Artifact phải được tạo lại khi aspect rule đổi.
- Deploy/rollback có thể rõ ràng thông qua các bản build bất biến.
- Nếu dùng compiler extension đặc thù, nâng phiên bản compiler có thể làm build fail.

**Phù hợp khi:** kiểm soát được nguồn và pipeline, muốn artifact có thể tái lập và có thể chấp nhận rebuild/redeploy cho thay đổi aspect.

## 7. Post-compile weaving: độ bao phủ, chi phí và vận hành

Post-compile weaving nhận **artifact đã biên dịch** rồi tạo một **artifact woven**:

~~~text
Source → compiler → Original.class/JAR
                               │
                               ▼
                   Binary weaver + aspect
                               │
                               ▼
                     Woven.class/JAR
                               │
                               ▼
                              Runtime
~~~

### 7.1. Độ bao phủ

Thường hữu ích khi **không có source của thư viện** nhưng có binary hợp lệ mà weaver được phép instrument. Có thể lựa chọn method execution hoặc call sites trong những class/JAR được đưa vào weave, tùy ngôn ngữ AOP.

Tuy nhiên:

- Không phải mọi binary đều được phép hoặc có thể được biến đổi tùy chữ ký số, license, deployment policy và engine.
- Nếu chỉ weave target nhưng pointcut muốn chọn **call sites trong các caller ngoài tập binary**, những vị trí đó vẫn có thể không được chạm tới.
- Nếu một subclass binary mới không được đưa vào pipeline, execution override của nó có thể bị bỏ sót.
- Class file phiên bản không tương thích hoặc có những instruction engine không hiểu có thể gây lỗi.

### 7.2. Chi phí

Trả thêm bước build/release:

- Đọc/giải mã class/JAR.
- Match điểm, sinh instruction hoặc helper.
- Cập nhật class metadata, stack map frames, exception handlers nếu cần.
- Kiểm tra class-file correctness, test artifact sau dệt.
- Lưu cả artifact trước và sau weaving nếu cần đối chiếu.

Runtime đã nhận một artifact có code AOP, nhưng vẫn phải chạy advice, object allocation và runtime tests nếu pointcut đòi hỏi.

### 7.3. Độ phức tạp vận hành

Điểm khó quan trọng nhất là **provenance**:

- Binary nào đã được weave, version và checksum ra sao?
- Pipeline đóng gói **bản woven** hay vô tình lấy bản chưa weave?
- Weaver được chạy một lần hay bị chạy lặp lên output trước?
- Có thể tái tạo cùng một output từ cùng input/config không?
- Khi rollback, có khôi phục cả artifact và version aspect tương thích không?

Một build báo weave thành công nhưng deploy classpath vẫn trỏ đến binary cũ là lỗi vận hành, không phải lỗi của advice.

**Phù hợp khi:** cần tác động vào binary, muốn phát hành artifact woven cụ thể và có kiểm soát build/deploy chặt.

## 8. Load-time weaving: độ bao phủ, chi phí và vận hành

Một runtime có thể đăng ký **class-file transformer** để xử lý bytecode trước khi JVM **define class**.

~~~text
Original JAR trên đĩa
        │
        ▼
Class loader chuẩn bị define
        │
        ▼
Transformer nhận class bytes
        │
        ▼
Woven class definition trong JVM
        │
        ▼
Method/điểm đã weave được thực thi sau đó
~~~

### 8.1. Độ bao phủ

Điểm mạnh tiềm năng: có thể instrument các class **được nạp sau khi cấu hình weaving có hiệu lực**, kể cả một số thư viện mà ứng dụng chỉ có binary, nếu engine/agent/class loader cho phép.

Nhưng coverage cần kiểm tra từng đường:

- Transformer có chạy với **class loader** đó không?
- Có class nào đã được define **trước lúc agent/transformer đăng ký** không?
- Module và quyền truy cập có cho phép code helper hoạt động không?
- Một class được nạp lại qua loader khác có nhận cùng aspect không?
- Class được exclude vì package/loader hoặc không match pointcut không?

Một engine có hỗ trợ **retransform/redefine** không mặc định được sửa mọi thành phần cấu trúc của class đã nạp; giới hạn của JVM/API phải được kiểm tra.

### 8.2. Chi phí

Chi phí có thể chuyển từ build sang **startup/class loading**:

- Đăng ký, khởi tạo agent và transformer.
- Phân tích/biến đổi mỗi class được chọn.
- Verify, link và load những helper liên quan.
- Thời gian và bộ nhớ tạm cho byte arrays/class metadata.
- Khả năng ảnh hưởng cold-start và thời điểm một module được sử dụng lần đầu.

**Lưu ý:** class loading thường diễn ra trước một số lời gọi, không phải transformer chạy lại cho mỗi invocation. Sau khi class được định nghĩa với code đã weave, lời gọi method thông thường thực thi code đó; không tự trả lại “chi phí biến đổi bytecode” ở mọi lần gọi.

### 8.3. Độ phức tạp vận hành

- Cấu hình agent/transformer phải được cài đúng trước class load cần thiết.
- Độ khó quan sát cao hơn vì binary trên đĩa **có thể không phải bytes mà JVM đã dùng**.
- Nhiều class loader và nhiều transformer có thể có thứ tự ảnh hưởng kết quả.
- Log trong chính quá trình nạp class có thể gây reentrancy nếu helper lại kích hoạt class loading.
- Thay cấu hình có thể yêu cầu restart hoặc cơ chế retransform hợp lệ; không có bảo đảm mọi class đang sống lập tức đổi.

**Phù hợp khi:** cần tác động lên class lúc runtime nạp mà không muốn phát hành một bản JAR woven riêng, đồng thời đội ngũ đủ khả năng quan sát và vận hành class loading/agent.

## 9. Runtime proxy/interception: độ bao phủ, chi phí và vận hành

Proxy, decorator và interceptor chain tạo một **đường gọi được bao ngoài**:

~~~text
Caller → Proxy/interceptor → Target
            │                 │
            ├─ before         │
            ├─ invoke ───────►│
            └─ after ◄────────┘
~~~

### 9.1. Độ bao phủ

Độ bao phủ phụ thuộc **caller có sử dụng đúng reference proxy** và **method có được proxy expose/intercept**.

Điểm mạnh:

- Dễ giới hạn chính sách tại boundary/API đã định nghĩa.
- Có thể bọc implementation mà không sửa class gốc.
- Có thể chọn interceptor chain khác nhau cho các object hoặc request theo thiết kế.

Giới hạn:

- **Bypass:** caller cầm target gốc có thể gọi thẳng.
- **Self-invocation:** target tự gọi method khác trên chính nó thường không đi lại qua proxy.
- **Interface proxy:** chỉ những method thuộc interface được proxy implement mới có đường dispatch handler tương ứng.
- **Static/private/final/constructor/field:** cần xem loại proxy hoặc engine cụ thể, không coi mọi khả năng đều tồn tại.
- **Call-site context:** một proxy nhận method call nhưng không tự biết chính xác source location của mọi caller như một call-site weaver có thể biết.

### 9.2. Chi phí

Chi phí phân bố vào:

- Tạo proxy/decorator và lắp interceptor chain.
- Dispatch mỗi invocation qua tầng bao.
- Reflection hoặc method-handle call nếu engine dùng; boxing/array allocation ở một số API.
- Runtime pointcut checks nếu có.
- Chi phí code advice thực sự chạy.

Một proxy chỉ có một interceptor không có cùng overhead với một chain nhiều tầng, nhiều dynamic predicate và logging network. JIT có thể tối ưu nhiều đường gọi; không đo thì không có cơ sở kết luận chênh lệch bao nhiêu.

### 9.3. Độ phức tạp vận hành

- Phải kiểm soát **object creation/wiring** để caller nhận đúng proxy.
- Cần biết vòng đời proxy so với target.
- Nếu policy quan trọng, phải tìm đường giữ target gốc hoặc self-call ngoài ý muốn.
- Thứ tự nhiều interceptor phải được đặc tả và test.
- Thay config có thể cần dựng chain/proxy mới hoặc một cơ chế dynamic refresh được engine hỗ trợ.
- Dễ debug một số boundary, nhưng lỗi coverage có thể ẩn vì code target vẫn chạy đúng, chỉ **thiếu advice**.

**Phù hợp khi:** concern ở public/service boundary rõ, object graph được quản lý và không yêu cầu quan sát mọi điểm bên trong implementation.

## 10. So sánh riêng từng chiều bao phủ

### 10.1. Khi cần chặn method execution ở mọi caller

Một execution weaver instrument trực tiếp thân method có khả năng bắt lời gọi đến thân ấy từ các caller khác nhau, kể cả caller không biết aspect, **nếu** method thực thi đúng thân đã weave.

Proxy không có bảo đảm tương ứng nếu một caller giữ target gốc. Vì vậy, trong yêu cầu “mọi đường vào implementation phải qua kiểm tra”, nên xem xét trực tiếp độ bao phủ mà execution weaving thực sự đạt được hoặc ràng buộc kiến trúc khiến mọi caller chỉ dùng proxy.

### 10.2. Khi cần chặn đúng call site

Một call join point chọn **vị trí phát lời gọi**, có thể phân biệt một caller trong module A với caller trong module B. Để có bao phủ, **caller code** liên quan phải được instrument. Weave chỉ mỗi target body không đương nhiên biết call site nào đã phát lệnh.

Proxy thường biết invocation đến qua object đại diện nhưng không tự sở hữu toàn bộ thông tin lexical source location. Nếu chính sách phụ thuộc caller/source code, cần đánh giá khả năng hỗ trợ call join point của engine cụ thể.

### 10.3. Khi có lời gọi nội bộ

~~~java
class Task {
    void outer() { inner(); }
    void inner() { /* công việc */ }
}
~~~

Đoạn này chỉ minh họa. Nếu caller bên ngoài gọi proxy.outer, proxy có thể chặn outer; nhưng bên trong target, **inner()** không tự đi qua proxy.

Một weaver tại **execution của inner** có thể quan sát inner nếu method được instrument và pointcut khớp. Không nên áp giới hạn self-invocation của proxy thành định luật chung của AOP.

### 10.4. Với override và subtype

~~~java
BaseService service = new FastService();
service.execute();
~~~

Đây là minh họa cần định nghĩa hai class. Compiler có thể nhìn chữ ký BaseService tại call site; runtime dispatch tới override FastService.execute. Vì thế:

- Call pointcut có thể xét chữ ký ở caller.
- Execution pointcut có thể xét thân FastService.
- Dynamic target condition có thể xét kiểu thực của object.
- Weaving chỉ ở Base nhưng không ở override FastService có thể không đáp ứng yêu cầu.

Cần test cả subclass **kế thừa không override**, subclass **có override**, interface implementation và những method compiler sinh như bridge nếu công cụ ở mức bytecode liên quan.

### 10.5. Static, private, final và constructor

Không thể xếp hạng “CTW luôn chặn được” hoặc “LTW luôn chặn được”. Khả năng phụ thuộc ngôn ngữ AOP và join point model.

- **Static:** không có receiver instance theo nghĩa method instance.
- **Private:** không lộ qua interface proxy thông thường; một bytecode weaver có thể hỗ trợ execution tùy engine.
- **Final:** không thể override bằng subclass proxy; một bytecode weaver can thiệp trực tiếp có ràng buộc khác.
- **Constructor:** một số weaver có constructor call/execution join points; interface proxy không tự chặn quá trình khởi tạo target.
- **Field get/set:** có thể là join point ở mô hình weaving phong phú; method-only proxy không tự quan sát trực tiếp field access.

### 10.6. Class loader và code thư viện

Thời điểm dệt càng muộn **không tự khiến độ bao phủ rộng hơn**. Load-time engine có thể nhìn nhiều class runtime nhưng cũng có thể bị giới hạn ở loader, module và registration timing. Một post-compile binary weaver được cấu hình đầy đủ có thể có phạm vi thư viện rõ hơn, nhưng sẽ bỏ qua code sinh mới sau build nếu không có bước phù hợp.

Độ bao phủ phải chứng minh bằng **tập class/call sites được instrument** cộng **runtime invocation tests**, không phải tên pha.

## 11. Mô hình chi phí ba pha

Để so sánh công bằng, hãy tách chi phí thành:

**A. Chi phí tạo artifact/configuration**

- Build/compile, parse pointcut, match type hierarchy.
- Binary transformation và validation.
- Tạo proxy và interceptor chain.

**B. Chi phí khởi động / nạp class**

- Agent initialization và transformer work.
- Class loading/verification/linking và helper initialization.
- Khởi tạo proxy/graph nếu thực hiện ở startup.

**C. Chi phí ở mỗi lời gọi**

- Dynamic type/args/control-flow checks.
- Interceptor dispatch/delegation hoặc woven advice call.
- Advice body (logging, trace export, security checks, metrics).
- Side effects và synchronization.

Một mô hình tổng chi phí hữu ích:

~~~text
TotalCost ≈ BuildCost
          + NumberOfStarts × StartupCost
          + NumberOfInvocations × AverageInvocationCost
          + OperationalCost
~~~

Đây là **mô hình định tính**, không phải công thức đo chính xác mọi chi phí tài nguyên bằng cùng một đơn vị. Build time, latency, CPU, memory, dung lượng artifact và chi phí vận hành không thể tự cộng thành một số tiền nếu chưa có cách quy đổi.

### 11.1. Dịch chuyển chi phí không có nghĩa xóa chi phí

- CTW/PCW có thể dồn chi phí transformation vào build, nhưng advice runtime vẫn chạy.
- LTW có thể tăng startup/class-load time, nhưng **không phải một lượt transform cho mỗi method call**.
- Proxy có thể có chi phí tạo object khi dựng graph, và chi phí invocation routing sau đó.
- Logging I/O nhiều khi áp đảo chi phí dispatch; khi đó tranh luận “proxy hay bytecode nhanh hơn vài nano giây” có thể không liên quan đến nút thắt thật.

### 11.2. Chi phí cold-start và steady-state khác nhau

Một dịch vụ sống nhiều ngày có thể chấp nhận thêm thời gian khởi động để đổi lấy behavior cần thiết. Một workload serverless/chạy ngắn và khởi tạo nhiều lần có thể nhạy cảm với load-time instrumentation hoặc xây dựng object graph.

Ngược lại, một method được gọi hàng triệu lượt có thể đáng đo chi phí dispatch tại steady-state. Không có một phép đo startup nào đại diện cho steady-state, và ngược lại.

## 12. Benchmark như thế nào để không tự đánh lừa?

Đừng đưa ra bảng nano giây hay phần trăm “độ chậm” khi chưa có cùng môi trường kiểm chứng. Khi đo thực tế:

1. Giữ **cùng chức năng target**, cùng input và cùng hành vi advice ở các kịch bản.
2. Dùng **baseline không advice**, rồi đo từng cơ chế với policy tương đương.
3. Tách **thời gian build**, **startup**, **class-load**, **steady-state throughput**, **latency p50/p95/p99**, **allocation** và **memory footprint**.
4. Với Java, dùng công cụ microbenchmark phù hợp như **JMH** khi cần đo overhead nhỏ, có warmup, fork, công cụ tránh dead-code elimination và kiểm soát JIT.
5. Tránh dùng vòng lặp tự đo một lần trong main rồi kết luận hiệu năng tương đối của engine.
6. Lặp trên các cấu hình có **ít/nhiều join point**, **ít/nhiều advice**, có/không dynamic predicate.
7. Tách thí nghiệm advice rỗng/nhẹ và advice có I/O thật.
8. Kiểm tra cả GC, object allocation, logging queue/backpressure và contention.
9. Đo trên **đúng JDK, phần cứng, toolchain và workload** của hệ thống mục tiêu.
10. Báo cáo sai số, độ biến động và trade-off thay vì chọn một giá trị nhanh nhất.

**Không được dùng JDK proxy demo phần sau để chứng minh một kỹ thuật nhanh hơn kỹ thuật khác.** Demo chỉ xác minh phạm vi interception và số lần gọi; so sánh performance cần benchmark riêng.

## 13. Độ phức tạp vận hành: phải quản lý những gì?

### 13.1. Build và phát hành

- CTW cần compiler integration, generated code consistency, cache invalidation và rebuild khi thay aspect.
- PCW cần lưu original/woven artifact, kiểm tra binary provenance, ngăn double weaving.
- LTW cần agent/transformer có mặt đúng lúc, class loader/module compatibility.
- Proxy cần factory, wiring và lifecycle kiểm soát reference.

### 13.2. Chẩn đoán sự cố

“Không có log advice” có thể do:

- Pointcut không match.
- Join point chưa thực thi.
- Class chưa được weave.
- Binary chưa weave được đóng gói nhầm.
- Class được định nghĩa trước transformer.
- Class loader ngoài scope.
- Caller bypass proxy.
- Dynamic predicate false.
- Around advice ở tầng ngoài không gọi proceed.
- Log bị drop hoặc exporter lỗi.

Mỗi cơ chế có **bằng chứng chẩn đoán khác nhau**. Nếu dùng proxy, kiểm tra reference; nếu dùng LTW, kiểm tra loader/agent; nếu dùng binary weaving, kiểm tra artifact chạy thật.

### 13.3. Rollback và incident response

- Với artifact woven: rollback bằng **artifact version đã xác minh**.
- Với LTW: có thể cần đổi agent/config và restart, hoặc cơ chế retransform được hỗ trợ.
- Với proxy: có thể thay interceptor chain/wiring theo lifecycle, nhưng caller giữ proxy cũ có thể chưa cập nhật.
- Với source generation: cần build/deploy lại khi rule đã đi vào binary.

Một hệ thống cần yêu cầu rollback nhanh có thể ưu tiên cách có **versioning và khả năng chứng minh trạng thái runtime** tốt nhất cho môi trường đó.

### 13.4. Quan sát và giám sát

Nên thu các tín hiệu:

- Số join point thực sự được chọn và số advice invocations.
- Số target invocations so với advice, đặc biệt khi retry/short-circuit.
- Build/weave warnings hoặc runtime transformer errors.
- Class loader/class-definition diagnostic khi cần.
- Proxy routing anomalies hoặc invocation bypass trong test.
- Startup time, class-loading latency, allocation và CPU overhead.
- Số log/trace dropped nếu dùng exporter bất đồng bộ.

**Số advice invocation không tự bằng số nghiệp vụ.** Một request có thể tạo nhiều join point hoặc nhiều retries.

## 14. Java 17 runnable: chứng minh giới hạn coverage của runtime proxy

Chương trình độc lập sau dùng **JDK dynamic proxy**. Mục tiêu là phân biệt:

- Lời gọi trực tiếp target.
- Lời gọi qua proxy.
- Lời gọi nội bộ từ target method đến method khác trên chính target.
- Lời gọi riêng đến method đó qua proxy.
- Số lượt interceptor so với số lượt method body thực chạy.

Nó **không triển khai bytecode weaving**. Vì vậy không dùng ví dụ để suy số liệu overhead hoặc khả năng constructor/field join points của các engine khác.

Tạo file **CoverageBoundaryDemo.java**:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

public class CoverageBoundaryDemo {
    interface Service {
        int process(int value);
        void internalStep();
    }

    static final class Core implements Service {
        int processCalls;
        int stepCalls;

        @Override
        public int process(int value) {
            processCalls++;
            System.out.println("core: process " + value);
            internalStep();
            return value * 2;
        }

        @Override
        public void internalStep() {
            stepCalls++;
            System.out.println("core: step");
        }
    }

    static Service proxy(Service target, AtomicInteger interceptions) {
        return (Service) Proxy.newProxyInstance(
                Service.class.getClassLoader(),
                new Class<?>[]{Service.class},
                (instance, method, args) -> {
                    interceptions.incrementAndGet();
                    System.out.println("proxy: before " + method.getName());
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    } finally {
                        System.out.println("proxy: exit " + method.getName());
                    }
                });
    }

    public static void main(String[] args) {
        Core core = new Core();
        AtomicInteger interceptions = new AtomicInteger();
        Service observed = proxy(core, interceptions);

        System.out.println("direct:");
        System.out.println("result=" + core.process(2));

        System.out.println("through proxy:");
        System.out.println("result=" + observed.process(3));

        System.out.println("separate proxy step:");
        observed.internalStep();

        System.out.println("interceptions=" + interceptions.get());
        System.out.println("core processes=" + core.processCalls);
        System.out.println("core steps=" + core.stepCalls);
    }
}
~~~

Biên dịch và chạy trên JDK 17+:

~~~bash
javac --release 17 CoverageBoundaryDemo.java
java CoverageBoundaryDemo
~~~

Stdout chính xác:

~~~text
direct:
core: process 2
core: step
result=4
through proxy:
proxy: before process
core: process 3
core: step
proxy: exit process
result=6
separate proxy step:
proxy: before internalStep
core: step
proxy: exit internalStep
interceptions=2
core processes=2
core steps=3
~~~

### 14.1. Phân tích coverage

- **core.process(2)** gọi target trực tiếp: method body chạy, nhưng interceptor không thấy lời gọi.
- **observed.process(3)** đi qua proxy: handler ghi **before process** rồi target chạy.
- Trong Core.process, lời gọi **internalStep()** vẫn chạy như một method Java bình thường và được tính vào **stepCalls**, nhưng không quay lại proxy để tạo thêm một sự kiện interceptor.
- **observed.internalStep()** gọi riêng qua proxy, nên có **before internalStep** và **exit internalStep**.
- **interceptions=2** nhưng **core steps=3**: đếm method body và đếm interception không giống nhau.

**Ý nghĩa so sánh:** một bytecode weaver instrument trực tiếp execution của internalStep có thể quan sát những lượt self-call này nếu đúng class/method được weave; proxy trong ví dụ không làm như vậy. Không được kết luận bytecode weaving luôn bao phủ mọi method: nó cũng phụ thuộc pointcut, scope và join point model.

### 14.2. Ví dụ không đo hiệu năng

System.out.println là I/O có chi phí lớn và không ổn định. AtomicInteger cũng có synchronization semantics riêng. Vì vậy không được đo tổng thời gian chạy chương trình này rồi tuyên bố overhead proxy bằng một tỷ lệ cố định.

### 14.3. Vì sao unwrap InvocationTargetException?

Khi Method.invoke gọi target và target ném ngoại lệ, Java reflection có thể bọc lỗi trong **InvocationTargetException**. Handler lấy **getCause()** để tiếp tục truyền lỗi gốc. Đây là một phần **chi phí đúng đắn** của interception: một cơ chế chỉ thêm tracing cũng có thể vô ý đổi exception nếu wrapper xử lý sai.

## 15. Tình huống 1: policy bắt buộc tại mọi đường thực thi một method

Giả sử một hệ thống có nhiều caller; một số caller giữ reference đến target gốc. Yêu cầu là “mọi execution của một method quan trọng phải được ghi nhận”.

**Phương án proxy:** có thể chưa đủ vì một số caller bypass. Có thể giải quyết bằng cách sửa kiến trúc để mọi caller chỉ được cấp proxy và không truy cập trực tiếp target, nhưng đó là một điều kiện cần bảo đảm bằng code review và integration test.

**Phương án execution weaving:** có thể phù hợp hơn nếu instrument được chính method thực thi, kể cả subclass override quan trọng và class loader liên quan. Tuy nhiên vẫn phải xác minh vùng weave, dynamic pointcut, lỗi sau refactor và việc advice có thể throw.

Đánh đổi: coverage mạnh hơn trong phạm vi method đã weave có thể đi kèm **toolchain, class loading và vận hành phức tạp hơn**. Không nên chọn weaving sâu nếu một boundary proxy được kiểm soát chặt đã đáp ứng đầy đủ yêu cầu.

## 16. Tình huống 2: thư viện chỉ có binary, không kiểm soát source

Đầu vào có thể là JAR của một thư viện.

- **Source-level weave:** không có source để transform theo pipeline thông thường.
- **Post-compile binary weave:** có thể áp dụng nếu engine hiểu class version, phạm vi pháp lý/kỹ thuật cho phép và output vẫn hợp lệ.
- **Load-time weave:** có thể instrument class khi runtime load, nhưng cần agent, loader, module và tính tương thích.
- **Proxy:** có thể bọc API công khai nếu caller sử dụng proxy, nhưng không quan sát mọi method/field nội bộ.

Câu hỏi quyết định không chỉ là “có binary không”, mà còn là **join point muốn quan sát nằm ở API hay sâu bên trong implementation**, và có quản lý được runtime/classloader không.

## 17. Tình huống 3: yêu cầu khởi động nhanh và deploy thường xuyên

Một hệ thống nhiều instance được tạo/hủy thường xuyên có thể nhạy cảm với startup:

- CTW/PCW đã trả phần lớn chi phí transform tại build/release.
- LTW có thể thêm chi phí khi class nạp/define, đáng chú ý ở cold-start.
- Proxy có thể trả chi phí tạo chain khi dựng object graph và thêm dispatch mỗi call.
- Advice I/O vẫn có thể chiếm phần lớn trong tất cả chiến lược.

Tuy nhiên không được kết luận “LTW luôn startup chậm hơn” nếu chưa đo: số class được instrument, agent, lazy loading, JIT, class sharing và tool implementation đều có ảnh hưởng.

Một quyết định có cơ sở cần ít nhất **cold-start p95, tổng thời gian boot, CPU/memory peak**, bên cạnh steady-state latency. Nếu build dài ra nhưng deploy nhanh hơn, team phải cân nhắc thời gian CI/CD và yêu cầu availability.

## 18. Tình huống 4: muốn bật/tắt hoặc đổi concern mà không build lại

Runtime interceptor chain thường thuận tiện cho cấu hình linh hoạt **nếu** kiến trúc có cách cấp lại proxy hoặc thay handler trong phạm vi hợp lệ. Nhưng không phải mọi proxy hiện tại tự cập nhật theo config mới.

LTW có thể nhận config mới cho các class **được define sau**, trong khi class đã define có thể cần restart hoặc retransform/redefine được hỗ trợ. CTW/PCW thường cần artifact mới khi aspect đã được nhúng vào binary.

Nếu yêu cầu đổi policy nhanh, hãy viết rõ:

- Những class/objects đang sống có cần cập nhật không?
- Có chấp nhận restart không?
- Thời gian rollout và rollback mong muốn là gì?
- Có cần consistency giữa các instance tại cùng thời điểm không?
- Làm sao biết chính xác version policy đang áp dụng cho một invocation?

Một giải pháp “dynamic” nhưng thiếu quan sát version runtime có thể khó kiểm soát hơn deploy một artifact bất biến.

## 19. Tình huống 5: nhiều class loader và extension/plugin

Trong JVM, class identity gắn với **binary name** và **defining class loader**. Một class tên giống nhau có thể được định nghĩa riêng ở hai loader khác nhau.

- Compile-time/post-compile cần biết **artifact nào** chứa class thực sự sẽ được runtime dùng.
- LTW phải có transformer áp dụng trên **loader** liên quan, có dependency và visibility phù hợp.
- Proxy phải được tạo cho **interface/Class có identity tương thích** với caller và target.
- Aspect helper cần hiện diện trong đúng loading context để không gây LinkageError hoặc class cast ngoài ý định.

Khi module/plugin sinh code và load động, phải kiểm tra đúng đường, không chỉ tên class trong log. “Class A đã được weave” không tự nghĩa tất cả class có cùng tên trong mọi loader đều đã được weave.

## 20. Rủi ro chồng nhiều cơ chế

Một ứng dụng có thể vừa có proxy tại service boundary vừa có một bytecode weaver instrument execution:

~~~text
Caller
   └─► Proxy trace
           └─► Woven target execution
                   └─► Business core
~~~

Kết quả có thể là **hai event** cho một invocation nghiệp vụ. Nếu proxy retry, woven execution có thể chạy nhiều lần trong khi proxy chỉ tạo một event ngoài retry.

Để tránh đo sai:

1. Chỉ định **owner** cho từng concern và biên đo.
2. Ghi **join point kind, signature, correlation ID** trong test.
3. Kiểm tra thứ tự advice giữa các tầng.
4. Phân biệt số lần target chạy với số lần interceptor được gọi.
5. Không dùng cùng nhãn metric “request completed” cho cả biên call và execution khi chúng có thể lồng nhau.
6. Đảm bảo quanh thao tác có side effect, retry phải an toàn/idempotent.

Kết hợp nhiều chiến lược không nhất thiết sai, nhưng làm tăng chi phí cấu hình, gỡ lỗi và hiểu semantics của chương trình.

## 21. Bảng so sánh mức độ phức tạp vận hành theo tác vụ

| Tác vụ vận hành | Compile-time | Post-compile | Load-time | Proxy |
|---|---|---|---|---|
| Thay aspect | Rebuild | Reweave + redeploy | Đổi config/agent, xem lifecycle class | Đổi chain/reference, xem lifecycle object |
| Đối chiếu đầu ra | Source/compiled artifact | Binary woven | Bytes/class runtime, diagnostics | Object graph/handler order |
| Debug không match | Compiler/pointcut diagnostics | Binary match report | Loader/transform logs | Routing và dynamic match |
| Xử lý version JVM mới | Compiler/plugin compatibility | Bytecode parser/verification | Agent/transformer compatibility | Proxy API/handler compatibility |
| Rollback | Bản build cũ | Woven artifact cũ | Config/agent/restart hợp lệ | Wiring/handler version cũ |
| Class mới sinh runtime | Ngoài build có thể bỏ sót | Ngoài release có thể bỏ sót | Có thể instrument khi define | Có thể bọc khi object được tạo |
| Chặn self-invocation | Phụ thuộc join point đã dệt | Phụ thuộc execution woven | Phụ thuộc execution woven | Thường bypass |
| Kiểm soát đường trực tiếp | Body được weave có thể vẫn chặn | Body woven có thể vẫn chặn | Body woven có thể vẫn chặn | Cần ngăn bypass |

Các cụm “có thể” trong bảng là điều kiện cố ý: **khả năng phụ thuộc công cụ và cấu hình**, không chỉ thời điểm.

## 22. Kiểm thử coverage theo ma trận thay vì một happy path

Tạo fixture:

~~~text
Interface Operation
       ▲
       │
BaseOperation
       ▲
       ├── InheritingOperation  (không override)
       └── OverridingOperation (override)

Caller A qua interface
Caller B qua target gốc
Target gọi method nội bộ
Một class từ loader khác nếu kiến trúc có
~~~

Các test cần hỏi:

| Tình huống | Điều bắt buộc kiểm tra |
|---|---|
| Caller qua proxy | Advice có chạy đúng một lần tại boundary? |
| Caller trực tiếp target | Có bypass proxy không, policy có chấp nhận không? |
| Target self-call | Có execution advice khi được weave, hay không có proxy event? |
| Override ở subclass | Execution thực nằm đâu, class subclass có được weave? |
| Method kế thừa | Thân khai báo ở superclass có được instrument? |
| Hai caller khác nhau | Call pointcut đã bao đủ những caller cần thiết? |
| Static/private/final | Join point model và interception hỗ trợ ra sao? |
| Target ném exception | Advice thứ tự/exception contract có đúng? |
| Before/after advice ném exception | Có vô ý chặn/che lỗi nghiệp vụ? |
| Retry/nested method | Số lần target và advice có chính xác? |
| Dynamic condition false | Không chạy advice ở ca bị loại? |
| Nhiều class loader | Đúng định nghĩa runtime được bao phủ? |
| Aspect/config đổi | Output cũ/mới có được áp dụng nhất quán? |

Những test “không được chạy” giúp phát hiện pointcut quá rộng; test “phải chạy” giúp phát hiện thiếu đường. Chỉ xem log ở một happy path không chứng minh coverage.

## 23. Quy trình ra quyết định có thể lặp lại

### Bước 1 — Viết policy

Ví dụ “mỗi execution của method nhận tiền trên mọi implementation phải qua policy” hoặc “chỉ đo số request qua API boundary”. Hai yêu cầu này không tương đương.

### Bước 2 — Liệt kê join points và đường gọi

Đánh dấu call/execution, caller, receiver, override, self-call, constructor/field/static nếu liên quan. Xác định lớp nào cần được instrument và caller nào có thể bypass.

### Bước 3 — Đặt trọng số cho mục tiêu

Xếp hạng **bắt buộc** và **mong muốn**:

- Coverage correctness.
- Startup latency.
- Steady-state overhead.
- Build time và thời gian release.
- Operability/debuggability.
- Policy update/rollback speed.
- Quyền truy cập source/binary và bảo mật.

Không nên cộng điểm cơ học khi một tiêu chí **bắt buộc** không được đáp ứng. Ví dụ bảo mật cần bao phủ mọi đường nhưng proxy không được quản lý toàn bộ reference: “proxy dễ vận hành” không bù được lỗ hổng coverage.

### Bước 4 — Chọn hai phương án có khả năng đáp ứng

Loại ngay giải pháp không có join point model hoặc không kiểm soát được đường gọi. Chỉ benchmark những phương án đủ yêu cầu chức năng.

### Bước 5 — Tạo PoC thực tế

Thử một method chính, một self-call, một subclass override, một đường lỗi và một đường bypass. Với LTW, thử class nạp muộn; với PCW, kiểm chứng đúng woven artifact; với proxy, xác định caller nhận proxy.

### Bước 6 — Đo chi phí cùng workload

Tách build/startup/per invocation và advice I/O. Dùng cùng JDK và môi trường, báo throughput, latency p95/p99, allocation, memory và khả năng ổn định.

### Bước 7 — Kiểm tra sự cố và rollback

Đưa ra một kịch bản “pointcut không match sau refactor”, một kịch bản “transformer lỗi khi startup”, hoặc “proxy wiring bị bỏ qua”, rồi đánh giá thời gian chẩn đoán và phục hồi.

### Bước 8 — Chốt cùng bằng chứng

Lưu decision record gồm yêu cầu, các phương án bị loại, rủi ro còn lại, test coverage, benchmark thực và phương án rollback. Không ghi con số hiệu năng nếu chưa đo.

## 24. Các sai lầm thường gặp

**Sai lầm 1 — “Compile-time luôn ít chi phí runtime hơn”.** Advice và dynamic checks vẫn chạy mỗi lần cần thiết; chỉ có một phần chi phí transform chuyển về build.

**Sai lầm 2 — “Load-time transformer chạy ở mọi lời gọi method”.** Nó có thể chạy khi class được define, còn method chạy nhiều lần sau đó.

**Sai lầm 3 — “Proxy luôn chậm hơn bytecode một tỷ lệ cố định”.** Hiệu năng phụ thuộc implementation, JIT, số interceptor, advice body và workload; cần benchmark.

**Sai lầm 4 — “Dệt ở compile đảm bảo bao phủ thư viện ngoài”.** Chỉ những nguồn/artifact được đưa vào pipeline và được engine hỗ trợ mới có cơ hội weave.

**Sai lầm 5 — “Đã có proxy thì self-invocation cũng bị chặn”.** Lời gọi nội bộ trên target có thể bỏ qua proxy.

**Sai lầm 6 — “LTW bao phủ mọi class sau khi agent đăng ký”.** Cần xem loader, include/exclude, module, pointcut, định nghĩa trước đó và khả năng tương thích.

**Sai lầm 7 — “Một log before nghĩa code gốc bị biến đổi”.** Runtime proxy/decorator cũng có thể tạo log mà target binary vẫn nguyên.

**Sai lầm 8 — “Có woven binary trên đĩa nghĩa JVM đang chạy đúng nó”.** Có thể classpath hoặc loader đưa binary khác vào runtime.

**Sai lầm 9 — “Advice đếm 2 nghĩa target xử lý hai request”.** Call/execution, retry, nested method và multiple aspects có thể sinh nhiều advice events hơn số nghiệp vụ.

**Sai lầm 10 — “Chi phí chỉ là CPU per invocation”.** Build time, cold-start, memory, artifact size, debugger và incident recovery đều có tác động vận hành.

**Sai lầm 11 — “Có thể đổi aspect ở runtime mà mọi class/instance lập tức cập nhật”.** Tùy pha composition, lifecycle và khả năng hỗ trợ cập nhật.

**Sai lầm 12 — “Pointcut rộng là chắc chắn hơn”.** Nó có thể chặn nhầm helper, gây recursion, log dữ liệu nhạy cảm hoặc thay hành vi ngoài ý định.

**Sai lầm 13 — “Final/private/static đều không thể áp dụng AOP”.** Đây là kết luận quá rộng; cần phân biệt subclass proxy, interface proxy và bytecode execution weaving.

**Sai lầm 14 — “Một công cụ mạnh về join point chắc chắn dễ vận hành hơn”.** Coverage nhiều hơn có thể đi cùng độ khó debugging, startup và compatibility cao hơn.

## 25. Tự kiểm tra và trả lời

**Câu 1.** Một class đã được weave lúc build nhưng method chưa chạy. Có advice invocation chưa?

**Đáp:** Chưa chỉ vì weaving đã hoàn tất. Advice chạy khi join point phù hợp thật sự xuất hiện.

**Câu 2.** Nếu một yêu cầu bảo mật phải áp dụng cho mọi execution của method nhưng caller có thể gọi trực tiếp target, proxy có đáp ứng mặc nhiên không?

**Đáp:** Không. Cần kiểm soát mọi đường reference hoặc dùng cơ chế khác có coverage đã được kiểm chứng.

**Câu 3.** Vì sao load-time weaving có thể làm startup nặng hơn nhưng không nhất thiết làm transform lặp lại trên mỗi invocation?

**Đáp:** Transformer làm việc khi class được định nghĩa; code đã instrument sau đó chạy như class được JVM định nghĩa.

**Câu 4.** Tại sao post-compile weaving cần quản trị provenance chặt?

**Đáp:** Phải chắc chắn runtime dùng đúng woven binary và có thể tái tạo/rollback artifact, không nhầm với bản gốc.

**Câu 5.** Nếu bytecode weaver chặn được execution của inner, lời gọi outer tự gọi inner có thể được advice quan sát không?

**Đáp:** Có nếu class/method execution ấy thực sự được weave và pointcut khớp, khác với self-call có thể bypass proxy.

**Câu 6.** Có nên kết luận proxy chậm hơn khi demo print nhiều dòng log hơn?

**Đáp:** Không. Logging I/O làm méo phép đo; cần benchmark kiểm soát tương đương advice và warmup/JIT.

**Câu 7.** Một class có tên giống nhau được loader A và B định nghĩa. Weave ở A bảo đảm B cũng weave không?

**Đáp:** Không. Runtime class identity và transformer scope cần xét từng defining loader.

**Câu 8.** Một pointcut có điều kiện target subtype. Compile-time weaving có thể tự biết mọi object runtime sẽ thỏa không?

**Đáp:** Không nhất thiết. Engine có thể phải chèn runtime type check ở các join point ứng viên.

**Câu 9.** Khi vừa có proxy, vừa có execution advice trong class target, một request có thể tạo hai trace không?

**Đáp:** Có; cần phân biệt join point và ownership của metric để tránh đếm đôi.

**Câu 10.** Hai phương án có chức năng giống nhau, một giảm runtime overhead nhưng làm build chậm hơn. Chọn như thế nào?

**Đáp:** Dựa vào tần suất build, số lượt invocation, startup, SLO, chi phí CI/CD, benchmark thực và độ phức tạp rollback. Không có lựa chọn phổ quát.

**Câu 11.** Chỉ thấy tool báo “weaving complete” có đủ chứng minh coverage không?

**Đáp:** Không. Phải kiểm chứng artifact/class/route đã sử dụng và test cả join point phải khớp/không được khớp ở runtime.

**Câu 12.** Khi nào runtime proxy có lợi thế vận hành?

**Đáp:** Khi boundary rõ, reference được kiểm soát, không cần chặn sâu trong implementation và chain dễ cấu hình/quan sát theo vòng đời ứng dụng.

## 26. Sơ đồ ra quyết định ngắn

~~~text
          Policy cần quan sát những join point nào?
                            │
                 ┌──────────┴──────────┐
                 ▼                     ▼
       API boundary có thể        Phải chặn execution
       kiểm soát reference?       bên trong / call sites?
                 │                     │
                 ▼                     ▼
          Proxy/decorator         Source/IR/bytecode weave
          là ứng viên             là ứng viên
                 │                     │
                 └──────────┬──────────┘
                            ▼
                 Có source/binary và
                 kiểm soát build không?
                      ┌─────┴──────┐
                      ▼            ▼
                     Có           Không/khó
                      │            │
                      ▼            ▼
              Compile/post-     Xem load-time
              compile weave     nếu engine hỗ trợ
                      │            │
                      └─────┬──────┘
                            ▼
                 Test coverage bắt buộc
                 và trường hợp không match
                            │
                            ▼
                 Đo build/startup/runtime
                 + chi phí vận hành/rollback
                            │
                            ▼
                  Chọn theo yêu cầu thật
~~~

**Kết luận:** không có một thời điểm dệt AOP nào đồng thời tối ưu mọi trục. Compile-time/post-compile thường giúp đóng gói kết quả vào artifact trước deploy; load-time tạo khả năng instrument khi class được nạp nhưng làm tăng độ khó quản lý agent/classloader; proxy thuận tiện cho boundary rõ nhưng phụ thuộc đường gọi. Việc chọn đúng đòi hỏi **ma trận coverage có thể kiểm chứng**, **phân bổ chi phí theo pha**, **quy trình debugging/rollback** và benchmark trên hệ thống thực.

## Tài liệu tham khảo

- [Java Virtual Machine Specification, Java SE 17 – The Class File Format](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html): cấu trúc class file và ràng buộc bytecode verification.
- [Java Virtual Machine Specification, Java SE 17 – Loading, Linking, and Initializing](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html): vòng đời class, class loading và initialization.
- [Java SE 17 – Instrumentation API](https://docs.oracle.com/en/java/javase/17/docs/api/java.instrument/java/lang/instrument/Instrumentation.html): transformer, retransform/redefine cùng những giới hạn runtime.
- [Java SE 17 – InvocationHandler](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationHandler.html): JDK dynamic proxy dùng trong ví dụ Java runnable.
- [AspectJ Programming Guide](https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html): mô hình join point, pointcut, advice và các chiến lược weaving trong một ngôn ngữ AOP cụ thể.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="weaving-lifecycle-009">Chọn thời điểm dệt theo yêu cầu hệ thống</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn thời điểm dệt theo yêu cầu hệ thống

## 1. Bài toán: dệt vào lúc nào, để đáp ứng yêu cầu gì?

Trong **Aspect-Oriented Programming (AOP)**, **weaving** là quá trình kết hợp phần hành vi của aspect với chương trình tại những **join point** được mô hình AOP hỗ trợ và được **pointcut** lựa chọn. Việc kết hợp có thể được thực hiện khi xây dựng mã, sau khi biên dịch nhưng trước khi triển khai, lúc class được nạp, hoặc bằng một cơ chế can thiệp lúc chương trình đang chạy. Mỗi thời điểm cho ta quyền kiểm soát, phạm vi, chi phí và rủi ro khác nhau.

Khi thiết kế một hệ thống cần AOP, câu hỏi đúng không phải chỉ là “compile-time weaving tốt hơn hay runtime weaving tốt hơn?”, mà là:

> **Những loại điểm nào bắt buộc phải can thiệp, công cụ đang dùng có thực hiện được ở thời điểm nào, ai kiểm soát artifact/loader/runtime, yêu cầu hiệu năng và vận hành ra sao, và bằng chứng nào chứng minh hệ thống thực sự đã được dệt đúng?**

Một cơ chế phù hợp cho một ứng dụng Java được build và triển khai nguyên khối chưa chắc phù hợp với ứng dụng plugin có nhiều classloader, môi trường khởi tạo linh hoạt, native executable, hoặc nơi không thể đụng vào bytecode của dependencies.

### Bạn sẽ học được gì?

1. Phân biệt **source/compile-time weaving**, **post-compile/binary weaving**, **load-time weaving**, **runtime instrumentation/retransformation** và **proxy/decorator interception**.
2. Hiểu khác biệt giữa **thời điểm kết hợp hành vi**, **thời điểm kích hoạt advice** và **thời điểm runtime xảy ra join point**.
3. Đánh giá trade-off: kiểm soát artifact, khả năng thay đổi chính sách, classloader/module, startup, build complexity, runtime overhead, rollback và bảo mật.
4. Chọn một chiến lược bằng **ma trận yêu cầu**, không dựa trên lời quảng cáo hay tên gọi của kỹ thuật.
5. Hiểu các giới hạn: method call khác method execution; proxy không tự bao phủ self-invocation; không phải mọi JVM runtime transformer có thể sửa tùy ý class đã nạp.
6. Thiết kế quy trình QA chứng minh **phạm vi dệt**, **pointcut matched**, **advice runtime**, cùng ca negative/bypass.
7. Chạy ví dụ Java 17 so sánh **decorator lắp ghép tường minh** với **dynamic proxy tạo khi chạy** mà không nhầm chúng với bytecode weaving.

Bài tập trung vào AOP **nói chung**, gồm cả nguyên tắc cơ bản và các cân nhắc kiến trúc nâng cao. Không có một cú pháp cấu hình weaving chung cho mọi công cụ/ngôn ngữ.

## 2. Bốn khái niệm cần phân biệt trước khi chọn thời điểm

### 2.1. Join point là sự kiện hoặc điểm được mô hình hóa, không phải chỉ một dòng mã

Tùy mô hình AOP, join point có thể là method execution, method call, constructor execution, field access hoặc những sự kiện khác. Ví dụ một call site trong vòng lặp có thể gây ra nhiều lượt method execution.

Phải hỏi công cụ hỗ trợ **loại join point** cần thiết trước khi hỏi công cụ thực hiện weaving vào lúc nào. Nếu kỹ thuật chỉ có khả năng chặn interface invocation, thay đổi thời điểm tạo proxy không tự khiến nó chặn được mọi field access.

### 2.2. Pointcut là quy tắc chọn, weaving là cách kết hợp

Một pointcut có thể mô tả “mọi execution của method được đánh dấu `@Observed`”. Nhưng có marker và pointcut khớp không đủ: class phải nằm trong phạm vi công cụ có thể xử lý, aspect được kích hoạt, artifact có hook, và đường runtime thực sự đến điểm đó.

```text
Class/artifact trong phạm vi công cụ có thể dệt?
   |
   +-- không --> không có hook từ cơ chế đó
   |
   +-- có ----> join-point kind có được hỗ trợ?
                   |
                   +-- không --> không chọn được loại điểm đó
                   |
                   +-- có ----> pointcut và điều kiện có khớp?
                                   |
                                   +-- không --> không có advice tương ứng
                                   |
                                   +-- có ----> runtime có occurrence?
                                                   |
                                                   +-- không --> advice không chạy
                                                   |
                                                   +-- có --> advice có thể kích hoạt
```

Không suy ra advice đã chạy chỉ vì một công cụ liệt kê class đã biến đổi; cũng không suy ra weaving thất bại chỉ vì chương trình test chưa từng đi qua điểm đã chọn.

### 2.3. “Dệt lúc runtime” có nhiều nghĩa khác nhau

Cụm “runtime AOP” thường bị dùng rộng, có thể chỉ:

- **Proxy/wrapper** được tạo khi chạy và nhận lời gọi qua proxy.
- **Load-time weaving** khi class vừa được định nghĩa bởi loader.
- **Instrumentation/retransformation** với class đã load, nếu runtime và công cụ hỗ trợ.
- **Các hook do compiler chèn từ trước** nhưng được bật/tắt bằng điều kiện runtime.
- **Cơ chế dynamic dispatch** hoặc interceptor trong framework.

Đây không phải cùng cơ chế. Chúng khác nhau ở **đơn vị bị tác động**, **điểm có thể chặn**, **khả năng tháo hook**, **quyền cấu hình** và **chi phí**.

### 2.4. Phân biệt thời điểm “cài hook” với thời điểm “hook được gọi”

Một method được weave ở build time **không có nghĩa advice chạy lúc build**. Build chỉ kết hợp code hoặc tạo artifact chứa hook; khi chương trình chạy đến join point, advice mới được thực thi.

Ngược lại, runtime proxy được tạo khi chương trình chạy nhưng handler cũng chỉ chạy **khi caller gọi qua proxy**. Không phải việc tạo proxy đã thực hiện advice nghiệp vụ.

## 3. Bức tranh các chiến lược phổ biến

| Chiến lược | Khi kết hợp hành vi | Đơn vị bị xử lý | Cần lưu ý |
| --- | --- | --- | --- |
| **Source-time / compile-time weaving** | Trong pipeline biên dịch hoặc biến đổi source/IR. | Source, AST/IR hoặc code đầu ra. | Cần tích hợp build/compiler; artifact thường được kiểm chứng trước chạy. |
| **Post-compile / binary weaving** | Sau compile, trước package/deploy hoặc trước chạy. | Bytecode/binary đã biên dịch. | Có thể biến đổi artifact từ bước khác nhưng phải kiểm soát binary và tương thích. |
| **Load-time weaving** | Khi class/module được runtime loader đưa vào sử dụng. | Định nghĩa class sắp load. | Phụ thuộc loader, thứ tự agent, quyền runtime và thời điểm đăng ký transformer. |
| **Runtime retransformation/redefinition** | Sau khi class đã load, nếu API/runtime cho phép. | Một số class đã có trong process. | Có ràng buộc về loại thay đổi, quyền attach/agent, trạng thái đang chạy và rollback. |
| **Runtime proxy/decorator** | Khi object hoặc wrapper được tạo/lắp ghép. | Object reference và những lời gọi đi qua wrapper. | Không đòi thay đổi bytecode của target, nhưng tập join point và đường gọi thường hẹp. |

**Điểm quan trọng:** Đây là phân loại kiến trúc, không phải xếp hạng chất lượng. Một chương trình có thể kết hợp nhiều kỹ thuật. Phần “compile-time” của một hệ thống có thể phát sinh wrapper thay vì chèn mọi hook vào method body; phải xem điều gì thực sự được tạo và giới hạn join-point model.

## 4. Dệt ở thời điểm source/compile-time

### 4.1. Cơ chế khái quát

Một công cụ có thể phân tích source, AST, intermediate representation hoặc cấu trúc compiler, tìm những điểm phù hợp, rồi sinh mã đã kết hợp advice.

```text
Source + mô tả aspect/pointcut
          |
          v
Compiler / source transformer
          |
          v
Artifact đã kết hợp logic can thiệp
          |
          v
Deploy -> runtime thực thi hook
```

Đối với phần lớn pipeline được kiểm soát chặt chẽ, kết quả weaving có thể được xem là **một phần của sản phẩm build**. Có thể kiểm chứng trong CI, ký artifact, chạy test và triển khai chính xác binary đã được đánh giá.

### 4.2. Ưu điểm thường có

- **Có cơ hội phát hiện lỗi sớm:** công cụ có thể báo một số lỗi matching, type/visibility hoặc transform trước triển khai.
- **Tái lập artifact:** cùng source, dependency, compiler, flags và rule có thể tạo ra đầu ra được version hóa, kiểm thử, ký và rollout.
- **Không bắt buộc chờ runtime classloading để chèn mọi hook:** một phần việc phân tích/biến đổi được hoàn tất trước khởi động.
- **Dễ đối chiếu phạm vi:** có thể kiểm tra mã sau biến đổi, danh sách join-point shadow/hook nếu công cụ hỗ trợ.
- **Phù hợp chính sách không cho phép runtime attach agent:** vì logic cần thiết có thể nằm sẵn trong artifact.

Không nên hứa “compile-time weaving luôn cho runtime overhead bằng 0”. Khi join point xảy ra, advice vẫn chạy và làm việc. Một số cơ chế có thêm kiểm tra động, cấp phát hoặc I/O; overhead thực tế phải đo.

### 4.3. Đánh đổi và rủi ro

- Cần quyền điều chỉnh **build pipeline**; khó hơn nếu chỉ có một binary đóng, không có source hoặc không được phép thay đổi bước build.
- Thời gian build, cache, incremental build và khả năng tái lập có thể bị ảnh hưởng.
- Nếu thay đổi aspect/pointcut thường xuyên, có thể cần **rebuild + redeploy**.
- Source/generated code do compiler khác sinh ra có thể không nằm trong transformer đã chọn.
- Nhiều compiler plugin có thể xung đột về thứ tự hoặc cấu trúc AST/IR.
- Native image/AOT và optimizer có thể tạo yêu cầu riêng; không tự cho rằng mọi công cụ source weaving tương thích.

### 4.4. Khi nên cân nhắc

Phù hợp khi hệ thống:

- Có quy trình build/release tập trung và có thể phát hành artifact đã qua QA.
- Muốn kiểm soát chặt thay đổi mã và không muốn agent tùy ý biến đổi class trong production.
- Không cần bật/tắt tập hook bằng thay đổi bytecode động ở runtime.
- Cần độ phủ rộng ở các loại join point mà **công cụ compile-time đang xét thực sự hỗ trợ**.

**Phản ví dụ:** một nhóm vận hành chỉ có quyền cấu hình production, không có quyền build lại artifact. Dù compile-time weaving có nhiều ưu điểm, nó có thể không đáp ứng yêu cầu triển khai thực tế.

## 5. Dệt binary/post-compile trước triển khai

### 5.1. Vị trí trong pipeline

```text
Source --compile--> bytecode gốc
                        |
                        v
                  binary weaver
                        |
                        v
                bytecode đã dệt
                        |
                        v
                 package/deploy
```

Lúc này hệ thống đã có binary nhưng chưa chạy. Một công cụ có thể sửa class file, assembly hoặc artifact phù hợp theo điều kiện được hỗ trợ.

### 5.2. Khi mô hình này có lợi

- Cần tách trách nhiệm giữa nhóm compile code nghiệp vụ và bước instrumentation.
- Có nhiều ngôn ngữ nguồn tạo cùng định dạng trung gian/bytecode mà weaver tương thích.
- Muốn inspect và kiểm chứng **artifact cuối** trước khi runtime.
- Muốn tạo các biến thể artifact được dệt và không được dệt phục vụ rollout hoặc thử nghiệm hiệu năng.

### 5.3. Điều kiện quan trọng

- Có quyền **biến đổi và phân phối** artifact liên quan; phải xét licensing, chữ ký binary và chính sách chuỗi cung ứng phần mềm.
- Phải có cách phân biệt artifact **chưa dệt / đã dệt** để tránh weave lặp gây hook trùng hoặc binary lỗi.
- Nếu một thư viện đã được ký, bước biến đổi có thể làm chữ ký cũ không còn hợp lệ; quy trình packaging/signing phải tính tới.
- Mapping source line và stack trace sau weaving có thể thay đổi; cần kiểm thử debug/diagnostic.
- Kết quả dùng đúng compiler/weaver/runtime tương thích, không chỉ “classfile hợp lệ ở một máy”.

### 5.4. Post-compile không đồng nghĩa load-time

Hai mô hình có thể cùng biến đổi bytecode, nhưng **thời điểm và trạng thái class khác nhau**: post-compile biến đổi file trước khi chạy; load-time can thiệp lúc class sắp được định nghĩa bởi loader. Điều này tác động đến kiểm thử, khả năng đảo chiều, ownership và độ phủ đối với những class được tạo/nạp riêng.

## 6. Load-time weaving: kết hợp khi class được nạp

### 6.1. Cơ chế tổng quát

Một class transformer/classloader hoặc hook tương tự xử lý class trước khi nó được định nghĩa trong runtime.

```text
Runtime cần load class
         |
         v
Classloader / transformer pipeline
         |
         +-- class ngoài include --> không biến đổi
         |
         +-- class hợp lệ -------> xét pointcut / weave
                                       |
                                       v
                              Runtime định nghĩa class
                                       |
                                       v
                              Các invocation tương lai
```

Một class đã load trước khi công cụ được cài có thể **không được xử lý bởi load-time hook đó**, trừ khi có cơ chế riêng dành cho class đã load. Vì vậy thời điểm **attach agent/đăng ký transformer** và việc runtime có nhiều loader rất quan trọng.

### 6.2. Lợi ích

- Có thể không cần phân phối binary đã biến đổi trước nếu runtime nhận transform ở lúc load.
- Có thể áp dụng scope dựa trên classloader/module/metadata runtime mà build pipeline không biết hết.
- Có thể phù hợp ứng dụng có **module/plugin được nạp động** nếu weaver được cấu hình và có quyền quan sát đúng loader.
- Dễ điều chỉnh phần nào phạm vi cho các class **chưa load**, theo cách công cụ hỗ trợ.

### 6.3. Đánh đổi

- **Startup/classloading overhead:** transformer có thể tốn CPU, allocation, I/O hoặc làm tăng độ trễ load class.
- **Khả năng chẩn đoán:** phải phân biệt “class loader đã thử xử lý”, “class thực sự được biến đổi” và “pointcut có hook”.
- **Classloader isolation:** một loader ngoài phạm vi có thể bỏ qua instrument.
- **Thứ tự agent/transformer:** nhiều công cụ có thể cùng động vào bytecode.
- **Quyền runtime:** môi trường có thể cấm attach/transformer hoặc module access.
- **Rollback:** thay config có thể không xóa hook khỏi class đã nạp.

### 6.4. Không mặc định “load-time là dynamic hot-swap”

Một class thường được định nghĩa một lần trong một classloader; “load-time weaving” không tự có nghĩa có thể thay đổi mọi method đang chạy bất cứ lúc nào. Cần phân biệt **class chưa load sẽ dùng policy mới** với **class đã load đã có hình thái runtime riêng**.

### 6.5. Khi phù hợp

Xem xét khi:

- Không muốn hoặc không thể build một artifact weaving trước.
- Có quyền khởi tạo/configure loader hoặc JVM agent **trước khi các class mục tiêu được load**.
- Cần instrumentation cho code nạp động theo quy tắc đã biết.
- Chấp nhận chi phí startup và có đủ test trên cấu trúc loader/module thực.

## 7. Runtime instrumentation và class retransformation

### 7.1. Tại sao đây là một lớp khác load-time?

Có công cụ có thể đính vào một process đang chạy và yêu cầu runtime biến đổi hoặc định nghĩa lại **một số class đã load**. Trên JVM, các API instrumentation có khái niệm **retransform/redefine**; khả năng thật sự phải được runtime và agent công bố.

Không nên suy rằng có thể thêm/bớt mọi loại field, đổi class hierarchy, thay signature hoặc sửa mọi bytecode tùy ý trên class đã chạy. Có những giới hạn về **kiểu thay đổi**, class modifiability, quyền truy cập và trạng thái class.

### 7.2. Khi cần thiết

- Chẩn đoán một hệ thống đang chạy mà việc redeploy tốn kém, nếu chính sách cho phép attach/instrument.
- Bật một loại profiling/trace tạm thời để tìm bottleneck.
- Cần thay đổi phạm vi đo ở runtime mà không muốn rebuild toàn bộ artifact, trong phạm vi engine hỗ trợ.

### 7.3. Rủi ro vận hành

- Cần **quyền attach agent** và quy trình phê duyệt bảo mật.
- Có thể cần dừng ngắn hoặc đồng bộ hóa tại safepoint khi retransform; chi phí phụ thuộc runtime.
- Hai agent cùng instrument một class có thể tạo xung đột.
- Một lần biến đổi sai có thể ảnh hưởng process đang phục vụ traffic.
- Việc “tắt agent” không bảo đảm bytecode đã bị biến đổi được khôi phục đúng, trừ khi công cụ có cơ chế undo/retransform đáng tin.
- Thay đổi class đang hoạt động có thể làm diagnostic và phiên bản hook khó truy vết.

Vì vậy runtime modification cần **canary, rollback, observability, audit trail và thử nghiệm cùng runtime phiên bản thật**.

### 7.4. Một mục tiêu “bật/tắt logging” không nhất thiết cần retransform

Nếu hook đã có sẵn, bật/tắt việc ghi log bằng config runtime có thể đủ mà không sửa class. Nhưng một hook đã cài vẫn có thể có overhead tối thiểu và điều kiện pointcut/runtime guard; phải đo.

Nên phân biệt:

- **Cài hoặc gỡ điểm can thiệp** (structural change).
- **Bật/tắt hành vi của advice đã cài** (behavior/config change).
- **Lọc những event được ghi/hiển thị** (observability pipeline).

Ba việc này có tác động khác nhau đến correctness và performance.

## 8. Runtime proxy/decorator: không nhất thiết dệt bytecode

### 8.1. Dynamic proxy

Một proxy theo interface có thể nhận lời gọi từ caller, chạy handler rồi gọi target. Việc tạo proxy có thể xảy ra lúc runtime, nhưng target class **không tự bị biến đổi**.

```text
Caller --> Proxy --> Handler/advice --> Target
Caller -------------------------------> Target   (bypass)
```

Ưu điểm:
- Có thể lắp ghép/tắt tại ranh giới tạo object.
- Không nhất thiết cần thay đổi source/bytecode target.
- Dễ kiểm thử từng wrapper và dependency injection theo hợp đồng.

Giới hạn:
- Caller có thể giữ target gốc và bỏ qua proxy.
- Self-invocation trên `this` có thể không đi qua proxy.
- JDK dynamic proxy theo interface không tự chặn constructor/field access.
- Đối tượng, kiểu, identity và reflection của proxy có thể khác target.

### 8.2. Explicit decorator

Một decorator viết bằng Java thuần là wrapper triển khai cùng interface và gọi target. Nó có thể được **viết lúc development nhưng lắp ghép lúc build startup hoặc runtime**; không được gọi đó là “compile-time bytecode weaving” chỉ vì class decorator đã được compile.

Decorator có lợi khi cần nhìn thấy luồng rõ ràng và không cần join-point model phức tạp. Proxy có thể phù hợp khi nhiều interface methods cần handler thống nhất. Cả hai có thể dùng để minh họa sự lựa chọn **interception mechanism** nhưng không thay thế thảo luận về weaving thật.

### 8.3. Khi nào proxy đáp ứng yêu cầu?

Nếu hệ thống chỉ cần chặn **lời gọi công khai qua một interface boundary đã kiểm soát**, proxy/decorator có thể là lựa chọn đơn giản.

Nếu cần can thiệp **field access, constructors, private methods** hoặc những lời gọi nội bộ không đi qua boundary, cần công cụ có join-point model và cơ chế phù hợp; không thể biến proxy thành công cụ toàn năng bằng một pattern include rộng.

## 9. Ma trận yêu cầu để chọn thời điểm

Bảng dưới là **khung quyết định định tính**, không phải số liệu benchmark hoặc cam kết về từng sản phẩm.

| Yêu cầu | Hướng thường đáng xem xét | Phải xác minh |
| --- | --- | --- |
| Artifact phải được ký, review và tái lập trước deploy. | Compile-time hoặc post-compile weave có kiểm soát. | Artifact cuối đã được weave và ký đúng thứ tự. |
| Không cho phép runtime agent/attach. | Build/binary weaving hoặc wrapper đã đóng gói. | Runtime không cần quyền transform. |
| Chỉ có binary và không kiểm soát pipeline build. | Load-time/runtime instrumentation nếu được phép; hoặc proxy ở boundary khả dụng. | Licensing, agent privileges, classloader và tương thích. |
| Nhiều plugin/classloader được nạp sau startup. | Load-time weaving theo loader; wrapper ở plugin boundary. | Transformer thấy đúng loader và class. |
| Cần thay đổi instrumentation trong process đang chạy. | Runtime config trên hook sẵn có; retransformation khi cần và khả dụng. | Giới hạn modifiable class, rollback, phí tổn. |
| Startup latency rất nhạy. | Dệt trước chạy hoặc proxy nhẹ đã đo kiểm. | Cost chuyển từ startup sang build/run thế nào? |
| Cần can thiệp private/constructor/field. | Engine có mô hình join point rộng với compile/load-time weaving phù hợp. | Cú pháp, visibility, class constraints. |
| Chỉ cần ghi log ở interface service boundary. | Decorator/dynamic proxy hoặc weaving có scope hẹp. | Mọi caller có thật qua wrapper không? |
| Chính sách bảo mật không được bypass. | Bảo vệ ở trusted boundary/domain invariant, weaving chỉ hỗ trợ. | Không có đường truy cập bỏ qua bất biến. |
| Muốn overhead có thể dự đoán và benchmark. | Mô hình nào cũng có thể phù hợp nếu đo đầy đủ. | Runtime occurrences, dynamic guards, logging cost. |
| Cần canary/rollback trong production. | Artifact-based rollout hoặc instrumentation có cơ chế undo đã kiểm chứng. | Không nhầm “tắt config” với unweave class. |
| Có native/AOT/deployment đóng. | Cơ chế được toolchain AOT hỗ trợ; thường cần tích hợp build sớm. | Reflection/proxy/agent availability và closed-world constraints. |

Chọn thời điểm là **bài toán nhiều tiêu chí**. “Nhạy hiệu năng” không tự chỉ ra compile-time weaving; nếu advice ghi log mạng ở một hot path, bất cứ thời điểm weave nào cũng có thể gây chi phí lớn.

## 10. Quy trình quyết định tám bước

### Bước 1 — Nêu concern và hợp đồng hành vi

Muốn đo thời gian, audit, retry, quản lý transaction hay kiểm soát quyền? Advice chỉ quan sát hay có thể đổi result/exception? Có bắt buộc không thể bị bypass?

Nếu hành vi ảnh hưởng an toàn dữ liệu hoặc quyền truy cập, phải bảo vệ bằng **domain/trusted boundary** phù hợp trước; weaving có thể hỗ trợ nhưng không phải chứng cứ duy nhất rằng mọi đường đã được chặn.

### Bước 2 — Chọn join-point model cần thiết

Ghi rõ:
- Method call hay execution?
- Constructor, field get/set hoặc event đặc thù?
- Có cần biết caller, receiver runtime, arguments hay annotation?
- Có cần bao quanh async **task completion**, chứ không chỉ method return?

Nếu chỉ có proxy nhưng yêu cầu private field writes, kế hoạch không khả thi; cần thay đổi công cụ hoặc ranh giới kiến trúc.

### Bước 3 — Xác định ai sở hữu code/artifact

Hệ thống có:
- Source code và quyền build?
- Binary third-party nhưng không có source?
- Module được tải từ bên ngoài sau deploy?
- Artifact được ký hoặc yêu cầu supply-chain attestation?
- Quy định không sửa binary của dependency?

Một công cụ weaving có thể về mặt kỹ thuật làm được nhưng bị chính sách phân phối hoặc quyền truy cập cấm.

### Bước 4 — Xác định thời điểm được phép thay đổi

Nếu policy phải cố định theo release → dệt trước runtime thuận lợi cho QA. Nếu cần bật instrumentation trên một process đang chạy → xét runtime configuration/retransformation. Nếu class nạp động và agent có sẵn từ startup → load-time có thể phù hợp. Nếu chỉ cần đổi một wrapper ở module boundary → proxy/decorator có thể đủ.

### Bước 5 — Xác định startup, throughput và footprint

Đo các chi phí riêng:
- **Build time** thêm vì weaving.
- **Startup/classload time** thêm vì transformer.
- **Memory/metadata footprint** của hook, proxy, cache.
- **Steady-state overhead** khi join point xảy ra.
- **Cost của advice**: log I/O, serialize, metrics cardinality, lock.
- **Chi phí đổi cấu hình/rollout**.

Không gộp tất cả thành một con số “AOP overhead”.

### Bước 6 — Chọn chính sách include/exclude

Ưu tiên danh sách được phép có chủ đích, xác định class/module/package/loader được xét và loại trừ code instrumentation tránh recursion. Cần phân biệt **weaving eligibility** với **pointcut exclusion**: exclude cả class khỏi weaving có thể khiến nhiều aspect mất hook; exclude một pointcut có tác động hẹp hơn.

Ghi rõ precedence nếu một class khớp cả include và exclude. Không giả định deny luôn thắng ở mọi công cụ; phải xác minh hợp đồng.

### Bước 7 — Xây thử nghiệm đúng cùng runtime

Không chỉ test biểu thức pointcut. Phải test:
- Artifact/build output đã thực sự weave?
- Classloader nào được transformer nhìn thấy?
- Pointcut khớp join point nào?
- Positive/negative runtime occurrences có advice đúng?
- Trường hợp bypass proxy, self-call, async completion?
- Errors/advice order/context/thread safety?
- Hiệu năng dưới tải thực?

### Bước 8 — Có kế hoạch vận hành và quay lại

Với build weaving: rollback artifact/version. Với load-time: quay config và restart/rollout nếu class đã load không thể unweave. Với runtime retransformation: xác nhận undo thật và hành vi khi agent bị lỗi. Với proxy: thay đổi wiring/reference theo policy, bảo đảm caller không giữ target gốc trái ý định.

## 11. Ba tình huống chọn chiến lược chi tiết

### Tình huống A — Ứng dụng xử lý giao dịch có release nghiêm ngặt

**Yêu cầu:**
- Code ứng dụng do team sở hữu.
- Mọi artifact cần CI/test/scan/sign trước production.
- Không cho phép attach agent động.
- Cần log các method execution tại một số tầng ổn định.

**Hướng quyết định:** cân nhắc compile-time hoặc post-compile weaving nếu engine hỗ trợ join point, hoặc một interceptor/decorator cố định tại boundary khi đủ. Điểm quan trọng là **artifact cuối cùng chứa đúng hook và được QA trước khi ký/phân phối**.

**Không suy ra:** build weaving tự đảm bảo mọi policy bảo mật đã bao phủ; vẫn phải kiểm thử bypass, lỗi và transaction semantics.

### Tình huống B — Nền tảng plugin nạp class động

**Yêu cầu:**
- Plugin được nạp sau startup và có classloader riêng.
- Không thể rebuild tất cả plugin khi host nâng cấp.
- Cần đo latency ở những class/plugin đã được phép.

**Hướng quyết định:** load-time weaving có thể phù hợp nếu transformer được đăng ký trước plugin load và có quyền quan sát loader/plugin. Một decorator quanh **plugin API boundary** cũng có thể tốt hơn nếu chỉ cần đo lời gọi từ host.

**Rủi ro cần kiểm tra:** tên class pattern đúng nhưng plugin dùng loader không qua transformer; version mismatch; unload plugin mà cache metadata/agent giữ classloader; plugin gọi nội bộ không qua API boundary.

### Tình huống C — Điều tra sự cố production ngắn hạn

**Yêu cầu:**
- Hệ thống đã chạy, khó tái triển khai tức thì.
- Cần thu thêm trace cho một số class trong khoảng thời gian giới hạn.
- Hệ thống có chính sách cho phép công cụ chẩn đoán được cấp quyền.

**Hướng quyết định:** dùng hook đã cài sẵn và bật runtime config nếu có. Nếu chưa có hook, runtime instrumentation/retransformation có thể là lựa chọn **chỉ khi** runtime/agent hỗ trợ và có canary + rollback.

**Rủi ro:** làm biến đổi class nhạy cảm khi đang phục vụ request; overhead làm sự cố tệ hơn; log lộ dữ liệu; không có cách unweave ổn định. Nên có TTL cho cấu hình chẩn đoán, sampling và kiểm soát truy cập.

## 12. Java 17 runnable: ranh giới khi lắp decorator và tạo dynamic proxy

Java chuẩn không có một API “dệt mọi join point” tổng quát. Để thấy khác biệt giữa **lắp một wrapper tường minh** và **tạo một proxy khi chạy**, ta dùng interface, target, decorator và JDK dynamic proxy.

**Quan trọng:** Chương trình dưới **không triển khai compile-time weaving, post-compile binary weaving, load-time transformer hay retransformation**. Nó chỉ minh họa hai mô hình interception dựa trên **object reference**. Dựa vào demo này không được kết luận bất kỳ bytecode nào đã bị dệt.

Tạo file **`WeavingTimingDecisionDemo.java`**:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class WeavingTimingDecisionDemo {
    interface Work {
        String run(String value);
    }

    static final class RealWork implements Work {
        @Override
        public String run(String value) {
            System.out.println("TARGET " + value);
            if ("FAIL".equals(value)) {
                throw new IllegalStateException("operation failed");
            }
            return "OK:" + value;
        }
    }

    static Work decorate(Work target) {
        return new Work() {
            @Override
            public String run(String value) {
                System.out.println("DECORATOR before");
                try {
                    String result = target.run(value);
                    System.out.println("DECORATOR returning");
                    return result;
                } catch (RuntimeException ex) {
                    System.out.println("DECORATOR throwing "
                            + ex.getClass().getSimpleName());
                    throw ex;
                }
            }
        };
    }

    static Work proxy(Work target) {
        return (Work) Proxy.newProxyInstance(
                Work.class.getClassLoader(),
                new Class<?>[] { Work.class },
                (proxyInstance, method, args) -> {
                    System.out.println("PROXY before");
                    try {
                        Object result = method.invoke(target, args);
                        System.out.println("PROXY returning");
                        return result;
                    } catch (InvocationTargetException wrapped) {
                        Throwable original = wrapped.getCause();
                        System.out.println("PROXY throwing "
                                + original.getClass().getSimpleName());
                        throw original;
                    }
                }
        );
    }

    public static void main(String[] args) {
        Work target = new RealWork();

        System.out.println("RESULT " + target.run("A"));

        Work decorator = decorate(target);
        System.out.println("RESULT " + decorator.run("B"));

        Work runtimeProxy = proxy(target);
        System.out.println("RESULT " + runtimeProxy.run("C"));

        try {
            runtimeProxy.run("FAIL");
        } catch (IllegalStateException ex) {
            System.out.println("CALLER caught " + ex.getMessage());
        }

        System.out.println("RESULT " + target.run("DIRECT"));
    }
}
```

Biên dịch và chạy:

```bash
javac WeavingTimingDecisionDemo.java
java WeavingTimingDecisionDemo
```

Stdout theo đúng thứ tự:

```text
TARGET A
RESULT OK:A
DECORATOR before
TARGET B
DECORATOR returning
RESULT OK:B
PROXY before
TARGET C
PROXY returning
RESULT OK:C
PROXY before
TARGET FAIL
PROXY throwing IllegalStateException
CALLER caught operation failed
TARGET DIRECT
RESULT OK:DIRECT
```

### 12.1. Direct target — không có interception

`target.run("A")` và `target.run("DIRECT")` đi thẳng vào `RealWork`, vì thế chỉ có dòng TARGET/RESULT. Việc target có thể được bọc bằng decorator hay proxy ở chỗ khác không thay đổi call path của reference gốc.

Đây là điều quan trọng khi cân nhắc proxy: **khả năng tạo wrapper khác với khả năng buộc mọi caller dùng wrapper**.

### 12.2. Decorator — lắp ghép được kiểm soát

`decorate(target)` tạo một object khác thực hiện `Work`. Code của decorator được viết tường minh, nhưng việc nó được lắp vào ứng dụng diễn ra khi chạy. Lời gọi `decorator.run("B")` in before, target, returning rồi kết quả.

Không gọi hình thức này là “compiler đã weave body của `RealWork`”. Nó là composition/wrapping đã được lập trình viên tạo ra.

### 12.3. Dynamic proxy — handler tạo khi chạy

`Proxy.newProxyInstance` sinh một object proxy triển khai `Work`; handler nhận invocation qua proxy. Handler dùng reflection để gọi target, ghi trước/sau và gỡ `InvocationTargetException` khi target ném lỗi.

Với `runtimeProxy.run("FAIL")`, target in `TARGET FAIL` rồi ném `IllegalStateException`; handler in `PROXY throwing IllegalStateException`, chuyển tiếp nguyên nhân để caller nhận `operation failed`.

**Chú ý:** Code chỉ minh họa một method được caller gọi. Với một interface có các method kế thừa từ `Object`, overload hoặc default methods, handler thực tế cần cách xử lý chính xác những invocation đó theo hợp đồng; không dựa vào demo đơn giản để khẳng định proxy bao mọi trường hợp.

### 12.4. Kết luận từ demo — và giới hạn của kết luận

Bạn có thể chứng minh:
- Decorator/proxy có thể tạo một boundary kiểm soát lời gọi.
- Advice trong wrapper chạy **khi caller dùng wrapper**.
- Direct call vào target không đi qua wrapper.
- Có thể giữ nguyên kết quả/exception bằng cách tiếp tục gọi target đúng cách.

Bạn **không thể** chứng minh:
- Một class đã được compile-time/load-time weave.
- Constructor, field hoặc private method đã có hook.
- Công cụ AOP cụ thể hỗ trợ retransform class đã load.
- Bản thân việc tạo proxy làm thay đổi bytecode của target.
- Mọi caller của target đã được bao phủ.

Để kiểm chứng những điều đó, cần một công cụ weaving tương ứng và inspection/runtime tests cho chính artifact được chọn.

## 13. Chọn theo loại join point thay vì chỉ tên thời điểm

### 13.1. Method execution và method call

Một công cụ có thể phân biệt join point ở **phía method body bắt đầu thực thi** và join point ở **vị trí gọi method**. Một method có thể được gọi từ nhiều call site; một call site trong loop có thể chạy nhiều lần.

Nếu yêu cầu đo mọi execution của một method, một hook ở implementation có thể phù hợp hơn hook ở một vài call site cụ thể. Nếu yêu cầu theo dõi caller cụ thể, method-call join point có thể cần thiết. Phải kiểm tra công cụ ở thời điểm weaving nào hỗ trợ từng loại điểm; không mặc định mọi kỹ thuật có cùng tập điểm.

### 13.2. Field access và constructor

JDK interface proxy không tự chặn việc đọc/ghi field hoặc tạo object trước khi có proxy. Một số công cụ weaving tại source/bytecode có thể hỗ trợ những điểm đó, nhưng cần xem giới hạn về visibility, class verification và định nghĩa join-point model.

Đây là ví dụ **loại điểm quan trọng hơn nhãn “compile/load/runtime”**. Chọn sai công cụ dù đúng thời điểm cũng không đáp ứng yêu cầu.

### 13.3. Self-invocation và recursion

Nếu target gọi một method khác trên `this`, đường đó thường **không đi lại qua JDK proxy**. Với một engine weave trực tiếp vào body, kết quả có thể khác. Nếu yêu cầu đo mọi execution kể cả gọi nội bộ/đệ quy, cần kiểm tra bằng test thực chứ không suy từ chữ “AOP”.

### 13.4. Async completion

Một around advice bao quanh method trả `CompletionStage` thường kết thúc khi method **trả handle**, không tự kéo dài tới lúc task hoàn thành. Nếu phải đo end-to-end latency, cần hook theo completion API hoặc join point khác mà công cụ hỗ trợ. Thời điểm cài hook vào class **không tự giải quyết lifecycle bất đồng bộ**.

## 14. Hạn chế và tương thích công nghệ

### 14.1. Bytecode version và verifier

Một transformer phải tạo output tương thích class file format và runtime verifier. Sau khi nâng compiler/JDK, một engine chưa hỗ trợ bytecode mới có thể lỗi khi load hoặc tạo instrumentation sai. Cần pin/test ma trận compiler, JDK, weaver và dependency thực.

### 14.2. Debug symbols và stack trace

Source-time/post-compile/load-time weaving có thể thay đổi số lượng instruction, frame, method phụ hoặc call stack. Công cụ có thể bảo toàn mapping tốt, nhưng không được hứa line numbers/stack traces giống trước khi weave trong mọi trường hợp. Cần kiểm thử logging, exception và profiling.

### 14.3. Reflection, module và security boundary

Một số agent/proxy dùng reflection hoặc cần module access. Quyền truy cập tại runtime có thể bị giới hạn. Cấu hình include một class không đủ để vượt quy định của module system hoặc policy bảo mật. Không nên mở rộng quyền runtime quá mức cần thiết chỉ để “cho weaving chạy”.

### 14.4. Native image hoặc AOT

Trong môi trường closed-world/AOT, nhiều khả năng runtime bytecode generation, reflection hoặc attach có thể không khả dụng giống một JVM động. Nếu hệ thống cần AOT/native, phải đánh giá toolchain từ sớm; một lựa chọn build-time có thể phù hợp, nhưng vẫn tùy framework, compiler, reflection configuration và cơ chế weaving được hỗ trợ.

### 14.5. Dynamic code generation

Khi ứng dụng sinh class ở runtime, class đó có thể không tồn tại trong artifact build. Build-time weaving sẽ không tự xử lý mã chưa được tạo, trừ khi architecture đã biết cách instrument output trước hoặc trong quá trình sinh. Load-time transformer có thể thấy class sinh ra trong một số mô hình, nhưng cần đúng loader/hook; không có bảo đảm chung.

## 15. Kiểm soát phạm vi, nhiều agent và khả năng trùng hook

### 15.1. Include/exclude ở đúng tầng

Một chính sách có thể gồm:
- **Eligible classes/artifacts**: lớp nào được phép biến đổi.
- **Join-point kind**: loại sự kiện nào engine hỗ trợ.
- **Pointcut matcher**: method/annotation/arguments nào khớp.
- **Runtime condition**: lượt xảy ra nào thỏa điều kiện động.

Exclude toàn bộ class khỏi weaving có thể tắt nhiều aspect trên class đó, trong khi điều kiện `NOT internal` ở một pointcut có thể chỉ tắt một advice. Cần xem hậu quả lên tracing, metrics, audit và logic kỹ thuật khác.

### 15.2. Hai weaver cùng chạm một class

Nếu pipeline có nhiều compiler transform, binary weaver hoặc runtime agent, chúng có thể áp dụng trên output của nhau. Thứ tự biến đổi, tính idempotent và khả năng tương thích rất quan trọng.

Tình huống thường phải kiểm:
- Artifact đã được weave ở build nhưng runtime agent cũng instrument cùng method.
- Một agent retransform class nhiều lần và thêm hook trùng.
- Một transformer giả định cấu trúc bytecode gốc trong khi transformer trước đã đổi nó.
- Pointcut rộng chọn cả code instrumentation gây vòng lặp `advice -> logger -> advice`.

Cần có rule tránh biến đổi mã của chính logging/metrics/advice runtime khi không có chủ đích, đồng thời kiểm thử những chính sách khác không bị vô tình loại.

### 15.3. Không xem mọi duplicate log là “weaving kép”

Hai advice riêng có thể cùng chọn một join point một cách hợp lệ. Một logical operation cũng có thể đi qua method call và execution join points, hoặc retry nhiều lần. Phải phân biệt **trùng hook ngoài ý định** với **nhiều sự kiện/advice có chủ đích** bằng correlation, attempt ID và thông tin về điểm can thiệp.

## 16. Đánh giá hiệu năng bằng số đo đúng loại

Một phương án có thể tăng **build time** nhưng giảm **startup transform time**. Một phương án khác có thể không làm chậm build nhưng tăng **classloading latency**. Proxy có thể thêm dispatch/reflection cost ở mỗi invocation. Những con số này khác nhau, không thể gọi chung là “chậm hơn”.

### 16.1. Những metric nên thu

| Thời điểm | Metric có giá trị |
| --- | --- |
| Compile/build weaving | Thời gian compile/transform, kích thước artifact, số class được xử lý, cache misses. |
| Post-compile binary weaving | Số bytecode artifacts thay đổi, thời gian patch, ký/verify, số lỗi class verification. |
| Load-time weaving | Startup time, time spent transforming, số class load/transform, số lỗi loader. |
| Runtime retransformation | Thời gian retransform, số class affected, mức gián đoạn, lỗi, thời gian rollback. |
| Runtime proxy/decorator | Chi phí dispatch theo invocation, allocation, khả năng inline/optimize, call-path coverage. |
| Mọi chiến lược | Advice invocation rate, CPU/memory, tail latency, I/O telemetry, GC, throughput. |

Một class eligible nhưng method ít được gọi có thể tạo overhead runtime rất thấp; một method hot path được gọi hàng triệu lần có thể tốn kém dù chỉ có một class được weave. Phải đo theo **runtime occurrences** và payload thực.

### 16.2. Benchmark phải tách overhead của cơ chế khỏi overhead của advice

Một phép đo có thể so sánh:
1. Không instrument.
2. Có hook nhưng advice rỗng/không ghi I/O.
3. Có advice thực nhưng dùng sink giả có kiểm soát.
4. Có logging/tracing production với sampling, queue và backend.

Nếu chỉ so AOP có log mạng với code không log, chênh lệch không chứng minh do **thời điểm weaving**. Đo p50/p95/p99 trong workload đại diện, kể cả startup/redeploy nếu đó là yêu cầu.

### 16.3. Yêu cầu ổn định cũng bao gồm failure mode

Một transformer lỗi lúc load có thể làm class không định nghĩa được, kéo sập startup. Một proxy handler lỗi có thể làm invocation thất bại. Một aspect ném exception trong cleanup có thể che lỗi target. Vì vậy đánh giá performance phải đi cùng **resilience/correctness**, không chỉ microbenchmark.

## 17. CI/CD, artifact provenance và rollback

### 17.1. Với weaving trước runtime

Pipeline gợi ý:

```text
Compile
  -> Weave / transform
  -> Inspect / verify output
  -> Unit + integration tests
  -> Performance / compatibility checks
  -> Package + sign
  -> Deploy canary
  -> Promote / rollback artifact
```

Đảm bảo **artifact được ký là artifact sẽ chạy**, không ký binary trước rồi weave sau khiến chữ ký không còn phản ánh bytes thực. Nếu hệ thống có SBOM và provenance, bước biến đổi phải được mô tả như một phần của build pipeline.

### 17.2. Với load-time weaving

Pipeline vẫn cần kiểm thử với **cùng flags/agent order, classloader graph và quyền module** như production. Không đủ nếu unit tests chỉ chạy code mà chưa bật transformer.

Rollout có thể cần restart để những class đã load nhận phiên bản weave mới. Không khẳng định restart luôn cần cho mọi engine; kiểm tra khả năng unweave/retransform của công cụ.

### 17.3. Với runtime instrumentation

Phải có:
- Danh tính và quyền người/tiến trình được phép attach.
- Danh sách class/pointcut nằm trong scope.
- Canaries và ngưỡng dừng khi CPU/latency/error rate tăng.
- Biện pháp không ghi dữ liệu nhạy cảm.
- TTL cho cấu hình chẩn đoán.
- Kiểm chứng cơ chế rollback/untransform thực tế.
- Audit sự kiện thay đổi instrumentation.

Một “toggle OFF” có thể chỉ khiến advice không ghi log, trong khi bytecode vẫn bị biến đổi. Báo cáo rollout phải diễn đạt đúng điều đã thay đổi.

## 18. Kiểm thử chứng minh lựa chọn thời điểm đáp ứng yêu cầu

### 18.1. Unit test cho bộ chọn policy

Cho danh sách class/module đại diện:

| Class/kiểu | Kỳ vọng |
| --- | --- |
| `app.orders.OrderService` | Được xét nếu nằm trong allowlist. |
| `app.internal.Metrics` | Có thể loại khỏi pointcut/logging tránh recursion. |
| `vendor.adapter.Client` | Theo policy có/không instrument dependencies. |
| `app.generated.Mapper` | Phụ thuộc chiến lược generated-code rõ ràng. |
| Class nested/bridge/synthetic | Theo tên binary/metadata thực, không đoán từ source. |

Nếu engine có include/exclude ở cấp classloader/module, bổ sung test đúng ranh giới ấy.

### 18.2. Build-time evidence

Với compile/post-compile weaving, kiểm tra binary thực:
- Hook hoặc biến đổi có nằm trong artifact cuối không?
- Tên method/descriptor còn đúng không?
- Bytecode có verify trên runtime mục tiêu không?
- Pointcut match đúng và không sinh duplicate advice?
- Debug/exception behavior có đúng không?

Đừng chỉ tin build thành công: bước weaving có thể bị skip vì config, incremental cache hoặc artifact khác được deploy.

### 18.3. Load-time evidence

Với load-time:
- Transformer được đăng ký **trước** thời điểm class mục tiêu load chưa?
- Class nào được **xét** và class nào **thực sự transform**?
- Classloader và module nào xử lý?
- Có lỗi module access/verification không?
- Những class có cùng tên nhưng khác loader có được xử lý đúng không?

### 18.4. Runtime evidence

Bất kể cơ chế:
- **Positive:** join point đủ điều kiện, pointcut khớp, runtime xảy ra → advice đúng.
- **Negative scope:** class nằm ngoài eligible không bị can thiệp ngoài ý định.
- **Negative pointcut:** method không khớp không nhận advice đó.
- **Bypass:** đường gọi trực tiếp vào target trong mô hình proxy được xác định rõ.
- **Nested/self-call:** quan sát thực tế thay vì suy luận.
- **Error path:** exception type/cause được bảo toàn theo hợp đồng.
- **Async:** return handle vs task completion được phân loại đúng.
- **Concurrency:** không rò context hoặc trộn state của các invocation.
- **Performance:** chi phí phù hợp ngân sách tại workload thật.

### 18.5. Test nâng cấp và rollback

Khi thay compiler/runtime/weaver:
1. Rebuild với version mới.
2. So artifact transformed và class counts.
3. Chạy integration tests cho cả target và advice.
4. So performance và startup.
5. Deploy canary, theo dõi error/latency.
6. Thử rollback đúng thủ tục khi có vấn đề.

Một chiến lược “có thể lắp trong runtime” nhưng **không có rollback an toàn** có thể kém phù hợp production hơn một artifact-based release dù việc cập nhật mất thêm thời gian.

## 19. Sai lầm thường gặp khi chọn thời điểm weaving

**“Compile-time weaving nghĩa là advice chạy khi biên dịch.”** Sai. Advice thường chạy khi runtime đến join point; compile-time là thời điểm cài logic.

**“Load-time weaving có thể sửa mọi class bất kỳ lúc nào.”** Sai. Class đã load cần cơ chế khác và có giới hạn riêng.

**“Đã có Java agent thì retransform được mọi thay đổi cấu trúc class.”** Sai. Phải xem runtime capabilities, class modifiability và giới hạn loại thay đổi.

**“Runtime proxy tức là bytecode của target đã được weave.”** Sai. Proxy có thể là object riêng; target ban đầu không bị sửa.

**“Chọn compile-time là hiệu năng runtime luôn tối ưu.”** Không có bảo đảm. Advice vẫn tạo chi phí và công cụ có trade-off riêng.

**“Chọn load-time là không cần kiểm thử artifact.”** Sai. Binary gốc, transformer, cấu hình, classloader và runtime kết hợp mới tạo hành vi cuối.

**“Pointcut match nên mọi caller sẽ chạy advice.”** Sai nếu caller bỏ qua proxy, join point không xảy ra hoặc class không được weave.

**“Hot reload config tức là unweave code đã cài.”** Không thể mặc định. Cần cơ chế undo/retransform được chứng minh.

**“Dệt trước runtime luôn bao được generated code.”** Chưa chắc mã ấy được sinh sau thời điểm dệt hoặc nằm ngoài build graph.

**“Mọi app có cùng yêu cầu do đều dùng Java 17.”** Sai. Classloader, module, đóng gói, lifecycle, JIT, native image và chính sách vận hành khác nhau.

**“Một benchmark nhỏ đủ chọn chiến lược.”** Không. Phải tính build/startup/run overhead, failure modes, đổi chính sách, rollback và đúng join-point model.

**“Muốn bảo mật thì chỉ cần weave advice kiểm tra quyền.”** Không đủ khi có đường bypass hoặc biến đổi thiếu độ phủ. Authorization invariant cần được bảo đảm ở tầng tin cậy.

## 20. Bài tập quyết định

### Bài 1 — Runtime bị cấm attach agent

Ứng dụng có source và CI kiểm soát chặt. Chính sách yêu cầu mọi binary cuối phải có chữ ký; runtime không cho attach agent. Bạn chọn hướng nào?

**Đáp án gợi ý:** Xem xét compile-time/post-compile weaving hoặc wrapper đóng gói; nếu weave binary thì verify/test/sign **sau biến đổi**. Kiểm tra công cụ hỗ trợ join points cần thiết, không chọn chỉ vì tên kỹ thuật.

### Bài 2 — Chỉ có interface boundary

Ứng dụng chỉ cần ghi trace khi gọi `PaymentPort.charge()`, mọi caller được quản lý qua cùng factory. Có nhất thiết phải bytecode weave không?

**Đáp án:** Không nhất thiết. Decorator hoặc interface proxy có thể đủ nếu call path được bảo đảm và hợp đồng observability rõ; kiểm thử bypass/self-call.

### Bài 3 — Plugin load sau startup

Host tải plugin qua loader riêng. Một load-time transformer chỉ gắn cho app classloader. Pattern class trông đúng nhưng không thấy advice. Nguyên nhân khả dĩ?

**Đáp án:** Transformer không thấy plugin loader, hoặc plugin class đã load trước khi hook được cài, ngoài những khả năng khác như pointcut không khớp. Cần diagnostic classloader và thời điểm load.

### Bài 4 — Cần thay đổi logging ngay trong production

Nếu đã có hook nhưng có config runtime bật/tắt logging, có nhất thiết retransform class?

**Đáp án:** Không. Có thể chỉ đổi policy của advice/hook có sẵn; cần đo overhead còn lại và giới hạn dữ liệu, không nhầm với unweave.

### Bài 5 — Cần quan sát field writes

JDK interface proxy đang chạy tốt cho method calls. Có thể thêm `@Observed` vào field để proxy tự chặn setter bypass và direct field writes không?

**Đáp án:** Không. Annotation chỉ là metadata; proxy không tự có field-access join point. Cần công cụ hỗ trợ loại điểm và cơ chế thích hợp.

### Bài 6 — Đo end-to-end async

Một around advice dệt ở compile time quanh method trả `CompletableFuture`, đo tới khi `proceed()` trả handle. Có phải đã đo hết task?

**Đáp án:** Không. Thời điểm dệt không đổi sự khác biệt giữa method return và task completion. Phải quan sát terminal event của future/task.

### Bài 7 — Rollback

Bạn tắt agent sau khi instrument class đang chạy. Điều đó bảo đảm method trở lại bytecode gốc chưa?

**Đáp án:** Không mặc định. Cần xem agent có undo/retransformation hay cần restart/rollback artifact; có thể chỉ tắt handler nhưng bytecode đã biến đổi vẫn tồn tại.

### Bài 8 — Tối ưu hiệu năng

Load-time weaving làm startup chậm đáng kể nhưng runtime throughput tương đương. Phần lớn deployment là serverless short-lived tasks. Hướng đánh giá?

**Đáp án:** Startup/cold-start là yêu cầu trọng yếu; cân nhắc dệt trước runtime hoặc scope hẹp hơn, benchmark đúng workload và kiểm tra tính khả thi. Không khẳng định chiến lược mới chắc nhanh hơn khi chưa đo.

## 21. Checklist chọn và quản trị thời điểm weaving

1. **Mục tiêu:** Concern là gì, advice chỉ quan sát hay thay đổi hành vi?
2. **Join points:** Call, execution, constructor, field hay event đặc thù?
3. **Artifact ownership:** Có source, binary, quyền build/modify/sign/deploy không?
4. **Thời điểm:** Class được sinh/load khi nào, có đổi policy trong process không?
5. **Tool capability:** Compiler/weaver/agent hỗ trợ những điểm/kiểu bytecode nào?
6. **Runtime policy:** Có cho phép agent, retransform, reflection/module access không?
7. **Scope:** Những module/classloader/artifact nào đủ điều kiện, loại trừ ra sao?
8. **Compatibility:** Compiler/JDK/native image/module, generated classes và dependencies thế nào?
9. **Artifact evidence:** Có thể inspect transform output và verify bytes deploy không?
10. **Runtime evidence:** Positive/negative tests có chứng minh hook và occurrence thực không?
11. **Error semantics:** Exception, result, advice precedence và retry có được giữ đúng không?
12. **Async semantics:** Chỉ method invocation hay toàn bộ task completion?
13. **Performance:** Tách build/startup/retransform/invocation/advice I/O overhead chưa?
14. **Privacy/security:** Có tránh dữ liệu nhạy cảm và bypass policy quan trọng không?
15. **Operational change:** Cần hot config, canary, rollback và audit trail thế nào?
16. **Maintenance:** Refactor/package/module updates có làm scope silently thay đổi không?

## Kết luận

Không có thời điểm dệt AOP “tốt nhất cho mọi hệ thống”. **Compile/source-time** và **post-compile weaving** thuận lợi khi muốn kiểm soát, kiểm thử và phân phối artifact đã biến đổi trước runtime; **load-time weaving** phù hợp khi cần xử lý class lúc được nạp và quản lý loader; **runtime instrumentation** có thể mang lại linh hoạt trong giới hạn quyền và khả năng sửa class; **proxy/decorator** đơn giản hơn cho những boundary được kiểm soát nhưng không tự cung cấp mọi join point.

Hãy quyết định từ **yêu cầu join-point model, ownership artifact, khả năng vận hành, chi phí ở từng giai đoạn và bằng chứng QA**. Chọn đúng thời điểm chỉ là một phần: còn phải bảo đảm class nằm trong scope, pointcut khớp, hook đã cài và runtime thật sự đi qua nó. Một lựa chọn tốt là lựa chọn có thể **giải thích, kiểm thử, đo lường, triển khai và rollback** với rủi ro được kiểm soát.


</details>

- [Quay lại đầu trang](#back-to-top)
