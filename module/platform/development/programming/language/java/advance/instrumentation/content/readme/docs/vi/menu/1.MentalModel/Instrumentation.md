<a id="back-to-top"></a>

# Mô hình Java Instrumentation

## Menu
- [Instrumentation là gì và vì sao tồn tại?](#instrumentation-purpose)
- [Vấn đề nào cần Instrumentation giải quyết?](#instrumentation-problem)
- [Cơ chế instrumentation ở mức bytecode](#instrumentation-mechanism)
- [Các nhóm trường hợp sử dụng điển hình](#instrumentation-use-case-shape)
- [Kiến thức tiên quyết và ranh giới module](#instrumentation-boundaries)

## <a id="instrumentation-purpose">Instrumentation là gì và vì sao tồn tại?</a>

<details>
<summary>Xem chi tiết</summary>

Java Instrumentation là cơ chế để một **Java agent** tham gia vào quá trình JVM định nghĩa hoặc thay đổi class. Điểm can thiệp chính là bytecode: agent có thể quan sát class đang được nạp và, khi cần, trả về một phiên bản bytecode khác trước khi JVM cài đặt định nghĩa đó.

Trong Java SE 21, package java.lang.instrument mô tả instrumentation như dịch vụ cho agent đang chạy trên JVM. Interface Instrumentation nhấn mạnh trường hợp phổ biến là **bổ sung bytecode để thu thập dữ liệu** cho profiler, agent giám sát, công cụ phân tích coverage hoặc bộ ghi sự kiện. Quy ước của package cũng cho phép agent biến đổi class khi nạp và, nếu JVM có khả năng tương ứng, xử lý lại class đã được nạp.

Mô hình tư duy nên giữ là:

~~~text
bytes của class ứng dụng
        ↓
JVM / nạp class
        ↓
ClassFileTransformer đã đăng ký
        ↓
bytes gốc hoặc bytes đã biến đổi
        ↓
JVM verify + link + cài định nghĩa class
~~~

Instrumentation tồn tại vì đôi khi logic cần áp dụng **xuyên suốt nhiều class mà không sửa mã nguồn của ứng dụng**. Đây là lý do nó thuộc Java Advanced: lập trình viên không chỉ gọi API ứng dụng nữa mà tham gia trực tiếp vào vòng đời của class trong JVM.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="instrumentation-problem">Vấn đề nào cần Instrumentation giải quyết?</a>

<details>
<summary>Xem chi tiết</summary>

Giả sử ta muốn đo thời gian của mọi lời gọi OrderService.placeOrder() trong nhiều ứng dụng đã build sẵn. Cách đơn giản nhất là sửa mã nguồn:

~~~java
public Order placeOrder(Request request) {
    long start = System.nanoTime();
    try {
        return doPlaceOrder(request);
    } finally {
        metrics.record(System.nanoTime() - start);
    }
}
~~~

Nhưng cách này không phù hợp khi ta đang xây profiler/APM agent, không sở hữu mã nguồn, hoặc cần áp dụng cùng một chính sách cho hàng nghìn class. Reflection có thể khám phá và gọi member ở runtime, còn proxy chỉ chặn được những lời gọi đi qua ranh giới proxy. Cả hai đều không cho ta một hook tổng quát vào **chuỗi định nghĩa class**.

Instrumentation giải quyết khoảng trống đó:

- logic được đóng gói trong agent thay vì rải vào ứng dụng;
- transformer có thể chọn class theo tên, loader hoặc module;
- probe có thể được chèn ngay lúc class được nạp;
- với khả năng phù hợp, class đã được nạp có thể được retransform/redefine.

Đổi lại, agent chạy rất gần JVM và có phạm vi tác động lớn. Một transformer sai có thể làm quá trình verification/linkage của class thất bại hoặc ảnh hưởng toàn ứng dụng. Vì vậy module này luôn học khả năng cùng với giới hạn và an toàn vận hành.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="instrumentation-mechanism">Cơ chế instrumentation ở mức bytecode</a>

<details>
<summary>Xem chi tiết</summary>

Cơ chế cốt lõi không phải “sửa object đang sống”, mà là xử lý **class-file bytes**. ClassFileTransformer nhận metadata của class cùng mảng byte đại diện cho class file. Nó có thể:

1. trả về null để giữ nguyên bytes hiện tại;
2. trả về một mảng byte hợp lệ khác;
3. ném IllegalClassFormatException khi đầu vào không thể xử lý đúng.

Ở lần nạp đầu tiên, phép biến đổi xảy ra trước khi định nghĩa được cài đặt. Với class đã được nạp, redefine và retransform có thể kích hoạt quy trình thay đổi khác nhưng vẫn chịu giới hạn của JVM.

Ví dụ một timing agent về mặt ý tưởng sẽ biến:

~~~text
OrderService.placeOrder()
~~~

thành:

~~~text
ghi thời điểm bắt đầu
try
    chạy bytecode gốc
finally
    ghi thời lượng
~~~

Thư viện như ASM hoặc Byte Buddy có thể giúp tạo mảng byte mới, nhưng chúng **không phải Instrumentation API**. Instrumentation cung cấp vòng đời/hook; công cụ bytecode chỉ giúp hiện thực phép biến đổi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="instrumentation-use-case-shape">Các nhóm trường hợp sử dụng điển hình</a>

<details>
<summary>Xem chi tiết</summary>

Các trường hợp sử dụng thường gặp có thể chia thành bốn nhóm:

| Nhóm | Instrumentation thường làm gì? |
| --- | --- |
| Profiling | chèn timing/counter để đo method hoặc event liên quan cấp phát |
| Coverage | ghi nhận block/line/method nào đã được thực thi |
| Monitoring / APM | chèn probe quanh HTTP, database, messaging hoặc ranh giới nghiệp vụ |
| Event logging / tracing | phát event hoặc hook truyền ngữ cảnh mà ứng dụng không phải tự viết |

Một agent cũng có thể dùng redefine/retransform cho gỡ lỗi hoặc công cụ runtime. Tuy nhiên “có thể sửa bytecode” không đồng nghĩa “nên sửa hành vi nghiệp vụ tùy ý”. Java SE mô tả các công cụ instrumentation lành tính chủ yếu bổ sung logic quan sát thay vì thay đổi trạng thái/ngữ nghĩa của ứng dụng.

Điểm phân ranh giới:

- **Instrumentation** sở hữu cách chèn/biến đổi code và vòng đời của agent.
- **Runtime Diagnostics** sở hữu cách diễn giải thread dump, heap dump, JFR, bằng chứng GC và quy trình tìm nguyên nhân.

Một profiler có thể dùng Instrumentation để thu dữ liệu, nhưng việc biến dữ liệu đó thành kết luận chẩn đoán là lớp trách nhiệm khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="instrumentation-boundaries">Kiến thức tiên quyết và ranh giới module</a>

<details>
<summary>Xem chi tiết</summary>

Trước khi học sâu, người học nên có các mô hình tư duy sau:

- **JVM / bytecode:** class file là đầu vào để JVM verify, link và thực thi.
- **ClassLoader:** identity của class gắn với binary name + defining loader; transformer nhận ngữ cảnh loader.
- **JPMS cơ bản:** readability, exports và opens để hiểu redefineModule.
- **Concurrency cơ bản:** quá trình nạp class có thể xảy ra trên nhiều thread; transformer phải an toàn trước race/reentrancy.

Module này sở hữu Java Agent, Instrumentation, ClassFileTransformer, redefine/retransform và ranh giới vận hành trực tiếp của agent.

Các phần được handoff:

~~~text
định dạng class file / phần thực thi nội bộ của JVM
→ JVM

ủy quyền nạp class / identity chuyên sâu
→ Java Core ClassLoader

ngữ nghĩa JPMS chuyên sâu
→ Java 9 Module System

diễn giải bằng chứng / xử lý sự cố
→ Runtime Diagnostics

phần nội bộ JVMTI / native agent
→ ranh giới công cụ native/JVM
~~~

Giữ ranh giới này giúp ta học đủ để viết và đánh giá agent mà không biến module thành một khóa JVM, ClassLoader, JPMS hay ASM thứ hai.

</details>

- [Quay lại đầu trang](#back-to-top)
