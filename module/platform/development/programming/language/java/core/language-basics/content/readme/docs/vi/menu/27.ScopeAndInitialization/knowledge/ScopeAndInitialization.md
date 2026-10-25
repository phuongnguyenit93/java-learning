<a id="back-to-top"></a>

# Phạm vi và khởi tạo

## Menu
- [Phạm vi tên trong khối, vòng lặp và phương thức](#scope-and-initialization-001)
- [Thời gian tồn tại của biến khác phạm vi sử dụng tên](#scope-and-initialization-002)
- [Biến cục bộ trùng tên trong khối lồng và các khai báo không hợp lệ](#scope-and-initialization-003)
- [Tham số hoặc biến cục bộ che khuất trường](#scope-and-initialization-004)
- [Dùng this hoặc tên lớp để chỉ rõ trường bị che khuất](#scope-and-initialization-005)
- [Kiểm tra biến đã được gán trên mọi nhánh trước khi sử dụng](#scope-and-initialization-006)
- [final có thể được gán trong các nhánh loại trừ nhau](#scope-and-initialization-007)
- [Giữ phạm vi biến nhỏ và khai báo gần nơi sử dụng](#scope-and-initialization-008)

## <a id="scope-and-initialization-001">Phạm vi tên trong khối, vòng lặp và phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Phạm vi tên trong khối, vòng lặp và phương thức — Java 17+

> **Java core — language-basics / Phạm vi và khởi tạo**  
> **Mục tiêu:** Xác định một tên biến/tham số có thể dùng ở đâu, vì sao tên biến trong vòng lặp không dùng được bên ngoài, lúc nào được dùng lại tên, và phân biệt **phạm vi tên (scope)** với **biến đã được gán giá trị (definite assignment)**.

## 1. Phạm vi tên (scope) là gì?

*Scope* là **vùng mã nguồn mà khai báo một tên có hiệu lực để tham chiếu tới thực thể đó**. Với biến cục bộ khai báo trong một khối lệnh, phạm vi thông thường kéo dài từ vị trí khai báo đến cuối khối chứa khai báo; không tự mở rộng ra ngoài dấu `}`.

~~~java
static void example() {
    int outer = 10;
    {
        int inner = 5;
        System.out.println(outer + inner); // 15: thấy cả hai tên.
    }
    System.out.println(outer); // 10.
    // System.out.println(inner); // LỖI BIÊN DỊCH: inner ngoài scope.
}
~~~

Dấu `{ ... }` có thể mở ra một khối trong phương thức, nhánh `if`, thân vòng lặp hoặc `try`. Quy tắc phạm vi giúp giới hạn chỗ có thể sử dụng tên; **không** có nghĩa đối tượng tham chiếu từ biến nhất thiết bị hủy ngay khi rời khối.

Ba câu hỏi hữu ích khi gặp lỗi `cannot find symbol`:

1. Tên đã được khai báo **trước vị trí đang dùng** và trong vùng nhìn thấy chưa?
2. Tên thuộc **biến cục bộ**, **tham số**, **biến vòng lặp** hay **trường của lớp/đối tượng**?
3. Nếu nhìn thấy tên, biến đã được **gán chắc chắn** trước lúc đọc chưa?

## 2. Khối lệnh `{ }`: biến ngoài nhìn vào được, biến trong không đi ra được

~~~java
int total = 0;

if (total == 0) {
    int adjustment = 3;
    total += adjustment; // Sửa được total của khối ngoài.
}
System.out.println(total); // 3.
// System.out.println(adjustment); // LỖI: adjustment hết scope.
~~~

Trong một khối, tên của biến cục bộ có phạm vi từ **khai báo của nó** về phía sau, không phải từ đầu khối:

~~~java
{
    // System.out.println(score); // LỖI: score chưa được khai báo.
    int score = 8;
    System.out.println(score); // Hợp lệ.
}
~~~

Hai khối **không chồng lấn** có thể khai báo lại cùng một tên:

~~~java
{
    int temp = 7;
    System.out.println(temp);
}
{
    int temp = 8; // Hợp lệ: temp trước đã hết scope.
    System.out.println(temp);
}
~~~

Nhưng Java **không cho khai báo lại biến cục bộ cùng tên trong phạm vi chồng lấn**, kể cả ở khối con:

~~~java
// KHÔNG BIÊN DỊCH:
// int count = 1;
// {
//     int count = 2; // Tên count ngoài vẫn đang có hiệu lực.
// }
~~~

Đây là khác biệt quan trọng so với việc *che khuất trường của đối tượng* bằng một biến cục bộ, được trình bày ở phần 6.

## 3. Biến trong vòng `for` thông thường

Biến khai báo trong phần **khởi tạo** của `for` có thể dùng tại phần điều kiện, phần cập nhật và thân vòng lặp; nó không có hiệu lực sau câu lệnh `for`:

~~~java
for (int i = 0; i < 3; i++) {
    System.out.println("i=" + i);
}
// System.out.println(i); // LỖI BIÊN DỊCH: i hết scope.
~~~

Có thể dùng lại tên biến trong hai vòng `for` độc lập, nối tiếp:

~~~java
for (int i = 0; i < 2; i++) {
    System.out.print(i); // 01.
}
for (int i = 2; i < 4; i++) {
    System.out.print(i); // 23.
}
~~~

Nhưng không thể khai báo `int i` ở một `for` nằm bên trong thân của `for` khác khi `i` của vòng ngoài vẫn còn scope:

~~~java
// KHÔNG BIÊN DỊCH:
// for (int i = 0; i < 2; i++) {
//     for (int i = 0; i < 3; i++) { } // i bị khai báo trùng.
// }
~~~

Để duyệt lồng nhau, dùng các tên khác như `row` và `column`. Nếu khai báo biến **trước** vòng lặp, nó thuộc phạm vi của khối bao ngoài, nên có thể đọc sau vòng lặp:

~~~java
int i = 0;
for (; i < 3; i++) {
    // Thân có thể dùng i.
}
System.out.println(i); // 3: i được khai báo bên ngoài for.
~~~

## 4. Enhanced `for` và biến khai báo trong thân vòng lặp

Với enhanced `for`, biến phần tử `element` được khai báo ở đầu vòng lặp và được dùng trong **thân** của vòng:

~~~java
for (String word : new String[] {"A", "B"}) {
    System.out.println(word);
}
// System.out.println(word); // LỖI: word không tồn tại ở đây.
~~~

Biểu thức nguồn ở bên phải dấu `:` **không dùng được biến phần tử mới khai báo** như một tên đã có sẵn. Mỗi lần lặp, biến này nhận giá trị phần tử tương ứng. Nếu nguồn chứa kiểu tham chiếu, giá trị nhận được là **bản sao tham chiếu**, không có tác dụng kéo dài scope ra ngoài vòng.

Trong thân `for`/`while`, biến mới khai báo chỉ dùng được trong thân hoặc khối con phù hợp:

~~~java
int sum = 0;
for (int n = 1; n <= 3; n++) {
    int part = n * 2;
    sum += part;
}
// System.out.println(part); // LỖI: part nằm trong thân vòng.
// System.out.println(n);    // LỖI: n thuộc phạm vi for.
System.out.println(sum); // 12: sum khai báo bên ngoài.
~~~

Nếu cần kết quả sau khi duyệt, hãy khai báo biến kết quả trong khối **bao ngoài**, như `sum`, và cập nhật nó trong vòng lặp. Tránh cố đọc một biến tạm chỉ được khai báo trong thân vòng.

## 5. `while`, `if` và tham số của phương thức

Một biến cục bộ tạo trong thân `while` có scope thuộc thân đó; biến điều khiển khai báo ở ngoài thì tiếp tục nhìn thấy sau vòng:

~~~java
int turn = 0;
while (turn < 2) {
    int step = turn + 10;
    System.out.println(step); // 10 rồi 11.
    turn++;
}
// System.out.println(step); // LỖI: ngoài scope.
System.out.println(turn); // 2.
~~~

Trong `if`/`else`, biến khai báo **trong nhánh** chỉ nhìn thấy ở nhánh đó. Nếu cần một kết quả chung, khai báo biến ở khối bao ngoài và gán trong các nhánh:

~~~java
static int choose(boolean flag) {
    int result;
    if (flag) {
        result = 1;
    } else {
        result = 2;
    }
    return result; // Cả hai nhánh đã gán.
}
~~~

Tham số hình thức của một phương thức có scope trong **thân phương thức đó**, gồm các khối lồng bên trong; chúng không xuất hiện trong phương thức gọi hoặc phương thức khác:

~~~java
static String format(int input) {
    String prefix = "P";
    return prefix + input;
}
// Ở phương thức khác, không thể dùng riêng tên input hoặc prefix.
~~~

Không được khai báo `int input` trong một khối lồng của chính `format` vì tên tham số `input` đang có hiệu lực. Các phương thức khác nhau có thể dùng cùng tên tham số mà không xung đột.

## 6. Trường của đối tượng và che khuất tên (*shadowing*)

Phạm vi một **field** không giống phạm vi local: trong phương thức instance, tham số hoặc biến cục bộ được phép trùng tên field. Khi ấy tên ngắn chọn biến gần hơn, còn `this.count` chỉ đúng field của đối tượng:

~~~java
class Counter {
    private int count = 100;
    void show() {
        int count = 3;
        System.out.println(count);      // 3: local
        System.out.println(this.count); // 100: instance field
    }
}
~~~

Với field `static`, viết `ClassName.field` để phân biệt với local cùng tên; trong phương thức `static` không có `this`. Hai khai báo **local-local** chồng phạm vi vẫn bị cấm, dù local trùng field là hợp lệ. Bài về *tham số/biến cục bộ che khuất trường* sẽ phân tích kỹ lỗi tự gán `value = value;` và cách sửa.

## 7. Scope khác với *definite assignment* và vòng đời dữ liệu

Có thể **nhìn thấy tên**, nhưng vẫn **chưa được phép đọc biến cục bộ** nếu Java không chứng minh được nó đã được gán giá trị:

~~~java
// KHÔNG BIÊN DỊCH:
// static int bad(boolean condition) {
//     int result;
//     if (condition) {
//         result = 1;
//     }
//     return result; // Có đường đi condition=false chưa gán.
// }
~~~

Trong ví dụ `choose` ở phần 5, hai nhánh `if` và `else` đều gán `result` trước lúc `return`, nên biên dịch thành công.

| Khái niệm | Câu hỏi cần trả lời |
| --- | --- |
| **Scope** | Ở vị trí này có thể tham chiếu đến **tên** đã khai báo không? |
| **Definite assignment** | Trước khi **đọc giá trị** biến cục bộ, mọi đường đến đây đã gán nó chưa? |
| **Lifetime / thời gian tồn tại** | Giá trị, vùng nhớ hoặc đối tượng liên quan còn tồn tại trong bao lâu? |

Mỗi lần gọi một phương thức thông thường tạo ngữ cảnh gọi riêng cho tham số và biến cục bộ. Nếu trả về tham chiếu tới một đối tượng, đối tượng đó **có thể tiếp tục tồn tại** sau khi tên biến cục bộ đi ra khỏi scope; thời điểm thu gom rác không được quyết định chỉ bằng dấu `}`.

`final` ngăn gán lại biến sau khi đã được gán theo quy tắc Java, nhưng **không mở rộng scope** và không tự làm đối tượng mutable trở thành bất biến. Các pattern variable của `instanceof`/`switch` có quy tắc phạm vi theo luồng điều khiển riêng; không nên áp dụng máy móc quy tắc khối đơn giản cho chúng.

## 8. Chương trình Java 17+ chạy độc lập

Tạo file **ScopeBlocksDemo.java**:

~~~java
public final class ScopeBlocksDemo {
    private static int shared = 50;
    private int count = 100;

    private static int increment(int number) {
        int result = number + 1;
        return result;
    }

    private static String format(int input) {
        String prefix = "P";
        return prefix + input;
    }

    private static int choose(boolean flag) {
        int result;
        if (flag) {
            result = 1;
        } else {
            result = 2;
        }
        return result;
    }

    private void demonstrate() {
        int count = 3;
        System.out.println("localCount=" + count);
        System.out.println("fieldCount=" + this.count);

        {
            int inside = 5;
            System.out.println("inside=" + inside);
        }

        {
            int temp = 7;
            System.out.println("siblingA=" + temp);
        }
        {
            int temp = 8;
            System.out.println("siblingB=" + temp);
        }

        for (int i = 0; i < 3; i++) {
            System.out.println("forIndex=" + i);
        }

        for (String item : new String[] {"A", "B"}) {
            System.out.println("item=" + item);
        }

        int total = 0;
        for (int i = 1; i <= 3; i++) {
            int part = i * 2;
            total += part;
        }
        System.out.println("total=" + total);

        int turn = 0;
        while (turn < 2) {
            int step = turn + 10;
            System.out.println("whileStep=" + step);
            turn++;
        }
        System.out.println("afterWhile=" + turn);
    }

    public static void main(String[] args) {
        ScopeBlocksDemo demo = new ScopeBlocksDemo();
        demo.demonstrate();

        System.out.println("methodOne=" + increment(5));
        System.out.println("methodTwo=" + increment(10));
        System.out.println("format=" + format(4));
        System.out.println("assignedTrue=" + choose(true));
        System.out.println("assignedFalse=" + choose(false));

        int shared = 9;
        System.out.println("localShared=" + shared);
        System.out.println("fieldShared=" + ScopeBlocksDemo.shared);
    }
}
~~~

Biên dịch và chạy trong thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 ScopeBlocksDemo.java
java ScopeBlocksDemo
~~~

**Expected stdout chính xác:**

~~~text
localCount=3
fieldCount=100
inside=5
siblingA=7
siblingB=8
forIndex=0
forIndex=1
forIndex=2
item=A
item=B
total=12
whileStep=10
whileStep=11
afterWhile=2
methodOne=6
methodTwo=11
format=P4
assignedTrue=1
assignedFalse=2
localShared=9
fieldShared=50
~~~

Các dòng `siblingA`/`siblingB` chứng minh có thể dùng lại tên ở hai khối không chồng lấn; `forIndex` và `item` chỉ tồn tại trong vòng tương ứng; `total` có thể in sau vòng vì được khai báo bên ngoài; `localCount`/`fieldCount` chứng minh hiện tượng che khuất trường.

## 9. Checklist và bài tập

| Tình huống | Kiểm tra / cách giải quyết |
| --- | --- |
| `cannot find symbol` với tên vừa khai báo trong khối | Tên đã ra ngoài dấu `}` hoặc chưa tới vị trí khai báo? |
| Muốn dùng kết quả sau `for` | Khai báo biến kết quả ngoài `for` rồi cập nhật ở trong |
| Hai vòng `for (int i...)` nối tiếp | Hợp lệ nếu phạm vi hai biến `i` không chồng lấn |
| Vòng `for` lồng nhau cùng tên `i` | Không hợp lệ; đổi tên biến vòng trong |
| Khai báo local trùng local/parameter bên ngoài còn scope | Không hợp lệ trong vùng chồng lấn |
| Local trùng tên trường | Hợp lệ nhưng shadowing; cân nhắc `this.field` hoặc `ClassName.field` |
| Biến được khai báo nhưng chưa chắc được gán | Kiểm tra *definite assignment* trên mọi đường đi |
| Trả về đối tượng rồi rời khối | Đối tượng vẫn có thể tồn tại qua tham chiếu bên ngoài |

**Bài 1.** Khai báo `int sum = 0` ngoài vòng `for` và `int term` bên trong. Tính tổng bình phương từ 1 đến 4, sau đó in `sum`. Vì sao không in `term` sau vòng được?

**Bài 2.** Viết hai vòng `for (int i...)` nối tiếp, rồi thử khai báo một vòng cùng tên `i` lồng trong vòng trước. Dự đoán lỗi biên dịch.

**Bài 3.** Tạo hai khối liên tiếp cùng khai báo `String message`. Sau đó thử đặt chúng lồng nhau để thấy khi nào trùng tên bị cấm.

**Bài 4.** Viết phương thức nhận tham số `int count` và lớp có trường `count`. In giá trị tham số, trường và một local trong khối con; so sánh phạm vi các tên.

**Bài 5.** Viết hàm `int choose(boolean flag)` dùng biến `result` khai báo trước `if`. Thử bỏ nhánh `else` để thấy quy tắc *definite assignment*.

### Ghi nhớ

**Scope xác định tên dùng được ở đâu, không đồng nghĩa biến đã được gán hoặc đối tượng bị hủy khi hết khối.** Biến khai báo trong `{ }`/thân vòng lặp không dùng được ngoài vùng của nó; biến `for` khai báo ở phần khởi tạo cũng hết scope sau vòng. Tham số có hiệu lực trong thân phương thức. Các local cùng tên không được chồng phạm vi, nhưng local có thể che khuất field, truy cập field qua `this` hoặc tên lớp phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scope-and-initialization-002">Thời gian tồn tại của biến khác phạm vi sử dụng tên</a>

<details>
<summary>Xem chi tiết</summary>

# Thời gian tồn tại của biến khác phạm vi sử dụng tên trong Java

## 1. Hai khái niệm không đồng nhất

Khi nói về biến Java, cần phân biệt **scope** (*phạm vi sử dụng tên*) với **lifetime** (*thời gian tồn tại khi chạy chương trình*).

- **Phạm vi sử dụng tên (scope)** là phần văn bản mã nguồn mà trình biên dịch cho phép dùng **tên định danh** để tham chiếu tới một khai báo. Đây chủ yếu là quy tắc tĩnh, được kiểm tra khi biên dịch.
- **Thời gian tồn tại của biến (variable lifetime)** liên quan tới thời điểm một lần thực thi tạo và ngừng sử dụng thực thể biến đó. Nó phụ thuộc loại biến: biến cục bộ/tham số, trường của đối tượng, hay trường `static`.
- **Thời gian tồn tại của đối tượng** lại là chuyện riêng. Đối tượng được tạo bằng `new` có thể vẫn được tham chiếu sau khi biến cục bộ ban đầu ra khỏi scope. JVM quản lý khả năng thu gom đối tượng bằng tính *reachability* (còn có thể tới được từ các gốc tham chiếu); không có bảo đảm thu gom ngay khi kết thúc một khối lệnh.

**Quy tắc quan trọng:** Hết phạm vi gọi tên biến **không đồng nghĩa** với đối tượng từng được biến ấy trỏ tới đã bị hủy; và còn trong phạm vi cú pháp **không đồng nghĩa** giá trị của đối tượng bắt buộc phải tồn tại đến cuối scope theo một bố trí bộ nhớ cố định.

Ví dụ đoạn mã minh họa đặt bên trong một phương thức:

```java
{
    int count = 5;
    System.out.println(count); // Được phép: count đang trong scope
}
// System.out.println(count); // LỖI BIÊN DỊCH: không thấy tên count
```

Bên ngoài khối lệnh `{}`, tên `count` không còn thuộc scope; trình biên dịch từ chối dùng tên đó. Không cần quan sát việc JVM cấp phát/giải phóng bộ nhớ để kết luận lỗi trên.

## 2. Các loại biến và ý nghĩa của scope/lifetime

| Loại biến | Nơi khai báo và phạm vi sử dụng tên | Thời gian tồn tại của thực thể biến |
| --- | --- | --- |
| **Biến cục bộ (local)** | Trong khối lệnh, thân phương thức, vòng lặp, hoặc khối tương ứng theo quy tắc ngôn ngữ | Gắn với lần thực thi đoạn mã và các phạm vi thực thi tương ứng; mỗi lần gọi phương thức có các biến cục bộ riêng |
| **Tham số phương thức/constructor** | Trong thân phương thức/constructor được khai báo, với quy tắc scope tương ứng | Biến tham số riêng cho mỗi lần gọi |
| **Trường instance** (không `static`) | Thành viên của lớp, truy cập qua `this`/tham chiếu đối tượng theo quyền truy cập | Là một phần trạng thái của từng đối tượng; có từ khi đối tượng được tạo đến lúc đối tượng không còn tồn tại |
| **Trường `static`** | Thành viên của lớp, truy cập theo quy tắc thành viên và quyền truy cập | Có một bản cho mỗi lần định nghĩa lớp trong một runtime class loader; gắn với vòng đời lớp được nạp/khởi tạo, không gắn từng instance |

Bảng này mô tả mô hình ngôn ngữ ở mức lập trình, **không** khẳng định mọi biến local bắt buộc luôn nằm vật lý trên stack hay mọi đối tượng luôn nằm ở một vùng bộ nhớ cụ thể. JVM/JIT có thể tối ưu hóa bố trí và lưu trữ mà vẫn giữ đúng hành vi Java.

**Ví dụ phân biệt instance với `static`:**

```java
class Counter {
    static int totalCreated;
    int value;

    Counter(int value) {
        this.value = value;
        totalCreated++;
    }
}
```

Hai đối tượng `Counter` có hai trường `value` độc lập, còn `Counter.totalCreated` được chia sẻ trong cùng một lớp runtime. Scope của tên `value` trong lớp và thời gian tồn tại của trạng thái `value` trong từng đối tượng là hai vấn đề khác nhau.

### Khi một phương thức chạy nhiều lần

```java
static int square(int n) {
    int result = n * n;
    return result;
}
```

Mỗi lần gọi `square` tạo ngữ cảnh thực thi riêng cho tham số `n` và biến cục bộ `result`. Các lần gọi lồng nhau/đệ quy không dùng chung một biến local, dù trong mã nguồn chúng cùng có tên `result`. Sau `return`, nơi gọi nhận **giá trị trả về**, chứ không tiếp tục truy cập biến `result` theo tên.

## 3. Scope của tên biến trong khối và vòng lặp

Quy tắc cơ bản là tên local có thể được tham chiếu từ nơi khai báo đến hết khối thích hợp; biến khai báo trong phần khởi tạo `for` chỉ có phạm vi trong câu lệnh `for`:

~~~java
for (int i = 0; i < 2; i++) {
    int square = i * i;
    System.out.println(square); // 0, rồi 1
}
// i và square không còn trong scope ở đây.
~~~

Đây là quy tắc **mã nguồn**, không chỉ ra thời điểm GC hay cách JVM bố trí vật lý các biến. Trong vòng lặp, `i` tồn tại qua các lượt cập nhật chỉ số, còn biểu thức khởi tạo `square` được thực hiện lại mỗi khi đi vào thân vòng. Một lần gọi phương thức khác tạo ngữ cảnh local khác, dù chúng có cùng tên trong source.

Hai khối anh em có thể dùng cùng tên local vì phạm vi không chồng nhau; khối lồng không được khai báo lại tên local/parameter đang trong scope của khối ngoài. Muốn hiểu chính xác các lỗi khai báo trùng, xem bài về *biến cục bộ trùng tên*. Ở đây cần tập trung vào câu hỏi khác: **khi không còn gọi được tên, giá trị tham chiếu và đối tượng có tiếp tục tồn tại không?**

## 4. Biến tham chiếu hết scope nhưng đối tượng vẫn còn

Xét một phương thức tạo mảng rồi trả về:

```java
static int[] createValues() {
    int[] numbers = {3, 6, 9};
    return numbers;
}

int[] received = createValues();
System.out.println(received[1]); // 6
```

Ở cuối `createValues()`, tên local `numbers` không còn dùng được ở nơi gọi; lần thực thi phương thức cũng kết thúc. **Nhưng đối tượng mảng `int[]` vẫn có thể truy cập qua biến `received`**, vì phương thức trả về giá trị tham chiếu trỏ tới chính mảng ấy.

Tương tự, đối tượng có thể tiếp tục tồn tại khi tham chiếu tới nó được lưu ở field, phần tử mảng khác, hoặc cấu trúc dữ liệu còn sống. Thời gian tồn tại của **đối tượng** không bị ràng buộc bởi scope của **một biến local ban đầu**.

Ở chiều ngược lại, khi một biến tham chiếu vẫn ở trong scope, không nên suy ra đối tượng cũ vẫn cần thiết sau một phép gán lại:

```java
StringBuilder builder = new StringBuilder("old");
builder = new StringBuilder("new");
```

Biến `builder` vẫn đang dùng được, nhưng nó không còn trỏ tới đối tượng `StringBuilder("old")`. Nếu không có tham chiếu sống nào khác tới đối tượng cũ, đối tượng đó **có thể trở thành ứng viên thu gom rác**. Thời điểm JVM thật sự thu gom không được quy định và không nên dùng `System.gc()` để suy ra một thời gian sống chính xác.

## 5. Lambda và việc bắt giá trị local (*capture*)

Một lambda có thể sử dụng biến cục bộ của phạm vi bao ngoài khi biến đó là `final` hoặc **effectively final** (được gán tối đa một lần và không bị gán lại). Điều này không biến Java thành ngôn ngữ truyền tham số theo tham chiếu: lambda nắm giữ giá trị cần thiết để thực hiện hành vi sau đó.

```java
static Runnable makePrinter() {
    String message = "Xin chao";
    return () -> System.out.println(message);
}

Runnable printer = makePrinter();
printer.run(); // Xin chao
```

Lệnh `printer.run()` vẫn thực thi được **sau khi** `makePrinter()` đã trả về và tên `message` không còn tồn tại trong ngữ cảnh thực thi trước đó. Lambda giữ dữ liệu cần thiết để sử dụng `message`; với một giá trị tham chiếu, đối tượng được tham chiếu có thể vẫn tồn tại.

Không được gán lại biến local đã được lambda bắt:

```java
int count = 2;
Runnable show = () -> System.out.println(count);
// count++; // LỖI BIÊN DỊCH: count không còn effectively final
```

Nếu biến local tham chiếu tới **đối tượng mutable**, lambda vẫn có thể sửa trạng thái đối tượng ấy, miễn là không gán lại chính biến local đã bắt:

```java
int[] counter = {0};
Runnable increase = () -> counter[0]++;
increase.run();
// counter[0] == 1
```

Scope của tên `counter`, việc lambda giữ giá trị tham chiếu, và thời gian tồn tại của mảng là **ba yếu tố riêng biệt**. Không nên dùng mảng một phần tử chỉ để “lách” ràng buộc effectively final trong thiết kế chương trình; hãy chọn cấu trúc trạng thái có ý nghĩa rõ ràng khi thật sự cần.

## 6. Chương trình Java 17+ độc lập

Tạo tệp **`ScopeLifetimeDemo.java`**:

```java
import java.util.Arrays;

public class ScopeLifetimeDemo {
    private static int created;
    private int value;

    public ScopeLifetimeDemo(int value) {
        this.value = value;
        created++;
    }

    static int calculate(int input) {
        int result;
        {
            int temp = input * 2;
            result = temp + 1;
            System.out.println("Khoi mot: " + temp);
        }
        {
            int temp = input + 10; // Ten temp duoc su dung lai
            System.out.println("Khoi hai: " + temp);
        }
        return result;
    }

    static int[] makeArray() {
        int[] localArray = {3, 6, 9};
        return localArray; // Tham chieu thoat ra khoi phuong thuc
    }

    static Runnable makePrinter() {
        String message = "Hello";
        return () -> System.out.println("Lambda: " + message);
    }

    public static void main(String[] args) {
        int outer = 5;
        {
            int inside = outer * 2;
            System.out.println("Trong khoi: " + inside);
        }
        System.out.println("Ngoai khoi: " + outer);

        int answer = calculate(4);
        System.out.println("Ket qua: " + answer);

        ScopeLifetimeDemo object = new ScopeLifetimeDemo(12);
        System.out.println("Instance field: " + object.value);
        System.out.println("Static field: " + ScopeLifetimeDemo.created);

        int[] returned = makeArray();
        System.out.println("Mang sau return: " + Arrays.toString(returned));

        Runnable action = makePrinter();
        action.run();

        for (int i = 0; i < 2; i++) {
            System.out.println("Lap: " + i);
        }
    }
}
```

Trong thư mục chứa tệp, sử dụng JDK 17 trở lên trên Windows PowerShell/CMD hoặc Linux/macOS:

```bash
javac -encoding UTF-8 ScopeLifetimeDemo.java
java ScopeLifetimeDemo
```

Kết quả stdout **chính xác** (biên dịch thành công không in dòng nào):

```text
Trong khoi: 10
Ngoai khoi: 5
Khoi mot: 8
Khoi hai: 14
Ket qua: 9
Instance field: 12
Static field: 1
Mang sau return: [3, 6, 9]
Lambda: Hello
Lap: 0
Lap: 1
```

Giải thích kết quả:

1. `inside` chỉ được gọi tên bên trong khối, nhưng `outer` vẫn dùng được sau khối đó.
2. Hai biến `temp` ở hai khối anh em hợp lệ, dù cùng tên. `result` được khai báo bên ngoài hai khối, nên có thể nhận kết quả bên trong khối rồi được trả về.
3. `object.value` là trường của đối tượng `ScopeLifetimeDemo`, còn `created` là trường `static` của lớp.
4. Mảng tạo trong `makeArray()` vẫn được dùng sau khi phương thức kết thúc, vì giá trị tham chiếu được trả về.
5. Lambda trả về từ `makePrinter()` vẫn in `Hello`; điều này **không có nghĩa** local `message` vẫn đang truy cập được bằng tên từ `main`.
6. Biến đếm `i` của `for` có thể sử dụng trong vòng lặp, nhưng không thể gọi tên bên ngoài vòng lặp.

## 7. Thời gian tồn tại của field và các lưu ý thực tế

### Field instance

Khi tạo một đối tượng, trạng thái instance được khởi tạo theo các quy tắc Java. Mỗi đối tượng có các field instance riêng; field được giữ như một phần trạng thái của đối tượng khi đối tượng còn tồn tại. Một phương thức có thể kết thúc trong khi đối tượng vẫn được truy cập qua field của đối tượng khác, biến trả về, hay tham chiếu khác.

### Field `static`

Trường `static` thuộc về lớp tại runtime, không thuộc về từng đối tượng. Việc khởi tạo trường `static` xảy ra theo cơ chế khởi tạo lớp; các lần gọi phương thức hoặc tạo thêm instance không tạo một bản static mới cho mỗi instance. Không nên đơn giản hóa thành “trường static sống cho đến khi máy tính tắt”: vòng đời lớp và class loader có thể kết thúc khi điều kiện runtime cho phép.

### GC và `null`

- `reference = null` chỉ **thay giá trị của một biến tham chiếu**; nó không trực tiếp xóa đối tượng.
- Đối tượng có thể còn được tham chiếu từ nơi khác; ngay cả khi không còn truy cập được, thời điểm GC thu gom là không xác định.
- Biến local hết scope không phải một **hàm hủy** và không làm JVM buộc phải gọi cleanup.
- Đối với tài nguyên như file, socket, database connection, phải chủ động đóng đúng cách; ưu tiên `try-with-resources` cho đối tượng `AutoCloseable`, không đợi GC.

## 8. Các nhầm lẫn cần tránh

| Nhận định | Đánh giá | Vì sao? |
| --- | --- | --- |
| “Không gọi được tên biến sau dấu `}` nghĩa là đối tượng nó giữ đã bị hủy.” | Sai | Scope của tên và reachability của đối tượng độc lập. |
| “Biến cục bộ trong phương thức được chia sẻ giữa các lần gọi.” | Sai | Mỗi lần thực thi có biến cục bộ và tham số riêng, kể cả đệ quy. |
| “Mảng được trả về sẽ mất vì biến local ban đầu kết thúc.” | Sai | Giá trị tham chiếu trả về giữ khả năng truy cập mảng. |
| “`static` tạo một bản giá trị cho mỗi đối tượng.” | Sai | Field `static` gắn với lớp tại runtime. |
| “Dùng `System.gc()` bảo đảm đối tượng bị thu gom ngay.” | Sai | Không có bảo đảm thu gom tức thời. |
| “Lambda có thể giữ giá trị local để chạy sau khi phương thức tạo lambda kết thúc.” | Đúng | Quy tắc capture với biến final/effectively final cho phép hành vi này. |

**Ghi nhớ:** *Scope trả lời “ở đâu trong mã được viết tên biến?”; lifetime trả lời “một thực thể biến/đối tượng còn tồn tại trong lúc chạy đến khi nào?”*. Để hiểu đúng Java, hãy tiếp tục phân biệt **biến chứa giá trị tham chiếu** với **đối tượng mà giá trị ấy trỏ tới**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scope-and-initialization-003">Biến cục bộ trùng tên trong khối lồng và các khai báo không hợp lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Biến cục bộ trùng tên trong khối lồng và các khai báo không hợp lệ — Java 17+

## 1. Quy tắc cốt lõi

**Phạm vi (scope)** của một biến cục bộ xác định vùng mã nơi tên của nó có thể được sử dụng. Trong Java, khi một biến cục bộ đã nằm trong phạm vi, **không được khai báo một biến cục bộ khác cùng tên ở khối lồng bên trong**. Java báo lỗi biên dịch, ngay cả khi lập trình viên muốn biến trong “che” biến ngoài.

~~~java
static void invalid() {
    int count = 10;
    {
        // int count = 20; // LỖI BIÊN DỊCH: count đã được khai báo.
        System.out.println(count); // Hợp lệ: dùng count của khối ngoài.
    }
}
~~~

Điều này khác với việc **biến cục bộ hoặc tham số che khuất trường (field) của lớp**; trường vẫn có thể được truy cập qua `this.field` hoặc `ClassName.field`.

**Phân biệt cần nhớ:** cùng tên trong hai khối **có phạm vi không chồng lấn** có thể hợp lệ; cùng tên trong hai khối **lồng nhau khi biến ngoài còn trong phạm vi** thì không.

## 2. Các trường hợp hợp lệ và không hợp lệ

| Tình huống | Hợp lệ? | Giải thích |
|---|---|---|
| `int x=1; int x=2;` trong cùng khối | **Không** | Khai báo trùng tên biến cục bộ |
| Khai báo `x` trong khối con khi `x` của khối cha đang trong phạm vi | **Không** | Java cấm khai báo lại biến cục bộ trong phạm vi chồng lấn |
| Dùng lại `x` trong hai khối ngang hàng | **Có** | Hai phạm vi tách biệt |
| Khai báo `x` sau khi khối chứa một `x` khác đã kết thúc | **Có** | Phạm vi cũ không kéo dài ra ngoài khối |
| Tham số `x`, rồi khai báo `int x` trong thân hoặc khối con của phương thức | **Không** | Trùng tên với tham số còn trong phạm vi |
| Biến cục bộ `name` cùng tên field `name` | **Có** | Biến cục bộ che khuất field |
| Tham số `name` cùng tên field `name` | **Có** | Dùng `this.name` để truy cập instance field |
| Hai vòng `for (int i=...)` tuần tự | **Có** | `i` của vòng thứ nhất hết phạm vi trước vòng kế |
| Khai báo `int i` trong thân của vòng `for (int i=...)` | **Không** | `i` vòng ngoài còn trong phạm vi |
| Lambda khai báo tham số cùng tên local đang trong phạm vi | **Không** | Tham số lambda không được khai báo trùng biến cục bộ bao quanh |

**Lưu ý thuật ngữ:** *shadowing* của một field bởi local là hợp lệ; việc khai báo lại local đang trong phạm vi ở khối con không phải một cơ chế shadowing được Java cho phép.

## 3. Hai khối độc lập được phép dùng cùng tên

~~~java
static void siblings() {
    {
        int value = 1;
        System.out.println("first=" + value);
    }

    {
        int value = 2;
        System.out.println("second=" + value);
    }

    int value = 3;
    System.out.println("after=" + value);
}
~~~

**Exact stdout khi gọi `siblings()`:**

~~~text
first=1
second=2
after=3
~~~

Mỗi `value` được khai báo khi `value` ở khối trước **đã ra khỏi phạm vi**. Không có ba biến cùng tên cùng tồn tại trong một vùng khai báo chồng lấn.

Ngược lại:

~~~java
// KHÔNG BIÊN DỊCH nếu bỏ chú thích:
static void duplicate() {
    int value = 1;
    // int value = 2; // Trùng tên trong cùng khối.
    {
        // int value = 3; // Trùng local ở khối ngoài.
    }
}
~~~

Việc **gán lại** thì hoàn toàn khác với **khai báo lại**:

~~~java
int value = 1;
value = 2; // Hợp lệ: cập nhật biến đã tồn tại.
~~~

## 4. Tham số và biến đếm `for` cũng có phạm vi

Tham số phương thức có thể được đọc trong thân và các khối con của phương thức. Không được khai báo biến local trùng tên nó ở đó:

~~~java
static void process(int total) {
    // int total = 0; // Lỗi: trùng tham số.
    {
        // int total = 5; // Cũng lỗi.
        System.out.println(total);
    }
}
~~~

Với vòng `for`:

~~~java
for (int i = 0; i < 2; i++) {
    System.out.println("loopA=" + i);
    // int i = 99; // Lỗi: i của for đang trong phạm vi.
}

for (int i = 2; i < 4; i++) {
    System.out.println("loopB=" + i);
}
~~~

**Exact stdout:**

~~~text
loopA=0
loopA=1
loopB=2
loopB=3
~~~

Tên `i` có thể được sử dụng lại vì vòng `for` thứ nhất đã kết thúc **vùng phạm vi khai báo `i`**. Nhưng nếu khai báo `int i = 10;` ở khối bao quanh cả hai vòng, việc khai báo `for (int i = ...)` bên trong sẽ bị cấm.

## 5. Field shadowing là trường hợp khác

Một local/parameter cùng tên **field** vẫn hợp lệ, vì field không phải local của khối chứa method:

~~~java
class Person {
    private String name = "FIELD";
    void print(String name) {
        System.out.println(name);      // Tham số
        System.out.println(this.name); // Field
    }
}
~~~

Trong `static` context, dùng tên lớp để chỉ field `static` bị che khuất. Điều này khác hoàn toàn với khai báo lại local/parameter trong khối lồng đang chồng phạm vi; bài về field shadowing đi sâu vào việc cập nhật đúng field.

## 6. Khai báo trùng tên khác lỗi gán chưa chắc chắn

~~~java
static int choose(boolean flag) {
    int result;
    if (flag) result = 1;
    else result = 2;
    return result; // Đã gán ở cả hai đường tới return.
}
~~~

Ở đây chỉ có **một** biến `result` và không có khai báo trùng tên. Nếu bỏ `else`, lỗi khi đọc `result` là *definite assignment*, vì nhánh `false` có thể chưa gán; đây là phép kiểm tra khác với kiểm tra khai báo trùng phạm vi.

## 7. Lambda: không khai báo tham số trùng local bao quanh

~~~java
static void lambdaExample() {
    int bonus = 3;
    java.util.function.IntUnaryOperator plusBonus =
            number -> number + bonus;

    System.out.println("lambda=" + plusBonus.applyAsInt(4));
}
~~~

**Exact stdout:**

~~~text
lambda=7
~~~

Nếu dùng `bonus -> bonus + 1` làm tham số lambda ngay tại đây, tên tham số `bonus` sẽ trùng với local `bonus` bao quanh và không hợp lệ.

Biến local được lambda **capture** phải là `final` hoặc *effectively final* (không được gán lại sau khi khởi tạo). Đây là một yêu cầu khác ngoài quy tắc trùng tên:

~~~java
// int bonus = 3;
// bonus++;
// IntUnaryOperator op = x -> x + bonus; // Không hợp lệ: bonus không effectively final.
~~~

## 8. Chương trình Java 17+ hoàn chỉnh

Lưu đúng file **`LocalScopeNameDemo.java`**:

~~~java
public final class LocalScopeNameDemo {
    private String name = "FIELD";
    private static String label = "STATIC_FIELD";

    private static void nestedBlock() {
        int count = 10;
        {
            int increment = 5;
            System.out.println("nested total=" + (count + increment));
            // int count = 20; // Không hợp lệ.
        }
        System.out.println("outer count=" + count);
    }

    private static void siblingBlocks() {
        {
            int value = 1;
            System.out.println("first=" + value);
        }
        {
            int value = 2;
            System.out.println("second=" + value);
        }
        int value = 3;
        System.out.println("after=" + value);
    }

    private static void consecutiveLoops() {
        for (int i = 0; i < 2; i++) {
            System.out.println("loopA=" + i);
        }
        for (int i = 2; i < 4; i++) {
            System.out.println("loopB=" + i);
        }
    }

    private void showFields(String name) {
        System.out.println("parameter=" + name);
        System.out.println("instance field=" + this.name);
        String label = "LOCAL";
        System.out.println("local label=" + label);
        System.out.println("static field=" + LocalScopeNameDemo.label);
    }

    private static int initialized(boolean flag) {
        int result;
        if (flag) {
            result = 1;
        } else {
            result = 2;
        }
        return result;
    }

    private static void lambdaExample() {
        int bonus = 3;
        java.util.function.IntUnaryOperator add =
                value -> value + bonus;
        System.out.println("lambda=" + add.applyAsInt(4));
    }

    public static void main(String[] args) {
        nestedBlock();
        siblingBlocks();
        consecutiveLoops();
        new LocalScopeNameDemo().showFields("PARAMETER");
        System.out.println("assigned true=" + initialized(true));
        System.out.println("assigned false=" + initialized(false));
        lambdaExample();
    }
}
~~~

**Bash (Linux/macOS), JDK 17+:**

~~~bash
javac --release 17 -encoding UTF-8 LocalScopeNameDemo.java
java -cp . LocalScopeNameDemo
~~~

**PowerShell (Windows), JDK 17+:**

~~~powershell
javac --release 17 -encoding UTF-8 .\LocalScopeNameDemo.java
java -cp . LocalScopeNameDemo
~~~

### Exact expected stdout

~~~text
nested total=15
outer count=10
first=1
second=2
after=3
loopA=0
loopA=1
loopB=2
loopB=3
parameter=PARAMETER
instance field=FIELD
local label=LOCAL
static field=STATIC_FIELD
assigned true=1
assigned false=2
lambda=7
~~~

**Giải thích:** khối lồng đọc `count` của khối ngoài, nhưng không khai báo lại nó; ba khối không chồng lấn dùng lại tên `value`; hai vòng `for` tuần tự dùng lại `i`. Tham số `name` che khuất instance field, được phân biệt bằng `this.name`; local `label` che khuất static field, được phân biệt bằng tên lớp.

## 9. Lỗi thường gặp và bài tập

| Sai lầm | Nguyên nhân | Cách sửa |
|---|---|---|
| `int x=1;` rồi `int x=2;` | Trùng tên local cùng khối | Gán `x=2;` hoặc đổi tên |
| Khai báo `int x` ở khối trong khi local `x` ngoài còn hiệu lực | Phạm vi chồng lấn | Dùng biến cũ, hoặc chọn tên mới |
| Khai báo local trùng tham số | Tham số vẫn trong phạm vi | Đổi tên local |
| Khai báo local trùng biến đếm `for` bên trong thân `for` | `i` của vòng còn hiệu lực | Dùng chỉ số sẵn có |
| Nghĩ local trùng field luôn bị cấm | Field có thể bị local che khuất | Dùng `this.field` hoặc `ClassName.field` khi cần |
| Dùng biến sau khi ra khỏi khối khai báo | Tên hết phạm vi | Khai báo ở khối phù hợp |
| Sử dụng local chưa gán ở mọi đường | Definite assignment chưa thỏa | Gán ở tất cả đường đi |
| Lambda tham số trùng local bao quanh | Khai báo trùng tên | Đổi tên tham số lambda |
| Lambda capture local bị gán lại | Không effectively final | Không gán lại hoặc chuyển cách lưu trạng thái |

**Bài A.** Đoạn nào hợp lệ?

~~~java
{
    int n = 1;
}
{
    int n = 2;
}
~~~

**Đáp án:** hợp lệ, vì hai phạm vi riêng biệt.

**Bài B.** Vì sao đoạn này sai?

~~~java
int n = 1;
{
    // int n = 2;
}
~~~

**Đáp án:** `n` bên ngoài vẫn trong phạm vi tại khối con, nên không thể khai báo local `n` lần nữa ở đó.

**Bài C.** Với instance field `name` và tham số `name`, truy cập field ra sao? **`this.name`**.

**Bài D.** `int result;` rồi chỉ gán `result` trong `if (flag)` có dùng được sau `if` không? **Không chắc**; nếu `flag == false`, biến chưa khởi tạo. Cần gán ở nhánh `else` hoặc trước `if`.

## 10. Checklist và kết luận

- [ ] Biết phạm vi tên của biến cục bộ bắt đầu từ khai báo trong khối tương ứng.
- [ ] Không khai báo local trùng tên trong cùng khối hoặc khối con có phạm vi chồng lấn.
- [ ] Dùng lại tên trong hai khối tuần tự không chồng lấn khi hợp lý.
- [ ] Phân biệt khai báo lại với gán lại.
- [ ] Hiểu tham số và biến `for` cũng có phạm vi riêng.
- [ ] Phân biệt shadowing **field** hợp lệ với redeclaration **local** bị cấm.
- [ ] Dùng `this.field` hoặc `ClassName.field` để truy cập field bị che khuất.
- [ ] Phân biệt lỗi trùng tên với lỗi definite assignment.
- [ ] Nhớ lambda parameter không được trùng local bao quanh.
- [ ] Chạy `LocalScopeNameDemo.java` bằng Java 17+ và đối chiếu exact stdout.

**Kết luận:** Java không cho khai báo một biến cục bộ hoặc tham số mới **trùng tên local đang nằm trong phạm vi bao quanh**. Tên có thể tái sử dụng khi phạm vi trước đã kết thúc, và local/parameter có thể **che khuất field** của lớp. Xác định đúng phạm vi của từng tên sẽ tránh lỗi biên dịch và giúp chọn cách đặt tên dễ đọc.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scope-and-initialization-004">Tham số hoặc biến cục bộ che khuất trường</a>

<details>
<summary>Xem chi tiết</summary>

# Tham số hoặc biến cục bộ che khuất trường trong Java

> **Java core — language-basics / Phạm vi và khởi tạo**  
> **Môi trường:** JDK 17+, một file chạy độc lập, không sử dụng thư viện ngoài.  
> **Mục tiêu:** Nhận diện *shadowing* khi tham số/biến cục bộ trùng tên trường (field), dùng `this.field` hoặc `ClassName.staticField` để chọn đúng đối tượng cần truy cập, và tránh nhầm với lỗi khai báo trùng hai biến cục bộ.

## 1. Che khuất trường (*field shadowing*) là gì?

Một lớp có thể khai báo trường và một phương thức có thể khai báo **tham số hoặc biến cục bộ cùng tên**. Trong phạm vi của biến cục bộ/tham số, tên viết đơn lẻ thường chỉ đến khai báo **gần hơn**, khiến trường bị che khuất:

~~~java
public final class Sample {
    private int score = 10;

    public void show() {
        int score = 3; // Local score che khuất trường score.
        System.out.println(score);      // 3: local.
        System.out.println(this.score); // 10: field.
    }
}
~~~

Hai thực thể vẫn tồn tại riêng. Shadowing không xóa trường, không thay kiểu của trường và không tự chuyển dữ liệu giữa trường với biến cục bộ.

- `score`: tên đơn, được phân giải đến local `score` trong phương thức `show`.
- `this.score`: truy cập rõ ràng trường instance `score` của **đối tượng đang gọi** `show`.
- Sau khi rời phạm vi local, có thể dùng tên trường không bị che khuất ở ngữ cảnh instance phù hợp.

**Phân biệt:** Biến cục bộ che khuất **trường** là hợp lệ; nhưng khai báo một local mới trùng tên với local hoặc tham số còn đang có phạm vi chồng lấn thường là **lỗi biên dịch**.

## 2. Tham số trùng tên trường: ví dụ constructor và setter

Mẫu rất phổ biến trong Java:

~~~java
public final class Student {
    private String name;
    private int age;

    public Student(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public void setAge(int age) {
        this.age = age;
    }
}
~~~

Trong constructor `Student(String name, int age)`:

| Biểu thức | Đối tượng được tham chiếu |
| --- | --- |
| `name` | Tham số `name` của constructor |
| `this.name` | Trường `name` thuộc đối tượng hiện tại |
| `age` | Tham số `age` |
| `this.age` | Trường `age` thuộc đối tượng hiện tại |
| `this.age = age` | Gán **giá trị tham số** vào **trường instance** |

`this` là tham chiếu tới đối tượng hiện tại trong constructor và phương thức instance. Nếu đặt tên tham số khác tên trường (`studentName`), có thể viết `name = studentName;` khi không còn local nào che khuất `name`; tuy nhiên `this.name = studentName;` thường rõ nghĩa hơn.

Constructor không có kiểu trả về, còn setter như `setAge` là phương thức trả `void`. Cả hai đều có thể nhận tham số trùng tên trường.

## 3. Lỗi dễ bỏ sót: `value = value` chỉ tự gán tham số

~~~java
public final class Counter {
    private int value; // Mặc định 0.

    public void wrongSet(int value) {
        value = value; // Chỉ tự gán tham số, KHÔNG sửa field.
    }

    public void correctSet(int value) {
        this.value = value; // Sửa field.
    }

    public int current() {
        return value; // Không có local value nên đọc field.
    }
}
~~~

Nếu tạo `Counter counter = new Counter();` rồi gọi `counter.wrongSet(7)`, `counter.current()` vẫn bằng 0. Sau `counter.correctSet(7)`, kết quả mới là 7.

**Vì sao Java không báo lỗi?** Lệnh `value = value` đúng cú pháp: cả hai tên đều phân giải đến tham số local, nên đó là phép gán hợp lệ nhưng không có tác dụng mong muốn. Nhiều IDE hoặc static analyzer có thể cảnh báo dạng tự gán này.

Không nên khắc phục bằng cách biến tất cả trường thành `static`; cần dùng đúng tham chiếu `this` hoặc đổi tên tham số hợp lý.

## 4. Biến cục bộ cũng có thể che khuất trường

Shadowing không chỉ xảy ra ở danh sách tham số:

~~~java
public final class Gauge {
    private int level = 20;

    public void inspect() {
        int level = 5;
        System.out.println("local=" + level);      // local=5
        System.out.println("field=" + this.level); // field=20
        this.level += level;
        System.out.println("updated=" + this.level); // updated=25
    }
}
~~~

Ở đây giá trị local `level` và field `this.level` độc lập. Phép `this.level += level` dùng cả hai trong một biểu thức để cập nhật trường.

Với local cùng tên trường đặt trong **khối con**, tên local có hiệu lực trong vùng thích hợp:

~~~java
class Example {
    private int total = 100;

    void run() {
        System.out.println(total); // 100.
        {
            int total = 7;
            System.out.println(total);      // 7.
            System.out.println(this.total); // 100.
        }
        System.out.println(total); // 100: local trong khối đã hết scope.
    }
}
~~~

Local `total` của khối chỉ tồn tại về mặt phạm vi tên từ chỗ khai báo đến cuối khối. **Trường vẫn là trường của đối tượng**, không bị xóa khi local bắt đầu hoặc kết thúc scope.

## 5. Trường `static`: dùng tên lớp để tránh nhập nhằng

Trường `static` thuộc về lớp, không phải trạng thái riêng của một instance. Nếu local hoặc tham số có tên giống trường static, dùng `ClassName.field`:

~~~java
public final class AppSettings {
    private static String mode = "DEFAULT";

    public static void setMode(String mode) {
        AppSettings.mode = mode;
    }

    public static void show(String mode) {
        System.out.println("parameter=" + mode);
        System.out.println("classField=" + AppSettings.mode);
    }
}
~~~

`AppSettings.mode = mode` cập nhật trường của lớp bằng giá trị tham số; `mode = mode` chỉ tự gán tham số.

Trong một phương thức `static` **không có `this`**, nên `this.mode` là lỗi biên dịch. Nếu cần trường instance từ phương thức static, phải có một đối tượng thích hợp rồi truy cập thông qua tham chiếu đó (và theo quyền truy cập Java).

Một phương thức instance vẫn có thể truy cập trường static, nhưng nên dùng tên lớp để người đọc biết đây là trạng thái dùng chung. Trường static có thể bị thay đổi từ các lời gọi khác; local trùng tên không tạo ra một bản sao của trường static.

## 6. Phân biệt shadowing với trùng tên local và thiếu gán

Ba tình huống thường bị nhầm nhưng có kết quả biên dịch khác nhau:

| Tình huống | Kết quả |
| --- | --- |
| `private int count;` và phương thức nhận `int count` | Hợp lệ: tham số che khuất field; dùng `this.count` để đọc/ghi field |
| Phương thức nhận `int count` rồi khai báo thêm `int count` trong khối con | Không hợp lệ: tên tham số vẫn trong scope |
| `int count;` trong thân phương thức rồi đọc ngay | Không hợp lệ: local chưa được *definitely assigned* |

Một field `int` nhận giá trị mặc định 0 khi đối tượng được tạo; local `int` không tự nhận giá trị mặc định. Shadowing chỉ đổi cách **phân giải tên**, không thay đổi quy tắc khởi tạo của hai loại biến.

## 7. Hướng thiết kế để giảm nhầm lẫn

Dùng tên tham số giống trường (ví dụ `this.name = name`) là cách viết Java phổ biến, nhưng nên giữ mã dễ đọc:

- Trong constructor/setter, dùng nhất quán `this.field = field` khi có tên trùng nhau.
- Dùng `Type.staticField` để truy cập trạng thái của lớp, không giả định `this` có trong phương thức static.
- Tránh đặt local trùng tên field trong các khối quá dài nếu khiến người đọc liên tục phải phân biệt `value` với `this.value`.
- Cân nhắc `final` cho tham số/local không cần gán lại. `final` ngăn thay đổi giá trị biến theo quy tắc Java, nhưng **không** tự giải quyết shadowing hay khiến đối tượng mutable trở thành bất biến.
- Dùng getter hoặc các phương thức cập nhật có ý nghĩa nghiệp vụ nếu việc chỉnh sửa trường cần kiểm tra điều kiện, thay vì cho phép thay trực tiếp ở mọi nơi.
- Khi đọc lỗi hoặc điều tra bug, xác định rõ từng tên đang tham chiếu **local**, **parameter**, **instance field** hay **static field**.

Ví dụ truy cập trường instance từ static theo đối tượng được truyền vào:

~~~java
static int readCount(Counter counter) {
    return counter.current();
}
~~~

Những lựa chọn này giúp mã dễ bảo trì; bản thân từ khóa `this` không làm một phép gán đúng về mặt nghiệp vụ nếu điều kiện hoặc giá trị truyền vào đã sai.

## 8. Chương trình Java 17+ chạy độc lập

Tạo file **FieldShadowingDemo.java**:

~~~java
public final class FieldShadowingDemo {
    private static String status = "READY";

    private final String name;
    private int count; // Mặc định 0.

    public FieldShadowingDemo(String name) {
        this.name = name;
    }

    private void wrongSet(int count) {
        count = count; // Chỉ tự gán tham số.
    }

    private void setCount(int count) {
        this.count = count;
    }

    private void increase(int count) {
        this.count += count;
    }

    private void showLocal() {
        int count = 100;
        System.out.println("localCount=" + count);
        System.out.println("fieldCount=" + this.count);
    }

    private void showName() {
        String name = "Temporary";
        System.out.println("localName=" + name);
        System.out.println("instanceName=" + this.name);
    }

    private void showBlock() {
        String status = "BLOCK";
        System.out.println("blockStatus=" + status);
        System.out.println("classStatus=" + FieldShadowingDemo.status);
    }

    private static String statusPair(String status) {
        return status + "/" + FieldShadowingDemo.status;
    }

    private static void changeStatus(String status) {
        FieldShadowingDemo.status = status;
    }

    private static int readCount(FieldShadowingDemo demo) {
        return demo.count; // Có đối tượng để đọc field instance.
    }

    private static int plusOne(int count) {
        return count + 1;
    }

    public static void main(String[] args) {
        FieldShadowingDemo demo = new FieldShadowingDemo("Lan");
        System.out.println("constructorName=" + demo.name);
        System.out.println("defaultCount=" + demo.count);

        demo.wrongSet(8);
        System.out.println("afterWrong=" + demo.count);

        demo.setCount(8);
        System.out.println("afterSetter=" + demo.count);
        demo.showLocal();

        demo.increase(3);
        System.out.println("afterIncrease=" + demo.count);
        demo.showName();

        System.out.println("staticValue=" + FieldShadowingDemo.status);
        System.out.println("statusPair=" + statusPair("LOCAL"));
        changeStatus("ACTIVE");
        System.out.println("newStatus=" + FieldShadowingDemo.status);
        System.out.println("newPair=" + statusPair("PARAM"));

        demo.showBlock();
        System.out.println("afterBlockStatus=" + FieldShadowingDemo.status);
        System.out.println("readInstanceFromStatic=" + readCount(demo));
        System.out.println("methodResult=" + plusOne(4));

        {
            int temporary = 2;
            System.out.println("siblingOne=" + temporary);
        }
        {
            int temporary = 3;
            System.out.println("siblingTwo=" + temporary);
        }
    }
}
~~~

Biên dịch và chạy tại thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 FieldShadowingDemo.java
java FieldShadowingDemo
~~~

**Expected stdout chính xác:**

~~~text
constructorName=Lan
defaultCount=0
afterWrong=0
afterSetter=8
localCount=100
fieldCount=8
afterIncrease=11
localName=Temporary
instanceName=Lan
staticValue=READY
statusPair=LOCAL/READY
newStatus=ACTIVE
newPair=PARAM/ACTIVE
blockStatus=BLOCK
classStatus=ACTIVE
afterBlockStatus=ACTIVE
readInstanceFromStatic=11
methodResult=5
siblingOne=2
siblingTwo=3
~~~

`afterWrong=0` cho thấy tự gán tham số không cập nhật field; `afterSetter=8` và `afterIncrease=11` là hai cập nhật trường đúng cách qua `this.count`. `localName`/`instanceName` phân biệt local và field, còn `statusPair`/`newPair` chứng minh tham số trùng tên với trường static vẫn có thể được phân biệt qua tên lớp.

## 9. Checklist và bài tập

| Tình huống | Cần làm gì? |
| --- | --- |
| Constructor nhận `String name` và lớp có field `name` | Dùng `this.name = name;` |
| Setter chỉ viết `value = value;` | Kiểm tra lại: thường là lỗi tự gán tham số |
| Local `count` trùng với field `count` | Dùng `count` cho local, `this.count` cho field |
| Tham số `mode` trùng static field `mode` | Dùng `ClassName.mode` khi cần field |
| Dùng `this` trong static method | Không hợp lệ; dùng tên lớp hoặc tham chiếu đối tượng |
| Local mới trùng tham số ở khối lồng | Lỗi trùng khai báo, không phải field shadowing |
| Đọc local chưa gán | Không đạt definite assignment dù tên đang trong scope |
| Hai khối anh em cùng dùng tên `temp` | Hợp lệ khi hai phạm vi không chồng lấn |

**Bài 1.** Viết lớp `Account` có field `balance` và setter `setBalance(int balance)`. Dự đoán trạng thái với `balance = balance;` rồi sửa bằng `this.balance = balance;`.

**Bài 2.** Tạo một phương thức có local trùng tên field, in cả `name` và `this.name`. Điều gì thay đổi khi local ra khỏi khối?

**Bài 3.** Khai báo `static int limit` và phương thức `static void change(int limit)`. Vì sao `limit = limit;` không cập nhật field? Sửa bằng tên lớp.

**Bài 4.** Thử khai báo local `int count` bên trong một phương thức đã nhận tham số `int count`; lý giải lỗi biên dịch, rồi chuyển local đó sang một khối anh em không trùng scope.

**Bài 5.** Phân biệt một field `int value;` chưa gán tường minh với một local `int value;` chưa gán. Biến nào được đọc ngay và tại sao?

### Ghi nhớ

**Tham số/biến cục bộ có thể che khuất field cùng tên mà không xóa field.** Trong phương thức/constructor instance, dùng `this.field` để truy cập đúng trường; trong ngữ cảnh static, dùng `ClassName.staticField`. Tránh lỗi `value = value`, phân biệt shadowing field hợp lệ với local-local trùng phạm vi không hợp lệ, và nhớ rằng scope không thay thế kiểm tra definite assignment.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scope-and-initialization-005">Dùng this hoặc tên lớp để chỉ rõ trường bị che khuất</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng `this` hoặc tên lớp để chỉ rõ trường bị che khuất trong Java

## 1. Vì sao cần chỉ rõ field?

Trong constructor và method, một local/parameter có thể trùng tên field. Khi đó tên ngắn thường chọn local/parameter. Ví dụ:

~~~java
class Account {
    private int balance;
    Account(int balance) {
        this.balance = balance; // Field nhận giá trị từ tham số.
    }
}
~~~

Nếu chỉ viết `balance = balance;`, mã hợp lệ nhưng tự gán tham số, bỏ qua field. Đây là một trường hợp *shadowing*; phần giải thích các lỗi constructor/setter đã được thực hành ở bài trước. Bài này tập trung vào **các cú pháp chọn field**, đặc biệt khi có kế thừa hoặc lớp bao ngoài.

## 2. Phân biệt `this.field`, `ClassName.field` và `super.field`

| Cú pháp | Nên dùng để truy cập | Bối cảnh hợp lệ | Ý nghĩa |
| --- | --- | --- | --- |
| `field` | Biến/tham số gần nhất mang tên đó; nếu không có, có thể là field | Tùy phạm vi | Tên ngắn có thể bị che khuất |
| `this.field` | Field **instance** của đối tượng hiện tại | Constructor/phương thức/khối instance | Bỏ qua việc tên ngắn bị tham số/local che khuất |
| `ClassName.field` | Field **static** được khai báo hoặc kế thừa phù hợp trên lớp | Kể cả phương thức `static` | Chỉ rõ field thuộc lớp, không thuộc từng instance |
| `super.field` | Field instance được tìm từ lớp cha | Ngữ cảnh instance của lớp con | Phân biệt field lớp cha với field cùng tên trong lớp con |
| `object.field` | Field truy cập được qua một biến tham chiếu đối tượng | Khi có đối tượng và quyền truy cập | Chỉ rõ instance, có kiểm tra quyền truy cập |

**Lưu ý:** `ClassName.instanceField` **không hợp lệ**, vì field instance phải truy cập qua một đối tượng (`this` hoặc tham chiếu cụ thể). Ngược lại, Java cho phép một số cách truy cập field `static` qua tham chiếu đối tượng, nhưng nên sử dụng `ClassName.staticField` để tránh gây hiểu nhầm và cảnh báo từ công cụ phân tích mã.

Một trường có `private` vẫn có thể truy cập bằng `this.field` từ **bên trong chính lớp**. `this` không bỏ qua quy tắc quyền truy cập: lớp khác không thể đọc field `private` chỉ nhờ viết `someObject.field`.

## 3. Chọn đúng field instance bằng `this.field`

`this` chỉ đối tượng hiện tại của constructor/method instance. Nó cho phép chọn field ngay cả khi tên ngắn đang trỏ tới tham số hoặc local:

~~~java
class Student {
    private String name;
    Student(String name) { this.name = name; }
    String display() {
        String name = "LOCAL";
        return name + "/" + this.name;
    }
}
~~~

Với `new Student("Mai").display()`, kết quả là `LOCAL/Mai`. Trong `static` method không có `this`. Nếu không có local/parameter che khuất, dùng tên field ngắn vẫn có thể hợp lệ; `this.` giúp thể hiện rõ trạng thái instance.

## 4. Chọn đúng field `static` bằng `ClassName.field`

Với trạng thái chung của lớp, viết tên lớp trước field để tránh nhầm với local/parameter:

~~~java
class Settings {
    private static int timeout = 30;
    static void configure(int timeout) {
        Settings.timeout = timeout;
    }
}
~~~

`Settings.timeout` cập nhật field, trong khi `timeout` đơn lẻ ở vế phải là tham số. `ClassName.instanceField` không hợp lệ vì field instance cần đối tượng; truy cập field `static` qua đối tượng tuy có thể hợp lệ trong Java nhưng khiến mã khó hiểu. Kế thừa field cùng tên lại đặt ra câu hỏi khác: nên dùng `this.field` hay `super.field`?

## 5. Khi lớp con che khuất trường lớp cha: `this` và `super`

Hai lớp trong quan hệ kế thừa có thể khai báo **hai field riêng biệt cùng tên**. Đây là trường hợp *field hiding*. Không giống phương thức instance bị override, **truy cập field không sử dụng dynamic dispatch** theo kiểu runtime của đối tượng; field được chọn dựa trên kiểu tham chiếu và quy tắc tra cứu tại compile time.

```java
class Base {
    int level = 10;
}

class Derived extends Base {
    int level = 20;

    void inspect(int level) {
        System.out.println(level);       // Tham số
        System.out.println(this.level);  // Field Derived
        System.out.println(super.level); // Field Base
    }
}
```

Trong `Derived.inspect(int level)`:

- `level` là **tham số**.
- `this.level` trỏ tới field của `Derived`.
- `super.level` chọn field kế thừa từ `Base`.

Cả `Base.level` và `Derived.level` đều nằm trong cùng đối tượng `Derived`; không phải JVM tự chuyển chúng thành một trường duy nhất.

```java
Base baseView = new Derived();
Derived derivedView = (Derived) baseView;
// baseView.level == 10
// derivedView.level == 20
```

Sự khác biệt xảy ra vì kiểu biểu thức `baseView` là `Base` còn `derivedView` là `Derived`. Điều này không phải đa hình động của phương thức. `super.field` chỉ sử dụng trong ngữ cảnh instance phù hợp của lớp con và vẫn chịu giới hạn quyền truy cập của field lớp cha.

Nếu field **static** cùng tên tồn tại ở cả lớp cha và lớp con, ưu tiên viết rõ `Base.field` hoặc `Derived.field` (với quyền truy cập hợp lệ), không dựa vào tên ngắn.

## 6. Chương trình Java 17+ chạy độc lập

Tạo tệp **`FieldShadowDemo.java`**:

```java
public class FieldShadowDemo {
    private String title = "Mac dinh";
    private int level;
    private static int created = 0;

    FieldShadowDemo(String title, int level) {
        this.title = title;
        this.level = level;
        created++;
    }

    void setLevel(int level) {
        this.level = level;
    }

    void showLocalShadowing() {
        String title = "Bien cuc bo";
        System.out.println("local title: " + title);
        System.out.println("this.title: " + this.title);
    }

    static void increaseCreated(int created) {
        FieldShadowDemo.created += created;
    }

    static class Parent {
        int code = 1;
    }

    static class Child extends Parent {
        int code = 2;

        void show(int code) {
            System.out.println("parameter code: " + code);
            System.out.println("this.code: " + this.code);
            System.out.println("super.code: " + super.code);
        }
    }

    public static void main(String[] args) {
        FieldShadowDemo example = new FieldShadowDemo("Java", 1);
        System.out.println("level before: " + example.level);
        example.setLevel(4);
        System.out.println("level after: " + example.level);
        example.showLocalShadowing();

        System.out.println("created before: " + FieldShadowDemo.created);
        increaseCreated(2);
        System.out.println("created after: " + FieldShadowDemo.created);

        Child child = new Child();
        child.show(3);
        Parent asParent = child;
        System.out.println("parent view: " + asParent.code);
        System.out.println("child view: " + child.code);
    }
}
```

Tại thư mục chứa tệp, dùng JDK 17 trở lên (PowerShell, CMD, bash):

```bash
javac -encoding UTF-8 FieldShadowDemo.java
java FieldShadowDemo
```

Kết quả stdout chính xác (lệnh `javac` thành công không in gì):

```text
level before: 1
level after: 4
local title: Bien cuc bo
this.title: Java
created before: 1
created after: 3
parameter code: 3
this.code: 2
super.code: 1
parent view: 1
child view: 2
```

Giải thích:

1. Constructor và setter dùng `this.level` để tác động đúng field instance. Biến cục bộ `title` chỉ che khuất tên field trong phương thức, không xóa `this.title`.
2. Tham số `created` của phương thức `increaseCreated` không phải field `static created`. Câu lệnh `FieldShadowDemo.created += created` cập nhật field từ `1` thành `3`.
3. `Child.show` có ba cách hiểu khác nhau cho tên `code` như trong ví dụ: tham số `3`, field lớp con `2` và field lớp cha `1`.
4. `asParent.code` đọc field từ kiểu `Parent`, còn `child.code` đọc field từ kiểu `Child` mặc dù cả hai biến tham chiếu cùng một đối tượng.

## 7. Trường hợp tên trường ở lớp bao ngoài

Trong **inner class không `static`**, muốn truy cập field của đối tượng lớp bao ngoài khi tên bị che khuất, dùng `Outer.this.field`:

```java
class Outer {
    int size = 10;

    class Inner {
        int size = 20;

        void print(int size) {
            System.out.println(size);            // Tham số
            System.out.println(this.size);       // Field Inner
            System.out.println(Outer.this.size); // Field Outer
        }
    }
}
```

Đoạn này minh họa trong lớp (không phải chương trình độc lập). `Outer.this` là tham chiếu đến instance `Outer` bao quanh instance `Inner`. Nó **không áp dụng tương tự** cho lớp lồng nhau khai báo `static`, vì lớp lồng nhau static không tự mang một tham chiếu instance lớp ngoài.

## 8. Lỗi thường gặp và cách chọn cú pháp

| Lỗi/nhầm lẫn | Cách hiểu hoặc sửa |
| --- | --- |
| Constructor viết `name = name;` và tưởng field được gán | Hai tên thường cùng trỏ vào tham số; dùng `this.name = name;` |
| Trong `static` method viết `this.field` | Không có `this` trong static context; field static viết `ClassName.field`, instance cần đối tượng |
| Gọi `ClassName.instanceField` | Field instance không truy cập như field static |
| Dùng `this.field` để chọn field lớp cha khi lớp con có field trùng tên | Dùng `super.field` nếu field lớp cha có thể truy cập |
| Tưởng hai field cùng tên trong quan hệ kế thừa là một | Field hiding tạo hai field phân biệt; kiểu biểu thức quyết định field được chọn |
| Tưởng `this` vượt qua mọi giới hạn truy cập | `this` chỉ là tham chiếu hiện tại, không thay đổi quyền truy cập Java |
| Dùng `object.staticField` như field instance | Viết `ClassName.staticField` để diễn đạt rõ field thuộc lớp |

**Ghi nhớ:** dùng `this.field` để gọi rõ **field instance của đối tượng hiện tại**, dùng `ClassName.field` để gọi rõ **field static của lớp**, và dùng `super.field` khi cần **field của lớp cha** bị field lớp con che khuất. Khi tên không trùng, qualifier vẫn có thể giúp mã dễ đọc hơn, nhưng nên lựa chọn nhất quán với kiểu field.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scope-and-initialization-006">Kiểm tra biến đã được gán trên mọi nhánh trước khi sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra biến đã được gán trên mọi nhánh trước khi sử dụng — Java 17+

## 1. Quy tắc definite assignment

Trong Java, **biến cục bộ phải chắc chắn đã được gán giá trị trước khi đọc** (*definite assignment*). Trình biên dịch phân tích các đường điều khiển có thể đi tới phép đọc, thay vì chỉ xét một lần chạy thử.

~~~java
static int choose(boolean flag) {
    int result;                 // Hợp lệ: chỉ khai báo.
    if (flag) {
        result = 10;
    } else {
        result = 20;
    }
    return result;              // Hợp lệ: cả hai nhánh đều gán.
}
~~~

Với `choose(true)`, kết quả là 10; với `choose(false)`, kết quả là 20. Ngược lại:

~~~java
// Không biên dịch nếu bỏ comment:
static int wrong(boolean flag) {
    int result;
    if (flag) {
        result = 10;
    }
    // return result; // variable result might not have been initialized
    return -1; // Dòng này chỉ để ví dụ vẫn biên dịch khi dòng sai bị comment.
}
~~~

**Điểm cốt lõi:** được phép **khai báo trước, gán sau**; nhưng ở mọi đường đi tới chỗ **đọc**, biến phải được gán chắc chắn.

## 2. Biến cục bộ không có giá trị mặc định

~~~java
int[] values = new int[2]; // Hai phần tử mặc định là 0.
int local;                 // Không tự động có giá trị 0.
// System.out.println(local); // Lỗi biên dịch: chưa gán.
local = 5;
System.out.println(local); // 5.
~~~

Phân biệt:

| Nơi lưu dữ liệu | Có giá trị mặc định? |
|---|---|
| Biến cục bộ `int x;` | **Không**; phải gán trước khi đọc |
| Tham số phương thức | Có giá trị do caller truyền vào khi lời gọi bắt đầu |
| Trường instance `int count;` | **Có**: mặc định 0 |
| Trường static `boolean ready;` | **Có**: mặc định false |
| Phần tử `new int[3]` | **Có**: các phần tử mặc định 0 |
| Biến cục bộ kiểu tham chiếu `String s;` | **Không**; không tự nhận null |
| Trường tham chiếu `String s;` | **Có**: mặc định null |

**Scope** (biến đang trong phạm vi) không đồng nghĩa với **definite assignment** (biến đã được gán chắc chắn).

## 3. `if/else`: phải xét mọi nhánh đi tới phép đọc

**Sai:**

~~~java
// static int bad(boolean active) {
//     int score;
//     if (active) score = 90;
//     return score; // Lỗi: active == false chưa gán.
// }
~~~

**Đúng — cả hai nhánh cùng gán:**

~~~java
static int score(boolean active) {
    int value;
    if (active) value = 90;
    else value = 0;
    return value;
}
~~~

**Đúng — khởi tạo trước rồi cập nhật có điều kiện:**

~~~java
int value = 0;
if (active) {
    value = 90;
}
System.out.println(value);
~~~

Trong đoạn sau, `value = 0` cung cấp giá trị cho mọi đường đi; `if` chỉ **ghi đè** khi điều kiện đúng. Hãy chọn cách biểu đạt đúng ý nghĩa dữ liệu, không gán số 0 chỉ để làm compiler hết báo lỗi nếu nghiệp vụ không cho phép số 0.

**Đúng — nhánh không gán thì kết thúc phương thức:**

~~~java
static int doublePositive(int input) {
    int result;
    if (input < 0) return -1;
    result = input * 2;
    return result;
}
~~~

Khi `input < 0`, không có đường đi tới `return result`. Vì vậy nhánh đó không cần gán `result`.

## 4. Nhiều nhánh và phép gán bằng biểu thức

Nếu có chuỗi `if / else if`, đừng quên tình huống **không nhánh nào đúng**.

~~~java
static String band(int n) {
    String result;
    if (n < 0) {
        result = "NEGATIVE";
    } else if (n == 0) {
        result = "ZERO";
    } else {
        result = "POSITIVE";
    }
    return result; // Mọi đường tới đây đã gán.
}
~~~

Nếu bỏ nhánh `else`, compiler không thể khẳng định `n > 0` cũng đã được gán.

Cũng có thể tạo giá trị trực tiếp bằng toán tử ba ngôi:

~~~java
int result = active ? 90 : 0;
~~~

Cả hai toán hạng kết quả cung cấp giá trị cho biểu thức; không cần tạo local chưa khởi tạo rồi tự chứng minh từng nhánh.

## 5. `switch`: dùng `default` hoặc switch expression hợp lệ

Với `switch statement`, các `case` có thể không bao phủ mọi giá trị:

~~~java
// static int badSwitch(int code) {
//     int result;
//     switch (code) {
//         case 1 -> result = 100;
//         case 2 -> result = 200;
//     }
//     return result; // Không chắc đã gán khi code khác 1,2.
// }
~~~

**Sửa bằng `default`:**

~~~java
static int statusCode(int code) {
    int result;
    switch (code) {
        case 1 -> result = 100;
        case 2 -> result = 200;
        default -> result = -1;
    }
    return result;
}
~~~

Hoặc dùng **switch expression** được Java 17 hỗ trợ:

~~~java
static int statusCodeExpression(int code) {
    return switch (code) {
        case 1 -> 100;
        case 2 -> 200;
        default -> -1;
    };
}
~~~

Switch expression bắt buộc cung cấp giá trị theo quy tắc exhaustiveness; với selector `int`, dùng `default` trong ví dụ giúp bao phủ các giá trị còn lại. Đừng lẫn **switch statement** với **switch expression**.

## 6. Vòng lặp: không mặc định body chắc chắn chạy

~~~java
// static int brokenLoop(boolean run) {
//     int result;
//     while (run) {
//         result = 7;
//         break;
//     }
//     return result; // Lỗi: while có thể có 0 lượt.
// }
~~~

Nếu điều kiện vòng lặp false ngay, phần thân không chạy và `result` chưa được gán.

**Cách rõ ràng nhất khi cần giá trị mặc định:**

~~~java
static int loopWithDefault(boolean run) {
    int result = -1;
    while (run) {
        result = 7;
        break;
    }
    return result;
}
~~~

Một vòng `while (true)` mà **mọi đường thoát qua `break` đều đã gán** cũng có thể chứng minh được definite assignment:

~~~java
static int fromLoop() {
    int result;
    while (true) {
        result = 7;
        break;
    }
    return result;
}
~~~

Trong ví dụ này, đường hoàn thành bình thường của vòng đi qua `result = 7` rồi mới `break`. Không suy rộng sang mọi vòng `while(true)`, nhất là khi có nhiều nhánh `break` hoặc ngoại lệ.

## 7. `try/catch`: mỗi đường tới sau khối đều cần gán

~~~java
static int parseOrDefault(boolean fail) {
    int result;
    try {
        if (fail) {
            throw new IllegalArgumentException("simulated");
        }
        result = 40;
    } catch (IllegalArgumentException ex) {
        result = -40;
    }
    return result;
}
~~~

Nếu `fail == false`, nhánh `try` gán 40; nếu `fail == true`, ngoại lệ được bắt và `catch` gán -40. Cả hai đường hoàn thành bình thường tới `return` đều đã gán.

**Lưu ý:** một `catch` không chắc luôn chạy; không nên cho rằng lệnh gán **chỉ trong catch** là đủ cho mọi đường đi. `finally` có quy tắc điều khiển luồng riêng; khi code phức tạp, nên giảm số nhánh hoặc khởi tạo kết quả ở nơi rõ ràng.

## 8. Biến `final`: gán đúng một lần trên từng đường thực thi

Biến cục bộ `final` chưa gán có thể được gán sau khi khai báo, nhưng không được **gán lại** sau khi đã gán:

~~~java
static int finalResult(boolean flag) {
    final int result;
    if (flag) {
        result = 1;
    } else {
        result = 2;
    }
    return result;
}
~~~

Các nhánh loại trừ nhau nên mỗi lần thực thi chỉ có một phép gán. Ngược lại, đoạn dưới không biên dịch:

~~~java
// final int x = 1;
// x = 2; // Lỗi: không thể gán lại final.
~~~

Kiểm tra *definite assignment* bảo đảm local đã được gán trước khi đọc; kiểm tra *definite unassignment* đối với `final` giúp tránh gán lại sai quy tắc. Hai vấn đề liên quan nhưng không giống nhau.

## 9. Chương trình Java 17+ chạy độc lập

Tạo đúng file **`DefiniteAssignmentDemo.java`**:

~~~java
public final class DefiniteAssignmentDemo {
    private static int field; // Giá trị mặc định là 0.

    private DefiniteAssignmentDemo() {}

    static int ifElse(boolean flag) {
        int result;
        if (flag) result = 10;
        else result = 20;
        return result;
    }

    static int earlyExit(int input) {
        int value;
        if (input < 0) return -1;
        if (input == 0) {
            throw new IllegalArgumentException("zero not allowed");
        }
        value = input * 2;
        return value;
    }

    static int switchValue(String mode) {
        int result = switch (mode) {
            case "FAST" -> 100;
            case "SLOW" -> 20;
            default -> 0;
        };
        return result;
    }

    static int loopValue() {
        int result;
        while (true) {
            result = 7;
            break;
        }
        return result;
    }

    static int tryCatch(boolean fail) {
        int result;
        try {
            if (fail) {
                throw new IllegalArgumentException("simulated");
            }
            result = 40;
        } catch (IllegalArgumentException ex) {
            result = -40;
        }
        return result;
    }

    static int finalOnce(boolean flag) {
        final int result;
        if (flag) result = 1;
        else result = 2;
        return result;
    }

    public static void main(String[] args) {
        System.out.println("field=" + field);
        System.out.println("if true=" + ifElse(true));
        System.out.println("if false=" + ifElse(false));

        System.out.println("early positive=" + earlyExit(3));
        System.out.println("early negative=" + earlyExit(-3));
        try {
            earlyExit(0);
        } catch (IllegalArgumentException ex) {
            System.out.println("early zero=" + ex.getMessage());
        }

        System.out.println("switch FAST=" + switchValue("FAST"));
        System.out.println("switch SLOW=" + switchValue("SLOW"));
        System.out.println("switch OTHER=" + switchValue("OTHER"));
        System.out.println("loop=" + loopValue());
        System.out.println("try normal=" + tryCatch(false));
        System.out.println("try catch=" + tryCatch(true));
        System.out.println("final true=" + finalOnce(true));
        System.out.println("final false=" + finalOnce(false));
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên; không cần thư viện ngoài hay preview feature.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 DefiniteAssignmentDemo.java
java -cp . DefiniteAssignmentDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\DefiniteAssignmentDemo.java
java -cp . DefiniteAssignmentDemo
~~~

### Exact expected stdout

~~~text
field=0
if true=10
if false=20
early positive=6
early negative=-1
early zero=zero not allowed
switch FAST=100
switch SLOW=20
switch OTHER=0
loop=7
try normal=40
try catch=-40
final true=1
final false=2
~~~

Kết quả cho thấy biến local có thể khai báo rồi gán ở các nhánh, nhánh `return`/`throw` không cần gán cho code phía sau mà nó không thể tới, và field mặc định bằng 0 không đồng nghĩa local cũng bằng 0.

## 10. Lỗi thường gặp và bài tập

| Sai lầm | Vì sao lỗi? | Cách sửa |
|---|---|---|
| `int x; println(x);` | Local chưa được gán | Gán trước phép đọc |
| Chỉ gán `x` khi `if(flag)` | Nhánh false chưa gán | Thêm `else` hoặc mặc định |
| `if/else if` không bao phủ mọi nhánh | Có đường bỏ qua mọi phép gán | Thêm `else` hoặc đổi cấu trúc |
| `switch statement` thiếu nhánh bao phủ | Có selector không khớp `case` | Thêm `default` khi phù hợp |
| Chỉ gán trong `while` | Vòng có thể chạy 0 lượt | Gán mặc định hoặc chứng minh đường thoát |
| Chỉ gán trong `catch` | `catch` có thể không chạy | Xử lý cả đường `try` bình thường |
| Nghĩ local tự nhận 0/null như field | Local không có giá trị mặc định | Gán rõ ràng |
| Gán hai lần vào biến `final` | Vi phạm quy tắc final | Đảm bảo một lần trên từng đường |

**Bài A:** `int n; if (flag) n=1; else n=2; System.out.println(n);` hợp lệ không? **Có**, vì hai nhánh đã bao phủ mọi đường.

**Bài B:** `int n; if (flag) n=1; System.out.println(n);` hợp lệ không? **Không**, vì flag có thể false.

**Bài C:** `int n; if (flag) return; n=3; System.out.println(n);` trong phương thức `void` có hợp lệ không? **Có**, vì mọi đường tới `println` đều đã gán `n=3`.

**Bài D:** Có thể đọc `private int count;` của object trước khi gán tường minh không? **Có**, giá trị mặc định của field `int` là 0.

**Bài E:** Nếu `final int result;` được gán trong cả hai nhánh loại trừ nhau, có được không? **Có**, miễn biến chắc chắn được gán trước khi đọc và không bị gán lại trên cùng đường thực thi.

## 11. Checklist và kết luận

- [ ] Phân biệt **phạm vi tên biến** với **đã gán chắc chắn**.
- [ ] Biết local không có giá trị mặc định, khác field và phần tử mảng.
- [ ] Kiểm tra tất cả đường `if/else` có thể đi tới phép đọc.
- [ ] Biết `return` hoặc `throw` có thể chấm dứt một nhánh chưa gán.
- [ ] Hiểu `switch statement` và `switch expression` khác nhau.
- [ ] Không mặc định thân vòng lặp hoặc `catch` luôn được thực thi.
- [ ] Với `final`, kiểm soát cả gán trước khi đọc và không gán lại.
- [ ] Chạy `DefiniteAssignmentDemo.java` với JDK 17+, đối chiếu exact stdout.

**Kết luận:** Java 17 yêu cầu một biến cục bộ phải được **gán chắc chắn trên mọi đường thực thi có thể dẫn đến phép đọc**. Để code rõ và an toàn, hãy bao phủ các nhánh, dùng guard clause khi hợp lý, và phân biệt *definite assignment* với phạm vi biến cũng như giá trị mặc định của field.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scope-and-initialization-007">final có thể được gán trong các nhánh loại trừ nhau</a>

<details>
<summary>Xem chi tiết</summary>

# `final` có thể được gán trong các nhánh loại trừ nhau — Java 17+

> **Java core — language-basics / Phạm vi và khởi tạo**  
> **Yêu cầu:** JDK 17+; ví dụ một file, chạy bằng `javac` và `java`.  
> **Mục tiêu:** Hiểu vì sao một biến `final` chưa khởi tạo có thể được gán **một lần trên mỗi đường thực thi**, chẳng hạn trong hai nhánh `if/else`; biết phân biệt *definite assignment* với *definite unassignment*, tránh thiếu gán/gán hai lần và áp dụng cho trường `final` trong constructor.

## 1. `final` giới hạn việc gán lại, không bắt buộc gán ngay

Với biến cục bộ (*local variable*), có hai cách khai báo `final`:

~~~java
final int fixed = 10; // Có giá trị ngay khi khai báo.

// Trong một phương thức:
final int selected;   // Blank final: chưa có initializer.
if (true) {
    selected = 20;    // Lần gán đầu tiên.
}
System.out.println(selected); // 20.
~~~

*Blank final* là biến `final` được khai báo **chưa có initializer**. Nó có thể nhận giá trị sau, miễn việc gán tuân theo quy tắc kiểm tra luồng điều khiển của Java.

- `final` không có nghĩa “mỗi nhánh được phép gán một lần rồi cộng lại”; tại **mỗi lần thực thi**, một biến `final` chỉ được nhận giá trị trong những trường hợp mà ngôn ngữ chứng minh được nó **chưa được gán** trước phép gán ấy.
- Để **đọc** một biến cục bộ, trình biên dịch phải xác định nó **chắc chắn đã được gán** (*definitely assigned*, DA) trên mọi đường đi tới vị trí đọc.
- Để **gán** cho một biến `final` chưa có initializer, trình biên dịch phải xác định nó **chắc chắn chưa được gán** (*definitely unassigned*, DU) ngay trước phép gán.

Các kiểm tra DA và DU được thực hiện khi **biên dịch**, dựa trên cấu trúc chương trình, không đợi đến lúc chạy rồi mới xem nhánh nào xảy ra.

## 2. Hai nhánh `if/else` loại trừ nhau: cách viết chuẩn

~~~java
static int select(boolean flag) {
    final int result;

    if (flag) {
        result = 11;
    } else {
        result = 22;
    }

    return result;
}
~~~

Hai nhánh của một câu lệnh `if/else` **không chạy đồng thời trong cùng một lượt gọi**. Khi tới `return result`, bất kể `flag` là `true` hay `false`, **đúng một nhánh** đã gán `result`. Vì vậy:

~~~java
System.out.println(select(true));  // 11
System.out.println(select(false)); // 22
~~~

**Phân tích đường đi:**

| Giá trị `flag` | Nhánh thực thi | Giá trị gán cho `result` | Đã gán trước `return`? |
| --- | --- | --- | --- |
| `true` | `if` | `11` | Có |
| `false` | `else` | `22` | Có |

Điểm quan trọng không phải biến `result` xuất hiện trong **hai câu lệnh gán ở mã nguồn**, mà là **không có đường thực thi hợp lệ nào đi qua cả hai phép gán đó**. Java xác minh sự loại trừ qua cấu trúc `if/else`.

Cách viết tương đương, ngắn hơn khi mỗi nhánh chỉ chọn một giá trị:

~~~java
final int result = flag ? 11 : 22;
~~~

Toán tử điều kiện `?:` trả một giá trị được lựa chọn để khởi tạo `result` đúng một lần; hãy dùng khi biểu thức đủ đơn giản để đọc.

## 3. Thiếu một nhánh gán: lỗi *might not have been initialized*

Đoạn sau **không biên dịch** nếu bỏ comment:

~~~java
// KHÔNG BIÊN DỊCH:
// static int missing(boolean flag) {
//     final int result;
//     if (flag) {
//         result = 11;
//     }
//     return result; // flag=false: result chưa hề được gán.
// }
~~~

Một nhánh có thể không gán gì, nhưng **không được đọc biến khi có đường đi tới chỗ đọc mà chưa gán**. Nếu cần một giá trị cho cả hai trường hợp, dùng `else`:

~~~java
static int withElse(boolean flag) {
    final int result;
    if (flag) {
        result = 11;
    } else {
        result = 0;
    }
    return result;
}
~~~

Hoặc sắp xếp luồng điều khiển để đường không gán **kết thúc trước khi đọc**:

~~~java
static int withEarlyReturn(boolean enabled) {
    final int code;
    if (!enabled) {
        return -1; // Đường này không bao giờ tới return code.
    }
    code = 42;
    return code;
}
~~~

Với `enabled == false`, phương thức trả `-1` ngay, nên không đọc `code`. Với `enabled == true`, `code` được gán `42` trước khi trả. Trình biên dịch có thể chứng minh `code` đã được gán trên mọi đường thực sự tới `return code`.

Tương tự, một nhánh có thể `throw` thay vì gán nếu nó **không tiếp tục** tới chỗ dùng biến. Việc có nhánh `return` hoặc `throw` không tự tạo thêm quyền gán nhiều lần cho `final`.

## 4. Gán lần thứ hai: vẫn là lỗi dù gán cùng giá trị

Một biến `final` đã có giá trị không thể gán lại:

~~~java
// KHÔNG BIÊN DỊCH:
// final int limit = 10;
// limit = 10; // Lỗi: gán lại dù giá trị không thay đổi.
~~~

Điều tương tự xảy ra khi đã gán qua một trong hai nhánh rồi tiếp tục gán sau đó:

~~~java
// KHÔNG BIÊN DỊCH:
// static int assignedTwice(boolean flag) {
//     final int result;
//     if (flag) {
//         result = 1;
//     } else {
//         result = 2;
//     }
//     result = 3; // Lỗi: result chắc chắn đã được gán.
//     return result;
// }
~~~

Không nên viết hai câu `if` độc lập để thay thế một `if/else` trong bài toán lựa chọn một giá trị. Trình biên dịch kiểm tra DA/DU theo quy tắc luồng điều khiển cụ thể; nó **không phải bộ chứng minh tùy ý mọi quan hệ logic** giữa các điều kiện. Cấu trúc `if/else` truyền đạt ý định “chính xác một nhánh” rõ ràng và dễ kiểm chứng hơn.

Với biến `final`, phép `++`, `--` và phép gán kết hợp như `+=` cũng là những thao tác gán lại và không thể dùng sau khi biến đã khởi tạo. `final` bảo vệ **giá trị được giữ trong biến**, không tự đảm bảo kết quả tính toán hay đối tượng là bất biến.

## 5. Nhiều nhánh `else if` và `switch`

Khi có ba hoặc nhiều lựa chọn, cần bao phủ mọi đường đi tới chỗ đọc:

~~~java
static String classify(int number) {
    final String category;

    if (number < 0) {
        category = "NEG";
    } else if (number == 0) {
        category = "ZERO";
    } else {
        category = "POS";
    }

    return category;
}
~~~

Các nhánh trong cùng chuỗi `if/else if/else` loại trừ nhau. Nhánh `else` bảo đảm rằng khi không thỏa hai điều kiện trước, biến vẫn được khởi tạo.

Trong Java 17, `switch` dùng **arrow cases** (`->`) giúp tránh lỗi chạy xuyên xuống case tiếp theo (*fall-through*):

~~~java
static String fromSwitch(int code) {
    final String label;

    switch (code) {
        case 0 -> label = "ZERO";
        case 1 -> label = "ONE";
        default -> label = "OTHER";
    }

    return label;
}
~~~

Ở đây mỗi nhánh `switch` chỉ thực thi một phép gán và `default` bao phủ các giá trị còn lại. Nếu bỏ `default` khỏi ví dụ này, còn đường đi không gán `label` khi `code` khác 0 và 1.

Một cách ngắn gọn khác là **switch expression** trả giá trị khởi tạo trực tiếp:

~~~java
static String fromExpression(int code) {
    final String label = switch (code) {
        case 0 -> "ZERO";
        case 1 -> "ONE";
        default -> "OTHER";
    };
    return label;
}
~~~

Trong `switch expression`, tất cả nhánh của biểu thức phải đáp ứng quy tắc tạo kết quả/hoàn tất của biểu thức. Cả `fromSwitch` và `fromExpression` cho `code=0` kết quả `"ZERO"`, `code=1` kết quả `"ONE"`, và các giá trị khác kết quả `"OTHER"`.

## 6. Trường `final` chưa có initializer trong constructor

Quy tắc tương tự còn rất quan trọng với **blank final instance field**:

~~~java
class Plan {
    private final String tier;
    private final int quota;

    Plan(boolean premium) {
        if (premium) {
            tier = "PREMIUM";
            quota = 100;
        } else {
            tier = "BASIC";
            quota = 10;
        }
    }

    String summary() {
        return tier + ":" + quota;
    }
}
~~~

Mỗi đối tượng `Plan` có các trường `tier` và `quota` riêng; constructor gán đúng một lần cho mỗi trường trên mọi đường kết thúc bình thường. Có thể tạo hai đối tượng có giá trị cuối khác nhau:

~~~java
Plan first = new Plan(true);
Plan second = new Plan(false);
// first.summary()  -> "PREMIUM:100"
// second.summary() -> "BASIC:10"
~~~

Các điểm cần phân biệt:

- **Biến cục bộ blank final:** muốn **đọc** phải thỏa DA; nếu không đọc, vẫn cần tuân thủ quy tắc DU khi gán.
- **Blank final instance field:** phải được khởi tạo theo quy tắc Java trước khi constructor hoàn tất bình thường; nếu có nhiều constructor, các đường khởi tạo tương ứng đều phải hợp lệ.
- **Blank static final field:** được khởi tạo trong phạm vi khởi tạo static hợp lệ của lớp, không phải riêng mỗi lần tạo đối tượng.
- Không thể tạo thêm quyền gán field `final` sau khi đã khởi tạo bằng cách dùng một setter thông thường.

`final` trên trường không đồng nghĩa mọi đối tượng có trường đó là “hằng số tại thời điểm biên dịch”. Chỉ những trường hợp phù hợp quy tắc *constant variable* (ví dụ một số trường primitive/`String` có constant expression) mới có tính chất đó.

## 7. `final` tham chiếu: cấm đổi tham chiếu, không cấm sửa mảng

Trường hợp nhánh chọn một trong hai **mảng**:

~~~java
static int[] chooseArray(boolean shortArray) {
    final int[] values;

    if (shortArray) {
        values = new int[] {1, 2};
    } else {
        values = new int[] {3, 4, 5};
    }

    values[0] = 9; // HỢP LỆ: sửa nội dung mảng.
    return values;
}
~~~

Sau một trong hai nhánh, biến `values` chỉ giữ một tham chiếu được gán đúng một lần. `values[0] = 9` **không gán lại biến tham chiếu**; nó sửa phần tử của đối tượng mảng:

~~~java
// KHÔNG BIÊN DỊCH nếu đặt sau phần if/else:
// values = new int[] {8}; // Gán lại biến final đã khởi tạo.
~~~

Tương tự, một `final StringBuilder` vẫn có thể gọi `append(...)` để đổi trạng thái của đối tượng; còn phép `builder = new StringBuilder()` bị cấm sau lần gán ban đầu. Vì vậy **`final` không đồng nghĩa đối tượng bất biến (*immutable*)**.

Với mỗi lần gọi phương thức, biến cục bộ mới được tạo theo ngữ cảnh lời gọi. Nếu khai báo `final` ở bên trong thân vòng lặp và khởi tạo đúng cách, từng lượt lặp cũng có biến cục bộ theo phạm vi riêng; không được suy diễn rằng một biến `final` duy nhất nằm **ngoài vòng lặp** có thể liên tục nhận giá trị mới.

## 8. Ví dụ Java 17+ chạy độc lập

Tạo file **FinalExclusiveBranchesDemo.java**:

~~~java
import java.util.Arrays;

public final class FinalExclusiveBranchesDemo {
    private final String tier;
    private final int quota;

    public FinalExclusiveBranchesDemo(boolean premium) {
        if (premium) {
            tier = "PREMIUM";
            quota = 100;
        } else {
            tier = "BASIC";
            quota = 10;
        }
    }

    public String summary() {
        return tier + ":" + quota;
    }

    private static int select(boolean flag) {
        final int result;
        if (flag) {
            result = 11;
        } else {
            result = 22;
        }
        return result;
    }

    private static int withEarlyReturn(boolean enabled) {
        final int code;
        if (!enabled) {
            return -1;
        }
        code = 42;
        return code;
    }

    private static String classify(int number) {
        final String category;
        if (number < 0) {
            category = "NEG";
        } else if (number == 0) {
            category = "ZERO";
        } else {
            category = "POS";
        }
        return category;
    }

    private static String fromSwitch(int code) {
        final String label;
        switch (code) {
            case 0 -> label = "ZERO";
            case 1 -> label = "ONE";
            default -> label = "OTHER";
        }
        return label;
    }

    private static String fromExpression(int code) {
        final String label = switch (code) {
            case 0 -> "ZERO";
            case 1 -> "ONE";
            default -> "OTHER";
        };
        return label;
    }

    private static int throwOrAssign(boolean valid) {
        final int value;
        if (!valid) {
            throw new IllegalArgumentException("invalid");
        }
        value = 7;
        return value;
    }

    private static int[] chooseArray(boolean shortArray) {
        final int[] values;
        if (shortArray) {
            values = new int[] {1, 2};
        } else {
            values = new int[] {3, 4, 5};
        }
        values[0] = 9;
        return values;
    }

    private static int loopSum() {
        int sum = 0;
        for (int i = 0; i < 3; i++) {
            final int increment;
            if (i % 2 == 0) {
                increment = i * 10;
            } else {
                increment = i;
            }
            sum += increment;
        }
        return sum;
    }

    public static void main(String[] args) {
        System.out.println("trueBranch=" + select(true));
        System.out.println("falseBranch=" + select(false));
        System.out.println("guardOff=" + withEarlyReturn(false));
        System.out.println("guardOn=" + withEarlyReturn(true));

        System.out.println("negative=" + classify(-2));
        System.out.println("zero=" + classify(0));
        System.out.println("positive=" + classify(5));
        System.out.println("switchZero=" + fromSwitch(0));
        System.out.println("switchOne=" + fromSwitch(1));
        System.out.println("switchOther=" + fromSwitch(8));
        System.out.println("expression=" + fromExpression(1));

        FinalExclusiveBranchesDemo premium =
                new FinalExclusiveBranchesDemo(true);
        FinalExclusiveBranchesDemo basic =
                new FinalExclusiveBranchesDemo(false);
        System.out.println("premium=" + premium.summary());
        System.out.println("basic=" + basic.summary());

        System.out.println("shortArray=" + Arrays.toString(chooseArray(true)));
        System.out.println("longArray=" + Arrays.toString(chooseArray(false)));
        System.out.println("loopSum=" + loopSum());

        System.out.println("throwValid=" + throwOrAssign(true));
        try {
            System.out.println("throwInvalid=" + throwOrAssign(false));
        } catch (IllegalArgumentException ex) {
            System.out.println("throwInvalid=IllegalArgumentException");
        }
    }
}
~~~

Biên dịch và chạy tại thư mục chứa file trong Bash, PowerShell hoặc Command Prompt:

~~~bash
javac -encoding UTF-8 FinalExclusiveBranchesDemo.java
java FinalExclusiveBranchesDemo
~~~

**Expected stdout chính xác:**

~~~text
trueBranch=11
falseBranch=22
guardOff=-1
guardOn=42
negative=NEG
zero=ZERO
positive=POS
switchZero=ZERO
switchOne=ONE
switchOther=OTHER
expression=ONE
premium=PREMIUM:100
basic=BASIC:10
shortArray=[9, 2]
longArray=[9, 4, 5]
loopSum=21
throwValid=7
throwInvalid=IllegalArgumentException
~~~

`trueBranch` và `falseBranch` chứng minh hai nhánh có thể gán cho cùng một blank final; `guardOff` chứng minh một nhánh kết thúc sớm không cần gán giá trị để đi tiếp; `switchZero/One/Other` chứng minh các case loại trừ nhau. Hai trường `final` của constructor được gán trong mỗi nhánh; `shortArray` và `longArray` cho thấy `final` không cấm sửa phần tử mảng.

## 9. Lỗi thường gặp và bài tập

| Hiểu nhầm / vấn đề | Cách kiểm tra hoặc sửa |
| --- | --- |
| Biến `final` phải được gán ngay tại chỗ khai báo | Không bắt buộc với *blank final*; có thể gán hợp lệ ở các nhánh |
| Hai phép gán xuất hiện trong `if/else` là gán hai lần | Sai nếu mỗi đường thực thi chỉ chạy một nhánh |
| Chỉ `if(flag) value=1;` rồi dùng `value` | Thiếu gán khi `flag=false`, không đạt DA |
| `if/else` đã gán rồi lại `value=3` | Gán lần hai, vi phạm quy tắc của `final` |
| Bỏ `default` trong `switch` rồi vẫn đọc biến | Có thể còn đường không gán |
| `final int[] values` không cho sửa `values[0]` | Sai; cấm gán lại tham chiếu, không cấm sửa mảng |
| Trường blank `final` có thể để không gán khi constructor kết thúc | Không; phải khởi tạo hợp lệ trên mọi đường hoàn tất bình thường |
| Nhiều câu `if` độc lập luôn được phân tích như một `if/else` | Không nên giả định; dùng cấu trúc nhánh rõ ràng |
| `final` làm một đối tượng bất biến hoàn toàn | Không; tính bất biến của đối tượng còn phụ thuộc thiết kế kiểu |

**Bài 1.** Viết `static int pick(boolean flag)` có `final int n;`, gán `5` trong nhánh `if` và `8` trong nhánh `else`. Dự đoán kết quả với hai giá trị `flag`.

**Bài 2.** Bỏ nhánh `else` của bài 1 nhưng giữ `return n;`. Giải thích lỗi DA; sửa bằng cách trả sớm hoặc bao phủ đủ các nhánh.

**Bài 3.** Sau `if/else` hợp lệ, thêm `n = 100;`. Giải thích vì sao dù gán cùng giá trị cũ hay khác giá trị, lệnh vẫn bị từ chối.

**Bài 4.** Khai báo lớp có trường `private final String category;` và constructor nhận số. Trong `if/else if/else`, gán các nhãn phù hợp; kiểm tra mọi đường constructor kết thúc.

**Bài 5.** Thay `if/else` bằng `switch` có `default` hoặc `switch expression` trên Java 17. So sánh khả năng bao phủ nhánh và độ rõ ràng.

**Bài 6.** Dùng `final int[] selected;`, chọn một trong hai mảng theo `boolean`, sau đó sửa `selected[0]`. Vì sao sửa phần tử hợp lệ nhưng `selected = new int[2]` thì không?

### Ghi nhớ

**Một blank `final` có thể xuất hiện trong nhiều phép gán ở các nhánh loại trừ nhau**, miễn trình biên dịch chứng minh nó **chưa được gán trước từng phép gán** và **đã được gán trước mọi lần đọc**. `if/else` và `switch` bao phủ đầy đủ trường hợp giúp diễn đạt quy tắc này rõ nhất; `return`/`throw` có thể kết thúc sớm một đường đi. `final` cấm đổi giá trị biến sau khi khởi tạo, nhưng không tự làm đối tượng tham chiếu trở nên bất biến.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="scope-and-initialization-008">Giữ phạm vi biến nhỏ và khai báo gần nơi sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ phạm vi biến nhỏ và khai báo gần nơi sử dụng trong Java

> **Java core — language-basics / Phạm vi và khởi tạo**  
> **Môi trường:** Java 17+, ví dụ trong một file, chỉ cần JDK.  
> **Mục tiêu:** Khai báo biến tại nơi bắt đầu cần dùng, giới hạn phạm vi của chúng trong khối/nhánh/vòng lặp phù hợp, tránh sử dụng nhầm trạng thái cũ và phân biệt phạm vi (*scope*) với quy tắc phải gán giá trị trước khi đọc (*definite assignment*).

## 1. Nguyên tắc: phạm vi nhỏ nhất nhưng đủ dùng

*Scope* là vùng mã nguồn mà tên biến có hiệu lực. Với biến cục bộ khai báo trong khối `{ ... }`, tên thường có hiệu lực từ vị trí khai báo đến cuối khối. Vì vậy, **khai báo gần lần dùng đầu tiên** giúp người đọc thấy rõ ý nghĩa và tránh kéo dài phạm vi của biến không cần thiết.

~~~java
static void example(int score) {
    System.out.println("received=" + score);

    if (score >= 60) {
        String message = "PASS"; // Chỉ cần trong nhánh này.
        System.out.println(message);
    }

    // System.out.println(message); // Không biên dịch: hết scope.
}
~~~

**Quy tắc thực hành:** Nếu một giá trị chỉ được dùng trong một nhánh, đặt biến trong nhánh. Nếu chỉ dùng trong một lần lặp, đặt nó trong thân lặp. Nếu kết quả phải được dùng sau vòng lặp, khai báo **biến tích lũy/kết quả** ngoài vòng lặp.

Không nên biến quy tắc này thành “nhồi mọi thứ vào một dòng”. Cần đủ chỗ cho tên có nghĩa, các bước kiểm tra rõ ràng, và không ép một biểu thức phức tạp trở nên khó đọc.

## 2. Trước và sau: tránh khai báo quá sớm

**Trước — biến sống trong phạm vi rộng hơn nhu cầu:**

~~~java
static String oldStatus(boolean enabled, int score) {
    String result = "";
    String label = "";
    if (enabled) {
        label = score >= 60 ? "PASS" : "RETRY";
        result = label + ":" + score;
    } else {
        result = "SKIPPED";
    }
    return result;
}
~~~

**Sau — đưa biến vào nhánh cần dùng, trả kết quả sớm:**

~~~java
static String status(boolean enabled, int score) {
    if (!enabled) {
        return "SKIPPED";
    }

    String label = score >= 60 ? "PASS" : "RETRY";
    return label + ":" + score;
}
~~~

Hai phiên bản trả cùng kết quả ở các đầu vào ví dụ, nhưng bản sau không phải duy trì `result` và `label` từ trước khi biết có dùng chúng hay không.

- `label` chỉ tồn tại trong phần còn lại của phương thức sau nhánh `return` sớm; nó không xuất hiện trong nhánh không kích hoạt.
- Nếu giá trị trung gian được dùng ở nhiều nhánh, nên chọn khối chung phù hợp thay vì sao chép tính toán vô ích.
- Một phương thức ngắn, tên biến có nghĩa và kiểm tra đầu vào rõ ràng quan trọng hơn việc giảm số ký tự.

## 3. Khối `if`/`else`: đặt biến nơi thật sự cần

~~~java
static String classify(int number) {
    String result;
    if (number < 0) {
        result = "NEG";
    } else if (number == 0) {
        result = "ZERO";
    } else {
        result = "POS";
    }
    return result;
}
~~~

`result` phải ở ngoài các nhánh vì dùng sau chuỗi `if`/`else`. Đây là **phạm vi rộng có lý do**, không phải lỗi thiết kế. Mỗi nhánh gán giá trị nên Java có thể chứng minh `result` đã được gán trước `return`.

Nếu mỗi nhánh có thể trả về ngay, ta có thể tránh biến `result`:

~~~java
static String classifyDirect(int number) {
    if (number < 0) return "NEG";
    if (number == 0) return "ZERO";
    return "POS";
}
~~~

Trường hợp không biên dịch vì thiếu gán:

~~~java
// KHÔNG BIÊN DỊCH:
// static int broken(boolean flag) {
//     int value;
//     if (flag) value = 5;
//     return value; // flag=false nên value chưa chắc đã gán.
// }
~~~

**Phân biệt:** Biến `value` đã nằm trong *scope* tại `return`, nhưng **chưa đạt definite assignment**. Thêm `else` hợp lý, giá trị mặc định có chủ đích hoặc thay đổi cấu trúc trả về để sửa.

## 4. Vòng `for`: giới hạn biến điều khiển và biến tạm

Khi không cần sử dụng chỉ số sau vòng lặp, khai báo nó ngay trong phần khởi tạo `for`:

~~~java
int sum = 0; // Kết quả cần sau vòng, nên đặt ngoài.
for (int i = 1; i <= 4; i++) {
    int square = i * i; // Chỉ phục vụ lần lặp hiện tại.
    sum += square;
}
System.out.println(sum); // 30.
// System.out.println(i);      // Lỗi: i ngoài scope.
// System.out.println(square); // Lỗi: square ngoài scope.
~~~

Mỗi lần lặp chạy thân, phép khởi tạo `square = i * i` được thực hiện với chỉ số hiện tại. Không nên đưa `square` ra ngoài `for` nếu không có nhu cầu sử dụng ngoài vòng.

Enhanced `for` cũng có biến phần tử chỉ có hiệu lực trong thân vòng:

~~~java
static int sumPositiveTwice(int[] values) {
    int total = 0; // Cần qua nhiều lần lặp.
    for (int number : values) {
        if (number <= 0) {
            continue;
        }
        int contribution = number * 2; // Dùng cho lần lặp này.
        total += contribution;
    }
    return total;
}
~~~

Với `{2, -1, 3, 0, 5}`, tổng là `(2 + 3 + 5) * 2 = 20`. Biến `contribution` chỉ được khởi tạo khi số dương; tránh tạo một biến trống rồi gán lại ở mọi nhánh.

**Ngoại lệ hợp lý:** Nếu cần dùng chỉ số sau vòng để báo vị trí kết thúc hoặc tái sử dụng một đối tượng có chi phí tạo lớn, có thể khai báo bên ngoài; nhưng cần nói rõ lý do và không vô tình giữ trạng thái cũ giữa các lần lặp.

## 5. `while`, duyệt mảng và kiểm tra đầu vào

Với vòng `while`, biến điều khiển có thể cần dùng cả sau vòng:

~~~java
int steps = 0;
while (steps < 3) {
    int nextStep = steps + 1;
    System.out.println("step=" + nextStep);
    steps++;
}
System.out.println("done=" + steps);
~~~

`steps` ở ngoài vì dùng trong điều kiện và sau vòng. `nextStep` chỉ phục vụ phần thân, nên khai báo trong thân.

Khi duyệt mảng chuỗi, đặt biến chuyển đổi **sau kiểm tra null**, ngay trước khi sử dụng:

~~~java
static String firstNonBlank(String[] values) {
    for (String value : values) {
        if (value == null) {
            continue;
        }
        String normalized = value.trim();
        if (!normalized.isEmpty()) {
            return normalized;
        }
    }
    return "NONE";
}
~~~

`normalized` không bị tạo khi `value == null`; nó chỉ tồn tại trong thân vòng lặp sau điểm khai báo. `firstNonBlank` ở trên yêu cầu đối số mảng khác null; nếu hợp đồng cho phép mảng null, hãy kiểm tra thêm ở đầu phương thức.

Để lấy thông tin sau vòng, trả trực tiếp khi tìm được là hợp lý; không cần giữ biến `found` từ đầu nếu chỉ cần kết quả đầu tiên.

## 6. Scope nhỏ không tự làm khai báo trùng tên hợp lệ

Hai khối ngang hàng có thể dùng lại local `attempt`; khai báo `attempt` lần nữa trong khối lồng khi local bên ngoài còn trong scope vẫn là lỗi:

~~~java
{
    int attempt = 1;
    System.out.println(attempt);
}
{
    int attempt = 2; // Hợp lệ: phạm vi trước đã hết.
    System.out.println(attempt);
}
~~~

Khi local trùng tên field, Java cho phép *shadowing*, nhưng phải phân biệt tên ngắn với `this.field` hoặc `ClassName.staticField`. Trong mã dễ bảo trì, ưu tiên tên thể hiện đúng mục đích và phạm vi nhỏ; các quy tắc khai báo trùng và shadowing vẫn phải được tuân thủ.

## 7. `final`, `var` và ranh giới của lời khuyên

Trong Java 17, `final` và `var` có thể giúp ý định của biến rõ hơn nhưng **không thay đổi quy tắc phạm vi**:

~~~java
static String upper(String raw) {
    if (raw == null) return "MISSING";

    final String cleaned = raw.trim();
    if (cleaned.isEmpty()) return "EMPTY";

    var result = cleaned.toUpperCase(java.util.Locale.ROOT);
    return result;
}
~~~

- `final` cấm gán lại `cleaned` sau khi đã gán, không tự làm đối tượng được tham chiếu trở nên bất biến và không nới rộng scope.
- `var` suy luận kiểu biến cục bộ từ biểu thức khởi tạo; `result` vẫn có phạm vi như local thông thường. Không thể dùng `var x;` mà không có initializer.
- Nếu kiểu không rõ khi đọc biểu thức khởi tạo, khai báo tường minh như `String result = ...` thường dễ hiểu hơn.
- Không đưa mọi kết quả trung gian thành field hay biến static chỉ để “dùng được ở khắp nơi”: đó là thay đổi trạng thái/chế độ chia sẻ dữ liệu, không phải đơn giản hóa phạm vi.

*Scope* chỉ nói **vị trí tên có thể được tham chiếu**. Đối tượng được biến local trỏ tới vẫn có thể sống tiếp nếu có tham chiếu khác sau khi local hết scope; dấu `}` không quyết định thời điểm garbage collection.

## 8. Chương trình Java 17+ chạy độc lập

Tạo file **SmallScopeDemo.java**:

~~~java
import java.util.Locale;

public final class SmallScopeDemo {
    private static String status(boolean enabled, int score) {
        if (!enabled) {
            return "SKIPPED";
        }
        String label = score >= 60 ? "PASS" : "RETRY";
        return label + ":" + score;
    }

    private static int sumPositiveTwice(int[] values) {
        int total = 0;
        for (int number : values) {
            if (number <= 0) {
                continue;
            }
            int contribution = number * 2;
            total += contribution;
        }
        return total;
    }

    private static int squareSum(int limit) {
        int sum = 0;
        for (int i = 1; i <= limit; i++) {
            int square = i * i;
            sum += square;
        }
        return sum;
    }

    private static String firstNonBlank(String[] values) {
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String normalized = value.trim();
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }
        return "NONE";
    }

    private static String classify(int number) {
        String result;
        if (number < 0) {
            result = "NEG";
        } else if (number == 0) {
            result = "ZERO";
        } else {
            result = "POS";
        }
        return result;
    }

    private static String upper(String raw) {
        if (raw == null) {
            return "MISSING";
        }
        final String cleaned = raw.trim();
        if (cleaned.isEmpty()) {
            return "EMPTY";
        }
        var result = cleaned.toUpperCase(Locale.ROOT);
        return result;
    }

    public static void main(String[] args) {
        System.out.println("statusOff=" + status(false, 78));
        System.out.println("statusPass=" + status(true, 78));
        System.out.println("statusRetry=" + status(true, 42));

        System.out.println("positiveTwice="
                + sumPositiveTwice(new int[] {2, -1, 3, 0, 5}));
        System.out.println("positiveEmpty="
                + sumPositiveTwice(new int[0]));
        System.out.println("squareSum=" + squareSum(4));

        System.out.println("firstNonBlank=" + firstNonBlank(
                new String[] {" ", null, " Java ", "Other"}));
        System.out.println("firstMissing=" + firstNonBlank(
                new String[] {null, "   "}));

        System.out.println("negative=" + classify(-2));
        System.out.println("zero=" + classify(0));
        System.out.println("positive=" + classify(9));

        System.out.println("upper=" + upper(" java "));
        System.out.println("upperEmpty=" + upper("   "));
        System.out.println("upperMissing=" + upper(null));

        {
            int attempt = 1;
            System.out.println("firstBlock=" + attempt);
        }
        {
            int attempt = 2;
            System.out.println("secondBlock=" + attempt);
        }

        int steps = 0;
        while (steps < 3) {
            int nextStep = steps + 1;
            System.out.println("step=" + nextStep);
            steps++;
        }
        System.out.println("done=" + steps);
    }
}
~~~

Biên dịch/chạy tại thư mục chứa file (PowerShell, Command Prompt hoặc Bash):

~~~bash
javac -encoding UTF-8 SmallScopeDemo.java
java SmallScopeDemo
~~~

**Expected stdout chính xác:**

~~~text
statusOff=SKIPPED
statusPass=PASS:78
statusRetry=RETRY:42
positiveTwice=20
positiveEmpty=0
squareSum=30
firstNonBlank=Java
firstMissing=NONE
negative=NEG
zero=ZERO
positive=POS
upper=JAVA
upperEmpty=EMPTY
upperMissing=MISSING
firstBlock=1
secondBlock=2
step=1
step=2
step=3
done=3
~~~

Các phương thức `status`, `firstNonBlank` và `upper` chỉ khai báo biến khi thực sự cần. `sumPositiveTwice` và `squareSum` đặt biến tích lũy bên ngoài vòng nhưng biến tạm bên trong. `attempt` được phép khai báo hai lần ở hai khối không chồng scope. `steps` vẫn có thể đọc sau vòng vì đã khai báo bên ngoài.

## 9. Lỗi phổ biến, checklist và bài tập

| Dấu hiệu | Điều chỉnh phù hợp |
| --- | --- |
| Khai báo nhiều biến ở đầu phương thức, mãi sau mới dùng | Đưa khai báo về gần lần dùng đầu |
| Biến chỉ dùng trong một nhánh `if` nhưng nằm ngoài | Đưa vào nhánh nếu không cần kết quả sau nhánh |
| Biến tạm chỉ dùng cho một lượt lặp nhưng đặt ngoài `for` | Khai báo và khởi tạo bên trong thân vòng |
| Cần tổng/số đếm sau vòng nhưng khai báo bên trong | Đưa accumulator ra khối bao ngoài |
| Dùng biến `i` sau `for (int i = ...)` | Nếu thật sự cần, khai báo `i` trước vòng; nếu không, dùng kết quả riêng |
| Local còn scope bị khai báo lại trong khối con | Đổi tên hoặc bố trí lại các khối |
| Biến đã trong scope nhưng chưa chắc có giá trị | Sửa *definite assignment* ở mọi đường đi |
| Dùng field/static để tránh giới hạn scope của local | Cân nhắc tham số/giá trị trả về trước khi mở rộng trạng thái chia sẻ |

**Bài 1.** Viết hàm đếm số chẵn trong `int[]`, chỉ khai báo biến `even` ở nơi cần dùng và giữ biến `count` đủ rộng để trả về cuối vòng.

**Bài 2.** Viết hàm tìm chuỗi đầu tiên không trống trong `String[]`, tránh giữ biến `normalized` ngoài vòng lặp. Vì sao cần kiểm tra null trước `trim()`?

**Bài 3.** Với hai nhánh `if` gán cho một kết quả chung, thử đặt biến `result` trong nhánh và giải thích lỗi khi `return result` sau nhánh. Viết lại có definite assignment hợp lệ hoặc return sớm.

**Bài 4.** Biến `int i` cần in sau vòng `for`. Hãy so sánh `for (int i = ...)` với `int i = 0; for (; ...)` để chọn phạm vi phù hợp.

**Bài 5.** Viết hai khối anh em cùng dùng local `temp`; sau đó lồng khối thứ hai trong khối thứ nhất để quan sát lỗi khai báo trùng tên. Giải thích vì sao field có thể bị shadow bởi local nhưng local-local chồng lấn thì không.

### Ghi nhớ

**Khai báo local gần nơi sử dụng và giữ phạm vi hẹp nhất đủ nhu cầu.** Biến tạm thuộc nhánh hoặc lượt lặp nên ở trong khối đó; accumulator dùng sau vòng cần ở ngoài vòng. Scope nhỏ giúp giảm dùng nhầm tên và trạng thái, nhưng không thay thế quy tắc definite assignment, khả năng đọc hiểu hay thiết kế API rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)
