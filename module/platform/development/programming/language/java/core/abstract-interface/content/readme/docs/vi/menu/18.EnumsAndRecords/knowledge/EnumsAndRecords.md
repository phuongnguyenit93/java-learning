<a id="back-to-top"></a>

# Enum và record

## Menu
- [Enum thực thi interface và hành vi riêng của từng hằng](#enums-and-records-001)
- [Phương thức abstract trong enum và trách nhiệm của các hằng](#enums-and-records-002)
- [Record thực thi interface bằng accessor hoặc phương thức bổ sung](#enums-and-records-003)
- [Vì sao enum và record không extends lớp trừu tượng tùy ý](#enums-and-records-004)
- [Kết hợp record, enum và interface để mô hình hóa miền dữ liệu](#enums-and-records-005)
- [Giới hạn khi dùng record hoặc enum trong phân cấp sealed](#enums-and-records-006)

## <a id="enums-and-records-001">Enum thực thi interface và hành vi riêng của từng hằng</a>

<details>
<summary>Xem chi tiết</summary>

# Enum triển khai interface và hành vi riêng của từng hằng

## 1. Mục tiêu

Trong Java, enum biểu diễn một tập hữu hạn các giá trị được xác định trước. Enum có thể triển khai một hoặc nhiều interface, vì vậy các hằng enum có thể được dùng ở nơi chương trình yêu cầu kiểu interface đó. Khi từng hằng cần hành vi khác nhau, mỗi hằng có thể có một class body riêng để override phương thức.

Ví dụ điển hình là một tập phép toán: ADD cộng, SUBTRACT trừ và MULTIPLY nhân. Chúng cùng tuân theo hợp đồng IntBinaryOperator, nhưng cách thực hiện phép toán khác nhau.

Sau bài này, bạn cần biết cách:

- Cho enum triển khai interface và hiểu hợp đồng mà interface xác định.
- Viết hành vi riêng cho từng hằng bằng constant-specific class body.
- Dùng enum qua kiểu interface để tận dụng đa hình.
- Chọn giữa hành vi riêng của từng hằng, phương thức chung của enum và dữ liệu truyền qua constructor.
- Tránh các lỗi thường gặp khi thiết kế enum có hành vi.

## 2. Enum và interface kết hợp với nhau như thế nào?

Một enum có thể khai báo interface sau tên enum:

~~~java
enum Operation implements IntBinaryOperator {
    // Các hằng enum và phần thân enum
}
~~~

Từ khóa implements có ý nghĩa như khi một class thông thường triển khai interface: kiểu enum cung cấp hợp đồng phương thức của interface. Mỗi hằng enum là một instance của kiểu enum, vì thế có thể truyền hằng đó vào phương thức nhận kiểu interface tương ứng.

Có hai cách triển khai phổ biến:

1. **Hành vi chung cho mọi hằng:** khai báo phương thức một lần trong thân enum. Các hằng dùng cùng cách xử lý, thường lấy dữ liệu riêng từ constructor.
2. **Hành vi khác nhau theo từng hằng:** đặt class body ngay sau hằng và override phương thức trong body đó. Ví dụ, ADD và MULTIPLY dùng cùng chữ ký phương thức nhưng thực hiện các phép tính khác nhau.

Nếu interface có phương thức trừu tượng mà enum không cung cấp một cách triển khai chung, mỗi hằng phải cung cấp phần triển khai phù hợp. Có thể khai báo phương thức trừu tượng trong enum để compiler buộc từng hằng thực hiện hợp đồng đó.

Một enum đã có lớp cha là java.lang.Enum; Java không cho enum kế thừa thêm một lớp tùy ý. Enum vẫn có thể triển khai nhiều interface, phân tách các hợp đồng mà nó hỗ trợ.

## 3. Ví dụ chạy được với Java 17+

Tạo file Main.java với toàn bộ nội dung sau. Ví dụ dùng IntBinaryOperator từ thư viện chuẩn java.util.function, có phương thức applyAsInt(int, int).

~~~java
import java.util.function.IntBinaryOperator;

enum Operation implements IntBinaryOperator {
    ADD("+") {
        @Override
        public int applyAsInt(int left, int right) {
            return left + right;
        }
    },
    SUBTRACT("-") {
        @Override
        public int applyAsInt(int left, int right) {
            return left - right;
        }
    },
    MULTIPLY("*") {
        @Override
        public int applyAsInt(int left, int right) {
            return left * right;
        }
    };

    private final String symbol;

    Operation(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    @Override
    public abstract int applyAsInt(int left, int right);
}

public class Main {
    public static void main(String[] args) {
        int left = 3;
        int right = 4;

        for (Operation operation : Operation.values()) {
            int result = operation.applyAsInt(left, right);
            System.out.println(
                operation.symbol() + " " + left + " and " + right + " = " + result
            );
        }

        IntBinaryOperator operation = Operation.MULTIPLY;
        System.out.println("Through interface: " + operation.applyAsInt(6, 7));
    }
}
~~~

Biên dịch và chạy từ thư mục chứa file:

~~~text
javac Main.java
java Main
~~~

Kết quả stdout chính xác:

~~~text
+ 3 and 4 = 7
- 3 and 4 = -1
* 3 and 4 = 12
Through interface: 42
~~~

### Đọc ví dụ theo từng phần

- enum Operation implements IntBinaryOperator khai báo enum hỗ trợ hợp đồng phép toán hai số nguyên.
- ADD("+"), SUBTRACT("-") và MULTIPLY("*") truyền ký hiệu vào constructor chung. Trường symbol là final, nên ký hiệu được gán khi instance enum được khởi tạo và không đổi sau đó.
- Mỗi hằng có một class body và override applyAsInt. Phương thức có cùng tên, tham số và kiểu trả về, nhưng Java gọi phần triển khai gắn với hằng đang được dùng.
- Dòng khai báo public abstract int applyAsInt(...) nêu rõ hợp đồng cần được từng hằng cung cấp. Nếu thêm hằng mới mà thiếu phần override cần thiết, compiler sẽ báo lỗi.
- Vòng lặp dùng Operation.values() để duyệt các hằng theo thứ tự khai báo. Với mỗi hằng, lời gọi applyAsInt được phân phối đến cách triển khai tương ứng.
- Biến operation ở cuối có kiểu IntBinaryOperator, nhưng vẫn nhận được Operation.MULTIPLY. Khi gọi qua interface, chương trình không cần biết kiểu enum cụ thể để sử dụng hợp đồng phép toán.

Đây là ví dụ đầy đủ, có thể biên dịch trực tiếp. Không cần tạo interface riêng vì ví dụ tận dụng interface có sẵn trong JDK.

## 4. Constant-specific class body là gì?

Phần nằm trong cặp ngoặc nhọn ngay sau một hằng enum được gọi là constant-specific class body. Ví dụ:

~~~java
ADD("+") {
    @Override
    public int applyAsInt(int left, int right) {
        return left + right;
    }
}
~~~

Phần thân này định nghĩa cách hằng ADD triển khai phương thức. Những hằng có body riêng có lớp triển khai cụ thể riêng ở phía Java; chúng vẫn là các hằng của cùng enum Operation, và vẫn có thể được gán cho biến kiểu Operation hoặc interface mà enum triển khai.

Cơ chế này là đa hình. Mã gọi chỉ cần biết hợp đồng phương thức. Việc triển khai nào được chạy phụ thuộc vào instance thực tế, nên không phải viết một câu lệnh switch ở mọi nơi để kiểm tra hằng đang là ADD, SUBTRACT hay MULTIPLY.

Không bắt buộc mọi hằng đều phải có body. Nếu enum cung cấp một phương thức cụ thể dùng chung và chỉ một vài hằng cần cách xử lý khác, có thể để các hằng dùng triển khai chung và chỉ override tại những hằng cần khác biệt. Nếu phương thức là trừu tượng, mọi hằng phải có triển khai phù hợp.

## 5. Khi nào nên dùng từng cách?

| Nhu cầu | Cách thiết kế phù hợp |
| --- | --- |
| Mọi hằng thực hiện cùng thuật toán nhưng khác dữ liệu | Dùng constructor và trường final, rồi viết phương thức chung trong enum |
| Hành vi là một phần bản chất của từng hằng | Dùng constant-specific class body để override phương thức |
| Muốn các đối tượng enum được truyền vào nơi nhận một hợp đồng chung | Cho enum triển khai interface và sử dụng qua kiểu interface |
| Có nhiều interface mô tả các khả năng độc lập | Cho enum triển khai các interface cần thiết, miễn là thiết kế vẫn dễ hiểu |
| Logic thuộc về bên gọi và không phải hành vi cốt lõi của enum | Có thể dùng switch hoặc một lớp xử lý bên ngoài thay vì nhồi mọi logic vào enum |

Ví dụ, nếu chỉ khác mức phí cố định, constructor nhận mức phí thường đơn giản hơn việc viết body riêng cho từng hằng. Nếu mỗi hằng có thuật toán riêng, như cách làm tròn hoặc cách tính khác nhau, body riêng sẽ diễn đạt rõ hơn.

Đừng tạo interface chỉ để làm cho thiết kế có vẻ trừu tượng. Interface hữu ích khi cần một hợp đồng có thể được dùng độc lập với enum cụ thể, hoặc khi nhiều kiểu khác nhau cùng cung cấp khả năng đó.

## 6. Ranh giới giữa hợp đồng interface và phương thức abstract

`IntBinaryOperator` xác định hợp đồng mà mã gọi nhìn thấy: `applyAsInt(int, int)`. `Operation` vừa triển khai interface đó, vừa khai báo phương thức `abstract` trong thân enum để khẳng định rằng **mỗi hằng phải tự cung cấp thuật toán**. Trong ví dụ này, ADD cộng, SUBTRACT trừ và MULTIPLY nhân nên không có thuật toán chung phù hợp.

Nếu mọi hằng chỉ khác dữ liệu, enum có thể triển khai phương thức interface một lần bằng phương thức cụ thể, thay vì khai báo phương thức abstract và tạo body riêng cho tất cả hằng. Nếu chỉ một vài hằng khác biệt, hãy dùng phương thức chung rồi chỉ override ở các hằng cần thiết.

Điểm thiết kế chính của bài là **interface cho phép nơi gọi phụ thuộc vào một hợp đồng**, còn constant-specific class body quyết định cách thực hiện hợp đồng theo từng hằng. Bài tiếp theo phân tích riêng cách phương thức abstract khiến trình biên dịch kiểm tra trách nhiệm của từng hằng.

## 7. Những điều cần nhớ về vòng đời và trạng thái

Mỗi hằng enum là một instance duy nhất trong tiến trình chạy thông thường của ứng dụng. Các trường của hằng được dùng chung bởi mọi nơi tham chiếu đến hằng đó. Vì vậy:

- Ưu tiên trường private final cho dữ liệu cấu hình như ký hiệu, mã hoặc tên hiển thị.
- Tránh lưu dữ liệu thay đổi theo từng yêu cầu, người dùng hoặc lần gọi vào trường có thể thay đổi của enum. Trạng thái dùng chung có thể làm các luồng ảnh hưởng lẫn nhau.
- Nếu phương thức chỉ tính kết quả từ tham số, giữ nó không có tác dụng phụ sẽ dễ kiểm thử và an toàn hơn khi dùng đồng thời.
- Constructor của enum không được khai báo public hoặc protected; thông thường để constructor ở phạm vi mặc định hoặc private.
- Các hằng được khởi tạo như những instance của enum. Không tạo hằng bằng new Operation(...) từ bên ngoài.

Enum còn có các phương thức tiện ích như values(), valueOf(String) và name(). valueOf tìm hằng theo đúng tên định danh, phân biệt chữ hoa chữ thường, và ném IllegalArgumentException nếu tên không khớp. ordinal() trả về vị trí khai báo bắt đầu từ 0; không nên dùng vị trí này làm mã lưu trữ hoặc mã nghiệp vụ vì việc sắp xếp lại hằng sẽ làm thay đổi giá trị ordinal. Nếu cần mã ổn định, hãy khai báo trường riêng như code.

## 8. Lỗi thường gặp và cách xử lý

**Quên triển khai phương thức bắt buộc.** Nếu enum hoặc hằng không cung cấp triển khai cần thiết cho phương thức trừu tượng, chương trình không biên dịch. Giữ khai báo phương thức rõ ràng và kiểm tra tất cả hằng khi thêm một giá trị mới.

**Viết logic phân nhánh lặp lại ở nhiều nơi.** Nếu mọi nơi đều kiểm tra loại enum bằng switch để chọn thuật toán, hãy cân nhắc chuyển hành vi về phương thức của enum hoặc constant-specific class body. Điều này gom hành vi liên quan vào cùng một nơi.

**Dùng body riêng dù chỉ khác dữ liệu.** Nếu các hằng chỉ khác một con số hoặc một nhãn, dùng constructor thường ngắn và dễ bảo trì hơn. Body riêng phù hợp khi thuật toán thực sự khác nhau.

**Cố kế thừa một lớp khác.** Enum đã kế thừa java.lang.Enum, nên không thể thêm một lớp cha tùy ý. Nếu cần chia sẻ khả năng với kiểu khác, hãy xem xét interface hoặc một đối tượng cộng tác.

**Đưa trạng thái biến đổi vào enum singleton.** Một trường có thể thay đổi trên enum sẽ được chia sẻ giữa các nơi gọi. Hãy truyền dữ liệu theo tham số hoặc tạo đối tượng riêng cho trạng thái theo yêu cầu.

**Dùng ordinal() làm mã nghiệp vụ.** Giá trị này phụ thuộc thứ tự khai báo. Dùng trường mã rõ ràng và ổn định nếu cần lưu hoặc trao đổi dữ liệu.

**Cho rằng kiểu interface làm mất hành vi đa hình.** Khi một biến interface tham chiếu đến hằng enum, lời gọi phương thức vẫn chạy triển khai cụ thể của hằng. Đó chính là mục tiêu của interface kết hợp với hành vi riêng từng hằng.

## 9. Quy trình áp dụng

1. Xác định tập giá trị hữu hạn và đặt tên enum constants rõ nghĩa.
2. Xác định hợp đồng mà các giá trị cần cung cấp. Nếu hợp đồng có thể được dùng ở nơi khác, chọn hoặc định nghĩa một interface phù hợp.
3. Nếu các hằng chỉ khác dữ liệu, khai báo trường final, constructor và phương thức chung.
4. Nếu từng hằng có thuật toán riêng, viết constant-specific class body và override phương thức cần thiết.
5. Dùng enum qua kiểu enum khi cần các tính năng đặc thù của enum; dùng kiểu interface ở nơi chỉ cần hợp đồng.
6. Biên dịch với Java 17+, chạy ví dụ, so sánh stdout với kết quả dự kiến và thêm kiểm thử cho các trường hợp biên của thuật toán thực tế.
7. Khi thêm hằng mới, kiểm tra lại mọi phương thức trừu tượng, hành vi mặc định và các nơi chuyển đổi dữ liệu từ/đến enum.

Thiết kế tốt làm cho mỗi hằng thể hiện hành vi đúng với ý nghĩa của nó, trong khi mã sử dụng chỉ phụ thuộc vào hợp đồng ổn định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="enums-and-records-002">Phương thức abstract trong enum và trách nhiệm của các hằng</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức `abstract` trong `enum` và trách nhiệm của các hằng

Trong Java, `enum` biểu diễn một tập hợp hữu hạn các hằng đã biết trước, chẳng hạn các phép toán, trạng thái hoặc loại sự kiện. Khi hành vi của từng hằng khác nhau, enum có thể khai báo một phương thức `abstract` và giao cho từng hằng trách nhiệm triển khai phương thức đó. Trình biên dịch kiểm tra hợp đồng này ngay khi biên dịch.

## 1. Phương thức abstract tạo một nghĩa vụ biên dịch

Phương thức `abstract` trong thân enum chỉ khai báo chữ ký và không có phần triển khai. Khi đó, mỗi hằng phải có *constant-specific class body* triển khai phương thức tương ứng. Nếu thiếu một hằng, sai tên hoặc sai chữ ký, toàn bộ enum không biên dịch được.

Cách này khác với việc viết một phương thức thông thường trong enum: phương thức có thân tạo hành vi chung và chỉ các hằng thực sự cần thay đổi mới phải override. `@Override` giúp trình biên dịch phát hiện lỗi chữ ký khi định nghĩa phần triển khai riêng.

Phần sau dùng chính ràng buộc này để tách quy tắc kiểm tra chung khỏi công thức tính phí riêng của từng hằng.

## 2. Ví dụ hoàn chỉnh có thể chạy

Lưu nội dung dưới đây vào tệp `EnumAbstractMethodDemo.java`. Mỗi lựa chọn giao hàng phải cung cấp cách tính phí, nhưng tất cả đều dùng chung bước kiểm tra số lượng. Dùng Java 17 trở lên.

~~~java
public class EnumAbstractMethodDemo {
    enum DeliveryFee {
        STANDARD {
            @Override
            protected int feeInCents(int itemCount) {
                return 200 + itemCount * 100;
            }
        },
        EXPRESS {
            @Override
            protected int feeInCents(int itemCount) {
                return 500 + itemCount * 200;
            }
        },
        PICKUP {
            @Override
            protected int feeInCents(int itemCount) {
                return 0;
            }
        };

        protected abstract int feeInCents(int itemCount);

        public final int quote(int itemCount) {
            if (itemCount <= 0) {
                throw new IllegalArgumentException("itemCount must be positive");
            }
            return feeInCents(itemCount);
        }
    }

    public static void main(String[] args) {
        for (DeliveryFee delivery : DeliveryFee.values()) {
            System.out.printf(
                "%s: %d cents%n",
                delivery,
                delivery.quote(2)
            );
        }
    }
}
~~~

Biên dịch và chạy:

~~~text
javac EnumAbstractMethodDemo.java
java EnumAbstractMethodDemo
~~~

Đầu ra:

~~~text
STANDARD: 400 cents
EXPRESS: 900 cents
PICKUP: 0 cents
~~~

Dòng `delivery.quote(2)` chạy kiểm tra đầu vào chung rồi mới gọi phương thức `feeInCents(2)` của hằng hiện tại. STANDARD tính `200 + 2 × 100 = 400`, EXPRESS tính `500 + 2 × 200 = 900`, còn PICKUP không thu phí. Nếu truyền `0`, `quote` ném `IllegalArgumentException` trước khi tính toán.

Ở đây `feeInCents` là `abstract` nên **từng hằng bắt buộc triển khai**. `quote` là phương thức cụ thể `final` nên các hằng không thể bỏ qua chính sách kiểm tra chung bằng cách override nó. Nếu thêm hằng `DRONE` mà quên triển khai `feeInCents`, trình biên dịch báo lỗi ngay.

Ví dụ sử dụng `int` và phí tính bằng cent để tập trung vào cơ chế của enum. Ứng dụng tính tiền thật còn phải kiểm tra giới hạn số lượng và tránh tràn số, hoặc chọn biểu diễn số tiền phù hợp với yêu cầu nghiệp vụ.

## 3. Trách nhiệm của từng hằng

- **Thực hiện đúng chữ ký:** mỗi hằng cung cấp `feeInCents(int)` với mức truy cập phù hợp; `@Override` giúp kiểm tra hợp đồng ở thời điểm biên dịch.
- **Chỉ giữ logic riêng:** STANDARD, EXPRESS và PICKUP tính phí theo ba quy tắc khác nhau. Việc kiểm tra `itemCount` chỉ xuất hiện một lần trong `quote`.
- **Phân phối hành vi đa hình:** nơi gọi chỉ cần kiểu `DeliveryFee` và phương thức `quote`; phần triển khai `feeInCents` được chọn theo hằng đang dùng.
- **Buộc xử lý khi mở rộng:** một hằng mới không có cách tính phí sẽ gây lỗi biên dịch, thay vì âm thầm dùng một giá trị mặc định không phù hợp.

Các hằng enum vẫn là những đối tượng được khai báo trước. Chúng có thể có class body riêng nhưng không được tạo thêm bằng `new` từ bên ngoài.

## 4. Phương thức abstract và phương thức có thân mặc định

| Cách thiết kế | Trách nhiệm của các hằng |
| --- | --- |
| Phương thức `abstract` | Mọi hằng đều phải triển khai phương thức. Dùng khi mỗi hằng bắt buộc có hành vi riêng. |
| Phương thức thông thường có thân | Các hằng kế thừa hành vi mặc định; hằng nào cần khác biệt có thể ghi đè. |
| `switch` ở nơi gọi | Nơi gọi phải chọn hành vi theo từng hằng; khi thêm hằng có thể phải cập nhật nhiều điểm gọi. |

Không phải enum nào cũng cần phương thức abstract. Nếu mọi hằng có cùng hành vi, một phương thức thông thường thường đơn giản hơn. Nếu chỉ một vài hằng cần khác biệt và có một mặc định hợp lý, phương thức có thân cũng có thể phù hợp. Hãy dùng phương thức abstract khi muốn trình biên dịch đảm bảo mọi hằng đều xác định hành vi bắt buộc.

## 5. Lỗi thường gặp

1. **Bỏ triển khai ở một hằng:** enum không biên dịch được nếu một hằng không triển khai phương thức abstract.
2. **Sai chữ ký:** thay đổi tham số, kiểu trả về hoặc mức truy cập theo hướng không hợp lệ có thể làm việc ghi đè thất bại. Giữ `@Override` để trình biên dịch kiểm tra.
3. **Quên dấu `;` sau danh sách hằng:** khi enum còn khai báo phương thức, trường hoặc constructor phía sau, phải kết thúc danh sách hằng bằng dấu chấm phẩy.
4. **Viết thân cho phương thức abstract:** phương thức abstract chỉ khai báo chữ ký, không chứa phần thân trong `{ ... }`.
5. **Dùng modifier không tương thích:** phương thức abstract không thể đồng thời là `private`, `static` hoặc `final`, vì các modifier này không phù hợp với việc ghi đè triển khai.
6. **Đặt logic phân nhánh lại ở mọi nơi gọi:** nếu enum đã mô hình hóa hành vi bằng phương thức, hãy cân nhắc gọi trực tiếp phương thức đó thay vì lặp lại `switch` ở nhiều nơi.

## 6. Khi nào nên dùng

Phương thức abstract trong enum phù hợp khi tập hợp lựa chọn là hữu hạn, ổn định và mỗi lựa chọn cần thực hiện cùng một thao tác theo cách riêng, ví dụ các chính sách tính phí theo loại, quy tắc xử lý trạng thái hoặc định dạng đầu ra theo loại sự kiện.

Nếu hành vi phức tạp, thay đổi thường xuyên hoặc cần mở rộng bằng các triển khai do bên ngoài cung cấp, hãy cân nhắc interface cùng các lớp chiến lược riêng. Enum phù hợp nhất khi danh sách hằng được kiểm soát và việc thêm một hằng mới nên buộc mã nguồn xử lý rõ hành vi của hằng đó.

## 7. Ghi nhớ

- Khai báo phương thức abstract trong enum khi mọi hằng phải cung cấp một hành vi cụ thể.
- Mỗi hằng triển khai phương thức bằng thân lớp riêng và phải khớp chữ ký.
- Mã gọi sử dụng kiểu enum chung; đa hình chọn triển khai đúng lúc chạy.
- Dùng phương thức có thân mặc định khi không cần bắt buộc từng hằng triển khai.
- Biên dịch lại sau khi thêm hằng mới để phát hiện các triển khai còn thiếu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="enums-and-records-003">Record thực thi interface bằng accessor hoặc phương thức bổ sung</a>

<details>
<summary>Xem chi tiết</summary>

# Record thực thi interface bằng accessor hoặc phương thức bổ sung

## Mục tiêu

Trong Java, một record có thể triển khai interface giống như một class thông thường. Điểm hữu ích là accessor được Java tự sinh từ các record component có thể đáp ứng trực tiếp một phương thức của interface; khi chữ ký không khớp, record có thể khai báo thêm phương thức để thực hiện hợp đồng đó.

Sau bài này, bạn có thể xác định phương thức nào được accessor đáp ứng, khi nào cần tự viết phương thức, và cách kiểm tra hợp đồng interface khi biên dịch.

## 1. Record có thể triển khai interface

Record là một dạng class được thiết kế để biểu diễn dữ liệu. Ví dụ:

~~~java
record Person(String name, int age) {}
~~~

Java tự tạo các accessor công khai có tên theo component: `name()` trả về `String`, còn `age()` trả về `int`. Các accessor này không dùng tiền tố `get` như nhiều JavaBean truyền thống.

Record có thể khai báo một hoặc nhiều interface bằng từ khóa `implements`:

~~~java
interface HasName {
    String name();
}

record Person(String name, int age) implements HasName {}
~~~

Interface `HasName` yêu cầu một phương thức công khai `name()` không có tham số và trả về `String`. Accessor `name()` do record sinh ra có đúng chữ ký đó, nên nó tự đáp ứng hợp đồng interface. Không cần viết lại accessor trong record.

Record vẫn là một class `final` và trực tiếp kế thừa `java.lang.Record`; nó không thể kế thừa một class tùy ý khác. Tuy vậy, record có thể triển khai nhiều interface và cung cấp các phương thức cần thiết cho từng interface.

## 2. Khi accessor đáp ứng phương thức interface

Một accessor có thể đáp ứng phương thức trừu tượng của interface khi chữ ký tương thích. Hãy kiểm tra các điểm sau:

- Tên phương thức phải giống nhau.
- Danh sách tham số phải giống nhau. Accessor của component không nhận tham số.
- Kiểu trả về phải tương thích theo quy tắc override của Java. Với accessor tự sinh, kiểu trả về là kiểu của component; trường hợp thông thường nên khai báo interface với đúng kiểu đó.
- Phương thức triển khai phải có mức truy cập công khai. Accessor được sinh cho record là `public`, phù hợp với phương thức trừu tượng của interface.

Tên phương thức là một phần của hợp đồng. Nếu interface yêu cầu `getName()`, accessor `name()` không tự đáp ứng được chỉ vì cả hai cùng trả về tên. Khi đó, record phải cung cấp `getName()` một cách tường minh.

## 3. Khi cần khai báo phương thức bổ sung

Interface có thể yêu cầu một hành vi không tương ứng với accessor nào. Record có thể khai báo phương thức instance thông thường để đáp ứng yêu cầu ấy, miễn là tuân thủ chữ ký và mức truy cập của interface.

~~~java
interface HasDisplayName {
    String displayName();
}

record User(String firstName, String lastName) implements HasDisplayName {
    @Override
    public String displayName() {
        return firstName + " " + lastName;
    }
}
~~~

Ở ví dụ này, `firstName()` và `lastName()` là accessor được sinh tự động. Chúng không thể đáp ứng `displayName()` vì tên và mục đích của phương thức khác nhau. Record bổ sung `displayName()` để ghép hai component thành tên hiển thị. Chú thích `@Override` giúp trình biên dịch xác nhận phương thức đang ghi đè hoặc triển khai một phương thức có thật trong hợp đồng cha.

Nếu interface yêu cầu `getName()`, cách làm tương tự là khai báo `public String getName()` trong record và trả về component `name`. Java không tự đổi tên accessor từ `name()` thành `getName()`.

Các phương thức `default` trong interface đã có phần thân và có thể được record kế thừa mà không cần triển khai lại. Record chỉ cần ghi đè phương thức mặc định nếu muốn thay đổi hành vi. Phương thức `static` của interface được gọi qua interface và không phải là phương thức instance mà record cần triển khai.

## 4. Ví dụ hoàn chỉnh có thể chạy

Ví dụ dưới đây kết hợp cả hai cách: accessor `id()` do record sinh ra thực hiện hợp đồng `HasId`, còn phương thức bổ sung `description()` thực hiện hợp đồng `Describable`. Interface `Describable` cũng có một phương thức mặc định để minh họa cách dùng hành vi kế thừa.

Lưu toàn bộ nội dung mã vào tệp `Main.java`:

~~~java
interface HasId {
    String id();
}

interface Describable {
    String description();

    default boolean hasDescription() {
        return !description().isBlank();
    }
}

record Service(String id, String name, int port)
        implements HasId, Describable {

    @Override
    public String description() {
        return name + " listens on " + port;
    }
}

public class Main {
    public static void main(String[] args) {
        Service service = new Service("svc-1", "Catalog", 8080);

        HasId idView = service;
        Describable descriptionView = service;

        System.out.println(idView.id());
        System.out.println(descriptionView.description());
        System.out.println(descriptionView.hasDescription());
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~text
javac Main.java
java Main
~~~

Kết quả chuẩn đầu ra chính xác:

~~~text
svc-1
Catalog listens on 8080
true
~~~

Giải thích kết quả:

1. `idView.id()` gọi accessor `id()` của record `Service`. Không cần khai báo lại phương thức này trong record.
2. `descriptionView.description()` gọi phương thức `description()` mà `Service` tự triển khai. Nội dung trả về được tạo từ hai giá trị `name` và `port`.
3. `descriptionView.hasDescription()` gọi phương thức `default` của `Describable`. Phương thức này gọi tiếp `description()` trên cùng đối tượng và kiểm tra kết quả có rỗng hay không.

Ví dụ cũng cho thấy tính đa hình: biến có kiểu interface có thể tham chiếu đến một đối tượng record. Qua biến đó, chương trình chỉ gọi được các phương thức có trong interface tương ứng, nhưng phần triển khai được thực thi trên đối tượng `Service`.

## 5. Các lỗi thường gặp

| Lỗi | Vì sao lỗi | Cách xử lý |
|---|---|---|
| Cho rằng `name()` đáp ứng `getName()` | Tên phương thức khác nhau | Khai báo thêm `getName()` nếu interface yêu cầu |
| Viết phương thức triển khai mà bỏ `public` | Phương thức interface cần mức truy cập công khai; không thể giảm mức truy cập khi triển khai | Khai báo `public` |
| Sai số lượng hoặc kiểu tham số | Phương thức không có chữ ký mà interface yêu cầu | Đối chiếu tên, tham số và kiểu trả về với interface |
| Kiểu trả về không tương thích | Phương thức không đáp ứng quy tắc override | Dùng kiểu trả về phù hợp với hợp đồng |
| Quên triển khai một phương thức trừu tượng | Record là class cụ thể, không thể để lại phương thức trừu tượng chưa được đáp ứng | Dùng accessor phù hợp hoặc khai báo phương thức còn thiếu |
| Cố khai báo một class cha tùy ý | Record đã trực tiếp kế thừa `java.lang.Record` | Dùng `implements` để kết hợp hành vi qua interface |

Lỗi chữ ký thường được phát hiện ngay khi biên dịch. Vì vậy, khi trình biên dịch báo record không triển khai một phương thức trừu tượng, hãy so sánh chính xác tên phương thức, danh sách tham số, kiểu trả về và mức truy cập trước khi thay đổi thiết kế.

## 6. Quy trình lựa chọn cách triển khai

1. Đọc interface và liệt kê các phương thức trừu tượng mà record phải cung cấp.
2. Với từng phương thức, kiểm tra xem có record component nào sinh ra accessor cùng chữ ký hay không.
3. Nếu accessor khớp, để Java tự sinh accessor và không viết lại không cần thiết.
4. Nếu không khớp, khai báo phương thức công khai trong record với chữ ký tương thích.
5. Kiểm tra những phương thức `default` có thể kế thừa và chỉ ghi đè khi có lý do.
6. Biên dịch chương trình, đọc lỗi nếu có, rồi chạy và đối chiếu đầu ra mong đợi.

## Ghi nhớ

Record triển khai interface theo hợp đồng phương thức của Java. Accessor tự sinh chỉ đáp ứng những phương thức có chữ ký phù hợp; nó không tự đáp ứng phương thức khác tên như `getName()`, phương thức có tham số, hay hành vi tính toán riêng. Khi interface yêu cầu hành vi bổ sung, hãy định nghĩa phương thức đó trong record với mức truy cập và chữ ký chính xác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="enums-and-records-004">Vì sao enum và record không extends lớp trừu tượng tùy ý</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao enum và record không thể extends một lớp trừu tượng tùy ý?

Trong Java, một lớp chỉ có **một lớp cha trực tiếp**. Khai báo extends xác định lớp cha đó; implements khai báo các interface mà kiểu dữ liệu cam kết thực hiện. enum và record đều là những dạng khai báo lớp có quy tắc kế thừa riêng, vì vậy chúng không thể chọn thêm một lớp cha tùy ý.

- Mỗi enum có lớp cha trực tiếp là java.lang.Enum&lt;E&gt;, trong đó E là kiểu enum đang khai báo.
- Mỗi record có lớp cha trực tiếp là java.lang.Record.
- Cả enum và record đều có thể implements nhiều interface.
- Nếu cần dùng một lớp trừu tượng có sẵn, có thể đặt enum hoặc record bên trong một đối tượng adapter/wrapper kế thừa lớp đó và ủy quyền công việc cho đối tượng được bọc.

## 1. Quy tắc kế thừa của Java

Một lớp thông thường có thể kế thừa một lớp trừu tượng:

~~~java
abstract class Auditable {
    abstract String auditId();
}

final class Invoice extends Auditable {
    @Override
    String auditId() {
        return "INV-17";
    }
}
~~~

Invoice có Auditable làm lớp cha trực tiếp. Java không cho phép khai báo một lớp thông thường kế thừa đồng thời hai lớp, chẳng hạn vừa extends Auditable vừa extends một lớp khác.

enum và record cũng tuân theo giới hạn một lớp cha, nhưng chúng đã có lớp cha do ngôn ngữ quy định. Vì thế, các khai báo sau là **ví dụ minh họa cú pháp không hợp lệ**:

~~~java
abstract class Auditable {
    abstract String auditId();
}

enum OrderStatus extends Auditable {
    OPEN, CLOSED;
}

record Customer(String id) extends Auditable {}
~~~

Cần bỏ phần extends Auditable khỏi cả hai khai báo. Không có cách thêm một lớp cha tùy ý vào cú pháp enum hoặc record; đây là quy tắc của ngôn ngữ Java, không phải giới hạn riêng của một IDE.

## 2. Vì sao enum kế thừa java.lang.Enum?

enum biểu diễn một tập hằng số hữu hạn được khai báo trước. Ví dụ:

~~~java
enum OrderStatus {
    OPEN, PROCESSING, CLOSED
}
~~~

Java cung cấp các hành vi đặc biệt cho enum, chẳng hạn tên hằng số qua name(), vị trí khai báo qua ordinal(), khả năng so sánh giữa các hằng số cùng enum và cách xử lý hằng số enum khi tuần tự hóa. Lớp nền java.lang.Enum&lt;E&gt; cung cấp một phần các hành vi chuẩn này, còn trình biên dịch tạo các hằng số enum theo quy tắc riêng.

Nếu enum có thể kế thừa một lớp tùy ý, nó sẽ cần đồng thời kế thừa cả lớp tùy ý đó lẫn java.lang.Enum&lt;E&gt;. Điều đó vi phạm quy tắc một lớp cha trực tiếp. Java giải quyết bằng cách cố định lớp cha của enum là java.lang.Enum&lt;E&gt;.

enum vẫn có thể chứa trường, constructor, phương thức và triển khai interface. Constructor enum không được gọi bằng new từ bên ngoài để tạo thêm hằng số tùy ý; các hằng số hợp lệ được liệt kê trong khai báo enum.

Ví dụ enum có thể triển khai interface:

~~~java
interface Labelled {
    default String label() {
        return getClass().getSimpleName();
    }
}

enum OrderStatus implements Labelled {
    OPEN, CLOSED
}
~~~

OrderStatus dùng được phương thức mặc định label() từ interface Labelled. Việc triển khai interface bổ sung hợp đồng và hành vi mà không thay đổi lớp cha của enum. Một hằng số enum cũng có thể có phần thân riêng để ghi đè phương thức, nhưng điều đó vẫn không cho phép enum kế thừa một lớp tùy ý.

## 3. Vì sao record kế thừa java.lang.Record?

record được thiết kế để khai báo kiểu dữ liệu chủ yếu mang dữ liệu. Ví dụ:

~~~java
record Customer(String id, String name) {}
~~~

Khai báo trên cho biết Customer có hai thành phần dữ liệu là id và name. Nếu không tự định nghĩa các thành phần tương ứng, trình biên dịch cung cấp các trường thành phần final, phương thức truy cập id() và name(), constructor chuẩn, cùng các triển khai mặc định cho equals(), hashCode() và toString().

Mọi record đều có java.lang.Record làm lớp cha trực tiếp và record là kiểu final. Do đó, record không thể thêm một lớp cha khác, kể cả lớp đó là abstract. Record cũng không có cú pháp extends trong khai báo.

Quy tắc này giúp mô hình dữ liệu của record nhất quán: các thành phần được khai báo xác định dữ liệu của record, còn các hành vi dùng chung có thể đến từ interface hoặc được ủy quyền cho đối tượng khác. Trạng thái tùy ý kế thừa từ một lớp cha có thể làm mờ ranh giới dữ liệu và gây khó hiểu khi đánh giá equality giữa các record.

Record vẫn có thể implements nhiều interface, ghi đè phương thức, khai báo constructor tường minh hoặc compact constructor để kiểm tra dữ liệu đầu vào. Những khả năng đó không thay đổi lớp cha java.lang.Record và không làm record trở thành kiểu có thể được kế thừa.

## 4. Ví dụ chạy được: interface và composition

Ví dụ dưới đây dùng Java 17 trở lên. Customer là record, OrderStatus là enum; cả hai triển khai interface. CustomerAdapter là một lớp thông thường kế thừa lớp trừu tượng ExternalIdSource và ủy quyền việc lấy ID cho Customer.

Lưu toàn bộ mã vào tệp EnumRecordDemo.java:

~~~java
interface Identified {
    String id();
}

interface Labelled {
    default String label() {
        return getClass().getSimpleName();
    }
}

enum OrderStatus implements Labelled {
    OPEN, CLOSED
}

record Customer(String id, String name)
        implements Identified, Labelled {}

abstract class ExternalIdSource {
    abstract String externalId();
}

final class CustomerAdapter extends ExternalIdSource {
    private final Customer customer;

    CustomerAdapter(Customer customer) {
        this.customer = customer;
    }

    @Override
    String externalId() {
        return customer.id();
    }
}

public class EnumRecordDemo {
    public static void main(String[] args) {
        Customer customer = new Customer("C-17", "Lan");

        System.out.println(customer);
        System.out.println(customer.id());
        System.out.println(customer.label());
        System.out.println(OrderStatus.OPEN.label());

        ExternalIdSource legacy = new CustomerAdapter(customer);
        System.out.println(legacy.externalId());
    }
}
~~~

Biên dịch và chạy:

~~~text
javac EnumRecordDemo.java
java EnumRecordDemo
~~~

Kết quả chuẩn trên stdout:

~~~text
Customer[id=C-17, name=Lan]
C-17
Customer
OrderStatus
C-17
~~~

Ở đây, Customer không kế thừa ExternalIdSource. CustomerAdapter mới là lớp kế thừa ExternalIdSource, giữ một tham chiếu đến Customer và chuyển lời gọi externalId() thành customer.id(). Đây là cách kết hợp kiểu dữ liệu record với một API cũ yêu cầu lớp con của lớp trừu tượng.

## 5. Chọn cách thiết kế phù hợp

| Nhu cầu | Cách phù hợp |
|---|---|
| Nhiều kiểu cần cùng cam kết một nhóm phương thức | Khai báo interface và cho enum/record implements interface đó |
| Cần hành vi dùng chung không phụ thuộc trạng thái instance | Có thể dùng phương thức default trong interface |
| API hoặc framework bắt buộc nhận lớp con của một abstract class | Tạo adapter/wrapper kế thừa abstract class và ủy quyền cho enum/record |
| Cần dùng lại trạng thái instance và triển khai dựa trên lớp cha | Cân nhắc một lớp thông thường trong hệ phân cấp kế thừa |
| Record cần thêm thông tin dữ liệu | Thêm thành phần record phù hợp, hoặc chứa một đối tượng khác để composition |

Interface mô tả hợp đồng; nó không thay thế hoàn toàn lớp trừu tượng. Interface không cung cấp các trường instance tùy ý hoặc constructor kế thừa, dù có thể khai báo phương thức default. Nếu cần trạng thái và quy trình khởi tạo chung của một lớp cha, adapter hoặc composition thường là lựa chọn rõ ràng hơn cho enum và record.

## 6. Những nhầm lẫn thường gặp

- **“Lớp abstract không tạo instance nên có thể làm lớp cha thứ hai.”** Không đúng. Quy tắc một lớp cha áp dụng cho mọi lớp, bất kể lớp cha có abstract hay không.
- **“enum chỉ là class có danh sách hằng số.”** enum là một dạng khai báo đặc biệt với lớp cha java.lang.Enum&lt;E&gt; và quy tắc tạo hằng số riêng.
- **“record là class nên có thể extends tùy ý.”** record là dạng khai báo lớp có lớp cha cố định là java.lang.Record và không cho phép khai báo extends.
- **“implements tương đương extends.”** Interface cung cấp hợp đồng và có thể cung cấp phương thức default, nhưng không truyền trạng thái instance hay constructor của một lớp cha.
- **“Có thể sửa lớp cha bằng cách ghi đè phương thức.”** Ghi đè thay đổi hành vi phương thức, không thay đổi cây kế thừa.
- **“Nếu cần hành vi của abstract class thì phải bỏ record hoặc enum ngay.”** Không nhất thiết. Trước hết hãy xem có thể mô hình hóa hành vi bằng interface hay bọc đối tượng bằng adapter hay không. Chỉ chuyển sang lớp thông thường khi mô hình thực sự cần kế thừa trạng thái và hành vi của lớp cha.

## 7. Cách ghi nhớ

enum kế thừa java.lang.Enum&lt;E&gt; vì Java quản lý chúng như các hằng số enum có hành vi chuẩn. record kế thừa java.lang.Record vì Java quản lý chúng như các kiểu dữ liệu có thành phần được khai báo rõ ràng. Cả hai có thể triển khai nhiều interface, nhưng không thể extends một lớp trừu tượng tùy ý.

Khi gặp yêu cầu “enum/record phải là subclass của abstract class”, hãy kiểm tra xem yêu cầu đó thực sự cần hợp đồng phương thức, cần dùng lại trạng thái và triển khai của lớp cha, hay chỉ cần tương thích với một API cũ. Dùng interface cho hợp đồng; dùng adapter/composition khi cần tích hợp với lớp trừu tượng; dùng lớp thông thường nếu mô hình thực sự đòi hỏi một lớp cha do ứng dụng chọn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="enums-and-records-005">Kết hợp record, enum và interface để mô hình hóa miền dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# Kết hợp record, enum và interface để mô hình hóa miền dữ liệu

Khi mô hình hóa một miền nghiệp vụ, ta thường cần biểu diễn dữ liệu, giới hạn các giá trị hợp lệ và định nghĩa những hành vi mà nhiều kiểu có thể cùng cung cấp. Trong Java 17+, **record**, **enum** và **interface** giải quyết ba phần việc khác nhau và có thể phối hợp trong cùng một mô hình.

- **Record** phù hợp với dữ liệu có các thành phần xác định, chẳng hạn một dòng hàng hoặc một đơn hàng. Java tự sinh accessor, equals, hashCode và toString; record cũng là kiểu final.
- **Enum** biểu diễn một tập giá trị hữu hạn, có tên và được biết trước khi biên dịch, chẳng hạn trạng thái đơn hàng hoặc phương thức giao hàng. Mỗi hằng enum có thể mang dữ liệu và triển khai hành vi riêng.
- **Interface** mô tả một khả năng hoặc hợp đồng mà nhiều kiểu có thể thực hiện. Một record hoặc enum có thể triển khai nhiều interface để được sử dụng thông qua cùng một kiểu trừu tượng.

Không nên dùng một trong ba cấu trúc cho mọi mục đích. Hãy chọn theo bản chất của dữ liệu và quy tắc nghiệp vụ, rồi kết hợp chúng khi các trách nhiệm đó giao nhau.

## 1. Tách dữ liệu, tập lựa chọn và hợp đồng

Giả sử ứng dụng cần mô hình hóa đơn hàng trực tuyến.

| Thành phần | Vai trò trong miền nghiệp vụ |
|---|---|
| OrderLine (record) | Mã hàng, số lượng, đơn giá; tự tính thành tiền |
| Order (record) | Mã đơn, các dòng hàng, phương thức giao hàng và trạng thái |
| ShippingMethod (enum) | Tập phương thức giao hàng được hỗ trợ, mỗi phương thức có phí và thời gian dự kiến |
| OrderStatus (enum) | Tập trạng thái hợp lệ và quy tắc chuyển trạng thái |
| LineAmount (interface) | Hợp đồng cho đối tượng có thể tính thành tiền |
| Labeled (interface) | Hợp đồng cung cấp nhãn hiển thị cho nhiều kiểu miền nghiệp vụ |

Mô hình này có hai lợi ích. Thứ nhất, record gom dữ liệu liên quan vào một giá trị rõ ràng. Thứ hai, enum giữ những lựa chọn có giới hạn ở một nơi và có thể gắn hành vi với từng lựa chọn. Interface cho phép phần gọi làm việc với khả năng mà một đối tượng cung cấp thay vì phụ thuộc vào lớp cụ thể.

## 2. Ví dụ hoàn chỉnh chạy trên Java 17+

Lưu toàn bộ mã sau vào tệp **OrderDomainDemo.java**. Các interface, record và enum phụ trợ được khai báo ở cấp cao nhất với phạm vi package; lớp công khai OrderDomainDemo là điểm vào chương trình.

~~~java
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

interface Labeled {
    String label();
}

interface LineAmount {
    BigDecimal amount();
}

record OrderLine(String sku, int quantity, BigDecimal unitPrice)
        implements LineAmount {
    public OrderLine {
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(unitPrice, "unitPrice");

        if (sku.isBlank()) {
            throw new IllegalArgumentException("sku must not be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice must not be negative");
        }
    }

    @Override
    public BigDecimal amount() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}

enum ShippingMethod implements Labeled {
    STANDARD("Standard", new BigDecimal("3.50"), 5),
    EXPRESS("Express", new BigDecimal("9.00"), 2);

    private final String label;
    private final BigDecimal fee;
    private final int deliveryDays;

    ShippingMethod(String label, BigDecimal fee, int deliveryDays) {
        this.label = label;
        this.fee = fee;
        this.deliveryDays = deliveryDays;
    }

    @Override
    public String label() {
        return label;
    }

    public BigDecimal fee() {
        return fee;
    }

    public int deliveryDays() {
        return deliveryDays;
    }
}

enum OrderStatus implements Labeled {
    DRAFT("Draft"),
    PAID("Paid"),
    SHIPPED("Shipped"),
    CANCELLED("Cancelled");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }

    public boolean canTransitionTo(OrderStatus next) {
        Objects.requireNonNull(next, "next");

        return switch (this) {
            case DRAFT -> next == PAID || next == CANCELLED;
            case PAID -> next == SHIPPED || next == CANCELLED;
            case SHIPPED, CANCELLED -> false;
        };
    }
}

record Order(
        String id,
        List<OrderLine> lines,
        ShippingMethod shippingMethod,
        OrderStatus status
) implements Labeled {
    public Order {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(shippingMethod, "shippingMethod");
        Objects.requireNonNull(status, "status");

        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("order must contain at least one line");
        }

        // Make a defensive, unmodifiable copy of the supplied list.
        lines = List.copyOf(lines);
    }

    public BigDecimal subtotal() {
        return lines.stream()
                .map(OrderLine::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal total() {
        return subtotal().add(shippingMethod.fee());
    }

    @Override
    public String label() {
        return id + " - " + status.label();
    }

    public Order withStatus(OrderStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException(
                    "Cannot transition from " + status + " to " + next);
        }

        // Records are immutable values: return a new Order with the new status.
        return new Order(id, lines, shippingMethod, next);
    }
}

public class OrderDomainDemo {
    public static void main(String[] args) {
        Order order = new Order(
                "ORD-1001",
                List.of(
                        new OrderLine("BOOK-JAVA", 2, new BigDecimal("19.90")),
                        new OrderLine("MUG", 1, new BigDecimal("8.50"))
                ),
                ShippingMethod.EXPRESS,
                OrderStatus.PAID
        );

        System.out.println("Order: " + order.label());
        System.out.println("Items: " + order.lines().size());
        System.out.println("Subtotal: " + order.subtotal().setScale(2));
        System.out.println("Shipping: " + order.shippingMethod().label()
                + " (" + order.shippingMethod().deliveryDays()
                + " days) = " + order.shippingMethod().fee().setScale(2));
        System.out.println("Total: " + order.total().setScale(2));
        System.out.println("Transition: "
                + order.withStatus(OrderStatus.SHIPPED).label());

        List<Labeled> labels = List.of(
                order.status(),
                order.shippingMethod(),
                order
        );
        System.out.println("Labels through interface:");
        for (Labeled value : labels) {
            System.out.println("- " + value.label());
        }
    }
}
~~~

Biên dịch và chạy từ thư mục chứa tệp:

~~~bash
javac OrderDomainDemo.java
java OrderDomainDemo
~~~

Kết quả chuẩn:

~~~text
Order: ORD-1001 - Paid
Items: 2
Subtotal: 48.30
Shipping: Express (2 days) = 9.00
Total: 57.30
Transition: ORD-1001 - Shipped
Labels through interface:
- Paid
- Express
- ORD-1001 - Paid
~~~

## 3. Vì sao các kiểu này phối hợp được?

### Record giữ dữ liệu và bảo vệ các bất biến cơ bản

OrderLine gom mã hàng, số lượng và đơn giá thành một giá trị. Constructor dạng compact cho phép kiểm tra dữ liệu đầu vào mà không cần viết lại toàn bộ phép gán trường. Sau khi constructor hoàn tất, các thành phần của record không thể được gán lại; Java tự cung cấp accessor như sku(), quantity() và unitPrice().

Order chứa danh sách dòng hàng, phương thức giao hàng và trạng thái. List.copyOf tạo một bản sao không thể sửa đổi của danh sách đầu vào, vì vậy bên gọi không thể thay đổi danh sách gốc rồi làm thay đổi nội dung Order. Đây là **bất biến nông**: nếu record chứa một đối tượng có thể thay đổi mà không sao chép hoặc bảo vệ đối tượng đó, trạng thái bên trong vẫn có thể bị thay đổi. Trong ví dụ này, OrderLine cũng là record và BigDecimal là bất biến nên dữ liệu dòng hàng phù hợp với cách dùng đó.

Record không tự kiểm tra mọi quy tắc nghiệp vụ. Constructor ở ví dụ kiểm tra mã đơn, số lượng, đơn giá và danh sách rỗng. Những quy tắc phụ thuộc vào dữ liệu lưu trữ, người dùng hiện tại hoặc hệ thống bên ngoài thường cần được kiểm tra ở tầng nghiệp vụ phù hợp.

### Enum biểu diễn lựa chọn hữu hạn và gắn hành vi vào lựa chọn

ShippingMethod có hai hằng được khai báo trước: STANDARD và EXPRESS. Mỗi hằng có nhãn, phí và số ngày giao dự kiến. Khi bổ sung một phương thức giao hàng mới, ta bổ sung hằng và các giá trị liên quan tại một chỗ thay vì truyền những chuỗi tự do như "express" xuyên suốt ứng dụng.

OrderStatus cũng là tập trạng thái hữu hạn. Phương thức canTransitionTo tập trung quy tắc chuyển trạng thái: đơn ở DRAFT có thể chuyển sang PAID hoặc CANCELLED; đơn ở PAID có thể chuyển sang SHIPPED hoặc CANCELLED; SHIPPED và CANCELLED là trạng thái kết thúc trong mô hình minh họa. Order.withStatus dùng quy tắc đó để từ chối chuyển đổi không hợp lệ và trả về một Order mới khi chuyển đổi hợp lệ.

Đây là quy tắc ví dụ để minh họa mô hình hóa, không phải quy trình phù hợp cho mọi hệ thống. Nghiệp vụ thực tế có thể cần thêm trạng thái, điều kiện thanh toán, quyền người dùng, kiểm soát đồng thời và lưu lịch sử chuyển trạng thái.

### Interface mô tả khả năng chung

LineAmount chỉ yêu cầu một phương thức amount(). OrderLine triển khai hợp đồng này bằng cách nhân đơn giá với số lượng. Mã tính tổng có thể gọi amount() mà không cần biết cách mỗi loại dòng hàng tính ra số tiền.

Labeled yêu cầu label(). OrderStatus, ShippingMethod và Order cùng triển khai interface đó. Vì vậy, chương trình có thể đặt các đối tượng khác kiểu vào một List&lt;Labeled&gt; rồi xử lý tất cả theo cùng một cách. Đây là đa hình: mã gọi dựa vào hợp đồng chung, còn phương thức được thực thi phụ thuộc vào kiểu đối tượng thực tế.

Interface không lưu trạng thái đối tượng như một record. Nó chủ yếu xác định những thao tác mà kiểu triển khai cam kết cung cấp. Record và enum vẫn giữ dữ liệu, còn interface cho phép sử dụng các kiểu đó thông qua một hợp đồng ổn định.

## 4. Những giới hạn cần nhớ

- **Record là final.** Không thể tạo lớp con của Order hoặc OrderLine. Nếu cần một hệ phân cấp kiểu, hãy cân nhắc interface hoặc một thiết kế khác phù hợp hơn.
- **Enum không thể kế thừa lớp miền nghiệp vụ tùy ý.** Mỗi enum đã kế thừa java.lang.Enum, nhưng có thể triển khai một hoặc nhiều interface. Dùng enum khi tập lựa chọn thực sự hữu hạn và được kiểm soát.
- **Interface không bảo đảm mọi quy tắc được thực hiện đúng.** Nó bảo đảm kiểu triển khai cung cấp các phương thức đã khai báo; tính đúng đắn của phần triển khai vẫn cần được thiết kế và kiểm thử.
- **Record không tự tạo bản sao sâu.** Các thành phần tham chiếu vẫn có thể trỏ tới đối tượng mutable. Dùng kiểu bất biến hoặc sao chép phòng vệ khi cần bảo đảm dữ liệu không đổi.
- **Không dùng double cho phép tính tiền chính xác.** Ví dụ dùng BigDecimal và tạo giá trị từ chuỗi thập phân. Hệ thống thực tế còn cần xác định đơn vị tiền, quy tắc làm tròn, thuế, chiết khấu và cách lưu số tiền.
- **Không để enum trở thành nơi chứa mọi logic.** Hành vi gắn trực tiếp với một lựa chọn hữu hạn thường hợp lý; quy trình phức tạp phụ thuộc nhiều dịch vụ nên được đặt ở thành phần nghiệp vụ phù hợp.

## 5. Cách quyết định trong một mô hình mới

1. Xác định dữ liệu nào tạo thành một giá trị có cấu trúc rõ ràng. Nếu giá trị chủ yếu gồm các thành phần xác định và không cần định danh mutable riêng, hãy cân nhắc record.
2. Xác định các lựa chọn có tập hữu hạn, ổn định hay không. Nếu đúng, enum giúp tránh chuỗi tùy ý và tập trung dữ liệu hoặc hành vi gắn với từng lựa chọn.
3. Xác định những thao tác mà nhiều kiểu cần cung cấp. Nếu phần gọi chỉ cần biết khả năng đó, hãy diễn tả nó bằng interface.
4. Đặt kiểm tra bất biến tại nơi tạo giá trị; đặt quy tắc chuyển đổi trạng thái tại mô hình hoặc dịch vụ nghiệp vụ có đủ thông tin để quyết định.
5. Kiểm tra cả đường đi hợp lệ và không hợp lệ: dữ liệu đầu vào sai, chuyển trạng thái bị cấm, tổng tiền và hành vi đa hình qua interface.

Kết hợp đúng cách giúp mô hình miền dữ liệu dễ đọc hơn: record làm rõ dữ liệu, enum giới hạn những lựa chọn đã biết, còn interface cho phép nhiều kiểu cung cấp chung một hành vi. Mỗi cấu trúc có trách nhiệm riêng nên thay đổi nghiệp vụ cũng dễ xác định hơn nơi cần chỉnh sửa.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="enums-and-records-006">Giới hạn khi dùng record hoặc enum trong phân cấp sealed</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn khi dùng `record` hoặc `enum` trong phân cấp `sealed`

## Mục tiêu

Từ Java 17, `sealed class` và `sealed interface` cho phép khai báo rõ những kiểu nào được phép kế thừa hoặc triển khai trực tiếp. `record` và `enum` có các quy tắc kế thừa riêng, vì vậy chúng phù hợp làm nhánh lá của một phân cấp sealed theo những cách nhất định, nhưng không thể thay thế tùy ý một lớp cơ sở.

Điểm cần nhớ: **record và enum có thể triển khai một sealed interface; chúng không thể kế thừa một sealed class do người dùng định nghĩa.**

## 1. Quy tắc của phân cấp sealed

Một lớp hoặc interface được khai báo `sealed` phải xác định các kiểu con trực tiếp được phép bằng mệnh đề `permits`, hoặc để trình biên dịch suy luận danh sách đó khi các kiểu con trực tiếp phù hợp được khai báo trong cùng compilation unit.

Ví dụ:

```java
sealed interface KetQua permits ThanhCong, ThatBai, TrangThai {
}
```

Các kiểu được liệt kê phải là kiểu con trực tiếp của `KetQua`: một lớp phải `implements KetQua`, hoặc một interface khác phải `extends KetQua`. Không liệt kê kiểu cháu chỉ kế thừa thông qua một kiểu trung gian.

Mỗi lớp con trực tiếp của một sealed class phải là `final`, `sealed` hoặc `non-sealed`. Với sealed interface, quy tắc tương tự áp dụng cho các lớp triển khai trực tiếp và các interface mở rộng trực tiếp. Mục đích là để mỗi nhánh tiếp tục đóng hoặc chủ động mở rộng phân cấp một cách rõ ràng.

Các kiểu được phép kế thừa hoặc triển khai phải ở cùng module với kiểu sealed khi dùng named module; nếu ứng dụng nằm trong unnamed module, chúng phải ở cùng package. Vi phạm điều kiện này sẽ khiến chương trình không biên dịch.

## 2. Vì sao record không thể là lớp con của sealed class?

Một record luôn kế thừa trực tiếp từ `java.lang.Record`. Cú pháp khai báo record không cho phép chọn một lớp cha khác. Record cũng **ngầm định là `final`**, vì vậy không thể khai báo nó thành `sealed` hay `non-sealed`, cũng không thể tạo lớp con từ record.

Do đó, record không thể trực tiếp mở rộng một sealed class tùy chỉnh. Dù sealed class có liệt kê tên record trong `permits`, điều đó cũng không thay đổi quy tắc lớp cha cố định của record.

Record có thể triển khai một interface thông thường hoặc sealed interface. Đây là cách phù hợp để biểu diễn một nhánh dữ liệu bất biến trong phân cấp kiểu đóng.

```java
sealed interface ThongTin permits NguoiDung {
}

record NguoiDung(String ten, int tuoi) implements ThongTin {
}
```

Trong ví dụ này, `NguoiDung` là một kiểu con trực tiếp hợp lệ của `ThongTin`. Record không cần khai báo `final` vì tính chất đó đã được ngôn ngữ áp dụng ngầm định.

Nếu cần dùng chung trạng thái hoặc hành vi từ một lớp cơ sở, hãy dùng một lớp thông thường làm nhánh con của sealed class. Nếu mô hình chủ yếu là các biến thể dữ liệu, sealed interface kết hợp với record thường đơn giản hơn.

## 3. Vì sao enum không thể là lớp con của sealed class?

Mọi enum đều kế thừa từ `java.lang.Enum`, và Java không cho phép enum mở rộng một lớp khác. Vì thế, enum không thể trực tiếp kế thừa một sealed class do ứng dụng định nghĩa.

Enum có thể triển khai nhiều interface, kể cả sealed interface. Điều này hữu ích khi các giá trị hữu hạn như trạng thái hoặc loại sự kiện cần tham gia cùng một mô hình kết quả.

```java
sealed interface TrangThaiXuLy permits TrangThaiDonHang {
}

enum TrangThaiDonHang implements TrangThaiXuLy {
    MOI,
    DANG_XU_LY,
    HOAN_TAT
}
```

Khi không có hằng enum nào khai báo class body riêng, enum ngầm định là `final`. Nếu ít nhất một hằng enum có class body riêng, kiểu enum được xem là ngầm định `sealed`; các lớp ẩn danh gắn với những hằng đó là các lớp con do trình biên dịch tạo ra. Trường hợp này không biến enum thành một lớp có thể được người dùng tùy ý kế thừa, và không cho phép enum mở rộng một sealed class tùy chỉnh.

## 4. Ví dụ hoàn chỉnh: sealed interface kết hợp record và enum

Ví dụ dưới đây chạy độc lập trên Java 17 trở lên. Hai record mang dữ liệu cho các kết quả khác nhau, còn enum biểu diễn một trạng thái cố định. Cả ba đều triển khai cùng một sealed interface.

Lưu nội dung vào tệp `SealedRecordEnumDemo.java`:

```java
import java.util.List;

public class SealedRecordEnumDemo {
    public static void main(String[] args) {
        List<KetQua> ketQua = List.of(
            new ThanhCong("OK", 200),
            new ThatBai("NOT_FOUND", 404),
            TrangThai.DANG_XU_LY
        );

        for (KetQua item : ketQua) {
            System.out.println(moTa(item));
        }
    }

    private static String moTa(KetQua item) {
        if (item instanceof ThanhCong t) {
            return "Thành công: " + t.ma() + " (" + t.httpCode() + ")";
        }
        if (item instanceof ThatBai f) {
            return "Thất bại: " + f.ma() + " (" + f.httpCode() + ")";
        }
        if (item instanceof TrangThai s) {
            return "Trạng thái: " + s.name();
        }

        throw new IllegalStateException(
            "Loại kết quả chưa được xử lý: " + item.getClass().getName()
        );
    }
}

sealed interface KetQua permits ThanhCong, ThatBai, TrangThai {
}

record ThanhCong(String ma, int httpCode) implements KetQua {
}

record ThatBai(String ma, int httpCode) implements KetQua {
}

enum TrangThai implements KetQua {
    DANG_XU_LY
}
```

Biên dịch và chạy:

```text
javac -encoding UTF-8 SealedRecordEnumDemo.java
java SealedRecordEnumDemo
```

Kết quả chuẩn trên đầu ra:

```text
Thành công: OK (200)
Thất bại: NOT_FOUND (404)
Trạng thái: DANG_XU_LY
```

Ví dụ cho thấy sealed interface kiểm soát tập kiểu triển khai trực tiếp, trong khi record và enum vẫn giữ các quy tắc riêng của chúng. Câu lệnh `instanceof` dùng cú pháp pattern matching đã được hỗ trợ chính thức từ Java 16, nên ví dụ không cần tính năng preview.

## 5. Những thiết kế không hợp lệ và cách chọn mô hình

| Nhu cầu | Cách thiết kế phù hợp | Giới hạn cần nhớ |
|---|---|---|
| Một biến thể dữ liệu gồm các trường cố định | Record triển khai sealed interface | Record không thể kế thừa lớp cơ sở tùy chỉnh |
| Một tập trạng thái hữu hạn, có tên cố định | Enum triển khai sealed interface | Enum không thể kế thừa lớp cơ sở tùy chỉnh |
| Các nhánh cần kế thừa trạng thái hoặc hành vi từ lớp cha | Các lớp thông thường được liệt kê trong `permits` | Mỗi lớp con trực tiếp phải là `final`, `sealed` hoặc `non-sealed` |
| Một nhánh cần mở rộng tiếp nhưng vẫn kiểm soát các kiểu con | Một sealed class hoặc sealed interface trung gian | Kiểu trung gian cũng phải khai báo các nhánh hợp lệ của nó |
| Cần cho phép bên ngoài bổ sung lớp con | Một nhánh `non-sealed` hoặc thiết kế interface không sealed | Nhánh `non-sealed` mở phần phân cấp đó ra khỏi danh sách đóng |

Không thể khai báo một record theo dạng `record ChiTiet(...) extends LopCoSo`: record không có mệnh đề `extends` cho lớp ứng dụng. Tương tự, khai báo enum để mở rộng `LopCoSo` là không hợp lệ vì enum đã kế thừa `java.lang.Enum`.

Một lỗi thường gặp khác là đưa kiểu không phải con trực tiếp vào `permits`, hoặc đặt kiểu được phép ở sai package/module. Hãy kiểm tra quan hệ kế thừa thực tế và vị trí khai báo của từng kiểu trước khi điều tra các lỗi khác.

## 6. Cách kiểm tra khi biên dịch báo lỗi

1. Xác định kiểu cha là sealed class hay sealed interface.
2. Với từng mục trong `permits`, kiểm tra kiểu đó có trực tiếp `extends` hoặc `implements` kiểu sealed hay không.
3. Nếu kiểu con là record hoặc enum, kiểm tra nó có đang cố kế thừa một lớp ứng dụng hay không. Trường hợp đó cần đổi mô hình sang sealed interface hoặc dùng lớp con thông thường.
4. Với lớp con trực tiếp thông thường, kiểm tra modifier `final`, `sealed` hoặc `non-sealed`.
5. Kiểm tra các điều kiện cùng module/package và tên kiểu trong `permits`.
6. Biên dịch lại toàn bộ các tệp liên quan cùng phiên bản JDK phù hợp; không kết luận từ một tệp riêng nếu các kiểu phụ thuộc chưa được biên dịch.

Trong Java 17, sealed classes/interfaces và records đã là tính năng chính thức. Nếu muốn dùng `switch` với pattern matching trên phân cấp sealed, hãy lưu ý rằng pattern matching for switch chỉ trở thành tính năng chính thức ở Java 21; trên Java 17 nó còn là tính năng preview. Các ví dụ trong bài dùng `instanceof` để chạy mà không cần bật preview.

## Ghi nhớ

- **Record → sealed interface:** hợp lệ; record là nhánh lá ngầm định `final`.
- **Enum → sealed interface:** hợp lệ; enum biểu diễn tập hằng hữu hạn và có thể ngầm định `final` hoặc `sealed` tùy class body của các hằng.
- **Record → sealed class tùy chỉnh:** không hợp lệ vì record chỉ kế thừa `java.lang.Record`.
- **Enum → sealed class tùy chỉnh:** không hợp lệ vì enum chỉ kế thừa `java.lang.Enum`.
- **Sealed class → lớp con thông thường:** hợp lệ nếu lớp con được cho phép và tuân thủ quy tắc modifier cùng điều kiện module/package.

Hãy chọn sealed interface khi muốn kết hợp nhiều kiểu lá khác nhau như record và enum; chọn sealed class khi các nhánh cần chia sẻ một lớp cha cụ thể.

</details>

- [Quay lại đầu trang](#back-to-top)
