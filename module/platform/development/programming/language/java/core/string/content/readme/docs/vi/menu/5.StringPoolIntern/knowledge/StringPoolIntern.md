<a id="back-to-top"></a>

# Pool và intern

## Menu
- [String pool và việc dùng chung literal](#string-pool-intern-001)
- [Biểu thức hằng chuỗi được tính khi biên dịch](#string-pool-intern-002)
- [Phân biệt nối hằng với nối giá trị lúc chạy](#string-pool-intern-003)
- [new String tạo đối tượng và quan hệ với literal có sẵn](#string-pool-intern-004)
- [String.intern và tham chiếu chuẩn hóa theo nội dung](#string-pool-intern-005)
- [Phân biệt string pool với constant pool của class file](#string-pool-intern-006)
- [Vì sao quan sát == trên literal không thay thế so sánh nội dung](#string-pool-intern-007)
- [Khi nào intern có ích và giới hạn khi dùng trên dữ liệu không kiểm soát](#string-pool-intern-008)

## <a id="string-pool-intern-001">String pool và việc dùng chung literal</a>

<details>
<summary>Xem chi tiết</summary>

# String pool và việc dùng chung literal trong Java

## 1. String pool là gì?

**String pool** (hay *string intern pool*) là cơ chế Java dùng để quản lý các chuỗi đã được *intern*: mỗi nội dung chuỗi có một tham chiếu chuẩn (*canonical reference*) trong pool tại một thời điểm. Khi mã Java sử dụng literal như `"Java"`, JVM dùng tham chiếu của chuỗi đã được intern tương ứng; nhiều lần xuất hiện cùng literal có thể dùng chung một đối tượng `String`.

```java
String first = "Java";
String second = "Java";

System.out.println(first == second);      // true
System.out.println(first.equals(second)); // true
```

Hai phép so sánh đều trả về `true`, nhưng **vì hai lý do khác nhau**:

- `first == second` đúng vì hai biến tham chiếu **cùng đối tượng** `String` đã được intern.
- `first.equals(second)` đúng vì hai chuỗi có **cùng nội dung**.

Bản thân `String` là kiểu **bất biến** (*immutable*): sau khi đối tượng được tạo, các ký tự của đối tượng không thay đổi. Nhờ đó, các nơi giữ chung tham chiếu đến một literal không thể sửa nội dung và làm ảnh hưởng nhau. Đây là một cơ sở quan trọng cho việc chia sẻ chuỗi an toàn qua pool.

**Giới hạn cần nhớ:** Pool không có nghĩa mọi đối tượng `String` có cùng nội dung đều là cùng một đối tượng. Java vẫn cho phép tạo các đối tượng `String` khác nhau nhưng `equals()` trả về `true`.

## 2. Literal được dùng chung như thế nào?

**String literal** là chuỗi viết trực tiếp trong mã nguồn, ví dụ `""`, `"hello"`, `"Xin chào"`. Java Language Specification quy định literal chuỗi (và kết quả biểu thức hằng kiểu chuỗi) được intern, qua đó cùng nội dung sẽ có tham chiếu chuẩn.

```java
String a = "hello";
String b = "hello";
String c = "world";

System.out.println(a == b); // true
System.out.println(a == c); // false
```

Dòng thứ nhất so sánh hai literal giống nhau nên `true`. Dòng thứ hai là hai nội dung khác nhau nên không thể là cùng chuỗi, trả về `false`.

Có thể hình dung:

```text
                  String pool (mô hình khái niệm)

a --------------------+
                      +----> String "hello"
b --------------------+

c --------------------------> String "world"
```

Sơ đồ chỉ minh họa việc **dùng chung tham chiếu**, không khẳng định bố trí ô nhớ, địa chỉ hay thuật toán thực thi của một JVM cụ thể.

### Literal giống nhau có thể nằm ở nhiều nơi trong chương trình

Nếu các lớp, phương thức hay phần khác nhau của một chương trình chạy trong cùng JVM sử dụng các chuỗi literal giống nội dung, các literal đó được intern và có thể cùng tham chiếu đến chuỗi chuẩn. Cơ chế này không đòi hỏi lập trình viên tự gọi `intern()` trên từng literal.

**Không suy ra số lượng đối tượng chỉ bằng cách đếm dòng mã:** Một literal xuất hiện nhiều lần không hàm ý có chừng ấy đối tượng, và thời điểm đối tượng được tạo/lấy từ pool phụ thuộc quá trình tải, liên kết, thực thi lớp cũng như chi tiết triển khai.

## 3. Kiểm chứng việc chia sẻ literal qua hai lớp

Ví dụ trên chỉ dùng một phương thức; hãy thử viết literal cùng nội dung ở **hai lớp khác nhau** trong tệp `LiteralAcrossClasses.java`:

~~~java
class LiteralProvider {
    static String getLabel() {
        return "java-pool";
    }
}

public class LiteralAcrossClasses {
    public static void main(String[] args) {
        String local = "java-pool";
        String anotherClass = LiteralProvider.getLabel();
        String constructed = new String("java-pool");

        System.out.println(local == anotherClass);
        System.out.println(local == constructed);
        System.out.println(local.equals(constructed));
    }
}
~~~

~~~bash
javac -encoding UTF-8 LiteralAcrossClasses.java
java LiteralAcrossClasses
~~~

~~~text
true
false
true
~~~

Hai literal giống nhau dùng chung tham chiếu dù nằm ở hai lớp. `constructed` là đối tượng riêng do `new` tạo nhưng có nội dung bằng nhau. Việc các class file có bảng constant pool riêng **không** đồng nghĩa mỗi lớp có một string pool riêng: metadata của class file và đối tượng interned lúc chạy là hai tầng khác nhau.

## 4. Liên hệ với tính bất biến và cấu trúc dữ liệu

Pool hoạt động an toàn vì `String` bất biến. Ví dụ:

```java
String one = "color";
String two = "color";

one = one.replace('c', 'C');

System.out.println(one); // Color
System.out.println(two); // color
```

`replace()` trả về một chuỗi có nội dung khác; đối tượng `"color"` gốc không bị sửa. Việc `one` đổi tham chiếu không ảnh hưởng `two`.

Tương tự, `String` thích hợp làm khóa cho `HashMap` vì nội dung dùng bởi `equals()` và `hashCode()` không biến đổi sau khi chèn. Việc được intern **không phải điều kiện** để `HashMap` nhận diện hai khóa chuỗi bằng nhau:

```java
java.util.Map<String, Integer> counts = new java.util.HashMap<>();
counts.put("Java", 5);

String key = new String("Java");
System.out.println(counts.get(key)); // 5
```

`HashMap` dùng `hashCode()` và `equals()` theo hợp đồng của khóa, không bắt buộc hai khóa phải trỏ cùng đối tượng.

Pool và tính bất biến cũng không tự làm mọi thao tác với biến `String` chia sẻ giữa các thread trở nên an toàn về thứ tự thực thi/khả năng quan sát. Chia sẻ **nội dung đối tượng bất biến** và cập nhật **biến tham chiếu chung** là hai việc khác nhau; trường hợp thứ hai vẫn phải tuân theo Java Memory Model và chiến lược đồng bộ phù hợp.

## 5. Giới hạn của điều quan sát được

- Pool quản lý **chuỗi đã intern**, không phải mọi `String` được tạo từ file, request, JSON hay phép nối runtime.
- `"Ja" + "va"` là biểu thức hằng có kết quả `"Java"` nên được intern; phép nối với biến không phải constant variable là một tình huống khác. Bài về biểu thức hằng và nối runtime sẽ phân tích ranh giới này.
- `intern()` lấy **tham chiếu chuẩn** cho nội dung; nó không chỉnh sửa biến gọi phương thức và không chuyển đổi chữ hoa/thường, khoảng trắng hay Unicode.
- Không suy diễn vị trí bộ nhớ hoặc số lần cấp phát từ số literal trong source. Phân giải class, thời điểm sử dụng và tối ưu JVM có thể khác nhau.
- Trong logic kiểm tra nội dung, dùng `equals()`; một kết quả `== true` trên literal chỉ chứng minh hai tham chiếu đang cùng đối tượng.

## 6. Tự kiểm tra

Hãy dự đoán bốn kết quả sau:

```java
String x = "code";
String y = "co" + "de";
String part = "co";
String z = part + "de";
String t = new String("code");

System.out.println(x == y);
System.out.println(x == z);
System.out.println(x == t);
System.out.println(z.intern() == x);
```

Kết quả:

```text
true
false
false
true
```

Giải thích: `y` là kết quả biểu thức hằng được intern; `z` là phép nối với biến thực hiện lúc chạy; `t` là đối tượng tạo riêng bằng `new`; `z.intern()` trả về tham chiếu chuẩn của literal `"code"`.

**Ghi nhớ:** *Cùng nội dung* (`equals`) và *cùng đối tượng* (`==`) là hai khái niệm khác nhau. String pool giúp literal và chuỗi đã intern chia sẻ tham chiếu chuẩn; nó không phải quy tắc so sánh nội dung cho chương trình Java.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-pool-intern-002">Biểu thức hằng chuỗi được tính khi biên dịch</a>

<details>
<summary>Xem chi tiết</summary>

# Biểu thức hằng chuỗi được tính khi biên dịch trong Java

## 1. Hiện tượng cần hiểu

Hai cách viết sau tạo ra **cùng một giá trị chuỗi** `"Java"`:

```java
String a = "Java";
String b = "Ja" + "va";
```

Điểm thú vị: biểu thức `"Ja" + "va"` **không cần ghép hai chuỗi trong lúc chương trình chạy**. Vì cả hai toán hạng đều là hằng, trình biên dịch Java có thể và theo ngữ nghĩa ngôn ngữ sẽ xử lý chúng như một **biểu thức hằng chuỗi** (*constant string expression*), có giá trị `"Java"` ngay từ lúc biên dịch.

```java
System.out.println(a == b);      // true
System.out.println(a.equals(b)); // true
```

Ở ví dụ này, `a == b` là `true` vì các chuỗi có giá trị là biểu thức hằng được **intern**: chuỗi có cùng nội dung dùng chung một tham chiếu chuẩn hóa trong *string pool*. Tuy nhiên, trong ứng dụng thực tế, muốn kiểm tra **nội dung văn bản**, luôn ưu tiên `.equals(...)` thay vì dựa vào `==`.

Bài học này giải thích điều kiện để phép nối chuỗi được tính khi biên dịch, mối liên hệ với pool và `intern()`, cách phân biệt với nối chuỗi lúc chạy, cùng các nhầm lẫn khi đọc hoặc tối ưu mã Java.

## 2. Biểu thức hằng (*constant expression*) là gì?

**Constant expression** là biểu thức có giá trị được xác định từ những thành phần và phép toán được Java cho phép tại thời điểm biên dịch. Quy tắc ngôn ngữ được mô tả trong *Java Language Specification*, mục **15.29 – Constant Expressions**.

Với chuỗi, các ví dụ đơn giản là:

```java
String first = "Java";                   // Literal chuỗi
String second = "Ja" + "va";             // Nối hai literal
String version = "Java " + 17;           // Chuỗi cộng với hằng số nguyên
String calculated = "Kết quả: " + (1 + 2); // Hằng số học + chuỗi
```

Giá trị tương ứng là `"Java"`, `"Java"`, `"Java 17"` và `"Kết quả: 3"`.

Toán tử `+` có hai ý nghĩa liên quan ở đây:

- Nếu hai toán hạng là số, `+` có thể là phép cộng số học.
- Nếu một toán hạng là `String`, `+` dùng để **nối chuỗi**, chuyển toán hạng còn lại sang biểu diễn văn bản theo quy tắc của Java.

Khi các thành phần của phép nối thỏa điều kiện *constant expression*, nội dung kết quả được tính trước, không cần thực hiện một phép nối động tại thời điểm chạy.

**Phân biệt hai thời điểm:** *Tính nội dung chuỗi lúc biên dịch* không có nghĩa trình biên dịch đã tạo một đối tượng `String` Java nằm trong heap của tiến trình tương lai. Trình biên dịch biểu diễn hằng trong dữ liệu của class file; khi class được sử dụng, JVM giải quyết literal/hằng chuỗi theo cơ chế runtime và pool.

## 3. Khi nào một biến được coi là hằng tại thời điểm biên dịch?

Đặt từ khóa `final` lên biến **chưa đủ** để mọi cách sử dụng biến trở thành constant expression.

Java dùng thuật ngữ **constant variable** cho biến:

1. Có kiểu nguyên thủy (*primitive*) hoặc `String`.
2. Được khai báo `final`.
3. Được khởi tạo **bằng một constant expression** ngay trong phần khai báo.

Ví dụ về các biến hằng hợp lệ (đặt trong phương thức):

```java
final String PREFIX = "Ja";
final String WORD = PREFIX + "va";
final int MAJOR = 17;

String a = WORD;              // "Java"
String b = "Java " + MAJOR;    // "Java 17"
```

Vì `PREFIX`, `WORD` và `MAJOR` thỏa ba điều kiện, trình biên dịch có thể thay các tham chiếu của chúng bằng giá trị hằng khi xác định biểu thức.

Các trường `static final` cũng có thể là constant variable:

```java
class Constants {
    static final String PREFIX = "Ja";
    static final String WORD = PREFIX + "va";
}
```

Nhưng hãy xem những ví dụ **không** phải constant variable:

```java
String prefix = "Ja";                  // Không final
final String fromMethod = getPrefix(); // Gọi phương thức lúc chạy
final String boxed = new String("Ja"); // Khởi tạo bằng new
```

Trong đó `getPrefix()` giả sử là phương thức được định nghĩa ở nơi khác, trả về `String`; ba dòng này chỉ minh họa cú pháp trong ngữ cảnh có khai báo phương thức phù hợp.

Cả khi `getPrefix()` luôn trả về `"Ja"`, hoặc `new String("Ja")` luôn chứa cùng ký tự, các biểu thức ấy vẫn **không** trở thành constant expression chỉ vì ta dự đoán được kết quả.

Một biến `final` được **gán trong khối khởi tạo** thay vì có initializer ngay trong khai báo cũng không thỏa định nghĩa constant variable:

```java
class Config {
    static final String PREFIX;
    static {
        PREFIX = "Ja";
    }
}
```

Sau đó `Config.PREFIX + "va"` là phép nối phụ thuộc giá trị của biến tại runtime, không phải constant expression theo quy tắc đã nêu.

### So sánh nhanh

| Biểu thức | Hằng khi biên dịch? | Giải thích |
|---|---|---|
| `"Ja" + "va"` | Có | Hai toán hạng là literal |
| `"Java " + 17` | Có | Literal chuỗi và hằng số nguyên |
| `final String x = "Ja";` rồi `x + "va"` | Có | `x` là constant variable |
| `String x = "Ja";` rồi `x + "va"` | Không | `x` không `final` |
| `final String x = method();` rồi `x + "va"` | Không | Initializer là lời gọi phương thức |
| `final String x = new String("Ja");` rồi `x + "va"` | Không | Initializer không phải constant expression |
| `String.valueOf(17) + "!"` | Không | Có lời gọi phương thức |

Ở bảng này, các dòng chứa khai báo biến và biểu thức nối mô tả những đoạn mã riêng biệt, không phải một chương trình đầy đủ. Đừng đồng nhất “có giá trị luôn giống nhau khi chạy” với “được JLS công nhận là constant expression”.

## 4. Mối liên hệ giữa constant expression, string pool và `intern()`

### String pool hoạt động thế nào?

*String pool* là cơ chế JVM duy trì tham chiếu chuẩn hóa cho các chuỗi có cùng nội dung. String literal và các `String` là **giá trị của constant expression** được intern để các giá trị hằng tương đương tham chiếu đến cùng một đối tượng chuỗi chuẩn hóa.

Vì vậy:

```java
String literal = "Java";
String folded = "Ja" + "va";

System.out.println(literal == folded); // true
```

Nội dung `"Ja" + "va"` được coi là hằng `"Java"`, và hai biểu thức hằng nội dung tương đương dùng chung tham chiếu chuẩn hóa.

### Nối chuỗi lúc chạy là tình huống khác

```java
String prefix = "Ja";
String joined = prefix + "va";

System.out.println("Java".equals(joined)); // true
System.out.println("Java" == joined);       // false
```

`prefix` không phải constant variable. Biểu thức `prefix + "va"` cần đọc giá trị của biến khi chạy; nó là phép nối động, cho một đối tượng kết quả khác với đối tượng interned được literal `"Java"` tham chiếu. Nội dung bằng nhau vẫn không đồng nghĩa tham chiếu bằng nhau.

### `intern()` đóng vai trò gì?

`String.intern()` trả về tham chiếu chuẩn hóa trong pool cho chuỗi có cùng nội dung. Nếu chưa có chuỗi tương ứng trong pool, cơ chế intern sẽ thiết lập tham chiếu chuẩn hóa đó theo hợp đồng API.

```java
String prefix = "Ja";
String joined = prefix + "va";

System.out.println(joined == "Java");          // false
System.out.println(joined.intern() == "Java"); // true
```

Lời gọi `intern()` **không biến phép cộng lúc chạy thành phép cộng lúc biên dịch**. Đây là một hành động lúc chương trình chạy để lấy tham chiếu chuẩn hóa. Nó cũng không thay nội dung của `joined`: `String` bất biến.

Một lưu ý về thuật ngữ: **constant pool trong class file** là cấu trúc dữ liệu lưu nhiều loại hằng và tham chiếu biểu tượng của mã biên dịch; **string pool** là cơ chế chia sẻ đối tượng `String` khi thực thi. Hai khái niệm có liên hệ, nhưng không phải một nơi chứa duy nhất và cũng không nên coi là đồng nghĩa.

## 5. Ví dụ Java 17+ hoàn chỉnh và kết quả kiểm chứng

Tạo tệp **`StringConstantExpressionDemo.java`**:

```java
public class StringConstantExpressionDemo {
    private static final String STATIC_PREFIX = "Ja";

    private static final String LATE_PREFIX;
    static {
        LATE_PREFIX = "Ja";
    }

    private static String getPrefix() {
        return "Ja";
    }

    public static void main(String[] args) {
        String literal = "Java";
        String folded = "Ja" + "va";

        final String localPrefix = "Ja";
        String fromLocalConstant = localPrefix + "va";
        String fromStaticConstant = STATIC_PREFIX + "va";

        String mutablePrefix = "Ja";
        String runtimeConcat = mutablePrefix + "va";

        final String methodPrefix = getPrefix();
        String fromMethod = methodPrefix + "va";
        String fromLateFinal = LATE_PREFIX + "va";

        String explicitNew = new String("Java");

        System.out.println("literal == folded: "
                + (literal == folded));
        System.out.println("literal == local constant: "
                + (literal == fromLocalConstant));
        System.out.println("literal == static constant: "
                + (literal == fromStaticConstant));
        System.out.println("literal == runtime concat: "
                + (literal == runtimeConcat));
        System.out.println("literal == method result: "
                + (literal == fromMethod));
        System.out.println("literal == late final: "
                + (literal == fromLateFinal));
        System.out.println("literal == new String: "
                + (literal == explicitNew));
        System.out.println("literal == interned runtime: "
                + (literal == runtimeConcat.intern()));
        System.out.println("literal.equals(runtime): "
                + literal.equals(runtimeConcat));
    }
}
```

Biên dịch và chạy (JDK 17 trở lên):

```bash
javac -encoding UTF-8 StringConstantExpressionDemo.java
java StringConstantExpressionDemo
```

**Stdout:**

```text
literal == folded: true
literal == local constant: true
literal == static constant: true
literal == runtime concat: false
literal == method result: false
literal == late final: false
literal == new String: false
literal == interned runtime: true
literal.equals(runtime): true
```

**Đọc kết quả:**

1. `folded`, `fromLocalConstant` và `fromStaticConstant` có nội dung được xác định bởi constant expression. Chúng cùng tham chiếu đến giá trị interned `"Java"` như literal.
2. `runtimeConcat`, `fromMethod` và `fromLateFinal` tham gia phép nối lúc chạy, nên nội dung vẫn là `"Java"` nhưng đối tượng kết quả không phải đối tượng được literal `"Java"` tham chiếu.
3. `new String("Java")` yêu cầu tạo một đối tượng `String` riêng; vì thế tham chiếu khác literal ngay cả khi nội dung giống nhau.
4. `runtimeConcat.intern()` lấy tham chiếu chuẩn hóa của `"Java"` và so sánh bằng `==` với literal sẽ là `true`.
5. `literal.equals(runtimeConcat)` là `true`, xác nhận so sánh nội dung không phụ thuộc hai biến có trỏ đến cùng đối tượng hay không.

Ví dụ kiểm soát rõ nguồn của từng chuỗi để minh họa quy tắc. Trong chương trình thông thường, **không dùng kết quả `==` giữa `String` để suy đoán dữ liệu đã được tính lúc nào**; nếu mục tiêu là so sánh văn bản, dùng `equals`.

## 6. Nhận diện phép ghép hằng qua bytecode

Với đoạn nguồn:

```java
String result = "Ja" + "va";
```

Compiler có thể đưa thẳng nội dung `"Java"` vào class file thay vì mã nối hai toán hạng riêng. Đây là *constant folding*: rút gọn biểu thức hằng trước khi chương trình thực thi.

Có thể quan sát class file được tạo ở mục 5 bằng:

```bash
javap -c -p StringConstantExpressionDemo
```

Kết quả `javap` phụ thuộc compiler và phiên bản JDK, nhưng bạn có thể tìm các lệnh tải hằng chuỗi như `ldc ... // String Java` ở những chỗ dùng constant expression. Với phép nối runtime, bytecode của `javac` trên JDK hiện đại thường sử dụng `invokedynamic` và cơ chế nối chuỗi của JVM; cách sinh bytecode có thể thay đổi qua các bản JDK.

**Không cần dựa vào một opcode cụ thể để hiểu quy tắc**. Điều cốt lõi là: phép nối của constant expression có giá trị xác định trước theo JLS; phép nối phụ thuộc biến không phải constant variable phải sử dụng dữ liệu runtime.

Cũng cần tránh hiểu sai rằng một biểu thức được tối ưu nhờ JIT lúc chạy sẽ tự động trở thành **constant expression của ngôn ngữ Java**. Đây là hai tầng khác nhau: tính hằng theo JLS được xác định từ mã nguồn/biên dịch, còn JIT là tối ưu thực thi.

## 7. Những lỗi hiểu sai thường gặp

### Lỗi 1: “Có chữ `final` là trình biên dịch tính hết”

Không đúng. Hãy so sánh:

```java
final String a = "Ja";             // Constant variable
final String b = "J".concat("a");  // Không phải constant variable
```

`b` là `final`, nhưng lời gọi `concat(...)` không phải constant expression theo định nghĩa JLS. `b + "va"` vẫn không phải phép nối hằng tại thời điểm biên dịch.

### Lỗi 2: “Dùng `==` để so sánh hai chuỗi sẽ nhanh hơn nên an toàn”

`==` so sánh **đồng nhất đối tượng**. Những chuỗi hằng được intern có thể tình cờ làm phép so sánh `==` cho kết quả đúng như nội dung, nhưng chuỗi nhập từ người dùng, đọc file, API hoặc ghép động thường không có bảo đảm ấy.

Dùng:

```java
"Java".equals(candidate)
```

Khi `candidate` có thể là `null`, cách viết này tránh `NullPointerException` ở phía nhận phương thức. Hoặc dùng `java.util.Objects.equals(a, b)` khi cả hai biến đều có thể `null`.

### Lỗi 3: “Nối hai giá trị luôn giống nhau thì chắc chắn là nối hằng”

Ví dụ `getPrefix() + "va"` vẫn là nối runtime, dù phương thức `getPrefix()` luôn trả `"Ja"`. Quy tắc xét **cấu trúc biểu thức** và những thành phần được JLS cho phép, không dựa vào việc con người biết trước kết quả.

### Lỗi 4: “Compiler đã tính chuỗi thì chương trình hoàn toàn không dùng bộ nhớ cho `String`”

Sai. Compiler xác định **nội dung hằng**, còn JVM vẫn có đối tượng `String` được sử dụng ở runtime. Việc intern giúp tái sử dụng tham chiếu của các hằng có cùng nội dung, chứ không xóa khái niệm đối tượng chuỗi.

### Lỗi 5: “Gọi `intern()` sẽ làm toàn bộ việc xử lý chuỗi nhanh hơn”

Không có quy tắc chung như vậy. `intern()` mang thêm hoạt động quản lý tham chiếu chuẩn hóa; chỉ nên sử dụng khi thực sự hiểu yêu cầu chia sẻ/chuẩn hóa tham chiếu và đặc điểm dữ liệu. Cách so sánh nội dung bình thường vẫn là `.equals(...)`; không cần intern mọi chuỗi để so sánh.

## 8. Khi nào kiến thức này hữu ích?

- **Đọc kết quả `==` bất ngờ:** giải thích vì sao `"a" + "b" == "ab"` có thể đúng trong biểu thức hằng, còn nối bằng biến có cùng nội dung thì khác.
- **Hiểu compiler:** nhận diện ranh giới giữa tính hằng tại biên dịch và nối chuỗi tại runtime.
- **Đọc bytecode:** hiểu tại sao source dùng toán tử `+` mà class file chỉ cần tải một hằng chuỗi.
- **Thiết kế hằng dùng chung:** `static final String` được khởi tạo bởi constant expression có thể được nội tuyến giá trị vào mã của lớp sử dụng khi biên dịch. Vì thế nếu thay giá trị hằng công khai trong thư viện, các lớp đã biên dịch trước đó có thể vẫn giữ giá trị cũ cho tới khi được biên dịch lại. Không nên dựa vào field hằng biên dịch để cung cấp cấu hình cần thay đổi mà không biên dịch lại client.
- **Tránh tối ưu hóa sai:** lựa chọn giữa `==`, `.equals(...)` và `intern()` dựa vào **ngữ nghĩa** cần thiết, không chỉ vào việc thấy hai chuỗi có nội dung giống nhau.

## 9. Tự kiểm tra

**Câu 1.** Đoạn sau in gì?

```java
String x = "ab";
String y = "a" + "b";
System.out.println(x == y);
```

**Đáp án:** `true`, vì `"a" + "b"` là constant expression có giá trị interned `"ab"`.

**Câu 2.** Thêm `final` vào biến có thay đổi kết quả không?

```java
String a = "a";
String x = a + "b";
System.out.println(x == "ab");
```

**Đáp án:** Mã như trên in `false`. Nếu thay khai báo bằng `final String a = "a";`, `a` trở thành constant variable; phép nối `a + "b"` là constant expression và kết quả so sánh được in là `true`.

**Câu 3.** Vì sao ví dụ này vẫn nối lúc chạy?

```java
final String a = new String("a");
String x = a + "b";
```

**Đáp án:** `a` không phải constant variable vì initializer dùng `new`, vốn không là constant expression.

**Câu 4.** `String t = dynamic + "ab";` có nên dùng `t.intern() == "someValue"` để kiểm tra nội dung không?

**Đáp án:** Không cần. Khi muốn kiểm tra văn bản, dùng `"someValue".equals(t)`. `intern()` phục vụ chuẩn hóa tham chiếu, không phải công cụ so sánh nội dung thông thường.

**Câu 5.** “Tính khi biên dịch” và “intern lúc chạy” có mâu thuẫn không?

**Đáp án:** Không. Compiler xác định **giá trị hằng của biểu thức**, còn JVM sử dụng cơ chế **chuỗi chuẩn hóa trong runtime** để biểu diễn và chia sẻ đối tượng `String` tương ứng.

## 10. Điểm cần nhớ

Một biểu thức nối chuỗi chỉ được xem là **constant expression** khi các thành phần tuân theo đúng quy tắc ngôn ngữ Java; literal và **constant variable** là những trường hợp thường gặp. Giá trị `String` của constant expression được intern, nên các chuỗi hằng có cùng nội dung dùng chung tham chiếu chuẩn hóa. Phép nối sử dụng biến không hằng hay lời gọi phương thức là nối runtime, dù nội dung cuối cùng trông giống hệt. Hãy dùng kiến thức về string pool để hiểu Java, và dùng `.equals(...)` khi cần kiểm tra nội dung văn bản.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-pool-intern-003">Phân biệt nối hằng với nối giá trị lúc chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt nối chuỗi hằng và nối giá trị lúc chạy trong Java

Phép toán `+` có thể nối các `String` thành một chuỗi mới, nhưng **không phải mọi biểu thức nối chuỗi đều được tính ở cùng thời điểm**. Java phân biệt:

- **Nối chuỗi bằng biểu thức hằng tại thời điểm biên dịch** (*compile-time constant expression*): trình biên dịch có thể tính trước toàn bộ kết quả. `"Java" + "Core"` được xem như chuỗi literal `"JavaCore"` và kết quả hằng được **intern**.
- **Nối chuỗi lúc chương trình chạy** (*runtime concatenation*): có ít nhất một toán hạng không tạo thành biểu thức hằng theo quy tắc ngôn ngữ. Giá trị chuỗi được ghép khi thực thi, ngay cả khi tại thời điểm đó nó có nội dung giống một literal có sẵn.

Sự khác biệt quan trọng khi học **String Pool, `intern()`, tính chất `final`, so sánh `==` và `equals()`**, cũng như khi đọc bytecode và đánh giá chi phí nối chuỗi.

## 1. Ranh giới giữa nối hằng và nối runtime

**Nối hằng** chỉ xảy ra khi toàn bộ biểu thức thỏa định nghĩa *constant expression* của Java: compiler xác định giá trị trước khi chương trình chạy. **Nối runtime** cần đọc giá trị không phải constant expression lúc thực thi; kết quả có thể cùng nội dung literal nhưng không tự động được intern.

~~~java
String literal = "Java";
String folded = "Ja" + "va";       // Hằng tại biên dịch
final String constant = "Ja";
String foldedFinal = constant + "va";
String variable = "Ja";
String runtime = variable + "va";   // Nối lúc chạy
~~~

`folded == literal` và `foldedFinal == literal` là `true`, còn `runtime == literal` là `false`; `runtime.equals(literal)` vẫn `true`. `final String` chỉ trở thành **constant variable** khi được khai báo kèm initializer là constant expression; `final String s = new String("Ja")` không thỏa. Bài về biểu thức hằng đã đi sâu vào các ngoại lệ, cách compiler nội tuyến hằng và ảnh hưởng đến API.

Trọng tâm tiếp theo là phép nối **khi runtime thực thi**, thứ tự dấu `+`, bytecode và kỹ thuật nối nhiều phần.

## 2. Liên hệ với String Pool và phép so sánh

**String Pool** là cơ chế Java duy trì một phiên bản chuẩn (*canonical representation*) cho các chuỗi đã được intern. Chuỗi literal và chuỗi kết quả của constant expression kiểu `String` được intern theo ngữ nghĩa ngôn ngữ. Hai biểu thức hằng có cùng nội dung vì vậy có thể tham chiếu cùng đối tượng `String`.

Với nối lúc chạy, nội dung có thể giống hệt chuỗi trong pool, nhưng **không vì thế mà phép nối tự động sử dụng tham chiếu trong pool**.

Hãy biên dịch cả class sau bằng Java 17+.

Lưu tệp `ConcatIdentityDemo.java`:

```java
public class ConcatIdentityDemo {
    public static void main(String[] args) {
        String pooled = "JavaCore";

        // Hai cách tạo constant expression
        String folded = "Java" + "Core";
        final String constantPrefix = "Java";
        final String constantSuffix = "Core";
        String foldedFinals = constantPrefix + constantSuffix;

        // Hai cách nối lúc chạy
        String variablePrefix = "Java";
        String runtime = variablePrefix + "Core";

        final String notConstantPrefix = new String("Java");
        String runtimeFinal = notConstantPrefix + "Core";

        System.out.println(folded == pooled);
        System.out.println(foldedFinals == pooled);
        System.out.println(runtime == pooled);
        System.out.println(runtime.equals(pooled));
        System.out.println(runtimeFinal == pooled);
        System.out.println(runtimeFinal.intern() == pooled);
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ConcatIdentityDemo.java
java ConcatIdentityDemo
```

Kết quả:

```text
true
true
false
true
false
true
```

Giải thích theo từng dòng:

1. `folded == pooled` là `true` vì `"Java" + "Core"` được gộp thành chuỗi hằng `"JavaCore"` và dùng tham chiếu interned.
2. `foldedFinals == pooled` là `true` vì hai biến `final` nhận initializer là literal, nên là constant variables; `constantPrefix + constantSuffix` cũng là biểu thức hằng.
3. `runtime == pooled` là `false`: `variablePrefix` không phải constant variable, phép nối diễn ra lúc chạy và kết quả không tự động là đối tượng interned `"JavaCore"`.
4. `runtime.equals(pooled)` là `true`: hai chuỗi chứa **cùng nội dung**.
5. `runtimeFinal == pooled` là `false`: `new String("Java")` được đánh giá khi chạy; có `final` cũng không làm biểu thức nối thành hằng biên dịch.
6. `runtimeFinal.intern() == pooled` là `true`: `intern()` trả về tham chiếu canonical trong pool ứng với nội dung `"JavaCore"`.

**Không dùng `==` để so sánh nội dung chuỗi.** `==` so tham chiếu đến đối tượng, nên hai `String` cùng chữ có thể cho kết quả khác nhau tùy đường tạo chuỗi. `equals()` so nội dung; với hai giá trị có thể chứa `null`, dùng `Objects.equals(a, b)` nếu muốn kết quả boolean an toàn.

## 3. Phép cộng số và thứ tự đánh giá khi nối chuỗi

`+` có thể là **cộng số học** hoặc **nối chuỗi** tùy kiểu toán hạng. Java đánh giá biểu thức theo thứ tự, và dấu ngoặc có thể làm thay đổi kết quả.

Ví dụ độc lập ở `ConcatOrderDemo.java`:

```java
public class ConcatOrderDemo {
    public static void main(String[] args) {
        System.out.println("A" + 1 + 2);
        System.out.println(1 + 2 + "A");
        System.out.println("A" + (1 + 2));

        final int constantNumber = 2;
        int variableNumber = 2;

        System.out.println(("v" + constantNumber) == "v2");
        System.out.println(("v" + variableNumber) == "v2");
        System.out.println(("v" + variableNumber).equals("v2"));
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 ConcatOrderDemo.java
java ConcatOrderDemo
```

Kết quả:

```text
A12
3A
A3
true
false
true
```

- `"A" + 1 + 2` thực hiện nối từ trái sang phải, thành `"A1"` rồi `"A12"`.
- `1 + 2 + "A"` cộng hai số thành `3` trước, rồi nối `"A"`, được `"3A"`.
- `"A" + (1 + 2)` buộc cộng trong ngoặc, nên được `"A3"`.
- `constantNumber` là constant variable (kiểu `int`, `final`, khởi tạo bằng literal), nên `"v" + constantNumber` là constant expression và đã được intern.
- `variableNumber` không phải constant variable, nên `"v" + variableNumber` được thực hiện lúc chạy; so nội dung vẫn bằng `true` với `equals()`.

Một phép tính có thể tạo ra kết quả đúng về nội dung nhưng diễn ra lúc chạy. **“Biểu thức tạo chuỗi cố định ở lần chạy này”** không đồng nghĩa với **“constant expression theo JLS”**.

## 4. Nhìn từ lúc biên dịch và bytecode

Ở cấp ngôn ngữ Java, điểm cần phân biệt là **khi nào kết quả là constant expression**; cách compiler/JVM thực hiện phép nối lúc chạy là chi tiết triển khai.

Với `javac` hiện đại trên Java 17+, lệnh:

```bash
javap -c -p ConcatIdentityDemo
```

cho phép xem bytecode của class đã biên dịch. Những dấu hiệu thường gặp:

- Với `"Java" + "Core"`, có thể thấy mã nạp literal đã gộp `"JavaCore"`, chẳng hạn instruction `ldc`; không cần một phép nối runtime để hình thành kết quả này.
- Với `variablePrefix + "Core"`, `javac` hiện đại thường tạo `invokedynamic`, bootstrap qua `java.lang.invoke.StringConcatFactory`. Thông tin nối chuỗi/đối số được dùng khi chương trình chạy.
- Các trình biên dịch hoặc phiên bản Java cũ có thể sinh `StringBuilder.append(...)` để nối lúc chạy. Java 9+ đã đưa ra chiến lược dựa trên `invokedynamic` qua JEP 280; vì vậy **không nên khẳng định mọi phép nối runtime đều được biên dịch thành `new StringBuilder()` trong bytecode**.
- JIT/JVM có thể tối ưu phân bổ và ghép chuỗi trong lúc chạy mà không làm thay đổi ngữ nghĩa chương trình. Không suy ra số lần cấp phát thực tế chỉ từ một đoạn mã nguồn hoặc một phép đo `==`.

Mô hình tư duy:

```text
"Java" + "Core"
    -> toàn constant expression
    -> compiler tính "JavaCore"
    -> tham chiếu tới kết quả hằng được intern

variablePrefix + "Core"
    -> có toán hạng không phải compile-time constant
    -> phép nối runtime
    -> tạo nội dung mới theo giá trị hiện tại
    -> KHÔNG mặc nhiên nhận tham chiếu String Pool
```

Hai đường đi có thể cho **chuỗi ký tự giống nhau**, nhưng cách xác định thời điểm nối và quan hệ tham chiếu với pool là khác nhau.

## 5. Tương tác với toán tử `+=`, vòng lặp và StringBuilder

`+=` trên `String` có ý nghĩa tương đương về mặt nối chuỗi với việc lấy giá trị hiện tại rồi nối thêm phần mới, sau đó gán lại biến.

```java
String value = "a";
value += "b"; // giá trị hiện tại + "b", được đánh giá lúc chạy
```

`value` là biến thông thường, không phải constant variable. Kể cả sau câu lệnh, `value` có nội dung `"ab"`, **không nên dựa trên tham chiếu của nó** khi so với literal `"ab"`.

Với các đoạn nối cố định và ngắn, toán tử `+` đọc dễ và `javac`/JVM có cơ chế tối ưu. Nhưng khi nối lặp lại nhiều phần bằng một biến tích lũy, nên cân nhắc `StringBuilder` để tránh tạo các chuỗi trung gian lặp đi lặp lại:

```java
import java.util.List;

public class JoinPartsDemo {
    public static void main(String[] args) {
        List<String> parts = List.of("Java", " ", "Core");

        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            builder.append(part);
        }

        String result = builder.toString();
        System.out.println(result);
        System.out.println(result.equals("Java Core"));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 JoinPartsDemo.java
java JoinPartsDemo
```

Kết quả:

```text
Java Core
true
```

`StringBuilder` gom các phần có thể thay đổi vào vùng đệm, phù hợp với vòng lặp nhiều lần; `toString()` trả về `String` kết quả. Với các trường hợp kết hợp chuỗi bằng dấu phân tách, `String.join(...)` hoặc `Collectors.joining(...)` có thể diễn đạt ý định rõ hơn. Không dùng benchmark đơn lẻ hoặc suy đoán từ `+` để kết luận chắc chắn mức hiệu năng; cần đo với khối lượng thực tế khi tối ưu là yêu cầu.

## 6. Quy trình kiểm tra trong bài tập và thực tế

Khi bắt gặp một phép nối chuỗi, hãy tự hỏi theo thứ tự:

1. **Tất cả toán hạng có tham gia constant expression không?** Phân biệt literal, phép toán hằng được phép và constant variable với dữ liệu từ biến/phương thức.
2. **Kết quả có được tính lúc biên dịch hay phải chờ chạy?** Nếu toàn bộ là constant expression kiểu `String` thì được gộp và intern theo quy tắc Java; nếu không thì là nối runtime.
3. **Đang kiểm tra nội dung hay tham chiếu?** Dùng `equals()` cho nội dung; chỉ dùng `==` nếu mục tiêu thực sự là danh tính đối tượng hoặc tìm hiểu intern.
4. **Đang ghép một biểu thức ngắn hay một vòng lặp dài?** Viết `+` cho mã đơn giản, `StringBuilder`/công cụ join khi có nhiều phần hoặc tích lũy liên tục.
5. **Cần khẳng định chiến lược compiler/JVM không?** Xem bytecode bằng `javap` và kiểm thử trên JDK mục tiêu; đừng biến dấu hiệu của một phiên bản thành quy tắc áp dụng cho mọi bản Java.

### Tự kiểm tra

| Biểu thức | Constant expression? | Lý do |
| --- | --- | --- |
| `"a" + "b"` | Có | Chỉ gồm literal |
| `"n=" + (2 * 3)` | Có | Phép toán số học là hằng |
| `final String a = "a"; a + "b"` | Có | `a` là constant variable |
| `String a = "a"; a + "b"` | Không | `a` không có `final` |
| `final String a = new String("a"); a + "b"` | Không | Initializer của `a` được tính lúc chạy |
| `"a" + args.length` | Không | `args.length` chỉ biết lúc chạy |
| `String.valueOf(1) + "a"` | Không | Có lời gọi phương thức |
| `"v" + 1` | Có | Literal `String` + literal nguyên thủy |

Các dòng có khai báo biến trong bảng là **đoạn mã minh họa đặt trong cùng scope**; đây không phải các biểu thức Java độc lập có thể copy nguyên bảng vào một file `.java`.

**Ghi nhớ:** Nối hằng là trường hợp compiler xác định toàn bộ nội dung từ *constant expression* và kết quả được intern. Nối lúc chạy sử dụng giá trị thật khi thực thi, không mặc nhiên chia sẻ đối tượng trong String Pool. Việc so sánh hai `String` nên dựa vào **`equals()`**, không dựa vào sự tình cờ của `==`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-pool-intern-004">new String tạo đối tượng và quan hệ với literal có sẵn</a>

<details>
<summary>Xem chi tiết</summary>

# `new String` tạo đối tượng và quan hệ với literal có sẵn trong Java

Khi gặp hai cách viết `String a = "Java";` và `String b = new String("Java");`, cả `a` và `b` đều biểu diễn nội dung `Java`, nhưng **khác nhau về danh tính đối tượng (object identity)**. Literal được Java quản lý theo cơ chế **string pool**; biểu thức `new String(...)` tạo một đối tượng `String` mới theo ngữ nghĩa của ngôn ngữ, ngay cả khi pool đã có một đối tượng chứa cùng nội dung.

Bài này làm rõ điều gì được tái sử dụng, điều gì được tạo mới, cách `==`, `equals()` và `intern()` liên quan với nhau, và khi nào thực sự cần dùng constructor `String`.

## 1. Hai kiểu khởi tạo: literal và `new String(...)`

### Dùng literal

```java
String a = "Java";
String b = "Java";
```

- `"Java"` là một **string literal** (chuỗi literal) trong mã nguồn.
- Các string literal có cùng dãy ký tự được **intern**: trong cùng một lần chạy chương trình Java, các tham chiếu đến giá trị literal tương đương sẽ sử dụng đại diện chuẩn (canonical representation) của string pool.
- Với ví dụ này, `a == b` là `true` vì cả hai biến cùng tham chiếu một đối tượng `String` chuẩn.
- Việc gán `b = "Java"` không yêu cầu tạo thêm một đối tượng `String` mới có nội dung `Java`.

### Dùng constructor `new String(String original)`

```java
String a = "Java";
String b = new String("Java");
```

- Literal `"Java"` là đầu vào của constructor và có đại diện trong string pool.
- `new String("Java")` biểu thị việc **tạo một đối tượng `String` khác** với đối tượng tham chiếu bởi literal. Giá trị nội dung của đối tượng mới bằng giá trị literal.
- `a == b` là `false`, còn `a.equals(b)` là `true`.
- Constructor không tự đưa đối tượng `b` trở thành đại diện chuẩn của pool. Muốn lấy đại diện chuẩn, có thể gọi `b.intern()`.

Cần phân biệt **ngữ nghĩa tạo đối tượng** với việc JVM phải cấp phát bao nhiêu bộ nhớ tại thời điểm thực thi. Trình biên dịch JIT có thể loại bỏ một cấp phát không quan sát được khi tối ưu hóa, nhưng chương trình vẫn phải biểu hiện đúng quy tắc danh tính và so sánh đối tượng của Java. Vì thế không nên khẳng định chung chung rằng một dòng `new String("Java")` *luôn* làm JVM cấp phát chính xác hai đối tượng mới: literal có thể đã tồn tại trong pool từ trước, còn cách cấp phát vật lý có thể được tối ưu.

## 2. String pool, heap và tham chiếu: sơ đồ tư duy

Hình dung ví dụ:

```java
String literal = "Java";
String sameLiteral = "Java";
String constructed = new String("Java");
String copied = new String(literal);
```

Quan hệ tham chiếu theo **ngữ nghĩa Java**:

```text
literal -----------+
                   |
sameLiteral -------+----> [String chuẩn: "Java"]  (đại diện trong pool)

constructed ------------> [String khác: "Java"]

copied -----------------> [String khác nữa: "Java"]
```

Hai đối tượng tạo bởi `new` có nội dung bằng với literal nhưng là các đối tượng `String` riêng biệt về danh tính. Không được nhầm sơ đồ danh tính đối tượng với sơ đồ cấp phát vùng nhớ nội bộ. Trên các JVM hiện đại, đối tượng trong pool cũng được quản lý như những đối tượng Java; pool không phải một khu vực bộ nhớ “ngoài heap” dành riêng cho literal. Ngoài ra, các đối tượng `String` bất biến có thể sử dụng các cách lưu trữ dữ liệu bên trong tùy JVM mà không làm thay đổi hợp đồng API.

**Điểm cốt lõi:** String pool lưu và trả về tham chiếu đại diện cho nội dung chuỗi, còn `new String(...)` là yêu cầu tạo một đối tượng khác để giữ cùng giá trị.

## 3. Ví dụ Java 17+: kiểm tra `==`, `equals()` và `intern()`

Tạo tệp `NewStringVsLiteral.java`:

```java
public class NewStringVsLiteral {
    public static void main(String[] args) {
        String literal = "Java";
        String sameLiteral = "Java";
        String constructed = new String("Java");
        String copied = new String(literal);

        System.out.println("literal == sameLiteral: "
                + (literal == sameLiteral));
        System.out.println("literal == constructed: "
                + (literal == constructed));
        System.out.println("literal == copied: "
                + (literal == copied));
        System.out.println("constructed == copied: "
                + (constructed == copied));

        System.out.println("literal.equals(constructed): "
                + literal.equals(constructed));
        System.out.println("constructed.equals(copied): "
                + constructed.equals(copied));

        System.out.println("constructed.intern() == literal: "
                + (constructed.intern() == literal));
        System.out.println("copied.intern() == literal: "
                + (copied.intern() == literal));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 NewStringVsLiteral.java
java NewStringVsLiteral
```

Kết quả:

```text
literal == sameLiteral: true
literal == constructed: false
literal == copied: false
constructed == copied: false
literal.equals(constructed): true
constructed.equals(copied): true
constructed.intern() == literal: true
copied.intern() == literal: true
```

Giải thích từng nhóm kết quả:

1. Hai biến dùng cùng literal `"Java"` cùng trỏ đến tham chiếu chuẩn nên `==` trả về `true`.
2. Mỗi lần thực thi biểu thức `new String(...)` tạo một đối tượng riêng theo ngữ nghĩa Java; `constructed` và `copied` khác tham chiếu nhau và khác `literal`.
3. `equals()` do `String` triển khai để so sánh **chuỗi ký tự**; vì ba đối tượng có cùng nội dung nên kết quả bằng `true`.
4. `intern()` trả về tham chiếu chuẩn trong pool cho chuỗi cùng nội dung. Vì literal `"Java"` đã có đại diện chuẩn, `constructed.intern()` và `copied.intern()` cùng trỏ đến đối tượng đó.

Bạn có thể thay chuỗi `"Java"` bằng một nội dung khác và áp dụng cùng quy tắc. Điều được đảm bảo là **quan hệ giữa các tham chiếu**, không phải một con số cố định về đối tượng thực tế nằm trong bộ nhớ JVM tại mọi thời điểm.

## 4. Vì sao `==` và `equals()` cho kết quả khác nhau?

| Phép kiểm tra | So sánh gì? | Ví dụ với literal và đối tượng `new` |
| --- | --- | --- |
| `a == b` | Hai biến có trỏ đến **cùng một đối tượng** không? | `"Java"` và `new String("Java")` → `false` |
| `a.equals(b)` | Hai chuỗi có **cùng dãy ký tự** không? | `"Java"` và `new String("Java")` → `true` |
| `a.equalsIgnoreCase(b)` | Nội dung có tương đương theo quy tắc so sánh không phân biệt hoa/thường của phương thức không? | `"Java"` và `"JAVA"` → `true` |
| `a.intern() == b.intern()` | Hai giá trị có cùng đại diện chuẩn trong pool không? | Hai chuỗi bằng nội dung → `true` sau khi intern |

Trong phần lớn logic nghiệp vụ, mục tiêu là so sánh **giá trị văn bản**, nên dùng `equals()` hoặc `Objects.equals(a, b)` nếu có thể có `null`. Sự tình cờ `==` trả về `true` với literal không khiến `==` trở thành cách đúng để so sánh nội dung mọi `String`.

Ví dụ dễ gây lỗi:

```java
String expected = "OK";
String actual = new String("OK");

// Sai mục đích nếu muốn so sánh nội dung:
if (actual == expected) {
    System.out.println("Match");
}

// Đúng mục đích:
if (expected.equals(actual)) {
    System.out.println("Match");
}
```

Đây là đoạn minh họa đặt trong một phương thức. Chỉ nhánh dùng `equals()` in `Match`. Việc `expected.equals(actual)` đặt literal/giá trị đã biết không null ở phía trước cũng tránh lỗi `NullPointerException` nếu `actual` bất ngờ là `null`.

## 5. `String.intern()` trả về gì khi đã có literal?

Phương thức `String.intern()` tìm đại diện chuẩn trong string pool cho **nội dung** của đối tượng nhận lời gọi:

- Nếu pool đã có một `String` có nội dung bằng nhau theo `equals()`, `intern()` trả về đối tượng đã có.
- Nếu chưa có, đối tượng tương ứng sẽ được đưa vào pool và trở thành đại diện chuẩn, theo hợp đồng `intern()`.
- `intern()` **không đổi nội dung** của `String` và không có nghĩa rằng biến nhận lời gọi tự chuyển sang tham chiếu mới.

Với `String a = new String("Java");`, literal `"Java"` đã có trong mã nguồn và được intern. Do đó:

```java
String literal = "Java";
String differentObject = new String("Java");
String canonical = differentObject.intern();

System.out.println(differentObject == literal); // false
System.out.println(canonical == literal);       // true
System.out.println(differentObject == canonical); // false
```

Các dòng trên là ví dụ minh họa có thể đặt trong `main`. Nếu muốn biến `differentObject` sau đó trỏ đến tham chiếu chuẩn, cần **gán lại**: `differentObject = differentObject.intern();`. Lời gọi `intern()` đơn lẻ không thay đổi biến gốc.

Khi không có literal tương ứng trong mã và nội dung được tạo động, không nên đoán trước **đại diện chuẩn là đối tượng nào** hoặc cố xác định chính xác thời điểm một chuỗi bắt đầu có trong pool. Điều chắc chắn để dùng là: hai chuỗi bằng nội dung, sau khi intern, sẽ tham chiếu đến cùng một đại diện chuẩn.

**Không nên gọi `intern()` cho mọi chuỗi** theo thói quen. Pool cần quản lý các giá trị chuẩn, và intern một tập rất lớn các chuỗi động ít trùng lặp có thể làm tăng chi phí bộ nhớ hoặc xử lý. Hãy chỉ cân nhắc khi có nhu cầu thực tế về chuẩn hóa tham chiếu cho nhiều giá trị lặp và đã đo lường tác động.

## 6. Literal, biểu thức hằng lúc biên dịch và nối chuỗi khi chạy

Java có thêm quy tắc quan trọng: **string constant expressions** (biểu thức hằng kiểu `String`) cũng được intern. Ví dụ `"Ja" + "va"` là biểu thức hằng tại thời điểm biên dịch nên tương ứng với literal `"Java"`.

Nhưng phép nối có một toán hạng không phải biểu thức hằng được thực hiện lúc chạy, tạo ra giá trị `String` có nội dung tương ứng và thường khác tham chiếu với literal, cho đến khi bạn chủ động `intern()`.

Tệp `ConstantVsRuntime.java`:

```java
public class ConstantVsRuntime {
    public static void main(String[] args) {
        String literal = "Java";
        String folded = "Ja" + "va";

        String suffix = new String("va");
        String joinedAtRuntime = "Ja" + suffix;

        System.out.println("folded == literal: "
                + (folded == literal));
        System.out.println("joinedAtRuntime == literal: "
                + (joinedAtRuntime == literal));
        System.out.println("joinedAtRuntime.equals(literal): "
                + joinedAtRuntime.equals(literal));
        System.out.println("joinedAtRuntime.intern() == literal: "
                + (joinedAtRuntime.intern() == literal));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 ConstantVsRuntime.java
java ConstantVsRuntime
```

Kết quả:

```text
folded == literal: true
joinedAtRuntime == literal: false
joinedAtRuntime.equals(literal): true
joinedAtRuntime.intern() == literal: true
```

Lý do: `folded` là kết quả nối **hai literal**; trình biên dịch xác định giá trị hằng `"Java"` và dùng đại diện chuẩn. Ngược lại, `suffix` được nhận từ biểu thức khởi tạo đối tượng `new String("va")`, nên `"Ja" + suffix` không phải biểu thức hằng theo quy tắc ngôn ngữ. Kết quả nối có nội dung `"Java"` nhưng không tự trở thành tham chiếu chuẩn của literal.

Một biến `final String` được khởi tạo bằng biểu thức hằng cũng có thể là **constant variable**:

```java
final String prefix = "Ja";
String stillConstant = prefix + "va";
System.out.println(stillConstant == "Java"); // true
```

Ngược lại, không phải cứ đặt `final` là mọi phép nối đều trở thành hằng tại lúc biên dịch: giá trị khởi tạo còn phải thỏa điều kiện *constant expression*. Đây là nguyên nhân khiến một số bài kiểm tra `==` với nối chuỗi có vẻ cho kết quả “lúc true, lúc false”. Vì thế, hãy dùng `equals()` cho nội dung.

## 7. `new String` với nguồn không phải literal

Phạm vi của constructor không chỉ có literal. Java cũng hỗ trợ tạo `String` từ một đối tượng `String` đã có, từ `char[]`, từ các đoạn của mảng hoặc từ byte kèm bộ mã hóa phù hợp.

### Tạo từ một `String` có sẵn

```java
String original = "data";
String copy = new String(original);

System.out.println(original == copy);      // false
System.out.println(original.equals(copy)); // true
```

Cả hai có nội dung bằng nhau, nhưng constructor vẫn tạo một đối tượng mới theo ngữ nghĩa Java. Tuy vậy, vì `String` **bất biến**, việc tạo đối tượng mới chỉ để “bảo vệ nội dung” khỏi bị sửa là không cần thiết. Cũng không nên tự suy luận rằng constructor này bắt buộc tạo bản sao độc lập của toàn bộ vùng nhớ chứa ký tự nội bộ; đó là chi tiết triển khai JVM, không phải lý do để dùng constructor.

### Tạo từ `char[]`: lấy một bản nội dung tại thời điểm tạo

Một mảng `char[]` **có thể thay đổi**, nên trường hợp này có ý nghĩa khác.

Tệp `NewStringFromArray.java`:

```java
public class NewStringFromArray {
    public static void main(String[] args) {
        char[] letters = {'J', 'a', 'v', 'a'};
        String fromArray = new String(letters);
        String literal = "Java";

        letters[0] = 'X';

        System.out.println("array=" + new String(letters));
        System.out.println("fromArray=" + fromArray);
        System.out.println("sameContent=" + fromArray.equals(literal));
        System.out.println("sameReference=" + (fromArray == literal));
    }
}
```

Chạy:

```bash
javac -encoding UTF-8 NewStringFromArray.java
java NewStringFromArray
```

Kết quả:

```text
array=Xava
fromArray=Java
sameContent=true
sameReference=false
```

`new String(letters)` lấy nội dung mảng tại thời điểm tạo. Sửa `letters` sau đó không sửa `fromArray`; đây là hợp đồng quan trọng giữa nguồn mutable `char[]` và đích immutable `String`. Giá trị `fromArray` vẫn bằng literal `"Java"`, nhưng vì được tạo qua `new` nên không cùng tham chiếu với literal.

Tương tự, có constructor `new String(StringBuilder)` hoặc `new String(StringBuffer)` để tạo `String` từ nội dung hiện tại của vùng đệm. Tuy nhiên, khi đã có một `String` hợp lệ, `new String(existingString)` thường không cung cấp thêm giá trị.

### Tạo từ byte cần biết charset

`new String(bytes, StandardCharsets.UTF_8)` có nhiệm vụ **giải mã byte** theo UTF-8; đây không phải là thao tác “copy một literal”. Khi sử dụng các constructor từ byte, cần truyền charset rõ ràng phù hợp với dữ liệu đầu vào để tránh sai ký tự. Các constructor nhận `String` và `char[]` không giải quyết bài toán giải mã byte.

## 8. Có nên dùng `new String("literal")` trong ứng dụng?

**Thông thường không cần.** Nếu chỉ muốn sử dụng một nội dung văn bản cố định, gán trực tiếp literal vừa ngắn gọn vừa tận dụng cơ chế tái sử dụng đại diện chuẩn:

```java
String status = "READY"; // Phù hợp cho giá trị hằng đã biết
```

Nếu đã có đối tượng `String` từ một phương thức hay tham số, chỉ cần dùng lại giá trị đó:

```java
static String normalizeStatus(String status) {
    return status == null ? "UNKNOWN" : status.trim();
}
```

Đây là phương thức minh họa đặt trong một lớp. Dùng `new String(status)` trước hoặc sau `trim()` không giúp so sánh chuỗi an toàn hơn và không ngăn người khác sửa nội dung của `String` (vì nội dung vốn đã bất biến).

Các lý do **không hợp lệ** để thêm `new String("...")` tùy tiện:

- “Để chuỗi không bị sửa từ bên ngoài”: `String` vốn bất biến.
- “Để luôn tạo chuỗi khác nội dung”: constructor tạo khác **đối tượng**, không làm nội dung khác.
- “Để so sánh bằng `==` cho chính xác”: làm việc so sánh tham chiếu khác đi; so sánh giá trị vẫn cần `equals()`.
- “Để tiết kiệm bộ nhớ nhờ string pool”: `new` không phải cơ chế tái sử dụng đại diện chuẩn.
- “Để copy chắc chắn các byte/char nội bộ”: chi tiết chia sẻ hay sao chép dữ liệu bên trong `String` không phải hợp đồng của constructor nhận `String`.

**Dùng constructor khi đầu vào thực sự cần chuyển đổi sang `String`**, như `char[]`, `StringBuilder`, `StringBuffer`, hay byte đã xác định charset. Với nguồn đã là `String`, constructor sao chép theo ngữ nghĩa đối tượng hiếm khi cần thiết.

## 9. `null`, identity và các lỗi thường gặp

### Truyền `null` vào constructor

Một tham chiếu `String` bằng `null` không phải đối tượng chuỗi rỗng:

```java
String absent = null;
// String copy = new String(absent); // Ném NullPointerException khi chạy
```

Đây là đoạn minh họa bên trong một phương thức. Việc viết trực tiếp `new String(null)` cũng không nên dùng để thử nghiệm, bởi `String` có nhiều constructor quá tải nên literal `null` trần có thể gây lỗi **không xác định được overload** ngay lúc biên dịch. Nếu cần chuỗi rỗng theo quy ước nghiệp vụ, hãy kiểm tra `null` và trả về `""` một cách tường minh.

### Những lỗi thiết kế phổ biến

1. **Giả định hai chuỗi bằng nội dung luôn `==` nhau.** Chỉ có thể dựa vào quan hệ tham chiếu khi bạn chủ động làm việc với pool/identity; để so sánh văn bản, dùng `equals()`.
2. **Tưởng `new String("x")` làm xuất hiện một nội dung mới.** Giá trị vẫn là `"x"`; khác biệt là danh tính đối tượng.
3. **Đếm số đối tượng từ số literal và số chữ `new` trong mã nguồn.** Pool có thể đã chứa giá trị, literal có thể xuất hiện nhiều nơi, và JVM có quyền tối ưu hóa cấp phát nếu không thay đổi hành vi quan sát được.
4. **Tưởng `intern()` thay đổi biến gốc.** `intern()` trả về một tham chiếu; cần nhận và dùng giá trị trả về nếu mục tiêu là dùng tham chiếu chuẩn.
5. **Dùng `new String(existingString)` để phòng vệ với dữ liệu immutable.** Bản thân `String` không bị sửa tại chỗ; việc tạo object mới thường thừa.
6. **Tưởng mọi phép nối chuỗi đều dùng string pool.** Biểu thức hằng được intern; nối có toán hạng động tuân theo quy tắc khác và không tự chuyển thành đại diện chuẩn.
7. **Tự động intern tất cả giá trị đầu vào.** Cần cân nhắc số chuỗi duy nhất, số lượng trùng lặp và tài nguyên thực tế; intern không thay thế việc thiết kế cache/deduplication có giới hạn.

## 10. Câu hỏi tự kiểm tra

**Câu 1.** Đoạn sau in gì?

```java
String a = "hello";
String b = new String("hello");

System.out.println(a == b);
System.out.println(a.equals(b));
System.out.println(a == b.intern());
```

**Trả lời:** lần lượt là `false`, `true`, `true`. Constructor tạo đối tượng khác; nội dung bằng nhau; `intern()` trả về đại diện chuẩn của literal có sẵn.

**Câu 2.** Tại sao `String a = "J" + "ava";` có thể `==` với `"Java"` nhưng `String b = "J" + someVariable;` thì không nên kiểm tra theo cách này?

**Trả lời:** `"J" + "ava"` là biểu thức hằng tại lúc biên dịch và có đại diện chuẩn trong pool. Nếu `someVariable` không phải constant variable, phép nối được thực hiện lúc chạy, cho nội dung có thể bằng `"Java"` nhưng tham chiếu không nên dùng làm điều kiện so sánh nội dung.

**Câu 3.** `new String("Java")` có tự trả về đúng đối tượng nằm trong pool không?

**Trả lời:** Không. `new String("Java")` tạo đối tượng `String` riêng; gọi `intern()` trên đối tượng đó mới lấy đại diện chuẩn đã có cho literal `"Java"`.

**Câu 4.** Vì sao `new String(charArray)` hữu ích hơn `new String(existingString)` khi mục tiêu là bảo toàn nội dung trước thay đổi của nguồn?

**Trả lời:** `char[]` có thể bị sửa sau khi truyền, còn `String` bất biến. Constructor nhận mảng cung cấp một giá trị chuỗi độc lập với các thay đổi tiếp theo của mảng; tạo thêm `String` từ một `String` đã có thường không cần thiết.

**Ghi nhớ:** Literal và biểu thức hằng kiểu `String` được intern, có thể tái sử dụng tham chiếu chuẩn. `new String("...")` tạo một đối tượng chuỗi khác nhưng có nội dung bằng literal. Dùng `equals()` cho so sánh văn bản; chỉ dùng `intern()` khi thật sự cần đại diện chuẩn và đã cân nhắc chi phí.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-pool-intern-005">String.intern và tham chiếu chuẩn hóa theo nội dung</a>

<details>
<summary>Xem chi tiết</summary>

# String.intern và tham chiếu chuẩn hóa theo nội dung

Trong Java, hai đối tượng `String` có thể chứa **cùng nội dung** nhưng mang **hai tham chiếu khác nhau**. Phương thức `String.intern()` giải quyết một nhu cầu riêng: lấy **tham chiếu chuẩn dùng chung cho cùng nội dung chuỗi** từ *string pool*. Nhờ vậy, khi hai chuỗi đã được intern và có nội dung bằng nhau, kết quả `intern()` của chúng tham chiếu đến **cùng một đối tượng**.

Từ “chuẩn hóa” trong bài này có nghĩa là **chuẩn hóa tham chiếu theo nội dung (canonicalization)**; nó **không** có nghĩa tự chuẩn hóa chữ hoa/chữ thường, khoảng trắng hoặc Unicode (NFC/NFD). Để viết chương trình đúng, cần tách rõ ba thao tác: **tạo hoặc giữ đối tượng `String`**, **so nội dung bằng `equals()`**, và **lấy tham chiếu từ pool bằng `intern()`**.

## 1. Nội dung chuỗi và định danh đối tượng khác nhau

Ví dụ:

```java
String a = new String("Java");
String b = new String("Java");
```

Mỗi biểu thức `new String("Java")` tạo một **đối tượng `String` mới**, nên `a` và `b` là hai tham chiếu khác nhau, dù nội dung giống hệt:

```text
a ──────> String #1 ── nội dung "Java"
b ──────> String #2 ── nội dung "Java"
```

- `a.equals(b)` trả `true` vì `String.equals()` kiểm tra cùng dãy ký tự.
- `a == b` trả `false` vì `==` giữa hai biến kiểu tham chiếu hỏi chúng có trỏ đến **cùng đối tượng** hay không.
- Cả hai đối tượng đều **immutable**: nội dung `String` không bị sửa sau khi đối tượng hình thành. Bất biến nội dung không đồng nghĩa mọi chuỗi bằng nhau đều là một đối tượng.

Ngược lại:

```java
String x = "Java";
String y = x;
```

`y = x` chỉ sao chép giá trị **tham chiếu**, vì vậy `x == y` là `true`. Hiện tượng này không cần `intern()`: cả hai biến trỏ đến một đối tượng ngay từ phép gán.

## 2. String pool và hợp đồng của `intern()`

*String pool* là kho do lớp `String` quản lý để các chuỗi cùng nội dung có thể dùng chung một đối tượng chuẩn. Đây là cơ chế liên quan đến **định danh tham chiếu**, không thay đổi quy tắc so nội dung của `String.equals()`.

Với biến không null `s`, lời gọi:

```java
String canonical = s.intern();
```

có ý nghĩa:

1. Nếu pool đã có một `String` có nội dung `equals(s)`, `intern()` trả về **tham chiếu đến đối tượng đã có trong pool**.
2. Nếu chưa có, pool ghi nhận chính đối tượng `s` làm đại diện chuẩn và trả về tham chiếu của nó, theo hợp đồng `String.intern()`.
3. Sau đó, các lời gọi `intern()` trên chuỗi khác nhưng **cùng nội dung** sẽ nhận cùng tham chiếu chuẩn, miễn các đối tượng và nội dung được xét theo hợp đồng Java.

Đặc tính cốt lõi cho hai chuỗi **không null** `s` và `t`:

```java
s.intern() == t.intern()
```

có cùng giá trị logic với:

```java
s.equals(t)
```

Đây chính là bảo đảm cần hiểu: **nội dung bằng nhau ⇒ kết quả intern có cùng tham chiếu**; nội dung khác nhau ⇒ kết quả intern không cùng đối tượng. Không cần (và không nên) dựa vào địa chỉ bộ nhớ, thứ tự cấp phát hoặc cách JVM triển khai pool để dùng hợp đồng này.

### Sơ đồ khái niệm

Giả sử pool đã có chuỗi `"Java"`:

```text
a ────────> String #1: "Java" (ngoài đối tượng chuẩn)
                            \
                             \ a.intern()
                              v
String pool ["Java"] ──────> String chuẩn: "Java"
                              ^
                             / b.intern()
                            /
b ────────> String #2: "Java" (ngoài đối tượng chuẩn)
```

Hai lời gọi `a.intern()` và `b.intern()` dẫn tới **cùng đối tượng chuẩn**. Tuy nhiên, lời gọi **không tự gán lại** biến `a` hay `b`. Muốn một biến giữ tham chiếu chuẩn, phải dùng kết quả: `a = a.intern();`.

### Điều gì không được suy ra?

- `s.intern()` **không nhất thiết** trả đối tượng khác `s`. Nếu chính `s` được chọn làm đại diện chuẩn (hoặc đã là đại diện chuẩn), có thể có `s == s.intern()`. Ngược lại, khi pool đã có đại diện khác, biểu thức đó có thể là `false`.
- `intern()` không sửa nội dung của `s` và không “hợp nhất” tất cả các biến đang giữ các đối tượng khác nhau.
- Hai đối tượng `String` được tạo độc lập không trở thành cùng đối tượng chỉ vì cùng gọi `equals()`. `equals()` là phép kiểm tra nội dung, không đổi tham chiếu.
- `intern()` là phương thức của đối tượng; gọi qua `null` sẽ gây `NullPointerException`.

## 3. Ví dụ hoàn chỉnh: intern trả đối tượng chuẩn, biến cũ vẫn giữ nguyên

Lưu mã dưới tên **`InternReferenceDemo.java`**:

```java
public class InternReferenceDemo {
    public static void main(String[] args) {
        String literal = "Java";
        String fromNew = new String("Java");
        String fromBuilder = new StringBuilder()
                .append("Ja")
                .append("va")
                .toString();

        String canonical = fromBuilder.intern();

        System.out.println("new == literal: " + (fromNew == literal));
        System.out.println("new.equals(literal): " + fromNew.equals(literal));
        System.out.println("new.intern == literal: "
                + (fromNew.intern() == literal));

        System.out.println("builder == literal: "
                + (fromBuilder == literal));
        System.out.println("builder == canonical: "
                + (fromBuilder == canonical));
        System.out.println("canonical == literal: "
                + (canonical == literal));
        System.out.println("builder.equals(canonical): "
                + fromBuilder.equals(canonical));

        fromBuilder = fromBuilder.intern();
        System.out.println("builder after assignment == literal: "
                + (fromBuilder == literal));
    }
}
```

Tại thư mục có tệp, dùng JDK 17+:

```bash
javac InternReferenceDemo.java
java InternReferenceDemo
```

**Stdout:**

```text
new == literal: false
new.equals(literal): true
new.intern == literal: true
builder == literal: false
builder == canonical: false
canonical == literal: true
builder.equals(canonical): true
builder after assignment == literal: true
```

**Diễn giải:**

1. Literal `"Java"` được intern tự động, nên biến `literal` trỏ đến đại diện trong pool.
2. `new String("Java")` tạo một đối tượng `String` riêng. Vì vậy `fromNew == literal` là `false`, nhưng `fromNew.equals(literal)` là `true`.
3. `fromNew.intern()` trả đại diện `"Java"` trong pool, do đó có cùng tham chiếu với `literal`.
4. `StringBuilder.toString()` tạo một đối tượng kết quả, nội dung bằng `"Java"` nhưng khác đối tượng literal. `fromBuilder.intern()` tìm thấy đại diện chuẩn và trả về tham chiếu ấy.
5. Sau khi gọi `intern()`, biến `fromBuilder` **vẫn** giữ tham chiếu cũ: `fromBuilder == canonical` là `false`. Chỉ câu lệnh `fromBuilder = fromBuilder.intern();` cuối cùng mới cập nhật biến, khiến `fromBuilder == literal` trở thành `true`.

Ví dụ cố ý dùng literal có sẵn trước khi intern để kết quả tham chiếu **không phụ thuộc** vào việc một chuỗi nội dung chưa gặp lần nào sẽ được thêm vào pool ra sao.

## 4. Literal, hằng biên dịch và nối chuỗi lúc chạy

Không cần gọi `intern()` khi chỉ muốn một string literal: **các string literal của Java được intern**. Theo quy tắc của Java, các **biểu thức hằng kiểu `String`** được tính ở thời điểm biên dịch cũng có cùng đặc tính. Nhưng nối chuỗi dựa trên biến không phải hằng biên dịch là một thao tác lúc chạy; đừng coi kết quả đó là literal.

Lưu **`InternConstantDemo.java`**:

```java
public class InternConstantDemo {
    public static void main(String[] args) {
        String literal = "Java";

        String folded = "Ja" + "va";
        final String constantPart = "Ja";
        String foldedFromFinal = constantPart + "va";

        String mutablePart = "Ja";
        String runtime = mutablePart + "va";

        System.out.println("literal == folded: " + (literal == folded));
        System.out.println("literal == foldedFromFinal: "
                + (literal == foldedFromFinal));
        System.out.println("literal == runtime: "
                + (literal == runtime));
        System.out.println("literal.equals(runtime): "
                + literal.equals(runtime));
        System.out.println("literal == runtime.intern(): "
                + (literal == runtime.intern()));
    }
}
```

```bash
javac InternConstantDemo.java
java InternConstantDemo
```

**Stdout:**

```text
literal == folded: true
literal == foldedFromFinal: true
literal == runtime: false
literal.equals(runtime): true
literal == runtime.intern(): true
```

Vì sao?

- `"Ja" + "va"` là **hằng biên dịch**. Trình biên dịch coi nó là `"Java"`; đại diện literal được intern.
- `constantPart` là **constant variable**: một biến `final` kiểu `String` được khởi tạo bằng biểu thức hằng. `constantPart + "va"` cũng là biểu thức hằng, nên nhận cùng tham chiếu với literal.
- `mutablePart` không phải constant variable. `mutablePart + "va"` được nối lúc chạy, tạo đối tượng kết quả riêng; vì vậy `literal == runtime` là `false`, dù nội dung bằng nhau.
- `runtime.intern()` tìm đại diện literal đã có trong pool. Kết quả `literal == runtime.intern()` là `true`.

**Chú ý:** không phải **mọi biến `final`** đều tạo biểu thức hằng. Ví dụ `final String part = getInput();` không phải hằng biên dịch, vì giá trị chỉ biết lúc chạy. Phân biệt theo **constant expression**, không chỉ nhìn từ khóa `final`.

**Bài học thực hành:** việc `==` đôi khi cho `true` với hai chuỗi bằng nhau là hệ quả của literal/intern. Nó không biến `==` thành phép so nội dung cho các chuỗi bất kỳ.

## 5. Hai chuỗi động cùng nội dung: intern cho cùng tham chiếu dù pool có gì trước đó

Ta có thể kiểm tra thuộc tính chuẩn hóa tham chiếu mà **không cần đoán** pool đã chứa giá trị hay chưa.

**`InternInvariantDemo.java`**

```java
public class InternInvariantDemo {
    public static void main(String[] args) {
        char[] letters = {'q', 'u', 'a', 'r', 't', 'z'};

        String first = new String(letters);
        String second = new String(letters);
        String other = new String(new char[] {'Q', 'u', 'a', 'r', 't', 'z'});

        System.out.println("first == second: " + (first == second));
        System.out.println("first.equals(second): " + first.equals(second));
        System.out.println("first.intern == second.intern: "
                + (first.intern() == second.intern()));
        System.out.println("first.intern == other.intern: "
                + (first.intern() == other.intern()));
        System.out.println("first unchanged content: " + first);
    }
}
```

```bash
javac InternInvariantDemo.java
java InternInvariantDemo
```

**Stdout:**

```text
first == second: false
first.equals(second): true
first.intern == second.intern: true
first.intern == other.intern: false
first unchanged content: quartz
```

- Hai lời gọi `new String(letters)` tạo hai đối tượng khác nhau.
- Vì nội dung `first` và `second` bằng nhau, **bất kể** pool có `"quartz"` từ trước hay không, `first.intern() == second.intern()` phải là `true`.
- `other` có chữ đầu là `Q` thay vì `q`; `String.equals()` phân biệt hoa/thường, nên đại diện intern cũng khác.
- Lời gọi `intern()` không thay đổi `first`: nội dung vẫn là `"quartz"` và biến chưa được gán lại.

Nếu pool **chưa** có đại diện tương đương, đặc tả của `intern()` cho phép chính đối tượng đầu tiên được thêm vào làm đại diện. Nếu pool **đã** có, nó trả đại diện hiện hữu. Vì không kiểm soát được mọi chuỗi mà JVM/thư viện có thể đã intern trước đó, ví dụ không giả định `first == first.intern()` luôn đúng hoặc luôn sai.

## 6. “Chuẩn hóa tham chiếu” khác với chuẩn hóa văn bản Unicode

`String.intern()` dùng tiêu chí **cùng nội dung theo `String.equals()`**, nên không tự xóa khoảng trắng, đổi chữ hoa thành chữ thường, hay chuẩn hóa Unicode.

Chẳng hạn:

- `"ready"` và `"READY"` không `equals()`, vì khác chữ hoa/thường. Chúng có hai tham chiếu chuẩn khác nhau.
- `"Java"` và `"Java "` không `equals()`, vì một chuỗi có dấu cách ở cuối.
- `"\u00E9"` (é dạng dựng sẵn: một code point) và `"e\u0301"` (e và dấu sắc kết hợp: hai code point) có thể trông giống nhau nhưng **không** `equals()` và **không** trở thành một đối tượng chỉ nhờ `intern()`.

Nếu yêu cầu nghiệp vụ cần so sánh các dạng Unicode tương đương, **chuẩn hóa văn bản trước** bằng `java.text.Normalizer`, rồi mới so sánh hoặc intern nếu thật sự cần chia sẻ tham chiếu.

**`InternUnicodeDemo.java`**

```java
import java.text.Normalizer;

public class InternUnicodeDemo {
    public static void main(String[] args) {
        String composed = "\u00E9";
        String decomposed = "e\u0301";

        System.out.println("raw equals: "
                + composed.equals(decomposed));
        System.out.println("raw intern ==: "
                + (composed.intern() == decomposed.intern()));

        String normalized = Normalizer.normalize(
                decomposed, Normalizer.Form.NFC);

        System.out.println("NFC equals: "
                + composed.equals(normalized));
        System.out.println("NFC intern ==: "
                + (composed.intern() == normalized.intern()));
    }
}
```

```bash
javac InternUnicodeDemo.java
java InternUnicodeDemo
```

**Stdout:**

```text
raw equals: false
raw intern ==: false
NFC equals: true
NFC intern ==: true
```

`Normalizer.Form.NFC` giải quyết dạng Unicode tương đương trong ví dụ. Nó không mặc định giải quyết phân biệt hoa/thường, ngữ nghĩa ngôn ngữ, hay các quy tắc so sánh theo locale. Dùng `intern()` **sau** bước chuẩn hóa chỉ khi việc chia sẻ tham chiếu thật sự có ích; `equals()` trên chuỗi đã chuẩn hóa vẫn đủ để kiểm tra nội dung.

## 7. Khi nào dùng `intern()` và khi nào dùng `equals()`?

| Mục tiêu | Công cụ phù hợp | Lý do |
|---|---|---|
| Kiểm tra hai chuỗi biểu diễn cùng văn bản | `a.equals(b)`, hoặc `Objects.equals(a, b)` khi có thể null | Đúng theo nội dung mà không phụ thuộc pool |
| Đảm bảo hai chuỗi có nội dung bằng nhau dùng cùng tham chiếu chuẩn | `a.intern()`, `b.intern()` | Hai kết quả `intern()` của chuỗi bằng nhau có cùng định danh |
| So sánh hai biến có trỏ đúng cùng một đối tượng hay không | `a == b` | So sánh định danh đối tượng, không tự so nội dung |
| Lấy một literal làm chuỗi hằng | Viết `"text"` | Literal được intern tự động |
| Tìm khóa bằng văn bản trong `HashMap<String, ...>` | Để `HashMap` sử dụng `equals()`/`hashCode()` | Không cần intern để lookup đúng |
| Giảm đối tượng lặp lại của tập giá trị tương đối nhỏ, xuất hiện nhiều lần | Cân nhắc intern nếu đo đạc xác nhận có lợi | Pool cũng tốn bộ nhớ và chi phí xử lý; cần đo trên workload thực |
| Chuẩn hóa chữ Unicode hoặc so không phân biệt hoa/thường | `Normalizer` hoặc quy tắc so sánh văn bản riêng | Intern không thực hiện các phép biến đổi văn bản |

### Ví dụ: HashMap vẫn tra khóa đúng dù tham chiếu khác nhau

**`MapWithoutInternDemo.java`**

```java
import java.util.HashMap;
import java.util.Map;

public class MapWithoutInternDemo {
    public static void main(String[] args) {
        Map<String, Integer> levels = new HashMap<>();
        levels.put("READY", 7);

        String input = new String("READY");

        System.out.println("same reference: " + (input == "READY"));
        System.out.println("same content: " + input.equals("READY"));
        System.out.println("lookup: " + levels.get(input));
        System.out.println("contains: " + levels.containsKey(input));
    }
}
```

```bash
javac MapWithoutInternDemo.java
java MapWithoutInternDemo
```

**Stdout:**

```text
same reference: false
same content: true
lookup: 7
contains: true
```

`HashMap<String, Integer>` dùng `hashCode()` và `equals()` của `String` để nhận diện khóa. Việc `input` là đối tượng riêng **không** ngăn tra cứu. Nếu chỉ cần tra cứu, intern từng khóa hoặc từng đầu vào là không cần thiết và có thể gây thêm chi phí.

### So sánh an toàn khi có thể null

`intern()` và `equals()` đều gọi trên đối tượng. Với `candidate == null`, `candidate.intern()` và `candidate.equals("READY")` đều ném `NullPointerException`. Có thể dùng:

```java
"READY".equals(candidate);          // an toàn nếu candidate null
java.util.Objects.equals(a, b);      // an toàn nếu a/b null
```

Đây là **đoạn minh họa**, không phải chương trình độc lập. `Objects.equals(null, null)` trả `true` vì cả hai tham chiếu cùng null; điều đó không tạo một chuỗi rỗng hay đối tượng intern nào.

## 8. Hợp đồng của intern và các giới hạn thực tế

### 8.1. Tại sao không dùng `==` thay cho `equals()` trong mã nghiệp vụ?

Nếu **cả hai tham chiếu chắc chắn đã intern**, `==` sẽ phản ánh nội dung bằng nhau. Tuy nhiên, trong chương trình thực tế, dữ liệu có thể đến từ file, HTTP, cơ sở dữ liệu, `StringBuilder`, người dùng hoặc thư viện bên ngoài. Không có bảo đảm mọi đầu vào đã intern, trừ khi chính ứng dụng thiết kế và duy trì ràng buộc đó.

Một đoạn mã như:

```java
if (status == "READY") { /* ... */ }
```

là **mã minh họa lỗi khi dùng để so nội dung**: nó có thể vô tình chạy đúng với literal nhưng sai với một `new String("READY")` hay kết quả được tạo lúc chạy. Nếu mục tiêu là so nội dung, viết:

```java
if ("READY".equals(status)) { /* ... */ }
```

Điều này rõ ý định hơn và an toàn khi `status` có thể null.

### 8.2. Intern không thay thế quản lý dữ liệu

Không nên tự động intern **mọi** chuỗi đi vào ứng dụng:

- Mỗi lần intern cần tra cứu/duy trì dữ liệu của pool; gọi dày đặc có chi phí CPU, bộ nhớ và tương tác với quản lý bộ nhớ.
- Nếu nhiều giá trị **duy nhất** hoặc đầu vào không giới hạn số lượng, lợi ích chia sẻ nhỏ trong khi chi phí vẫn phát sinh.
- Nếu nội dung nhỏ, lặp lại rất nhiều, có thể tiết kiệm số đối tượng trùng nhau; song phải đo đạc bằng profiler/benchmark trên dữ liệu đại diện để biết có thực sự có lợi.
- Không dựa vào giả định “mọi chuỗi đã intern sống vĩnh viễn” hoặc “pool không bao giờ tốn bộ nhớ”: chính sách lưu giữ/thu gom và cách triển khai có thể khác theo JVM và phiên bản.
- Không coi intern như một cơ chế quản lý cache nghiệp vụ đầy đủ: không có metadata, giới hạn kích thước hay chính sách loại bỏ riêng theo ứng dụng để tùy chỉnh như một cache thông thường.

Nếu tập nhãn trạng thái nhỏ và được kiểm soát, có thể cân nhắc enum hoặc mã định danh nghiệp vụ thay vì ép mọi giá trị người dùng nhập thành chuỗi intern.

### 8.3. Không dùng chuỗi intern làm khóa đồng bộ tùy tiện

Ví dụ:

```java
synchronized (userId.intern()) {
    // mã minh họa: khóa có thể bị chia sẻ ngoài ý muốn
}
```

Đây là **ví dụ cần tránh**, không phải mẫu đồng bộ khuyến nghị. Mọi thành phần intern nội dung giống `userId` đều có thể nhận cùng đối tượng khóa, kể cả mã khác nằm ngoài vùng kiểm soát. Điều này có thể gây tranh chấp khóa hoặc tương tác không dự kiến giữa các thành phần. Hãy quản lý khóa riêng có vòng đời và phạm vi rõ ràng; không biến string pool chung thành hệ thống khóa nghiệp vụ.

### 8.4. Tham chiếu chuẩn không phải là giá trị được băm

`intern()` không trả về ID số, mã băm hay con trỏ bộ nhớ có thể đem lưu vào file. Nó trả về **tham chiếu `String`**. Không nên dùng `System.identityHashCode()` để thay kiểm tra `==` hoặc để xác nhận tham chiếu chuẩn: giá trị hash định danh không nhất thiết duy nhất cho mọi đối tượng. Khi cần kiểm tra nội dung, `equals()` vẫn là lựa chọn đúng.

## 9. Những hiểu nhầm thường gặp

| Hiểu nhầm | Sự thật | Cách làm đúng |
|---|---|---|
| Hai chuỗi có cùng nội dung thì `==` luôn `true` | `==` so tham chiếu; hai đối tượng riêng có thể `equals()` nhưng không `==` | Dùng `equals()` cho nội dung |
| Gọi `a.intern();` sẽ khiến biến `a` trỏ sang pool | `intern()` **trả về** tham chiếu chuẩn; không tự gán biến | Khi thật sự cần, dùng `a = a.intern();` |
| Intern tạo đối tượng mới mỗi lần | Nếu đã có đại diện, trả tham chiếu hiện hữu; nếu chưa, có thể thêm chính đối tượng gọi | Hiểu đúng hợp đồng pool |
| Mọi lời gọi `s.intern()` chắc chắn khiến `s == s.intern()` | Tùy `s` đã là đối tượng chuẩn hay chưa | Chỉ dựa vào `s.intern() == t.intern()` với chuỗi bằng nhau |
| `"Ja" + "va"` và nối biến lúc chạy có cùng hành vi tham chiếu | Biểu thức hằng được xử lý lúc biên dịch; nối lúc chạy sinh kết quả riêng | Không suy tính bằng nhau qua `==` |
| Intern sẽ xử lý Unicode, dấu, chữ hoa/thường | Pool chỉ gom những chuỗi `equals()` | Chuẩn hóa nội dung trước nếu nghiệp vụ yêu cầu |
| Muốn `HashMap` tra chuỗi chính xác phải intern | `HashMap` dựa trên `equals()` và `hashCode()` | Dùng khóa `String` bình thường |
| Càng intern nhiều càng tiết kiệm bộ nhớ | Pool và chi phí xử lý có overhead; chuỗi ít lặp lại không chắc có lợi | Đo đạc, chỉ dùng khi có lý do |
| Có thể dùng chuỗi intern làm lock mặc định | Các phần khác của JVM có thể chia sẻ cùng đối tượng chuẩn | Dùng cơ chế khóa thuộc quyền kiểm soát của ứng dụng |

## 10. Tự kiểm tra kiến thức

Đọc chương trình sau và dự đoán kết quả trước khi chạy:

**`InternSelfCheck.java`**

```java
public class InternSelfCheck {
    public static void main(String[] args) {
        String a = "cat";
        String b = new String("cat");
        String c = b.intern();

        System.out.println(a == b);
        System.out.println(a == c);
        System.out.println(b.equals(c));

        b = b.intern();
        System.out.println(a == b);

        String d = new String("CAT");
        System.out.println(a.intern() == d.intern());
    }
}
```

```bash
javac InternSelfCheck.java
java InternSelfCheck
```

**Stdout:**

```text
false
true
true
true
false
```

- `a` là literal đã intern; `b` là một đối tượng mới có cùng nội dung nên ban đầu `a == b` là `false`.
- `c = b.intern()` lấy đại diện literal `"cat"`, vì thế `a == c` là `true`.
- `b.equals(c)` là `true` bởi nội dung giống nhau, không liên quan trực tiếp đến định danh.
- `b = b.intern()` cập nhật biến `b` thành tham chiếu chuẩn nên `a == b` chuyển thành `true`.
- `"CAT"` khác `"cat"` theo `equals()`, do vậy các kết quả intern có tham chiếu khác nhau.

**Ghi nhớ:** `equals()` trả lời câu hỏi **“hai chuỗi có cùng nội dung không?”**; `intern()` trả một **tham chiếu chuẩn** cho nội dung đó; `==` trả lời câu hỏi **“hai tham chiếu có trỏ cùng một đối tượng không?”**. Khi biết chắc hai chuỗi đều đã intern, có thể dùng `==` để kiểm tra tính đồng nhất của chúng; còn trong logic ứng dụng thông thường, `equals()`/`Objects.equals()` diễn đạt đúng yêu cầu so sánh văn bản và tránh phụ thuộc vào chi tiết xuất xứ của chuỗi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-pool-intern-006">Phân biệt string pool với constant pool của class file</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt String Pool với Constant Pool của class file trong Java

**String Pool** và **constant pool trong file `.class`** đều chứa từ “pool”, nhưng phục vụ **hai nhiệm vụ hoàn toàn khác nhau**:

- **Constant pool của class file** là một **bảng cấu trúc dữ liệu nằm trong bytecode `.class`**, mô tả các hằng số và liên kết ký hiệu mà mã Java đã biên dịch sử dụng. Trong đó có thể có mô tả của một chuỗi literal, tên lớp, tên phương thức, kiểu dữ liệu, hằng số số học và nhiều thành phần khác. Đây không phải kho tập hợp các đối tượng `java.lang.String` đang chạy.
- **String Pool** là cơ chế **dùng chung đối tượng `String` theo nội dung trong lúc chương trình chạy** (*string interning*). Khi hai tham chiếu cùng trỏ tới một `String` đã được intern, chúng có cùng identity. Pool này liên quan tới Java runtime và `String.intern()`, không phải cấu trúc bảng `constant_pool` ghi trên đĩa.

Một literal như `"java"` có thể được **mô tả** bởi mục `CONSTANT_String_info` trong class file; khi JVM sử dụng literal đó, JVM thu được **tham chiếu đến một `String` được intern**. Đây là mối liên hệ giữa hai khái niệm, chứ không phải bằng chứng rằng chúng là cùng một vùng nhớ hoặc cùng một bảng.

## 1. So sánh hai khái niệm

| Tiêu chí | Constant pool của class file | String Pool (intern pool) |
|---|---|---|
| **Mục đích** | Biểu diễn hằng và các tham chiếu ký hiệu trong định dạng `.class` | Dùng chung `String` có nội dung bằng nhau |
| **Giai đoạn chính** | Sinh ra khi biên dịch/biến đổi bytecode; đọc khi JVM nạp và liên kết lớp | Được sử dụng trong quá trình thực thi |
| **Chứa gì** | Các entry có kiểu/tag như `CONSTANT_Utf8`, `CONSTANT_String`, `CONSTANT_Class`, `CONSTANT_Methodref`, `CONSTANT_Integer`, `CONSTANT_InvokeDynamic`… | Các đối tượng `String` được intern (và cơ chế ánh xạ để tìm chúng) |
| **Phạm vi mô tả** | **Mỗi class file** có bảng riêng | Cơ chế interning bảo đảm các literal/các chuỗi interned có nội dung tương đương dùng tham chiếu canonical theo hợp đồng của Java |
| **Tra cứu/quan sát** | `javap -v TenLop`; nhìn thấy các mục `#n = …` và lệnh bytecode tham chiếu chúng | Xem hành vi literal, `intern()` và toán tử `==`; `javap` không in ra toàn bộ runtime String Pool |
| **Quan hệ với object identity** | Entry trong `.class` **không tự nó là** đối tượng `String` | Hai tham chiếu đến cùng instance `String` thì `==` là `true` |
| **Có mọi đối tượng `String`?** | Không; chỉ có metadata/hằng của class file đó | Không; chuỗi mới tạo bằng `new String(...)` hoặc phép toán runtime không mặc nhiên được intern |
| **Có mọi loại hằng?** | Có nhiều loại thông tin, không chỉ chuỗi | Chỉ liên quan đến `String` được intern |

**Không nhầm:** Một số tài liệu còn nói tới **run-time constant pool** theo đặc tả JVM. Đây là biểu diễn **theo từng class/interface** được dẫn xuất từ `constant_pool` của `.class` khi lớp được JVM tạo, phục vụ liên kết và thực thi (`JVMS `2.5.5`). Nó cũng **không đồng nghĩa** với String Pool dùng để intern các đối tượng `String`.

## 2. Trong `.class`, một string literal được lưu thế nào?

Giả sử mã nguồn dùng:

```java
public class ClassPoolView {
    static final String MESSAGE = "Con" + "stant";

    public static String getMessage() {
        return "Constant";
    }
}
```

Java compiler có thể nhận ra `"Con" + "stant"` là **biểu thức hằng tại thời điểm biên dịch** (*compile-time constant expression*). Với `javac` thông thường, giá trị của `MESSAGE` là `"Constant"` và getter trả về literal có cùng nội dung.

Trong định dạng class file, những cấu trúc liên quan thường là:

```text
CONSTANT_String_info
    tag          = 8   (CONSTANT_String)
    string_index = n   (chỉ tới CONSTANT_Utf8_info)

CONSTANT_Utf8_info
    tag    = 1   (CONSTANT_Utf8)
    length = ...
    bytes  = ... (nội dung dùng modified UTF-8)
```

- `CONSTANT_String_info` là một **entry có chỉ số** trong bảng; nó trỏ đến entry `CONSTANT_Utf8_info` mô tả nội dung chuỗi. Nó **không chứa con trỏ tới một đối tượng Java `String` đã nằm sẵn trong class file**.
- `CONSTANT_Utf8_info` là dạng dữ liệu có độ dài và mảng byte trong định dạng `.class`. Nó chứa **modified UTF-8 (MUTF-8)**, khác UTF-8 chuẩn ở một số trường hợp (chẳng hạn NUL và ký tự ngoài BMP). Không đồng nhất “các byte UTF-8 trong `.class`” với “cách `String` được lưu trong heap”.
- `CONSTANT_Utf8_info` còn biểu diễn tên lớp, tên phương thức, descriptor và nhiều thông tin **không phải literal**. Thấy `Utf8 java` trong output của `javap` **không đủ** để kết luận một đối tượng `String` tên `"java"` đã được intern.
- `CONSTANT_String_info` chỉ là **mô tả một chuỗi có thể được tải bởi JVM**. Sau khi phân giải, JVM có thể sử dụng tham chiếu tới `String` thực tế theo quy tắc interning.
- Một class file có rất nhiều entry kiểu khác: mô tả lớp, trường, phương thức, kiểu hàm, số nguyên, số thực, v.v. Chúng không thuộc String Pool.

### Quan sát bằng `javap`

Biên dịch và xem bytecode:

```bash
javac -encoding UTF-8 ClassPoolView.java
javap -v -c ClassPoolView
```

Trong output sẽ có một mục **`Constant pool:`** chứa các entry có chỉ số `#n`. Một phần **minh họa cấu trúc** (chỉ số `#n` thay đổi theo compiler/JDK và chi tiết class, **không** phải output nguyên văn cố định):

```text
Constant pool:
   #a = String             #b             // Constant
   #b = Utf8               Constant
   ...
   #c = Utf8               MESSAGE
   ...
   #d = Utf8               ConstantValue
   ...

public static java.lang.String getMessage();
  Code:
     ...
     ldc      #a             // String Constant
     areturn
```

Ở đây, `ldc` nạp một giá trị tham chiếu tới `String` tương ứng với hằng literal sau khi JVM xử lý mục `CONSTANT_String`. Trình biên dịch cũng có thể đặt **`ConstantValue`** cho trường `static final String MESSAGE` vì đây là một constant variable. Chi tiết thứ tự entry và các mục bổ sung phụ thuộc bytecode được sinh ra.

Nhìn vào `javap -v` là cách tốt để kiểm tra **class file constant pool**. Tuy nhiên, output đó không thể tự cho biết toàn bộ các `String` nào đang được intern ở runtime, địa chỉ đối tượng, số lượng đối tượng trong pool, hay thuật toán lưu trữ của JVM.

## 3. Vì sao literal lại dùng chung một `String` khi chạy?

Java Language Specification yêu cầu các **string literal** và chuỗi là giá trị của **constant expression** được intern. Khi các literal có cùng nội dung được dùng, chúng tham chiếu tới **cùng một instance `String`**; quy tắc này có ý nghĩa kể cả khi literal ở các lớp khác nhau.

Ví dụ:

```java
String a = "java";
String b = "java";
System.out.println(a == b); // true
```

Hai literal có cùng nội dung được dùng chung tham chiếu. Dù mỗi class file có thể có một entry `CONSTANT_String` cho `"java"`, các entry đó vẫn **khác nhau về mặt metadata**; runtime đảm bảo chuỗi literal được intern tương ứng là một đối tượng canonical.

### Cầu nối từ `CONSTANT_String` đến `String`

Có thể hình dung luồng xử lý điển hình như sau:

```text
Mã nguồn Java: "java"
         |
         v
Biên dịch thành class file
         |
         v
constant_pool của class file
  CONSTANT_String -> CONSTANT_Utf8 ("java")
         |
         v
JVM nạp/liên kết và dùng hằng chuỗi
  (ví dụ khi thực thi lệnh ldc)
         |
         v
Phân giải/thu được tham chiếu String
theo quy tắc interning
         |
         v
Đối tượng java.lang.String canonical
         |
         v
Lệnh Java sử dụng tham chiếu đó
```

Đây là **mô hình khái niệm**, không phải lời khẳng định chính xác thời điểm JVM tạo đối tượng. JVM có thể tối ưu và phân giải hằng vào thời điểm được phép theo đặc tả; việc JVM phân giải sớm hoặc muộn không làm thay đổi quy tắc observable của literal và `intern()`.

Tương tự, **run-time constant pool** có thể giữ giá trị/tham chiếu đã phân giải cho class đang chạy; điều đó không biến toàn bộ run-time constant pool thành String Pool.

## 4. Ba khái niệm hay bị gọi cùng là “constant pool”

| Tên chính xác | Khi nào tồn tại/được dùng | Cách hiểu |
|---|---|---|
| **Class-file constant pool** (`constant_pool`) | Cấu trúc trong định dạng `.class` | Các entry ký hiệu và hằng số do compiler hoặc trình xử lý bytecode ghi ra |
| **Run-time constant pool** | Theo từng class/interface do JVM tạo khi nạp class; dùng trong liên kết/thực thi | Biểu diễn runtime được dẫn xuất từ class-file constant pool, có thể bao gồm các giá trị/đích đã phân giải |
| **String Pool / intern pool** | Khi ứng dụng chạy và có literal/`intern()` | Cơ chế dùng chung instance `java.lang.String` theo nội dung |

**Câu hỏi “String Pool nằm ở đâu?”** có thể được trả lời ở hai mức:

- **Theo hợp đồng Java:** các chuỗi interned cung cấp tham chiếu canonical. JLS không yêu cầu người lập trình suy luận một địa chỉ nhớ vật lý cụ thể từ từ “pool”.
- **Theo triển khai:** JVM như HotSpot quản lý dữ liệu intern với cấu trúc runtime riêng; cách tổ chức, kích thước và chính sách quản lý là chủ đề JVM/GC, có thể thay đổi theo phiên bản. Không nên ghi nhớ quy tắc kiểu “String Pool chắc chắn ở PermGen” cho Java hiện đại, vì PermGen đã bị loại bỏ khỏi HotSpot từ Java 8.

Quan trọng hơn vị trí vật lý là **nguyên tắc nào làm hai tham chiếu cùng một đối tượng**: literal và constant expression được intern; `intern()` cho phép nhận tham chiếu canonical; string tạo từ runtime không mặc nhiên được intern.

## 5. Sai lầm thường gặp và cách kiểm tra

| Nhận định | Vấn đề | Cách kiểm tra/hiểu đúng |
|---|---|---|
| “String Pool chính là bảng `Constant pool:` trong `javap`.” | `javap` đọc metadata `.class`, không liệt kê heap object canonical | Phân biệt file format với hành vi identity của `String` runtime |
| “`CONSTANT_Utf8_info` là một đối tượng `String`.” | Entry này là dữ liệu bytes trong class file | Phân biệt mô tả dạng binary với Java object |
| “Có `String` trong class-file constant pool thì nó chắc chắn đã được khởi tạo lúc compile.” | Compiler không tạo object Java runtime cho file `.class` | Đối tượng `String` tương ứng được JVM thu được khi runtime dùng hằng |
| “Hai class có `CONSTANT_String` riêng nên literal cùng nội dung là hai objects.” | Metadata riêng không đồng nghĩa instance riêng | Literal được intern theo nội dung |
| “`new String("x")` lấy đúng đối tượng interned vì literal `"x"` được intern.” | `new` tạo đối tượng riêng | Dùng `==` để quan sát identity, `equals` để so nội dung |
| “`static final` luôn có một entry hằng compile-time.” | Phải xem initializer có là constant expression không | So sánh `static final String A="x"` với `static final String B=new String("x")` |
| “Hai chuỗi `equals` nhau thì `==` cũng phải đúng.” | Nội dung và object identity là hai phép so sánh khác nhau | Dùng `equals` cho logic nghiệp vụ |
| “`intern()` luôn là cách tối ưu hiệu năng.” | Việc intern cũng có chi phí tra cứu và quản lý pool; hiệu quả phụ thuộc tập dữ liệu | Chỉ dùng khi đã xác định lợi ích và kiểm chứng bằng đo đạc |

## 6. Tài liệu đặc tả để đối chiếu

- [Java Language Specification, 3.10.5 — String Literals](https://docs.oracle.com/javase/specs/jls/se17/html/jls-3.html#jls-3.10.5): quy tắc string literal và interning.
- [Java Language Specification, 15.29 — Constant Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.29): biểu thức hằng tại compile-time.
- [Java Virtual Machine Specification, 4.4 — The Constant Pool](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.4): cấu trúc `CONSTANT_String_info` và `CONSTANT_Utf8_info`.
- [Java Virtual Machine Specification, 2.5.5 — Run-Time Constant Pool](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.5): khái niệm run-time constant pool.

**Ghi nhớ:** `constant_pool` trong `.class` là **bảng mô tả hằng và ký hiệu**; String Pool là **cơ chế dùng chung đối tượng `String` lúc chạy**. Entry `CONSTANT_String_info` có thể là đầu mối để JVM thu được tham chiếu `String` interned, nhưng entry của class file và đối tượng được intern là **hai thứ khác nhau**. Khi cần kiểm tra, dùng `javap -v` cho cấu trúc `.class`, và dùng `==`/`equals`/`intern()` trong chương trình để phân biệt identity với nội dung.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-pool-intern-007">Vì sao quan sát == trên literal không thay thế so sánh nội dung</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao quan sát `==` trên literal không thay thế so sánh nội dung của `String`

Khi thử Java, nhiều người thấy:

```java
String a = "Java";
String b = "Java";
System.out.println(a == b); // true
```

Rồi kết luận rằng có thể dùng `==` để kiểm tra hai chuỗi có cùng chữ. **Đó là kết luận sai.** Kết quả `true` ở đây đến từ cơ chế *string pool*: hai literal cùng nội dung được tham chiếu tới cùng một đối tượng `String`. Toán tử `==` vẫn chỉ so sánh **danh tính đối tượng (object identity)**, không tự so sánh từng ký tự.

Với nhu cầu kiểm tra hai giá trị văn bản có giống nhau hay không, dùng `String.equals(...)` (hoặc một phép so sánh giá trị phù hợp với yêu cầu nghiệp vụ). Bài học này giải thích tại sao những phép thử nhỏ trên literal dễ gây hiểu nhầm và cách viết code đúng cho chuỗi đến từ đầu vào thực tế.

## 1. Ba thứ khác nhau: biến, đối tượng và nội dung

Xét mã:

```java
String first = "core";
String second = first;
String third = new String("core");
```

- **Biến tham chiếu** `first`, `second` và `third` lưu các giá trị tham chiếu đến đối tượng `String`.
- **Đối tượng `String`** có danh tính riêng trong bộ nhớ; hai biến có thể trỏ đến cùng một đối tượng.
- **Nội dung chuỗi** là dãy ký tự, ở đây đều là `"core"`.

Mô hình tham chiếu:

```text
first  ──┐
         ├──────────> String "core" (đối tượng từ pool)
second ──┘

third ──────────────> String "core" (đối tượng do new tạo)
```

Khi so sánh hai biến kiểu tham chiếu:

- `first == second` hỏi: «Hai biến có trỏ tới **cùng một đối tượng** không?» → `true`.
- `first == third` hỏi cùng câu hỏi đó → `false`.
- `first.equals(third)` hỏi: «Hai đối tượng `String` có **cùng nội dung** không?» → `true`.

Dù các chuỗi cùng chữ, identity và value không phải một khái niệm. Dùng `==` để kiểm tra nội dung chỉ tình cờ cho kết quả mong đợi khi hai bên đang tham chiếu tới cùng đối tượng.

## 2. Tại sao hai literal giống nhau có thể cho `== true`?

Java lưu các chuỗi literal dưới dạng đã được *intern* trong **string pool**. Theo ngữ nghĩa Java, các literal cùng nội dung (và các biểu thức chuỗi hằng có cùng nội dung) tham chiếu đến đối tượng `String` chuẩn tắc trong pool.

```java
String a = "Java";
String b = "Java";
System.out.println(a == b); // true
```

Đây là đặc tính có chủ ý của ngôn ngữ và cách tổ chức literal. Nó giúp sử dụng lại đối tượng cho các giá trị hằng giống nhau. Tuy nhiên, điều này **không biến `==` thành phép so sánh nội dung**. Khi hai tham chiếu khác đối tượng, `==` vẫn trả về `false` ngay cả khi các ký tự bằng nhau.

Có thể hình dung câu hỏi sai thường gặp như sau:

> «Hai literal cùng chữ trả về `true` với `==`, vậy mọi chuỗi cùng chữ cũng sẽ trả về `true`.»

Bước suy luận sai là đánh đồng «cùng nội dung» với «luôn cùng tham chiếu». Pool giải thích **vì sao trường hợp literal đặc biệt có thể chia sẻ identity**; nó không đưa ra bảo đảm cho mọi chuỗi được sinh ra lúc chạy.

### Ví dụ 1: literal và bản sao có cùng nội dung

Tạo tệp `LiteralIdentityDemo.java`:

```java
public class LiteralIdentityDemo {
    public static void main(String[] args) {
        String a = "Java";
        String b = "Java";
        String copied = new String("Java");

        System.out.println(a == b);
        System.out.println(a == copied);
        System.out.println(a.equals(copied));
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac LiteralIdentityDemo.java
java LiteralIdentityDemo
```

Kết quả chính xác:

```text
true
false
true
```

Dòng đầu đúng vì literal `"Java"` được dùng chung qua pool. Dòng thứ hai sai vì `new String("Java")` tạo **đối tượng mới** có identity khác. Dòng cuối đúng vì `equals` so sánh nội dung hai `String`.

Cả ba phép so sánh đều đang làm đúng công việc của chúng. Lỗi nằm ở việc chọn `==` khi thật ra bài toán yêu cầu so sánh văn bản.

## 3. Chuỗi hằng tại thời điểm biên dịch cũng có thể dùng chung đối tượng pooled

Không chỉ literal viết nguyên một lần mới có thể nhận cùng tham chiếu. **Biểu thức hằng chuỗi (constant expression)** được đánh giá tại thời điểm biên dịch cũng có thể trở thành cùng giá trị literal/interned.

Ví dụ:

```java
String a = "Java";
String b = "Ja" + "va";
System.out.println(a == b); // true
```

`"Ja" + "va"` là biểu thức hằng, được tính tại thời điểm biên dịch thành `"Java"`. Vì vậy `b` nhận tham chiếu tới cùng chuỗi đã intern.

Tương tự, một **constant variable** `final String` được khởi tạo bằng biểu thức hằng có thể góp phần tạo biểu thức hằng:

```java
final String prefix = "Ja";
String c = prefix + "va";
System.out.println(c == "Java"); // true
```

Điểm cần nhớ: **`final` không tự động làm mọi phép nối trở thành biểu thức hằng**. Giá trị khởi tạo và loại biểu thức phải thỏa định nghĩa *constant expression* của ngôn ngữ. Ví dụ, nếu một biến `final` nhận giá trị từ phương thức lúc chạy thì phép nối với nó không phải biểu thức hằng chỉ vì nó có `final`.

### Ví dụ 2: kiểm tra literal và phép nối hằng

Tạo tệp `ConstantExpressionDemo.java`:

```java
public class ConstantExpressionDemo {
    public static void main(String[] args) {
        String literal = "Java";
        String folded = "Ja" + "va";

        final String compileTimePrefix = "Ja";
        String foldedWithFinal = compileTimePrefix + "va";

        String runtimePrefix = new String("Ja");
        String constructedAtRuntime = runtimePrefix + "va";

        System.out.println(literal == folded);
        System.out.println(literal == foldedWithFinal);
        System.out.println(literal.equals(constructedAtRuntime));
        System.out.println(literal == constructedAtRuntime);
    }
}
```

```bash
javac ConstantExpressionDemo.java
java ConstantExpressionDemo
```

Kết quả với Java 17+:

```text
true
true
true
false
```

Hai lần `true` đầu không chứng minh `==` so sánh chữ: chúng chỉ phản ánh phép nối **hằng** cùng nhận đối tượng pooled. Với `runtimePrefix` là đối tượng tạo lúc chạy, phép nối sinh ra giá trị `"Java"` mới; `equals` xác nhận nội dung đúng, còn `==` cho thấy nó không cùng identity với literal ở ví dụ này.

Trong ứng dụng thực tế, chuỗi nhận từ mạng, bàn phím, file, cơ sở dữ liệu và kết quả xử lý lúc chạy không được giả định có cùng identity với literal dù nội dung trùng khớp.

## 4. Chuỗi từ bên ngoài có thể bằng literal mà `==` vẫn `false`

Một ví dụ rất gần với việc đọc dữ liệu bên ngoài là tạo chuỗi từ mảng ký tự:

```java
char[] incoming = {'o', 'k'};
String fromInput = new String(incoming);

System.out.println(fromInput == "ok");       // false
System.out.println(fromInput.equals("ok"));   // true
```

`fromInput` và literal `"ok"` có cùng nội dung. Tuy nhiên, constructor `String(char[])` tạo một đối tượng `String` từ dữ liệu đầu vào, nên tham chiếu của nó khác tham chiếu literal pooled. Nếu code phân nhánh theo `if (fromInput == "ok")`, nhánh này sẽ **không chạy** dù người dùng đã nhập đúng chữ.

### Ví dụ 3: một nhánh lỗi và một nhánh đúng

Tạo tệp `StringDecisionDemo.java`:

```java
public class StringDecisionDemo {
    public static void main(String[] args) {
        String expected = "READY";
        String received = new String(
                new char[] {'R', 'E', 'A', 'D', 'Y'}
        );

        if (received == expected) {
            System.out.println("Identity matched");
        } else {
            System.out.println("Identity did not match");
        }

        if (expected.equals(received)) {
            System.out.println("Content matched");
        } else {
            System.out.println("Content did not match");
        }
    }
}
```

```bash
javac StringDecisionDemo.java
java StringDecisionDemo
```

Kết quả:

```text
Identity did not match
Content matched
```

Chỉ thay đổi **cách tạo đối tượng** mà nội dung vẫn như nhau đã khiến `==` chuyển từ `true` sang `false`. Đó chính là lý do không nên dùng một phép thử trên hai literal để xác nhận logic so sánh nội dung của chương trình.

## 5. `String.intern()` thay đổi điều gì, và không thay đổi điều gì?

`String.intern()` trả về một **tham chiếu chuẩn tắc** tới chuỗi có cùng nội dung trong string pool. Nếu chuỗi tương ứng đã có trong pool, nó trả về tham chiếu ở pool. Nếu chưa có, pool sẽ bảo đảm có một đối tượng chuẩn tắc tương ứng và trả về tham chiếu đó.

Ví dụ:

```java
String literal = "topic";
String dynamic = new String("topic");
String canonical = dynamic.intern();

System.out.println(literal == dynamic);   // false
System.out.println(literal == canonical); // true
System.out.println(dynamic == canonical); // false
```

Điểm quan trọng:

1. `dynamic` được tạo riêng qua `new`, vì vậy nó có identity khác `literal`.
2. `dynamic.intern()` trả về tham chiếu chuẩn tắc cho nội dung `"topic"`; literal `"topic"` đã hiện diện trong ví dụ.
3. Gọi `intern()` **không gán lại biến `dynamic`** và không biến đối tượng mà `dynamic` đang tham chiếu thành đối tượng literal.

### Ví dụ 4: kiểm tra rõ trước và sau `intern()`

Tạo tệp `InternDemo.java`:

```java
public class InternDemo {
    public static void main(String[] args) {
        String literal = "topic";
        String dynamic = new String("topic");
        String canonical = dynamic.intern();

        System.out.println(literal == dynamic);
        System.out.println(literal == canonical);
        System.out.println(dynamic == canonical);
        System.out.println(literal.equals(dynamic));
    }
}
```

```bash
javac InternDemo.java
java InternDemo
```

Kết quả:

```text
false
true
false
true
```

`intern()` có thể phục vụ những thiết kế cần chuẩn tắc hóa tham chiếu có chủ đích. Tuy nhiên, không cần (và thường không nên) gọi `intern()` chỉ để dùng `==` thay `equals`. Việc tự quản lý chuẩn tắc hóa làm code phụ thuộc thêm vào chiến lược chia sẻ đối tượng và có thể tốn tài nguyên mà yêu cầu so sánh nội dung không cần đến.

Một nguyên tắc tốt: **pool/intern giải thích nguồn gốc tham chiếu; `equals` giải quyết nhu cầu so sánh nội dung.**

## 6. Viết phép so sánh nội dung như thế nào?

Với hai `String` khác `null`:

```java
String expected = "Java";
String actual = new String("Java");

boolean sameContent = expected.equals(actual); // true
```

`String.equals(Object other)` trả về `true` khi đối số là một `String` có cùng dãy ký tự. Nó không yêu cầu cùng đối tượng hay cùng nguồn khởi tạo.

Về hướng gọi và `null`:

- Nếu biết chắc `a != null`, dùng `a.equals(b)`.
- Nếu có một hằng mong đợi, dùng `"READY".equals(received)`: nhận `false` an toàn khi `received == null`.
- Nếu **cả hai phía có thể null**, dùng `Objects.equals(a, b)` (import `java.util.Objects`); hai giá trị cùng `null` cho `true`.
- Để kiểm tra một tham chiếu **có null hay không**, dùng `value == null` hoặc `value != null`. Đây là so sánh tham chiếu đúng mục đích.
- Chỉ dùng `equalsIgnoreCase` khi nghiệp vụ thực sự yêu cầu bỏ qua khác biệt hoa/thường; nó không phải lựa chọn mặc định cho mọi văn bản.

### Ví dụ 5: so sánh an toàn khi có `null`

Tạo tệp `NullSafeComparisonDemo.java`:

```java
import java.util.Objects;

public class NullSafeComparisonDemo {
    public static void main(String[] args) {
        String received = null;

        System.out.println("READY".equals(received));
        System.out.println(Objects.equals("READY", received));
        System.out.println(Objects.equals(null, received));

        received = new String("READY");
        System.out.println("READY".equals(received));
        System.out.println("READY" == received);
    }
}
```

```bash
javac NullSafeComparisonDemo.java
java NullSafeComparisonDemo
```

Kết quả:

```text
false
false
true
true
false
```

Nếu viết `received.equals("READY")` khi `received == null`, chương trình sẽ ném `NullPointerException`. Không nên «sửa» lỗi này bằng `received == "READY"`: phép so sánh tham chiếu chỉ che mất lỗi null trong ví dụ nhỏ rồi gây ra lỗi logic khi dữ liệu hợp lệ đến từ nơi khác.

## 7. So sánh nhanh: kết quả nào được bảo đảm?

| Hai biểu thức được so sánh | `==` | `equals` | Giải thích |
| --- | --- | --- | --- |
| `"x"` và `"x"` | `true` | `true` | Hai literal cùng giá trị đã intern |
| `"x"` và `"x" + ""` | `true` | `true` | Phép nối toàn hằng được tính ở compile time |
| `a` và `b` sau `String b = a` | `true` | `true` khi `a` là `String` khác null | Cùng tham chiếu; với null, không được gọi `a.equals(...)` |
| `"x"` và `new String("x")` | `false` | `true` | `new` tạo đối tượng `String` khác |
| `"x"` và `new String("x").intern()` | `true` | `true` | `intern()` trả về tham chiếu pooled |
| `"x"` và `new String("y")` | `false` | `false` | Khác đối tượng và khác nội dung |

Bảng này sử dụng các biểu thức có điều kiện rõ ràng. Với một `String` bất kỳ lấy từ hệ thống bên ngoài, **không thể đoán kết quả `==` chỉ từ chữ hiển thị**. Không nên coi `== true` là «nội dung bằng nhau» theo hợp đồng API so sánh giá trị, dù cùng tham chiếu tất nhiên dẫn tới cùng nội dung tại thời điểm đó.

## 8. Hai lỗi dễ lọt qua kiểm thử

### Lỗi A — kiểm thử chỉ dùng literal

```java
static boolean isAdmin(String role) {
    return role == "ADMIN"; // Sai mục đích nếu kiểm tra nội dung.
}
```

Nếu thử `isAdmin("ADMIN")`, kết quả thường là `true` và có thể khiến bạn nghĩ hàm đúng. Nhưng khi nguồn dữ liệu tạo đối tượng mới cùng nội dung, ví dụ `isAdmin(new String("ADMIN"))`, kết quả là `false`.

Sửa thành:

```java
static boolean isAdmin(String role) {
    return "ADMIN".equals(role);
}
```

Đây là đoạn minh họa phương thức, có thể đặt trong một lớp Java; nó không phải chương trình độc lập vì chưa có `main`. Phép so sánh mới trả về `true` cho các `String` có nội dung chính xác `"ADMIN"` bất kể cách tạo đối tượng. Khi `role == null`, nó trả về `false`.

### Lỗi B — dùng `intern()` để hợp thức hóa `==`

Một cách sửa kém cần thiết là:

```java
String received = new String("READY");
if (received.intern() == "READY") {
    // ...
}
```

Điều này hoạt động với ví dụ cụ thể, nhưng làm logic dựa vào pool, còn người đọc phải hiểu chiến lược canonicalization. Cách trực tiếp và đúng nghĩa hơn:

```java
String received = new String("READY");
if ("READY".equals(received)) {
    // ...
}
```

Các phương thức so sánh giá trị đều thể hiện ý định rõ hơn việc thay đổi cách quản lý tham chiếu chỉ để tạo điều kiện cho `==`.

## 9. Khi nào `==` với `String` vẫn có ý nghĩa?

`==` không phải toán tử «cấm dùng» trên `String`. Nó đúng khi điều bạn muốn kiểm tra **thực sự là identity**, chẳng hạn:

- `value == null` để kiểm tra không có tham chiếu.
- `a == b` trong mã nội bộ cố ý xác định hai biến có cùng đối tượng hay không, ví dụ khi nghiên cứu pool hoặc kiểm tra tối ưu hóa; kết quả này nói về *đối tượng*, không thay thế bước kiểm tra nội dung.
- Logic có một hợp đồng riêng thật sự dựa trên identity (trường hợp hiếm và cần được nêu rõ).

Nếu câu hỏi nghiệp vụ là «mã trạng thái có đúng `READY` không?», «username có giống nhau không?», hay «giá trị text ở hai nguồn có bằng nhau không?», thì câu trả lời cần phép so sánh **nội dung**. Đừng thay hợp đồng nghiệp vụ bằng giả định về pooling.

Cũng cần nhớ: `String` bất biến nên hai biến chia sẻ một đối tượng pooled không có nghĩa là sửa chuỗi của một biến sẽ tác động đến biến kia. Gán lại biến hoặc tạo giá trị chuỗi mới không sửa nội dung đối tượng `String` cũ.

## 10. Tự kiểm tra

### Câu 1 — các giá trị đúng hay sai?

```java
String x = "cat";
String y = "cat";
String z = new String("cat");

System.out.println(x == y);
System.out.println(y == z);
System.out.println(x.equals(z));
```

Đáp án:

```text
true
false
true
```

`x` và `y` cùng tham chiếu literal. `z` là đối tượng riêng có cùng nội dung.

### Câu 2 — lý do thật sự của `true`?

```java
String text = "abcdef";
String built = "abc" + "def";
System.out.println(text == built);
```

Đáp án là `true` vì phép nối toàn literal là biểu thức hằng được tính tại thời điểm biên dịch. Điều này **không** có nghĩa toán tử `==` so từng ký tự.

### Câu 3 — thử trường hợp data đến từ runtime

```java
String expected = "abc";
String actual = new String(new char[] {'a', 'b', 'c'});

System.out.println(expected == actual);
System.out.println(expected.equals(actual));
```

Đáp án:

```text
false
true
```

Chuỗi runtime không cùng identity với literal dù nội dung bằng nhau.

### Câu 4 — chọn phép so sánh

Giả sử `String token` có thể bằng `null` và bạn muốn biết token có chính xác là `"OK"` không. Chọn `token == "OK"`, `token.equals("OK")` hay `"OK".equals(token)`?

**Đáp án:** `"OK".equals(token)`. Nó trả về `true` khi nội dung khớp, `false` nếu `token` khác hoặc `null`. `token.equals("OK")` có nguy cơ ném `NullPointerException`; `token == "OK"` kiểm tra identity sai mục đích.

## Kết luận

**`==` trên `String` so sánh tham chiếu; `equals` so sánh nội dung.** Hai literal cùng chữ, hoặc hai biểu thức hằng có cùng kết quả, có thể cùng tham chiếu nhờ string pool nên `==` trả về `true`. Đó là một quan sát đúng về **identity của literal**, nhưng **không** phải bảo đảm rằng mọi chuỗi có cùng chữ được tạo ở thời điểm chạy sẽ cùng tham chiếu. Với dữ liệu văn bản thực tế, hãy viết phép so sánh theo yêu cầu giá trị ngay từ đầu, sử dụng `equals` và xử lý `null` rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="string-pool-intern-008">Khi nào intern có ích và giới hạn khi dùng trên dữ liệu không kiểm soát</a>

<details>
<summary>Xem chi tiết</summary>

# Khi nào `String.intern()` có ích và giới hạn khi dùng trên dữ liệu không kiểm soát

`String.intern()` cho phép nhiều chuỗi **có nội dung bằng nhau** dùng chung một đối tượng đại diện (*canonical string*) trong **string pool** của JVM. Ý tưởng này hữu ích khi dữ liệu lặp lại rất nhiều và số giá trị khác nhau được khống chế. Ngược lại, với các giá trị do người dùng hoặc hệ thống bên ngoài tự do tạo ra, số chuỗi khác nhau có thể tăng vô hạn theo thời gian; intern tràn lan có thể làm tốn CPU, gây áp lực bộ nhớ, GC và ảnh hưởng hiệu năng toàn JVM.

Bài này giải thích cơ chế, điều kiện nên dùng, giới hạn với dữ liệu không tin cậy, lựa chọn thay thế và cách đo đạc. Các ví dụ sử dụng Java 17+.

## 1. Hiểu đúng cơ chế String pool và `intern()`

Trong Java, `String` bất biến (*immutable*). Hai đối tượng có thể cùng nội dung nhưng khác tham chiếu:

- `a.equals(b)` hỏi **nội dung** có giống nhau hay không.
- `a == b` hỏi có cùng **tham chiếu đối tượng** hay không.
- `a.intern()` yêu cầu JVM trả về đối tượng đại diện cho nội dung của `a` trong pool. Nếu pool có đại diện bằng nội dung thì trả đối tượng đó, nếu chưa có thì đưa một đại diện vào pool rồi trả lại.

Với hai chuỗi bằng nhau về nội dung, hợp đồng API `String.intern()` bảo đảm hai kết quả `intern()` là cùng tham chiếu. Phép gọi **không thay đổi biến nhận** nếu bạn không gán lại:

~~~java
String a = new String("READY");
String b = new String("READY");

System.out.println(a == b);               // false
System.out.println(a.equals(b));          // true
System.out.println(a.intern() == b.intern()); // true

String canonical = a.intern();
System.out.println(canonical == "READY");  // true
System.out.println(a == "READY");          // false
~~~

Literal như `"READY"` và biểu thức hằng `"REA" + "DY"` được canonical hóa qua cơ chế liên quan đến string pool. Một biểu thức ghép thực thi lúc chạy không được bảo đảm có cùng tham chiếu:

~~~java
String a = "READY";
String b = "REA" + "DY"; // compile-time constant
String suffix = "DY";
String c = "REA" + suffix; // biểu thức không phải constant expression

System.out.println(a == b);          // true
System.out.println(a.equals(c));     // true
System.out.println(a == c);          // false
System.out.println(a == c.intern()); // true
~~~

**Nguyên tắc quan trọng:** Dù có intern ở một phần hệ thống, các API khác vẫn có thể tạo `String` không intern. Vì vậy so sánh trạng thái nghiệp vụ từ nhiều nguồn **bằng `equals()`**, hoặc `Objects.equals(a,b)` nếu có thể null. Chỉ so `==` đối với String khi có hợp đồng nội bộ rõ ràng bảo đảm **mọi** đường tạo/nhận giá trị đều canonical; tránh dùng nó như một giả định ngầm.

## 2. Pool có giống cache ứng dụng không?

String pool là tài nguyên ở phạm vi JVM, không phải cache cục bộ mà code tùy ý xóa một entry bằng `remove()`. Triển khai cụ thể khác nhau theo JVM, JDK và collector. Trên HotSpot hiện đại (bao gồm Java 17), việc quản lý đối tượng chuỗi gắn với Java heap; thông tin lịch sử rằng toàn bộ interned strings nằm vĩnh viễn trong *PermGen* của các phiên bản rất cũ không còn là mô hình phù hợp.

Hai cực đoan đều không đúng:

1. **“Hễ intern là giữ chuỗi vĩnh viễn.”** Không được khẳng định chung cho mọi JVM. Khả năng thu hồi một đại diện phụ thuộc việc còn tham chiếu mạnh từ ứng dụng, literal/constant liên quan, JVM và chính sách collector. Một số chuỗi có thể được thu hồi khi không còn reachable.
2. **“GC chắc chắn thu hồi kịp nên intern mọi input không sao.”** Cũng không đúng. Chương trình không quyết định khi nào GC diễn ra, không có API evict riêng cho pool, và vẫn phải trả chi phí tạo/tra cứu/quản lý các giá trị mới. Những giá trị còn được ứng dụng giữ sẽ không được thu hồi chỉ vì chúng đã intern.

Không được thiết kế theo giả định chuỗi intern luôn *leak* vĩnh viễn, cũng không dựa vào giả định chúng biến mất tức thì. Các khác biệt giữa JVM/GC cần kiểm tra trên môi trường chạy thực tế.

## 3. Điều kiện `intern()` có thể đem lại lợi ích

### 3.1. Tập giá trị nhỏ và lặp rất nhiều

Ví dụ một hệ thống giữ hàng triệu đối tượng sự kiện; mỗi sự kiện chứa một trạng thái tạo từ dữ liệu đã parse, nhưng trạng thái chỉ có `READY`, `RUNNING`, `DONE`. Nếu mỗi lần parse sinh một đối tượng `String` mới và những đối tượng sự kiện sống lâu, nhiều String giữ nội dung giống nhau có thể chiếm heap.

Trong mô hình trực giác với \(N\) lần xuất hiện và \(U\) giá trị khác nhau, khi \(U\) nhỏ hơn nhiều so với \(N\), thay nhiều bản sao chuỗi sống bằng \(U\) đại diện dùng chung có thể giảm bộ nhớ. Tuy nhiên đây **không phải công thức chính xác về byte**: kích thước object, compact string, sharing của mảng byte, tối ưu JVM, overhead của StringTable và vòng đời đối tượng đều ảnh hưởng thực tế.

Những tiêu chí để cân nhắc:

- **Cardinality có giới hạn đáng tin**: tập giá trị khác nhau được kiểm soát bằng danh sách hoặc quy tắc có ngưỡng rõ.
- **Tần suất trùng lặp cao**: nhiều đối tượng cùng giữ các nội dung giống nhau.
- **Chuỗi được giữ đủ lâu**: lợi ích giảm heap sống bù cho công tạo chuỗi và tra cứu pool.
- **Đã kiểm soát đầu vào**: giá trị lạ, quá dài hoặc vượt chính sách bị từ chối **trước** khi intern.
- **Đo được cải thiện**: heap sau GC, chi phí cấp phát, CPU, GC, throughput và độ trễ dưới tải đại diện.

Nếu chỉ vài hằng chuỗi `static final` hoặc literal được tái dùng, chúng có thể đã chia sẻ tham chiếu; thêm `intern()` không mang lại lợi ích.

### 3.2. Các tên nhãn lặp trong cấu trúc dữ liệu

Trong một parser, một cây cú pháp lớn hoặc hệ thống metadata, nhiều node có thể mang cùng tên nhãn như `"identifier"`, `"literal"`, `"property"`. Nếu chúng được tạo từ các lát dữ liệu đầu vào riêng và được giữ lâu, canonicalization có thể giảm số đối tượng/nội dung trùng được lưu giữ.

Tuy nhiên, phải xác định **đang tối ưu gì**: một chương trình tạo String tạm rồi loại bỏ ngay không nhất thiết hưởng lợi. Chỉ vì `HashMap<String, V>` có nhiều lookup cũng không có nghĩa cần intern key: `String.hashCode()` và `String.equals()` vốn giúp Map tìm đúng key có cùng nội dung dù khác tham chiếu.

### 3.3. Tối ưu đồng nhất trong một module khép kín

Một số thuật toán nội bộ có thể dựa vào canonicalization để dùng `==` sau khi biết chắc mọi giá trị đi qua một cổng chuẩn hóa. Cách này dễ lỗi nếu có đường dữ liệu mới bỏ qua bước chuẩn hóa. Nên cân nhắc `enum`, ID số nguyên hoặc symbol table của module nếu danh mục giá trị có giới hạn và quy ước nội bộ khó duy trì.

**Đừng coi `intern()` là thủ thuật mặc định để String so sánh “nhanh hơn”.** Tra cứu StringTable cũng có chi phí. Lợi ích so sánh, nếu có, phải được đo toàn đường xử lý chứ không chỉ thời gian của phép `==`.

## 4. Dữ liệu không kiểm soát nguy hiểm ở đâu?

“Không kiểm soát” có nghĩa là hệ thống không bảo đảm được số giá trị khác nhau, độ dài hoặc tốc độ xuất hiện. Những nguồn điển hình:

- UUID, request ID, trace ID, session ID, nonce, token ngẫu nhiên.
- Tên người dùng hoặc chuỗi tìm kiếm tự do.
- URL đầy đủ với query string tùy ý.
- HTTP header, giá trị JSON, nội dung log, filename hoặc payload từ client.
- Chuỗi sinh bởi dịch vụ bên ngoài với schema không ràng buộc cardinality.

Anti-pattern:

~~~java
// Minh họa anti-pattern, không phải một chương trình chạy độc lập:
String userInput = request.getParameter("key");
String key = userInput.intern(); // đưa dữ liệu chưa giới hạn vào pool
~~~

Nếu một client gửi 100.000 giá trị gần như đều khác nhau, `intern()` hầu như không tìm thấy chuỗi trùng để chia sẻ. Chương trình vẫn phải chịu chi phí xây dựng/tra cứu bảng, xử lý đối tượng và có thể áp lực GC; trên hệ thống chịu tải cao, đây có thể là một điểm làm giảm throughput hoặc tăng tail latency. Nếu lượng giá trị mới liên tục hoặc có độ dài lớn, tài nguyên có thể bị tiêu hao theo cách kẻ gửi dữ liệu điều khiển được, tạo nguy cơ từ chối dịch vụ.

**Không đồng nghĩa “mọi chuỗi intern sẽ bị giữ vĩnh viễn”.** Dù JVM có thể thu gom một số đại diện không còn reachable, điều đó không làm `intern()` phù hợp với input vô hạn. Tần suất, độ dài, việc ứng dụng giữ tham chiếu và khả năng thu gom mới quyết định tác động.

Chỉ `strip()`, `toLowerCase()` hoặc chuẩn hóa Unicode **không** làm số giá trị trở nên hữu hạn; còn phải có whitelist/giới hạn thật. Kiểm tra số entry mà bỏ qua độ dài cũng không đủ: một entry có thể chứa dữ liệu rất lớn.

### Bảng quyết định nhanh

| Dữ liệu | Có nên intern? | Hướng xử lý phù hợp |
|---|---|---|
| `READY`, `RUNNING`, `DONE` cố định | Có thể nhưng thường không cần | Ưu tiên `enum` hoặc hằng chung |
| Tên token thuộc grammar giới hạn | Cân nhắc sau khi đo | Canonical hóa sau xác thực hoặc dùng symbol table |
| Nhãn từ bộ cấu hình có giới hạn | Có thể | Giới hạn số giá trị và chiều dài, đo thực tế |
| Request ID/UUID mỗi request | Không | Giữ String bình thường, chỉ giữ khi cần |
| URL và query string tùy ý | Không | Kiểm tra định dạng, giới hạn chiều dài, tránh giữ không cần thiết |
| Tên tài khoản/tìm kiếm tự do | Không | Validation và cấu trúc dữ liệu thông thường |
| Dòng log/JSON payload bất kỳ | Không | Parse phần cần thiết, không intern cả payload |
| String key của `HashMap` | Không bắt buộc | `equals/hashCode` đủ để tra cứu đúng |

## 5. Quy trình dùng intern có kiểm soát

Một chính sách hợp lý là **validate trước, intern sau, đo cuối**.

1. Đầu tiên xác định dữ liệu nào đang lặp nhiều, có được giữ lâu hay không. Dùng profiler/heap dump/JFR thay vì đoán từ số lần tạo chuỗi.
2. Xác định **tập giá trị chấp nhận**. Với các trạng thái, dùng whitelist hoặc enum. Với nhãn do cấu hình tạo, xác định ngưỡng số giá trị và kích thước.
3. Giới hạn độ dài và tần suất đầu vào ở ranh giới nhận dữ liệu. Đây là biện pháp chung để không cho client cung cấp input cực lớn.
4. Chuẩn hóa nếu quy tắc nghiệp vụ yêu cầu; sau đó kiểm tra chính xác giá trị được phép. Các chuẩn hóa có thể thay đổi ngữ nghĩa ngôn ngữ; chọn `Locale.ROOT` khi làm việc với mã máy không phụ thuộc locale, không áp dụng bừa cho tên người dùng.
5. Chỉ gọi `intern()` cho giá trị đã hợp lệ, và **dùng tham chiếu trả về**.
6. Đo và so sánh với lựa chọn đơn giản hơn. Giữ `equals()` cho mọi API không bảo đảm identity.

## 6. Ví dụ Java 17 hoàn chỉnh: whitelist trước khi intern

Đoạn dưới đây minh họa khác biệt `==`/`equals()`, việc gọi `intern()` sau validation và lựa chọn `enum` khi tập trạng thái cố định. Lưu nguyên file UTF-8 với tên `InternSafetyDemo.java`.

~~~java
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class InternSafetyDemo {
    private static final Set<String> ALLOWED = Set.of("NEW", "READY", "DONE");

    enum Status {
        NEW, READY, DONE
    }

    static String internValidatedStatus(String raw) {
        Objects.requireNonNull(raw, "raw");
        String normalized = raw.strip().toUpperCase(Locale.ROOT);
        if (!ALLOWED.contains(normalized)) {
            throw new IllegalArgumentException("Unknown status");
        }
        return normalized.intern();
    }

    static Status parseStatus(String raw) {
        Objects.requireNonNull(raw, "raw");
        return Status.valueOf(raw.strip().toUpperCase(Locale.ROOT));
    }

    public static void main(String[] args) {
        String first = new String(new char[] {'R', 'E', 'A', 'D', 'Y'});
        String second = new String(new char[] {'R', 'E', 'A', 'D', 'Y'});

        System.out.println("rawIdentity=" + (first == second));
        System.out.println("rawContent=" + first.equals(second));

        String a = internValidatedStatus(first);
        String b = internValidatedStatus(" ready ");
        System.out.println("canonicalIdentity=" + (a == b));
        System.out.println("sameAsLiteral=" + (a == "READY"));

        Status e1 = parseStatus(first);
        Status e2 = parseStatus(" ready ");
        System.out.println("enumIdentity=" + (e1 == e2));

        boolean rejected = false;
        try {
            internValidatedStatus("CLIENT_CUSTOM_00001");
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        System.out.println("unknownRejected=" + rejected);
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 InternSafetyDemo.java
java InternSafetyDemo
~~~

Stdout:

~~~text
rawIdentity=false
rawContent=true
canonicalIdentity=true
sameAsLiteral=true
enumIdentity=true
unknownRejected=true
~~~

`first` và `second` là hai String tạo tường minh bằng `new`, do đó tham chiếu khác nhưng `equals()` bằng nhau. `internValidatedStatus()` chuẩn hóa rồi chỉ cho qua ba mã có sẵn; kết quả canonical trùng tham chiếu literal `"READY"`. Trường hợp lạ bị từ chối trước intern. `enum` có các instance duy nhất cố định nên so `==` đúng ngữ nghĩa enum.

**Vì sao code còn dùng intern dù enum tốt hơn?** Để minh họa ranh giới bảo vệ. Trong ví dụ đúng ba trạng thái cố định, `enum` hoặc trả trực tiếp ba literal còn đơn giản hơn, vì literal đã ở pool. Intern đáng cân nhắc hơn khi từ vựng được dựng lúc chạy nhưng có **giới hạn thực sự**, xuất hiện lặp lại và được giữ lâu; vẫn phải đo lợi ích.

## 7. Những lựa chọn thay thế đáng cân nhắc

### 7.1. Enum, hằng và ID

Khi tập giá trị cố định tại lúc phát triển, `enum` có type safety, tập phần tử hữu hạn và identity ổn định. `switch` trên enum rõ ý hơn so String và dễ quản lý đường nhập. Nếu phần tử xuất hiện trong cấu trúc dữ liệu rất lớn, ID số nguyên hoặc một mã đại diện riêng có thể thích hợp. Khi `Enum.valueOf()` nhận input ngoài tập, cần xử lý `IllegalArgumentException`; với null, cần kiểm tra riêng.

### 7.2. Cache/symbol table cục bộ có giới hạn

Khi cần canonical hóa các chuỗi runtime, một cache cục bộ có **maximum size**, giới hạn độ dài, TTL/LRU hoặc khả năng loại bỏ rõ ràng có thể dễ kiểm soát hơn pool JVM. Cần tính đến đồng bộ nếu cache dùng chung nhiều thread, chính sách loại bỏ khi đầy và overhead cache; không phải mọi chuỗi ngắn ngủi đều cần cache.

Không tự cho rằng `WeakHashMap<String, String>` với **cùng đối tượng** làm key và value sẽ tự dọn entry: value mạnh giữ sống key, chống lại ý định weak key. Các cấu trúc weak/soft references đòi hỏi hiểu reference graph và kiểm thử bộ nhớ; một thư viện cache có giới hạn thường dễ dùng đúng hơn.

### 7.3. String deduplication của GC khác intern

Một số JVM/collector hỗ trợ **String Deduplication**, chẳng hạn G1 trên HotSpot khi bật tùy chọn phù hợp như `-XX:+UseStringDeduplication`. Deduplication có thể cho nhiều đối tượng `String` **chia sẻ mảng byte chứa nội dung**, nhưng **không bắt buộc hợp nhất bản thân các đối tượng String**. Vì vậy:

- String deduplication có thể giảm vùng lưu nội dung, nhưng `a == b` vẫn có thể false.
- `intern()` trả về cùng một đại diện String khi các nội dung bằng nhau.
- Cả hai đều có chi phí/điều kiện riêng; tính khả dụng và hiệu quả phụ thuộc JVM và collector, phải kiểm chứng với cấu hình thật.

Không nên bật cấu hình hoặc thay code chỉ dựa vào phỏng đoán rằng một kỹ thuật luôn nhanh hơn.

## 8. Đo hiệu quả thế nào?

Cần ít nhất hai bộ tải:

| Tải đo | Dữ liệu | Các chỉ số quan trọng |
|---|---|---|
| Lặp cao, vocabulary nhỏ | Hàng triệu lần xuất hiện với ít mã khác nhau | Heap sống sau GC, số lượng String/mảng byte, allocation, throughput |
| Cardinality cao, dữ liệu đến liên tục | Phần lớn giá trị mới, có thể rất dài | CPU tra cứu, allocation, GC pause, độ trễ đuôi, mức dùng heap |

Dùng profiler allocation/heap, Java Flight Recorder và GC logs theo hoàn cảnh. Nếu có công cụ JDK, `jcmd <pid> GC.class_histogram` giúp quan sát số đối tượng theo class; **riêng histogram không chứng minh số chuỗi trùng nội dung**. Đo microbenchmark bằng JMH thay vì một vòng lặp `System.nanoTime()` ngắn vì JVM có JIT, warmup và GC khiến kết quả lệch.

Tiêu chí ra quyết định gồm: có giảm **heap sống thực** không, có tăng CPU/latency/GC không, có làm hệ thống chịu tải nhập dữ liệu mới kém hơn không, và liệu enum hoặc cache giới hạn có tốt hơn không. Nếu khác biệt không đáng kể, ưu tiên cách đơn giản và dễ kiểm soát.

## 9. Sai lầm phổ biến và cách nhớ

| Nhận định sai | Cách hiểu đúng |
|---|---|
| Mọi String cùng nội dung đều `==` | Chỉ `equals()` kiểm tra nội dung; `==` kiểm tra tham chiếu |
| `intern()` sửa String gốc | Trả về tham chiếu canonical; cần gán lại nếu muốn dùng |
| `intern()` luôn tiết kiệm RAM | Còn chi phí tra cứu, pool và tạo chuỗi tạm |
| Interned String luôn bất tử | Vòng đời phụ thuộc JVM, GC và tham chiếu; không khẳng định chung |
| GC sẽ dọn nhanh nên cứ intern mọi input | Không kiểm soát được GC hay vòng đời pool; input mới liên tục vẫn gây chi phí |
| Intern trước để biến input thành hợp lệ | Phải validation và giới hạn **trước** intern |
| `HashMap` cần intern key | `String.equals()` và `hashCode()` đã xử lý khóa theo nội dung |
| String deduplication làm hai String cùng `==` | Có thể chia sẻ dữ liệu byte mà vẫn là hai object |

## 10. Checklist thực hành

- [ ] Tập giá trị có giới hạn đáng tin về **cardinality**, **độ dài** và **tần suất**.
- [ ] Đã xác minh có nhiều String trùng nội dung sống đủ lâu.
- [ ] Mọi nguồn không tin cậy đều qua ràng buộc, validation và chuẩn hóa phù hợp trước intern.
- [ ] Đã thử `enum`, constants, ID hoặc cache cục bộ có giới hạn.
- [ ] Không dựa vào `==` ở ranh giới mà String có thể chưa canonical.
- [ ] Đã đo cả dữ liệu nhiều lặp lẫn trường hợp đầu vào có rất nhiều giá trị mới.
- [ ] Lợi ích bộ nhớ/hiệu năng thật sự bù được chi phí CPU, GC và độ phức tạp.

`intern()` phù hợp **khi đầu vào có từ vựng giới hạn, trùng lặp nhiều, được giữ lâu và việc chia sẻ đã được đo có ích**. Không intern đại trà chuỗi do người dùng hoặc bên thứ ba tự do cung cấp; với dữ liệu như vậy, ưu tiên validation, `equals()` và cách lưu trữ có giới hạn do ứng dụng kiểm soát.

</details>

- [Quay lại đầu trang](#back-to-top)
