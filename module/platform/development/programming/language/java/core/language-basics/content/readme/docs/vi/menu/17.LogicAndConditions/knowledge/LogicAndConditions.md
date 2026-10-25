<a id="back-to-top"></a>

# Logic và điều kiện

## Menu
- [&amp;&amp;, || và ! trong biểu thức boolean](#logic-and-conditions-001)
- [Đánh giá ngắn mạch và những biểu thức không được chạy](#logic-and-conditions-002)
- [Dùng ngắn mạch để kiểm tra null và giới hạn mảng](#logic-and-conditions-003)
- [&amp;, | và ^ khi áp dụng lên boolean](#logic-and-conditions-004)
- [Phân biệt &amp;&amp; với &amp; và || với |](#logic-and-conditions-005)
- [Định luật De Morgan và đơn giản hóa điều kiện](#logic-and-conditions-006)
- [Toán tử ba ngôi ?: và chỉ đánh giá nhánh được chọn](#logic-and-conditions-007)
- [Kiểu kết quả của biểu thức điều kiện và chuyển kiểu có thể xảy ra](#logic-and-conditions-008)
- [Tránh lồng quá nhiều toán tử ba ngôi](#logic-and-conditions-009)

## <a id="logic-and-conditions-001">&amp;&amp;, || và ! trong biểu thức boolean</a>

<details>
<summary>Xem chi tiết</summary>

# `&&`, `||` và `!` trong biểu thức boolean — Java 17+

Trong Java, ba toán tử logic này kết hợp hoặc đảo các **biểu thức `boolean`** để tạo ra kết quả `boolean`:

- `a && b` (*conditional AND*): đúng khi **cả hai** điều kiện đúng; nếu `a` sai thì **không đánh giá `b`**.
- `a || b` (*conditional OR*): đúng khi **ít nhất một** điều kiện đúng; nếu `a` đúng thì **không đánh giá `b`**.
- `!a` (*logical complement/NOT*): đảo giá trị logic, `true` thành `false` và ngược lại.

~~~java
boolean loggedIn = true;
boolean isAdmin = false;

boolean canManage = loggedIn && isAdmin; // false.
boolean canView = loggedIn || isAdmin;   // true.
boolean guest = !loggedIn;              // false.
~~~

**Quan trọng:** `&&` và `||` là toán tử **ngắn mạch** (*short-circuit*). Vế phải chỉ chạy nếu kết quả chưa xác định được từ vế trái. Điều này ảnh hưởng cả **tính đúng đắn** lẫn **tác dụng phụ** trong biểu thức.

## 1. Bảng chân trị

| `a` | `b` | `a && b` | `a || b` | `!a` |
| --- | --- | --- | --- | --- |
| false | false | false | false | true |
| false | true | false | true | true |
| true | false | false | true | false |
| true | true | true | true | false |

Cách nhớ:

- **AND `&&`**: “tất cả điều kiện đều phải đúng”.
- **OR `||`**: “chỉ cần một điều kiện đúng”.
- **NOT `!`**: “điều kiện không đúng”; `!!a` có cùng giá trị boolean với `a` khi được đánh giá bình thường.

Ví dụ xét quyền truy cập:

~~~java
boolean hasPermission = true;
boolean suspended = false;
boolean allowed = hasPermission && !suspended; // true.
~~~

Chủ thể phải có quyền **và** không bị đình chỉ. `!suspended` áp dụng cho riêng `suspended`; việc đặt tên biến rõ nghĩa giúp tránh phủ định nhầm.

## 2. Chương trình Java 17+ runnable

Tạo tệp UTF-8 tên **`BooleanLogicDemo.java`**:

~~~java
public class BooleanLogicDemo {
    static boolean mark(String name, boolean result) {
        System.out.println("eval=" + name);
        return result;
    }

    public static void main(String[] args) {
        boolean hasTicket = true;
        boolean adult = false;
        boolean blocked = false;

        System.out.println("and=" + (hasTicket && adult));
        System.out.println("or=" + (hasTicket || adult));
        System.out.println("not=" + (!blocked));
        System.out.println("combined="
                + (hasTicket && (adult || !blocked)));

        System.out.println("== truth table ==");
        System.out.println("FF=" + (false && false)
                + "," + (false || false));
        System.out.println("FT=" + (false && true)
                + "," + (false || true));
        System.out.println("TF=" + (true && false)
                + "," + (true || false));
        System.out.println("TT=" + (true && true)
                + "," + (true || true));

        System.out.println("== short circuit ==");
        boolean shortAnd = mark("and-left", false)
                && mark("and-right-skipped", true);
        System.out.println("shortAnd=" + shortAnd);

        boolean shortOr = mark("or-left", true)
                || mark("or-right-skipped", false);
        System.out.println("shortOr=" + shortOr);

        System.out.println("== eager alternatives ==");
        boolean eagerAnd = mark("eager-and-left", false)
                & mark("eager-and-right", true);
        System.out.println("eagerAnd=" + eagerAnd);

        boolean eagerOr = mark("eager-or-left", true)
                | mark("eager-or-right", false);
        System.out.println("eagerOr=" + eagerOr);

        System.out.println("== grouping ==");
        System.out.println("precedence="
                + (true || false && false));
        System.out.println("parenthesized="
                + ((true || false) && false));
        System.out.println("notAnd="
                + (!(true && false)));
        System.out.println("deMorganAnd="
                + ((!true) || (!false)));
        System.out.println("notOr="
                + (!(true || false)));
        System.out.println("deMorganOr="
                + ((!true) && (!false)));

        System.out.println("== guards ==");
        String name = null;
        boolean nonBlank = name != null && !name.isBlank();
        System.out.println("nonBlank=" + nonBlank);

        int divisor = 0;
        boolean safeDivision =
                divisor != 0 && 12 / divisor > 2;
        System.out.println("safeDivision=" + safeDivision);

        Boolean preference = null;
        System.out.println("nullableSafe="
                + Boolean.TRUE.equals(preference));
        try {
            boolean unsafe = !preference;
            System.out.println("unexpected=" + unsafe);
        } catch (NullPointerException ex) {
            System.out.println("nullableNot=NullPointerException");
        }

        boolean access = hasTicket && (!blocked || adult);
        if (access) {
            System.out.println("access=allowed");
        } else {
            System.out.println("access=denied");
        }

        System.out.println("doubleNot=" + (!!hasTicket));
    }
}
~~~

Dùng **JDK 17 trở lên**. Mở PowerShell hoặc Bash tại thư mục chứa file:

~~~bash
java -version
javac -version
javac -encoding UTF-8 BooleanLogicDemo.java
java BooleanLogicDemo
~~~

**Exact expected stdout** của riêng lệnh `java BooleanLogicDemo`:

~~~text
and=false
or=true
not=true
combined=true
== truth table ==
FF=false,false
FT=false,true
TF=false,true
TT=true,true
== short circuit ==
eval=and-left
shortAnd=false
eval=or-left
shortOr=true
== eager alternatives ==
eval=eager-and-left
eval=eager-and-right
eagerAnd=false
eval=eager-or-left
eval=eager-or-right
eagerOr=true
== grouping ==
precedence=true
parenthesized=false
notAnd=true
deMorganAnd=true
notOr=false
deMorganOr=false
== guards ==
nonBlank=false
safeDivision=false
nullableSafe=false
nullableNot=NullPointerException
access=allowed
doubleNot=true
~~~

Hai lệnh `java -version` và `javac -version` hiển thị thông tin JDK tùy môi trường, không thuộc khối stdout cố định trên. Các phương thức `mark(...)` chỉ được sử dụng để in dấu vết và quan sát thứ tự thực thi, **không phải** phong cách khuyến nghị cho điều kiện nghiệp vụ.

## 3. Ngắn mạch: tại sao có lời gọi bị bỏ qua?

### `&&`: bên trái false thì không cần xét bên phải

~~~java
boolean result = mark("A", false) && mark("B", true);
~~~

Lời gọi `mark("A", false)` trả false nên `result` chắc chắn false. Java **không gọi** `mark("B", true)`. Dòng `eval=B` không xuất hiện.

### `||`: bên trái true thì không cần xét bên phải

~~~java
boolean result = mark("A", true) || mark("B", false);
~~~

Lời gọi `mark("A", true)` đã xác định kết quả true; `mark("B", false)` không được gọi.

Nếu vế phải được đánh giá, Java đánh giá **vế trái trước rồi mới đến vế phải**, không hoán đổi hai vế tùy ý. Với chuỗi `a && b && c`, các toán hạng được kiểm tra theo thứ tự từ trái sang phải và dừng tại false đầu tiên; chuỗi `a || b || c` dừng tại true đầu tiên. Quy tắc này đặc biệt quan trọng nếu biểu thức con có thể ném ngoại lệ hoặc tác động tới trạng thái.

**Không nên dựa vào tác dụng phụ của vế phải** để cập nhật dữ liệu bắt buộc:

~~~java
// Khong nen: updateCounter() co the khong duoc goi.
// boolean ok = condition || updateCounter();
~~~

Nếu cập nhật là bắt buộc, hãy đặt thành câu lệnh độc lập; dùng `&&`/`||` để mô tả điều kiện chứ không để giấu thao tác nghiệp vụ.

## 4. `&&` so với `&`, `||` so với `|`

Java còn có toán tử `&` và `|` áp dụng cho `boolean`. Kết quả logic **giống bảng chân trị** AND/OR, nhưng khác ở **việc đánh giá toán hạng**:

| Với toán hạng boolean | Kết quả logic | Đánh giá vế phải nếu vế trái đã đủ quyết định? |
| --- | --- | --- |
| `a && b` | AND | **Không** nếu `a == false` |
| `a & b` | AND | **Có** |
| `a || b` | OR | **Không** nếu `a == true` |
| `a | b` | OR | **Có** |

Trong demo, `eagerAnd` và `eagerOr` đều in hai dòng `eval=...`; các phép ngắn mạch chỉ in vế trái.

`&` và `|` cũng có nghĩa **phép toán bit** với các kiểu số nguyên, không giống `&&` và `||`: toán tử logic ngắn mạch **chỉ nhận boolean**, không nhận số nguyên để làm “truthy/falsy”.

Nếu không có lý do rõ ràng để buộc cả hai điều kiện boolean thực thi, hãy ưu tiên `&&` và `||` vì chúng phản ánh điều kiện kết hợp và tránh thao tác không cần thiết.

## 5. Dùng điều kiện ngắn mạch để bảo vệ thao tác nguy hiểm

### Tránh truy cập `null`

~~~java
String name = null;
boolean hasText = name != null && !name.isBlank(); // false.
~~~

Vế trái false khiến lời gọi `name.isBlank()` **bị bỏ qua**, nên không gây `NullPointerException`. Đảo thứ tự là không an toàn:

~~~java
// Sai khi name == null:
// boolean hasText = !name.isBlank() && name != null;
~~~

Điều kiện gác cổng (*guard condition*) phải được đặt **trước** biểu thức phụ thuộc vào nó.

### Tránh chia số nguyên cho 0

~~~java
int divisor = 0;
boolean valid = divisor != 0 && (12 / divisor > 2); // false.
~~~

Nếu mẫu số bằng 0, Java không đánh giá phép chia phía sau. Để mã dễ đọc, có thể thêm ngoặc quanh phép so sánh bên phải.

Đây là cách bảo vệ phù hợp với **biến cục bộ không bị thay đổi bất ngờ giữa hai phép kiểm tra**. Khi trạng thái được chia sẻ giữa nhiều luồng, cần thiết kế đồng bộ/đọc snapshot phù hợp; không mặc nhiên coi hai lần đọc một trường dùng chung là một giao dịch nguyên tử.

### Xử lý `Boolean` có thể null

~~~java
Boolean enabled = null;
// boolean disabled = !enabled; // Nem NullPointerException do unboxing.
boolean enabledSafely = Boolean.TRUE.equals(enabled); // false.
~~~

`!` yêu cầu `boolean`; nếu vế toán hạng là `Boolean` thì Java unbox. `null` không thể unbox thành boolean và sẽ ném `NullPointerException`. `Boolean.TRUE.equals(value)` là cách kiểm tra rõ ý khi chỉ muốn đúng với `Boolean.TRUE`.

## 6. Độ ưu tiên: `!` trước `&&` trước `||`

Java nhóm ba toán tử theo thứ tự độ ưu tiên:

1. `!`: phủ định.
2. `&&`: AND ngắn mạch.
3. `||`: OR ngắn mạch.

~~~java
boolean result = true || false && false; // true.
// Nhom nhu: true || (false && false).

boolean different = (true || false) && false; // false.
~~~

Dấu ngoặc trong biểu thức phức tạp thường đáng dùng ngay cả khi compiler đã biết độ ưu tiên, vì chúng làm **ý nghĩa điều kiện rõ hơn cho người đọc**.

~~~java
boolean canEnter = hasTicket && (!blocked || adult);
~~~

Cách viết này thể hiện yêu cầu có vé **và** (không bị chặn **hoặc** đủ tuổi). Nếu bỏ ngoặc, `hasTicket && !blocked || adult` được nhóm khác: người đủ tuổi có thể vượt qua điều kiện vé, khiến ý đồ phân quyền thay đổi.

**Độ ưu tiên quyết định cách nhóm**, không tự thay đổi thứ tự chạy thành “tính phép trong ngoặc ưu tiên trước mọi toán hạng ngoài ngoặc”. Trong `a || (b && c)`, Java vẫn đánh giá `a` trước; chỉ khi `a` false mới cần đánh giá `b && c`.

## 7. Phủ định `!` và quy tắc De Morgan

`!` đảo boolean:

~~~java
boolean ready = true;
boolean notReady = !ready; // false.
boolean stillReady = !!ready; // true.
~~~

Khi phủ định biểu thức ghép, có hai quy tắc quan trọng:

| Biểu thức | Dạng tương đương |
| --- | --- |
| `!(a && b)` | `!a || !b` |
| `!(a || b)` | `!a && !b` |

Các công thức áp dụng với biểu thức boolean; chúng cũng giữ quy luật ngắn mạch và thứ tự đánh giá trái-sang-phải khi biến đổi đúng. Ví dụ:

~~~java
boolean isAdult = false;
boolean hasGuardian = true;

boolean rejected = !(isAdult || hasGuardian); // false.
boolean equivalent = !isAdult && !hasGuardian; // false.
~~~

**Lỗi phổ biến:** `!(a && b)` **không** tương đương `!a && !b`. Khi phủ định toàn bộ điều kiện, phải đổi AND ↔ OR và phủ định **từng toán hạng**.

Ngoài ra, `!` khác `~`: `~` đảo bit số nguyên, còn `!` phủ định boolean. `!0` hoặc `!1` **không hợp lệ** trong Java; Java không tự coi số 0 là false và số khác 0 là true.

## 8. Kết hợp điều kiện trong `if` và `while`

~~~java
int age = 20;
boolean hasIdentityCard = true;

if (age >= 18 && hasIdentityCard) {
    System.out.println("eligible");
}
~~~

Dùng `&&` để diễn đạt các điều kiện bắt buộc cùng đúng; dùng `||` cho các lựa chọn thay thế; dùng `!` để phủ định một điều kiện:

~~~java
boolean expired = false;
boolean locked = false;
boolean needsRenewal = expired || locked; // false.
boolean usable = !needsRenewal;          // true.
~~~

Cũng có thể dùng trong `while`, nhưng phải bảo đảm điều kiện cuối cùng có khả năng chuyển thành false nếu cần kết thúc vòng lặp; ngắn mạch **không tự bảo đảm** vòng lặp dừng.

Khi điều kiện quá dài, nên tách các phần thành biến tên rõ ràng như `hasValidSession`, `hasPermission`, `isBlocked` để hạn chế phủ định chồng nhau.

## 9. Các lỗi thường gặp

| Sai lầm | Cách hiểu đúng |
| --- | --- |
| “`&&` và `&` với boolean hoàn toàn giống nhau.” | Kết quả logic thường giống, nhưng `&` **luôn đánh giá** vế phải nếu không có ngoại lệ |
| “`||` luôn đánh giá hai vế.” | Không, vế phải bị bỏ qua khi vế trái true |
| “`!` đảo một số như `!0`.” | Sai kiểu; `!` yêu cầu boolean |
| “`!a && !b` luôn bằng `!(a && b)`.” | Sai; dạng đúng là `!a || !b` |
| “`true || false && false` giống `(true || false) && false`.” | Không; kết quả lần lượt true và false |
| “`name != null && name.isBlank()` vẫn gọi phương thức khi null.” | Không; vế phải bị bỏ qua nếu null |
| “`Boolean` null tự được coi là false.” | Không; unboxing null ném `NullPointerException` |
| “Có thể dựa vào vế phải `&&` để luôn cập nhật bộ đếm.” | Không; vế phải có thể không được đánh giá |
| “Thứ tự các điều kiện AND không quan trọng.” | Khi có exception/tác dụng phụ, thứ tự và guard rất quan trọng |

## 10. Bài tập tự kiểm tra

1. `true && false`, `true || false`, `!true` cho gì?  
   *Đáp án:* false, true, false.
2. `true || false && false` bằng gì? Thêm ngoặc `(true || false) && false` thì sao?  
   *Đáp án:* true và false.
3. Trong `false && expensive()`, phương thức `expensive()` có được gọi không?  
   *Đáp án:* Không.
4. Trong `true | check()` với hai toán hạng boolean, `check()` có được gọi không?  
   *Đáp án:* Có, nếu việc đánh giá tới vế phải bình thường.
5. Với `String s = null`, `s != null && !s.isBlank()` có gây `NullPointerException` không?  
   *Đáp án:* Không; ngắn mạch bỏ qua vế phải.
6. Viết lại `!(isAdmin || isOwner)` theo De Morgan.  
   *Đáp án:* `!isAdmin && !isOwner`.
7. `Boolean flag = null; boolean b = !flag;` gây gì?  
   *Đáp án:* `NullPointerException` khi unbox.
8. Cần đủ tuổi **và** (có vé **hoặc** có giấy mời). Viết điều kiện.  
   *Đáp án:* `isAdult && (hasTicket || hasInvitation)`.

## Checklist

- [ ] Phân biệt `&&` (AND), `||` (OR), `!` (NOT), tất cả đều làm việc với boolean.
- [ ] Hiểu hai toán tử `&&`/`||` **đánh giá ngắn mạch từ trái sang phải**.
- [ ] Biết `&` và `|` boolean **không ngắn mạch**.
- [ ] Dùng điều kiện guard trước khi truy cập nullable hoặc chia nguyên.
- [ ] Ghi nhớ thứ tự ưu tiên: `!` rồi `&&` rồi `||`.
- [ ] Áp dụng đúng De Morgan khi phủ định điều kiện ghép.
- [ ] Không dùng số hoặc String như boolean; kiểm tra `Boolean` có thể null.
- [ ] Tránh phụ thuộc vào tác dụng phụ ẩn trong một biểu thức logic.

**Kết luận:** `&&`, `||` và `!` là nền tảng của biểu thức điều kiện Java. Muốn dùng đúng, không chỉ thuộc bảng chân trị mà còn cần hiểu **đánh giá ngắn mạch**, **thứ tự ưu tiên**, **phủ định điều kiện ghép** và **các rủi ro null/exception**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="logic-and-conditions-002">Đánh giá ngắn mạch và những biểu thức không được chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Đánh giá ngắn mạch và những biểu thức không được chạy trong Java

> **Java core — language-basics / Logic và điều kiện**  
> **Yêu cầu:** JDK 17+, một file Java độc lập, chỉ dùng thư viện chuẩn.  
> **Mục tiêu:** Dự đoán khi nào Java **không đánh giá vế phải** của `&&`, `||` và **nhánh không được chọn** của `?:`; phân biệt chúng với `&`, `|`, `^` trên boolean; viết guard tránh lỗi và kiểm soát side effect.

## 1. Đánh giá ngắn mạch là gì?

**Short-circuit evaluation** (đánh giá ngắn mạch) nghĩa là Java **không cần chạy một biểu thức con** khi vế đã tính đủ để biết kết quả của toàn bộ phép logic.

- `left && right`: nếu `left` là **false**, kết quả chắc chắn false, nên **không đánh giá `right`**.
- `left || right`: nếu `left` là **true**, kết quả chắc chắn true, nên **không đánh giá `right`**.
- Nếu cần biết vế phải để quyết định kết quả, Java **có đánh giá** vế phải.

Ví dụ:

~~~java
boolean andResult = false && expensiveCheck(); // false, không gọi hàm.
boolean orResult = true || expensiveCheck();   // true, không gọi hàm.
~~~

`expensiveCheck()` ở đây chỉ là tên minh họa; hãy xem chương trình runnable bên dưới để có các phương thức thực tế.

**Phân biệt:** “Vế phải không được chạy” là hành vi **lúc thực thi**. Java vẫn phải phân tích cú pháp và kiểm tra kiểu của toàn bộ biểu thức khi **biên dịch**, kể cả một nhánh sẽ bị bỏ qua ở lần chạy cụ thể.

## 2. Bảng quyết định: vế phải có được đánh giá không?

| Toán tử | Vế trái | Có chạy vế phải? | Kết quả |
| --- | --- | --- | --- |
| `&&` | false | **Không** | false |
| `&&` | true | **Có** | Theo giá trị vế phải |
| OR ngắn mạch (`\|\|`) | true | **Không** | true |
| OR ngắn mạch (`\|\|`) | false | **Có** | Theo giá trị vế phải |

Java đánh giá **vế trái trước vế phải**. Nếu vế trái tự ném ngoại lệ thì biểu thức kết thúc bất thường và vế phải cũng không được đánh giá, nhưng đó **không phải** trường hợp short-circuit trả về giá trị boolean bình thường.

Chẳng hạn:

~~~java
boolean first = true && check();  // Phải đánh giá check().
boolean second = false || check(); // Phải đánh giá check().
~~~

Vì vậy không thể khẳng định “`&&` không chạy vế phải” hoặc “`||` không chạy vế phải” trong mọi trường hợp. Điều kiện chính xác là **vế trái đủ quyết định kết quả**.

## 3. Những biểu thức bị bỏ qua không gây side effect hay exception

Khi nhánh bị bỏ qua, các thao tác **bên trong nhánh đó không xảy ra**: không gọi hàm, không tăng biến, không truy cập mảng, không unbox đối tượng và không ném ngoại lệ bắt nguồn từ biểu thức bị bỏ qua.

~~~java
int counter = 0;
boolean a = false && (++counter > 0); // a=false, counter=0.
boolean b = true || (++counter > 0);  // b=true, counter vẫn=0.
~~~

Ví dụ với mẫu số bằng 0:

~~~java
int divisor = 0;
boolean valid = divisor != 0 && (10 / divisor > 2);
// valid=false, phép chia không được chạy, không có ArithmeticException.
~~~

**Không đảo guard:** `10 / divisor > 2 && divisor != 0` sẽ thực hiện phép chia trước và ném ngoại lệ nếu `divisor == 0`.

Các điều kiện trong biểu thức ngắn mạch là các biểu thức `boolean`; Java **không** chuyển ngầm số nguyên 0/1 thành false/true.

## 4. Guard an toàn cho null, mảng và điều kiện phụ thuộc

Short-circuit đặc biệt hữu ích khi một biểu thức chỉ hợp lệ **sau khi đã kiểm tra điều kiện trước đó**.

~~~java
String text = null;
boolean hasText = text != null && !text.isBlank(); // false, không NPE.

int[] numbers = {5, 10, 15};
int index = -1;
boolean validIndex = index >= 0
        && index < numbers.length
        && numbers[index] > 0; // false, không truy cập numbers[-1].
~~~

Với nhiều `&&` liên tiếp, Java đánh giá từ trái sang phải và **dừng tại điều kiện false đầu tiên**; mọi điều kiện còn lại đều không chạy.

Với `||`, điều kiện có thể được viết theo dạng có giá trị mặc định:

~~~java
String name = null;
boolean missingOrBlank = name == null || name.isBlank();
// true, không gọi name.isBlank().
~~~

Điều kiện bảo vệ phải nằm ở **bên trái** phép logic tương ứng. Nếu `name` là null mà viết `name.isBlank() || name == null`, ngoại lệ xảy ra trước khi tới phép kiểm tra null.

## 5. `&`, `|` và `^` trên boolean không ngắn mạch

`&`, `|` và `^` có thể dùng cho boolean, nhưng khác với `&&`/`||` ở việc **vẫn đánh giá cả hai vế**:

| Toán tử boolean | Ý nghĩa | Đánh giá vế phải |
| --- | --- | --- |
| `&&` | AND ngắn mạch | Chỉ khi vế trái true |
| `\|\|` | OR ngắn mạch | Chỉ khi vế trái false |
| `&` | AND không ngắn mạch | **Luôn** |
| `\|` | OR không ngắn mạch | **Luôn** |
| `^` | XOR | **Luôn** |

~~~java
int counter = 0;
boolean result = false & (++counter > 0);
// result=false, nhưng counter=1 vì vế phải vẫn chạy.
~~~

Nếu vế phải ném ngoại lệ, `false & right` **vẫn ném**, còn `false && right` bỏ qua vế phải.

**Ghi nhớ về kiểu dữ liệu:** `&`, `|` và `^` cũng có cách dùng *bitwise* với số nguyên; `&&` và `||` không phải toán tử bitwise và chỉ dùng với boolean. Không tùy tiện thay cặp toán tử ngắn mạch bằng loại một ký tự khi vế phải chứa thao tác tốn kém hoặc có thể lỗi.

## 6. Toán tử điều kiện `?:` cũng bỏ qua nhánh không chọn

Biểu thức `condition ? whenTrue : whenFalse` đánh giá `condition` trước và **chỉ đánh giá một trong hai nhánh**:

~~~java
int x = 5;
int result = x > 0 ? computePositive() : computeNonPositive();
// Nếu x > 0: chỉ gọi computePositive().
// Nếu x <= 0: chỉ gọi computeNonPositive().
~~~

Đây là **đánh giá có điều kiện**, tương tự cơ chế chỉ chạy nhánh được chọn trong `if/else`. Không nên đánh đồng `?:` với việc chạy cả hai phép tính rồi chọn một kết quả.

Ví dụ với log:

~~~java
StringBuilder log = new StringBuilder();
int result = false
        ? markInt(log, "X", 10)
        : markInt(log, "R", 20);
// result=20, log="R"; markInt(...,"X",...) không được gọi.
~~~

Khi hai nhánh có tác dụng phụ đáng kể, `if/else` thường diễn đạt ý đồ dễ đọc và debug hơn toán tử ba ngôi lồng nhau.

## 7. Code Java 17+ runnable: quan sát nhánh bị bỏ qua

Tạo file **ShortCircuitDemo.java**:

~~~java
public final class ShortCircuitDemo {
    private static boolean mark(
            StringBuilder log, String label, boolean value) {
        log.append(label);
        return value;
    }

    private static int markInt(
            StringBuilder log, String label, int value) {
        log.append(label);
        return value;
    }

    private static boolean fail() {
        throw new IllegalStateException("executed");
    }

    public static void main(String[] args) {
        StringBuilder trace = new StringBuilder();

        boolean and = mark(trace, "F", false)
                && mark(trace, "X", true);
        boolean or = mark(trace, "T", true)
                || mark(trace, "Y", false);
        boolean eagerAnd = mark(trace, "A", false)
                & mark(trace, "B", true);
        boolean eagerOr = mark(trace, "C", true)
                | mark(trace, "D", false);
        boolean xor = mark(trace, "E", true)
                ^ mark(trace, "G", false);

        System.out.println("and=" + and);
        System.out.println("or=" + or);
        System.out.println("eagerAnd=" + eagerAnd);
        System.out.println("eagerOr=" + eagerOr);
        System.out.println("xor=" + xor);
        System.out.println("trace=" + trace);

        String name = null;
        System.out.println("nullGuard="
                + (name != null && !name.isBlank()));
        System.out.println("nullOr="
                + (name == null || name.isBlank()));

        int[] numbers = {5, 10, 15};
        int index = -1;
        System.out.println("indexGuard="
                + (index >= 0 && index < numbers.length
                    && numbers[index] > 0));

        int zero = 0;
        System.out.println("divideGuard="
                + (zero != 0 && 10 / zero > 2));

        boolean skippedAnd = false && fail();
        boolean skippedOr = true || fail();
        System.out.println("skippedAnd=" + skippedAnd);
        System.out.println("skippedOr=" + skippedOr);

        try {
            boolean eager = false & fail();
            System.out.println("eager=" + eager);
        } catch (IllegalStateException ex) {
            System.out.println("eagerThrows=IllegalStateException");
        }

        StringBuilder branches = new StringBuilder();
        int selected = false
                ? markInt(branches, "X", 10)
                : markInt(branches, "R", 20);
        int other = true
                ? markInt(branches, "L", 30)
                : markInt(branches, "Y", 40);
        System.out.println("conditional=" + selected + "," + other);
        System.out.println("branchTrace=" + branches);

        int[] visits = {0};
        boolean short1 = false && (++visits[0] > 0);
        boolean short2 = true || (++visits[0] > 0);
        System.out.println("shortCount=" + visits[0]);
        boolean eager = false & (++visits[0] > 0);
        System.out.println("eagerCount=" + visits[0]);

        Boolean unknown = null;
        boolean boxedGuard = unknown != null && unknown;
        System.out.println("boxedGuard=" + boxedGuard);
        try {
            boolean unsafe = true && unknown;
            System.out.println("unsafe=" + unsafe);
        } catch (NullPointerException ex) {
            System.out.println("boxedNull=NullPointerException");
        }
    }
}
~~~

Biên dịch/chạy ở thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 ShortCircuitDemo.java
java ShortCircuitDemo
~~~

**Expected stdout chính xác:**

~~~text
and=false
or=true
eagerAnd=false
eagerOr=true
xor=true
trace=FTABCDEG
nullGuard=false
nullOr=true
indexGuard=false
divideGuard=false
skippedAnd=false
skippedOr=true
eagerThrows=IllegalStateException
conditional=20,30
branchTrace=RL
shortCount=0
eagerCount=1
boxedGuard=false
boxedNull=NullPointerException
~~~

Dấu vết `FTABCDEG`: `&&` bỏ qua X, `||` bỏ qua Y, còn `&`, `|`, `^` đều chạy cả hai vế. `branchTrace=RL` chỉ có nhánh được chọn; `shortCount=0` xác nhận hai thao tác `++` bị bỏ qua, còn `eagerCount=1` thể hiện tác dụng phụ của `&`.

## 8. Sai lầm thường gặp và bài tập

| Sai lầm | Vì sao nguy hiểm? | Cách sửa |
| --- | --- | --- |
| `obj.method() && obj != null` | Có thể gọi method trên null trước khi guard | `obj != null && obj.method()` |
| `array[i] > 0 && i >= 0` | Có thể truy cập chỉ số không hợp lệ | `i >= 0 && i < array.length && array[i] > 0` |
| `divisor != 0 & 10 / divisor > 1` | `&` vẫn tính phép chia khi divisor=0 | Dùng `&&` |
| `true \|\| validate()` mà muốn validate luôn chạy | `validate()` không được gọi | Gọi riêng `validate()` nếu bắt buộc có side effect |
| `flag = cond && updateState()` và luôn muốn updateState | Hàm bị bỏ qua khi cond=false | Tách việc cập nhật thành câu lệnh riêng |
| Dùng `?:` với hai nhánh side effect dài | Khó đọc khi nhánh nào chạy không rõ | Dùng `if/else` |

**Bài 1.** Dự đoán bao nhiêu lần `check()` được gọi trong `false && check()`, `true && check()`, `true || check()`, `false || check()`.

**Bài 2.** Cho `int x = 0;`. So sánh `false && (++x > 0)` với `false & (++x > 0)`. Giá trị `x` cuối mỗi trường hợp là gì?

**Bài 3.** Với `String s = null;`, điều gì xảy ra trong `s != null && s.length() > 0` và `s.length() > 0 && s != null`?

**Bài 4.** Tạo biểu thức ba ngôi với hai hàm ghi dấu vết. Xác định nhánh nào được chạy ở mỗi giá trị điều kiện.

**Bài 5.** Phân biệt `false && fail()` với `false & fail()` nếu `fail()` ném exception.

### Ghi nhớ

**`&&` bỏ vế phải khi vế trái false; `||` bỏ vế phải khi vế trái true.** `?:` chỉ chạy nhánh được chọn. Biểu thức bị bỏ qua **không thực hiện tác dụng phụ, lời gọi hay exception của chính nó**, nhưng vẫn phải hợp lệ khi biên dịch. `&`, `|`, `^` trên boolean đánh giá cả hai vế. Đặt guard đúng phía và không dựa vào short-circuit nếu một thao tác **bắt buộc phải chạy**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="logic-and-conditions-003">Dùng ngắn mạch để kiểm tra null và giới hạn mảng</a>

<details>
<summary>Xem chi tiết</summary>

# Dùng ngắn mạch để kiểm tra null và giới hạn mảng — Java 17+

## 1. Bài học cốt lõi

Trong Java, toán tử logic **`&&` (AND ngắn mạch)** và **`||` (OR ngắn mạch)** có thể **không đánh giá toán hạng bên phải** nếu kết quả đã được quyết định từ toán hạng bên trái.

Đây là cơ sở để kiểm tra an toàn theo thứ tự: **tham chiếu khác `null` → chỉ số không âm → chỉ số nhỏ hơn `array.length` → mới truy cập phần tử**.

~~~java
int[] values = {4, 0, -2};
int index = 0;

boolean positive = values != null
        && index >= 0
        && index < values.length
        && values[index] > 0;

System.out.println(positive); // true
~~~

Nếu `values == null`, biểu thức `values.length` **không được đánh giá**. Nếu `index` ngoài miền hợp lệ, `values[index]` **không được truy cập**. Nhưng điều này chỉ đúng khi các phép kiểm tra được đặt **đúng thứ tự và dùng toán tử ngắn mạch**.

## 2. Quy tắc `&&` và `||`

Với hai biểu thức boolean `left`, `right`:

| Biểu thức | Khi nào bỏ qua `right`? | Kết quả khi bỏ qua |
|---|---|---|
| `left && right` | Khi `left == false` | `false` |
| `left || right` | Khi `left == true` | `true` |

Cụ thể:

~~~java
boolean a = false;
boolean b = true;

boolean andResult = a && b; // false
boolean orResult = b || a;  // true
~~~

Java đánh giá toán hạng trái trước. **Nếu cần thiết**, toán hạng phải mới được đánh giá; đây không phải đặc tính “compiler tình cờ tối ưu” mà là **ngữ nghĩa của toán tử**.

~~~mermaid
flowchart TD
    A["Đánh giá vế trái của &&"] --> B{"Vế trái true?"}
    B -- "Không" --> C["Trả false, bỏ qua vế phải"]
    B -- "Có" --> D["Đánh giá vế phải và dùng kết quả"]
    E["Đánh giá vế trái của ||"] --> F{"Vế trái true?"}
    F -- "Có" --> G["Trả true, bỏ qua vế phải"]
    F -- "Không" --> H["Đánh giá vế phải và dùng kết quả"]
~~~

Các biểu thức như `a && b && c` được nhóm theo tính kết hợp trái; cả chuỗi có thể dừng ngay khi tìm được một điều kiện false. Tương tự `a || b || c` có thể dừng ngay khi thấy true.

## 3. Kiểm tra `null` đúng thứ tự

Giả sử tham chiếu `String` có thể null:

~~~java
String name = null;

boolean valid = name != null && !name.isBlank();
System.out.println(valid); // false, không ném NullPointerException
~~~

Vế trái `name != null` là false, vì vậy Java **không gọi** `name.isBlank()`. Nếu đảo thứ tự:

~~~java
String name = null;
// boolean invalid = !name.isBlank() && name != null;
// Khi chạy: NullPointerException vì gọi isBlank() trước.
~~~

**Quy tắc:** điều kiện an toàn phải **đi trước thao tác phụ thuộc vào nó**.

| Nên dùng | Vì sao? |
|---|---|
| `obj != null && obj.isReady()` | Không gọi method trên null |
| `text != null && !text.isEmpty()` | Không truy cập String null |
| `array != null && array.length > 0` | Không lấy `length` trên null |
| `boxed != null && boxed > 0` | Không unbox wrapper null |

Không phải cứ viết `&&` ở đâu cũng an toàn. Ví dụ `obj.isReady() && obj != null` vẫn truy cập null **trước** khi kiểm tra.

## 4. Guard giới hạn mảng một chiều

Với mảng `int[] array` có độ dài `n`, chỉ số hợp lệ phải thuộc khoảng **`[0, n)`**:

\[
0 \le \text{index} < \text{array.length}
\]

Java **không** hỗ trợ cú pháp `0 <= index < array.length` kiểu toán học. Phải viết hai điều kiện riêng.

~~~java
static boolean positiveAt(int[] array, int index) {
    return array != null
            && index >= 0
            && index < array.length
            && array[index] > 0;
}
~~~

Hàm có các thuộc tính sau:

1. Mảng null → `false`, không truy cập `length`.
2. Chỉ số âm → `false`, không truy cập phần tử.
3. `index == array.length` → `false`: đây là **vị trí nằm ngay sau phần tử cuối**.
4. Chỉ số hợp lệ → mới kiểm tra `array[index] > 0`.
5. Mảng rỗng → luôn trả `false` với mọi chỉ số `int`.

Với `int[] values = {4, 0, -2}`, các chỉ số hợp lệ là `0`, `1`, `2`, **không phải 3**.

**Lỗi off-by-one rất thường gặp:**

~~~java
// index <= array.length // SAI với truy cập phần tử
// index < array.length  // ĐÚNG nếu đồng thời index >= 0
~~~

Chỉ số âm cũng không thể bỏ qua: Java **không** coi `array[-1]` là phần tử cuối như một số ngôn ngữ khác.

## 5. Kiểm tra giá trị phần tử có thể null

Một mảng tham chiếu như `String[]` có thể **không null**, nhưng một phần tử của nó lại **null**:

~~~java
String[] names = {"Java", null, "Go"};

boolean startsWithJ = names != null
        && 1 >= 0
        && 1 < names.length
        && names[1] != null
        && names[1].startsWith("J");

System.out.println(startsWithJ); // false, không gọi startsWith trên null
~~~

Quy trình guard phải phân biệt **null của mảng** với **null của phần tử**:

~~~java
static boolean startsWithJAt(String[] names, int index) {
    return names != null
            && index >= 0
            && index < names.length
            && names[index] != null
            && names[index].startsWith("J");
}
~~~

Nếu bỏ `names[index] != null`, đoạn cuối có thể gây `NullPointerException`. Đừng suy rằng `names != null` đủ bảo đảm mọi phần tử đều tồn tại và không null.

**Tính dễ đọc:** Nếu dùng cùng `names[index]` quá nhiều lần và dữ liệu có thể thay đổi đồng thời, hãy xác thực môi trường/đồng bộ trước; chỉ lặp biểu thức guard không biến thao tác thành nguyên tử.

## 6. Dùng `||` để loại bỏ dữ liệu không hợp lệ

Khi muốn phát hiện một chỉ số **không hợp lệ**, có thể dùng điều kiện nghịch đảo:

~~~java
static boolean invalidIndex(int[] array, int index) {
    return array == null || index < 0 || index >= array.length;
}
~~~

Ở đây:

- Nếu `array == null` true, Java trả `true` ngay; không truy cập `array.length`.
- Nếu `index < 0` true, điều kiện tiếp theo cũng bị bỏ qua.
- Nếu đã vượt qua hai guard, `array.length` mới được đọc.

Mẫu hay dùng trong một phương thức:

~~~java
if (array == null || index < 0 || index >= array.length) {
    return false; // Hoặc throw theo hợp đồng API
}
return array[index] > 0; // Sau guard, chỉ số hợp lệ
~~~

Hai cách mô tả cùng miền điều kiện:

| Ý nghĩa | Mẫu logic |
|---|---|
| Hợp lệ để truy cập | `array != null && index >= 0 && index < array.length` |
| Không hợp lệ | `array == null || index < 0 || index >= array.length` |
| Truy cập phần tử nếu hợp lệ | `guard && array[index] > 0` |
| Từ chối rồi mới truy cập | `if (invalid) return ...;` rồi `array[index]` |

Đây là một ứng dụng của quy tắc **De Morgan** khi điều kiện không có tác dụng phụ. Chọn cách diễn đạt đúng ngữ nghĩa nghiệp vụ và dễ kiểm tra.

## 7. `&` và `|` KHÔNG ngắn mạch trên boolean

Java có các toán tử `&` (AND không ngắn mạch) và `|` (OR không ngắn mạch) dùng được trên boolean. Chúng **đánh giá cả hai vế**, khác với `&&` và `||`.

~~~java
int[] numbers = null;

// boolean unsafe = numbers != null & numbers.length > 0;
// Nếu chạy: NullPointerException vì vế phải vẫn được đánh giá.
~~~

Tương tự:

~~~java
int[] values = {4, 0};

// boolean unsafe = (values != null) & (values[99] > 0);
// Nếu chạy: ArrayIndexOutOfBoundsException.
~~~

**Đừng thay `&&` bằng `&` trong null guard.** Với `|`, cũng không dùng để thay `||` nếu dựa vào việc bỏ qua phép truy cập nguy hiểm ở vế phải:

~~~java
// values == null | values.length == 0
// Nếu values là null, truy cập values.length vẫn chạy và có thể ném NPE.
~~~

Bảng đối chiếu:

| Toán tử | Dùng với boolean | Đánh giá vế phải khi vế trái đã quyết định kết quả? |
|---|---|---|
| `&&` | Có | Không |
| `||` | Có | Không |
| `&` | Có | **Có** |
| `|` | Có | **Có** |

Toán tử `&`/`|` còn dùng trong thao tác bitwise số nguyên; đây là chức năng khác. Bài này chỉ so sánh **ngữ nghĩa boolean**.

## 8. Mảng hai chiều có thể chứa hàng null hoặc hàng dài ngắn khác nhau

Trong Java, `int[][]` là **mảng chứa các tham chiếu tới từng hàng**. Một hàng có thể null, hàng khác có độ dài riêng (*jagged array*). Không được giả định mọi hàng đều có `matrix[0].length`.

~~~java
static boolean positiveAt2D(int[][] matrix, int row, int col) {
    return matrix != null
            && row >= 0
            && row < matrix.length
            && matrix[row] != null
            && col >= 0
            && col < matrix[row].length
            && matrix[row][col] > 0;
}
~~~

Thứ tự đúng:

1. Mảng ngoài khác null.
2. `row` nằm trong `[0, matrix.length)`.
3. Hàng `matrix[row]` khác null.
4. `col` không âm, nhỏ hơn **độ dài của chính hàng đã chọn**.
5. Chỉ khi ấy mới đọc `matrix[row][col]`.

Nếu đảo `matrix[row] != null` lên trước kiểm tra `row`, vẫn có thể ném `ArrayIndexOutOfBoundsException`. Nếu lấy `matrix[row].length` trước khi chắc chắn hàng khác null, có thể ném `NullPointerException`.

## 9. Boolean wrapper cũng có thể là `null`

Với kiểu nguyên thủy `boolean`, chỉ có `true` hoặc `false`. Với wrapper `Boolean`, còn có thể là `null`:

~~~java
Boolean enabled = null;
boolean allowed = enabled != null && enabled;

System.out.println(allowed); // false
~~~

Khi `enabled == null`, toán hạng `enabled` ở bên phải không được đánh giá, tránh auto-unboxing null.

Nếu muốn chỉ true mới được chấp nhận, một lựa chọn khác là:

~~~java
boolean allowed = Boolean.TRUE.equals(enabled);
~~~

Cách này xử lý null an toàn, nhưng đừng nhầm kiểm tra Boolean với kiểm tra **miền chỉ số mảng**: hai vấn đề có thể xuất hiện cùng lúc và cần guard độc lập.

## 10. Ngoại lệ và tác dụng phụ: short-circuit không bảo vệ mọi thứ

`&&`/`||` chỉ bỏ qua những **toán hạng chưa được đánh giá**. Chúng không hoàn tác tác dụng phụ trước đó và không tự xử lý các lỗi bên trong vế trái.

~~~java
int index = 0;
int[] values = {10, 20};

boolean result = index++ < values.length
        && values[index] > 0;

// Không nên viết: index đã tăng trước khi vế phải dùng index.
~~~

Dù biểu thức này có thể chạy với một số dữ liệu, nó dễ sai ở ranh giới: ví dụ `index` ban đầu là `values.length - 1`; vế trái có thể true rồi tăng chỉ số thành `values.length`, và vế phải truy cập ngoài mảng.

**Guard không nên tự thay đổi biến mà nó dùng để kiểm tra.** Tốt hơn:

~~~java
int index = 1;
boolean valid = values != null
        && index >= 0
        && index < values.length;
if (valid) {
    int current = values[index];
    index++; // Cập nhật rõ ràng ở một câu lệnh khác
}
~~~

Với nhiều thread cùng sửa tham chiếu, chỉ số hoặc nội dung mảng, các guard thông thường **không bảo đảm atomicity hoặc thread safety**. Cần đồng bộ/phân quyền sở hữu dữ liệu theo yêu cầu, thay vì chỉ đặt thêm `&&`.

## 11. Khi nào nên tách thành `if` và guard clause?

Một điều kiện ngắn như:

~~~java
boolean safe = array != null
        && index >= 0
        && index < array.length;
~~~

thường rất dễ hiểu. Nhưng khi phải kiểm tra nhiều thuộc tính hoặc cần **thông báo lỗi riêng** cho null, chỉ số âm và chỉ số quá lớn, cách dùng guard clause rõ hơn:

~~~java
static int requireElement(int[] array, int index) {
    if (array == null) {
        throw new IllegalArgumentException("array is null");
    }
    if (index < 0 || index >= array.length) {
        throw new IndexOutOfBoundsException("invalid index: " + index);
    }
    return array[index];
}
~~~

Ưu điểm:

- Chỉ ra chính xác nguyên nhân từ chối.
- Tách bước validation khỏi thao tác đọc.
- Có thể kiểm thử riêng từng nhánh lỗi.
- Tránh điều kiện boolean quá dài hoặc lặp thao tác có side effect.

Java 17 cũng có `Objects.checkIndex(index, length)` để kiểm tra chỉ số trong `[0,length)`; hàm này ném ngoại lệ nếu không hợp lệ. Nhưng khi đối tượng mảng có thể null, vẫn cần xử lý null **trước khi** truy cập `array.length`.

**Chọn API theo hợp đồng:** hàm truy vấn có thể trả false khi dữ liệu không hợp lệ; hàm truy cập bắt buộc có thể ném exception có ý nghĩa. Không âm thầm trả giá trị mặc định nếu làm mất khác biệt giữa dữ liệu lỗi và dữ liệu hợp lệ.

## 12. Ví dụ Java 17 hoàn chỉnh, có kết quả cố định

Tạo file đúng tên **`ShortCircuitArrayGuardDemo.java`**:

~~~java
public final class ShortCircuitArrayGuardDemo {
    private ShortCircuitArrayGuardDemo() {}

    static boolean positiveAt(int[] array, int index) {
        return array != null
                && index >= 0
                && index < array.length
                && array[index] > 0;
    }

    static boolean invalidIndex(int[] array, int index) {
        return array == null || index < 0 || index >= array.length;
    }

    static boolean startsWithJAt(String[] names, int index) {
        return names != null
                && index >= 0
                && index < names.length
                && names[index] != null
                && names[index].startsWith("J");
    }

    static boolean positiveAt2D(int[][] matrix, int row, int col) {
        return matrix != null
                && row >= 0
                && row < matrix.length
                && matrix[row] != null
                && col >= 0
                && col < matrix[row].length
                && matrix[row][col] > 0;
    }

    static boolean traced(String label, boolean answer) {
        System.out.println("called " + label);
        return answer;
    }

    static int requireElement(int[] array, int index) {
        if (array == null) {
            throw new IllegalArgumentException("array is null");
        }
        if (index < 0 || index >= array.length) {
            throw new IndexOutOfBoundsException("invalid index: " + index);
        }
        return array[index];
    }

    public static void main(String[] args) {
        int[] scores = {4, 0, -2};
        System.out.println("positive[0]=" + positiveAt(scores, 0));
        System.out.println("positive[1]=" + positiveAt(scores, 1));
        System.out.println("positive[2]=" + positiveAt(scores, 2));
        System.out.println("negative index=" + positiveAt(scores, -1));
        System.out.println("end index=" + positiveAt(scores, 3));
        System.out.println("null array=" + positiveAt(null, 0));
        System.out.println("empty array=" + positiveAt(new int[0], 0));

        System.out.println("invalid null=" + invalidIndex(null, 0));
        System.out.println("invalid end=" + invalidIndex(scores, 3));
        System.out.println("valid index=" + !invalidIndex(scores, 2));

        String[] names = {"Java", null, "Go"};
        System.out.println("name[0]=" + startsWithJAt(names, 0));
        System.out.println("name[1]=" + startsWithJAt(names, 1));
        System.out.println("name null=" + startsWithJAt(null, 0));

        int[][] grid = {null, {3, 0}, {}};
        System.out.println("grid null row=" + positiveAt2D(grid, 0, 0));
        System.out.println("grid positive=" + positiveAt2D(grid, 1, 0));
        System.out.println("grid col end=" + positiveAt2D(grid, 1, 2));
        System.out.println("grid empty row=" + positiveAt2D(grid, 2, 0));

        Boolean enabled = null;
        System.out.println("nullable Boolean="
                + (enabled != null && enabled));

        boolean left = false;
        boolean skippedAnd = left && traced("and-right", true);
        System.out.println("&& skipped=" + skippedAnd);

        left = true;
        boolean skippedOr = left || traced("or-right", false);
        System.out.println("|| skipped=" + skippedOr);

        boolean eagerAnd = false & traced("and-eager", true);
        System.out.println("& evaluated=" + eagerAnd);

        boolean eagerOr = true | traced("or-eager", false);
        System.out.println("| evaluated=" + eagerOr);

        int[] missing = null;
        try {
            boolean unsafe = missing != null & missing.length > 0;
            System.out.println(unsafe);
        } catch (NullPointerException ex) {
            System.out.println("eager null=NullPointerException");
        }

        try {
            boolean unsafe = scores != null & scores[99] > 0;
            System.out.println(unsafe);
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.out.println("eager index=ArrayIndexOutOfBoundsException");
        }

        System.out.println("required element=" + requireElement(scores, 0));
        try {
            requireElement(scores, 3);
        } catch (IndexOutOfBoundsException ex) {
            System.out.println("required invalid=" + ex.getMessage());
        }
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên; terminal ở thư mục chứa file; không cần Maven, Gradle hoặc thư viện ngoài JDK.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 ShortCircuitArrayGuardDemo.java
java -cp . ShortCircuitArrayGuardDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\ShortCircuitArrayGuardDemo.java
java -cp . ShortCircuitArrayGuardDemo
~~~

**Exact expected stdout:**

~~~text
positive[0]=true
positive[1]=false
positive[2]=false
negative index=false
end index=false
null array=false
empty array=false
invalid null=true
invalid end=true
valid index=true
name[0]=true
name[1]=false
name null=false
grid null row=false
grid positive=true
grid col end=false
grid empty row=false
nullable Boolean=false
&& skipped=false
|| skipped=true
called and-eager
& evaluated=false
called or-eager
| evaluated=true
eager null=NullPointerException
eager index=ArrayIndexOutOfBoundsException
required element=4
required invalid=invalid index: 3
~~~

### Cách kiểm tra kết quả

1. `positiveAt` từ chối mảng null, mảng rỗng, chỉ số âm và chỉ số bằng `length`; chỉ truy cập khi các guard đã qua.
2. `invalidIndex` dùng OR ngắn mạch: mảng null hoặc chỉ số ngoài miền đều cho true an toàn.
3. `startsWithJAt` kiểm tra cả mảng và **phần tử** trước khi gọi method trên String.
4. `positiveAt2D` kiểm tra hàng null, độ dài riêng từng hàng và cột ngoài miền.
5. `Boolean enabled = null` không gây NPE nhờ guard trước lúc unboxing.
6. Hai dòng `&& skipped` và `|| skipped` **không in `called and-right` hoặc `called or-right`**, chứng minh vế phải bị bỏ qua.
7. `&` và `|` vẫn in `called and-eager`, `called or-eager` vì chúng không short-circuit.
8. Hai trường hợp `eager ...` ném NPE/AIOOBE; ví dụ bắt đúng loại và in thông báo cố định.
9. `requireElement` minh họa tình huống dùng exception rõ ràng thay vì một giá trị mặc định.

## 13. Lỗi thường gặp

| Lỗi hoặc ngộ nhận | Hậu quả | Cách đúng |
|---|---|---|
| `array.length > 0 && array != null` | NPE nếu mảng null | Kiểm tra `array != null` trước |
| `array != null & array.length > 0` | `&` luôn đánh giá vế phải | Dùng `&&` |
| `array == null | array.length == 0` | `|` luôn đánh giá vế phải | Dùng `||` |
| `index < array.length` không kiểm tra âm | Chỉ số âm vẫn có thể truy cập sai | `index >= 0 && index < array.length` |
| `index <= array.length` | Cho phép index bằng length | Dùng `index < array.length` |
| `array != null && array[index] > 0` | Chỉ bảo vệ null, chưa bảo vệ index | Thêm hai kiểm tra giới hạn |
| `names != null && names[i].isBlank()` | Phần tử `names[i]` có thể null | Kiểm tra `names[i] != null` |
| `matrix[row].length` trước khi kiểm tra row | Row có thể ngoài miền hoặc null | Guard ma trận, row, hàng rồi col |
| `array[index++]` trong guard | Index có thể đổi giữa kiểm tra và truy cập | Tách cập nhật index khỏi validation |
| `&&` tự đồng bộ mọi dữ liệu | Không atomic nếu dữ liệu bị thread khác sửa | Dùng đồng bộ/ownership phù hợp |
| Nghĩ `&&` và `&` chỉ khác hiệu năng | Side effect và exception có thể khác | Chọn theo ngữ nghĩa |
| Dùng null guard nhưng vẫn truy cập bên ngoài guard | Guard chỉ bảo vệ đoạn được kiểm soát | Xác thực ở điểm truy cập hoặc thiết kế API rõ |
| `Boolean nullable` tự chuyển null thành false | Unboxing null ném NPE | Guard `!= null` hoặc `Boolean.TRUE.equals` |

## 14. Bài tập có đáp án

### Bài A — kiểm tra chỉ số hợp lệ

Cho `int[] values = {5, 8, 13};` và một `index`. Hãy viết điều kiện chỉ đọc `values[index]` khi mảng tồn tại và index hợp lệ.

**Đáp án:**

~~~java
boolean valid = values != null
        && index >= 0
        && index < values.length;
if (valid) {
    System.out.println(values[index]);
}
~~~

Nếu `index == 3` hoặc `-1`, đoạn truy cập bị bỏ qua.

### Bài B — `&&` khác `&`

~~~java
int[] a = null;
boolean safe = a != null && a.length > 0;
// boolean unsafe = a != null & a.length > 0;
~~~

**Đáp án:** `safe == false`, không ném lỗi. Nếu chạy dòng `unsafe`, vế phải vẫn đọc `a.length` và ném `NullPointerException`.

### Bài C — guard với `||`

~~~java
int[] a = {2, 3};
int i = -1;
boolean invalid = a == null || i < 0 || i >= a.length;
System.out.println(invalid);
~~~

**Đáp án:** `true`; Java dừng ở `i < 0`, không cần tính vế cuối.

### Bài D — phần tử String null

~~~java
String[] names = {null, "Java"};
int i = 0;
boolean ok = names != null
        && i >= 0 && i < names.length
        && names[i] != null && names[i].startsWith("J");
System.out.println(ok);
~~~

**Đáp án:** `false`, không ném NPE.

### Bài E — mảng hai chiều

~~~java
int[][] matrix = {null, {3, 4}};
System.out.println(positiveAt2D(matrix, 0, 0));
System.out.println(positiveAt2D(matrix, 1, 1));
~~~

**Đáp án:**

~~~text
false
true
~~~

Hàm `positiveAt2D` được định nghĩa trong ví dụ độc lập phía trên.

### Bài F — lỗi khi index thay đổi ngay trong guard

~~~java
int[] values = {10, 20};
int index = 1;
// boolean ok = index++ < values.length && values[index] > 0;
~~~

**Đáp án:** vế trái so `1 < 2` cho true nhưng tăng `index` lên 2; vế phải `values[2]` gây `ArrayIndexOutOfBoundsException`. Hãy dùng index không thay đổi trong quá trình guard, rồi cập nhật nó bằng câu lệnh riêng.

## 15. Checklist và kết luận

- [ ] Nhớ `&&` bỏ vế phải khi vế trái false; `||` bỏ vế phải khi vế trái true.
- [ ] Không nhầm `&&`/`||` với `&`/`|` boolean.
- [ ] Luôn kiểm tra `array != null` trước `array.length`.
- [ ] Kiểm tra cả `index >= 0` và `index < array.length`.
- [ ] Với mảng reference, kiểm tra `array[index] != null` trước gọi method trên phần tử.
- [ ] Với mảng hai chiều, guard từng cấp theo đúng thứ tự.
- [ ] Dùng `array == null || index < 0 || index >= array.length` khi cần phát hiện invalid.
- [ ] Hiểu short-circuit bỏ qua side effect ở vế phải nhưng không rollback thao tác đã xảy ra.
- [ ] Không tăng/giảm index ngay trong các phép so sánh guard nếu còn dùng lại nó.
- [ ] Phân biệt kiểm tra dữ liệu an toàn trong một thread và thread safety thực sự.
- [ ] Chọn trả boolean hoặc ném exception theo hợp đồng API.
- [ ] Biên dịch/chạy `ShortCircuitArrayGuardDemo.java` bằng Java 17+ và đối chiếu chính xác stdout.

**Kết luận:** Dùng ngắn mạch hiệu quả nhất khi **đặt điều kiện bảo vệ trước thao tác có thể lỗi**, theo thứ tự **null → chỉ số không âm → chỉ số nhỏ hơn length → phần tử tồn tại → truy cập**. `&&` và `||` cung cấp bảo đảm ngữ nghĩa để bỏ qua vế phải; `&` và `|` không làm vậy. Guard rõ ràng, không kèm side effect và có kiểm thử biên giúp code Java 17+ dễ đọc, đúng logic và tránh `NullPointerException`/`ArrayIndexOutOfBoundsException` không cần thiết.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="logic-and-conditions-004">&amp;, | và ^ khi áp dụng lên boolean</a>

<details>
<summary>Xem chi tiết</summary>

# `&`, `|` và `^` khi áp dụng lên `boolean` — Java 17+

Trong Java, các toán tử `&`, `|` và `^` có thể kết hợp **hai giá trị `boolean`**. Kết quả của chúng cũng có kiểu `boolean`:

- `a & b`: **AND** — true chỉ khi **cả hai** cùng true.
- `a | b`: **OR** — true khi **ít nhất một** toán hạng true.
- `a ^ b`: **XOR** (*exclusive OR*) — true khi **chính xác một** trong hai toán hạng true.

**Điểm cần nhớ nhất:** với các toán hạng boolean, **cả hai vế** của `&`, `|` và `^` đều được đánh giá nếu việc đánh giá vế trái hoàn tất bình thường, **không ngắn mạch** như `&&` và `||`.

~~~java
boolean a = true;
boolean b = false;

System.out.println(a & b); // false.
System.out.println(a | b); // true.
System.out.println(a ^ b); // true.
~~~

Không nhầm dạng một ký tự `&`/`|` với dạng hai ký tự `&&`/`||`. Chúng có thể cho **cùng kết quả logic** nhưng **khác việc thực thi các biểu thức con**, điều rất quan trọng khi có lời gọi phương thức, `null` hoặc ngoại lệ.

## 1. Bảng chân trị đầy đủ

| `a` | `b` | `a & b` (AND) | `a \| b` (OR) | `a ^ b` (XOR) |
| --- | --- | --- | --- | --- |
| false | false | false | false | false |
| false | true | false | true | true |
| true | false | false | true | true |
| true | true | true | true | false |

**Ý nghĩa của XOR:** chỉ **một** điều kiện được phép đúng, không phải “một hoặc cả hai”.

~~~java
boolean hasPassword = true;
boolean hasOneTimeCode = false;
boolean exactlyOneMethod = hasPassword ^ hasOneTimeCode; // true.

boolean bothMethods = true ^ true; // false.
~~~

Với hai toán hạng kiểu boolean, `a ^ b` cho cùng **giá trị** như `a != b`, nhưng `^` giúp thể hiện ý định XOR rõ hơn trong một số tình huống. Nó **không** có nghĩa phép lũy thừa. Java không dùng `^` để tính `2^3 = 8`; phép lũy thừa số thực có thể dùng `Math.pow(2, 3)`.

## 2. Chương trình Java 17+ chạy trực tiếp bằng CLI

Tạo file UTF-8 tên **`EagerBooleanOperatorsDemo.java`**:

~~~java
public class EagerBooleanOperatorsDemo {
    static boolean mark(String label, boolean value) {
        System.out.println("eval=" + label);
        return value;
    }

    public static void main(String[] args) {
        System.out.println("== truth table ==");
        System.out.println("FF=" + (false & false)
                + "," + (false | false) + "," + (false ^ false));
        System.out.println("FT=" + (false & true)
                + "," + (false | true) + "," + (false ^ true));
        System.out.println("TF=" + (true & false)
                + "," + (true | false) + "," + (true ^ false));
        System.out.println("TT=" + (true & true)
                + "," + (true | true) + "," + (true ^ true));

        System.out.println("== eager evaluation ==");
        boolean eagerAnd = mark("and-left", false)
                & mark("and-right", true);
        System.out.println("eagerAnd=" + eagerAnd);

        boolean eagerOr = mark("or-left", true)
                | mark("or-right", false);
        System.out.println("eagerOr=" + eagerOr);

        boolean xor = mark("xor-left", true)
                ^ mark("xor-right", false);
        System.out.println("xor=" + xor);

        System.out.println("== short circuit ==");
        boolean shortAnd = mark("short-and-left", false)
                && mark("short-and-skipped", true);
        System.out.println("shortAnd=" + shortAnd);

        boolean shortOr = mark("short-or-left", true)
                || mark("short-or-skipped", false);
        System.out.println("shortOr=" + shortOr);

        System.out.println("== precedence ==");
        boolean p = true;
        boolean q = false;
        boolean r = false;
        System.out.println("ungrouped=" + (p | q & r));
        System.out.println("grouped=" + ((p | q) & r));
        System.out.println("notBeforeAnd=" + (!q & p));

        System.out.println("== guard conditions ==");
        String text = null;
        boolean safeText = text != null && !text.isBlank();
        System.out.println("safeText=" + safeText);
        try {
            boolean unsafeText = text != null & !text.isBlank();
            System.out.println("unexpectedText=" + unsafeText);
        } catch (NullPointerException ex) {
            System.out.println("eagerNull=NullPointerException");
        }

        int divisor = 0;
        boolean safeDivide = divisor == 0 || 12 / divisor > 3;
        System.out.println("safeDivide=" + safeDivide);
        try {
            boolean unsafeDivide = divisor == 0 | 12 / divisor > 3;
            System.out.println("unexpectedDivide=" + unsafeDivide);
        } catch (ArithmeticException ex) {
            System.out.println("eagerDivide=ArithmeticException");
        }

        System.out.println("== wrappers and toggle ==");
        Boolean missing = null;
        try {
            boolean unused = true ^ missing;
            System.out.println("unexpectedWrapper=" + unused);
        } catch (NullPointerException ex) {
            System.out.println("xorNull=NullPointerException");
        }

        boolean enabled = false;
        enabled ^= true;
        System.out.println("toggleOne=" + enabled);
        enabled ^= true;
        System.out.println("toggleTwo=" + enabled);
        System.out.println("xorVsNotEqual="
                + ((true ^ false) == (true != false)));

        System.out.println("integerBitwiseXor="
                + (0b1010 ^ 0b1100));
    }
}
~~~

Chạy trên **JDK 17 trở lên** trong PowerShell (Windows) hoặc Bash (Linux/macOS), tại thư mục chứa file:

~~~bash
java -version
javac -version
javac -encoding UTF-8 EagerBooleanOperatorsDemo.java
java EagerBooleanOperatorsDemo
~~~

**Exact expected stdout** của riêng lệnh `java EagerBooleanOperatorsDemo`:

~~~text
== truth table ==
FF=false,false,false
FT=false,true,true
TF=false,true,true
TT=true,true,false
== eager evaluation ==
eval=and-left
eval=and-right
eagerAnd=false
eval=or-left
eval=or-right
eagerOr=true
eval=xor-left
eval=xor-right
xor=true
== short circuit ==
eval=short-and-left
shortAnd=false
eval=short-or-left
shortOr=true
== precedence ==
ungrouped=true
grouped=false
notBeforeAnd=true
== guard conditions ==
safeText=false
eagerNull=NullPointerException
safeDivide=true
eagerDivide=ArithmeticException
== wrappers and toggle ==
xorNull=NullPointerException
toggleOne=true
toggleTwo=false
xorVsNotEqual=true
integerBitwiseXor=6
~~~

Các lệnh `java -version` và `javac -version` hiển thị phiên bản của máy, không thuộc khối expected stdout cố định. Phương thức `mark` chỉ giúp quan sát thứ tự đánh giá; trong mã thực tế, hãy tránh giấu tác dụng phụ vào điều kiện.

## 3. `&` và `&&`: cùng AND, khác cách đánh giá

Hai biểu thức đều cho kết quả AND trên boolean:

~~~java
boolean a = false;
boolean b = true;

System.out.println(a & b);  // false.
System.out.println(a && b); // false.
~~~

Sự khác biệt là **có đánh giá toán hạng phải hay không**.

~~~java
boolean eager = mark("L", false) & mark("R", true);
// In eval=L, sau do eval=R; eager=false.

boolean shortCircuit = mark("L", false) && mark("R", true);
// Chi in eval=L; shortCircuit=false.
~~~

Với `&`, nếu biểu thức trái hoàn tất bình thường, Java vẫn đánh giá biểu thức phải ngay cả khi giá trị trái là false. Với `&&`, khi vế trái false thì vế phải không chạy.

Điều này có thể làm khác **nhật ký**, **trạng thái**, **lời gọi API** hoặc **ngoại lệ**, dù kết quả boolean cuối cùng giống nhau.

## 4. `|` và `||`: cùng OR, khác ngắn mạch

~~~java
boolean eager = mark("L", true) | mark("R", false);
// In ca eval=L va eval=R, eager=true.

boolean shortCircuit = mark("L", true) || mark("R", false);
// Chi in eval=L, shortCircuit=true.
~~~

Với `|`, toán hạng phải vẫn được đánh giá khi vế trái true. Với `||`, vế trái true đủ quyết định kết quả nên vế phải được bỏ qua.

| Điều kiện vế trái | `a & b` | `a && b` | `a \| b` | `a || b` |
| --- | --- | --- | --- | --- |
| `a == false` | Đánh giá `b` | Bỏ qua `b` | Đánh giá `b` | Đánh giá `b` |
| `a == true` | Đánh giá `b` | Đánh giá `b` | Đánh giá `b` | Bỏ qua `b` |

**Mặc định**, nên dùng `&&`/`||` cho điều kiện thường gặp, nhất là điều kiện gác cổng. Chỉ chọn `&`/`|` trên boolean khi việc đánh giá **cả hai** thật sự cần thiết và đã được cân nhắc rõ ràng.

## 5. `^`: XOR và hai vế luôn được đánh giá

Với boolean, `a ^ b` đúng khi đúng **chính xác một** trong hai điều kiện. Không có dạng toán tử Java `^^`.

~~~java
System.out.println(false ^ false); // false.
System.out.println(false ^ true);  // true.
System.out.println(true ^ false);  // true.
System.out.println(true ^ true);   // false.
~~~

`^` luôn đánh giá cả hai toán hạng nếu vế trái hoàn thành bình thường:

~~~java
boolean different = mark("first", true) ^ mark("second", false);
// In eval=first, eval=second; different=true.
~~~

Ứng dụng điển hình là kiểm tra **đúng một lựa chọn**:

~~~java
boolean choseEmail = true;
boolean chosePhone = false;
boolean exactlyOne = choseEmail ^ chosePhone; // true.
~~~

Nếu muốn cho phép **ít nhất một** lựa chọn, dùng `||` thay vì `^`. Nếu muốn **cả hai** cùng có, dùng `&&`.

Với hai giá trị boolean, `a ^ b` và `a != b` có cùng kết quả. Khi các toán hạng có tác dụng phụ, cả `^` lẫn `!=` đều đánh giá hai toán hạng theo các quy tắc của Java. Tuy nhiên, cách biểu đạt `^` có thể giúp người đọc nhận ra rõ hơn khái niệm “đúng một”.

## 6. Không dùng `&` hoặc `|` làm null guard

Vì đánh giá vế phải luôn được thực hiện, dùng `&` có thể tạo `NullPointerException`:

~~~java
String name = null;

// An toan: vế phải bị bỏ qua nếu name null.
boolean safe = name != null && !name.isBlank();

// Nguy hiem:
// boolean wrong = name != null & !name.isBlank();
// Ném NullPointerException.
~~~

Tương tự với chia số nguyên cho 0:

~~~java
int divisor = 0;

// An toan: vế trái true làm || bỏ qua phép chia.
boolean safe = divisor == 0 || 12 / divisor > 3;

// Nguy hiem:
// boolean wrong = divisor == 0 | 12 / divisor > 3;
// Ném ArithmeticException.
~~~

Đây là khác biệt về **luồng đánh giá**, không phải về “độ chính xác của bảng chân trị”. Với toán hạng thứ hai có thể ném ngoại lệ, `&`/`|` có thể không trả kết quả boolean bình thường.

**Chú ý concurrency:** một guard đọc giá trị và một biểu thức truy cập cùng dữ liệu dùng chung có thể chịu ảnh hưởng từ luồng khác. Bản thân `&&` không thay thế cơ chế đồng bộ hoặc snapshot nếu chương trình có dữ liệu thay đổi đồng thời.

## 7. `Boolean` wrapper và unboxing

Ba toán tử nhận biểu thức `boolean` nguyên thủy hoặc `Boolean` có thể **unbox** về `boolean`.

~~~java
Boolean enabled = Boolean.TRUE;
boolean result = enabled ^ false; // true.
~~~

Nhưng nếu wrapper chứa `null`, unboxing gây `NullPointerException`:

~~~java
Boolean missing = null;
// boolean result = true ^ missing; // Ném NullPointerException.
~~~

Đừng ngầm coi `Boolean null` là false. Cần xác định chính sách nghiệp vụ; khi muốn coi chỉ `Boolean.TRUE` là đúng:

~~~java
Boolean flag = null;
boolean isEnabled = Boolean.TRUE.equals(flag); // false.
~~~

Với `&&`/`||`, vế phải chứa wrapper null **có thể được bỏ qua nhờ ngắn mạch**, nhưng nếu đến lượt đánh giá và unbox null thì vẫn ném ngoại lệ.

## 8. Độ ưu tiên và dấu ngoặc

Với các toán tử logic liên quan, thứ tự ưu tiên từ cao xuống thấp gồm:

| Độ ưu tiên tương đối | Toán tử |
| --- | --- |
| Cao | `!` |
| Sau đó | `&` |
| Sau đó | `^` |
| Sau đó | `\|` |
| Sau đó | `&&` |
| Thấp hơn | `||` |

Do `&` ưu tiên hơn `|`:

~~~java
boolean p = true;
boolean q = false;
boolean r = false;

boolean ungrouped = p | q & r;   // true: p | (q & r).
boolean grouped = (p | q) & r;   // false.
~~~

**Độ ưu tiên quyết định cách nhóm**, không đảo thứ tự đánh giá toán hạng. Những biểu thức có nhiều toán tử nên dùng ngoặc để thể hiện rõ ý thay vì buộc người đọc ghi nhớ mọi mức ưu tiên.

Ví dụ `!a & b` được nhóm `(!a) & b`, còn `!(a & b)` phủ định toàn bộ kết quả AND — đây là hai điều kiện khác nhau.

## 9. Phân biệt phép logic boolean với bitwise trên số nguyên

`&`, `|`, `^` còn được Java hỗ trợ trên **các kiểu số nguyên** để thao tác từng bit. Compiler quyết định phép toán nào dựa vào **kiểu toán hạng**.

~~~java
boolean logical = true ^ false; // true, XOR boolean.
int bitwise = 0b1010 ^ 0b1100;  // 0b0110 = 6.
~~~

Nếu toán hạng là hai số nguyên, `^` thực hiện XOR bit; nếu là hai boolean, `^` thực hiện XOR logic. Với toán hạng boolean, `&` và `|` không đổi thành phép số nguyên và trả `boolean`.

- `&&` và `||` **chỉ áp dụng cho boolean**.
- `&`, `|`, `^` áp dụng cho **boolean hoặc kiểu số nguyên phù hợp**.
- Java không tự đổi số 0 thành false, số khác 0 thành true; vì vậy `1 && 2` và `!0` đều không hợp lệ.
- `^` không phải phép lũy thừa; `Math.pow(...)` là API khác với số thực.

### Gán kết hợp trên boolean

Java còn có các dạng `&=`, `|=`, `^=`. Ví dụ:

~~~java
boolean enabled = false;
enabled ^= true;  // true: toggle.
enabled ^= true;  // false: toggle them lan nua.
~~~

`x ^= true` là một cách đảo `boolean`, nhưng `x = !x` thường rõ ràng hơn khi mục đích chỉ là phủ định trạng thái. Với biểu thức vế trái phức tạp, gán kết hợp có quy tắc đánh giá vế trái một lần; không tự thay mã bằng phép gán hai lần đọc vế trái có tác dụng phụ.

## 10. Bẫy thường gặp

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “`&` và `&&` giống hệt nhau trên boolean.” | Bảng chân trị giống, nhưng `&` **không ngắn mạch** |
| “`|` và `||` đều bỏ qua vế phải khi vế trái true.” | Chỉ `||` bỏ qua |
| “`^` đúng nếu ít nhất một điều kiện true.” | `^` đúng khi **chính xác một** điều kiện true |
| “`^` là lũy thừa số nguyên.” | `^` là XOR boolean/bit |
| “`false & risky()` an toàn vì kết quả đã false.” | `risky()` vẫn được đánh giá |
| “`true | risky()` không cần chạy risky.” | Vế phải vẫn chạy |
| “`Boolean null` tự coi là false.” | Unboxing null gây `NullPointerException` |
| “`a | b & c` luôn nhóm như `(a | b) & c`.” | Thực ra `a | (b & c)` |
| “Dùng `&` thay `&&` giúp null guard.” | Có thể gây lỗi do đánh giá vế phải |

## 11. Bài tập tự kiểm tra

1. `true & false`, `true | false`, `true ^ false` cho gì?  
   *Đáp án:* false, true, true.
2. `true ^ true` và `false ^ false` cho gì?  
   *Đáp án:* Cả hai false.
3. `false & check()` có đánh giá `check()` không?  
   *Đáp án:* Có nếu vế trái hoàn thành bình thường; kết quả AND vẫn false nếu `check()` trả bình thường.
4. `false && check()` có đánh giá `check()` không?  
   *Đáp án:* Không.
5. `true | check()` khác `true || check()` thế nào?  
   *Đáp án:* `|` đánh giá `check()`, `||` bỏ qua.
6. Với `String s = null`, tại sao `s != null & !s.isBlank()` không an toàn?  
   *Đáp án:* `&` vẫn đánh giá lời gọi `isBlank()` trên null.
7. `true | false & false` cho giá trị nào?  
   *Đáp án:* true, vì `&` ưu tiên hơn `|`.
8. `Boolean b = null; boolean x = true ^ b;` có kết quả gì?  
   *Đáp án:* Ném `NullPointerException` khi unbox.
9. `boolean x = false; x ^= true; x ^= true;` cuối cùng `x` bằng gì?  
   *Đáp án:* false.

## Checklist

- [ ] Biết `&` là AND, `|` là OR, `^` là XOR với boolean.
- [ ] Nhớ `^` chỉ true khi **chính xác một** toán hạng true.
- [ ] Hiểu `&`, `|` và `^` **đánh giá cả hai vế**, không ngắn mạch.
- [ ] Phân biệt với `&&` và `||`, đặc biệt trong null guard và exception.
- [ ] Chú ý `Boolean` null có thể ném `NullPointerException` do unboxing.
- [ ] Biết độ ưu tiên `!` rồi `&`, `^`, `|`, `&&`, `||`.
- [ ] Phân biệt toán tử boolean với bitwise trên số nguyên.
- [ ] Không dùng XOR như phép lũy thừa hoặc OR thông thường.

**Kết luận:** Trong biểu thức `boolean` Java, `&`, `|` và `^` lần lượt thực hiện AND, OR, XOR. Điểm cốt lõi là **không ngắn mạch**, khác `&&`/`||`. Chọn đúng toán tử theo **ý nghĩa điều kiện** và **nhu cầu đánh giá vế phải** để tránh những lỗi khó nhận ra với tác dụng phụ, null và ngoại lệ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="logic-and-conditions-005">Phân biệt &amp;&amp; với &amp; và || với |</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt `&&` với `&` và `||` với `|` — Java 17+

## 1. Vấn đề cần nắm

Trong Java, `&&` và `||` là **toán tử logic ngắn mạch** (*short-circuit*). `&` và `|` cũng có thể thao tác trên `boolean`, nhưng **không ngắn mạch**: vế phải vẫn được đánh giá dù kết quả logic đã được xác định. Khi dùng với kiểu số nguyên, `&` và `|` còn mang ý nghĩa **bitwise**, không phải phép so điều kiện.

~~~java
boolean a = false;
boolean b = true;

System.out.println(a && b); // false
System.out.println(a & b);  // false
System.out.println(b || a); // true
System.out.println(b | a);  // true
~~~

Bốn kết quả trên **giống nhau về giá trị logic**. Sự khác biệt quan trọng không nằm ở bảng chân trị mà ở **vế phải có được đánh giá hay không**. Vế phải có thể gọi method, sửa trạng thái, tốn tài nguyên hoặc ném ngoại lệ.

**Mục tiêu bài học:** biết chọn đúng toán tử, giải thích thứ tự thực thi, bảo vệ truy cập `null`/mảng, nhận diện side effect, hiểu bitwise trên kiểu nguyên và tránh lỗi do dấu ngoặc/độ ưu tiên.

## 2. Bảng so sánh nhanh

| Toán tử | Toán hạng hợp lệ tiêu biểu | Khi nào bỏ qua vế phải? | Ý nghĩa |
|---|---|---|---|
| `&&` | `boolean` / `Boolean` qua unboxing | Khi vế trái `false` | AND ngắn mạch |
| `&` | `boolean` hoặc số nguyên | **Không bao giờ** bỏ qua theo short-circuit | AND boolean không ngắn mạch hoặc AND từng bit |
| `||` | `boolean` / `Boolean` qua unboxing | Khi vế trái `true` | OR ngắn mạch |
| `|` | `boolean` hoặc số nguyên | **Không bao giờ** bỏ qua theo short-circuit | OR boolean không ngắn mạch hoặc OR từng bit |

**Trong bài này, “không ngắn mạch” nghĩa là** khi đã tới phép toán đó trong luồng thực thi bình thường, Java đánh giá cả hai toán hạng theo thứ tự từ trái sang phải. Nếu đánh giá vế trái đã ném exception, quá trình vẫn dừng; không có quy tắc nào buộc vế phải phải chạy sau một exception.

`&&` và `||` **không áp dụng cho hai toán hạng số nguyên**:

~~~java
int x = 6, y = 3;
int bits = x & y; // Hợp lệ
// int invalid = x && y; // Lỗi compile: toán hạng không phải boolean
~~~

Java không coi số nguyên 0 là `false` và số nguyên khác 0 là `true` như trong một số ngôn ngữ khác.

## 3. Quy tắc ngắn mạch của `&&`

`left && right` hoạt động như sau:

1. Đánh giá `left`.
2. Nếu `left` là `false`, **trả `false` ngay**; không đánh giá `right`.
3. Nếu `left` là `true`, đánh giá `right` và lấy kết quả boolean đó.

Ví dụ:

~~~java
int denominator = 0;
boolean safe = (denominator != 0) && (10 / denominator > 2);

System.out.println(safe); // false
~~~

Do `denominator != 0` là false, Java **không thực hiện** `10 / denominator`; không có `ArithmeticException`.

### Nếu đổi sang `&` sẽ khác hẳn

~~~java
int denominator = 0;
// boolean unsafe = (denominator != 0) & (10 / denominator > 2);
// Nếu chạy: ArithmeticException (chia số nguyên cho 0).
~~~

Với `&` trên boolean, vế phải **bắt buộc được đánh giá** khi vế trái đã hoàn tất bình thường, kể cả khi vế trái false. Đừng coi `&` là cách viết ngắn của `&&`.

## 4. Quy tắc ngắn mạch của `||`

`left || right` hoạt động như sau:

1. Đánh giá `left`.
2. Nếu `left` là `true`, **trả `true` ngay**; không đánh giá `right`.
3. Nếu `left` là `false`, đánh giá `right` và lấy kết quả boolean đó.

~~~java
int denominator = 0;
boolean invalidOrLarge = (denominator == 0)
        || (10 / denominator > 2);

System.out.println(invalidOrLarge); // true
~~~

Vế trái true, nên Java **bỏ qua phép chia**.

Nếu đổi sang `|`:

~~~java
// boolean unsafe = (denominator == 0) | (10 / denominator > 2);
// Nếu chạy: ArithmeticException.
~~~

**Mẹo nhớ:** `&&` dừng khi thấy false; `||` dừng khi thấy true. `&` và `|` trên boolean không có cơ chế bỏ qua vế phải này.

~~~mermaid
flowchart TD
    A["Đánh giá vế trái của &&"] --> B{"left là true?"}
    B -- "Không" --> C["Trả false; bỏ qua right"]
    B -- "Có" --> D["Đánh giá right"]
    E["Đánh giá vế trái của ||"] --> F{"left là true?"}
    F -- "Có" --> G["Trả true; bỏ qua right"]
    F -- "Không" --> H["Đánh giá right"]
    I["& hoặc | trên boolean"] --> J["Đánh giá left rồi right"]
    J --> K["Tính AND/OR từ hai kết quả"]
~~~

## 5. `&` và `|` trên boolean có cùng bảng chân trị

Nếu **cả hai toán hạng là boolean thuần**, không có side effect và không ném lỗi, `&&` và `&` cho cùng giá trị; `||` và `|` cũng vậy.

| `a` | `b` | `a && b` | `a & b` | `a || b` | `a | b` |
|---|---|---|---|---|---|
| false | false | false | false | false | false |
| false | true | false | false | true | true |
| true | false | false | false | true | true |
| true | true | true | true | true | true |

Vì sao không nên thay bừa?

~~~java
boolean result = false && doWork(); // doWork() KHÔNG được gọi
boolean other = false & doWork();   // doWork() ĐƯỢC gọi
~~~

Trong code thật, `doWork()` có thể cập nhật counter, gọi API bên ngoài hoặc phát sinh exception. Hai lệnh không có cùng hành vi, dù cùng cho giá trị false khi method chạy bình thường.

**Chọn ngắn mạch theo mặc định** khi viết điều kiện thông thường. Chỉ chọn `&`/`|` boolean khi **chủ ý yêu cầu đánh giá cả hai vế** và đã xem xét tác dụng phụ, chi phí và ngoại lệ.

## 6. Thứ tự đánh giá và side effect

Java đánh giá vế trái trước; khi phải đánh giá vế phải, nó diễn ra **sau vế trái**. Có thể quan sát bằng một method in ra tên:

~~~java
static boolean trace(String label, boolean answer) {
    System.out.println("called " + label);
    return answer;
}
~~~

Minh họa:

~~~java
boolean a = false && trace("skip-and", true);
boolean b = false & trace("eager-and", true);
boolean c = true || trace("skip-or", false);
boolean d = true | trace("eager-or", false);
~~~

Chỉ hai dòng được in:

~~~text
called eager-and
called eager-or
~~~

Nếu vế trái cũng có side effect:

~~~java
boolean result = trace("left", true) & trace("right", false);
~~~

thứ tự là:

~~~text
called left
called right
~~~

rồi kết quả boolean của phép `&` là false. **Không có việc hai vế được tính song song chỉ vì dùng `&` hoặc `|`**.

## 7. Guard cho tham chiếu có thể null

Khi viết `text != null && !text.isBlank()`, Java chỉ gọi `isBlank()` nếu `text` không null. Điều kiện đối xứng `text == null || text.isBlank()` cũng an toàn: nếu giá trị null, vế phải của `||` không chạy.

~~~java
String text = null;
boolean hasText = text != null && !text.isBlank();  // false
boolean missing = text == null || text.isBlank();    // true
~~~

Nếu thay `&&` bằng `&`, hoặc `||` bằng `|`, lời gọi trên null vẫn diễn ra và có thể gây `NullPointerException`. Với `Boolean enabled = null`, phép unboxing cũng có rủi ro tương tự; `enabled != null && enabled` hoặc `Boolean.TRUE.equals(enabled)` diễn đạt rõ chính sách cho phép true. Bài **Dùng ngắn mạch để kiểm tra null và giới hạn mảng** hướng dẫn các guard nhiều tầng; ở đây trọng tâm là **sự khác biệt về đánh giá giữa hai cặp toán tử**.

## 8. Mảng: thứ tự guard thay đổi hành vi

Một chỉ số hợp lệ phải không âm và nhỏ hơn độ dài mảng; với mảng nullable, kiểm tra tham chiếu trước:

~~~java
static boolean positiveAt(int[] array, int index) {
    return array != null
            && index >= 0
            && index < array.length
            && array[index] > 0;
}
~~~

Viết các dấu `&` thay cho `&&` khiến Java có thể đánh giá `array.length` ngay cả khi mảng null, hoặc `array[index]` dù chỉ số ngoài miền, rồi ném ngoại lệ. Ví dụ này đủ để so sánh toán tử; kiểm tra phần tử nullable, mảng hai chiều, các ca biên và cách chọn guard clause được thực hành trong bài riêng ở hàng về **null và giới hạn mảng**.

## 9. Khi nào thật sự muốn đánh giá cả hai vế?

Đôi khi hai phép kiểm tra **độc lập** cần được thực hiện dù kiểm tra đầu không đạt, ví dụ ghi nhận đầy đủ lỗi đầu vào:

~~~java
boolean hasName = validateName();
boolean hasEmail = validateEmail();
boolean allValid = hasName && hasEmail;
~~~

Hai method được gọi **một lần mỗi method**, sau đó `&&` chỉ nối hai giá trị boolean đã tính. Cách này thường **rõ chủ đích hơn**:

~~~java
boolean allValid = validateName() & validateEmail();
~~~

Dòng thứ hai có thể hợp lệ nếu API thật sự muốn **cả hai method chạy**, nhưng kém rõ ràng đối với người đọc vốn quen `&` như toán tử bitwise. Khi việc chạy đầy đủ quan trọng, cách tách method thành các câu lệnh giúp bảo trì, test và log dễ hơn.

**Cẩn thận:** Nếu `validateName()` ném exception, Java không tiếp tục gọi `validateEmail()` trong **cả hai mẫu** trên nếu không có xử lý exception thích hợp. “Không ngắn mạch” không đồng nghĩa “luôn gọi đủ hai method bất kể lỗi”.

## 10. `&` và `|` còn là toán tử bitwise trên số nguyên

Khác với `&&`/`||`, toán tử `&`/`|` cũng có ý nghĩa bitwise với `byte`, `short`, `char`, `int`, `long` (sau numeric promotion).

Ví dụ:

~~~java
int a = 0b1100; // 12
int b = 0b1010; // 10

System.out.println(a & b); // 8: 1000
System.out.println(a | b); // 14: 1110
~~~

Biểu diễn:

~~~text
  1100 (12)
& 1010 (10)
= 1000 (8)

  1100 (12)
| 1010 (10)
= 1110 (14)
~~~

- Bitwise AND `&`: bit kết quả là 1 **khi cả hai bit ở cùng vị trí là 1**.
- Bitwise OR `|`: bit kết quả là 1 **khi ít nhất một bit ở vị trí đó là 1**.

Một ứng dụng thực tế là kiểm tra flag:

~~~java
int flags = 0b1100;
boolean hasRead = (flags & 0b0100) != 0;
System.out.println(hasRead); // true
~~~

**Dấu ngoặc cần thiết để thể hiện ý định:** `(flags & 0b0100) != 0` là phép AND bit trước rồi so khác 0. Dùng `&&` trực tiếp giữa hai số nguyên sẽ lỗi compile.

## 11. Độ ưu tiên toán tử và dấu ngoặc

Các toán tử có **độ ưu tiên khác nhau**. Với các nhóm đang học, theo thứ tự từ cao xuống thấp:

| Nhóm toán tử | Ví dụ | Ghi chú |
|---|---|---|
| So sánh bằng | `==`, `!=` | Ưu tiên cao hơn bitwise AND/OR |
| Bitwise/boolean AND | `&` | Ưu tiên cao hơn `^` và `|` |
| Bitwise/boolean XOR | `^` | Nằm giữa `&` và `|` |
| Bitwise/boolean OR | `|` | Ưu tiên cao hơn `&&` |
| AND ngắn mạch | `&&` | Ưu tiên cao hơn `||` |
| OR ngắn mạch | `||` | Thấp hơn `&&` |

Ví dụ:

~~~java
boolean a = true;
boolean b = false;
boolean c = false;

System.out.println(a || b && c);   // true: a || (b && c)
System.out.println((a || b) && c); // false
~~~

**Đặt ngoặc theo yêu cầu nghiệp vụ** khi trộn `&&` và `||`, hoặc `&`, `|` với so sánh số. Dấu ngoặc không tự biến `&` thành ngắn mạch và cũng không xóa side effect.

Lưu ý toán tử `!` (phủ định) áp dụng trên boolean: `!(a && b)` tương đương `!a || !b` về kết quả logic. Tuy nhiên, khi các toán hạng có side effect, **cần giữ thứ tự và số lần đánh giá** khi refactor.

## 12. Phép gán kết hợp `&=` và `|=` cũng không ngắn mạch

Trên biến `boolean`:

~~~java
boolean good = false;
good &= expensiveCheck(); // expensiveCheck() vẫn chạy

boolean ready = true;
ready |= expensiveCheck(); // expensiveCheck() vẫn chạy
~~~

`&=`/`|=` là **compound assignment** ứng với `&`/`|`, không phải `&&`/`||`.

Java **không có** các toán tử `&&=` hoặc `||=`.

Nếu mục tiêu là giữ ngắn mạch và cập nhật biến:

~~~java
good = good && expensiveCheck();
ready = ready || expensiveCheck();
~~~

Tuy nhiên, đây là **thay đổi ngữ nghĩa** nếu code gốc cần thực hiện `expensiveCheck()` dù giá trị `good`/ `ready` đã quyết định kết quả. Chỉ refactor sau khi xác nhận điều đó.

## 13. Tách việc cập nhật trạng thái khỏi guard

Toán tử ngắn mạch không tự sửa lỗi khi guard **thay đổi chính chỉ số đang kiểm tra**. Với `index = 1` và `values.length = 2`, phép `index++ < values.length` đúng rồi tăng `index` lên 2, khiến lần đọc `values[index]` tiếp theo vượt miền.

~~~java
int[] values = {10, 20};
int index = 1;
boolean valid = index >= 0 && index < values.length;
if (valid) {
    int current = values[index];
    index++; // Tách thay đổi khỏi điều kiện bảo vệ.
}
~~~

Guard cũng không tạo tính nguyên tử trước thay đổi từ thread khác; khi chia sẻ dữ liệu, cần đồng bộ hoặc snapshot đúng cách. Trọng tâm so sánh ở đây là **trình tự và số lần đánh giá**, không chỉ bảng chân trị.

## 14. Chương trình Java 17 hoàn chỉnh, chạy độc lập

Lưu file đúng tên **`ShortCircuitVsEagerDemo.java`**:

~~~java
public final class ShortCircuitVsEagerDemo {
    private ShortCircuitVsEagerDemo() {}

    static boolean trace(String label, boolean answer) {
        System.out.println("called " + label);
        return answer;
    }

    static boolean positiveAt(int[] array, int index) {
        return array != null
                && index >= 0
                && index < array.length
                && array[index] > 0;
    }

    public static void main(String[] args) {
        boolean leftFalse = false;
        boolean rightTrue = true;

        System.out.println("AND short=" + (leftFalse && rightTrue));
        System.out.println("AND eager=" + (leftFalse & rightTrue));
        System.out.println("OR short=" + (rightTrue || leftFalse));
        System.out.println("OR eager=" + (rightTrue | leftFalse));

        boolean skipAnd = false && trace("skip-and", true);
        System.out.println("skip &&=" + skipAnd);

        boolean eagerAnd = false & trace("eager-and", true);
        System.out.println("eager &=" + eagerAnd);

        boolean skipOr = true || trace("skip-or", false);
        System.out.println("skip ||=" + skipOr);

        boolean eagerOr = true | trace("eager-or", false);
        System.out.println("eager |=" + eagerOr);

        boolean both = trace("left", true)
                & trace("right", false);
        System.out.println("both evaluated=" + both);

        int zero = 0;
        boolean safeAnd = zero != 0 && 10 / zero > 2;
        boolean safeOr = zero == 0 || 10 / zero > 2;
        System.out.println("safe division &&=" + safeAnd);
        System.out.println("safe division ||=" + safeOr);

        try {
            boolean invalid = zero != 0 & 10 / zero > 2;
            System.out.println(invalid);
        } catch (ArithmeticException ex) {
            System.out.println("eager division=ArithmeticException");
        }

        String text = null;
        System.out.println("safe null &&="
                + (text != null && !text.isBlank()));
        System.out.println("safe null ||="
                + (text == null || text.isBlank()));
        try {
            boolean invalid = text != null & !text.isBlank();
            System.out.println(invalid);
        } catch (NullPointerException ex) {
            System.out.println("eager null=NullPointerException");
        }

        Boolean boxed = null;
        System.out.println("nullable Boolean="
                + (boxed != null && boxed));
        System.out.println("boxed skipped &&=" + (false && boxed));
        System.out.println("boxed skipped ||=" + (true || boxed));
        try {
            boolean invalid = false & boxed;
            System.out.println(invalid);
        } catch (NullPointerException ex) {
            System.out.println("eager unboxing=NullPointerException");
        }

        int[] scores = {7, 0};
        System.out.println("array[0]=" + positiveAt(scores, 0));
        System.out.println("array end=" + positiveAt(scores, 2));
        System.out.println("array null=" + positiveAt(null, 0));

        try {
            boolean invalid = scores != null & scores[99] > 0;
            System.out.println(invalid);
        } catch (ArrayIndexOutOfBoundsException ex) {
            System.out.println("eager array=ArrayIndexOutOfBoundsException");
        }

        int first = 0b1100;
        int second = 0b1010;
        System.out.println("bitwise &=" + (first & second));
        System.out.println("bitwise |=" + (first | second));
        System.out.println("flag present="
                + ((first & 0b0100) != 0));

        boolean a = true, b = false, c = false;
        System.out.println("precedence default=" + (a || b && c));
        System.out.println("precedence grouped=" + ((a || b) && c));

        boolean compoundAnd = false;
        compoundAnd &= trace("compound-and", true);
        System.out.println("compound &=" + compoundAnd);

        boolean compoundOr = true;
        compoundOr |= trace("compound-or", false);
        System.out.println("compound |=" + compoundOr);
    }
}
~~~

**Yêu cầu:** JDK 17 trở lên, terminal ở thư mục chứa file. Không cần Maven, Gradle hay thư viện ngoài JDK.

**Bash (Linux/macOS):**

~~~bash
javac --release 17 -encoding UTF-8 ShortCircuitVsEagerDemo.java
java -cp . ShortCircuitVsEagerDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\ShortCircuitVsEagerDemo.java
java -cp . ShortCircuitVsEagerDemo
~~~

**Exact expected stdout:**

~~~text
AND short=false
AND eager=false
OR short=true
OR eager=true
skip &&=false
called eager-and
eager &=false
skip ||=true
called eager-or
eager |=true
called left
called right
both evaluated=false
safe division &&=false
safe division ||=true
eager division=ArithmeticException
safe null &&=false
safe null ||=true
eager null=NullPointerException
nullable Boolean=false
boxed skipped &&=false
boxed skipped ||=true
eager unboxing=NullPointerException
array[0]=true
array end=false
array null=false
eager array=ArrayIndexOutOfBoundsException
bitwise &=8
bitwise |=14
flag present=true
precedence default=true
precedence grouped=false
called compound-and
compound &=false
called compound-or
compound |=true
~~~

### Đối chiếu ý nghĩa của output

1. Bốn dòng đầu chứng minh `&&`/`&`, `||`/`|` có **cùng bảng chân trị** trên boolean thuần.
2. Không có `called skip-and` hoặc `called skip-or`: vế phải của `&&`/`||` đã bị bỏ qua.
3. `called eager-and` và `called eager-or` xuất hiện: `&`/`|` boolean vẫn đánh giá vế phải.
4. `called left` trước `called right`: hai toán hạng của phép `&` được đánh giá từ trái sang phải.
5. Guard chia nguyên và null tránh exception; dùng eager boolean thì exception xuất hiện như in ra.
6. Wrapper `Boolean` null được bỏ qua bởi ngắn mạch nhưng gây NPE nếu buộc phải unbox.
7. Mảng truy cập an toàn nhờ đủ ba điều kiện; toán tử `&` với chỉ số ngoài miền gây exception.
8. `0b1100 & 0b1010 = 8`; `0b1100 | 0b1010 = 14`; đây là **bitwise**, không phải boolean short-circuit.
9. Hai dòng precedence khác nhau vì dấu ngoặc thay đổi nhóm logic.
10. Compound `&=`/`|=` vẫn gọi method ở vế phải, kể cả khi giá trị biến đã quyết định kết quả.

## 15. Lỗi thường gặp và cách xử lý

| Lỗi / ngộ nhận | Vì sao sai? | Hướng xử lý |
|---|---|---|
| `&&` và `&` boolean giống nhau trong mọi trường hợp | `&` đánh giá cả vế phải | Dùng `&&` nếu cần short-circuit |
| `||` và `|` boolean giống nhau trong mọi trường hợp | `|` đánh giá cả vế phải | Dùng `||` nếu cần short-circuit |
| `obj != null & obj.ready()` | Gọi method trên null | `obj != null && obj.ready()` |
| `array == null | array.length == 0` | Có thể đọc length của null | `array == null || array.length == 0` |
| `i < array.length && array[i] > 0` | Chưa kiểm tra `i >= 0` | Thêm guard chỉ số âm |
| Dùng `&` để buộc cả hai validation chạy, nhưng không xét exception | Exception vế trái vẫn dừng toàn biểu thức | Gọi từng validation có xử lý lỗi phù hợp |
| Nghĩ `&`/`|` chạy hai vế đồng thời | Java đánh giá tuần tự từ trái sang phải | Không giả định parallel |
| `int result = a && b;` | `&&` không thao tác trên int | `a & b` nếu mục tiêu là bitwise |
| `flags & MASK != 0` viết không ngoặc | Dễ hiểu nhầm precedence; có thể lỗi kiểu | `(flags & MASK) != 0` |
| `a || b && c` xem như `(a || b) && c` | `&&` ưu tiên cao hơn `||` | Dùng ngoặc theo ý định |
| `Boolean flag = null; false & flag` | Vế phải bị unbox dù vế trái false | Dùng `false && flag` nếu thật sự muốn skip |
| `flag &= check()` là ngắn mạch | `&=` không short-circuit | `flag = flag && check()` khi phù hợp |
| Đưa `index++` vào điều kiện guard | Chỉ số đổi trước lần đọc sau | Tách cập nhật thành câu lệnh riêng |
| Tin rằng short-circuit tạo thread safety | Guard không đồng bộ trạng thái giữa thread | Có chính sách đồng bộ/ownership |

## 16. Bài tập có đáp án

### Bài A — null guard

~~~java
String text = null;
boolean result = text != null && text.length() > 0;
System.out.println(result);
~~~

**Đáp án:** `false`, không ném NPE. Nếu đổi `&&` thành `&`, vế phải vẫn chạy và có thể ném NPE.

### Bài B — OR ngắn mạch

~~~java
int divisor = 0;
boolean invalid = divisor == 0 || 10 / divisor > 2;
System.out.println(invalid);
~~~

**Đáp án:** `true`; phép chia không chạy. Với `|`, phép chia sẽ được đánh giá và ném `ArithmeticException`.

### Bài C — side effect

~~~java
int[] calls = {0};
boolean result = false & (++calls[0] > 0);
System.out.println(result);
System.out.println(calls[0]);
~~~

**Đáp án:**

~~~text
false
1
~~~

Dù vế trái false, `&` vẫn tăng bộ đếm. Nếu thay bằng `&&`, kết quả false nhưng bộ đếm là 0.

### Bài D — bitwise

~~~java
int a = 0b0101;
int b = 0b0011;
System.out.println(a & b);
System.out.println(a | b);
~~~

**Đáp án:**

~~~text
1
7
~~~

Đây là phép toán trên **các bit**, không phải logic `&&`/`||`.

### Bài E — compound assignment

~~~java
int[] calls = {0};
boolean ready = true;
ready |= (++calls[0] > 0);
System.out.println(ready);
System.out.println(calls[0]);
~~~

**Đáp án:**

~~~text
true
1
~~~

`|=` vẫn đánh giá vế phải. Nếu chỉ muốn tăng khi `ready` false, có thể xem xét `ready = ready || (++calls[0] > 0);`, nhưng phải bảo đảm việc thay đổi side effect là đúng yêu cầu.

### Bài F — chọn cách kiểm tra đủ hai trường

Yêu cầu chạy cả `validateName()` và `validateEmail()` để thu thập thông báo lỗi từ mỗi hàm. Nên viết gì?

**Một đáp án dễ đọc:**

~~~java
boolean nameValid = validateName();
boolean emailValid = validateEmail();
boolean valid = nameValid && emailValid;
~~~

Hai lời gọi độc lập đều chạy khi thực hiện bình thường; biểu thức cuối chỉ tổng hợp boolean. Nếu từng validator có thể ném exception, cần xử lý exception theo hợp đồng để bảo đảm mục tiêu thu thập đủ lỗi.

## 17. Checklist và kết luận

- [ ] Biết `&&` bỏ qua vế phải khi trái false, còn `||` bỏ qua vế phải khi trái true.
- [ ] Biết `&`/`|` trên boolean **luôn đánh giá vế phải khi phép toán được thực hiện bình thường**.
- [ ] Phân biệt cùng **kết quả bảng chân trị** với khác **side effect/exception/chi phí**.
- [ ] Biết Java đánh giá vế trái trước vế phải, không tự chạy song song.
- [ ] Dùng `&&` cho null guard, index guard và phép toán có thể lỗi.
- [ ] Dùng `||` cho điều kiện loại trừ: null hoặc giá trị không hợp lệ.
- [ ] Tránh dùng `&`/`|` boolean thay toán tử ngắn mạch trong guard.
- [ ] Nhớ `&`/`|` còn là bitwise trên kiểu số nguyên.
- [ ] Dùng ngoặc khi kết hợp bitwise với so sánh và khi trộn `&&`/`||`.
- [ ] Biết `&=`/`|=` không ngắn mạch; Java không có `&&=`/`||=`.
- [ ] Không dùng side effect như `index++` làm thay đổi trạng thái giữa guard và truy cập.
- [ ] Hiểu short-circuit không thay thế xử lý exception hoặc đồng bộ đa luồng.
- [ ] Chạy `ShortCircuitVsEagerDemo.java` bằng Java 17+ và đối chiếu từng dòng stdout.

**Kết luận:** `&&` và `||` là lựa chọn mặc định cho **điều kiện logic có bảo vệ**, vì Java bảo đảm bỏ qua vế phải khi kết quả đã được quyết định. `&` và `|` trên boolean cho cùng bảng chân trị nhưng **đánh giá cả hai vế**, nên có thể làm thay đổi tác dụng phụ và ngoại lệ; trên số nguyên, chúng là **toán tử bitwise**. Chọn toán tử dựa trên **ngữ nghĩa đánh giá** chứ không chỉ nhìn vào kết quả true/false.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="logic-and-conditions-006">Định luật De Morgan và đơn giản hóa điều kiện</a>

<details>
<summary>Xem chi tiết</summary>

# Định luật De Morgan và đơn giản hóa điều kiện trong Java

> **Java core — language-basics / Logic và điều kiện**  
> **Môi trường:** JDK 17+, một file Java độc lập, không cần thư viện ngoài.  
> **Mục tiêu:** Đổi đúng phủ định của AND/OR, đọc bảng chân trị, viết điều kiện rõ ràng hơn mà không làm sai logic hoặc thứ tự đánh giá ngắn mạch.

## 1. Hai định luật De Morgan

Cho hai biểu thức `boolean` là `P` và `Q`:

~~~text
!(P && Q)  tương đương  !P || !Q
!(P || Q)  tương đương  !P && !Q
~~~

**Quy tắc:** Khi đưa dấu phủ định `!` vào trong ngoặc, hãy **đảo AND thành OR hoặc ngược lại**, đồng thời **phủ định từng vế**.

~~~java
boolean p = true;
boolean q = false;

boolean a = !(p && q); // true.
boolean b = !p || !q;  // true: tương đương a.

boolean c = !(p || q); // false.
boolean d = !p && !q;  // false: tương đương c.
~~~

Không được chỉ đảo từng toán hạng mà giữ nguyên toán tử:

~~~java
// SAI về logic:
// !(p && q)  KHÔNG tương đương  !p && !q.
// !(p || q)  KHÔNG tương đương  !p || !q.
~~~

Ví dụ `!(p && q)` có nghĩa **“không phải cả hai cùng đúng”**; tương đương **“ít nhất một vế sai”**, chứ không phải “cả hai đều sai”.

## 2. Bảng chân trị chứng minh

| `P` | `Q` | `!(P AND Q)` | `!P OR !Q` | `!(P OR Q)` | `!P AND !Q` |
| --- | --- | --- | --- | --- | --- |
| false | false | true | true | true | true |
| false | true | true | true | false | false |
| true | false | true | true | false | false |
| true | true | false | false | false | false |

Hai cột của mỗi cặp luôn bằng nhau với mọi tổ hợp `P`/`Q`. Đó là cơ sở để thay biểu thức mà **không đổi kết quả boolean**.

Có thể mở rộng cho nhiều điều kiện:

~~~java
// Phủ định việc cả ba điều kiện đều đúng:
boolean denied = !(p && q && r);
// Tương đương:
boolean denied2 = !p || !q || !r;

// Phủ định việc ít nhất một điều kiện đúng:
boolean none = !(p || q || r);
// Tương đương:
boolean none2 = !p && !q && !r;
~~~

Trong đoạn mở rộng, `p`, `q`, `r` là các biến boolean đã được khai báo. Đừng quên **ngoặc** khi kết hợp với điều kiện khác; `!` có độ ưu tiên cao hơn `&&` và `||`.

## 3. Áp dụng vào điều kiện nghiệp vụ và kiểm tra phạm vi

Ví dụ một người bị **từ chối** khi không đồng thời đủ tuổi và có vé:

~~~java
int age = 20;
boolean hasTicket = false;

boolean denied = !(age >= 18 && hasTicket);
boolean deniedSimplified = age < 18 || !hasTicket;
// Cả hai cùng true ở ví dụ này.
~~~

Đọc câu thứ hai dễ hơn: **chưa đủ tuổi HOẶC không có vé**.

Với `int x`, điều kiện nằm trong khoảng đóng từ 0 đến 100:

~~~java
boolean inRange = x >= 0 && x <= 100;
boolean outOfRange = !(x >= 0 && x <= 100);
boolean simpler = x < 0 || x > 100;
~~~

Khi `x` là kiểu nguyên, `outOfRange` và `simpler` tương đương. Ngược lại, muốn phủ định điều kiện “nằm ngoài khoảng”, áp dụng De Morgan:

~~~java
boolean inRangeAgain = !(x < 0 || x > 100);
// Với int, tương đương x >= 0 && x <= 100.
~~~

**Lưu ý:** Phép biến đổi `!(x >= 0)` thành `x < 0` chỉ luôn tương đương với **kiểu nguyên** hoặc khi bạn đã loại bỏ `NaN` ở kiểu số thực. Xem mục 6.

## 4. Guard với `null` và điều kiện dễ đọc

De Morgan giúp diễn đạt trường hợp dữ liệu không hợp lệ:

~~~java
String name = null;
boolean valid = name != null && !name.isBlank();
boolean invalid = !(name != null && !name.isBlank());

// Đổi theo De Morgan:
boolean invalidSimplified = name == null || name.isBlank();
~~~

Nếu `name == null`, vế phải `name.isBlank()` ở biểu thức cuối **không được chạy** nhờ `||` ngắn mạch. `invalid` và `invalidSimplified` đều true; `valid` false.

Cách viết này vẫn **giữ điều kiện guard đứng trước** thao tác có thể ném `NullPointerException`. Tránh đổi thứ tự thành:

~~~java
// KHÔNG AN TOÀN khi name == null:
// boolean broken = name.isBlank() || name == null;
~~~

Khi thay đổi hoặc rút gọn điều kiện, mục tiêu không chỉ là ít ký tự hơn mà còn là **diễn đạt đúng nghiệp vụ, dễ bảo trì và bảo vệ điều kiện phụ thuộc**.

## 5. Short-circuit và tác dụng phụ khi áp dụng De Morgan

Trong Java, `&&` bỏ qua vế phải nếu vế trái false; `||` bỏ qua vế phải nếu vế trái true. Hai biến đổi De Morgan dưới đây giữ **thứ tự đánh giá P rồi Q** và cùng điều kiện bỏ qua Q:

~~~java
!(P && Q)  →  !P || !Q
!(P || Q)  →  !P && !Q
~~~

Với các biểu thức `P`/`Q` giống hệt nhau ở hai vế và không có thao tác khác chen vào, cả kết quả lẫn việc vế nào được đánh giá đều giữ nguyên theo quy tắc ngắn mạch.

~~~java
StringBuilder log = new StringBuilder();
boolean result = !(mark(log, "F", false)
        && mark(log, "X", true));
// result=true, log="F": không gọi mark(..., "X", ...).
~~~

Dạng viết lại `!mark(log, "F", false) || !mark(log, "X", true)` cũng không gọi nhánh X.

**Không tự ý đổi vị trí, nhân bản hay loại bỏ một biểu thức có tác dụng phụ**: ví dụ `readValue()`, `i++`, một lệnh ghi log hoặc lời gọi cập nhật trạng thái. Những biến đổi đại số khác ngoài De Morgan có thể cho cùng bảng chân trị nhưng khác số lần thực thi biểu thức con.

Ngoài ra, `&` và `|` trên boolean **không short-circuit**. De Morgan cũng có công thức logic cho chúng, nhưng nếu dùng một ký tự thay `&&`/`||` thì có thể khiến vế phải vốn bị bỏ qua lại được chạy.

## 6. Cẩn thận khi đổi dấu toán tử quan hệ với `double`/`float`

Thường thấy những phép biến đổi như:

~~~text
!(x < 10)   →   x >= 10
!(x >= 0)   →   x < 0
~~~

Với **`int`/`long`**, chúng tương đương. Nhưng với `double` hoặc `float`, `NaN` làm phép đổi dấu trực tiếp **không còn tương đương**:

~~~java
double x = Double.NaN;

boolean negatedLess = !(x < 10.0); // true.
boolean greaterOrEqual = x >= 10.0; // false.

boolean negatedRange = !(x >= 0.0 && x <= 100.0); // true.
boolean outsideByComparisons = x < 0.0 || x > 100.0; // false.
~~~

Các phép quan hệ `<`, `<=`, `>`, `>=` với `NaN` đều false, nên phủ định của một phép quan hệ false là true. **Định luật De Morgan vẫn đúng**; điều sai là thay `!(x >= 0)` bằng `x < 0` mà quên `NaN`.

Nếu nghiệp vụ không chấp nhận giá trị không hữu hạn, hãy quy định điều đó rõ ràng:

~~~java
double x = Double.NaN;
boolean valid = Double.isFinite(x) && x >= 0.0 && x <= 100.0;
boolean invalid = !valid;
// Có thể viết De Morgan:
// !Double.isFinite(x) || !(x >= 0.0) || !(x <= 100.0)
~~~

Với `boolean` nullable kiểu `Boolean`, phủ định `!flag` đòi hỏi unboxing, có thể ném `NullPointerException` nếu `flag == null`. Xác định rõ quy tắc xử lý null trước khi rút gọn.

## 7. Chương trình Java 17+ chạy độc lập

Tạo file **DeMorganDemo.java**:

~~~java
public final class DeMorganDemo {
    private static boolean mark(
            StringBuilder log, String label, boolean result) {
        log.append(label);
        return result;
    }

    public static void main(String[] args) {
        boolean p = true;
        boolean q = false;
        System.out.println("andNot=" + (!(p && q)));
        System.out.println("andDeMorgan=" + (!p || !q));
        System.out.println("orNot=" + (!(p || q)));
        System.out.println("orDeMorgan=" + (!p && !q));

        boolean allEquivalent = true;
        for (boolean a : new boolean[] {false, true}) {
            for (boolean b : new boolean[] {false, true}) {
                allEquivalent &= (!(a && b) == (!a || !b));
                allEquivalent &= (!(a || b) == (!a && !b));
            }
        }
        System.out.println("truthTableEquivalent=" + allEquivalent);

        int age = 20;
        boolean hasTicket = false;
        System.out.println("denied=" + (!(age >= 18 && hasTicket)));
        System.out.println("deniedSimplified=" + (age < 18 || !hasTicket));

        String name = null;
        System.out.println("nameValid="
                + (name != null && !name.isBlank()));
        System.out.println("nameInvalid="
                + (name == null || name.isBlank()));

        StringBuilder andOriginalLog = new StringBuilder();
        boolean originalAnd = !(mark(andOriginalLog, "F", false)
                && mark(andOriginalLog, "X", true));
        System.out.println("andOriginal=" + originalAnd
                + ",trace=" + andOriginalLog);

        StringBuilder andRewriteLog = new StringBuilder();
        boolean rewrittenAnd = !mark(andRewriteLog, "F", false)
                || !mark(andRewriteLog, "X", true);
        System.out.println("andRewrite=" + rewrittenAnd
                + ",trace=" + andRewriteLog);

        StringBuilder orOriginalLog = new StringBuilder();
        boolean originalOr = !(mark(orOriginalLog, "T", true)
                || mark(orOriginalLog, "Y", false));
        System.out.println("orOriginal=" + originalOr
                + ",trace=" + orOriginalLog);

        StringBuilder orRewriteLog = new StringBuilder();
        boolean rewrittenOr = !mark(orRewriteLog, "T", true)
                && !mark(orRewriteLog, "Y", false);
        System.out.println("orRewrite=" + rewrittenOr
                + ",trace=" + orRewriteLog);

        double nan = Double.NaN;
        System.out.println("nanNotLess=" + (!(nan < 10.0)));
        System.out.println("nanGreaterEqual=" + (nan >= 10.0));
        System.out.println("nanNegatedRange="
                + (!(nan >= 0.0 && nan <= 100.0)));
        System.out.println("nanOutsideComparisons="
                + (nan < 0.0 || nan > 100.0));
    }
}
~~~

Biên dịch và chạy ở thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 DeMorganDemo.java
java DeMorganDemo
~~~

**Expected stdout chính xác:**

~~~text
andNot=true
andDeMorgan=true
orNot=false
orDeMorgan=false
truthTableEquivalent=true
denied=true
deniedSimplified=true
nameValid=false
nameInvalid=true
andOriginal=true,trace=F
andRewrite=true,trace=F
orOriginal=false,trace=T
orRewrite=false,trace=T
nanNotLess=true
nanGreaterEqual=false
nanNegatedRange=true
nanOutsideComparisons=false
~~~

`truthTableEquivalent=true` kiểm tra đủ bốn tổ hợp của hai giá trị boolean. Hai cặp trace chứng minh phép đổi De Morgan **không buộc nhánh bị bỏ qua chạy thêm**. Bốn dòng `nan...` chứng minh cảnh báo về đổi dấu bất đẳng thức với `NaN`.

## 8. Quy trình rút gọn và bài tập

Khi gặp một điều kiện nhiều phủ định, hãy làm theo thứ tự:

1. **Nhận diện nhóm trong ngoặc**: `!(P && Q)` hay `!(P || Q)`?
2. **Đảo phép nối** (`&&` ↔ `||`) và **phủ định mỗi toán hạng**.
3. **Đọc lại bằng lời**: “không đồng thời” → “ít nhất một điều kiện không đúng”.
4. **Giữ guard đúng vị trí** và không thay đổi thứ tự/ số lần gọi biểu thức có side effect.
5. **Kiểm tra kiểu và dữ liệu đặc biệt**, nhất là `double NaN`, `Boolean null`, và các ranh giới 0, min, max.

| Dạng ban đầu | Dạng theo De Morgan |
| --- | --- |
| `!(a && b)` | `!a \|\| !b` |
| `!(a \|\| b)` | `!a && !b` |
| `!(x >= 0 && x <= 10)` | `!(x >= 0) \|\| !(x <= 10)` |
| `!(obj != null && obj.ready())` | `obj == null \|\| !obj.ready()` |

**Bài 1.** Viết lại `!(hasAccount && isActive)` bằng OR và hai phủ định; đọc nghĩa bằng tiếng Việt.

**Bài 2.** Viết lại `!(isWeekend || isHoliday)` bằng AND; lập bảng chân trị nhỏ để kiểm chứng.

**Bài 3.** Cho `int x = -1`, so sánh `!(x >= 0 && x <= 10)` với `x < 0 || x > 10`. Hai biểu thức có tương đương khi `x` thuộc kiểu `int` không?

**Bài 4.** Cho `String name = null`. Vì sao `name == null || name.isBlank()` không ném ngoại lệ? Viết lại theo dạng phủ định của một AND.

**Bài 5.** Vì sao với `double x = Double.NaN`, `!(x < 10)` khác `x >= 10`? Sự khác biệt đó có phủ định định luật De Morgan không?

### Ghi nhớ

**De Morgan:** `!(P && Q) == (!P || !Q)` và `!(P || Q) == (!P && !Q)`. Muốn đẩy `!` vào trong ngoặc, phải **đổi AND ↔ OR** và **phủ định từng điều kiện**. Với Java, ưu tiên dễ đọc, giữ short-circuit an toàn và chú ý `NaN` trước khi đổi dấu quan hệ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="logic-and-conditions-007">Toán tử ba ngôi ?: và chỉ đánh giá nhánh được chọn</a>

<details>
<summary>Xem chi tiết</summary>

# Toán tử ba ngôi `?:` và chỉ đánh giá nhánh được chọn — Java 17+

**Toán tử điều kiện `?:`** (*conditional operator*, thường gọi là *toán tử ba ngôi*) chọn **một trong hai biểu thức kết quả** dựa trên giá trị của một điều kiện `boolean`.

~~~java
int score = 85;
String level = score >= 80 ? "Tốt" : "Cần cải thiện";
// level = "Tốt".
~~~

Cú pháp:

~~~text
condition ? expressionIfTrue : expressionIfFalse
    (1)            (2)                 (3)
~~~

- **(1) Điều kiện** được đánh giá trước, phải có kiểu `boolean` hoặc `Boolean` có thể unbox.
- **(2) Nhánh true** chỉ được đánh giá khi điều kiện trả true.
- **(3) Nhánh false** chỉ được đánh giá khi điều kiện trả false.
- **Nhánh không được chọn KHÔNG chạy**: không gọi phương thức, không phát sinh tác dụng phụ và không ném ngoại lệ từ nhánh đó.

Ví dụ `boolean premium = true; int fee = premium ? 0 : 30_000;` làm `fee` nhận 0 mà không cần một khối `if/else` dài.

## 1. Quy tắc đánh giá và bảng kết quả

| Điều kiện | Nhánh được đánh giá | Nhánh bị bỏ qua | Giá trị toàn biểu thức |
| --- | --- | --- | --- |
| `true` | Sau dấu `?` | Sau dấu `:` | Giá trị của nhánh true |
| `false` | Sau dấu `:` | Sau dấu `?` | Giá trị của nhánh false |
| `Boolean null` | Không có | Cả hai | Ném `NullPointerException` khi unbox điều kiện |
| Điều kiện tự ném ngoại lệ | Không có | Cả hai | Biểu thức kết thúc đột ngột theo ngoại lệ |

Giản đồ luồng:

~~~mermaid
flowchart TD
    A["Đánh giá condition"] --> B{"Điều kiện true?"}
    B -- "Có" --> C["Chỉ đánh giá nhánh sau ?"]
    B -- "Không" --> D["Chỉ đánh giá nhánh sau :"]
    C --> E["Giá trị biểu thức ?:"]
    D --> E
~~~

**Khác biệt quan trọng với lời gọi phương thức:** `choose(condition, positive(), negative())` sẽ đánh giá **cả hai đối số** `positive()` và `negative()` trước khi gọi phương thức `choose`. Trong khi đó, `condition ? positive() : negative()` chỉ đánh giá **một** trong hai biểu thức nhánh.

## 2. Demo Java 17+ độc lập và có expected stdout

Tạo file UTF-8 tên **`TernaryBranchDemo.java`**:

~~~java
public class TernaryBranchDemo {
    static boolean test(String label, boolean result) {
        System.out.println("test=" + label);
        return result;
    }

    static int mark(String label, int value) {
        System.out.println("eval=" + label);
        return value;
    }

    static int fail() {
        System.out.println("eval=failing");
        throw new IllegalStateException("boom");
    }

    public static void main(String[] args) {
        boolean premium = true;
        int fee = premium ? 0 : 30_000;
        System.out.println("fee=" + fee);

        boolean accepted = false;
        String status = accepted ? "accepted" : "rejected";
        System.out.println("status=" + status);

        System.out.println("== selected branch ==");
        int first = test("condition-true", true)
                ? mark("true-branch", 10)
                : mark("skipped-false-branch", 20);
        System.out.println("first=" + first);

        int second = test("condition-false", false)
                ? mark("skipped-true-branch", 30)
                : mark("false-branch", 40);
        System.out.println("second=" + second);

        System.out.println("== exception behavior ==");
        int safeTrue = true ? mark("safe-true", 5) : fail();
        System.out.println("safeTrue=" + safeTrue);

        int safeFalse = false ? fail() : mark("safe-false", 6);
        System.out.println("safeFalse=" + safeFalse);

        try {
            int unused = true ? fail() : mark("never", 99);
            System.out.println("unexpected=" + unused);
        } catch (IllegalStateException ex) {
            System.out.println("caught=" + ex.getMessage());
        }

        System.out.println("== types and null ==");
        var mixedNumeric = true ? 1 : 2.5;
        System.out.println("mixedNumeric=" + mixedNumeric);

        String optionalText = false ? "text" : null;
        System.out.println("nullResult=" + (optionalText == null));

        Integer missingNumber = null;
        int safeNumber = true ? 7 : missingNumber;
        System.out.println("safeNumber=" + safeNumber);

        Boolean missingCondition = null;
        try {
            String unexpected = missingCondition ? "yes" : "no";
            System.out.println("unexpected=" + unexpected);
        } catch (NullPointerException ex) {
            System.out.println("nullCondition=NullPointerException");
        }

        System.out.println("== nesting and state ==");
        boolean mainRoute = false;
        boolean backupRoute = true;
        String route = mainRoute
                ? "main"
                : backupRoute ? "backup" : "off";
        System.out.println("route=" + route);

        int counter = 0;
        int choice = (++counter == 1) ? 8 : 9;
        System.out.println("choice=" + choice + ",counter=" + counter);

        int visits = 0;
        int onlyOne = false
                ? (visits += 10)
                : (visits += 1);
        System.out.println("onlyOne=" + onlyOne + ",visits=" + visits);

        double nan = Double.NaN;
        String nanResult = nan > 0.0 ? "positive" : "not-positive";
        System.out.println("nanResult=" + nanResult);

        System.out.println("== grouping ==");
        int grouped = (true ? 2 : 3) + 4;
        System.out.println("grouped=" + grouped);
        boolean allowed = true || false;
        String access = allowed ? "allow" : "deny";
        System.out.println("access=" + access);
    }
}
~~~

Yêu cầu **JDK 17 trở lên**; mở PowerShell (Windows) hoặc Bash (Linux/macOS) tại thư mục chứa file:

~~~bash
java -version
javac -version
javac -encoding UTF-8 TernaryBranchDemo.java
java TernaryBranchDemo
~~~

**Exact expected stdout** từ riêng lệnh `java TernaryBranchDemo`:

~~~text
fee=0
status=rejected
== selected branch ==
test=condition-true
eval=true-branch
first=10
test=condition-false
eval=false-branch
second=40
== exception behavior ==
eval=safe-true
safeTrue=5
eval=safe-false
safeFalse=6
eval=failing
caught=boom
== types and null ==
mixedNumeric=1.0
nullResult=true
safeNumber=7
nullCondition=NullPointerException
== nesting and state ==
route=backup
choice=8,counter=1
onlyOne=1,visits=1
nanResult=not-positive
== grouping ==
grouped=6
access=allow
~~~

Hai lệnh `java -version` và `javac -version` cho đầu ra phụ thuộc máy, không thuộc khối stdout cố định. Ví dụ này được rà soát theo ngữ nghĩa Java 17; có thể dùng các lệnh trên để tự biên dịch và đối chiếu.

## 3. Vì sao chỉ một nhánh được đánh giá?

~~~java
int value = true ? mark("YES", 1) : mark("NO", 2);
~~~

Khi điều kiện true, chương trình chỉ gọi `mark("YES", 1)`, lấy giá trị 1 rồi hoàn tất. Không có lời gọi `mark("NO", 2)`.

Điều này đặc biệt quan trọng với **tác dụng phụ**:

~~~java
int count = 0;
int value = false ? (count += 10) : (count += 1);
// value = 1, count = 1; nhánh +10 hoàn toàn không chạy.
~~~

Và với **ngoại lệ**:

~~~java
int result = true ? 5 : (10 / 0);
// Phép chia ở nhánh không được chọn không được đánh giá.
~~~

Để thể hiện an toàn tại thời điểm chạy, biểu thức chia ở ví dụ trên có thể được thay bằng phép chia với biến mẫu số bằng 0; Java vẫn bỏ qua nhánh đó. Nếu **nhánh được chọn** ném ngoại lệ, toàn biểu thức `?:` kết thúc đột ngột và không sinh giá trị bình thường. Nếu **chính điều kiện** ném ngoại lệ, cả hai nhánh đều chưa được đánh giá.

Đừng dựa vào nhánh bị bỏ qua để thực hiện công việc bắt buộc. Nếu một thao tác phải chạy bất kể điều kiện, hãy đặt nó thành câu lệnh riêng **trước hoặc sau** biểu thức.

## 4. Cả hai nhánh ảnh hưởng đến kiểu kết quả

Dù chỉ một nhánh chạy, **compiler vẫn kiểm tra cả hai nhánh để xác định kiểu** của `?:`. Khi trộn giá trị số, nhánh được chọn có thể được chuyển sang kiểu chung:

~~~java
var mixed = true ? 1 : 2.5;       // double, giá trị 1.0
String note = false ? "text" : null; // tham chiếu null
Integer missing = null;
int safe = true ? 7 : missing;    // 7; không unbox nhánh bỏ qua
~~~

Nếu chọn nhánh `Integer` đang null trong cặp biểu thức số `int`/`Integer`, việc unbox có thể gây `NullPointerException`; điều kiện `Boolean` null cũng lỗi ngay khi chọn nhánh. Với các nhánh tham chiếu, kiểu đích của phép gán đôi khi còn ảnh hưởng đến kiểm tra kiểu. Những quy tắc nâng kiểu, hằng `byte`/`short`, boxing và target typing được phân tích có ví dụ riêng trong bài **Kiểu kết quả của biểu thức điều kiện và chuyển kiểu có thể xảy ra**.

## 5. Độ ưu tiên, tính kết hợp và ternary lồng nhau

`?:` có độ ưu tiên **thấp hơn** nhiều toán tử số học, so sánh và logic `&&`/`||`, nhưng **cao hơn** phép gán `=`.

~~~java
int score = 80;
String rank = score >= 75 && score < 90 ? "good" : "other";
// Dieu kien: (score >= 75 && score < 90).
~~~

Dấu ngoặc thường làm mã rõ ý hơn:

~~~java
String rank = (score >= 75 && score < 90)
        ? "good" : "other";
~~~

**Toán tử điều kiện kết hợp từ phải sang trái** (*right-associative*) trong biểu thức lồng:

~~~java
String route = mainRoute ? "main"
        : backupRoute ? "backup" : "off";
~~~

Nó được nhóm tương đương:

~~~java
String route = mainRoute
        ? "main"
        : (backupRoute ? "backup" : "off");
~~~

Khi `mainRoute` false mới đánh giá điều kiện `backupRoute`. Nếu `mainRoute` true thì toàn bộ phần ternary bên phải bị bỏ qua. Với điều kiện lồng từ ba mức trở lên, **`if/else if/else` thường dễ đọc hơn**.

## 6. Khi nào dùng `?:`, khi nào dùng `if/else`?

Nên dùng `?:` cho **một lựa chọn giá trị ngắn, rõ nghĩa**:

~~~java
int max = a > b ? a : b;
String label = enabled ? "ON" : "OFF";
~~~

Nếu mục đích là nhiều câu lệnh, ghi log, cập nhật trạng thái hoặc bắt exception, dùng `if/else`:

~~~java
String label;
if (enabled) {
    label = "ON";
} else {
    label = "OFF";
}
~~~

Một số khác biệt cần nhớ:

| Đặc điểm | `?:` | `if/else` |
| --- | --- | --- |
| Cấu trúc | **Biểu thức** tạo một giá trị | **Câu lệnh** điều khiển luồng |
| Thích hợp nhất | Chọn một kết quả ngắn | Nhiều thao tác, nhiều nhánh hoặc xử lý lỗi |
| Đánh giá | Chỉ một nhánh được chọn | Chỉ thực hiện khối phù hợp |
| Hai nhánh giá trị | Phải tuân thủ quy tắc kiểu của biểu thức điều kiện | Mỗi nhánh có thể chứa những câu lệnh khác nhau |
| Gọi phương thức `void` ở hai nhánh | Không hợp lệ làm hai biểu thức kết quả của `?:` | Hoàn toàn hợp lệ trong hai khối |

**Sai lầm cú pháp phổ biến:** `?:` không phải là *statement expression* có thể tự đứng thành câu lệnh `condition ? f() : g();`. Nếu chỉ muốn gọi một trong hai phương thức, hãy dùng `if/else`; nếu muốn chọn **giá trị** để gán hoặc truyền cho hàm thì `?:` phù hợp.

## 7. Ngắn mạch của `?:` và `&&`/`||` có gì giống, khác?

Cả ba cơ chế đều có thể **bỏ qua việc đánh giá một phần biểu thức**, nhưng chúng thực hiện nhiệm vụ khác nhau:

| Cấu trúc | Phần được bỏ qua | Kiểu kết quả |
| --- | --- | --- |
| `a && b` | Bỏ `b` khi `a` false | `boolean` |
| `a \|\| b` | Bỏ `b` khi `a` true | `boolean` |
| `cond ? x : y` | Bỏ **một trong hai** nhánh `x`, `y` | Tùy kiểu hai nhánh và ngữ cảnh |

Ví dụ lựa chọn một trong hai phương thức có **giá trị trả về**:

~~~java
int result = valid ? calculateFast() : calculateFallback();
~~~

Khi `valid` true chỉ gọi `calculateFast()`; khi false chỉ gọi `calculateFallback()`. Hai phương thức ở ví dụ mang tính minh họa nên cần được khai báo trong chương trình thực tế; chương trình runnable ở mục 2 dùng các phương thức `mark` và `fail` được định nghĩa đầy đủ.

Không thay bằng `choose(valid, calculateFast(), calculateFallback())` nếu muốn giữ hành vi bỏ qua nhánh: Java đánh giá đối số lời gọi từ trái sang phải, tức **cả hai phương thức tính** có thể chạy trước khi `choose` nhận các giá trị.

## 8. Những lỗi thường gặp

| Ngộ nhận | Thực tế |
| --- | --- |
| “`?:` luôn chạy cả hai nhánh rồi chọn kết quả.” | Chỉ đánh giá nhánh được chọn |
| “Nếu nhánh bị bỏ qua ném exception thì ternary cũng lỗi.” | Nhánh không được chọn không được thực thi |
| “`Boolean null` được hiểu là false.” | Unboxing null ở điều kiện gây `NullPointerException` |
| “Kiểu của ternary luôn là kiểu của nhánh chạy.” | Kiểu quyết định tại compile time, xét hai nhánh và ngữ cảnh |
| “`true ? 1 : 2.5` có kiểu int vì chọn 1.” | Kết quả có kiểu `double`, giá trị `1.0` |
| “`a ? b : c ? d : e` luôn nhóm từ trái.” | `?:` kết hợp từ phải sang trái |
| “Ternary rất dài dễ đọc hơn `if/else`.” | Nhiều mức lồng nhau thường khó kiểm tra |
| “`?:` dùng được cho hai lời gọi `void` như một câu lệnh.” | Không; dùng `if/else` |
| “Tạo hàm `choose(flag, first(), second())` có cùng lazy evaluation.” | Sai; cả hai đối số có thể được đánh giá |

## 9. Bài tập tự kiểm tra

1. `int x = true ? 10 : 20;` cho `x` bằng bao nhiêu?  
   *Đáp án:* 10.
2. `boolean ok = false; String s = ok ? "Y" : "N";` cho chuỗi gì?  
   *Đáp án:* `"N"`.
3. `int n = true ? 5 : (10 / 0);` có thực hiện phép chia không?  
   *Đáp án:* Không; nhánh thứ ba bị bỏ qua.
4. `var x = true ? 1 : 2.5;`, kiểu và giá trị của `x`?  
   *Đáp án:* `double`, `1.0`.
5. `Boolean flag = null; int n = flag ? 1 : 2;` cho kết quả gì?  
   *Đáp án:* Ném `NullPointerException` khi đánh giá điều kiện.
6. `boolean a = false, b = true; String s = a ? "A" : b ? "B" : "C";` cho chuỗi gì?  
   *Đáp án:* `"B"`.
7. Nếu nhánh không được chọn tăng biến đếm, biến đó có thay đổi không?  
   *Đáp án:* Không.
8. Khi phải gọi một trong hai phương thức `void` với logic dài, nên dùng gì?  
   *Đáp án:* `if/else`.

## Checklist

- [ ] Biết cú pháp `condition ? trueExpr : falseExpr` và kiểu điều kiện là boolean.
- [ ] Nhớ **chỉ một nhánh kết quả được đánh giá** sau điều kiện.
- [ ] Biết điều kiện hoặc nhánh được chọn có thể ném ngoại lệ; nhánh không chọn không chạy.
- [ ] Hiểu kiểu toàn biểu thức quyết định khi biên dịch, không phụ thuộc nhánh thực tế lúc chạy.
- [ ] Cẩn thận `Boolean` null và unboxing của nhánh số wrapper.
- [ ] Hiểu `?:` kết hợp từ phải sang trái và nên thêm dấu ngoặc khi lồng.
- [ ] Dùng `?:` cho chọn giá trị ngắn, `if/else` cho luồng xử lý phức tạp.
- [ ] Không nhầm `?:` với một lời gọi hàm nhận cả hai kết quả đã tính.

**Kết luận:** `?:` giúp biểu diễn ngắn gọn lựa chọn giữa hai giá trị, nhưng đặc điểm quan trọng nhất là **chỉ đánh giá nhánh được chọn**. Khi hiểu thêm **kiểu biểu thức**, **null**, **ngoại lệ** và **cách nhóm biểu thức lồng**, bạn có thể dùng ternary đúng và dễ đọc trong Java 17+.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="logic-and-conditions-008">Kiểu kết quả của biểu thức điều kiện và chuyển kiểu có thể xảy ra</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu kết quả của biểu thức điều kiện và chuyển kiểu có thể xảy ra — Java 17+

## 1. Toán tử `?:` không chỉ chọn một trong hai giá trị

Biểu thức điều kiện (*conditional expression*, thường gọi toán tử ba ngôi) có cú pháp:

~~~java
condition ? whenTrue : whenFalse
~~~

Java **đánh giá `condition` trước**, rồi **chỉ đánh giá nhánh được chọn**. Đồng thời, compiler xác định **kiểu của toàn biểu thức** theo quy tắc của Java: kiểu đó không nhất thiết giống kiểu của giá trị ở nhánh đang chạy.

~~~java
boolean chooseFirst = true;
double value = chooseFirst ? 10 : 2.5;

System.out.println(value); // 10.0
~~~

Nhánh `10` là literal `int`, nhánh `2.5` là literal `double`. Vì đây là **biểu thức điều kiện số**, Java suy ra kiểu kết quả `double` và chuyển 10 thành `10.0`. Việc nhánh `2.5` **không chạy** không ngăn kiểu của nó ảnh hưởng đến kiểu chung.

**Phân biệt ba câu hỏi:**

1. **Nhánh nào được chạy?** Phụ thuộc kết quả boolean của `condition`.
2. **Kiểu biểu thức là gì?** Do quy tắc kiểu tại compile time; cần xem cả hai nhánh và đôi khi cả ngữ cảnh đích.
3. **Có chuyển kiểu nào?** Có thể gồm numeric promotion, narrowing đặc biệt, boxing/unboxing hoặc reference conversion.

## 2. Ba nhóm biểu thức điều kiện

Java phân loại `?:` theo kiểu của hai nhánh:

| Nhóm | Nhánh 2 và 3 | Ví dụ | Đặc điểm |
|---|---|---|---|
| **Boolean conditional** | `boolean`/`Boolean` phù hợp | `flag ? true : false` | Kết quả logic; có thể unbox wrapper |
| **Numeric conditional** | Hai kiểu số hoặc wrapper số phù hợp | `flag ? 1 : 2L` | Có quy tắc chọn kiểu số và chuyển kiểu |
| **Reference conditional** | Các trường hợp reference/null thích hợp | `flag ? "OK" : null` | Có thể chịu **target typing** trong ngữ cảnh gán/lời gọi |

Java 17 có các quy tắc chi tiết theo cặp kiểu cụ thể; **không thể luôn nói “toán tử `?:` lấy kiểu lớn hơn”**. Với `byte`/`short` và hằng `int`, có ngoại lệ quan trọng. Với kiểu tham chiếu, ngữ cảnh đích cũng có thể quyết định việc biểu thức có hợp lệ không.

~~~mermaid
flowchart TD
    A["condition ? expr1 : expr2"] --> B{"Hai nhánh là boolean/Boolean?"}
    B -- "Có" --> C["Boolean conditional expression"]
    B -- "Không" --> D{"Hai nhánh là kiểu số phù hợp?"}
    D -- "Có" --> E["Numeric conditional expression"]
    D -- "Không" --> F["Reference conditional expression"]
    C --> G["Kiểm tra chọn nhánh và unboxing có thể xảy ra"]
    E --> H["Kiểm tra numeric promotion/narrowing đặc biệt"]
    F --> I["Kiểm tra kiểu tham chiếu và target typing"]
~~~

**Điều kiện đầu** phải có kiểu `boolean` hoặc `Boolean` có thể unbox. Java không tự coi một số khác 0 là true:

~~~java
// int number = 1;
// int result = number ? 10 : 20; // LỖI COMPILE: condition không là boolean
~~~

## 3. Chỉ nhánh được chọn mới được đánh giá

~~~java
static int trace(String label, int value) {
    System.out.println("evaluated " + label);
    return value;
}
~~~

Nếu:

~~~java
boolean flag = true;
int selected = flag
        ? trace("true", 7)
        : trace("false", 9);

System.out.println(selected);
~~~

**Expected stdout:**

~~~text
evaluated true
7
~~~

Nhánh false **không chạy**: không gọi method, không phát sinh side effect hoặc exception trong nhánh đó.

Tuy nhiên, compiler vẫn **kiểm tra kiểu cả hai nhánh**. Một nhánh không bao giờ chạy theo dữ liệu ví dụ vẫn không được chứa biểu thức sai kiểu:

~~~java
// int wrong = true ? 10 : "text";
// Không thể gán reference conditional này cho int theo quy tắc kiểu Java.
~~~

**Lưu ý:** việc loại bỏ nhánh runtime không giống loại bỏ nhánh khỏi kiểm tra kiểu tại compile time.

## 4. Kiểu số thông thường: `int`, `long`, `float`, `double`

Với hai nhánh số phổ biến:

~~~java
boolean flag = true;

var first = flag ? 10 : 20;    // int
var second = flag ? 10 : 20L;  // long
var third = flag ? 10 : 2.5;   // double
var fourth = flag ? 10 : 2.5f; // float
~~~

| Hai nhánh | Kiểu biểu thức | Giá trị khi `flag == true` |
|---|---|---|
| `10` và `20` | `int` | `10` |
| `10` và `20L` | `long` | `10L` |
| `10` và `2.5f` | `float` | `10.0f` |
| `10` và `2.5` | `double` | `10.0` |

Với những cặp kiểu này, chuyển kiểu số học thích hợp làm hai nhánh tương thích với kiểu kết quả.

**Đừng suy kiểu bằng kiểu biến nhận:** ví dụ dưới đây có hai bước khác nhau:

~~~java
double d = flag ? 1 : 2;
~~~

Bản thân `flag ? 1 : 2` là biểu thức số kiểu **`int`**; phép gán sau đó mở rộng giá trị sang `double`. Ngược lại, `flag ? 1 : 2.0` có kiểu **`double` ngay ở biểu thức điều kiện**.

## 5. Ngoại lệ đặc biệt: `byte`/`short` và hằng `int` vừa miền

Java có quy tắc đặc biệt khi một nhánh là `byte`, `short` hoặc wrapper tương ứng và nhánh kia là **hằng `int` biểu diễn được trong kiểu nhỏ**.

~~~java
boolean flag = true;
byte small = 10;

var within = flag ? small : 100;  // byte
var outside = flag ? small : 1000; // int
~~~

- `100` là **hằng `int`** và nằm trong miền `byte` (-128..127), nên kiểu biểu thức `within` là `byte`.
- `1000` nằm ngoài miền `byte`, nên biểu thức `outside` dùng kiểu `int`.

Kiểm tra bằng overload:

~~~java
static String kind(byte value) { return "byte"; }
static String kind(int value) { return "int"; }

System.out.println(kind(flag ? small : 100));  // byte
System.out.println(kind(flag ? small : 1000)); // int
~~~

**Không nhầm literal với biến:** ngay cả khi một biến `int` đang chứa 100, biểu thức `flag ? small : intVariable` thông thường có kiểu `int`, vì giá trị của biến không trở thành *constant expression* chỉ nhờ nó đang bằng 100.

~~~java
int intVariable = 100;
System.out.println(kind(flag ? small : intVariable)); // int
~~~

Chi tiết này chứng tỏ việc chọn kiểu là **quy tắc compile time**, không phụ thuộc giá trị nhánh được chọn ở runtime.

## 6. Chuyển số nguyên lớn sang số thực có thể mất độ chính xác

~~~java
long exact = 9_007_199_254_740_993L; // 2^53 + 1
boolean flag = true;

double result = flag ? exact : 0.5;
System.out.println(result); // 9.007199254740992E15
~~~

Cả biểu thức điều kiện ở đây có kiểu `double`. Khi nhánh `long` được chọn, `exact` phải được chuyển sang `double`; số nguyên **2^53 + 1** không biểu diễn chính xác trong binary64 nên được làm tròn thành **2^53**.

**Hệ quả:** dù nhánh `0.5` không chạy, kiểu `double` của nó vẫn dẫn tới việc chuyển số nguyên lớn ở nhánh true. Nếu phải bảo toàn một ID/`long` chính xác, đừng trộn nó với số thực chỉ để tạo một biểu thức `?:`.

### Chuyển kiểu không sửa phép tính đã diễn ra trong nhánh

~~~java
boolean flag = true;
double quotient = flag ? (3 / 2) : 2.5;

System.out.println(quotient); // 1.0
~~~

`3 / 2` **đã thực hiện chia nguyên**, trả 1, rồi mới được chuyển sang `double` của toàn biểu thức. Nó **không trở thành phép chia 1.5** chỉ vì nhánh kia là `double`.

Nếu cần phần lẻ, chuyển kiểu **trước phép chia**:

~~~java
double quotient = flag ? (3.0 / 2) : 2.5;
System.out.println(quotient); // 1.5
~~~

## 7. Numeric wrapper: unboxing có thể xảy ra

Ví dụ quan trọng với `Integer` và `int`:

~~~java
Integer boxed = 7;
int primitive = 9;
boolean flag = true;

int selected = flag ? boxed : primitive;
System.out.println(selected); // 7
~~~

Trong cặp kiểu này, biểu thức điều kiện số cho kiểu `int`; khi nhánh `boxed` được chọn, Java **unbox `Integer`** để lấy giá trị nguyên thủy.

Nếu wrapper đang null:

~~~java
Integer missing = null;
// int result = true ? missing : 9;
// Khi chạy: NullPointerException do unbox missing.
~~~

Một điều rất quan trọng: **chỉ nhánh được chọn mới được đánh giá và chuyển kiểu cần thiết tại runtime**. Trong ví dụ sau nhánh wrapper null không được chọn nên không có lỗi:

~~~java
Integer missing = null;
int safe = true ? 9 : missing;
System.out.println(safe); // 9
~~~

Đừng nghĩ rằng nhánh null sẽ luôn ném NPE chỉ vì compiler biết nhánh đó có thể cần unboxing. Nó cần thực sự được chọn và dùng ở runtime.

## 8. Hai numeric wrapper khác nhau cũng có thể tạo kiểu nguyên thủy

~~~java
Integer whole = 7;
Double fraction = 2.5;
boolean flag = true;

Number chosen = flag ? whole : fraction;
System.out.println(chosen); // 7.0
~~~

Với cặp `Integer` và `Double`, đây là **numeric conditional**, Java áp dụng unboxing và quy tắc numeric promotion để có kiểu `double`. Khi gán cho `Number`, kết quả có thể được **boxing thành `Double`**. Không nên đoán rằng chọn `whole` thì object kết quả chắc chắn là `Integer`.

Nếu cần giữ nguyên loại object cụ thể theo nhánh, hãy thiết kế rõ **kiểu tham chiếu** và cách chuyển đổi thay vì dựa vào suy đoán của phép `?:`. Trong một số trường hợp, tách `if/else` thành gán `Number` rõ ràng sẽ dễ kiểm thử hơn.

Đây là một trong những lý do không nên viết các biểu thức `?:` trộn nhiều kiểu wrapper khi ngữ nghĩa về loại object quan trọng.

## 9. Biểu thức điều kiện boolean và `Boolean` nullable

~~~java
boolean flag = true;
boolean selected = flag ? true : false;
System.out.println(selected); // true
~~~

Một biểu thức có hai nhánh `boolean` có kiểu `boolean`; nếu phối hợp với `Boolean`, có thể phát sinh unboxing.

~~~java
Boolean boxed = null;
boolean flag = true;

// boolean result = flag ? boxed : false;
// Nếu nhánh boxed được chọn sẽ ném NullPointerException.
~~~

Ngoài ra, **chính điều kiện** cũng có thể là `Boolean`:

~~~java
Boolean condition = null;
// int value = condition ? 1 : 2;
// NullPointerException khi unbox condition để quyết định nhánh.
~~~

**Phân biệt lỗi:**
- Điều kiện null → lỗi khi **xác định nhánh**.
- Nhánh wrapper null → lỗi khi **nhánh được chọn cần unbox**.
- Nhánh không được chọn → không bị đánh giá, không tự ném NPE.

Nếu dữ liệu `Boolean` nullable, nên định nghĩa chính sách `null` rõ ràng, ví dụ dùng `Boolean.TRUE.equals(condition)` khi chỉ giá trị true mới được chấp nhận.

## 10. Biểu thức tham chiếu và target typing

Không phải `?:` nào cũng dùng numeric promotion. Khi hai nhánh thuộc nhóm tham chiếu, Java có thể dùng **kiểu mục tiêu** (*target type*) trong ngữ cảnh gán hoặc truyền đối số.

~~~java
boolean flag = true;

String label = flag ? "ready" : null;
System.out.println(label); // ready
~~~

Ví dụ rõ hơn với kiểu tổng quát:

~~~java
java.util.List<String> names = flag
        ? new java.util.ArrayList<>()
        : new java.util.LinkedList<>();

names.add("Java");
System.out.println(names.get(0)); // Java
~~~

Hai nhánh tham chiếu được kiểm tra theo **kiểu mục tiêu `List<String>`**; các toán tử `new ...<>()` cũng nhận ngữ cảnh kiểu phù hợp. Java không cần một “phép cộng kiểu” số học ở đây.

Với nhánh kiểu tham chiếu khác nhau:

~~~java
Object result = flag ? "ready" : Integer.valueOf(7);
System.out.println(result.getClass().getSimpleName()); // String
~~~

Đây là **reference conditional expression** và có target type `Object`. Chỉ nhánh được chọn tạo ra giá trị runtime, nhưng cả hai nhánh phải chuyển đổi được trong ngữ cảnh đích.

**Cẩn thận với `var`:** khi khởi tạo biến bằng `var x = flag ? expr1 : expr2;`, không có kiểu biến khai báo sẵn làm target type cho biểu thức con theo cách của ví dụ `List<String>` ở trên. Compiler phải xác định kiểu ở ngữ cảnh standalone; với các nhánh reference khác nhau có thể suy ra kiểu chung hoặc intersection type phức tạp. Nếu kiểu API cần chính xác và dễ đọc, **khai báo kiểu đích tường minh**.

## 11. `null` không có nghĩa mọi biểu thức `?:` đều nullable

Với reference:

~~~java
String text = true ? null : "backup";
System.out.println(text == null); // true
~~~

Nhánh true là null; kiểu kết quả vẫn có thể phù hợp `String` nhờ ngữ cảnh tham chiếu.

Nhưng với numeric conditional dùng primitive và wrapper:

~~~java
Integer nullable = null;
// int result = true ? nullable : 5;
~~~

Đây là trường hợp cần unboxing `nullable`, nên **không giống** reference conditional trả null bình thường. Không nên suy luận “một nhánh null thì toán tử sẽ trả null mà không lỗi” trước khi xác định **loại conditional expression**.

**Hợp đồng API:** nếu null là một trạng thái hợp lệ, thiết kế rõ output tham chiếu/Optional; nếu không chấp nhận null, validate trước khi dùng.

## 12. Thứ tự ưu tiên, tính kết hợp và điều kiện lồng nhau

Toán tử `?:` có tính kết hợp **từ phải sang trái**:

~~~java
int score = 75;
String grade = score >= 90 ? "A"
        : score >= 70 ? "B"
        : "C";

System.out.println(grade); // B
~~~

Được hiểu theo nhóm:

~~~java
String grade = score >= 90
        ? "A"
        : (score >= 70 ? "B" : "C");
~~~

Nếu biểu thức lồng nhiều nhánh có kiểu hỗn hợp, cả việc **chọn kiểu** và **thứ tự chọn nhánh** đều khó theo dõi. Ưu tiên `if/else if/else` khi có nhiều điều kiện, kiểm tra lỗi hoặc logic nghiệp vụ dài.

Dấu ngoặc giúp làm rõ cách nhóm, **nhưng không làm mọi cặp kiểu nhánh tự nhiên tương thích**, và không đổi quy tắc numeric promotion.

## 13. Khi nào nên dùng `if/else` thay vì `?:`?

Dùng `?:` khi hai nhánh là **hai giá trị ngắn, dễ đoán kiểu**:

~~~java
String status = active ? "ON" : "OFF";
int timeout = strict ? 100 : 300;
~~~

Cân nhắc `if/else` khi:
- Một nhánh có side effect, gọi nhiều method hoặc có thể ném lỗi.
- Hai nhánh chứa kiểu số/wrapper khác nhau cần chuyển đổi có chủ đích.
- Cần xử lý null, overflow, mất độ chính xác hoặc chính sách rounding.
- Có nhiều nhánh `?:` lồng nhau khó review.

Ví dụ làm rõ chuyển đổi:

~~~java
Number selected;
if (flag) {
    selected = whole;    // Giữ giá trị tham chiếu Integer
} else {
    selected = fraction; // Giữ giá trị tham chiếu Double
}
~~~

Cách này khác về **loại object được giữ** với numeric conditional `flag ? whole : fraction` trong một số tình huống. Lựa chọn đúng phụ thuộc hợp đồng mong muốn.

## 14. Bảng lỗi phổ biến

| Nhầm lẫn | Thực tế trong Java | Cách xử lý |
|---|---|---|
| `?:` luôn có kiểu của nhánh đang chạy | Kiểu được xác định theo quy tắc compile time | Xét cả hai nhánh và target type |
| `true ? 10 : 2.5` có kiểu int | Có kiểu `double` | Dùng numeric promotion đúng |
| `double d = flag ? 1 : 2;` làm `?:` thành double | Conditional số bên trong là int, rồi gán mới widening | Tách kiểu biểu thức và kiểu biến nhận |
| `byte b; flag ? b : 100` chắc chắn kiểu int | Hằng int 100 vừa byte → kết quả byte | Biết ngoại lệ constant-expression |
| `flag ? b : intVar` kiểu byte khi intVar đang bằng 100 | intVar thường không phải hằng compile time | Kiểu thường là int |
| Nhánh `long` luôn giữ chính xác khi nhánh kia double | Widening long→double có thể làm tròn | Giữ cùng loại số nếu cần chính xác |
| `flag ? (3/2) : 2.5` cho 1.5 | Chia nguyên 3/2 đã cho 1 | Dùng 3.0/2 trước |
| Wrapper null luôn an toàn vì có nhánh khác | Nhánh được chọn có thể unbox null | Guard null hoặc thiết kế reference output |
| Nhánh không được chọn vẫn chạy để chuyển kiểu | Chỉ nhánh được chọn được đánh giá runtime | Tách compile-time typing và runtime evaluation |
| `Boolean` null dùng làm condition nghĩa là false | Unboxing null ném NPE | Dùng chính sách null rõ |
| `Integer`/ `Double` chọn nhánh nào giữ đúng wrapper đó | Numeric conditional có thể unbox/promote rồi box lại | Nếu cần object identity, dùng if/else reference |
| `var` luôn suy kiểu như target khai báo tường minh | Không có kiểu đích khai báo sẵn như `List<String>` | Khai báo type khi quan trọng |
| `?:` lồng dài luôn dễ hơn if/else | Khó suy kiểu và thứ tự chọn nhánh | Tách if/else hoặc hàm rõ ý |

## 15. Chương trình Java 17 hoàn chỉnh

Tạo file đúng tên **`ConditionalExpressionTypeDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public final class ConditionalExpressionTypeDemo {
    private ConditionalExpressionTypeDemo() {}

    static String kind(byte value) { return "byte"; }
    static String kind(int value) { return "int"; }
    static String kind(long value) { return "long"; }
    static String kind(float value) { return "float"; }
    static String kind(double value) { return "double"; }

    static int trace(String label, int value) {
        System.out.println("evaluated " + label);
        return value;
    }

    public static void main(String[] args) {
        boolean choose = true;

        System.out.println("numeric int="
                + kind(choose ? 10 : 20));
        System.out.println("numeric long="
                + kind(choose ? 10 : 20L));
        System.out.println("numeric float="
                + kind(choose ? 10 : 2.5f));
        System.out.println("numeric double="
                + kind(choose ? 10 : 2.5));

        byte small = 10;
        int regularInt = 100;
        System.out.println("byte constant="
                + kind(choose ? small : 100));
        System.out.println("byte too large="
                + kind(choose ? small : 1000));
        System.out.println("byte int variable="
                + kind(choose ? small : regularInt));

        double widenedTarget = choose ? 1 : 2;
        System.out.println("widened assignment=" + widenedTarget);

        long exact = 9_007_199_254_740_993L;
        double rounded = choose ? exact : 0.5;
        System.out.println("long promoted to double=" + rounded);

        double lateDivision = choose ? (3 / 2) : 2.5;
        double earlyDivision = choose ? (3.0 / 2) : 2.5;
        System.out.println("integer division then widen=" + lateDivision);
        System.out.println("floating division=" + earlyDivision);

        int selected = choose
                ? trace("true", 7)
                : trace("false", 9);
        System.out.println("selected=" + selected);

        Integer boxed = 7;
        int primitive = 9;
        System.out.println("int unboxed=" + (choose ? boxed : primitive));

        Integer missing = null;
        int safe = choose ? 9 : missing;
        System.out.println("unselected null=" + safe);
        try {
            int broken = choose ? missing : 9;
            System.out.println(broken);
        } catch (NullPointerException ex) {
            System.out.println("selected null=NullPointerException");
        }

        Integer whole = 7;
        Double fraction = 2.5;
        Number numericWrapper = choose ? whole : fraction;
        System.out.println("wrapper value=" + numericWrapper);
        System.out.println("wrapper class="
                + numericWrapper.getClass().getSimpleName());

        Number referenceWrapper;
        if (choose) {
            referenceWrapper = whole;
        } else {
            referenceWrapper = fraction;
        }
        System.out.println("reference class="
                + referenceWrapper.getClass().getSimpleName());

        Boolean boxedCondition = null;
        try {
            int number = boxedCondition ? 1 : 2;
            System.out.println(number);
        } catch (NullPointerException ex) {
            System.out.println("null condition=NullPointerException");
        }

        String text = choose ? "ready" : null;
        System.out.println("reference string=" + text);
        String nullable = choose ? null : "fallback";
        System.out.println("reference null=" + (nullable == null));

        Object objectValue = choose ? "ready" : Integer.valueOf(7);
        System.out.println("target object class="
                + objectValue.getClass().getSimpleName());

        List<String> list = choose
                ? new ArrayList<>()
                : new LinkedList<>();
        list.add("Java");
        System.out.println("target typed list=" + list.get(0));

        int score = 75;
        String grade = score >= 90 ? "A"
                : score >= 70 ? "B"
                : "C";
        System.out.println("nested condition=" + grade);
    }
}
~~~

**Bash (Linux/macOS)** — terminal đang ở thư mục chứa file, yêu cầu JDK 17+:

~~~bash
javac --release 17 -encoding UTF-8 ConditionalExpressionTypeDemo.java
java -cp . ConditionalExpressionTypeDemo
~~~

**PowerShell (Windows):**

~~~powershell
javac --release 17 -encoding UTF-8 .\ConditionalExpressionTypeDemo.java
java -cp . ConditionalExpressionTypeDemo
~~~

Không dùng Maven, Gradle hoặc thư viện ngoài JDK.

### Exact expected stdout

~~~text
numeric int=int
numeric long=long
numeric float=float
numeric double=double
byte constant=byte
byte too large=int
byte int variable=int
widened assignment=1.0
long promoted to double=9.007199254740992E15
integer division then widen=1.0
floating division=1.5
evaluated true
selected=7
int unboxed=7
unselected null=9
selected null=NullPointerException
wrapper value=7.0
wrapper class=Double
reference class=Integer
null condition=NullPointerException
reference string=ready
reference null=true
target object class=String
target typed list=Java
nested condition=B
~~~

**Giải thích điểm quan trọng của stdout:**

1. Bốn dòng `numeric ...` xác nhận numeric conditional expression có kiểu chung do Java quyết định.
2. `byte constant=byte`, `byte too large=int`: hằng `int` vừa miền có quy tắc riêng; một biến `int` thường không được hưởng điều kiện này.
3. `widened assignment=1.0`: toán tử `?:` bên trong có kiểu int; widening diễn ra ở phép gán ra biến double.
4. `long promoted to double`: `long` lớn bị làm tròn khi chọn biểu thức có kiểu `double`.
5. `integer division then widen=1.0`: chuyển kiểu ở ngoài không làm phép chia nguyên thành phép chia thực.
6. Không có `evaluated false`: Java chỉ thực hiện nhánh true.
7. `selected null` ném NPE do unboxing nhánh được chọn; `unselected null=9` không có lỗi.
8. `wrapper class=Double` đối lập `reference class=Integer`: numeric conditional khác việc gán trực tiếp từng wrapper vào `Number`.
9. `null condition` ném NPE ở bước đánh giá điều kiện `Boolean`.
10. `target typed list=Java` chứng minh biểu thức tham chiếu có thể dùng kiểu đích `List<String>`; `nested condition=B` do toán tử `?:` kết hợp phải.

## 16. Bài tập có đáp án

### Bài A — xác định kiểu biểu thức

~~~java
boolean flag = true;
var result = flag ? 3 : 4L;
System.out.println(result);
~~~

**Đáp án:** `result` có kiểu **`long`**, stdout là `3`. `var` không làm biểu thức thành kiểu runtime động.

### Bài B — hằng `int` với `byte`

~~~java
byte b = 5;
System.out.println(kind(true ? b : 100));
System.out.println(kind(true ? b : 200));
~~~

**Đáp án:**

~~~text
byte
int
~~~

Hàm overload `kind` định nghĩa trong chương trình đầy đủ. 100 nằm trong miền byte, 200 không nằm trong miền byte.

### Bài C — unboxing nhánh null

~~~java
Integer missing = null;
int value = true ? 1 : missing;
System.out.println(value);
~~~

**Đáp án:** `1`, không NPE vì nhánh `missing` không được chọn. Nếu đổi `true` thành `false`, nhánh `Integer` null được chọn và cần unboxing → `NullPointerException`.

### Bài D — chia nguyên hay chia thực?

~~~java
boolean flag = true;
double result = flag ? (7 / 2) : 2.5;
System.out.println(result);
~~~

**Đáp án:** `3.0`. `7 / 2` xảy ra trong nhánh với kiểu nguyên, cắt phần lẻ trước khi chuyển sang double.

### Bài E — chọn object hay chọn giá trị số?

~~~java
Integer a = 7;
Double b = 2.5;
Number result = true ? a : b;
System.out.println(result.getClass().getSimpleName());
~~~

**Đáp án:** `Double`. Numeric conditional thực hiện unboxing/numeric promotion rồi boxing khi gán vào Number, chứ không bảo toàn wrapper Integer chỉ vì nhánh true chọn a.

### Bài F — target typing với reference

~~~java
java.util.List<String> names = true
        ? new java.util.ArrayList<>()
        : new java.util.LinkedList<>();
names.add("JDK 17");
System.out.println(names.get(0));
~~~

**Đáp án:** `JDK 17`. Biểu thức tham chiếu nhận target type `List<String>`, cho phép hai nhánh được kiểm tra trong ngữ cảnh đó.

## 17. Checklist và kết luận

- [ ] Hiểu cú pháp `condition ? whenTrue : whenFalse`.
- [ ] Biết chỉ **một nhánh được chọn** được đánh giá runtime.
- [ ] Hiểu compiler xét cả hai nhánh để kiểm tra kiểu.
- [ ] Phân biệt boolean, numeric và reference conditional expression.
- [ ] Biết numeric promotion khi trộn `int`, `long`, `float`, `double`.
- [ ] Biết quy tắc đặc biệt với `byte`/`short` và hằng `int` vừa miền.
- [ ] Phân biệt kiểu của `?:` với chuyển kiểu ở phép gán ra biến.
- [ ] Hiểu `long → double` có thể mất độ chính xác; chuyển kiểu ngoài không sửa phép chia nguyên đã tính.
- [ ] Nhận diện unboxing và NPE ở nhánh wrapper được chọn.
- [ ] Biết `Boolean` null khi dùng làm điều kiện có thể NPE.
- [ ] Hiểu reference conditional có thể chịu target typing, `var` không thay thế khai báo kiểu khi cần rõ API.
- [ ] Hiểu tính kết hợp phải của `?:` và tránh lồng điều kiện quá sâu.
- [ ] Biên dịch `ConditionalExpressionTypeDemo.java` trên Java 17+ và đối chiếu exact stdout.

**Kết luận:** Toán tử ba ngôi `?:` trong Java 17+ không đơn giản là “trả nguyên kiểu của nhánh được chọn”. Kiểu được quyết định **ở compile time**, đôi khi dựa vào **kiểu mục tiêu**, trong khi runtime chỉ đánh giá **nhánh được chọn**. Numeric promotion, narrowing đặc biệt và boxing/unboxing có thể làm kết quả khác trực giác hoặc phát sinh lỗi. Hãy dùng `?:` cho lựa chọn giá trị ngắn, cùng kiểu rõ ràng; khi có nhiều kiểu hỗn hợp hoặc chính sách chuyển đổi quan trọng, ưu tiên `if/else` và chuyển kiểu tường minh.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="logic-and-conditions-009">Tránh lồng quá nhiều toán tử ba ngôi</a>

<details>
<summary>Xem chi tiết</summary>

# Tránh lồng quá nhiều toán tử ba ngôi trong Java

> **Java core — language-basics / Logic và điều kiện**  
> **Yêu cầu:** Java 17+, một file Java độc lập, không cần thư viện ngoài.  
> **Mục tiêu:** Dùng `?:` cho lựa chọn ngắn gọn; hiểu cách nhóm, nhánh thực sự được đánh giá và lý do nên thay chuỗi `?:` dài bằng `if/else`, phương thức có tên hoặc `switch` expression.

## 1. Toán tử ba ngôi `?:` là gì?

Cú pháp:

~~~java
result = condition ? valueIfTrue : valueIfFalse;
~~~

`condition` phải là biểu thức `boolean` hoặc `Boolean` có thể unbox thành `boolean`; nếu `Boolean` là `null` thì có thể ném `NullPointerException` khi đánh giá điều kiện. Java **chỉ đánh giá một trong hai nhánh kết quả**, tùy điều kiện:

~~~java
int age = 20;
String category = age >= 18 ? "adult" : "minor";
System.out.println(category); // adult
~~~

`?:` là **biểu thức tạo ra một giá trị**, khác `if/else` là cấu trúc điều khiển luồng. Nó phù hợp khi cả hai nhánh là biểu thức ngắn, dễ đọc, không mang tác dụng phụ khó hiểu.

## 2. Lồng `?:` nhiều tầng vẫn hợp lệ nhưng khó đọc

Ví dụ phân loại điểm:

~~~java
int score = 82;
String grade = score >= 90 ? "A"
             : score >= 80 ? "B"
             : score >= 70 ? "C"
             : "D";
~~~

Java cho phép biểu thức điều kiện **kết hợp từ phải sang trái**. Cách nhóm tương ứng:

~~~java
String grade = score >= 90 ? "A"
             : (score >= 80 ? "B"
             : (score >= 70 ? "C" : "D"));
~~~

Nếu `score == 82` thì `grade == "B"` về nội dung. Nhánh cho điểm từ 70 trở xuống không cần chạy khi đã chọn nhánh B.

Vấn đề là người đọc phải tự xác định:

- Mỗi dấu `?` thuộc điều kiện nào, mỗi dấu `:` thuộc nhánh nào.
- Các điều kiện có **thứ tự ưu tiên nghiệp vụ** không (ví dụ ngưỡng 90 phải xét trước 80).
- Nhánh mặc định cuối cùng ứng với trường hợp nào.
- Khi thêm một mức phân loại, ngoặc và căn lề có còn đúng không.

**Nguyên tắc:** Càng nhiều điều kiện/nhánh, `if/else` càng dễ kiểm tra, đặt breakpoint và thay đổi. Không có một “giới hạn cú pháp” bắt buộc về số tầng; đây là khuyến nghị về chất lượng mã.

## 3. Cách viết rõ hơn bằng `if/else`

~~~java
static String grade(int score) {
    if (score >= 90) {
        return "A";
    } else if (score >= 80) {
        return "B";
    } else if (score >= 70) {
        return "C";
    } else {
        return "D";
    }
}
~~~

Cách này giữ **cùng thứ tự xét ngưỡng** nhưng mỗi điều kiện là một nhánh rõ ràng. Có thể dùng *guard clauses* để tránh lồng khối:

~~~java
static String gradeWithEarlyReturns(int score) {
    if (score >= 90) return "A";
    if (score >= 80) return "B";
    if (score >= 70) return "C";
    return "D";
}
~~~

Với nhánh nhiều dòng, cần log, xử lý lỗi hoặc gọi phương thức có tác dụng phụ, dùng `if/else` thường dễ bảo trì hơn cố nhét cả logic vào `?:`.

## 4. Khi nào vẫn nên dùng một `?:`?

| Tình huống | Cách viết | Nhận xét |
| --- | --- | --- |
| Chọn một trong hai chuỗi ngắn | `passed ? "PASS" : "FAIL"` | Dễ đọc |
| Giá trị mặc định | `name != null ? name : "guest"` | Giữ guard trước nhánh sử dụng |
| Chọn nhãn sau nhiều ngưỡng | `a ? "A" : b ? "B" : c ? "C" : "D"` | Nên cân nhắc `if/else` |
| Hai nhánh nhiều lệnh hoặc log | `condition ? complexA() : complexB()` | Tách phương thức hoặc dùng `if/else` khi logic phức tạp |
| Nhiều trạng thái rời rạc | Chuỗi kiểm tra `status == ... ? ...` | Dùng `switch` expression/enum khi phù hợp |

`?:` chỉ dùng được khi hai nhánh có kiểu **tương thích theo quy tắc kiểu của Java**. Ví dụ `true ? 1 : 2.5` có kiểu `double` và trả `1.0`; không phải lúc nào kiểu kết quả cũng giống đúng kiểu của nhánh được chọn. Tránh dựa vào phép chuyển kiểu ngầm khó đoán ở chuỗi `?:` nhiều tầng.

## 5. Java 17: `switch` expression cho lựa chọn theo trạng thái

Với một trạng thái có nhiều giá trị rời rạc, `switch` expression có thể sáng sủa hơn chuỗi `?:`:

~~~java
enum State { NEW, APPROVED, REJECTED }

static String label(State state) {
    return switch (state) {
        case NEW -> "New";
        case APPROVED -> "Approved";
        case REJECTED -> "Rejected";
    };
}
~~~

Đây là cú pháp chuẩn trong Java 17, không cần bật preview. `switch` expression phải **exhaustive** (bao phủ hết các trường hợp). Ở ví dụ `enum` trên, đã liệt kê đủ ba hằng; nếu bổ sung trạng thái mới, trình biên dịch sẽ buộc xử lý cho phù hợp.

**Chọn công cụ theo bài toán:** Dãy kiểm tra **khoảng số** thường hợp với `if/else if`; tập **trạng thái rời rạc** thường hợp với `switch`. Không ép mọi bài toán sang `switch` chỉ để bỏ dấu `?:`.

## 6. Nhánh không được chọn không chạy — nhưng cẩn thận khi refactor

~~~java
StringBuilder log = new StringBuilder();
String selected = false
        ? trace(log, "X", "unused")
        : trace(log, "R", "chosen");
// selected="chosen", log="R"; nhánh X hoàn toàn không chạy.
~~~

Các điều kiện trong chuỗi `?:` lồng nhau được đánh giá theo cách nhóm và **chỉ khi cần**. Nếu nhánh không được chọn gọi hàm, tăng biến, truy cập null hoặc ném ngoại lệ, tác dụng phụ đó không xảy ra trong lần đánh giá ấy.

Tuy nhiên, **tránh nhét tác dụng phụ vào điều kiện hoặc nhánh** chỉ vì cú pháp cho phép. Khi chuyển sang `if/else`, phải giữ nguyên thứ tự xét điều kiện, không gọi lại hàm có side effect và không vô tình thực hiện cả hai nhánh.

Ví dụ guard an toàn:

~~~java
String text = null;
String display = text != null ? text.toUpperCase() : "(unknown)";
// Không gọi text.toUpperCase() khi text == null.
~~~

Ví dụ quá phức tạp để viết một dòng:

~~~java
// Khó kiểm tra khi tăng số nhánh:
String access = !loggedIn ? "SIGN_IN"
              : suspended ? "BLOCKED"
              : admin ? "ADMIN"
              : "USER";
~~~

Refactor bằng phương thức rõ nghĩa:

~~~java
static String accessLabel(boolean loggedIn,
                          boolean suspended,
                          boolean admin) {
    if (!loggedIn) return "SIGN_IN";
    if (suspended) return "BLOCKED";
    return admin ? "ADMIN" : "USER";
}
~~~

Giữ một `?:` đơn giản ở cuối vẫn ổn; mục tiêu là **giảm độ phức tạp**, không cấm tuyệt đối toán tử ba ngôi.

## 7. Chương trình Java 17+ runnable

Tạo file **ReadableTernaryDemo.java**:

~~~java
public final class ReadableTernaryDemo {
    enum State { NEW, APPROVED, REJECTED }

    private static String gradeNested(int score) {
        return score >= 90 ? "A"
             : score >= 80 ? "B"
             : score >= 70 ? "C"
             : "D";
    }

    private static String gradeClear(int score) {
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 70) return "C";
        return "D";
    }

    private static String accessLabel(boolean loggedIn,
                                      boolean suspended,
                                      boolean admin) {
        if (!loggedIn) return "SIGN_IN";
        if (suspended) return "BLOCKED";
        return admin ? "ADMIN" : "USER";
    }

    private static String stateLabel(State state) {
        return switch (state) {
            case NEW -> "New";
            case APPROVED -> "Approved";
            case REJECTED -> "Rejected";
        };
    }

    private static String trace(StringBuilder log,
                                String label, String value) {
        log.append(label);
        return value;
    }

    public static void main(String[] args) {
        for (int score : new int[] {95, 82, 74, 40}) {
            System.out.println("grade=" + score + ":"
                    + gradeNested(score) + "/" + gradeClear(score));
        }

        boolean passed = true;
        System.out.println("simple=" + (passed ? "PASS" : "FAIL"));

        System.out.println("accessGuest="
                + accessLabel(false, false, true));
        System.out.println("accessBlocked="
                + accessLabel(true, true, true));
        System.out.println("accessAdmin="
                + accessLabel(true, false, true));
        System.out.println("accessUser="
                + accessLabel(true, false, false));

        System.out.println("state=" + stateLabel(State.APPROVED));

        String name = null;
        String display = name != null ? name.toUpperCase() : "(unknown)";
        System.out.println("nullSafe=" + display);

        StringBuilder branchLog = new StringBuilder();
        String selected = false
                ? trace(branchLog, "X", "unused")
                : trace(branchLog, "R", "chosen");
        System.out.println("selected=" + selected);
        System.out.println("branchTrace=" + branchLog);

        double numeric = true ? 1 : 2.5;
        System.out.println("numericType=" + numeric);
    }
}
~~~

Biên dịch/chạy ở thư mục chứa file (Bash, PowerShell hoặc Command Prompt):

~~~bash
javac -encoding UTF-8 ReadableTernaryDemo.java
java ReadableTernaryDemo
~~~

**Expected stdout chính xác:**

~~~text
grade=95:A/A
grade=82:B/B
grade=74:C/C
grade=40:D/D
simple=PASS
accessGuest=SIGN_IN
accessBlocked=BLOCKED
accessAdmin=ADMIN
accessUser=USER
state=Approved
nullSafe=(unknown)
selected=chosen
branchTrace=R
numericType=1.0
~~~

Hai cột điểm `A/A`, `B/B`, ... chứng tỏ hai cách triển khai phân loại thống nhất với dữ liệu thử; `branchTrace=R` cho thấy nhánh X không được gọi. `numericType=1.0` minh họa kiểu kết quả do Java quyết định, không chỉ dựa vào literal `1` của nhánh được chọn.

## 8. Checklist và bài tập

Trước khi giữ một chuỗi `?:` lồng nhau, tự kiểm tra:

- Có thể hiểu kết quả chỉ bằng một lần đọc, không cần đếm dấu `?`/`:` không?
- Các điều kiện có thứ tự ưu tiên rõ ràng và trường hợp mặc định dễ nhận thấy không?
- Có nhánh nào cần nhiều câu lệnh, log, exception hoặc thay đổi trạng thái không?
- Khi thêm nhánh mới, đoạn mã có dễ viết test cho các đường đi không?
- `if/else`, helper có tên hoặc `switch` expression có giúp ý nghĩa sáng tỏ hơn không?

**Bài 1.** Viết lại chuỗi chọn `"A"/"B"/"C"/"D"` từ điểm bằng `if/else if`; thử các mốc 69, 70, 79, 80, 89, 90.

**Bài 2.** Giải thích cách nhóm của `a ? x : b ? y : z`. Với `a=false` và `b=true`, giá trị được chọn là gì?

**Bài 3.** Chuyển lựa chọn theo trạng thái `NEW`/`APPROVED`/`REJECTED` sang `switch` expression của Java 17.

**Bài 4.** Cho `String s = null;`, giải thích tại sao `s != null ? s.trim() : ""` không ném `NullPointerException`.

**Bài 5.** Với lời gọi có side effect trong mỗi nhánh `?:`, trình bày cách refactor sang `if/else` mà vẫn chỉ thực hiện **một nhánh được chọn**.

### Ghi nhớ

**`?:` tốt cho lựa chọn ngắn, nhưng lồng nhiều tầng làm điều kiện khó đọc và khó bảo trì.** Java nhóm biểu thức điều kiện từ phải sang trái và chỉ đánh giá nhánh được chọn. Khi có nhiều ngưỡng, ưu tiên `if/else if`; khi có nhiều trạng thái rời rạc, cân nhắc `switch` expression; với quy tắc nghiệp vụ, đặt tên phương thức rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)
