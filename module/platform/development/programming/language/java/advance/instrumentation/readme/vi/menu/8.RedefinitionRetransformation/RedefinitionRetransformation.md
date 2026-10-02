<a id="back-to-top"></a>

# Redefinition và Retransformation

## Menu
- [Redefinition dùng để làm gì?](#redefine-purpose)
- [Retransformation dùng để làm gì?](#retransform-purpose)
- [Redefine và retransform khác nhau ở đâu?](#redefine-vs-retransform)
- [Stack frame đang hoạt động khi method được thay đổi](#active-frame-behavior)
- [Trạng thái instance và static sau khi class được thay đổi](#existing-state-behavior)

## <a id="redefine-purpose">Redefinition dùng để làm gì?</a>

<details>
<summary>Xem chi tiết</summary>

Redefinition thay định nghĩa class bằng **một bộ class-file bytes do bên gọi cung cấp**:

~~~java
byte[] replacement = loadCompiledReplacement();
ClassDefinition definition =
        new ClassDefinition(OrderService.class, replacement);

inst.redefineClasses(definition);
~~~

Mô hình tư duy:

~~~text
bên gọi đã có class bytes thay thế
        ↓
redefineClasses(...)
        ↓
các transformer đã đăng ký tham gia theo quy tắc của chuỗi
        ↓
JVM verify + cài định nghĩa mới
~~~

Trường hợp sử dụng điển hình là fix-and-continue/gỡ lỗi hoặc công cụ đã có sẵn bytes thay thế. Java API còn gợi ý rằng nếu mục tiêu là “lấy class hiện tại rồi áp dụng bytecode instrumentation”, retransformClasses thường phù hợp hơn.

Redefinition cần Can-Redefine-Classes trong manifest và JVM phải hỗ trợ khả năng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="retransform-purpose">Retransformation dùng để làm gì?</a>

<details>
<summary>Xem chi tiết</summary>

Retransformation dùng khi muốn **chạy lại quá trình biến đổi trên class đã được nạp**.

~~~java
if (inst.isRetransformClassesSupported()
        && inst.isModifiableClass(OrderService.class)) {
    inst.retransformClasses(OrderService.class);
}
~~~

Agent không truyền bytes thay thế trực tiếp vào retransformClasses. JVM tự xây đầu vào chuỗi từ bytes ban đầu/lần redefine gần nhất, tái dùng đầu ra của các transformer không hỗ trợ retransformation rồi gọi lại các transformer có hỗ trợ retransformation.

“Bytes ban đầu” ở đây không phải cam kết byte-for-byte giống file .class ban đầu. API cho phép bố trí/thứ tự constant pool khác và một số attribute có thể khác hoặc không còn hiện diện, miễn các tham chiếu bytecode vẫn tương ứng về ngữ nghĩa. Transformer nên dựa trên mô hình/ngữ nghĩa class file thay vì bố trí byte thô chính xác của file gốc.

Điều này rất phù hợp cho cấu hình động:

~~~text
agent attach
→ đăng ký transformer hỗ trợ retransformation
→ tìm OrderService đã được nạp
→ retransform
→ timing probe xuất hiện
~~~

Sau đó nếu cấu hình đổi, agent có thể retransform lại và tạo bytes theo trạng thái mong muốn mới — miễn thiết kế phép biến đổi hỗ trợ điều đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="redefine-vs-retransform">Redefine và retransform khác nhau ở đâu?</a>

<details>
<summary>Xem chi tiết</summary>

Khác biệt cốt lõi:

| | Redefine | Retransform |
| --- | --- | --- |
| Bên gọi cung cấp bytes thay thế | Có | Không |
| Chạy lại chuỗi transformer | Có, trên bytes được redefine | Có, theo quy tắc retransformation |
| Transformer không hỗ trợ retransformation | được gọi trong redefine | không gọi lại; tái dùng đầu ra cũ |
| Phù hợp | đã có định nghĩa thay thế | áp dụng lại instrumentation hiện tại |

Quy tắc thực tế:

~~~text
"Tôi có class bytes mới cụ thể muốn cài"
→ redefine

"Tôi muốn chuỗi instrumentation tính lại class đã được nạp"
→ retransform
~~~

Cả hai đều cần class có thể sửa đổi và đều chịu các giới hạn cấu trúc. Không nên chọn chỉ vì tên một method nghe “mạnh hơn”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="active-frame-behavior">Stack frame đang hoạt động khi method được thay đổi</a>

<details>
<summary>Xem chi tiết</summary>

Khi method được redefine/retransform trong lúc đang có lời gọi đang chạy, JVM **không chuyển stack frame đang hoạt động sang bytecode mới giữa chừng**.

Ví dụ:

~~~text
Thread A đi vào OrderService.placeOrder()
        ↓
bytecode cũ đang chạy

Thread B kích hoạt retransform
        ↓
định nghĩa mới được cài

Thread A
→ tiếp tục bytecode cũ cho lời gọi hiện tại

lời gọi mới
→ dùng bytecode mới
~~~

Hệ quả là trong một khoảng ngắn, cùng một method có thể có lời gọi cũ đang chạy và lời gọi mới dùng định nghĩa mới.

Thiết kế agent/probe phải chịu được giai đoạn chuyển tiếp này. Đừng giả định “retransform trả về rồi thì mọi stack frame đều đang chạy code mới”.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="existing-state-behavior">Trạng thái instance và static sau khi class được thay đổi</a>

<details>
<summary>Xem chi tiết</summary>

Redefine/retransform thay **định nghĩa code**, không tạo lại object đang tồn tại.

Java API nêu rõ:

- các instance của class không bị thay thế;
- biến static giữ giá trị hiện tại;
- class initializer không tự chạy lại chỉ vì redefine/retransform.

Ví dụ OrderService có:

~~~java
static int requestCount = 42;
private final String region = "ap-southeast";
~~~

Sau khi retransform thân method, requestCount không tự về 0 và constructor không chạy lại cho các instance OrderService hiện có.

Đây là lý do các thay đổi cấu trúc bị giới hạn mạnh: JVM phải giữ identity/trạng thái của class và object hiện có trong khi phần hiện thực method có thể đổi.

Nếu công cụ cần “di chuyển schema/trạng thái object”, Java Instrumentation redefine/retransform không phải cơ chế phù hợp.

</details>

- [Quay lại đầu trang](#back-to-top)
