<a id="back-to-top"></a>

# Kiểu lồng

## Menu
- [Interface lồng trong lớp và interface lồng trong interface](#nested-types-001)
- [Interface lồng ngầm định static và không có đối tượng bao ngoài](#nested-types-002)
- [Phạm vi truy cập của kiểu lồng và cách tham chiếu](#nested-types-003)
- [Lớp thành viên bên trong interface và modifier ngầm định](#nested-types-004)
- [Triển khai bằng local class và anonymous class](#nested-types-005)
- [Khởi tạo anonymous subclass của abstract class](#nested-types-006)
- [So sánh anonymous class với lambda về this và phạm vi tên](#nested-types-007)
- [Biến được bắt giữ và quy tắc effectively final](#nested-types-008)

## <a id="nested-types-001">Interface lồng trong lớp và interface lồng trong interface</a>

<details>
<summary>Xem chi tiết</summary>

# Interface lồng trong lớp và interface lồng trong interface

Bài này giải thích cách khai báo, truy cập và sử dụng một interface được đặt bên trong lớp hoặc bên trong một interface khác trong Java. Hai trường hợp có cùng quy tắc cốt lõi: member interface luôn là kiểu `static`, nên nó không cần một đối tượng của kiểu bao ngoài.

## 1. Khái niệm

**Nested interface** là interface được khai báo bên trong một kiểu khác. Khi interface được khai báo trực tiếp như thành viên của một lớp hoặc interface, nó được gọi là **member interface**.

- **Interface lồng trong lớp:** nhóm một hợp đồng nhỏ với lớp liên quan, chẳng hạn callback chỉ phục vụ một API cụ thể.
- **Interface lồng trong interface:** nhóm một kiểu phụ trợ với hợp đồng API mà nó mô tả, chẳng hạn `Listener` bên trong `EventSource`.
- Interface lồng vẫn là một kiểu độc lập. Nó có thể được lớp khác triển khai, được dùng làm kiểu tham số hoặc kiểu trả về, và có thể là kiểu đích của lambda nếu chỉ có một phương thức trừu tượng.

## 2. Quy tắc ngôn ngữ Java

### Interface lồng trong lớp

```java
class TaskRunner {
    public interface CompletionCallback {
        void onComplete(String result);
    }
}
```

- `CompletionCallback` là member interface của `TaskRunner` và **ngầm định là `static`**, dù từ khóa `static` không xuất hiện. Viết `public static interface CompletionCallback` cũng hợp lệ nhưng `static` là dư thừa.
- Trong một lớp, interface lồng có thể dùng mức truy cập `public`, `protected`, `private` hoặc package-private. Chọn mức truy cập theo nơi cần sử dụng hợp đồng.
- Từ bên ngoài lớp bao ngoài, tên kiểu thường được viết đầy đủ là `TaskRunner.CompletionCallback`. Bên trong phạm vi có thể nhìn thấy tên thành viên, có thể dùng tên ngắn `CompletionCallback`.
- Vì interface lồng là `static`, nó không giữ tham chiếu ngầm tới một đối tượng `TaskRunner`. Không cần tạo `TaskRunner` để truy cập kiểu này.
- Khai báo interface bên trong một lớp **không tự động làm cho lớp bao ngoài triển khai interface đó**. Nếu cần triển khai, phải khai báo `implements TaskRunner.CompletionCallback` ở một lớp phù hợp.

### Interface lồng trong interface

```java
interface EventSource {
    void start();

    interface Listener {
        void onEvent(String event);
    }
}
```

- `Listener` được ngầm định là `public static`. Có thể ghi rõ `public static interface Listener`, nhưng hai từ khóa này là dư thừa trong ngữ cảnh đó.
- Từ bên ngoài, tên kiểu là `EventSource.Listener`. Kiểu lồng vẫn chịu ảnh hưởng bởi khả năng truy cập của `EventSource`: nếu interface bao ngoài không thể truy cập từ một nơi nào đó, tên kiểu lồng cũng không thể được dùng từ nơi đó.
- Các phương thức thông thường khai báo trong interface là `public abstract` ngầm định, trừ khi được khai báo với dạng khác được Java cho phép như `default`, `static` hoặc `private` (private methods trong interface được hỗ trợ từ Java 9).
- Không khai báo `private` hoặc `protected` cho member type trong interface; member type của interface luôn công khai và tĩnh theo quy tắc ngôn ngữ.

### Điểm chung cần nhớ

1. Member interface luôn `static`; không dùng cú pháp tạo đối tượng kiểu `outer.new NestedInterface()`.
2. Interface không thể được khởi tạo trực tiếp bằng `new`. Hãy cung cấp một lớp triển khai, lớp ẩn danh hoặc lambda phù hợp.
3. Tên kiểu lồng được phân tách bằng dấu chấm, ví dụ `TaskRunner.CompletionCallback` và `EventSource.Listener`.
4. Nếu interface chỉ có một phương thức trừu tượng, nó là functional interface và có thể dùng làm kiểu đích cho lambda. Các phương thức `default` và `static` không làm tăng số phương thức trừu tượng.

## 3. Ví dụ chạy được với Java 17+

Lưu toàn bộ đoạn mã sau vào tệp `NestedInterfacesDemo.java`. Ví dụ có một interface lồng trong lớp `NestedInterfacesDemo` và một interface lồng trong interface `MessageSource`.

```java
public class NestedInterfacesDemo {
    // Member interface trong lớp: static là ngầm định.
    public interface CompletionCallback {
        void onComplete(String message);
    }

    public static void run(CompletionCallback callback) {
        callback.onComplete("job done");
    }

    public static void main(String[] args) {
        CompletionCallback callback =
                message -> System.out.println("callback: " + message);
        run(callback);

        // Listener là member interface của MessageSource.
        MessageSource.Listener listener =
                message -> System.out.println("event: " + message);

        MessageSource source = (target, message) -> target.onMessage(message);
        source.publish(listener, "ready");
    }
}

interface MessageSource {
    void publish(Listener listener, String message);

    // Trong interface, Listener ngầm định là public static.
    interface Listener {
        void onMessage(String message);
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```shell
javac NestedInterfacesDemo.java
java NestedInterfacesDemo
```

Kết quả stdout chính xác:

```text
callback: job done
event: ready
```

Trong ví dụ, `CompletionCallback` là kiểu tham số cho `run`. Lambda cung cấp phần triển khai của `onComplete`. Tương tự, `MessageSource.Listener` mô tả cách nhận thông điệp; lambda thứ hai triển khai listener, còn lambda cho `MessageSource` cung cấp hành vi của phương thức `publish`.

## 4. Truy cập và triển khai interface lồng

### Dùng tên kiểu đầy đủ

```java
TaskRunner.CompletionCallback callback = result ->
        System.out.println(result);

EventSource.Listener listener = event ->
        System.out.println("Nhận sự kiện: " + event);
```

Đây là cách viết thường dùng khi tham chiếu từ một lớp khác. Kiểu lồng vẫn được truy cập qua kiểu bao ngoài, không qua một đối tượng của kiểu bao ngoài.

### Dùng lớp triển khai tường minh

```java
class ConsoleListener implements EventSource.Listener {
    @Override
    public void onEvent(String event) {
        System.out.println("Nhận sự kiện: " + event);
    }
}
```

`ConsoleListener` triển khai hợp đồng `EventSource.Listener`. Vì `onEvent` là phương thức `public` của interface, phương thức triển khai cũng phải có quyền truy cập `public`; giảm mức truy cập sẽ gây lỗi biên dịch.

## 5. Khi nào nên lồng interface?

- Đặt interface trong lớp khi hợp đồng gắn chặt với lớp đó và không cần trở thành khái niệm độc lập trong API. Ví dụ: callback riêng cho một bộ xử lý công việc.
- Đặt interface trong interface khi kiểu phụ trợ có ý nghĩa như một phần của hợp đồng công khai. Ví dụ: `EventSource.Listener` mô tả cách nhận sự kiện do nguồn sự kiện phát ra.
- Để interface ở cấp cao nhất khi nhiều lớp hoặc nhiều gói cần sử dụng nó độc lập. Cách này giúp tên kiểu ngắn hơn và tránh phụ thuộc không cần thiết vào kiểu bao ngoài.
- Giữ độ sâu lồng hợp lý. Các tên như `A.B.C.Listener` có thể làm API khó đọc nếu việc lồng không đem lại lợi ích rõ ràng.

## 6. Lỗi thường gặp

| Lỗi hoặc hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| Viết `new TaskRunner.CompletionCallback()` để tạo interface | Interface không thể được khởi tạo trực tiếp. Dùng lambda nếu đó là functional interface, hoặc viết lớp triển khai/lớp ẩn danh. |
| Cho rằng cần `new TaskRunner()` để dùng `TaskRunner.CompletionCallback` | Member interface là `static`, nên không cần đối tượng của lớp bao ngoài. |
| Nghĩ lớp bao ngoài tự động triển khai interface lồng | Việc khai báo kiểu lồng không tạo quan hệ `implements`. Khai báo triển khai một cách tường minh ở lớp cần thiết. |
| Quên tên bao ngoài khi dùng kiểu từ nơi khác | Dùng dạng `Outer.Nested`, chẳng hạn `EventSource.Listener`, và bảo đảm kiểu bao ngoài có thể truy cập được. |
| Nghĩ interface lồng trong interface có thể là `private` hoặc `protected` | Member type của interface ngầm định là `public static`; không thể giảm quyền truy cập của nó bằng `private` hoặc `protected`. |
| Dùng lambda cho interface có nhiều phương thức trừu tượng | Lambda chỉ phù hợp khi kiểu đích là functional interface, tức có đúng một phương thức trừu tượng theo quy tắc Java. |
| Cho rằng interface lồng có thể truy cập trực tiếp trường instance của lớp bao ngoài | Vì member interface là `static`, nó không có đối tượng bao ngoài ngầm định. Muốn dùng dữ liệu instance, hãy truyền đối tượng hoặc giá trị cần thiết vào. |

## 7. Checklist nhanh

- [ ] Xác định interface được khai báo trong lớp hay trong interface.
- [ ] Dùng tên kiểu đúng: `Outer.Nested` khi tham chiếu từ bên ngoài phạm vi bao ngoài.
- [ ] Kiểm tra mức truy cập của kiểu bao ngoài và interface lồng.
- [ ] Nhớ rằng member interface ngầm định là `static`; không cần tạo đối tượng bao ngoài.
- [ ] Chọn lambda chỉ khi interface là functional interface; nếu không, cung cấp một lớp triển khai đầy đủ.
- [ ] Với ví dụ chạy được, kiểm tra tên tệp trùng tên lớp `public`, lệnh biên dịch và stdout.

## 8. Kết luận về quy tắc

Khác biệt chính giữa hai vị trí khai báo nằm ở mức truy cập ngầm định: interface lồng trong lớp có mức truy cập do người viết chọn, còn member interface trong interface luôn là `public static`. Cả hai đều là member interface tĩnh, được tham chiếu bằng tên kiểu bao ngoài và có thể được triển khai hoặc dùng làm kiểu đích cho lambda khi thỏa điều kiện functional interface.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nested-types-002">Interface lồng ngầm định static và không có đối tượng bao ngoài</a>

<details>
<summary>Xem chi tiết</summary>

# Interface lồng trong Java: ngầm định static và không cần đối tượng bao ngoài

Một **member interface** khai báo trực tiếp bên trong class hay interface đều là kiểu lồng `static`, dù không viết từ khóa `static`. Điều quan trọng không chỉ là cú pháp `Outer.Listener` mà là hiểu kiểu đó **không mang theo một đối tượng `Outer`**. Bài này phân biệt hợp đồng lồng, đối tượng triển khai và nhu cầu dùng trạng thái của lớp bao ngoài.

## 1. `static` thuộc về kiểu, không thuộc về phương thức

```java
class Dispatcher {
    interface Listener {
        void onEvent(String event);
    }
}
```

`Dispatcher.Listener` là một member interface tĩnh. Phương thức `onEvent` vẫn là phương thức instance thuộc về **đối tượng triển khai `Listener`**. Interface lồng trong interface khác còn ngầm định `public`; interface lồng trong class có thể mang các mức truy cập thông thường của member type.

Việc khai báo `static` cho kiểu không biến `onEvent` thành phương thức tĩnh, cũng không buộc lớp triển khai là `static`.

## 2. Kiểu lồng và lớp triển khai có vòng đời độc lập

```java
class Dispatcher {
    interface Listener {
        void onEvent(String message);
    }

    class BoundListener implements Listener {
        @Override public void onEvent(String message) {
            System.out.println(message);
        }
    }
}
```

Ở nơi truy cập được hai kiểu:

```java
Dispatcher.Listener listenerTypeReference; // Chỉ khai báo tham chiếu kiểu
Dispatcher dispatcher = new Dispatcher();
Dispatcher.BoundListener bound = dispatcher.new BoundListener();
```

Kiểu `Dispatcher.Listener` không cần enclosing instance, nhưng `BoundListener` **là inner class không static** nên cần `dispatcher` khi khởi tạo. Nếu thay `BoundListener` bằng lớp cấp cao nhất triển khai `Dispatcher.Listener`, không còn nhu cầu này. Nhu cầu đối tượng bao ngoài thuộc về **lớp triển khai hoặc logic nghiệp vụ**, không thuộc về kiểu interface lồng.

## 3. Vì sao không cần đối tượng bao ngoài?

Một lớp lồng không static (inner class) thường mang theo liên kết với đối tượng bao ngoài đã tạo ra nó. Member interface thì khác: nó là kiểu thành viên static và không giữ một tham chiếu ngầm đến đối tượng Outer.

Điều đó mang lại một số hệ quả thực tế:

- Có thể dùng tên Outer.Listener trong khai báo biến, tham số, kiểu trả về và mệnh đề implements mà không cần có đối tượng Outer.
- Có thể triển khai Outer.Listener trong một class cấp cao nhất hoặc trong một lớp lồng static.
- Không có cú pháp tạo đối tượng bao ngoài kiểu outer.new Listener(); ngoài ra, Listener là interface nên không thể khởi tạo trực tiếp dù có hay không có Outer.
- Các phương thức của interface lồng không có một đối tượng Outer ngầm định để truy cập trực tiếp trường hoặc phương thức instance của Outer. Nếu cần dữ liệu từ một đối tượng Outer, hãy truyền dữ liệu đó qua tham số, cung cấp một phương thức trên Outer, hoặc truyền đối tượng Outer cho lớp triển khai theo thiết kế phù hợp.
- Kiểu lồng có thể truy cập các thành viên static của kiểu bao ngoài theo quy tắc truy cập Java. Điều này không tạo ra một liên kết đến bất kỳ đối tượng Outer nào.

Lưu ý: việc một interface lồng không cần đối tượng bao ngoài không có nghĩa mọi lớp triển khai nó đều không cần đối tượng bao ngoài. Nếu lớp triển khai được khai báo là inner class không static, chính lớp triển khai đó vẫn cần một đối tượng Outer khi tạo instance. Đây là đặc điểm của lớp triển khai, không phải của interface lồng.

## 4. Ví dụ Java chạy được

Ví dụ dưới đây khai báo Listener bên trong Main, sau đó triển khai interface bằng một class cấp cao nhất. ConsoleListener được tạo độc lập với một đối tượng Main; đối tượng Main chỉ được dùng để gọi phương thức publish.

~~~java
public class Main {
    private final String serviceName;

    public Main(String serviceName) {
        this.serviceName = serviceName;
    }

    public interface Listener {
        void onMessage(String message);
    }

    public void publish(String message, Listener listener) {
        listener.onMessage(serviceName + ": " + message);
    }

    public static void main(String[] args) {
        Main service = new Main("Service");
        Main.Listener listener = new ConsoleListener();
        service.publish("ready", listener);
    }
}

final class ConsoleListener implements Main.Listener {
    @Override
    public void onMessage(String message) {
        System.out.println(message);
    }
}
~~~

Lưu nội dung vào tệp Main.java, sau đó chạy bằng Java 17 trở lên:

~~~text
javac Main.java
java Main
~~~

Kết quả stdout chính xác:

~~~text
Service: ready
~~~

Dòng khai báo Main.Listener listener không yêu cầu một đối tượng Main để xác định kiểu Listener. ConsoleListener cũng không phải inner class của Main, nên có thể tạo nó trực tiếp bằng new ConsoleListener(). Phương thức publish được gọi trên service vì phương thức đó sử dụng trường instance serviceName; đây là nhu cầu riêng của hoạt động publish, không phải điều kiện để dùng kiểu Main.Listener.

## 5. Kiểm tra ba câu hỏi độc lập

| Câu hỏi | Cách xác định |
|---|---|
| Có cần tạo `Outer` để **tham chiếu kiểu** `Outer.Listener`? | Không. Member interface luôn static; việc gọi tên kiểu không tạo đối tượng. |
| Có cần `Outer` để **tạo implementation**? | Chỉ khi chính lớp triển khai là inner class không static hoặc constructor của nó cần dữ liệu đó. |
| Có cần `Outer` để **gọi hành vi**? | Tùy API. `publish` là instance method của `Main`, còn `onMessage` chạy trên đối tượng listener. |

## 6. Ba lỗi dễ lẫn

- Viết `outer.new Listener()`: interface không được khởi tạo trực tiếp và member interface không có enclosing instance. Hãy tạo một implementation hợp lệ.
- Viết `new Outer.BoundListener()` cho một **non-static inner class**: kiểu triển khai này cần `outer.new BoundListener()`; tính static của interface nó implements không thay đổi quy tắc đó.
- Truy cập `Outer.this` từ chính member interface như thể có đối tượng `Outer` gắn sẵn: không tồn tại tham chiếu ngầm định như vậy. Nếu hành vi cần trạng thái instance, hãy truyền nó qua phương thức hoặc implementation.

## 7. Tự kiểm tra

Thử đổi `ConsoleListener` trong ví dụ chạy được thành một non-static inner class của `Main`. Khi đó câu lệnh tạo listener phải thay đổi theo cú pháp `service.new ConsoleListener()` (nếu constructor và phạm vi truy cập phù hợp), nhưng khai báo kiểu `Main.Listener` vẫn giữ nguyên. Đây là phép thử ngắn để phân biệt **tính static của hợp đồng** với **vòng đời của lớp triển khai**.

## Ghi nhớ

`static` ở member interface mô tả quan hệ giữa **kiểu** và kiểu bao ngoài. Nó không quyết định liệu một implementation có enclosing instance, cũng không biến các phương thức trừu tượng thành phương thức static.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nested-types-003">Phạm vi truy cập của kiểu lồng và cách tham chiếu</a>

<details>
<summary>Xem chi tiết</summary>

# Phạm vi truy cập của kiểu lồng và cách tham chiếu trong Java

**Kiểu lồng (nested type)** là kiểu được khai báo bên trong một kiểu khác, chẳng hạn class, interface, enum hoặc record nằm trong một class. Khai báo lồng giúp nhóm những kiểu có quan hệ chặt chẽ, kiểm soát nơi có thể sử dụng chúng và thể hiện rõ kiểu nào thuộc về kiểu bao ngoài.

Để dùng kiểu lồng đúng cách, cần phân biệt hai câu hỏi: **kiểu được nhìn thấy ở đâu** (phạm vi và quyền truy cập) và **đối tượng được tạo hoặc kiểu được gọi bằng cú pháp nào** (tham chiếu kiểu lồng tĩnh hay kiểu lồng gắn với một đối tượng bên ngoài).

## 1. Các loại kiểu lồng

Java có một số cách khai báo kiểu bên trong kiểu hoặc khối lệnh khác:

- **Member nested class:** class được khai báo trực tiếp bên trong một class hoặc kiểu khác. Nó có thể là `static` hoặc non-static (inner class).
- **Local class:** class được khai báo trong thân một phương thức, constructor hoặc khối lệnh. Tên kiểu chỉ có hiệu lực trong phạm vi từ vị trí khai báo đến hết khối chứa nó.
- **Anonymous class:** lớp không có tên, được khai báo và khởi tạo tại một biểu thức; thường dùng để triển khai một interface hoặc mở rộng một class ngay tại chỗ.
- **Nested interface, enum và record:** các kiểu này có thể được khai báo bên trong kiểu khác. Nested interface, enum và record là kiểu lồng tĩnh về mặt ngữ nghĩa, nên không cần đối tượng của kiểu bao ngoài để tạo hoặc tham chiếu kiểu đó.

Không phải mọi kiểu được khai báo trong một class đều cần một instance của class bên ngoài. Điểm khác biệt quan trọng nhất là member class `static` so với member class non-static.

## 2. Phạm vi truy cập của member nested type

Với một kiểu lồng được khai báo trực tiếp trong kiểu khác, có thể dùng các mức truy cập `public`, `protected`, package-private (không ghi modifier) hoặc `private`. Các modifier này kiểm soát nơi mã khác được phép tham chiếu **tên kiểu lồng**.

| Modifier | Phạm vi sử dụng điển hình |
| --- | --- |
| `public` | Có thể được tham chiếu từ nơi nhìn thấy kiểu bao ngoài và kiểu lồng. |
| `protected` | Trong cùng package; ngoài package theo quy tắc truy cập `protected`, thường thông qua quan hệ kế thừa. |
| Không ghi modifier | Chỉ trong cùng package. |
| `private` | Trong phạm vi kiểu bao ngoài khai báo kiểu lồng đó. |

Ví dụ khai báo:

```java
public class Library {
    public static class PublicEntry { }
    protected static class ProtectedEntry { }
    static class PackageEntry { }
    private static class PrivateEntry { }
}
```

Các lớp bên ngoài có thể tham chiếu `Library.PublicEntry` khi các điều kiện truy cập khác cũng được đáp ứng. `Library.PrivateEntry` chỉ được dùng trong phạm vi `Library`; việc class bên ngoài có một đối tượng `Library` không làm nó có quyền truy cập kiểu private.

Một kiểu cấp cao nhất (top-level type) chỉ được khai báo `public` hoặc package-private; `private` và `protected` không hợp lệ cho top-level class/interface. Local class và anonymous class cũng không khai báo các modifier truy cập này.

## 3. `static` nested class và inner class khác nhau thế nào?

### `static` nested class

`static` nested class được gắn với tên kiểu bao ngoài, không gắn với một instance cụ thể của kiểu đó. Khi tạo nó, không cần tạo đối tượng bao ngoài trước.

```java
Library.Helper helper = new Library.Helper();
```

Ví dụ trên áp dụng nếu `Helper` là nested class có quyền truy cập phù hợp và constructor có thể được gọi từ vị trí đang viết mã. Từ bên trong `Library`, có thể thường dùng tên ngắn `Helper` nếu không có tên khác gây nhập nhằng.

`static` nested class không có tham chiếu ngầm định tới đối tượng bao ngoài. Vì vậy, nó không thể dùng trực tiếp các field hoặc method instance của `Library` như thể chúng thuộc về chính nó. Nếu cần dữ liệu instance, hãy truyền đối tượng vào qua constructor hoặc method:

```java
Library.Helper helper = new Library.Helper(library);
```

Constructor và chữ ký minh họa này cần được khai báo tương ứng trong `Helper`. Một static nested class vẫn có thể truy cập các thành viên `private` của kiểu bao ngoài khi có đối tượng tham chiếu thích hợp; `private` kiểm soát quyền truy cập theo ngôn ngữ, không bắt buộc phải là cùng một object.

### Non-static member class (inner class)

Inner class gắn với một instance cụ thể của kiểu bao ngoài. Muốn tạo nó từ bên ngoài phạm vi instance của kiểu bao ngoài, trước tiên cần có đối tượng bao ngoài rồi dùng cú pháp `outer.new InnerType()`.

```java
Library library = new Library();
Library.Worker worker = library.new Worker();
```

Đoạn này áp dụng nếu `Worker` là member inner class có thể truy cập được và constructor phù hợp có thể gọi từ vị trí đó. Mỗi đối tượng `Worker` được tạo theo cách này gắn với instance `library` cụ thể.

Bên trong inner class, có thể dùng trực tiếp các field và method instance của đối tượng bao ngoài. Khi cần làm rõ đối tượng đó, dùng `Library.this` (tên kiểu bao ngoài theo ví dụ) để lấy tham chiếu tới instance bao ngoài.

```java
System.out.println(Library.this.toString());
```

`Outer.this` chỉ dùng trong ngữ cảnh inner class thích hợp của `Outer`; không dùng được trong static nested class, static method hoặc mã không có enclosing instance tương ứng.

## 4. Truy cập thành viên `private` giữa kiểu bao ngoài và kiểu lồng

Kiểu lồng có thể truy cập các thành viên `private` của kiểu bao ngoài. Ngược lại, kiểu bao ngoài cũng có thể truy cập thành viên `private` của kiểu lồng. Đây là quyền truy cập giữa các kiểu thuộc cùng nhóm lồng (nest), được Java hỗ trợ theo quy tắc ngôn ngữ.

Điều này không làm các thành viên private trở thành public. Mã không liên quan ở bên ngoài vẫn không được phép truy cập trực tiếp chúng. Ngoài ra, quyền truy cập và việc có sẵn instance là hai vấn đề riêng:

- `static` nested class có thể đọc `Outer.privateStatic` trực tiếp trong phạm vi hợp lệ.
- Muốn dùng `Outer.privateInstance` từ static nested class, phải có một tham chiếu `Outer` thích hợp, ví dụ `outer.privateInstance`.
- Inner class có thể truy cập field hoặc method instance của enclosing object mà nó gắn với.
- `private` trên chính kiểu lồng vẫn giới hạn nơi khác có thể viết tên kiểu đó, kể cả khi constructor hoặc thành viên của kiểu có modifier rộng hơn.

## 5. Local class và anonymous class

### Local class

Local class được khai báo trong một block và chỉ dùng được trong block đó sau vị trí khai báo. Nó không có modifier truy cập như `public` hoặc `private` vì phạm vi đã được xác định bởi block.

Local class có thể truy cập các biến cục bộ và tham số của block bao quanh nếu chúng là `final` hoặc **effectively final** (giá trị không bị gán lại sau khi khởi tạo). Nếu được khai báo trong instance method, nó cũng có thể truy cập thành viên instance của enclosing object.

```java
void printPrefix(String prefix) {
    class Printer {
        void print(String value) {
            System.out.println(prefix + value);
        }
    }
    new Printer().print("Java");
}
```

Trong ví dụ, `prefix` được đọc nhưng không bị gán lại nên là effectively final. Nếu cố gán lại `prefix` sau khi dùng nó trong `Printer`, đoạn mã sẽ không biên dịch.

### Anonymous class

Anonymous class không có tên để khai báo biến theo kiểu lớp cụ thể. Biểu thức tạo lớp vô danh đồng thời tạo ra một instance, thường thông qua một interface hoặc superclass:

```java
Runnable task = new Runnable() {
    @Override
    public void run() {
        System.out.println("Running");
    }
};
task.run();
```

Không thể khai báo constructor có tên cho anonymous class vì nó không có tên lớp. Các method mới chỉ có trong phần thân anonymous class cũng không thể được gọi thông qua biến kiểu `Runnable` nếu không có một kiểu hoặc giao diện khai báo chúng.

## 6. Ví dụ Java hoàn chỉnh

Chương trình dưới đây cho thấy static nested class nhận tham chiếu tới đối tượng bao ngoài, inner class truy cập enclosing instance, cùng local và anonymous class được dùng trong phạm vi phương thức.

```java
public class NestedTypeAccessDemo {
    private final String name;
    private int visits;
    private static final String APPLICATION = "AccessDemo";

    public NestedTypeAccessDemo(String name) {
        this.name = name;
    }

    private void mark(String from) {
        visits++;
        System.out.println(from + " -> " + name + ", visits=" + visits);
    }

    private static class StaticNested {
        void show(NestedTypeAccessDemo outer) {
            System.out.println("static -> " + outer.name);
            outer.mark("static");
            System.out.println("app -> " + APPLICATION);
        }
    }

    private class Inner {
        void show() {
            System.out.println("inner -> " + name + " / " + NestedTypeAccessDemo.this.name);
            mark("inner");
        }
    }

    private void demonstrateScopedTypes() {
        class Local {
            void show() {
                System.out.println("local -> " + name);
            }
        }
        new Local().show();

        Runnable anonymous = new Runnable() {
            @Override
            public void run() {
                System.out.println("anonymous -> " + name);
            }
        };
        anonymous.run();
    }

    public static void main(String[] args) {
        NestedTypeAccessDemo outer = new NestedTypeAccessDemo("Ada");

        StaticNested nested = new StaticNested();
        nested.show(outer);

        Inner inner = outer.new Inner();
        inner.show();

        outer.demonstrateScopedTypes();
    }
}
```

Lưu mã vào `NestedTypeAccessDemo.java`, sau đó biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac NestedTypeAccessDemo.java
java NestedTypeAccessDemo
```

Stdout chính xác:

```text
static -> Ada
static -> Ada, visits=1
app -> AccessDemo
inner -> Ada / Ada
inner -> Ada, visits=2
local -> Ada
anonymous -> Ada
```

Trong `StaticNested.show`, lớp lồng đọc field private qua `outer.name` và gọi `outer.mark(...)` bằng tham chiếu được truyền vào. Nó không cần enclosing instance ngầm định. `Inner.show` dùng được `name` và `mark` qua enclosing instance; `NestedTypeAccessDemo.this.name` làm rõ chính instance đó. `Local` và anonymous class được tạo, sử dụng trong phương thức chứa chúng.

Các kiểu `StaticNested` và `Inner` được khai báo private để ví dụ thể hiện quyền truy cập bên trong `NestedTypeAccessDemo`. Nếu cần dùng chúng từ class khác, phải chọn modifier phù hợp và giữ constructor/kiểu tham chiếu có thể truy cập được.

## 7. Quy tắc tham chiếu cần nhớ

| Trường hợp | Cú pháp thường dùng |
| --- | --- |
| Tham chiếu kiểu lồng tĩnh từ bên ngoài | `Outer.Nested` |
| Tạo đối tượng của kiểu lồng tĩnh | `new Outer.Nested()` |
| Tham chiếu inner class từ bên ngoài | `Outer.Inner` |
| Tạo inner class gắn với một đối tượng | `outer.new Inner()` |
| Lấy enclosing instance từ inner class | `Outer.this` |
| Tham chiếu local class | Tên lớp chỉ trong block khai báo |
| Anonymous class | Tạo trực tiếp qua biểu thức `new Type() { ... }` |

Các cú pháp trong bảng chỉ hợp lệ khi kiểu và constructor có thể truy cập tại vị trí gọi, và khi kiểu được khai báo đúng loại. Ví dụ, `outer.new Inner()` không áp dụng cho static nested class.

## 8. Lỗi thường gặp

1. **Dùng `new Outer.Inner()` cho inner class:** cú pháp này thiếu instance bao ngoài. Dùng `outer.new Inner()`.
2. **Tạo `Outer.StaticNested` với cú pháp `outer.new StaticNested()`:** static nested class không cần enclosing instance; dùng `new Outer.StaticNested()` khi kiểu có thể truy cập.
3. **Dùng `Outer.this` trong static nested class:** static nested class không có tham chiếu enclosing instance ngầm định. Truyền đối tượng bao ngoài vào nếu cần dữ liệu instance.
4. **Cho rằng nested class không thể truy cập private:** các kiểu lồng trong cùng nest có quyền truy cập private theo quy tắc Java. Điều này không cấp quyền cho mã không liên quan.
5. **Nhầm quyền truy cập của kiểu với quyền truy cập constructor:** muốn tạo instance, cả tên kiểu lẫn constructor phải có thể truy cập từ vị trí sử dụng.
6. **Cho rằng local variable nào cũng được capture:** biến cục bộ hoặc tham số được local/anonymous class sử dụng phải là `final` hoặc effectively final.
7. **Khai báo `private` hoặc `protected` cho top-level class:** modifier này chỉ hợp lệ cho member nested type trong ngữ cảnh phù hợp, không hợp lệ cho top-level type.

## Kết luận

Quyền truy cập quyết định nơi có thể nhắc tới kiểu lồng; `static` hay non-static quyết định kiểu đó có cần instance của kiểu bao ngoài hay không. Dùng `Outer.Nested` cho tên kiểu lồng tĩnh, `outer.new Inner()` cho inner class, và `Outer.this` khi cần tham chiếu rõ ràng tới enclosing instance. Local và anonymous class có phạm vi hẹp hơn, phù hợp khi kiểu chỉ cần phục vụ một đoạn xử lý.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nested-types-004">Lớp thành viên bên trong interface và modifier ngầm định</a>

<details>
<summary>Xem chi tiết</summary>

# Lớp thành viên bên trong interface và modifier ngầm định

## Mục tiêu

Sau bài này, bạn có thể giải thích vì sao lớp được khai báo trực tiếp trong interface mặc định là `public static`, tạo đối tượng từ lớp đó, đọc đúng phạm vi của các modifier và tránh nhầm modifier của lớp lồng với modifier của interface.

## 1. Lớp thành viên trong interface là gì?

Java cho phép khai báo một lớp ngay trong thân interface. Khi lớp được khai báo trực tiếp ở cấp thành viên của interface, đó là **member class** (lớp thành viên). Ví dụ:

~~~java
interface Payment {
    class Receipt {
        public String render() {
            return "Receipt";
        }
    }
}
~~~

Khai báo `class Receipt` ở trên tương đương về modifier kiểu với:

~~~java
interface Payment {
    public static class Receipt {
        public String render() {
            return "Receipt";
        }
    }
}
~~~

Theo quy tắc ngôn ngữ Java, mọi **member type** được khai báo trong interface đều ngầm định là `public static`. Quy tắc này áp dụng cho lớp thành viên, interface lồng, enum và các kiểu thành viên hợp lệ khác. Vì vậy, từ khóa `public` và `static` có thể được viết tường minh để nhấn mạnh ý nghĩa, nhưng không bắt buộc.

- **`public` ngầm định:** kiểu lồng là một phần có thể truy cập công khai của API interface, tùy theo khả năng truy cập của chính interface chứa nó.
- **`static` ngầm định:** kiểu lồng không gắn với một đối tượng của interface bao ngoài. Không cần và cũng không thể tạo nó theo cú pháp của inner class cần đối tượng bao ngoài.
- **Không ngầm định `final` hoặc `abstract`:** lớp vẫn tuân theo quy tắc modifier thông thường của lớp Java; có thể khai báo `final` hoặc `abstract` khi phù hợp, nhưng không thể đồng thời dùng cả hai.
- **Không thể biến member type này thành `private` hoặc `protected`:** các modifier đó xung đột với tính `public` ngầm định của member type trong interface.

## 2. Tạo và sử dụng đối tượng

Hãy lưu chương trình sau trong tệp `InterfaceNestedClassDemo.java`. Interface `Payment` được để ở phạm vi package để cả ví dụ có thể nằm trong một tệp; lớp `Receipt` vẫn minh họa quy tắc `public static` ngầm định bên trong interface.

~~~java
interface Payment {
    String CURRENCY = "VND";

    class Receipt {
        private final String transactionId;

        public Receipt(String transactionId) {
            this.transactionId = transactionId;
        }

        public String render() {
            return "Receipt " + transactionId + " in " + CURRENCY;
        }
    }
}

public class InterfaceNestedClassDemo {
    public static void main(String[] args) {
        Payment.Receipt receipt = new Payment.Receipt("TX-101");
        System.out.println(receipt.render());
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~text
javac InterfaceNestedClassDemo.java
java InterfaceNestedClassDemo
~~~

Kết quả chuẩn:

~~~text
Receipt TX-101 in VND
~~~

Tên kiểu đầy đủ là `Payment.Receipt`. Có thể tạo đối tượng trực tiếp bằng `new Payment.Receipt("TX-101")`; không cần một đối tượng `Payment` làm đối tượng bao ngoài. `Payment` là interface nên bản thân nó không thể được khởi tạo bằng `new`.

Trong ví dụ, `CURRENCY` là trường khai báo trực tiếp trong interface nên ngầm định là `public static final`. Ngược lại, `transactionId` và `render()` nằm trong lớp `Receipt`; chúng tuân theo quy tắc truy cập và modifier thông thường của lớp. Modifier ngầm định của kiểu `Receipt` không tự động làm mọi thành viên bên trong lớp thành `public` hay `static`.

## 3. Phạm vi của modifier ngầm định

| Khai báo | Quy tắc |
|---|---|
| `class Receipt { }` trực tiếp trong interface | Kiểu thành viên ngầm định `public static` |
| `public static class Receipt { }` trực tiếp trong interface | Hợp lệ; `public static` được ghi tường minh nhưng dư thừa |
| `private class Receipt { }` hoặc `protected class Receipt { }` trực tiếp trong interface | Không hợp lệ vì modifier truy cập xung đột với `public` ngầm định |
| Trường khai báo trực tiếp trong interface | Ngầm định `public static final` |
| Phương thức khai báo trực tiếp trong interface | Theo quy tắc riêng của phương thức interface; không suy ra từ modifier của lớp lồng |
| Lớp cục bộ khai báo bên trong thân phương thức | Là lớp cục bộ, không phải member type trực tiếp của interface; không nhận quy tắc `public static` ở trên |

Một điểm dễ nhầm là **modifier của kiểu lồng và modifier của constructor là hai vấn đề khác nhau**. Nếu không khai báo constructor, Java sinh constructor mặc định có mức truy cập tương ứng với lớp. Nếu tự khai báo constructor mà bỏ modifier truy cập, constructor đó có phạm vi package-private, dù lớp `Receipt` là `public`. Khi cần cho mã ở package khác khởi tạo lớp, hãy khai báo constructor là `public`.

Lớp thành viên trong interface có thể có trường instance, constructor và phương thức instance như một lớp thông thường. Vì lớp này là `static`, nó không có tham chiếu ngầm định tới một instance bao ngoài. Nếu cần dùng hành vi instance của một đối tượng khác, hãy truyền đối tượng đó vào hoặc gọi thông qua biến tham chiếu thích hợp.

## 4. Những kiểu thành viên lồng khác

Quy tắc `public static` ngầm định cũng áp dụng cho các kiểu thành viên được khai báo trong interface, không chỉ riêng class:

~~~java
interface Registry {
    interface Reader {
        String read();
    }

    enum State {
        READY, CLOSED
    }

    record Item(String id) {
    }
}
~~~

Trong ví dụ này, `Reader`, `State` và `Item` là các kiểu thành viên của `Registry`; chúng được khai báo trong interface và có thể được tham chiếu bằng tên định danh như `Registry.Reader`, `Registry.State` và `Registry.Item`. Enum vốn có tính chất static theo quy tắc riêng của enum; record thành viên cũng là kiểu static. Record được hỗ trợ từ Java 16, nên ví dụ tổng hợp này cần Java 16 trở lên (Java 17 đáp ứng yêu cầu).

Không nên nhầm những kiểu thành viên công khai này với lớp trợ giúp nội bộ. Nếu một lớp chỉ phục vụ riêng cho một implementation và không nên xuất hiện trong API, hãy cân nhắc đặt nó ở phạm vi phù hợp bên ngoài interface hoặc bên trong lớp triển khai, thay vì khai báo trực tiếp trong interface.

## 5. Lỗi thường gặp và cách tránh

1. **Cố tạo lớp lồng như inner class:** viết `payment.new Receipt(...)` hoặc cho rằng phải có instance bao ngoài. Cách đúng là `new Payment.Receipt(...)`.
2. **Cho rằng lớp lồng tự động có mọi thành viên là public static:** chỉ kiểu thành viên khai báo trực tiếp trong interface nhận quy tắc `public static`; các trường, constructor và phương thức bên trong lớp lồng vẫn theo quy tắc riêng của lớp.
3. **Dùng `private` hoặc `protected` cho member type:** các modifier này không hợp lệ cho kiểu thành viên khai báo trực tiếp trong interface do kiểu đó đã ngầm định `public`.
4. **Cho rằng `static` đồng nghĩa với không thể có trạng thái:** modifier này chỉ nói rằng lớp lồng không cần instance bao ngoài. Mỗi đối tượng `Receipt` vẫn có thể giữ trạng thái riêng, như trường `transactionId`.
5. **Bỏ qua phạm vi của constructor:** khi cần cung cấp khả năng khởi tạo cho caller ở package khác, hãy đặt modifier truy cập rõ ràng cho constructor được khai báo tường minh.
6. **Đặt lớp triển khai riêng tư vào API interface:** member type của interface là public theo ngôn ngữ. Chỉ khai báo ở đây khi kiểu đó thực sự nên là một phần của API.

## 6. Quy trình kiểm tra nhanh

Khi gặp một lớp bên trong interface, hãy kiểm tra theo thứ tự:

1. Nó được khai báo trực tiếp trong thân interface hay nằm trong một phương thức/lớp khác?
2. Nếu là member type trực tiếp, hãy nhớ `public static` được ngầm định; không cần instance của interface bao ngoài.
3. Kiểm tra riêng modifier và phạm vi truy cập của constructor, trường và phương thức bên trong lớp.
4. Dùng tên có định danh interface, ví dụ `Payment.Receipt`, để làm rõ kiểu đang được tham chiếu.
5. Nếu thêm ví dụ runnable, biên dịch đúng tên tệp và kiểm tra stdout thực tế thay vì tự suy đoán kết quả.

## Kết luận

Lớp thành viên khai báo trực tiếp trong interface luôn có modifier ngầm định `public static`. Điều này quyết định khả năng truy cập kiểu và cách tạo đối tượng, nhưng không thay thế các quy tắc modifier thông thường bên trong chính lớp đó. Hiểu rõ ranh giới này giúp thiết kế API interface dễ dự đoán và tránh lỗi khi biên dịch hoặc sử dụng lớp lồng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nested-types-005">Triển khai bằng local class và anonymous class</a>

<details>
<summary>Xem chi tiết</summary>

# Triển khai bằng local class và anonymous class

Trong Java, **local class (lớp cục bộ)** và **anonymous class (lớp ẩn danh)** cho phép đặt một phần triển khai ngay gần nơi nó được dùng. Chúng thường hữu ích khi một phương thức cần một cách triển khai riêng cho interface hoặc abstract class, nhưng kiểu triển khai đó không cần trở thành một lớp cấp cao nhất hay một lớp thành viên của lớp khác.

- **Local class** có tên và được khai báo bên trong một khối lệnh, chẳng hạn trong phương thức, constructor hoặc initializer. Tên lớp chỉ có hiệu lực trong phạm vi khối đó.
- **Anonymous class** không có tên lớp do lập trình viên đặt. Nó được khai báo ngay trong biểu thức tạo đối tượng bằng cú pháp `new KiểuCha(...) { ... }`.

Cả hai có thể chứa trường, phương thức và logic riêng. Điểm khác biệt chính nằm ở cách khai báo, phạm vi tên lớp và việc có thể khai báo constructor riêng hay không.

## 1. Local class là gì?

Local class là một lớp được khai báo bên trong một khối lệnh. Ví dụ thường gặp nhất là khai báo lớp bên trong một phương thức:

~~~java
void process() {
    class LocalWorker {
        void work() {
            System.out.println("Đang xử lý");
        }
    }

    LocalWorker worker = new LocalWorker();
    worker.work();
}
~~~

Lớp `LocalWorker` chỉ có thể được tham chiếu bằng tên trong phạm vi khai báo của nó. Mã bên ngoài phương thức `process()` không thể viết `new LocalWorker()`, vì tên này không tồn tại ngoài khối đó.

Local class có thể:

- Triển khai một hoặc nhiều interface.
- Kế thừa một lớp cha, kể cả abstract class.
- Khai báo trường, phương thức và constructor của chính nó.
- Được tạo thành nhiều đối tượng nếu cần trong phạm vi khai báo.
- Truy cập thành viên của đối tượng bên ngoài khi lớp được khai báo trong ngữ cảnh instance và có đối tượng bên ngoài tương ứng.

Local class cũng có thể dùng tham số và biến cục bộ của phương thức bao quanh. Những biến này phải là `final` hoặc **effectively final**: sau khi được gán giá trị, chúng không bị gán lại. Java bảo đảm đối tượng của lớp cục bộ không phụ thuộc vào việc một biến cục bộ đã bị thay đổi sau đó.

~~~java
void greet() {
    String prefix = "Xin chào"; // effectively final

    class LocalGreeter {
        String greet(String name) {
            return prefix + ", " + name;
        }
    }

    System.out.println(new LocalGreeter().greet("Lan"));
}
~~~

Ví dụ trên hợp lệ vì `prefix` không bị gán lại. Nếu thêm `prefix = "Hello";` sau khi đã gán giá trị ban đầu, việc tham chiếu `prefix` bên trong `LocalGreeter` sẽ gây lỗi biên dịch.

## 2. Anonymous class là gì?

Anonymous class là một lớp không có tên được khai báo ngay tại biểu thức tạo đối tượng. Nó thường dùng để triển khai interface hoặc kế thừa abstract class tại một vị trí cụ thể.

Cú pháp khi triển khai interface:

~~~java
Greeter greeter = new Greeter() {
    @Override
    public String greet(String name) {
        return "Xin chào, " + name;
    }
};
~~~

Cú pháp khi kế thừa abstract class:

~~~java
TextFormatter formatter = new TextFormatter() {
    @Override
    String format(String text) {
        return "[" + text + "]";
    }
};
~~~

Ở cả hai trường hợp, phần thân trong cặp ngoặc nhọn là phần định nghĩa của lớp ẩn danh. Biểu thức `new ... { ... }` tạo ra một đối tượng thuộc lớp ẩn danh đó.

Một anonymous class có thể có trường, phương thức và khối khởi tạo instance. Tuy nhiên, nó **không thể khai báo constructor có tên riêng**, vì bản thân lớp không có tên để dùng làm tên constructor. Khi tạo lớp ẩn danh kế thừa một lớp, phần `new KiểuCha(...)` gọi constructor của lớp cha.

Nếu lớp ẩn danh triển khai interface có nhiều phương thức trừu tượng, nó phải triển khai đầy đủ các phương thức cần thiết để trở thành lớp cụ thể. Nếu chỉ cần triển khai một phương thức trừu tượng duy nhất của functional interface và không cần thêm trạng thái hay logic đặc biệt, lambda thường gọn hơn.

### Phạm vi biến và từ khóa `this`

Anonymous class cũng chỉ được dùng các biến cục bộ bên ngoài nếu chúng là `final` hoặc effectively final.

Trong thân anonymous class, `this` tham chiếu đến đối tượng của chính lớp ẩn danh. Nếu lớp được khai báo bên trong một lớp bao ngoài và cần tham chiếu đến đối tượng của lớp bao ngoài, có thể dùng cú pháp `OuterClass.this` trong ngữ cảnh instance thích hợp.

Với lambda, `this` có ý nghĩa khác: nó tham chiếu đến đối tượng bao quanh lambda, vì lambda không tạo ra một lớp ẩn danh với đối tượng `this` riêng. Đây là một điểm cần nhớ khi chuyển từ anonymous class sang lambda.

## 3. Ví dụ Java hoàn chỉnh

Ví dụ sau minh họa ba trường hợp: local class triển khai interface, anonymous class triển khai cùng interface và anonymous class kế thừa abstract class. Tất cả được đặt trong một tệp để có thể biên dịch và chạy trực tiếp.

~~~java
import java.util.Locale;

interface Greeter {
    String greet(String name);
}

abstract class TextFormatter {
    abstract String format(String text);
}

public class LocalAndAnonymousClassesDemo {
    public static void main(String[] args) {
        String prefix = "Xin chào";

        // Local class: có tên, phạm vi giới hạn trong main().
        class LocalGreeter implements Greeter {
            @Override
            public String greet(String name) {
                // prefix được capture vì effectively final.
                return prefix + ", " + name + "!";
            }
        }

        Greeter localGreeter = new LocalGreeter();

        // Anonymous class: triển khai Greeter ngay tại nơi tạo đối tượng.
        Greeter anonymousGreeter = new Greeter() {
            @Override
            public String greet(String name) {
                return "Hello, " + name + "!";
            }
        };

        // Anonymous class: kế thừa abstract class và ghi đè phương thức.
        TextFormatter formatter = new TextFormatter() {
            @Override
            String format(String text) {
                return "[" + text.toUpperCase(Locale.ROOT) + "]";
            }
        };

        System.out.println(localGreeter.greet("Lan"));
        System.out.println(anonymousGreeter.greet("Minh"));
        System.out.println(formatter.format("java"));
    }
}
~~~

Lưu nội dung vào tệp `LocalAndAnonymousClassesDemo.java`, sau đó chạy bằng Java 17 trở lên:

~~~text
javac LocalAndAnonymousClassesDemo.java
java LocalAndAnonymousClassesDemo
~~~

Kết quả chuẩn của chương trình:

~~~text
Xin chào, Lan!
Hello, Minh!
[JAVA]
~~~

Tên tệp phải khớp chính xác với tên lớp `public LocalAndAnonymousClassesDemo`. Hai interface/lớp hỗ trợ trong ví dụ không được khai báo `public`, nên có thể cùng nằm trong tệp đó. `Locale.ROOT` giúp chuyển chữ hoa theo quy tắc không phụ thuộc ngôn ngữ mặc định của máy chạy.

## 4. So sánh local class và anonymous class

| Tiêu chí | Local class | Anonymous class |
|---|---|---|
| Tên lớp | Có tên do lập trình viên đặt | Không có tên lớp được đặt trong mã nguồn |
| Cách khai báo | Khai báo lớp trong một khối lệnh | Khai báo phần thân lớp trong biểu thức `new ... { ... }` |
| Phạm vi | Tên lớp dùng được trong khối khai báo | Không có tên lớp để tham chiếu trực tiếp ở nơi khác |
| Tạo đối tượng | Có thể tạo nhiều đối tượng từ lớp đã khai báo | Mỗi lần biểu thức được thực thi sẽ tạo một đối tượng mới |
| Constructor riêng | Có thể khai báo constructor | Không thể khai báo constructor riêng có tên |
| Trạng thái và logic | Phù hợp khi cần nhiều trường, phương thức hoặc logic dài hơn | Phù hợp với phần triển khai ngắn, gắn với một điểm sử dụng |
| Kế thừa/triển khai | Có thể kế thừa một lớp và triển khai interface | Có thể kế thừa một lớp hoặc triển khai một interface tại biểu thức tạo đối tượng |

Cả hai đều có thể truy cập biến cục bộ effectively final và đều có thể được dùng để cung cấp triển khai cho một kiểu trừu tượng. Khác biệt nằm ở cách định danh lớp và cách tổ chức mã nguồn.

## 5. Khi nào nên dùng mỗi loại?

Chọn **local class** khi phần triển khai có nhiều bước, cần tên gọi rõ ràng, có constructor hoặc cần tạo nhiều đối tượng cùng kiểu trong một phương thức. Việc đặt lớp gần nơi sử dụng vẫn giữ phạm vi của nó nhỏ, đồng thời làm phần triển khai dễ đọc hơn một khối anonymous class dài.

Chọn **anonymous class** khi cần một triển khai cục bộ ngắn và chỉ cần dùng lớp tại biểu thức đó. Ví dụ điển hình là ghi đè một callback hoặc cung cấp một triển khai riêng của interface cho một lời gọi cụ thể.

Nếu kiểu cần triển khai là functional interface và chỉ cần cung cấp phần thân cho một phương thức trừu tượng, hãy cân nhắc lambda:

~~~java
Greeter greeter = name -> "Xin chào, " + name;
~~~

Lambda không thể thay thế anonymous class trong mọi trường hợp. Lambda chỉ dùng được với functional interface, trong khi anonymous class còn có thể kế thừa abstract class. Anonymous class cũng có thể có trường instance và ghi đè nhiều phương thức, nên vẫn phù hợp khi cần những khả năng đó.

## 6. Lỗi thường gặp và cách xử lý

1. **Dùng tên local class ngoài phạm vi khai báo.** Hãy chuyển phần sử dụng vào đúng khối hoặc chọn một lớp có phạm vi rộng hơn nếu nhiều nơi thực sự cần dùng.
2. **Gán lại biến cục bộ đã được capture.** Giữ biến effectively final, hoặc thiết kế lại cách truyền trạng thái thay vì thay đổi trực tiếp biến đó.
3. **Quên triển khai phương thức trừu tượng.** Kiểm tra đầy đủ các phương thức bắt buộc của interface hoặc abstract class. Với phương thức interface, phần triển khai thông thường phải khai báo `public`.
4. **Cố khai báo constructor cho anonymous class.** Anonymous class không có tên lớp để đặt tên constructor. Nếu cần constructor riêng hoặc nhiều cấu hình khởi tạo, local class hay một lớp được đặt tên thường phù hợp hơn.
5. **Gọi phương thức bổ sung qua kiểu tham chiếu quá tổng quát.** Nếu biến được khai báo là kiểu interface, chỉ những phương thức có trong interface mới có thể được gọi trực tiếp qua biến đó, dù anonymous class có thêm phương thức riêng.
6. **Nhầm ý nghĩa của `this`.** Trong anonymous class, `this` trỏ đến đối tượng ẩn danh; dùng `OuterClass.this` khi cần đối tượng bao ngoài và ngữ cảnh cho phép.

Khi đọc hoặc thiết kế mã, hãy xác định trước kiểu trừu tượng cần triển khai, phạm vi sử dụng, lượng trạng thái cần lưu và số phương thức cần ghi đè. Những yếu tố này giúp quyết định giữa local class, anonymous class và lambda một cách rõ ràng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nested-types-006">Khởi tạo anonymous subclass của abstract class</a>

<details>
<summary>Xem chi tiết</summary>

# Khởi tạo anonymous subclass của abstract class

Trong Java, một **anonymous class** (lớp ẩn danh) là lớp được khai báo và khởi tạo ngay tại một biểu thức, không có tên lớp do lập trình viên đặt. Khi cần tạo một đối tượng dựa trên **abstract class**, ta có thể viết một lớp con ẩn danh bằng cú pháp `new AbstractClass(...){ ... }`, cung cấp phần triển khai cho các phương thức trừu tượng ngay tại chỗ.

Cách này hữu ích khi chỉ cần một triển khai nhỏ, dùng tại một vị trí và không có nhu cầu tái sử dụng như một lớp riêng. Nó cũng giúp ta sử dụng được cả trạng thái, constructor và phương thức đã triển khai sẵn của abstract class.

## 1. Cú pháp cơ bản

Cấu trúc tổng quát:

~~~java
AbstractClass reference = new AbstractClass(arguments) {
    @Override
    public ReturnType abstractMethod(Parameters parameters) {
        // Cung cấp phần triển khai cụ thể
    }

    // Có thể ghi đè các phương thức khác nếu cần
};
~~~

Các phần cần hiểu:

- `AbstractClass` là abstract class mà đối tượng ẩn danh sẽ kế thừa.
- `arguments` được truyền vào constructor của abstract class. Nếu lớp cha không có constructor không tham số truy cập được, phải gọi một constructor phù hợp.
- Phần thân trong cặp ngoặc nhọn `{ ... }` khai báo lớp con ẩn danh và triển khai các phương thức trừu tượng bắt buộc.
- Biến `reference` thường được khai báo theo kiểu abstract class để sử dụng đối tượng thông qua hợp đồng chung.
- Dấu chấm phẩy sau dấu `}` là cần thiết khi kết thúc câu lệnh khai báo và khởi tạo biến.

Không thể tạo trực tiếp một instance của abstract class bằng `new AbstractClass(...)` nếu không có phần thân lớp con. Phần thân trong biểu thức `new AbstractClass(...){...}` định nghĩa một lớp con cụ thể, và đối tượng được tạo là instance của lớp con đó.

## 2. Ví dụ hoàn chỉnh có thể chạy

Tạo tệp **AnonymousAbstractSubclassDemo.java** với nội dung:

~~~java
abstract class Report {
    private final String owner;

    protected Report(String owner) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("owner must not be blank");
        }
        this.owner = owner;
    }

    public String label() {
        return "Report for " + owner;
    }

    public abstract String format(String message);

    public abstract void deliver(String message);
}

public class AnonymousAbstractSubclassDemo {
    public static void main(String[] args) {
        Report report = new Report("Mai") {
            @Override
            public String format(String message) {
                return "[EMAIL] " + message.trim();
            }

            @Override
            public void deliver(String message) {
                System.out.println(label() + ": " + format(message));
            }
        };

        report.deliver("  Build passed  ");
        System.out.println(report.getClass().isAnonymousClass());
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~bash
javac AnonymousAbstractSubclassDemo.java
java AnonymousAbstractSubclassDemo
~~~

Kết quả stdout:

~~~text
Report for Mai: [EMAIL] Build passed
true
~~~

Điều gì xảy ra trong ví dụ này?

1. `new Report("Mai")` gọi constructor của abstract class `Report`; chuỗi `"Mai"` được truyền vào tham số `owner`.
2. Phần thân sau dấu ngoặc tròn khai báo một lớp con ẩn danh của `Report`.
3. Lớp con triển khai cả hai phương thức trừu tượng `format` và `deliver`, vì vậy nó là lớp cụ thể có thể khởi tạo.
4. Phương thức `label()` là phương thức cụ thể được kế thừa. Bên trong `deliver`, lời gọi `label()` sử dụng phần triển khai kế thừa đó.
5. Biến `report` có kiểu khai báo `Report`, nên mã gọi sử dụng các phương thức được định nghĩa trong abstract class. Kiểu thực tế của đối tượng là lớp con ẩn danh.
6. `getClass().isAnonymousClass()` trả về `true`, xác nhận đối tượng thuộc một lớp ẩn danh.

Lớp `Report` được khai báo không có từ khóa `public` để có thể đặt cùng tệp với lớp public `AnonymousAbstractSubclassDemo`. Trong mã ứng dụng, hãy tuân theo quy tắc tên tệp và lớp public của Java.

## 3. Vì sao cần abstract class?

Abstract class cho phép gom trạng thái và hành vi chung vào một lớp cơ sở, đồng thời yêu cầu lớp con cung cấp một số hành vi cụ thể. Nó có thể chứa:

- Trường dữ liệu, bao gồm trường `private` và `final`.
- Constructor để kiểm tra hoặc khởi tạo trạng thái chung.
- Phương thức cụ thể có sẵn phần thân.
- Phương thức trừu tượng chỉ khai báo chữ ký.
- Phương thức `final`, `static`, hoặc các thành phần khác theo quy tắc của Java.

Lớp con ẩn danh kế thừa các thành phần có thể truy cập từ abstract class. Nó phải triển khai tất cả phương thức trừu tượng còn chưa được triển khai từ lớp cha hoặc các lớp cha của nó. Nếu bỏ sót một phương thức trừu tượng bắt buộc, trình biên dịch báo lỗi; lớp ẩn danh được tạo trong biểu thức này không thể được khai báo là abstract để trì hoãn việc triển khai.

Nếu abstract class có phương thức cụ thể mà ta muốn thay đổi riêng cho đối tượng này, có thể ghi đè thêm phương thức đó. Không bắt buộc phải ghi đè mọi phương thức cụ thể.

## 4. Truyền đối số vào constructor của lớp cha

Phần nằm trong ngoặc tròn ngay sau tên abstract class là lời gọi constructor của lớp cha. Điều này cho phép lớp cha khởi tạo trạng thái dùng chung trước khi các phương thức của đối tượng được sử dụng.

~~~java
abstract class Greeting {
    private final String name;

    Greeting(String name) {
        this.name = name;
    }

    public String greetingPrefix() {
        return "Hello, " + name;
    }

    public abstract String message();
}
~~~

Có thể tạo một triển khai ẩn danh như sau:

~~~java
Greeting greeting = new Greeting("Linh") {
    @Override
    public String message() {
        return greetingPrefix() + "!";
    }
};

System.out.println(greeting.message());
~~~

Đây là đoạn minh họa để đặt bên trong một phương thức; nó phụ thuộc vào lớp `Greeting` ở trên. Kết quả:

~~~text
Hello, Linh!
~~~

Nếu abstract class không khai báo constructor, Java cung cấp constructor mặc định khi các điều kiện của ngôn ngữ cho phép. Nếu lớp cha khai báo constructor có tham số và không có constructor không tham số truy cập được, biểu thức tạo lớp con ẩn danh phải truyền đối số phù hợp. Quyền truy cập của constructor cũng được áp dụng như với một lớp con thông thường.

Lớp ẩn danh không khai báo constructor riêng có tên. Nếu cần truyền dữ liệu ban đầu, thường truyền dữ liệu đó vào constructor của lớp cha; các trường bổ sung của lớp ẩn danh có thể được khởi tạo bằng biểu thức khởi tạo trường hoặc khối khởi tạo instance khi cần.

## 5. Kiểu tham chiếu và đa hình

Một đối tượng tạo từ anonymous subclass có thể được lưu trong biến kiểu abstract class:

~~~java
Report report = new Report("Mai") {
    @Override
    public String format(String message) {
        return message.toUpperCase();
    }

    @Override
    public void deliver(String message) {
        System.out.println(format(message));
    }
};
~~~

Mã sử dụng `report` chỉ cần biết hợp đồng được khai báo trong `Report`. Khi gọi `report.deliver(...)`, Java thực thi phần triển khai của lớp con ẩn danh. Đây là **đa hình**: lời gọi qua tham chiếu kiểu cha có thể dẫn đến phần triển khai được ghi đè trong lớp con.

Kiểu khai báo và kiểu thực tế có vai trò khác nhau:

- **Kiểu khai báo** quyết định những thành viên nào có thể được gọi trực tiếp khi biên dịch.
- **Kiểu thực tế** quyết định phương thức instance nào được chọn khi thực thi theo cơ chế ghi đè động.
- Nếu lớp ẩn danh khai báo thêm một phương thức riêng không có trong abstract class, không thể gọi phương thức đó qua biến chỉ có kiểu abstract class nếu không có cách truy cập kiểu an toàn khác. Thông thường, nên thiết kế hợp đồng cần thiết ngay trong lớp cơ sở hoặc một interface phù hợp.

Cách sử dụng theo kiểu cha giúp mã gọi ít phụ thuộc vào chi tiết của lớp con ẩn danh và giữ được tính thay thế của đa hình.

## 6. Ghi đè phương thức đúng cách

Nên đặt chú thích `@Override` trước mỗi phương thức được ghi đè. Trình biên dịch sẽ phát hiện trường hợp chữ ký không khớp với phương thức cần ghi đè.

Ví dụ, nếu lớp cha khai báo:

~~~java
public abstract int calculate(int value);
~~~

thì lớp con cần cung cấp một triển khai tương thích:

~~~java
@Override
public int calculate(int value) {
    return value * 2;
}
~~~

Tên phương thức, danh sách tham số và kiểu trả về phải tuân thủ quy tắc ghi đè của Java. Không thể giảm mức truy cập của phương thức khi ghi đè; ví dụ, không thể ghi đè một phương thức `public` bằng phương thức `protected`. Nếu phương thức cha khai báo ngoại lệ checked, phương thức ghi đè cũng phải tuân theo các giới hạn về ngoại lệ của quy tắc ghi đè.

Nếu phương thức abstract có kiểu trả về khác `void`, mọi nhánh thực thi cần trả về giá trị tương thích. Nếu kiểu trả về là `void`, triển khai phải thực hiện hành động phù hợp và không trả về giá trị.

## 7. Cảnh báo vòng đời: constructor lớp cha gọi phương thức có thể ghi đè

Khi chạy `new AbstractClass(...) { ... }`, **constructor lớp cha chạy trước phần khởi tạo trường của lớp con ẩn danh**. Nếu constructor lớp cha gọi một phương thức có thể ghi đè, Java sẽ phân phối lời gọi đến phần triển khai lớp con, dù trạng thái riêng của lớp con chưa được khởi tạo đầy đủ.

Ví dụ sau cố ý mô tả một thiết kế nguy hiểm; đặt khai báo `Base` bên ngoài và câu lệnh tạo đối tượng trong một phương thức:

```java
abstract class Base {
    Base() { display(); }
    abstract void display();
}

Base instance = new Base() {
    private final String status = " ready ".trim();
    @Override void display() {
        System.out.println(status);
    }
};
```

Đầu ra là `null`, **không phải** `ready`: lời gọi `display()` xảy ra trong constructor `Base` trước khi trường `status` được gán. Phép gọi `trim()` khiến biểu thức khởi tạo chạy khi tạo đối tượng; nếu dùng `final String status = "ready"`, đây là hằng tại thời điểm biên dịch và trình biên dịch có thể *inline* giá trị, che khuất lỗi thứ tự khởi tạo. Đây là rủi ro của mọi lớp con, đặc biệt dễ bị bỏ sót khi lớp con được viết ngay trong biểu thức `new`. Tốt hơn, constructor lớp cha chỉ thiết lập trạng thái của mình; gọi các phương thức có thể ghi đè sau khi xây dựng đối tượng xong.

## 8. Ranh giới với lambda và biến bên ngoài

Anonymous subclass có `this` của chính đối tượng lớp con, có thể ghi đè nhiều phương thức và kế thừa trạng thái từ `Report`. Lambda không thể kế thừa abstract class, kể cả khi lớp cha chỉ có một phương thức abstract: kiểu đích của lambda phải là **functional interface**.

Khi thân lớp ẩn danh dùng biến cục bộ của phương thức chứa nó, biến phải `final` hoặc *effectively final*. Quy tắc này áp dụng cho **biến cục bộ được bắt giữ**, không ngăn lớp con có các trường instance riêng. Nếu anonymous class nằm trong instance method và cần đối tượng bao ngoài, dùng `Outer.this` khi có enclosing instance thích hợp.

## 9. Khi nào nên dùng anonymous subclass?

Cách này phù hợp khi:

- Cần một triển khai duy nhất, nhỏ và cục bộ cho một abstract class.
- Muốn tận dụng constructor, trường dữ liệu hoặc phương thức chung đã được định nghĩa trong lớp cơ sở.
- Cần ghi đè một hoặc nhiều phương thức cho một tình huống cụ thể.
- Việc tạo một lớp có tên riêng sẽ làm tăng số lượng lớp mà không đem lại lợi ích tái sử dụng rõ ràng.

Hãy cân nhắc tạo một lớp con có tên nếu phần triển khai dài, có nhiều trạng thái hoặc quy tắc nghiệp vụ, cần được tái sử dụng ở nhiều nơi, hoặc cần được kiểm thử và đọc hiểu độc lập. Một lớp có tên thường giúp đặt tên cho ý định của hành vi và giảm độ phức tạp ở nơi khởi tạo.

Anonymous subclass cũng không phải lựa chọn tốt nếu phần thân trở thành một khối mã dài với nhiều trách nhiệm. Trong trường hợp đó, tách logic thành các phương thức hoặc một lớp riêng sẽ làm cấu trúc rõ ràng hơn.

## 10. Lỗi thường gặp

- **Không triển khai đủ phương thức abstract:** kiểm tra toàn bộ phương thức trừu tượng chưa được triển khai trong cây kế thừa. Nếu còn thiếu, trình biên dịch báo lỗi.
- **Gọi constructor không tồn tại hoặc không truy cập được:** truyền đối số phù hợp và kiểm tra mức truy cập của constructor lớp cha.
- **Quên dấu chấm phẩy sau biểu thức tạo đối tượng:** câu lệnh gán biến cần kết thúc bằng dấu `;` sau dấu ngoặc nhọn.
- **Sai chữ ký khi ghi đè:** thêm `@Override` và kiểm tra tên, tham số, kiểu trả về cùng mức truy cập.
- **Gán lại biến cục bộ được sử dụng bên trong lớp ẩn danh:** giữ biến `final`/effectively final hoặc thiết kế lại cách truyền dữ liệu.
- **Hiểu nhầm kiểu đối tượng:** biến kiểu abstract class chỉ cho phép gọi các thành viên có thể truy cập qua kiểu khai báo; phương thức được ghi đè sẽ được phân giải khi chạy.
- **Dùng lambda để kế thừa abstract class:** lambda chỉ nhắm tới functional interface. Dùng anonymous subclass nếu cần kế thừa một lớp.
- **Để lớp ẩn danh quá phức tạp:** chuyển triển khai thành lớp có tên khi cần tái sử dụng, kiểm thử hoặc thể hiện nghiệp vụ rõ ràng.

## 11. Quy trình thực hiện

1. Xác định abstract class cần kế thừa và các phương thức trừu tượng chưa được triển khai.
2. Kiểm tra constructor lớp cha, tham số cần truyền và quyền truy cập của constructor đó.
3. Viết `new AbstractClass(arguments) { ... }` và triển khai mọi phương thức trừu tượng bắt buộc.
4. Dùng `@Override` để xác minh các phương thức đang ghi đè đúng chữ ký.
5. Gán đối tượng vào tham chiếu kiểu abstract class hoặc truyền trực tiếp cho API phù hợp.
6. Biên dịch bằng `javac`, chạy chương trình và kiểm tra đầu ra.
7. Nếu phần thân lớn hoặc cần tái sử dụng, cân nhắc chuyển thành lớp con có tên.

Anonymous subclass là cách Java tạo một lớp con cụ thể ngay tại nơi cần đối tượng. Nó cho phép triển khai phương thức trừu tượng, sử dụng trạng thái và hành vi chung của abstract class, đồng thời tận dụng đa hình mà không cần khai báo một lớp con có tên riêng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nested-types-007">So sánh anonymous class với lambda về this và phạm vi tên</a>

<details>
<summary>Xem chi tiết</summary>

# So sánh anonymous class với lambda về this và phạm vi tên

Anonymous class (lớp ẩn danh) và lambda đều có thể cung cấp hành vi cho một functional interface, chẳng hạn Runnable. Khác biệt quan trọng nằm ở ngữ cảnh mà biểu thức tạo ra: anonymous class tạo một đối tượng thuộc lớp mới, còn lambda giữ ngữ cảnh từ vị trí khai báo. Điều này quyết định ý nghĩa của this, cách tra cứu tên và những thành viên có thể khai báo.

## 1. Bảng so sánh nhanh

| Khía cạnh | Anonymous class | Lambda |
|---|---|---|
| Ý nghĩa của this | this trỏ đến đối tượng anonymous class | this giữ nghĩa từ ngữ cảnh bao ngoài |
| Truy cập đối tượng bao ngoài | Dùng Outer.this khi cần chỉ rõ instance bên ngoài | this đã trỏ đến instance bao ngoài; có thể dùng Outer.this để viết rõ ý định |
| Thành viên riêng | Có thể khai báo field, method và initializer trong thân lớp | Không thể khai báo field hay method riêng trong thân lambda |
| Phạm vi tên | Có phạm vi thành viên của lớp; thành viên riêng có thể che khuất tên từ bên ngoài | Dùng phạm vi từ vựng của vị trí khai báo và phạm vi tham số/local của lambda |
| Biến local từ method bao ngoài | Chỉ được capture biến final hoặc effectively final | Cũng chỉ được capture biến final hoặc effectively final |
| Kiểu đích | Có thể tạo lớp con của class hoặc triển khai interface | Cần kiểu đích là functional interface, tức có một phương thức abstract cần triển khai |
| Khi phù hợp | Cần trạng thái riêng, nhiều method hoặc cần this của đối tượng callback | Cần truyền một hành vi ngắn gọn, tập trung vào một phương thức |

## 2. this trong anonymous class

Anonymous class có một đối tượng riêng. Vì vậy, bên trong method của nó, this là đối tượng anonymous class đang thực thi. Nếu lớp đó được khai báo trong một instance method của lớp ThisAndScopeDemo, biểu thức ThisAndScopeDemo.this trỏ đến instance bao ngoài.

Khi tên field trùng nhau, hãy dùng tiền tố thể hiện rõ đối tượng cần truy cập:

- this.name: field name của anonymous class nếu lớp đó khai báo field này.
- ThisAndScopeDemo.this.name: field name của instance ThisAndScopeDemo bao ngoài.
- Một lời gọi method không có tiền tố bên trong anonymous class được tra cứu trong ngữ cảnh lớp đó trước; dùng ThisAndScopeDemo.this.someMethod() khi cần gọi rõ method của instance bên ngoài.

Anonymous class cũng có thể khai báo method, field và initializer. Nó không khai báo constructor có tên riêng, vì bản thân lớp không có tên; có thể dùng initializer nếu cần khởi tạo trạng thái riêng.

## 3. this trong lambda

Lambda không tạo binding this mới. Nếu lambda được viết trong instance method của ThisAndScopeDemo, this bên trong lambda vẫn là instance ThisAndScopeDemo. Do đó, this.name đọc field của lớp bao ngoài.

Lambda cũng không tạo ngữ cảnh lớp mới cho super. Các tham chiếu this và super được hiểu theo ngữ cảnh từ vựng nơi lambda được khai báo. Nếu lambda nằm trong static method, không có instance bao ngoài để this trỏ đến, nên viết this tại đó vẫn là lỗi biên dịch.

Đừng nhầm this với chính đối tượng lambda. Lambda vẫn là một object có thể được gán cho biến, truyền làm đối số hoặc so sánh tham chiếu. Tuy nhiên, từ bên trong thân lambda, this có nghĩa là instance bao ngoài. Thông tin lớp được sinh ra cho object lambda là chi tiết triển khai; không nên dựa vào tên lớp sinh tự động hoặc hành vi identity của lambda để thiết kế logic nghiệp vụ.

## 4. Ví dụ Java chạy được

Lưu nội dung sau trong file ThisAndScopeDemo.java. Ví dụ minh họa cả this, field bị che khuất và biến local được capture.

~~~java
public class ThisAndScopeDemo {
    private final String name = "outer field";

    private void demonstrate() {
        String text = "captured local";

        Runnable anonymous = new Runnable() {
            private final String name = "anonymous field";

            @Override
            public void run() {
                System.out.println("anonymous this == outer: "
                        + (this == (Object) ThisAndScopeDemo.this));
                System.out.println("anonymous this.name = " + this.name);
                System.out.println("anonymous outer name = "
                        + ThisAndScopeDemo.this.name);
                System.out.println("anonymous captured text = " + text);
            }
        };

        Runnable lambda = () -> {
            System.out.println("lambda this == outer: "
                    + (this == ThisAndScopeDemo.this));
            System.out.println("lambda this.name = " + this.name);
            System.out.println("lambda captured text = " + text);
        };

        anonymous.run();
        lambda.run();
    }

    public static void main(String[] args) {
        new ThisAndScopeDemo().demonstrate();
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~text
javac ThisAndScopeDemo.java
java ThisAndScopeDemo
~~~

Kết quả stdout:

~~~text
anonymous this == outer: false
anonymous this.name = anonymous field
anonymous outer name = outer field
anonymous captured text = captured local
lambda this == outer: true
lambda this.name = outer field
lambda captured text = captured local
~~~

Ở dòng đầu, phép ép kiểu sang Object giúp so sánh hai tham chiếu có kiểu tĩnh khác nhau. Kết quả false cho thấy this trong anonymous class là đối tượng callback; dòng tương ứng của lambda là true vì this trong lambda chính là instance bao ngoài. Anonymous class khai báo field name riêng nên this.name đọc field đó, còn ThisAndScopeDemo.this.name đọc field của instance bao ngoài.

## 5. Phạm vi tên và che khuất tên

### Field cùng tên

Trong ví dụ trên, cả lớp bao ngoài và anonymous class đều có field name. Tên không định danh this.name trong method của anonymous class trỏ đến field của đối tượng anonymous class. Muốn truy cập field của lớp bao ngoài, phải chỉ rõ ThisAndScopeDemo.this.name.

Trong lambda, không có field riêng thuộc thân lambda. Biểu thức this.name vì vậy trỏ đến field name của instance bao ngoài. Quy tắc này giúp lambda thường giữ cách tra cứu tên gần với code được viết trực tiếp trong method bao ngoài.

### Tham số và biến local

Tham số lambda có phạm vi trong lambda. Tên tham số hoặc biến local của lambda không được trùng tên với biến local hay tham số đang có trong phạm vi bao ngoài. Ví dụ sau cố ý không biên dịch:

~~~java
String value = "outer";
java.util.function.Consumer<String> consumer =
        value -> System.out.println(value); // Lỗi: tên value đã được khai báo ở scope bao ngoài
~~~

Hãy đổi tên tham số, chẳng hạn item, để tránh xung đột. Quy tắc này áp dụng cả khi tham số lambda nằm trong biểu thức nhiều dòng.

Anonymous class có phạm vi lớp riêng, nên có thể khai báo thành viên với tên trùng tên thành viên của lớp bao ngoài. Khi tên có khả năng gây nhầm lẫn, hãy dùng this.field cho thành viên của anonymous class và Outer.this.field cho thành viên của instance bao ngoài. Method bên trong anonymous class cũng có phạm vi tham số và biến local riêng của method đó.

### Capture biến local

Cả anonymous class lẫn lambda đều chỉ capture biến local hoặc tham số được khai báo final hay effectively final. Effectively final nghĩa là sau khi gán giá trị ban đầu, biến không được gán lại. Ví dụ, text trong chương trình mẫu được đọc từ hai callback nhưng không được gán lại nên hợp lệ.

Capture không biến biến local thành một biến có thể gán lại từ callback. Nếu cần thay đổi trạng thái, hãy chọn mô hình dữ liệu phù hợp, chẳng hạn một object có trạng thái mutable được thiết kế rõ ràng, hoặc chuyển trạng thái thành field của một object có vòng đời phù hợp. Không nên dùng wrapper mutable chỉ để lách quy tắc khi một thiết kế đơn giản hơn có thể giải quyết vấn đề.

## 6. Cách chọn

Chọn lambda khi cần triển khai một hành vi đơn lẻ cho functional interface, chẳng hạn predicate, comparator, callback hoặc thao tác trên stream. Cú pháp ngắn hơn và this tiếp tục trỏ đến instance bao ngoài, nên ít gây bất ngờ khi callback cần gọi method hoặc đọc field của lớp đang viết.

Chọn anonymous class khi cần field riêng cho từng callback, cần nhiều method trong thân lớp, cần tạo lớp con của một abstract class hoặc chủ ý cần this trỏ đến đối tượng callback. Với interface chỉ có một abstract method, cả hai cách đều có thể phù hợp; hãy chọn cách thể hiện rõ ý định và làm cho phạm vi tên dễ hiểu.

Đừng chuyển anonymous class sang lambda chỉ để giảm số dòng nếu thay đổi đó làm đổi ý nghĩa của this, super hoặc cách tra cứu tên. Trước khi refactor, hãy kiểm tra mọi lần dùng this, super, field trùng tên, method không định danh và biến local được capture.

## 7. Lỗi thường gặp

- Dùng this trong lambda như thể nó trỏ đến object callback. Trong lambda, this giữ ngữ cảnh bao ngoài.
- Dùng this.name trong anonymous class rồi nghĩ rằng nó luôn đọc field của lớp bao ngoài. Nếu anonymous class có field name, biểu thức đọc field của anonymous class; dùng Outer.this.name để chỉ rõ.
- Gán lại biến local sau khi biến đã được capture. Cả hai kiểu callback đều yêu cầu biến local được capture là final hoặc effectively final.
- Khai báo tham số lambda trùng tên biến local trong method bao ngoài. Hãy chọn tên khác để tránh lỗi biên dịch.
- Dựa vào tên lớp được sinh tự động hoặc identity của lambda. Đó không phải hợp đồng ổn định cho logic nghiệp vụ.
- Cho rằng lambda có thể khai báo field hoặc method như một lớp. Nếu callback cần trạng thái hoặc nhiều method, hãy cân nhắc anonymous class hoặc một lớp có tên.

## 8. Tự kiểm tra

1. Trong method của anonymous class, this.name trỏ đến đâu nếu anonymous class khai báo field name? Trả lời: field name của anonymous class.
2. Trong lambda viết bên trong instance method, this.name trỏ đến đâu? Trả lời: field name của instance bao ngoài.
3. Hai callback có thể capture một biến local không được khai báo final tường minh không? Có, nếu biến đó effectively final.
4. Khi cần gọi rõ method của instance bao ngoài từ anonymous class, nên viết thế nào? Dùng Outer.this.methodName() với tên lớp bao ngoài phù hợp.



</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nested-types-008">Biến được bắt giữ và quy tắc effectively final</a>

<details>
<summary>Xem chi tiết</summary>

# Biến được bắt giữ và quy tắc effectively final

Trong Java, lambda expression và lớp cục bộ (local class) hoặc lớp ẩn danh (anonymous class) có thể sử dụng biến được khai báo ở phạm vi bên ngoài. Khi một lambda hay lớp như vậy tham chiếu đến biến cục bộ hoặc tham số bên ngoài, biến đó được gọi là **biến được bắt giữ** (captured variable). Biến cục bộ được bắt giữ phải được khai báo bằng `final` hoặc phải là **effectively final** (final một cách hiệu dụng).

Quy tắc này thường xuất hiện khi viết callback, xử lý collection bằng Stream, tạo tác vụ chạy sau, hoặc định nghĩa lớp bên trong một phương thức.

## 1. Effectively final nghĩa là gì?

Một biến là effectively final khi không được khai báo bằng từ khóa `final` nhưng vẫn đáp ứng quy tắc gán của biến final: sau khi biến được khởi tạo hoặc được gán lần đầu theo luồng điều khiển, chương trình không gán lại giá trị cho biến đó. Các phép gán như `=`, `+=`, `-=` và phép tăng/giảm như `++`, `--` đều có thể khiến biến không còn effectively final.

~~~java
int limit = 10;                 // effectively final
Runnable task = () -> System.out.println(limit);
task.run();
~~~

Đoạn mã hợp lệ vì `limit` không bị gán lại sau khi khởi tạo. Không bắt buộc phải thêm `final`; trình biên dịch xác định được tính effectively final từ cách biến được sử dụng.

~~~java
final int limit = 10;           // final tường minh
Runnable task = () -> System.out.println(limit);
task.run();
~~~

Đoạn mã trên cũng hợp lệ. `final` thể hiện rõ ý định và ngăn việc gán lại ngay tại nơi khai báo.

Một biến chưa có giá trị khởi tạo ngay tại khai báo vẫn có thể effectively final nếu nó được gán đúng một lần trên mọi nhánh có thể đi đến điểm sử dụng:

~~~java
String mode;
if (args.length > 0) {
    mode = "provided";
} else {
    mode = "default";
}

Runnable task = () -> System.out.println(mode);
task.run();
~~~

Ở mỗi nhánh, `mode` chỉ được gán một lần và đã có giá trị trước khi lambda sử dụng. Nếu có một nhánh khiến biến chưa được khởi tạo hoặc có phép gán lại sau đó, đoạn mã sẽ không còn đáp ứng điều kiện.

## 2. Những biến nào chịu quy tắc này?

Quy tắc áp dụng khi lambda, local class hoặc anonymous class bắt giữ một biến cục bộ hay tham số từ phạm vi bên ngoài. Các trường hợp thường gặp gồm:

- Biến cục bộ khai báo trong phương thức.
- Tham số phương thức hoặc constructor.
- Tham số của lambda bên ngoài khi một lambda lồng bên trong sử dụng nó.
- Biến được khai báo trong một phương thức rồi được dùng trong local class hoặc anonymous class.

Ví dụ với tham số phương thức:

~~~java
static Runnable createPrinter(String message) {
    return () -> System.out.println(message);
}
~~~

`message` là tham số của `createPrinter`. Vì phương thức không gán lại tham số này, lambda có thể bắt giữ nó. Lambda có thể được chạy sau khi `createPrinter` trả về; vì vậy biến cục bộ không cần tiếp tục tồn tại như một biến cục bộ đang hoạt động của phương thức.

Các trường instance và static không chịu yêu cầu effectively final này. Lambda có thể đọc hoặc cập nhật một trường theo quy tắc truy cập thông thường. Tuy vậy, khả năng cập nhật trường không tự bảo đảm an toàn luồng; nếu nhiều luồng cùng truy cập, vẫn cần thiết kế đồng bộ phù hợp.

## 3. Ví dụ Java chạy hoàn chỉnh

Lưu mã sau thành `EffectivelyFinalDemo.java`. Ví dụ cho thấy lambda bắt giữ một biến số nguyên, anonymous class bắt giữ một chuỗi, và lambda vẫn có thể thay đổi trạng thái của đối tượng được tham chiếu bởi một biến effectively final.

~~~java
import java.util.function.IntUnaryOperator;

public class EffectivelyFinalDemo {
    public static void main(String[] args) {
        int multiplier = 3;
        IntUnaryOperator timesMultiplier = value -> value * multiplier;
        System.out.println(timesMultiplier.applyAsInt(4));

        String prefix = "ID-";
        Runnable printId = new Runnable() {
            @Override
            public void run() {
                System.out.println(prefix + 7);
            }
        };
        printId.run();

        int[] counter = {0};
        Runnable increment = () -> counter[0]++;
        increment.run();
        increment.run();
        System.out.println(counter[0]);
    }
}
~~~

Biên dịch và chạy bằng Java 17 trở lên:

~~~text
javac EffectivelyFinalDemo.java
java EffectivelyFinalDemo
~~~

Kết quả stdout chính xác:

~~~text
12
ID-7
2
~~~

`multiplier` và `prefix` được bắt giữ nhưng không bị gán lại. Biến `counter` cũng effectively final: biến tham chiếu vẫn trỏ đến cùng một mảng. Lệnh `counter[0]++` thay đổi phần tử của mảng, không gán lại biến `counter`. Vì thế ví dụ hợp lệ và in ra `2`.

## 4. Gán lại biến sẽ gây lỗi biên dịch

Một khi biến cục bộ được lambda hoặc lớp lồng bắt giữ, không thể gán lại biến đó, kể cả phép gán nằm sau dòng khai báo lambda:

~~~java
int attempts = 1;
Runnable task = () -> System.out.println(attempts);
attempts = 2; // Lỗi biên dịch: attempts không effectively final
~~~

Lỗi không phụ thuộc vào việc lambda đã chạy hay chưa. Trình biên dịch xét toàn bộ cách sử dụng biến. Phép gán kết hợp như `attempts += 1` và phép tăng như `attempts++` cũng vi phạm quy tắc. Cách sửa tùy ý nghĩa mong muốn:

- Nếu giá trị phải cố định, hãy bỏ phép gán lại hoặc khai báo biến là `final`.
- Nếu mỗi tác vụ cần một giá trị riêng, hãy tạo một biến mới, effectively final, cho tác vụ đó.
- Nếu cần trạng thái thay đổi, hãy cân nhắc một đối tượng có trạng thái hoặc một trường phù hợp; vẫn phải xử lý vấn đề đồng bộ nếu có nhiều luồng.

## 5. Vòng lặp và biến được lambda bắt giữ

Biến đếm của vòng `for` cổ điển thường được tăng ở phần cập nhật của vòng lặp, nên không effectively final:

~~~java
for (int i = 0; i < 3; i++) {
    Runnable task = () -> System.out.println(i); // Lỗi biên dịch
}
~~~

`i++` thay đổi chính biến `i` qua mỗi vòng. Nếu cần mỗi lambda dùng giá trị của một vòng cụ thể, hãy sao chép giá trị đó vào biến mới bên trong thân vòng lặp:

~~~java
for (int i = 0; i < 3; i++) {
    int current = i;
    Runnable task = () -> System.out.println(current);
    task.run();
}
~~~

Kết quả:

~~~text
0
1
2
~~~

Mỗi lần chạy qua thân vòng lặp, một biến `current` mới được khởi tạo. Biến đó không bị gán lại sau khi khởi tạo, vì vậy lambda có thể bắt giữ nó. Nếu đưa khai báo `current` ra ngoài vòng lặp rồi gán lại ở mỗi vòng, vấn đề effectively final sẽ xuất hiện trở lại.

## 6. Bắt giữ tham chiếu không đồng nghĩa với đóng băng đối tượng

Effectively final nói về **biến không bị gán lại**, chứ không nói rằng đối tượng mà biến tham chiếu đến là bất biến.

~~~java
var names = new java.util.ArrayList<String>();
Runnable task = () -> names.add("Ada");
task.run();
System.out.println(names);
~~~

Kết quả:

~~~text
[Ada]
~~~

`names` vẫn trỏ đến cùng một danh sách, nên biến tham chiếu effectively final. Nội dung danh sách có thể thay đổi thông qua `add`. Tương tự, `final List<String> names` ngăn gán một danh sách khác cho `names` nhưng không tự ngăn thêm hoặc xóa phần tử.

Cần phân biệt ba việc:

- **Gán lại biến:** `names = anotherList` đổi tham chiếu và vi phạm effectively final nếu `names` đang bị bắt giữ.
- **Thay đổi đối tượng:** `names.add("Ada")` giữ nguyên tham chiếu và được phép.
- **Đảm bảo an toàn luồng:** effectively final không làm cho đối tượng mutable trở nên thread-safe. Dùng chung một đối tượng giữa nhiều luồng vẫn cần cân nhắc đồng bộ, cấu trúc dữ liệu an toàn luồng hoặc quyền sở hữu dữ liệu.

Với kiểu nguyên thủy, lambda nhận giá trị của biến. Với biến tham chiếu, giá trị được bắt giữ là tham chiếu đến đối tượng; lambda không nhận một bản sao sâu của toàn bộ đối tượng.

## 7. Lambda, local class và anonymous class

Cùng một nguyên tắc được áp dụng khi local class hoặc anonymous class sử dụng biến cục bộ bên ngoài.

~~~java
int base = 10;

class Adder {
    int add(int value) {
        return base + value;
    }
}

Adder adder = new Adder();
System.out.println(adder.add(5));
~~~

`base` phải là final hoặc effectively final vì local class `Adder` sử dụng biến khai báo trong phương thức bên ngoài. Anonymous class trong ví dụ hoàn chỉnh cũng bắt giữ `prefix` theo quy tắc đó.

Có một khác biệt đáng nhớ về `this`: bên trong lambda, `this` vẫn chỉ đối tượng bao quanh lambda; bên trong anonymous class, `this` chỉ chính đối tượng anonymous class. Khác biệt này có thể ảnh hưởng đến cách truy cập thành viên, nhưng không thay đổi quy tắc effectively final dành cho biến cục bộ được bắt giữ.

## 8. Lỗi phổ biến và cách xử lý

| Triệu chứng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| Lỗi “must be final or effectively final” | Biến cục bộ bị gán lại hoặc tăng/giảm sau khi khởi tạo | Bỏ phép gán lại hoặc dùng biến mới cho mỗi tác vụ |
| Đã gán lại sau dòng lambda nhưng lambda chưa chạy | Trình biên dịch xét quy tắc gán, không chờ đến lúc chạy | Giữ biến effectively final trong toàn bộ phạm vi |
| Đã khai báo `final` nhưng vẫn thay đổi được danh sách | `final` khóa việc gán lại tham chiếu, không khóa trạng thái đối tượng | Dùng kiểu dữ liệu bất biến nếu cần bất biến thực sự |
| Lambda sửa được trường nhưng không sửa được biến cục bộ | Trường và biến cục bộ chịu các quy tắc khác nhau | Chọn nơi lưu trạng thái có chủ đích và xử lý đồng bộ khi cần |
| Sao chép biến nhưng vẫn lỗi | Biến sao chép cũng bị gán lại sau đó, hoặc bản sao nằm ngoài vòng lặp và bị cập nhật liên tục | Khởi tạo biến riêng trong phạm vi mỗi tác vụ và không gán lại |

## 9. Checklist khi đọc hoặc viết code

1. Xác định biến nào được khai báo bên ngoài lambda hoặc local/anonymous class nhưng được sử dụng bên trong.
2. Kiểm tra tất cả đường đi trong mã: biến có được khởi tạo trước khi sử dụng và có bị gán lại, tăng hoặc giảm không?
3. Nếu cần giữ giá trị riêng cho từng vòng lặp, tạo biến cục bộ mới trong thân vòng lặp.
4. Nếu chỉ thay đổi trạng thái của đối tượng được tham chiếu, xác nhận rằng việc đó là chủ đích và có an toàn trong ngữ cảnh chạy hay không.
5. Phân biệt quy tắc biên dịch effectively final với tính bất biến và an toàn luồng; đây là ba thuộc tính khác nhau.

Quy tắc effectively final giúp Java xác định rõ giá trị cục bộ nào có thể được lambda và lớp cục bộ/ẩn danh sử dụng, kể cả khi hành động đó chạy sau khi phương thức ban đầu kết thúc.

</details>

- [Quay lại đầu trang](#back-to-top)
