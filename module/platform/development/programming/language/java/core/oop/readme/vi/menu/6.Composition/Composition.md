# Kết hợp đối tượng (Composition)

Kế thừa trả lời câu hỏi:

> “Đối tượng này có phải là một kiểu con của kiểu kia không?”

Composition trả lời một câu hỏi khác:

> “Đối tượng này cần cộng tác với đối tượng nào để hoàn thành trách nhiệm của nó?”

## <a id="composition-has-a">Composition</a>

### KHÁI NIỆM

Với composition, một object giữ tham chiếu tới object khác và giao một phần công việc cho object đó.

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

Trong thảo luận OOP, **object composition** thường được dùng theo nghĩa rộng: xây một object bằng cách ghép các collaborator lại với nhau thay vì kế thừa implementation.

Trong UML/object modeling, **composition** còn có nghĩa hẹp hơn: một quan hệ whole–part với ownership mạnh và lifecycle của part gắn chặt với whole.

Chapter này dùng cả hai ngữ cảnh, vì vậy cần nhìn câu hỏi đang được trả lời:

```text
composition như kỹ thuật thiết kế
→ ghép object / delegate behavior thay vì extends

composition như quan hệ UML
→ strong ownership + lifecycle semantics
```

Hai cách dùng liên quan nhưng **không đồng nghĩa hoàn toàn**. Một object giữ collaborator để delegation chưa đủ để kết luận đó là UML composition.

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

Class `Checkout` không thay đổi. Chỉ object `Pricing` được thay thế.

Kết quả cho thấy hành vi tính giá thay đổi trong khi kiểu của `Checkout` vẫn giữ nguyên.

## <a id="object-relationships">Quan hệ giữa các object</a>

Các thuật ngữ này mô tả **mức độ quan hệ giữa các object trong thiết kế**. Chúng không phải bốn tính năng runtime riêng biệt của Java.

| Quan hệ | Ý nghĩa thường gặp | Cách biểu diễn thường thấy trong Java |
| --- | --- | --- |
| Dependency | Dùng tạm một object để hoàn thành thao tác | Parameter, local variable |
| Association | Hai object có quan hệ lâu dài hơn | Field/reference |
| Aggregation | Sở hữu yếu hoặc có thể chia sẻ | Field/reference + quy ước vòng đời |
| Composition | Sở hữu mạnh, vòng đời của part gắn với whole | Field/reference + quy tắc tạo/sở hữu |

Java chủ yếu chỉ nhìn thấy **reference**. Ý nghĩa “sở hữu yếu” hay “sở hữu mạnh” đến từ cách ta thiết kế API và quản lý vòng đời object.

### PITFALL — `final` reference không tự tạo composition

Ví dụ:

```java
class Team {
    private final Player captain;

    Team(Player captain) {
        this.captain = captain;
    }
}
```

`final` chỉ đảm bảo field `captain` không bị gán sang reference khác sau khi khởi tạo. Nó **không chứng minh** rằng:

- `Team` là owner duy nhất của `Player`;
- `Player` không được share cho object khác;
- `Player` phải chết theo vòng đời của `Team`;
- quan hệ này chắc chắn là composition theo nghĩa modeling.

Ownership là **semantics của thiết kế**, không phải thuộc tính mà Java suy ra từ một keyword đơn lẻ.

### VÌ SAO — không thể nhìn syntax rồi kết luận ngay quan hệ

Hai class cùng có field tham chiếu tới object khác chưa đủ để kết luận đó là aggregation hay composition.

Ta cần xem:

- ai tạo object con;
- object con có thể tồn tại độc lập không;
- object con có thể được chia sẻ cho nhiều owner không;
- ai kiểm soát vòng đời của nó.

## <a id="association-dependency">Dependency và Association</a>

Một dependency có thể chỉ tồn tại trong một thao tác:

```java
Receipt checkout(PaymentGateway gateway) {
    return gateway.charge(...);
}
```

`PaymentGateway` được truyền vào để dùng cho lời gọi hiện tại.

Nếu một đối tượng cộng tác là phần ổn định của trạng thái object, field thường phù hợp hơn:

```java
class CheckoutService {
    private final PaymentGateway gateway;
}
```

Trong trường hợp này, `CheckoutService` giữ quan hệ lâu dài hơn với `PaymentGateway`.

Một collaborator lưu trong field cũng chưa chắc bị sở hữu mạnh. Hai service hoàn toàn có thể giữ reference tới cùng một object mutable. Khi đó cần suy nghĩ thêm về aliasing, shared mutation và contract vòng đời thay vì chỉ nhìn syntax field/reference.

### ĐÁNH ĐỔI — không cần gắn nhãn cho mọi reference

Không cần ép mọi parameter thành “dependency” và mọi field thành “association” nếu việc phân loại không giúp ích cho thiết kế.

Mục tiêu là hiểu **mức phụ thuộc và vòng đời**, không phải gắn thuật ngữ UML cho mọi object.

## <a id="ownership-lifecycle">Ownership và vòng đời</a>

### Aggregation — sở hữu yếu hơn

Part có thể tồn tại độc lập hoặc được chia sẻ.

Ví dụ:

```text
Team
→ tham chiếu Player

nhưng Player vẫn có thể tồn tại ngay cả khi Team không còn
```

### Composition — sở hữu mạnh hơn

Whole thường chịu trách nhiệm tạo và quản lý part.

Ví dụ:

```text
Order
→ sở hữu các OrderLine

OrderLine thường chỉ có ý nghĩa khi thuộc một Order cụ thể
```

### CƠ CHẾ — Java không tự enforce ownership

Garbage Collector chỉ quan tâm object còn được tham chiếu hay không. Nó không biết khái niệm UML như aggregation hay composition.

Vì vậy quan hệ sở hữu phải được thể hiện bằng:

- constructor hoặc factory;
- mức độ mutable;
- cách expose reference;
- có cho phép share object hay không;
- quy tắc tạo và giữ object.

## <a id="composition-vs-inheritance">Composition và Inheritance</a>

Hai cơ chế giải quyết hai loại quan hệ khác nhau:

| Câu hỏi | Inheritance | Composition |
| --- | --- | --- |
| Quan hệ chính | is-a | has-a / cộng tác với |
| Dùng lại hành vi | Kế thừa phần triển khai | Delegation |
| Thay đổi hành vi | Override ở kiểu con | Thay đối tượng cộng tác |
| Mức phụ thuộc | Chặt với class cha | Phụ thuộc vào hợp đồng của đối tượng cộng tác |
| Thay cách triển khai ở runtime | Thường khó tự nhiên hơn | Tự nhiên nếu đối tượng cộng tác được truyền vào |

### Cách quyết định đơn giản

Nếu object **thực sự cần được dùng như một kiểu tổng quát hơn (supertype)**, inheritance có thể phù hợp.

Nếu mục tiêu chủ yếu là:

- dùng lại hành vi;
- thay strategy;
- thay cách triển khai;

thì nên xem xét composition trước.

Câu “favor composition over inheritance” không có nghĩa là “không bao giờ dùng inheritance”. Nó nhắc ta không nên dùng inheritance chỉ như một mẹo để tái sử dụng vài dòng code khi không có quan hệ kiểu con thực sự.

## <a id="delegation">Delegation</a>

**Delegation** là việc một object nhận yêu cầu rồi chuyển một phần công việc cho object cộng tác phù hợp.

```java
int total(int base) {
    return pricing.price(base);
}
```

`Checkout` không cần biết công thức giảm giá. Nó chỉ cần biết hợp đồng `Pricing`.

### MỐI LIÊN HỆ — delegation dẫn tới abstraction

Để composition linh hoạt, `Checkout` không nên phụ thuộc vào chi tiết của `Discount` hay `Regular`.

Nó nên phụ thuộc vào một hợp đồng ổn định như `Pricing`.

Đây chính là cầu nối sang **trừu tượng hóa (abstraction)**.

### PHÂN BIỆT — composition và delegation

Hai thuật ngữ thường đi cùng nhau nhưng không đồng nghĩa:

```text
Composition
→ A giữ/có collaborator B như một phần của cấu trúc object

Delegation
→ A nhận yêu cầu rồi giao một responsibility cụ thể cho B thực hiện
```

Trong running example của module, `Checkout` có thể giữ một `Pricing` mà không có nghĩa mọi hành vi của `Checkout` đều thuộc về `Pricing`. Khi `Checkout.total(...)` gọi `pricing.price(...)`, **chính responsibility tính giá** được delegation sang collaborator `Pricing`.

```text
Checkout
→ sở hữu responsibility điều phối checkout

Pricing
→ sở hữu responsibility tính giá

Checkout.total(...)
→ delegate phần tính giá cho Pricing.price(...)
```

### ĐÁNH ĐỔI

Nếu mỗi thao tác đơn giản đều được chuyển qua quá nhiều lớp trung gian, code sẽ trở nên rườm rà.

Delegation có giá trị khi object được giao việc thực sự có trách nhiệm hoặc điểm biến đổi riêng, chứ không chỉ tồn tại để chuyển tiếp lời gọi.
