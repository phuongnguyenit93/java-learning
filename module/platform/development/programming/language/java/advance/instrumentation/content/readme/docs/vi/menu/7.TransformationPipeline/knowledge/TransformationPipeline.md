<a id="back-to-top"></a>

# Chuỗi biến đổi class

## Menu
- [Khi nào transformation pipeline được kích hoạt?](#transformation-trigger-points)
- [Thứ tự thực thi các nhóm transformer](#transformer-order)
- [Cách đầu ra của transformer trước trở thành đầu vào của transformer sau](#transformer-chaining)
- [Transformer có và không có khả năng retransformation](#retransform-capability-groups)
- [Reentrancy, dependency và recursive instrumentation](#reentrancy-recursion)
- [Thiết kế phép biến đổi có tính idempotent](#idempotent-transformation)

## <a id="transformation-trigger-points">Khi nào transformation pipeline được kích hoạt?</a>

<details>
<summary>Xem chi tiết</summary>

Chuỗi biến đổi có thể được kích hoạt bởi ba loại thao tác:

~~~text
định nghĩa class mới
→ ClassLoader.defineClass hoặc cơ chế native tương đương

redefine class
→ Instrumentation.redefineClasses

retransform class
→ Instrumentation.retransformClasses
~~~

Trong cả ba trường hợp, transformer chạy trước khi JVM áp dụng định nghĩa kết quả. Nhưng **bytes đầu vào và transformer nào được gọi** không giống nhau.

Với lần nạp đầu, đầu vào bắt nguồn từ bytes được đưa vào defineClass. Với redefine, đầu vào bắt nguồn từ ClassDefinition do bên gọi cung cấp. Với retransform, JVM quay về bytes ban đầu/lần redefine gần nhất phù hợp rồi tự tái áp dụng kết quả của các transformer không hỗ trợ retransformation trước khi gọi nhóm transformer hỗ trợ retransformation.

Vì vậy khi gỡ lỗi chuỗi biến đổi, câu hỏi đầu tiên luôn là: **thao tác hiện tại là nạp lần đầu, redefine hay retransform?**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transformer-order">Thứ tự thực thi các nhóm transformer</a>

<details>
<summary>Xem chi tiết</summary>

Java 21 định nghĩa thứ tự transformer theo bốn nhóm:

~~~text
1. Java transformers không hỗ trợ retransformation
2. native transformers không hỗ trợ retransformation
3. Java transformers hỗ trợ retransformation
4. native transformers hỗ trợ retransformation
~~~

Trong từng nhóm, transformer được gọi theo thứ tự đăng ký.

Điều này nghĩa là “thứ tự đăng ký” chỉ đúng **bên trong một nhóm**. Một transformer canRetransform=true có thể được đăng ký trước một transformer canRetransform=false nhưng vẫn chạy sau nhóm không hỗ trợ retransformation.

Native transformer thuộc JVMTI ClassFileLoadHook và được nhắc ở đây chỉ để hiểu đầy đủ thứ tự. Việc viết native agent/JVMTI không thuộc phạm vi của module.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="transformer-chaining">Cách đầu ra của transformer trước trở thành đầu vào của transformer sau</a>

<details>
<summary>Xem chi tiết</summary>

Nhiều transformer được **ghép thành chuỗi**:

~~~text
bytes đầu vào
  ↓ transformer A
bytes A
  ↓ transformer B
bytes B
  ↓ transformer C
bytes cuối cùng
~~~

Đầu ra byte[] của transformer trước trở thành classfileBuffer của transformer sau. Vì vậy một transformer không nhất thiết thấy “class gốc”; nó có thể thấy class đã bị transformer trước chỉnh sửa.

Ví dụ:

~~~text
coverage agent
→ chèn counter

APM agent
→ nhận bytes đã có counter
→ chèn span đo thời gian
~~~

Hệ quả thiết kế:

- tránh phụ thuộc vào bố trí byte chính xác nếu không cần;
- bộ phân tích phải chịu được attribute/code do công cụ khác thêm;
- cần kiểm thử khả năng cùng tồn tại với agent khác;
- phép biến đổi nên giữ ngữ nghĩa của phần không thuộc trách nhiệm mình.

Việc ghép chuỗi là lý do hệ sinh thái agent cần công cụ bytecode đủ bền vững và tương thích phiên bản.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="retransform-capability-groups">Transformer có và không có khả năng retransformation</a>

<details>
<summary>Xem chi tiết</summary>

Khi retransformClasses() được gọi, hai nhóm Java transformer khác nhau rõ rệt:

**canRetransform=false**

- transform() **không được gọi lại**;
- JVM tái sử dụng đầu ra mà transformer đó tạo ở lần nạp/redefine trước;
- hiệu ứng cũ được tự động áp dụng lại vào chuỗi biến đổi.

**canRetransform=true**

- transform() được gọi lại;
- transformer nhận đầu vào sau khi phần không hỗ trợ retransformation đã được tái áp dụng;
- transformer có thể tạo đầu ra mới dựa trên cấu hình/trạng thái hiện tại.

Mô hình tư duy:

~~~text
bytes ban đầu / lần redefine gần nhất
        ↓
tái sử dụng đầu ra trước đó của transformer không hỗ trợ retransformation
        ↓
gọi lại các transformer hỗ trợ retransformation
        ↓
verify + cài định nghĩa
~~~

Đây là lý do khả năng được chọn lúc addTransformer, không phải lúc gọi retransformClasses.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reentrancy-recursion">Reentrancy, dependency và recursive instrumentation</a>

<details>
<summary>Xem chi tiết</summary>

Transformer chạy trong đường định nghĩa class, nên code nó gọi có thể vô tình kích hoạt thêm quá trình nạp class.

Ví dụ:

~~~text
transform OrderService
→ lần đầu dùng logger
→ nạp hiện thực Logger
→ transformer gặp class Logger
→ code biến đổi lại ghi log
→ nạp đệ quy / vấn đề phụ thuộc
~~~

Java API loại trừ một số lần định nghĩa class mà transformer phụ thuộc, nhưng agent vẫn phải thiết kế để tránh đệ quy rộng hơn.

Các biện pháp:

- nạp/khởi tạo helper quan trọng trước khi đăng ký transformer;
- denylist package của agent/công cụ;
- giữ đường thực thi transform nhẹ và ít phụ thuộc;
- dùng ThreadLocal hoặc guard chống reentrancy khi phù hợp;
- không thực hiện network I/O hoặc khởi tạo nặng trong callback nếu có thể tránh.

Quá trình nạp class là môi trường đồng thời và có thể reentrant. Transformer phải được thiết kế như hạ tầng runtime, không như một request handler thông thường.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="idempotent-transformation">Thiết kế phép biến đổi có tính idempotent</a>

<details>
<summary>Xem chi tiết</summary>

Một phép biến đổi **idempotent** hướng tới kết quả ổn định khi gặp cùng một trạng thái đầu vào hợp lệ. Tuy nhiên cần hiểu đúng quy ước của retransformation: với transformer có canRetransform=true, JVM **không** đưa đầu ra của chính lần retransform trước của transformer đó trở lại làm đầu vào cho lần sau. JVM bắt đầu lại từ mốc bytes ban đầu/lần redefine gần nhất, tái áp dụng đầu ra của nhóm không hỗ trợ retransformation, rồi mới gọi lại transformer có hỗ trợ retransformation.

Vì vậy tình huống sau là **mô hình tư duy sai**:

~~~text
retransform #1 → probe A
retransform #2 → probe A + probe A
retransform #3 → probe A + probe A + probe A
~~~

Một transformer hỗ trợ retransformation đúng quy ước không tự tích lũy đầu ra cũ theo cách đó.

Rủi ro probe trùng vẫn có thể xuất hiện khi **mốc đầu vào đã chứa instrumentation** từ weaving lúc build, definition thay thế qua redefine, transformer không hỗ trợ retransformation, hoặc khi cùng logic bị đăng ký/chạy nhiều lần trong một chuỗi. Khi đó transformer cần nhận diện trạng thái đã được instrument thay vì chèn thêm mù quáng.

Các chiến lược:

- hiểu chính xác mốc cơ sở mà quy ước API cung cấp và các nhóm transformer;
- nhận diện marker/instruction/lời gọi helper do chính agent đã thêm;
- tạo phép biến đổi xác định từ cấu hình;
- tách “trạng thái instrumentation mong muốn” khỏi “bytes hiện tại”;
- kiểm thử nạp → retransform → retransform nhiều lần.

Tính idempotent đặc biệt quan trọng khi agent phải cùng tồn tại với instrumentation đã có, nhiều transformer hoặc cấu hình động. Nếu agent không biết bytes nào sẽ là đầu vào ở mỗi giai đoạn của chuỗi biến đổi, gần như không thể suy luận rollback một cách an toàn.

</details>

- [Quay lại đầu trang](#back-to-top)
