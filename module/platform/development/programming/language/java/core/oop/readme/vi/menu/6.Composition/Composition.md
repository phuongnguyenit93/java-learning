# Cộng tác giữa các đối tượng bằng kết hợp và ủy quyền

Kế thừa trả lời câu hỏi:

> “Đối tượng này có phải là một kiểu con của kiểu kia không?”

Kết hợp đối tượng trả lời một câu hỏi khác:

> “Đối tượng này cần cộng tác với đối tượng nào để hoàn thành trách nhiệm của nó?”

## <a id="composition-has-a">Kết hợp đối tượng (Composition)</a>

### KHÁI NIỆM

Với kết hợp đối tượng, một đối tượng giữ tham chiếu tới đối tượng khác và giao một phần công việc cho đối tượng đó.

```java
final class Checkout {
    private final Pricing pricing;

    Checkout(Pricing pricing) {
        this.pricing = pricing;
    }

    int total(int base) {
        return pricing.price(base);
    }
}
```

Ta có thể nói:

```text
Checkout has-a Pricing
```

`Checkout` **có một** `Pricing`, chứ `Checkout` không phải là một loại `Pricing`.

Vì vậy `Checkout extends Pricing` sẽ không thể hiện đúng mối quan hệ của bài toán.

### THUẬT NGỮ — “composition” có hai cách dùng phổ biến

Trong thảo luận OOP, **kết hợp đối tượng (object composition)** thường được dùng theo nghĩa rộng: xây một đối tượng bằng cách ghép các đối tượng cộng tác lại với nhau thay vì kế thừa phần triển khai.

Trong mô hình hóa UML/đối tượng, **composition** còn có nghĩa hẹp hơn: một quan hệ toàn thể–thành phần với quyền sở hữu mạnh và vòng đời của thành phần gắn chặt với toàn thể.

Chapter này dùng cả hai ngữ cảnh, vì vậy cần nhìn câu hỏi đang được trả lời:

```text
composition như kỹ thuật thiết kế
→ ghép đối tượng / ủy quyền hành vi thay vì extends

composition như quan hệ UML
→ quyền sở hữu mạnh + ngữ nghĩa vòng đời
```

Hai cách dùng liên quan nhưng **không đồng nghĩa hoàn toàn**. Một đối tượng giữ một đối tượng cộng tác để ủy quyền chưa đủ để kết luận đó là composition theo UML.

### VÌ SAO — dùng lại hoặc thay đổi hành vi mà không cần kế thừa

Nếu mục tiêu là thay cách tính giá, composition cho phép truyền vào các cách triển khai khác nhau:

```text
Regular
Discount
SeasonalPricing
...
```

mà không cần biến `Checkout` thành một cây kế thừa gồm nhiều class con.

Điểm thay đổi được khoanh vùng ở đối tượng cộng tác (`Pricing`) thay vì ở chính `Checkout`.

### MINH CHỨNG — `CompositionController#strategySwap()`

Ví dụ thực thi tạo:

```text
new Checkout(new Regular())
new Checkout(new Discount())
```

Class `Checkout` không thay đổi. Chỉ đối tượng `Pricing` được thay thế.

Kết quả cho thấy hành vi tính giá thay đổi trong khi kiểu của `Checkout` vẫn giữ nguyên.

## <a id="object-relationships">Quan hệ giữa các đối tượng</a>

Các thuật ngữ này mô tả **mức độ quan hệ giữa các đối tượng trong thiết kế**. Chúng không phải bốn tính năng lúc chạy riêng biệt của Java.

| Quan hệ | Ý nghĩa thường gặp | Cách biểu diễn thường thấy trong Java |
| --- | --- | --- |
| Dependency | Dùng tạm một đối tượng để hoàn thành thao tác | Tham số / biến cục bộ |
| Association | Hai đối tượng có quan hệ lâu dài hơn | Trường / tham chiếu |
| Aggregation | Sở hữu yếu hoặc có thể chia sẻ | Trường / tham chiếu + quy ước vòng đời |
| Composition | Sở hữu mạnh, vòng đời của thành phần gắn với toàn thể | Trường / tham chiếu + quy tắc tạo/sở hữu |

Java chủ yếu chỉ nhìn thấy **tham chiếu**. Ý nghĩa “sở hữu yếu” hay “sở hữu mạnh” đến từ cách ta thiết kế API và quản lý vòng đời đối tượng.

### CẠM BẪY — tham chiếu `final` không tự tạo quan hệ hợp thành

Ví dụ:

```java
class Team {
    private final Player captain;

    Team(Player captain) {
        this.captain = captain;
    }
}
```

`final` chỉ đảm bảo trường `captain` không bị gán sang tham chiếu khác sau khi khởi tạo. Nó **không chứng minh** rằng:

- `Team` là bên sở hữu duy nhất của `Player`;
- `Player` không được chia sẻ cho đối tượng khác;
- `Player` phải chết theo vòng đời của `Team`;
- quan hệ này chắc chắn là composition theo nghĩa mô hình hóa.

Quyền sở hữu là **ngữ nghĩa của thiết kế**, không phải thuộc tính mà Java suy ra từ một từ khóa đơn lẻ.

### VÌ SAO — không thể nhìn cú pháp rồi kết luận ngay quan hệ

Hai class cùng có trường tham chiếu tới đối tượng khác chưa đủ để kết luận đó là aggregation hay composition.

Ta cần xem:

- ai tạo đối tượng con;
- đối tượng con có thể tồn tại độc lập không;
- đối tượng con có thể được chia sẻ cho nhiều bên sở hữu không;
- ai kiểm soát vòng đời của nó.

## <a id="association-dependency">Phụ thuộc (Dependency) và liên kết (Association)</a>

Một dependency có thể chỉ tồn tại trong một thao tác:

```java
Receipt checkout(PaymentGateway gateway) {
    return gateway.charge(...);
}
```

`PaymentGateway` được truyền vào để dùng cho lời gọi hiện tại.

Nếu một đối tượng cộng tác là phần ổn định của trạng thái đối tượng, trường thường phù hợp hơn:

```java
class CheckoutService {
    private final PaymentGateway gateway;
}
```

Trong trường hợp này, `CheckoutService` giữ quan hệ lâu dài hơn với `PaymentGateway`.

Một đối tượng cộng tác lưu trong trường cũng chưa chắc bị sở hữu mạnh. Hai service hoàn toàn có thể giữ tham chiếu tới cùng một đối tượng có thể thay đổi. Khi đó cần suy nghĩ thêm về dùng chung tham chiếu, thay đổi trạng thái dùng chung và hợp đồng vòng đời thay vì chỉ nhìn cú pháp trường/tham chiếu.

### ĐÁNH ĐỔI — không cần gắn nhãn cho mọi tham chiếu

Không cần ép mọi tham số thành “dependency” và mọi trường thành “association” nếu việc phân loại không giúp ích cho thiết kế.

Mục tiêu là hiểu **mức phụ thuộc và vòng đời**, không phải gắn thuật ngữ UML cho mọi đối tượng.

## <a id="ownership-lifecycle">Quyền sở hữu và vòng đời</a>

### Kết tập (Aggregation) — sở hữu yếu hơn

Part có thể tồn tại độc lập hoặc được chia sẻ.

Ví dụ:

```text
Team
→ tham chiếu Player

nhưng Player vẫn có thể tồn tại ngay cả khi Team không còn
```

### Hợp thành (Composition) — sở hữu mạnh hơn

Whole thường chịu trách nhiệm tạo và quản lý part.

Ví dụ:

```text
Order
→ sở hữu các OrderLine

OrderLine thường chỉ có ý nghĩa khi thuộc một Order cụ thể
```

### CƠ CHẾ — Java không tự áp đặt quyền sở hữu

Garbage Collector chỉ quan tâm đối tượng còn được tham chiếu hay không. Nó không biết khái niệm UML như aggregation hay composition.

Vì vậy quan hệ sở hữu phải được thể hiện bằng:

- hàm khởi tạo hoặc factory;
- mức độ có thể thay đổi;
- cách công khai tham chiếu;
- có cho phép chia sẻ đối tượng hay không;
- quy tắc tạo và giữ đối tượng.

## <a id="composition-vs-inheritance">Kết hợp đối tượng và kế thừa</a>

Hai cơ chế giải quyết hai loại quan hệ khác nhau:

| Câu hỏi | Inheritance | Composition |
| --- | --- | --- |
| Quan hệ chính | is-a | has-a / cộng tác với |
| Dùng lại hành vi | Kế thừa phần triển khai | Ủy quyền |
| Thay đổi hành vi | Ghi đè ở kiểu con | Thay đối tượng cộng tác |
| Mức phụ thuộc | Chặt với class cha | Phụ thuộc vào hợp đồng của đối tượng cộng tác |
| Thay cách triển khai lúc chạy | Thường khó tự nhiên hơn | Tự nhiên nếu đối tượng cộng tác được truyền vào |

### Cách quyết định đơn giản

Nếu đối tượng **thực sự cần được dùng như một kiểu tổng quát hơn (supertype)**, kế thừa có thể phù hợp.

Nếu mục tiêu chủ yếu là:

- dùng lại hành vi;
- thay chiến lược;
- thay cách triển khai;

thì nên xem xét kết hợp đối tượng trước.

Câu “favor composition over inheritance” không có nghĩa là “không bao giờ dùng kế thừa”. Nó nhắc ta không nên dùng kế thừa chỉ như một mẹo để tái sử dụng vài dòng mã khi không có quan hệ kiểu con thực sự.

## <a id="delegation">Ủy quyền (Delegation)</a>

**Ủy quyền (delegation)** là việc một đối tượng nhận yêu cầu rồi chuyển một phần công việc cho đối tượng cộng tác phù hợp.

```java
int total(int base) {
    return pricing.price(base);
}
```

`Checkout` không cần biết công thức giảm giá. Nó chỉ cần biết hợp đồng `Pricing`.

### MỐI LIÊN HỆ — ủy quyền dẫn tới trừu tượng hóa

Để kết hợp đối tượng linh hoạt, `Checkout` không nên phụ thuộc vào chi tiết của `Discount` hay `Regular`.

Nó nên phụ thuộc vào một hợp đồng ổn định như `Pricing`.

Đây chính là cầu nối sang **trừu tượng hóa (abstraction)**.

### PHÂN BIỆT — kết hợp đối tượng và ủy quyền

Hai thuật ngữ thường đi cùng nhau nhưng không đồng nghĩa:

```text
Composition
→ A giữ/có đối tượng cộng tác B như một phần của cấu trúc đối tượng

Delegation
→ A nhận yêu cầu rồi giao một trách nhiệm cụ thể cho B thực hiện
```

Trong ví dụ xuyên suốt của mô-đun, `Checkout` có thể giữ một `Pricing` mà không có nghĩa mọi hành vi của `Checkout` đều thuộc về `Pricing`. Khi `Checkout.total(...)` gọi `pricing.price(...)`, **chính trách nhiệm tính giá** được ủy quyền sang đối tượng cộng tác `Pricing`.

```text
Checkout
→ sở hữu trách nhiệm điều phối checkout

Pricing
→ sở hữu trách nhiệm tính giá

Checkout.total(...)
→ ủy quyền phần tính giá cho Pricing.price(...)
```

### ĐÁNH ĐỔI

Nếu mỗi thao tác đơn giản đều được chuyển qua quá nhiều lớp trung gian, mã sẽ trở nên rườm rà.

Ủy quyền có giá trị khi đối tượng được giao việc thực sự có trách nhiệm hoặc điểm biến đổi riêng, chứ không chỉ tồn tại để chuyển tiếp lời gọi.
