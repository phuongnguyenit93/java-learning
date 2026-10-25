<a id="back-to-top"></a>

# Suy luận với var

## Menu
- [var suy luận kiểu biến cục bộ từ biểu thức khởi tạo](#var-type-inference-001)
- [var giữ nguyên kiểm tra kiểu tĩnh](#var-type-inference-002)
- [Những vị trí được phép dùng var](#var-type-inference-003)
- [Vì sao var cần giá trị khởi tạo](#var-type-inference-004)
- [Không suy ra kiểu từ null hoặc danh sách khởi tạo mảng đứng riêng](#var-type-inference-005)
- [Không dùng var cho trường hoặc kiểu trả về phương thức](#var-type-inference-006)
- [Kiểu suy ra từ literal và các phép tính số](#var-type-inference-007)
- [Chọn var khi kiểu vẫn rõ ràng với người đọc](#var-type-inference-008)

## <a id="var-type-inference-001">var suy luận kiểu biến cục bộ từ biểu thức khởi tạo</a>

<details>
<summary>Xem chi tiết</summary>

# `var` suy luận kiểu biến cục bộ từ biểu thức khởi tạo — Java 17+

Từ Java 10, `var` cho phép **trình biên dịch suy ra kiểu tĩnh của biến cục bộ** từ biểu thức khởi tạo. Đây là thay đổi về cú pháp khai báo: `var` không tạo kiểu dữ liệu mới, không làm Java trở thành ngôn ngữ kiểu động và không đổi kiểu biến theo các lần gán tiếp theo.

~~~java
var count = 10;        // int count = 10;
var title = "Java";    // String title = "Java";
var active = true;     // boolean active = true;
~~~

## 1. Compiler đọc kiểu của biểu thức bên phải

Khi gặp `var name = expression;` ở vị trí được phép, compiler kiểm tra `expression`, xác định kiểu của nó, rồi dùng kiểu ấy để kiểm tra mọi câu lệnh tiếp theo. Compiler không chạy chương trình để quan sát giá trị và không suy luận ngược từ các phép gán sau khai báo.

| Biểu thức khởi tạo | Kiểu tĩnh suy ra | Điều cần chú ý |
| --- | --- | --- |
| `10` | `int` | Literal nguyên không hậu tố thường là `int` |
| `10L` | `long` | `L` chọn literal `long` |
| `2.5` | `double` | Literal thực mặc định là `double` |
| `"Java"` | `String` | Kiểu chuỗi đã rõ |
| `Integer.valueOf(7)` | `Integer` | Kiểu trả về đã khai báo của method |
| `new int[]{1, 2}` | `int[]` | Biểu thức tạo mảng mang kiểu cụ thể |
| `java.util.List.of("A", "B")` | `List<String>` | Tham số generic suy ra từ đối số |
| `new java.util.ArrayList<String>()` | `ArrayList<String>` | Kiểu lớp triển khai và phần tử đã rõ |

Với `var count = 10;`, compiler chấp nhận `count = 20;` nhưng từ chối `count = "20";`. Thay `var` bằng `int` trong cùng khai báo không thay đổi phép kiểm tra kiểu.

## 2. Ví dụ chạy độc lập trên JDK 17+

Lưu chương trình dưới tên **`VarInferenceDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;

public final class VarInferenceDemo {
    private static String findTitle(boolean found) {
        return found ? "Guide" : null;
    }

    public static void main(String[] args) {
        var copies = 3;                         // int
        var language = "Java";                  // String
        var labels = List.of("red", "blue");     // List<String>
        var editable = new ArrayList<String>(); // ArrayList<String>
        editable.add(labels.get(0));
        // editable.add(7); // Lỗi biên dịch: cần String.

        var total = 0;                          // int
        for (var value : new int[]{2, 4, 6}) { // value là int
            total += value;
        }

        var title = findTitle(false);           // String, giá trị null
        // copies = "many"; // Lỗi biên dịch: copies là int.

        System.out.println("copies=" + copies);
        System.out.println("language=" + language);
        System.out.println("labels=" + labels);
        System.out.println("editable=" + editable);
        System.out.println("total=" + total);
        System.out.println("titleIsNull=" + (title == null));
    }
}
~~~

Tại thư mục chứa tệp, chạy:

~~~bash
javac --release 17 -encoding UTF-8 VarInferenceDemo.java
java VarInferenceDemo
~~~

Kết quả:

~~~text
copies=3
language=Java
labels=[red, blue]
editable=[red]
total=12
titleIsNull=true
~~~

Trong chương trình trên, `findTitle` khai báo trả `String`, nên `title` có kiểu tĩnh `String` **ngay cả khi giá trị thực tế là `null`**. Vòng `for` suy ra kiểu phần tử từ mảng `int[]`; còn lời gọi `List.of` và constructor `ArrayList<String>` mang thông tin generic ở thời điểm biên dịch.

## 3. Biểu thức phải thể hiện kiểu mà bạn thực sự muốn

`var` suy luận từ **initializer hiện tại**; compiler không dùng các lệnh sau để đoán kiểu phần tử của một collection:

~~~java
var mixed = new ArrayList<>();    // ArrayList<Object>
mixed.add("red");
mixed.add(7);                     // Hợp lệ với ArrayList<Object>.

var names = new ArrayList<String>();
names.add("red");
// names.add(7);                  // Không hợp lệ: cần String.
~~~

Nếu cần kiểu tĩnh là interface, viết kiểu ấy ở phía trái:

~~~java
List<String> otherNames = new ArrayList<>();
~~~

Ở khai báo này, `List<String>` thể hiện mức trừu tượng mong muốn và cung cấp kiểu đích cho diamond `<>`. Với `var`, kiểu biến ở ví dụ trước là `ArrayList<String>`, không tự chuyển thành `List<String>` dù class triển khai interface đó.

## 4. Những ranh giới để đọc các bài tiếp theo

- **Kiểm tra kiểu tĩnh:** `var` vẫn tuân thủ phép gán, overload, generic và truy cập method/field; ví dụ chuyên sâu nằm ở bài “var giữ nguyên kiểm tra kiểu tĩnh”.
- **Vị trí khai báo:** `var` áp dụng cho các vị trí local hợp lệ, vòng lặp, resource và tham số lambda theo quy tắc riêng; không dùng làm kiểu field hoặc return type.
- **Nguồn suy kiểu:** local `var` thông thường cần initializer; `var x;` không hợp lệ. `var x = null;` và `var a = {1, 2};` cũng không hợp lệ, trong khi `var a = new int[]{1, 2};` hợp lệ.
- **Biểu thức số:** `var ratio = 5 / 2;` nhận `int` với giá trị `2`. Phép chia nguyên diễn ra trước suy luận; bài về literal và phép tính số giải thích promotion, cast và overflow.
- **Dễ đọc:** initializer đủ kiểu cho compiler chưa chắc đủ rõ với người đọc. Có lúc `List<String> names = ...` diễn đạt ý đồ tốt hơn.

Những quy tắc trên cùng xuất phát từ một điểm: **`var` nhận kiểu của biểu thức ở vị trí khai báo, rồi Java áp dụng hệ thống kiểu bình thường**.

## 5. Tự kiểm tra

1. `var n = 1; var m = 1L;` suy ra kiểu nào? **Đáp án:** `int`, `long`.
2. Vì sao `var x = Integer.valueOf(7);` không suy ra `int`? **Đáp án:** `valueOf` trả `Integer`.
3. `var list = new ArrayList<>();` có tự suy ra `ArrayList<String>` từ `list.add("A")` không? **Đáp án:** Không; ở đây là `ArrayList<Object>`.
4. `var value = findTitle(false);` có hợp lệ nếu method trả `String` nhưng giá trị `null` không? **Đáp án:** Có, kiểu tĩnh từ chữ ký method là `String`.
5. Nếu muốn biến mang kiểu interface `List<String>` thay vì class `ArrayList<String>`, viết thế nào? **Đáp án:** `List<String> names = new ArrayList<>();`.

**Ghi nhớ:** Hãy xác định kiểu của biểu thức khởi tạo, kiểm tra kiểu suy ra có đúng ý đồ không và dùng khai báo tường minh khi điều đó giúp người đọc hiểu nhanh hơn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="var-type-inference-002">var giữ nguyên kiểm tra kiểu tĩnh</a>

<details>
<summary>Xem chi tiết</summary>

# `var` giữ nguyên kiểm tra kiểu tĩnh trong Java 17+

## 1. Mục tiêu: ít chữ hơn, không bớt an toàn kiểu

Trong Java, `var` cho phép **suy luận kiểu của biến cục bộ** (*local-variable type inference*) từ biểu thức khởi tạo. Tính năng xuất hiện từ Java 10 và tiếp tục được hỗ trợ trong Java 17+.

~~~java
var count = 10;        // Compiler suy luận int
var title = "Java";    // Compiler suy luận String
var price = 12.5;      // Compiler suy luận double

System.out.println(count + 1);  // 11
System.out.println(title);      // Java
System.out.println(price);      // 12.5
~~~

Điều cốt lõi: **`var` không làm Java trở thành ngôn ngữ có kiểu động (*dynamic typing*)**. Kiểu biến được xác định **khi biên dịch**, sau đó vẫn được compiler kiểm tra trong mọi phép gán và lời gọi method.

~~~java
var count = 10;
count = 20;         // Hợp lệ: int -> int
// count = "twenty"; // Lỗi biên dịch: String không chuyển thành int
~~~

Bạn có thể hình dung `var count = 10;` có tác dụng tương đương về kiểu và hành vi sau suy luận với `int count = 10;`, **không phải** một biến chứa “giá trị bất kỳ”.

### Điều cần đạt sau bài

- Hiểu `var` suy luận kiểu tĩnh, **không đổi kiểu khi gán lại**.
- Phân tích kiểu được suy ra từ literal, method, constructor và generic.
- Dự đoán lỗi compile do gán sai kiểu, thiếu initializer hoặc thiếu kiểu đích.
- Dùng `var` đúng chỗ mà không làm code khó đọc.

## 2. Kiểu tĩnh được chốt một lần từ initializer

Với `var name = expression;`, compiler xác định kiểu của `expression` ngay lúc khai báo. Các lần gán tiếp theo phải phù hợp với kiểu đã suy ra:

~~~java
var number = 7;              // int
number = 9;                  // Hợp lệ.
// number = 9L;              // Lỗi: long không tự thu hẹp về int.
var label = "Java";           // String
// label = 42;                // Lỗi: int không gán được cho String.
~~~

Độ rộng số được quyết định bởi literal hoặc phép toán trong initializer, không bởi các giá trị muốn gán về sau. Bài “Kiểu suy ra từ literal và các phép tính số” phân tích riêng numeric promotion và overflow.

## 3. Vì sao đây vẫn là static typing?

Compiler vẫn kiểm tra những yếu tố bình thường của Java:

1. **Tính hợp lệ của phép gán:** kiểu biểu thức bên phải phải tương thích với kiểu đã suy ra.
2. **Truy cập method/field:** chỉ các thành viên hợp lệ theo **kiểu tĩnh** mới được gọi.
3. **Toán tử:** các toán tử được kiểm tra theo kiểu toán hạng.
4. **Kiểu generic:** `List<String>` khác `List<Integer>`; compiler áp dụng kiểm tra kiểu với phần tử.
5. **Overload resolution:** chọn method theo kiểu tĩnh của đối số tại compile time.

~~~java
var text = "Java"; // String
System.out.println(text.length()); // 4
// System.out.println(text.nonExistingMethod()); // Lỗi compile
// text = 123; // Lỗi compile
~~~

Bạn sẽ thấy cùng mức kiểm tra kiểu nếu viết:

~~~java
String text = "Java";
~~~

**`var` không phải `Object`**. Với `var text = "Java";`, biến có kiểu `String`, nên `text.length()` biên dịch được; với `Object text = "Java";`, lời gọi `text.length()` không hợp lệ nếu không ép kiểu vì `Object` không khai báo `length()`.

**`var` cũng không phải JavaScript `var`**: hai ngôn ngữ dùng cùng chuỗi ký tự cho những cơ chế khác nhau.

## 4. Hai ví dụ cùng chữ `var`, nhưng suy ra hai kiểu khác nhau

~~~java
var primitive = 42;                  // int
var wrapped = Integer.valueOf(42);  // Integer

System.out.println(primitive + 1); // 43
System.out.println(wrapped + 1);   // 43: unboxing rồi tính
~~~

Ở ví dụ `wrapped`, compiler biết biến có kiểu `Integer`; ở ví dụ `primitive`, compiler biết biến có kiểu `int`.

Điều này ảnh hưởng tới khả năng giữ `null`:

~~~java
var count = 5;             // int
// count = null;            // Lỗi compile: primitive int không nhận null

var optionalCount = Integer.valueOf(5); // Integer
optionalCount = null;          // Biên dịch được, vì Integer là reference type
~~~

Nhưng khi auto-unboxing một wrapper null, bạn vẫn gặp `NullPointerException` như với khai báo `Integer` thông thường:

~~~java
if (optionalCount != null) {
    int safe = optionalCount;
    System.out.println(safe);
}
~~~

**Suy luận kiểu không tự bổ sung kiểm tra null runtime.** `var` chỉ giảm lặp tên kiểu tại chỗ khai báo, không thay đổi hợp đồng nullable của kiểu đã suy ra.

## 5. Các lỗi biên dịch điển hình chứng minh kiểu vẫn cố định

Những dòng có comment bên dưới là **ví dụ sai có chủ đích**, không đưa vào chương trình chạy ở phần sau:

~~~java
var score = 10;
// score = "ten";             // String không gán được vào int

var city = "Hà Nội";
// city = 42;                 // int không gán được vào String

var names = new java.util.ArrayList<String>();
names.add("Lan");
// names.add(100);            // int không phù hợp với List<String>

var answer = 2 + 3;         // int
// answer.notARealMethod();   // int không có method như vậy
~~~

Không có “lần gán thứ hai khiến biến đổi kiểu”. Compiler **không suy luận lại** kiểu của `score` hoặc `city` ở các câu lệnh sau.

Một ngoại lệ dễ gây hiểu nhầm về giá trị nhưng **không phải đổi kiểu**:

~~~java
var number = 3;   // int
number = (int) 3.9; // Hợp lệ: cast double -> int, giá trị thành 3
~~~

Ở đây kiểu `number` vẫn là **int**. Dùng cast có thể làm mất dữ liệu; tính đúng nghiệp vụ vẫn phải kiểm tra riêng.

## 6. Những giới hạn kiểu tĩnh vẫn áp dụng khi dùng `var`

Compiler chỉ có thể suy luận khi initializer **có kiểu hợp lệ**. Không thể viết `var item;`, `var item = null;`, `var values = {1, 2};` hoặc gán trực tiếp lambda không có kiểu đích. Có thể sử dụng kiểu tường minh hoặc tạo biểu thức đủ kiểu:

~~~java
String nullable = null;
var typedNull = (String) null; // Kiểu String dù giá trị null
var values = new int[] {1, 2}; // Kiểu int[]
java.util.function.IntUnaryOperator f = x -> x + 1;
var f2 = (java.util.function.IntUnaryOperator) (x -> x + 1);
~~~

Trong cả `f` và `f2`, **functional interface** cung cấp kiểu đích cho lambda; `var` không tự phát minh kiểu functional interface. Quy tắc suy luận từ `null`, mảng và vị trí được phép viết `var` có bài chuyên biệt trong chương; ở đây chúng là **bằng chứng rằng static typing không bị bỏ qua**.

### Kiểu generic và abstraction vẫn được giữ

~~~java
var strings = new java.util.ArrayList<String>();
strings.add("An");
// strings.add(5); // Lỗi biên dịch: cần String

var bag = new java.util.ArrayList<>(); // ArrayList<Object>
bag.add("An");
bag.add(5);

java.util.List<String> names = new java.util.ArrayList<>();
~~~

Không có kiểu đích `String` cho diamond trống của `bag`, nên biến suy ra `ArrayList<Object>` và `add(5)` hợp lệ (boxing thành `Integer`). Đặt `List<String>` ở vế trái khi mục tiêu là dùng **API của interface**, thay vì cho `var` suy ra lớp triển khai cụ thể. Sau khai báo, generic và tập method hợp lệ vẫn được compiler kiểm tra; `var` **không** bỏ qua `UnsupportedOperationException` nếu gọi thao tác không hỗ trợ trên danh sách từ `List.of` lúc chạy.

## 7. Overload được chọn theo kiểu tĩnh, không theo nội dung lúc chạy

Giả sử có hai overload:

~~~java
static void describe(Object object) {
    System.out.println("Object");
}

static void describe(String text) {
    System.out.println("String");
}
~~~

Trong `main`:

~~~java
var text = "Java";           // Kiểu String
var object = (Object) "Java"; // Kiểu Object

describe(text);   // String
describe(object); // Object
~~~

Cả hai biến **có thể cùng tham chiếu tới một object String**, nhưng overload được lựa chọn **khi biên dịch** dựa trên kiểu tĩnh của biểu thức:

- `text` kiểu `String` → chọn `describe(String)`.
- `object` kiểu `Object` → chọn `describe(Object)`.

**`var` không hỗ trợ tự đổi overload theo runtime.** Đây là dấu hiệu trực quan rằng suy luận kiểu trong Java vẫn thuộc mô hình static typing.



## 8. Chương trình Java 17 chạy độc lập

Tạo file đúng tên **`VarStaticTypingDemo.java`**:

~~~java
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.IntUnaryOperator;

public final class VarStaticTypingDemo {
    private VarStaticTypingDemo() {}

    record Person(String name, int age) {}

    static int sum(int a, int b) {
        return a + b;
    }

    static void describe(Object object) {
        System.out.println("overload=Object");
    }

    static void describe(String text) {
        System.out.println("overload=String");
    }

    public static void main(String[] args) throws Exception {
        var number = 10;         // int
        number = 12;
        var longNumber = 10L;    // long
        var ratio = 2.5;         // double
        var enabled = true;      // boolean
        var title = "Java";      // String

        System.out.println("int=" + number);
        System.out.println("long=" + longNumber);
        System.out.println("double=" + ratio);
        System.out.println("boolean=" + enabled);
        System.out.println("text length=" + title.length());

        var answer = sum(2, 3); // int từ kiểu trả về
        System.out.println("sum=" + answer);

        var names = new ArrayList<String>();
        names.add("Lan");
        names.add("An");
        System.out.println("names=" + names);
        System.out.println("first name=" + names.get(0));

        var words = List.of("A", "B");
        for (var word : words) {
            System.out.println("word=" + word);
        }

        var bag = new ArrayList<>();
        bag.add("text");
        bag.add(123);
        System.out.println("Object bag=" + bag);

        var flags = new boolean[] {true, false};
        System.out.println("flags length=" + flags.length);

        var person = new Person("Mai", 20);
        System.out.println("person=" + person.name() + ":" + person.age());

        var asString = "Java";
        var asObject = (Object) "Java";
        describe(asString);
        describe(asObject);

        var optional = (String) null;
        System.out.println("typed null=" + (optional == null));

        var increment = (IntUnaryOperator) (x -> x + 1);
        System.out.println("lambda=" + increment.applyAsInt(4));

        BinaryOperator<String> merge =
                (var left, var right) -> left + "-" + right;
        System.out.println("lambda parameters=" + merge.apply("A", "B"));

        for (var i = 0; i < 2; i++) {
            System.out.println("for i=" + i);
        }

        try (var reader = new StringReader("X")) {
            System.out.println("resource char=" + (char) reader.read());
        }

        var original = new ArrayList<String>();
        var alias = original;
        alias.add("shared");
        System.out.println("same reference=" + (original == alias));
        System.out.println("original=" + original);
    }
}
~~~

**Biên dịch và chạy với JDK 17+**. Mở terminal trong thư mục chứa file.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 VarStaticTypingDemo.java
java -cp . VarStaticTypingDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\VarStaticTypingDemo.java
java -cp . VarStaticTypingDemo
~~~

**Exact expected output:**

~~~text
int=12
long=10
double=2.5
boolean=true
text length=4
sum=5
names=[Lan, An]
first name=Lan
word=A
word=B
Object bag=[text, 123]
flags length=2
person=Mai:20
overload=String
overload=Object
typed null=true
lambda=5
lambda parameters=A-B
for i=0
for i=1
resource char=X
same reference=true
original=[shared]
~~~

**Giải thích kết quả dễ nhầm:**

- `number` suy ra kiểu `int`; gán `12` được phép nhưng không thể gán String.
- `answer` suy ra `int` từ method `sum`.
- `names` có kiểu `ArrayList<String>`, nên chỉ cho thêm giá trị String theo generic.
- `bag` được tạo với diamond trống và không có target type ngoài `var`, vì vậy suy ra `ArrayList<Object>`; thêm cả String và Integer là hợp lệ.
- `asString` và `asObject` dẫn tới overload khác nhau **theo kiểu tĩnh**, không do object String thay đổi.
- `optional` vẫn có kiểu `String` dù giá trị hiện tại là null.
- `increment` có target type qua cast `IntUnaryOperator`; `merge` có target type `BinaryOperator<String>`.
- `for` và `try-with-resources` sử dụng `var` trong vị trí hợp lệ.
- `alias = original` giữ cùng tham chiếu, không sao chép ArrayList.

**Không có** các dòng compile error cố ý trong chương trình này; các ví dụ code không hợp lệ ở phần trước đều được comment hoặc đặt riêng. Ví dụ dùng các API tiêu chuẩn của JDK 17 và không cần thư viện ngoài.



## 9. Bài tập và đáp án

### Bài A — suy luận literal

~~~java
var x = 1;
var y = 1L;
var z = 1.0f;
~~~

**Đáp án:** `x` là `int`, `y` là `long`, `z` là `float`.

### Bài B — gán lại sai kiểu

~~~java
var label = "A";
label = "B";
// label = 10;
~~~

**Đáp án:** gán `"B"` hợp lệ; gán `10` lỗi biên dịch vì `label` có kiểu `String` đã chốt.

### Bài C — generic

~~~java
var scores = new java.util.ArrayList<Integer>();
scores.add(1);
// scores.add("two");
~~~

**Đáp án:** `scores` có kiểu `ArrayList<Integer>`, nên thêm `"two"` không hợp lệ. `var` không bỏ kiểm tra generic.

### Bài D — overload

~~~java
static void show(Object x) { System.out.println("Object"); }
static void show(String x) { System.out.println("String"); }

// Trong main:
var a = "Hi";
var b = (Object) "Hi";
show(a);
show(b);
~~~

**Đáp án:**

~~~text
String
Object
~~~

### Bài E — null

~~~java
// var a = null;
String b = null;
var c = (String) null;
~~~

**Đáp án:** khai báo `a` sai, vì compiler không suy ra kiểu từ null literal. `b` và `c` hợp lệ; `c` có kiểu `String` dù giữ giá trị null.

### Bài F — chọn `var` hay kiểu tường minh?

Bạn muốn công khai ý định làm việc qua giao diện `List<String>` thay vì các thao tác riêng của `ArrayList`:

~~~java
var names = new java.util.ArrayList<String>();
~~~

**Đáp án:** có thể viết:

~~~java
java.util.List<String> names = new java.util.ArrayList<>();
~~~

Cách này thể hiện rõ **hợp đồng phụ thuộc vào `List`**. `var` vẫn đúng về kiểu; vấn đề ở đây là tính dễ đọc và mức abstraction mong muốn, không phải Java kiểm tra kiểu yếu hơn.

## 10. Checklist và kết luận

- [ ] Với `var`, phép gán sai kiểu vẫn bị từ chối tại compile time.
- [ ] Method access, generic và overload vẫn dựa trên kiểu tĩnh suy ra.
- [ ] Phân biệt `int` với `Integer` và kiểm tra null trước khi unbox.
- [ ] Phân biệt `var text = "Java";` với `Object text = "Java";`.
- [ ] Hiểu vì sao biến cùng trỏ một `String` có thể chọn hai overload khác nhau khi kiểu tĩnh khác nhau.
- [ ] Có thể chạy `VarStaticTypingDemo.java` và giải thích các kết quả.

**Kết luận:** `var` rút gọn khai báo biến cục bộ, nhưng kiểm tra kiểu tĩnh, lựa chọn overload và kiểm tra generic vẫn hoạt động như với khai báo tường minh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="var-type-inference-003">Những vị trí được phép dùng var</a>

<details>
<summary>Xem chi tiết</summary>

# Những vị trí được phép dùng `var` trong Java 17+

> **Java core — language-basics / Suy luận với var**  
> **Yêu cầu:** JDK 17+; ví dụ độc lập, không sử dụng thư viện ngoài Java.  
> **Mục tiêu:** Biết chính xác nơi được dùng `var`, nơi không được dùng, vì sao trình biên dịch suy ra kiểu tại thời điểm biên dịch và cách chọn `var` mà không làm mã khó đọc.

## 1. `var` là suy luận kiểu cục bộ, không phải kiểu động

Từ **Java 10**, Java cho phép viết `var` ở một số khai báo biến cục bộ thay cho tên kiểu tường minh:

~~~java
var count = 3;          // Trình biên dịch suy ra int.
var title = "Java";     // Suy ra String.
var price = 12.5;       // Suy ra double.
~~~

Kiểu được **suy ra khi biên dịch** từ biểu thức khởi tạo (hoặc từ ngữ cảnh hợp lệ của vòng lặp/lambda), sau đó **cố định**. Không thể gán một kiểu tùy ý khác vào cùng biến:

~~~java
var count = 3;
// count = "three"; // Lỗi biên dịch: count có kiểu int, không phải String.
~~~

`var` **không phải** tên một kiểu dữ liệu thực, không phải `Object` mặc định và không mang cơ chế “dynamic typing”. Java không cho dùng `var` ở mọi nơi có thể viết một tên kiểu.

## 2. Danh sách vị trí được phép

| Vị trí | Ví dụ hợp lệ | Kiểu được xác định như thế nào? |
| --- | --- | --- |
| Biến cục bộ có khởi tạo trong phương thức | `var number = 42;` | Theo biểu thức `42` → `int` |
| Biến cục bộ trong constructor hoặc khối khởi tạo | `var text = "ok";` | Theo biểu thức khởi tạo → `String` |
| Phần khai báo khởi tạo của `for` thông thường | `for (var i = 0; i < 3; i++)` | Theo `0` → `int` |
| Biến phần tử trong enhanced `for` | `for (var item : names)` | Theo kiểu phần tử của mảng/`Iterable` |
| Biến tài nguyên trong `try-with-resources` | `try (var reader = new StringReader("hi"))` | Theo đối tượng tài nguyên được khởi tạo |
| Tham số **lambda** có `var` | `(var a, var b) -> a + b` | Theo *target functional interface* của lambda; hỗ trợ từ **Java 11** |

Đối với biến cục bộ thông thường, **phải có initializer**. Enhanced `for` suy ra kiểu từ phần tử được duyệt; lambda suy ra từ kiểu mục tiêu (ví dụ `BiFunction<Integer, Integer, Integer>`). Đây là các dạng cú pháp hợp lệ riêng, không phải “cho phép biến `var` bất kỳ ở mọi nơi”.

### Biến cục bộ trong block

~~~java
void print() {
    var message = "Hello"; // String cục bộ.
    {
        var active = true;  // boolean cục bộ trong block lồng.
        System.out.println(message + active);
    }
}
~~~

Tương tự, trong **thân constructor**, khối `static { ... }` hoặc khối khởi tạo instance `{ ... }`, có thể dùng `var` để khai báo **biến cục bộ** thuộc block. Nhưng **field** của class vẫn phải khai báo bằng kiểu cụ thể.

## 3. `var` trong hai kiểu vòng lặp `for`

### `for` thông thường

~~~java
for (var i = 0; i < 3; i++) {
    System.out.println(i);
}
~~~

`i` được suy ra `int` từ `0` và chỉ có phạm vi phù hợp với cấu trúc vòng lặp. Không có chuyện `i` tự đổi kiểu khi giá trị tăng.

### Enhanced `for` trên mảng hoặc collection

~~~java
String[] fruits = {"cam", "tao"};
for (var fruit : fruits) {
    System.out.println(fruit); // fruit có kiểu String.
}
~~~

Cũng có thể dùng `for (var element : list)` với collection có kiểu phần tử cụ thể. **`var` không tự khám phá kiểu runtime của từng object**: compiler suy ra kiểu phần tử tĩnh từ khai báo mảng hoặc kiểu phần tử được khai báo của `Iterable`.

Nếu collection được khai báo `List<Object>` thì `var element` được suy ra `Object`, dù vài phần tử thực tế là `String`. Do đó lựa chọn kiểu generic của collection vẫn rất quan trọng.

## 4. `var` trong `try-with-resources`

Một biến tài nguyên trong `try-with-resources` có thể khai báo bằng `var` nếu biểu thức khởi tạo có kiểu tài nguyên hợp lệ (triển khai `AutoCloseable`):

~~~java
import java.io.StringReader;

try (var reader = new StringReader("AB")) {
    System.out.println((char) reader.read()); // A
}
~~~

`reader` được suy ra `StringReader`, có phương thức `read()`. Khi rời khối `try`, `close()` được gọi tự động theo cơ chế `try-with-resources`.

**Đừng nhầm** `var` với quy tắc của tài nguyên: chỉ những object có kiểu phù hợp `AutoCloseable` mới được khai báo làm resource. `try (var number = 10) { ... }` **không hợp lệ** vì `int` không phải tài nguyên có thể đóng.

## 5. `var` trong tham số lambda — Java 11 trở lên

Khi lambda có **target type** rõ ràng, từ Java 11 có thể dùng `var` cho **tất cả** tham số:

~~~java
import java.util.function.BiFunction;

BiFunction<Integer, Integer, Integer> add =
        (var left, var right) -> left + right;
System.out.println(add.apply(4, 5)); // 9
~~~

Ở đây compiler biết lambda phải triển khai `BiFunction<Integer, Integer, Integer>` nên `left` và `right` tương ứng có kiểu `Integer` (và phép `+` tự unbox để cộng). `var` không làm suy luận kiểu lambda từ hư không: lambda **vẫn cần kiểu đích**.

Cách dùng hợp lệ đối với hai tham số:

~~~java
BiFunction<Integer, Integer, Integer> a = (x, y) -> x + y;
BiFunction<Integer, Integer, Integer> b = (var x, var y) -> x + y;
BiFunction<Integer, Integer, Integer> c =
        (Integer x, Integer y) -> x + y;
~~~

**Không trộn kiểu khai báo của các tham số cùng lambda:**

~~~java
// KHÔNG biên dịch:
// BiFunction<Integer, Integer, Integer> bad = (var x, y) -> x + y;
// BiFunction<Integer, Integer, Integer> bad2 = (var x, Integer y) -> x + y;
~~~

Nếu dùng `var` cho một tham số lambda thì các tham số còn lại cũng phải dùng `var`. Dạng `var` tiện khi cần dùng annotation hợp lệ trên tham số mà vẫn muốn kiểu được suy luận, nhưng trong ví dụ cơ bản có thể chọn cú pháp `(x, y) -> ...` đơn giản hơn.

## 6. Những vị trí **không** được phép dùng `var`

| Vị trí hoặc kiểu khai báo | Ví dụ không hợp lệ | Lý do |
| --- | --- | --- |
| Field của class (kể cả `static`) | `class A { var n = 1; }` | `var` chỉ được hỗ trợ trong ngữ cảnh cục bộ cho phép |
| Kiểu trả về của method | `var getCount() { return 1; }` | Return type phải khai báo rõ |
| Tham số phương thức thường | `void send(var message) { }` | Không phải lambda formal parameter |
| Tham số constructor | `A(var value) { }` | Không dùng `var` tại khai báo tham số constructor |
| Tham số `catch` | `catch (var ex) { }` | Cần kiểu exception |
| Biến cục bộ không khởi tạo | `var n;` | Không có thông tin để suy ra kiểu |
| Khởi tạo chỉ bằng `null` | `var value = null;` | `null` không đủ suy ra một kiểu |
| Khai báo nhiều biến trong một câu | `var a = 1, b = 2;` | Khai báo dùng `var` chỉ được một biến |
| Array initializer không có `new` | `var data = {1, 2};` | Không có kiểu mục tiêu cho initializer này |
| Tự tham chiếu trong initializer | `var n = n + 1;` | Không thể suy ra kiểu hợp lệ từ chính biến đang khai báo |

**Cách sửa:**

~~~java
int n;                         // Không cần initializer ở đây.
String value = null;           // Kiểu tường minh nếu muốn null ban đầu.
var typedNull = (String) null; // Hợp lệ: kiểu String đến từ cast.
var data = new int[] {1, 2};  // Hợp lệ: kiểu suy ra int[].
var a = 1;                     // Mỗi khai báo var một biến.
var b = 2;
~~~

Trường hợp `var typedNull = (String) null;` hợp lệ về **cú pháp suy luận kiểu**, nhưng trong ứng dụng thật cần xem liệu null có ý nghĩa không. Đừng cast chỉ để tránh một khai báo kiểu rõ ràng.

## 7. Ví dụ Java 17+ runnable

Tạo file **VarAllowedPlacesDemo.java** chứa nguyên chương trình:

~~~java
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.function.BiFunction;

public final class VarAllowedPlacesDemo {
    public static void main(String[] args) throws IOException {
        var language = "Java";
        var count = 2;
        var names = new ArrayList<String>();
        names.add("An");
        names.add("Binh");

        System.out.println("local=" + language + ":" + count);

        for (var index = 0; index < names.size(); index++) {
            System.out.println("index=" + index + ":" + names.get(index));
        }

        for (var name : names) {
            System.out.println("each=" + name);
        }

        try (var reader = new StringReader("OK")) {
            var first = (char) reader.read();
            var second = (char) reader.read();
            System.out.println("resource=" + first + second);
        }

        BiFunction<Integer, Integer, Integer> plus =
                (var left, var right) -> left + right;
        System.out.println("lambda=" + plus.apply(4, 5));

        var scores = new int[] {3, 7};
        System.out.println("array=" + (scores[0] + scores[1]));

        var typedNull = (String) null;
        System.out.println("typedNull=" + (typedNull == null));
    }
}
~~~

Mở terminal tại thư mục chứa tệp (Bash, PowerShell hoặc Command Prompt) rồi chạy:

~~~bash
javac -encoding UTF-8 VarAllowedPlacesDemo.java
java VarAllowedPlacesDemo
~~~

**Expected output chính xác:**

~~~text
local=Java:2
index=0:An
index=1:Binh
each=An
each=Binh
resource=OK
lambda=9
array=10
typedNull=true
~~~

Những vị trí `var` đã minh họa:

1. `language`, `count`, `names`, `scores` và `typedNull` là **biến cục bộ có initializer**.
2. `index` được khai báo trong phần khởi tạo của vòng `for` thường.
3. `name` là biến phần tử của enhanced `for`, suy ra `String` từ `ArrayList<String>`.
4. `reader` là tài nguyên `try-with-resources`, suy ra `StringReader`.
5. `left` và `right` là tham số lambda (hợp lệ từ Java 11).
6. Giá trị `typedNull` bằng null nhưng **kiểu suy luận là String** do có cast; đối lập với `var typedNull = null;` không hợp lệ.

## 8. Khi nào nên dùng `var` để code dễ hiểu?

`var` giảm lặp kiểu, nhưng không bắt buộc. Hãy dùng khi **kiểu rõ từ initializer** hoặc ngữ cảnh:

~~~java
var reader = new StringReader("Hi"); // Kiểu StringReader rõ.
var count = 0;                        // Kiểu int rõ.
~~~

Cân nhắc kiểu tường minh khi initializer không nói rõ ý nghĩa hoặc dễ suy luận nhầm:

~~~java
var result = calculate();       // Muốn hiểu result phải biết calculate() trả gì.
double result2 = calculate();   // Thể hiện ngay kiểu kỳ vọng.
~~~

Lưu ý đặc biệt với generic diamond:

~~~java
var list = new ArrayList<>();        // Thường suy ra ArrayList<Object>.
var strings = new ArrayList<String>(); // Kiểu phần tử String rõ ràng.
~~~

Nếu cần kiểu API là `List<String>` thay vì concrete class `ArrayList<String>`, có thể viết `List<String> names = new ArrayList<>();`. `var` không tự chọn abstraction cho bạn.

**`var` không thay thế phân tích kiểu**: vẫn cần xem literal số là `int`/`long`/`double`, kiểu generic của initializer, và các phép chuyển kiểu Java có thể thực hiện.

## 9. Bài tập tự kiểm tra

**Bài 1.** Đánh dấu hợp lệ/không hợp lệ: `var x = 1;`, `var y;`, `var z = null;`, `var a = new int[]{2, 3};`, `var b = {2, 3};`.

**Bài 2.** Chuyển vòng `for (int i = 0; i < 5; i++)` sang `var`. Kiểu suy ra là gì? Khi nào biến rời phạm vi?

**Bài 3.** Cho `ArrayList<String>` có hai phần tử; viết enhanced `for` dùng `var` để in từng phần tử. Kiểu tĩnh của biến phần tử là gì?

**Bài 4.** Dùng `try (var reader = new StringReader("A"))` để đọc ký tự. Vì sao kiểu tài nguyên phải triển khai `AutoCloseable`?

**Bài 5.** Khai báo một `BiFunction<Integer, Integer, Integer>` bằng lambda sử dụng hai tham số `var`. Sau đó thử sửa một tham số thành `Integer` và giải thích lỗi biên dịch.

### Ghi nhớ

`var` được dùng ở **biến cục bộ có initializer**, **phần khai báo của `for`**, **biến enhanced `for`**, **biến tài nguyên `try-with-resources`**, và **tham số lambda** (từ Java 11). `var` **không** được dùng làm kiểu của field, return type, tham số method/constructor, tham số `catch` hoặc biến cục bộ thiếu dữ liệu suy luận. Kiểu vẫn được chốt tại **compile time**; hãy ưu tiên sự rõ ràng của mã nguồn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="var-type-inference-004">Vì sao var cần giá trị khởi tạo</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao `var` cần giá trị khởi tạo — Java 17+

`var` được Java hỗ trợ cho **suy luận kiểu biến cục bộ** (*local variable type inference*). Khi viết:

~~~java
var quantity = 10;
~~~

compiler đọc biểu thức khởi tạo `10`, xác định biểu thức đó có kiểu `int` và khai báo `quantity` như một biến cục bộ kiểu **`int`**. Sau đó, biến vẫn chịu kiểm tra kiểu tĩnh như bình thường. `var` **không** phải kiểu dữ liệu mới, cũng không làm Java trở thành ngôn ngữ có kiểu động.

**Tại sao cần giá trị khởi tạo?** Vì trong câu lệnh khai báo biến cục bộ dạng `var`, compiler không thấy kiểu được viết ở bên trái. Nếu không có **biểu thức khởi tạo có kiểu xác định**, nó không có căn cứ để suy luận kiểu của biến ngay lúc khai báo.

~~~java
int number;       // Hop le: kieu int da duoc viet ro.
// var number;     // LOI bien dich: khong co initializer de suy kieu.

var age = 18;     // Hop le: suy ra int.
// age = "18";    // LOI bien dich: String khong phai int.
~~~

**Phạm vi bài:** Quy tắc “phải khởi tạo” ở đây nói về **khai báo biến cục bộ thông thường** bằng `var`. Java còn cho phép `var` ở các ngữ cảnh có **nguồn suy kiểu khác**, chẳng hạn biến phần tử trong enhanced `for` và tham số lambda; các trường hợp đó được giải thích riêng ở phần 6.

## 1. So sánh khai báo tường minh và khai báo bằng `var`

| Khai báo | Hợp lệ? | Compiler biết kiểu từ đâu? |
| --- | --- | --- |
| `String text;` | Có | Kiểu `String` được viết trực tiếp |
| `String text = null;` | Có | Kiểu `String` đã được chỉ rõ |
| `var text = "Java";` | Có | Initializer `"Java"` có kiểu `String` |
| `var count = 7;` | Có | Initializer `7` có kiểu `int` |
| `var amount = 7L;` | Có | Initializer `7L` có kiểu `long` |
| `var item;` | **Không** | Thiếu biểu thức để suy kiểu |
| `var item = null;` | **Không** | Literal `null` không cung cấp kiểu tham chiếu cụ thể |
| `var item = (String) null;` | Có | Cast khiến biểu thức có kiểu `String` |
| `var list = new java.util.ArrayList<String>();` | Có | Biểu thức tạo đối tượng có kiểu `ArrayList<String>` |

Điểm dễ nhầm là **biến khai báo tường minh** vẫn có thể chưa được gán khi khai báo, nhưng **biến khai báo bằng `var`** không thể bỏ initializer trong trường hợp khai báo local thông thường.

## 2. Suy luận kiểu diễn ra **lúc biên dịch**, không phải lúc chạy

Quy trình cơ bản:

~~~text
var count = Integer.parseInt("42");
          |
          v
Compiler kiem tra bieu thuc:
  Integer.parseInt(...) tra ve int
          |
          v
Kieu tinh cua count la int
          |
          v
Kiem tra cac lenh su dung count
  count = 50;      // Hop le
  count = "fifty"; // Sai kieu
~~~

Compiler dựa vào **kiểu tĩnh của biểu thức**, không dựa vào giá trị sau khi chương trình đã chạy. Ví dụ một phương thức được khai báo trả về `String` thì lời gọi của nó là biểu thức kiểu `String`, **kể cả khi một lần gọi cụ thể trả về `null`**.

~~~java
static String findTitle(boolean exists) {
    return exists ? "Guide" : null;
}

// Trong mot phuong thuc:
var title = findTitle(false); // Hop le: suy ra String.
System.out.println(title == null); // true.
~~~

Điều này khác hẳn `var title = null;`: literal `null` đơn lẻ không có kiểu cụ thể để dùng làm kiểu biến. Gán cast `(String) null` lại hợp lệ vì lúc đó biểu thức đã có kiểu `String`.

## 3. Ví dụ Java 17+ chạy độc lập

Tạo file UTF-8 tên **`VarNeedsInitializerDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class VarNeedsInitializerDemo {
    static String findTitle(boolean found) {
        return found ? "Guide" : null;
    }

    static int readCount() {
        return 3;
    }

    public static void main(String[] args) {
        var count = readCount();
        var price = 2.5;
        var title = "Java";

        System.out.println("count=" + count);
        System.out.println("price=" + price);
        title = title + " 17";
        System.out.println("title=" + title);

        var numbers = List.of(2, 4, 6);
        var sum = 0;
        for (var number : numbers) {
            sum += number;
        }
        System.out.println("sum=" + sum);

        var indices = new StringBuilder();
        for (var i = 0; i < 3; i++) {
            indices.append(i);
        }
        System.out.println("indices=" + indices);

        var typedNull = (String) null;
        System.out.println("typedNull=" + (typedNull == null));
        typedNull = "ready";
        System.out.println("typedNullAfterSet=" + typedNull);

        var fromMethod = findTitle(false);
        System.out.println("methodReturnedNull=" + (fromMethod == null));
        fromMethod = findTitle(true);
        System.out.println("methodReturnedText=" + fromMethod);

        String declaredLater;
        declaredLater = "assigned";
        System.out.println("explicitTypeLater=" + declaredLater);

        var parsed = Integer.parseInt("42");
        var boxed = Integer.valueOf(42);
        System.out.println("parsed=" + parsed);
        System.out.println("boxedType="
                + boxed.getClass().getSimpleName());

        var names = new ArrayList<String>();
        names.add("Ada");
        System.out.println("names=" + names);

        var mixed = new ArrayList<>();
        mixed.add("text");
        mixed.add(1);
        System.out.println("mixed=" + mixed);

        var data = new int[]{1, 2, 3};
        System.out.println("arrayLength=" + data.length);

        final var enabled = true;
        System.out.println("enabled=" + enabled);
    }
}
~~~

Mở PowerShell (Windows) hoặc Bash (Linux/macOS) trong thư mục chứa file, dùng **JDK 17 trở lên**:

~~~bash
java -version
javac -version
javac -encoding UTF-8 VarNeedsInitializerDemo.java
java VarNeedsInitializerDemo
~~~

**Expected output chính xác của `java VarNeedsInitializerDemo`:**

~~~text
count=3
price=2.5
title=Java 17
sum=12
indices=012
typedNull=true
typedNullAfterSet=ready
methodReturnedNull=true
methodReturnedText=Guide
explicitTypeLater=assigned
parsed=42
boxedType=Integer
names=[Ada]
mixed=[text, 1]
arrayLength=3
enabled=true
~~~

Đầu ra của `java -version` và `javac -version` phụ thuộc môi trường, không được tính trong khối expected output.

### Giải thích từng trường hợp

- `var count = readCount();` suy ra `int` từ **khai báo kiểu trả về** của `readCount()`. Compiler không cần thực thi phương thức.
- `var price = 2.5;` suy ra `double`; `var title = "Java";` suy ra `String`. Gán nối thêm `" 17"` vẫn hợp lệ vì kiểu biến là String.
- `var numbers = List.of(2, 4, 6);` suy ra `List<Integer>`; trong enhanced `for`, biến `number` nhận kiểu phần tử là `Integer`, rồi được unbox khi cộng vào `int sum`.
- `for (var i = 0; i < 3; i++)` có initializer `0`, nên `i` là `int`; các giá trị lặp là 0, 1, 2.
- `var typedNull = (String) null;` hợp lệ: biểu thức có kiểu `String` mặc dù **giá trị** là null. `var fromMethod = findTitle(false);` cũng hợp lệ vì lời gọi có kiểu `String`.
- `String declaredLater;` hợp lệ vì kiểu khai báo đã rõ. Nếu đọc `declaredLater` trước khi gán, compiler vẫn báo lỗi *variable might not have been initialized*; **có kiểu** không đồng nghĩa **đã có giá trị**.
- `parsed` suy ra `int` từ `parseInt`; `boxed` suy ra `Integer` từ `valueOf`. Java không suy ra wrapper từ primitive chỉ vì hai kết quả đều hiển thị là 42.
- `names` suy ra `ArrayList<String>`. Với `var mixed = new ArrayList<>();` không có kiểu đích generic tường minh nên trường hợp này diamond suy ra `Object`, tạo biến `ArrayList<Object>`.
- `var data = new int[]{1, 2, 3};` có initializer mảng mang kiểu `int[]`. `final var enabled = true;` có kiểu `boolean` và không cho phép gán lại biến.

## 4. Tại sao `var x = null` không hợp lệ, nhưng null có thể là kết quả?

Phân biệt **kiểu của biểu thức** và **giá trị của biểu thức**:

~~~java
// var a = null; // Sai: null literal khong co kieu cu the.

var b = (String) null; // Dung: b co kieu String.
String c = null;       // Dung: kieu viet ro.
~~~

Ví dụ phương thức:

~~~java
static String loadName() {
    return null;
}

// Trong mot phuong thuc:
var name = loadName(); // Dung: suy ra String tu chu ky loadName.
~~~

Khi chạy, `name` vẫn mang `null`; gọi `name.length()` sẽ ném `NullPointerException`. **Dùng `var` không bỏ qua kiểm tra null** và cũng không tạo đối tượng String thay cho null.

Một vài biểu thức khác không có kiểu đích rõ ràng cũng không hợp lệ ngay sau `var`:

~~~java
// var f = x -> x + 1;     // Lambda can target functional interface.
// var maker = String::new; // Method reference can target type.
// var a = {1, 2, 3};      // Dạng initializer mang rut gon can target array.
~~~

Cách viết rõ kiểu hợp lệ:

~~~java
java.util.function.IntUnaryOperator f = x -> x + 1;
java.util.function.Supplier<String> maker = String::new;
var a = new int[]{1, 2, 3};
~~~

Với `var`, compiler không nhận được kiểu đích viết bên trái để dùng cho các biểu thức *poly expression* như lambda hoặc method reference. Dùng kiểu tường minh cho functional interface thường rõ ý hơn.

## 5. Không thể suy kiểu từ phép gán viết **sau** khai báo

~~~java
// var points;
// points = 10;
~~~

Đoạn trên không hợp lệ. Compiler phải xác định kiểu **ở vị trí khai báo** để kiểm tra phần còn lại của chương trình. Nó không “đọc ngược” phép gán phía sau để suy ra kiểu cho khai báo thiếu initializer.

Nếu muốn gán sau, phải cung cấp kiểu:

~~~java
int points;
points = 10;
System.out.println(points);
~~~

Và còn phải thỏa *definite assignment*: một biến cục bộ có kiểu tường minh nhưng chưa được gán không thể bị đọc tùy ý.

Cũng không thể nhờ giá trị thay đổi về sau để “nâng kiểu”:

~~~java
var points = 10;  // int.
points = 20;      // Hop le.
// points = 20L;  // Loi: long khong tu dong thu hep thanh int.
~~~

## 6. Ngoại lệ về **nguồn thông tin kiểu**: enhanced `for` và lambda

Phát biểu “`var` cần initializer” thường được dùng cho khai báo biến cục bộ đơn giản (`var name = expression;`). Tuy nhiên Java còn cho phép `var` ở các vị trí **không có phép gán khởi tạo tường minh ngay tại khai báo** vì compiler lấy kiểu từ ngữ cảnh.

### Biến phần tử trong enhanced `for`

~~~java
int[] values = {2, 4, 6};
for (var value : values) {
    System.out.println(value);
}
~~~

`value` được suy kiểu `int` từ **kiểu phần tử của mảng `int[]`**. Dấu `:` của enhanced `for` cung cấp nguồn suy kiểu, nên không cần viết `var value = ...`.

### Tham số lambda trong Java 17

~~~java
java.util.function.BinaryOperator<Integer> add =
        (var left, var right) -> left + right;
~~~

Hai tham số được suy là `Integer` nhờ **target type** `BinaryOperator<Integer>`. Đây là dạng `var` cho lambda, không phải khai báo biến cục bộ thông thường và không có initializer đi kèm từng tham số.

Khi dùng `var` trong danh sách tham số lambda, **tất cả tham số phải dùng `var`**; không trộn `var` với dạng viết kiểu tường minh hoặc dạng bỏ kiểu trong cùng danh sách.

### Các trường hợp khác

`var` được dùng trong initializer của `for` cổ điển, ví dụ `for (var i = 0; ... )`, và trong khai báo resource của `try-with-resources` như `try (var reader = new StringReader("hi")) { ... }`. Các trường hợp này **vẫn có biểu thức khởi tạo** khi dùng cách khai báo bằng `var`.

**Điểm chung:** compiler phải có **nguồn thông tin kiểu tại chỗ**. Nguồn đó là initializer đối với local variable thông thường, kiểu phần tử với enhanced `for`, hoặc target type đối với lambda.

## 7. Những lỗi biên dịch dễ gặp và cách sửa

| Ví dụ không hợp lệ | Nguyên nhân | Cách sửa |
| --- | --- | --- |
| `var x;` | Không có initializer để suy kiểu | `int x;` hoặc `var x = 0;` |
| `var x = null;` | Null literal không xác định kiểu cụ thể | `String x = null;` hoặc `var x = (String) null;` |
| `var x = {1, 2};` | Array initializer ngắn không có kiểu đích | `var x = new int[]{1, 2};` |
| `var f = x -> x + 1;` | Lambda cần target functional interface | `IntUnaryOperator f = x -> x + 1;` |
| `var x = x;` | Không có giá trị hợp lệ để dùng trong chính initializer | Khởi tạo từ biểu thức độc lập, ví dụ `var x = 1;` |
| `var a = 1, b = 2;` | Không được khai báo nhiều biến bằng một local `var` declarator | Tách `var a = 1; var b = 2;` |
| `var x = 1; x = "one";` | `x` đã được suy ra `int` | Chọn kiểu khác ngay từ khai báo nếu cần |

Ngoài ra, `var` **không được dùng làm kiểu trả về phương thức, field của lớp hoặc kiểu tham số phương thức thường**. Đây là giới hạn vị trí cú pháp, độc lập với việc có biểu thức khởi tạo hay không.

## 8. Khi nào nên viết `var`, khi nào nên viết kiểu tường minh?

**Ưu tiên `var`** khi biểu thức khởi tạo làm kiểu đủ rõ:

~~~java
var names = new java.util.ArrayList<String>();
var length = "Java".length();
~~~

**Ưu tiên kiểu tường minh** khi cần để trống biến đến lúc có dữ liệu, cần chủ động chọn interface/superclass làm kiểu biến, hoặc muốn có target type giúp suy generic:

~~~java
String name;
name = readName();

java.util.List<String> names = new java.util.ArrayList<>();
~~~

Với `var list = new java.util.ArrayList<>();`, diamond `<>` không nhận được ràng buộc phần tử từ tên biến bên trái, nên trường hợp này suy ra `ArrayList<Object>`. Ghi `ArrayList<String>` ở initializer hoặc dùng `List<String>` bên trái nếu muốn ràng buộc phần tử String.

`var` là công cụ giảm lặp tên kiểu, **không nên dùng để che giấu kiểu khó hiểu**. Tên biến, phương thức khởi tạo và kiểu dữ liệu phải cùng giúp người đọc suy luận chính xác.

## 9. Bài tập tự kiểm tra

1. **Thiếu initializer:** Vì sao `var score;` không biên dịch nhưng `int score;` hợp lệ?  
   *Đáp án:* `int` cung cấp kiểu tĩnh; `var` cần initializer để suy kiểu của local variable thông thường.
2. **Null:** `var text = null;` khác `var text = (String) null;` thế nào?  
   *Đáp án:* Câu đầu không có kiểu cụ thể để suy; câu sau suy `String` từ cast, dù giá trị là null.
3. **Phương thức trả null:** `static String f() { return null; }` thì `var x = f();` có hợp lệ không?  
   *Đáp án:* Có. Kiểu tĩnh của biểu thức gọi `f()` là `String`.
4. **Không dùng giá trị về sau:** `var x; x = 5;` có hợp lệ không?  
   *Đáp án:* Không; suy kiểu phải xảy ra tại khai báo, không tìm kiểu từ phép gán phía sau.
5. **Hai literal:** `var n = 10;` và `var n = 10L;` lần lượt có kiểu gì?  
   *Đáp án:* `int` và `long`.
6. **Enhanced for:** `for (var c : "ABC".toCharArray())` suy ra `c` kiểu gì dù không có `= initializer`?  
   *Đáp án:* `char` từ kiểu phần tử của mảng.
7. **Lambda:** Vì sao `BinaryOperator<Integer> add = (var a, var b) -> a + b;` hợp lệ?  
   *Đáp án:* Target type `BinaryOperator<Integer>` cung cấp kiểu tham số.

## Checklist

- [ ] `var` dùng để **suy luận kiểu tĩnh**, không phải kiểu động.
- [ ] Với local variable khai báo thông thường, `var` **cần biểu thức khởi tạo** cùng câu lệnh.
- [ ] `var x = null` không hợp lệ; `var x = (String) null` hoặc lời gọi phương thức trả `String` có thể hợp lệ.
- [ ] Kiểu được suy từ **kiểu biểu thức**, không từ giá trị runtime hoặc phép gán phía sau.
- [ ] Không dùng array initializer dạng ngắn, lambda hay method reference trần nếu không có target type.
- [ ] Enhanced `for` suy kiểu từ phần tử, lambda suy kiểu tham số từ target functional interface.
- [ ] Viết kiểu tường minh nếu muốn khai báo trước rồi gán sau, hoặc cần kiểm soát interface/generic target type.

**Kết luận:** `var` cần biểu thức khởi tạo ở khai báo biến cục bộ thông thường vì compiler **phải suy ra kiểu ngay tại nơi khai báo**. Khi một ngữ cảnh khác cung cấp thông tin kiểu (enhanced `for`, lambda), Java có quy tắc riêng; điều không thay đổi là **`var` luôn tham gia hệ thống kiểu tĩnh**, không chờ dữ liệu runtime để đoán kiểu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="var-type-inference-005">Không suy ra kiểu từ null hoặc danh sách khởi tạo mảng đứng riêng</a>

<details>
<summary>Xem chi tiết</summary>

# Không suy ra kiểu từ `null` hoặc danh sách khởi tạo mảng đứng riêng với `var`

> **Java core — language-basics / Suy luận với var**  
> **Phiên bản:** Java 17+ (các ví dụ `var` cục bộ yêu cầu từ Java 10).  
> **Mục tiêu:** Hiểu vì sao `var x = null` và `var values = {1, 2}` không biên dịch; nắm sự khác biệt giữa **giá trị khởi tạo** và **ngữ cảnh kiểu đích**, từ đó viết khai báo đúng mà vẫn sử dụng `var` khi biểu thức khởi tạo đã có kiểu xác định.

## 1. `var` cần một kiểu suy ra được tại thời điểm biên dịch

`var` cho phép trình biên dịch suy ra **kiểu tĩnh** của biến cục bộ từ biểu thức khởi tạo. Nó **không** phải kiểu dữ liệu động, không tự chọn `Object`, và không chờ đến runtime để đoán kiểu.

~~~java
var count = 10;           // int
var title = "Java";       // String
var numbers = new int[3]; // int[]
~~~

Sau khai báo, kiểu của biến đã cố định. Ví dụ `count = "ten";` không hợp lệ vì `count` là `int`.

Hai cách viết sau **không hợp lệ** trong Java 17:

~~~java
// KHÔNG BIÊN DỊCH:
var unknown = null;
var scores = {10, 20, 30};
~~~

Đây không phải lỗi do nội dung `null` hay số 10, 20, 30 “xấu”, mà vì **không có đủ thông tin để tạo kiểu khai báo hợp lệ** cho `var`.

## 2. Vì sao `var x = null` không hợp lệ?

`null` là giá trị có thể gán cho nhiều **kiểu tham chiếu** khác nhau như `String`, `Integer`, `int[]`, `Object`. Chỉ nhìn literal `null`, Java không thể suy ra nên khai báo biến với kiểu nào.

~~~java
// var name = null;  // Lỗi biên dịch: không thể suy ra kiểu từ null.
~~~

Khi kiểu đã được chỉ rõ, việc khởi tạo `null` hoàn toàn hợp lệ:

~~~java
String name = null;
Integer quantity = null;
int[] data = null;
~~~

Có thể cung cấp kiểu cho **chính biểu thức khởi tạo**, khiến `var` suy ra được:

~~~java
var name = (String) null;     // String
var quantity = (Integer) null; // Integer
var data = (int[]) null;      // int[]
~~~

Dù đúng cú pháp, viết `String name = null;` **thường rõ ràng hơn** `var name = (String) null;`. Không nên lạm dụng cast chỉ để né yêu cầu khai báo kiểu tường minh.

**Phân biệt:** Các cách trên chỉ tạo **tham chiếu bằng null**; chúng không tạo object `String`, `Integer` hay mảng. Gọi `name.length()` khi `name == null` vẫn gây `NullPointerException`.

## 3. Tại sao `var scores = {1, 2, 3}` không hợp lệ?

Cặp ngoặc nhọn đứng riêng `{1, 2, 3}` trong ngữ cảnh này là **array initializer**, không phải một biểu thức tự mang kiểu mảng hoàn chỉnh. Java cho phép dạng rút gọn này khi **khai báo đã có kiểu mảng rõ ràng**:

~~~java
int[] scores = {1, 2, 3};        // Hợp lệ: kiểu int[] đã có.
String[] labels = {"A", "B"};    // Hợp lệ: kiểu String[] đã có.
~~~

Còn ở câu `var scores = {1, 2, 3};`, không có kiểu mảng đích để quyết định array initializer; cú pháp này không được chấp nhận.

Hãy tạo **biểu thức tạo mảng có kiểu tường minh**:

~~~java
var scores = new int[] {1, 2, 3};     // Suy ra int[].
var labels = new String[] {"A", "B"}; // Suy ra String[].
var empty = new int[0];              // Suy ra int[].
~~~

`new int[] {1, 2, 3}` đã là **một biểu thức** với kiểu `int[]`, nên `var` có thể suy ra. Trái lại, `{1, 2, 3}` đứng một mình không phải biểu thức khởi tạo hợp lệ để dùng cùng `var`.

## 4. Bảng so sánh cú pháp hợp lệ và không hợp lệ

| Cách viết | Biên dịch? | Lý do hoặc kiểu suy ra |
| --- | --- | --- |
| `var a = null;` | **Không** | Literal `null` không xác định kiểu |
| `String a = null;` | Có | Kiểu `String` đã khai báo rõ |
| `var a = (String) null;` | Có | Kiểu suy ra `String` từ cast |
| `var a = {1, 2};` | **Không** | Array initializer không có kiểu đích |
| `int[] a = {1, 2};` | Có | Kiểu mảng `int[]` khai báo rõ |
| `var a = new int[] {1, 2};` | Có | Kiểu suy ra `int[]` từ array creation |
| `var a = new int[2];` | Có | Biểu thức có kiểu `int[]` |
| `var a = new String[] {null, "A"};` | Có | Kiểu mảng `String[]` đã xác định |
| `var grid = new int[][] {{1}, {2, 3}};` | Có | Kiểu suy ra `int[][]` |
| `var a = valueReturningString();` | Có nếu method khai báo trả `String` | Kiểu `String` suy ra từ **kiểu trả về**, dù giá trị runtime là `null` |

**Lưu ý:** Mảng có thể chứa phần tử `null` nếu đó là mảng tham chiếu. Ví dụ `new String[] {null, "A"}` vẫn có kiểu `String[]` rõ ràng; nó khác hoàn toàn với `var a = null;`.

## 5. Dữ liệu nhận về `null` vẫn có thể dùng `var`

Điểm quan trọng là **kiểu tĩnh của biểu thức**, không phải giá trị runtime:

~~~java
static String lookupName() {
    return null;
}

// Trong phương thức khác:
var name = lookupName(); // Hợp lệ: compiler biết kiểu trả về là String.
~~~

`name` có **kiểu `String`**, nhưng giá trị hiện tại bằng `null`. Java không hề phải đoán kiểu từ literal null ở đây; kiểu đã nằm trong **khai báo phương thức** `lookupName()`.

Cũng có thể suy ra kiểu từ biểu thức điều kiện nếu quy tắc kiểu của biểu thức đã xác định được:

~~~java
var selected = true ? null : "fallback"; // String, giá trị null.
var other = false ? null : "fallback";   // String, giá trị "fallback".
~~~

Ở đây sự hiện diện của nhánh `"fallback"` cung cấp kiểu tham chiếu `String` phù hợp cho biểu thức ba ngôi. Nhưng `var selected = null;` tự nó vẫn không hợp lệ.

Không nên coi cú pháp này là cách né kiểm tra null: nếu `selected == null`, gọi `selected.length()` vẫn gây `NullPointerException`.

## 6. Chương trình Java 17+ runnable

Tạo file **VarNullArrayDemo.java** với toàn bộ nội dung:

~~~java
public final class VarNullArrayDemo {
    private static String lookupName() {
        return null;
    }

    private static String describe(String text) {
        return text == null ? "(missing)" : text;
    }

    public static void main(String[] args) {
        String explicitNull = null;
        var typedNull = (String) null;
        var fromMethod = lookupName();

        System.out.println("explicitNull=" + describe(explicitNull));
        System.out.println("typedNull=" + (typedNull == null));
        System.out.println("fromMethodNull=" + (fromMethod == null));

        int[] compact = {1, 2, 3};
        var inferred = new int[] {1, 2, 3};
        var empty = new int[0];

        System.out.println("compactSum="
                + (compact[0] + compact[1] + compact[2]));
        System.out.println("inferredLength=" + inferred.length);
        System.out.println("inferredSecond=" + inferred[1]);
        System.out.println("emptyLength=" + empty.length);

        var labels = new String[] {null, "OK"};
        System.out.println("labelsLength=" + labels.length);
        System.out.println("firstLabelNull=" + (labels[0] == null));
        System.out.println("secondLabel=" + labels[1]);

        var grid = new int[][] {{1, 2}, {3, 4}};
        System.out.println("gridElement=" + grid[1][0]);

        var selected = true ? null : "fallback";
        var alternate = false ? null : "fallback";
        System.out.println("selectedNull=" + (selected == null));
        System.out.println("alternate=" + alternate);
    }
}
~~~

Biên dịch và chạy tại thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 VarNullArrayDemo.java
java VarNullArrayDemo
~~~

**Expected output chính xác:**

~~~text
explicitNull=(missing)
typedNull=true
fromMethodNull=true
compactSum=6
inferredLength=3
inferredSecond=2
emptyLength=0
labelsLength=2
firstLabelNull=true
secondLabel=OK
gridElement=3
selectedNull=true
alternate=fallback
~~~

Giải thích:

1. `explicitNull` có kiểu `String` tường minh; `typedNull` suy ra `String` nhờ cast; `fromMethod` suy ra `String` nhờ kiểu trả về của method. Cả ba đều có thể giữ giá trị `null` tại runtime.
2. `compact` là mảng `int[]` hợp lệ vì khai báo đã nêu kiểu; `inferred` và `empty` cũng là `int[]` vì biểu thức `new int[]`/`new int[0]` cung cấp kiểu.
3. `labels` có kiểu `String[]` bất kể phần tử đầu bằng `null`. Mảng `grid` có kiểu `int[][]` và giá trị ở `grid[1][0]` là 3.
4. Hai biểu thức ba ngôi có kiểu `String` nhưng chọn các nhánh khác nhau nên một kết quả bằng `null`, một kết quả là `"fallback"`.

## 7. Sửa lỗi khai báo một cách có chủ đích

Khi cần khởi tạo **null**:

~~~java
// Sai:
// var token = null;

// Đúng và rõ ràng:
String token = null;

// Hợp lệ nhưng thường kém rõ hơn:
var castToken = (String) null;
~~~

Khi cần khởi tạo **mảng**:

~~~java
// Sai:
// var points = {4, 8};

// Đúng nếu muốn dùng dạng { ... } rút gọn:
int[] points = {4, 8};

// Đúng nếu muốn dùng var:
var otherPoints = new int[] {4, 8};
~~~

Đừng nhầm array initializer với *collection literal*: Java 17 không có cú pháp `var numbers = {1, 2};` để tự suy ra `List<Integer>` hay `int[]`. Nếu muốn `List`, dùng API phù hợp như `List.of(1, 2)` (trường hợp cần list bất biến) và hiểu rõ kiểu trả về cùng đặc tính của collection.

Khi review code, tự hỏi **“Biểu thức khởi tạo này có kiểu tĩnh xác định được không?”**. Nếu không, viết **kiểu khai báo tường minh** hoặc dùng **biểu thức tạo object/mảng đã nêu kiểu** thay vì cố buộc `var` phải đoán.

## 8. Các bẫy cần tránh

| Hiểu lầm | Điều đúng trong Java 17 |
| --- | --- |
| “`var` luôn suy ra `Object` khi không biết kiểu.” | Sai: compiler từ chối nếu không suy ra được kiểu hợp lệ |
| “Giá trị `null` ở runtime khiến `var` lỗi.” | Sai: `var x = lookupName();` hợp lệ nếu return type rõ |
| “`var x = (String) null;` tạo đối tượng String.” | Sai: tham chiếu vẫn `null` |
| “`{1, 2}` đứng riêng đã là `int[]`.” | Sai: cần kiểu mảng đích hoặc cú pháp `new int[] {...}` |
| “`new String[] {null}` không thể suy ra kiểu.” | Sai: `String[]` có sẵn trong biểu thức array creation |
| “Dùng `var` thì có thể gán lại bất kỳ kiểu nào.” | Sai: kiểu đã cố định ở compile time |
| “`new int[] {1, 2}` là `List<Integer>`.” | Sai: nó là mảng primitive `int[]` |

## 9. Bài tập tự kiểm tra

**Bài 1.** Giải thích riêng nguyên nhân hai dòng `var a = null;` và `var b = {1, 2};` không hợp lệ. Chúng có cùng loại thiếu thông tin kiểu hay không?

**Bài 2.** Sửa `var name = null;` theo hai cách: khai báo kiểu tường minh, hoặc tạo một biểu thức initializer có kiểu `String`. Cách nào rõ hơn khi code thật?

**Bài 3.** Sửa `var values = {2, 4, 6};` theo hai cách: dùng `int[]` với array initializer rút gọn, hoặc dùng `var` với `new int[]`.

**Bài 4.** Viết `var names = new String[] {null, "A"};`. Biến `names` có kiểu gì? `names[0] == null` cho kết quả gì? Câu trả lời có liên quan gì tới việc suy ra kiểu từ literal null?

**Bài 5.** Viết method trả `String` nhưng đôi khi trả `null`, rồi khai báo `var result = method();`. Vì sao dòng này vẫn biên dịch được dù giá trị trả về có thể vắng mặt?

### Ghi nhớ

**`var` cần một biểu thức khởi tạo có kiểu tĩnh suy ra được.** Literal `null` đứng một mình không cung cấp kiểu; array initializer `{...}` đứng một mình cần kiểu mảng đích nên không hợp lệ với `var`. Dùng `String x = null;` hoặc `var x = (String) null;` khi cần tham chiếu null có kiểu, và dùng `int[] x = {...};` hoặc `var x = new int[] {...};` khi cần mảng. `var` chỉ thay cách **viết khai báo**, không làm Java thành ngôn ngữ kiểu động.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="var-type-inference-006">Không dùng var cho trường hoặc kiểu trả về phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Không dùng `var` cho trường hoặc kiểu trả về phương thức — Java 17+

## 1. Mục tiêu và câu trả lời ngắn

Trong Java 17+, `var` hỗ trợ **suy luận kiểu biến cục bộ** (*local-variable type inference*), chứ không phải cú pháp thay thế kiểu khai báo ở mọi vị trí trong chương trình.

**Hai quy tắc trọng tâm:**

- **Không dùng `var` để khai báo field** (*trường dữ liệu*) của class, dù là field instance, `static` hay `final`.
- **Không dùng `var` làm kiểu trả về của method**, dù method có `return` một biểu thức mà compiler có thể xác định kiểu.

~~~java
public final class Counter {
    private int value = 0; // Đúng: field có kiểu int tường minh

    public int getValue() { // Đúng: kiểu trả về int tường minh
        var snapshot = value; // Đúng: var khai báo biến cục bộ trong thân method
        return snapshot;
    }
}
~~~

Các dòng sau **không phải cú pháp Java hợp lệ**:

~~~java
// private var value = 0;       // SAI: var không khai báo field
// public var getValue() {      // SAI: var không là return type
//     return 0;
// }
~~~

`var` không biến Java thành ngôn ngữ kiểu động. Compiler vẫn suy ra một **kiểu tĩnh cụ thể** cho biến cục bộ tại nơi khai báo và kiểm tra kiểu trong các câu lệnh sau.

## 2. Field, local variable và method return type khác nhau

Ba khái niệm rất dễ nhầm khi học cú pháp:

| Thành phần | Nằm ở đâu? | Kiểu khai báo |
|---|---|---|
| **Instance field** | Trong class, ngoài thân method/constructor/block cục bộ | Viết tên kiểu tường minh |
| **Static field** | Trong class, có `static` | Viết tên kiểu tường minh |
| **Local variable** | Trong method, constructor hoặc khối lệnh hợp lệ | Có thể dùng `var` nếu đủ điều kiện |
| **Method return type** | Trước tên method trong chữ ký | Viết kiểu trả về, hoặc `void` |
| **Method parameter** | Trong `(...)` của khai báo method | Viết kiểu tham số tường minh |

Ví dụ:

~~~java
final class Account {
    private final String owner;       // Field: String
    private static int nextId = 1;    // Static field: int

    Account(String owner) {            // Tham số constructor: String
        var normalized = owner.trim(); // Local variable: String
        this.owner = normalized;
    }

    String getOwner() {                // Method return: String
        var result = owner;            // Local variable: String
        return result;
    }
}
~~~

Trong ví dụ, compiler tự xác định kiểu của `normalized` và `result` từ biểu thức khởi tạo. Nhưng cú pháp của **field** và **chữ ký method** vẫn bắt buộc nêu tên kiểu cụ thể.

**Một class có thể chứa đồng thời khai báo kiểu tường minh và `var`**: mỗi thứ thuộc phạm vi hợp lệ riêng, không mâu thuẫn nhau.

## 3. Sai ở field: initializer không làm `var` hợp lệ

Kể cả field có giá trị khởi tạo hiển nhiên, cũng **không thể** viết:

~~~java
// KHÔNG ĐƯỢC CHÈN VÀO CLASS ĐỂ BIÊN DỊCH
// class Wrong {
//     private var name = "Java";
//     static var attempts = 0;
//     final var enabled = true;
// }
~~~

Không hợp lệ vì `var` không được phép đứng tại vị trí tên kiểu của field. Tính từ `private`, `static` hoặc `final` không thay đổi giới hạn này.

Cách đúng:

~~~java
final class Settings {
    private String name = "Java";
    private static int attempts = 0;
    private final boolean enabled = true;
}
~~~

**Không tự suy luận field từ giá trị khởi tạo** trong Java 17. Compiler biết `"Java"` có kiểu `String`, nhưng Java không định nghĩa cú pháp `var` cho field dựa trên thông tin ấy.

### Nếu field nhận object generic thì sao?

~~~java
import java.util.ArrayList;
import java.util.List;

final class Group {
    private final List<String> members = new ArrayList<>();
}
~~~

Ở đây Java cho phép **diamond `<>`** ở phía phải nhờ **kiểu đích tường minh** `List<String>` bên trái. Đây không phải khai báo field bằng `var`.

Với code:

~~~java
// private final var members = new ArrayList<String>(); // SAI
~~~

vẫn sai cú pháp dù initializer đã nêu `ArrayList<String>`.

**Lợi ích thiết kế:** khai báo `List<String>` làm field cho thấy code dựa vào abstraction `List`, thay vì khóa API vào lớp triển khai `ArrayList`.

## 4. Sai ở return type: `return` không thay thế khai báo kiểu

Java yêu cầu method khai báo kiểu trả về ở chữ ký:

~~~java
static int sum(int a, int b) {
    return a + b;
}
~~~

Không thể thay `int` bằng `var`:

~~~java
// static var sum(int a, int b) {
//     return a + b;
// }
~~~

Lý do: `var` là cơ chế suy luận cho **khai báo biến cục bộ ở những vị trí ngôn ngữ cho phép**, không phải cơ chế suy luận kiểu trả về của method từ tất cả nhánh `return`.

Một method không trả giá trị dùng `void`, **không dùng `var`**:

~~~java
static void logMessage(String message) {
    System.out.println(message);
}
~~~

### Vẫn được dùng `var` bên trong method trả giá trị

~~~java
static long square(int input) {
    var result = (long) input * input; // Suy ra long
    return result;                     // Kiểu trả về long tường minh
}
~~~

Ở đây `result` là biến cục bộ kiểu `long`; method `square` vẫn có chữ ký trả `long`.

### Phân biệt return expression và method signature

~~~java
static double average(int x, int y) {
    var total = x + y;   // int
    return total / 2.0;  // double
}
~~~

Một method hoàn toàn có thể chứa nhiều biến cục bộ được suy luận kiểu. Nhưng **hợp đồng trả về** vẫn được định nghĩa bằng kiểu `double` ở đầu chữ ký.

## 5. `var` trong thân constructor, instance initializer và static initializer

Không được khai báo field bằng `var`, nhưng bạn **được khai báo biến cục bộ bằng `var` trong khối lệnh** của class, ví dụ constructor:

~~~java
final class Product {
    private final String code;

    Product(String rawCode) {
        var normalized = rawCode.strip();
        this.code = normalized;
    }
}
~~~

Và trong **khối khởi tạo instance**:

~~~java
final class Metrics {
    private int total = 0;

    {
        var initialValue = 5; // Biến cục bộ trong initializer block
        total = initialValue;
    }
}
~~~

Tương tự trong **static initializer**:

~~~java
final class Configuration {
    private static final int DEFAULT_SIZE;

    static {
        var computed = 16 * 2; // Local variable kiểu int
        DEFAULT_SIZE = computed;
    }
}
~~~

**Điểm phân biệt vị trí:**

- `private var total = 0;` → sai vì đang khai báo field.
- Trong `{ var temporary = 0; }` → đúng nếu là một khối lệnh hợp lệ và có initializer.

Khối lệnh **không biến local variable thành field**. Biến `temporary` chỉ tồn tại trong phạm vi lexical của khối, không thể truy cập từ bên ngoài.

## 6. Điều kiện để suy kiểu local không cho phép suy kiểu field

Ở **vị trí local** hợp lệ, compiler suy kiểu từ initializer; ở **vị trí field** hay **chữ ký method**, dù có thể đoán kiểu từ biểu thức thì Java 17 vẫn đòi kiểu tường minh.

~~~java
var count = 1;                   // local: int
var list = new java.util.ArrayList<String>(); // local: ArrayList<String>
String nullable = null;          // Kiểu tham chiếu tường minh
// var absent = null;            // Local không suy được kiểu
// var missing;                  // Local thiếu initializer
~~~

Đừng trộn hai câu hỏi “**biểu thức có kiểu suy ra được không?**” và “**vị trí khai báo có cho dùng var không?**”. Biểu thức hợp lệ chưa đủ nếu đang khai báo field hoặc kiểu trả về.

## 7. `var` không thay đổi hợp đồng field hoặc method return

Ví dụ một lớp có giao diện đơn giản:

~~~java
final class Invoice {
    private final long cents;

    Invoice(long cents) {
        if (cents < 0) {
            throw new IllegalArgumentException("negative amount");
        }
        this.cents = cents;
    }

    long getCents() {
        var snapshot = cents; // long
        return snapshot;
    }

    String describe() {
        var label = "cents=";
        return label + cents;
    }
}
~~~

Những phần **ổn định trong API**:

- Field `cents` luôn khai báo `long`.
- Constructor nhận tham số `long`.
- `getCents()` khai báo trả `long`.
- `describe()` khai báo trả `String`.

Những phần có thể **rút gọn tại chỗ**:

- `snapshot`, `label` là biến cục bộ, có initializer phù hợp.

Đây là cách phối hợp `var` hợp lý: giữ kiểu rõ ở **biên class/API** và giảm lặp kiểu ở những điểm mà initializer cho thấy kiểu cụ thể.

### Tại sao kiểu trả về rõ lại quan trọng?

Caller có thể phụ thuộc vào chữ ký:

~~~java
long totalCents = invoice.getCents();
String text = invoice.describe();
~~~

Nếu tác giả method sửa biểu thức tính toán bên trong, chữ ký tường minh giúp compiler kiểm tra rằng giá trị trả về vẫn tương thích. Đây là một lớp bảo vệ hợp đồng API khi refactor.

## 8. Tham số phương thức, lambda và kiểu generic tại ranh giới API

**Tham số method** vẫn cần kiểu rõ: `static String format(int n)` hợp lệ, còn `format(var n)` không hợp lệ. Riêng tham số **lambda** từ Java 11 có thể dùng `var` khi lambda đã có target type:

~~~java
java.util.function.IntBinaryOperator add =
        (var left, var right) -> left + right;
System.out.println(add.applyAsInt(2, 3)); // 5
~~~

Một danh sách tham số lambda không được trộn `var` với tham số không ghi kiểu hoặc ghi kiểu tường minh. Ví dụ `var callback = x -> x + 1;` vẫn sai vì initializer là lambda thiếu functional target type.

Đối với generic, khai báo field/API nên thể hiện **mức abstraction mong muốn**:

~~~java
private final java.util.List<String> members =
        new java.util.ArrayList<>();
~~~

Ở đây `List<String>` là kiểu field được công bố; diamond nhận kiểu đích. Nếu trong local dùng `var bag = new java.util.ArrayList<>();`, kiểu suy ra là `ArrayList<Object>` trong tình huống này, không tự suy thành `ArrayList<String>` từ các lần `add` viết sau. Nội dung về vị trí hợp lệ và suy luận generic được nhắc ở đây để **phân biệt ranh giới API với local**, không phải thay thế chủ đề chuyên sâu của các bài khác.

## 9. Return type không tự đổi theo `var` trong method

Giả sử method khai báo trả `long` nhưng biểu thức bên trong có kiểu `int`:

~~~java
static long twice(int input) {
    var result = input * 2; // int; phép tính diễn ra ở int
    return result;          // Widening int -> long khi return
}
~~~

**Bẫy:** nếu `input` quá lớn, phép nhân `int` có thể overflow **trước khi** chuyển thành `long`. `var` không tự dự đoán ý định muốn tính kiểu rộng hơn.

~~~java
static long twiceSafe(int input) {
    var result = (long) input * 2; // long từ lúc nhân
    return result;
}
~~~

Với `input = 2_000_000_000`:

~~~java
System.out.println(twice(2_000_000_000));     // -294967296
System.out.println(twiceSafe(2_000_000_000)); // 4000000000
~~~

Cả hai method đều **trả `long` theo chữ ký**, nhưng **kiểu của phép tính trung gian** khác nhau. Đây là lý do quan trọng để hiểu kiểu mà compiler suy ra, không phải chỉ đọc chữ `var`.

### Compiler vẫn kiểm tra kiểu trả về

~~~java
static int count() {
    var text = "not an int";
    // return text; // Lỗi compile: String không chuyển thành int
    return 1;
}
~~~

`var` không cho phép “giấu” một giá trị trả về sai kiểu. Chữ ký method vẫn là hợp đồng được compiler kiểm tra.

## 10. Sửa đúng lỗi tại field và chữ ký method

| Vị trí viết sai | Vì sao sai? | Thay bằng |
| --- | --- | --- |
| `private var name = "Java";` | `var` không khai báo field | `private String name = "Java";` |
| `static var count = 1;` | `static` không làm field được suy kiểu | `static int count = 1;` |
| `public var name() { ... }` | Chữ ký method phải khai báo return type | `public String name() { ... }` |
| `void set(var x)` | Method parameter phải có kiểu | `void set(int x)` |
| `record Pair(var a, int b)` | Record component cần kiểu | `record Pair(int a, int b)` |

Nếu code **đã ở trong thân method/constructor/block**, `var local = expression;` có thể hợp lệ; đừng sửa field hoặc chữ ký method bằng cách di chuyển dữ liệu vào local mà làm mất hợp đồng bên ngoài.

## 11. Ví dụ Java 17 đầy đủ, độc lập

Lưu file đúng tên **`VarMemberReturnDemo.java`**:

~~~java
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;

public final class VarMemberReturnDemo {
    private static final int DEFAULT_LIMIT = 5; // Field: kiểu tường minh
    private static int created = 0;             // Static field: kiểu tường minh

    private final String label;                 // Instance field
    private final List<String> history = new ArrayList<>();

    static {
        var marker = "class initialized";      // Local trong static block
        if (marker.isEmpty()) {
            throw new IllegalStateException("empty marker");
        }
    }

    {
        var entry = "instance initialized";     // Local trong instance block
        history.add(entry);
    }

    VarMemberReturnDemo(String rawLabel) {
        var normalized = Objects.requireNonNull(rawLabel).strip();
        label = normalized;
        created++;
    }

    public String labelText() {                 // Return type tường minh
        var prefix = "Label:";
        return prefix + label;
    }

    public int sum(int left, int right) {        // Parameters/return rõ kiểu
        var result = left + right;
        return result;
    }

    public List<String> history() {              // Return interface List<String>
        return List.copyOf(history);
    }

    static long twice(int input) {               // Return long, tính int
        var result = input * 2;
        return result;
    }

    static long twiceSafe(int input) {           // Return long, tính long
        var result = (long) input * 2;
        return result;
    }

    public static void main(String[] args) throws Exception {
        var demo = new VarMemberReturnDemo("  Core  ");
        System.out.println("label=" + demo.labelText());
        System.out.println("history=" + demo.history());
        System.out.println("sum=" + demo.sum(2, 3));

        var count = DEFAULT_LIMIT; // int
        count += 2;
        System.out.println("local int=" + count);

        var title = "Java"; // String
        System.out.println("text length=" + title.length());

        var names = new ArrayList<String>();
        names.add("Lan");
        System.out.println("typed list=" + names);

        List<String> contract = new ArrayList<>();
        contract.add("An");
        System.out.println("interface list=" + contract);

        var inferredReturn = demo.history(); // List<String>
        System.out.println("return item=" + inferredReturn.get(0));

        var nullable = (String) null;
        System.out.println("typed null=" + (nullable == null));

        IntBinaryOperator add = (var a, var b) -> a + b;
        System.out.println("lambda vars=" + add.applyAsInt(3, 4));

        var increment = (IntUnaryOperator) (x -> x + 1);
        System.out.println("lambda target=" + increment.applyAsInt(5));

        for (var i = 0; i < 2; i++) {
            System.out.println("for=" + i);
        }

        try (var reader = new StringReader("Z")) {
            System.out.println("reader=" + (char) reader.read());
        }

        System.out.println("twice int first=" + twice(2_000_000_000));
        System.out.println("twice long first=" + twiceSafe(2_000_000_000));

        var alias = demo;
        System.out.println("same instance=" + (alias == demo));
        System.out.println("objects created=" + created);
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên; terminal đứng trong thư mục có file `VarMemberReturnDemo.java`. Không cần Maven, Gradle hay thư viện ngoài JDK.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 VarMemberReturnDemo.java
java -cp . VarMemberReturnDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\VarMemberReturnDemo.java
java -cp . VarMemberReturnDemo
~~~

**Exact expected output:**

~~~text
label=Label:Core
history=[instance initialized]
sum=5
local int=7
text length=4
typed list=[Lan]
interface list=[An]
return item=instance initialized
typed null=true
lambda vars=7
lambda target=6
for=0
for=1
reader=Z
twice int first=-294967296
twice long first=4000000000
same instance=true
objects created=1
~~~

### Tại sao code này đúng phạm vi `var`?

1. Các field `DEFAULT_LIMIT`, `created`, `label`, `history` đều ghi **kiểu tường minh**.
2. Method `labelText`, `sum`, `history`, `twice`, `twiceSafe`, `main` đều có **return type tường minh**, gồm cả `void`.
3. Các biến `marker`, `entry`, `normalized`, `prefix`, `result` là **local** trong những khối lệnh hợp lệ.
4. `IntBinaryOperator add = (var a, var b) -> ...` hợp lệ vì lambda có target type.
5. `var nullable = (String) null` có kiểu `String` vì cast cung cấp kiểu cho initializer.
6. `var inferredReturn = demo.history()` suy ra `List<String>` theo **kiểu trả về đã khai báo của method**, không phải do compiler nhìn vào runtime object.
7. `twice` và `twiceSafe` cùng trả `long`, nhưng kiểu phép nhân trung gian khác nên kết quả khác — static typing vẫn kiểm soát.
8. `same instance=true` vì gán biến `alias` sao chép tham chiếu, không tạo object mới.

## 12. Bài tập có đáp án

### Bài A — sửa khai báo field

Đoạn sai:

~~~java
// final class Item {
//     private var id = 7;
// }
~~~

**Đáp án:**

~~~java
final class Item {
    private int id = 7;
}
~~~

### Bài B — sửa kiểu trả về method

Đoạn sai:

~~~java
// static var greeting() {
//     return "Hello";
// }
~~~

**Đáp án:**

~~~java
static String greeting() {
    return "Hello";
}
~~~

Có thể dùng local `var` trong thân method:

~~~java
static String greeting() {
    var text = "Hello";
    return text;
}
~~~

### Bài C — `var` trong constructor

~~~java
final class User {
    private final String name;

    User(String input) {
        var trimmed = input.trim();
        name = trimmed;
    }
}
~~~

**Đáp án:** hợp lệ. `trimmed` là biến cục bộ kiểu `String`; `name` là field khai báo tường minh. Nếu `input` có thể null, cần thêm validation theo hợp đồng.

### Bài D — `var` có làm method trả về kiểu mới không?

~~~java
static long compute(int x) {
    var doubled = x * 2;
    return doubled;
}
~~~

**Đáp án:** kiểu trả về method vẫn **`long`**; `doubled` có kiểu **`int`**. Phép nhân diễn ra trong `int`, sau đó kết quả mới chuyển rộng sang long. Với đầu vào lớn, có nguy cơ int overflow trước khi trả. Cách sửa nếu cần phép nhân long: `var doubled = (long) x * 2;`.

### Bài E — vị trí hợp lệ hay không?

Đánh giá:

~~~java
// private final var names = new ArrayList<String>();
// public var names() { return List.of("An"); }

static List<String> names() {
    var result = List.of("An");
    return result;
}
~~~

**Đáp án:** hai dòng comment là **không hợp lệ** do field và return type. Method `names()` hợp lệ, khai báo trả `List<String>` rõ ràng; `result` là local variable suy ra kiểu từ `List.of("An")`.

### Bài F — generic với field

Muốn field duy trì API `List<String>` nhưng tạo bằng `ArrayList`:

~~~java
private final java.util.List<String> names =
        new java.util.ArrayList<>();
~~~

**Đáp án:** hợp lệ và thể hiện abstraction. Không đổi thành `private final var names = ...` vì `var` không được khai báo field.

## 13. Checklist và kết luận

- [ ] Biết `var` chỉ là cú pháp suy luận kiểu ở **những khai báo cục bộ hợp lệ**.
- [ ] Không dùng `var` cho instance field, static field hoặc final field.
- [ ] Không dùng `var` ở vị trí return type của method.
- [ ] Không dùng `var` cho tham số method thông thường hay record component.
- [ ] Dùng kiểu tường minh để biểu diễn hợp đồng class/API.
- [ ] Biết local `var` hợp lệ trong method, constructor, instance/static initializer block.
- [ ] Biết `var` cần initializer có kiểu suy ra được; null literal trần không đủ.
- [ ] Biết `var` trong for, enhanced for, try-with-resources và lambda parameter có điều kiện.
- [ ] Hiểu cú pháp `var` không đổi kiểu khi gán lại.
- [ ] Nhớ generic, overload, toán tử và access tới member vẫn được **kiểm tra tĩnh**.
- [ ] Biết kiểu của initializer có thể gây overflow trước khi kết quả được return sang kiểu rộng.
- [ ] Chọn khai báo kiểu tường minh khi mức abstraction hoặc hợp đồng dữ liệu quan trọng.
- [ ] Biên dịch và chạy `VarMemberReturnDemo.java` bằng `javac`/`java`; so từng dòng expected output.

**Kết luận:** Trong Java 17+, `var` là tiện ích giảm lặp tên kiểu cho **biến cục bộ có initializer hợp lệ**. Nó **không phải kiểu dữ liệu**, nên không được dùng làm **kiểu field** hoặc **kiểu trả về method**. Hãy khai báo rõ kiểu ở biên class/API và dùng `var` trong thân khối lệnh khi initializer làm cho kiểu tĩnh dễ hiểu, dễ kiểm tra.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="var-type-inference-007">Kiểu suy ra từ literal và các phép tính số</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu suy ra từ literal và các phép tính số với `var` trong Java

> **Java core — language-basics / Suy luận với var**  
> **Môi trường:** JDK 17+; một tệp Java độc lập, chỉ dùng thư viện chuẩn.  
> **Mục tiêu:** Dự đoán chính xác **kiểu tĩnh** được suy ra cho `var` từ literal, phép toán số học, phép ép kiểu và biểu thức điều kiện; tránh lỗi chia nguyên, tràn số trước khi mở rộng và hiểu nhầm rằng `var` tự chọn kiểu nhỏ nhất.

## 1. `var` lấy kiểu của biểu thức, không đoán theo giá trị

Khi khai báo biến cục bộ bằng `var`, trình biên dịch suy luận kiểu từ **biểu thức khởi tạo tại thời điểm biên dịch**:

~~~java
var count = 10;     // int, không phải byte dù 10 rất nhỏ.
var distance = 10L; // long.
var ratio = 2.5;    // double.
var precise = 2.5f; // float.
var symbol = 'A';   // char.
~~~

`var` **không phải kiểu động**. Sau `var count = 10;`, biến `count` có kiểu `int` cố định; `count = 2.5;` không biên dịch vì không thể ngầm chuyển `double` sang `int`.

**Quy tắc thực hành:** Muốn dự đoán `var result = expression;`, trước tiên hãy xác định **kiểu của `expression` theo các quy tắc Java**. Kiểu suy ra chính là kiểu biểu thức đó.

## 2. Kiểu mặc định của literal số

| Literal | Kiểu của literal | Ghi chú |
| --- | --- | --- |
| `10`, `1_000`, `0xFF`, `0b1010` | `int` | Literal số nguyên không hậu tố mặc định là `int` nếu hợp lệ |
| `10L`, `10l` | `long` | Nên viết `L` hoa tránh nhầm `l` với chữ số 1 |
| `2.5`, `2.5d`, `1e3` | `double` | Literal số thực mặc định là `double` |
| `2.5f`, `2.5F` | `float` | Cần hậu tố `f` hoặc `F` để literal là `float` |
| `'A'` | `char` | Literal ký tự không phải `String` hay `int` |
| `(byte) 10`, `(short) 10` | `byte`, `short` | Cast quyết định kiểu của biểu thức khởi tạo |

~~~java
var a = 1_000;        // int.
var b = 1_000L;       // long.
var c = 1e3;          // double (giá trị 1000.0).
var d = 1e3f;         // float.
var e = (byte) 10;    // byte.
~~~

Đừng nhầm **kiểu** với **cách in** hoặc độ lớn giá trị: `1000` và `1000L` có cùng giá trị toán học nhưng khác kiểu. Literal thập phân nguyên lớn ngoài phạm vi `int` cần hậu tố thích hợp như `2147483648L`; viết `var n = 2147483648;` không khiến Java tự đoán thành `long`.

## 3. Phép tính quyết định kiểu của `var` trước khi gán

Với phép toán số học hai ngôi như `+`, `-`, `*`, `/`, `%`, Java áp dụng **binary numeric promotion** cho toán hạng trước khi tính:

1. Có `double` → kết quả kiểu `double`.
2. Nếu không, có `float` → kết quả kiểu `float`.
3. Nếu không, có `long` → kết quả kiểu `long`.
4. Còn lại → kết quả kiểu `int` (kể cả toán hạng `byte`, `short`, `char`).

| Biểu thức khởi tạo | Kiểu suy ra với `var` | Kết quả ví dụ |
| --- | --- | --- |
| `2 + 3` | `int` | `5` |
| `2 + 3L` | `long` | `5` |
| `2 + 3.5f` | `float` | `5.5` |
| `2L + 3.5` | `double` | `5.5` |
| `(byte) 10 + (short) 20` | `int` | `30` |
| `'A' + 1` | `int` | `66` |
| `7 / 2` | `int` | `3` |
| `7 / 2.0` | `double` | `3.5` |

Ví dụ:

~~~java
var sum = (byte) 10 + (short) 20; // int, không phải byte/short.
var afterChar = 'A' + 1;          // int, không phải char.
var mixed = 2 + 3L;               // long.
~~~

`var` không phải nguyên nhân khiến `byte + short` trở thành `int`: **chính quy tắc toán tử** quyết định kiểu đó. `var` chỉ dùng lại kiểu đã được xác định.

## 4. Bẫy chia số nguyên

~~~java
var whole = 7 / 2;       // int: 3, không phải 3.5.
var fraction = 7 / 2.0;  // double: 3.5.
var byCast = (double) 7 / 2; // double: 3.5.
~~~

Khi cả hai toán hạng của `/` là các kiểu số nguyên, Java thực hiện phép chia nguyên, bỏ phần lẻ theo hướng về 0. Khai báo `double result = 7 / 2;` vẫn cho **3.0**: phép chia nguyên **đã xảy ra trước** khi kết quả được chuyển thành `double` để gán.

Muốn chia số thực, đưa **ít nhất một toán hạng** sang kiểu số thực **trước dấu chia**, bằng literal `2.0` hoặc cast `(double) 7`.

Không dùng mẹo `var` để kỳ vọng Java tự tìm kiểu “đủ giữ phần lẻ”: `var` giữ đúng kiểu biểu thức, không sửa phép tính.

## 5. Bẫy tràn số trước khi mở rộng

Ngay cả khi biến đích là `long`, phép tính bằng `int` có thể tràn **trước lúc gán**:

~~~java
var overflow = Integer.MAX_VALUE + 1;    // int: -2147483648.
var exact = (long) Integer.MAX_VALUE + 1; // long: 2147483648.
var lateCast = (long) (Integer.MAX_VALUE + 1); // long: -2147483648.
~~~

- `Integer.MAX_VALUE + 1`: hai toán hạng đều `int`, phép cộng kiểu `int` bị tràn rồi `var` suy ra `int`.
- `(long) Integer.MAX_VALUE + 1`: cast làm **toán hạng** đầu thành `long` trước phép cộng; kết quả kiểu `long` không tràn.
- `(long) (Integer.MAX_VALUE + 1)`: phép cộng đã tràn trong ngoặc, sau đó mới cast giá trị sai sang `long`.

**Ghi nhớ:** Nếu cần kiểu rộng, làm một toán hạng rộng lên **trước phép tính**, không chỉ chuyển kết quả đã tính.

## 6. Biểu thức điều kiện và phép ép kiểu

Kiểu của phép ba ngôi `condition ? a : b` phụ thuộc quy tắc chọn kiểu của hai nhánh:

~~~java
var mixedConditional = true ? 1 : 2.0; // double, giá trị 1.0.
var castFirst = (float) 3;             // float, giá trị 3.0f.
~~~

Trong ví dụ đầu, hai nhánh `int` và `double` làm biểu thức có kiểu `double`, dù điều kiện luôn chọn nhánh `1`. `var` suy ra `double` **từ kiểu của biểu thức**, không suy từ nhánh được chạy.

**Lưu ý:** Java có quy tắc bổ sung cho biểu thức điều kiện số học (đặc biệt với literal hằng/kiểu nhỏ), nên không được áp dụng máy móc bảng phép cộng cho **mọi** dấu `?:`. Với biểu thức phức tạp, hãy xét quy tắc kiểu riêng của `?:` hoặc viết kiểu/cast tường minh nếu tính chính xác quan trọng.

## 7. Chương trình Java 17+ chạy được

Tạo tệp **VarNumericInferenceDemo.java**:

~~~java
public final class VarNumericInferenceDemo {
    private static String kind(byte x)   { return "byte"; }
    private static String kind(short x)  { return "short"; }
    private static String kind(char x)   { return "char"; }
    private static String kind(int x)    { return "int"; }
    private static String kind(long x)   { return "long"; }
    private static String kind(float x)  { return "float"; }
    private static String kind(double x) { return "double"; }

    public static void main(String[] args) {
        var integer = 12;
        var bigInteger = 12L;
        var singlePrecision = 1.25f;
        var doublePrecision = 1.25;
        var letter = 'A';

        System.out.println("literals=" + kind(integer)
                + "," + kind(bigInteger)
                + "," + kind(singlePrecision)
                + "," + kind(doublePrecision)
                + "," + kind(letter));

        var bytes = (byte) 10 + (short) 20;
        var afterChar = 'A' + 1;
        var longSum = 2 + 3L;
        var floatSum = 2 + 3.5f;
        var doubleSum = 2L + 3.5;

        System.out.println("smallSum=" + bytes + ":" + kind(bytes));
        System.out.println("charSum=" + afterChar + ":" + kind(afterChar));
        System.out.println("mixedKinds=" + kind(longSum)
                + "," + kind(floatSum) + "," + kind(doubleSum));

        var integerDivision = 7 / 2;
        var realDivision = 7 / 2.0;
        System.out.println("division=" + integerDivision
                + ":" + kind(integerDivision) + ","
                + realDivision + ":" + kind(realDivision));

        var overflow = Integer.MAX_VALUE + 1;
        var exact = (long) Integer.MAX_VALUE + 1;
        var lateCast = (long) (Integer.MAX_VALUE + 1);
        System.out.println("overflow=" + overflow + ":"
                + kind(overflow));
        System.out.println("exact=" + exact + ":" + kind(exact));
        System.out.println("lateCast=" + lateCast + ":" + kind(lateCast));

        var conditional = true ? 1 : 2.0;
        var narrow = (byte) 10;
        System.out.println("conditional=" + conditional + ":"
                + kind(conditional));
        System.out.println("explicitCast=" + narrow + ":"
                + kind(narrow));
    }
}
~~~

Biên dịch và chạy tại thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 VarNumericInferenceDemo.java
java VarNumericInferenceDemo
~~~

**Expected output chính xác:**

~~~text
literals=int,long,float,double,char
smallSum=30:int
charSum=66:int
mixedKinds=long,float,double
division=3:int,3.5:double
overflow=-2147483648:int
exact=2147483648:long
lateCast=-2147483648:long
conditional=1.0:double
explicitCast=10:byte
~~~

Các overload `kind(...)` giúp chương trình in **kiểu mà trình biên dịch chọn**, không suy luận kiểu qua `toString()` hay cách in số. Từ output có thể kiểm tra riêng kiểu biểu thức và giá trị thực tế.

## 8. Thói quen tốt và bài tập

| Điều cần quyết định | Gợi ý |
| --- | --- |
| Muốn `long` ngay từ literal | Viết hậu tố `L`, ví dụ `var n = 10L;` |
| Muốn `float` ngay từ literal | Dùng `f`/`F`, ví dụ `var f = 1.5f;` |
| Muốn phép chia có phần lẻ | Đổi toán hạng sang `double` trước phép chia |
| Muốn tổng không tràn `int` | Đưa toán hạng lên `long` trước phép cộng/nhân |
| Kiểu suy ra khó nhìn từ initializer | Dùng kiểu khai báo rõ ràng hoặc cast có chủ đích |
| So sánh các kết quả số thực | Xem xét độ chính xác và quy tắc làm tròn, đừng chỉ dựa vào hình thức literal |

**Bài 1.** Dự đoán kiểu của `var a = 7;`, `var b = 7L;`, `var c = 7f;` và `var d = 7.0;`. Mỗi biến có giá trị số học giống nhau không? Kiểu có giống nhau không?

**Bài 2.** Cho `var a = (byte) 5 + (short) 6;`. Kiểu của `a` là gì và vì sao không phải `byte`/`short`?

**Bài 3.** Tính `var quotient = 9 / 4;` và `var preciseQuotient = 9.0 / 4;`. Hai biến có kiểu và giá trị thế nào?

**Bài 4.** So sánh `var a = Integer.MAX_VALUE + 1;`, `var b = (long) Integer.MAX_VALUE + 1;` và `var c = (long) (Integer.MAX_VALUE + 1);`. Hãy chỉ rõ chỗ tràn xảy ra.

**Bài 5.** Cho `var result = false ? 1 : 2.0;`. Kiểu của `result` là gì? Có phụ thuộc nhánh nào chạy không?

### Ghi nhớ

**`var` không thay đổi luật tính số của Java: nó chỉ suy ra kiểu tĩnh từ biểu thức khởi tạo.** Literal mặc định `int` hoặc `double` nếu không có hậu tố; phép toán áp dụng numeric promotion; chia nguyên và tràn số xảy ra **trước khi** giá trị được gán. Khi cần chính xác, hãy chọn literal, cast và toán hạng đúng **trước phép tính**, thay vì trông chờ `var` tự chọn kiểu phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="var-type-inference-008">Chọn var khi kiểu vẫn rõ ràng với người đọc</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn `var` khi kiểu vẫn rõ ràng với người đọc — Java 17+

`var` cho phép Java **suy luận kiểu tĩnh của biến cục bộ** từ biểu thức khởi tạo. Lợi ích chính là giảm lặp tên kiểu, **không phải** biến Java thành ngôn ngữ kiểu động. Một khai báo tốt cần đồng thời đạt hai mục tiêu:

1. **Compiler biết kiểu chính xác** để kiểm tra mọi phép gán và lời gọi tiếp theo.
2. **Người đọc hiểu đủ nhanh** biến chứa loại dữ liệu gì, ý nghĩa nghiệp vụ ra sao, và các thao tác nào hợp lệ.

Hãy chọn `var` **khi biểu thức khởi tạo, tên biến và ngữ cảnh khiến kiểu đủ rõ**. Khi kiểu đích hoặc ý đồ thiết kế không rõ, khai báo tường minh thường tốt hơn.

~~~java
var student = new Student("An", 8);  // De thay la Student.
var names = new ArrayList<String>(); // De thay kieu phan tu String.
var retryCount = 3;                  // Don gian va ro rang: int.

// Can the hien y do API la interface:
List<String> visibleNames = new ArrayList<>();
~~~

Cả `var` và khai báo tường minh đều có chỗ đứng. **Không có quy tắc “luôn dùng” hoặc “luôn tránh” `var`** áp dụng cho mọi dự án.

## 1. Bảng quyết định nhanh

| Tình huống | Ví dụ | Gợi ý | Vì sao |
| --- | --- | --- | --- |
| Constructor cho biết lớp | `var user = new User("An");` | **Thường dùng `var`** | Tên kiểu hiện ngay tại initializer |
| Kiểu phần tử generic nêu rõ | `var names = new ArrayList<String>();` | **Có thể dùng `var`** | `String` không bị che khuất |
| Literal đơn giản | `var retries = 3;` | **Có thể dùng `var`** | Kiểu `int` dễ nhận biết |
| Kết quả phương thức khó đoán | `var result = load();` | **Cân nhắc viết kiểu** | Phải tra chữ ký `load()` mới hiểu kết quả |
| Muốn ràng buộc qua interface | `List<String> items = new ArrayList<>();` | **Viết kiểu tường minh** | Làm rõ API mà biến cam kết |
| Diamond thiếu kiểu phần tử | `var items = new ArrayList<>();` | **Nên tránh** | Có thể suy thành `ArrayList<Object>` |
| Giá trị số cần độ rộng cụ thể | `long bytes = 1_000_000;` | **Cân nhắc viết kiểu** | Tránh nhầm `int`, `long` hoặc số thực |
| Chuỗi gọi phương thức phức tạp | `var summary = pipeline.compute();` | **Viết rõ nếu giúp hiểu** | Tên phương thức không bảo đảm rõ kiểu |
| Biến phần tử trong vòng lặp | `for (var item : typedItems)` | **Thường dùng `var`** | Có thể suy kiểu từ collection/mảng |

“Rõ” là tiêu chí dành cho người đọc **tại vị trí khai báo**, không phải chỉ người đã thuộc toàn bộ mã nguồn.

## 2. Ví dụ Java 17+ biên dịch và chạy bằng CLI

Tạo file UTF-8 **`ReadableVarDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReadableVarDemo {
    record Student(String name, int points) {}

    static List<Student> loadStudents() {
        return List.of(
                new Student("An", 8),
                new Student("Binh", 9));
    }

    static String normalizeName(String text) {
        return text.strip().toUpperCase(Locale.ROOT);
    }

    public static void main(String[] args) {
        var retries = 3;
        var language = "Java";
        var first = new Student("Lan", 10);

        System.out.println("retries=" + retries);
        System.out.println("language=" + language);
        System.out.println("first=" + first.name()
                + ":" + first.points());

        var names = new ArrayList<String>();
        names.add("An");
        names.add("Binh");
        System.out.println("names=" + String.join(",", names));

        // Muon API bien la List, viet kieu interface tuong minh.
        List<String> publishedNames = new ArrayList<>();
        publishedNames.add("Lan");
        System.out.println("published=" + publishedNames);

        // Ten ham chua cho nguoi doc biet chac kieu tra ve.
        List<Student> students = loadStudents();
        var total = 0;
        for (var student : students) {
            total += student.points();
        }
        System.out.println("studentCount=" + students.size());
        System.out.println("totalPoints=" + total);

        // Chon String tường minh de bieu dat hop dong du lieu.
        String normalizedName = normalizeName("  an  ");
        System.out.println("normalized=" + normalizedName);

        // Kieu co the thay ngay qua literal va API tieu chuan.
        var uppercase = "java".toUpperCase(Locale.ROOT);
        System.out.println("uppercase=" + uppercase);

        var primitive = Integer.parseInt("7");
        var wrapper = Integer.valueOf("7");
        System.out.println("parsed=" + primitive);
        System.out.println("wrapperType="
                + wrapper.getClass().getSimpleName());

        // Diamond khong co kieu dich ro rang: suy ra Object.
        var mixed = new ArrayList<>();
        mixed.add("text");
        mixed.add(7);
        System.out.println("mixed=" + mixed);

        var positions = new StringBuilder();
        for (var i = 0; i < 3; i++) {
            positions.append(i);
        }
        System.out.println("positions=" + positions);

        var integerDivision = 5 / 2;
        var decimalDivision = 5 / 2.0;
        System.out.println("integerDivision=" + integerDivision);
        System.out.println("decimalDivision=" + decimalDivision);

        long largeValue = 1_000_000_000L + 1_000_000_000L;
        System.out.println("largeValue=" + largeValue);
    }
}
~~~

Dùng JDK 17 trở lên. Tại thư mục chứa file, mở PowerShell hoặc Bash và chạy:

~~~bash
java -version
javac -version
javac -encoding UTF-8 ReadableVarDemo.java
java ReadableVarDemo
~~~

**Exact expected output** của lệnh `java ReadableVarDemo` (không tính dòng phiên bản JDK):

~~~text
retries=3
language=Java
first=Lan:10
names=An,Binh
published=[Lan]
studentCount=2
totalPoints=17
normalized=AN
uppercase=JAVA
parsed=7
wrapperType=Integer
mixed=[text, 7]
positions=012
integerDivision=2
decimalDivision=2.5
largeValue=2000000000
~~~

### Vì sao chương trình chọn kiểu khai báo khác nhau?

- `var first = new Student(...)` rõ vì tên lớp `Student` hiện ngay tại `new`; biến suy ra kiểu `Student`.
- `var names = new ArrayList<String>()` thể hiện rõ kiểu phần tử. Trong khi đó, `List<String> publishedNames = new ArrayList<>()` chọn **kiểu interface `List<String>`** làm hợp đồng sử dụng thay vì cố định kiểu khai báo của biến là `ArrayList<String>`.
- `List<Student> students = loadStudents()` làm rõ kiểu kết quả ngay cho người đọc, không buộc họ mở định nghĩa `loadStudents()`.
- Trong `for (var student : students)`, kiểu của phần tử đã rõ từ `List<Student>`, nên tránh lặp `Student` mà vẫn dễ hiểu.
- `String normalizedName = normalizeName(...)` làm rõ kiểu dữ liệu của một hàm nghiệp vụ có tên không nhất thiết nói hết chữ ký.
- `var primitive = Integer.parseInt("7")` suy ra `int`, còn `var wrapper = Integer.valueOf("7")` suy ra `Integer`. Hai kết quả có thể cùng hiển thị là 7 nhưng **khác kiểu**.
- `var mixed = new ArrayList<>()` không có kiểu đích generic cụ thể, nên trong trường hợp này `<>` suy ra `Object` và kiểu biến là `ArrayList<Object>`. Điều đó giải thích tại sao danh sách nhận được cả `String` và `Integer`.
- `var integerDivision = 5 / 2` có kiểu `int`, giá trị 2; `var decimalDivision = 5 / 2.0` có kiểu `double`, giá trị 2.5. `var` không sửa kiểu của biểu thức trước khi gán.

## 3. `var` vẫn là kiểu tĩnh, không phải “kiểu tùy biến”

~~~java
var count = 10; // int.
count = 20;     // Hop le.
// count = "20"; // LOI bien dich: String khong gan duoc cho int.

var name = "An"; // String.
// name = 123;    // LOI bien dich: int khong gan duoc cho String.
~~~

Compiler suy kiểu **một lần tại khai báo**, sau đó áp dụng các quy tắc Java bình thường (gán kiểu, overload, generic, truy cập thành viên).

Vì vậy đừng chọn `var` chỉ vì muốn biến “linh hoạt hơn”. Nếu một biến cần mang kiểu cơ sở hoặc interface để nhận nhiều lớp triển khai, hãy **chủ động chọn kiểu tường minh** phù hợp:

~~~java
CharSequence text = new StringBuilder("Hi");
text = "Hello"; // Hop le: String cung implements CharSequence.

// var text = new StringBuilder("Hi");
// text = "Hello"; // LOI: kieu suy ra la StringBuilder.
~~~

Đây là khác biệt về **kiểu khai báo được chọn**, không phải giới hạn đặc biệt về đối tượng do `var` tạo ra.

## 4. Bẫy generic khi dùng diamond `<>`

~~~java
var values = new ArrayList<>();
values.add("Ada");
values.add(42); // Hop le, vi ArrayList<Object>.
~~~

Người đọc có thể đoán “đây là danh sách tên” nhưng compiler chỉ thấy biểu thức tạo `ArrayList<>` thiếu kiểu đích cụ thể. Đừng chờ các câu `add(...)` viết về sau để compiler suy lại kiểu phần tử: việc suy kiểu đã kết thúc tại khai báo.

Hai cách rõ ràng hơn:

~~~java
var names = new ArrayList<String>();

List<String> otherNames = new ArrayList<>();
~~~

- Cách đầu thích hợp khi muốn `ArrayList<String>` và constructor đã nêu rõ generic.
- Cách sau tốt khi muốn mã phụ thuộc vào **interface `List<String>`** thay vì lớp triển khai cụ thể.

`var` không tự tạo ràng buộc generic theo ý định nghiệp vụ; hãy thể hiện ràng buộc đó trong initializer hoặc kiểu tường minh.

## 5. Tên phương thức không phải hợp đồng kiểu

Đoạn sau **có thể hợp lệ về mặt compiler** nếu `load()` tồn tại, nhưng khó hiểu khi chỉ đọc tại vị trí khai báo:

~~~java
// var result = load();
~~~

Hãy cân nhắc khai báo trực tiếp loại dữ liệu:

~~~java
// List<Student> students = load();
~~~

Khi thực sự cần biết `result` có thể là `List<Student>`, `Optional<Student>` hay `Map<String, Student>`, người đọc phải lần ngược về chữ ký phương thức. `var` tiết kiệm vài ký tự nhưng làm tăng chi phí tìm hiểu.

Ngược lại, nếu tên hàm rất đặc trưng và ngữ cảnh gần đó đã chỉ rõ kiểu, `var` vẫn có thể hợp lý. Đây là **quy ước về khả năng đọc và bảo trì**, không phải quy tắc compiler bắt buộc. Trong code review, nên dựa vào sự rõ ràng của đoạn mã thay vì số lần xuất hiện của `var`.

## 6. Những ngữ cảnh thường đọc rõ với `var`

| Ngữ cảnh | Ví dụ hợp lệ | Nguồn suy luận |
| --- | --- | --- |
| Local có initializer | `var count = 1;` | Biểu thức `1`: `int` |
| Constructor nêu kiểu | `var builder = new StringBuilder();` | Kiểu `StringBuilder` |
| Enhanced `for` | `for (var name : names) { }` | Kiểu phần tử của `names` |
| Vòng `for` thường | `for (var i = 0; i < 3; i++) { }` | Initializer `0` |
| `try-with-resources` | `try (var reader = new java.io.StringReader("x")) { }` | Constructor của resource |
| Local bất biến tham chiếu | `final var max = 10;` | Kiểu `int` từ `10` |

`var` không được dùng để khai báo field của class, kiểu trả về phương thức hoặc kiểu tham số phương thức thông thường. Với lambda, Java 17 hỗ trợ `var` cho tham số có kiểu suy luận từ functional interface mục tiêu, nhưng đó là một ngữ cảnh riêng.

Hai khai báo sau cố ý **không hợp lệ**:

~~~java
// var value;        // Thieu initializer.
// var value = null; // Null literal khong cho kieu cu the.
~~~

Khi cần khai báo trước rồi mới gán, hoặc cần một tham chiếu nullable mà kiểu chưa hiện trong biểu thức, hãy viết kiểu tường minh như `String value = null;`.

## 7. Kiểu số và phép toán: đọc nhanh chưa chắc đoán đúng

~~~java
var a = 2;        // int.
var b = 2L;       // long.
var c = 2.0;      // double.
var d = 2.0f;     // float.

var quotient = 5 / 2;   // int, gia tri 2.
var exact = 5 / 2.0;    // double, gia tri 2.5.
~~~

Nếu dữ liệu là số tiền, bộ đếm có thể lớn, hoặc tỷ lệ cần độ chính xác cụ thể, hãy chú ý **kiểu của literal** và **kiểu biểu thức trung gian**. `var` chỉ chọn kiểu kết quả hiện có, **không ngăn tràn số** hay tự chuyển chia nguyên thành chia số thực.

Ví dụ:

~~~java
long total = 1_000_000_000L + 1_000_000_000L;
~~~

Khai báo tường minh `long` và hậu tố `L` làm chủ đích dùng số nguyên rộng dễ nhận biết, dù trong trường hợp giá trị ví dụ chưa vượt miền `int`. Nếu phép toán có thể vượt `long`, vẫn phải xử lý phù hợp.

## 8. Quy tắc code review thực tế

Khi nhìn vào một khai báo `var`, hãy tự hỏi:

1. **Người chưa đọc hàm trả về có đoán đúng kiểu không?** Nếu không, thêm kiểu tường minh.
2. **Kiểu phần tử generic có được xác định rõ?** Tránh `var items = new ArrayList<>()` khi muốn `String` hay một kiểu nghiệp vụ cụ thể.
3. **Kiểu interface hoặc superclass có phải ý đồ thiết kế?** Nếu có, viết `List<String>`, `Collection<T>` hoặc interface thích hợp bên trái.
4. **Kiểu primitive/wrapper và độ rộng số có quan trọng?** Nếu có, làm rõ literal, cast hoặc khai báo.
5. **Đặt tên biến có giúp hiểu ý nghĩa?** `var retryCount = 3` rõ hơn `var x = 3`, dù kiểu cùng là `int`.
6. **Có giảm lặp thực chất không?** `var student = new Student(...)` thường gọn hơn `Student student = new Student(...)` mà không giấu thông tin.

Không có câu trả lời tự động cho mọi khai báo. Mục tiêu là **giảm chữ thừa mà vẫn giữ lại thông tin cần thiết cho người đọc**.

## 9. Những hiểu nhầm thường gặp

| Nhận định | Đúng hay sai? | Giải thích |
| --- | --- | --- |
| “Dùng `var` là bỏ kiểm tra kiểu.” | **Sai** | Java vẫn xác định kiểu tĩnh |
| “Cứ có `new` thì `var` luôn dễ hiểu.” | **Sai** | `new ArrayList<>()` có thể giấu kiểu generic |
| “`var x = 3;` có thể đổi sang `String`.” | **Sai** | `x` đã có kiểu `int` |
| “`var list = new ArrayList<>()` chắc là `ArrayList<String>`.” | **Sai** | Không có target type; trường hợp này suy ra `ArrayList<Object>` |
| “Kiểu của `var x = parseInt(...)` là wrapper.” | **Sai** | `Integer.parseInt` trả `int` |
| “Viết `List<String>` bên trái có thể thể hiện interface mong muốn.” | **Đúng** | Đó là hợp đồng kiểu tĩnh của biến |
| “Tên biến tốt có thể giúp `var` dễ đọc hơn.” | **Đúng** | Tên và initializer bổ sung ngữ cảnh |

## 10. Bài tập tự kiểm tra

1. **Rõ kiểu:** `var book = new Book("Java");` hay `var result = fetch();` dễ hiểu kiểu hơn nếu chưa đọc hàm?  
   *Gợi ý:* Câu thứ nhất thể hiện `Book` trực tiếp; câu thứ hai phụ thuộc chữ ký `fetch()`.
2. **Generic:** `var names = new ArrayList<>();` có bảo đảm `ArrayList<String>` không?  
   *Đáp án:* Không; trường hợp này suy ra `ArrayList<Object>`. Viết `new ArrayList<String>()` hoặc `List<String> names = new ArrayList<>()`.
3. **Interface:** Vì sao `List<String> names = new ArrayList<>()` có thể tốt hơn `var names = new ArrayList<String>()`?  
   *Đáp án:* Nó thể hiện biến dự kiến dùng qua API `List`, không ràng buộc kiểu khai báo vào `ArrayList`.
4. **Số học:** `var result = 5 / 2;` và `var result = 5 / 2.0;` có kiểu gì?  
   *Đáp án:* `int` và `double`, lần lượt có giá trị 2 và 2.5.
5. **Chữ ký phương thức:** Nếu `loadStudents()` trả `List<Student>`, dùng `var students = loadStudents();` có sai không?  
   *Đáp án:* Không sai về ngôn ngữ; nhưng `List<Student> students = loadStudents();` có thể rõ hơn cho người đọc.
6. **Thực hành:** Đổi tên `var x = 3;` cho dễ hiểu nếu đây là số lần thử.  
   *Gợi ý:* `var retryCount = 3;`.

## Checklist cuối bài

- [ ] Chỉ dùng `var` khi kiểu vẫn **rõ ràng tại chỗ khai báo**.
- [ ] Hiểu `var` không tạo kiểu động hay thay đổi cơ chế type checking.
- [ ] Không để diamond `<>` làm mất ràng buộc generic mong muốn.
- [ ] Chọn kiểu tường minh khi cần thể hiện interface/superclass hoặc hợp đồng API.
- [ ] Cân nhắc primitive/wrapper, độ rộng số và phép toán khi suy luận kiểu.
- [ ] Ưu tiên tên biến có nghĩa và initializer nói rõ dữ liệu.
- [ ] Trong code review, lấy **tính dễ đọc và bảo trì** làm tiêu chí, không coi việc dùng `var` là mục đích tự thân.

**Kết luận:** `var` tốt nhất khi giúp bỏ phần kiểu lặp lại **mà không bắt người đọc đi tìm kiểu ở nơi khác**. Nếu biểu thức khởi tạo không cho thấy đủ loại dữ liệu hoặc che mất hợp đồng quan trọng, viết kiểu tường minh là lựa chọn rõ ràng và an toàn hơn.


</details>

- [Quay lại đầu trang](#back-to-top)
