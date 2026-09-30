# Lập trình hướng đối tượng (OOP) là gì và vì sao cần?

Lập trình hướng đối tượng (**Object-Oriented Programming - OOP**) không bắt đầu từ `class`, `extends` hay `interface`. Nó bắt đầu từ một câu hỏi thiết kế: **nên đặt dữ liệu, quy tắc và trách nhiệm ở đâu để khi chương trình thay đổi, ảnh hưởng không lan ra khắp nơi?**

Hãy hình dung một chức năng thanh toán đơn giản. Ban đầu ta hoàn toàn có thể viết một hàm lớn để đọc giỏ hàng, tính giá, chọn hình thức thanh toán và cập nhật trạng thái đơn hàng. Khi yêu cầu tăng dần, hàm đó sẽ có ngày càng nhiều `if/else`, nhiều nơi cùng đọc hoặc sửa một dữ liệu, và nhiều đoạn mã cùng phải biết những quy tắc giống nhau.

OOP là một cách tổ chức lại chương trình để **đưa dữ liệu và hành vi có liên quan về đúng đối tượng chịu trách nhiệm**, từ đó giúp việc thay đổi dễ kiểm soát hơn.

## <a id="oop-object-collaboration">OOP là gì?</a>

### KHÁI NIỆM — OOP là gì?

Trong OOP, chương trình được tổ chức xoay quanh các **đối tượng (object)**. Mỗi đối tượng thường có:

- một phần **trạng thái (state)** mà nó sở hữu;
- một **trách nhiệm (responsibility)** rõ ràng;
- các **hành vi (behavior)** mà bên ngoài có thể yêu cầu nó thực hiện.

Một đối tượng vì vậy không chỉ là “một nơi chứa các trường (`field`)”. Khi thiết kế một đối tượng, ta nên trả lời được ba câu hỏi:

1. đối tượng này sở hữu trạng thái nào;
2. nó chịu trách nhiệm về điều gì;
3. đoạn mã bên ngoài được phép yêu cầu nó thực hiện những hành vi nào.

Ví dụ trong một luồng thanh toán:

```text
ShoppingCart
→ sở hữu danh sách sản phẩm và các quy tắc thay đổi giỏ hàng

PricingPolicy
→ chịu trách nhiệm tính giá theo một chính sách cụ thể

PaymentMethod
→ chịu trách nhiệm thực hiện thanh toán

CheckoutService
→ phối hợp các đối tượng trên để hoàn thành quá trình thanh toán
```

### VÍ DỤ XUYÊN SUỐT — mô hình nào sẽ được dùng xuyên mô-đun?

Phần lớn mô-đun sẽ quay lại cùng một nhóm vai trò để người học nhìn thấy **một thiết kế tiến hóa qua từng khái niệm**, thay vì mỗi chương bắt đầu từ một miền bài toán hoàn toàn mới:

```text
Checkout / CheckoutService
→ thành phần điều phối luồng checkout

PaymentMethod
→ hợp đồng/hành vi thanh toán có nhiều cách triển khai

CardPayment / WalletPayment
→ các cách triển khai cụ thể để quan sát quan hệ kiểu con, đa hình và phân phối lời gọi

Pricing
→ đối tượng cộng tác có thể được thay thế để quan sát kết hợp đối tượng, ủy quyền và trừu tượng hóa
```

Một vài ví dụ phụ như `Account`, `Team/Player` hoặc `Order/OrderLine` vẫn xuất hiện khi chúng minh họa tốt hơn một cạm bẫy chuyên biệt như điều kiện bất biến hoặc quyền sở hữu/vòng đời. Chúng là ví dụ hỗ trợ, không phải một lộ trình kiến thức riêng tách khỏi luồng checkout chính.

Điểm quan trọng không nằm ở việc ta đã tạo bao nhiêu `class`. Điểm quan trọng là **quy tắc được đặt ở đúng nơi chịu trách nhiệm**. Đoạn mã bên ngoài gửi yêu cầu thông qua phương thức (`method`), còn đối tượng tự bảo vệ trạng thái và thực hiện các quy tắc thuộc về nó.

### VÌ SAO — tại sao không dồn tất cả vào một hàm?

Với chương trình nhỏ, mã lệnh viết theo phong cách thủ tục thường rất dễ đọc. Vấn đề xuất hiện khi nhiều thao tác cùng đọc hoặc thay đổi một trạng thái và nhiều nơi cùng phải biết những quy tắc giống nhau.

Ví dụ, nếu quy tắc giảm giá được viết ở ba nơi khác nhau thì khi chính sách giảm giá thay đổi, cả ba nơi đều phải được sửa. Nếu một nơi bị quên, hành vi của hệ thống sẽ không còn nhất quán.

OOP cố gắng **khoanh vùng ảnh hưởng của thay đổi**:

```text
quy tắc về giá
→ đặt gần thành phần chịu trách nhiệm tính giá

quy tắc đảm bảo trạng thái hợp lệ
→ đặt gần đối tượng sở hữu trạng thái đó

quy tắc thanh toán
→ đặt gần thành phần chịu trách nhiệm thanh toán
```

Vì vậy, giá trị của OOP không nằm ở việc “chia chương trình thành thật nhiều class”, mà nằm ở việc **đặt đúng dữ liệu và hành vi vào đúng nơi chịu trách nhiệm**.

### MỐI LIÊN HỆ — các chặng trong mô-đun nối với nhau thế nào?

Các chương tiếp theo không phải những định nghĩa rời rạc. Chúng hình thành một chuỗi câu hỏi:

```text
Lập trình hướng đối tượng (OOP) là gì và vì sao cần?
        ↓
Một đối tượng sở hữu trạng thái
        ↓
Làm sao ngăn bên ngoài phá trạng thái đó?
Đóng gói và bảo vệ trạng thái hợp lệ
        ↓
Làm sao một kiểu có thể được dùng như một kiểu tổng quát hơn?
Kế thừa và quan hệ kiểu con
        ↓
Làm sao cùng một hợp đồng nhưng mỗi kiểu có hành vi khác nhau?
Đa hình và phân phối động
        ↓
Java chọn phương thức ở lúc biên dịch và lúc chạy như thế nào?
Cách Java chọn phương thức
        ↓
Nếu kế thừa làm các lớp phụ thuộc nhau quá chặt thì sao?
Cộng tác giữa các đối tượng bằng kết hợp và ủy quyền
        ↓
Bên sử dụng thực sự cần biết điều gì?
Trừu tượng hóa và hợp đồng hành vi
        ↓
Kết nối các quyết định trên thành một mô hình thống nhất
Tổng hợp tư duy thiết kế hướng đối tượng
```

Đóng gói, kế thừa, đa hình và trừu tượng hóa thường được gọi là “bốn trụ cột của OOP”. Cách gọi này hữu ích để ghi nhớ, nhưng không nên hiểu chúng là bốn phần độc lập. Chúng liên hệ chặt chẽ và thường được sử dụng cùng với kết hợp đối tượng, ủy quyền và cách chia trách nhiệm rõ ràng.

### CƠ CHẾ — Java hỗ trợ OOP bằng những gì?

Java cung cấp nhiều cơ chế như:

- `class` và đối tượng;
- từ khóa kiểm soát truy cập như `private`, `protected`, `public`;
- `extends` và `implements`;
- overriding;
- overloading;
- cơ chế chọn phương thức khi chạy (**phân phối động - dynamic dispatch**);
- giữ tham chiếu tới đối tượng khác để tạo **kết hợp đối tượng (composition)**.

Nhưng cần phân biệt rõ:

```text
Khái niệm thiết kế
→ đóng gói, trách nhiệm, đa hình, khả năng thay thế, trừu tượng hóa...

Cơ chế của Java
→ `private`, `extends`, `implements`, ghi đè, nạp chồng, phân phối động...
```

Một chương trình dùng đầy đủ từ khóa và cơ chế thường gắn với OOP vẫn có thể thiết kế kém. Ngược lại, thiết kế tốt bắt đầu từ việc hiểu trách nhiệm và mối quan hệ giữa các đối tượng, sau đó mới chọn cơ chế Java phù hợp.

### THỰC HÀNH — câu hỏi tự kiểm tra

Khi nhìn vào một `class`, đừng chỉ hỏi “class này có những trường và phương thức nào?”. Hãy thử hỏi:

1. trạng thái nào thực sự thuộc về nó;
2. quy tắc nào nó phải tự bảo vệ;
3. đoạn mã nào bên ngoài đang biết quá nhiều chi tiết nội bộ của nó.

## <a id="oop-boundaries">Ranh giới trách nhiệm</a>

### KHÁI NIỆM — ranh giới là gì?

Ranh giới của một đối tượng là giới hạn giữa:

```text
những gì đối tượng tự chịu trách nhiệm
và
những gì bên ngoài được phép biết hoặc yêu cầu
```

Một ranh giới tốt chỉ công khai những hành vi cần thiết và giữ các chi tiết triển khai ở bên trong.

Ví dụ, đoạn mã bên ngoài nên có thể nói:

```java
checkoutService.checkout(cart);
```

thay vì phải tự làm toàn bộ các bước:

```text
đọc từng trường của ShoppingCart
→ tự tính giá
→ tự kiểm tra loại thanh toán
→ tự cập nhật trạng thái Order
```

Phiên bản đầu giúp bên gọi chỉ cần biết **ý định cần thực hiện**. Phiên bản sau buộc bên gọi phải biết quá nhiều chi tiết.

### VÌ SAO — ranh giới trách nhiệm quan trọng?

Khi dữ liệu và quy tắc liên quan được đặt cùng một nơi, ta dễ biết phải tìm logic ở đâu. Đây là **độ gắn kết nội bộ (cohesion)** tốt.

Ngược lại, nếu nhiều đối tượng phải biết chi tiết bên trong của nhau, mức **phụ thuộc lẫn nhau (coupling)** tăng lên. Khi một bên thay đổi, nhiều bên khác có thể phải sửa theo.

OOP tốt không có nghĩa là càng nhiều class càng tốt. Nếu tách một trách nhiệm thành nhiều lớp trung gian nhưng các lớp đó không tạo ra ranh giới hay trách nhiệm mới, chương trình chỉ trở nên khó đọc hơn.

### MỐI LIÊN HỆ — từ ranh giới đến đóng gói

Ngay khi ta nói “đối tượng này sở hữu trạng thái”, câu hỏi tiếp theo là:

> Ai được phép thay đổi trạng thái đó, và được thay đổi theo cách nào?

Đây chính là vấn đề mà **đóng gói (encapsulation)** giải quyết.

Một dấu hiệu đáng chú ý là:

```text
nếu bên gọi phải lấy rất nhiều trường ra ngoài để thực hiện một quy tắc
→ có thể quy tắc đó đang nằm sai ranh giới trách nhiệm
```

### THỰC HÀNH — ba câu hỏi về ranh giới

Với mỗi `class`, hãy thử trả lời:

1. trạng thái nào thực sự thuộc trách nhiệm của class này;
2. bên ngoài cần yêu cầu hành vi nào thay vì lấy dữ liệu thô nào;
3. nếu cách lưu dữ liệu bên trong thay đổi, có bao nhiêu đoạn mã bên ngoài phải sửa theo.

## <a id="oop-vs-procedural">OOP và lập trình thủ tục</a>

### KHÁI NIỆM — hai cách tổ chức khác nhau ở đâu?

Lập trình theo thủ tục thường tổ chức chương trình quanh **các thao tác và luồng xử lý dữ liệu**. OOP thường tổ chức chương trình quanh **đối tượng và trách nhiệm**.

| Câu hỏi | Theo thủ tục | Theo OOP |
| --- | --- | --- |
| Đơn vị chính | Hàm / thủ tục | Đối tượng / trách nhiệm |
| Trạng thái | Thường được truyền qua các hàm | Thường do đối tượng sở hữu |
| Thay đổi thường được khoanh vùng theo | Luồng xử lý | Trách nhiệm / kiểu đối tượng |
| Phù hợp tự nhiên với | Thuật toán, pipeline, biến đổi dữ liệu tuyến tính | Miền có trạng thái, nhiều đối tượng cộng tác, hành vi thay đổi theo kiểu |

### VÌ SAO — OOP không phải lúc nào cũng tốt hơn

Một phép biến đổi dữ liệu ngắn hoặc một thuật toán thuần có thể rõ ràng hơn nếu viết thành hàm. Nếu ép mọi thứ thành một cây kế thừa gồm nhiều đối tượng, chương trình có thể trở nên rườm rà mà không nhận được lợi ích gì.

Ngược lại, khi bài toán có:

- trạng thái tồn tại lâu;
- nhiều quy tắc cần được bảo vệ;
- nhiều cách triển khai khác nhau;
- nhiều thành phần phải phối hợp với nhau;

thì việc chia trách nhiệm theo đối tượng thường giúp suy luận và thay đổi dễ hơn.

Java hỗ trợ cả hai phong cách. Một chương trình Java tốt hoàn toàn có thể dùng đối tượng để sở hữu trách nhiệm và dùng hàm hoặc lambda cho những phép biến đổi cục bộ.

### MINH CHỨNG — nên quan sát điều gì khi học OOP?

Đừng đánh giá một thiết kế OOP bằng số lượng class. Hãy xem một thay đổi có được khoanh vùng tốt hơn hay không.

Ví dụ, nếu thêm một cách tính giá mới mà `Checkout` không phải sửa, đó là minh chứng tốt hơn nhiều so với việc chỉ chứng minh rằng Java có từ khóa `interface`.

### THỰC HÀNH — câu hỏi xuyên suốt mô-đun

Khi học từng khái niệm tiếp theo, hãy luôn hỏi:

> “Khái niệm này đang giải quyết vấn đề nào vừa xuất hiện trong mô hình hiện tại?”

Chương tiếp theo bắt đầu từ vấn đề đầu tiên: **một đối tượng đã sở hữu trạng thái thì làm sao ngăn bên ngoài thay đổi trạng thái đó một cách tùy ý?**
