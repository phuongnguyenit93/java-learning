# Trừu tượng hóa và hợp đồng hành vi

Sau khi đi qua đóng gói, kế thừa, đa hình và kết hợp đối tượng, ta có thể đặt câu hỏi cuối cùng:

> Bên sử dụng thực sự cần biết điều gì để dùng một khả năng nào đó?

**Trừu tượng hóa (abstraction)** giữ lại phần cần thiết đó và loại bỏ những chi tiết triển khai không cần thiết khỏi cách suy nghĩ của bên sử dụng.

## <a id="abstraction-model">Trừu tượng hóa là gì?</a>

### KHÁI NIỆM — trừu tượng hóa là gì?

Abstraction là một mô hình hoặc hợp đồng chỉ công khai những khái niệm và hành vi mà bên sử dụng thực sự cần, đồng thời giấu những chi tiết không liên quan ở phía sau ranh giới.

Ví dụ với việc tính giá, `Checkout` có thể chỉ cần biết:

```java
interface Pricing {
    int price(int base);
}
```

`Checkout` không cần biết cách triển khai cụ thể tính giảm giá bằng:

- công thức;
- bảng quy tắc;
- database;
- dịch vụ bên ngoài.

Miễn là các chi tiết đó không thuộc hợp đồng `Pricing`, chúng không cần xuất hiện trong cách suy nghĩ của `Checkout`.

### MỐI LIÊN HỆ — trừu tượng hóa và đóng gói bổ sung nhau

Có thể phân biệt ngắn gọn như sau:

```text
Abstraction
→ bên sử dụng cần nhìn thấy điều gì?

Encapsulation
→ bên sử dụng không được tùy ý truy cập hoặc thay đổi điều gì?
```

Một API có thể dùng `private` rất nhiều nhưng trừu tượng hóa vẫn tệ nếu nó bắt bên ngoài phải biết quá nhiều thao tác chi tiết.

Ngược lại, một `interface` rất nhỏ cũng chưa chắc là trừu tượng hóa tốt nếu những chi tiết quan trọng như lỗi, vòng đời hay thứ tự vẫn bị rò rỉ một cách khó hiểu.

### CƠ CHẾ — trừu tượng hóa không đồng nghĩa với từ khóa `abstract`

`interface` và `abstract class` là những cơ chế phổ biến để biểu diễn trừu tượng hóa trong Java.

Nhưng trừu tượng hóa không đồng nghĩa với từ khóa `abstract`.

Một class cụ thể hoặc thậm chí một hàm cũng có thể tạo ra trừu tượng hóa tốt nếu hợp đồng của nó rõ ràng và bên sử dụng không cần biết chi tiết triển khai.

### HỢP ĐỒNG — trừu tượng hóa không chỉ là danh sách phương thức

Một trừu tượng hữu ích thường cần làm rõ nhiều hơn tên phương thức. Tùy miền bài toán, hợp đồng có thể bao gồm:

```text
hành vi được cung cấp
+
input hợp lệ / precondition
+
kết quả hoặc postcondition
+
ngữ nghĩa khi thất bại
+
side effects
+
ràng buộc vòng đời / thứ tự khi chúng ảnh hưởng tới tính đúng đắn
```

Ví dụ:

```java
interface Storage {
    void save(Data data);
}
```

chưa tự nói cho bên gọi biết `save` có thể thất bại thế nào, có tính lũy đẳng (idempotent) hay không, có yêu cầu transaction/vòng đời nào không. Những chi tiết ảnh hưởng cách dùng đúng **là một phần của hợp đồng**, không nên bị giấu chỉ vì cách triển khai nằm phía sau `interface`.

Mô-đun `abstract-interface` sẽ đi sâu hơn vào việc chọn `interface` hay `abstract class`. Ở mô-đun này, trọng tâm là **vai trò thiết kế của trừu tượng hóa**.

## <a id="program-to-abstraction">Lập trình dựa trên trừu tượng</a>

### KHÁI NIỆM

“Program to abstraction” có nghĩa là thành phần sử dụng nên phụ thuộc vào **vai trò hoặc hợp đồng ổn định**, thay vì phụ thuộc vào một cách triển khai cụ thể nếu điều đó không cần thiết.

```java
final class Checkout {
    private final Pricing pricing;

    Checkout(Pricing pricing) {
        this.pricing = pricing;
    }
}
```

`Checkout` biết `Pricing`.

Nó không cần biết chính xác đối tượng được truyền vào là:

```text
Regular
Discount
SeasonalPricing
```

### VÌ SAO — giảm lượng kiến thức mà bên sử dụng phải biết

Nếu cách triển khai thay đổi nhưng hợp đồng vẫn giữ nguyên, `Checkout` có thể không cần sửa.

Đây là nền tảng để:

- đa hình mang lại khả năng thay thế;
- kết hợp đối tượng cho phép thay đối tượng cộng tác;
- khi kiểm thử, dễ thay phần triển khai thật bằng phần triển khai giả khi cần.

### ĐÁNH ĐỔI — không tạo `interface` cho mọi class một cách máy móc

Một tầng trừu tượng chỉ đáng tồn tại khi nó biểu diễn một vai trò hoặc ranh giới có ý nghĩa.

Ví dụ nó có thể hữu ích khi:

- có nhiều cách triển khai;
- cần một điểm thay thế rõ ràng;
- cần ranh giới kiến trúc;
- cần tách bên sử dụng khỏi chi tiết hạ tầng.

Nếu chỉ tạo:

```text
FooInterface
FooImpl
```

cho mọi class mà không có lý do cụ thể, ta chỉ thêm một lớp trung gian mà chưa chắc giảm phụ thuộc thực sự.

### THỰC HÀNH — kiểm tra hướng phụ thuộc

Hãy hỏi thành phần sử dụng:

> Nó thực sự cần khả năng nào, hay nó đang phụ thuộc vào class cụ thể chỉ vì tiện?

Nếu bên sử dụng chỉ cần một tập nhỏ hành vi ổn định, đó có thể là dấu hiệu nên phụ thuộc vào một trừu tượng nhỏ hơn.

## <a id="abstraction-leak">Rò rỉ trừu tượng</a>

### KHÁI NIỆM — rò rỉ trừu tượng là gì?

Abstraction bị **rò rỉ (leak)** khi bên sử dụng vẫn phải hiểu chi tiết triển khai mới có thể dùng hợp đồng đúng cách.

Ví dụ một API tưởng như đơn giản:

```java
interface ReportStore {
    void save(Report report);
}
```

nhưng nếu bên gọi phải tự biết rằng cách triển khai A chỉ chấp nhận tên file không dấu, cách triển khai B yêu cầu gọi `flush()` trước khi kết thúc, còn cách triển khai C có giới hạn kích thước không được nói trong hợp đồng, thì trừu tượng hóa đã không che được những chi tiết cần thiết để sử dụng đúng.

Ví dụ:

- API chỉ nói `save`, nhưng bên gọi phải biết nhà cung cấp cụ thể mới xử lý lỗi đúng;
- collection che giấu cách lưu dữ liệu nhưng hiệu năng thay đổi rất lớn theo cách triển khai;
- lớp trừu tượng cho tài nguyên che vòng đời đến mức bên gọi không biết phải `close`;
- hợp đồng không nói gì về thứ tự phần tử hoặc tính an toàn khi chạy đa luồng (`thread-safety`) dù chúng ảnh hưởng trực tiếp tới tính đúng đắn.

### VÌ SAO — không trừu tượng nào che được mọi chi tiết

Mục tiêu của trừu tượng hóa không phải là giấu tất cả.

Những ràng buộc ảnh hưởng tới:

- tính đúng đắn;
- cách xử lý lỗi;
- vòng đời;
- hiệu năng quan trọng;

nên được công khai hoặc ghi rõ trong hợp đồng.

Chỉ những chi tiết triển khai không liên quan mới nên được giấu đi.

### PHÂN BIỆT — trừu tượng hóa, đóng gói và che giấu thông tin

Ba ý này liên quan nhưng có trọng tâm khác nhau:

```text
Trừu tượng hóa (Abstraction)
→ chọn mô hình/hợp đồng tối thiểu mà bên gọi cần suy nghĩ tới

Đóng gói (Encapsulation)
→ đặt trạng thái + hành vi sau một ranh giới được kiểm soát

Che giấu thông tin (Information hiding)
→ che những quyết định thiết kế có khả năng thay đổi để bên gọi không phụ thuộc không cần thiết
```

Trong mã thực tế chúng thường hỗ trợ nhau, nhưng hiểu khác biệt giúp tránh suy luận sai rằng chỉ cần `private` hoặc `interface` là tự động có một trừu tượng tốt.

## <a id="oop-synthesis">Tổng hợp tư duy thiết kế hướng đối tượng</a>

Sau toàn bộ mô-đun, có thể nhìn OOP như một dòng suy nghĩ liên tục:

```text
Đối tượng sở hữu trạng thái và trách nhiệm
        ↓
Đóng gói
giữ các thay đổi trạng thái hợp lệ
        ↓
Quan hệ kiểu con / Kế thừa
tạo quan hệ kiểu chung và kiểu cụ thể
        ↓
Đa hình
cho phép một hợp đồng có nhiều hành vi
        ↓
Ghi đè + Phân phối động
là cơ chế Java giúp hành vi của kiểu con được chọn lúc chạy
        ↓
Kết hợp đối tượng / Ủy quyền
cho phép thay đổi hành vi thông qua đối tượng cộng tác thay vì kéo dài cây kế thừa
        ↓
Trừu tượng hóa
giữ lại hợp đồng cần thiết và giảm chi tiết mà bên sử dụng phải biết
```

Nạp chồng (`Overloading`) xuất hiện trong mô-đun để phân biệt với ghi đè (`Overriding`), vì hai cơ chế này rất dễ bị nhầm. Nó **không phải một trụ cột OOP độc lập**.

### MÔ HÌNH RA QUYẾT ĐỊNH — kết hợp các công cụ OOP như thế nào?

Các khái niệm trong mô-đun không tạo thành một danh sách để áp dụng máy móc. Chúng trả lời những câu hỏi thiết kế khác nhau:

```text
Đối tượng có trạng thái cần tự bảo vệ không?
→ dùng đóng gói để kiểm soát các chuyển đổi trạng thái hợp lệ

Một kiểu có thật sự phải dùng được thay cho kiểu tổng quát hơn không?
→ cân nhắc quan hệ kiểu con và kế thừa

Bên gọi cần cùng một hợp đồng nhưng nhiều cách thực hiện khác nhau không?
→ dùng đa hình và khả năng thay thế

Mục tiêu chỉ là ghép hoặc thay đổi một phần hành vi mà không cần quan hệ kiểu con?
→ ưu tiên kết hợp đối tượng và ủy quyền

Bên sử dụng đang biết quá nhiều chi tiết triển khai không?
→ tạo một trừu tượng/hợp đồng ổn định ở đúng ranh giới
```

Kế thừa và kết hợp đối tượng không loại trừ nhau trong toàn bộ hệ thống. Một thiết kế có thể dùng kế thừa ở nơi thật sự có quan hệ kiểu con, đồng thời dùng kết hợp ở nơi các đối tượng chỉ cần cộng tác. Trừu tượng hóa có thể bao quanh cả hai để giữ bên sử dụng phụ thuộc vào hợp đồng thay vì chi tiết triển khai.

### ĐÁNH ĐỔI — khi nào không cần đẩy bài toán thành OOP phức tạp?

Nếu bài toán chỉ là một phép biến đổi dữ liệu ngắn, một thuật toán thuần hoặc một luồng xử lý tuyến tính không có trạng thái dài hạn, quy tắc bất biến hay nhiều biến thể cộng tác, việc thêm nhiều lớp, interface và tầng ủy quyền có thể chỉ làm tăng độ phức tạp.

Mục tiêu của OOP không phải là tạo nhiều đối tượng nhất có thể. Mục tiêu là tạo **ranh giới trách nhiệm giúp chương trình dễ hiểu và dễ thay đổi hơn**. Nếu một lớp trừu tượng, hệ phân cấp hoặc đối tượng cộng tác không tạo ra ranh giới hay điểm biến đổi có ý nghĩa, hãy xem lại liệu nó có thật sự cần thiết hay không.

### THỰC HÀNH — tiêu chí tự kiểm tra sau mô-đun

Khi nhìn một thiết kế OOP, đừng đếm số lượng class hoặc interface. Hãy thử trả lời năm câu hỏi:

1. mỗi trách nhiệm có nơi sở hữu rõ ràng không;
2. trạng thái và invariant có được đối tượng phù hợp bảo vệ không;
3. các kiểu con có thực sự thay thế được nhau theo hợp đồng không;
4. kế thừa và kết hợp đối tượng có được chọn vì đúng bản chất quan hệ không;
5. bên sử dụng có đang phụ thuộc vào những chi tiết triển khai không cần thiết không.

Nếu trả lời được năm câu hỏi này, bạn đã có một mô hình tư duy về OOP hữu ích hơn nhiều so với việc chỉ học thuộc lòng “bốn trụ cột”.
