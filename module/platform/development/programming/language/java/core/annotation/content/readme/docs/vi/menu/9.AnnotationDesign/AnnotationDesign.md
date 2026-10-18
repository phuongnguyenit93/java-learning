# Khi nào nên dùng Annotation?

Sau khi đã hiểu Annotation là gì, cách định nghĩa Annotation, `@Retention`, `@Target`, Meta-Annotation, `@Repeatable`, `@Inherited` và xử lý Annotation tại thời điểm biên dịch, câu hỏi cuối cùng không còn là **“Annotation viết như thế nào?”** mà là:

> **Bài toán này có thực sự nên được biểu diễn bằng Annotation không?**

Annotation là một công cụ mô tả. Nó hữu ích khi thông tin cần nằm sát mã nguồn, tương đối ổn định và có một thành phần đọc rõ ràng. Nhưng nếu dùng Annotation để giấu quá nhiều cấu hình hoặc luồng xử lý, mã nguồn có thể khó hiểu hơn một API hay cấu hình tường minh.

Chương này tổng hợp toàn bộ module thành một mô hình thiết kế từ đầu đến cuối.

## <a id="annotation-fit">Khi nào Annotation là lựa chọn phù hợp?</a>

Annotation phù hợp khi thông tin:

- gắn tự nhiên với class, method, field, parameter hoặc type;
- tương đối ổn định;
- mang tính khai báo hơn là thuật toán;
- cần trình biên dịch, công cụ hoặc framework đọc;
- nên nằm gần mã nguồn để thay đổi cùng mã nguồn khi tái cấu trúc (refactor).

Ví dụ:

```text
method này là test
class này ánh xạ tới một loại dữ liệu
field này có quy tắc kiểm tra hợp lệ
method này cần audit
API này đã deprecated
```

Điểm chung của các ví dụ trên là Annotation đang trả lời câu hỏi:

```text
"Thành phần mã nguồn này có đặc điểm hay ý nghĩa gì?"
```

chứ không trực tiếp mô tả từng bước thuật toán phải chạy.

Annotation thường là lựa chọn tốt khi có một thành phần đọc rõ ràng:

```text
siêu dữ liệu
   ↓
thành phần đọc hiểu quy tắc
   ↓
thành phần đọc kiểm tra / sinh mã / đăng ký / tạo hành vi
```

Nếu không xác định được **ai đọc Annotation** và **đọc để làm gì**, Annotation rất dễ trở thành một nhãn không có quy tắc rõ ràng.

## <a id="annotation-alternatives">Khi nào nên dùng cơ chế khác?</a>

Không nên biến Annotation thành nơi chứa:

- logic nghiệp vụ phức tạp;
- dữ liệu thay đổi liên tục ở thời gian chạy;
- dữ liệu bí mật;
- cấu hình lớn phụ thuộc môi trường;
- dữ liệu cần thay đổi thường xuyên mà không muốn biên dịch lại ứng dụng;
- đồ thị đối tượng động.

Nếu một thông tin phù hợp hơn với đối tượng, cơ sở dữ liệu hoặc tệp cấu hình thì nên dùng đúng công cụ đó.

Một cách chọn đơn giản:

| Nhu cầu | Cơ chế thường rõ ràng hơn |
| --- | --- |
| Gắn thông tin khai báo nhỏ, ổn định vào mã nguồn | Annotation |
| Muốn ép một type cung cấp hành vi cụ thể | `interface` / abstract type |
| Muốn bên gọi chủ động gọi một thao tác | Method/API tường minh |
| Dữ liệu thay đổi theo môi trường triển khai | Tệp cấu hình / biến môi trường / đối tượng cấu hình |
| Dữ liệu thay đổi thường xuyên lúc chạy | Đối tượng / cơ sở dữ liệu / dịch vụ |
| Cần lưu dữ liệu bí mật | Kho lưu trữ bí mật / cơ chế cấu hình bảo mật |

Ví dụ, nếu mục tiêu là bắt một dịch vụ phải có method `audit()`, một `interface` có thể rõ ràng hơn một marker Annotation vì trình biên dịch kiểm tra trực tiếp quy tắc hành vi.

Ngược lại, nếu mục tiêu chỉ là nói **“method này cần được hệ thống audit quan sát”**, Annotation có thể phù hợp hơn vì thông tin đó là siêu dữ liệu về method, không phải method mà đối tượng bắt buộc phải tự triển khai.

## <a id="annotation-end-to-end">Mô hình Annotation từ đầu đến cuối</a>

Toàn bộ module có thể được nhìn như một luồng siêu dữ liệu:

```text
định nghĩa cấu trúc siêu dữ liệu
→ các phần tử + giá trị mặc định
→ @Retention: siêu dữ liệu sống bao lâu
→ @Target: siêu dữ liệu được đặt ở đâu
→ meta-annotations: cấu hình quy tắc của Annotation
→ @Repeatable/@Inherited: ngữ nghĩa lặp lại và kế thừa khi tra cứu
→ bộ xử lý lúc biên dịch hoặc thành phần đọc lúc chạy đọc siêu dữ liệu
```

Với ví dụ xuyên suốt `@Audit`, luồng thiết kế đầy đủ là:

```text
Bài toán
→ cần mô tả method nào phải được audit
        ↓
Kiểu Annotation và các phần tử
→ action, level
        ↓
@Target
→ chỉ METHOD nếu quy tắc chỉ có nghĩa trên method
        ↓
@Retention
→ RUNTIME nếu thành phần ở thời gian chạy cần đọc
→ SOURCE nếu bộ xử lý lúc biên dịch là thành phần đọc duy nhất
        ↓
Thành phần đọc
→ processor / Reflection / framework
        ↓
Hành vi
→ kiểm tra / sinh mã / đăng ký / audit
```

Điểm phải giữ xuyên suốt:

> **Annotation chỉ mô tả siêu dữ liệu. Hành vi đến từ thành phần đọc và xử lý siêu dữ liệu đó.**

Đây cũng là lý do cùng một cú pháp `@Something` có thể dẫn tới các hành vi hoàn toàn khác nhau tùy thành phần đọc.

## <a id="annotation-overuse-pitfalls">Rủi ro khi lạm dụng Annotation</a>

### 1. Luồng xử lý bị ẩn

Mã nguồn như:

```java
@Transactional
@Audited
@Secured
void transfer() {
}
```

có thể kéo theo nhiều hành vi bên ngoài thân method. Điều này không sai, nhưng lập trình viên phải biết framework hoặc thành phần nào đọc từng Annotation và thứ tự/điều kiện xử lý của chúng.

Nếu hành vi quan trọng nhưng không thể lần ra từ quy tắc hoặc tài liệu, Annotation đang làm mã nguồn khó suy luận.

### 2. Phụ thuộc chặt vào framework

Annotation do framework sở hữu có thể làm class nhìn “nhẹ” hơn về mã nguồn nhưng vẫn tạo phụ thuộc mạnh vào ngữ nghĩa của framework.

Hãy phân biệt:

```text
Cơ chế Annotation của Java
≠
hành vi framework được kích hoạt bởi Annotation
```

### 3. Chọn sai `@Retention`

```text
thành phần đọc lúc chạy
+ chính sách SOURCE
→ siêu dữ liệu biến mất trước khi thành phần đọc có thể đọc
```

Ngược lại, dùng `RUNTIME` cho mọi Annotation chỉ vì “an toàn hơn” làm quy tắc rộng hơn mức cần thiết và có thể khiến người đọc hiểu nhầm rằng kiểm tra bằng Reflection lúc chạy là một phần bắt buộc của thiết kế.

### 4. Chọn `@Target` quá rộng

Nếu Annotation chỉ có ý nghĩa trên method nhưng lại cho phép cả `FIELD`, `TYPE`, `PARAMETER`..., trình biên dịch không còn giúp chặn những vị trí sử dụng vô nghĩa.

### 5. Biến Annotation thành một ngôn ngữ cấu hình lớn

Một Annotation có hàng chục phần tử, nhiều giá trị đặc biệt, nhiều cờ tương tác và quy tắc ghi đè phức tạp có thể khó hiểu hơn một đối tượng cấu hình có type rõ ràng.

Ví dụ:

```java
@Feature(
        mode = Mode.AUTO,
        fallback = true,
        cache = true,
        retries = 3,
        async = true,
        strict = false
)
```

Khi siêu dữ liệu bắt đầu mô tả một thuật toán phức tạp hoặc nhiều cờ tương tác, một type thực, builder hoặc đối tượng cấu hình thường làm hành vi rõ hơn và dễ kiểm thử hơn.

### 6. Giá trị mặc định làm thay đổi quy tắc theo thời gian

Giá trị mặc định thuộc kiểu Annotation. Khi thành phần đọc xử lý một cách sử dụng không ghi giá trị tường minh, thay đổi giá trị mặc định của kiểu Annotation có thể làm giá trị quan sát được thay đổi. Vì vậy giá trị mặc định không chỉ là cú pháp tiện lợi; nó là một phần của quy tắc tương thích.

## <a id="annotation-design-checklist">Danh sách kiểm tra khi thiết kế Annotation</a>

Trước khi tạo hoặc sử dụng một Annotation, hãy lần lượt hỏi:

```text
1. Annotation đang mô tả điều gì?
2. Vì sao thông tin này nên nằm sát mã nguồn?
3. Ai là thành phần đọc?
4. Thành phần đọc chạy khi biên dịch hay lúc chạy?
5. Annotation được phép đặt ở đâu?
6. Siêu dữ liệu phải tồn tại đến giai đoạn nào?
7. Có cần lặp lại hay tham gia tra cứu kế thừa class không?
8. Thành phần đọc sẽ kiểm tra, sinh mã, đăng ký hay tạo hành vi gì?
9. API, interface, đối tượng hoặc cấu hình tường minh có dễ hiểu hơn không?
10. Khi đọc mã nguồn, lập trình viên có tìm được quy tắc và hành vi liên quan không?
```

Nếu trả lời được các câu hỏi trên, ta có thể đi từ “thấy một `@Something`” đến một mô hình tư duy đầy đủ:

```text
Annotation
→ siêu dữ liệu có cấu trúc
→ có cấu trúc dữ liệu
→ có quy tắc về vị trí sử dụng
→ có vòng đời
→ có thành phần đọc
→ thành phần đọc tạo ra hành vi
```

Khi đi sâu vào kiểm tra lúc chạy, module `reflection` là nơi sở hữu cơ chế Reflection chi tiết. Module Annotation dừng ở quy tắc siêu dữ liệu, cách thiết kế Annotation và cách các thành phần đọc tương tác với quy tắc đó.
