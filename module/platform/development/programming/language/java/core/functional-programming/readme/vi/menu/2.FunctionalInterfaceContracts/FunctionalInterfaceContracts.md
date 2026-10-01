# Giao diện hàm và quy tắc một phương thức trừu tượng (SAM)

## <a id="functional-interface-purpose">Điều gì làm một giao diện trở thành giao diện hàm?</a>

Một giao diện hàm (functional interface) là giao diện mà hành vi trừu tượng của nó có thể được biểu diễn bằng **một hợp đồng hàm duy nhất**. Nhờ đặc điểm đó, Java có thể dùng giao diện này làm kiểu đích cho biểu thức lambda hoặc tham chiếu phương thức.

```java
@FunctionalInterface
interface TextRule {
    boolean test(String text);
}

TextRule nonEmpty = text -> !text.isEmpty();
```

Điểm cần nhìn vào là **hợp đồng hành vi**, không phải tổng số phương thức xuất hiện trong tệp. Một giao diện hàm vẫn có thể chứa phương thức `default`, `static` hoặc `private` vì các phương thức này đã có phần cài đặt và không tạo thêm hành vi trừu tượng mà lambda phải cung cấp.

Java dùng chính mô hình giao diện quen thuộc để biểu diễn hành vi. Không có một hệ "kiểu hàm" độc lập hoàn toàn với giao diện thông thường; giao diện hàm chính là cầu nối giữa hai cách nhìn này.

## <a id="sam-contract">Hợp đồng một phương thức trừu tượng (SAM)</a>

SAM là viết tắt của **Single Abstract Method**. Với người mới, có thể bắt đầu bằng mô hình đơn giản: lambda cần một thao tác trừu tượng duy nhất để triển khai.

```java
@FunctionalInterface
interface Transformer<T, R> {
    R apply(T value);
}
```

`Transformer<T, R>` có một thao tác trừu tượng là `apply`, nên một lambda tương thích có thể cung cấp hành vi đó:

```java
Transformer<String, Integer> length = text -> text.length();
```

Tuy nhiên, Java không xác định giao diện hàm bằng cách đếm máy móc mọi khai báo `abstract`. Kế thừa và các phương thức tương ứng với phương thức thực thể `public` của `Object` có thể làm thay đổi cách xác định hợp đồng SAM.

Trong thực tế, hãy giữ mô hình tư duy này: sau khi Java áp dụng đầy đủ các quy tắc giao diện hàm, phải còn lại một hợp đồng trừu tượng thống nhất để lambda hoặc tham chiếu phương thức triển khai.

## <a id="functional-interface-function-type">Kiểu hàm của giao diện hàm</a>

Giao diện hàm không chỉ nói rằng "có một phương thức trừu tượng". Từ hợp đồng đó, Java xác định **kiểu hàm (function type)** dùng để kiểm tra lambda hoặc tham chiếu phương thức.

Về mặt khái niệm, kiểu hàm mô tả:

- các kiểu tham số mà hành vi nhận vào;
- kiểu kết quả phải tạo ra, hoặc `void` nếu không trả kết quả;
- các ngoại lệ được kiểm tra (checked exception) mà hợp đồng cho phép truyền tiếp.

Ví dụ:

```java
@FunctionalInterface
interface Parser<T> {
    T parse(String text) throws IOException;
}
```

Với kiểu đích `Parser<Integer>`, hành vi tương ứng nhận `String`, tạo ra `Integer` và có thể ném `IOException`.

Khái niệm này sẽ được dùng lại ở các chương sau khi kiểm tra kiểu tham số, giá trị trả về, tham chiếu phương thức và ngoại lệ được kiểm tra. Đây là khái niệm của trình biên dịch gắn với giao diện hàm; Java không cung cấp một lớp chung tên `FunctionType` để lập trình viên tự tạo và truyền đi.

## <a id="sam-object-method-rule">Các chữ ký phương thức của Object trong quy tắc SAM</a>

Một giao diện có thể khai báo lại phương thức có chữ ký tương ứng với **phương thức thực thể `public` của `Object`**, chẳng hạn `equals(Object)`. Khai báo như vậy không tạo thêm một hợp đồng trừu tượng cần được lambda triển khai.

```java
@FunctionalInterface
interface Matcher {
    boolean matches(String value);

    @Override
    boolean equals(Object other);
}
```

`Matcher` vẫn có thể là giao diện hàm vì `equals(Object)` tương ứng với phương thức `public` của `Object`, còn `matches` là hợp đồng hàm thực sự.

Không nên mở rộng quy tắc này thành "mọi phương thức có tên giống phương thức trong Object đều bị bỏ qua". Quy tắc dựa trên chữ ký của các phương thức thực thể `public` tương ứng của `Object`, không chỉ dựa vào tên quen thuộc như `clone` hay `finalize`.

## <a id="sam-inherited-method-rule">Các phương thức trừu tượng kế thừa tương đương nhau</a>

Kế thừa có thể làm một giao diện nhìn như có nhiều phương thức trừu tượng, nhưng các khai báo đó vẫn có thể mô tả cùng một thao tác nếu chúng tương thích theo quy tắc ghi đè (override) của Java.

```java
interface Left {
    CharSequence value();
}

interface Right {
    String value();
}

@FunctionalInterface
interface Combined extends Left, Right {
}
```

`String` là kiểu trả về hiệp biến phù hợp với `CharSequence`, nên hai khai báo `value()` có thể hợp thành một hợp đồng tương thích. Lambda nhắm tới `Combined` vẫn chỉ cần cung cấp một hành vi `value`.

Ngược lại, các phương thức trừu tượng không liên quan như `read()` và `write()` không tự hợp thành một SAM. Các khai báo kế thừa có chữ ký hoặc kiểu trả về không thể tạo quan hệ ghi đè hợp lệ cũng khiến giao diện không còn là giao diện hàm.

Vì vậy, "đếm số phương thức trừu tượng nhìn thấy trong mã nguồn" chỉ là mẹo ban đầu, không phải quy tắc đầy đủ.

## <a id="functional-interface-annotation">@FunctionalInterface</a>

`@FunctionalInterface` yêu cầu trình biên dịch xác nhận giao diện thật sự thỏa các quy tắc giao diện hàm.

```java
@FunctionalInterface
interface Validator<T> {
    boolean isValid(T value);
}
```

Chú thích (annotation) này vừa là tài liệu thể hiện ý định thiết kế, vừa là hàng rào kiểm tra. Nếu sau này có người thêm một hợp đồng trừu tượng thứ hai không tương thích, lỗi biên dịch xuất hiện ngay tại định nghĩa giao diện.

`@FunctionalInterface` **không bắt buộc**. Một giao diện không có chú thích này nhưng vẫn thỏa quy tắc cấu trúc thì vẫn là giao diện hàm và vẫn có thể làm kiểu đích cho lambda.

Nên dùng chú thích này khi giao diện được chủ đích thiết kế như một hợp đồng hàm, vì nó cho người đọc biết rằng việc giữ một hợp đồng hàm duy nhất là một phần của thiết kế API.

## <a id="non-abstract-interface-methods">Phương thức default, static và private trong giao diện</a>

Các phương thức `default`, `static` và `private` đã có phần cài đặt nên không làm tăng số hành vi trừu tượng mà lambda phải cung cấp.

Phương thức `default` và `static` trong giao diện có từ Java 8. Phương thức `private` trong giao diện được bổ sung từ Java 9, vì vậy hàm hỗ trợ `private static` ở ví dụ bên dưới không hợp lệ nếu biên dịch với mức tương thích nguồn Java 8.

```java
@FunctionalInterface
interface TextFormatter {
    String format(String value);

    default String formatTrimmed(String value) {
        return format(value.trim());
    }

    static TextFormatter identity() {
        return value -> value;
    }

    private static boolean missing(String value) {
        return value == null;
    }
}
```

Ở đây chỉ `format` là hợp đồng hàm. Những phương thức còn lại có thể cung cấp tiện ích, cơ chế tạo hoặc logic nội bộ mà không phá SAM.

Dù vậy, không nên biến giao diện hàm thành nơi gom hàng loạt hàm hỗ trợ không liên quan. Giao diện vẫn cần truyền đạt một hành vi trung tâm rõ ràng cho bên gọi.

## <a id="custom-functional-interface">Tự định nghĩa giao diện hàm</a>

Nên tạo giao diện hàm riêng khi ý nghĩa nghiệp vụ cần một tên rõ hơn so với giao diện tổng quát, hoặc khi hợp đồng cần đặc điểm mà các giao diện chuẩn không mô tả được, chẳng hạn một ngoại lệ được kiểm tra (checked exception) cụ thể.

```java
@FunctionalInterface
interface DiscountPolicy {
    BigDecimal discountFor(Order order);
}
```

So với `Function<Order, BigDecimal>`, tên `DiscountPolicy` cho biết ngay vai trò của hành vi tại nơi sử dụng:

```java
BigDecimal checkout(Order order, DiscountPolicy policy) {
    return order.total().subtract(policy.discountFor(order));
}
```

Nếu giao diện chuẩn đã diễn đạt đủ rõ hợp đồng thì nên dùng lại giao diện chuẩn. Nếu tên miền nghiệp vụ, tài liệu API hoặc khả năng tiến hóa của hợp đồng quan trọng hơn, giao diện hàm tự định nghĩa thường dễ đọc hơn.

## <a id="functional-interface-contract-violations">Khi hợp đồng giao diện hàm không còn hợp lệ</a>

Trường hợp dễ thấy nhất là thêm phương thức trừu tượng thứ hai không liên quan:

```java
@FunctionalInterface
interface BrokenRule {
    boolean test(String value);
    String describe(); // lỗi biên dịch: không còn là giao diện hàm
}
```

Lỗi cũng có thể đến từ kế thừa khi các khai báo trừu tượng không thể hợp thành một phương thức ghi đè hợp lệ, ví dụ kiểu trả về xung đột.

Nếu giao diện vốn được thiết kế để dùng với lambda, không nên chỉ xóa `@FunctionalInterface` để làm mất lỗi. Hãy xem lại khái niệm trừu tượng:

- giữ một hành vi trừu tượng và chuyển hàm hỗ trợ phù hợp thành phương thức `default` hoặc `static`;
- tách các trách nhiệm không liên quan thành nhiều giao diện;
- dùng giao diện thông thường nếu khái niệm trừu tượng thật sự cần nhiều thao tác trừu tượng.

Mục tiêu không phải biến mọi giao diện thành giao diện hàm. Chỉ dùng mô hình này khi **một hợp đồng hành vi** thực sự mô tả đúng khái niệm trừu tượng.
