# Bắt giữ biến, phạm vi và trạng thái

## <a id="lambda-lexical-scope">Phạm vi của lambda theo vị trí khai báo</a>

Lambda trong Java tuân theo **phạm vi từ vựng (lexical scope)**: tên được dùng bên trong lambda được tìm theo ngữ cảnh mã nguồn bao quanh, tương tự cách tên được phân giải trong phương thức hoặc khối chứa nó.

```java
class PriceService {
    private final BigDecimal taxRate = new BigDecimal("0.10");

    Function<BigDecimal, BigDecimal> taxedPrice() {
        BigDecimal serviceFee = new BigDecimal("2.00");

        return price -> price
                .add(serviceFee)
                .multiply(BigDecimal.ONE.add(taxRate));
    }
}
```

Thân lambda dùng được biến cục bộ `serviceFee` và trường `taxRate` vì cả hai đều nhìn thấy được tại vị trí khai báo lambda.

Lambda không tạo ra một phạm vi đối tượng mới cho `this` hoặc `super`. Đây là điểm khác quan trọng so với lớp vô danh và sẽ được làm rõ ở các mục phía dưới.

## <a id="captured-local-variables">Bắt giữ biến cục bộ</a>

Khi lambda dùng một biến cục bộ được khai báo bên ngoài thân lambda, biến đó được gọi là **biến cục bộ bị bắt giữ (captured local variable)**.

```java
Predicate<String> minimumLength(int min) {
    return text -> text.length() >= min;
}
```

Tham số `min` thuộc lần gọi `minimumLength`, nhưng lambda được trả về có thể được dùng sau khi phương thức này đã kết thúc. Vì vậy Java phải để lambda giữ lại giá trị cần thiết mà không phụ thuộc vào khung ngăn xếp ban đầu.

Biến được khai báo ngay bên trong lambda là biến cục bộ của chính lần thực thi lambda, không phải biến bị bắt giữ:

```java
Function<String, Integer> length = text -> {
    String trimmed = text.trim();
    return trimmed.length();
};
```

Ở đây `trimmed` chỉ tồn tại trong lần gọi lambda.

## <a id="captured-local-values">Lambda giữ lại gì từ biến cục bộ?</a>

Với biến cục bộ bị bắt giữ, lambda giữ lại **giá trị của liên kết biến cục bộ** mà hành vi cần dùng.

Với kiểu nguyên thủy, đó là giá trị nguyên thủy:

```java
int offset = 10;
IntUnaryOperator addOffset = value -> value + offset;
```

Với biến tham chiếu, giá trị được giữ lại là tham chiếu đối tượng:

```java
String prefix = "ID-";
Function<Integer, String> formatter = value -> prefix + value;
```

Lambda không có một liên kết đặc biệt tới một "ô biến cục bộ" có thể bị đổi sang giá trị khác sau đó. Quy tắc không bị gán lại sau khi khởi tạo (effectively final) chính là thứ ngăn việc gán lại biến cục bộ đã được bắt giữ.

Điểm này cũng giải thích vì sao một tham chiếu bị bắt giữ vẫn có thể trỏ tới đối tượng có thể thay đổi: **giá trị tham chiếu** không đổi, nhưng đối tượng mà tham chiếu trỏ tới vẫn có thể thay đổi trạng thái.

## <a id="effectively-final">final và effectively final</a>

Biến cục bộ, tham số phương thức hoặc tham số ngoại lệ được lambda bắt giữ phải là `final` hoặc **không bị gán lại sau khi khởi tạo (effectively final)**.

Một biến effectively final không cần ghi từ khóa `final`, nhưng sau khi được gán giá trị thì không bị gán lại:

```java
int minimum = 3;
Predicate<String> longEnough = text -> text.length() >= minimum;
```

Đoạn này hợp lệ vì `minimum` không bị gán lại.

```java
int minimum = 3;
minimum = 5;

// Không biên dịch vì minimum không còn effectively final.
Predicate<String> longEnough = text -> text.length() >= minimum;
```

Ràng buộc áp dụng cho chính **liên kết biến của biến cục bộ**, không áp dụng cho toàn bộ đồ thị đối tượng mà biến tham chiếu có thể trỏ tới. `final` hoặc effectively final chỉ ngăn biến tham chiếu trỏ sang đối tượng khác; nó không biến đối tượng thành bất biến.

Nếu một hành vi thật sự cần trạng thái thay đổi theo thời gian, nên mô hình hóa trạng thái đó rõ ràng thay vì dùng mẹo như mảng một phần tử chỉ để né quy tắc bắt giữ biến.

## <a id="instance-static-state-access">Truy cập trạng thái của đối tượng và thành viên static</a>

Quy tắc effectively final áp dụng cho biến cục bộ bị bắt giữ. Trường thực thể và trường `static` vẫn tuân theo quy tắc truy cập trường thông thường của Java và có thể được đọc hoặc thay đổi từ lambda nếu chúng nằm trong phạm vi truy cập hợp lệ.

```java
class Counter {
    private int count;
    private static int total;

    Runnable increment = () -> {
        count++;
        total++;
    };
}
```

`count` và `total` là trường nên không phải biến cục bộ bị bắt giữ và không chịu ràng buộc effectively final.

Điều này **không** đồng nghĩa với việc thay đổi trạng thái của trường tự động an toàn. Nếu lambda có thể chạy đồng thời ở nhiều luồng, các vấn đề của Java Memory Model về khả năng nhìn thấy (visibility) và đồng bộ hóa (synchronization) vẫn áp dụng như bình thường. Lambda không tự làm trạng thái có thể thay đổi trở nên an toàn luồng.

## <a id="lambda-this">Ý nghĩa của this bên trong lambda</a>

Bên trong lambda, `this` mang cùng ý nghĩa với `this` ở ngữ cảnh bao quanh. Lambda không tạo một `this` riêng của nó.

```java
class Greeter {
    private final String prefix = "Hello";

    Runnable greeting(String name) {
        return () -> System.out.println(this.prefix + " " + name);
    }
}
```

Trong ví dụ này, `this` vẫn trỏ tới thực thể `Greeter`.

Việc truy cập thành viên không kèm phần định danh (qualifier), cũng như việc dùng `super`, vẫn giữ ngữ nghĩa của phạm vi bao quanh khi hợp lệ tại vị trí đó. Vì vậy lambda giống hành vi được viết ngay trong phương thức hiện tại hơn là một đối tượng lồng có danh tính riêng.

## <a id="lambda-vs-anonymous-class-scope">Phạm vi của lambda và lớp vô danh khác nhau thế nào?</a>

Lớp vô danh tạo ra một phạm vi đối tượng mới. Bên trong phương thức thực thể của lớp vô danh, `this` trỏ tới chính đối tượng vô danh đó. Lambda thì không.

```java
class Demo {
    void showDifference() {
        Runnable anonymous = new Runnable() {
            @Override
            public void run() {
                System.out.println(this.getClass().getName());
            }
        };

        Runnable lambda = () ->
                System.out.println(this.getClass().getName());
    }
}
```

Trong lớp vô danh, `this` là đối tượng `Runnable` vô danh. Trong lambda, `this` là thực thể `Demo` bao quanh.

Vì thế, thay lớp vô danh bằng lambda không phải lúc nào cũng chỉ là rút gọn cú pháp. Nếu mã phụ thuộc vào danh tính của đối tượng vô danh, `this` riêng hoặc thành viên riêng của lớp đó, lambda không phải thay thế tương đương.

## <a id="captured-reference-mutation">Tham chiếu được bắt giữ và đối tượng có thể thay đổi</a>

Effectively final không có nghĩa là bất biến.

```java
List<String> names = new ArrayList<>();

Consumer<String> addName = name -> names.add(name);
addName.accept("Lan");
addName.accept("Minh");
```

Biến cục bộ `names` không bị gán lại nên nó effectively final và có thể được bắt giữ. Tuy nhiên `ArrayList` vẫn có thể thay đổi, vì vậy lambda có thể gọi `add` để thay đổi nội dung đối tượng.

Trường hợp sau lại khác:

```java
List<String> names = new ArrayList<>();
Consumer<String> addName = name -> names.add(name);

// names = new ArrayList<>(); // sẽ làm mất trạng thái effectively final
```

Việc thay đổi đối tượng qua tham chiếu bị bắt giữ là hợp lệ về mặt ngôn ngữ, nhưng cũng là một điểm cần cân nhắc trong thiết kế. Sự thay đổi trạng thái ẩn bên trong hàm gọi lại hoặc chuỗi hành vi có thể khiến thứ tự thực thi và trạng thái dùng chung khó theo dõi. Khi thay đổi trạng thái là phần quan trọng của bài toán, nên làm rõ nơi sở hữu và thời điểm thay đổi thay vì cho rằng dùng lambda sẽ làm việc thay đổi trạng thái trở nên vô hại.
