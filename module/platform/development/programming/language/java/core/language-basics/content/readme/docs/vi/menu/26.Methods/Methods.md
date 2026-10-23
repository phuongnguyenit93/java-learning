<a id="back-to-top"></a>

# Phương thức

## Menu
- [Khai báo phương thức: tên, tham số, kiểu trả về và thân](#methods-001)
- [Định nghĩa phương thức khác với gọi phương thức](#methods-002)
- [Phân biệt tham số hình thức và đối số](#methods-003)
- [Phương thức static cho các chương trình nhập môn](#methods-004)
- [return với giá trị và return trong phương thức void](#methods-005)
- [Truyền tham trị cho kiểu nguyên thủy và tham chiếu](#methods-006)
- [Thay đổi nội dung mảng khác với gán lại tham số mảng](#methods-007)
- [Nạp chồng phương thức và những thành phần tạo chữ ký](#methods-008)
- [Varargs và mối quan hệ với mảng](#methods-009)
- [Đệ quy, trường hợp dừng và nguy cơ StackOverflowError](#methods-010)
- [Tách bài toán thành các phương thức có trách nhiệm rõ ràng](#methods-011)

## <a id="methods-001">Khai báo phương thức: tên, tham số, kiểu trả về và thân</a>

<details>
<summary>Xem chi tiết</summary>

# Khai báo phương thức: tên, tham số, kiểu trả về và thân trong Java

> **Java core — language-basics / Phương thức**  
> **Môi trường:** JDK 17+, ví dụ một file độc lập, không dùng thư viện ngoài.  
> **Mục tiêu:** Tự khai báo và gọi phương thức, xác định rõ **tên**, **danh sách tham số**, **kiểu trả về** và **thân**; hiểu `void`, `return`, phạm vi biến, `static` và phương thức đối tượng.

## 1. Cấu trúc của một khai báo phương thức

Phương thức là một khối hành vi có tên được định nghĩa trong một lớp, interface hoặc kiểu cho phép khai báo phương thức của Java.

~~~java
public static int add(int left, int right) {
    int result = left + right;
    return result;
}
~~~

Các phần trong khai báo:

| Thành phần | Trong ví dụ | Ý nghĩa |
| --- | --- | --- |
| Bộ điều chỉnh | `public static` | Quyền truy cập và thuộc về lớp hay đối tượng |
| Kiểu trả về | `int` | Kết quả mà nơi gọi nhận được |
| Tên phương thức | `add` | Định danh để gọi phương thức |
| Tham số hình thức | `int left, int right` | Hai biến nhận đối số khi gọi |
| Thân phương thức | `{ ... }` | Những câu lệnh chạy khi phương thức được gọi |
| Lệnh kết thúc có giá trị | `return result;` | Trả giá trị và kết thúc lần gọi |

Khi gọi `add(3, 4)`, đối số `3` gán vào tham số `left` và `4` gán vào `right`, nên phương thức trả `7`. Chỉ **khai báo** không làm thân chạy; thân được thực hiện khi có lời gọi phù hợp.

Dạng khái quát:

~~~java
[modifiers] ReturnType methodName(Type1 parameter1, Type2 parameter2) {
    // statements
}
~~~

Dấu ngoặc vuông trong dòng minh họa là **ký hiệu mô tả tùy chọn**, không phải ký tự phải nhập vào Java. Một khai báo có thể **không có tham số**: `static String version() { return "17+"; }`. Các loại phương thức không trừu tượng thông thường có thân trong dấu `{ }`; khai báo phương thức `abstract` hoặc trong interface có thể dùng dấu `;` thay cho thân theo quy tắc riêng.

## 2. Đặt tên và chọn kiểu trả về

Tên phương thức tuân theo quy tắc định danh của Java; thông lệ là **lowerCamelCase** với một động từ hoặc cụm mô tả hành động như `calculateTotal`, `findFirst`, `printReport`:

~~~java
static int square(int number) {
    return number * number;
}

static boolean isPositive(int number) {
    return number > 0;
}

static String title() {
    return "Java 17";
}
~~~

Kiểu trả về có thể là primitive (`int`, `boolean`, `double`...), lớp (`String`), mảng (`int[]`), một interface hoặc `void`.

- Với kiểu khác `void`, `return expression;` phải trả giá trị **gán được** cho kiểu khai báo; các đường điều khiển hoàn tất bình thường không được thiếu kết quả.
- Với `void`, phương thức không cung cấp giá trị trả về; có thể kết thúc bằng `return;` hoặc đi đến cuối thân.
- `return` kết thúc **lần gọi phương thức hiện tại**, không tự động kết thúc `main`, vòng lặp của phương thức gọi hay toàn bộ JVM.

~~~java
static void announce(String text) {
    if (text == null) {
        return; // Kết thúc phương thức void sớm.
    }
    System.out.println(text);
}
~~~

Một phương thức `void` không thể dùng `return 5;`; một phương thức khai báo trả `int` không thể dùng `return "five";`.

## 3. Tham số hình thức khác đối số thực tế

~~~java
static double average(int a, int b) {
    return ((double) a + b) / 2;
}

// Trong phương thức main:
// double result = average(5, 2); // 3.5
~~~

`a` và `b` là **tham số hình thức** (*parameters*) trong khai báo; `5` và `2` là **đối số** (*arguments*) tại lời gọi.

- Đối số tương ứng với tham số theo **thứ tự vị trí**, cần tương thích kiểu; `average(2, 5)` cho cùng kết quả ở đây nhưng không phải mọi phương thức đều đối xứng.
- Trong khai báo, **mỗi tham số cần ghi kiểu**: `int a, int b`, không phải `int a, b` nếu đang viết danh sách tham số phương thức.
- Không thể dùng cùng một tên cho hai tham số trong cùng khai báo.
- Tham số chỉ có phạm vi trong phần thân/phạm vi cho phép của phương thức, không phải biến toàn cục.
- Java **truyền đối số theo giá trị**: với primitive, tham số nhận bản sao giá trị; với đối tượng, tham số nhận bản sao **tham chiếu**.

~~~java
static void incrementLocal(int value) {
    value++;
}

// int count = 10;
// incrementLocal(count);
// count vẫn bằng 10.
~~~

Gán lại một tham số tham chiếu không đổi biến của caller; nhưng gọi phương thức làm thay đổi **đối tượng mutable được tham chiếu chung** có thể làm caller thấy trạng thái mới. Đây vẫn là truyền theo giá trị của tham chiếu, không phải truyền biến theo tham chiếu.

## 4. Thân phương thức, biến cục bộ và luồng điều khiển

Phần thân là một khối lệnh `{ ... }`. Nó có thể khai báo biến cục bộ, kiểm tra điều kiện, lặp, gọi phương thức khác và trả kết quả:

~~~java
static int clamp(int value, int min, int max) {
    if (min > max) {
        throw new IllegalArgumentException("min must not exceed max");
    }
    if (value < min) {
        return min;
    }
    if (value > max) {
        return max;
    }
    return value;
}
~~~

Hàm này trả số đã giới hạn vào đoạn `[min, max]`. Các `return` nằm ở nhánh khác nhau; khi đi tới một `return`, phần còn lại của thân không chạy.

**Phạm vi và khởi tạo:**

~~~java
static int example(int input) {
    int doubled = input * 2; // Biến cục bộ.
    return doubled;
}
// doubled và input không dùng trực tiếp bên ngoài phương thức.
~~~

Biến cục bộ phải được **gán chắc chắn trước khi đọc**. Một khai báo như `int result; return result;` không biên dịch nếu chưa có đường gán hợp lệ. Không thể khai báo trực tiếp **một phương thức bên trong thân một phương thức khác**; hãy đặt nó là thành viên của lớp hoặc một kiểu phù hợp.

**Lỗi biên dịch điển hình:**

~~~java
// SAI: thiếu return cho trường hợp number <= 0.
// static int missingResult(int number) {
//     if (number > 0) return number;
// }

// SAI: kiểu return không tương thích.
// static int wrongType() { return "Java"; }

// SAI: khai báo phương thức lồng trực tiếp trong phương thức.
// static void outer() { int inner() { return 1; } }
~~~

## 5. `static` khác phương thức của đối tượng

Phương thức `static` thuộc về **lớp** và có thể gọi bằng tên lớp. Phương thức **instance** gắn với một đối tượng, nên thường gọi qua tham chiếu đối tượng:

~~~java
public final class Counter {
    private int count;

    public Counter(int initial) {
        count = initial;
    }

    public void increase() { // Phương thức instance.
        count++;
    }

    public int current() { // Phương thức instance.
        return count;
    }

    public static int add(int a, int b) { // Phương thức static.
        return a + b;
    }
}
~~~

Cách gọi (nếu đặt lớp ví dụ vào file hợp lệ):

~~~java
Counter counter = new Counter(2);
counter.increase();
System.out.println(counter.current()); // 3
System.out.println(Counter.add(3, 4)); // 7
~~~

**Đặc biệt:** `Counter(int initial)` là **constructor**, vì tên trùng với lớp và **không có kiểu trả về**; nó không phải phương thức `void`. Ngược lại, `void Counter()` sẽ là một phương thức có tên `Counter`, không phải constructor.

`static` không được dùng trực tiếp biến instance qua `this` khi không có đối tượng. Trong ví dụ chạy ở cuối bài, `main` là `static`; nó gọi các phương thức static trực tiếp và tạo đối tượng trước khi gọi phương thức instance.

## 6. Chữ ký phương thức, nạp chồng và tham số thay đổi

Với các phương thức thông thường trong một lớp, **chữ ký** chủ yếu được phân biệt bằng **tên và kiểu các tham số theo thứ tự**. Tên biến tham số và kiểu trả về **không đủ** để tạo một overload khác:

~~~java
static String describe(int number) {
    return "int:" + number;
}

static String describe(String text) {
    return "String:" + text;
}
~~~

Hai phương thức cùng tên `describe` được **nạp chồng** (*overloading*) vì một cái nhận `int`, cái kia nhận `String`. Lời gọi `describe(12)` chọn phiên bản `int`; `describe("12")` chọn phiên bản `String`. Trình biên dịch dùng kiểu tại nơi gọi để phân giải overload.

~~~java
// SAI nếu đã có static int add(int a, int b):
// static long add(int x, int y) { return x + y; }
// Chỉ đổi tên tham số và kiểu trả về không tạo overload hợp lệ.
~~~

Nếu cần số đối số `int` thay đổi, có thể dùng **varargs**, phải đặt sau cùng trong danh sách tham số:

~~~java
static int sumAll(int... values) {
    int sum = 0;
    for (int value : values) {
        sum += value;
    }
    return sum;
}

// sumAll(2, 3, 4) == 9; sumAll() == 0.
~~~

`int... values` hoạt động như một mảng `int[]` trong thân. Không cần dùng varargs cho mọi bài: số lượng tham số cố định thường làm hợp đồng đơn giản hơn.

## 7. Cú pháp CLI và chương trình Java 17+ runnable

Tạo file **MethodDeclarationDemo.java**:

~~~java
public final class MethodDeclarationDemo {
    private final String prefix;

    // Constructor: không có kiểu trả về.
    public MethodDeclarationDemo(String prefix) {
        this.prefix = prefix;
    }

    // Phương thức instance: cần đối tượng để gọi.
    public String greet(String name) {
        if (name == null || name.isBlank()) {
            return prefix + "bạn";
        }
        return prefix + name.trim();
    }

    public static int add(int left, int right) {
        return left + right;
    }

    private static double average(int a, int b) {
        return ((double) a + b) / 2;
    }

    private static void announce(String label) {
        System.out.println("announce=" + label);
    }

    private static int clamp(int value, int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException("min > max");
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    private static int lengthOrMinusOne(String value) {
        return value == null ? -1 : value.length();
    }

    private static void incrementLocal(int value) {
        value++; // Không đổi biến bên ngoài phương thức.
    }

    private static String describe(int number) {
        return "int:" + number;
    }

    private static String describe(String text) {
        return "String:" + text;
    }

    private static int sumAll(int... values) {
        int sum = 0;
        for (int value : values) {
            sum += value;
        }
        return sum;
    }

    public static void main(String[] args) {
        System.out.println("add=" + add(3, 4));
        System.out.println("average=" + average(5, 2));
        announce("GO");

        System.out.println("clampLow=" + clamp(-5, 0, 10));
        System.out.println("clampMiddle=" + clamp(6, 0, 10));
        System.out.println("clampHigh=" + clamp(18, 0, 10));

        System.out.println("length=" + lengthOrMinusOne("Java"));
        System.out.println("lengthNull=" + lengthOrMinusOne(null));

        MethodDeclarationDemo demo =
                new MethodDeclarationDemo("Xin chào, ");
        System.out.println("greet=" + demo.greet(" Lan "));
        System.out.println("greetBlank=" + demo.greet("  "));

        int counter = 10;
        incrementLocal(counter);
        System.out.println("afterCall=" + counter);

        System.out.println("describeNumber=" + describe(12));
        System.out.println("describeText=" + describe("12"));
        System.out.println("sumAll=" + sumAll(2, 3, 4));
        System.out.println("sumNone=" + sumAll());
    }
}
~~~

Biên dịch và chạy tại thư mục chứa file, trong Bash, PowerShell hoặc Command Prompt:

~~~bash
javac -encoding UTF-8 MethodDeclarationDemo.java
java MethodDeclarationDemo
~~~

**Expected stdout chính xác:**

~~~text
add=7
average=3.5
announce=GO
clampLow=0
clampMiddle=6
clampHigh=10
length=4
lengthNull=-1
greet=Xin chào, Lan
greetBlank=Xin chào, bạn
afterCall=10
describeNumber=int:12
describeText=String:12
sumAll=9
sumNone=0
~~~

`add`/`average` minh họa nhận tham số và trả giá trị; `announce` là `void`. Ba nhánh `clamp` cho thấy nhiều đường `return`; `greet` là phương thức instance có tham chiếu `this` gián tiếp qua trường `prefix`. `afterCall=10` chứng minh tham số `int` không sửa biến caller. Hai `describe` minh họa overload, còn `sumAll()` cho thấy varargs có thể nhận 0 đối số.

## 8. Lỗi thường gặp và bài tập

| Lỗi / hiểu lầm | Cách sửa |
| --- | --- |
| Quên kiểu trả về trong khai báo phương thức | Ghi `T` hoặc `void`; chỉ constructor mới không có kiểu trả về |
| Nhầm tham số và đối số | Tham số đặt trong khai báo; đối số truyền ở nơi gọi |
| Viết `static int add(int a, b)` | Ghi đủ kiểu: `int a, int b` |
| Thiếu `return` trên một đường đi của hàm trả kết quả | Bảo đảm mọi đường hoàn tất bình thường đều trả giá trị |
| `return value;` trong hàm `void` | Dùng `return;` hoặc bỏ return |
| Gọi method instance từ `main` mà không có đối tượng | Tạo đối tượng, rồi gọi qua tham chiếu |
| Muốn overload chỉ bằng đổi kiểu trả về/tên tham số | Đổi danh sách kiểu tham số một cách hợp lệ |
| Mong sửa biến `int` của caller bằng cách sửa tham số | Giá trị tham số là bản sao; trả kết quả rồi gán ở caller |

**Bài 1.** Viết `static int multiply(int a, int b)` và gọi trong `main` với `3, 5`; ghi chính xác giá trị trả về.

**Bài 2.** Viết `static String category(int n)` trả `"NEG"`, `"ZERO"` hoặc `"POS"`, không bỏ sót đường `return`.

**Bài 3.** Viết `static void printIfNonBlank(String text)`, xử lý `null` và chuỗi blank bằng `return;` rồi in khi hợp lệ.

**Bài 4.** Tạo một lớp có trường instance, constructor và phương thức instance trả giá trị; so sánh cách gọi với một phương thức `static`.

**Bài 5.** Viết hai overload `format(int value)` và `format(double value)`. Giải thích vì sao chỉ viết hai hàm cùng tham số nhưng kiểu trả về khác nhau là sai.

**Bài 6.** Dùng `int... values` để viết hàm đếm số phần tử dương; thử không đối số, một đối số và nhiều đối số.

### Ghi nhớ

**Một phương thức được định nghĩa bởi tên, tham số, kiểu trả về và thân lệnh.** Khi gọi, đối số được truyền theo giá trị vào tham số. `return value;` cung cấp kết quả cho phương thức có kiểu trả về, còn `void` không trả giá trị. Dùng `static` khi hành vi thuộc lớp, dùng phương thức instance khi cần trạng thái đối tượng, và phân biệt rõ method với constructor.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="methods-002">Định nghĩa phương thức khác với gọi phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Định nghĩa phương thức khác với gọi phương thức — Java 17+

## 1. Hai thao tác không giống nhau

**Định nghĩa phương thức** (*method declaration/definition*) là viết tên, kiểu tham số, kiểu trả về và thân lệnh để chỉ rõ một công việc **sẽ thực hiện như thế nào**. **Gọi phương thức** (*method invocation*) là yêu cầu thực hiện công việc đó tại một vị trí cụ thể trong chương trình.

~~~java
static void greet() {                    // ĐỊNH NGHĨA
    System.out.println("Hello!");
}

public static void main(String[] args) {
    System.out.println("Start");
    greet();                               // GỌI
    greet();                               // GỌI lần nữa
    System.out.println("End");
}
~~~

**Exact stdout khi chạy lớp chứa mã trên:**

~~~text
Start
Hello!
Hello!
End
~~~

- Phần `static void greet() { ... }` chỉ **mô tả** phương thức; bản thân việc khai báo không in `Hello!`.
- Mỗi lời gọi `greet();` thực hiện thân phương thức **một lần**.
- Khi thân `greet` kết thúc, luồng điều khiển trở lại **ngay sau vị trí gọi**.
- `main` cũng là một phương thức được định nghĩa trong mã. Khi chạy ứng dụng theo cách thông thường, Java launcher/JVM gọi `main` làm điểm bắt đầu.

**Ghi nhớ:** *Định nghĩa = phương thức làm gì; lời gọi = thực hiện khi nào, với dữ liệu nào.*

## 2. Cấu trúc một định nghĩa phương thức

~~~java
static int add(int a, int b) {
    int result = a + b;
    return result;
}
~~~

| Thành phần | Mã | Ý nghĩa |
|---|---|---|
| Modifier | `static` | Cho phép gọi theo lớp, không cần instance |
| Kiểu trả về | `int` | Giá trị trả về là một số nguyên `int` |
| Tên phương thức | `add` | Tên được dùng khi gọi |
| Danh sách tham số | `int a, int b` | Hai **tham số hình thức** có tên và kiểu |
| Thân phương thức | `{ ... }` | Các lệnh chạy khi phương thức được gọi |
| Lệnh trả về | `return result;` | Trả kết quả và kết thúc lời gọi |

**Lời gọi tương ứng:**

~~~java
int total = add(2, 5);
System.out.println(total);
~~~

**Exact stdout:**

~~~text
7
~~~

Ở lời gọi, `2` và `5` là **đối số** (*arguments*), được dùng để khởi tạo các **tham số** `a` và `b` cho lần gọi đó. `add(2, 5)` là một biểu thức có giá trị `7`; `int total = ...` lưu kết quả vào biến.

Không dùng chữ `int` để khai báo kiểu của đối số tại vị trí gọi:

~~~java
// SAI: add(int 2, int 5);
// ĐÚNG: add(2, 5);
~~~

## 3. Chỉ định nghĩa không có nghĩa là tự chạy

~~~java
public final class DefineOnlyDemo {
    static void announce() {
        System.out.println("ANNOUNCE");
    }

    public static void main(String[] args) {
        System.out.println("MAIN");
        // Không hề gọi announce().
    }
}
~~~

**Exact stdout:**

~~~text
MAIN
~~~

Biên dịch bằng `javac` kiểm tra chương trình và tạo class file, **không gọi** `announce()`. Chạy bằng `java` vào `main` cũng không tự gọi `announce()` khi không có câu lệnh invocation.

Nếu thêm `announce();` bên trong `main`, lúc chạy mới xuất hiện dòng `ANNOUNCE`. Nguyên tắc này áp dụng cho cả phương thức `void` và phương thức trả giá trị.

Ngoại lệ về **cơ chế khởi động và khởi tạo lớp** không thay đổi quy tắc trên: khối khởi tạo tĩnh/constructor là cơ chế khác, không phải mọi phương thức bạn định nghĩa đều tự chạy.

## 4. Luồng điều khiển khi gọi phương thức

~~~mermaid
flowchart TD
    A["main: trước lời gọi"] --> B["Đánh giá các đối số"]
    B --> C["Gọi phương thức và tạo ngữ cảnh lời gọi"]
    C --> D["Chạy thân phương thức"]
    D --> E["return giá trị hoặc kết thúc void"]
    E --> F["Quay về vị trí gọi"]
    F --> G["Tiếp tục câu lệnh tiếp theo"]
~~~

Ví dụ:

~~~java
static int square(int x) {
    System.out.println("inside square");
    return x * x;
}

public static void main(String[] args) {
    System.out.println("before");
    int value = square(3);
    System.out.println("result=" + value);
    System.out.println("after");
}
~~~

**Exact stdout:**

~~~text
before
inside square
result=9
after
~~~

Dòng `inside square` ở giữa `before` và `result` vì thân phương thức chạy **trong quá trình đánh giá lời gọi**. Sau `return`, phép gán `value = 9` hoàn thành và `main` thực thi các câu lệnh còn lại.

Một lần gọi thường có bộ tham số/biến cục bộ riêng. Nhiều lời gọi nối tiếp không chia sẻ biến cục bộ chỉ vì dùng cùng tên phương thức.

## 5. Đối số, tham số và thứ tự đánh giá

Java đánh giá các đối số theo thứ tự **từ trái sang phải**, trước khi thân phương thức được gọi.

~~~java
static int trace(String tag, int value) {
    System.out.println("argument " + tag);
    return value;
}

static int sum(int left, int right) {
    System.out.println("inside sum");
    return left + right;
}

// Ở trong main:
int result = sum(trace("LEFT", 2), trace("RIGHT", 5));
System.out.println("result=" + result);
~~~

**Exact stdout:**

~~~text
argument LEFT
argument RIGHT
inside sum
result=7
~~~

Luồng thực hiện:

1. Đánh giá `trace("LEFT", 2)`, nhận `2`.
2. Đánh giá `trace("RIGHT", 5)`, nhận `5`.
3. Gọi `sum(2, 5)`, thực hiện thân `sum` và trả `7`.
4. Gán `result = 7`, rồi in kết quả.

**Sai lầm phổ biến:** tưởng `sum` bắt đầu chạy trước khi các biểu thức đối số được đánh giá.

## 6. `return` và phương thức `void`

Hai dạng thường gặp:

~~~java
static int doubleValue(int value) {
    return value * 2;
}

static void printMessage(String text) {
    System.out.println(text);
}
~~~

Cách gọi:

~~~java
int number = doubleValue(4);
printMessage("value=" + number);
~~~

**Exact stdout:**

~~~text
value=8
~~~

- Phương thức kiểu `int` phải tạo ra giá trị trả về phù hợp trên mọi đường kết thúc bình thường theo luật Java; `return value;` đồng thời **kết thúc** phương thức.
- Phương thức `void` không cung cấp giá trị để gán; nó có thể kết thúc khi chạy hết thân, hoặc dùng `return;` để rời sớm.
- Có thể gọi một phương thức trả giá trị như một statement rồi bỏ qua giá trị: `doubleValue(4);` hợp lệ, nhưng thường cần xem vì sao không dùng kết quả.
- `int x = printMessage("Hi");` **không biên dịch**, vì phương thức `void` không trả `int`.

`System.out.println` cũng là một **lời gọi phương thức**, không phải cú pháp tạo phương thức mới.

## 7. `static` và phương thức instance

**Phương thức `static`** thuộc lớp và có thể gọi qua tên lớp:

~~~java
Math.max(3, 7);
~~~

**Phương thức instance** được gọi trên một đối tượng:

~~~java
String word = "Java";
System.out.println(word.length());
~~~

Ví dụ lớp có trạng thái:

~~~java
static final class Counter {
    private int count;

    int increase(int step) {
        count += step;
        return count;
    }
}

// Trong main:
Counter counter = new Counter();
System.out.println(counter.increase(2));
System.out.println(counter.increase(3));
~~~

**Exact stdout:**

~~~text
2
5
~~~

`increase` chỉ là định nghĩa trong lớp `Counter`. `new Counter()` tạo đối tượng với `count = 0`; **lời gọi** `counter.increase(...)` mới làm tăng `count`.

Gọi phương thức instance cần đối tượng phù hợp. Trong ngữ cảnh `static`, không thể viết `increase(2)` như thể đó là static method của lớp ngoài khi không có đối tượng `Counter`.

## 8. Phương thức có thể gọi phương thức khác

~~~java
static int twice(int value) {
    return value * 2;
}

static int fourfold(int value) {
    int first = twice(value);
    return twice(first);
}
~~~

Gọi `fourfold(3)` làm `twice(3)` trả 6, rồi `twice(6)` trả 12. Java thực thi lần lượt các lời gọi và quay lại phương thức gọi sau mỗi kết quả.

**Lưu ý:** việc định nghĩa `twice` và `fourfold` không tự tạo các lần gọi đó. Chỉ khi thực sự gọi `fourfold(3)` thì các lời gọi bên trong mới diễn ra.

Nếu các phương thức gọi nhau lặp mãi mà thiếu điều kiện dừng (đệ quy vô hạn), chương trình có thể gây `StackOverflowError`. Đây là vấn đề khi **thực thi lời gọi**, không phải do chỉ có nhiều định nghĩa.

## 9. Tác dụng của một lời gọi khi truyền giá trị và tham chiếu

Lời gọi đánh giá biểu thức đối số rồi dùng kết quả để khởi tạo **tham số riêng**. Sửa tham số primitive không làm thay đổi biến caller:

~~~java
static void replaceLocal(int value) { value = 100; }
int original = 5;
replaceLocal(original);
System.out.println(original); // 5.
~~~

Với tham số object, giá trị sao chép là **tham chiếu**: hai biến có thể cùng trỏ tới một đối tượng mutable.

~~~java
static void addMark(StringBuilder text) { text.append("!"); }
StringBuilder message = new StringBuilder("Hi");
addMark(message);
System.out.println(message); // Hi!
~~~

`append` làm thay đổi **object dùng chung**; trái lại, `text = new StringBuilder("Other")` chỉ gán lại biến tham số, không gán lại biến của caller. Điểm chính của bài này là **thân phương thức chỉ tạo tác dụng khi được gọi**. Bài tham số/đối số và bài truyền tham trị phân tích sâu hơn về phạm vi, alias, mảng và `final`.


## 10. Cùng một tên có thể dẫn tới những thân phương thức khác nhau

~~~java
static String describe(int value) { return "int:" + value; }
static String describe(String value) { return "String:" + value; }

System.out.println(describe(7));    // int:7.
System.out.println(describe("Hi")); // String:Hi.
~~~

Hai **định nghĩa** có cùng tên nhưng khác kiểu tham số; mỗi **lời gọi** được compiler phân giải tới overload phù hợp theo kiểu biểu thức đối số. Chỉ thay kiểu trả về hoặc tên biến tham số không tạo overload mới.

Bài hiện tại tập trung vào mối quan hệ *định nghĩa → lựa chọn lời gọi → thực thi thân*. Bài nạp chồng chuyên sâu sẽ giải thích chữ ký, mở rộng kiểu, boxing, varargs, `null` mơ hồ và generic erasure.


## 11. Chương trình Java 17+ hoàn chỉnh

Tạo file đúng tên **`MethodDefinitionCallDemo.java`**:

~~~java
public final class MethodDefinitionCallDemo {
    private MethodDefinitionCallDemo() {}

    static int square(int number) {
        System.out.println("body square " + number);
        return number * number;
    }

    static void greet(String name) {
        System.out.println("body greet " + name);
    }

    static int trace(String tag, int value) {
        System.out.println("arg " + tag + "=" + value);
        return value;
    }

    static int add(int left, int right) {
        System.out.println("body add " + left + "+" + right);
        return left + right;
    }

    static int twice(int value) {
        System.out.println("body twice " + value);
        return value * 2;
    }

    static int fourfold(int value) {
        System.out.println("body fourfold start");
        int first = twice(value);
        int result = twice(first);
        System.out.println("body fourfold end");
        return result;
    }

    static String describe(int value) {
        return "int:" + value;
    }

    static String describe(String value) {
        return "String:" + value;
    }

    static void replaceLocal(int value) {
        value = 100;
        System.out.println("inside local=" + value);
    }

    static void appendMark(StringBuilder builder) {
        builder.append("!");
        System.out.println("inside builder=" + builder);
    }

    static final class Counter {
        private int count;

        int increase(int step) {
            count += step;
            System.out.println("body increase count=" + count);
            return count;
        }
    }

    public static void main(String[] args) {
        System.out.println("main begin");

        int first = square(3);
        System.out.println("square result=" + first);
        System.out.println("square second=" + square(4));

        greet("Mai");

        int total = add(trace("left", 2), trace("right", 5));
        System.out.println("add result=" + total);

        System.out.println("fourfold result=" + fourfold(3));

        System.out.println("overload int=" + describe(7));
        System.out.println("overload String=" + describe("Hi"));

        int original = 5;
        replaceLocal(original);
        System.out.println("outside original=" + original);

        StringBuilder builder = new StringBuilder("Hi");
        appendMark(builder);
        System.out.println("outside builder=" + builder);

        Counter counter = new Counter();
        System.out.println("counter first=" + counter.increase(2));
        System.out.println("counter second=" + counter.increase(3));

        System.out.println("main end");
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên; mở terminal trong thư mục chứa file. Không cần Maven, Gradle, thư viện ngoài JDK hay preview features.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 MethodDefinitionCallDemo.java
java -cp . MethodDefinitionCallDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\MethodDefinitionCallDemo.java
java -cp . MethodDefinitionCallDemo
~~~

### Exact expected stdout

~~~text
main begin
body square 3
square result=9
body square 4
square second=16
body greet Mai
arg left=2
arg right=5
body add 2+5
add result=7
body fourfold start
body twice 3
body twice 6
body fourfold end
fourfold result=12
overload int=int:7
overload String=String:Hi
inside local=100
outside original=5
inside builder=Hi!
outside builder=Hi!
body increase count=2
counter first=2
body increase count=5
counter second=5
main end
~~~

**Cách quan sát:** dòng `body square 3` xuất hiện trước `square result=9`, chứng minh thân chạy ở **thời điểm gọi**. Hai dòng `arg left/right` xuất hiện trước `body add`. `fourfold` gọi `twice` hai lần; biến `original` vẫn là 5 sau khi truyền vào tham số `int`; đối tượng `StringBuilder` đổi nội dung qua phương thức. `Counter` giữ trạng thái vì hai lời gọi được thực hiện trên **cùng object**.

## 12. Những lỗi thường gặp

| Sai lầm | Tại sao sai? | Cách sửa |
|---|---|---|
| Viết phương thức rồi chờ nó tự chạy | Định nghĩa không phải lời gọi | Gọi đúng tên với dấu `()` tại nơi cần |
| Nhầm `add(int a, int b)` với `add(2, 3)` | Một bên khai báo tham số; bên kia cung cấp đối số | Phân biệt cú pháp định nghĩa và gọi |
| Không có `()` khi muốn thực thi | Tên phương thức đơn lẻ không phải lời gọi Java thông thường | Viết `greet();` |
| Gọi sai số đối số/kiểu | Lỗi biên dịch | Khớp chữ ký/overload phù hợp |
| Gán kết quả từ phương thức `void` | `void` không tạo giá trị | Gọi riêng, không gán |
| Nghĩ `return` chỉ in hoặc lưu giá trị | `return` kết thúc phương thức và trả về caller | Dùng kết quả ở biểu thức/lệnh gán |
| Gọi instance method như static method | Thiếu đối tượng | Tạo instance rồi gọi `obj.method()` |
| Nghĩ gán tham số `int` thay biến caller | Java truyền đối số theo giá trị | Trả giá trị mới hoặc thay dữ liệu theo API |
| Nghĩ `StringBuilder` tự sao chép khi truyền | Giá trị tham chiếu trỏ cùng object mutable | Hiểu mutation khác gán lại tham số |
| Chỉ khác kiểu trả về để overload | Không tạo chữ ký khác | Thay số lượng/kiểu/thứ tự tham số phù hợp |
| Cho rằng `javac` thực thi toàn bộ method | Compiler kiểm tra và tạo class file | Dùng `java` và lời gọi thực tế để chạy |
| Cho rằng hai định nghĩa cùng tên/cùng tham số được phép | Khai báo trùng chữ ký | Đổi tên hoặc tạo overload hợp lệ |

## 13. Bài tập có đáp án

### A — Định nghĩa và lời gọi

~~~java
static void ping() {
    System.out.println("PING");
}
public static void main(String[] args) {
    System.out.println("START");
}
~~~

**Câu hỏi:** chạy lớp thì có in `PING` không? **Đáp án:** không; chỉ in `START`. Muốn in `PING`, thêm `ping();` vào `main`.

### B — Đếm số lần gọi

~~~java
static void hi() { System.out.println("HI"); }
public static void main(String[] args) {
    hi();
    hi();
}
~~~

**Exact stdout:**

~~~text
HI
HI
~~~

Định nghĩa một lần, nhưng thân thực thi **hai lần**.

### C — Tham số và đối số

~~~java
static int subtract(int a, int b) {
    return a - b;
}
// Trong main:
System.out.println(subtract(10, 3));
~~~

**Đáp án:** `a` và `b` là tham số; `10` và `3` là đối số; stdout `7`.

### D — `void` hay `int`?

~~~java
static void announce() {
    System.out.println("READY");
}
// int n = announce(); // Có hợp lệ không?
~~~

**Đáp án:** không; `announce()` không trả giá trị. Dùng `announce();` như một câu lệnh riêng.

### E — Đối tượng và trạng thái

~~~java
Counter counter = new Counter();
System.out.println(counter.increase(1));
System.out.println(counter.increase(2));
~~~

**Đáp án:** phương thức instance `increase` cập nhật cùng trường `count`; với lớp Counter ở chương trình chính, stdout gồm `body increase count=1`, `1`, `body increase count=3`, `3` theo thứ tự.

## 14. Checklist và kết luận

- [ ] Phân biệt **định nghĩa** và **gọi** phương thức.
- [ ] Biết khai báo modifier, kiểu trả về, tên và tham số.
- [ ] Gọi với dấu `()` và danh sách đối số đúng.
- [ ] Hiểu thân phương thức chỉ chạy khi lời gọi được thực thi.
- [ ] Biết `return` trả kết quả và quay về caller; `void` không trả giá trị.
- [ ] Hiểu thứ tự đánh giá đối số từ trái sang phải.
- [ ] Phân biệt `static` và instance method.
- [ ] Biết phương thức có thể gọi các phương thức khác.
- [ ] Hiểu truyền theo giá trị, kể cả giá trị tham chiếu object.
- [ ] Nhận diện overloading và lỗi chữ ký/phương thức không phù hợp.
- [ ] Chạy `MethodDefinitionCallDemo.java` bằng Java 17+ và đối chiếu exact stdout.

**Kết luận:** Định nghĩa phương thức là **mô tả một hành vi có thể được dùng lại**; lời gọi phương thức mới **thực thi hành vi đó** với các đối số cụ thể, sau đó nhận kết quả hoặc tiếp tục sau phương thức `void`. Hiểu khác biệt này là nền tảng để đọc đúng luồng thực thi Java.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="methods-003">Phân biệt tham số hình thức và đối số</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt tham số hình thức và đối số — Java 17+

## 1. Hai khái niệm ở hai vị trí khác nhau

Khi **định nghĩa** một phương thức, các biến được khai báo trong danh sách giữa dấu ngoặc tròn là **tham số hình thức** (*formal parameters*). Khi **gọi** phương thức, các biểu thức cung cấp dữ liệu ở vị trí tương ứng là **đối số** (*arguments*).

~~~java
static int add(int left, int right) { // left, right: tham số hình thức.
    return left + right;
}

int result = add(2, 3);              // 2, 3: đối số của lời gọi.
~~~

- `left` và `right` được khai báo với kiểu `int` **trong định nghĩa phương thức**.
- `2` và `3` là hai **biểu thức đối số** tại lời gọi `add(2, 3)`.
- Khi gọi, giá trị của `2` được truyền cho tham số `left`; giá trị của `3` cho `right`.
- `return left + right;` tạo **kết quả trả về**, không phải một tham số hay đối số mới.

**Exact stdout**, nếu in kết quả bằng `System.out.println(result);`:

~~~text
5
~~~

Các thuật ngữ tiếng Việt có thể được diễn đạt là “tham số” và “đối số”; trong tài liệu này luôn dùng **tham số hình thức** cho biến ở phía khai báo, **đối số** cho giá trị/biểu thức ở phía lời gọi.

## 2. So sánh nhanh

| Đặc điểm | Tham số hình thức (*formal parameter*) | Đối số (*argument*) |
|---|---|---|
| Xuất hiện ở đâu? | Danh sách tham số của khai báo phương thức hoặc constructor | Danh sách biểu thức của lời gọi |
| Ví dụ | `int price` trong `cost(int price)` | `20` trong `cost(20)` |
| Có khai báo kiểu? | Có, chẳng hạn `String name` | Là biểu thức có kiểu suy ra/biết được; không phải khai báo tham số |
| Tên do ai đặt? | Người định nghĩa phương thức | Có thể truyền literal, biến hoặc biểu thức tùy ý |
| Phạm vi tồn tại | Trong thân phương thức theo quy tắc scope của Java | Biểu thức thuộc mã nơi lời gọi được viết |
| Có thể được phương thức sử dụng? | Có, bằng tên tham số | Giá trị đối số đã được chuyển đến tham số |
| Quan hệ | Nhận giá trị từ vị trí lời gọi tương ứng | Cung cấp giá trị để khởi tạo tham số tương ứng |

Ví dụ:

~~~java
static void welcome(String guest, int times) {
    for (int i = 0; i < times; i++) {
        System.out.println("Hello " + guest);
    }
}

String person = "Lan";
int count = 2;
welcome(person, count);
~~~

Ở lời gọi, **`person` và `count` là các biểu thức đối số**, không phải tham số hình thức. Chỉ **`guest` và `times`** trong khai báo `welcome` mới là tham số hình thức. Tên của biến phía caller **không cần trùng** tên tham số. Khi chạy đoạn minh họa trong một `main` phù hợp, stdout là:

~~~text
Hello Lan
Hello Lan
~~~

## 3. Ánh xạ vị trí, số lượng và kiểu

Với `static void display(String title, int score)`, lời gọi:

~~~java
display("Java", 90);
~~~

có ánh xạ:

~~~text
Lời gọi:        display("Java", 90)
                         │      │
                         ▼      ▼
Khai báo:        display(String title, int score)
                          title      score
~~~

Phương thức nhận từng giá trị theo **vị trí**. Không có cú pháp gọi theo tên kiểu `display(score = 90, title = "Java")` như một số ngôn ngữ khác; trong Java đây không phải *named arguments*.

Các lỗi phổ biến:

~~~java
// Định nghĩa minh họa:
static void display(String title, int score) { }

// display("Java");             // Thiếu đối số int.
// display("Java", 90, 100);    // Thừa đối số.
// display(90, "Java");         // Thứ tự/kiểu không khớp.
// display("Java", "90");       // String không tự là int.
~~~

Thông thường, số đối số phải phù hợp số tham số hình thức và kiểu đối số phải **tương thích với kiểu tham số theo quy tắc lời gọi Java**. Có những chuyển đổi hợp lệ như từ `int` sang `long`, boxing, hoặc cách xử lý đặc biệt của **varargs**; không nên kết luận các kiểu phải luôn khớp hoàn toàn theo đúng văn bản.

Java không tự lấy “giá trị mặc định của tham số bị thiếu” cho một phương thức bình thường. Khi cần cách gọi với ít dữ liệu hơn, có thể dùng overload hoặc thiết kế tham số thích hợp.

## 4. Đối số không nhất thiết là biến

Đối số là một **biểu thức**, nên nhiều dạng đều hợp lệ:

~~~java
static int square(int number) {
    return number * number;
}

int n = 4;
int a = square(3);          // Literal.
int b = square(n);          // Biến ở caller.
int c = square(n + 1);      // Biểu thức tính toán.
int d = square(square(2));  // Kết quả lời gọi khác.
~~~

Nếu in lần lượt `a`, `b`, `c`, `d`, stdout là:

~~~text
9
16
25
16
~~~

Trong **mọi lần gọi** `square(...)`, tham số hình thức vẫn là `int number`. Chỉ **giá trị đối số** tại từng lời gọi là khác nhau: `3`, `4`, `5`, `4`.

Có thể truyền giá trị trả về của phương thức khác làm đối số. Lời gọi lồng nhau cần được tính ra giá trị trước khi phương thức bên ngoài dùng nó.

## 5. Tham số nhận bản sao **giá trị của biểu thức đối số**

Java luôn **pass-by-value**. Trong lời gọi `f(expression)`, `expression` được tính ở caller, sau đó giá trị dùng để khởi tạo một biến tham số hình thức:

~~~java
static void increase(int number) { number++; }
int original = 5;
increase(original);
System.out.println(original); // 5.
~~~

`original` là một **biểu thức đối số**, `number` là **tham số**; vì là hai biến riêng nên gán lại `number` không đổi `original`.

Với kiểu tham chiếu, giá trị được sao chép là tham chiếu tới object. Hai biến có thể cùng trỏ tới **một mảng**, cho phép phương thức sửa nội dung của mảng chung:

~~~java
static void changeFirst(int[] values) { values[0] = 50; }
int[] data = {1, 2};
changeFirst(data);
System.out.println(java.util.Arrays.toString(data)); // [50, 2].
~~~

Gán lại `values = new int[]{99}` bên trong phương thức **không** làm biến `data` trỏ sang mảng mới. Bài này nhấn mạnh vị trí **khai báo tham số so với cung cấp đối số**; các bài về truyền tham trị và tham số mảng có ví dụ chuyên sâu về object alias, `null`, copy và mutation.


## 6. Tham số là biến cục bộ của lượt gọi

Các tham số có phạm vi trong thân phương thức. Mỗi lần phương thức được gọi, giá trị tham số được sử dụng trong **lần thực thi ấy**; không phải một biến toàn cục được dùng chung giữa mọi lời gọi.

~~~java
static int addBonus(int score) {
    score += 5;
    return score;
}

int first = addBonus(10);
int second = addBonus(30);
System.out.println(first);
System.out.println(second);
~~~

**Exact stdout:**

~~~text
15
35
~~~

Đổi tên tham số trong khai báo, ví dụ từ `score` thành `number`, không bắt caller đổi tên biến đối số. Ngược lại, caller có thể gọi `addBonus(first)`, `addBonus(10)` hoặc `addBonus(5 + 5)`.

Trong một phương thức, không thể khai báo một biến cục bộ khác **trùng tên tham số** khi tham số còn trong phạm vi:

~~~java
static void wrong(int n) {
    // int n = 3; // Lỗi biên dịch: trùng tên tham số.
}
~~~

Tham số có thể che tên một field của lớp; nếu đang ở instance method và cần truy cập field, dùng `this.score` để phân biệt với tham số `score`.

## 7. Tham số `final` và object mutable

Tham số hình thức có thể được khai báo `final`:

~~~java
static void print(final int number) {
    System.out.println(number);
    // number = 20; // Không hợp lệ: không được gán lại final parameter.
}
~~~

Với tham số mảng tham chiếu:

~~~java
static void update(final int[] values) {
    // values = new int[2]; // Không hợp lệ: gán lại tham số final.
    values[0] = 7;          // Hợp lệ: sửa object mảng được tham chiếu.
}
~~~

`final` áp dụng cho **biến tham số**, không tự tạo bản sao đối số và không khiến dữ liệu bên trong object trở thành bất biến. Khi không muốn method sửa mảng caller, có thể sao chép mảng bằng `Arrays.copyOf` rồi thao tác trên bản sao, hoặc không thực hiện mutation.

## 8. Thứ tự đánh giá biểu thức đối số

Trong Java, các biểu thức đối số được đánh giá theo **thứ tự từ trái sang phải**. Các giá trị đã được tính sẽ được truyền đến tham số tương ứng khi lời gọi được thực hiện.

~~~java
static int trace(String label, int value) {
    System.out.println("eval " + label + "=" + value);
    return value;
}

static int subtract(int left, int right) {
    return left - right;
}

int result = subtract(trace("left", 8), trace("right", 3));
System.out.println("result=" + result);
~~~

**Exact stdout:**

~~~text
eval left=8
eval right=3
result=5
~~~

Lời gọi `trace("left", 8)` được đánh giá trước `trace("right", 3)`. Nếu một biểu thức đối số ném ngoại lệ khiến lời gọi không thể tiếp tục, các biểu thức đối số phía sau có thể **không được đánh giá**.

Trong ví dụ số học đơn giản không tác dụng phụ, thứ tự dễ bị bỏ qua; khi đối số gọi method làm thay đổi trạng thái, việc hiểu thứ tự từ trái sang phải giúp tránh dự đoán sai kết quả.

## 9. Constructor và varargs vẫn dùng quy tắc tham số/đối số

Constructor cũng có **tham số hình thức** trong khai báo và nhận **đối số** khi dùng `new`:

~~~java
class Book {
    final String title;
    Book(String title) { this.title = title; }
}
Book book = new Book("Java"); // "Java" là đối số.
~~~

Một tham số `int... values` (*varargs*) có thể nhận nhiều phần tử mà caller cung cấp, nhưng trong thân phương thức, `values` chính là **một biến mảng `int[]`**:

~~~java
static int sum(int... values) {
    int result = 0;
    for (int n : values) result += n;
    return result;
}
System.out.println(sum());           // 0.
System.out.println(sum(2, 3));       // 5.
System.out.println(sum(new int[]{4})); // 4.
~~~

Đừng nhầm **số biểu thức đối số** ở `sum(2, 3)` với **số tham số hình thức**: khai báo chỉ có một `values`. Bài varargs/mảng sẽ xét chi tiết cách gọi, mảng có sẵn, `null` và overload.


## 10. Chương trình Java 17+ có thể chạy độc lập

Tạo file đúng tên **`ParametersArgumentsDemo.java`**:

~~~java
import java.util.Arrays;

public final class ParametersArgumentsDemo {
    private ParametersArgumentsDemo() {}

    static void welcome(String guest, int times) {
        for (int i = 0; i < times; i++) {
            System.out.println("Hello " + guest);
        }
    }

    static int add(int left, int right) {
        return left + right;
    }

    static int square(int number) {
        return number * number;
    }

    static void increasePrimitive(int value) {
        value++;
        System.out.println("inside primitive=" + value);
    }

    static void changeArray(int[] values) {
        values[0] += 10;
        System.out.println("inside array=" + Arrays.toString(values));
    }

    static void rebindArray(int[] values) {
        values = new int[]{99};
        System.out.println("inside rebound=" + Arrays.toString(values));
    }

    static void changeString(String text) {
        text = "B";
        System.out.println("inside string=" + text);
    }

    static int trace(String label, int value) {
        System.out.println("eval " + label + "=" + value);
        return value;
    }

    static int subtract(int left, int right) {
        return left - right;
    }

    static int sum(int... numbers) {
        int total = 0;
        for (int number : numbers) {
            total += number;
        }
        return total;
    }

    static void showVarargs(String tag, int... numbers) {
        System.out.println(tag + " values=" + Arrays.toString(numbers)
                + " sum=" + sum(numbers));
    }

    public static void main(String[] args) {
        int result = add(2, 3);
        System.out.println("add=" + result);

        String person = "Lan";
        int repeat = 2;
        welcome(person, repeat);

        System.out.println("literal square=" + square(3));
        System.out.println("expression square=" + square(2 + 2));
        System.out.println("nested square=" + square(square(2)));

        int original = 5;
        increasePrimitive(original);
        System.out.println("outside primitive=" + original);

        int[] data = {1, 2};
        changeArray(data);
        System.out.println("outside array=" + Arrays.toString(data));
        rebindArray(data);
        System.out.println("outside rebound=" + Arrays.toString(data));

        String originalText = "A";
        changeString(originalText);
        System.out.println("outside string=" + originalText);

        int difference = subtract(
                trace("left", 8), trace("right", 3));
        System.out.println("subtract=" + difference);

        showVarargs("empty");
        showVarargs("many", 3, 4);
        int[] existing = {4, 5};
        showVarargs("existing", existing);
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên; không cần thư viện ngoài JDK hoặc tính năng preview.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 ParametersArgumentsDemo.java
java -cp . ParametersArgumentsDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\ParametersArgumentsDemo.java
java -cp . ParametersArgumentsDemo
~~~

### Exact expected stdout

~~~text
add=5
Hello Lan
Hello Lan
literal square=9
expression square=16
nested square=16
inside primitive=6
outside primitive=5
inside array=[11, 2]
outside array=[11, 2]
inside rebound=[99]
outside rebound=[11, 2]
inside string=B
outside string=A
eval left=8
eval right=3
subtract=5
empty values=[] sum=0
many values=[3, 4] sum=7
existing values=[4, 5] sum=9
~~~

**Đọc theo thứ tự:** `add` ghép cặp hai đối số `2,3` với hai tham số `left,right`; `welcome` nhận đối số từ **biến** chứ không nhất thiết từ literal. Thay `value` nguyên thủy trong method không làm đổi caller, nhưng thay phần tử qua tham số `int[]` có thể đổi object mà caller giữ. Gán lại tham số `values` hoặc tham số `text` không đổi biến caller. Hai biểu thức `trace` cho thấy thứ tự đánh giá trái → phải. Varargs sử dụng một tham số `int[]` nhận các dạng đối số linh hoạt.

## 11. Các nhầm lẫn thường gặp

| Nhầm lẫn | Giải thích / cách sửa |
|---|---|
| Gọi biến `n` ở caller là “tham số hình thức” | Ở vị trí `f(n)`, `n` là **biểu thức đối số** |
| Phải đặt tên biến caller giống tham số | Không; ánh xạ theo **vị trí** và khả năng tương thích kiểu |
| Đối số luôn là literal | Có thể là biến, phép tính hoặc kết quả method |
| Đổi tham số `int` sẽ đổi biến caller | Java truyền giá trị nguyên thủy bằng bản sao |
| Java truyền mảng theo kiểu pass-by-reference | Java sao chép **giá trị tham chiếu**; mảng được tham chiếu có thể bị sửa |
| Gán `values = new int[...]` làm caller đổi tham chiếu | Chỉ gán lại biến tham số |
| `return` là một tham số khác | Return value là **kết quả** mà phương thức trả về |
| Quên số/thứ tự/kiểu đối số | Compiler cần lời gọi phù hợp khai báo/overload |
| Tưởng `int... numbers` khai báo nhiều tham số | Đó là **một** tham số varargs kiểu mảng trong thân |
| Cho rằng argument expressions luôn chạy đồng thời | Java đánh giá từ **trái sang phải** |

## 12. Bài tập và đáp án

**A — Chỉ ra vị trí:**

~~~java
static void printScore(String name, int score) {
    System.out.println(name + ":" + score);
}
printScore("An", 90);
~~~

**Đáp án:** `String name`, `int score` là hai **tham số hình thức**; `"An"`, `90` là hai **đối số**.

**B — Dùng biến caller:**

~~~java
int current = 10;
System.out.println(square(current + 1));
~~~

**Đáp án:** đối số là toàn bộ biểu thức `current + 1`, giá trị bằng 11; stdout là **`121`**.

**C — Dự đoán thay đổi biến:**

~~~java
static void setValue(int value) {
    value = 99;
}
int x = 7;
setValue(x);
System.out.println(x);
~~~

**Đáp án:** **`7`**, vì phép gán tác động tham số `value`, không tác động biến `x`.

**D — Dự đoán thay đổi mảng:**

~~~java
static void setFirst(int[] array) {
    array[0] = 99;
}
int[] a = {1};
setFirst(a);
System.out.println(a[0]);
~~~

**Đáp án:** **`99`**, vì phương thức sửa phần tử trên **đối tượng mảng được tham chiếu chung**.

**E — Với varargs:** `static int sum(int... numbers)` có bao nhiêu tham số hình thức?  
**Đáp án:** **một** (`numbers`), kiểu mảng `int[]` trong thân. Lời gọi `sum(1,2,3)` cung cấp ba phần tử được đóng gói cho tham số varargs.

## 13. Checklist và kết luận

- [ ] Phân biệt **khai báo tham số hình thức** và **biểu thức đối số** tại lời gọi.
- [ ] Hiểu đối số được ánh xạ theo vị trí vào tham số tương ứng.
- [ ] Biết kiểu/số lượng đối số phải phù hợp khai báo, trừ các quy tắc đặc biệt như varargs.
- [ ] Biết đối số có thể là literal, biến, biểu thức hoặc lời gọi khác.
- [ ] Hiểu Java luôn **pass-by-value**, kể cả khi truyền giá trị tham chiếu object/mảng.
- [ ] Phân biệt sửa object qua tham số và gán lại biến tham số.
- [ ] Hiểu tham số là biến trong phạm vi lần gọi, khác với biến caller.
- [ ] Nhận diện tham số `final` và giới hạn của nó.
- [ ] Biết Java đánh giá biểu thức đối số từ trái sang phải.
- [ ] Chạy `ParametersArgumentsDemo.java` với JDK 17+ và đối chiếu exact stdout.

**Kết luận:** **Tham số hình thức** là các biến được **khai báo** ở phía phương thức; **đối số** là các biểu thức cung cấp giá trị tại **lời gọi**. Java truyền giá trị đối số theo thứ tự vào tham số tương ứng. Với kiểu nguyên thủy, giá trị được sao chép; với mảng/object, giá trị **tham chiếu** được sao chép — đây là chìa khóa để hiểu khi nào phương thức có thể làm thay đổi dữ liệu mà caller quan sát.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="methods-004">Phương thức static cho các chương trình nhập môn</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức `static` cho các chương trình nhập môn Java

> **Java core — language-basics / Phương thức**  
> **Môi trường:** JDK 17+, một file Java độc lập, không cần thư viện ngoài.  
> **Mục tiêu:** Tạo và gọi phương thức `static` từ `main`; sử dụng tham số, giá trị trả về hoặc `void`; hiểu vì sao `static` thuộc về lớp, khác phương thức instance và khi nào không nên lạm dụng trạng thái static.

## 1. `static` nghĩa là phương thức thuộc về lớp

Phương thức có từ khóa `static` được gắn với **lớp**, không cần tạo đối tượng của lớp mới gọi được:

~~~java
public final class MathHelper {
    public static int add(int left, int right) {
        return left + right;
    }
}

// Có thể gọi MathHelper.add(3, 4), trả 7.
~~~

Các thành phần chính:

| Phần khai báo | Trong ví dụ | Vai trò |
| --- | --- | --- |
| Bộ điều chỉnh | `public static` | Cho phép gọi từ nơi có quyền truy cập mà không cần đối tượng |
| Kiểu trả về | `int` | Kết quả phép cộng |
| Tên phương thức | `add` | Tên dùng ở lời gọi |
| Tham số | `int left, int right` | Nhận hai đối số kiểu int |
| Thân | `{ return left + right; }` | Lệnh thực thi và trả kết quả |

Dạng tổng quát: `[access] static ReturnType name(Type parameter, ...) { ... }`. Dấu `[...]` trong dòng mô tả chỉ biểu thị thành phần tùy chọn, **không phải cú pháp Java** phải gõ.

Không nên hiểu “static” nghĩa là phương thức chỉ chạy một lần hoặc không thể nhận tham số: mỗi lần gọi vẫn thực thi thân với đối số được cung cấp. `static` chỉ xác định **cách phương thức thuộc về lớp và truy cập thành viên**.

## 2. `main` là phương thức `static` quen thuộc nhất

Với chương trình Java 17 chạy trực tiếp từ CLI, điểm bắt đầu thông thường:

~~~java
public static void main(String[] args) {
    System.out.println(add(2, 3));
}
~~~

Ý nghĩa:

- `public`: cho phép Java launcher truy cập điểm vào.
- `static`: launcher gọi `main` **không cần tạo một đối tượng** của lớp chứa nó.
- `void`: phương thức không trả giá trị cho nơi gọi; kết quả chương trình được quan sát qua in ra hoặc tác động khác.
- `main`: tên phương thức điểm vào trong dạng chuẩn này.
- `String[] args`: mảng nhận các đối số CLI; tên biến có thể khác `args`.

Khi `add` là một phương thức static hợp lệ nằm trong cùng lớp, có thể gọi ngay bằng `add(2, 3)`. Cách viết tường minh hơn là `ClassName.add(2, 3)`; thường nên dùng tên lớp khi gọi static từ bên ngoài.

~~~java
public final class Starter {
    private static int add(int a, int b) {
        return a + b;
    }

    public static void main(String[] args) {
        System.out.println("sum=" + add(2, 3));
        System.out.println("qualified=" + Starter.add(4, 5));
    }
}
~~~

Biên dịch/lệnh chạy với lớp `Starter` phải dùng file `Starter.java` khi lớp là `public`. Dòng `main` không tự chạy khi chỉ có khai báo; nó được launcher gọi khi thực thi `java Starter`.

## 3. Tham số, kiểu trả về và `void` hoạt động như bình thường

`static` không làm thay đổi quy tắc tham số và giá trị trả về:

~~~java
static double average(int a, int b) {
    return ((double) a + b) / 2;
}

static boolean isEven(int number) {
    return number % 2 == 0;
}

static void greet(String name) {
    if (name == null || name.isBlank()) {
        System.out.println("hello=guest");
        return; // Kết thúc sớm vì phương thức trả void.
    }
    System.out.println("hello=" + name.trim());
}
~~~

- `average(5, 2)` trả `3.5`. Ép `(double) a` trước khi cộng/chia giữ phần thập phân và tránh cộng hai `int` trước khi chuyển kiểu.
- `isEven(8)` trả `true`; `isEven(7)` trả `false`.
- `greet(" Lan ")` in `hello=Lan`; `greet(null)` in `hello=guest`.
- Phương thức có kiểu trả về khác `void` cần cung cấp giá trị phù hợp trên tất cả các đường đi hoàn tất bình thường. Phương thức `void` có thể dùng `return;`, nhưng không thể trả một giá trị như `return 5;`.

**Phân biệt:** Các số, chuỗi hay biến đặt trong dấu ngoặc **lúc gọi** là *đối số* (*arguments*); biến có kiểu được khai báo **trong định nghĩa phương thức** là *tham số* (*parameters*). Java truyền đối số **theo giá trị**, kể cả khi giá trị đó là tham chiếu đối tượng.

## 4. Gọi phương thức `static` trong cùng lớp hoặc lớp khác

Trong cùng lớp, gọi không kèm tên lớp thường dễ đọc:

~~~java
static int square(int n) {
    return n * n;
}

static int squarePlusOne(int n) {
    return square(n) + 1; // Gọi một phương thức static khác.
}
~~~

Khi gọi phương thức static của **lớp khác**, dùng tên lớp. Ví dụ quen thuộc trong JDK:

~~~java
int bigger = Math.max(4, 9);           // 9
String text = Integer.toString(42);   // "42"
~~~

`Math.max` và `Integer.toString` là phương thức static: không cần `new Math()` hay `new Integer(...)`. Java có cơ chế `static import` cho phép rút gọn tên lớp, nhưng trong bài nhập môn nên giữ `Math.max(...)` để thấy nguồn gốc phương thức.

Không bắt buộc mọi phương thức trong ứng dụng đều phải static. Chọn static khi hành vi có thể làm việc chỉ qua các tham số hoặc các tài nguyên lớp phù hợp, không phụ thuộc trạng thái riêng của một đối tượng.

## 5. Hạn chế: không có `this` và không truy cập instance trực tiếp

Một phương thức instance chạy với một đối tượng hiện tại, có thể dùng `this`. Một phương thức static **không có đối tượng `this` ngầm định**:

~~~java
public final class Multiplier {
    private final int factor;

    public Multiplier(int factor) {
        this.factor = factor;
    }

    public int multiply(int value) { // Instance method.
        return factor * value;
    }

    public static int triple(int value) { // Static method.
        return value * 3;
    }
}
~~~

Lời gọi hợp lệ:

~~~java
Multiplier item = new Multiplier(4);
System.out.println(item.multiply(3));       // 12.
System.out.println(Multiplier.triple(3));   // 9.
~~~

Trong phương thức static, không thể sử dụng `this.factor` hoặc gọi `multiply(3)` trực tiếp khi chưa có đối tượng phù hợp:

~~~java
// KHÔNG BIÊN DỊCH:
// static int wrong(int n) {
//     return factor * n; // factor là instance field.
// }
// static int wrongThis(int n) {
//     return this.factor * n; // 'this' không tồn tại trong static context.
// }
~~~

Nếu thật sự cần gọi phương thức instance từ một phương thức static, phải **có đối tượng** (ví dụ nhận đối tượng qua tham số hoặc tạo bằng `new`), rồi gọi `item.multiply(3)`.

| Tiêu chí | Phương thức `static` | Phương thức instance |
| --- | --- | --- |
| Thuộc về | Lớp | Đối tượng |
| Cách gọi nên dùng | `Type.method(args)` | `object.method(args)` |
| Cần đối tượng của lớp khi gọi? | Không | Có |
| Sử dụng `this` trực tiếp? | Không | Có |
| Truy cập trường instance trực tiếp? | Không | Có, trên đối tượng hiện tại |
| Gọi thành viên static khác? | Có | Có |

Constructor là cơ chế tạo/khởi tạo đối tượng, **không phải phương thức static**, và không khai báo kiểu trả về.

## 6. Trường `static`: trạng thái chung và nguy cơ khi học lập trình

Có thể khai báo một trường thuộc lớp để nhiều lần gọi phương thức static dùng chung:

~~~java
static int callCount = 0;

static int recordCall() {
    callCount++;
    return callCount;
}
~~~

Trong một lần chạy chương trình thông thường, hai lần gọi liên tiếp `recordCall()` sẽ trả `1` rồi `2`. Trạng thái **không reset về 0 ở mỗi lần gọi** vì `callCount` là trường của lớp chứ không phải biến cục bộ của phương thức.

Tuy nhiên, trạng thái static mutable làm cho kết quả phụ thuộc vào lịch sử lời gọi và gây khó kiểm thử. Khi nhiều luồng cùng sửa `callCount++`, thao tác này **không tự bảo đảm an toàn luồng**. Với bài nhập môn, ưu tiên các hàm static nhận tham số và trả kết quả rõ ràng:

~~~java
static int calculateTotal(int price, int quantity) {
    return price * quantity;
}
~~~

Phép tính trên vẫn có nguy cơ tràn `int` nếu giá trị lớn; cần kiểu hoặc kiểm tra thích hợp trong ứng dụng thực. `static final` phù hợp cho **hằng số bất biến theo hợp đồng** (ví dụ `static final int MAX_RETRIES = 3;`), nhưng `final` trên tham chiếu tới một đối tượng mutable không tự làm đối tượng bất biến.

## 7. Chương trình Java 17+ chạy độc lập

Tạo file **StaticMethodsIntroDemo.java**:

~~~java
public final class StaticMethodsIntroDemo {
    private static int callCount = 0;

    private final int factor;

    public StaticMethodsIntroDemo(int factor) {
        this.factor = factor;
    }

    public int multiply(int value) {
        return factor * value;
    }

    private static int add(int left, int right) {
        return left + right;
    }

    private static double average(int a, int b) {
        return ((double) a + b) / 2;
    }

    private static boolean isEven(int n) {
        return n % 2 == 0;
    }

    private static String category(int n) {
        if (n < 0) {
            return "NEG";
        }
        if (n == 0) {
            return "ZERO";
        }
        return "POS";
    }

    private static void greet(String name) {
        if (name == null || name.isBlank()) {
            System.out.println("hello=guest");
            return;
        }
        System.out.println("hello=" + name.trim());
    }

    private static int recordCall() {
        callCount++;
        return callCount;
    }

    public static void main(String[] args) {
        System.out.println("add=" + add(3, 4));
        System.out.println("qualifiedAdd="
                + StaticMethodsIntroDemo.add(5, 6));
        System.out.println("average=" + average(5, 2));
        System.out.println("even8=" + isEven(8));
        System.out.println("even7=" + isEven(7));

        System.out.println("negative=" + category(-2));
        System.out.println("zero=" + category(0));
        System.out.println("positive=" + category(9));

        greet(" Lan ");
        greet(null);

        System.out.println("mathMax=" + Math.max(4, 9));
        System.out.println("callOne=" + recordCall());
        System.out.println("callTwo=" + recordCall());

        StaticMethodsIntroDemo first = new StaticMethodsIntroDemo(3);
        StaticMethodsIntroDemo second = new StaticMethodsIntroDemo(10);
        System.out.println("instanceOne=" + first.multiply(4));
        System.out.println("instanceTwo=" + second.multiply(2));
        System.out.println("classCounter=" + StaticMethodsIntroDemo.callCount);
    }
}
~~~

Biên dịch và chạy tại thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 StaticMethodsIntroDemo.java
java StaticMethodsIntroDemo
~~~

**Expected stdout chính xác:**

~~~text
add=7
qualifiedAdd=11
average=3.5
even8=true
even7=false
negative=NEG
zero=ZERO
positive=POS
hello=Lan
hello=guest
mathMax=9
callOne=1
callTwo=2
instanceOne=12
instanceTwo=20
classCounter=2
~~~

Hai lời gọi `add` cho thấy static có thể gọi trực tiếp hoặc qua tên lớp. `instanceOne` và `instanceTwo` khác nhau vì hai đối tượng có `factor` khác nhau. `callOne`/`callTwo` và `classCounter` minh họa một trường static dùng chung trong cùng lần chạy.

## 8. Lỗi thường gặp, bài tập và checklist

| Lỗi / hiểu lầm | Cách xử lý |
| --- | --- |
| Cho rằng phải `new` mới gọi được static | Gọi `Type.method(...)` hoặc trực tiếp trong cùng lớp |
| Dùng `this` trong phương thức static | Không có `this`; cần đối tượng nếu truy cập state instance |
| Gọi phương thức instance từ `main` không qua đối tượng | Tạo/nhận đối tượng rồi gọi `object.method(...)` |
| Nghĩ static chạy chỉ một lần | Mỗi lần gọi đều thực thi thân phương thức |
| Nghĩ biến cục bộ trong static tồn tại mãi giữa các lần gọi | Biến cục bộ được tạo theo mỗi lần gọi; trường static thuộc lớp |
| Dùng static mutable field cho mọi kết quả trung gian | Ưu tiên tham số + return để tránh state dùng chung |
| Quên `return value;` trong hàm static trả `int` | Các nhánh hoàn tất bình thường phải trả giá trị đúng kiểu |
| Dùng `void` rồi cố lấy kết quả từ biểu thức gọi | `void` không cung cấp giá trị để gán |

**Bài 1.** Viết `static int cube(int n)` rồi gọi từ `main` với `n = 3`. Dự đoán kết quả.

**Bài 2.** Viết `static boolean isAdult(int age)` trả true khi `age >= 18`. In kết quả với tuổi 17, 18 và 21.

**Bài 3.** Viết `static void printIfPresent(String value)`: bỏ qua null hoặc chuỗi trắng bằng `return;`, in nếu có nội dung.

**Bài 4.** Tạo lớp có trường `factor` và phương thức instance `multiply`. Thử gọi từ `main` trước/sau khi tạo đối tượng; xác định lỗi biên dịch.

**Bài 5.** So sánh biến cục bộ `int calls = 0` khai báo trong hàm với trường `static int callCount` sau hai lần gọi. Vì sao kết quả khác nhau?

**Checklist:** Phương thức có thực sự không cần `this` hoặc trạng thái của một đối tượng không? Tham số/kiểu trả về/`void` đã đúng chưa? Tên lớp/file và lệnh CLI có khớp không? Có state static mutable gây phụ thuộc giữa các lần gọi không?

### Ghi nhớ

**`static` đặt phương thức ở cấp lớp**: rất phù hợp cho `main` và các hàm tiện ích nhỏ nhận tham số rồi trả kết quả. Phương thức static không có `this`, không trực tiếp truy cập thành viên instance nếu thiếu đối tượng. Khi cần trạng thái riêng cho từng đối tượng, dùng phương thức instance. Đừng nhầm `static method` với `static field`: trường static có thể giữ trạng thái **chung**, nên cần dùng có chủ đích.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="methods-005">return với giá trị và return trong phương thức void</a>

<details>
<summary>Xem chi tiết</summary>

# `return` với giá trị và `return` trong phương thức `void` — Java 17+

## 1. Quy tắc cốt lõi

Trong Java, `return` **kết thúc lời gọi phương thức hiện tại** và chuyển điều khiển về nơi đã gọi. Việc có thể trả một giá trị hay không phụ thuộc **kiểu trả về trong khai báo phương thức**.

| Kiểu phương thức | Lệnh hợp lệ | Ý nghĩa |
|---|---|---|
| `int`, `boolean`, `String`… | `return expression;` | Tính biểu thức, trả giá trị phù hợp với kiểu khai báo và thoát phương thức |
| `void` | `return;` | Thoát sớm, **không trả giá trị** |
| `void` | Chạy hết thân mà không gặp `return` | Cũng kết thúc bình thường, không trả giá trị |

~~~java
static int square(int n) {
    return n * n;        // Giá trị int.
}

static void printIfPositive(int n) {
    if (n <= 0) return;  // Thoát sớm, không có biểu thức.
    System.out.println(n);
}
~~~

`square(4)` có giá trị `16`; `printIfPositive(4)` in `4` nhưng **không có giá trị để gán vào biến**. Trong cả hai trường hợp, `return` đều kết thúc lời gọi hiện tại.

## 2. `return expression;` trong phương thức không phải `void`

~~~java
static int larger(int a, int b) {
    if (a >= b) {
        return a;
    }
    return b;
}
~~~

Gọi `larger(3, 8)` trả `8`. Biểu thức sau `return` phải tạo giá trị **tương thích với kiểu trả về** của phương thức theo luật chuyển đổi của Java.

Các ví dụ khác:

~~~java
static boolean isAdult(int age) {
    return age >= 18;
}

static String status(boolean active) {
    return active ? "ACTIVE" : "INACTIVE";
}
~~~

**Đúng:** `return age >= 18;` vì biểu thức có kiểu `boolean`. **Sai:** trong phương thức khai báo `int`, viết `return "hello";` hoặc `return;` sẽ không biên dịch.

Với kiểu tham chiếu như `String`, `return null;` có thể hợp lệ về kiểu, nhưng caller cần xử lý null theo hợp đồng API. Với kiểu nguyên thủy như `int`, không thể `return null;`.

## 3. Phương thức trả giá trị phải xử lý đủ đường đi

~~~java
// SAI nếu bỏ chú thích: có đường flag == false không trả int.
// static int broken(boolean flag) {
//     if (flag) return 1;
// }
~~~

Compiler sẽ báo lỗi thiếu `return` khi một phương thức không phải `void` có thể **hoàn tất bình thường** mà chưa trả giá trị.

Sửa:

~~~java
static int choose(boolean flag) {
    if (flag) {
        return 1;
    }
    return 2;
}
~~~

Một đường đi có thể kết thúc bằng `throw` thay vì trả về:

~~~java
static int positiveOnly(int value) {
    if (value <= 0) {
        throw new IllegalArgumentException("must be positive");
    }
    return value;
}
~~~

**Không phải mọi dòng trong thân đều phải chứa `return`**; yêu cầu là các đường thực thi có thể kết thúc bình thường phải trả giá trị. Một nhánh `throw` kết thúc đột ngột và không cần giá trị trả về.

## 4. `return;` trong phương thức `void`: guard clause

`void` có nghĩa là phương thức không trả giá trị; `return;` hữu ích để **ngăn chạy phần còn lại** nếu đầu vào không phù hợp.

~~~java
static void announce(String message) {
    if (message == null || message.isBlank()) {
        System.out.println("skip empty");
        return;
    }
    System.out.println("announce:" + message);
}
~~~

Với `announce(null)`, chương trình in `skip empty` rồi thoát phương thức. Với `announce("Hi")`, điều kiện false nên chạy `announce:Hi`.

Lưu ý:

- `return;` hợp lệ trong phương thức `void`.
- `return 1;` **không hợp lệ** trong phương thức `void`.
- Không bắt buộc viết `return;` ở cuối phương thức `void`; Java tự kết thúc khi thân chạy hết.
- `void` không phải là một giá trị `null` hay số `0`; không viết `int x = announce("Hi");`.

Một lệnh `return` trong `main(String[] args)` (có kiểu `void`) cũng kết thúc `main` mà không trả giá trị.

## 5. `return` khác `break` và `continue`

Đặt lệnh trong một vòng lặp:

| Lệnh | Phạm vi tác động |
|---|---|
| `return value;` | Kết thúc **phương thức** hiện tại và trả giá trị |
| `return;` | Kết thúc phương thức `void` |
| `break;` | Thoát vòng lặp hoặc `switch` gần nhất phù hợp |
| `continue;` | Bỏ phần còn lại của lượt lặp gần nhất, chuyển sang lượt kế tiếp |

~~~java
static int firstEven(int[] numbers) {
    for (int number : numbers) {
        if (number % 2 == 0) {
            return number; // Thoát cả phương thức, không chỉ thoát vòng.
        }
    }
    return -1; // Không tìm thấy.
}
~~~

`firstEven(new int[]{3, 7, 10, 12})` trả `10` ngay khi gặp phần tử chẵn đầu tiên. Còn `firstEven(new int[]{3, 7})` trả `-1`.

Đừng thay `return number;` bằng `break;` nếu yêu cầu là **trả số tìm thấy**: `break` chỉ ra khỏi vòng và phương thức vẫn cần đường trả giá trị.

## 6. Thứ tự thực thi và câu lệnh không thể tới

~~~java
static int twice(int n) {
    System.out.println("inside twice");
    return n * 2;
}

System.out.println("before");
System.out.println("result=" + twice(4));
System.out.println("after");
~~~

**Exact stdout** khi ba câu gọi cuối nằm trong `main`:

~~~text
before
inside twice
result=8
after
~~~

Java đánh giá biểu thức sau `return`, kết thúc lời gọi, rồi caller tiếp tục. Lệnh nằm **trực tiếp sau một `return` vô điều kiện trong cùng block** là *unreachable statement* và gây lỗi biên dịch:

~~~java
// static int invalid() {
//     return 5;
//     System.out.println("never"); // Unreachable.
// }
~~~

`return` trong một nhánh `if` không tự làm mọi câu lệnh sau `if` trở nên unreachable: nhánh đó có thể không được chọn.

**`try/finally`:** Nếu `return` xảy ra trong `try`, `finally` vẫn được thực hiện trước khi việc trả về hoàn tất, theo quy tắc của Java. Tránh đặt `return` mới trong `finally` vì có thể làm thay đổi kết quả hoặc che ngoại lệ.

## 7. Java truyền và trả giá trị thế nào?

~~~java
static int changeLocal(int n) {
    n = 99;
    return n;
}

int x = 4;
int y = changeLocal(x);
System.out.println("x=" + x + ",y=" + y);
~~~

**Exact stdout:**

~~~text
x=4,y=99
~~~

Java truyền đối số **theo giá trị**; tham số `n` là bản sao của giá trị `x`, nên gán `n=99` không đổi `x`. `return n;` trả `99` để caller có thể lưu vào `y`.

Với object, giá trị truyền/nhận là **tham chiếu tới object**. Trả một tham chiếu không tự tạo bản sao object; nếu caller và callee cùng tham chiếu object mutable, thay đổi trạng thái object có thể quan sát được ở cả hai nơi. Điều này khác với gán lại biến tham số.

## 8. Chương trình Java 17 hoàn chỉnh

Tạo file **`ReturnValueVoidDemo.java`**:

~~~java
public final class ReturnValueVoidDemo {
    private ReturnValueVoidDemo() {}

    static int larger(int a, int b) {
        if (a >= b) return a;
        return b;
    }

    static String grade(int score) {
        if (score < 0 || score > 100) return "INVALID";
        if (score >= 90) return "A";
        if (score >= 70) return "B";
        return "C";
    }

    static void announce(String message) {
        if (message == null || message.isBlank()) {
            System.out.println("skip empty");
            return;
        }
        System.out.println("announce:" + message);
    }

    static int firstEven(int[] numbers) {
        for (int n : numbers) {
            if (n % 2 == 0) return n;
        }
        return -1;
    }

    static int twice(int n) {
        System.out.println("inside twice");
        return n * 2;
    }

    static int changeLocal(int n) {
        n = 99;
        return n;
    }

    static void finallyDemo() {
        try {
            System.out.println("inside try");
            return;
        } finally {
            System.out.println("inside finally");
        }
    }

    public static void main(String[] args) {
        System.out.println("larger=" + larger(3, 8));
        System.out.println("grade -1=" + grade(-1));
        System.out.println("grade 69=" + grade(69));
        System.out.println("grade 70=" + grade(70));
        System.out.println("grade 90=" + grade(90));
        System.out.println("grade 101=" + grade(101));

        announce(null);
        announce(" ");
        announce("Hi");

        System.out.println("firstEven=" + firstEven(new int[]{3, 7, 10, 12}));
        System.out.println("none=" + firstEven(new int[]{3, 7}));

        System.out.println("before twice");
        System.out.println("twice=" + twice(4));
        System.out.println("after twice");

        int x = 4;
        int y = changeLocal(x);
        System.out.println("x=" + x + ",y=" + y);

        finallyDemo();
        System.out.println("after finally");
    }
}
~~~

**Bash (Linux/macOS), JDK 17+:**

~~~bash
javac --release 17 -encoding UTF-8 ReturnValueVoidDemo.java
java -cp . ReturnValueVoidDemo
~~~

**PowerShell (Windows), JDK 17+:**

~~~powershell
javac --release 17 -encoding UTF-8 .\ReturnValueVoidDemo.java
java -cp . ReturnValueVoidDemo
~~~

### Exact expected stdout

~~~text
larger=8
grade -1=INVALID
grade 69=C
grade 70=B
grade 90=A
grade 101=INVALID
skip empty
skip empty
announce:Hi
firstEven=10
none=-1
before twice
inside twice
twice=8
after twice
x=4,y=99
inside try
inside finally
after finally
~~~

**Đối chiếu:** `larger` trả `int`; `grade` trả sớm theo các ngưỡng điểm; `announce` dùng `return;` để bỏ qua đầu vào rỗng; `firstEven` thoát cả phương thức từ trong vòng; `twice` tính xong trước khi caller in kết quả; `finallyDemo` vẫn chạy `finally` trước khi quay về `main`.

## 9. Lỗi thường gặp và bài tập

| Sai lầm | Vì sao sai? | Cách sửa |
|---|---|---|
| `return 1;` trong phương thức `void` | Không được trả giá trị | Dùng `return;` |
| `return;` trong phương thức `int` | Thiếu giá trị trả về | Dùng `return intExpression;` |
| `return "A";` trong phương thức `boolean` | Sai kiểu | Trả biểu thức boolean |
| Thiếu `return` ở một đường có thể kết thúc bình thường | `missing return statement` | Xử lý đủ đường đi |
| Nghĩ `return` chỉ thoát vòng | `return` thoát **phương thức** | Dùng `break` nếu chỉ muốn ra vòng |
| Đặt lệnh sau `return` vô điều kiện | `unreachable statement` | Đặt trước `return` hoặc sửa luồng |
| Gán kết quả của phương thức `void` | Không có giá trị | Gọi như một statement |
| Bỏ qua `finally` khi phân tích `return` | Thứ tự thực thi bị hiểu sai | Xét khối `finally` |

**Bài A.** Phương thức `static void stop() { return; }` hợp lệ không? **Có.** Nó thoát khỏi `void`, không trả giá trị.

**Bài B.** `static int sign(int n) { if (n > 0) return 1; }` có biên dịch không? **Không**, vì khi `n <= 0`, thân có thể kết thúc mà không trả `int`. Có thể thêm `return 0;` nếu hợp đồng quy định như vậy.

**Bài C.** Gọi `firstEven(new int[]{1, 3, 6, 8})` trả gì? **`6`**, vì `return` ngay tại số chẵn đầu tiên.

**Bài D.** Phương thức `void` không có `return;` có được không? **Có**, nếu chạy hết thân thì nó kết thúc bình thường.

## 10. Checklist và kết luận

- [ ] Phân biệt `return expression;` và `return;`.
- [ ] Biết `void` không trả giá trị và có thể kết thúc tự nhiên.
- [ ] Biết kiểu biểu thức trả về phải phù hợp với kiểu khai báo.
- [ ] Biết phương thức không `void` phải xử lý đủ đường hoàn thành bình thường.
- [ ] Hiểu `return` thoát **phương thức**, khác `break`/`continue`.
- [ ] Nhận diện `unreachable statement` sau `return` vô điều kiện.
- [ ] Hiểu giá trị trả về được caller sử dụng sau khi lời gọi kết thúc.
- [ ] Xét `finally` khi theo dõi đường `return`.
- [ ] Biên dịch và chạy `ReturnValueVoidDemo.java` với Java 17+, đối chiếu stdout.

**Kết luận:** `return expression;` cung cấp **một giá trị phù hợp kiểu trả về** và kết thúc phương thức; `return;` trong `void` **chỉ kết thúc sớm**, không sinh ra giá trị. Cả hai đều trả quyền điều khiển về nơi gọi, không chỉ thoát vòng lặp chứa chúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="methods-006">Truyền tham trị cho kiểu nguyên thủy và tham chiếu</a>

<details>
<summary>Xem chi tiết</summary>

# Truyền tham trị cho kiểu nguyên thủy và tham chiếu trong phương thức Java

## 1. Quy tắc cốt lõi: Java luôn truyền tham trị

Trong Java, khi gọi một phương thức, **giá trị của từng biểu thức đối số** được dùng để khởi tạo **biến tham số riêng của phương thức**. Cơ chế này gọi là *pass-by-value* (truyền tham trị) và áp dụng **cho cả kiểu nguyên thủy lẫn kiểu tham chiếu**.

- **Kiểu nguyên thủy** (`int`, `double`, `boolean`, `char`, ...): tham số nhận bản sao của chính giá trị, chẳng hạn `10` hoặc `true`. Thay đổi tham số không thay đổi biến ở nơi gọi.
- **Kiểu tham chiếu** (`Person`, `String`, `int[]`, ...): tham số nhận bản sao **giá trị tham chiếu** tới đối tượng. Hai biến có thể cùng tham chiếu một đối tượng; thao tác sửa trạng thái đối tượng qua tham số có thể thấy được ở nơi gọi. Tuy nhiên, **gán lại tham số** không làm thay đổi biến tham chiếu của nơi gọi.

Không có cú pháp tham số `ref`, `out` hay “pass-by-reference” trong chữ ký phương thức Java. Cụm từ “truyền tham chiếu” đôi khi được dùng không chính xác để mô tả việc đối số có kiểu tham chiếu. Nói chính xác, Java **truyền bản sao của giá trị tham chiếu**, không truyền chính biến của bên gọi cho phương thức sửa lại.

## 2. Truyền kiểu nguyên thủy: thay đổi tham số chỉ có tác dụng bên trong

Ví dụ dưới đây là đoạn mã minh họa đặt trong một lớp:

```java
static void increase(int value) {
    value += 10; // Chỉ cập nhật tham số cục bộ
}

int x = 5;
increase(x);
// x vẫn bằng 5
```

Khi gọi `increase(x)`, giá trị `5` được sao chép vào tham số `value`. Câu lệnh `value += 10` biến tham số thành `15`, nhưng biến `x` của nơi gọi không thay đổi.

Kể cả khi tham số và biến bên gọi trùng tên, chúng vẫn là hai biến khác nhau thuộc phạm vi khác nhau. Giá trị được truyền vào chỉ được xác định **tại lần gọi**; thay đổi tham số sau đó không đồng bộ ngược về biến ban đầu.

Với các giá trị `byte`, `short`, `char`, `int`, `long`, `float`, `double`, `boolean`, quy tắc là như nhau (có thể có chuyển đổi kiểu thích hợp theo quy tắc gọi phương thức, ví dụ `int` sang `long`). Muốn bên gọi nhận giá trị mới, hãy **trả về rồi gán lại**:

```java
static int increased(int value) {
    return value + 10;
}

int x = 5;
x = increased(x);
// x == 15
```

Phương thức `void` không tự đưa bản cập nhật của tham số trở lại nơi gọi.

## 3. Truyền tham chiếu: đối tượng chung, biến tham số riêng

Xét một phương thức nhận đối tượng có trạng thái thay đổi được:

```java
class Person {
    String name;

    Person(String name) {
        this.name = name;
    }
}

static void rename(Person person) {
    person.name = "Lan"; // Sửa đối tượng chung
}

static void replace(Person person) {
    person = new Person("Hoa"); // Gán lại biến tham số
}
```

Ở nơi gọi:

```java
Person p = new Person("Minh");
rename(p);  // p.name trở thành "Lan"
replace(p); // p vẫn trỏ đến đối tượng có name là "Lan"
```

Khi gọi `rename(p)`, giá trị tham chiếu của `p` được sao chép vào tham số `person`. Hai biến cùng chỉ tới một đối tượng, vì vậy `person.name = "Lan"` làm đổi trường `name` trên đối tượng đó.

Khi gọi `replace(p)`, gán `person = new Person("Hoa")` chỉ đổi biến `person` **bên trong phương thức** sang đối tượng mới. Biến `p` vẫn giữ tham chiếu cũ. Cũng vì thế, `person = null` không khiến biến `p` ở nơi gọi thành `null`.

Có thể hình dung:

```text
Trước khi gán lại tham số:
  p -----------+---> Person("Lan")
  person ------+

Sau person = new Person("Hoa"):
  p ----------------> Person("Lan")
  person -----------> Person("Hoa")
```

Sơ đồ chỉ mô tả quan hệ tham chiếu, không khẳng định vị trí vật lý cụ thể của biến hay đối tượng trong bộ nhớ.

## 4. Mảng làm rõ sửa object khác gán lại tham số

Mảng `int[]` là **đối tượng**, nên tham số nhận bản sao **tham chiếu mảng**, không tự nhận bản sao từng phần tử.

~~~java
static void changeFirst(int[] numbers) { numbers[0] = 99; }
static void rebind(int[] numbers) { numbers = new int[]{7, 8}; }

int[] data = {1, 2};
changeFirst(data);
rebind(data);
System.out.println(java.util.Arrays.toString(data)); // [99, 2].
~~~

`changeFirst` sửa object dùng chung; `rebind` chỉ thay biến tham số. Với mảng hai chiều, thay `matrix[1]` là sửa **phần tử của mảng ngoài**, còn gán `matrix = other` là gán lại tham số. Bài tiếp theo phân tích riêng mảng nhiều chiều, alias, `clone()` và hợp đồng sửa mảng/trả mảng.


## 5. Ví dụ Java 17+ đầy đủ, chạy và đối chiếu đầu ra

Tạo tệp **`PassByValueDemo.java`**:

```java
import java.util.Arrays;

public class PassByValueDemo {
    static class Person {
        String name;

        Person(String name) {
            this.name = name;
        }
    }

    static void changeNumber(int number) {
        number = 99;
        System.out.println("Trong changeNumber: " + number);
    }

    static void changeElement(int[] values) {
        values[0] = 99;
    }

    static void replaceArray(int[] values) {
        values = new int[]{7, 8};
        System.out.println("Trong replaceArray: " + Arrays.toString(values));
    }

    static void rename(Person person) {
        person.name = "Lan";
    }

    static void replacePerson(Person person) {
        person = new Person("Hoa");
        System.out.println("Trong replacePerson: " + person.name);
    }

    static void changeString(String text) {
        text = text.toUpperCase();
        System.out.println("Trong changeString: " + text);
    }

    static int[] doubledCopy(int[] source) {
        int[] copy = source.clone();
        for (int i = 0; i < copy.length; i++) {
            copy[i] *= 2;
        }
        return copy;
    }

    public static void main(String[] args) {
        int number = 10;
        changeNumber(number);
        System.out.println("Sau changeNumber: " + number);

        int[] values = {1, 2};
        changeElement(values);
        System.out.println("Sau changeElement: " + Arrays.toString(values));
        replaceArray(values);
        System.out.println("Sau replaceArray: " + Arrays.toString(values));

        Person person = new Person("Minh");
        rename(person);
        System.out.println("Sau rename: " + person.name);
        replacePerson(person);
        System.out.println("Sau replacePerson: " + person.name);

        String word = "java";
        changeString(word);
        System.out.println("Sau changeString: " + word);

        int[] doubled = doubledCopy(values);
        System.out.println("Mang goc: " + Arrays.toString(values));
        System.out.println("Ban sao gap doi: " + Arrays.toString(doubled));
    }
}
```

Mở terminal tại thư mục chứa tệp. Với **JDK 17 trở lên**, chạy như nhau trên Windows (PowerShell/CMD) hoặc Linux/macOS:

```bash
javac -encoding UTF-8 PassByValueDemo.java
java PassByValueDemo
```

**Kết quả stdout chính xác** (lệnh `javac` thành công không in dòng nào):

```text
Trong changeNumber: 99
Sau changeNumber: 10
Sau changeElement: [99, 2]
Trong replaceArray: [7, 8]
Sau replaceArray: [99, 2]
Sau rename: Lan
Trong replacePerson: Hoa
Sau replacePerson: Lan
Trong changeString: JAVA
Sau changeString: java
Mang goc: [99, 2]
Ban sao gap doi: [198, 4]
```

Các cặp kết quả minh họa ba tình huống khác nhau:

1. **Sửa biến tham số `int`** trong `changeNumber`: giá trị bên ngoài vẫn `10`.
2. **Sửa đối tượng được chia sẻ** trong `changeElement` và `rename`: nội dung mảng và trường của `Person` thực sự thay đổi.
3. **Gán tham số tham chiếu sang đối tượng khác** trong `replaceArray` và `replacePerson`: người gọi tiếp tục dùng đối tượng ban đầu.

Phương thức `doubledCopy` minh họa cách tạo bản sao để tính toán mà không sửa mảng đầu vào.

## 6. Vì sao `String` và `Integer` dễ gây hiểu nhầm?

`String` là **đối tượng bất biến** (*immutable*). Phép `text.toUpperCase()` tạo/trả về một chuỗi tương ứng và không thay đổi đối tượng `String` đã có; phép `text = ...` sau đó chỉ gán lại **tham số cục bộ**. Vì vậy, sau `changeString(word)`, biến `word` của người gọi vẫn giữ giá trị `"java"`.

`Integer` cũng bất biến; thêm vào đó, Java hỗ trợ **autoboxing/unboxing**, khiến cú pháp trông giống đang sửa đối tượng:

```java
static void addOne(Integer value) {
    value++; // Unboxing, tăng int, boxing thành Integer rồi gán lại tham số
}

Integer count = 3;
addOne(count);
// count vẫn bằng 3
```

Đây là mã minh họa bên trong lớp/phương thức. Biểu thức `value++` không sửa đối tượng `Integer` ban đầu. Nếu `value` là `null`, thao tác unboxing này ném `NullPointerException`.

Không nên suy ra rằng “đối số kiểu tham chiếu luôn làm thay đổi biến bên ngoài”: chỉ **phép sửa trạng thái trên cùng đối tượng có thể thay đổi** mới tạo ra ảnh hưởng được quan sát qua tham chiếu khác. Nếu đối tượng bất biến hoặc phương thức chỉ gán lại tham số, đối tượng/biến bên ngoài không đổi.

## 7. Các giới hạn của tham số tham chiếu trong API

`final` cấm **gán lại biến tham số**, không biến object được tham chiếu thành immutable:

~~~java
static void setFirst(final int[] data) {
    data[0] = 5;          // Hợp lệ.
    // data = new int[2]; // Lỗi: final parameter.
}
~~~

Đối số `null` cũng được truyền **theo giá trị**; truy cập `person.name` khi tham số null gây `NullPointerException`. Gán `person = new Person(...)` trong method không thay biến caller vốn đang null.

Muốn trả kết quả mảng mà không sửa nguồn, tạo bản sao rồi **return**; chẳng hạn `int[] copy = input.clone();` với `int[]` tạo mảng mới chứa giá trị primitive độc lập. Với mảng object, `clone()` chỉ **sao chép nông** các tham chiếu phần tử; `int[][].clone()` vẫn chia sẻ các hàng.

Tên API như `sortInPlace` và `sortedCopy` nên nói rõ liệu nó **sửa dữ liệu đầu vào** hay **trả về mảng mới**. Chủ đề này giải thích quy tắc chung cho primitive/reference; bài tham số mảng đi sâu cách sửa phần tử, gán lại hàng của mảng nhiều chiều và sao chép an toàn.


## 8. Lỗi thường gặp và câu hỏi kiểm tra

| Nhận định | Đúng/Sai | Giải thích |
| --- | --- | --- |
| “Java truyền `int` tham trị, nhưng truyền `Person` tham chiếu.” | **Sai** | Cả hai đều truyền tham trị; với `Person`, giá trị được sao chép là tham chiếu. |
| “Gán `person = new Person(...)` sẽ thay biến đối số của bên gọi.” | **Sai** | Chỉ gán lại biến tham số riêng. |
| “`person.name = ...` có thể làm thay đổi đối tượng quan sát ở nơi gọi.” | **Đúng** | Khi cả hai tham chiếu cùng trỏ đến một đối tượng có trạng thái thay đổi được. |
| “Gán `numbers[0] = 99` khác với gán `numbers = new int[]{...}`.” | **Đúng** | Một thao tác thay phần tử của đối tượng mảng chung, thao tác còn lại chỉ thay tham chiếu cục bộ. |
| “Tham số `final int[]` cấm sửa phần tử mảng.” | **Sai** | `final` chỉ cấm gán lại biến tham số. |
| “`String` không đổi sau `text = text.toUpperCase()` trong phương thức vì Java truyền tham trị.” | **Đúng**, với biến bên gọi | `String` bất biến; biến bên gọi vẫn trỏ tới giá trị cũ. |

**Ghi nhớ:** luôn tách ba việc: **(1)** sao chép giá trị của đối số vào tham số, **(2)** sửa trạng thái của đối tượng chung, và **(3)** gán lại chính biến tham số. Chỉ (2) có thể làm dữ liệu được quan sát qua tham chiếu của người gọi thay đổi mà không cần giá trị trả về.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="methods-007">Thay đổi nội dung mảng khác với gán lại tham số mảng</a>

<details>
<summary>Xem chi tiết</summary>

# Thay đổi nội dung mảng khác với gán lại tham số mảng trong Java

> **Java core — language-basics / Phương thức**  
> **Môi trường:** JDK 17+, một file Java tự chứa, không dùng thư viện ngoài.  
> **Mục tiêu:** Hiểu Java **truyền đối số theo giá trị**, kể cả khi đối số là tham chiếu mảng; phân biệt sửa phần tử của mảng, gán lại biến tham số, trả về mảng mới và gán lại biến ở phía gọi.

## 1. Quy tắc cốt lõi: tham số nhận bản sao của tham chiếu

Khi truyền một biến mảng vào phương thức, Java **không sao chép toàn bộ các phần tử** và cũng không cho phương thức nắm quyền gán lại biến của bên gọi. Thay vào đó, giá trị tham chiếu tới mảng được **sao chép** sang biến tham số.

~~~java
static void changeFirst(int[] values) {
    values[0] = 99;
}

int[] numbers = {1, 2, 3};
changeFirst(numbers);
// numbers hiện là [99, 2, 3].
~~~

Tại lúc vào phương thức, `numbers` và `values` là **hai biến khác nhau** đang trỏ tới **cùng một đối tượng mảng**:

~~~text
Trong phương thức:
Bên gọi                       Phương thức
numbers ─────────┐          values ─────────┐
                 ▼                          ▼
               [ 1, 2, 3 ]   (CÙNG MỘT MẢNG)
~~~

`values[0] = 99` sửa một phần tử **trong đối tượng mảng dùng chung**. Vì bên gọi vẫn trỏ tới mảng ấy, sau khi phương thức trở về, `numbers[0]` là `99`.

**Thuật ngữ chính xác:** Java dùng *pass-by-value* (truyền theo giá trị), **không** dùng *pass-by-reference* theo nghĩa phương thức có thể gán lại biến `numbers` của bên gọi. Giá trị được sao chép ở đây tình cờ là **tham chiếu đối tượng**, nên các thao tác qua tham chiếu có thể làm thay đổi đối tượng chung.

## 2. Gán lại tham số chỉ đổi biến cục bộ của phương thức

So sánh với lời gọi sau:

~~~java
static void replaceLocal(int[] values) {
    values = new int[] {7, 8}; // values trỏ tới mảng mới.
    values[0] = 70;            // Chỉ sửa mảng mới này.
}

int[] numbers = {1, 2, 3};
replaceLocal(numbers);
// numbers vẫn là [1, 2, 3].
~~~

Sau phép `values = new int[] {7, 8}`:

~~~text
numbers ────────> [1, 2, 3]    (mảng gốc bên gọi)

values  ────────> [7, 8]       (mảng mới của phương thức)
                          └── sửa thành [70, 8]
~~~

Phép gán thay **giá trị tham chiếu được giữ trong tham số `values`**, không thay đổi tham chiếu lưu trong biến `numbers` bên ngoài. Vì vậy bên gọi không tự chuyển sang mảng mới.

| Câu lệnh trong phương thức | Thay đổi gì? | Bên gọi có quan sát được? |
| --- | --- | --- |
| `values[0] = 99;` | Giá trị một phần tử của mảng đang trỏ tới | Có, nếu cùng tham chiếu mảng |
| `values[0]++;` | Tăng phần tử mảng đang trỏ tới | Có, nếu cùng mảng |
| `values = new int[] {7, 8};` | Tham chiếu **cục bộ** trong `values` | Không đổi biến tham chiếu của bên gọi |
| `values = null;` | Chỉ đặt **tham số** thành `null` | Không tự làm biến mảng bên gọi thành `null` |
| `values = values.clone();` | Tham số trỏ sang mảng mới được clone | Không tự thay tham chiếu của bên gọi |
| `return new int[] {...};` | Trả tham chiếu mảng mới | Bên gọi chỉ đổi biến nếu tự gán kết quả |

Quan trọng: Nếu vừa sửa mảng gốc rồi mới gán lại tham số, **phần sửa trước đó vẫn còn** trong mảng gốc.

## 3. Kết hợp sửa nội dung và gán lại tham số

~~~java
static void mutateThenRebind(int[] data) {
    data[1] = 22;               // Sửa mảng gốc.
    data = new int[] {3, 4};    // Chỉ đổi tham số cục bộ.
    data[0] = 30;               // Sửa mảng mới, không phải mảng gốc.
}

int[] original = {11, 2, 3};
mutateThenRebind(original);
// original == [11, 22, 3] theo nội dung.
~~~

Trình tự rất quan trọng: `data[1] = 22` xảy ra **trước** lúc `data` bị gán sang mảng khác, nên kết quả bên gọi nhìn thấy là `[11, 22, 3]`. Còn `data[0] = 30` diễn ra sau phép gán lại, không ảnh hưởng mảng gốc.

**Không nhầm phép so sánh:** `original == other` trên hai biến mảng kiểm tra chúng có cùng tham chiếu không. Để so sánh nội dung mảng một chiều `int[]`, dùng `java.util.Arrays.equals(original, other)`.

## 4. Muốn bên gọi dùng mảng khác: trả về rồi nhận kết quả

Nếu mục tiêu là thay **mảng mà biến ở phía gọi trỏ tới**, cách rõ ràng là phương thức trả về mảng mong muốn và bên gọi tự gán:

~~~java
static int[] replacement(int[] old) {
    return new int[] {100, 200};
}

int[] numbers = {1, 2, 3};
int[] newer = replacement(numbers); // numbers vẫn giữ mảng cũ.
numbers = newer;                    // Bên gọi chủ động gán lại biến.
~~~

Cũng có thể viết trực tiếp:

~~~java
numbers = replacement(numbers);
~~~

Sau phép gán đó, chính **mã bên gọi** thay giá trị của biến `numbers`. Phương thức `replacement` chỉ trả về một tham chiếu; nó không có khả năng truy cập phép gán tới biến local `numbers` của caller.

Nếu muốn tạo mảng mới **dựa trên dữ liệu cũ** mà không sửa nguồn, dùng `Arrays.copyOf`:

~~~java
static int[] withAppended(int[] source, int value) {
    int[] output = java.util.Arrays.copyOf(source, source.length + 1);
    output[source.length] = value;
    return output;
}
~~~

`withAppended(new int[] {1, 2}, 3)` trả mảng nội dung `[1, 2, 3]`, còn mảng nguồn `[1, 2]` không bị thay đổi. `Arrays.copyOf` có thể ném ngoại lệ nếu `source == null`; cần xác định hợp đồng đầu vào trước khi gọi.

## 5. Alias, `clone()` và từ khóa `final`

Hai biến tham chiếu tới cùng mảng được gọi là **alias**. Sửa qua alias cũng là sửa dữ liệu được quan sát qua biến còn lại:

~~~java
int[] first = {3, 4};
int[] alias = first;
alias[0] = 10;

System.out.println(first[0]);   // 10
System.out.println(alias == first); // true
~~~

Còn `clone()` trên mảng `int[]` tạo **đối tượng mảng mới**:

~~~java
int[] copy = first.clone();
copy[0] = 99;

System.out.println(first[0]);      // 10
System.out.println(copy[0]);       // 99
System.out.println(copy == first); // false
~~~

Đối với `int[]`, clone sao chép các giá trị nguyên thủy. Với mảng `String[]` hoặc `Object[]`, clone chỉ sao chép **tham chiếu phần tử** chứ không tự nhân bản các đối tượng được tham chiếu. Với `int[][]`, clone mảng ngoài vẫn chia sẻ các mảng con.

`final` trên một tham số mảng **cấm gán lại biến tham số**, nhưng không tự cấm thay đổi phần tử:

~~~java
static void increment(final int[] values) {
    values[0]++;                  // Hợp lệ.
    // values = new int[] {5};   // LỖI biên dịch vì values là final.
}
~~~

Mảng Java vẫn là đối tượng mutable, kể cả khi tham chiếu tới nó nằm trong biến `final`.

## 6. Mảng tham chiếu và mảng nhiều chiều tuân theo cùng nguyên lý

Nguyên tắc không chỉ áp dụng cho `int[]`:

~~~java
static void changeLabel(String[] labels) {
    labels[0] = "X";                 // Sửa phần tử của mảng gốc.
}

static void rebindLabels(String[] labels) {
    labels = new String[] {"LOCAL"}; // Chỉ đổi tham số.
}
~~~

Với mảng nhiều chiều, cần phân biệt **thay phần tử của mảng ngoài** với **gán lại biến tham số mảng ngoài**:

~~~java
static void changeRows(int[][] matrix) {
    matrix[0][1] = 9;         // Sửa phần tử trong mảng con hiện tại.
    matrix[1] = new int[] {8}; // Sửa phần tử của mảng NGOÀI: thay hàng 1.
}

static void replaceMatrixLocally(int[][] matrix) {
    matrix = new int[][] {{100}}; // Chỉ gán lại tham số cục bộ.
}
~~~

Cả `matrix[0][1] = 9` và `matrix[1] = new int[] {8}` đều thay đổi đối tượng có thể được bên gọi quan sát. Nhưng `matrix = new int[][] {{100}}` **không** thay mảng ngoài mà biến của bên gọi đang giữ.

Tương tự, nếu phần tử của `Person[]` là tham chiếu tới đối tượng mutable, cần phân biệt thay `people[0] = new Person(...)` (thay phần tử mảng) với `people[0].setName(...)` (thay trạng thái của đối tượng mà phần tử trỏ tới). Đó là hai thao tác khác nhau, dù cả hai có thể được bên gọi quan sát qua tham chiếu phù hợp.

## 7. `null`, chỉ số và hợp đồng của phương thức

Ví dụ đơn giản thường giả định tham số có mảng khác `null` và đủ phần tử:

~~~java
static void increaseFirst(int[] numbers) {
    numbers[0] += 10;
}
~~~

- Nếu `numbers == null`, phép truy cập `numbers[0]` gây `NullPointerException`.
- Nếu `numbers.length == 0`, chỉ số 0 không tồn tại và gây `ArrayIndexOutOfBoundsException`.
- Nếu người dùng chỉ muốn gán lại tham số `numbers = new int[3]`, thao tác này **không** tạo/thay mảng bên caller.

Có thể chọn một hợp đồng an toàn và rõ ràng:

~~~java
static boolean tryIncreaseFirst(int[] numbers) {
    if (numbers == null || numbers.length == 0) {
        return false;
    }
    numbers[0] += 10;
    return true;
}
~~~

Nếu cần tạo mảng mới cho bên gọi trong trường hợp null, **trả mảng mới** rồi caller gán kết quả. Không thể biến tham số `null` thành mảng mới ở caller bằng một phép `numbers = new int[3]` nằm trong phương thức `void`.

Trong API thực tế, hãy ghi rõ phương thức có **sửa mảng đầu vào** hay trả về bản sao không sửa nguồn. Chọn tên như `sortInPlace`, `fill`, `copyAndAppend` hoặc tài liệu tương ứng để người gọi không nhầm tác dụng.

## 8. Chương trình Java 17+ độc lập, chạy và xem kết quả

Tạo file **ArrayParameterSemanticsDemo.java**:

~~~java
import java.util.Arrays;

public final class ArrayParameterSemanticsDemo {
    private static void increaseFirst(int[] values) {
        values[0] += 10;
    }

    private static void rebindOnly(int[] values) {
        values = new int[] {7, 8};
        values[0] = 70;
        System.out.println("insideRebind=" + Arrays.toString(values));
    }

    private static void mutateThenRebind(int[] values) {
        values[1] = 22;
        values = new int[] {3, 4};
        values[0] = 30;
        System.out.println("insideCombined=" + Arrays.toString(values));
    }

    private static void setParameterNull(int[] values) {
        values = null;
        System.out.println("insideNull=" + (values == null));
    }

    private static int[] replacement(int[] old) {
        return new int[] {100, 200};
    }

    private static void finalMutation(final int[] values) {
        values[0]++;
    }

    private static void changeLabel(String[] values) {
        values[0] = "X";
    }

    private static void rebindLabels(String[] values) {
        values = new String[] {"LOCAL"};
        System.out.println("insideLabelRebind=" + Arrays.toString(values));
    }

    private static void changeRows(int[][] values) {
        values[0][1] = 9;
        values[1] = new int[] {8};
    }

    private static void rebindMatrix(int[][] values) {
        values = new int[][] {{100}};
        System.out.println("insideMatrixRebind=" + Arrays.deepToString(values));
    }

    public static void main(String[] args) {
        int[] original = {1, 2, 3};
        System.out.println("start=" + Arrays.toString(original));

        increaseFirst(original);
        System.out.println("afterElement=" + Arrays.toString(original));

        rebindOnly(original);
        System.out.println("afterRebind=" + Arrays.toString(original));

        mutateThenRebind(original);
        System.out.println("afterCombined=" + Arrays.toString(original));

        int[] alias = original;
        alias[2] = 33;
        System.out.println("aliasSame=" + (alias == original));
        System.out.println("afterAlias=" + Arrays.toString(original));

        setParameterNull(original);
        System.out.println("afterNullOriginal=" + (original != null));

        int[] returned = replacement(original);
        System.out.println("returned=" + Arrays.toString(returned));
        System.out.println("callerBeforeAssign=" + Arrays.toString(original));
        original = returned;
        System.out.println("callerAfterAssign=" + Arrays.toString(original));
        System.out.println("oldAlias=" + Arrays.toString(alias));

        int[] copied = original.clone();
        copied[0] = 500;
        System.out.println("copySame=" + (copied == original));
        System.out.println("copyChanged=" + Arrays.toString(copied));
        System.out.println("sourceAfterCopy=" + Arrays.toString(original));

        finalMutation(original);
        System.out.println("afterFinalMutation=" + Arrays.toString(original));

        String[] labels = {"A", "B"};
        changeLabel(labels);
        System.out.println("afterLabelMutation=" + Arrays.toString(labels));
        rebindLabels(labels);
        System.out.println("afterLabelRebind=" + Arrays.toString(labels));

        int[][] matrix = {{1, 2}, {3, 4}};
        changeRows(matrix);
        System.out.println("afterNestedMutation=" + Arrays.deepToString(matrix));
        rebindMatrix(matrix);
        System.out.println("afterMatrixRebind=" + Arrays.deepToString(matrix));

        try {
            increaseFirst(null);
        } catch (NullPointerException ex) {
            System.out.println("nullMutation=NullPointerException");
        }
    }
}
~~~

Từ thư mục chứa file, chạy trong PowerShell, Command Prompt hoặc Bash:

~~~bash
javac -encoding UTF-8 ArrayParameterSemanticsDemo.java
java ArrayParameterSemanticsDemo
~~~

**Expected stdout chính xác:**

~~~text
start=[1, 2, 3]
afterElement=[11, 2, 3]
insideRebind=[70, 8]
afterRebind=[11, 2, 3]
insideCombined=[30, 4]
afterCombined=[11, 22, 3]
aliasSame=true
afterAlias=[11, 22, 33]
insideNull=true
afterNullOriginal=true
returned=[100, 200]
callerBeforeAssign=[11, 22, 33]
callerAfterAssign=[100, 200]
oldAlias=[11, 22, 33]
copySame=false
copyChanged=[500, 200]
sourceAfterCopy=[100, 200]
afterFinalMutation=[101, 200]
afterLabelMutation=[X, B]
insideLabelRebind=[LOCAL]
afterLabelRebind=[X, B]
afterNestedMutation=[[1, 9], [8]]
insideMatrixRebind=[[100]]
afterMatrixRebind=[[1, 9], [8]]
nullMutation=NullPointerException
~~~

Các dòng `afterElement`, `afterCombined` và `afterNestedMutation` chứng minh sửa **đối tượng mảng** vẫn được thấy sau lời gọi. Các dòng `afterRebind`, `afterLabelRebind` và `afterMatrixRebind` chứng minh thay **tham chiếu của biến tham số** không thay biến của caller. `callerAfterAssign` là trường hợp duy nhất trong ví dụ bên gọi chủ động gán mảng được trả về.

## 9. Lỗi thường gặp và bài tập

| Hiểu nhầm | Cách sửa cách nghĩ |
| --- | --- |
| “Java truyền mảng theo tham chiếu” có nghĩa hàm thay được biến caller | Java truyền **bản sao giá trị tham chiếu**; gán lại tham số không gán lại caller |
| Hàm `void` viết `values = new int[3]` sẽ thay mảng bên gọi | Không; hãy trả mảng hoặc dùng kiểu dữ liệu bao trạng thái nếu thiết kế cần |
| `values[0] = ...` chỉ đổi biến tham số | Không; đó là sửa phần tử của **đối tượng mảng** |
| Thay `matrix[0] = new int[] {...}` cũng không thấy ngoài caller | Sai: đây là gán **phần tử của mảng ngoài**, thấy được qua alias |
| `clone()` luôn sao chép sâu mọi đối tượng | Không; với mảng tham chiếu, phần tử được sao chép dưới dạng tham chiếu |
| Gán `values = null` làm caller thành `null` | Không; chỉ tham số bên trong phương thức đổi |
| Tham số `final int[]` biến mảng thành bất biến | Không; vẫn có thể đổi `values[i]` |
| `Arrays.equals(a,b)` kiểm tra cùng đối tượng | Không; so sánh **nội dung** một chiều, còn `a == b` so sánh cùng tham chiếu |
| Gọi `array[0]` khi mảng `null` hoặc rỗng | Cần kiểm tra null và độ dài trước khi truy cập |

**Bài 1.** Viết `static void doubleFirst(int[] values)` nhân đôi phần tử đầu. Dự đoán giá trị ở bên gọi sau khi chạy với `int[] numbers = {3, 5}`.

**Bài 2.** Viết `static void reset(int[] values) { values = new int[values.length]; }`. Vì sao mảng cũ vẫn giữ giá trị? Sửa bằng `Arrays.fill(values, 0)` nếu muốn thay đổi mảng gốc.

**Bài 3.** Viết `static int[] resized(int[] source)` tạo mảng lớn hơn một phần tử, rồi dùng `source = resized(source)` trong **mã bên gọi**. So sánh trước và sau phép gán.

**Bài 4.** Truyền `String[]` vào phương thức đổi `values[0]`, sau đó thử gán `values = new String[] {...}`. So sánh kết quả trên mảng gốc.

**Bài 5.** Với `int[][]`, so sánh `matrix[0] = new int[] {9}` với `matrix = new int[][] {{9}}` trong phương thức. Lệnh nào thay được hàng của mảng caller?

**Bài 6.** Thử `final int[] data` trong tham số: biên dịch trường hợp thay `data[0]` và trường hợp `data = new int[1]`; giải thích vì sao chỉ một trường hợp hợp lệ.

### Ghi nhớ

**Java luôn truyền đối số theo giá trị.** Khi đối số là mảng, giá trị được sao chép là **tham chiếu tới mảng**: sửa `values[i]` thay đổi mảng dùng chung, còn `values = anotherArray` chỉ thay tham chiếu **cục bộ**. Muốn caller trỏ tới mảng mới, hãy **trả tham chiếu mới và gán ở phía gọi**; muốn giữ cùng mảng thì sửa các phần tử có chủ đích và nêu rõ hợp đồng phương thức.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="methods-008">Nạp chồng phương thức và những thành phần tạo chữ ký</a>

<details>
<summary>Xem chi tiết</summary>

# Nạp chồng phương thức và những thành phần tạo chữ ký trong Java

> **Java core — language-basics / Phương thức**  
> **Môi trường:** JDK 17+, không cần preview hoặc thư viện ngoài.  
> **Mục tiêu:** Nhận diện những khai báo **overload hợp lệ**, biết thành phần nào thực sự phân biệt **chữ ký phương thức**, hiểu cách Java chọn overload tại thời điểm biên dịch và tránh trùng chữ ký hoặc lời gọi mơ hồ.

## 1. Nạp chồng phương thức là gì?

*Nạp chồng phương thức* (*method overloading*) cho phép một lớp có nhiều phương thức **cùng tên** nhưng có **danh sách kiểu tham số khác nhau**, để biểu diễn những thao tác cùng ý tưởng:

~~~java
static String render(int number) {
    return "int:" + number;
}

static String render(long number) {
    return "long:" + number;
}

static String render(String text) {
    return "String:" + text;
}

static String render(int left, int right) {
    return "pair:" + (left + right);
}
~~~

Những lời gọi sau được phân giải đến các khai báo tương ứng:

~~~java
render(7);        // "int:7"
render(7L);       // "long:7"
render("7");      // "String:7"
render(3, 4);     // "pair:7"
~~~

Tất cả được gọi bằng tên `render`, nhưng trình biên dịch chọn phương thức dựa trên **số đối số, kiểu của biểu thức đối số và quy tắc chọn overload của Java**. Việc sử dụng cùng tên không có nghĩa các phương thức có thể khai báo tùy ý.

Overloading là **chọn giữa các khai báo có chữ ký khác nhau**; không phải *overriding* (ghi đè), nơi lớp con định nghĩa lại phương thức được kế thừa theo các quy tắc riêng.

## 2. Thành phần nào tạo nên chữ ký (*method signature*)?

Trong trường hợp phương thức không generic ở mức nhập môn, hãy xác định chữ ký bằng **tên phương thức + danh sách kiểu tham số theo đúng thứ tự**.

| Thay đổi trong khai báo | Tạo overload khác? | Ví dụ |
| --- | --- | --- |
| Tên giống nhau, **số tham số khác** | Có | `print(int)` và `print(int, int)` |
| Tên giống nhau, **kiểu tham số khác** | Có | `print(int)` và `print(long)` |
| Tên giống nhau, **thứ tự kiểu tham số khác** | Có | `show(int, String)` và `show(String, int)` |
| Chỉ đổi **tên biến tham số** | Không | `calc(int x)` và `calc(int y)` |
| Chỉ đổi **kiểu trả về** | Không | `int calc(int)` và `long calc(int)` |
| Chỉ đổi `public`/`private`, `static` hay `final` | Không | `static calc(int)` và instance `calc(int)` |
| Chỉ đổi khai báo `throws` | Không | `work(int)` và `work(int) throws IOException` |

Hai phương thức cùng lớp không thể tồn tại chỉ nhờ đổi những thành phần **không phân biệt chữ ký**. Ngay cả `static` và instance cũng **không** được khai báo cùng tên, cùng danh sách tham số trong một lớp.

Ví dụ hợp lệ vì **thứ tự tham số khác**:

~~~java
static String combine(int count, String label) {
    return count + "/" + label;
}

static String combine(String label, int count) {
    return label + "/" + count;
}
~~~

Tham số kiểu `int, String` và `String, int` tạo hai chữ ký khác nhau dù cùng có hai tham số. Chỉ hoán đổi **tên** `count`/`label` nhưng giữ nguyên kiểu theo vị trí thì **không** tạo overload.

**Lưu ý phạm vi đặc tả:** Với phương thức *generic*, định nghĩa chữ ký theo Java Language Specification còn xét các tham số kiểu và quy tắc tương đương của kiểu; việc kiểm tra trùng tên/kiểu sau *type erasure* cũng quan trọng. Quy tắc tên + các kiểu tham số ở trên là mô hình đúng cho những ví dụ không generic trong bài.

## 3. Những khai báo không hợp lệ do trùng chữ ký

Các cặp sau là **ví dụ lỗi biên dịch**; không đặt chung vào chương trình runnable:

~~~java
// LỖI 1: chỉ đổi tên tham số.
// static int size(int count) { return count; }
// static int size(int amount) { return amount; }

// LỖI 2: chỉ đổi kiểu trả về.
// static int value(int x) { return x; }
// static String value(int x) { return "" + x; }

// LỖI 3: chỉ đổi static hoặc phạm vi truy cập.
// static int code(int x) { return x; }
// int code(int x) { return x; }

// LỖI 4: chỉ khác throws.
// static int load(int x) { return x; }
// static int load(int x) throws java.io.IOException { return x; }
~~~

Kiểu trả về khác nhau **được phép** nếu danh sách tham số đã khác:

~~~java
static int parseValue(int value) {
    return value;
}

static String parseValue(String value) {
    return value.trim();
}
~~~

Trong hai phương thức trên, sự khác biệt `int` so với `String` ở **tham số** mới là lý do overload hợp lệ; khác kiểu trả về chỉ là lựa chọn của từng phương thức.

**Mảng và varargs:** `int... values` được xem như tham số mảng `int[]` trong chữ ký; không thể khai báo đồng thời `sum(int[])` và `sum(int...)` cùng tên trong cùng lớp:

~~~java
// Chỉ chọn MỘT trong hai khai báo sau:
// static int sum(int[] values) { ... }
// static int sum(int... values) { ... } // Trùng chữ ký với bản trên.
~~~

**Generics và erasure:** `process(List<String>)` không thể overload với `process(List<Integer>)` trong cùng lớp chỉ nhờ đối số kiểu `String`/`Integer`. Cả hai có cùng *erasure* của tham số là `List` và gây *name clash*:

~~~java
// KHÔNG BIÊN DỊCH nếu có cả hai trong cùng lớp:
// static void process(java.util.List<String> a) {}
// static void process(java.util.List<Integer> b) {}
~~~

## 4. Java chọn overload tại thời điểm biên dịch như thế nào?

Trình biên dịch xét các phương thức có tên phù hợp, kiểm tra khả năng áp dụng cho kiểu đối số và chọn phương thức cụ thể nhất theo quy tắc của Java. Có thể ghi nhớ thứ tự xem xét các pha quan trọng:

1. **Lời gọi số đối số cố định, không cần boxing/unboxing** (có thể dùng mở rộng primitive/reference).
2. **Lời gọi cố định cho phép boxing/unboxing** khi pha trước không có phương thức áp dụng.
3. **Lời gọi varargs** khi những pha trước không chọn được phương thức phù hợp.

Ví dụ với mở rộng primitive `int → long` và boxing `int → Integer`:

~~~java
static String select(long value) {
    return "long:" + value;
}

static String select(Integer value) {
    return "Integer:" + value;
}
~~~

| Lời gọi | Chọn overload | Vì sao |
| --- | --- | --- |
| `select(5)` | `select(long)` | Mở rộng `int` thành `long` được xét trước boxing |
| `select(5L)` | `select(long)` | Khớp `long` |
| `select(Integer.valueOf(5))` | `select(Integer)` | Khớp tham chiếu `Integer` |
| `select((Integer) null)` | `select(Integer)` | Kiểu biểu thức là `Integer`; không unbox null |

**Đừng đoán theo “kiểu runtime của đối tượng”:** Lựa chọn overload thông thường dựa trên **kiểu tĩnh của biểu thức ở nơi gọi**. Nếu có `Object value = "Hi";`, lời gọi `label(value)` chọn overload `label(Object)` chứ không tự chuyển sang `label(String)` vì đối tượng thực sự là chuỗi.

Với các overload cùng áp dụng, Java còn dùng quy tắc **most specific**. Nếu không chọn được một phương thức cụ thể nhất, lời gọi bị lỗi **ambiguous** thay vì chạy thử ngẫu nhiên.

## 5. `null`, ép kiểu và lời gọi mơ hồ

Giả sử có hai phương thức:

~~~java
static String label(Object value) {
    return "Object:" + value;
}

static String label(String value) {
    return "String:" + value;
}
~~~

Vì `String` là subtype của `Object`, các kết quả là:

~~~java
label("Hi");              // "String:Hi"
label((Object) "Hi");     // "Object:Hi"
label(null);              // "String:null" (String cụ thể hơn Object).
~~~

Dùng **cast** để làm rõ kiểu biểu thức ở nơi gọi khi cần. Nhưng cast không nên thay thế thiết kế API rõ ràng.

Nếu khai báo một cặp overload có các kiểu **không liên quan theo quan hệ subtype**:

~~~java
// Giả sử đã khai báo:
// static void choose(String text) {}
// static void choose(Integer number) {}

// choose(null);             // LỖI: ambiguous.
// choose((String) null);    // HỢP LỆ: chọn String.
// choose((Integer) null);   // HỢP LỆ: chọn Integer.
~~~

Java không thể chọn một cách duy nhất cho `choose(null)` giữa `String` và `Integer`. Đó là lỗi **ngay khi biên dịch**, không phải `NullPointerException` lúc chạy.

## 6. Varargs và thứ tự pha phân giải overload

Trong chữ ký, `int... values` tương ứng tham số mảng `int[]`: không được khai báo cả `f(int...)` và `f(int[])` cùng tên trong một lớp.

~~~java
static String choose(int value) { return "single:" + value; }
static String choose(int... values) {
    return "count=" + values.length;
}

System.out.println(choose(4));    // single:4.
System.out.println(choose(4, 5)); // count=2.
System.out.println(choose());     // count=0.
~~~

Lời gọi một `int` chọn overload **cố định** khi có phương thức áp dụng ở pha fixed-arity, trước khi cần tới varargs. Nhiều overload varargs với kiểu phần tử không liên quan có thể khiến `f()` hoặc `f(null)` **mơ hồ tại biên dịch**.

Bài này chỉ cần biết varargs **không tạo chữ ký mới** và ảnh hưởng tới lựa chọn overload. Bài varargs/mảng tập trung vào mảng được truyền, `null`, kiểu phần tử là mảng và rủi ro generic varargs.


## 7. Chương trình Java 17+ chạy độc lập

Tạo file **MethodOverloadDemo.java**:

~~~java
public final class MethodOverloadDemo {
    private static String render(int value) {
        return "int:" + value;
    }

    private static String render(long value) {
        return "long:" + value;
    }

    private static String render(String value) {
        return "String:" + value;
    }

    private static String render(int left, int right) {
        return "pair:" + (left + right);
    }

    private static String combine(int count, String label) {
        return count + "/" + label;
    }

    private static String combine(String label, int count) {
        return label + "/" + count;
    }

    private static String select(long value) {
        return "long:" + value;
    }

    private static String select(Integer value) {
        return "Integer:" + value;
    }

    private static String label(Object value) {
        return "Object:" + value;
    }

    private static String label(String value) {
        return "String:" + value;
    }

    private static String aggregate(int... values) {
        int sum = 0;
        for (int value : values) {
            sum += value;
        }
        return "count=" + values.length + ",sum=" + sum;
    }

    private static String aggregate(int value) {
        return "single:" + value;
    }

    // Hợp lệ: overload main, nhưng launcher chọn main(String[]).
    public static String main(int value) {
        return "main(int):" + value;
    }

    public static void main(String[] args) {
        System.out.println("int=" + render(7));
        System.out.println("long=" + render(7L));
        System.out.println("string=" + render("7"));
        System.out.println("pair=" + render(3, 4));

        System.out.println("orderedA=" + combine(2, "X"));
        System.out.println("orderedB=" + combine("X", 2));

        System.out.println("widen=" + select(5));
        System.out.println("boxed=" + select(Integer.valueOf(5)));
        System.out.println("castNull=" + select((Integer) null));

        Object upcast = "Hi";
        System.out.println("referenceStaticType=" + label(upcast));
        System.out.println("referenceString=" + label("Hi"));
        System.out.println("referenceNull=" + label(null));

        System.out.println("varargsEmpty=" + aggregate());
        System.out.println("varargsMany=" + aggregate(2, 3, 4));
        System.out.println("varargsArray=" + aggregate(new int[] {8, 9}));
        System.out.println("fixedOne=" + aggregate(4));

        System.out.println("otherMain=" + main(3));
    }
}
~~~

Biên dịch và chạy tại thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 MethodOverloadDemo.java
java MethodOverloadDemo
~~~

**Expected stdout chính xác:**

~~~text
int=int:7
long=long:7
string=String:7
pair=pair:7
orderedA=2/X
orderedB=X/2
widen=long:5
boxed=Integer:5
castNull=Integer:null
referenceStaticType=Object:Hi
referenceString=String:Hi
referenceNull=String:null
varargsEmpty=count=0,sum=0
varargsMany=count=3,sum=9
varargsArray=count=2,sum=17
fixedOne=single:4
otherMain=main(int):3
~~~

`widen=long:5` minh họa mở rộng primitive ưu tiên hơn boxing; `referenceStaticType` cho thấy overload được chọn dựa trên kiểu của biểu thức. `fixedOne` cho thấy bản cố định có thể được ưu tiên hơn varargs. Launcher vẫn chạy `main(String[])` dù lớp có thêm `main(int)`.

## 8. Checklist và bài tập

| Trước khi khai báo overload | Cần kiểm tra |
| --- | --- |
| Tên có trùng nhau không? | Muốn overload thì dùng cùng tên |
| Kiểu/số lượng/thứ tự tham số có khác thật không? | Đây là yếu tố quyết định ở mức nhập môn |
| Chỉ đổi return type hoặc tên tham số? | Không tạo overload hợp lệ |
| Chỉ đổi `static`, access modifier, `throws`? | Không phân biệt chữ ký |
| `int...` so với `int[]`? | Trùng chữ ký, không khai báo cả hai |
| Generic `List<String>` và `List<Integer>`? | Đụng name clash vì erasure |
| Truyền `null` cho hai kiểu tham số không liên quan? | Có thể ambiguous; cân nhắc cast hoặc API rõ hơn |
| Quá nhiều overload nhận kiểu gần nhau? | Thử lời gọi với literal, cast, boxing và varargs |

**Bài 1.** Viết `area(int side)` và `area(int width, int height)` để tính diện tích hình vuông/hình chữ nhật. Nêu hai chữ ký khác nhau ở đâu.

**Bài 2.** Viết `describe(int)`, `describe(long)` và `describe(String)`. Dự đoán lựa chọn khi truyền `4`, `4L` và `"4"`.

**Bài 3.** Giải thích vì sao `void test(int x)` và `int test(int y)` không thể cùng khai báo dù kiểu trả về khác nhau.

**Bài 4.** Viết hai overload nhận `(int, String)` và `(String, int)`; thử truyền đúng và sai thứ tự để thấy kiểm tra tại biên dịch.

**Bài 5.** Tạo `print(Object)` và `print(String)`. Dự đoán lời gọi `print(null)`, `print((Object) "x")` và `print("x")`. Sau đó phân tích lỗi mơ hồ nếu thay `Object` thành `Integer`.

**Bài 6.** Viết `total(int... values)` và gọi với 0, 1, nhiều phần tử hoặc một `int[]`; giải thích vì sao không thể khai báo thêm `total(int[])` với cùng tên.

### Ghi nhớ

**Overloading phân biệt phương thức theo tên và danh sách kiểu tham số (số lượng, kiểu, thứ tự)**; không thể chỉ thay return type, tên tham số, `static` hay `throws`. Java chọn overload dựa vào **kiểu đối số tại thời điểm biên dịch**, theo quy tắc áp dụng và mức độ cụ thể. Chú ý `null` mơ hồ, boxing/varargs và trùng chữ ký sau generic erasure.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="methods-009">Varargs và mối quan hệ với mảng</a>

<details>
<summary>Xem chi tiết</summary>

# Varargs và mối quan hệ với mảng — Java 17+

## 1. Varargs là gì?

**Varargs** (*variable-length arguments*) cho phép một phương thức nhận **số lượng đối số thay đổi**. Ký hiệu là ba dấu chấm `...` sau kiểu phần tử:

~~~java
static int sum(int... numbers) {
    int total = 0;
    for (int number : numbers) {
        total += number;
    }
    return total;
}
~~~

Có thể gọi cùng phương thức bằng các cách sau:

~~~java
System.out.println(sum());                  // Không truyền phần tử nào.
System.out.println(sum(7));                 // Một phần tử.
System.out.println(sum(1, 2, 3));           // Ba phần tử.
System.out.println(sum(new int[]{4, 5}));   // Truyền mảng int[] có sẵn.
~~~

**Exact expected stdout:**

~~~text
0
7
6
9
~~~

**Điểm cốt lõi:** trong thân phương thức, `numbers` có **kiểu `int[]`**, nên dùng được `numbers.length`, `numbers[i]` và enhanced `for` như với một mảng bình thường. Varargs chủ yếu là cú pháp tiện lợi cho **người gọi**, không phải một cấu trúc lưu trữ dữ liệu mới.

## 2. So sánh `int...` và `int[]`

| Định nghĩa | `sum()` | `sum(1, 2)` | `sum(new int[]{1, 2})` |
|---|---|---|---|
| `static int sum(int... a)` | Hợp lệ | Hợp lệ | Hợp lệ |
| `static int sum(int[] a)` | Không hợp lệ | Không hợp lệ | Hợp lệ |

Bên trong phương thức, hai khai báo đều cho tham số kiểu `int[]`. Tuy nhiên, chỉ dạng `int...` cho phép trình biên dịch tạo mảng **từ danh sách nhiều đối số** ở vị trí gọi.

~~~java
static void inspect(int... data) {
    System.out.println("length=" + data.length);
}
~~~

~~~java
inspect();               // length=0
inspect(10, 20);         // length=2
inspect(new int[]{30});  // length=1
~~~

Không được khai báo hai phương thức chỉ khác `int[]` và `int...` cùng tên trong cùng lớp: về **chữ ký sau biên dịch**, chúng là cùng kiểu tham số mảng.

~~~java
// Không thể cùng tồn tại:
// static void f(int[] a) { }
// static void f(int... a) { } // Trùng chữ ký f(int[]).
~~~

## 3. Varargs phải là tham số cuối cùng

Varargs có thể đứng sau những tham số cố định:

~~~java
static void log(String prefix, int... values) {
    System.out.println(prefix + ":" + values.length);
}
~~~

~~~java
log("empty");       // empty:0
log("count", 1, 2); // count:2
~~~

Các quy tắc cú pháp:

- Mỗi khai báo phương thức có **tối đa một** tham số varargs.
- Tham số varargs phải là **tham số cuối cùng**.
- `static void bad(int... a, String tag)` không hợp lệ vì sau varargs còn tham số khác.
- `static void bad(int... a, String... b)` cũng không hợp lệ.

Số lượng tham số **cố định** trước varargs vẫn phải được truyền đúng: `log()` không hợp lệ vì thiếu `prefix`, còn `log("ok")` hợp lệ và tạo dãy số rỗng.

## 4. Mảng tạm và mảng có sẵn: khác biệt về tham chiếu

Với lời gọi `sum(1, 2, 3)`, Java có thể coi ba đối số đó như nội dung của **một mảng `int[]` mới** để truyền vào tham số varargs. Caller không có biến nào trỏ tới mảng tạm ấy.

Với lời gọi `sum(existing)` khi `existing` là `int[]`, Java truyền **tham chiếu mảng có sẵn**; nó không tự sao chép mảng.

~~~java
static void changeFirst(int... data) {
    if (data.length > 0) {
        data[0] = 99;
    }
}

int[] original = {2, 3, 4};
changeFirst(original);
System.out.println(java.util.Arrays.toString(original));
changeFirst(8, 9);
System.out.println(java.util.Arrays.toString(original));
~~~

**Exact expected stdout:**

~~~text
[99, 3, 4]
[99, 3, 4]
~~~

Lời gọi đầu sửa **chính phần tử mảng của caller**. Lời gọi thứ hai sửa mảng được tạo cho danh sách đối số `8, 9`, không làm `original` thay đổi. Java truyền đối số **theo giá trị**, nhưng với mảng giá trị được truyền là **tham chiếu tới object mảng**.

Nếu không muốn phương thức làm đổi mảng được truyền vào, hãy không sửa phần tử hoặc chủ động sao chép bằng `Arrays.copyOf`.

## 5. `sum()` tạo mảng rỗng; truyền null thì khác

Không truyền đối số varargs:

~~~java
sum(); // numbers là int[] không null, độ dài 0.
~~~

Khác với cố ý truyền tham chiếu mảng `null`:

~~~java
sum((int[]) null); // Truyền null làm đối số mảng.
~~~

Nếu thân phương thức duyệt `numbers` hay truy cập `numbers.length` ngay, trường hợp thứ hai có thể gây `NullPointerException`. Một API có thể chọn từ chối null rõ ràng:

~~~java
static int checkedSum(int... numbers) {
    if (numbers == null) {
        throw new IllegalArgumentException("null array");
    }
    int result = 0;
    for (int n : numbers) result += n;
    return result;
}
~~~

| Lời gọi | Trạng thái của `numbers` |
|---|---|
| `checkedSum()` | Mảng rỗng, `length == 0` |
| `checkedSum(1, 2)` | Mảng chứa `1, 2` |
| `checkedSum(new int[0])` | Mảng rỗng đã có |
| `checkedSum((int[]) null)` | Chính là `null`; ví dụ ném `IllegalArgumentException` |

**Lưu ý:** `null` có thể làm lời gọi varargs kiểu tham chiếu trở nên khó phân giải hoặc xuất hiện cảnh báo; dùng ép kiểu mảng rõ ràng khi chủ đích là truyền mảng null.

## 6. Varargs với mảng nhiều chiều

Kiểu phần tử của varargs có thể là **một mảng**:

~~~java
static void showRows(int[]... rows) {
    System.out.println("rows=" + rows.length);
    for (int[] row : rows) {
        System.out.println(java.util.Arrays.toString(row));
    }
}
~~~

Hai cách gọi hợp lệ:

~~~java
showRows(new int[]{1, 2}, new int[]{3});
int[][] matrix = {{4}, {5, 6}};
showRows(matrix);
~~~

**Exact expected stdout:**

~~~text
rows=2
[1, 2]
[3]
rows=2
[4]
[5, 6]
~~~

Ở đây `int[]... rows` có kiểu thực tế **`int[][]`** bên trong phương thức. Mỗi phần tử `row` là `int[]`. Nếu hàng có thể null, cần kiểm tra riêng trước khi truy cập `row.length`.

## 7. Nạp chồng phương thức với varargs

~~~java
static String choose(int value) {
    return "single";
}

static String choose(int... values) {
    return "varargs count=" + values.length;
}
~~~

~~~java
System.out.println(choose(4));
System.out.println(choose(4, 5));
System.out.println(choose());
~~~

**Exact expected stdout:**

~~~text
single
varargs count=2
varargs count=0
~~~

Với một đối số `int`, overload cố định `choose(int)` được chọn trước dạng varargs có thể áp dụng. Với không đối số hoặc hai đối số, chỉ overload varargs trong cặp trên áp dụng.

**Cẩn thận:** nhiều overload varargs gần giống nhau có thể tạo lời gọi **mơ hồ** (*ambiguous*). Ví dụ nếu có cả `f(String...)` và `f(Integer...)`, lời gọi `f()` không xác định được overload nào. Tránh thiết kế overload varargs khó đoán chỉ để rút ngắn cách gọi.

## 8. Lưu ý với varargs kiểu tham chiếu hoặc generic

Với `String... words`, tham số trong thân là `String[]`; mỗi phần tử có thể là null nên code dùng `words[i].length()` cần kiểm tra null ở phần tử.

Với varargs chứa kiểu generic, ví dụ `List<String>... groups`, Java có thể phát cảnh báo về **heap pollution** vì mảng và generic có những quy tắc kiểu khác nhau. Không bỏ qua cảnh báo một cách máy móc hoặc tự thêm `@SafeVarargs` khi chưa chắc phương thức an toàn; với Java cơ bản nên ưu tiên kiểu varargs đơn giản như `int...` và `String...`.

Nếu đối số nhiều và dữ liệu đã nằm sẵn trong mảng, truyền mảng trực tiếp vừa rõ ràng vừa tránh tạo thêm mảng từ danh sách phần tử tại lời gọi.

## 9. Chương trình Java 17+ chạy bằng CLI

Tạo file đúng tên **`VarargsArrayDemo.java`**:

~~~java
import java.util.Arrays;

public final class VarargsArrayDemo {
    private VarargsArrayDemo() {}

    static int sum(int... values) {
        if (values == null) {
            throw new IllegalArgumentException("null array");
        }
        int total = 0;
        for (int value : values) total += value;
        return total;
    }

    static String describe(String title, String... names) {
        return title + " count=" + names.length
                + " " + Arrays.toString(names);
    }

    static void changeFirst(int... values) {
        if (values != null && values.length > 0) {
            values[0] = 99;
        }
    }

    static void showRows(int[]... rows) {
        System.out.println("row count=" + rows.length);
        for (int i = 0; i < rows.length; i++) {
            System.out.println("row " + i + "=" + Arrays.toString(rows[i]));
        }
    }

    static String choose(int value) {
        return "single";
    }

    static String choose(int... values) {
        return "varargs count=" + values.length;
    }

    public static void main(String[] args) {
        System.out.println("sum empty=" + sum());
        System.out.println("sum one=" + sum(7));
        System.out.println("sum many=" + sum(1, 2, 3));

        int[] existing = {2, 3, 4};
        System.out.println("array before=" + Arrays.toString(existing));
        System.out.println("sum array=" + sum(existing));
        changeFirst(existing);
        System.out.println("array after change=" + Arrays.toString(existing));
        changeFirst(8, 9);
        System.out.println("array after temporary=" + Arrays.toString(existing));

        System.out.println(describe("empty"));
        System.out.println(describe("names", "Lan", "Minh"));
        String[] savedNames = {"An", "Binh"};
        System.out.println(describe("array", savedNames));

        showRows(new int[]{1, 2}, new int[]{3});
        int[][] matrix = {{4}, {5, 6}};
        showRows(matrix);

        System.out.println("choose one=" + choose(4));
        System.out.println("choose two=" + choose(4, 5));
        System.out.println("choose empty=" + choose());

        try {
            sum((int[]) null);
        } catch (IllegalArgumentException ex) {
            System.out.println("null input=" + ex.getMessage());
        }
    }
}
~~~

**Bash (Linux/macOS), JDK 17+:**

~~~bash
javac --release 17 -encoding UTF-8 VarargsArrayDemo.java
java -cp . VarargsArrayDemo
~~~

**PowerShell (Windows), JDK 17+:**

~~~powershell
javac --release 17 -encoding UTF-8 .\VarargsArrayDemo.java
java -cp . VarargsArrayDemo
~~~

### Exact expected stdout

~~~text
sum empty=0
sum one=7
sum many=6
array before=[2, 3, 4]
sum array=9
array after change=[99, 3, 4]
array after temporary=[99, 3, 4]
empty count=0 []
names count=2 [Lan, Minh]
array count=2 [An, Binh]
row count=2
row 0=[1, 2]
row 1=[3]
row count=2
row 0=[4]
row 1=[5, 6]
choose one=single
choose two=varargs count=2
choose empty=varargs count=0
null input=null array
~~~

## 10. Lỗi thường gặp và bài tập

| Sai lầm | Nguyên nhân | Cách sửa |
|---|---|---|
| Nghĩ `int...` là kiểu dữ liệu mới | Trong thân nó là `int[]` | Dùng `.length`, `[i]`, vòng duyệt mảng |
| Đặt tham số sau varargs | Varargs phải đứng cuối | `f(String label, int... values)` |
| Có hai varargs trong một phương thức | Cú pháp không hợp lệ | Chỉ dùng một tham số varargs |
| Khai báo đồng thời `f(int[])` và `f(int...)` | Trùng chữ ký | Giữ một định nghĩa |
| Cho rằng `f(existingArray)` tự copy mảng | Truyền giá trị tham chiếu mảng cũ | Tránh mutate hoặc sao chép rõ ràng |
| Nghĩ `f()` tạo tham số null | Lời gọi varargs rỗng tạo mảng dài 0 | Phân biệt `f((int[]) null)` |
| Nhầm `int[]...` với `int...` | Kiểu phần tử khác nhau | `int[]...` tương ứng `int[][]` |
| Lạm dụng overload varargs | Dễ mơ hồ ở lời gọi | Dùng tên/phân loại tham số rõ ràng |

**Bài A.** Với `static int count(String... words) { return words.length; }`, `count()` trả bao nhiêu? **0**; `count("a", "b")` trả **2**; `count(new String[]{"a"})` trả **1**.

**Bài B.** Vì sao không định nghĩa cùng lúc `print(int[] a)` và `print(int... a)`? Vì Java coi hai dạng có **cùng kiểu tham số mảng**, gây trùng chữ ký phương thức.

**Bài C.** `changeFirst(existing)` có sửa mảng caller không? **Có**, nếu mảng có phần tử và method gán vào `values[0]`. Với danh sách đối số riêng `changeFirst(1, 2)`, caller không có mảng gốc tương ứng để quan sát.

**Bài D.** Nếu `int[][] a = {{1}, {2, 3}}`, `showRows(a)` nhận tham số kiểu gì? **`int[][]`**, vì khai báo `int[]... rows` là varargs của `int[]`.

## 11. Checklist và kết luận

- [ ] Biết cú pháp `T... values` và varargs phải là tham số cuối.
- [ ] Hiểu trong thân phương thức, varargs chính là `T[]`.
- [ ] Gọi được với 0, 1, nhiều đối số hoặc với mảng có sẵn.
- [ ] Phân biệt mảng tạm từ danh sách đối số với mảng caller truyền trực tiếp.
- [ ] Không tự giả định varargs luôn sao chép mảng được truyền.
- [ ] Phân biệt mảng rỗng và giá trị null truyền chủ đích.
- [ ] Biết `int[]...` tương ứng kiểu tham số `int[][]`.
- [ ] Nhận diện trường hợp overload varargs mơ hồ.
- [ ] Chạy `VarargsArrayDemo.java` trên JDK 17+ và đối chiếu exact stdout.

**Kết luận:** Varargs `T...` làm cách **gọi** phương thức linh hoạt hơn, nhưng trong **thân** nó vẫn là mảng `T[]`. Khi truyền danh sách phần tử, Java tạo mảng chứa các giá trị đó; khi truyền một mảng có sẵn, phương thức nhận tham chiếu tới **chính mảng ấy**, nên thay đổi phần tử có thể ảnh hưởng caller.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="methods-010">Đệ quy, trường hợp dừng và nguy cơ StackOverflowError</a>

<details>
<summary>Xem chi tiết</summary>

# Đệ quy, trường hợp dừng và nguy cơ `StackOverflowError` — Java 17+

## 1. Đệ quy là gì?

**Đệ quy (recursion)** là kỹ thuật một phương thức gọi lại **chính nó** (trực tiếp), hoặc gọi một phương thức khác dẫn tới lời gọi ban đầu (gián tiếp), nhằm giải một bài toán qua các bài toán con nhỏ hơn.

Ví dụ: tính giai thừa số nguyên không âm `n`:

~~~java
static long factorial(int n) {
    if (n == 0) {
        return 1L;                  // Trường hợp dừng (base case).
    }
    return n * factorial(n - 1);    // Bước đệ quy (recursive case).
}
~~~

Với `n = 4`, ta có `4! = 4 × 3 × 2 × 1 = 24`. Trình tự phép gọi có thể hình dung:

~~~text
factorial(4)
  4 * factorial(3)
        3 * factorial(2)
              2 * factorial(1)
                    1 * factorial(0)
                          return 1
                    return 1
              return 2
        return 6
  return 24
~~~

Phương thức trên **chỉ là minh họa** cho đầu vào `n >= 0`. Nếu gọi với số âm, `n - 1` sẽ không tiến tới `0` theo mục đích. Chương trình hoàn chỉnh bên dưới thêm kiểm tra miền đầu vào và giới hạn an toàn của kiểu `long`.

Đệ quy không phải là một kiểu vòng lặp đặc biệt: mỗi lời gọi thực sự là **một lần gọi phương thức** với tham số và trạng thái thực thi riêng.

## 2. Hai điều kiện quan trọng để đệ quy kết thúc

Một thuật toán đệ quy đúng thường cần cả hai yếu tố:

1. **Trường hợp dừng (base case):** điều kiện mà phương thức trả kết quả hoặc kết thúc mà **không gọi đệ quy thêm**. Ví dụ `n == 0` khi tính giai thừa.
2. **Bước tiến (progress toward base case):** mỗi lần gọi đệ quy phải làm bài toán **gần trường hợp dừng hơn**. Ví dụ `n - 1` khi `n > 0`.

~~~java
static int sumToZero(int n) {
    if (n < 0) {
        throw new IllegalArgumentException("n must be non-negative");
    }
    if (n == 0) {
        return 0;
    }
    return n + sumToZero(n - 1);
}
~~~

~~~text
sumToZero(3)
= 3 + sumToZero(2)
= 3 + 2 + sumToZero(1)
= 3 + 2 + 1 + sumToZero(0)
= 3 + 2 + 1 + 0
= 6
~~~

Có trường hợp dừng nhưng **không tiến tới nó** cũng là lỗi:

~~~java
// KHÔNG CHẠY: minh họa lỗi.
// static int wrong(int n) {
//     if (n == 0) return 0;
//     return n + wrong(n); // n không thay đổi.
// }
~~~

Tương tự, lời gọi `wrong(-1)` trong phiên bản chỉ giảm `n - 1` nhưng dừng ở `n == 0` cũng không tiến đúng miền đầu vào. Hãy xác định **miền hợp lệ**, **trường hợp dừng** và **đại lượng giảm dần** trước khi viết phương thức.

## 3. Stack frame và hai pha gọi — quay về

JVM quản lý quá trình gọi phương thức bằng **ngăn xếp lời gọi** (*call stack*). Mỗi lần gọi đệ quy có một **stack frame** chứa thông tin thực thi như biến cục bộ, tham số và vị trí tiếp tục sau lời gọi.

Khi gọi `factorial(3)`:

~~~text
Đang đi sâu xuống (call):
factorial(3) -> factorial(2) -> factorial(1) -> factorial(0)

Ở độ sâu lớn nhất:
+--------------------+
| factorial(0)       | <- lời gọi hiện tại; gặp base case
+--------------------+
| factorial(1)       |
+--------------------+
| factorial(2)       |
+--------------------+
| factorial(3)       |
+--------------------+

Đang quay về (return):
factorial(0) -> factorial(1) -> factorial(2) -> factorial(3)
~~~

`factorial(3)` phải đợi kết quả của `factorial(2)`, phương thức đó lại đợi `factorial(1)`, v.v. Sau trường hợp dừng, kết quả được trả về **ngược thứ tự gọi**, và các frame hoàn thành lần lượt được loại khỏi stack.

Mỗi lời gọi có bản riêng của tham số `n`. Việc thay đổi `n` ở một lần gọi không tự thay biến `n` của frame khác.

## 4. Truy vết thứ tự thực thi

Để quan sát các lời gọi, in thông tin **trước** và **sau** bước đệ quy:

~~~java
static void trace(int n) {
    System.out.println("enter " + n);
    if (n == 0) {
        System.out.println("base " + n);
        return;
    }
    trace(n - 1);
    System.out.println("leave " + n);
}
~~~

Khi `trace(3)` chạy với đầu vào không âm:

~~~text
enter 3
enter 2
enter 1
enter 0
base 0
leave 1
leave 2
leave 3
~~~

- Các dòng `enter` xuất hiện khi **đi xuống** chuỗi gọi.
- `base 0` là điểm dừng, không gọi thêm.
- Các dòng `leave` xuất hiện khi các lời gọi **quay về**.
- Nhánh `n == 0` dùng `return;` để kết thúc phương thức `void`, nên không in `leave 0`.

Khi đọc một phương thức đệ quy, cần theo dõi đồng thời **điều kiện dừng**, **tham số của lời gọi tiếp theo** và **công việc xảy ra sau khi lời gọi trả về**.

## 5. `StackOverflowError` là gì?

Mỗi lời gọi đệ quy bổ sung thêm một stack frame, tiêu thụ tài nguyên của ngăn xếp luồng. Nếu chương trình tiếp tục gọi sâu tới mức không thể tạo thêm frame, JVM có thể ném **`java.lang.StackOverflowError`**.

Các nguyên nhân điển hình:

- **Không có trường hợp dừng**, ví dụ phương thức tự gọi chính mình không điều kiện.
- Có base case nhưng **tham số không tiến gần nó**, ví dụ `f(n)` gọi `f(n)`.
- Điều kiện dừng không bao phủ **miền đầu vào**: đếm xuống `0` nhưng bắt đầu từ số âm.
- Thuật toán **đúng** nhưng cần quá nhiều lời gọi lồng nhau, ví dụ xử lý một cấu trúc dữ liệu quá sâu hoặc đệ quy tuyến tính với đầu vào rất lớn.
- Các phương thức **đệ quy gián tiếp** gọi qua lại mà không có điều kiện kết thúc.

~~~java
// KHÔNG CHẠY đoạn mã này:
// static void endless() {
//     endless(); // Không có base case.
// }
~~~

**Đừng chạy đoạn ví dụ lỗi chỉ để xem stack tràn.** `StackOverflowError` là một `Error`, không phải `Exception`. Bắt nó bằng `catch (StackOverflowError e)` không phải cách bình thường để kiểm soát luồng nghiệp vụ: stack có thể đang ở trạng thái thiếu tài nguyên, và việc xử lý có thể không an toàn.

Không có một **số lần gọi tối đa cố định** áp dụng cho mọi JVM. Độ sâu chịu ảnh hưởng bởi giới hạn stack của luồng, JVM, hệ điều hành, biên dịch JIT và kích thước frame; không nên dựa vào một ngưỡng đo thử trên máy cụ thể.

## 6. Đệ quy đúng vẫn có nguy cơ tràn stack

Xét:

~~~java
static long sumRecursively(int n) {
    if (n == 0) return 0;
    return n + sumRecursively(n - 1);
}
~~~

Về logic với `n >= 0`, `n` giảm đều và cuối cùng đạt `0`. Tuy nhiên với `n` rất lớn, phương thức cần xấp xỉ `n + 1` lời gọi lồng nhau, có thể tràn stack **trước khi tới base case**.

Phiên bản lặp:

~~~java
static long sumIteratively(int n) {
    if (n < 0) throw new IllegalArgumentException("negative n");
    long total = 0;
    for (long i = 1; i <= n; i++) {
        total += i;
    }
    return total;
}
~~~

Dùng biến đếm `long` để `i++` không tràn khi `n == Integer.MAX_VALUE`; với bộ đếm `int`, điều kiện `i <= n` có thể tiếp tục đúng sau khi `i` tràn sang số âm. Vòng lặp cũng tránh **tăng độ sâu call stack theo `n`**. Với `n` thuộc miền `int` không âm, tổng từ 1 đến `n` luôn nằm trong miền `long`; nếu mở rộng đầu vào hoặc thay phép tính, cần kiểm tra tràn số riêng. Công thức `n(n+1)/2` cũng cần tránh tràn ở các phép nhân trung gian.

## 7. Độ phức tạp: thời gian và bộ nhớ stack

| Bài toán / cách làm | Thời gian gần đúng | Stack bổ sung | Nhận xét |
|---|---|---|---|
| Giai thừa đệ quy `factorial(n)` | `O(n)` | `O(n)` | Mỗi lần giảm `n` một đơn vị |
| Giai thừa với vòng `for` | `O(n)` | `O(1)` | Dễ tránh stack sâu |
| Tìm kiếm nhị phân đệ quy trong mảng đã sắp xếp | `O(log n)` | `O(log n)` | Mỗi lần loại khoảng một nửa |
| Tìm kiếm nhị phân với `while` | `O(log n)` | `O(1)` | Cũng cần dữ liệu đã sắp xếp |
| Fibonacci đệ quy ngây thơ `fib(n-1)+fib(n-2)` | `O(2^n)` (chặn trên) | `O(n)` | Tính lặp lại nhiều bài toán con |

**Lưu ý:** “Đệ quy” không tự động đồng nghĩa với chậm. Chi phí phụ thuộc số bài toán con và độ sâu. Với bài toán chia để trị, đệ quy thường diễn đạt cấu trúc bài toán dễ đọc. Với một chuỗi tuyến tính dài, vòng lặp có thể thực tế hơn.

Fibonacci đệ quy ngây thơ có base case ở `0` và `1` nhưng vẫn có thể chạy chậm vì lặp lại các phép tính. Dùng **memoization** hoặc vòng lặp để tránh tính trùng, chứ chỉ sửa điều kiện dừng là chưa đủ.

## 8. Đệ quy trên khoảng chỉ số: tìm kiếm nhị phân

Không phải lúc nào base case cũng là `n == 0`. Với tìm kiếm nhị phân trên một mảng **đã sắp xếp tăng dần**, bài toán con là một **khoảng chỉ số** `low..high`.

~~~java
static int search(int[] a, int target, int low, int high) {
    if (low > high) return -1;                 // Khoảng rỗng.
    int mid = low + (high - low) / 2;
    if (a[mid] == target) return mid;          // Tìm thấy.
    if (target < a[mid]) {
        return search(a, target, low, mid - 1);
    }
    return search(a, target, mid + 1, high);
}
~~~

Ở mỗi bước, khoảng mới **nhỏ hơn** khoảng cũ; độ sâu tăng theo `O(log n)`. Base case `low > high` diễn tả “không còn vị trí để kiểm tra”; trường hợp `a[mid] == target` diễn tả “đã tìm thấy”.

Ví dụ `a = {1,3,5,7,9}`:

~~~text
search target=7 trong [0..4], mid=2 -> a[2]=5
search tiếp [3..4], mid=3 -> a[3]=7
return 3
~~~

**Tiền điều kiện:** mảng phải được sắp xếp đúng thứ tự mà thuật toán dùng để so sánh. Nếu đầu vào chưa sắp xếp, kết quả không còn được bảo đảm.

## 9. Khi nào nên dùng đệ quy, khi nào dùng vòng lặp?

Chọn **đệ quy** khi mô tả bài toán tự nhiên bằng các bài toán con, ví dụ:

- Duyệt cây thư mục, cây biểu thức, cấu trúc lồng nhau.
- Tìm kiếm nhị phân, chia để trị trên khoảng dữ liệu.
- Thuật toán quay lui (*backtracking*) có trạng thái, điểm dừng và hoàn tác rõ ràng.

Cân nhắc **vòng lặp** khi:

- Các bước lặp đơn giản và tuyến tính.
- Đầu vào có thể gây độ sâu hàng trăm nghìn/hàng triệu lời gọi.
- Cần kiểm soát tài nguyên stack hoặc hiệu năng thực tế.
- Có thể tính kết quả bằng bộ nhớ `O(1)` mà không làm code khó hiểu hơn.

Java **không bảo đảm tối ưu hóa đệ quy đuôi** (*tail-call optimization*). Một lời gọi đệ quy đặt ở cuối phương thức **không có nghĩa** stack frame chắc chắn được tái sử dụng trên JVM. Với bài toán cần xử lý độ sâu rất lớn, dùng vòng lặp hoặc một cấu trúc stack tường minh khi thích hợp.

Tham số `-Xss` có thể điều chỉnh kích thước stack trên một số cấu hình JVM, nhưng **không sửa lỗi logic thiếu base case** và không phải giải pháp đầu tiên cho thuật toán có độ sâu tăng không kiểm soát.

## 10. Chương trình Java 17+ chạy độc lập

Tạo file đúng tên **`RecursionBaseCaseDemo.java`**:

~~~java
import java.util.Arrays;

public final class RecursionBaseCaseDemo {
    private RecursionBaseCaseDemo() {}

    // Validate tại ranh giới gọi; 20! còn nằm trong long.
    static long factorial(int n) {
        if (n < 0 || n > 20) {
            throw new IllegalArgumentException("factorial n must be 0..20");
        }
        return factorialRecursive(n);
    }

    private static long factorialRecursive(int n) {
        if (n == 0) return 1L;             // Base case: 0! = 1.
        return n * factorialRecursive(n - 1);
    }

    static long factorialIterative(int n) {
        if (n < 0 || n > 20) {
            throw new IllegalArgumentException("factorial n must be 0..20");
        }
        long value = 1L;
        for (int i = 2; i <= n; i++) {
            value *= i;
        }
        return value;
    }

    static void trace(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("trace n must be >= 0");
        }
        System.out.println("enter " + n);
        if (n == 0) {
            System.out.println("base " + n);
            return;
        }
        trace(n - 1);
        System.out.println("leave " + n);
    }

    static int digitSum(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("digits n must be >= 0");
        }
        if (n < 10) return n;
        return (n % 10) + digitSum(n / 10);
    }

    // Chỉ dùng với mảng int[] sắp xếp tăng dần.
    static int binarySearch(int[] sorted, int target) {
        if (sorted == null) {
            throw new IllegalArgumentException("array must not be null");
        }
        return search(sorted, target, 0, sorted.length - 1);
    }

    private static int search(int[] a, int target, int low, int high) {
        if (low > high) return -1;
        int mid = low + (high - low) / 2;
        if (a[mid] == target) return mid;
        if (target < a[mid]) {
            return search(a, target, low, mid - 1);
        }
        return search(a, target, mid + 1, high);
    }

    public static void main(String[] args) {
        System.out.println("factorial 0=" + factorial(0));
        System.out.println("factorial 5=" + factorial(5));
        System.out.println("factorial 10=" + factorial(10));
        System.out.println("factorial 20=" + factorial(20));
        System.out.println("factorial loop 5=" + factorialIterative(5));
        System.out.println("digit sum 405=" + digitSum(405));
        System.out.println("digit sum 0=" + digitSum(0));

        trace(3);

        int[] sorted = {1, 3, 5, 7, 9};
        System.out.println("sorted=" + Arrays.toString(sorted));
        System.out.println("search 7=" + binarySearch(sorted, 7));
        System.out.println("search 2=" + binarySearch(sorted, 2));
        System.out.println("search empty="
                + binarySearch(new int[0], 7));

        try {
            factorial(-1);
        } catch (IllegalArgumentException ex) {
            System.out.println("invalid factorial=" + ex.getMessage());
        }
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên, chỉ dùng Java chuẩn; không cần thư viện ngoài hay preview features. Mã ví dụ **không cố ý gây `StackOverflowError`**.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 RecursionBaseCaseDemo.java
java -cp . RecursionBaseCaseDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\RecursionBaseCaseDemo.java
java -cp . RecursionBaseCaseDemo
~~~

### Exact expected stdout

~~~text
factorial 0=1
factorial 5=120
factorial 10=3628800
factorial 20=2432902008176640000
factorial loop 5=120
digit sum 405=9
digit sum 0=0
enter 3
enter 2
enter 1
enter 0
base 0
leave 1
leave 2
leave 3
sorted=[1, 3, 5, 7, 9]
search 7=3
search 2=-1
search empty=-1
invalid factorial=factorial n must be 0..20
~~~

**Đối chiếu:** `factorial(0)` kết thúc ngay; `factorial(5)` đi sâu rồi nhân kết quả khi quay về. `digitSum(405)` tính `5 + 0 + 4 = 9` qua `n % 10` và `n / 10`. `trace(3)` cho thấy thứ tự vào/ra frame. Tìm kiếm nhị phân có hai kiểu dừng: tìm thấy mục tiêu hoặc hết khoảng tìm kiếm. Đầu vào âm của `factorial` bị từ chối trước khi bắt đầu chuỗi đệ quy.

## 11. Những lỗi thường gặp và cách khắc phục

| Sai lầm | Hậu quả | Cách sửa |
|---|---|---|
| Không viết trường hợp dừng | Lời gọi có thể tăng không giới hạn | Viết `if (base) return ...;` |
| Có base case nhưng không thay tham số | Không tiến tới base case | Chứng minh mỗi lời gọi làm bài toán nhỏ đi |
| Chỉ xét `n == 0`, bỏ qua `n < 0` | Đầu vào âm có thể không dừng đúng | Kiểm tra miền đầu vào |
| Dùng `return n * factorial(n)` | Tự gọi lại với cùng bài toán | Thường cần `factorial(n - 1)` |
| Gọi `factorial(21)` bằng `long` không kiểm soát | **Tràn số học**, không nhất thiết tràn stack | Giới hạn 0..20 hoặc dùng `BigInteger` |
| Đệ quy tuyến tính với đầu vào cực lớn | Stack frame tăng tuyến tính | Chuyển vòng lặp/stack tường minh |
| Tin Java luôn tối ưu tail recursion | JVM không bảo đảm | Thiết kế độc lập tối ưu hóa này |
| Bắt `StackOverflowError` để sửa lỗi đệ quy | Có thể thiếu tài nguyên, che bug | Sửa logic và giới hạn độ sâu |
| Tìm kiếm nhị phân khi mảng chưa sắp xếp | Kết quả tìm kiếm có thể sai | Đảm bảo tiền điều kiện sorted |
| Chỉ quan tâm thời gian `O(n)` | Bỏ qua stack space `O(n)` | Xem cả bộ nhớ stack |

**Phân biệt hai nguy cơ:** `StackOverflowError` liên quan đến ngăn xếp lời gọi; **tràn số nguyên** xảy ra khi kết quả vượt phạm vi kiểu số, thường không tự ném `StackOverflowError`. Đệ quy giai thừa của `long` phải kiểm soát cả độ sâu lẫn miền giá trị kết quả.

## 12. Bài tập và đáp án

**A — Base case ở đâu?**

~~~java
static int countDown(int n) {
    if (n == 0) return 0;
    return 1 + countDown(n - 1);
}
~~~

**Đáp án:** base case `n == 0`. Với `n = 3`, kết quả là `3`, nhưng chưa an toàn với `n < 0`. Thêm guard cho số âm.

**B — Vì sao đoạn sau có thể không dừng?**

~~~java
// Không chạy:
static void loopForever(int n) {
    if (n == 0) return;
    loopForever(n);
}
~~~

**Đáp án:** tham số `n` không đổi; với `n != 0`, không thể tiến tới base case.

**C — `trace(2)` in gì?**

~~~text
enter 2
enter 1
enter 0
base 0
leave 1
leave 2
~~~

**D — Khi nào stack tăng theo `O(n)`?**  
**Đáp án:** khi các lời gọi lồng nhau giảm `n` từng đơn vị đến base case, ví dụ `factorial(n)`, cần khoảng `n + 1` frame ở độ sâu lớn nhất.

**E — `binarySearch(new int[]{1,3,5,7,9}, 7)` cho gì?**  
**Đáp án:** `3` (chỉ số tính từ 0); với mục tiêu 2 trả `-1`.

**F — Có thể thay giai thừa đệ quy bằng vòng lặp để tránh stack sâu không?**  
**Đáp án:** có; vòng lặp dùng stack bổ sung `O(1)` cho cách cài đặt thông thường. Nhưng vẫn phải chú ý giai thừa lớn tràn giới hạn `long`.

## 13. Checklist và kết luận

- [ ] Hiểu đệ quy là lời gọi phương thức lặp lại trên bài toán con.
- [ ] Xác định được **base case** không gọi đệ quy.
- [ ] Chứng minh các lời gọi tiến tới base case trên miền đầu vào hợp lệ.
- [ ] Hiểu trình tự “gọi sâu xuống” và “trả kết quả ngược lên” trên call stack.
- [ ] Nhận diện `StackOverflowError` do thiếu dừng hoặc do độ sâu quá lớn.
- [ ] Không cố ý chạy đệ quy vô hạn hoặc dựa vào `catch StackOverflowError`.
- [ ] Phân biệt lỗi tràn stack và lỗi tràn số học.
- [ ] Biết so sánh chi phí thời gian, bộ nhớ của đệ quy và vòng lặp.
- [ ] Không mặc định Java có tối ưu hóa tail call.
- [ ] Đọc được base case theo khoảng `low > high` ở tìm kiếm nhị phân.
- [ ] Chạy `RecursionBaseCaseDemo.java` trên JDK 17+ và đối chiếu exact stdout.

**Kết luận:** Một thuật toán đệ quy an toàn cần **trường hợp dừng đúng**, **bước tiến làm nhỏ bài toán** và **độ sâu thực thi phù hợp với stack**. Base case đúng về logic chưa bảo đảm an toàn khi đầu vào quá lớn. Hãy kiểm tra miền đầu vào, dự tính cả stack space, và chọn vòng lặp khi độ sâu có nguy cơ vượt khả năng của JVM.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="methods-011">Tách bài toán thành các phương thức có trách nhiệm rõ ràng</a>

<details>
<summary>Xem chi tiết</summary>

# Tách bài toán thành các phương thức có trách nhiệm rõ ràng — Java 17+

## 1. Vì sao cần tách phương thức?

Khi một phương thức vừa kiểm tra đầu vào, vừa tính toán, vừa xếp loại, vừa in kết quả, người đọc phải theo dõi **nhiều mục đích cùng lúc**. Khi yêu cầu thay đổi, dễ sửa nhầm logic không liên quan.

Một phương thức có **trách nhiệm rõ ràng** nên mô tả được bằng một hành động cụ thể: `validateScores` kiểm tra dữ liệu, `calculateAverage` tính trung bình, `classify` xếp loại, `formatReport` tạo chuỗi và `showCase` hiển thị.

**Nguyên tắc thực hành:** một phương thức nên có **một lý do chính để thay đổi**, chứ không nhất thiết chỉ có một dòng lệnh. Tách theo ý nghĩa nghiệp vụ, không chia vụn mọi phép gán hay phép cộng.

Ví dụ bài toán: nhận tên học viên và mảng điểm nguyên (0–100), tính trung bình **lấy phần nguyên**, xếp loại và xuất một dòng báo cáo. Nếu đầu vào sai, báo lỗi rõ ràng.

## 2. Phân rã bài toán thành các bước

| Trách nhiệm | Phương thức | Đầu vào → đầu ra |
|---|---|---|
| Kiểm tra tên | `requireName` | `String` → `String` đã bỏ khoảng trắng đầu/cuối hoặc ném lỗi |
| Kiểm tra danh sách điểm | `validateScores` | `int[]` → `void` hoặc ném lỗi |
| Tính điểm trung bình | `calculateAverage` | `int[]` hợp lệ → `int` |
| Xếp loại | `classify` | `int average` → `String` |
| Tạo kết quả | `formatReport` | Tên, số môn, trung bình, xếp loại → `String` |
| Phối hợp các bước | `buildReport` | Tên + điểm → chuỗi hoàn chỉnh |
| Hiển thị / xử lý lỗi | `showCase` | Dữ liệu mẫu → in báo cáo hoặc lỗi |

~~~mermaid
flowchart TD
    A["showCase"] --> B["buildReport"]
    B --> C["requireName"]
    B --> D["validateScores"]
    B --> E["calculateAverage"]
    B --> F["classify"]
    B --> G["formatReport"]
    G --> H["Chuỗi kết quả"]
    H --> A
    A --> I["System.out.println"]
~~~

`buildReport` là phương thức **điều phối**: gọi các phương thức nhỏ theo đúng trình tự, không tự lặp lại công thức tính điểm hoặc xếp loại. Đây là cách tách hợp lý giữa “biết cần làm gì” và “biết một bước làm như thế nào”.

## 3. Xác định hợp đồng của từng phương thức

Trước khi code, hãy ghi rõ **đầu vào, đầu ra và tình huống lỗi**:

- `requireName(name)`: từ chối `null` hoặc chỉ toàn khoảng trắng; tên hợp lệ được `strip()`.
- `validateScores(scores)`: từ chối `null`, mảng rỗng, điểm ngoài khoảng 0–100; không sửa mảng.
- `calculateAverage(scores)`: giả định dữ liệu **đã được kiểm tra**, tính tổng bằng `long`, chia nguyên cho số môn. Ví dụ `(70 + 71) / 2 = 70`.
- `classify(avg)`: `avg >= 80` → `GOOD`; `avg >= 50` → `PASS`; còn lại → `RETRY`.
- `formatReport(...)`: chỉ ghép chuỗi, không in và không thay dữ liệu.
- `buildReport(...)`: trả chuỗi; nếu dữ liệu sai, cho phép `IllegalArgumentException` đi lên caller.

Mỗi phương thức nên có **tên động từ rõ nghĩa**, kiểu trả về đúng công việc, và không thay đổi trạng thái ngoài ý muốn. `validateScores` là `void` vì nhiệm vụ của nó là **xác thực hoặc báo lỗi**, không phải tính tổng.

**Cảnh báo về tính toán:** sử dụng `long` để cộng các giá trị `int` hạn chế tràn tổng; với dữ liệu điểm 0–100 và độ dài mảng Java hợp lệ, `long` đủ chứa tổng. Trung bình ở đây là **phép chia nguyên có chủ đích**, không phải làm tròn số thực.

## 4. Tách tính toán khỏi tác dụng phụ

Một hàm như:

~~~java
static int classify(int average) {
    // SAI về ý nghĩa ví dụ: vừa quyết định xếp loại vừa in, khó tái sử dụng.
    System.out.println("classifying");
    return average >= 50 ? 1 : 0;
}
~~~

trộn **kết quả tính toán** với **ghi ra màn hình**. Nó khó dùng khi sau này phải ghi vào file, trả JSON hoặc chạy test không mong đợi stdout.

Cách tách:

~~~java
static String classify(int average) {
    if (average >= 80) return "GOOD";
    if (average >= 50) return "PASS";
    return "RETRY";
}

// Gọi: chỉ khi phần giao diện cần hiển thị
System.out.println(classify(75)); // PASS
~~~

`classify` không phụ thuộc `Scanner`, `System.out` hay nơi hiển thị kết quả. Cùng một đầu vào cho cùng kết quả, dễ thử các biên 49/50/79/80.

**Một phương thức không cần “thuần” tuyệt đối mới có trách nhiệm rõ ràng.** Việc in thông báo là trách nhiệm hợp lệ của `showCase`; cần tránh để nó xen lẫn công thức tính.

## 5. `private`, `static` và việc tái sử dụng

Trong ví dụ, các bước nội bộ được khai báo `private static`:

~~~java
private static int calculateAverage(int[] scores) { /* ... */ }
~~~

- `private`: giới hạn nơi có thể gọi từ ngoài lớp, tránh làm lộ chi tiết triển khai thành API.
- `static`: công việc không cần trạng thái instance, có thể gọi từ phương thức `static` cùng lớp.
- Kiểu tham số / kiểu trả về tạo **hợp đồng** giúp compiler kiểm tra cách gọi.

Không phải tất cả các phương thức trong mọi chương trình đều nên `static`: khi trách nhiệm thật sự gắn với trạng thái một đối tượng, instance method có thể phù hợp hơn. Bài này dùng `static` vì ví dụ xử lý dữ liệu đầu vào và trả kết quả không cần object chuyên biệt.

**Tránh trùng lặp:** nếu ba nơi đều kiểm tra `score >= 80`, hãy dùng chung một `classify`; khi đổi ngưỡng, chỉ phải sửa một nơi. Nhưng đừng tạo hàng chục phương thức một dòng chỉ để “đủ số lượng hàm”.

## 6. Chương trình Java 17+ hoàn chỉnh

Lưu file đúng tên **`ClearMethodResponsibilitiesDemo.java`**:

~~~java
public final class ClearMethodResponsibilitiesDemo {
    private ClearMethodResponsibilitiesDemo() {}

    // Trách nhiệm 1: kiểm tra và chuẩn hóa tên.
    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return name.strip();
    }

    // Trách nhiệm 2: xác thực điểm, không tính toán.
    private static void validateScores(int[] scores) {
        if (scores == null || scores.length == 0) {
            throw new IllegalArgumentException("scores must not be null or empty");
        }
        for (int i = 0; i < scores.length; i++) {
            if (scores[i] < 0 || scores[i] > 100) {
                throw new IllegalArgumentException(
                        "score at index " + i + " must be 0..100");
            }
        }
    }

    // Trách nhiệm 3: tính trung bình, giả định đã validate.
    private static int calculateAverage(int[] scores) {
        long sum = 0;
        for (int score : scores) {
            sum += score;
        }
        return (int) (sum / scores.length);
    }

    // Trách nhiệm 4: áp dụng duy nhất quy tắc xếp loại.
    private static String classify(int average) {
        if (average >= 80) return "GOOD";
        if (average >= 50) return "PASS";
        return "RETRY";
    }

    // Trách nhiệm 5: định dạng; không in ra màn hình.
    private static String formatReport(
            String name, int count, int average, String level) {
        return name + " | count=" + count
                + " | avg=" + average + " | level=" + level;
    }

    // Điều phối luồng nghiệp vụ, không nhân đôi công thức.
    public static String buildReport(String name, int[] scores) {
        String cleanName = requireName(name);
        validateScores(scores);
        int average = calculateAverage(scores);
        String level = classify(average);
        return formatReport(cleanName, scores.length, average, level);
    }

    // Ranh giới hiển thị: in kết quả và xử lý đầu vào không hợp lệ.
    private static void showCase(String name, int[] scores) {
        try {
            System.out.println(buildReport(name, scores));
        } catch (IllegalArgumentException ex) {
            System.out.println("ERROR: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        showCase(" An ", new int[]{90, 80, 70});
        showCase("Binh", new int[]{40, 50, 60, 50});
        showCase("Chi", new int[]{30, 40});
        showCase("Dung", new int[]{});
        showCase("Em", new int[]{90, 101});
        showCase("", new int[]{90});
        showCase("Phuc", null);
        showCase("Giang", new int[]{100});
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên, terminal đặt tại thư mục chứa file. Chương trình chỉ dùng Java chuẩn, không cần thư viện ngoài, Maven/Gradle hoặc preview feature.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 ClearMethodResponsibilitiesDemo.java
java -cp . ClearMethodResponsibilitiesDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\ClearMethodResponsibilitiesDemo.java
java -cp . ClearMethodResponsibilitiesDemo
~~~

### Exact expected stdout

~~~text
An | count=3 | avg=80 | level=GOOD
Binh | count=4 | avg=50 | level=PASS
Chi | count=2 | avg=35 | level=RETRY
ERROR: scores must not be null or empty
ERROR: score at index 1 must be 0..100
ERROR: name must not be blank
ERROR: scores must not be null or empty
Giang | count=1 | avg=100 | level=GOOD
~~~

Đọc theo luồng: `showCase` gọi `buildReport`, nơi này kiểm tra tên và điểm trước khi tính. Ví dụ `Em` chứa điểm 101 nên bị từ chối **trước** bước tính trung bình; dữ liệu sai không bị âm thầm thay thế bằng giá trị 0.

## 7. Kiểm thử theo trách nhiệm và trường hợp biên

Khi đã tách phương thức, có thể kiểm tra mỗi nhóm yêu cầu riêng:

| Nội dung cần kiểm | Input | Expected |
|---|---|---|
| Tên | `" An "` | `"An"` |
| Điểm hợp lệ | `{90,80,70}` | Average `80`, level `GOOD` |
| Ngưỡng trên | Average `79` và `80` | `PASS`, `GOOD` |
| Ngưỡng dưới | Average `49` và `50` | `RETRY`, `PASS` |
| Điểm trung bình không nguyên | `{70,71}` | `70` theo quy tắc chia nguyên |
| Mảng rỗng/null | `{}`, `null` | `IllegalArgumentException` |
| Điểm ngoài biên | `{-1}`, `{101}` | `IllegalArgumentException` |

Ví dụ một kiểm tra đơn giản khi đặt trong cùng lớp:

~~~java
if (!"GOOD".equals(classify(80))) {
    throw new AssertionError("80 must be GOOD");
}
if (!"PASS".equals(classify(50))) {
    throw new AssertionError("50 must be PASS");
}
~~~

Đây là các ca kiểm tra logic, không phải ví dụ khác của câu lệnh `assert` (vốn tắt mặc định nếu không dùng `-ea`). Trong dự án lớn có thể sử dụng JUnit và unit test chuyên dụng; ví dụ chính cố ý không cần thư viện ngoài.

## 8. Mở rộng và lỗi thiết kế cần tránh

**Khi đổi quy tắc xếp loại** từ 80 thành 85, sửa `classify` và test liên quan, không phải sửa code xác thực hoặc in. Khi đổi giao diện xuất từ console sang web, tái sử dụng `buildReport` rồi thay tầng hiển thị.

| Dấu hiệu | Rủi ro | Cách cải thiện |
|---|---|---|
| Một method dài, gồm validate + tính + in | Khó đọc, khó test, khó sửa | Tách theo các mục tiêu riêng |
| `calculateAverage` tự gọi `System.out.println` | Phụ thuộc giao diện | Trả giá trị, để caller hiển thị |
| Nhiều nơi dùng cùng ngưỡng xếp loại | Logic không nhất quán | Tập trung vào `classify` |
| Method tên `process` không rõ kết quả | Người dùng API khó hiểu | Chọn tên như `buildReport`, `validateScores` |
| Hàm tính sửa `scores[i]` ngoài ý muốn | Caller nhìn thấy dữ liệu bị đổi | Tránh tác dụng phụ, ghi rõ hợp đồng |
| Tách mọi dòng lệnh thành một method | Code phân mảnh, phải nhảy liên tục | Tách theo **trách nhiệm thực sự** |
| Bắt mọi `Exception` trong hàm tính | Có thể che bug | Xử lý lỗi ở ranh giới thích hợp |
| Không xác định dữ liệu đầu vào hợp lệ | Dễ chia cho 0, trả sai | Kiểm tra trước khi tính |
| Để mọi helper thành `public` | Mở rộng API không cần thiết | Giữ `private` khi chỉ dùng trong lớp |

**Điểm cần cân nhắc:** `buildReport` hiện trả một chuỗi phục vụ một định dạng cố định. Khi ứng dụng phải hỗ trợ nhiều đầu ra phức tạp, có thể trả một đối tượng kết quả (ví dụ record Java 17), để tầng hiển thị định dạng sau. Đừng tăng độ phức tạp khi bài toán chưa cần.

## 9. Bài tập và đáp án

**Bài A.** Một method vừa nhập dữ liệu từ bàn phím, tính tổng, xếp loại và in. Hãy đề xuất cách tách.

**Đáp án:** `readInput` đọc dữ liệu; `validateInput` kiểm tra; `calculateTotal` tính; `classify` xếp loại; `formatReport` tạo chuỗi; `main` hoặc `showResult` phối hợp và hiển thị. Có thể gộp bước nhỏ nếu thật sự cùng một mục tiêu rõ ràng.

**Bài B.** Nếu thay ngưỡng `GOOD` từ 80 thành 85, nên sửa phương thức nào?

**Đáp án:** `classify` và các test của quy tắc mới. Không cần thay `validateScores` hay `formatReport`.

**Bài C.** `buildReport("Mai", new int[]{70,71})` trả giá trị nào?

**Đáp án:** `Mai | count=2 | avg=70 | level=PASS` vì chia nguyên `141 / 2 = 70`.

**Bài D.** Vì sao tính toán và in kết quả nên được tách? **Đáp án:** cùng phép tính có thể dùng trong CLI, web và test mà không phát sinh stdout; tầng giao diện có thể thay độc lập.

## 10. Checklist và kết luận

- [ ] Có thể mô tả trách nhiệm của mỗi phương thức bằng một câu ngắn.
- [ ] Đặt tên method phản ánh hành động hoặc kết quả thật.
- [ ] Biết đầu vào, đầu ra, điều kiện hợp lệ và lỗi có thể ném.
- [ ] Tách xác thực, tính toán, quy tắc nghiệp vụ, định dạng và hiển thị.
- [ ] Dùng một phương thức điều phối để giữ luồng xử lý dễ đọc.
- [ ] Giữ các helper nội bộ `private` khi phù hợp.
- [ ] Hạn chế thay đổi dữ liệu caller ngoài ý muốn.
- [ ] Tập trung các quy tắc lặp lại vào một nơi.
- [ ] Kiểm thử giá trị thông thường, mảng rỗng, null, chỉ số và ngưỡng.
- [ ] Không tách vụn quá mức thành hàng chục method vô nghĩa.
- [ ] Chạy `ClearMethodResponsibilitiesDemo.java` bằng JDK 17+ và so sánh exact stdout.

**Kết luận:** Tách bài toán thành các phương thức có trách nhiệm rõ ràng giúp mỗi thay đổi được **khoanh đúng phạm vi**, dễ đọc, dễ kiểm tra và dễ tái sử dụng. Hãy để hàm kiểm tra làm nhiệm vụ kiểm tra, hàm tính trả kết quả, hàm định dạng xây dựng chuỗi và tầng hiển thị chịu trách nhiệm xuất dữ liệu.

</details>

- [Quay lại đầu trang](#back-to-top)
