<a id="back-to-top"></a>

# Biểu thức

## Menu
- [Biểu thức tạo ra giá trị và có kiểu tại thời điểm biên dịch](#expressions-001)
- [Toán hạng, toán tử và biểu thức lồng nhau](#expressions-002)
- [Độ ưu tiên và tính kết hợp của toán tử](#expressions-003)
- [Dùng dấu ngoặc để làm rõ ý định](#expressions-004)
- [Phân biệt thứ tự nhóm biểu thức với thứ tự đánh giá](#expressions-005)
- [Đánh giá toán hạng và đối số từ trái sang phải](#expressions-006)
- [Tác dụng phụ trong biểu thức và nguy cơ khó dự đoán](#expressions-007)
- [Kết thúc bình thường hoặc đột ngột do ngoại lệ](#expressions-008)

## <a id="expressions-001">Biểu thức tạo ra giá trị và có kiểu tại thời điểm biên dịch</a>

<details>
<summary>Xem chi tiết</summary>

# Biểu thức tạo ra giá trị và có kiểu tại thời điểm biên dịch — Java 17+

## 1. Mục tiêu bài học

Trong Java, **biểu thức** (*expression*) là một cấu trúc cú pháp được đánh giá theo các quy tắc của ngôn ngữ. Với biểu thức tạo giá trị, kết quả có **một giá trị** và compiler xác định được **kiểu của biểu thức** trước khi chương trình chạy.

~~~java
int a = 7;
int b = 3;

int sum = a + b;       // Biểu thức a + b có kiểu int, giá trị 10
boolean larger = a > b; // Biểu thức a > b có kiểu boolean, giá trị true
String label = "Java"; // Literal chuỗi có kiểu String, giá trị "Java"
~~~

Ba điểm cần phân biệt:

1. **Kiểu tĩnh (*compile-time type*)**: compiler xác định từ toán hạng, toán tử, khai báo và ngữ cảnh.
2. **Giá trị lúc chạy (*runtime value*)**: kết quả cụ thể khi biểu thức được đánh giá, tùy dữ liệu hiện thời.
3. **Đối tượng thực tế (*runtime object*)**: với biểu thức tham chiếu không null, object mà giá trị tham chiếu trỏ tới có thể thuộc class con của kiểu tĩnh.

Ví dụ `a + b` luôn có kiểu `int` trong các khai báo ở trên; nếu `a` và `b` thay đổi, **giá trị** có thể đổi nhưng **kiểu biểu thức không đổi**.

> Ngoại lệ cần nắm: có lời gọi method kiểu `void` được coi là biểu thức nhưng **không tạo ra một giá trị để dùng tiếp**. Một biểu thức có thể không hoàn tất bình thường nếu ném exception. Vì thế phát biểu “mọi biểu thức đều luôn trả về một giá trị” là quá tuyệt đối.

## 2. Biểu thức khác câu lệnh (*statement*) như thế nào?

Biểu thức tạo giá trị có thể là:

~~~java
42               // Literal int
2 + 3            // Phép cộng int
"Hi".length()    // Method invocation trả int
a > b            // Phép so sánh trả boolean
new StringBuilder("A") // Tạo object, cho giá trị tham chiếu
~~~

Những dòng này là **mảnh biểu thức**, không nhất thiết là những câu lệnh Java độc lập hợp lệ nếu chép nguyên cả dòng vào method.

Câu lệnh (*statement*) diễn đạt một hành động trong luồng thực thi: khai báo biến, gán, rẽ nhánh, vòng lặp, `return`, v.v.

~~~java
int result = 2 + 3;         // Câu lệnh khai báo; initializer là biểu thức
System.out.println(result); // Câu lệnh biểu thức: method invocation
if (result > 4) {           // Câu lệnh if; điều kiện là biểu thức boolean
    System.out.println("ok");
}
~~~

**Không phải mọi biểu thức đều đứng một mình thành câu lệnh được.**

~~~java
// 2 + 3; // Sai: phép cộng đơn thuần không phải statement expression hợp lệ
~~~

Một số nhóm biểu thức **được phép dùng như câu lệnh** (*statement expressions*) là phép gán, tăng/giảm, một số lời gọi method và biểu thức tạo class instance:

~~~java
result = 10;             // Assignment expression dùng làm câu lệnh
result++;                // Increment expression dùng làm câu lệnh
System.out.println(result); // Method invocation dùng làm câu lệnh
new StringBuilder("A");   // Class instance creation expression làm câu lệnh
~~~

**Ghi nhớ:** câu lệnh và biểu thức không phải hai khái niệm đồng nghĩa. Một câu lệnh có thể chứa nhiều biểu thức con.

## 3. Kiểu biểu thức được xác định như thế nào?

Compiler áp dụng các quy tắc kiểu Java, chẳng hạn:

| Biểu thức | Kiểu tĩnh | Ví dụ giá trị |
|---|---|---|
| `10` | `int` | `10` |
| `10L` | `long` | `10` |
| `2.5` | `double` | `2.5` |
| `2.5f` | `float` | `2.5` |
| `'A'` | `char` | UTF-16 code unit `'A'` |
| `true` | `boolean` | `true` |
| `"Java"` | `String` | Văn bản `Java` |
| `a + b` với a,b `int` | `int` | Ví dụ `10` |
| `a > b` với a,b `int` | `boolean` | Ví dụ `true` |
| `"Hi".length()` | `int` | `2` |
| `new int[3]` | `int[]` | Tham chiếu mảng mới |
| `new StringBuilder()` | `StringBuilder` | Tham chiếu object mới |

Không đoán kiểu từ tên biến hoặc hình thức giá trị in ra. Hãy xem **kiểu khai báo toán hạng, loại literal, chữ ký method** và **ngữ cảnh chuyển kiểu**.

Sơ đồ suy luận:

~~~mermaid
flowchart TD
    A["Biểu thức Java"] --> B["Xác định kiểu toán hạng / chữ ký / literal"]
    B --> C["Áp dụng toán tử và quy tắc chuyển kiểu"]
    C --> D["Kiểu tĩnh tại compile time"]
    D --> E["Compiler kiểm tra ngữ cảnh sử dụng"]
    E --> F["Khi chạy: đánh giá và tạo giá trị nếu hoàn tất bình thường"]
~~~

Sơ đồ mô tả quá trình **logic về kiểu và đánh giá**, không phải sơ đồ nội bộ bắt buộc của JVM.

## 4. Biểu thức số học: kiểu không nhất thiết bằng kiểu biến nhận

~~~java
int a = 7;
int b = 2;

int whole = a / b;       // Biểu thức int / int: kiểu int, giá trị 3
double alsoWhole = a / b; // Vẫn tính int: giá trị 3, sau đó đổi thành 3.0
double fractional = (double) a / b; // double / double: giá trị 3.5

System.out.println(whole);      // 3
System.out.println(alsoWhole);  // 3.0
System.out.println(fractional); // 3.5
~~~

**Tại sao `alsoWhole` không thành 3.5?** Phép chia `a / b` được thực hiện theo kiểu của **biểu thức** (`int`); chỉ sau đó kết quả `3` mới widening sang `double` để gán.

Phần `(double) a / b` khiến phép chia được tiến hành theo `double` ngay từ đầu, từ đó giữ phần lẻ.

**Không dùng kiểu biến đích để suy ngược phép tính đã xảy ra**. Với `var`, kiểu cũng được suy ra từ initializer:

~~~java
var result = a / b; // result có kiểu int
~~~

Không có chuyện `var` làm biểu thức tự chọn kiểu rộng hơn.

## 5. Numeric promotion: byte, short, char thường nâng thành int

Một phép cộng bình thường giữa hai biến `byte` tạo biểu thức kiểu `int`:

~~~java
byte left = 100;
byte right = 20;

int sum = left + right; // Kết quả biểu thức là int
System.out.println(sum); // 120

// byte invalid = left + right; // Lỗi compile: int không tự thu hẹp về byte
~~~

Điểm quan trọng:

- Cả hai biến là `byte`, nhưng quy tắc **binary numeric promotion** nâng lên `int` trước khi cộng.
- Compiler không tự suy ra “vì kết quả 120 vừa byte nên kiểu biểu thức sẽ là byte”.
- Muốn chuyển về byte cần cast phù hợp **và** tự kiểm tra giới hạn dữ liệu nếu không cho phép tràn.

Với `char`:

~~~java
char letter = 'A';
int nextCodeUnit = letter + 1; // Kết quả là int, giá trị 66
char nextLetter = (char) nextCodeUnit;
System.out.println(nextLetter); // B
~~~

Với một toán hạng `double`, biểu thức số học hỗn hợp thường được nâng tới `double`:

~~~java
double mixed = 7 + 0.5;
System.out.println(mixed); // 7.5
~~~

Không có widening/narrowing primitive số học từ hoặc sang `boolean`.

## 6. Kiểu tại compile time không bảo đảm tránh overflow

~~~java
long wrong = 2_000_000_000 + 2_000_000_000;
long right = 2_000_000_000L + 2_000_000_000;

System.out.println(wrong); // -294967296
System.out.println(right); // 4000000000
~~~

Ở dòng đầu:

1. Hai literal đều kiểu `int`.
2. Biểu thức `int + int` có kiểu `int` và **tràn** trong phép cộng.
3. Sau đó giá trị `int` bị tràn mới đổi sang `long` để gán.

Dòng thứ hai có toán hạng đầu kiểu `long`, nên phép cộng có kiểu `long` từ đầu.

**Compiler biết kiểu biểu thức nhưng không tự chứng minh biểu thức số học đáp ứng miền nghiệp vụ.** Nếu cần phát hiện tràn int, có thể dùng `Math.addExact(int, int)` khi phù hợp: method ném `ArithmeticException` nếu tổng vượt miền int.

## 7. So sánh và logic: kiểu boolean

Toán tử so sánh số trả `boolean`:

~~~java
int a = 7;
int b = 3;
boolean greater = a > b;
boolean equal = a == b;

System.out.println(greater); // true
System.out.println(equal);   // false
~~~

Toán tử logic `&&`, `||` cho kết quả kiểu `boolean`, đồng thời **short-circuit** khi kết quả đã xác định:

~~~java
int divisor = 0;
boolean safe = divisor != 0 && 10 / divisor > 1;

System.out.println(safe); // false, không chia cho 0
~~~

Nếu toán hạng đầu `divisor != 0` là false, Java **không đánh giá** phần `10 / divisor > 1`. Điều này cho thấy **có kiểu tĩnh** không đồng nghĩa mọi biểu thức con được thực thi trong tất cả các lượt chạy.

**Cú pháp `if` yêu cầu biểu thức điều kiện kiểu `boolean`** (hoặc `Boolean` được unbox thích hợp), không chấp nhận số nguyên như điều kiện:

~~~java
// if (1) { ... } // Lỗi compile: int không phải boolean
if (a > b) {
    System.out.println("a lớn hơn b");
}
~~~

## 8. Nối chuỗi cũng là biểu thức có kiểu String

Toán tử `+` vừa có thể cộng số vừa có thể nối String. Ngữ nghĩa phụ thuộc kiểu toán hạng và thứ tự đánh giá.

~~~java
int a = 7;
int b = 3;

System.out.println("result=" + a + b);   // result=73
System.out.println("result=" + (a + b)); // result=10
~~~

`+` kết hợp trái:

- `"result=" + a + b` bắt đầu từ String → nối String từng bước.
- `"result=" + (a + b)` thực hiện phép cộng số trong ngoặc trước, rồi nối kết quả 10.

Bảng đối chiếu:

| Biểu thức | Kiểu tĩnh | Giá trị |
|---|---|---|
| `7 + 3` | `int` | `10` |
| `"7" + 3` | `String` | `"73"` |
| `"result=" + 7 + 3` | `String` | `"result=73"` |
| `"result=" + (7 + 3)` | `String` | `"result=10"` |

Java **không tự phân tích chuỗi số** để biến `"7" + "3"` thành phép cộng 10. Muốn biến văn bản thành số phải sử dụng API như `Integer.parseInt` và xử lý dữ liệu sai định dạng.

## 9. Lời gọi method: kiểu biểu thức theo chữ ký trả về

~~~java
static int twice(int value) {
    return value * 2;
}

// Trong main:
int answer = twice(6);
System.out.println(answer); // 12
~~~

Biểu thức `twice(6)` có kiểu **`int`**, do method `twice` khai báo trả `int`. Compiler không cần chạy method để biết kiểu ấy.

Tương tự:

~~~java
var length = "Java".length(); // Suy ra int
var message = String.valueOf(123); // Suy ra String
~~~

Nếu method khai báo trả kiểu `Object`, thì **kiểu tĩnh của biểu thức gọi method là `Object`**, dù object trả về lúc chạy có thể là `String`:

~~~java
static Object makeText() {
    return "Java";
}

// Trong main:
Object value = makeText(); // Kiểu biểu thức makeText() là Object
System.out.println(value.toString()); // Java
// value.length(); // Lỗi compile: Object không khai báo length()
~~~

**Không dùng giá trị thực tế lúc chạy để đoán quyền gọi method tại compile time.** Kiểu tham chiếu tĩnh là hợp đồng mà compiler sử dụng.

## 10. Kiểu tĩnh của tham chiếu và class thật ở runtime

~~~java
class Animal {
    String sound() {
        return "animal";
    }
}

class Dog extends Animal {
    @Override
    String sound() {
        return "woof";
    }

    void fetch() {}
}
~~~

Trong một method:

~~~java
Animal pet = new Dog();

System.out.println(pet.sound()); // woof
// pet.fetch(); // Lỗi compile: kiểu tĩnh Animal không có fetch()
~~~

Giải thích:

- Biểu thức `new Dog()` có kiểu tĩnh `Dog` và tạo tham chiếu tới một instance `Dog`.
- Biến `pet` có kiểu khai báo **`Animal`**, nhận tham chiếu ấy qua widening reference conversion.
- Khi viết `pet.sound()`, compiler cho phép gọi vì `Animal` khai báo `sound()`; lúc chạy, **dynamic dispatch** chọn implementation override trong `Dog`.
- `pet.fetch()` không biên dịch vì kiểu tĩnh `Animal` không có method `fetch()`, dù object thật là Dog.

**Kiểu tĩnh** kiểm soát thành viên có thể truy cập; **class runtime** quyết định implementation của method instance được override.

## 11. Overload chọn theo kiểu tĩnh ở compile time

~~~java
static void describe(Object value) {
    System.out.println("Object overload");
}

static void describe(String value) {
    System.out.println("String overload");
}
~~~

Với:

~~~java
Object value = "Java";
String text = "Java";

describe(value); // Object overload
describe(text);  // String overload
~~~

Hai biến có thể trỏ **đến cùng một String object**, nhưng compiler chọn overload dựa trên **kiểu tĩnh của đối số**:

- `value` có kiểu `Object`: chọn `describe(Object)`.
- `text` có kiểu `String`: chọn `describe(String)`.

**Đừng nhầm overload resolution với override dispatch:**

| Cơ chế | Quyết định chính | Thời điểm |
|---|---|---|
| **Overload**: nhiều method cùng tên, tham số khác | Chữ ký áp dụng theo kiểu tĩnh đối số | Compile time |
| **Override**: class con định nghĩa lại method instance | Implementation theo object runtime | Runtime |

Kiểu của một biểu thức có thể được compiler quyết định sớm, nhưng điều đó không có nghĩa Java bỏ qua tính đa hình khi thực thi.

## 12. Biểu thức gán cũng có kiểu và giá trị

Phép gán `=` là một biểu thức: nó cập nhật biến và cho giá trị thuộc kiểu của biến bên trái.

~~~java
int count = 1;
int result = (count = 4) + 2;
System.out.println(count);  // 4
System.out.println(result); // 6
~~~

Tương tự, `++count` và `count++` có giá trị biểu thức lần lượt sau/trước khi tăng biến. Đây là các ví dụ cho thấy **kiểu tĩnh và giá trị** không mô tả hết hành vi lúc chạy: biểu thức còn có thể đổi trạng thái. Khi thao tác cập nhật không cần nằm trong phép tính, hãy tách ra thành câu lệnh riêng.

## 13. Biểu thức điều kiện `?:` có quy tắc xác định kiểu

~~~java
boolean enabled = true;
double selected = enabled ? 1 : 2.5;
System.out.println(selected); // 1.0
~~~

Nhánh được chọn quyết định **giá trị runtime**, còn trường hợp biểu thức điều kiện số học `int`/`double` này có kiểu `double` theo quy tắc numeric conditional expression. Không suy ra kiểu từ nhánh được chạy. Với biểu thức điều kiện tham chiếu, Java còn có các quy tắc target typing/poly expression; không có quy tắc chung “luôn chọn kiểu rộng nhất”.

## 14. Ép kiểu và phép chuyển đổi sau khi biết kiểu biểu thức

~~~java
double reading = 7.9;

int truncated = (int) reading; // Cast double -> int
System.out.println(truncated); // 7
~~~

Biểu thức `(int) reading` có kiểu `int`, khác với `reading` có kiểu `double`. Cast yêu cầu chuyển đổi hợp lệ; với giá trị hữu hạn trong miền, chuyển từ double sang int **cắt phần lẻ theo hướng về 0**, không làm tròn gần nhất.

Một tình huống khác:

~~~java
int value = 130;
byte small = (byte) value;
System.out.println(small); // -126
~~~

Kết quả `(byte) value` có kiểu `byte` nhưng không giữ nguyên giá trị 130, vì thu hẹp số nguyên có thể làm mất dữ liệu.

**Phân biệt ba thứ:**

- **Type checking:** compiler xác định và kiểm tra loại giá trị có thể sử dụng.
- **Conversion:** Java có thể biến đổi biểu diễn/giá trị để phù hợp kiểu đích.
- **Business validation:** chương trình phải tự kiểm tra kết quả có phù hợp yêu cầu hay không.

`(int) reading` đúng cú pháp không bảo đảm thao tác này đúng với số tiền, số lượng hoặc phép đo đòi hỏi phần lẻ.

## 15. `var` quan sát được kiểu compile time từ initializer

~~~java
var count = 10;           // int
var title = "Java";       // String
var average = 10 / 4;     // int: giá trị 2
var preciseAverage = 10.0 / 4; // double: giá trị 2.5
~~~

`var` **không tạo biến kiểu động**: nó suy luận kiểu tĩnh của biểu thức khởi tạo.

~~~java
var count = 10;
// count = "ten"; // Lỗi biên dịch: count đã có kiểu int
~~~

Một điểm nên nhớ:

~~~java
// var unknown = null; // Không hợp lệ: null literal không cung cấp kiểu suy luận
String knownType = null;  // Hợp lệ: kiểu được khai báo tường minh
~~~

Java có khái niệm kiểu của `null` literal và các ngữ cảnh target typing; nhưng bản thân `var unknown = null` không có đủ thông tin cho một kiểu biến cục bộ có thể suy luận hợp lệ.

Với generic:

~~~java
var names = java.util.List.of("Lan", "An"); // List<String>
System.out.println(names.get(0).length()); // 3
// names.add(123); // Lỗi compile: Integer không phải String
~~~

Không nhầm **kiểm tra kiểu tĩnh** với **khả năng thay đổi collection tại runtime**. `List.of` tạo danh sách không sửa được; thử thêm phần tử hợp kiểu String vẫn có thể ném `UnsupportedOperationException` lúc chạy.

## 16. Khi có kiểu nhưng không có giá trị dùng được

Lời gọi method khai báo `void` có thể hoàn tất bình thường nhưng không cho giá trị để gán:

~~~java
System.out.println("Hello"); // Hợp lệ như một statement expression
// int n = System.out.println("Hello"); // Sai kiểu: lời gọi trả void
~~~

Một biểu thức kiểu `int` cũng có thể **không trả được kết quả runtime** nếu ném ngoại lệ, chẳng hạn `10 / denominator` với `denominator == 0`. Kiểu được compiler kiểm tra, còn thành công hoặc thất bại xảy ra khi đánh giá; những side effect trước lỗi không tự động được hoàn tác.

## 17. Ví dụ Java 17 đầy đủ, chạy được

Tạo file chính xác **`ExpressionValueTypeDemo.java`**:

~~~java
public final class ExpressionValueTypeDemo {
    private ExpressionValueTypeDemo() {}

    static class Animal {
        String sound() {
            return "animal";
        }
    }

    static final class Dog extends Animal {
        @Override
        String sound() {
            return "woof";
        }

        void fetch() {
            System.out.println("fetch");
        }
    }

    static int twice(int value) {
        return value * 2;
    }

    static void describe(Object value) {
        System.out.println("overload=Object");
    }

    static void describe(String value) {
        System.out.println("overload=String");
    }

    public static void main(String[] args) {
        int a = 7;
        int b = 3;
        int sum = a + b;
        boolean greater = a > b;
        System.out.println("sum=" + sum);
        System.out.println("greater=" + greater);

        int whole = a / 2;
        double widenedAfter = a / 2;
        double fractional = (double) a / 2;
        System.out.println("int division=" + whole);
        System.out.println("widen after division=" + widenedAfter);
        System.out.println("double division=" + fractional);

        byte small = 10;
        int promoted = small + 1;
        System.out.println("promoted byte expression=" + promoted);
        System.out.println("mixed expression=" + (a + 0.5));

        System.out.println("concat=" + a + b);
        System.out.println("grouped=" + (a + b));

        int assigned = 1;
        int assignmentResult = (assigned = 4) + 2;
        System.out.println("assignment target=" + assigned);
        System.out.println("assignment result=" + assignmentResult);
        int before = ++assigned;
        int after = assigned++;
        System.out.println("prefix value=" + before);
        System.out.println("postfix value=" + after);
        System.out.println("after increment=" + assigned);

        boolean chooseFirst = true;
        double selected = chooseFirst ? 1 : 2.5;
        System.out.println("conditional selected=" + selected);

        int methodResult = twice(6);
        System.out.println("method expression=" + methodResult);

        Object object = "Java";
        String text = "Java";
        describe(object);
        describe(text);
        System.out.println("object toString=" + object.toString());
        System.out.println("text length=" + text.length());

        Animal pet = new Dog();
        System.out.println("virtual sound=" + pet.sound());

        int[] numbers = {2, 4};
        System.out.println("array element=" + numbers[1]);

        var inferred = "Hi".length();
        System.out.println("var inferred int=" + inferred);

        Integer boxed = 10;
        int unboxedSum = boxed + 2;
        System.out.println("wrapper arithmetic=" + unboxedSum);

        int divisor = 0;
        boolean guarded = divisor != 0 && 10 / divisor > 1;
        System.out.println("short circuit=" + guarded);

        long overflowFirst = 2_000_000_000 + 2_000_000_000;
        long longFirst = 2_000_000_000L + 2_000_000_000;
        System.out.println("overflow first=" + overflowFirst);
        System.out.println("long first=" + longFirst);

        System.out.println("void call completed");
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên. Mở terminal trong thư mục chứa `ExpressionValueTypeDemo.java`. Không cần Maven, Gradle hoặc thư viện ngoài JDK.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 ExpressionValueTypeDemo.java
java -cp . ExpressionValueTypeDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\ExpressionValueTypeDemo.java
java -cp . ExpressionValueTypeDemo
~~~

**Exact expected output:**

~~~text
sum=10
greater=true
int division=3
widen after division=3.0
double division=3.5
promoted byte expression=11
mixed expression=7.5
concat=73
grouped=10
assignment target=4
assignment result=6
prefix value=5
postfix value=5
after increment=6
conditional selected=1.0
method expression=12
overload=Object
overload=String
object toString=Java
text length=4
virtual sound=woof
array element=4
var inferred int=2
wrapper arithmetic=12
short circuit=false
overflow first=-294967296
long first=4000000000
void call completed
~~~

### Kiểm tra từng nhóm kết quả

- `sum`, `greater`: biểu thức số học tạo `int`, biểu thức so sánh tạo `boolean`.
- Ba dòng phép chia: cùng các giá trị liên quan nhưng **kiểu biểu thức tại lúc chia** quyết định giữ hay bỏ phần lẻ.
- `small + 1`: toán hạng `byte` được nâng thành `int`.
- `concat=73` so với `grouped=10`: kiểu toán hạng String đổi ý nghĩa phép `+`.
- `(assigned = 4) + 2`: phép gán là expression có giá trị; prefix/postfix có thời điểm cập nhật khác nhau.
- `conditional selected=1.0`: biểu thức `?:` số học có kiểu `double`.
- `describe(object)` và `describe(text)`: overload chọn theo **kiểu tĩnh**.
- `pet.sound()`: method override thực thi theo **object Dog** tại runtime.
- `var inferred int=2`: `var` suy ra kiểu `int` từ return type `length()`.
- `short circuit=false`: nhánh chia cho 0 không bị đánh giá.
- Hai dòng overflow: toán tử cộng có kiểu `int` hoặc `long` trước khi gán vào biến `long`.

## 18. Lỗi thường gặp và cách sửa

| Ngộ nhận / lỗi | Nguyên nhân | Cách đúng |
|---|---|---|
| `2 + 3;` là câu lệnh hợp lệ độc lập | Không phải mọi expression là statement expression | Gán, sử dụng kết quả hoặc đặt trong ngữ cảnh phù hợp |
| `double avg = 7 / 2;` cho 3.5 | Phép chia `int / int` trả `int` | `(double) 7 / 2` |
| `byte result = byteA + byteB;` | Numeric promotion trả `int` | Dùng `int` hoặc cast có kiểm tra |
| `long total = intA + intB;` luôn tránh tràn | int cộng trước khi gán | Widen toán hạng trước phép cộng |
| `"sum=" + 7 + 3` cho `sum=10` | Nối String từ trái sang phải | `"sum=" + (7 + 3)` |
| `Object x = "Java"; x.length()` | Kiểu tĩnh Object không có length | Chọn String ở khai báo hoặc cast có kiểm tra |
| `var n = 1;` tự đổi thành long khi cần | `var` suy ra int cố định | Khởi tạo bằng `1L` hoặc khai báo long |
| `Integer i = null; int x = i + 1;` | Unboxing null ở runtime | Kiểm tra null trước |
| `if (10)` hợp lệ vì số khác 0 | Java yêu cầu boolean | Viết điều kiện như `10 != 0` |
| `int n = System.out.println("x");` | Method có return type void, không trả giá trị | Gọi làm câu lệnh riêng |
| `n = 0; 10 / n` được compiler chặn | Kiểu hợp lệ nhưng giá trị runtime sai | Kiểm tra mẫu số khác 0 |
| Nghĩ class runtime quyết định overload | Overload phân giải theo kiểu tĩnh | Đọc kiểu khai báo biểu thức đối số |
| Nghĩ kiểu tĩnh ngăn method override | Method instance có dispatch động | Tách overload và override |

**Mẹo debug:** khi một biểu thức cho kết quả không như dự đoán, hãy phân tích theo thứ tự **(1) kiểu từng toán hạng → (2) quy tắc chuyển kiểu/toán tử → (3) kiểu biểu thức → (4) giá trị lúc chạy → (5) chuyển đổi khi gán**.

## 19. Bài tập có đáp án

### Bài A — phép chia và kiểu biểu thức

~~~java
int a = 9;
int b = 4;
double x = a / b;
double y = (double) a / b;
System.out.println(x);
System.out.println(y);
~~~

**Đáp án:**

~~~text
2.0
2.25
~~~

Phép `a / b` có kiểu `int`; cast toán hạng đầu trước khi chia làm biểu thức `double`.

### Bài B — numeric promotion

~~~java
byte value = 12;
var result = value + 1;
System.out.println(result);
~~~

**Đáp án:** in `13`, biến `result` có kiểu **int**, không phải byte.

### Bài C — toán tử `+` và String

~~~java
System.out.println("value=" + 2 + 3);
System.out.println("value=" + (2 + 3));
~~~

**Đáp án:**

~~~text
value=23
value=5
~~~

### Bài D — kiểu tĩnh và overload

~~~java
static void show(Object value) { System.out.println("Object"); }
static void show(String value) { System.out.println("String"); }

// Trong main:
Object a = "X";
String b = "X";
show(a);
show(b);
~~~

**Đáp án:**

~~~text
Object
String
~~~

### Bài E — biểu thức gán có giá trị

~~~java
int n = 1;
int x = (n = 5) * 2;
System.out.println(n);
System.out.println(x);
~~~

**Đáp án:**

~~~text
5
10
~~~

### Bài F — khi expression có kiểu nhưng không có giá trị dùng được

Tại sao dòng này lỗi?

~~~java
// int n = System.out.println("Hello");
~~~

**Đáp án:** `println` có kiểu trả về `void`; lời gọi có thể dùng làm statement expression nhưng **không tạo giá trị `int` để gán**.

## 20. Checklist cuối bài

- [ ] Phân biệt biểu thức và câu lệnh Java.
- [ ] Biết kiểu của biểu thức được compiler xác định từ toán hạng, toán tử và chữ ký API.
- [ ] Hiểu giá trị runtime có thể thay đổi dù kiểu tĩnh vẫn cố định.
- [ ] Nhận biết literal `int`, `long`, `double`, `float`, `char`, `boolean`, `String`.
- [ ] Không nhầm kiểu biến nhận với kiểu của phép tính trung gian.
- [ ] Hiểu numeric promotion và integer division.
- [ ] Biết biểu thức so sánh/logic có kiểu boolean; `&&`/`||` short-circuit.
- [ ] Phân biệt phép cộng số và phép nối String.
- [ ] Biết method invocation có kiểu theo chữ ký trả về; `void` không cung cấp giá trị gán được.
- [ ] Phân biệt static type của tham chiếu với runtime class của object.
- [ ] Hiểu overload compile-time và override runtime.
- [ ] Biết phép gán, `++`, `--` có kết quả expression và tác dụng phụ.
- [ ] Hiểu biểu thức điều kiện `?:` có kiểu theo các quy tắc ngôn ngữ.
- [ ] Biết `var` suy luận kiểu tĩnh từ initializer.
- [ ] Nhớ compiler chặn sai kiểu nhưng không đảm bảo không có exception/overflow.
- [ ] Biên dịch và chạy `ExpressionValueTypeDemo.java`, đối chiếu từng dòng exact expected output.

**Kết luận:** Biểu thức Java không chỉ “làm ra một kết quả”: Java gán cho chúng **kiểu tĩnh tại compile time**, từ đó kiểm tra toán tử, phép gán, lời gọi method và truy cập thành viên. Giá trị thực tế được tính khi chạy và có thể phụ thuộc dữ liệu, nhánh đánh giá hoặc object runtime. Hiểu hai lớp **kiểu tĩnh** và **giá trị runtime** là nền tảng để đọc, dự đoán và sửa lỗi biểu thức Java 17+.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="expressions-002">Toán hạng, toán tử và biểu thức lồng nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Toán hạng, toán tử và biểu thức lồng nhau — Java 17+

Một **biểu thức** (*expression*) trong Java là cấu trúc có thể được đánh giá để tạo ra một **giá trị** hoặc biến (trong một số trường hợp) thực hiện phép gán hay gây tác dụng phụ. Để đọc đúng biểu thức phức tạp, cần phân biệt ba khái niệm:

- **Toán hạng** (*operand*): phần dữ liệu mà toán tử tác động lên, ví dụ literal `5`, biến `price`, lời gọi `getPrice()` hoặc **một biểu thức con**.
- **Toán tử** (*operator*): ký hiệu hoặc cấu trúc thực hiện phép toán, ví dụ `+`, `*`, `!`, `&&`, `?:`, `=`.
- **Biểu thức lồng nhau** (*nested expression*): một biểu thức được dùng làm toán hạng trong biểu thức lớn hơn, có thể được nhóm bằng dấu ngoặc tròn để thể hiện rõ ý.

~~~java
int total = 8 + 3 * 2;
~~~

Trong biểu thức `8 + 3 * 2`, `8`, `3`, `2` là toán hạng đơn giản; `+` và `*` là toán tử. Do `*` có độ ưu tiên cao hơn `+`, biểu thức được **nhóm** như `8 + (3 * 2)` và có giá trị **14**.

**Điểm then chốt:** *độ ưu tiên quyết định cách nhóm toán hạng*, còn *thứ tự đánh giá* quyết định khi nào các phần được chạy. Đây là hai khái niệm khác nhau trong Java.

## 1. Các dạng toán hạng và toán tử

| Dạng | Biểu thức minh họa | Diễn giải |
| --- | --- | --- |
| Literal | `42`, `true`, `"Java"` | Giá trị viết trực tiếp |
| Biến | `quantity` | Đọc giá trị biến |
| Lời gọi phương thức | `name.length()` | Kết quả của phương thức có kiểu trả về phù hợp |
| Truy cập phần tử | `scores[0]` | Giá trị ở vị trí mảng |
| Toán tử một ngôi | `-x`, `!enabled`, `++n` | Một toán hạng |
| Toán tử hai ngôi | `a + b`, `a * b`, `x > y` | Hai toán hạng |
| Toán tử điều kiện ba ngôi | `condition ? a : b` | Ba phần: điều kiện và hai nhánh |
| Gán | `x = 10`, `x += 2` | Có giá trị biểu thức và tác dụng gán |
| Ép kiểu | `(double) sum` | Chuyển kiểu biểu thức |
| Lồng nhau | `(a + b) * (c - d)` | Biểu thức con làm toán hạng |

Một **literal** như `7` đã là biểu thức đơn giản; không phải mọi biểu thức đều cần một toán tử. Ngược lại, một **câu lệnh** (*statement*) như `if (...) { ... }` là cấu trúc điều khiển, không phải toán hạng mang giá trị có thể thay trực tiếp vào phép cộng.

## 2. Nhóm biểu thức và dấu ngoặc

Đọc hai biểu thức có cùng con số nhưng khác cách nhóm:

~~~java
int a = 8 + 3 * 2;     // 14.
int b = (8 + 3) * 2;   // 22.
~~~

Có thể hình dung cây biểu thức:

~~~text
8 + 3 * 2             (8 + 3) * 2

       +                     *
      / \                   / \
     8   *                 +   2
        / \               / \
       3   2             8   3
~~~

Nhóm theo cây bên trái: `3 * 2` là một toán hạng của `+`. Nhóm theo cây bên phải: `8 + 3` là một toán hạng của `*`.

Dấu ngoặc tròn có thể **thay đổi cách nhóm** hoặc chỉ làm cách nhóm sẵn có **dễ đọc hơn**:

~~~java
int a = 8 + 3 * 2;          // 14.
int b = 8 + (3 * 2);        // 14: nhom ro hon, khong doi gia tri.
int c = (8 + 3) * 2;        // 22: thay doi nhom.
~~~

Tuy vậy, dấu ngoặc **không** thay đổi quy tắc kiểu dữ liệu. Ví dụ `(5 / 2)` vẫn chia nguyên và trả `2`; muốn `2.5` phải có toán hạng số thực, chẳng hạn `5 / 2.0`.

## 3. Nhìn cây nhóm trước, theo dõi đánh giá sau

Độ ưu tiên và tính kết hợp xác định biểu thức con nào là toán hạng của toán tử lớn hơn. Chẳng hạn, `20 - 5 - 3` nhóm thành `(20 - 5) - 3` và có giá trị 12; `a = b = 7` nhóm thành `a = (b = 7)`.

Đừng nhầm cấu trúc ấy với **thứ tự gọi method**. Trong `first() + second() * third()`, phép nhân là nhánh phải của phép cộng nhưng các lời gọi (khi đều hoàn tất bình thường) chạy lần lượt `first`, `second`, `third`. Nếu là `left && right`, Java còn có thể bỏ qua `right`. Vì vậy khi đọc biểu thức lồng nhau, hãy vẽ cây **nhóm toán tử** trước rồi mới lần theo thứ tự đánh giá các toán hạng của từng nhánh.

## 4. Chương trình Java 17+ runnable

Tạo file UTF-8 tên **`NestedExpressionsDemo.java`**:

~~~java
public class NestedExpressionsDemo {
    static int mark(String label, int value) {
        System.out.println("visit=" + label);
        return value;
    }

    static boolean check(String label, boolean value) {
        System.out.println("check=" + label);
        return value;
    }

    public static void main(String[] args) {
        int x = 8;
        int y = 3;

        System.out.println("precedence=" + (x + y * 2));
        System.out.println("parentheses=" + ((x + y) * 2));
        System.out.println("nested=" + ((x + (y * 2)) - (x / y)));
        System.out.println("remainder=" + (x % y));
        System.out.println("unary=" + (-x + y));
        System.out.println("leftAssociative=" + (20 - 5 - 3));

        boolean comparison = (x + y * 2) > 10;
        System.out.println("comparison=" + comparison);
        System.out.println("logical=" + ((x > y) && (y > 0)));
        System.out.println("ternary=" + (x > y ? x - y : y - x));

        System.out.println("stringUngrouped=" + 1 + 2);
        System.out.println("stringGrouped=" + (1 + 2));
        System.out.println("integerDivision=" + ((x + y) / 4));
        System.out.println("decimalDivision=" + ((x + y) / 4.0));

        int order = mark("left", 2)
                + mark("middle", 3) * mark("right", 4);
        System.out.println("orderResult=" + order);

        boolean shortcut = check("first", true)
                || check("skipped", false);
        System.out.println("orResult=" + shortcut);

        int divisor = 0;
        boolean safe = (divisor != 0) && (x / divisor > 1);
        System.out.println("safeDivision=" + safe);

        int count = 4;
        int assigned = (count += 3) * 2;
        System.out.println("assignmentValue=" + assigned);
        System.out.println("countAfterAssignment=" + count);

        int n = 5;
        int post = n++ + 2;
        System.out.println("postResult=" + post);
        System.out.println("nAfterPost=" + n);

        int a;
        int b;
        a = b = 7;
        System.out.println("chainedAssignment=" + a + "," + b);
    }
}
~~~

Mở PowerShell (Windows) hoặc Bash (Linux/macOS) ở thư mục chứa file, dùng **JDK 17+**:

~~~bash
java -version
javac -version
javac -encoding UTF-8 NestedExpressionsDemo.java
java NestedExpressionsDemo
~~~

**Exact expected output** của lệnh `java NestedExpressionsDemo`:

~~~text
precedence=14
parentheses=22
nested=12
remainder=2
unary=-5
leftAssociative=12
comparison=true
logical=true
ternary=5
stringUngrouped=12
stringGrouped=3
integerDivision=2
decimalDivision=2.75
visit=left
visit=middle
visit=right
orderResult=14
check=first
orResult=true
safeDivision=false
assignmentValue=14
countAfterAssignment=7
postResult=7
nAfterPost=6
chainedAssignment=7,7
~~~

Hai lệnh `java -version` và `javac -version` hiển thị phiên bản của máy đang chạy, không thuộc khối stdout cố định ở trên.

### Đọc kết quả theo từng nhóm

**Nhóm số học:** `x + y * 2` bằng `8 + 6 = 14`, còn `(x + y) * 2` bằng `11 * 2 = 22`. Biểu thức lồng `(x + (y * 2)) - (x / y)` bằng `14 - 2 = 12` vì `8 / 3` là phép chia `int`.

**Nhóm so sánh và logic:** toán tử `>` tạo kết quả `boolean`; `&&` kết hợp hai giá trị boolean; `?:` chỉ đánh giá nhánh được chọn. Điều kiện `x > y` là true nên kết quả ba ngôi là `x - y = 5`.

**Nhóm chuỗi:** `"stringUngrouped=" + 1 + 2` được đánh giá qua các phép nối từ trái sang phải, cho `"stringUngrouped=12"`. `"stringGrouped=" + (1 + 2)` buộc cộng số trước, cho `"stringGrouped=3"`. Toán tử `+` có thể là phép cộng số **hoặc nối chuỗi**, tùy kiểu toán hạng.

**Nhóm thứ tự đánh giá:** dù phép nhân có độ ưu tiên cao hơn cộng, `mark("left", 2)` chạy trước, rồi `mark("middle", 3)` và `mark("right", 4)`. Các dấu `visit=...` trong output chứng minh trình tự đánh giá các toán hạng của biểu thức `a() + b() * c()`.

**Nhóm ngắn mạch:** với `||`, khi toán hạng trái đã là true, phương thức `check("skipped", false)` **không được gọi**; vì thế chỉ có `check=first`. Với `&&`, khi `divisor != 0` là false, Java không đánh giá `x / divisor > 1`, nên tránh được phép chia cho 0. Không dựa vào mẹo này nếu biến có thể bị thay đổi đồng thời mà chưa đồng bộ phù hợp.

**Nhóm tác dụng phụ:** `count += 3` vừa cập nhật `count` từ 4 thành 7, vừa có giá trị 7; sau đó nhân 2 cho `assigned = 14`. `n++` sử dụng giá trị cũ 5 trong biểu thức, sau đó biến thành 6; `post = 5 + 2 = 7`.

## 5. Phân tích biểu thức theo ba bước

Khi gặp một biểu thức dài, hãy đi theo quy trình:

1. **Xác định các toán hạng**: literal, biến, lời gọi phương thức, truy cập phần tử hoặc biểu thức lồng.
2. **Nhóm theo dấu ngoặc, độ ưu tiên và tính kết hợp**: xác định cây biểu thức và kiểu kết quả của từng phần.
3. **Theo dõi đánh giá lúc chạy**: xét thứ tự toán hạng, ngắn mạch, nhánh điều kiện được chọn và tác dụng phụ.

Ví dụ `(price + tax) * quantity >= minimum && active`:

~~~text
                     &&
                    /  \
                  >=   active
                 /  \
                *  minimum
               / \
              + quantity
             / \
          price tax
~~~

Để đúng kiểu Java, giả sử `price`, `tax`, `quantity`, `minimum` có các kiểu số tương thích và `active` là `boolean`. Biểu thức con bên trái `>=` có kiểu boolean; toàn bộ `&&` cũng có kiểu boolean. Nếu phần bên trái `&&` false thì giá trị `active` không cần được đánh giá.

Sơ đồ chỉ thể hiện **cách nhóm**, không phải lịch trình từng lời gọi hoặc tác dụng phụ trong biểu thức.

## 6. Kiểu của từng biểu thức con

Lồng nhau không làm mất quy tắc kiểu: mỗi nút của cây biểu thức vẫn cần toán hạng đúng kiểu. Ví dụ:

~~~java
int a = 5, b = 2;
boolean ok = (a + b) > 6 && b != 0; // true
~~~

`a + b` có kiểu `int`; phép `> 6` và `b != 0` đều có kiểu `boolean`; kết quả của `&&` cũng là `boolean`. Ngoặc quanh `(a + b)` chỉ nhóm phép tính, không làm nó thành `double`; Java không chuyển ngầm số 0 thành `false` như một số ngôn ngữ khác.

## 7. Cách nhóm không thay thế kiểm tra kiểu và giới hạn

~~~java
int price = 50_000, units = 50_000;
long overflowed = (long) (price * units); // Nhân int trước, đã tràn
long safeSize = (long) price * units;      // Nhân long, kết quả 2500000000
~~~

Cây nhóm giúp biết phép nhân nằm ở đâu, nhưng không tự mở rộng kiểu số hay kiểm tra overflow. Muốn tránh mất dữ liệu cần chuyển kiểu **trước** phép tính hoặc dùng phép kiểm tra tràn phù hợp. Với lời gọi có side effect, cân nhắc tách toán hạng thành các biến có tên để dễ kiểm tra.

## 8. Lỗi phổ biến và cách nhận diện

| Ngộ nhận | Thực tế |
| --- | --- |
| “Toán tử `*` ưu tiên cao nên lời gọi ở vế nhân luôn chạy trước.” | Độ ưu tiên quyết định **nhóm**, không đảo thứ tự đánh giá toán hạng Java |
| “`(5 / 2)` có kết quả số thực vì có ngoặc.” | Ngoặc không tự đổi `int` thành `double` |
| “`"x=" + 1 + 2` cho `x=3`.” | Nối chuỗi từ trái sang phải, cho `x=12` |
| “`(a && b)` lúc nào cũng chạy cả hai phía.” | `&&` ngắn mạch khi bên trái false |
| “`a || b` luôn gọi `b`.” | `||` bỏ qua `b` nếu `a` đã true |
| “`(n += 1)` không phải biểu thức có giá trị.” | Phép gán có giá trị và cập nhật biến |
| “`n++` và `++n` luôn cho cùng giá trị trong biểu thức.” | Postfix dùng giá trị cũ, prefix dùng giá trị mới |
| “Thêm ngoặc luôn khiến phép toán không tràn.” | Ngoặc không tự mở rộng miền số hoặc kiểm tra overflow |

## 9. Bài tập tự kiểm tra

1. **Ưu tiên:** `2 + 3 * 4` và `(2 + 3) * 4` lần lượt bằng bao nhiêu?  
   *Đáp án:* 14 và 20.
2. **Kết hợp:** `20 - 5 - 3` bằng bao nhiêu?  
   *Đáp án:* 12, nhóm `(20 - 5) - 3`.
3. **Nối chuỗi:** `"n=" + 1 + 2` khác `"n=" + (1 + 2)` thế nào?  
   *Đáp án:* `"n=12"` và `"n=3"`.
4. **Ngắn mạch:** `int d = 0; boolean ok = d != 0 && 10 / d > 1;` có chia cho 0 không?  
   *Đáp án:* Không, vế phải `&&` bị bỏ qua khi điều kiện đầu false.
5. **Tăng hậu tố:** `int n = 4; int r = n++ + 2;`, cuối cùng `r` và `n` bằng bao nhiêu?  
   *Đáp án:* `r = 6` và `n = 5`.
6. **Kiểu kết quả:** `double x = 5 / 2;` có giá trị bao nhiêu?  
   *Đáp án:* `2.0`; muốn `2.5` cần `5 / 2.0` hoặc cast phù hợp.
7. **Thứ tự đánh giá:** Trong `first() + second() * third()`, nếu các hàm đều trả số và in dấu vết, thứ tự lời gọi nào xảy ra?  
   *Đáp án:* `first()` rồi `second()` rồi `third()`.

## Checklist

- [ ] Nhận diện **toán hạng**, **toán tử**, và **biểu thức con**.
- [ ] Tách biệt **cách nhóm do precedence/associativity** với **thứ tự đánh giá toán hạng**.
- [ ] Sử dụng ngoặc để làm rõ ý, không cho rằng ngoặc tự thay đổi kiểu dữ liệu.
- [ ] Hiểu `&&`/`||`/`?:` có thể bỏ qua đánh giá một nhánh.
- [ ] Theo dõi kiểu và giá trị trung gian, nhất là phép chia nguyên và số có thể tràn.
- [ ] Cẩn thận tác dụng phụ của `=`, `+=`, `++` và lời gọi phương thức trong biểu thức.
- [ ] Khi biểu thức quá dài, cân nhắc đặt tên các bước trung gian để dễ kiểm tra.

**Kết luận:** Hãy đọc biểu thức Java bằng cách xác định **các toán hạng**, **toán tử liên kết chúng**, **cây nhóm của những biểu thức lồng nhau** và **thứ tự đánh giá thực tế**. Cách tiếp cận này giúp tránh nhầm về độ ưu tiên, sai kiểu kết quả và tác dụng phụ khó thấy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="expressions-003">Độ ưu tiên và tính kết hợp của toán tử</a>

<details>
<summary>Xem chi tiết</summary>

# Độ ưu tiên và tính kết hợp của toán tử trong Java

> **Java core — language-basics / Biểu thức**  
> **Môi trường:** JDK 17+, chỉ cần Java standard library.  
> **Mục tiêu:** Đọc đúng cách Java nhóm biểu thức dựa trên **độ ưu tiên** (*precedence*) và **tính kết hợp** (*associativity*), phân biệt chúng với **thứ tự đánh giá toán hạng** (*evaluation order*), đồng thời dùng dấu ngoặc để code rõ nghĩa.

## 1. Ba khái niệm khác nhau

Cho biểu thức:

~~~java
int result = 2 + 3 * 4;
~~~

Kết quả là **14**, không phải 20, vì `*` có độ ưu tiên cao hơn `+`: biểu thức được hiểu là `2 + (3 * 4)`.

Cần phân biệt ba quy tắc:

- **Độ ưu tiên:** Khi không có ngoặc, toán tử ở mức ưu tiên cao hơn nhóm với toán hạng của nó trước. Ví dụ `a + b * c` được nhóm như `a + (b * c)`.
- **Tính kết hợp:** Khi nhiều toán tử ở cùng mức ưu tiên, quy tắc kết hợp xác định **cách nhóm**. Ví dụ `a - b - c` nhóm từ trái: `(a - b) - c`; còn `a = b = c` nhóm từ phải: `a = (b = c)`.
- **Thứ tự đánh giá:** Java đánh giá các toán hạng từ **trái sang phải** (kèm quy tắc đặc biệt của các toán tử đánh giá có điều kiện). Điều này **không đồng nghĩa** Java luôn tính toán tử ở bên trái trước.

Ví dụ có dấu ngoặc:

~~~java
int first = 2 + 3 * 4;   // 14
int second = (2 + 3) * 4; // 20
~~~

Dấu ngoặc thay đổi **cách nhóm**, giúp người đọc không phải tự nhớ toàn bộ bảng ưu tiên.

## 2. Bảng độ ưu tiên các toán tử thường gặp

Bảng theo thứ tự **cao → thấp**. Đây là bảng học tập cho các biểu thức phổ biến trong Java 17; một số cấu trúc ngữ pháp đặc biệt (như lambda) không được xem là phép toán số học trong bảng.

| Mức | Nhóm/toán tử | Tính kết hợp hoặc ghi chú |
| --- | --- | --- |
| 1 (cao nhất) | Truy cập `.`, `[]`, lời gọi `()`, hậu tố `x++`, `x--` | Gắn với biểu thức nhận/toán hạng |
| 2 | Tiền tố `++x`, `--x`, `+x`, `-x`, `!`, `~`, cast `(Type) x` | Toán tử một ngôi, nhóm từ phải |
| 3 | `*`, `/`, `%` | Trái → phải |
| 4 | `+`, `-` | Trái → phải; `+` cũng có thể nối chuỗi |
| 5 | `<<`, `>>`, `>>>` | Trái → phải |
| 6 | `<`, `<=`, `>`, `>=`, `instanceof` | So sánh/kiểm tra kiểu |
| 7 | `==`, `!=` | Bằng/khác |
| 8 | `&` | Bitwise AND hoặc boolean AND không short-circuit |
| 9 | `^` | Bitwise XOR hoặc boolean XOR |
| 10 | `\|` | Bitwise OR hoặc boolean OR không short-circuit |
| 11 | `&&` | AND logic, short-circuit |
| 12 | `\|\|` | OR logic, short-circuit |
| 13 | `? :` | Toán tử điều kiện, nhóm từ phải |
| 14 (thấp nhất) | `=`, `+=`, `-=`, `*=`, `/=`, v.v. | Các phép gán, nhóm từ phải |

**Ví dụ nhớ nhanh:**

~~~java
boolean ok = 3 + 2 > 4 && 2 * 3 == 6;
// Tương đương về cách nhóm:
// boolean ok = ((3 + 2) > 4) && ((2 * 3) == 6);
// ok = true.
~~~

`==` có ưu tiên **cao hơn** boolean `&` và `&&`. `&&` lại có ưu tiên **cao hơn** `||`. Với biểu thức phức tạp, thêm ngoặc theo ý định nghiệp vụ thay vì dựa vào trực giác.

**Lưu ý về `<<`:** `1 + 2 << 2` nhóm thành `(1 + 2) << 2` nên kết quả **12**, chứ không phải `1 + (2 << 2)` (9).

## 3. Tính kết hợp: cùng mức thì nhóm về phía nào?

Các phép toán số học hai ngôi phổ biến như `+`, `-`, `*`, `/` nhóm **trái → phải** khi cùng mức:

~~~java
int a = 20 / 5 * 2;     // (20 / 5) * 2 = 8.
int b = 20 / (5 * 2);   // 20 / 10 = 2.
int c = 10 - 3 - 2;     // (10 - 3) - 2 = 5.
~~~

Tính kết hợp đặc biệt quan trọng khi toán tử **không có tính giao hoán hoặc kết hợp toán học phù hợp**, chẳng hạn trừ, chia, nối chuỗi và phép toán có thể tràn.

Ngược lại, **phép gán kết hợp từ phải sang trái**:

~~~java
int x, y;
x = y = 7; // x = (y = 7), cuối cùng x == 7 và y == 7.
~~~

Biểu thức điều kiện lồng nhau cũng nhóm từ phải:

~~~java
int selected = false ? 10 : true ? 20 : 30;
// Nhóm như: false ? 10 : (true ? 20 : 30)
// selected = 20.
~~~

**Cẩn thận:** Tính kết hợp quyết định **cấu trúc cú pháp**, không quyết định một lời gọi phương thức nào được chạy trước nếu các toán hạng chứa side effect. Quy tắc đánh giá của Java vẫn phải được xem riêng.

## 4. Nhóm phép toán không đảo thứ tự gọi method

Trong `left() + middle() * right()`, phép nhân được nhóm bên trong toán hạng phải của phép cộng; tuy nhiên Java đánh giá lời gọi `left()` trước `middle()` rồi `right()` nếu không có ngoại lệ. Các vế không nhất thiết đều chạy: `&&`, `||` và `?:` có quy tắc bỏ qua nhánh.

~~~java
static int mark(StringBuilder log, String name, int value) {
    log.append(name);
    return value;
}
// Trong một method:
StringBuilder log = new StringBuilder();
int result = mark(log, "A", 1) + mark(log, "B", 2) * mark(log, "C", 3);
// result = 7; log.toString() = "ABC"
~~~

Bảng ưu tiên ở trên giúp xác định **cây nhóm**; dấu vết thực thi giúp xác định **thứ tự đánh giá**. Hai kiểm tra này độc lập.

## 5. Toán tử logic: độ ưu tiên và short-circuit

`&&` ưu tiên cao hơn `||`, nên:

~~~java
boolean value = true || false && false;
// Nhóm như: true || (false && false)
// value = true.
~~~

Với `&&`, nếu vế trái false, Java **không chạy vế phải**. Với `||`, nếu vế trái true, Java **không chạy vế phải**:

~~~java
int zero = 0;
boolean noDivide = false && (10 / zero > 1); // false, không chia 0.
boolean noDivideEither = true || (10 / zero > 1); // true.
~~~

Trái lại, boolean `&` và `|` **không short-circuit**: chúng đánh giá cả hai vế. Ví dụ `false & (10 / zero > 1)` sẽ ném `ArithmeticException` do phép chia cho 0.

**Chọn toán tử theo ý định:**

| Ý định | Toán tử | Đánh giá vế phải? |
| --- | --- | --- |
| AND logic, bỏ qua vế sau khi false | `&&` | Chỉ nếu vế trái true |
| OR logic, bỏ qua vế sau khi true | `\|\|` | Chỉ nếu vế trái false |
| AND boolean bắt buộc xét cả hai | `&` | Luôn |
| OR boolean bắt buộc xét cả hai | `\|` | Luôn |

Khi kiểm tra null, thứ tự guard phải đúng:

~~~java
String name = null;
boolean valid = name != null && !name.isBlank(); // false, an toàn.
// boolean bad = !name.isBlank() && name != null; // NPE.
~~~

## 6. Một số bẫy trong biểu thức

| Biểu thức | Cách hiểu chính xác |
| --- | --- |
| `1 + 2 << 2` | `(1 + 2) << 2` = 12 |
| `10 - 3 - 2` | `(10 - 3) - 2` = 5 |
| `20 / 5 * 2` | `(20 / 5) * 2` = 8 |
| `true \|\| false && false` | `true \|\| (false && false)` = true |
| `1 \| 2 & 3` | `1 \| (2 & 3)` = 3 |
| `"sum=" + 1 + 2` | Nối chuỗi từ trái → phải = `"sum=12"` |
| `"sum=" + (1 + 2)` | Cộng số trong ngoặc trước = `"sum=3"` |
| `false ? 10 : true ? 20 : 30` | `false ? 10 : (true ? 20 : 30)` = 20 |

Dấu ngoặc vừa giúp chống lỗi vừa là tài liệu về ý đồ của lập trình viên. Tuy nhiên, đừng thêm ngoặc bừa bãi vào biểu thức nhiều side effect với giả định rằng nó sẽ đảo thứ tự đánh giá lời gọi hàm.

## 7. Chương trình Java 17+ runnable

Tạo tệp **OperatorPrecedenceDemo.java**:

~~~java
public final class OperatorPrecedenceDemo {
    private static int mark(StringBuilder order, String label, int value) {
        order.append(label);
        return value;
    }

    public static void main(String[] args) {
        System.out.println("precedence=" + (2 + 3 * 4));
        System.out.println("parentheses=" + ((2 + 3) * 4));
        System.out.println("leftAssociative=" + (20 / 5 * 2));
        System.out.println("rightGrouped=" + (20 / (5 * 2)));
        System.out.println("subtraction=" + (10 - 3 - 2));
        System.out.println("shift=" + (1 + 2 << 2));
        System.out.println("relational="
                + (3 + 2 > 4 && 2 * 3 == 6));
        System.out.println("andOr=" + (true || false && false));
        System.out.println("bitwise=" + (1 | 2 & 3));

        StringBuilder order = new StringBuilder();
        int evaluated = mark(order, "A", 1)
                + mark(order, "B", 2) * mark(order, "C", 3);
        System.out.println("operandOrder=" + order);
        System.out.println("operandValue=" + evaluated);

        int zero = 0;
        boolean shortAnd = false && (10 / zero > 1);
        boolean shortOr = true || (10 / zero > 1);
        System.out.println("shortCircuit=" + shortAnd + "," + shortOr);

        try {
            boolean evaluatedBoth = false & (10 / zero > 1);
            System.out.println("eagerResult=" + evaluatedBoth);
        } catch (ArithmeticException ex) {
            System.out.println("eagerAndThrows=true");
        }

        int x, y;
        x = y = 7;
        System.out.println("assignment=" + x + "," + y);

        int selected = false ? 10 : true ? 20 : 30;
        System.out.println("conditional=" + selected);

        System.out.println("concat=" + ("" + 1 + 2)
                + "," + (1 + 2));
    }
}
~~~

Biên dịch/chạy tại thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 OperatorPrecedenceDemo.java
java OperatorPrecedenceDemo
~~~

**Expected output chính xác:**

~~~text
precedence=14
parentheses=20
leftAssociative=8
rightGrouped=2
subtraction=5
shift=12
relational=true
andOr=true
bitwise=3
operandOrder=ABC
operandValue=7
shortCircuit=false,true
eagerAndThrows=true
assignment=7,7
conditional=20
concat=12,3
~~~

Dòng `operandOrder=ABC` minh họa **thứ tự thực thi lời gọi từ trái qua phải**, dù `*` ưu tiên `+`. `eagerAndThrows=true` chỉ ra khác biệt giữa `&` với `&&` cho toán hạng boolean. `assignment=7,7` và `conditional=20` minh họa cách **nhóm từ phải**.

## 8. Bài tập và quy tắc viết mã dễ đọc

**Bài 1.** Tính `5 + 2 * 3` và `(5 + 2) * 3`. Giải thích vì sao kết quả khác.

**Bài 2.** Tính `24 / 4 / 2` và `24 / (4 / 2)`. Toán tử `/` có tính kết hợp theo hướng nào?

**Bài 3.** Dự đoán `1 + 2 << 2`. Viết lại bằng ngoặc để không cần tra bảng ưu tiên.

**Bài 4.** Cho `int z = 0;`. Dự đoán hành vi `false && (10 / z > 0)`, `true || (10 / z > 0)` và `false & (10 / z > 0)`.

**Bài 5.** Viết `int a, b; a = b = 5;` và giải thích vì sao gán từ phải không có nghĩa mọi toán hạng nói chung đều được thực thi từ phải sang trái.

**Bài 6.** Dùng hàm ghi log hoặc in ra tên trong ba toán hạng để chứng minh `methodA() + methodB() * methodC()` gọi các method theo thứ tự A, B, C.

**Nguyên tắc:** Với công thức toán học hoặc logic dài, đặt ngoặc ở những chỗ **ý đồ nhóm biểu thức cần được thể hiện rõ**. Không trông cậy vào cách “đọc từ trái sang phải” để quyết định ưu tiên; đồng thời tránh nhầm ưu tiên/kết hợp với thứ tự chạy của lời gọi có side effect.

### Ghi nhớ

**Độ ưu tiên** quyết định cách nhóm toán tử khác mức; **tính kết hợp** quyết định cách nhóm toán tử cùng mức; **thứ tự đánh giá** của Java là quy tắc riêng, thông thường thực hiện các toán hạng từ trái sang phải. `&&`/`||` và `?:` có thể bỏ qua nhánh không cần đánh giá. Dùng dấu ngoặc để viết rõ nghĩa, đặc biệt khi trộn số học, dịch bit, so sánh, logic và phép gán.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="expressions-004">Dùng dấu ngoặc để làm rõ ý định</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng dấu ngoặc để làm rõ ý định — Java 17+

## 1. Vì sao cần dấu ngoặc trong biểu thức?

Trong Java, **dấu ngoặc tròn `(...)` dùng để phân nhóm một biểu thức con**, làm rõ thao tác nào được thực hiện chung trước khi kết hợp với các phần còn lại. Dấu ngoặc giúp người đọc nhận ra ý định của tác giả và đôi khi **thay đổi kết quả phép tính**.

~~~java
int a = 10;
int b = 3;
int c = 2;

System.out.println(a + b * c);     // 16
System.out.println(a + (b * c));   // 16
System.out.println((a + b) * c);   // 26
~~~

- `a + b * c`: phép nhân có mức ưu tiên cao hơn phép cộng.
- `a + (b * c)`: dấu ngoặc **làm rõ** đúng cách nhóm vốn có, không đổi kết quả.
- `(a + b) * c`: dấu ngoặc **thay đổi nhóm phép toán**, khiến cộng trước, nhân sau.

**Nguyên tắc:** hiểu thứ tự ưu tiên mặc định, rồi dùng dấu ngoặc để biểu đạt rõ ý định. Đừng thêm ngoặc ngẫu nhiên để chữa một kết quả sai mà chưa xác định nguyên nhân.

## 2. Dấu ngoặc nhóm biểu thức không phải “ép kiểu”

Hai cú pháp này khác nhau:

~~~java
double x = 7.9;
double grouped = (x);   // Nhóm biểu thức x: vẫn double
int converted = (int) x; // Cast: yêu cầu chuyển double sang int

System.out.println(grouped);   // 7.9
System.out.println(converted); // 7
~~~

- `(x)` là **biểu thức được đặt trong ngoặc**: kiểu và giá trị của `x` được giữ theo ngữ cảnh thông thường.
- `(int) x` là **cast expression**: `int` là tên kiểu, có tác dụng chuyển đổi và có thể làm mất phần thập phân.
- `method(a, b)`, `if (condition)`, `while (condition)` cũng dùng ngoặc theo **cú pháp lời gọi/điều khiển**, không nhất thiết là thao tác thay đổi ưu tiên.

Vì vậy cần nhìn vào **vị trí ngoặc và phần nằm trong ngoặc** trước khi giải thích.

## 3. Dùng kiến thức precedence vừa đủ để đặt ngoặc

Java ưu tiên truy cập/hậu tố và toán tử một ngôi trước nhân/chia, rồi cộng/trừ, so sánh, logic `&&`, `||`, điều kiện `?:` và phép gán. Các toán tử cùng mức còn có quy tắc kết hợp; chẳng hạn `*` và `/` nhóm từ trái sang phải:

~~~java
int x = 20, y = 5, z = 2;
System.out.println(x / y * z);   // 8: (20 / 5) * 2
System.out.println(x / (y * z)); // 2: 20 / 10
~~~

Khi công thức có nhiều nhóm điều kiện hay phép tính, đặt ngoặc theo **ý nghĩa** cần biểu đạt. Người đọc sẽ thấy rõ ý định mà không phải nhớ mọi mức ưu tiên. Dấu ngoặc thay đổi nhóm khi cần, nhưng không tự đảo thứ tự đánh giá toán hạng có tác dụng phụ.

## 4. Dùng ngoặc để tách công thức thành các ý nghĩa

Giả sử giá trị cần tính là **tổng giá và phí**, sau đó nhân số lượng:

~~~java
int unitPrice = 40;
int fee = 5;
int quantity = 3;

int total = (unitPrice + fee) * quantity;
System.out.println(total); // 135
~~~

Nếu bỏ ngoặc:

~~~java
int incorrect = unitPrice + fee * quantity;
System.out.println(incorrect); // 55
~~~

Công thức ban đầu `(40 + 5) * 3` khác `40 + (5 * 3)`. Ở đây ngoặc **thay đổi hành vi** chứ không chỉ trang trí.

Với biểu thức dài, bạn cũng có thể đặt tên cho kết quả trung gian:

~~~java
int priceWithFee = unitPrice + fee;
int explicitTotal = priceWithFee * quantity;
~~~

Cách tách biến thường phù hợp khi mỗi phép tính mang một ý nghĩa nghiệp vụ riêng; dấu ngoặc hữu ích khi biểu thức vẫn đủ ngắn.

## 5. Ngoặc không biến phép chia nguyên thành phép chia thực

Một bẫy thường gặp là phép chia nguyên:

~~~java
int a = 7;
int b = 2;

double wrong = (a / b);
double right = ((double) a) / b;

System.out.println(wrong); // 3.0
System.out.println(right); // 3.5
~~~

Ngoặc `(a / b)` **không thay đổi kiểu toán hạng**. Vì `a` và `b` đều là `int`, biểu thức chia có kiểu `int`, nhận giá trị 3; khi gán sang `double` mới thành 3.0.

Ở `((double) a) / b`, `a` được cast thành double **trước khi chia**, nên phép chia sử dụng kiểu `double` và cho 3.5.

Bảng so sánh:

| Biểu thức | Kiểu của phép chia | Giá trị cuối |
|---|---|---|
| `7 / 2` | `int` | `3` |
| `(7 / 2)` | `int` | `3` |
| `(double) (7 / 2)` | `int`, sau đó cast `double` | `3.0` |
| `((double) 7) / 2` | `double` | `3.5` |
| `7 / 2.0` | `double` | `3.5` |

**Bài học:** dùng ngoặc cho **thứ tự nhóm**, dùng cast/literal đúng kiểu cho **chọn phép toán số học**.

## 6. Nối chuỗi: dấu ngoặc phân biệt cộng số và ghép văn bản

Trong Java, `+` còn nối chuỗi khi có `String`. Phép `+` kết hợp trái:

~~~java
int a = 10;
int b = 3;

System.out.println("sum=" + a + b);    // sum=103
System.out.println("sum=" + (a + b));  // sum=13
System.out.println((a + b) + " items"); // 13 items
~~~

Ở dòng đầu, Java nhóm như `("sum=" + a) + b`: sau khi nối String với 10, phần tiếp theo lại tiếp tục nối với 3.

Ở dòng hai, `(a + b)` thực hiện phép cộng số trước. Trong log, thông báo lỗi hoặc báo cáo, dấu ngoặc này giúp tránh các giá trị **trông hợp lệ nhưng thực tế bị ghép sai**.

**Một quy ước dễ đọc:**

~~~java
String summary = "total=" + (a + b);
~~~

Nếu chuỗi đầu ra chứa nhiều phép tính, nên đặt các phép tính trong ngoặc hoặc tính chúng vào biến có tên trước khi ghép.

## 7. Điều kiện phức hợp: ngoặc biểu thị quy tắc truy cập

Giả sử một thao tác được phép khi **tài khoản đang hoạt động** và người dùng **là quản trị viên hoặc có token hợp lệ**:

~~~java
boolean isAdmin = true;
boolean hasToken = false;
boolean active = false;

boolean correct = (isAdmin || hasToken) && active;
System.out.println(correct); // false
~~~

Nếu viết:

~~~java
boolean misleading = isAdmin || hasToken && active;
System.out.println(misleading); // true
~~~

Do `&&` ưu tiên cao hơn `||`, cách viết không có ngoặc được nhóm thành `isAdmin || (hasToken && active)`: quản trị viên sẽ vượt qua điều kiện `active`.

Dấu ngoặc ở `(isAdmin || hasToken) && active` **định nghĩa chính xác ý định**: điều kiện hoạt động bắt buộc đối với cả hai cách được cấp quyền.

| isAdmin | hasToken | active | `isAdmin || (hasToken && active)` | `(isAdmin || hasToken) && active` |
|---|---|---|---|---|
| true | false | false | true | false |
| false | true | true | true | true |
| false | true | false | false | false |
| true | false | true | true | true |

**Quy tắc review:** với biểu thức pha trộn `&&` và `||`, thêm ngoặc xung quanh từng nhóm điều kiện theo ý nghĩa nghiệp vụ, kể cả khi bạn nhớ chính xác mức ưu tiên.

## 8. Dùng ngoặc để chỉ rõ điều kiện bảo vệ

~~~java
int divisor = 0;
boolean safe = (divisor != 0) && (10 / divisor > 1); // false, không chia 0
String name = null;
boolean hasName = (name != null) && (!name.isBlank()); // false, không NPE
~~~

Ngoặc cho biết vế phải phụ thuộc điều kiện guard ở vế trái; **toán tử `&&`** mới là cơ chế bỏ qua vế phải khi vế trái false. Nếu thay bằng boolean `&`, cả hai phía vẫn được đánh giá khi bình thường, dù thêm bao nhiêu ngoặc. Với `||`, vế phải bị bỏ qua khi vế trái true.

## 9. Khoảng giá trị: đừng viết so sánh dây chuyền kiểu toán học

Toán học thường viết \(1 \le x \le 10\), nhưng Java không cho diễn đạt tương đương bằng:

~~~java
// 1 <= x <= 10   // Lỗi biên dịch
~~~

Vì `1 <= x` tạo kết quả `boolean`, không thể tiếp tục so `boolean <= 10`.

Cách diễn đạt rõ ràng là:

~~~java
int x = 5;
boolean inRange = (x >= 1) && (x <= 10);

System.out.println(inRange); // true
~~~

Ở đây ngoặc tách **hai điều kiện có kiểu boolean** và `&&` kết hợp chúng. Đây là một kiểu phân nhóm làm code gần với ngôn ngữ tự nhiên hơn: “x từ 1 trở lên” **và** “x không vượt 10”.

## 10. Biểu thức `?:`: phân nhóm nhánh có chủ đích

Toán tử điều kiện ba ngôi `condition ? first : second` chọn **một trong hai nhánh** theo điều kiện boolean.

~~~java
int score = 82;
String grade = (score >= 90) ? "A"
        : ((score >= 70) ? "B" : "C");

System.out.println(grade); // B
~~~

Dấu ngoặc làm rõ cấu trúc “nếu đạt 90 thì A, nếu không nhưng đạt 70 thì B, nếu không thì C”.

Với nhiều cấp phân nhánh, một khối `if / else if / else` có thể dễ bảo trì hơn:

~~~java
String grade;
if (score >= 90) {
    grade = "A";
} else if (score >= 70) {
    grade = "B";
} else {
    grade = "C";
}
~~~

**Không cần ép mọi ý định vào một biểu thức `?:` lớn.** Chọn ngoặc, biến trung gian hoặc câu lệnh điều khiển tùy mức độ rõ ràng.

## 11. Ngoặc không loại bỏ tác dụng phụ

~~~java
int n = 1;
int value = (n++) + 2;
System.out.println(value); // 3: dùng giá trị cũ
System.out.println(n);     // 2: biến đã tăng
~~~

Dấu ngoặc cho thấy `n++` là biểu thức con nhưng không khiến nó trả giá trị sau tăng. Tương tự, `(x = 4)` vẫn thực hiện gán. Khi một công thức có nhiều cập nhật trạng thái hoặc lời gọi method có side effect, hãy tách thành bước rõ ràng để dễ debug và tránh đổi hành vi trong lúc refactor.

## 12. Lưu ý về thứ tự đánh giá trong Java

Java xác định thứ tự đánh giá toán hạng cho nhiều cấu trúc, ví dụ các toán hạng của toán tử nhị phân được đánh giá theo thứ tự **từ trái sang phải**. Nhưng dấu ngoặc phân nhóm quyết định **phép toán nào kết hợp với phép toán nào**, và có thể ảnh hưởng tới giá trị cuối hoặc cấu trúc đánh giá.

Ví dụ không có tác dụng phụ:

~~~java
int a = 2, b = 3, c = 4;
System.out.println(a + b * c);   // 14
System.out.println((a + b) * c); // 20
~~~

Đừng xem “thứ tự ưu tiên” như lời cho phép compiler **tự ý đổi thứ tự mọi lời gọi có tác dụng phụ** theo cách làm chương trình đổi nghĩa. Và cũng đừng dùng dấu ngoặc để che các đoạn như `x++ + ++x`; nên tách thành nhiều câu lệnh.

**Khuyến nghị:** một biểu thức nên ưu tiên **một ý định rõ ràng**, nhất là khi có gán, tăng/giảm, thay đổi trạng thái hay method tạo side effect.

## 13. Ngoặc không tự khắc phục tràn số nguyên

~~~java
long wrong = (2_000_000_000 + 2_000_000_000); // -294967296
long right = ((long) 2_000_000_000) + 2_000_000_000; // 4000000000
~~~

Dòng đầu cộng hai `int` nên tràn trước khi gán vào `long`. Dòng sau chuyển một toán hạng sang `long` **trước phép cộng**. Ngoặc chỉ nhóm, cast thay đổi kiểu; cần `Math.addExact` hoặc kiểm tra giới hạn nếu nghiệp vụ phải phát hiện overflow.

## 14. Ngoặc có thể thay đổi kết quả dấu phẩy động

Trong số học số thực nhị phân, một số phép toán không có tính kết hợp toán học tuyệt đối do **làm tròn sau từng bước**:

~~~java
double left = (1.0e16 + -1.0e16) + 1.0;
double right = 1.0e16 + (-1.0e16 + 1.0);

System.out.println(left);  // 1.0
System.out.println(right); // 0.0
~~~

Dòng đầu: `1.0e16 + -1.0e16` được 0.0, sau đó cộng 1.0. Dòng sau: `-1.0e16 + 1.0` được làm tròn thành `-1.0e16` vì 1 quá nhỏ so với độ lớn ấy; cộng với `1.0e16` được 0.0.

**Vì vậy không nên bỏ/di chuyển ngoặc tùy tiện trong biểu thức `float`/`double` với lý do “cộng có tính kết hợp”.** Việc đổi nhóm có thể đổi kết quả làm tròn ngay cả khi không có exception.

Nếu cần decimal chính xác, cần xem xét mô hình dữ liệu/thuật toán phù hợp như `BigDecimal`; thêm ngoặc **không tự biến `double` thành số thập phân chính xác**.

## 15. Các loại dấu ngoặc tròn cần phân biệt

| Cú pháp | Vai trò |
|---|---|
| `(a + b) * c` | Phân nhóm biểu thức, thay đổi thứ tự kết hợp |
| `a + (b * c)` | Phân nhóm để thể hiện thứ tự ưu tiên vốn có |
| `(double) a / b` | **Cast**, ảnh hưởng kiểu phép chia |
| `if (ready)` | Cú pháp điều kiện của câu lệnh `if` |
| `while (ready)` | Cú pháp điều kiện của câu lệnh `while` |
| `Math.max(a, b)` | Ngoặc bao danh sách đối số gọi method |
| `new Box(7)` | Ngoặc truyền đối số cho constructor |
| `(condition) ? x : y` | Phân nhóm điều kiện, tăng dễ đọc |

Các dấu ngoặc này có hình dạng tương tự nhưng **không có cùng ngữ nghĩa**. Khi đọc code, hãy xác định cấu trúc Java đang được sử dụng trước khi suy luận kết quả.

## 16. Chương trình Java 17 hoàn chỉnh

Tạo đúng tệp **`ParenthesesIntentDemo.java`**:

~~~java
public final class ParenthesesIntentDemo {
    private ParenthesesIntentDemo() {}

    public static void main(String[] args) {
        int a = 10;
        int b = 3;
        int c = 2;

        System.out.println("default arithmetic=" + (a + b * c));
        System.out.println("explicit precedence=" + (a + (b * c)));
        System.out.println("grouped arithmetic=" + ((a + b) * c));
        System.out.println("grouped division=" + ((a + b) / c));

        int numerator = 7;
        int denominator = 2;
        System.out.println("integer division=" + (numerator / denominator));
        System.out.println("late cast=" + ((double) (numerator / denominator)));
        System.out.println("early cast=" + (((double) numerator) / denominator));

        System.out.println("concat plain=" + ("x=" + a + b));
        System.out.println("concat grouped=" + ("x=" + (a + b)));
        System.out.println("numbers first=" + ((a + b) + " units"));

        boolean isAdmin = true;
        boolean hasToken = false;
        boolean active = false;
        boolean defaultAccess = isAdmin || hasToken && active;
        boolean groupedAccess = (isAdmin || hasToken) && active;
        System.out.println("boolean default=" + defaultAccess);
        System.out.println("boolean grouped=" + groupedAccess);

        int divisor = 0;
        boolean safeDivision = (divisor != 0) && (10 / divisor > 1);
        System.out.println("safe division=" + safeDivision);

        String name = null;
        boolean validName = (name != null) && (!name.isBlank());
        System.out.println("safe text=" + validName);

        int number = 5;
        boolean inRange = (number >= 1) && (number <= 10);
        System.out.println("range=" + inRange);

        int score = 82;
        String grade = (score >= 90) ? "A"
                : ((score >= 70) ? "B" : "C");
        System.out.println("grade=" + grade);

        int n = 1;
        int postfixValue = (n++) + 2;
        System.out.println("postfix result=" + postfixValue);
        System.out.println("postfix after=" + n);

        int updated = 0;
        int assignmentValue = (updated = 4) + 2;
        System.out.println("assignment target=" + updated);
        System.out.println("assignment result=" + assignmentValue);

        long overflowFirst = (2_000_000_000 + 2_000_000_000);
        long safeLong = ((long) 2_000_000_000) + 2_000_000_000;
        System.out.println("overflow before long=" + overflowFirst);
        System.out.println("long arithmetic=" + safeLong);

        double left = (1.0e16 + -1.0e16) + 1.0;
        double right = 1.0e16 + (-1.0e16 + 1.0);
        System.out.println("floating left=" + left);
        System.out.println("floating right=" + right);
    }
}
~~~

**Yêu cầu:** JDK 17 hoặc mới hơn. Mở terminal ở thư mục chứa `ParenthesesIntentDemo.java`; không cần Maven, Gradle hoặc thư viện bên ngoài JDK.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 ParenthesesIntentDemo.java
java -cp . ParenthesesIntentDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\ParenthesesIntentDemo.java
java -cp . ParenthesesIntentDemo
~~~

**Exact expected output:**

~~~text
default arithmetic=16
explicit precedence=16
grouped arithmetic=26
grouped division=6
integer division=3
late cast=3.0
early cast=3.5
concat plain=x=103
concat grouped=x=13
numbers first=13 units
boolean default=true
boolean grouped=false
safe division=false
safe text=false
range=true
grade=B
postfix result=3
postfix after=2
assignment target=4
assignment result=6
overflow before long=-294967296
long arithmetic=4000000000
floating left=1.0
floating right=0.0
~~~

**Giải thích các dòng quan trọng:**

- `default arithmetic` và `explicit precedence` đều 16 vì phép nhân vốn ưu tiên cao hơn cộng.
- `grouped arithmetic` ra 26 vì nhóm phép cộng trước; `grouped division` ra 6 vì `(10+3)/2` là chia nguyên.
- `late cast` vẫn 3.0 vì phép chia int đã xảy ra; `early cast` ra 3.5 khi chuyển toán hạng trước.
- `concat plain` ra `x=103`, nhưng `concat grouped` ra `x=13` vì phép cộng số được nhóm.
- `boolean default=true` có thể là logic sai nếu mọi truy cập đều cần `active`; `boolean grouped=false` thể hiện đúng điều kiện bắt buộc đó.
- `safe division` và `safe text` không ném exception nhờ short-circuit.
- `postfix result` là 3 vì `n++` đóng góp giá trị cũ 1; `assignment target` và `assignment result` minh họa phép gán có giá trị.
- `overflow before long` chứng minh ngoặc không chữa overflow; `long arithmetic` dùng cast trước phép cộng.
- Hai dòng `floating` cho thấy đổi cách nhóm trong floating-point có thể đổi kết quả làm tròn.

## 17. Khi nào nên thêm ngoặc?

Nên thêm ngoặc khi:

- Pha trộn **`&&` và `||`** để minh họa quy tắc nghiệp vụ.
- Ghép **String với một phép tính** cần cộng/trừ/nhân/chia trước.
- Một công thức có **nhiều nhóm có ý nghĩa riêng**, ví dụ `(unitPrice + fee) * quantity`.
- Sử dụng **`?:` lồng nhau** và vẫn muốn giữ biểu thức ngắn, dễ kiểm tra.
- Muốn giữ rõ cách nhóm của các phép toán floating-point nhạy với làm tròn.
- Code review cho thấy người đọc dễ nhầm mức ưu tiên hoặc tính kết hợp.

Không nên thêm ngoặc tới mức che mất cấu trúc logic. Nếu biểu thức vẫn quá dài, **tách biến trung gian có tên tốt** hoặc dùng `if/else` để trình bày rõ ràng.

### Checklist review nhanh

1. Đọc từng biểu thức con và xác định kiểu dữ liệu.
2. Xác định ưu tiên và tính kết hợp mặc định.
3. Đặt ngoặc quanh các nhóm mang **ý nghĩa nghiệp vụ**.
4. Kiểm tra ngoặc có **thay đổi hành vi** hay chỉ giúp dễ đọc.
5. Kiểm tra `int` division/overflow, String concatenation, `&&`/`||`, tác dụng phụ và nullable.
6. Chạy chương trình với giá trị ranh giới và so expected output.

## 18. Các lỗi và ngộ nhận phổ biến

| Cách viết hoặc giả định | Vấn đề | Hướng xử lý |
|---|---|---|
| `a + b * c` khi muốn cộng trước | Nhân có ưu tiên cao hơn | `(a + b) * c` |
| `double x = (7 / 2);` khi muốn 3.5 | Ngoặc không đổi kiểu int của phép chia | `((double) 7) / 2` |
| `"sum=" + a + b` khi muốn cộng số | String concatenation kết hợp trái | `"sum=" + (a + b)` |
| `admin || token && active` khi muốn active bắt buộc | `&&` được nhóm trước `||` | `(admin || token) && active` |
| `(x >= 1) & (x <= 10)` để short-circuit | `&` trên boolean đánh giá cả hai bên | Dùng `&&` khi cần short-circuit |
| `1 <= x <= 10` | So sánh đầu tạo boolean, không so được với int | `(x >= 1) && (x <= 10)` |
| `(int)(7.9)` được nghĩ là chỉ nhóm | `(int)` là cast | Phân biệt cast và grouping |
| `long x = (intA + intB)` để tránh tràn | Cộng int có thể tràn trước | Chuyển một toán hạng sang long trước |
| Đổi nhóm `double` vì nghĩ cộng luôn kết hợp | Làm tròn sau từng bước có thể khác | Giữ grouping có chủ đích, kiểm thử |
| `(x++)` được nghĩ trả giá trị sau tăng | Postfix cho giá trị cũ | Dùng biến trung gian hoặc `++x` đúng ý |
| Ngoặc giúp tránh `NullPointerException` | Chỉ ngoặc không làm phép truy cập an toàn | Dùng guard và short-circuit |
| Lồng nhiều `?:` mà không làm rõ nhánh | Khó đọc, dễ hiểu nhầm | Ngoặc rõ hoặc chuyển sang `if/else` |

**Đừng nhầm compile success với đúng nghiệp vụ.** Một biểu thức có thể đúng cú pháp và đúng kiểu nhưng tính sai do cách nhóm.

## 19. Bài tập có đáp án

### Bài A — thay đổi kết quả phép toán

~~~java
int a = 8;
int b = 4;
int c = 2;
System.out.println(a - b / c);
System.out.println((a - b) / c);
~~~

**Đáp án:**

~~~text
6
2
~~~

Dòng đầu `8 - (4 / 2)` ra 6; dòng hai `(8 - 4) / 2` ra 2.

### Bài B — phép chia và kiểu số

~~~java
int a = 5;
int b = 2;
System.out.println((double) (a / b));
System.out.println(((double) a) / b);
~~~

**Đáp án:**

~~~text
2.0
2.5
~~~

Phép cast sau chia nguyên không khôi phục phần lẻ đã mất.

### Bài C — nối String

~~~java
System.out.println("n=" + 2 + 3);
System.out.println("n=" + (2 + 3));
~~~

**Đáp án:**

~~~text
n=23
n=5
~~~

### Bài D — điều kiện nghiệp vụ

Giả sử mọi người dùng phải `active` và đồng thời (`admin` hoặc có `token`). Hãy viết biểu thức đúng.

**Đáp án:**

~~~java
boolean allowed = (admin || token) && active;
~~~

Nếu dùng `admin || token && active`, quản trị viên có thể vượt qua điều kiện `active`.

### Bài E — giới hạn giá trị

Viết điều kiện `age` nằm trong khoảng 18 đến 65 (bao gồm hai biên).

**Đáp án:**

~~~java
boolean validAge = (age >= 18) && (age <= 65);
~~~

### Bài F — dấu ngoặc có chống overflow không?

~~~java
long n = (2_000_000_000 + 2_000_000_000);
System.out.println(n);
~~~

**Đáp án:** `-294967296` vì cộng int đã tràn. Muốn tính đúng 4 tỷ:

~~~java
long n = ((long) 2_000_000_000) + 2_000_000_000;
System.out.println(n); // 4000000000
~~~

### Bài G — khi nào thêm ngoặc không đổi kết quả?

~~~java
int a = 2, b = 3, c = 4;
System.out.println(a + b * c);
System.out.println(a + (b * c));
~~~

**Đáp án:** cả hai in `14`. Ngoặc ở dòng hai chỉ trình bày rõ nhóm phép nhân theo ưu tiên vốn có.

## 20. Tổng kết

- [ ] Hiểu dấu ngoặc dùng để nhóm biểu thức và diễn đạt ý định.
- [ ] Phân biệt ngoặc **làm rõ thứ tự vốn có** với ngoặc **thay đổi cách nhóm**.
- [ ] Biết thứ tự ưu tiên và tính kết hợp của các toán tử cơ bản.
- [ ] Không nhầm ngoặc nhóm với cast hoặc cú pháp method/`if`.
- [ ] Dùng ngoặc hợp lý trong công thức, phép chia và nối String.
- [ ] Phân nhóm `&&`/`||` dựa trên quy tắc nghiệp vụ, không chỉ dựa vào nhớ precedence.
- [ ] Hiểu short-circuit là thuộc tính toán tử, không do ngoặc tự tạo.
- [ ] Biết viết `(x >= min) && (x <= max)` để kiểm tra khoảng.
- [ ] Biết khi nào tách `?:` phức tạp thành `if/else`.
- [ ] Không tin rằng ngoặc tự sửa overflow, null hoặc sai kiểu dữ liệu.
- [ ] Nhận ra đổi nhóm phép toán floating-point có thể đổi kết quả.
- [ ] Hạn chế các tác dụng phụ ẩn trong biểu thức dài.
- [ ] Chạy được `ParenthesesIntentDemo.java` bằng Java 17+ và kiểm tra exact expected output.

**Kết luận:** Dấu ngoặc giúp lập trình viên nói rõ **phần nào của biểu thức thuộc cùng một ý nghĩa**. Dùng ngoặc vừa đủ để người đọc không phải đoán precedence, đặc biệt trong điều kiện logic và phép nối String; đồng thời luôn kiểm tra kiểu toán hạng, short-circuit, overflow và tác dụng phụ vì dấu ngoặc **không tự giải quyết** những vấn đề đó.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="expressions-005">Phân biệt thứ tự nhóm biểu thức với thứ tự đánh giá</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt thứ tự nhóm biểu thức với thứ tự đánh giá trong Java

> **Java core — language-basics / Biểu thức**  
> **Yêu cầu:** Java 17+, một tệp Java độc lập; chỉ dùng thư viện chuẩn.  
> **Mục tiêu:** Phân biệt **cách nhóm biểu thức** (*grouping*) theo độ ưu tiên/tính kết hợp với **thứ tự thực thi các phép đánh giá** (*evaluation order*); tự dự đoán cả **kết quả** và **thứ tự tác dụng phụ** trong biểu thức Java.

## 1. Hai câu hỏi phải trả lời riêng

Với biểu thức:

~~~java
int result = 2 + 3 * 4;
~~~

- **Nhóm thế nào?** Toán tử `*` ưu tiên hơn `+` nên biểu thức được nhóm thành `2 + (3 * 4)`; kết quả bằng **14**.
- **Đánh giá các toán hạng theo thứ tự nào?** Java có quy tắc đánh giá riêng: với phép toán hai ngôi thông thường, **toán hạng trái được đánh giá trước toán hạng phải**. Đối với literal như ví dụ trên, không quan sát thấy khác biệt về tác dụng phụ; với lời gọi phương thức thì thấy rõ.

**Sai lầm phổ biến:** “`*` ưu tiên cao nên tất cả lời gọi phương thức quanh dấu `*` chạy trước lời gọi ở phía trái `+`”. Điều đó **không đúng**. Độ ưu tiên quyết định **cấu trúc biểu thức**, không tự đảo thứ tự gọi hàm.

## 2. Độ ưu tiên và tính kết hợp quyết định cách nhóm

| Biểu thức | Cách nhóm | Giá trị `int` |
| --- | --- | ---: |
| `2 + 3 * 4` | `2 + (3 * 4)` | 14 |
| `(2 + 3) * 4` | `(2 + 3) * 4` | 20 |
| `10 - 3 - 2` | `(10 - 3) - 2` | 5 |
| `10 - (3 - 2)` | `10 - (3 - 2)` | 9 |
| `24 / 4 / 2` | `(24 / 4) / 2` | 3 |
| `24 / (4 / 2)` | `24 / (4 / 2)` | 12 |

**Độ ưu tiên** giải thích vì sao `*` được nhóm sát toán hạng hơn `+`. **Tính kết hợp** (*associativity*) giải thích vì sao các toán tử cùng mức như `-` và `/` được nhóm từ **trái sang phải**. Các toán tử gán như `a = b = 5` nhóm từ **phải sang trái**: `a = (b = 5)`.

Dấu ngoặc thay đổi **cách nhóm**, từ đó có thể thay đổi kết quả. Nó không mặc nhiên biến mọi biểu thức thành “đánh giá toán hạng ở trong ngoặc trước mọi thứ nằm ngoài ngoặc”.

## 3. Java đánh giá các toán hạng: ví dụ ghi dấu vết

Tưởng tượng một hàm `step` ghi tên mỗi lần được gọi:

~~~java
static int step(StringBuilder log, String label, int value) {
    log.append(label);
    return value;
}
~~~

Biểu thức:

~~~java
var log = new StringBuilder();
int value = step(log, "A", 2)
        + step(log, "B", 3) * step(log, "C", 4);
~~~

được **nhóm** như:

~~~text
A + (B * C)
~~~

Nhưng các lời gọi `step` thực sự chạy theo thứ tự **A, B, C**, vì Java đánh giá toán hạng trái của phép `+` trước khi đánh giá toán hạng phải là biểu thức nhân. Khi tới vế phải, Java đánh giá B trước C. Kết quả bằng `2 + 3 * 4 = 14`.

Thay cách nhóm:

~~~java
log.setLength(0);
int changed = (step(log, "A", 2) + step(log, "B", 3))
        * step(log, "C", 4);
~~~

Thứ tự ghi dấu vẫn là **A, B, C**, còn kết quả chuyển thành **20** do dấu ngoặc làm đổi phép toán được nhóm.

**Phân biệt thật rõ:**

| Thuộc tính | `A() + B() * C()` | `(A() + B()) * C()` |
| --- | --- | --- |
| Cách nhóm | `A() + (B() * C())` | `(A() + B()) * C()` |
| Thứ tự lời gọi (đánh giá bình thường) | A → B → C | A → B → C |
| Kết quả nếu A=2, B=3, C=4 | 14 | 20 |

Quy tắc trái sang phải cũng áp dụng cho việc đánh giá các đối số phương thức: `print(computeA(), computeB())` đánh giá `computeA()` trước `computeB()`. Tuy nhiên, mỗi biểu thức con có thể chứa toán tử riêng và các nhánh đánh giá có điều kiện.

## 4. Tác dụng phụ giúp thấy thứ tự thực thi

Toán tử hậu tố `i++` **trả giá trị cũ rồi mới tăng biến**. Vì vậy:

~~~java
int i = 1;
int result = i++ + i++ * i++;
~~~

Java lấy ba toán hạng theo thứ tự trái → phải:

1. `i++` đầu trả **1**, i thành 2.
2. `i++` thứ hai trả **2**, i thành 3.
3. `i++` thứ ba trả **3**, i thành 4.

**Cách nhóm:** `1 + (2 * 3)` nên `result = 7`; sau đó `i = 4`. Không có chuyện phép nhân làm hai lần tăng ở bên phải chạy trước lần tăng ngoài cùng bên trái.

Đây là **bài tập nhận biết thứ tự**, không phải cách viết mã được khuyên dùng. Tránh gom nhiều `++` hoặc lời gọi có tác dụng phụ vào một biểu thức dài; chia thành câu lệnh riêng để dễ đọc và bảo trì.

## 5. Những toán tử đánh giá có điều kiện

Quy tắc **trái sang phải** không có nghĩa **mọi toán hạng hoặc nhánh đều được đánh giá**.

| Biểu thức | Thứ tự/nhánh thực sự đánh giá |
| --- | --- |
| `left && right` | Đánh giá left; chỉ đánh giá right nếu left là `true` |
| `left \|\| right` | Đánh giá left; chỉ đánh giá right nếu left là `false` |
| `condition ? yes : no` | Đánh giá condition, rồi **chỉ một** trong hai nhánh yes/no |
| `left & right` với boolean | Đánh giá **cả hai** vế, không short-circuit |
| `left \| right` với boolean | Đánh giá **cả hai** vế, không short-circuit |

Ví dụ:

~~~java
String name = null;
boolean valid = name != null && !name.isBlank();
~~~

Kết quả là **false**, vì `name != null` sai và vế gọi `isBlank()` bị bỏ qua. Đổi thứ tự thành `!name.isBlank() && name != null` sẽ ném `NullPointerException`.

Dùng `&&`, `||` hoặc `?:` khi cần đánh giá theo điều kiện, và đừng dùng `&`/`|` thay cho `&&`/`||` nếu yêu cầu bỏ qua vế phải.

## 6. Chương trình Java 17+ runnable

Tạo file **GroupingVsEvaluationDemo.java**:

~~~java
public final class GroupingVsEvaluationDemo {
    private static int step(StringBuilder log, String label, int value) {
        log.append(label);
        return value;
    }

    private static boolean test(
            StringBuilder log, String label, boolean value) {
        log.append(label);
        return value;
    }

    public static void main(String[] args) {
        System.out.println("precedence=" + (2 + 3 * 4));
        System.out.println("parentheses=" + ((2 + 3) * 4));
        System.out.println("subtractLeft=" + (10 - 3 - 2));
        System.out.println("subtractRight=" + (10 - (3 - 2)));

        StringBuilder log = new StringBuilder();
        int first = step(log, "A", 2)
                + step(log, "B", 3) * step(log, "C", 4);
        System.out.println("firstValue=" + first);
        System.out.println("firstOrder=" + log);

        log.setLength(0);
        int second = (step(log, "A", 2) + step(log, "B", 3))
                * step(log, "C", 4);
        System.out.println("secondValue=" + second);
        System.out.println("secondOrder=" + log);

        log.setLength(0);
        int left = step(log, "A", 24) / step(log, "B", 4)
                / step(log, "C", 2);
        System.out.println("divisionLeft=" + left + ":" + log);

        log.setLength(0);
        int right = step(log, "A", 24)
                / (step(log, "B", 4) / step(log, "C", 2));
        System.out.println("divisionRight=" + right + ":" + log);

        int i = 1;
        int increments = i++ + i++ * i++;
        System.out.println("increments=" + increments);
        System.out.println("afterIncrements=" + i);

        StringBuilder checks = new StringBuilder();
        boolean and = test(checks, "F", false)
                && test(checks, "X", true);
        boolean or = test(checks, "T", true)
                || test(checks, "Y", false);
        int selected = test(checks, "Q", false)
                ? step(checks, "L", 100)
                : step(checks, "R", 200);
        boolean eager = test(checks, "E", false)
                & test(checks, "G", true);

        System.out.println("shortAnd=" + and);
        System.out.println("shortOr=" + or);
        System.out.println("selected=" + selected);
        System.out.println("eagerAnd=" + eager);
        System.out.println("conditionalTrace=" + checks);
    }
}
~~~

Tại thư mục chứa file, chạy bằng Bash, PowerShell hoặc Command Prompt:

~~~bash
javac -encoding UTF-8 GroupingVsEvaluationDemo.java
java GroupingVsEvaluationDemo
~~~

**Expected output chính xác:**

~~~text
precedence=14
parentheses=20
subtractLeft=5
subtractRight=9
firstValue=14
firstOrder=ABC
secondValue=20
secondOrder=ABC
divisionLeft=3:ABC
divisionRight=12:ABC
increments=7
afterIncrements=4
shortAnd=false
shortOr=true
selected=200
eagerAnd=false
conditionalTrace=FTQREG
~~~

**Đối chiếu với mã:**

- Hai dòng `firstOrder`/`secondOrder` đều là `ABC`; nhưng cách nhóm khác nhau cho `firstValue=14` và `secondValue=20`.
- `divisionLeft` và `divisionRight` đều gọi A, B, C theo thứ tự đó; kết quả 3 và 12 khác vì nhóm phép chia khác.
- `increments=7`, `afterIncrements=4` xác nhận các lần `i++` được đánh giá trái → phải.
- Dấu vết `FTQREG`: F=false nên X bị bỏ qua; T=true nên Y bị bỏ qua; Q=false nên chỉ nhánh R chạy; cuối cùng cả E và G đều chạy vì boolean `&` không short-circuit.

## 7. Sai lầm thường gặp và cách đọc biểu thức

| Ngộ nhận | Hiểu đúng |
| --- | --- |
| “Nhân ưu tiên cao nên `B()`/`C()` chạy trước `A()` trong `A() + B() * C()`.” | Sai: phép nhân được **nhóm** với nhau, còn lời gọi thường chạy A → B → C |
| “Tính kết hợp trái → phải nghĩa là mọi phép tính chỉ chạy từ trái sang phải.” | Sai: kết hợp nói về **cây biểu thức**; thứ tự đánh giá có quy tắc riêng |
| “Có ngoặc thì lời gọi trong ngoặc luôn chạy đầu tiên.” | Sai: trong `A() + (B() * C())`, A vẫn được đánh giá trước B và C |
| “`&&` luôn đánh giá cả hai vế.” | Sai: vế phải có thể bị bỏ qua |
| “`&` và `&&` tương đương cho mọi boolean.” | Sai khi có lỗi hoặc side effect trong vế phải |
| “Có thể thoải mái viết nhiều `i++` trong biểu thức vì Java quy định rõ thứ tự.” | Hợp lệ nhưng khó đọc; nên tách thao tác có side effect |

**Quy trình tự kiểm tra:** (1) viết lại cách nhóm với ngoặc; (2) tìm các toán hạng có tác dụng phụ; (3) áp dụng thứ tự đánh giá và quy tắc bỏ qua nhánh; (4) tính giá trị; (5) đối chiếu trạng thái biến sau cùng.

## 8. Bài tập

**Bài 1.** Tính `3 + 4 * 5` và `(3 + 4) * 5`. Giải thích thứ tự nhóm, không dùng lập luận “toán tử nào chạy trước” để thay thế.

**Bài 2.** Một hàm `mark("X")` ghi X và trả giá trị. Dự đoán chuỗi log của `mark("A") + mark("B") * mark("C")` khi các hàm chỉ trả số nguyên, và nhóm của phép tính.

**Bài 3.** Cho `int i = 1; int result = i++ + i++ * i++;`. Dự đoán `result` và giá trị cuối của `i`; đề xuất cách tách mã tránh side effect chồng nhau.

**Bài 4.** Với `boolean a = false;` và một hàm ghi log, so sánh `a && logCall()` với `a & logCall()`. Trường hợp nào chạy `logCall()`?

**Bài 5.** Viết hai biểu thức `24 / 4 / 2` và `24 / (4 / 2)` bằng các lời gọi `step` và chứng minh log vẫn là ABC dù hai kết quả khác nhau.

### Ghi nhớ

**Nhóm biểu thức** trả lời “toán tử áp dụng lên biểu thức con nào?”, còn **thứ tự đánh giá** trả lời “toán hạng/lời gọi/side effect nào được thực hiện trước?”. Trong Java, toán hạng của phép toán thông thường được đánh giá từ trái sang phải; `&&`, `||` và `?:` có thể bỏ qua vế hoặc nhánh. Phân tích **cả hai** quy tắc khi đọc biểu thức có phép tính lẫn tác dụng phụ; dùng ngoặc và tách biến trung gian để mã dễ hiểu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="expressions-006">Đánh giá toán hạng và đối số từ trái sang phải</a>

<details>
<summary>Xem chi tiết</summary>

# Đánh giá toán hạng và đối số từ trái sang phải — Java 17+

Trong Java, **thứ tự đánh giá** (*evaluation order*) quyết định thời điểm các biểu thức con được thực thi. Đối với toán hạng của phép toán thông thường và đối số của lời gọi phương thức, Java quy định **đánh giá từ trái sang phải**. Quy tắc này đặc biệt quan trọng khi biểu thức con gọi phương thức, thay đổi biến, đọc mảng hoặc ném ngoại lệ.

~~~java
int result = first() + second() * third();
~~~

Mặc dù `*` có **độ ưu tiên** cao hơn `+`, các lời gọi được đánh giá theo thứ tự `first()`, `second()`, `third()`. Phép nhân vẫn thuộc biểu thức con bên phải của phép cộng. **Độ ưu tiên quyết định cách nhóm; thứ tự đánh giá quyết định lúc thực thi.**

> Phạm vi: các quy tắc đánh giá của biểu thức Java 17. Không suy diễn rằng **mọi toán hạng/nhánh** đều bắt buộc chạy; `&&`, `||`, `?:` và tình huống ném ngoại lệ có thể làm bỏ qua phần còn lại.

## 1. Phân biệt ba khái niệm

| Khái niệm | Câu hỏi trả lời | Ví dụ |
| --- | --- | --- |
| **Precedence** (độ ưu tiên) | Toán tử nào nhóm chặt hơn? | `a + b * c` nhóm thành `a + (b * c)` |
| **Associativity** (tính kết hợp) | Các toán tử cùng mức được nhóm thế nào? | `a - b - c` nhóm thành `(a - b) - c` |
| **Evaluation order** (thứ tự đánh giá) | Biểu thức con nào được đánh giá trước? | Trong `f() + g() * h()` gọi `f()` rồi `g()` rồi `h()` |

Với biểu thức có lời gọi phương thức:

~~~text
                       +
                      / \
                   f()   *
                        / \
                      g() h()

Cây nhóm:  f() + (g() * h())
Thứ tự đánh giá lời gọi: f() -> g() -> h()
~~~

Cây biểu thức mô tả **sự phụ thuộc về kết quả**, không phải lịch gọi các hàm theo thứ tự toán tử có độ ưu tiên cao nhất.

## 2. Quy tắc cốt lõi và ngoại lệ

- Với các **toán tử hai ngôi thông thường**, Java đánh giá toán hạng trái trước toán hạng phải. Giá trị của toán hạng trái được giữ để sử dụng sau khi đánh giá toán hạng phải, ngay cả khi vế phải làm thay đổi biến liên quan.
- Với **lời gọi phương thức**, biểu thức chọn đối tượng nhận lời gọi (nếu có) được đánh giá theo quy tắc của lời gọi, rồi các **đối số** được đánh giá từ trái sang phải; sau đó phương thức mới được gọi.
- Nếu một toán hạng hoặc đối số **hoàn thành bất thường**, ví dụ ném ngoại lệ, các phần cần đánh giá **sau đó** không tiếp tục chạy.
- Với `&&` và `||`, toán hạng phải chỉ được đánh giá khi cần để quyết định kết quả; với `?:`, chỉ một trong hai nhánh kết quả được chọn.
- Với `&` và `|` trên `boolean`, **cả hai toán hạng** được đánh giá khi không có ngoại lệ; chúng không có hành vi ngắn mạch giống `&&` và `||`.

**Đừng viết mã dựa vào việc tác dụng phụ ẩn trong biểu thức dài.** Dù thứ tự được Java quy định, tách bước thường giúp người đọc dễ hiểu hơn.

## 3. Demo Java 17+ hoàn chỉnh

Tạo tệp UTF-8 tên **`LeftToRightDemo.java`**:

~~~java
public class LeftToRightDemo {
    static int mark(String label, int value) {
        System.out.println("visit=" + label);
        return value;
    }

    static boolean check(String label, boolean value) {
        System.out.println("visit=" + label);
        return value;
    }

    static int add(int left, int right) {
        return left + right;
    }

    static int digits(int hundreds, int tens, int units) {
        return hundreds * 100 + tens * 10 + units;
    }

    static class Box {
        private final int base;

        Box(int base) {
            this.base = base;
        }

        int addToBase(int first, int second) {
            return base + first + second;
        }
    }

    static Box makeReceiver() {
        System.out.println("visit=receiver");
        return new Box(10);
    }

    static int fail() {
        System.out.println("visit=failure");
        throw new IllegalStateException("stop");
    }

    public static void main(String[] args) {
        System.out.println("== binary ==");
        int binary = mark("left", 2)
                + mark("middle", 3) * mark("right", 4);
        System.out.println("binary=" + binary);

        System.out.println("== arguments ==");
        int arguments = digits(
                mark("arg-1", 1),
                mark("arg-2", 2),
                mark("arg-3", 3));
        System.out.println("arguments=" + arguments);

        System.out.println("== nested ==");
        int nested = add(
                mark("outer", 4),
                add(mark("inner-left", 5), mark("inner-right", 6)));
        System.out.println("nested=" + nested);

        System.out.println("== receiver ==");
        int receiverTotal = makeReceiver().addToBase(
                mark("method-1", 2),
                mark("method-2", 3));
        System.out.println("receiverTotal=" + receiverTotal);

        System.out.println("== snapshot ==");
        int current = 10;
        int snapshot = current + (current = 20);
        System.out.println("snapshot=" + snapshot + ",current=" + current);

        int counter = 1;
        int captured = digits(counter++, counter++, counter++);
        System.out.println("captured=" + captured + ",counter=" + counter);

        System.out.println("== short-circuit ==");
        boolean andResult = false && check("and-skipped", true);
        System.out.println("and=" + andResult);

        boolean orResult = true || check("or-skipped", false);
        System.out.println("or=" + orResult);

        boolean eagerAnd = false & check("and-eager", true);
        System.out.println("eagerAnd=" + eagerAnd);

        int selected = true
                ? mark("selected", 7)
                : mark("not-selected", 8);
        System.out.println("conditional=" + selected);

        System.out.println("== arrays ==");
        int[] values = {10, 20, 30};
        int index = 0;
        int arraySum = values[index++] + values[index++];
        System.out.println("arraySum=" + arraySum + ",index=" + index);

        System.out.println("== strings ==");
        String concat = "" + mark("concat-1", 1) + mark("concat-2", 2);
        System.out.println("concat=" + concat);

        System.out.println("== exception ==");
        try {
            int unreachable = mark("before-failure", 1)
                    + fail() + mark("after-failure", 2);
            System.out.println("unreachable=" + unreachable);
        } catch (IllegalStateException ex) {
            System.out.println("caught=" + ex.getMessage());
        }
    }
}
~~~

Mở PowerShell (Windows) hoặc Bash (Linux/macOS) tại thư mục chứa tệp. Dùng **JDK 17 trở lên**:

~~~bash
java -version
javac -version
javac -encoding UTF-8 LeftToRightDemo.java
java LeftToRightDemo
~~~

**Exact expected output** của lệnh `java LeftToRightDemo`:

~~~text
== binary ==
visit=left
visit=middle
visit=right
binary=14
== arguments ==
visit=arg-1
visit=arg-2
visit=arg-3
arguments=123
== nested ==
visit=outer
visit=inner-left
visit=inner-right
nested=15
== receiver ==
visit=receiver
visit=method-1
visit=method-2
receiverTotal=15
== snapshot ==
snapshot=30,current=20
captured=123,counter=4
== short-circuit ==
and=false
or=true
visit=and-eager
eagerAnd=false
visit=selected
conditional=7
== arrays ==
arraySum=30,index=2
== strings ==
visit=concat-1
visit=concat-2
concat=12
== exception ==
visit=before-failure
visit=failure
caught=stop
~~~

Kết quả `java -version` và `javac -version` tùy JDK và không nằm trong khối stdout cố định trên.

## 4. Đọc demo theo nhóm

### 4.1. Toán hạng: thứ tự gọi khác thứ tự nhóm

~~~java
int binary = mark("left", 2)
        + mark("middle", 3) * mark("right", 4);
~~~

- Java đánh giá `mark("left", 2)` trước, ghi dấu `visit=left` và thu được giá trị 2.
- Tiếp theo, để đánh giá toán hạng phải của `+`, Java đánh giá `mark("middle", 3)` rồi `mark("right", 4)`.
- Toán tử nhân xử lý `3 * 4 = 12`; sau đó cộng `2 + 12 = 14`.

**Không suy luận rằng `mark("middle", ...)` phải chạy trước `mark("left", ...)` chỉ vì `*` ưu tiên cao hơn `+`.**

### 4.2. Đối số của phương thức: trái sang phải

~~~java
int value = digits(
        mark("arg-1", 1),
        mark("arg-2", 2),
        mark("arg-3", 3));
~~~

Các biểu thức đối số được đánh giá lần lượt **1 → 2 → 3**. Khi cả ba biểu thức hoàn thành bình thường, Java gọi `digits(1, 2, 3)` và trả **123**.

Đối với **lời gọi lồng nhau**:

~~~java
add(mark("outer", 4),
    add(mark("inner-left", 5), mark("inner-right", 6)));
~~~

Java đánh giá đối số thứ nhất của lời gọi ngoài trước (in `visit=outer`), rồi mới đi vào biểu thức đối số thứ hai: các đối số bên trong được đánh giá từ trái sang phải (in `inner-left`, `inner-right`). Sau đó `add(5, 6)` trả 11 và `add(4, 11)` trả **15**.

### 4.3. Biểu thức chọn đối tượng trước các đối số

~~~java
makeReceiver().addToBase(
        mark("method-1", 2),
        mark("method-2", 3));
~~~

`makeReceiver()` được đánh giá để xác định đối tượng nhận lời gọi, nên dấu `visit=receiver` xuất hiện **trước** `visit=method-1` và `visit=method-2`. Sau đó `addToBase(2, 3)` dùng `base=10` để trả **15**.

Đây là quy tắc trong ngữ cảnh gọi phương thức instance có biểu thức đối tượng nhận lời gọi. Nếu bản thân biểu thức tạo/tra cứu đối tượng thất bại, lời gọi có thể không diễn ra.

### 4.4. Giá trị đã đánh giá không bị “đọc lại” sau tác dụng phụ

~~~java
int current = 10;
int result = current + (current = 20);
~~~

Toán hạng trái `current` được đọc và giữ giá trị **10**. Sau đó, toán hạng phải gán `current = 20` và có giá trị 20. Kết quả `result = 10 + 20 = 30`, trong khi biến `current` sau biểu thức bằng **20**.

Tương tự:

~~~java
int counter = 1;
int value = digits(counter++, counter++, counter++);
~~~

Mỗi đối số đánh giá từ trái sang phải. Hậu tố `++` lấy **giá trị cũ** trước khi tăng, nên các đối số lần lượt là **1, 2, 3**, biến sau đó bằng **4**, và `digits(1, 2, 3)` trả 123.

Đây là ví dụ để **hiểu quy tắc**, không phải lời khuyên nên lồng nhiều thao tác tăng biến vào một lời gọi: mã như vậy khó rà soát và sửa lỗi.

### 4.5. Ngắn mạch và chọn nhánh

~~~java
boolean a = false && check("unused", true);
boolean b = true || check("also-unused", false);
~~~

Với `&&`, nếu toán hạng trái đã false, toán hạng phải **không được đánh giá**. Với `||`, nếu toán hạng trái đã true, toán hạng phải **không được đánh giá**.

Ngược lại:

~~~java
boolean c = false & check("executed", true);
~~~

Toán tử boolean `&` vẫn đánh giá phía phải, vì nó **không ngắn mạch**. Giá trị `c` là false nhưng phương thức vẫn được gọi.

Với `?:`:

~~~java
int value = true ? mark("selected", 7) : mark("ignored", 8);
~~~

Java đánh giá điều kiện trước, rồi **chỉ nhánh được chọn**. Vì điều kiện true, chỉ `visit=selected` xuất hiện.

Các ngoại lệ này không phủ nhận quy tắc trái-sang-phải; chúng xác định **biểu thức nào thật sự cần được đánh giá**.

### 4.6. Truy cập mảng và chuỗi

~~~java
int index = 0;
int[] values = {10, 20, 30};
int sum = values[index++] + values[index++];
~~~

Ở phép cộng, toán hạng trái được đánh giá trọn vẹn trước: lấy `values[0] = 10` và tăng `index` lên 1. Toán hạng phải sau đó lấy `values[1] = 20` và tăng `index` lên 2. Tổng bằng **30**.

Với nối chuỗi:

~~~java
String text = "" + mark("first", 1) + mark("second", 2);
~~~

Hai lời gọi vẫn được đánh giá theo thứ tự trái-sang-phải; các phép `+` sau khi bắt đầu từ `String` thực hiện nối chuỗi, nên kết quả là `"12"`, không phải số 3.

### 4.7. Ngoại lệ chặn phần đánh giá phía sau

~~~java
int result = mark("before", 1) + fail() + mark("after", 2);
~~~

Nếu `fail()` ném ngoại lệ, `mark("after", 2)` **không chạy**, và không có giá trị kết quả bình thường cho phép cộng. Khối `catch` có thể xử lý ngoại lệ, nhưng không làm những biểu thức còn lại tự động chạy tiếp.

## 5. Cách viết mã dễ kiểm tra hơn khi có tác dụng phụ

Dù Java đã xác định thứ tự đánh giá, vẫn nên **tách lệnh** trong mã nghiệp vụ để người đọc không phải mô phỏng các bước tăng biến hoặc lời gọi có tác dụng phụ:

~~~java
int first = nextNumber();
int second = nextNumber();
int third = nextNumber();
int result = digits(first, second, third);
~~~

Ví dụ này thể hiện cách tổ chức; để chạy độc lập, cần có định nghĩa phù hợp cho `nextNumber()` và `digits(...)`. Trong demo mục 3, mọi phương thức đều đã được khai báo đầy đủ.

Nên đặc biệt tách bước khi có thao tác I/O, đổi trạng thái dùng chung, tăng bộ đếm, xử lý lỗi hoặc khi thứ tự thao tác là một phần hợp đồng nghiệp vụ.

### Không nhầm với xử lý song song

Quy tắc ngôn ngữ quy định thứ tự đánh giá trong **một luồng thực thi biểu thức**. Nó **không** đảm bảo thứ tự xuất hiện của tác dụng phụ từ các luồng khác nếu chương trình dùng concurrency. Muốn phối hợp luồng cần quy tắc đồng bộ và memory model phù hợp; việc viết phép cộng không thay thế synchronization.

## 6. Những hiểu nhầm phổ biến

| Ngộ nhận | Điều đúng trong Java |
| --- | --- |
| “Toán tử `*` ưu tiên cao nên hàm trong phép nhân luôn được gọi trước.” | Precedence quy định **nhóm toán tử**, không đảo thứ tự đánh giá trái-sang-phải |
| “Đối số có thể chạy theo thứ tự bất kỳ để tối ưu.” | Java quy định **đối số lời gọi được đánh giá từ trái sang phải** |
| “Biểu thức bên trái có thể bị đọc lại sau khi vế phải gán biến.” | Giá trị toán hạng trái đã đánh giá được giữ theo ngữ nghĩa toán tử |
| “`counter++` trong ba đối số đều trả cùng số.” | Đối số lần lượt thấy giá trị đã tăng từ đối số trước |
| “`&&` và `&` đều bỏ qua vế phải khi vế trái false.” | Chỉ `&&` ngắn mạch; `&` boolean đánh giá hai vế nếu bình thường |
| “`?:` đánh giá cả hai nhánh rồi chọn kết quả.” | Chỉ nhánh được chọn được đánh giá |
| “Nếu một đối số ném exception, các đối số còn lại vẫn chạy.” | Không đánh giá tiếp các đối số phía sau |
| “Java quyết định thứ tự gọi theo cách trình biên dịch thích.” | Thứ tự quan sát được phải tuân theo ngữ nghĩa được đặc tả |

## 7. Bài tập tự kiểm tra

1. **Precedence:** Trong `a() + b() * c()`, thứ tự gọi là gì nếu cả ba chạy bình thường?  
   *Đáp án:* `a()`, `b()`, `c()`; giá trị vẫn được nhóm như `a() + (b() * c())`.
2. **Đối số:** `show(mark("A", 1), mark("B", 2))` in dấu nào trước?  
   *Đáp án:* `A` trước `B`.
3. **Tác dụng phụ:** `int n = 10; int x = n + (n = 20);` cho `x`, `n` bao nhiêu?  
   *Đáp án:* `x = 30`, `n = 20`.
4. **Tăng hậu tố:** `int n = 1; digits(n++, n++, n++);` nhận ba đối số nào và `n` sau đó là gì?  
   *Đáp án:* 1, 2, 3 và `n = 4`.
5. **Ngắn mạch:** `false && sideEffect()` có gọi `sideEffect()` không?  
   *Đáp án:* Không.
6. **Không ngắn mạch:** `false & sideEffect()` có gọi `sideEffect()` không, nếu phương thức trả `boolean`?  
   *Đáp án:* Có, nếu không có ngoại lệ.
7. **Ngoại lệ:** Nếu đối số thứ hai ném ngoại lệ, đối số thứ ba có được đánh giá không?  
   *Đáp án:* Không; lời gọi phương thức không được thực hiện theo cách bình thường.

## Checklist

- [ ] Tách biệt **độ ưu tiên/tính kết hợp** với **thứ tự đánh giá**.
- [ ] Với toán hạng và đối số thông thường, nắm quy tắc **trái sang phải**.
- [ ] Khi gọi phương thức instance, xem xét biểu thức receiver trước danh sách đối số.
- [ ] Hiểu giá trị đã đánh giá được giữ khi vế sau có tác dụng phụ.
- [ ] Nhận diện ngoại lệ ngắn mạch `&&`, `||` và chọn nhánh `?:`.
- [ ] Hiểu exception có thể ngăn phần biểu thức phía sau được đánh giá.
- [ ] Ưu tiên tách các biểu thức có nhiều tác dụng phụ thành các lệnh rõ ràng.

**Kết luận:** Java quy định rõ thứ tự đánh giá toán hạng và đối số **từ trái sang phải** trong các ngữ cảnh tương ứng. Hãy phân biệt điều đó với **cách nhóm phép toán**, và nhớ rằng ngắn mạch, chọn nhánh và ngoại lệ có thể làm bỏ qua các biểu thức không cần hoặc không thể tiếp tục.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="expressions-007">Tác dụng phụ trong biểu thức và nguy cơ khó dự đoán</a>

<details>
<summary>Xem chi tiết</summary>

# Tác dụng phụ trong biểu thức và nguy cơ khó dự đoán — Java 17+

## 1. Mục tiêu bài học

Trong Java, một biểu thức (*expression*) có thể vừa **tính ra giá trị**, vừa thực hiện **tác dụng phụ** (*side effect*) — làm thay đổi trạng thái có thể quan sát được, chẳng hạn gán biến, tăng bộ đếm, sửa field, thay phần tử mảng, ghi log hoặc thực hiện I/O.

~~~java
int count = 5;
int old = count++; // Giá trị biểu thức là 5, nhưng count đổi thành 6

System.out.println(old);   // 5
System.out.println(count); // 6
~~~

Phần **giá trị** của `count++` là 5; phần **tác dụng phụ** là cập nhật `count` từ 5 thành 6. Đây là hai khía cạnh khác nhau của cùng một biểu thức.

**Điểm quan trọng:** Java 17 có quy tắc **thứ tự đánh giá được xác định**, không phải ngẫu nhiên. Tuy nhiên, khi một biểu thức chứa nhiều lần đọc và sửa cùng trạng thái, người đọc vẫn rất dễ dự đoán sai. Vì vậy, bài học hướng đến **hiểu đúng Java và viết code dễ kiểm chứng**, thay vì tìm cách nhồi mọi thao tác vào một dòng.

## 2. Nhận diện những biểu thức có tác dụng phụ

| Biểu thức / lời gọi | Giá trị biểu thức | Tác dụng phụ có thể xảy ra |
|---|---|---|
| `a + b` với hai `int` | Tổng `int` | Thông thường không sửa `a`, `b` |
| `x = 10` | Giá trị sau phép gán | Gán lại `x` |
| `x++` | Giá trị **trước** khi tăng | Tăng `x` |
| `++x` | Giá trị **sau** khi tăng | Tăng `x` |
| `x += 3` | Giá trị của `x` sau cập nhật | Cập nhật `x` |
| `array[index++]` | Phần tử mảng tại chỉ số cũ | Tăng `index` |
| `object.change()` | Tùy kiểu trả về method | Method có thể sửa object hoặc trạng thái khác |
| `System.out.println("hi")` | `void`, không có giá trị dùng để gán | Ghi ra output |
| `new SomeClass()` | Tham chiếu tới instance mới nếu thành công | Tạo object; constructor có thể có hành vi quan sát được |

**Không được suy luận method “chỉ đọc” hay “có sửa” dựa duy nhất vào tên.** Với lời gọi `service.getValue()`, cần xem hợp đồng và implementation của API: một getter có thể ghi log, cập nhật cache hoặc ném ngoại lệ.

Phép toán thuần như `a + b` không tự sửa toán hạng; nhưng **biểu thức toán hạng** có thể có tác dụng phụ:

~~~java
int result = next() + current(); // next() hoặc current() có thể thay đổi trạng thái
~~~

Muốn hiểu đúng phải xem cả **quá trình đánh giá toán hạng**, không chỉ toán tử `+`.

## 3. Tiền tố `++x`, hậu tố `x++`: cập nhật giống, giá trị khác

~~~java
int x = 3;

int before = x++; // before = 3, x thành 4
int after = ++x;  // x thành 5, after = 5

System.out.println(before); // 3
System.out.println(after);  // 5
System.out.println(x);      // 5
~~~

| Cú pháp | Giá trị dùng trong biểu thức | Trạng thái sau |
|---|---|---|
| `x++` | Giá trị cũ | `x` tăng 1 |
| `++x` | Giá trị mới | `x` tăng 1 |
| `x--` | Giá trị cũ | `x` giảm 1 |
| `--x` | Giá trị mới | `x` giảm 1 |

Trong vòng lặp đơn giản, `i++` thường dễ hiểu:

~~~java
for (int i = 0; i < 3; i++) {
    System.out.println(i);
}
~~~

Nhưng trong phép tính lớn, phải ghi nhớ **giá trị trước/sau khi cập nhật** và **thời điểm biểu thức khác đọc biến**. Tách câu lệnh giúp tránh nhầm lẫn.

## 4. Java không “không xác định”, nhưng biểu thức phức tạp rất khó đọc

Ví dụ dưới đây **hợp lệ và có kết quả xác định theo Java**:

~~~java
int i = 1;
int sum = i++ + ++i;

System.out.println(sum); // 4
System.out.println(i);   // 3
~~~

Giải thích chính xác từ trái sang phải:

1. `i++` cho **giá trị cũ 1**, rồi cập nhật `i = 2`.
2. `++i` cập nhật `i = 3`, rồi cho **giá trị mới 3**.
3. `1 + 3` bằng **4**, và giá trị cuối của `i` là **3**.

Không gọi trường hợp này là “undefined behavior” theo nghĩa của một số ngôn ngữ khác: **Java quy định thứ tự đánh giá**. Vấn đề nằm ở khả năng đọc code, bảo trì và rủi ro sửa nhầm khi refactor.

Viết tường minh hơn:

~~~java
int i = 1;
int left = i;
i++;
i++;
int right = i;
int sum = left + right;
~~~

Đoạn tách bước thể hiện rõ mục đích và cho cùng kết quả trong trường hợp này. Nếu logic nghiệp vụ không cần hai lần cập nhật, cách tốt hơn là **đơn giản hóa thuật toán**, không chỉ thêm biến.

### Bẫy `i = i++`

~~~java
int i = 5;
i = i++;
System.out.println(i); // 5
~~~

Vì sao vẫn 5? Vế phải `i++` cho giá trị cũ **5** và tăng `i` lên 6; cuối cùng **phép gán bên ngoài** gán lại giá trị cũ 5 cho `i`.

Hãy viết `i++;` nếu chỉ muốn tăng, hoặc `i = desiredValue;` nếu chỉ muốn gán. **Đừng kết hợp hai cơ chế gán lên cùng biến khi không cần thiết.**

## 5. Side effect tuân theo thứ tự đánh giá, không theo mức ưu tiên

~~~java
static int trace(String label, int value) {
    System.out.println("evaluate " + label);
    return value;
}
// Trong một method:
int result = trace("A", 2) + trace("B", 3) * trace("C", 4);
~~~

Ba dòng `evaluate A`, `evaluate B`, `evaluate C` xuất hiện theo thứ tự ấy; `result == 14`. Phép nhân được **nhóm** trước phép cộng khi tính giá trị, nhưng không khiến lời gọi B/C thực thi trước A.

Đặc biệt khi các method thay đổi trạng thái, nên đưa kết quả vào biến riêng theo đúng thứ tự mong muốn, sau đó thực hiện phép tính. Cách này giảm rủi ro vô tình đổi side effect khi sửa code.

## 6. Đối số method được đánh giá trước khi method bắt đầu

Khi gọi:

~~~java
show(trace("first", 1), trace("second", 2));
~~~

Java đánh giá đối số **từ trái sang phải** trước khi thực thi phần thân `show`. Trong ví dụ này, `trace("first", 1)` chạy trước `trace("second", 2)`, sau đó mới tới `show`.

Điều này có thể tạo bug nếu hai đối số cùng sửa một object:

~~~java
send(counter.incrementAndGet(), counter.get());
~~~

Nếu `incrementAndGet()` tăng trạng thái, đối số thứ hai có thể đọc **giá trị sau khi tăng**, không phải trạng thái ban đầu. Không nên giả định hai đối số được tính “cùng một lúc”.

Cách rõ hơn:

~~~java
int newValue = counter.incrementAndGet();
int snapshotAfter = counter.get();
send(newValue, snapshotAfter);
~~~

**Lưu ý:** compiler chọn overload theo kiểu tĩnh của biểu thức đối số, còn tác dụng phụ khi **đánh giá** đối số xảy ra lúc chạy. Hai điều này tách biệt.

## 7. Short-circuit với `&&` và `||`

Toán tử **`&&`** bỏ qua vế phải nếu vế trái đã `false`; **`||`** bỏ qua vế phải nếu vế trái đã `true`.

~~~java
int divisor = 0;
boolean safe = (divisor != 0) && (10 / divisor > 2);
System.out.println(safe); // false, không chia cho 0
~~~

Nếu một method có tác dụng phụ nằm trong nhánh bị bỏ qua, tác dụng phụ **không xảy ra**:

~~~java
boolean result = false && updateCounter(); // updateCounter() không được gọi
~~~

Mô hình:

~~~mermaid
flowchart TD
    A["Đánh giá vế trái của &&"] --> B{"Có true không?"}
    B -- "Không" --> C["Kết quả false, bỏ qua vế phải"]
    B -- "Có" --> D["Đánh giá vế phải"]
    E["Đánh giá vế trái của ||"] --> F{"Có true không?"}
    F -- "Có" --> G["Kết quả true, bỏ qua vế phải"]
    F -- "Không" --> H["Đánh giá vế phải"]
~~~

**Phân biệt `&&` với `&`, `||` với `|` khi toán hạng là boolean:**

| Toán tử | Có bỏ qua vế phải? | Lưu ý |
|---|---|---|
| `&&` | Có, khi vế trái false | Thường dùng để bảo vệ thao tác cần điều kiện |
| `||` | Có, khi vế trái true | Thường dùng để đặt lựa chọn thay thế |
| `&` | Không | Đánh giá cả hai vế |
| `|` | Không | Đánh giá cả hai vế |

Dùng `&` thay `&&` có thể **vô tình gọi thêm method**, sửa dữ liệu hoặc gây ngoại lệ. Dùng toán tử đúng và kiểm thử cả hai trường hợp true/false.

## 8. Toán tử điều kiện `?:` chỉ đánh giá nhánh được chọn

~~~java
boolean useCache = true;
int answer = useCache ? readCache() : calculateFresh();
~~~

Compiler kiểm tra kiểu của hai nhánh theo quy tắc conditional expression, nhưng khi chạy Java chỉ đánh giá **nhánh được chọn**. Nếu `useCache` true, `calculateFresh()` không được gọi.

Vì vậy, nếu một nhánh gửi yêu cầu mạng, ghi log hoặc thay đổi bộ đếm, **số lần gọi có thể khác** giữa các lượt chạy. Điều này hoàn toàn theo ngữ nghĩa Java, không phải tính ngẫu nhiên.

Nếu hai nhánh mang nhiều logic tác động trạng thái, dùng `if/else` thường dễ quan sát:

~~~java
int answer;
if (useCache) {
    answer = readCache();
} else {
    answer = calculateFresh();
}
~~~

**Không đặt tác dụng phụ vào cả hai nhánh rồi mong cả hai đều được thực hiện.**

## 9. Gán biến, sửa object và alias

Side effect không chỉ là sửa một biến primitive:

~~~java
final class Counter {
    int value;

    Counter(int value) {
        this.value = value;
    }

    int incrementAndGet() {
        return ++value;
    }
}

// Trong method:
Counter original = new Counter(10);
Counter alias = original;

int current = alias.incrementAndGet();
System.out.println(current);        // 11
System.out.println(original.value); // 11
~~~

`original` và `alias` cùng tham chiếu đến **một Counter**. Một method trên `alias` sửa field trên object, nên bên dùng `original` cũng thấy giá trị mới.

Một lời gọi method có thể trông giống biểu thức lấy giá trị nhưng lại sửa trạng thái được chia sẻ. Với code có nhiều object, hãy kiểm tra:

- Method chỉ đọc hay thay đổi field/collection?
- Có nhiều biến cùng tham chiếu tới cùng object không?
- Có được phép gọi method này nhiều lần không?
- Có cần đồng bộ hoặc bảo vệ khi nhiều thread cùng truy cập không?

**Phép tính có kiểu hợp lệ** không đồng nghĩa không có thay đổi trạng thái.

## 10. Chỉ số mảng có `i++`: đọc ở đâu, cập nhật khi nào?

~~~java
int[] values = {10, 20, 30};
int i = 0;

int sum = values[i++] + values[i];

System.out.println(sum); // 30
System.out.println(i);   // 1
~~~

Java đánh giá `values[i++]` trước:

1. `i++` cung cấp chỉ số cũ **0**, rồi đổi `i` thành **1**.
2. Đọc `values[0]` bằng **10**.
3. Đánh giá `values[i]` bên phải với `i = 1`: lấy **20**.
4. Tổng là **30**.

Cách dễ đọc hơn:

~~~java
int first = values[i];
i++;
int second = values[i];
int sum = first + second;
~~~

Trường hợp dùng `values[i++] = nextValue` trong thuật toán duyệt mảng có thể phù hợp khi đơn giản, nhưng **tránh kết hợp nhiều lần cập nhật cùng chỉ số trong một biểu thức dài**. Nếu `i` vượt giới hạn, chương trình có thể ném `ArrayIndexOutOfBoundsException`; Java không tự đoán ý định dịch chỉ số.

## 11. Nối chuỗi và log có thể che tác dụng phụ

~~~java
int n = 1;
System.out.println("n=" + n++ + ", now=" + n);
~~~

Output:

~~~text
n=1, now=2
~~~

`n++` đóng góp giá trị cũ 1 vào chuỗi, sau đó `n` đã bằng 2 khi biểu thức tiếp theo được đánh giá.

**Không nên dùng biểu thức có tác dụng phụ chỉ để tạo thông báo log.** Cách rõ hơn:

~~~java
int old = n;
n++;
System.out.println("old=" + old + ", now=" + n);
~~~

Một biểu thức được truyền vào `logger.debug(...)` hoặc `System.out.println(...)` có thể được **đánh giá trước lời gọi**, kể cả khi code bên trong method quyết định không ghi ra đích. Muốn tối ưu chi phí đánh giá log, cần xem API logging cụ thể hỗ trợ lazy evaluation hay không.

## 12. Exception có thể dừng quá trình đánh giá

Một biểu thức có kiểu compile time hoàn toàn hợp lệ vẫn có thể **không hoàn tất bình thường** tại runtime:

~~~java
int zero = 0;
int value = (10 / zero) + expensiveOperation();
~~~

Nếu `10 / zero` ném `ArithmeticException`, Java không tiếp tục đánh giá `expensiveOperation()`. Do đó tác dụng phụ bên trong method ở vế phải **không xảy ra**.

Tương tự với:

~~~java
String text = null;
// int length = text.length() + mutate(); // text.length() ném NPE;
// mutate() không được gọi.
~~~

Đây là một lý do để tránh gói **tính toán có thể lỗi và cập nhật trạng thái** vào cùng một biểu thức dài: đường thực thi thất bại có thể để lại trạng thái được cập nhật một phần, khó rollback.

**Không hiểu nhầm:** `try/catch` xử lý exception không đồng nghĩa các side effect đã xảy ra trước exception tự động được hoàn tác. Nếu cần tính nhất quán qua nhiều thao tác, thiết kế transaction/rollback phù hợp.

## 13. Cách refactor biểu thức nhiều tác dụng phụ

**Trước — khó đọc:**

~~~java
int result = counter.incrementAndGet()
        + values[index++]
        + service.nextValue();
~~~

Đoạn này trộn ba thay đổi/quan sát trạng thái:

1. Tăng `counter`.
2. Tăng `index` khi chọn phần tử.
3. Gọi `service.nextValue()`, có thể có tác dụng phụ theo hợp đồng API.

**Sau — tách ý định, giữ thứ tự:**

~~~java
int counterValue = counter.incrementAndGet();
int selectedValue = values[index];
index++;
int serviceValue = service.nextValue();
int result = counterValue + selectedValue + serviceValue;
~~~

Lợi ích:

- Debug từng bước dễ hơn.
- Log/test có thể kiểm tra trạng thái tại ranh giới rõ ràng.
- Dễ đặt validation trước thao tác nguy hiểm.
- Code review nhìn được thứ tự hoạt động, thay vì phải tính nhẩm side effect.

**Cẩn thận:** khi refactor, đừng tự ý đổi thứ tự gọi method, đổi loại toán tử hoặc bỏ mất guard; hãy giữ đúng ngữ nghĩa cần thiết, nhất là khi method có side effect.

## 14. Java có thứ tự xác định, nhưng concurrency vẫn có vấn đề riêng

Các ví dụ trên giả sử chạy trong **một thread**. Quy tắc đánh giá từ trái sang phải không thay thế cơ chế đồng bộ giữa các thread.

~~~java
static int count = 0;

// Nếu nhiều thread cùng làm:
count++;
~~~

`count++` gồm đọc, tính và ghi; **không phải phép tăng nguyên tử an toàn cho mọi tình huống đa luồng**. Khi nhiều thread cùng ghi không đồng bộ, có thể xảy ra race condition và cập nhật bị mất.

Nếu cần bộ đếm được chia sẻ, có thể dùng `java.util.concurrent.atomic.AtomicInteger` theo yêu cầu:

~~~java
java.util.concurrent.atomic.AtomicInteger count =
        new java.util.concurrent.atomic.AtomicInteger(0);
int next = count.incrementAndGet();
~~~

**Phân biệt:** Java xác định thứ tự đánh giá trong một thread; nhưng tính đúng của trạng thái chia sẻ trong đa luồng cần tuân thủ Java Memory Model và đồng bộ phù hợp. Không dùng dấu ngoặc hay đổi `i++` thành `++i` để sửa race condition.

## 15. Chương trình Java 17 độc lập, có expected output

Lưu file đúng tên **`ExpressionSideEffectsDemo.java`**:

~~~java
public final class ExpressionSideEffectsDemo {
    private ExpressionSideEffectsDemo() {}

    static int trace(String label, int value) {
        System.out.println("evaluate " + label);
        return value;
    }

    static void show(int a, int b) {
        System.out.println("arguments=" + a + "," + b);
    }

    static boolean check(boolean result, int[] calls) {
        calls[0]++;
        return result;
    }

    static int select(int value, int[] calls) {
        calls[0]++;
        return value;
    }

    static final class Counter {
        int value;

        Counter(int value) {
            this.value = value;
        }

        int addAndGet(int amount) {
            value += amount;
            return value;
        }
    }

    public static void main(String[] args) {
        int x = 3;
        int postfix = x++;
        int prefix = ++x;
        System.out.println("postfix=" + postfix
                + ", prefix=" + prefix + ", x=" + x);

        int i = 1;
        int mixed = i++ + ++i;
        System.out.println("mixed=" + mixed + ", i=" + i);

        int confusing = 5;
        confusing = confusing++;
        System.out.println("self-assignment=" + confusing);

        int arithmetic = trace("A", 2)
                + trace("B", 3) * trace("C", 4);
        System.out.println("arithmetic=" + arithmetic);

        show(trace("first", 1), trace("second", 2));

        int[] calls = {0};
        boolean guardedAnd = check(false, calls)
                && check(true, calls);
        System.out.println("&&=" + guardedAnd + ", calls=" + calls[0]);

        boolean guardedOr = check(true, calls)
                || check(false, calls);
        System.out.println("||=" + guardedOr + ", calls=" + calls[0]);

        boolean eagerAnd = check(false, calls)
                & check(true, calls);
        System.out.println("&=" + eagerAnd + ", calls=" + calls[0]);

        int[] branchCalls = {0};
        int chosen = true
                ? select(5, branchCalls)
                : select(9, branchCalls);
        System.out.println("ternary=" + chosen
                + ", calls=" + branchCalls[0]);

        Counter counter = new Counter(10);
        Counter alias = counter;
        int changed = alias.addAndGet(5);
        System.out.println("counter=" + changed
                + ", original=" + counter.value);

        int[] values = {10, 20, 30};
        int index = 0;
        int arraySum = values[index++] + values[index];
        System.out.println("array sum=" + arraySum + ", index=" + index);

        int n = 1;
        System.out.println("string side effect=" + n++
                + ", now=" + n);

        int zero = 0;
        try {
            int unused = (10 / zero) + trace("unreached", 99);
            System.out.println(unused);
        } catch (ArithmeticException ex) {
            System.out.println("division error, right skipped");
        }
    }
}
~~~

Cài **JDK 17+**, mở terminal trong thư mục chứa file.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 ExpressionSideEffectsDemo.java
java -cp . ExpressionSideEffectsDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\ExpressionSideEffectsDemo.java
java -cp . ExpressionSideEffectsDemo
~~~

**Exact expected output:**

~~~text
postfix=3, prefix=5, x=5
mixed=4, i=3
self-assignment=5
evaluate A
evaluate B
evaluate C
arithmetic=14
evaluate first
evaluate second
arguments=1,2
&&=false, calls=1
||=true, calls=2
&=false, calls=4
ternary=5, calls=1
counter=15, original=15
array sum=30, index=1
string side effect=1, now=2
division error, right skipped
~~~

### Đối chiếu các điểm quan trọng

1. `x++` trả giá trị cũ, `++x` trả giá trị mới; cả hai cùng sửa `x`.
2. `i++ + ++i` có kết quả xác định, nhưng khó đọc hơn việc tách bước.
3. `i = i++` có thể tự ghi đè kết quả tăng và giữ giá trị cũ.
4. `trace A/B/C` chạy từ trái qua phải dù toán tử `*` có mức ưu tiên cao hơn `+`.
5. Các đối số `show` được đánh giá lần lượt trước thân method.
6. `&&` và `||` bỏ qua vế phải khi đủ kết luận; `&` không bỏ qua.
7. `?:` chỉ gọi nhánh được chọn.
8. Hai biến alias cùng thấy side effect trên object Counter.
9. `index++` sửa chỉ số trước khi vế phải đọc lại mảng.
10. Vế phải sau phép chia cho 0 không chạy; vì vậy **không có dòng `evaluate unreached`** trong output.

## 16. Lỗi thường gặp và hướng xử lý

| Code/ngộ nhận | Vấn đề | Cách sửa hoặc nguyên tắc |
|---|---|---|
| `int sum = i++ + ++i;` | Có kết quả xác định nhưng khó audit | Tách cập nhật và phép cộng |
| `i = i++;` để tăng `i` | Phép gán có thể ghi đè giá trị sau tăng | Viết `i++;` |
| Nghĩ `*` ưu tiên hơn nên toán hạng bên phải của phép cộng luôn gọi trước | Nhầm precedence với evaluation order | Phân biệt cách nhóm và thứ tự đánh giá |
| Gọi method có side effect trong nhiều đối số | Trạng thái đối số sau phụ thuộc đối số trước | Tách biến tạm, gọi theo thứ tự |
| Dùng `&` thay `&&` để guard null | `&` đánh giá cả hai vế | Dùng `&&` |
| Mong hai nhánh `?:` đều có tác dụng | Chỉ một nhánh được chọn | Dùng câu lệnh riêng nếu cần hai thao tác |
| `list.get(index++)` lồng trong phép tính dài | Dễ dùng sai chỉ số, khó debug | Lưu chỉ số/phần tử tạm |
| Cho rằng getter luôn không sửa gì | Method có thể thay đổi cache/log/field | Xem contract và implementation |
| Ghép log bằng `value++` | Ghi log làm đổi trạng thái | Tách cập nhật khỏi formatting |
| Tưởng ngoại lệ rollback side effects trước đó | Java không tự rollback trạng thái | Transaction/rollback theo thiết kế |
| Dùng `count++` làm bộ đếm nhiều thread | Không bảo đảm atomicity | Synchronization hoặc `AtomicInteger` |
| Kết luận Java có thứ tự không xác định | Java có quy tắc đánh giá rõ | Hiểu thứ tự, nhưng ưu tiên code đơn giản |

## 17. Bài tập có đáp án

### Bài A — hậu tố và giá trị

~~~java
int x = 2;
int y = x++ + x++;
System.out.println(y);
System.out.println(x);
~~~

**Đáp án:**

~~~text
5
4
~~~

Lần hậu tố thứ nhất đóng góp 2 rồi `x = 3`; lần thứ hai đóng góp 3 rồi `x = 4`. Tổng bằng 5. Dù hợp lệ, cách viết này không nên là lựa chọn mặc định trong code nghiệp vụ.

### Bài B — thứ tự đánh giá đối số

~~~java
static int step(int value) {
    System.out.println("step " + value);
    return value;
}

static void show(int a, int b) {
    System.out.println(a + b);
}

// Trong main:
show(step(1), step(2));
~~~

**Đáp án:**

~~~text
step 1
step 2
3
~~~

### Bài C — short-circuit

~~~java
int[] calls = {0};
boolean result = false && check(true, calls);
System.out.println(result);
System.out.println(calls[0]);
~~~

**Đáp án:**

~~~text
false
0
~~~

`check` là hàm trong ví dụ chạy ở trên; vế phải không được gọi nên bộ đếm giữ nguyên 0.

### Bài D — mảng và index

~~~java
int[] a = {4, 9, 16};
int i = 0;
int sum = a[i++] + a[i];
System.out.println(sum);
System.out.println(i);
~~~

**Đáp án:**

~~~text
13
1
~~~

### Bài E — refactor code khó đọc

Cho biểu thức:

~~~java
int result = service.next() + counter.incrementAndGet();
~~~

Hãy viết lại để thứ tự thao tác rõ ràng.

**Một đáp án:**

~~~java
int serviceValue = service.next();
int counterValue = counter.incrementAndGet();
int result = serviceValue + counterValue;
~~~

Đây là phiên bản tách bước nhưng **giữ thứ tự gọi bên trái trước bên phải**.

### Bài F — tác dụng phụ và exception

~~~java
int denominator = 0;
int result = (10 / denominator) + trace("after", 5);
~~~

**Đáp án:** có `ArithmeticException` khi chia nguyên cho 0; `trace("after", 5)` không chạy nên không có output `evaluate after`.

## 18. Checklist và kết luận

- [ ] Phân biệt **giá trị biểu thức** và **tác dụng phụ**.
- [ ] Nhớ `i++` trả giá trị cũ, `++i` trả giá trị mới.
- [ ] Hiểu biểu thức như `i++ + ++i` có kết quả xác định nhưng khó bảo trì.
- [ ] Biết vì sao `i = i++` có thể không tăng kết quả cuối.
- [ ] Phân biệt **operator precedence** với **evaluation order**.
- [ ] Biết Java đánh giá đối số method theo thứ tự trái sang phải.
- [ ] Biết `&&`/`||` short-circuit, còn `&`/`|` boolean không short-circuit.
- [ ] Biết `?:` chỉ đánh giá nhánh được chọn.
- [ ] Nhận diện side effect khi sửa object qua alias hoặc index mảng.
- [ ] Hiểu ngoại lệ có thể dừng đánh giá và không tự rollback trạng thái đã đổi.
- [ ] Không nhúng phép cập nhật vào biểu thức log hoặc công thức dài nếu không cần thiết.
- [ ] Tách phép gọi có side effect thành các câu lệnh có tên rõ ràng.
- [ ] Phân biệt thứ tự biểu thức một thread với vấn đề race condition đa luồng.
- [ ] Chạy `ExpressionSideEffectsDemo.java` bằng `javac`/`java` Java 17+ và đối chiếu exact expected output.

**Kết luận:** Biểu thức Java có thể vừa tạo giá trị vừa sửa trạng thái. Java 17 định nghĩa thứ tự đánh giá, nhưng nhiều side effect trong cùng một biểu thức vẫn là nguồn lỗi đọc hiểu, thay đổi ngoài ý muốn và khó kiểm thử. Hãy **giữ tính toán đơn giản, tách cập nhật trạng thái thành bước rõ ràng, sử dụng short-circuit đúng mục đích và kiểm thử đường thành công lẫn đường exception**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="expressions-008">Kết thúc bình thường hoặc đột ngột do ngoại lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Kết thúc bình thường hoặc đột ngột do ngoại lệ — Java 17+

Khi Java **đánh giá một biểu thức** (*expression evaluation*), việc đánh giá có thể:

- **Kết thúc bình thường** (*completes normally*): biểu thức được đánh giá xong; nếu biểu thức có giá trị thì giá trị đó sẵn sàng để dùng trong biểu thức bao ngoài, phép gán, đối số hoặc câu lệnh.
- **Kết thúc đột ngột** (*completes abruptly*): một phần của quá trình đánh giá thất bại, ví dụ ném ngoại lệ (*exception*). Biểu thức **không hoàn tất theo đường bình thường**; các bước phụ thuộc phía sau bị bỏ qua và luồng điều khiển chuyển tới cơ chế xử lý ngoại lệ phù hợp.

~~~java
int sum = 2 + 3;    // Binh thuong: sum nhan 5.

// int bad = 10 / 0; // Khi chay: ArithmeticException.
// Khong co ket qua int binh thuong de gan cho bad.
~~~

Trong Java, một lời gọi trả `void` cũng có thể **kết thúc bình thường** dù không sinh ra giá trị để cộng/gán. Vì vậy không đồng nhất “bình thường” với “luôn trả một đối tượng hoặc một con số”.

**Trọng tâm:** khi một biểu thức con ném ngoại lệ, việc đánh giá biểu thức lớn bị dừng; những tác dụng phụ **đã diễn ra trước lỗi** không tự động được hoàn tác.

## 1. Hai kết quả của đánh giá biểu thức

| Tình huống | Hoàn tất thế nào? | Hệ quả |
| --- | --- | --- |
| `4 + 5` | Bình thường | Có giá trị `int 9` |
| `"Java".length()` | Bình thường | Có giá trị `int 4` |
| `System.out.println("Hi")` | Bình thường nếu in thành công | Lời gọi `void` không cung cấp một giá trị có thể đem cộng |
| `10 / 0` với toán hạng `int` | Đột ngột | Ném `ArithmeticException` |
| `((String) null).length()` | Đột ngột | Ném `NullPointerException` |
| `array[99]` nếu mảng quá ngắn | Đột ngột | Ném `ArrayIndexOutOfBoundsException` |
| `Integer.parseInt("abc")` | Đột ngột | Ném `NumberFormatException` |
| `false && risky()` | Bình thường với `false` | `risky()` **không được gọi** |
| `true ? 1 : riskyInt()` | Bình thường với `1` | Nhánh không được chọn **không được đánh giá** |

Một ngoại lệ được **ném** tại một điểm sẽ tìm khối `catch` phù hợp ở ngăn xếp lời gọi. Nếu không có nơi xử lý, ngoại lệ có thể làm luồng đang chạy chấm dứt. Một `catch` xử lý thành công **không làm Java quay lại tính tiếp biểu thức bị ngắt**; chương trình tiếp tục từ vị trí hợp lệ **sau cấu trúc xử lý**.

## 2. Quy tắc lan truyền từ biểu thức con

Các toán hạng và đối số thông thường được đánh giá **từ trái sang phải**. Nếu một biểu thức con ném ngoại lệ:

1. Các biểu thức con **đã hoàn tất trước đó** vẫn đã được đánh giá; những thay đổi trạng thái trước lỗi vẫn còn, trừ khi ứng dụng có cơ chế hoàn tác riêng.
2. Các biểu thức con **phía sau chưa tới lượt** sẽ không được đánh giá trong cùng phép tính hoặc lời gọi đó.
3. Toàn bộ biểu thức bao ngoài **kết thúc đột ngột**, không trả giá trị bình thường cho phép gán hoặc lời gọi bên ngoài.
4. `catch` phù hợp có thể xử lý lỗi và cho phép chương trình tiếp tục ở một câu lệnh sau `try/catch`.

Ví dụ:

~~~java
int result = left() + fail() + right();
~~~

Nếu `left()` trả giá trị bình thường rồi `fail()` ném exception, `right()` **không chạy** và phép gán cho `result` không hoàn tất. Nếu `left()` đã ghi log hoặc tăng bộ đếm thì thay đổi đó vẫn tồn tại.

Sơ đồ luồng:

~~~text
Danh gia left() ----> Danh gia fail() ----> Danh gia right()
       |                    |
    thanh cong          nem exception
                            |
                            v
                  Bo qua right() va phep gan
                            |
                            v
                    Tim catch phu hop
~~~

Sơ đồ chỉ thể hiện một nhánh thất bại minh họa; nếu `fail()` thành công, việc đánh giá sẽ đi tiếp sang `right()`.

## 3. Demo Java 17+ chạy độc lập

Tạo file UTF-8 tên **`ExpressionCompletionDemo.java`**:

~~~java
public class ExpressionCompletionDemo {
    static int mark(String label, int value) {
        System.out.println("eval=" + label);
        return value;
    }

    static int fail(String label) {
        System.out.println("eval=" + label);
        throw new IllegalStateException("boom-" + label);
    }

    static int combine(int first, int second, int third) {
        System.out.println("combine=called");
        return first + second + third;
    }

    static boolean check(String label, boolean value) {
        System.out.println("eval=" + label);
        return value;
    }

    public static void main(String[] args) {
        System.out.println("== normal ==");
        int normal = mark("left", 2) + mark("right", 3);
        System.out.println("normal=" + normal);

        System.out.println("== abrupt operand ==");
        try {
            int unused = mark("before", 1)
                    + fail("middle")
                    + mark("after", 9);
            System.out.println("unexpected=" + unused);
        } catch (IllegalStateException ex) {
            System.out.println("caught=" + ex.getMessage());
        }

        System.out.println("== abrupt argument ==");
        try {
            int unused = combine(
                    mark("arg-1", 10),
                    fail("arg-2"),
                    mark("arg-3", 30));
            System.out.println("unexpected=" + unused);
        } catch (IllegalStateException ex) {
            System.out.println("argumentCaught=" + ex.getMessage());
        }

        System.out.println("== arithmetic ==");
        try {
            int quotient = mark("numerator", 10)
                    / mark("denominator", 0);
            System.out.println("unexpected=" + quotient);
        } catch (ArithmeticException ex) {
            System.out.println("divisionError="
                    + ex.getClass().getSimpleName());
        }

        System.out.println("== references ==");
        String text = null;
        try {
            int size = text.length();
            System.out.println("unexpected=" + size);
        } catch (NullPointerException ex) {
            System.out.println("nullError="
                    + ex.getClass().getSimpleName());
        }

        System.out.println("== arrays ==");
        int[] values = {7, 8};
        try {
            int element = values[mark("bad-index", 3)];
            System.out.println("unexpected=" + element);
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.out.println("arrayError="
                    + ex.getClass().getSimpleName());
        }

        System.out.println("== skip evaluation ==");
        boolean andResult = false && check("skip-and", true);
        boolean orResult = true || check("skip-or", false);
        int selected = true ? mark("selected", 7)
                : fail("skip-branch");
        System.out.println("and=" + andResult);
        System.out.println("or=" + orResult);
        System.out.println("selected=" + selected);

        System.out.println("== prior side effect ==");
        int counter = 0;
        try {
            int unused = mark("counter-start", counter++)
                    + fail("counter-fail");
            System.out.println("unexpected=" + unused);
        } catch (IllegalStateException ex) {
            System.out.println("sideEffectCaught="
                    + ex.getMessage());
        }
        System.out.println("counterAfter=" + counter);

        System.out.println("== catch and finally ==");
        int recovered;
        try {
            recovered = mark("recover-left", 4)
                    + fail("recover-right");
        } catch (IllegalStateException ex) {
            recovered = 99;
            System.out.println("recoveredFrom=" + ex.getMessage());
        } finally {
            System.out.println("finally=cleanup");
        }
        System.out.println("recoveredValue=" + recovered);
        System.out.println("program=continued");
    }
}
~~~

Mở PowerShell (Windows) hoặc Bash (Linux/macOS) trong thư mục chứa file, dùng **JDK 17 trở lên**:

~~~bash
java -version
javac -version
javac -encoding UTF-8 ExpressionCompletionDemo.java
java ExpressionCompletionDemo
~~~

**Exact expected output** của lệnh `java ExpressionCompletionDemo` (không bao gồm đầu ra các lệnh `-version`):

~~~text
== normal ==
eval=left
eval=right
normal=5
== abrupt operand ==
eval=before
eval=middle
caught=boom-middle
== abrupt argument ==
eval=arg-1
eval=arg-2
argumentCaught=boom-arg-2
== arithmetic ==
eval=numerator
eval=denominator
divisionError=ArithmeticException
== references ==
nullError=NullPointerException
== arrays ==
eval=bad-index
arrayError=ArrayIndexOutOfBoundsException
== skip evaluation ==
eval=selected
and=false
or=true
selected=7
== prior side effect ==
eval=counter-start
eval=counter-fail
sideEffectCaught=boom-counter-fail
counterAfter=1
== catch and finally ==
eval=recover-left
eval=recover-right
recoveredFrom=boom-recover-right
finally=cleanup
recoveredValue=99
program=continued
~~~

Những dòng có tiền tố `unexpected=` **không xuất hiện** vì biểu thức tương ứng đã ném ngoại lệ trước khi hoàn tất bình thường.

## 4. Đọc kết quả của demo

### 4.1. Kết thúc bình thường

~~~java
int normal = mark("left", 2) + mark("right", 3);
~~~

`mark("left", 2)` và `mark("right", 3)` chạy theo thứ tự trái → phải, cùng trả `int` bình thường. Phép cộng nhận hai giá trị 2 và 3, hoàn tất với 5, nên phép gán cho `normal` thành công.

### 4.2. Biểu thức con ném exception: phần sau không được chạy

~~~java
mark("before", 1) + fail("middle") + mark("after", 9)
~~~

`mark("before", 1)` đã chạy. `fail("middle")` ghi dấu rồi ném `IllegalStateException`. Vì vậy:

- `mark("after", 9)` **không được gọi**.
- Phép cộng lớn **không có giá trị bình thường**.
- `catch (IllegalStateException ex)` nhận exception và in `boom-middle`.

Biểu thức trong khối `try` không được tiếp tục tại chỗ sau khi exception đã xử lý.

### 4.3. Đối số thất bại: phương thức đích chưa được gọi

~~~java
combine(mark("arg-1", 10), fail("arg-2"), mark("arg-3", 30))
~~~

Đối số đầu tiên chạy, đối số thứ hai ném exception. Đối số thứ ba **không được đánh giá**, và bản thân phương thức `combine` **không được gọi**; vì vậy sẽ không có dòng `combine=called`.

Việc phương thức nhận ba tham số không có nghĩa Java đánh giá đồng thời hoặc đánh giá tất cả bất chấp lỗi.

### 4.4. Ngoại lệ do phép toán

`int` chia cho số nguyên 0 ném `ArithmeticException`. Hai toán hạng trong:

~~~java
mark("numerator", 10) / mark("denominator", 0)
~~~

đều được đánh giá trước khi phép chia phát hiện mẫu số bằng 0. Do đó có cả hai dòng `eval=...`, nhưng không có kết quả `quotient`.

**Lưu ý khác biệt:** `double` chia cho `0.0` tuân theo quy tắc số thực, có thể tạo `Infinity` hoặc `NaN` thay vì `ArithmeticException`. Không áp dụng máy móc quy tắc chia cho 0 của số nguyên cho mọi kiểu số.

### 4.5. Null và truy cập mảng

Gọi `text.length()` khi `text == null` ném `NullPointerException`. Trong `values[mark("bad-index", 3)]`, chỉ số 3 được đánh giá xong rồi thao tác truy cập mảng ném `ArrayIndexOutOfBoundsException` vì mảng chỉ có hai phần tử, chỉ số hợp lệ là 0 và 1.

Tên lớp của ngoại lệ trong log giúp phân biệt **loại thất bại**, nhưng không phải thông báo đầy đủ của ngoại lệ nào cũng giống nhau giữa các JDK. Demo chỉ in `getClass().getSimpleName()` để stdout ổn định.

### 4.6. Phần bị bỏ qua là ngữ nghĩa bình thường, không phải exception

~~~java
boolean a = false && check("skip-and", true);
boolean b = true || check("skip-or", false);
int c = true ? mark("selected", 7) : fail("skip-branch");
~~~

Ở hai biểu thức boolean, vế trái đã đủ xác định kết quả nên không gọi vế phải. Với toán tử ba ngôi, chỉ nhánh được chọn được đánh giá. Tất cả các biểu thức này **kết thúc bình thường**, mặc dù không đánh giá một phần cú pháp.

Vì vậy, **“biểu thức con không được chạy” không đồng nghĩa “có exception”**.

### 4.7. Tác dụng phụ trước lỗi không tự được hoàn tác

~~~java
int counter = 0;
int value = mark("counter-start", counter++)
        + fail("counter-fail");
~~~

`counter++` trả giá trị cũ và tăng `counter` lên 1 trước khi `fail(...)` ném exception. `value` không nhận được một kết quả gán bình thường, nhưng `counter` vẫn bằng 1 sau khi exception được bắt.

Đây không phải transaction. Muốn khôi phục trạng thái cần có thiết kế xử lý/hoàn tác phù hợp; `try/catch` tự thân không khôi phục mọi biến hay thao tác I/O trước đó.

### 4.8. `catch` và `finally` có vai trò khác nhau

Trong phần cuối demo, phép tính cho `recovered` kết thúc đột ngột, nhưng `catch` bắt exception và **chủ động gán giá trị dự phòng 99**. Khối `finally` in dòng dọn dẹp và sau đó chương trình tiếp tục.

- `catch` xử lý loại exception phù hợp; không tự sinh kết quả cho biểu thức đã hỏng.
- `finally` thông thường được thực thi khi rời `try/catch`, kể cả khi có exception, nhưng cũng có các trường hợp JVM/tiến trình không thể hoàn tất dọn dẹp (ví dụ bị kết thúc cưỡng bức).
- Nếu bản thân `finally` ném exception mới, exception đó có thể làm thay đổi cách khối hoàn tất. Tránh `return` hoặc ném exception mới trong `finally` trừ khi thực sự có chủ đích.

Trong mã ứng dụng, ưu tiên **try-with-resources** để đóng các tài nguyên hỗ trợ `AutoCloseable`, thay vì tự xử lý nhiều đường thoát phức tạp khi không cần.

## 5. Chỉ cần một phần lỗi, toàn bộ biểu thức bao ngoài bị ảnh hưởng

Ví dụ:

~~~java
int total = 5 + Integer.parseInt("abc") * 2;
~~~

Biểu thức có cách nhóm `5 + (parseInt("abc") * 2)`. Khi `parseInt` ném `NumberFormatException`:

1. Lời gọi không trả `int` bình thường.
2. Phép nhân không có đủ toán hạng để tính giá trị.
3. Phép cộng không nhận được giá trị cho toán hạng phải.
4. Phép gán cho `total` không hoàn tất.
5. Luồng xử lý chuyển theo quy tắc exception.

Nếu đây là bước xử lý dữ liệu người dùng, nên bắt `NumberFormatException` tại nơi có thể thông báo lỗi, yêu cầu nhập lại hoặc áp dụng một giá trị dự phòng **đã được đặc tả**, không lặng lẽ đoán thay.

## 6. Checked và unchecked exceptions

Sự khác biệt **checked / unchecked** liên quan tới yêu cầu **khai báo hoặc xử lý tại compile time**; không làm thay đổi nguyên tắc một biểu thức ném exception sẽ kết thúc đột ngột.

| Loại | Ví dụ | Compiler yêu cầu gì? |
| --- | --- | --- |
| Checked exception | `java.io.IOException` | Thông thường phải `catch` hoặc khai báo `throws` |
| Unchecked exception | `ArithmeticException`, `NullPointerException`, `NumberFormatException` | Không buộc phải `catch` hoặc khai báo `throws` |
| Lỗi nghiêm trọng thuộc `Error` | `OutOfMemoryError` | Cũng unchecked; thường không nên cố xử lý như lỗi nhập liệu |

Một exception có thể phát sinh sâu trong lời gọi phương thức rồi lan qua các biểu thức bao ngoài. Dù thuộc loại checked hay unchecked, nếu không được xử lý phù hợp thì đường thực thi bình thường không thể hoàn tất như dự kiến.

## 7. Phân biệt “kết thúc đột ngột” của biểu thức với câu lệnh

**Biểu thức** có thể kết thúc đột ngột do exception. **Câu lệnh** trong Java còn có các dạng hoàn tất đột ngột khác như `return`, `break` hoặc `continue` ở ngữ cảnh phù hợp.

~~~java
static int example(boolean stop) {
    if (stop) {
        return 1; // Phương thuc roi tai return.
    }
    return 2;
}
~~~

`return` ở ví dụ là **câu lệnh điều khiển**, không phải một toán tử số hoặc giá trị exception. Do đó khi bàn riêng về **biểu thức ném ngoại lệ**, tránh đánh đồng `return` với “exception được ném từ phép cộng”.

## 8. Cách viết mã dễ bảo trì hơn

Khi một biểu thức có nhiều lời gọi có thể thất bại, thường nên tách các bước để xác định rõ điểm lỗi và chính sách dự phòng:

~~~java
String text = "42";
int parsed;
try {
    parsed = Integer.parseInt(text);
} catch (NumberFormatException ex) {
    parsed = 0; // Chi dung neu bai toan cho phep fallback 0.
}
int total = parsed * 2;
~~~

Cách này làm rõ:

- Dữ liệu nào cần được parse.
- Loại exception nào có thể phát sinh và được bắt.
- Giá trị dự phòng có thực sự được cho phép hay không.
- Phép nhân chỉ bắt đầu **sau khi** bước parse đã được giải quyết.

Không cần bắt mọi `RuntimeException` hoặc `Exception` một cách vô điều kiện. Nên bắt **lỗi có thể xử lý hợp lý tại đúng ranh giới nghiệp vụ**, để các lỗi lập trình không bị che giấu.

## 9. Bài tập tự kiểm tra

1. **Bình thường:** `int n = 3 * (2 + 1);` kết thúc ra sao?  
   *Đáp án:* Bình thường, `n = 9`.
2. **Dừng sớm:** Trong `first() + fail() + last()`, nếu `fail()` ném exception thì `last()` chạy không?  
   *Đáp án:* Không; toàn bộ phép cộng kết thúc đột ngột.
3. **Đối số:** Nếu đối số thứ hai của lời gọi ba đối số ném exception, đối số thứ ba và thân phương thức có chạy không?  
   *Đáp án:* Không.
4. **Ngắn mạch:** `false && risky()` không gọi `risky()`; đây có phải kết thúc đột ngột không?  
   *Đáp án:* Không; toàn biểu thức kết thúc bình thường với `false`.
5. **Trạng thái:** `int x = 0;` rồi đánh giá `x++ + fail()`; bắt exception xong `x` bằng bao nhiêu?  
   *Đáp án:* `1` vì phép tăng đã diễn ra trước khi ném exception.
6. **Chia cho 0:** `int x = 10 / 0;` và `double y = 10.0 / 0.0;` khác nhau ra sao?  
   *Đáp án:* Phép thứ nhất ném `ArithmeticException`; phép thứ hai cho số thực dương vô cực theo quy tắc floating-point.
7. **`catch`:** Bắt được exception từ `Integer.parseInt("abc")` có nghĩa biểu thức ban đầu tự tính tiếp không?  
   *Đáp án:* Không; phải chọn luồng xử lý mới (ví dụ nhập lại hoặc gán fallback hợp lệ).

## Checklist

- [ ] Phân biệt kết thúc **bình thường** với kết thúc **đột ngột do exception**.
- [ ] Khi một biểu thức con thất bại, biểu thức bao ngoài không tạo kết quả bình thường.
- [ ] Những toán hạng/đối số chưa tới lượt sẽ **không được đánh giá** sau exception.
- [ ] Tác dụng phụ đã diễn ra **không tự động rollback**.
- [ ] Hiểu `&&`, `||`, `?:` có thể bỏ qua nhánh **mà vẫn hoàn tất bình thường**.
- [ ] Phân biệt trách nhiệm của `try`, `catch`, `finally`.
- [ ] Biết checked/unchecked là vấn đề hợp đồng compile-time, không thay đổi cơ chế lan truyền exception.
- [ ] Bắt và xử lý lỗi tại nơi ứng dụng có thể đưa ra quyết định chính xác.

**Kết luận:** Đánh giá một biểu thức Java có thể **hoàn thành với kết quả bình thường** hoặc **dừng đột ngột vì ngoại lệ**. Khi ngoại lệ phát sinh, các bước phía sau không tiếp tục, nhưng những tác dụng phụ trước đó vẫn còn; xử lý bằng `catch` là **một luồng điều khiển mới**, không phải “chạy tiếp” phép tính đã hỏng.


</details>

- [Quay lại đầu trang](#back-to-top)
