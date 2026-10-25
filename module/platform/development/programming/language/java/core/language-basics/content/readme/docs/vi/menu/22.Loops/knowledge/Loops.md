<a id="back-to-top"></a>

# Vòng lặp

## Menu
- [while kiểm tra điều kiện trước mỗi lần lặp](#loops-001)
- [do-while thực thi thân ít nhất một lần](#loops-002)
- [for với phần khởi tạo, điều kiện và cập nhật](#loops-003)
- [Vòng đời biến đếm trong for](#loops-004)
- [Vòng lặp vô hạn và điều kiện bảo đảm kết thúc](#loops-005)
- [Lặp lồng nhau và theo dõi từng biến đếm](#loops-006)
- [Lỗi lệch một đơn vị tại biên vòng lặp](#loops-007)
- [Mẫu tích lũy tổng, đếm và tìm giá trị lớn nhất](#loops-008)
- [Duy trì điều kiện bất biến đơn giản qua mỗi lần lặp](#loops-009)
- [Chọn while, do-while hoặc for theo bài toán](#loops-010)

## <a id="loops-001">while kiểm tra điều kiện trước mỗi lần lặp</a>

<details>
<summary>Xem chi tiết</summary>

# `while` kiểm tra điều kiện trước mỗi lần lặp — Java 17+

Vòng lặp `while` thực thi một khối lệnh **chừng nào điều kiện còn đúng**. Điểm quan trọng nhất: Java **kiểm tra điều kiện trước mỗi lần chạy thân**, bao gồm cả lần đầu. Vì thế, nếu điều kiện sai ngay từ đầu, thân vòng lặp chạy **0 lần**.

~~~java
int n = 1;
while (n <= 3) {
    System.out.println(n);
    n++;
}
// In: 1, 2, 3; sau vòng lặp n == 4.
~~~

## 1. Cú pháp và thứ tự thực thi

~~~java
while (booleanCondition) {
    // Các lệnh thực hiện khi điều kiện true.
}
~~~

Quy trình:

1. **Đánh giá** biểu thức điều kiện `booleanCondition`.
2. Nếu `false`: thoát vòng `while`, tiếp tục câu lệnh ngay sau nó.
3. Nếu `true`: chạy thân vòng lặp, trừ trường hợp có điều khiển luồng như `break`, `return` hoặc ngoại lệ.
4. Sau khi thân hoàn tất bình thường (hoặc gặp `continue`), **quay lại bước 1**; không giữ nguyên kết quả điều kiện của lần trước.

~~~mermaid
flowchart TD
    A["Bắt đầu"] --> B{"Điều kiện true?"}
    B -- "Không" --> D["Câu lệnh sau while"]
    B -- "Có" --> C["Chạy thân while"]
    C --> B
~~~

**Điều kiện phải có kiểu `boolean`** (hoặc `Boolean` được unbox). Java không chấp nhận `while (1)` hay `while (number)` như một số ngôn ngữ khác; dùng biểu thức như `number > 0`.

## 2. Ví dụ Java 17+ chạy được bằng CLI

Tạo tệp UTF-8 tên **`WhilePrecheckDemo.java`**:

~~~java
public class WhilePrecheckDemo {
    static boolean check(int value) {
        System.out.println("check=" + value);
        return value < 2;
    }

    static void precheckTrace() {
        int n = 0;
        while (check(n)) {
            System.out.println("body=" + n);
            n++;
        }
        System.out.println("after=" + n);
    }

    static void zeroIterations() {
        int stock = 0;
        int visits = 0;
        while (stock > 0) {
            visits++;
            stock--;
        }
        System.out.println("visits=" + visits);
    }

    static void skipEvenNumbers() {
        int n = 0;
        while (n < 5) {
            n++; // Tiến triển trước nhánh continue.
            if (n % 2 == 0) {
                continue;
            }
            System.out.println("odd=" + n);
        }
        System.out.println("last=" + n);
    }

    static void stopOnSentinel() {
        String[] inputs = {"2", "bad", "4", "stop", "99"};
        int index = 0;
        int sum = 0;
        while (index < inputs.length) {
            String token = inputs[index++];
            if (token.equals("stop")) {
                System.out.println("found=stop");
                break;
            }
            try {
                sum += Integer.parseInt(token);
            } catch (NumberFormatException ex) {
                System.out.println("invalid=" + token);
            }
        }
        System.out.println("sum=" + sum);
        System.out.println("consumed=" + index);
    }

    static void exitInfiniteLoop() {
        int attempts = 0;
        while (true) {
            attempts++;
            if (attempts == 3) {
                System.out.println("breakAt=" + attempts);
                break;
            }
        }
        System.out.println("afterBreak=" + attempts);
    }

    public static void main(String[] args) {
        System.out.println("== count ==");
        int count = 1;
        while (count <= 3) {
            System.out.println("count=" + count);
            count++;
        }
        System.out.println("countAfter=" + count);

        System.out.println("== zero iterations ==");
        zeroIterations();

        System.out.println("== check before every body ==");
        precheckTrace();

        System.out.println("== continue ==");
        skipEvenNumbers();

        System.out.println("== sentinel and break ==");
        stopOnSentinel();

        System.out.println("== while true with exit ==");
        exitInfiniteLoop();
        System.out.println("done");
    }
}
~~~

Yêu cầu **JDK 17 trở lên**. Mở PowerShell (Windows) hoặc Bash (Linux/macOS) tại thư mục chứa tệp rồi chạy:

~~~bash
java -version
javac -version
javac -encoding UTF-8 WhilePrecheckDemo.java
java WhilePrecheckDemo
~~~

**Expected stdout chính xác** của riêng lệnh `java WhilePrecheckDemo`:

~~~text
== count ==
count=1
count=2
count=3
countAfter=4
== zero iterations ==
visits=0
== check before every body ==
check=0
body=0
check=1
body=1
check=2
after=2
== continue ==
odd=1
odd=3
odd=5
last=5
== sentinel and break ==
invalid=bad
found=stop
sum=6
consumed=4
== while true with exit ==
breakAt=3
afterBreak=3
done
~~~

`java -version` và `javac -version` hiển thị thông tin tùy bản JDK, không thuộc stdout cố định.

## 3. Vì sao `while` có thể lặp 0 lần?

~~~java
int available = 0;
while (available > 0) {
    System.out.println("processing");
    available--;
}
System.out.println("finished");
~~~

Điều kiện `available > 0` là false ngay tại lần kiểm tra đầu tiên. Vì vậy **không in `processing`**, chỉ in `finished`. Không có “một lượt đầu tiên bắt buộc” trong `while`.

Nếu yêu cầu nghiệp vụ là *thực hiện thân ít nhất một lần rồi mới kiểm tra*, có thể xem xét `do ... while`:

~~~java
int n = 0;
do {
    System.out.println("run once");
} while (n > 0);
~~~

Đoạn này in `run once` một lần dù điều kiện false. Đây là khác biệt về **thời điểm kiểm tra**, không phải tên hai cú pháp tương đương.

| Cấu trúc | Thời điểm kiểm tra | Số lượt ít nhất |
| --- | --- | ---: |
| `while (condition) { ... }` | Trước từng lượt, kể cả lượt đầu | 0 |
| `do { ... } while (condition);` | Sau từng lượt | 1 |
| `for (init; condition; update) { ... }` | Kiểm tra trước từng lượt sau init | 0 |

## 4. Điều kiện được đánh giá lại, không phải chỉ một lần

Trong `precheckTrace()`, phương thức `check(n)` in giá trị khi **được gọi**:

~~~text
check=0
body=0
check=1
body=1
check=2
after=2
~~~

Dòng `check=2` xuất hiện nhưng **không có `body=2`**: điều kiện đã false và vòng lặp dừng ngay.

Nếu biểu thức điều kiện đọc biến hoặc gọi phương thức, giá trị có thể thay đổi qua mỗi lần đánh giá. Vì vậy hãy tránh đặt **tác dụng phụ khó quan sát** trong điều kiện; nếu buộc phải dùng, làm rõ thứ tự và điểm dừng.

Cũng không nên lẫn `while (flag = true)` với `while (flag == true)`: nếu `flag` có kiểu `boolean`, **phép gán `=` hợp lệ** và làm `flag` luôn true, dễ tạo vòng lặp vô hạn. Thường chỉ cần `while (flag)` khi muốn kiểm tra cờ.

## 5. Cần có cơ chế tiến triển và điểm dừng

Ví dụ lỗi:

~~~java
int n = 1;
// while (n <= 3) {
//     System.out.println(n);
//     // Quên n++: điều kiện cứ đúng mãi nếu không có can thiệp.
// }
~~~

Một vòng `while` hợp lý thường cần **biến điều khiển thay đổi**, dữ liệu đầu vào được tiêu thụ hoặc một lệnh thoát được bảo đảm xảy ra theo giả định nghiệp vụ.

Ví dụ an toàn theo kích thước mảng:

~~~java
String[] items = {"A", "B", "C"};
int index = 0;
while (index < items.length) {
    System.out.println(items[index]);
    index++;
}
~~~

Biến `index` tăng từ 0 tới `items.length`; ngay trước khi truy cập mảng, điều kiện xác nhận chỉ số nằm trong miền hợp lệ. Đừng sửa mảng/dữ liệu ngoài theo cách khiến giả định dừng không còn đúng.

**Cẩn thận `continue`:** nó bỏ phần còn lại của thân và quay về **kiểm tra điều kiện**. Nếu lệnh tăng biến nằm sau `continue`, vòng lặp có thể không tiến triển:

~~~java
int n = 0;
// while (n < 5) {
//     if (n == 0) continue; // Sai: n vẫn bằng 0.
//     n++;
// }
~~~

Trong `skipEvenNumbers()`, lệnh `n++` được đặt **trước** `continue` nên vòng lặp vẫn kết thúc.

## 6. `break`, `continue`, `return` và ngoại lệ

| Trường hợp | Tác dụng |
| --- | --- |
| Kết thúc thân bình thường | Quay lại đánh giá điều kiện `while` |
| `continue;` | Bỏ các lệnh còn lại của lượt hiện tại, quay về kiểm tra điều kiện của vòng lặp |
| `break;` | Thoát vòng lặp gần nhất, đi tới câu lệnh sau vòng |
| `return;` / `return value;` | Thoát **phương thức** hiện tại |
| `throw ...;` | Ném ngoại lệ, không tiếp tục luồng bình thường |

Dùng `while (true)` khi **chủ ý** điều khiển dừng bằng `break`/`return` hoặc ngoại lệ. Trong demo, điều kiện luôn true nhưng khi `attempts == 3`, `break` khiến vòng lặp kết thúc.

Với vòng lặp đọc dữ liệu cho tới ký hiệu kết thúc (*sentinel*) như `"stop"`, `break` giúp dừng ngay khi gặp ký hiệu. Trong ví dụ, `"99"` đứng sau `"stop"` **không được xử lý**, nên tổng chỉ là 2 + 4 = 6.

## 7. Tránh các lỗi phổ biến

| Nhầm lẫn | Điều đúng |
| --- | --- |
| “Thân `while` chạy rồi mới xét điều kiện.” | `while` **kiểm tra trước**, có thể chạy 0 lần |
| “Điều kiện chỉ đánh giá một lần.” | Đánh giá lại trước **mỗi lượt** |
| “`while (1)` giống C.” | Java yêu cầu điều kiện kiểu `boolean` |
| “`continue` đi thẳng tới cuối vòng mà không kiểm tra lại điều kiện.” | `while` vẫn đánh giá lại điều kiện |
| “`break` thoát luôn phương thức.” | `break` chỉ thoát vòng lặp (hoặc switch) gần nhất |
| “Thân không cần thay đổi gì vì điều kiện tự cập nhật.” | Cần cơ chế tiến triển/thoát phù hợp |
| “`while (flag = true)` chỉ kiểm tra flag.” | Đây là **gán true**, có thể gây vòng vô hạn |
| “Thêm `;` sau `while (condition)` vô hại.” | Tạo **thân rỗng**, khối sau đó có thể không thuộc vòng lặp |

Nếu điều kiện là một wrapper `Boolean` có giá trị `null`, việc unbox để đánh giá `while` sẽ gây `NullPointerException`. Với đầu vào có thể null, kiểm tra rõ ràng trước khi lặp hoặc dùng điều kiện an toàn như `Boolean.TRUE.equals(flag)`.

## 8. Bài tập và checklist

1. `int n = 5; while (n < 5) { n++; }` lặp mấy lượt? **0 lượt**.
2. `int n = 0; while (n < 2) { n++; }` điều kiện được đánh giá bao nhiêu lần nếu không có thoát bất thường? **3 lần** (đúng hai lần, sai lần cuối).
3. Vì sao `continue` trước `n++` có thể gây vô hạn? **Biến điều khiển không thay đổi**.
4. `while (true) { break; }` có thoát được không? **Có**, thoát ngay tại `break`.
5. Khi nào cân nhắc `do ... while`? **Khi thân phải chạy ít nhất một lần**.

**Tự kiểm:** Tôi có xác định rõ điều kiện kiểu `boolean`, thời điểm điều kiện được đánh giá, cơ chế tiến triển, đường thoát và hành vi khi điều kiện false ngay từ đầu hay chưa?

**Kết luận:** `while` là **vòng lặp kiểm tra trước**. Mỗi lượt đều phải qua điều kiện; thân có thể không chạy lần nào. Viết vòng lặp an toàn bằng cách giữ điều kiện rõ nghĩa, bảo đảm có tiến triển hoặc điểm dừng, và hiểu chính xác `break`/`continue`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="loops-002">do-while thực thi thân ít nhất một lần</a>

<details>
<summary>Xem chi tiết</summary>

# `do-while` thực thi thân ít nhất một lần trong Java

> **Java core — language-basics / Vòng lặp**  
> **Môi trường:** JDK 17+, một file Java độc lập, không cần thư viện ngoài.  
> **Mục tiêu:** Hiểu vì sao `do-while` kiểm tra điều kiện **sau** thân, chọn đúng giữa `while` và `do-while`, sử dụng vòng lặp để thử lại thao tác, và tránh các lỗi liên quan đến dấu `;`, `break`, `continue`.

## 1. Cú pháp và thứ tự thực thi

~~~java
do {
    // Công việc cần thực hiện.
} while (condition);
~~~

**Thứ tự khi chương trình đi tới vòng lặp:**

1. Thực thi thân `do { ... }` **trước khi kiểm tra điều kiện lần đầu**.
2. Sau khi thân hoàn thành bình thường, đánh giá `condition`, bắt buộc có kiểu `boolean` hoặc `Boolean` có thể unbox.
3. Nếu điều kiện `true`, quay lại thực thi thân; nếu `false`, thoát vòng lặp và chạy câu lệnh kế tiếp.

Nếu chương trình đã đi tới `do` thì thân **được bắt đầu ít nhất một lần**, kể cả điều kiện sẽ sai ngay ở lần kiểm tra đầu tiên. Điều đó không có nghĩa mọi câu lệnh trong thân chắc chắn chạy xong: `break`, `return`, ngoại lệ hoặc luồng bị ngắt có thể kết thúc sớm.

~~~java
int count = 0;

do {
    count++;
    System.out.println("count=" + count);
} while (count < 3);

// In count=1, count=2, count=3.
~~~

Dấu **`;` sau `while (condition)` là bắt buộc** trong cú pháp `do-while`. Đây là khác biệt dễ quên so với `while (condition) { ... }` thông thường.

## 2. So sánh `while` và `do-while` khi điều kiện sai ngay từ đầu

~~~java
int a = 0;
while (a < 0) {
    a++;
}

int b = 0;
do {
    b++;
} while (b < 0);

System.out.println(a); // 0
System.out.println(b); // 1
~~~

| Tiêu chí | `while` | `do-while` |
| --- | --- | --- |
| Kiểm tra điều kiện lần đầu | **Trước** thân | **Sau** thân |
| Điều kiện ban đầu false | Thân chạy **0 lần** | Thân chạy **1 lần** |
| Phù hợp với | Công việc chỉ được phép chạy nếu điều kiện đang đúng | Công việc phải thử/thực hiện lần đầu rồi mới quyết định lặp |
| Dấu `;` ở cuối cấu trúc | Không đặt sau khối `while { ... }` | **Phải có** sau `while (condition)` |

Đừng chọn `do-while` chỉ vì cú pháp ngắn. Hãy hỏi: **có cần thực hiện thao tác đầu tiên trước khi biết nên lặp lại không?** Nếu cần kiểm tra điều kiện trước khi cho phép thao tác, `while` hoặc `for` thường hợp lý hơn.

## 3. Bảng diễn tiến một vòng lặp cụ thể

Giả sử `n = 0` và điều kiện `n < 3`:

| Lượt thân | `n` trước thân | `n` sau `n++` | Kiểm tra `n < 3` | Tiếp tục? |
| ---: | ---: | ---: | --- | --- |
| 1 | 0 | 1 | true | Có |
| 2 | 1 | 2 | true | Có |
| 3 | 2 | 3 | false | Không |

Bảng này giúp kiểm tra lỗi lệch một lần (*off-by-one*). Trong `do-while`, điều kiện được đánh giá **sau khi** `n` đã tăng, nên chương trình in ba lượt, không phải bốn.

Một tình huống đơn giản chỉ muốn thực thi đúng một lần:

~~~java
int executions = 0;
do {
    executions++;
} while (false);
// executions == 1.
~~~

Cú pháp này hợp lệ, nhưng nếu **luôn chỉ thực hiện một lần** thì một khối lệnh thường dễ đọc hơn; không cần ép công việc một lần vào vòng lặp.

## 4. Ví dụ sử dụng: thử lại cho tới khi đầu vào hợp lệ

Với chương trình nhập liệu, có thể cần **đọc lần đầu**, kiểm tra kết quả, rồi hỏi lại khi không hợp lệ. Ví dụ dưới đây sử dụng mảng đầu vào mô phỏng để chạy bằng CLI mà không phải nhập tay:

~~~java
int[] candidates = {-2, 0, 7};
int index = 0;
int selected;

do {
    selected = candidates[index++];
    System.out.println("candidate=" + selected);
} while (selected <= 0 && index < candidates.length);

System.out.println("selected=" + selected);
~~~

Kết quả lần lượt là `candidate=-2`, `candidate=0`, `candidate=7` và `selected=7`.

**Lưu ý an toàn:** Ví dụ có ít nhất một phần tử. Nếu mảng có thể rỗng, phải kiểm tra độ dài **trước khi vào `do`** vì thân vẫn thử truy cập mảng một lần. Điều kiện `index < candidates.length` ngăn việc lặp tiếp quá giới hạn nhưng **không bảo vệ lần truy cập đầu tiên**.

Ngoài ra, nếu dữ liệu thử hết mà vẫn không có phần tử hợp lệ, vòng lặp cũng kết thúc vì hết dữ liệu; ứng dụng thực cần phân biệt **hết lượt thử** với **đã có kết quả hợp lệ**, ví dụ kiểm tra `selected > 0` sau vòng lặp.

## 5. `break` và `continue` trong `do-while`

- `break;` kết thúc ngay vòng lặp gần nhất; điều kiện ở cuối vòng **không** được đánh giá cho lượt bị `break`.
- `continue;` bỏ qua phần thân còn lại của lượt hiện tại và chuyển tới bước **đánh giá điều kiện `while (...)`** rồi mới quyết định có vào lượt mới hay không.
- `return` kết thúc phương thức, có thể đồng thời kết thúc vòng lặp; ngoại lệ chưa xử lý cũng có thể ngắt luồng.

Ví dụ với `break`:

~~~java
int tries = 0;
do {
    tries++;
    if (tries == 2) {
        System.out.println("breakAt=" + tries);
        break;
    }
} while (tries < 4);
// In: breakAt=2; tries == 2.
~~~

Ví dụ với `continue`:

~~~java
int i = 0;
do {
    i++;                         // Luôn cập nhật i trước continue.
    if (i % 2 == 0) {
        continue;
    }
    System.out.println("odd=" + i);
} while (i < 4);
// In odd=1, odd=3; i == 4.
~~~

Ở lượt `i == 2`, `continue` không trở lại đầu thân ngay lập tức: Java **kiểm tra `i < 4`** trước. Nếu quên cập nhật biến kiểm soát trước `continue`, vòng lặp có thể không bao giờ kết thúc.

## 6. Các lỗi phổ biến

| Lỗi | Vì sao nguy hiểm | Cách sửa |
| --- | --- | --- |
| Quên `;` sau `while (condition)` | Sai cú pháp `do-while` | Viết `} while (condition);` |
| Nghĩ điều kiện được xét trước thân | Có thể chạy một lần ngoài ý muốn | Dùng `while` nếu phải kiểm tra trước |
| Điều kiện không bao giờ false | Vòng lặp vô hạn, tốn tài nguyên | Cập nhật trạng thái và quy định điểm dừng |
| `continue` trước thao tác cập nhật | Có thể kẹt cùng một trạng thái | Cập nhật trước `continue` hoặc thiết kế lại |
| Dùng `do` để truy cập mảng có thể rỗng | Truy cập sai chỉ số ở lượt đầu | Guard kiểm tra rỗng trước vòng lặp |
| Cho rằng kết thúc vòng nghĩa là dữ liệu hợp lệ | Có thể dừng do hết lượt thử | Kiểm tra riêng trạng thái thành công |
| Dùng `int` thay vì boolean làm điều kiện | Không biên dịch trong Java | So sánh tường minh như `count < limit` |

Ví dụ vòng lặp dễ bị vô hạn, **không nên chạy**:

~~~java
// int x = 0;
// do {
//     System.out.println(x);
// } while (x < 3); // x không đổi, điều kiện luôn đúng.
~~~

`Boolean` wrapper có giá trị `null` cũng không phải một giá trị điều kiện hợp lệ lúc chạy: Java sẽ cố unbox và có thể ném `NullPointerException`. Dùng biến `boolean` nguyên thủy hoặc đảm bảo wrapper không null.

## 7. Chương trình Java 17+ chạy độc lập

Tạo file **DoWhileDemo.java**:

~~~java
public final class DoWhileDemo {
    public static void main(String[] args) {
        int whileCount = 0;
        while (whileCount < 0) {
            whileCount++;
        }
        System.out.println("whileInitiallyFalse=" + whileCount);

        int doCount = 0;
        do {
            doCount++;
        } while (doCount < 0);
        System.out.println("doInitiallyFalse=" + doCount);

        int rounds = 0;
        do {
            rounds++;
            System.out.println("round=" + rounds);
        } while (rounds < 3);

        int[] candidates = {-2, 0, 7};
        int index = 0;
        int selected;
        do {
            selected = candidates[index++];
            System.out.println("candidate=" + selected);
        } while (selected <= 0 && index < candidates.length);
        System.out.println("selected=" + selected);
        System.out.println("valid=" + (selected > 0));

        int tries = 0;
        do {
            tries++;
            if (tries == 2) {
                System.out.println("breakAt=" + tries);
                break;
            }
        } while (tries < 4);
        System.out.println("breakCount=" + tries);

        int i = 0;
        do {
            i++;
            if (i % 2 == 0) {
                continue;
            }
            System.out.println("odd=" + i);
        } while (i < 4);
        System.out.println("continueCount=" + i);

        int once = 0;
        do {
            once++;
        } while (false);
        System.out.println("once=" + once);
    }
}
~~~

Biên dịch và chạy tại thư mục chứa file (PowerShell, Command Prompt hoặc Bash):

~~~bash
javac -encoding UTF-8 DoWhileDemo.java
java DoWhileDemo
~~~

**Expected stdout chính xác:**

~~~text
whileInitiallyFalse=0
doInitiallyFalse=1
round=1
round=2
round=3
candidate=-2
candidate=0
candidate=7
selected=7
valid=true
breakAt=2
breakCount=2
odd=1
odd=3
continueCount=4
once=1
~~~

Đối chiếu: `doInitiallyFalse=1` khẳng định thân chạy một lần khi điều kiện ban đầu false; `round=1..3` thể hiện kiểm tra điều kiện sau thân; `breakCount=2` và `continueCount=4` minh họa hai lệnh điều khiển. `valid=true` chứng minh giá trị cuối cùng hợp lệ, thay vì chỉ suy luận từ việc vòng lặp đã dừng.

## 8. Bài tập và checklist

**Bài 1.** Với `int n = 5;`, so sánh số lần thân được chạy của `while (n < 3)` và `do { n++; } while (n < 3);`. Giải thích nguyên nhân.

**Bài 2.** Viết `do-while` tăng biến `i` từ 0 và in `i` cho đến khi `i == 4`. Lập bảng trạng thái trước thân, sau thân và sau phép kiểm tra.

**Bài 3.** Cho một danh sách số có thể rỗng: hãy bổ sung guard trước `do` để tránh truy cập sai phần tử đầu tiên, và trả trạng thái lỗi nếu hết danh sách mà chưa gặp số dương.

**Bài 4.** Chèn `continue` khi `i` là số chẵn, chỉ in số lẻ từ 1 đến 5. Đặt thao tác cập nhật ở đâu để chắc chắn vòng lặp kết thúc?

**Bài 5.** Dùng `break` khi gặp giá trị cần tìm và giải thích điều kiện cuối vòng có còn được đánh giá trong lượt bị `break` hay không.

**Checklist:** Điều kiện boolean hợp lệ chưa? Biến kiểm soát có thay đổi trong mọi đường đi cần thiết không? Lượt đầu tiên có an toàn không? Có phân biệt dừng do thành công, giới hạn hay lỗi không? Đã ghi `;` kết thúc cú pháp `do-while` chưa?

### Ghi nhớ

**`do { ... } while (condition);` luôn bắt đầu thực hiện thân trước lần kiểm tra điều kiện đầu tiên.** Nó thích hợp cho công việc **phải thử một lần rồi mới quyết định lặp**. Khác với `while`, điều kiện ban đầu false không ngăn lượt đầu; hãy kiểm tra an toàn đầu vào, điểm dừng và vị trí cập nhật biến, đặc biệt khi dùng `continue`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="loops-003">for với phần khởi tạo, điều kiện và cập nhật</a>

<details>
<summary>Xem chi tiết</summary>

# `for` với phần khởi tạo, điều kiện và cập nhật — Java 17+

## 1. Vòng lặp `for` truyền thống là gì?

Trong Java, `for` truyền thống (*basic for statement*) thích hợp khi cần **khởi tạo trạng thái**, **kiểm tra điều kiện trước mỗi lượt**, rồi **cập nhật trạng thái sau lượt**. Cú pháp:

~~~java
for (initialization; condition; update) {
    // loop body
}
~~~

Ví dụ đếm từ 0 đến 2:

~~~java
for (int i = 0; i < 3; i++) {
    System.out.println("i=" + i);
}
~~~

**Exact expected stdout:**

~~~text
i=0
i=1
i=2
~~~

| Thành phần | Ví dụ | Vai trò |
|---|---|---|
| **Khởi tạo** (*initialization*) | `int i = 0` | Chạy **một lần** trước lượt đầu |
| **Điều kiện** (*condition*) | `i < 3` | Kiểm tra **trước mỗi lượt**, phải là `boolean` hoặc `Boolean` có thể unboxing |
| **Cập nhật** (*update*) | `i++` | Chạy **sau thân vòng lặp**, trước khi kiểm tra điều kiện tiếp |
| **Thân vòng lặp** (*body*) | `System.out.println(...)` | Chạy khi điều kiện đúng |

Dấu chấm phẩy giữa ba phần trong ngoặc là **bắt buộc** ngay cả khi một phần bị bỏ trống. Đừng nhầm với `for (Type item : collection)` (*enhanced for*), có cú pháp và mục tiêu khác.

## 2. Thứ tự thực thi chính xác

Với `for (int i = 0; i < 2; i++)`, thứ tự là:

~~~text
Khởi tạo i = 0 (một lần)
    ↓
Kiểm tra i < 2
    ├── false → kết thúc for
    └── true
          ↓
       Thân vòng lặp
          ↓
       Cập nhật i++
          ↓
       Quay lại kiểm tra i < 2
~~~

~~~mermaid
flowchart TD
    A["Chạy initialization một lần"] --> B{"condition đúng?"}
    B -- "Không" --> E["Rời vòng for"]
    B -- "Có" --> C["Chạy body"]
    C --> D["Chạy update"]
    D --> B
~~~

Có thể quan sát từng bước bằng hàm in log:

~~~java
static int init() {
    System.out.println("init");
    return 0;
}

static boolean check(int i) {
    System.out.println("check " + i);
    return i < 2;
}

static int next(int i) {
    System.out.println("update " + i);
    return i + 1;
}

// Trong main:
for (int i = init(); check(i); i = next(i)) {
    System.out.println("body " + i);
}
~~~

**Exact expected stdout:**

~~~text
init
check 0
body 0
update 0
check 1
body 1
update 1
check 2
~~~

**Điểm cần nhớ:** Sau lượt cuối vẫn có bước **update**, rồi **condition** được kiểm tra và trả false. Khi điều kiện ban đầu false thì body và update **không chạy**.

## 3. Phần khởi tạo: chỉ chạy một lần

Phần `initialization` thường khai báo và khởi tạo biến đếm:

~~~java
for (int index = 0; index < 4; index++) {
    System.out.println(index);
}
~~~

Biến đếm cũng có thể được **khai báo từ trước**:

~~~java
int index = 0;
for (; index < 3; index++) {
    // Dùng biến index đã có.
}
System.out.println(index); // 3
~~~

Lúc đó initialization bị bỏ trống nhưng **dấu `;` đầu vẫn phải có**.

Java còn cho khai báo **nhiều biến cùng một kiểu** trong phần khởi tạo:

~~~java
for (int left = 0, right = 6; left < right; left++, right--) {
    System.out.println(left + ":" + right);
}
~~~

**Exact expected stdout:**

~~~text
0:6
1:5
2:4
~~~

Không khai báo hai kiểu khác nhau trong **cùng một danh sách khai báo**:

~~~java
// Không hợp lệ:
// for (int i = 0, long j = 3; i < 3; i++) { }
~~~

Nếu cần các loại biến khác nhau, khai báo chúng trước `for`.

## 4. Phần điều kiện: kiểu boolean, được xét trước mỗi lượt

Ví dụ:

~~~java
int count = 0;
for (int i = 5; i < 5; i++) {
    count++;
}
System.out.println(count); // 0
~~~

Vì `5 < 5` sai ngay lúc bắt đầu nên không có lượt lặp nào.

Java không có kiểu “truthy” theo nghĩa số khác 0 tự thành true:

~~~java
// Sai: int không dùng trực tiếp làm điều kiện.
// for (int i = 3; i; i--) { }
~~~

Sửa bằng điều kiện boolean rõ ràng:

~~~java
for (int i = 3; i != 0; i--) {
    System.out.println(i);
}
~~~

**Exact expected stdout:**

~~~text
3
2
1
~~~

Nếu điều kiện là `Boolean` có thể null, Java sẽ cần **unboxing** lúc xét; null gây `NullPointerException`. Trong vòng lặp cơ bản nên dùng `boolean` nguyên thủy hoặc xử lý null có chủ đích.

## 5. Phần cập nhật: chạy sau body, không phải trước body

Cập nhật thường là `i++`, `i--`, `i += 2` hoặc một danh sách các biểu thức cập nhật:

~~~java
for (int i = 0; i <= 6; i += 2) {
    System.out.println(i);
}
~~~

**Exact expected stdout:**

~~~text
0
2
4
6
~~~

Nếu danh sách cập nhật có hai biểu thức:

~~~java
for (int a = 0, b = 4; a < b; a++, b--) {
    System.out.println(a + "/" + b);
}
~~~

**Exact expected stdout:**

~~~text
0/4
1/3
~~~

Các biểu thức cập nhật được thực thi **theo thứ tự từ trái sang phải**. Trong vị trí update, `i++` và `++i` có cùng tác dụng lên `i` khi kết quả biểu thức không được dùng, dù ở ngữ cảnh khác hậu tố và tiền tố có thể trả giá trị khác nhau.

**Không phải** mọi biểu thức Java đều được dùng tùy ý ở initializer/update: danh sách này phải gồm những *statement expressions* hợp lệ, chẳng hạn gán, tăng/giảm, gọi phương thức hay tạo đối tượng; không thể đặt trần `i + 1` để thay `i++`.

## 6. Có thể bỏ trống ba thành phần, nhưng phải kiểm soát kết thúc

Một hoặc nhiều thành phần của `for` có thể bỏ trống:

~~~java
int cursor = 0;
for (; cursor < 3;) {
    System.out.println(cursor);
    cursor++; // Cập nhật bên trong body.
}
~~~

**Exact expected stdout:**

~~~text
0
1
2
~~~

Nếu bỏ hẳn điều kiện, nó được xem là **luôn true**:

~~~java
int attempts = 0;
for (;;) {
    attempts++;
    if (attempts == 3) {
        break;
    }
}
System.out.println(attempts); // 3
~~~

`for (;;)` là dạng vòng lặp vô hạn theo cú pháp; muốn có điểm thoát phải thiết kế `break`, `return`, `throw` hoặc cơ chế dừng khác.

**Nguy cơ:** `for (int i = 0; i < 3; )` mà body không tăng `i` có thể không bao giờ kết thúc nếu không có đường thoát khác. Bỏ trống update chỉ nên dùng khi cơ chế thay đổi điều kiện nằm ở nơi rõ ràng.

## 7. `break` và `continue` tác động đến update ra sao?

Trong **basic for**:

- `break;` thoát ngay vòng lặp; không chạy phần update của lượt đang bị ngắt.
- `continue;` bỏ phần còn lại của body, nhưng **vẫn chạy update của for**, sau đó mới kiểm tra điều kiện lượt kế.
- `return` kết thúc phương thức; không tiếp tục update của vòng lặp ấy.
- Nếu có exception làm thoát vòng lặp, update cũng không tự chạy như một phần `finally`.

~~~java
for (int i = 0; i < 5; i++) {
    if (i == 2) {
        System.out.println("continue=" + i);
        continue;
    }
    if (i == 4) {
        System.out.println("break=" + i);
        break;
    }
    System.out.println("visit=" + i);
}
~~~

**Exact expected stdout:**

~~~text
visit=0
visit=1
continue=2
visit=3
break=4
~~~

Sau `continue=2`, update `i++` vẫn diễn ra nên lượt kế là 3. Sau `break=4`, vòng lặp kết thúc ngay.

**Phân biệt với `while`:** khi tự đặt `i++` cuối body của `while`, một `continue` ở phía trước có thể bỏ qua thao tác tăng và gây lặp mãi. Với `for` truyền thống, update nằm ở phần thứ ba vẫn được chạy sau `continue` thông thường.

## 8. Phạm vi biến đếm: khai báo ở trong hay ngoài `for`?

Một biến được khai báo trong `ForInit` dùng được ở điều kiện, phần cập nhật và thân của **chính** vòng đó; nó không còn thuộc phạm vi sau vòng:

~~~java
for (int i = 0; i < 3; i++) {
    System.out.println(i);
}
// System.out.println(i); // Không biên dịch: i đã ngoài phạm vi.
~~~

Nếu ứng dụng cần biết giá trị cuối sau vòng, khai báo biến **trước** `for`:

~~~java
int index = 0;
for (; index < 3; index++) {
    // Có thể dùng index ở đây.
}
System.out.println(index); // 3.
~~~

Không khai báo lại biến cục bộ cùng tên khi phạm vi còn chồng lấn. Biến đếm đang thay đổi trong `update` cũng không phải biến **effectively final** để lambda bắt trực tiếp; có thể sao chép giá trị của nó vào một biến cục bộ trong từng lượt nếu cần.

Ở bài này, phạm vi là điều cần nhớ để dùng đúng ba thành phần `for`. Bài **vòng đời biến đếm** đi sâu vào thời điểm khởi tạo lại, `continue`/`break`, phạm vi lồng nhau và truy vết biến theo từng lượt.


## 9. Duyệt mảng với đúng biên

Mảng dài `n` chỉ có chỉ số hợp lệ `0..n-1`:

~~~java
int[] values = {4, 7, 9};
int total = 0;
for (int i = 0; i < values.length; i++) {
    total += values[i];
}
System.out.println(total); // 20.
~~~

Điều kiện đúng để **đọc phần tử** là `i < values.length`, không phải `i <= values.length`: ở lượt `i == length`, truy cập `values[i]` sẽ ném `ArrayIndexOutOfBoundsException`. Ngược lại, khi bài toán thật sự cần **bao gồm** đầu mút, `<=` hoàn toàn có thể đúng.

Duyệt ngược từ cuối mảng thường viết `for (int i = values.length - 1; i >= 0; i--)`; mảng rỗng sẽ lặp 0 lượt. Điều kiện đầu, bước cập nhật và biên cuối luôn phải tương thích. Bài **lỗi lệch một đơn vị** phân tích sâu đoạn nửa mở `[start,end)`, truy cập mảng rỗng và kiểm thử biên.


## 10. `for` lồng nhau: initializer của vòng trong chạy lại

~~~java
int cells = 0;
for (int row = 0; row < 2; row++) {
    for (int col = 0; col < 3; col++) {
        cells++;
    }
}
System.out.println(cells); // 6.
~~~

Ở đây vòng ngoài có 2 lượt; mỗi lượt **khởi tạo lại `col = 0`**, rồi vòng trong chạy 3 lượt. Tổng là `2 × 3 = 6`. Nếu giới hạn vòng trong phụ thuộc `row`, không thể luôn nhân hai kích thước cố định: phải cộng số lượt trong của từng lượt ngoài.

Với thân chi phí hằng số và cận độc lập `n`, `m`, độ phức tạp là `O(nm)`. Bài **vòng lặp lồng nhau** sẽ truy vết từng cặp biến đếm, duyệt mảng hai chiều không đều và phân biệt `break`/`continue` có nhãn.


## 11. Chọn cú pháp để ý định rõ ràng

| Công việc | Lựa chọn thường dễ đọc |
| --- | --- |
| Cần chỉ số hoặc thay đổi biến đếm rõ ràng | Basic `for (init; condition; update)` |
| Chỉ cần lần lượt lấy giá trị từng phần tử | Enhanced `for (Type value : array)` |
| Số lượt phụ thuộc sự kiện/trạng thái bên ngoài | `while` |
| Phải thử thân ít nhất một lần rồi mới kiểm tra | `do-while` |

~~~java
int[] numbers = {4, 7, 9};
for (int i = 0; i < numbers.length; i++) {
    System.out.println("index=" + i + ",value=" + numbers[i]);
}
for (int value : numbers) {
    System.out.println("value=" + value);
}
~~~

Enhanced `for` không phơi bày ba thành phần của basic `for`; nếu cần truy cập bằng chỉ số, hãy dùng basic `for`. Với collection, sửa cấu trúc trong lúc đang duyệt còn liên quan tới hợp đồng của iterator. Không có loại vòng nào luôn nhanh hơn các loại khác. Bài **chọn loại vòng lặp** sẽ so sánh điểm kiểm tra điều kiện và các tình huống thực hành.


## 12. Chương trình Java 17 hoàn chỉnh

Tạo file **`BasicForThreePartsDemo.java`** với đúng nội dung:

~~~java
public final class BasicForThreePartsDemo {
    private BasicForThreePartsDemo() {}

    static int init() {
        System.out.println("init");
        return 0;
    }

    static boolean check(int i) {
        System.out.println("check " + i);
        return i < 2;
    }

    static int update(int i) {
        System.out.println("update " + i);
        return i + 1;
    }

    public static void main(String[] args) {
        System.out.println("== order ==");
        for (int i = init(); check(i); i = update(i)) {
            System.out.println("body " + i);
        }

        System.out.println("== basic ==");
        for (int i = 0; i < 3; i++) {
            System.out.println("i=" + i);
        }

        System.out.println("== zero iterations ==");
        int count = 0;
        for (int i = 4; i < 4; i++) {
            count++;
        }
        System.out.println("count=" + count);

        System.out.println("== two variables ==");
        for (int left = 0, right = 6;
                left < right; left++, right--) {
            System.out.println("pair=" + left + ":" + right);
        }

        System.out.println("== continue and break ==");
        for (int i = 0; i < 5; i++) {
            if (i == 2) {
                System.out.println("continue=" + i);
                continue;
            }
            if (i == 4) {
                System.out.println("break=" + i);
                break;
            }
            System.out.println("visit=" + i);
        }

        System.out.println("== array ==");
        int[] numbers = {4, 7, 9};
        int total = 0;
        for (int i = 0; i < numbers.length; i++) {
            total += numbers[i];
        }
        System.out.println("sum=" + total);

        System.out.println("== nested ==");
        int cells = 0;
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 3; c++) {
                cells++;
            }
        }
        System.out.println("cells=" + cells);

        System.out.println("== omitted parts ==");
        int cursor = 0;
        for (; cursor < 3;) {
            System.out.println("cursor=" + cursor);
            cursor++;
        }

        int attempts = 0;
        for (;;) {
            attempts++;
            if (attempts == 3) {
                break;
            }
        }
        System.out.println("infinite break=" + attempts);

        System.out.println("== outer variable ==");
        int index = 0;
        for (; index < 3; index++) {
            // Body can be empty.
        }
        System.out.println("after i=" + index);
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên, terminal ở thư mục chứa file; không dùng thư viện ngoài JDK, Maven hay Gradle.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 BasicForThreePartsDemo.java
java -cp . BasicForThreePartsDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\BasicForThreePartsDemo.java
java -cp . BasicForThreePartsDemo
~~~

### Exact expected stdout

~~~text
== order ==
init
check 0
body 0
update 0
check 1
body 1
update 1
check 2
== basic ==
i=0
i=1
i=2
== zero iterations ==
count=0
== two variables ==
pair=0:6
pair=1:5
pair=2:4
== continue and break ==
visit=0
visit=1
continue=2
visit=3
break=4
== array ==
sum=20
== nested ==
cells=6
== omitted parts ==
cursor=0
cursor=1
cursor=2
infinite break=3
== outer variable ==
after i=3
~~~

Đối chiếu: `init` một lần; `check` chạy trước mỗi body và cả lượt kiểm tra cuối thất bại; `update` chạy sau body bình thường/continue; cặp hai biến kết thúc khi `left == right`; array có `4+7+9=20`; vòng lồng nhau có `2×3=6`; vòng `for (;;)` thoát nhờ `break`.

## 13. Lỗi thường gặp và cách sửa

| Lỗi | Hậu quả | Cách xử lý |
|---|---|---|
| Nhầm init chạy mỗi lượt | Hiểu sai thứ tự hoặc chi phí | Init chỉ chạy **một lần** |
| Nhầm update chạy trước body | Lệch giá trị hiển thị | Order: init → condition → body → update |
| `i <= array.length` khi lấy `array[i]` | Truy cập vượt cuối mảng | `i < array.length` |
| `for (int i=0; i; i++)` | Không biên dịch, điều kiện `int` | Dùng biểu thức boolean |
| Bỏ update mà body cũng không đổi biến | Nguy cơ lặp vô hạn | Thêm cập nhật hoặc đường thoát |
| Dùng `i++` khi muốn đếm ngược | Sai chiều lặp | Dùng `i--` với điều kiện thích hợp |
| Tưởng `continue` bỏ cả update trong `for` | Sai luồng điều khiển | Basic `for` vẫn thực hiện update |
| Tưởng `break` vẫn chạy update trước khi thoát | Sai giá trị cuối | `break` bỏ update |
| Dùng `i` sau vòng khi khai báo trong init | Không thuộc phạm vi | Khai báo `i` ngoài vòng nếu cần |
| `for(int i=0, long j=0;...)` | Cú pháp sai ở phần khai báo | Dùng cùng kiểu hoặc khai báo trước |
| Viết `i + 1` trong update | Không phải statement expression hợp lệ | Viết `i++`, `i += 1` hoặc `i = i + 1` |
| Dùng biến `int` tăng tới `Integer.MAX_VALUE` mà không xét overflow | Có thể tràn và lặp sai | Chọn điều kiện/kiểu phù hợp, kiểm tra biên |
| Cố dùng `for` ba phần khi chỉ cần đọc từng phần tử | Code dài, dễ lỗi chỉ số | Cân nhắc enhanced `for` |
| Tạo `for (...); { ... }` vô tình | Dấu `;` kết thúc loop với body rỗng | Bỏ `;` thừa trước block |

## 14. Bài tập có đáp án

### Bài A — in số chẵn từ 0 tới 8

~~~java
for (int i = 0; i <= 8; i += 2) {
    System.out.println(i);
}
~~~

**Expected stdout:**

~~~text
0
2
4
6
8
~~~

### Bài B — đếm ngược từ 3 tới 1

~~~java
for (int i = 3; i >= 1; i--) {
    System.out.println(i);
}
~~~

**Expected stdout:**

~~~text
3
2
1
~~~

### Bài C — có bao nhiêu lượt?

~~~java
int count = 0;
for (int i = 10; i < 10; i++) {
    count++;
}
System.out.println(count);
~~~

**Đáp án:** `0`. Điều kiện sai trước body đầu tiên.

### Bài D — tìm lỗi khi duyệt mảng

~~~java
int[] data = {1, 2, 3};
// for (int i = 0; i <= data.length; i++) {
//     System.out.println(data[i]);
// }
~~~

**Đáp án:** sửa `i <= data.length` thành `i < data.length`. Chỉ số cuối là 2, không phải 3.

### Bài E — `continue` có làm tăng `i` không?

~~~java
for (int i = 0; i < 4; i++) {
    if (i == 1) continue;
    System.out.println(i);
}
~~~

**Expected stdout:**

~~~text
0
2
3
~~~

Với `i == 1`, `continue` chuyển sang bước **update `i++`**, rồi điều kiện được xét lại. Không bị kẹt ở 1.

### Bài F — hai biến trong cùng `for`

~~~java
for (int a = 0, b = 5; a < b; a++, b--) {
    System.out.println(a + ":" + b);
}
~~~

**Expected stdout:**

~~~text
0:5
1:4
2:3
~~~

Đến bước cập nhật kế, `a == 3`, `b == 2`, điều kiện `a < b` sai.

## 15. Checklist và kết luận

- [ ] Viết được cấu trúc `for (init; condition; update) { body }`.
- [ ] Nhớ init **một lần**, condition **trước mỗi lượt**, update **sau body**.
- [ ] Hiểu vòng lặp có thể chạy **0 lượt** khi điều kiện đầu tiên false.
- [ ] Biết điều kiện phải là `boolean`/`Boolean`, không phải số nguyên truthy/falsy.
- [ ] Dùng được nhiều biến cùng kiểu ở init và nhiều biểu thức update.
- [ ] Biết cách bỏ trống một phần, vẫn giữ hai dấu `;`.
- [ ] Nhận ra `for (;;)` cần thiết kế cơ chế thoát.
- [ ] Phân biệt `continue` vẫn chạy update với `break` thoát ngay.
- [ ] Hiểu phạm vi biến đếm khai báo bên trong init.
- [ ] Tránh lỗi off-by-one bằng `i < array.length`.
- [ ] Biết khi nào dùng basic `for`, enhanced `for`, hay `while`.
- [ ] Chạy `BasicForThreePartsDemo.java` với `javac`/`java` trên JDK 17+ và đối chiếu exact stdout.

**Kết luận:** Trong Java 17+, ba phần của basic `for` biểu diễn rõ **thiết lập ban đầu**, **điều kiện tiếp tục** và **bước thay đổi sau mỗi lượt**. Để tránh lỗi, hãy kiểm tra thứ tự `init → condition → body → update`, bảo đảm điều kiện có thể kết thúc, và chú ý phạm vi biến cùng các giá trị tại biên.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="loops-004">Vòng đời biến đếm trong for</a>

<details>
<summary>Xem chi tiết</summary>

# Vòng đời biến đếm trong `for` — Java 17+

Vòng lặp `for` cơ bản thường dùng **biến đếm** để theo dõi số lần lặp hoặc vị trí đang xử lý. Hiểu đúng *lúc biến được khởi tạo, lúc nào nó thay đổi và phạm vi sử dụng* giúp tránh lỗi lệch một đơn vị, dùng biến ngoài phạm vi và vòng lặp không kết thúc.

~~~java
for (int i = 0; i < 3; i++) {
    System.out.println(i);
}
// In lần lượt: 0, 1, 2.
~~~

Trong ví dụ này, `i` được **khai báo và khởi tạo một lần** khi câu lệnh `for` bắt đầu, lần lượt mang các giá trị 0, 1, 2 trong thân; sau khi cập nhật thành 3 thì điều kiện sai và vòng lặp kết thúc. Biến `i` **không còn nằm trong phạm vi truy cập** sau câu lệnh `for` vì được khai báo trong phần khởi tạo.

## 1. Phân biệt giá trị biến đếm với phạm vi của tên

Trong `for (int i = 0; i < 3; i++)`, phần `int i = 0` chỉ chạy **một lần mỗi khi chương trình đi vào câu lệnh `for`**; chính biến `i` ấy nhận giá trị 0, 1, 2 qua các lượt thân. Sau lượt cuối, phép cập nhật đưa `i` lên 3 rồi điều kiện sai. Nếu khai báo `i` ở đầu `for`, mã sau vòng **không được tham chiếu tên `i`**; đây là quy tắc phạm vi tại lúc biên dịch, không phải phép kiểm tra giá trị lúc chạy.

| Tình huống | Điều cần theo dõi |
| --- | --- |
| Một câu `for` chạy nhiều lượt | Khởi tạo một lần, cập nhật sau mỗi lượt bình thường hoặc `continue` nhắm tới vòng |
| Câu `for` nằm trong vòng ngoài hoặc phương thức được gọi lại | **Mỗi lần đi vào câu lệnh** là một lần khởi tạo mới |
| `break` dừng giữa chừng | Không có lần cập nhật cuối của vòng bị thoát |
| Biến khai báo trong `for` | Dùng được ở điều kiện, cập nhật, thân; hết phạm vi sau `for` |
| Biến khai báo trước `for` | Vẫn trong phạm vi sau vòng để kiểm tra giá trị cuối |

Vì thế, khi muốn quan sát giá trị sau vòng, hãy khai báo biến bên ngoài. Khi muốn giới hạn biến đếm cho đúng một thao tác duyệt, khai báo ở đầu `for` thường an toàn và dễ đọc. Các ví dụ dưới đây tập trung vào **phạm vi, lần tái khởi tạo và những đường thoát**, còn thứ tự ba thành phần được giới thiệu ở bài `for` trước đó.
## 2. Chương trình Java 17+ có thể chạy bằng CLI

Tạo tệp UTF-8 tên **`ForCounterLifecycleDemo.java`**:

~~~java
public class ForCounterLifecycleDemo {
    static void zeroIterations() {
        for (int i = 3; i < 3; i++) {
            System.out.println("unreachable");
        }
        System.out.println("zero=done");
    }

    static void declaredOutside() {
        int index = 0;
        for (; index < 3; index++) {
            System.out.println("external=" + index);
        }
        System.out.println("externalAfter=" + index);
    }

    static int step(int i) {
        System.out.println("step=" + i);
        return i + 1;
    }

    static void continueAndBreak() {
        for (int i = 0; i < 4; i = step(i)) {
            if (i == 1) {
                System.out.println("continueAt=1");
                continue;
            }
            if (i == 3) {
                System.out.println("breakAt=3");
                break;
            }
            System.out.println("seen=" + i);
        }
    }

    static void nested() {
        for (int row = 0; row < 2; row++) {
            for (int col = 0; col < 2; col++) {
                System.out.println("grid=" + row + "," + col);
            }
        }
    }

    static void twoCounters() {
        for (int left = 0, right = 3;
             left < right;
             left++, right--) {
            System.out.println("pair=" + left + "," + right);
        }
    }

    static void repeatedEntry() {
        for (int run = 1; run <= 2; run++) {
            int sum = 0;
            for (int i = 0; i < 2; i++) {
                sum += i;
            }
            System.out.println("run=" + run + ",sum=" + sum);
        }
    }

    public static void main(String[] args) {
        System.out.println("== initial false ==");
        zeroIterations();

        System.out.println("== outer scope ==");
        declaredOutside();

        System.out.println("== continue and break ==");
        continueAndBreak();

        System.out.println("== nested ==");
        nested();

        System.out.println("== two counters ==");
        twoCounters();

        System.out.println("== re-enter ==");
        repeatedEntry();
    }
}
~~~

Mở PowerShell (Windows) hoặc Bash (Linux/macOS) trong thư mục chứa file; sử dụng **JDK 17 trở lên**:

~~~bash
java -version
javac -version
javac -encoding UTF-8 ForCounterLifecycleDemo.java
java ForCounterLifecycleDemo
~~~

**Exact expected stdout** của riêng lệnh `java ForCounterLifecycleDemo`:

~~~text
== initial false ==
zero=done
== outer scope ==
external=0
external=1
external=2
externalAfter=3
== continue and break ==
seen=0
step=0
continueAt=1
step=1
seen=2
step=2
breakAt=3
== nested ==
grid=0,0
grid=0,1
grid=1,0
grid=1,1
== two counters ==
pair=0,3
pair=1,2
== re-enter ==
run=1,sum=1
run=2,sum=1
~~~

Đầu ra `java -version` và `javac -version` thay đổi theo môi trường, không nằm trong khối stdout cố định. Chương trình không cần thư viện bên ngoài.

## 3. Phạm vi biến đếm phụ thuộc vị trí khai báo

### Khai báo ngay trong `for`

~~~java
for (int i = 0; i < 3; i++) {
    System.out.println(i); // Hợp lệ.
}
// System.out.println(i); // KHÔNG biên dịch: i ngoài phạm vi.
~~~

Phạm vi của `i` khai báo trong **ForInit** bao gồm những phần liên quan của câu lệnh `for`: điều kiện, cập nhật và thân. Khi câu lệnh kết thúc, tên `i` không còn dùng được bên ngoài.

Nói “biến tồn tại đến khi thoát vòng” là một cách diễn đạt trực quan về phạm vi; compiler quyết định **có được truy cập biến hay không** theo phạm vi khai báo, không phải theo việc liệu bộ nhớ vật lý của biến đã bị giải phóng hay chưa.

### Khai báo trước `for`

~~~java
int i = 0;
for (; i < 3; i++) {
    System.out.println(i);
}
System.out.println("after=" + i); // after=3.
~~~

Vì `i` được khai báo bên ngoài, nó **vẫn trong phạm vi** sau khi vòng lặp kết thúc. Chỗ khởi tạo trong đầu `for` có thể để trống nhưng vẫn phải giữ dấu `;`.

Khi đã có `int i` trong cùng phạm vi bao quanh, không được khai báo lại `int i` trong phần khởi tạo của một `for` lồng ở phạm vi xung đột:

~~~java
int i = 10;
// for (int i = 0; i < 3; i++) { } // Lỗi khai báo trùng tên.
for (int j = 0; j < 3; j++) { } // Hợp lệ.
~~~

Ngược lại, hai câu `for (int i = ... )` **nối tiếp** có thể khai báo cùng tên `i` vì phạm vi vòng trước đã kết thúc.

## 4. `continue`, `break` và phần cập nhật

**`continue`** trong `for` cơ bản bỏ các câu lệnh còn lại của lần lặp, nhưng vẫn đi tới **phần cập nhật** trước khi kiểm tra điều kiện tiếp. Demo cho thấy:

~~~text
seen=0
step=0
continueAt=1
step=1
seen=2
step=2
breakAt=3
~~~

Cụ thể: gặp `continue` tại `i == 1`, Java vẫn gọi `step(1)`, nhận 2 rồi kiểm tra điều kiện. Gặp `break` ở `i == 3`, Java rời vòng lặp **ngay**: không có `step=3`.

| Đường thực thi | Phần cập nhật có chạy? |
| --- | --- |
| Thân kết thúc bình thường | Có |
| `continue;` nhắm tới vòng `for` này | Có |
| `break;` thoát vòng này | Không |
| `return;` / `throw ...;` từ thân | Không theo luồng bình thường |
| Điều kiện false trước thân | Không |

Với vòng lặp lồng nhau hoặc `continue`/`break` có nhãn, phải xét **câu lệnh mà lệnh điều khiển nhắm tới**, không kết luận mọi `continue` đều chạy cập nhật của mọi vòng `for`.

## 5. Biến đếm không tự động bắt đầu từ 0

`for` không gắn biến với quy ước 0, 1, 2...; người viết quyết định giá trị ban đầu, điều kiện và cách cập nhật:

~~~java
for (int n = 5; n >= 1; n--) {
    System.out.print(n + " ");
}
// In: 5 4 3 2 1 (có dấu cách cuối).
~~~

Có thể dùng **hai biến cùng kiểu** trong phần khởi tạo và nhiều biểu thức cập nhật, ngăn bằng dấu phẩy:

~~~java
for (int left = 0, right = 3;
     left < right;
     left++, right--) {
    System.out.println(left + "," + right);
}
// In: 0,3 rồi 1,2.
~~~

Phần điều kiện trong `for` là **một biểu thức boolean**, không phải danh sách điều kiện bằng dấu phẩy. Dùng `&&` hoặc `||` nếu cần ghép điều kiện. Phần cập nhật cũng không bắt buộc là `i++`: có thể dùng `i += 2` hoặc một phép gán khác, miễn bảo đảm vòng lặp có cơ chế kết thúc phù hợp.

**Thân `for` có thể chạy 0 lần** nếu điều kiện false ngay sau khởi tạo. Trong ví dụ `for (int i = 3; i < 3; i++)`, chỉ có phần khởi tạo và kiểm tra đầu tiên; không chạy thân hoặc cập nhật.

## 6. Mỗi lần đi vào câu lệnh `for` là một lần khởi tạo mới

Khi cùng một câu lệnh `for` được thực hiện lại (ví dụ nằm trong thân một vòng ngoài hoặc phương thức được gọi lần nữa), phần khởi tạo của nó chạy **lại từ đầu**.

~~~java
for (int run = 1; run <= 2; run++) {
    for (int i = 0; i < 2; i++) {
        System.out.print(i);
    }
    System.out.println();
}
// In hai dòng: 01 rồi 01.
~~~

Biến `i` của vòng trong bắt đầu từ 0 mỗi lần câu lệnh vòng trong được đi vào; nó **không tiếp tục từ giá trị của lần chạy trước**.

Trong hai vòng lặp lồng nhau, dùng tên khác nhau như `row` và `col` để biểu đạt từng vai trò. Đổi nhầm biến ở điều kiện hoặc cập nhật có thể khiến vòng lặp chạy sai hoặc không kết thúc.

### Phân biệt `for` cơ bản với enhanced `for`

~~~java
int[] numbers = {4, 5};
for (int value : numbers) {
    System.out.println(value);
}
~~~

Đây là **enhanced for**. Biến `value` đại diện cho phần tử ở từng lượt; không có ba phần khởi tạo/điều kiện/cập nhật do người viết nêu rõ. Không lấy quy tắc `i++` trong basic `for` áp nguyên vẹn cho dạng này.

## 7. Các lỗi thường gặp

| Hiểu nhầm | Quy tắc đúng |
| --- | --- |
| “`int i = 0` trong `for` chạy lại ở mọi lượt.” | Khởi tạo chỉ một lần **mỗi lần đi vào câu lệnh for** |
| “`i` khai báo trong `for` dùng được sau vòng.” | Không; đã ra ngoài phạm vi |
| “`continue` bỏ cả phần cập nhật.” | Trong basic `for`, cập nhật vẫn chạy nếu `continue` nhắm tới vòng đó |
| “`break` vẫn chạy `i++` thêm lần nữa.” | Không |
| “`for` luôn thực hiện ít nhất một lượt.” | Có thể chạy 0 lượt |
| “Hai vòng `for` nối tiếp không được dùng cùng tên `i`.” | Được, nếu phạm vi khai báo không chồng lấn |
| “Mọi biến đếm bắt buộc là `int` hoặc bắt đầu từ 0.” | Chọn kiểu/khởi đầu phù hợp; miễn cú pháp và điều kiện hợp lệ |
| “`for (int i = 0; i < n; i++);` có thân là khối sau.” | Dấu `;` khiến vòng có **thân rỗng** |

**Tránh vòng vô hạn và tràn số:** Luôn xác nhận cập nhật đưa biến tiến tới điều kiện dừng. Với số nguyên cố định độ rộng, `i++` có thể tràn và quay vòng; đừng mặc định mọi phép tăng sẽ đạt một mốc toán học bất kỳ.

## 8. Bài tập tự kiểm tra

1. `for (int i=0; i<3; i++)`: thân chạy những giá trị nào?  
   *Đáp án:* 0, 1, 2; giá trị 3 chỉ được dùng ở lần kiểm tra cuối.
2. `i` khai báo trong đầu `for` có thể in sau dấu `}` của vòng lặp không?  
   *Đáp án:* Không, nằm ngoài phạm vi.
3. Nếu `int i=0; for (;i<2;i++) {}` thì sau vòng `i` bằng mấy?  
   *Đáp án:* 2.
4. Khi thân gặp `continue` nhắm tới basic `for`, Java làm gì tiếp?  
   *Đáp án:* Thực hiện phần cập nhật rồi kiểm tra điều kiện.
5. Nếu thân gặp `break` thì phần cập nhật có chạy không?  
   *Đáp án:* Không.
6. `for (int i=4; i<4; i++)` chạy thân bao nhiêu lần?  
   *Đáp án:* 0 lần.
7. Khi vòng `for` nằm trong vòng ngoài được thực hiện hai lần, phần khởi tạo vòng trong chạy mấy lần?  
   *Đáp án:* Hai lần, mỗi lần đi vào vòng trong một lần.

## Checklist

- [ ] Nhớ thứ tự **khởi tạo → kiểm tra → thân → cập nhật → kiểm tra lại**.
- [ ] Biết biến đếm khai báo trong ForInit nằm trong phạm vi của vòng, không ở ngoài.
- [ ] Phân biệt biến khai báo **trước vòng** với biến khai báo **trong đầu vòng**.
- [ ] Hiểu `continue` (nhắm tới vòng) vẫn thực hiện cập nhật, còn `break` thì không.
- [ ] Biết phần khởi tạo chạy lại khi cả câu lệnh `for` được đi vào lần khác.
- [ ] Kiểm tra điều kiện ban đầu, cập nhật và điểm dừng để tránh lặp sai/vô hạn.

**Kết luận:** Biến đếm trong basic `for` có một vòng đời cú pháp rõ ràng: được khởi tạo khi vào vòng, kiểm tra và cập nhật qua từng lượt, và chỉ được truy cập trong phạm vi khai báo. Nắm chắc thứ tự thực thi cùng `continue`/`break` giúp dự đoán đúng giá trị biến và viết vòng lặp an toàn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="loops-005">Vòng lặp vô hạn và điều kiện bảo đảm kết thúc</a>

<details>
<summary>Xem chi tiết</summary>

# Vòng lặp vô hạn và điều kiện bảo đảm kết thúc trong Java

> **Java core — language-basics / Vòng lặp**  
> **Môi trường:** Java 17+, ví dụ một file tự chứa, chỉ dùng JDK.  
> **Mục tiêu:** Phân biệt vòng lặp vô hạn do lỗi với vòng lặp chạy lâu có chủ đích; xác định điều kiện dừng, trạng thái tiến triển, giới hạn số lượt và cách tránh bẫy `continue`, `break`, tràn số.

## 1. Thế nào là vòng lặp vô hạn?

Một vòng lặp tiếp tục khi điều kiện lặp còn `true` (hoặc cố ý dùng `for (;;)`/`while (true)`). Nó trở thành **vòng lặp vô hạn** khi không có đường đi thực tế khiến luồng thoát khỏi vòng lặp.

~~~java
// Ví dụ lỗi: KHÔNG CHẠY đoạn này.
// int count = 0;
// while (count < 3) {
//     System.out.println(count);
//     // Quên count++: count mãi bằng 0.
// }
~~~

Có hai tình huống cần phân biệt:

- **Vô hạn ngoài ý muốn:** Biến không cập nhật, điều kiện không thể sai, bỏ qua cập nhật bằng `continue`, hoặc tràn số làm vòng lặp quay lại trạng thái cũ.
- **Vô hạn có chủ đích:** Bộ xử lý sự kiện, server hoặc worker chạy cho tới khi nhận tín hiệu dừng. Loại này vẫn cần cơ chế **hủy, ngắt, thoát hoặc đóng tài nguyên** rõ ràng.

Đừng coi “chạy rất lâu” đồng nghĩa “vô hạn”. Một vòng lặp có giới hạn hàng triệu lần vẫn hữu hạn, nhưng có thể tốn CPU hoặc làm ứng dụng thiếu phản hồi. Ngược lại, có điều kiện thoát trong mã **không bảo đảm** điều kiện đó nhất định sẽ xảy ra khi dữ liệu từ bên ngoài không hợp tác.

## 2. Ba câu hỏi để đánh giá khả năng kết thúc

Đối với mỗi vòng lặp, hãy xác định:

1. **Điều kiện tiếp tục / dừng:** Khi nào phép kiểm tra trở thành false, hoặc khi nào `break`/`return` được thực hiện?
2. **Trạng thái tiến triển:** Trong mọi đường chạy tiếp, biến hoặc dữ liệu nào được cập nhật để tiến gần điểm dừng?
3. **Giới hạn:** Có miền hữu hạn, giới hạn số lượt thử, deadline hoặc tín hiệu hủy đáng tin cậy không?

Một cách chứng minh hữu ích là tìm *loop variant* (đại lượng đo tiến triển): một số nguyên **không âm**, **giảm nghiêm ngặt mỗi lượt** khi vòng lặp tiếp tục và không bị reset hoặc tràn số. Khi đó nó không thể giảm mãi, nên vòng lặp phải dừng nếu mỗi lượt đều hoàn thành.

Ví dụ:

~~~java
int remaining = 4;

while (remaining > 0) {
    System.out.println("remaining=" + remaining);
    remaining--; // 4 -> 3 -> 2 -> 1 -> 0
}
~~~

Bất biến có ích: đầu mỗi lượt, `remaining >= 0`. Variant: `remaining`. Mỗi lượt giảm đúng 1, đến 0 thì `remaining > 0` false. Với biến ban đầu bằng 4, thân chạy bốn lần.

**Điều kiện cần nêu rõ:** Lập luận trên giả định thân vòng lặp luôn chạy đến bước cập nhật (không bị treo chờ bên ngoài) và phép tính không vi phạm miền/giới hạn kiểu số.

## 3. Ví dụ có bảo đảm kết thúc với `for` và `while`

~~~java
static int countUp(int limit) {
    int count = 0;
    for (int i = 0; i < limit; i++) {
        count++;
    }
    return count;
}
~~~

Với `limit = 4`, `i` đi qua 0, 1, 2, 3 và dừng khi `i == 4`. Với `limit <= 0`, thân chạy 0 lần. Điều kiện biên và cập nhật nằm gần nhau nên dễ kiểm tra.

Ví dụ đếm ngược:

~~~java
static int countdown(int start) {
    int remaining = Math.max(start, 0);
    int rounds = 0;
    while (remaining > 0) {
        rounds++;
        remaining--;
    }
    return rounds;
}
~~~

Với `start = 3`, variant là `remaining`: 3 → 2 → 1 → 0. Với `start <= 0`, vòng lặp không chạy.

Ví dụ tiến triển không cần giảm mỗi lần đúng 1:

~~~java
int n = 15;
int rounds = 0;
while (n > 0) {
    n /= 2;    // 15 -> 7 -> 3 -> 1 -> 0.
    rounds++;
}
// rounds == 4
~~~

Khi `n` là số nguyên dương, `n / 2 < n` và kết quả không âm, nên số lượt hữu hạn. Một variant tốt cần thể hiện **tiến triển trên mọi nhánh có thể quay lại đầu vòng lặp**.

## 4. Những lỗi khiến trạng thái không tiến triển

| Lỗi | Ví dụ | Cách sửa |
| --- | --- | --- |
| Quên cập nhật biến | `while (i < 5) { print(i); }` | Tăng `i` hoặc làm trạng thái thay đổi trong mọi đường đi |
| Cập nhật sai chiều | `while (i < 5) { i--; }` khi `i=0` | Cập nhật hướng tới ngưỡng dừng |
| `continue` bỏ qua cập nhật | `while (i < 5) { if (i==2) continue; i++; }` | Di chuyển cập nhật trước `continue` hoặc dùng `for` phù hợp |
| Điều kiện luôn đúng | `while (true)` không có cơ chế thoát | Thiết kế `break`, `return` hoặc tín hiệu hủy |
| Điều kiện chỉ phụ thuộc ngoại cảnh | Chờ một giá trị không bao giờ đến | Giới hạn retry, timeout và xử lý hết hạn |
| Tràn số ở biến điều khiển | `i++` sau `Integer.MAX_VALUE` | Dùng giới hạn phù hợp và không dựa vào overflow để dừng |

Ví dụ `continue` đúng với vòng `while`:

~~~java
int i = 0;
int oddCount = 0;

while (i < 6) {
    i++; // Cập nhật TRƯỚC mọi continue.
    if (i % 2 == 0) {
        continue;
    }
    oddCount++;
}
// i == 6; oddCount == 3 (1, 3, 5).
~~~

Trong `while`, `continue` nhảy thẳng tới kiểm tra điều kiện. Trong `for (init; condition; update)`, `continue` sẽ chuyển tới **biểu thức update** rồi mới kiểm tra điều kiện, miễn không có luồng thoát bất thường.

**Tràn `int`:** `Integer.MAX_VALUE + 1` trở thành `Integer.MIN_VALUE`. Vòng như sau có thể chạy mãi vì mọi giá trị `int` đều thỏa `i <= Integer.MAX_VALUE`:

~~~java
// KHÔNG CHẠY: điều kiện luôn đúng với kiểu int.
// for (int i = 0; i <= Integer.MAX_VALUE; i++) {
//     // ...
// }
~~~

Dùng điều kiện giới hạn thực sự có thể thành false, tránh so sánh với giá trị lớn nhất của kiểu, và cân nhắc `long` hay một thiết kế lặp khác nếu miền đếm lớn.

## 5. `while (true)` có thể đúng nếu có lối ra hữu hạn

Dùng vòng vô hạn có chủ đích khi **điểm dừng được quyết định trong thân**. Ví dụ đọc một dãy cho tới khi thấy số dương hoặc hết dữ liệu:

~~~java
static String firstPositive(int[] values) {
    int index = 0;
    while (true) {
        if (index >= values.length) {
            return "NOT_FOUND"; // Hết dữ liệu -> kết thúc.
        }

        int candidate = values[index++]; // Tiến triển trước continue.
        if (candidate <= 0) {
            continue;
        }
        return "FOUND:" + candidate;
    }
}
~~~

Dù điều kiện vòng là hằng `true`, hàm vẫn **bảo đảm kết thúc** với mảng hữu hạn: `index` tăng mỗi lượt, hoặc tìm được số dương để `return`, hoặc đến `values.length` để `return`. Không truy cập ngoài mảng, kể cả mảng rỗng vì kiểm tra trước khi lấy phần tử.

Đây là bằng chứng dừng dựa trên đại lượng `values.length - index` (không âm và giảm mỗi lượt có thể lặp lại).

**Nếu nguồn dữ liệu là mạng/bàn phím** thay vì mảng hữu hạn, việc “sớm muộn gì cũng có dữ liệu hợp lệ” chỉ là giả định. Cần tính đến hết luồng, timeout, lỗi I/O và tín hiệu hủy.

## 6. Chặn số lần thử, xử lý `break` và hủy tác vụ

Một giới hạn rõ ràng làm vòng lặp dừng ngay cả khi chưa tìm thấy kết quả:

~~~java
static String tryGuess(int[] guesses, int target, int maxAttempts) {
    int bound = Math.min(guesses.length, Math.max(maxAttempts, 0));
    int attempts = 0;

    while (attempts < bound) {
        int guess = guesses[attempts++];
        if (guess == target) {
            return "FOUND:" + attempts;
        }
    }
    return "STOP:" + attempts;
}
~~~

Vì `attempts` tăng mỗi lượt và `bound` cố định, không âm và không vượt độ dài mảng, vòng lặp dừng tối đa `bound` lượt. Hàm phân biệt thành công với hết lượt thử thay vì giả định kết thúc nghĩa là đã tìm thấy.

Một ví dụ ngắn cho `break`:

~~~java
int attempts = 0;
while (true) {
    attempts++;
    if (attempts == 2) {
        break;
    }
}
// attempts == 2.
~~~

`break` rời vòng gần nhất; `return` rời phương thức. Tuy nhiên, **chỉ đặt `break` trong thân chưa phải bằng chứng dừng**: phải chỉ ra vì sao điều kiện dẫn tới `break` cuối cùng chắc chắn đúng.

Với worker/service chạy lâu, bổ sung cơ chế hủy bằng cách phù hợp (ví dụ `Thread.interrupt()` và xử lý ngắt ở nơi chờ), tránh vòng `while (true)` quay liên tục không nghỉ làm chiếm CPU. Nếu dùng cờ dừng chia sẻ giữa các luồng, cần cơ chế nhìn thấy cập nhật đúng như `volatile` hoặc lớp đồng bộ phù hợp; biến boolean thường không đảm bảo đủ tính hiển thị giữa các luồng.

## 7. Chương trình Java 17+ runnable

Tạo file **LoopTerminationDemo.java**:

~~~java
public final class LoopTerminationDemo {
    private static int countUp(int limit) {
        int count = 0;
        for (int i = 0; i < limit; i++) {
            count++;
        }
        return count;
    }

    private static int countdown(int start) {
        int remaining = Math.max(start, 0);
        int rounds = 0;
        while (remaining > 0) {
            rounds++;
            remaining--;
        }
        return rounds;
    }

    private static int halveUntilZero(int start) {
        int n = Math.max(start, 0);
        int rounds = 0;
        while (n > 0) {
            n /= 2;
            rounds++;
        }
        return rounds;
    }

    private static String firstPositive(int[] values) {
        int index = 0;
        while (true) {
            if (index >= values.length) {
                return "NOT_FOUND";
            }

            int candidate = values[index++];
            if (candidate <= 0) {
                continue;
            }
            return "FOUND:" + candidate;
        }
    }

    private static int countOdds(int limit) {
        int i = 0;
        int oddCount = 0;
        while (i < limit) {
            i++;
            if (i % 2 == 0) {
                continue;
            }
            oddCount++;
        }
        return oddCount;
    }

    private static String tryGuess(
            int[] guesses, int target, int maxAttempts) {
        int bound = Math.min(guesses.length, Math.max(maxAttempts, 0));
        int attempts = 0;

        while (attempts < bound) {
            int guess = guesses[attempts++];
            if (guess == target) {
                return "FOUND:" + attempts;
            }
        }
        return "STOP:" + attempts;
    }

    public static void main(String[] args) {
        System.out.println("forCount4=" + countUp(4));
        System.out.println("forCount0=" + countUp(0));
        System.out.println("countdown3=" + countdown(3));
        System.out.println("countdown0=" + countdown(0));
        System.out.println("halve15=" + halveUntilZero(15));
        System.out.println("halve1=" + halveUntilZero(1));

        System.out.println("positiveFound="
                + firstPositive(new int[] {-2, 0, 5, 9}));
        System.out.println("positiveMissing="
                + firstPositive(new int[] {-3, 0}));
        System.out.println("positiveEmpty="
                + firstPositive(new int[] {}));

        System.out.println("oddsUpTo6=" + countOdds(6));

        int[] guesses = {2, 7, 9};
        System.out.println("guessFound=" + tryGuess(guesses, 7, 3));
        System.out.println("guessStop=" + tryGuess(guesses, 9, 2));
        System.out.println("guessZero=" + tryGuess(guesses, 9, 0));

        int attempts = 0;
        while (true) {
            attempts++;
            if (attempts == 2) {
                break;
            }
        }
        System.out.println("breakAfter=" + attempts);

        int max = Integer.MAX_VALUE;
        System.out.println("intOverflow=" + (max + 1));
    }
}
~~~

Biên dịch và chạy trong thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 LoopTerminationDemo.java
java LoopTerminationDemo
~~~

**Expected stdout chính xác:**

~~~text
forCount4=4
forCount0=0
countdown3=3
countdown0=0
halve15=4
halve1=1
positiveFound=FOUND:5
positiveMissing=NOT_FOUND
positiveEmpty=NOT_FOUND
oddsUpTo6=3
guessFound=FOUND:2
guessStop=STOP:2
guessZero=STOP:0
breakAfter=2
intOverflow=-2147483648
~~~

Những dòng `positiveMissing`, `positiveEmpty` và `guessStop` cho thấy lối thoát khi không thành công. `oddsUpTo6=3` chứng tỏ `continue` không chặn cập nhật biến điều khiển. `intOverflow` minh họa vì sao không dùng giới hạn `Integer.MAX_VALUE` theo cách khiến điều kiện luôn true.

## 8. Checklist và bài tập

| Câu hỏi trước khi chấp nhận vòng lặp | Yêu cầu |
| --- | --- |
| Điều kiện dừng là gì? | Có điều kiện trở thành false hoặc lối thoát bảo đảm đạt được |
| Tiến triển đo bằng đại lượng nào? | Không âm, tiến về giới hạn theo mọi đường tiếp tục |
| Có đường `continue` không cập nhật? | Cập nhật trước `continue` hoặc có update bảo đảm |
| Biến đếm có thể tràn số không? | Kiểm tra miền số và cấu trúc điều kiện |
| Có đang phụ thuộc vào input/nguồn ngoài? | Có giới hạn, timeout, EOF hoặc hủy |
| Khi hết lượt mà chưa thành công? | Trả trạng thái thất bại riêng, không suy diễn là thành công |

**Bài 1.** Xác định vì sao `int i=0; while(i<5){ if(i==2)continue; i++; }` kẹt ở `i == 2`, sau đó sửa bằng cách bảo đảm tăng `i` ở mọi đường đi.

**Bài 2.** Dùng variant để giải thích tại sao `while (remaining > 0) { remaining--; }` kết thúc khi `remaining` là số nguyên không âm ban đầu.

**Bài 3.** Thay `n /= 2` thành `n = n - 2` cho `n=15`. Viết điều kiện dừng sao cho không có giá trị âm gây hành vi sai ý định.

**Bài 4.** Viết `while(true)` để tìm phần tử phù hợp trong mảng hữu hạn. Phải có các trường hợp kết thúc nào khi mảng rỗng hoặc không tìm thấy?

**Bài 5.** Thiết kế hàm thử đăng nhập tối đa 3 lần: nếu đúng, trả thành công; nếu hết 3 lần, trả thất bại. Giải thích vì sao chỉ chờ người dùng nhập đúng không phải một bảo đảm kết thúc.

### Ghi nhớ

**Bảo đảm kết thúc = có điểm dừng + tiến triển đáng tin cậy + không mất điểm dừng do tràn số, đường nhánh hoặc dữ liệu bên ngoài.** Với vòng lặp hữu hạn, dùng giới hạn hoặc một variant không âm giảm nghiêm ngặt. Với `while(true)`, cần lối thoát có thể chứng minh và cơ chế hủy thích hợp; tránh vòng quay chiếm CPU không mục đích.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="loops-006">Lặp lồng nhau và theo dõi từng biến đếm</a>

<details>
<summary>Xem chi tiết</summary>

# Lặp lồng nhau và theo dõi từng biến đếm — Java 17+

## 1. Mục tiêu: hiểu rõ vòng ngoài và vòng trong

**Vòng lặp lồng nhau** (*nested loops*) là một vòng lặp nằm trong thân vòng lặp khác. Mỗi lần vòng ngoài đi qua một lượt, vòng trong được bắt đầu lại từ phần khởi tạo của nó.

~~~java
for (int i = 0; i < 2; i++) {
    for (int j = 0; j < 3; j++) {
        System.out.println("i=" + i + ",j=" + j);
    }
}
~~~

**Exact expected stdout:**

~~~text
i=0,j=0
i=0,j=1
i=0,j=2
i=1,j=0
i=1,j=1
i=1,j=2
~~~

Ở đây:

- `i` là **biến đếm vòng ngoài**: chạy qua 0 và 1.
- `j` là **biến đếm vòng trong**: chạy qua 0, 1, 2 **ở từng giá trị i**.
- Tổng lượt của thân trong là **2 × 3 = 6**, vì hai số lượt là độc lập.
- Sau khi vòng trong hoàn thành, vòng ngoài mới cập nhật `i`.

Mục tiêu bài học: theo dõi `i` và `j` từng bước, biết khi nào vòng trong khởi tạo lại, phân biệt các dạng lặp hình chữ nhật/tam giác, duyệt mảng hai chiều và kiểm soát `break`/`continue`.

## 2. Thứ tự thực thi từng bước

Với `for` cơ bản, thứ tự là **khởi tạo → kiểm tra điều kiện → thân → cập nhật → kiểm tra lại**.

Khi có hai vòng:

~~~text
Khởi tạo i = 0
  Kiểm tra i < 2
    Khởi tạo j = 0
      Kiểm tra j < 3 → thân(i,j) → j++
      Kiểm tra j < 3 → thân(i,j) → j++
      Kiểm tra j < 3 → thân(i,j) → j++
      Kiểm tra j < 3 → false: ra vòng trong
    Tiếp tục phần thân vòng ngoài nếu còn câu lệnh
  Cập nhật i++
  Kiểm tra i < 2 → khởi tạo j = 0 cho lượt ngoài kế tiếp
  ...
~~~

~~~mermaid
flowchart TD
    A["Khởi tạo i"] --> B{"Điều kiện vòng ngoài?"}
    B -- "Sai" --> H["Kết thúc"]
    B -- "Đúng" --> C["Khởi tạo j lại từ đầu"]
    C --> D{"Điều kiện vòng trong?"}
    D -- "Đúng" --> E["Xử lý cặp i,j"]
    E --> F["Cập nhật j"]
    F --> D
    D -- "Sai" --> G["Cập nhật i"]
    G --> B
~~~

**Chú ý:** vòng trong không chỉ “tiếp tục `j` từ lượt vòng ngoài trước”. Nếu `j` khai báo trong `for` trong, `int j = 0` chạy **lại mỗi lần** vòng ngoài tiến tới câu lệnh `for` đó.

## 3. Bảng truy vết `i` và `j`

Với `i = 0..1`, `j = 0..2`:

| Lượt thân trong | `i` | `j` | Diễn giải |
|---:|---:|---:|---|
| 1 | 0 | 0 | Lượt đầu của vòng ngoài đầu |
| 2 | 0 | 1 | Vòng trong cập nhật j |
| 3 | 0 | 2 | Lượt cuối của vòng trong |
| — | 0 | 3 | Điều kiện `j < 3` sai, không chạy body |
| 4 | 1 | 0 | Vòng ngoài tăng i, **j được khởi tạo lại** |
| 5 | 1 | 1 | Vòng trong tiếp tục |
| 6 | 1 | 2 | Lượt cuối |
| — | 1 | 3 | Vòng trong dừng; sau đó i tăng lên 2 |
| — | 2 | — | Điều kiện `i < 2` sai, vòng ngoài dừng |

Các hàng dấu **—** là bước kiểm tra, **không phải lượt thực thi body**.

Một phương pháp tự truy vết hữu ích: lập bảng gồm `i`, `j`, điều kiện vòng ngoài, điều kiện vòng trong và hành động, rồi **ghi riêng các lần kiểm tra trả false**. Cách này giúp phát hiện lỗi thiếu một lượt (*off-by-one*) hoặc hiểu nhầm lúc cập nhật.

## 4. Hình chữ nhật và tam giác: số lượt không phải lúc nào cũng nhân đơn giản

**Hình chữ nhật:** cận vòng trong độc lập với `i`.

~~~java
int count = 0;
for (int i = 0; i < 2; i++) {
    for (int j = 0; j < 3; j++) {
        count++;
    }
}
System.out.println(count); // 6
~~~

**Tam giác:** cận vòng trong **phụ thuộc `i`**.

~~~java
int count = 0;
for (int i = 1; i <= 3; i++) {
    for (int j = 1; j <= i; j++) {
        System.out.println(i + "," + j);
        count++;
    }
}
System.out.println("count=" + count);
~~~

**Exact expected stdout:**

~~~text
1,1
2,1
2,2
3,1
3,2
3,3
count=6
~~~

| `i` | Các `j` thực thi | Lượt vòng trong |
|---:|---|---:|
| 1 | 1 | 1 |
| 2 | 1, 2 | 2 |
| 3 | 1, 2, 3 | 3 |
| **Tổng** | | **1 + 2 + 3 = 6** |

Nếu `i` chạy từ 1 đến `n` và vòng trong `j = 1..i`, số lượt là \(\frac{n(n+1)}2\), không phải `n * n`. Khi `n` đủ lớn, vẫn thuộc bậc `O(n²)`.

## 5. Biến đếm vòng ngoài có giữ nguyên khi vòng trong chạy không?

Với cách viết thông thường, **chính thân trong không tăng `i`**, nên `i` giữ nguyên suốt một lần hoàn chỉnh của vòng trong:

~~~java
for (int i = 0; i < 2; i++) {
    System.out.println("start i=" + i);
    for (int j = 0; j < 2; j++) {
        System.out.println("  j=" + j);
    }
    System.out.println("end i=" + i);
}
~~~

**Exact expected stdout:**

~~~text
start i=0
  j=0
  j=1
end i=0
start i=1
  j=0
  j=1
end i=1
~~~

Tuy nhiên, Java **không tự cấm** thân trong sửa `i`. Nếu viết `i++` trong vòng trong, hoặc một hàm làm đổi trạng thái liên quan, số lượt vòng ngoài có thể thay đổi và code khó theo dõi. Quy tắc thực hành: để `i` do **vòng ngoài** quản lý, `j` do **vòng trong** quản lý, trừ khi thuật toán thật sự cần cách khác.

## 6. Phạm vi biến và cách đặt tên

~~~java
for (int i = 0; i < 2; i++) {
    for (int j = 0; j < 3; j++) {
        System.out.println(i + j); // i và j đều thuộc phạm vi.
    }
    // j không còn trong phạm vi ở đây.
}
// i cũng không còn trong phạm vi ở đây.
~~~

Biến khai báo ở phần khởi tạo của `for` chỉ tồn tại trong phạm vi phù hợp với `for` đó. **Vòng trong thấy `i`**, nhưng mã ở ngoài vòng trong không thấy `j` nếu `j` khai báo ngay trong initializer của vòng trong.

Đừng khai báo thêm một biến cục bộ cùng tên `i` ở vòng trong khi `i` của vòng ngoài vẫn trong phạm vi:

~~~java
// Không biên dịch do trùng tên biến cục bộ trong phạm vi lồng nhau:
// for (int i = 0; i < 2; i++) {
//     for (int i = 0; i < 3; i++) { }
// }
~~~

Tên `row`/`column`, `outer`/`inner` hoặc `studentIndex`/`subjectIndex` thường rõ hơn `i`/`j` trong code nghiệp vụ.

## 7. Duyệt mảng hai chiều: dùng đúng độ dài từng hàng

Mảng 2 chiều trong Java là **mảng các mảng**, nên mỗi hàng có thể có độ dài khác nhau (*jagged array*):

~~~java
int[][] matrix = {
    {1, 2},
    {3, 4, 5}
};

int sum = 0;
for (int row = 0; row < matrix.length; row++) {
    for (int col = 0; col < matrix[row].length; col++) {
        System.out.println(row + "," + col + "=" + matrix[row][col]);
        sum += matrix[row][col];
    }
}
System.out.println("sum=" + sum);
~~~

**Exact expected stdout:**

~~~text
0,0=1
0,1=2
1,0=3
1,1=4
1,2=5
sum=15
~~~

**Không dùng** `matrix[0].length` làm cận cột chung khi các hàng có thể không đều:

~~~java
// Dễ sai với mảng không chữ nhật:
// for (int col = 0; col < matrix[0].length; col++) { ... }
~~~

Hãy kiểm tra `row < matrix.length` và `col < matrix[row].length`. Nếu một hàng có thể là `null` trong dữ liệu thực tế, phải kiểm tra null trước khi gọi `.length`.

## 8. `break` và `continue` không nhãn trong vòng lặp lồng nhau

Không có nhãn, `break;` kết thúc **vòng lặp gần nhất**; `continue;` bỏ phần body còn lại của **vòng lặp gần nhất** và chuyển sang bước cập nhật/điều kiện lượt tiếp theo.

**Continue vòng trong:**

~~~java
for (int i = 0; i < 2; i++) {
    for (int j = 0; j < 3; j++) {
        if (j == 1) continue;
        System.out.println(i + "," + j);
    }
}
~~~

**Exact expected stdout:**

~~~text
0,0
0,2
1,0
1,2
~~~

Nhánh `j == 1` bị bỏ qua, nhưng **vòng trong vẫn cập nhật `j++`**, nên `j == 2` vẫn được xét.

**Break vòng trong:**

~~~java
for (int i = 1; i <= 2; i++) {
    for (int j = 1; j <= 3; j++) {
        if (j == 2) break;
        System.out.println("inner " + i + "," + j);
    }
    System.out.println("after inner " + i);
}
~~~

**Exact expected stdout:**

~~~text
inner 1,1
after inner 1
inner 2,1
after inner 2
~~~

`break` ở `j == 2` **không kết thúc vòng ngoài**; do đó vẫn có lượt `i == 2`. Đặt `break` sai mức có thể khiến chương trình chạy nhiều lượt hơn dự kiến.

## 9. `break label` và `continue label` để tác động lên vòng ngoài

Java cho phép đặt **nhãn** ngay trước vòng lặp:

~~~java
outer:
for (int i = 1; i <= 3; i++) {
    for (int j = 1; j <= 3; j++) {
        if (i == 2 && j == 2) {
            break outer; // Kết thúc cả vòng ngoài.
        }
        System.out.println("seen " + i + "," + j);
    }
}
~~~

**Exact expected stdout:**

~~~text
seen 1,1
seen 1,2
seen 1,3
seen 2,1
~~~

`break outer;` thoát cấu trúc mang nhãn `outer`, không tiếp tục các lượt `i` sau đó.

Ngược lại, `continue outer;` bỏ phần còn lại của lượt vòng **ngoài** hiện tại rồi chuyển sang bước cập nhật `i++`:

~~~java
outer:
for (int i = 1; i <= 3; i++) {
    for (int j = 1; j <= 3; j++) {
        if (j == 2) {
            System.out.println("skip row " + i);
            continue outer;
        }
        System.out.println("check " + i + "," + j);
    }
}
~~~

**Exact expected stdout:**

~~~text
check 1,1
skip row 1
check 2,1
skip row 2
check 3,1
skip row 3
~~~

**Phân biệt:**

| Lệnh ở thân vòng trong | Ảnh hưởng |
|---|---|
| `break;` | Thoát vòng trong, quay lại thân vòng ngoài sau vòng trong |
| `continue;` | Bỏ phần còn lại của lượt vòng trong, chuyển sang cập nhật `j` |
| `break outer;` | Thoát hẳn vòng ngoài có nhãn |
| `continue outer;` | Bỏ phần còn lại của lượt vòng ngoài, chuyển sang cập nhật `i` |

Sử dụng nhãn vừa đủ; với logic nhiều tầng phức tạp, tách thành method trả `boolean` hoặc kết quả rõ ràng có thể dễ bảo trì hơn.

## 10. Lỗi thường gặp khi theo dõi hai biến đếm

| Lỗi | Hệ quả | Cách sửa |
|---|---|---|
| Nghĩ `j` chỉ khởi tạo một lần cho toàn chương trình | Sai số lượt và kết quả | Nhớ `for (int j = ...)` được tới lại ở mỗi lượt ngoài |
| Dùng `j < matrix.length` thay vì độ dài hàng | Bỏ sót hoặc vượt cột | `j < matrix[i].length` |
| Dùng `i <= length` khi truy cập mảng | Off-by-one, có thể lỗi chỉ số | `i < length` |
| Tăng `i` trong update của vòng trong khi định tăng `j` | Sai tiến trình, có thể lặp vô hạn | Dùng `j++` trong vòng trong |
| Viết `j <= i` nhưng bắt đầu `j = 0` khi chỉ muốn 1..i | Có thêm một lượt `j == 0` | Đồng bộ cận và giá trị khởi tạo |
| Cho rằng `break;` bên trong thoát cả hai vòng | Vòng ngoài vẫn chạy | Dùng `break outer;` hoặc thiết kế lại |
| Cho rằng `continue;` ở vòng trong bỏ cả hàng | Chỉ bỏ một lượt `j` | `continue outer;` nếu muốn bỏ cả lượt ngoài |
| Dùng cùng tên biến cục bộ ở phạm vi lồng nhau | Lỗi compile | Chọn tên riêng `i`/`j` hoặc `row`/`col` |
| Chỉ in kết quả cuối mà không log `i,j` khi debug | Khó tìm chỗ sai | Tạm in từng cặp và bước cập nhật |
| Luôn cho rằng số lượt = `n*m` | Sai khi cận `j` phụ thuộc `i` | Tính tổng lượt theo từng `i` |
| Bỏ sót hàng mảng rỗng hoặc null | Lỗi logic hoặc NPE | Xét `matrix[row].length == 0`, kiểm tra null nếu dữ liệu cho phép |
| Dùng vòng lồng `O(n²)` khi dữ liệu rất lớn | Có thể tốn thời gian | Xem lại thuật toán/độ phức tạp thực tế |

## 11. Độ phức tạp: tính theo tổng số lượt của vòng trong

Nếu vòng ngoài có `n` lượt, vòng trong có đúng `m` lượt **ở mỗi lượt ngoài**, tổng body là `n × m`, chi phí `O(nm)` nếu mỗi body mất thời gian hằng số.

Nếu vòng ngoài `i = 1..n`, vòng trong `j = 1..i`, tổng body là:

\[
1 + 2 + \cdots + n = \frac{n(n+1)}{2}
\]

Vì vậy chi phí tăng theo `O(n²)`. Nếu vòng trong kết thúc sớm do `break`, số lượt thực tế có thể thấp hơn. Nếu thân trong gọi một thao tác tốn `O(k)` thì cần nhân thêm chi phí đó khi phân tích.

Đừng chỉ đếm **số câu lệnh `for` xuất hiện** để kết luận độ phức tạp; cận thực tế và điều kiện dừng mới là yếu tố quyết định.

## 12. Chương trình Java 17 hoàn chỉnh, chạy bằng CLI

Lưu đúng tên **`NestedLoopCountersDemo.java`**:

~~~java
public final class NestedLoopCountersDemo {
    private NestedLoopCountersDemo() {}

    static void rectangle() {
        int pairs = 0;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 3; j++) {
                System.out.println("pair " + i + "," + j);
                pairs++;
            }
            System.out.println("after outer " + i);
        }
        System.out.println("pairs=" + pairs);
    }

    static void triangle() {
        int count = 0;
        for (int i = 1; i <= 3; i++) {
            for (int j = 1; j <= i; j++) {
                System.out.println("triangle " + i + "," + j);
                count++;
            }
        }
        System.out.println("triangleCount=" + count);
    }

    static void jaggedArray() {
        int[][] matrix = {{1, 2}, {3, 4, 5}};
        int sum = 0;
        for (int row = 0; row < matrix.length; row++) {
            for (int col = 0; col < matrix[row].length; col++) {
                int value = matrix[row][col];
                System.out.println("cell " + row + "," + col + "=" + value);
                sum += value;
            }
        }
        System.out.println("sum=" + sum);
    }

    static void innerContinueAndBreak() {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 3; j++) {
                if (j == 1) continue;
                System.out.println("visit " + i + "," + j);
            }
        }
        System.out.println("finish after continue");
        for (int i = 1; i <= 2; i++) {
            for (int j = 1; j <= 3; j++) {
                if (j == 2) break;
                System.out.println("inner " + i + "," + j);
            }
            System.out.println("after inner " + i);
        }
    }

    static void labeledBreak() {
        outer:
        for (int i = 1; i <= 3; i++) {
            for (int j = 1; j <= 3; j++) {
                if (i == 2 && j == 2) {
                    System.out.println("stop at 2,2");
                    break outer;
                }
                System.out.println("seen " + i + "," + j);
            }
        }
        System.out.println("after labeled break");
    }

    static void labeledContinue() {
        outer:
        for (int i = 1; i <= 3; i++) {
            for (int j = 1; j <= 3; j++) {
                if (j == 2) {
                    System.out.println("skip row " + i);
                    continue outer;
                }
                System.out.println("check " + i + "," + j);
            }
        }
        System.out.println("after labeled continue");
    }

    public static void main(String[] args) {
        System.out.println("== rectangle ==");
        rectangle();
        System.out.println("== triangle ==");
        triangle();
        System.out.println("== jagged ==");
        jaggedArray();
        System.out.println("== continue and break ==");
        innerContinueAndBreak();
        System.out.println("== labels ==");
        labeledBreak();
        labeledContinue();
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên, terminal tại thư mục chứa file. Không dùng Maven, Gradle, preview feature hay thư viện ngoài JDK.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 NestedLoopCountersDemo.java
java -cp . NestedLoopCountersDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\NestedLoopCountersDemo.java
java -cp . NestedLoopCountersDemo
~~~

### Exact expected stdout

~~~text
== rectangle ==
pair 0,0
pair 0,1
pair 0,2
after outer 0
pair 1,0
pair 1,1
pair 1,2
after outer 1
pairs=6
== triangle ==
triangle 1,1
triangle 2,1
triangle 2,2
triangle 3,1
triangle 3,2
triangle 3,3
triangleCount=6
== jagged ==
cell 0,0=1
cell 0,1=2
cell 1,0=3
cell 1,1=4
cell 1,2=5
sum=15
== continue and break ==
visit 0,0
visit 0,2
visit 1,0
visit 1,2
finish after continue
inner 1,1
after inner 1
inner 2,1
after inner 2
== labels ==
seen 1,1
seen 1,2
seen 1,3
seen 2,1
stop at 2,2
after labeled break
check 1,1
skip row 1
check 2,1
skip row 2
check 3,1
skip row 3
after labeled continue
~~~

**Đối chiếu:**

1. `rectangle()` tạo 6 cặp `i,j`; `j` trở về 0 khi `i` chuyển từ 0 sang 1.
2. `triangle()` có số lượt 1 + 2 + 3, không đều giữa các lượt `i`.
3. `jaggedArray()` đi qua hai hàng khác độ dài, tổng 1+2+3+4+5 = 15.
4. `continue` bỏ lượt `j == 1`; `break` chỉ thoát vòng trong khi `j == 2`.
5. `break outer` làm toàn bộ vòng ngoài kết thúc ở `i=2,j=2`.
6. `continue outer` chuyển ngay sang lượt ngoài mới khi `j == 2`; `j == 3` không được xét.

## 13. Bài tập có đáp án

### Bài A — đếm số lần in

~~~java
for (int i = 0; i < 3; i++) {
    for (int j = 0; j < 2; j++) {
        System.out.println(i + ":" + j);
    }
}
~~~

**Đáp án:** 6 dòng, theo thứ tự:

~~~text
0:0
0:1
1:0
1:1
2:0
2:1
~~~

### Bài B — cận vòng trong phụ thuộc vòng ngoài

~~~java
int count = 0;
for (int i = 1; i <= 4; i++) {
    for (int j = 1; j <= i; j++) count++;
}
System.out.println(count);
~~~

**Đáp án:** `10`, do 1+2+3+4 = 10.

### Bài C — sửa duyệt mảng lệch biên

~~~java
int[][] matrix = {{5}, {6, 7}};
// Sai: dùng matrix[0].length cho tất cả các hàng.
~~~

**Đáp án:**

~~~java
for (int i = 0; i < matrix.length; i++) {
    for (int j = 0; j < matrix[i].length; j++) {
        System.out.println(matrix[i][j]);
    }
}
~~~

**Exact stdout:** ba dòng `5`, `6`, `7`.

### Bài D — `break` thoát vòng nào?

~~~java
for (int i = 0; i < 2; i++) {
    for (int j = 0; j < 3; j++) {
        if (j == 1) break;
        System.out.println(i + "," + j);
    }
}
~~~

**Đáp án:**

~~~text
0,0
1,0
~~~

`break` thoát vòng trong tại `j == 1`, nhưng vòng ngoài vẫn chuyển từ 0 sang 1.

### Bài E — dừng cả hai vòng

Muốn thoát cả hai vòng khi thấy `matrix[i][j] == target`, có thể đặt nhãn:

~~~java
search:
for (int i = 0; i < matrix.length; i++) {
    for (int j = 0; j < matrix[i].length; j++) {
        if (matrix[i][j] == target) {
            break search;
        }
    }
}
~~~

**Đáp án:** `break search` thoát vòng ngoài có nhãn và cả các vòng nằm trong nó. Trong phương thức tìm kiếm, một cách khác là `return` ngay vị trí tìm thấy.

## 14. Checklist và kết luận

- [ ] Phân biệt biến đếm vòng ngoài `i` và vòng trong `j`.
- [ ] Biết initializer của vòng trong chạy lại **mỗi lượt vòng ngoài**.
- [ ] Lập được bảng từng cặp `i,j` và bước điều kiện false.
- [ ] Phân biệt hình chữ nhật `n×m` với vòng tam giác `1+...+n`.
- [ ] Giữ việc cập nhật `i` ở vòng ngoài, `j` ở vòng trong nếu không có yêu cầu đặc biệt.
- [ ] Hiểu phạm vi biến `i`/`j` và tránh khai báo trùng tên.
- [ ] Duyệt mảng 2D bằng `matrix.length` và `matrix[row].length`.
- [ ] Hiểu `break;`/`continue;` không nhãn chỉ tác động lên vòng gần nhất.
- [ ] Phân biệt `break outer;` và `continue outer;`.
- [ ] Biết tính số lượt/độ phức tạp dựa trên cận thực tế của vòng trong.
- [ ] Kiểm tra giá trị tại biên, hàng rỗng và điều kiện dừng.
- [ ] Chạy `NestedLoopCountersDemo.java` với JDK 17+, so sánh exact stdout.

**Kết luận:** Để hiểu vòng lặp lồng nhau, hãy luôn truy vết theo **từng cặp giá trị `i,j`**: giữ `i` ở một lượt ngoài, cho `j` chạy tới khi dừng, rồi cập nhật `i` và khởi tạo lại `j`. Khi cận vòng trong phụ thuộc `i`, số lượt là một **tổng theo từng vòng ngoài**, không còn đơn thuần là tích. Biết rõ mức tác động của `break`/`continue` sẽ giúp tránh lỗi luồng điều khiển.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="loops-007">Lỗi lệch một đơn vị tại biên vòng lặp</a>

<details>
<summary>Xem chi tiết</summary>

# Lỗi lệch một đơn vị tại biên vòng lặp — Java 17+

**Lỗi lệch một đơn vị** (*off-by-one error*) xảy ra khi vòng lặp chạy **thiếu một lượt** hoặc **thừa một lượt** so với yêu cầu. Lỗi thường nằm ở giá trị bắt đầu, điều kiện `<`/`<=`, bước cập nhật hoặc cách hiểu giới hạn cuối có được tính vào hay không.

~~~java
int[] numbers = {10, 20, 30};

for (int i = 0; i < numbers.length; i++) {
    System.out.println(numbers[i]); // 10, 20, 30.
}
~~~

Trong Java, mảng dài `n` có chỉ số hợp lệ từ **`0` đến `n - 1`**. Giá trị `n` là **biên kết thúc không bao gồm**; không phải chỉ số phần tử cuối.

## 1. Phân biệt biên đóng và biên mở

| Mục tiêu | Cách viết đúng | Số lượt khi miền hợp lệ |
| --- | --- | --- |
| Duyệt mảng độ dài `n` | `for (int i = 0; i < n; i++)` | `n` |
| Duyệt từ `0` đến `n` **bao gồm `n`** | `for (int i = 0; i <= n; i++)` | `n + 1` |
| Duyệt chỉ số `start` tới **trước `end`** | `for (int i = start; i < end; i++)` | `end - start` nếu `start <= end` |
| Duyệt từ `start` tới `end` **bao gồm `end`** | `for (int i = start; i <= end; i++)` | `end - start + 1` nếu `start <= end` |
| Duyệt mảng theo chiều ngược | `for (int i = a.length - 1; i >= 0; i--)` | `a.length` |

Ví dụ bắt đầu ở 1, kết thúc ở 3:

~~~text
[1, 3)  → 1, 2       (2 giá trị, không bao gồm 3)
[1, 3]  → 1, 2, 3    (3 giá trị, bao gồm 3)
~~~

Ký hiệu `[start, end)` là **đoạn nửa mở**, rất hữu ích khi xử lý chỉ số mảng. `[start, end]` là đoạn đóng; hãy ghi rõ kiểu biên trong tên biến hoặc hợp đồng phương thức.

**Cần chú ý:** các công thức số lượt trên giả định bước tăng là 1, thứ tự biên hợp lệ và không có `break`/`return`. Nếu có bước tăng khác, giảm dần hoặc kiểu số nguyên bị tràn, phải phân tích lại.

## 2. Chương trình Java 17+ tự chứa và chạy được

Tạo tệp UTF-8 **`OffByOneDemo.java`**:

~~~java
public class OffByOneDemo {
    static int sumFirst(int[] values, int count) {
        if (count < 0 || count > values.length) {
            throw new IllegalArgumentException("invalid count");
        }
        int sum = 0;
        for (int i = 0; i < count; i++) {
            sum += values[i];
        }
        return sum;
    }

    static int sumRange(int[] values, int start, int endExclusive) {
        if (start < 0 || endExclusive < start
                || endExclusive > values.length) {
            throw new IllegalArgumentException("invalid range");
        }
        int sum = 0;
        for (int i = start; i < endExclusive; i++) {
            sum += values[i];
        }
        return sum;
    }

    public static void main(String[] args) {
        int[] values = {10, 20, 30};
        System.out.println("== array indices ==");
        System.out.println("length=" + values.length);
        for (int i = 0; i < values.length; i++) {
            System.out.println("index=" + i + ",value=" + values[i]);
        }

        System.out.println("== inclusive vs exclusive ==");
        for (int i = 1; i < 3; i++) {
            System.out.println("exclusive=" + i);
        }
        for (int i = 1; i <= 3; i++) {
            System.out.println("inclusive=" + i);
        }

        System.out.println("== prefix and range ==");
        System.out.println("first0=" + sumFirst(values, 0));
        System.out.println("first2=" + sumFirst(values, 2));
        System.out.println("first3=" + sumFirst(values, 3));
        System.out.println("range1to3=" + sumRange(values, 1, 3));
        System.out.println("emptyRange=" + sumRange(values, 2, 2));

        System.out.println("== reverse and empty array ==");
        for (int i = values.length - 1; i >= 0; i--) {
            System.out.println("reverse=" + values[i]);
        }
        int[] empty = {};
        int visits = 0;
        for (int i = 0; i < empty.length; i++) {
            visits++;
        }
        System.out.println("emptyVisits=" + visits);

        System.out.println("== one past the end ==");
        int index = 0;
        while (index < values.length) {
            index++;
        }
        System.out.println("indexAfterWhile=" + index);
        try {
            System.out.println("unexpected=" + values[values.length]);
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.out.println("atLength=ArrayIndexOutOfBoundsException");
        }
        try {
            sumFirst(values, 4);
        } catch (IllegalArgumentException ex) {
            System.out.println("count4=" + ex.getMessage());
        }

        System.out.println("== substring uses exclusive end ==");
        String text = "ABCDE";
        System.out.println("slice=" + text.substring(1, 4));
        System.out.println("done");
    }
}
~~~

Tại thư mục chứa file, dùng **JDK 17 trở lên** với PowerShell hoặc Bash:

~~~bash
java -version
javac -version
javac -encoding UTF-8 OffByOneDemo.java
java OffByOneDemo
~~~

**Expected stdout chính xác** của riêng lệnh `java OffByOneDemo`:

~~~text
== array indices ==
length=3
index=0,value=10
index=1,value=20
index=2,value=30
== inclusive vs exclusive ==
exclusive=1
exclusive=2
inclusive=1
inclusive=2
inclusive=3
== prefix and range ==
first0=0
first2=30
first3=60
range1to3=50
emptyRange=0
== reverse and empty array ==
reverse=30
reverse=20
reverse=10
emptyVisits=0
== one past the end ==
indexAfterWhile=3
atLength=ArrayIndexOutOfBoundsException
count4=invalid count
== substring uses exclusive end ==
slice=BCD
done
~~~

Kết quả của `java -version`/`javac -version` phụ thuộc JDK cài đặt, không nằm trong stdout cố định. Mã sử dụng API chuẩn, không yêu cầu thư viện bên ngoài.

## 3. Vì sao `i <= array.length` thường là sai?

~~~java
int[] values = {10, 20, 30};

// SAI nếu truy cập values[i]:
// for (int i = 0; i <= values.length; i++) {
//     System.out.println(values[i]);
// }
~~~

Vòng `i <= values.length` chạy khi `i == 3`, nhưng với mảng dài 3, chỉ các chỉ số 0, 1 và 2 là hợp lệ. Truy cập `values[3]` ném `ArrayIndexOutOfBoundsException`.

Nếu yêu cầu là **duyệt phần tử**, dùng `i < values.length`. Nếu yêu cầu là **duyệt các vị trí ngăn cách giữa phần tử**, có thể cần `i <= values.length`, bởi có `n + 1` vị trí biên, nhưng lúc ấy không được đọc `values[i]` ở vị trí `i == n`.

Nói cách khác, **`<=` không tự động sai**; nó chỉ sai khi không phù hợp với miền giá trị và thao tác trong thân.

## 4. Đếm số lượt khác với truy cập chỉ số

Hai nhiệm vụ có thể dùng điều kiện khác nhau:

~~~java
// Thực hiện hành động đúng 3 lần:
for (int turn = 1; turn <= 3; turn++) {
    System.out.println("turn=" + turn);
}

// Duyệt ba phần tử mảng:
for (int i = 0; i < 3; i++) {
    System.out.println(values[i]);
}
~~~

Cả hai đều chạy ba lượt, nhưng một biến là **số thứ tự lượt** 1..3, một biến là **chỉ số** 0..2. Trộn hai quy ước dễ gây lệch một.

Đối với `count` phần tử đầu của mảng, số phần tử cần xử lý chính là `count`; viết `i < count`. Với `count == 0`, thân phải chạy 0 lần. Nếu viết `i <= count`, ta sẽ xử lý **một phần tử** ngay cả khi yêu cầu là 0 phần tử.

### Đoạn nửa mở giúp ghép các đoạn liền nhau

Ví dụ muốn xử lý các chỉ số thuộc hai đoạn:

~~~text
[0, 2) = 0, 1
[2, 5) = 2, 3, 4
Ghép lại: [0, 5) = 0, 1, 2, 3, 4
~~~

Hai đoạn không chồng nhau và không có lỗ hổng. Với `start == end`, đoạn `[start, end)` **rỗng**, nên lặp 0 lượt là hành vi đúng. API Java `String.substring(beginIndex, endIndex)` cũng sử dụng **biên cuối loại trừ**.

## 5. Duyệt ngược và mảng rỗng

Mảng `n` phần tử có chỉ số cuối hợp lệ là `n - 1`, nên:

~~~java
for (int i = values.length - 1; i >= 0; i--) {
    System.out.println(values[i]);
}
~~~

Nếu `values.length == 0`, giá trị đầu `i == -1` và điều kiện `i >= 0` sai ngay; không đọc phần tử nào. Đây là lý do cách viết này xử lý mảng rỗng đúng.

Tránh mở đầu ở `i = values.length` nếu trong thân sẽ đọc `values[i]`: chỉ số ấy **vượt biên phải một đơn vị**.

Cần đặc biệt cẩn thận với điều kiện như `i > 0`: khi duyệt về 0, nó **bỏ sót phần tử chỉ số 0**. Dùng `i >= 0` nếu muốn bao gồm phần tử đầu.

### Cẩn thận khi đếm kiểu không thích hợp

Java dùng `int` cho chỉ số mảng. Không chuyển biến đếm sang kiểu không hỗ trợ giá trị âm rồi giả định `i >= 0` sẽ kết thúc giống cách dùng `int`. Trong Java, `char` là kiểu không dấu 16 bit; sau decrement và chuyển kiểu, nó có thể quay vòng, nên không phù hợp cho kiểu duyệt ngược dùng điều kiện `>= 0`.

## 6. Kiểm thử các điểm sát biên

Lỗi off-by-one thường lộ ra tại **giá trị sát biên**, không nhất thiết ở đầu vào thông thường.

| Trường hợp kiểm thử | Điều mong đợi |
| --- | --- |
| Mảng rỗng (`length == 0`) | Không truy cập phần tử |
| Mảng 1 phần tử | Đúng 1 lần duyệt; không vượt chỉ số 0 |
| Mảng 3 phần tử | Các chỉ số `0, 1, 2`; không truy cập 3 |
| `count = 0` | Xử lý 0 phần tử |
| `count = length` | Xử lý toàn bộ phần tử, không thừa |
| `count = length + 1` | Bị từ chối nếu tham số là số phần tử cần xử lý |
| `start == endExclusive` | Đoạn rỗng, tổng ban đầu không đổi |
| Duyệt ngược | Đến và bao gồm chỉ số 0 |

Một cách kiểm thử hiệu quả là ghi trước **miền chỉ số hợp lệ**, rồi kiểm chứng ba giá trị: ngay trước biên, đúng tại biên và ngay sau biên. Khi viết vòng `for` hoặc `while`, luôn xác nhận cả **giá trị cuối của biến đếm** lẫn **giá trị cuối thực sự được xử lý trong thân**.

## 7. Lỗi thường gặp

| Nhầm lẫn | Cách đúng |
| --- | --- |
| “Mảng dài `n` có chỉ số cuối `n`.” | Chỉ số cuối là `n - 1` |
| “`i <= array.length` dùng để duyệt mọi phần tử.” | Thường thừa một lượt; dùng `i < array.length` |
| “`i < n` bắt đầu ở 0 thì chạy `n + 1` lần.” | Chạy `n` lần khi `n >= 0` và bước tăng 1 |
| “`for(i=n-1; i>0; i--)` duyệt mọi phần tử.” | Bỏ chỉ số 0 |
| “Đoạn `[start, end)` bao gồm `end`.” | `end` bị loại trừ |
| “Nếu mảng rỗng thì phải truy cập ít nhất một phần tử.” | Duyệt đúng là 0 lần |
| “Chỉ cần test mảng lớn là đủ.” | Các biên 0, 1 và đúng `length` rất quan trọng |
| “Có ngoại lệ thì sửa bằng cách bỏ đi `break` cuối.” | Phải sửa **miền chỉ số và điều kiện** |

## 8. Bài tập tự kiểm tra

1. Với `int[] a = new int[4]`, chỉ số hợp lệ là gì?  
   *Đáp án:* `0, 1, 2, 3`.
2. `for (int i = 0; i <= 4; i++)` chạy bao nhiêu lượt?  
   *Đáp án:* `5` lượt; nếu đọc `a[i]` ở lượt cuối là vượt biên.
3. Đoạn `[2, 6)` chứa mấy số nguyên liên tiếp?  
   *Đáp án:* 4 số (`2, 3, 4, 5`).
4. Duyệt ngược `for (int i = 4; i > 0; i--)` có xử lý `i == 0` không?  
   *Đáp án:* Không.
5. `String "ABCDE".substring(1, 4)` cho gì?  
   *Đáp án:* `"BCD"`.
6. Nếu `count = 0`, vòng `for (int i = 0; i < count; i++)` chạy mấy lần?  
   *Đáp án:* 0 lần.

## Checklist

- [ ] Phân biệt rõ **chỉ số phần tử** với **số lượt**.
- [ ] Biết `n` phần tử có chỉ số `0..n-1`.
- [ ] Dùng đúng biên cuối **loại trừ** với `<` và **bao gồm** với `<=`.
- [ ] Kiểm thử mảng rỗng, 1 phần tử và giá trị đúng sát biên.
- [ ] Duyệt ngược có xét đến chỉ số 0.
- [ ] Ghi rõ quy ước `[start, end)` và kiểm tra `start <= end <= length`.
- [ ] Đảm bảo các biến đếm tiến triển và tránh tràn số nếu biên rất lớn.

**Kết luận:** Off-by-one không chỉ là nhầm dấu `<` thành `<=`, mà là nhầm **ý nghĩa của biên**. Trước khi viết vòng lặp, xác định giá trị đầu được xử lý, giá trị cuối được xử lý, số lượt mong muốn và liệu biên kết thúc có được bao gồm hay không.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="loops-008">Mẫu tích lũy tổng, đếm và tìm giá trị lớn nhất</a>

<details>
<summary>Xem chi tiết</summary>

# Mẫu tích lũy tổng, đếm và tìm giá trị lớn nhất trong Java

> **Java core — language-basics / Vòng lặp**  
> **Môi trường:** JDK 17+, chương trình một file, không dùng thư viện bên ngoài.  
> **Mục tiêu:** Dùng một vòng lặp để tính tổng, đếm phần tử/đếm theo điều kiện và tìm giá trị lớn nhất; chọn giá trị khởi tạo chính xác, xử lý mảng rỗng và tránh tràn tổng kiểu `int`.

## 1. Mẫu tích lũy (*accumulator*) là gì?

Khi duyệt một dãy số, ta có thể giữ các biến **trạng thái tóm tắt**. Mỗi lượt xử lý một phần tử và cập nhật trạng thái:

~~~java
int[] numbers = {3, -2, 7, 0, -5};
long sum = 0;
int count = 0;

for (int number : numbers) {
    sum += number;
    count++;
}

System.out.println(sum);   // 3
System.out.println(count); // 5
~~~

- **Tổng**: khởi tạo `sum = 0`, cập nhật bằng `sum += number`.
- **Đếm**: khởi tạo `count = 0`, tăng đúng một lần cho mỗi phần tử được tính.
- **Giá trị lớn nhất**: sau khi gặp phần tử đầu tiên, giữ `max` bằng phần tử lớn nhất đã thấy; mỗi lượt so sánh rồi cập nhật nếu cần.

Cả ba phép tính có thể được thực hiện trong **cùng một lần duyệt**, không nhất thiết tạo ba vòng lặp. Với `n` phần tử, thời gian là **O(n)** và bộ nhớ phụ cho các bộ tích lũy là **O(1)**.

## 2. Khởi tạo và cập nhật: tổng, đếm, đếm có điều kiện

Tổng của dãy rỗng được định nghĩa là `0`. Đếm phần tử của dãy rỗng cũng là `0`. Ta có thể đếm thêm các phần tử thỏa điều kiện:

~~~java
int[] numbers = {3, -2, 7, 0, -5};
long sum = 0;
int count = 0;
int positiveCount = 0;

for (int number : numbers) {
    sum += number;
    count++;

    if (number > 0) {
        positiveCount++;
    }
}
// sum = 3; count = 5; positiveCount = 2.
~~~

Ở đây `0` **không** được tính là số dương vì điều kiện là `number > 0`. Nếu yêu cầu đếm số không âm, thay bằng `number >= 0` và cập nhật cả bảng test.

Không nhầm `count` (tổng số phần tử) với `positiveCount` (số phần tử thỏa điều kiện). Nếu chỉ cộng số dương, điều kiện lọc phải bao quanh **cả phép cộng lẫn phép đếm cùng miền**, thay vì dùng tổng tất cả chia cho số lượng số dương.

## 3. Tìm `max` đúng cả khi toàn số âm hoặc mảng rỗng

Một lỗi phổ biến là khởi tạo `max = 0` rồi chỉ cập nhật nếu phần tử lớn hơn:

~~~java
// SAI với dãy {-8, -3, -12}:
// int max = 0;
// for (int x : numbers) {
//     if (x > max) max = x;
// }
// Kết quả sai là 0; lớn nhất thực sự là -3.
~~~

Có hai cách an toàn:

1. Nếu biết mảng **không rỗng**, đặt `max = numbers[0]` và bắt đầu duyệt từ phần tử thứ hai.
2. Nếu mảng **có thể rỗng**, dùng cờ `hasAny` để ghi nhận đã thấy phần tử nào, rồi xuất kết quả tùy chọn (`OptionalInt`).

~~~java
int max = 0;
boolean hasAny = false;

for (int number : numbers) {
    if (!hasAny || number > max) {
        max = number;
        hasAny = true;
    }
}

java.util.OptionalInt maximum =
        hasAny ? java.util.OptionalInt.of(max)
               : java.util.OptionalInt.empty();
~~~

Trong cách thứ hai, giá trị ban đầu `max = 0` **không được coi là kết quả** khi chưa gặp phần tử: lượt đầu sẽ gán `max = number` bất kể âm hay dương. Với mảng rỗng, `maximum.isPresent()` là false. Không tự kết luận max của mảng rỗng bằng 0 vì 0 không phải phần tử của mảng.

## 4. Bảng diễn tiến và bất biến vòng lặp

Với dãy `{3, -2, 7, 0, -5}`, sau mỗi lượt, trạng thái biến đổi như sau:

| Phần tử vừa xử lý | `sum` | `count` | `positiveCount` | `max` |
| ---: | ---: | ---: | ---: | ---: |
| Chưa xử lý | 0 | 0 | 0 | Chưa có |
| 3 | 3 | 1 | 1 | 3 |
| -2 | 1 | 2 | 1 | 3 |
| 7 | 8 | 3 | 2 | 7 |
| 0 | 8 | 4 | 2 | 7 |
| -5 | 3 | 5 | 2 | 7 |

**Bất biến vòng lặp** sau `k` phần tử là: `sum` bằng tổng `k` phần tử đầu; `count = k`; `positiveCount` bằng số phần tử dương trong `k` phần tử; nếu `k > 0` thì `max` bằng phần tử lớn nhất trong đoạn đã duyệt.

Lập luận: trạng thái ban đầu đúng với `k = 0`; mỗi lượt cập nhật tổng, các bộ đếm và max theo phần tử mới nên vẫn đúng với `k + 1`. Khi duyệt hết `n` phần tử, các biến mô tả chính xác toàn bộ dãy.

Việc viết bất biến giúp phát hiện lỗi đặt `count++` sai chỗ, bỏ qua phần tử đầu, hoặc cập nhật `max` bằng điều kiện ngược `number < max`.

## 5. Trung bình, kiểu dữ liệu và tràn số

Sau khi có tổng và số lượng, có thể tính trung bình:

~~~java
double average = count == 0
        ? Double.NaN
        : (double) sum / count;
~~~

Phải kiểm tra `count == 0` vì trung bình của dãy rỗng **không xác định**. Trong ví dụ runnable bên dưới, ta in `"N/A"` thay cho trung bình rỗng. Ép kiểu `(double) sum` **trước phép chia** để tránh phép chia nguyên làm mất phần thập phân.

**Dùng `long` cho tổng số nguyên `int`:**

~~~java
int large = Integer.MAX_VALUE;
int wrong = large + large;       // Tràn int: -2.
long correct = (long) large + large; // 4_294_967_294.
~~~

Nếu dùng `long sum = 0; sum += number;` với từng `int` thì phép cộng được thực hiện dưới kiểu `long`, tránh tràn `int` trước khi cộng. Nhưng `long` **không bảo đảm vô hạn**: nếu cộng rất nhiều số trong luồng dữ liệu lớn, vẫn có thể tràn `long`; khi cần phát hiện tràn có thể dùng `Math.addExact` hoặc `BigInteger` tùy bài toán.

Khi mảng chứa số âm, trung bình vẫn có thể âm. Không lấy số lượng số dương làm mẫu số nếu tổng đang tính trên **toàn bộ** phần tử.

## 6. Chọn vòng lặp và kiểm tra trường hợp biên

| Trường hợp | Kết quả cần kiểm tra |
| --- | --- |
| Mảng rỗng `{}` | Tổng 0, đếm 0, không có max, trung bình `N/A` |
| Chỉ số âm `{-8, -3, -12}` | Max `-3`, không phải 0 |
| Có số 0 | 0 được đếm là phần tử, nhưng không phải số dương |
| Một phần tử | Tổng và max bằng chính phần tử; đếm 1 |
| Hai `Integer.MAX_VALUE` | Tổng `4_294_967_294` bằng `long` |
| Mảng null | Không thể duyệt bằng for-each; phải kiểm tra null theo hợp đồng |

Dùng `for (int x : numbers)` khi chỉ cần **giá trị** mỗi phần tử. Dùng `for (int i = 0; i < numbers.length; i++)` nếu cần chỉ số hoặc bắt đầu từ vị trí 1 sau khi gán max bằng phần tử đầu.

Với đầu vào null, có thể chủ động báo lỗi bằng `IllegalArgumentException` thay vì để vòng lặp gây `NullPointerException` không rõ mục đích; ví dụ runnable lựa chọn báo lỗi tường minh.

## 7. Chương trình Java 17+ chạy độc lập

Tạo file **LoopAccumulatorDemo.java**:

~~~java
import java.util.Locale;
import java.util.OptionalInt;

public final class LoopAccumulatorDemo {
    private record Summary(
            long sum, int count, int positiveCount, OptionalInt max) {}

    private static Summary analyze(int[] values) {
        if (values == null) {
            throw new IllegalArgumentException("values must not be null");
        }

        long sum = 0L;
        int count = 0;
        int positiveCount = 0;
        int max = 0;
        boolean hasAny = false;

        for (int number : values) {
            sum += number;
            count++;

            if (number > 0) {
                positiveCount++;
            }
            if (!hasAny || number > max) {
                max = number;
                hasAny = true;
            }
        }

        return new Summary(sum, count, positiveCount,
                hasAny ? OptionalInt.of(max) : OptionalInt.empty());
    }

    private static void show(String label, int[] values) {
        Summary result = analyze(values);

        String maxText = result.max().isPresent()
                ? String.valueOf(result.max().getAsInt()) : "NONE";
        String averageText = result.count() == 0
                ? "N/A"
                : String.format(Locale.ROOT, "%.2f",
                        (double) result.sum() / result.count());

        System.out.println(label
                + ":sum=" + result.sum()
                + ",count=" + result.count()
                + ",positive=" + result.positiveCount()
                + ",max=" + maxText
                + ",average=" + averageText);
    }

    public static void main(String[] args) {
        show("mixed", new int[] {3, -2, 7, 0, -5});
        show("negative", new int[] {-8, -3, -12});
        show("empty", new int[] {});
        show("zeros", new int[] {0, 0});
        show("min", new int[] {Integer.MIN_VALUE});
        show("large", new int[] {
                Integer.MAX_VALUE, Integer.MAX_VALUE
        });

        try {
            analyze(null);
        } catch (IllegalArgumentException ex) {
            System.out.println("nullInput=IllegalArgumentException");
        }
    }
}
~~~

Biên dịch và chạy trong thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 LoopAccumulatorDemo.java
java LoopAccumulatorDemo
~~~

**Expected stdout chính xác:**

~~~text
mixed:sum=3,count=5,positive=2,max=7,average=0.60
negative:sum=-23,count=3,positive=0,max=-3,average=-7.67
empty:sum=0,count=0,positive=0,max=NONE,average=N/A
zeros:sum=0,count=2,positive=0,max=0,average=0.00
min:sum=-2147483648,count=1,positive=0,max=-2147483648,average=-2147483648.00
large:sum=4294967294,count=2,positive=2,max=2147483647,average=2147483647.00
nullInput=IllegalArgumentException
~~~

Bốn kết quả quan trọng: `negative` phát hiện khởi tạo max sai; `empty` minh họa không có giá trị lớn nhất; `large` chứng minh tổng vượt miền `int` vẫn được giữ trong `long`; và `mixed` thể hiện tổng, các bộ đếm và max được tính từ **cùng một lượt duyệt**.

## 8. Lỗi thường gặp và bài tập

| Lỗi | Hệ quả | Cách sửa |
| --- | --- | --- |
| Khởi tạo `max = 0` rồi dùng luôn làm giá trị lớn nhất | Sai với toàn số âm hoặc mảng rỗng | Khởi tạo từ phần tử đầu hợp lệ hoặc dùng `hasAny` |
| Dùng `int sum` cho tổng lớn | Có thể tràn số | Dùng `long` và tránh tràn trước khi ép kiểu |
| `count++` chỉ chạy khi số dương | Đếm sai tổng phần tử | Tách `count` và `positiveCount` |
| Tính trung bình `sum / count` bằng số nguyên | Mất phần lẻ, sai với dãy rỗng | Kiểm tra count, ép kiểu trước phép chia |
| Truy cập `numbers[0]` khi mảng rỗng | `ArrayIndexOutOfBoundsException` | Kiểm tra rỗng hoặc dùng cờ lần đầu |
| Nhầm `> 0` với `>= 0` | Đếm khác ở phần tử 0 | Bám đúng định nghĩa số dương/không âm |

**Bài 1.** Tự tính `sum`, `count`, `positiveCount`, `max` cho dãy `{4, -1, 0, 10}`, sau đó kiểm chứng bằng chương trình.

**Bài 2.** Thay điều kiện đếm số dương thành đếm số chẵn; chú ý cách xử lý số âm chẵn và số 0.

**Bài 3.** Sửa mẫu để chỉ lấy tổng và max của **các phần tử dương**. Nếu không có số dương thì trả kết quả gì để không nhầm với max=0?

**Bài 4.** Viết biến thể sử dụng `for (int i = 1; i < values.length; i++)` với `max = values[0]`, đồng thời xử lý đầu vào rỗng trước.

**Bài 5.** Dùng dãy `{Integer.MAX_VALUE, Integer.MAX_VALUE}` để so sánh tổng lưu vào `int` và `long`; giải thích tại sao chỉ ép kiểu **sau khi** đã cộng theo `int` là quá muộn.

### Ghi nhớ

**Tích lũy tổng bắt đầu từ 0, đếm bắt đầu từ 0, còn max cần phần tử đầu tiên thực sự tồn tại.** Mỗi lượt duyệt cập nhật đúng các bộ tích lũy và giữ bất biến. Với mảng rỗng, không có max; với toàn số âm, max vẫn âm. Sử dụng `long` cho tổng `int` có thể lớn và kiểm tra `count == 0` trước khi tính trung bình.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="loops-009">Duy trì điều kiện bất biến đơn giản qua mỗi lần lặp</a>

<details>
<summary>Xem chi tiết</summary>

# Duy trì điều kiện bất biến đơn giản qua mỗi lần lặp — Java 17+

## 1. Điều kiện bất biến vòng lặp là gì?

**Bất biến vòng lặp** (*loop invariant*) là một mệnh đề về trạng thái chương trình được giữ đúng **mỗi lần chuẩn bị kiểm tra điều kiện lặp**, kể cả lần kiểm tra cuối cùng khi vòng lặp kết thúc bình thường.

Ví dụ, khi tính tổng mảng từ trái sang phải:

~~~java
int[] numbers = {2, -1, 4, 3};
long sum = 0;

for (int i = 0; i < numbers.length; i++) {
    sum += numbers[i];
}

System.out.println(sum);
~~~

**Exact expected stdout:**

~~~text
8
~~~

Một bất biến hữu ích ở **đầu mỗi lượt** là:

> `sum` bằng tổng của **đúng `i` phần tử đầu tiên**: `numbers[0]` đến `numbers[i-1]`.

Với `i = 0`, chưa xử lý phần tử nào nên `sum = 0`. Với `i = 2`, đã xử lý `2` và `-1` nên `sum = 1`. Khi vòng lặp hoàn tất với `i = numbers.length`, bất biến cho biết `sum` là tổng của toàn mảng.

Bất biến không phải từ khóa hay cú pháp Java; đó là **khẳng định logic** do người viết code xác định, lý giải và có thể kiểm tra trong lúc debug/test.

## 2. Ba bước chứng minh một bất biến

| Bước | Câu hỏi cần trả lời | Ví dụ tính tổng tiền tố |
|---|---|---|
| **Khởi tạo** (*initialization*) | Mệnh đề có đúng trước lượt đầu? | `i=0`, `sum=0` là tổng tiền tố rỗng |
| **Bảo toàn** (*maintenance*) | Nếu đúng trước lượt này, chạy body và update xong có còn đúng? | Cộng `numbers[i]`, rồi `i++`; tổng ứng với tiền tố dài hơn một phần tử |
| **Kết thúc** (*termination*) | Khi điều kiện lặp sai, bất biến suy ra kết quả gì? | `i=n` nên `sum` là tổng của `n` phần tử |

~~~mermaid
flowchart TD
    A["Khởi tạo: bất biến đúng"] --> B{"Điều kiện lặp đúng?"}
    B -- "Đúng" --> C["Thực thi thân vòng lặp"]
    C --> D["Cập nhật biến đếm"]
    D --> E["Bất biến vẫn đúng"]
    E --> B
    B -- "Sai" --> F["Bất biến + điều kiện dừng"]
    F --> G["Suy ra hậu điều kiện/kết quả"]
~~~

**Phân biệt quan trọng:** Ba bước trên chủ yếu chứng minh **tính đúng từng phần**: nếu vòng lặp kết thúc, kết quả là đúng. Để chứng minh nó **chắc chắn kết thúc**, cần thêm lập luận về sự tiến triển, thường dùng một đại lượng giảm dần (*loop variant*).

## 3. Xác định thời điểm bất biến phải đúng

Xét:

~~~java
for (int i = 0; i < numbers.length; i++) {
    sum += numbers[i];
}
~~~

Bất biến được mô tả tại **đầu vòng lặp, trước kiểm tra điều kiện**. Sau dòng `sum += numbers[i]` nhưng **trước khi `i++` chạy**, biến `sum` đã là tổng `i+1` phần tử, còn `i` vẫn có giá trị cũ. Vì vậy mệnh đề “`sum` là tổng `i` phần tử” không nhất thiết đúng **tại mọi dòng trung gian**.

Sau update `i++`, đầu lượt kế tiếp ta lại có:

- `i` mới = `i` cũ + 1.
- `sum` = tổng của `i` mới phần tử đầu tiên.
- Bất biến được **khôi phục đúng tại điểm kiểm tra**.

Điểm này giúp tránh lỗi lập luận: bất biến phải đúng ở **các điểm kiểm tra đã xác định**, không cần bất biến đúng sau từng phép gán trong body.

## 4. Truy vết bất biến tổng tiền tố

Mảng `{2, -1, 4, 3}`, khởi tạo `sum = 0`:

| Đầu lượt / khi dừng | `i` | Tiền tố đã xử lý | `sum` |
|---|---:|---|---:|
| Trước lượt đầu | 0 | Rỗng | 0 |
| Trước lượt thứ 2 | 1 | `[2]` | 2 |
| Trước lượt thứ 3 | 2 | `[2, -1]` | 1 |
| Trước lượt thứ 4 | 3 | `[2, -1, 4]` | 5 |
| Khi `i < 4` sai | 4 | `[2, -1, 4, 3]` | 8 |

**Cách bảo toàn:**

Giả sử trước lượt có `0 <= i < n` và `sum = numbers[0] + ... + numbers[i-1]`.

1. Thân vòng lặp thực hiện `sum += numbers[i]`.
2. Update của `for` thực hiện `i++`.
3. Tại đầu lượt kế, `sum` là tổng phần tử có chỉ số từ 0 đến `i-1` theo `i` mới.

**Kết thúc:** điều kiện `i < n` sai và `i` được tăng từng đơn vị từ 0. Khi không có thay đổi bất thường hoặc overflow chỉ số, `i=n`. Kết hợp bất biến, kết quả đúng bằng tổng cả mảng.

**Giả định dữ liệu:** ví dụ dùng `long` làm tổng để có miền lớn hơn `int`. Với dữ liệu thực tế có thể khiến `long` tràn, cần phép toán được kiểm tra hoặc `BigInteger`; không thể dùng phép cộng tràn mà vẫn coi kết quả là tổng toán học chính xác.

## 5. Chọn bất biến đủ mạnh để suy ra kết quả

Một mệnh đề như “`i >= 0`” thường đúng trong vòng duyệt mảng, nhưng **không đủ** để chứng minh tổng tính được là đúng.

Với thuật toán tính tổng, ta nên duy trì đồng thời:

- **Giới hạn biến đếm:** `0 <= i <= n`.
- **Quan hệ với dữ liệu đã xử lý:** `sum` là tổng tiền tố dài `i`.

Khi điều kiện dừng cho `i == n`, cả hai mệnh đề kết hợp mới suy ra `sum` là tổng toàn bộ mảng.

**Mẹo thiết kế:** từ **hậu điều kiện mong muốn** (“tổng của tất cả phần tử”), thay cụm “tất cả” thành “những phần tử đã xử lý” và gắn với biến đếm `i`. Đó thường là một bất biến đơn giản, dễ chứng minh.

## 6. Ví dụ thứ hai: tìm phần tử lớn nhất

Với một mảng **không rỗng**:

~~~java
int[] data = {-3, 5, 1, 7, 7};
int best = data[0];

for (int i = 1; i < data.length; i++) {
    if (data[i] > best) {
        best = data[i];
    }
}

System.out.println(best);
~~~

**Exact expected stdout:**

~~~text
7
~~~

**Bất biến tại đầu mỗi lượt:** `best` là **giá trị lớn nhất** trong tiền tố `data[0..i-1]`, với `1 <= i <= data.length`.

- **Khởi tạo:** trước lượt đầu `i=1`, tiền tố gồm một phần tử `data[0]`; `best=data[0]` là lớn nhất của tiền tố đó.
- **Bảo toàn:** ở lượt `i`, so `data[i]` với `best`; sau khi cập nhật, `best` là max của tiền tố dài `i+1`. Sau `i++`, bất biến đúng với chỉ số mới.
- **Kết thúc:** khi `i=data.length`, tiền tố chính là toàn bộ mảng, nên `best` là giá trị lớn nhất của mảng.

**Biên quan trọng:** mảng rỗng không có `data[0]`; phải từ chối đầu vào rỗng hoặc định nghĩa một API kết quả tùy chọn. Đặt `best = 0` cho mọi mảng là **sai** với mảng chỉ chứa số âm.

Nếu có nhiều phần tử cùng lớn nhất, code trên trả về **giá trị lớn nhất**, không cam kết chỉ số của lần xuất hiện cuối hay đầu. Muốn trả vị trí, cần một bất biến có thêm biến chỉ số.

## 7. Ví dụ thứ ba: tìm vị trí xuất hiện đầu tiên

~~~java
static int findFirst(int[] data, int target) {
    for (int i = 0; i < data.length; i++) {
        if (data[i] == target) {
            return i;
        }
    }
    return -1;
}
~~~

**Bất biến tại đầu lượt `i`:** không có phần tử nào trong **các vị trí `0..i-1`** bằng `target`.

- **Khởi tạo:** `i=0` thì tiền tố rỗng, mệnh đề đúng.
- **Bảo toàn:** nếu `data[i] != target`, tiếp tục sang `i+1` và tiền tố chưa tìm thấy được mở rộng thêm một vị trí.
- **Nếu tìm thấy:** `data[i] == target`; mọi vị trí trước `i` đã được chứng minh khác target, nên `i` là **vị trí đầu tiên**.
- **Nếu duyệt hết:** `i=n`, tất cả vị trí đều khác target; trả `-1` là đúng.

Ví dụ:

~~~java
int[] data = {4, 6, 2, 6};
System.out.println(findFirst(data, 6)); // 1
System.out.println(findFirst(data, 9)); // -1
~~~

Mã trên minh họa logic; phương thức `findFirst` cần nằm cùng lớp hoặc được gọi qua tên lớp chứa phương thức.

**Chú ý:** một `return` khi tìm thấy là **kết thúc sớm hợp lệ**. Lập luận đúng đắn khi đó dùng bất biến của tiền tố cộng với điều kiện tìm thấy, không phụ thuộc việc vòng lặp phải chạy đến `i=n`.

## 8. Bất biến không đồng nghĩa điều kiện dừng

Hai điều khác nhau:

- **Loop invariant:** điều luôn đúng ở điểm kiểm tra được chọn, dùng để chứng minh **tính đúng kết quả**.
- **Loop variant** (*đại lượng tiến triển*): một đại lượng không âm và giảm dần theo mỗi lượt tiếp tục, thường dùng để chứng minh **kết thúc**.

Ví dụ:

~~~java
for (int remaining = 3; remaining > 0; remaining--) {
    System.out.println("remaining=" + remaining);
}
~~~

**Exact stdout:**

~~~text
remaining=3
remaining=2
remaining=1
~~~

Bất biến: `0 <= remaining && remaining <= 3` ở mỗi đầu lượt, kể cả lúc `remaining=0` và điều kiện đã false. Variant có thể chọn chính `remaining`: khi vẫn lặp, nó là số nguyên dương và mỗi lần update giảm 1, nên cuối cùng đạt 0.

Bất biến **một mình** không ngăn vòng lặp vô hạn:

~~~java
int i = 0;
// while (i >= 0) {
//     // i không đổi, điều kiện luôn true, bất biến i >= 0 vẫn đúng.
// }
~~~

Để bảo đảm dừng, phải có **tiến triển thực sự** và xét tác động của `break`, `continue`, phép cập nhật cũng như khả năng tràn số.

## 9. `break`, `continue` và bất biến

Với `for` ba phần:

- `continue;` bỏ phần body còn lại nhưng **vẫn chạy update** rồi kiểm tra lượt kế. Bất biến cần được bảo toàn cho mọi đường đi dùng `continue`.
- `break;` thoát vòng ngay, **không chạy update cuối**. Nếu cần một hậu điều kiện cho nhánh `break`, hãy chứng minh riêng rằng điều kiện tại vị trí break là đủ.
- `return` kết thúc phương thức; hậu điều kiện của giá trị trả về phải được chứng minh theo đường đi đó.

Một lỗi phổ biến:

~~~java
int[] data = {3, -1, 4};
int sum = 0;

for (int i = 0; i < data.length; i++) {
    if (data[i] < 0) {
        continue;
    }
    sum += data[i];
}
~~~

Code này trả **7**, không phải tổng **6** của mảng. Nếu mục tiêu thật sự là **tổng phần tử không âm**, bất biến đúng phải được viết lại: `sum` bằng tổng các giá trị không âm trong tiền tố đã xử lý.

**Bất biến phải phản ánh đúng yêu cầu bài toán và các nhánh điều khiển**, không chỉ giống một mẫu có sẵn.

## 10. Tác dụng của kiểm tra bất biến khi debug

Bạn có thể tạm tạo một hàm **tính lại tổng tiền tố** rồi so sánh với trạng thái trong mỗi lượt:

~~~java
static long prefixSum(int[] data, int limit) {
    long result = 0;
    for (int k = 0; k < limit; k++) {
        result += data[k];
    }
    return result;
}

static void requireInvariant(boolean condition) {
    if (!condition) {
        throw new IllegalStateException("loop invariant violated");
    }
}
~~~

Trong thuật toán tính tổng:

~~~java
for (int i = 0; i < data.length; i++) {
    requireInvariant(sum == prefixSum(data, i));
    sum += data[i];
}
requireInvariant(sum == prefixSum(data, data.length));
~~~

- Mỗi lần kiểm tra đầu lượt xác nhận mệnh đề đang được duy trì.
- Lần kiểm tra **sau vòng lặp** xác nhận mệnh đề ở trạng thái kết thúc.
- Nếu sai, `IllegalStateException` giúp phát hiện lỗi sớm.

**Giới hạn:** phép kiểm tra này chạy một vòng lặp phụ để tính tổng tiền tố, nên tổng chi phí debug có thể từ `O(n)` thành **`O(n²)`**. Chỉ dùng cho bài học, test hoặc dữ liệu nhỏ; trong production nên bỏ hoặc chỉ bật có kiểm soát.

Bạn cũng có thể dùng `assert` của Java:

~~~java
assert sum == prefixSum(data, i) : "bad prefix sum";
~~~

Tuy nhiên, Java **tắt assertions theo mặc định**; phải chạy với `java -ea ...` mới kiểm tra. Không dùng `assert` làm cơ chế xác thực dữ liệu bắt buộc hoặc để thay lập luận đúng đắn.

## 11. Chương trình Java 17 hoàn chỉnh

Tạo file đúng tên **`LoopInvariantDemo.java`**:

~~~java
public final class LoopInvariantDemo {
    private LoopInvariantDemo() {}

    static void requireInvariant(boolean condition) {
        if (!condition) {
            throw new IllegalStateException("loop invariant violated");
        }
    }

    static long prefixSum(int[] data, int limit) {
        long total = 0;
        for (int k = 0; k < limit; k++) {
            total += data[k];
        }
        return total;
    }

    static long sum(int[] data) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }

        long total = 0;
        for (int i = 0; i < data.length; i++) {
            // Invariant: total == sum of data[0..i-1].
            requireInvariant(total == prefixSum(data, i));
            System.out.println("sum entry i=" + i + " total=" + total);
            total += data[i];
        }

        requireInvariant(total == prefixSum(data, data.length));
        System.out.println("sum final=" + total);
        return total;
    }

    static int prefixMax(int[] data, int limit) {
        int result = data[0];
        for (int k = 1; k < limit; k++) {
            if (data[k] > result) {
                result = data[k];
            }
        }
        return result;
    }

    static int max(int[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("requires nonempty array");
        }

        int best = data[0];
        for (int i = 1; i < data.length; i++) {
            // Invariant: best == max of data[0..i-1].
            requireInvariant(best == prefixMax(data, i));
            System.out.println("max entry i=" + i + " best=" + best);
            if (data[i] > best) {
                best = data[i];
            }
        }

        requireInvariant(best == prefixMax(data, data.length));
        System.out.println("max final=" + best);
        return best;
    }

    static int findFirst(int[] data, int target) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }

        for (int i = 0; i < data.length; i++) {
            // Invariant: data[k] != target for all 0 <= k < i.
            for (int k = 0; k < i; k++) {
                requireInvariant(data[k] != target);
            }
            System.out.println("scan i=" + i + " value=" + data[i]);
            if (data[i] == target) {
                return i;
            }
        }
        System.out.println("not found");
        return -1;
    }

    static int countdown() {
        int ticks = 0;
        for (int remaining = 3; remaining > 0; remaining--) {
            // Invariant: 0 <= remaining <= 3.
            requireInvariant(remaining >= 0 && remaining <= 3);
            System.out.println("remaining=" + remaining);
            ticks++;
        }
        return ticks;
    }

    public static void main(String[] args) {
        System.out.println("== sum ==");
        System.out.println("sum result=" + sum(new int[]{2, -1, 4, 3}));

        System.out.println("== max ==");
        System.out.println("max result=" + max(new int[]{-3, 5, 1, 7, 7}));

        try {
            max(new int[]{});
        } catch (IllegalArgumentException ex) {
            System.out.println("max empty=" + ex.getMessage());
        }

        System.out.println("== find first ==");
        int[] input = {4, 6, 2, 6};
        System.out.println("first 6=" + findFirst(input, 6));
        System.out.println("first 9=" + findFirst(input, 9));

        System.out.println("== termination ==");
        System.out.println("ticks=" + countdown());
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên, terminal tại thư mục chứa `LoopInvariantDemo.java`. Chỉ dùng thư viện tiêu chuẩn JDK; không cần Maven, Gradle hoặc preview feature.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 LoopInvariantDemo.java
java -cp . LoopInvariantDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\LoopInvariantDemo.java
java -cp . LoopInvariantDemo
~~~

### Exact expected stdout

~~~text
== sum ==
sum entry i=0 total=0
sum entry i=1 total=2
sum entry i=2 total=1
sum entry i=3 total=5
sum final=8
sum result=8
== max ==
max entry i=1 best=-3
max entry i=2 best=5
max entry i=3 best=5
max entry i=4 best=7
max final=7
max result=7
max empty=requires nonempty array
== find first ==
scan i=0 value=4
scan i=1 value=6
first 6=1
scan i=0 value=4
scan i=1 value=6
scan i=2 value=2
scan i=3 value=6
not found
first 9=-1
== termination ==
remaining=3
remaining=2
remaining=1
ticks=3
~~~

**Đọc kết quả:**

1. `sum`: trước mỗi lượt, `total` đúng bằng tổng tiền tố đã xử lý; sau vòng, tổng bằng 8.
2. `max`: `best` là max của các phần tử đã xét; chạy với mảng toàn âm vẫn đúng; mảng rỗng được từ chối.
3. `findFirst`: trước mỗi lượt kiểm tra rằng không có kết quả ở tiền tố trước `i`; thấy 6 tại vị trí 1 thì trả ngay; không có 9 nên trả -1.
4. `countdown`: bất biến giới hạn `remaining` được giữ; variant giảm từ 3 xuống 0 nên vòng kết thúc.
5. Các hàm `prefixSum`/`prefixMax`/vòng kiểm tra tiền tố dùng để **xác minh trong demo**, làm tăng chi phí chạy. Khi tối ưu, giữ nguyên lập luận bất biến nhưng bỏ kiểm tra lặp thừa.

## 12. Những sai lầm thường gặp

| Sai lầm | Hệ quả | Cách sửa |
|---|---|---|
| Gọi `i < n` là bất biến | Nó sai khi vòng kết thúc bình thường | Dùng `0 <= i <= n` cho giới hạn chỉ số |
| Viết bất biến chỉ là `sum >= 0` | Không đúng nếu mảng có số âm; không chứng minh tổng | Dùng mệnh đề tổng tiền tố |
| Giả định bất biến đúng ngay sau mọi câu lệnh | Có thể sai giữa body và update | Xác định rõ điểm kiểm tra đầu lượt |
| Bỏ qua bước khởi tạo | Mệnh đề không chắc đúng trước lượt đầu | Chứng minh trường hợp tiền tố rỗng/1 phần tử |
| Chỉ chứng minh bảo toàn mà không nối với hậu điều kiện | Chưa chứng minh kết quả cuối | Ghép bất biến với điều kiện dừng |
| Có bất biến nhưng không có tiến triển | Vẫn có thể lặp vô hạn | Chọn variant giảm dần, kiểm tra update |
| Khởi tạo max bằng 0 | Sai với mảng chỉ có số âm | Lấy phần tử đầu, xử lý mảng rỗng |
| Thêm `continue` nhưng giữ bất biến cũ không phù hợp | Bất biến không phản ánh phần tử bị bỏ | Định nghĩa lại tiền tố đã xử lý / yêu cầu |
| Dùng `break` rồi kết luận như trường hợp `i=n` | Kết thúc sớm có điều kiện khác | Chứng minh riêng đường `break`/`return` |
| Kiểm tra bất biến bằng vòng phụ ở mọi lượt trong production | Dễ làm thuật toán `O(n)` thành `O(n²)` | Chỉ dùng trong test/debug |
| Tin rằng `assert` luôn hoạt động | Mặc định JVM không bật assertions | Dùng `-ea` trong test, không dùng làm kiểm tra bắt buộc |
| Không xét overflow ở tổng số nguyên | Kết quả có thể khác tổng toán học | Dùng kiểu đủ lớn hoặc kiểm tra overflow |

## 13. Bài tập có đáp án

### Bài A — viết bất biến của tổng tiền tố

~~~java
long total = 0;
for (int i = 0; i < data.length; i++) {
    total += data[i];
}
~~~

**Đáp án:** tại đầu mỗi lượt, `0 <= i <= data.length` và `total` là tổng `data[0..i-1]`. Sau điều kiện dừng `i=data.length`, tổng là toàn mảng (giả định phép cộng không overflow).

### Bài B — vì sao khởi tạo max bằng 0 sai?

~~~java
int[] data = {-8, -3, -10};
int best = 0; // Sai nếu bài toán là max của toàn mảng.
~~~

**Đáp án:** 0 không phải phần tử của mảng và lớn hơn tất cả giá trị. Khởi tạo `best=data[0]`, duyệt từ `i=1`; kết quả đúng là `-3`.

### Bài C — bất biến khi tìm phần tử đầu tiên

~~~java
for (int i = 0; i < data.length; i++) {
    if (data[i] == target) return i;
}
return -1;
~~~

**Đáp án:** trước lượt `i`, không phần tử nào trong `data[0..i-1]` bằng target. Khi gặp target, `i` là vị trí đầu tiên; khi duyệt hết, target không có trong mảng.

### Bài D — bất biến có bảo đảm dừng không?

~~~java
int i = 0;
// while (i == 0) { /* không đổi i */ }
~~~

**Đáp án:** mệnh đề `i == 0` vẫn đúng nhưng vòng có thể chạy mãi. Muốn chứng minh kết thúc phải có biến tiến triển và điều kiện dừng đạt được.

### Bài E — `continue` thay đổi mục tiêu tính tổng

~~~java
int[] data = {3, -1, 4};
int total = 0;
for (int i = 0; i < data.length; i++) {
    if (data[i] < 0) continue;
    total += data[i];
}
System.out.println(total);
~~~

**Đáp án:** in `7`. Bất biến phù hợp là “tổng các phần tử **không âm** đã xử lý”, không phải tổng mọi phần tử tiền tố.

## 14. Checklist và kết luận

- [ ] Phân biệt bất biến vòng lặp với **điều kiện tiếp tục lặp**.
- [ ] Xác định rõ **điểm** mà bất biến phải đúng (đầu mỗi lượt).
- [ ] Chứng minh **khởi tạo**, **bảo toàn**, **kết thúc**.
- [ ] Viết bất biến gắn trực tiếp với kết quả mong muốn, ví dụ tổng/max của tiền tố đã xử lý.
- [ ] Dùng điều kiện dừng cùng bất biến để suy ra kết quả.
- [ ] Phân biệt bất biến với **loop variant** dùng để chứng minh kết thúc.
- [ ] Kiểm tra đường `continue`, `break`, `return` để không giả định sai trạng thái.
- [ ] Xét trường hợp mảng rỗng, chỉ số biên, số âm và overflow.
- [ ] Biết công cụ debug có thể xác nhận bất biến trên bộ test nhưng không thay thế chứng minh logic.
- [ ] Hiểu bật assertions bằng `-ea`; không phụ thuộc assert cho validation bắt buộc.
- [ ] Chạy `LoopInvariantDemo.java` bằng Java 17+ và đối chiếu exact stdout.

**Kết luận:** Bất biến vòng lặp là **mối liên hệ được giữ qua từng lượt** giữa trạng thái tích lũy và phần dữ liệu đã xử lý. Thiết kế bất biến đủ mạnh, chứng minh nó đúng ở đầu lượt, bảo toàn qua body/update, rồi kết hợp với điều kiện dừng sẽ giải thích **tại sao thuật toán đúng**, chứ không chỉ cho thấy ví dụ chạy đúng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="loops-010">Chọn while, do-while hoặc for theo bài toán</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn `while`, `do-while` hoặc `for` theo bài toán — Java 17+

Java có ba cấu trúc lặp cơ bản. **Chọn vòng lặp dựa vào thời điểm kiểm tra điều kiện và cách xác định điểm dừng**, không phải vì một cú pháp luôn chạy nhanh hơn cú pháp khác:

- **`for`**: phù hợp khi quản lý chỉ số, biến đếm hoặc các bước khởi tạo–kiểm tra–cập nhật thuộc về cùng một vòng.
- **`while`**: phù hợp khi chưa biết trước số lượt, chỉ tiếp tục khi điều kiện còn đúng; thân **có thể chạy 0 lần**.
- **`do-while`**: phù hợp khi cần thực hiện thân **ít nhất một lần** trước khi quyết định lặp tiếp, chẳng hạn yêu cầu nhập dữ liệu lần đầu.

## 1. Bảng chọn nhanh

| Tình huống | Nên cân nhắc | Lý do |
| --- | --- | --- |
| Duyệt các chỉ số `0..n-1` | `for` | Khởi tạo, điều kiện và cập nhật biến đếm nằm cùng một chỗ |
| Lặp đúng `n` lượt | `for` | Số lượt và biên được biểu đạt rõ |
| Đọc đến khi hết dữ liệu/đến dấu kết thúc | `while` | Số lượt không cố định; có thể không có dữ liệu nào |
| Chờ trạng thái thay đổi, có giới hạn số lần thử | `while` hoặc `for` | Dừng khi điều kiện đạt hoặc hết lượt |
| Hiển thị yêu cầu nhập rồi kiểm tra để nhập lại | `do-while` | Thân cần chạy ít nhất một lần |
| Muốn thực thi thân 0 lần khi điều kiện ban đầu sai | `while` hoặc `for` | Cả hai đều kiểm tra trước |
| Duyệt giá trị từng phần tử mảng, không cần vị trí | Enhanced `for` | Đọc dễ hơn, không quản lý chỉ số thủ công |

**Không có quy tắc bắt buộc**: cùng một thuật toán có thể viết đúng bằng nhiều loại vòng lặp. Chọn cấu trúc làm nổi bật **điều kiện dừng**, giảm rủi ro quên cập nhật và dễ kiểm thử.

## 2. Ba cú pháp và thứ tự thực thi

### `for` — biểu đạt các bước biến đếm

~~~java
for (int i = 0; i < 3; i++) {
    System.out.println(i);
}
~~~

Thứ tự: **khởi tạo một lần → kiểm tra → thân → cập nhật → kiểm tra lại**. Khi điều kiện sai ngay từ đầu, thân không chạy. Nếu biến `i` được khai báo ngay trong phần khởi tạo, nó không còn nằm trong phạm vi sử dụng sau câu lệnh `for`.

### `while` — kiểm tra trước khi chạy thân

~~~java
int remaining = 3;
while (remaining > 0) {
    System.out.println(remaining);
    remaining--;
}
~~~

Thứ tự: **kiểm tra → thân → kiểm tra lại**. Người viết thường chịu trách nhiệm cập nhật trạng thái trong thân; nếu `remaining--` bị quên, vòng có thể không kết thúc. `while` phù hợp khi điểm dừng phụ thuộc dữ liệu/trạng thái chứ không chỉ một chỉ số đếm.

### `do-while` — chạy trước rồi mới kiểm tra

~~~java
int attempt = 0;
do {
    attempt++;
    System.out.println("attempt=" + attempt);
} while (attempt < 2);
~~~

Thứ tự: **thân → kiểm tra → thân...**; thân chạy ít nhất một lần nếu đi vào vòng lặp bình thường. Cú pháp có **dấu chấm phẩy bắt buộc sau `while (condition);`**. Khác `while`, dù điều kiện ban đầu đã false, thân vẫn được chạy một lần trước lần kiểm tra đầu tiên.

| Cấu trúc | Kiểm tra trước thân đầu tiên? | Số lượt tối thiểu | Nơi cập nhật thường gặp |
| --- | --- | ---: | --- |
| Basic `for` | Có | 0 | Phần cập nhật trong đầu vòng |
| `while` | Có | 0 | Trong thân, hoặc từ thay đổi trạng thái |
| `do-while` | Không | 1 | Trong thân trước khi kiểm tra |

## 3. Demo Java 17+ tự chứa, runnable CLI

Tạo tệp UTF-8 tên **`ChooseLoopDemo.java`**:

~~~java
public class ChooseLoopDemo {
    static void countedFor() {
        for (int i = 0; i < 3; i++) {
            System.out.println("for=" + i);
        }
    }

    static void unknownAttemptsWhile() {
        int attempts = 0;
        boolean connected = false;
        while (!connected && attempts < 3) {
            attempts++;
            System.out.println("try=" + attempts);
            connected = attempts == 2;
        }
        System.out.println("connected=" + connected);
        System.out.println("attempts=" + attempts);
    }

    static void promptDoWhile() {
        String[] inputs = {"", " An "};
        int index = 0;
        String name;
        do {
            name = inputs[index++];
            System.out.println("entry="
                    + (name.isBlank() ? "blank" : name.trim()));
        } while (name.isBlank() && index < inputs.length);
        System.out.println("name=" + name.trim());
    }

    static void initialFalse() {
        int available = 0;
        int whileVisits = 0;
        while (available > 0) {
            whileVisits++;
        }

        int doVisits = 0;
        do {
            doVisits++;
        } while (available > 0);

        System.out.println("whileVisits=" + whileVisits);
        System.out.println("doVisits=" + doVisits);
    }

    static void readUntilSentinel() {
        String[] tokens = {"4", "bad", "6", "stop", "100"};
        int index = 0;
        int total = 0;

        while (index < tokens.length) {
            String token = tokens[index++];
            if (token.equals("stop")) {
                System.out.println("sentinel=stop");
                break;
            }
            try {
                total += Integer.parseInt(token);
            } catch (NumberFormatException ex) {
                System.out.println("invalid=" + token);
            }
        }
        System.out.println("total=" + total);
        System.out.println("consumed=" + index);
    }

    static void forContinue() {
        for (int i = 0; i < 4; i++) {
            if (i == 1) {
                continue;
            }
            System.out.println("keep=" + i);
        }
    }

    static void enhancedFor() {
        int total = 0;
        for (int value : new int[] {2, 4, 6}) {
            total += value;
        }
        System.out.println("enhancedTotal=" + total);
    }

    public static void main(String[] args) {
        System.out.println("== counted for ==");
        countedFor();

        System.out.println("== conditional while ==");
        unknownAttemptsWhile();

        System.out.println("== do-while prompt ==");
        promptDoWhile();

        System.out.println("== initially false ==");
        initialFalse();

        System.out.println("== sentinel while ==");
        readUntilSentinel();

        System.out.println("== continue in for ==");
        forContinue();

        System.out.println("== enhanced for ==");
        enhancedFor();
        System.out.println("done");
    }
}
~~~

Sử dụng **JDK 17+**. Mở PowerShell (Windows) hoặc Bash (Linux/macOS) tại thư mục chứa file:

~~~bash
java -version
javac -version
javac -encoding UTF-8 ChooseLoopDemo.java
java ChooseLoopDemo
~~~

**Exact expected stdout** của riêng lệnh `java ChooseLoopDemo`:

~~~text
== counted for ==
for=0
for=1
for=2
== conditional while ==
try=1
try=2
connected=true
attempts=2
== do-while prompt ==
entry=blank
entry=An
name=An
== initially false ==
whileVisits=0
doVisits=1
== sentinel while ==
invalid=bad
sentinel=stop
total=10
consumed=4
== continue in for ==
keep=0
keep=2
keep=3
== enhanced for ==
enhancedTotal=12
done
~~~

`java -version` và `javac -version` cho thông tin phiên bản phụ thuộc máy, không thuộc stdout cố định. Chương trình mô phỏng dữ liệu sẵn để kết quả không phụ thuộc bàn phím hay mạng.

## 4. Chọn theo điều kiện đầu vào, không chỉ số lượt

### Đã biết số lượt hoặc giới hạn chỉ số: `for`

~~~java
int[] values = {10, 20, 30};
for (int i = 0; i < values.length; i++) {
    System.out.println(values[i]);
}
~~~

Biên đúng là `i < values.length`, không phải `<=`. Với mảng rỗng, vòng lặp chạy **0 lượt**. Enhanced `for (int value : values)` còn gọn hơn nếu chỉ cần giá trị, không cần chỉ số hoặc thay đổi phần tử qua vị trí.

### Lặp phụ thuộc trạng thái: `while`

~~~java
int attempts = 0;
boolean success = false;
while (!success && attempts < 3) {
    attempts++;
    success = tryOnce(attempts);
}
~~~

`tryOnce` ở đây chỉ là tên mô tả một phương thức nghiệp vụ do ứng dụng cung cấp; ví dụ **chạy được** trong phần 3 đã dùng điều kiện giả lập `attempts == 2`. Thực tế nên đặt giới hạn để tránh thử vô hạn, và xác định rõ một lần thử có được thực hiện nếu trạng thái thành công ngay từ đầu hay không.

### Phải thực hiện lần đầu rồi mới biết nên lặp không: `do-while`

~~~java
String input;
do {
    input = askForName(); // Phương thức lấy dữ liệu do ứng dụng cung cấp.
} while (input == null || input.isBlank());
~~~

Đây là hình thức thường thấy khi cần hiển thị lời nhắc ít nhất một lần. Trong ứng dụng thực, cần thêm chính sách hủy, đóng input hoặc giới hạn thử; nếu luôn nhận dữ liệu không hợp lệ, vòng có thể không bao giờ kết thúc. Phương thức `askForName()` trong ví dụ ý tưởng này **không thuộc file demo**.

## 5. Có thể chuyển đổi `for` và `while`, nhưng phải giữ thứ tự

Hai đoạn sau thường tương đương nếu không có điều khiển luồng đặc biệt:

~~~java
for (int i = 0; i < 3; i++) {
    System.out.println(i);
}
~~~

~~~java
int i = 0;
while (i < 3) {
    System.out.println(i);
    i++;
}
~~~

Nhưng **`continue` làm thay đổi cách chuyển đổi**:

~~~java
for (int i = 0; i < 4; i++) {
    if (i == 1) {
        continue; // Java vẫn chạy phần cập nhật i++ của for.
    }
    System.out.println(i);
}
~~~

Nếu chép sang `while` rồi đặt `i++` sau `continue`, vòng có thể lặp mãi với `i == 1` vì cập nhật bị bỏ qua. Khi chuyển đổi, bảo đảm mọi đường tiếp tục vòng đều **tiến triển**.

| Lệnh trong thân | Basic `for` | `while` / `do-while` |
| --- | --- | --- |
| `continue` nhắm đến vòng này | Chạy phần cập nhật của `for` rồi kiểm tra điều kiện | Chuyển tới kiểm tra điều kiện; tự bảo đảm trạng thái được cập nhật |
| `break` | Thoát vòng ngay, không chạy cập nhật `for` | Thoát vòng ngay |
| `return` hoặc `throw` | Thoát phương thức hoặc ném ngoại lệ | Tác dụng tương tự |

`do-while` còn khác ở bước kiểm tra **sau thân**, vì thế không thể đổi máy móc thành `while` mà vẫn giữ đúng hành vi nếu điều kiện sai từ đầu.

## 6. Lưu ý về độ an toàn và khả năng đọc

- **Điều kiện phải là `boolean`**, không dùng `while (1)` hay `for (; number;)` như cách coi số là boolean trong một số ngôn ngữ.
- **Dấu `;` có vị trí quan trọng**: basic `for` có hai dấu `;` trong phần đầu; `do-while` có `;` ở cuối; vô tình đặt `;` sau `while (condition)` hoặc `for (...)` sẽ tạo **thân rỗng**.
- **Có điểm dừng rõ ràng**: thay đổi biến đếm/trạng thái, tiêu thụ dữ liệu hoặc dùng `break`/`return` có chủ đích. `while (true)` không tự sai, nhưng phải có chiến lược thoát.
- **Đừng dựa vào độ dài của vòng để chọn cấu trúc**: ba cú pháp không có thứ hạng tốc độ cố định. Hãy ưu tiên biểu đạt đúng hành vi và dễ bảo trì.
- **Phạm vi biến**: `int i` khai báo trong phần đầu `for` không thể dùng ngoài vòng; biến khai báo trước `while` thường vẫn trong phạm vi sau vòng.

## 7. Bài tập tự kiểm tra

1. Muốn duyệt mọi phần tử mảng bằng chỉ số `0..length-1`, nên chọn gì?  
   *Gợi ý:* `for` với `i < array.length`.
2. Muốn đọc dữ liệu cho tới khi gặp `"stop"`, chưa biết bao nhiêu mục, chọn gì?  
   *Gợi ý:* `while`, cập nhật vị trí hoặc đọc mục tiếp theo mỗi lượt.
3. Muốn hiển thị yêu cầu nhập **ít nhất một lần** rồi kiểm tra hợp lệ, chọn gì?  
   *Gợi ý:* `do-while`.
4. Điều kiện sai ngay từ đầu, ba dạng có số lượt tối thiểu lần lượt là bao nhiêu?  
   *Đáp án:* Basic `for` = 0, `while` = 0, `do-while` = 1.
5. Khi chuyển `for` có `continue` sang `while`, dễ quên điều gì?  
   *Đáp án:* Cập nhật trạng thái trước khi kiểm tra tiếp.
6. Nếu không cần chỉ số khi duyệt mảng, cú pháp nào làm mã đơn giản hơn?  
   *Gợi ý:* Enhanced `for`.

## Checklist

- [ ] Chọn **`for`** khi các bước đếm/duyệt chỉ số rõ ràng.
- [ ] Chọn **`while`** khi điều kiện được kiểm tra **trước** và số lượt tùy trạng thái.
- [ ] Chọn **`do-while`** khi thân cần chạy **ít nhất một lần**.
- [ ] Kiểm tra biên chỉ số, điều kiện đầu và cơ chế tiến triển.
- [ ] Hiểu `continue`/`break` trước khi chuyển đổi loại vòng lặp.
- [ ] Biết phân biệt basic `for` với enhanced `for`.

**Kết luận:** Hãy chọn theo **ngữ nghĩa bài toán**: `for` cho vòng đếm rõ ràng, `while` cho điều kiện tiếp diễn kiểm tra trước và `do-while` cho bước thực thi đầu tiên bắt buộc. Kiểm tra kỹ điều kiện dừng và cập nhật sẽ quan trọng hơn việc cố dùng một loại vòng lặp cho mọi trường hợp.


</details>

- [Quay lại đầu trang](#back-to-top)
