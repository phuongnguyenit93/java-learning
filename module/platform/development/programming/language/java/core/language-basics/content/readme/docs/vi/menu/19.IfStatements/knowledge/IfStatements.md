<a id="back-to-top"></a>

# Rẽ nhánh if

## Menu
- [Cú pháp if, else và chuỗi else if](#if-statements-001)
- [Điều kiện phải có kiểu boolean hoặc Boolean được unboxing](#if-statements-002)
- [Khối lệnh và rủi ro bỏ dấu ngoặc nhọn](#if-statements-003)
- [else gắn với if gần nhất chưa có else](#if-statements-004)
- [Phân biệt nhiều if độc lập với một chuỗi loại trừ](#if-statements-005)
- [Điều kiện biên và thứ tự xét các nhánh](#if-statements-006)
- [Guard clause để giảm mức lồng nhau](#if-statements-007)
- [Kiểm tra từng nhánh bằng bảng trường hợp đầu vào](#if-statements-008)

## <a id="if-statements-001">Cú pháp if, else và chuỗi else if</a>

<details>
<summary>Xem chi tiết</summary>

# Cú pháp `if`, `else` và chuỗi `else if` — Java 17+

`if` là câu lệnh **rẽ nhánh theo điều kiện**: chương trình đánh giá một biểu thức `boolean` và chỉ chạy khối lệnh phù hợp. Có thể viết một `if` đơn, thêm nhánh `else`, hoặc nối các trường hợp bằng `else if`.

~~~java
int age = 20;

if (age >= 18) {
    System.out.println("Đủ tuổi");
} else {
    System.out.println("Chưa đủ tuổi");
}
~~~

Ở ví dụ này, điều kiện `age >= 18` là `true` nên chỉ nhánh đầu tiên chạy. Không có cơ chế tự động chạy tiếp nhánh `else`.

## 1. Ba dạng cú pháp nền tảng

### Dạng 1 — Chỉ `if`

~~~java
int quantity = 3;

if (quantity > 0) {
    System.out.println("Có hàng");
}
~~~

Nếu điều kiện false, khối bị bỏ qua; chương trình tiếp tục ở câu lệnh sau `if`. **`else` không bắt buộc**.

### Dạng 2 — `if / else`

~~~java
int balance = 50;

if (balance >= 100) {
    System.out.println("Đủ tiền");
} else {
    System.out.println("Chưa đủ tiền");
}
~~~

Chọn **một trong hai** khối. `else` không có điều kiện riêng: nó được chọn khi điều kiện `if` là false.

### Dạng 3 — Chuỗi `if / else if / else`

~~~java
int score = 83;

if (score >= 90) {
    System.out.println("A");
} else if (score >= 75) {
    System.out.println("B");
} else if (score >= 60) {
    System.out.println("C");
} else {
    System.out.println("D");
}
~~~

Java đánh giá các điều kiện **từ trên xuống**. Gặp điều kiện true đầu tiên thì chạy nhánh tương ứng và **bỏ qua tất cả nhánh còn lại**. `else` cuối cùng là nhánh mặc định, có thể bỏ nếu không cần.

**`else if` không phải từ khóa ghép riêng:** về cú pháp, đó là một `else` theo sau bởi một câu lệnh `if`; cách viết liền mạch tạo thành chuỗi nhiều trường hợp.

## 2. Sơ đồ lựa chọn nhánh

~~~mermaid
flowchart TD
    Start["Bắt đầu"] --> C1{"Điều kiện 1?"}
    C1 -- true --> B1["Chạy nhánh if"]
    C1 -- false --> C2{"Điều kiện 2?"}
    C2 -- true --> B2["Chạy nhánh else if"]
    C2 -- false --> B3["Chạy else (nếu có)"]
    B1 --> End["Lệnh sau chuỗi"]
    B2 --> End
    B3 --> End
~~~

Sơ đồ minh họa một `else if`. Có thể thêm nhiều `else if` theo cách tương tự; nguyên tắc vẫn là **nhánh đúng đầu tiên thắng**.

| Cấu trúc | Điều kiện được xét | Số nhánh có thể chạy trong một lần đi qua |
| --- | --- | --- |
| `if (c) {...}` | Một điều kiện | 0 hoặc 1 |
| `if (c) {...} else {...}` | Một điều kiện | Đúng 1 nếu điều kiện hoàn tất bình thường |
| `if (a) {...} else if (b) {...} else {...}` | Lần lượt `a` rồi `b` khi cần | Đúng 1 nếu có `else` và không có ngoại lệ |
| Hai câu `if` độc lập | Mỗi câu có điều kiện riêng | Có thể chạy cả hai |

Nếu một điều kiện đang được đánh giá ném ngoại lệ, chuỗi không hoàn tất theo đường đi bình thường; các bảng trên giả định điều kiện được đánh giá thành công.

## 3. Chương trình Java 17+ có thể biên dịch và chạy

Ví dụ này tập trung vào ba dạng cú pháp vừa học. Các kỹ thuật như `Boolean` nullable, `if` độc lập, khối lệnh và guard clause được thực hành riêng ở các bài tiếp theo.

Tạo tệp UTF-8 tên **`IfElseChainDemo.java`**:

~~~java
public class IfElseChainDemo {
    static String grade(int score) {
        if (score < 0 || score > 100) {
            return "invalid";
        }
        if (score >= 90) {
            return "A";
        } else if (score >= 75) {
            return "B";
        } else if (score >= 60) {
            return "C";
        } else {
            return "D";
        }
    }

    public static void main(String[] args) {
        System.out.println("== if ==");
        int stock = 3;
        if (stock > 0) {
            System.out.println("stock=available");
        }

        System.out.println("== if / else ==");
        int age = 20;
        if (age >= 18) {
            System.out.println("age=adult");
        } else {
            System.out.println("age=minor");
        }

        System.out.println("== else if ==");
        for (int score : new int[] {95, 75, 60, 59, -1, 101}) {
            System.out.println(score + "=" + grade(score));
        }
        System.out.println("end");
    }
}
~~~

Trong thư mục chứa tệp, dùng **JDK 17 trở lên** với PowerShell hoặc Bash:

~~~bash
javac -encoding UTF-8 IfElseChainDemo.java
java IfElseChainDemo
~~~

**Exact expected stdout** của lệnh `java IfElseChainDemo`:

~~~text
== if ==
stock=available
== if / else ==
age=adult
== else if ==
95=A
75=B
60=C
59=D
-1=invalid
101=invalid
end
~~~

Cả ba dạng đều có thể chạy độc lập. `grade` còn xác thực điểm ngoài miền trước khi phân loại, giúp đầu vào không hợp lệ không bị gán nhãn.

## 4. Điều kiện của `if`: boolean, không phải truthiness

Java chỉ chấp nhận biểu thức điều kiện có kiểu `boolean` hoặc `Boolean` có thể **unbox**. Số, chuỗi hay đối tượng không tự chuyển thành true/false:

~~~java
int count = 3;
if (count > 0) {
    System.out.println("has items");
}
// if (count) { } // Lỗi biên dịch: int không phải boolean.
~~~

Một biến `Boolean` có thể null; `if (nullableFlag)` sẽ ném `NullPointerException` khi unbox nếu giá trị null. Muốn chỉ chấp nhận true, dùng `Boolean.TRUE.equals(nullableFlag)`; với `String`, guard `s != null && !s.isBlank()` tránh gọi phương thức trên null.

Cẩn thận `if (flag = true)`: đây là **phép gán boolean**, có thể biên dịch và khiến nhánh chạy, không phải phép kiểm tra thuần túy. Chủ đề `Boolean` nullable, unboxing, biểu thức điều kiện và short-circuit được giải thích sâu ở bài chuyên `boolean/Boolean`.


## 5. Nhánh đúng đầu tiên thắng: sắp điều kiện có chủ đích

Với điểm từ 0 đến 100, phải kiểm tra **ngưỡng hẹp/cao** trước ngưỡng rộng/thấp:

~~~java
if (score >= 90) {
    level = "A";
} else if (score >= 75) {
    level = "B";
} else if (score >= 60) {
    level = "C";
} else {
    level = "D";
}
~~~

Nếu đảo thành `if (score >= 60) ... else if (score >= 90)`, điểm 95 rơi vào nhánh đầu và bị phân loại sai. Trong chương trình chính, `grade` còn kiểm tra `score < 0 || score > 100` trước khi xếp loại.

Khi lập quy tắc phân loại, hãy xác định miền hợp lệ, **nhánh ưu tiên** và giá trị tại đúng biên. Bài điều kiện biên sẽ xây dựng bảng điểm ngay dưới/đúng/trên các ngưỡng và phân tích nhánh bị che khuất.


## 6. Nhiều `if` độc lập có thể cùng chạy

~~~java
int value = 5;
if (value > 0) {
    System.out.println("positive");
}
if (value < 10) {
    System.out.println("below ten");
}
~~~

Hai điều kiện đều đúng nên cả hai dòng được in. Nếu đổi `if` thứ hai thành `else if`, nó **không được xét** sau khi nhánh đầu đã chạy. Dùng nhiều `if` khi cần nhiều hành động đồng thời; dùng một chuỗi `else if` khi chỉ chọn **một** hành động/nhãn.

Quy tắc này còn ảnh hưởng tới **việc điều kiện sau có được đánh giá không**, đặc biệt nếu chứa lời gọi phương thức có tác dụng phụ. Bài chuyên so sánh `if` độc lập và chuỗi loại trừ phân tích bảng hành vi, cảnh báo tích lũy và trace của hai cách viết.


## 7. Ngoặc nhọn xác định đúng thân nhánh

~~~java
boolean authorized = false;
if (authorized)
    System.out.println("authorized");
System.out.println("always"); // Luôn chạy.
~~~

Khi bỏ `{}`, chỉ **một câu lệnh** ngay sau điều kiện là thân `if`; mức thụt lề không thay đổi ngữ pháp. Với `if` lồng nhau, `else` gắn với `if` gần nhất chưa có `else`:

~~~java
if (outer) {
    if (inner) {
        System.out.println("inner true");
    } else {
        System.out.println("inner false");
    }
}
~~~

Dùng ngoặc nhọn ngay cả khi chỉ có một câu lệnh để bảo vệ phạm vi và tránh lỗi sau khi bổ sung code. Đừng đặt dấu `;` thừa sau `if (condition)`, vì `;` có thể trở thành **empty statement** làm thân `if` rỗng. Hai bài chuyên về khối lệnh và `dangling else` trình bày chi tiết phạm vi biến, các cách ghép `else` và ví dụ gây lỗi logic.


## 8. `return` sớm cho trường hợp đã kết luận

Một phương thức có thể **kết thúc ngay** khi đã biết kết quả, nhờ vậy không cần lồng mọi trường hợp vào nhiều nhánh:

~~~java
static String sign(int n) {
    if (n > 0) {
        return "positive";
    }
    if (n < 0) {
        return "negative";
    }
    return "zero";
}
~~~

Hai `if` ở đây không cần `else if` vì `return` đã kết thúc phương thức ở nhánh được chọn. Tuy nhiên, nếu nhánh trước **không `return`/`throw`**, thay `else if` bằng `if` độc lập có thể đổi hành vi. Dùng guard clause khi cần loại trường hợp không hợp lệ hoặc kết thúc sớm trước đường xử lý chính; bài guard clause đi sâu vào thứ tự, ngoại lệ, vòng lặp và dọn dẹp tài nguyên.


## 9. Các lỗi thường gặp

| Sai lầm | Cách hiểu đúng |
| --- | --- |
| “`if (1)` hợp lệ vì 1 nghĩa là true.” | Java yêu cầu `boolean`, không có truthiness cho số |
| “`else` bắt buộc sau mọi `if`.” | `else` là tùy chọn |
| “Mọi `else if` đều được chạy để tìm tất cả nhánh đúng.” | Chỉ kiểm tra đến **nhánh true đầu tiên** |
| “Hai `if` liên tiếp giống `if / else if`.” | Hai `if` độc lập có thể cùng chạy |
| “Thứ tự điều kiện không quan trọng.” | Điều kiện rộng đặt trước có thể che điều kiện cụ thể |
| “`if (flag = true)` tương đương kiểm tra flag.” | Đó là phép gán true, có thể tạo bug |
| “`Boolean null` tự hiểu là false.” | Unboxing null gây `NullPointerException` |
| “Nếu bỏ ngoặc nhọn, mọi dòng thụt đầu dòng đều thuộc `if`.” | Chỉ **một câu lệnh** ngay sau điều kiện |
| “`else` luôn gắn với `if` ngoài cùng.” | Gắn với `if` gần nhất chưa có `else` |
| “`if (condition);` có khối điều kiện phía sau.” | Dấu `;` làm thân `if` trở thành câu lệnh rỗng |

## 10. Bài tập tự kiểm tra

1. `int x = 7; if (x > 5) {...} else {...}` chạy nhánh nào?  
   *Đáp án:* Nhánh `if`.
2. `int x = 5; if (x > 0) {...} if (x < 10) {...}` có thể chạy bao nhiêu nhánh?  
   *Đáp án:* Cả hai.
3. Với `score = 95`, vì sao `if (score >= 60) ... else if (score >= 90) ...` sai ý định phân loại?  
   *Đáp án:* Nhánh `>= 60` đã đúng nên `>= 90` không được xét.
4. `if (0)` có biên dịch không?  
   *Đáp án:* Không; điều kiện phải có kiểu boolean.
5. `Boolean b = null; if (b) {...}` có hành vi gì khi chạy?  
   *Đáp án:* Ném `NullPointerException` do unboxing.
6. `if (n > 0);` có tác dụng gì?  
   *Đáp án:* Điều kiện được kiểm tra, nhưng thân là **câu lệnh rỗng**.
7. Với hai `if` lồng không có ngoặc nhọn, `else` gắn với nhánh nào?  
   *Đáp án:* `if` gần nhất còn chưa có `else`.
8. Chuỗi `else if` có thể kết thúc không có `else` không?  
   *Đáp án:* Có; nếu không nhánh nào đúng thì không khối nào được chạy.

## Checklist

- [ ] Viết đúng `if (booleanCondition) { ... }`.
- [ ] Biết `else` là tùy chọn và `else if` nối thêm những trường hợp theo thứ tự.
- [ ] Hiểu **chỉ nhánh đúng đầu tiên** trong chuỗi được chạy.
- [ ] Phân biệt nhiều `if` độc lập với chuỗi `else if`.
- [ ] Kiểm tra kiểu `boolean`, wrapper null và các điều kiện ngắn mạch.
- [ ] Đặt điều kiện cụ thể trước điều kiện rộng khi có chồng lấp.
- [ ] Luôn ưu tiên dấu ngoặc nhọn để tránh lỗi `dangling else` và thụt dòng gây hiểu nhầm.
- [ ] Không vô tình thêm `;` ngay sau `if (...)` hoặc gán `=` thay cho kiểm tra.

**Kết luận:** `if`, `else` và chuỗi `else if` là công cụ rẽ nhánh cốt lõi của Java. Đọc đúng **điều kiện boolean**, **thứ tự đánh giá**, **phạm vi ngoặc nhọn** và **mối quan hệ giữa các nhánh** giúp tránh lỗi logic mà trình biên dịch không nhất thiết phát hiện.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="if-statements-002">Điều kiện phải có kiểu boolean hoặc Boolean được unboxing</a>

<details>
<summary>Xem chi tiết</summary>

# Điều kiện phải có kiểu `boolean` hoặc `Boolean` được unboxing — Java 17+

## 1. Quy tắc quan trọng nhất

Trong Java, biểu thức đặt trong dấu ngoặc của `if` phải có kiểu **`boolean`** hoặc **`java.lang.Boolean`** có thể được *unboxing* thành `boolean`. Không thể dùng trực tiếp số, chuỗi hoặc tham chiếu đối tượng làm điều kiện chỉ vì chúng “có giá trị”.

~~~java
boolean ready = true;

if (ready) {
    System.out.println("ready");
}
~~~

**Expected stdout:**

~~~text
ready
~~~

Với `Boolean`, Java tự gọi chuyển đổi unboxing để lấy `boolean` khi cần:

~~~java
Boolean enabled = Boolean.TRUE;

if (enabled) {
    System.out.println("enabled");
}
~~~

**Expected stdout:**

~~~text
enabled
~~~

**Cảnh báo:** nếu `enabled == null`, unboxing gây `NullPointerException`. Vì vậy cần phân biệt:

- **`boolean` nguyên thủy:** chỉ có `true` hoặc `false`, không thể null.
- **`Boolean` lớp bao:** có thể mang `Boolean.TRUE`, `Boolean.FALSE` hoặc `null`; phải quy định cách xử lý null.

Bài này tập trung vào kiểu của điều kiện `if`, không mở rộng sang hệ thống rẽ nhánh hoặc vòng lặp nói chung.

## 2. Cú pháp `if` và kiểm tra kiểu tại compile time

Cú pháp cơ bản:

~~~java
if (condition) {
    // Chạy nếu condition có giá trị true
} else {
    // Chạy nếu condition có giá trị false
}
~~~

Compiler phải xác nhận `condition` có kiểu boolean/Boolean thích hợp **trước khi chạy**. Kết quả cụ thể true hay false chỉ quyết định nhánh được thực thi.

| Biểu thức trong `if (...)` | Hợp lệ? | Vì sao? |
|---|---|---|
| `true` | Có | Literal `boolean` |
| `ready` với `boolean ready` | Có | Biến `boolean` |
| `enabled` với `Boolean enabled` | Có | `Boolean` được unboxing |
| `score >= 60` | Có | Phép so sánh trả `boolean` |
| `value != 0` | Có | Phép so sánh trả `boolean` |
| `text != null` | Có | Phép so sánh tham chiếu với null trả `boolean` |
| `score` với `int score` | **Không** | `int` không phải điều kiện boolean |
| `text` với `String text` | **Không** | Java không coi String rỗng/không rỗng là truthy/falsy |
| `object` với `Object object` | **Không** | Java không coi object khác null là true |
| `boxedNumber` với `Integer boxedNumber` | **Không** | Unboxing `Integer` ra `int`, vẫn không phải boolean |

Ví dụ bị lỗi compile (giữ nguyên ở dạng comment để code ví dụ vẫn chạy):

~~~java
int count = 3;
// if (count) { } // SAI: int cannot be converted to boolean

String title = "Java";
// if (title) { } // SAI: String cannot be converted to boolean

Object object = Boolean.TRUE;
// if (object) { } // SAI: kiểu tĩnh Object, không phải Boolean
~~~

Ngay cả khi một biến `Object` **đang trỏ tới object `Boolean.TRUE`**, compiler nhìn thấy kiểu tĩnh `Object` trong `if (object)`. Muốn dùng, phải kiểm tra/chuyển kiểu phù hợp thay vì giả định Java tự đoán kiểu object ở runtime.

## 3. Chuyển ý nghĩa nghiệp vụ thành biểu thức boolean

Nếu đầu vào là số, hãy viết **phép so sánh tường minh**:

~~~java
int count = 3;

if (count > 0) {
    System.out.println("positive");
}

if (count != 0) {
    System.out.println("nonzero");
}
~~~

**Expected stdout:**

~~~text
positive
nonzero
~~~

Tương tự, với `String`:

~~~java
String title = "Java";
boolean valid = title != null && !title.isBlank();

if (valid) {
    System.out.println("valid title");
}
~~~

Ở đây `title != null`, `!title.isBlank()` và `valid` đều trả hoặc chứa giá trị **boolean**; `title` tự nó không phải điều kiện hợp lệ.

### Các biểu thức thường cho kết quả boolean

| Biểu thức | Kiểu kết quả |
|---|---|
| `a == b`, `a != b` | `boolean` |
| `x < y`, `x >= y` | `boolean` |
| `!active` | `boolean` |
| `hasAccess && isActive` | `boolean` |
| `isAdmin \|\| hasPermission` | `boolean` |
| `name == null` | `boolean` |
| `Boolean.TRUE.equals(flag)` | `boolean` |

**Đừng dùng giá trị số 0/1 thay `false`/`true`**: Java không thực hiện chuyển kiểu số → boolean ngầm định.

## 4. `Boolean` trong `if`: Java thực hiện unboxing

~~~java
Boolean boxedTrue = Boolean.TRUE;
Boolean boxedFalse = Boolean.FALSE;

if (boxedTrue) {
    System.out.println("true branch");
}

if (boxedFalse) {
    System.out.println("unexpected");
} else {
    System.out.println("false branch");
}
~~~

**Expected stdout:**

~~~text
true branch
false branch
~~~

Về ý nghĩa, `if (boxedTrue)` cần chuyển giá trị `Boolean` thành `boolean` để quyết định nhánh. Trường hợp này là **unboxing conversion**, không phải so sánh địa chỉ tham chiếu.

`Boolean` thường xuất hiện trong dữ liệu cho phép null, các collection/generic hoặc API trả kết quả ba trạng thái. Unboxing tiện lợi khi giá trị chắc chắn **khác null**, nhưng có thể gây lỗi runtime nếu dữ liệu thiếu.

## 5. `Boolean null`: hợp lệ khi biên dịch, lỗi khi chạy

~~~java
Boolean flag = null;

try {
    if (flag) {
        System.out.println("never printed");
    }
} catch (NullPointerException ex) {
    System.out.println("null Boolean -> NullPointerException");
}
~~~

**Expected stdout:**

~~~text
null Boolean -> NullPointerException
~~~

Mã **biên dịch hợp lệ** vì biến có kiểu tĩnh `Boolean`. Tuy nhiên, ở runtime, Java cần unbox giá trị null và ném `NullPointerException` **trước khi chọn nhánh**.

Lỗi không phải do `if` không chấp nhận `Boolean`; lỗi là do giá trị wrapper không thể unbox khi bằng null.

**Phân biệt hai loại lỗi:**

- `if (123)`: lỗi **compile time** vì điều kiện có kiểu `int`.
- `Boolean flag = null; if (flag)`: compile được, nhưng lỗi **runtime** khi unboxing.

## 6. Các cách kiểm tra `Boolean` nullable an toàn

Giả sử yêu cầu: **chỉ khi giá trị đúng là true mới cho phép**.

**Cách 1 — Guard với `&&`:**

~~~java
Boolean enabled = null;
boolean allowed = enabled != null && enabled;
System.out.println(allowed); // false
~~~

`&&` bỏ qua toán hạng phải khi `enabled != null` là false. Do đó, `enabled` không bị unbox lúc null.

**Cách 2 — Dùng `Boolean.TRUE.equals`:**

~~~java
Boolean enabled = null;
boolean allowed = Boolean.TRUE.equals(enabled);
System.out.println(allowed); // false
~~~

Lời gọi trên object hằng `Boolean.TRUE` không unbox `enabled`, và an toàn với null.

**Cách 3 — Phân biệt đủ ba trạng thái:**

~~~java
static String describe(Boolean flag) {
    if (flag == null) {
        return "UNKNOWN";
    }
    return flag ? "ON" : "OFF";
}
~~~

| Giá trị `Boolean` | `Boolean.TRUE.equals(flag)` | `describe(flag)` |
|---|---|---|
| `Boolean.TRUE` | true | `ON` |
| `Boolean.FALSE` | false | `OFF` |
| `null` | false | `UNKNOWN` |

**Quan trọng:** Nếu null mang ý nghĩa **chưa biết/chưa xác nhận**, không nên tự động coi null là false mà không có quyết định nghiệp vụ. `Boolean.TRUE.equals(flag)` phù hợp với quy tắc “chỉ true mới đạt”, nhưng cố ý không phân biệt false và null trong kết quả boolean.

## 7. `flag == true` không phải null guard

~~~java
Boolean flag = null;

// if (flag == true) { }
// Khi chạy: NullPointerException do phải unbox flag.
~~~

So sánh với primitive `true` có thể khiến `Boolean` phải unbox, nên vẫn nguy hiểm khi flag null.

**Nên dùng:**

~~~java
if (Boolean.TRUE.equals(flag)) {
    // Chỉ chạy khi flag khác null và có giá trị true
}
~~~

Hoặc:

~~~java
if (flag != null && flag) {
    // Kiểm tra null trước khi unbox
}
~~~

**Không lẫn hai ý nghĩa:**

- `flag == null`: kiểm tra tham chiếu null, an toàn.
- `flag == true`: kiểm tra giá trị logic bằng true, có thể unbox và gây NPE.
- `Boolean.TRUE.equals(flag)`: so **giá trị object** an toàn với null.

Để kiểm tra đúng false (không phải null):

~~~java
boolean explicitlyFalse = Boolean.FALSE.equals(flag);
~~~

Với `flag == null`, cả `Boolean.TRUE.equals(flag)` và `Boolean.FALSE.equals(flag)` đều false.

## 8. `&&` và `||`: đặt guard trước phép dùng giá trị

Java đánh giá các toán hạng của `&&` và `||` từ trái sang phải, có cơ chế **ngắn mạch**.

~~~java
Boolean approved = null;

boolean allowed = approved != null && approved;
boolean deniedOrUnknown = approved == null || !approved;

System.out.println(allowed);         // false
System.out.println(deniedOrUnknown); // true
~~~

- `approved != null && approved`: nếu approved null, bỏ qua vế phải, an toàn.
- `approved == null || !approved`: nếu approved null, bỏ qua vế phải, an toàn.
- `approved != null & approved`: toán tử `&` boolean **không ngắn mạch**, phải đánh giá vế phải và có thể NPE.
- `approved == null | !approved`: `|` cũng **không ngắn mạch**, có thể NPE.

Ví dụ `Boolean` trong **vế phải**:

~~~java
Boolean missing = null;

System.out.println(false && missing); // false, không unbox missing
System.out.println(true || missing);  // true, không unbox missing
~~~

Nhưng nếu **buộc phải đánh giá** nhánh chứa null, unboxing vẫn ném NPE:

~~~java
// boolean bad = true && missing; // NPE khi chạy
// boolean bad = false || missing; // NPE khi chạy
~~~

**Điều kiện bảo vệ phải được đặt trước thao tác nguy hiểm.** Chỉ xuất hiện toán tử `&&` ở đâu đó không đủ tạo an toàn nếu tham chiếu đã bị sử dụng khi chưa kiểm tra.

## 9. Phân biệt `=` với `==` trong điều kiện boolean

Một lỗi logic nguy hiểm:

~~~java
boolean granted = false;

if (granted = true) {
    System.out.println("entered");
}

System.out.println(granted);
~~~

**Expected stdout:**

~~~text
entered
true
~~~

`granted = true` là **biểu thức gán** có kiểu và giá trị `boolean`. Nó biên dịch hợp lệ, đặt `granted` thành true rồi khiến nhánh `if` chạy.

Nếu ý định chỉ là **kiểm tra**, hãy viết:

~~~java
if (granted) {
    // Kiểm tra true, không gán
}
~~~

Với số nguyên, `if (number = 1)` lại **không biên dịch** vì biểu thức gán có kiểu `int`, không phải boolean. Đây là lý do lỗi `if (booleanVariable = true)` đặc biệt dễ lọt qua compile time.

**Khuyến nghị:** tránh gán trạng thái bên trong điều kiện `if` trừ khi có lý do rất rõ; tách phép gán thành câu lệnh riêng.

## 10. Chuỗi `"true"` và `"false"` không phải kiểu boolean

~~~java
String input = "true";
// if (input) { } // SAI: String không phải boolean/Boolean
~~~

Nếu dữ liệu đến từ văn bản, cần **phân tích giá trị** trước:

~~~java
boolean enabled = Boolean.parseBoolean(input);
if (enabled) {
    System.out.println("parsed true");
}
~~~

**Expected stdout:**

~~~text
parsed true
~~~

`Boolean.parseBoolean(String)` trả true **chỉ nếu chuỗi bằng `"true"` không phân biệt chữ hoa/thường**; các giá trị khác (kể cả null) trả false. Do đó:

~~~java
System.out.println(Boolean.parseBoolean("yes")); // false
~~~

Nếu API phải **từ chối văn bản không hợp lệ** thay vì âm thầm coi là false, tự định nghĩa parser nghiêm ngặt:

~~~java
static boolean parseStrictBoolean(String value) {
    if ("true".equalsIgnoreCase(value)) {
        return true;
    }
    if ("false".equalsIgnoreCase(value)) {
        return false;
    }
    throw new IllegalArgumentException("invalid boolean text");
}
~~~

Parser trên phân biệt `"true"`, `"false"` với `"yes"`, `"1"` hay null. Chính sách trim khoảng trắng hoặc chấp nhận các từ khác phải được định nghĩa theo yêu cầu input cụ thể.

## 11. Ví dụ Java 17 hoàn chỉnh, chạy được bằng CLI

Tạo đúng file **`IfBooleanConditionDemo.java`**:

~~~java
public final class IfBooleanConditionDemo {
    private IfBooleanConditionDemo() {}

    static boolean inScoreRange(int score) {
        return score >= 0 && score <= 100;
    }

    static String describe(Boolean flag) {
        if (flag == null) {
            return "UNKNOWN";
        }
        return flag ? "ON" : "OFF";
    }

    static boolean parseStrictBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException("invalid boolean text");
    }

    public static void main(String[] args) {
        boolean active = true;
        if (active) {
            System.out.println("primitive true=entered");
        }
        if (!active) {
            System.out.println("not active=entered");
        } else {
            System.out.println("primitive else=entered");
        }

        int score = 75;
        if (inScoreRange(score)) {
            System.out.println("score in range=true");
        }
        int count = 3;
        if (count > 0) {
            System.out.println("numeric comparison=true");
        }

        Boolean enabled = Boolean.TRUE;
        if (enabled) {
            System.out.println("boxed true=entered");
        }

        Boolean disabled = Boolean.FALSE;
        if (disabled) {
            System.out.println("boxed false=entered");
        } else {
            System.out.println("boxed false=else");
        }

        Boolean missing = null;
        try {
            if (missing) {
                System.out.println("never printed");
            }
        } catch (NullPointerException ex) {
            System.out.println("null condition=NullPointerException");
        }

        System.out.println("safe &&="
                + (missing != null && missing));
        System.out.println("safe true equals="
                + Boolean.TRUE.equals(missing));
        System.out.println("safe false equals="
                + Boolean.FALSE.equals(missing));
        System.out.println("null state=" + describe(missing));
        System.out.println("true state=" + describe(Boolean.TRUE));
        System.out.println("false state=" + describe(Boolean.FALSE));

        try {
            boolean result = missing == true;
            System.out.println(result);
        } catch (NullPointerException ex) {
            System.out.println("null == true=NullPointerException");
        }

        try {
            boolean result = missing != null & missing;
            System.out.println(result);
        } catch (NullPointerException ex) {
            System.out.println("eager guard=NullPointerException");
        }

        System.out.println("skip &&=" + (false && missing));
        System.out.println("skip ||=" + (true || missing));

        try {
            boolean result = true && missing;
            System.out.println(result);
        } catch (NullPointerException ex) {
            System.out.println("evaluated null=NullPointerException");
        }

        boolean granted = false;
        if (granted = true) {
            System.out.println("assignment in if=entered");
        }
        System.out.println("granted after assignment=" + granted);

        String input = "TrUe";
        if (parseStrictBoolean(input)) {
            System.out.println("strict parsed=true");
        }
        System.out.println("strict false="
                + parseStrictBoolean("FALSE"));
        System.out.println("lenient yes="
                + Boolean.parseBoolean("yes"));

        try {
            parseStrictBoolean("yes");
        } catch (IllegalArgumentException ex) {
            System.out.println("invalid input=" + ex.getMessage());
        }
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên; terminal đang ở thư mục chứa file. Chương trình chỉ dùng thư viện tiêu chuẩn JDK.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 IfBooleanConditionDemo.java
java -cp . IfBooleanConditionDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\IfBooleanConditionDemo.java
java -cp . IfBooleanConditionDemo
~~~

### Exact expected stdout

~~~text
primitive true=entered
primitive else=entered
score in range=true
numeric comparison=true
boxed true=entered
boxed false=else
null condition=NullPointerException
safe &&=false
safe true equals=false
safe false equals=false
null state=UNKNOWN
true state=ON
false state=OFF
null == true=NullPointerException
eager guard=NullPointerException
skip &&=false
skip ||=true
evaluated null=NullPointerException
assignment in if=entered
granted after assignment=true
strict parsed=true
strict false=false
lenient yes=false
invalid input=invalid boolean text
~~~

### Các điểm cần tự kiểm tra

1. `boolean` primitive chạy theo true/false như mong đợi; số nguyên chỉ được dùng thông qua phép so sánh `count > 0`.
2. `Boolean.TRUE` và `Boolean.FALSE` được unbox để điều khiển `if`.
3. `if (missing)` biên dịch nhưng ném NPE khi runtime unbox null.
4. `missing != null && missing` và `Boolean.TRUE.equals(missing)` cho false **mà không ném lỗi**.
5. `describe(missing)` giữ riêng trạng thái UNKNOWN, không nhập nhằng với false.
6. `missing == true` và `missing != null & missing` ném NPE vì vẫn yêu cầu unboxing null.
7. `false && missing` và `true || missing` bỏ qua vế phải, không unbox; ngược lại `true && missing` phải đánh giá vế phải nên NPE.
8. `if (granted = true)` cho thấy lỗi gán thay vì kiểm tra có thể compile và thay đổi trạng thái.
9. Parser nghiêm ngặt chấp nhận chữ hoa/thường của `true`/`false`, từ chối `"yes"`; `Boolean.parseBoolean("yes")` lặng lẽ cho false.

## 12. Lỗi phổ biến và cách tránh

| Sai lầm | Hệ quả | Cách phù hợp |
|---|---|---|
| `if (1)` | Lỗi compile: int không phải boolean | `if (number != 0)` nếu muốn kiểm tra khác 0 |
| `if (text)` | Lỗi compile: String không phải boolean | `text != null && !text.isBlank()` |
| `if (object)` với Object trỏ Boolean.TRUE | Kiểu tĩnh Object không hợp lệ | Dùng Boolean có kiểu rõ hoặc kiểm tra/chuyển kiểu |
| `if (nullableFlag)` khi null | Runtime NPE do unboxing | Guard hoặc `Boolean.TRUE.equals` |
| `if (nullableFlag == true)` là null-safe | Vẫn cần unboxing | `Boolean.TRUE.equals(nullableFlag)` |
| `flag != null & flag` | `&` đánh giá cả vế phải | `flag != null && flag` |
| `flag == null \| !flag` | `\|` đánh giá cả vế phải | `flag == null \|\| !flag` |
| Coi `null` là `false` mà không xét nghiệp vụ | Mất thông tin “chưa biết” | Giữ trạng thái UNKNOWN nếu cần |
| `if (flag = true)` khi định kiểm tra | Gán true rồi nhánh chạy | `if (flag)` |
| Dùng `Boolean.parseBoolean("yes")` như parser bắt lỗi | Trả false thay vì báo input không hợp lệ | Validate văn bản tường minh |
| Đổi `Boolean` thành `boolean` khi dữ liệu nullable | Mất trạng thái null hoặc NPE trong quá trình unbox | Xác định hợp đồng dữ liệu trước khi đổi |

## 13. Bài tập có đáp án

### Bài A — tại sao `if (number)` sai?

~~~java
int number = 5;
// if (number) { System.out.println("yes"); }
~~~

**Đáp án:** `number` có kiểu `int`, Java không cho chuyển `int → boolean` ngầm định. Nếu muốn kiểm tra dương, viết `if (number > 0)`; nếu muốn khác 0, viết `if (number != 0)`.

### Bài B — unboxing null

~~~java
Boolean accepted = null;
// if (accepted) { System.out.println("accepted"); }
~~~

**Đáp án:** Nếu thực thi câu `if`, chương trình ném `NullPointerException`. Có thể thay bằng:

~~~java
if (Boolean.TRUE.equals(accepted)) {
    System.out.println("accepted");
}
~~~

Không in gì và không ném NPE.

### Bài C — ba trạng thái

Yêu cầu `Boolean` nhận true, false hoặc null, tương ứng in `ON`, `OFF`, `UNKNOWN`.

**Đáp án:**

~~~java
static String state(Boolean flag) {
    if (flag == null) return "UNKNOWN";
    return flag ? "ON" : "OFF";
}
~~~

Phải kiểm tra null **trước khi** cho `Boolean` tham gia `?:` với hai nhánh logic có thể dùng unboxing.

### Bài D — ngắn mạch khác toán tử `&`

~~~java
Boolean flag = null;
System.out.println(flag != null && flag);
// System.out.println(flag != null & flag);
~~~

**Đáp án:** dòng đầu in `false`, không NPE. Dòng comment nếu thực thi sẽ ném NPE do `&` không short-circuit.

### Bài E — gán hay so sánh?

~~~java
boolean ready = false;
if (ready = true) {
    System.out.println("inside");
}
System.out.println(ready);
~~~

**Đáp án:**

~~~text
inside
true
~~~

Đây là phép gán `ready = true` hợp lệ về kiểu, nhưng thường sai ý định. Dùng `if (ready)` để kiểm tra biến.

### Bài F — xử lý văn bản đầu vào

Có input `"TRUE"`, `"FALSE"`, `"yes"`. Chỉ hai giá trị đầu được coi hợp lệ, không phân biệt hoa/thường.

**Đáp án:** dùng `parseStrictBoolean` ở chương trình đầy đủ, trả true cho TRUE, false cho FALSE và ném `IllegalArgumentException` với yes. Không dùng riêng `Boolean.parseBoolean` nếu cần phân biệt false hợp lệ với văn bản không hợp lệ.

## 14. Checklist và kết luận

- [ ] Biết điều kiện `if` phải có kiểu `boolean` hoặc `Boolean` được unboxing.
- [ ] Hiểu Java **không có implicit truthiness** cho số, String hay object.
- [ ] Biết phép so sánh trả boolean, ví dụ `count > 0`, `text != null`.
- [ ] Biết `if (Boolean.TRUE)` hợp lệ, nhưng `if ((Boolean) null)` có thể ném NPE.
- [ ] Phân biệt lỗi kiểu tại compile time và lỗi unboxing null tại runtime.
- [ ] Dùng `Boolean.TRUE.equals(flag)` cho chính sách “chỉ true”, hoặc `flag != null && flag`.
- [ ] Không coi null bằng false nếu nghiệp vụ cần đủ ba trạng thái.
- [ ] Biết `flag == true` có thể unbox null; `flag == null` là kiểm tra null an toàn.
- [ ] Biết `&&`/`||` ngắn mạch; `&`/`|` boolean không ngắn mạch.
- [ ] Không dùng nhầm `if (flag = true)` thành phép kiểm tra.
- [ ] Kiểm tra định dạng String trước khi chuyển thành boolean nếu cần từ chối input sai.
- [ ] Chạy `IfBooleanConditionDemo.java` bằng Java 17+ và đối chiếu exact stdout.

**Kết luận:** Java bắt buộc biểu thức điều kiện của `if` mang kiểu `boolean` hoặc `Boolean`. Với `Boolean`, compiler cho phép **unboxing**, nhưng runtime sẽ ném `NullPointerException` nếu giá trị là null. Viết điều kiện rõ nghĩa, guard null trước khi unbox và chọn chính sách xử lý ba trạng thái có chủ đích sẽ giúp nhánh `if` đúng kiểu, đúng logic và dễ bảo trì.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="if-statements-003">Khối lệnh và rủi ro bỏ dấu ngoặc nhọn</a>

<details>
<summary>Xem chi tiết</summary>

# Khối lệnh và rủi ro bỏ dấu ngoặc nhọn trong Java

> **Java core — language-basics / Rẽ nhánh if**  
> **Yêu cầu:** Java 17+, một file Java độc lập, chỉ dùng thư viện chuẩn.  
> **Mục tiêu:** Hiểu `if`/`else` điều khiển **một câu lệnh** hoặc **một khối lệnh `{ ... }`**; nhận diện lỗi do bỏ ngoặc nhọn, sai lệch thụt lề, `else` gắn nhầm và dấu chấm phẩy thừa. Biết viết nhánh rõ ràng, dễ sửa và dễ kiểm thử.

## 1. Một câu lệnh khác với một khối lệnh

Cú pháp `if` cơ bản:

~~~java
if (condition) {
    // Các câu lệnh thuộc nhánh if.
}
~~~

`condition` phải có kiểu `boolean` (hoặc `Boolean` có thể unbox). Một **khối** `{ ... }` được Java xem như **một câu lệnh khối**, chứa được nhiều câu lệnh con.

Java cho phép bỏ ngoặc nhọn khi thân `if` chỉ là **một câu lệnh**:

~~~java
boolean ready = true;
if (ready)
    System.out.println("READY");
~~~

Nhưng khi cần hai thao tác cùng phụ thuộc `ready`, phải dùng khối:

~~~java
if (ready) {
    System.out.println("READY");
    System.out.println("AUDITED");
}
~~~

Ngoặc nhọn quyết định **phạm vi thuộc nhánh**, còn dấu cách và thụt lề chỉ giúp người đọc. Java không dùng mức thụt lề để gom các câu lệnh như một số ngôn ngữ khác.

## 2. Lỗi kinh điển: nhìn thụt lề tưởng cả hai câu cùng thuộc `if`

Đoạn này hợp lệ về cú pháp nhưng có thể sai nghiệp vụ:

~~~java
boolean authorized = false;
if (authorized)
    System.out.println("Access granted");
    System.out.println("Sensitive action"); // LUÔN chạy.
~~~

Java hiểu là:

~~~java
if (authorized) {
    System.out.println("Access granted");
}
System.out.println("Sensitive action");
~~~

Lệnh in `"Sensitive action"` **nằm ngoài** `if`, cho dù được thụt vào cùng mức với lệnh đầu. Khi `authorized == false`, lệnh đó **vẫn chạy**.

Cách sửa an toàn:

~~~java
if (authorized) {
    System.out.println("Access granted");
    System.out.println("Sensitive action");
}
~~~

**Rủi ro khi bảo trì:** Ban đầu có thể chỉ một dòng sau `if` nên chương trình đúng. Người sửa thêm dòng thứ hai theo cùng thụt lề nhưng quên `{}`, khiến dòng mới chạy vô điều kiện. Những lỗi này đặc biệt nghiêm trọng nếu thân `if` là bước xác thực, trừ tiền, xóa dữ liệu hoặc đổi trạng thái.

## 3. Ngoặc nhọn thay đổi liên kết giữa các `if` lồng nhau

Khi viết `if` lồng mà không dùng ngoặc nhọn, `else` thuộc `if` gần nhất chưa có `else` (*dangling else*), không theo thụt lề:

~~~java
if (isMember)
    if (hasTicket)
        System.out.println("Enter");
    else
        System.out.println("No ticket");
~~~

Nếu `isMember` false, **không in dòng nào**; `else` thuộc `if (hasTicket)` nhưng toàn bộ câu `if` bên trong bị bỏ qua. Muốn `else` thuộc `if` ngoài, phải đóng **khối thân của `if` ngoài** trước `else`:

~~~java
if (isMember) {
    if (hasTicket) {
        System.out.println("Enter");
    }
} else {
    System.out.println("Not a member");
}
~~~

Với `isMember=false`, đoạn sau in `Not a member`. Đây là **hai hành vi khác nhau**, không chỉ hai cách format. Bài hiện tại nhấn mạnh **khối ngoặc quyết định phạm vi**; bài kế tiếp phân tích chuyên sâu luật gắn `else`, hai `else` lồng nhau, bảng trường hợp và thứ tự đánh giá.


## 4. Dấu `;` thừa có thể tạo một nhánh rỗng

`;` đơn lẻ là **câu lệnh rỗng** (*empty statement*). Vì vậy:

~~~java
int attempts = 0;
if (false); // Dấu ; chính là toàn bộ thân if: không làm gì.
attempts++;
System.out.println(attempts); // 1
~~~

`attempts++` nằm **ngoài** `if` và chạy bất kể điều kiện false. Dấu `;` sau `if (condition)` thường là lỗi gõ phím.

**Đúng:**

~~~java
if (allowed) {
    performAction();
}
~~~

**Sai về ý định:**

~~~java
if (allowed); // Nhánh if rỗng.
{
    performAction(); // Khối này độc lập, vẫn chạy.
}
~~~

Dùng formatter và lint/static analysis để phát hiện `if` có thân rỗng bất thường; vẫn cần review ngữ nghĩa vì chương trình **có thể biên dịch bình thường**.

## 5. Ngoặc nhọn còn tạo phạm vi biến (*scope*)

Bên trong khối có thể khai báo biến cục bộ chỉ dùng trong khối:

~~~java
int total = 0;
if (true) {
    int increment = 7;
    total += increment;
}
System.out.println(total); // 7
// System.out.println(increment); // LỖI BIÊN DỊCH: ngoài phạm vi.
~~~

Ngoặc nhọn giúp giới hạn thời gian **biến cục bộ có thể được gọi bằng tên đó trong mã** (lexical scope); không có nghĩa mọi đối tượng được tạo trong khối bị hủy ngay khi khối kết thúc. Với `if` và `else`, mỗi khối có thể có các biến cục bộ riêng.

Cần chú ý **definite assignment** khi gán giá trị trong nhánh:

~~~java
int discount;
if (eligible) {
    discount = 10;
} else {
    discount = 0;
}
System.out.println(discount); // Hợp lệ: cả hai nhánh đều gán.
~~~

Nếu chỉ gán `discount` trong `if` nhưng không có `else`, sau đó dùng `discount` thì trình biên dịch có thể báo biến chưa được gán ở mọi đường đi. Ngoặc nhọn giúp thấy rõ đường đi, nhưng **không thay thế kiểm tra luồng gán của Java**.

## 6. Quy tắc chọn cách viết

| Tình huống | Nên làm |
| --- | --- |
| `if` hiện tại chỉ có một câu lệnh | Vẫn dùng `{ ... }` để dễ bổ sung sau này |
| `if` lồng `if / else` | Dùng khối và thụt lề rõ, tránh *dangling else* |
| Sau điều kiện có dấu `;` đáng ngờ | Kiểm tra xem có vô tình tạo câu lệnh rỗng không |
| Nhánh bảo vệ thao tác quan trọng | Đặt mọi thao tác được bảo vệ vào cùng một khối |
| Cần biến cục bộ phục vụ riêng một nhánh | Khai báo bên trong khối nhánh |
| Thêm/sửa câu lệnh trong code cũ không ngoặc | Xác minh lại phạm vi điều khiển trước và sau khi sửa |

**Khuyến nghị thực hành:** Luôn viết ngoặc nhọn cho `if`, `else if` và `else` ngay cả khi chỉ có một câu lệnh. Java không bắt buộc quy tắc này, nhưng nó làm ý định rõ ràng và giúp hạn chế lỗi khi thêm dòng. Format code tự động để phát hiện thụt lề đáng ngờ; viết test cho cả nhánh đúng và sai.

## 7. Chương trình Java 17+ chạy độc lập

Tạo file **IfBlocksDemo.java**:

~~~java
public final class IfBlocksDemo {
    private static void withoutBraces(boolean authorized) {
        if (authorized)
            System.out.println("unguardedProtected");
            System.out.println("unguardedAlways");
    }

    private static void withBraces(boolean authorized) {
        if (authorized) {
            System.out.println("guardedProtected");
            System.out.println("guardedAudit");
        }
        System.out.println("guardedAfter");
    }

    private static String danglingElse(boolean member, boolean ticket) {
        if (member)
            if (ticket)
                return "ENTER";
            else
                return "NO_TICKET";
        return "NO_BRANCH";
    }

    private static String outerElse(boolean member, boolean ticket) {
        if (member) {
            if (ticket) {
                return "ENTER";
            }
        } else {
            return "NOT_MEMBER";
        }
        return "NO_TICKET";
    }

    public static void main(String[] args) {
        System.out.println("authorized=false");
        withoutBraces(false);
        withBraces(false);

        System.out.println("authorized=true");
        withoutBraces(true);
        withBraces(true);

        System.out.println("danglingFalseTrue=" + danglingElse(false, true));
        System.out.println("danglingTrueFalse=" + danglingElse(true, false));
        System.out.println("danglingTrueTrue=" + danglingElse(true, true));

        System.out.println("outerFalseTrue=" + outerElse(false, true));
        System.out.println("outerTrueFalse=" + outerElse(true, false));
        System.out.println("outerTrueTrue=" + outerElse(true, true));

        int attempts = 0;
        if (false);
        attempts++;
        System.out.println("emptyStatementAttempts=" + attempts);

        int total = 0;
        if (true) {
            int increment = 7;
            total += increment;
        }
        System.out.println("blockScopeTotal=" + total);

        boolean eligible = false;
        int discount;
        if (eligible) {
            discount = 10;
        } else {
            discount = 0;
        }
        System.out.println("discount=" + discount);
    }
}
~~~

Biên dịch và chạy tại thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 IfBlocksDemo.java
java IfBlocksDemo
~~~

**Expected stdout chính xác:**

~~~text
authorized=false
unguardedAlways
guardedAfter
authorized=true
unguardedProtected
unguardedAlways
guardedProtected
guardedAudit
guardedAfter
danglingFalseTrue=NO_BRANCH
danglingTrueFalse=NO_TICKET
danglingTrueTrue=ENTER
outerFalseTrue=NOT_MEMBER
outerTrueFalse=NO_TICKET
outerTrueTrue=ENTER
emptyStatementAttempts=1
blockScopeTotal=7
discount=0
~~~

Dòng `unguardedAlways` xuất hiện **cả khi authorized=false** dù nhìn như nằm trong `if`; `guardedAudit` chỉ xuất hiện khi true. `danglingFalseTrue=NO_BRANCH` khác `outerFalseTrue=NOT_MEMBER` vì `else` thuộc các `if` khác nhau. `emptyStatementAttempts=1` cho thấy `;` tạo thân rỗng.

## 8. Bài tập tự luyện

**Bài 1.** Dự đoán đầu ra với `boolean active = false; if (active) System.out.println("A"); System.out.println("B");`. Thêm ngoặc nhọn để cả A và B chỉ in khi `active` true.

**Bài 2.** Viết `if (member) if (ticket) enter(); else reject();` có ngoặc nhọn để `else` chắc chắn thuộc `if (member)`; nêu khác biệt khi `member=false`.

**Bài 3.** Giải thích ý nghĩa của `if (enabled); doWork();` và sửa lại để `doWork()` chỉ chạy khi `enabled` đúng.

**Bài 4.** Khai báo biến `int amount` trong khối `if`. Thử sử dụng bên ngoài khối và giải thích lỗi scope; viết cách lưu kết quả hợp lệ để dùng về sau.

**Bài 5.** Tạo `if/else` gán một biến ở cả hai nhánh, rồi thử xóa phép gán trong nhánh `else`. Vì sao Java không cho đọc biến cục bộ có thể chưa được gán?

### Ghi nhớ

**Không có `{}`, `if`/`else` chỉ điều khiển đúng một câu lệnh; thụt lề không quyết định luồng chạy.** `else` gắn với `if` gần nhất chưa có `else`, còn dấu `;` thừa có thể khiến thân `if` rỗng. Dùng ngoặc nhọn nhất quán để bảo vệ thao tác, làm rõ phạm vi biến và tránh lỗi khó nhìn thấy khi chỉnh sửa.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="if-statements-004">else gắn với if gần nhất chưa có else</a>

<details>
<summary>Xem chi tiết</summary>

# `else` gắn với `if` gần nhất chưa có `else` — Java 17+

Trong Java, nếu nhiều câu lệnh `if` **lồng nhau mà không có ngoặc nhọn xác định rõ khối**, một `else` sẽ thuộc về **`if` gần nhất ở trước nó mà chưa có `else` tương ứng**. Quy tắc này thường được gọi là **dangling else** (*else lơ lửng*).

~~~java
boolean outer = true;
boolean inner = false;

if (outer)
    if (inner)
        System.out.println("A");
    else
        System.out.println("B");
~~~

Kết quả là **`B`**, vì `else` gắn với **`if (inner)`**, không phải `if (outer)`. **Thụt đầu dòng không quyết định phạm vi câu lệnh trong Java**; cấu trúc cú pháp và dấu `{ }` mới quyết định.

Nếu `outer` là `false`, chương trình bỏ qua toàn bộ câu lệnh `if (inner) ... else ...`, vì đó là phần thân của `if (outer)`. Lúc ấy **không có dòng A/B nào được in**.

## 1. Quy tắc gắn `else` chính xác

Với đoạn sau không có ngoặc nhọn:

~~~java
if (outer)
    if (inner)
        actionA();
    else
        actionB();
~~~

Java hiểu tương đương:

~~~java
if (outer) {
    if (inner) {
        actionA();
    } else {
        actionB();
    }
}
~~~

Hai phương thức `actionA()`, `actionB()` ở hai đoạn trên chỉ là tên minh họa cú pháp; chương trình runnable ở phần tiếp theo tự định nghĩa đầy đủ các phương thức của nó.

### Bảng kết quả

| `outer` | `inner` | Hành vi của đoạn không có ngoặc |
| --- | --- | --- |
| false | false | Không gọi `actionA` hay `actionB` |
| false | true | Không gọi `actionA` hay `actionB` |
| true | false | Gọi `actionB` — `else` của `inner` |
| true | true | Gọi `actionA` |

**Không được** tự diễn giải `else` thành “nhánh ngược lại của điều kiện đầu tiên”. Khi có hai `if` lồng nhau, nó thường thuộc về `if` **bên trong** nếu `if` đó chưa có `else`.

## 2. Chương trình Java 17+ chạy độc lập

Tạo file UTF-8 tên **`DanglingElseDemo.java`**:

~~~java
public class DanglingElseDemo {
    static boolean test(String label, boolean value) {
        System.out.println("check=" + label);
        return value;
    }

    static void withoutBraces(boolean outer, boolean inner) {
        System.out.println("input=" + outer + "," + inner);

        if (outer)
            if (inner)
                System.out.println("branch=inner-true");
            else
                System.out.println("branch=inner-false");

        System.out.println("after");
    }

    static void withBraces(boolean outer, boolean inner) {
        if (outer) {
            if (inner) {
                System.out.println("braced=inner-true");
            } else {
                System.out.println("braced=inner-false");
            }
        } else {
            System.out.println("braced=outer-false");
        }
    }

    static void withTwoElses(boolean outer, boolean inner) {
        if (outer)
            if (inner)
                System.out.println("two=inner-true");
            else
                System.out.println("two=inner-false");
        else
            System.out.println("two=outer-false");
    }

    public static void main(String[] args) {
        System.out.println("== no braces ==");
        withoutBraces(false, false);
        withoutBraces(false, true);
        withoutBraces(true, false);
        withoutBraces(true, true);

        System.out.println("== explicit braces ==");
        withBraces(false, false);
        withBraces(true, false);
        withBraces(true, true);

        System.out.println("== two else keywords ==");
        withTwoElses(false, true);
        withTwoElses(true, false);
        withTwoElses(true, true);

        System.out.println("== skipped inner condition ==");
        if (test("outer", false))
            if (test("inner-skipped", true))
                System.out.println("unexpected=inner-true");
            else
                System.out.println("unexpected=inner-false");
        System.out.println("after-skipped");

        System.out.println("== else-if chain ==");
        int score = 85;
        if (score >= 90) {
            System.out.println("grade=A");
        } else if (score >= 75) {
            System.out.println("grade=B");
        } else {
            System.out.println("grade=C");
        }

        System.out.println("== independent if ==");
        boolean first = true;
        boolean second = true;
        if (first) {
            System.out.println("independent=first");
        }
        if (second) {
            System.out.println("independent=second");
        }

        System.out.println("== chained instead ==");
        if (first) {
            System.out.println("chained=first");
        } else if (second) {
            System.out.println("chained=second");
        }

        System.out.println("done");
    }
}
~~~

Mở PowerShell (Windows) hoặc Bash (Linux/macOS) tại thư mục chứa file; sử dụng **JDK 17 trở lên**:

~~~bash
java -version
javac -version
javac -encoding UTF-8 DanglingElseDemo.java
java DanglingElseDemo
~~~

**Exact expected stdout** của riêng lệnh `java DanglingElseDemo`:

~~~text
== no braces ==
input=false,false
after
input=false,true
after
input=true,false
branch=inner-false
after
input=true,true
branch=inner-true
after
== explicit braces ==
braced=outer-false
braced=inner-false
braced=inner-true
== two else keywords ==
two=outer-false
two=inner-false
two=inner-true
== skipped inner condition ==
check=outer
after-skipped
== else-if chain ==
grade=B
== independent if ==
independent=first
independent=second
== chained instead ==
chained=first
done
~~~

Lệnh `java -version` và `javac -version` hiển thị phiên bản cài trên từng máy, nên không thuộc expected stdout cố định. Ví dụ này chỉ dùng Java chuẩn.

## 3. Dùng ngoặc nhọn để gắn `else` với `if` ngoài

Nếu muốn `else` chạy khi `outer` false thì **phải đóng phần thân của `if (outer)` trước `else`**:

~~~java
if (outer) {
    if (inner) {
        System.out.println("inner is true");
    }
} else {
    System.out.println("outer is false");
}
~~~

Trong trường hợp này, `else` **thuộc `if (outer)`**, vì `if (inner)` đã nằm trong khối ngoặc của `outer`, và khối đó kết thúc trước `else`.

So sánh trực tiếp:

| Cách viết | `else` gắn với | Khi `outer=false` |
| --- | --- | --- |
| `if (outer) if (inner) A; else B;` | `if (inner)` | Không chạy A/B |
| `if (outer) { if (inner) A; } else B;` | `if (outer)` | Chạy B |
| `if (outer) { if (inner) A; else B; }` | `if (inner)` | Không chạy A/B |

`A;` và `B;` trong bảng là ký hiệu cho một câu lệnh hợp lệ, không phải các biến có sẵn của Java. Cần thay chúng bằng câu lệnh thực tế khi sao chép để chạy.

**Nguyên tắc:** Viết dấu ngoặc nhọn cho từng nhánh ngay cả khi chỉ có một câu lệnh. Việc này khiến người đọc và trình biên dịch hiểu cùng một cấu trúc, tránh lỗi khi thêm mã sau này.

## 4. Nếu có hai `else` thì sao?

Có thể có một `else` cho `if` bên trong **và** một `else` cho `if` bên ngoài:

~~~java
if (outer)
    if (inner)
        System.out.println("inner=true");
    else
        System.out.println("inner=false");
else
    System.out.println("outer=false");
~~~

Cách viết này hợp lệ. `else` thứ nhất thuộc `if (inner)`; `else` thứ hai thuộc `if (outer)`. Nhưng khi có nhiều mức lồng, cách không ngoặc dễ làm người đọc nhầm; nên viết lại:

~~~java
if (outer) {
    if (inner) {
        System.out.println("inner=true");
    } else {
        System.out.println("inner=false");
    }
} else {
    System.out.println("outer=false");
}
~~~

| `outer` | `inner` | Nhánh được chọn |
| --- | --- | --- |
| false | bất kỳ | `outer=false` |
| true | false | `inner=false` |
| true | true | `inner=true` |

Đây là **một cây quyết định lồng nhau** chứ không phải ba điều kiện độc lập chạy liên tục.

## 5. `else if` là một `else` theo sau bởi `if`

Java không có từ khóa riêng tên `elseif`. Cú pháp:

~~~java
if (score >= 90) {
    System.out.println("A");
} else if (score >= 75) {
    System.out.println("B");
} else {
    System.out.println("C");
}
~~~

Có thể hiểu cấu trúc như:

~~~java
if (score >= 90) {
    System.out.println("A");
} else {
    if (score >= 75) {
        System.out.println("B");
    } else {
        System.out.println("C");
    }
}
~~~

Điều này giải thích vì sao `else if` thực chất kết hợp `else` với một câu `if` khác. Với chuỗi này, các điều kiện được đánh giá từ trên xuống; gặp nhánh true đầu tiên thì những nhánh sau bị bỏ qua.

**Khác `if` độc lập:** Hai câu `if` đặt liên tiếp có thể cùng chạy. Một chuỗi `if / else if / else` sẽ chỉ chạy tối đa một nhánh trong một lần đi qua chuỗi, nếu điều kiện không ném ngoại lệ.

## 6. Thụt đầu dòng không thay thế cú pháp

Đây là ví dụ dễ gây hiểu nhầm:

~~~java
boolean outer = false;
boolean inner = false;

if (outer)
    if (inner)
        System.out.println("A");
else
    System.out.println("B");
~~~

Mặc dù `else` trông giống thụt dòng cùng cấp với `if (outer)`, nó vẫn **thuộc `if (inner)`**. Khi `outer` false, không in A hoặc B. Thụt đầu dòng chỉ là hình thức trình bày mã nguồn; Java không dựa vào số khoảng trắng để ghép `else`.

Hãy sửa thành cấu trúc có ngoặc:

~~~java
if (outer) {
    if (inner) {
        System.out.println("A");
    }
} else {
    System.out.println("B");
}
~~~

Bây giờ `B` được in khi `outer` false. Hai đoạn **không tương đương** dù có thể được trình bày với thụt đầu dòng khá giống nhau.

## 7. `else` gần nhất chưa có `else` và những giới hạn cú pháp

Quy tắc “gần nhất” áp dụng cho `if` chưa có `else` trong **cấu trúc câu lệnh hợp lệ**; nó **không có nghĩa** có thể nối `else` vào bất kỳ câu `if` nào xuất hiện trước đó dù đã đóng khối hoặc tách ra bằng câu lệnh khác.

Ví dụ:

~~~java
int n = 2;

if (n > 0) {
    System.out.println("positive");
}
// else phải đặt ngay sau câu if ở trên nếu muốn tạo nhánh của nó.

System.out.println("between");
// else { System.out.println("non-positive"); } // Lỗi biên dịch.
~~~

Câu `else` không thể đứng riêng sau `System.out.println("between")` vì không có một câu `if` hợp lệ tương ứng tại vị trí cú pháp đó.

Một bẫy khác là dấu chấm phẩy ngay sau điều kiện:

~~~java
boolean ready = true;

if (ready); // Đây là if có thân là câu lệnh rỗng.
{
    System.out.println("always");
}
~~~

Khối có `System.out.println("always")` là **khối độc lập**, không phải phần thân của `if`. Vì thế hãy tránh thêm `;` sau `if (...)` và luôn xem lại vị trí đóng mở ngoặc.

## 8. Khi nào cần quan tâm thứ tự đánh giá điều kiện?

Xét:

~~~java
if (outer)
    if (inner)
        doWork();
    else
        doFallback();
~~~

Nếu `outer` false, biểu thức `inner` **không được đánh giá**, `doWork()` và `doFallback()` đều không chạy.

Điều này đáng chú ý nếu `inner` là lời gọi phương thức có log, thay đổi biến hoặc có thể ném exception. Ví dụ trong chương trình `DanglingElseDemo`:

~~~java
if (test("outer", false))
    if (test("inner-skipped", true))
        System.out.println("unexpected=inner-true");
    else
        System.out.println("unexpected=inner-false");
~~~

Chỉ xuất hiện `check=outer`, không có `check=inner-skipped`. Không phải Java “quên” đánh giá điều kiện trong; đó là quy tắc **điều khiển luồng của `if` ngoài**.

Nên tránh dựa vào tác dụng phụ ẩn trong điều kiện để thực hiện thao tác bắt buộc; hãy tách thao tác đó thành câu lệnh rõ ràng nếu nó phải xảy ra dù nhánh nào được chọn.

## 9. Những lỗi thường gặp

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “`else` gắn với `if` có cùng mức thụt dòng.” | Gắn với `if` gần nhất chưa có `else` theo **cú pháp** |
| “`else` luôn thuộc `if` ngoài cùng.” | Sai khi có `if` bên trong chưa có `else` |
| “Nếu `outer` false, `else` của `inner` vẫn chạy.” | Không; toàn bộ `if (inner)...else...` bị bỏ qua |
| “Thêm ngoặc nhọn chỉ để đẹp.” | Có thể **thay đổi quan hệ gắn `else`** và hành vi |
| “`else if` là một từ khóa riêng.” | Là `else` theo sau bởi câu `if` |
| “Hai `if` liên tiếp giống chuỗi `else if`.” | Hai `if` độc lập có thể cùng chạy |
| “Có thể đặt một `else` sau một câu lệnh bất kỳ.” | `else` phải theo đúng cấu trúc `if` |
| “Thêm `;` ngay sau `if (...)` vô hại.” | Tạo thân `if` rỗng, dễ làm mã chạy khác ý định |
| “Một `else` có thể thuộc hai `if` cùng lúc.” | Mỗi `else` chỉ thuộc **một** `if` hợp lệ |

## 10. Bài tập tự kiểm tra

1. Với `outer=true`, `inner=false` và đoạn `if (outer) if (inner) A; else B;`, câu lệnh nào được chạy?  
   *Đáp án:* **B**, thuộc `else` của `inner`.
2. Với `outer=false`, `inner=false` và cùng đoạn trên, có chạy `B` không?  
   *Đáp án:* **Không**; toàn bộ câu `if` bên trong bị bỏ qua.
3. Thụt `else` về cùng mức với `if (outer)` có đổi cách Java ghép `else` không?  
   *Đáp án:* **Không**.
4. Muốn `else` thuộc `if (outer)`, cách chắc chắn nhất là gì?  
   *Đáp án:* Đặt `{ }` quanh phần thân của `if (outer)`, đóng khối trước `else`.
5. Đoạn lồng có hai `else`, `else` đầu gắn với câu `if` nào?  
   *Đáp án:* `if` bên trong chưa có `else`; `else` tiếp theo thuộc `if` ngoài.
6. `else if` có được xem như một từ khóa riêng trong Java không?  
   *Đáp án:* Không; là `else` + một câu `if`.
7. Vì sao đoạn `if (outer) if (inner) A; else B;` không gọi phép kiểm tra `inner` khi `outer=false`?  
   *Đáp án:* Thân `if (outer)` đã bị bỏ qua.
8. `if (ready);` gây vấn đề gì?  
   *Đáp án:* `if` có thân rỗng; câu lệnh hoặc khối tiếp theo không được bảo vệ bởi điều kiện.

## Checklist

- [ ] Hiểu `else` gắn với **`if` gần nhất chưa có `else`** trong cấu trúc hợp lệ.
- [ ] Nhận biết `dangling else` khi viết `if` lồng nhau không ngoặc.
- [ ] Phân biệt `else` của `if` trong với `else` của `if` ngoài.
- [ ] Biết thụt đầu dòng **không quyết định** liên kết cú pháp.
- [ ] Luôn ưu tiên `{ }` để thể hiện rõ các nhánh.
- [ ] Hiểu `else if` là `else` tiếp theo bởi một câu `if`.
- [ ] Phân biệt chuỗi `else if` với nhiều câu `if` độc lập.
- [ ] Không vô tình tạo thân `if` rỗng bằng dấu `;`.

**Kết luận:** Quy tắc **dangling else** của Java đơn giản nhưng dễ gây lỗi: **`else` thuộc `if` gần nhất chưa có `else`**, bất kể cách thụt đầu dòng. Dùng ngoặc nhọn nhất quán để làm rõ ý định, tránh thay đổi hành vi khi thêm hoặc chỉnh sửa các nhánh.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="if-statements-005">Phân biệt nhiều if độc lập với một chuỗi loại trừ</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt nhiều `if` độc lập với một chuỗi loại trừ trong Java

> **Java core — language-basics / Rẽ nhánh if**  
> **Yêu cầu:** Java 17+, một file Java độc lập, chỉ dùng thư viện chuẩn.  
> **Mục tiêu:** Phân biệt **nhiều câu lệnh `if` độc lập** với **một chuỗi `if / else if / else`**; dự đoán số nhánh được thực hiện, thứ tự kiểm tra điều kiện và lựa chọn cú pháp đúng theo nghiệp vụ.

## 1. Hai cấu trúc nhìn giống nhưng khác hành vi

**Nhiều `if` độc lập** là các câu lệnh riêng biệt:

~~~java
int score = 95;

if (score >= 60) {
    System.out.println("PASS");
}
if (score >= 80) {
    System.out.println("MERIT");
}
if (score >= 90) {
    System.out.println("HONOR");
}
// In cả ba dòng: PASS, MERIT, HONOR.
~~~

Vì cả ba điều kiện đều đúng, **cả ba khối được chạy**. Trong luồng thực thi bình thường, Java vẫn kiểm tra câu `if` tiếp theo sau khi xử lý xong câu trước.

**Một chuỗi loại trừ** dùng `if / else if / else`:

~~~java
int score = 95;

if (score >= 90) {
    System.out.println("A");
} else if (score >= 80) {
    System.out.println("B");
} else if (score >= 60) {
    System.out.println("C");
} else {
    System.out.println("F");
}
// Chỉ in: A.
~~~

Ngay khi `score >= 90` đúng, Java chọn nhánh `"A"` và **bỏ qua toàn bộ các điều kiện/nhánh `else if` phía sau** trong cùng chuỗi. Không phải ba phép kiểm tra đều chạy rồi Java mới chọn kết quả.

## 2. Bảng so sánh hành vi

| Khía cạnh | Nhiều `if` độc lập | `if / else if / else` |
| --- | --- | --- |
| Số nhánh có thể chạy | **0, 1 hoặc nhiều** | **Tối đa 1** |
| Khi điều kiện đầu đúng | Vẫn tới những `if` tiếp theo nếu luồng không bị ngắt | Không đánh giá những `else if` sau |
| Điều kiện chồng lấp | Có thể kích hoạt nhiều hành động | Chọn điều kiện đúng **đầu tiên** |
| Khi không điều kiện nào đúng | Không chạy nhánh nào (trừ khi có mã khác) | Chạy `else` nếu có |
| Mục đích điển hình | Các quy tắc/hành động **không loại trừ nhau** | Các kết quả/nhóm **chỉ chọn một** |

**Điểm tinh tế:** Không nên nói mọi `if` độc lập *chắc chắn* đều được đánh giá: nhánh trước có thể `return`, ném ngoại lệ, `break` hoặc `continue` khiến luồng không tới nhánh tiếp theo. Bảng mô tả trường hợp luồng tiếp tục bình thường.

Một chuỗi `else if` có thể **không có `else`**; khi mọi điều kiện sai, chuỗi không chạy nhánh nào.

## 3. Khi nào dùng `if` độc lập?

Dùng các `if` độc lập khi nhiều quy tắc **có thể đồng thời đúng** và mỗi quy tắc cần hành động riêng.

~~~java
boolean sendEmail = true;
boolean saveLog = true;

if (sendEmail) {
    System.out.println("SEND_EMAIL");
}
if (saveLog) {
    System.out.println("SAVE_LOG");
}
// In cả SEND_EMAIL và SAVE_LOG.
~~~

Nếu đổi thành `if (sendEmail) ... else if (saveLog) ...`, việc lưu log sẽ bị **bỏ qua** khi gửi email được chọn, dù `saveLog` cũng true. Đó là thay đổi logic, không chỉ thay đổi cách viết.

Ví dụ thực tế: áp dụng nhiều cảnh báo độc lập, bật nhiều tính năng, thực hiện nhiều phép kiểm tra chất lượng, hoặc thu thập nhiều lỗi validation thay vì dừng ở lỗi đầu tiên.

Ngược lại, nếu chỉ chọn **một nhãn mức độ** hoặc **một tuyến xử lý ưu tiên**, chuỗi loại trừ phù hợp hơn.

## 4. Khi nào dùng chuỗi `if / else if / else`?

Khi các nhánh cùng quyết định **một kết quả**, chuỗi loại trừ bảo đảm chỉ một nhánh được chọn.

Ví dụ phân loại điểm từ cao xuống thấp:

~~~java
static String grade(int score) {
    if (score >= 90) {
        return "A";
    } else if (score >= 80) {
        return "B";
    } else if (score >= 60) {
        return "C";
    } else {
        return "F";
    }
}
~~~

**Thứ tự điều kiện quan trọng.** Cách viết sai thứ tự dưới đây vẫn biên dịch:

~~~java
static String wrongGrade(int score) {
    if (score >= 60) {
        return "C";
    } else if (score >= 80) {
        return "B"; // Không thể tới với điểm >= 80.
    } else if (score >= 90) {
        return "A"; // Không thể tới với điểm >= 90.
    }
    return "F";
}
~~~

Vì điểm `95` thỏa `score >= 60` ngay nhánh đầu nên kết quả là `"C"`, không phải `"A"`. Để xử lý mức ưu tiên, kiểm tra ngưỡng **cao trước thấp** hoặc thiết kế những điều kiện thực sự tách biệt.

## 5. Điều kiện chồng lấp và nhánh mặc định

Xét `int score = 85` với ba điều kiện `score >= 60`, `score >= 80`, `score >= 90`:

| Điều kiện | Với `score = 85` | `if` độc lập | Chuỗi loại trừ theo thứ tự 90 → 80 → 60 |
| --- | --- | --- | --- |
| `score >= 90` | false | Không chạy | Xét rồi bỏ qua |
| `score >= 80` | true | Chạy | **Chọn và dừng xét nhánh sau** |
| `score >= 60` | true | Chạy | **Không xét** |

Dùng nhiều `if` để **gắn nhiều thuộc tính tích lũy** (PASS, MERIT). Dùng `else if` để **chọn đúng một loại** (A, B, C, F).

Ngay cả khi các điều kiện không chồng lấp, như `x < 0` và `x > 0`, các `if` độc lập *có thể* chỉ chạy tối đa một nhánh do chính các điều kiện loại trừ nhau; **cú pháp `if` độc lập không tự bảo đảm điều đó**.

Với các điều kiện không bao phủ mọi khả năng (ví dụ `x < 0` và `x > 0` bỏ sót số 0), dùng `else` khi muốn có kết quả mặc định rõ ràng.

## 6. Điều kiện sau có được đánh giá không?

Khác biệt không chỉ là **khối lệnh nào chạy** mà còn là **biểu thức điều kiện nào thực sự được đánh giá**.

~~~java
StringBuilder independentTrace = new StringBuilder();

if (check(independentTrace, "A", true)) {
    independentTrace.append("1");
}
if (check(independentTrace, "B", true)) {
    independentTrace.append("2");
}
// Dấu vết: A1B2 — cả A và B được kiểm tra và chạy.
~~~

Còn trong chuỗi loại trừ:

~~~java
StringBuilder exclusiveTrace = new StringBuilder();

if (check(exclusiveTrace, "A", true)) {
    exclusiveTrace.append("1");
} else if (check(exclusiveTrace, "B", true)) {
    exclusiveTrace.append("2");
}
// Dấu vết: A1 — B không được gọi, không có tác dụng phụ B.
~~~

Trong hai đoạn trên, `check(log, label, value)` là hàm minh họa có trong chương trình runnable phía dưới.

Điều này quan trọng nếu điều kiện chứa lời gọi hàm, truy cập dữ liệu, tăng biến hoặc có thể ném ngoại lệ. **Không chuyển các `if` độc lập thành `else if` chỉ để “tối ưu” số lần kiểm tra** nếu nghiệp vụ thực sự cần đánh giá từng quy tắc.

## 7. Lưu ý về `else` và các điều kiện phụ thuộc

`else` luôn gắn với **`if` gần nhất chưa có `else`** khi không có ngoặc nhọn để đổi cấu trúc.

~~~java
if (isMember) {
    System.out.println("Member");
}
if (isActive) {
    System.out.println("Active");
} else {
    System.out.println("Inactive");
}
~~~

Ở đây `else` thuộc **`if (isActive)`**, không thuộc `if (isMember)` hoặc “cả hai `if`”. Hai `if` vẫn độc lập.

Trường hợp có điều kiện phụ thuộc:

~~~java
String name = null;

// SAI nếu nghĩ if thứ nhất ngăn if thứ hai:
// if (name == null) System.out.println("Missing");
// if (name.isBlank()) System.out.println("Blank"); // Có thể NPE.

// ĐÚNG: các trường hợp loại trừ nhau và có guard.
if (name == null) {
    System.out.println("Missing");
} else if (name.isBlank()) {
    System.out.println("Blank");
} else {
    System.out.println("Present");
}
~~~

Với chuỗi này, khi `name == null`, Java không đánh giá `name.isBlank()`. Khi dùng các `if` độc lập, bạn có thể thay bằng `name != null && name.isBlank()` để bảo vệ vế phụ thuộc, nếu vẫn cần thực hiện từng kiểm tra độc lập.

**Luôn dùng ngoặc nhọn `{}`** để làm rõ nhánh, đặc biệt khi sửa điều kiện hoặc bổ sung hành động.

## 8. Chương trình Java 17+ chạy độc lập

Tạo file **IndependentIfVsElseIfDemo.java**:

~~~java
public final class IndependentIfVsElseIfDemo {
    private static String tags(int score) {
        StringBuilder result = new StringBuilder();
        if (score >= 60) {
            result.append("PASS");
        }
        if (score >= 80) {
            result.append("+MERIT");
        }
        if (score >= 90) {
            result.append("+HONOR");
        }
        return result.length() == 0 ? "NONE" : result.toString();
    }

    private static String grade(int score) {
        if (score >= 90) {
            return "A";
        } else if (score >= 80) {
            return "B";
        } else if (score >= 60) {
            return "C";
        } else {
            return "F";
        }
    }

    private static String wrongGrade(int score) {
        if (score >= 60) {
            return "C";
        } else if (score >= 80) {
            return "B";
        } else if (score >= 90) {
            return "A";
        }
        return "F";
    }

    private static boolean check(
            StringBuilder log, String label, boolean value) {
        log.append(label);
        return value;
    }

    private static String nameStatus(String name) {
        if (name == null) {
            return "MISSING";
        } else if (name.isBlank()) {
            return "BLANK";
        }
        return "PRESENT";
    }

    public static void main(String[] args) {
        for (int score : new int[] {55, 60, 79, 80, 89, 90, 95}) {
            System.out.println("score=" + score
                    + ",tags=" + tags(score)
                    + ",grade=" + grade(score));
        }

        System.out.println("wrongOrdered95=" + wrongGrade(95));

        boolean sendEmail = true;
        boolean saveLog = true;
        StringBuilder independentActions = new StringBuilder();
        if (sendEmail) {
            independentActions.append("SEND_EMAIL");
        }
        if (saveLog) {
            if (independentActions.length() > 0) {
                independentActions.append("+");
            }
            independentActions.append("SAVE_LOG");
        }
        System.out.println("independentActions=" + independentActions);

        String exclusiveAction;
        if (sendEmail) {
            exclusiveAction = "SEND_EMAIL";
        } else if (saveLog) {
            exclusiveAction = "SAVE_LOG";
        } else {
            exclusiveAction = "NONE";
        }
        System.out.println("exclusiveAction=" + exclusiveAction);

        StringBuilder independentTrace = new StringBuilder();
        if (check(independentTrace, "A", true)) {
            independentTrace.append("1");
        }
        if (check(independentTrace, "B", true)) {
            independentTrace.append("2");
        }
        System.out.println("independentTrace=" + independentTrace);

        StringBuilder exclusiveTrace = new StringBuilder();
        if (check(exclusiveTrace, "A", true)) {
            exclusiveTrace.append("1");
        } else if (check(exclusiveTrace, "B", true)) {
            exclusiveTrace.append("2");
        }
        System.out.println("exclusiveTrace=" + exclusiveTrace);

        System.out.println("guardNull=" + nameStatus(null));
        System.out.println("guardBlank=" + nameStatus("  "));
        System.out.println("guardText=" + nameStatus("Java"));

        int value = 0;
        String independentNoMatch = "NONE";
        if (value < 0) {
            independentNoMatch = "NEGATIVE";
        }
        if (value > 0) {
            independentNoMatch = "POSITIVE";
        }
        System.out.println("independentNoMatch=" + independentNoMatch);

        String exclusiveFallback;
        if (value < 0) {
            exclusiveFallback = "NEGATIVE";
        } else if (value > 0) {
            exclusiveFallback = "POSITIVE";
        } else {
            exclusiveFallback = "ZERO";
        }
        System.out.println("exclusiveFallback=" + exclusiveFallback);
    }
}
~~~

Mở terminal tại thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 IndependentIfVsElseIfDemo.java
java IndependentIfVsElseIfDemo
~~~

**Expected stdout chính xác:**

~~~text
score=55,tags=NONE,grade=F
score=60,tags=PASS,grade=C
score=79,tags=PASS,grade=C
score=80,tags=PASS+MERIT,grade=B
score=89,tags=PASS+MERIT,grade=B
score=90,tags=PASS+MERIT+HONOR,grade=A
score=95,tags=PASS+MERIT+HONOR,grade=A
wrongOrdered95=C
independentActions=SEND_EMAIL+SAVE_LOG
exclusiveAction=SEND_EMAIL
independentTrace=A1B2
exclusiveTrace=A1
guardNull=MISSING
guardBlank=BLANK
guardText=PRESENT
independentNoMatch=NONE
exclusiveFallback=ZERO
~~~

Bảy dòng điểm chứng minh `if` độc lập tích lũy nhiều tag trong khi chuỗi loại trừ chọn một grade. `wrongOrdered95=C` phơi bày sai thứ tự ưu tiên. Hai dòng `Trace` cho thấy `else if` không đánh giá B sau khi A đúng. `exclusiveFallback=ZERO` chứng minh vai trò của nhánh `else`.

## 9. Checklist và bài tập tự luyện

Trước khi quyết định dùng cấu trúc nào, hãy hỏi:

- Một đối tượng có thể thỏa **nhiều quy tắc cần cùng chạy**, hay chỉ được **chọn một kết quả**?
- Các điều kiện có chồng lấp không? Nếu có, chọn **tất cả** hay ưu tiên **điều kiện đầu tiên**?
- Thứ tự xét điều kiện có làm thay đổi kết quả hoặc tác dụng phụ không?
- Khi mọi điều kiện sai, có cần nhánh `else` làm mặc định không?
- Có điều kiện phụ thuộc vào guard (null, chỉ số mảng, trạng thái...) không?

**Bài 1.** Với `int score = 90`, dự đoán có bao nhiêu `if` độc lập đúng cho các ngưỡng 60, 80, 90. Chuỗi `if/else if/else` từ cao xuống sẽ chạy bao nhiêu nhánh?

**Bài 2.** Cho hai cờ `sendEmail=true` và `saveLog=true`. Giải thích vì sao đổi hai `if` độc lập thành `if/else if` khiến một hành động bị bỏ qua.

**Bài 3.** Đổi chuỗi xét ngưỡng điểm theo thứ tự `>=60`, `>=80`, `>=90` thành đúng thứ tự và kiểm tra với điểm 95.

**Bài 4.** Viết xử lý `String name` có ba trạng thái null, blank, có nội dung bằng `if/else if/else` sao cho không ném `NullPointerException`.

**Bài 5.** Viết hai phương thức: một phương thức thu thập **tất cả** cảnh báo thỏa điều kiện, một phương thức trả **cảnh báo ưu tiên cao nhất**. Cấu trúc nào phù hợp cho từng phương thức?

### Ghi nhớ

**Nhiều `if` độc lập có thể thực hiện nhiều nhánh; `if / else if / else` là một chuỗi chỉ chọn tối đa một nhánh — nhánh đúng đầu tiên.** Với nhiều `if`, những điều kiện tiếp theo vẫn được xét nếu luồng tiếp tục bình thường; với `else if`, điều kiện sau nhánh đã chọn không được đánh giá. Chọn cú pháp theo **ngữ nghĩa nghiệp vụ**, không chỉ theo số dòng mã.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="if-statements-006">Điều kiện biên và thứ tự xét các nhánh</a>

<details>
<summary>Xem chi tiết</summary>

# Điều kiện biên và thứ tự xét các nhánh — Java 17+

## 1. Mục tiêu

Trong Java, `if`, `else if` và `else` giúp chọn nhánh theo điều kiện `boolean`. Hai nguồn lỗi phổ biến là:

1. **Sai điều kiện biên** (*boundary condition*): viết `>` thay vì `>=`, bỏ sót điểm đầu/cuối, hoặc chấp nhận dữ liệu ngoài miền.
2. **Sai thứ tự nhánh**: một điều kiện rộng đứng trước đã nhận mất những giá trị lẽ ra phải thuộc nhánh cụ thể phía sau.

~~~java
int score = 90;
if (score >= 90) {
    System.out.println("A");
} else if (score >= 70) {
    System.out.println("B");
} else {
    System.out.println("C");
}
~~~

**Expected stdout:**

~~~text
A
~~~

Khi `score == 90`, nhánh `score >= 90` là true và chạy trước; các nhánh `else if` và `else` **không được đánh giá/thực hiện**.

Trong bài, ta học cách đặc tả miền, sắp điều kiện theo thứ tự rõ ràng, xác thực giá trị, tránh nhánh che khuất và xây dựng kiểm thử tại biên bằng Java 17+.

## 2. Thứ tự xét nhánh là một phần của điều kiện biên

Một chuỗi `if / else if / else` duyệt điều kiện **từ trên xuống** và dừng ở nhánh đúng đầu tiên. Điều đó khiến thứ tự nhánh và toán tử so sánh **phối hợp** quyết định khoảng giá trị mà mỗi nhánh thực sự nhận:

~~~java
if (score >= 90) {
    grade = "A";
} else if (score >= 70) {
    grade = "B"; // Chỉ nhận 70..89 khi score là int hợp lệ.
} else {
    grade = "C"; // Nhận dưới 70 trong miền hợp lệ.
}
~~~

Không phải mọi biểu thức được đánh giá: với `score=95`, điều kiện nhánh B không được xét. Nếu đảo điều kiện `>=70` lên trước, nhánh A có thể trở nên không thể chọn dù cú pháp vẫn hợp lệ.

Bài này tập trung **miền, điểm biên và nhánh bị che khuất**. Chi tiết về độc lập hay loại trừ khi có nhiều `if` đã thuộc bài trước; vì vậy khi review một chuỗi, hãy ưu tiên vẽ **khoảng thực tế** của từng nhánh thay vì chỉ đếm bao nhiêu nhánh chạy.


## 3. Biên đóng và mở: `<`, `<=`, `>`, `>=`

Trước khi viết code, hãy xác định **giá trị bằng đúng biên có được chấp nhận hay không**.

| Mô tả | Điều kiện Java | Giá trị đúng tại biên |
|---|---|---|
| Lớn hơn 18 | `age > 18` | 18 bị loại |
| Từ đủ 18 tuổi | `age >= 18` | 18 được nhận |
| Dưới 65 | `age < 65` | 65 bị loại |
| Tối đa 65 | `age <= 65` | 65 được nhận |
| Khoảng đóng 18 đến 65 | `age >= 18 && age <= 65` | Nhận 18 và 65 |
| Khoảng nửa mở 0 đến `length` | `index >= 0 && index < length` | Nhận 0, loại `length` |

Ví dụ:

~~~java
int age = 18;
boolean adult = age >= 18;
System.out.println(adult); // true
~~~

Java **không cho viết** `18 <= age <= 65`. Hãy dùng hai biểu thức boolean nối bằng `&&`:

~~~java
boolean eligible = age >= 18 && age <= 65;
~~~

Các giá trị nằm ngay ngoài miền cũng phải được kiểm tra; chúng thường phát hiện lỗi *off-by-one*.

## 4. Sắp nhánh theo thứ tự từ điều kiện cụ thể đến bao quát

Giả sử yêu cầu:

- Từ 65 tuổi: `SENIOR`.
- Từ 18 đến dưới 65: `ADULT`.
- Dưới 18: `MINOR`.

**Thứ tự sai:**

~~~java
if (age >= 18) {
    group = "ADULT";
} else if (age >= 65) {
    group = "SENIOR"; // Không bao giờ được chọn cho tuổi >= 65
} else {
    group = "MINOR";
}
~~~

Với `age = 70`, nhánh đầu đã đúng. Điều kiện `age >= 65` **bị che khuất về logic** mặc dù vẫn có cú pháp hợp lệ. Compiler không bắt buộc báo lỗi cho trường hợp này.

**Thứ tự đúng:**

~~~java
if (age >= 65) {
    group = "SENIOR";
} else if (age >= 18) {
    group = "ADULT";
} else {
    group = "MINOR";
}
~~~

Bởi vì khi tới nhánh thứ hai, `age >= 65` đã được loại, nên `age >= 18` ở đó ngầm chỉ khoảng `[18, 65)`.

Nếu chính sách phân loại có nhiều điều kiện giao nhau nhưng **ưu tiên khác nhau** (ví dụ trạng thái khẩn cấp phải thắng trạng thái thường), hãy ghi rõ mức ưu tiên đó trước rồi mới sắp nhánh, thay vì áp dụng một quy tắc sắp xếp máy móc.

## 5. Đặc tả miền cho hệ thống phân loại điểm

Giả sử `score` phải từ **0 đến 100** và có bốn mức:

| Điểm | Nhãn | Điều kiện |
|---|---|---|
| Ngoài `[0,100]` | `INVALID` | `score < 0 \|\| score > 100` |
| `[90,100]` | `A` | `score >= 90`, sau khi đã validate |
| `[70,90)` | `B` | `score >= 70`, sau khi A không đúng |
| `[50,70)` | `C` | `score >= 50`, sau khi A/B không đúng |
| `[0,50)` | `D` | `else` |

~~~java
static String grade(int score) {
    if (score < 0 || score > 100) {
        return "INVALID";
    }
    if (score >= 90) {
        return "A";
    } else if (score >= 70) {
        return "B";
    } else if (score >= 50) {
        return "C";
    } else {
        return "D";
    }
}
~~~

**Đặc điểm đáng chú ý:**

- Kiểm tra miền hợp lệ **trước khi phân loại**, nên `score = 101` không bị nhận nhầm là A.
- Kiểm tra ngưỡng giảm dần `90 → 70 → 50`; ngưỡng lớn không bị nhánh rộng che khuất.
- `score = 90` là A, `score = 70` là B, `score = 50` là C.
- `score = 0` là D, `score = 100` là A.
- Không cần lặp lại `score < 90` trong nhánh B vì nhánh A đã thất bại.

Với dữ liệu ngoài miền, một hệ thống khác có thể **ném `IllegalArgumentException`** thay vì trả `INVALID`. Chọn theo hợp đồng API; không ngầm “clamp” về miền 0–100 nếu người dùng không yêu cầu.

## 6. Bảng test biên — mỗi ngưỡng nên thử ba điểm

Với điểm nguyên, tối thiểu thử ngay dưới, đúng bằng và ngay trên ngưỡng, cộng thêm biên toàn miền:

| `score` | Expected | Mục đích |
|---:|---|---|
| -1 | `INVALID` | Dưới miền |
| 0 | `D` | Biên dưới hợp lệ |
| 49 | `D` | Ngay dưới ngưỡng 50 |
| 50 | `C` | Đúng ngưỡng 50 |
| 51 | `C` | Ngay trên ngưỡng 50 |
| 69 | `C` | Ngay dưới ngưỡng 70 |
| 70 | `B` | Đúng ngưỡng 70 |
| 71 | `B` | Ngay trên ngưỡng 70 |
| 89 | `B` | Ngay dưới ngưỡng 90 |
| 90 | `A` | Đúng ngưỡng 90 |
| 91 | `A` | Ngay trên ngưỡng 90 |
| 100 | `A` | Biên trên hợp lệ |
| 101 | `INVALID` | Trên miền |

Điểm mấu chốt: chỉ thử một giá trị “giữa khoảng” như 75 là **chưa đủ** để phát hiện việc dùng sai `>`/`>=`.

Nếu kiểu dữ liệu là `long` và biên gần `Long.MIN_VALUE`/`Long.MAX_VALUE`, **không tính máy móc `min - 1` hoặc `max + 1`** vì có thể overflow. Hãy dùng các giá trị kiểm thử có biểu diễn hợp lệ trong kiểu.

## 7. Chú ý khi điều kiện giao nhau hoặc không phủ hết miền

Với hai điều kiện:

~~~java
if (value >= 10) {
    label = "TEN_OR_MORE";
} else if (value >= 20) {
    label = "TWENTY_OR_MORE"; // Bị che khuất
}
~~~

Nhánh thứ hai không nhận giá trị nào vì mọi số `>=20` đã thuộc nhánh đầu.

Ngược lại, nếu thiếu `else` hoặc một ngưỡng:

~~~java
String label = "UNKNOWN";
if (value < 0) {
    label = "NEGATIVE";
} else if (value > 0) {
    label = "POSITIVE";
}
// value == 0 => giữ UNKNOWN
~~~

Đây có thể là **chủ ý** nếu `UNKNOWN` là trạng thái hợp lệ, hoặc là bug nếu phải phân loại mọi số. Hãy lập bảng miền và xác định có cần bao phủ đầy đủ (*exhaustive*) không.

Đối với những điều kiện không phải ngưỡng đơn điệu (ví dụ `isAdmin`, `isSuspended`, `age >= 18`), hãy **đặt ưu tiên quy tắc rõ ràng**, thay vì mặc định xếp theo độ lớn.

## 8. Ngoặc nhọn giúp điều kiện biên thuộc đúng nhánh

Với `if` lồng nhau, một `else` không ngoặc có thể gắn với `if` gần nhất còn thiếu `else`, dù người đọc tưởng đó là nhánh ngoài. Điều này có thể làm **sai điều kiện kiểm tra biên** vì nhánh dự phòng thuộc một điều kiện khác:

~~~java
if (validRange) {
    if (score >= 90) {
        System.out.println("A");
    }
} else {
    System.out.println("INVALID");
}
~~~

Ở đây `else` thuộc `if (validRange)`, nên mọi điểm **ngoài miền** được đánh dấu INVALID. Nếu muốn báo mức thấp cho điểm hợp lệ, cần thêm một `else` **bên trong** khối `validRange` hoặc dùng chuỗi phân loại đã kiểm tra miền như `grade(int)` trong chương trình chính.

Không để sự nhập nhằng ngoặc nhọn làm đảo ý nghĩa bảng biên; quy tắc `dangling else` và các biến thể cú pháp được trình bày toàn diện trong bài riêng.


## 9. Thứ tự đánh giá có thể ảnh hưởng side effect và exception

~~~java
static boolean check(String name, boolean answer) {
    System.out.println("checked " + name);
    return answer;
}
~~~

~~~java
if (check("first", false)) {
    System.out.println("first branch");
} else if (check("second", true)) {
    System.out.println("second branch");
} else if (check("third", true)) {
    System.out.println("third branch");
}
~~~

**Expected stdout:**

~~~text
checked first
checked second
second branch
~~~

Nhánh `third` **không được kiểm tra** vì nhánh thứ hai đã true. Do đó, đổi thứ tự các nhánh không phải lúc nào cũng chỉ đổi nhãn kết quả: nó có thể đổi **method nào được gọi, side effect nào xảy ra hoặc exception nào xuất hiện**.

Khuyến nghị: các điều kiện phân loại thông thường nên **thuần túy, không cập nhật trạng thái**. Nếu cần log hoặc xác thực nhiều trường, tách thao tác thành bước rõ ràng.

### Guard với `&&` và `||`

~~~java
int[] numbers = {10, 20, 30};
int index = 2;

if (numbers != null
        && index >= 0
        && index < numbers.length) {
    System.out.println(numbers[index]); // 30
}
~~~

Guard ở vế trái đi trước thao tác có thể lỗi. `&&` dừng ngay khi gặp false. Nếu dùng `&` boolean hoặc đặt thao tác truy cập trước guard, có thể ném `NullPointerException` / `ArrayIndexOutOfBoundsException`.

## 10. Biên `double`: giá trị biểu diễn và chính sách sai số

Khi kiểm tra một ngưỡng số thực, `>=`, `<=` so **giá trị `double` đã được biểu diễn**, không tự áp dụng dung sai:

~~~java
double computed = 0.1 + 0.2;

System.out.println(computed);        // 0.30000000000000004
System.out.println(computed <= 0.3); // false
~~~

Nếu nghiệp vụ quy định giá trị chấp nhận sai số, hãy đặc tả ngưỡng dựa trên **đơn vị và yêu cầu**. Không tự nới một biên cứng chỉ để test thành công.

Đối với `NaN`, mọi phép so sánh thứ tự `<`, `<=`, `>`, `>=` đều false. Vì thế, nếu đầu vào buộc phải hữu hạn:

~~~java
static boolean isValidRatio(double ratio) {
    return Double.isFinite(ratio)
            && ratio >= 0.0
            && ratio <= 1.0;
}
~~~

**Không dùng** `if (ratio < 0 || ratio > 1)` làm kiểm tra duy nhất để từ chối dữ liệu không hợp lệ: `NaN` không thỏa vế nào và có thể lọt qua.

## 11. Viết code dễ đọc và dễ kiểm thử

Một số nguyên tắc:

1. **Đặt tên cho ngưỡng** khi có ý nghĩa nghiệp vụ: `PASS_MARK`, `SENIOR_AGE`, `FREE_SHIPPING_FROM`.
2. **Từ chối giá trị ngoài miền trước**, sau đó mới phân loại.
3. Nếu xét ngưỡng giảm/tăng dần, giữ thứ tự nhất quán và đảm bảo nhánh trước không che nhánh sau.
4. Nếu quy tắc **có ưu tiên**, đặt quy tắc ưu tiên cao hơn trước và ghi chú tại sao.
5. **Tránh thay đổi biến ngay trong điều kiện** như `if (index++ < length)` nếu nhánh sau còn dùng nó.
6. Dùng **ngoặc nhọn** và **bảng kiểm thử biên** để tránh lỗi off-by-one hoặc dangling else.
7. Test **kết quả** và, nếu điều kiện có side effect, cả **thứ tự thao tác**.

Không cần ép mọi phân loại thành một biểu thức điều kiện `?:` lồng dài. Chuỗi `if/else if` rõ thường phù hợp hơn khi có từ ba nhánh, kiểm tra đầu vào hoặc xử lý ngoại lệ.

## 12. Chương trình Java 17 chạy độc lập

Lưu đúng tên file **`IfBoundaryBranchOrderDemo.java`**:

~~~java
public final class IfBoundaryBranchOrderDemo {
    private IfBoundaryBranchOrderDemo() {}

    static String grade(int score) {
        if (score < 0 || score > 100) {
            return "INVALID";
        }
        if (score >= 90) {
            return "A";
        } else if (score >= 70) {
            return "B";
        } else if (score >= 50) {
            return "C";
        }
        return "D";
    }

    static String ageGroup(int age) {
        if (age >= 65) {
            return "SENIOR";
        } else if (age >= 18) {
            return "ADULT";
        }
        return "MINOR";
    }

    static String wrongAgeGroup(int age) {
        if (age >= 18) {
            return "ADULT";
        } else if (age >= 65) {
            return "SENIOR";
        }
        return "MINOR";
    }

    static int shippingFee(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("negative amount");
        }
        if (amount >= 500_000) {
            return 0;
        } else if (amount >= 200_000) {
            return 15_000;
        }
        return 30_000;
    }

    static String elementOrError(int[] values, int index) {
        if (values == null || index < 0 || index >= values.length) {
            return "OUT_OF_RANGE";
        }
        return Integer.toString(values[index]);
    }

    static boolean check(String name, boolean answer) {
        System.out.println("checked " + name);
        return answer;
    }

    public static void main(String[] args) {
        int[] testScores = {-1, 0, 49, 50, 69, 70, 89, 90, 100, 101};
        for (int score : testScores) {
            System.out.println("score " + score + "=" + grade(score));
        }

        System.out.println("age 17=" + ageGroup(17));
        System.out.println("age 18=" + ageGroup(18));
        System.out.println("age 65=" + ageGroup(65));
        System.out.println("wrong age 70=" + wrongAgeGroup(70));
        System.out.println("right age 70=" + ageGroup(70));

        System.out.println("fee 199999=" + shippingFee(199_999));
        System.out.println("fee 200000=" + shippingFee(200_000));
        System.out.println("fee 499999=" + shippingFee(499_999));
        System.out.println("fee 500000=" + shippingFee(500_000));

        int[] values = {10, 20, 30};
        System.out.println("index -1=" + elementOrError(values, -1));
        System.out.println("index 0=" + elementOrError(values, 0));
        System.out.println("index 2=" + elementOrError(values, 2));
        System.out.println("index 3=" + elementOrError(values, 3));
        System.out.println("index null=" + elementOrError(null, 0));

        double sum = 0.1 + 0.2;
        System.out.println("double sum=" + sum);
        System.out.println("double at most 0.3=" + (sum <= 0.3));
        System.out.println("NaN valid ratio=" + isValidRatio(Double.NaN));

        if (check("first", false)) {
            System.out.println("chosen=first");
        } else if (check("second", true)) {
            System.out.println("chosen=second");
        } else if (check("third", true)) {
            System.out.println("chosen=third");
        } else {
            System.out.println("chosen=else");
        }

        Boolean permission = null;
        if (Boolean.TRUE.equals(permission)) {
            System.out.println("permission=ALLOWED");
        } else {
            System.out.println("permission=DENIED");
        }
    }

    static boolean isValidRatio(double ratio) {
        return Double.isFinite(ratio)
                && ratio >= 0.0
                && ratio <= 1.0;
    }
}
~~~

**Yêu cầu:** JDK 17+, terminal ở thư mục chứa file; chỉ dùng JDK tiêu chuẩn.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 IfBoundaryBranchOrderDemo.java
java -cp . IfBoundaryBranchOrderDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\IfBoundaryBranchOrderDemo.java
java -cp . IfBoundaryBranchOrderDemo
~~~

**Exact expected stdout:**

~~~text
score -1=INVALID
score 0=D
score 49=D
score 50=C
score 69=C
score 70=B
score 89=B
score 90=A
score 100=A
score 101=INVALID
age 17=MINOR
age 18=ADULT
age 65=SENIOR
wrong age 70=ADULT
right age 70=SENIOR
fee 199999=30000
fee 200000=15000
fee 499999=15000
fee 500000=0
index -1=OUT_OF_RANGE
index 0=10
index 2=30
index 3=OUT_OF_RANGE
index null=OUT_OF_RANGE
double sum=0.30000000000000004
double at most 0.3=false
NaN valid ratio=false
checked first
checked second
chosen=second
permission=DENIED
~~~

Giải thích nhanh:

- `score 50`, `70`, `90` đi vào nhóm bắt đầu **ngay tại ngưỡng** nhờ `>=`.
- `wrong age 70=ADULT` cho thấy nhánh rộng `age >= 18` đã che khuất nhánh `age >= 65`.
- Mức phí giao hàng giảm đúng tại 200.000 và 500.000, không giảm trước ngưỡng.
- Chỉ số 3 bằng `values.length` nên bị loại.
- `0.1+0.2` vượt literal `0.3` một sai số nhị phân nhỏ, và `NaN` bị kiểm tra hữu hạn từ chối.
- Chỉ có `checked first` và `checked second`; điều kiện thứ ba không được gọi.
- `Boolean.TRUE.equals(null)` cho false, không unbox null.

## 13. Các lỗi thường gặp

| Lỗi | Hệ quả | Cách tránh |
|---|---|---|
| Dùng `score > 90` khi 90 thuộc hạng A | Bỏ sót đúng biên | `score >= 90` |
| Đặt `score >= 50` trước `score >= 90` | Giá trị 90 bị nhánh C nhận trước | Xếp ngưỡng theo thứ tự phù hợp |
| Dùng `else if` cho hai tính chất đều cần báo | Chỉ nhánh đúng đầu tiên chạy | Dùng hai `if` độc lập |
| Dùng nhiều `if` khi chỉ muốn một nhãn | Có thể chạy nhiều nhánh | Dùng chuỗi `else if` |
| Không validate `score > 100` | Điểm 101 bị nhận thành A | Kiểm tra miền trước |
| Dùng `index <= array.length` | Off-by-one, có thể lỗi truy cập | `index < array.length` |
| Viết `18 <= age <= 65` | Không hợp lệ trong Java | `age >= 18 && age <= 65` |
| Xem `else` thuộc `if` bên ngoài tùy ý | Dangling else gắn với if gần nhất | Dùng `{ }` |
| Chuyển thứ tự điều kiện có side effect | Đổi thứ tự gọi method/lỗi runtime | Tách side effect, giữ thứ tự |
| Giả định `double` so biên như thập phân chính xác | Sai số tính toán | Chọn mô hình/dung sai theo yêu cầu |
| Chỉ kiểm tra `ratio < 0 \|\| ratio > 1` | `NaN` có thể lọt | `Double.isFinite(ratio)` |
| Tính `max+1` gần `Integer.MAX_VALUE` để test | Overflow | Dùng kiểm thử biểu diễn hợp lệ |
| Chỉ kiểm tra giá trị giữa khoảng | Bỏ sót lỗi tại hai đầu | Test dưới/đúng/trên ngưỡng |

## 14. Bài tập có đáp án

### Bài A — xếp loại từ ngưỡng cao xuống thấp

Yêu cầu: `score >= 80` → GOOD, `score >= 50` → PASS, còn lại → FAIL.

~~~java
String label;
if (score >= 80) {
    label = "GOOD";
} else if (score >= 50) {
    label = "PASS";
} else {
    label = "FAIL";
}
~~~

**Đáp án tại biên:** 49 → FAIL; 50 → PASS; 79 → PASS; 80 → GOOD. Nếu miền hợp lệ riêng (ví dụ 0–100), cần validate trước.

### Bài B — vì sao `age >= 18` trước `age >= 65` sai?

**Đáp án:** tuổi 65 trở lên đã thỏa `age >= 18`, nên nhánh senior phía sau không được xét. Đặt `age >= 65` trước.

### Bài C — hai `if` hay `else if`?

Muốn in **cả** `positive` và `even` cho số 4:

~~~java
if (number > 0) System.out.println("positive");
if (number % 2 == 0) System.out.println("even");
~~~

**Đáp án:** cần hai `if` độc lập, vì cả hai điều kiện có thể đồng thời đúng. Nếu dùng `else if`, chỉ in `positive`.

### Bài D — kiểm tra chỉ số mảng

Mảng có ba phần tử, chỉ số hợp lệ là bao nhiêu?

**Đáp án:** `0`, `1`, `2`; điều kiện `index >= 0 && index < 3`. Chỉ số -1 và 3 đều bị loại.

### Bài E — thứ tự thực hiện nhánh

~~~java
if (check("A", false)) {
    System.out.println("A");
} else if (check("B", true)) {
    System.out.println("B");
} else if (check("C", true)) {
    System.out.println("C");
}
~~~

**Đáp án:**

~~~text
checked A
checked B
B
~~~

Method `check` lấy từ chương trình đầy đủ. Không gọi `check("C", true)`.

## 15. Checklist và kết luận

- [ ] Xác định rõ miền hợp lệ và ý nghĩa đúng tại từng biên.
- [ ] Dùng `>`/`>=`, `<`/`<=` theo đặc tả.
- [ ] Hiểu `if/else if/else` xét từ trên xuống và dừng ở **nhánh đúng đầu tiên**.
- [ ] Phân biệt chuỗi `else if` với nhiều câu `if` độc lập.
- [ ] Xếp điều kiện rộng/hẹp hoặc ưu tiên nghiệp vụ sao cho không che nhánh cần thiết.
- [ ] Validate giá trị ngoài miền trước khi phân loại.
- [ ] Xây dựng bảng test tại **dưới/đúng/trên** từng ngưỡng, gồm hai biên toàn miền.
- [ ] Không viết so sánh dây chuyền kiểu `min <= value <= max` trong Java.
- [ ] Dùng `{ }` để tránh dangling else.
- [ ] Không bỏ qua tác động của thứ tự nhánh lên side effect và ngoại lệ.
- [ ] Dùng guard `&&`/`||` đúng thứ tự trước truy cập có thể lỗi.
- [ ] Cẩn thận với `double` tại biên, NaN và overflow trong test số nguyên cực trị.
- [ ] Chạy `IfBoundaryBranchOrderDemo.java` bằng Java 17+ và đối chiếu exact stdout.

**Kết luận:** Viết `if` đúng không chỉ là tạo điều kiện trả true/false; còn phải **định nghĩa ranh giới**, **ưu tiên nhánh**, **bao phủ miền dữ liệu** và **kiểm tra các giá trị sát ngưỡng**. Với chuỗi `else if`, thứ tự chính là một phần của logic nghiệp vụ. Đặt điều kiện có chủ đích và kiểm thử tại biên sẽ tránh được cả lỗi *off-by-one* lẫn nhánh bị che khuất.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="if-statements-007">Guard clause để giảm mức lồng nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Guard clause để giảm mức lồng nhau — Java 17+

**Guard clause** (*mệnh đề bảo vệ*) là kiểm tra đặt ở **đầu một phương thức hoặc đoạn xử lý** để loại bỏ sớm những trường hợp không thể tiếp tục. Khi điều kiện loại trừ đúng, chương trình **thoát sớm** bằng `return`, `throw` hoặc, trong vòng lặp, `continue`. Phần xử lý chính vì vậy không phải nằm sâu trong nhiều lớp `if`.

~~~java
static String greet(String name) {
    if (name == null || name.isBlank()) {
        return "Tên không hợp lệ";
    }

    return "Xin chào " + name.trim();
}
~~~

Với tên không hợp lệ, phương thức trả kết quả ngay; lệnh tạo lời chào **không chạy**. Đây là một kỹ thuật tổ chức luồng điều khiển bằng các câu lệnh Java thông thường, **không phải từ khóa hoặc cú pháp mới**.

## 1. So sánh trước và sau khi dùng guard clause

### Nhiều cấp `if` lồng nhau

~~~java
static String checkNested(String name, Integer age, boolean banned) {
    if (name != null && !name.isBlank()) {
        if (age != null) {
            if (age >= 18) {
                if (!banned) {
                    return "approved:" + name.trim();
                } else {
                    return "reject:banned";
                }
            } else {
                return "reject:underage";
            }
        } else {
            return "reject:age-missing";
        }
    } else {
        return "reject:name";
    }
}
~~~

Mỗi điều kiện bổ sung đẩy đường đi thành công vào sâu hơn. Người đọc phải theo dõi nhiều dấu `{ }` và nhánh `else` mới tìm được câu xử lý chính.

### Tách các trường hợp loại trừ bằng guard

~~~java
static String checkGuarded(String name, Integer age, boolean banned) {
    if (name == null || name.isBlank()) {
        return "reject:name";
    }
    if (age == null) {
        return "reject:age-missing";
    }
    if (age < 18) {
        return "reject:underage";
    }
    if (banned) {
        return "reject:banned";
    }

    return "approved:" + name.trim();
}
~~~

Cả hai phiên bản cho **cùng kết quả** với những đầu vào minh họa ở bài, nhưng phiên bản guard làm nổi bật **đường đi thành công** ở cuối, thay vì giấu nó trong bốn cấp lồng nhau.

| Khía cạnh | `if` lồng nhau | Guard clause |
| --- | --- | --- |
| Kiểm tra đầu vào sai | Nằm ở nhiều nhánh `else` | Đọc theo thứ tự từ trên xuống |
| Phần xử lý chính | Dễ bị đẩy sâu vào trong | Ở mức thụt dòng ngoài cùng của phương thức |
| Luồng thoát | Có thể là một nhánh khác | Rõ ràng: `return` hoặc `throw` |
| Khi nên dùng | Có nhiều nhánh nghiệp vụ thực sự phải đi tiếp | Muốn loại bỏ các trường hợp không hợp lệ sớm |
| Rủi ro | Lồng sâu, dễ nhầm `else` | Quá nhiều điểm thoát nếu tổ chức kém |

**Không có quy tắc bắt buộc** mọi `if` phải biến thành guard. Mục tiêu là luồng điều khiển **dễ hiểu, đúng ngữ nghĩa**.

## 2. Chương trình Java 17+ hoàn chỉnh

Tạo tệp UTF-8 tên **`GuardClauseDemo.java`**:

~~~java
public class GuardClauseDemo {
    static String checkNested(String name, Integer age, boolean banned) {
        if (name != null && !name.isBlank()) {
            if (age != null) {
                if (age >= 18) {
                    if (!banned) {
                        return "approved:" + name.trim();
                    } else {
                        return "reject:banned";
                    }
                } else {
                    return "reject:underage";
                }
            } else {
                return "reject:age-missing";
            }
        } else {
            return "reject:name";
        }
    }

    static String checkGuarded(String name, Integer age, boolean banned) {
        if (name == null || name.isBlank()) {
            return "reject:name";
        }
        if (age == null) {
            return "reject:age-missing";
        }
        if (age < 18) {
            return "reject:underage";
        }
        if (banned) {
            return "reject:banned";
        }
        return "approved:" + name.trim();
    }

    static String trace(String name, Integer age, boolean banned) {
        System.out.println("check=name");
        if (name == null || name.isBlank()) {
            return "reject:name";
        }

        System.out.println("check=age");
        if (age == null || age < 18) {
            return "reject:age";
        }

        System.out.println("check=banned");
        if (banned) {
            return "reject:banned";
        }

        System.out.println("process=main");
        return "approved:" + name.trim();
    }

    static int averagePerPerson(int total, int people) {
        if (people <= 0) {
            throw new IllegalArgumentException("people must be positive");
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must be non-negative");
        }
        return total / people;
    }

    static void printPositive(int[] numbers) {
        for (int number : numbers) {
            if (number <= 0) {
                continue;
            }
            System.out.println("positive=" + number);
        }
    }

    static void logIfPresent(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        System.out.println("log=" + value.trim());
    }

    public static void main(String[] args) {
        String[] names = {"An", null, "   ", "Binh", "Chi", "Dung", " Hoa "};
        Integer[] ages = {20, 20, 22, null, 17, 20, 21};
        boolean[] banned = {false, false, false, false, false, true, false};

        System.out.println("== nested vs guarded ==");
        for (int i = 0; i < names.length; i++) {
            String nested = checkNested(names[i], ages[i], banned[i]);
            String guarded = checkGuarded(names[i], ages[i], banned[i]);
            System.out.println("case" + (i + 1) + "=" + guarded
                    + ",same=" + nested.equals(guarded));
        }

        System.out.println("== early exit ==");
        System.out.println("invalid=" + trace(null, null, false));
        System.out.println("valid=" + trace("Lan", 25, false));

        System.out.println("== guard with throw ==");
        System.out.println("average=" + averagePerPerson(12, 3));
        try {
            averagePerPerson(12, 0);
        } catch (IllegalArgumentException ex) {
            System.out.println("invalidPeople=" + ex.getMessage());
        }
        try {
            averagePerPerson(-1, 1);
        } catch (IllegalArgumentException ex) {
            System.out.println("invalidTotal=" + ex.getMessage());
        }

        System.out.println("== continue in loop ==");
        printPositive(new int[] {-2, 0, 3, 5});

        System.out.println("== void guard ==");
        logIfPresent(null);
        logIfPresent("  OK  ");
        System.out.println("done");
    }
}
~~~

Yêu cầu **JDK 17 trở lên**. Tại thư mục chứa tệp, sử dụng PowerShell trên Windows hoặc Bash trên Linux/macOS:

~~~bash
java -version
javac -version
javac -encoding UTF-8 GuardClauseDemo.java
java GuardClauseDemo
~~~

**Exact expected stdout** của riêng lệnh `java GuardClauseDemo`:

~~~text
== nested vs guarded ==
case1=approved:An,same=true
case2=reject:name,same=true
case3=reject:name,same=true
case4=reject:age-missing,same=true
case5=reject:underage,same=true
case6=reject:banned,same=true
case7=approved:Hoa,same=true
== early exit ==
check=name
invalid=reject:name
check=name
check=age
check=banned
process=main
valid=approved:Lan
== guard with throw ==
average=4
invalidPeople=people must be positive
invalidTotal=total must be non-negative
== continue in loop ==
positive=3
positive=5
== void guard ==
log=OK
done
~~~

`java -version` và `javac -version` có đầu ra tùy máy, không nằm trong khối stdout cố định. Mọi phương thức được sử dụng trong chương trình đều đã được định nghĩa trong cùng một lớp.

## 3. Luồng chạy của guard clause

Chương trình `trace` in từng lần kiểm tra. Với đầu vào `trace(null, null, false)`, stdout chỉ có `check=name` rồi `invalid=reject:name`. Các lần kiểm tra tuổi và trạng thái bị chặn **không diễn ra**.

Với `trace("Lan", 25, false)`, mọi guard đều vượt qua; chương trình đến phần `process=main` và trả giá trị thành công.

~~~mermaid
flowchart TD
    A["Bắt đầu phương thức"] --> B{"Tên không hợp lệ?"}
    B -- "Có" --> R1["return reject:name"]
    B -- "Không" --> C{"Tuổi thiếu hoặc quá nhỏ?"}
    C -- "Có" --> R2["return reject:age"]
    C -- "Không" --> D{"Bị chặn?"}
    D -- "Có" --> R3["return reject:banned"]
    D -- "Không" --> E["Xử lý chính"]
    E --> F["return approved"]
~~~

**Quan trọng:** `return` rời khỏi **toàn bộ phương thức hiện tại**, không chỉ rời khỏi khối `if`. Mọi câu lệnh nằm sau `return` trên đường chạy đó đều không được thực thi.

## 4. Chọn `return`, `throw` hay `continue`

Guard clause là cách tổ chức các **điểm thoát sớm**. Điểm thoát thích hợp phụ thuộc vào việc đầu vào sai là một trường hợp bình thường hay một lỗi cần báo cho bên gọi.

| Câu lệnh | Phạm vi thoát | Dùng khi |
| --- | --- | --- |
| `return value;` | Kết thúc phương thức có giá trị trả về | Một trường hợp hợp lệ được giải quyết sớm bằng kết quả |
| `return;` | Kết thúc phương thức `void` | Không có gì cần xử lý tiếp |
| `throw new ...;` | Kết thúc luồng bình thường bằng ngoại lệ | Đầu vào vi phạm hợp đồng và bên gọi cần được báo lỗi |
| `continue;` | Bỏ phần còn lại của lần lặp, sang lần kế tiếp | Loại một phần tử khỏi xử lý trong vòng lặp |
| `break;` | Kết thúc vòng lặp hoặc `switch` chứa nó | Không cần tiếp tục lặp |

### `return` trong phương thức `void`

~~~java
static void logIfPresent(String text) {
    if (text == null || text.isBlank()) {
        return;
    }
    System.out.println(text.trim());
}
~~~

`return;` không mang giá trị vì phương thức có kiểu trả về `void`.

### `throw` để báo vi phạm điều kiện đầu vào

~~~java
static int divide(int total, int divisor) {
    if (divisor == 0) {
        throw new IllegalArgumentException("divisor must not be zero");
    }
    return total / divisor;
}
~~~

Nếu `divisor` bằng 0, phương thức ném ngoại lệ **trước khi chia**, không chỉ trả một giá trị mặc định có thể che giấu lỗi.

### `continue` để tránh lồng `if` trong vòng lặp

~~~java
for (int number : numbers) {
    if (number <= 0) {
        continue;
    }
    process(number);
}
~~~

Khi `number <= 0`, `process(number)` bị bỏ qua **chỉ trong lần lặp đó**. `continue` **không** kết thúc phương thức bao quanh. `process` ở ví dụ này là tên mô tả một thao tác thực tế; chương trình runnable dùng `System.out.println` để minh họa mà không cần phương thức ngoài.

**Đừng nhầm `return` với `continue`:** dùng `return` trong vòng lặp sẽ kết thúc luôn phương thức; dùng `continue` chỉ bỏ qua phần xử lý còn lại của lần lặp hiện tại.

## 5. Thứ tự guard ảnh hưởng đến an toàn và kết quả

### Kiểm tra `null` trước khi sử dụng dữ liệu

~~~java
static boolean isAdult(Integer age) {
    if (age == null) {
        return false;
    }
    return age >= 18; // Unbox an toàn sau guard.
}
~~~

Nếu viết `if (age < 18)` trước khi kiểm tra `age == null`, Java có thể ném `NullPointerException` vì phải unbox `Integer` null.

Có thể kết hợp bằng điều kiện ngắn mạch:

~~~java
if (name == null || name.isBlank()) {
    return "reject:name";
}
~~~

`||` không gọi `name.isBlank()` nếu `name == null` đã true. Nếu dùng `|` thay `||`, cả hai vế được đánh giá và lời gọi trên null có thể ném ngoại lệ.

### Kiểm tra miền đầu vào trước khi thực hiện phép tính

~~~java
if (people <= 0) {
    throw new IllegalArgumentException("people must be positive");
}
int average = total / people;
~~~

Kiểm tra này không chỉ tránh phép chia cho 0 mà còn thực thi quy định nghiệp vụ “số người phải dương”.

### Thứ tự quyết định thông báo lỗi

~~~java
if (name == null || name.isBlank()) {
    return "reject:name";
}
if (age == null) {
    return "reject:age-missing";
}
~~~

Nếu cả hai đầu vào đều sai, guard **đầu tiên** quyết định giá trị được trả về. Khi nhiều điều kiện cùng có thể sai, hãy chọn thứ tự kiểm tra có chủ đích và giữ nguyên nếu đang refactor mã có hành vi quan sát được.

**Guard clause không tự động loại bỏ tác dụng phụ** của biểu thức điều kiện: nếu điều kiện gọi phương thức có thay đổi trạng thái, việc sắp xếp lại thứ tự guard có thể thay đổi hành vi.

## 6. Guard clause và `if/else`: nên chọn phong cách nào?

**Guard phù hợp** khi có một đường đi chính và một số điều kiện khiến xử lý phải dừng sớm:

~~~java
static String describe(String text) {
    if (text == null) {
        return "missing";
    }
    if (text.isBlank()) {
        return "empty";
    }
    return "text:" + text.trim();
}
~~~

**`if/else` phù hợp** khi cả hai nhánh đều là hành vi nghiệp vụ bình thường và có trọng lượng tương đương:

~~~java
if (isMorning) {
    serveBreakfast();
} else {
    serveDinner();
}
~~~

Hai lời gọi `serveBreakfast`/`serveDinner` minh họa cấu trúc nhánh, không thuộc chương trình runnable ở mục 2. Không cần ép một lựa chọn hai nhánh cân bằng thành nhiều guard không tự nhiên.

**Các điểm cân nhắc:**

- Giữ điều kiện guard **đơn giản, có tên rõ ràng**, ưu tiên trả sớm khi không thể tiếp tục.
- Nếu guard liên tục lặp lại ở nhiều nơi, cân nhắc một phương thức xác thực có hợp đồng rõ ràng.
- Không cố gom mọi điều kiện vào một câu `if` dài nếu các trường hợp lỗi cần thông báo riêng.
- Không dùng `return null` hoặc giá trị mặc định nếu nó khiến lỗi đầu vào nghiêm trọng trở nên khó phát hiện; cân nhắc `throw` hoặc kiểu kết quả phù hợp.
- Không đặt quá nhiều điểm thoát khiến việc theo dõi **tài nguyên và thay đổi trạng thái** trở nên khó hiểu.

## 7. Cleanup và tài nguyên khi thoát sớm

Một `return` sớm có thể bỏ qua các câu lệnh ở phía sau. Vì vậy phải tính đến thao tác dọn dẹp:

~~~java
static void example() {
    // Nếu đã mở tài nguyên, không được chỉ dựa vào một câu
    // lệnh close() nằm sau mọi đường return.
}
~~~

Với tài nguyên implements `AutoCloseable`, nên dùng **try-with-resources** để bảo đảm gọi `close()` khi rời khối, kể cả thoát bằng `return` hoặc `throw`:

~~~java
static String firstLine(java.io.BufferedReader reader)
        throws java.io.IOException {
    try (reader) {
        String line = reader.readLine();
        if (line == null) {
            return "empty";
        }
        return line;
    }
}
~~~

Ví dụ nhận một `BufferedReader` và chủ động **nhận quyền đóng reader** trong khối try. Không dùng mẫu này nếu hợp đồng yêu cầu bên gọi tiếp tục sở hữu và sử dụng reader sau lời gọi. Java cũng thực hiện khối `finally` khi rời `try` bằng `return` theo luồng thông thường; hãy tránh viết `return` trong `finally` vì có thể che giấu kết quả hoặc ngoại lệ.

## 8. Lỗi thường gặp

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “Guard clause là cú pháp riêng của Java.” | Chỉ là mẫu dùng `if` với điểm thoát sớm |
| “`return` chỉ thoát khỏi khối `if`.” | `return` kết thúc **phương thức** hiện tại |
| “`continue` và `return` giống nhau trong vòng lặp.” | `continue` bỏ qua một lần lặp; `return` rời phương thức |
| “Cứ đổi mọi `else` thành `return` là tốt.” | Cần giữ ngữ nghĩa và tính dễ đọc |
| “Có thể kiểm tra `age < 18` trước khi biết `age != null`.” | Có thể gây unboxing null |
| “Thứ tự các guard không quan trọng.” | Guard đầu tiên quyết định lỗi, tác dụng phụ và đường chạy |
| “`throw` và `return false` tương đương.” | Một bên là ngoại lệ, một bên là kết quả bình thường |
| “Thoát sớm luôn bỏ qua dọn dẹp `try-with-resources`.” | Tài nguyên vẫn được đóng khi rời khối theo cơ chế Java |
| “Guard luôn phải ở dòng đầu tiên.” | Đặt trước đoạn xử lý phụ thuộc vào tiền điều kiện; có thể cần khởi tạo hợp lý trước đó |

## 9. Bài tập tự kiểm tra

1. `String s = null; if (s == null) return;` có gọi `s.isBlank()` ở lệnh sau không?  
   *Đáp án:* Không; phương thức đã kết thúc.
2. Vì sao `if (age == null) return false;` hữu ích trước `age >= 18` khi `age` là `Integer`?  
   *Đáp án:* Tránh unboxing null gây `NullPointerException`.
3. Khi kiểm tra `people <= 0` và đây là lỗi đầu vào, nên làm gì?  
   *Đáp án:* Thường `throw new IllegalArgumentException(...)` nếu hợp đồng phương thức yêu cầu số người dương.
4. Trong vòng lặp, muốn bỏ qua phần tử âm nhưng vẫn tiếp tục phần tử sau thì chọn câu lệnh nào?  
   *Đáp án:* `continue`.
5. Trong vòng lặp, dùng `return` thay `continue` thì điều gì thay đổi?  
   *Đáp án:* Kết thúc toàn bộ phương thức, không xử lý phần tử sau.
6. Nếu `name` null và `age` null, phương thức `checkGuarded` báo lỗi gì?  
   *Đáp án:* `reject:name` vì guard đó đứng trước.
7. Hai phiên bản lồng nhau và dùng guard có nhất thiết luôn tương đương không?  
   *Đáp án:* Không; cần giữ thứ tự đánh giá, tác dụng phụ, kết quả và ngoại lệ.
8. Khi phương thức mở tài nguyên trước các guard, nên quản lý việc đóng tài nguyên bằng gì?  
   *Đáp án:* Ví dụ `try-with-resources` (khi phương thức chịu trách nhiệm đóng tài nguyên).

## Checklist

- [ ] Hiểu guard clause là một câu `if` để **thoát sớm khi không thể tiếp tục**.
- [ ] Dùng `return` cho kết quả/điều kiện kết thúc bình thường, `throw` cho lỗi hợp đồng.
- [ ] Dùng `continue` để bỏ qua phần tử trong vòng lặp, không nhầm với `return`.
- [ ] Kiểm tra `null` và miền giá trị trước khi dùng dữ liệu.
- [ ] Giữ **đường xử lý chính ít lồng nhau** và dễ đọc.
- [ ] Bảo toàn thứ tự kiểm tra, thông báo lỗi và tác dụng phụ khi refactor.
- [ ] Biết khi nào một `if/else` cân bằng phù hợp hơn guard.
- [ ] Không quên dọn dẹp tài nguyên khi phương thức có nhiều đường thoát.

**Kết luận:** Guard clause không thay đổi khả năng rẽ nhánh của Java; nó giúp **diễn đạt các điều kiện dừng một cách sớm và rõ**, từ đó giảm mức lồng `if` cho đường đi chính. Hãy dùng nó có chủ đích và giữ nguyên hành vi cần thiết khi chuyển từ mã lồng nhau.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="if-statements-008">Kiểm tra từng nhánh bằng bảng trường hợp đầu vào</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra từng nhánh bằng bảng trường hợp đầu vào trong Java

> **Java core — language-basics / Rẽ nhánh if**  
> **Môi trường:** JDK 17+, một file Java độc lập, không dùng thư viện bên ngoài.  
> **Mục tiêu:** Chuyển các nhánh `if / else if / else` thành **bảng trường hợp đầu vào – đầu ra mong đợi**, kiểm thử các biên và điều kiện chồng lấp, chạy kiểm tra tự động và nhận biết giới hạn của việc thử một vài giá trị ngẫu nhiên.

## 1. Tại sao cần bảng kiểm tra từng nhánh?

Một chuỗi `if / else if / else` có nhiều đường đi. Chạy một giá trị thuận tiện, ví dụ chỉ `score = 95`, **không chứng minh** các nhánh khác hoạt động đúng.

Cách làm có thể lặp lại:

1. Đọc điều kiện theo đúng **thứ tự** và xác định những nhánh có thể được chọn.
2. Ghi rõ mỗi **đầu vào**, **nhánh mong đợi** và **kết quả mong đợi** vào bảng.
3. Chọn ít nhất một trường hợp đi vào **mỗi nhánh**, kể cả `else` và đường xử lý dữ liệu không hợp lệ.
4. Thêm dữ liệu **ngay dưới, đúng tại và ngay trên** các ngưỡng có ý nghĩa.
5. Chạy chương trình, so sánh **actual** với **expected**, báo FAIL khi khác nhau.

**Lưu ý:** Bảng do người viết thiết kế là một bộ *test cases*, không tự động chứng minh mọi đầu vào đều đúng. Một bảng tốt giúp nhìn rõ nhánh còn thiếu; kiểm thử càng chặt cần xét cả tổ hợp điều kiện, lỗi, null và thay đổi trạng thái.

## 2. Ví dụ: phân loại điểm theo chuỗi loại trừ

Quy tắc nghiệp vụ: điểm phải thuộc `0..100`; các nhãn lần lượt là `A` (≥90), `B` (≥75), `C` (≥50), `D` (còn lại).

~~~java
static String classifyScore(int score) {
    if (score < 0 || score > 100) {
        return "INVALID";
    } else if (score >= 90) {
        return "A";
    } else if (score >= 75) {
        return "B";
    } else if (score >= 50) {
        return "C";
    } else {
        return "D";
    }
}
~~~

Java chỉ chọn **nhánh đúng đầu tiên** trong chuỗi. Do đó cần kiểm tra ngưỡng cao trước ngưỡng thấp: nếu kiểm tra `score >= 50` trước `score >= 90`, điểm 95 có thể bị phân loại thành C.

Các nhánh cần được quan sát: `INVALID`, `A`, `B`, `C`, `D`. Riêng điều kiện đầu `score < 0 || score > 100` có **hai lý do khác nhau** khiến giá trị không hợp lệ: quá thấp hoặc quá cao. Kiểm thử cả hai để tránh bỏ sót lỗi chỉ xuất hiện ở một phía.

## 3. Bảng test cases và giá trị sát ranh giới

| Đầu vào `score` | Nhánh mong đợi | Expected | Lý do chọn |
| ---: | --- | --- | --- |
| -1 | Không hợp lệ | `INVALID` | Dưới ngưỡng 0 |
| 0 | `else` | `D` | Biên dưới hợp lệ |
| 49 | `else` | `D` | Ngay dưới 50 |
| 50 | `score >= 50` | `C` | Ngay tại 50 |
| 74 | `score >= 50` | `C` | Ngay dưới 75 |
| 75 | `score >= 75` | `B` | Ngay tại 75 |
| 89 | `score >= 75` | `B` | Ngay dưới 90 |
| 90 | `score >= 90` | `A` | Ngay tại 90 |
| 100 | `score >= 90` | `A` | Biên trên hợp lệ |
| 101 | Không hợp lệ | `INVALID` | Trên ngưỡng 100 |

Bảng bao phủ **cả năm nhánh đầu ra** và các biên quan trọng. Đặc biệt, với `score == 50`, điều kiện `score >= 50` phải đúng; với `score == 49` phải sai. Cặp này giúp phát hiện lỗi vô tình đổi `>=` thành `>`.

Với nhánh kiểm tra `score < 0 || score > 100`, `score = -1` làm vế trái đúng nên vế phải không chạy do short-circuit; `score = 101` làm vế trái sai và vế phải đúng. Hai trường hợp kiểm thử các tình huống khác nhau của điều kiện ghép.

**Không nhất thiết liệt kê mọi điểm 0..100** để kiểm tra biên, nhưng có thể dùng thêm kiểm thử toàn miền hoặc sinh trường hợp khi yêu cầu độ tin cậy cao hơn.

## 4. Bảng cho nhiều `if` độc lập khác với chuỗi `else if`

Với hai cờ `review` và `urgent`, **cả hai hành động có thể cần chạy**:

~~~java
StringBuilder actions = new StringBuilder();
if (review) {
    actions.append("REVIEW");
}
if (urgent) {
    if (actions.length() > 0) actions.append("+");
    actions.append("URGENT");
}
String result = actions.length() == 0 ? "NONE" : actions.toString();
~~~

Các trường hợp phải kiểm tra:

| `review` | `urgent` | Expected | Nhánh thực thi |
| --- | --- | --- | --- |
| false | false | `NONE` | Không nhánh nào |
| true | false | `REVIEW` | Chỉ `if` đầu |
| false | true | `URGENT` | Chỉ `if` thứ hai |
| true | true | `REVIEW+URGENT` | **Cả hai `if`** |

Nếu vô tình thay `if (urgent)` bằng `else if (urgent)`, trường hợp `true, true` sẽ bị thiếu `URGENT`. Đây là lý do bảng **tổ hợp các điều kiện** quan trọng hơn chỉ thử riêng từng cờ.

Với một chuỗi loại trừ `if/else if/else`, bảng lại cần xác nhận **chỉ một nhánh** chạy và điều kiện phía sau nhánh đã chọn không được đánh giá.

## 5. Phân biệt độ phủ nhánh và kiểm thử giá trị biên

| Kỹ thuật | Câu hỏi cần trả lời | Ví dụ |
| --- | --- | --- |
| Bao phủ nhánh đầu ra | Mọi nhánh `if/else if/else` đã được chọn ít nhất một lần chưa? | Đầu ra `INVALID`, `A`, `B`, `C`, `D` |
| Kiểm tra giá trị biên | Dấu `<`, `<=`, `>`, `>=` có chính xác tại ranh giới không? | 49/50, 74/75, 89/90 |
| Tổ hợp điều kiện | Các điều kiện đồng thời đúng/sai có bị bỏ quên không? | Hai cờ true/true |
| Điều kiện ghép | Mỗi nguyên nhân làm một điều kiện ghép đúng/sai đã được xét chưa? | Điểm `-1` và `101` |
| Giá trị ngoại lệ | Đầu vào ngoài miền, null hoặc trạng thái không hợp lệ được xử lý ra sao? | `score=-1`, `score=101` |

**Bao phủ nhánh chưa đủ để bảo đảm tính đúng.** Ví dụ một test có thể vào nhánh B bằng điểm 80, nhưng không phát hiện lỗi ở biên 75. Độ phủ cao cũng không thay thế việc đối chiếu với **yêu cầu nghiệp vụ**.

Khi điều kiện phụ thuộc thứ tự hoặc có side effect, hãy lập bảng thể hiện **biểu thức nào được đánh giá**; không giả định mọi `else if` đều chạy. Với `null`, cần thiết kế đầu vào có và không có dữ liệu, kiểm tra guard trước khi gọi phương thức.

## 6. Chương trình Java 17+ tự kiểm tra bảng đầu vào

Tạo file **IfBranchCaseTableDemo.java**:

~~~java
public final class IfBranchCaseTableDemo {
    private record ScoreCase(int input, String expected) {}
    private record FlagCase(boolean review, boolean urgent, String expected) {}

    private static String classifyScore(int score) {
        if (score < 0 || score > 100) {
            return "INVALID";
        } else if (score >= 90) {
            return "A";
        } else if (score >= 75) {
            return "B";
        } else if (score >= 50) {
            return "C";
        } else {
            return "D";
        }
    }

    private static String actions(boolean review, boolean urgent) {
        StringBuilder result = new StringBuilder();
        if (review) {
            result.append("REVIEW");
        }
        if (urgent) {
            if (result.length() > 0) {
                result.append("+");
            }
            result.append("URGENT");
        }
        return result.length() == 0 ? "NONE" : result.toString();
    }

    public static void main(String[] args) {
        ScoreCase[] scores = {
            new ScoreCase(-1, "INVALID"),
            new ScoreCase(0, "D"),
            new ScoreCase(49, "D"),
            new ScoreCase(50, "C"),
            new ScoreCase(74, "C"),
            new ScoreCase(75, "B"),
            new ScoreCase(89, "B"),
            new ScoreCase(90, "A"),
            new ScoreCase(100, "A"),
            new ScoreCase(101, "INVALID")
        };

        int scorePassed = 0;
        for (ScoreCase test : scores) {
            String actual = classifyScore(test.input());
            boolean passed = test.expected().equals(actual);
            if (passed) scorePassed++;
            System.out.println("score=" + test.input()
                    + ",expected=" + test.expected()
                    + ",actual=" + actual
                    + ",status=" + (passed ? "PASS" : "FAIL"));
        }
        System.out.println("scoreTests=" + scorePassed + "/" + scores.length);

        FlagCase[] flags = {
            new FlagCase(false, false, "NONE"),
            new FlagCase(true, false, "REVIEW"),
            new FlagCase(false, true, "URGENT"),
            new FlagCase(true, true, "REVIEW+URGENT")
        };

        int flagsPassed = 0;
        for (FlagCase test : flags) {
            String actual = actions(test.review(), test.urgent());
            boolean passed = test.expected().equals(actual);
            if (passed) flagsPassed++;
            System.out.println("review=" + test.review()
                    + ",urgent=" + test.urgent()
                    + ",expected=" + test.expected()
                    + ",actual=" + actual
                    + ",status=" + (passed ? "PASS" : "FAIL"));
        }
        System.out.println("flagTests=" + flagsPassed + "/" + flags.length);

        if (scorePassed != scores.length || flagsPassed != flags.length) {
            throw new AssertionError("At least one case failed");
        }
    }
}
~~~

Biên dịch và chạy trong thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 IfBranchCaseTableDemo.java
java IfBranchCaseTableDemo
~~~

**Expected stdout chính xác:**

~~~text
score=-1,expected=INVALID,actual=INVALID,status=PASS
score=0,expected=D,actual=D,status=PASS
score=49,expected=D,actual=D,status=PASS
score=50,expected=C,actual=C,status=PASS
score=74,expected=C,actual=C,status=PASS
score=75,expected=B,actual=B,status=PASS
score=89,expected=B,actual=B,status=PASS
score=90,expected=A,actual=A,status=PASS
score=100,expected=A,actual=A,status=PASS
score=101,expected=INVALID,actual=INVALID,status=PASS
scoreTests=10/10
review=false,urgent=false,expected=NONE,actual=NONE,status=PASS
review=true,urgent=false,expected=REVIEW,actual=REVIEW,status=PASS
review=false,urgent=true,expected=URGENT,actual=URGENT,status=PASS
review=true,urgent=true,expected=REVIEW+URGENT,actual=REVIEW+URGENT,status=PASS
flagTests=4/4
~~~

Bài demo dùng `record` có sẵn trong Java 17 để khai báo từng dòng dữ liệu thử; test tách **expected** khỏi **actual** rồi so sánh bằng `equals`. Nếu thay đổi logic làm hai giá trị không trùng nhau, chương trình in `FAIL` và ném `AssertionError` sau khi chạy hết bảng.

Các giá trị expected phải được suy ra từ **đặc tả**, không sao chép máy móc từ output hiện tại của hàm cần kiểm thử; nếu không test có thể luôn PASS dù code sai yêu cầu.

## 7. Checklist trước khi kết luận nhánh đã được kiểm tra

- Tất cả nhánh có thể chọn, kể cả `else`, đã có ít nhất một test?
- Mọi ngưỡng có test ngay **dưới / bằng / trên** hay chưa?
- Điều kiện ghép `&&`/`||` có test cho các tình huống quyết định ở vế trái và vế phải?
- Nhiều `if` độc lập có test khi **cả hai cùng đúng**?
- Các trường hợp không hợp lệ, null, empty hoặc ngoài miền đã có expected rõ ràng?
- Mỗi hàng đều so sánh expected với actual và **báo lỗi khi mismatch**?
- Sau khi sửa thứ tự nhánh hoặc toán tử, các test cũ có được chạy lại không?

Để có độ tin cậy cao hơn, kết hợp test case table với kiểm thử tự động như JUnit khi dự án đã dùng framework; ví dụ trong bài này chỉ cần JDK 17 và chương trình `main` tự kiểm tra.

## 8. Bài tập thực hành

**Bài 1.** Lập bảng đầu vào cho `if (x < 0) ... else if (x == 0) ... else ...`. Chọn ít nhất `-1`, `0` và `1`, ghi đúng nhánh và expected.

**Bài 2.** Trong `classifyScore`, cố ý đổi `score >= 75` thành `score > 75`. Dòng nào trong bảng dự kiến FAIL? Vì sao?

**Bài 3.** Đổi thứ tự hai nhánh `score >= 90` và `score >= 50`. Hàng đầu vào nào giúp phát hiện lỗi chọn nhánh đầu tiên?

**Bài 4.** Với hai `if` độc lập cho `review` và `urgent`, giải thích vì sao cần đủ bốn tổ hợp. Điều gì xảy ra nếu đổi `if` thứ hai thành `else if`?

**Bài 5.** Thêm yêu cầu riêng cho `score == 100` (ví dụ nhãn `PERFECT`) và cập nhật cả code lẫn bảng test: nhánh mới nên đặt ở đâu để được chọn?

### Ghi nhớ

**Kiểm thử rẽ nhánh bắt đầu từ bảng đầu vào → nhánh dự kiến → kết quả dự kiến.** Mỗi nhánh cần có ca thử; các ngưỡng cần test sát biên; điều kiện chồng lấp và nhiều `if` độc lập cần test theo tổ hợp. Việc kiểm tra expected/actual chỉ có ý nghĩa khi expected được xác định độc lập với mã đang kiểm thử.


</details>

- [Quay lại đầu trang](#back-to-top)
