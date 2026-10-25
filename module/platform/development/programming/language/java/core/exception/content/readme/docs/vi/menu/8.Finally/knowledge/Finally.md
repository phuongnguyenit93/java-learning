<a id="back-to-top"></a>

# Khối finally

## Menu
- [Cú pháp try-finally và try-catch-finally](#finally-001)
- [Finally khi try hoàn tất bình thường hoặc có ngoại lệ](#finally-002)
- [Finally khi return, break hoặc continue](#finally-003)
- [Giá trị trả về được tính trước khi thực thi finally](#finally-004)
- [Thay đổi đối tượng trả về khác thay đổi biến cục bộ như thế nào](#finally-005)
- [Return hoặc throw trong finally che mất kết quả trước đó](#finally-006)
- [Ngoại lệ cleanup che mất ngoại lệ ban đầu](#finally-007)
- [Giới hạn thực thi finally khi JVM dừng, tiến trình bị kết thúc hoặc mã không thoát](#finally-008)
- [Phân biệt finally, final và finalize](#finally-009)

## <a id="finally-001">Cú pháp try-finally và try-catch-finally</a>

<details>
<summary>Xem chi tiết</summary>

# Cú pháp `try-finally` và `try-catch-finally` trong Java

`finally` là phần được đặt ở cuối một câu lệnh `try` để thực thi mã khi quyền điều khiển rời khỏi phần `try` (và `catch` nếu có) theo quy tắc của Java. Hai cách viết chính của `try` thông thường có `finally` là **`try-finally`** và **`try-catch-finally`**. Cách thứ nhất thực thi phần cuối mà không có nhánh `catch` tại cùng cấu trúc; cách thứ hai vừa có thể xử lý một số ngoại lệ vừa chạy phần cuối trước khi hoàn tất câu lệnh.

Đây là nội dung về **cú pháp, thứ tự chạy và phạm vi trách nhiệm** của từng khối. Để hiểu đúng `finally`, cần phân biệt hai chuyện: `catch` chọn cách xử lý ngoại lệ tương thích, còn `finally` tham gia vào việc hoàn tất câu lệnh `try` bất kể phần trước đó hoàn tất bình thường hay đột ngột, miễn luồng thực thi thực sự đến được bước thực hiện `finally` của Java.

## 1. Hai cấu trúc cú pháp căn bản

### 1.1. `try-finally`: thực hiện công việc cuối mà không bắt lỗi tại chỗ

```java
try {
    // Những thao tác cần thực hiện.
} finally {
    // Công việc cần thực hiện khi rời try.
}
```

Đây là **mẫu cú pháp**, hai khối comment đại diện cho mã ứng dụng. `try` đứng trước, `finally` đứng sau; không có `catch` nào trong cùng cấu trúc.

Nếu `try` hoàn tất bình thường và `finally` cũng hoàn tất bình thường, chương trình chuyển tiếp tới câu lệnh sau toàn bộ `try-finally`. Nếu `try` ném một ngoại lệ mà `finally` hoàn tất bình thường, ngoại lệ ấy **vẫn được truyền ra ngoài**; không có `catch` tại cấu trúc này để xử lý nó. `finally` tự nó không có nghĩa “đã bắt” hay “đã giải quyết” lỗi.

### 1.2. `try-catch-finally`: bắt lỗi tương thích rồi thực hiện phần cuối

```java
try {
    // Thao tác có thể phát sinh lỗi.
} catch (IllegalArgumentException ex) {
    // Xử lý lỗi tham số phù hợp.
} catch (RuntimeException ex) {
    // Xử lý một số lỗi runtime khác.
} finally {
    // Thực hiện bước cuối khi rời cấu trúc.
}
```

Các khối `catch` là **tùy chọn theo số lượng**, nhưng nếu có thì phải xuất hiện **trước `finally`**. Có thể có một hoặc nhiều `catch`, hoặc không có `catch` nếu đã có `finally`. Với các `catch` cùng cấp, nhánh đầu tiên tương thích với **kiểu thực tế** của ngoại lệ được ném từ `try` sẽ được chọn. Khi `catch` được chọn đã thực hiện xong, `finally` chạy theo quy tắc của câu lệnh, rồi mới chuyển quyền điều khiển ra ngoài.

Trong mẫu trên, `IllegalArgumentException extends RuntimeException` nên nhánh `IllegalArgumentException` phải đứng trước nhánh `RuntimeException`. Nếu đổi thứ tự, nhánh chuyên biệt bị che và mã không biên dịch.

**Mệnh đề `finally` chỉ xuất hiện một lần**, sau toàn bộ danh sách `catch`. Không có cú pháp `catch` đặt sau `finally` trong cùng câu lệnh `try`.

### 1.3. So sánh nhanh

| Thuộc tính | `try-finally` | `try-catch-finally` |
| --- | --- | --- |
| `try` | Bắt buộc | Bắt buộc |
| `catch` | Không có ở cùng cấu trúc | Một hoặc nhiều nhánh |
| `finally` | Bắt buộc | Bắt buộc |
| Có thể bắt lỗi tại cùng cấu trúc? | Không | Có, nếu ngoại lệ tương thích |
| Nếu lỗi từ `try` không được bắt và `finally` hoàn tất bình thường | Lỗi tiếp tục lan ra ngoài | Lỗi tiếp tục lan ra ngoài |
| Nếu mọi phần kết thúc bình thường | Chạy tiếp sau cấu trúc | Chạy tiếp sau cấu trúc |
| Nếu `finally` tự `throw` hoặc `return` | Có thể thay thế lý do hoàn tất trước đó | Có thể thay thế lý do hoàn tất trước đó |

Một `try-catch` **không có `finally`** cũng hợp lệ, nhưng đó là hình thức khác. Ngược lại, một `try` thông thường đứng một mình **không có cả `catch` lẫn `finally`** thì không hợp lệ. Ngoại lệ về yêu cầu cú pháp này là **`try-with-resources`** có phần khai báo tài nguyên trong ngoặc, được trình bày riêng ở phần cuối.

## 2. Thứ tự thực thi trong `try-finally`

Với một `try-finally` thông thường, Java thực hiện phần `try` trước. Sau khi phần `try` hoàn tất, Java thực hiện `finally`, rồi xác định cách hoàn tất toàn bộ câu lệnh.

```text
Bắt đầu try-finally
        |
        v
    Khối try
        |
        +---- Hoàn tất bình thường --------+
        |                                  |
        +---- return / break / continue ---+---> Khối finally
        |                                  |          |
        +---- Ném ngoại lệ ----------------+          v
                                            Xét cách finally hoàn tất
                                                       |
                              +------------------------+------------------+
                              |                                           |
                        Bình thường                                 Đột ngột
                              |                                           |
                 Giữ kết quả/lý do trước đó                  Lý do mới thay thế
```

Ví dụ đặt trong một phương thức:

```java
System.out.println("A");
try {
    System.out.println("B");
} finally {
    System.out.println("C");
}
System.out.println("D");
```

Kết quả:

```text
A
B
C
D
```

Phần `try` hoàn tất bình thường, sau đó `finally` chạy và hoàn tất bình thường. Vì vậy, câu lệnh `System.out.println("D")` được thực hiện.

Nếu thêm `throw new IllegalStateException("failed");` sau khi in `B` trong phần `try`, dòng `C` vẫn được thực hiện trong luồng Java bình thường; nhưng dòng `D` không chạy trên đường ném ngoại lệ ấy, trừ khi có một `catch` **bên ngoài** xử lý sự cố và đặt luồng điều khiển theo một đường khác.

## 3. Thứ tự thực thi trong `try-catch-finally`

Khi có `catch`, Java thực hiện bước chọn bộ xử lý **trước bước hoàn tất `finally`**:

1. Thực hiện khối `try`.
2. Nếu `try` hoàn tất bình thường, bỏ qua mọi `catch` rồi thực hiện `finally`.
3. Nếu `try` ném một đối tượng ngoại lệ, xét các `catch` cùng cấp theo thứ tự khai báo. Chỉ nhánh **đầu tiên tương thích** mới được thực thi.
4. Nếu không có `catch` phù hợp, ngoại lệ tiếp tục là lý do hoàn tất đang chờ, nhưng `finally` vẫn được thực hiện trước khi lan ra ngoài trong quá trình xử lý Java bình thường.
5. Nếu `catch` phù hợp hoàn tất bình thường, cấu trúc có thể hoàn tất bình thường sau khi `finally` hoàn tất bình thường.
6. Nếu `catch` hoàn tất đột ngột vì `throw` hay `return`, `finally` vẫn được thực hiện; nếu `finally` hoàn tất bình thường, lý do đang chờ được giữ lại.
7. Nếu chính `finally` hoàn tất đột ngột, **lý do mới của `finally`** có thể thay thế lý do trước đó.

```text
                      try
                       |
          +------------+-------------+
          |                          |
     Kết thúc thường             Ném ngoại lệ
          |                          |
       Bỏ catch               Tìm catch phù hợp
          |                     |           |
          |                   Có           Không
          |                     |           |
          |               Chạy catch     Lan ngoại lệ
          |                     |           |
          +---------------------+-----------+
                                |
                             finally
                                |
                   Xét cách hoàn tất rồi ra ngoài
```

**Không có đường “thực hiện tất cả các `catch` rồi mới chạy `finally`”.** Mỗi ngoại lệ chính chỉ làm chọn tối đa một nhánh `catch` của cùng một câu lệnh `try`. `finally` không phải là một nhánh `catch` thay thế.

## 4. Ví dụ Java 17+: quan sát cả hai cú pháp

Lưu nguyên tệp `FinallyFormsDemo.java`:

```java
public class FinallyFormsDemo {
    static void onlyFinally(boolean fail) {
        try {
            System.out.println("try-only");
            if (fail) {
                throw new IllegalStateException("failed");
            }
            System.out.println("work-ok");
        } finally {
            System.out.println("finally-only");
        }
        System.out.println("after-only");
    }

    static void catchFinally(int code) {
        try {
            System.out.println("try=" + code);
            if (code == 1) {
                throw new NumberFormatException("invalid number");
            }
            if (code == 2) {
                throw new IllegalStateException("state");
            }
            System.out.println("try-ok");
        } catch (NumberFormatException ex) {
            System.out.println("catch-number=" + ex.getMessage());
        } catch (RuntimeException ex) {
            System.out.println("catch-runtime=" + ex.getMessage());
        } finally {
            System.out.println("finally-mixed");
        }
        System.out.println("after-mixed");
    }

    public static void main(String[] args) {
        onlyFinally(false);

        try {
            onlyFinally(true);
        } catch (IllegalStateException ex) {
            System.out.println("outer=" + ex.getMessage());
        }

        catchFinally(0);
        catchFinally(1);
        catchFinally(2);
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac -encoding UTF-8 FinallyFormsDemo.java
java FinallyFormsDemo
```

Kết quả chính xác:

```text
try-only
work-ok
finally-only
after-only
try-only
finally-only
outer=failed
try=0
try-ok
finally-mixed
after-mixed
try=1
catch-number=invalid number
finally-mixed
after-mixed
try=2
catch-runtime=state
finally-mixed
after-mixed
```

Giải thích:

- `onlyFinally(false)`: `try` kết thúc bình thường, sau đó `finally` chạy và phương thức in `after-only`.
- `onlyFinally(true)`: `throw` làm phần `try` hoàn tất đột ngột. `finally` vẫn in `finally-only`. Vì cấu trúc này không có `catch` tại chỗ, ngoại lệ tiếp tục tới `catch` bên ngoài ở `main`; `after-only` không được in trong lần gọi đó.
- `catchFinally(0)`: phần `try` không ném lỗi; hai `catch` đều bị bỏ qua; `finally` chạy.
- `catchFinally(1)`: `NumberFormatException` khớp nhánh cụ thể đầu tiên; nhánh `RuntimeException` không chạy.
- `catchFinally(2)`: ngoại lệ không khớp `NumberFormatException` nhưng khớp `RuntimeException`. Sau nhánh này, `finally` chạy và phương thức tiếp tục tới `after-mixed`.

Đoạn mã cố ý dùng ngoại lệ unchecked để tập trung quan sát cú pháp và thứ tự chạy, không cần thêm mệnh đề `throws` trong các chữ ký.

## 5. Checked exception vẫn phải bắt hoặc khai báo

Thêm `finally` **không miễn quy tắc checked exception** của Java. Nếu một lời gọi có thể ném `IOException` và không có `catch` xử lý ngoại lệ đó, phương thức chứa `try-finally` thường phải khai báo `throws IOException` hoặc một kiểu cha được phép.

Lưu thành `CheckedFinallyDemo.java`:

```java
import java.io.IOException;

public class CheckedFinallyDemo {
    static void readConfig() throws IOException {
        try {
            System.out.println("read");
            throw new IOException("config missing");
        } finally {
            System.out.println("cleanup");
        }
    }

    public static void main(String[] args) {
        try {
            readConfig();
        } catch (IOException ex) {
            System.out.println("handled=" + ex.getMessage());
        }
        System.out.println("after main catch");
    }
}
```

```bash
javac -encoding UTF-8 CheckedFinallyDemo.java
java CheckedFinallyDemo
```

Kết quả:

```text
read
cleanup
handled=config missing
after main catch
```

Điểm cần nhớ: `readConfig()` công bố `throws IOException` vì lỗi có thể đi ra ngoài phương thức. `finally` chỉ thực hiện bước cuối; `main()` mới có `catch` để xử lý lỗi ấy.

Nếu trong chính `readConfig()` đặt `catch (IOException ex)` **trước `finally`** và xử lý lỗi xong mà không ném tiếp checked exception, việc khai báo `throws IOException` có thể không cần thiết nữa. Dẫu vậy, `catch` chỉ nên xử lý những gì tầng hiện tại thực sự biết cách xử lý, thay vì tự động nuốt lỗi cho mã “gọn”.

## 6. `return`, `break` và `continue` vẫn đi qua `finally`

Theo JLS, những câu lệnh như `return`, `break`, `continue` làm mã đang chạy **hoàn tất đột ngột** với lý do chuyển quyền điều khiển, dù không có ngoại lệ nào được ném. `finally` được thực hiện trước khi lý do đang chờ hoàn tất việc chuyển quyền điều khiển ra khỏi cấu trúc.

Ví dụ chạy được, lưu thành `FinallyControlFlowDemo.java`:

```java
public class FinallyControlFlowDemo {
    static int returnValue() {
        int value = 10;
        try {
            return value;
        } finally {
            value = 99;
            System.out.println("finally value=" + value);
        }
    }

    static void loop() {
        for (int i = 0; i < 3; i++) {
            try {
                if (i == 0) {
                    continue;
                }
                if (i == 1) {
                    break;
                }
            } finally {
                System.out.println("finally i=" + i);
            }
        }
        System.out.println("loop finished");
    }

    public static void main(String[] args) {
        System.out.println("returned=" + returnValue());
        loop();
    }
}
```

```bash
javac -encoding UTF-8 FinallyControlFlowDemo.java
java FinallyControlFlowDemo
```

Kết quả:

```text
finally value=99
returned=10
finally i=0
finally i=1
loop finished
```

Ở `returnValue()`, biểu thức `return value;` đã được đánh giá thành giá trị nguyên thủy `10` trước khi chạy `finally`. Gán lại biến cục bộ thành `99` trong `finally` không đổi giá trị trả về đã xác định. `finally` chạy xong bình thường thì phương thức trả `10`.

Trong vòng lặp, `continue` ở `i = 0` và `break` ở `i = 1` đều làm thực hiện `finally`. Mỗi lần lặp chỉ có một lần chạy `finally` tương ứng; vòng lặp dừng trước `i = 2`.

**Đặc biệt:** đây là ví dụ về `finally` khi rời cấu trúc theo đường điều khiển Java. Không có nghĩa mọi cách chấm dứt JVM đều bảo đảm `finally` chạy, ví dụ tiến trình bị hệ điều hành cưỡng bức kết thúc.

## 7. Nếu chính `finally` ném lỗi hoặc `return` thì sao?

`finally` không bị giới hạn phải hoàn tất bình thường. Nó có thể ném ngoại lệ, thực hiện `return` hoặc có hành vi chuyển quyền điều khiển khác hợp lệ trong ngữ cảnh. Nếu nó hoàn tất đột ngột, lý do mới **có thể thay thế** lý do ban đầu từ `try` hay `catch`.

| Phần `try`/`catch` | Phần `finally` | Kết quả toàn bộ câu lệnh |
| --- | --- | --- |
| Kết thúc bình thường | Kết thúc bình thường | Kết thúc bình thường |
| `return 10` | Kết thúc bình thường | Tiếp tục `return 10` |
| `throw A` | Kết thúc bình thường | Tiếp tục ném ngoại lệ `A` |
| `return 10` | `return 20` | Trả `20`, thay giá trị `10` đã chọn |
| `throw A` | `throw B` | Ném `B`; lý do ném `A` bị thay thế |
| Khối `catch` xử lý lỗi và hoàn tất bình thường | `throw B` | Toàn bộ cấu trúc lại kết thúc vì `B` |

Lưu thành `FinallyOverrideDemo.java`:

```java
public class FinallyOverrideDemo {
    static int overriddenReturn() {
        try {
            return 10;
        } finally {
            return 20; // Minh họa hành vi cần tránh.
        }
    }

    static void maskedException() {
        try {
            throw new IllegalStateException("original");
        } finally {
            throw new IllegalArgumentException("cleanup"); // Không nên viết như vậy.
        }
    }

    public static void main(String[] args) {
        System.out.println("value=" + overriddenReturn());

        try {
            maskedException();
        } catch (RuntimeException ex) {
            System.out.println("observed=" + ex.getMessage());
            System.out.println("cause=" + ex.getCause());
        }
    }
}
```

```bash
javac -encoding UTF-8 FinallyOverrideDemo.java
java FinallyOverrideDemo
```

Kết quả:

```text
value=20
observed=cleanup
cause=null
```

Dù `try` ban đầu muốn trả `10`, lệnh `return 20` trong `finally` đã thay thế nó. Trong `maskedException()`, ngoại lệ `IllegalStateException("original")` không phải đối tượng được truyền ra: `finally` đã ném ngoại lệ mới `IllegalArgumentException("cleanup")`. Ngoại lệ mới này **không tự động chứa ngoại lệ cũ ở `getCause()`**; ví dụ in ra `null`.

Đây là **ví dụ cảnh báo**, không phải mẫu thiết kế nên áp dụng. Tránh `return` trong `finally`, tránh ghi đè ngoại lệ gốc khi dọn dẹp. Với tài nguyên đóng được, ưu tiên `try-with-resources` để Java quản lý ngoại lệ phát sinh khi đóng.

## 8. Ngoại lệ từ `catch` hoặc `finally` không được `catch` “anh em” bắt tiếp

Một nhầm lẫn phổ biến là cho rằng khi một khối `catch` ném lỗi mới, Java sẽ chuyển xuống nhánh `catch` kế tiếp của **cùng** câu lệnh `try`. Điều này sai. Các `catch` cùng cấp là các phương án xử lý lỗi được ném từ khối `try`; chúng không tạo thành một chuỗi xử lý nối tiếp.

```java
try {
    throw new IllegalArgumentException("input");
} catch (IllegalArgumentException ex) {
    throw new IllegalStateException("converted", ex);
} catch (IllegalStateException ex) {
    System.out.println("not selected for converted error");
} finally {
    System.out.println("finally");
}
```

Đây là đoạn minh họa hợp lệ trong phương thức. Nếu không có `catch` ngoài, `IllegalStateException("converted")` vẫn lan ra sau khi in `finally`. Nhánh `catch (IllegalStateException)` cùng cấp **không xử lý** ngoại lệ vừa phát sinh trong nhánh `catch` trước.

Tương tự, một ngoại lệ phát sinh **từ `finally`** không được thử lại qua các nhánh `catch` cùng cấu trúc. Muốn xử lý ngoại lệ mới, phải đặt một cấu trúc `try-catch` bao ngoài hoặc xử lý riêng tại nơi có thể phát sinh.

Cú pháp lồng nhau hợp lệ:

```java
try {
    try {
        throw new IllegalArgumentException("input");
    } catch (IllegalArgumentException ex) {
        throw new IllegalStateException("converted", ex);
    } finally {
        System.out.println("inner-finally");
    }
} catch (IllegalStateException ex) {
    System.out.println("outer-catch=" + ex.getMessage());
}
```

Kết quả đoạn minh họa thứ hai:

```text
inner-finally
outer-catch=converted
```

Điểm quan trọng là **ranh giới của cấu trúc `try`**: lỗi được ném từ khối `catch` của cấu trúc trong sẽ đi ra ngoài và có thể được bộ xử lý của cấu trúc ngoài tiếp nhận.

## 9. Dọn dẹp tài nguyên: `finally` thông thường và `try-with-resources`

`finally` có thể dùng để hoàn trả trạng thái tạm thời hoặc thực hiện thao tác kết thúc cần thiết theo hợp đồng ứng dụng. Nhưng dùng `finally` để đóng mọi đối tượng tài nguyên theo cách thủ công thường làm mã dài, nhất là khi **cả phần nghiệp vụ lẫn thao tác đóng đều có thể ném ngoại lệ**.

Cách thủ công, minh họa:

```java
Resource resource = openResource();
try {
    useResource(resource);
} finally {
    resource.close();
}
```

Đoạn trên **chỉ là mã minh họa**, giả định `Resource`, `openResource()` và `useResource(...)` tồn tại. Nó có các giới hạn: `openResource()` được gọi **trước `try`**, nên nếu thao tác mở thất bại thì `finally` này chưa được áp dụng; đồng thời lỗi `close()` có thể làm mất lỗi ban đầu nếu `try` cũng đã ném lỗi.

Với kiểu `AutoCloseable`, Java cung cấp `try-with-resources`:

```java
try (Resource resource = openResource()) {
    useResource(resource);
} catch (IOException ex) {
    handleIoFailure(ex);
} finally {
    finishOperation();
}
```

Đây là mẫu minh họa với kiểu `Resource` thực hiện `AutoCloseable` và các hàm hỗ trợ có chữ ký phù hợp. Cấu trúc này khác với `try-finally` thông thường vì có phần chỉ định tài nguyên `(...)` sau `try`. Một `try-with-resources` **có thể không có cả `catch` và `finally`**, miễn có ít nhất một tài nguyên hợp lệ trong phần `(...)`.

Các tài nguyên được khởi tạo thành công sẽ được đóng theo thứ tự ngược với lúc khởi tạo. Khi có `catch`/`finally` đi kèm, các thao tác đóng tự động của `try-with-resources` xảy ra **trước** khối `catch` và `finally` ở cùng câu lệnh. Nếu phần thân `try` ném lỗi còn `close()` cũng ném lỗi, lỗi đóng có thể được giữ trong `getSuppressed()` của ngoại lệ chính thay vì ghi đè ngoại lệ đó.

### Chương trình chạy được minh họa thứ tự đóng và `catch-finally`

Lưu thành `ResourceOrderDemo.java`:

```java
public class ResourceOrderDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() {
            System.out.println("close");
        }
    }

    public static void main(String[] args) {
        try (Resource resource = new Resource()) {
            System.out.println("body");
            throw new IllegalStateException("failed");
        } catch (IllegalStateException ex) {
            System.out.println("catch=" + ex.getMessage());
        } finally {
            System.out.println("finally");
        }
        System.out.println("after");
    }
}
```

```bash
javac -encoding UTF-8 ResourceOrderDemo.java
java ResourceOrderDemo
```

Kết quả:

```text
body
close
catch=failed
finally
after
```

Thứ tự này cho thấy: thân `try-with-resources` chạy, tài nguyên tự động đóng, nhánh `catch` xử lý lỗi, `finally` thực hiện bước cuối, rồi chương trình chạy tiếp. Cần nhớ đây là **đối chiếu với cú pháp quản lý tài nguyên**; khi chỉ cần học hai cấu trúc gốc, `try-finally` và `try-catch-finally` không yêu cầu tài nguyên `AutoCloseable`.

## 10. Phân biệt phạm vi `try` với mã trước/sau cấu trúc

`finally` gắn với **một câu lệnh `try` cụ thể**. Nó không phải bộ xử lý “mọi mã trong phương thức”, nên phải chọn vị trí đặt `try` chính xác.

Ví dụ minh họa:

```java
String config = loadConfig(); // Nếu ném lỗi, chưa hề vào try bên dưới.
try {
    useConfig(config);
} finally {
    System.out.println("finally");
}
```

Nếu `loadConfig()` ném ngoại lệ trước khi câu lệnh `try` bắt đầu thực thi, `finally` dưới đó **không chạy** do chưa vào cấu trúc. Muốn dọn dẹp liên quan cả hai thao tác, cần thiết kế lại phạm vi `try` hoặc dùng cấu trúc tài nguyên phù hợp, không chỉ đặt `finally` ở bất kỳ đâu cuối phương thức.

Phạm vi biến cũng quan trọng. Một biến cục bộ được khai báo **chỉ bên trong khối `try`** không tự hiển thị trong `finally`:

```java
try {
    int count = 10;
    System.out.println(count);
} finally {
    // System.out.println(count); // Nếu mở comment: không tìm thấy biến count.
}
```

Khi thực sự cần dùng biến ở cả hai khối, phải khai báo nó ở phạm vi bao ngoài, đồng thời bảo đảm biến đã được khởi tạo theo quy tắc *definite assignment*:

```java
int count = 0;
try {
    count = 10;
} finally {
    System.out.println(count);
}
```

Ở ví dụ sau, `count` nhìn thấy và đã được khởi tạo trước cấu trúc, nên mã hợp lệ. Hai đoạn này là mã minh họa trong thân phương thức, không phải tệp có `main` độc lập.

## 11. Cú pháp sai thường gặp

### Sai 1: `try` thường không có `catch` hoặc `finally`

```java
class InvalidBareTry {
    void run() {
        try {
            System.out.println("work");
        }
    }
}
```

**Cố ý không biên dịch.** Đây không phải `try-with-resources`; cần ít nhất một `catch` hoặc `finally`.

### Sai 2: đặt `finally` trước `catch`

```java
class InvalidClauseOrder {
    void run() {
        try {
            throw new IllegalArgumentException("bad");
        } finally {
            System.out.println("cleanup");
        } catch (IllegalArgumentException ex) {
            System.out.println("handled");
        }
    }
}
```

**Cố ý không biên dịch.** Đúng thứ tự là `try { ... } catch (...) { ... } finally { ... }`. Không thể đặt `catch` sau `finally` trong cùng cấu trúc.

### Sai 3: bắt ngoại lệ cha trước ngoại lệ con

```java
class InvalidCatchOrder {
    void run() {
        try {
            throw new IllegalArgumentException("bad");
        } catch (RuntimeException ex) {
            System.out.println("general");
        } catch (IllegalArgumentException ex) {
            System.out.println("specific");
        } finally {
            System.out.println("cleanup");
        }
    }
}
```

**Cố ý không biên dịch** vì `RuntimeException` đã bao phủ `IllegalArgumentException`. Đặt nhánh con trước nhánh cha nếu thực sự cần xử lý riêng.

### Sai 4: xem `finally` như `catch` để nuốt checked exception

```java
import java.io.IOException;

class InvalidCheckedFinally {
    void run() {
        try {
            throw new IOException("failed");
        } finally {
            System.out.println("cleanup");
        }
    }
}
```

**Cố ý không biên dịch** vì `IOException` là checked exception và có thể thoát khỏi `run()`. Sửa bằng `void run() throws IOException` hoặc bắt đúng kiểu ở phạm vi phù hợp. `finally` không tự hoàn thành nghĩa vụ này.

### Sai 5: nghĩ `finally` luôn cứu được tiến trình

Ngay cả khi cú pháp hợp lệ, `finally` không bảo đảm chạy nếu tiến trình bị chấm dứt đột ngột từ bên ngoài, JVM crash hoặc lệnh dừng tiến trình không cho phép luồng Java thông thường tiếp tục. Đừng dùng `finally` như bảo đảm tuyệt đối cho việc ghi dữ liệu quan trọng khi tiến trình có thể mất nguồn hoặc bị dừng cưỡng bức.

## 12. Cách lựa chọn cấu trúc trong thực tế

| Nhu cầu | Cấu trúc thích hợp |
| --- | --- |
| Cần làm công việc cuối, nhưng lỗi nên truyền lên để tầng trên xử lý | `try-finally` |
| Có thể bắt lỗi cụ thể tại chỗ và vẫn cần thực hiện bước cuối | `try-catch-finally` |
| Muốn bắt lỗi và không có công việc cuối riêng | `try-catch` |
| Cần đóng một hay nhiều tài nguyên `AutoCloseable` | Ưu tiên `try-with-resources`; thêm `catch`/`finally` nếu cần |
| Muốn giữ thông tin lỗi gốc khi dịch ngoại lệ | `catch` tạo lỗi mới với `cause`; tránh `finally` ném lỗi đè |
| Chỉ muốn ghi log rồi để lỗi tiếp tục truyền | Thường cân nhắc ghi tại ranh giới thích hợp; `try-finally` không có nghĩa vụ log |
| Cần phục hồi bằng fallback hợp lệ | `catch` ở tầng có ngữ cảnh; `finally` chỉ xử lý việc cuối |

Cấu trúc phù hợp phụ thuộc trách nhiệm của tầng hiện tại. Ví dụ, tầng repository có thể để `IOException` lan lên service, còn tầng API có thể dùng `catch` hoặc một cơ chế xử lý lỗi tập trung. `finally` nên chứa công việc phải làm khi rời phạm vi, **không nên chứa logic thay thế kết quả hoặc ngoại lệ**.

## 13. Cách dự đoán kết quả của một đoạn `try-catch-finally`

Đọc mã theo thứ tự sau:

1. **Phần `try` làm gì?** Có hoàn tất bình thường, `throw`, `return`, `break` hoặc `continue`?
2. **Nếu có ngoại lệ, `catch` nào phù hợp?** Kiểm tra kiểu thực tế và thứ tự `catch`; đừng giả định mọi nhánh sẽ chạy.
3. **Nếu `catch` đã chạy, nó hoàn tất thế nào?** Trả kết quả, ném lại lỗi, tạo ngoại lệ mới hay hoàn tất bình thường?
4. **Phần `finally` có làm gì thay đổi quyền điều khiển không?** Nếu hoàn tất bình thường, lý do trước đó được giữ; nếu `throw` hoặc `return`, nó có thể thay thế.
5. **Sau toàn bộ cấu trúc, điều gì chạy?** Chỉ chạy câu lệnh tiếp theo nếu cấu trúc có thể hoàn tất bình thường trên đường đang xét. Nếu đang truyền lỗi, xét `catch` ở phạm vi ngoài.

Thử tự tính đầu ra của đoạn đặt trong phương thức:

```java
try {
    System.out.println("start");
    throw new IllegalArgumentException("bad");
} catch (IllegalArgumentException ex) {
    System.out.println("handled");
} finally {
    System.out.println("end");
}
System.out.println("next");
```

Kết quả:

```text
start
handled
end
next
```

Lỗi được xử lý ở `catch` đầu tiên tương thích, `finally` hoàn tất bình thường nên toàn bộ `try-catch-finally` cũng hoàn tất bình thường, sau đó mã chạy đến `next`.

Nếu xóa `catch` và giữ nguyên `finally`, kết quả trước khi ngoại lệ truyền ra ngoài chỉ gồm `start` và `end`; dòng `next` không chạy trên đường đó.

## 14. Câu hỏi tự kiểm tra

**1.** Có thể viết `try { ... } finally { ... }` mà không có `catch` không?  
**Có.** Đó là `try-finally`. Nếu `try` ném lỗi, `finally` bình thường không ngăn lỗi lan ra ngoài.

**2.** Trong `try-catch-finally`, `finally` đứng trước hay sau các `catch`?  
**Sau tất cả `catch`** cùng cấu trúc; viết ngược thứ tự là sai cú pháp.

**3.** Khi `try` không ném lỗi, `catch` có được chạy như `else` không?  
**Không.** Các `catch` bị bỏ qua; `finally` vẫn được thực hiện theo quy tắc.

**4.** Khi `catch` đã xử lý một lỗi và hoàn tất bình thường, `finally` có chạy không?  
**Có.** Sau đó chương trình có thể tiếp tục câu lệnh kế tiếp nếu `finally` cũng hoàn tất bình thường.

**5.** `return 10` trong `try` và `finally` chỉ in log thì kết quả trả về là gì?  
**10**, trừ khi `finally` tự gây lý do hoàn tất đột ngột khác. `finally` chạy trước khi phương thức thực sự trả về.

**6.** Nếu `finally` viết `return 20` trong khi `try` dự định `return 10` thì sao?  
Kết quả trở thành **20**. Đây là lý do cần tránh `return` trong `finally`.

**7.** Ngoại lệ ném từ `finally` có tự được một `catch` cùng cấp trước đó bắt không?  
**Không.** Nó truyền ra phạm vi ngoài; cần bộ xử lý bao ngoài nếu muốn bắt.

**8.** Đóng tài nguyên với `try-with-resources` có hoàn toàn giống viết `resource.close()` thủ công trong `finally` không?  
**Không.** Cú pháp tài nguyên có quy tắc khởi tạo, đóng tự động và suppressed exceptions riêng.

**9.** `try { throw new IOException(); } finally { ... }` có thể bỏ `throws IOException` ở chữ ký phương thức không?  
**Không**, nếu không có bộ xử lý thích hợp và `finally` có thể hoàn tất bình thường: checked exception vẫn phải được bắt hoặc khai báo.

**10.** `finally` có chạy nếu một thao tác trước câu lệnh `try` ném lỗi khiến không vào `try` không?  
**Không.** Phạm vi `finally` gắn với câu lệnh `try` mà chương trình chưa bắt đầu.

## Tài liệu tham khảo chuẩn

- [Java Language Specification 21 — §14.20: The `try` Statement](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20).
- [JLS 21 — §14.20.1: Execution of `try-catch`](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20.1).
- [JLS 21 — §14.20.2: Execution of `try-finally` and `try-catch-finally`](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20.2).
- [JLS 21 — §14.20.3: `try-with-resources`](https://docs.oracle.com/javase/specs/jls/se21/html/jls-14.html#jls-14.20.3).
- [JLS 21 — §11.2: Compile-Time Checking of Exceptions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-11.html#jls-11.2).

**Ghi nhớ:** `try-finally` dùng khi cần thực thi bước cuối và cho phép lỗi tiếp tục lan truyền; `try-catch-finally` thêm khả năng xử lý ngoại lệ tại chỗ. Trong cả hai cấu trúc, `finally` chạy trước khi hoàn tất việc rời câu lệnh theo luồng Java bình thường; nếu `finally` tự `return` hoặc ném lỗi, nó có thể thay thế kết quả hoặc ngoại lệ ban đầu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="finally-002">Finally khi try hoàn tất bình thường hoặc có ngoại lệ</a>

<details>
<summary>Xem chi tiết</summary>

# Khối `finally` khi `try` hoàn tất bình thường hoặc phát sinh ngoại lệ trong Java

Khối **`finally`** là phần tùy chọn của cấu trúc `try` trong Java, dùng để thực hiện công việc cần làm **khi luồng điều khiển rời cấu trúc `try`**, bất kể phần `try` hoàn tất bình thường hay kết thúc bất thường vì ngoại lệ. Nếu có `catch`, `finally` còn chạy sau khi nhánh `catch` được chọn hoàn tất hoặc trước khi một ngoại lệ từ `catch` tiếp tục truyền ra ngoài.

Điều quan trọng là **`finally` không tự bắt ngoại lệ và cũng không bảo đảm chương trình tiếp tục chạy bình thường**. Nó là bước trong quá trình rời khối; sau khi `finally` hoàn tất bình thường, kết quả đang chờ từ `try`/`catch` được giữ nguyên. Nếu chính `finally` ném ngoại lệ hoặc `return`, nó có thể thay thế kết quả đang chờ, thậm chí che mất lỗi gốc.

Bài học này tập trung vào hai đường đi cốt lõi: **`try` hoàn tất bình thường** và **`try` phát sinh ngoại lệ**. Đồng thời, bạn sẽ thấy các trường hợp `catch`, `return`, `throw`, `finally` lồng nhau, giới hạn bảo đảm thực thi và cách dọn dẹp tài nguyên đúng trên Java 17+.

## 1. Cú pháp và vị trí của `finally`

Một cấu trúc `try-catch-finally` thông thường:

~~~java
try {
    // Tác vụ có thể thành công hoặc ném ngoại lệ.
} catch (SomeException ex) {
    // Xử lý ngoại lệ tương ứng.
} finally {
    // Công việc cần thực hiện khi rời cấu trúc.
}
~~~

Đây là khung minh họa; `SomeException` phải là kiểu ngoại lệ hợp lệ và các phần thân cần được thay bằng mã thật.

Java cho phép một số cách tổ chức:

~~~java
// Chỉ try-finally, không có catch.
try {
    work();
} finally {
    cleanup();
}
~~~

~~~java
// Có catch và finally.
try {
    work();
} catch (IOException ex) {
    handle(ex);
} finally {
    cleanup();
}
~~~

~~~java
// Có nhiều catch và một finally.
try {
    work();
} catch (FileNotFoundException ex) {
    handleMissing(ex);
} catch (IOException ex) {
    handleOtherIo(ex);
} finally {
    cleanup();
}
~~~

Những đoạn trên là **minh họa cấu trúc**, giả định các phương thức/kiểu/import đã được định nghĩa. Một `try` truyền thống phải có ít nhất một `catch` hoặc một `finally`. Khối `finally` đứng **sau toàn bộ `catch`** của cấu trúc đó và chỉ có **một khối `finally`** trực tiếp cho mỗi `try`.

`finally` không phải phương thức, không có kiểu trả về, không nhận tham số và không có danh sách ngoại lệ `throws` riêng. Nếu câu lệnh bên trong `finally` có thể ném checked exception, nghĩa vụ bắt/khai báo thuộc về phương thức hoặc constructor chứa câu lệnh đó.

## 2. Khi `try` hoàn tất bình thường

**Hoàn tất bình thường** nghĩa là luồng thực thi đi đến hết phần thân `try` mà không bị chuyển hướng bởi một nguyên nhân kết thúc bất thường như ngoại lệ hoặc `return`. Khi đó:

1. Các câu lệnh trong `try` lần lượt được thực thi.
2. Không nhánh `catch` nào của cấu trúc này được chọn.
3. Khối `finally` chạy.
4. Nếu `finally` hoàn tất bình thường, chương trình tiếp tục tại câu lệnh sau toàn bộ cấu trúc `try`/`catch`/`finally`.

Ví dụ ngắn:

~~~java
// Đoạn minh họa bên trong một phương thức.
try {
    System.out.println("A. Trong try");
    System.out.println("B. Try thành công");
} finally {
    System.out.println("C. Trong finally");
}
System.out.println("D. Sau try");
~~~

Stdout:

~~~text
A. Trong try
B. Try thành công
C. Trong finally
D. Sau try
~~~

**Điểm cần nhớ:** `finally` không chỉ chạy khi có ngoại lệ. Nếu `try` đi hết bình thường, `finally` vẫn chạy như bước cuối trước khi rời cấu trúc. Vì vậy không nên coi `finally` là một loại `catch` đặc biệt hay nơi chỉ chứa logic xử lý lỗi.

## 3. Khi `try` phát sinh ngoại lệ và có `catch` phù hợp

Nếu ngoại lệ được ném từ phần thân `try`, Java tìm `catch` phù hợp theo thứ tự đã khai báo. Khi một `catch` bắt được và xử lý thành công:

1. Các câu lệnh còn lại trong phần `try` sau điểm ném bị bỏ qua.
2. Nhánh `catch` phù hợp được thực thi.
3. Sau khi `catch` hoàn tất bình thường, `finally` được thực thi.
4. Nếu `finally` cũng hoàn tất bình thường, chương trình tiếp tục sau toàn bộ cấu trúc.

Ví dụ:

~~~java
// Đoạn minh họa bên trong một phương thức.
try {
    System.out.println("Trong try");
    throw new IllegalArgumentException("dữ liệu sai");
} catch (IllegalArgumentException ex) {
    System.out.println("Đã bắt: " + ex.getMessage());
} finally {
    System.out.println("Trong finally");
}
System.out.println("Sau cấu trúc");
~~~

Stdout:

~~~text
Trong try
Đã bắt: dữ liệu sai
Trong finally
Sau cấu trúc
~~~

Ngoại lệ ở đây **đã được xử lý trong `catch`**. Vì thế, sau `finally`, luồng thực thi có thể tiếp tục bình thường. Điều này không có nghĩa `finally` đã xử lý ngoại lệ. Chính `catch` làm việc đó; `finally` chỉ thực hiện bước cuối khi rời cấu trúc.

Một `catch` khác cùng cấp không được chạy thêm chỉ vì `catch` thứ nhất đã kết thúc. Các `catch` là những nhánh lựa chọn để xử lý một ngoại lệ từ phạm vi `try`, không phải một chuỗi thực hiện tuần tự.

## 4. Khi `try` phát sinh ngoại lệ nhưng không có `catch` phù hợp

Nếu không có `catch` nào xử lý được ngoại lệ, **`finally` vẫn có cơ hội chạy trước khi ngoại lệ tiếp tục lan truyền** đến nơi gọi hoặc bộ xử lý bao ngoài.

~~~java
// Đoạn minh họa nằm trong phương thức không tự bắt IllegalStateException.
try {
    System.out.println("Trong try");
    throw new IllegalStateException("trạng thái sai");
} finally {
    System.out.println("Trong finally");
}
~~~

Khi thực hiện, chương trình in:

~~~text
Trong try
Trong finally
~~~

Sau đó, `IllegalStateException` vẫn đang được ném. Nếu phương thức gọi không bắt được, lỗi tiếp tục đi qua chuỗi lời gọi. Nếu không có bộ xử lý phù hợp, luồng có thể kết thúc bất thường và trình xử lý ngoại lệ chưa bắt có thể in stack trace ra stderr.

Trong trường hợp này, `finally` **không biến đường đi bất thường thành bình thường**. Nó chạy rồi cho phép nguyên nhân thoát ban đầu tiếp tục, miễn là bản thân `finally` hoàn tất bình thường.

Điều tương tự xảy ra khi `catch` được chọn nhưng **tự ném lại** ngoại lệ hoặc tạo ngoại lệ khác: `finally` được thực thi trên đường truyền ngoại lệ ra ngoài. Khác biệt là ngoại lệ cuối cùng được truyền có thể là lỗi gốc hoặc một ngoại lệ mới do code trong `catch` tạo ra.

## 5. Chương trình hoàn chỉnh: so sánh ba đường thực thi

Lưu chương trình sau vào `FinallyPathsDemo.java`:

~~~java
public class FinallyPathsDemo {
    static void run(String mode) {
        System.out.println("Bắt đầu: " + mode);
        try {
            System.out.println("Trong try");
            if (mode.equals("caught")) {
                throw new IllegalArgumentException("đầu vào sai");
            }
            if (mode.equals("uncaught")) {
                throw new IllegalStateException("trạng thái sai");
            }
            System.out.println("Try hoàn tất");
        } catch (IllegalArgumentException ex) {
            System.out.println("Catch: " + ex.getMessage());
        } finally {
            System.out.println("Finally: " + mode);
        }
        System.out.println("Sau try: " + mode);
    }

    public static void main(String[] args) {
        run("normal");
        run("caught");
        try {
            run("uncaught");
        } catch (IllegalStateException ex) {
            System.out.println("Catch ngoài: " + ex.getMessage());
        }
        System.out.println("Kết thúc main");
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 FinallyPathsDemo.java
java FinallyPathsDemo
~~~

Stdout chính xác:

~~~text
Bắt đầu: normal
Trong try
Try hoàn tất
Finally: normal
Sau try: normal
Bắt đầu: caught
Trong try
Catch: đầu vào sai
Finally: caught
Sau try: caught
Bắt đầu: uncaught
Trong try
Finally: uncaught
Catch ngoài: trạng thái sai
Kết thúc main
~~~

**Phân tích từng lượt:**

- **`normal`:** không có ngoại lệ. `try` hoàn tất, `finally` chạy, sau đó dòng `Sau try: normal` chạy.
- **`caught`:** `IllegalArgumentException` phát sinh rồi được `catch` cùng cấu trúc xử lý. `finally` chạy sau handler, và dòng `Sau try: caught` vẫn được in.
- **`uncaught`:** `IllegalStateException` không khớp `catch (IllegalArgumentException)`. `finally` vẫn chạy, rồi lỗi truyền lên `catch (IllegalStateException)` của `main`. Dòng `Sau try: uncaught` **không chạy**, vì phương thức `run` đã kết thúc bất thường.

Đây là minh họa cụ thể cho sự khác biệt giữa **có chạy `finally`** và **có tiếp tục sau cấu trúc `try` hay không**. Hai kết quả đó phải xét riêng.

## 6. Bảng diễn tiến luồng điều khiển

| Trường hợp | `catch` cùng cấu trúc | `finally` | Sau cấu trúc `try` |
| --- | --- | --- | --- |
| Phần `try` chạy hết bình thường | Không chạy | Chạy | Chạy nếu `finally` hoàn tất bình thường |
| `try` ném ngoại lệ, `catch` phù hợp xử lý xong | Chạy | Chạy | Chạy nếu `catch` và `finally` hoàn tất bình thường |
| `try` ném ngoại lệ, không có `catch` phù hợp | Không xử lý lỗi đó | Chạy trên đường thoát | Không chạy trong cùng đường thực thi nếu ngoại lệ vẫn lan truyền |
| `catch` ném lại hoặc ném ngoại lệ khác | Nhánh được chọn kết thúc bất thường | Chạy trước khi thoát | Không chạy nếu ngoại lệ vẫn lan truyền |
| `try` hoặc `catch` thực hiện `return` | Tùy đường đi | Chạy trước khi trả về | Không chạy trong phương thức đó nếu `return` vẫn có hiệu lực |
| Chính `finally` ném hoặc `return` | Tùy đường đi | Kết thúc bất thường | Kết quả đang chờ có thể bị thay thế |

Bảng giả định mã thật sự đi đến điểm rời cấu trúc và môi trường thực thi cho phép các bước Java tiếp tục. Trường hợp chấm dứt JVM cưỡng bức, luồng không bao giờ rời vòng lặp hoặc lỗi máy nghiêm trọng được bàn ở phần giới hạn.

## 7. `finally` còn chạy khi `try` hoặc `catch` có `return`

Một trường hợp dễ nhầm là `return` không phải ngoại lệ, nhưng vẫn là **nguyên nhân hoàn tất bất thường của câu lệnh `try` theo thuật ngữ JLS**: quyền điều khiển chuẩn bị rời phương thức. Trước khi việc trả về có hiệu lực, Java thực hiện khối `finally`.

Ví dụ:

~~~java
// Đoạn minh họa đặt trong một lớp.
static int ten() {
    try {
        return 10;
    } finally {
        System.out.println("Finally trước return");
    }
}
~~~

Nếu in `ten()`, thứ tự sẽ là:

~~~text
Finally trước return
10
~~~

Với `return expression;`, Java **đánh giá biểu thức trả về trước khi chạy `finally`**. Nếu `finally` hoàn tất bình thường, giá trị đã được tính sẽ được trả về. Điều này đặc biệt quan trọng khi `finally` sửa một **biến cục bộ** sau khi giá trị primitive đã được chuẩn bị trả về.

### Ví dụ Java hoàn chỉnh với `return`

Tệp `FinallyReturnDemo.java`:

~~~java
public class FinallyReturnDemo {
    static int compute() {
        int value = 10;
        try {
            System.out.println("Try chuẩn bị return");
            return value;
        } finally {
            value = 20;
            System.out.println("Finally đổi local thành " + value);
        }
    }

    static int catchAndReturn() {
        try {
            throw new IllegalArgumentException("lỗi");
        } catch (IllegalArgumentException ex) {
            System.out.println("Catch chuẩn bị return");
            return 3;
        } finally {
            System.out.println("Finally chạy sau catch");
        }
    }

    public static void main(String[] args) {
        System.out.println("Kết quả: " + compute());
        System.out.println("Kết quả catch: " + catchAndReturn());
    }
}
~~~

Lệnh:

~~~bash
javac -encoding UTF-8 FinallyReturnDemo.java
java FinallyReturnDemo
~~~

Stdout:

~~~text
Try chuẩn bị return
Finally đổi local thành 20
Kết quả: 10
Catch chuẩn bị return
Finally chạy sau catch
Kết quả catch: 3
~~~

Lý do `compute()` trả về `10` là biểu thức `return value` được đánh giá **trước** khi `finally` sửa biến `value` thành `20`. `finally` không tự đánh giá lại biểu thức trả về.

Lưu ý: đối với **kiểu tham chiếu**, giá trị trả về là tham chiếu đã tính. Nếu `finally` thay đổi trạng thái của **chính đối tượng** được tham chiếu, người gọi có thể quan sát thay đổi đó; nhưng việc gán biến cục bộ sang một tham chiếu khác trong `finally` không tự đổi tham chiếu đã được chọn để trả về.

Phương thức `catchAndReturn()` cho thấy một `return` trong `catch` cũng đi qua `finally` trước khi kết thúc phương thức.

## 8. Khi `finally` cũng phát sinh ngoại lệ: nguy cơ che lỗi gốc

Giả sử phần `try` ném lỗi A, nhưng trong quá trình dọn dẹp, `finally` lại ném lỗi B:

~~~java
// Đoạn minh họa một tình huống nguy hiểm.
try {
    throw new IllegalStateException("lỗi A");
} finally {
    throw new IllegalArgumentException("lỗi B");
}
~~~

Trong đường đi này, lỗi B từ `finally` **thay thế nguyên nhân thoát đang chờ** là lỗi A. Nếu bộ xử lý phía ngoài bắt một `RuntimeException`, nó thấy `IllegalArgumentException("lỗi B")`. Java không tự động nối lỗi A làm `cause` hoặc `suppressed` của lỗi B đối với `finally` thủ công.

Cũng vậy, `return` trong `finally` có thể **che cả ngoại lệ đang lan truyền**. Đó là lý do không nên viết `return` hoặc `throw` tùy tiện trong `finally`.

### Chương trình kiểm chứng hành vi che lỗi

Tệp `FinallyOverridesDemo.java`:

~~~java
public class FinallyOverridesDemo {
    static int unsafe() {
        try {
            throw new IllegalStateException("lỗi gốc");
        } finally {
            return 99;
        }
    }

    public static void main(String[] args) {
        System.out.println("Kết quả: " + unsafe());
    }
}
~~~

Chạy:

~~~bash
javac -encoding UTF-8 FinallyOverridesDemo.java
java FinallyOverridesDemo
~~~

Stdout chính xác:

~~~text
Kết quả: 99
~~~

Đây là ví dụ **cố ý minh họa cách viết không an toàn**. Ngoại lệ `IllegalStateException` bị bỏ qua khỏi đường lỗi ra ngoài vì `finally` kết thúc bằng `return 99`. Trong ứng dụng thật, cách này có thể làm một sự cố nghiêm trọng trông như một lượt thực thi thành công.

Đặc biệt với cleanup thủ công:

~~~java
// Đoạn minh họa có close() có thể ném IOException.
try {
    doWork();
} finally {
    resource.close();
}
~~~

Nếu cả `doWork()` và `resource.close()` cùng ném ngoại lệ, lỗi đóng tài nguyên có thể che lỗi từ `doWork()`. Khi quản lý `AutoCloseable`, ưu tiên **`try-with-resources`** để có cơ chế suppressed exception thay vì tự viết cleanup thiếu kiểm soát.

## 9. `finally` và `try-with-resources`: thời điểm và bảo toàn lỗi

Cú pháp `try-with-resources`:

~~~java
// Đoạn minh họa dùng đối tượng AutoCloseable do openResource() trả về.
try (Resource resource = openResource()) {
    work(resource);
} catch (IOException ex) {
    handle(ex);
} finally {
    afterWork();
}
~~~

Đoạn trên cần các khai báo `Resource`, `openResource`, `work`, `handle`, `afterWork` và import phù hợp. Với `try-with-resources`, Java tự đóng tài nguyên đã khởi tạo thành công khi rời phần thân `try`, theo thứ tự **ngược** với thứ tự khởi tạo thành công.

Trong cấu trúc trên, việc đóng tài nguyên diễn ra **trước khi** `catch` hoặc `finally` đi kèm của chính cấu trúc đó chạy. Vì vậy handler có thể nhận một ngoại lệ phát sinh từ body hoặc từ việc đóng tài nguyên.

Khi thân `try` ném ngoại lệ chính và `close()` cũng ném, ngoại lệ đóng thường được thêm vào danh sách **suppressed exceptions** của ngoại lệ chính. Xem `ex.getSuppressed()` nếu cần chẩn đoán nhiều lỗi cùng lúc.

| Cơ chế | Lỗi từ phần thân và lỗi đóng tài nguyên |
| --- | --- |
| `try` + `finally { resource.close(); }` viết thủ công | Lỗi `close()` có thể thay thế lỗi gốc nếu chính `finally` ném |
| `try-with-resources` | Lỗi `close()` thường được gắn làm suppressed của lỗi chính |
| `catch` bọc lỗi bằng `new ...(..., cause)` | Lỗi gốc được lưu như **cause**, khác với suppressed |

`try-with-resources` phù hợp cho tài nguyên `AutoCloseable` như stream, reader hoặc kết nối. `finally` vẫn hữu ích cho thao tác kết thúc khác, ví dụ khôi phục trạng thái hoặc ghi nhận thời gian hoàn tất, nhưng nên giữ phần thân gọn và hạn chế các thao tác có thể thất bại khi một lỗi khác đang truyền ra.

Không nên tự đóng tài nguyên hai lần nếu đã để `try-with-resources` chịu trách nhiệm; đóng lặp có thể thừa hoặc gây hệ quả tùy loại tài nguyên.

## 10. `finally` chạy sau `catch`, kể cả khi `catch` ném tiếp

Ví dụ:

~~~java
// Đoạn minh họa nằm trong phương thức có thể ném IllegalStateException.
try {
    throw new IllegalArgumentException("đầu vào sai");
} catch (IllegalArgumentException ex) {
    System.out.println("Trong catch");
    throw new IllegalStateException("xử lý thất bại", ex);
} finally {
    System.out.println("Trong finally");
}
~~~

Stdout trước khi ngoại lệ mới tiếp tục truyền:

~~~text
Trong catch
Trong finally
~~~

Ở đây, `IllegalStateException` được tạo trong `catch`, lấy `IllegalArgumentException` làm nguyên nhân. Sau `finally` (nếu nó hoàn tất bình thường), ngoại lệ mới tiếp tục đến cấu trúc `try` bao ngoài hoặc phương thức gọi.

Các `catch` cùng cấp của chính cấu trúc này **không bắt ngoại lệ mới ném từ một `catch` khác**. Khi cần xử lý lỗi phát sinh trong handler, đặt một `try/catch` lồng trong handler hoặc một `try` thực sự bao quanh toàn bộ cấu trúc.

Điểm này củng cố hai chức năng riêng:

- `catch` chọn cách xử lý ngoại lệ phù hợp và có thể kết thúc bình thường hoặc ném tiếp.
- `finally` thực hiện bước cuối của cấu trúc bất kể nhánh `catch` được chọn đã hoàn tất theo cách nào, miễn quá trình thực thi còn tiếp tục tới bước đó.

## 11. Các khối `finally` lồng nhau chạy theo thứ tự nào?

Trong các cấu trúc `try` lồng nhau, một ngoại lệ phát sinh ở tầng sâu có thể đi qua nhiều `finally`. Về mặt luồng điều khiển, `finally` ở **cấu trúc bên trong** chạy trước, rồi tới các `finally` bao ngoài khi luồng tiếp tục rời từng cấu trúc.

~~~java
// Đoạn minh họa bên trong một phương thức.
try {
    try {
        System.out.println("Trong try trong");
        throw new IllegalStateException("lỗi");
    } finally {
        System.out.println("Finally trong");
    }
} catch (IllegalStateException ex) {
    System.out.println("Catch ngoài");
} finally {
    System.out.println("Finally ngoài");
}
~~~

Stdout:

~~~text
Trong try trong
Finally trong
Catch ngoài
Finally ngoài
~~~

Giải thích: ngoại lệ xuất hiện trong `try` bên trong, không có handler ở chính cấu trúc bên trong nên `finally` của nó chạy rồi lỗi được chuyển đến `catch` của tầng ngoài. Sau khi handler ngoài hoàn tất bình thường, `finally` ngoài thực thi.

Nếu một `finally` ở tầng sâu ném ngoại lệ mới, tầng ngoài có thể nhận ngoại lệ mới đó; điều này làm việc đọc stack trace và chuỗi nguyên nhân trở nên quan trọng hơn khi debug.

## 12. `finally` có được bảo đảm chạy trong mọi hoàn cảnh không?

**Không phải trong mọi hoàn cảnh tuyệt đối.** Theo ngữ nghĩa của Java, khi luồng điều khiển **rời một cấu trúc `try` theo các đường được đặc tả** (thành công, ngoại lệ, `return`, `break`, `continue`), `finally` liên quan được thực thi trước khi hoàn tất việc rời cấu trúc. Nhưng điều đó không đảm bảo `finally` luôn chạy dù chương trình hoặc môi trường đã ngừng thực thi.

Ví dụ về giới hạn:

- **`System.exit(...)` hoặc `Runtime.halt(...)`:** có thể làm JVM kết thúc mà không tiếp tục thực hiện các `finally` của những lời gọi đang hoạt động; `System.exit` thực hiện cơ chế shutdown, nhưng không quay lại unwinding các stack như khi `throw` thông thường.
- **Tiến trình bị kết thúc cưỡng bức, JVM bị crash, mất điện:** không thể trông cậy vào `finally` để hoàn tất một thao tác bên ngoài.
- **Luồng không bao giờ rời `try`** (chẳng hạn bị kẹt vô hạn): `finally` chưa có cơ hội chạy.
- **Lỗi cực kỳ nghiêm trọng của VM hoặc môi trường:** không thể luôn bảo đảm các bước cleanup tiếp tục.

Vì vậy, `finally` phù hợp cho cleanup **trong vòng đời thực thi Java bình thường**, nhưng không thay thế cơ chế lưu trữ bền vững, transaction hoặc thiết kế chịu lỗi khi cần đảm bảo hệ thống phục hồi sau crash.

Tránh nhầm với các lỗi runtime thông thường như `NullPointerException`: chúng vẫn là đối tượng `Throwable`; khi được ném và Java thực hiện đường thoát có thể xử lý, `finally` liên quan vẫn chạy.

## 13. `finally` trong vòng lặp với `break` và `continue`

Khi phần thân `try` được đặt trong vòng lặp, `break` hoặc `continue` có thể yêu cầu chuyển quyền điều khiển khỏi khối. Nếu đường chuyển này rời cấu trúc `try`, `finally` vẫn chạy trước khi vòng lặp thoát hoặc bắt đầu lượt tiếp theo.

~~~java
// Đoạn minh họa bên trong một phương thức.
for (int i = 0; i < 3; i++) {
    try {
        if (i == 1) {
            continue;
        }
        System.out.println("Try: " + i);
    } finally {
        System.out.println("Finally: " + i);
    }
}
~~~

Stdout:

~~~text
Try: 0
Finally: 0
Finally: 1
Try: 2
Finally: 2
~~~

Ở `i == 1`, câu `continue` bỏ qua phần in của `try`, nhưng `finally` vẫn được thực hiện trước khi chuyển sang lượt `i == 2`. Với `break`, `finally` cũng sẽ chạy trước khi rời vòng lặp.

Đây là lý do khi đọc code có `return`, `break`, `continue` hoặc `throw`, cần tính cả tác động của `finally` trước khi quyết định câu lệnh tiếp theo nào thật sự chạy.

## 14. Lỗi phổ biến khi sử dụng `finally`

| Hiểu nhầm hoặc lỗi thiết kế | Hệ quả | Cách xử lý |
| --- | --- | --- |
| Nghĩ `finally` chỉ chạy khi có lỗi | Bỏ sót công việc cuối trên đường thành công | Hiểu rằng `finally` chạy khi rời `try` cả bình thường lẫn bất thường |
| Nghĩ `finally` tự bắt ngoại lệ chưa xử lý | Ngoại lệ vẫn lan ra ngoài sau `finally` | Dùng `catch` phù hợp ở nơi có thể xử lý |
| Nghĩ các câu lệnh sau `throw` trong `try` vẫn chạy trước `finally` | Đọc sai luồng điều khiển | Đi từ điểm ném tới handler/`finally`, bỏ qua đường bình thường còn lại |
| Đặt `return` trong `finally` | Có thể thay thế `return` hoặc che ngoại lệ từ `try`/`catch` | Tránh `return` trong `finally` |
| Thực hiện cleanup dễ ném lỗi trong `finally` thủ công | Có thể che lỗi ban đầu | Ưu tiên `try-with-resources` hoặc xử lý lỗi cleanup có chủ đích |
| Nghĩ thay đổi biến cục bộ trong `finally` sẽ làm `return value` trả giá trị mới | `return` đã đánh giá biểu thức trước đó | Xác định giá trị chờ trả và phân biệt kiểu primitive/tham chiếu |
| Bỏ qua `finally` của các `try` lồng nhau | Đoán sai thứ tự log/dọn dẹp | Theo dõi thứ tự rời khối từ trong ra ngoài |
| Coi `finally` là nơi phù hợp để xử lý mọi ngoại lệ | Mất phân loại và cách phục hồi từng lỗi | Dùng `catch` cho xử lý, `finally` cho công việc cuối |
| Cho rằng `finally` chắc chắn chạy dù gọi `System.exit` | Có thể bỏ lỡ cleanup khi JVM kết thúc | Dùng cơ chế quản lý vòng đời phù hợp khi cần shutdown |
| Chỉ thử đường thành công | Không phát hiện lỗi cleanup hoặc ngoại lệ bị che | Kiểm thử cả thành công, caught, uncaught và lỗi phát sinh trong cleanup |

Nên giữ công việc trong `finally` **ngắn, dễ dự đoán và ít khả năng thất bại**. Nếu nghiệp vụ cần rollback thực sự hoặc tính nguyên tử của dữ liệu, hãy dùng transaction hoặc một cơ chế phù hợp; `finally` chỉ kiểm soát luồng điều khiển Java.

## 15. Tự kiểm tra kiến thức

**Câu 1.** `try` chạy thành công đến hết. `finally` có chạy không?

**Trả lời:** Có, nếu luồng điều khiển rời cấu trúc bình thường và môi trường tiếp tục thực thi.

**Câu 2.** `try` ném `IllegalArgumentException`, `catch` phù hợp xử lý xong. Thứ tự là gì?

**Trả lời:** Điểm ném trong `try` → `catch` phù hợp → `finally` → câu lệnh sau toàn bộ cấu trúc (nếu `finally` hoàn tất bình thường).

**Câu 3.** `try` ném `IllegalStateException` nhưng không có `catch` phù hợp. `finally` có làm lỗi biến mất không?

**Trả lời:** Không. `finally` chạy trước khi lỗi tiếp tục lan ra ngoài, miễn `finally` hoàn tất bình thường.

**Câu 4.** `return 10;` trong `try`, sau đó `finally` gán biến cục bộ ban đầu thành `20`. Giá trị trả về là gì?

**Trả lời:** Nếu biểu thức `return` đã được tính là `10` và `finally` hoàn tất bình thường, kết quả là `10`. Với đối tượng tham chiếu cần phân biệt việc thay đổi chính đối tượng.

**Câu 5.** Nếu `finally` có `return 99;` trong lúc `try` ném ngoại lệ, điều gì có thể xảy ra?

**Trả lời:** `return 99` có thể thay thế nguyên nhân thoát từ `try`, làm ngoại lệ bị che.

**Câu 6.** Nếu `finally` ném IOException trong khi `try` đang ném lỗi khác, lỗi nào có thể truyền ra?

**Trả lời:** Ngoại lệ từ `finally` có thể thay thế lỗi trước đó khi sử dụng cleanup thủ công. Nếu được quản lý bằng `try-with-resources`, lỗi đóng tài nguyên có cơ chế suppressed exception.

**Câu 7.** `finally` của `try` lồng nhau chạy trước hay sau `finally` ngoài?

**Trả lời:** Theo thứ tự rời cấu trúc, `finally` của tầng trong thường chạy trước tầng ngoài.

**Câu 8.** `continue` trong `try` có bỏ qua `finally` không?

**Trả lời:** Không, `finally` liên quan chạy trước khi chuyển sang lượt lặp tiếp theo, nếu việc chuyển đó rời cấu trúc.

**Câu 9.** Có nên dùng `finally` để đóng mọi tài nguyên `AutoCloseable` bằng tay không?

**Trả lời:** Thường nên ưu tiên `try-with-resources` vì nó quản lý việc đóng và giúp bảo toàn lỗi đóng qua suppressed exceptions.

**Câu 10.** `finally` có chắc chắn chạy sau `System.exit(0)` không?

**Trả lời:** Không; không nên phụ thuộc vào `finally` để cleanup khi JVM bị kết thúc.

## Tài liệu đối chiếu

- [Java Language Specification 17, §14.20.2 — Execution of try-finally and try-catch-finally](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.2): quy tắc `finally` khi `try` hoàn tất bình thường hoặc bất thường.
- [Java Language Specification 17, §14.20.3 — try-with-resources](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3): đóng tài nguyên, suppressed exceptions và thứ tự thực thi.
- [Java Language Specification 17, §14.17 — The return Statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.17): đánh giá biểu thức `return` trước khi rời phương thức.
- [Java SE 17 API — Throwable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Throwable.html): `getCause()`, `getSuppressed()` và stack trace.
- [Oracle Java Tutorials — The finally Block](https://docs.oracle.com/javase/tutorial/essential/exceptions/finally.html): giải thích trực quan về cleanup (ví dụ trong tutorial lịch sử, quy tắc nền tảng vẫn áp dụng).

**Ghi nhớ:** khi `try` hoàn tất bình thường, `finally` chạy trước khi chương trình đi tiếp; khi `try` ném ngoại lệ, `finally` chạy trên đường xử lý hoặc lan truyền lỗi. Nếu chính `finally` kết thúc bất thường, nó có thể thay đổi toàn bộ kết quả. Vì vậy hãy dùng `catch` để xử lý ngoại lệ, `finally` cho công việc cuối, và `try-with-resources` để quản lý tài nguyên `AutoCloseable` khi phù hợp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="finally-003">Finally khi return, break hoặc continue</a>

<details>
<summary>Xem chi tiết</summary>

# Finally khi `return`, `break` hoặc `continue` trong Java

Trong Java, khối `finally` gắn với một cấu trúc `try` được thiết kế để thực hiện các công việc cuối cùng khi luồng điều khiển **rời khỏi cấu trúc `try/catch`**. Điểm dễ nhầm là: việc rời khỏi `try` không chỉ xảy ra khi chạy hết các lệnh bình thường hoặc khi có ngoại lệ; nó còn xảy ra khi mã thực thi `return`, `break` hoặc `continue`.

**Quy tắc cốt lõi:** Nếu luồng đang ở trong một `try` có `finally`, khi `try` hoặc một `catch` liên quan bắt đầu hoàn tất vì `return`, `break`, `continue` hay ngoại lệ, Java thông thường thực thi `finally` **trước khi hoàn tất việc chuyển điều khiển ra ngoài**. Sau khi `finally` kết thúc bình thường, tác động đang chờ của `return`, `break`, `continue` hoặc ngoại lệ tiếp tục được thực hiện.

Cần hiểu cả ngoại lệ đặc biệt: nếu **chính `finally` hoàn tất bất thường** (ví dụ `throw` hoặc `return` trong `finally`), nó có thể thay thế nguyên nhân hoàn tất đang chờ. Vì vậy, `finally` không phải lúc nào cũng “chỉ dọn dẹp rồi giữ nguyên kết quả”.

Bài này thuộc Java Core – Exception, nhóm **Khối finally** và tập trung cụ thể vào ba lệnh chuyển điều khiển `return`, `break` và `continue`.

## 1. Ôn lại cấu trúc và phạm vi của `finally`

Cú pháp phổ biến:

~~~java
try {
    // Công việc chính.
} catch (SomeException e) {
    // Xử lý loại ngoại lệ tương ứng.
} finally {
    // Dọn dẹp hoặc hành động cuối cùng.
}
~~~

Có thể sử dụng `try/finally` **không có `catch`**:

~~~java
try {
    perform();
} finally {
    cleanup();
}
~~~

Hai đoạn là **cú pháp minh họa**, giả định các phương thức `perform()` và `cleanup()` đã tồn tại. Khối `finally` thuộc về cấu trúc `try` cụ thể đó, không phải một hàm toàn cục “luôn chạy khi cả ứng dụng thoát”.

Trong điều kiện Java chạy thông thường, `finally` được thực thi khi rời vùng `try/catch` theo các đường:

| Đường rời cấu trúc | `finally` thông thường chạy? | Điều diễn ra tiếp theo nếu `finally` hoàn tất bình thường |
| --- | --- | --- |
| Chạy hết khối `try` | Có | Chương trình tiếp tục sau `try/catch/finally` |
| Ngoại lệ được bắt và `catch` kết thúc bình thường | Có | Tiếp tục sau cấu trúc |
| Ngoại lệ chưa được bắt ở đây | Có | Tiếp tục lan truyền đến handler bên ngoài |
| `return` trong `try` hoặc `catch` | Có | Hoàn tất trả về từ phương thức |
| `break` rời vòng lặp/switch từ bên trong vùng `try` | Có | Chuyển tới vị trí sau cấu trúc bị `break` |
| `continue` tới vòng lặp kế tiếp từ bên trong vùng `try` | Có | Thực hiện bước tiếp theo theo loại vòng lặp |

**Cụm “thông thường” quan trọng:** không có cam kết `finally` sẽ chạy khi JVM bị kết thúc cưỡng bức, tiến trình bị hệ điều hành dừng, `Runtime.halt` chấm dứt VM, mất điện, hoặc luồng không bao giờ rời vùng `try`. Những giới hạn này được giải thích ở phần cuối.

## 2. `return` trong `try`: `finally` chạy trước khi caller nhận giá trị

Ví dụ:

~~~java
static int calculate() {
    try {
        System.out.println("try: prepare return");
        return 42;
    } finally {
        System.out.println("finally: cleanup");
    }
}
~~~

Nếu gọi `System.out.println("result=" + calculate());`, stdout là:

~~~text
try: prepare return
finally: cleanup
result=42
~~~

Thứ tự là:

1. Java bắt đầu đánh giá biểu thức sau `return` (`42` trong ví dụ).
2. Phương thức chuẩn bị hoàn tất bằng cách trả giá trị.
3. Trước khi thoát, Java chạy `finally`.
4. `finally` kết thúc bình thường.
5. Giá trị `42` mới được trả về cho caller; caller in `result=42`.

Không phải cứ gặp `return` là “bỏ qua mọi câu lệnh còn lại” theo nghĩa bỏ qua `finally`. Các câu lệnh **sau `return` trong cùng khối** không thể tiếp tục bình thường, nhưng `finally` đi kèm vẫn được xử lý theo luật ngôn ngữ.

### Biểu thức `return` được đánh giá trước `finally`

Xem ví dụ:

~~~java
static int snapshot() {
    int value = 3;
    try {
        return value;
    } finally {
        value = 9;
        System.out.println("finally value=" + value);
    }
}
~~~

Nếu gọi `System.out.println("snapshot=" + snapshot());`:

~~~text
finally value=9
snapshot=3
~~~

Lý do: biểu thức `return value;` được đánh giá **trước** khi `finally` chạy. Với kiểu nguyên thủy `int`, giá trị dự kiến trả đã là `3`. Việc gán lại biến cục bộ `value = 9` trong `finally` không khiến biểu thức `return` cũ được đánh giá lại, nên caller vẫn nhận `3`.

Điều này không có nghĩa Java ngăn `finally` thay đổi trạng thái mọi đối tượng liên quan: trường hợp trả tham chiếu sẽ được xét riêng.

## 3. Trả về đối tượng: `finally` có thể thay đổi trạng thái của đối tượng

Một tham chiếu cũng được đánh giá trước khi `finally` chạy. Nhưng nếu tham chiếu trỏ tới **đối tượng có thể thay đổi** và `finally` sửa chính đối tượng đó, caller có thể quan sát được thay đổi.

~~~java
static StringBuilder createText() {
    StringBuilder result = new StringBuilder("A");
    try {
        return result;
    } finally {
        result.append("B");
    }
}
~~~

Sau `System.out.println(createText());`, kết quả là:

~~~text
AB
~~~

Diễn giải:

- Biểu thức `return result;` đã chọn tham chiếu đến `StringBuilder`.
- `finally` gọi `append("B")` trên **cùng đối tượng**.
- Caller nhận tham chiếu đã chọn, nay trỏ đến đối tượng có nội dung `"AB"`.

Phân biệt **gán lại biến tham chiếu** và **thay đổi đối tượng**:

~~~java
static StringBuilder replaceLocalReference() {
    StringBuilder result = new StringBuilder("A");
    try {
        return result;
    } finally {
        result = new StringBuilder("B");
    }
}
~~~

Ở phương thức trên, caller vẫn nhận đối tượng `"A"` vì tham chiếu trả về đã được xác định trước `finally`; phép gán `result = ...` chỉ thay đổi biến cục bộ.

Đây là lý do không nên diễn giải cứng nhắc “`finally` không ảnh hưởng kết quả trả về”. Nó **không đánh giá lại giá trị trả của biểu thức ban đầu**, nhưng hoàn toàn có thể thay đổi trạng thái nhìn thấy từ ngoài, hoặc tự thay thế kết quả bằng một lệnh chuyển điều khiển khác.

## 4. `break` bên trong `try`: `finally` chạy trước khi rời vòng lặp

`break` không nhảy qua `finally`. Nếu một nhánh ở bên trong `try` thực hiện `break` để thoát khỏi vòng lặp bao quanh, Java thực thi `finally` liên quan trước khi chuyển điều khiển tới vị trí sau vòng lặp.

~~~java
static void breakExample() {
    for (int i = 0; i < 3; i++) {
        try {
            System.out.println("try " + i);
            if (i == 1) {
                break;
            }
        } finally {
            System.out.println("finally " + i);
        }
    }
    System.out.println("after loop");
}
~~~

Kết quả:

~~~text
try 0
finally 0
try 1
finally 1
after loop
~~~

Ở `i = 0`, thân `try` chạy xong bình thường; `finally` chạy rồi vòng lặp chuyển tới lần kế. Ở `i = 1`, `break` được thực hiện, nhưng `finally 1` **vẫn in trước** khi thoát khỏi vòng lặp. `i = 2` không xảy ra.

**Điểm cần nhớ:** `break` xác định đích nhảy theo cú pháp vòng lặp/switch; `finally` là phần cần thực thi trong quá trình rời các vùng `try` mà đường nhảy đó đi qua. `finally` không “hủy” `break` khi hoàn tất bình thường.

### `break` có nhãn

Trong vòng lặp lồng nhau, `break label;` có thể thoát nhiều tầng. Nếu đường thoát rời nhiều khối `try` có `finally`, chúng được thực thi **từ vùng trong ra vùng ngoài**.

~~~java
static void labeledBreak() {
    OUTER:
    for (int i = 0; i < 2; i++) {
        try {
            for (int j = 0; j < 2; j++) {
                try {
                    System.out.println("body " + i + "," + j);
                    break OUTER;
                } finally {
                    System.out.println("inner finally");
                }
            }
        } finally {
            System.out.println("outer finally");
        }
    }
    System.out.println("after labeled break");
}
~~~

Đây là phương thức Java hợp lệ. Khi gọi một lần, stdout là:

~~~text
body 0,0
inner finally
outer finally
after labeled break
~~~

Vùng `try` trong được rời trước, sau đó vùng `try` ngoài. Vì đích của `break OUTER` nằm ngoài cả hai, cả hai `finally` chạy trước khi chương trình in dòng sau vòng lặp.

## 5. `continue` bên trong `try`: `finally` chạy trước vòng lặp kế tiếp

`continue` bỏ phần thân vòng lặp còn lại của lượt hiện tại để chuyển sang tiến trình của lượt tiếp theo. Nếu `continue` xuất phát từ một `try` có `finally`, `finally` liên quan chạy **trước** bước cập nhật/kiểm tra để sang lượt mới.

~~~java
static void continueExample() {
    for (int i = 0; i < 3; i++) {
        try {
            System.out.println("try " + i);
            if (i < 2) {
                continue;
            }
            System.out.println("body " + i);
        } finally {
            System.out.println("finally " + i);
        }
    }
    System.out.println("after loop");
}
~~~

Kết quả:

~~~text
try 0
finally 0
try 1
finally 1
try 2
body 2
finally 2
after loop
~~~

Ở `i = 0` và `i = 1`, lệnh `continue` bỏ qua `System.out.println("body " + i)`, nhưng không bỏ qua `finally`. Ở `i = 2`, điều kiện `i < 2` sai, chương trình in `body 2` rồi vẫn chạy `finally 2`.

Với vòng `for (init; condition; update)`, sau khi `finally` hoàn tất bình thường trên đường `continue`, Java thực hiện bước **update** rồi kiểm tra điều kiện cho lần lặp kế. Với `while` hoặc `do/while`, bước tiếp theo tương ứng là đánh giá điều kiện vòng lặp theo quy tắc của loại vòng đó.

### `continue` có nhãn

`continue OUTER;` chuyển tới lần lặp tiếp theo của vòng lặp được gắn nhãn `OUTER`, không nhất thiết vòng gần nhất. Nếu đường nhảy rời một hoặc nhiều cấu trúc `try`, các `finally` liên quan vẫn được thực thi trước khi chuyển tới bước của vòng lặp đích.

~~~java
static void labeledContinue() {
    OUTER:
    for (int i = 0; i < 2; i++) {
        try {
            for (int j = 0; j < 2; j++) {
                try {
                    System.out.println("body " + i + "," + j);
                    continue OUTER;
                } finally {
                    System.out.println("inner finally " + i);
                }
            }
        } finally {
            System.out.println("outer finally " + i);
        }
    }
}
~~~

Kết quả khi gọi một lần:

~~~text
body 0,0
inner finally 0
outer finally 0
body 1,0
inner finally 1
outer finally 1
~~~

Không có lần `j = 1` vì `continue OUTER` bỏ phần còn lại của vòng trong và chuyển thẳng đến lượt tiếp theo của `OUTER`, **sau khi** các `finally` cần thiết đã chạy.

## 6. Chương trình Java 17+ hoàn chỉnh: cả `return`, `break` và `continue`

Tạo tệp **`FinallyControlFlowDemo.java`**:

~~~java
public class FinallyControlFlowDemo {
    public static void main(String[] args) {
        System.out.println("== RETURN ==");
        System.out.println("return result: " + returnNormally());
        System.out.println("snapshot result: " + returnSnapshot());

        System.out.println("== BREAK ==");
        breakDemo();

        System.out.println("== CONTINUE ==");
        continueDemo();

        System.out.println("== EXCEPTION ==");
        try {
            throwingDemo();
        } catch (IllegalStateException e) {
            System.out.println("caught: " + e.getMessage());
        }

        System.out.println("== OVERRIDE ==");
        System.out.println("overridden result: " + overrideReturn());
    }

    static int returnNormally() {
        try {
            System.out.println("try return");
            return 10;
        } finally {
            System.out.println("finally after return");
        }
    }

    static int returnSnapshot() {
        int n = 3;
        try {
            return n;
        } finally {
            n = 9;
            System.out.println("finally local=" + n);
        }
    }

    static void breakDemo() {
        for (int i = 0; i < 3; i++) {
            try {
                System.out.println("break try " + i);
                if (i == 1) {
                    break;
                }
            } finally {
                System.out.println("break finally " + i);
            }
        }
        System.out.println("break exit");
    }

    static void continueDemo() {
        for (int i = 0; i < 3; i++) {
            try {
                System.out.println("continue try " + i);
                if (i < 2) {
                    continue;
                }
                System.out.println("continue body " + i);
            } finally {
                System.out.println("continue finally " + i);
            }
        }
        System.out.println("continue exit");
    }

    static void throwingDemo() {
        try {
            throw new IllegalStateException("boom");
        } finally {
            System.out.println("finally before propagation");
        }
    }

    // Chỉ để minh họa lỗi thiết kế: đừng return trong finally.
    static int overrideReturn() {
        try {
            return 1;
        } finally {
            System.out.println("finally overrides return");
            return 2;
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac FinallyControlFlowDemo.java
java FinallyControlFlowDemo
~~~

**Stdout xác định:**

~~~text
== RETURN ==
try return
finally after return
return result: 10
finally local=9
snapshot result: 3
== BREAK ==
break try 0
break finally 0
break try 1
break finally 1
break exit
== CONTINUE ==
continue try 0
continue finally 0
continue try 1
continue finally 1
continue try 2
continue body 2
continue finally 2
continue exit
== EXCEPTION ==
finally before propagation
caught: boom
== OVERRIDE ==
finally overrides return
overridden result: 2
~~~

### Đối chiếu với các quy tắc

**`returnNormally()`:** Phương thức chuẩn bị trả `10`; trước khi caller in kết quả, nó chạy khối `finally`. Không có `return` khác trong `finally` nên giá trị `10` được giữ nguyên.

**`returnSnapshot()`:** Giá trị nguyên thủy `3` được xác định trước `finally`; sửa biến cục bộ thành `9` và in giá trị `9` không thay đổi kết quả mà caller nhận.

**`breakDemo()`:** Khi `i = 1`, lệnh `break` yêu cầu rời vòng `for` nhưng `finally` ở chính lượt đó vẫn chạy. Không có `break try 2` vì vòng đã kết thúc.

**`continueDemo()`:** Hai lượt đầu có `continue` nên không in `continue body 0` hoặc `continue body 1`; cả hai vẫn in `finally` trước khi lặp tiếp.

**`throwingDemo()`:** Ngoại lệ `IllegalStateException` được ném trong `try`; `finally` in dòng dọn dẹp trước khi `catch` bên ngoài nhận ngoại lệ. Không có `catch` bên trong phương thức `throwingDemo`.

**`overrideReturn()`:** Cả `return 1` trong `try` và `return 2` trong `finally` đều là cú pháp hợp lệ, nhưng lệnh `return 2` của `finally` **thay thế** kết quả dự kiến `1`. Đây là phản ví dụ cố ý; không nên dùng kiểu này trong ứng dụng thật.

## 7. Khi `finally` tự `return` hoặc `throw`: nó có thể ghi đè luồng đang chờ

Đây là ngoại lệ quan trọng đối với câu nói đơn giản “`finally` luôn chạy rồi quay lại luồng ban đầu”.

Nếu `finally` **kết thúc bình thường**, nguyên nhân hoàn tất trước đó được giữ lại:

~~~java
static int keepsReturn() {
    try {
        return 7;
    } finally {
        System.out.println("cleanup");
    }
}
~~~

Nếu `finally` **tự hoàn tất bất thường** bằng `return` hoặc `throw`, kết quả từ `try` có thể bị thay thế.

### Trường hợp A: `return` trong `finally` ghi đè `return` từ `try`

~~~java
static int replacesReturn() {
    try {
        return 7;
    } finally {
        return 9;
    }
}
~~~

Phương thức trả `9`, không phải `7`. Lệnh `return` của `finally` là lý do hoàn tất được giữ lại. Trong công việc thực tế, **tránh viết `return` trong `finally`** vì nó làm luồng điều khiển khó suy luận và có thể che mất thất bại.

### Trường hợp B: `return` trong `finally` che ngoại lệ

~~~java
static int hidesFailure() {
    try {
        throw new IllegalStateException("original failure");
    } finally {
        return 9;
    }
}
~~~

Đây là **một phương thức Java hợp lệ nhưng cực kỳ không nên dùng**. Nếu gọi `hidesFailure()`, phương thức có thể trả `9` mà ngoại lệ gốc `IllegalStateException` không còn được truyền ra ngoài. Điều này làm người gọi tưởng thao tác thành công và mất tín hiệu lỗi.

### Trường hợp C: `throw` trong `finally` thay thế `return`

~~~java
static int throwsInsteadOfReturning() {
    try {
        return 7;
    } finally {
        throw new IllegalStateException("cleanup failed");
    }
}
~~~

Phương thức không trả `7`. Nó ném `IllegalStateException("cleanup failed")`. Nếu caller không bắt, ngoại lệ tiếp tục lan truyền.

### Trường hợp D: `throw` trong `finally` thay thế ngoại lệ từ `try`

~~~java
static void replacesFailure() {
    try {
        throw new IllegalArgumentException("original failure");
    } finally {
        throw new IllegalStateException("cleanup failure");
    }
}
~~~

Ngoại lệ thoát ra là `IllegalStateException("cleanup failure")`. Ngoại lệ gốc không tự được lưu thành `cause` hoặc `suppressed` chỉ bởi vì hai lệnh `throw` xảy ra trong `try/finally` thủ công. Nếu cần giữ nhiều lỗi, hãy tổ chức cơ chế xử lý và liên kết nguyên nhân phù hợp; với tài nguyên `AutoCloseable`, try-with-resources thường giải quyết việc giữ lỗi tốt hơn.

**Quy tắc tổng quát:** Khi khối `finally` hoàn tất bất thường vì một nguyên nhân mới, nguyên nhân đó có thể **thay thế** lý do rời `try/catch` đang chờ, dù lý do cũ là `return`, `break`, `continue` hay một ngoại lệ.

## 8. `finally` có thể thay đổi mục tiêu `break` hoặc `continue` không?

Cần phân biệt hai trường hợp.

- Nếu `finally` chỉ chạy các lệnh bình thường và kết thúc, yêu cầu `break` hoặc `continue` ban đầu sẽ được thực hiện tiếp.
- Nếu `finally` phát sinh ngoại lệ, trả khỏi phương thức, hoặc tự chuyển điều khiển theo cách hợp lệ, luồng đang chờ có thể bị thay thế.

Ví dụ:

~~~java
static void breakCancelledByException() {
    for (int i = 0; i < 1; i++) {
        try {
            break;
        } finally {
            throw new IllegalStateException("cleanup failed");
        }
    }
}
~~~

Đây là phương thức minh họa hợp lệ: `break` được yêu cầu nhưng `finally` ném ngoại lệ; phương thức kết thúc bất thường và caller nhận `IllegalStateException` nếu không có handler ở đây. Vì vậy không thể diễn giải `finally` như một “hàm callback luôn trả về” trong mọi trường hợp.

Ngược lại, nếu `finally` chỉ ghi log:

~~~java
static void breakPreserved() {
    while (true) {
        try {
            break;
        } finally {
            System.out.println("cleanup");
        }
    }
    System.out.println("done");
}
~~~

Thứ tự là `cleanup` rồi `done`. `finally` không thay đổi mục tiêu `break`.

Một ví dụ tương tự với `continue`:

~~~java
static void continuePreserved() {
    for (int i = 0; i < 2; i++) {
        try {
            continue;
        } finally {
            System.out.println("finally " + i);
        }
    }
    System.out.println("done");
}
~~~

Stdout:

~~~text
finally 0
finally 1
done
~~~

Không cần gán giá trị trả về cho `continue`; nó điều hướng vòng lặp. `finally` chạy trước mỗi bước tiếp theo.

## 9. `finally` lồng nhau: chạy từ trong ra ngoài

Khi `return`, `break`, `continue` hoặc ngoại lệ phải rời nhiều vùng `try`, Java thực hiện các khối `finally` liên quan theo trật tự tháo dần vùng lồng nhau.

~~~java
static int nestedFinally() {
    try {
        try {
            System.out.println("inner try");
            return 5;
        } finally {
            System.out.println("inner finally");
        }
    } finally {
        System.out.println("outer finally");
    }
}
~~~

Kết quả khi gọi `nestedFinally()`:

~~~text
inner try
inner finally
outer finally
~~~

Sau đó caller nhận `5` nếu cả hai `finally` hoàn tất bình thường.

Trong một chương trình có nhiều lời gọi phương thức, luồng ném ngoại lệ còn có thể làm Java chạy các `finally` của **các phương thức đang thoát khỏi stack** trước khi tới handler bên ngoài. Đối với `return`, chỉ các vùng `try` liên quan trên đường rời phương thức hiện tại được thực hiện; `return` không tự đi ngược qua caller.

## 10. `finally` với `catch` và ngoại lệ

Ví dụ:

~~~java
static int recover(String text) {
    try {
        return Integer.parseInt(text);
    } catch (NumberFormatException e) {
        System.out.println("parse failed");
        return -1;
    } finally {
        System.out.println("cleanup");
    }
}
~~~

Nếu `text` là `"17"`, phương thức in `cleanup` rồi trả `17`. Nếu là `"abc"`, `catch` in `parse failed`, chuẩn bị trả `-1`, `finally` in `cleanup`, rồi mới trả `-1`.

Điều này minh họa rằng `finally` chạy cả sau `return` xuất phát từ **khối `catch`**, không chỉ từ thân `try`.

Trong ví dụ ở phần trước, `throwingDemo()` cho thấy trường hợp không có handler trong phương thức; `finally` vẫn chạy trước khi ngoại lệ đi ra caller. Khi một ngoại lệ phát sinh trong chính `finally`, việc lựa chọn ngoại lệ cuối cùng phụ thuộc quy tắc hoàn tất bất thường đã giải thích.

Một khối `catch` **không bắt lại** ngoại lệ mới phát sinh trong khối `finally` cùng cấp của cấu trúc `try` đó; lỗi mới được truyền ra các vùng xử lý bao ngoài.

## 11. So sánh với try-with-resources

Mục đích phổ biến của `finally` là giải phóng tài nguyên:

~~~java
java.io.InputStream stream = openStream();
try {
    useStream(stream);
} finally {
    stream.close();
}
~~~

Đây là **đoạn minh họa**, giả định `openStream()` và `useStream(...)` có sẵn và phương thức bao quanh xử lý checked exceptions phù hợp. Nếu `useStream` thực hiện `return`, `break`, `continue` hoặc ném lỗi khiến mã rời cấu trúc, Java vẫn đi qua lệnh `stream.close()` trong `finally` theo quy tắc.

Tuy nhiên `close()` cũng có thể ném ngoại lệ và **làm che lỗi hoặc thay đổi kết quả trả về**. Với tài nguyên triển khai `AutoCloseable`, nên ưu tiên **try-with-resources**:

~~~java
static String firstLine(String text) throws java.io.IOException {
    try (java.io.BufferedReader reader =
                 new java.io.BufferedReader(
                         new java.io.StringReader(text))) {
        return reader.readLine();
    }
}
~~~

Phương thức này có thể đặt trong một lớp Java 17+. Tài nguyên `reader` được đóng tự động sau khi thân `try` chuẩn bị hoàn tất và trước khi `return` của phương thức thực sự hoàn tất.

Với try-with-resources, nếu thân `try` đã ném một ngoại lệ và `close()` cũng ném lỗi, Java thường giữ **ngoại lệ từ thân `try` làm ngoại lệ chính** và đưa lỗi đóng tài nguyên vào `getSuppressed()`. Cơ chế này khác với việc viết `finally { resource.close(); }` thủ công, nơi lỗi mới từ `close()` có thể thay thế lỗi cũ.

Nếu thân `try` đang **trả về bình thường** nhưng việc `close()` ném lỗi, lời gọi vẫn có thể kết thúc bất thường thay vì trả kết quả. Vì vậy, ý tưởng “đã đến `return` là chắc chắn caller nhận kết quả” cũng không đúng trong try-with-resources.

## 12. Những trường hợp `finally` không được bảo đảm sẽ chạy

`finally` cung cấp hành vi dọn dẹp mạnh mẽ trong luồng thực thi Java thông thường, nhưng không phải bảo đảm bất chấp mọi hoàn cảnh.

Các tình huống cần lưu ý:

- **VM hoặc tiến trình bị chấm dứt:** chẳng hạn `Runtime.getRuntime().halt(...)`, hệ điều hành chấm dứt tiến trình hoặc lỗi JVM nghiêm trọng.
- **`System.exit(...)` khi thực sự làm VM kết thúc:** chương trình không thực hiện quá trình tháo stack Java theo nghĩa bảo đảm chạy `finally` cho mọi luồng.
- **Không bao giờ rời khối `try`:** ví dụ luồng bị kẹt trong vòng lặp vô hạn mà không có bước thoát.
- **Hoạt động không thể tiếp tục bình thường do môi trường:** mất điện, dừng cưỡng bức hoặc lỗi ngoài khả năng ứng dụng kiểm soát.

Ngay cả khi `finally` chạy, nó cũng có thể bị lỗi giữa chừng; đừng giả định mọi câu lệnh trong `finally` chắc chắn đã hoàn tất. Nếu cần cam kết bền vững về dữ liệu, giao dịch hay tiến trình, phải có thiết kế thích hợp như quản lý transaction, ghi nhận sự kiện, cơ chế phục hồi khi khởi động lại hoặc bảo đảm ở hệ thống lưu trữ.

**Không nên dùng `System.exit` để “kiểm tra” thứ tự `finally` trong chương trình chính** vì nó kết thúc tiến trình và có thể gây nhiễu cho việc chạy các ví dụ khác. Hãy thử các tình huống `return`, `break`, `continue` với những phương thức nhỏ như phần trên.

## 13. Các sai lầm phổ biến

### Sai lầm 1: “`return` chạy thì `finally` bị bỏ qua”

Sai. Khi `return` nằm trong `try`/`catch` có `finally`, `finally` thông thường chạy **trước khi caller nhận kết quả**.

### Sai lầm 2: “`finally` sửa biến nguyên thủy thì giá trị `return` sẽ đổi theo”

Không nhất thiết. Giá trị biểu thức `return` được xác định trước `finally`. Với `int value = 3; try { return value; } finally { value = 9; }`, kết quả trả bình thường vẫn là `3`.

### Sai lầm 3: “`finally` không thể tác động tới đối tượng được trả”

Sai. Nếu biểu thức trả về tham chiếu đến một đối tượng mutable, `finally` có thể sửa trạng thái của cùng đối tượng đó trước khi caller nhận tham chiếu. Đây khác với việc gán lại biến tham chiếu cục bộ.

### Sai lầm 4: “`break` hoặc `continue` sẽ nhảy thẳng qua `finally`”

Sai. Các vùng `finally` bị rời bởi đường nhảy vẫn chạy theo thứ tự trước khi Java thực hiện đích nhảy, trừ khi có lý do hoàn tất bất thường mới thay thế.

### Sai lầm 5: “`continue` khiến vòng `for` bỏ qua cả bước tăng biến”

Sai. Sau `finally` hoàn tất bình thường, `continue` trong vòng `for` đi tới phần cập nhật `update` rồi kiểm tra điều kiện vòng kế. Nó bỏ phần thân còn lại của lượt hiện tại, không mặc định bỏ cơ chế cập nhật vòng.

### Sai lầm 6: “`finally` chỉ chạy khi có ngoại lệ”

Sai. `finally` còn chạy khi `try` hoàn tất bình thường, khi `catch` đã xử lý lỗi, và khi chuẩn bị rời do `return`/`break`/`continue`.

### Sai lầm 7: “Đặt `return` trong `finally` để bảo đảm có kết quả”

Đây là thực hành nguy hiểm. `return` trong `finally` có thể thay thế `return` ở `try` và **nuốt ngoại lệ đang truyền**, khiến caller không biết thao tác đã thất bại.

### Sai lầm 8: “Nếu `finally` ném lỗi thì ngoại lệ cũ tự động là cause”

Không. Trong `try/finally` thủ công, Java không tự động nối ngoại lệ cũ thành `cause` của lỗi mới. Cần chủ động thiết kế xử lý lỗi, hoặc tận dụng quy tắc suppressed exceptions của try-with-resources khi phù hợp.

### Sai lầm 9: “Các `finally` lồng nhau chạy từ ngoài vào trong”

Sai. Khi thoát nhiều vùng `try` lồng nhau, khối `finally` của vùng trong chạy trước, sau đó mới tới vùng ngoài.

### Sai lầm 10: “`finally` bảo đảm tuyệt đối kể cả JVM bị kill”

Không. Những cơ chế làm VM/tiến trình dừng cưỡng bức hoặc không thoát khỏi `try` có thể ngăn `finally` được thực hiện.

### Sai lầm 11: “Một lệnh `return` khác trong `finally` chỉ sửa code style”

Không. Nó thay đổi **ngữ nghĩa thực thi** và có thể che lỗi. Một số công cụ phân tích mã sẽ cảnh báo, nhưng quy tắc ngôn ngữ mới là lý do chính cần tránh.

### Sai lầm 12: “`break` trong `switch statement` và `yield` của `switch expression` giống nhau”

Không. Trong `switch statement` kiểu cổ điển, `break` kết thúc statement; trong `switch expression`, `yield` cung cấp giá trị biểu thức. Khi các đường này rời cấu trúc `try` có `finally`, `finally` liên quan vẫn được xử lý. Đừng thay `yield` bằng `break value` trong Java 17.

## 14. Bài tập tự kiểm tra

**Câu 1.** Phương thức chứa `try { return 5; } finally { System.out.println("cleanup"); }` in dòng nào trước khi caller nhận `5`?

**Câu 2.** `int x = 1; try { return x; } finally { x = 2; }` trả `1` hay `2`? Vì sao?

**Câu 3.** Nếu `try` trả về một `StringBuilder`, `finally` gọi `append(...)` trên cùng đối tượng, caller có thể thấy nội dung mới không?

**Câu 4.** `break` trong `try` của vòng `for` khiến `finally` có chạy trước khi rời vòng không?

**Câu 5.** Với `continue` trong vòng `for`, bước cập nhật biến vòng xảy ra trước hay sau `finally` liên quan?

**Câu 6.** Khi `break OUTER` thoát khỏi hai `try` lồng nhau, khối `finally` nào chạy trước?

**Câu 7.** Nếu `try` định trả `7` nhưng `finally` chứa `return 9;`, caller nhận gì?

**Câu 8.** Nếu `try` ném `IllegalArgumentException` nhưng `finally` chứa `return 0;` trong phương thức trả `int`, ngoại lệ còn truyền ra caller không?

**Câu 9.** Nếu `try` định `return 7` nhưng `finally` ném `IllegalStateException`, kết quả là gì?

**Câu 10.** Vì sao lỗi xảy ra trong `finally { resource.close(); }` có thể che ngoại lệ ban đầu từ `try`?

**Câu 11.** Try-with-resources lưu lỗi đóng tài nguyên ở đâu khi thân `try` đã ném ngoại lệ chính?

**Câu 12.** Khi một `catch` thực hiện `return -1;`, `finally` cùng cấu trúc có thông thường được chạy không?

**Câu 13.** Một vòng lặp vô hạn nằm trong `try` và không bao giờ thoát. Có cơ sở nào để khẳng định `finally` sẽ chạy ngay không?

**Câu 14.** Việc dùng `System.exit(...)` có thể phá vỡ kỳ vọng “mọi `finally` đều chạy” như thế nào?

**Câu 15.** Nếu một `finally` hoàn tất bình thường, nó giữ hay thay thế lý do hoàn tất đang chờ của `try`?

### Đáp án gợi ý

1. In `cleanup` trước rồi caller mới nhận `5`.
2. Trả `1` vì giá trị biểu thức `return` được đánh giá trước khi `finally` sửa biến.
3. Có, vì tham chiếu trả về vẫn trỏ tới cùng đối tượng có thể thay đổi.
4. Có, theo luồng Java thông thường.
5. Sau `finally`, trước khi kiểm tra điều kiện cho lượt tiếp theo.
6. `finally` ở vùng trong chạy trước.
7. Nhận `9`; `return` từ `finally` thay thế kết quả đang chờ.
8. Không, lỗi có thể bị che và caller nhận `0`; đây là mã nguy hiểm.
9. Phương thức ném `IllegalStateException`, không trả `7`.
10. Vì `throw` mới từ `finally` có thể thay thế lý do hoàn tất bất thường trước đó.
11. Thường ở `getSuppressed()` của ngoại lệ chính.
12. Có; `finally` chạy trước khi `return -1` được hoàn tất.
13. Không; phải có đường thực thi rời `try` thì mới đi đến xử lý dọn dẹp.
14. VM có thể kết thúc mà không tháo hết các khung stack Java để chạy `finally`.
15. Giữ lý do hoàn tất cũ; chỉ khi `finally` tự hoàn tất bất thường mới có thể thay thế.

## 15. Ghi nhớ

**`finally` không chỉ dành cho exception.** Với `return`, Java đánh giá biểu thức trả về trước, chạy `finally` liên quan rồi mới hoàn tất trả về nếu `finally` kết thúc bình thường. Với `break` và `continue`, Java thực hiện các `finally` cần thiết trước khi rời vòng lặp hoặc đi đến lượt tiếp theo. Với các vùng lồng nhau, `finally` chạy từ trong ra ngoài khi thoát nhiều phạm vi. **Tránh `return` hoặc `throw` ngoài ý muốn trong `finally`**, vì chúng có thể ghi đè giá trị trả về, yêu cầu nhảy hoặc ngoại lệ ban đầu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="finally-004">Giá trị trả về được tính trước khi thực thi finally</a>

<details>
<summary>Xem chi tiết</summary>

# Giá trị trả về được tính trước khi thực thi finally trong Java

> Java Core – Exception · Phần: **Khối finally** · Tập trung vào thứ tự tính biểu thức `return`, biến cục bộ, tham chiếu đối tượng và trường hợp `finally` thay thế luồng điều khiển. Ví dụ Java 17+.

## 1. Quy tắc cốt lõi

Khi một phương thức Java thực thi câu lệnh `return expression;` trong `try` hoặc `catch` có `finally` liên quan, trình tự về mặt ngữ nghĩa là:

1. **Đánh giá toàn bộ `expression`** và thu được giá trị `V`, nếu biểu thức hoàn thành bình thường.
2. Ghi nhận lý do kết thúc luồng hiện tại là **trả về giá trị `V`**. Phương thức chưa chuyển điều khiển hẳn cho caller.
3. **Thực hiện các khối `finally` phải chạy** trên đường rời các phạm vi liên quan.
4. Nếu các khối `finally` hoàn thành bình thường, caller nhận **giá trị `V` đã tính** ở bước 1.
5. Nếu `finally` lại kết thúc bất thường, ví dụ tự `return` hoặc `throw`, lý do mới có thể **thay thế** yêu cầu trả về đang chờ. Khi đó caller **không nhất thiết** nhận `V`.

Điều này được mô tả trong [Java Language Specification 17, mục 14.17 – The return Statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.17) và [mục 14.20.2 – Execution of try-finally and try-catch-finally](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.2).

**Điều quan trọng nhất:** `finally` chạy **trước khi caller nhận kết quả**, nhưng **sau khi biểu thức của `return` đã được đánh giá**. Đừng nhầm “return được viết trước finally” với “phương thức đã thoát hoàn toàn trước khi finally chạy”.

### Sơ đồ luồng thực thi

```text
           Gặp return expression;
                     |
                     v
        Đánh giá expression trước
                /         \
      Thành công            Expression tự ném E
          |                         |
          v                         v
     Có giá trị V               Chưa có V
          |                         |
          +-------------+-----------+
                        |
                        v
                 Chạy finally
                  /        \
        Hoàn thành          Kết thúc bất thường
          bình thường         (return/throw khác)
            |                         |
            v                         v
       Dùng lý do gốc          Lý do mới có thể
       return V hoặc E          thay thế lý do gốc
            |                         |
            v                         v
      Caller nhận V           Caller nhận kết quả
      hoặc nhận lỗi E         khác / ngoại lệ khác
```

Sơ đồ là cách trực quan hóa quy tắc ngôn ngữ, không phải yêu cầu JVM lưu giá trị vào một biến tên `V` hoặc một bytecode cố định. Compiler có thể chọn cách triển khai khác nhau miễn giữ ngữ nghĩa JLS.

## 2. Mã ngắn nhất: sửa biến trong finally không đổi giá trị đã return

Xét một phương thức (đoạn dưới là **mã minh họa trong class**, chưa phải chương trình đầy đủ):

```java
static int example() {
    int number = 10;
    try {
        return number;
    } finally {
        number = 99;
    }
}
```

Giá trị của lời gọi `example()` là **`10`**, không phải `99`.

Phân tích từng bước:

- Khi gặp `return number;`, biểu thức biến `number` được đọc; giá trị nguyên thủy `int` hiện tại là `10`.
- Phương thức ghi nhận yêu cầu trả về `10` rồi chuyển sang khối `finally`.
- Khối `finally` gán biến cục bộ `number = 99`; thay đổi này diễn ra thật, nhưng không làm thay đổi giá trị `10` đã được tính cho việc return.
- Vì `finally` hoàn thành bình thường, phương thức hoàn tất việc trả giá trị đã ghi nhận.

Tương tự với các kiểu nguyên thủy `boolean`, `long`, `double` và các giá trị bất biến như tham chiếu đến một đối tượng `String`: phép **gán lại biến cục bộ** trong `finally` không tự đánh giá lại biểu thức `return` trước đó.

Không nên giải thích rằng “finally không thực hiện việc gán”: nó **vẫn thực hiện**, chỉ là giá trị trả về đã được tính trước.

## 3. Chương trình Java 17 hoàn chỉnh và stdout đã kiểm chứng

Lưu tệp **`FinallyReturnDemo.java`** với nội dung sau. Bảy tình huống được tách thành các phương thức để so sánh kết quả trả về nguyên thủy, biểu thức gọi hàm, tham chiếu đối tượng, return trong finally, throw trong finally, lỗi khi tính biểu thức return và return trong catch.

```java
public class FinallyReturnDemo {
    private static int calculate() {
        System.out.println("evaluate calculate()");
        return 7;
    }

    private static int primitive() {
        int value = 10;
        try {
            System.out.println("try primitive");
            return value + 1;
        } finally {
            value = 99;
            System.out.println("finally primitive value=" + value);
        }
    }

    private static int expression() {
        try {
            return calculate();
        } finally {
            System.out.println("finally expression");
        }
    }

    private static StringBuilder reference() {
        StringBuilder text = new StringBuilder("A");
        try {
            System.out.println("try reference");
            return text;
        } finally {
            text.append("B");
            text = new StringBuilder("C");
            System.out.println("finally reference local=" + text);
        }
    }

    private static int override() {
        try {
            System.out.println("try override");
            return 1;
        } finally {
            System.out.println("finally override");
            return 2; // Biên dịch được nhưng che giá trị return từ try.
        }
    }

    private static int throwFromFinally() {
        try {
            System.out.println("try throw");
            return 3;
        } finally {
            System.out.println("finally throw");
            throw new IllegalStateException("cleanup failed");
        }
    }

    private static int failedExpression() {
        try {
            System.out.println("try parse");
            return Integer.parseInt("bad");
        } finally {
            System.out.println("finally parse");
        }
    }

    private static int returnFromCatch() {
        try {
            throw new IllegalArgumentException("bad input");
        } catch (IllegalArgumentException ex) {
            System.out.println("catch returns");
            return 5;
        } finally {
            System.out.println("finally catch");
        }
    }

    public static void main(String[] args) {
        System.out.println("== primitive ==");
        System.out.println("result=" + primitive());

        System.out.println("== expression ==");
        System.out.println("result=" + expression());

        System.out.println("== reference ==");
        StringBuilder result = reference();
        System.out.println("result=" + result);

        System.out.println("== override ==");
        System.out.println("result=" + override());

        System.out.println("== throw ==");
        try {
            System.out.println("result=" + throwFromFinally());
        } catch (IllegalStateException ex) {
            System.out.println("caught=" + ex.getMessage());
        }

        System.out.println("== failed expression ==");
        try {
            System.out.println("result=" + failedExpression());
        } catch (NumberFormatException ex) {
            System.out.println("caught=NumberFormatException");
        }

        System.out.println("== catch return ==");
        System.out.println("result=" + returnFromCatch());
    }
}
```

### Biên dịch và chạy

Dùng JDK 17 hoặc mới hơn tại thư mục chứa file `FinallyReturnDemo.java`:

```bash
javac --release 17 -encoding UTF-8 FinallyReturnDemo.java
java FinallyReturnDemo
```

**Stdout chính xác:**

```text
== primitive ==
try primitive
finally primitive value=99
result=11
== expression ==
evaluate calculate()
finally expression
result=7
== reference ==
try reference
finally reference local=C
result=AB
== override ==
try override
finally override
result=2
== throw ==
try throw
finally throw
caught=cleanup failed
== failed expression ==
try parse
finally parse
caught=NumberFormatException
== catch return ==
catch returns
finally catch
result=5
```

Kết quả trên đã được kiểm chứng bằng cách **biên dịch với `javac --release 17` và chạy trên JDK có hỗ trợ Java 17**. Các thông điệp ngoại lệ do code tự tạo; chương trình không phụ thuộc cách JVM diễn giải stack trace hoặc thông điệp `NumberFormatException` để tạo stdout.

## 4. Phân tích chi tiết bảy trường hợp

### 4.1. `primitive()`: giá trị của biểu thức đã chốt trước finally

Trong nhánh `primitive()`:

```java
int value = 10;
try {
    System.out.println("try primitive");
    return value + 1;
} finally {
    value = 99;
    System.out.println("finally primitive value=" + value);
}
```

Thứ tự:

1. In `try primitive`.
2. Đọc `value == 10` và tính `value + 1 == 11` cho biểu thức return.
3. Đến `finally`, gán `value = 99` rồi in `finally primitive value=99`.
4. `finally` hoàn thành bình thường; method trả **`11`**, caller in `result=11`.

Nếu nhầm rằng cuối cùng method “đọc lại biến `value`”, bạn sẽ dự đoán `99` — sai. Một biểu thức return không tự động được đánh giá lại sau finally.

### 4.2. `expression()`: lời gọi trong return được thực hiện trước finally

Đối với:

```java
try {
    return calculate();
} finally {
    System.out.println("finally expression");
}
```

Lời gọi `calculate()` diễn ra ngay khi đánh giá biểu thức của `return`; nó in `evaluate calculate()` rồi trả về `7`. **Sau khi đã có số `7`**, Java chạy finally và in `finally expression`. Caller mới nhận số `7`.

Vì vậy stdout không thể đảo thành `finally expression` trước `evaluate calculate()`. Điều này cũng đúng nếu biểu thức return chứa phép toán, truy cập mảng, hoặc các lời gọi khác: chúng được đánh giá theo quy tắc biểu thức trước khi thực hiện finally, miễn là bước đánh giá hoàn thành bình thường.

### 4.3. `reference()`: tham chiếu được chốt, trạng thái đối tượng vẫn có thể đổi

Đây là trường hợp thường gây hiểu nhầm lớn nhất:

```java
StringBuilder text = new StringBuilder("A");
try {
    return text;
} finally {
    text.append("B");
    text = new StringBuilder("C");
}
```

Điều được trả về là **giá trị tham chiếu đến đối tượng StringBuilder ban đầu**, không phải một bản sao bất biến của toàn bộ đối tượng. Diễn biến:

1. Biến `text` trỏ đến đối tượng `StringBuilder("A")`.
2. `return text;` đánh giá và giữ **tham chiếu đó** để trả về.
3. Trong finally, `text.append("B")` làm thay đổi **chính đối tượng đã được chọn trả về** từ nội dung `A` thành `AB`.
4. Sau đó, `text = new StringBuilder("C")` chỉ gán lại **biến cục bộ** `text` sang một đối tượng mới; giá trị tham chiếu đã chọn ở bước 2 không đổi.
5. Caller nhận đối tượng ban đầu, hiện có nội dung `AB`.

Do đó chương trình in `finally reference local=C` trong finally nhưng caller in `result=AB`. Không có nghịch lý: **giá trị tham chiếu** đã được chọn trước finally, còn **trạng thái của đối tượng mà tham chiếu chỉ đến** có thể bị sửa sau đó.

| Thao tác trong finally | Có làm thay đổi giá trị tham chiếu đã chọn để return? | Caller có thể thấy tác động? |
| --- | --- | --- |
| `text.append("B")` | Không | **Có**, vì đối tượng được trả về đã bị sửa |
| `text = new StringBuilder("C")` | Không | **Không**, nếu chỉ đổi biến cục bộ |
| `text = null` | Không | Không làm return biến thành null |
| Sửa trường/mảng của đối tượng được trả về | Không | Có, nếu trạng thái đó nằm trên đối tượng caller nhận |

Như vậy câu “giá trị trả về không đổi trong finally” cần được diễn đạt cẩn thận: với một **tham chiếu đến đối tượng mutable**, *tham chiếu* không đổi nhưng *đối tượng* vẫn có thể đổi.

### 4.4. `override()`: một return khác trong finally ghi đè ý định return trước

Xét:

```java
static int override() {
    try {
        return 1;
    } finally {
        return 2;
    }
}
```

Đây là một ví dụ **Java hợp lệ về cú pháp**, nhưng được khuyến nghị **không dùng trong code sản xuất**.

- `return 1;` đã được đánh giá, ghi nhận lý do kết thúc là “trả 1”.
- Khối `finally` thực thi `return 2;`, tạo **lý do kết thúc bất thường mới** là “trả 2”.
- Theo JLS mục 14.20.2, lý do mới từ finally **thay thế** lý do cũ.
- Caller nhận `2`; đây không phải vì biểu thức `return 1` được tính lại hay biến cục bộ bị đổi.

Trong chương trình đầy đủ, thứ tự là `try override` → `finally override` → `result=2`.

**Chỉ nên xem `return` trong finally như một ví dụ về ngữ nghĩa ngôn ngữ, không phải thủ thuật thiết kế**. Nó có thể che kết quả và lỗi nghiêm trọng, khiến code khó bảo trì và phân tích.

### 4.5. `throwFromFinally()`: finally ném lỗi, caller không nhận kết quả đã tính

Trong:

```java
static int throwFromFinally() {
    try {
        return 3;
    } finally {
        throw new IllegalStateException("cleanup failed");
    }
}
```

Java đã tính giá trị trả về là `3`, nhưng finally lại ném `IllegalStateException`. Yêu cầu trả về `3` **bị bỏ**, và caller nhận đường điều khiển ngoại lệ. Vì thế không có dòng `result=3` trong stdout; nhánh `catch` ở caller in `caught=cleanup failed`.

Phần tính biểu thức return có thể đã tạo tác dụng phụ trước khi exception từ finally xuất hiện. Exception không tự rollback những tác dụng phụ ấy.

Đây là một lý do cần xử lý cleanup cẩn trọng. Nếu một đoạn cleanup cần ném lỗi, phương thức có thể không trả được kết quả đã tính; lựa chọn xử lý nên có chủ đích thay vì ngẫu nhiên vì mã `finally` phức tạp.

### 4.6. `failedExpression()`: biểu thức return không tạo được giá trị

Trong:

```java
try {
    return Integer.parseInt("bad");
} finally {
    System.out.println("finally parse");
}
```

Lời gọi `Integer.parseInt("bad")` ném `NumberFormatException` **trong giai đoạn đánh giá expression**. Không có kết quả `int` nào được tạo ra để ghi nhận một giá trị return. Java vẫn thực hiện finally theo quy tắc, in `finally parse`, rồi exception từ parse tiếp tục đến caller (vì finally hoàn thành bình thường).

Trong chương trình, caller bắt đúng kiểu và in `caught=NumberFormatException`. Không có dòng `result=...` từ lời gọi này. Đây là khác biệt căn bản giữa:

- **Return expression thành công**: có giá trị `V` đang chờ trả về.
- **Return expression ném exception**: chưa có `V`; lý do chuyển điều khiển là ném lỗi, không phải trả giá trị.

Nếu finally tiếp tục ném lỗi thứ hai, lỗi thứ hai có thể thay thế lỗi parse gốc. Tránh viết finally mang nhiều nhánh exception chưa được kiểm soát.

### 4.7. `returnFromCatch()`: return trong catch cũng đi qua finally

Hãy xem:

```java
try {
    throw new IllegalArgumentException("bad input");
} catch (IllegalArgumentException ex) {
    System.out.println("catch returns");
    return 5;
} finally {
    System.out.println("finally catch");
}
```

Đây là đoạn bên trong phương thức minh họa. `try` ném lỗi; `catch` nhận và xử lý, rồi bắt đầu yêu cầu trả giá trị `5`. Trước khi caller nhận số `5`, `finally` vẫn chạy và in `finally catch`. Kết quả cuối cùng là `5`.

Thứ tự stdout: `catch returns` → `finally catch` → `result=5`.

**Không phải chỉ có return bên trong try mới chạy finally**. Return bên trong catch thuộc cùng cấu trúc cũng chịu quy tắc dọn dẹp tương ứng.

## 5. Phân biệt giá trị nguyên thủy, tham chiếu và trạng thái đối tượng

Java **truyền giá trị** khi trả kết quả qua lời gọi phương thức: kết quả có thể là giá trị nguyên thủy hoặc giá trị tham chiếu.

### Với kiểu nguyên thủy

```java
int x = 10;
try {
    return x;
} finally {
    x++;
}
```

Giả định ở trong một phương thức trả `int`. Kết quả trả về là `10`, không phải `11`. Biến `x` đã thay đổi trong finally nhưng biểu thức return trước đó không được tính lại.

Các biểu thức như `return x + 2;`, `return x++;` hoặc `return method();` vẫn tuân thứ tự: toàn bộ biểu thức return được tính trước finally.

**Trường hợp `x++`:** giá trị của hậu tố increment là giá trị **trước khi tăng**, trong khi biến `x` tăng như một tác dụng phụ trước khi cuối cùng sang finally:

```java
int x = 4;
try {
    return x++; // giá trị return được chọn là 4, x trở thành 5.
} finally {
    x = 100;
}
```

Ví dụ này trả `4`. Nó minh họa rằng cả tác dụng phụ của biểu thức return và công việc trong finally có thể thực thi, nhưng không làm giá trị đã chọn bị đánh giá lại.

### Với kiểu tham chiếu

```java
StringBuilder builder = new StringBuilder("Hello");
try {
    return builder;
} finally {
    builder.append(" Java");
}
```

Caller nhận tham chiếu đến **cùng một đối tượng** mà nội dung lúc được sử dụng có thể là `Hello Java`. Return không clone đối tượng và cũng không “đóng băng” toàn bộ trạng thái của nó. Một thiết kế API muốn giữ trạng thái bất biến nên trả về immutable object, copy phù hợp, hoặc tránh sửa đối tượng sau khi đã chọn trả về.

### Với String bất biến

```java
String name = "A";
try {
    return name;
} finally {
    name = name + "B";
}
```

Kết quả là `"A"`. Vì `String` bất biến, phép nối tạo ra một String mới; gán lại biến `name` không sửa đối tượng String cũ. Điểm quan trọng vẫn là **tham chiếu trả về được chọn từ trước**, không phải vì Java có quy tắc đặc biệt cho keyword `String`.

## 6. Khi finally có return/throw mới: quy tắc ưu tiên chính xác

JLS mô tả luồng điều khiển của `try-finally` bằng các **lý do kết thúc** (reason for abrupt completion). Với một lý do cũ `R` từ try/catch:

- **Finally hoàn thành bình thường:** lý do `R` tiếp tục có hiệu lực. Với `return V`, trả `V`; với `throw E`, tiếp tục ném `E`.
- **Finally kết thúc bất thường vì lý do mới `S`:** toàn cấu trúc kết thúc theo `S`; lý do `R` bị thay thế.

Bảng so sánh:

| Trong try/catch | Trong finally | Hệ quả |
| --- | --- | --- |
| `return 10;` | Chỉ log, kết thúc bình thường | Caller nhận `10` |
| `return 10;` | Gán lại biến cục bộ rồi kết thúc bình thường | Caller vẫn nhận `10` |
| `return object;` | Sửa trạng thái của chính object | Caller nhận cùng tham chiếu, thấy object đã sửa |
| `return 10;` | `return 20;` | Caller nhận `20`, return cũ bị che |
| `return 10;` | `throw new RuntimeException();` | Caller không nhận 10; exception lan lên |
| `throw new IOException(...);` | Log, kết thúc bình thường | IOException gốc tiếp tục lan |
| `throw new IOException(...);` | `return 20;` | Exception gốc bị che; caller nhận 20 |
| `throw new IOException(...);` | `throw new IllegalStateException(...);` | Exception mới có thể thay thế lỗi gốc |

Hai dòng cuối là ví dụ **mã có hành vi nguy hiểm nhưng hợp lệ theo ngôn ngữ** (giả định đủ khai báo/handler). Chúng minh họa vì sao return trong finally thường bị xem là lỗi thiết kế. Không nên dùng để “sửa nhanh” một lỗi bằng cách ép phương thức luôn trả về thành công.

### Cleanup và suppressed exception là trường hợp riêng

`try-with-resources` có quy tắc chuyên biệt về việc đóng tài nguyên. Khi thân try và `close()` cùng phát sinh exception, exception đóng tài nguyên thường được thêm vào danh sách `getSuppressed()` của exception chính, thay vì mặc định thay thế nó theo cách một `finally` tự viết ném lỗi mới.

Điều này không làm đổi thứ tự tính biểu thức return: Java vẫn áp dụng các quy tắc ngôn ngữ về luồng thực thi và đóng tài nguyên. Tuy nhiên, **đừng trộn** quy tắc suppressed của tài nguyên với quy tắc một `finally` thủ công tự `throw`.

Nếu việc mở/đóng tài nguyên cần quản lý, ưu tiên cấu trúc `try-with-resources` với `AutoCloseable` khi phù hợp, thay vì một finally phức tạp và dễ che mất exception.

## 7. Return trong try/catch/finally: phân tích thứ tự cho các trường hợp khác

### 7.1. Nhiều return trong các nhánh điều kiện

```java
static int choose(boolean valid) {
    try {
        if (valid) {
            return 1;
        }
        return 2;
    } finally {
        System.out.println("cleanup");
    }
}
```

Trong một chương trình hợp lệ, nếu `valid == true` thì biểu thức trả về `1` được tính trước finally; nếu `false` thì tính `2` trước finally. Cả hai đường đều in `cleanup` trước khi caller nhận kết quả.

Không phải finally làm phương thức chỉ có một return; các nhánh quyết định kết quả từ trước, finally chỉ thực thi công việc theo đường đi đang rời phạm vi.

### 7.2. Return không có biểu thức trong void method

```java
static void save(boolean valid) {
    try {
        if (!valid) {
            return;
        }
        System.out.println("saving");
    } finally {
        System.out.println("cleanup");
    }
}
```

Với `return;` trong phương thức `void`, không có giá trị `V` cần tính, nhưng điều khiển vẫn đi qua finally trước khi caller tiếp tục. Đừng suy rằng finally chỉ chạy khi `return` có giá trị.

### 7.3. Một method được gọi trong biểu thức return có side effect

```java
static int logAndCompute() {
    System.out.println("compute");
    return 30;
}

static int example() {
    try {
        return logAndCompute() + 2;
    } finally {
        System.out.println("cleanup");
    }
}
```

Khi gọi `example()`, kết quả là 32. Dòng `compute` phải in trước `cleanup` vì lời gọi nằm trong biểu thức `return`. Phép cộng với 2 cũng hoàn thành trước finally nếu `logAndCompute()` trả về bình thường.

Nếu `logAndCompute()` ném exception trước khi trả kết quả, không có 32; Java đi qua finally theo đường exception như phần 4.6.

### 7.4. Nhiều finally lồng nhau

```java
static int nested() {
    try {
        try {
            return 9;
        } finally {
            System.out.println("inner");
        }
    } finally {
        System.out.println("outer");
    }
}
```

Kết quả trả về là `9`. Lý do `return 9` được xác lập trước, sau đó Java chạy finally **từ phạm vi trong ra phạm vi ngoài**, nên stdout bên trong method là `inner` rồi `outer`. Nếu một finally bên trong ném lỗi, luồng có thể vẫn đi qua finally ngoài; caller có thể nhận exception thay vì 9. Đoạn trên là phương thức minh họa trong class, không phải tệp Java độc lập.

## 8. Phân biệt “return đang chờ” và “phương thức đã trả xong”

Một cách ghi chú trong review code:

```text
return expression;   // Chọn giá trị cần trả, yêu cầu chuyển điều khiển.
finally { ... }      // Thực hiện trước khi phương thức chuyển hẳn điều khiển về caller.
caller                // Nhận kết quả nếu không có reason mới ghi đè.
```

JLS gọi việc `return` tạo ra lý do **abrupt completion**; từ *abrupt* ở đây không đồng nghĩa “đang xảy ra lỗi”. Câu lệnh `return` là một cách kết thúc có chủ đích, còn `throw` là một lý do abrupt khác. `finally` có thể chạy trên cả hai đường.

Việc chờ để hoàn tất return không bảo đảm không có side effect trong finally. Nếu finally thay đổi file, database, cache, collection hoặc đối tượng mà caller sắp nhận, những thay đổi ấy vẫn có thể quan sát thấy. Nhưng finally cũng có thể ném lỗi và khiến caller **không nhận** giá trị đã được tính.

**Không nên mô tả máy móc rằng “Java copy toàn bộ object vào vùng nhớ return”.** Ngôn ngữ chỉ bảo đảm giá trị biểu thức được đánh giá trước finally. Với đối tượng, giá trị đó là tham chiếu, không phải bản sao sâu; chi tiết bytecode hoặc vị trí lưu tạm là chuyện của trình biên dịch/JVM.

## 9. Kỹ thuật viết code rõ ràng hơn

### 9.1. Chỉ dùng finally để cleanup, tránh return trong finally

Mẫu nên dùng:

```java
static int calculateSafely() {
    try {
        return 10;
    } finally {
        System.out.println("cleanup");
    }
}
```

Ví dụ trên minh họa return từ try nhưng finally hoàn thành bình thường. Trong ứng dụng thật, phần cleanup có thể là giải phóng khóa/tài nguyên, cập nhật trạng thái dọn dẹp hoặc log ở mức phù hợp.

Mẫu gây hiểu nhầm:

```java
static int calculateBadly() {
    try {
        return 10;
    } finally {
        return 20; // Che mất ý định return của try.
    }
}
```

Đây là **mã có thể biên dịch nhưng không nên dùng**, trừ khi đang minh họa chính ngữ nghĩa `finally`. Một lỗi nguy hiểm hơn là `return` trong finally che exception thực sự từ try, khiến chương trình báo thành công giả.

### 9.2. Tách computation ra khỏi cleanup nếu thuận lợi

Nếu computation có nhiều side effect, có thể viết:

```java
static int clearer() {
    int answer;
    try {
        answer = compute();
    } finally {
        cleanUp();
    }
    return answer;
}
```

Đây là **mã minh họa**: `compute()` và `cleanUp()` phải được định nghĩa/nhập phù hợp. Nếu `compute()` và `cleanUp()` hoàn thành bình thường, kết quả trả về được xử lý rõ ràng sau cleanup. Nếu `compute()` ném lỗi, finally vẫn chạy, sau đó exception lan đi (trừ khi finally có lỗi mới). Không dùng cách này để né các quy tắc cleanup; nó chỉ có thể giúp một số trường hợp dễ đọc hơn.

Đối với mã xử lý tài nguyên, `try-with-resources` thường rõ ràng hơn một finally tự đóng tài nguyên phức tạp.

### 9.3. Đừng thay đổi đối tượng sắp return một cách bất ngờ

Nếu phương thức trả về một đối tượng mutable, hãy xem xét trạng thái tại thời điểm caller nhận. Việc sửa đối tượng trong finally **hợp lệ về Java** nhưng có thể khiến người đọc nhầm rằng caller nhận trạng thái cũ.

Ưu tiên hoàn thiện đối tượng trước khi viết `return`, hoặc trả về bản sao bất biến khi hợp đồng yêu cầu không bị sửa tiếp. Nếu phải làm cleanup có side effect, giải thích rõ ranh giới đó để việc đọc API và viết test không mơ hồ.

## 10. Cách dự đoán đầu ra của một đoạn finally

Để trả lời các câu hỏi phỏng vấn hoặc debug thực tế, hãy làm theo thứ tự:

1. **Xác định nơi có `return` hoặc `throw`.** Nó nằm trong try, catch hay finally?
2. **Đánh giá biểu thức return ngay tại thời điểm gặp return.** Ghi giá trị hoặc tham chiếu được chọn. Nếu biểu thức ném lỗi, ghi nhận exception thay vì giả định có giá trị trả về.
3. **Liệt kê side effect đã xảy ra** trong chính biểu thức return: tăng biến, sửa mảng, gọi phương thức, in log...
4. **Thực hiện finally theo thứ tự phạm vi**, từ trong ra ngoài. Phân biệt gán lại biến cục bộ với thay đổi nội dung của object.
5. **Xác định finally hoàn thành bình thường hay bất thường.** Nếu có `return`/`throw` trong finally, cập nhật lý do điều khiển cuối cùng.
6. **Suy ra kết quả caller quan sát:** giá trị nguyên thủy, tham chiếu (và trạng thái object), exception hoặc một đường điều khiển khác.
7. **Đọc stdout theo thời điểm:** các dòng in trong expression của return phải trước các dòng in trong finally; dòng in kết quả ở caller chỉ có khi lời gọi thực sự trả về bình thường.

Bài tập minh họa (đặt trong một phương thức trả `int`):

```java
int count = 4;
try {
    return count++;
} finally {
    count = 100;
    System.out.println("finally count=" + count);
}
```

Giải thích: biểu thức `count++` chọn **4** làm giá trị return rồi tăng biến lên **5**; finally gán biến thành **100**, in `finally count=100`; caller nhận **4** nếu finally hoàn thành bình thường.

Nếu một bạn khác dự đoán caller nhận 100, hãy chỉ ra rằng phần tăng và phần gán đều thực sự diễn ra nhưng không làm **tính lại** giá trị của biểu thức return đã hoàn thành.

## 11. Những lỗi hiểu sai phổ biến

- **“finally chạy trước return nên return dùng giá trị mới trong finally.”** Sai: biểu thức `return` được đánh giá **trước**, chỉ việc chuyển điều khiển cho caller được trì hoãn.
- **“Gán biến trong finally không hề có hiệu lực.”** Sai: việc gán có hiệu lực với biến đang tồn tại, nhưng không thay đổi giá trị đã chọn trả về.
- **“return một object nghĩa là snapshot của object trước finally.”** Sai: Java trả **giá trị tham chiếu**, còn object mutable có thể đổi.
- **“Đổi biến tham chiếu sang object mới trong finally làm caller nhận object mới.”** Sai: giá trị tham chiếu đã chọn trước khi gán lại.
- **“finally có return nhưng vẫn nhận return của try vì try chạy trước.”** Sai: return trong finally có thể thay thế lý do return của try.
- **“finally ném lỗi nhưng caller vẫn nhận giá trị return trước đó.”** Sai: nếu finally kết thúc bằng throw, caller nhận đường exception.
- **“Biểu thức return ném exception vẫn tạo ra một giá trị return.”** Sai: không có giá trị `V` khi expression hoàn thành bất thường.
- **“finally chỉ chạy khi xảy ra exception.”** Sai: nó cũng chạy khi try thành công, return bình thường hoặc return trong catch theo quy tắc cấu trúc.
- **“return trong catch bỏ qua finally.”** Sai: finally vẫn được thực hiện trước khi chuyển điều khiển hoàn toàn.
- **“finally luôn chạy trong mọi tình huống tuyệt đối.”** Không nên khẳng định: khi JVM/tiến trình bị chấm dứt đột ngột, mã cleanup có thể không có cơ hội chạy.
- **“return trong finally là cách chắc chắn không báo lỗi.”** Nó có thể **che lỗi nghiêm trọng**, không phải cách phục hồi đúng.
- **“try-with-resources và finally tự viết ném lỗi có cùng cơ chế suppressed.”** Sai: suppressed exception là quy tắc đặc thù của việc đóng resource tự động.

## 12. Tự kiểm tra kiến thức

**Câu 1.** Tại sao `int x=10; try {return x;} finally {x=99;}` trả về 10?

*Đáp án:* Vì giá trị biểu thức `return x` được đọc là 10 trước finally. Gán 99 sau đó không làm giá trị đã chọn bị đánh giá lại.

**Câu 2.** Với `return text;`, finally gọi `text.append("B")` rồi `text = new StringBuilder("C")`, caller nhận gì?

*Đáp án:* Cùng object ban đầu được chọn trước finally, nhưng nội dung có thể đã thêm B. Gán lại biến cục bộ chỉ đổi tham chiếu của biến đó.

**Câu 3.** Nếu finally chứa `return 2;` còn try đã đánh giá `return 1;`, kết quả là gì?

*Đáp án:* Caller nhận 2, vì finally kết thúc bất thường với lý do return mới thay thế yêu cầu return cũ.

**Câu 4.** Nếu finally ném `IllegalStateException` trong lúc try đang yêu cầu trả số 3, caller có nhận 3 không?

*Đáp án:* Không. Lý do throw từ finally có thể thay thế return đang chờ; caller thấy ngoại lệ.

**Câu 5.** Nếu biểu thức `Integer.parseInt("bad")` trong return ném lỗi, finally có chạy không?

*Đáp án:* Có theo quy tắc try/finally bình thường; chưa có giá trị int để return. Nếu finally hoàn thành bình thường, NumberFormatException được truyền ra ngoài.

**Câu 6.** Return trong catch có phải bỏ qua finally không?

*Đáp án:* Không; finally vẫn chạy trước khi caller nhận giá trị nếu không bị một lý do điều khiển mới thay thế.

**Câu 7.** Một `return x++;` mà `x` ban đầu là 4, finally gán `x=100` thì giá trị method trả là gì?

*Đáp án:* 4. Hậu tố tăng trả giá trị cũ, side effect tăng và phép gán trong finally không làm kết quả return bị tính lại.

**Câu 8.** Tại sao không nên `return` trong finally để “bảo đảm có kết quả”?

*Đáp án:* Vì nó có thể che kết quả từ try/catch hoặc che exception thật sự, khiến caller nhận trạng thái thành công sai.

## 13. Tài liệu đối chiếu chính thức

- [Java SE 17 JLS mục 14.17 – The return Statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.17): thứ tự đánh giá expression, giá trị return và điều khiển bị trì hoãn bởi finally.
- [Java SE 17 JLS mục 14.20.2 – Execution of try-finally and try-catch-finally](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.2): nguyên tắc lý do abrupt completion được giữ hoặc bị thay thế.
- [Java SE 17 JLS mục 14.1 – Normal and Abrupt Completion of Statements](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.1): phân biệt return và throw như các lý do chuyển điều khiển.
- [Java SE 17 JLS mục 15.6 – Normal and Abrupt Completion of Evaluation](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.6): nếu chính biểu thức return ném ngoại lệ.
- [Java SE 17 JLS mục 14.20.3 – try-with-resources](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.3): ngữ nghĩa đóng tài nguyên và suppressed exception.
- [Java SE 17 API – StringBuilder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html): ví dụ đối tượng mutable khi trả tham chiếu.

**Kết luận:** `return expression;` **chọn giá trị hoặc tham chiếu trước khi finally thực thi**. Nếu finally hoàn thành bình thường, caller nhận giá trị đã chọn; sửa biến cục bộ không tính lại return, nhưng thay đổi trạng thái object vẫn có thể được quan sát qua cùng tham chiếu. Nếu finally tự return hoặc throw, nó có thể **thay thế kết quả đang chờ**. Thiết kế an toàn nhất thường là dùng finally để cleanup, không để quyết định kết quả trả về.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="finally-005">Thay đổi đối tượng trả về khác thay đổi biến cục bộ như thế nào</a>

<details>
<summary>Xem chi tiết</summary>

# Thay đổi đối tượng trả về khác thay đổi biến cục bộ như thế nào trong `finally`?

## 1. Mục tiêu học tập

Trong Java, câu lệnh `return` trong khối `try` và khối `finally` có một thứ tự thực thi rất quan trọng: **biểu thức `return` được đánh giá trước, rồi `finally` chạy, sau đó phương thức mới hoàn tất trả về** nếu `finally` hoàn thành bình thường.

Điều này dẫn đến hai kết quả tưởng như mâu thuẫn:

- Nếu `try` chuẩn bị trả về một **giá trị nguyên thủy** hoặc một **tham chiếu được giữ trong biến cục bộ**, việc gán lại biến cục bộ trong `finally` không làm thay đổi giá trị đã được chuẩn bị để trả về.
- Nếu `try` chuẩn bị trả về **tham chiếu tới một đối tượng có thể thay đổi**, `finally` có thể sửa **trạng thái của chính đối tượng ấy**. Bên gọi nhận cùng đối tượng và quan sát trạng thái đã được sửa.

Bài này giúp bạn nắm chắc sự khác nhau giữa **giá trị biến**, **giá trị tham chiếu**, **đối tượng được tham chiếu**, và **giá trị của biểu thức `return`**; dự đoán đầu ra chương trình; nhận biết trường hợp `return` hoặc `throw` trong `finally` ghi đè kết quả trước đó; và thiết kế mã xử lý ngoại lệ không gây bất ngờ.

Ví dụ dùng Java 17+; các tệp có `main`, lệnh `javac`/`java` và `stdout` bên dưới là chương trình độc lập.

## 2. Thứ tự thực thi của `return` và `finally`

Xét cấu trúc:

```java
static int compute() {
    int result = 10;
    try {
        return result;
    } finally {
        result = 20;
    }
}
```

Đây là một phương thức minh họa. Một cách hiểu sai phổ biến là “`finally` chạy sau `return` nên việc gán `result = 20` sẽ đổi kết quả trả về thành 20”. Java **không** hoạt động như vậy.

Theo Java Language Specification (JLS `14.17 và `14.20.2), khi thực hiện `return expression;`:

1. Java đánh giá `expression`. Ở đây biểu thức là biến `result` đang mang giá trị `10`.
2. Kết quả đánh giá được lưu như **giá trị sắp trả về** cho lời gọi hiện tại.
3. Khi rời `try`, `finally` liên quan được thực thi.
4. Nếu `finally` hoàn thành bình thường, phương thức thực hiện việc trả **giá trị đã được chuẩn bị ở bước 2**.
5. Nếu `finally` tự hoàn thành đột ngột (chẳng hạn `throw` hoặc `return` riêng), lý do thoát mới **thay thế** lý do thoát đang chờ; kết quả cũ có thể không còn được trả về.

Do đó, trong ví dụ trên `compute()` trả về **10**, dù biến cục bộ mang giá trị 20 khi `finally` kết thúc.

Sơ đồ thời gian:

```text
result = 10
    |
    v
try: đánh giá return result
    |
    +--> giá trị trả về dự kiến = 10
    |
    v
finally: result = 20
    |
    v
finally kết thúc bình thường
    |
    v
bên gọi nhận 10
```

Điểm cốt lõi là **biểu thức `return` đã được đánh giá**, chứ phương thức chưa hoàn tất rời stack. Khi `finally` thay đổi biến, nó đang làm việc với biến cục bộ, không viết lại bản sao giá trị đã được tính cho lệnh trả về.

## 3. Giá trị nguyên thủy: thay đổi biến không đổi kết quả `return`

Tạo tệp **`PrimitiveFinallyDemo.java`**:

```java
public class PrimitiveFinallyDemo {
    static int number() {
        int value = 10;
        try {
            return value;
        } finally {
            value = 99;
            System.out.println("finally value=" + value);
        }
    }

    public static void main(String[] args) {
        System.out.println("returned=" + number());
    }
}
```

Biên dịch và chạy:

```bash
javac PrimitiveFinallyDemo.java
java PrimitiveFinallyDemo
```

Kết quả `stdout` chính xác:

```text
finally value=99
returned=10
```

Giải thích:

- `value` là biến cục bộ kiểu `int`, ban đầu bằng 10.
- Khi gặp `return value`, Java đánh giá và chuẩn bị trả về số 10.
- Khối `finally` vẫn chạy và thay đổi biến thành 99; vì thế dòng đầu in 99.
- Khi `finally` kết thúc bình thường, phương thức trả lại 10 cho lời gọi ở `main`.
- Không tồn tại một “liên kết sống” giữa kết quả trả về kiểu `int` và biến cục bộ `value` sau thời điểm đánh giá `return`.

Các kiểu nguyên thủy khác (`long`, `double`, `boolean`, ...) cũng theo nguyên tắc đánh giá biểu thức trả về trước khi xử lý `finally`.

### Đừng nhầm `return value` với `return 99` nằm trong `finally`

Hai đoạn sau có ý nghĩa khác nhau:

```java
// A: gán lại biến, KHÔNG tạo một return mới.
try {
    return value;
} finally {
    value = 99;
}
```

```java
// B: chính finally có return khác, thay thế return cũ.
try {
    return value;
} finally {
    return 99;
}
```

Đây là hai **mảnh mã minh họa** bên trong phương thức trả về `int`. A trả giá trị đã được đánh giá ở `try`. B trả `99` do câu lệnh `return` **của `finally`**, không phải vì việc gán lại biến. Viết `return` trong `finally` có thể che lỗi và thường là thực hành nên tránh; phần 9 phân tích kỹ hơn.

## 4. Tham chiếu đối tượng: Java vẫn truyền/trả về **giá trị**

Với đối tượng Java, biến cục bộ kiểu tham chiếu chứa **giá trị tham chiếu** đến đối tượng, không chứa chính toàn bộ đối tượng trong biến theo kiểu sao chép khi `return`.

Xem tình huống:

```java
StringBuilder text = new StringBuilder("A");
try {
    return text;
} finally {
    text.append("B");
}
```

Đây là mảnh mã đặt trong phương thức trả về `StringBuilder`. Khi đánh giá `return text`, Java chuẩn bị trả **giá trị tham chiếu** đến đối tượng `StringBuilder` hiện tại. Sau đó `finally` dùng cùng tham chiếu để gọi `append("B")`, làm **thay đổi trạng thái của đối tượng** từ `"A"` thành `"AB"`.

Bên gọi nhận tham chiếu đã được chuẩn bị trước đó, nhưng đối tượng đằng sau tham chiếu đã bị thay đổi. Vì vậy bên gọi thấy `"AB"`.

So sánh với trường hợp:

```java
StringBuilder text = new StringBuilder("A");
try {
    return text;
} finally {
    text = new StringBuilder("B");
}
```

Ở đây `finally` **gán biến `text` sang đối tượng mới**. Nó không sửa đối tượng cũ. Giá trị tham chiếu của biểu thức `return text` đã được giữ trước khi gán lại, nên bên gọi vẫn nhận đối tượng cũ chứa `"A"`.

Vì thế cần phân biệt:

| Hành động trong `finally` | Cái gì thay đổi? | Đối tượng mà `return` ban đầu đã tham chiếu |
| --- | --- | --- |
| `text.append("B")` | Trạng thái của đối tượng hiện tại | **Bị thay đổi**, bên gọi có thể thấy `"AB"` |
| `text = new StringBuilder("B")` | Biến cục bộ `text` trỏ tới đối tượng khác | **Không bị thay đổi** bởi phép gán này |
| `text = null` | Biến cục bộ nhận `null` | Đối tượng cũ vẫn là đối tượng sắp trả về |
| `text.append("B"); text = new StringBuilder("C");` | Đối tượng cũ bị sửa rồi biến trỏ nơi khác | Bên gọi nhận đối tượng cũ đã chứa `"AB"` |
| `text.delete(0, text.length())` | Nội dung của đối tượng cũ | Bên gọi thấy đối tượng cũ đã bị xóa nội dung |

Đây là khác biệt **mutation (sửa trạng thái của đối tượng)** và **reassignment/rebinding (gán lại biến tham chiếu)**.

## 5. Chương trình chạy được: đối chiếu mutation và reassignment

Tạo tệp **`ReferenceFinallyDemo.java`**:

```java
public class ReferenceFinallyDemo {
    static StringBuilder mutated() {
        StringBuilder text = new StringBuilder("A");
        try {
            return text;
        } finally {
            text.append("B");
        }
    }

    static StringBuilder reassigned() {
        StringBuilder text = new StringBuilder("A");
        try {
            return text;
        } finally {
            text = new StringBuilder("B");
        }
    }

    static StringBuilder both() {
        StringBuilder text = new StringBuilder("A");
        try {
            return text;
        } finally {
            text.append("!");
            text = new StringBuilder("C");
        }
    }

    static StringBuilder assignedNull() {
        StringBuilder text = new StringBuilder("A");
        try {
            return text;
        } finally {
            text = null;
        }
    }

    public static void main(String[] args) {
        System.out.println("mutated=" + mutated());
        System.out.println("reassigned=" + reassigned());
        System.out.println("both=" + both());
        System.out.println("assignedNull=" + assignedNull());
    }
}
```

```bash
javac ReferenceFinallyDemo.java
java ReferenceFinallyDemo
```

Kết quả `stdout`:

```text
mutated=AB
reassigned=A
both=A!
assignedNull=A
```

Diễn giải:

- `mutated()`: biểu thức `return` giữ tham chiếu đến đối tượng A; `finally` sửa **chính đối tượng đó** thành AB.
- `reassigned()`: biểu thức `return` đã giữ tham chiếu đến đối tượng A; `finally` chỉ đổi biến cục bộ sang đối tượng B.
- `both()`: đối tượng A nhận thêm `!` trước khi biến được gán sang C; bên gọi nhận A!.
- `assignedNull()`: biến cục bộ thành `null` nhưng giá trị tham chiếu đã chuẩn bị để trả vẫn chỉ đến đối tượng A; bên gọi nhận đối tượng không null.

Lưu ý `StringBuilder` là mutable (có thể thay đổi). `System.out.println("..." + someStringBuilder)` sử dụng nội dung hiện thời của đối tượng để tạo chuỗi hiển thị; đó là lý do dòng in phản ánh các thay đổi trong `finally`.

### Một quy tắc đủ để dự đoán cả bốn kết quả

**Giá trị tham chiếu được đánh giá khi gặp `return`; đối tượng được tham chiếu có thể tiếp tục bị thay đổi trước khi phương thức thực sự trả về.** Việc gán lại biến cục bộ không thay thế giá trị tham chiếu đã được chuẩn bị.

## 6. Đối tượng do người lập trình định nghĩa và aliasing

Không chỉ `StringBuilder` mới có hành vi này. Mọi đối tượng có trạng thái mutable đều có thể bị sửa trong `finally` và cho bên gọi quan sát.

Tạo tệp **`HolderFinallyDemo.java`**:

```java
public class HolderFinallyDemo {
    static class Holder {
        int count;

        Holder(int count) {
            this.count = count;
        }
    }

    static Holder create() {
        Holder holder = new Holder(1);
        try {
            return holder;
        } finally {
            holder.count = 2;
        }
    }

    public static void main(String[] args) {
        Holder result = create();
        System.out.println("count=" + result.count);
    }
}
```

```bash
javac HolderFinallyDemo.java
java HolderFinallyDemo
```

`stdout`:

```text
count=2
```

`result` trỏ đến đúng đối tượng `Holder` mà biến cục bộ `holder` đã trỏ tới khi `return` được đánh giá. `finally` sửa trường `count` trên cùng đối tượng đó thành 2.

Khái niệm *aliasing* là có nhiều tham chiếu trỏ đến cùng một đối tượng. Thay đổi trạng thái qua bất kỳ tham chiếu hợp lệ nào sẽ làm thay đổi đối tượng, nên các tham chiếu khác có thể quan sát thay đổi (trong phạm vi các quy tắc đồng bộ/visibility thích hợp nếu có nhiều luồng).

### Phân biệt “sửa đối tượng” và “đổi một tham chiếu khác”

```java
Holder first = new Holder(1);
Holder alias = first;
alias.count = 2;          // Sửa cùng một đối tượng; first.count cũng là 2.
alias = new Holder(99);   // Chỉ đổi biến alias, first vẫn trỏ tới đối tượng cũ.
```

Đây là đoạn mã minh họa, giả định lớp `Holder` ở ví dụ trên. Tương tự, trong `finally`, biến đang được gán lại là một ô lưu giá trị tham chiếu riêng; nó không tự cập nhật giá trị trả về đã được đánh giá.

## 7. Mảng và collection: nội dung thay đổi, tham chiếu đã giữ vẫn như cũ

Mảng Java là đối tượng có thể thay đổi phần tử. Xét:

```java
static int[] values() {
    int[] data = {1, 2};
    try {
        return data;
    } finally {
        data[0] = 99;
    }
}
```

Đây là mảnh phương thức minh họa. Bên gọi nhận **cùng mảng** sau khi phần tử đầu đã được sửa thành 99. Không có một bản sao mảng tự động nào phát sinh vì `return data`.

Chương trình đầy đủ **`ArrayFinallyDemo.java`**:

```java
import java.util.Arrays;

public class ArrayFinallyDemo {
    static int[] create() {
        int[] values = {1, 2, 3};
        try {
            return values;
        } finally {
            values[0] = 7;
            values = new int[] {9, 9, 9};
        }
    }

    public static void main(String[] args) {
        System.out.println(Arrays.toString(create()));
    }
}
```

```bash
javac ArrayFinallyDemo.java
java ArrayFinallyDemo
```

Kết quả:

```text
[7, 2, 3]
```

Dòng `values[0] = 7` sửa mảng được chuẩn bị trả về. Dòng `values = new int[] {9, 9, 9}` chỉ gán biến `values` sang một mảng mới nên không thay thế mảng trả về.

Điều tương tự áp dụng cho collection mutable như `ArrayList`:

```java
static java.util.List<String> createList() {
    java.util.List<String> items = new java.util.ArrayList<>();
    try {
        return items;
    } finally {
        items.add("added in finally");
    }
}
```

Đây là mảnh phương thức minh họa. Nếu hoàn thành bình thường, bên gọi nhận danh sách có phần tử `"added in finally"`. Tuy nhiên, đừng dùng `finally` như một chỗ “âm thầm bổ sung dữ liệu trước khi trả về”; điều đó khiến hợp đồng phương thức khó đọc và dễ gây nhầm lẫn.

## 8. Đối tượng immutable: gán lại biến không sửa đối tượng đã trả

`String` trong Java là immutable: không có thao tác nào sửa nội dung của đối tượng `String` hiện có. Biểu thức `text + "B"` tạo ra một giá trị chuỗi mới (hoặc được runtime tối ưu theo cách vẫn giữ ngữ nghĩa immutable), rồi gán giá trị tham chiếu mới cho biến `text`.

Tạo **`ImmutableFinallyDemo.java`**:

```java
public class ImmutableFinallyDemo {
    static String text() {
        String value = "A";
        try {
            return value;
        } finally {
            value = value + "B";
        }
    }

    public static void main(String[] args) {
        System.out.println(text());
    }
}
```

```bash
javac ImmutableFinallyDemo.java
java ImmutableFinallyDemo
```

`stdout`:

```text
A
```

Cơ chế giống `reassigned()`: `try` đã chuẩn bị trả tham chiếu đến chuỗi `"A"`. Trong `finally`, phép nối chuỗi tạo nội dung mới và gán biến cục bộ `value`, không làm đối tượng chuỗi `"A"` thay đổi.

Lưu ý: “trả về đối tượng” không tự đồng nghĩa “bên gọi sẽ thấy nội dung thay đổi”. Phải xét **đối tượng có mutable hay không** và `finally` thực sự làm gì.

Các kiểu bao như `Integer`, `Long` cũng là immutable. Nếu `finally` gán `number = number + 1` cho một biến `Integer`, đó là thao tác tính/gán lại giá trị tham chiếu của biến, không sửa đối tượng `Integer` đã được chuẩn bị trả về.

## 9. `finally` tự `return` hoặc `throw`: ngoại lệ quan trọng

Cho đến lúc này, các ví dụ đều có `finally` **hoàn thành bình thường**. Khi `finally` tự `return` hoặc ném ngoại lệ, Java áp dụng quy tắc ưu tiên lý do hoàn thành đột ngột mới: `finally` có thể **thay thế** kết quả hoặc ngoại lệ đang chờ.

### 9.1. `return` trong `finally` ghi đè kết quả cũ

Tạo tệp **`FinallyReturnOverrideDemo.java`**:

```java
public class FinallyReturnOverrideDemo {
    static int value() {
        try {
            return 10;
        } finally {
            return 20;
        }
    }

    public static void main(String[] args) {
        System.out.println(value());
    }
}
```

```bash
javac FinallyReturnOverrideDemo.java
java FinallyReturnOverrideDemo
```

Kết quả:

```text
20
```

`return 10` đã được đánh giá, nhưng `finally` tự thực hiện `return 20`. Đây là **câu lệnh trả về khác** làm thay thế kết quả đang chờ. Không được lẫn với việc chỉ thay đổi biến cục bộ như `value = 20` trong `finally`.

### 9.2. `return` trong `finally` còn có thể che khuất ngoại lệ

Tạo tệp **`FinallySwallowDemo.java`**:

```java
public class FinallySwallowDemo {
    static int risky() {
        try {
            throw new IllegalStateException("original failure");
        } finally {
            return 42;
        }
    }

    public static void main(String[] args) {
        System.out.println(risky());
    }
}
```

```bash
javac FinallySwallowDemo.java
java FinallySwallowDemo
```

`stdout`:

```text
42
```

Ngoại lệ `IllegalStateException` được ném trong `try`, nhưng `finally` trả 42 nên lý do kết thúc mới thay thế ngoại lệ đang lan truyền. Trình biên dịch Java chấp nhận cấu trúc này, nhưng nó **che mất lỗi** và thường gây khó chẩn đoán.

**Khuyến nghị:** không viết `return`, `break` hay `continue` để chuyển hướng luồng thực thi ra khỏi `finally` khi đang xử lý tài nguyên/ngoại lệ; để khối `finally` chuyên thực hiện dọn dẹp cần thiết.

### 9.3. `finally` ném ngoại lệ mới cũng có thể ghi đè kết quả cũ

Ví dụ mảnh mã:

```java
static String read() {
    try {
        return "ready";
    } finally {
        throw new IllegalStateException("cleanup failed");
    }
}
```

Khi gọi `read()`, lời gọi không trả `"ready"`; nó kết thúc bằng `IllegalStateException("cleanup failed")`. Quy tắc này cũng áp dụng khi `try` đã ném ngoại lệ khác: một ngoại lệ mới từ `finally` có thể che nguyên nhân ban đầu.

Đây là lý do việc dọn dẹp trong `finally` cần được thiết kế cẩn thận. Với tài nguyên triển khai `AutoCloseable`, `try-with-resources` có cơ chế quản lý lỗi đóng tài nguyên bằng **suppressed exceptions**; đừng tự giả định rằng một `finally` thủ công cũng tự động lưu ngoại lệ ban đầu dưới dạng suppressed.

## 10. Thời điểm đánh giá biểu thức trả về và các side effect

Không chỉ `return variable;`, Java còn đánh giá các biểu thức phức tạp **trước khi chạy `finally`**. Ví dụ:

```java
static int next() {
    int count = 1;
    try {
        return count++;
    } finally {
        count += 10;
    }
}
```

Đây là mảnh phương thức. `count++` trả về **giá trị cũ 1** cho biểu thức, đồng thời tăng biến thành 2. Sau đó `finally` tăng biến thành 12. Giá trị trả về vẫn là **1**.

Nếu dùng `return ++count;`, biểu thức sẽ tạo kết quả là 2 trước khi `finally` chạy, vì toán tử tiền tố tăng rồi trả giá trị mới.

### Ví dụ chạy được: kiểm tra thứ tự in

Tạo tệp **`ReturnEvaluationOrderDemo.java`**:

```java
public class ReturnEvaluationOrderDemo {
    static StringBuilder make() {
        System.out.println("evaluate return expression");
        return new StringBuilder("created");
    }

    static StringBuilder produce() {
        try {
            return make();
        } finally {
            System.out.println("execute finally");
        }
    }

    public static void main(String[] args) {
        StringBuilder result = produce();
        System.out.println("caller sees: " + result);
    }
}
```

```bash
javac ReturnEvaluationOrderDemo.java
java ReturnEvaluationOrderDemo
```

Kết quả:

```text
evaluate return expression
execute finally
caller sees: created
```

Từ kết quả có thể rút ra: phương thức `make()` được gọi và cho kết quả **trước khi** `finally` của `produce()` chạy; chỉ sau đó `main` mới nhận đối tượng.

Nếu việc đánh giá **chính biểu thức trả về** ném ngoại lệ (ví dụ `return make();` nhưng `make()` ném lỗi), không có giá trị trả về được tạo ra. `finally` liên quan vẫn được xử lý theo quy tắc thoát đột ngột, rồi ngoại lệ tiếp tục lan truyền nếu `finally` hoàn thành bình thường.

## 11. `finally`, đồng bộ hóa và aliasing: các giới hạn thực tế

### 11.1. Quan sát đối tượng sau khi nhận khác với dữ liệu snapshot

Khi API cần **snapshot bất biến**, bạn không nên vô tình trả đối tượng mutable rồi sửa nó trong `finally`. Ví dụ một phương thức trả về `List` mà `finally` còn thêm/xóa phần tử sẽ tạo kết quả khó đoán cho người đọc. Một lựa chọn dễ hiểu là hoàn tất các thay đổi **trước khi `return`**, sau đó trả giá trị hoàn chỉnh hoặc snapshot phù hợp.

Dùng `List.copyOf(items)` có thể tạo danh sách không sửa đổi được ở tầng container, nhưng đó không nhất thiết là *deep copy* của các đối tượng phần tử mutable. Việc chọn snapshot và ranh giới sao chép phụ thuộc hợp đồng dữ liệu.

### 11.2. Không phải mọi sửa đổi đối tượng đều có nghĩa mọi luồng nhìn thấy ngay

Trong các ví dụ đơn luồng của bài, bên gọi thực hiện tiếp sau khi `finally` chạy nên quan sát thay đổi xác định. Với nhiều luồng cùng truy cập đối tượng mutable, cần xem thêm Java Memory Model: đồng bộ hóa, `volatile`, khóa hoặc cấu trúc concurrent thích hợp. Việc sửa một trường trong `finally` **không tự tạo ra** bảo đảm visibility cho mọi luồng không đồng bộ.

### 11.3. `finally` không phải hệ thống giao dịch

Sửa đối tượng rồi ném ngoại lệ có thể để trạng thái đã thay đổi. Java **không tự rollback** các thay đổi đối tượng chỉ vì khối `try` có lỗi. Nếu một bất biến dữ liệu quan trọng, hãy thiết kế bước chuẩn bị, xác thực và commit rõ ràng hoặc dùng cơ chế transaction phù hợp.

### 11.4. Tránh cố thao tác quá nhiều trong `finally`

Mục đích điển hình của `finally` là dọn dẹp tài nguyên cần thiết khi rời phạm vi, như nhả một tài nguyên không được quản lý tự động. Trong mã hiện đại, ưu tiên `try-with-resources` cho `AutoCloseable`. Trộn việc chỉnh sửa giá trị kết quả vào `finally` gây bất ngờ và khiến API khó bảo trì.

## 12. Bảng đối chiếu nhanh

| Biểu thức trong `try` | Thao tác trong `finally` | Kết quả nếu `finally` hoàn thành bình thường |
| --- | --- | --- |
| `int x = 1; return x;` | `x = 9;` | Trả **1** |
| `int x = 1; return x++;` | `x = 9;` | Trả **1** (hậu tố trả giá trị cũ) |
| `int x = 1; return ++x;` | `x = 9;` | Trả **2** |
| `String s = "A"; return s;` | `s = "B";` | Trả `"A"` |
| `StringBuilder b = new StringBuilder("A"); return b;` | `b.append("B");` | Trả cùng đối tượng hiện chứa `"AB"` |
| `StringBuilder b = new StringBuilder("A"); return b;` | `b = new StringBuilder("B");` | Trả đối tượng chứa `"A"` |
| `int[] a = {1}; return a;` | `a[0] = 7;` | Trả mảng có phần tử đầu **7** |
| `Holder h = new Holder(1); return h;` | `h.count = 2;` | Trả `Holder` có `count == 2` |
| `return 1;` | `return 2;` | Trả **2**, vì `finally` có lệnh return riêng |
| `return 1;` | `throw new IllegalStateException();` | **Ném ngoại lệ**, không trả số |

Bảng ghi kết quả theo Java 17 và giả định thao tác bên trong không có lỗi mới ngoài những trường hợp đã nêu. Các đoạn viết gọn ở cột `try`/`finally` dùng để so sánh ý nghĩa, không phải từng tệp Java độc lập.

## 13. Các lỗi hiểu nhầm phổ biến

**“`finally` chạy sau `return` nên thay đổi biến trong `finally` sẽ luôn cập nhật giá trị trả về.”** Sai. Biểu thức `return` đã được đánh giá và giá trị dự kiến đã được lưu trước khi `finally` bắt đầu.

**“`finally` chạy nên không thể ảnh hưởng đối tượng đang trả về.”** Sai. Nếu `finally` dùng một tham chiếu tới **cùng đối tượng mutable** để sửa nội dung, bên gọi có thể nhận đối tượng đã bị sửa.

**“Gán biến tham chiếu sang đối tượng mới giống như sửa đối tượng cũ.”** Sai. Gán lại biến đổi **giá trị tham chiếu trong biến**; nó không thay đổi đối tượng mà giá trị trả về đã trỏ tới.

**“`text = null` trong `finally` khiến phương thức trả `null`.”** Không đúng nếu `return text` đã đánh giá trước đó với tham chiếu không null và `finally` hoàn thành bình thường.

**“`String` và `StringBuilder` thay đổi giống nhau.”** Sai. `String` immutable; `StringBuilder` mutable. Phép `String.concat` hay `+` không sửa đối tượng `String` gốc.

**“Tham số, biến cục bộ và giá trị trả về đều trỏ vào một ô nhớ tham chiếu duy nhất.”** Sai. Chúng có thể chứa **các bản sao giá trị tham chiếu** đến cùng đối tượng, nhưng là những biến/giá trị khác nhau. Thay đổi một tham chiếu không bắt buộc thay đổi tham chiếu còn lại.

**“`finally` chỉ dọn dẹp nên không thể thay đổi kiểu hoàn thành của phương thức.”** Sai. `return` hoặc `throw` trong `finally` có thể thay thế giá trị trả về hoặc ngoại lệ cũ.

**“Nếu `finally` ném lỗi mới, Java tự bảo toàn lỗi cũ dưới dạng suppressed.”** Không đúng với `finally` thủ công nói chung. Cơ chế suppressed có quy tắc cụ thể của `try-with-resources`; cần phân biệt.

**“Thấy một đối tượng được sửa trong `finally` nghĩa là Java return-by-reference.”** Sai. Java luôn truyền/trả **giá trị**; với kiểu tham chiếu, giá trị ấy là **tham chiếu tới đối tượng**, và việc hai nơi cùng trỏ tới một đối tượng giải thích hành vi mutation.

**“Đã `return` nghĩa là `finally` có thể bị bỏ qua trong mọi trường hợp.”** Sai. Khi trả về theo đường bình thường của ngôn ngữ, `finally` liên quan được chạy. Tuy nhiên, điều này không phải bảo đảm dọn dẹp khi JVM/tiến trình bị kết thúc cưỡng bức.

## 14. Bài tập tự kiểm tra

### Bài 1: Biến nguyên thủy

```java
static int test() {
    int n = 2;
    try {
        return n;
    } finally {
        n += 5;
    }
}
```

**Kết quả?** `test()` trả **2**. `finally` sửa biến thành 7 nhưng không thay đổi giá trị đã chuẩn bị trả về.

### Bài 2: Sửa đối tượng

```java
static StringBuilder test() {
    StringBuilder b = new StringBuilder("X");
    try {
        return b;
    } finally {
        b.append("Y");
    }
}
```

**Bên gọi quan sát chuỗi gì?** `XY`, vì chính đối tượng được trả về đã bị sửa.

### Bài 3: Gán lại biến

```java
static StringBuilder test() {
    StringBuilder b = new StringBuilder("X");
    try {
        return b;
    } finally {
        b = new StringBuilder("Y");
    }
}
```

**Bên gọi quan sát chuỗi gì?** `X`. Biến cục bộ mới trỏ sang đối tượng Y, còn kết quả `return` vẫn giữ tham chiếu đến đối tượng X.

### Bài 4: Mảng

```java
static int[] test() {
    int[] a = {3};
    try {
        return a;
    } finally {
        a[0] = 8;
        a = new int[]{99};
    }
}
```

**Bên gọi thấy phần tử đầu tiên là mấy?** **8**. Thao tác ghi phần tử sửa mảng cũ, còn phép gán biến sau đó chỉ đổi tham chiếu trong biến.

### Bài 5: `return` trong `finally`

```java
static int test() {
    try {
        return 1;
    } finally {
        return 2;
    }
}
```

**Kết quả?** **2**. `finally` tự có `return` mới, ghi đè kết quả đang chờ. Đây là một ví dụ cần tránh trong ứng dụng thực tế.

### Bài 6: Ngoại lệ khi chuẩn bị trả về

Giả sử `return produce();` nằm trong `try` và `produce()` ném `IOException` trước khi tạo kết quả, còn `finally` chỉ ghi log rồi kết thúc bình thường. **Phương thức có trả giá trị không?** Không. Ngoại lệ `IOException` tiếp tục lan truyền sau `finally` nếu không bị một handler phù hợp xử lý.

### Bài 7: Thiết kế API

Nếu một phương thức muốn trả snapshot bất biến của một danh sách, có nên trả `ArrayList` rồi tiếp tục `add` phần tử trong `finally` không? **Không nên**. Nên hoàn tất dữ liệu trước khi trả và chọn kiểu/cách sao chép biểu thị đúng mức bất biến cần thiết.

## 15. Tài liệu tham khảo chuẩn

- [Java Language Specification 17 — `14.17 The return Statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.17): đánh giá biểu thức trả về và cách `return` hoàn thành đột ngột.
- [Java Language Specification 17 — `14.20.2 Execution of try-finally and try-catch-finally](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20.2): thứ tự chạy `finally` và quy tắc thay thế lý do thoát.
- [Java Language Specification 17 — `4.3 Reference Types and Values](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.3): ý nghĩa giá trị tham chiếu và đối tượng.
- [Java SE 17 API — StringBuilder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/StringBuilder.html): đối tượng chuỗi mutable.
- [Java SE 17 API — String](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html): chuỗi immutable.

**Ghi nhớ:** `return` đánh giá và giữ **giá trị trả về** trước khi `finally` chạy. Với biến nguyên thủy, đó là giá trị nguyên thủy; với biến tham chiếu, đó là giá trị tham chiếu đến đối tượng. Thay đổi **biến cục bộ** không viết lại kết quả đã giữ, nhưng thay đổi **trạng thái của đối tượng đã được tham chiếu** có thể hiển thị ở bên gọi. Quy tắc này áp dụng khi `finally` hoàn thành bình thường; `return` hoặc `throw` mới trong `finally` có thể thay thế toàn bộ kết quả.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="finally-006">Return hoặc throw trong finally che mất kết quả trước đó</a>

<details>
<summary>Xem chi tiết</summary>

# `return` hoặc `throw` trong `finally` che mất kết quả trước đó

Trong Java, khối `finally` thường được thực thi khi thoát khỏi cấu trúc `try`, cho dù luồng đang kết thúc bình thường, chuẩn bị `return`, hoặc đang lan truyền ngoại lệ. Đây là cơ chế quan trọng để dọn dẹp tài nguyên, nhưng cũng mang một rủi ro:

> **Nếu `finally` tự kết thúc bất thường bằng `return` hoặc `throw`, lý do hoàn tất đang chờ từ `try` hoặc `catch` bị thay thế bằng lý do mới của `finally`.**

Ví dụ, một phương thức định trả `10` nhưng `finally` lại `return 20;` thì caller nhận `20`. Một phương thức định ném `IllegalStateException("original")` nhưng `finally` lại ném `IllegalArgumentException("cleanup failed")` thì caller nhận **ngoại lệ mới**, không phải ngoại lệ gốc. Đây không chỉ là vấn đề phong cách code; nó có thể làm mất dấu lỗi, trả kết quả sai và khiến chương trình báo thành công dù thao tác thực sự đã thất bại.

## 1. Quy tắc nền tảng: lý do hoàn tất của `finally` có thể ghi đè lý do trước

Java phân biệt hai kiểu hoàn tất:

- **Hoàn tất bình thường (*normal completion*):** một khối chạy đến hết mà không chuyển điều khiển ra bằng `return`, `throw`, `break` hoặc `continue`.
- **Hoàn tất bất thường (*abrupt completion*):** quá trình rời khỏi khối do một nguyên nhân như `return`, `throw`, `break` hoặc `continue`.

Khi rời `try` hay `catch`, một khối `finally` tương ứng sẽ được thực thi theo quy tắc của ngôn ngữ. Lúc đó:

1. Nếu `finally` **hoàn tất bình thường**, lý do hoàn tất trước đó được giữ nguyên. Một `return` từ `try` tiếp tục trả kết quả; một ngoại lệ từ `try` tiếp tục lan truyền.
2. Nếu `finally` **hoàn tất bất thường**, lý do mới từ `finally` thay thế lý do hoàn tất trước đó, dù lý do trước là một giá trị trả về hay một ngoại lệ.

Ta có thể hình dung:

```text
try hoặc catch chuẩn bị hoàn tất với lý do R
                  │
                  ▼
              chạy finally
                  │
          ┌───────┴────────┐
          ▼                ▼
    finally bình thường  finally bất thường với lý do S
          │                │
          ▼                ▼
       giữ R              dùng S
                         (R bị bỏ)
```

Các trường hợp `R` hoặc `S` có thể là `return`, `throw`, `break` hoặc `continue`. Bài này tập trung vào **`return` và `throw` bên trong `finally`** vì chúng dễ tạo lỗi nghiêm trọng nhất: trả sai kết quả hoặc che khuất nguyên nhân thực sự.

## 2. Ma trận hành vi: `try` làm gì, `finally` làm gì?

| Kết quả từ `try` / `catch` | `finally` hoàn tất thế nào? | Kết quả mà caller nhìn thấy |
| --- | --- | --- |
| Chạy đến cuối bình thường | Chạy đến cuối bình thường | Tiếp tục luồng bình thường |
| `return A;` | Chạy đến cuối bình thường | Trả `A` |
| `throw E1;` | Chạy đến cuối bình thường | Ném `E1` |
| `return A;` | `return B;` | **Trả `B`**, `A` bị bỏ |
| `throw E1;` | `return B;` | **Trả `B`**, `E1` bị che |
| `return A;` | `throw E2;` | **Ném `E2`**, không trả `A` |
| `throw E1;` | `throw E2;` | **Ném `E2`**, `E1` bị che |
| Chạy đến cuối bình thường | `return B;` | Trả `B` |
| Chạy đến cuối bình thường | `throw E2;` | Ném `E2` |

Trong bảng, `A` và `B` chỉ là các giá trị minh họa; `E1` và `E2` là các ngoại lệ. “Bị che” có nghĩa rằng **nếu không được lưu bằng một cơ chế riêng, caller không còn nhận được lỗi hoặc kết quả trước đó**. Java không tự gắn `E1` thành `cause` hay *suppressed exception* của `E2` chỉ vì `finally` ném lỗi mới.

**Không nhầm:** một `finally` thông thường chỉ ghi log hoặc giải phóng tài nguyên rồi hoàn tất bình thường **không** tự làm mất giá trị trả về hay ngoại lệ từ `try`.

## 3. `return` trong `finally` ghi đè `return` ở `try`

Xét phương thức:

```java
static int wrongResult() {
    try {
        return 10;
    } finally {
        return 20;
    }
}
```

Đây là một **phương thức minh họa**, cần đặt trong một lớp Java. Kết quả `wrongResult()` là **`20`**.

Trình tự:

1. `try` đánh giá biểu thức `10` và chuẩn bị trả về.
2. Trước khi phương thức thực sự trả cho caller, Java chạy `finally`.
3. `finally` thực hiện `return 20;`, tạo **một lý do hoàn tất mới**.
4. Giá trị `10` đang chờ bị bỏ; caller nhận `20`.

Nhiều người tưởng `finally` chỉ “chạy thêm” sau khi đã `return` xong. Điều này sai: `finally` chạy **trước khi kết thúc lời gọi**, và có thể thay đổi kết quả hoàn tất.

Điều nguy hiểm là code có thể trông như đã xử lý đúng trong `try`, nhưng một `return` vô tình nằm trong nhánh dọn dẹp sẽ khiến kết quả không phản ánh thao tác chính.

### `return` trong `catch` cũng có thể bị ghi đè

```java
static String fallback() {
    try {
        throw new IllegalArgumentException("invalid");
    } catch (IllegalArgumentException ex) {
        return "recovered";
    } finally {
        return "overridden";
    }
}
```

Caller nhận `"overridden"`, không phải `"recovered"`. `finally` được thực thi khi handler `catch` chuẩn bị trả về, và `return` cuối cùng thay thế giá trị của handler.

Nếu `finally` không có `return`/`throw` gây hoàn tất bất thường, `"recovered"` sẽ được giữ nguyên.

## 4. `return` trong `finally` có thể nuốt luôn ngoại lệ

Ví dụ:

```java
static int dangerouslySilent() {
    try {
        throw new IllegalStateException("database write failed");
    } finally {
        return 0;
    }
}
```

Đây là phương thức minh họa. **Kết quả là `0`**; caller không nhận `IllegalStateException`.

Diễn biến:

1. Thao tác trong `try` ném `IllegalStateException`.
2. Java chuẩn bị cho ngoại lệ lan ra caller.
3. `finally` chạy và gặp `return 0;`.
4. `return` mới thay thế hoàn tất do `throw`. Ngoại lệ đang chờ bị bỏ.

Đây là một **anti-pattern nguy hiểm**: phương thức có thể báo một giá trị bình thường, thậm chí được diễn giải là thành công, trong khi thao tác chính thật ra đã thất bại.

Nếu caller dùng giá trị `0` như số bản ghi đã xử lý hoặc mã kết quả, lỗi ghi cơ sở dữ liệu có thể bị che mất hoàn toàn. Các cơ chế ở tầng trên như retry, transaction rollback dựa vào exception, logging lỗi hoặc cảnh báo giám sát có thể không nhận được tín hiệu cần thiết.

**Không dùng `return` trong `finally` để đưa ra giá trị mặc định khi `try` lỗi.** Nếu muốn fallback, đặt logic trong `catch` phù hợp, xác minh rằng việc phục hồi là hợp lệ, rồi trả một kết quả có nghĩa.

## 5. `throw` trong `finally` ghi đè giá trị đang chuẩn bị trả

```java
static int resultLost() {
    try {
        return 42;
    } finally {
        throw new IllegalStateException("cleanup failed");
    }
}
```

Ở đây, caller **không nhận `42`**. Thay vào đó, nó nhận `IllegalStateException("cleanup failed")`.

Điều này có thể xảy ra ngay cả khi phần xử lý chính thành công: một thao tác giải phóng tài nguyên thất bại trong `finally` làm toàn bộ lời gọi kết thúc bằng ngoại lệ. Đôi khi đó là chính sách hợp lệ vì đóng/ghi dữ liệu cuối cùng không thành công, nhưng cần quyết định có chủ đích. Không được suy luận rằng có `return` trong `try` thì phương thức chắc chắn trả về.

Một điểm khác: **biểu thức trả về đã được đánh giá trước khi `finally` chạy**, nhưng kết quả vẫn có thể không đến được caller nếu `finally` ném ngoại lệ mới.

## 6. `throw` trong `finally` che khuất một ngoại lệ trước đó

```java
static void originalErrorLost() {
    try {
        throw new IllegalArgumentException("invalid request");
    } finally {
        throw new IllegalStateException("cleanup failed");
    }
}
```

Ngoại lệ mà caller nhận là **`IllegalStateException("cleanup failed")`**. `IllegalArgumentException("invalid request")` không tự động xuất hiện trong `getCause()` hay `getSuppressed()` của ngoại lệ mới.

Hãy phân biệt:

- **Ngoại lệ chính của thao tác:** phát sinh khi xử lý yêu cầu, ví dụ dữ liệu đầu vào sai.
- **Ngoại lệ trong dọn dẹp:** phát sinh khi gọi `close()`, ghi log, giải phóng tài nguyên hoặc một bước cleanup khác.
- **Ngoại lệ mà caller quan sát được:** với `finally` thông thường ném lỗi mới, thường là **ngoại lệ cleanup**, vì nó thay thế sự cố chính.

Khi xem stack trace, người điều tra có thể chỉ thấy vị trí cleanup và tin nhầm cleanup là sự cố đầu tiên. Điều này rất tốn công trong các hệ thống có nhiều tầng `try-catch-finally`.

### `finally` không tự bảo toàn `cause`

Giả sử:

```java
try {
    throw new IllegalArgumentException("original");
} finally {
    throw new IllegalStateException("replacement");
}
```

Không có quy tắc tự động gắn `IllegalArgumentException` vào `getCause()` của `IllegalStateException`. Muốn bảo toàn quan hệ nguyên nhân, mã phải **chủ động** giữ nguyên đối tượng hoặc dùng cấu trúc có cơ chế thích hợp như `try-with-resources`.

Điều này khác với trường hợp lập trình viên chủ động viết:

```java
throw new IllegalStateException("higher-level failure", original);
```

Ở câu thứ hai, `original` là một biến `Throwable` đã được lưu hợp lệ ở phạm vi hiện tại. Chính constructor nhận `cause` tạo quan hệ nguyên nhân, không phải vì câu lệnh được viết trong `finally`.

## 7. Ví dụ Java 17+ hoàn chỉnh: bốn cách ghi đè kết quả

Lưu chương trình sau thành **`FinallyOverrideDemo.java`**. Mọi thông điệp và kiểu lỗi được kiểm soát, nên stdout có thể dự đoán chính xác.

```java
public class FinallyOverrideDemo {

    static int returnOverReturn() {
        try {
            System.out.println("try A: return 10");
            return 10;
        } finally {
            System.out.println("finally A: return 20");
            return 20;
        }
    }

    static int returnOverThrow() {
        try {
            System.out.println("try B: throw original");
            throw new IllegalStateException("original");
        } finally {
            System.out.println("finally B: return 30");
            return 30;
        }
    }

    static int throwOverReturn() {
        try {
            System.out.println("try C: return 40");
            return 40;
        } finally {
            System.out.println("finally C: throw cleanup");
            throw new IllegalArgumentException("cleanup");
        }
    }

    static void throwOverThrow() {
        try {
            System.out.println("try D: throw original");
            throw new IllegalStateException("original");
        } finally {
            System.out.println("finally D: throw replacement");
            throw new IllegalArgumentException("replacement");
        }
    }

    static int safeFinally() {
        int value = 7;
        try {
            System.out.println("try E: return 7");
            return value;
        } finally {
            value = 8;
            System.out.println("finally E: local value is " + value);
        }
    }

    public static void main(String[] args) {
        System.out.println("A result=" + returnOverReturn());
        System.out.println("B result=" + returnOverThrow());

        try {
            System.out.println("C result=" + throwOverReturn());
        } catch (IllegalArgumentException ex) {
            System.out.println("C caught=" + ex.getMessage());
        }

        try {
            throwOverThrow();
        } catch (IllegalArgumentException ex) {
            System.out.println("D caught=" + ex.getMessage());
            System.out.println("D cause=" + ex.getCause());
            System.out.println(
                    "D suppressed=" + ex.getSuppressed().length);
        }

        System.out.println("E result=" + safeFinally());
    }
}
```

Biên dịch/chạy với **JDK 17 trở lên**:

```bash
javac -encoding UTF-8 FinallyOverrideDemo.java
java FinallyOverrideDemo
```

**Stdout chính xác:**

```text
try A: return 10
finally A: return 20
A result=20
try B: throw original
finally B: return 30
B result=30
try C: return 40
finally C: throw cleanup
C caught=cleanup
try D: throw original
finally D: throw replacement
D caught=replacement
D cause=null
D suppressed=0
try E: return 7
finally E: local value is 8
E result=7
```

### Giải thích từng trường hợp

**A — `return` đè `return`:** `try` định trả `10`, `finally` trả `20`. Caller thấy `20`.

**B — `return` nuốt `throw`:** ngoại lệ `IllegalStateException("original")` trong `try` không còn được lan ra; `finally` trả `30`. Caller nhận `30` như một kết quả bình thường.

**C — `throw` đè `return`:** `try` chuẩn bị trả `40`, nhưng `finally` ném `IllegalArgumentException("cleanup")`. Lời gọi không trả `40` nên dòng `"C result=..."` không được in; handler ngoài in `C caught=cleanup`.

**D — `throw` đè `throw`:** `try` ném `IllegalStateException("original")`, còn `finally` ném `IllegalArgumentException("replacement")`. Caller bắt lỗi thay thế; `getCause()` là `null` và danh sách `getSuppressed()` rỗng, chứng minh nguyên nhân gốc **không được Java tự động gắn vào** trong mẫu `try-finally` này.

**E — `finally` hoàn tất bình thường:** `try` đánh giá `value` thành `7` trước khi trả, `finally` chỉ gán biến cục bộ thành `8` rồi in. Vì `finally` không `return`/`throw`, kết quả `7` được giữ nguyên.

Ví dụ này cố ý trình bày các cách viết **không nên dùng trong ứng dụng thật** (A–D) để kiểm chứng quy tắc của Java. Hãy học hành vi, **không sao chép `return` hay `throw` vô điều kiện trong `finally`** vào mã nghiệp vụ.

## 8. Thứ tự đánh giá biểu thức `return` trước `finally`

Một điểm tinh tế: Java **đánh giá biểu thức của `return` trước**, rồi thực thi `finally`, rồi mới hoàn tất việc trả giá trị nếu `finally` không thay đổi lý do hoàn tất.

Ví dụ với kiểu nguyên thủy:

```java
static int primitiveSnapshot() {
    int n = 5;
    try {
        return n;
    } finally {
        n = 9;
    }
}
```

Kết quả là **`5`**. Không phải `9`, vì giá trị của biểu thức `return n` đã được lấy trước khi gán lại `n` trong `finally`.

### Với đối tượng có thể thay đổi, caller có thể thấy trạng thái mới

```java
static StringBuilder referenceResult() {
    StringBuilder builder = new StringBuilder("A");
    try {
        return builder;
    } finally {
        builder.append("B");
    }
}
```

Caller nhận một tham chiếu tới đối tượng `StringBuilder` **đã chứa `"AB"`**. Vì biểu thức `return builder` lưu **tham chiếu**, việc sửa trạng thái của đối tượng trước khi lời gọi hoàn tất vẫn nhìn thấy được qua tham chiếu đó.

Không mâu thuẫn với quy tắc kiểu nguyên thủy:

- Với `int`, giá trị `5` đã được tính và không bị biến cục bộ `n` thay đổi ngược lại.
- Với `StringBuilder`, tham chiếu đã được tính, nhưng đối tượng mà tham chiếu trỏ tới vẫn có thể bị sửa trong `finally`.
- Nếu `finally` chỉ **gán biến `builder` sang một đối tượng khác**, tham chiếu đang chờ trả về vẫn trỏ tới đối tượng đã được đánh giá ở biểu thức `return`.
- Nếu `finally` ném lỗi hoặc `return` khác, caller không nhận kết quả ban đầu, dù biểu thức đã được đánh giá.

### Biểu thức `return` tự ném ngoại lệ thì sao?

```java
static int expressionFails() {
    try {
        return Integer.parseInt("not-a-number");
    } finally {
        System.out.println("cleanup");
    }
}
```

Ở đây, `Integer.parseInt` ném `NumberFormatException` **trước khi có giá trị `int` để trả**. `finally` in `cleanup`, rồi ngoại lệ gốc tiếp tục lan nếu `finally` hoàn tất bình thường. Nếu `finally` lại `return 0;` thì `NumberFormatException` có thể bị nuốt như trường hợp B.

Nhờ vậy, cần phân biệt “đã đánh giá giá trị để trả” với “biểu thức `return` bị lỗi khi đánh giá”: cả hai đều là những đường thoát có thể đi qua `finally`.

## 9. `catch` và `finally`: handler cùng cấp có bắt được lỗi do `finally` ném không?

**Không.** Các nhánh `catch` của một cấu trúc `try-catch-finally` xử lý ngoại lệ thoát ra từ **phần thân `try`**. Chúng không quay lại bắt ngoại lệ mới ném từ `finally` của chính cấu trúc ấy.

Ví dụ:

```java
try {
    System.out.println("work completed");
} catch (IllegalStateException ex) {
    System.out.println("caught by sibling");
} finally {
    throw new IllegalStateException("cleanup failed");
}
```

Mã trên in `work completed` rồi ném `IllegalStateException` khỏi toàn bộ cấu trúc. Nhánh `catch (IllegalStateException)` cùng cấp **không chạy** cho lỗi được ném từ `finally`.

Nếu muốn xử lý lỗi dọn dẹp trong một phạm vi riêng, có thể đặt một `try-catch` **bao ngoài** cấu trúc:

```java
try {
    try {
        System.out.println("work completed");
    } finally {
        throw new IllegalStateException("cleanup failed");
    }
} catch (IllegalStateException ex) {
    System.out.println("outer caught: " + ex.getMessage());
}
```

Kết quả:

```text
work completed
outer caught: cleanup failed
```

Ví dụ giải thích **ranh giới bắt lỗi**, không khuyến khích chủ động ném lỗi dọn dẹp chỉ để tầng ngoài bắt. Nếu cleanup có thể lỗi, cần chính sách quản lý tài nguyên phù hợp.

## 10. `finally` ở nhiều tầng: lý do hoàn tất có thể bị thay thế nhiều lần

Với `try-finally` lồng nhau, mỗi khối `finally` có cơ hội giữ hoặc thay thế lý do hoàn tất mà nó nhận được.

```java
static int nestedReturns() {
    try {
        try {
            return 1;
        } finally {
            return 2;
        }
    } finally {
        return 3;
    }
}
```

Kết quả cuối cùng là **`3`**:

1. `try` trong chuẩn bị trả `1`.
2. `finally` trong thay thành `return 2`.
3. Toàn bộ cấu trúc trong đang chuẩn bị trả `2`.
4. `finally` ngoài thay tiếp thành `return 3`.

Đây là một ví dụ **cố ý thể hiện hành vi xấu**. Càng nhiều khối `finally` có điều khiển luồng bất thường, càng khó biết lý do kết thúc cuối cùng là gì. Các quy tắc tương tự áp dụng khi một hoặc nhiều `finally` ném ngoại lệ.

Trong code thực tế, hãy để `finally` thực hiện **dọn dẹp có giới hạn**, còn việc quyết định giá trị trả về hoặc lỗi nghiệp vụ nên nằm ở `try`, `catch` hay tầng điều phối phù hợp.

## 11. Vì sao `return`/`throw` trong `finally` làm log và giám sát sai lệch?

Giả sử có một job:

```java
int processJob() {
    try {
        writeDatabase();
        return 1;
    } finally {
        return 0;
    }
}
```

Đây là **đoạn chống mẫu**, vì `writeDatabase()` chỉ là phương thức giả định. Nếu database báo lỗi bằng ngoại lệ unchecked, `finally` vẫn trả `0`; hệ thống điều phối có thể coi đó là kết quả bình thường thay vì thất bại cần retry.

Các hậu quả thực tế gồm:

- **Mất nguyên nhân gốc:** hệ thống log tập trung không nhận được ngoại lệ chính, hoặc chỉ thấy lỗi cleanup ở lớp ngoài.
- **Phân loại sai trạng thái:** caller nhận giá trị bình thường, dù thao tác chính không hoàn tất.
- **Không thực hiện retry hoặc cảnh báo:** cơ chế retry dựa trên exception không kích hoạt nếu lỗi bị `return` nuốt.
- **Dữ liệu dễ bị sai lệch:** một phần tác động có thể đã xảy ra trước ngoại lệ; trả thành công giả không bảo đảm rollback.
- **Stack trace gây hiểu lầm:** khi `finally` ném ngoại lệ mới mà không giữ nguyên nhân, vị trí cleanup có thể trở thành dấu vết lỗi duy nhất mà caller thấy.

Chính vì vậy, nhiều quy tắc lint/static analysis cảnh báo `return` trong `finally`. Đây là cảnh báo có cơ sở ngữ nghĩa, không chỉ là sở thích định dạng.

## 12. Cách sửa: để `finally` hoàn tất bình thường

Một thiết kế cơ bản:

```java
static int compute() {
    try {
        int answer = 42;
        return answer;
    } finally {
        System.out.println("compute: finished");
    }
}
```

Caller nhận `42`, vì `finally` chỉ in một thông điệp rồi hoàn tất bình thường. Nếu `try` ném ngoại lệ thì lời gọi vẫn ném ngoại lệ đó, trừ khi chính câu lệnh ghi log ở `finally` phát sinh ngoại lệ mới.

Cấu trúc có `finally` là hữu ích khi cần một công việc luôn phải thử thực hiện vào cuối phạm vi, ví dụ cập nhật số liệu thống kê hoặc giải phóng trạng thái. Tuy nhiên, **không nên giả định mọi lệnh dọn dẹp đều chắc chắn không ném lỗi**. Nếu cleanup có khả năng thất bại, chính sách xử lý phải được thiết kế cụ thể.

### Đừng đặt fallback trong `finally`

Mã lỗi:

```java
static int parseOrDefault(String raw) {
    try {
        return Integer.parseInt(raw);
    } finally {
        return 0;
    }
}
```

Mã này luôn trả `0`, kể cả khi chuỗi là `"123"`. Nếu `raw` không hợp lệ, `NumberFormatException` cũng bị che.

Mã thể hiện chính sách fallback rõ hơn:

```java
static int parseOrDefault(String raw) {
    try {
        return Integer.parseInt(raw);
    } catch (NumberFormatException ex) {
        return 0;
    }
}
```

Đây chỉ là một lựa chọn hợp lệ **nếu hợp đồng thật sự cho phép mặc định là `0` khi chuỗi không thể parse**. Nếu `0` không phân biệt được lỗi với giá trị hợp lệ, nên dùng kiểu kết quả tường minh, giá trị tùy chọn hoặc để lỗi lan lên.

Điểm quan trọng là fallback nằm trong **handler dành cho lỗi có thể phục hồi**, không nằm trong `finally` vốn chạy cả ở đường thành công.

## 13. Nếu cleanup có thể lỗi, dùng `try-with-resources` khi thích hợp

Với các tài nguyên triển khai `AutoCloseable`, Java cung cấp `try-with-resources` để xử lý việc đóng tài nguyên. Khi **thân `try` ném một ngoại lệ chính**, còn thao tác `close()` ném một ngoại lệ khác, Java lưu ngoại lệ lúc đóng vào **danh sách suppressed** của ngoại lệ chính theo quy tắc của `try-with-resources`.

Đó là một khác biệt quan trọng so với `try-finally` tự viết, nơi lỗi cleanup có thể thay thế lỗi chính.

### Ví dụ Java 17+ hoàn chỉnh: giữ lỗi chính và lỗi đóng

Lưu tệp **`SuppressedCloseDemo.java`**:

```java
public class SuppressedCloseDemo {

    static final class FailingResource implements AutoCloseable {
        @Override
        public void close() {
            System.out.println("resource: close");
            throw new IllegalStateException("close failed");
        }
    }

    static void work() {
        try (FailingResource resource = new FailingResource()) {
            System.out.println("body: fails");
            throw new IllegalArgumentException("body failed");
        }
    }

    public static void main(String[] args) {
        try {
            work();
        } catch (IllegalArgumentException ex) {
            System.out.println("primary: " + ex.getMessage());
            System.out.println("suppressed count: "
                    + ex.getSuppressed().length);
            for (Throwable suppressed : ex.getSuppressed()) {
                System.out.println("suppressed: "
                        + suppressed.getMessage());
            }
        }
    }
}
```

Biên dịch/chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 SuppressedCloseDemo.java
java SuppressedCloseDemo
```

**Stdout chính xác:**

```text
body: fails
resource: close
primary: body failed
suppressed count: 1
suppressed: close failed
```

### Vì sao lần này lỗi gốc không biến mất?

1. Phần thân `try` ném `IllegalArgumentException("body failed")`.
2. Khi rời `try-with-resources`, Java tự gọi `close()` của `FailingResource`.
3. `close()` ném `IllegalStateException("close failed")`.
4. Thay vì để lỗi `close()` tự động ghi đè lỗi từ thân `try`, cơ chế `try-with-resources` thêm lỗi đóng vào `getSuppressed()` của ngoại lệ chính.
5. Caller bắt `IllegalArgumentException` và có thể xem cả nguyên nhân chính lẫn lỗi đóng.

Vì ví dụ dùng lỗi unchecked và tài nguyên giả lập, đầu ra không phụ thuộc hệ điều hành hay file thật.

**Lưu ý quan trọng:** nếu phần thân `try` hoàn tất **bình thường** hoặc đang định `return` mà `close()` ném ngoại lệ, lỗi đóng vẫn có thể khiến lời gọi kết thúc bằng ngoại lệ, thay vì trả kết quả. `try-with-resources` không có nghĩa mọi lỗi cleanup đều bị bỏ qua; nó ưu tiên **bảo toàn lỗi chính khi cả phần thân và close đều ném lỗi**.

### Suppressed exception không giống `cause`

- **`cause`:** thể hiện một ngoại lệ làm nguyên nhân cho ngoại lệ khác, thường được lập trình viên gắn qua constructor `new X(message, cause)`.
- **`suppressed`:** danh sách lỗi phụ được gắn vào ngoại lệ chính, chẳng hạn lỗi khi đóng tài nguyên trong `try-with-resources`.
- `getCause()` không tự trả về lỗi đóng bị suppressed; cần `getSuppressed()`.
- Không có quy tắc tự động thêm suppressed exception khi lập trình viên viết một câu `throw` mới trong `finally` thông thường.

Có thể chủ động gọi `Throwable.addSuppressed(...)` trong một cơ chế cleanup phức tạp, nhưng phải giữ tham chiếu đúng tới ngoại lệ gốc và tránh xử lý sai các trường hợp lỗi khác. Trong phần lớn mã quản lý `AutoCloseable`, **ưu tiên `try-with-resources`** thay vì tự tái triển khai quy tắc suppression.

## 14. So sánh `try-finally` tự viết và `try-with-resources`

| Tiêu chí | `try-finally` gọi `close()` thủ công | `try-with-resources` |
| --- | --- | --- |
| Cách dọn dẹp | Lập trình viên tự gọi trong `finally` | Java tự gọi `close()` khi rời phạm vi |
| Có thể làm mất lỗi chính khi `close()` ném? | **Có**, nếu không tự quản lý lỗi | Cơ chế suppressed giúp giữ lỗi chính khi cả body và close lỗi |
| Đóng nhiều tài nguyên | Tự viết thứ tự và xử lý lỗi | Các tài nguyên được đóng theo **thứ tự ngược** với khai báo |
| Có cần `AutoCloseable`? | Không bắt buộc, tùy API | Có; tài nguyên phải là `AutoCloseable` |
| Có thể kết hợp `catch`/`finally` khác? | Có | Có |
| Có thay thế mọi rollback/giao dịch? | Không | Không |

Tuy nhiên `try-with-resources` **không thích hợp cho mọi cleanup**: có những công việc không sở hữu tài nguyên `AutoCloseable` hoặc cần quyết định xử lý lỗi đặc biệt. Khi dùng `finally` thủ công, hãy giữ khối rõ ràng và bảo đảm không vô tình thay thế kết quả/lỗi chính.

## 15. Trường hợp `finally` ném lỗi: xử lý ở đâu?

Một lỗi ném từ `finally` không được các nhánh `catch` **anh em cùng cấu trúc** bắt. Để xử lý nó ở caller:

```java
try {
    operationWithFinally();
} catch (IllegalStateException ex) {
    System.out.println("operation failed: " + ex.getMessage());
}
```

Đây là **khung minh họa**, giả định `operationWithFinally()` có thể ném `IllegalStateException`. Handler ngoài có thể bắt lỗi hiện đang lan lên, nhưng nếu lỗi gốc đã bị thay thế trong `finally` bên trong, **handler ngoài không thể tự khôi phục ngoại lệ gốc** chỉ bằng việc thêm một `catch`.

Cần giải quyết vấn đề ở **nơi dọn dẹp**, ví dụ:

- Sử dụng `try-with-resources` cho tài nguyên phù hợp.
- Nếu cleanup có thể thất bại mà không được phép che lỗi nghiệp vụ, giữ nguyên đối tượng lỗi chính và ghi nhận lỗi phụ theo chính sách cụ thể.
- Nếu lỗi cleanup nghiêm trọng đến mức thao tác phải coi là thất bại, cho phép ngoại lệ đó đi lên **có chủ đích** và thông tin chẩn đoán cần thiết, thay vì đặt `throw` thiếu cân nhắc trong `finally`.
- Không bao giờ im lặng nuốt mọi `Exception` trong cleanup chỉ để tránh che lỗi; việc bỏ qua lỗi phải được xác định và tài liệu hóa.

Ranh giới xử lý ở tầng ngoài quyết định phản hồi hệ thống, nhưng **không thể sửa hoàn toàn việc tầng dưới đã làm mất thông tin lỗi**.

## 16. Ngoại lệ và `return` từ `catch` cũng chịu cùng quy tắc

`finally` không chỉ đi cùng một `try` đơn. Với cấu trúc:

```java
static String recover() {
    try {
        throw new IllegalArgumentException("bad input");
    } catch (IllegalArgumentException ex) {
        return "fallback";
    } finally {
        System.out.println("cleanup");
    }
}
```

Caller nhận `"fallback"`; `finally` chỉ in và hoàn tất bình thường.

Nếu đổi `finally` thành:

```java
finally {
    throw new IllegalStateException("cleanup failed");
}
```

thì caller sẽ **nhận `IllegalStateException`, không nhận `"fallback"`**. Điều tương tự xảy ra nếu `catch` định ném lại ngoại lệ: một `return` khác trong `finally` có thể nuốt lỗi đang được ném lại.

Cách đọc chính xác là: **bất kỳ lý do hoàn tất đang chờ nào của `try` hoặc `catch` đều có thể bị thay thế bởi lý do mới do `finally` tạo ra**.

## 17. Có phải `finally` luôn được thực thi?

Theo luồng điều khiển Java thông thường, `finally` được thực thi khi rời `try` hoặc `catch`, kể cả khi có `return` hoặc ngoại lệ. Tuy vậy, **không nên coi đây là cam kết tuyệt đối trong mọi cách JVM/process kết thúc**.

Những tình huống có thể khiến `finally` không chạy như bạn kỳ vọng:

- `Runtime.getRuntime().halt(...)` kết thúc JVM trực tiếp.
- `System.exit(...)` bắt đầu quy trình tắt JVM, không bảo đảm chạy các `finally` ở thread đang hoạt động.
- Tiến trình bị hệ điều hành chấm dứt cưỡng bức, gặp sự cố nghiêm trọng hoặc máy mất điện.
- Thread bị kẹt trong thao tác không hoàn tất nên chưa rời `try` để tới `finally`.

Điều này không thay đổi quy tắc chính của bài: **khi `finally` thực sự được chạy theo luồng bình thường và nó hoàn tất bất thường, nó có thể thay thế kết quả đang chờ**.

## 18. `break` và `continue` trong `finally` cũng có thể thay đổi luồng

Ngoài `return` và `throw`, lý do hoàn tất bất thường còn có thể đến từ `break` hoặc `continue` trong vòng lặp.

Ví dụ cú pháp Java hợp lệ trong thân một phương thức:

```java
for (int i = 0; i < 2; i++) {
    try {
        System.out.println("try " + i);
        if (i == 0) {
            throw new IllegalStateException("failed");
        }
    } finally {
        continue;
    }
}
System.out.println("after loop");
```

**Stdout:**

```text
try 0
try 1
after loop
```

`continue` trong `finally` khiến vòng lặp chuyển sang lần lặp tiếp theo, làm ngoại lệ `IllegalStateException` ở lần đầu **không lan ra ngoài**. Đây là cùng một quy tắc “lý do hoàn tất mới thay thế lý do cũ”, dù không dùng `return` hay `throw`.

Đoạn mã này được đưa ra để **nhận diện anti-pattern**, không khuyến nghị viết `continue` hoặc `break` trong `finally`. Với các vòng lặp, các lệnh điều khiển luồng này còn làm hành vi khó đọc và dễ giấu lỗi.

## 19. Checklist phát hiện và sửa lỗi mất kết quả trong `finally`

Khi review một phương thức có `try-catch-finally`, hãy tự kiểm tra:

1. **`finally` có `return` không?** Nếu có, xác định nó đang thay thế giá trị trả về hay ngoại lệ nào. Thông thường nên chuyển chính sách trả về ra `try`/`catch`.
2. **`finally` có `throw` trực tiếp không?** Xem nó có che ngoại lệ đang lan truyền hay không, và caller có còn cách truy nguyên lỗi chính không.
3. **Các lời gọi trong `finally` có thể ném lỗi không?** `close()`, ghi log, callback cleanup, giải phóng tài nguyên đều có thể thất bại tùy API.
4. **`return` ở `try` đã được đánh giá trước khi dọn dẹp chưa?** Với kiểu nguyên thủy, sửa biến cục bộ trong `finally` không đổi giá trị đã tính; với đối tượng mutable, thay đổi trạng thái có thể vẫn quan sát được.
5. **`catch` có dự kiến trả fallback hoặc ném lại không?** Đảm bảo `finally` không âm thầm thay thế quyết định của handler.
6. **Có thể dùng `try-with-resources` không?** Nếu tài nguyên triển khai `AutoCloseable`, cơ chế này quản lý đóng và suppressed exceptions tốt hơn trong nhiều tình huống.
7. **Có đang log và xử lý cả lỗi chính lẫn lỗi dọn dẹp đúng chính sách không?** Tránh làm mất nguyên nhân chính nhưng cũng không giấu lỗi cleanup quan trọng.
8. **Có nested `finally` không?** Các lớp `finally` ngoài có thể thay thế tiếp kết quả do cấu trúc trong chuẩn bị trả.
9. **Có `break` hoặc `continue` trong `finally` không?** Chúng cũng thay đổi lý do hoàn tất và có thể che ngoại lệ.
10. **Đã kiểm thử đường lỗi chưa?** Ít nhất kiểm tra `return` trước `finally`, `throw` trước `finally`, cleanup thất bại, và tình huống body + close đồng thời thất bại.

## 20. Bài tập tự kiểm tra

**Bài A. Giá trị nào được trả về?**

```java
static int f() {
    try {
        return 1;
    } finally {
        return 2;
    }
}
```

**Đáp án:** `2`. `return` của `finally` ghi đè `return` trước đó.

**Bài B. Lỗi có đi tới caller không?**

```java
static String f() {
    try {
        throw new IllegalStateException("fail");
    } finally {
        return "OK";
    }
}
```

**Đáp án:** caller nhận `"OK"`, ngoại lệ bị nuốt. Đây là ví dụ gây hiểu lầm nghiêm trọng nếu `"OK"` được xem là thành công.

**Bài C. Caller nhận lỗi nào?**

```java
static void f() {
    try {
        throw new IllegalArgumentException("original");
    } finally {
        throw new IllegalStateException("new");
    }
}
```

**Đáp án:** `IllegalStateException("new")`. Nếu mã không gắn `cause`/suppressed có chủ đích, lỗi `original` không tự xuất hiện trong ngoại lệ mới.

**Bài D. Biến bị sửa trong `finally`, kết quả trả về có đổi không?**

```java
static int f() {
    int x = 3;
    try {
        return x;
    } finally {
        x = 4;
    }
}
```

**Đáp án:** `3`, vì biểu thức trả về đã được đánh giá trước `finally` và không có `return` hoặc `throw` mới.

**Bài E. Tại sao đối tượng mutable có thể khác?**

```java
static StringBuilder f() {
    StringBuilder b = new StringBuilder("X");
    try {
        return b;
    } finally {
        b.append("Y");
    }
}
```

**Đáp án:** đối tượng được trả về chứa `"XY"`. Tham chiếu đã được đánh giá trước `finally` nhưng chính đối tượng đã bị sửa trong `finally`.

**Bài F. Thân `try-with-resources` và `close()` cùng ném lỗi thì sao?**

**Đáp án:** theo cơ chế `try-with-resources`, ngoại lệ của thân `try` thường là lỗi chính và lỗi từ `close()` được thêm vào danh sách suppressed. Có thể xem bằng `ex.getSuppressed()`; không mặc nhiên mất lỗi gốc như một `throw` mới từ `finally` thủ công.

**Bài G. Handler `catch` cùng cấp có xử lý ngoại lệ từ `finally` không?**

**Không.** Phải dùng handler ở phạm vi ngoài hoặc xử lý ngay trong cleanup; một handler cùng cấu trúc chỉ xử lý lỗi đến từ phần thân `try` mà nó bảo vệ.

**Bài H. Nested `finally` lần lượt trả `2` rồi `3`, kết quả cuối là gì?**

**`3`**, vì `return` ở `finally` ngoài thay thế kết quả đang chờ từ cấu trúc trong.

## Kết luận

Hãy ghi nhớ một quy tắc duy nhất:

**Khi `finally` hoàn tất bình thường, Java giữ lý do hoàn tất từ `try`/`catch`. Khi `finally` hoàn tất bất thường bằng `return` hoặc `throw`, lý do mới thay thế lý do trước đó.**

Hệ quả có thể là **trả sai giá trị**, **nuốt ngoại lệ gốc**, hoặc **chỉ còn ngoại lệ cleanup khiến việc chẩn đoán sai hướng**. Vì vậy, tránh `return` và các thao tác `throw` không có chủ đích trong `finally`; đưa quyết định kết quả vào `try`/`catch`, đặt cleanup ở phạm vi rõ ràng, và ưu tiên `try-with-resources` để quản lý tài nguyên `AutoCloseable` cùng các lỗi đóng tài nguyên bị suppressed.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="finally-007">Ngoại lệ cleanup che mất ngoại lệ ban đầu</a>

<details>
<summary>Xem chi tiết</summary>

# Ngoại lệ cleanup che mất ngoại lệ ban đầu trong Java

Một thao tác có thể thất bại **hai lần**: lần đầu khi thực hiện công việc chính, lần thứ hai khi dọn dẹp tài nguyên. Ví dụ, chương trình đang ghi dữ liệu thì gặp lỗi, sau đó lệnh đóng kết nối cũng ném exception. Nếu việc dọn dẹp được viết không cẩn thận trong khối `finally`, **ngoại lệ mới của cleanup có thể thay thế ngoại lệ ban đầu**, khiến caller chỉ nhìn thấy “không đóng được” mà không thấy “ghi dữ liệu thất bại”.

Đây là vấn đề **exception masking** hoặc **exception suppression không đúng cách** theo nghĩa thông thường: *nguyên nhân lỗi quan trọng nhất bị che khuất bởi một lỗi phát sinh sau đó*. Cần phân biệt hiện tượng “che mất” với cơ chế **suppressed exceptions** chính thức của Java. `try-with-resources` được thiết kế để giữ ngoại lệ chính và đính kèm ngoại lệ khi đóng tài nguyên vào danh sách phụ thông qua `Throwable.getSuppressed()`.

Bài này phân tích chính xác quy tắc luồng điều khiển của `finally`, ví dụ Java 17 có thể biên dịch/chạy, cách đọc thông tin lỗi, cách sửa bằng `try-with-resources`, và trường hợp buộc phải tự xử lý cleanup.

## 1. Tình huống dẫn đến ngoại lệ bị che khuất

Hình dung một thao tác:

1. Mở tài nguyên.
2. Thực hiện công việc chính: đọc dữ liệu, gửi yêu cầu, ghi tệp hoặc cập nhật cơ sở dữ liệu.
3. Công việc chính thất bại và ném ngoại lệ A.
4. Khi rời phạm vi, cleanup cũng thất bại và ném ngoại lệ B.
5. Caller cần biết cả hai, **đặc biệt A**, vì A mô tả lý do công việc chính thất bại.

Nếu mã được viết theo mẫu:

```java
try {
    doWork();
} finally {
    closeResource();
}
```

đây chỉ là **mã khung**, giả sử cả hai phương thức đã được định nghĩa. Nếu `doWork()` ném A rồi `closeResource()` ném B từ `finally`, **B trở thành exception được truyền ra từ toàn bộ cấu trúc**. A không tự động được gắn thành `cause` hay `suppressed` của B.

Điều này gây hậu quả:

- Stack trace bên ngoài có thể hướng người vận hành tới `closeResource()` trong khi lỗi đầu tiên thực sự xảy ra ở `doWork()`.
- Thao tác có thể ghi dở, gửi dở hoặc chưa hoàn tất, nhưng việc phân tích chỉ tập trung vào cleanup.
- Các mã xử lý theo loại exception có thể nhận B thay vì A, dẫn tới retry, rollback hoặc phản hồi sai.
- Nếu hệ thống không lưu A trước đó, việc tái dựng nguyên nhân gốc trở nên khó khăn.

**Đừng hiểu rằng `finally` luôn làm mất lỗi gốc.** Nếu `finally` hoàn tất bình thường, A tiếp tục lan truyền. Việc che khuất xảy ra khi `finally` **kết thúc bất thường bằng một lý do mới** như ném exception mới hoặc thực hiện một dạng điều khiển luồng thay thế.

## 2. Quy tắc ngôn ngữ: lý do hoàn tất sau cùng

Một khối `try` có thể kết thúc **bình thường** hoặc **bất thường** (abrupt completion), chẳng hạn vì `throw`, `return`, `break` hay `continue`. Khi có `finally`, Java thực thi `finally` trước khi hoàn tất việc rời cấu trúc theo đường thông thường.

Hai trường hợp cần phân biệt:

| Tình huống | `finally` làm gì? | Kết quả tiếp theo |
| --- | --- | --- |
| `try` hoàn tất bình thường | `finally` hoàn tất bình thường | Chạy câu lệnh sau cấu trúc |
| `try` ném A | `finally` hoàn tất bình thường | A tiếp tục lan truyền |
| `try` ném A | `finally` ném B | B thay thế A như lý do thoát ra |
| `catch` ném lại A | `finally` ném B | B thay thế A |
| `try` chuẩn bị `return` giá trị V | `finally` hoàn tất bình thường | Trả V |
| `try` chuẩn bị `return` giá trị V | `finally` ném B | Không trả V; ném B |
| `try` ném A | `finally` thực hiện `return` | Exception A bị che; phương thức trả về |
| `try` ném A | `finally` thực hiện `break` hoặc `continue` hợp lệ | Điều khiển luồng từ `finally` có thể thay thế việc ném A |

Một cách nhớ ngắn: **kết quả đang chờ từ `try/catch` chỉ được giữ khi `finally` hoàn tất bình thường**. Nếu `finally` kết thúc bất thường, lý do kết thúc mới của `finally` thường thắng.

Quy tắc này không liên quan checked hay unchecked: A và B có thể đều là `RuntimeException`, hoặc một/both là checked exception. Nghĩa vụ `throws` thay đổi theo kiểu exception, nhưng hiện tượng masking là về **luồng thực thi**.

## 3. Ví dụ độc lập: cleanup làm mất ngoại lệ gốc

Lưu tệp **`FinallyMasksExceptionDemo.java`**:

```java
public class FinallyMasksExceptionDemo {
    static void perform() {
        try {
            System.out.println("Đang xử lý");
            throw new IllegalArgumentException("Dữ liệu đầu vào sai");
        } finally {
            System.out.println("Đang cleanup");
            throw new IllegalStateException("Không đóng được tài nguyên");
        }
    }

    public static void main(String[] args) {
        try {
            perform();
        } catch (RuntimeException e) {
            System.out.println("Caller nhận: "
                    + e.getClass().getSimpleName());
            System.out.println("Message: " + e.getMessage());
            System.out.println("Cause null: " + (e.getCause() == null));
            System.out.println("Suppressed: " + e.getSuppressed().length);
        }
        System.out.println("Main tiếp tục");
    }
}
```

Biên dịch và chạy với Java 17+:

```bash
javac -encoding UTF-8 --release 17 FinallyMasksExceptionDemo.java
java FinallyMasksExceptionDemo
```

**Stdout chính xác** khi console hiển thị UTF-8:

```text
Đang xử lý
Đang cleanup
Caller nhận: IllegalStateException
Message: Không đóng được tài nguyên
Cause null: true
Suppressed: 0
Main tiếp tục
```

Phân tích từng bước:

1. Thân `try` ném `IllegalArgumentException` với message “Dữ liệu đầu vào sai”.
2. Trước khi exception được đẩy ra khỏi `perform()`, Java đi vào `finally`.
3. `finally` lại ném `IllegalStateException` với message “Không đóng được tài nguyên”.
4. `perform()` thoát ra với **`IllegalStateException`**, không phải `IllegalArgumentException` ban đầu.
5. `getCause() == null` và `getSuppressed().length == 0` vì không có mã nào liên kết hai ngoại lệ. **`finally` không tự tạo `cause` hoặc `suppressed`.**

`main` bắt `RuntimeException` nên xử lý được B và tiếp tục sau handler. Nếu caller chỉ viết `catch (IllegalArgumentException e)`, nó **không** bắt được B; điều này có thể khiến lỗi đi xa hơn dự kiến.

> Đây là **phản ví dụ có chủ đích** nhằm phơi bày masking. Không viết cleanup luôn ném exception như vậy trong ứng dụng thực.

## 4. Nếu `finally` không ném gì, lỗi gốc vẫn tồn tại

Ngược lại, `finally` hoàn tất bình thường thì ngoại lệ ban đầu không bị thay thế. Chương trình độc lập **`FinallyKeepsExceptionDemo.java`**:

```java
public class FinallyKeepsExceptionDemo {
    static void perform() {
        try {
            System.out.println("Work");
            throw new IllegalArgumentException("Lỗi gốc");
        } finally {
            System.out.println("Cleanup hoàn tất");
        }
    }

    public static void main(String[] args) {
        try {
            perform();
        } catch (IllegalArgumentException e) {
            System.out.println("Caller: " + e.getMessage());
        }
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 FinallyKeepsExceptionDemo.java
java FinallyKeepsExceptionDemo
```

**Stdout chính xác:**

```text
Work
Cleanup hoàn tất
Caller: Lỗi gốc
```

Handler ngoài nhận đúng `IllegalArgumentException`. Sự khác biệt với ví dụ trước chỉ nằm ở chỗ `finally` **hoàn tất bình thường**.

Một lưu ý sâu hơn: nếu `finally` gọi một phương thức cleanup và phương thức đó âm thầm **nuốt lỗi cleanup** rồi trả về bình thường, A vẫn đi lên caller nhưng B có thể bị mất. Điều này không masking A, nhưng vẫn có thể khiến chẩn đoán bỏ sót lỗi phụ. Nên có chính sách ghi nhận B phù hợp.

## 5. Trường hợp `catch` đã ném lại nhưng `finally` vẫn che mất

Việc đã bắt và ném lại A bằng `throw e;` **không bảo đảm caller nhận A** nếu phần `finally` sau đó ném B.

Ví dụ:

```java
try {
    doWork();
} catch (IllegalArgumentException e) {
    System.out.println("Ghi nhận lỗi gốc: " + e.getMessage());
    throw e;
} finally {
    closeResource(); // Nếu ném B, B có thể thay A.
}
```

Đây là **mã khung**, giả sử `doWork()` và `closeResource()` đã được định nghĩa. Trình tự khi cả hai thao tác thất bại:

```text
doWork()                 → throw A
catch                   → nhận A, định ném lại A
finally                 → closeResource() throw B
caller                  → nhận B
```

Bản thân `throw e` không tạo exception mới; nó giữ tham chiếu gốc. Nhưng do `finally` kết thúc bất thường, **kết quả ném lại đang chờ bị thay thế**.

Ngoài ra, exception được ném từ **thân `catch`** không được thử bắt bởi các nhánh `catch` đứng sau cùng cấu trúc. Khi `finally` ném B, B tìm handler ở một cấu trúc bao ngoài/caller, không quay lại các handler cùng cấp bên trong.

## 6. Không chỉ `throw`: `return` trong `finally` cũng che lỗi

Một sai lầm nguy hiểm là `return` từ `finally`. Nó có thể khiến chương trình **trả kết quả trông như thành công dù `try` đã ném exception**.

Lưu `FinallyReturnMaskDemo.java`:

```java
public class FinallyReturnMaskDemo {
    static int maskedFailure() {
        try {
            throw new IllegalArgumentException("Không tính được");
        } finally {
            return 10; // Phản ví dụ: nuốt lỗi gốc!
        }
    }

    static int maskedReturn() {
        try {
            return 20;
        } finally {
            return 30; // Phản ví dụ: ghi đè kết quả.
        }
    }

    public static void main(String[] args) {
        System.out.println("Lỗi bị che: " + maskedFailure());
        System.out.println("Return bị ghi đè: " + maskedReturn());
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 FinallyReturnMaskDemo.java
java FinallyReturnMaskDemo
```

**Stdout chính xác:**

```text
Lỗi bị che: 10
Return bị ghi đè: 30
```

Với `maskedFailure()`, một `IllegalArgumentException` thực sự được ném trong `try`, nhưng `finally` trả về `10` và **không có exception nào thoát ra tới caller**. Với `maskedReturn()`, `return 30` của `finally` thắng `return 20` đang chờ.

Java cho phép cú pháp trong các phương thức kiểu này, nhưng thiết kế rất khó kiểm tra và dễ che giấu sự cố. Quy tắc thực hành: **không `return`, `break` hoặc `continue` từ `finally` trừ khi có lý do đặc biệt được kiểm soát chặt**; cleanup nên hoàn tất bình thường trong khả năng cho phép.

## 7. Giải pháp ưu tiên: `try-with-resources`

Từ Java 7, `try-with-resources` giúp quản lý những đối tượng thực hiện giao diện `AutoCloseable`. Tài nguyên được khai báo trong phần `try (...)` sẽ được Java **cố gắng đóng** khi rời khối, kể cả khi có exception.

Điểm khác biệt lớn nhất với việc `finally` ném lỗi thủ công:

- Nếu **thân `try` ném A**, rồi `close()` ném B, Java thường giữ **A làm exception chính**.
- **B được gắn vào A dưới dạng suppressed exception**, truy cập bằng `A.getSuppressed()`.
- Nếu thân `try` kết thúc bình thường nhưng `close()` ném B, **B có thể trở thành exception chính**.
- Nếu nhiều tài nguyên được khai báo, Java đóng chúng theo **thứ tự ngược** với thứ tự khai báo.
- Nếu nhiều lỗi đóng xảy ra khi A đang lan truyền, các lỗi đóng được đính kèm theo thứ tự đóng, thay vì thay thế A.

Điều này giúp giữ lại bằng chứng của cả lỗi chính và lỗi cleanup, tránh hiện tượng lỗi sau che mất lỗi trước.

### `cause` khác `suppressed`

| Thuộc tính | Cách thiết lập phổ biến | Ý nghĩa |
| --- | --- | --- |
| `getCause()` | Constructor như `new IllegalStateException("...", cause)`, hoặc `initCause` hợp lệ | Một lỗi **gây ra** exception hiện tại khi bọc/chuyển tầng |
| `getSuppressed()` | `try-with-resources` hoặc `addSuppressed` | Một hoặc nhiều lỗi phụ bị **gác lại** để giữ exception chính |

Một exception có thể có cả `cause` và nhiều `suppressed`. Không tự suy luận rằng mỗi lỗi cleanup luôn là cause; trong `try-with-resources`, **lỗi cleanup đi vào suppressed** nếu đã có lỗi chính từ thân `try`.

## 8. Ví dụ chạy được: bảo toàn lỗi chính và lỗi cleanup

Lưu tệp **`SuppressedCleanupDemo.java`**:

```java
public class SuppressedCleanupDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() {
            System.out.println("Đang đóng tài nguyên");
            throw new IllegalStateException("Đóng thất bại");
        }
    }

    public static void main(String[] args) {
        try (Resource resource = new Resource()) {
            System.out.println("Đang xử lý");
            throw new IllegalArgumentException("Dữ liệu sai");
        } catch (IllegalArgumentException e) {
            System.out.println("Lỗi chính: " + e.getMessage());
            System.out.println("Số lỗi suppressed: "
                    + e.getSuppressed().length);
            System.out.println("Lỗi phụ: "
                    + e.getSuppressed()[0].getMessage());
            System.out.println("Cause null: " + (e.getCause() == null));
        }
        System.out.println("Main tiếp tục");
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 SuppressedCleanupDemo.java
java SuppressedCleanupDemo
```

**Stdout chính xác** khi hiển thị UTF-8:

```text
Đang xử lý
Đang đóng tài nguyên
Lỗi chính: Dữ liệu sai
Số lỗi suppressed: 1
Lỗi phụ: Đóng thất bại
Cause null: true
Main tiếp tục
```

Ở đây, thân `try` ném `IllegalArgumentException` (A). Khi rời `try`, `close()` được gọi và ném `IllegalStateException` (B). Java giữ A làm exception chính, còn B nằm ở `A.getSuppressed()[0]`. Handler bắt A vẫn hoạt động; caller không bị chuyển bất ngờ sang xử lý B.

Kết quả `Cause null: true` cho thấy không có chuỗi `cause` nào được thiết lập; quan hệ giữa A và B được lưu trong **suppressed**, không phải `getCause()`.

Ví dụ giả định luôn có một suppressed exception để minh họa truy cập `[0]`; với mã thực, **kiểm tra độ dài trước khi truy cập** và không mặc định mọi exception đều có suppressed.

## 9. Nhiều tài nguyên: đóng ngược thứ tự và giữ tất cả lỗi đóng

Khi khai báo nhiều tài nguyên, mỗi resource đã khởi tạo thành công được đóng theo thứ tự từ phải sang trái. Nếu thân `try` đã ném exception chính, các exception phát sinh từ `close()` được thêm vào danh sách suppressed của exception chính.

Lưu **`MultipleCleanupDemo.java`**:

```java
public class MultipleCleanupDemo {
    static class Resource implements AutoCloseable {
        private final String name;

        Resource(String name) {
            this.name = name;
            System.out.println("Mở " + name);
        }

        @Override
        public void close() {
            System.out.println("Đóng " + name);
            throw new IllegalStateException("Lỗi đóng " + name);
        }
    }

    public static void main(String[] args) {
        try (Resource first = new Resource("A");
             Resource second = new Resource("B")) {
            System.out.println("Trong try");
            throw new IllegalArgumentException("Lỗi xử lý");
        } catch (IllegalArgumentException e) {
            System.out.println("Chính: " + e.getMessage());
            System.out.println("Suppressed: " + e.getSuppressed().length);
            for (Throwable t : e.getSuppressed()) {
                System.out.println("Phụ: " + t.getMessage());
            }
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 MultipleCleanupDemo.java
java MultipleCleanupDemo
```

**Stdout chính xác:**

```text
Mở A
Mở B
Trong try
Đóng B
Đóng A
Chính: Lỗi xử lý
Suppressed: 2
Phụ: Lỗi đóng B
Phụ: Lỗi đóng A
```

Resource B được đóng trước A, nên thứ tự suppressed cũng là lỗi đóng B trước rồi đến lỗi đóng A. **Không chỉ tài nguyên cuối mới được đóng**: Java vẫn cố đóng các tài nguyên còn lại đã được khởi tạo thành công, kể cả khi một thao tác đóng ném exception.

Đây là lợi thế lớn của `try-with-resources` so với kiểu cleanup thủ công không có chính sách rõ: các lỗi đóng không buộc chương trình đánh mất nguyên nhân chính hoặc bỏ qua những tài nguyên khác.

## 10. Khi cleanup là lỗi duy nhất, nó trở thành lỗi chính

`try-with-resources` không làm biến mất lỗi đóng. Nếu thân `try` hoàn tất bình thường nhưng `close()` thất bại, thì **lỗi cleanup là lỗi chính của toàn bộ câu lệnh**.

Lưu **`OnlyCleanupFailsDemo.java`**:

```java
public class OnlyCleanupFailsDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() {
            System.out.println("Đang đóng");
            throw new IllegalStateException("Lỗi cleanup");
        }
    }

    public static void main(String[] args) {
        try (Resource resource = new Resource()) {
            System.out.println("Work thành công");
        } catch (IllegalStateException e) {
            System.out.println("Bắt: " + e.getMessage());
            System.out.println("Suppressed: " + e.getSuppressed().length);
        }
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 OnlyCleanupFailsDemo.java
java OnlyCleanupFailsDemo
```

**Stdout chính xác:**

```text
Work thành công
Đang đóng
Bắt: Lỗi cleanup
Suppressed: 0
```

Thân `try` đã chạy hết nhưng **toàn bộ câu lệnh `try-with-resources` không hoàn tất bình thường** vì `close()` thất bại. B trở thành exception chính và không có lỗi chính trước đó để đính suppressed. Không nên báo thành công toàn bộ thao tác nếu hợp đồng yêu cầu đóng/flushing thành công trước khi xác nhận.

## 11. Lỗi khi khởi tạo tài nguyên cũng cần được bảo toàn

`try-with-resources` còn xử lý tình huống một tài nguyên đã mở thành công nhưng việc **khởi tạo tài nguyên tiếp theo** thất bại. Java sẽ cố đóng những tài nguyên đã mở, và nếu việc đóng phát sinh lỗi, lỗi đó có thể được đính kèm vào exception chính phát sinh khi khởi tạo.

Ví dụ độc lập **`InitializationCleanupDemo.java`**:

```java
public class InitializationCleanupDemo {
    static class Resource implements AutoCloseable {
        private final String name;

        Resource(String name) {
            this.name = name;
            System.out.println("Mở " + name);
            if ("B".equals(name)) {
                throw new IllegalArgumentException("Không tạo được B");
            }
        }

        @Override
        public void close() {
            System.out.println("Đóng " + name);
            throw new IllegalStateException("Không đóng được " + name);
        }
    }

    public static void main(String[] args) {
        try (Resource first = new Resource("A");
             Resource second = new Resource("B")) {
            System.out.println("Không tới thân try");
        } catch (IllegalArgumentException e) {
            System.out.println("Chính: " + e.getMessage());
            System.out.println("Phụ: "
                    + e.getSuppressed()[0].getMessage());
        }
    }
}
```

Biên dịch/chạy:

```bash
javac -encoding UTF-8 --release 17 InitializationCleanupDemo.java
java InitializationCleanupDemo
```

**Stdout chính xác:**

```text
Mở A
Mở B
Đóng A
Chính: Không tạo được B
Phụ: Không đóng được A
```

Resource A đã khởi tạo thành công. Constructor của B ném ngoại lệ trước khi B trở thành resource đã được quản lý thành công, nên Java **không gọi `close()` cho B** trong cấu trúc này; A được đóng và lỗi đóng A được gắn suppressed vào lỗi khởi tạo B.

Đây là một lý do quan trọng để tránh tự viết chuỗi mở/đóng tài nguyên dài bằng `finally` thủ công: các đường thất bại giữa chừng rất dễ bỏ sót cleanup hoặc vô tình che exception đầu tiên.

## 12. Một ví dụ thực tế với file I/O

Đối tượng từ `Files.newBufferedReader` triển khai `AutoCloseable`, nên Java có thể quản lý việc đóng tài nguyên qua `try-with-resources`.

Ví dụ **phương thức minh họa** (chưa có `main`, và phụ thuộc nội dung tệp thật):

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

class ConfigReader {
    static String firstLine(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path)) {
            return reader.readLine();
        }
    }
}
```

Cấu trúc này đảm bảo Java **cố gọi `reader.close()`** khi rời khối, kể cả khi `readLine()` ném `IOException`. Nếu cả đọc và đóng đều ném exception, lỗi đóng có thể nằm trong `getSuppressed()` của lỗi đọc chính.

Lệnh gọi cần chạy trong chương trình có đường dẫn hợp lệ; vì đầu ra phụ thuộc môi trường/tệp, ví dụ này **không công bố stdout cố định**. `readLine()` có thể trả `null` nếu đã hết dữ liệu, và điều đó không đồng nghĩa ngoại lệ.

Tránh mô hình “mở reader → gọi `readLine` → gọi `close` ở cuối hàm” mà không có cleanup cấu trúc: nếu `readLine` ném lỗi, dòng `close` bình thường ở cuối có thể bị bỏ qua.

## 13. Vì sao không tự động chuyển sang `getCause()`?

Nếu `finally` ném B khi A đang chờ, Java **không tự** làm `B.initCause(A)` hay `B.addSuppressed(A)`. Chỉ những đoạn code/hệ thống được thiết kế để liên kết ngoại lệ mới tạo mối liên hệ đó.

Có ba mô hình khác nhau:

| Mô hình | Caller thấy ngoại lệ chính nào? | Xem lỗi còn lại ở đâu? |
| --- | --- | --- |
| `try` ném A, `finally` ném B (không xử lý) | B | A có thể mất khỏi kết quả ném |
| `try` ném A, `catch` ném `new ServiceException("...", A)` | Exception wrapper mới | `getCause()` trả A |
| `try-with-resources`: thân ném A, `close` ném B | A | `getSuppressed()` của A chứa B |

Một ngoại lệ được bọc (*wrapping*) và một ngoại lệ bị đính kèm (*suppressed*) đều cung cấp thông tin chẩn đoán, nhưng quan hệ của chúng khác nhau. **Cause** mô tả chuỗi lý do dẫn đến lỗi cấp cao; **suppressed** thường lưu sự cố phụ xảy ra trong lúc đang xử lý lỗi chính.

Nếu dùng `Throwable.printStackTrace()`, Java thường hiển thị phần `Suppressed:` cho suppressed exception và phần `Caused by:` cho nguyên nhân. Không dựa vào văn bản hay số dòng cụ thể của stack trace như một hợp đồng ổn định; khi xử lý có cấu trúc, dùng `getCause()`, `getSuppressed()` và loại ngoại lệ thực.

## 14. Khi phải dùng `finally` thủ công: tự giữ ngoại lệ chính một cách có chủ đích

Không phải mọi tài nguyên cũ đều triển khai `AutoCloseable`. Khi buộc phải cleanup thủ công, cần chính sách **không ghi đè A bằng B**. Một lựa chọn là bắt lỗi cleanup rồi đính kèm nó vào exception chính đang được ném.

Ví dụ **chỉ minh họa trường hợp hai thao tác công bố `IOException`**:

```java
import java.io.IOException;

class LegacyCleanup {
    static void work() throws IOException {
        throw new IOException("Work failed");
    }

    static void cleanup() throws IOException {
        throw new IOException("Cleanup failed");
    }

    static void run() throws IOException {
        IOException primary = null;
        try {
            work();
        } catch (IOException e) {
            primary = e;
            throw e;
        } finally {
            try {
                cleanup();
            } catch (IOException closing) {
                if (primary != null) {
                    primary.addSuppressed(closing);
                } else {
                    throw closing;
                }
            }
        }
    }
}
```

Đây là **lớp minh họa không có `main`**. Khi `work()` ném `IOException`, handler giữ A trong biến `primary` và ném lại A. `finally` chạy `cleanup()`, nhận B và gắn B vào `primary`, rồi hoàn tất bình thường; do đó **A tiếp tục được truyền ra cùng suppressed B**.

Nếu `work()` hoàn tất bình thường, `primary == null`, và lỗi cleanup B sẽ được ném ra như lỗi chính. Cách tiếp cận này tránh mất lỗi gốc **trong phạm vi `IOException` đã thiết kế**. Nó **không** phải thư viện cleanup tổng quát cho mọi `RuntimeException` hoặc `Error`: nếu `work()` ném một loại khác mà handler không bắt, cleanup mới vẫn có thể che lỗi đó. Cũng cần xét điều kiện tài nguyên đã thực sự được mở và có cần cleanup hay không.

Do đó, **ưu tiên `try-with-resources` khi có thể**. Nếu thư viện cũ không triển khai `AutoCloseable`, cân nhắc bọc một adapter `AutoCloseable`, rồi dùng cấu trúc quản lý tài nguyên của Java thay vì viết lặp lại logic giữ primary/suppressed bằng tay.

## 15. Dùng adapter `AutoCloseable` cho API cũ

Giả sử một API cũ chỉ cung cấp phương thức `release()` mà không triển khai `AutoCloseable`. Có thể tạo adapter:

```java
class LegacySession {
    void doWork() {
        throw new IllegalArgumentException("Work failed");
    }

    void release() {
        throw new IllegalStateException("Release failed");
    }
}

class SessionAdapter implements AutoCloseable {
    private final LegacySession session;

    SessionAdapter(LegacySession session) {
        this.session = session;
    }

    void doWork() {
        session.doWork();
    }

    @Override
    public void close() {
        session.release();
    }
}
```

Đây là **hai lớp minh họa**. Khi dùng:

```java
try (SessionAdapter resource =
             new SessionAdapter(new LegacySession())) {
    resource.doWork();
}
```

là **đoạn code trong phương thức**, Java sẽ quản lý `close()` của adapter. Nếu `doWork` ném `IllegalArgumentException`, rồi `release` ném `IllegalStateException`, exception đầu vẫn là exception chính, và exception thứ hai được đính kèm suppressed. Adapter giúp đóng gói trách nhiệm cleanup ở một nơi.

Cần bảo đảm adapter thực sự sở hữu tài nguyên và không đóng sai một đối tượng dùng chung. Không nên tự động “adapter hóa” mọi dependency nếu người gọi không có quyền quản lý vòng đời của tài nguyên.

## 16. Kiểm thử masking và suppressed bằng kiểu, không bằng chuỗi stack trace

Một bài test tốt cần khẳng định **exception chính đúng loại và thông điệp**, cũng như **danh sách suppressed**. Không chỉ kiểm tra có lỗi xuất hiện trên console.

Ví dụ Java 17 độc lập **`SuppressedAssertionsDemo.java`** không yêu cầu thư viện test ngoài:

```java
public class SuppressedAssertionsDemo {
    static class Resource implements AutoCloseable {
        @Override
        public void close() {
            throw new IllegalStateException("cleanup");
        }
    }

    static void run() {
        try (Resource resource = new Resource()) {
            throw new IllegalArgumentException("work");
        }
    }

    public static void main(String[] args) {
        try {
            run();
            throw new AssertionError("Phải có exception");
        } catch (IllegalArgumentException e) {
            if (!"work".equals(e.getMessage())) {
                throw new AssertionError("Sai lỗi chính");
            }
            Throwable[] other = e.getSuppressed();
            if (other.length != 1
                    || !(other[0] instanceof IllegalStateException)
                    || !"cleanup".equals(other[0].getMessage())) {
                throw new AssertionError("Sai lỗi suppressed");
            }
            System.out.println("PASS: giữ lỗi chính và lỗi cleanup");
        }
    }
}
```

Lệnh:

```bash
javac -encoding UTF-8 --release 17 SuppressedAssertionsDemo.java
java SuppressedAssertionsDemo
```

**Stdout chính xác:**

```text
PASS: giữ lỗi chính và lỗi cleanup
```

Bài test này chủ động ném `AssertionError` khi kết quả trái mong đợi, không phụ thuộc việc JVM có bật tùy chọn `-ea` cho lệnh `assert` hay không. Khi exception xảy ra đúng như thiết kế, nhánh `catch` kiểm tra cấu trúc dữ liệu lỗi và in một thông báo duy nhất.

Trong bộ kiểm thử thực tế có thể dùng JUnit `assertThrows` và các assertion về `getSuppressed()`; logic cần kiểm tra vẫn là **primary exception đúng** và **cleanup exception không bị mất**.

## 17. Những sai lầm cần tránh

### 17.1. Bắt rồi nuốt sạch lỗi cleanup

```java
finally {
    try {
        cleanup();
    } catch (Exception ignored) {
        // Mất dấu vết cleanup failure.
    }
}
```

Đây là **khung minh họa**, chưa có `cleanup()`. Cách này có thể bảo toàn A đang lan truyền, nhưng **làm mất B**. Nếu lỗi cleanup quan trọng đối với tài nguyên, cần chiến lược ghi nhận phù hợp hoặc dùng suppressed; không đơn giản bỏ qua.

### 17.2. Cleanup trong `finally` tự ném exception mới

Nếu cleanup lỗi, `finally` có thể đẩy B lên và che A. Đây là vấn đề chính của bài; thay bằng `try-with-resources` nếu có tài nguyên `AutoCloseable`.

### 17.3. Dùng `return` trong `finally`

`return` trong `finally` có thể loại bỏ cả lỗi lẫn giá trị return đang chờ. Tránh dùng cho nghiệp vụ hoặc dọn dẹp.

### 17.4. Cho rằng `try-with-resources` luôn gắn suppressed

Nếu **chỉ `close()` ném lỗi**, lỗi đó là exception chính, không có exception chính trước đó để gắn suppressed. Nếu `close()` thành công, không có suppressed mới. Không truy `getSuppressed()[0]` khi chưa kiểm tra mảng có phần tử.

### 17.5. Đọc `getCause()` mà quên `getSuppressed()`

Một lỗi công việc chính có thể có cause riêng và **đồng thời** có suppressed cleanup exception. Chỉ lần theo `cause` có thể bỏ sót các vấn đề về tài nguyên.

### 17.6. Ghi log mọi tầng hoặc để lộ chi tiết

Chọn nơi có đủ ngữ cảnh để log cả exception chính, cause và suppressed. Tránh ghi thông tin nhạy cảm như mật khẩu, token hay nội dung nội bộ vào message, rồi hiển thị nguyên stack trace cho người dùng cuối.

### 17.7. Cho rằng lỗi đã được giải quyết chỉ vì handler bắt được

Một handler có thể bắt exception chính nhưng vẫn để tài nguyên ở trạng thái không nhất quán. **Bảo toàn dấu vết lỗi khác với khôi phục trạng thái ứng dụng**. Có thể cần rollback giao dịch, hủy yêu cầu, đóng các tài nguyên khác hoặc thông báo job thất bại.

### 17.8. Xem `finally` là bảo đảm tuyệt đối

`finally` thường chạy khi rời `try`/`catch` qua các đường đi Java bình thường và bất thường. Tuy nhiên tiến trình bị chấm dứt cưỡng bức, JVM gặp sự cố nghiêm trọng hoặc `System.exit` có thể ngăn cleanup. `try-with-resources` cũng không thể bảo đảm tài nguyên bên ngoài thực sự đóng thành công nếu `close()` đã thất bại.

## 18. Cách chẩn đoán một lỗi có cleanup failure

Khi một tác vụ thất bại, hãy đặt các câu hỏi theo thứ tự:

1. **Exception mà caller nhận được là gì?** Tên lớp, message, stack trace đầu tiên.
2. **Nó xuất hiện trong công việc chính hay trong cleanup?** Đọc các frame và cấu trúc `try/finally` hoặc `try-with-resources`.
3. **Có `getCause()` không?** Xác định chuỗi nguyên nhân khi exception được wrapping.
4. **Có `getSuppressed()` không?** Liệt kê và kiểm tra từng lỗi phụ, đặc biệt khi có nhiều tài nguyên.
5. **Có `return`, `throw` hoặc exception mới trong `finally` không?** Chúng có thể làm mất exception trước đó.
6. **Tài nguyên được khai báo/đóng theo thứ tự nào?** Với `try-with-resources`, đóng theo thứ tự ngược; khi khởi tạo thất bại, những tài nguyên đã mở được cố đóng.
7. **Có thể tạo lại lỗi mà không gây tác động nguy hiểm không?** Dùng fake resource như các ví dụ để test tình huống công việc + cleanup cùng thất bại.
8. **Chính sách ứng dụng là gì?** Khi thao tác chính thất bại, không báo thành công chỉ vì cleanup hoàn tất; khi cleanup thất bại, đánh giá ảnh hưởng tài nguyên trước khi tiếp tục.

Một phương pháp kiểm thử đáng tin là chủ động làm **cả hai đường thất bại**, rồi kiểm tra rằng caller nhận **A là primary, B là suppressed**. Điều này trực tiếp phát hiện lỗi masking mà các test chỉ kiểm tra “có ném exception” thường bỏ sót.

## 19. Câu hỏi tự kiểm tra

**1. Thân `try` ném A, `finally` hoàn tất bình thường. Caller nhận gì?**  
Caller nhận A (trừ khi một cấu trúc xử lý bao ngoài bắt hoặc chuyển đổi nó). `finally` không tự làm mất A nếu kết thúc bình thường.

**2. Thân `try` ném A, `finally` ném B. Caller nhận gì?**  
Theo cấu trúc `try/finally` thông thường, B trở thành exception thoát ra; A có thể bị che và không tự nằm trong `cause` hay `suppressed`.

**3. `catch` đã `throw e` với A, `finally` lại ném B. `throw e` có bảo đảm A đến caller không?**  
Không. B có thể thay thế A vì `finally` hoàn tất bất thường.

**4. `finally` có `return 1` trong khi `try` ném A thì sao?**  
`return` từ `finally` có thể che A và khiến phương thức trả về 1. Đây là phản ví dụ cần tránh.

**5. Trong `try-with-resources`, thân ném A và `close()` ném B thì lỗi nào là primary?**  
Thông thường A vẫn là primary; B được đính kèm suppressed. Có thể xem B qua `A.getSuppressed()`.

**6. Thân `try` thành công nhưng `close()` ném B thì sao?**  
B trở thành exception chính của câu lệnh `try-with-resources`, không phải suppressed của một exception không tồn tại.

**7. Hai tài nguyên A và B cùng được khai báo trong `try (A a = ...; B b = ...)`. Thứ tự đóng?**  
B trước A. Nếu cả hai `close()` lỗi khi thân đã ném exception, các lỗi đóng sẽ được ghi dưới dạng suppressed theo thứ tự xảy ra.

**8. `getCause()` và `getSuppressed()` giống nhau không?**  
Không. Cause diễn tả quan hệ nguyên nhân của exception wrapper; suppressed lưu các exception phụ được giữ lại, thường khi đóng tài nguyên.

**9. Nếu resource B ném lỗi ngay trong constructor còn A đã mở, A có được đóng không?**  
`try-with-resources` cố đóng A. Nếu việc đóng A cũng ném, lỗi đóng có thể bị suppressed vào lỗi khởi tạo B.

**10. Một lỗi cleanup được suppressed có nghĩa nó không cần xử lý nữa không?**  
Không. Suppressed chỉ giữ thông tin chẩn đoán và ưu tiên lỗi chính; cần xem xét trạng thái tài nguyên, rò rỉ, dữ liệu hoặc side effect.

## 20. Checklist thiết kế cleanup

Trước khi hoàn tất mã Java có xử lý tài nguyên:

- Tài nguyên có triển khai `AutoCloseable` không? Nếu có, ưu tiên `try-with-resources`.
- Có cả đường công việc chính thất bại **và** cleanup thất bại không? Đã test cả hai xảy ra cùng lúc chưa?
- Nếu dùng `finally`, liệu một lệnh `throw` hoặc `return` mới có thể che exception đang chờ không?
- Mã cleanup có phân biệt **lỗi chính** với **lỗi phụ** không?
- Caller có thể đọc `getSuppressed()` và `getCause()` để điều tra đầy đủ không?
- Với nhiều tài nguyên, thứ tự tạo/đóng và việc xử lý lỗi trong khởi tạo đã được kiểm tra chưa?
- Với checked exception, chữ ký `throws` có đúng hợp đồng không? Nếu close có thể ném checked exception, caller phải được xử lý/khai báo phù hợp.
- Việc đóng tài nguyên thất bại có ảnh hưởng tính nhất quán của dữ liệu hoặc xác nhận thành công không?
- Có chỗ nào đang `catch (Exception ignored)` và bỏ qua một lỗi cần được ghi nhận không?
- Log có đủ ngữ cảnh nhưng không lộ dữ liệu nhạy cảm và không bị lặp vô ích ở mọi tầng không?

## 21. Ghi nhớ cuối cùng

**Ngoại lệ cleanup che mất ngoại lệ ban đầu khi đoạn dọn dẹp (thường trong `finally`) hoàn tất bất thường và thay thế lý do thất bại đang được truyền ra.** Điều này có thể khiến caller nhận một lỗi đóng tài nguyên thay vì lỗi nghiệp vụ/kỹ thuật ban đầu. `return` trong `finally` còn có thể xóa luôn tín hiệu exception.

**`try-with-resources` là cách ưu tiên cho tài nguyên `AutoCloseable`**: nếu công việc chính ném A và đóng ném B, Java giữ A là primary và gắn B trong `getSuppressed()`; nếu chỉ đóng thất bại, B là lỗi chính. Đừng nhầm suppressed với cause, và đừng nghĩ việc lưu hai lỗi tự động phục hồi hệ thống.

Nguyên tắc áp dụng: **bảo toàn nguyên nhân đầu tiên có ý nghĩa, không để cleanup vô tình ghi đè nó, vẫn ghi nhận các lỗi dọn dẹp, và bảo đảm caller hiểu rõ thao tác đã thành công hay thất bại.**


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="finally-008">Giới hạn thực thi finally khi JVM dừng, tiến trình bị kết thúc hoặc mã không thoát</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn thực thi `finally` khi JVM dừng, tiến trình bị kết thúc hoặc mã không thoát

Trong Java, `finally` là cơ chế thực thi công việc cuối cùng khi luồng điều khiển **rời khỏi một cấu trúc `try`**. Nó hữu ích để dọn dẹp, giải phóng khóa hoặc ghi nhận trạng thái khi thao tác hoàn thành, ném ngoại lệ, hay thoát sớm bằng `return`, `break` hoặc `continue`.

Tuy nhiên, **`finally` không phải lời hứa rằng mã dọn dẹp sẽ luôn được thực thi trong mọi tình huống**. Nó không thể bảo đảm chạy khi JVM bị chấm dứt ngay, tiến trình bị hệ điều hành kết thúc cưỡng bức, thiết bị mất nguồn hoặc đoạn mã không bao giờ rời khỏi `try`. Ngay cả khi `finally` bắt đầu chạy, nó vẫn có thể bị ngắt giữa chừng bởi lỗi mới hoặc kết thúc tiến trình.

Bài này phân biệt những tình huống thường bị gộp nhầm dưới câu hỏi “Tại sao `finally` không chạy?”, từ đó lựa chọn cách quản lý tài nguyên và tính nhất quán dữ liệu phù hợp.

## 1. Điều kiện để `finally` được thực thi

Cấu trúc cơ bản:

~~~java
try {
    // Công việc chính.
} catch (SomeException e) {
    // Xử lý lỗi phù hợp.
} finally {
    // Công việc cuối cùng.
}
~~~

Đây là **đoạn cú pháp minh họa**, trong đó `SomeException` là một loại ngoại lệ có thật trong chương trình và khối `try` phải phù hợp với nhánh `catch`.

Đối với **luồng điều khiển Java thông thường**, `finally` được thực thi khi quá trình rời `try` hoặc `catch` liên quan bắt đầu, bao gồm:

- Thân `try` chạy tới cuối mà không có lỗi.
- `return` yêu cầu trả về từ phương thức.
- `break` hoặc `continue` rời phạm vi `try` tương ứng.
- Ngoại lệ xuất hiện; nó có thể được `catch` trong cùng cấu trúc xử lý hoặc lan truyền ra vùng bao ngoài.

Ví dụ:

~~~java
static int normalReturn() {
    try {
        System.out.println("try");
        return 7;
    } finally {
        System.out.println("finally");
    }
}
~~~

Khi gọi `normalReturn()`, Java in `try` rồi `finally` trước khi caller nhận giá trị `7`. Lệnh `return` không bỏ qua `finally`.

**Nhưng tất cả những trường hợp trên cùng có một tiền đề quan trọng:** luồng thực thi còn vận hành theo cơ chế điều khiển của Java và đang **rời** phạm vi cần dọn dẹp. Nếu chương trình ngừng đột ngột mà không trải qua bước rời phạm vi đó, hoặc đứng mãi trong `try`, không thể suy ra `finally` sẽ được chạy.

## 2. Bảng phân loại các tình huống

| Tình huống | `finally` có chạy không? | Bản chất |
| --- | --- | --- |
| `try` kết thúc bình thường | Có, theo luồng thông thường | Rời cấu trúc có kiểm soát |
| `return`/`break`/`continue` rời `try` | Có, thông thường | Thoát phạm vi theo luật ngôn ngữ |
| Ngoại lệ Java thông thường thoát `try` | Có, khi rời phạm vi | Tháo dần stack và tìm handler |
| `InterruptedException` được ném khỏi thao tác chờ | Có, khi luồng rời `try` | Ngắt thao tác theo cơ chế ngoại lệ |
| `System.exit(status)` thực sự kết thúc JVM | **Không được bảo đảm**, thông thường `finally` của stack đang chạy bị bỏ qua | Khởi động quá trình shutdown của JVM, không phải `return` |
| `Runtime.getRuntime().halt(status)` | **Không được bảo đảm**, thông thường không chạy | Chấm dứt VM ngay, không thực hiện shutdown thông thường |
| Hệ điều hành kill cưỡng bức, tiến trình bị crash, mất điện | **Không được bảo đảm** | Không còn cơ hội thực thi bytecode Java |
| Luồng kẹt mãi trong vòng lặp hoặc chờ không kết thúc | **Chưa chạy** nếu vẫn ở trong `try` | Không xảy ra bước rời phạm vi |
| JVM tự kết thúc vì không còn luồng non-daemon | Không thể dựa vào `finally` của daemon thread còn hoạt động | Shutdown VM có thể cắt ngang công việc daemon |
| Một `finally` khác tự ném lỗi | Khối hiện tại có thể bắt đầu nhưng không hoàn thành | Quá trình dọn dẹp có thể thất bại |

**Lưu ý cách diễn đạt:** `finally` không chạy trong một ví dụ cụ thể **không** đồng nghĩa Java đã “bỏ quy tắc `finally`”. Có thể nguyên nhân là không xảy ra bước rời `try` theo ngữ nghĩa thông thường, JVM shutdown không tháo stack như `return`, hoặc quá trình thực thi đã bị chấm dứt.

## 3. `System.exit(status)` khác `return` ở đâu?

`System.exit(int status)` yêu cầu chấm dứt JVM và tương đương về ý nghĩa với lời gọi `Runtime.getRuntime().exit(status)`. Nó **không phải** lệnh `return` khỏi phương thức `main` và không khởi tạo quá trình tháo mọi khung gọi để chạy `finally`.

Xét:

~~~java
static void exitExample() {
    try {
        System.out.println("Trước System.exit");
        System.out.flush();
        System.exit(0);
    } finally {
        System.out.println("finally sau System.exit");
    }
}
~~~

Đây là **phương thức minh họa**. Nếu `System.exit(0)` thực sự làm JVM bắt đầu và hoàn tất shutdown, bạn **không được dựa vào** dòng `finally sau System.exit` sẽ được in. Trong cách chạy thông thường, nó sẽ không xuất hiện vì JVM kết thúc thay vì cho phương thức này rời `try` theo đường `return`.

Một vài chi tiết quan trọng:

- `status = 0` thường biểu thị kết thúc thành công theo quy ước hệ điều hành; trạng thái khác `0` thường chỉ kết thúc không thành công. **Giá trị status không quyết định có chạy `finally` hay không**.
- `System.exit` thực hiện quá trình shutdown có tổ chức của JVM, trong đó có **shutdown hooks** đã đăng ký, nhưng không bảo đảm các phương thức đang chạy được quay lại để thực thi `finally`.
- Nếu việc gọi `System.exit` không thật sự chấm dứt JVM (chẳng hạn bị một cơ chế an ninh trong môi trường cũ chặn bằng ngoại lệ), các quy tắc điều khiển Java tương ứng lại được áp dụng. Không nên dùng ngoại lệ hiếm gặp này để thiết kế việc dọn dẹp.
- Gọi `System.out.flush()` có thể hỗ trợ đẩy dữ liệu đang đệm trước khi kết thúc, nhưng **không** biến công việc trong `finally` thành được bảo đảm.

### Vì sao `return` khỏi `main` khác `System.exit`?

~~~java
public static void main(String[] args) {
    try {
        System.out.println("main đang chạy");
        return;
    } finally {
        System.out.println("main finally");
    }
}
~~~

Đây là đoạn **phương thức `main` minh họa**, cần đặt trong một lớp. `return` khiến `main` thoát theo luật Java nên `finally` chạy. Tuy nhiên, sau khi `main` trả về, JVM có thể tiếp tục nếu còn các luồng non-daemon khác đang chạy.

Ngược lại, `System.exit(...)` yêu cầu shutdown của **cả JVM** thay vì chỉ chấm dứt lời gọi phương thức hiện tại. Cần tách biệt hai khái niệm: **kết thúc một phương thức** và **chấm dứt tiến trình JVM**.

## 4. `Runtime.halt(status)`: kết thúc đột ngột hơn `System.exit`

`Runtime.getRuntime().halt(int status)` là cơ chế chấm dứt JVM một cách cưỡng bức. Nó **không thực hiện chu trình shutdown thông thường** và không chạy các shutdown hook còn chờ.

~~~java
static void haltExample() {
    try {
        System.out.println("Trước halt");
        System.out.flush();
        Runtime.getRuntime().halt(1);
    } finally {
        System.out.println("finally sau halt");
    }
}
~~~

Trong lần chạy mà `halt(1)` thật sự chấm dứt VM, chương trình không thể dựa vào `finally` này hoặc các shutdown hook để dọn dẹp. Dòng sau `halt` trong ví dụ thường không xuất hiện.

| Đặc điểm | `System.exit(status)` | `Runtime.halt(status)` |
| --- | --- | --- |
| Mục tiêu | Shutdown JVM theo quy trình thông thường | Dừng VM ngay |
| Shutdown hooks | Thông thường được JVM khởi chạy theo cơ chế shutdown | Không được chạy như một bước shutdown |
| Chờ hook hoàn tất | Theo cơ chế JVM shutdown, trừ khi bị ngắt/bị halt | Không |
| Tháo stack để chạy mọi `finally` | Không bảo đảm | Không bảo đảm |
| Phù hợp với dọn dẹp dữ liệu bình thường | Không nên dùng thay cơ chế đóng tài nguyên có kiểm soát | Không nên dùng |

`Runtime.halt` có thể hữu ích ở một số tình huống vận hành rất đặc biệt, nhưng không phải cách phù hợp để thoát một phương thức, hủy một tác vụ hay xử lý ngoại lệ thông thường.

## 5. Shutdown hook không thay thế `finally`

Có thể đăng ký hook bằng:

~~~java
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    System.out.println("shutdown hook");
}));
~~~

Đoạn trên cần đặt trong một phương thức và được thực hiện trước khi shutdown bắt đầu. Một shutdown hook là một luồng Java đã đăng ký để JVM **khởi chạy trong giai đoạn shutdown có tổ chức**, chẳng hạn khi `System.exit` hoặc một số tín hiệu kết thúc được xử lý theo quy trình shutdown của JVM.

Điều này tạo ra hai cơ chế khác nhau:

- **`finally`:** gắn với việc **rời một phạm vi `try`** trong luồng điều khiển Java.
- **Shutdown hook:** gắn với **giai đoạn JVM shutdown**, chạy theo cơ chế riêng.

Vì vậy có tình huống shutdown hook chạy nhưng khối `finally` trong phương thức đang gọi `System.exit` không chạy.

### Những giới hạn của shutdown hooks

1. **Không có thứ tự chạy mặc định giữa các hook.** Nhiều hook có thể được khởi chạy đồng thời; không nên suy ra thứ tự từ lúc đăng ký.
2. **Không có bảo đảm hook sẽ chạy xong.** Hook có thể treo, ném lỗi hoặc bị quá trình chấm dứt cưỡng bức ngắt giữa chừng.
3. **`Runtime.halt` và kill cưỡng bức bỏ qua shutdown thông thường.** Vì vậy hook không thay thế được thiết kế phục hồi sau crash.
4. **Không được dựa vào trạng thái các luồng khác đang thay đổi trong lúc shutdown.** Các luồng khác có thể vẫn đang hoạt động; shutdown hook nên có phạm vi trách nhiệm nhỏ, an toàn, có thời hạn.
5. **Ghi log không đồng nghĩa dữ liệu đã bền vững.** Nội dung có thể còn trong bộ đệm ứng dụng, runtime, hệ điều hành hoặc thiết bị lưu trữ.

Nên dùng shutdown hook cho những hành động **best-effort** phù hợp với giai đoạn dừng dịch vụ, không dùng như cơ chế duy nhất bảo đảm mọi giao dịch đều hoàn tất.

## 6. Tiến trình bị hệ điều hành kết thúc hoặc gặp sự cố

JVM là một tiến trình của hệ điều hành. Nếu tiến trình bị kết thúc theo cách không cho code Java tiếp tục thực thi, các khối `finally` không có cơ hội chạy.

Những ví dụ cần phân biệt:

- **Kill cưỡng bức:** trên hệ Unix-like, `SIGKILL` (ví dụ qua `kill -9`) không thể được tiến trình bắt để thực hiện dọn dẹp Java trước khi kết thúc. Trên Windows, cơ chế kết thúc tiến trình cưỡng bức cũng không cung cấp bảo đảm tháo stack Java.
- **Tín hiệu kết thúc có thể xử lý:** một yêu cầu kết thúc “mềm” như `SIGTERM` có thể đưa JVM vào quá trình shutdown và chạy hook tùy nền tảng/runtime, nhưng **không nên cam kết mọi `finally` của mọi luồng được thực thi**.
- **Crash JVM/native code:** lỗi nghiêm trọng khiến tiến trình không còn chạy bình thường có thể ngăn hoàn toàn việc thực thi mã Java tiếp theo.
- **Mất điện, lỗi phần cứng, hết tài nguyên nghiêm trọng:** không thể giả định tiến trình còn đủ khả năng để chạy hoặc hoàn tất mã dọn dẹp.

Không nên dạy một quy tắc đơn giản như “mọi tín hiệu từ hệ điều hành đều cho JVM chạy `finally`” hoặc “mọi tín hiệu đều không thể chạy shutdown hook”. Hành vi phụ thuộc loại kết thúc, nền tảng và cơ chế JVM, nhưng **thiết kế an toàn phải giả định có thể không có cơ hội dọn dẹp**.

### So sánh kết thúc có kiểm soát và kết thúc cưỡng bức

~~~text
Ứng dụng hoạt động
       |
       +-- Đường kết thúc tác vụ bình thường
       |      |
       |      +-- rời try -> finally -> trả kết quả / dừng tác vụ
       |
       +-- JVM bắt đầu shutdown
       |      |
       |      +-- shutdown hooks có thể chạy
       |      +-- không có bảo đảm tháo các try của mọi luồng
       |
       +-- Tiến trình bị kill cưỡng bức / crash
              |
              +-- Không thể trông chờ finally hoặc hook
~~~

Sơ đồ diễn tả phạm vi trách nhiệm và giới hạn bảo đảm, không mô tả thứ tự chính xác của mọi sự kiện shutdown trên tất cả JVM.

## 7. Mã không bao giờ thoát khỏi `try`

Đôi khi không có `System.exit`, `halt` hay kill từ bên ngoài. `finally` **vẫn chưa chạy** vì chương trình chưa rời khối `try`.

Ví dụ:

~~~java
static void neverLeavesTry() {
    try {
        while (true) {
            // Vòng lặp không có đường thoát.
            Thread.onSpinWait();
        }
    } finally {
        System.out.println("Không tới đây khi còn lặp vô hạn");
    }
}
~~~

Đây là phương thức minh họa, **không nên gọi trực tiếp khi học** vì vòng lặp không kết thúc và có thể tiêu thụ CPU. Khối `finally` không được thực thi chừng nào luồng vẫn ở trong `try`; Java không tự ném ngoại lệ chỉ vì bạn đã chờ lâu.

Một trường hợp thực tế hơn là luồng chờ mãi cho dữ liệu từ socket, lock, hàng đợi hoặc điều kiện đồng bộ. Nếu thao tác chờ không hoàn tất, không bị hủy và không ném ngoại lệ, luồng chưa rời `try`. `finally` chỉ là phần dọn dẹp **khi ra khỏi vùng**, không phải bộ hẹn giờ buộc vùng đó phải kết thúc.

### Ví dụ chờ có thể bị gián đoạn

~~~java
static void waitForSignal() throws InterruptedException {
    java.util.concurrent.CountDownLatch latch =
            new java.util.concurrent.CountDownLatch(1);
    try {
        latch.await();
        System.out.println("Nhận tín hiệu");
    } finally {
        System.out.println("Đã rời vùng chờ");
    }
}
~~~

Đây là phương thức minh họa. Nếu không có luồng nào gọi `countDown()` và không có yêu cầu gián đoạn, `await()` có thể chờ vô hạn; `finally` chưa chạy. Nếu luồng bị interrupt trong khi chờ, `await()` ném `InterruptedException`; lúc đó luồng **rời `try`**, và `finally` thường chạy trước khi ngoại lệ truyền ra bên gọi.

Lưu ý: không phải mọi thao tác chờ đều hỗ trợ ngắt bằng `Thread.interrupt()`. Có API chờ phản ứng với interrupt, có API yêu cầu đóng tài nguyên hoặc một cơ chế hủy khác.

## 8. `Thread.interrupt()` không đồng nghĩa kill luồng

Một hiểu nhầm phổ biến là “ngắt luồng thì `finally` sẽ không chạy”. `Thread.interrupt()` chủ yếu **đặt trạng thái interrupt** và làm một số thao tác chờ có khả năng gián đoạn ném `InterruptedException`. Nó không tương đương kill tiến trình hay buộc mọi câu lệnh đang chạy dừng ngay.

Ví dụ:

~~~java
static void interruptible() {
    try {
        try {
            System.out.println("Trước sleep");
            Thread.currentThread().interrupt();
            Thread.sleep(100);
        } finally {
            System.out.println("finally sau interrupt");
        }
    } catch (InterruptedException e) {
        System.out.println("Đã bắt InterruptedException");
        Thread.currentThread().interrupt();
    }
}
~~~

Khi gọi trong một luồng bình thường chưa bị ngắt trước đó, stdout:

~~~text
Trước sleep
finally sau interrupt
Đã bắt InterruptedException
~~~

Ở đây `Thread.sleep` phát hiện trạng thái interrupt và ném `InterruptedException`. Khối `finally` của `try` trong chạy khi ngoại lệ rời khối; sau đó `catch` ngoài nhận lỗi.

**Chính quá trình ném và lan truyền `InterruptedException` khiến `finally` chạy**, không phải hành vi “interrupt luôn gọi `finally`” theo một cơ chế đặc biệt.

Nếu đoạn mã lặp liên tục, phớt lờ interrupt và không rời `try`, `finally` có thể tiếp tục không chạy. Khi thiết kế tác vụ dài hạn, nên có điều kiện dừng, xử lý interrupt theo hợp đồng và giải phóng tài nguyên ở đường thoát thích hợp.

## 9. Luồng daemon và thời điểm JVM kết thúc

JVM thông thường còn tiếp tục chạy khi vẫn có luồng **non-daemon** đang sống, dù phương thức `main` đã kết thúc. Ngược lại, khi tất cả luồng non-daemon kết thúc, JVM có thể bắt đầu shutdown trong lúc một số daemon thread vẫn đang hoạt động.

Do đó không nên giả định:

> “Nếu một daemon thread còn ở trong `try`, JVM chắc chắn sẽ cho nó chạy hết `finally` trước khi thoát.”

Điều đó **không được bảo đảm**. Daemon thread thường phục vụ công việc nền và không giữ JVM sống chỉ để hoàn thành phần việc của nó. Nếu cần hoàn tất thao tác quan trọng, hãy quản lý lifecycle rõ ràng: tín hiệu dừng, chờ luồng/tác vụ hoàn thành có giới hạn và đóng tài nguyên trước khi dừng ứng dụng.

Một số nguyên tắc:

- Không dựa vào daemon thread để flush dữ liệu quan trọng sau khi các luồng nghiệp vụ kết thúc.
- Với `ExecutorService`, chủ động yêu cầu `shutdown()`, chờ `awaitTermination(...)` khi hợp đồng cho phép, và xác định chính sách hủy phần việc còn lại.
- Không xem `shutdownNow()` là bảo đảm mọi tác vụ đã bị dừng; nó thường cố gắng ngắt các tác vụ nhưng tác vụ có thể không phản hồi.
- Hãy phân biệt dừng **một nhiệm vụ** và dừng **cả JVM**.

`finally` vẫn có giá trị trong một daemon thread **nếu thread đó thực sự rời phạm vi `try` theo luồng Java bình thường**. Vấn đề là vòng đời JVM có thể kết thúc trước lúc ấy.

## 10. Ví dụ Java 17+ chạy độc lập, không tự treo ở chế độ mặc định

Chương trình có các chế độ riêng. Mặc định chỉ chạy trường hợp an toàn `normal`; các chế độ `exit`, `halt` và `wait` **phải chạy trong tiến trình Java riêng** nếu muốn thử. Chúng không phải phần của luồng mặc định.

Lưu thành **`FinallyLimitsDemo.java`**:

~~~java
import java.util.concurrent.CountDownLatch;

public class FinallyLimitsDemo {
    public static void main(String[] args) {
        String mode = args.length == 0 ? "normal" : args[0];

        switch (mode) {
            case "normal" -> normal();
            case "exception" -> exceptionCase();
            case "interrupt" -> interruptCase();
            case "exit" -> exitCase();
            case "halt" -> haltCase();
            case "wait" -> waitCase();
            default -> System.out.println("Unknown mode");
        }
    }

    static void normal() {
        try {
            System.out.println("try: normal");
            return;
        } finally {
            System.out.println("finally: normal");
        }
    }

    static void exceptionCase() {
        try {
            try {
                System.out.println("try: exception");
                throw new IllegalStateException("boom");
            } finally {
                System.out.println("finally: exception");
            }
        } catch (IllegalStateException e) {
            System.out.println("caught: " + e.getMessage());
        }
    }

    static void interruptCase() {
        try {
            try {
                System.out.println("try: sleep");
                Thread.currentThread().interrupt();
                Thread.sleep(100);
            } finally {
                System.out.println("finally: interrupt");
            }
        } catch (InterruptedException e) {
            System.out.println("caught: interrupted");
            Thread.currentThread().interrupt();
        }
    }

    static void exitCase() {
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> System.out.println("hook: exit"))
        );

        try {
            System.out.println("try: exit");
            System.out.flush();
            System.exit(0);
        } finally {
            System.out.println("finally: exit");
        }
    }

    static void haltCase() {
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> System.out.println("hook: halt"))
        );

        try {
            System.out.println("try: halt");
            System.out.flush();
            Runtime.getRuntime().halt(0);
        } finally {
            System.out.println("finally: halt");
        }
    }

    static void waitCase() {
        CountDownLatch neverReleased = new CountDownLatch(1);
        try {
            System.out.println("try: wait");
            System.out.flush();
            neverReleased.await();
        } catch (InterruptedException e) {
            System.out.println("caught: wait interrupted");
            Thread.currentThread().interrupt();
        } finally {
            System.out.println("finally: wait");
        }
    }
}
~~~

Biên dịch:

~~~bash
javac FinallyLimitsDemo.java
~~~

Chạy ba chế độ an toàn **riêng từng lần**, không chấm dứt cưỡng bức tiến trình:

~~~bash
java FinallyLimitsDemo
java FinallyLimitsDemo exception
java FinallyLimitsDemo interrupt
~~~

**Stdout chính xác của `java FinallyLimitsDemo`:**

~~~text
try: normal
finally: normal
~~~

**Stdout chính xác của `java FinallyLimitsDemo exception`:**

~~~text
try: exception
finally: exception
caught: boom
~~~

**Stdout chính xác của `java FinallyLimitsDemo interrupt`:**

~~~text
try: sleep
finally: interrupt
caught: interrupted
~~~

Ba chế độ này làm rõ: `return` và các ngoại lệ thông thường, kể cả `InterruptedException` từ thao tác chờ, không tự động ngăn `finally`.

### Chạy thử `System.exit` trong tiến trình riêng

~~~bash
java FinallyLimitsDemo exit
~~~

Trong môi trường JVM Java 17 thông thường, khi `System.exit(0)` thực sự chấm dứt JVM, có thể quan sát đầu ra tương tự:

~~~text
try: exit
hook: exit
~~~

Đây là **đầu ra thường thấy, không phải cam kết tuyệt đối về mọi môi trường**. Dòng `finally: exit` không được bảo đảm sẽ xuất hiện và thông thường không xuất hiện. Shutdown hook có cơ hội được chạy trong quy trình shutdown, khác với khối `finally` đang nằm trên stack của luồng gọi `System.exit`.

Không diễn giải tình huống này thành “`System.exit` đảm bảo hook in được mọi thứ”: có thể xảy ra chấm dứt cưỡng bức, I/O thất bại hoặc hook không hoàn thành.

### Chạy thử `Runtime.halt` trong tiến trình riêng

~~~bash
java FinallyLimitsDemo halt
~~~

Trong một JVM hoạt động bình thường, có thể chỉ thấy:

~~~text
try: halt
~~~

Đây cũng là **đầu ra minh họa thông thường**, không phải lời hứa rằng mọi thông báo đều ra console trong mọi môi trường. `Runtime.halt(0)` dừng VM mà không thực hiện shutdown hook `hook: halt`; `finally: halt` cũng không được thực thi theo đường thoát bình thường.

### Chế độ `wait` cố ý không hoàn thành

**Không chạy chế độ sau như một phần của bài kiểm tra tự động hoặc trong terminal không thể hủy tác vụ:**

~~~bash
java FinallyLimitsDemo wait
~~~

`CountDownLatch` được khởi tạo với count bằng `1` nhưng trong chương trình không có nơi `countDown()`, nên `await()` sẽ chờ cho tới khi luồng bị ngắt hoặc tiến trình bị kết thúc. Nếu không có sự kiện làm nó thoát, chương trình in `try: wait` rồi cứ chờ; `finally: wait` chưa được thực thi.

Khi cần kiểm tra sự khác nhau giữa ngắt thao tác và kill cưỡng bức, phải sử dụng môi trường thử nghiệm có thể kiểm soát tiến trình. Không dùng lệnh kill nhầm vào JVM dịch vụ hoặc tiến trình của người khác.

## 11. Phân tích: “`finally` chưa chạy” không đồng nghĩa “`finally` bị bỏ qua”

Có ba tình huống cần dùng từ chính xác:

1. **Đã rời `try` và chạy `finally`:** ví dụ `return`, `throw` bình thường; khối dọn dẹp được thực thi.
2. **Chưa rời `try` nên chưa đến `finally`:** ví dụ `await()` không có tín hiệu hoặc vòng lặp vô hạn.
3. **Không còn cơ hội thực thi `finally`:** ví dụ VM bị `halt`, tiến trình bị kill cưỡng bức, thiết bị mất nguồn.

Trong trường hợp (2), nếu sau đó luồng được đánh thức, gặp timeout, nhận interrupt và thực sự thoát phạm vi `try`, `finally` **có thể chạy**. Trong trường hợp (3), code Java không còn cơ hội tiếp tục theo đường bình thường.

Còn một trường hợp thứ tư: **`finally` đã bắt đầu nhưng không hoàn tất**. Ví dụ lệnh đóng tài nguyên trong `finally` ném ngoại lệ, hoặc tiến trình bị chấm dứt giữa quá trình đóng. Vì vậy một dòng log ở đầu `finally` không chứng minh toàn bộ công việc dọn dẹp đã thành công.

## 12. `finally` tự ném lỗi và giới hạn của việc dọn dẹp

Xét:

~~~java
static void cleanupCanFail() {
    try {
        throw new IllegalArgumentException("main failure");
    } finally {
        throw new IllegalStateException("cleanup failure");
    }
}
~~~

Phương thức minh họa này kết thúc bằng ngoại lệ `IllegalStateException("cleanup failure")`. Lỗi ban đầu `IllegalArgumentException` bị thay thế và **không tự được gắn làm cause hoặc suppressed exception** trong cấu trúc `try/finally` thủ công đó.

Ngay cả khi JVM đang chạy bình thường, `finally` vẫn không đồng nghĩa mọi dọn dẹp an toàn hoặc thành công. Lưu ý hai mặt:

- Mã dọn dẹp có thể ném lỗi mới và che lỗi chính.
- Mã dọn dẹp có thể chạy lâu, kẹt lock hoặc gặp trạng thái không còn hợp lệ.

Không nên để một lệnh `return` trong `finally` vô tình nuốt lỗi đang truyền. Tránh viết logic nghiệp vụ dài và phức tạp trong `finally`; ưu tiên cấu trúc xử lý đóng tài nguyên có kiểm soát và bọc nguyên nhân khi cần.

## 13. Try-with-resources cũng có giới hạn khi tiến trình chấm dứt

Try-with-resources là lựa chọn tốt khi thao tác với tài nguyên `AutoCloseable`:

~~~java
static String firstLine(String text) throws java.io.IOException {
    try (java.io.BufferedReader reader =
                 new java.io.BufferedReader(
                         new java.io.StringReader(text))) {
        return reader.readLine();
    }
}
~~~

Đây là một phương thức minh họa hợp lệ trong Java 17+. Khi rời `try` theo đường Java thông thường, tài nguyên được đóng tự động. Nếu thân `try` ném ngoại lệ còn `close()` cũng ném, ngoại lệ khi đóng thường được giữ dưới dạng `suppressed exception` thay vì thay thế lỗi chính như một `finally` thủ công viết sai.

Nhưng **try-with-resources không phải cơ chế dọn dẹp sau crash**. Việc đóng tài nguyên vẫn cần mã Java được thực thi khi rời phạm vi. Nếu JVM bị `Runtime.halt`, kill cưỡng bức hoặc mất nguồn trước thời điểm đó, không thể dựa vào lệnh `close()` tự động đã chạy.

Cần phân biệt:

- **Quản lý scope đúng:** try-with-resources giúp bảo đảm đóng trong các đường thoát thông thường.
- **Chống mất dữ liệu khi crash:** cần thiết kế lưu trữ, transaction, commit/rollback, cơ chế phục hồi và xác nhận độ bền ở tầng hệ thống.

Đóng một `BufferedWriter` đúng cách là quan trọng, nhưng `finally` hoặc try-with-resources **không thay thế** cho chính sách flush, fsync, giao dịch hay các bảo đảm lưu trữ cụ thể khi hệ thống có yêu cầu độ bền cao.

## 14. Thiết kế tác vụ để có cơ hội chạy `finally`

Muốn `finally` thực thi đáng tin cậy **trong các đường vận hành bình thường**, hãy tạo các điều kiện để thao tác có thể kết thúc có kiểm soát.

### Có timeout cho các thao tác chờ

Không nên để mọi lời gọi mạng hoặc chờ lock tồn tại vô hạn khi ứng dụng yêu cầu shutdown đúng hạn. Dùng timeout phù hợp của từng API, chẳng hạn timeout kết nối/đọc dữ liệu hoặc chờ có giới hạn. Timeout có thể khiến thao tác trả trạng thái thất bại hoặc ném ngoại lệ, giúp luồng rời `try` và chạy dọn dẹp.

Timeout phải được xử lý theo hợp đồng: chỉ retry khi an toàn, có giới hạn thời gian và tránh lặp vô hạn gây quá tải.

### Hỗ trợ yêu cầu hủy và interrupt

Ví dụ một vòng xử lý dài nhưng có đường thoát:

~~~java
static void doWork(java.util.concurrent.atomic.AtomicBoolean running) {
    try {
        while (running.get()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }
            // Thực hiện một đơn vị công việc nhỏ, hữu hạn.
            doOneStep();
        }
    } finally {
        releaseResources();
    }
}
~~~

Đây là **đoạn minh họa**, `doOneStep()` và `releaseResources()` là các hàm của ứng dụng. Giả định một đơn vị công việc có thể hoàn thành hoặc phản hồi hủy; nếu `doOneStep()` tự chặn vô hạn, chỉ kiểm tra `running` ở đầu vòng vẫn không đủ.

`Thread.interrupt()` là tín hiệu hủy hợp tác, không phải kill tiến trình. Nếu phương thức đang chờ ném `InterruptedException`, hãy tuân thủ hợp đồng của tác vụ, thường là chuyển tiếp hoặc khôi phục cờ ngắt bằng `Thread.currentThread().interrupt()` khi không thể chuyển tiếp, rồi kết thúc công việc phù hợp.

### Đóng dịch vụ theo nhiều bước

Một quy trình shutdown có kiểm soát có thể gồm:

1. Ngừng nhận công việc mới.
2. Ra tín hiệu yêu cầu những tác vụ hiện tại kết thúc.
3. Chờ trong một thời hạn phù hợp và xử lý các tác vụ không phản hồi.
4. Đóng tài nguyên, hoàn tất hoặc hủy giao dịch đúng cách.
5. Kết thúc JVM sau khi các bước quan trọng đã được xác nhận.

Các bước trên diễn ra **khi ứng dụng còn sống**, thay vì hy vọng `finally` sẽ xử lý mọi thứ sau một lệnh `System.exit` bất ngờ.

## 15. Thiết kế an toàn khi không có `finally`

Với dữ liệu hoặc thao tác có giá trị, hãy giả định tiến trình có thể dừng **ở bất kỳ điểm nào** và có kế hoạch khôi phục.

| Nhu cầu | `finally` giúp gì? | Bảo vệ thêm cần cân nhắc |
| --- | --- | --- |
| Đóng socket/file trong hoạt động bình thường | Chạy mã đóng khi thoát `try` | Timeout, quản lý vòng đời, tài nguyên hệ điều hành |
| Không để giao dịch dở dang | Có thể gọi rollback khi đường lỗi được phát hiện | Transaction/atomic commit của hệ quản trị lưu trữ |
| Ghi dữ liệu quan trọng | Có thể yêu cầu flush/close trên đường bình thường | Cơ chế durable write, commit xác nhận, phục hồi sau crash |
| Tránh xử lý yêu cầu trùng khi khởi động lại | Không giải quyết đầy đủ | Idempotency key, deduplication, trạng thái bền vững |
| Hoàn thành job nền trước shutdown | Hữu ích nếu job thực sự thoát `try` | Graceful shutdown, chờ có giới hạn, quản lý non-daemon/daemon |
| Dọn dẹp sau tiến trình bị kill cưỡng bức | Không được bảo đảm | Lease, TTL, watchdog ngoài tiến trình, quy trình recovery |

### Ví dụ: ghi tệp thay thế an toàn hơn

Với một tệp cấu hình quan trọng, chỉ ghi trực tiếp lên tệp đích rồi đóng trong `finally` có thể để lại dữ liệu không hoàn chỉnh nếu crash giữa chừng.

Một chiến lược thường dùng là ghi vào **tệp tạm cùng hệ thống tệp**, đồng bộ dữ liệu khi cần, rồi đổi tên/thay thế tệp đích bằng thao tác nguyên tử **nếu hệ thống tệp và API thực sự hỗ trợ**. Sau crash, quy trình khởi động kiểm tra trạng thái và xử lý tệp tạm còn dư.

Không phải mọi `rename` hay `Files.move(..., ATOMIC_MOVE)` đều được mọi môi trường hỗ trợ, và “atomic rename” không tự bảo đảm dữ liệu đã được ghi bền vững trên thiết bị. Cần kiểm tra yêu cầu về durability và tính năng của hệ thống lưu trữ cụ thể.

Đó là ví dụ cho cách tư duy đúng: **khả năng chống crash đến từ giao thức cập nhật bền vững**, không chỉ từ `finally`.

## 16. Những hiểu lầm thường gặp

**Hiểu lầm 1 — “`finally` luôn chạy, không có ngoại lệ.”**  
Không đúng. `finally` là phần của luồng điều khiển Java, không phải cơ chế bất tử khi VM hoặc hệ điều hành dừng đột ngột.

**Hiểu lầm 2 — “`System.exit` cũng giống `return` trong `main`.”**  
Không. `return` rời phương thức bình thường và kích hoạt các `finally` liên quan. `System.exit` yêu cầu shutdown JVM, không bảo đảm tháo các khung gọi để chạy `finally`.

**Hiểu lầm 3 — “Có shutdown hook thì mọi `finally` cũng phải chạy.”**  
Không. Shutdown hook và `finally` là hai cơ chế khác nhau. Hook có thể được chạy trong shutdown thông thường ngay cả khi một `finally` trên stack của luồng đang gọi `System.exit` không được chạy.

**Hiểu lầm 4 — “`Runtime.halt` sẽ cho hook dọn dẹp trước.”**  
Không. `halt` chấm dứt VM mà không thực hiện shutdown bình thường. Không dùng nó khi cần một quy trình đóng có kiểm soát.

**Hiểu lầm 5 — “Dừng từ hệ điều hành thì Java luôn kịp lưu mọi thứ.”**  
Không. Kill cưỡng bức, crash và mất điện có thể không cho bytecode Java tiếp tục chạy.

**Hiểu lầm 6 — “Vòng lặp vô hạn khiến Java bỏ qua `finally`.”**  
Chưa chính xác. Nếu vòng lặp không thoát, chương trình đơn giản **chưa đi tới bước thực thi `finally`**. Khác với việc VM đã kết thúc và không còn cơ hội chạy.

**Hiểu lầm 7 — “`Thread.interrupt()` luôn bỏ qua `finally`.”**  
Không. Interrupt có thể khiến thao tác chờ ném `InterruptedException`; nếu luồng rời `try` thì `finally` thường chạy. Nếu code phớt lờ tín hiệu và không thoát, `finally` có thể vẫn chưa chạy.

**Hiểu lầm 8 — “Daemon thread sẽ được JVM chờ dọn dẹp hết trước khi thoát.”**  
Không có bảo đảm như vậy. Nếu công việc quan trọng phải hoàn tất, cần quản lý lifecycle và chờ đúng cách từ các luồng còn giữ JVM hoạt động.

**Hiểu lầm 9 — “Try-with-resources đóng tài nguyên kể cả sau SIGKILL.”**  
Không. Nó chỉ chạy logic đóng tài nguyên khi luồng Java có cơ hội rời scope.

**Hiểu lầm 10 — “Đã in log trong `finally` nghĩa là dữ liệu chắc chắn được lưu.”**  
Không. `finally` có thể mới bắt đầu, log có thể đang đệm, lệnh ghi có thể chưa bền vững hoặc hệ thống có thể crash ngay sau đó.

**Hiểu lầm 11 — “Bắt `Throwable` bảo đảm ứng dụng sẽ sống đủ lâu để chạy `finally`.”**  
Không. Bắt rộng không thể sửa tình huống JVM bị dừng cưỡng bức, mất điện hay code bị kẹt mãi. Bắt `Error` bừa bãi còn có thể khiến hệ thống tiếp tục trong trạng thái không an toàn.

**Hiểu lầm 12 — “Nếu `finally` chạy thì chắc chắn hoàn tất thành công.”**  
Không. Chính mã trong `finally` có thể ném ngoại lệ hoặc bị chặn, và nó có thể bị tiến trình kết thúc giữa chừng.

## 17. Kiểm tra kiến thức

1. `return` từ trong `try` có thông thường làm `finally` chạy trước khi caller nhận kết quả không?
2. Vì sao `System.exit(0)` không thể xem là đồng nghĩa với `return` khỏi `main`?
3. Shutdown hook được khởi chạy trong trường hợp nào, và tại sao không thể dùng nó làm cơ chế duy nhất để chống mất dữ liệu?
4. `Runtime.halt(1)` có chạy shutdown hook theo quy trình shutdown thông thường không?
5. Nếu tiến trình bị `SIGKILL`, có thể bảo đảm `finally` ghi xong dữ liệu không?
6. `while (true)` bên trong `try` không có đường thoát: nên mô tả `finally` “bị bỏ qua” hay “chưa được thực thi”?
7. Khi một luồng đang `CountDownLatch.await()` bị interrupt và `InterruptedException` thoát khỏi `try`, `finally` thường hoạt động thế nào?
8. `Thread.interrupt()` có tương đương với kết thúc cưỡng bức một tiến trình không?
9. Vì sao không nên đặt ghi dữ liệu quan trọng chỉ trong shutdown hook và giả định nó luôn hoàn tất?
10. Nếu daemon thread còn ở giữa `try` nhưng tất cả luồng non-daemon đã kết thúc, liệu JVM có phải chờ daemon thread chạy hết `finally` không?
11. Try-with-resources có đảm bảo đóng tài nguyên sau khi `Runtime.halt` không?
12. Trong `try/finally` thủ công, một `close()` ném ngoại lệ mới có thể gây tác dụng gì với ngoại lệ gốc?
13. Vì sao timeout và hỗ trợ hủy hợp tác giúp tăng cơ hội chạy `finally`?
14. Hãy nêu một cơ chế cần thiết ngoài `finally` để bảo vệ cập nhật dữ liệu khi mất điện.
15. Nếu một dòng log ở đầu `finally` đã được in, ta có thể kết luận toàn bộ mã dọn dẹp phía sau đã thành công không?
16. Nêu sự khác nhau giữa **dừng một tác vụ**, **shutdown JVM thông thường** và **kill cưỡng bức tiến trình**.
17. Với `java FinallyLimitsDemo exit` và `java FinallyLimitsDemo halt`, vì sao các đầu ra được mô tả là đầu ra thường thấy thay vì cam kết tuyệt đối?

### Gợi ý đáp án

1. Có, khi luồng Java rời cấu trúc theo cách bình thường.
2. `System.exit` yêu cầu kết thúc cả JVM, không tháo stack như `return` thông thường.
3. Hook thuộc giai đoạn shutdown có tổ chức; có thể không chạy/hoàn tất khi halt, kill hoặc crash.
4. Không.
5. Không.
6. “Chưa được thực thi” vì chưa xảy ra bước rời `try`.
7. Nó thường chạy trong quá trình lan truyền `InterruptedException`.
8. Không; interrupt là tín hiệu hợp tác và cơ chế của các thao tác chờ hỗ trợ ngắt.
9. Vì hook phụ thuộc việc JVM còn đủ cơ hội shutdown và hoàn thành I/O.
10. Không có bảo đảm.
11. Không.
12. Có thể thay thế/che ngoại lệ gốc; try-with-resources thường lưu lỗi đóng thành suppressed khi thân `try` đã ném lỗi chính.
13. Giúp tác vụ có đường ra khỏi `try` và giải phóng tài nguyên trước shutdown.
14. Ví dụ transaction/atomic commit, đồng bộ dữ liệu bền vững và quy trình khôi phục sau crash.
15. Không; phần còn lại có thể chưa chạy, ném lỗi hoặc bị chấm dứt.
16. Dừng tác vụ có thể đi qua `finally`; JVM shutdown có hook nhưng không tháo mọi stack; kill cưỡng bức có thể dừng tức thời.
17. Vì hành vi ghi console, cơ chế shutdown và môi trường runtime không phải một lời hứa rằng mọi dòng sẽ được xuất ra.

## 18. Ghi nhớ

**`finally` đáng tin cậy trong các đường thoát có kiểm soát của luồng thực thi Java, nhưng không phải bảo đảm tuyệt đối trong trường hợp JVM/tiến trình chấm dứt hoặc khi mã không bao giờ thoát `try`.** `System.exit` không đồng nghĩa với `return`; `Runtime.halt` và kill cưỡng bức còn làm giảm cơ hội dọn dẹp; shutdown hook có phạm vi riêng và cũng không chống được mọi sự cố. Hãy dùng `finally` hoặc try-with-resources để quản lý scope và tài nguyên trong vận hành bình thường, đồng thời xây dựng timeout, hủy hợp tác, shutdown có kiểm soát và cơ chế dữ liệu bền vững để chịu được những trường hợp mà mã Java không còn cơ hội chạy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="finally-009">Phân biệt finally, final và finalize</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt `finally`, `final` và `finalize()` trong Java

Ba tên **`finally`**, **`final`** và **`finalize()`** trông giống nhau nhưng có ý nghĩa hoàn toàn khác trong Java. `finally` là một phần của cấu trúc xử lý ngoại lệ, `final` là từ khóa hạn chế việc gán lại hoặc kế thừa/ghi đè, còn `finalize()` là một phương thức cũ thuộc cơ chế finalization của đối tượng, đã bị **deprecated** và không thích hợp để dọn dẹp tài nguyên đáng tin cậy.

Điểm cần nắm chắc: **`finally` gắn với luồng điều khiển của `try`; `final` gắn với khai báo trong mã nguồn; `finalize()` gắn với một cơ chế vòng đời đối tượng không xác định được thời điểm thực hiện.** Chúng không thể thay thế cho nhau và không có quan hệ “bản rút gọn/bản nâng cao”.

## 1. So sánh nhanh

| Tiêu chí | `finally` | `final` | `finalize()` |
| --- | --- | --- | --- |
| Loại cú pháp | Từ khóa mở đầu một khối trong cấu trúc `try` | Từ khóa modifier | Phương thức instance của `java.lang.Object` |
| Công dụng chính | Chạy công việc khi rời `try`/`catch` | Ngăn gán lại biến hoặc ngăn kế thừa/override | Cơ chế finalization cũ cho đối tượng trước khi bị thu hồi |
| Nơi xuất hiện | Sau phần thân `try` hoặc các `catch` tương ứng | Khai báo biến, tham số, field, phương thức, lớp (theo quy tắc) | `protected void finalize() throws Throwable` ở lớp kế thừa nếu override |
| Cơ chế thực thi | Khi điều khiển rời cấu trúc `try` qua các đường hợp lệ | Được compiler kiểm tra về phép gán và kế thừa | Có thể được hệ thống finalization gọi sau khi đối tượng đủ điều kiện, **không bảo đảm khi nào hoặc có chạy hay không** |
| Có phải công cụ xử lý ngoại lệ? | Có liên quan đến `try`, nhưng **không tự bắt lỗi** | Không | Không phải cơ chế xử lý ngoại lệ thông thường |
| Dùng trong Java mới? | Có, nhất là với công việc kết thúc/cleanup ngoài quản lý `AutoCloseable` | Có, rất phổ biến | **Không nên dùng cho thiết kế mới** |
| Ví dụ | `try { work(); } finally { cleanup(); }` | `final int limit = 10;` | `protected void finalize() throws Throwable` |

**Nhớ theo ba câu ngắn:** `finally` = *thực hiện khi rời try*; `final` = *giới hạn một số thay đổi trong mã*; `finalize()` = *cơ chế finalizer cũ, không đáng tin để đóng tài nguyên*.

## 2. `finally`: phần cuối của cấu trúc `try`

Dạng đơn giản:

~~~java
try {
    doWork();
} finally {
    cleanup();
}
~~~

Với cấu trúc có xử lý ngoại lệ:

~~~java
try {
    doWork();
} catch (IOException ex) {
    handleIOException(ex);
} finally {
    cleanup();
}
~~~

Các đoạn trên là **minh họa cấu trúc**, cần những phương thức và `import` tương ứng. Trong Java, `finally` là **từ khóa**, không phải tên phương thức, không có cặp ngoặc tham số `()`, không thể gọi bằng `object.finally()`.

### 2.1. Khi nào `finally` chạy?

Thông thường khi luồng thực thi rời một cấu trúc `try` liên quan, `finally` chạy dù phần `try`:

- hoàn tất bình thường, không ném ngoại lệ;
- ném ngoại lệ đã được một `catch` phù hợp xử lý;
- ném ngoại lệ chưa được bắt tại cấu trúc hiện tại, đang truyền lên tầng ngoài;
- đang chuẩn bị `return`, `break` hoặc `continue` làm rời cấu trúc;
- hoặc khi một `catch` được chọn tự ném tiếp ngoại lệ.

Ví dụ nhỏ:

~~~java
// Đặt trong phương thức.
try {
    System.out.println("Trong try");
} finally {
    System.out.println("Trong finally");
}
System.out.println("Sau try");
~~~

Stdout:

~~~text
Trong try
Trong finally
Sau try
~~~

Không có ngoại lệ nào, nhưng `finally` vẫn chạy. Vì vậy hiểu `finally` là “chỉ xử lý khi lỗi” là sai.

### 2.2. `finally` không tự xử lý hoặc nuốt ngoại lệ

~~~java
// Đặt trong phương thức được phép ném unchecked exception.
try {
    throw new IllegalStateException("thất bại");
} finally {
    System.out.println("Đã chạy finally");
}
~~~

Dòng in `Đã chạy finally` xuất hiện, sau đó `IllegalStateException` tiếp tục truyền ra ngoài nếu `finally` hoàn tất bình thường. Khối `finally` **không tương đương** với `catch (IllegalStateException)`.

Nếu muốn xử lý lỗi, dùng `catch` ở tầng có chiến lược xử lý phù hợp. Nếu muốn dọn dẹp lúc rời một thao tác, `finally` có thể thích hợp. Hai mục đích khác nhau.

### 2.3. `finally` không phải cam kết tuyệt đối bất chấp việc JVM dừng

Nếu JVM/tiến trình bị kết thúc cưỡng bức, gọi `Runtime.halt`, một đường `System.exit` hoàn tất việc dừng máy ảo, hoặc luồng không bao giờ rời khối `try`, không nên kỳ vọng `finally` vẫn hoàn tất. Sự cố như crash hoặc mất điện cũng có thể làm công việc chưa được thực hiện.

Bảo đảm của `finally` là **quy tắc luồng điều khiển Java khi thực thi còn tiếp tục**, không phải cơ chế phục hồi giao dịch bền vững. Các yêu cầu như ghi dữ liệu an toàn sau crash cần chiến lược khác.

## 3. `final`: modifier được kiểm tra khi biên dịch

`final` có ý nghĩa tùy theo nơi xuất hiện, nhưng đều nhằm giới hạn một số phép thay đổi:

- **Biến/field/parameter `final`:** không thể gán lại sau khi đã được gán giá trị theo luật definite assignment.
- **Phương thức `final`:** không thể bị override (hoặc hidden đối với những trường hợp phương thức static tương ứng) ở lớp con.
- **Lớp `final`:** không thể được kế thừa.
- **Tham số `catch` đơn có thể khai báo `final`:** không được gán lại biến tham chiếu đã bắt. Riêng tham số multi-catch được ngôn ngữ coi là final.

`final` **không tự đóng tài nguyên, không giữ lỗi khỏi lan truyền, không tạo `try` hoặc `catch`**.

### 3.1. Biến cục bộ `final`

~~~java
final int maxRetries = 3;
System.out.println(maxRetries); // 3

// Không biên dịch nếu bỏ chú thích:
// maxRetries = 4;
~~~

Biến được gán một lần thì không thể gán lại. Một biến cục bộ `final` chưa được khởi tạo ngay ở khai báo có thể được gán **một lần trên mỗi đường điều khiển hợp lệ** mà compiler xác nhận:

~~~java
final int limit;
if (premiumUser) {
    limit = 100;
} else {
    limit = 20;
}
// Từ đây limit đã được gán; không thể gán lại.
// premiumUser là biến boolean ở ngữ cảnh bao ngoài.
~~~

Điều kiện là cả hai nhánh không làm biến nhận hai lần gán trên cùng đường thực thi. Compiler áp dụng quy tắc **definite assignment**; không phải “cứ có chữ `final` thì phải có giá trị ngay ở dòng khai báo”.

### 3.2. `final` với tham chiếu đối tượng không làm đối tượng bất biến

~~~java
final StringBuilder builder = new StringBuilder("A");
builder.append("B");            // Hợp lệ: đổi trạng thái đối tượng.
System.out.println(builder);   // AB

// Không biên dịch:
// builder = new StringBuilder("C");
~~~

`final` bảo vệ **biến `builder` khỏi được gán sang tham chiếu khác**. Nó không cấm gọi phương thức làm đổi trạng thái của đối tượng được tham chiếu.

Tương tự, `final List<String> items = new ArrayList<>();` không tự biến `items` thành danh sách bất biến; `items.add("x")` vẫn có thể hợp lệ. Nếu cần tính bất biến, thiết kế API của đối tượng cũng phải bảo đảm điều đó (ví dụ dùng giá trị immutable, defensive copy hoặc các collection bất biến phù hợp).

Phân biệt:
- **`final` reference:** không thể gán lại biến tham chiếu.
- **Immutable object:** bản thân đối tượng không cho thay đổi trạng thái quan sát được sau khi tạo.
- **Unmodifiable view:** cấm thao tác sửa qua một view, nhưng dữ liệu nền có thể vẫn thay đổi bởi nơi khác tùy implementation.

Ba khái niệm này không đồng nghĩa.

### 3.3. `final` field, constructor và giá trị khởi tạo

~~~java
class Order {
    private final long id;

    Order(long id) {
        this.id = id;
    }

    long id() {
        return id;
    }
}
~~~

Một instance field `final` có thể được khởi tạo tại chỗ hoặc gán trong đường khởi tạo/constructor theo luật Java. Trường hợp trên, mỗi `Order` được tạo thành công có `id` được gán đúng một lần.

Với `static final`:

~~~java
class Defaults {
    static final int MAX_SIZE = 100;
    static final String APP_NAME = "Training";
}
~~~

`static final` là một cách thông dụng để diễn đạt các hằng/giá trị cấu hình cấp lớp. Nhưng **không phải mọi `static final` đều là compile-time constant**; ví dụ `static final StringBuilder BUFFER = new StringBuilder();` có tham chiếu final nhưng đối tượng vẫn mutable. Các biến hằng theo ngôn ngữ có tiêu chí riêng về kiểu primitive/`String` và constant expression.

### 3.4. `final` class và method

~~~java
final class ImmutableTag {
    private final String value;

    ImmutableTag(String value) {
        this.value = java.util.Objects.requireNonNull(value);
    }

    String value() {
        return value;
    }
}
~~~

`final class ImmutableTag` cấm viết lớp con `extends ImmutableTag`. Tuy nhiên, bản thân việc khai báo `final class` **không tự đảm bảo bất biến**: ví dụ một lớp final vẫn có thể chứa mutable field.

Phương thức final:

~~~java
class BaseService {
    public final String version() {
        return "1.0";
    }
}

class ChildService extends BaseService {
    // Không thể override method version().
}
~~~

Compiler ngăn lớp con định nghĩa một phương thức override `version()` theo chữ ký tương ứng. Khác với `finally`, `final` không thực hiện câu lệnh nào khi phương thức kết thúc.

## 4. Ví dụ Java 17+ hoàn chỉnh kết hợp `final` và `finally`

Tạo tệp `FinalAndFinallyDemo.java`:

~~~java
public class FinalAndFinallyDemo {
    static final class Receipt {
        private final int id;
        private final StringBuilder notes = new StringBuilder();

        Receipt(int id) {
            this.id = id;
        }

        final String label() {
            return "R-" + id;
        }

        void append(String text) {
            notes.append(text);
        }

        String notes() {
            return notes.toString();
        }
    }

    static int process(boolean fail) {
        try {
            System.out.println("Trong try");
            if (fail) {
                throw new IllegalStateException("xử lý lỗi");
            }
            return 7;
        } catch (IllegalStateException ex) {
            System.out.println("Trong catch: " + ex.getMessage());
            return -1;
        } finally {
            System.out.println("Trong finally");
        }
    }

    public static void main(String[] args) {
        final Receipt receipt = new Receipt(17);
        receipt.append("A");
        receipt.append("B");
        System.out.println("Receipt: " + receipt.label() + " / " + receipt.notes());
        System.out.println("Thành công: " + process(false));
        System.out.println("Thất bại: " + process(true));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 FinalAndFinallyDemo.java
java FinalAndFinallyDemo
~~~

Stdout chính xác:

~~~text
Receipt: R-17 / AB
Trong try
Trong finally
Thành công: 7
Trong try
Trong catch: xử lý lỗi
Trong finally
Thất bại: -1
~~~

**Giải thích:**

1. `Receipt` là lớp `final`, nên không có lớp con hợp lệ. Field `id` là `final`, được gán trong constructor, còn `label()` là phương thức `final`, không thể override.
2. Biến `receipt` trong `main` là **tham chiếu final**. Bạn không được gán `receipt = new Receipt(...)` ở phần còn lại của phương thức, nhưng vẫn được gọi `receipt.append(...)`, làm thay đổi đối tượng `StringBuilder` bên trong. Vì thế đầu ra là `AB`.
3. Khi `process(false)` chạy, `try` chuẩn bị trả về `7`. **`finally` thực thi trước khi lời gọi hoàn tất**, nên `Trong finally` xuất hiện trước `Thành công: 7`.
4. Khi `process(true)` chạy, `try` ném `IllegalStateException`, `catch` in thông báo và chuẩn bị trả về `-1`. Khối `finally` vẫn được thực hiện trước khi `main` in `Thất bại: -1`.
5. `final` trên biến/lớp/method **không quyết định** `finally` có chạy hay không. Hai từ khóa kiểm soát hai khía cạnh độc lập.

### Một lỗi biên dịch xác nhận ý nghĩa của `final`

Tệp `InvalidChild.java` sau đây **cố ý không biên dịch**:

~~~java
final class NonExtendable { }
class InvalidChild extends NonExtendable { }
~~~

Khi chạy `javac InvalidChild.java`, compiler báo lỗi chứa thông tin tương tự:

~~~text
error: cannot inherit from final NonExtendable
~~~

Lỗi được phát hiện **ở thời điểm biên dịch**; nó không liên quan tới ngoại lệ runtime, `try`, `catch` hay `finally`.

## 5. `finalize()`: phương thức cũ liên quan tới vòng đời đối tượng

`finalize()` là một **phương thức instance** được định nghĩa trên `java.lang.Object`. Trong cơ chế finalization lịch sử, một đối tượng có override `finalize()` có thể được hệ thống gọi khi đối tượng đã trở nên không còn được truy cập bình thường, trước khi bộ nhớ có thể được thu hồi theo quy tắc của cơ chế đó.

Chữ ký truyền thống:

~~~java
// Minh họa API cũ; KHÔNG khuyến nghị dùng.
@Override
protected void finalize() throws Throwable {
    try {
        // Logic finalizer cũ, không đáng tin để giải phóng tài nguyên.
    } finally {
        super.finalize();
    }
}
~~~

Đoạn trên là **phần thân phương thức minh họa đặt trong một lớp**, không phải chương trình chạy độc lập; có thể phát sinh cảnh báo deprecation tùy phiên bản JDK. Không viết mới kiểu thiết kế này.

Những điều cần biết:

- `finalize` là **tên phương thức**, khác với từ khóa `final` và `finally`. Có dấu `()`, có thể được override trong một số lớp cũ và có quy tắc truy cập/exception của phương thức.
- Finalization **không đồng nghĩa với garbage collection**: GC quyết định quản lý và thu hồi bộ nhớ; finalizer là cơ chế callback cũ được gắn với một số đối tượng khi đủ điều kiện.
- **Không có đảm bảo thời điểm thực hiện `finalize()`, cũng không có đảm bảo nó sẽ chạy trước khi chương trình kết thúc.** `System.gc()` chỉ là yêu cầu/hint và không biến finalization thành một lịch trình xác định.
- Một finalizer có thể không được gọi nếu đối tượng vẫn được tham chiếu, JVM dừng sớm hoặc hệ thống finalization bị vô hiệu hóa. Dựa vào nó để đóng file/socket/connection dễ làm rò rỉ tài nguyên.
- Ngoại lệ chưa bắt phát sinh trong finalizer không được xử lý như lỗi thông thường do lời gọi ứng dụng thực hiện; theo hợp đồng finalization lịch sử, nó không cung cấp một kênh báo lỗi đáng tin cho luồng gọi. Vì vậy finalizer không phải nơi thích hợp để xử lý lỗi nghiệp vụ.
- Finalizers có rủi ro về hiệu năng, bảo mật, hành vi khó dự đoán và **object resurrection** (đối tượng được đưa trở lại trạng thái có thể truy cập). Điều này làm vòng đời và điều kiện race trở nên phức tạp.

**Trạng thái theo phiên bản Java:** `Object.finalize()` đã được đánh dấu deprecated từ Java 9. JEP 421 đưa ra việc **deprecate finalization for removal trong JDK 18** và định hướng loại bỏ cơ chế này trong tương lai. Vì bài học ưu tiên Java 17+, cần phân biệt **deprecated ở Java 17** với mốc **for-removal gắn với JDK 18**. Dù đang dùng phiên bản vẫn cung cấp API, lập trình viên nên tránh viết code mới dựa vào finalization.

Không nên gọi trực tiếp `someObject.finalize()` để “kích hoạt GC”. Nếu một phương thức có thể truy cập được và được gọi thủ công, đó chỉ là một lời gọi phương thức Java bình thường trong ngữ cảnh ấy; nó **không buộc GC thu hồi đối tượng** và có thể làm cleanup bị thực thi sai thời điểm/nhiều lần.

## 6. Vì sao không dùng `finalize()` để thay `finally`?

Giả sử code cần mở một tài nguyên hệ điều hành như tệp, socket hoặc kết nối cơ sở dữ liệu. Điều bạn cần là **đóng tài nguyên khi đã dùng xong**, không phải đợi khi đối tượng có thể được GC thu hồi.

| Vấn đề | `finally` / `try-with-resources` | `finalize()` |
| --- | --- | --- |
| Thời điểm liên quan | Khi rời phạm vi `try` đã quy định | Không xác định; tùy điều kiện vòng đời đối tượng và runtime |
| Có nằm trong đường thực thi do code gọi chủ động không? | Có | Không phải đường điều khiển thông thường của caller |
| Có thể lập luận thứ tự các bước cleanup? | Có, theo quy tắc `try` và resource close | Không đáng tin để quản lý thứ tự tài nguyên |
| Có phù hợp cho `AutoCloseable`? | Ưu tiên `try-with-resources` | Không |
| Nếu cleanup thất bại? | Có thể bắt/lan truyền; `try-with-resources` hỗ trợ suppressed exceptions | Không có đường xử lý lỗi đáng tin cho caller |
| Khuyến nghị cho code mới | Có | Không |

`finally` chạy khi rời cấu trúc `try` (theo các điều kiện thực thi đã nêu). Finalization không gắn với thời điểm một lời gọi phương thức dùng xong tài nguyên. Mặc dù tên giống nhau, **chúng không có quan hệ kế thừa chức năng và không thể dùng thay thế**.

Một điểm thực tế: **`finally` viết thủ công cũng không phải luôn là cách tốt nhất để đóng tài nguyên**. Đối với `AutoCloseable`, ưu tiên `try-with-resources` vì Java tự đóng và có cơ chế giữ suppressed exception khi thân `try` và `close()` đều ném lỗi.

## 7. Ví dụ Java 17+: `try-with-resources` thay vì finalizer

Tạo tệp `FinallyResourceDemo.java`:

~~~java
public class FinallyResourceDemo {
    static class Resource implements AutoCloseable {
        Resource() {
            System.out.println("Mở tài nguyên");
        }

        void work(boolean fail) {
            System.out.println("Đang xử lý");
            if (fail) {
                throw new IllegalStateException("lỗi tác vụ");
            }
        }

        @Override
        public void close() {
            System.out.println("Đóng tài nguyên");
        }
    }

    static void run(boolean fail) {
        try (Resource resource = new Resource()) {
            resource.work(fail);
        } catch (IllegalStateException ex) {
            System.out.println("Bắt lỗi: " + ex.getMessage());
        } finally {
            System.out.println("Finally của run");
        }
    }

    public static void main(String[] args) {
        run(false);
        run(true);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 FinallyResourceDemo.java
java FinallyResourceDemo
~~~

Stdout chính xác:

~~~text
Mở tài nguyên
Đang xử lý
Đóng tài nguyên
Finally của run
Mở tài nguyên
Đang xử lý
Đóng tài nguyên
Bắt lỗi: lỗi tác vụ
Finally của run
~~~

**Diễn giải:**

1. `Resource implements AutoCloseable` khai báo cách đóng tài nguyên bằng `close()`. Đây là một hợp đồng **chủ động và dự đoán được** ở cuối phạm vi sử dụng tài nguyên.
2. Lượt `run(false)`: `work` thành công → `close()` được gọi tự động → không có `catch` → `finally` in thông báo kết thúc.
3. Lượt `run(true)`: `work` ném `IllegalStateException` → tài nguyên được đóng **trước khi** `catch` của cấu trúc chạy → `catch` in thông báo → `finally` in dòng cuối.
4. Chương trình không cần override `finalize()`, không cần `System.gc()`, không phụ thuộc việc GC có thu hồi đối tượng trong khoảng thời gian nào.

Đối với code sản phẩm, `AutoCloseable` và `try-with-resources` là hướng chính để quản lý tài nguyên phải đóng. Nếu muốn tùy biến hành vi dọn dẹp, đặt nó vào `close()` có hợp đồng rõ ràng thay vì trong finalizer.

## 8. Các tình huống dễ nhầm về `finally`

### 8.1. `return` trong `try` không bỏ qua `finally`

~~~java
// Đoạn minh họa nằm trong lớp.
static int compute() {
    int number = 5;
    try {
        return number;
    } finally {
        number = 9;
    }
}
~~~

Phương thức trả **5**, nếu `finally` hoàn tất bình thường. Biểu thức `return number` được đánh giá trước khi `finally` thực thi; việc gán biến cục bộ trong `finally` không tính lại kết quả. Nếu kiểu trả về là tham chiếu tới một đối tượng mutable, thay đổi chính đối tượng trong `finally` có thể được người gọi quan sát — phải phân biệt với gán lại biến tham chiếu.

### 8.2. `return` hoặc `throw` trong `finally` có thể che lỗi

~~~java
// Ví dụ cố ý cho thấy anti-pattern.
static int unsafe() {
    try {
        throw new IllegalStateException("lỗi ban đầu");
    } finally {
        return 42; // Che lỗi ban đầu; KHÔNG nên viết như vậy.
    }
}
~~~

Nếu gọi `unsafe()`, nó trả về **42** thay vì ném `IllegalStateException`. Trường hợp `finally` ném ngoại lệ khác cũng có thể thay thế ngoại lệ đang lan truyền. Tránh `return` trong `finally`; khi cleanup có thể lỗi, cân nhắc quản lý bằng `try-with-resources` hoặc một chính sách bảo toàn lỗi chính thích hợp.

### 8.3. `final` không bảo vệ khỏi ngoại lệ

~~~java
final String text = null;
System.out.println(text.length()); // Vẫn ném NullPointerException.
~~~

`final` chỉ ngăn gán lại biến `text`. Nó không bảo đảm khác `null`, không thay việc kiểm tra đầu vào, không sinh `catch` và không giải phóng tài nguyên. Cũng vậy, một lớp `final` có thể ném bất kỳ ngoại lệ hợp lệ nào mà code bên trong lớp tạo ra.

### 8.4. Có thể dùng `final` trong `catch`, nhưng đó không phải `finally`

~~~java
try {
    throw new IllegalArgumentException("sai");
} catch (final IllegalArgumentException ex) {
    System.out.println(ex.getMessage());
}
~~~

Cách viết hợp lệ: biến tham số `ex` không được gán lại, song vẫn có thể dùng để đọc thông tin lỗi. Đây là cú pháp `final` trên tham số, **không phải khối `finally`**. Với multi-catch, tham số được xem là final theo quy tắc Java ngay cả khi không ghi từ khóa đó.

### 8.5. `finally` không làm thay đổi quan hệ `final` / override

Nếu bạn đánh dấu phương thức `final`, các lớp con không được override phương thức đó. Việc thêm `try/finally` bên trong phương thức không làm phương thức mất trạng thái `final`. Ngược lại, không có modifier `final` nào tự làm các khối `finally` bên trong phương thức hoạt động khác đi.

## 9. `final` và đa luồng: cần phân biệt với cleanup

Trong Java Memory Model, các `final` field có một số quy tắc đặc biệt giúp **an toàn khởi tạo** khi đối tượng được xây dựng đúng cách và tham chiếu không bị công bố sai trong lúc xây dựng. Tuy nhiên, **`final` không tự làm mọi phương thức thread-safe**.

Ví dụ, một field `final StringBuilder buffer` đảm bảo bản thân biến field không đổi tham chiếu sau khi gán, nhưng `StringBuilder` vẫn mutable và không được thiết kế cho truy cập cập nhật đồng thời không đồng bộ. Nếu nhiều luồng cùng gọi `buffer.append`, vẫn cần chiến lược đồng bộ hoặc dùng cấu trúc dữ liệu khác.

Điều này càng làm rõ ba tên:

- `final` liên quan đến ràng buộc khai báo và, với final fields, một số quy tắc hiển thị dữ liệu đã khởi tạo.
- `finally` liên quan đến thời điểm hoàn tất một vùng thực thi.
- `finalize()` là cơ chế vòng đời cũ và còn khó dự đoán hơn khi có nhiều luồng; nó không phải công cụ đồng bộ.

Không nên suy luận từ chữ *final* rằng dữ liệu, thao tác tài nguyên hay vòng đời đối tượng đã “an toàn cuối cùng” ở mọi nghĩa.

## 10. Khi cần dọn dẹp tài nguyên, nên chọn gì?

**Đối với tài nguyên đóng được:**

~~~java
// Đoạn minh họa trong phương thức có throws IOException.
try (java.io.BufferedReader reader =
             java.nio.file.Files.newBufferedReader(path)) {
    return reader.readLine();
}
~~~

Đây là code fragment; `path` là một `Path` ở ngữ cảnh chứa và phương thức phải xử lý hoặc khai báo `IOException`. `try-with-resources` đóng `reader` khi rời khối, kể cả khi đọc thất bại. Cơ chế này được thiết kế riêng cho tài nguyên `AutoCloseable`.

**Đối với công việc cuối không thuộc `AutoCloseable`:**

~~~java
// Đoạn minh họa trong phương thức.
lock.lock();
try {
    updateSharedState();
} finally {
    lock.unlock();
}
~~~

Đoạn trên giả định `lock` là đối tượng khóa hợp lệ và `updateSharedState()` đã được khai báo; nguyên tắc là khóa được thả ở `finally` khi phần thân hoàn tất hoặc ném lỗi. Với `Lock` trong `java.util.concurrent.locks`, đây là mẫu thường gặp. `unlock()` phải được gọi bởi luồng đang giữ khóa và chỉ sau khi `lock.lock()` thành công.

**Đối với cơ chế dự phòng quản lý tài nguyên cực kỳ chuyên biệt:** Java có `Cleaner` và các API tham chiếu của `java.lang.ref`. Chúng chỉ nên dùng nếu bạn hiểu vòng đời, tính bất định của cleanup và không thể biểu diễn trách nhiệm đóng trực tiếp. `Cleaner` không thay thế `close()` đúng lúc, và không nên tạo ấn tượng rằng nó cung cấp đảm bảo thời gian thực hoặc sẽ chạy trước khi JVM dừng.

**Không chọn `finalize()` cho code mới.** Nếu gặp finalizer trong hệ thống cũ, xác định tài nguyên nó đang quản lý, chuyển trách nhiệm cleanup sang cơ chế chủ động phù hợp, kiểm thử cả đường thành công và thất bại, rồi loại bỏ dần sự phụ thuộc vào finalization.

## 11. Phân biệt về cú pháp: ba tên không thay thế được

| Cách viết | Ý nghĩa/kết quả |
| --- | --- |
| `try { run(); } finally { cleanup(); }` | Hợp lệ: `finally` là khối thuộc cấu trúc `try`. |
| `final int n = 1;` | Hợp lệ: biến `n` không được gán lại. |
| `final class A { }` | Hợp lệ: lớp `A` không được kế thừa. |
| `final void m() { }` | Hợp lệ trong khai báo phương thức của lớp, tùy bối cảnh: không cho override. |
| `protected void finalize() throws Throwable { ... }` | Cú pháp override finalizer lịch sử, nhưng deprecated/không khuyến nghị. |
| `finally int x = 1;` | Sai: `finally` không phải modifier biến. |
| `try { run(); } final { cleanup(); }` | Sai: `final` không mở khối cleanup. |
| `finalize { cleanup(); }` | Sai: `finalize` không phải từ khóa của cấu trúc `try`. |
| `object.finally()` | Sai: `finally` là từ khóa, không thể dùng làm định danh phương thức. |
| `finalize()` thay cho `resource.close()` | Không phải cơ chế quản lý tài nguyên đáng tin và không bảo đảm GC đóng đúng lúc. |

Các đoạn có `...` trong bảng chỉ là cú pháp gợi ý, **không phải tệp Java độc lập có thể biên dịch**. Chỉ các ví dụ được ghi tên tệp và lệnh chạy là chương trình hoàn chỉnh.

## 12. Lỗi phổ biến khi học và sử dụng

| Nhầm lẫn | Vì sao sai? | Điều nên làm |
| --- | --- | --- |
| `final` là dạng viết ngắn của `finally` | Một từ khóa hạn chế gán/kế thừa, một từ khóa mở khối thuộc `try`. | Xem ngữ cảnh khai báo hay luồng xử lý ngoại lệ. |
| `finally` chỉ chạy khi có ngoại lệ | Khi `try` thành công, `finally` vẫn chạy trên đường rời cấu trúc. | Kiểm thử cả đường bình thường lẫn lỗi. |
| `finally` tự `catch` mọi lỗi | Lỗi chưa xử lý tiếp tục lan truyền sau `finally`, nếu `finally` hoàn tất bình thường. | Dùng `catch` khi cần xử lý lỗi. |
| Biến `final` tham chiếu tới object thì object không thể thay đổi | `final` ngăn gán lại biến, không tự cấm mutation của object. | Dùng đối tượng bất biến/defensive copy khi cần. |
| Lớp `final` chắc chắn immutable | Lớp không thể bị kế thừa vẫn có thể chứa trạng thái mutable. | Kiểm tra toàn bộ field và phương thức. |
| Phương thức `final` không thể ném ngoại lệ | Hạn chế override không liên quan việc ném `Throwable`. | Phân biệt hợp đồng phương thức với modifier. |
| `finalize()` được gọi ngay khi biến ra khỏi scope | Scope của biến không xác định được thời điểm hoặc khả năng GC/finalization. | Đóng tài nguyên chủ động. |
| `System.gc()` bảo đảm finalizer chạy | GC không phải lệnh hẹn giờ đáng tin cho finalization. | Không phụ thuộc finalizer để cleanup. |
| Dùng finalizer để đóng stream/connection | Thời điểm không xác định, dễ rò rỉ tài nguyên hoặc hành vi khó kiểm soát. | Dùng `AutoCloseable` và `try-with-resources`. |
| `return` trong `finally` vô hại | Có thể che `return` hoặc ngoại lệ đang chờ. | Tránh `return`/`throw` tùy tiện trong `finally`. |
| Dùng `finally` đóng tài nguyên nhưng bỏ qua lỗi từ `close()` | Ngoại lệ đóng có thể che lỗi chính. | Ưu tiên `try-with-resources` để giữ suppressed exception. |
| Gọi `finalize()` thủ công tương đương chạy GC | Chỉ là lời gọi phương thức (khi hợp lệ về truy cập), không ép GC thu hồi đối tượng. | Thiết kế API `close()` rõ ràng. |

## 13. Câu hỏi tự kiểm tra

1. **`finally` là từ khóa hay phương thức?** — Từ khóa mở khối thuộc cấu trúc `try`, thường để thực hiện công việc khi rời cấu trúc.
2. **`final` trên biến tham chiếu có ngăn gọi phương thức thay đổi đối tượng không?** — Không. Nó ngăn gán lại tham chiếu của biến.
3. **Có thể viết `class Child extends FinalClass` khi `FinalClass` được khai báo `final` không?** — Không, compiler từ chối.
4. **Một phương thức `final` có được chứa `try/catch/finally` không?** — Có; `final` không giới hạn cấu trúc xử lý ngoại lệ bên trong.
5. **`try` không ném lỗi thì `finally` có chạy không?** — Có, khi rời cấu trúc theo đường bình thường.
6. **Nếu `try` ném lỗi chưa được bắt, `finally` có xử lý xong lỗi không?** — Không; nó chạy rồi lỗi tiếp tục lan truyền nếu `finally` hoàn tất bình thường.
7. **`finalize()` có chắc chắn được gọi trước khi tiến trình kết thúc không?** — Không; không có bảo đảm đó.
8. **`Object.finalize()` đã deprecated ở mốc nào?** — Deprecated từ Java 9; JEP 421 đưa trạng thái deprecate-for-removal ở JDK 18.
9. **Muốn đóng một `BufferedReader`, nên ưu tiên `finalize()`, `final` hay `try-with-resources`?** — `try-with-resources` và `AutoCloseable`; hai lựa chọn kia không giải quyết việc đóng đúng lúc.
10. **Nếu `finally` có `return` và `try` đang ném lỗi, điều gì có thể xảy ra?** — `return` có thể thay thế đường thoát bằng ngoại lệ, khiến lỗi gốc bị che.

## 14. Tài liệu tham khảo chính thức

- [Java Language Specification 17 — §14.20, The `try` Statement](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html#jls-14.20): quy tắc thực hiện `try`, `catch` và `finally`.
- [Java Language Specification 17 — §4.12.4, `final` Variables](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.12.4): biến `final`, definite assignment.
- [Java Language Specification 17 — §8.1.1.2, `final` Classes](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.1.1.2): lớp `final` không thể có lớp con.
- [Java Language Specification 17 — §8.4.3.3, `final` Methods](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.3.3): phương thức `final` và ghi đè.
- [Java SE 17 API — `java.lang.Object.finalize()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html#finalize()): hợp đồng và tình trạng deprecated của phương thức.
- [OpenJDK JEP 421 — Deprecate Finalization for Removal](https://openjdk.org/jeps/421): bối cảnh deprecate-for-removal từ JDK 18, rủi ro finalization và hướng thay thế.
- [Java SE 17 API — `AutoCloseable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/AutoCloseable.html): cơ chế đóng tài nguyên chủ động dùng với `try-with-resources`.

**Kết luận:** `finally` là khối thực thi khi rời `try`; `final` là modifier được compiler kiểm tra đối với biến, field, lớp và phương thức; `finalize()` là phương thức thuộc cơ chế finalization cũ, không nên dùng để dọn dẹp tài nguyên. Khi cần quản lý tài nguyên, chọn `try-with-resources`/`close()`; khi cần logic cuối cho một vùng thực thi, dùng `finally`; khi cần ràng buộc khai báo, dùng `final`.


</details>

- [Quay lại đầu trang](#back-to-top)
