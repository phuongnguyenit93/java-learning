<a id="back-to-top"></a>

# Switch

## Menu
- [Switch statement và các kiểu dữ liệu lựa chọn được hỗ trợ](#switch-statements-001)
- [Nhãn case, default và nhãn trùng lặp](#switch-statements-002)
- [Dạng dấu hai chấm và cơ chế rơi tiếp xuống nhánh sau](#switch-statements-003)
- [break để kết thúc switch statement](#switch-statements-004)
- [Dạng mũi tên -&gt; và phạm vi của khối nhánh](#switch-statements-005)
- [Switch expression trả về một giá trị](#switch-statements-006)
- [yield trong khối nhánh của switch expression](#switch-statements-007)
- [Yêu cầu bao phủ các trường hợp và chọn kiểu kết quả](#switch-statements-008)
- [Xử lý null trong switch và phân biệt với default](#switch-statements-009)
- [Chọn if hoặc switch theo bản chất điều kiện](#switch-statements-010)

## <a id="switch-statements-001">Switch statement và các kiểu dữ liệu lựa chọn được hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

# Switch statement và các kiểu dữ liệu lựa chọn được hỗ trợ — Java 17+

## 1. Switch statement dùng để làm gì?

`switch` chọn cách xử lý dựa trên **một giá trị** (*selector*). Nó hữu ích khi nhiều nhánh cùng so một mã lệnh, ký tự, trạng thái enum hoặc giá trị rời rạc.

~~~java
int option = 2;

switch (option) {
    case 1:
        System.out.println("Create");
        break;
    case 2:
        System.out.println("Update");
        break;
    default:
        System.out.println("Unknown");
}
~~~

**Exact output:**

~~~text
Update
~~~

Trong Java 17, cần phân biệt:

- **Switch statement**: thực thi câu lệnh hoặc khối lệnh; **không tự trả một giá trị** cho phép gán.
- **Switch expression**: tạo một giá trị bằng `switch` ở vị trí biểu thức; có quy tắc khác về tính đầy đủ của các nhánh và `yield`. Bài này tập trung vào **switch statement**.

Có thể viết switch statement bằng cú pháp truyền thống `case ...:` hoặc cú pháp nhánh mũi tên `case ... ->` (đã ổn định trước Java 17).

## 2. Các kiểu selector được hỗ trợ trong Java 17

Trong cú pháp switch thông thường, ổn định của Java 17, biểu thức `switch (selector)` có thể thuộc các kiểu:

| Nhóm | Kiểu selector hợp lệ | Ví dụ |
|---|---|---|
| Số nguyên nguyên thủy | `byte`, `short`, `char`, `int` | `switch (code)` với `int code` |
| Lớp bao tương ứng | `Byte`, `Short`, `Character`, `Integer` | `switch (boxedCode)` |
| Chuỗi | `String` | `switch (command)` |
| Liệt kê | Một kiểu `enum` cụ thể | `switch (status)` với `Status status` |

**Không được dùng trực tiếp**: `long`/`Long`, `float`/`Float`, `double`/`Double`, `boolean`/`Boolean`, `Object`, mảng hoặc một class tùy ý.

~~~java
long bigId = 10L;
// switch (bigId) { case 10 -> System.out.println("ten"); }
// LỖI BIÊN DỊCH ở Java 17: long không phải kiểu selector hợp lệ.

boolean enabled = true;
// switch (enabled) { case true -> System.out.println("enabled"); }
// LỖI BIÊN DỊCH ở Java 17: boolean không được hỗ trợ.
~~~

Một trường hợp đặc biệt: nếu selector có **kiểu tĩnh `Object`**, việc object ở runtime tình cờ chứa một `Integer` không biến `switch (object)` thành switch hợp lệ. Compiler kiểm tra **kiểu biểu thức**, không suy từ giá trị runtime.

**Phạm vi phiên bản:** Java 17 có tính năng *pattern matching for switch* ở trạng thái preview, phải bật tùy chọn preview và có các quy tắc khác. Ví dụ trong bài dùng **switch Java 17 ổn định**, biên dịch bằng `--release 17`, **không bật preview**.

## 3. Nhận diện dấu hai chấm và fall-through

Ở dạng truyền thống, `case VALUE:` đánh dấu điểm bắt đầu thực thi. Nếu không có `break`, `return` hay `throw`, Java có thể chạy các câu lệnh sau nhãn kế tiếp mà không kiểm tra lại selector. Đây là *fall-through*, không có nghĩa nhiều giá trị cùng khớp. Bài về dạng dấu hai chấm và bài `break` phân tích luồng này đầy đủ.

## 4. Nhận diện switch statement dạng mũi tên `->`

Java 17 cho phép `case 2, 3 -> System.out.println("two or three");`. Khi thực hiện xong nhánh mũi tên, chương trình không rơi xuống nhánh sau. Vế phải có thể là statement expression, khối `{ ... }` hoặc `throw`.

Mũi tên không tự biến switch thành **expression**: ví dụ trên vẫn là statement thực hiện thao tác. Bài về khối nhánh mũi tên trình bày riêng cú pháp và phạm vi biến.

## 5. Selector số nguyên nhỏ: `byte`, `short`, `char`, `int`

Các kiểu nguyên thủy được hỗ trợ trực tiếp:

~~~java
byte byteCode = 1;
short shortCode = 300;
char letter = 'A';
int action = 2;

switch (byteCode) {
    case 1 -> System.out.println("byte one");
    default -> System.out.println("byte other");
}

switch (shortCode) {
    case 300 -> System.out.println("short 300");
    default -> System.out.println("short other");
}

switch (letter) {
    case 'A' -> System.out.println("letter A");
    default -> System.out.println("other letter");
}

switch (action) {
    case 2 -> System.out.println("action two");
    default -> System.out.println("other action");
}
~~~

Java kiểm tra **giá trị nhãn `case` có tương thích miền kiểu selector**:

~~~java
byte small = 10;
// switch (small) {
//     case 128 -> System.out.println("impossible");
// }
// LỖI BIÊN DỊCH: 128 vượt giới hạn byte (-128..127).
~~~

Với `char`, nhãn có thể dùng ký tự như `'A'` hoặc biểu thức hằng số nguyên phù hợp. `'A'` và `65` cùng một giá trị, nên không được đặt thành hai nhãn case trùng nhau.

## 6. Nhãn `case` số nguyên phải là hằng tại compile time

Trong switch dùng nhãn hằng truyền thống, `case` số nguyên phải là **biểu thức hằng** phù hợp; một biến thông thường không hợp lệ chỉ vì đang chứa đúng số.

~~~java
static final int OPEN = 1; // constant variable hợp lệ

int option = 1;
switch (option) {
    case OPEN -> System.out.println("open");
    default -> System.out.println("unknown");
}
~~~

Một lỗi thường gặp:

~~~java
int dynamicCode = 1;
// switch (option) {
//     case dynamicCode -> System.out.println("open");
// }
// LỖI BIÊN DỊCH: dynamicCode không phải constant expression.
~~~

`final int` được khởi tạo bằng biểu thức hằng phù hợp có thể dùng làm nhãn. Nhưng `final` đơn thuần **không biến mọi giá trị tính ở runtime thành hằng compile time**.

Ví dụ `final int code = Integer.parseInt("1")` không dùng được làm nhãn `case code` dù code không bị gán lại.

**Nhãn trùng giá trị** là lỗi biên dịch:

~~~java
// case 1 -> ...;
// case 1 -> ...; // LỖI: duplicate case label.
~~~

## 7. Selector `String`: so nội dung, phân biệt hoa/thường

`switch` trên `String` lựa chọn theo **nội dung chuỗi**, không yêu cầu hai biến `String` cùng tham chiếu object.

~~~java
String command = new String("start");

switch (command) {
    case "start" -> System.out.println("starting");
    case "stop" -> System.out.println("stopping");
    default -> System.out.println("unknown");
}
~~~

**Exact output:**

~~~text
starting
~~~

Nhãn `case "start"` phải là hằng `String` tại compile time. Java so nội dung cho mục đích chọn case, tương ứng với nguyên tắc `String.equals`, **không dựa vào `==` tham chiếu**.

**Phân biệt hoa/thường:** `"start"` và `"START"` không khớp như nhau. Nếu muốn không phân biệt hoa/thường, chuẩn hóa đầu vào **theo quy tắc rõ ràng** trước khi switch, ví dụ `command.toLowerCase(Locale.ROOT)` đối với một tập lệnh ASCII không phân biệt chữ hoa/thường.

Không tự gọi `toLowerCase()` trên input chưa được kiểm tra null.

## 8. Selector `enum`: nhãn là tên hằng enum

~~~java
enum Status {
    NEW, RUNNING, DONE
}

Status status = Status.RUNNING;
switch (status) {
    case NEW -> System.out.println("created");
    case RUNNING -> System.out.println("working");
    case DONE -> System.out.println("finished");
}
~~~

**Exact output:**

~~~text
working
~~~

Trong một switch có selector kiểu `Status`, nhãn enum viết dạng **`case RUNNING`**, không viết `case Status.RUNNING` trong cú pháp switch enum thông thường của Java 17. Các nhãn enum phải thuộc kiểu enum đang dùng.

**Switch statement không bắt buộc luôn có `default`**. Tuy nhiên, nếu muốn xử lý một enum value chưa liệt kê hoặc giúp code rõ ràng khi enum thay đổi, hãy quyết định chính sách `default` phù hợp. Với switch expression, tính đầy đủ của các nhánh có yêu cầu riêng.

## 9. Các wrapper `Byte`, `Short`, `Character`, `Integer`

Bốn lớp bao sau đều được dùng làm selector Java 17 ổn định:

~~~java
Byte boxedByte = 1;
Short boxedShort = 300;
Character boxedChar = 'A';
Integer boxedInt = 7;

switch (boxedByte) {
    case 1 -> System.out.println("Byte one");
    default -> System.out.println("Byte other");
}
switch (boxedShort) {
    case 300 -> System.out.println("Short 300");
    default -> System.out.println("Short other");
}
switch (boxedChar) {
    case 'A' -> System.out.println("Character A");
    default -> System.out.println("Character other");
}
switch (boxedInt) {
    case 7 -> System.out.println("Integer seven");
    default -> System.out.println("Integer other");
}
~~~

Wrapper là tham chiếu đối tượng nhưng selector kiểu này được xử lý theo các giá trị nguyên tương ứng; cần lưu ý **unboxing** khi selector nullable.

Không mở rộng kết luận này thành “mọi wrapper đều được hỗ trợ”: **`Long`**, **`Float`**, **`Double`** và **`Boolean`** không phải selector hợp lệ trong switch Java 17 thông thường.

## 10. Selector kiểu tham chiếu là `null`

Với `String`, enum hoặc wrapper được phép làm selector, Java 17 tiêu chuẩn ném `NullPointerException` nếu selector là `null`; `default` chỉ xử lý **giá trị không khớp case**. Kiểm tra null trước khi switch nếu đầu vào có thể thiếu:

~~~java
if (command == null) {
    System.out.println("missing command");
} else {
    switch (command) {
        case "start" -> System.out.println("start");
        default -> System.out.println("unknown");
    }
}
~~~

Chính sách trả kết quả hoặc ném lỗi với đầu vào thiếu được phân tích ở bài chuyên về `null`.

## 11. Khi nào nên dùng `if` thay cho `switch`?

`switch` phù hợp với nhiều giá trị rời rạc của một selector hợp lệ. Các ngưỡng (`age >= 18`), khoảng số, tổ hợp nhiều điều kiện hoặc ưu tiên thường rõ hơn bằng `if/else if`. Không ép `long` hay `double` sang `int` chỉ để viết switch nếu có thể làm mất thông tin. Bài cuối chương cung cấp quy trình chọn cấu trúc bằng ví dụ nghiệp vụ.

## 12. Chương trình Java 17 hoàn chỉnh, chạy độc lập

Tạo file đúng tên **`SwitchStatementTypesDemo.java`**:

~~~java
public final class SwitchStatementTypesDemo {
    private SwitchStatementTypesDemo() {}

    enum Status {
        NEW, RUNNING, DONE
    }

    static final int OPEN = 1;

    static void showInt(int value) {
        switch (value) {
            case OPEN:
                System.out.println("int=open");
                break;
            case 2:
            case 3:
                System.out.println("int=two-or-three");
                break;
            default:
                System.out.println("int=other");
        }
    }

    static void showByte(byte value) {
        switch (value) {
            case -1 -> System.out.println("byte=negative-one");
            case 127 -> System.out.println("byte=max");
            default -> System.out.println("byte=other");
        }
    }

    static void showShort(short value) {
        switch (value) {
            case 300 -> System.out.println("short=300");
            default -> System.out.println("short=other");
        }
    }

    static void showChar(char value) {
        switch (value) {
            case 'A' -> System.out.println("char=A");
            default -> System.out.println("char=other");
        }
    }

    static void showString(String command) {
        switch (command) {
            case "start" -> System.out.println("string=start");
            case "stop" -> System.out.println("string=stop");
            default -> System.out.println("string=other");
        }
    }

    static void showStatus(Status status) {
        switch (status) {
            case NEW -> System.out.println("enum=new");
            case RUNNING -> System.out.println("enum=running");
            case DONE -> System.out.println("enum=done");
        }
    }

    static void showInteger(Integer value) {
        switch (value) {
            case 7 -> System.out.println("Integer=seven");
            default -> System.out.println("Integer=other");
        }
    }

    static void showWrappers() {
        Byte b = 1;
        Short s = 300;
        Character c = 'A';

        switch (b) {
            case 1 -> System.out.println("Byte=one");
            default -> System.out.println("Byte=other");
        }
        switch (s) {
            case 300 -> System.out.println("Short=300");
            default -> System.out.println("Short=other");
        }
        switch (c) {
            case 'A' -> System.out.println("Character=A");
            default -> System.out.println("Character=other");
        }
    }

    static void showFallThrough(int value) {
        switch (value) {
            case 1:
                System.out.println("fall=one");
                break;
            case 2:
                System.out.println("fall=two");
            case 3:
                System.out.println("fall=three");
                break;
            default:
                System.out.println("fall=other");
        }
    }

    public static void main(String[] args) {
        showInt(1);
        showInt(2);
        showInt(9);

        showByte((byte) -1);
        showByte((byte) 127);
        showShort((short) 300);
        showChar('A');

        showString(new String("start"));
        showString("START");
        showStatus(Status.RUNNING);

        showWrappers();
        showInteger(Integer.valueOf(7));

        showFallThrough(2);

        String missing = null;
        try {
            showString(missing);
        } catch (NullPointerException ex) {
            System.out.println("null String=NullPointerException");
        }

        Integer missingNumber = null;
        try {
            showInteger(missingNumber);
        } catch (NullPointerException ex) {
            System.out.println("null Integer=NullPointerException");
        }
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên; terminal tại thư mục chứa `SwitchStatementTypesDemo.java`. Chương trình không dùng thư viện bên ngoài JDK hoặc preview feature.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 SwitchStatementTypesDemo.java
java -cp . SwitchStatementTypesDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\SwitchStatementTypesDemo.java
java -cp . SwitchStatementTypesDemo
~~~

### Exact expected stdout

~~~text
int=open
int=two-or-three
int=other
byte=negative-one
byte=max
short=300
char=A
string=start
string=other
enum=running
Byte=one
Short=300
Character=A
Integer=seven
fall=two
fall=three
null String=NullPointerException
null Integer=NullPointerException
~~~

**Đọc kết quả:**

1. `int=open` chứng minh `case OPEN` là hằng compile time, dùng được làm nhãn.
2. `int=two-or-three` minh họa nhiều nhãn dùng chung một hành động.
3. `byte`, `short`, `char`, `int` và bốn wrapper tương ứng đều được dùng làm selector.
4. `new String("start")` vẫn khớp `case "start"`: switch so **nội dung String**; `"START"` rơi vào default vì phân biệt hoa/thường.
5. Enum chọn hằng `RUNNING` bằng tên không kèm prefix kiểu.
6. `fall=two` và `fall=three` cho thấy dạng dấu `:` có thể fall-through khi thiếu `break`.
7. Selector `String` hoặc `Integer` null ném NPE; `default` không bắt null trong switch Java 17 ổn định.

## 13. Lỗi thường gặp

| Lỗi hoặc ngộ nhận | Hệ quả | Cách đúng |
|---|---|---|
| `switch(longValue)` trong Java 17 | Không compile | `if/else` hoặc thiết kế mã int rõ |
| `switch(booleanFlag)` | Không compile | `if (flag) ... else ...` |
| `switch(doubleValue)` | Không compile | Điều kiện số thực có chính sách sai số phù hợp |
| `switch(object)` với `Object` đang chứa Integer | Kiểu tĩnh Object không hợp lệ | Dùng selector có kiểu cụ thể |
| `case variable:` với biến runtime | Nhãn không là compile-time constant | Hằng `static final` hoặc biểu thức hằng |
| Hai nhãn case cùng giá trị | Duplicate case compile error | Loại bỏ trùng lặp |
| `case 128` với selector byte | Ngoài miền kiểu byte | Chọn nhãn biểu diễn được |
| Thiếu `break` trong nhánh `case:` | Fall-through ngoài ý muốn | Thêm `break` hoặc dùng `->` |
| Mong `default` xử lý selector null | Null gây NPE | Guard null trước switch |
| Mong `switch(String)` so tham chiếu | Thực tế so nội dung | Hiểu case-sensitive nội dung |
| Viết `case Status.RUNNING` trong switch enum thường | Sai dạng nhãn enum Java 17 | Viết `case RUNNING` |
| Nghĩ dạng `->` luôn là switch expression | Switch statement cũng dùng `->` | Phân biệt ngữ cảnh và mục đích |
| Đọc tài liệu `case null` của phiên bản khác rồi áp dụng trực tiếp | Khác chuẩn Java 17 không preview | Kiểm tra phiên bản/cờ preview |

## 14. Bài tập và đáp án

### Bài A — selector nào hợp lệ?

Xét `byte`, `long`, `Character`, `String`, `boolean`, `Status enum`, `Double`, `Integer`.

**Đáp án:** `byte`, `Character`, `String`, `Status` và `Integer` hợp lệ; `long`, `boolean`, `Double` không hợp lệ với switch thông thường ổn định trong Java 17.

### Bài B — fall-through

~~~java
int x = 1;
switch (x) {
    case 1:
        System.out.println("A");
    case 2:
        System.out.println("B");
        break;
    default:
        System.out.println("C");
}
~~~

**Đáp án:**

~~~text
A
B
~~~

Thiếu `break` sau nhánh 1 nên tiếp tục chạy nhánh 2.

### Bài C — tránh fall-through

Viết lại để `x = 1` chỉ in `A` bằng cú pháp Java 17:

~~~java
switch (x) {
    case 1 -> System.out.println("A");
    case 2 -> System.out.println("B");
    default -> System.out.println("C");
}
~~~

**Đáp án:** Chỉ in `A`. Dạng `->` không fall-through.

### Bài D — String khác tham chiếu

~~~java
String command = new String("stop");
switch (command) {
    case "stop" -> System.out.println("matched");
    default -> System.out.println("other");
}
~~~

**Đáp án:** `matched`, vì switch String so **nội dung**, không phải object identity.

### Bài E — wrapper null

~~~java
Integer code = null;
// switch (code) {
//     case 1 -> System.out.println("one");
//     default -> System.out.println("other");
// }
~~~

**Đáp án:** Khi thực thi switch trên `code` null, ném `NullPointerException`, không chạy default. Phải kiểm tra null trước.

### Bài F — chọn switch hay if?

Yêu cầu: phân loại tuổi `age >= 18 && age < 65`, hoặc xử lý command `"start"`/`"stop"`.

**Đáp án:** dùng `if` cho điều kiện **miền tuổi**, dùng `switch(String)` cho **các giá trị command rời rạc**.

## 15. Checklist và kết luận

- [ ] Biết switch statement chọn nhánh dựa trên selector có **kiểu hỗ trợ**.
- [ ] Nhớ bốn primitive được hỗ trợ: `byte`, `short`, `char`, `int`.
- [ ] Nhớ bốn wrapper tương ứng: `Byte`, `Short`, `Character`, `Integer`.
- [ ] Biết `String` và kiểu `enum` cụ thể cũng dùng được làm selector.
- [ ] Biết `long`, `float`, `double`, `boolean` và wrapper tương ứng không được dùng trực tiếp ở Java 17 ổn định.
- [ ] Nhãn `case` hằng phải hợp lệ về kiểu, giá trị và không bị trùng.
- [ ] Biết switch String so nội dung và phân biệt chữ hoa/thường.
- [ ] Biết nhãn enum thông thường viết `case RUNNING`, không `case Status.RUNNING`.
- [ ] Hiểu `case:` cần `break` khi không muốn fall-through.
- [ ] Hiểu `case ->` không fall-through và **vẫn có thể là statement**.
- [ ] Guard selector null trước switch; `default` không bắt null trong Java 17 không preview.
- [ ] Dùng `if/else` cho điều kiện miền/ràng buộc không thể biểu đạt bằng case constant.
- [ ] Chạy `SwitchStatementTypesDemo.java` trên Java 17+ bằng `javac`/`java`, đối chiếu stdout.

**Kết luận:** Với switch statement Java 17 ổn định, các selector được hỗ trợ là **`byte`/`short`/`char`/`int`**, **bốn wrapper tương ứng**, **`String`** và **`enum`**. Nhãn `case` phải đúng kiểu và quy tắc hằng; dạng dấu `:` có thể fall-through, còn dạng `->` giúp tránh điều đó. Đặc biệt, hãy kiểm tra **null** trước khi switch với kiểu tham chiếu và chọn `if/else` khi cần điều kiện quan hệ/phức tạp.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="switch-statements-002">Nhãn case, default và nhãn trùng lặp</a>

<details>
<summary>Xem chi tiết</summary>

# Nhãn `case`, `default` và nhãn trùng lặp trong Java

> **Java core — language-basics / Switch**  
> **Môi trường:** Java 17+, không bật preview, chỉ dùng thư viện chuẩn.  
> **Mục tiêu:** Chọn đúng nhãn `case`, hiểu `default` và trường hợp không khớp, nhận biết lỗi nhãn trùng; phân biệt cú pháp `case ...:` truyền thống với `case ... ->` hiện đại.

## 1. Nhãn `case` dùng để làm gì?

`switch` đánh giá biểu thức chọn (*selector*) và đối chiếu kết quả với các nhãn. Trong một chuỗi `if/else if`, ta viết điều kiện; với `switch`, ta liệt kê các giá trị được chọn:

~~~java
int code = 2;

switch (code) {
    case 1:
        System.out.println("ONE");
        break;
    case 2:
        System.out.println("TWO");
        break;
    default:
        System.out.println("OTHER");
}
// In: TWO
~~~

Ở đây:

- `case 1:` và `case 2:` là nhãn cho hai giá trị.
- `default:` là điểm bắt đầu khi không nhãn `case` nào khớp.
- `break` thoát `switch` dạng câu lệnh, tránh chạy tiếp từ nhánh đang chọn vào nhánh phía sau.

Một `switch` có thể có nhiều nhãn `case` nhưng **không được có hai nhãn mang cùng giá trị** trong cùng một khối.

## 2. Loại giá trị nào dùng được làm nhãn?

Với `switch` thông thường trong Java 17 (không bật preview), selector hỗ trợ các kiểu nguyên `byte`, `short`, `char`, `int`, các lớp bao tương ứng, `String` và `enum`. Không dùng trực tiếp `long`, `float`, `double` hoặc `boolean` làm selector.

Đối với số nguyên/chuỗi, nhãn `case` thông thường phải là **biểu thức hằng tại thời điểm biên dịch** (*compile-time constant*) với kiểu phù hợp selector; không được là kết quả tính lúc chạy:

~~~java
static final int BASE = 2;

// Hợp lệ trong switch (code):
// case 1:
// case BASE + 1:     // Hằng số 3 vì BASE là constant variable.
// case 'A':          // Ký tự có giá trị số tương ứng; hợp lệ với int.
~~~

Ví dụ không hợp lệ (chỉ minh họa, không đưa vào chương trình chạy):

~~~java
int runtime = 2;
// case runtime:             // Không phải hằng tại compile time.
// case Integer.parseInt("2"): // Lời gọi hàm không là hằng compile time.
~~~

Nếu selector là `byte` thì nhãn nguyên `case 128` không hợp lệ vì ngoài miền giá trị `byte`. Nếu selector là `String` thì dùng nhãn chuỗi hằng như `case "go"`. Với `enum`, nhãn dùng **tên hằng chưa định danh kiểu**, ví dụ `case LOW`, không phải `case Level.LOW` trong switch thông thường của Java 17.

## 3. Nhãn trùng lặp: lỗi ngay khi biên dịch

Nhãn trùng không nhất thiết phải viết giống hệt nhau. Hai biểu thức hằng có **cùng giá trị sau khi tính** cũng trùng:

~~~java
// KHÔNG BIÊN DỊCH — hai nhãn cùng là 2:
// switch (number) {
//     case 2:   System.out.println("first");  break;
//     case 1+1: System.out.println("second"); break;
// }
~~~

Tương tự:

~~~java
// KHÔNG BIÊN DỊCH trong một switch (int):
// case 65:
// case 'A': // Ký tự 'A' có giá trị 65.

// KHÔNG BIÊN DỊCH trong một switch (String):
// case "go":
// case "g" + "o": // Cùng chuỗi hằng "go".
~~~

Các nhãn trùng không trở thành “hai lựa chọn đều đúng” như hai `if` độc lập. Java **từ chối biên dịch** vì hai nhãn trong cùng `switch` không thể phân biệt.

**Cách sửa:** Gộp nhãn nếu nhiều giá trị khác nhau thực sự dùng chung một cách xử lý, hoặc chỉ giữ **một** nhãn cho cùng một giá trị. Không thể chữa hai nhãn trùng bằng cách thêm `break`.

## 4. Gộp nhiều nhãn hợp lệ vào một nhánh

Trong `switch` truyền thống, nhiều `case` liên tiếp có thể dẫn tới cùng thân:

~~~java
switch (code) {
    case 1:
    case 2:
        System.out.println("SMALL");
        break;
    default:
        System.out.println("OTHER");
}
~~~

Trong cú pháp mũi tên Java 17, dùng danh sách nhãn:

~~~java
String group = switch (code) {
    case 1, 2 -> "SMALL";
    case 3 -> "THREE";
    default -> "OTHER";
};
~~~

**`case 1, 2` là hai giá trị khác nhau trong cùng một nhóm** — hoàn toàn hợp lệ. Nhưng `case 1, 1` hoặc đặt thêm `case 2` ở nhánh khác vẫn là nhãn trùng.

Các nhánh `->` **không tự chạy xuyên xuống nhánh sau** (*no fall-through*); có thể dùng `switch` như một biểu thức để trả về giá trị. Đừng trộn dạng `case ...:` và `case ... ->` trong cùng một `switch`.

## 5. Vai trò và vị trí của `default`

`default` được dùng khi **không có nhãn `case` nào khớp**, không phải “nhánh luôn chạy sau mọi case”:

~~~java
int code = 9;
switch (code) {
    case 1:
        System.out.println("ONE");
        break;
    default:
        System.out.println("OTHER"); // code = 9.
}
~~~

Các điều cần nhớ:

- Tối đa **một** nhãn `default` trong một `switch`; khai báo `default` hai lần là lỗi biên dịch.
- Với `switch` **statement**, `default` có thể không có; nếu không nhãn nào khớp và không có `default` thì bỏ qua thân `switch`.
- Với `switch` **expression**, mọi giá trị có thể phải được bao phủ (*exhaustive*); các nhãn `int` hoặc `String` thông thường cần `default`. Riêng switch expression trên `enum` có thể bỏ `default` nếu liệt kê đủ mọi hằng enum.
- Trong `switch` dạng dấu hai chấm `:`, `default` **không bắt buộc nằm cuối**. Nếu thiếu `break`, thực thi có thể tiếp tục từ `default` sang `case` ở sau.
- `default` không xử lý `null` cho `String`/enum/wrapper selector trong Java 17 không bật preview; giá trị selector `null` gây `NullPointerException` trước khi vào nhánh.

Ví dụ cố ý đặt `default` giữa các nhãn để thấy fall-through:

~~~java
static String unusualOrder(int code) {
    StringBuilder result = new StringBuilder();
    switch (code) {
        case 0:
            result.append("ZERO");
            break;
        default:
            result.append("DEFAULT-");
        case 2:
            result.append("TWO");
            break;
    }
    return result.toString();
}
// unusualOrder(0) = "ZERO"
// unusualOrder(2) = "TWO"
// unusualOrder(9) = "DEFAULT-TWO"
~~~

Đây là cú pháp hợp lệ nhưng **không khuyến nghị** khi không chủ ý fall-through. Thực tế nên đặt `default` rõ ràng, dùng `break` với `:` hoặc ưu tiên `->` khi chỉ muốn một nhánh.

## 6. `String`, `enum` và tính tương thích Java 17

`switch` trên `String` đối chiếu **nội dung chuỗi** chứ không phải chỉ so sánh hai tham chiếu bằng `==`; phân biệt chữ hoa/chữ thường:

~~~java
String text = new String("go");

String action = switch (text) {
    case "go" -> "MOVE";
    case "stop" -> "HALT";
    default -> "UNKNOWN";
};
// action = "MOVE".
~~~

Với `enum`:

~~~java
enum Level { LOW, MEDIUM, HIGH }

static String label(Level level) {
    return switch (level) {
        case LOW -> "low";
        case MEDIUM -> "medium";
        case HIGH -> "high";
    };
}
~~~

Biểu thức `switch` trên `Level` đã liệt kê đủ ba hằng, nên không cần `default`. Nếu `level == null`, vẫn ném `NullPointerException`. Hãy kiểm tra `null` **trước switch** nếu dữ liệu có thể thiếu.

Các cú pháp như `case null` hoặc mẫu kiểu (*pattern matching for switch*) không thuộc switch Java 17 tiêu chuẩn khi không bật tính năng preview; không dùng chúng để thay thế `default` trong bài này.

## 7. Chương trình Java 17+ chạy độc lập

Tạo file **SwitchCaseLabelsDemo.java**:

~~~java
public final class SwitchCaseLabelsDemo {
    private static final int BASE = 2;

    enum Level { LOW, MEDIUM, HIGH }

    private static String classic(int code) {
        switch (code) {
            case 1:
            case 2:
                return "SMALL";
            case 3:
                return "THREE";
            default:
                return "OTHER";
        }
    }

    private static String arrow(int code) {
        return switch (code) {
            case 1, 2 -> "SMALL";
            case 3 -> "THREE";
            default -> "OTHER";
        };
    }

    private static String constantCase(int code) {
        return switch (code) {
            case BASE + 1 -> "THREE";
            default -> "OTHER";
        };
    }

    private static String byText(String text) {
        return switch (text) {
            case "go" -> "MOVE";
            case "stop" -> "HALT";
            default -> "UNKNOWN";
        };
    }

    private static String byLevel(Level level) {
        return switch (level) {
            case LOW -> "low";
            case MEDIUM -> "medium";
            case HIGH -> "high";
        };
    }

    private static String unusualOrder(int code) {
        StringBuilder result = new StringBuilder();
        switch (code) {
            case 0:
                result.append("ZERO");
                break;
            default:
                result.append("DEFAULT-");
            case 2:
                result.append("TWO");
                break;
        }
        return result.toString();
    }

    public static void main(String[] args) {
        System.out.println("classic1=" + classic(1));
        System.out.println("classic2=" + classic(2));
        System.out.println("classic3=" + classic(3));
        System.out.println("classic9=" + classic(9));
        System.out.println("arrow2=" + arrow(2));
        System.out.println("arrow3=" + arrow(3));
        System.out.println("arrow9=" + arrow(9));
        System.out.println("constant3=" + constantCase(3));

        System.out.println("stringGo=" + byText(new String("go")));
        System.out.println("stringStop=" + byText("stop"));
        System.out.println("stringOther=" + byText("GO"));
        System.out.println("enumLow=" + byLevel(Level.LOW));
        System.out.println("enumHigh=" + byLevel(Level.HIGH));

        System.out.println("default0=" + unusualOrder(0));
        System.out.println("default2=" + unusualOrder(2));
        System.out.println("default9=" + unusualOrder(9));

        try {
            byText(null);
        } catch (NullPointerException ex) {
            System.out.println("nullSelector=NullPointerException");
        }
    }
}
~~~

Biên dịch và chạy trong thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 SwitchCaseLabelsDemo.java
java SwitchCaseLabelsDemo
~~~

**Expected stdout chính xác:**

~~~text
classic1=SMALL
classic2=SMALL
classic3=THREE
classic9=OTHER
arrow2=SMALL
arrow3=THREE
arrow9=OTHER
constant3=THREE
stringGo=MOVE
stringStop=HALT
stringOther=UNKNOWN
enumLow=low
enumHigh=high
default0=ZERO
default2=TWO
default9=DEFAULT-TWO
nullSelector=NullPointerException
~~~

Ví dụ `default9=DEFAULT-TWO` cố ý cho thấy **fall-through trong switch dạng `:`** khi `default` đứng trước `case 2` mà không có `break`. Đây không phải hành vi của dạng `->`.

## 8. Lỗi thường gặp và bài tập

| Lỗi | Nguyên nhân | Cách tránh |
| --- | --- | --- |
| `case 2` và `case 1 + 1` | Trùng giá trị hằng | Chỉ giữ một giá trị |
| `case value` với biến thường | Giá trị không phải compile-time constant | Dùng hằng hợp lệ hoặc `if` |
| Hai nhãn `default` | `default` phải duy nhất | Chỉ có một nhánh mặc định |
| Quên `break` ở cú pháp `:` | Fall-through ngoài ý muốn | Thêm `break` hoặc dùng `->` |
| Cho rằng `default` bắt `null` | Java 17 không bật preview không làm vậy | Kiểm tra `null` trước |
| Đặt `case Level.LOW` cho enum | Nhãn enum phải viết tên không định danh kiểu | Dùng `case LOW` |
| Bỏ `default` khi switch expression không exhaustive | Không có kết quả cho mọi giá trị | Thêm `default` hoặc bao phủ đủ enum |

**Bài 1.** Viết `switch` để mã 1 và 2 dùng chung kết quả `"SMALL"`, mã 3 là `"THREE"` và mọi mã khác là `"OTHER"`. Thực hiện bằng cả `:` và `->`.

**Bài 2.** Giải thích vì sao trong cùng một switch `int`, hai nhãn `case 3` và `case 1 + 2` bị từ chối dù khác cách viết.

**Bài 3.** Thử thêm nhãn `default` thứ hai vào một `switch` mẫu. Nêu loại lỗi xảy ra và sửa thế nào.

**Bài 4.** Dự đoán `unusualOrder(9)` và `unusualOrder(2)`. Vì sao không giống nhau dù `default` nằm trước `case 2`?

**Bài 5.** Viết `switch` expression trên enum có đủ ba hằng; giải thích khi nào có thể bỏ `default`, và xử lý trước thế nào nếu selector nullable.

### Ghi nhớ

**`case` chọn giá trị hằng hợp lệ, các nhãn trong cùng `switch` không được trùng giá trị.** `default` là nhánh khi không có case khớp, tối đa một nhãn và không tự xử lý `null` trong Java 17 tiêu chuẩn. Dùng nhiều nhãn **khác nhau** để gộp xử lý; nhớ `break` ở cú pháp `:` hoặc dùng `->` để tránh fall-through ngoài ý muốn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="switch-statements-003">Dạng dấu hai chấm và cơ chế rơi tiếp xuống nhánh sau</a>

<details>
<summary>Xem chi tiết</summary>

# `switch` dạng dấu hai chấm và cơ chế rơi tiếp xuống nhánh sau — Java 17+

Trong Java, một `switch` dạng **dấu hai chấm** (*colon-style switch statement*) đặt các nhãn `case ...:` và `default:` trước những câu lệnh sẽ chạy. Điểm cần nhớ là **nhãn `case` không tự kết thúc một nhánh**: khi đã bắt đầu từ nhãn khớp, Java tiếp tục chạy các câu lệnh bên dưới, kể cả qua những nhãn `case` khác, cho đến khi gặp `break`, `return`, `throw` hoặc kết thúc khối `switch`.

Hiện tượng này gọi là **fall-through** (*rơi tiếp xuống nhánh sau*).

~~~java
int code = 1;
switch (code) {
    case 1:
        System.out.println("one");
    case 2:
        System.out.println("two");
        break;
    default:
        System.out.println("other");
}
~~~

Với `code = 1`, chương trình in **cả `one` và `two`**. Lý do: không có `break` sau `case 1:`, nên sau khi in `one`, luồng xử lý tiếp tục tới câu lệnh bên dưới `case 2:` mà **không kiểm tra lại điều kiện case 2**.

## 1. Cú pháp `switch` dạng dấu hai chấm

~~~java
switch (expression) {
    case CONSTANT_1:
        // Các câu lệnh cho nhãn thứ nhất
        break;
    case CONSTANT_2:
        // Các câu lệnh cho nhãn thứ hai
        break;
    default:
        // Trường hợp không nhãn case nào khớp
}
~~~

Quy tắc thực thi:

1. Java đánh giá **biểu thức chọn** `expression` đúng một lần cho lần thực hiện `switch` đó.
2. Tìm **nhãn `case` tương ứng** với giá trị, hoặc chuyển tới `default` nếu không nhãn nào khớp.
3. Bắt đầu chạy **từ vị trí nhãn đã chọn**.
4. Tiếp tục lần lượt qua các câu lệnh bên dưới; những nhãn xuất hiện tiếp theo **không phải hàng rào tự động**.
5. `break;` thông thường thoát khỏi `switch` gần nhất; câu lệnh phía sau khối `switch` được thực hiện tiếp.

**`default` không bắt buộc.** Nếu không có `case` khớp và không có `default`, Java bỏ qua toàn bộ thân `switch`.

### Bảng hành vi điển hình

~~~java
switch (n) {
    case 1:
        System.out.println("A");
    case 2:
        System.out.println("B");
    case 3:
        System.out.println("C");
        break;
    default:
        System.out.println("D");
}
~~~

| Giá trị `n` | Những dòng được in từ `switch` | Giải thích |
| --- | --- | --- |
| 1 | A, B, C | Rơi qua case 2 và case 3 trước khi gặp `break` |
| 2 | B, C | Rơi qua case 3 |
| 3 | C | Dừng tại `break` |
| 9 | D | Không case nào khớp; chạy `default` |

Không nên suy luận rằng chỉ vì biểu thức chọn bằng 1 thì “chỉ câu lệnh của case 1 được chạy”. Trong dạng dấu hai chấm, **`break` mới tạo ranh giới dừng thực thi**.

## 2. Chương trình Java 17+ hoàn chỉnh

Tạo tệp UTF-8 tên **`SwitchColonDemo.java`**:

~~~java
public class SwitchColonDemo {
    static void fallThrough(int value) {
        System.out.println("input=" + value);
        switch (value) {
            case 1:
                System.out.println("one");
            case 2:
                System.out.println("two");
            case 3:
                System.out.println("three");
                break;
            default:
                System.out.println("other");
        }
        System.out.println("after");
    }

    static void withBreak(int value) {
        System.out.println("break-input=" + value);
        switch (value) {
            case 1:
                System.out.println("break=one");
                break;
            case 2:
                System.out.println("break=two");
                break;
            default:
                System.out.println("break=other");
        }
        System.out.println("break-after");
    }

    static String season(int month) {
        switch (month) {
            case 12:
            case 1:
            case 2:
                return "winter";
            case 3:
            case 4:
            case 5:
                return "spring";
            case 6:
            case 7:
            case 8:
                return "summer";
            case 9:
            case 10:
            case 11:
                return "autumn";
            default:
                return "invalid";
        }
    }

    static void defaultInMiddle(int value) {
        System.out.println("middle-input=" + value);
        switch (value) {
            case 1:
                System.out.println("case-one");
                break;
            default:
                System.out.println("default");
            case 2:
                System.out.println("case-two");
                break;
            case 3:
                System.out.println("case-three");
                break;
        }
    }

    static void continueInLoop() {
        for (int i = 0; i < 3; i++) {
            switch (i) {
                case 0:
                    System.out.println("loop-zero");
                    continue;
                case 1:
                    System.out.println("loop-one");
                    break;
                default:
                    System.out.println("loop-two");
            }
            System.out.println("loop-after=" + i);
        }
    }

    static void localScopes(int option) {
        switch (option) {
            case 1: {
                int local = 10;
                System.out.println("scope=" + local);
                break;
            }
            case 2: {
                int local = 20;
                System.out.println("scope=" + local);
                break;
            }
            default:
                System.out.println("scope=other");
        }
    }

    static String state(String code) {
        if (code == null) {
            return "null";
        }
        switch (code) {
            case "ON":
                return "enabled";
            case "OFF":
                return "disabled";
            default:
                return "unknown";
        }
    }

    public static void main(String[] args) {
        System.out.println("== fall-through ==");
        fallThrough(1);
        fallThrough(2);
        fallThrough(3);
        fallThrough(9);

        System.out.println("== break ==");
        withBreak(1);
        withBreak(2);
        withBreak(9);

        System.out.println("== grouped labels ==");
        System.out.println("season1=" + season(1));
        System.out.println("season4=" + season(4));
        System.out.println("season8=" + season(8));
        System.out.println("season10=" + season(10));
        System.out.println("season13=" + season(13));

        System.out.println("== default in middle ==");
        defaultInMiddle(9);
        defaultInMiddle(2);
        defaultInMiddle(1);
        defaultInMiddle(3);

        System.out.println("== switch inside loop ==");
        continueInLoop();

        System.out.println("== scoped cases ==");
        localScopes(1);
        localScopes(2);
        localScopes(7);

        System.out.println("== String selectors ==");
        System.out.println("stateON=" + state("ON"));
        System.out.println("stateNull=" + state(null));
        System.out.println("stateOther=" + state("UNKNOWN"));

        System.out.println("== switch expression with colon ==");
        String result = switch (2) {
            case 1:
                yield "one";
            case 2:
                yield "two";
            default:
                yield "other";
        };
        System.out.println("yieldResult=" + result);
    }
}
~~~

Dùng **JDK 17 trở lên**. Mở PowerShell (Windows) hoặc Bash (Linux/macOS) tại thư mục chứa file:

~~~bash
java -version
javac -version
javac -encoding UTF-8 SwitchColonDemo.java
java SwitchColonDemo
~~~

**Exact expected stdout** của riêng lệnh `java SwitchColonDemo`:

~~~text
== fall-through ==
input=1
one
two
three
after
input=2
two
three
after
input=3
three
after
input=9
other
after
== break ==
break-input=1
break=one
break-after
break-input=2
break=two
break-after
break-input=9
break=other
break-after
== grouped labels ==
season1=winter
season4=spring
season8=summer
season10=autumn
season13=invalid
== default in middle ==
middle-input=9
default
case-two
middle-input=2
case-two
middle-input=1
case-one
middle-input=3
case-three
== switch inside loop ==
loop-zero
loop-one
loop-after=1
loop-two
loop-after=2
== scoped cases ==
scope=10
scope=20
scope=other
== String selectors ==
stateON=enabled
stateNull=null
stateOther=unknown
== switch expression with colon ==
yieldResult=two
~~~

Hai lệnh `java -version` và `javac -version` cho thông tin phụ thuộc phiên bản JDK cài đặt và không nằm trong expected stdout cố định.

## 3. Điểm dừng của luồng fall-through

Nhãn `case:` không tạo ranh giới dừng; `break;` thoát switch gần nhất. `return` rời phương thức, còn `throw` chuyển điều khiển sang xử lý ngoại lệ. Khi switch nằm trong vòng lặp, `continue` tác động lên vòng lặp phù hợp. Các khác biệt về lệnh thoát, switch lồng nhau và vòng lặp được thực hành chi tiết ở bài kế tiếp.

## 4. Rơi tiếp có chủ ý: gom nhiều nhãn `case`

Một ứng dụng dễ hiểu của fall-through là **nhiều nhãn dùng chung một thân lệnh**:

~~~java
switch (month) {
    case 12:
    case 1:
    case 2:
        System.out.println("winter");
        break;
    case 3:
    case 4:
    case 5:
        System.out.println("spring");
        break;
    default:
        System.out.println("other");
}
~~~

Với `month == 12`, Java vào nhãn `case 12:`, đi qua `case 1:`, `case 2:` mà không có câu lệnh nào ở giữa, rồi in `winter`. Đây là **fall-through có chủ ý**, không phải rủi ro bị chạy các hành động khác nhau.

Trong Java 17, các nhãn có cùng thân cũng có thể viết theo dạng danh sách:

~~~java
switch (month) {
    case 12, 1, 2:
        System.out.println("winter");
        break;
    default:
        System.out.println("other");
}
~~~

Dạng dấu hai chấm vẫn cần `break` nếu muốn dừng sau nhóm. Khi nhiệm vụ chỉ là chọn một giá trị và không cần fall-through, `case ... ->` thường biểu đạt ý định rõ hơn.

## 5. `default` không bắt buộc đứng cuối

`default:` là điểm bắt đầu khi **không nhãn `case` nào khớp**, nhưng vị trí viết của nó trong khối `switch` có thể không ở cuối.

~~~java
switch (value) {
    case 1:
        System.out.println("one");
        break;
    default:
        System.out.println("default");
    case 2:
        System.out.println("two");
        break;
}
~~~

Nếu `value == 9`, Java bắt đầu ở `default`, in `default` rồi **rơi tiếp** xuống `case 2:` để in `two` trước khi gặp `break`. Nếu `value == 2`, Java bắt đầu **trực tiếp tại `case 2:`** và chỉ in `two`.

| `value` | Kết quả |
| --- | --- |
| 1 | `one` |
| 2 | `two` |
| 9 | `default`, rồi `two` |

**Khuyến nghị:** thông thường đặt `default` cuối và kết thúc rõ ràng từng nhánh giúp tránh gây nhầm lẫn; đặt `default` ở giữa là hợp lệ nhưng phải xét khả năng fall-through.

## 6. Phạm vi biến cục bộ trong các nhãn `case`

Một nhãn `case` **không tự tạo một scope mới**. Các khai báo biến cục bộ ở những case khác nhau trong cùng khối `switch` có thể xung đột tên, và việc sử dụng trước khi được gán có thể dẫn đến lỗi **definite assignment**.

Cách rõ ràng là tạo một khối riêng cho từng case:

~~~java
switch (option) {
    case 1: {
        int result = 10;
        System.out.println(result);
        break;
    }
    case 2: {
        int result = 20; // Khai báo lại hợp lệ vì ở block khác.
        System.out.println(result);
        break;
    }
    default: {
        System.out.println("unknown");
    }
}
~~~

Đừng nhầm `case:` với khối `{...}`: nhãn là điểm vào của luồng điều khiển; dấu ngoặc nhọn mới tạo phạm vi block rõ ràng. Cách viết có `{ }` còn giúp tránh vô tình đọc một biến chưa được khởi tạo ở đường đi khác.

## 7. Ràng buộc selector và nhãn

Trong Java 17 tiêu chuẩn, switch hỗ trợ `byte`, `short`, `char`, `int` và wrapper tương ứng, `String`, hoặc enum. Nhãn hằng phải hợp lệ ở compile time, tương thích miền giá trị và không trùng. Selector tham chiếu `null` gây `NullPointerException`, không chạy `default`. Các bảng kiểu selector và chính sách null có bài chuyên biệt; ở đây ta dùng chúng làm điều kiện đầu vào của cơ chế dấu hai chấm.

## 8. Đối chiếu nhanh với `case ... ->`

Dạng `case ...:` có thể fall-through và thường cần `break` sau phần thân. Dạng `case ... ->` thực hiện một rule, không chạy sang rule sau; có thể nhóm nhiều giá trị bằng `case 1, 2 -> ...`. Không trộn hai dạng trong một switch. Bài mũi tên giải thích riêng cách dùng block và scope.

## 9. Switch ở trong vòng lặp

Nếu switch nằm trong `for`, thiếu `break` vẫn gây fall-through **bên trong switch**. Trái lại, `continue;` chuyển tới lần lặp kế tiếp và bỏ qua phần còn lại của thân vòng lặp, không nhảy sang nhãn case khác. Bài `break` có trace cụ thể về tương tác giữa switch, vòng lặp và các cấu trúc lồng nhau.

## 10. Lỗi thường gặp

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “Mỗi `case:` tự kết thúc như `if/else if`.” | Không; thiếu `break` có thể rơi tiếp |
| “Java kiểm tra lại điều kiện ở mỗi nhãn sau.” | Không; đã nhảy tới nhãn khớp thì chạy tuần tự |
| “`break` trong switch luôn kết thúc phương thức.” | Chỉ thoát switch gần nhất |
| “`default` đứng giữa sẽ luôn dừng trước case sau.” | Vẫn có thể fall-through |
| “`case 1: case 2:` là lỗi.” | Hợp lệ và dùng để gom nhãn |
| “Hai case có thể khai báo cùng tên biến vì case tạo scope.” | Case không tự tạo block; cần `{ }` khi thích hợp |
| “`switch(null)` tự động chạy `default`.” | Thông thường ném `NullPointerException` trong Java 17 |
| “`case x ->` cũng có fall-through nếu không có `break`.” | Dạng rule `->` không rơi tiếp |
| “`continue` trong switch chuyển sang case tiếp theo.” | Nó chuyển sang lần lặp kế khi switch nằm trong vòng lặp |
| “Trong switch expression, `break` tạo giá trị trả về.” | Dùng `yield` hoặc switch rule thích hợp |

## 11. Bài tập tự kiểm tra

1. `switch(1) { case 1: print("A"); case 2: print("B"); break; }` có thể in gì?  
   *Đáp án:* `AB` theo thứ tự, vì thiếu `break` sau case 1.
2. Nếu thêm `break;` ngay sau `print("A")` thì còn in `B` không?  
   *Đáp án:* Không.
3. Với `case 1: case 2: System.out.println("yes"); break;`, giá trị 1 và 2 có chạy chung phần thân không?  
   *Đáp án:* Có.
4. `default` có bắt buộc đặt cuối khối `switch` không?  
   *Đáp án:* Không; nhưng đặt giữa cần chú ý fall-through.
5. Trong switch có `default: print("D"); case 2: print("B"); break;`, chọn giá trị 9 cho gì?  
   *Đáp án:* In `D` rồi `B`.
6. Dùng `break` trong switch nằm trong vòng `for` có kết thúc luôn vòng lặp không?  
   *Đáp án:* Không; chỉ thoát switch gần nhất.
7. Vì sao nên dùng `{ }` cho mỗi case có khai báo biến cục bộ?  
   *Đáp án:* Tạo scope rõ ràng và tránh xung đột tên/khởi tạo.
8. `switch` dạng mũi tên `case 1 -> ...` có rơi qua case 2 không?  
   *Đáp án:* Không.
9. Nếu selector là `String null` trong Java 17 thông thường, `default` có xử lý nó không?  
   *Đáp án:* Không; phải xử lý null trước để tránh `NullPointerException`.

## Checklist

- [ ] Phân biệt `case ...:` (dạng dấu hai chấm) với `case ... ->` (dạng mũi tên).
- [ ] Hiểu điểm vào `case` và việc **rơi tiếp** qua nhãn sau nếu không dừng.
- [ ] Đặt `break` khi mỗi nhánh cần độc lập; ghi rõ ý định khi cố tình fall-through.
- [ ] Biết gom nhiều nhãn dùng chung phần thân.
- [ ] Không mặc định `default` tạo ranh giới dừng.
- [ ] Dùng khối `{ }` khi khai báo biến cục bộ trong case.
- [ ] Phân biệt `break`, `continue`, `return` và `yield`.
- [ ] Xử lý null selector trước khi dùng `switch` Java 17 thông thường.

**Kết luận:** Điểm khác biệt lớn nhất của `switch` dạng dấu hai chấm là **fall-through**: khi đã vào một nhãn, Java chạy tiếp các câu lệnh phía dưới cho đến khi bị ngắt hoặc hết khối. Dùng `break` có chủ đích, gom nhãn khi cần chung hành vi và cân nhắc dạng `->` để giảm nguy cơ rơi tiếp ngoài ý muốn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="switch-statements-004">break để kết thúc switch statement</a>

<details>
<summary>Xem chi tiết</summary>

# `break` để kết thúc switch statement — Java 17+

## 1. `break` làm gì trong `switch`?

Trong **switch statement** dùng cú pháp truyền thống `case ...:`, lệnh `break;` **kết thúc ngay switch gần nhất đang bao quanh nó**. Chương trình tiếp tục từ câu lệnh **sau** switch, không phải sau nhãn `case` kế tiếp.

~~~java
int choice = 2;
switch (choice) {
    case 1:
        System.out.println("One");
        break;
    case 2:
        System.out.println("Two");
        break;
    default:
        System.out.println("Other");
}
System.out.println("After switch");
~~~

**Exact expected stdout:**

~~~text
Two
After switch
~~~

Nếu thiếu `break;` sau `case 2`, thực thi có thể **rơi xuống nhánh `default`**, dù `choice` không khớp `default`. Đây là *fall-through*, nguồn lỗi thường gặp trong switch dạng dấu hai chấm.

Sau bài, bạn cần phân biệt `break` với `return` và `continue`, biết `break` thoát switch nào khi lồng nhau, khi nào có thể bỏ `break`, và cách dùng `case ... ->` trong Java 17 để tránh fall-through.

## 2. Luồng chạy switch dạng dấu hai chấm

~~~mermaid
flowchart TD
    A["Đánh giá selector"] --> B{"Nhãn case khớp?"}
    B -- "Có" --> C["Bắt đầu chạy từ case khớp"]
    B -- "Không" --> D{"Có default?"}
    D -- "Không" --> H["Sau switch"]
    D -- "Có" --> E["Bắt đầu chạy từ default"]
    C --> F{"Gặp break thoát switch?"}
    E --> F
    F -- "Có" --> H
    F -- "Không" --> G["Tiếp tục câu lệnh và có thể fall-through"]
    G --> F
~~~

Các bước quan trọng:

1. Selector được đánh giá.
2. Java xác định nhãn `case` khớp hoặc `default` nếu không có `case` khớp.
3. Với dạng `case:`, thực thi bắt đầu tại nhãn đã chọn và **tiến theo thứ tự câu lệnh**, không tự dừng ở cuối nhóm lệnh của nhãn.
4. Gặp `break;` không có nhãn thì thoát **switch hoặc vòng lặp gần nhất bao quanh**.
5. Nếu chạy tới hết switch mà không gặp `break`, chương trình cũng ra khỏi switch theo cách bình thường.

`break` **không phải bắt buộc về cú pháp ở mọi `case`**. Nó quan trọng khi cần ngăn chạy tiếp sang các nhãn phía sau.

## 3. Ví dụ thiếu `break`

~~~java
switch (2) {
    case 2: System.out.println("Two");
    case 3: System.out.println("Three"); break;
    default: System.out.println("Other");
}
~~~

Kết quả lần lượt là `Two`, `Three`: sau khi chọn case 2, Java chạy tiếp đến `break` mà không so lại nhãn case 3. Trọng tâm ở đây là vị trí đặt lệnh thoát; bài dạng dấu hai chấm đã mô tả chi tiết mọi đường fall-through.

## 4. Đặt `break` ở đúng vị trí

Nếu chỉ muốn xử lý một mã, đặt `break` sau các câu lệnh của mã đó:

~~~java
switch (code) {
    case 1:
        handleOne();
        break;
    case 2:
        handleTwo();
        break;
    default:
        handleOther();
}
~~~

Ví dụ trên các hàm `handleOne`, `handleTwo`, `handleOther` là những hàm nghiệp vụ do bạn tự định nghĩa; đoạn này minh họa **vị trí `break`**, không phải một chương trình độc lập.

Có thể viết cả khối để giữ biến cục bộ theo phạm vi:

~~~java
switch (code) {
    case 1: {
        String message = "one";
        System.out.println(message);
        break;
    }
    case 2: {
        String message = "two";
        System.out.println(message);
        break;
    }
    default:
        System.out.println("other");
}
~~~

Ngoặc `{ ... }` **không làm `break` chỉ thoát khối ngoặc**: `break;` vẫn kết thúc switch bao quanh.

**Ghi chú:** nếu một `case` đã kết thúc bằng `return` hoặc `throw`, không cần đặt thêm `break` phía sau — dòng `break` đó có thể thành *unreachable statement* và gây lỗi biên dịch.

## 5. Dừng sau thân chung của nhiều nhãn

~~~java
switch (day) {
    case 6:
    case 7:
        System.out.println("Weekend");
        break;
    default:
        System.out.println("Weekday");
}
~~~

Hai nhãn liên tiếp cố ý dùng chung thân lệnh; `break` nằm **sau thân chung**. Nếu một nhánh thực hiện hành động riêng rồi rơi tiếp sang nhánh khác, cần xác nhận đó là chủ ý và ghi chú rõ.

## 6. `default` có cần `break`?

Nếu `default` là nhãn cuối và switch kết thúc ngay sau thân của nó thì không cần `break`. Khi `default` nằm giữa các `case:`, thiếu `break` vẫn gây fall-through. Hãy quyết định dựa trên **đường thực thi**, không dựa vào tên nhãn. Bài nhãn `case/default` có ví dụ đối chiếu vị trí.

## 7. `break` khác `return` và `continue`

| Câu lệnh | Hiệu ứng chính | Sau khi thực hiện |
|---|---|---|
| `break;` | Thoát switch hoặc vòng lặp gần nhất | Tiếp tục sau cấu trúc đã thoát |
| `return;` / `return value;` | Thoát **toàn bộ phương thức** | Không chạy câu lệnh sau switch trong cùng method |
| `continue;` | Bỏ phần còn lại của **lần lặp**, chỉ hợp lệ trong vòng lặp | Chuyển sang bước cập nhật/điều kiện của vòng lặp |
| `throw ...;` | Ném ngoại lệ | Chuyển điều khiển theo cơ chế exception |

Ví dụ `break` chỉ kết thúc switch:

~~~java
static void printChoice(int choice) {
    switch (choice) {
        case 1:
            System.out.println("one");
            break;
        default:
            System.out.println("other");
    }
    System.out.println("method continues");
}
~~~

Ví dụ `return` kết thúc method:

~~~java
static String label(int choice) {
    switch (choice) {
        case 1:
            return "ONE";
        default:
            return "OTHER";
    }
}
~~~

**Không thể thay máy móc `break` bằng `return`**: nếu phương thức còn xử lý sau switch, `return` sẽ bỏ qua phần xử lý đó.

`continue;` **không thay thế `break;` trong switch độc lập**; nó cần có vòng lặp bao quanh. Khi switch ở bên trong vòng lặp, `continue;` áp dụng cho vòng lặp ấy.

## 8. Switch bên trong vòng lặp: `break` không thoát vòng lặp

~~~java
for (int i = 0; i < 3; i++) {
    switch (i) {
        case 1:
            System.out.println("case one");
            break; // Thoát switch, KHÔNG thoát for.
        default:
            System.out.println("other " + i);
    }
    System.out.println("after switch " + i);
}
~~~

**Exact expected stdout:**

~~~text
other 0
after switch 0
case one
after switch 1
other 2
after switch 2
~~~

Khi `i == 1`, `break;` kết thúc switch, sau đó `System.out.println("after switch " + i)` vẫn chạy và vòng `for` tiếp tục.

Nếu cần thoát chính vòng lặp, đặt điều kiện `break` ở **ngoài switch**, hoặc dùng **labeled break** có chủ đích:

~~~java
outer:
for (int i = 0; i < 5; i++) {
    switch (i) {
        case 2:
            break outer; // Thoát hẳn vòng lặp có nhãn outer.
        default:
            System.out.println(i);
    }
}
~~~

**Exact expected stdout:**

~~~text
0
1
~~~

Sử dụng nhãn khi thực sự cần thoát một cấu trúc bao ngoài; không lạm dụng để thay cho việc thiết kế vòng lặp rõ ràng.

## 9. Switch lồng nhau: `break` thoát switch gần nhất

~~~java
int outer = 1;
int inner = 7;

switch (outer) {
    case 1:
        switch (inner) {
            case 7:
                System.out.println("inner seven");
                break; // Chỉ thoát switch(inner).
            default:
                System.out.println("inner other");
        }
        System.out.println("after inner");
        break; // Thoát switch(outer).
    default:
        System.out.println("outer other");
}
~~~

**Exact expected stdout:**

~~~text
inner seven
after inner
~~~

Nếu bỏ `break` của switch ngoài, có thể tiếp tục fall-through sang nhãn phía sau của switch ngoài. **Mỗi switch có luồng điều khiển của riêng nó**.

## 10. Đối chiếu với nhánh mũi tên

Trong switch statement Java 17, `case 1 -> action();` không cần `break` để chống fall-through. Nếu nhánh cần nhiều lệnh thì dùng block `{ ... }`. Khi sửa switch dạng `case:` cũ, chỉ bỏ `break` khi đã chủ đích chuyển cú pháp sang rule mũi tên.

## 11. `break` khác `yield`

`break;` thoát switch statement hoặc vòng lặp thích hợp, không tạo giá trị. Switch **expression** với block `case 2 -> { ... }` cần `yield value;` trên đường đi bình thường. Không thay `yield` bằng `break` hoặc `break value`. Những đường đi có điều kiện, kết quả và ngoại lệ thuộc bài `yield`.

## 12. Ví dụ Java 17 hoàn chỉnh, chạy bằng CLI

Tạo file đúng tên **`SwitchBreakDemo.java`**:

~~~java
public final class SwitchBreakDemo {
    private SwitchBreakDemo() {}

    static String classify(int code) {
        String result;
        switch (code) {
            case 1:
                result = "ONE";
                break;
            case 2:
            case 3:
                result = "TWO_OR_THREE";
                break;
            default:
                result = "OTHER";
        }
        return result;
    }

    static void fallThrough(int code) {
        switch (code) {
            case 1:
                System.out.println("fall:one");
                // Fall through intentionally.
            case 2:
                System.out.println("fall:two");
                break;
            default:
                System.out.println("fall:other");
        }
        System.out.println("fall:after-switch");
    }

    static void arrow(int code) {
        switch (code) {
            case 1 -> System.out.println("arrow:one");
            case 2, 3 -> System.out.println("arrow:two-or-three");
            default -> System.out.println("arrow:other");
        }
    }

    static String withReturn(int code) {
        switch (code) {
            case 0:
                return "ZERO";
            default:
                return "NONZERO";
        }
    }

    static String nested(int outer, int inner) {
        String result;
        switch (outer) {
            case 1:
                result = "outer-one";
                switch (inner) {
                    case 7:
                        result = "inner-seven";
                        break; // Only exits the inner switch.
                    default:
                        result = "inner-other";
                }
                result += "-after-inner";
                break; // Exits the outer switch.
            case 2:
                result = "outer-two";
                break;
            default:
                result = "outer-other";
        }
        return result;
    }

    static void loopWithSwitch() {
        for (int i = 0; i < 3; i++) {
            switch (i) {
                case 1:
                    System.out.println("loop:case-one");
                    break;
                default:
                    System.out.println("loop:other-" + i);
            }
            System.out.println("loop:after-switch-" + i);
        }
    }

    static void labeledBreak() {
        outer:
        for (int i = 0; i < 5; i++) {
            switch (i) {
                case 2:
                    break outer;
                default:
                    System.out.println("labeled:" + i);
            }
        }
        System.out.println("labeled:after-loop");
    }

    static void blockBreak(int code) {
        switch (code) {
            case 1: {
                String message = "block:one";
                System.out.println(message);
                break;
            }
            default:
                System.out.println("block:other");
        }
        System.out.println("block:after-switch");
    }

    public static void main(String[] args) {
        System.out.println("classify 1=" + classify(1));
        System.out.println("classify 2=" + classify(2));
        System.out.println("classify 3=" + classify(3));
        System.out.println("classify 9=" + classify(9));

        fallThrough(1);
        fallThrough(2);
        arrow(1);
        arrow(3);
        arrow(9);

        System.out.println("return 0=" + withReturn(0));
        System.out.println("return 8=" + withReturn(8));

        System.out.println("nested 1/7=" + nested(1, 7));
        System.out.println("nested 1/8=" + nested(1, 8));
        System.out.println("nested 2/7=" + nested(2, 7));

        loopWithSwitch();
        labeledBreak();
        blockBreak(1);

        int code = 2;
        String expression = switch (code) {
            case 1 -> "ONE";
            case 2 -> {
                String value = "TWO";
                yield value;
            }
            default -> "OTHER";
        };
        System.out.println("expression=" + expression);
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên, terminal tại thư mục chứa `SwitchBreakDemo.java`; không cần Maven/Gradle, không sử dụng tính năng preview hoặc thư viện ngoài JDK.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 SwitchBreakDemo.java
java -cp . SwitchBreakDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\SwitchBreakDemo.java
java -cp . SwitchBreakDemo
~~~

**Exact expected stdout:**

~~~text
classify 1=ONE
classify 2=TWO_OR_THREE
classify 3=TWO_OR_THREE
classify 9=OTHER
fall:one
fall:two
fall:after-switch
fall:two
fall:after-switch
arrow:one
arrow:two-or-three
arrow:other
return 0=ZERO
return 8=NONZERO
nested 1/7=inner-seven-after-inner
nested 1/8=inner-other-after-inner
nested 2/7=outer-two
loop:other-0
loop:after-switch-0
loop:case-one
loop:after-switch-1
loop:other-2
loop:after-switch-2
labeled:0
labeled:1
labeled:after-loop
block:one
block:after-switch
expression=TWO
~~~

### Đọc kết quả

- `classify` dùng `break` để bảo đảm mỗi mã chỉ được gán một kết quả.
- `fallThrough(1)` in cả `fall:one` và `fall:two` theo **chủ ý** thiếu `break`; `fallThrough(2)` chỉ in `fall:two`.
- `arrow` không fall-through, dù không có `break;`.
- `withReturn` trả trực tiếp từ phương thức, không chạy thêm câu lệnh trong method.
- `nested` cho thấy `break` của switch trong không tự thoát switch ngoài.
- `loopWithSwitch` vẫn chạy `for` sau khi switch dùng `break;`.
- `labeledBreak` thoát vòng `for` được gắn nhãn `outer`.
- `blockBreak` xác nhận `break` rời switch ngay cả khi nằm trong `case` có `{ ... }`.
- `yield` cung cấp `"TWO"` cho switch expression.

## 13. Bảng lỗi thường gặp

| Sai lầm | Hậu quả | Cách xử lý |
|---|---|---|
| Quên `break` sau `case:` | Chạy tiếp câu lệnh của case sau | Thêm `break;` hoặc dùng `->` |
| Nghĩ Java kiểm tra lại case sau fall-through | Diễn giải sai kết quả | Hiểu rằng thực thi chỉ tiếp tục tuần tự |
| Nghĩ `default` luôn tự kết thúc switch | Default đặt giữa vẫn fall-through | Thêm `break` nếu cần |
| Đặt `break` ngay sau `return` | Code không thể đạt tới, có thể lỗi biên dịch | Chọn một cơ chế thoát đúng mục đích |
| Dùng `break` rồi mong thoát cả phương thức | Method vẫn tiếp tục sau switch | Dùng `return` nếu muốn thoát method |
| Dùng `break` trong switch bên trong `for` để kết thúc `for` | Chỉ thoát switch | Đặt break ngoài switch hoặc dùng nhãn |
| Nghĩ `break` trong switch con thoát cả switch cha | Chỉ thoát cấu trúc gần nhất | Quản lý riêng từng mức hoặc dùng return khi phù hợp |
| Dùng `continue` trong switch không có vòng lặp | Không hợp lệ | `break` để thoát switch |
| Đặt `break;` để trả giá trị của switch expression | Không cung cấp kết quả | Dùng `yield` cho nhánh có block |
| Trộn cú pháp `case:` với `case ->` trong cùng switch | Lỗi biên dịch | Chọn một kiểu nhãn nhất quán |
| Dùng fall-through cho nhiều nhánh giống nhau nhưng không ghi chú | Người đọc tưởng thiếu break | Dùng nhãn rỗng liên tiếp hoặc `case 1, 2 ->` |

## 14. Bài tập có đáp án

### Bài A — dự đoán fall-through

~~~java
int x = 1;
switch (x) {
    case 1:
        System.out.println("A");
    case 2:
        System.out.println("B");
        break;
    default:
        System.out.println("C");
}
~~~

**Đáp án:**

~~~text
A
B
~~~

`case 1` khớp nhưng không có `break`, nên chạy tiếp lệnh của `case 2`.

### Bài B — chỉ chạy case đã chọn

Viết lại ví dụ trên để `x == 1` chỉ in A.

**Một đáp án:**

~~~java
switch (x) {
    case 1:
        System.out.println("A");
        break;
    case 2:
        System.out.println("B");
        break;
    default:
        System.out.println("C");
}
~~~

Cũng có thể dùng nhánh `case 1 -> System.out.println("A");` trong Java 17.

### Bài C — `break` trong vòng lặp

~~~java
for (int i = 0; i < 2; i++) {
    switch (i) {
        case 0:
            break;
        default:
            System.out.println("default");
    }
    System.out.println("after " + i);
}
~~~

**Đáp án:**

~~~text
after 0
default
after 1
~~~

Lệnh `break;` chỉ kết thúc switch ở vòng đầu tiên, không kết thúc `for`.

### Bài D — switch lồng nhau

~~~java
switch (1) {
    case 1:
        switch (7) {
            case 7:
                System.out.println("inner");
                break;
        }
        System.out.println("outer continues");
        break;
}
~~~

**Đáp án:**

~~~text
inner
outer continues
~~~

Break trong chỉ thoát switch trong; câu lệnh của case ngoài vẫn chạy.

### Bài E — switch expression

Cần tạo một `String` từ switch có nhánh xử lý gồm nhiều câu lệnh. Nên dùng `break` hay `yield`?

**Đáp án:** trong switch **expression**, dùng `yield value;` để trả giá trị từ block:

~~~java
String label = switch (code) {
    case 1 -> {
        String value = "ONE";
        yield value;
    }
    default -> "OTHER";
};
~~~

## 15. Checklist và kết luận

- [ ] Hiểu `break;` kết thúc switch gần nhất và chương trình tiếp tục **sau switch**.
- [ ] Biết `case:` không tự dừng; thiếu `break` có thể fall-through.
- [ ] Biết khi nào fall-through là chủ ý (gộp case hoặc luồng đã được thiết kế).
- [ ] Không xem `default` là nơi tự động dừng nếu nó không đứng cuối.
- [ ] Phân biệt `break` thoát switch với `return` thoát phương thức.
- [ ] Phân biệt `break` với `continue` của vòng lặp.
- [ ] Hiểu `break` trong switch lồng `for` không thoát vòng `for`.
- [ ] Hiểu `break` trong switch con không tự thoát switch cha.
- [ ] Biết `case ->` của Java 17 **không fall-through**, không cần `break` để chống rơi nhánh.
- [ ] Biết switch expression dùng `yield` để trả giá trị từ block.
- [ ] Không đặt `break` sau một `return` không thể quay lại.
- [ ] Chạy `SwitchBreakDemo.java` bằng JDK 17+ và đối chiếu exact stdout.

**Kết luận:** Với switch statement dạng `case:`, lệnh `break;` là cách rõ ràng để **ngăn fall-through** và rời switch tại vị trí mong muốn. Nó không rời phương thức hay tự thoát vòng lặp bao ngoài. Trong Java 17, cú pháp `case ->` là lựa chọn tốt khi mỗi case chỉ nên chạy một nhánh mà không cần `break`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="switch-statements-005">Dạng mũi tên -&gt; và phạm vi của khối nhánh</a>

<details>
<summary>Xem chi tiết</summary>

# Dạng mũi tên `->` và phạm vi của khối nhánh trong Java

> **Java core — language-basics / Switch**  
> **Môi trường:** JDK 17+, không bật preview, không dùng thư viện ngoài.  
> **Mục tiêu:** Viết đúng `switch` dạng `case ... ->`, phân biệt `switch` **statement** và **expression**, hiểu phạm vi biến trong mỗi khối `{ ... }`, biết khi nào cần `yield` và vì sao không xảy ra *fall-through* như dạng `case ...:`.

## 1. Quy tắc cơ bản của dạng mũi tên

Dạng truyền thống sử dụng nhãn `case ...:` và thường cần `break`. Dạng mũi tên `case ... ->` được hỗ trợ chính thức ở Java 17 và **chỉ chạy nhánh được chọn**, không tự chuyển sang mã của nhánh tiếp theo:

~~~java
int code = 2;
switch (code) {
    case 1 -> System.out.println("ONE");
    case 2 -> System.out.println("TWO");
    default -> System.out.println("OTHER");
}
// Chỉ in TWO.
~~~

Cấu trúc `switch` này là một **statement**, tức thao tác điều khiển, không phải biểu thức trả về giá trị.

Một nhánh mũi tên trong switch statement có thể là một **statement expression** (ví dụ lời gọi phương thức), **block `{...}`** hoặc câu lệnh `throw`. Nếu phải thực hiện nhiều câu lệnh trong nhánh, đặt chúng trong một khối:

~~~java
switch (code) {
    case 1 -> {
        System.out.println("ONE");
        System.out.println("Audited");
    }
    case 2 -> System.out.println("TWO");
    default -> System.out.println("OTHER");
}
~~~

Không cần `break` sau nhánh `->`. Việc thêm ngoặc nhọn là để nhóm nhiều câu lệnh và xác định phạm vi biến, **không phải để ngăn fall-through** như dạng `:`.

## 2. Statement và expression đều có thể dùng `->`

`switch (code) { case 1 -> System.out.println("ONE"); default -> System.out.println("OTHER"); }` là **statement** thực hiện thao tác. Ngược lại, `String label = switch (code) { case 1 -> "ONE"; default -> "OTHER"; };` là **expression** tạo giá trị.

Cả hai đều không fall-through. Expression còn phải bao phủ các trường hợp và tạo kết quả đúng kiểu; những bài sau phân tích riêng điều này. Ở đây trọng tâm là rule mũi tên và phạm vi biến.

## 3. Phạm vi biến cục bộ của khối nhánh

Mỗi khối `{ ... }` của một nhánh mũi tên là một **phạm vi cục bộ riêng**:

~~~java
int code = 1;
switch (code) {
    case 1 -> {
        int amount = 10;
        System.out.println(amount);
    }
    case 2 -> {
        int amount = 20; // HỢP LỆ: biến khác, trong khối khác.
        System.out.println(amount);
    }
    default -> System.out.println("OTHER");
}
~~~

Biến `amount` của nhánh 1 không nhìn thấy ở nhánh 2 hoặc sau `switch`. Nhánh 2 có thể khai báo **tên giống hệt** vì phạm vi hai biến không giao nhau. Biến cục bộ chỉ tồn tại trong **khối khai báo của nó**; nó không tự trở thành biến dùng chung giữa các case.

~~~java
// KHÔNG BIÊN DỊCH nếu đặt sau switch ở ví dụ trên:
// System.out.println(amount); // Cannot find symbol.
~~~

Nếu dữ liệu phải được sử dụng **sau `switch`**, chọn một trong hai cách:

- Với dữ liệu là **một giá trị**, ưu tiên `String result = switch (...) { ... };`.
- Với statement cần cập nhật biến có sẵn, khai báo biến ngoài switch rồi gán rõ ràng theo từng nhánh; đảm bảo mọi đường đi cần thiết đều gán giá trị.

Không dựa vào thụt lề để tạo scope: Java dựa vào dấu `{ }` và vị trí khai báo. Việc dùng cùng tên ở nhiều nhánh giúp minh họa phạm vi, nhưng thực tế vẫn nên đặt tên có ý nghĩa.

## 4. Khi block cần trả giá trị

Trong switch expression, nhánh đơn có thể là `case 0 -> "ZERO";`. Nếu nhánh cần nhiều lệnh, block trả kết quả qua `yield`:

~~~java
String result = switch (code) {
    case 2 -> {
        int scaled = code * 10;
        yield "GROUP-" + scaled;
    }
    default -> "OTHER";
};
~~~

Switch statement có block thì chỉ thực hiện lệnh và **không cần** `yield`. Bài `yield` phân tích đường đi, `break` và `throw` chi tiết hơn.

## 5. Gộp nhãn, `default`, `throw` và nhánh không được chọn

Có thể nhóm nhiều giá trị khác nhau trên một nhánh mũi tên:

~~~java
String group = switch (code) {
    case 1, 2 -> "SMALL";
    case 3 -> "THREE";
    default -> "OTHER";
};
~~~

`case 1, 2` không chạy nhánh hai lần; đó là **một nhánh** được chọn khi code là 1 **hoặc** 2. Các nhãn trùng giá trị trong cùng switch bị từ chối khi biên dịch.

Một nhánh biểu thức có thể ném ngoại lệ, thay vì tạo giá trị:

~~~java
static String positiveLabel(int value) {
    return switch (value) {
        case -1 -> throw new IllegalArgumentException("negative");
        case 0 -> "ZERO";
        default -> {
            int twice = value * 2;
            yield "DOUBLE=" + twice;
        }
    };
}
~~~

Nếu `value == 0` thì **không chạy** khối `default`. Nếu `value == -1` thì ném ngoại lệ từ nhánh được chọn. Trong cả hai trường hợp, Java không chạy mã của nhánh khác chỉ vì nó xuất hiện bên dưới.

Trong Java 17 không bật preview, selector `String`/`enum`/wrapper có giá trị `null` có thể gây `NullPointerException`; `default` không tự bắt null. Hãy kiểm tra null trước nếu dữ liệu cho phép vắng mặt.

## 6. So sánh phạm vi và fall-through với dạng dấu hai chấm

`switch` dạng dấu hai chấm dùng **nhóm câu lệnh** `case ...:`; nếu thiếu `break`, mã có thể chạy xuyên tới nhóm tiếp theo:

~~~java
static String colonDemo(int code) {
    StringBuilder log = new StringBuilder();
    switch (code) {
        case 1:
            log.append("ONE");
        case 2:
            log.append("+TWO");
            break;
        default:
            log.append("OTHER");
    }
    return log.toString();
}
// colonDemo(1) = "ONE+TWO" (fall-through).
// colonDemo(2) = "+TWO".
~~~

Ngược lại, `case 1 -> log.append("ONE");` trong switch statement mũi tên **không** tự thực hiện case 2.

| Vấn đề | Dạng `case ...:` | Dạng `case ... ->` |
| --- | --- | --- |
| Fall-through ngoài ý muốn | Có thể xảy ra nếu thiếu `break` | Không có |
| Khối cục bộ `{...}` | Nên thêm khi cần scope riêng | Rõ ràng theo từng nhánh block |
| Nhiều nhãn dùng chung | Nhiều `case` liên tiếp | `case 1, 2 ->` |
| Nhiều lệnh trong nhánh | Các statement tới `break` hoặc nhóm tiếp | Viết `{ statement1; statement2; }` |

Với case dạng dấu hai chấm, các biến khai báo trực tiếp trong cùng khối switch có thể gây xung đột tên hoặc lỗi *definite assignment* khi luồng nhảy vào nhãn khác. Đặt khối `{...}` cho từng nhóm cũng giúp tránh nhầm scope trong dạng truyền thống.

**Không trộn** `case ...:` và `case ... ->` trong cùng một `switch`.

## 7. Ví dụ Java 17+ chạy độc lập

Tạo file **ArrowSwitchScopeDemo.java**:

~~~java
public final class ArrowSwitchScopeDemo {
    enum State { NEW, RUNNING, DONE }

    private static String statement(int code) {
        StringBuilder log = new StringBuilder();
        switch (code) {
            case 0 -> log.append("ZERO");
            case 1 -> {
                int local = code + 10;
                log.append("ONE=").append(local);
            }
            case 2 -> {
                int local = code + 20; // Hợp lệ: scope riêng.
                log.append("TWO=").append(local);
            }
            default -> log.append("UNKNOWN");
        }
        return log.toString();
    }

    private static String expression(int code) {
        return switch (code) {
            case 0 -> "ZERO";
            case 1, 2 -> {
                int scaled = code * 10;
                yield "GROUP-" + scaled;
            }
            default -> "OTHER";
        };
    }

    private static String byState(State state) {
        return switch (state) {
            case NEW -> "new";
            case RUNNING -> "running";
            case DONE -> "done";
        };
    }

    private static String colonDemo(int code) {
        StringBuilder log = new StringBuilder();
        switch (code) {
            case 1:
                log.append("ONE");
            case 2:
                log.append("+TWO");
                break;
            default:
                log.append("OTHER");
        }
        return log.toString();
    }

    private static String guarded(int value) {
        return switch (value) {
            case -1 -> throw new IllegalArgumentException("negative");
            case 0 -> "ZERO";
            default -> {
                int twice = value * 2;
                yield "DOUBLE=" + twice;
            }
        };
    }

    public static void main(String[] args) {
        System.out.println("stmt0=" + statement(0));
        System.out.println("stmt1=" + statement(1));
        System.out.println("stmt2=" + statement(2));
        System.out.println("stmt9=" + statement(9));

        System.out.println("expr0=" + expression(0));
        System.out.println("expr1=" + expression(1));
        System.out.println("expr2=" + expression(2));
        System.out.println("expr9=" + expression(9));

        System.out.println("enumNew=" + byState(State.NEW));
        System.out.println("enumDone=" + byState(State.DONE));

        System.out.println("colon1=" + colonDemo(1));
        System.out.println("colon2=" + colonDemo(2));
        System.out.println("colon9=" + colonDemo(9));

        System.out.println("guard0=" + guarded(0));
        System.out.println("guard3=" + guarded(3));
        try {
            guarded(-1);
        } catch (IllegalArgumentException ex) {
            System.out.println("guardNegative=IllegalArgumentException");
        }
    }
}
~~~

Biên dịch/chạy tại thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 ArrowSwitchScopeDemo.java
java ArrowSwitchScopeDemo
~~~

**Expected stdout chính xác:**

~~~text
stmt0=ZERO
stmt1=ONE=11
stmt2=TWO=22
stmt9=UNKNOWN
expr0=ZERO
expr1=GROUP-10
expr2=GROUP-20
expr9=OTHER
enumNew=new
enumDone=done
colon1=ONE+TWO
colon2=+TWO
colon9=OTHER
guard0=ZERO
guard3=DOUBLE=6
guardNegative=IllegalArgumentException
~~~

Hai nhánh statement của `code=1`/`code=2` đều khai báo biến `local` mà không xung đột vì có scope riêng. Các dòng `expr1`/`expr2` minh họa `yield` từ block. `colon1=ONE+TWO` đối chiếu fall-through của `:` với việc `->` chỉ thực hiện một nhánh.

## 8. Lỗi thường gặp và bài tập

| Trường hợp | Cách xử lý |
| --- | --- |
| Muốn thực hiện nhiều statement sau `->` | Đặt chúng trong `{ ... }` |
| Đọc biến cục bộ của nhánh ở bên ngoài khối | Khai báo biến dùng chung ở ngoài hoặc trả giá trị bằng switch expression |
| Khối của switch expression không trả giá trị | Viết `yield value;` trên đường đi hoàn thành bình thường |
| Cố dùng `break value;` thay `yield` | Dùng `yield value;` |
| Lo phải thêm `break` sau nhánh `->` | Không cần; arrow rule không fall-through |
| Muốn nhóm nhiều giá trị | `case 1, 2 -> ...` |
| Selector nullable trong Java17 | Kiểm tra `null` trước switch |

**Bài 1.** Viết `switch` statement dùng `->` với mã 1, 2, mặc định. Ở mỗi nhánh, thêm hai câu lệnh in; xác định có cần `break` không.

**Bài 2.** Trong hai khối `case 1 -> { int x = 1; ... }` và `case 2 -> { int x = 2; ... }`, tại sao cùng tên `x` không gây lỗi? Thử dùng `x` sau switch để xem vấn đề scope.

**Bài 3.** Chuyển `switch` statement tính nhãn thành `switch` expression. Khi một nhánh cần tạo biến rồi trả giá trị, dùng từ khóa nào?

**Bài 4.** Dự đoán kết quả của `colonDemo(1)` và giải thích vì sao switch dạng `->` không có cùng hiện tượng.

**Bài 5.** Viết switch expression trên enum chứa đủ các hằng để không cần `default`. Kiểm tra riêng trường hợp selector `null` trong Java 17.

### Ghi nhớ

**`case ... ->` không fall-through.** Một nhánh có nhiều câu lệnh cần `{...}`, cũng là nơi giới hạn phạm vi biến cục bộ. Với **switch expression**, nhánh đơn có thể trả biểu thức trực tiếp; nhánh block nhiều lệnh dùng **`yield`** để trả giá trị. `switch statement` thực hiện thao tác, còn `switch expression` tính giá trị và phải bao phủ các trường hợp hợp lệ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="switch-statements-006">Switch expression trả về một giá trị</a>

<details>
<summary>Xem chi tiết</summary>

# Switch expression trả về một giá trị — Java 17+

Từ Java 14, `switch` không chỉ là **câu lệnh rẽ nhánh** (*switch statement*) mà còn có dạng **biểu thức** (*switch expression*) để **tạo ra một giá trị**. Java 17 hỗ trợ đầy đủ tính năng này, không cần bật preview.

~~~java
int month = 7;
String season = switch (month) {
    case 12, 1, 2 -> "winter";
    case 3, 4, 5 -> "spring";
    case 6, 7, 8 -> "summer";
    case 9, 10, 11 -> "autumn";
    default -> "invalid";
};
// season == "summer"
~~~

Biểu thức `switch` có thể đứng bên phải phép gán, trong `return` hoặc làm đối số cho phương thức. **Điểm khác với switch statement:** toàn bộ cấu trúc `switch (...) { ... }` được đánh giá thành **một giá trị**, chứ không chỉ điều khiển các câu lệnh.

## 1. Cú pháp và quy tắc chính

Dạng thông dụng nhất là các **switch rule** dùng dấu mũi tên `->`:

~~~java
String label = switch (number) {
    case 0 -> "zero";
    case 1, 2 -> "small";
    default -> "other";
};
~~~

Java đánh giá `number`, chọn đúng nhãn tương ứng rồi đánh giá biểu thức sau `->`. **Không xảy ra fall-through** từ nhánh này sang nhánh sau và **không cần `break`** để tránh rơi tiếp.

| Thuộc tính | Switch expression |
| --- | --- |
| Vai trò | Biểu thức tạo giá trị, có thể gán hoặc `return` |
| Giá trị nhánh đơn | `case X -> expression;` |
| Nhánh nhiều câu lệnh | `case X -> { ... yield value; }` |
| Nhiều nhãn cùng kết quả | `case A, B -> value;` |
| Tính đầy đủ | Phải **exhaustive**: mọi giá trị đầu vào có đường xử lý |
| Fall-through với dạng `->` | Không |
| Dấu `;` sau `}` | Cần khi toàn bộ cấu trúc nằm trong câu lệnh gán |

Những nhánh có kiểu khác nhau phải thỏa mãn **quy tắc kiểu của switch expression** và ngữ cảnh sử dụng. Không thể tùy ý trả một kiểu bất kỳ ở từng nhánh rồi giả định Java luôn chuyển chúng thành cùng kiểu.

## 2. Demo Java 17+ chạy trực tiếp

Tạo file UTF-8 tên **`SwitchExpressionDemo.java`**:

~~~java
public class SwitchExpressionDemo {
    enum Day {
        MON, TUE, WED, THU, FRI, SAT, SUN
    }

    static String category(int number) {
        return switch (number) {
            case 0 -> "zero";
            case 1, 2 -> "small";
            default -> "other";
        };
    }

    static String kind(Day day) {
        return switch (day) {
            case MON, TUE, WED, THU, FRI -> "work";
            case SAT, SUN -> "rest";
        };
    }

    static int score(int level) {
        return switch (level) {
            case 0 -> 0;
            case 1, 2 -> {
                int computed = level * 10;
                System.out.println("computed=" + computed);
                yield computed;
            }
            default -> -1;
        };
    }

    static String direction(int code) {
        return switch (code) {
            case 1:
                yield "left";
            case 2:
                yield "right";
            default:
                yield "unknown";
        };
    }

    static String traditional(int code) {
        String result;
        switch (code) {
            case 1:
                result = "left";
                break;
            case 2:
                result = "right";
                break;
            default:
                result = "unknown";
        }
        return result;
    }

    static String safeStatus(String input) {
        if (input == null) {
            return "missing";
        }
        return switch (input) {
            case "ON" -> "enabled";
            case "OFF" -> "disabled";
            default -> "unknown";
        };
    }

    static String requireNonNegative(int number) {
        return switch (number) {
            case 0 -> "zero";
            default -> {
                if (number < 0) {
                    throw new IllegalArgumentException("negative");
                }
                yield "positive";
            }
        };
    }

    public static void main(String[] args) {
        System.out.println("== arrow rules ==");
        System.out.println("category0=" + category(0));
        System.out.println("category2=" + category(2));
        System.out.println("category9=" + category(9));

        System.out.println("== exhaustive enum ==");
        System.out.println("monday=" + kind(Day.MON));
        System.out.println("sunday=" + kind(Day.SUN));

        System.out.println("== yield ==");
        System.out.println("score0=" + score(0));
        System.out.println("score2=" + score(2));
        System.out.println("score9=" + score(9));

        System.out.println("== colon expression ==");
        System.out.println("direction1=" + direction(1));
        System.out.println("direction9=" + direction(9));

        System.out.println("== statement comparison ==");
        System.out.println("traditional2=" + traditional(2));

        System.out.println("== String and null ==");
        System.out.println("statusON=" + safeStatus("ON"));
        System.out.println("statusOther=" + safeStatus("PAUSE"));
        System.out.println("statusNull=" + safeStatus(null));

        System.out.println("== exceptions ==");
        System.out.println("nonNegative0=" + requireNonNegative(0));
        System.out.println("nonNegative3=" + requireNonNegative(3));
        try {
            requireNonNegative(-1);
        } catch (IllegalArgumentException ex) {
            System.out.println("caught=" + ex.getMessage());
        }

        var inferred = switch (1) {
            case 1 -> "yes";
            default -> "no";
        };
        System.out.println("inferred=" + inferred);
    }
}
~~~

Mở PowerShell hoặc Bash tại thư mục chứa file; yêu cầu **JDK 17 trở lên**:

~~~bash
java -version
javac -version
javac -encoding UTF-8 SwitchExpressionDemo.java
java SwitchExpressionDemo
~~~

**Exact expected stdout** của lệnh `java SwitchExpressionDemo` (không tính thông tin phiên bản JDK):

~~~text
== arrow rules ==
category0=zero
category2=small
category9=other
== exhaustive enum ==
monday=work
sunday=rest
== yield ==
score0=0
computed=20
score2=20
score9=-1
== colon expression ==
direction1=left
direction9=unknown
== statement comparison ==
traditional2=right
== String and null ==
statusON=enabled
statusOther=unknown
statusNull=missing
== exceptions ==
nonNegative0=zero
nonNegative3=positive
caught=negative
inferred=yes
~~~

Chương trình sử dụng Java chuẩn; không cần thư viện bên thứ ba. Kết quả trên được xác định theo các nhánh và thứ tự thực thi của Java 17.

## 3. Nhánh có nhiều lệnh trả kết quả thế nào?

Với `case -> expression`, chính biểu thức sau mũi tên là kết quả. Nếu cần tính toán hoặc ghi log, dùng block và `yield`: `case 1 -> { int x = 10; yield x * 2; }`. `yield` kết thúc **switch expression**, không rời toàn bộ phương thức như `return`.

Switch expression cũng cho phép nhãn `case:`, ví dụ `case 1: yield "ONE";`. Bài chuyên về từ khóa `yield` đi sâu vào đường đi có điều kiện và lỗi thiếu kết quả.

## 4. Biểu thức phải bao phủ mọi trường hợp

Với `int` và `String`, thông thường cần `default` để xử lý giá trị chưa liệt kê. Với enum, liệt kê đủ mọi hằng có thể làm switch expression exhaustive mà không cần `default`. Việc thêm hằng enum sau này có thể khiến mã phải bổ sung case khi biên dịch lại. Quy tắc chứng minh độ bao phủ và chọn kiểu kết quả được phân tích ở bài riêng.

## 5. Dạng `->` khác `case:` và switch statement

| Cấu trúc | Có tạo ra giá trị? | Có fall-through? | Cách kết thúc |
| --- | --- | --- | --- |
| `switch (x) { case 1: ... break; }` | Không: **statement** | Có thể | `break`, `return` hoặc hết khối |
| `switch (x) { case 1 -> ... }` | Không nếu dùng như **statement** | Không | Kết thúc switch rule |
| `var y = switch (x) { case 1 -> v; ... };` | Có: **expression** | Không | Giá trị biểu thức hoặc `yield` |
| `var y = switch (x) { case 1: yield v; ... };` | Có: **expression** | Dạng dấu hai chấm có luồng thực thi tuần tự | `yield`/`throw` hoặc đường thực thi hợp lệ khác |

Dấu mũi tên `->` **không đồng nghĩa switch expression**: Java 17 cũng cho phép switch **statement** với `case ->`. Phân biệt dựa vào **ngữ cảnh có đang lấy một giá trị từ switch hay không**.

Trong một khối `switch`, không trộn nhãn `case ...:` với `case ... ->`. Với switch expression dạng dấu hai chấm, mọi đường chạy bình thường phải tạo được giá trị theo quy tắc Java.

## 6. Kiểu kết quả, `var` và `null`

~~~java
var label = switch (option) {
    case 1 -> "start";
    default -> "other";
};
~~~

Ở đây `label` suy ra kiểu `String` từ switch expression; `var` không “đổi kiểu linh hoạt” theo nhánh được chạy. Trình biên dịch quyết định kiểu theo **các nhánh và ngữ cảnh đích**.

`switch` Java 17 thông thường không xem `String null` hay wrapper số null như một nhãn mặc định:

~~~java
String value = null;
// String result = switch (value) {
//     case "ON" -> "enabled";
//     default -> "unknown";
// };
// Khi chạy: NullPointerException với selector null.
~~~

Nếu cần xử lý null, kiểm tra **trước** khi đưa vào `switch`; phương thức `safeStatus` trong demo làm điều này.

## 7. Bẫy thường gặp

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “`switch` luôn là statement.” | Java 17 còn có switch **expression** |
| “Có dấu `->` nghĩa là chắc chắn switch expression.” | Switch statement cũng dùng được `->` |
| “Nhánh `->` cần `break`.” | Không có fall-through, thường không cần |
| “Trong khối `-> { ... }` dùng `return` để tạo giá trị.” | Dùng **`yield`** |
| “`yield` tương đương `return` khỏi phương thức.” | `yield` trả giá trị từ switch expression |
| “Switch expression có thể bỏ mọi nhánh còn lại mà không cần default.” | Phải **exhaustive** |
| “`default` tự bắt null selector.” | Không trong switch truyền thống Java 17 |
| “`var` thay đổi kiểu ở runtime theo case.” | Kiểu xác định khi biên dịch |
| “Có thể trộn `case X:` và `case Y ->`.” | Không được trộn trong cùng switch |

## 8. Tự kiểm tra

1. `int n = switch (2) { case 1 -> 10; case 2 -> 20; default -> 0; };` cho gì?  
   *Đáp án:* `20`.
2. Nhánh `case 1 -> { int x = 5; yield x * 2; }` tạo giá trị nào?  
   *Đáp án:* `10`.
3. `yield` có thoát luôn phương thức bao ngoài không?  
   *Đáp án:* Không; nó đưa giá trị ra khỏi switch expression.
4. Có thể dùng `case 1: yield "one";` trong switch expression không?  
   *Đáp án:* Có.
5. Switch expression trên `int` có một nhánh `case 1` nhưng không có `default` có đầy đủ không?  
   *Đáp án:* Không.
6. Dạng `case 1 -> System.out.println("x");` có nhất thiết là switch expression không?  
   *Đáp án:* Không; đó có thể là switch statement.
7. Vì sao cần xử lý `String null` trước switch Java 17 thường?  
   *Đáp án:* Selector null có thể gây `NullPointerException`.

## Checklist

- [ ] Hiểu switch **expression** tạo giá trị để gán, `return` hoặc truyền vào hàm.
- [ ] Dùng `case -> value` cho nhánh đơn; dùng `yield` khi nhánh là khối nhiều lệnh.
- [ ] Nhớ dạng `->` không rơi tiếp xuống case sau.
- [ ] Bảo đảm nhánh bao phủ đầy đủ; chọn `default` hoặc liệt kê toàn bộ enum.
- [ ] Phân biệt `yield` với `return` và `break`.
- [ ] Không nhầm switch statement dùng `->` với switch expression.
- [ ] Hiểu kiểu kết quả xác định khi biên dịch và xử lý selector null khi cần.

**Kết luận:** Switch expression giúp lựa chọn **một giá trị** trực tiếp từ nhiều trường hợp, giảm việc khai báo biến rồi gán trong từng nhánh. Trong Java 17, hãy ưu tiên `->` cho nhánh độc lập, dùng `yield` khi cần khối xử lý, và giữ tính **đầy đủ** của các trường hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="switch-statements-007">yield trong khối nhánh của switch expression</a>

<details>
<summary>Xem chi tiết</summary>

# `yield` trong khối nhánh của switch expression — Java 17+

## 1. Mục tiêu: nhánh switch tạo ra một giá trị

Từ Java 14, **switch expression** là tính năng ổn định, nên Java 17 sử dụng trực tiếp, **không cần `--enable-preview`**. Khác với switch statement, switch expression **tạo ra một kết quả** để gán, trả về hoặc dùng trong biểu thức khác.

~~~java
int code = 2;
String label = switch (code) {
    case 1 -> "ONE";
    case 2 -> {
        String message = "TWO";
        yield message;
    }
    default -> "OTHER";
};
System.out.println(label); // TWO
~~~

Ở `case 2 -> { ... }`, khối chứa nhiều câu lệnh. Lệnh **`yield message;` cung cấp giá trị `"TWO"` của toàn bộ switch expression** và kết thúc nhánh đó. Đây là mục đích chính của `yield`.

Mục tiêu bài học:

- Phân biệt nhánh `-> value` với `-> { ... yield value; }`.
- Hiểu `yield` khác `break` và `return`.
- Biết dùng `yield` khi nhánh cần tính toán, kiểm tra điều kiện hoặc tạo biến tạm.
- Đảm bảo các đường đi hợp lệ **tạo được giá trị** hoặc **ném ngoại lệ**.
- Biết cú pháp switch expression với nhãn `case:` và cách xử lý kiểu kết quả, `default`, enum.

## 2. Khi nào không cần `yield`?

Một nhánh mũi tên chỉ cần **một biểu thức** thì viết trực tiếp kết quả:

~~~java
int number = 1;
String name = switch (number) {
    case 1 -> "ONE";
    case 2 -> "TWO";
    default -> "OTHER";
};
~~~

Nếu muốn vừa ghi log vừa tính kết quả, dùng **khối nhánh** và `yield`:

~~~java
String name = switch (number) {
    case 1 -> {
        System.out.println("Matched one");
        yield "ONE";
    }
    case 2 -> "TWO";
    default -> "OTHER";
};
~~~

**Exact stdout với `number = 1`:**

~~~text
Matched one
~~~

Giá trị cuối của biểu thức switch là `"ONE"`; chương trình trên chưa in biến `name`.

**Không phải cú pháp đúng:**

~~~java
// String s = switch (number) {
//     case 1 -> {
//         "ONE"; // Không thay yield; string literal không phải statement expression hợp lệ.
//     }
//     default -> "OTHER";
// };
~~~

Khối `{ ... }` của switch expression **không tự lấy giá trị của biểu thức cuối** như một số ngôn ngữ khác. Khi cần kết quả từ khối, dùng `yield result;` rõ ràng.

## 3. Vai trò chính xác của `yield`

Cú pháp:

~~~java
yield expression;
~~~

Ý nghĩa:

1. Đánh giá `expression` sau từ khóa `yield`.
2. Dùng giá trị thu được làm kết quả của **switch expression chứa nó**.
3. Chuyển điều khiển ra khỏi switch expression để tiếp tục phần chương trình xung quanh.

Ví dụ:

~~~java
int base = 6;
int computed = switch (base) {
    case 6 -> {
        int doubleBase = base * 2;
        int extra = 3;
        yield doubleBase + extra;
    }
    default -> 0;
};
System.out.println(computed); // 15
~~~

`yield` không phải khai báo biến và **không gán trực tiếp** vào `computed`; phép gán xảy ra vì switch expression đã trả giá trị 15.

**Một `yield` kết thúc đường đi được thực thi.** Các câu lệnh nằm sau `yield` trong cùng một đường đi không thể được thực thi; nếu compiler xác định câu lệnh sau là unreachable, chương trình sẽ không biên dịch.

## 4. `yield`, `break`, `return`, `throw`: không hoán đổi cho nhau

| Cấu trúc | Mục đích | Tác động |
|---|---|---|
| `yield value;` | Cung cấp kết quả của **switch expression** | Rời switch expression, tiếp tục biểu thức/câu lệnh bao ngoài |
| `break;` | Thoát switch **statement** hoặc vòng lặp | Không tạo ra giá trị cho switch expression |
| `return value;` | Trả kết quả của **phương thức** | Rời phương thức, không chỉ rời switch |
| `throw exception;` | Báo lỗi qua ngoại lệ | Không trả giá trị bình thường; switch expression có thể dùng một nhánh `throw` |

Trong switch statement truyền thống:

~~~java
switch (code) {
    case 1:
        System.out.println("one");
        break; // Kết thúc switch statement, không trả giá trị.
    default:
        System.out.println("other");
}
~~~

Trong switch expression:

~~~java
String result = switch (code) {
    case 1 -> {
        yield "ONE"; // Giá trị của switch expression.
    }
    default -> "OTHER";
};
~~~

**Sai:**

~~~java
// String result = switch (code) {
//     case 1 -> { break; } // Không dùng break để kết thúc switch expression.
//     default -> "OTHER";
// };
// String result = switch (code) {
//     case 1 -> { break "ONE"; } // Java 17 không có cú pháp break value.
//     default -> "OTHER";
// };
~~~

Muốn rời toàn bộ phương thức sớm, đặt lệnh `return` bên ngoài switch expression hoặc thiết kế các nhánh trả giá trị rồi `return switch (...)`:

~~~java
static String describe(int code) {
    return switch (code) {
        case 1 -> "ONE";
        default -> "OTHER";
    };
}
~~~

## 5. `yield` trong `if/else` bên trong khối nhánh

Không phải nhánh nào cũng tính giá trị theo một công thức đơn giản. Có thể dùng điều kiện phụ:

~~~java
static String sign(int value) {
    return switch (Integer.signum(value)) {
        case -1 -> {
            yield "NEGATIVE";
        }
        case 0 -> "ZERO";
        default -> {
            yield "POSITIVE";
        }
    };
}
~~~

Hoặc sử dụng nhiều `yield` ở những đường đi loại trừ nhau:

~~~java
static int normalize(int input) {
    return switch (input) {
        case 0 -> 0;
        default -> {
            if (input < 0) {
                yield -1;
            }
            yield 1;
        }
    };
}
~~~

Khi `input < 0`, `yield -1;` kết thúc switch expression; câu `yield 1;` chỉ thực thi khi điều kiện trước false.

**Không cần** biến kết quả trung gian chỉ để lưu sau mỗi nhánh nếu một `yield` ở cuối khối diễn đạt rõ hơn. Nhưng với nhánh dài, nên cân nhắc tách logic thành một method giúp dễ kiểm thử.

## 6. Mỗi đường đi phải tạo ra kết quả hoặc ném ngoại lệ

Switch expression phải có cách cho ra giá trị trên mọi đường đi có thể **hoàn thành bình thường**. Ví dụ sau thiếu `yield` khi `value <= 0`:

~~~java
// int result = switch (code) {
//     case 1 -> {
//         if (value > 0) {
//             yield value;
//         }
//         // LỖI: có đường đi hết khối mà không tạo giá trị.
//     }
//     default -> 0;
// };
~~~

Cách sửa:

~~~java
int result = switch (code) {
    case 1 -> {
        if (value > 0) {
            yield value;
        }
        yield 0;
    }
    default -> 0;
};
~~~

Một nhánh **có thể ném ngoại lệ** thay vì trả giá trị:

~~~java
int result = switch (code) {
    case 1 -> 100;
    case 2 -> {
        if (value < 0) {
            throw new IllegalArgumentException("negative value");
        }
        yield value * 2;
    }
    default -> 0;
};
~~~

Đây là hợp lệ: đường đi có `throw` không hoàn thành bình thường, còn đường đi hợp lệ có `yield`.

**Lỗi cần tránh:** viết `yield;` (không có giá trị). Switch expression cần một biểu thức sau `yield`; không có cú pháp `yield;` đơn lẻ.

## 7. Cú pháp dấu hai chấm `case:` trong switch expression

Switch expression vẫn có thể dùng dạng nhóm câu lệnh `case VALUE:`, nhưng khi nhánh tính ra giá trị cần **`yield`**:

~~~java
static String word(int code) {
    return switch (code) {
        case 1:
            yield "ONE";
        case 2:
        case 3:
            String message = "TWO_OR_THREE";
            yield message;
        default:
            yield "OTHER";
    };
}
~~~

Với `code=2`, chương trình trả `"TWO_OR_THREE"`.

**Khác dạng mũi tên:**

- `case 2 -> "TWO";`: kết quả viết ngay sau `->`.
- `case 2 -> { yield "TWO"; }`: phải `yield` từ khối.
- `case 2: yield "TWO";`: nhóm câu lệnh kiểu truyền thống, dùng `yield`.

Trong dạng `case:`, **fall-through vẫn là điều cần xét** nếu một đường đi không kết thúc bằng `yield`/`throw` trước nhãn tiếp theo. Với switch expression, nên viết rõ ràng để không có fall-through khó hiểu.

**Không trộn `case:` và `case ->` trong cùng một switch block.**

## 8. `yield` phải trả giá trị tương thích kiểu đích

Compiler kiểm tra mọi nhánh có tạo giá trị tương thích ngữ cảnh kết quả. Đích `double` có thể nhận `int` trong một nhánh:

~~~java
double amount = switch (code) {
    case 1 -> { yield 10; }
    default -> 2.5;
};
~~~

Với `code == 1`, `amount` là `10.0`. `yield` không bỏ qua kiểm tra kiểu; nên chọn kiểu kết quả tường minh khi nghiệp vụ cần thống nhất. Bài tiếp theo đi sâu vào target typing và chuyển kiểu số.

## 9. Bao phủ trường hợp và `default`

Switch expression phải exhaustive: với `int`/`String` thường có `default`; với enum có thể liệt kê đủ các hằng. Điều kiện này **độc lập** với việc từng block cần `yield` hoặc `throw` trên mọi đường đi phù hợp. `default` không tự xử lý selector `null` trong Java 17 tiêu chuẩn. Bài kế tiếp tập trung vào chứng minh độ bao phủ và kiểu kết quả.

## 10. `break` trong vòng lặp lồng ở nhánh không thay `yield`

Một nhánh mũi tên có thể chứa **vòng lặp**, và `break;` bên trong vòng lặp đó chỉ kết thúc **vòng lặp gần nhất**. Sau vòng lặp vẫn cần cung cấp kết quả cho switch expression:

~~~java
int count = switch (code) {
    case 1 -> {
        int sum = 0;
        for (int i = 1; i <= 5; i++) {
            if (i == 3) {
                break; // Thoát for, không trả giá trị switch.
            }
            sum += i;
        }
        yield sum;
    }
    default -> 0;
};
~~~

Khi `code == 1`, vòng lặp cộng `1 + 2` rồi dừng, `yield sum;` trả **3**.

**Nguyên tắc:** mỗi cấu trúc điều khiển có mục đích riêng; `break` của vòng lặp không thay thế `yield` của switch expression.

## 11. Phạm vi biến và tác dụng phụ trong nhánh

Biến khai báo trong block rule `case -> { ... }` có phạm vi ở block đó:

~~~java
String result = switch (code) {
    case 1 -> {
        String message = "first";
        yield message;
    }
    default -> {
        String message = "other"; // Hợp lệ: block khác.
        yield message;
    }
};
~~~

Đặt biến tạm trong khối giúp tránh xung đột tên và dễ bảo trì.

Chỉ nhánh được chọn mới được đánh giá runtime:

~~~java
static int trace(String name, int value) {
    System.out.println("evaluated " + name);
    return value;
}

int n = switch (code) {
    case 1 -> {
        yield trace("one", 10);
    }
    default -> {
        yield trace("other", 20);
    }
};
~~~

Với `code == 1`, chỉ in `evaluated one`, không gọi nhánh default.

Tuy nhiên, **không nên dựa vào side effect để xác định kết quả nghiệp vụ** nếu có thể tách việc ghi log/validation khỏi phép tính. `yield` làm rõ giá trị được trả, không tự làm thao tác bên trong nhánh thành atomic hoặc thread-safe.

## 12. Chương trình Java 17 hoàn chỉnh

Lưu file đúng tên **`SwitchYieldDemo.java`**:

~~~java
public final class SwitchYieldDemo {
    private SwitchYieldDemo() {}

    enum Channel { WEB, MOBILE, API }

    static String channelName(Channel channel) {
        return switch (channel) {
            case WEB -> "BROWSER";
            case MOBILE -> {
                String label = "PHONE";
                yield label;
            }
            case API -> "SERVICE";
        };
    }

    static String grade(int score) {
        if (score < 0 || score > 100) {
            return "INVALID";
        }
        return switch (score / 10) {
            case 10, 9 -> "A";
            case 8, 7 -> {
                String label = "B";
                yield label;
            }
            case 6, 5 -> "C";
            default -> "D";
        };
    }

    static String fromColon(int code) {
        return switch (code) {
            case 1:
                yield "ONE";
            case 2:
            case 3:
                String label = "TWO_OR_THREE";
                yield label;
            default:
                yield "OTHER";
        };
    }

    static int sign(int value) {
        return switch (value) {
            case 0 -> {
                System.out.println("zero branch");
                yield 0;
            }
            default -> {
                if (value < 0) {
                    yield -1;
                }
                yield 1;
            }
        };
    }

    static int sumUntilThree(int code) {
        return switch (code) {
            case 1 -> {
                int sum = 0;
                for (int i = 1; i <= 5; i++) {
                    if (i == 3) {
                        break;
                    }
                    sum += i;
                }
                yield sum;
            }
            default -> 0;
        };
    }

    static double promoted(int code) {
        return switch (code) {
            case 1 -> {
                yield 10;  // int: chuyển thành double.
            }
            default -> 2.5;
        };
    }

    static int checkedDouble(int value) {
        return switch (value) {
            case 0 -> 0;
            default -> {
                if (value < 0) {
                    throw new IllegalArgumentException("negative");
                }
                yield value * 2;
            }
        };
    }

    static int trace(String name, int value) {
        System.out.println("evaluated " + name);
        return value;
    }

    static int traceChoice(int code) {
        return switch (code) {
            case 1 -> {
                yield trace("one", 10);
            }
            default -> {
                yield trace("other", 20);
            }
        };
    }

    static void statementExample(int code) {
        switch (code) {
            case 1 -> System.out.println("statement:one");
            default -> System.out.println("statement:other");
        }
    }

    public static void main(String[] args) {
        System.out.println("channel WEB=" + channelName(Channel.WEB));
        System.out.println("channel MOBILE=" + channelName(Channel.MOBILE));
        System.out.println("channel API=" + channelName(Channel.API));

        System.out.println("grade -1=" + grade(-1));
        System.out.println("grade 49=" + grade(49));
        System.out.println("grade 50=" + grade(50));
        System.out.println("grade 70=" + grade(70));
        System.out.println("grade 90=" + grade(90));
        System.out.println("grade 100=" + grade(100));

        System.out.println("colon 1=" + fromColon(1));
        System.out.println("colon 2=" + fromColon(2));
        System.out.println("colon 3=" + fromColon(3));
        System.out.println("colon 4=" + fromColon(4));

        System.out.println("sign 0=" + sign(0));
        System.out.println("sign -5=" + sign(-5));
        System.out.println("sign 5=" + sign(5));

        System.out.println("loop 1=" + sumUntilThree(1));
        System.out.println("loop 9=" + sumUntilThree(9));
        System.out.println("numeric promotion=" + promoted(1));

        System.out.println("checked 7=" + checkedDouble(7));
        try {
            System.out.println(checkedDouble(-2));
        } catch (IllegalArgumentException ex) {
            System.out.println("checked -2=" + ex.getMessage());
        }

        System.out.println("trace result=" + traceChoice(1));
        statementExample(1);
        statementExample(2);
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên, mở terminal tại thư mục chứa `SwitchYieldDemo.java`; không cần Maven/Gradle, thư viện ngoài JDK hay cờ preview.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 SwitchYieldDemo.java
java -cp . SwitchYieldDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\SwitchYieldDemo.java
java -cp . SwitchYieldDemo
~~~

### Exact expected stdout

~~~text
channel WEB=BROWSER
channel MOBILE=PHONE
channel API=SERVICE
grade -1=INVALID
grade 49=D
grade 50=C
grade 70=B
grade 90=A
grade 100=A
colon 1=ONE
colon 2=TWO_OR_THREE
colon 3=TWO_OR_THREE
colon 4=OTHER
zero branch
sign 0=0
sign -5=-1
sign 5=1
loop 1=3
loop 9=0
numeric promotion=10.0
checked 7=14
checked -2=negative
evaluated one
trace result=10
statement:one
statement:other
~~~

**Giải thích nhanh:**

1. `channelName` xử lý đầy đủ enum; nhánh MOBILE tạo biến rồi `yield`, nhưng không cần `default`.
2. `grade` kiểm tra miền 0–100 trước khi switch, nên `grade(100)` là A và `grade(-1)` là INVALID.
3. `fromColon` minh họa dạng `case:` của switch expression vẫn dùng được `yield`.
4. `sign(0)` in `zero branch` **trước** dòng `sign 0=0` vì lời gọi được đánh giá trước khi in toàn biểu thức nối chuỗi.
5. `sumUntilThree(1)` cộng 1 và 2; `break` chỉ rời `for`, `yield` trả tổng 3.
6. `promoted(1)` trả 10.0 theo kiểu double của switch expression trong ngữ cảnh này.
7. `checkedDouble(-2)` ném ngoại lệ, không phải `yield` từ đường đi lỗi.
8. Chỉ có `evaluated one`: nhánh default của `traceChoice(1)` không được đánh giá.
9. `statementExample` dùng `case ->` nhưng **không tạo giá trị**, khác với các phương thức trả `switch`.

## 13. Lỗi thường gặp

| Lỗi hoặc nhầm lẫn | Vấn đề | Cách sửa |
|---|---|---|
| `case 1 -> { "ONE"; }` | Không tạo giá trị; literal không là statement hợp lệ | `yield "ONE";` |
| `case 1 -> { break; }` | Không dùng `break` để kết thúc switch expression | `yield value;` |
| `break "ONE";` | Java 17 không có break-with-value | `yield "ONE";` |
| Quên `yield` ở một đường đi trong block | Switch expression có thể hoàn thành mà không có kết quả | Bảo đảm mọi đường đi yield/throw |
| `yield;` | Thiếu biểu thức giá trị | `yield expression;` |
| Nghĩ `return` và `yield` giống nhau | `return` rời method, `yield` rời switch expression | Chọn đúng mức điều khiển |
| Nhánh `->` luôn phải dùng `yield` | Biểu thức đơn giản đã trả kết quả trực tiếp | `case 1 -> "ONE";` |
| Nghĩ nhánh `->` có fall-through | Rule `->` không fall-through | Dùng nhánh riêng hoặc gộp `case 1, 2` |
| Dùng `case:` mà quên tác động fall-through | Có thể chạy tới nhóm sau | Thiết kế đường đi yield/throw rõ |
| Thiếu `default` cho switch expression trên `int` | Không bao phủ mọi giá trị | Thêm `default` |
| Cố ép các nhánh trả kiểu không tương thích | Không compile | Chọn kiểu chung hợp lệ/target type |
| Dùng `break` trong vòng lặp rồi tưởng đã trả switch | Chỉ rời vòng lặp | `yield` sau vòng lặp |
| Đưa `case null` của pattern switch mới vào code Java 17 thường | Khác chế độ/tính năng | Kiểm tra null trước khi switch |

## 14. Bài tập có đáp án

### Bài A — thêm `yield`

Điền phần còn thiếu để nhánh tính toán trả tổng:

~~~java
int n = 3;
int value = switch (n) {
    case 3 -> {
        int sum = 1 + 2 + 3;
        yield sum;
    }
    default -> 0;
};
System.out.println(value);
~~~

**Đáp án:** dùng `yield sum;`, stdout:

~~~text
6
~~~

### Bài B — dự đoán thứ tự in

~~~java
int n = 1;
String value = switch (n) {
    case 1 -> {
        System.out.println("inside");
        yield "ONE";
    }
    default -> "OTHER";
};
System.out.println(value);
~~~

**Đáp án:**

~~~text
inside
ONE
~~~

### Bài C — hai đường đi trong cùng block

Yêu cầu: `n > 0` trả 1, còn lại trả -1:

~~~java
int result = switch (code) {
    case 1 -> {
        if (n > 0) {
            yield 1;
        }
        yield -1;
    }
    default -> 0;
};
~~~

**Đáp án:** cả hai đường đi của case 1 đều trả giá trị qua `yield`.

### Bài D — phân biệt switch statement và expression

~~~java
switch (code) {
    case 1 -> System.out.println("one");
    default -> System.out.println("other");
}
~~~

**Đáp án:** đây là **switch statement** vì được dùng để thực hiện câu lệnh; không cần yield. Để tạo giá trị, viết `String result = switch (code) { case 1 -> "ONE"; default -> "OTHER"; };`.

### Bài E — `break` trong vòng lặp

Vì sao `break;` của vòng `for` nằm trong block case không thay thế `yield`?

**Đáp án:** `break` không nhãn thoát **vòng lặp gần nhất**. Sau đó, block case vẫn phải dùng `yield` hoặc một đường đi ném ngoại lệ để switch expression tạo kết quả.

### Bài F — nhãn kiểu dấu hai chấm

Hoàn thành switch expression dùng nhãn `case:`:

~~~java
String result = switch (code) {
    case 1:
        yield "ONE";
    case 2:
        yield "TWO";
    default:
        yield "OTHER";
};
~~~

**Đáp án:** dạng `case:` được dùng trong switch expression Java 17, nhưng thay `break` bằng `yield` khi cần trả kết quả.

## 15. Checklist và kết luận

- [ ] Biết switch expression tạo giá trị và có thể dùng trong phép gán/`return`.
- [ ] Biết nhánh `case x -> expression` trả giá trị trực tiếp.
- [ ] Biết dùng `case x -> { ... yield expression; }` khi nhánh có nhiều câu lệnh.
- [ ] Hiểu `yield` kết thúc switch expression và truyền giá trị ra ngoài.
- [ ] Không thay `yield` bằng `break`, `return` hay `break value`.
- [ ] Bảo đảm mọi đường đi có thể hoàn thành bình thường đều trả kết quả hoặc ném ngoại lệ.
- [ ] Hiểu `yield` có thể nằm trong `if/else` ở block nhánh.
- [ ] Biết switch expression dạng `case:` cũng dùng `yield`.
- [ ] Nhớ rule mũi tên `->` không fall-through, còn dạng `case:` cần xét fall-through.
- [ ] Hiểu quy tắc kiểu kết quả, numeric conversion, target typing và các lỗi kiểu.
- [ ] Biết switch expression phải bao phủ selector; enum đầy đủ có thể không cần default.
- [ ] Phân biệt `break` của vòng lặp lồng trong nhánh với `yield` của switch.
- [ ] Biên dịch và chạy `SwitchYieldDemo.java` bằng Java 17+; đối chiếu từng dòng exact stdout.

**Kết luận:** Trong Java 17, khi một nhánh của **switch expression** là khối `{ ... }`, dùng **`yield value;`** để trả giá trị cho toàn switch expression. Một nhánh đơn giản có thể viết `case ... -> value` không cần yield. Hãy phân biệt rõ `yield` (trả giá trị của switch expression), `break` (thoát statement/vòng lặp) và `return` (thoát phương thức), đồng thời bảo đảm đủ đường đi và đúng kiểu kết quả.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="switch-statements-008">Yêu cầu bao phủ các trường hợp và chọn kiểu kết quả</a>

<details>
<summary>Xem chi tiết</summary>

# Yêu cầu bao phủ các trường hợp và chọn kiểu kết quả của `switch` trong Java

> **Java core — language-basics / Switch**  
> **Phiên bản:** Java 17+ (không bật preview); chương trình độc lập, chỉ dùng JDK.  
> **Mục tiêu:** Hiểu vì sao `switch expression` phải **bao phủ toàn bộ trường hợp**, khi nào cần `default`, cách `yield`/`throw` ảnh hưởng đến đường đi, và vì sao **kiểu kết quả** phải phù hợp với nơi dùng biểu thức.

## 1. Phân biệt `switch statement` với `switch expression`

`switch statement` điều khiển các thao tác; nó **không bắt buộc** có `default`:

~~~java
int code = 9;
switch (code) {
    case 1 -> System.out.println("ONE");
    case 2 -> System.out.println("TWO");
}
// Không in gì khi code = 9: statement không có nhánh phù hợp.
~~~

`switch expression` **tính một giá trị** để trả về hoặc gán:

~~~java
int code = 9;
String text = switch (code) {
    case 1 -> "ONE";
    case 2 -> "TWO";
    default -> "OTHER";
};
System.out.println(text); // OTHER
~~~

Nếu không có `default` cho hai nhãn 1 và 2 ở `switch expression` trên `int`, trình biên dịch không thể đảm bảo có giá trị khi code bằng 9. Vì thế nó báo lỗi **switch expression does not cover all possible input values** (câu chữ tùy JDK).

**Tính bao phủ (exhaustiveness)** là yêu cầu được kiểm tra khi **biên dịch** cho switch expression; nó không có nghĩa mọi nhánh đều chạy hoặc mọi giá trị đều phải có một nhãn riêng.

## 2. `default` và bao phủ các giá trị số hoặc `String`

Đối với selector kiểu `int` hoặc `String` trong Java 17, cách thông dụng để bao phủ toàn bộ miền là thêm **một `default`**:

~~~java
static String classify(int value) {
    return switch (value) {
        case 0 -> "ZERO";
        case 1, 2 -> "SMALL";
        default -> "OTHER";
    };
}
~~~

`case 1, 2` gộp **hai giá trị khác nhau** vào một nhánh. `default` xử lý mọi giá trị `int` khác 0, 1, 2. Thứ tự nhãn trong dạng mũi tên không tạo fall-through.

Ví dụ lỗi (để trong comment, không đưa vào code chạy):

~~~java
// KHÔNG BIÊN DỊCH: còn vô số giá trị int không được xử lý.
// String label = switch (code) {
//     case 0 -> "ZERO";
//     case 1 -> "ONE";
// };
~~~

Với `String` cũng cần nhánh cho các nội dung không liệt kê:

~~~java
static String command(String input) {
    return switch (input) {
        case "go" -> "MOVE";
        case "stop" -> "HALT";
        default -> "UNKNOWN";
    };
}
~~~

**`default` không phải nhánh `null`**: Java 17 không bật preview sẽ ném `NullPointerException` nếu selector `String`/`enum`/wrapper là null. Nếu null là đầu vào hợp lệ của ứng dụng, cần kiểm tra nó **trước `switch`**.

## 3. `enum` có thể exhaustive mà không cần `default`

Với một `enum` đã biết đủ các hằng, mọi trường hợp có thể được liệt kê:

~~~java
enum State { NEW, RUNNING, DONE }

static String status(State state) {
    return switch (state) {
        case NEW -> "NEW";
        case RUNNING -> "IN_PROGRESS";
        case DONE -> "COMPLETE";
    };
}
~~~

`switch expression` trên `State` ở trên **đã bao phủ đủ** các hằng enum và không cần `default`.

Lợi ích khi **không** viết `default` cho enum đã liệt kê đầy đủ: nếu thêm một hằng mới rồi biên dịch lại mã nguồn, trình biên dịch có thể chỉ ra các switch expression cần cập nhật thay vì âm thầm chuyển trạng thái mới vào một nhánh mặc định chung.

Nếu bỏ sót `DONE` mà không có `default`, switch expression **không exhaustive** và không biên dịch. Ngược lại, `switch statement` có thể chỉ xử lý một số trạng thái mà không bắt buộc phải bao phủ tất cả.

Bao phủ đủ các hằng enum **không đồng nghĩa bao phủ null**. Cần guard null riêng khi dữ liệu có thể thiếu.

## 4. Mỗi đường đi bình thường phải tạo được giá trị

Với `switch expression`, một nhánh mũi tên ngắn có thể cung cấp kết quả trực tiếp:

~~~java
int score = 1;
int points = switch (score) {
    case 0 -> 0;
    case 1 -> 10;
    default -> 100;
};
~~~

Nếu một nhánh cần nhiều câu lệnh, đặt trong khối và dùng `yield value;`:

~~~java
int result = switch (score) {
    case 0 -> 0;
    default -> {
        int computed = score * 10;
        yield computed;
    }
};
~~~

Đường đi **kết thúc bình thường** của khối trong một switch expression phải cung cấp giá trị qua `yield`; không thể đi tới cuối khối mà thiếu kết quả.

Một nhánh được phép **ném ngoại lệ** thay vì trả giá trị:

~~~java
static int priority(int value) {
    return switch (value) {
        case 0 -> 0;
        default -> {
            if (value < 0) {
                throw new IllegalArgumentException("negative");
            }
            yield value * 10;
        }
    };
}
~~~

Cách này vẫn exhaustive: `default` xử lý tất cả giá trị khác 0, nhưng trường hợp âm kết thúc bằng exception chứ không hoàn thành bình thường.

Đừng nhầm `yield` với `break`: `break;` thoát switch statement dạng truyền thống; `yield expression;` tạo kết quả cho switch expression. `break value;` không phải cú pháp Java 17 hợp lệ.

## 5. Kiểu kết quả phải phù hợp với nơi dùng biểu thức

Java không chỉ xét nhánh nào chạy mà còn kiểm tra **kiểu của các biểu thức nhánh khi biên dịch**.

~~~java
int code = 1;
String label = switch (code) {
    case 1 -> "ONE";
    default -> "OTHER";
};
~~~

Ở đây mọi kết quả đều chuyển được sang `String`. Nhưng đoạn sau **không biên dịch**:

~~~java
// KHÔNG BIÊN DỊCH: nhánh int không dùng làm String.
// String wrong = switch (code) {
//     case 1 -> "ONE";
//     default -> 2;
// };
~~~

Quy tắc hữu ích:

- Nếu switch expression xuất hiện trong **ngữ cảnh gán hoặc truyền đối số**, nó có thể được **target-typed** (biểu thức *poly*): từng nhánh phải tương thích với kiểu đích.
- Ở ngữ cảnh không có kiểu đích thích hợp (*standalone expression*), Java xác định kiểu theo quy tắc riêng từ các nhánh, gồm nâng kiểu số hoặc kiểu tham chiếu chung khi phù hợp.
- `yield` bên trong khối cũng phải tạo một giá trị tương thích với kiểu kết quả yêu cầu.
- Không suy đoán kiểu từ **nhánh thực sự được chọn lúc chạy**; trình biên dịch kiểm tra các nhánh có thể cho kết quả.

Ví dụ đích là `double`, nhánh có literal `int` vẫn chuyển được:

~~~java
double number = switch (code) {
    case 1 -> 1;
    default -> 2.5;
};
// code=1: number=1.0.
~~~

Nếu đích là `Number`, có thể chọn các đối tượng số khác lớp:

~~~java
Number boxed = switch (code) {
    case 1 -> Integer.valueOf(10);
    default -> Double.valueOf(2.5);
};
// code=1: boxed thuộc lớp Integer.
~~~

`Number` là lớp cha chung; kết quả thực tế vẫn có thể là `Integer` hoặc `Double` tùy nhánh. Đây **không** phải lời khuyên trộn kiểu tùy tiện: trong code nghiệp vụ, chọn kiểu đích thống nhất và rõ nghĩa sẽ giảm bất ngờ khi chuyển đổi/boxing.

## 6. Bảng chọn cách bao phủ và kiểu kết quả

| Tình huống | Bao phủ hợp lệ trong Java 17 | Kiểu kết quả |
| --- | --- | --- |
| Switch expression trên `int` | Có `default` cho các giá trị ngoài `case` | Mọi nhánh trả kiểu phù hợp |
| Switch expression trên `String` | Có `default` cho nội dung chưa liệt kê; xử lý `null` riêng | Ví dụ `String` |
| Switch expression trên `enum` | Liệt kê đủ hằng, hoặc có nhánh mặc định phù hợp | Ví dụ `String` |
| Nhánh arrow chứa nhiều lệnh | `{ ... yield value; }` hoặc ném ngoại lệ | `yield` tương thích kiểu đích |
| Nhánh chỉ có `throw` | Kết thúc bất thường, không cần giá trị trên đường đó | Các nhánh trả kết quả còn lại vẫn phải tương thích |
| Switch statement dùng để in log | Có thể không cần `default` | Không có kiểu giá trị của cả switch |

Hai kiểm tra **độc lập**: (1) cấu trúc bao phủ mọi selector cần thiết; (2) các nhánh trả kết quả tương thích với kiểu đích. Có `default` vẫn có thể lỗi vì nhánh trả sai kiểu; mọi nhánh cùng trả `String` vẫn có thể lỗi vì thiếu `default`.

Đừng nhầm exhaustive với “không bao giờ lỗi”: một nhánh có thể ném exception, selector null có thể gây NPE, và dữ liệu đầu vào vẫn cần được kiểm tra theo nghiệp vụ.

## 7. Ví dụ Java 17+ chạy độc lập

Tạo file **ExhaustiveSwitchTypeDemo.java**:

~~~java
public final class ExhaustiveSwitchTypeDemo {
    enum State { NEW, RUNNING, DONE }

    private static String action(int code) {
        return switch (code) {
            case 0 -> "ZERO";
            case 1, 2 -> "SMALL";
            default -> {
                String label = "OTHER:" + code;
                yield label;
            }
        };
    }

    private static String stateName(State state) {
        return switch (state) {
            case NEW -> "NEW";
            case RUNNING -> "IN_PROGRESS";
            case DONE -> "COMPLETE";
        };
    }

    private static String command(String text) {
        return switch (text) {
            case "go" -> "MOVE";
            case "stop" -> "HALT";
            default -> "UNKNOWN";
        };
    }

    private static double numeric(int code) {
        return switch (code) {
            case 1 -> 1;
            default -> 2.5;
        };
    }

    private static Number boxed(int code) {
        return switch (code) {
            case 1 -> Integer.valueOf(10);
            default -> Double.valueOf(2.5);
        };
    }

    private static int priority(int value) {
        return switch (value) {
            case 0 -> 0;
            default -> {
                if (value < 0) {
                    throw new IllegalArgumentException("negative");
                }
                yield value * 10;
            }
        };
    }

    public static void main(String[] args) {
        System.out.println("action0=" + action(0));
        System.out.println("action1=" + action(1));
        System.out.println("action2=" + action(2));
        System.out.println("action3=" + action(3));
        System.out.println("actionMinus1=" + action(-1));

        System.out.println("enumNew=" + stateName(State.NEW));
        System.out.println("enumRunning=" + stateName(State.RUNNING));
        System.out.println("enumDone=" + stateName(State.DONE));

        System.out.println("commandGo=" + command(new String("go")));
        System.out.println("commandOther=" + command("GO"));

        System.out.println("numeric1=" + numeric(1));
        System.out.println("numeric2=" + numeric(2));

        Number a = boxed(1);
        Number b = boxed(2);
        System.out.println("boxed1=" + a.getClass().getSimpleName() + ":" + a);
        System.out.println("boxed2=" + b.getClass().getSimpleName() + ":" + b);

        System.out.println("priority0=" + priority(0));
        System.out.println("priority3=" + priority(3));
        try {
            priority(-1);
        } catch (IllegalArgumentException ex) {
            System.out.println("priorityNegative=IllegalArgumentException");
        }

        try {
            command(null);
        } catch (NullPointerException ex) {
            System.out.println("nullSelector=NullPointerException");
        }
    }
}
~~~

Biên dịch và chạy tại thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 ExhaustiveSwitchTypeDemo.java
java ExhaustiveSwitchTypeDemo
~~~

**Expected stdout chính xác:**

~~~text
action0=ZERO
action1=SMALL
action2=SMALL
action3=OTHER:3
actionMinus1=OTHER:-1
enumNew=NEW
enumRunning=IN_PROGRESS
enumDone=COMPLETE
commandGo=MOVE
commandOther=UNKNOWN
numeric1=1.0
numeric2=2.5
boxed1=Integer:10
boxed2=Double:2.5
priority0=0
priority3=30
priorityNegative=IllegalArgumentException
nullSelector=NullPointerException
~~~

`action3`/`actionMinus1` đi qua `default` và `yield`; enum không cần `default` do đã đủ hằng. `numeric1=1.0` là chuyển `int` sang `double`; `boxed1`/`boxed2` cho thấy kiểu đích `Number` có thể nhận các lớp con khác nhau. Nhánh `priority(-1)` ném ngoại lệ nhưng toàn bộ biểu thức vẫn exhaustive.

## 8. Lỗi thường gặp và bài tập

| Lỗi | Cách sửa |
| --- | --- |
| Switch expression trên `int` chỉ có `case 1` | Thêm `default` để bao phủ đầu vào khác |
| Switch expression trên enum thiếu một hằng | Thêm `case` còn thiếu hoặc nhánh mặc định phù hợp |
| Nhánh block không `yield` trên đường hoàn thành bình thường | Viết `yield value;` hoặc dùng nhánh biểu thức trực tiếp |
| Dùng `break value;` để tạo kết quả | Thay bằng `yield value;` |
| Kết quả `String` và `int` trong đích `String` | Đổi nhánh sang kiểu tương thích |
| Cho rằng `default` xử lý `null` | Kiểm tra null trước switch Java 17 tiêu chuẩn |
| Dùng switch statement mong nhận giá trị | Chuyển sang switch expression |

**Bài 1.** Viết switch expression phân loại `int level`: 0 → `"LOW"`, 1 → `"MEDIUM"`, còn lại → `"OTHER"`. Thử bỏ `default` và giải thích lỗi.

**Bài 2.** Tạo enum có ba hằng và viết switch expression liệt kê đủ ba nhánh không có `default`. Thêm hằng thứ tư rồi thử biên dịch lại.

**Bài 3.** Viết một nhánh `case 2 -> { int x = 20; yield "Value=" + x; }`. Vì sao không thể chỉ viết `x;` ở cuối khối?

**Bài 4.** So sánh kiểu của kết quả `double x = switch (...) { case 1 -> 1; default -> 2.5; };` với `Number x = switch (...) { case 1 -> Integer.valueOf(1); default -> Double.valueOf(2.5); };`.

**Bài 5.** Viết nhánh `default` ném `IllegalArgumentException` nếu đầu vào âm, trả giá trị nếu dương. Hệ thống có cần giá trị từ nhánh ném exception không? Vì sao?

### Ghi nhớ

**Switch expression phải exhaustive và có kiểu kết quả hợp lệ.** Với `int`/`String` thường cần `default`; với `enum` có thể bao phủ đủ hằng. Nhánh một biểu thức trả giá trị trực tiếp, khối nhiều lệnh dùng **`yield`**, nhánh lỗi có thể `throw`. Java kiểm tra sự tương thích kiểu của **các nhánh khi biên dịch**, không đợi đến khi một nhánh được chọn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="switch-statements-009">Xử lý null trong switch và phân biệt với default</a>

<details>
<summary>Xem chi tiết</summary>

# Xử lý `null` trong `switch` và phân biệt với `default` — Java 17+

**`default` không phải nhánh xử lý `null`.** Trong `switch` Java 17 sử dụng tính năng chuẩn, nếu biểu thức chọn (*selector*) có giá trị `null`, chương trình ném `NullPointerException` **trước khi đi vào bất kỳ nhãn `case` hoặc `default` nào**.

~~~java
String status = null;

// Đoạn này sẽ ném NullPointerException tại runtime:
// switch (status) {
//     case "ON" -> System.out.println("enabled");
//     default -> System.out.println("unknown");
// }
~~~

Ngược lại, `default` chạy khi selector **không null**, nhưng không trùng với bất cứ nhãn `case` nào:

~~~java
String status = "PAUSE";
switch (status) {
    case "ON" -> System.out.println("enabled");
    case "OFF" -> System.out.println("disabled");
    default -> System.out.println("unknown"); // Chạy nhánh này.
}
~~~

## 1. Phân biệt trường hợp không khớp và `null`

| Giá trị selector `String` | Có khớp `case "ON"`? | `default` chạy? | Hành vi |
| --- | --- | --- | --- |
| `"ON"` | Có | Không | Chạy `case "ON"` |
| `"OFF"` | Không, nhưng khớp case khác | Không | Chạy `case "OFF"` |
| `"PAUSE"` | Không | Có | Chạy `default` |
| `""` (chuỗi rỗng) | Không | Có | Chạy `default` |
| `null` | Không xét case | **Không** | Ném `NullPointerException` |

Chuỗi rỗng `""` là **một đối tượng `String` không null**, nên hoàn toàn có thể được xử lý bởi `default`. `null` thì không trỏ tới đối tượng `String` nào.

Điều này áp dụng cho cả **switch statement** và **switch expression** trong Java 17 tiêu chuẩn, kể cả khi đã khai báo `default` đầy đủ.

## 2. Cách an toàn: kiểm tra `null` trước `switch`

### Trả kết quả riêng cho dữ liệu thiếu

~~~java
static String describe(String status) {
    if (status == null) {
        return "missing";
    }
    return switch (status) {
        case "ON" -> "enabled";
        case "OFF" -> "disabled";
        default -> "unknown";
    };
}
~~~

- `"missing"`: đầu vào **không tồn tại**.
- `"unknown"`: đầu vào **có tồn tại**, nhưng không phải `"ON"` hay `"OFF"`.
- Các nhánh `case`: những giá trị hợp lệ được nhận diện.

Đây là hai trạng thái nghiệp vụ khác nhau. Không tự động gộp `null` với `default` nếu ứng dụng cần phân biệt “chưa nhập” và “đã nhập nhưng không hợp lệ”.

### Báo lỗi thay vì tạo giá trị thay thế

Nếu `null` vi phạm hợp đồng phương thức, có thể từ chối rõ ràng:

~~~java
static String requireStatus(String status) {
    if (status == null) {
        throw new IllegalArgumentException("status required");
    }
    return switch (status) {
        case "ON" -> "enabled";
        default -> "unknown";
    };
}
~~~

`IllegalArgumentException` ở đây thể hiện chính sách **do chương trình lựa chọn**. Còn `NullPointerException` tự phát sinh từ `switch(null)` thường khiến nơi gặp lỗi khó giải thích ý nghĩa nghiệp vụ hơn.

## 3. Ví dụ Java 17+ độc lập, runnable CLI

Tạo file UTF-8 **`SwitchNullDemo.java`**:

~~~java
public class SwitchNullDemo {
    enum Mode { START, STOP }

    static String statement(String code) {
        if (code == null) {
            return "missing";
        }
        String result;
        switch (code) {
            case "ON":
                result = "enabled";
                break;
            case "OFF":
                result = "disabled";
                break;
            default:
                result = "unknown";
        }
        return result;
    }

    static String expression(String code) {
        if (code == null) {
            return "missing";
        }
        return switch (code) {
            case "ON" -> "enabled";
            case "OFF" -> "disabled";
            default -> "unknown";
        };
    }

    static String number(Integer n) {
        if (n == null) {
            return "missing";
        }
        return switch (n) {
            case 0 -> "zero";
            case 1 -> "one";
            default -> "other";
        };
    }

    static String mode(Mode m) {
        if (m == null) {
            return "missing";
        }
        return switch (m) {
            case START -> "start";
            case STOP -> "stop";
        };
    }

    static String rawString(String code) {
        return switch (code) {
            case "ON" -> "enabled";
            default -> "default";
        };
    }

    static String rawNumber(Integer n) {
        return switch (n) {
            case 0 -> "zero";
            default -> "default";
        };
    }

    static String rawMode(Mode m) {
        return switch (m) {
            case START -> "start";
            case STOP -> "stop";
        };
    }

    static String requireCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("code required");
        }
        return rawString(code);
    }

    public static void main(String[] args) {
        System.out.println("== String guard ==");
        for (String s : new String[] {null, "ON", "OFF", "PAUSE", ""}) {
            System.out.println("input=" + String.valueOf(s)
                    + ",statement=" + statement(s)
                    + ",expression=" + expression(s));
        }

        System.out.println("== wrapper and enum ==");
        System.out.println("numberNull=" + number(null));
        System.out.println("number0=" + number(0));
        System.out.println("number9=" + number(9));
        System.out.println("modeNull=" + mode(null));
        System.out.println("modeStart=" + mode(Mode.START));
        System.out.println("modeStop=" + mode(Mode.STOP));

        System.out.println("== default versus null ==");
        System.out.println("unmatched=" + rawString("PAUSE"));
        System.out.println("empty=" + rawString(""));
        System.out.println("unmatchedNumber=" + rawNumber(7));

        try {
            System.out.println("unexpected=" + rawString(null));
        } catch (NullPointerException ex) {
            System.out.println("nullString=NullPointerException");
        }
        try {
            System.out.println("unexpected=" + rawNumber(null));
        } catch (NullPointerException ex) {
            System.out.println("nullNumber=NullPointerException");
        }
        try {
            System.out.println("unexpected=" + rawMode(null));
        } catch (NullPointerException ex) {
            System.out.println("nullMode=NullPointerException");
        }

        System.out.println("== explicit contract ==");
        try {
            requireCode(null);
        } catch (IllegalArgumentException ex) {
            System.out.println("invalid=" + ex.getMessage());
        }
        System.out.println("required=" + requireCode("ON"));

        String maybeNull = null;
        String safe = maybeNull == null
                ? "missing"
                : rawString(maybeNull);
        System.out.println("ternary=" + safe);
    }
}
~~~

Dùng **JDK 17 trở lên**, mở PowerShell hoặc Bash tại thư mục chứa tệp:

~~~bash
java -version
javac -version
javac -encoding UTF-8 SwitchNullDemo.java
java SwitchNullDemo
~~~

**Exact expected stdout** từ riêng lệnh `java SwitchNullDemo`:

~~~text
== String guard ==
input=null,statement=missing,expression=missing
input=ON,statement=enabled,expression=enabled
input=OFF,statement=disabled,expression=disabled
input=PAUSE,statement=unknown,expression=unknown
input=,statement=unknown,expression=unknown
== wrapper and enum ==
numberNull=missing
number0=zero
number9=other
modeNull=missing
modeStart=start
modeStop=stop
== default versus null ==
unmatched=default
empty=default
unmatchedNumber=default
nullString=NullPointerException
nullNumber=NullPointerException
nullMode=NullPointerException
== explicit contract ==
invalid=code required
required=enabled
ternary=missing
~~~

Đầu ra của hai lệnh `-version` phụ thuộc bản JDK trên máy; chúng không thuộc khối stdout cố định.

## 4. `String`, wrapper và `enum` đều cần chú ý `null`

| Kiểu selector trong Java 17 | Selector không null | Selector null |
| --- | --- | --- |
| `String` | Khớp theo **nội dung** với nhãn chuỗi | Ném `NullPointerException` |
| `Integer`, `Byte`, `Short`, `Character` | Giá trị wrapper được xử lý theo quy tắc `switch` số nguyên | Ném `NullPointerException` |
| `enum` | Khớp tên hằng enum | Ném `NullPointerException` |
| `int`, `byte`, `short`, `char` | Dùng giá trị nguyên thủy | **Không thể** là null |

Trong demo, `rawNumber(null)` gây lỗi do selector wrapper không có giá trị để dùng; `rawMode(null)` cũng không được `default` “bắt hộ”.

**Không nhầm `default` với `case null`.** Trong Java 17 biên dịch thông thường (không bật preview), không viết `case null -> ...` cho switch theo cú pháp chuẩn. Các ví dụ về `case null` trong tài liệu Java mới hơn thường thuộc **pattern matching for switch**, được chuẩn hóa từ Java 21; Java 17 chỉ có tính năng liên quan ở dạng preview với yêu cầu cấu hình riêng, **không áp dụng vào bài chạy CLI tiêu chuẩn này**.

## 5. Khi nào cần `default`?

`default` dành cho **giá trị hợp lệ về kiểu nhưng không khớp case**:

~~~java
String code = "MAYBE";
String result = switch (code) {
    case "YES" -> "accepted";
    case "NO" -> "rejected";
    default -> "unrecognized";
};
~~~

Với `"MAYBE"`, kết quả là `"unrecognized"`.

Với **switch expression**, trình biên dịch yêu cầu các trường hợp được bao phủ đầy đủ (*exhaustive*). Với `String` hay `int`, điều này thường đòi hỏi `default`. Với một `enum`, liệt kê đủ hằng số có thể không cần `default`, ví dụ `switch(Mode)` trong chương trình.

Tính đầy đủ đó **không đồng nghĩa chấp nhận null**. Biểu thức switch trên enum liệt kê đủ hằng vẫn gây lỗi nếu selector là `null`.

## 6. Chọn chính sách xử lý rõ ràng

Ba chính sách phổ biến:

| Chính sách | Khi `null` | Khi không khớp case |
| --- | --- | --- |
| Trả kết quả riêng | `"missing"` | `"unknown"` |
| Từ chối dữ liệu | `throw IllegalArgumentException` | `throw` hoặc `default` báo lỗi khác |
| Chuyển sang giá trị thay thế | Chỉ khi có quy ước nghiệp vụ rõ ràng | `default` theo quy ước |

Có thể viết kiểm tra ngắn bằng toán tử `?:`:

~~~java
String result = code == null
        ? "missing"
        : switch (code) {
            case "ON" -> "enabled";
            default -> "unknown";
        };
~~~

Do `?:` chỉ đánh giá nhánh được chọn, nếu `code == null` thì `switch` **không được đánh giá**. Nhưng nếu kiểm tra nhiều điều kiện hoặc cần nhiều lệnh, phương thức có guard `if (code == null)` thường dễ đọc hơn.

Không dùng một “giá trị giả” như `""` để thay null mà không cân nhắc va chạm với dữ liệu hợp lệ: chuỗi rỗng có thể mang ý nghĩa riêng.

## 7. Những lỗi thường gặp

| Hiểu nhầm | Cách đúng |
| --- | --- |
| “`default` chạy khi selector null.” | Sai với switch Java 17 tiêu chuẩn; ném NPE |
| “`case "ON"` so sánh bằng tham chiếu `==`.” | Switch `String` khớp theo nội dung chuỗi |
| “Một wrapper null sẽ tự đổi thành số 0.” | Null không có giá trị để unbox |
| “`enum` đã liệt kê đủ case thì null tự được xử lý.” | Không; vẫn cần guard nếu có thể null |
| “`""` chính là null.” | Chuỗi rỗng là `String` không null |
| “Java 17 chuẩn có thể dùng `case null` như Java 21.” | Không nếu biên dịch bình thường không bật preview |
| “Dùng `default` là đã xác thực đầu vào.” | Chỉ bao phủ nhãn không khớp, không thay thế kiểm tra hợp lệ nghiệp vụ |

## 8. Bài tập tự kiểm tra

1. `switch("UNKNOWN")` có `default` thì sao?  
   *Đáp án:* Chạy `default` nếu không case nào khác khớp.
2. `switch((String) null)` có `default` thì sao trong Java 17 tiêu chuẩn?  
   *Đáp án:* `NullPointerException` trước khi chạy default.
3. `String s = "";` có được default xử lý không?  
   *Đáp án:* Có, nếu không có nhãn khớp chuỗi rỗng.
4. `Integer n = null; switch(n) {...}` có tự hiểu n bằng 0 không?  
   *Đáp án:* Không; ném NPE.
5. Switch expression liệt kê đủ mọi hằng enum nhưng không có default có xử lý null không?  
   *Đáp án:* Không.
6. Khi dữ liệu thiếu và dữ liệu không nhận diện là hai trạng thái khác nhau, nên làm gì?  
   *Đáp án:* Kiểm tra null trước switch, trả kết quả riêng cho hai tình huống.

## Checklist

- [ ] `default` xử lý **không khớp case**, không tự xử lý `null`.
- [ ] Kiểm tra `null` trước switch Java 17 đối với `String`, wrapper và enum có thể thiếu.
- [ ] Phân biệt switch statement và switch expression nhưng nhớ cả hai đều cần kiểm soát selector null.
- [ ] Chọn chính sách trả kết quả hoặc ném ngoại lệ rõ ràng.
- [ ] Không dùng `case null` trong ví dụ Java 17 tiêu chuẩn không có preview.
- [ ] Dùng `default` để bao phủ trường hợp còn lại, không xem đó là kiểm tra toàn bộ tính hợp lệ của đầu vào.

**Kết luận:** Trong Java 17 tiêu chuẩn, `default` có nghĩa **“không case nào khớp với một selector hợp lệ, không null”**, chứ không có nghĩa **“mọi trường hợp còn lại kể cả null”**. Kiểm tra `null` trước, rồi dùng `case` và `default` để xử lý giá trị có mặt.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="switch-statements-010">Chọn if hoặc switch theo bản chất điều kiện</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn `if` hoặc `switch` theo bản chất điều kiện — Java 17+

## 1. Nguyên tắc quyết định

Cả `if/else if/else` và `switch` đều điều khiển luồng thực thi, nhưng diễn đạt **hai loại bài toán** khác nhau:

- **`if`**: trả lời những câu hỏi **đúng/sai** tùy ý, chẳng hạn “điểm từ 70 trở lên”, “người dùng đủ 18 tuổi **và** đã xác minh”, hoặc “ưu tiên khẩn cấp”.
- **`switch`**: chọn xử lý theo **một giá trị rời rạc** có các trường hợp xác định, chẳng hạn mã trạng thái `1/2/3`, lệnh `"start"/"stop"` hoặc một hằng `enum`.

~~~java
int score = 85;
if (score >= 90) {
    System.out.println("A");
} else if (score >= 70) {
    System.out.println("B");
} else {
    System.out.println("C");
}

String command = "start";
switch (command) {
    case "start" -> System.out.println("START");
    case "stop" -> System.out.println("STOP");
    default -> System.out.println("UNKNOWN");
}
~~~

**Exact stdout:**

~~~text
B
START
~~~

**Đừng chọn theo cảm giác “switch trông ngắn hơn” hoặc “if chạy nhanh hơn”.** Hãy chọn theo **loại điều kiện cần biểu đạt**, quy tắc miền dữ liệu và khả năng bảo trì.

## 2. Bảng chọn nhanh

| Nhu cầu | Thường chọn | Lý do |
|---|---|---|
| Kiểm tra `age >= 18` | `if` | Điều kiện quan hệ, không phải một giá trị cố định |
| Kiểm tra `0 <= score <= 100` | `if` | Kết hợp nhiều điều kiện boolean |
| Phân loại điểm theo các khoảng 0–49, 50–69, 70–89, 90–100 | `if/else if` | Các ngưỡng và biên có ý nghĩa |
| Quy tắc `isAdmin || (verified && !blocked)` | `if` | Logic `&&`, `||`, `!` |
| Xử lý `code == 1`, `code == 2`, `code == 3` | `switch` | Các trường hợp rời rạc của cùng selector |
| Xử lý lệnh `"start"`, `"stop"` | `switch(String)` | Nhãn giá trị String dễ đọc |
| Xử lý `Status.NEW/RUNNING/DONE` | `switch(enum)` | Tập giá trị hữu hạn, đặt tên rõ |
| Một điều kiện có độ ưu tiên cao phải xét trước các điều kiện khác | `if/else if` | Thứ tự là một phần của quy tắc |
| Hai hành động phải cùng chạy nếu hai điều kiện đều true | Hai `if` độc lập | Chuỗi `else if` chỉ chọn nhánh đầu tiên |
| Phân loại `double` theo ngưỡng | `if` | `double` không là selector switch Java 17 ổn định |

Một bài toán có thể dùng **cả hai**: `if` xác thực đầu vào, sau đó `switch` xử lý mã lệnh hợp lệ.

## 3. `if` phù hợp với miền liên tục và điều kiện kết hợp

Giả sử điểm hợp lệ là `[0,100]` và quy định:

| Miền điểm | Kết quả |
|---|---|
| Ngoài `[0,100]` | INVALID |
| `[90,100]` | A |
| `[70,90)` | B |
| `[50,70)` | C |
| `[0,50)` | D |

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
    }
    return "D";
}
~~~

Cách này bộc lộ rõ **ngưỡng** và **biên bao gồm**:

- 90 thuộc A, 70 thuộc B, 50 thuộc C.
- 100 vẫn hợp lệ, 101 bị loại.
- Kiểm tra ngưỡng từ cao xuống thấp; nếu đặt `score >= 50` lên trước, các hạng A/B có thể bị nhánh rộng **che khuất**.

**Không nên thay bằng:**

~~~java
// switch (score) {
//     case 90 -> "A";
//     case 70 -> "B";
//     default -> "C";
// }
~~~

Biểu thức trên chỉ khớp **đúng các số 90 hoặc 70**, không biểu diễn “từ 90 trở lên” hay “từ 70 trở lên”. Java 17 switch thông thường **không cho nhãn `case score >= 90`**.

## 4. `if` phù hợp với điều kiện nhiều biến và độ ưu tiên

~~~java
boolean eligible = age >= 18
        && verified
        && !banned;
~~~

Ba phép kiểm tra trên sử dụng **ba dữ liệu**, với quan hệ AND và NOT. `switch` không thay thế được biểu thức logic này một cách tự nhiên.

Với nhiều quy tắc ưu tiên:

~~~java
static String priority(int risk, boolean emergency) {
    if (risk < 0 || risk > 100) {
        return "INVALID";
    }
    if (emergency) {
        return "P0";
    } else if (risk >= 80) {
        return "P1";
    } else if (risk >= 50) {
        return "P2";
    }
    return "P3";
}
~~~

- **Khẩn cấp** thắng các ngưỡng rủi ro thông thường.
- Khi không khẩn cấp, mức rủi ro cao hơn được xét trước.
- Dữ liệu sai miền bị từ chối trước khi phân loại.

**Thứ tự các nhánh có ý nghĩa nghiệp vụ.** Không tự đảo thứ tự vì nghĩ điều kiện ngắn hơn nên đặt lên trước.

Nếu cần in/đánh dấu **cả hai tính chất** cùng đúng:

~~~java
if (score >= 50) {
    System.out.println("PASS");
}
if (score >= 90) {
    System.out.println("EXCELLENT");
}
~~~

Với score 95, **cả hai** được in. Nếu dùng `else if`, chỉ một nhánh được chạy. `switch` cũng không phải thay thế trực tiếp cho yêu cầu đánh giá nhiều tính chất độc lập.

## 5. `switch` phù hợp khi chọn một trong nhiều giá trị rời rạc

~~~java
static String status(int code) {
    return switch (code) {
        case 1 -> "NEW";
        case 2 -> "RUNNING";
        case 3 -> "DONE";
        default -> "UNKNOWN";
    };
}
~~~

Đây là **switch expression**, trả một `String`. Nó tự nhiên vì:

- Chỉ xét **một selector** `code`.
- Mỗi nhãn `case` là một giá trị hằng rời rạc.
- Các nhánh `->` không fall-through.
- `default` xử lý mã không được khai báo.

Nếu chỉ muốn in log hoặc thực hiện hành động mà **không tạo giá trị**, dùng switch **statement**:

~~~java
switch (code) {
    case 1 -> System.out.println("created");
    case 2 -> System.out.println("running");
    case 3 -> System.out.println("done");
    default -> System.out.println("unknown");
}
~~~

**Phân biệt:** `case ->` không tự động biến switch thành expression; **ngữ cảnh sử dụng** mới quyết định nó là statement hay expression.

## 6. Kiểm tra kiểu selector trước khi chọn switch

Java 17 ổn định (không preview) hỗ trợ selector `byte`, `short`, `char`, `int`, bốn wrapper tương ứng, `String` hoặc enum cụ thể. `long`, `float`, `double`, `boolean`, `Object` và các wrapper còn lại không được dùng trực tiếp.

Nếu mã nghiệp vụ là `long`, đừng ép sang `int` chỉ để dùng switch vì có thể mất thông tin. Chọn `if` hoặc thiết kế lại mô hình dữ liệu nếu có lý do. Bài đầu chương trình bày bảng selector và phạm vi phiên bản đầy đủ.

## 7. Chọn `switch(String)`: mã lệnh, không phải mọi chuỗi văn bản

Một lệnh có tập giá trị nhỏ và xác định rất phù hợp với switch:

~~~java
static String command(String input) {
    if (input == null) {
        return "MISSING";
    }
    String normalized = input.strip().toLowerCase(java.util.Locale.ROOT);

    return switch (normalized) {
        case "start", "run" -> "START";
        case "stop" -> "STOP";
        default -> "UNKNOWN";
    };
}
~~~

`switch(String)` so khớp theo **nội dung**, có phân biệt chữ hoa/thường. Ví dụ chủ động chuẩn hóa đầu vào ASCII như `"RUN"` hoặc `" Start "` trước khi chọn lệnh.

**Quan trọng:**

- `null` cần được kiểm tra trước; selector String null có thể ném `NullPointerException` trong switch Java 17 ổn định, `default` **không bắt null**.
- Không coi chuẩn hóa tất cả chuỗi về chữ thường là phù hợp mọi nghiệp vụ; ID, token hoặc mật khẩu có thể phân biệt hoa/thường.
- `case` phải là hằng phù hợp; không thể dùng một biến String runtime làm case constant chỉ vì giá trị của nó hiện là `"start"`.

## 8. `switch(enum)`: các trạng thái đặt tên rõ ràng

~~~java
enum Channel { WEB, MOBILE, API }

static String channelName(Channel channel) {
    if (channel == null) {
        return "MISSING";
    }
    return switch (channel) {
        case WEB -> "BROWSER";
        case MOBILE -> "PHONE";
        case API -> "SERVICE";
    };
}
~~~

Vì đã liệt kê đủ tất cả hằng `Channel`, switch expression có thể **không cần `default`**. Đây là ưu điểm khi muốn compiler hỗ trợ phát hiện thiếu trường hợp nếu enum được mở rộng.

Ngược lại, thêm `default` là hợp lý khi thực sự có một chính sách cho giá trị chưa liệt kê; không nên thêm mặc định tùy tiện khiến một trạng thái mới bị xử lý lặng lẽ như trạng thái cũ.

Trong switch của enum ở Java 17 ổn định, nhãn viết `case WEB`, **không** viết `case Channel.WEB`.

Với enum nullable, vẫn phải guard null trước switch.

## 9. Chọn statement hay expression

Khi cần thực hiện hành động, dùng switch statement; khi cần tạo một giá trị, dùng switch expression. Trong Java 17, nhánh `->` không fall-through. Mã `case:` vẫn hợp lệ nhưng cần xem có phải dùng `break` để dừng trước nhánh kế không. Với switch expression có block nhiều lệnh, dùng `yield` để tạo kết quả. Đây là lựa chọn **sau khi** đã xác nhận bài toán phù hợp selector rời rạc.

## 10. Câu hỏi lựa chọn theo yêu cầu

Trước khi quyết định, hãy thử trả lời theo thứ tự:

~~~mermaid
flowchart TD
    A["Yêu cầu rẽ nhánh"] --> B{"Điều kiện là khoảng, so sánh, nhiều biến, ưu tiên?"}
    B -- "Có" --> C["Dùng if / else if"]
    B -- "Không" --> D{"Chọn theo một selector có kiểu switch hỗ trợ?"}
    D -- "Không" --> E["Dùng if hoặc thiết kế lại mô hình"]
    D -- "Có" --> F{"Các trường hợp rời rạc, case constant/enum?"}
    F -- "Có" --> G["Dùng switch"]
    F -- "Không" --> C
    G --> H{"Cần tạo một giá trị?"}
    H -- "Có" --> I["Switch expression, -> và yield khi cần"]
    H -- "Không" --> J["Switch statement, ưu tiên -> khi không muốn fall-through"]
~~~

Đây là **quy tắc thực hành**, không phải luật bắt buộc. Có thể viết `if (code == 1) ... else if (code == 2)` vẫn đúng; khi nhiều lựa chọn cùng một selector, switch thường dễ quét hơn.

Không quyết định chỉ vì tuyên bố hiệu năng: JVM/JIT và đặc điểm dữ liệu ảnh hưởng đến tối ưu hóa, còn **đúng ngữ nghĩa và dễ đọc** là tiêu chí đầu tiên. Nếu hiệu năng thực sự là yêu cầu, đo đạc bằng benchmark đúng phương pháp với dữ liệu đại diện.

## 11. Kết hợp `if` và `switch` khi cần

Một ứng dụng có thể vừa kiểm tra dữ liệu, vừa phân loại lệnh:

~~~java
static String execute(String command, boolean authenticated) {
    if (!authenticated) {
        return "DENIED";
    }
    if (command == null) {
        return "MISSING";
    }
    return switch (command) {
        case "start" -> "STARTED";
        case "stop" -> "STOPPED";
        default -> "UNKNOWN";
    };
}
~~~

Trong ví dụ:

1. `if` áp dụng **quy tắc xác thực** trước.
2. `if` loại selector `null`.
3. `switch` lựa chọn theo **giá trị command**.

Trật tự có thể quan trọng: nếu không xác thực, không nên tiết lộ những nhánh xử lý có sẵn cho người gọi. Đây chỉ là ví dụ về luồng điều khiển; kiểm soát quyền thực tế còn đòi xác thực và kiểm tra phía đáng tin cậy.

## 12. Chương trình Java 17 hoàn chỉnh

Tạo file đúng tên **`IfOrSwitchDecisionDemo.java`**:

~~~java
import java.util.Locale;

public final class IfOrSwitchDecisionDemo {
    private IfOrSwitchDecisionDemo() {}

    enum Channel { WEB, MOBILE, API }

    static String grade(int score) {
        if (score < 0 || score > 100) return "INVALID";
        if (score >= 90) return "A";
        if (score >= 70) return "B";
        if (score >= 50) return "C";
        return "D";
    }

    static String ageGroup(int age) {
        if (age < 0) return "INVALID";
        if (age >= 65) return "SENIOR";
        if (age >= 18) return "ADULT";
        return "MINOR";
    }

    static String wrongAgeGroup(int age) {
        if (age >= 18) return "ADULT";
        if (age >= 65) return "SENIOR";
        return "MINOR";
    }

    static boolean eligible(int age, boolean verified, boolean banned) {
        return age >= 18 && verified && !banned;
    }

    static String priority(int risk, boolean emergency) {
        if (risk < 0 || risk > 100) return "INVALID";
        if (emergency) return "P0";
        if (risk >= 80) return "P1";
        if (risk >= 50) return "P2";
        return "P3";
    }

    static String status(int code) {
        return switch (code) {
            case 1 -> "NEW";
            case 2 -> "RUNNING";
            case 3 -> "DONE";
            default -> "UNKNOWN";
        };
    }

    static String command(String input) {
        if (input == null) return "MISSING";
        String normalized = input.strip().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "start", "run" -> "START";
            case "stop" -> "STOP";
            default -> "UNKNOWN";
        };
    }

    static String channelName(Channel channel) {
        if (channel == null) return "MISSING";
        return switch (channel) {
            case WEB -> "BROWSER";
            case MOBILE -> "PHONE";
            case API -> "SERVICE";
        };
    }

    static String traditionalStatement(int code) {
        String result;
        switch (code) {
            case 1:
                result = "ONE";
                break;
            case 2:
                result = "TWO";
                break;
            default:
                result = "OTHER";
        }
        return result;
    }

    static String execute(String command, boolean authenticated) {
        if (!authenticated) return "DENIED";
        if (command == null) return "MISSING";
        return switch (command) {
            case "start" -> "STARTED";
            case "stop" -> "STOPPED";
            default -> "UNKNOWN";
        };
    }

    public static void main(String[] args) {
        System.out.println("grade 49=" + grade(49));
        System.out.println("grade 50=" + grade(50));
        System.out.println("grade 69=" + grade(69));
        System.out.println("grade 70=" + grade(70));
        System.out.println("grade 89=" + grade(89));
        System.out.println("grade 90=" + grade(90));
        System.out.println("grade 101=" + grade(101));

        System.out.println("age 17=" + ageGroup(17));
        System.out.println("age 18=" + ageGroup(18));
        System.out.println("age 65=" + ageGroup(65));
        System.out.println("wrong age 70=" + wrongAgeGroup(70));
        System.out.println("right age 70=" + ageGroup(70));

        System.out.println("eligible yes=" + eligible(20, true, false));
        System.out.println("eligible banned=" + eligible(20, true, true));
        System.out.println("eligible minor=" + eligible(17, true, false));

        System.out.println("priority emergency=" + priority(99, true));
        System.out.println("priority high=" + priority(99, false));
        System.out.println("priority low=" + priority(49, false));
        System.out.println("priority invalid=" + priority(-1, true));

        System.out.println("status 1=" + status(1));
        System.out.println("status 2=" + status(2));
        System.out.println("status 9=" + status(9));

        System.out.println("command start=" + command(" Start "));
        System.out.println("command run=" + command("RUN"));
        System.out.println("command stop=" + command("stop"));
        System.out.println("command pause=" + command("pause"));
        System.out.println("command null=" + command(null));

        System.out.println("channel WEB=" + channelName(Channel.WEB));
        System.out.println("channel MOBILE=" + channelName(Channel.MOBILE));
        System.out.println("channel null=" + channelName(null));

        System.out.println("statement 2=" + traditionalStatement(2));
        System.out.println("statement 5=" + traditionalStatement(5));

        System.out.println("execute blocked=" + execute("start", false));
        System.out.println("execute null=" + execute(null, true));
        System.out.println("execute start=" + execute("start", true));

        int[] sample = {70, 95};
        for (int score : sample) {
            if (score >= 50) System.out.println("passed " + score);
            if (score >= 90) System.out.println("excellent " + score);
        }
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên; mở terminal tại thư mục chứa file. Chỉ dùng API tiêu chuẩn JDK, không cần Maven, Gradle hoặc preview feature.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 IfOrSwitchDecisionDemo.java
java -cp . IfOrSwitchDecisionDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\IfOrSwitchDecisionDemo.java
java -cp . IfOrSwitchDecisionDemo
~~~

### Exact expected stdout

~~~text
grade 49=D
grade 50=C
grade 69=C
grade 70=B
grade 89=B
grade 90=A
grade 101=INVALID
age 17=MINOR
age 18=ADULT
age 65=SENIOR
wrong age 70=ADULT
right age 70=SENIOR
eligible yes=true
eligible banned=false
eligible minor=false
priority emergency=P0
priority high=P1
priority low=P3
priority invalid=INVALID
status 1=NEW
status 2=RUNNING
status 9=UNKNOWN
command start=START
command run=START
command stop=STOP
command pause=UNKNOWN
command null=MISSING
channel WEB=BROWSER
channel MOBILE=PHONE
channel null=MISSING
statement 2=TWO
statement 5=OTHER
execute blocked=DENIED
execute null=MISSING
execute start=STARTED
passed 70
passed 95
excellent 95
~~~

**Cách đọc output:**

1. Các mốc 49/50/69/70/89/90 cho thấy `if` phù hợp với **ngưỡng**, và 101 bị từ chối trước khi xếp loại.
2. `wrong age 70=ADULT` là lỗi **nhánh rộng che nhánh hẹp**; sửa bằng xét `age >= 65` trước.
3. `eligible` dùng nhiều biến boolean và điều kiện kết hợp; `priority` có ưu tiên khẩn cấp.
4. `status` xử lý các mã rời rạc; `command` xử lý String đã chuẩn hóa; `channelName` xử lý enum.
5. `traditionalStatement` dùng `break` để ngăn fall-through; `execute` dùng `if` để bảo vệ trước `switch`.
6. Hai câu `if` độc lập cho phép `95` in **cả** `passed` và `excellent`.

## 13. Sai lầm thường gặp

| Sai lầm | Hậu quả | Cách phù hợp |
|---|---|---|
| Dùng switch để kiểm tra `score >= 90` | `case 90` chỉ khớp số 90 | Dùng `if` cho ngưỡng |
| Chọn switch cho `double` hoặc `long` trên Java 17 ổn định | Lỗi compile | Dùng `if` hoặc thiết kế kiểu khác |
| Đặt `age >= 18` trước `age >= 65` | Nhánh senior bị che khuất | Xét ngưỡng cụ thể/ưu tiên trước |
| Dùng `else if` cho hai tính chất phải cùng báo | Chỉ nhánh đầu true chạy | Hai `if` độc lập |
| Không validate điểm ngoài `[0,100]` | Điểm 101 có thể thành A | Guard miền trước |
| Dùng `switch(String)` với null | NullPointerException | Kiểm tra null trước |
| Nghĩ switch trên String so tham chiếu `==` | Sai ý nghĩa so khớp | Switch so nội dung, case-sensitive |
| Quên `break` trong dạng `case:` | Fall-through ngoài ý muốn | Dùng `break` hoặc `case ->` |
| Nghĩ `case ->` chỉ có ở switch expression | Switch statement cũng hỗ trợ | Nhìn ngữ cảnh |
| Không có `default` trong switch expression trên int | Không bao phủ giá trị | Thêm default |
| Thêm `default` vào enum để che mọi thay đổi | Có thể bỏ sót trạng thái mới cần xử lý | Cân nhắc exhaustiveness của enum |
| Chọn chỉ vì “switch nhanh hơn” | Không bảo đảm hiệu năng ở mọi tình huống | Chọn theo đúng ngữ nghĩa, đo nếu cần |
| Chuẩn hóa String vô điều kiện | Có thể đổi ý nghĩa token phân biệt hoa/thường | Chỉ chuẩn hóa khi yêu cầu cho phép |

## 14. Bài tập có đáp án

### Bài A — phân loại khoảng

Yêu cầu: nhiệt độ dưới 0 → COLD, từ 0 đến dưới 30 → NORMAL, từ 30 trở lên → HOT.

**Nên chọn:** `if/else if`, vì so sánh **miền**:

~~~java
String label;
if (temperature < 0) {
    label = "COLD";
} else if (temperature < 30) {
    label = "NORMAL";
} else {
    label = "HOT";
}
~~~

### Bài B — chọn mã thao tác

Mã `1` → CREATE, `2` → UPDATE, `3` → DELETE, còn lại → UNKNOWN.

**Nên chọn:** `switch` vì đây là các giá trị rời rạc:

~~~java
String action = switch (code) {
    case 1 -> "CREATE";
    case 2 -> "UPDATE";
    case 3 -> "DELETE";
    default -> "UNKNOWN";
};
~~~

### Bài C — nhiều điều kiện

Yêu cầu chỉ chấp nhận khi tuổi từ 18 trở lên, đã xác minh và chưa bị khóa.

**Nên chọn:** biểu thức boolean/ `if`:

~~~java
boolean accepted = age >= 18 && verified && !blocked;
~~~

Không dùng `switch` cho ba điều kiện độc lập này.

### Bài D — hai hành động có thể cùng đúng

Với điểm 95, cần ghi đồng thời PASS và EXCELLENT.

**Đáp án:** hai `if` độc lập, không dùng `else if`:

~~~java
if (score >= 50) System.out.println("PASS");
if (score >= 90) System.out.println("EXCELLENT");
~~~

**Exact stdout:**

~~~text
PASS
EXCELLENT
~~~

### Bài E — switch và null

~~~java
String command = null;
// switch (command) {
//     case "start" -> System.out.println("start");
//     default -> System.out.println("unknown");
// }
~~~

**Đáp án:** không dùng `default` để thay null guard; selector String null gây NPE trong Java 17 ổn định. Kiểm tra `if (command == null)` trước khi switch.

## 15. Checklist và kết luận

- [ ] Chọn `if` khi cần so sánh quan hệ, khoảng giá trị, điều kiện kết hợp hay ưu tiên.
- [ ] Chọn `switch` khi xét nhiều **giá trị rời rạc của cùng một selector** hợp lệ.
- [ ] Phân biệt chuỗi `else if` với nhiều `if` độc lập.
- [ ] Kiểm tra biên dưới, biên trên và các mốc phân loại.
- [ ] Không đặt điều kiện rộng lên trước để che mất nhánh cụ thể.
- [ ] Biết các kiểu selector được hỗ trợ trong Java 17 không bật preview.
- [ ] Biết switch String so nội dung và phân biệt hoa/thường.
- [ ] Kiểm tra null trước switch với String, enum và wrapper.
- [ ] Biết switch statement `case:` có thể fall-through; `case ->` không.
- [ ] Biết switch expression tạo kết quả và dùng `yield` khi nhánh là block.
- [ ] Không lạm dụng `default` để che trường hợp enum mới quan trọng.
- [ ] Đừng quyết định theo tốc độ mà không có đo đạc phù hợp.
- [ ] Chạy `IfOrSwitchDecisionDemo.java` bằng Java 17+ và đối chiếu exact stdout.

**Kết luận:** Hãy chọn `if` để **diễn đạt một điều kiện boolean** (ngưỡng, miền, tổ hợp, ưu tiên), và chọn `switch` để **phân phối xử lý theo một tập giá trị rời rạc đã biết**. Hai cấu trúc có thể kết hợp: dùng `if` để bảo vệ/validate, dùng `switch` để chọn hành động. Điều quan trọng nhất là **đúng bản chất điều kiện, rõ biên và dễ bảo trì**.

</details>

- [Quay lại đầu trang](#back-to-top)
