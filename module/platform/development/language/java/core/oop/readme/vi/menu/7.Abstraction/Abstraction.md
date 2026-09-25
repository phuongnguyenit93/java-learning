# Trừu tượng hóa (Abstraction)

Sau khi đi qua đóng gói, kế thừa, đa hình và composition, ta có thể đặt câu hỏi cuối cùng:

> Bên sử dụng thực sự cần biết điều gì để dùng một khả năng nào đó?

**Trừu tượng hóa (abstraction)** giữ lại phần cần thiết đó và loại bỏ những chi tiết triển khai không cần thiết khỏi cách suy nghĩ của bên sử dụng.

## <a id="abstraction-model">Trừu tượng hóa là gì?</a>

### KHÁI NIỆM — abstraction là gì?

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

### MỐI LIÊN HỆ — abstraction và encapsulation bổ sung nhau

Có thể phân biệt ngắn gọn như sau:

```text
Abstraction
→ bên sử dụng cần nhìn thấy điều gì?

Encapsulation
→ bên sử dụng không được tùy ý truy cập hoặc thay đổi điều gì?
```

Một API có thể dùng `private` rất nhiều nhưng abstraction vẫn tệ nếu nó bắt bên ngoài phải biết quá nhiều thao tác chi tiết.

Ngược lại, một interface rất nhỏ cũng chưa chắc là abstraction tốt nếu những chi tiết quan trọng như lỗi, vòng đời hay thứ tự vẫn bị rò rỉ một cách khó hiểu.

### CƠ CHẾ — abstraction không đồng nghĩa với keyword `abstract`

`interface` và `abstract class` là những cơ chế phổ biến để biểu diễn abstraction trong Java.

Nhưng abstraction không đồng nghĩa với keyword `abstract`.

Một concrete class hoặc thậm chí một function cũng có thể tạo ra abstraction tốt nếu hợp đồng của nó rõ ràng và bên sử dụng không cần biết chi tiết triển khai.

Module `abstract-interface` sẽ đi sâu hơn vào việc chọn `interface` hay `abstract class`. Ở module này, trọng tâm là **vai trò thiết kế của abstraction**.

## <a id="program-to-abstraction">Program to Abstraction</a>

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

Nó không cần biết chính xác object được truyền vào là:

```text
Regular
Discount
SeasonalPricing
```

### VÌ SAO — giảm lượng kiến thức mà bên sử dụng phải biết

Nếu cách triển khai thay đổi nhưng hợp đồng vẫn giữ nguyên, `Checkout` có thể không cần sửa.

Đây là nền tảng để:

- polymorphism mang lại khả năng thay thế;
- composition cho phép thay đối tượng cộng tác;
- khi kiểm thử, dễ thay phần triển khai thật bằng phần triển khai giả khi cần.

### ĐÁNH ĐỔI — không tạo interface cho mọi class một cách máy móc

Một abstraction layer chỉ đáng tồn tại khi nó biểu diễn một vai trò hoặc ranh giới có ý nghĩa.

Ví dụ nó có thể hữu ích khi:

- có nhiều cách triển khai;
- cần một điểm thay thế rõ ràng;
- cần ranh giới kiến trúc;
- cần tách consumer khỏi chi tiết hạ tầng.

Nếu chỉ tạo:

```text
FooInterface
FooImpl
```

cho mọi class mà không có lý do cụ thể, ta chỉ thêm một lớp trung gian mà chưa chắc giảm phụ thuộc thực sự.

### THỰC HÀNH — kiểm tra hướng phụ thuộc

Hãy hỏi thành phần sử dụng:

> Nó thực sự cần capability nào, hay nó đang phụ thuộc vào concrete class chỉ vì tiện?

Nếu bên sử dụng chỉ cần một tập nhỏ hành vi ổn định, đó có thể là dấu hiệu nên phụ thuộc vào một abstraction nhỏ hơn.

## <a id="abstraction-leak">Leaky Abstraction</a>

### KHÁI NIỆM — abstraction leak là gì?

Abstraction bị **rò rỉ (leak)** khi bên sử dụng vẫn phải hiểu chi tiết triển khai mới có thể dùng hợp đồng đúng cách.

Ví dụ:

- API chỉ nói `save`, nhưng bên gọi phải biết nhà cung cấp cụ thể mới xử lý lỗi đúng;
- collection che giấu cách lưu dữ liệu nhưng hiệu năng thay đổi rất lớn theo cách triển khai;
- lớp trừu tượng cho tài nguyên che vòng đời đến mức bên gọi không biết phải `close`;
- hợp đồng không nói gì về thứ tự phần tử hoặc tính an toàn khi chạy đa luồng (`thread-safety`) dù chúng ảnh hưởng trực tiếp tới tính đúng đắn.

### VÌ SAO — không abstraction nào che được mọi chi tiết

Mục tiêu của abstraction không phải là giấu tất cả.

Những ràng buộc ảnh hưởng tới:

- tính đúng đắn;
- cách xử lý lỗi;
- vòng đời;
- hiệu năng quan trọng;

nên được công khai hoặc ghi rõ trong hợp đồng.

Chỉ những chi tiết triển khai không liên quan mới nên được giấu đi.

### MỐI LIÊN HỆ — nối lại toàn bộ module OOP

Sau toàn bộ module, có thể nhìn OOP như một dòng suy nghĩ liên tục:

```text
Object sở hữu trạng thái và trách nhiệm
        ↓
Encapsulation
giữ các thay đổi trạng thái hợp lệ
        ↓
Subtyping / Inheritance
tạo quan hệ kiểu chung và kiểu cụ thể
        ↓
Polymorphism
cho phép một hợp đồng có nhiều hành vi
        ↓
Overriding + Dynamic Dispatch
là cơ chế Java giúp hành vi của kiểu con được chọn ở runtime
        ↓
Composition / Delegation
cho phép thay đổi hành vi thông qua đối tượng cộng tác thay vì kéo dài cây kế thừa
        ↓
Abstraction
giữ lại hợp đồng cần thiết và giảm chi tiết mà bên sử dụng phải biết
```

`Overloading` xuất hiện trong module để phân biệt với `Overriding`, vì hai cơ chế này rất dễ bị nhầm. Nó **không phải một trụ cột OOP độc lập**.

### THỰC HÀNH — tiêu chí tự kiểm tra sau module

Khi nhìn một thiết kế OOP, đừng đếm số lượng class hoặc interface. Hãy thử trả lời năm câu hỏi:

1. mỗi trách nhiệm có nơi sở hữu rõ ràng không;
2. trạng thái và invariant có được đối tượng phù hợp bảo vệ không;
3. các kiểu con có thực sự thay thế được nhau theo hợp đồng không;
4. inheritance và composition có được chọn vì đúng bản chất quan hệ không;
5. bên sử dụng có đang phụ thuộc vào những chi tiết triển khai không cần thiết không.

Nếu trả lời được năm câu hỏi này, bạn đã có một mô hình tư duy về OOP hữu ích hơn nhiều so với việc chỉ học thuộc lòng “bốn trụ cột”.
