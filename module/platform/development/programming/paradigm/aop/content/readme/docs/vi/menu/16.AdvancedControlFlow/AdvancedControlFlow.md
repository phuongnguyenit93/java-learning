<a id="back-to-top"></a>

# Luồng điều khiển nâng cao

## Menu
- [Pointcut theo luồng điều khiển và phạm vi lời gọi động](#advanced-control-flow-001)
- [Phân biệt điểm khởi đầu với các điểm nằm bên dưới luồng gọi](#advanced-control-flow-002)
- [Giới hạn hành vi theo chuỗi lời gọi thay vì vị trí mã đơn lẻ](#advanced-control-flow-003)
- [Loại trừ mã của chính aspect để tránh đệ quy](#advanced-control-flow-004)
- [Ràng buộc dữ liệu giữa các điểm trong cùng ngữ cảnh](#advanced-control-flow-005)
- [Lời gọi đệ quy và ý nghĩa của độ sâu can thiệp](#advanced-control-flow-006)
- [Luồng bất đồng bộ, callback và giới hạn suy diễn quan hệ gọi](#advanced-control-flow-007)
- [Chi phí theo dõi ngữ cảnh động và phương án đơn giản hơn](#advanced-control-flow-008)
- [Thiết kế ví dụ để kiểm chứng chính xác phạm vi điều khiển](#advanced-control-flow-009)

## <a id="advanced-control-flow-001">Pointcut theo luồng điều khiển và phạm vi lời gọi động</a>

<details>
<summary>Xem chi tiết</summary>

# Pointcut theo luồng điều khiển và phạm vi lời gọi động trong Aspect/AOP

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming — AOP**), **pointcut** chọn những **join point** nơi **advice** được áp dụng. Một điều kiện pointcut theo tên phương thức hoặc package mang tính **tĩnh**: cùng một phương thức vẫn có thể khớp bất kể nó được gọi từ đâu. Nhưng nhiều yêu cầu lại phụ thuộc **đường gọi đang diễn ra**. Ví dụ, chỉ đo việc truy cập dữ liệu **khi một lượt checkout còn đang thực thi**, chứ không đo cùng thao tác khi được chạy bởi một công việc bảo trì độc lập.

Đó là **pointcut theo luồng điều khiển động** (*dynamic control-flow pointcut*): nó chọn điểm không chỉ vì kiểu/chữ ký mà còn vì **đang có một điểm gốc được chọn nằm trên chuỗi lời gọi runtime**. Trong những ngôn ngữ AOP hỗ trợ, hai khái niệm quen thuộc là **`cflow(P)`** và **`cflowbelow(P)`**, với P là pointcut chọn **root join point**.

- `cflow(P)` thường bao gồm chính join point root P và những join point trong phạm vi thực thi động bên dưới root.
- `cflowbelow(P)` thường chọn phần **bên dưới** root P, không tính chính điểm đang mở root đó.
- Trong **nested roots**, một root P bên trong vẫn có thể thuộc `cflowbelow(P)` của **root P bên ngoài**. Không được suy rằng `cflowbelow` luôn loại mọi phương thức có tên root.
- Một pointcut theo flow chỉ là **điều kiện chọn điểm**: nó **không tự tạo một aspect instance cho từng flow**. Chính sách vòng đời kiểu `percflow`, nếu công nghệ hỗ trợ, là khái niệm khác.

**Giới hạn ngữ nghĩa:** dynamic call flow đồng bộ khác lexical scope, trạng thái per-thread, logical HTTP request và một correlation ID được truyền qua executor. Khả năng chọn method call, method execution, field access, constructor hay các điểm khác phụ thuộc **mô hình AOP và công cụ**.

Bài này chỉ bàn **Aspect/AOP cơ bản và nâng cao nói chung**. Ví dụ cú pháp AspectJ được ghi rõ là đặc thù ngôn ngữ đó, không phải mã biên dịch bằng `javac`; ví dụ Java 17 hoàn chỉnh phía dưới **mô phỏng** cflow để học mà không cần một AOP weaver.

## 1. Mục tiêu học tập

Sau bài học, bạn có thể giải thích phạm vi động khác phạm vi tĩnh; xác định điểm root/child nào khớp `cflow` hoặc `cflowbelow`; phân tích nested/recursive call và ngoại lệ; ghép điều kiện flow với join point chính xác; phân biệt binding, per-flow instance và ThreadLocal; nhận ra giới hạn proxy, weaving và async; đo hiệu năng; thiết kế kiểm thử cả đường được chọn lẫn đường không được chọn.

## 2. Những thuật ngữ quan trọng

| Khái niệm | Vai trò |
| --- | --- |
| **Join point** | Điểm trong chương trình mà mô hình AOP cho phép can thiệp |
| **Pointcut** | Biểu thức hoặc luật chọn những join point |
| **Advice** | Hành vi bổ sung chạy trước, sau, quanh điểm được chọn |
| **Aspect** | Đơn vị đóng gói pointcut, advice và đôi khi có state |
| **Root pointcut P** | Chọn những điểm mở dynamic flow cần theo dõi |
| **Dynamic scope** | Thời gian root còn hiện diện trên đường thực thi |
| **Weaving/composition** | Kết hợp chính sách với chương trình theo cơ chế compiler, bytecode, proxy... |
| **`cflow(P)`** | Quan hệ membership: đang trong flow của một P |
| **`cflowbelow(P)`** | Membership trong phần dưới một P, loại chính root tương ứng |
| **Binding** | Lấy dữ liệu từ join point/root/context vào advice nếu công nghệ hỗ trợ |
| **Instantiation policy** | Quy định bao nhiêu instance aspect; khác pointcut membership |

Pointcut có thể gồm phần tĩnh `execution(Repository.save(..))` và điều kiện động `cflow(checkoutRoot)`. **Phần tĩnh hỏi “điểm nào?”; phần động hỏi “ở trong đường thực thi nào?”**.

## 3. Đường gọi động khác vị trí khai báo tĩnh

```text
Đường A:
main -> Checkout.checkout() -> Repository.reserve()
                              [in checkout flow]

Đường B:
main -> MaintenanceJob.run() -> Repository.reserve()
                               [outside checkout flow]
```

Ở cả A và B, mã `Repository.reserve` có thể là cùng một phương thức, cùng package và cùng chữ ký. Bộ lọc tĩnh theo tên/package có thể chọn cả hai, nhưng điều kiện `cflow(checkoutRoot)` thường **chỉ đúng ở A** khi root còn hoạt động.

Điểm này khác hoàn toàn với “phương thức repository nằm trong package checkout”. Một utility trong package khác vẫn có thể ở trong flow khi được checkout gọi. Ngược lại, mã trong package checkout có thể chạy ngoài flow do được gọi độc lập.

Các điều kiện như `within` và `withincode` trong một số ngôn ngữ AOP thể hiện ranh giới **tĩnh/lexical**; chúng không tự phát biểu quan hệ một root đang tồn tại trên call stack.

### 3.1. Cùng tên nhưng overload khác

```java
void checkout(String orderId);
void checkout(String orderId, boolean dryRun);
```

Đây là chữ ký minh họa, không phải chương trình độc lập. Một root pointcut chỉ chọn `checkout(String)` không tự tạo flow khi bản overload thứ hai được thực thi. Nếu sử dụng wildcard chọn mọi overload, phải đánh giá **đó có đúng yêu cầu nghiệp vụ không**.

### 3.2. Root có thể là nhiều phương thức

P có thể chọn nhiều join point của nhiều lớp. Nếu chương trình đang thực thi root P1 rồi lại gọi root P2 mà cả hai thỏa P, **hai activation P có thể đồng thời hoạt động**. Đây là lý do một biến `boolean inRoot` không đủ để biểu diễn toàn bộ quan hệ.

## 4. Hiểu `cflow(P)` qua entry và exit

```text
Checkout.checkout() entered   <-- Root P begins
  Validation.check()          <-- inside cflow(P)
  Repository.reserve()        <-- inside cflow(P)
  Notification.prepare()      <-- inside cflow(P)
Checkout.checkout() returns   <-- Root P ends

Repository.reserve()          <-- outside cflow(P)
```

Khi root được vào theo **loại join point P đã định nghĩa**, những điểm thực thi bên dưới nó có thể được xét theo flow. Root kết thúc khi execution/call tương ứng rời phạm vi, dù return bình thường hay unwinding vì exception.

**Không phải “đã có checkout trong 5 giây gần đây”.** Một lời gọi repository ở thời điểm sau khi checkout đã kết thúc không còn thuộc dynamic call stack ấy.

Nếu root trả về `CompletableFuture` chưa hoàn tất, execution đồng bộ của root có thể đã kết thúc trong khi công việc async vẫn chạy. Không tự suy `cflow` phải tồn tại tới khi future hoàn tất.

### 4.1. Ngoại lệ

Một root có thể ném lỗi. Khi phương thức rời call stack vì lỗi, flow của activation đó không còn hoạt động trên đường thực thi sau đó. Cơ chế manual muốn mô phỏng phải dọn state trong `finally` sau khi mở thành công scope.

Nếu chỉ tắt cờ trong nhánh after-returning, flow có thể rò vào lượt gọi khác khi exception xảy ra.

### 4.2. Root lồng nhau và recursion

```text
checkout(outer)         Root P1
   -> checkout(inner)   Root P2 (inside P1)
       -> reserve()     Inside P1 and P2
   -> reserve()         Still inside P1, P2 ended
```

Trong recursion, P1/P2 thậm chí có thể là các execution của **cùng một phương thức** ở những depth khác nhau. Flow không chỉ là tên method, mà gồm **những lần kích hoạt khác nhau**.

## 5. `cflow` khác `cflowbelow` ở đâu?

Với **một root không lồng**:

| Join point đang xét | `cflow(P)` | `cflowbelow(P)` |
| --- | --- | --- |
| Chính root P | Có | Không |
| Child method trong root | Có | Có |
| Child sâu nhiều tầng | Có | Có |
| Cùng method child ngoài root | Không | Không |
| Phương thức khác sau root return | Không | Không |

Tuy nhiên, ở **nested roots**:

```text
P(outer)
   -> P(inner)
      -> child()
```

Tại **execution của P(inner)**, điểm này là root của inner, nhưng nó **nằm bên dưới outer**. Do vậy nó có thể thỏa `cflowbelow(P)` vì **root outer** đang hoạt động. Đây là một ngoại lệ quan trọng với diễn giải quá đơn giản “cflowbelow luôn bỏ qua mọi execution có tên P”.

Với child bên trong inner, cả outer và inner là các activation đang hoạt động, và child nằm dưới cả hai. Nhưng Boolean membership **không có nghĩa advice bắt buộc chạy hai lần**. Số lượt dispatch và instance còn phụ thuộc join point/advice/instantiation của công cụ.

### 5.1. Biên root tùy loại join point

Nếu P chọn **method call**, root là thao tác lời gọi ở phía caller. Nếu P chọn **method execution**, root là sự kiện thực thi thân phương thức đích. Không đồng nhất hai ranh giới ấy khi xét điều kiện ở đúng chính điểm root.

Cần xác nhận loại join point, lớp nào đã được weave và pointcut/around advice nào có thể chặn lời gọi trước khi root thực sự bắt đầu.

## 6. Kết hợp bộ lọc tĩnh với điều kiện flow

Một yêu cầu cụ thể: “Chỉ log việc `Inventory.reserve` khi checkout đang chạy.”

Mô hình:

```text
execution of Inventory.reserve(...)
AND
cflow(execution of Checkout.checkout(...))
```

Đây là **giả mã**, không phải Java chuẩn. Phần `execution` chọn **điểm con cần đo**, `cflow` xác nhận bối cảnh runtime của nó. Nếu chỉ dùng `cflow(checkout)` không kèm bộ lọc con, số điểm phù hợp có thể rộng hơn mong muốn.

Các phép AND, OR, NOT hoặc pointcut có tên có thể giúp đọc và tái sử dụng điều kiện, tùy ngôn ngữ AOP. Cần đặt ngoặc rõ để tránh thay nghĩa giữa biểu thức “A AND (B OR C)” và “(A AND B) OR C”.

### 6.1. Loại bỏ hạ tầng advice

Nếu advice ghi log nhưng logger lại gọi phương thức nằm trong pointcut, nó có thể gây **reentrancy** và log đệ quy. Nên chọn điểm hẹp và loại những package/method hạ tầng có thể tự kích hoạt advice, khi được công nghệ hỗ trợ.

Không giả định `cflow` tự bảo vệ bạn khỏi advice tự gọi lại root/child.

### 6.2. Chọn đúng overload, annotation và access

Một pointcut theo tên `reserve` có thể chọn nhiều overload; một điều kiện annotation có thể chỉ chọn những phương thức thật sự mang metadata tương ứng; một `private` hoặc field access có thể không phải join point của cơ chế proxy đang dùng. Tất cả phải được kiểm tra theo **join point model**.

## 7. Cú pháp AspectJ minh họa — không phải Java `javac` tiêu chuẩn

Ví dụ **riêng cho ngôn ngữ AspectJ** (cần AOP-aware compiler/weaver và các lớp đích phù hợp):

```java
package demo;

public aspect CheckoutFlowAspect {
    pointcut checkoutRoot():
        execution(* demo.CheckoutService.checkout(..));

    pointcut reservation():
        execution(* demo.InventoryRepository.reserve(..));

    before(): reservation() && cflow(checkoutRoot()) {
        System.out.println("reserve inside checkout");
    }
}
```

Ý nghĩa: advice `before` được chọn tại `reserve` khi một execution `checkout` phù hợp đang nằm trong dynamic flow. Lời gọi `reserve` từ một job độc lập không thỏa điều kiện flow. Cú pháp `..` là ký hiệu của pointcut language, không phải danh sách tham số Java trong mọi chỗ.

Ví dụ thay điều kiện bằng `cflowbelow(checkoutRoot())`:

```java
package demo;

public aspect CheckoutBelowAspect {
    pointcut root():
        execution(* demo.CheckoutService.checkout(..));

    before(): execution(* demo.InventoryRepository.reserve(..))
              && cflowbelow(root()) {
        System.out.println("reserve below checkout");
    }
}
```

Vì `reserve` là join point bên dưới `checkout`, cả hai ví dụ có thể cho kết quả tương tự ở trường hợp đơn giản. Muốn nhìn khác biệt, test thêm **chính root** và **nested root**. Hai đoạn trên không có `main`, không tự chạy bằng lệnh `javac` của ví dụ bên dưới.

**Không có bảo đảm chung** rằng mọi hệ AOP chấp nhận cú pháp trên hoặc hỗ trợ đủ field/constructor/method call join points. Dùng những đoạn này để học cách diễn đạt, không sao chép nguyên vào một công nghệ khác mà không kiểm tra.

## 8. Lọc theo flow không đồng nghĩa tạo aspect instance theo flow

Giả sử một singleton aspect có field `int total` và advice của nó chỉ chạy khi `cflow(P)`. Hai lượt root khác nhau vẫn có thể cập nhật **cùng một `total`**: `cflow` chỉ lọc việc chạy advice.

Một cơ chế **per-control-flow aspect instantiation** như `percflow(P)` trong những mô hình hỗ trợ có thể tạo/khôi phục instance riêng theo mỗi root activation. Đây là **vòng đời object**, không phải chỉ Boolean membership.

| Cần đạt | Cơ chế phù hợp để cân nhắc |
| --- | --- |
| Advice chỉ kích hoạt khi root còn hoạt động | `cflow(P)` |
| Chỉ áp tại các điểm bên dưới root | `cflowbelow(P)` |
| Một counter độc lập cho mỗi root | Per-flow instance hoặc scope được quản lý riêng |
| Một tổng counter cho tất cả root | Shared counter thread-safe |
| Giá trị chỉ sống trong một lần around advice | Biến cục bộ theo invocation |
| Context qua nhiều thread cho logical request | Explicit propagation/lifecycle, không chỉ cflow đồng bộ |

Nếu một root bên ngoài gọi root bên trong, có thể có nhiều instance theo activation. Cơ chế xác định **instance nào được advice sử dụng** phụ thuộc ngôn ngữ/công cụ, đặc biệt khi có binding theo flow; không suy chỉ từ tên `cflow`.

## 9. Binding đối số và dữ liệu ngữ cảnh

Một chính sách muốn biết **order ID của checkout root** ngay khi `reserve` chạy. Khái niệm cần tách:

- **Đối số của join point hiện tại**: ví dụ các tham số truyền vào `reserve`.
- **Đối số của root**: ví dụ `orderId` của `checkout` làm bắt đầu flow.
- **Target/this**: ngữ cảnh đối tượng ở join point đang xét.
- **Flow membership**: có một root phù hợp đang hoạt động không.
- **Flow instance state**: state được một instantiation policy quản lý nếu công nghệ hỗ trợ.

Không mặc định các đối số của `reserve` giống `checkout`. Với nested checkout, cũng không thể tự khẳng định một phép binding luôn trả **root ngoài cùng** hoặc **root gần nhất** khi chưa xem semantics của công nghệ.

Nếu cần lưu giá trị root trong scope thủ công, nên giữ dữ liệu **nhỏ, bất biến**, có thời hạn, thay vì giữ nguyên request/session/payload lớn. Lời gọi có exception phải dọn scope đúng; lời gọi trong thread pool không được dùng dữ liệu stale.

## 10. Java 17 runnable: mô hình root, cflowbelow, nested flow và async

**Đây là chương trình Java 17 thuần mô phỏng membership**, không phải compiler AOP hoặc ví dụ native `cflow` tự động. Nó dùng `ThreadLocal<Deque<String>>` để lưu các root được **mở tường minh** bởi `enterRoot`, giúp nhìn rõ khác biệt giữa root hiện tại và root cha. Thao tác `leaf` mô phỏng một join point con.

Tạo file `DynamicFlowPointcutDemo.java`:

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DynamicFlowPointcutDemo {
    private static final ThreadLocal<Deque<String>> ROOTS =
            new ThreadLocal<>();

    static List<String> names(Deque<String> stack) {
        if (stack == null || stack.isEmpty()) {
            return List.of();
        }
        ArrayList<String> result = new ArrayList<>(stack);
        Collections.reverse(result);
        return result;
    }

    static List<String> belowNewRoot(Deque<String> stack) {
        ArrayList<String> result = new ArrayList<>(stack);
        result.remove(0); // Root mới chưa nằm dưới chính nó.
        Collections.reverse(result);
        return result;
    }

    static void leaf(String name) {
        List<String> active = names(ROOTS.get());
        System.out.println("LEAF " + name
                + " cflow=" + active + " below=" + active);
    }

    static <T> T enterRoot(String name, Callable<T> body)
            throws Exception {
        Deque<String> stack = ROOTS.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            ROOTS.set(stack);
        }

        stack.push(name);
        System.out.println("ENTER " + name + " depth=" + stack.size());
        System.out.println("ROOT " + name
                + " cflow=" + names(stack)
                + " below=" + belowNewRoot(stack));

        try {
            return body.call();
        } finally {
            String removed = stack.pop();
            if (!removed.equals(name)) {
                throw new IllegalStateException("flow stack corrupted");
            }
            if (stack.isEmpty()) {
                ROOTS.remove();
            }
            System.out.println("EXIT " + name + " depth=" + stack.size());
        }
    }

    public static void main(String[] args) throws Exception {
        leaf("outside");

        enterRoot("outer", () -> {
            leaf("A");
            enterRoot("inner", () -> {
                leaf("B");
                return null;
            });
            leaf("C");
            return null;
        });

        try {
            enterRoot("error", () -> {
                leaf("throw-path");
                throw new IllegalArgumentException("stop");
            });
        } catch (IllegalArgumentException error) {
            System.out.println("CAUGHT " + error.getMessage());
        }
        leaf("after-error");

        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            enterRoot("parent", () -> {
                Future<?> one = pool.submit(
                        () -> leaf("async-default"));
                one.get();

                Future<?> two = pool.submit(() -> {
                    try {
                        enterRoot("child-async", () -> {
                            leaf("async-explicit");
                            return null;
                        });
                    } catch (Exception error) {
                        throw new RuntimeException(error);
                    }
                });
                two.get();

                leaf("after-task");
                return null;
            });
        } finally {
            pool.shutdown();
        }

        System.out.println("END");
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 DynamicFlowPointcutDemo.java
java DynamicFlowPointcutDemo
```

Stdout dự kiến chính xác:

```text
LEAF outside cflow=[] below=[]
ENTER outer depth=1
ROOT outer cflow=[outer] below=[]
LEAF A cflow=[outer] below=[outer]
ENTER inner depth=2
ROOT inner cflow=[outer, inner] below=[outer]
LEAF B cflow=[outer, inner] below=[outer, inner]
EXIT inner depth=1
LEAF C cflow=[outer] below=[outer]
EXIT outer depth=0
ENTER error depth=1
ROOT error cflow=[error] below=[]
LEAF throw-path cflow=[error] below=[error]
EXIT error depth=0
CAUGHT stop
LEAF after-error cflow=[] below=[]
ENTER parent depth=1
ROOT parent cflow=[parent] below=[]
LEAF async-default cflow=[] below=[]
ENTER child-async depth=1
ROOT child-async cflow=[child-async] below=[]
LEAF async-explicit cflow=[child-async] below=[child-async]
EXIT child-async depth=0
LEAF after-task cflow=[parent] below=[parent]
EXIT parent depth=0
END
```

### 10.1. Root outer: vì sao below trống?

`outer` vừa bắt đầu, nên nó thuộc `cflow` của chính mình. Nhưng root không “nằm dưới chính nó”; không có root cha nên `below=[]`. Điểm `leaf A` được gọi sau khi vào outer nên ở dưới outer: hai danh sách đều chứa `outer`.

### 10.2. Root inner: điểm kiểm tra dễ nhầm

`ROOT inner cflow=[outer, inner] below=[outer]` có nghĩa **inner là root của flow inner** nhưng vẫn **nằm dưới flow outer**. `leaf B` ở bên dưới cả hai root. Sau inner kết thúc, `leaf C` lại chỉ có outer; đó là tính chất khôi phục outer scope.

### 10.3. Khi root ném ngoại lệ

`enterRoot("error")` có `finally`. Dù body ném lỗi, nó pop root rồi `ROOTS.remove()` nếu stack rỗng. Sau đó caller catch `stop`, và `leaf after-error` không còn flow. Không cần chờ GC để kiểm chứng logic này.

### 10.4. Thread khác và explicit child root

`ExecutorService` chạy `async-default` trên worker với ThreadLocal riêng, nên nó in `cflow=[]` dù parent trên thread gọi vẫn đang hoạt động. Task thứ hai tự mở root **mới** `child-async` trên worker. Root đó **không phải phần mở rộng tự động của call stack parent**. `Future.get()` giúp thứ tự stdout xác định.

### 10.5. Giới hạn mô hình

Mô hình dùng `enterRoot()` tường minh và `leaf()` thủ công; nó không weave các method execution/call join points. `ThreadLocal` hỗ trợ dữ liệu per-thread đồng bộ nhưng không tự xác định pointcut, không đảm bảo lifetime của logical request xuyên async và không bảo đảm dữ liệu được truyền sang executor. Trạng thái stack không được truy cập đồng thời từ nhiều thread trong ví dụ.

## 11. Đệ quy và số lượng root activation

Khi `checkout(3)` gọi `checkout(2)`, rồi `checkout(1)`, ba execution P có thể đang hoạt động. Một điểm con dưới mức 1 có thể nằm trong flow của cả ba.

Đó không có nghĩa một `before` advice với điều kiện `cflow(P)` chắc chắn chạy **ba lần** tại cùng một join point. Membership Boolean là **có root phù hợp hay không**. Số lần chạy advice, việc áp vào bao nhiêu instance và precedence do công nghệ quy định.

Một cài đặt thủ công chỉ dùng Boolean:

```text
enter P: inFlow = true
exit P:  inFlow = false
```

sẽ sai khi inner thoát còn outer đang hoạt động. Nếu chỉ cần biết membership, counter depth với `try/finally` có thể đủ; nếu cần tên/đối số/state của từng root, một stack activation phù hợp hơn.

## 12. Giới hạn với proxy, weaving và self-invocation

**JDK dynamic proxy** thường chỉ chặn lời gọi đi qua **đối tượng proxy** và những phương thức của giao diện được đại diện. Một target gọi `this.inner()` có thể bypass interception của inner. Caller có tham chiếu target gốc và gọi trực tiếp cũng có thể bỏ qua proxy.

Điều này **khác** với việc một phương thức tự nó nằm trong dynamic call stack của root. Một helper ở cùng thread có thể đọc context thủ công nhưng không có advice riêng nếu cơ chế proxy không chặn nó.

Một weaver có thể chọn execution join point ở bytecode của target hoặc call join point ở phía caller, tùy loại điểm hỗ trợ và lớp đã weave. Nếu chỉ xử lý code của target, không mặc định đã xử lý mọi call-site ở các caller bên ngoài.

Ba câu hỏi kiểm chứng riêng:

1. Lời gọi có thuộc **flow P** theo đường thực thi không?
2. Join point cần can thiệp có được **cơ chế AOP áp dụng** thật không?
3. Dữ liệu/instance aspect theo flow có được **tạo và dọn đúng** không?

Có cho câu thứ nhất **không tự chứng minh** hai câu còn lại.

## 13. `ThreadLocal`, thread pool và asynchronous propagation

Một worker pool thường tái sử dụng thread. Nếu code thủ công đặt `ThreadLocal` khi vào root nhưng quên `remove()`, tác vụ sau có thể đọc flow/context **stale** từ tác vụ trước. Đây là lỗi dữ liệu và nguy cơ giữ bộ nhớ, nhất là khi state tham chiếu tới request/session/payload.

Không đồng nhất `ThreadLocal` với native `cflow`. Nếu tự quản lý một scope per-call:

```text
previous = currentContext
install(newContext)
try:
    executeTask()
finally:
    restore(previous) or remove()
```

Với nested scope, phải **khôi phục parent**, không remove toàn bộ state của outer. Nếu task trên thread khác, nên capture những metadata tối thiểu, bất biến (ví dụ correlation ID) lúc submit và cài một scope mới trên worker lúc chạy; sau đó restore/cleanup.

`InheritableThreadLocal` có thể kế thừa giá trị ở **thời điểm tạo thread**, không tạo propagation per-submit tự động cho thread pool. Một callback/future có thể hoàn tất sau khi method root đồng bộ đã trả về. Copy một ID **không đồng nghĩa native `cflow` kéo dài qua thread**.

Nếu cần “toàn bộ logical checkout kể cả tác vụ async”, phải định nghĩa lifecycle và propagation riêng, không suy từ stack quan hệ đồng bộ.

## 14. Thứ tự aspect và luồng điều khiển

Vị trí của flow-root advice so với Cache, Authorization, Retry có thể thay đổi phạm vi quan sát:

```text
FlowScope(Retry(Target))    -- một flow có thể bao nhiều attempts
Retry(FlowScope(Target))    -- có thể có flow riêng mỗi attempt
```

Trong mô hình around advice, `proceed` có thể chạy phần còn lại của chain. Nếu Cache trả sớm **trước** điểm mở root, flow chưa hình thành. Nếu root được mở bên ngoài Cache, một logical call vẫn có thể có flow dù target không được thực thi.

Với retry, điều quan trọng là **mỗi attempt thực sự đi lại qua join point root hay không**. Các cơ chế AOP có thể khác nhau về khả năng gọi `proceed` nhiều lần.

Cần phân biệt các loại chỉ số:

- **Logical requests**: bao nhiêu yêu cầu được tiếp nhận;
- **Attempts**: bao nhiêu lần thử gọi target;
- **Target executions**: thực sự có bao nhiêu execution;
- **Effects committed**: nghiệp vụ tạo bao nhiêu tác dụng phụ;
- **Advice invocations**: lời gọi advice khớp bao nhiêu lần.

Pointcut theo flow không tự gộp tất cả thành một đại lượng.

## 15. Tối ưu hiệu năng và độ phủ

`cflow` là điều kiện phụ thuộc runtime; công cụ có thể tối ưu phần tĩnh rồi kiểm tra trạng thái flow tại những điểm còn lại. Không mặc định mọi weaver đều dùng stack trace ở mỗi join point.

Một chính sách tốt cần:

- Root pointcut hẹp, đúng chữ ký và overload;
- Bộ lọc join point con chính xác, ví dụ chỉ `Repository.reserve`;
- Không vô tình chọn logging/trace infrastructure mà advice sẽ gọi lại;
- Thử cả match và non-match path;
- Kiểm tra nested/recursive calls và exception cleanup;
- Benchmark trên hot path cùng logging/serialization thực tế;
- Xác minh toolchain đã weave đúng classes/artifact.

Nếu `cflow(P)` đứng một mình và chọn quá nhiều điểm, chi phí có thể lớn hơn yêu cầu. Nếu root bị bỏ sót do config/overload/không đi qua proxy, advice có thể **không chạy** dù người phát triển tưởng đã bảo vệ toàn bộ thao tác.

**Lưu ý bảo mật:** một guard chỉ chạy dưới `cflow(checkout)` sẽ không bảo vệ một đường độc lập gọi tới effect ngoài checkout. Chính sách bảo mật bắt buộc cần ranh giới đáng tin và test mọi đường bypass; không dùng cflow làm bảo đảm quyền truy cập duy nhất.

## 16. Ma trận kiểm thử

| Trường hợp | Điều phải kiểm chứng |
| --- | --- |
| Điểm con đúng chữ ký trong root | Điều kiện `cflow` khớp |
| Cùng điểm con ngoài root | Không khớp |
| Chính root P không lồng | `cflow` khớp, `cflowbelow` không |
| Điểm con nhiều tầng dưới P | Hai điều kiện flow khớp theo semantics |
| Root P bên trong root P khác | Root trong có thể thỏa below của root ngoài |
| Recursion P nhiều mức | Depth/activation đúng, không nhân số advice vô cớ |
| Root return bình thường | Scope của root kết thúc |
| Root throw | Scope được dọn, lỗi gốc được giữ |
| Overload P khác | Khớp/không khớp đúng chữ ký |
| Method call vs execution | Đúng join point kind và mã được weave |
| Advice tự gọi code khớp pointcut | Không đệ quy hoặc áp thừa ngoài ý muốn |
| Proxy self-invocation/direct target | Đường bypass được hiểu và kiểm soát |
| Thread A submit worker B | Không mặc định B có call flow từ A |
| Thread pool tái sử dụng | Không còn state stale |
| Singleton aspect dùng cflow | Không tự tuyên bố instance độc lập mỗi root |
| Per-flow instantiation nếu hỗ trợ | State theo root được tách và cleanup đúng |
| Artifact đã weave | Kiểm thử trên output triển khai thật |
| Advice quan sát | Giữ nguyên return/exception và số hiệu ứng |

Ngoài test đúng pointcut, cần test **tác động nghiệp vụ**: một around advice có thể gọi tiếp nhiều lần, đổi ngoại lệ hoặc trả fallback, khiến số lần effect không còn như mong đợi.

## 17. Sai lầm thường gặp

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Coi lexical package scope là dynamic call flow | Chọn nhầm các đường gọi | Dùng điều kiện đúng về root đang hoạt động |
| Coi `cflowbelow` là luôn loại mọi execution P | Bỏ qua root lồng nhau | Xét outer activation |
| Dùng Boolean global `inFlow` | Sai khi thread khác/nested root | Depth/scope có ownership |
| Chỉ cleanup ở after-returning | Rò flow sau exception | `finally` và nested restore |
| Coi `cflow` tự tạo state per-request | State singleton bị chia sẻ | Per-flow policy hoặc scoped state |
| Coi ThreadLocal là request scope | Worker pool giữ giá trị cũ | Scope per-task, `remove`/restore |
| Truyền correlation ID rồi nói đã kéo dài cflow | Suy sai ngữ nghĩa call stack | Propagation/lifecycle riêng |
| Đặt root pointcut quá rộng | Tăng overhead, áp advice thừa | Thu hẹp loại/chữ ký root |
| Không lọc điểm con | Có thể chọn nhiều join point ngoài ý định | Ghép `execution`/bộ lọc tương ứng |
| Nhầm method call với execution | Weaving sai phía caller/target | Kiểm tra join point model |
| Tin proxy xử lý self-invocation | Thiếu advice trên đường nội bộ | Xác minh đường gọi và reference |
| Cho rằng cflow bảo vệ mọi effect | Đường ngoài root bypass guard | Bảo vệ ranh giới đáng tin |

## 18. Bài tập và đáp án

**Bài 1.** `Repository.load` chạy từ checkout A và một maintenance job B. Pointcut là `execution(load) AND cflow(execution(checkout))`. Lượt nào khớp?

**Đáp án:** A khớp khi checkout root còn hoạt động và các điểm được weaver hỗ trợ; B không vì không có root checkout trong đường gọi.

**Bài 2.** Chính root P không lồng đang chạy. So sánh `cflow(P)` và `cflowbelow(P)`.

**Đáp án:** `cflow` thường gồm root; `cflowbelow` thường loại chính root ấy.

**Bài 3.** `P(outer) -> P(inner) -> child`. Tại execution P(inner), `cflowbelow(P)` có thể đúng không?

**Đáp án:** Có, bởi P(inner) nằm bên dưới root P(outer), dù không nằm dưới chính mình.

**Bài 4.** Ba lời gọi P đệ quy đang hoạt động. Advice `before(): child && cflow(P)` phải chạy ba lần trên một execution child không?

**Đáp án:** Không suy như vậy. Có nhiều root activation không tự đồng nghĩa nhiều lần advice dispatch tại cùng join point.

**Bài 5.** Một singleton aspect có `int counter` và advice chỉ chạy trong `cflow(P)`. Nó có tự đếm riêng cho từng root không?

**Đáp án:** Không. Điều kiện flow không tạo instance riêng. Cần instantiation/state theo scope nếu muốn dữ liệu tách biệt.

**Bài 6.** Worker B thực thi task submit từ root trên thread A. Có tự nằm trong `cflow(P)` đồng bộ của A không?

**Đáp án:** Không. Task trên B có call stack riêng. Truyền một trace ID không tự mở rộng pointcut cflow.

**Bài 7.** Target `this.inner()` từ phương thức ngoài đang được proxy chặn. Có chắc advice riêng ở inner chạy không?

**Đáp án:** Không. Self-invocation qua target có thể bypass proxy; đó là vấn đề coverage khác với membership động trong call stack.

**Bài 8.** Cần đo chỉ `Repository.save` trong flow checkout, nên dùng mỗi `cflow(checkout)` hay thêm bộ lọc execution của save?

**Đáp án:** Ghép cả hai. Điều kiện flow một mình có thể chọn nhiều điểm hơn mục tiêu, làm tăng overhead.

**Bài 9.** Root kết thúc do exception, context thủ công chỉ dọn ở after-returning. Nguy cơ gì?

**Đáp án:** State stale có thể tồn tại sau lỗi và rò sang lần gọi khác. Dọn trong finally và xử lý nested scopes.

**Bài 10.** Muốn chặn mọi tác dụng phụ của một phương thức nhạy cảm, có thể dùng duy nhất guard trong `cflow(checkout)` không?

**Đáp án:** Không nếu còn đường gọi effect ngoài checkout. Kiểm soát phải nằm ở ranh giới đáng tin, được xác minh bằng test độ phủ.

## 19. Tóm tắt

- **Pointcut theo dynamic control flow** phụ thuộc root đang hoạt động trên call stack; nó không chỉ chọn theo vị trí mã nguồn.
- **`cflow(P)`** thường bao gồm root và các điểm con; **`cflowbelow(P)`** chọn phần bên dưới một root, nhưng **nested P** có thể nằm dưới P ngoài.
- Ghép điều kiện flow với bộ lọc join point **chính xác**; phân biệt method call và method execution.
- **Lọc theo flow khác tạo aspect instance per-flow**; binding dữ liệu, lifecycle và context propagation là những hợp đồng riêng.
- **Nested/recursive flows** cần quản lý depth/stack và cleanup trên cả return/throw.
- **Executor, thread pool, async** không tự tiếp nối call stack đồng bộ; ThreadLocal không phải request scope và propagation ID không phải native cflow.
- Test dương, âm, nested, ngoại lệ, bypass và woven artifact; không dựa vào cflow như guard bảo mật duy nhất.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-control-flow-002">Phân biệt điểm khởi đầu với các điểm nằm bên dưới luồng gọi</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt điểm khởi đầu với các điểm nằm bên dưới luồng gọi trong AOP

Khi thiết kế **Aspect-Oriented Programming (AOP – lập trình hướng khía cạnh)** theo **luồng điều khiển động (dynamic control flow)**, đôi khi ta không chỉ muốn chọn một join point bởi tên hoặc chữ ký phương thức, mà còn muốn biết: **join point đang xảy ra có nằm trong quá trình xử lý được bắt đầu bởi một join point khác hay không?**

Một nhầm lẫn quan trọng là đánh đồng **điểm khởi đầu** với **những điểm xảy ra ở bên dưới nó**. Hai kiểu lựa chọn thường được diễn đạt bởi các khái niệm:

- **`cflow(P)` – control flow của P:** chọn join point khớp P đóng vai trò bắt đầu luồng **và** các join point xảy ra động bên trong quá trình xử lý đó.
- **`cflowbelow(P)` – control flow nằm dưới P:** chọn các join point xảy ra **bên dưới** một lần khớp P đang hoạt động, thay vì chính lần P tạo ra phạm vi ấy.

Ở trường hợp đơn giản chỉ có **một P ngoài cùng**, `cflow(P)` bao gồm P và hậu duệ động; `cflowbelow(P)` bao gồm hậu duệ nhưng không tính P ngoài cùng. Tuy nhiên, **nếu một P khác được gọi lồng nhau/đệ quy dưới P ngoài**, chính P bên trong đó vẫn có thể được `cflowbelow(P)` chọn: nó **nằm dưới lần P ngoài**, dù bản thân cũng khớp P. Vì thế không nên mô tả `cflowbelow(P)` là “loại trừ mọi join point khớp P” trong mọi tình huống.

Đây là **khái niệm AOP tổng quát** về phạm vi control-flow. Các tên cú pháp trên phổ biến trong những ngôn ngữ pointcut có khả năng chọn control flow; **không phải mọi AOP proxy/interceptor tự hỗ trợ cùng cú pháp hay tập join point**. Bài phân tích ngữ nghĩa, vận dụng, trường hợp lồng/đệ quy, khác biệt tĩnh–động, xử lý async và cung cấp ví dụ **Java 17 độc lập** để trực quan hóa các phạm vi.

## 1. Chọn điểm khởi đầu và các điểm bên dưới

Trong AOP, **join point** là sự kiện can thiệp được mô hình hỗ trợ; **pointcut** chọn join point và **advice** chạy tại điểm được chọn nếu cơ chế weaving/interception thực sự bao phủ đường gọi. Với một pointcut động theo luồng điều khiển, cần phân biệt **occurrence P mở phạm vi** với các occurrence **xảy ra bên dưới P khi P còn hoạt động**.

~~~text
Caller
  └─ P: Checkout.process()  ← gốc
       ├─ validate()         ← bên dưới P
       └─ reserve()          ← bên dưới P
Caller tiếp tục sau khi P kết thúc → ngoài phạm vi P
~~~

Hai câu hỏi chính của bài: **tại chính occurrence P** thì điều kiện có đúng không, và **tại một occurrence P lồng trong P khác** thì vì sao quan hệ “bên dưới” lại có thể đúng? Điều này phụ thuộc *dynamic invocation tree*, không phải chỉ tên method, vị trí source hoặc mối liên hệ nghiệp vụ suy đoán.

**Ranh giới quan trọng:** P có thể là method call hoặc method execution tùy công cụ, và chúng không phải cùng một join point. Trong proxy, chỉ đường đi qua proxy được quan sát; trong bytecode weaving, class/call site phải thực sự được weave. Các phần sau dùng tập hợp occurrence và ví dụ đệ quy để phân định chính xác root với descendant.

## 2. Công thức tư duy: bao gồm gốc và loại trừ gốc của cùng một phạm vi

Giả sử có một lần xảy ra join point **p** khớp P.

~~~text
                p = start of control flow P
                |
                +--> j1
                |     |
                |     +--> j2
                |
                +--> j3
~~~

Theo cách mô hình hóa một **instance control-flow** bắt đầu tại p:

~~~text
cflow_started_at(p)      = {p, j1, j2, j3}
cflowbelow_started_at(p) = {j1, j2, j3}
~~~

Đây là **ký hiệu tập hợp giải thích ý nghĩa**, không phải cú pháp của một ngôn ngữ AOP. Nó chỉ xét **một** p cụ thể. Khi P có nhiều lần xuất hiện, cần xét **hợp của những phạm vi đang hoạt động**.

Một tiêu chí ở runtime:

- `cflow(P)` đúng tại j nếu **có ít nhất một** join point p khớp P sao cho j là chính p **hoặc** nằm dưới p trong luồng điều khiển động.
- `cflowbelow(P)` đúng tại j nếu **có ít nhất một** join point p khớp P sao cho j nằm **bên dưới** p (j không phải chính p ấy).

Điểm tinh tế nằm ở từ **“có ít nhất một”**. Một j có thể chính là điểm khởi đầu của phạm vi P bên trong, đồng thời là điểm con của một P bên ngoài.

### 2.1 Ví dụ lồng nhau

~~~text
P_outer()                      <-- khớp P, bắt đầu flow ngoài
  |
  +--> helper()                <-- ở dưới P_outer
  |
  +--> P_inner()               <-- cũng khớp P
         |
         +--> nestedWork()     <-- ở dưới P_outer lẫn P_inner
~~~

Ở `P_outer`: có `cflow(P)`; chưa có `cflowbelow(P)` khi đó là root ngoài cùng.

Ở `P_inner`: có `cflow(P)` **và** `cflowbelow(P)`, vì `P_inner` nằm **dưới P_outer**. Không thể nói "mọi execution của một phương thức khớp P đều bị loại bởi `cflowbelow(P)`".

Ở `nestedWork`: cả hai điều kiện đều đúng.

### 2.2 Phân biệt điểm (join point) và một đoạn code

Một phương thức có thể được gọi nhiều lần. `P_outer` và `P_inner` trong sơ đồ có thể cùng là **một phương thức `process()` được gọi đệ quy**, nhưng là **hai lần occurrence của join point** trong runtime. Chúng có vai trò root khác nhau, tùy stack động tại thời điểm đó.

Một bảng phân loại theo **method name** cố định không thể thay thế việc xem **occurrence và active flow**.

## 3. Vì sao đây là dynamic control flow, không phải phạm vi từ vựng?

Xét mã minh họa:

~~~java
void start() {
    helper();
}

void helper() {
    perform();
}
~~~

Đây chỉ là một mảnh Java, chưa là chương trình độc lập. Khi `helper()` thực sự được gọi từ `start()`, execution của `perform()` nằm bên dưới start, nếu P chọn execution của `start()`.

Nhưng cùng `helper()` có thể được gọi từ đường khác:

~~~java
void unrelated() {
    helper();
}
~~~

Khi gọi `unrelated()` trực tiếp **không qua start**, `helper()` và `perform()` **không nằm trong cflow(P)**, dù đây là **cùng source code** và cùng method signature.

**Kết luận:** pointcut control-flow không chỉ xét method nằm trong class/package nào hoặc được viết bên trong method khác. Nó xét **đường thực thi đã xảy ra**, chứ không chỉ nhìn cấu trúc source.

### 3.1 So với lựa chọn theo phạm vi mã tĩnh

| Loại lựa chọn | Căn cứ | Có đổi theo caller runtime? |
| --- | --- | --- |
| Signature/metadata matching | Method name, owner type, annotation, modifier | Thường mang phần cấu trúc cố định |
| Lexical scope | Vị trí join point được định nghĩa trong type/code block | Cơ bản dựa trên cấu trúc source/bytecode |
| Dynamic control flow | Một P có đang active trên đường thực thi không | Có |
| Runtime argument/target tests | Tham số và object của occurrence hiện tại | Có thể có |
| Proxy boundary | Caller có thực sự gọi qua proxy được instrument không | Có thể có |

Một biểu thức P có thể là **structural pointcut**, còn phần `cflow(P)` thêm **điều kiện động**: join point hiện tại nằm trong phạm vi thực thi được khởi phát bởi P hay không.

### 3.2 Call graph tĩnh không đủ

Một sơ đồ call graph tĩnh chỉ nói `start()` **có thể** gọi `perform()`. Nhưng nếu một nhánh `if` không được đi vào, hoặc method thực tế không chạy, sẽ **không có occurrence** tương ứng để advice được áp dụng. Dynamic pointcut không tạo sự kiện cho những phương thức chưa chạy.

Một indirect call qua interface, callback đồng bộ hay dispatch đa hình vẫn có thể nằm trong cflow nếu lời gọi thực tế xảy ra **bên dưới root đang active** và mô hình AOP quan sát được join point đó.

## 4. Phân biệt method-call và method-execution tại điểm khởi đầu

Trong các mô hình có cả **method call join point** và **method execution join point**, cùng một lời gọi source có thể gắn các điểm khác nhau:

~~~text
Caller.method()
    |
    | method-call join point tại call site
    v
Target.operation()
    | method-execution join point tại target body
    v
  business implementation
~~~

Nếu P chọn **call** của `operation()`, phạm vi gốc bắt đầu tại **call join point**. Nếu P chọn **execution** của `operation()`, gốc bắt đầu tại **execution join point**.

Hai phạm vi thường chồng lên nhau trong một lời gọi đồng bộ bình thường, nhưng **không được mặc định giống nhau** trong mọi tình huống:
- Một call có thể đi qua dispatch khác hoặc ném lỗi trước execution target.
- Một method-execution có thể xảy ra từ call site không được mô hình/công cụ chọn.
- Proxy interception thường quan sát **lời gọi qua ranh giới proxy**, không phải toàn bộ method execution trên mọi đường.
- Dynamic bytecode weaving có thể có khả năng điểm nối khác với một wrapper thủ công.

**Cần ghi cụ thể P chọn loại join point nào.** Nói “cflow của hàm `checkout`” mà không nói call hay execution có thể khiến test hiểu sai ngay tại ranh giới gốc.

### 4.1 Nếu P chọn nhiều phương thức

Ví dụ P chọn `execute` và `retry`. Khi `retry` chạy bên trong `execute`, một occurrence khớp P có thể nằm dưới occurrence P khác. Khi ấy **chính occurrence `retry`** có thể khớp `cflowbelow(P)`.

Muốn "chỉ loại gốc khởi đầu ở mức ngoài cùng", phải định nghĩa rõ **outermost root** theo hợp đồng của bài toán; đừng mặc định `cflowbelow(P)` sẽ loại mọi occurrence match P.

## 5. Ví dụ Java 17 hoàn chỉnh: gốc, hậu duệ và root đệ quy

Chương trình sau dùng Java 17 thuần, **không dùng bộ weaver**. Nó đặt một `ThreadLocal<Integer>` làm bộ đếm số root P **đang active trên thread hiện tại**, tăng khi bắt đầu một root và giảm/khôi phục trong `finally`.

Ta mô phỏng hai nhóm điểm:
- **Root event**: execution occurrence của `root(...)`, giả định khớp P. Tại root, `cflow(P)=true`. `cflowbelow(P)` chỉ true nếu đã có root P **bên ngoài**.
- **Other event**: execution occurrence của `leaf(...)`, không khớp P. Nếu đang có ít nhất một root active, cả `cflow(P)` lẫn `cflowbelow(P)` đều true.

Chương trình còn dùng executor một thread để minh họa **`ThreadLocal` của caller không tự chuyển sang một worker thread khác**. Theo ý nghĩa **dynamic call stack**, một task được queue sang worker cũng **không còn tự là hậu duệ cùng call stack** chỉ vì được lên lịch khi root đang hoạt động.

Lưu toàn bộ thành `ControlFlowEntryDemo.java`:

~~~java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ControlFlowEntryDemo {
    // Số lần execution khớp P đang bao quanh đường gọi trên thread.
    private static final ThreadLocal<Integer> ROOT_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    static String inspect(String name, boolean isRootP) {
        int depth = ROOT_DEPTH.get();

        boolean inCflow = depth > 0;
        boolean inCflowBelow =
                isRootP ? depth > 1 : depth > 0;

        return name
                + " depth=" + depth
                + " cflow=" + inCflow
                + " cflowbelow=" + inCflowBelow;
    }

    static void leaf(String name) {
        System.out.println(inspect("leaf:" + name, false));
    }

    static void root(
            String name,
            boolean callNestedRoot,
            ExecutorService executor)
            throws Exception {
        int previous = ROOT_DEPTH.get();
        ROOT_DEPTH.set(previous + 1);

        try {
            // Đây là occurrence của root execution được mô phỏng.
            System.out.println(inspect("root:" + name, true));
            leaf(name + "-a");

            if (callNestedRoot) {
                root("inner", false, executor);
            }

            if ("outer".equals(name)) {
                // Worker khác không có ROOT_DEPTH của caller.
                Future<String> async = executor.submit(
                        () -> inspect("async-worker", false));
                System.out.println(async.get());
            }

            leaf(name + "-b");
        } finally {
            // Đóng đúng phạm vi: không phá root bên ngoài.
            if (previous == 0) {
                ROOT_DEPTH.remove();
            } else {
                ROOT_DEPTH.set(previous);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println(inspect("outside-before", false));

        ExecutorService executor =
                Executors.newSingleThreadExecutor();
        try {
            root("outer", true, executor);
        } finally {
            executor.shutdown();
        }

        System.out.println(inspect("outside-after", false));
    }
}
~~~

Biên dịch/chạy bằng JDK 17+:

~~~bash
javac ControlFlowEntryDemo.java
java ControlFlowEntryDemo
~~~

Stdout dự kiến và có thứ tự xác định:

~~~text
outside-before depth=0 cflow=false cflowbelow=false
root:outer depth=1 cflow=true cflowbelow=false
leaf:outer-a depth=1 cflow=true cflowbelow=true
root:inner depth=2 cflow=true cflowbelow=true
leaf:inner-a depth=2 cflow=true cflowbelow=true
leaf:inner-b depth=2 cflow=true cflowbelow=true
async-worker depth=0 cflow=false cflowbelow=false
leaf:outer-b depth=1 cflow=true cflowbelow=true
outside-after depth=0 cflow=false cflowbelow=false
~~~

### 5.1 Vì sao `root:outer` không ở below?

Ở root đầu tiên, `ROOT_DEPTH=1`. Chính occurrence `root:outer` là **điểm khởi đầu của phạm vi đang xét**, không có P khác trên stack ngoài nó. `cflow(P)` true vì bao gồm root; `cflowbelow(P)` false vì chưa có **root P ở phía trên**.

### 5.2 Vì sao `leaf:outer-a` ở dưới P?

`leaf` không phải root P; nhưng khi nó được gọi, root ngoài còn active (`depth=1`). Nó là **hậu duệ động**, nên cả `cflow(P)` và `cflowbelow(P)` đều true.

### 5.3 Vì sao `root:inner` vẫn là `cflowbelow(P)`?

Đây là tình huống quan trọng nhất. Khi `root("inner")` được gọi trong `root("outer")`, **hai occurrence của P đang lồng nhau** và `depth=2`. `root:inner`:
- Là chính P **mới**, nên tự khởi đầu một phạm vi `cflow(P)`.
- Đồng thời **nằm bên dưới P ngoài**, nên khớp `cflowbelow(P)` nếu xem quan hệ với P ngoài.

Suy ra: `cflowbelow(P)` không có nghĩa "mọi method cũng khớp P đều không được chọn". Nó có nghĩa "bên dưới **một occurrence P đang hoạt động**"; cùng một join point có thể là root trong và là hậu duệ của root ngoài.

### 5.4 Vì sao `async-worker` không ở dưới P?

`executor.submit(...)` diễn ra khi `root:outer` vẫn active **trên caller thread**, nhưng callable chạy **trên thread pool worker khác**. `ThreadLocal` của caller không tự chuyển sang worker, và **dynamic call stack** của worker không chứa execution occurrence P của caller.

Điều này **không ngăn** ta chủ động truyền một `traceId` hoặc logical operation context sang task. Nhưng **propagate logical trace** và **đúng là hậu duệ trong dynamic call stack** là hai khái niệm khác nhau. Không nên giả định `cflow(P)` tự áp dụng trên mọi executor task nếu mô hình không định nghĩa cách mở rộng phạm vi qua async boundary.

### 5.5 Vì sao có `finally`?

Phạm vi root phải chấm dứt khi execution root trả về **hoặc ném ngoại lệ**. `finally` giúp:
- Giảm độ sâu khi root lồng nhau kết thúc.
- Khôi phục độ sâu root ngoài nếu còn.
- `remove()` nếu không còn root active, tránh ThreadLocal tồn tại trên thread pool lâu hơn cần thiết.

**Giới hạn:** đây là một **mô hình theo dõi control-flow tự xây**, không phải hiện thực đầy đủ của mọi `cflow/cflowbelow` pointcut. Một hệ thống weaving có thể theo dõi occurrence, bindings và loại join point phức tạp hơn; ở đây `isRootP` được truyền tường minh để minh họa điều kiện.

## 6. Phân biệt cflow và cflowbelow bằng biểu thức pointcut khái niệm

Giả sử P chọn execution của một entrypoint:

~~~text
P = execution of Checkout.process(..)

Select cflow(P):
    entrypoint execution itself
    AND every observable join point dynamically inside it

Select cflowbelow(P):
    join points dynamically underneath an active P
    excluding the particular P occurrence that starts that scope
~~~

Đây là **giả mã diễn đạt**, không phải cú pháp dùng chung cho mọi bộ AOP. Với cùng lời gọi:

~~~text
Checkout.process()     <-- cflow yes, cflowbelow no (nếu root ngoài)
  Inventory.check()    <-- cflow yes, cflowbelow yes
  Checkout.process()   <-- cflow yes, cflowbelow yes (P lồng dưới P ngoài)
~~~

### 6.1 Khi dùng `cflow(P)`

Chọn `cflow(P)` khi concern **cần áp dụng cả ở chính root và những join point dưới nó**, theo khả năng của mô hình. Ví dụ:
- Tạo một phạm vi quan sát bao gồm cả entrypoint.
- Áp dụng một chính sách logging/metrics cho mọi join point thuộc thao tác logic khởi đầu từ P.
- Phân loại các sự kiện bên trong một hoạt động được bắt đầu tại root P.

Nhưng khi advice được áp dụng **tại P** và lại gọi helper cũng thuộc pointcut, cần cân nhắc **đệ quy instrumentation** và chi phí quan sát. Không phải cứ chọn `cflow(P)` là đủ cho bảo mật: một lời gọi trực tiếp nằm ngoài phạm vi P vẫn có thể tồn tại.

### 6.2 Khi dùng `cflowbelow(P)`

Chọn `cflowbelow(P)` khi concern **chỉ có ý nghĩa với hoạt động con sau khi một entrypoint đã được bắt đầu**, ví dụ:
- Đo số lượng internal method executions bên dưới một use case, nhưng không tính execution entrypoint ấy như một "internal call".
- Chỉ áp dụng diagnostic logging cho các bước bên trong một thao tác lớn.
- Phân biệt root latency với latency của các thành phần con.

**Đặt câu hỏi về P lồng nhau:** nếu P có thể tự gọi lại hoặc nhiều P occurrences xuất hiện trong một flow, inner P có thể được tính như một điểm nằm bên dưới. Nếu yêu cầu phải loại tất cả inner P theo signature, cần **thêm một điều kiện exclude phù hợp** hoặc thiết kế guard/outermost matching riêng theo hợp đồng; đừng đổi nghĩa của `cflowbelow(P)`.

## 7. Control flow và pointcut binding

Một số mô hình AOP cho phép P **ràng buộc giá trị** (binding) từ join point khởi đầu để advice ở bên dưới dùng, chẳng hạn một request ID hoặc tham số đã được kiểm tra.

Giả mã:

~~~text
P(requestId) = execution of Checkout.process(requestId)

for join points in cflow(P(requestId)):
    emit safeEvent(operation, requestId)
~~~

Điều cần xác định:
1. Giá trị đến từ **root occurrence nào** khi có nhiều P lồng nhau?
2. Có dùng giá trị root gần nhất, root ngoài cùng hay một collection of contexts?
3. Nếu có nhiều root phù hợp, cách xử lý binding của nền tảng là gì?
4. Giá trị có được phép mang tới advice khác về bảo mật và retention không?
5. Với tham số mutable, có cần snapshot để không bị thay sau lúc root bắt đầu?
6. Khi root kết thúc, binding được dọn đúng không?

**Không tự bịa** một quy tắc binding chung: một ngôn ngữ AOP cụ thể sẽ có semantics riêng về `cflow`, tham số và precedence khi nhiều pointcut match.

### 7.1 Root gần nhất và root ngoài cùng khác nhau

~~~text
P(tenant A)
  helper()
  P(tenant B)
    charge()
~~~

Một advice ở `charge()` có thể nằm dưới **cả hai** P occurrences. Nếu chỉ có một trường `currentTenant`, phải quy định nó mang tenant A hay B, và quyền thực tế phải được xác minh như thế nào.

Một **stack of scoped contexts** có thể phù hợp cho một instrumentation tự viết, nhưng security không thể chỉ dựa vào chuỗi tenant được binding từ một method không đáng tin. Với authorization, source xác thực và principal phải được xử lý độc lập.

## 8. Ứng dụng thường gặp

### 8.1 Tracing: root span và child spans

Trong trace một thao tác:
- **Root span** thường được tạo tại entrypoint.
- **Child span** tạo cho các bước nội bộ.
- Một điểm **bên dưới root** có thể là vị trí hợp lý để tạo child span, tránh tạo thêm root-level span trên cùng một entrypoint.

Điều quan trọng: tracing logical operation **có thể kéo dài qua async và remote boundary**, trong khi dynamic call-stack `cflow` theo nghĩa chặt có thể không. Đừng đồng nhất cấu trúc parent/child spans với stack frame thực của một thread.

### 8.2 Audit cho use case

Một thao tác có thể phát sự kiện:
- START ở entrypoint.
- ACTION cho các bước quan trọng bên dưới.
- SUCCESS/ERROR khi root kết thúc.

Nếu audit bắt buộc ghi denied requests ngay tại entrypoint, chỉ dùng `cflowbelow(P)` có thể **bỏ qua chính root** và những lần bị từ chối trước khi có hoạt động con. Cần thiết kế việc ghi nhận ở đúng ranh giới, với event schema an toàn.

### 8.3 Performance profiling

Nếu đo `cflow(P)` và ghi metric cho mọi method, có thể **đếm cả root và các phương thức con**. Tổng thời gian các method có thể lớn hơn wall-clock của request do lồng nhau; không cộng mù quáng mà coi là request latency.

Nếu muốn số method nội bộ, `cflowbelow(P)` có thể giúp loại root ngoài cùng trong trường hợp không có P lồng. Nếu P đệ quy, phải quyết định có tính nested root như một internal method hay không.

### 8.4 Validation và phân quyền

Một chính sách validation có thể chỉ cần áp dụng tại entrypoint. Một chính sách quyền có thể cần áp dụng cả ở các thao tác con độc lập, kể cả khi chúng được gọi ngoài flow P. Sử dụng `cflow(P)` như **điều kiện duy nhất** cho authorization thường có nguy cơ để lọt đường gọi không bắt đầu từ P.

Hãy xác định những **invariants bắt buộc** trước rồi mới dùng dynamic pointcut để tối ưu hóa observability hoặc chọn những hoạt động liên quan.

## 9. Ngữ nghĩa với before, after, around và ngoại lệ

### 9.1 Before advice

Nếu before advice chọn `cflow(P)`, nó có thể chạy ở occurrence root P và cả các join point khác trong control flow, tùy pointcut được ghép thêm.

Nếu chọn `cflowbelow(P)`, nó có thể chỉ chạy tại các điểm bên dưới một P active. Với P lồng, một occurrence P bên trong cũng có thể được chọn.

### 9.2 After-returning, after-throwing và finally

Một advice kiểu **after-returning** không chạy tại một join point kết thúc bằng ngoại lệ, kể cả join point đó nằm trong `cflow(P)`. **After-throwing** chỉ liên quan khi join point kết thúc bằng throw phù hợp; **finally-like advice** phục vụ cleanup theo scope nó đã vào.

Vì vậy, "không thấy after" không chứng minh `cflowbelow(P)` false. Có thể join point đúng nhưng **nhánh điều khiển** đi qua exception.

### 9.3 Around advice có thể short-circuit

Một around advice ở root P có thể **không gọi continuation**:
- Target/root body không chạy.
- Không phát sinh các join point con dự kiến.
- Advice chọn các điểm bên dưới không có cơ hội được thực thi.

Đây không phải lỗi lựa chọn `cflowbelow(P)`, mà là **control flow thực tế không đi qua chúng**.

Nếu quanh root gọi continuation nhiều lần (một số mô hình cho phép), nhiều tập join point con có thể xuất hiện theo từng attempt. Metric đếm các điểm con phải phân biệt **request**, **attempt** và **execution**.

### 9.4 Cleanup khi root ném lỗi

Một cơ chế tracking root tự cài bằng stack/depth cần cleanup trong `finally`. Nếu không, exception có thể để marker `P active` trên thread pool; invocation không liên quan sau đó bị nhận nhầm nằm trong `cflow(P)`.

Nếu dùng phương tiện weaving có sẵn, hãy xác minh lifecycle của control-flow state theo hợp đồng. Nếu viết wrapper/handler riêng, test exception path và nested restoration là bắt buộc.

## 10. Đệ quy, nested roots và nhiều P đồng thời

### 10.1 Root đệ quy

~~~text
P(n=2)
  |
  +--> P(n=1)
         |
         +--> P(n=0)
~~~

Khi vào `P(n=2)` ngoài cùng: không có P tổ tiên, nên `cflowbelow(P)` false tại occurrence này.

Khi vào `P(n=1)`: có P ngoài, nên `cflowbelow(P)` true **tại chính occurrence P(n=1)**.

Khi vào `P(n=0)`: tương tự, vì nằm dưới hai P occurrences.

Một **boolean `inP`** tự tạo có thể cho biết có P đang active, nhưng không đủ nếu bài toán cần phân biệt **độ sâu/root gần nhất/outermost root**. Một depth counter hoặc stack context có thể tốt hơn.

### 10.2 Hai lần P độc lập theo thời gian

~~~text
P request A enters -> leaves
P request B enters -> leaves
~~~

Khi request A đã rời, `cflow(P)` của A không tiếp tục áp dụng cho request B. Không có "vùng P vĩnh viễn" chỉ vì method từng chạy.

### 10.3 Hai thread độc lập

~~~text
Thread T1: P(A) -> child(A)
Thread T2: independentTask(B)
~~~

Dynamic call stack của T2 không tự chứa P(A) của T1. Không dùng một field `static boolean inP` để theo dõi cflow cho cả process: nó có thể làm T2 bị đánh dấu sai và gây race. Một cơ chế AOP có cflow state phải bảo vệ **phạm vi thực thi**; ThreadLocal depth chỉ là một minh họa đơn giản cho synchronous per-thread stack.

### 10.4 Nếu P khớp nhiều join point khác nhau

Một P có thể chọn nhiều entrypoints, ví dụ `checkout` và `refund`. Khi `checkout` gọi `refund`, `refund` là **root thứ hai** bên trong root thứ nhất. Điều kiện `cflowbelow(P)` tại `refund` có thể true vì có P `checkout` ở phía ngoài.

Nếu yêu cầu chỉ áp dụng khi **đúng loại** root gọi con, cần giữ thông tin lựa chọn root cụ thể, kết hợp thêm pointcut về operation hoặc dùng policy scope rõ.

## 11. Async, executor và control flow logic

Một lời gọi có thể tạo tác vụ async:

~~~text
Thread T1:
  P() --------------------- returns
    |
    +-- executor.submit(task)

Thread T2:
  task.run() ---------------------------- completes
~~~

Nếu P là **method execution** ở T1, P kết thúc trước khi task ở T2 chạy cũng có thể xảy ra. Task không nằm trong dynamic call stack của P theo nghĩa truyền thống, dù nó bắt nguồn **về mặt nghiệp vụ** từ P.

Do đó phân biệt:
- **Dynamic call-flow scope:** mối quan hệ trong quá trình thực thi/lời gọi đang active, như `cflow(P)`.
- **Logical operation context:** trace/correlation/cancellation context có thể được truyền có chủ đích qua async task hoặc network.
- **Causal relationship:** task được scheduling do P, nhưng không nhất thiết còn nằm trong runtime stack của P.

**Không coi propagation trace ID là cách “làm cho cflow trở thành true”** trừ khi một cơ chế cụ thể có semantics mở rộng như vậy. Một AOP pointcut về dynamic cflow và một distributed tracing context phục vụ hai mục tiêu khác nhau.

### 11.1 Nếu cần truyền context qua task

Cách làm ở cấp khái niệm:
1. Capture một snapshot **tối thiểu, đã được phép** ở submission boundary.
2. Bọc task để activate snapshot khi execution trên worker.
3. Restore/remove context trong `finally`, kể cả khi lỗi.
4. Theo dõi completion, cancellation và timeout.
5. Không dùng trace ID như bằng chứng xác thực quyền.
6. Không mang mutable request object hoặc thread-bound transaction resource tùy tiện qua executor.

Đây là thiết kế **logical context propagation**; không nên mô tả nó là AOP `cflowbelow` tự xuất hiện trên một thread khác.

### 11.2 Callback đồng bộ khác callback async

Một callback được gọi trực tiếp trên **cùng call stack** khi P còn active có thể nằm trong control flow của P. Một callback được gửi vào queue rồi chạy về sau ở worker khác có thể không nằm trong stack đó. Cùng một callback implementation không quyết định membership; **thời điểm và đường thực thi** mới quyết định.

## 12. Những giới hạn khi dùng AOP qua proxy

Proxy-based AOP thường chặn những lời gọi thực sự đi **qua proxy**. Trong mô hình delegation:

~~~text
Client -> Proxy.root() -> Target.root()
                              |
                              +--> this.child()
~~~

Lời gọi `this.child()` trên target có thể **bypass proxy**. Nếu child không được instrument ở một join point khác, advice gắn vào child qua proxy không chạy; dù child đang thực thi động bên trong root nghiệp vụ, hệ thống proxy vẫn có thể **không tạo được join point được advice nhìn thấy tại child**.

Vì vậy cần tách hai điều kiện:
1. **Ngữ nghĩa control flow:** child thật sự có xảy ra bên trong một root P active không?
2. **Khả năng quan sát của mô hình AOP:** child có phải join point mà cơ chế weaving/proxy này intercept được không?

Nếu một hệ thống chỉ cung cấp proxy interceptor cho entrypoints, nó có thể không hỗ trợ pointcut `cflow/cflowbelow` với cùng mức chi tiết như một bộ dệt method-execution. Không được suy từ cú pháp khái niệm rằng mọi private/static/constructor call đều được chọn.

### 12.1 Call và execution dưới cùng root

Ở một pointcut call, caller-side context và object `this` có thể khác target. Ở execution join point của instance method, `this` thường liên quan đối tượng đang chạy method. Đừng dùng tên `this`, `target` hay `args` của một InvocationHandler để khẳng định đồng nhất với mọi pointcut cflow.

### 12.2 Instrumentation của advice helper

Một advice có thể gọi helper cũng nằm trong tập join point thuộc cflow P. Nếu điểm helper bị instrument:
- Số lần advice có thể tăng ngoài dự kiến.
- Logger có thể tự gọi lại logging advice.
- Tracing/metrics có thể sinh thêm sự kiện do chính việc ghi log.

Nên dùng pointcut phạm vi hẹp và các exclude được phép cho helper/infrastructure; đồng thời không bỏ qua những policy bảo mật bắt buộc chỉ để giảm chi phí.

## 13. Hiệu năng và chi phí runtime của control-flow selection

Pointcut chỉ chọn method signature có thể được xác định phần lớn ở compile/weaving time; một điều kiện **dynamic cflow** phải xem xét trạng thái runtime để biết root còn active không.

Tùy công cụ, có thể dùng:
- Counter theo thread/flow.
- Stack context.
- Runtime test ở join point ứng viên.
- Instrumentation tại entry và exit của P.
- Một cơ chế tương đương khác.

**Không giả định một thuật toán hoặc chi phí cụ thể cho mọi weaver.** Nhưng về nguyên tắc, việc kiểm tra cflow có thể thêm overhead tại cả root và các join point được xét, nhất là nếu P/pointcut chọn quá rộng.

### 13.1 Giảm chi phí bằng selection rõ

- Chọn P là **entrypoint có ý nghĩa**, không phải mọi method.
- Chỉ xét cflow ở những **join point ứng viên** cần quan sát.
- Tránh advice logging sâu trên toàn bộ thư viện nền.
- Phân biệt root metrics và child metrics thay vì phát event cho mỗi helper nhỏ.
- Không thu thập raw arguments/return values chỉ để xác định flow.
- Đo chi phí thật trong workload thực tế, kể cả recursion và exception paths.

### 13.2 Không lạm dụng marker toàn cục

Một flag dùng chung kiểu `static boolean inCheckout` có thể:
- Bị request khác ghi đè.
- Bật sai trên thread khác.
- Không về false nếu có lỗi.
- Không biểu diễn root lồng nhau.
- Nhầm P A với P B.

Thread-local depth/stack cho synchronous flow là một mô hình an toàn hơn flag toàn cục ở nhiều tình huống, nhưng vẫn cần cleanup và không giải quyết async tự động.

## 14. Ma trận tình huống cần kiểm thử

| Tình huống | `cflow(P)` | `cflowbelow(P)` | Điểm cần xác nhận |
| --- | --- | --- | --- |
| Root P ngoài cùng vừa bắt đầu | Có | Không | Không có P tổ tiên |
| Method con trực tiếp của P | Có | Có | Child thực sự chạy bên trong P |
| Method con nhiều tầng | Có | Có | P vẫn active |
| P lồng/đệ quy dưới P khác | Có | Có | Inner P ở dưới outer P |
| Method con bên trong inner P | Có | Có | Có một hoặc nhiều root tổ tiên |
| Sibling method sau khi P return | Không | Không | Root đã kết thúc |
| Cùng method con nhưng gọi ngoài P | Không | Không | Static signature không đủ |
| P kết thúc do exception | Không tại những điểm về sau | Không | Cleanup scope đúng |
| P short-circuit trước khi gọi child | Tại P có thể có | Không có child occurrence | Đừng tạo event cho code chưa chạy |
| Callback đồng bộ dưới P | Có thể có | Có thể có | Cần join point được instrument |
| Async task trên worker khác | Không tự động | Không tự động | Dynamic stack khác logical context |
| P ở T1, công việc độc lập ở T2 | Không ở T2 | Không ở T2 | Không dùng global flag |
| Direct call bypass proxy | Tùy join point được hỗ trợ | Tùy | Proxy không thấy mọi execution |
| Instrumented helper trong P | Có thể có | Có thể có | Cẩn thận reentrancy/overhead |

**Bảng mô tả ngữ nghĩa ở những join point có thể quan sát được** trong một mô hình có hỗ trợ control-flow selection. Không kết luận rằng mọi công cụ AOP đều instrument tất cả các tình huống ấy.

## 15. Phương pháp chẩn đoán khi advice chọn sai

Khi lời gọi root/child không kích hoạt như mong muốn:

1. **Xác định P:** chọn call hay execution? Chữ ký và overload có đúng không?
2. **Xác định root occurrence:** đây là P ngoài cùng hay P lồng?
3. **Xác định đường thực thi:** child có thực sự xảy ra trong thời gian P còn active không?
4. **Xác định khả năng instrument:** mô hình hiện tại chặn được join point của child không?
5. **Phân biệt cflow/cflowbelow:** đang kỳ vọng advice tại root hay chỉ các điểm dưới root?
6. **Xem precedence:** around advice ngoài có short-circuit hoặc không proceed không?
7. **Xem exception:** after-returning có bị bỏ do error path không?
8. **Xem concurrency:** flow đang ở thread nào? Có dùng global flag sai không?
9. **Xem async:** đã chuyển sang worker khác chưa? Có đang nhầm logical trace với cflow không?
10. **Xem logging:** advice có chạy nhưng sink/sampling không ghi sự kiện không?
11. **Kiểm tra root cleanup:** một exception có để marker active sai tới request sau không?
12. **Kiểm thử nested roots:** inner P có được tính là below outer P đúng không?

Một cách debug an toàn là ghi **event schema tối thiểu**, ví dụ:

~~~text
joinPoint=Checkout.process
matchesP=true
activeRootDepth=1
insideCflow=true
belowActiveRoot=false
~~~

Không tự ghi token, password, full args, target object hoặc exception message tùy ý vào log. Đặc biệt khi root/child hoạt động trên nhiều tenant, việc debug không được kéo theo rò dữ liệu của request khác.

## 16. Các lỗi hiểu sai phổ biến

1. **“`cflow(P)` chỉ là P.”** Sai: nó còn bao gồm những điểm xảy ra trong control flow được P khởi đầu.
2. **“`cflowbelow(P)` luôn loại mọi occurrence match P.”** Sai khi P lồng dưới P khác.
3. **“`cflowbelow(P)` và `cflow(P)` luôn đồng nghĩa.”** Root ngoài cùng phân biệt hai điều kiện.
4. **“Một method nằm trong cùng package nên ở dưới P.”** Không: dynamic scope không phải lexical scope.
5. **“Static call graph có cạnh A→B nghĩa B luôn nằm trong cflow(A).”** Không: chỉ khi đường gọi thực tế diễn ra.
6. **“Root P chạy hôm trước khiến các lời gọi sau mãi ở trong P.”** Không: scope kết thúc khi occurrence P rời.
7. **“Hai thread khác nhau cùng dùng một `static boolean` để mô phỏng cflow an toàn.”** Không: dễ cross-request race.
8. **“Task được submit trong P đương nhiên ở trong cflow(P) khi chạy sau.”** Không tự động trên worker khác.
9. **“Truyền trace ID bằng ThreadLocal làm `cflow` tự true.”** Trace context và dynamic call stack khác nhau.
10. **“Pointcut trên proxy thấy mọi self-invocation.”** Không trong mọi mô hình proxy.
11. **“Khi child không được instrument, cflowbelow phải sai.”** Cần phân biệt scope thực và cơ hội quan sát join point.
12. **“After-returning không thấy log nghĩa pointcut không khớp.”** Có thể target đã ném lỗi.
13. **“Around không proceed vẫn tạo các điểm con giả định.”** Không: không có child occurrence nếu phần đó chưa được thực thi.
14. **“Đếm mọi method trong cflow luôn bằng số lời gọi nghiệp vụ.”** Có helper, nested roots, retry và callback.
15. **“Đếm thời gian của tất cả child rồi cộng sẽ ra root wall-clock.”** Có thể double count do nesting.
16. **“Một boolean depth đủ cho root lồng nhau.”** Không nếu cần số tầng/outermost binding.
17. **“Có cùng method name thì là cùng join point occurrence.”** Occurrences khác nhau theo runtime.
18. **“Mọi AOP framework/proxy đều có cflow với cùng semantics.”** Cần kiểm tra khả năng cụ thể.
19. **“Có thể dùng cflow như hàng rào bảo mật duy nhất.”** Đường vào ngoài root có thể bypass.
20. **“Vì cflow là runtime nên không cần quan tâm loại pointcut P.”** Call vs execution vẫn làm khác ranh giới gốc.

## 17. Checklist vận dụng

- [ ] Định nghĩa root P bằng **một loại join point cụ thể**.
- [ ] Chọn `cflow(P)` nếu phải xét cả root và hậu duệ.
- [ ] Chọn `cflowbelow(P)` nếu mục tiêu là các điểm nằm dưới một P active.
- [ ] Viết test cho **root ngoài cùng** và **inner P occurrence**.
- [ ] Ghi rõ root lồng/đệ quy có được tính là “internal” không.
- [ ] Phân biệt dynamic cflow với lexical scope và static call graph.
- [ ] Kiểm tra advice có làm short-circuit hoặc retry continuation không.
- [ ] Kiểm thử before, after-success, after-error và finally theo đúng ngữ nghĩa.
- [ ] Nếu tự tracking depth, restore đúng trong `finally` và khi nested.
- [ ] Không dùng một mutable flag global để biểu thị P đang active.
- [ ] Xác minh ranh giới thread và executor; không gộp logical trace với cflow.
- [ ] Kiểm tra coverage thực tế của proxy/weaver cho các child join point.
- [ ] Cẩn thận pointcut chọn cả helper của advice gây recursion.
- [ ] Đo overhead khi root kéo dài hoặc pointcut bao phủ quá rộng.
- [ ] Tối thiểu hóa dữ liệu log và context bindings.
- [ ] Không đặt security invariant bắt buộc chỉ trên một đường gọi dễ bypass.
- [ ] Dùng regression tests cho đồng bộ, nested recursion, error và async.

## 18. Bài tập tự kiểm tra

**Bài 1.** P chọn execution `checkout()`; gọi `checkout()` rồi `inventory()` bên trong. `cflow(P)` có chọn execution checkout ngoài không?

**Đáp:** có. Nó bao gồm điểm khởi đầu P và các điểm bên dưới theo ngữ nghĩa dynamic cflow.

**Bài 2.** Trên occurrence checkout ngoài cùng ấy, `cflowbelow(P)` có đúng không nếu không còn P nào đang bao quanh?

**Đáp:** không. Chính occurrence đó là điểm khởi đầu ngoài cùng, không nằm dưới P khác.

**Bài 3.** `checkout()` gọi đệ quy `checkout()`. Occurrence bên trong có thể khớp `cflowbelow(P)` không?

**Đáp:** có, vì nó nằm dưới occurrence P ngoài. Không nên diễn giải `cflowbelow(P)` là loại mọi root theo signature.

**Bài 4.** `inventory()` cũng được gọi từ `profile()` khi không có checkout trên stack. Invocation ấy có thuộc `cflow(P)` không?

**Đáp:** không chỉ vì trùng method name; phải có P active trên đường thực thi liên quan.

**Bài 5.** Checkout submit task sang executor; worker chạy task sau khi checkout trả về. Task mặc nhiên ở trong dynamic cflow của checkout không?

**Đáp:** không. Đường stack đã tách; nếu cần logical context phải có propagation được thiết kế, và nó không tự đồng nghĩa cflow.

**Bài 6.** Around advice ở root từ chối và không gọi continuation, tại sao child advice không chạy?

**Đáp:** vì các child join point không hề xảy ra. Không thể chọn một điểm không được thực thi.

**Bài 7.** AOP qua proxy chặn `Checkout.process`, bên trong target gọi `this.validate()`. Tại sao validate advice có thể không chạy?

**Đáp:** trong mô hình delegation, self-invocation có thể bypass proxy. Cần phân biệt membership của flow với khả năng instrument child.

**Bài 8.** Nếu chỉ muốn đếm method thực thi bên dưới root nhưng không tính root ngoài cùng, lựa chọn nào phù hợp khi không có P lồng?

**Đáp:** `cflowbelow(P)`, có thể kết hợp selector của nhóm method cần đếm để tránh helper ngoài ý định.

**Bài 9.** Một exception làm root kết thúc. Vì sao phải cleanup tracking depth bằng `finally`?

**Đáp:** để không giữ trạng thái P active sai cho các invocation sau, nhất là trên thread pool.

**Bài 10.** Tại sao một inner P có thể vừa là “root” vừa là “below”?

**Đáp:** vì root/below là **quan hệ giữa những occurrence**. Inner P mở phạm vi riêng đồng thời là hậu duệ của outer P.

## 19. Kết luận

Khi áp dụng **control-flow pointcuts** trong AOP, phải xác định **điểm P nào bắt đầu một phạm vi động**, rồi mới quyết định có chọn **chính điểm khởi đầu** hay **chỉ các join point xảy ra bên dưới**.

- `cflow(P)` bao gồm root occurrence P và các điểm nằm trong control flow ấy.
- `cflowbelow(P)` chọn các điểm nằm bên dưới **một** P active, thường loại root ngoài cùng nhưng **vẫn có thể chọn P lồng/đệ quy**.
- Dynamic control flow khác lexical scope, static call graph, logical tracing context và phạm vi được proxy instrument.
- Điều kiện dùng được còn phụ thuộc đường gọi thực, nested roots, loại join point P, exception, short-circuit và ranh giới async.
- Bài toán nghiệp vụ cần kiểm thử **root/child, nested P, outside flow, error và async**, không chỉ một nhánh thành công.

**Ghi nhớ:** “điểm khởi đầu” và “điểm ở dưới” là **vai trò tương đối của những join point occurrences trên đường thực thi**, không phải nhãn cố định của một method hoặc class.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-control-flow-003">Giới hạn hành vi theo chuỗi lời gọi thay vì vị trí mã đơn lẻ</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn hành vi theo chuỗi lời gọi thay vì vị trí mã đơn lẻ

## 1. Tư duy cốt lõi: cùng một method, hành vi AOP có thể khác theo đường đi

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP)**, một **pointcut** chọn những *join point* mà cơ chế AOP có thể can thiệp; **advice** định nghĩa hành vi chạy ở các điểm được chọn. Nhiều quy tắc quen thuộc chỉ xét vị trí mã hoặc chữ ký, ví dụ “mọi execution của method `reserve()`”. Nhưng trong thực tế, cùng `reserve()` có thể cần chính sách khác khi được gọi từ một quy trình **checkout** so với lúc chạy từ tác vụ bảo trì hoặc một API kiểm tra riêng.

Ví dụ:

~~~text
Đường A: checkout(orderId)       Đường B: nightlyRepair()
             |                            |
             v                            v
        validate()                     reserve()
             |
             v
          reserve()
             |
             v
          audit()
~~~

Tại những lần `reserve()`, **vị trí implementation có thể giống hệt nhau**, nhưng **ngữ cảnh gọi động** khác:

- Khi `reserve` nằm trong chuỗi lời gọi đang hoạt động bắt đầu từ `checkout`, ta có thể muốn gắn một trace span đặc biệt hoặc kiểm tra invariant dành riêng cho workflow checkout.
- Khi `reserve` chạy độc lập hoặc từ `nightlyRepair`, advice dành riêng cho checkout **không nên chạy**.

Ý tưởng **giới hạn hành vi theo chuỗi lời gọi (dynamic control-flow scoping)** là:

> Chọn một join point mục tiêu `Q` **chỉ khi** trong chuỗi lời gọi đang hoạt động có một join point gốc `P` đã được chọn, thay vì chỉ xét vị trí mã của `Q`.

Về mặt logic:

~~~text
Q  AND  insideDynamicControlFlow(P)
~~~

Ví dụ:

~~~text
Q = execution(reserve(..))
P = execution(checkout(..))

Chỉ quan sát reserve khi một checkout đang hoạt động ở chuỗi gọi hiện tại.
~~~

Đây là **mô hình khái niệm**, không phải cú pháp bắt buộc của mọi AOP engine. Một số ngôn ngữ pointcut gọi các phép chọn theo luồng động là `cflow(P)` và `cflowbelow(P)`; những công cụ khác có thể dùng tên/API hoặc cơ chế context propagation khác, hoặc không hỗ trợ tính năng này.

### Kết quả học tập

Sau bài này, có thể:

1. Phân biệt **bộ chọn theo vị trí mã** và **bộ chọn theo luồng điều khiển động**.
2. Xác định chính xác lúc một luồng `P` bắt đầu/kết thúc và join point `Q` nào thuộc luồng ấy.
3. Phân biệt logic bao gồm root (`cflow`) và loại trừ root hiện tại (`cflowbelow`).
4. Hiểu tác động của recursion, nested root, exception, proxy bypass và các thread/async boundary.
5. Mô phỏng cơ chế với Java 17, nhưng không nhầm simulation với một bytecode weaver thật.
6. Thiết kế advice có state theo flow an toàn về hiệu năng, đồng thời và cleanup.
7. Viết integration tests chứng minh điểm nào **được chọn** và điểm nào **không được chọn**.

## 2. Vị trí mã tĩnh và chuỗi lời gọi động không cùng một câu hỏi

Một pointcut theo vị trí hoặc cấu trúc thường quan tâm:

- Loại join point: call hay execution, field get/set, constructor, v.v., nếu engine hỗ trợ.
- Kiểu/owner của method, method name, return type, parameters, modifiers.
- Annotation hoặc metadata được engine cung cấp.
- Vị trí lexical, chẳng hạn mã nằm trong một kiểu/khối nhất định.
- Quan hệ với caller được mô tả từ cấu trúc tĩnh trong những mô hình phù hợp.

Một pointcut theo control flow hỏi thêm:

- **Tại thời điểm join point này thực thi**, một lời gọi/root `P` nào đang ở **dynamic call stack**?
- Flow bắt đầu ở đâu và chưa kết thúc hay đã rời phạm vi?
- Mục tiêu `Q` có xảy ra khi flow đang active không?
- Nếu có nhiều root lồng nhau, `Q` thuộc root nào hoặc bao nhiêu root?

Bảng so sánh:

| Tiêu chí | Chọn theo vị trí/chữ ký | Chọn theo luồng điều khiển động |
| --- | --- | --- |
| Nguồn thông tin | Thường dựa trên join point tĩnh và metadata của nó | Dựa thêm vào lịch sử lời gọi **đang active** |
| Cùng method `reserve` từ hai workflow | Thường cùng match nếu signature giống | Có thể match ở checkout, không match ở repair |
| Cần biết root hiện tại đang nằm trên stack? | Không nhất thiết | Có |
| Matching tại build-time | Có thể đánh giá phần tĩnh sớm | Điều kiện flow thường phải đánh giá lúc chạy |
| Phạm vi bao phủ | Những join points được tool instrument/match | Giao của join points được instrument và điều kiện runtime |
| Async/thread hop | Phụ thuộc join point gốc/tầng mã | Phải xét propagation của ngữ cảnh động |
| Chi phí runtime | Có thể ít khi chỉ dùng matching tĩnh | Có thể phải duy trì depth/context hoặc kiểm stack |
| Nguy cơ | Match quá rộng theo chữ ký | Scope leak, nhầm flow, overhead và propagation sai |

**Luồng điều khiển động không bằng cây lời gọi tĩnh (static call graph).** Call graph mô tả những cạnh *có thể* xuất hiện; dynamic call stack chỉ phản ánh những khung lời gọi *đang thực thi*. Một path có trong source nhưng không đi qua ở request cụ thể thì không tự làm flow active.

## 3. Ba sơ đồ để đọc flow scope

### 3.1. Target chạy ngoài mọi root

~~~text
main
  └── reserve()           <- Q có, P=checkout không active
~~~

`Q` theo tên method match, nhưng `Q AND cflow(P)` là false.

### 3.2. Target chạy bên dưới root

~~~text
main
  └── checkout()          <- P bắt đầu
        ├── validate()
        ├── reserve()     <- Q trong flow P
        └── audit()       <- có thể là Q khác
      checkout() returns  <- P kết thúc
~~~

Khi `reserve()` chạy, `checkout()` vẫn còn ở dynamic call stack. Vì vậy điều kiện `cflow(P)` là true cho `reserve`; nếu target Q không trùng root, trường hợp này cũng là `cflowbelow(P)`.

### 3.3. Root đã return, target chạy lại

~~~text
main
  ├── checkout()          <- P đã kết thúc
  └── reserve()           <- Q sau đó, P không còn active
~~~

Một lời gọi `checkout` **đã từng** xảy ra không đủ để làm những lần `reserve` sau đó nằm trong flow checkout. Flow là phạm vi **động và có ranh giới thời gian**, không phải một cờ “đã gọi checkout một lần”.

## 4. Kết hợp root `P` và target `Q`

Cách xây pointcut nên tách hai vai trò:

- **P = root selector:** một loại join point mở phạm vi, ví dụ execution của `checkout(..)`.
- **Q = target selector:** một loại join point được advice muốn can thiệp, ví dụ execution của `reserve(..)`.
- **C = dynamic flow condition:** tại `Q`, có root `P` đang active không?

Ta cần **giao**:

~~~text
SelectedJoinPoints = Match(Q) ∩ WithinActiveFlow(P)
~~~

Một số phép logic hữu ích:

~~~text
Q AND cflow(P)
Q AND cflowbelow(P)
Q AND cflow(P1) AND NOT cflow(P2)
(Q1 OR Q2) AND cflow(P)
~~~

Đây là cú pháp **ý niệm**. Tính khả dụng của `AND`, `NOT`, `OR`, các biến/ngữ cảnh và sự ràng buộc tham số phụ thuộc ngôn ngữ pointcut thực tế. Tránh tự áp vào mọi engine.

### 4.1. Không nên đặt `P` quá rộng

Nếu `P` chọn một wrapper chung bao mọi request, gần như mọi `Q` trong ứng dụng đều ở trong flow. Khi đó điều kiện động không còn thu hẹp hữu ích mà còn thêm chi phí kiểm tra.

Nếu `P` quá hẹp hoặc bị proxy bypass, những flows thực tế cần được quan sát có thể không mở, làm advice không chạy.

### 4.2. Không nên đặt `Q` quá rộng

Một `Q` match **mọi method execution** bên trong checkout có thể tạo lượng event lớn, kể cả helper, logging, instrumentation và thư viện mà engine hỗ trợ. Điều này dễ tăng overhead và gây recursion của chính advice.

Hãy thu hẹp **cả P lẫn Q**, và có test âm (negative coverage) cho các loại join point không được phép.

## 5. Giao giữa target Q và luồng động P

Với một **root occurrence P duy nhất**, `cflow(P)` có thể chọn chính P và các join point bên trong; `cflowbelow(P)` chọn phần bên dưới, không chọn **chính occurrence bắt đầu phạm vi đó**. Khi có **P lồng trong P**, inner P lại là descendant của outer P, nên tại inner P điều kiện `cflowbelow(P)` có thể đúng. Không được rút gọn thành “loại mọi method có cùng tên P”.

~~~text
P checkout(outer)            cflow: có; below: không (nếu không có ancestor P)
  Q reserve()                cflow: có; below: có
  P checkout(inner)          cflow: có; below: có (do outer)
    Q reserve()              cflow: có; below: có
outside Q reserve()          cflow: không; below: không
~~~

## 6. Áp dụng vào đường nghiệp vụ thay vì chỉ dựa trên tên mã

Mục tiêu bài này là **cùng Q nhưng chỉ chọn khi một P cụ thể còn nằm trên đường gọi thực tế**. Ghép `Q AND cflow(P)` hoặc `Q AND cflowbelow(P)` nếu engine có designator đó, rồi kiểm tra cả luồng checkout và lời gọi Q từ workflow khác. Kết hợp **pointcut Q đủ hẹp** để tránh áp dụng advice lên mọi join point bên trong P. Một root P quá rộng khiến flow condition mất ý nghĩa chọn lọc.

Đây là mô tả ngữ nghĩa chung; call-vs-execution, nested roots, quyền binding và khả năng observe của proxy/weaving vẫn cần kiểm thử theo công nghệ. Trọng tâm tiếp theo là **thiết kế một chính sách theo đường đi thực**, không dạy lại bảng định nghĩa pointcut.

## 7. Ví dụ thực tế: bật audit riêng trong luồng checkout

Giả sử `reserve(resourceId)` là một API được tái sử dụng:

~~~text
API A:
  checkout() -> reserve() -> persist()

API B:
  performMaintenance() -> reserve() -> persist()
~~~

Nhu cầu: ghi audit `RESERVATION_IN_CHECKOUT` chỉ khi `reserve` được gọi từ checkout, nhưng không ghi event này khi reserve nằm trong maintenance.

Thay vì sao chép hai implementations `reserveForCheckout()` và `reserveForMaintenance()`, có thể mô tả:

~~~text
Q = execution(reserve)
P = execution(checkout)

observe(Q) only if cflow(P)
~~~

Ưu điểm:

- Target `reserve` không phải biết mọi workflow caller.
- Aspect có thể diễn đạt **ý định theo ngữ cảnh**, không theo package.
- Audit được giới hạn theo scope của logical operation cụ thể.
- Có thể quan sát nhiều join points Q bên trong cùng P.

Điều cần cân nhắc:

- Nếu yêu cầu audit mang tính bắt buộc về bảo mật, **không dựa duy nhất** vào flow có thể bị bỏ qua do proxy/raw target/cross-thread.
- Nếu luồng checkout gọi một API async, điều kiện control flow theo stack có thể không đi qua thread worker.
- Nếu `reserve` đồng thời được gọi ở một flow khác lồng trong checkout, dynamic scope vẫn có P trên stack; cần thêm điều kiện nếu đó không phải intent.

## 8. Ví dụ Java 17 hoàn chỉnh: root lồng nhau, lời gọi ngoài flow và ngoại lệ

Chương trình **CallFlowScopeDemo.java** dùng Java 17 và **JDK interface proxy** để mô phỏng hai lớp can thiệp:

1. Proxy cho `Checkout.checkout()` mở một flow scope bằng `Flow.inRoot()`.
2. Proxy cho `Steps.step()` kiểm tra có checkout root nào đang active ở **thread hiện tại** rồi in trạng thái pointcut điều kiện.
3. `CheckoutCore("outer")` gọi thêm `checkout("inner")` **qua proxy đã lắp**, tạo hai root lồng nhau.
4. `outer` cũng khởi tạo một thread mới gọi `Steps.step()`; thread mới không tự nhận context ThreadLocal của thread ngoài.
5. `checkout("bad")` ném exception, nhưng `finally` vẫn pop root và loại bỏ ThreadLocal khi stack rỗng.
6. Khi gọi `Steps.step()` bên ngoài checkout, condition flow là false.

**Lưu ý về ngữ nghĩa mô phỏng:** dòng `ROOT enter ... below=...` đo root hiện tại có **một root ancestor** hay không; còn `POINT step ... below=...` xem `step` là descendant không phải root, nên `below=true` khi có ít nhất một root active. Đây là phép minh họa các quan hệ `cflow`/`cflowbelow`, **không phải một implementation pointcut đầy đủ hay bytecode weaving**.

Tạo file `CallFlowScopeDemo.java`:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Deque;

public final class CallFlowScopeDemo {
    @FunctionalInterface interface Action<T> { T run() throws Throwable; }
    interface Steps { void step(String name); }
    interface Checkout { String checkout(String id); }

    static final class Flow {
        private final ThreadLocal<Deque<String>> roots =
                ThreadLocal.withInitial(ArrayDeque::new);

        <T> T inRoot(String name, Action<T> action) throws Throwable {
            Deque<String> stack = roots.get();
            stack.push(name);
            System.out.println("ROOT enter " + name
                    + " cflow=true below=" + (stack.size() > 1)
                    + " depth=" + stack.size());
            try {
                return action.run();
            } finally {
                System.out.println("ROOT exit " + name);
                stack.pop();
                if (stack.isEmpty()) roots.remove();
            }
        }

        void report(String name) {
            Deque<String> stack = roots.get();
            boolean active = !stack.isEmpty();
            System.out.println("POINT step " + name
                    + " cflow=" + active
                    + " below=" + active
                    + " depth=" + stack.size());
            if (!active) roots.remove();
        }

        int depth() {
            Deque<String> stack = roots.get();
            int count = stack.size();
            if (count == 0) roots.remove();
            return count;
        }
    }

    static Object invoke(Object target, Method method, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapper) {
            throw wrapper.getCause();
        }
    }

    static final class StepCore implements Steps {
        public void step(String name) {
            System.out.println("BODY step " + name);
        }
    }

    static Steps observe(Steps target, Flow flow) {
        return (Steps) Proxy.newProxyInstance(
                Steps.class.getClassLoader(),
                new Class<?>[] { Steps.class },
                (proxy, method, args) -> {
                    if ("step".equals(method.getName())) {
                        flow.report((String) args[0]);
                    }
                    return invoke(target, method, args);
                });
    }

    static final class CheckoutCore implements Checkout {
        private final Steps steps;
        Checkout nestedProxy;

        CheckoutCore(Steps steps) { this.steps = steps; }

        public String checkout(String id) {
            steps.step("begin-" + id);
            if ("outer".equals(id)) {
                System.out.println("NESTED result=" + nestedProxy.checkout("inner"));
                Thread child = new Thread(() -> steps.step("new-thread"));
                child.start();
                try {
                    child.join();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted", error);
                }
            }
            if ("bad".equals(id)) {
                throw new IllegalArgumentException("invalid order");
            }
            steps.step("end-" + id);
            return "ok:" + id;
        }
    }

    static Checkout scope(Checkout target, Flow flow) {
        return (Checkout) Proxy.newProxyInstance(
                Checkout.class.getClassLoader(),
                new Class<?>[] { Checkout.class },
                (proxy, method, args) -> {
                    if (!"checkout".equals(method.getName())) {
                        return invoke(target, method, args);
                    }
                    String id = (String) args[0];
                    return flow.inRoot(id, () -> invoke(target, method, args));
                });
    }

    public static void main(String[] args) {
        Flow flow = new Flow();
        Steps steps = observe(new StepCore(), flow);
        CheckoutCore core = new CheckoutCore(steps);
        Checkout checkout = scope(core, flow);
        core.nestedProxy = checkout;

        steps.step("outside");
        System.out.println("RESULT " + checkout.checkout("outer"));
        steps.step("outside-again");

        try {
            checkout.checkout("bad");
        } catch (IllegalArgumentException error) {
            System.out.println("CAUGHT " + error.getMessage());
        }
        steps.step("after-error");
        if (flow.depth() != 0) {
            throw new AssertionError("flow leaked");
        }
        System.out.println("ASSERT depth=0");
    }
}
~~~

Biên dịch/chạy trên JDK 17 trở lên:

~~~bash
javac --release 17 -encoding UTF-8 CallFlowScopeDemo.java
java CallFlowScopeDemo
~~~

**Đầu ra chính xác** đã được kiểm chứng bằng trình biên dịch và JVM:

~~~text
POINT step outside cflow=false below=false depth=0
BODY step outside
ROOT enter outer cflow=true below=false depth=1
POINT step begin-outer cflow=true below=true depth=1
BODY step begin-outer
ROOT enter inner cflow=true below=true depth=2
POINT step begin-inner cflow=true below=true depth=2
BODY step begin-inner
POINT step end-inner cflow=true below=true depth=2
BODY step end-inner
ROOT exit inner
NESTED result=ok:inner
POINT step new-thread cflow=false below=false depth=0
BODY step new-thread
POINT step end-outer cflow=true below=true depth=1
BODY step end-outer
ROOT exit outer
RESULT ok:outer
POINT step outside-again cflow=false below=false depth=0
BODY step outside-again
ROOT enter bad cflow=true below=false depth=1
POINT step begin-bad cflow=true below=true depth=1
BODY step begin-bad
ROOT exit bad
CAUGHT invalid order
POINT step after-error cflow=false below=false depth=0
BODY step after-error
ASSERT depth=0
~~~

### 8.1. Vì sao `outside` không thuộc flow?

`steps.step("outside")` được gọi khi không có `checkout()` qua root proxy đang active. `Flow.report` thấy stack rỗng, in `cflow=false below=false depth=0`. Target `StepCore.step` vẫn chạy.

Điều này cho thấy **không match flow không nhất thiết là không thực thi method nghiệp vụ**. Trong chương trình, proxy `Steps` vẫn quan sát vị trí gọi và in trạng thái `false` để học; một pointcut `Q AND cflow(P)` thực sự có thể chọn **không chạy advice chuyên biệt** trên những lời gọi ấy.

### 8.2. Vì sao root `outer` có `below=false`?

`Flow.inRoot("outer")` đẩy root đầu tiên vào stack, nên `depth=1`. Chính occurrence root đầu tiên thuộc `cflow(P)` nhưng **không nằm bên dưới một occurrence P khác**. Vì vậy `below=false` cho **root event**.

Sau đó `step("begin-outer")` là một join point Q khác root. Nó chạy **bên dưới checkout đang active**, vì vậy `below=true` dù chỉ có một root và `depth=1`.

### 8.3. Vì sao root `inner` có `below=true`?

`CheckoutCore` gọi `nestedProxy.checkout("inner")` từ bên trong `checkout("outer")`. Proxy root được vào lần nữa, stack có `["inner","outer"]`, `depth=2`.

`inner` vừa là root P của chính nó, vừa **nằm dưới root `outer`**, nên `cflowbelow(P)` có thể true ở inner-root. Đây là ca dễ làm lộ sai lầm `cflowbelow(P) = cflow(P) && !P`.

### 8.4. Vì sao thread mới có `cflow=false` dù được tạo trong checkout?

`outer` tạo một Java thread mới và gọi `steps.step("new-thread")` ở thread đó. `Flow` dùng `ThreadLocal` thông thường, nên thread con **không thừa hưởng root stack** của caller thread.

Nội dung `POINT step new-thread cflow=false...` là kết quả đúng với cơ chế mô phỏng này. Không nên suy rộng rằng **mọi** hệ AOP hoặc distributed tracing đều mất ngữ cảnh qua async: một số hệ thống có cơ chế explicit propagation hoặc mô hình flow khác. Phải biết chính xác hợp đồng của công nghệ đang dùng.

### 8.5. Vì sao flow không bị rò khi target ném lỗi?

`checkout("bad")` mở root, chạy một `step` rồi ném `IllegalArgumentException`. `Flow.inRoot()` dùng `finally` để pop root kể cả khi `action.run()` throw, và gọi `roots.remove()` khi stack rỗng.

Sau đó `steps.step("after-error")` lại có `cflow=false`; `ASSERT depth=0` xác nhận scope không còn active.

Nếu quên `finally/remove`, một thread pool worker được tái sử dụng có thể mang sai context từ request trước, gây **giới hạn hành vi sai** hoặc rò dữ liệu.

### 8.6. Proxy vs weaving trong ví dụ

- `scope(...)` là **JDK interface proxy**; root được mở vì caller thực sự gọi **`checkout` qua proxy**.
- `observe(...)` cũng là proxy; `step` được quan sát vì `CheckoutCore` giữ `Steps` proxy.
- Nếu caller giữ `CheckoutCore` raw hoặc tự tạo `StepCore` raw, lớp proxy tương ứng có thể bị bỏ qua.
- `nestedProxy` được truyền vào core để nested checkout đi qua root wrapper; gọi `this.checkout(...)` trong target thường bypass wrapper bên ngoài.
- Code mô phỏng này **không tự động nhận dạng join point bằng cú pháp pointcut và không biến đổi bytecode**.
- Một AOP engine dệt được `execution(checkout)` có thể có hành vi khác với proxy trước self-invocation, nhưng vẫn phải kiểm capability và scope thực.

**Bài học được kiểm chứng là điều kiện dynamic root stack và cleanup**, không phải bảo đảm mọi công cụ AOP có `ThreadLocal` implementation giống nhau.

## 9. Tại sao `ThreadLocal` hữu ích nhưng chưa đủ?

`ThreadLocal` cho phép mỗi thread có stack trạng thái riêng. Một stack thay vì một `boolean` đơn lẻ giúp xử lý nested roots:

~~~text
enter outer: [outer]
enter inner: [inner, outer]
exit inner:  [outer]
exit outer:  []
~~~

Nếu chỉ dùng `boolean active = true/false`:

~~~text
outer sets true
  inner sets true
  inner finally sets false  <- SAI: outer vẫn active
outer continues            <- bị hiểu là ngoài flow
~~~

Kỹ thuật có thể dùng:

- **Counter depth:** tăng tại root enter, giảm tại exit; đủ nếu chỉ cần biết có bao nhiêu roots cùng loại active.
- **Stack:** lưu identity/context của từng root, dùng khi cần đúng request ID, parent scope và nested lifecycle.
- **Immutable context token:** truyền rõ ràng qua các ranh giới async khi áp dụng.
- **Scoped execution context:** runtime abstraction có lifecycle rõ, khả năng propagate tùy API.
- **Instrumentation-provided state:** engine có thể tự quản lý flow, không cần ứng dụng viết ThreadLocal.

**Cleanup phải đối xứng**. Với `ThreadLocal` ở thread pool, `remove()`/restore previous value khi rời scope là quan trọng. Một value giữ payload, user info hoặc classloader lâu quá có thể tạo memory leak và lộ context giữa requests.

### 9.1. Tại sao không dùng một biến `static boolean`?

Vì mọi threads có thể chia sẻ cùng cờ. Request A chạy checkout bật true, request B gọi reserve độc lập cũng có thể bị hiểu là trong checkout. `volatile` chỉ giúp visibility, không biến cờ global thành trạng thái theo chuỗi lời gọi riêng.

### 9.2. Có nên dùng `InheritableThreadLocal`?

Không coi đó là giải pháp mặc định. `InheritableThreadLocal` có thể sao chép giá trị khi thread con được **tạo**, nhưng trong thread pools các threads được tạo từ trước và tái sử dụng, nên đó **không phải context propagation đáng tin** cho logical task boundaries. Nó còn có thể mang context quá lâu hoặc ngoài phạm vi dự kiến.

## 10. Đường điều khiển động khác đường liên hệ nghiệp vụ

Một luồng gọi có thể bao nhiều bước:

~~~text
checkout()
  validation()
    loadUser()
  reserve()
    persist()
  notify()
~~~

Một root `checkout()` có thể bao tất cả chúng, kể cả code thư viện nếu pointcut Q chọn và engine có thể instrument. Tuy nhiên, **cùng nằm dưới root checkout không có nghĩa tất cả thao tác thuộc cùng transaction**.

- Control-flow scope là **quan hệ lời gọi**.
- Transaction scope là **quan hệ commit/rollback của tài nguyên**.
- Trace scope là **quan hệ quan sát của logical operations**.
- Security scope là **quan hệ quyền đáng tin cậy**.
- Async operation scope có thể tồn tại sau khi method root return.

Một flow pointcut không tự tạo database transaction, không bảo đảm identity được xác minh và không ngăn một caller khác bypass code bảo mật.

Nếu yêu cầu kiểm quyền tuyệt đối, nên đặt enforcement ở boundary đáng tin cậy, không phụ thuộc duy nhất vào việc một method có `checkout` trên stack trong mọi đường gọi.

## 11. Recursion, nested roots và per-flow state

### 11.1. Recursion cùng root selector

~~~text
process(level=3)   <- P root #1
  process(level=2) <- P root #2, dưới #1
    process(1)     <- P root #3, dưới cả #1 và #2
~~~

Nếu dùng `cflow(P)`, cả ba method executions có thể match. Với `cflowbelow(P)`, root #1 không ở dưới chính mình, nhưng root #2 và #3 nằm dưới những roots ngoài.

Một counter `depth` cho biết số root occurrences đang active, nhưng không chỉ ra **root nào** nếu cần phân biệt actor/order/request context. Với logic per-flow, dùng stack hoặc context frames có định danh riêng.

### 11.2. Per-flow aspect instantiation khác per-invocation

Một engine có thể cho một aspect instance tồn tại cho **một root control flow**, dùng chung cho các join points con, và cleanup khi root kết thúc. Đây không đồng nghĩa tạo instance mới ở **mọi** `reserve` invocation.

Ví dụ một trace context có `traceId` tạo ở `checkout` và được `reserve`, `persist` dùng chung. Nếu `checkout` lồng nhau, phải định nghĩa inner dùng trace của outer hay tạo span/context con.

Không tự giả định mọi AOP engine cung cấp loại scope này. Có thể mô phỏng bằng explicit context object, nhưng cần ownership và propagation thật.

### 11.3. Root có thể xuất hiện trong advice

Nếu một advice quanh Q gọi một method cũng match P, flow mới có thể mở **bên trong code advice**. Nếu một advice quanh P tự gọi P và không có guard, recursion có thể xảy ra.

Cần test những trường hợp:
- advice tạo logging/metrics call lại đi vào pointcut;
- nested root được mở trong exceptional path;
- root được mở rồi target return sớm;
- root mở nhưng `proceed` không được gọi (short-circuit);
- outer root còn active sau khi inner root kết thúc.

## 12. Ngoại lệ và các đường rút khỏi flow

Một root P có thể kết thúc bằng:

- Return bình thường.
- Throw exception.
- Advice quanh root catch exception và return fallback.
- Advice short-circuit trước khi gọi target.
- Target/inner advice ném lỗi trong cleanup.

**Nguyên tắc:** flow scope phải kết thúc theo **lifecycle của root mà engine thực sự định nghĩa**, không chỉ theo kết quả thành công. Với implementation tự viết, dùng `try/finally` và restore/remove context.

Mẫu Java khái niệm:

~~~java
Context previous = current.get();
current.set(next);
try {
    return proceed();
} finally {
    if (previous == null) current.remove();
    else current.set(previous);
}
~~~

Đây là pseudocode không độc lập; `Context`, `current`, `next` và `proceed` phải được cung cấp. Nó minh họa **restore previous context** khi rời nested scope, không chỉ gán null một cách thô.

### 12.1. Lỗi do advice trước root

Nếu advice định nghĩa root scope trước `proceed` nhưng phần khởi tạo context ném exception, phải biết liệu scope đã được cài vào ThreadLocal hay chưa và cần cleanup gì. Đừng làm `push` rồi gọi logic không tin cậy **trước khi vào vùng `try/finally`**.

### 12.2. Cleanup cũng có thể lỗi

Nếu `close()` của resource trong flow ném lỗi, nó có thể che exception từ target. Cleanup tốt cần phân biệt lỗi chính và lỗi thứ cấp, sử dụng hợp đồng phù hợp (`try-with-resources` có hỗ trợ suppressed exception trong những trường hợp Java định nghĩa).

### 12.3. Flow nào còn active khi callback after chạy?

Nếu advice `around(P)` vẫn đang ở trong `try` trước khi pop, các hoạt động trong `afterReturning` hoặc `finally` của nó có thể **còn nằm trong flow P** tùy cách tổ chức. Nếu gọi Q từ chính cleanup logic, Q cũng có thể thỏa dynamic condition.

Muốn loại bỏ advice-internal logging khỏi Q, nên thiết kế pointcut loại trừ vùng instrumentation hoặc chuyển công việc ra sau khi đã restore context, tùy mục tiêu. Đừng giả định root kết thúc đúng lúc method target vừa return nếu outer advice vẫn giữ scope.

## 13. Thread hop, async và distributed call: ranh giới khó nhất

Call stack đồng bộ truyền thống:

~~~text
Thread A:
  checkout()
    reserve()
      persist()
  return
~~~

Nếu có async handoff:

~~~text
Thread A: checkout()
            submit(task) --------+
          return                |
                                v
Thread B:                   reserve()
                               persist()
~~~

Tại lúc Thread B chạy `reserve`, stack của Thread B **không chứa khung `checkout()` của Thread A**. Thậm chí `checkout` có thể đã return. Với định nghĩa cflow theo call stack vật lý, `reserve` không thuộc flow đang hoạt động của P trên Thread B.

### 13.1. Muốn flow qua async thì phải định nghĩa lại scope

Có thể dùng **logical operation context** để truyền `operationId`, security principal, trace IDs, deadline... tới task B. Nhưng đây là **một cơ chế propagation bổ sung**, không mặc nhiên cùng ngữ nghĩa với `cflow(P)`.

Cần trả lời:

1. Context được capture trước khi `submit` ở đâu?
2. Task nhận context bằng tham số, wrapper, scheduler API hay abstraction nào?
3. Nếu task chạy sau root method return, context còn hợp lệ bao lâu?
4. Có thể dùng context đó để **mở một root logic mới** hay chỉ dùng làm dữ liệu tracing?
5. Ai restore/remove context trong worker thread sau task?
6. Khi cancellation/retry xảy ra, context nào kết thúc, resource nào được đóng?
7. Context có chứa secrets hoặc thông tin cá nhân cần hạn chế propagation không?

### 13.2. Async method return không bằng operation completion

Một method trả `CompletableFuture<T>` có thể kết thúc **ngay sau khi lập lịch tác vụ**. Around advice bao method có thể pop control-flow scope ở method return, trong khi công việc thực vẫn đang pending. Nếu audit cần theo dõi toàn operation, phải chọn một lifecycle khác, gắn vào Future completion/cancellation.

Không tự `join()` trong advice để kéo dài stack scope nếu điều đó làm biến API async thành blocking và có nguy cơ deadlock.

### 13.3. Distributed tracing là mối liên hệ, không phải stack chung

Service A gọi Service B qua HTTP. B không có Java call stack của A. Một trace context có thể được gửi qua request headers và liên kết spans, nhưng điều đó không khiến một JVM-local `cflow(checkout)` pointcut ở B tự động true. Phải có policy rõ ở B dựa trên context được xác thực/cho phép.

## 14. Call join point và execution join point làm thay đổi ranh giới flow

Trong một số mô hình AOP:

- **Call join point** nằm tại **vị trí caller thực hiện lời gọi**.
- **Execution join point** nằm tại **thân thực thi của callee**.

Với `P=call(checkout(..))` và `P=execution(checkout(..))`, flow root có thể bắt đầu ở các điểm logic hơi khác nhau. Tương tự, `Q=call(reserve(..))` khác `Q=execution(reserve(..))`.

Các hệ quả:

- Nếu chỉ instrument **caller call sites**, những cuộc gọi không qua caller đã được dệt có thể không được chọn.
- Nếu instrument **callee execution**, mọi lần thực sự vào callee đã dệt có thể được chọn, kể cả direct calls.
- Với **proxy**, JDK `InvocationHandler` chỉ quan sát lời gọi qua proxy. Nó không tự tương đương mọi call/execution join point trong ngôn ngữ AOP.
- Nếu `checkout()` gọi `this.checkout()`, proxy ngoài có thể bị bypass ở lần gọi nội bộ; một execution weaving có thể vẫn thấy nó nếu class đã dệt.

Do đó khi chọn P/Q cần xác định **join point kind**, không chỉ tên method.

## 15. Độ phủ của weaver/proxy là điều kiện trước khi nói đến flow

Dù flow concept đúng, nếu join point không được instrument thì advice không chạy:

| Kịch bản | Câu hỏi cần kiểm |
| --- | --- |
| JDK interface proxy | Caller có thật sự giữ proxy của P và Q không? |
| Subclass proxy | Override/dispatch của method có hợp lệ? |
| Compile-time weaving | Artifact cuối đã chứa các join points cần quan sát? |
| Binary/post-compile weaving | Raw/woven output có bị trộn? |
| Load-time weaving | Transformer được cài trước lúc class cần xử lý được định nghĩa? |
| Classloader riêng | P/Q runtime classes có thuộc loader được chọn? |
| Method `final`, `private`, `static` | Engine hỗ trợ những điểm đó hay chỉ method qua proxy? |
| Constructor/field | Pointcut model có định nghĩa loại join point ấy? |
| Native/JDK internals | Có được instrument hợp lệ trong runtime hiện tại? |

Một proxy wrapper có thể can thiệp lời gọi `checkout` nhưng **không nhìn thấy `reserve` nếu reserve được gọi qua raw target**. Vì vậy test “`checkout` mở flow đúng” chưa chứng minh `Q AND cflow(P)` có độ phủ đúng.

## 16. Tối ưu hiệu năng cho dynamic control-flow pointcut

Một pointcut thuần tĩnh có thể được engine tối ưu ở compile/load-time. Điều kiện dynamic flow có thể yêu cầu **kiểm runtime** hoặc duy trì context/counter.

Chi phí có thể đến từ:

- Nhiều Q join points được instrument và phải kiểm active flag/depth.
- Root enter/exit phải push/pop context.
- `ThreadLocal` lookup, allocations hoặc context object creation.
- Stack inspection trong những implementation dùng kỹ thuật kiểm call stack.
- Advice chạy nhiều lần ở nested flows.
- Async context capture/restore.
- Logging/metrics/I/O bên trong advice, đôi khi đắt hơn phần matching.

Không nên tuyên bố `cflow` luôn tốn một độ phức tạp cố định cho mọi công cụ. Một engine có thể tối ưu bằng counter/marker; engine khác dùng chiến lược khác, cùng một expression có overhead khác nhau.

### 16.1. Thu hẹp `Q` trước

Thay vì match mọi method rồi hỏi “có trong checkout không?”, có thể chọn `Q` hẹp như những operations thật cần audit. Điều này giảm số join points có khả năng thực thi điều kiện dynamic.

### 16.2. Giữ context nhẹ

Một flow scope chỉ cần identifier, depth và vài thông tin immutable có thể rẻ hơn một object đầy tài nguyên. Tránh tạo HTTP clients/loggers mỗi Q invocation.

### 16.3. Tránh ghi log vô hạn do self-interception

Nếu advice gọi logger mà logger method cũng thuộc Q, có thể xảy ra recursion. Phân biệt advice implementation với domain join points, và test negative coverage.

### 16.4. Đo chi phí trên workload thật

Đo `Q` hit frequency, flow active ratio, allocation rate, p95/p99 latency và throughput. So với baseline không instrument hoặc chỉ dùng static pointcut. Không dùng một microbenchmark đơn luồng rồi kết luận hiệu năng production dưới concurrency.

## 17. Khi nào nên dùng dynamic flow và khi nào nên truyền context tường minh?

| Bài toán | Hướng có thể phù hợp |
| --- | --- |
| Thêm tracing/logging cho các helper calls **đồng bộ** bên dưới một root | Dynamic control-flow pointcut nếu engine có |
| Chọn Q khi method thuộc một workflow đồng bộ nhất định | `Q AND cflow(P)` hoặc tương đương |
| Logic bắt buộc đúng qua async/thread/distributed boundaries | Explicit/context propagation với ownership rõ |
| Authorization bắt buộc đối với mọi caller | Enforce tại trust boundary, không dựa duy nhất vào stack root |
| Audit theo business commit | Gắn event vào commit/outbox boundary thay vì chỉ method flow |
| Per-request timing | Per-invocation local state hoặc context propagation |
| Theo dõi nested/recursive operations | Stack frames hoặc scoped context hỗ trợ nesting |
| Mọi method trong một package cần same behavior | Static structural pointcut có thể đơn giản hơn |
| Control flow quá rộng và hot path nhiều | Cân nhắc thu hẹp Q hoặc thiết kế lại |

**Không phải tính năng càng động càng tốt.** Nếu ý định là “mọi method của một type”, một pointcut static dễ hiểu và thường ít runtime work hơn. Flow condition chỉ có giá trị khi **ngữ cảnh runtime mới phân biệt đúng nhu cầu**.

## 18. Kiểm thử: chứng minh cả positive và negative coverage

Bảng test tối thiểu cho `P=checkout` và `Q=reserve/step`:

| Ca | Flow P active? | Kỳ vọng với Q |
| --- | --- | --- |
| `reserve` độc lập | Không | Không kích hoạt advice theo `cflow(P)` |
| `checkout → reserve` | Có | Kích hoạt |
| `checkout → helper → reserve` | Có | Kích hoạt dù không gọi trực tiếp |
| `checkout` return rồi `reserve` | Không | Không kích hoạt |
| Nested `checkout(inner)` | Có outer P ancestor | Inner root có thể được `cflowbelow(P)` chọn |
| Recursion nhiều cấp | Có nhiều P frames | Scope/depth đúng, không leak |
| Target throw | Có trước lúc thoát | Cleanup; lần gọi sau phải không còn flow |
| Advice around root catch/recover | Tùy lifecycle root | Scope kết thúc đúng nơi |
| `checkout` qua raw target bypass proxy | Không mở scope proxy | Test phát hiện thiếu coverage |
| `reserve` qua raw target bypass Q proxy | Không thấy Q proxy | Không giả vờ Q advice đã chạy |
| Child thread dùng ThreadLocal thường | Không tự có context | Không kích hoạt flow theo mô phỏng |
| Explicit propagation được cấu hình | Tùy hợp đồng | Test capture/restore, timeout và cancellation |
| Có hai requests đồng thời | Scope riêng mỗi thread/task | Không lẫn flow của request khác |
| Một root call chạy `proceed` hai lần | Có thể 2 downstream flows | Đếm Q invocations rõ ràng |
| Q có overload khác | Tùy signature pointcut | Không match nhầm |

### 18.1. Assertion nên có những gì?

- **Outcome của caller:** result/exception gốc được bảo toàn nếu advice chỉ quan sát.
- **Root depth/context:** thay đổi đúng khi P enter và exit.
- **Advice event count:** Q trong flow chạy đúng số lần.
- **Target execution count:** không dùng advice count thay business count nếu retry/cache.
- **Negative coverage:** Q ngoài flow không ghi event flow-specific.
- **Exception cleanup:** sau lỗi, ThreadLocal/stack sạch.
- **Concurrency:** hai flows khác nhau không dùng nhầm root ID.
- **Async propagation:** không suy từ logical relation rằng thread stack đã có P.

### 18.2. Tại sao chỉ test một Q call trong checkout chưa đủ?

Nó không chứng minh:
- `reserve` độc lập sẽ không có advice.
- Exception không làm scope bị giữ mãi.
- Nested roots hoạt động đúng.
- Một callback thread khác có/không có context theo thiết kế.
- Code AOP thật được lắp qua đúng proxy hoặc woven artifact.

Tính đúng của flow-based AOP nằm ở **ranh giới**; cần test những lúc vào, ra và xuyên ranh giới.

## 19. Những lỗi thường gặp

1. **“Cùng method thì advice phải luôn chạy giống nhau.”** Không với dynamic flow; context caller có thể khác.
2. **“Static call graph đủ chứng minh P active.”** Không; dynamic stack chỉ chứa calls đang diễn ra.
3. **“`cflow(P)` chỉ chọn các method con.”** Thường còn bao gồm root P.
4. **“`cflowbelow(P)` chỉ là `cflow(P) && !P`.”** Sai ở nested/recursive P.
5. **“Có P trong lịch sử request thì flow còn active mãi.”** Không; root kết thúc thì scope phải được đóng.
6. **“Một `boolean active` đủ cho mọi nested roots.”** Sai nếu inner exit tắt outer flow.
7. **“Một `static boolean` theo dõi đúng flow nhiều threads.”** Sai; context global lẫn requests.
8. **“ThreadLocal tự đi theo mọi Future và executor.”** Không.
9. **“Thread con luôn kế thừa đúng logical control-flow scope.”** Không; propagation cần hợp đồng cụ thể.
10. **“Một cflow pointcut cũng là transaction.”** Không có atomic commit/rollback chỉ từ quan hệ stack.
11. **“Pointcut khớp Q thì proxy nào cũng thấy self-invocation.”** Không đối với nhiều wrappers.
12. **“Compile-time weaving không có điều kiện runtime.”** Một số điều kiện flow vẫn được đánh giá lúc chạy.
13. **“Một root proxy mở flow thì mọi Q đều instrument.”** Q còn có coverage riêng.
14. **“`cflowbelow` luôn loại mọi P occurrence.”** Không khi P lồng dưới P khác.
15. **“Flow cleanup chỉ cần ở normal return.”** Sai; exceptional exit cũng phải pop/restore.
16. **“Để ThreadLocal nguyên trong worker pool không sao.”** Dễ rò memory và context giữa requests.
17. **“Các control-flow selectors có cùng cú pháp ở mọi engine.”** Không; capability/semantics khác nhau.
18. **“Chỉ cần root method name, không cần call/execution kind.”** Không; kind ảnh hưởng coverage.
19. **“Q càng rộng càng an toàn.”** Q quá rộng có thể instrument advice/logging chính nó.
20. **“cflow luôn nhanh như static matching.”** Không có bảo đảm; có runtime checks.
21. **“Đặt security hoàn toàn dựa trên checkout có mặt trong stack là đủ.”** Nguy hiểm khi có bypass/async và trust boundary khác.
22. **“Method trả Future đồng nghĩa toàn operation đã kết thúc.”** Không; task có thể tiếp tục.
23. **“ThreadMX/stack trace lúc bất kỳ đều luôn hiển thị context đúng.”** Instrumentation và async có thể thay ngữ cảnh.
24. **“Mọi nested root phải tạo một aspect instance.”** Tùy scope/lifecycle của engine.
25. **“Chỉ có direct child mới thuộc cflow.”** Không; cháu và các lời gọi sâu hơn cũng nằm dưới root nếu flow vẫn active.
26. **“Một advice return fallback vẫn tự giữ P active cho các cuộc gọi sau.”** Không nếu root đã rời scope.
27. **“Hễ Q ở cùng type với P là cflow(P).”** Không; quan hệ type không đủ chứng minh dynamic ancestry.
28. **“Một log `cflow=true` là bằng chứng mọi method đều woven.”** Không; phải test Q coverage từng nhóm.

## 20. Câu hỏi tự kiểm tra

**Câu 1.** `reserve()` được gọi một lần từ `checkout()` và một lần độc lập. Điểm code `reserve` có thay đổi không?  
**Đáp án:** Không nhất thiết. Nhưng điều kiện dynamic control-flow có thể true ở lần trong checkout và false ở lần độc lập.

**Câu 2.** `cflow(P)` có bao root P không?  
**Đáp án:** Trong ngữ nghĩa thông dụng của mô hình này, có.

**Câu 3.** `cflowbelow(P)` có bao root P đầu tiên không?  
**Đáp án:** Không nếu nó không nằm dưới occurrence P nào khác.

**Câu 4.** Trong `checkout outer → checkout inner`, root inner có thể match `cflowbelow(P)` không?  
**Đáp án:** Có, vì root inner nằm **bên dưới root outer**, dù tự nó cũng thuộc P.

**Câu 5.** Tại sao `boolean active` gây lỗi với nested roots?  
**Đáp án:** Inner exit có thể đặt false khi outer vẫn active; cần counter/stack hoặc restore previous context.

**Câu 6.** Nếu checkout throw, điều kiện flow trên lời gọi kế tiếp phải là gì?  
**Đáp án:** False khi root đã thoát; scope phải cleanup trong `finally`.

**Câu 7.** Nếu một `reserve` chạy trong child thread được tạo từ checkout, ThreadLocal thường có tự thấy P không?  
**Đáp án:** Không; thread mới có context riêng nếu không có propagation được thiết kế.

**Câu 8.** Một distributed trace context sang service khác có làm `cflow(P)` ở remote tự true không?  
**Đáp án:** Không. Tracing context là dữ liệu truyền qua network, không phải call stack chung của hai JVM.

**Câu 9.** Vì sao Q cần được giới hạn riêng ngoài P?  
**Đáp án:** Q quyết định loại join point có thể nhận advice; Q quá rộng gây overhead, recursion và event noise.

**Câu 10.** Phân biệt dynamic flow condition với lexical location bằng câu ngắn.  
**Đáp án:** Lexical chọn dựa trên vị trí/cấu trúc mã; dynamic flow chọn dựa trên lời gọi **đang active** tại thời điểm thực thi.

**Câu 11.** Trong ví dụ `CallFlowScopeDemo`, vì sao `ROOT enter inner` có `below=true`?  
**Đáp án:** Vì `inner` được gọi qua proxy khi `outer` root vẫn active, nên root inner là hậu duệ của root outer.

**Câu 12.** Tại sao `POINT step new-thread` có `cflow=false`?  
**Đáp án:** Demo dùng ThreadLocal thông thường và không truyền stack sang thread mới.

**Câu 13.** Có thể dùng một `cflow` condition làm phương tiện duy nhất bảo vệ quyền không?  
**Đáp án:** Không nên khi tồn tại proxy bypass, async boundary hoặc đường gọi không qua root; quyền phải được enforce ở ranh giới đáng tin.

**Câu 14.** Một instrumented method Q được gọi sau checkout đã return. Nó có còn ở trong flow không?  
**Đáp án:** Không, trừ khi có một root P khác đang active trong ngữ cảnh đang xét.

## 21. Quy trình áp dụng

1. **Xác định P rõ ràng:** đâu là workflow root thực sự, call hay execution join point?
2. **Xác định Q rõ ràng:** loại methods/operations nào chỉ cần advice khi nằm trong P?
3. **Chọn inclusive/exclusive:** cần bao root hay chỉ các hậu duệ? Xem nested roots.
4. **Xác định cơ chế can thiệp:** proxy, code weaving hoặc instrumentation khác có đủ coverage không?
5. **Thiết kế context scope:** depth/stack/owner, khi nào push/pop, cách cleanup trên exception.
6. **Phân tích recursion/nesting:** inner root có cần context riêng hay dùng chung outer?
7. **Phân tích async/thread hop:** có cần explicit propagation hay chỉ hỗ trợ synchronous scope?
8. **Không dùng flow làm bằng chứng security/transaction nếu chưa xác minh** các ranh giới thật.
9. **Thu hẹp P và Q:** tránh match mọi thứ rồi lọc ở runtime.
10. **Kiểm thử positive và negative paths:** cùng Q trong/ngoài P; root return/throw; nested P; multithreading.
11. **Kiểm thử trên artifact/deployment cuối:** classloader, agent, proxy references và precedence.
12. **Đo overhead:** instrumented joinpoint count, Q frequency, memory/context cleanup và latency.
13. **Ghi tài liệu semantic:** flow lifetime theo stack hay logical task, ai propagate và ai dispose?
14. **Theo dõi drift:** khi gọi từ workflow mới hoặc khi chuyển sang async, cập nhật test scope.

## 22. Tổng kết

**Giới hạn hành vi theo chuỗi lời gọi thay vì vị trí mã đơn lẻ** cho phép diễn đạt một ý định AOP mang tính ngữ cảnh: **chọn join point Q khi nó xảy ra bên trong dynamic control flow của root P đang active**. Các điều kiện theo `cflow(P)` thường bao root và hậu duệ; `cflowbelow(P)` thường loại chính root occurrence nhưng vẫn có thể chọn một P lồng dưới P khác.

Điểm quyết định là **quan hệ thời gian và call stack**, không phải chỉ method signature hoặc sơ đồ call graph. Vì thế thiết kế phải xử lý đúng nested/recursive roots, exception cleanup, per-flow state, proxy/weaving coverage, thread/async boundaries và runtime overhead. Java `ThreadLocal` + stack có thể mô phỏng ý tưởng cho một luồng đồng bộ nhưng không tự trở thành pointcut engine hoặc context propagation xuyên hệ thống.

Hãy kết hợp **root selector P, target selector Q và dynamic condition có phạm vi rõ**, sau đó test cả trường hợp Q nằm trong P lẫn những trường hợp nó phải ở ngoài. Khi cùng một method được tái sử dụng ở nhiều workflow, cách nhìn theo flow giúp định nghĩa chính xác **khi nào** một advice thực sự có ý nghĩa, thay vì áp một chính sách quá rộng lên mọi lời gọi.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-control-flow-004">Loại trừ mã của chính aspect để tránh đệ quy</a>

<details>
<summary>Xem chi tiết</summary>

# Loại trừ mã của chính aspect để tránh đệ quy trong AOP

## 1. Vấn đề cốt lõi

Trong **Aspect-Oriented Programming (AOP)**, một **aspect** chứa logic xử lý mối quan tâm xuyên suốt như logging, audit, metrics hoặc kiểm tra quyền. **Pointcut** lựa chọn các **join point** thuộc mô hình của công cụ; **advice** được thực thi khi một join point phù hợp xảy ra.

Một lỗi thường gặp: advice muốn quan sát rất nhiều method, nhưng **mã mà advice tự thực thi** cũng tạo ra join point phù hợp. Điều này có thể khiến aspect **tự kích hoạt chính nó** nhiều lần.

Ví dụ:

```text
Caller -> Business.save()
  -> LoggingAdvice.before()
      -> Logger.write()
          -> LoggingAdvice.before()
              -> Logger.write()
                  -> LoggingAdvice.before()
                      ...
```

Nếu không có điều kiện dừng, chuỗi có thể gây `StackOverflowError`, tăng đột biến CPU/log, hoặc bão sự kiện trong bộ ghi audit.

**Loại trừ mã của chính aspect** là một nhóm kỹ thuật ngăn những join point phát sinh từ đường hoạt động nội bộ của aspect bị advice của chính nó chọn lại ngoài ý muốn. Nhưng phải làm chính xác: loại trừ **quá hẹp** không hết đệ quy; loại trừ **quá rộng** làm mất advice của những lời gọi nghiệp vụ lồng nhau cần quan sát.

Bài tập trung vào AOP nói chung từ cơ bản đến nâng cao. Các ví dụ cú pháp **AspectJ** được nêu rõ là ví dụ **một ngôn ngữ pointcut cụ thể**, không phải quy tắc của mọi công cụ. Ví dụ Java 17 thuần cuối bài minh họa hai chiến lược lọc mà không cần công cụ weaving bên ngoài.

## 2. Mục tiêu học tập

Sau bài này, người học có thể:

1. Xác định đường đệ quy bắt đầu ở advice và quay về advice qua một hoặc nhiều helper.
2. Phân biệt **lời gọi lồng nhau hợp lệ** với **self-interception** ngoài ý muốn.
3. Phân biệt loại trừ theo **vị trí khai báo mã (lexical scope)**, theo **kiểu/nhóm method** và theo **control flow**.
4. Đọc đúng ý nghĩa của `within`, `withincode`, `adviceexecution`, `cflow` và `cflowbelow` trong cú pháp công cụ có hỗ trợ.
5. Hiểu tại sao loại trừ call site bên trong aspect có thể không loại trừ **method execution trong helper ở class khác**.
6. Dùng guard runtime phạm vi hẹp và khôi phục trạng thái khi có exception.
7. Giữ lại interception cho business call lồng nhau, không vô hiệu hóa toàn bộ advice.
8. Kiểm thử cả những điểm **phải loại** và những điểm **vẫn phải áp dụng**.
9. Thiết kế an toàn khi có nhiều aspect, callback, async, multi-thread và lỗi từ logging.

## 3. Những khái niệm phải tách bạch

| Khái niệm | Câu hỏi cần trả lời |
| --- | --- |
| **Join point** | Loại sự kiện nào có thể nhận advice: call, execution hay loại khác? |
| **Pointcut** | Tại sự kiện nào điều kiện lựa chọn đúng? |
| **Shadow** | Vị trí trong chương trình có thể tạo join point theo mô hình weaving tĩnh |
| **Advice execution** | Bản thân phần thực thi advice có được mô hình hóa thành join point không? |
| **Lexical scope** | Mã tạo join point được khai báo **ở đâu**? |
| **Control flow** | Khi join point xảy ra, stack động đang nằm **trong lời gọi nào**? |
| **Helper/sink** | Hàm phục vụ advice: log, metrics, audit writer, serializer... |
| **Self-interception** | Advice gọi một thao tác lại bị cùng advice hoặc chain liên quan chặn |
| **Recursion** | Call graph quay lại thành phần đang chạy trước khi kết thúc |
| **Reentrancy guard** | Điều kiện runtime ngăn đường tái nhập không mong muốn |
| **Self-invocation bypass** | Target gọi nội bộ mà không đi qua proxy, làm advice **không chạy** |
| **Proceed** | Around advice tiếp tục phần bên trong (nếu công cụ hỗ trợ) |

**Mọi self-interception có thể dẫn tới tái nhập**, nhưng không phải lần tái nhập nào cũng gây vòng vô hạn. Ví dụ một logger có counter giới hạn có thể thoát, song hệ thống vẫn ghi log lặp không đúng mục đích.

**Nested business calls** như `checkout → reserve → charge` có thể hoàn toàn hợp lệ; không được lọc bỏ chỉ vì chúng chạy khi advice ngoài chưa kết thúc.

## 4. Call graph giúp tìm nguồn đệ quy

Trước khi sửa pointcut, hãy vẽ tất cả cạnh mà advice tạo ra:

```text
Business.service()
    |
    v
TraceAspect.before()
    |
    +--> TraceFormatter.format(args)
    |         |
    |         +--> args.toString()
    |
    +--> AuditSink.write(event)
              |
              +--> Repository.insertAudit(event)
```

Nếu pointcut đang lựa chọn toàn bộ execution của `toString`, `write` hoặc `insertAudit`, những method đó có thể gọi lại `TraceAspect.before`.

Điểm cần xem:

- **Từ advice đi tới helper nào?**
- **Helper thuộc class/package nào?**
- **Join-point kind** của pointcut là call hay execution?
- **Vị trí lexical** của điểm được chọn có nằm trong aspect không?
- **Control flow** tại điểm ấy có bắt nguồn từ advice không?
- Có guard điều kiện dừng không?
- Có aspect thứ hai làm đường vòng trở về aspect thứ nhất không?

Không cần thấy một câu lệnh `TraceAspect.before()` gọi trực tiếp chính nó mới có recursion. Một chu trình dài qua hai ba class vẫn là recursion.

## 5. Hai vị trí khác nhau: điểm gọi và phần thực thi

Giả sử advice trong `TraceAspect` gọi `AuditSink.write`:

```text
TraceAspect code                    AuditSink code
+---------------------+             +-----------------------+
| sink.write(event) --+------------>| write(event)          |
|         ^           |             |   persist(event)      |
|       call site     |             |       ^ execution     |
+---------------------+             +-----------------------+
```

Một pointcut chọn **method-call join point** ở dòng `sink.write(event)` có thể thấy điểm ấy nằm **trong mã aspect**.

Một pointcut chọn **method-execution join point** của `AuditSink.write` lại có điểm xảy ra trong **mã được khai báo ở AuditSink**, không phải lexical body của aspect.

Vì vậy:

- Loại trừ mã **được khai báo trong aspect** có thể chặn call site bên trái.
- Nó **không tự động loại** mọi method execution của helper bên phải nếu helper thuộc class khác.
- Muốn loại cả đường helper do advice phát sinh, có thể cần điều kiện control flow hoặc exclusion chính xác hơn theo method/type.

Điểm khác biệt này đặc biệt quan trọng khi một pointcut rộng chọn `execution(* *(..))` hoặc cùng lúc chọn nhiều loại join point.

## 6. Cách phòng ngừa thứ nhất: thu hẹp pointcut theo nghiệp vụ

Phương án tốt thường là **chọn đúng những thao tác thực sự cần advice**, thay vì bắt mọi method rồi loại ra hàng loạt.

Ví dụ mục tiêu là audit khi có thao tác ghi đơn hàng:

```text
Nên chọn:
  OrderService.submit(...)
  PaymentService.capture(...)
  InventoryService.reserve(...)

Không nhất thiết chọn:
  AuditSink.write(...)
  TraceFormatter.format(...)
  JsonEncoder.encode(...)
  Logger.debug(...)
```

Các dòng này là **mô tả lựa chọn**, không phải cú pháp DSL.

Ưu điểm:

- Tập điểm khớp nhỏ, dễ review và kiểm thử.
- Ít khả năng aspect can thiệp vào dependency của chính nó.
- Giảm lượng event và overhead.
- Ít phụ thuộc guard động.
- Dễ lập báo cáo "advised/unadvised" theo use case.

Nhược điểm:

- Khi thêm nghiệp vụ mới, cần cập nhật chính sách lựa chọn.
- Một số mối quan tâm như profiling toàn ứng dụng thực sự cần phạm vi rộng.
- Một helper có thể vừa được advice sử dụng, vừa có lời gọi trực tiếp từ nghiệp vụ cần quan sát.

Với helper dùng hai vai trò khác nhau, loại nó **vĩnh viễn** theo class có thể quá mạnh; lúc này điều kiện theo **nguồn phát sinh lời gọi** có giá trị.

## 7. Loại trừ theo vị trí lexical của mã

Một số pointcut language có điều kiện "join point nằm trong vùng mã được khai báo ở kiểu X", thường gọi là **lexical selection/exclusion**.

Ý tưởng:

```text
businessJoinPoints
AND NOT codeLexicallyInside(MyAspect)
```

Điều này hữu ích nếu advice gọi các phương thức ngay trong body aspect và những call site ấy không được phép tạo advice tiếp.

**Nhưng lexical exclusion không đồng nghĩa loại trừ mọi thao tác xảy ra trong quá trình advice đang chạy.** Một method execution của `AuditSink` được khai báo trong `AuditSink`, dù do aspect gọi tới, vẫn nằm ngoài body của `MyAspect`.

### 7.1. Lexical và dynamic khác nhau

Một vị trí source có thể được gọi trong nhiều control flow; lexical scope của nó **không đổi** khi caller thay đổi.

```text
Business -> SharedHelper.execute()   [lexical: SharedHelper]
Aspect   -> SharedHelper.execute()   [lexical: SharedHelper]
```

Cả hai có cùng execution method được khai báo trong SharedHelper. Nếu muốn chỉ bỏ lời gọi do aspect gây ra nhưng vẫn audit lời gọi từ business, điều kiện dựa **chỉ trên kiểu chứa mã** thường không đủ.

## 8. Cú pháp minh họa AspectJ: `within`

**Phần này dùng cú pháp AspectJ cụ thể**, không phải Java `javac` thuần và không áp dụng mặc nhiên cho các hệ thống AOP khác.

Với cú pháp này, `within(TypePattern)` chọn các join point được gắn với mã **được khai báo trong** những type khớp mẫu.

Mẫu minh họa:

```java
aspect TraceAspect {
    pointcut observedCalls():
        call(* *(..)) && !within(TraceAspect);

    before(): observedCalls() {
        System.out.println("observed call");
    }
}
```

Đây là **mảnh ngôn ngữ AspectJ** cần compiler/weaver tương ứng; không chạy độc lập bằng `javac TraceAspect.java`. Nó giải thích một tư tưởng: chọn lời gọi method nhưng **không chọn** call site nằm trong mã khai báo của `TraceAspect`.

Nếu advice gọi `System.out.println` ngay trong body aspect, call site đó được loại theo lexical scope.

**Giới hạn:** giả sử aspect gọi `AuditSink.write`, mà bên trong `AuditSink.write` gọi `Repository.save`. Những join point của mã trong AuditSink/Repository **không trở thành lexical code trong TraceAspect** chỉ vì được gọi từ advice. Nếu pointcut chọn execution/call ở các vị trí bên ngoài aspect, điều kiện `!within(TraceAspect)` một mình có thể không chặn hết chu trình.

### 8.1. Đừng nhầm `within` với `target`

Một pointcut lọc **kiểu của receiver/target** không giống lọc **vị trí khai báo code**. Một instance của `AuditSink` được gọi trong body aspect nhưng target type vẫn là `AuditSink`. Cần xác định bài toán đang lọc theo **đối tượng**, **vị trí source** hay **luồng thực thi**.

### 8.2. Không có công thức “loại tất cả aspect” cho mọi hệ thống

Mỗi engine có tập join-point kind khác nhau. Với proxy interface chỉ chặn lời gọi vào target được quản lý, body advice có thể không tự được proxy nếu các helper không dùng proxy ấy; nhưng nếu advice gọi một service proxy khác, vẫn có thể tạo vòng. Không được mặc định cần hay không cần `within` ở mọi engine.

## 9. Cú pháp minh họa AspectJ: `withincode`

Trong một số DSL, thay vì loại cả type, có thể loại một method hoặc constructor theo **vị trí lexical được khai báo**.

Trong AspectJ, `withincode(MethodPattern)` ràng buộc join point nằm trong code của method có chữ ký phù hợp.

Ví dụ **mảnh minh họa cú pháp đặc thù**, không phải chương trình chạy độc lập:

```java
pointcut selected():
    call(* *(..))
    && !withincode(* TraceFormatter.format(..));
```

Ý tưởng là loại các call site **nằm trong phần thân** `TraceFormatter.format` theo pattern, không tự loại mọi invocation từ xa có liên quan tới formatter.

**Cảnh báo:** đoạn trên chỉ thể hiện cơ chế lexical. Trong thiết kế thật, cần đối chiếu chữ ký, package, visibility và hạn chế compiler để bảo đảm pattern match đúng method.

### 9.1. Khi nào lexical exclusion đủ?

Nó thường đủ khi:

- Điểm đang chọn là **call site** nằm trong aspect hoặc helper được xác định.
- Vòng đệ quy chỉ được tạo bởi những call site ấy.
- Không có execution join point khác trong helper tiếp tục khớp pointcut.
- Call graph và weaving scope đã được kiểm chứng.
- Negative tests xác nhận không còn self-interception.

Nó có thể chưa đủ nếu **execution join point của helper** vẫn khớp hoặc aspect gọi qua callback/dispatcher tạo đường kiểm soát gián tiếp.

## 10. Loại trừ theo control flow

Khác với lexical filter, **control-flow exclusion** xem một join point có nằm trong quá trình thực thi của một điểm bắt đầu hay không.

Sơ đồ:

```text
Business.save()
   |
   v
TraceAspect advice starts       <-- nguồn của control flow
   |
   +--> Formatter.format()
   |       +--> input.toString()
   |
   +--> Sink.write()
           +--> Repository.append()
   |
   v
Advice ends
```

Một policy có thể nói:

> Đối với aspect tracing này, không áp trace lên những join point xảy ra **trong luồng điều khiển của chính advice đang phát trace**.

Ưu điểm:

- Bao gồm cả helper khác class được gọi sâu qua nhiều tầng.
- Không cần biết hết mọi tên helper khi call graph thay đổi.
- Có thể giữ instrumentation của cùng helper khi helper được gọi **ngoài** control flow advice.

Rủi ro:

- Có thể loại **quá nhiều** join point: một business callback được advice chủ động gọi cũng ở trong control flow ấy.
- Có thể cần guard runtime và overhead động.
- Không nhất thiết tiếp tục có hiệu lực khi công việc chuyển sang **thread/task khác**.
- Phải hiểu có bao gồm chính điểm bắt đầu hay không.
- Có thể tương tác phức tạp với nhiều aspect, recursion, proceed và exception.

**Điểm mấu chốt:** control-flow exclusion giải quyết **đường phát sinh** chứ không chỉ **vị trí khai báo code**, nhưng cần phạm vi hợp đồng rõ.

## 11. Cú pháp minh họa AspectJ: `adviceexecution` và `cflow`

Trong AspectJ có join-point designator `adviceexecution()` để lựa chọn **việc thực thi advice**. Ta có thể kết hợp với `within` để giới hạn **advice thuộc chính aspect cần bảo vệ**.

Ví dụ minh họa dựa trên ngữ nghĩa tài liệu chính thức:

```java
aspect TraceAspect {
    pointcut ownAdvice():
        adviceexecution() && within(TraceAspect);

    before(): call(* *(..)) && !cflow(ownAdvice()) {
        System.out.println("call outside this advice control flow");
    }
}
```

Đây là **AspectJ language snippet**, không phải Java standalone. Ý nghĩa:

1. `ownAdvice()` chỉ những advice-execution join point được khai báo trong `TraceAspect`.
2. `cflow(ownAdvice())` chọn các join point thuộc control flow của việc thực thi advice ấy.
3. `!cflow(ownAdvice())` loại các call join point xảy ra **trong control flow của chính advice**.
4. Nhờ có `within(TraceAspect)` trong `ownAdvice`, chính sách không tự động lấy mọi advice của **mọi aspect** làm gốc control flow.

Mô hình này có thể ngăn cả lời gọi từ advice tới logger và những lời gọi sâu hơn nảy sinh trong quá trình logger xử lý, trong phạm vi **call stack** mà công cụ mô hình hóa.

### 11.1. Vì sao cần giới hạn `ownAdvice`?

Nếu chỉ viết ý tưởng tương tự:

```java
call(* *(..)) && !cflow(adviceexecution())
```

thì có thể loại cả những join point phát sinh khi **aspect khác** đang hoạt động. Điều đó có thể tắt instrumentation hợp lệ một cách không chủ đích.

Tốt hơn phải chỉ ra **nguồn control flow nào thực sự cần tránh**: advice của chính aspect, một logger emission method, hay một nhóm callback.

### 11.2. Tránh diễn giải `cflow` như context async tự động

Trong ngữ nghĩa AspectJ, `cflow(P)` dựa vào luồng điều khiển động của những join point `P`. Không suy rằng một future/callback chạy ở thread khác vẫn mang cùng cflow như call stack đã tạo nó.

Nếu cần theo dõi logical flow qua async, phải xác định **context propagation** và ranh giới task riêng.

### 11.3. Không dùng cú pháp công cụ làm định nghĩa AOP chung

Một số engine proxy không hỗ trợ `adviceexecution` hoặc `cflow`. Khi ấy có thể phải dùng **exclusion dựa trên API/helper**, cấu hình interceptor, guard runtime hoặc thiết kế lại điểm quan sát.

## 12. `cflow` so với `cflowbelow`

Theo ngữ nghĩa của AspectJ:

- `cflow(P)` chọn các join point nằm trong control flow của `P`, **bao gồm** chính join point `P`.
- `cflowbelow(P)` chọn những join point **nằm bên dưới** trong control flow ấy, **không bao gồm** `P`.

Ví dụ:

```text
P starts                         <-- cflow(P) có P
    Q                            <-- cflow(P), cflowbelow(P)
        R                        <-- cflow(P), cflowbelow(P)
P ends
```

Trong bài toán tránh self-interception, việc bỏ qua chính root join point hoặc chỉ các descendant là một quyết định cần kiểm tra chính xác. Không thể thay `cflow` bằng `cflowbelow` theo cảm giác rằng tên gọi “dưới” an toàn hơn.

### 12.1. Phải suy theo *loại* join point

Nếu `P` là `adviceexecution()` còn pointcut cần lọc chỉ chọn `call` join point, root advice-execution join point **không đồng thời là một call join point**. Bởi vậy sự khác biệt bao gồm root có thể không hiện ra trong một số phép kết hợp, nhưng vẫn quan trọng với các tổ hợp pointcut khác.

Không nên dùng các toán tử như `call(..) && execution(..)` rồi tưởng rằng một điểm vừa là call vừa là execution. Thông thường đó là **hai loại join point khác nhau**.

## 13. Dùng exclusion theo helper: khi nào tốt hơn cflow?

Giả sử `AuditAspect` chỉ gọi một helper duy nhất `AuditSink.append` để lưu event. Nếu helper này **không bao giờ cần được aspect audit**, có thể chỉ loại nó theo kiểu/method.

Mô tả:

```text
eligibleBusinessOperations
AND NOT auditSinkMethods
```

Ưu điểm:

- Dễ đọc và dễ lập danh sách loại trừ.
- Có thể xác định ở thời điểm weaving.
- Ít logic runtime hơn một control-flow guard.
- Không loại những lời gọi nghiệp vụ khác xảy ra trong lời gọi advice.

Nhược điểm:

- Nếu `AuditSink.append` được nghiệp vụ dùng trực tiếp và **cần được audit**, loại theo kiểu làm mất coverage đó.
- Nếu helper gọi vào một service **khác** vẫn nằm trong pointcut, exclusion chỉ một helper có thể không đủ với execution join points.
- Helper mới có thể xuất hiện khi refactor, tạo đường vòng mới.

### 13.1. Phối hợp chọn hẹp và guard hẹp

Một chiến lược thực tế:

1. Chọn **nghiệp vụ** trước, không chọn toàn hệ thống vô điều kiện.
2. Loại **các helper instrumentation** nếu chúng không cần advice.
3. Chỉ bổ sung **control-flow exclusion** hoặc guard động khi có những đường tái nhập không thể mô tả an toàn bằng static scope.
4. Kiểm thử những nested business calls vẫn được advice.
5. Theo dõi số lần advice, số lần target và số lần guard skip.

Không nên đồng nhất “ít event hơn” với “đã sửa đúng” nếu mất cả event nghiệp vụ bắt buộc.

## 14. Java 17 runnable: exclusion tĩnh và control-flow guard hẹp

Java 17 không tích hợp một DSL pointcut AOP thống nhất trong ngôn ngữ. Để mô phỏng hành vi, ví dụ này dùng **JDK dynamic proxy**. Hai cơ chế được mô phỏng:

- **Static method exclusion:** method `emit` là helper phát log, nên proxy **không áp advice** cho `emit`.
- **Dynamic flow guard:** method `inspect` vẫn có thể được advice khi caller gọi trực tiếp qua proxy, **nhưng** được bỏ instrumentation khi nó được advice gọi để hỗ trợ việc phát event.

Đặc biệt, `outer` gọi `inner` qua proxy. `inner` vẫn phải được advice **ngay cả khi đã có một outer advice đang hoạt động**, tránh sai lầm “depth lớn hơn 0 thì bỏ qua mọi advice”.

Tạo tệp `AspectExclusionDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class AspectExclusionDemo {

    interface Actions {
        String outer(String id);
        String inner(String id);
        void emit(String message);
        String inspect(String key);
    }

    static final class Core implements Actions {
        private Actions self;

        void setSelf(Actions proxy) {
            this.self = proxy;
        }

        @Override
        public String outer(String id) {
            System.out.println("business:outer:" + id);
            return self.inner(id) + "!";
        }

        @Override
        public String inner(String id) {
            System.out.println("business:inner:" + id);
            if ("ERR".equals(id)) {
                throw new IllegalStateException("rejected");
            }
            return "done:" + id;
        }

        @Override
        public void emit(String message) {
            System.out.println("sink:" + message);
        }

        @Override
        public String inspect(String key) {
            System.out.println("business:inspect:" + key);
            return "ok:" + key;
        }
    }

    static Object callTarget(Method method, Core target,
                             Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException wrapper) {
            throw wrapper.getCause();
        }
    }

    public static void main(String[] args) {
        Core target = new Core();

        ThreadLocal<Boolean> emitting =
                ThreadLocal.withInitial(() -> false);
        ThreadLocal<Integer> depth =
                ThreadLocal.withInitial(() -> 0);

        Actions proxy = (Actions) Proxy.newProxyInstance(
                Actions.class.getClassLoader(),
                new Class<?>[] { Actions.class },
                (Object p, Method method, Object[] arguments) -> {
                    String name = method.getName();

                    // Rule 1: helper phát event không nhận advice.
                    if ("emit".equals(name)) {
                        System.out.println("filter:static:emit");
                        return callTarget(method, target, arguments);
                    }

                    // Rule 2: không instrument inspect khi do advice phát ra.
                    if ("inspect".equals(name) && emitting.get()) {
                        System.out.println("filter:flow:inspect");
                        return callTarget(method, target, arguments);
                    }

                    int level = depth.get();
                    System.out.println("advice:before:" + name
                            + ":d=" + level);
                    depth.set(level + 1);

                    try {
                        boolean oldEmitting = emitting.get();
                        emitting.set(true);
                        try {
                            ((Actions) p).emit("enter:" + name);
                            ((Actions) p).inspect("sink-check");
                        } finally {
                            if (oldEmitting) {
                                emitting.set(true);
                            } else {
                                emitting.remove();
                            }
                        }

                        try {
                            Object result = callTarget(method, target, arguments);
                            System.out.println("advice:after:" + name);
                            return result;
                        } catch (Throwable original) {
                            System.out.println("advice:throw:" + name + ":"
                                    + original.getClass().getSimpleName());
                            throw original;
                        }
                    } finally {
                        if (level == 0) {
                            depth.remove();
                        } else {
                            depth.set(level);
                        }
                        System.out.println("advice:finally:" + name
                                + ":d=" + level);
                    }
                }
        );

        target.setSelf(proxy);

        System.out.println("case:ok");
        System.out.println("caller:" + proxy.outer("A"));

        System.out.println("case:error");
        try {
            proxy.outer("ERR");
        } catch (IllegalStateException error) {
            System.out.println("caller:error:" + error.getMessage());
        }

        System.out.println("case:inspect-external");
        System.out.println("caller:" + proxy.inspect("external"));

        System.out.println("case:direct");
        System.out.println("caller:" + target.inner("DIRECT"));
    }
}
```

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

```bash
javac AspectExclusionDemo.java
java AspectExclusionDemo
```

**Đầu ra xác định:**

```text
case:ok
advice:before:outer:d=0
filter:static:emit
sink:enter:outer
filter:flow:inspect
business:inspect:sink-check
business:outer:A
advice:before:inner:d=1
filter:static:emit
sink:enter:inner
filter:flow:inspect
business:inspect:sink-check
business:inner:A
advice:after:inner
advice:finally:inner:d=1
advice:after:outer
advice:finally:outer:d=0
caller:done:A!
case:error
advice:before:outer:d=0
filter:static:emit
sink:enter:outer
filter:flow:inspect
business:inspect:sink-check
business:outer:ERR
advice:before:inner:d=1
filter:static:emit
sink:enter:inner
filter:flow:inspect
business:inspect:sink-check
business:inner:ERR
advice:throw:inner:IllegalStateException
advice:finally:inner:d=1
advice:throw:outer:IllegalStateException
advice:finally:outer:d=0
caller:error:rejected
case:inspect-external
advice:before:inspect:d=0
filter:static:emit
sink:enter:inspect
filter:flow:inspect
business:inspect:sink-check
business:inspect:external
advice:after:inspect
advice:finally:inspect:d=0
caller:ok:external
case:direct
business:inner:DIRECT
caller:done:DIRECT
```

### 14.1. Giải thích `filter:static:emit`

Handler nhận lời gọi `emit` qua proxy, nhưng filter theo tên cho phép gọi target trực tiếp và **không** phát thêm advice. Nhờ đó phát log một lần không tạo một log của chính log.

Trong production, nếu có overload/method trùng tên cần phân biệt bằng signature/metadata chính xác; filter chỉ theo tên trong demo nhằm minh họa nguyên lý, **không phải mẫu pointcut hoàn chỉnh**.

### 14.2. Giải thích `filter:flow:inspect`

Mỗi advice đặt `emitting=true` trong **phạm vi ngắn khi phát event**. Nó gọi `proxy.inspect("sink-check")`. Handler thấy method `inspect` trong đúng scope này nên **chuyển tiếp mà không instrument**.

Sau khối phát event, `emitting` được trả về trạng thái trước đó (hoặc gỡ), rồi mới chạy business. Do vậy:

- `Core.outer` gọi `self.inner` qua proxy vẫn có `advice:before:inner`.
- Caller ngoài gọi `proxy.inspect("external")` vẫn nhận advice, bởi `emitting` đã false.
- Một lời gọi trực tiếp `target.inner` không qua proxy nên **không có advice**, đúng giới hạn ví dụ.

### 14.3. Vì sao không đặt guard quanh toàn bộ `callTarget`?

Nếu giữ `emitting=true` cả khi gọi `Core.outer`, lời gọi `self.inner` có thể bị loại nhầm nếu guard áp chung cho mọi method. Thiết kế demo giới hạn guard ở **emission phase** và chỉ dùng nó để loại `inspect` trong phase đó.

Thực tế, không có nguyên tắc mọi nested business call đều phải được advice; điều quan trọng là **loại có chủ đích, theo hợp đồng**, không biến guard tránh recursion thành cách tắt toàn bộ instrumentation.

### 14.4. Lỗi và cleanup

Khi `inner("ERR")` ném `IllegalStateException`, `callTarget` bỏ lớp bọc `InvocationTargetException` của reflection và ném lại lỗi gốc. Advice trong ghi `advice:throw:inner` rồi dọn depth; advice ngoài ghi `advice:throw:outer` rồi dọn depth.

Nhờ `finally`, invocation tiếp theo `inspect("external")` bắt đầu tại `d=0`, không bị nhiễm depth từ lần lỗi.

### 14.5. Ví dụ này không chứng minh điều gì?

- JDK proxy **không dùng** `within`, `withincode` hoặc `cflow` của AspectJ; hai filter thủ công chỉ thể hiện **ý tưởng tương đương về mục tiêu**.
- Không có bytecode weaving hay parser DSL.
- Một proxy method call **không** bao mọi lời gọi direct trên target.
- `ThreadLocal` ở ví dụ không tự truyền context sang task async.
- Exclusion/guard không tạo thread safety cho target.
- Demo dùng output đơn luồng; trình tự đa luồng có thể xen kẽ.

## 15. Chọn guard runtime: biến boolean, depth hay invocation token?

### 15.1. Guard boolean cho emission ngắn

Nếu chỉ cần biết "đang ghi telemetry từ advice không?", một boolean **per-thread/per-invocation** có thể đủ cho đường thực thi đồng bộ.

Nhưng phải kiểm tra:

- Scope đủ hẹp chưa?
- Có nested emission khi boolean đã true không?
- Có lưu/khôi phục trạng thái cũ không?
- Có lỗi ném trước cleanup không?
- Có nguy cơ guard của một task ảnh hưởng task khác không?

### 15.2. Guard theo depth

Một `depth` đếm số lần invocation đang hoạt động:

```text
enter outer -> depth 1
enter inner -> depth 2
exit inner  -> depth 1
exit outer  -> depth 0
```

Depth hữu ích cho indentation hoặc xác định root span, **không nhất thiết phù hợp để quyết định bỏ qua advice**. Nếu loại mọi invocation có `depth>1`, ta dễ bỏ cả nested business operations.

### 15.3. Invocation token và scope ownership

Khi nhiều aspect chia sẻ infrastructure, một boolean không cho biết ai đang sở hữu trạng thái guard. Một token hoặc stack scope tường minh giúp khôi phục trạng thái đúng khi có callback lồng nhau:

```text
previousState = guard.capture()
guard.enter("audit-emission")
try:
    sendAudit()
finally:
    guard.restore(previousState)
```

Đây là **mã giả**, không phải Java API chuẩn.

### 15.4. Guard toàn cục là lựa chọn nguy hiểm

Một `static boolean currentlyLogging` không được bảo vệ có thể tạo race. Kể cả đã đồng bộ, nó còn có thể khiến request của thread B **mất audit** chỉ vì thread A đang ghi log.

Guard thường phải thuộc invocation, thread hoặc logical task theo đúng hợp đồng, không tự tạo một cờ cho cả VM.

## 16. `ThreadLocal` giúp gì và không giúp gì?

Java 17 có `ThreadLocal<T>` phù hợp cho một số state **đồng bộ theo thread**.

Ưu điểm:

- Mỗi thread có giá trị guard riêng.
- Có thể giữ depth hoặc ngữ cảnh từng thread.
- Dễ dọn bằng `try/finally`.
- Tránh xung đột trực tiếp giữa thread khác nhau khi truy cập cùng cờ.

Giới hạn:

- Thread pool **tái sử dụng** thread; nếu quên `remove`, invocation sau có thể nhận trạng thái cũ.
- Nested calls trên cùng thread vẫn cần stack/local frame khi có nhiều state.
- Một logical request có thể chạy qua **nhiều thread**.
- Future/callback async không nhất thiết thấy guard/context của thread tạo task.
- `InheritableThreadLocal` không thay thế context propagation theo logical task, đặc biệt với pool.
- Biến guard có thể ngăn recursion nhưng không đảm bảo log thành công hay thread safety của target.

### 16.1. Không mang guard emission sang task nghiệp vụ

Trace ID/correlation ID có thể cần truyền sang task mới. Nhưng `emitting=true` chỉ mang ý nghĩa **đang thực hiện emission của advice tại scope hiện tại**. Sao chép cờ này sang async worker có thể khiến một tác vụ nghiệp vụ hợp lệ bị bỏ instrumentation.

Phân biệt rõ **business context** và **implementation guard**.

## 17. Exception trong guard và khôi phục trạng thái

Nếu emission helper ném exception, guard cần được reset:

```java
boolean previous = emitting.get();
emitting.set(true);
try {
    emitEvent();
} finally {
    if (previous) {
        emitting.set(true);
    } else {
        emitting.remove();
    }
}
```

Đây là **mảnh Java minh họa**; giả định `emitting` là `ThreadLocal<Boolean>` và `emitEvent` là method có thể ném lỗi.

### 17.1. Vì sao khôi phục trạng thái cũ thay vì luôn false?

Nếu có scope ngoài đã đặt guard true rồi scope trong cũng đặt true, scope trong không nên tự xóa guard của scope ngoài. Khôi phục `previous` đúng với **ownership lồng nhau**.

### 17.2. Lỗi của cleanup có thể che lỗi chính

Một `finally` ném lỗi mới có thể thay exception đang thoát theo ngữ nghĩa Java. Do đó cleanup guard nên đơn giản, không gọi I/O hoặc helper phức tạp. Nếu phát log lỗi trong finally và log lại bị advice chọn, rất dễ lặp đúng tình huống đang cần chữa.

### 17.3. Phân biệt best-effort và mandatory audit

Với metrics best-effort, lỗi exporter có thể được bắt và ghi nhận phụ để **không phá** kết quả business. Nhưng với audit bắt buộc, việc gửi audit thất bại có thể buộc chặn thao tác; hành vi này phải được thiết kế/ghi trong contract, không tùy tiện "nuốt" exception.

## 18. Khi helper trực tiếp nằm trong class aspect

Một aspect có thể tự chứa method `formatEvent` hoặc `emitEvent`. Dùng `!within(MyAspect)` cho **call sites** trong aspect có thể đủ với một số join-point selection.

Tuy nhiên cần phân biệt:

- **Call** tới `formatEvent` từ code ngoài aspect: call site ở ngoài, nên `!within(MyAspect)` có thể vẫn khớp nếu phương thức đó được gọi từ business.
- **Execution** của `formatEvent`: code được khai báo trong aspect, nên lexical filter có thể loại nó.
- **Call** do chính `formatEvent` thực hiện: call site nằm trong body aspect, có thể bị loại.
- **Execution** của `Logger.write` ở class bên ngoài: không tự bị loại bởi `!within(MyAspect)`.

Chính loại join point giải thích tại sao cùng một biểu thức lexical có thể cho kết quả khác dự đoán.

### 18.1. Không dùng exclusion thiếu chính xác theo tên method

Loại mọi method tên `log` trên toàn chương trình có thể vô tình loại một business method `OrderService.log(...)` quan trọng. Ưu tiên pattern có **declaring type**, package hoặc marker được thiết kế rõ, rồi thêm kiểm thử khi refactor.

## 19. Đệ quy do `toString`, getter hoặc serialization

Một advice muốn ghi tham số:

```text
before:
    logger.info("arg=" + arg.toString())
```

Nếu `arg.toString()` tự gọi getter/service mà pointcut đang chọn, advice có thể tiếp tục phát log từ việc chuẩn bị log.

Ngay cả khi đã loại `Logger.write`, chu trình có thể nằm ở bước **format dữ liệu** trước khi tới logger.

Biện pháp:

- Tránh gọi `toString()` của object nghiệp vụ phức tạp chỉ để trace.
- Chỉ ghi những trường đã được cho phép và có cách lấy ổn định.
- Dùng serializer/formatter nằm ngoài scope cần advice hoặc có guard riêng.
- Hạn chế độ sâu khi duyệt object graph, chống cycle và giới hạn kích thước event.
- Không thực hiện API call/IO bất ngờ trong formatter.
- Kiểm tra cả exception path của formatter khi source object có lỗi.

**Không log toàn bộ args/result theo mặc định**: ngoài recursion, còn rủi ro lộ mật khẩu, token, thông tin cá nhân và dữ liệu tài chính.

## 20. Đệ quy qua nhiều aspect

Một chu trình không nhất thiết chỉ nằm trong một aspect:

```text
AuditAspect
  -> AuthorizationService.check()
       -> SecurityAspect
            -> AuditClient.write()
                 -> AuditAspect
```

Nếu `AuditAspect` chỉ loại call trực tiếp bên trong mình, `AuditClient.write` do `SecurityAspect` khởi tạo vẫn có thể tạo đường quay về.

Cần thiết kế **graph phụ thuộc của instrumentation**:

- Sink/logging adapter nên nằm ở lớp hạ tầng ít phụ thuộc aspect chính sách.
- Tránh để kiểm quyền của audit sink lại phụ thuộc việc audit chính nó trừ khi có một protocol bootstrap rõ ràng.
- Chọn policy cho thao tác nội bộ, khác policy cho operation public.
- Đánh dấu event theo nguồn và guard scope khi cần.
- Kiểm thử các chu trình A → B → A, không chỉ A → A.

### 20.1. Loại mọi join point khi có bất kỳ advice nào đang chạy là quá mạnh

Nếu loại toàn bộ `cflow(adviceexecution())` không ràng buộc aspect nguồn, một advice metrics có thể vô tình tắt security advice của business nested call. Các yêu cầu quan trọng về bảo mật hoặc kiểm toán không được phép biến mất chỉ vì một telemetry advice khác đang hoạt động.

## 21. Phân biệt self-interception và self-invocation bypass

Hai hiện tượng dễ bị đánh đồng:

**Self-interception:**

```text
Advice -> helper -> advice -> helper ...
```

Hệ thống bị can thiệp **quá nhiều**.

**Self-invocation bypass (trong một số proxy):**

```text
Caller -> Proxy -> Target.outer()
                    |
                    +-> this.inner()
                        (không qua proxy)
```

Hệ thống bị can thiệp **quá ít**.

Nếu khắc phục self-interception bằng guard chung `skipWhenNested`, ta có thể làm self-invocation bypass còn trầm trọng hơn hoặc tạo thêm đường bỏ audit.

Giải pháp phải xác định:

- **Receiver** của lời gọi (proxy hay target).
- **Loại join point**.
- **Điểm kích hoạt advice**.
- **Nguồn control flow**.
- **Mục tiêu coverage** của chính sách.

## 22. Callback, event bus và reentrancy bất ngờ

Một advice có thể công bố event lên event bus. Listener có thể gọi lại business service được aspect quan sát:

```text
TraceAdvice
  -> EventBus.publish()
      -> Listener.onEvent()
          -> BusinessService.execute()
              -> TraceAdvice
```

Nếu listener đồng bộ, call stack vẫn nằm trong advice emission. Nếu event bus chạy bất đồng bộ, listener có thể chạy sau khi advice ban đầu kết thúc hoặc trên thread khác.

Có hai vấn đề:

1. Bỏ mọi lời gọi xuất phát trong control flow advice có thể làm **mất business event hợp lệ** ở listener đồng bộ.
2. Dựa hoàn toàn vào ThreadLocal guard có thể **không chặn** listener bất đồng bộ, vì guard không được mang sang thread khác.

Đừng tự động coi cả hai là lỗi. Cần quyết định **event handler có là operation độc lập cần audit hay không**, và đặt instrumentation ở boundary phù hợp.

### 22.1. Event loop cần đường xử lý không quay lại publisher vô hạn

Nếu sự kiện audit tạo ra một sự kiện audit khác, hệ thống có thể sinh event storm ngay cả khi không có recursion trên một call stack duy nhất. Một guard chỉ dựa trên `cflow` không tự chặn được vòng lặp **bất đồng bộ qua queue**.

Có thể cần event type/source marker, idempotency key, deduplication, giới hạn attempts, cấu trúc luồng dữ liệu không chu trình hoặc chính sách sink riêng.

## 23. Trong code có nhiều `proceed` hoặc retry

Một around advice có thể gọi continuation hơn một lần nếu engine cho phép:

```text
RetryAround
  -> proceed attempt 1
  -> proceed attempt 2
```

Một trace aspect bên trong có thể chạy ở cả hai attempts. Nếu guard được bật quá lâu ở RetryAround, lần 2 có thể bị bỏ instrumentation. Nếu guard chỉ ở trace emission, mỗi attempt vẫn có trace riêng.

Phân biệt các sự kiện:

- **Logical request**: thao tác mà caller muốn thực hiện.
- **Attempt**: một lần chạy continuation.
- **Advice invocation**: một lần vào advice.
- **Business execution**: một lần thân method thực sự chạy.
- **Emission**: một lần gửi event phụ trợ.

Không dùng số log lines để khẳng định số lần business thành công. Một lần business có thể tạo nhiều event; một advice có thể không proceed; một retry có thể tạo thêm business executions.

### 23.1. Retry lỗi sinh sau side effect

Nếu business hoàn tất rồi after advice hoặc sink ném lỗi, một retry ngoài có thể thực hiện business thêm lần nữa. Loại trừ recursion không sửa được rủi ro retry này; cần kiểm soát loại lỗi được retry và idempotency của operation.

## 24. Exception và nguồn lỗi trong vòng instrumentation

Giả sử:

```text
Business throws E1
  -> ErrorAdvice.log(E1)
       -> Logger throws E2
```

Nếu handler không quản lý, caller có thể thấy **E2 thay E1**. Nhưng đối với một logging aspect **best effort**, hợp đồng có thể yêu cầu luôn truyền E1.

Các hướng xử lý:

- Bắt lỗi sink trong phạm vi chỉ xử lý logging.
- Không để việc ghi lỗi quay lại pointcut mà chính nó đang tạo.
- Khi cần chuyển đổi lỗi, lưu `cause` hoặc `suppressed` theo policy.
- Không tự ném `Throwable` mới trong cleanup guard nếu không cần.
- Đảm bảo guard được reset ngay cả khi `proceed` hoặc sink thất bại.
- Không nuốt `Error` hoặc exception thuộc miền nghiệp vụ ngoài chủ đích chỉ để giảm log.

### 24.1. `finally` không phải một event audit bền vững

`finally` giúp dọn state trong control flow bình thường của Java, nhưng không bảo đảm event đã được ghi bền vững hoặc tất cả tiến trình luôn chạy cleanup trong mọi tình huống VM/process bị dừng bất thường.

## 25. Constructor, static initializer và initialization side effects

Một số mô hình AOP có thể chọn constructor call/execution, class initialization hoặc các loại join point khác. Nếu aspect rộng chạy ngay trong quá trình khởi tạo hạ tầng logging, nó có thể tạo **bootstrap cycle**:

```text
Aspect.initialize()
  -> Logger.initialize()
       -> AdviceTriggeredDuringInitialization()
            -> Logger.initialize() ...
```

Những vấn đề bổ sung:

- Singleton/aspect instance chưa khởi tạo hoàn chỉnh.
- Helper dependency chưa sẵn sàng.
- Static initialization lock có thể ảnh hưởng liveness.
- Class loader và module có thể làm điều kiện chọn không như khi runtime ổn định.
- Logging trong constructor có thể gọi `toString`/getter trên object chưa hoàn tất init.

**Không áp dụng bừa exclusion dành cho method call vào constructor**: mỗi join-point kind có ngữ nghĩa riêng. Tốt hơn xác định bootstrap boundary và không cho helper instrumentation phụ thuộc ngược vào service chưa sẵn sàng.

## 26. Gỡ lỗi khi guard hoạt động quá mạnh

Các dấu hiệu:

- Không còn đệ quy, nhưng mất log của inner business method.
- `depth=1` hoặc `depth>0` dẫn đến skip mọi thứ.
- Request A làm request B không được instrument.
- Sau một lỗi sink, tất cả invocation sau trên thread pool mất trace.
- Một aspect bảo mật không chạy khi metrics aspect đang có hoạt động.
- Khi tách event bus thành async, số event thay đổi bất thường.

Quy trình:

1. Ghi rõ **join point phải được advice**.
2. Ghi rõ **join point cần loại**.
3. Phân biệt static exclusion với runtime guard.
4. Rà soát scope đặt guard: chỉ emission hay toàn continuation?
5. Kiểm tra guard có được reset khi exception không.
6. Kiểm tra state được lưu per thread, per invocation hay global.
7. Kiểm tra callback cùng thread và callback async.
8. Kiểm tra thứ tự aspect và việc một advice khác gọi helper.
9. Dùng event collector đếm **business / advised / excluded** riêng.
10. Chạy negative test và positive test sau mỗi thay đổi pointcut.

Đừng coi số log giảm là bằng chứng đã sửa đúng nếu số event cần thiết cũng giảm.

## 27. Gỡ lỗi khi exclusion quá yếu

Các dấu hiệu:

- Stack trace có mẫu `Advice → Logger → Advice → Logger` lặp.
- Tăng CPU/memory dù business rất ít việc.
- Một request tạo hàng trăm event giống nhau.
- Pointcut chỉ loại `within(Aspect)` nhưng helper execution trong class khác vẫn khớp.
- Cơ chế guard đang false khi helper không nằm trong scope guard dự kiến.
- Vòng async tiếp diễn sau khi call stack ban đầu đã kết thúc.

Quy trình:

1. Xác định join-point kind trong stack: call hay execution.
2. Xác định **lexical declaring type** ở điểm bị advice chọn.
3. Vẽ đường từ advice tới helper và quay lại.
4. Rà soát liệu `!within` có loại đúng **vị trí code** chứ không phải chỉ **type receiver**.
5. Xác định control flow root nên dùng: own advice execution hay emission helper.
6. Kiểm tra `cflow`/`cflowbelow` và root inclusion theo ngữ nghĩa engine.
7. Chọn exclusion tĩnh chính xác nếu có thể.
8. Thêm guard động hẹp khi cần, với rollback state qua `finally`.
9. Không thử lỗi bằng vòng lặp vô hạn thật trong production.
10. Kiểm thử regression khi nâng cấp/đổi helper.

## 28. Ma trận kiểm thử bắt buộc

| Test | Mong đợi |
| --- | --- |
| Caller gọi business qua interception boundary | Advice chạy đúng số lần |
| Advice tự gọi logger/helper bị loại | Không kích hoạt advice của chính nó ngoài ý định |
| Helper được gọi trực tiếp từ business | Có/không được instrument đúng theo hợp đồng |
| Business outer gọi inner qua proxy | Inner vẫn được advice nếu nằm trong scope |
| Raw target gọi nội bộ `this.inner` | Phản ánh đúng giới hạn cơ chế proxy |
| Helper ở class khác | Lexical exclusion không bị hiểu nhầm là dynamic exclusion |
| Advice gọi helper qua nhiều tầng | Control-flow guard/exclusion chặn đúng chu trình |
| Aspect A gọi helper kích hoạt Aspect B | Không tạo vòng A → B → A ngoài ý muốn |
| Sink ném exception | Guard được dọn, primary error xử lý theo policy |
| Business ném exception | Không rò depth hoặc context |
| Invocation kế tiếp trên cùng thread | Guard trở về trạng thái bình thường |
| Hai thread đồng thời | Không trộn guard; không bỏ audit thread khác |
| Retry gọi proceed nhiều lần | Trace theo request/attempt đúng yêu cầu |
| Callback đồng bộ | Không loại business event hợp lệ nếu policy yêu cầu |
| Callback bất đồng bộ | Không phụ thuộc sai vào cùng call stack/thread |
| Init/constructor | Không tạo bootstrap cycle |
| Pointcut đổi khi refactor | Positive/negative tests phát hiện coverage đổi |
| Method overload/helper mới | Không loại nhầm method nghiệp vụ do tên giống |
| Logging args/toString | Không đệ quy, không lộ dữ liệu nhạy cảm |
| Excessive instrumentation | Event volume và overhead nằm trong phạm vi đã chấp nhận |

### 28.1. Kiểm thử điều cần loại và điều phải giữ

Để bảo đảm bài toán được giải quyết, test phải có **hai phía**:

- **Negative test:** join point của sink/helper nội bộ không tạo thêm advice.
- **Positive test:** nested business join point phù hợp vẫn tạo advice.

Chỉ test “không bị StackOverflowError” là chưa đủ; một guard tắt toàn bộ aspect cũng có thể làm test ấy PASS.

### 28.2. Không dựa vào timestamp làm định danh invocation

Trong logging concurrent, timestamp có thể trùng hoặc log xen kẽ. Nên có invocation ID, parent ID, attempt ID khi cần, và phân biệt emitter thread với task identity.

## 29. Các hiểu nhầm thường gặp

**“Loại `within(MyAspect)` luôn ngăn mọi self-interception.”** Không. Nó là điều kiện lexical; execution của helper class khác có thể vẫn khớp.

**“Loại code của aspect đồng nghĩa loại mọi method aspect từng gọi tới.”** Không. Phải xem call site, execution và control flow.

**“Điều kiện `!cflow(adviceexecution())` luôn là tốt nhất.”** Không. Nó có thể loại quá rộng, bao cả những điểm phát sinh từ các aspect khác; cần giới hạn nguồn và kiểm thử.

**“`cflow` và `cflowbelow` giống hệt.”** Không. Theo AspectJ, khác nhau về việc bao gồm root join point.

**“`call` và `execution` là cùng một join point.”** Không. Chúng đại diện cho các ranh giới khác nhau ở những công cụ có hỗ trợ cả hai.

**“Một guard `insideAspect` global đủ an toàn.”** Không. Nó gây race và có thể tắt advice ở các thread khác.

**“Bỏ mọi lời gọi nested sẽ hết lỗi.”** Có thể hết recursion nhưng cũng bỏ mất logging, security hoặc audit của business inner.

**“`synchronized` tự chặn recursion.”** Không. Java monitor cho phép reentry cùng thread.

**“ThreadLocal tự truyền qua mọi callback bất đồng bộ.”** Không. Phải xác định task context propagation.

**“Không thấy advice của inner thì inner không chạy.”** Sai khi target tự gọi qua `this` mà không đi qua proxy.

**“Aspect chỉ logging không ảnh hưởng kết quả.”** Không chắc nếu formatter/logger ném lỗi hoặc thay đổi state/timing.

**“Log nhiều lần chứng minh business chạy nhiều lần.”** Không. Cần đếm business invocation riêng advice events.

**“Exclusion ở một điểm đã đủ cho mọi phiên bản thư viện.”** Không. Call graph và helper class có thể thay khi refactor.

**“Một lời gọi helper là vô hại vì nó không thuộc domain logic.”** Chưa chắc. Nó có thể có side effect, khóa, I/O và trigger các aspect khác.

## 30. Bài tập có giải thích

### Bài 1: log chính log

Một advice chọn mọi method-call join point và gọi `System.out.println` trong body. Vì sao có thể xảy ra recursion?

**Đáp án:** Lời gọi `println` từ chính advice cũng thuộc tập điểm được chọn, khiến advice chạy lại rồi gọi `println` tiếp. Với DSL hỗ trợ lexical exclusion, có thể loại call site nằm trong aspect; hoặc thu hẹp tập call phù hợp.

### Bài 2: lexical exclusion không đủ

Aspect gọi `AuditSink.write`. Sink gọi `DatabaseClient.insert`. Pointcut chọn execution của `DatabaseClient.insert` và chỉ loại `!within(AuditAspect)`. Có chắc ngăn recursion không?

**Đáp án:** Không. Execution được khai báo trong DatabaseClient, nằm ngoài AuditAspect về lexical scope. Cần xem dynamic control flow hoặc exclusion chính xác của helper execution.

### Bài 3: business và advice cùng dùng helper

`SharedHelper.process` được cả business lẫn advice gọi. Muốn chỉ loại lời gọi do advice phát sinh nên chọn loại trừ nào?

**Đáp án:** Một điều kiện theo **nguồn control flow** hoặc guard đúng scope có thể phù hợp hơn loại toàn bộ helper. Cần xác định ngữ nghĩa engine và test cả hai đường.

### Bài 4: bỏ tất cả invocation có depth > 0

Outer và inner là hai operation được audit. Nếu guard bỏ mọi invocation nested, kết quả?

**Đáp án:** Advice của inner bị bỏ dù yêu cầu audit có thể đòi ghi nó. Chỉ loại đường phát event phụ trợ gây recursion.

### Bài 5: một lỗi sink để lại guard

Logger ném exception giữa lúc `emitting=true` và code không có `finally`. Request sau chạy trên cùng thread pool sẽ ra sao?

**Đáp án:** Guard còn kẹt, có thể làm request sau mất instrumentation. Cần cleanup theo scope trong `finally`.

### Bài 6: khác biệt `cflow`/`cflowbelow`

Với AspectJ, nếu pointcut `P` xác định một root join point, `cflowbelow(P)` có chọn chính root đó không?

**Đáp án:** Không. `cflow(P)` gồm root và descendants, còn `cflowbelow(P)` không gồm root.

### Bài 7: hai aspect

AuditAspect gọi SecurityService; SecurityAspect gọi AuditSink; AuditSink lại bị AuditAspect chọn. Có thể có recursion không?

**Đáp án:** Có thể tạo chu trình nhiều aspect. Cần vẽ dependency graph, bảo vệ sink và phân biệt operation nào thật sự phải audit.

### Bài 8: self-invocation bypass

Caller đi qua interface proxy để gọi target.outer; target gọi `this.inner()`. Có chắc inner nhận advice không?

**Đáp án:** Không với proxy delegation vào target riêng, vì lời gọi nội bộ không quay qua proxy. Điều này khác self-interception.

### Bài 9: async event bus

Advice ghi event vào queue; consumer ở thread khác xử lý và lại ghi event. Guard `ThreadLocal` của producer có chặn chu trình này không?

**Đáp án:** Không thể giả định. Chu trình có thể đi qua queue/task khác. Cần thiết kế event type/source marker, idempotency/deduplication hoặc cấu trúc luồng không tạo vòng.

### Bài 10: hoàn tất sau retry

Retry gọi business hai lần, nhưng logger ghi ba event. Có thể kết luận business chạy ba lần không?

**Đáp án:** Không. Số event không tương đương số business execution. Cần correlation request/attempt và counter cụ thể.

## 31. Checklist áp dụng trước khi bật aspect trong môi trường thực

- [ ] Xác định đúng **join-point kind** và weaving/proxy boundary.
- [ ] Viết rõ tập operation cần advice và tập phải loại.
- [ ] Vẽ call graph từ advice tới logger/metrics/audit/helper/formatter.
- [ ] Kiểm tra code tự aspect có tạo join point phù hợp hay không.
- [ ] Phân biệt lexical exclusion với control-flow exclusion.
- [ ] Với AspectJ, đối chiếu chính xác `within`/`withincode`/`adviceexecution`/`cflow`/`cflowbelow`.
- [ ] Không dựa vào cú pháp của một DSL khi engine không hỗ trợ.
- [ ] Ưu tiên pointcut nghiệp vụ hẹp trước wildcard bao mọi method.
- [ ] Exclusion của helper có căn cứ, không loại nhầm business method.
- [ ] Guard runtime chỉ bao emission/helper cần ngăn tái nhập.
- [ ] Không dùng cờ global để tắt advice của thread khác.
- [ ] Có cleanup bằng `finally`, bảo toàn scope lồng nhau.
- [ ] Dùng local frame/snapshot per invocation cho thông tin depth/timing.
- [ ] Không dùng depth để tự động bỏ mọi nested business calls.
- [ ] Kiểm thử cả self-interception và self-invocation bypass.
- [ ] Có test exception từ sink và từ business.
- [ ] Không làm mất lỗi gốc khi logger/formatter thất bại ngoài ý định.
- [ ] Không lấy `toString`, reflection hay serialization quá rộng để ghi log.
- [ ] Kiểm tra callback đồng bộ và event loop bất đồng bộ.
- [ ] Có chính sách riêng cho async context/correlation và guard propagation.
- [ ] Kiểm tra chu trình A → B → A giữa nhiều aspect.
- [ ] Kiểm tra constructor/static initialization nếu pointcut bao chúng.
- [ ] Có test số lần advice, số business invocation và số guard skip riêng.
- [ ] Có test concurrency và thread-pool reuse.
- [ ] Xác định retry attempts và idempotency trước khi kết hợp aspect.
- [ ] Theo dõi event volume, latency, memory và lỗi liên quan instrumentation.
- [ ] Có đường rollback/disable aspect khi xuất hiện event storm.
- [ ] Không coi "không còn stack overflow" là đủ nếu mất audit nghiệp vụ.

## 32. Tài liệu đối chiếu

Những tài liệu sau mô tả **ngữ nghĩa của một công cụ AOP cụ thể**, giúp hiểu rõ các mẫu lexical và control-flow exclusion. Khi dùng công cụ khác, cần đối chiếu tài liệu tương ứng:

- [Eclipse AspectJ — Pointcuts](https://eclipse.dev/aspectj/doc/released/progguide/semantics-pointcuts.html): giải thích `within`, `withincode`, `adviceexecution`, `cflow` và `cflowbelow`.
- [Eclipse AspectJ — Programming Guide](https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html): có ví dụ `!within` nhằm ngăn lời gọi trong advice tự tạo recursion.
- [Eclipse AspectJ — Debugging Pointcuts](https://eclipse.dev/aspectj/doc/latest/pdguide/pointcuts.html): cách kiểm từng phần pointcut, các lỗi về join-point kind, lexical scope và runtime constraints.
- [Java SE 17 — Proxy](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Proxy.html): JDK dynamic proxy, giao diện và invocation handler.
- [Java SE 17 — ThreadLocal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html): state theo thread và vòng đời cleanup.

## 33. Kết luận

**Tránh đệ quy do aspect tự can thiệp không chỉ là đặt một cờ "đang trong advice".** Cần nhận diện đúng **nguồn của join point**, **loại điểm được chọn**, **vị trí khai báo mã** và **control flow đang hoạt động**.

Loại trừ theo lexical code hữu ích cho call site hoặc join point nằm trong aspect. Nhưng khi advice gọi helper ở class khác, **execution join point của helper** có thể vẫn được chọn; khi đó cần một exclusion đúng helper hoặc một điều kiện dựa control flow. Các công cụ hỗ trợ `adviceexecution` và `cflow` có thể diễn đạt những ranh giới này trực tiếp; các mô hình proxy khác có thể cần handler guard.

Giải pháp chất lượng vừa **chặn được đường tự phát event quay lại advice**, vừa **giữ được nested business advice cần thiết**, dọn trạng thái khi lỗi, không làm mất audit bảo mật và có kiểm thử dương/âm chứng minh đúng coverage.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-control-flow-005">Ràng buộc dữ liệu giữa các điểm trong cùng ngữ cảnh</a>

<details>
<summary>Xem chi tiết</summary>

# Ràng buộc dữ liệu giữa các điểm trong cùng ngữ cảnh

## 1. Từ một join point tới nhiều join point có liên hệ

Trong **Aspect-Oriented Programming (AOP)**, một **join point** là điểm/sự kiện mà mô hình AOP cho phép can thiệp, chẳng hạn một lần gọi hoặc thực thi phương thức theo khả năng của công cụ. **Pointcut** chọn những join point phù hợp; **advice** mô tả hành vi thực thi tại các điểm đó.

Bình thường, advice xử lý dữ liệu **của một join point**: tên phương thức, receiver, arguments, return value hoặc exception. Nhưng nhiều yêu cầu xuyên suốt cần gắn dữ liệu thu được ở **điểm A** với hành vi ở những **điểm B, C, D** xảy ra sau đó trong **cùng một ngữ cảnh logic**.

Ví dụ:
- Khi bắt đầu xử lý một đơn hàng, lấy `orderId`.
- Khi gọi các bước `reserveInventory`, `chargePayment`, `publishEvent` bên dưới luồng đó, ghi trace/metric với **cùng orderId**.
- Nếu xử lý đơn hàng B được lồng bên trong đơn hàng A, các join point của B phải dùng ID B; sau khi B kết thúc, các join point tiếp theo của A phải dùng lại ID A.
- Khi xử lý lỗi hoặc trả về, state của flow phải được dọn để tác vụ khác không nhận nhầm ID.

Đây là chủ đề **ràng buộc dữ liệu giữa các điểm trong cùng ngữ cảnh**: dữ liệu ở các join point không còn được xem là những mẩu thông tin độc lập; chúng được kết nối bởi **scope**, **identity** và **lifetime** của một control flow.

> **Cốt lõi:** Muốn một advice ở B dùng đúng dữ liệu thu tại A, phải xác định **A và B thuộc cùng ngữ cảnh nào**, data được **bind** ở đâu, được **truyền hoặc tra cứu** bằng cách nào, gặp flow lồng nhau và bất đồng bộ ra sao, và **khi nào dữ liệu không còn hợp lệ**.

### Mục tiêu học tập

Sau bài học, bạn có thể:
1. Phân biệt binding **argument/receiver/return/exception tại một join point** với **binding xuyên các join point** trong cùng control flow.
2. Hiểu dynamic scope, control-flow pointcut, các khái niệm thường gặp như `cflow`, `cflowbelow` và per-control-flow association ở mức **khái niệm**, không áp cú pháp của một engine thành chuẩn chung.
3. Xác định **root join point**, **descendant join points**, **context identity**, **binding key**, **scope entry/exit**, **ngữ nghĩa nested/reentrant calls**.
4. Thiết kế việc lưu/tra dữ liệu bằng context object, stack, advice state có scope hoặc propagation tường minh.
5. Hiểu tại sao ThreadLocal chỉ tự gắn với **thread**, không tự đại diện **logical request/async operation**.
6. Kiểm soát lỗi context bị trộn khi recursion, retry, callback, thread pool, cancellation và short-circuit.
7. Không biến dữ liệu do join point cung cấp thành **nguồn xác thực quyền** khi không có trusted boundary.
8. Chạy ví dụ **Java 17** dùng JDK dynamic proxy và context stack để chứng minh binding `flowId` giữa các điểm `reserve`, `charge`, nested flow và cleanup sau lỗi.
9. Lập test matrix về scope, value binding, nested flow, error/async và concurrency.

Bài xét **AOP cơ bản và nâng cao nói chung**. Một số kỹ thuật ở đây được hiện thực bằng Java thuần để minh họa semantics; đó không phải tuyên bố mọi AOP engine có cùng pointcut DSL hoặc runtime support.

## 2. “Ràng buộc dữ liệu” có hai cấp độ

### 2.1. Cấp một: binding của chính join point

Giả sử một method:

~~~java
Receipt submit(String orderId, int quantity);
~~~

Một advice ở join point tương ứng có thể cần:
- `orderId` từ argument 0.
- `quantity` từ argument 1.
- Method/receiver.
- `Receipt` sau khi trả bình thường.
- Exception nếu method ném lỗi.

Ở cấp này, ràng buộc thường diễn ra tại **một occurrence**. Công cụ AOP có thể đưa variables ấy tới advice bằng typed parameters, context object hoặc reflection, tùy công cụ.

Không phải công cụ nào cũng cung cấp đầy đủ argument names, generic types, source locations hoặc return values tại cùng loại join point. **Đừng nhầm khả năng đọc metadata của tool với dữ liệu luôn sẵn có trong mọi execution model.**

### 2.2. Cấp hai: binding xuyên nhiều join points

Giả sử một workflow:

~~~text
Workflow.execute(orderId="ORD-7")  <-- root A
  |
  +-- Inventory.reserve(sku="SKU-2")   <-- descendant B
  |
  +-- Payment.charge(amount=100)       <-- descendant C
  |
  +-- Events.publish(type="PAID")      <-- descendant D
~~~

Tại B chỉ có `sku`, tại C chỉ có `amount`, tại D chỉ có `type`. Cả ba vẫn cần `orderId="ORD-7"` từ A.

Nếu mỗi advice chỉ đọc arguments của join point đang chạy, không có lý do B/C/D **tự biết** orderId. Cần một cơ chế liên kết:
- Bind `orderId` ở entry A.
- Lưu trong **context đang active**.
- Đảm bảo B/C/D chỉ được liên hệ tới A khi nằm trong scope phù hợp.
- Tra cứu dữ liệu theo identity/scope.
- Thoát scope và dọn khi A kết thúc.

Khi thiết kế, cần phân biệt **dữ liệu nghiệp vụ phải truyền qua API** với **metadata quan sát/correlation**. Nếu `orderId` quyết định tính đúng đắn của giao dịch, việc đưa nó tường minh vào domain API có thể đáng tin hơn một context ẩn.

## 3. Control flow và “cùng ngữ cảnh” là gì?

**Control flow** có thể hiểu là chuỗi/đồ thị các bước thực thi được liên kết bởi một lần bắt đầu operation. Trong synchronous call stack, điều này thường gần với “những lời gọi diễn ra trong khi method root đang active”. Nhưng ở callback/async systems, một **logical operation** có thể tiếp tục sau khi root method đã return hoặc qua thread khác.

Các khái niệm cần tách:

| Khái niệm | Ý nghĩa | Nguy cơ nhầm lẫn |
| --- | --- | --- |
| **Root join point** | Sự kiện bắt đầu scope muốn theo dõi. | Không phải mọi lời gọi root đều tạo context mới nếu policy khác. |
| **Descendant join point** | Điểm xảy ra trong flow liên quan tới root. | Không nhất thiết đồng nghĩa cùng thread trong hệ async. |
| **Dynamic scope** | Context có hiệu lực trong khoảng execution nhất định. | Không phải lexical scope của source code. |
| **Flow identity** | ID xác định một logical operation. | Một thread xử lý nhiều operations nối tiếp. |
| **Context frame** | Data gắn với một lần vào flow, gồm ID và metadata. | Cần tách biệt khi nested/reentrant. |
| **Association rule** | Điều kiện B thuộc flow A. | Không tự suy từ tên method hoặc class. |
| **Lifetime** | Khi entry tạo, exit cleanup, task completion. | Method return khác async completion. |
| **Propagation** | Cách context chuyển qua calls/threads/queues. | `ThreadLocal` không tự vượt async boundary. |

### 3.1. Dynamic scope khác lexical scope

**Lexical scope** liên quan vị trí viết code, block, variable ở source. **Dynamic scope** liên quan flow **thực tế đang chạy** tại runtime.

Một helper method được khai báo trong class độc lập vẫn có thể chạy bên trong flow A và dùng correlation của A. Ngược lại, một method được gọi tên giống nhau nhưng chạy bên ngoài A **không được tự nhận** context của A.

### 3.2. Cùng thread chưa đủ chứng minh cùng flow

Một worker thread xử lý request A, sau đó tái sử dụng cho request B. Nếu context A không được dọn, B có thể nhận nhầm orderId của A. Đây là lỗi **context bleed**. Vì vậy không thể định nghĩa flow là “mọi thứ xảy ra trên cùng thread mãi mãi”.

### 3.3. Cùng flow có thể vượt qua nhiều threads

Một operation thực hiện `submitAsync` có thể tạo task ở executor và callback trên worker khác. Logical flow có thể giữ nguyên ID qua các chặng, nhưng ThreadLocal của caller không được tự động truyền tới worker. **Propagate context theo task** mới duy trì đúng binding.

### 3.4. Đường gọi không qua proxy có thể làm mất binding tại các điểm cần quan sát

Nếu engine chỉ chặn proxy invocation, target tự gọi `this.step()` có thể không tạo event cho handler. Dù context vẫn active, advice tương ứng **không chạy** tại step ấy. Phải tách **scope tồn tại** với **join point thực sự được intercept**.

## 4. Các kiểu control-flow pointcut và ràng buộc theo ngữ cảnh

Một số mô hình AOP nâng cao dùng khái niệm **control-flow pointcut**:
- Chọn những điểm xảy ra **trong control flow** của một điểm root.
- Trong một số ngôn ngữ/công cụ, có cách phân biệt **gồm cả root** và **chỉ các điểm bên dưới root**.
- Có thể bind dữ liệu từ root vào advice áp dụng cho những join points xuất hiện bên dưới.
- Một số mô hình có instantiation scope **per control flow** để các occurrences liên quan dùng cùng aspect state.

Những tên được gặp trong tài liệu AOP là `cflow` và `cflowbelow`. **Chúng được nêu như khái niệm**, không phải cú pháp chung mọi công cụ AOP đều hỗ trợ. Ngữ nghĩa chính xác về root, call/execution join points, data binding, nested flows, precedence và tối ưu hóa phụ thuộc engine.

### 4.1. Vì sao phân biệt gồm root và chỉ descendants?

Giả sử root là `execute(orderId)`:
- Nếu ràng buộc scope gồm root, advice có thể áp dụng ngay tại `execute`.
- Nếu chỉ áp dụng **bên dưới root**, advice được chọn tại `reserve`, `charge`, v.v., nhưng không chọn root theo chính điều kiện control-flow đó.

Khác biệt này quan trọng để tránh ghi metric root hai lần hoặc chạy policy ở điểm root khi chỉ định áp vào các bước con.

### 4.2. Join point kind vẫn quyết định data binding

Một **method-call join point** có thể thấy caller/receiver theo mô hình ở call site. Một **method-execution join point** xảy ra khi method body bắt đầu, có thể quan sát target receiver và arguments tại execution. Các engine có loại join point và semantics `this`/`target` khác nhau.

Không mặc định:
- `this` ở call point luôn là object target.
- Arguments của root có sẵn với mọi descendant bằng magic.
- Return value của root có sẵn trước khi root kết thúc.
- Một pointcut được match bằng method name đủ để suy flow identity.

### 4.3. Dữ liệu tại root có thể được giữ trong aspect state

Mô hình khái niệm:

~~~text
Root enter (orderId)
    |
    +--> create FlowContext(orderId, id, startTime)
            |
            +--> Descendant B advice: currentContext.orderId
            +--> Descendant C advice: currentContext.orderId
            +--> Descendant D advice: currentContext.orderId
    |
Root exit (normal / throw)
    |
    +--> pop/close FlowContext
~~~

Nếu context object giữ mutable fields, phải xác định nó dùng trong một thread, nhiều threads hay nhiều nested calls. Không chọn shared singleton field `currentOrderId` để biểu diễn dữ liệu của mọi flows đồng thời.

## 5. Dữ liệu nào nên bind giữa các join point?

Không phải cứ nhận được dữ liệu là nên truyền xuyên toàn bộ flow.

| Loại dữ liệu | Ví dụ | Lưu ý |
| --- | --- | --- |
| **Correlation ID** | `operationId` hoặc `traceId`. | Có thể truyền qua boundaries; giới hạn độ dài và format. |
| **Định danh nghiệp vụ** | `orderId`, `customerId`. | Không thay thế validation/authorization. |
| **Chính sách đã xác thực** | Tenant ID được trusted entrypoint xác định. | Phải bảo toàn trust boundary, chống spoofing. |
| **Timestamp** | `startedNanos`, deadline. | Monotonic time cho duration trong cùng JVM. |
| **Attempt number** | Retry attempt hiện tại. | Phân biệt per-attempt với per-logical-operation. |
| **Kết quả của bước trước** | Status từ reserve để annotate charge. | Nếu ảnh hưởng correctness, ưu tiên API/domain dataflow rõ. |
| **Error context** | Failure type, stage name. | Tránh giữ Throwable/stack/args lâu. |
| **Resource handle** | Transaction/lock/stream. | Cần owner, scope và concurrency contract rất chặt. |
| **Baggage/metadata** | Locale, trace tags. | Bounded size, không lộ secrets. |

### 5.1. Correlation khác quyền truy cập

Nếu một caller có thể tự chọn `traceId="admin"`, ID đó không chứng minh caller là admin. Tương tự `orderId` trong log không chứng minh được phép đọc/ghi order đó.

Một aspect binding dữ liệu để observability **không tự tạo security context đáng tin**. Principal/tenant phải đến từ authentication/trusted boundary, được xác thực và propagate qua những điểm đã kiểm soát.

### 5.2. Đừng đặt bí mật vào context toàn cục

Nếu context chứa access token, raw payment details hoặc object request lớn, nó có thể được ghi log, capture bởi callback hoặc giữ trong registry lâu hơn cần thiết. Dùng **ID an toàn, immutable snapshot tối thiểu, masking và TTL**.

### 5.3. Dữ liệu bất biến là lựa chọn mặc định tốt

Một context kiểu `record FlowId(String value)` hoặc record lưu correlation ID/tenant đã xác thực giúp giảm race. State cần tăng đếm hoặc thay đổi ở các join point nên có owner và synchronization rõ. Không biến cả context thành mutable `Map<String,Object>` không kiểm soát nếu có thể thiết kế schema typed.

## 6. Mô hình ràng buộc: capture → associate → read → close

Một quy trình đáng tin có bốn giai đoạn:

1. **Capture tại A:** đọc dữ liệu từ occurrence root, validate dạng và xác định flow identity.
2. **Associate:** tạo một context frame hoặc chọn context đang có theo chính sách nested/reentrancy.
3. **Read tại B/C/D:** xác minh join point thuộc đúng scope, tra đúng context và dùng dữ liệu mà không mutate bừa bãi.
4. **Close:** khi root scope kết thúc theo semantics đã công bố, pop/restore context và dọn resource, kể cả khi error.

Một bước bổ sung thường cần với async là **propagate** context snapshot từ task cha sang task con có chủ đích, rồi close/restore tại mỗi worker boundary.

### 6.1. Validation ở root

Nếu root nhận `orderId=null`, cần quyết định:
- Từ chối operation trước khi mở context?
- Sinh internal correlation ID riêng nhưng vẫn giữ domain ID nullable?
- Trả failure rõ ràng?

Không tự chuyển `null` thành `"unknown"` rồi để policy nghiệp vụ dùng nó như orderId hợp lệ.

### 6.2. Association key

Không dùng ID chuỗi đơn thuần làm bằng chứng “hai operations là một flow”: hai invocations có thể cùng orderId nhưng là hai attempts độc lập. Thường cần một **flow occurrence ID** khác `orderId` domain.

### 6.3. Không đọc context ngoài scope

Một advice tại descendant có thể:
- **Reject** nếu không có context (khi binding là bắt buộc).
- **Skip instrumentation** nếu context là optional observability.
- Tạo context mới nếu contract cho phép (nhưng phải tránh biến mọi helper thành root).

Chính sách phải rõ, test được, không âm thầm mượn context từ request trước.

## 7. Nested flows, recursion và stack ngữ cảnh

Một flow A có thể mở flow B trước khi A kết thúc:

~~~text
A begin / context A
  A.step1             -> A
  B begin / context B
    B.step1           -> B
  B end / pop B
  A.step2             -> A (phải restore)
A end / pop A
~~~

Nếu dùng một field global `currentId`, B ghi đè A. Sau khi B kết thúc, A.step2 có thể bị gán nhãn B. Nếu dùng một `ThreadLocal<String>` nhưng không lưu giá trị trước khi vào B, lỗi tương tự vẫn xảy ra ở **cùng thread**.

### 7.1. Stack thay vì single current slot

Một **stack các frames** cho phép:
- Push context mới khi vào nested scope.
- Đọc top frame cho chính sách “innermost wins”.
- Pop khi exit và khôi phục parent.
- Đọc ancestor khi chính sách cần quan sát cả flow ngoài.
- Dọn ThreadLocal khi stack trống.

Không phải mọi use case cần stack: nếu API cấm nested operation, có thể reject ngay entry B. Nhưng một mô hình hỗ trợ nesting phải **quy định rõ B độc lập hay kế thừa A**.

### 7.2. Innermost vs ancestor semantics

Khi B nằm trong A:
- **Innermost binding:** B.step dùng ID B, A.step sau B dùng A.
- **Ancestor binding:** một cross-cutting observer của A có thể còn muốn quan sát B.step như hoạt động thuộc flow A ở cấp cao.
- **All active flows:** advice có thể cần biết cả A và B cho tracing span tree.

Đó là các policy khác nhau. Không chỉ có một “context hiện hành” chung đúng cho mọi concern. Có thể lưu một parent pointer hoặc một scoped stack snapshot tùy nhu cầu.

### 7.3. Recursion

Nếu `process(id)` gọi lại `process(id)` đệ quy, hai occurrences có thể cùng **domain ID** nhưng là hai frames khác nhau. Bám theo string `id` sẽ không phân biệt được các mức recursion. Một scope design có thể:
- Tạo frame mới cho mỗi occurrence.
- Dùng cùng logical frame khi recursion được định nghĩa là một operation.
- Gắn child frame chứa parent ID và depth.

Phải nói rõ trước khi viết advice.

### 7.4. Exception phải restore parent context

Nếu B ném exception, `finally` của B vẫn cần pop B. Nếu A bắt lỗi của B và tiếp tục, A.step2 phải lấy context A, không phải B. Nếu A cũng ném lỗi ra ngoài, `finally` của A phải pop A. Đây là lý do các scope APIs thường cần một biên `try/finally`.

## 8. Reentrancy, retry và scope association

### 8.1. Một advice có thể kích hoạt join point khác

Ví dụ advice ghi metric bằng cách gọi `MetricsExporter.record()`, và pointcut lại chọn chính `record`. Một context stack đúng vẫn không tự ngăn **vòng đệ quy instrumentation**. Cần loại trừ các điểm instrumentation hoặc có guard scoped với cleanup.

### 8.2. Retry tạo những điểm lặp lại trong một flow

Một operation `charge` có thể được retry ba lần:
- **Logical flow ID** vẫn là một.
- **Attempt ID** phải khác nhau nếu muốn đo từng lượt.
- Data của attempt trước không được ghi đè dữ liệu logical flow sai phạm vi.

Một thiết kế hay dùng hai tầng:
- Flow context: `operationId`, `tenantId`, `deadline`.
- Attempt context: `attemptNumber`, `startedNanos`, `failureType`.

Nếu aspect metric nằm trong retry, nó có thể chạy mỗi attempt; nếu ngoài retry, có thể đo một logical operation. Ordering phải tường minh, không suy từ tên class.

### 8.3. Không gọi continuation nhiều lần nếu engine không hỗ trợ

Một số around-advice models cho phép `proceed` nhiều lần, số khác có hạn chế. Đừng mặc định mọi AOP engine triển khai retry bằng cách gọi lại continuation như một function tự do.

Ngay cả khi kỹ thuật cho phép, business operation phải có idempotency/attempt semantics phù hợp.

## 9. Java 17 runnable: bind flow ID giữa reserve và charge

Ví dụ sau cố ý dùng **Java thuần** để minh họa cơ chế control-flow data binding, không dựa vào một AOP engine cụ thể:

- `FlowScope.run(flowId, action)` đại diện **điểm root mở ngữ cảnh**.
- `FlowScope` dùng `ThreadLocal<Deque<Frame>>` quản lý stack context của synchronous flow.
- `Steps` là interface cho hai bước `reserve` và `charge`.
- JDK dynamic proxy chặn mỗi lời gọi qua proxy, đọc **top context frame**, tăng step count và ghi log gồm `flowId`.
- `run("A")` mở flow A, sau đó `run("B")` lồng trong A: B dùng ID B, quay lại A thì A dùng ID A.
- Flow C ném lỗi từ target: `finally` vẫn pop context.
- Lời gọi tới proxy sau khi không còn flow bị từ chối thay vì mượn context rác.

Tạo tệp **`FlowDataBindingDemo.java`**:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Deque;

public class FlowDataBindingDemo {
    interface Steps {
        void reserve(String sku);
        void charge(int amount);
    }

    static final class Target implements Steps {
        @Override
        public void reserve(String sku) {
            System.out.println("TARGET reserve " + sku);
            if ("FAIL".equals(sku)) {
                throw new IllegalArgumentException("reservation failed");
            }
        }

        @Override
        public void charge(int amount) {
            System.out.println("TARGET charge " + amount);
        }
    }

    static final class FlowScope {
        static final class Frame {
            final String flowId;
            int steps;

            Frame(String flowId) {
                this.flowId = flowId;
            }
        }

        private static final ThreadLocal<Deque<Frame>> ACTIVE =
                new ThreadLocal<>();

        static Frame requireCurrent() {
            Deque<Frame> stack = ACTIVE.get();
            if (stack == null || stack.isEmpty()) {
                throw new IllegalStateException("no active flow");
            }
            return stack.peek();
        }

        static void run(String flowId, Runnable action) {
            if (flowId == null || flowId.isBlank()) {
                throw new IllegalArgumentException("invalid flow ID");
            }
            Deque<Frame> stack = ACTIVE.get();
            if (stack == null) {
                stack = new ArrayDeque<>();
                ACTIVE.set(stack);
            }

            Frame frame = new Frame(flowId);
            stack.push(frame);
            System.out.println("BEGIN " + flowId);
            try {
                action.run();
            } finally {
                Frame popped = stack.pop();
                System.out.println("END " + popped.flowId
                        + " steps=" + popped.steps);
                if (stack.isEmpty()) {
                    ACTIVE.remove();
                }
            }
        }
    }

    static Steps observed(Steps target) {
        return (Steps) Proxy.newProxyInstance(
                Steps.class.getClassLoader(),
                new Class<?>[] { Steps.class },
                (proxy, method, args) -> {
                    FlowScope.Frame frame = FlowScope.requireCurrent();
                    int step = ++frame.steps;
                    System.out.println("BIND " + frame.flowId + " "
                            + method.getName() + " arg=" + args[0]
                            + " step=" + step);
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException wrapped) {
                        throw wrapped.getCause();
                    }
                }
        );
    }

    public static void main(String[] args) {
        Steps steps = observed(new Target());

        FlowScope.run("A", () -> {
            steps.reserve("SKU-A");
            FlowScope.run("B", () -> steps.charge(20));
            steps.charge(10);
        });

        try {
            FlowScope.run("C", () -> steps.reserve("FAIL"));
        } catch (IllegalArgumentException failure) {
            System.out.println("FLOW failed " + failure.getMessage());
        }

        try {
            steps.charge(5);
        } catch (IllegalStateException failure) {
            System.out.println("OUTSIDE blocked " + failure.getMessage());
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac FlowDataBindingDemo.java
java FlowDataBindingDemo
~~~

**Stdout kỳ vọng, chính xác theo thứ tự của ví dụ tuần tự:**

~~~text
BEGIN A
BIND A reserve arg=SKU-A step=1
TARGET reserve SKU-A
BEGIN B
BIND B charge arg=20 step=1
TARGET charge 20
END B steps=1
BIND A charge arg=10 step=2
TARGET charge 10
END A steps=2
BEGIN C
BIND C reserve arg=FAIL step=1
TARGET reserve FAIL
END C steps=1
FLOW failed reservation failed
OUTSIDE blocked no active flow
~~~

### 9.1. Điều gì được bind tại root A?

`FlowScope.run("A")` tạo `Frame(flowId="A", steps=0)` và push vào stack của thread. Root là **API tạo scope tường minh** trong demo. Một engine control-flow AOP có thể làm vai trò tương tự bằng advice ở root join point nếu hỗ trợ.

Ở `steps.reserve("SKU-A")`, target chỉ nhận SKU-A; proxy advice tra `Frame` và bind thêm ID A cho log. Không có chuyện `reserve` tự nhận parameter `flowId` từ chữ ký của nó.

### 9.2. Vì sao nested B không làm A mất ID?

Khi vào `FlowScope.run("B")`, stack lúc đó có A. Push B khiến B thành top frame; `charge(20)` dùng B và step của B tăng lên 1. Khi B exit, `finally` pop B; A trở lại top, nên `charge(10)` dùng A và step A tăng từ 1 lên 2.

**Giá trị quan trọng không chỉ là ID**, mà là một **context occurrence** với lifecycle độc lập.

### 9.3. Vì sao error không làm context C rò ra ngoài?

`reserve("FAIL")` thực thi target rồi ném `IllegalArgumentException`. Reflective invocation bọc lỗi trong `InvocationTargetException`, proxy handler gỡ cause và ném lại. `FlowScope.run` vẫn vào `finally`, pop C và `ACTIVE.remove()` khi stack trống. Caller in `FLOW failed reservation failed`.

Nếu thiếu cleanup, lời gọi sau flow C có thể nhận nhầm ID C; ví dụ cho thấy sau khi flow đã đóng, proxy từ chối `charge(5)` với `no active flow`.

### 9.4. Step count là state theo frame, không phải global counter

A có hai steps, B có một, C có một. Biến `steps` thuộc **Frame** của flow hiện hành, không nằm trong shared static field `currentSteps`. Vì thế một nested flow không ghi đè counter của parent.

**Giới hạn:** Frame mutable chỉ an toàn dưới giả định code demo chạy synchronous trên một thread. Nếu cùng một logical frame được chia cho nhiều threads, `++frame.steps` không thread-safe; cần synchronization hoặc aggregate phù hợp.

### 9.5. Demo minh họa điều gì và không minh họa điều gì?

**Có thể kết luận:**
- Một root scope có thể cung cấp dữ liệu cho advice của các join points khác.
- Stack context hỗ trợ nested flows và restore parent.
- `finally` cần cho cleanup trên lỗi.
- Proxy có thể enrich log bằng flow ID dù target method không nhận ID ấy.
- Không có active flow thì policy có thể reject thay vì dùng ID cũ.

**Không thể kết luận:**
- Mọi AOP engine có pointcut cú pháp `cflow`.
- ThreadLocal tự propagate qua executor, HTTP client hoặc message broker.
- Direct target call và self-invocation luôn được proxy handler nhìn thấy.
- Flow ID là principal/security authorization token.
- Một mutable Frame có thể chia sẻ vô điều kiện giữa nhiều threads.
- Context binding tự giải quyết transaction/rollback của target.
- JDK proxy trong ví dụ tự động weave method bodies.

## 10. Những cách mang context từ A tới B

### 10.1. Truyền tường minh bằng parameters

~~~java
// Mã minh họa, không phải chương trình độc lập.
void reserve(String orderId, String sku);
void charge(String orderId, int amount);
~~~

Ưu điểm:
- Data dependency hiển thị rõ trong API.
- Compiler và tests thấy những arguments bắt buộc.
- Dễ chuyển qua threads, queues, remote boundaries bằng serialization có kiểm soát.
- Tránh hidden ambient state.

Đánh đổi:
- Nhiều methods phải chuyển parameter mà bản thân chúng không dùng nghiệp vụ.
- Một số metadata observability có thể làm API domain dài không cần thiết.

**Khuyến nghị:** Với dữ liệu quyết định correctness hoặc security, thường nên ưu tiên API/message contract tường minh hoặc trusted context service có hợp đồng rõ, không dựa độc nhất vào interception ẩn.

### 10.2. Per-control-flow aspect state

Một engine hỗ trợ instantiation/association theo flow có thể giữ `orderId` trong một aspect object dành riêng cho flow occurrence. Descendant advice tra aspect instance tương ứng.

Ưu điểm: mô hình hóa trực tiếp “một context cho mỗi flow”. Nhưng:
- Khả năng hỗ trợ tùy engine.
- Need cleanup/association identity rõ.
- Nested flows có thể tạo nhiều instances.
- Async propagation không tự miễn phí.
- Memory retention nếu lifecycle owner giữ state ngoài dự kiến.

### 10.3. ThreadLocal scope stack

Như demo: tốt cho synchronous stack-scoped context trong cùng thread. Cần:
- `try/finally` ở cả success/failure.
- Push/pop hoặc save/restore đúng khi nested.
- Remove khi root exit, tránh thread pool bleed.
- Không dùng như storage lâu dài cho task pending.
- Không dùng ID từ untrusted input làm principal.

### 10.4. Scoped contextual API

Một số môi trường có abstraction scoped context hoặc context propagation riêng. Ý tưởng là tạo scope cho một callback/task rồi khôi phục previous scope, không cần mỗi method nhận thêm argument. **Khả năng và lifetime cụ thể tùy nền tảng**; đừng suy rằng context của synchronous lexical scope sẽ tự chạy theo bất kỳ async callback nào.

### 10.5. Message headers / trace propagation

Qua queue hoặc RPC boundary, có thể truyền correlation ID và một tập metadata nhỏ qua headers/payload. Receiver phải:
- Validate format và trust level.
- Quyết định accept/create flow ID.
- Không truyền secrets tùy tiện.
- Đặt scope trong consumer execution.
- Cleanup sau message handling.
- Cân nhắc retries và message redelivery.

Đó là propagation **bằng dữ liệu**, khác với `ThreadLocal` in-process.

## 11. Bất đồng bộ: một logical flow có thể không còn trên call stack

Tình huống:

~~~text
Thread T1: Root A enters
           creates future/task
           root method returns
Thread T2: task executes B
Thread T3: callback executes C
~~~

Nếu một control-flow pointcut chỉ định nghĩa bằng synchronous call stack, B/C có thể **không còn nằm trong dynamic scope** của root A tại thời điểm chạy. Muốn tiếp tục dùng cùng correlation, phải có **quy tắc nối logical task**.

### 11.1. Capture/restore per task

Mô hình:
1. Tại task creation, lấy immutable snapshot của ID/tenant/trace metadata đã được xác thực.
2. Truyền snapshot theo một task wrapper hoặc message.
3. Khi worker bắt đầu, mở context scope với snapshot.
4. Chạy task.
5. Trong `finally`, restore/remove context cũ của worker.
6. Nếu task spawn tasks khác, quyết định propagate hoặc tạo child span/flow.

Không sao chép nguyên mutable stack đang dùng ở thread cha rồi share giữa threads mà không phân tích concurrency. Dùng một **immutable parent snapshot** cộng child frames riêng, hoặc cơ chế thread-safe được thiết kế theo logical-flow semantics.

### 11.2. Context không đồng nghĩa ThreadLocal snapshot

`ThreadLocal` là cơ chế truy cập state từ **thread hiện hành**. Context snapshot là dữ liệu độc lập có thể truyền rõ. Nhầm hai thứ dẫn tới:
- Async callback không thấy dữ liệu.
- Worker reuse giữ dữ liệu cũ.
- Concurrent tasks cùng sửa một mutable frame.
- Cleanup trên thread khác không xóa ThreadLocal của thread gốc.

### 11.3. Return handle khác completion

Nếu root method trả `CompletionStage<T>`, **scope method invocation** kết thúc khi trả handle, nhưng **logical operation** có thể tiếp tục tới completion. Nếu muốn ghi duration tới task completion, một immutable snapshot và callback observer có thể sống tới khi stage terminal. Không giữ caller ThreadLocal mở vô thời hạn để chờ stage.

### 11.4. Cancellation, timeout và context cleanup

Một task bị cancel/timeout không có nghĩa remote side effect đã dừng. Nếu registry giữ flow states cho tasks pending, phải có owner, deadline, cleanup ở terminal events và giới hạn số entries. Timeout observer không tự bảo đảm context không bị giữ bởi một callback/worker khác còn reachable.

## 12. Thread pool, nested scope và tránh context bleed

Một thread pool có thể chạy A rồi B trên cùng worker:

~~~text
T1 starts A -> context=A
T1 ends A   -> context removed
T1 starts B -> context=B (không phải A)
~~~

Nếu advice chỉ set context mà không restore/remove, B có thể ghi log theo A. Đây không chỉ là thiếu chính xác telemetry mà có thể **rò dữ liệu tenant/user** nếu một policy lầm dùng context đó để ra quyết định.

### 12.1. Restore previous context, không chỉ remove vô điều kiện

Trong nested scope, B đóng xong thì A vẫn active. Nếu B luôn `ThreadLocal.remove()` mà không restore A, advice sau B trong A bị mất liên kết. Stack hoặc save-previous semantics là cần thiết.

### 12.2. Kiểm thử bằng single-thread executor

Để phát hiện bleed, dùng một single-thread executor và hai tasks nối tiếp:
- Task A mở context rồi cố ý ném lỗi.
- Task B kiểm tra lúc bắt đầu không thấy context A.
- Nếu B có context riêng, kiểm B nhận đúng ID B.

Dùng hai threads ngẫu nhiên có thể che lỗi vì task B không chạy cùng worker T1.

### 12.3. Context propagation phải có giới hạn

Không truyền toàn bộ request object, access token, Throwable và mutable object graphs chỉ để ghi correlation ID. Càng nhiều dữ liệu mang theo, càng dễ memory retention và privacy leakage.

## 13. Nhận diện join points cùng flow bằng những tiêu chí nào?

Đừng dựa độc nhất vào **method name** hoặc **object target**:
- Cùng method có thể được gọi bởi nhiều flows.
- Cùng target singleton phục vụ nhiều users đồng thời.
- Một flow có thể gọi nhiều targets.
- Một target có thể thuộc cả parent flow và child flow theo logic.
- Một operation ID có thể lặp qua retries/redelivery.
- Một thread có thể dùng lại cho nhiều operations.

Các tiêu chí thường cần phối hợp:
- **Flow occurrence identity** do root tạo.
- **Scope entry/exit** xác định lifetime.
- **Parent-child relationship** khi nesting hoặc async task fork.
- **Receiver/arguments** của join point hiện hành.
- **Pointcut matching** xác định loại event được can thiệp.
- **Trust boundary** cho fields có tính bảo mật.
- **Explicit propagation** qua async/network.

### 13.1. “Cùng ngữ cảnh” không nhất thiết mọi advice dùng chung mọi field

Tracing muốn parent/child span IDs; metrics muốn aggregate theo logical operation; authorization cần principal đã xác thực; transaction management cần resource ownership và propagation semantics riêng. Không nên xây một context object không kiểu với mọi field rồi cho mọi aspect mutate tùy ý.

### 13.2. Key collision và nested identity

Hai flows có cùng `orderId` không nên bị tự động nhập vào cùng state map. Nếu muốn association theo **logical operation**, dùng unique occurrence/correlation ID riêng; `orderId` là **thuộc tính domain**. Nếu muốn aggregate metrics theo orderId, đó là phép group có ý nghĩa khác và cần giới hạn cardinality.

## 14. Data binding với return và exception xảy ra ở những thời điểm khác nhau

Arguments và receiver của root thường có trước body. Return value chỉ xuất hiện khi method return bình thường; exception có thể xuất hiện khi throw. Descendant B xảy ra **trước khi root hoàn tất**, nên advice B không thể mặc định đọc return value cuối của A.

### 14.1. Binding dữ liệu sớm

Nếu B cần `orderId` từ root args, capture ở entry và lưu vào flow frame. Đừng chờ after-returning của root rồi hy vọng B ở trong body đã nhận được value đó.

### 14.2. Binding dữ liệu từ bước trước

Nếu `reserve` trả `reservationId` rồi `charge` cần ID ấy, có hai hướng:
- **Truyền tường minh** `reservationId` qua domain API.
- Gắn nó vào một flow context typed và có owner, nếu policy cho phép.

Vì đây là **dependency nghiệp vụ**, truyền tường minh thường có lợi cho correctness, transaction tests và khả năng chạy qua threads. Hidden context phù hợp hơn với metadata correlation hoặc concern xuyên suốt.

### 14.3. After-throwing và lỗi bị bắt bên trong

Nếu B ném lỗi nhưng root A bắt rồi return fallback, advice after-throwing của A có thể không nhìn thấy lỗi B. Muốn ghi attempt failure, phải observe tại B hoặc trong scope nhận exception trước khi nó bị catch.

Một flow context có thể giữ failure metadata, nhưng không được để một exception cũ khiến đoạn A tiếp tục thành công bị gắn “failed” không chính xác. Tách **attempt-level** và **final logical outcome**.

## 15. Bảo toàn kiểu dữ liệu và ý nghĩa binding

### 15.1. Định danh phải typed/validated

Một `Map<String,Object>` dùng chung cho `"id"` dễ nhầm orderId với traceId hay tenantId. Dùng typed context fields hoặc value objects để không vô tình ép kiểu/ghi đè.

### 15.2. Không chuyển absence thành success

Nếu không có context, trả string `"unknown"` có thể hợp với logger nhưng không được mang nghĩa ID nghiệp vụ đã được xác minh. Nếu ID bắt buộc, reject bằng lỗi rõ. Nếu optional, ghi nhận absence minh bạch.

### 15.3. Kết quả của advice không được thay đổi ngoài ý định

Một advice chỉ enrich log bằng flowId nên vẫn trả nguyên result của target, truyền exception gốc và không mutate arguments, trừ khi có chính sách transformation đã được công bố. Nếu advice short-circuit hoặc retry, số lần target chạy có thể thay đổi và phải được test.

### 15.4. Không biến exception thành giá trị giả

Một method trả `int` có thể trả `0` hợp lệ. Nếu advice mất context và tự return 0 thay vì báo lỗi, caller có thể tin rằng operation thành công với result 0. Binding failure khác business result.

### 15.5. Mutable context và dữ liệu đồng thời

Nếu nhiều threads cập nhật chung một counter trong flow context, `count++` không atomic. Local per-thread frames giúp tách state nhưng không tự tổng hợp đúng khi một logical flow phân nhánh. Cần hợp đồng merge và primitive đồng bộ phù hợp.

## 16. Ranh giới bảo mật và tính đáng tin cậy của context

### 16.1. Correlation metadata không phải authorization

`tenantId` gửi qua header từ client có thể giả mạo. Nếu một aspect trích tenantId từ header rồi cho tất cả descendant advice dùng nó như sự thật xác thực, ứng dụng có nguy cơ cross-tenant access.

Thiết kế đúng cần:
1. Xác thực tại trusted entrypoint.
2. Xác định principal/tenant được ủy quyền.
3. Tạo trusted context snapshot.
4. Chỉ propagate fields tối thiểu cần thiết.
5. Không cho untrusted data overwrite trusted fields ở descendant.
6. Kiểm tra invariant bảo mật tại boundary thích hợp, kể cả đường bypass proxy.

### 16.2. Không để aspect “thay” validation domain

Một log advice có `orderId` không chứng minh các bước `charge` hoặc `ship` được phép tác động tới order đó. Domain/service layer vẫn phải bảo đảm owner, trạng thái và transaction invariants.

### 16.3. Không log secrets

Khi bind arguments ở join point, dễ vô tình log token, password, card number hoặc payload nhạy cảm. Chọn fields có kiểm soát, masking và bounded sizes. Tránh biến context thành một bảng lưu toàn bộ invocation arguments/results.

### 16.4. Context injection từ external systems

Qua message/RPC, nếu cần chấp nhận trace headers từ caller, tách **external correlation** khỏi **internal authoritative identity**. Không phải trace ID hợp lệ về format là bằng chứng tính xác thực; trust policy phải độc lập với observability.

## 17. Nhiều aspects dùng chung ngữ cảnh: thiết kế owner và precedence

Giả sử có:
- Root binder tạo context.
- Trace advice đọc flowId.
- Metrics advice đọc attemptId.
- Security advice đọc trusted principal.
- Error advice ghi outcome.

Nếu các advice có thứ tự không rõ hoặc scope owner không được phân định, có thể xảy ra:
- Trace chạy trước khi binder tạo frame.
- Metrics đọc attemptId sau khi retry đã pop attempt context.
- Security đọc field untrusted bị overwritten.
- Error observer đóng scope trước khi outer advice đọc final outcome.
- Cleanup chạy hai lần khiến stack underflow.

### 17.1. Xác định lifecycle owner

Một thành phần nên có trách nhiệm mở/đóng root scope rõ ràng. Các descendants chủ yếu **đọc** hoặc tạo child context dưới sự điều phối có chính sách, không tùy tiện pop root frame.

### 17.2. Precedence theo yêu cầu thực tế

Nếu binder là advice bao ngoài descendants, nó cần tạo context **trước khi** descendants chạy và đóng **sau khi** chúng hoàn thành synchronous scope.

~~~text
FlowBinder.before
  -> Trace.before
     -> Step target
     <- Trace.after
FlowBinder.finally: close
~~~

Đây là sơ đồ ngữ nghĩa, không phải cú pháp ordering chung cho mọi AOP engine. Với async task, binder cần kế hoạch completion/propagation tách biệt.

### 17.3. Không dựa vào classpath/order tình cờ

Thứ tự đăng ký, classloader discovery hoặc tên class không phải hợp đồng precedence chung. Nếu engine cung cấp ordering primitives, khai tường minh và integration-test effective order.

## 18. Chi phí và giới hạn của control-flow matching

Chọn join points dựa trên call stack/dynamic flow có thể có thêm chi phí so với kiểm tra chữ ký method tĩnh:
- Theo dõi entry/exit root.
- Tra scope/current frame tại descendant.
- Quản lý nested stack hoặc association.
- Mang context qua async.
- Tạo labels và ghi metadata cho nhiều events.
- Kết hợp với những pointcuts khác khi advisor chain lớn.

Không đoán một con số overhead áp dụng mọi engine. Cần benchmark trên artifact/runtime thật và chọn đúng độ hạt:
- Chỉ bind những join points cần correlation.
- Chỉ mang fields tối thiểu.
- Tránh stack walk sâu hoặc reflection lặp lại khi có thể cache metadata an toàn.
- Tránh log theo mọi item nếu cardinality/throughput không phù hợp.
- Không capture Throwable/arguments lớn trong context lâu sống.

### 18.1. Khi nào explicit arguments tốt hơn?

Nếu `orderId` chỉ cần cho hai methods domain, truyền parameter có thể rõ, nhanh và dễ test hơn hidden flow context. Nếu concern xuyên suốt ở hàng chục services cần trace correlation mà business code không nên nhận tham số chỉ để log, scoped context có thể hợp lý.

Không chọn AOP chỉ vì muốn tránh viết parameter; chọn vì có một **cross-cutting boundary** và hợp đồng propagation rõ.

## 19. Ma trận kiểm thử data binding xuyên join points

| Ca | Thực thi | Kỳ vọng |
| --- | --- | --- |
| **Basic flow** | A → B → C. | B/C lấy đúng dữ liệu từ A. |
| **Outside flow** | Gọi B khi chưa có root. | Reject/skip theo policy; không dùng context cũ. |
| **Nested flow** | A → B(scope) → A. | B dùng B, A được restore sau B. |
| **Nested failure** | B ném lỗi, A catch tiếp tục. | B cleanup; A vẫn đúng ID. |
| **Recursive root** | Root gọi chính root. | Frames/occurrences không bị trộn. |
| **Concurrent flows** | Thread T1=A, T2=B. | A/B không lộ dữ liệu của nhau. |
| **Thread reuse** | Worker A rồi worker B. | B không thấy stale context A. |
| **Async child** | A spawn task trên worker khác. | Context được propagate có chủ đích, không phụ thuộc ThreadLocal tự động. |
| **Async failure** | Child hoàn tất exceptional. | Cleanup/metrics đúng terminal outcome. |
| **Cancellation** | Task bị cancel giữa chừng. | Lifecycle xử lý đúng, không giữ entry vô hạn. |
| **Retry** | B chạy nhiều attempts. | Logical ID ổn định, attempt ID tách. |
| **Short-circuit** | Advice cache return sớm. | Không giả định descendant đã chạy; scope vẫn đóng. |
| **Proxy bypass** | Target tự gọi helper. | Không suy handler nhận invocation chưa đi qua proxy. |
| **Trust** | External user giả tenantId. | Trusted identity không bị override. |
| **Data type** | Root có ID null/invalid. | Không tạo false-valid binding. |
| **Resource** | Context chứa handle. | Owner close đúng và không use-after-close. |
| **Leak** | Nhiều flows liên tiếp. | Stack/registry về trạng thái sạch. |
| **Multi-aspect** | Binder+Trace+Metrics. | Precedence đúng; không double-pop. |

### 19.1. Unit test cho stack

Nếu dùng mô hình giống Java demo, assert:
- Entry root có một top frame.
- Nested entry có hai frames, top là nested.
- Nested exit trả lại top parent.
- Exception vẫn giảm stack depth.
- Root exit làm `ThreadLocal` hết value.
- Ngoài scope, `requireCurrent()` ném đúng lỗi.

### 19.2. Integration test cho engine thật

Một demo JDK proxy không chứng minh control-flow pointcut trong engine khác hoạt động giống hệt. Với công cụ AOP thực, kiểm tra:
- Join-point kind được hỗ trợ.
- Phạm vi root và descendants.
- Include/exclude root.
- Nested scope semantics.
- Data binding từ arguments/root.
- Classloader/weaving/proxy coverage.
- Ordering và error handling.
- Concurrent và async behavior.

### 19.3. Kiểm thử với trạng thái bị lỗi có chủ đích

Inject exception ở:
- Ngay sau tạo frame.
- Trong descendant advice.
- Trong target.
- Trong nested root.
- Trong callback/completion observer.

Sau mỗi case, xác minh context vẫn được restore/close và không che lỗi gốc nếu policy quan sát là best effort.

## 20. Lỗi thường gặp

**“Các methods cùng nằm trong một class nghĩa là chúng dùng chung flow ID.”** Không. Cùng class không xác định dynamic operation.

**“Cùng thread tức cùng request.”** Không với thread pools và tái sử dụng threads.

**“ThreadLocal tự theo request qua CompletableFuture.”** Không. Phải propagate explicitly hoặc qua abstraction có hỗ trợ.

**“Một field `currentOrderId` trong shared aspect là đủ.”** Không với concurrent/nested invocations.

**“Nếu ThreadLocal được dùng thì nested calls tự restore parent.”** Không nếu chỉ set giá trị mới và remove mà không save/stack previous context.

**“Aspect instance per-target nghĩa là per-flow.”** Không. Một target có thể phục vụ nhiều flows đồng thời.

**“`cflow` và `cflowbelow` là cú pháp chuẩn mọi AOP engine.”** Không. Đây là những tên khái niệm được một số mô hình/công cụ sử dụng; cần xem engine cụ thể.

**“Data bind tại root tự động có sẵn tại mọi method descendant.”** Không. Cần association và propagation; event phải được interception.

**“Có correlation ID là đã xác thực principal.”** Không. Correlation và authorization là hai hợp đồng riêng.

**“Cùng orderId nghĩa là cùng invocation.”** Không. Retry/recursion và nhiều operations có thể cùng domain ID.

**“Root return nghĩa là mọi async child đã complete.”** Không. Method return-handle khác logical task completion.

**“Có context thì mọi bước phải được phép ghi vào nó.”** Không. Fields phải có owner, type và mutability policy.

**“Lưu toàn bộ request object giúp tiện debug.”** Có thể gây privacy leak và memory retention; giữ snapshot tối thiểu.

**“Bắt mọi exception rồi return default sẽ giúp cleanup.”** Không. Cleanup dùng `finally`; nuốt lỗi làm thay đổi contract.

**“Đặt một lock quanh toàn flow sẽ luôn an toàn hơn.”** Có thể serialize quá rộng, deadlock hoặc không bao async; chọn synchronization theo invariant.

**“Không thấy log của descendant nghĩa là descendant không chạy.”** Không. Có thể proxy bypass hoặc pointcut không khớp.

**“Chỉ cần test flow thành công.”** Không. Nested/failure/async/cancel/concurrency mới dễ phơi lộ lỗi context.

## 21. Bài tập vận dụng kèm đáp án

### Bài 1 — Root A và step B

`execute("O-1")` gọi `reserve("SKU-9")`. Step advice cần order ID. Nó có thể đọc từ argument của reserve không?

**Đáp án:** Không nếu `reserve` chỉ nhận SKU. Cần root bind ID vào context và descendant advice tra đúng scope, hoặc truyền orderId tường minh trong API.

### Bài 2 — Nested B

Flow A mở B rồi sau B tiếp tục A. Nếu giữ một ThreadLocal String và B overwrite mà không restore A, điều gì xảy ra?

**Đáp án:** Step tiếp theo của A có thể mang ID B hoặc mất context; dùng stack/save-and-restore trong finally.

### Bài 3 — Một orderId, hai attempts

Một charge bị retry, cùng orderId nhưng attempts khác nhau. Dữ liệu nào nên giữ và dữ liệu nào đổi?

**Đáp án:** Logical flow/order ID có thể giữ; attempt number/attempt ID, start time và failure state cần tách cho từng attempt.

### Bài 4 — Sau exception

Flow C ném lỗi từ target. Lời gọi không có root ngay sau đó vẫn thấy ID C. Đây là lỗi gì?

**Đáp án:** Scope cleanup không đúng, dẫn đến context bleed. `finally` phải pop/remove/restore.

### Bài 5 — Async worker

Root trên T1 tạo future chạy T2. T2 đọc `ThreadLocal` thấy null. Có phải ThreadLocal hỏng?

**Đáp án:** Không. ThreadLocal thuộc T1; phải explicit propagate immutable snapshot và mở scope trên T2.

### Bài 6 — Parent và child

Một observer của A cần nhìn mọi steps trong nested B, nhưng step business B phải lấy B. Có thể chỉ dùng top context không?

**Đáp án:** Không nếu cùng lúc cần cả hai viewpoints. Cần parent chain/active stack và quy tắc đọc riêng cho từng concern.

### Bài 7 — Tenant do caller gửi

Một user tự gửi header `tenantId="other"`. Advice gắn tenant này vào context rồi dùng để phân quyền. Sai ở đâu?

**Đáp án:** Trusted identity bị lấy từ dữ liệu chưa xác thực; phải xác thực/ủy quyền ở trusted boundary.

### Bài 8 — Return value của root

Một descendant B xảy ra bên trong root A trước khi A return. Nó có thể dựa vào return value cuối của A không?

**Đáp án:** Không mặc định: return của A chưa có. Nếu cần dữ liệu sớm, capture ở root entry hoặc từ một bước đã hoàn thành.

### Bài 9 — Binding và self-invocation

Target tự gọi `this.inner()`, proxy handler chỉ chặn những lời gọi qua proxy. Vì sao advice inner không log flowId dù ThreadLocal còn active?

**Đáp án:** Handler của proxy không nhận invocation inner; context existence và interception coverage là hai điều kiện riêng.

### Bài 10 — Kiểm chứng flow ID

Có `orderId="O-1"` ở hai requests đồng thời. Dùng `orderId` làm duy nhất key cho context map có an toàn không?

**Đáp án:** Không nếu hai occurrences cần state riêng. Dùng flow occurrence identity và xem orderId là thuộc tính domain.

### Bài 11 — Counter chung async

Một flow fork ra ba workers cùng cập nhật `frame.steps++`. Code có an toàn không?

**Đáp án:** Không, phép tăng trên mutable frame shared có race. Dùng counter/thread-safe aggregation và hợp đồng merge hoặc child frames tách biệt.

### Bài 12 — Context leak trong executor

A throw trên worker, B chạy tiếp cùng worker. B nhận ID A. Test nào bắt lỗi ổn định?

**Đáp án:** Single-thread executor chạy A rồi B nối tiếp, assert B không nhận context A; kiểm cleanup trên error path.

## 22. Checklist thiết kế binding giữa các join points

1. **Root:** Điểm nào tạo flow context và do cơ chế nào intercept?
2. **Join-point types:** Chọn call hay execution; có support root/descendants không?
3. **Scope semantics:** Root có được tính là một điểm được chọn không?
4. **Data source:** Arguments/metadata của root nào là authoritative?
5. **Identity:** Flow occurrence ID tách với domain ID chưa?
6. **Binding key:** Có typed key/record tránh nhầm field không?
7. **Owner:** Ai tạo, ghi, đọc, đóng context?
8. **Nested policy:** Top frame, parent frame hay tất cả ancestors?
9. **Reentrancy:** Recursion có tạo frame mới hay share logical flow?
10. **Concurrent flows:** Hai threads và hai targets có isolation không?
11. **Thread reuse:** Có restore/remove ở mọi exit path không?
12. **Async:** Context snapshot được propagate theo task/queue/RPC thế nào?
13. **Error:** Lỗi ở root/descendant có cleanup và không che exception gốc?
14. **Retry:** Logical ID và attempt ID có phân biệt không?
15. **Completion:** Scope tính tới method return hay async task complete?
16. **Security:** Tenant/principal đến từ trusted boundary, không bị spoof?
17. **Privacy:** Metadata tối thiểu, không log secrets/raw args?
18. **State mutation:** Có synchronization nếu nhiều workers sửa cùng context?
19. **Memory:** Registry/context/task references có TTL và cleanup?
20. **Coverage:** Proxy bypass hoặc weaving scope có làm thiếu descendant events?
21. **Order:** Binder có mở scope trước reader advice và đóng sau đúng mốc?
22. **Testing:** Nested, throw, async, cancel, concurrent, outside-flow đều có test?
23. **Portability:** Những semantics nào phụ thuộc engine AOP cụ thể?
24. **Performance:** Binding lookup, stack tracking, context propagation và metrics overhead đã đo?

## Kết luận

**Ràng buộc dữ liệu giữa các điểm trong cùng ngữ cảnh** là cách nối những join points riêng lẻ thành một luồng quan sát hoặc điều phối có identity chung. Root capture dữ liệu, một cơ chế association đặt dữ liệu vào đúng dynamic scope, các descendant advice tra cứu theo scope ấy, và cleanup/restore kết thúc vòng đời của context trên mọi nhánh success/failure.

Điểm quan trọng nhất không phải chọn một cấu trúc lưu trữ, mà là **định nghĩa đúng “cùng ngữ cảnh”**. Nested flows cần stack hoặc parent relation; concurrent flows cần isolation; async tasks cần explicit propagation; retries cần tách logical-operation data với attempt data; security context cần trusted source; context có data mutable cần owner và synchronization rõ. Với proxy, context có thể tồn tại nhưng join point vẫn không được handler quan sát nếu call path bypass proxy. Với weaving, phạm vi và loại join point quyết định độ phủ. Một giải pháp AOP tốt chỉ làm dữ liệu “đi theo flow” **khi scope, trust và lifecycle thật sự hợp lệ**, không để dữ liệu cũ hoặc dữ liệu của flow khác rò sang lời gọi kế tiếp.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-control-flow-006">Lời gọi đệ quy và ý nghĩa của độ sâu can thiệp</a>

<details>
<summary>Xem chi tiết</summary>

# Lời gọi đệ quy và ý nghĩa của độ sâu can thiệp trong AOP

## 1. Đệ quy tạo ra nhiều lần thực thi, nhưng có chắc tạo nhiều lần advice?

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming – AOP)**, một **join point** là một vị trí/sự kiện mà mô hình AOP cho phép can thiệp; **pointcut** lựa chọn các join point, còn **advice** thực thi hành vi cắt ngang như tracing, logging, auditing, đo thời gian hoặc kiểm tra chính sách.

Đối với một phương thức đệ quy, **cùng một phương thức có thể được gọi nhiều lần trước khi lần gọi trước kết thúc**. Thí dụ:

```java
// Mã minh họa ý tưởng.
int factorial(int n) {
    if (n <= 1) return 1;
    return n * factorial(n - 1);
}
```

`factorial(4)` tạo chuỗi gọi `factorial(4) → factorial(3) → factorial(2) → factorial(1)` trước khi lần lượt return. Nhưng liệu advice cũng chạy bốn lần? **Không thể trả lời chỉ từ source của method.** Điều đó phụ thuộc:

1. **Loại join point** được chọn: *method call*, *method execution* hay loại khác.
2. **Phạm vi can thiệp**: bytecode caller/callee có được dệt không, hay chỉ lời gọi qua proxy mới được intercept?
3. **Đường gọi đệ quy**: lời gọi quay qua boundary được intercept hay gọi nội bộ trực tiếp qua `this`?
4. **Điều kiện pointcut**: có chọn tất cả lần gọi, chỉ lần ngoài cùng, chỉ mức sâu nào đó, hay theo ngữ cảnh luồng điều khiển?
5. **Hành vi advice**: around advice có gọi `proceed` không, một lần hay nhiều lần, có tự sinh thêm lời gọi không?
6. **Ngữ nghĩa độ sâu**: đang đếm call stack nghiệp vụ, số advice activation, độ sâu control flow, hay số lần thử lại?

**Mệnh đề nền tảng:** *Một call site trong bytecode có thể tạo nhiều sự kiện runtime. Ngược lại, nhiều lần gọi nghiệp vụ không nhất thiết đều đi qua cùng một interception boundary.* Khi nghiên cứu AOP với đệ quy, phải phân biệt **độ sâu thực thi của chương trình** với **độ sâu can thiệp thực tế của advice**.

> Bài này trình bày AOP cơ bản và nâng cao nói chung, không dựa vào một công cụ riêng. Những cách diễn đạt như `cflow`, `cflowbelow`, around/proceed hoặc call/execution là khái niệm/mô hình thường gặp; **cú pháp chính xác và loại join point được hỗ trợ tùy engine**. Ví dụ Java 17+ sử dụng Java Dynamic Proxy để chứng minh những khác biệt quan sát được, không khẳng định proxy là hình thức AOP duy nhất.

## 2. Ba loại độ sâu cần tách biệt

**Độ sâu đệ quy nghiệp vụ (business recursion depth)** phản ánh số tầng gọi lồng nhau của thuật toán. **Độ sâu advice (advice activation depth)** đếm những lần một advice/interceptor cụ thể đang hoạt động trên một đường thực thi. **Độ sâu control-flow (dynamic control-flow depth)** diễn tả mức lồng nhau của các flow được xác định bởi pointcut gốc hoặc phạm vi động.

```text
Call stack logic:              Các advice activation (giả sử có interception):
factorial(4)                   advice[factorial(4)]  depth=0
  factorial(3)                   advice[factorial(3)] depth=1
    factorial(2)                   advice[factorial(2)] depth=2
      factorial(1)                   advice[factorial(1)] depth=3
```

Sơ đồ bên phải chỉ đúng nếu **mọi bước đệ quy đều được advice quan sát**. Nếu proxy chỉ thấy lời gọi ngoài cùng, cột advice chỉ có một entry với depth 0, dù call stack nghiệp vụ có bốn tầng.

| Khái niệm | Đối tượng đếm | Có luôn bằng số lần đệ quy? |
| --- | --- | --- |
| Recursion depth | Mức lồng nhau của lời gọi bài toán | Chính là độ sâu theo quy ước nghiệp vụ đã chọn |
| Advice activation depth | Số lời gọi advice đang chưa kết thúc | **Không**; tùy pointcut, interception và ordering |
| `cflow`/flow nesting | Số tầng của dynamic flow được định nghĩa | **Không**; tùy join point mở flow và semantics engine |
| Thread stack depth | Số frame thực thi trên thread | **Không**; còn runtime/internal frames/JIT |
| Retry attempt count | Số lần around gọi `proceed` | **Không**; retry không đồng nghĩa recursion |
| Distributed trace depth | Quan hệ span cha–con theo carrier | **Không**; có thể trải qua nhiều thread/process |

Đừng dùng một field tên `depth` mà không định nghĩa nó đo **độ sâu của cái gì** và lấy mốc 0 hay 1 ở đâu.

## 3. Lời gọi đệ quy trực tiếp và gián tiếp

### 3.1. Đệ quy trực tiếp

```java
// Ví dụ minh họa trong class nghiệp vụ.
int gcd(int a, int b) {
    return b == 0 ? a : gcd(b, a % b);
}
```

Một method gọi chính nó. Nếu execution join point của `gcd` được weave đầy đủ, mỗi lượt thực thi `gcd` có thể sinh một advice activation riêng. Nhưng nếu caller chỉ đi qua proxy ở lần đầu và lời gọi nội bộ bypass, advice của wrapper có thể chỉ chạy một lần.

### 3.2. Đệ quy tương hỗ (mutual recursion)

```java
// Đoạn minh họa, không phải tệp hoàn chỉnh.
boolean even(int n) {
    return n == 0 || odd(n - 1);
}
boolean odd(int n) {
    return n != 0 && even(n - 1);
}
```

Ở đây flow `even → odd → even → ...`. Pointcut chỉ chọn `even` sẽ thấy **ít activation hơn** tổng số method executions thuộc chuỗi. Một pointcut chọn cả hai có thể thấy tất cả, nếu các join point đều được intercept.

**Recursion không bắt buộc method gọi chính tên của nó.** Điều quan trọng là có đường đi trong call graph quay lại một node đang nằm trên dynamic call stack.

### 3.3. Recursion qua nhiều object/module

```text
OrderService.process()
  -> BillingService.charge()
      -> RetryCoordinator.dispatch()
          -> OrderService.process()
```

Một vòng gọi có thể đi qua nhiều target, class loader hoặc module. Trong AOP:

- Advice chọn execution của `OrderService.process` có thể kích hoạt lại khi flow quay về method ấy.
- Advice chọn call site ở `BillingService` không nhất thiết thấy call site từ `RetryCoordinator`.
- Proxy có thể được dùng ở một cạnh của call graph nhưng bỏ qua ở cạnh khác.
- Một context theo thread hoặc per-target không tự đại diện chính xác mọi vòng lặp trong call graph.

Khi phân tích độ sâu, cần vẽ **đường gọi thực** thay vì chỉ đếm số câu lệnh gọi hàm trong source.

## 4. Join point được dệt ở đâu quyết định độ sâu quan sát

Hai loại join point quan trọng:

- **Method call join point:** vị trí phát lời gọi, thường nằm trong bytecode **caller**.
- **Method execution join point:** thời điểm chạy phần thân phương thức, thường liên quan bytecode **callee**.

```text
callerMethod()
    |
    | [call join point ở caller]
    v
targetMethod()
    |
    | [execution join point ở callee]
    v
target body
```

Một source call site đệ quy như `factorial(n - 1)` có thể được **thực thi nhiều lần**. Nếu công cụ dệt call site ấy, mỗi lần đi qua instruction có thể tương ứng một call join point occurrence. Nếu chọn method execution và method body được weave, mỗi lần bắt đầu execution của method ấy có thể kích hoạt advice.

### Lần gọi gốc và lần gọi đệ quy không cùng call site

Giả sử:

```java
int value = service.factorial(4); // Call site của caller ngoài.
```

Trong `factorial` lại có instruction gọi `factorial(n-1)`. Nếu pointcut chỉ chọn **call site từ caller ngoài**, nó không tự chọn **call site bên trong factorial**. Ngược lại, execution pointcut cho `factorial` (nếu đã weave đúng method) có thể thấy mọi execution không phân biệt nguồn caller.

Đừng suy ra “đã chọn method name” nghĩa là engine tự chọn cả call và execution. **Loại join point và scope class weaving phải tường minh**.

### Cùng một join point tĩnh, nhiều occurrence động

Một call instruction nằm trong class file chỉ xuất hiện **một vị trí bytecode**, nhưng loop/recursion có thể chạy nó nhiều lần. AOP thường xử lý **những lần xuất hiện ở runtime**, không phải chỉ đếm số vị trí trong source/bytecode.

## 5. AOP qua proxy: self-invocation có thể làm advice depth thấp hơn recursion depth

Với một wrapper proxy đặt trước target:

```text
caller -> proxy.factorial(4)
           |
           -> target.factorial(4)
                -> this.factorial(3)
                     -> this.factorial(2)
                          -> this.factorial(1)
```

Chỉ lời gọi **đầu tiên** đi qua proxy. Những lời gọi `this.factorial(...)` nội bộ trên raw target **không quay ra wrapper proxy**, vì vậy handler không nhận thêm activation.

**Không nên kết luận thuật toán không đệ quy chỉ vì handler log một lần.** Độ sâu advice phụ thuộc đường interception.

### Đi qua proxy ở mỗi bước

Nếu target giữ một reference `self` trỏ tới proxy và gọi:

```java
// Mã minh họa, không phải chương trình độc lập.
return n * self.factorial(n - 1);
```

thì mỗi bước có thể quay vào proxy:

```text
proxy.factorial(4) -> advice depth 0
  target.factorial(4)
    proxy.factorial(3) -> advice depth 1
      target.factorial(3)
        proxy.factorial(2) -> advice depth 2
          ...
```

Đây là một **cách thử nghiệm có chủ đích**, không phải lời khuyên chung nên bắt mọi target giữ self-proxy. Thiết kế self-reference có thể tạo coupling, vòng dependency, khó test và lộ raw/proxy identity. Một phương án thiết kế khác là tách operation thành một component mà những lời gọi thật sự cần interception đi qua một contract công khai.

### Các loại proxy không hoàn toàn giống nhau

Một subclass proxy có cơ chế override/dispatch khác wrapper proxy. Với một số cách triển khai, `this.method()` có thể dispatch qua override bị intercept; với cách khác, không. Final/private/static methods lại có giới hạn riêng. Vì vậy kết luận **“self-invocation luôn bypass mọi proxy” là quá rộng**. Ví dụ trong bài sử dụng chính xác **Java Dynamic Proxy bao quanh target**.

## 6. Ví dụ Java 17+ hoàn chỉnh: một thuật toán, ba độ sâu

Chúng ta sẽ triển khai:

1. Đệ quy qua `this`: target thực thi ba tầng nhưng advice chỉ kích hoạt một lần.
2. Đệ quy qua `self` đang trỏ tới proxy: advice kích hoạt ba tầng với độ sâu **0, 1, 2**.
3. Ném ngoại lệ: advice in dòng throw, khôi phục bộ đếm trong `finally`.
4. Lời gọi tiếp theo: depth trở về **0**, chứng minh state không rò sang invocation mới.

Proxy handler giữ **ThreadLocal<Integer>` để minh họa việc theo dõi *advice activation depth*** trên cùng thread. Mã có `remove` ở tầng ngoài cùng và `set(d)` để khôi phục depth của tầng ngoài khi inner kết thúc.

**Chú ý:** ThreadLocal ở đây đo **advice nesting synchronous** trong một demo Java SE, không đại diện tự động cho request scope, distributed trace hay mọi mô hình AOP. Một around advice chỉ cần biết depth của chính call có thể dùng local variable; ThreadLocal được chọn để **liên kết các activation lồng nhau**.

### 6.1. Tệp `RecursionAspectDepthDemo.java`

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class RecursionAspectDepthDemo {
    interface MathTask {
        int factorial(int n);
    }

    static final class Factorial implements MathTask {
        private final boolean throughProxy;
        private MathTask self;

        Factorial(boolean throughProxy) {
            this.throughProxy = throughProxy;
        }

        void bind(MathTask proxy) {
            self = proxy;
        }

        @Override
        public int factorial(int n) {
            System.out.println("target n=" + n);
            if (n < 0) {
                throw new IllegalArgumentException("negative");
            }
            if (n <= 1) {
                return 1;
            }
            return n * (throughProxy
                    ? self.factorial(n - 1)
                    : this.factorial(n - 1));
        }
    }

    static final class TracingHandler
            implements java.lang.reflect.InvocationHandler {
        private final MathTask target;
        private final ThreadLocal<Integer> depth =
                ThreadLocal.withInitial(() -> 0);

        TracingHandler(MathTask target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            int d = depth.get();
            depth.set(d + 1);
            System.out.println(
                    "advice.enter depth=" + d + " n=" + args[0]);

            try {
                Object value;
                try {
                    value = method.invoke(target, args);
                } catch (InvocationTargetException wrapped) {
                    throw wrapped.getCause();
                }

                System.out.println(
                        "advice.exit depth=" + d + " value=" + value);
                return value;
            } catch (Throwable error) {
                System.out.println(
                        "advice.throw depth=" + d + " "
                        + error.getClass().getSimpleName());
                throw error;
            } finally {
                if (d == 0) {
                    depth.remove();
                } else {
                    depth.set(d);
                }
                System.out.println("advice.cleanup depth=" + d);
            }
        }
    }

    static MathTask wrap(Factorial target) {
        MathTask proxy = (MathTask) Proxy.newProxyInstance(
                MathTask.class.getClassLoader(),
                new Class<?>[]{MathTask.class},
                new TracingHandler(target));
        target.bind(proxy);
        return proxy;
    }

    public static void main(String[] args) {
        System.out.println("== direct self call ==");
        MathTask direct = wrap(new Factorial(false));
        System.out.println(
                "caller result=" + direct.factorial(3));

        System.out.println("== recursive call through proxy ==");
        MathTask reenter = wrap(new Factorial(true));
        System.out.println(
                "caller result=" + reenter.factorial(3));

        System.out.println("== exceptional unwind ==");
        try {
            reenter.factorial(-1);
        } catch (IllegalArgumentException error) {
            System.out.println(
                    "caller caught=" + error.getMessage());
        }
        System.out.println(
                "caller result=" + reenter.factorial(2));
    }
}
```

### 6.2. Lệnh biên dịch và chạy

Đặt đúng tên file `RecursionAspectDepthDemo.java`, dùng JDK 17 trở lên:

```bash
javac --release 17 -encoding UTF-8 RecursionAspectDepthDemo.java
java RecursionAspectDepthDemo
```

**Standard output chính xác đã kiểm bằng cách biên dịch/chạy với JDK:**

```text
== direct self call ==
advice.enter depth=0 n=3
target n=3
target n=2
target n=1
advice.exit depth=0 value=6
advice.cleanup depth=0
caller result=6
== recursive call through proxy ==
advice.enter depth=0 n=3
target n=3
advice.enter depth=1 n=2
target n=2
advice.enter depth=2 n=1
target n=1
advice.exit depth=2 value=1
advice.cleanup depth=2
advice.exit depth=1 value=2
advice.cleanup depth=1
advice.exit depth=0 value=6
advice.cleanup depth=0
caller result=6
== exceptional unwind ==
advice.enter depth=0 n=-1
target n=-1
advice.throw depth=0 IllegalArgumentException
advice.cleanup depth=0
caller caught=negative
advice.enter depth=0 n=2
target n=2
advice.enter depth=1 n=1
target n=1
advice.exit depth=1 value=1
advice.cleanup depth=1
advice.exit depth=0 value=2
advice.cleanup depth=0
caller result=2
```

### 6.3. Phân tích nhánh đệ quy trực tiếp qua `this`

Trong trường hợp `throughProxy=false`:

- Caller gọi `direct.factorial(3)` trên proxy, handler chạy ở depth **0**.
- Target thực hiện `factorial(3)`.
- Đường gọi `this.factorial(2)` và `this.factorial(1)` chạy trực tiếp trong target.
- Handler không nhận activation ở hai mức trong.
- Target trả `6`; handler in exit/cleanup depth 0.

Số **target executions = 3**, số **advice activations = 1**.

**Kết quả không chứng minh pointcut không hỗ trợ recursion**; nó chứng minh hai recursion calls đã bypass **wrapper proxy boundary**. Nếu method executions của target thực sự được bytecode-weave, coverage có thể khác.

### 6.4. Phân tích nhánh mỗi bước quay qua proxy

Với `throughProxy=true`:

- Outer handler lấy `d=0`, lưu `depth=1` trước khi proceed.
- Target tại `n=3` gọi `self.factorial(2)`, tức gọi **proxy**.
- Inner handler lấy `d=1`, lưu `depth=2`.
- Target tại `n=2` gọi `self.factorial(1)`, handler thứ ba lấy `d=2`.
- Tầng `n=1` trả `1`, advice ở depth 2 in exit/cleanup rồi restore `depth=2`.
- Tầng `n=2` tính `2*1=2`, advice depth 1 in exit/cleanup, restore `depth=1`.
- Tầng `n=3` tính `3*2=6`, advice depth 0 in exit/cleanup, `depth.remove()`.

Số **target executions = 3**, số **advice activations = 3**, advice depth theo quy ước **0, 1, 2**.

### 6.5. Tại sao thứ tự log “đi vào” và “đi ra” ngược nhau?

Đệ quy có dạng **nested LIFO**: đi vào outer → inner → innermost; khi trả về đi ra innermost → inner → outer.

```text
enter 0
  enter 1
    enter 2
    exit 2
  exit 1
exit 0
```

Nếu một after/finally advice ghi kết quả theo **thứ tự vào** thay vì **thứ tự unwind**, nó có thể đang dùng sai scope hoặc dữ liệu shared bị ghi đè. Tuy vậy, trong đa luồng/async không thể suy từ mọi log toàn cục một trật tự LIFO đơn nhất vì các luồng khác nhau có thể xen kẽ.

### 6.6. Khi ném lỗi, độ sâu phải được khôi phục

`reenter.factorial(-1)` ném `IllegalArgumentException`. Handler:

1. Ghi enter depth 0.
2. Reflection nhận lỗi target trong `InvocationTargetException`.
3. Handler mở wrapper, ném đúng cause gốc.
4. Nhánh catch ghi `advice.throw depth=0` và rethrow.
5. `finally` gọi `remove()` vì `d==0`.
6. Caller bắt ngoại lệ.
7. Lời gọi mới `factorial(2)` lại bắt đầu depth **0**, không phải **1**.

**Cleanup theo `finally` thiết yếu ngay cả khi target throw.** Nhưng resource exhaustion nghiêm trọng như `StackOverflowError` có thể làm việc cleanup/log không đáng tin cậy; không nên cố dựa vào advice để phục hồi mọi trường hợp cạn kiệt stack.

### 6.7. Ý nghĩa của depth được ghi

Biến `d` là **số advice activations đang bao ngoài** của handler cùng đối tượng `TracingHandler` trên thread hiện tại. Nó không phải:

- Số Java stack frames thực tế.
- Số lần method factorial từng được gọi trong lịch sử.
- Mức sâu toàn cục của mọi aspect hoặc mọi proxy.
- Request/trace depth xuyên thread.
- Một giá trị đảm bảo đúng cho native/async/distributed code.

**Nếu dùng một handler khác hoặc một ThreadLocal khác**, độ sâu quan sát của nó có thể khác dù chạy trong cùng call graph.

## 7. Control flow trong AOP: `cflow` và `cflowbelow`

Một số mô hình AOP cung cấp biểu thức chọn join point theo **luồng điều khiển động** (dynamic control flow).

Về ý tưởng:

- **`cflow(P)`:** chọn join point nằm **trong** dynamic control flow được khởi tạo khi một join point khớp `P` xuất hiện; thông thường bao gồm chính điểm gốc theo semantics của mô hình hỗ trợ.
- **`cflowbelow(P)`:** chọn join point **bên dưới** một điểm gốc khớp `P` trong dynamic control flow, không lấy **chính điểm gốc đó làm điểm được chọn vì flow của nó**.

Hai khái niệm này không giống việc lọc dựa vào **call stack text** hay số thread. Chúng yêu cầu engine quản lý/đánh giá ngữ cảnh động theo hợp đồng riêng.

### Một ví dụ khái niệm

```text
Root P: Checkout.run()          [điểm mở flow]
  |
  +-- Validate.check()          [bên dưới P]
  |
  +-- Billing.charge()          [bên dưới P]
       |
       +-- Retry.execute()      [bên dưới P]
```

Với một pointcut Q chọn execution của `Billing.charge`:

- `Q && cflow(P)` có thể chọn `Billing.charge` khi nằm trong flow `Checkout.run`.
- `Q && cflowbelow(P)` cũng có thể chọn `Billing.charge` nếu nó là điểm **phía dưới root P**.
- Chính `Checkout.run` không được chọn **bởi cflowbelow của lần P làm root**, theo định nghĩa “below”.

**Mốc root P được quyết định bởi một pointcut**, không phải mọi method trong chương trình mặc định đều mở flow AOP.

### Lưu ý khi chính P có recursion

```text
P(3)      [P đầu tiên, root]
  |
  +-- P(2)  [bên dưới P(3), đồng thời lại khớp P]
        |
        +-- P(1) [bên dưới P(2) và P(3)]
```

Đây là chi tiết nâng cao dễ nhầm:

- `P(3)` là điểm mở flow đầu tiên ở cấp ngoài.
- `P(2)` **không phải root P(3)**; nó nằm bên dưới flow của `P(3)`, nên **có thể khớp `cflowbelow(P)` nhờ một ancestor P**.
- `P(1)` tương tự.
- Nếu engine có semantics cflowbelow đặc thù, cần xác minh predicate tại các recursive occurrences theo contract của engine.

Không phát biểu đơn giản **“mọi join point khớp P đều không bao giờ khớp cflowbelow(P)”**. Một recursive occurrence P có thể nằm **bên dưới một occurrence P khác**. Cũng không kết luận `cflow(P)` tự đếm số tầng: predicate Boolean “có nằm trong flow” khác với integer “có bao nhiêu root P đang active”.

## 8. Chọn mức ngoài cùng và mức bên trong

Một nhu cầu phổ biến: **chỉ log một lần cho toàn bộ phép đệ quy**, thay vì log từng tầng. Có hai nhóm cách tiếp cận:

### 8.1. Dựa trên control flow (nếu engine hỗ trợ)

Với một pointcut P đủ chính xác cho method recursion, biểu thức kiểu:

```text
P AND NOT cflowbelow(P)
```

thường được dùng theo ý tưởng để chọn **occurrence P ở tầng ngoài cùng**, tức P không nằm bên dưới một occurrence P khác đang active trong cùng dynamic flow.

Ví dụ `factorial(4)` → `factorial(3)`:

- `factorial(4)` không có ancestor P, nên phù hợp.
- `factorial(3)` nằm dưới `factorial(4)`, bị loại.
- `factorial(2)` cũng bị loại.

**Điều kiện rất quan trọng:** engine phải hỗ trợ kiểu pointcut/control-flow đó, P phải match đúng những occurrence cần xét, và scope weaving phải bao phủ chúng. Nếu proxy chỉ thấy outer, kết quả “một advice” không chứng minh cflow filter đã hoạt động.

### 8.2. Dùng một guard đo advice depth

Trong interceptor custom, có thể:

```java
// Pseudocode: d là depth của advice activation hiện tại.
boolean outermost = (d == 0);
if (outermost) {
    emitStartEvent();
}
try {
    return proceed();
} finally {
    if (outermost) {
        emitEndEvent();
    }
}
```

Ở đây điều kiện dựa trên **độ sâu advice thực sự được gọi**, không nhất thiết trên toàn bộ recursion. Nếu inner recursion bypass interception, d vẫn là 0 ở mỗi lời gọi ngoài độc lập; không thể suy từ guard đó rằng toàn bộ business call graph đã được quan sát.

### Khi nào nên quan sát mọi tầng?

- Cần debug từng bước của thuật toán.
- Muốn đo duration riêng từng recursive activation.
- Cần đếm số node visited hoặc số tầng logic.
- Muốn kiểm chứng stop condition, input mỗi bước, exception propagation.
- Cần phân tích chi phí hoặc số lần chạm một dependency.

### Khi nào chỉ log tầng ngoài?

- Event audit đại diện **một thao tác business**.
- Một trace span cấp cao để hạn chế log volume.
- Muốn đo tổng elapsed time của cả phép đệ quy.
- Inner event đã có representation khác để tránh log trùng.

**“Chỉ tầng ngoài” là lựa chọn quan sát, không mặc nhiên là lựa chọn security enforcement đúng.** Một kiểm tra bắt buộc ở mỗi execution không nên bị guard depth tắt bừa.

## 9. Đếm chiều sâu: quy ước bắt đầu từ 0 hay 1?

Ví dụ demo:

```text
advice.enter depth=0  // không có advice activation cùng handler đang bao ngoài
advice.enter depth=1  // có một activation bao ngoài
advice.enter depth=2  // có hai activation bao ngoài
```

Đó là **zero-based nesting depth**. Nếu muốn “mức thứ mấy đang chạy”, có thể hiển thị `level = d + 1`.

Một số hệ thống định nghĩa depth là **số frame hiện đang active**, nghĩa là lúc vừa vào advice đầu tiên depth=1. Cả hai đều hợp lệ nếu được tài liệu hóa và kiểm tra.

### Tính depth trước hay sau khi tăng?

```java
int previous = depth.get();
depth.set(previous + 1);
try {
    log("depth=" + previous);
    return proceed();
} finally {
    restore(previous);
}
```

Ở đây `previous` là số activation đang bao ngoài. Nếu log `depth.get()` **sau tăng**, log sẽ hiển thị 1,2,3 thay cho 0,1,2. Không phải lỗi, nhưng **khác quy ước**.

Nếu nhiều advice cùng dùng một counter chung mà không xác định loại event nào làm tăng, depth có thể phản ánh **advice chain** thay vì **method recursion**.

## 10. Reentrancy của advice: đệ quy có thể xảy ra trong chính concern

Một aspect logging có thể gọi một helper để ghi audit; helper ấy lại khớp pointcut đang dùng. Khi advice chạy:

```text
advice.before()
   |
   +-- AuditWriter.write()
           |
           +-- matches pointcut -> advice.before()
                   |
                   +-- AuditWriter.write()
                           |
                           +-- ... recursion không mong muốn
```

Đó là **advice-induced recursion** hoặc reentrancy do concern tự kích hoạt mình, khác với recursion có chủ đích của thuật toán nghiệp vụ.

### Biểu hiện

- Stack tăng liên tục và có thể `StackOverflowError`.
- Event audit/log lặp lại quá nhiều.
- Latency tăng mạnh, CPU bị tiêu tốn.
- Advice không bao giờ tới `proceed`.
- Một exception từ helper làm chuỗi xử lý lỗi lại gọi helper lần nữa.

### Xử lý đúng: thu hẹp phạm vi can thiệp

Những hướng thiết kế:

1. **Loại trừ code runtime của aspect** khỏi pointcut nếu phù hợp.
2. Chọn **boundary nghiệp vụ thực sự**, thay vì match tất cả method bất kể package/type.
3. Tránh gọi lại chính API bị instrument từ trong logging sink, hoặc dùng một writer ở boundary không bị match.
4. Sử dụng **reentrancy guard có cleanup** nếu phải có.
5. Kiểm chứng exception path và nested calls để không vô tình khóa vĩnh viễn instrumentation.
6. Không tắt toàn bộ security/audit checks chỉ để giảm log nếu những kiểm tra đó là bắt buộc.

### Guard khác filter control flow

Một `ThreadLocal<Boolean> inAdvice` có thể tạm chặn **chính đoạn logic logging** khi reenter, nhưng **không tự phản ánh mọi business recursion**. Nếu guard làm around advice bỏ qua `proceed`, nó còn có thể khiến **target không chạy**. Một guard bảo vệ sink nên giới hạn đúng concern, vẫn giữ hành vi nghiệp vụ theo policy.

## 11. Giới hạn recursion: giới hạn nghiệp vụ khác giới hạn advice

Một thuật toán có thể có stop condition:

```java
if (n <= 1) return 1;
```

Stop condition này liên quan **business recursion**, không phải độ sâu advice. Một guard kiểu:

```java
if (adviceDepth > 100) throw new IllegalStateException("too deep");
```

chỉ có nghĩa phù hợp khi **adviceDepth phản ánh chính phạm vi cần giới hạn**. Nếu self-call bypass proxy, business depth có thể hàng nghìn trong khi adviceDepth vẫn bằng 0.

### An toàn hơn cho giới hạn nghiệp vụ

- Thiết kế thuật toán có base case/termination condition đúng.
- Đặt giới hạn theo **input và domain constraints**.
- Với dữ liệu không tin cậy, bảo vệ resource bằng budget/visited-state/explicit stack khi cần.
- Phân biệt recursion bất tận với một bài toán có depth lớn hợp lệ.
- Không trông cậy vào việc bắt `StackOverflowError` để quản lý depth bình thường.

Một aspect có thể hỗ trợ **đo và cảnh báo** độ sâu ở join point được quan sát; nó không thay cho chứng minh thuật toán luôn dừng hoặc giới hạn tài nguyên thực tế.

## 12. Around advice, `proceed` và độ sâu không phải lúc nào cũng 1:1

Một around advice có thể:

- Gọi `proceed` **một lần**: target/inner chain chạy một lần.
- **Không gọi**: skip target, trả fallback hoặc chặn theo policy.
- Gọi `proceed` **nhiều lần**: retry hoặc xử lý mô hình đặc biệt, nếu engine cho phép.
- Chuyển ngữ cảnh, bọc return hay thay Throwable theo contract.

Do đó:

```text
1 advice activation
   |
   +-- proceed attempt #1
   |
   +-- proceed attempt #2
   |
   +-- proceed attempt #3
```

Có thể có **một around advice activation** nhưng **ba lần target execution**. Nếu mỗi `proceed` chạm một join point khác lại được instrument, những advice ở bên trong có thể chạy nhiều lần, trong khi around ở ngoài vẫn chỉ có một activation.

**Đừng dùng retry count làm recursion depth**, cũng đừng suy số lần target chạy từ số lần logging quanh advice.

### Khi một around không `proceed`

Nếu advice ngoài quyết định chặn:

- Advice bên trong có thể không chạy.
- Method đệ quy có thể không được bắt đầu.
- Một “độ sâu” ghi trong logging bên ngoài không chứng minh tồn tại bất kỳ recursive target frame nào.
- Outcome cuối cùng được quy định bởi around advice, không phải body target.

### Advice ordering

```text
OuterAround
   -> InnerAround
        -> Target.recursive()
```

Nếu OuterAround và InnerAround cùng được áp dụng vào mỗi recursion execution, mỗi một mức nghiệp vụ có thể chứa **hai advice activations ở các layer khác nhau**. Counter đếm mọi interceptor entries có thể là 2× số recursion levels đang active, không bằng mức recursion.

Hãy xác định counter đếm **aspect nào** và **loại join point nào**.

## 13. Thời gian thực thi và đệ quy: inclusive vs exclusive

Một around advice có thể đo thời gian:

```java
// Pseudocode: proceed và metric recorder do công cụ/ứng dụng cung cấp.
long startedAt = System.nanoTime();
try {
    return proceed();
} finally {
    recordDuration(System.nanoTime() - startedAt);
}
```

Nếu advice chạy ở **mỗi recursive execution**, duration của outer **bao gồm** thời gian của inner. Đây là **inclusive time**.

```text
factorial(3)  [------------------ outer duration ------------------]
  factorial(2)    [---------- inner duration ------------]
    factorial(1)       [---- innermost ----]
```

Nếu **cộng tất cả inclusive durations**, tổng có thể lớn hơn elapsed time của cả phép gọi vì thời gian inner bị đếm lại ở outer.

### Exclusive time cần thiết kế riêng

**Exclusive time** là thời gian mà invocation hiện tại sử dụng, trừ thời gian ở những child operations được chọn để loại. Muốn tính đúng thường cần giữ **stack state của từng invocation**, lưu child elapsed và xử lý nested scopes:

```text
outerInclusive = outerEnd - outerStart
outerExclusive = outerInclusive - sum(selectedChildInclusive)
```

Công thức chỉ đúng khi những child durations **không chồng lấn theo cách gây đếm trùng**, semantics selected children phù hợp và đo trên cùng đồng hồ/thời gian. Với async/concurrent subtasks, phép trừ đơn giản có thể sai vì các khoảng thời gian chồng lấp.

### Dùng `System.nanoTime()` đúng mục đích

`System.nanoTime()` dành cho **elapsed duration**, không phải timestamp lịch hoặc distributed clock. Dữ liệu log có thể mang thêm wall-clock timestamp để đối chiếu, nhưng không trừ tùy tiện timestamps từ hai máy để suy elapsed time với độ chính xác cao.

## 14. Độ sâu, sampling và chi phí instrumentation

Đệ quy sâu có thể khiến advice kích hoạt rất nhiều lần:

```text
Recursion levels:      10,000
One advice per level:  10,000 advice activations
Three advices/level:   up to 30,000 advice activations
```

Đây là mô hình số đếm đơn giản, không phải cam kết mọi join point đều match. Chi phí có thể bao gồm:

- Pointcut dynamic predicate.
- Tạo context/argument bindings.
- ThreadLocal read/set/remove.
- Log formatting và output.
- Tạo trace span/metric event.
- Stack trace extraction nếu có.
- Resource/writer contention.
- GC từ allocation per-invocation.

Một event nhỏ có thể trở nên đắt khi nhân theo recursion depth.

### Chọn chính sách quan sát

**Ghi mỗi tầng** khi cần hiểu thuật toán cụ thể.

**Ghi root và failure** khi chỉ cần outcome của toàn thao tác.

**Sampling theo depth** khi quá nhiều tầng, ví dụ chọn các mức `0,1,2,4,8,16,...` cho mục tiêu debug — phải tài liệu hóa vì đây không phải số lượng event đầy đủ.

**Đếm tổng bằng counter** khi chỉ cần metric aggregate và không muốn tạo log ở mỗi mức.

**Không dùng sampling để tự tắt những enforcement checks bắt buộc**. Sampling nên giới hạn observability phụ trợ, không giới hạn logic an toàn quan trọng.

## 15. Dynamic control flow khác lexical nesting

Một method A gọi B nhưng B được gọi từ nơi khác nữa:

```text
Request path 1: Checkout -> Billing -> Storage
Request path 2: AdminTool ------> Billing -> Storage
```

Nếu một pointcut chọn `Billing` **chỉ khi nó nằm trong flow của Checkout**, việc chọn phụ thuộc **đường gọi runtime**, không chỉ vì Billing thuộc một package hoặc có static call graph gần Checkout.

Đệ quy càng làm rõ điều này: cùng method có thể xuất hiện ở các độ sâu khác nhau dưới các roots khác nhau.

- **Lexical/static location:** class/method/call site được biên dịch.
- **Dynamic control flow:** các join point đang active trong đường thực thi tại thời điểm runtime.
- **Aspect instance association:** singleton/per-object/per-flow nếu công cụ hỗ trợ.
- **Control-flow predicate:** cho biết một điểm có nằm trong scope đang mở hay không.

Không đồng nhất “một method được gọi trong source” với “một dynamic cflow context cụ thể”.

## 16. `cflowbelow` và điều kiện chọn root ngoài cùng

Xét một pointcut P chọn execution của một phương thức recursion. Về mặt ý tưởng, ta muốn:

```text
P and not cflowbelow(P)
```

khi có hỗ trợ để chỉ chọn occurrence P **không nằm bên dưới P khác**.

Tuy nhiên, hãy chú ý các trường hợp:

1. **Nested recursion:** inner P có P ancestor → bị loại.
2. **Mutual recursion:** nếu P chỉ chọn method A, các method B ở giữa không phải P; occurrence A quay lại vẫn có P ancestor.
3. **Call vs execution:** chọn call site P ở caller ngoài có thể không match các inner call sites; guard kết quả khác.
4. **Multiple roots:** nếu một flow P được mở bên trong flow P khác, điểm P mới có thể được coi là nested, không phải root độc lập.
5. **Self-invocation proxy bypass:** inner P execution không được intercept thì cflow predicate không “sửa” coverage thiếu.
6. **Advice-triggered calls:** logging helper khớp P có thể mở một flow P mới ngoài ý định.
7. **Thread hop:** task ở thread khác không tự nằm trong cflow đồng bộ của caller.
8. **Engine-specific semantics:** ký hiệu, khả năng dùng trong advice hay pointcut và chính xác điểm included/excluded phải theo tài liệu engine.

**Kết luận:** `cflowbelow` có thể giúp phân biệt root/inner dynamic context, nhưng **không phải công tắc “giới hạn recursion của Java”**. Nó là tiêu chí chọn join point trong một mô hình AOP có hỗ trợ.

## 17. `per-control-flow` và instance aspect

Một số mô hình AOP có thể tạo **aspect instance theo flow**: một lượt vào join point gốc tạo scope aspect riêng, các join point bên dưới liên kết với instance đó.

```text
Flow A: start P
   Aspect[A]
     -> recursive P
     -> recursive P

Flow B: start P
   Aspect[B]
     -> recursive P
```

Điều này khác shared singleton aspect:

- Một shared aspect có thể phục vụ cả Flow A và Flow B cùng lúc.
- Per-flow aspect có thể cô lập state giữa các flows theo semantics engine.
- Nhưng **một per-flow aspect vẫn có thể được gọi lại đệ quy** trong cùng flow, vì vậy một field `currentMethod` có thể bị ghi đè bởi inner invocation.
- **Per-flow ≠ per-invocation**.
- Scope flow cần cleanup và nested-flow handling rõ.

### Aspect instance và advice local state

Ngay cả khi aspect singleton, mỗi lần around advice chạy vẫn có **local variables riêng**. Một per-flow aspect giúp giữ **state chung của flow**, nhưng state chỉ có ý nghĩa trong một recursive level vẫn cần per-invocation local/scope tương ứng.

Ví dụ:

- **Flow trace ID:** có thể chia sẻ cho cả chuỗi recursion.
- **Current argument của một level:** cần riêng cho từng level.
- **Thời điểm bắt đầu của mỗi level:** cần riêng.
- **Tổng số node visited trong flow:** có thể gắn per-flow và cập nhật an toàn.
- **Global metric tổng lượt gọi:** thuộc shared metric sink, không cần per-flow instance.

## 18. ThreadLocal stack: khi nào giúp, khi nào sai?

Trong demo, `ThreadLocal<Integer>` lưu depth để các advice activations đồng bộ cùng thread nhận biết nhau. Một mô hình cần nhiều dữ liệu hơn có thể dùng `ThreadLocal<Deque<Frame>>`:

```text
enter outer: push Frame(outer)
enter inner: push Frame(inner)
exit inner:  pop  Frame(inner)
exit outer:  pop  Frame(outer)
```

Một stack LIFO đúng giúp khôi phục scope khi nested invocation return/throw.

### Những bất biến cần kiểm

1. Một entry thành công phải có một exit/cleanup tương ứng.
2. `push` trước khi code có thể gọi join point khác.
3. `pop` trong `finally` để xử lý return/throw.
4. Khi stack rỗng, `ThreadLocal.remove()` để tránh giữ state trong thread pool.
5. Nếu nested, restore outer frame chính xác.
6. Không phụ thuộc việc một callback chạy trên cùng thread nếu API không bảo đảm.
7. Khi advice chỉ được áp dụng một phần, stack đếm **những entry đã intercept**, không phải mọi business call.

### Không thay stack bằng một field duy nhất

`ThreadLocal<Frame> current` chỉ giữ một frame cho mỗi thread. Nếu inner ghi đè, outer sẽ bị mất context trừ khi bạn giữ previous và restore. **ThreadLocal cô lập giữa threads, không tự cô lập nested invocations trên cùng thread.**

### Khi thread pool tái sử dụng worker

Nếu một task để lại depth/context trên ThreadLocal, task kế tiếp trên cùng worker có thể bắt đầu ở depth > 0 dù không có outer advice nào của task mới. `finally` và `remove()` ở scope gốc là quan trọng để tránh lỗi logic lẫn memory retention.

## 19. Có nên dùng `StackWalker` hoặc stack trace để đếm depth?

Không nên xem việc đi qua Java stack trace là lời giải chung cho AOP control-flow depth.

- Stack trace có thể gồm proxy, reflection, compiler-generated và helper frames.
- Việc JIT tối ưu, inlining và lựa chọn runtime có thể ảnh hưởng cách quan sát/chi phí.
- Cùng method có thể được gọi từ nhiều đường khác nhau.
- Stack trace extraction có chi phí cao nếu diễn ra ở mọi recursive level.
- Một pointcut cflow không nhất thiết được triển khai bằng cách đọc stack trace mỗi lần.
- Async/distributed flows không nằm trên cùng một Java stack.

`StackWalker` là API Java có thể hữu ích cho những tình huống diagnostics, nhưng **không thay thế một semantics AOP cflow đã được định nghĩa**.

### Tail-call optimization

Java/JVM **không bảo đảm tổng quát việc tối ưu tail calls** như một phần contract mà mọi chương trình có thể dựa vào để đệ quy vô hạn không tốn stack. Đừng kết luận thuật toán an toàn với input không giới hạn chỉ vì một implementation có thể tối ưu một vài trường hợp.

Một around advice được chèn vào còn có thể thay shape của call stack/performance. **Không dùng call-stack depth quan sát được làm chuẩn duy nhất cho business recursion depth.**

## 20. Recursion qua async: không còn một stack đồng bộ

```text
Thread A:
  process(3)
     submit process(2)
     return

Thread B:
  process(2)
     submit process(1)
     return

Thread C:
  process(1)
```

Về nghiệp vụ có một chuỗi công việc đệ quy, nhưng tại runtime nó **không nằm trên một call stack đồng bộ duy nhất**. Một `ThreadLocal` ở A không tự xuất hiện ở B/C. Một `cflow` dựa trên call stack đồng bộ không tự bao phủ continuation qua executor.

Muốn nối operations async, cần:

- Correlation/trace ID có contract.
- Explicit context carrier hoặc propagation phù hợp.
- Parent/child operation references nếu cần.
- Cleanup khi complete/cancel/timeout.
- Chính sách lifecycle khi callback sống lâu hơn request.
- Quy tắc security đối với context đã được truyền.

**Distributed trace/span depth không đồng nghĩa AOP advice nesting depth.** Một span con có thể chạy đồng thời với span khác mà không có quan hệ LIFO.

## 21. Exception unwinding khi đệ quy

Nếu exception phát sinh ở tầng sâu:

```text
level 0 enters
  level 1 enters
    level 2 enters
      level 2 throws
    level 1 observes / rethrows
  level 0 observes / rethrows
caller receives error
```

Tùy loại advice và pointcut, nhiều lớp có thể ghi lỗi **cho cùng một Throwable**. Nếu mỗi tầng ghi full stack trace, số log có thể tăng rất nhanh. Cần phân biệt:

- **Một exception object** lan qua nhiều advice activations.
- **Nhiều exception objects** nếu các tầng wrap/translate lỗi.
- **Một business failure** có thể tạo nhiều error events ở các depth.
- **Một cleanup failure** có thể che lỗi gốc.

### Preserve lỗi khi unwind

```java
// Pseudocode trong around advice.
try {
    return proceed();
} catch (Throwable original) {
    recordFailureAtThisDepth(original);
    throw original;
} finally {
    restoreDepth();
}
```

Việc `throw original` giữ loại, cause, identity và trace của lỗi gốc theo Java semantics. Nếu advice tạo wrapper mới ở mỗi recursive level, cause chain có thể tăng theo depth mà không thêm ý nghĩa mới. Hãy map tại boundary thích hợp, không wrap tự động mọi tầng.

### Không suy ra “mỗi advice.throw = một lỗi nghiệp vụ mới”

Trong một recursive unwind, cùng một failure có thể được quan sát ở nhiều level. Nếu metric “business failures” tăng ở mọi level, nó có thể **đếm trùng**. Cân nhắc chỉ ghi final failure ở root, còn từng tầng có event kỹ thuật riêng nếu cần debug.

## 22. Security và recursion: guard không được làm mất kiểm tra bắt buộc

Một số advice dùng để kiểm tra quyền, validation hoặc những bất biến nghiệp vụ. Nếu dùng guard:

```java
// Pseudocode có thể nguy hiểm nếu chặn toàn bộ advice bắt buộc.
if (depth > 0) {
    return proceed();
}
checkPermission();
return proceed();
```

Các recursive executions ở depth > 0 bỏ qua `checkPermission`. Điều này **chỉ hợp lý nếu policy chứng minh kiểm tra một lần ở root là đủ cho toàn bộ những thao tác bên trong**. Nếu inner recursion truy cập đối tượng khác, tenant khác hoặc tài nguyên có quyền khác, cách làm trên có thể vi phạm bảo mật.

**Nên phân biệt guard chống log trùng** với **guard tắt một security decision**. Một aspect audit bắt buộc cũng cần kiểm tra coverage của mọi entry point: proxy/self-invocation bypass, load-time weaving scope và async boundaries có thể ảnh hưởng.

Một số kiểm tra nên đặt ở boundary không thể bị caller không tin cậy bypass, thay vì chỉ dựa vào giả định “aspect luôn bao phủ mọi đường”.

## 23. Những sai lầm thường gặp

1. **“Method đệ quy N mức thì advice luôn chạy N lần.”** Proxy/self-invocation và pointcut có thể khác.
2. **“Một dòng call site nghĩa là advice chỉ chạy một lần.”** Một call instruction có thể chạy nhiều occurrence.
3. **“Execution pointcut và call pointcut tương đương.”** Chúng nằm ở hai vị trí khác nhau.
4. **“`this.recursive()` luôn đi qua wrapper proxy.”** Không với Java Dynamic Proxy bao target riêng.
5. **“Mọi self-invocation của mọi kiểu proxy đều bypass.”** Quá rộng; còn tùy dispatch/interception model.
6. **“Depth của advice là depth của thuật toán.”** Chỉ có thể trùng khi coverage và quy ước phù hợp.
7. **“`cflow` là một bộ đếm nguyên.”** Nó là khái niệm điều kiện luồng điều khiển; nesting count riêng.
8. **“`cflowbelow(P)` không bao giờ khớp một occurrence P.”** Inner recursive P có thể nằm dưới ancestor P.
9. **“Chỉ cần `!cflowbelow(P)` là mọi thứ tự động được weave.”** Nó không sửa coverage bị thiếu.
10. **“Đệ quy chỉ là gọi cùng method.”** Mutual recursion đi qua nhiều method/object.
11. **“Một ThreadLocal slot tự xử lý nested context.”** Inner có thể ghi đè; cần stack/restore.
12. **“`volatile depth` đúng cho mọi thread.”** Một global counter không phản ánh nesting riêng từng thread/flow.
13. **“Tăng depth trong before nhưng không giảm khi exception.”** Dẫn tới depth rò sang lời gọi mới.
14. **“Một synchronized block sửa được sai scope.”** Lock không tạo invocation state riêng.
15. **“Per-flow aspect chắc giữ current argument an toàn.”** Nhiều tầng trong flow vẫn dùng cùng instance.
16. **“Một around advice chạy = một target execution.”** Around có thể skip hoặc gọi proceed nhiều lần.
17. **“Retry depth và recursion depth giống nhau.”** Số lần thử lại là trục khác.
18. **“Inclusive time của các tầng cộng lại bằng wall elapsed.”** Inner bị cộng lặp trong outer.
19. **“Log một lần ở root luôn tốt nhất.”** Debug/per-level accounting có thể cần từng tầng.
20. **“Log mọi tầng luôn vô hại.”** Deep recursion có thể làm bùng nổ event/chi phí.
21. **“Advice tự gọi logging helper thì helper không bao giờ bị intercept.”** Có thể tạo recursion trong chính aspect.
22. **“Guard chống recursion được phép bỏ qua permission check.”** Phải có chứng minh policy.
23. **“ThreadLocal tự truyền qua executor.”** Thread khác có context riêng.
24. **“Một recursive call async vẫn là một stack LIFO.”** Nhiều tasks có thể chạy đồng thời.
25. **“StackWalker luôn cho AOP cflow semantics.”** Diagnostics stack khác model pointcut.
26. **“JVM chắc luôn tail-call optimize recursion.”** Không có bảo đảm tổng quát.
27. **“Catch StackOverflowError để chữa thuật toán vô hạn.”** Không phải cơ chế kiểm soát resource đáng tin.
28. **“Exception ở ba depth là ba failure độc lập.”** Có thể là một Throwable đang unwind.
29. **“Độ sâu log toàn cục luôn tăng/giảm có trật tự.”** Nhiều thread khiến event interleave.
30. **“Một test factorial qua proxy chứng minh bytecode weaving của engine khác.”** Chỉ chứng minh cơ chế đã thực thi trong test.

## 24. Quy trình kiểm thử độ sâu advice

**Bước 1 — Chọn nghĩa của depth.** Zero-based active advice count, số business frames, hay số flow roots?

**Bước 2 — Xác định loại join point.** Method call hay execution? Caller/callee nào cần được instrument?

**Bước 3 — Xác định object/path.** Lời gọi qua proxy, self-reference, raw target hay execution weaving?

**Bước 4 — Test recursion bình thường.** Với `factorial(3)`, dự đoán ba target executions và advice entries tương ứng.

**Bước 5 — Test direct self-call.** Khẳng định boundary wrapper proxy không thấy inner `this` calls.

**Bước 6 — Test explicit proxy reentry.** Khẳng định nested advice depths 0,1,2.

**Bước 7 — Test return unwinding.** Thứ tự advice exits theo LIFO, kết quả đúng.

**Bước 8 — Test exception unwinding.** Throwable preserved, `finally` restore, không rò depth sau lỗi.

**Bước 9 — Test mutual recursion.** Chọn điểm A/B khác nhau để quan sát số activation.

**Bước 10 — Test guard/filter.** Root-only logging không loại bỏ những checks cần thực hiện ở inner.

**Bước 11 — Test concurrency.** Hai thread gọi cùng target/proxy không làm mất state của nhau.

**Bước 12 — Test async.** Đừng mong ThreadLocal/cflow đồng bộ tự propagate qua executor.

**Bước 13 — Test overhead.** Đo log/metric growth khi depth tăng và tình trạng resource.

### Ma trận cần có

| Tình huống | Kỳ vọng quan sát |
| --- | --- |
| Target recursion 3 tầng qua `this`, wrapper proxy | 3 target executions, 1 advice activation |
| Target recursion 3 tầng quay qua proxy | 3 target executions, 3 advice activations |
| Execution bytecode weaving đầy đủ 3 tầng | Có thể 3 advice activations nếu pointcut match mọi execution |
| Pointcut chỉ chọn root call site | Inner call sites không tự được chọn |
| Root-only flow filter | Chỉ outer P nếu inner P nằm dưới ancestor P |
| Around skip `proceed` | Target có thể không chạy |
| Around retry 3 lần | Có thể 3 target attempts với 1 outer around activation |
| Inner target throw, outer propagates | Một Throwable có thể được quan sát ở nhiều depth |
| Cleanup trong `finally` | Depth trở về 0 sau root |
| Nested advice trên cùng thread | Outer context không bị inner ghi đè |
| Hai threads cùng handler | Thread-local nesting tách theo thread |
| Thread pool tái sử dụng worker | Không còn ThreadLocal cũ sau scope |
| Async task tạo trên executor | Không mặc nhiên nằm trong synchronous cflow |
| Logging helper cũng match pointcut | Phải có scope/exclusion tránh recursion ngoài ý định |
| Deep recursion | Không bùng log và không dựa vào `StackOverflowError` làm policy |

### Không test chỉ bằng số dòng log

Một advice có thể chạy nhưng sampling/filter log không xuất event. Một target có thể chạy nhưng bypass proxy. Một exception được nhìn thấy nhiều lần nhưng vẫn là một failure. Test nên đếm riêng **target entries, advice entries, advice depth, returns, throws, scope cleanup** và kết quả cuối caller.

## 25. Bài tập tự luyện

1. Chạy `RecursionAspectDepthDemo.java` và đối chiếu toàn bộ stdout.
2. Với `factorial(4)`, dự đoán số dòng `target n=` và `advice.enter` trong hai chế độ.
3. Vẽ cây/stack của `factorial(4)`: biểu diễn depth 0,1,2,3 và thứ tự unwind.
4. Vì sao `this.factorial(n-1)` không quay vào Java Dynamic Proxy wrapper trong demo?
5. Thay `self.factorial(n-1)` bằng `this.factorial(n-1)`, giữ handler; output nào biến mất?
6. Sửa demo để in `level = d+1`, so sánh quy ước depth với level.
7. Bỏ `depth.remove()` ở tầng root; suy luận rủi ro giữ state trong thread pool. Chạy lời gọi tiếp theo và xem depth với/không có restore phù hợp.
8. Cố ý chuyển cleanup ra khỏi `finally`, rồi gây exception; đo depth của invocation sau.
9. Thêm một method `even` gọi `odd` và ngược lại; pointcut chỉ chọn `even` sẽ quan sát bao nhiêu activation?
10. Viết pointcut khái niệm cho “chọn P chỉ khi không nằm bên dưới P khác” nếu engine hỗ trợ dynamic control flow.
11. Giải thích vì sao inner occurrence P có thể khớp `cflowbelow(P)` do ancestor P.
12. Một method recursive được dệt execution join point: tại sao tạo object bằng `new` chưa chắc loại bỏ advice?
13. Một pointcut call chỉ dệt caller bên ngoài: tại sao không tự chọn recursive call site bên trong target?
14. Tạo một advice cố ý gọi lại method cùng pointcut. Quan sát số activation tăng và đề xuất exclusion an toàn.
15. Tạo guard chỉ log ở depth=0 nhưng vẫn proceed ở inner. Vì sao không dùng guard đó cho authorization bắt buộc mà thiếu chứng minh?
16. Thêm elapsed-time metric từng level, giải thích vì sao tổng inclusive time lớn hơn root duration.
17. Tính exclusive time khi có hai child invocations tuần tự và tổng time phù hợp; điều gì thay đổi khi hai child chạy song song?
18. Cho một around advice gọi `proceed` hai lần, xác định số advice entries bên ngoài và số target executions.
19. Cho hai advice lồng nhau quanh mỗi recursion level. Phân biệt depth của từng aspect và tổng stack advice frames.
20. Viết test hai thread dùng cùng `TracingHandler`, xác minh depth mỗi thread đều bắt đầu 0.
21. Dùng executor cho recursion theo task; tại sao ThreadLocal depth ở thread caller không xuất hiện ở worker?
22. Một Throwable ném ở depth 3 rồi được rethrow qua depth 2,1,0: làm sao tránh đếm thành bốn business failures?
23. Đưa ra budget giới hạn input recursion thay vì dựa vào advice depth; tại sao đáng tin cậy hơn khi proxy coverage không đầy đủ?
24. Thiết kế trace gồm root event, nested events được sample và một final failure event; giải thích cách tránh nhầm số business operations.
25. Lập checklist kiểm tra call/execution, proxy/weaving scope, `cflow` support, cleanup, concurrency và performance cho một ứng dụng thực.

**Tự kiểm tra:** Recursion depth và advice activation depth khác nhau như thế nào? Vì sao trong demo một thuật toán có ba target executions nhưng có lúc chỉ một advice? Một recursive occurrence P có thể nằm trong `cflowbelow(P)` không? `P && !cflowbelow(P)` cần những điều kiện nào để thật sự chọn root? Khi exception đi ra, vì sao phải khôi phục depth ở `finally`? Có thể dùng `ThreadLocal` làm distributed call depth không? Nếu một around advice retry, số lần `proceed` có bằng số advice activations không?

## Kết luận

**Lời gọi đệ quy làm lộ rõ khác biệt giữa độ sâu của chương trình và độ sâu can thiệp thực tế của AOP.** Một method có thể thực thi nhiều lần nhưng wrapper proxy chỉ quan sát lần đầu nếu inner `this` calls bypass; ngược lại execution weaving hoặc explicit proxy reentry có thể khiến mỗi tầng đệ quy kích hoạt advice. **`cflow`/`cflowbelow`** giúp mô tả vị trí của join point trong dynamic control flow khi engine hỗ trợ, nhưng **không tự bổ sung những join point chưa được weave** và không phải bộ đếm độ sâu universal.

Để thiết kế đúng, hãy xác định **loại join point, đường interception, nghĩa của depth, thứ tự quanh `proceed`, trạng thái riêng mỗi activation và cleanup khi exception**. Quan sát ở root hay mỗi tầng là lựa chọn theo nhu cầu debug, metric và nghiệp vụ; không dùng guard quan sát để bỏ qua kiểm tra bắt buộc. Với recursion có nhiều thread hoặc async, cần context propagation riêng chứ không giả định còn cùng call stack. **Chỉ khi phân biệt các tầng khái niệm ấy, depth mà advice ghi nhận mới có ý nghĩa và đáng tin cậy.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-control-flow-007">Luồng bất đồng bộ, callback và giới hạn suy diễn quan hệ gọi</a>

<details>
<summary>Xem chi tiết</summary>

# Luồng bất đồng bộ, callback và giới hạn suy diễn quan hệ gọi trong AOP

> **Mục tiêu học tập:** Hiểu điểm khác nhau giữa lời gọi đồng bộ, tác vụ bất đồng bộ, callback, continuation và luồng điều khiển động trong Aspect-Oriented Programming (AOP). Phân biệt **đăng ký callback** với **thực thi callback**, **method trả Future** với **công việc hoàn tất**, **quan hệ call-stack** với **quan hệ nhân quả logic**. Nhận ra giới hạn của pointcut dựa trên control flow khi chuyển thread; biết dùng metadata tương quan tường minh, dọn context, xử lý lỗi và kiểm thử. Bài nói về các khái niệm Aspect cơ bản/nâng cao nói chung, không gắn với một framework AOP cụ thể. Hai chương trình Java 17 độc lập có lệnh chạy và đầu ra xác định.

## 1. Vì sao bất đồng bộ thay đổi cách hiểu “ai gọi ai”?

Trong một call stack đồng bộ, khi A gọi B, có thể quan sát:

~~~text
A enters
    B enters
    B returns
A returns
~~~

Trong mô hình bất đồng bộ:

~~~text
Thread T1:
    A submits task
    A returns

Thread T2:
    task starts later
    task performs B
    task completes

Some callback:
    observes completion
~~~

B vẫn có thể thuộc cùng yêu cầu logic với A, nhưng lúc B thực thi, **stack frame của A có thể đã biến mất**. Việc A đã gửi task không chứng minh B đang chạy dưới dynamic call stack của A.

Vì vậy phải tách ba câu hỏi: **ai lập lịch công việc**, **ai đang thực thi trên thread hiện tại**, và **quan hệ logic/nhân quả giữa hai bước được biểu diễn ra sao**.

## 2. Các khái niệm AOP nền tảng

**Join point** là một loại điểm/sự kiện trong mô hình thực thi mà công cụ có thể nhận diện. **Pointcut** là điều kiện chọn điểm. **Advice** là hành vi bổ sung tại điểm được chọn. **Weaving hoặc interception** là cơ chế kết hợp advice với chương trình.

Một số mô hình có pointcut dựa trên **dynamic control flow**: chọn những join point nằm trong phạm vi hoạt động động của một join point khác. Điều đó không tự đồng nghĩa logical flow qua nhiều thread.

Không phải mọi công cụ AOP đều hỗ trợ callback registration, callback execution, task submission, constructor, field access hoặc method call với cùng độ bao phủ. Phải kiểm tra **join point model** và **runtime path** trước khi kết luận một event có thể được advice quan sát.

## 3. Bốn loại quan hệ cần phân biệt

| Quan hệ | Ý nghĩa | Ví dụ |
|---|---|---|
| **Synchronous call** | Lời gọi trong call stack hiện tại | handle gọi validate |
| **Submission** | Đưa công việc vào hệ lập lịch | executor.submit |
| **Completion dependency** | Stage sau phụ thuộc sự hoàn tất của stage trước | thenApply sau supplyAsync |
| **Logical correlation** | Hai hoạt động được gắn cùng ngữ cảnh yêu cầu | cùng traceId |

Cùng traceId **không chứng minh** hai hoạt động có quan hệ caller/callee trực tiếp. Completion dependency **không chứng minh** callback cùng stack với method đăng ký nó.

Một sơ đồ AOP đúng cần xác định **mỗi loại quan hệ dựa trên dữ kiện nào**, không gộp tất cả thành “flow”.

## 4. Đồng bộ: phạm vi cflow còn trên stack

Sơ đồ:

~~~text
process()
   validate()
      check()
   load()
process() returns
~~~

Nếu công cụ AOP có control-flow pointcut, các join point của validate, check hoặc load có thể được chọn theo phạm vi process **khi process đang hoạt động**, theo chính xác định nghĩa của công cụ.

Các lưu ý:

- Pointcut theo control flow là điều kiện **động**, không chỉ nhìn biểu đồ gọi tĩnh.
- Các nhánh không được thực thi không tạo join point runtime tương ứng.
- Recursive/nested calls có nhiều invocation của cùng method.
- Một around advice phía ngoài có thể short-circuit khiến điểm phía trong không xuất hiện.
- Một số mô hình phân biệt tính cả điểm mở đầu control flow với chỉ tính các điểm phía dưới, thường bằng các biểu thức như cflow/cflowbelow.

Tên và ngữ nghĩa các biểu thức đó **không phải API chung của mọi AOP runtime**.

## 5. Chuyển thread: cflow không mặc nhiên đi theo task

Khi A submit Runnable rồi return:

~~~text
T1 stack: A → executor.submit(...) → return
T2 stack: workerLoop → task.run() → work()
~~~

Task chạy trên T2 có **call stack khác**. Một pointcut quan sát động cflow của A trên T1 không tự mở rộng sang T2 chỉ vì task đã được A tạo.

Một engine có thể có extension hỗ trợ ngữ cảnh bất đồng bộ, nhưng phải xem hợp đồng của extension. Nếu không, cần một **correlation/scope propagation** rõ ràng, độc lập với ngữ nghĩa call stack.

**Logical request lifetime có thể dài hơn lifetime của từng method invocation hoặc từng thread.**

## 6. Method call khác method execution

Trong code bất đồng bộ có những sự kiện khác nhau:

~~~text
Caller calls executor.submit(task)
Executor processes submission
Worker later calls task.run()
Task executes work()
~~~

Nếu mô hình hỗ trợ cả method call và method execution, mỗi sự kiện có thể có join point riêng. Một aspect trên **method call của submit** không tự động là aspect trên **method execution của run**.

Một proxy bao executor có thể quan sát việc submit nhưng không chắc quan sát code bên trong Runnable. Một cơ chế weaving method execution có thể quan sát task.run khi bytecode ấy đã được dệt, nhưng vẫn không tự suy ra request cha từ thread khác.

Do đó **độ bao phủ can thiệp** và **gắn quan hệ nhân quả** là hai bài toán độc lập.

## 7. Callback registration khác callback execution

Xét:

~~~text
future.thenApply(callback)   // registration
...
callback.apply(value)        // execution
~~~

Registration không đồng nghĩa execution. Callback có thể chạy sau, trên thread khác, hoặc nhánh callback kiểu success có thể không chạy nếu upstream future hoàn tất exceptionally.

Khi viết advice, hỏi:

1. Đang chọn lời gọi API đăng ký callback?
2. Đang chọn method execution của callback?
3. Đang chọn event future hoàn tất?
4. Đang chọn kết quả cuối của cả chuỗi?

Bốn event này không thể tự thay nhau. Một log “callback registered” không chứng minh callback đã chạy, càng không chứng minh nó đã thành công.

## 8. Callback không bảo đảm luôn có thread mới

Với CompletableFuture, các dependent actions dạng không Async có thể chạy trên một thread tham gia completion hoặc thực hiện dependent registration theo hợp đồng API. Những dạng Async sử dụng facility/executor được xác định.

Đừng suy rằng:

- thenApply luôn chạy trên thread đăng ký.
- thenApplyAsync luôn mở thread mới cho từng callback.
- Callback nào đăng ký trước chắc chắn thực thi trước.
- Một callback đang chạy có cùng ThreadLocal context với caller.
- Callback được đăng ký một lần thì phần thân chắc chắn được vào một lần trong mọi kết quả completion.

Hãy dùng executor và ranh giới completion thật để xác định điều được phép kết luận.

## 9. Method trả Future chưa phải task hoàn tất

Một operation có thể:

~~~text
submitAsync()
    schedules task
    returns CompletableFuture immediately

later:
    task starts
    task finishes
    future completes
~~~

Advice quanh method submitAsync có thể thấy **normal return** ngay khi trả handle. Nếu task sau đó ném exception, một after-throwing advice chỉ quanh method submitAsync không nhất thiết thấy lỗi ấy.

Cần phân biệt:

- **Submission latency:** thời gian caller thực hiện lời gọi lập lịch.
- **Execution duration:** thời gian worker thực sự chạy task.
- **End-to-end latency:** thời gian từ root boundary tới logical completion đã định nghĩa.

Không đặt tên metric end-to-end nếu advice chỉ đo tới lúc method trả Future.

## 10. ThreadLocal không tự là context của request async

Java ThreadLocal gắn giá trị với **thread hiện tại**. Nếu caller T1 set requestId=R1 rồi submit task cho T2, ThreadLocal thường trên T2 không tự có R1.

~~~text
T1: context=R1
    submit task

T2: context may be absent
    execute task
~~~

T2 còn có thể mang **stale context từ task trước** nếu code quên cleanup trên thread pool.

Vì vậy cần:

- Capture một snapshot được phép ở ranh giới submit.
- Truyền tường minh snapshot tới task/callback.
- Worker tự cài scope trên thread thực thi.
- Worker dọn scope trong finally hoặc cơ chế tương đương khi hoàn tất.
- Không cố remove entry của worker từ caller thread.

Correlation ID được sao chép đúng không đồng nghĩa dynamic cflow pointcut đã mở rộng qua thread.

## 11. Ví dụ Java 17 số 1: giới hạn ThreadLocal qua executor

Chương trình dùng một executor có một worker, để thứ tự output xác định. Nó cho thấy lời gọi đồng bộ thấy context, task không bọc không tự thấy, task có snapshot cài context đúng rồi remove.

Đây là Java thuần, **không phải một engine weaving**.

Lưu **AsyncBoundaryDemo.java**:

~~~java
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncBoundaryDemo {
    private static final ThreadLocal<String> CONTEXT =
            new ThreadLocal<>();

    static void synchronousWork() {
        System.out.println("[sync] context=" + CONTEXT.get());
    }

    public static void main(String[] args) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            CONTEXT.set("REQ-1");
            try {
                System.out.println("[main] context=" + CONTEXT.get());
                synchronousWork();

                executor.submit(() ->
                    System.out.println("[worker-unwrapped] context="
                            + CONTEXT.get())
                ).get();

                String snapshot = CONTEXT.get();
                executor.submit(() -> {
                    CONTEXT.set(snapshot);
                    try {
                        System.out.println("[worker-wrapped] context="
                                + CONTEXT.get());
                    } finally {
                        CONTEXT.remove();
                        System.out.println("[worker-wrapped] cleared="
                                + CONTEXT.get());
                    }
                }).get();

                System.out.println("[main] after-submit="
                        + CONTEXT.get());
            } finally {
                CONTEXT.remove();
                System.out.println("[main] cleared=" + CONTEXT.get());
            }
        } finally {
            executor.shutdown();
        }
    }
}
~~~

Chạy bằng JDK 17:

~~~bash
javac AsyncBoundaryDemo.java
java AsyncBoundaryDemo
~~~

**Stdout chính xác:**

~~~text
[main] context=REQ-1
[sync] context=REQ-1
[worker-unwrapped] context=null
[worker-wrapped] context=REQ-1
[worker-wrapped] cleared=null
[main] after-submit=REQ-1
[main] cleared=null
~~~

### 11.1. Đọc kết quả

Lời gọi synchronousWork dùng **cùng main thread** nên thấy REQ-1. Task unwrapped chạy trên worker khác nên không có ThreadLocal của main. Task wrapped được truyền snapshot qua lambda và chủ động set trên worker.

Worker remove không ảnh hưởng ThreadLocal entry của main, vì mỗi thread có binding riêng. Main remove trong finally của chính nó.

### 11.2. Giới hạn của demo

Task wrapped dùng set/remove đơn giản vì worker ban đầu không có context cha hợp lệ. Nếu nested scope có previous context, cần **restore previous**, không remove mù quáng.

Ví dụ chỉ truyền một String không nhạy cảm. Trong thực tế, snapshot nên bất biến, nhỏ và chỉ gồm dữ liệu được phép. Context logic cần lifecycle riêng nếu task sống lâu hơn request ban đầu.

## 12. Root scope và nested scope không được dọn giống nhau

Một scope gốc:

~~~text
before: no context
enter A
execute
close A: remove
~~~

Một scope lồng:

~~~text
enter A
    enter B
    close B: restore A
close A: remove
~~~

Nếu mọi advice luôn remove ở cuối, inner B có thể phá context của A trong cùng call stack.

Một scope abstraction nên lưu:

1. Previous value trước khi enter.
2. Owner thread.
3. Installed value.
4. Trạng thái đã đóng.
5. Quy tắc close LIFO.

**ThreadLocal.remove từ thread khác chỉ tác động tới thread đang gọi**, không dọn hộ worker. Với async, mỗi execution boundary nên tự open/close scope.

## 13. InheritableThreadLocal không giải quyết per-task propagation

InheritableThreadLocal có khả năng khởi tạo giá trị cho thread con khi thread được tạo. Thread pool lại thường giữ worker sống lâu và giao rất nhiều task sau đó.

Một value được kế thừa lúc tạo worker **không đại diện chắc chắn cho request hiện tại** khi worker chạy task thứ mười nghìn.

Không xem InheritableThreadLocal là cơ chế truyền ngữ cảnh cho mỗi submit mặc định. Cần kiểm tra thời điểm tạo thread, ranh giới task, capture, cleanup và nguồn dữ liệu.

## 14. CompletableFuture có nhiều ranh giới completion

Một chuỗi:

~~~text
Caller registers supplier
    |
supplyAsync returns future F
    |
Worker executes supplier
    |
F completes normally or exceptionally
    |
dependent callback becomes eligible
    |
callback executes
    |
downstream future G completes
~~~

Điều quan trọng:

- F trả về caller trước khi F hoàn tất là điều bình thường.
- Callback được **đăng ký** không có nghĩa đã chạy.
- Một nhánh success callback có thể không thực thi khi F exceptional.
- Callback có thể tự lỗi, khiến G exceptional dù F normal.
- Callback recovery có thể khiến G normal dù F exceptional.
- G có outcome riêng, không chỉ là “một log bổ sung” của F.

Để đo logical request, phải chọn đúng future/stage đại diện cho ranh giới completion cần quan sát.

## 15. TraceId, spanId và parent không tự suy ra từ stack khác thread

Một mô hình quan sát có thể có:

~~~text
traceId = T-1
root span = R
task span = A, parent=R
callback span = C, parent=A
~~~

Các quan hệ parent được **mô hình hóa tường minh**, không tự xuất hiện từ việc task có cùng traceId.

Hai events cùng traceId chỉ chứng minh chúng được gắn cùng mã tương quan trong dữ liệu. Từ đó không tự suy:

- Event thứ nhất gọi trực tiếp event thứ hai.
- Chúng chạy trên cùng thread.
- Chúng thuộc cùng dynamic control flow pointcut.
- Chúng có quan hệ parent-child.
- Cả hai thành công.
- Metadata có nguồn tin cậy.

Với fan-in, một event có thể phụ thuộc nhiều nguồn và cần **links** hoặc mô hình dependency khác thay vì gán bừa một parent duy nhất.

## 16. Ví dụ Java 17 số 2: callback và causal metadata tường minh

Ví dụ dùng record bất biến mang traceId/spanId/parentSpanId qua các stage, rồi minh họa exceptional completion và recovery.

Lưu **AsyncCallbackTraceDemo.java**:

~~~java
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncCallbackTraceDemo {
    record Trace(String traceId, String spanId, String parentSpanId) {}

    record StepResult(String value, Trace trace) {}

    static String describe(Trace t) {
        return "trace=" + t.traceId()
                + " span=" + t.spanId()
                + " parent=" + t.parentSpanId();
    }

    static Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException
                && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }

    public static void main(String[] args) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Trace root = new Trace("T-1", "root", "-");
            System.out.println("[caller] " + describe(root));

            CompletableFuture<StepResult> task =
                    CompletableFuture.supplyAsync(() -> {
                        Trace child = new Trace("T-1", "task", "root");
                        System.out.println("[task] " + describe(child));
                        return new StepResult("OK", child);
                    }, executor);

            CompletableFuture<String> callback =
                    task.thenApplyAsync(result -> {
                        Trace next = new Trace(
                                result.trace().traceId(),
                                "callback", result.trace().spanId());
                        System.out.println("[callback] "
                                + describe(next));
                        return result.value() + "-DONE";
                    }, executor);

            System.out.println("[caller] result=" + callback.join());

            CompletableFuture<String> failed =
                    CompletableFuture.supplyAsync(() -> {
                        System.out.println("[failed-task] execute");
                        throw new IllegalStateException("boom");
                    }, executor);

            CompletableFuture<String> recovered =
                    failed.handleAsync((value, error) -> {
                        if (error != null) {
                            Throwable cause = unwrap(error);
                            System.out.println("[error-callback] "
                                    + cause.getClass().getSimpleName()
                                    + ": " + cause.getMessage());
                            return "RECOVERED";
                        }
                        return value;
                    }, executor);

            System.out.println("[caller] error-result="
                    + recovered.join());
        } finally {
            executor.shutdown();
        }
    }
}
~~~

Biên dịch và chạy trên JDK 17:

~~~bash
javac AsyncCallbackTraceDemo.java
java AsyncCallbackTraceDemo
~~~

**Stdout chính xác:**

~~~text
[caller] trace=T-1 span=root parent=-
[task] trace=T-1 span=task parent=root
[callback] trace=T-1 span=callback parent=task
[caller] result=OK-DONE
[failed-task] execute
[error-callback] IllegalStateException: boom
[caller] error-result=RECOVERED
~~~

### 16.1. Cha–con là metadata đã thiết kế

Task có parent=root **vì code đặt vậy**; callback có parent=task **vì StepResult đưa metadata từ task sang callback**. Không có thao tác suy từ call stack của main sang worker.

Đây là ví dụ về cách xây một **correlation model có chủ đích**, chứ không chứng minh mọi async callback phải là span con của future supplier.

### 16.2. Vì sao error callback thấy boom?

Supplier thất bại, failed future hoàn tất exceptionally. handleAsync nhận error, unwrap một lớp CompletionException nếu có và ghi IllegalStateException: boom.

Sau khi handleAsync trả RECOVERED, **downstream future hoàn tất bình thường**. Điều này không làm task gốc “chưa từng thất bại”.

Nên phân biệt **attempt failure** với **recovered final outcome**.

### 16.3. Vì sao stdout ổn định?

Single-thread executor thực thi các task được xếp lịch; caller join vào downstream future trước khi lập lịch nhánh lỗi. Do đó thứ tự các dòng trong ví dụ được xác định mà không cần đoán thread scheduling.

Không suy từ điều này rằng callback của mọi thư viện hoặc các tác vụ trên nhiều worker luôn có cùng thứ tự.

## 17. CompletionException và ExecutionException

Trong Java, lời gọi CompletableFuture.join có thể ném CompletionException bao lỗi gốc. Future.get có thể ném ExecutionException cùng các checked exceptions khác theo API.

Nếu một advice chỉ log lớp wrapper, người đọc có thể không biết lỗi thực sự bắt đầu ở task hay callback nào.

Cần kiểm tra **cause chain** phù hợp, nhưng cũng phải biết exception mới có thể được tạo ở callback và trở thành nguyên nhân của downstream failure.

Đừng dùng việc outer method trả bình thường để kết luận không có async exception. **Exception của task thường được lưu ở future hoặc truyền qua completion callback**, không nhất thiết thoát qua stack của caller khi submit.

## 18. Recovery và observability phải phân biệt ranh giới

Giả sử một task ném IOException E; callback recovery đọc lỗi rồi trả fallback.

~~~text
Task A: FAILED(E)
    |
Recovery callback: returns FALLBACK
    |
Request result stage: NORMAL(FALLBACK)
~~~

Một observer của task có thể ghi failed, trong khi một observer của stage cuối ghi recovered success. Hai kết luận không mâu thuẫn nếu được đặt tên rõ.

Tuy nhiên, fallback phải có nghĩa nghiệp vụ. Một callback không được “nuốt lỗi” chỉ vì muốn giảm số log nếu caller cần exception để rollback, retry hoặc báo lỗi.

## 19. Fan-out tạo nhiều nhánh không có caller trực tiếp

Một request có thể submit hai tasks độc lập:

~~~text
            Request R
             /     \
          Task A   Task B
             \     /
             Combine
~~~

Task A không nhất thiết gọi Task B. Dù log A xuất hiện trước log B, thứ tự log chưa chứng minh quan hệ trực tiếp.

Bước Combine phụ thuộc cả A và B, vì vậy một mô hình chỉ cho một parent không biểu diễn đủ mọi dependency. Có thể dùng links hoặc một causal graph phù hợp.

Không dùng timestamp đơn lẻ để tự phát minh causal edge: scheduling, buffering, clock accuracy và đồng bộ hóa đều ảnh hưởng thứ tự quan sát.

## 20. Fan-in, allOf và điều kiện hoàn tất

Khi nhiều futures được kết hợp, phải phân biệt:

- Từng task đã **bắt đầu** chưa.
- Từng future đã **hoàn tất** chưa.
- Kết quả normal hay exceptional.
- Bước aggregate có thật sự chạy không.
- Aggregate có chỉ chạy khi các dependency normal?
- Caller chờ future nào: một nhánh hay kết quả tổng hợp?

Một advice quan sát submit của Task A chưa đủ chứng minh Task B đã hoàn tất, và cũng chưa đủ để đánh dấu request end-to-end thành công.

Nếu aggregate future exceptional, kiểm tra nguyên nhân từ dependency hoặc callback thay vì gán lỗi cho method submit.

## 21. Multiple callbacks không mặc nhiên là một chuỗi

Một future có thể có callback A, B, C **đăng ký độc lập**. Chúng có thể là ba dependent actions trên cùng upstream stage, không phải A gọi B rồi B gọi C.

~~~text
Future F
  +-- callback A
  +-- callback B
  +-- callback C
~~~

Đừng lập parent span theo thứ tự log A, B, C rồi suy chuỗi A→B→C. Nếu chúng là sibling dependencies, model phải phản ánh mối quan hệ ấy.

Một callback object được đăng ký tại nhiều nơi cũng khác với **một lần execution cụ thể** của callback.

## 22. Retry có thể tạo nhiều task attempts

Một logical request R có thể:

~~~text
R
 +-- Attempt 1: throws E
 +-- Attempt 2: returns OK
 +-- Final outcome: OK
~~~

Nếu metric đếm request và metric đếm task attempts, hai số có thể khác.

Một around advice bên ngoài retry có thể chạy một lần, trong khi advice bên trong retry được vào nhiều lần. Với retry bất đồng bộ, các attempts có thể chạy trên những thread khác nhau và có future riêng.

Một attempt thất bại không chứng minh mọi side effect của nó đã được hoàn tác. Retry cần chính sách idempotency và exception classification rõ.

## 23. Timeout và cancellation không chứng minh task đã dừng

Caller có thể timeout tại T1, nhưng task vẫn chạy tới T2.

~~~text
Caller stops waiting at T1
Worker finishes at T2
T1 precedes T2
~~~

Không được suy khi Future.cancel được gọi thì mọi join point của task đã ngừng xuất hiện ngay.

Nếu policy dùng ThreadLocal cho worker, cleanup phải được thực hiện khi **worker scope thực sự kết thúc**, không phải bằng ThreadLocal.remove từ caller thread.

Tracing nên phân biệt các event như cancel_requested, task_cancelled và task_completed_after_timeout nếu phù hợp.

## 24. Happens-before không phải call-stack ancestry

Một số API đồng bộ Java có **memory consistency guarantees**; ví dụ Future.get trong ExecutorService giúp quan sát kết quả/tác dụng bộ nhớ sau khi task hoàn tất theo hợp đồng.

Nhưng:

- **Happens-before** là quan hệ trong Java Memory Model.
- **Completion dependency** là quan hệ giữa stages theo API.
- **Stack ancestry** là quan hệ caller/callee đang hoạt động.
- **Trace parent/link** là mô hình quan sát được thiết kế.

Chúng không tự thay nhau. Một task có thể có một kết quả được công bố an toàn cho caller mà không bao giờ nằm trên cùng call stack với caller.

## 25. Proxy/weaving và callback coverage

Một proxy chặn executor.submit có thể chỉ quan sát **submission**. Nếu task.run không đi qua proxy, advice của proxy trên submit không tự ghi nhận execution trong task.

Một bytecode weaving engine có thể quan sát task.run nếu class và loại method execution được hỗ trợ, nhưng điều đó vẫn chưa cho biết **request logic nào đã tạo task**.

Đặc biệt, self-invocation, method final/static/private và class loading có thể ảnh hưởng một số cơ chế proxy hoặc weaving khác nhau. Không áp dụng một quy tắc duy nhất cho mọi engine.

Cần kiểm thử **đường gọi thật**, không chỉ biểu thức pointcut.

## 26. Security và dữ liệu context

Trace context có thể chứa actor hoặc tenant. Một snapshot đưa sang worker phải được kiểm duyệt:

- Không tự sao chép token, cookie hoặc raw payload khi không cần.
- Actor/tenant phải đến từ nguồn được xác thực.
- Không dùng traceId trùng như chứng cứ authorization.
- Không giữ object graph lớn qua future có lifetime dài.
- Không dùng static mutable currentRequest trong aspect singleton.
- Không để request B nhận stale context của A vì thread pool reuse.
- Không đưa requestId của từng event vào metrics label có cardinality vô hạn.

Record bất biến giúp giảm rủi ro shared mutation nhưng **không tự bảo đảm nguồn metadata đáng tin**.

## 27. Ma trận kiểm thử bắt buộc

| Tình huống | Điều kiểm tra |
|---|---|
| Đồng bộ A gọi B | Stack relation đúng tại runtime |
| A submit task rồi return | Task execution khác submission |
| Method trả Future | Không đồng nhất với task completed |
| Callback registration | Không đếm như callback execution |
| Worker ThreadLocal mặc định | Không tự copy context caller |
| Task wrapper có snapshot | Worker thấy context đúng |
| Worker finally cleanup | Task tiếp theo không thấy stale context |
| Callback đã đăng ký trên future thất bại | Success transform không bị đánh dấu đã chạy |
| handle recovery | Upstream failed, downstream normal |
| Callback tự ném lỗi | Downstream future exceptional |
| Fan-out A/B | Không bịa A là parent B |
| Fan-in aggregate | Quan hệ nhiều dependencies được biểu diễn |
| Retry hai attempts | Request count khác attempt count |
| Timeout trước task stop | Không ghi task hoàn tất sai |
| Callback trên executor khác | Scope mở/đóng đúng thread |
| Direct target call | Không mặc định đi qua proxy |
| Data context nhạy cảm | Snapshot tối thiểu, nguồn đáng tin |
| Nested context | Restore previous đúng LIFO |

Với concurrent tests, tránh assert một total order chưa được API bảo đảm. Hãy assert **quan hệ thực sự được bảo đảm** và những dữ liệu có ý nghĩa nghiệp vụ.

## 28. Quy trình chẩn đoán khi quan hệ gọi sai

1. Xác định event là call, execution, submit, registration, callback invocation hay completion.
2. Kiểm tra loại join point được engine hỗ trợ.
3. Xác minh call đi qua proxy hoặc bytecode đã được dệt.
4. Xác định thread nào đang chạy từng event.
5. Xác định future/stage nào là upstream/downstream.
6. Kiểm tra callback được đăng ký lúc nào và có thực sự chạy không.
7. Kiểm tra executor và dạng callback Async/non-Async.
8. Tìm nơi capture và propagate context.
9. Kiểm tra scope cleanup và stale ThreadLocal.
10. Phân biệt traceId với spanId và parent/link.
11. Kiểm tra fan-out/fan-in và nhiều dependencies.
12. Phân biệt attempt failure với final outcome.
13. Xem exception cause và completion wrapper.
14. Kiểm tra cancellation/timeout khi task vẫn chạy.
15. Tạo test tái hiện có executor/scope rõ ràng.

Không chỉ sửa thứ tự logging cho “đúng” nếu metadata về causal edges vẫn sai.

## 29. Những ngộ nhận thường gặp

**Ngộ nhận 1:** callback đăng ký đồng nghĩa callback chạy.

**Ngộ nhận 2:** method trả Future nghĩa là task xong.

**Ngộ nhận 3:** dynamic control flow của caller tự kéo dài qua thread pool.

**Ngộ nhận 4:** ThreadLocal tự mang context tới worker.

**Ngộ nhận 5:** InheritableThreadLocal tự truyền đúng value cho từng task trong pool.

**Ngộ nhận 6:** cùng traceId chứng minh trực tiếp caller/callee.

**Ngộ nhận 7:** log A trước B chứng minh A tạo B.

**Ngộ nhận 8:** mọi callbacks của một future có parent là callback trước.

**Ngộ nhận 9:** chỉ cần chặn submit thì đã đo task execution duration.

**Ngộ nhận 10:** after-returning của method submit chứng minh logical work succeeded.

**Ngộ nhận 11:** after-throwing quanh submit tự thấy exception task ném sau này.

**Ngộ nhận 12:** retry chỉ lặp business, không lặp advice bên trong.

**Ngộ nhận 13:** Future.cancel chắc chắn ngừng task lập tức.

**Ngộ nhận 14:** quan hệ happens-before tự định nghĩa trace parent.

**Ngộ nhận 15:** task.run được weave tự có parent request metadata.

Các lỗi này có chung gốc: **đồng nhất các boundary khác nhau**.

## 30. Checklist thiết kế advice cho hệ bất đồng bộ

- [ ] Đã định nghĩa event mình cần quan sát chính xác chưa?
- [ ] Pointcut chỉ chọn loại join point được công cụ hỗ trợ?
- [ ] Có phân biệt submit với task start và completion?
- [ ] Có phân biệt callback registration với execution?
- [ ] Có phân biệt method return với Future completion?
- [ ] Có dùng dynamic cflow đúng giới hạn, không suy xuyên thread tùy ý?
- [ ] Có snapshot context được tạo và truyền tại boundary đúng?
- [ ] Worker tự mở và dọn context đúng thread?
- [ ] Nested context restore previous đúng LIFO?
- [ ] Có phân biệt parent với link và correlation?
- [ ] Fan-out/fan-in được mô hình hóa đúng dependencies?
- [ ] Recovery và retry được tính đúng theo attempts/outcomes?
- [ ] Có theo dõi exceptional completion và cause?
- [ ] Timeout/cancel không bị coi là task stop tức thời?
- [ ] Không ghi dữ liệu nhạy cảm hoặc raw context quá mức?
- [ ] Tests không giả định thread scheduling/order vô căn cứ?
- [ ] Có integration test về proxy/weaving coverage thực tế?

## 31. Bài tập tự kiểm tra

### Câu 1: cflow và executor

A submit task rồi return. Task chạy trên thread khác sau đó. Có mặc định task thuộc dynamic cflow đang hoạt động của A không?

**Đáp án:** không. Scheduling relation và call-stack extent là hai khái niệm khác nhau.

### Câu 2: Callback

Log cho thấy đã đăng ký thenApply nhưng future upstream hoàn tất exceptionally. Có chứng minh transform chạy không?

**Đáp án:** không. Callback normal thường không được vào trên exceptional path.

### Câu 3: Method return

Advice quanh submitWork thấy return Future bình thường. Có chắc work đã xong?

**Đáp án:** không. Task có thể chạy sau và thất bại.

### Câu 4: TraceId

A và B có traceId=T1. Có chắc A trực tiếp gọi B?

**Đáp án:** không. Cần parent/link hoặc causal edge có bằng chứng.

### Câu 5: ThreadLocal

Caller set context=R1, worker dùng cùng ThreadLocal key. Worker có tự nhận R1 không?

**Đáp án:** ThreadLocal thường không tự chuyển value qua thread. Cần propagation thích hợp.

### Câu 6: Fan-in

Một callback kết hợp hai futures A/B. Có phải callback chỉ cần parent=A vì A log trước B?

**Đáp án:** không. Nó phụ thuộc cả hai; có thể cần links/graph dependencies.

### Câu 7: Recovery

Task ném lỗi, handle callback trả RECOVERED. Có phải task thành công?

**Đáp án:** không. Upstream thất bại, downstream được phục hồi.

### Câu 8: Retry

Một request retry ba attempts. Số request và số attempts có cần bằng nhau?

**Đáp án:** không.

### Câu 9: Timeout

Caller timeout trước khi worker dừng. Có thể đánh dấu task hoàn tất ngay lúc caller timeout không?

**Đáp án:** không nếu metric định nghĩa task actual completion.

### Câu 10: Proxy

Proxy chặn submit nhưng callback thực thi bên ngoài proxy. Advice trên submit có tự đo callback execution không?

**Đáp án:** không. Cần instrumentation đúng boundary thực thi.

### Câu 11: Happens-before

Một Future.get hoàn tất và có memory guarantee. Có tự chứng minh worker đang nằm trên stack của caller không?

**Đáp án:** không. Memory ordering khác call-stack ancestry.

### Câu 12: Cleanup

Task A để ThreadLocal=R1 trên pool worker. Task B chạy sau. Nguy cơ gì?

**Đáp án:** B có thể dùng stale context R1. Cần finally/Scope cleanup trên worker.

## 32. Tổng kết

**Luồng bất đồng bộ, callback và continuation làm thay đổi cách diễn giải quan hệ gọi**: logical request có thể tiếp tục sau khi method caller đã trả và trên các thread khác nhau. Không nên lấy quan hệ call stack trong một thread để suy mọi causal edge của tác vụ đã lập lịch.

Các nguyên tắc chính:

- **Registration, execution và completion là ba ranh giới khác nhau.**
- **Method trả Future không đồng nghĩa Future đã hoàn tất.**
- **Dynamic cflow không mặc định đi theo async task qua thread.**
- **ThreadLocal cần propagation và cleanup rõ trên mỗi execution boundary.**
- **TraceId chung chỉ là correlation, không tự chứng minh parent–child.**
- **Fan-out/fan-in tạo graph dependencies, không phải luôn một cây caller/callee.**
- **Retry, recovery, exception, cancellation và timeout khiến outcome của từng tầng khác nhau.**
- **Pointcut/weaving coverage và metadata nhân quả phải được xác minh riêng.**
- **Kiểm thử nên chứng minh event boundary, task count, callback execution, cause và scope isolation**, không chỉ có hai dòng log cùng một requestId.

**Câu hỏi quan trọng trước khi viết một async aspect:** *Tôi đang quan sát lời gọi đồng bộ, sự kiện lập lịch, task execution, callback invocation hay logical completion — và bằng chứng nào cho phép tôi nối sự kiện này với phần còn lại của công việc?*


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-control-flow-008">Chi phí theo dõi ngữ cảnh động và phương án đơn giản hơn</a>

<details>
<summary>Xem chi tiết</summary>

# Chi phí theo dõi ngữ cảnh động và phương án đơn giản hơn trong AOP

## 1. Bài toán: chỉ áp advice khi đang ở trong một luồng gọi cụ thể

Trong **Aspect-Oriented Programming (AOP)**, một pointcut có thể lựa chọn join point dựa trên những thuộc tính tương đối **tĩnh** như loại method, tên/signature, annotation hoặc vị trí lexical trong mã nguồn. Nhưng đôi khi yêu cầu lại nói về **lịch sử gọi hàm đang hoạt động ở runtime**:

- Ghi log các thao tác khi đang xử lý một lệnh nhập dữ liệu, nhưng không ghi cùng thao tác nếu nó được gọi từ nơi khác.
- Đo các lệnh truy cập tài nguyên phát sinh **trong luồng điều khiển** của một yêu cầu cha.
- Áp dụng một chính sách chỉ khi một scope nghiệp vụ cụ thể đã được kích hoạt.
- Gắn trace hoặc correlation context cho những method được gọi gián tiếp nhiều tầng.
- Phân biệt method được gọi ở top-level với cùng method được gọi do một hoạt động khác dẫn đến.

Ví dụ:

~~~text
User request
  |
  +--> importBatch()                 <-- điểm kích hoạt scope
          |
          +--> validateRow()
          |      |
          |      +--> normalize()
          |
          +--> persist()
                 |
                 +--> encode()
~~~

Nếu muốn advice cho **normalize** và **encode** chỉ khi chúng nằm trong luồng gọi động do **importBatch** khởi đầu, một pointcut dựa duy nhất vào tên method không đủ phân biệt. Cần kiểm tra **dynamic control-flow context** hoặc thay đổi thiết kế để truyền điều kiện rõ hơn.

Một số ngôn ngữ AOP, ví dụ AspectJ, cung cấp **cflow(pointcut)** và **cflowbelow(pointcut)** để match các join point nằm trong luồng điều khiển động của join point anchor. Đây là một lựa chọn biểu đạt mạnh, nhưng có thể kéo theo chi phí runtime cao hơn phương pháp nhận diện hoàn toàn tĩnh.

**Ý chính:** theo dõi ngữ cảnh động hữu ích khi điều kiện phụ thuộc lịch sử gọi đang hoạt động. Nhưng nó không miễn phí, không phải hình thức duy nhất để biểu đạt cùng yêu cầu và không tự xử lý đúng async/thread boundaries. Cần cân nhắc lợi ích biểu đạt so với overhead, tính dễ kiểm thử và khả năng bảo trì.

Bài trình bày **AOP nói chung**, không giả định một framework ứng dụng cụ thể. Các ví dụ về cflow/cflowbelow được giải thích trong ngữ cảnh một ngôn ngữ AOP hỗ trợ chúng; mã Java 17 runnable mô phỏng scope bằng ThreadLocal để minh họa cơ chế, **không tuyên bố đó là cách mọi weaver cài đặt cflow**.

## 2. Phân biệt pointcut tĩnh và điểm chọn động

| Tiêu chí | Điều kiện tĩnh/lexical | Điều kiện theo luồng điều khiển động |
|---|---|---|
| Quyết định phụ thuộc | Signature, type, annotation, source location hoặc metadata ổn định | Call stack/scope của invocation runtime |
| Có thể xác định phần lớn khi weave? | Nhiều trường hợp có | Chỉ một phần: trạng thái động vẫn cần theo dõi |
| Cùng method được gọi từ hai callers | Thường match giống nhau nếu các điều kiện tĩnh giống | Có thể khác nhau tùy scope đang active |
| Runtime state | Có thể ít hoặc không cần | Có thể cần counter, stack, scope bit, thread context |
| Chi phí | Có cơ hội tối ưu bằng static matching | Có thể tăng chi phí enter/exit/check tại nhiều điểm |
| Độ khó reasoning | Thường dễ theo code location | Cần hiểu call path, nested calls, async, recursion |
| Khả năng thay thế | Annotation, interface boundary, explicit predicate | Parameter/context, riêng service boundary, event correlation |

### 2.1. Tĩnh không có nghĩa hoàn toàn zero-cost

Một pointcut chọn được join point ở compile/weave time vẫn có thể cần advice invocation, đo thời gian, logging, reflection hoặc một runtime guard cho một điều kiện động khác. Chi phí thực phụ thuộc advice body và công nghệ weave. Không nên so sánh “static = 0, dynamic = tốn kém” như một định luật.

### 2.2. Động không đồng nghĩa chậm trong mọi tình huống

Một weaver có thể tối ưu hóa cflow bằng counter hoặc context marker, giới hạn instrumentation, cache trạng thái hay loại bỏ phần kiểm tra không cần thiết. Với số điểm được chọn ít, overhead có thể không đáng kể. **Chỉ benchmark workload thật mới cho câu trả lời đáng tin.**

## 3. Chi phí của cflow bắt đầu từ phạm vi cần theo dõi

Trong các cơ chế hỗ trợ, **`cflow(P)`** áp dụng trong dynamic control flow bắt đầu ở P, kể cả root; **`cflowbelow(P)`** chỉ chọn join point **bên dưới một P occurrence đang hoạt động**. Nếu P gọi P lần nữa, inner P vẫn có thể nằm dưới outer P. Những điều kiện này dựa vào **trạng thái runtime**, không phải chỉ vào tên/position của method.

~~~text
importBatch() [root: enter scope]
   ├─ validateRow()   ← kiểm tra scope động
   └─ persist()       ← kiểm tra scope động
importBatch() returns [exit scope]
normalize() outside  ← không thuộc scope đó
~~~

Khi tối ưu, câu hỏi quan trọng không phải chỉ “cflow hay cflowbelow”, mà là **bao nhiêu lần root phải cập nhật state**, **bao nhiêu candidate join point phải kiểm tra state** và **mỗi lượt advice phát sinh bao nhiêu công việc bổ sung**. Lời gọi async sau khi root đã kết thúc không được mặc định mang cùng call-stack flow; context propagation là lựa chọn kiến trúc riêng.

Một `ThreadLocal<Boolean>` dùng để mô phỏng scope có thể thiếu thông tin root occurrence/depth khi nested hoặc đệ quy; không đồng nhất nó với đầy đủ semantics của một pointcut engine.

## 4. Vì sao phải theo dõi runtime state?

Một điều kiện như “method encode xảy ra trong importBatch” không thể chỉ suy từ method encode vì nó có thể được gọi qua nhiều callers:

~~~text
ImportBatch -> Store -> encode
ManualEdit  -> Store -> encode
Background  -> Store -> encode
~~~

Nếu tất cả các đường đều gọi **cùng method body**, lexical execution pointcut thấy cùng một method. Để phân biệt, runtime phải biết **lần invocation hiện tại đang thuộc dynamic flow nào**.

Một cơ chế khái niệm:

~~~text
On entering anchor:
    push marker / increment active counter

At candidate nested join point:
    check if marker active

On leaving anchor:
    pop marker / decrement counter
~~~

Chi phí có thể đến từ:

1. **Instrument anchor entry/exit** để duy trì marker.
2. **Kiểm tra runtime** trên những candidate join points cần biết flow status.
3. **Lưu và phục hồi scope** khi nested/reentrant calls xảy ra.
4. **Lưu dữ liệu binding** nếu pointcut cần context như actor, ID hoặc đối số từ anchor.
5. **Đồng bộ hóa/context isolation** giữa threads hoặc execution contexts.
6. **Allocation và GC** nếu tạo scope objects, stacks, context snapshots hoặc wrappers.

Đây là **cost model khái niệm**. Không phải mọi ngôn ngữ AOP hoặc compiler nhất thiết dùng call stack đầy đủ hay một ThreadLocal ArrayDeque; một số trường hợp có thể dùng counter, bitmask, mapping hoặc weaving chuyên biệt.

## 5. Chi phí CPU: enter, check, exit

Xét:

- \(N_a\): số lần vào/ra anchor.
- \(N_c\): số lần candidate join points thực sự cần kiểm tra flow.
- \(C_e\): chi phí trung bình enter/exit marker.
- \(C_c\): chi phí trung bình mỗi lần kiểm tra membership.
- \(C_b\): chi phí advice body nếu được kích hoạt.

Một mô hình sơ bộ:

\[
T_{\text{flow}} \approx N_a C_e + N_c C_c + N_{\text{matched}} C_b
\]

Mô hình giúp tìm nguyên nhân overhead, **không phải công thức dự báo chính xác**; JIT, cache, branch prediction, contention, object allocation và compiler optimizations có thể khiến các đại lượng thay đổi theo workload.

Điểm đáng chú ý: nếu anchor chỉ chạy một lần nhưng candidate join point nằm trên hot loop với hàng triệu lần gọi, **\(N_c C_c\)** có thể chi phối overhead. Ngược lại, nếu matcher có ít candidates, dynamic tracking có thể không đáng kể so với I/O.

### 5.1. Member check O(1) hay O(depth)?

Nếu chỉ cần biết một loại anchor có đang active không, runtime có thể quản lý một **counter** theo context và kiểm tra activeCount > 0, tương tự O(1) về số stack frames.

Nếu phải tìm marker cụ thể, binding hoặc so khớp một tập flow contexts, một giải pháp đơn giản dùng stack search có thể có chi phí **O(depth)**, trong đó depth là số nested scopes. Chi phí này thay đổi theo implementation.

Không nên phát biểu “mọi cflow check luôn duyệt toàn bộ stack” hoặc “luôn O(1)” khi chưa biết code được tạo bởi weaver.

### 5.2. Chi phí ở hot methods

Các leaf methods nhỏ như getter, encoder, parser helper có thể chỉ mất ít nanoseconds tới microseconds trong một workload nhất định. Nếu advice/cflow check được chèn vào từng leaf call, phần overhead tương đối có thể lớn hơn so với một phương thức I/O chậm.

**Mức chi phí phải được đo**, không tự gán con số tuyệt đối cho mọi JVM hoặc mọi AOP engine.

## 6. Chi phí bộ nhớ, allocation và GC

Một cơ chế dynamic context có thể cần:

- Thread-local storage entry.
- Một counter hoặc stack dữ liệu theo thread.
- Các objects chứa bindings hoặc metadata của mỗi invocation.
- Context snapshot khi chuyển qua async boundary.
- Callback wrappers để restore context.
- Trace/log events nếu advice active.

Một flow scope object rất nhỏ vẫn có thể trở thành lượng allocation lớn nếu được tạo hàng triệu lần trong một hot path. Nhưng JIT có thể thực hiện escape analysis và loại bỏ một số allocations trong điều kiện thích hợp. Tránh khẳng định mọi marker gây heap allocation không tránh được.

### 6.1. Thread pools và lifetime

Nếu ThreadLocal giữ một stack/context rồi **không remove khi scope kết thúc**, worker thread trong pool có thể giữ dữ liệu cũ lâu hơn mong muốn. Điều đó gây:

- Memory retention.
- Leaked business identity/tracing metadata.
- Context nhầm giữa hai requests trên cùng worker.
- Khó debug vì lỗi phụ thuộc thread reuse.

Khi dùng scoped context tự quản, phải restore/remove trong finally. Đừng thay thế việc giải phóng đúng bằng cách chờ GC.

### 6.2. Depth lớn và recursion

Một call tree đệ quy hoặc nhiều around advice lồng nhau có thể tạo depth lớn. Stack marker thiết kế sai có thể tăng chi phí kiểm tra membership, giữ nhiều objects hoặc làm sai cách suy luận về anchor đã active.

## 7. Chi phí gỡ lỗi và độ phức tạp ngữ nghĩa

Chi phí không chỉ nằm ở CPU:

- **Khó đọc:** một method được advice ở đường gọi này nhưng không được advice ở đường gọi khác.
- **Khó dự đoán:** thêm một lớp wrapper hoặc thay cách gọi helper có thể thay dynamic flow.
- **Khó test:** phải dựng đúng call path, recursion, exception, async và nested anchors.
- **Khó quan sát:** logs không ghi rõ vì sao một pointcut match hoặc không match.
- **Tính phụ thuộc:** code nghiệp vụ có thể âm thầm dựa vào presence của một call stack scope.
- **Độ mong manh:** tách một method thành task async có thể phá dynamic-flow membership.

Đây là các chi phí bảo trì quan trọng khi quyết định có nên dùng cflow thay cho một thiết kế rõ ràng hơn.

### 7.1. Những câu hỏi reviewer nên hỏi

1. Điều kiện thật sự cần dynamic call history không?
2. Có thể mô tả điều kiện bằng một parameter hoặc explicit context không?
3. Cùng một method cần hai hành vi tùy caller hay vì API chưa phân tách trách nhiệm?
4. Có async/thread boundaries hay RPC khiến stack dynamic thay đổi không?
5. Điều kiện có thuộc policy bảo mật bắt buộc không? Có đường bypass không?
6. Có kiểm thử được anchor/candidate match và exclude anchor riêng cho cflowbelow không?
7. Có resource scopes cần restore khi exception không?

## 8. Ví dụ Java 17 runnable: context động qua ThreadLocal

Ví dụ sau **mô phỏng ý tưởng flow membership** bằng ThreadLocal stack. Nó đủ để minh họa:

- Đang ngoài scope: active=false.
- Vào anchor: active=true.
- Trong nested scope: anchor vẫn active.
- Nếu nested operation throw: finally pop scope.
- Ra khỏi anchor: active=false và ThreadLocal.remove.
- Chuyển work sang ExecutorService: worker thread **không tự kế thừa** ThreadLocal của caller.
- Truyền boolean/context tường minh: worker nhận đúng ý nghĩa do caller chuyển.

**Quan trọng:** helper Flow.active("top") chỉ minh họa membership của một marker; nó **không cài đặt toàn bộ semantics của cflow/cflowbelow**. Không đưa đoạn demo này vào production như một runtime AOP engine.

Tạo tệp **DynamicFlowCostDemo.java**:

~~~java
import java.util.ArrayDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DynamicFlowCostDemo {
    static final class Flow {
        private static final ThreadLocal<ArrayDeque<String>> STACK =
                new ThreadLocal<>();

        static void within(String name, Runnable body) {
            ArrayDeque<String> stack = STACK.get();
            if (stack == null) {
                stack = new ArrayDeque<>();
                STACK.set(stack);
            }
            stack.push(name);
            try {
                body.run();
            } finally {
                String removed = stack.pop();
                if (!removed.equals(name)) {
                    throw new IllegalStateException("mismatched scope");
                }
                if (stack.isEmpty()) {
                    STACK.remove();
                }
            }
        }

        static boolean active(String name) {
            ArrayDeque<String> stack = STACK.get();
            return stack != null && stack.contains(name);
        }

        static int depth() {
            ArrayDeque<String> stack = STACK.get();
            return stack == null ? 0 : stack.size();
        }
    }

    static void leaf(String label) {
        System.out.println("leaf:" + label
                + " cflow(top)=" + Flow.active("top")
                + " depth=" + Flow.depth());
    }

    static void explicitLeaf(String label, boolean underTop) {
        System.out.println("explicit:" + label + " underTop=" + underTop);
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            leaf("outside");
            Flow.within("top", () -> {
                System.out.println("top: active=" + Flow.active("top")
                        + " depth=" + Flow.depth());
                leaf("inside");
                Flow.within("nested", () -> leaf("nested"));
                try {
                    Flow.within("fail", () -> {
                        leaf("before-error");
                        throw new IllegalStateException("boom");
                    });
                } catch (IllegalStateException e) {
                    System.out.println("caught=" + e.getMessage());
                }
                leaf("restored");
                try {
                    Future<Boolean> worker =
                            pool.submit(() -> Flow.active("top"));
                    System.out.println("worker: cflow(top)=" + worker.get());
                    Future<?> explicit = pool.submit(() ->
                            explicitLeaf("worker", true));
                    explicit.get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            leaf("after");
            System.out.println("final-depth=" + Flow.depth());
        } finally {
            pool.shutdown();
        }
    }
}
~~~

Chạy trên JDK 17:

~~~bash
javac --release 17 DynamicFlowCostDemo.java
java DynamicFlowCostDemo
~~~

**Stdout chính xác đã được kiểm chứng:**

~~~text
leaf:outside cflow(top)=false depth=0
top: active=true depth=1
leaf:inside cflow(top)=true depth=1
leaf:nested cflow(top)=true depth=2
leaf:before-error cflow(top)=true depth=2
caught=boom
leaf:restored cflow(top)=true depth=1
worker: cflow(top)=false
explicit:worker underTop=true
leaf:after cflow(top)=false depth=0
final-depth=0
~~~

### 8.1. Outside và inside

Trước khi Flow.within("top") được gọi, ThreadLocal chưa có stack nên active("top") false. Sau push("top"), nó true; depth là 1.

### 8.2. Nested context và recursion

Trong Flow.within("nested"), stack có ["nested", "top"]; active("top") true, depth 2. Việc một marker cũ vẫn active khi có marker mới bên trên là điều ta muốn quan sát ở một flow-membership demo.

Nếu dùng cflowbelow thật, cần thêm logic nhận diện current join point và anchor activation, không chỉ tìm marker trong stack.

### 8.3. Exception không làm rò context

Inside scope "fail", body throw "boom"; finally pop fail, khôi phục top. Vì vậy ngay sau catch, active("top") vẫn true và depth về 1. Khi top scope kết thúc, pop cuối cùng rồi remove ThreadLocal, depth về 0.

### 8.4. Async worker không có marker

Executor worker chạy trên thread khác, nên Flow.active("top") false dù caller vẫn đang trong top scope. Đây là kết quả **của mô hình ThreadLocal ở demo**, không tuyên bố mọi AOP engine luôn tạo worker có semantics giống vậy. Một hệ thống async-aware có thể truyền context bằng cơ chế khác.

### 8.5. Phương án truyền tường minh

Caller truyền underTop=true cho task, nên worker in **explicit:worker underTop=true** mà không cần flow stack. Trong thực tế nên dùng **một immutable context object** có các thuộc tính cần thiết thay vì truyền một boolean mơ hồ nếu có nhiều điều kiện.

### 8.6. Kết quả này không phải benchmark

Ví dụ có println và Future.get, không đo overhead hiệu năng. Số depth là minh họa kỹ thuật chứ không phải duration. Không dùng số dòng output để suy dynamic pointcuts tốn bao nhiêu nanoseconds.

## 9. Phương án đơn giản hơn 1: pointcut tĩnh theo type/method/annotation

Nếu yêu cầu chỉ là “log mọi lời gọi vào những operation xử lý import”, bạn có thể chọn **method execution** hoặc **annotation** phù hợp ở một entrypoint thay vì kiểm tra cflow ở từng helper.

~~~text
@ObservedImport
importBatch()
    -> validate()
    -> persist()
~~~

Đoạn này là sơ đồ, không phải Java runnable với một annotation đã định nghĩa.

Một static matcher có thể áp advice cho **importBatch** một lần:

- Ghi start timestamp.
- Ghi result/failure.
- Ghi số bản ghi xử lý.
- Kết thúc trong finally.

Nếu không cần log từng helper, đây có thể là giải pháp **nhỏ hơn, dễ test hơn và ít candidate checks hơn**.

### 9.1. Hạn chế của phương án tĩnh

Nếu business requirement thực sự cần chặn riêng normalize() chỉ khi nó được gọi gián tiếp từ importBatch(), static method matcher **không đủ thông tin** để phân biệt call paths. Không được thay yêu cầu mà không xem semantics. Có thể cần refactor service boundary hoặc explicit context.

### 9.2. Annotation không tự bằng dynamic control flow

Annotation đặt ở entrypoint là metadata tĩnh. Nếu helper cần biết nó đang xử lý một operation có annotation ở ancestor, vẫn phải có context propagation hoặc một cơ chế khác, trừ khi bạn chỉ cần quan sát entrypoint.

## 10. Phương án đơn giản hơn 2: truyền context qua tham số

Thay vì để helper đoán lịch sử call stack, bạn có thể truyền context tường minh.

~~~java
record OperationContext(String correlationId,
                        boolean importMode) {}

void normalize(String row, OperationContext context) {
    if (context.importMode()) {
        // Hành vi được chọn theo context đã truyền.
    }
}
~~~

Đây là snippet minh họa, cần class/import context và phần thân đầy đủ nếu muốn biên dịch độc lập.

Lợi ích:

- Dễ nhìn dependency từ signature.
- Test trực tiếp từng mode.
- Không phụ thuộc hình dạng call stack.
- Dễ chuyển qua Executor/CompletableFuture hoặc RPC khi đã thiết kế serialization/bảo mật.
- Có thể dùng immutable context và giảm hidden mutable state.
- Không cần instrument mọi candidate pointcut.

Chi phí đánh đổi:

- Phải cập nhật signatures hoặc truyền qua nhiều cấp.
- Có thể làm API dài nếu context quá lớn.
- Caller có thể truyền context giả nếu đó là thông tin bảo mật không được xác thực.
- Dữ liệu context có thể nhạy cảm và cần policy rõ.

**Không chuyển authorization từ một nguồn tin cậy sang một boolean caller tự kiểm soát** khi nó quyết định quyền truy cập. Explicit parameter là lựa chọn kỹ thuật biểu đạt context, không tự thay thế authentication/trust boundary.

### 10.1. Context object thay vì nhiều flags

Nếu nghiệp vụ cần correlationId, tenant, operationKind, traceSpanId, actor và policyVersion, dùng một immutable record/DTO hợp lý hơn bảy boolean rời rạc. Tuy nhiên chỉ đưa các fields thật sự cần thiết; không biến context object thành một “global mutable bag”.

## 11. Phương án đơn giản hơn 3: tách boundary nghiệp vụ

Khi cùng một helper phải xử lý hai hành vi khác nhau tùy call origin, có thể refactor thành hai explicit operations:

~~~text
ImportService.normalizeForImport(...)
EditService.normalizeForEdit(...)
        |
        v
PureNormalizer.normalize(...)
~~~

Thay vì chọn method PureNormalizer.normalize bằng cflow(importBatch), mỗi entrypoint quyết định behavior/correlation một lần rồi chuyển tham số cần thiết xuống pure helper.

Lợi ích:

- Tách trách nhiệm rõ, dễ unit test.
- Core helper có thể trở nên pure/stateless.
- Tránh phụ thuộc vào stack layout.
- Dễ đánh giá đúng các đường async, batch, scheduled.
- Có thể giảm số join points cần instrument.

Đánh đổi: thêm API/layer, có thể gây duplication nếu refactor không tốt; nếu nhiều cross-cutting concerns thực sự không thuộc service boundary nào, AOP vẫn có giá trị.

### 11.1. Không lặp mã chỉ để loại bỏ cflow bằng mọi giá

Một đích thiết kế tốt là **rõ semantics với mức lặp hợp lý**. Nếu việc tách boundary tạo hàng chục wrappers khó duy trì, một dynamic aspect đã được kiểm chứng có thể hợp lý hơn.

## 12. Phương án đơn giản hơn 4: một outer around advice

Nếu chỉ cần biết **một operation hoàn tất thế nào**, thay vì ghi từng inner join point, đặt một around advice ở entrypoint:

~~~text
Around(importBatch):
    start timer
    try:
        result = proceed()
        record completion
        return result
    catch:
        record failure
        rethrow
    finally:
        release scope
~~~

Đây là giả mã, không phải Java độc lập. Một advice như thế đo **toàn bộ thời gian từ entry đến return của continuation mà nó bao**, trong phạm vi synchronous boundary.

Nếu cần đếm records hoặc bytes xử lý, core có thể trả một **summary object** cho outer advice. Không cần cflow instrumentation ở mỗi normalize/encode call.

### 12.1. Khi một boundary chưa đủ

Nếu nghiệp vụ yêu cầu độ trễ từng bước hoặc danh sách các lệnh I/O cụ thể, một outer timer không cho thông tin đó. Có thể thêm một số **explicit spans** tại những đoạn thật sự có ý nghĩa thay vì instrument tất cả inner methods.

## 13. Phương án đơn giản hơn 5: scoped ThreadLocal khi API không thể đổi

Khi phải tích hợp thư viện sâu không nhận context param, một ThreadLocal scope có thể là thỏa hiệp:

~~~text
enter:
    previous = context.get()
    context.set(current)
try:
    proceed()
finally:
    restore previous / remove
~~~

Nếu đây là nhu cầu **truyền một request context giữa các helper trên cùng thread**, scoped ThreadLocal có thể đơn giản hơn một pointcut cflow phức tạp và cho phép helper đọc context chủ động.

Nhưng ThreadLocal **vẫn có overhead và hidden dependency**, không được mô tả là zero-cost hoặc hoàn toàn thay thế được cflow.

### 13.1. Những giới hạn

- Context theo thread, không tự theo một async task trên thread khác.
- Cần restore nested scopes trong finally.
- Thread pool reuse có thể gây leak nếu quên remove.
- Một ThreadLocal chứa mutable shared object vẫn có data race.
- Nếu chỉ cần truy vấn marker cho một condition, stack search có thể đắt hơn counter.
- Nếu nhiều nested scopes mang các values khác nhau, cần semantics của stack/binding rõ.

### 13.2. An toàn khi exception

Một scope utility cần đảm bảo pop/restore khi body throw; demo đã minh họa điều này. Nếu stack.pop hoặc cleanup itself throw, exception gốc có thể bị che; code production cần ownership và error policy phù hợp.

## 14. Phương án đơn giản hơn 6: event/correlation thay cho dynamic stack

Đôi khi yêu cầu ban đầu không phải “có đang trong call stack importBatch không” mà là “mọi event thuộc cùng một logical operation phải có correlation ID”.

Logical operation có thể vượt qua:

- Executor workers.
- Reactive/async continuations.
- Message queues.
- HTTP/RPC boundaries.
- Retries hoặc resumed tasks.

Dynamic call stack là đại diện thiếu chính xác cho logical operation nếu execution phân tán qua nhiều threads/processes.

Một phương án là tạo **correlation context** ở entrypoint, truyền rõ qua các boundaries và ghi structured events:

~~~text
Request creates correlationId = X
  -> sync method event tagged X
  -> enqueue job with validated X
  -> worker event tagged X
  -> remote service event tagged X
~~~

Cách này cần context propagation/security validation, có thể có serialization và truyền dữ liệu; đổi lại không phụ thuộc synchronous call stack.

**Đừng tự tin rằng một trace ID là bằng chứng một method nằm bên dưới cflow anchor.** Correlation là quan hệ logic, còn cflow là quan hệ của active dynamic execution context theo ngôn ngữ AOP.

## 15. So sánh các phương án

| Yêu cầu | Giải pháp thường cân nhắc | Chi phí/giới hạn |
|---|---|---|
| Log toàn operation | Static entrypoint pointcut + around | Không thấy từng inner method |
| Helper cần cờ mode | Explicit parameter/immutable context | Signatures có thể phình |
| Hai workflow có behavior khác | Tách service boundary + pure helper | Thêm API/refactor |
| Library không nhận context param, synchronous thread | Scoped ThreadLocal | Hidden context, cleanup, thread affinity |
| Theo dõi dynamic ancestor join point chính xác | cflow/cflowbelow của engine | Runtime instrumentation và test semantics |
| Correlation qua async/messaging | Explicit correlation propagation | Serialization/trust/observability policy |
| Chỉ đo vài nested phases | Explicit spans/events | Instrument thủ công tại điểm cần |
| Security policy dựa quyền thực | Authorized policy/context từ nguồn tin cậy | Không tin boolean tùy ý của caller |
| Công việc rất nóng/call count lớn | Static matching/refactor hoặc optimized flow | Phải benchmark trước/sau |
| Scope lồng nhau có bindings khác | AOP binding hoặc scoped context object | Semantics nested/recursion phức tạp |

Đây là bảng **định hướng**, không phải quyết định mặc định cho mọi dự án.

## 16. Những yếu tố làm overhead tăng nhanh

### 16.1. Candidate density

Càng nhiều method có thể là candidate cho một dynamic pointcut, càng nhiều lần cần runtime check nếu weaver không loại trừ được chúng từ trước. Có thể giảm candidate set bằng type/package/signature constraints nếu ngôn ngữ AOP hỗ trợ.

### 16.2. Nested anchors

Nhiều dynamic scopes hoạt động cùng lúc có thể đòi hỏi counters riêng, marker bindings hoặc stack nhiều tầng. Nếu một pointcut chỉ cần biết số anchor activations đang active, counter có thể đủ; nếu cần binding của anchor gần nhất, phải giữ thông tin sâu hơn.

### 16.3. Lời gọi cực nhỏ

Một getter vài phép toán có thể không xứng với check/interceptor trên mọi invocation. Nhưng không suy từ source lines: JIT inlining và code generation ảnh hưởng cost đáng kể.

### 16.4. Allocation và boxing

Nếu mỗi match tạo context object, đóng gói primitive hoặc ghi event, allocation có thể đáng kể. Phải phân biệt cost **flow tracking** với cost **logging/serialization**. Tối ưu flow marker nhưng giữ một expensive logger trong hot path có thể không cải thiện đáng kể.

### 16.5. Lock/contention

Nếu dynamic context được lưu trong một shared global map có locking, cost có thể tệ hơn thread-confined state. Nhưng ThreadLocal context lại có các chi phí và propagation constraints riêng. Không đánh đồng “shared” và “thread-local” như hai implementation duy nhất.

### 16.6. Async handoff

Capture/restore context ở mỗi task có chi phí wrapper/allocation, và phải xác định security, tracing, cancellation semantics. Không nên mô phỏng sync cflow như một stack liên tục xuyên thread boundaries nếu ngôn ngữ AOP không định nghĩa điều đó.

## 17. Benchmark công bằng: đo thứ gì?

Một benchmark hợp lý cần trả lời:

1. **Baseline không advice:** cùng nghiệp vụ không dùng dynamic pointcut.
2. **Static advice:** chọn method bằng pointcut tĩnh, cùng advice body.
3. **Dynamic flow advice:** điều kiện cflow/cflowbelow, cùng advice body.
4. **Explicit context:** truy vấn context được truyền, cùng nghiệp vụ quan sát.
5. **Scoped ThreadLocal:** context marker tự quản, cùng độ sâu/candidate count.

Các workloads cần thay đổi:

- Số candidate join points trên một request.
- Số anchor activations.
- Depth của nested scopes.
- Tỷ lệ match/không match.
- Tỷ lệ ngoại lệ hoặc short-circuit.
- Sync versus async boundary.
- Số threads và contention.
- Khối lượng advice body (empty/no-op, counters, logging, I/O).

Chỉ khi so apples-to-apples mới đo được **mức chi phí tăng thêm** do dynamic tracking thay vì do code log khác nhau.

### 17.1. Dùng JMH cho microbenchmark

Để benchmark Java hot paths, JMH có công cụ thiết kế cho warmup, forks, đo throughput/latency và hạn chế dead-code elimination. Cần tránh:

- Đo bằng System.out.println trong vòng lặp.
- Dùng một lần currentTimeMillis rồi coi là accurate.
- Để JIT loại bỏ hoàn toàn work của benchmark.
- So code khác behavior.
- Quên allocation rate, GC và multithreaded scenarios.
- Tổng quát hóa kết quả máy local thành số cố định cho mọi deployments.

### 17.2. End-to-end profiling

Trong hệ thống thật, có thể dùng CPU profiler, allocation profiler, async profiler và tracing phù hợp để xem:

- Thời gian enter/exit flow scope.
- Thời gian pointcut match/check.
- Advice body (logging, serializer, network).
- Allocations thuộc scope/binding.
- GC pressure, p95/p99 latency.
- Lock contention và thread scheduling.

Không nên tối ưu cflow trước khi chứng minh nó nằm trong hot path đáng kể.

### 17.3. Các chỉ số cần báo

Một báo cáo benchmark tốt nêu JDK, hardware, OS, loại engine/weaver, số join points, input size, depth, concurrency, warmup, forks, time unit, confidence interval và test harness. Nếu không có số thực đo, **đừng bịa mức overhead % hoặc nanoseconds**.

## 18. Test correctness cho cflow/cflowbelow

Một test suite nên phân biệt các tình huống:

| Tình huống | Điểm cần xác minh |
|---|---|
| Candidate gọi ngoài anchor | Không match dynamic flow |
| Candidate gọi trong anchor | Match theo cflow yêu cầu |
| Chính anchor join point | Phân biệt cflow với cflowbelow |
| Nested helper | Membership còn active |
| Anchor tự đệ quy | Activation identity và depth |
| Hai anchor lồng nhau | Binding của anchor nào được chọn |
| Target throw | Scope được cleanup trong finally |
| Advice throw trước proceed | Không rò marker |
| Advice throw sau proceed | Marker vẫn cleanup |
| Around short-circuit | Không giả định inner points chạy |
| Around retry/proceed nhiều lần | Số invocations và bindings đúng |
| Thread pool boundary | Không tự suy context được truyền |
| Async completion sau anchor return | Không nhầm stack với logical task |
| Multiple aspect scopes | Precedence và cleanup LIFO |
| Static filter + cflow | Filter không loại nhầm candidates |
| Unmatched hot method | Không thực hiện advice ngoài ý muốn |
| Context leak qua reused thread | Request mới không thấy marker cũ |
| cflowbelow dưới cùng anchor | Kiểm tra excluded anchor đúng semantics |

**Chạy cả negative tests** rất quan trọng. Chỉ thấy advice chạy trong một flow đúng không chứng minh nó **không chạy sai** ở flow khác.

## 19. Tính đúng và an toàn: cflow không phải security boundary tự đủ

Một runtime flow marker có thể bị bypass nếu:

- Caller gọi trực tiếp target ở một đường không đi qua anchor mong muốn.
- Proxy-based interception không quan sát self-invocation ở những method nhất định.
- Call tree được tách ra async và marker không được propagate.
- Exception làm scope restore sai.
- Reentrant flow trộn thông tin hai anchors.
- Code không đáng tin điều khiển được boolean context dùng thay security policy.

Nếu advice mang tính bảo mật bắt buộc, chỉ dùng “đang trong luồng gọi X” không tự xác minh quyền của actor. Cần authentication, authorization và kiểm tra resource invariant ở boundary đáng tin.

### 19.1. Context binding có thể chứa dữ liệu nhạy cảm

Flow binding có thể lưu user ID, tenant ID hoặc token. Không log/copy context indiscriminately vào shared caches, task queues hoặc diagnostic errors. Chọn context fields tối thiểu và phân biệt trusted versus untrusted sources.

## 20. Retry, transaction và lifecycle

Giả sử dynamic flow scope bao một transaction/retry chain:

~~~text
FlowAnchor(
    Retry(
        Transaction(
            Business
        )
    )
)
~~~

Nếu retry gọi proceed ba lần, FlowAnchor có thể chỉ push một marker cho toàn request, nhưng Business có thể execute ba lần. Một cflow pointcut trên nested operations có thể match ở cả ba attempts; metrics bên trong có thể ghi ba events.

Nếu thay thứ tự:

~~~text
Retry(
    FlowAnchor(
        Transaction(
            Business
        )
    )
)
~~~

Mỗi attempt có thể enter/exit một flow scope riêng. Chi phí tracking và ngữ nghĩa correlation khác nhau. Nếu binding chứa attempt ID, scope per-attempt có thể rõ hơn.

### 20.1. Transaction scope khác flow scope

Transaction state có isolation, commit/rollback rules và thread/resource ownership. Chỉ vì một cflow marker active không chứng minh DB transaction vẫn mở, hoặc một async task đã nằm trong cùng transaction. Các contexts có vòng đời và guarantees khác nhau.

### 20.2. Lỗi khi cleanup

Scope marker phải được restore/pop trong finally khi continuation throw. Nhưng finally itself có thể throw, che lỗi target. Một helper production nên có invariants rõ, nested restoration, error handling phù hợp và tests với failure injection.

## 21. Reentrancy và recursion

Cflow có thể trở nên khó reasoning khi anchor gọi lại chính nó hoặc một advice kích hoạt method nằm trong pointcut của nó.

~~~text
importBatch()
  -> helper()
      -> importBatch()       <-- nested anchor
          -> helper()
~~~

Nếu theo dõi bằng boolean active, không thể phân biệt depth 1 và 2. Nếu scope trong set false khi rời nested anchor, outer anchor vẫn đang hoạt động nhưng marker bị tắt sai. Vì vậy counter/stack với enter++ và exit-- theo LIFO có lợi hơn boolean naive trong nested/recursive scenarios.

Một ThreadLocal stack như demo có thể xử lý nested markers đơn giản, nhưng còn cần semantics chính xác nếu pointcut bind arguments của từng anchor.

### 21.1. Advice tự instrument helper của nó

Một logging advice gọi logger.write(), mà logger.write() cũng được pointcut chọn, có thể gây recursion vô hạn. Cần exclude infrastructure join points, hoặc dùng một reentrancy guard được restore trong finally.

Đây là vấn đề độc lập với cflow overhead, nhưng dynamic flow tracking làm call paths khó đọc hơn nếu scope markers cũng bị advice reenter.

## 22. Quy trình quyết định có dùng cflow không

1. **Viết requirement chính xác:** đang cần lexical location, runtime ancestry hay logical correlation?
2. **Xác định anchor join point:** call hay execution, loại method nào, cflow hay cflowbelow?
3. **Kiểm tra alternative rẻ hơn:** static pointcut, một outer around, explicit context hoặc refactor service boundary.
4. **Xác định số candidates:** những hot methods nào phải kiểm tra runtime?
5. **Vẽ nested/recursive flows:** có binding cần lưu và restore không?
6. **Vẽ async boundaries:** stack flow có kết thúc trước task không?
7. **Định nghĩa error semantics:** marker cleanup khi target/advice throw.
8. **Thiết kế context trust/ownership:** không lộ dữ liệu nhạy cảm.
9. **Viết positive/negative tests:** cả anchor match và excluded anchor của cflowbelow.
10. **Đo baseline:** so static/dynamic/explicit context với cùng business behavior.
11. **Tối ưu điểm nóng được chứng minh:** thu hẹp candidate set hoặc giảm dynamic checks.
12. **Ghi kiến trúc rõ:** khi nào pointcut này cần dynamic control flow, rủi ro còn lại và phương án fallback.

Không chọn một phương án chỉ vì ngắn code nhất. Một annotation gắn ở entrypoint có thể không thay được một policy thật sự dựa trên call ancestry; ngược lại, dùng cflow chỉ để đo tổng thời gian request thường là quá phức tạp.

## 23. Những lỗi thường gặp

**Lỗi 1 — Cflow giống call stack history vĩnh viễn.** Sai: nó gắn với dynamic flow đang active, không ghi nhớ mọi lời gọi trong quá khứ.

**Lỗi 2 — Cflowbelow chỉ cần kiểm tra boolean active.** Không đủ để exclude anchor join point chính xác.

**Lỗi 3 — Mọi cflow check phải duyệt toàn bộ Java stack.** Implementation có thể dùng counter/optimized instrumentation.

**Lỗi 4 — Mọi cflow check luôn O(1).** Các bindings/scopes/stack searches có thể phức tạp hơn.

**Lỗi 5 — ThreadLocal tự theo Executor workers.** Không đúng mặc định.

**Lỗi 6 — Dùng InheritableThreadLocal giải quyết hoàn toàn thread pools.** Worker reuse và timing làm kết quả dễ sai.

**Lỗi 7 — Quên pop/remove sau exception.** Dẫn đến context leak sang request khác.

**Lỗi 8 — Dùng một boolean cho recursive anchors.** Nested scope exit có thể tắt marker khi outer scope vẫn active.

**Lỗi 9 — Đo bằng println rồi kết luận overhead của cflow.** Logging I/O che gần hết overhead tracking.

**Lỗi 10 — Static matcher không có runtime cost.** Advice body và runtime guards khác vẫn có thể tốn.

**Lỗi 11 — Dùng cflow cho mọi helper thay vì instrument một operation boundary.** Có thể làm tăng candidate checks và khó bảo trì.

**Lỗi 12 — Truyền mutable context object cho async workers không synchronization.** Có thể tạo data race.

**Lỗi 13 — Coi correlation ID tương đương cflow ancestry.** Một ID có thể trải qua threads/processes ngoài call stack.

**Lỗi 14 — Coi cflow là authentication/authorization.** Dynamic ancestry không tự chứng minh caller có quyền.

**Lỗi 15 — Không test negative flows.** Advice có thể match sai ở những callers khác.

**Lỗi 16 — So overhead mà không giữ advice body giống nhau.** Benchmark không cô lập dynamic tracking.

**Lỗi 17 — Rely vào type/name của helper để suy logical operation.** Cùng helper có thể được gọi từ nhiều flows.

**Lỗi 18 — Retry không ảnh hưởng metric.** Retry có thể chạy nested pointcuts nhiều lần.

**Lỗi 19 — Context không cần restore khi nested scopes.** Outer context có thể bị xóa hoặc thay bằng inner value.

**Lỗi 20 — Java ThreadLocal stack demo là implementation cflow chuẩn.** Nó chỉ minh họa membership, không có compiler join point semantics.

## 24. Câu hỏi ôn tập và đáp án

**Câu 1.** Vì sao method encode có thể match pointcut ở lần gọi này nhưng không match ở lần khác?

**Đáp:** Vì dynamic control flow đang active khác nhau, dù method body giống nhau.

**Câu 2.** Cflowbelow khác cflow ở điểm nào?

**Đáp:** Theo semantics của ngôn ngữ hỗ trợ, cflowbelow loại chính anchor join point và chọn các join points bên dưới; cflow gồm anchor.

**Câu 3.** Vì sao boolean active có thể sai với recursive anchors?

**Đáp:** Nested anchor exit có thể set false trong khi outer anchor vẫn active. Cần counter/stack đúng scope.

**Câu 4.** Chi phí cflow đến từ đâu?

**Đáp:** Anchor enter/exit tracking, runtime membership checks, bindings/context storage, allocations/GC nếu có và advice body.

**Câu 5.** Có bắt buộc implementation duyệt Java stack để check cflow?

**Đáp:** Không. Có thể dùng counter, marker hoặc optimized weaving; phụ thuộc engine.

**Câu 6.** Vì sao dynamic pointcut ở hot helper có thể tốn đáng kể?

**Đáp:** Vì rất nhiều candidate invocations phải chạy runtime check dù chỉ ít flow matches.

**Câu 7.** Khi chỉ cần đo tổng thời gian importBatch, cần cflow ở normalize không?

**Đáp:** Thường không; một outer around advice ở importBatch có thể đủ.

**Câu 8.** Khi helper cần biết importMode, giải pháp nào dễ unit test hơn?

**Đáp:** Truyền một explicit immutable context hoặc parameter nếu thiết kế API cho phép.

**Câu 9.** ThreadLocal context có tự đi qua ExecutorService không?

**Đáp:** Không mặc định. Cần capture/transfer context theo API và restore worker scope.

**Câu 10.** Trong demo vì sao worker cflow(top)=false?

**Đáp:** Worker chạy thread khác và không nhận ThreadLocal marker từ caller.

**Câu 11.** Vì sao Flow.within dùng finally?

**Đáp:** Để pop marker khi body return hoặc throw, tránh giữ state cũ trên reused thread.

**Câu 12.** Cflow có bảo đảm mọi async task sinh bởi importBatch vẫn được match không?

**Đáp:** Không có bảo đảm chung; phải xem language/runtime async context semantics.

**Câu 13.** Một correlation ID bằng nhau chứng minh cùng dynamic call stack không?

**Đáp:** Không. Đó là logical grouping, có thể vượt qua các threads/processes.

**Câu 14.** Cần benchmark những phiên bản nào để so overhead?

**Đáp:** Baseline, static pointcut cùng advice body, dynamic cflow cùng advice body, explicit context và scoped marker nếu liên quan.

**Câu 15.** Vì sao test đúng một case trong anchor chưa đủ?

**Đáp:** Cần đảm bảo các cases ngoài anchor không bị advice chọn nhầm và nested/exception paths đúng.

**Câu 16.** Có thể dùng boolean importMode được caller tự gửi để cấp quyền bảo mật không?

**Đáp:** Không nếu caller không đáng tin. Authorization cần dữ liệu và policy có nguồn tin cậy.

## 25. Sơ đồ tổng kết

~~~text
Requirement: chỉ chạy advice trong luồng import?
                        |
                        v
          Cần biết call ancestry thật?
             /                    \
           KHÔNG                   CÓ
            |                      |
            v                      v
 Static pointcut /         Có thể refactor hoặc
 outer around advice       truyền context rõ?
            |                 /          \
            v               CÓ            KHÔNG
          Ít flow         Explicit        cflow /
          checks          context         cflowbelow
                              |                 |
                              v                 v
                     Dễ qua async        Phải theo dõi flow
                     và kiểm thử         runtime + nested
                              |                 |
                              +--------+--------+
                                       |
                                       v
                        Test failure/recursion/async
                        Measure actual CPU/alloc/GC
                        Preserve security and context
~~~

**Kết luận:** theo dõi ngữ cảnh động bằng cflow/cflowbelow là công cụ diễn đạt mạnh khi advice thật sự cần biết ancestry của join point đang active. Nhưng nó có thể thêm runtime checks, state tracking, allocation và độ phức tạp nested/async. Hãy cân nhắc static pointcuts, một outer around advice, explicit immutable context hoặc refactor boundary trước khi chọn dynamic flow. Nếu vẫn cần cflow, phải làm rõ semantics của anchor/cflowbelow, giảm candidate set, bảo đảm cleanup và **đo overhead trên workload thực** thay vì đưa ra con số không có bằng chứng.

## Tài liệu tham khảo

- [AspectJ Programming Guide](https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html): cflow, cflowbelow, pointcut, advice và dynamic control flow trong một ngôn ngữ AOP cụ thể.
- [Java SE 17 — ThreadLocal](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html): thread-specific state và lifecycle.
- [Java SE 17 — ArrayDeque](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/ArrayDeque.html): stack/deque operations dùng trong demo.
- [Java SE 17 — ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html): task execution và worker threads.
- [Java SE 17 — Future](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): task completion và get.
- [JMH — Java Microbenchmark Harness](https://openjdk.org/projects/code-tools/jmh/): thiết kế microbenchmark đáng tin cho JVM.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="advanced-control-flow-009">Thiết kế ví dụ để kiểm chứng chính xác phạm vi điều khiển</a>

<details>
<summary>Xem chi tiết</summary>

# Thiết kế ví dụ để kiểm chứng chính xác phạm vi điều khiển trong AOP

Một ví dụ về **luồng điều khiển nâng cao** trong lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**) không chỉ cần in ra vài dòng log rồi kết luận rằng pointcut chạy đúng. Cần thiết kế một **thí nghiệm có giả thuyết, tập join point có thể quan sát, kết quả kỳ vọng độc lập, phản ví dụ và tiêu chí kiểm chứng cụ thể**. Đặc biệt khi dùng các khái niệm **`cflow(P)`** và **`cflowbelow(P)`**, một test chỉ có một entrypoint và một lời gọi con rất dễ bỏ sót lỗi ở root, recursion, ngoại lệ, boundary của proxy và bất đồng bộ.

**Mục tiêu:** tự xây được một ví dụ đủ nhỏ để giải thích và đủ chặt để kiểm tra **điểm khởi đầu P, các điểm nằm dưới P, những điểm nằm ngoài phạm vi, những P lồng nhau và các đường không phát sinh join point**; từ đó phân biệt lỗi ngữ nghĩa control flow với lỗi khả năng intercept của công cụ.

Bài trình bày AOP **nói chung**, không quy định một bộ weaver, framework hay cú pháp DSL cụ thể. Hai chương trình Java 17 dùng instrumentation tường minh và JDK dynamic proxy để kiểm nghiệm những **tính chất được nêu**, chứ **không giả vờ** rằng Java thuần tự cung cấp mọi pointcut control-flow. Mỗi ví dụ có lệnh chạy, stdout xác định và giải thích rõ điều gì được chứng minh, điều gì chưa.

## 1. Xác định chính xác “phạm vi điều khiển” cần kiểm chứng

Các khái niệm gốc:
- **Join point:** một điểm chương trình có thể được AOP can thiệp, như method call hoặc method execution khi mô hình hỗ trợ.
- **Pointcut:** điều kiện chọn các join point; có thể dùng thông tin cấu trúc, runtime args/target, hoặc dynamic control flow.
- **Advice:** hành vi before, after, around hoặc dạng khác thực thi ở những join point phù hợp.
- **Weaving:** quá trình đưa advice vào mã, bytecode, lúc nạp lớp hoặc qua proxy/interceptor.
- **Control flow động:** quan hệ các lần thực thi/lời gọi đã thực sự diễn ra trong một hoạt động; nó phụ thuộc vào stack và đường điều khiển runtime.
- **P:** pointcut dùng để chọn **các occurrence có thể mở phạm vi**.
- **Root occurrence:** một lần xảy ra join point thực sự khớp P.
- **Descendant/below:** một join point occurrence xảy ra khi một root P ở phía ngoài **vẫn đang active**.

Khi P là điều kiện khớp nhiều lần, không thể đơn giản gọi “method này là root nên chắc không phải below”. Tính chất root/below là quan hệ **giữa những occurrence trên đường thực thi**, không phải nhãn cố định trên tên method.

Hai lựa chọn khái niệm phổ biến:

- **`cflow(P)`**: tại join point j đúng nếu có một P occurrence đang bao gồm chính j hoặc bao một đường thực thi chứa j.
- **`cflowbelow(P)`**: tại j đúng nếu có một P occurrence **ở bên ngoài j**, tức j nằm **bên dưới** lần P ấy, chứ không phải chính occurrence P khởi đầu phạm vi ấy.

Ví dụ đơn giản:

~~~text
entry()  <- P: root ngoài
   |
   +--> validate()   <- bên dưới P
   +--> persist()    <- bên dưới P

unrelated()            <- nằm ngoài phạm vi P
~~~

| Occurrence | `cflow(P)` | `cflowbelow(P)` |
| --- | --- | --- |
| `entry()` root ngoài cùng | Có | Không |
| `validate()` dưới entry | Có | Có |
| `persist()` dưới entry | Có | Có |
| `unrelated()` sau khi entry kết thúc | Không | Không |

**Bẫy quan trọng:** khi `entry()` gọi chính `entry()` lần nữa, root **bên trong** có thể thuộc **`cflowbelow(P)`** vì nó nằm dưới root **bên ngoài**. Một ví dụ không kiểm tra tình huống này chưa đủ chặt.

## 2. Phát biểu hợp đồng trước khi viết code

Một test tốt cần định nghĩa đủ sáu thành phần:

1. **Loại join point:** P chọn method **call**, method **execution**, hay một ranh giới proxy được mô phỏng? Không dùng lẫn các khái niệm.
2. **Vị trí gốc:** đâu là P occurrence bắt đầu phạm vi? P có thể được gọi lồng hoặc đệ quy không?
3. **Universe các điểm quan sát:** root, child, sibling, ngoài root, lỗi, async; **không** tính những phương thức chưa thực sự chạy.
4. **Semantics đối chiếu:** root có được tính trong `cflow`? Thế nào là “below”? Khi có nhiều P, chọn root nào?
5. **Tín hiệu quan sát:** một dòng event có loại occurrence, active ancestor depth, kết quả match và correlation phù hợp; không chỉ `println("advice fired")`.
6. **Điều kiện pass/fail:** giá trị và thứ tự dự kiến độc lập với code đang tính kết quả; mọi điểm không chọn cũng được kiểm tra.

Một hợp đồng thí nghiệm ví dụ:

~~~text
P := execution occurrence of operation root()

cflow(P):
  true at P itself and at every observable join point
  dynamically nested inside any active P occurrence.

cflowbelow(P):
  true at a join point j when there exists an active
  P ancestor distinct from the occurrence j itself.

When no P is active and j is not P, both false.
~~~

Đây là **giả mã ngữ nghĩa**, không phải cú pháp của mọi AOP language. Với mô hình **method-execution** trong ví dụ tự instrument, ta sẽ ghi nhận P tại thời điểm bắt đầu body rồi đẩy root lên stack cho các child events.

**Đừng chuyển ngay từ ý tưởng sang code:** nếu không thống nhất call hay execution, một test nhìn đúng về output vẫn có thể kiểm chứng sai điều kiện thực tế cần dùng.

## 3. Thiết kế bộ tình huống: dương, âm và biên

Một bộ test nên có cả những trường hợp **advice phải xuất hiện** và **advice không được xuất hiện**.

| ID | Đường chạy | Kỳ vọng cần kiểm chứng |
| --- | --- | --- |
| T0 | `outside()` khi chưa có P | Cả `cflow` và `cflowbelow` false |
| T1 | Bắt đầu P ngoài cùng | `cflow` true; `cflowbelow` false tại root |
| T2 | Child trực tiếp trong P | Cả hai true |
| T3 | Grandchild nhiều tầng bên dưới P | Cả hai true, dù không gọi trực tiếp từ P |
| T4 | Một P khác được gọi bên trong P ngoài | Cả hai true **ngay tại P trong** |
| T5 | Child bên trong P trong | Cả hai true; có hơn một P đang active |
| T6 | Quay ra P ngoài sau khi P trong kết thúc | Cả hai vẫn true ở child của P ngoài |
| T7 | Sibling/đường ngoài sau P return | Cả hai false |
| T8 | Root P ném exception | Cleanup, event sau root không bị đánh dấu P |
| T9 | Root P short-circuit trước child | Không có child occurrence để chọn |
| T10 | Nhánh `if` không được thực thi | Không phát event cho một điểm chỉ tồn tại trên sơ đồ tĩnh |
| T11 | Async worker được scheduling bên trong P | Không tự ở trong call stack P của caller |
| T12 | Một method được gọi cả trong và ngoài P | Cùng signature, kết quả membership khác |
| T13 | Proxy intercept outer, inner được gọi trên target | Có thể thiếu advice ở inner nếu bypass proxy |
| T14 | Hai thread cùng chạy hai flow độc lập | State P không rò từ flow này sang flow kia |
| T15 | Advice/handler ném lỗi | Phân biệt matcher với loại advice và exception path |
| T16 | Root P đệ quy nhiều tầng | Inner P vẫn có thể là below outer P |
| T17 | Helper/logging bên trong P | Không vô tình instrument thêm điểm gây recursion |

**Đừng cố gắng có 17 test chỉ bằng 17 dòng log không điều kiện.** Các ca được chọn phải liên kết với một giả thuyết rõ và kết quả độc lập. Nếu một join point không tồn tại do nhánh không chạy, **sự vắng mặt của event chính là kỳ vọng**.

## 4. “Oracle” độc lập là gì?

**Oracle** là bộ dữ liệu/kết quả dùng để xác định việc triển khai có đúng hay không. Nếu test xác nhận bằng cách **tính expected bằng chính hàm production đang được kiểm tra**, test có thể cùng sai với code mà vẫn pass.

Ví dụ dễ mắc lỗi:

~~~java
// Mảnh minh họa phản ví dụ, không phải chương trình độc lập.
boolean actual = matcher.isInControlFlow(event);
boolean expected = matcher.isInControlFlow(event);
assert actual == expected; // Không kiểm chứng điều gì.
~~~

Tốt hơn:
- Vẽ **timeline** của các P occurrences và điểm child.
- Tự lập **bảng expected** trước khi viết matcher.
- Dùng **literal expectations** trong test hoặc một oracle dựa trên stack mô tả riêng, không lấy expected từ matcher.
- Kiểm tra mỗi event theo **location + expected result**, không chỉ số event tổng.
- Thêm **negative controls** (outside, no-execution, direct call, cross-thread).
- Kiểm chứng cả **thứ tự** và **số lần** advice khi ngữ nghĩa yêu cầu.

Ở ví dụ đầy đủ bên dưới, mỗi event được đưa các expected booleans **ghi tường minh tại call site** và test ném `AssertionError` nếu lệch. Điều này đáng tin hơn việc nhìn một log được tính từ chính matcher rồi tự kết luận đúng.

## 5. Xác định điểm khởi đầu P và con nào là “below”

Một sơ đồ dễ đối chiếu:

~~~text
Time -------------------------------------------------------->

T0       outside()                            outside -> false,false
T1       P outer enters                       outer   -> true,false
T2         child A                            child A -> true,true
T3         P inner enters                     inner   -> true,true
T4           child B                          child B -> true,true
T5         P inner exits
T6         child C                            child C -> true,true
T7       P outer exits
T8       outside()                            outside -> false,false
~~~

Hai vấn đề quan trọng:

**Một:** `cflowbelow(P)` là **quan hệ với một occurrence P ở phía trên**. Chính P trong (T3) cũng là hậu duệ P ngoài (T1); do đó below=true tại inner root khi P ngoài đang active.

**Hai:** một join point có thể có **nhiều P ancestors**. Nếu muốn chỉ chọn “child trực tiếp của P ngoài” hoặc “chỉ một root ngoài cùng”, `cflowbelow(P)` **chưa đủ**, vì nó cũng nhận grandchild và nested P. Khi đó cần thêm điều kiện độ sâu, kind, entrypoint identity hoặc hợp đồng khác.

### 5.1 Sử dụng số root active thay vì một boolean ngây thơ

Một state thủ công có thể dùng:
- Boolean `inRoot`: đủ cho việc hỏi "có ít nhất một P active" nếu cleanup đúng, nhưng không phân biệt root lồng.
- Counter `activeRootDepth`: mô tả số P occurrences đang active.
- Stack root IDs: lưu thứ tự và root cụ thể, hữu ích khi cần binding theo root gần nhất/ngoài cùng.

Cẩn thận: không dùng `static boolean inRoot` dùng chung cho nhiều thread; nó có thể khiến một task độc lập bị đánh dấu nhầm ở trong P khi thread khác đang chạy root.

## 6. Java 17 hoàn chỉnh: bộ kiểm thử có expected literals

Chương trình dưới đây **mô phỏng** method-execution join point P bằng một `root(...)` có instrumentation tường minh. Một `ThreadLocal<Deque<String>>` chỉ chứa những P occurrences đang hoạt động trên **thread hiện tại**. Khi quan sát event:
- Nếu event **chính là P mới**: `cflow=true` ngay cả khi chưa có ancestor P; `below=true` chỉ nếu đã có root P phía ngoài.
- Nếu event **không phải P**: cả `cflow` và `below` true khi có ít nhất một root P active.
- Nếu không phải P và không có root active: cả hai false.

Điểm có chủ đích: **event P được kiểm tra trước khi push chính nó vào stack**. Nếu push trước rồi tính `below = depth>0`, root ngoài sẽ bị tính sai là below! Sau khi kiểm tra root, code mới push để các child nhìn thấy root đang active.

Mỗi event so sánh với **depth/cflow/below kỳ vọng ghi thành literal**, giúp phát hiện các sai lệch ở root, inner root và cleanup. Worker async **chỉ trả chuỗi** về main để stdout không phụ thuộc scheduler.

Lưu vào **`ControlFlowScopeVerification.java`**:

~~~java
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ControlFlowScopeVerification {
    private static final ThreadLocal<Deque<String>> ACTIVE =
            new ThreadLocal<>();

    static int ancestorDepth() {
        Deque<String> stack = ACTIVE.get();
        return stack == null ? 0 : stack.size();
    }

    static String inspect(
            String label,
            boolean thisEventMatchesP,
            int expectedDepth,
            boolean expectedCflow,
            boolean expectedBelow) {
        int depth = ancestorDepth();

        boolean cflow = thisEventMatchesP || depth > 0;
        boolean below = depth > 0;

        if (depth != expectedDepth
                || cflow != expectedCflow
                || below != expectedBelow) {
            throw new AssertionError("scope mismatch at " + label);
        }

        return label
                + " depth=" + depth
                + " cflow=" + cflow
                + " below=" + below;
    }

    static void event(
            String label,
            int depth,
            boolean cflow,
            boolean below) {
        System.out.println(
                inspect(label, false, depth, cflow, below));
    }

    static void root(
            String name,
            int expectedAncestors,
            boolean expectedBelow,
            Runnable body) {
        // Tại P occurrence: chưa push chính P vào stack.
        System.out.println(
                inspect("P:" + name, true,
                        expectedAncestors, true, expectedBelow));

        Deque<String> stack = ACTIVE.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            ACTIVE.set(stack);
        }

        stack.push(name);
        try {
            body.run();
        } finally {
            stack.pop();
            if (stack.isEmpty()) {
                ACTIVE.remove();
            }
        }
    }

    static void workerProbe(ExecutorService pool) {
        try {
            Future<String> future = pool.submit(
                    () -> inspect("async-worker", false,
                            0, false, false));
            System.out.println(future.get());
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    public static void main(String[] args) {
        event("outside-before", 0, false, false);

        ExecutorService pool =
                Executors.newSingleThreadExecutor();

        try {
            root("outer", 0, false, () -> {
                event("child:first", 1, true, true);
                root("inner", 1, true, () ->
                        event("child:inside-inner",
                                2, true, true));
                event("child:after-inner", 1, true, true);
                workerProbe(pool);
            });
        } finally {
            pool.shutdown();
        }

        event("outside-after", 0, false, false);

        // Short-circuit: root mở phạm vi nhưng không chạy child.
        root("short", 0, false, () -> {});
        event("after-short", 0, false, false);

        try {
            root("error", 0, false, () -> {
                event("child:before-error", 1, true, true);
                throw new IllegalArgumentException("boom");
            });
        } catch (IllegalArgumentException error) {
            System.out.println("CAUGHT " + error.getMessage());
        }

        event("after-error", 0, false, false);
    }
}
~~~

Biên dịch/chạy với **JDK 17+**:

~~~bash
javac ControlFlowScopeVerification.java
java ControlFlowScopeVerification
~~~

Stdout xác định:

~~~text
outside-before depth=0 cflow=false below=false
P:outer depth=0 cflow=true below=false
child:first depth=1 cflow=true below=true
P:inner depth=1 cflow=true below=true
child:inside-inner depth=2 cflow=true below=true
child:after-inner depth=1 cflow=true below=true
async-worker depth=0 cflow=false below=false
outside-after depth=0 cflow=false below=false
P:short depth=0 cflow=true below=false
after-short depth=0 cflow=false below=false
P:error depth=0 cflow=true below=false
child:before-error depth=1 cflow=true below=true
CAUGHT boom
after-error depth=0 cflow=false below=false
~~~

### 6.1 Ca root ngoài cùng: tránh off-by-one

Ngay tại event `P:outer`, depth **trước khi push P** là 0. `cflow` true vì **chính event khớp P**, còn `below` false vì chưa có P ancestor nào.

Nếu vô tình push root vào ACTIVE trước khi tính, code có thể suy `below=true` cho P ngoài. Một test chỉ kiểm child sẽ không phát hiện bug này; vì vậy test root là **bắt buộc**.

### 6.2 Ca root lồng: không loại mọi P occurrence

Tại `P:inner`, stack đã có `outer`, nên `depth=1`. Inner là P occurrence **mới**, nhưng cũng nằm dưới outer P. Kỳ vọng `cflow=true`, `below=true`.

Đây là **case phân biệt ngữ nghĩa**: một matcher sai kiểu `below = depth > 0 && !matchesP` sẽ trượt ca inner root, dù qua test outer root/leaf bình thường.

### 6.3 Ca child bên trong và sau root lồng

`child:inside-inner` thấy hai root active: outer và inner. `child:after-inner` chỉ còn outer sau khi inner kết thúc. Cả hai vẫn được chọn bởi `cflow` và `below`.

Bộ test này phát hiện lỗi **không pop stack khi inner root rời**: khi đó `child:after-inner` sẽ có depth 2 thay vì 1. Nếu chỉ ghi booleans, cả hai vẫn true và bug depth bị che.

### 6.4 Ca outside và cleanup

Khi `outer` kết thúc, `ACTIVE.remove()` xóa trạng thái của thread. Event `outside-after` có depth=0. Tương tự, khi `root("error")` ném lỗi, `finally` vẫn pop/remove; event `after-error` phải ở ngoài phạm vi.

**Lỗi điển hình**: chỉ xóa marker khi thành công, dẫn tới một request sau trên thread pool bị đánh dấu nhầm bên trong control flow của request trước.

### 6.5 Ca short-circuit

`root("short", ...)` có **root event**, nhưng body rỗng, không có child occurrence. Không thể yêu cầu một advice ở `cflowbelow(P)` ghi sự kiện cho một method không bao giờ chạy. Kỳ vọng là **không có dòng `child:short`**, chứ không phải một dòng child có `below=false`.

### 6.6 Ca async: một thread khác không tự kế thừa dynamic stack

`workerProbe(pool)` được gọi **khi root outer còn active trên caller thread**, nhưng callable thực thi trên một worker khác. Worker không có stack ACTIVE của caller nên depth=0, cả hai booleans false.

Đây là mô hình **per-thread dynamic stack**. Nó không cấm ứng dụng **chủ động truyền trace/correlation context** qua async boundary. Tuy nhiên, **logical context propagation** không đồng nhất với câu hỏi một method execution có thuộc cùng **dynamic call-stack control flow** hay không.

### 6.7 Mã này chứng minh đến đâu?

Nó minh họa **oracle và các quy tắc kiểm chứng** cho phạm vi P trong một instrumentation tự xây. Nó **không chứng minh** rằng:
- Java tự có một pointcut `cflow/cflowbelow` ngầm.
- Bất cứ advice nào ở bất cứ công cụ nào cũng quan sát được toàn bộ method execution.
- Mọi async task đều nằm ngoài mọi định nghĩa logic operation (những framework tracing có thể định nghĩa khái niệm flow mở rộng).
- Một `ThreadLocal` chứa root stack có thể dùng thay security context.
- Demo tự bao phủ tất cả loại join point, annotations, generics hoặc các đường gọi bypass.

Một test **cơ chế AOP thực tế** phải lấy sơ đồ này làm **bảng expected**, sau đó cấu hình **pointcut thật**, tạo những lời gọi thật và quan sát advice tương ứng. Không dùng implementation mô phỏng làm bằng chứng nền tảng có khả năng weaving mà nó không hỗ trợ.

## 7. Ví dụ Java 17 thứ hai: kiểm chứng giới hạn proxy không phải cflow

Một tình huống dễ chẩn đoán sai: child đang **nằm dưới root về mặt call stack**, nhưng advice đặt trên proxy **không nhận được lời gọi child**. Nếu chỉ nhìn “advice không chạy” rồi kết luận child ở ngoài `cflow`, ta đã nhầm **phạm vi động** với **khả năng intercept**.

Chương trình JDK dynamic proxy sau minh họa đúng ranh giới. `Real.outer()` gọi `this.inner()` trên chính target; `InvocationHandler` chỉ thấy lời gọi **đi qua proxy**.

Tạo file `ProxyBoundaryVerification.java`:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class ProxyBoundaryVerification {
    interface Work {
        void outer();
        void inner();
    }

    static final class Real implements Work {
        @Override
        public void outer() {
            System.out.println("TARGET outer");
            this.inner();
        }

        @Override
        public void inner() {
            System.out.println("TARGET inner");
        }
    }

    static Work wrap(Work target) {
        return (Work) Proxy.newProxyInstance(
                Work.class.getClassLoader(),
                new Class<?>[] { Work.class },
                (proxy, method, args) -> {
                    System.out.println("HANDLER " + method.getName());
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                });
    }

    public static void main(String[] args) {
        Real original = new Real();
        Work proxy = wrap(original);

        System.out.println("CASE through-proxy-outer");
        proxy.outer();

        System.out.println("CASE through-proxy-inner");
        proxy.inner();

        System.out.println("CASE direct-inner");
        original.inner();
    }
}
~~~

Lệnh chạy:

~~~bash
javac ProxyBoundaryVerification.java
java ProxyBoundaryVerification
~~~

Stdout xác định:

~~~text
CASE through-proxy-outer
HANDLER outer
TARGET outer
TARGET inner
CASE through-proxy-inner
HANDLER inner
TARGET inner
CASE direct-inner
TARGET inner
~~~

### 7.1 Bằng chứng về coverage

- Qua `proxy.outer()`: handler in `HANDLER outer`, target chạy outer rồi **tự gọi** inner. Không có `HANDLER inner` cho lời gọi này, vì `this.inner()` nằm trên target `Real`.
- Qua `proxy.inner()`: handler thấy inner.
- Qua `original.inner()`: bypass proxy hoàn toàn.

Do đó: **“target inner được thực thi”** và **“advice trong invocation handler chạy cho inner”** là hai mệnh đề khác nhau.

### 7.2 Không tuyệt đối hóa với mọi loại weaving

Một mô hình **bytecode weaving ở method execution** có thể quan sát cả inner method execution dù được gọi từ `this`; một cơ chế subclass proxy có thể có dispatch khác mô hình delegation nêu trên. Ví dụ chỉ khẳng định khả năng của **JDK interface proxy cụ thể được code tạo ra**.

Khi thiết kế test `cflowbelow(P)` của một bộ AOP, phải xác nhận công cụ có **khả năng quan sát child join point** trước khi dùng việc thiếu log để suy về control flow.

## 8. Ranh giới call và execution phải được kiểm chứng riêng

Trong những mô hình AOP phân biệt **method call** và **method execution**, một thao tác nguồn có thể có hai loại join point:

~~~text
Caller.doSomething()
   |
   | CALL of Target.handle(...)
   v
Target.handle(...)
   | EXECUTION of handle
   v
 business body
~~~

Nếu P là `call(Target.handle)`, root là **điểm phát sinh lời gọi**. Nếu P là `execution(Target.handle)`, root là **điểm thực thi phần thân**. Trong một call đơn giản hai phạm vi có thể gần như trùng nhau, nhưng:
- CALL có thể xảy ra rồi target execution chưa bắt đầu hoặc không thành công do dispatch.
- EXECUTION có thể bắt đầu từ một đường call không được pointcut/cơ chế hiện tại nhìn thấy.
- Caller-side `this` của call có thể khác target của execution.
- Proxy handler có thể thêm một boundary mà không thay thế mọi execution join point.

Một test đủ chặt cần ghi:
1. Tên loại join point.
2. Nguồn khai báo/target, đúng overload/descriptor.
3. Thời điểm root event được quan sát.
4. Các child events có thực sự nằm trong phần thân được instrument hay không.
5. Kỳ vọng khi lời gọi lỗi trước khi target body bắt đầu.
6. Kỳ vọng khi gọi cùng method qua một đường khác.

**Không gọi một phương thức cùng tên là cùng occurrence.** Nếu `handle()` được gọi hai lần, hai execution occurrences khác nhau; P có thể active trong lần này và không active ở lần kia.

## 9. Lồng nhau và đệ quy: test không được chỉ có một P

Để kiểm tra đầy đủ `cflowbelow(P)`, tạo cả:
- Một P **ngoài cùng**.
- Một P **bên trong**, được gọi khi P ngoài vẫn active.
- Một child **bên trong P trong**.
- Một child **sau khi P trong kết thúc nhưng trước khi P ngoài kết thúc**.
- Một invocation **ngoài mọi P**.

Ví dụ với P chọn execution của `calculate(int)`:

~~~text
P calculate(2)                 <- outer P: below false
   |
   +--> P calculate(1)         <- inner P: below true
   |      |
   |      +--> leaf()          <- below true
   |
   +--> finish()               <- below true; inner đã thoát
~~~

Một matcher sai có thể:
- Tính `below=false` trên mọi occurrence cũng match P.
- Quên giảm depth khi P trong thoát.
- Chỉ lưu boolean và ghi đè trong recursion.
- Chỉ kiểm một root ID mà bỏ qua root lồng.
- Tính depth sau khi push root nhưng lại dùng depth ấy để kết luận root ngoài thuộc below.

Các lỗi này thường **không lộ trong test chỉ có outer P + one leaf**.

### 9.1 Root ID và binding

Nếu P có binding giá trị `requestId` hoặc tham số, nested P có thể tạo nhiều context:

~~~text
P(request=A)
   |
   +--> P(request=B)
          |
          +--> child
~~~

Child nằm dưới cả P(A) và P(B). Nếu advice cần giá trị root, phải xác định dùng **root gần nhất**, **root ngoài cùng** hay một ngữ nghĩa khác do ngôn ngữ AOP quy định. Không mặc định một biến `currentRequest` toàn cục có thể trả lời đúng mọi trường hợp.

Một test binding nên dùng **hai giá trị phân biệt** và assert value được truyền tại child, không chỉ assert `true` cho membership. Khi có security context, không dùng giá trị binding từ pointcut như chứng cứ quyền nếu nguồn chưa được xác thực.

### 9.2 Kiểm tra cả thời điểm thoát và quay lại

Test chỉ ghi lại **khi root vào** không chứng minh root được dọn đúng. Sau khi P trong kết thúc phải còn root ngoài; sau khi P ngoài kết thúc phải về không. Nhánh exception phải cho kết quả tương tự nhánh return bình thường.

Với instrumentation tự viết, đặt pop/remove trong `finally`, không chỉ trong nhánh thành công.

## 10. Thiết kế thử nghiệm cho exception, short-circuit và retry

### 10.1 Exception

Tạo một child ném exception:

~~~text
P enters
  child throws
P finally exits
outside executes
~~~

Yêu cầu:
- Child là điểm có occurrence thực, nên trước lúc ném nó có thể nằm dưới P.
- Sau khi P thoát qua exception, outside không nằm trong P.
- Một **after-returning** advice không xuất hiện khi join point ném lỗi **không chứng minh cflow sai**; cần phân biệt loại advice và nhánh execution.
- Một **after-throwing** hoặc finally-like advice có điều kiện riêng, không được dùng thay matcher membership.

### 10.2 Short-circuit

Nếu around advice ở P **không gọi continuation**, các child dự kiến trong target body có thể **không xảy ra**:

~~~text
P/around before
  deny or cache hit
return without proceed

(no business child event)
~~~

Test đúng phải kiểm tra **sự vắng mặt của child event**. Không sinh một event giả với membership=false rồi ghi “child outside P”: nó chưa từng là join point runtime.

### 10.3 Retry

Nếu around advice gọi continuation nhiều lần theo hợp đồng của công cụ, một request logic có thể có **nhiều occurrences của target/child**. Metric số join point không tự là số request. Thiết kế test cần phân biệt:
- Request count.
- Attempt count.
- Execution/join-point occurrence count.
- Success/failure count.
- Retry limit và side effects.

Không retry thao tác có side effect bừa bãi chỉ để thu thêm log; cần idempotency/dedup và failure policy phù hợp.

### 10.4 Advice chính nó tạo join point

Một logging advice có thể gọi formatter, exporter hoặc sink nằm trong tập pointcut. Khi đó advice tự sinh thêm join point ở dưới P, có thể tạo recursion. Tách infrastructure khỏi phạm vi lựa chọn phù hợp hoặc dùng guard có chính sách rõ cho concern **được phép** giảm ghi nhận, không bỏ qua authorization/audit bắt buộc.

## 11. Đồng thời và bất đồng bộ: hai loại “flow” khác nhau

Trong ví dụ đồng bộ, dynamic cflow thường được hiểu theo một đường thực thi đang active. Khi `P()` gửi task sang executor:

~~~text
Thread T1                       Thread T2
P execution starts
  executor.submit(task) ---------> task starts
P execution ends                    task may continue
~~~

Nếu task thực thi trên T2, **stack của T2 không tự chứa P occurrence từ T1**. Một `ThreadLocal` trên caller không tự được truyền sang executor worker.

Phân biệt:
- **Dynamic call flow:** đường lời gọi/thực thi còn active; là cơ sở của `cflow/cflowbelow` theo nghĩa stack thông thường.
- **Logical operation:** request/tracing workflow có thể kéo dài sau khi method return.
- **Causal relationship:** task được scheduling do P, nhưng chưa chắc nằm trong dynamic stack P.
- **Explicit context propagation:** snapshot correlation/trace/tenant metadata được truyền có chủ đích qua task/network.

Một trace ID được truyền sang worker **không tự chứng minh `cflow(P)` true** trong ngôn ngữ pointcut. Một công cụ AOP có thể có mở rộng riêng cho async flow, nhưng phải đọc định nghĩa chính thức của cơ chế đó trước khi dùng.

### 11.1 Negative test quan trọng

Tạo một executor worker không có P đang active và gửi một callable trong lúc caller đang ở P. Test worker depth=0 trong mô hình stack địa phương. Nếu dùng một biến `static boolean inP` chung, worker có thể đọc true sai; ca này sẽ vạch ra lỗi cross-thread contamination.

### 11.2 Nếu muốn test logical context propagation

Đó là **một bài test khác**:
1. Caller capture snapshot an toàn ở submission.
2. Task wrapper install snapshot trên worker.
3. Task thực hiện và đọc correlation context.
4. Worker restore/remove trong `finally`.
5. Task kế tiếp không thấy context cũ.

Không trộn kết luận của bài test logical propagation vào kỳ vọng `cflowbelow(P)` **nếu công cụ không công bố cùng semantics**.

## 12. Điểm quan sát phải được phân loại

Một test trace nên có schema tối thiểu:

~~~text
operation=checkout
joinPointKind=execution
eventId=child-2
matchesRootP=false
activePAncestors=1
expectedCflow=true
expectedBelow=true
~~~

Các trường có giá trị:
- **eventId:** phân biệt occurrence, nhất là đệ quy/cùng method tên.
- **joinPointKind:** call/execution/proxy boundary.
- **matchesRootP:** occurrence hiện tại có thỏa P hay không.
- **activePAncestors:** P occurrences phía ngoài còn active.
- **expectedCflow/expectedBelow:** oracle đã viết độc lập.
- **actual advice hits:** khi chạy trong công cụ thực, advice nào được vào.
- **execution thread/flow ID:** chỉ dùng để phân biệt đường, không coi thread ID là identity người dùng.

Tránh:
- Log toàn bộ `args` hoặc payload.
- In `target.toString()` khi chưa kiểm duyệt dữ liệu.
- Lưu access token/credential.
- Chuyển exception message thô vào audit khi có bí mật.
- Dùng một global mutable `currentUser` để gắn các event.

Một test định danh root không cần PII. Dùng ID giả ngắn và event label cố định; kết quả phải kiểm chứng được từ text đơn giản.

## 13. Cách chạy kiểm chứng với một bộ AOP thực tế

Sau khi có một **mô hình oracle Java thuần**, để kiểm chứng một hệ thống AOP thật:
1. **Khai báo P chính xác**, gồm loại call/execution, method signature và ranh giới scope.
2. Viết advice **quan sát có kiểm soát**, không sửa args/result hay policy trong lần kiểm thử đầu.
3. Tạo một fixture **cố định đường gọi**, tương ứng với bảng T0–T17.
4. Ghi event mỗi join point theo tên phân biệt, không đếm mù.
5. Chạy và so sánh **đủ giá trị lẫn các event không xuất hiện**.
6. Kiểm tra nesting/recursion và exception cleanup.
7. Kiểm tra các điểm mà cơ chế không thể instrument (direct/proxy/self-call, private/static khi thích hợp).
8. Khi sai, xác định **matcher, weaving coverage, advice type, runtime condition hay telemetry** gây ra sai lệch.
9. Pin phiên bản weaver/runtime khi muốn regression ổn định.
10. Với security policy quan trọng, kiểm tra **tất cả đường vào** chứ không dựa duy nhất vào control-flow pointcut.

**Không giả vờ thiếu log luôn là pointcut false.** Một lời gọi có thể không qua proxy; một before advice bên ngoài có thể ném lỗi; around advice có thể short-circuit; logger có thể bị filter hoặc async exporter chưa flush.

### 13.1 Trình tự chẩn đoán có chứng cứ

~~~text
Observed event missing
     |
     +--> Join point really executed?
     |      |
     |      +-- No: branch/short-circuit; no event expected.
     |      +-- Yes
     |           |
     |           +--> Mechanism intercepts this kind of join point?
     |                  |
     |                  +-- No: instrumentation coverage issue.
     |                  +-- Yes
     |                       |
     |                       +--> P ancestor active for this occurrence?
     |                              |
     |                              +-- No: cflow condition false.
     |                              +-- Yes
     |                                   |
     |                                   +--> cflow vs cflowbelow?
     |                                   +--> advice type/runtime filter?
     |                                   +--> logging/telemetry visible?
~~~

Đây là **sơ đồ chẩn đoán**, không phải một API hay thuật toán mặc định. Mục tiêu là không sửa pointcut trước khi biết nguyên nhân mất event.

## 14. Một số ngộ nhận mà bộ test cần bác bỏ

1. **“`cflow(P)` và `cflowbelow(P)` khác nhau ở mọi child.”** Sai: với child dưới P, cả hai thường true; khác biệt rõ ở root ngoài.
2. **“`cflowbelow(P)` loại mọi điểm cũng khớp P.”** Sai với P lồng dưới P khác.
3. **“Chỉ cần một root và một child là kiểm chứng đủ.”** Sai: chưa phủ nested/recursive P, exception và outside.
4. **“Expected lấy từ cùng matcher đang được test vẫn ổn.”** Test có thể đồng thời sai cả expected và actual.
5. **“Không có log advice nghĩa không nằm trong flow.”** Có thể là proxy bypass, wrong advice type hoặc log filter.
6. **“Một method nằm trong cùng class với P thì thuộc cflow(P).”** Dynamic flow không là lexical scope.
7. **“Call graph tĩnh có cạnh P→child nghĩa child luôn chạy.”** Chỉ khi đường gọi thực sự xảy ra.
8. **“Một `static boolean` là đủ mô phỏng P active trên toàn JVM.”** Có race và cross-thread contamination.
9. **“Root bên trong recursion phải bị bỏ khỏi below vì nó khớp P.”** Không: nó là hậu duệ của P ngoài.
10. **“Sau exception, root marker vẫn active cho request kế.”** Phải cleanup trong finally.
11. **“Task được submit bên trong P tự giữ `cflow(P)`.”** Không mặc định qua worker khác.
12. **“Truyền trace context biến worker thành con của P trong dynamic stack.”** Hai loại quan hệ khác nhau.
13. **“Proxy thấy outer thì chắc cũng thấy self-invocation inner.”** Không trong mọi mô hình delegation.
14. **“After-returning không chạy là lỗi control flow.”** Có thể là exception path.
15. **“Around advice không proceed nhưng child vẫn phải match.”** Child không có occurrence nếu body không chạy.
16. **“Cộng thời gian tất cả child luôn bằng root latency.”** Có thể double-count do nesting.
17. **“Join point cùng tên là cùng occurrence.”** Đệ quy và lặp tạo occurrence khác nhau.
18. **“Mọi công cụ AOP hỗ trợ call và execution như nhau.”** Phải kiểm tra capability thực tế.
19. **“Test chạy một lần ra đúng stdout chứng minh không có race.”** Concurrency cần test riêng.
20. **“Quan sát root đủ để khẳng định audit/security đầy đủ.”** Đường vào không qua root có thể bypass.

## 15. Checklist xây bài thực hành chất lượng

- [ ] Viết một câu ngắn mô tả **P chọn join point loại gì**.
- [ ] Viết rõ **`cflow` bao root; `cflowbelow` cần P ancestor**.
- [ ] Lập bảng expected cho root/child/outside trước khi code.
- [ ] Thêm **một P lồng/đệ quy** với expected below=true.
- [ ] Kiểm tra child sau khi inner P rời nhưng outer P còn active.
- [ ] Kiểm tra method giống nhau nhưng chạy ngoài P.
- [ ] Thêm nhánh không thực thi để khẳng định **không có event giả**.
- [ ] Thêm short-circuit để phân biệt missing join point và false pointcut.
- [ ] Thêm exception và kiểm tra cleanup bằng event sau root.
- [ ] Thêm task async và phân biệt dynamic stack với logical trace.
- [ ] Nếu dùng proxy, kiểm tra direct-call/self-invocation boundary.
- [ ] Ghi tên occurrence khác nhau cho các lần gọi cùng method.
- [ ] Ghi expected độc lập, không gọi lại matcher để tạo expected.
- [ ] So sánh đủ **event values, sequence, hit count và absence**.
- [ ] Tách **matcher problem** khỏi **instrumentation coverage**.
- [ ] Không coi missing after-returning trên exception là false cflow.
- [ ] Dùng dữ liệu giả; không log raw args, secrets hoặc principal.
- [ ] Đảm bảo Java example có source/class filename, lệnh javac/java và stdout khớp.
- [ ] Định nghĩa những semantics phụ thuộc model/weaver là **giới hạn**, không bịa bảo đảm chung.
- [ ] Đưa những ca đã phát hiện lỗi vào regression suite.

## 16. Câu hỏi tự kiểm tra

**Câu 1.** Một P ngoài cùng vừa xảy ra. Expected `cflow(P)` và `cflowbelow(P)` tại occurrence ấy là gì?

**Đáp:** cflow=true, below=false nếu không có P khác đang bao quanh.

**Câu 2.** Một method con chạy trong P. Cả hai có thể true không?

**Đáp:** có. Child nằm dưới root P active.

**Câu 3.** P gọi đệ quy chính P. Tại P trong, below có thể true không?

**Đáp:** có. P trong nằm dưới P ngoài, dù tự khớp P.

**Câu 4.** Child được gọi từ đường ngoài P nhưng cùng chữ ký với một child trong P. Có tự ở trong cflow không?

**Đáp:** không. Membership phụ thuộc đường thực thi và P active, không chỉ method signature.

**Câu 5.** Root trả về qua exception; test nào chứng minh cleanup?

**Đáp:** gọi một event ngoài sau khi catch và assert depth=0, cflow=false, below=false.

**Câu 6.** Advice thiếu log cho inner trong proxy delegation. Có thể dùng kết quả đó để khẳng định inner ở ngoài P không?

**Đáp:** không. Inner có thể nằm dưới P về call stack nhưng bypass proxy qua self-invocation; phải kiểm tra coverage.

**Câu 7.** Bằng cách nào kiểm tra P short-circuit đúng?

**Đáp:** root event có mặt, nhưng **không có child occurrence**. Không tạo child event giả.

**Câu 8.** Tại sao expected cần viết trước khi chạy matcher?

**Đáp:** để tránh test tính lại cùng một logic sai và tự xác nhận; oracle phải độc lập.

**Câu 9.** Nếu executor task chạy trên thread khác khi caller vẫn ở P, task tự thuộc cflow(P) không?

**Đáp:** không mặc định theo ngữ nghĩa dynamic stack; truyền trace context là cơ chế riêng.

**Câu 10.** Trong ví dụ Java, tại sao `P:inner` có `depth=1` nhưng `child:inside-inner` có `depth=2`?

**Đáp:** depth trong event đo **số P ancestors active trước occurrence hiện tại**. P trong đang có P ngoài là một ancestor; child bên trong có cả hai P active.

## 17. Tóm kết phương pháp

Một ví dụ kiểm chứng **phạm vi điều khiển** tốt phải trả lời được ba nhóm câu hỏi:

1. **Ngữ nghĩa:** root P nào đang active, occurrence hiện tại chính là P hay nằm dưới một P ancestor, và `cflow(P)` khác `cflowbelow(P)` ở đâu?
2. **Khả năng quan sát:** join point có thực sự xảy ra, được weaver/proxy hỗ trợ và advice phù hợp có được vào hay không?
3. **Chứng cứ:** bảng expected độc lập có đủ positive/negative/boundary cases, test có kiểm tra exception cleanup, recursion, short-circuit và async không?

**Quy tắc dễ nhớ:** thiết kế **bảng kỳ vọng trước**, dựng một fixture nhỏ có **root–child–nested P–outside**, rồi thêm những tình huống có thể **bác bỏ lời giải sai**. Một ví dụ chỉ cho thấy advice chạy trong happy path chưa đủ chứng minh control-flow scope đúng.


</details>

- [Quay lại đầu trang](#back-to-top)
