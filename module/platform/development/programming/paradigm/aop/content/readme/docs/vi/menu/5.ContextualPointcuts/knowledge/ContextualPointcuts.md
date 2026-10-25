<a id="back-to-top"></a>

# Pointcut theo ngữ cảnh

## Menu
- [Chọn theo kiểu của đối tượng đang thực thi và đối tượng đích](#contextual-pointcuts-001)
- [Chọn theo kiểu hoặc giá trị tham số khi được hỗ trợ](#contextual-pointcuts-002)
- [Chọn theo annotation và siêu dữ liệu của chương trình](#contextual-pointcuts-003)
- [Chọn theo luồng điều khiển của một lời gọi](#contextual-pointcuts-004)
- [Điều kiện tĩnh và điều kiện cần đánh giá lúc chạy](#contextual-pointcuts-005)
- [Ràng buộc biến ngữ cảnh cho advice sử dụng](#contextual-pointcuts-006)
- [Kết hợp điều kiện ngữ cảnh với phạm vi cấu trúc](#contextual-pointcuts-007)
- [Giới hạn của ngữ cảnh khi công việc chuyển sang luồng hoặc tác vụ khác](#contextual-pointcuts-008)
- [Chi phí và khả năng kiểm thử của điều kiện động](#contextual-pointcuts-009)

## <a id="contextual-pointcuts-001">Chọn theo kiểu của đối tượng đang thực thi và đối tượng đích</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn theo kiểu của đối tượng đang thực thi và đối tượng đích trong AOP

## 1. Bài toán: hai đối tượng trong cùng một điểm can thiệp

Khi thiết kế pointcut cho **Aspect-Oriented Programming (AOP)**, không phải lúc nào chỉ tên phương thức hoặc lớp khai báo cũng đủ xác định vị trí áp dụng advice. Trong một lần gọi phương thức instance, có thể tồn tại ít nhất hai vai trò đối tượng:

- **Đối tượng đang thực thi (currently executing object, thường gọi là `this` theo mô hình join point):** đối tượng chứa ngữ cảnh instance của mã đang chạy ở điểm can thiệp.
- **Đối tượng đích (target object):** đối tượng nhận lời gọi hoặc là đối tượng chịu tác động trực tiếp của thao tác tại join point.

Hai đối tượng có thể **khác nhau tại method-call join point** nhưng thường **trùng nhau tại method-execution join point** của một phương thức instance. Vì vậy, việc chọn pointcut dựa trên loại đối tượng phải đi kèm câu trả lời: **join point nằm ở phía caller hay bên trong phần mã đang thực thi của callee?**

Ví dụ:

~~~text
Client (đang thực thi send())
    │
    └──► Service.execute()
              │
              └──► logic nghiệp vụ
~~~

Ở **method call join point** tại câu lệnh gọi bên trong `Client.send()`, đối tượng đang thực thi là instance `Client`, còn đối tượng đích là instance `Service`. Ở **method execution join point** của `Service.execute()`, phương thức thuộc chính đối tượng `Service` đang chạy; khi đó `this` và `target` cùng biểu thị receiver `Service` theo ngữ nghĩa AOP tương ứng.

Đây là kiến thức **pointcut theo ngữ cảnh động**, khác với chọn điểm chỉ dựa vào tên phương thức, vị trí mã hay chữ ký tĩnh. Những ví dụ `this(Type)` và `target(Type)` trong bài dùng **AspectJ** làm một ngôn ngữ AOP minh họa có mô hình join point rõ ràng. Không phải mọi công cụ AOP đều có hai toán tử này hoặc hỗ trợ cả call và execution. Phải kiểm tra cơ chế weaving/interception của công cụ cụ thể.

## 2. Khái niệm nền: join point, pointcut và advice

| Khái niệm | Nội dung cần nhớ |
|---|---|
| **Join point** | Một sự kiện hoặc vị trí trong quá trình chạy mà mô hình AOP hỗ trợ nhận diện, chẳng hạn method call, method execution hoặc field access. |
| **Pointcut** | Một điều kiện chọn một tập join point. Có thể xét chữ ký, kiểu, phạm vi mã và thông tin runtime. |
| **Advice** | Hành vi được thực thi tại join point phù hợp, ví dụ before, after hoặc around. |
| **Caller** | Mã thực hiện lời gọi phương thức. Khi mã caller nằm trong instance method, có thể có đối tượng đang thực thi ở phía caller. |
| **Receiver / callee** | Đối tượng nhận lời gọi instance; đó là target ở method-call join point trong mô hình xét ở đây. |
| **Context binding** | Khả năng cho advice nhận được chính đối tượng/đối số của join point phù hợp, thay vì chỉ biết điểm đã khớp. |

Cần phân biệt **một loại đối tượng (type)** với **một instance cụ thể**. Điều kiện `target(Service)` thường là kiểm tra quan hệ kiểu lúc chạy, tương tự ý tưởng `receiver instanceof Service`; nó không mặc định có nghĩa “đúng instance `service` này”. Nếu muốn một đối tượng cụ thể, phải có bước so sánh danh tính hoặc một điều kiện ngữ cảnh được công cụ hỗ trợ.

Cũng cần phân biệt ba ý nghĩa của chữ “this”:

1. **`this` trong Java:** reference đến instance trong mã Java đang thực thi; không tồn tại trong static context.
2. **`this(Type)` trong ngôn ngữ pointcut:** điều kiện chọn join point dựa trên đối tượng đang thực thi tại join point; ngữ nghĩa tùy mô hình AOP.
3. **`this` viết trong thân một aspect/advice:** có thể là bản thân instance của aspect trong những ngôn ngữ có cú pháp Java-like, **không được tự suy ra là đối tượng đang được advice quan sát**.

Không nên dùng một từ “this” mà bỏ mất ranh giới giữa ba ngữ cảnh trên.

## 3. Phân biệt loại join point trước khi kiểm tra `this` và `target`

### 3.1. Method call: góc nhìn của nơi gọi

Giả sử mã Java:

~~~java
final class Client {
    void send(Service service) {
        service.execute();
    }
}
~~~

Đây là đoạn minh họa; cần định nghĩa `Service` và tạo caller để thành chương trình độc lập.

Tại **call join point** của biểu thức `service.execute()` bên trong `Client.send()`:

- Đối tượng `this` là **instance Client** đang chạy `send()`.
- Đối tượng `target` là **instance Service** mà biến `service` trỏ đến.
- Kiểu tĩnh của biểu thức `service` là **Service**; kiểu runtime của instance đó có thể là Service hoặc một subclass.
- Nếu advice được kích hoạt bởi `call(...)`, bộ dệt phải có khả năng chặn **vị trí lời gọi** đó; không phải một proxy bất kỳ đều cung cấp call join point tại source này.

Ví dụ bảng dự đoán:

| Biểu thức pointcut tại call join point | Nếu caller là Client, receiver là FastService extends Service |
|---|---|
| `this(Client)` | Khớp |
| `this(Service)` | Không khớp nếu Client không phải subtype Service |
| `target(Service)` | Khớp |
| `target(FastService)` | Khớp |
| `this(Client) && target(FastService)` | Khớp |
| `this(OtherClient) && target(FastService)` | Không khớp nếu caller không phải OtherClient |

Kết quả nói về **kiểu runtime của hai instance tại call join point**, không phải về package khai báo method hoặc kiểu static của biến. Nếu caller thực chất là subtype Client, `this(Client)` vẫn có thể khớp do quan hệ kế thừa.

### 3.2. Method execution: góc nhìn của phương thức đang chạy

Bây giờ xét chính thân phương thức:

~~~java
class Service {
    void execute() {
        // Tại đây instance Service đang thực thi.
    }
}
~~~

Ở **execution join point** của một phương thức instance trong mô hình AspectJ:

- `this` trỏ đến instance đang thực thi phương thức.
- `target` cũng chính là instance đó.
- `this(Service)` và `target(Service)` vì vậy thường cùng đúng hoặc cùng sai đối với kiểu Service ở join point này.

Nếu receiver là `FastService extends Service`, cả `this(Service)` và `target(Service)` đều có thể khớp vì `FastService` là subtype của Service. Điều kiện `this(Client)` không “nhớ” caller Client ở execution của Service; execution nằm bên trong đối tượng Service, trừ khi bản thân Service đồng thời thuộc type Client theo một cấu trúc khác.

**Kết luận chính:** muốn phân biệt *ai gọi* và *ai được gọi*, hãy xem xét **call join point**; chỉ ghép `this(Client) && target(Service)` tại **execution** không có nghĩa là “Client gọi Service”. Với execution của Service, cả hai điều kiện áp vào receiver đang thực thi nên thường không thể diễn tả caller bằng cách đó.

### 3.3. Field access: bên truy cập và object chứa trường

Trong mô hình AOP hỗ trợ **field get/set join point**, có thể có một đối tượng thực hiện thao tác đọc/ghi, và một đối tượng khác **sở hữu trường instance đang được truy cập**.

~~~java
class Sensor {
    int value;
}

class Dashboard {
    int read(Sensor sensor) {
        return sensor.value;
    }
}
~~~

Đây là đoạn minh họa. Ở điểm đọc `sensor.value` bên trong `Dashboard.read()`:

- `this` thuộc instance **Dashboard** đang chạy mã đọc trường.
- `target` thuộc instance **Sensor** có trường `value` được đọc, theo mô hình AOP hỗ trợ field access.

Một pointcut `get(int Sensor.value) && this(Dashboard) && target(Sensor)` là ý tưởng lựa chọn theo cả hai đối tượng trong một mô hình như AspectJ. Với các field **static**, không có receiver instance cho trường static; không thể sử dụng `target(Sensor)` như thể đang có một đối tượng Sensor cụ thể.

## 4. Mô hình trực quan: `this` và `target` ở ba điểm

~~~text
[Client instance]
   │  đang chạy Client.send()
   │
   ├─ JOIN POINT A: method CALL của service.execute()
   │      this   ───► Client instance
   │      target ───► Service instance
   │
   ▼
[Service instance]
   │  bắt đầu chạy Service.execute()
   │
   ├─ JOIN POINT B: method EXECUTION của Service.execute()
   │      this   ───► Service instance
   │      target ───► Service instance
   │
   │ đọc sensor.value
   │
   └─ JOIN POINT C: instance FIELD GET
          this   ───► Service instance
          target ───► Sensor instance
~~~

Sơ đồ mô tả **ngữ nghĩa** của các join point giả định. Chương trình chỉ có các join point đó nếu công cụ AOP hỗ trợ chúng và mã tương ứng nằm trong phạm vi weaving. Proxy chỉ chặn lời gọi qua đối tượng đại diện sẽ có khả năng quan sát khác với bytecode weaver có method-call, method-execution và field-access pointcut.

## 5. Pointcut theo kiểu runtime khác pointcut theo chữ ký tĩnh

Đây là nguồn nhầm lẫn lớn nhất khi sử dụng kế thừa/đa hình.

~~~java
Service service = new FastService();
service.execute();
~~~

Trong ví dụ này, **kiểu tĩnh** của biến là `Service`, nhưng **kiểu runtime** của receiver là `FastService`. Trong mô hình AspectJ:

- Một điều kiện loại `call(* Service.execute(..))` xem xét chữ ký của **call join point** và quy tắc giải quyết lời gọi tĩnh.
- Một điều kiện loại `target(FastService)` xem xét đối tượng **thực tại runtime**.
- Hai điều kiện có thể **cùng khớp** khi caller gọi qua kiểu Service mà receiver thực là FastService.
- Một `execution(* FastService.execute(..))` phụ thuộc thân phương thức thực được chọn và chữ ký execution, không đơn thuần kiểu runtime của biến nhận.

Ví dụ giả mã ý tưởng:

~~~text
Chọn mọi lời gọi execute qua hợp đồng Service
    nếu receiver runtime thuộc FastService:

    call to Service.execute
    AND target instance-of FastService
~~~

Điều kiện runtime có thể thay đổi giữa các lần thực thi **cùng một call site** nếu caller nhận các implementation khác nhau. Đây là điểm mạnh của pointcut theo ngữ cảnh đối tượng.

**Không đồng nhất subtype pattern với runtime test.** Một mẫu chữ ký `Service+` trong một ngôn ngữ có biểu diễn subtype pattern có nghĩa chọn chữ ký theo tập kiểu và quy tắc của ngôn ngữ đó. Nó không tự thay thế mọi trường hợp cần `target(Service)`. Khi một subclass **kế thừa nhưng không override** phương thức, thân thực thi có thể vẫn khai báo ở superclass trong khi receiver runtime là subclass.

## 6. Cú pháp `this(Type)` và `target(Type)` trong một mô hình AOP cụ thể

AspectJ cung cấp ví dụ được định nghĩa rõ cho hai pointcut designator:

~~~text
this(Type)
target(Type)
~~~

Chúng chọn join point theo đối tượng đang thực thi và đối tượng đích có kiểu tương thích với `Type`. Biểu thức có thể kết hợp bằng các phép toán logic:

~~~text
call(* example.Service+.execute(..))
    && this(example.Client)
    && target(example.FastService)
~~~

Đây là **mẫu cú pháp pointcut minh họa**, chưa phải chương trình Java 17 chạy bằng `javac`. Ý định: chỉ chọn lời gọi `execute` trong vùng chữ ký đã mô tả, khi mã gọi chạy trong instance Client và receiver thực thuộc FastService. Cú pháp tên package, wildcard và subtype phải theo chuẩn ngôn ngữ AOP được dùng.

Một ví dụ khác:

~~~text
execution(* example.Service+.execute(..))
    && target(example.FastService)
~~~

Ý định khác: chọn **thực thi** phương thức phù hợp khi đối tượng đang chạy là FastService. Nó không yêu cầu caller phải là Client, và vì thế có thể bắt các lần gọi từ nhiều nơi khác nếu các execution join point được weave.

Cần lưu ý một số bộ dệt có thể đơn giản hóa điều kiện dựa trên thông tin tĩnh khi kiểu chắc chắn đúng/sai; nhưng kết quả cuối vẫn phải tuân theo ngữ nghĩa chọn join point của công cụ. Không nên dựa vào việc một tool có tạo ra phép `instanceof` ở bytecode hay không để quyết định logic pointcut.

### 6.1. AND, OR và phủ định

Các ví dụ logic:

~~~text
this(Client) && target(FastService)
    → Caller runtime thuộc Client VÀ receiver runtime thuộc FastService.

this(Client) || target(FastService)
    → Ít nhất một điều kiện phù hợp.

target(Service) && !target(FastService)
    → Receiver thuộc Service nhưng không thuộc FastService.
~~~

Các biểu thức này chỉ minh họa ý nghĩa logic của **type matching**. Thứ tự đánh giá các điều kiện con và kỹ thuật tối ưu có thể do bộ dệt quyết định. Tránh đặt tác dụng phụ trong điều kiện động với giả định chúng luôn được đánh giá theo một thứ tự cụ thể.

### 6.2. Interface và subtype

Nếu **FastService implements Operation**, kiểm tra `target(Operation)` có thể đúng khi receiver là FastService, ngay cả khi caller giữ biến kiểu interface.

Nếu một đối tượng implement đồng thời hai interface, pointcut kiểm tra một trong hai interface đều có thể khớp. Điều này không phải “hai target khác nhau”; đó là **một instance có nhiều quan hệ kiểu**.

Nếu pointcut cần chọn **đúng lớp runtime cụ thể, không gồm subclass**, thì điều kiện dạng `target(Base)` thường chưa đủ vì type matching theo quan hệ subtype. Tùy công cụ, cần thêm kiểm tra chính xác runtime class hoặc cấu trúc loại trừ phù hợp. Phải cân nhắc các proxy/subclass được sinh động trước khi so sánh class tuyệt đối.

## 7. Ví dụ Java 17 có thể chạy: mô phỏng hai ngữ cảnh một cách tường minh

Để nhìn thấy loại đối tượng ở phía caller và phía target mà không cần trình dệt riêng, ví dụ này tự truyền hai đối tượng vào một bộ mô phỏng ghi nhận ngữ cảnh. **Nó không phải một công cụ weaving**: sự lựa chọn điểm quan sát được viết tường minh. Nó minh họa các quy tắc mà một engine AOP có thể dùng khi đánh giá `this(Type)` và `target(Type)`.

Tạo file **ObjectContextDemo.java**:

~~~java
public class ObjectContextDemo {
    interface Task {
        void run();
    }

    static class BaseTask implements Task {
        @Override
        public void run() {
            Context.execution(this, () -> System.out.println("BaseTask.run"));
        }
    }

    static class FastTask extends BaseTask {
        @Override
        public void run() {
            Context.execution(this, () -> System.out.println("FastTask.run"));
        }
    }

    static class Client {
        void send(Task target) {
            Context.call(this, target, target::run);
        }
    }

    static class Scheduler {
        void send(Task target) {
            Context.call(this, target, target::run);
        }
    }

    static class Context {
        static void call(Object caller, Object target, Runnable action) {
            System.out.println("CALL this=" + caller.getClass().getSimpleName()
                    + " target=" + target.getClass().getSimpleName());
            System.out.println("match=" + (caller instanceof Client
                    && target instanceof FastTask));
            action.run();
        }

        static void execution(Object receiver, Runnable body) {
            System.out.println("EXEC this=" + receiver.getClass().getSimpleName()
                    + " target=" + receiver.getClass().getSimpleName());
            body.run();
        }
    }

    public static void main(String[] args) {
        new Client().send(new BaseTask());
        new Client().send(new FastTask());
        new Scheduler().send(new FastTask());
    }
}
~~~

Biên dịch và chạy trên Java 17+:

~~~bash
javac --release 17 ObjectContextDemo.java
java ObjectContextDemo
~~~

Stdout chính xác:

~~~text
CALL this=Client target=BaseTask
match=false
EXEC this=BaseTask target=BaseTask
BaseTask.run
CALL this=Client target=FastTask
match=true
EXEC this=FastTask target=FastTask
FastTask.run
CALL this=Scheduler target=FastTask
match=false
EXEC this=FastTask target=FastTask
FastTask.run
~~~

**Phân tích:**

1. Ở lời gọi thứ nhất, caller là Client nhưng target là BaseTask. Điều kiện **caller thuộc Client AND target thuộc FastTask** sai.
2. Ở lời gọi thứ hai, caller vẫn là Client; target đã là FastTask nên hai điều kiện đều đúng. Lớp FastTask override `run()`, vì thế body FastTask.run thực thi.
3. Ở lời gọi thứ ba, target vẫn là FastTask, nhưng caller trở thành Scheduler. Điều kiện kết hợp sai vì `this(Client)` không đúng.
4. Tại phần mô phỏng **execution**, chương trình cố ý dùng **receiver** làm cả `this` lẫn `target`: đối tượng đang thực thi là BaseTask/FastTask tương ứng; nguồn gọi không còn là giá trị `this` của execution.

Khi viết một aspect thật, không cần tự gọi `Context.call`/`Context.execution` tại mỗi vị trí: bộ dệt có thể đảm nhận việc đó nếu loại join point và phạm vi được hỗ trợ. Nhưng mô phỏng này giúp phân biệt **logic chọn đối tượng** với **cơ chế tự động can thiệp**. Kết quả của một engine thật còn tùy kỹ thuật weaving, pointcut và chính sách khớp metadata.

## 8. Khi `this` và `target` trùng nhau

Chúng thường trùng ở **method-execution join point** của phương thức instance: chính receiver đang thực thi thân đó. Ngoài ra, tại một lời gọi nội bộ trên cùng object, ví dụ `this.otherMethod()`, hai vai trò ở **call join point** cũng có thể cùng chỉ về một instance.

~~~java
class Worker {
    void first() {
        second();
    }

    void second() {
        // Logic xử lý
    }
}
~~~

Đây là đoạn minh họa. Trong `first()`, lời gọi `second()` trên cùng object có **caller instance** và **receiver instance** là một.

Điều này **không làm mất ý nghĩa** hai designator: chúng vẫn trả lời hai câu hỏi khác nhau. Việc cả hai cùng đúng ở một số tình huống không cho phép coi `this(Type)` và `target(Type)` tương đương trong mọi loại join point.

Đặc biệt khi dùng proxy chặn method-call, lời gọi **self-invocation** bên trong target có thể không đi qua proxy nên advice của proxy **không chạy**, dù đối tượng Java vẫn có cả ngữ cảnh caller và target. Hai vấn đề độc lập:

- Ngữ nghĩa runtime của các đối tượng hiện diện.
- Cơ chế AOP có thực sự intercept được join point đó hay không.

## 9. Khi `this` không tồn tại hoặc không có target instance

### 9.1. Phương thức static

Trong Java, mã static không có `this` của instance. Với một **static method execution join point**, cũng không có receiver object theo nghĩa gọi phương thức instance; một pointcut bắt buộc `this(SomeClass)` hoặc `target(SomeClass)` không nên được kỳ vọng khớp như thể có một object tương ứng.

Xét mã minh họa:

~~~java
class Utility {
    static void check() {
        // Không có "this" của instance tại đây.
    }
}
~~~

Nếu mục tiêu là quan sát `Utility.check()`, thường phải dùng **chữ ký static/execution** hoặc điều kiện lexical thích hợp; không thể dùng `target(Utility)` để thay cho “method thuộc lớp Utility”.

### 9.2. Lời gọi instance phát ra từ static context

~~~java
class JobLauncher {
    static void launch(Task target) {
        target.run();
    }
}
~~~

Đây là đoạn minh họa, `Task` là một interface đã biết trong phạm vi ví dụ. Ở **call join point** của `target.run()` bên trong static method:

- Không có `this` instance của `JobLauncher`.
- Vẫn có đối tượng target nhận lời gọi, ví dụ một FastTask.

Vì thế `target(FastTask)` có thể khớp nhưng `this(JobLauncher)` không tự khớp vì source nằm trong lớp đó. Để chọn theo **nơi mã được khai báo**, cần điều kiện lexical như `within(JobLauncher)` hoặc `withincode(...)` ở mô hình hỗ trợ.

### 9.3. Một số join point khác

Constructor call/execution, static initialization, handler execution và các dạng advice execution có quy tắc availability của `this`/`target` riêng. Không nên mở rộng bảng method call/execution một cách máy móc sang mọi join point.

Nếu không có target object tại loại join point đã chọn, biểu thức `target(T)` không có instance để kiểm tra. Một bộ dệt có thể bỏ qua điểm đó vì không thể khớp, hoặc áp dụng ngữ nghĩa đặc thù đã được định nghĩa. Khi thiết kế pointcut, luôn tra **join point model** của engine.

## 10. Phân biệt `this`, `target`, `args` và vùng lexical

Các pointcut designator thường gặp có nhiệm vụ khác nhau:

| Điều kiện | Câu hỏi nó trả lời |
|---|---|
| **this(T)** | Đối tượng đang thực thi tại join point có thuộc T không? |
| **target(T)** | Đối tượng đích của join point có thuộc T không? |
| **args(T)** | Đối số của join point có tương thích kiểu T ở vị trí tương ứng không? |
| **within(T)** | Mã chứa join point có nằm trong khai báo kiểu T không? |
| **withincode(...)** | Join point có nằm trong thân method/constructor có chữ ký phù hợp không? |
| **call(...)** | Tại đây có một lời gọi với chữ ký thỏa điều kiện không? |
| **execution(...)** | Tại đây thân method có chữ ký phù hợp đang được thực thi không? |

Ví dụ ba thao tác tương tự về bề ngoài nhưng khác nghĩa:

~~~text
this(Client)        : object caller/current execution context là Client.
target(Client)      : receiver/target object là Client.
within(Client)      : mã chứa join point nằm về mặt lexical trong Client.
~~~

Một method **kế thừa** từ Base đang thực thi trên instance Child có thể khiến:

- `this(Child)` và `target(Child)` cùng đúng tại execution nếu receiver là Child.
- `within(Child)` không nhất thiết đúng cho thân method **khai báo trong Base**.
- Chỉ thấy tên Child ở runtime không đủ suy ra phạm vi lexical của body.

### 10.1. Một phản ví dụ thường gặp

Ý định: “Chỉ áp dụng advice khi Client gọi vào Service”.

Sai về mặt mô hình nếu viết:

~~~text
execution(* Service.execute(..))
    && this(Client)
    && target(Service)
~~~

Tại execution của method Service instance, `this` gắn với chính Service. Điều kiện `this(Client)` không diễn tả caller Client và thường khiến pointcut không khớp trong kiến trúc Client/Service tách biệt.

Nếu mô hình hỗ trợ method-call join point tại caller, một ý tưởng phù hợp hơn là:

~~~text
call(* Service.execute(..))
    && this(Client)
    && target(Service)
~~~

Hoặc nếu ý định thật là “mọi execution Service mà lời gọi bên ngoài xuất phát từ Client”, cần một **điều kiện theo luồng điều khiển** hoặc truyền **request context** theo cơ chế phù hợp, chứ không đồng nhất đối tượng `this` của execution với caller từ một khung stack khác. Các cơ chế theo control flow cũng phải cân nhắc nested calls, chi phí và asynchronous execution.

## 11. Kết hợp với điều kiện theo luồng thực thi

Đôi khi mong muốn không phải “caller ngay tại call site thuộc T” mà là “đang ở **bên trong chuỗi xử lý được bắt đầu bởi** một thao tác trên T”.

Một số ngôn ngữ AOP cung cấp khái niệm **cflow** (control flow) để diễn đạt join point hiện tại nằm trong luồng động của một điểm khác. Ví dụ ý tưởng:

~~~text
execution of Service.execute
AND within dynamic control flow of Client.handle
~~~

Khác biệt quan trọng:

- `this(Client)` ở execution của Service **không tự mang lại** caller Client.
- Một điều kiện control flow có thể chọn execution Service khi nó xảy ra trong ngữ cảnh động có liên quan đến Client.
- Control flow có thể chứa nhiều lớp trung gian, nhiều lời gọi lồng nhau hoặc recursion; cần xác định có bao gồm chính điểm bắt đầu hay không.

**Async boundary:** một callback chạy ở thread khác hoặc vào thời điểm sau khi call stack ban đầu kết thúc có thể không còn cùng control flow thông thường. Nếu cần theo yêu cầu xuyên thread, hãy dùng cơ chế truyền context/trace identity thích hợp; không giả định `this` hay `cflow` tự lan truyền sang mọi tác vụ bất đồng bộ.

## 12. Binding: lấy chính đối tượng được match để dùng trong advice

Một pointcut không chỉ có thể trả về đúng/sai; trong những mô hình hỗ trợ, nó còn **ràng buộc reference** của đối tượng liên quan cho advice.

Ví dụ cú pháp minh họa kiểu AspectJ:

~~~java
// Minh họa cú pháp advice của một ngôn ngữ AOP,
// không phải lớp Java độc lập biên dịch bằng javac.

before(Object current, Object receiver):
        call(* example.Service+.execute(..))
        && this(current)
        && target(receiver) {
    System.out.println(current.getClass().getSimpleName()
            + " -> " + receiver.getClass().getSimpleName());
}
~~~

Ví dụ diễn tả việc lấy đối tượng đang thực thi và receiver. Trong một mô hình AOP có **formal parameters** cho advice, tên tham số và khai báo pointcut phải đúng quy tắc binding của ngôn ngữ. Với kiểu formal cụ thể hơn, phép binding đồng thời có thể là điều kiện lọc kiểu.

Trong thực tế, ưu tiên **binding typed** có phạm vi rõ ràng khi cần truy cập API của một loại object. Nếu chỉ cần tên lớp, signature hoặc thống kê, có thể dùng metadata của join point thay vì giữ reference mạnh đến object, để giảm coupling và rủi ro lưu giữ object lâu ngoài ý định.

### 12.1. Chọn theo kiểu không phải chọn theo identity

~~~text
target(Service)
~~~

Có thể khớp nhiều instance khác nhau của Service và subclass của nó. Không nên dùng nó để diễn đạt “duy nhất instance có id 42”, trừ khi có thêm điều kiện ngữ cảnh thích hợp.

Nếu advice cần phân biệt hai instance có cùng runtime class, có thể dùng một khóa định danh thuộc miền hoặc so sánh reference một cách có kiểm soát. Không nên suy từ `getClass()` rằng hai object là một.

### 12.2. Kiểu runtime bị thay đổi bởi decorator hoặc proxy

Ở một đường interception sử dụng đối tượng đại diện, kiểu runtime **của reference đi qua interceptor** có thể không trùng với lớp implementation gốc:

~~~text
Caller ─► Proxy/Decorator ─► Service implementation
~~~

Một số công cụ quan sát target implementation; một số khác quan sát proxy hoặc một đối tượng khác do cơ chế weaving định nghĩa. Khi đánh giá `this(Type)` và `target(Type)` theo ngôn ngữ AOP cụ thể, phải xác nhận **đối tượng nào thực sự được bind**. Không chuyển quy tắc giữa hai công cụ chỉ vì cả hai đều có khái niệm proxy/target.

Đây cũng là lý do `object.getClass() == Service.class` có thể quá hẹp. Khi muốn match họ kiểu, `instanceof Service` hoặc điều kiện kiểu tương thích trong pointcut phản ánh polymorphism tốt hơn, nếu policy cần bao gồm subclass/proxy hợp lệ.

## 13. Phạm vi của advice và ảnh hưởng của polymorphism

Nếu `FastService extends Service`, `target(Service)` thường khớp receiver thuộc FastService vì nó là subtype. Điều này có thể đúng cả khi:

- Caller khai báo reference Service.
- Caller khai báo reference qua interface.
- Thân thực thi là một override ở FastService.
- Thân thực thi là một phương thức được kế thừa không override, do Service khai báo.

Tuy nhiên **pointcut type condition khớp** chưa có nghĩa **advice chắc chắn chạy**. Cần cả các điều kiện:

1. Loại join point (call/execution/field access...) được engine hỗ trợ.
2. Join point nằm trong phạm vi weaving hoặc đi qua interceptor đã đăng ký.
3. Các điều kiện chữ ký, visibility, lexical và control flow cùng khớp.
4. Tại runtime, object được bind làm `this` hoặc `target` thực sự tương thích T.
5. Không có điều kiện loại trừ hoặc advice precedence nào làm thay đổi kết quả quan sát theo cách ngoài dự tính.

Việc thay một instance Service bằng FastService có thể thay kết quả `target(FastService)` **tại cùng một call site**; đó là một hành vi mong đợi của dynamic pointcut. Nhưng nếu refactor đổi nơi khai báo thân hoặc bỏ phạm vi instrument, việc khớp pointcut dựa chữ ký cũng có thể thay đổi.

## 14. Khi `this` và `target` là hai vai trò khác nhau nhưng cùng kiểu

Giả sử `Service` có thể gọi một Service khác:

~~~java
class Service {
    void forward(Service next) {
        next.execute();
    }

    void execute() {
        // Xử lý
    }
}
~~~

Ở call join point bên trong `forward`:

- `this(Service)` đúng nếu caller object là Service hoặc subtype.
- `target(Service)` cũng đúng vì receiver `next` là Service hoặc subtype.
- Nhưng **hai instance có thể khác nhau**: caller `first`, target `second`.

Điều kiện `this(Service) && target(Service)` chỉ chứng minh cả hai thuộc loại Service, **không chứng minh `this == target`**. Nếu cần quan sát giao tiếp giữa các instance khác nhau, advice hoặc điều kiện động phải phân biệt danh tính và đảm bảo không thay đổi trạng thái ngoài ý định.

Ngược lại, một self-call dùng chính reference `this` có thể khiến danh tính trùng nhau; nhưng chỉ đọc kiểu cũng không phân biệt được hai trường hợp đó.

## 15. Tác dụng phụ và quyền truy cập khi bind đối tượng

Binding giúp advice đọc được object và thậm chí gọi phương thức của nó. Điều đó rất mạnh nhưng không đồng nghĩa luôn an toàn.

- **Side effect:** gọi một phương thức khác của receiver bên trong advice có thể tạo thêm join point, thậm chí kích hoạt lại cùng aspect.
- **Reentrancy:** nếu pointcut rộng, advice gọi logging/helper mà logging/helper lại nằm trong tập chọn, có thể tạo vòng lặp quan sát không mong muốn.
- **Thread safety:** object target có thể được nhiều luồng sử dụng; advice không nên giả định object chỉ có một caller.
- **Partial initialization:** tại join point constructor hoặc giai đoạn init, object có thể chưa hoàn tất bất biến.
- **Sensitive data:** việc có reference target không cho phép thu thập mọi trường/giá trị của target; dữ liệu truy vết phải tuân thủ chính sách hệ thống.
- **Object retention:** nếu advice đưa object vào một collection tĩnh tồn tại lâu, object có thể không được giải phóng như dự kiến.

Khi chỉ cần tag metric theo loại thao tác, hãy ưu tiên tên logic ổn định và metadata được phép dùng, thay vì giữ toàn bộ object để “tiện debug”.

## 16. Hiệu năng: điều kiện động có chi phí thực

Một pointcut chỉ dựa trên chữ ký có thể được tối ưu phần lớn ở lúc weaving. Ngược lại, `this(T)`/`target(T)` có thể cần đánh giá quan hệ kiểu trong lúc chạy nếu không thể xác định hoàn toàn từ thông tin tĩnh.

Điều này không có nghĩa dynamic pointcut luôn chậm: JVM có các tối ưu cho type check, và một số trường hợp bộ dệt suy luận được kết quả. Tổng chi phí còn bao gồm:

- Bao nhiêu join point phải qua điều kiện.
- Có bao nhiêu advice cùng được áp dụng.
- Advice làm I/O hay chỉ đọc metadata.
- Có tạo đối tượng/chuỗi log mới mỗi lượt không.
- Có kiểm tra thêm control flow hay binding phức tạp không.

Nếu một rule áp dụng vào phương thức được gọi rất thường xuyên, hãy đo trên workload thực tế. Không nên quyết định bỏ `target(Type)` chỉ dựa trên giả định rằng mọi phép `instanceof` đều tốn kém.

## 17. Lỗi thường gặp khi chọn pointcut theo đối tượng

### Lỗi 1 — Nhầm `this` với target ở method call

Một advice định giám sát “mọi lần Client gọi tới FastService” nhưng viết `this(FastService)`, khiến điều kiện chỉ xét caller có phải FastService không. Ở call site thuộc Client, điều kiện sai; cần dùng `target(FastService)` nếu muốn xét receiver, đồng thời chọn đúng điểm call.

### Lỗi 2 — Dùng `this(Client)` tại execution để suy ra caller

Ở execution của `FastService.run()`, current executing object chính là instance FastService. Nếu cần biết caller Client, pointcut execution + this(Client) không tự diễn đạt được nguồn gọi. Cần method-call join point tại caller hoặc cơ chế ngữ cảnh khác.

### Lỗi 3 — Đồng nhất `within(Client)` với `this(Client)`

`within` diễn tả nơi mã khai báo; `this` là object runtime. Một phương thức kế thừa khai báo ở Base nhưng chạy trên Child cho thấy hai điều kiện có thể khác nhau.

### Lỗi 4 — Chỉ dựa vào kiểu tĩnh của biến

`Service value = new FastService()` có type tĩnh Service nhưng receiver runtime FastService. Một dynamic target pointcut có thể khớp FastService ngay cả khi chữ ký call thuộc Service.

### Lỗi 5 — Nhầm subtype với đúng class

`target(Service)` thường bao gồm mọi subtype phù hợp; nếu policy chỉ dành cho đúng implementation cụ thể, phải bổ sung điều kiện thích hợp và đánh giá tác động proxy/subclass.

### Lỗi 6 — Tưởng static method có target object

Không có receiver instance cho static execution. Dùng `target(Utility)` để chọn static method trong Utility là mô hình sai; chọn theo signature/lexical context.

### Lỗi 7 — Tưởng có target thì sẽ có this

Một lời gọi instance nằm trong static method vẫn có target receiver nhưng không có this của caller instance. Hai ngữ cảnh cần được xét độc lập.

### Lỗi 8 — Tưởng hai object cùng kiểu là cùng một instance

`this(Service) && target(Service)` có thể đúng khi Service A gọi Service B. Nếu policy chỉ dành cho self-call, điều kiện kiểu đơn thuần chưa đủ.

### Lỗi 9 — Áp đặt semantics của một engine cho mọi công cụ AOP

Một proxy chỉ intercept public interface method có thể không hỗ trợ call-site hoặc field-get join point như một bytecode weaver. Tên “this”/“target” trong API của framework cũng có thể khác object trong mô hình AspectJ. Luôn xác định ranh giới interception thực tế.

### Lỗi 10 — Kiểm thử bằng cách gọi trực tiếp advice

Advice tự chạy đúng khi được gọi như method Java chỉ chứng minh logic bên trong. Phải có test đi qua cơ chế AOP thật để chứng minh join point được chọn, object được bind đúng và lời gọi ngoài phạm vi không bị ảnh hưởng.

## 18. Quy trình thiết kế một dynamic object-type pointcut

**Bước 1 — Viết một câu về chủ thể.** Ví dụ: “Ghi nhận khi một Client instance gọi một FastService instance”, hoặc “Ghi nhận mọi lần FastService instance thực thi phương thức execute”. Đừng gom hai yêu cầu vào một câu mơ hồ.

**Bước 2 — Chọn loại join point.** Chọn call nếu chính caller tại vị trí phát lệnh là quan trọng; chọn execution nếu cần quan sát thân đang chạy từ bất kỳ caller nào. Chỉ dùng loại được engine hỗ trợ.

**Bước 3 — Gán vai trò object.** Với call: ai là executing object (`this`), ai là target? Với execution: cả hai có cùng receiver không? Nếu mã static, vai trò nào không tồn tại?

**Bước 4 — Chọn type condition.** Chọn `this(Type)` hoặc `target(Type)` theo vai trò, thêm interface/subtype nếu cần. Đừng dùng `within(Type)` để thay dynamic type.

**Bước 5 — Giới hạn chữ ký và phạm vi.** Một `target(Service)` đơn độc có thể chọn nhiều join point ngoài dự tính. Kết hợp `call`/`execution`, method pattern, package/lexical restriction hoặc metadata phù hợp.

**Bước 6 — Kiểm thử cặp đối tượng.** Thử caller Client/OtherClient và target BaseService/FastService theo ma trận. Bảo đảm pointcut đúng cả trường hợp *không được chạy*.

**Bước 7 — Kiểm thử cơ chế dệt thật.** Chạy qua đúng đường proxy/weaving, với self-call, static context, override và lỗi khi phù hợp.

**Bước 8 — Kiểm tra advice và chi phí.** Đảm bảo advice không thay đổi return/exception ngoài ý định, không tái nhập vô hạn, không giữ target lâu và không ghi dữ liệu nhạy cảm.

## 19. Ma trận kiểm thử đề xuất

Giả sử policy mong muốn là “một **Client** gọi vào một **FastService**”. Với điểm quan sát **method call**, xét ma trận:

| Đối tượng đang thực thi | Đối tượng đích | Có chọn không? |
|---|---|---|
| Client | BaseService | Không |
| Client | FastService | Có |
| Scheduler | FastService | Không |
| Scheduler | BaseService | Không |
| Subclass của Client | FastService | Có, nếu điều kiện chấp nhận subtype Client |
| Client | Subclass của FastService | Có, nếu điều kiện chấp nhận subtype FastService |
| Không có caller instance (static context) | FastService | Không với điều kiện `this(Client)` |
| Client | Không có target instance (static call) | Không với điều kiện `target(FastService)` |

Để xây dựng bài kiểm thử AOP thực:

1. Dùng fixture có hai lớp caller instance khác nhau, một static caller và hai lớp target có quan hệ kế thừa.
2. Gắn advice đếm lượt và bind object nếu engine hỗ trợ.
3. Kích hoạt từng cặp caller-target qua cùng phương thức có chữ ký thích hợp.
4. Kiểm tra cặp được chọn chạy **đúng một** lượt advice tại join point mong muốn.
5. Kiểm tra cặp không được chọn chạy **không lượt** advice.
6. Với execution, đổi expectation: caller không còn là `this` của execution; kiểm tra `this`/`target` cùng là receiver.
7. Thử gọi trực tiếp target bên ngoài bộ chặn nếu đang dùng proxy; đánh giá rõ ràng hành vi có thể bỏ qua advice.
8. Khi có nhiều aspect khớp cùng điểm, kiểm tra precedence và số lần ghi nhận, tránh đếm lặp.

Có thể log `thisJoinPoint` hoặc dữ liệu join point tương đương để nhìn rõ loại điểm, chữ ký và thông tin object; đừng chỉ dựa vào tên method vì nhiều caller/overload/override có thể cùng tên.

## 20. Tự kiểm tra và lời giải

**Câu 1.** Trong `Client.send()`, Client gọi `FastService.execute()`. Tại **call join point**, `this(Client)` và `target(FastService)` cùng khớp không?

**Đáp:** Có nếu chính một instance Client đang thực thi send, receiver là instance FastService, bộ dệt có call join point và các điều kiện khác đều phù hợp.

**Câu 2.** Với cùng lời gọi, tại **execution join point** của FastService.execute, `this(Client)` có thể được dùng để tìm caller Client không?

**Đáp:** Không theo cách đó. `this` tại execution biểu thị FastService instance đang thực thi; caller phải được quan sát tại call site hoặc theo ngữ cảnh khác.

**Câu 3.** Một object FastService được giữ qua biến `Service service`. `target(FastService)` có thể khớp không?

**Đáp:** Có. Type check động dựa vào receiver thực, không chỉ kiểu tĩnh của biến.

**Câu 4.** Một Service A gọi instance Service B. `this(Service) && target(Service)` có chứng minh đó là self-call không?

**Đáp:** Không. Hai object cùng thuộc Service nhưng có thể khác identity.

**Câu 5.** `static void launch(Task task) { task.run(); }` có target Task không? Có `this` của caller không?

**Đáp:** Lời gọi `task.run()` có receiver instance Task; static method không có `this` instance của caller.

**Câu 6.** Lớp con FastService không override `execute()` của Service. `target(FastService)` có thể đúng tại execution thân Service.execute không?

**Đáp:** Có nếu object thực là FastService và join point thân Service.execute đã được weave. Kiểu khai báo method và kiểu runtime target khác nhau.

**Câu 7.** Một pointcut chỉ dựa vào `within(Client)` có tương đương “caller object runtime là Client” không?

**Đáp:** Không. `within` dựa vào vùng mã khai báo, còn `this` xét object runtime.

**Câu 8.** Vì sao test logic advice đơn thuần chưa đủ chứng minh AOP hoạt động?

**Đáp:** Test đó chưa xác nhận type condition có khớp, join point được engine hỗ trợ, weaving/interception có áp dụng và object có được bind đúng ở đường thực thi thật.

## 21. Sơ đồ ghi nhớ

~~~text
                     DỮ LIỆU MỘT JOIN POINT
                     ┌─────────────────────┐
                     │ Kind: call/execution│
                     │ Signature           │
                     │ Lexical location    │
                     │ Runtime context     │
                     └──────────┬──────────┘
                                │
                 ┌──────────────┴──────────────┐
                 ▼                             ▼
          THIS OBJECT                    TARGET OBJECT
    đối tượng đang thực thi           đối tượng nhận tác động
                 │                             │
          this(Type)                       target(Type)
                 │                             │
                 └──────────────┬──────────────┘
                                │
                        &&, ||, !, ...
                                │
                        POINTCUT KHỚP?
                                │
                 ┌──────────────┴──────────────┐
                Không                         Có
                 │                             │
           Không chạy advice              Chạy advice
                 │                         tại join point
                 └─────────────────────────────┘
~~~

**Nhớ ba nguyên tắc:**

1. Tại **method call**, `this` là đối tượng đang thực thi mã caller còn `target` là receiver của lời gọi.
2. Tại **method execution** của instance method, `this` và `target` cùng gắn với đối tượng đang chạy phương thức; chúng **không mang danh tính caller trước đó**.
3. Pointcut theo kiểu runtime cần được hiểu cùng với **loại join point, chữ ký, phạm vi weaving và đường đi của lời gọi**. Chỉ khi những điều kiện ấy đồng thời đúng, advice mới có thể được áp dụng như mong đợi.

## Tài liệu đối chiếu

- [AspectJ Programming Guide — The Dynamic Join Point Model](https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html): giải thích method-call và method-execution join points, phân biệt `this`, `target`, `args` và chữ ký điểm can thiệp.
- [AspectJ Programming Guide — Pointcuts](https://eclipse.dev/aspectj/doc/released/progguide/semantics-pointcuts.html): quy tắc lựa chọn theo object type, binding ngữ cảnh và kết hợp logic.
- [Java Language Specification, Java SE 17, §15.12](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.12): giải quyết method invocation, kiểu tĩnh, dynamic dispatch và Java method semantics.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contextual-pointcuts-002">Chọn theo kiểu hoặc giá trị tham số khi được hỗ trợ</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn pointcut theo kiểu hoặc giá trị tham số khi được hỗ trợ

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**), **pointcut** xác định những **join point** (điểm can thiệp) mà một **advice** có thể áp dụng. Ngoài tên phương thức, kiểu khai báo hoặc chữ ký phương thức, một số cơ chế AOP còn cho phép **chọn hoặc ràng buộc điểm can thiệp dựa trên tham số được truyền vào**.

Có hai câu hỏi liên quan nhưng khác bản chất:

1. **Tham số có kiểu gì?** Có thể nói đến **kiểu khai báo** trong chữ ký hoặc **kiểu thực tế** của đối tượng tại thời điểm gọi.
2. **Tham số mang giá trị gì?** Chẳng hạn đối tượng có trạng thái được phép xử lý, chuỗi có tiền tố nhất định hay số tiền vượt một ngưỡng.

Điều kiện về kiểu khai báo thường có thể biết từ metadata của phương thức trước khi lời gọi xảy ra. Điều kiện về **kiểu runtime** và nhất là **giá trị runtime** đòi hỏi quan sát đối số của *từng lượt gọi*. Khả năng biểu đạt trực tiếp các điều kiện đó trong pointcut, cách binding tham số và thời điểm đánh giá là **đặc tính của hệ thống AOP cụ thể**, không phải một khả năng bắt buộc của mọi công cụ.

> **Trọng tâm:** Hiểu khác biệt giữa **declared parameter type**, **runtime argument type** và **argument value**; biết cách thiết kế quy tắc chọn có điều kiện, kiểm thử các đường thực thi và nhận diện giới hạn của AOP động. Những biểu thức minh họa như `argumentType(0) == String` hoặc `value(0).startsWith("VIP-")` bên dưới chỉ là **ký pháp mô tả ý tưởng**, không phải cú pháp AOP phổ quát.

## 1. Bối cảnh: tại sao cần pointcut theo ngữ cảnh tham số?

Giả sử một ứng dụng có phương thức:

```java
void process(Object message)
```

Một cơ chế audit có thể muốn thực hiện các hành vi khác nhau:

- Chỉ ghi thông tin bổ sung khi **đối số thực tế là chuỗi**, dù tham số được khai báo là `Object`.
- Chỉ gắn nhãn ưu tiên khi chuỗi **bắt đầu bằng `VIP-`**.
- Chỉ ghi metric cho yêu cầu có **độ dài dữ liệu vượt ngưỡng**.
- Chỉ kiểm soát số lần gọi khi một thuộc tính của đối tượng cho biết yêu cầu thuộc một nhóm xử lý đặc biệt.

Một pointcut chỉ chọn chữ ký `process(Object)` không phân biệt được các trường hợp ấy. Nhưng việc dùng tham số để quyết định cần được thiết kế cẩn thận: **chọn một join point theo metadata tĩnh** khác với **đánh giá điều kiện động theo từng lượt gọi**.

### Điểm cần phân biệt

```text
Tại thời điểm biết metadata phương thức:
    method = process
    parameterTypes = [Object]
             |
             v
Tại một lượt gọi cụ thể:
    actualArgument = "VIP-72"
    runtimeType = String
    value = "VIP-72"
             |
             v
Quy tắc động:
    runtimeType là String?
    value bắt đầu bằng "VIP-"?
             |
             +-- Có --> chạy advice theo chính sách
             |
             +-- Không --> bỏ qua advice có điều kiện
```

**Một phương thức có thể được gọi nhiều lần với những giá trị khác nhau.** Pointcut động có thể khớp ở lần thứ nhất và không khớp ở lần thứ hai dù chữ ký method, vị trí gọi và target không đổi.

## 2. Ba mức độ thông tin về tham số

| Thông tin | Ví dụ | Biết được lúc nào? | Đặc điểm |
| --- | --- | --- | --- |
| **Kiểu tham số khai báo** | `process(Object)` có `Object.class` | Từ method metadata | Không thay đổi theo mỗi lần gọi |
| **Kiểu đối tượng lúc chạy** | Một lượt nhận `String`, lượt khác nhận `Integer` | Khi có đối số thực tế không null | Có thể thay đổi giữa các lượt gọi |
| **Giá trị thực tế** | `"VIP-72"`, `42`, `null` | Khi đối số được đánh giá và truyền tới join point | Có thể thay đổi từng lượt, và với object mutable còn có thể thay đổi theo thời gian |

**Kiểu khai báo** là một phần của chữ ký phương thức Java. **Kiểu runtime** là loại của object thực sự được tham chiếu; với reference `null` thì không có object để hỏi `getClass()`. **Giá trị** có thể được kiểm tra theo thuộc tính miền nghiệp vụ, nhưng pointcut không nhất thiết có hỗ trợ sẵn cho những biểu thức tùy ý đó.

Ví dụ:

```java
Object first = "VIP-72";
Object second = 42;
Object third = null;
```

Cả ba biến có **kiểu tĩnh `Object`**, nhưng đối tượng của `first` có runtime type `String`, `second` có runtime type `Integer`, và `third` không trỏ tới object. Nếu cả ba được truyền tới `process(Object)` thì **chữ ký phương thức không đổi**.

### Tránh ngộ nhận về “kiểu của tham số”

Câu “chỉ chọn phương thức có tham số String” có thể có hai ý nghĩa khác nhau:

- Chọn **phương thức khai báo** `process(String)`, bất kể giá trị thực tế có nội dung gì.
- Chọn **lượt gọi** `process(Object)` mà giá trị đối số thực tế là instance của `String`.

Hai điều kiện này **không tương đương**. Khi thiết kế AOP, phải ghi rõ đang lọc *method metadata* hay *invocation data*.

## 3. Bộ lọc theo kiểu khai báo: nền tảng tĩnh

Kiểu tham số khai báo cho phép phân biệt overload chính xác.

```java
// Minh họa khai báo, không phải chương trình độc lập.
class MessageHandler {
    void accept(String text) { }
    void accept(Object payload) { }
    void accept(int priority) { }
}
```

Một matcher theo metadata:

```text
methodName = accept
declaredParameterTypes = [String]
```

sẽ chọn `accept(String)` và không chọn `accept(Object)` hoặc `accept(int)`. Bộ lọc này có thể được xác định từ danh sách `Class<?>` phản ánh tham số phương thức.

Nếu một biến được khai báo `Object payload = "hello"`, lời gọi `accept(payload)` trong Java có thể chọn overload `accept(Object)` ở **compile time**; việc giá trị bên trong là `String` **không tự làm Java chọn lại overload `accept(String)` ở runtime**. Dynamic dispatch của method override là một vấn đề riêng; **overload resolution** dựa trên kiểu và biểu thức tại điểm gọi theo quy tắc của Java.

Điểm mấu chốt: **lọc signature không phải lọc runtime value**.

### Primitive và wrapper

`int` trong chữ ký và `Integer` trong runtime metadata không giống nhau. Khi dùng reflection, `int.class` khác `Integer.class`. Lời gọi có thể boxing/unboxing theo quy tắc Java, nhưng matcher dựa vào danh sách kiểu khai báo sẽ không tự coi hai phương thức này là một signature.

### Array và varargs

`String...` được biểu diễn với kiểu tham số array `String[]`. Hai khai báo `process(String[])` và `process(String...)` **không tạo hai overload hợp lệ** trong cùng class; ở reflection cả hai đều có parameter type `String[].class`, còn metadata `isVarArgs()` có thể phân biệt cách khai báo. Khi xét *giá trị runtime*, đối số array là một object kiểu array; mỗi phần tử trong array không tự trở thành một đối số join point riêng biệt.

## 4. Chọn theo kiểu thực tế của đối số

**Runtime type matching** xét loại đối tượng được truyền vào tại một lượt gọi. Ý tưởng này hữu ích khi API được khai báo qua interface, lớp cha hoặc kiểu tổng quát.

```java
// Minh họa ngắn, không có main().
interface Command { }

final class CreateCommand implements Command { }

final class CancelCommand implements Command { }

void dispatch(Command command) {
    // ...
}
```

Nếu cơ chế pointcut hỗ trợ kiểm tra kiểu đối số runtime, ta có thể yêu cầu “áp dụng advice khi `command` thực tế là `CreateCommand`” mà không cần tạo overload riêng cho từng subtype.

**Tuy nhiên, ý nghĩa phép khớp phải được làm rõ:**

- **Exact type:** runtime class chính xác là `CreateCommand`.
- **Subtype/assignability:** runtime object là `CreateCommand` hoặc subtype phù hợp.
- **Interface membership:** object triển khai một interface nào đó.
- **Declared parameter type:** kiểu ghi trong phương thức `dispatch(Command)`, hoàn toàn khác ba điều kiện trên.

Một lựa chọn `instanceof CreateCommand` có ý nghĩa **tương thích kiểu**, không phải “đúng class duy nhất”. Nếu cần exact type, Java có thể kiểm tra `obj != null && obj.getClass() == CreateCommand.class`. Công cụ AOP có thể cung cấp ký pháp riêng, thường với các giới hạn do ngôn ngữ pointcut và cách triển khai.

### Khi nào null khớp?

Với `null`, không thể gọi `getClass()`. Tùy biểu thức được dùng, kiểm tra `instanceof` sẽ trả về `false`. Nhưng **không nên suy rộng rằng mọi DSL AOP đều xử lý null giống một phép `instanceof` Java**; một số ngôn ngữ có binding hoặc match type dựa trên tham số khai báo và quy tắc đặc biệt cho null.

Trong ví dụ tự viết matcher, hãy định nghĩa tường minh:

```java
static boolean isRuntimeString(Object value) {
    return value instanceof String;
}
```

Điều kiện này là `false` với `null`, số nguyên hay một object khác. Nó kiểm tra type **runtime**, không thay đổi signature khai báo.

### Interface, superclass và subclass

Giả sử một tham số khai báo `CharSequence` và lượt gọi truyền `StringBuilder` hoặc `String`. Matcher theo **declared type** chọn cùng một method; matcher theo **runtime type** có thể phân biệt. Nếu dùng `instanceof CharSequence` thì cả hai có thể khớp. Nếu dùng `value.getClass() == String.class` thì chỉ object `String` khớp.

Việc chọn đúng phép toán kiểu quan trọng hơn tên biến hay tên pointcut.

## 5. Chọn theo giá trị tham số

**Value-based condition** sử dụng nội dung/thuộc tính thực của đối số, ví dụ:

```text
arg[0] instanceof String
AND arg[0] startsWith "VIP-"
```

Hoặc:

```text
arg[0] instanceof PaymentRequest
AND arg[0].amount > 100000
```

Đây là **pseudocode**, chưa phải cú pháp pointcut chuẩn. Có công cụ chỉ cho phép **bind argument** vào advice rồi kiểm tra giá trị bên trong advice; có công cụ hỗ trợ predicate/điều kiện tại điểm khớp; có công cụ chỉ cho phép match tĩnh và không cung cấp value matching.

### Phân biệt “pointcut khớp” với “advice tự kiểm tra điều kiện”

Hai cách cài có thể quan sát tương tự nhưng khác trách nhiệm:

1. **Dynamic pointcut:** hệ thống AOP đánh giá predicate theo đối số; chỉ khi điều kiện đúng, advice tương ứng mới được áp dụng.
2. **Pointcut rộng + guard trong advice:** advice được gọi cho nhiều join point, sau đó tự kiểm tra và có thể không thực hiện hành vi bổ sung nếu điều kiện sai.

Ở phương án 2, **advice vẫn được vào**, do đó có thể có overhead hoặc các tác động khác ngay cả khi phần hành vi chính được bỏ qua. Nếu advice là around và đi qua guard, nó vẫn phải tiếp tục lời gọi gốc theo contract; bỏ qua bước chuyển tiếp có thể làm nghiệp vụ không chạy.

### Khi nào chọn theo giá trị?

- Thu thập **metric tùy thuộc nhóm yêu cầu** mà dữ liệu đã đủ ổn định.
- Ghi thêm thông tin **tracing** khi một loại payload đặc biệt xuất hiện.
- Áp dụng giới hạn, bộ lọc hoặc kiểm tra tại ranh giới đã xác định.
- Phân loại thao tác để audit theo một nhãn nghiệp vụ.

Với **quyết định nghiệp vụ cốt lõi** hoặc **bảo mật bắt buộc**, cần đảm bảo quy tắc được thực thi ở ranh giới đáng tin cậy và không tồn tại đường bypass. Một điều kiện AOP chỉ chạy khi lời gọi đi qua proxy không bảo vệ được tất cả các đường gọi nếu đối tượng đích vẫn có thể được gọi trực tiếp.

## 6. Ví dụ Java 17+ độc lập: một method, nhiều giá trị runtime

Ví dụ dưới đây dùng **Java Dynamic Proxy** và `InvocationHandler` để mô phỏng **bộ lọc tĩnh theo chữ ký** kết hợp **kiểu runtime** và **điều kiện giá trị động**. Mỗi điều kiện là một hàm độc lập, vì vậy ta nhìn rõ cái gì được biết từ metadata và cái gì phụ thuộc vào lượt gọi.

**Quan trọng:** Đây là **một ví dụ interception bằng Java SE**, không phải một ngôn ngữ pointcut AOP chung. Dynamic proxy chỉ quan sát những lời gọi đi qua proxy cho các phương thức interface. Với công cụ AOP khác, cách binding tham số và điểm thực thi được hỗ trợ có thể khác.

Lưu tệp `RuntimeArgumentPointcutDemo.java` bằng UTF-8:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

public class RuntimeArgumentPointcutDemo {
    interface Inbox {
        String accept(Object payload);
        String accept(String payload);
        String check(Object payload);
    }

    static final class InboxImpl implements Inbox {
        @Override
        public String accept(Object payload) {
            return "OBJECT:" + String.valueOf(payload);
        }

        @Override
        public String accept(String payload) {
            return "STRING:" + String.valueOf(payload);
        }

        @Override
        public String check(Object payload) {
            return "CHECK:" + String.valueOf(payload);
        }
    }

    // Điều kiện tĩnh: chỉ chọn overload accept(Object).
    static boolean matchesDeclaredMethod(Method method) {
        return method.getDeclaringClass() == Inbox.class
                && method.getName().equals("accept")
                && Arrays.equals(
                        method.getParameterTypes(),
                        new Class<?>[] { Object.class });
    }

    // Điều kiện động: object thực tế là một String.
    static boolean matchesRuntimeType(Object[] args) {
        return args != null
                && args.length == 1
                && args[0] instanceof String;
    }

    // Điều kiện động theo nội dung String.
    static boolean matchesValue(Object[] args) {
        return ((String) args[0]).startsWith("VIP-");
    }

    static Inbox withAdvice(Inbox target) {
        return (Inbox) Proxy.newProxyInstance(
                Inbox.class.getClassLoader(),
                new Class<?>[] { Inbox.class },
                (proxy, method, args) -> {
                    if (matchesDeclaredMethod(method)
                            && matchesRuntimeType(args)) {
                        System.out.println("[type] String");

                        // Cast an toàn vì matchesRuntimeType() đã đúng.
                        if (matchesValue(args)) {
                            System.out.println("[value] VIP");
                        }
                    }

                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException error) {
                        // Giữ nguyên ngoại lệ được target ném ra.
                        throw error.getCause();
                    }
                });
    }

    public static void main(String[] args) {
        Inbox target = new InboxImpl();
        Inbox proxy = withAdvice(target);

        System.out.println(proxy.accept((Object) "VIP-42"));
        System.out.println(proxy.accept((Object) "NORMAL-1"));
        System.out.println(proxy.accept((Object) 17));
        System.out.println(proxy.accept((Object) null));

        // Java chọn overload accept(String) tại compile time.
        System.out.println(proxy.accept("VIP-99"));

        // Đối số đủ điều kiện về giá trị, nhưng method name khác.
        System.out.println(proxy.check((Object) "VIP-42"));
    }
}
```

Biên dịch và chạy với Java 17 trở lên:

```bash
javac -encoding UTF-8 RuntimeArgumentPointcutDemo.java
java RuntimeArgumentPointcutDemo
```

**Standard output chính xác:**

```text
[type] String
[value] VIP
OBJECT:VIP-42
[type] String
OBJECT:NORMAL-1
OBJECT:17
OBJECT:null
STRING:VIP-99
CHECK:VIP-42
```

### Phân tích từng lượt gọi

| Lời gọi | Overload được chọn | Runtime type của payload | Value condition | Có in advice? |
| --- | --- | --- | --- | --- |
| `accept((Object) "VIP-42")` | `accept(Object)` | `String` | Đúng | `[type]`, `[value]` |
| `accept((Object) "NORMAL-1")` | `accept(Object)` | `String` | Sai | Chỉ `[type]` |
| `accept((Object) 17)` | `accept(Object)` | `Integer` (boxing) | Không xét | Không |
| `accept((Object) null)` | `accept(Object)` | Không có object | Không xét | Không |
| `accept("VIP-99")` | `accept(String)` | `String` | Không xét do method khác | Không |
| `check((Object) "VIP-42")` | `check(Object)` | `String` | Không xét do tên khác | Không |

**Tại sao có cast `(Object)`?** Trong Java, overload resolution dựa trên kiểu biểu thức tại điểm gọi. `accept("VIP-42")` chọn overload `accept(String)`; `accept((Object) "VIP-42")` chọn `accept(Object)`. Dù object runtime đều là `String`, **metadata của method đã chọn khác nhau**. Cast giúp demo cố ý truyền `String` qua overload khai báo `Object`, để minh họa phần kiểm tra type động.

**Tại sao không lỗi với null?** `matchesRuntimeType` dùng `instanceof String` nên với `null` sẽ trả `false`. Vì có toán tử `&&` ngắn mạch, `matchesValue` chỉ chạy khi đối số chắc chắn là `String`. Phương thức target tiếp tục nhận null và `String.valueOf(null)` trả chuỗi `"null"`. Nếu viết guard sai rồi ép kiểu/dereference trước khi kiểm tra null, advice có thể ném lỗi trước khi nghiệp vụ chạy.

**Tại sao đầu ra cho `"VIP-99"` không có advice?** Lời gọi chọn `accept(String)`; `matchesDeclaredMethod` chỉ cho phép `accept(Object)`. Điều kiện type/value của argument không thể làm thay đổi overload đã được Java chọn.

**Tại sao `check("VIP-42")` không có advice?** Bộ lọc tĩnh kiểm tra tên `accept`. Một đối số trùng kiểu/trùng giá trị không đủ để làm khớp pointcut thuộc phương thức khác.

### Điều gì là “pointcut” trong ví dụ?

Cơ chế proxy chỉ gọi một `InvocationHandler` chung. `matchesDeclaredMethod` đại diện điều kiện tĩnh, `matchesRuntimeType` và `matchesValue` đại diện hai lớp điều kiện động. Việc in log trong handler được dùng để dễ nhìn cơ chế chọn. Trong một engine có **dynamic pointcut thực sự**, predicate có thể quyết định có kích hoạt advice hay không; handler demo chỉ làm một mô hình đơn giản.

## 7. Binding argument và vị trí tham số

Một số mô hình AOP cho phép **ràng buộc tham số được chọn** vào biến của advice để tránh đọc thủ công một mảng `Object[]`. Điều quan trọng là xác định:

- **Index:** tham số ở vị trí nào (0, 1, ...).
- **Type:** khai báo là gì và actual argument có phù hợp với kiểu bind không.
- **Nullability:** có cho phép null hay không.
- **Mỗi lần gọi:** giá trị bind của lượt gọi này không được lẫn với lượt khác.
- **Điều kiện khớp:** chỉ match type hay còn match value.

Ví dụ một chính sách áp dụng cho phương thức:

```java
void transfer(String source, String destination, long amount)
```

Nếu muốn kiểm tra ngưỡng `amount`, **đối số ở vị trí thứ ba** (index 2) mới là dữ liệu cần đọc. Nếu nhầm index 0 hoặc 1, advice có thể gặp sai kiểu hoặc kiểm tra nhầm dữ liệu.

**Với method đã được chọn bằng exact signature**, index/type thường có thể được xác định trước. Với wildcard “mọi method có một tham số bất kỳ”, phải kiểm tra số lượng và type trước khi ép kiểu. Một cơ chế binding tốt có thể làm bước này theo contract của DSL, nhưng không có bảo đảm chung rằng mọi hệ thống sẽ tự chuyển đổi kiểu an toàn.

## 8. Type matching, subtype và polymorphism

Khi một method khai báo tham số interface:

```java
interface Event { }

final class CreatedEvent implements Event { }

final class DeletedEvent implements Event { }

void handle(Event event) { }
```

Đây là mã minh họa. `handle(Event)` vẫn là một chữ ký duy nhất, nhưng mỗi lượt gọi có thể truyền loại event khác nhau.

Những matcher có ý nghĩa riêng:

```java
value instanceof CreatedEvent
value instanceof Event
value != null && value.getClass() == CreatedEvent.class
```

- `instanceof CreatedEvent` nhận object của loại đó và subtype tương thích (nếu có).
- `instanceof Event` có thể nhận nhiều implementation khác nhau.
- So class chính xác bằng `getClass() == ...` không nhận subtype và cần kiểm tra null trước.

Nếu loại `CreatedEvent` là `final`, nó không có subclass thông thường; ví dụ vẫn giúp làm rõ ý nghĩa khác nhau của **exact type** và **assignable type**.

### Generics và erasure

Giả sử:

```java
void handle(java.util.List<String> messages)
```

Ở runtime, phần generic `String` thường bị xóa theo **type erasure**. Một object `ArrayList<String>` không có runtime class duy nhất là “ArrayList of String” để kiểm tra bằng `instanceof List<String>`; phép `instanceof` trên generic parameterized type như vậy không hợp lệ trong Java. Runtime có thể kiểm tra `value instanceof java.util.List<?>`, nhưng điều đó **không chứng minh mọi phần tử là String**.

Nếu chính sách phụ thuộc nội dung phần tử, phải kiểm tra dữ liệu theo một contract phù hợp; không sử dụng cast unchecked rồi giả định kiểu generic được JVM kiểm tra đầy đủ. Việc duyệt phần tử chỉ để chọn pointcut cũng có thể tạo overhead, chạm dữ liệu nhạy cảm hoặc gặp collection bị sửa đồng thời.

### Bridge methods và method metadata

Khi generic method được override sau erasure, compiler có thể sinh **bridge method**. Công cụ AOP dựa trên bytecode/reflection có thể thấy nhiều method đại diện cho một ý định nghiệp vụ. Các API `Method.isBridge()` và `Method.isSynthetic()` có thể hỗ trợ phân tích, nhưng việc hệ thống AOP loại/gộp chúng như thế nào không có quy tắc phổ quát. Cần kiểm thử thực tế để tránh advice chạy trùng hoặc thiếu.

## 9. Value predicate cần đặc biệt cẩn thận với trạng thái thay đổi

Các kiểu bất biến như `String` tương đối dễ xử lý. Nhưng nếu đối số là một object mutable, giá trị dùng để xét khớp **có thể thay đổi** trước khi nghiệp vụ sử dụng nó.

Ví dụ:

```java
// Pseudocode minh họa, không phải chương trình Java độc lập.
if (request.amount > threshold) {
    runSpecialAdvice();
}
proceedWithTarget(request);
```

Nếu một thread khác thay đổi `request.amount` giữa lúc advice kiểm tra và lúc target xử lý, hai bên có thể dùng giá trị khác nhau. Đây là vấn đề **time-of-check/time-of-use (TOCTOU)**.

Một số cách thiết kế an toàn hơn:

1. Sử dụng đối tượng yêu cầu bất biến hoặc snapshot bất biến được chia sẻ giữa phần kiểm tra và thực thi.
2. Ràng buộc nghiệp vụ cốt lõi phải được xác nhận tại tầng thực thi đáng tin cậy.
3. Nếu cần tính nguyên tử, dùng chiến lược đồng bộ/giao dịch phù hợp chứ không dựa vào “đã kiểm tra một lần trong aspect”.
4. Tránh predicate đọc các nguồn dữ liệu thay đổi ngoài ý muốn hoặc tạo tác dụng phụ.

**Pointcut theo giá trị chỉ mô tả điều kiện áp dụng tại một thời điểm.** Nó không tự tạo khóa, snapshot hay giao dịch.

## 10. Điểm khác nhau giữa method call và method execution

Một loại pointcut **method call** có thể quan sát thông tin ở vị trí caller khi một lời gọi được phát ra; **method execution** gắn với ranh giới phương thức đích thật sự bắt đầu chạy.

Nếu dùng điều kiện runtime argument, phải biết engine có cho phép truy cập/bind đối số tại loại join point đó không. Thứ tự đánh giá đối số, receiver và thời điểm chạy advice có thể khác nhau theo ngôn ngữ và cơ chế weaving.

Một số tình huống:

- **Đối số ném lỗi khi đang được tính:** thân target có thể chưa được vào; execution advice của target không nhất thiết chạy.
- **Receiver là null:** không có target instance để thực thi phương thức instance; không suy ra có execution join point của target.
- **Reflection/callback:** phương thức đích có thể thực thi từ đường gọi gián tiếp, không có call site `target.method()` tường minh trong source.
- **Proxy:** handler thấy lời gọi qua proxy; lời gọi trực tiếp target hoặc từ bên trong target có thể bypass.
- **Async:** method gửi tác vụ có thể trả về trước khi tác vụ kết thúc; điều kiện tại call không đồng nghĩa với điều kiện lúc task thực sự thực thi.

Không coi pointcut theo tham số là thứ tự “đánh giá trước mọi thứ” bất biến. Hãy tham khảo semantics của công cụ và kiểm thử ngoại lệ ở phía caller lẫn callee.

## 11. Điều kiện động và thứ tự advice

Giả sử có hai concern:

- `TraceAspect` áp dụng lên mọi execution của `handle(Event)`.
- `PriorityAspect` chỉ áp dụng nếu đối số thỏa predicate về loại hoặc giá trị.

Nếu engine cho phép cả hai, có thể tồn tại nhiều chuỗi hành vi khác nhau:

```text
Trace.before
  Priority.before (nếu match)
    Target.handle(...)
  Priority.after
Trace.after
```

hoặc một thứ tự khác, tùy **precedence**. Nếu `Trace` là lớp ngoài, metric của trace có thể bao gồm overhead từ priority advice; nếu đổi thứ tự, phạm vi đo thay đổi.

**Pointcut quyết định nơi advice được áp dụng; precedence quyết định thứ tự khi nhiều advice cùng áp dụng.** Với around advice, việc có thực hiện hành vi gốc hay không phụ thuộc việc gọi tiếp theo contract. Một advice có thể bỏ qua hoặc gọi lại thao tác nếu cơ chế hỗ trợ; quyết định ấy phải được quản lý cẩn thận, nhất là với thao tác tạo tác dụng phụ.

Điều kiện động cũng có thể được đánh giá **nhiều lần** nếu chính sách hoặc cơ chế gọi lại đoạn xử lý. Vì vậy một predicate nên **không tạo side effect**, có kết quả dễ hiểu trong một trạng thái đã xác định và không thực hiện I/O nặng chỉ để xét khớp.

## 12. Hiệu năng của kiểm tra theo kiểu và theo giá trị

Bộ lọc tĩnh theo tên/kiểu/chữ ký có thể được phân tích hoặc tối ưu từ metadata. Điều kiện động theo đối số cần thông tin runtime, vì vậy thường phải đánh giá ở những lượt gọi phù hợp. Không có một tỷ lệ overhead cố định cho mọi cơ chế.

Chi phí có thể đến từ:

- Kiểm tra runtime type, ép kiểu hoặc truy xuất các trường của object.
- Tạo object trung gian, boxing/unboxing, reflection hoặc proxy dispatch.
- Predicate phải parse dữ liệu, duyệt collection hoặc gọi hàm có tính toán lớn.
- Ghi log/metric ngay trong advice, nhất là trên đường gọi tần suất cao.
- Đồng bộ hoặc lấy ngữ cảnh chung khi nhiều luồng chạy song song.

Cách tối ưu đúng là **hạn chế số join point phải xét điều kiện động** bằng pointcut tĩnh đủ hẹp, sau đó kiểm tra type/value trên các lời gọi còn lại. Ví dụ điều kiện:

```text
method = Inbox.accept(Object)
AND runtimeArgument[0] is String
AND runtimeArgument[0] startsWith "VIP-"
```

thường hợp lý hơn việc chạy phép đọc/parse payload VIP trên **mọi method của chương trình**. Nhưng thứ tự tối ưu thật sự và khả năng cache do engine quyết định; không khẳng định engine nào cũng tự áp dụng thứ tự lọc đó.

## 13. Những sai lầm về kiểu và giá trị tham số

### 13.1. Coi overload resolution là dispatch theo runtime value

Trong Java, `accept("VIP")` và `accept((Object) "VIP")` có thể chọn hai overload khác nhau. Việc object runtime là `String` không thay thế quyết định overload compile time. Phải phân biệt **static signature** và **runtime object type**.

### 13.2. Kiểm tra `getClass()` khi giá trị null

`null` không có runtime class. Gọi `value.getClass()` sẽ ném `NullPointerException`. Dùng guard trước khi dereference; ghi rõ ý nghĩa của null trong chính sách thay vì ngầm loại bỏ nếu bài toán cần xử lý null đặc biệt.

### 13.3. Coi kiểu tham số generic là được JVM bảo đảm đầy đủ

`List<String>` và `List<Integer>` có thể cùng runtime raw class. `instanceof List<?>` chỉ xác nhận object là một danh sách, không xác nhận kiểu phần tử. Không dùng cast unchecked như bằng chứng điểm khớp đúng.

### 13.4. Đặt logic nghiệp vụ cốt lõi vào điều kiện AOP mà không kiểm soát đường bypass

Một method có thể được gọi trực tiếp vào target, qua self-invocation hoặc qua một cơ chế không được weave. Nếu điều kiện value trong aspect là lớp bảo vệ duy nhất cho bất biến nghiệp vụ/bảo mật, những đường này có thể bỏ lọt kiểm tra. Điều kiện bắt buộc nên được bảo vệ tại ranh giới có tính toàn diện.

### 13.5. Dùng predicate có side effect

Predicate ghi dữ liệu, gửi thông báo hoặc thay đổi argument trước khi trả boolean có thể khiến lời gọi không còn thuần là “chọn điểm”. Khi quy tắc hoặc advice bị đánh giá lại, tác dụng phụ có thể lặp. Nên giữ predicate là kiểm tra nhẹ và dễ dự đoán.

### 13.6. Không kiểm tra concurrency

Một aspect giữ “argument hiện tại” trong trường mutable của instance dùng chung có thể trộn dữ liệu giữa các thread. Dữ liệu theo lượt gọi nên đi qua biến cục bộ, đối số đã bind hoặc cơ chế context propagation phù hợp. Đặc biệt khi async, không mặc định dữ liệu gắn với thread của caller được chuyển sang task khác.

### 13.7. Ghi toàn bộ argument vào log

Pointcut theo giá trị thường làm người viết advice muốn in trực tiếp payload để debug. Đối số có thể chứa token, mật khẩu, thông tin cá nhân hoặc dữ liệu không cần thiết. Nên tạo field audit/metric có kiểm soát, tránh chuỗi nhãn với cardinality vô hạn, và kiểm tra chính sách bảo mật dữ liệu.

### 13.8. Đánh đồng “advice không in log” với “pointcut không khớp”

Trong cơ chế pointcut rộng + guard, advice có thể **đã chạy** rồi quyết định không in. Nếu chỉ dựa vào stdout để kết luận pointcut có khớp hay không, rất dễ nhầm. Kiểm thử nên phân biệt: matcher/engine có chọn join point, handler có vào, guard có đúng, và hành vi bổ sung có xảy ra.

## 14. Kiểm thử điểm can thiệp có điều kiện

Bộ test tốt cần bao phủ **kiểu khai báo**, **kiểu runtime**, **giá trị** và **đường gọi**.

| Tình huống | Điều cần xác minh |
| --- | --- |
| `accept(Object)` nhận `String` VIP | Bộ lọc tĩnh đúng; type match; value match. |
| `accept(Object)` nhận `String` thường | Type match; value không match. |
| `accept(Object)` nhận `Integer` | Overload đúng; runtime type không match. |
| `accept(Object)` nhận `null` | Không dereference null; semantics đúng contract. |
| `accept(String)` nhận chuỗi VIP | Có phân biệt overload theo chữ ký khai báo. |
| `check(Object)` nhận chuỗi VIP | Tên method khác nên không khớp static filter. |
| Gọi trực tiếp target | Có thể bypass proxy; không khẳng định policy được áp dụng. |
| Object mutable đổi trạng thái | Predicate và target có dùng cùng snapshot/ngữ nghĩa không? |
| Nhiều luồng gọi đồng thời | Không lẫn dữ liệu giữa các invocation. |
| Target ném ngoại lệ | Handler không nuốt lỗi trái contract. |
| Advice cùng khớp | Thứ tự, số lần gọi tiếp và dữ liệu context chính xác. |
| Reflection hoặc method kế thừa | Có bao phủ theo cơ chế weaving thực tế không? |

### Tách ba mức kiểm thử

1. **Unit test matcher**: cho `Method` và argument fixture, kiểm từng predicate tĩnh/động độc lập.
2. **Integration test interception/weaving**: gọi qua cơ chế AOP đang dùng và xác nhận advice có/không chạy đúng ở những đường gọi quan trọng.
3. **Behavioral test**: xác nhận giá trị trả về, lỗi, số lần thực thi target và chính sách dữ liệu vẫn đúng.

Một test khớp thành công không đủ; cần test cả **false positive** (chạy ở điểm không mong muốn) lẫn **false negative** (bỏ lọt điểm cần áp dụng).

## 15. Khi nào nên chọn theo tham số?

**Chọn theo kiểu runtime** khi một contract rộng nhận nhiều subtype nhưng concern chỉ dành cho một nhóm subtype, và engine có khả năng quan sát/bind argument đúng. Chẳng hạn các handler chung nhận interface event, nhưng metric cần tách một loại event quan trọng.

**Chọn theo giá trị** khi hành vi bổ sung thực sự chỉ cần ở một trạng thái/nhóm dữ liệu nhất định, điều kiện ổn định và chi phí kiểm tra hợp lý. Ví dụ đặt nhãn tracing cho yêu cầu ưu tiên, có giới hạn quyền riêng tư và cardinality phù hợp.

**Không nên ép AOP làm mọi thứ** nếu điều kiện là quy tắc miền nghiệp vụ phức tạp quyết định kết quả chính, cần giao dịch/khóa/snapshot hoặc phải luôn được thấy tại call site. Khi đó, một hàm nghiệp vụ tường minh, thành phần policy hoặc validation ở boundary đáng tin cậy thường dễ kiểm soát hơn. AOP có thể tiếp tục dùng cho việc quan sát/audit xuyên suốt quanh logic ấy.

### Checklist thiết kế

- **Join point:** Call, execution hay điểm nào được engine hỗ trợ?
- **Static filter:** Đã thu hẹp bằng type/name/signature đủ chính xác chưa?
- **Runtime type:** Match exact class, subtype hay interface? Xử lý null ra sao?
- **Value:** Điều kiện có thuần, rẻ và có kết quả ổn định theo thời gian không?
- **Binding:** Index tham số, kiểu ép và vùng đời dữ liệu có chính xác không?
- **Semantics:** Advice chỉ quan sát hay có thể thay đổi luồng, kết quả hoặc ngoại lệ?
- **Coverage:** Có đường gọi trực tiếp, nội bộ, reflection hoặc async bỏ qua advice không?
- **Privacy:** Có in hoặc lưu thông tin đối số không cần thiết không?
- **Tests:** Có test cả trường hợp khớp, không khớp, lỗi, null và chạy đồng thời không?

## 16. Bài tập vận dụng

1. Trong `RuntimeArgumentPointcutDemo.java`, đổi điều kiện giá trị từ `startsWith("VIP-")` sang `endsWith("-42")`. Dự đoán chính xác các dòng `[value]` và chạy xác minh.
2. Sửa `matchesDeclaredMethod` để chọn `accept(String)` thay cho `accept(Object)`. Vì sao `accept("VIP-99")` bắt đầu có log, còn `accept((Object) "VIP-42")` thì không?
3. Thêm một overload `accept(Integer)`. Phân biệt `accept(17)` với `accept((Object) 17)` bằng overload resolution và runtime type.
4. Thay `instanceof String` bằng một điều kiện kiểu chính xác dùng `getClass()` và kiểm tra null trước. Trong ví dụ này kết quả có đổi không?
5. Viết một matcher cho `process(Number)` chỉ ghi metric khi runtime object là `Integer` và giá trị dương. Nếu đối số là `Long` cùng giá trị, nên khớp hay không? Nêu contract rõ ràng.
6. Tạo một class yêu cầu có trường `priority` thay đổi được. Giải thích nguy cơ khi priority bị sửa giữa lúc pointcut xét khớp và lúc target xử lý.
7. Bổ sung test gọi trực tiếp target để chứng minh handler proxy không quan sát được đường gọi đó.
8. Tạo một predicate có counter ghi số lần đánh giá, chạy nhiều lượt gọi và giải thích vì sao counter đó không nhất thiết bằng số lần target thực thi trong mọi hệ thống AOP.
9. Xây bảng có đủ ba trục: declared type, runtime type và actual value. Viết tối thiểu hai ca khớp và bốn ca không khớp cho một chính sách ưu tiên cụ thể.
10. Đề xuất cách đo thời gian hoàn tất của tác vụ async nếu một method chỉ trả về `CompletableFuture` ngay sau khi gửi tác vụ; nêu điểm nào cần quan sát thêm ngoài thời điểm call.

**Tự kiểm tra:** Runtime `String` có biến `accept(Object)` thành `accept(String)` không? Với `null`, có thể gọi `getClass()` để match type không? `instanceof List<?>` có chứng minh phần tử đều là `String` không? Vì sao matcher theo giá trị có thể thay đổi kết quả giữa hai lượt gọi cùng phương thức? Nếu target được gọi mà không đi qua proxy, chính sách advice có chắc chắn chạy không?

## Kết luận

Pointcut theo tham số mở rộng khả năng chọn điểm từ metadata **tĩnh** của phương thức sang **ngữ cảnh từng lượt gọi**. Cần phân biệt **kiểu tham số khai báo**, **kiểu đối tượng runtime** và **giá trị thực tế**; xác nhận engine có hỗ trợ binding hoặc điều kiện động phù hợp; xử lý đúng overload, null, generics, mutability và concurrency. Một thiết kế tốt thường **thu hẹp tập join point bằng điều kiện tĩnh trước**, rồi đánh giá điều kiện runtime ở phạm vi cần thiết, đồng thời kiểm thử đường can thiệp thật và giữ các bất biến quan trọng tại ranh giới đáng tin cậy.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contextual-pointcuts-003">Chọn theo annotation và siêu dữ liệu của chương trình</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn theo annotation và siêu dữ liệu của chương trình

## Mục tiêu bài học

Một **pointcut** trong Aspect-Oriented Programming (AOP) xác định những **join point** mà advice sẽ áp dụng. Ngoài lựa chọn theo tên, chữ ký, kiểu hoặc vị trí trong chương trình, nhiều cơ chế AOP cho phép chọn dựa trên **annotation** và các dạng **siêu dữ liệu (metadata)** khác. Đây là cách mô tả ý nghĩa của điểm can thiệp mà không phải gắn chặt pointcut với tên hàm.

Ví dụ, thay vì viết “chọn mọi phương thức có tên bắt đầu bằng `process`”, một nhóm phát triển có thể đặt marker `@Observed` lên những thao tác cần đo thời gian. Một pointcut thích hợp sau đó chọn những join point có metadata tương ứng.

Bài học giúp bạn:

1. Phân biệt **annotation là dữ liệu mô tả** với **pointcut là quy tắc chọn** và **advice là hành vi được thực thi**.
2. Hiểu metadata có thể nằm trên **lớp, interface, phương thức, tham số, trường, constructor, annotation khác** hoặc đến từ hệ thống build/runtime.
3. Xác định vai trò của `@Target`, `@Retention`, `@Inherited`, `@Repeatable`, quan hệ kế thừa và cơ chế reflection trong Java.
4. Thiết kế biểu thức ghép giữa metadata với tên, kiểu, phạm vi chương trình và thông tin runtime mà vẫn kiểm soát được tập join point.
5. Phân biệt **metadata ở declaration nào** với **matcher đang đọc declaration nào**, nhất là giữa interface và implementation, phương thức override hoặc method bridge.
6. Hiểu những khác biệt giữa **matching tĩnh**, **matching động**, **proxy** và **weaving**, cùng cách kiểm thử phần được chọn và phần phải loại trừ.

Đây là kiến thức về **AOP nói chung**. Các ví dụ Java 17 dùng annotation và reflection để minh họa cơ chế; không có một cú pháp pointcut áp dụng được cho mọi công cụ AOP.

## 1. Khái niệm nền tảng: ba vai trò không được đánh đồng

| Thành phần | Câu hỏi trả lời | Minh họa |
| --- | --- | --- |
| **Annotation / metadata** | Một phần của chương trình được mô tả bằng thông tin gì? | Phương thức `submit` được đánh dấu `@Observed`. |
| **Join point** | Sự kiện can thiệp có thể xuất hiện ở đâu trong luồng chạy? | Một lần thực thi hoặc một lần gọi `submit`, tùy mô hình AOP. |
| **Pointcut** | Trong các join point khả dụng, chọn những điểm nào? | Chỉ chọn execution của phương thức có marker `@Observed`. |
| **Advice** | Khi điểm được chọn xảy ra thì làm gì? | Đo latency, thêm correlation ID, ghi kết quả. |
| **Weaving / interception** | Làm sao advice thực sự được kết hợp với chương trình? | Proxy, biến đổi mã, instrumentation hoặc một cơ chế khác. |

**Annotation không tự thực thi hành vi.** Trong Java, khai báo:

```java
@Observed
void submit() {
    // logic
}
```

không tự tạo log, đo thời gian hay đổi luồng chạy. Phải có **thành phần đọc marker**, quy tắc chọn điểm và cơ chế thực thi advice. Các công cụ có thể đánh giá annotation khi build, khi nạp lớp hoặc khi runtime; metadata chỉ tạo **cơ sở lựa chọn**.

Nói cách khác:

```text
metadata trên mã
       |
       v
pointcut đọc/diễn giải metadata
       |
       v
lọc join point trong tập được công cụ hỗ trợ
       |
       v
cơ chế AOP kích hoạt advice ở lượt thực thi phù hợp
```

Không có bước nào tự bảo đảm các bước còn lại. Ví dụ, matcher có thể chọn đúng phương thức theo marker nhưng lời gọi đi thẳng vào target và bỏ qua proxy nên không có advice.

## 2. “Siêu dữ liệu của chương trình” rộng hơn một annotation

**Metadata** là thông tin mô tả chương trình hoặc phần tử của chương trình, không nhất thiết là logic trực tiếp xử lý nghiệp vụ. Trong nhiều ngôn ngữ, metadata có thể bao gồm:

- **Annotation / attribute / decorator metadata:** marker, tên chính sách, mức độ quan sát, nhãn kỹ thuật.
- **Kiểu và chữ ký:** tên lớp, interface đã cài đặt, tên method, kiểu tham số và return, visibility, modifier.
- **Cấu trúc sở hữu:** package, namespace, module, kiểu khai báo, quan hệ kế thừa và override.
- **Thông tin build hoặc bytecode:** annotation, cờ compiler, mã sinh, các metadata có thể xuất hiện ở tầng biên dịch nhưng không có ở runtime.
- **Quy ước hoặc registry bên ngoài:** cấu hình được gắn tới các loại và phương thức; có thể được engine AOP nạp thành một tập quy tắc.

Không phải mọi thứ ở trên là *annotation*, và cũng không phải công cụ AOP nào cho phép dùng tất cả làm điều kiện pointcut. Vì vậy cần xác định chính xác **nguồn metadata**, **vị trí lưu**, **thời điểm nó còn tồn tại** và **cách matcher truy cập**.

### 2.1. Marker và annotation có thuộc tính

Một marker rỗng chỉ biểu thị có/không:

```java
@interface Observed {}
```

Một annotation có thuộc tính mang thêm dữ liệu:

```java
@interface Observed {
    String operation();
    int sampleRate() default 100;
}
```

Ví dụ `@Observed(operation = "checkout", sampleRate = 10)` chứa metadata. Cơ chế AOP có thể dùng sự **hiện diện của annotation** làm điều kiện chọn, hoặc nếu được hỗ trợ thì **đọc thuộc tính** để cấu hình advice. Một số công cụ cho phép bind annotation vào tham số advice; một số đòi code tự tra cứu.

Không nên dùng thuộc tính annotation để cất thông tin nhạy cảm hoặc dữ liệu thay đổi theo từng request. Metadata khai báo thường là **cấu hình tương đối tĩnh**, còn dữ liệu request/transaction là **ngữ cảnh runtime**.

### 2.2. Quy tắc lựa chọn dựa trên ý nghĩa

Ví dụ, xét hai cách mô tả:

```text
Cách theo quy ước tên:
  methodNameStartsWith("save")

Cách theo ý nghĩa:
  methodHasAnnotation("Observed")
```

Cách thứ hai hữu ích khi method tên khác nhau nhưng cùng cần một chính sách kỹ thuật, chẳng hạn `send`, `save`, `publish`. Nó giúp người viết method **công bố điểm áp dụng có chủ đích**. Song marker cũng tạo trách nhiệm: ai phải gắn marker, việc bỏ marker có được review không, và quy tắc kế thừa có khiến một method không mong đợi bị chọn hay không?

### 2.3. Annotation không phải bất biến nghiệp vụ

Gắn `@RequireOwnership` lên một method không tự chứng minh tất cả đường cập nhật dữ liệu đều được bảo vệ. Nếu kiểm tra quyền phụ thuộc chủ sở hữu tài liệu, trạng thái bản ghi hoặc luồng nhiều bước, bất biến cần được đảm bảo ở biên tin cậy và logic nghiệp vụ. AOP có thể giúp áp một chính sách kỹ thuật đã định nghĩa rõ, nhưng metadata + pointcut có thể không bao phủ mọi đường gọi.

## 3. Annotation trong Java: Target, Retention và phạm vi khai báo

Trên Java, `@interface` khai báo một annotation type. Ba nhóm câu hỏi cần tách biệt:

1. **Annotation được phép đặt ở đâu?** Do `@Target` quy định nếu có.
2. **Annotation tồn tại ở giai đoạn nào?** Do `@Retention` quy định.
3. **Công cụ AOP kiểm tra annotation ở declaration nào và tại thời điểm nào?** Do engine/matcher và đường chạy quyết định.

### 3.1. `@Target`: nơi annotation có thể được dùng

Ví dụ:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@interface Observed {}
```

Marker trên có thể đặt ở **type declaration** và **method declaration**. Java còn có các phần tử như `FIELD`, `PARAMETER`, `CONSTRUCTOR`, `ANNOTATION_TYPE`, `TYPE_USE`, `TYPE_PARAMETER`, `PACKAGE`, `MODULE` và `RECORD_COMPONENT` (tùy phiên bản). Được phép đặt annotation tại một phần tử **không đồng nghĩa** một engine AOP có join point tương ứng với phần tử đó.

Ví dụ, `@Target(FIELD)` cho phép đánh dấu trường, nhưng một interface dynamic proxy không tự chặn thao tác đọc/ghi field. Muốn chọn **field-access join point**, công cụ phải hỗ trợ loại điểm này cùng cách weaving thích hợp.

Nếu không khai báo `@Target`, annotation có thể được áp lên các loại declaration trong phạm vi quy tắc Java; điều đó không tạo bảo đảm về khả năng matching hoặc interception của AOP.

### 3.2. `@Retention`: SOURCE, CLASS và RUNTIME

| Chính sách | Đặc điểm | Hệ quả với pointcut |
| --- | --- | --- |
| `RetentionPolicy.SOURCE` | Metadata được dùng ở source rồi bị compiler loại khỏi class file. | Không thể dựa vào reflection runtime để đọc trực tiếp annotation này từ class đã biên dịch. Công cụ phân tích source có thể sử dụng nó trước đó. |
| `RetentionPolicy.CLASS` | Metadata được ghi trong class file, nhưng Java reflection không bắt buộc trả về annotation đó ở runtime. | Công cụ đọc/biến đổi bytecode có thể dùng; matcher chỉ dùng reflection runtime không nên trông đợi nó. |
| `RetentionPolicy.RUNTIME` | Metadata có thể được đọc qua reflection ở runtime. | Thích hợp cho ví dụ runtime proxy/reflective matcher. |

**Mặc định của annotation Java khi không có `@Retention` là `RetentionPolicy.CLASS`.** Nếu thiết kế matcher bằng `Method.getAnnotation(...)` hoặc `isAnnotationPresent(...)`, cần annotation có `RUNTIME` retention ở vị trí đang đọc. Không nên suy rằng metadata không đọc được qua reflection thì vô dụng cho **mọi** công cụ AOP: các công cụ hoạt động ở compile/load time có thể xét cấu trúc class file hoặc source.

Ví dụ marker runtime:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Traced {
    String category() default "general";
}
```

Đoạn trên là **khai báo annotation để dùng cùng mã khác**; bản thân nó không phải một chương trình độc lập có `main`.

### 3.3. Một annotation xuất hiện ở đúng vị trí chưa chắc đúng pointcut

`@Traced` trên một method không tự có nghĩa “mọi method call tương ứng được bắt”. Nếu pointcut chọn **method execution**, loại sự kiện được xét có thể khác với **method call**. Nếu công cụ chỉ chặn lời gọi qua proxy, có annotation vẫn không khiến lời gọi trực tiếp target chạy advice.

Bất kỳ kết luận nào về độ phủ đều cần xác nhận **join-point kind + vị trí metadata + matcher + đường interception**.

## 4. Khác biệt giữa annotation trên method và annotation trên type

Xét:

```java
@Observed
class ImportService {
    void importFile() { }
    void validate() { }
}
```

Có hai cách thiết kế pointcut rất khác:

- **Chọn method được annotation trực tiếp:** matcher hỏi chính declaration của `importFile` có `@Observed` không. Nếu chỉ có marker ở class, một phép tra cứu `Method.getAnnotation(Observed.class)` đơn giản thường **không** trả về marker của class.
- **Chọn method nằm trong type có annotation:** matcher hỏi *declaring type* hoặc **runtime target type** có marker `@Observed` không. Khi đó có thể chọn cả `importFile` và `validate` theo quy tắc của matcher.

Hai câu hỏi này khác nhau. Đặt annotation ở class thường mang ngữ nghĩa **một nhóm method cùng thuộc chính sách**, nhưng không mặc nhiên là hành vi của mọi engine. Cần xác nhận matcher xét type khai báo, hierarchy, interface hay runtime class.

### 4.1. Annotation trên interface và implementation

Xét:

```java
interface FileApi {
    @Traced
    void save();
}

class FileService implements FileApi {
    @Override
    public void save() {}
}
```

Trên Java reflection thông thường, `FileApi.class.getMethod("save")` và `FileService.class.getMethod("save")` là **hai đối tượng `Method` mô tả hai declaration khác nhau**. `getAnnotation(Traced.class)` trên method của implementation không tự lấy annotation đã đặt ở method interface.

Vì vậy:

- Matcher xem **interface method** có thể thấy `@Traced`.
- Matcher xem **implementation method** có thể không thấy annotation nếu chỉ đặt marker trên interface.
- Một công cụ AOP có thể chủ động duyệt interface/implementation rồi hợp nhất metadata; đó là **khả năng riêng của công cụ**, không phải mặc định của Java reflection.
- Một proxy theo interface thường nhận method mô tả lời gọi qua **interface**; một cơ chế weaving có thể dùng loại metadata khác.

**Quy tắc học tập:** Luôn hỏi “annotation đặt ở đâu?” và “pointcut đọc `Method` hoặc `Class` nào?” trước khi kết luận đã chọn hay chưa.

### 4.2. Overload, override và generic bridge method

Method `save(String)` và `save(byte[])` là hai declaration khác nhau. Đánh dấu một overload không tự đánh dấu overload còn lại.

Khi override, annotation đặt trên method ở superclass hoặc interface không tự được kế thừa theo quy tắc `@Inherited` dành cho **class-level annotation**. Một công cụ có thể định nghĩa chiến lược riêng để tìm marker trên method bị override.

Với generics và *type erasure*, Java compiler có thể tạo **bridge method** để đảm bảo dispatch. Matcher dựa vào reflection/bytecode có thể gặp method synthetic hoặc bridge. Để tránh chọn sai hoặc xử lý trùng, cần hiểu engine có bỏ qua, ánh xạ hoặc chuẩn hóa bridge method không; thử nghiệm bằng generic interface nếu phạm vi quan trọng.

## 5. `@Inherited`, `@Repeatable` và meta-annotation: các giới hạn tinh tế

### 5.1. `@Inherited` chỉ áp dụng theo quy tắc class-level

Java cung cấp `@Inherited` để biểu thị một annotation **trên class** có thể được class con kế thừa khi truy vấn annotation bằng cơ chế Java reflection phù hợp.

Ví dụ ý tưởng:

```java
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Inherited
@Retention(RetentionPolicy.RUNTIME)
@interface AuditedType {}

@AuditedType
class BaseService {}

class ChildService extends BaseService {}
```

Trong ví dụ, `ChildService.class.isAnnotationPresent(AuditedType.class)` có thể trả về `true` theo quy tắc `@Inherited`. Tuy nhiên:

- `@Inherited` **không** làm annotation trên method tự động kế thừa sang method override.
- Quy tắc kế thừa này liên quan tới **superclass**, không có nghĩa annotation ở một interface tự động thành class annotation ở lớp cài interface.
- Matcher của một công cụ AOP có thể dùng phép tìm annotation riêng khác với `Class.getAnnotation`; cần kiểm tra hợp đồng.

### 5.2. `@Repeatable`: một declaration có thể có nhiều annotation cùng loại

Java cho phép loại annotation được `@Repeatable` xuất hiện nhiều lần với một container annotation. Ví dụ một method có thể mang hai nhãn kỹ thuật khác nhau để mô tả hai nhóm quan sát.

Với reflection, `getAnnotationsByType(SomeAnnotation.class)` là API thích hợp để thu thập các annotation cùng loại được lặp theo hợp đồng của Java; chỉ dùng `getAnnotation` có thể bỏ qua cách biểu diễn qua container trong một số tình huống. Khi công cụ AOP chọn theo “có annotation” và annotation ấy repeatable, cần xác minh matcher hiểu **container và các phần tử bên trong** thế nào.

Không được suy “hai annotation cùng loại = advice chạy hai lần”: việc có nhiều marker là thông tin, còn số advice được kích hoạt phụ thuộc **pointcut, cấu hình và từng occurrence**.

### 5.3. Meta-annotation: annotation mô tả annotation khác

Một annotation có thể được gắn lên **annotation type** để thể hiện phân loại:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
@interface ObservabilityMarker {}

@ObservabilityMarker
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Counted {}
```

Method gắn `@Counted` **không mặc nhiên có trực tiếp `@ObservabilityMarker`** theo phép `method.isAnnotationPresent(ObservabilityMarker.class)`. Một matcher muốn nhận diện meta-annotation phải đọc các annotation được gắn trên method, rồi kiểm tra annotation type của chúng có marker `@ObservabilityMarker` hay không, hoặc dùng API chuyên biệt nếu công cụ cung cấp.

Một số engine AOP hỗ trợ meta-annotation/composed annotation; một số chỉ tìm annotation trực tiếp. Đây là khác biệt có ý nghĩa về **mô hình matching**, không chỉ cách đặt tên marker.

## 6. Chọn điểm theo metadata: bốn cấp độ phổ biến

Bốn kiểu lựa chọn dưới đây có thể được hỗ trợ riêng rẽ hoặc kết hợp, tùy công cụ.

### Cấp 1: Chọn theo sự hiện diện của annotation

Giả mã:

```text
methodExecution
AND methodHasAnnotation("Observed")
```

Chỉ chọn những occurrence method execution mà matcher nhận diện marker phù hợp. Với static matching, engine có thể xác định từ chữ ký/bytecode trước; với runtime matching, nó có thể tra cứu reflective metadata.

### Cấp 2: Chọn theo thuộc tính annotation

Giả mã:

```text
methodHasAnnotation("Observed")
AND annotationAttribute("category") == "io"
```

Không phải DSL nào cũng có phép so sánh thuộc tính trực tiếp. Một cách khác là bind annotation instance vào advice để policy đọc giá trị `category`. Cần phân biệt **điều kiện chọn điểm** với **dữ liệu cấu hình hành vi advice sau khi đã chọn**.

### Cấp 3: Ghép annotation với kiểu, tên và phạm vi

Giả mã:

```text
methodExecution
AND (methodHasAnnotation("Observed") OR typeHasAnnotation("MonitoredType"))
AND NOT belongsToModule("internal.instrumentation")
```

Dấu ngoặc làm rõ phạm vi loại trừ: nếu viết `A OR (B AND NOT C)` thay vì `(A OR B) AND NOT C`, một nhánh có thể bỏ qua điều kiện cấm. Khi quy tắc có **OR + NOT**, cần có negative tests cho mọi nhánh.

### Cấp 4: Kết hợp metadata tĩnh với ngữ cảnh runtime

Giả mã:

```text
methodHasAnnotation("Sampled")
AND runtimeContext.sampled == true
```

Metadata cho phép xác định ứng viên; điều kiện runtime có thể thay đổi theo từng lần gọi. Một method được đánh dấu giống nhau nhưng có thể được advice chọn ở lượt này và không chọn ở lượt khác. Không phải mọi công cụ cho phép matcher đọc runtime context hoặc bind tham số; không nên coi giả mã như cú pháp chung.

### 6.1. Cần tách “đánh dấu để chọn” và “đánh dấu để cấu hình”

Nếu `@Observed(category = "payment")` chỉ dùng để **chọn** điểm, policy có thể coi mọi method có annotation giống nhau. Nếu category dùng để **định tuyến số liệu** sau khi advice chạy, annotation trở thành dữ liệu đầu vào của advice. Nếu category quyết định **có chạy advice hay không**, matcher cần năng lực so sánh giá trị.

Cùng một annotation có thể đóng nhiều vai trò, nhưng viết tài liệu phải nêu rõ vai trò nào đang được dùng để tránh hiểu nhầm.

## 7. Ví dụ Java 17 chạy được: chọn theo marker và loại trừ metadata

Ví dụ này dùng **JDK dynamic proxy** để minh họa một phần ý tưởng AOP. Handler đọc annotation ở **interface method**, chọn theo công thức:

```text
hasAnnotation(Tracked)
AND NOT hasAnnotation(Internal)
```

Nếu được chọn, handler ghi thông báo trước/sau hoặc khi target ném exception. Đây là ví dụ runnable bằng Java chuẩn để học **cách matcher dùng metadata**, không phải một AOP engine hoàn chỉnh.

Tạo tệp `AnnotationPointcutDemo.java`:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class AnnotationPointcutDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Tracked {
        String category();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Internal {}

    interface TaskApi {
        @Tracked(category = "orders")
        String submit(String id);

        @Tracked(category = "health")
        @Internal
        String sync();

        String status();
    }

    static final class TaskService implements TaskApi {
        @Override
        public String submit(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id required");
            }
            System.out.println("TARGET submit " + id);
            return "ACCEPTED " + id;
        }

        @Override
        public String sync() {
            System.out.println("TARGET sync");
            return "SYNCED";
        }

        @Override
        public String status() {
            System.out.println("TARGET status");
            return "READY";
        }
    }

    static TaskApi observed(TaskApi target) {
        return (TaskApi) Proxy.newProxyInstance(
                TaskApi.class.getClassLoader(),
                new Class<?>[] { TaskApi.class },
                (proxy, method, arguments) -> {
                    Tracked tracked = method.getAnnotation(Tracked.class);
                    boolean selected = tracked != null
                            && !method.isAnnotationPresent(Internal.class);

                    if (selected) {
                        System.out.println("ADVICE before "
                                + tracked.category() + ":" + method.getName());
                    }

                    try {
                        Object result = method.invoke(target, arguments);
                        if (selected) {
                            System.out.println("ADVICE success " + method.getName());
                        }
                        return result;
                    } catch (InvocationTargetException ex) {
                        if (selected) {
                            System.out.println("ADVICE failure " + method.getName());
                        }
                        throw ex.getCause();
                    }
                }
        );
    }

    public static void main(String[] args) {
        TaskApi target = new TaskService();
        TaskApi proxy = observed(target);

        System.out.println("RESULT " + proxy.submit("A-7"));
        System.out.println("RESULT " + proxy.sync());
        System.out.println("RESULT " + proxy.status());

        try {
            proxy.submit("");
        } catch (IllegalArgumentException ex) {
            System.out.println("CALLER caught " + ex.getMessage());
        }

        System.out.println("RESULT " + target.submit("DIRECT"));
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac AnnotationPointcutDemo.java
java AnnotationPointcutDemo
```

Stdout theo đúng thứ tự:

```text
ADVICE before orders:submit
TARGET submit A-7
ADVICE success submit
RESULT ACCEPTED A-7
TARGET sync
RESULT SYNCED
TARGET status
RESULT READY
ADVICE before orders:submit
ADVICE failure submit
CALLER caught id required
TARGET submit DIRECT
RESULT ACCEPTED DIRECT
```

### 7.1. Bảng đối chiếu tập được chọn

| Method trên interface | `@Tracked`? | `@Internal`? | Có advice khi gọi qua proxy? |
| --- | --- | --- | --- |
| `submit` | Có; category = `orders`. | Không. | **Có**, cả success và failure. |
| `sync` | Có; category = `health`. | Có. | **Không**, vì điều kiện NOT loại bỏ. |
| `status` | Không. | Không. | **Không**, vì thiếu điều kiện chọn dương. |

Lời gọi `target.submit("DIRECT")` không xuất hiện thông báo advice vì **đi thẳng vào target**, không thông qua proxy. Đây là giới hạn của cơ chế JDK proxy trong ví dụ, không phải mọi kiểu weaving.

### 7.2. Vì sao xử lý exception như vậy?

`Method.invoke(target, arguments)` bọc exception do target ném vào `InvocationTargetException`. Handler ghi sự kiện thất bại và `throw ex.getCause()` để caller nhận `IllegalArgumentException` gốc. Nếu handler nuốt lỗi rồi trả mặc định, hợp đồng method bị thay đổi ngoài ý định.

Nếu chính **advice ghi log** ném exception, ví dụ chưa đặt chính sách xử lý riêng. Trong hệ thống thật phải quyết định logging/metrics lỗi thì nghiệp vụ có tiếp tục không; điều này phụ thuộc mức độ bắt buộc của chính sách.

### 7.3. Vì sao `@Internal` cần đọc ở cùng nơi với `@Tracked`?

Trong demo, cả hai marker được đặt ở **method interface** và handler dùng `Method` của interface. Nếu chỉ chuyển `@Internal` xuống implementation, handler có thể không thấy điều kiện loại trừ và vô tình chạy advice cho `sync`. Đây là một ví dụ thực tế về lỗi **metadata khác declaration mà matcher đang đọc**.

### 7.4. Bài tập thay đổi chính xác một yếu tố

1. Bỏ `@Internal` ở `TaskApi.sync`: lời gọi qua proxy bắt đầu phát thông báo advice theo category `health`.
2. Chuyển `@Tracked` của `TaskApi.submit` sang `TaskService.submit` nhưng **giữ nguyên handler**: matcher kiểm tra method interface không thấy marker nữa, vì vậy không áp dụng advice cho `submit`.
3. Đổi `@Retention(RetentionPolicy.RUNTIME)` thành `CLASS` cho `Tracked`: matcher dùng reflection không nhận được annotation theo cách hiện tại.
4. Giữ nguyên annotation nhưng gọi trực tiếp `target.submit("X")`: target chạy, proxy handler không chạy.
5. Thêm một method overload ở interface và chỉ gắn marker cho một overload: dự đoán overload nào được chọn.

Mỗi thay đổi cần được kiểm tra cùng **expected stdout** và ghi rõ điều kiện nào trong pointcut đã đổi.

## 8. Phân biệt metadata tĩnh và dữ liệu thực thi

Metadata như annotation thường được cố định theo artifact chương trình. Một join point khi runtime xảy ra lại có thông tin biến đổi:

- **Receiver:** object thực hiện method thực sự có kiểu nào?
- **Arguments:** đối số hiện tại có giá trị gì?
- **Result/exception:** method trả về bình thường hay ném lỗi?
- **Thread/context:** đang ở tác vụ hoặc correlation context nào?
- **Thời điểm:** lần gọi hiện tại diễn ra trước/sau sự kiện nào?

Một pointcut dùng annotation và một điểm dựa vào tham số có thể biểu diễn hai tầng:

```text
STATIC:  methodHasAnnotation("Traced")
DYNAMIC: argument("important") == true

SELECT: STATIC AND DYNAMIC
```

Ở một cơ chế có hỗ trợ matching động, một method có marker được gọi mười lần, chỉ bốn lần thỏa `important=true`, thì advice có thể chạy ở bốn occurrence (giả sử không có các giới hạn khác). Không dùng số method có marker để suy ra số lời gọi advice.

**Lưu ý về metadata sửa được lúc chạy:** Một số ngôn ngữ hỗ trợ metadata động hoặc monkey-patching; trên JVM có cơ chế instrumentation và class loading riêng. Không nên áp trực giác của một ngôn ngữ lên ngôn ngữ khác. Với Java annotation runtime, nên coi metadata declaration là thông tin ổn định của class đang được nạp, trừ khi có cơ chế biến đổi được xác định rõ.

## 9. Cùng một annotation, nhiều join-point model khác nhau

Metadata có thể giống nhau nhưng pointcut chọn **loại sự kiện** khác nhau:

| Loại cần chọn | Điều cần xác minh |
| --- | --- |
| **Method execution** | Engine có nhận diện method implementation được thực thi? Nó đọc metadata ở interface, override hay implementation? |
| **Method call** | Engine có hỗ trợ điểm ở phía caller không? Metadata của method được gọi lấy theo kiểu tĩnh hay target runtime? |
| **Constructor execution/call** | Công cụ có hỗ trợ join point constructor không? Annotation ở constructor được đọc theo cách nào? |
| **Field get/set** | Công cụ có quan sát field access hay chỉ method wrapper? Annotation `@Target(FIELD)` không đủ để tạo hook. |
| **Type initialization** | Công cụ có hỗ trợ loại điểm này không? Metadata type được xác định tại thời điểm nào? |
| **Runtime proxy invocation** | Caller có đi qua proxy thật không? Proxy đọc `Method` nào? Lời gọi nội bộ có bị bỏ qua? |

Không có “cú pháp annotation pointcut” duy nhất đảm bảo chọn tất cả loại điểm. Trước khi viết matcher, phải chọn **join point kind** phù hợp với chính sách. Ví dụ: “theo dõi mọi lần lưu dữ liệu” cần xem việc đặt hook ở method execution của tầng lưu trữ có bao phủ được mọi đường gọi hay không; một marker trên interface đơn thuần không giải quyết mọi vấn đề độ phủ.

## 10. Vị trí và mức độ kế thừa của metadata

Để kiểm thử một pointcut theo annotation, hãy lập một **ma trận declaration** thay vì chỉ thử một method đơn giản:

| Trường hợp | Câu hỏi quan trọng |
| --- | --- |
| Annotation trên interface method | Matcher có duyệt interface method không? |
| Annotation trên implementation method | Matcher có duyệt method thực thi hay chỉ method interface không? |
| Annotation trên type | Pointcut đang chọn type annotation hay method annotation? |
| Superclass có `@Inherited` trên type | Matcher có tuân quy tắc Java reflection hoặc chiến lược kế thừa khác không? |
| Override method | Annotation ở method gốc có được matcher tìm thấy theo chính sách riêng không? |
| Overload cùng tên | Annotation gắn chính xác cho chữ ký nào? |
| Method generic/bridge | Công cụ có normalize bridge/synthetic và không xử lý trùng không? |
| Repeatable annotation | Matcher hiểu annotation trực tiếp và container ra sao? |
| Meta-annotation | Matcher có duyệt annotation type hay chỉ annotation trực tiếp? |
| Annotation giữ ở `CLASS`, không phải `RUNTIME` | Công cụ dùng bytecode matcher hay reflection runtime? |

Không có một đáp án “có/không” cho mọi hàng trên mọi công cụ. Thay vì đoán, hãy tạo class/method thử, quan sát **positive matching**, **negative matching** và đường gọi thực tế.

### Ví dụ về “cùng tên” nhưng metadata khác nhau

Giả sử interface có `process(String)`, implementation override và class khác có method trùng tên. Một pointcut “method có tên process” có thể chọn cả ba theo phạm vi rộng, trong khi marker đặt ở **một declaration duy nhất**. Metadata giúp chỉ định **ý nghĩa**, nhưng vẫn phải xác định engine giải quyết quan hệ interface/implementation như thế nào.

## 11. Thiết kế pointcut ghép với annotation mà vẫn kiểm soát phạm vi

Một bài toán thực tế: đo latency cho thao tác của nhóm I/O được đánh dấu hoặc thuộc một interface quan sát, nhưng phải bỏ qua module chứa bộ đo.

Viết bằng lời:

> Chọn method execution trong biên I/O, khi method có marker `@Observed` **hoặc** type có marker `@MeasuredType`, **và không** thuộc module instrumentation.

Giả mã:

```text
execution(method in IO boundary)
AND (
    methodHasAnnotation("Observed")
    OR targetTypeHasAnnotation("MeasuredType")
)
AND NOT belongsToModule("instrumentation")
```

Phải xác định:

- **`execution`:** Công cụ có method-execution join point không?
- **`methodHasAnnotation`:** Đọc marker trên method nào?
- **`targetTypeHasAnnotation`:** Là type khai báo hay runtime target type? Có xét superclass, interface không?
- **`NOT instrumentation`:** Loại trừ có bao phủ toàn bộ nhánh OR chưa?
- **`IO boundary`:** Là phạm vi module/package/class nào, có thay đổi theo refactor không?
- **Kết quả:** Advice chạy mỗi occurrence thỏa rule hay chỉ ở biên proxy?

### 11.1. Điều kiện loại trừ cần được áp cho toàn bộ nhánh OR

Khác biệt:

```text
(A OR B) AND NOT C       // C loại khỏi cả A và B
A OR (B AND NOT C)       // nhánh A vẫn được chọn dù C đúng
```

Nếu C biểu thị mã logging nội bộ, biểu thức thứ hai có thể khiến logging gọi lại chính aspect và phát sinh recursion. Dùng dấu ngoặc rõ ràng và kiểm thử method vừa thỏa A vừa thỏa C.

### 11.2. Binding annotation ở những nhánh khác nhau

Nếu một số công cụ cho phép advice nhận đối tượng annotation qua biến binding, thì biểu thức OR gồm:

```text
hasAnnotation(Tracked, bind annotation as tag)
OR typeMatches(ObservedType)
```

có thể tạo trường hợp nhánh thứ hai khớp nhưng **không có giá trị `tag`**. Engine có thể từ chối, yêu cầu mọi nhánh bind nhất quán hoặc định nghĩa quy tắc riêng. Đừng coi phép OR Boolean là đủ chứng minh binding hợp lệ. Có thể tách thành hai pointcut/advice hoặc thiết kế nguồn cấu hình chung rõ ràng.

## 12. Trường hợp nên và không nên dùng annotation-based pointcut

### Nên cân nhắc

- Một nhóm method hoặc type cần cùng **chính sách quan sát kỹ thuật**, không phụ thuộc chi tiết nghiệp vụ.
- Các nhóm tên khác nhau nhưng chung **ngữ nghĩa chọn**, chẳng hạn cần trace hoặc ghi chỉ số.
- Muốn nhà phát triển nhìn ngay ở declaration biết một chính sách sẽ được áp dụng.
- Tập điểm chọn cần được quản lý qua review và test rõ ràng; annotation có chủ sở hữu và quy ước sử dụng.
- Cơ chế interception/weaving đã được chứng minh bao phủ đường chạy dự kiến.

### Cần cân nhắc phương án khác

- Quyết định giá, trạng thái đơn hàng, quyền sở hữu dữ liệu hoặc quy tắc domain quan trọng: nên biểu diễn bằng domain service/policy hoặc workflow tường minh.
- Chính sách bắt buộc không được bỏ sót bất kỳ đường truy cập nào, nhưng chỉ dựa vào marker do con người nhớ gắn: nên có biên bảo vệ tin cậy và kiểm soát invariant thực tế.
- Một vài method cần xử lý khác nhau về thứ tự hoặc ngữ nghĩa: lời gọi trực tiếp, decorator hay interceptor tại biên rõ có thể dễ bảo trì hơn.
- Marker cần lưu dữ liệu thay đổi theo từng người dùng hoặc request: dùng runtime context phù hợp thay vì cố ép giá trị động vào annotation declaration.
- Mỗi loại method cần nhiều ngoại lệ riêng khiến pointcut trở nên dài và khó kiểm thử: rà soát lại ranh giới trách nhiệm và cách phân nhóm concern.

**Tiêu chí quan trọng:** Một annotation-based pointcut tốt không chỉ tiện gắn marker mà phải giúp người bảo trì **trả lời được chính xác những điểm nào được chọn và vì sao**.

## 13. Vấn đề bảo trì, bảo mật và vận hành

### 13.1. Pointcut có thể im lặng mất hiệu lực sau refactor

Đổi tên package/class, tách interface, di chuyển marker hoặc chuyển một method sang đường gọi mới có thể làm matcher thay đổi tập được chọn mà logic nghiệp vụ vẫn chạy bình thường. Cần kiểm thử độ phủ và review tác động khi thay declaration/metadata.

### 13.2. Annotation không phải một hàng rào bảo mật

Nếu advice kiểm tra quyền nhưng một số đường gọi bỏ qua proxy hoặc một vài method không gắn marker, kiểm tra có thể bị bỏ qua. Ngoài việc test metadata, phải bảo vệ bất biến tại **biên tin cậy** hoặc tầng nghiệp vụ liên quan. Không dùng annotation làm bằng chứng duy nhất rằng mọi đường truy cập đã được bảo vệ.

### 13.3. Tránh pointcut quá rộng và logging thông tin nhạy cảm

Một `@Observed` áp lên cả type có thể vô tình theo dõi method truy cập dữ liệu nhạy cảm. Advice không nên tự động in toàn bộ đối số/kết quả; cần cơ chế masking, sampling và kiểm soát cardinality. Logging ở hot path có thể tác động lớn hơn overhead của chính interception.

### 13.4. Phải kiểm thử lỗi của target và lỗi của advice

Một advice có thể ghi thành công khi method return, nhưng điều đó không chứng minh transaction đã được commit hoặc sự kiện đã lưu bền vững. Nếu ghi đo lường lỗi, policy fail-open/fail-closed cần rõ. Không để exception do advice gây ra vô tình thay thế lỗi gốc khi không có chủ đích.

### 13.5. Bất đồng bộ làm thay đổi mốc quan sát

Method trả `Future`/promise có thể kết thúc lời gọi trước khi công việc thực sự hoàn thành. Annotation trên method không tự làm advice quan sát được completion của task; cần hook tại mốc phù hợp nếu mục tiêu là đo latency end-to-end. Context như correlation ID cũng không tự truyền qua mọi scheduler/thread.

## 14. Quy trình kiểm thử annotation pointcut

Đề xuất một ma trận đủ bao phủ tính năng và ranh giới:

| Ca | Input/thiết lập | Kỳ vọng |
| --- | --- | --- |
| Positive direct marker | Method interface có `@Tracked`, gọi qua proxy. | Advice chạy đúng lần, đọc được category. |
| Negative missing marker | Method không có marker, gọi qua proxy. | Không có hành vi advice. |
| Negative excluded | Method có `@Tracked` và `@Internal`. | Điều kiện loại trừ thắng. |
| Error path | Method được chọn ném exception. | Advice failure chạy; caller nhận lỗi theo hợp đồng. |
| Direct target | Bỏ qua proxy. | Handler proxy không chạy. |
| Annotation trên implementation | Marker chỉ có ở implementation. | Quan sát và ghi rõ matcher interface-only có/không tìm thấy. |
| Marker trên type | Chỉ đánh dấu class. | Phân biệt type matcher với method matcher. |
| Override/overload | Marker ở method gốc hoặc một overload. | Tập pointcut khớp đúng đặc tả. |
| `SOURCE`/`CLASS` retention | Runtime matcher đọc qua reflection. | Không giả định marker vẫn xuất hiện. |
| Repeatable/meta-annotation | Marker lặp hoặc được bọc qua meta-annotation. | Hành vi đúng theo matcher công cụ đang dùng. |
| Async | Method được chọn trả handle công việc. | Tách return của method và completion thật. |
| Performance | Pointcut được gọi nhiều lần. | Đo overhead, log volume, cấp phát và không lộ dữ liệu. |

**Thứ tự QA hợp lý:**

1. Kiểm tra annotation declaration, `@Target`, `@Retention`, thuộc tính và nơi khai báo.
2. Kiểm tra matcher đang đọc đúng `Class`, `Method` hay thông tin bytecode nào.
3. Kiểm tra loại join point mà công cụ hỗ trợ.
4. Kiểm tra đường can thiệp thực tế: proxy hay weaving, có bao phủ caller không.
5. Chạy positive/negative tests, đường lỗi và trường hợp kế thừa/metadata khác tầng.
6. Kiểm thử concurrency và mức tải nếu advice sử dụng context hoặc có chi phí đáng kể.

Không chỉ kiểm tra “advice có chạy tại một ví dụ”; cần kiểm tra cả **nơi không được chạy** và đường có nguy cơ bypass.

## 15. Câu hỏi tự đánh giá

**1.** Một Java method có `@Tracked` nhưng không có công cụ nào đọc annotation hoặc kết hợp advice. Điều gì xảy ra?

**Đáp án:** Marker vẫn là metadata theo retention; không tự phát sinh hành vi AOP.

**2.** Vì sao `@Retention(RUNTIME)` cần thiết trong ví dụ JDK proxy?

**Đáp án:** Handler dùng reflection runtime để lấy annotation từ `Method`; nếu marker không được giữ ở runtime thì matcher này không quan sát được.

**3.** Marker trên method của interface có tự trở thành marker trên method implementation khi dùng `Method.getAnnotation` không?

**Đáp án:** Không. Hai declaration khác nhau; matcher phải chủ động duyệt hoặc hợp nhất nếu muốn hỗ trợ.

**4.** `@Inherited` có làm method override tự kế thừa annotation từ method superclass không?

**Đáp án:** Không. `@Inherited` trong Java quy định tra cứu annotation trên **class** theo superclass, không phải kế thừa method annotation.

**5.** `@Observed` trên một class có làm `Method.isAnnotationPresent(Observed.class)` của tất cả method true không?

**Đáp án:** Không mặc nhiên. Phải dùng type matcher hoặc logic tìm annotation trên declaring/runtime class phù hợp.

**6.** Một annotation repeatable xuất hiện hai lần. Có thể kết luận advice chạy hai lần không?

**Đáp án:** Không. Số annotation là dữ liệu; pointcut/advice có quy tắc matching và số occurrence riêng.

**7.** Có một method được marker `@Tracked` nhưng gọi trực tiếp target trong hệ thống chỉ dùng proxy. Advice có chắc chạy không?

**Đáp án:** Không. Điều kiện metadata có thể đúng nhưng handler không nhận lời gọi bỏ qua proxy.

**8.** Một method có marker `@Counted` và annotation type `Counted` có meta-annotation `@ObservabilityMarker`. Vì sao `method.isAnnotationPresent(ObservabilityMarker.class)` không phải cách chắc chắn để chọn method?

**Đáp án:** Vì `ObservabilityMarker` được khai báo trên **annotation type**, không trực tiếp trên method. Cần matcher hỗ trợ meta-annotation hoặc tự duyệt metadata nhiều cấp.

**9.** Lợi ích và rủi ro của chọn theo annotation thay vì tên method là gì?

**Đáp án:** Marker biểu đạt ý nghĩa và ít phụ thuộc quy ước tên hơn; nhưng gắn thiếu marker, đặt sai declaration, retention không phù hợp hoặc đường interception không bao phủ sẽ khiến advice bị bỏ qua.

## Kết luận

Chọn join point theo annotation và metadata là cách làm cho pointcut thể hiện **ngữ nghĩa và ý định áp dụng** thay vì chỉ dựa vào tên/chữ ký. Muốn chính xác, cần giải quyết đồng thời bốn câu hỏi: **metadata nằm ở đâu, còn tồn tại ở giai đoạn nào, matcher đọc chính xác declaration nào, và đường thực thi có đi qua cơ chế AOP hay không**. Khi sử dụng Java, đặc biệt lưu ý retention, type-vs-method, interface-vs-implementation, `@Inherited`, repeatable/meta-annotation và khả năng proxy/weaving. Một pointcut theo annotation đáng tin phải có positive tests, negative tests, đường lỗi và bằng chứng về độ phủ runtime.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contextual-pointcuts-004">Chọn theo luồng điều khiển của một lời gọi</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn theo luồng điều khiển của một lời gọi

## 1. Bài toán: cùng một phương thức nhưng khác ngữ cảnh gọi

Một **pointcut** trong lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP) chọn những **join point** mà một hành vi cắt ngang cần áp dụng. Nhiều pointcut cơ bản chọn theo thông tin tĩnh: tên và chữ ký phương thức, kiểu khai báo, mức truy cập hoặc annotation. Tuy nhiên, đôi khi yêu cầu không nằm ở **phương thức nào**, mà là **phương thức đó được gọi trong luồng xử lý nào**.

Ví dụ, ứng dụng có thao tác `reserve()` để giữ tồn kho. Phương thức này có thể chạy ở hai tình huống:

- Được gọi **bên trong một lần xử lý `checkout()`** để hoàn tất đơn hàng.
- Được gọi **trực tiếp** từ một luồng nghiệp vụ khác, không liên quan đến thanh toán.

Nếu một chính sách ghi sự kiện chỉ muốn quan sát thao tác giữ kho thuộc quá trình thanh toán, pointcut chọn mọi `reserve()` theo tên sẽ quá rộng. Ta muốn diễn đạt:

> “Chọn join point `reserve()` **khi nó đang nằm trong luồng điều khiển động của một lời gọi `checkout()` đã được chọn**.”

Đây là ý tưởng của **pointcut theo luồng điều khiển (control-flow pointcut)**. Một nhóm khái niệm phổ biến là `cflow(P)` và `cflowbelow(P)`, trong đó `P` là pointcut xác định **điểm bắt đầu** của phạm vi quan sát. Không phải mọi công cụ AOP đều hỗ trợ các toán tử hay cú pháp này; trong bài, chúng được dùng để giải thích *ngữ nghĩa* trước khi xét giới hạn triển khai.

## 2. “Luồng điều khiển” là gì?

**Luồng điều khiển động** là những gì đang thực sự chạy theo chuỗi gọi và quay về tại thời điểm hiện tại. Nếu `checkout()` gọi `reserve()`, rồi `reserve()` gọi `audit()`, thì trong lúc `audit()` được thực thi đồng bộ, cả `checkout()` và `reserve()` vẫn đang hoạt động phía ngoài.

~~~text
Caller
  |
  v
checkout()             <- điểm bắt đầu P
  |
  +--> reserve()
  |      |
  |      +--> audit("reserve")
  |
  +--> audit("checkout")
  |
  v
return
~~~

Một lần gọi `audit()` khác từ bên ngoài `checkout()` có thể đi tới **cùng một phương thức**, nhưng không nằm trong luồng điều khiển này.

Cần phân biệt:

| Đặc điểm | Khớp tĩnh theo chữ ký | Khớp động theo luồng điều khiển |
| --- | --- | --- |
| Câu hỏi chính | “Đây là phương thức/điểm nào?” | “Điểm này đang chạy bên trong chuỗi gọi nào?” |
| Dữ liệu quyết định | Kiểu, tên, modifier, annotation, v.v. | Trạng thái ngăn xếp gọi và điểm bắt đầu đang hoạt động |
| Hai lần gọi cùng phương thức | Thường có cùng kết quả khi xét điều kiện tĩnh | Có thể một lần khớp, lần còn lại không |
| Thời điểm đánh giá | Có thể xác định trước ở nhiều trường hợp | Phụ thuộc đường thực thi tại runtime |
| Chi phí | Có thể tối ưu nhiều thông tin tĩnh | Có thể cần theo dõi hoạt động của luồng thực thi |
| Mục đích | Chọn nhóm API, lớp và vai trò kỹ thuật | Chọn hành vi khi đang ở trong một tiến trình nghiệp vụ cụ thể |

Không nên hiểu “luồng điều khiển” như **luồng dữ liệu** hoặc “đồ thị nơi một phương thức *có khả năng* gọi tới phương thức khác”. Đồ thị lời gọi tĩnh cho biết khả năng; cflow quan tâm **lời gọi nào đang diễn ra thật** tại đúng thời điểm join point xuất hiện.

## 3. Ngữ nghĩa của `cflow(P)`

Cho `P` là pointcut chọn những join point đóng vai trò **điểm gốc** của luồng cần theo dõi. Trong mô hình AOP có hỗ trợ khái niệm này:

**`cflow(P)` chọn các join point diễn ra trong luồng điều khiển của join point khớp `P`, bao gồm chính điểm khớp `P`.**

Điều đó có nghĩa:

1. Khi chưa có join point nào khớp `P` đang hoạt động, một join point bên ngoài không được chọn chỉ vì cùng tên với một lời gọi trong tương lai.
2. Khi vào join point gốc khớp `P`, gốc đó **nằm trong `cflow(P)`**.
3. Trong khi gốc chưa kết thúc, các join point xảy ra bên dưới theo luồng gọi đồng bộ có thể thuộc `cflow(P)`.
4. Khi gốc kết thúc bình thường hoặc rời đi vì ngoại lệ, phạm vi của lần kích hoạt ấy kết thúc; các join point về sau không còn thuộc phạm vi ấy, trừ khi có một gốc khác đang hoạt động.

Ví dụ với `P` là “execution của `checkout()`”:

~~~text
POINT checkout              cflow(P) = true
  POINT reserve             cflow(P) = true
    POINT audit             cflow(P) = true
  POINT audit               cflow(P) = true
RETURN FROM checkout

POINT reserve (gọi riêng)  cflow(P) = false
  POINT audit              cflow(P) = false
~~~

Sơ đồ nêu ngữ nghĩa khái niệm. Nó không chỉ ra kiểu join point của từng công nghệ; chẳng hạn “method call” có thể được xem là một điểm tại caller, còn “method execution” ở callee.

Nếu cần quan sát **chỉ các lời gọi `audit()` trong quá trình checkout**, pointcut tổng hợp có thể mô tả:

~~~text
isAuditJoinPoint AND cflow(checkoutStart)
~~~

Ở đây `isAuditJoinPoint` là điều kiện chọn đối tượng cần quan sát, còn `checkoutStart` xác định **phạm vi ngữ cảnh**. Hai vai trò này khác nhau: bỏ điều kiện `isAuditJoinPoint` có thể khiến mọi join point bên trong checkout đều được chọn.

## 4. Khác biệt của `cflowbelow(P)`

**`cflowbelow(P)`** mô tả các join point **nằm bên dưới một join point khớp `P` đang hoạt động**, **không bao gồm chính điểm gốc khớp `P` của lần kích hoạt đó**.

Với `P` chọn lần vào `checkout()` từ bên ngoài:

| Join point | `cflow(P)` | `cflowbelow(P)` | Lý do |
| --- | --- | --- | --- |
| Điểm vào `checkout()` gốc | Có | Không | `cflow` gồm gốc; `cflowbelow` không gồm chính gốc ấy |
| `reserve()` gọi bên trong `checkout()` | Có | Có | Nằm thấp hơn gốc đang hoạt động |
| `audit()` gọi sâu từ `reserve()` | Có | Có | Vẫn ở trong ngăn xếp có gốc |
| `audit()` gọi trực tiếp ngoài checkout | Không | Không | Không có gốc đang hoạt động |
| `reserve()` sau khi checkout kết thúc | Không | Không | Phạm vi gốc đã kết thúc |

### 4.1. Vì sao cần loại bỏ điểm gốc?

Có những chính sách cần áp dụng **chỉ cho thao tác con** của một tác vụ, không áp dụng trực tiếp lên điểm bắt đầu. Chẳng hạn muốn đo mọi thao tác kho và audit thực hiện *bên dưới checkout* nhưng không tính cả điểm `checkout()` vào một bộ đếm join point. Lúc đó `cflowbelow(P)` diễn đạt đúng hơn `cflow(P)`.

### 4.2. Đệ quy và nhiều điểm gốc lồng nhau

Với đệ quy, `checkout()` có thể gọi một `checkout()` khác khi lần ngoài chưa kết thúc. **Lần `checkout()` bên trong** vẫn là join point khớp `P`, nhưng nó đồng thời **nằm bên dưới gốc khớp `P` phía ngoài**. Vì vậy, trong ngữ nghĩa luồng động, có thể có một join point mang nhãn `P` mà vẫn thuộc `cflowbelow(P)` do đang ở dưới **một gốc khác**.

Không nên phát biểu đơn giản rằng “mọi điểm khớp `P` đều bị `cflowbelow(P)` loại trừ” trong mọi trường hợp. Cách diễn đạt chính xác hơn là **loại chính điểm gốc của phạm vi đang xét; những điểm khớp `P` lồng bên trong có thể nằm dưới một gốc ngoài**.

### 4.3. Không coi “below” là một tầng trực tiếp

`cflowbelow(P)` không chỉ chọn lời gọi con trực tiếp. Một join point nằm sâu nhiều tầng (`checkout → reserve → audit → persist`) vẫn có thể khớp vì nó thuộc phần dưới trong cùng luồng điều khiển. Nếu yêu cầu chỉ chọn **callee trực tiếp** của một lời gọi, đó là bài toán khác, không được giải quyết chỉ bằng “below”.

## 5. Kết hợp pointcut luồng điều khiển với lựa chọn mục tiêu

Một điểm dễ gây lỗi là chỉ viết quy tắc phạm vi và quên điều kiện chọn đối tượng. Ví dụ mục tiêu:

> “Ghi metrics cho những lần `reserve()` diễn ra khi `checkout()` đang chạy, nhưng không ghi metrics cho mọi phương thức khác của checkout.”

Mô tả khái niệm:

~~~text
P = execution(checkout(...))
Q = execution(reserve(...))

selected = Q AND cflowbelow(P)
~~~

Các từ `execution`, `AND` và ký hiệu dấu ba chấm trong sơ đồ là **ký pháp giải thích**, không bảo đảm là cú pháp chung của mọi AOP engine.

Ý nghĩa: `Q` xác định *loại join point cần can thiệp*; `cflowbelow(P)` xác định *ngữ cảnh cần có tại thời điểm xảy ra*. Nếu `reserve()` chạy từ một đường gọi khác, `Q` vẫn đúng nhưng điều kiện ngữ cảnh sai, nên không được chọn. Nếu một `audit()` chạy trong checkout, ngữ cảnh đúng nhưng `Q` sai, nó cũng không thuộc pointcut tổng hợp này.

Có thể dùng các phép ghép khác khi cơ chế cho phép:

| Mô tả | Kết quả mong muốn |
| --- | --- |
| `Q AND cflow(P)` | Chọn `Q` khi thuộc luồng của `P`, có thể gồm gốc nếu gốc cũng khớp `Q` |
| `Q AND cflowbelow(P)` | Chọn `Q` nằm dưới điểm gốc `P` |
| `Q AND NOT cflow(P)` | Chọn `Q` khi **không** ở trong luồng của `P` |
| `(Q OR R) AND cflow(P)` | Chọn `Q` hoặc `R` nhưng chỉ trong ngữ cảnh `P` |
| `Q AND cflow(P) AND inBoundary(B)` | Chọn `Q` trong luồng `P` và phạm vi `B` đã định nghĩa |

Phủ định rộng `NOT cflow(P)` có thể chọn vô số join point không liên quan. Hãy luôn giới hạn nó bằng bộ chọn mục tiêu như `Q` để quy tắc diễn đạt đúng ý định và tránh overhead.

## 6. Ví dụ Java 17+ chạy được: mô phỏng cflow và cflowbelow

Ví dụ sau dùng **JDK dynamic proxy** và **`ThreadLocal` ghi độ sâu các lần `checkout()` đang hoạt động** để *mô phỏng trực quan* điểm gốc của luồng điều khiển. Đây **không phải engine AOP đầy đủ** và không khẳng định tên `cflow` là một API tích hợp của Java. Nó cho thấy cách một **bộ chọn có ngữ cảnh** thay đổi kết quả ở các lần gọi khác nhau.

Để mọi lời gọi nội bộ cũng đi qua proxy, `Core` nhận một tham chiếu `calls` trỏ tới interface proxy. Nếu gọi `this.reserve()` trực tiếp, proxy sẽ không quan sát lời gọi ấy. Mô hình này chỉ theo dõi **lời gọi đồng bộ qua proxy trên cùng thread**.

Tạo file `ControlFlowPointcutDemo.java`:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class ControlFlowPointcutDemo {
    interface Operations {
        String checkout(String id);
        String reserve(String id);
        void audit(String event);
    }

    static final class Core implements Operations {
        private Operations calls;

        void bind(Operations calls) {
            this.calls = calls;
        }

        @Override
        public String checkout(String id) {
            System.out.println("CORE checkout");
            calls.reserve(id);
            calls.audit("checkout " + id);
            if ("bad".equals(id)) {
                throw new IllegalArgumentException("invalid order");
            }
            return "done " + id;
        }

        @Override
        public String reserve(String id) {
            System.out.println("CORE reserve");
            calls.audit("reserve " + id);
            return "reserved " + id;
        }

        @Override
        public void audit(String event) {
            System.out.println("CORE audit " + event);
        }
    }

    static final class FlowHandler implements java.lang.reflect.InvocationHandler {
        private final Operations target;
        private final ThreadLocal<Integer> checkoutDepth =
                ThreadLocal.withInitial(() -> 0);

        FlowHandler(Operations target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            int depthAtEntry = checkoutDepth.get();
            boolean isCheckout = method.getName().equals("checkout");
            boolean inCheckoutFlow = isCheckout || depthAtEntry > 0;
            boolean strictlyBelowCheckout = depthAtEntry > 0;

            System.out.println("POINT " + method.getName()
                    + " cflow=" + inCheckoutFlow
                    + " cflowbelow=" + strictlyBelowCheckout);

            if (isCheckout) {
                checkoutDepth.set(depthAtEntry + 1);
            }
            try {
                try {
                    return method.invoke(target, args);
                } catch (InvocationTargetException wrapped) {
                    throw wrapped.getCause();
                }
            } finally {
                if (isCheckout) {
                    if (depthAtEntry == 0) {
                        checkoutDepth.remove();
                    } else {
                        checkoutDepth.set(depthAtEntry);
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        Core core = new Core();
        Operations operations = (Operations) Proxy.newProxyInstance(
                Operations.class.getClassLoader(),
                new Class<?>[] { Operations.class },
                new FlowHandler(core));
        core.bind(operations);

        operations.audit("outside");
        System.out.println("RESULT " + operations.checkout("A-1"));
        System.out.println("RESULT " + operations.reserve("B-2"));
        try {
            operations.checkout("bad");
        } catch (IllegalArgumentException error) {
            System.out.println("ERROR " + error.getMessage());
        }
        operations.audit("after-error");
    }
}
~~~

Lệnh biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 ControlFlowPointcutDemo.java
java ControlFlowPointcutDemo
~~~

Đầu ra chính xác, đã kiểm chứng với Java 17 language target:

~~~text
POINT audit cflow=false cflowbelow=false
CORE audit outside
POINT checkout cflow=true cflowbelow=false
CORE checkout
POINT reserve cflow=true cflowbelow=true
CORE reserve
POINT audit cflow=true cflowbelow=true
CORE audit reserve A-1
POINT audit cflow=true cflowbelow=true
CORE audit checkout A-1
RESULT done A-1
POINT reserve cflow=false cflowbelow=false
CORE reserve
POINT audit cflow=false cflowbelow=false
CORE audit reserve B-2
RESULT reserved B-2
POINT checkout cflow=true cflowbelow=false
CORE checkout
POINT reserve cflow=true cflowbelow=true
CORE reserve
POINT audit cflow=true cflowbelow=true
CORE audit reserve bad
POINT audit cflow=true cflowbelow=true
CORE audit checkout bad
ERROR invalid order
POINT audit cflow=false cflowbelow=false
CORE audit after-error
~~~

### 6.1. Giải thích năm đoạn quan trọng

1. **`audit("outside")`:** không có `checkout()` nào đang hoạt động, nên cả `cflow` lẫn `cflowbelow` đều `false`.
2. **Điểm `checkout("A-1")`:** đây là gốc vừa được chọn, vì vậy `cflow=true` nhưng `cflowbelow=false`. Bộ đếm độ sâu được tăng trước khi gọi vào target.
3. **`reserve()` và `audit()` lồng nhau:** khi checkout còn hoạt động, các lời gọi qua proxy nằm **dưới gốc** nên cả hai cờ là `true`. `audit("checkout A-1")` dù được gọi trực tiếp từ checkout cũng thuộc `cflowbelow`.
4. **`reserve("B-2")` gọi độc lập:** nó có cùng chữ ký với `reserve("A-1")` nhưng không nằm dưới checkout đang hoạt động; vì vậy cờ đều `false`. Đây chính là giá trị của pointcut theo ngữ cảnh.
5. **`checkout("bad")` có ngoại lệ:** trong `finally`, bộ đếm được phục hồi, sau đó `audit("after-error")` chạy bên ngoài với cả hai cờ `false`. Nếu quên cleanup khi lỗi, lời gọi sau có thể bị nhận nhầm vào luồng cũ.

### 6.2. Vì sao dùng bộ đếm thay vì boolean?

Nếu `checkout()` gọi lồng một `checkout()` khác, đặt cờ `true` khi vào rồi đặt `false` khi ra sẽ sai: inner checkout kết thúc có thể xóa trạng thái mà outer checkout vẫn đang hoạt động. **Đếm độ sâu** và khôi phục `depthAtEntry` trong `finally` cho phép quản lý phạm vi lồng nhau an toàn hơn.

Ví dụ chỉ xét một điểm gốc là `checkout()` và xử lý `cflowbelow` qua `depthAtEntry > 0`. Trong một engine AOP thực, việc khớp điểm gốc, precedence và tính toán các điểm thuộc luồng có thể được thực hiện khác; không xem ví dụ proxy này như đặc tả cho mọi công cụ.

### 6.3. Những giới hạn của ví dụ

- Chỉ intercept phương thức được gọi qua interface proxy `Operations`.
- Bộ chọn gốc ở đây so tên `checkout`; ứng dụng thật cần xét chữ ký/overload/annotation để tránh khớp nhầm.
- Dữ liệu trong `ThreadLocal` thuộc **thread hiện tại**, không tự động theo một tác vụ chuyển thread.
- Proxy quan sát các điểm vào phương thức, không tự phát hiện constructor, truy cập field, lời gọi trực tiếp tới target hoặc join point được bytecode weaving hỗ trợ sâu hơn.
- Không có advice tách riêng; chương trình in kết quả lựa chọn tại handler để minh họa semantics. Muốn thực hiện chính sách, kết hợp điều kiện ngữ cảnh với một bộ chọn join point mục tiêu và đặt advice vào đó.

## 7. Khác biệt giữa cflow và đồ thị gọi tĩnh

Giả sử mã có thể đi theo hai đường:

~~~text
Path A: checkout() -> reserve() -> audit()
Path B: adminTask() -> reserve() -> audit()
~~~

Nếu chỉ phân tích source hoặc đồ thị gọi, cả hai đường đều có `reserve()` gọi `audit()`. Một pointcut tĩnh chỉ chọn `audit()` sẽ khớp cả hai. Nhưng `cflow(checkout)` **chỉ đúng trên Path A khi một checkout đang thực sự hoạt động**.

Cũng không phải cứ code của `checkout()` từng tạo ra một tác vụ thì những công việc xảy ra sau đó đều nằm dưới stack của checkout. **Nguồn gốc dữ liệu** và **ngăn xếp điều khiển hiện tại** là hai mối quan hệ khác nhau. Muốn theo một request xuyên qua tác vụ bất đồng bộ hoặc hàng đợi, có thể cần correlation ID hoặc context propagation, không thể chỉ dựa vào `cflow` trên một stack đồng bộ.

Cách tự hỏi giúp chọn đúng phương pháp:

- “Một phương thức được khai báo trong class nào?” → pointcut theo metadata tĩnh.
- “Một phương thức được gọi khi `checkout()` vẫn đang nằm trên đường gọi?” → pointcut theo luồng điều khiển.
- “Một tác vụ có cùng request ID với checkout ban đầu dù chạy sau, ở thread khác?” → cần theo ngữ cảnh tương quan/phân tán; không mặc định `cflow` đáp ứng.
- “Một đối số có cùng đối tượng đã xuất hiện trong lời gọi gốc?” → bài toán identity/value flow khác với chỉ theo call stack.

## 8. Đệ quy, reentrancy và ngoại lệ

### 8.1. Đệ quy và sự lồng nhau

Trong thuật toán đệ quy, điểm gốc `P` có thể được kích hoạt nhiều lần trước khi lần đầu trả về. Nếu dùng một cờ boolean đơn giản, kết thúc một lần bên trong có thể làm mất trạng thái lần ngoài. Cấu trúc stack hoặc **depth counter** là mô hình phù hợp để biểu diễn có bao nhiêu phạm vi gốc đang hoạt động.

**`cflowbelow(P)` không đơn giản là NOT P.** Một lần kích hoạt `P` lồng bên trong có thể vừa thuộc tập `P` vừa nằm dưới lần `P` bên ngoài. Khi viết pointcut cho đệ quy, cần xác định muốn áp dụng trên **mọi cấp** hay chỉ **đỉnh đầu tiên** của call chain.

### 8.2. Ngoại lệ và cleanup

Khi lời gọi gốc ném ngoại lệ, các khối `finally` dọn trạng thái nên được thực thi trong phạm vi đã đi vào. Nếu cơ chế tự quản lý cflow, ta cần tin vào hợp đồng về kết thúc phạm vi; khi tự mô phỏng như ví dụ, buộc phải đặt cập nhật trạng thái trong `try/finally`.

Lưu ý: việc chính advice ghi sự kiện gây ngoại lệ có thể ảnh hưởng quá trình tháo stack. Nếu mục đích chỉ là đo lường, hãy có chính sách lỗi độc lập cho logging/metrics để không vô tình che ngoại lệ gốc.

### 8.3. Gọi lặp vào cùng target và thứ tự advice

Nếu cùng một join point bị bao bởi nhiều advice `around`, mỗi advice có thể gọi `proceed` hoặc đổi đường thực thi. Khi retry, một target có thể chạy nhiều lần dưới cùng một gốc hoặc dưới nhiều gốc khác nhau, tùy vị trí advice retry. Vì vậy, **cflow theo số lần kích hoạt thực tế**, không theo ý định “chỉ thực hiện một lần” của caller.

Hãy xác minh thứ tự advice và số lần target thực thi khi có cache, retry, kiểm quyền hoặc giao dịch, đặc biệt khi chính sách cắt ngang có tác dụng phụ.

## 9. Cflow với bất đồng bộ, thread, coroutine và tác vụ phân tán

Luồng điều khiển động của lời gọi đồng bộ thường dựa trên trạng thái call stack tại thời điểm join point xảy ra. Khi chuyển sang **thread khác, callback sau này, event loop hoặc tiến trình khác**, việc giữ nguyên stack đang hoạt động không được bảo đảm. Ví dụ:

~~~text
Thread A:
checkout()
  submitAsync(task)
  return                  <- phạm vi checkout kết thúc

Thread B (về sau):
task()
  reserve()
  audit()
~~~

Ở Thread B, không thể chỉ nhìn tên `reserve()` rồi kết luận `cflow(checkout)` đúng theo call stack của Thread A. Nếu cần tiếp tục theo dõi “cùng thao tác checkout”, hãy xem xét **truyền ngữ cảnh** qua tác vụ: request ID, trace ID hoặc một token đại diện cho workflow, kèm quy tắc vòng đời và cleanup rõ ràng.

Cần phân biệt:

| Cơ chế | Theo dõi | Phù hợp khi |
| --- | --- | --- |
| Control-flow pointcut | Join point nằm dưới một điểm gốc đang hoạt động | Tác vụ/lời gọi đồng bộ trong đường thực thi có thể quan sát |
| Thread-local state | Dữ liệu gắn với thread | Cần ngữ cảnh trong thread hiện tại; phải xử lý pool/reuse |
| Context propagation | Ngữ cảnh chuyển giữa các tác vụ/thread | Tác vụ bất đồng bộ cần giữ correlation |
| Distributed tracing | Quan hệ các span/trace giữa tiến trình | Theo dõi luồng phân tán qua mạng và dịch vụ |

Các công nghệ AOP khác nhau có thể mở rộng hoặc diễn giải phạm vi theo mô hình riêng. **Không tự suy luận rằng toán tử tên `cflow` tự động truyền qua thread hoặc tiến trình**. Chỉ coi một cơ chế có khả năng đó khi tài liệu và kiểm thử của chính công cụ xác nhận.

`ThreadLocal` cũng cần được xóa hoặc khôi phục ở phạm vi phù hợp, đặc biệt với thread pool dùng lại thread. Nếu không, lời gọi không liên quan sau đó có thể bị chọn nhầm.

## 10. Giới hạn theo công nghệ AOP

### 10.1. Không phải mọi pointcut engine hỗ trợ `cflow`

Có những engine chỉ hỗ trợ khớp chữ ký, annotation hoặc lời gọi qua proxy. Có những engine hỗ trợ điều kiện pointcut động, nhưng không có cú pháp `cflow` hay `cflowbelow`. Một bộ máy weaving có thể có ngôn ngữ pointcut phong phú hơn, tuy nhiên vẫn phải kiểm tra phạm vi join point thực tế.

Một phép thử khả năng tối thiểu:

1. Công cụ có toán tử chọn theo luồng động hay chỉ metadata tĩnh?
2. Toán tử có hỗ trợ loại join point gốc cần dùng (call/execution) không?
3. Nó có làm rõ **bao gồm hay loại trừ gốc** không?
4. Phạm vi weaving có bao gồm tất cả phương thức/lớp cần quan sát không?
5. Có giới hạn do classloader, module, proxy, lời gọi nội bộ, `private` hoặc `final` không?
6. Nó có bảo toàn ngữ nghĩa khi exception, recursion, thread switch và nested pointcut không?
7. Điều kiện runtime được đánh giá thế nào và chi phí ước lượng ra sao?

### 10.2. Method call khác method execution

Một số mô hình AOP phân biệt:

- **Call join point:** nơi mã caller thực hiện lời gọi tới phương thức.
- **Execution join point:** nơi thân phương thức được thực thi ở phía callee.

Điểm được chọn làm gốc `P` sẽ quyết định ranh giới của cflow bắt đầu và kết thúc. Nếu chọn call join point, một số vị trí quanh lệnh gọi có thể nằm trong phạm vi khác với execution join point. Cần chọn điểm gốc phù hợp với ý định quan sát và không tùy tiện coi hai loại tương đương.

### 10.3. Proxy và lời gọi nội bộ

Proxy thường chỉ quan sát lời gọi **đi qua proxy**. Lời gọi `this.reserve()` bên trong target có thể bỏ qua proxy, khiến lời gọi ấy không tạo join point theo mô hình proxy dù nó xảy ra trên call stack Java. Khi cần quan sát sâu, kiểm tra khả năng của công cụ và đường lời gọi thực; không dùng “cflow có vẻ đúng” để bỏ qua giới hạn can thiệp.

Trong ví dụ chạy được, `Core` cố ý gọi `calls.reserve()` và `calls.audit()` qua proxy để ta nhìn được các điểm lồng nhau. Điều này chỉ là kỹ thuật minh họa, không phải yêu cầu thiết kế áp dụng cho mọi codebase.

### 10.4. Chi phí tại runtime

Một pointcut theo metadata tĩnh có thể được quyết định trước ở nhiều trường hợp. Một pointcut theo luồng động thường cần biết “gốc `P` có đang hoạt động hay không”, dẫn đến việc duy trì trạng thái, kiểm tra mỗi join point hoặc thao tác bổ sung khi vào/ra vùng chọn.

Chi phí thực phụ thuộc engine, số join point, phương pháp triển khai và tần suất gọi. Không khẳng định `cflow` luôn đắt một mức cố định; thay vào đó, đo overhead thực tế dưới tải đại diện, giới hạn bộ chọn mục tiêu `Q`, và tránh phủ định/điều kiện rộng không cần thiết.

### 10.5. Không che khuất hợp đồng nghiệp vụ

Nếu một chức năng chỉ được phép chạy khi checkout đang hoạt động vì **bất biến nghiệp vụ**, cần cân nhắc biểu diễn bất biến đó trong API và logic nghiệp vụ. Pointcut theo cflow thích hợp hơn cho những **chính sách cắt ngang phụ thuộc ngữ cảnh** như audit/metrics/tracing, không nên biến việc có/không có advice thành cơ chế duy nhất bảo vệ dữ liệu miền nếu ranh giới đó có thể bị bỏ qua.

## 11. Thiết kế một control-flow pointcut có thể bảo trì

Một quy trình thực hành:

1. **Xác định chính sách:** cần chọn log, metrics hoặc quan sát gì? Viết một câu diễn đạt ngữ cảnh, ví dụ “ghi audit ở thao tác kho chỉ trong quy trình checkout đồng bộ”.
2. **Chọn gốc `P`:** đây là join point biểu thị rõ “checkout bắt đầu”; phân biệt call/execution và overload, tránh chọn quá rộng vì cùng tên.
3. **Chọn mục tiêu `Q`:** phương thức hoặc join point nào trong luồng cần advice? `Q` giúp tránh gắn advice vào mọi điểm dưới gốc.
4. **Quyết định `cflow` hay `cflowbelow`:** có muốn tính cả join point gốc vào tập chọn không?
5. **Liệt kê đường khớp và đường không khớp:** gọi lồng một tầng, nhiều tầng, độc lập, đệ quy, ném lỗi, tự gọi `this`, bất đồng bộ.
6. **Xác minh khả năng công cụ:** pointcut theo ngữ cảnh, weaving, proxy, runtime binding, visibility và module/classloader.
7. **Đo tác dụng phụ:** advice có làm thay đổi kết quả, số lần chạy, ngoại lệ, độ trễ hoặc lượng dữ liệu log không?
8. **Kiểm thử theo đường chạy thực tế:** kiểm tra cùng một phương thức `reserve` khi được gọi từ checkout và khi được gọi độc lập; xác minh cleanup sau lỗi.

Ví dụ bảng test cho `Q AND cflowbelow(P)` với `P=checkout`, `Q=reserve`:

| Trường hợp | Kỳ vọng |
| --- | --- |
| `checkout → reserve` trên đường gọi quan sát được | Khớp |
| `checkout → helper → reserve` nhiều tầng | Khớp |
| `reserve` gọi độc lập | Không khớp |
| `reserve` gọi sau khi checkout trả về | Không khớp |
| `checkout` là điểm gốc nhưng không khớp `reserve` | Không khớp `Q` |
| `checkout` ném lỗi, sau đó `reserve` gọi độc lập | Không khớp; cleanup phải đúng |
| Checkout đệ quy | Xác định rõ quan hệ với gốc bên ngoài |
| `checkout` tạo task chạy trên thread khác | Tùy mô hình công cụ; không tự nhận là cùng cflow |
| `this.reserve()` bỏ qua proxy | Không kỳ vọng proxy tạo join point cho reserve |

### Khi một flow có nhiều gốc

Nếu có hai lần `checkout()` nằm lồng nhau, quy tắc `cflow(checkout)` có thể đúng do **ít nhất một gốc** đang hoạt động. Nếu chính sách yêu cầu chỉ gắn sự kiện với *một* lần checkout cụ thể, boolean/depth không đủ phân biệt danh tính của gốc. Khi đó cần một context token hoặc stack của **định danh tác vụ**, cùng quy tắc truyền/khôi phục ngữ cảnh an toàn.

Đây là điểm khác nhau giữa kiểm tra **“có nằm trong một checkout?”** và **“nằm trong checkout có ID X?”**. Loại thứ hai đòi hỏi binding ngữ cảnh hoặc chính sách bổ sung ngoài toán tử cflow thuần túy.

## 12. Những lỗi hiểu sai phổ biến

- **Cho rằng cflow chọn phương thức theo tên:** nó chọn dựa trên trạng thái luồng động; thường cần kết hợp một pointcut mục tiêu theo tên/chữ ký.
- **Nhầm `cflow` với `cflowbelow`:** thứ nhất tính cả điểm gốc; thứ hai chọn các điểm dưới gốc.
- **Hiểu `cflowbelow` chỉ chọn con trực tiếp:** nó có thể chọn điểm ở sâu nhiều tầng trong ngăn xếp.
- **Coi `cflowbelow(P)` là `NOT P`:** đệ quy/lồng nhau cho thấy điều này không đúng.
- **Nhìn đồ thị source rồi suy ra runtime cflow:** khả năng gọi không chứng minh lời gọi đó đang hoạt động.
- **Đặt cờ boolean không khôi phục khi lỗi:** trạng thái rò rỉ sang lời gọi sau, đặc biệt nguy hiểm trên thread pool.
- **Bỏ qua self-invocation hoặc đường gọi trực tiếp:** proxy không quan sát mọi lời gọi của target.
- **Cho rằng một task bất đồng bộ vẫn nằm trên stack gốc:** cần ngữ cảnh được truyền rõ nếu muốn theo dõi giữa thread.
- **Dùng `NOT cflow(P)` quá rộng:** có thể áp advice lên cả hệ thống ngoài ý muốn.
- **Tin mọi công cụ đều có cùng cú pháp cflow:** pointcut model, loại join point và weaving khác nhau.
- **Đặt nghiệp vụ quan trọng chỉ trong advice có ngữ cảnh:** làm quy tắc miền khó nhìn thấy và dễ bị bỏ qua bởi những đường không được can thiệp.

## 13. Câu hỏi tự kiểm tra

**Câu 1.** `reserve()` được gọi trực tiếp và cũng được gọi từ `checkout()`. Có thể dùng pointcut chỉ theo chữ ký `reserve()` để phân biệt hai tình huống không?  
**Trả lời:** Không. Chữ ký giống nhau; cần thêm điều kiện ngữ cảnh luồng điều khiển hoặc ngữ cảnh runtime tương đương.

**Câu 2.** Pointcut `P` chọn `checkout()`. Chính điểm gốc này có thuộc `cflow(P)` và `cflowbelow(P)` không?  
**Trả lời:** Thuộc `cflow(P)`, nhưng không thuộc `cflowbelow(P)` do *chính lần kích hoạt gốc đó*. Nếu một `checkout` khác lồng dưới gốc ngoài, nó có thể thuộc `cflowbelow(P)` nhờ gốc ngoài.

**Câu 3.** Một `audit()` gọi sâu ba tầng dưới checkout có thuộc `cflowbelow(P)` không?  
**Trả lời:** Có thể có, nếu nó là một join point được công cụ quan sát và luồng gốc vẫn đang hoạt động; “below” không giới hạn một tầng.

**Câu 4.** Checkout tạo một `CompletableFuture` rồi trả về, còn `reserve()` chạy sau trên thread khác. Có thể mặc định `cflow(checkout)` đúng ở `reserve()` không?  
**Trả lời:** Không. Call stack động có thể đã kết thúc hoặc khác thread; cần kiểm tra hợp đồng công nghệ và cơ chế truyền ngữ cảnh nếu muốn theo dõi quan hệ tác vụ.

**Câu 5.** Tại sao trong ví dụ cần `finally` và bộ đếm độ sâu?  
**Trả lời:** `finally` đảm bảo phục hồi trạng thái trên nhánh lỗi; bộ đếm giữ được các phạm vi gốc lồng nhau mà boolean đơn giản có thể xử lý sai.

## 14. Tổng kết

**Pointcut theo luồng điều khiển** lựa chọn join point dựa trên **ngữ cảnh lời gọi đang diễn ra tại runtime**, thay vì chỉ dựa trên chữ ký hoặc vị trí khai báo. Ý tưởng `cflow(P)` gồm điểm gốc khớp `P` và những điểm thuộc luồng thực thi của nó; `cflowbelow(P)` chọn những điểm ở bên dưới gốc. Trong thực tế, cần kết hợp điều kiện chọn mục tiêu, chọn đúng loại join point gốc, bảo toàn trạng thái khi ngoại lệ/đệ quy, và hiểu giới hạn của proxy, weaving, thread cùng tác vụ bất đồng bộ. Kiểm thử cả đường trong luồng và ngoài luồng mới chứng minh advice áp dụng đúng ý định.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contextual-pointcuts-005">Điều kiện tĩnh và điều kiện cần đánh giá lúc chạy</a>

<details>
<summary>Xem chi tiết</summary>

# Điều kiện tĩnh và điều kiện cần đánh giá lúc chạy trong pointcut AOP

> **Mục tiêu học tập:** Phân biệt những điều kiện lựa chọn join point có thể quyết định từ cấu trúc chương trình với những điều kiện phụ thuộc dữ liệu/ngữ cảnh tại **mỗi lần thực thi**; hiểu cách hai nhóm điều kiện phối hợp trong một pointcut, thời điểm đánh giá, lợi ích của việc lọc sớm, giới hạn của weaving và cách kiểm thử để tránh lựa chọn thừa hoặc bỏ sót điểm can thiệp.

## 1. Vì sao cùng một phương thức có lần được chọn, lần không?

Giả sử một chính sách AOP cần đo thời gian xử lý **chỉ khi** một thao tác thanh toán có mã khách hàng thuộc nhóm ưu tiên và số tiền đạt ngưỡng. Phương thức có thể được gọi nhiều lần:

~~~java
pay("VIP", 120);
pay("VIP", 20);
pay("NORMAL", 150);
~~~

*Đây là ba lời gọi minh họa, chưa phải chương trình Java độc lập.*

Cả ba lời gọi có cùng **phương thức được chọn theo tên và chữ ký**. Nhưng điều kiện **giá trị đối số** chỉ đúng ở lần đầu.

Muốn mô tả chính sách đó, ta cần hai loại thông tin:

- **Thông tin cấu trúc:** có đang thực thi phương thức `pay(String, int)` trong loại thành phần dự định không? Phương thức có dấu hiệu khai báo cần thiết không?
- **Thông tin động:** ở lần gọi cụ thể, tier có bằng `"VIP"` và amount có đạt ngưỡng `100` hay không?

Trong AOP (Aspect-Oriented Programming), **pointcut** mô tả điều kiện chọn một tập **join point**; **advice** là hành vi bổ sung tại các điểm được chọn. Điều kiện có thể có phần xác định được từ cấu trúc mã và phần phải kiểm tra khi chương trình chạy. Đây là cốt lõi của việc phân biệt **static matching** và **dynamic matching**.

Không được suy rằng tên gọi “static” ở đây chỉ nói đến từ khóa `static` của Java. “Tĩnh” nói về **thông tin cần để quyết định điều kiện**, hoàn toàn khác với khái niệm **static method**.

## 2. Join point, pointcut, shadow và lần xuất hiện động

Những khái niệm dưới đây cần được giữ riêng:

| Khái niệm | Ý nghĩa | Ví dụ |
|---|---|---|
| **Join point** | Điểm/sự kiện trong mô hình thực thi mà cơ chế AOP có thể nhận diện | Một lần thực thi của `pay(...)` |
| **Join point shadow** | Vị trí trong mã có khả năng tạo join point tại lúc chạy, trong những mô hình có thuật ngữ này | Thân phương thức `pay(String,int)` hoặc một call site |
| **Pointcut** | Điều kiện chọn những join point cần áp dụng chính sách | Chọn `pay` khi tier VIP và amount đạt ngưỡng |
| **Điều kiện tĩnh** | Phần lựa chọn có thể quyết định từ thông tin cấu trúc được cung cấp, không cần giá trị của lần chạy đó | Tên, chữ ký, loại join point |
| **Điều kiện động** | Phần phụ thuộc dữ liệu hoặc ngữ cảnh của một lần xuất hiện | Giá trị đối số, đối tượng thực tế, luồng điều khiển |
| **Advice** | Hành vi thực hiện tại join point được chọn | Ghi bắt đầu/kết thúc |
| **Weaving** | Cơ chế kết hợp hành vi aspect vào chương trình | Kết hợp advice tại phạm vi điểm được hỗ trợ |

Một phương thức được biên dịch một lần nhưng thực thi nhiều lần. Với pointcut chỉ dựa trên **chữ ký phương thức**, các lần đó thường cho cùng một câu trả lời lựa chọn (giả sử cấu trúc mã không thay đổi). Với pointcut dựa trên **đối số thực tế**, mỗi lần thực thi có thể cho câu trả lời khác nhau.

Các tên như *shadow* hoặc cách phân loại join point là những khái niệm trong một số mô hình AOP; không phải mọi công cụ đều mô tả hoặc cung cấp cùng loại điểm.

## 3. Định nghĩa chính xác điều kiện tĩnh

Một điều kiện được gọi là **tĩnh** khi kết quả chọn có thể được xác định từ **thông tin không thay đổi giữa các lần xuất hiện của điểm đang xét**, trong phạm vi mô hình và cấu hình hiện hành.

Ví dụ mô tả khái niệm:

~~~text
S(j) =
    kind(j) = METHOD_EXECUTION
    AND declaredMethodName(j) = "pay"
    AND declaredParameterTypes(j) = (String, int)
~~~

Nếu cơ chế biết trước loại join point và chữ ký khai báo, nó có thể kết luận rằng những điểm không thuộc `pay(String,int)` **không bao giờ được chọn bởi phần này**.

Những đặc tính thường dùng cho static matching khi mô hình AOP hỗ trợ:

- Loại join point: method execution, method call, constructor execution, field access…
- Tên phương thức, lớp khai báo và chữ ký tham số.
- Phạm vi kiểu hoặc package theo cấu trúc đã biết.
- Modifier khai báo như public/private/static/final khi có ý nghĩa trong ngôn ngữ biểu thức.
- Metadata khai báo được công cụ đọc trong giai đoạn phù hợp, ví dụ annotation.
- Vị trí mã/call site đối với những mô hình có khả năng xác định chúng.

**Lưu ý quan trọng:** “Có thể xác định bằng thông tin cấu trúc” **không đồng nghĩa** “bắt buộc được đánh giá khi biên dịch”. Công cụ có thể lấy thông tin ấy lúc biên dịch, lúc nạp lớp hoặc ở thời điểm thiết lập một cơ chế can thiệp. Thời điểm **đánh giá** khác với **loại thông tin mà điều kiện cần**.

### 3.1. Khi nào thông tin trông có vẻ tĩnh nhưng chưa đủ?

Một biểu thức “đối tượng đích có runtime type là `PremiumPaymentService`” phụ thuộc kiểu thực tế của receiver tại lần chạy nếu kiểu ấy chưa xác định. Dù tên lớp là một hằng chuỗi, bản chất phép kiểm tra vẫn có thể **động**.

Ngược lại, “phương thức được khai báo trong `PaymentService`” có thể là cấu trúc xác định ngay từ mô tả phương thức. Phân biệt **declared type** với **runtime type** giúp tránh nhầm lẫn.

## 4. Định nghĩa chính xác điều kiện cần đánh giá lúc chạy

**Điều kiện động** chỉ quyết định được khi có dữ liệu/ngữ cảnh của một **lần xuất hiện thực tế**. Ví dụ:

~~~text
D(event) =
    actualArg(event, 0) = "VIP"
    AND actualArg(event, 1) >= 100
~~~

Giá trị đối số thay đổi giữa các lời gọi nên dù cùng một phương thức, `D` có thể đúng ở sự kiện này và sai ở sự kiện khác.

Các nguồn thông tin động thường gặp khi mô hình AOP hỗ trợ:

- Giá trị tham số tại lần gọi.
- Kiểu runtime hoặc danh tính của receiver/target.
- Ngữ cảnh luồng điều khiển: join point xảy ra bên trong một chuỗi lời gọi nhất định hay không.
- Trạng thái khả dụng lúc chạy mà một điều kiện động hợp lệ được phép kiểm tra.
- Ngữ cảnh theo luồng/tác vụ, nếu công cụ có thể truyền và đọc nó ở đúng join point.

**Điều kiện động không đương nhiên được phép đọc mọi biến cục bộ** nằm bên trong thân phương thức. Mỗi mô hình join point chỉ cung cấp một tập thông tin nhất định. Ví dụ, AOP chỉ đặt điểm chặn tại lời gọi method có thể đọc những đối số mà điểm chặn nhận được; nó không tự nhìn thấy mọi biến nội bộ hoặc tất cả tác dụng phụ sau đó.

### 4.1. “Đánh giá lúc chạy” không có nghĩa “luôn luôn chậm”

Một so sánh số nguyên lúc chạy có thể rất rẻ; một phép tra cứu trạng thái phức tạp có thể đắt. Chi phí phụ thuộc biểu thức và cách triển khai, không chỉ nhãn static/dynamic. Tối ưu hợp lý bắt đầu từ **tránh kiểm tra động tại điểm đã chắc chắn không khớp theo cấu trúc**.

## 5. Mô hình toán học: lọc tĩnh rồi lọc động

Gọi:

- `J`: tập join point mà công cụ AOP có thể xét.
- `s`: một vị trí/cấu trúc điểm ứng viên.
- `e`: một lần xuất hiện động tương ứng khi chạy.
- `S(s)`: điều kiện tĩnh.
- `D(e)`: điều kiện động.
- `P(e)`: kết quả lựa chọn của pointcut đối với lần xuất hiện `e`.

Một mô hình đơn giản là:

~~~text
P(e) = S(shadow(e)) AND D(e)
~~~

Trong đó `shadow(e)` chỉ vị trí cấu trúc gắn với lần xuất hiện động `e`, khi mô hình có khái niệm tương ứng.

Nếu `S` sai, toàn bộ `P` sai **bất kể** đối số, receiver hay luồng điều khiển. Nếu `S` đúng, muốn kết luận `P` đúng còn phải đánh giá `D`.

| S (tĩnh) | D (động) | P = S AND D |
|---|---|---|
| Sai | Sai | Sai |
| Sai | Đúng theo giá trị giả định | Sai |
| Đúng | Sai | Sai |
| Đúng | Đúng | **Đúng** |

Về thực thi, với những điểm đã được loại bởi phần tĩnh, **không cần đánh giá D**. Đây là cơ sở của phép lọc hai giai đoạn.

Tất nhiên, không phải mọi pointcut có thể tách sạch thành một `S` duy nhất và một `D` duy nhất bằng cú pháp đơn giản; những biểu thức có nhiều nhánh OR, loại join point khác nhau và ngữ cảnh phức tạp có thể cần nhiều tập con hoặc trạng thái trung gian.

## 6. Ba kết quả trong phân tích tĩnh: luôn đúng, luôn sai, cần kiểm tra động

Một cách mô tả tối ưu hữu ích là phân tích một **join point shadow** và trả về một trong ba trạng thái:

1. **NEVER / luôn sai:** chắc chắn điểm đó không khớp pointcut; không cần chèn logic advice.
2. **ALWAYS / luôn đúng:** theo thông tin và hợp đồng đang xét, điểm đó luôn khớp; không cần điều kiện động cho chính pointcut đó.
3. **MAYBE / phụ thuộc lúc chạy:** có khả năng khớp, cần sinh hoặc đặt một phép kiểm tra động.

Ví dụ:

~~~text
Pointcut: execution(pay(String,int)) AND tier == "VIP"

Một vị trí refund(String,int):
    structural match = NEVER

Một vị trí pay(String,int):
    structural match = MAYBE
    vì tier của từng lần chạy chưa được biết
~~~

Với pointcut chỉ là `execution(pay(String,int))`, cùng vị trí `pay` có thể được kết luận **ALWAYS** về phần phù hợp của pointcut, trong phạm vi mô hình xác định.

Tên **ALWAYS / NEVER / MAYBE** là cách giải thích kết quả phân tích; không nhất thiết là enum hay API công khai của công cụ. Cũng cần phân biệt kết quả *pointcut có thể khớp* với việc advice **thật sự chạy**: mã có thể không bao giờ được thực thi trong ca thử, nằm ngoài phạm vi weaving hoặc bị ràng buộc bởi đường can thiệp.

## 7. Ví dụ Java 17: cùng phương thức, kết quả lựa chọn khác nhau từng lần gọi

Chương trình sau **mô phỏng** tách điều kiện tĩnh/động bằng `java.lang.reflect.Proxy`. Nó sử dụng Java chuẩn để cho thấy:

- **Kiểm tra tĩnh một lần trong bước chuẩn bị**: phương thức giao diện có dấu hiệu `@Observed`, tên là `pay` và có hai tham số với đúng kiểu.
- **Kiểm tra động ở mỗi lời gọi đủ điều kiện tĩnh**: tier là `"VIP"`, amount từ 100 trở lên.
- **Phần can thiệp** chỉ in before/after cho lời gọi thỏa cả hai điều kiện.

**Giới hạn cần nhớ:** Đây là ví dụ trực quan về điều kiện lựa chọn; Java Dynamic Proxy chỉ quan sát các lời gọi **đi qua proxy** của giao diện. Nó không phải một hệ AOP tự động weaving mọi loại join point. Phần `after` trong chương trình chỉ chạy sau khi lời gọi đích hoàn tất bình thường.

Lưu file **`StaticDynamicPointcutDemo.java`**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

public class StaticDynamicPointcutDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Observed {}

    public interface PaymentService {
        @Observed
        String pay(String tier, int amount);

        String status(String code);
    }

    public static class PaymentImpl implements PaymentService {
        @Override
        public String pay(String tier, int amount) {
            System.out.println("business pay " + tier + " " + amount);
            return "receipt-" + tier + "-" + amount;
        }

        @Override
        public String status(String code) {
            System.out.println("business status " + code);
            return "status-" + code;
        }
    }

    // Phần cấu trúc: có thể chuẩn bị trước các lần gọi.
    static boolean staticMatch(Method method) {
        return method.isAnnotationPresent(Observed.class)
                && method.getName().equals("pay")
                && method.getReturnType() == String.class
                && method.getParameterCount() == 2
                && method.getParameterTypes()[0] == String.class
                && method.getParameterTypes()[1] == int.class;
    }

    // Phần động: giá trị có thể đổi ở từng lần gọi.
    static boolean dynamicMatch(Object[] values) {
        return values != null
                && values.length == 2
                && "VIP".equals(values[0])
                && values[1] instanceof Integer amount
                && amount >= 100;
    }

    static Object invoke(Method method, Object target, Object[] values)
            throws Throwable {
        try {
            return method.invoke(target, values);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    public static void main(String[] args) throws Exception {
        Method pay = PaymentService.class.getMethod(
                "pay", String.class, int.class);
        Method status = PaymentService.class.getMethod(
                "status", String.class);

        Map<Method, Boolean> staticMatches = Map.of(
                pay, staticMatch(pay),
                status, staticMatch(status)
        );

        System.out.println("static candidates: pay="
                + staticMatches.get(pay)
                + ", status=" + staticMatches.get(status));

        PaymentService target = new PaymentImpl();
        PaymentService proxy = (PaymentService) Proxy.newProxyInstance(
                PaymentService.class.getClassLoader(),
                new Class<?>[] {PaymentService.class},
                (proxyObject, method, values) -> {
                    boolean staticOk = staticMatches.getOrDefault(
                            method, false);
                    boolean dynamicOk = staticOk && dynamicMatch(values);

                    System.out.println("[pointcut] " + method.getName()
                            + " static=" + (staticOk ? "Y" : "N")
                            + " dynamic=" + (staticOk
                                    ? (dynamicOk ? "Y" : "N")
                                    : "SKIP"));

                    if (dynamicOk) {
                        System.out.println("[advice] before "
                                + method.getName());
                    }

                    Object result = invoke(method, target, values);

                    if (dynamicOk) {
                        System.out.println("[advice] after "
                                + method.getName());
                    }
                    return result;
                }
        );

        System.out.println(proxy.pay("VIP", 120));
        System.out.println(proxy.pay("VIP", 20));
        System.out.println(proxy.pay("NORMAL", 150));
        System.out.println(proxy.status("A-1"));
    }
}
~~~

Biên dịch và chạy trên JDK 17+:

~~~bash
javac StaticDynamicPointcutDemo.java
java StaticDynamicPointcutDemo
~~~

**Stdout chính xác:**

~~~text
static candidates: pay=true, status=false
[pointcut] pay static=Y dynamic=Y
[advice] before pay
business pay VIP 120
[advice] after pay
receipt-VIP-120
[pointcut] pay static=Y dynamic=N
business pay VIP 20
receipt-VIP-20
[pointcut] pay static=Y dynamic=N
business pay NORMAL 150
receipt-NORMAL-150
[pointcut] status static=N dynamic=SKIP
business status A-1
status-A-1
~~~

### 7.1. Đọc kết quả theo hai giai đoạn

| Lần gọi qua proxy | Phần tĩnh | Phần động | Advice | Vì sao? |
|---|---|---|---|---|
| `pay("VIP",120)` | Đúng | Đúng | **Có** | Đúng chữ ký/annotation và giá trị |
| `pay("VIP",20)` | Đúng | Sai | Không | Số tiền dưới ngưỡng |
| `pay("NORMAL",150)` | Đúng | Sai | Không | tier không phải VIP |
| `status("A-1")` | Sai | **Không đánh giá** | Không | Không thuộc cấu trúc được chọn |

Lời gọi `status` được hiển thị `dynamic=SKIP` để nhấn mạnh rằng **khi phần tĩnh chắc chắn sai, phần động không cần chạy**. Tuy nhiên, mã nghiệp vụ của `status` vẫn chạy bình thường. Điều kiện pointcut quyết định có gắn hành vi bổ sung hay không; nó không quyết định liệu hoạt động nghiệp vụ có được thực thi, trừ khi một advice khác có chủ đích làm thay đổi luồng điều khiển.

### 7.2. Annotation được đọc ở đâu?

Trong ví dụ, `@Observed` nằm ở phương thức **giao diện** và `Method` mà proxy dùng cũng thuộc giao diện. `@Retention(RUNTIME)` làm annotation có thể được reflection đọc lúc chạy.

Nếu đặt annotation **chỉ trên phương thức của lớp triển khai** mà vẫn dùng `Method` từ giao diện, kết quả `isAnnotationPresent` có thể khác. Một công cụ AOP khác có thể quy định lookup metadata theo lớp đích, interface, method được resolve hoặc chuỗi kế thừa. Đó là vấn đề **nguồn thông tin cấu trúc**, không có nghĩa annotation đột nhiên trở thành điều kiện phụ thuộc giá trị tham số.

### 7.3. Bảng tĩnh không làm biến mất mọi kiểm tra lúc chạy

`Map<Method, Boolean>` được lập trước các lời gọi để minh họa quá trình **chuẩn bị tập ứng viên theo cấu trúc**. Handler vẫn cần tìm cờ ứng viên mỗi lần lời gọi đi qua proxy, rồi kiểm tra đối số cho các lời gọi đủ điều kiện. Cơ chế weaving thực tế có thể tạo mã khác, phân giải/caching ở thời điểm khác hoặc loại hẳn các điểm không cần chặn.

Đừng đồng nhất cách tổ chức `Map` ở Java thuần với thuật toán bắt buộc của mọi bộ máy AOP.

## 8. Tại sao phải phân biệt “điều kiện động” với “advice”?

Một số người đặt kiểm tra giá trị bên trong advice:

~~~text
Pointcut rộng: chọn mọi lần thực thi pay(...)
Advice:
    nếu tier = "VIP" và amount >= 100:
        ghi đo lường
~~~

Một cách khác là mô tả điều kiện giá trị như một phần của pointcut (nếu ngôn ngữ/cơ chế AOP hỗ trợ), để chỉ chọn những lần cần chạy advice.

**Sự khác biệt về thiết kế:**

- **Pointcut động:** thể hiện *khi nào một join point đủ điều kiện để advice tham gia*.
- **Điều kiện trong advice:** thể hiện *hành vi bên trong advice cần thực hiện gì*, sau khi advice đã được kích hoạt bởi pointcut.

Hai cách có thể cùng tạo một dòng log ở ví dụ đơn giản nhưng **không mặc nhiên tương đương** về thứ tự, kiểm soát luồng, giá trị trả về, ngoại lệ, chi phí hoặc khả năng phối hợp với nhiều advice.

Không nên đẩy mọi quy tắc nghiệp vụ vào pointcut. Nếu điều kiện gắn với quyết định nghiệp vụ sâu bên trong, tốt hơn nên thiết kế một ranh giới API/chính sách nghiệp vụ rõ ràng rồi lựa chọn AOP ở ranh giới thực sự xuyên suốt.

## 9. Điều kiện cấu trúc nào hay được xem là tĩnh?

### 9.1. Tên phương thức và chữ ký

Ví dụ mô tả:

~~~text
execution of pay(String, int)
~~~

Khi cơ chế có loại join point method execution và biết chữ ký khai báo, phần lựa chọn có thể được quyết định theo cấu trúc. Với overload như `pay(String)` và `pay(String,int)`, hai chữ ký khác nhau; cần tránh wildcard quá rộng vô tình bao phủ cả hai.

Trong Java, generic type arguments chịu **type erasure**. Một biểu thức muốn chọn dựa trên `List<String>` đối lập `List<Integer>` cần kiểm tra metadata/cơ chế thực tế; không được giả định mỗi loại generic đều tạo một chữ ký runtime độc lập.

### 9.2. Lớp, interface, package và modifier

“Phương thức được khai báo trong package X” thường là điều kiện cấu trúc; “receiver thật lúc chạy có runtime type Y” có thể cần thông tin động. Một cơ chế weaving có thể suy ra một số trường hợp từ cấu trúc để tránh phép kiểm tra runtime; nhưng không được suy ngược rằng mọi điều kiện liên quan kiểu đều tĩnh.

Modifier `static` trong Java là metadata của một method; kiểm tra **method có khai báo static hay không** có thể là điều kiện tĩnh. Điều này khác hoàn toàn với nhãn “static pointcut matching”.

### 9.3. Annotation/metadata

Một dấu hiệu khai báo có thể là điều kiện cấu trúc nếu công cụ kiểm tra trên phần tử đã xác định (class/method/field). Phải làm rõ **vị trí khai báo**, thời điểm metadata có thể truy xuất, chính sách kế thừa annotation và việc weave những lớp liên quan.

Không phải mọi annotation đều tồn tại ở runtime. Một số được dùng ở giai đoạn nguồn hoặc class file theo retention của Java; khả năng nhìn thấy qua reflection thông thường khác với khả năng phân tích bytecode.

## 10. Điều kiện động nào thường cần kiểm tra theo từng lần xuất hiện?

### 10.1. Giá trị đối số

Cùng `pay(String,int)`, lời gọi `pay("VIP",120)` và `pay("NORMAL",120)` chỉ khác dữ liệu đầu vào. Nếu mục tiêu là chọn **VIP**, pointcut phải dùng thông tin giá trị ở lần chạy hoặc ranh giới khác thể hiện rõ phân loại đó.

### 10.2. Kiểu đối tượng đích thực tế

Một biến kiểu interface có thể tham chiếu nhiều lớp triển khai. Nếu pointcut phân biệt theo **runtime receiver type**, nó có thể cần kiểm tra đối tượng của lần thực thi, trong phạm vi mô hình hỗ trợ `this`, `target` hoặc khái niệm tương ứng.

Cần phân biệt kiểm tra **kiểu khai báo của phương thức**, **kiểu runtime của đối tượng** và **kiểu tham số thực tế**. Ba thông tin không nhất thiết trùng nhau.

### 10.3. Luồng điều khiển

Một điều kiện như “chỉ khi lời gọi xảy ra **trong luồng điều khiển của một thao tác cấp cao**” phụ thuộc ngữ cảnh tại thời điểm join point xuất hiện. Những mô hình AOP có hỗ trợ loại điều kiện này có thể phải theo dõi trạng thái ngữ cảnh hoặc chuỗi thực thi.

Một lời gọi thực hiện trên thread khác hoặc công việc bất đồng bộ **không mặc nhiên vẫn nằm trong cùng ngữ cảnh**. Cách truyền dữ liệu liên luồng là một thiết kế riêng, không thể tự suy ra từ biểu thức pointcut.

### 10.4. Trạng thái bên ngoài

Điều kiện đọc trạng thái thay đổi của một cấu hình, đối tượng hoặc thành phần ngoài có thể là động. Tuy nhiên, việc nhúng một lời gọi I/O chậm hoặc có tác dụng phụ vào điều kiện chọn cần được cân nhắc nghiêm túc. Nhiều ngôn ngữ pointcut không cho phép tùy ý gọi mã như vậy; nếu có, chi phí, exception và tính quyết định cần được làm rõ.

## 11. Điều kiện tĩnh và điều kiện động có thể kết hợp bằng logic

Xét các predicate:

~~~text
S1 = methodName = "pay"
S2 = declaredReturnType = String

D1 = tier = "VIP"
D2 = amount >= 100

P = (S1 AND S2) AND (D1 AND D2)
~~~

Điểm khớp khi thỏa **toàn bộ** điều kiện. Nếu thêm ngoại lệ:

~~~text
E = isInternalCall

P = (S1 AND S2) AND D1 AND D2 AND NOT E
~~~

Thì ngay cả lời gọi VIP đạt ngưỡng cũng không được chọn nếu thuộc ngoại lệ E.

Có thể dùng OR để cho phép nhiều nhóm:

~~~text
P = (S_pay AND D_vip) OR (S_refund AND D_highRisk)
~~~

Không được biến biểu thức này thành:

~~~text
P_wrong = (S_pay OR S_refund) AND (D_vip OR D_highRisk)
~~~

Hai biểu thức **khác nhau**: dạng sai cho phép kết hợp chéo `S_pay` với `D_highRisk` hoặc `S_refund` với `D_vip`, dù chính sách ban đầu không định làm vậy.

Dấu ngoặc cho thấy **nhóm điều kiện động nào thuộc nhóm cấu trúc nào**. Trong các ngôn ngữ pointcut có toán tử khác nhau, cần kiểm tra cú pháp và độ ưu tiên của công cụ.

## 12. Static matching không nhất thiết xảy ra trước runtime

Có ba trục phân loại dễ bị trộn lẫn:

1. **Loại thông tin:** cấu trúc không phụ thuộc lần chạy hay ngữ cảnh động của lần chạy?
2. **Thời điểm phân tích:** biên dịch, xử lý bytecode, nạp lớp, cấu hình runtime hay lúc lời gọi đầu tiên xuất hiện?
3. **Cơ chế thực thi:** weaving vào mã, lớp trung gian, interceptor, công cụ instrumentation hoặc cơ chế khác?

Một điều kiện chỉ dùng tên phương thức vẫn có thể được kiểm tra tại runtime bởi một implementation đơn giản. Nó **vẫn là điều kiện tĩnh theo nội dung**. Ngược lại, một điều kiện phụ thuộc giá trị `amount` thường cần dữ liệu tại lần thực thi, dù điểm can thiệp đã được chuẩn bị từ trước.

### 12.1. Weaving lúc biên dịch

Một hệ AOP có thể phân tích thông tin cấu trúc và lựa chọn các join point shadow ngay khi xây dựng mã. Nếu còn phần dynamic matching, mã sinh ra có thể chứa kiểm tra lúc chạy.

### 12.2. Weaving lúc nạp lớp

Một cơ chế khác có thể xác định cấu trúc khi class được nạp và chèn kiểm tra động vào các điểm phù hợp. Kết quả logic tương tự, thời điểm xử lý cấu trúc khác.

### 12.3. Proxy/interceptor lúc chạy

Một cơ chế trung gian có thể kiểm tra chữ ký khi tạo proxy hoặc lần đầu xử lý lời gọi, rồi cache kết quả. Nếu mỗi lần gọi cần xét đối số thực tế, kiểm tra động vẫn phải xảy ra ở những lần thích hợp.

**Không đồng nhất tất cả các cách này:** một loại join point tồn tại trong công cụ weaving mã chưa chắc được proxy interface hỗ trợ.

## 13. Kết quả ba trạng thái và tối ưu hóa điểm can thiệp

Giả sử pointcut có biểu thức:

~~~text
(methodName = "pay") AND (tier = "VIP")
~~~

Với từng phương thức, một bộ phân tích có thể quyết định:

| Vị trí cấu trúc | Phân tích sơ bộ | Điều cần làm lúc chạy |
|---|---|---|
| `status(String)` | Chắc chắn không khớp | Không cần kiểm tra tier |
| `pay(String,int)` | Có thể khớp | Kiểm tra tier từng lần |
| `pay(...)` với pointcut chỉ dựa trên tên | Cấu trúc đủ để khẳng định khớp theo phần lựa chọn | Không cần điều kiện tier |

Tối ưu không chỉ giảm phép so sánh. Ở một số cơ chế weaving, nếu biết một vị trí **không bao giờ** khớp thì có thể không cần sinh điểm chặn ở đó; nếu biết **luôn khớp** thì có thể bỏ phép kiểm tra tương ứng. Khi tồn tại điều kiện động, chi phí được dành cho các ứng viên thực sự có khả năng khớp.

Tuy nhiên, không nên tự khẳng định công cụ nào cũng thực hiện tối ưu giống nhau. Cần xem đặc tả hoặc kiểm chứng nếu hiệu năng là yêu cầu quan trọng.

## 14. Những giới hạn của dynamic matching

### 14.1. Thông tin có sẵn tại join point

Một điều kiện đòi hỏi “số tiền cuối cùng sau toàn bộ nghiệp vụ” thường không thể suy ra từ một join point đặt **trước khi phương thức bắt đầu** nếu số tiền được tính sau đó. Để chọn theo **kết quả** hoặc diễn biến bên trong, phải kiểm tra mô hình có hỗ trợ điều kiện phù hợp tại thời điểm phù hợp hay không, hoặc thay đổi ranh giới quan sát.

### 14.2. Đọc trạng thái biến đổi

Một đối tượng có thể thay đổi giữa thời điểm điều kiện chọn được kiểm tra và thời điểm advice hoàn tất. Vì thế, việc một dynamic pointcut khớp tại thời điểm bắt đầu không bảo đảm trạng thái vẫn như vậy suốt lời gọi. Nếu cần quy tắc nhất quán, phải thiết kế hợp đồng chụp trạng thái, khóa hoặc kiểm tra nghiệp vụ ở nơi sở hữu bất biến.

### 14.3. Tác dụng phụ khi đánh giá predicate

Điều kiện chọn nên dễ dự đoán. Nếu predicate có tác dụng phụ, gọi mạng, ghi cơ sở dữ liệu hoặc ném ngoại lệ, việc **lựa chọn có chạy advice hay không** cũng biến thành nguồn thay đổi trạng thái và lỗi. Nhiều ngôn ngữ pointcut cố ý giới hạn điều có thể kiểm tra. Khi có khả năng đặt điều kiện tùy ý, nên giữ phép đánh giá thuần, rẻ và rõ hợp đồng.

### 14.4. `null`, boxing và kiểu đối số

Trong Java, giá trị `null` và primitive boxing có thể ảnh hưởng đến logic điều kiện lúc chạy. Ở ví dụ Java 17, `amount` được truyền đến proxy như `Integer` do signature nguyên thủy `int`, và việc so sánh dùng pattern `instanceof Integer`. Điều kiện `"VIP".equals(values[0])` an toàn khi đối số đầu có thể `null`.

Một DSL pointcut có thể có ngữ nghĩa so khớp kiểu/generic riêng. Không sao chép cú pháp kiểm tra Java thuần thành cú pháp của mọi công cụ AOP.

## 15. Join point model, pointcut và phạm vi weaving là các tầng khác nhau

Một điều kiện static/dynamic hoàn toàn đúng **vẫn có thể không kích hoạt advice**.

Ví dụ cơ chế AOP đang dùng chỉ hỗ trợ method invocation qua một proxy giao diện. Khi đó:

~~~text
client --> proxy --> target.pay("VIP", 120)
             |
             +-- cơ chế proxy có thể kiểm tra điều kiện

client ------------> target.pay("VIP", 120)
                            |
                            +-- gọi thẳng target, không đi qua proxy
~~~

Hai lời gọi có cùng method và đối số. Nếu đánh giá `S AND D` riêng lẻ, cả hai đều đúng. Nhưng chỉ lời gọi qua proxy được **cơ chế minh họa** chặn.

Để phân tích hiện tượng này, cần phân biệt:

- **Join point model:** loại điểm như call, execution, field access, constructor… được công cụ hỗ trợ.
- **Pointcut:** điểm nào được chọn trong mô hình.
- **Weaving scope:** mã/lớp/vùng nào đã được công cụ xử lý.
- **Runtime path:** đường thực thi có đi qua cơ chế can thiệp và phát sinh điểm tương ứng hay không.

Sửa dynamic predicate không giải quyết được một đường gọi vốn không đi qua proxy hoặc không thuộc phạm vi weaving.

## 16. Những lỗi thường gặp khi nhận diện điều kiện tĩnh

### 16.1. Nhầm “static matching” với static method

Một **instance method** có thể được chọn hoàn toàn từ tên và chữ ký, tức dùng static matching. Một **static method** vẫn có thể chứa điều kiện cần đánh giá lúc chạy khi mô hình có loại join point thích hợp, ví dụ dữ liệu đối số.

Hai khái niệm độc lập.

### 16.2. Nhầm kiểu khai báo với kiểu runtime

Điều kiện “parameter declared type = `Object`” có thể kiểm tra từ chữ ký. Điều kiện “giá trị đối số lúc chạy là một `String`” không thể suy ra chỉ từ chữ ký `Object`.

### 16.3. Nhầm tên method với ý nghĩa nghiệp vụ

Pointcut tĩnh `name startsWith "save"` có thể chọn cả `saveDraft`, `saveDiagnosticData`, hoặc bỏ sót phương thức `persistOrder`. Điều kiện tĩnh đúng cú pháp không đồng nghĩa chính sách bao phủ đúng về ngữ nghĩa.

### 16.4. Nhầm điểm được nhận diện với điểm xảy ra

Phân tích tĩnh có thể xác định một join point shadow, nhưng nhánh mã chứa nó **không nhất thiết được thực thi** trong ca thử. Không thấy advice vì không phát sinh sự kiện là điều khác với static match sai.

## 17. Những lỗi thường gặp khi viết điều kiện động

### 17.1. Giữ kết quả động trong cache quá lâu

Sai lầm:

~~~text
Lần đầu pay("VIP",120) → match = true
Ghi nhớ match=true cho method pay
Lần sau pay("NORMAL",20) → dùng lại match=true
~~~

Phần **tĩnh** có thể cache theo phương thức hoặc vị trí mã khi thông tin cấu trúc phù hợp. Phần **động** phụ thuộc các giá trị từng lần gọi nên không được tái sử dụng như hằng bất biến chỉ vì method giống nhau.

Nếu có memoization cho predicate động, phải định nghĩa khóa, tính bất biến và vòng đời dữ liệu chính xác; đa số trường hợp không nên tự đưa thêm một bộ cache như vậy.

### 17.2. Điều kiện động quá đắt

Đọc cấu hình từ mạng tại mỗi lời gọi, tra cứu dữ liệu từ xa hoặc thực hiện regex phức tạp trên dữ liệu rất dài có thể gây chi phí lớn hơn cả nghiệp vụ được quan sát. Nên thu hẹp ứng viên bằng điều kiện cấu trúc, dùng ngữ cảnh đã có và đo thực tế.

### 17.3. Sai thời điểm lấy ngữ cảnh

Nếu chính sách cần chỉ đo khi tác vụ thực sự hoàn thành, kiểm tra động lúc phương thức **lập lịch tác vụ** trả về không phản ánh kết quả sau cùng. Cần xác định đúng join point của sự kiện cần quan sát.

### 17.4. Dynamic pointcut trở thành quy tắc nghiệp vụ bí mật

Một điều kiện phức tạp điều khiển “cho phép giao dịch hay không” có thể không nên nằm ẩn trong advice/pointcut nếu đó là bất biến cốt lõi của nghiệp vụ. AOP thích hợp với mối quan tâm xuyên suốt có ranh giới rõ; kết quả nghiệp vụ quan trọng cần được biểu diễn và kiểm thử tại thành phần sở hữu chính sách.

## 18. Trường hợp nâng cao: condition theo luồng điều khiển

Một điều kiện chọn kiểu “chỉ chọn join point `charge` nếu nó xảy ra trong chuỗi xử lý `checkout`” thường phụ thuộc **runtime control flow**. Mỗi lời gọi `charge` có thể đến từ `checkout` hoặc `batchSettlement`; cùng chữ ký nhưng ngữ cảnh khác.

Pseudocode độc lập công cụ:

~~~text
S = execution(charge(...))
D = withinControlFlow(checkout(...))

P = S AND D
~~~

Nếu công cụ hỗ trợ điều kiện luồng điều khiển, nó có thể theo dõi phạm vi gọi đang hoạt động, thực thi stack hoặc cơ chế ngữ cảnh tương ứng. Nhưng không phải mọi AOP đều hỗ trợ `withinControlFlow`, và cú pháp trên **chỉ là mô tả**.

**Lưu ý về luồng khác:** Khi `checkout` gửi một tác vụ để chạy về sau ở thread khác, quan hệ “được khởi phát từ checkout” và “đang nằm trên stack/luồng điều khiển của checkout” không tự đồng nghĩa. Cần có định nghĩa rõ về lan truyền ngữ cảnh nếu muốn chọn theo nguyên nhân logic xuyên thread.

## 19. Điều kiện tĩnh/động và thứ tự nhiều aspect

Một join point có thể được chọn bởi nhiều pointcut, trong đó có pointcut tĩnh và pointcut động. Ví dụ:

~~~text
P_metrics = execution(pay(...))
P_audit   = execution(pay(...)) AND tier == "VIP"
~~~

Một lần `pay("VIP",120)` có thể khớp cả hai; `pay("NORMAL",120)` chỉ khớp `P_metrics`.

Việc `P_audit` phụ thuộc động **không tự quyết định thứ tự advice** giữa metrics và audit. Thứ tự/chồng lớp advice là vấn đề khác, có thể ảnh hưởng phép đo thời gian, ngoại lệ và số lần phần gốc được gọi.

Vì thế, khi thiết kế, nên tài liệu hóa riêng:

1. **Điểm nào mỗi aspect chọn?**
2. **Advice nào chạy ở từng điểm?**
3. **Nếu nhiều advice cùng chạy, thứ tự và hợp đồng kết quả thế nào?**

## 20. Kiểm thử đúng hai loại điều kiện

Một bộ kiểm thử nên tách kết quả **chọn về logic** và **quan sát được trong runtime**.

### 20.1. Ma trận kiểm thử logic

Với `S = method pay(String,int) có dấu hiệu Observed`, `D = tier VIP AND amount >= 100`:

| Tình huống | S | D | P |
|---|---|---|---|
| `pay("VIP",120)` | Đúng | Đúng | **Đúng** |
| `pay("VIP",20)` | Đúng | Sai | Sai |
| `pay("NORMAL",150)` | Đúng | Sai | Sai |
| `pay(null,150)` | Đúng | Sai | Sai |
| `status("A-1")` | Sai | Không cần xét | Sai |

Kiểm thử nên xác minh điều kiện cấu trúc áp dụng đúng phương thức/interface và điều kiện động thay đổi theo từng lần gọi.

### 20.2. Ma trận kiểm thử phạm vi can thiệp

Ngay cả khi P đúng, cần kiểm tra:

- Lời gọi đi qua proxy/interceptor khi cơ chế yêu cầu.
- Lớp/method nằm trong weaving scope thực tế.
- Loại join point tương ứng được mô hình hỗ trợ.
- Điều kiện động dùng đúng dữ liệu của **lần thực thi hiện tại**.
- Advice ghi đúng và không làm sai giá trị trả về/ngoại lệ.
- Trường hợp lỗi của phần nghiệp vụ có được xử lý đúng theo loại advice đã chọn.

**Hai tầng kiểm chứng không được trộn:** một unit test cho `dynamicMatch` trả true không chứng minh toàn bộ luồng weaving sẽ kích hoạt advice.

## 21. Quy trình thiết kế pointcut có điều kiện tĩnh và động

1. **Viết mục tiêu chính sách:** “Đo thời gian lời thực thi `pay` với tier VIP và số tiền đạt ngưỡng”.
2. **Xác định join point:** đang chọn method **call**, method **execution**, hay loại khác? Công cụ có hỗ trợ không?
3. **Chỉ ra phần cấu trúc:** tên, chữ ký, package, metadata, loại khai báo nào thể hiện miền ứng viên?
4. **Chỉ ra phần động:** đối số, runtime type, luồng điều khiển hoặc ngữ cảnh nào có thể thay đổi từng lần?
5. **Ghép logic với dấu ngoặc:** đặc biệt khi có nhiều nhóm `OR` và ngoại lệ `NOT`.
6. **Đặt giả định về ngữ cảnh:** nơi đọc đối số, đối tượng/metadata nào, thời điểm nào.
7. **Kiểm tra hiệu năng:** loại sớm các điểm tĩnh chắc chắn sai; tránh predicate động đắt hoặc có tác dụng phụ.
8. **Lập bảng ca dương/âm:** thêm overload, `null`, tên gần giống, dữ liệu biên và nhiều lần gọi liên tiếp.
9. **Xác minh weaving/runtime path:** gọi qua ranh giới đúng, xác nhận advice được kích hoạt chính xác.
10. **Kiểm thử tính đúng đắn của nghiệp vụ:** cùng dữ liệu, logic nghiệp vụ trả kết quả/ngoại lệ đúng; chính sách can thiệp không làm thay đổi số lần xử lý nếu không có chủ đích.

Những bước này giúp pointcut vừa dễ đọc, vừa tránh lỗi “cùng một method nên chắc cùng kết quả khớp” trong hệ thống có dữ liệu động.

## 22. Câu hỏi tự kiểm tra

### Câu 1: Phân loại

Trong các điều kiện sau, điều kiện nào **có thể tĩnh theo thông tin sử dụng**, điều kiện nào **cần dữ liệu runtime**?

A. Tên phương thức khai báo là `calculate`.

B. Đối số thứ nhất ở lần gọi hiện tại lớn hơn 100.

C. Phương thức có annotation khai báo được công cụ nhìn thấy.

D. Receiver thực tế ở lần gọi là một subtype nhất định, trong trường hợp declared type chưa đủ khẳng định.

**Đáp án:** A và C có thể quyết định từ cấu trúc/metadata; B cần dữ liệu lúc chạy; D có thể cần runtime type. Thời điểm công cụ thực hiện phép kiểm tra cấu trúc vẫn phụ thuộc triển khai.

### Câu 2: Cùng một phương thức

Pointcut chọn `pay(String,int)` với tier VIP và amount từ 100. Có ba lời gọi `pay("VIP",120)`, `pay("VIP",30)`, `pay("NORMAL",120)`. Có bao nhiêu lần khớp?

**Đáp án:** một lần, nếu cả ba lời gọi đi tới cùng loại join point hợp lệ. Phần cấu trúc đều đúng, phần dữ liệu chỉ đúng ở lần đầu.

### Câu 3: Cache sai chỗ

Một lập trình viên lưu kết quả `tier == "VIP"` gắn với `Method pay` sau lời gọi đầu. Vì sao đó là lỗi?

**Đáp án:** tier là giá trị **mỗi lần gọi**, không phải metadata cố định của Method. Cache theo method làm nhầm các lần gọi có dữ liệu khác nhau.

### Câu 4: Static pointcut có nghĩa là static method?

**Đáp án:** không. Điều kiện tĩnh nói đến **cách quyết định lựa chọn** dựa trên cấu trúc. Một instance method vẫn có điều kiện chọn tĩnh; một static method vẫn có thể được quan sát theo điều kiện động nếu mô hình hỗ trợ join point tương ứng.

### Câu 5: Đúng pointcut nhưng thiếu advice

Một lần `pay("VIP",120)` thỏa tất cả điều kiện, nhưng lời gọi đi thẳng đến đối tượng đích trong ví dụ proxy. Advice có chắc chạy không?

**Đáp án:** không. **Runtime path** không đi qua proxy, nên cơ chế proxy không quan sát lời gọi. Đây không phải lỗi của phần điều kiện động.

### Câu 6: Ranh giới bất đồng bộ

Một phương thức `submitAsync()` trả về đối tượng đại diện tác vụ. Pointcut chọn method execution và đọc trạng thái “đang chạy” lúc method trả về. Điều đó có đo được trạng thái tác vụ lúc hoàn tất không?

**Đáp án:** không mặc nhiên. Phải xác định join point/sự kiện hoàn tất và cách lấy ngữ cảnh tại thời điểm đó.

### Câu 7: Dấu ngoặc OR

Hai chính sách lựa chọn `pay` khi VIP và `refund` khi highRisk. Vì sao `(pay OR refund) AND (VIP OR highRisk)` có thể sai?

**Đáp án:** biểu thức cho phép hai tổ hợp chéo không được yêu cầu. Phải giữ nhóm tương ứng: `(pay AND VIP) OR (refund AND highRisk)`.

## 23. Ghi nhớ

- **Điều kiện tĩnh** dùng thông tin như loại join point, tên/chữ ký khai báo, phạm vi mã hoặc metadata, có thể quyết định độc lập với giá trị của từng lần xuất hiện.
- **Điều kiện động** dùng ngữ cảnh thực tế: đối số, receiver, runtime type, luồng điều khiển hoặc trạng thái phù hợp; cùng một phương thức có thể khớp ở lần này nhưng không khớp ở lần khác.
- **Static matching khác static method**, và “tĩnh” **không bắt buộc đồng nghĩa** “đánh giá ở compile time”.
- Bộ máy AOP có thể **loại sớm điểm chắc chắn sai**, **chấp nhận điểm luôn đúng** hoặc **đặt kiểm tra động cho điểm có thể khớp**; mức tối ưu cụ thể phụ thuộc cơ chế.
- Cần tách **đúng về biểu thức pointcut** khỏi **được weaving và phát sinh qua đúng đường runtime**.
- Không cache kết quả động như hằng theo method; cần kiểm tra thời điểm, phạm vi, dữ liệu `null` và ngữ cảnh bất đồng bộ.
- Một pointcut tốt nêu rõ cả **miền cấu trúc được chọn** và **điều kiện của từng lần thực thi**, có kiểm thử dương/âm và không làm mất hợp đồng nghiệp vụ.

Câu hỏi quyết định khi nhìn một pointcut là: **“Phần nào biết được trước khi nhìn dữ liệu của lần chạy, phần nào chỉ có thể biết khi join point thực sự xuất hiện, và cơ chế AOP có quan sát được lần xuất hiện đó không?”**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contextual-pointcuts-006">Ràng buộc biến ngữ cảnh cho advice sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Ràng buộc biến ngữ cảnh cho advice sử dụng trong AOP

Khi một pointcut chọn được các điểm can thiệp (*join points*), advice thường cần biết **điểm nào đang chạy, ai là đối tượng liên quan, đối số nào được truyền và kết quả hoặc lỗi là gì**. Ví dụ, advice kiểm toán một thao tác gửi hàng cần lấy mã đơn và số lượng từ lời gọi hiện tại; advice đo đạc cần phân biệt lần chạy thành công với lần ném ngoại lệ. **Ràng buộc biến ngữ cảnh (context binding)** là cách nối những dữ kiện hiện diện ở điểm can thiệp với các **tham số có tên và kiểu xác định** mà advice có thể sử dụng.

Ý chính: **pointcut quyết định tập điểm được chọn; binding đưa dữ liệu từ một điểm đã chọn vào advice**. Chúng liên quan chặt chẽ nhưng không đồng nhất. Một biểu thức pointcut chỉ nêu tên phương thức có thể chọn đúng vị trí nhưng chưa cung cấp đối số cần dùng; ngược lại, ràng buộc kiểu/đối số có thể đồng thời thu hẹp điều kiện khớp. Mô hình và cú pháp ràng buộc phụ thuộc công nghệ AOP, loại join point và cơ chế weaving. Không phải hệ AOP nào cũng hỗ trợ mọi dạng ngữ cảnh.

Bài tập trung vào tư duy AOP nói chung, với ví dụ Java 17 mô phỏng việc chuyển dữ liệu vào advice qua một bộ chặn lời gọi giao diện và các ví dụ ký hiệu riêng cho một ngôn ngữ pointcut cụ thể.

## 1. Mục tiêu học tập

Sau bài, bạn có thể:

1. Phân biệt **dữ liệu ngữ cảnh**, **biến được ràng buộc**, **tham số advice** và **điều kiện lọc pointcut**.
2. Liệt kê những dữ kiện thường có thể ràng buộc: đối số, đối tượng đang chạy, đối tượng đích, join point, metadata, giá trị trả về, ngoại lệ, annotation hoặc trạng thái điều khiển nếu mô hình hỗ trợ.
3. Giải thích cách ánh xạ tên biến và kiểu dữ liệu từ pointcut sang advice, tránh nhầm theo thứ tự, overload và kiểu runtime.
4. Nêu được **thời điểm** dữ liệu khả dụng: trước lời gọi, sau khi thành công, khi thất bại hoặc bao quanh thao tác.
5. Thiết kế advice chỉ quan sát mà giữ nguyên kết quả/ngoại lệ; hiểu các nguy cơ khi sửa đối số hoặc gọi tiếp nhiều lần.
6. Phân biệt ràng buộc ngữ cảnh với lưu ngữ cảnh dùng chung qua trường tĩnh, `ThreadLocal` hay trạng thái toàn cục.
7. Kiểm thử binding trên nhiều trường hợp, gồm `null`, primitive/boxing, overload, lỗi, đa luồng và đường bỏ qua cơ chế AOP.

## 2. Nhìn vấn đề qua một ví dụ

Giả sử ứng dụng có phương thức:

```java
Receipt submit(String orderId, int quantity)
```

Đây là **chữ ký minh họa**, chưa phải mã biên dịch độc lập. Một chính sách ghi nhận hoạt động cần các dữ kiện:

- **Mã đơn** để liên hệ bản ghi với nghiệp vụ.
- **Số lượng** để áp quy tắc lấy mẫu hoặc ghi thông số thống kê, khi phù hợp.
- **Phương thức đang thực thi** để phân biệt `submit` với `cancel`.
- **Đích thực hiện** để biết thành phần nào xử lý.
- **Kết quả hoặc lỗi** để phân loại trạng thái thao tác.

Nếu advice dùng một biến `orderId` có giá trị của đối số đầu tiên và `quantity` có giá trị của đối số thứ hai, ta đã **ràng buộc** hai dữ kiện tại join point vào hai tham số cục bộ của advice. Khi lần gọi tiếp theo là `submit("O-2", 3)`, giá trị truyền vào advice phải thuộc lần gọi mới, không lấy nhầm dữ liệu lần gọi trước.

### 2.1. Dòng chảy khái niệm

```text
Lời gọi submit("O-2", 3)
             |
             v
[Điểm can thiệp được pointcut lựa chọn]
             |
             +-- Phương thức: submit(String, int)
             +-- Đối số 0: "O-2"
             +-- Đối số 1: 3
             +-- Đối tượng đích: OrderService instance
             |
             v
[Liên kết context -> tham số advice]
             |
             +-- orderId  = "O-2"  (String)
             +-- quantity = 3      (int)
             +-- method   = metadata về join point
             |
             v
[Advice sử dụng các giá trị đã ràng buộc]
             |
             v
[Tiếp tục thao tác chính / xử lý kết quả hoặc lỗi]
```

Mô hình trên giả định cơ chế đang dùng **có thể cung cấp** dữ liệu tương ứng. Một bộ chặn chỉ có tên phương thức nhưng không có giá trị đối số sẽ không tự nhiên cấp đủ dữ kiện cho phép ràng buộc.

### 2.2. Bốn thành phần cần tách bạch

| Khái niệm | Vai trò | Ví dụ |
| --- | --- | --- |
| **Join point** | Điểm trong luồng thực thi có thể được can thiệp | Một lần thực thi `submit(String,int)` |
| **Pointcut** | Quy tắc chọn những join point phù hợp | Các thao tác `submit` của thành phần đặt hàng |
| **Binding** | Ánh xạ dữ kiện ở điểm đã chọn vào biến có tên và kiểu | `orderId` nhận đối số thứ nhất |
| **Advice** | Hành vi sử dụng dữ kiện đã ràng buộc | In thông tin đo đạc, ghi kết quả |

Một pointcut có thể dùng điều kiện “đối số thứ nhất phải là đối tượng thuộc kiểu X” vừa để **lọc** vừa để **ràng buộc giá trị**; nhưng điều kiện ấy cần được hiểu theo ngữ nghĩa thực tế của công nghệ (khớp chữ ký tĩnh hay kiểm tra kiểu đối tượng tại thời gian chạy).

## 3. Những loại dữ liệu ngữ cảnh thường gặp

Các công nghệ AOP khác nhau ở phạm vi hỗ trợ. Bảng là tập **khả năng thường gặp**, không phải cam kết một bộ weaving duy nhất luôn cung cấp hết.

| Dữ kiện | Ý nghĩa | Điểm cần lưu ý |
| --- | --- | --- |
| **Arguments (đối số)** | Giá trị truyền vào lời gọi/điểm thực thi | Có thể `null`, boxed primitive hoặc đối tượng có thể thay đổi |
| **Join point signature** | Tên, kiểu khai báo, danh sách tham số, loại join point | Metadata tĩnh khác giá trị đối số ở lần chạy cụ thể |
| **Current context / this** | Đối tượng liên quan tới điểm thực thi ở phía ngữ cảnh hiện tại | Không nhất thiết là cùng đối tượng đích |
| **Target** | Đối tượng nhận/thực hiện thao tác chính, nếu điểm nối có target | Không phải mọi join point đều có target |
| **Return value** | Giá trị hoàn tất khi thành công | Không có ở nhánh lỗi; `void` không có giá trị trả về hữu ích |
| **Thrown exception** | Ngoại lệ từ đường thực thi thất bại | Không có ở nhánh thành công |
| **Annotation/metadata** | Dấu đánh dấu trên lớp/phương thức/tham số tùy mô hình | Phải biết metadata nằm ở interface, implementation hay nơi khác |
| **Control-flow context** | Quan hệ đang nằm trong luồng gọi của một điểm khác, nếu được hỗ trợ | Có thể tăng chi phí; không tự truyền qua ranh giới bất đồng bộ |
| **Execution data** | Thời điểm, mã tương quan, thông tin tracing truyền từ ngoài | Không nhất thiết là dữ kiện built-in của pointcut |

Cần phân biệt hai nguồn dữ liệu:

- **Ngữ cảnh join point do cơ chế AOP cung cấp:** ví dụ đối số, phương thức, target và kết quả.
- **Ngữ cảnh ứng dụng do người lập trình tự truyền vào hoặc tra cứu:** ví dụ danh tính người dùng đã xác minh, transaction ID, trace ID. Đây không phải thông tin tự nhiên xuất hiện trong mọi join point.

## 4. Ràng buộc đối số (argument binding)

Đối số là trường hợp phổ biến và trực quan nhất. Với thao tác:

```java
void transfer(String from, String to, long amount)
```

Advice có thể cần `from`, `to` và `amount`. Một cơ chế hỗ trợ binding cho phép ánh xạ từng dữ kiện vào biến tham số advice.

### 4.1. Ánh xạ theo số lượng, vị trí và kiểu

Nếu advice kỳ vọng:

```text
audit(String source, String destination, long amount)
```

thì ràng buộc cần xác định rõ `source` ứng với tham số đầu, `destination` ứng với tham số thứ hai, `amount` ứng với tham số thứ ba. Không thể suy từ **tên** `source` hay `destination` mà tự biết vị trí chính xác của đối số trong phương thức đích.

Một cách nhìn:

```text
Đích:    transfer( String from, String to, long amount )
Vị trí:            [0]          [1]         [2]
Binding:           |            |           |
Advice:    audit( String source, String dest, long amount )
```

Mỗi hệ pointcut có cú pháp khác nhau để mô tả phép ánh xạ ấy. Một số buộc khai báo tham số của named pointcut rồi chuyển tiếp vào advice; một số cung cấp mảng đối số để advice đọc và ép kiểu. Dù hình thức nào, **nhầm thứ tự có thể tạo lỗi nghiệp vụ mà compiler không phát hiện** nếu hai vị trí cùng kiểu.

### 4.2. Kiểu khai báo và kiểu giá trị runtime

Xét hai overload:

```java
void accept(String value);
void accept(Object value);
```

Lời gọi `accept((Object) "ABC")` chọn overload `accept(Object)` dù giá trị runtime là `String`. Một pointcut chọn **chữ ký khai báo `accept(String)`** không khớp lời gọi này. Ngược lại, một điều kiện theo **kiểu đối số runtime `String`** có thể khớp, tùy ngôn ngữ pointcut.

Nếu mục tiêu là chọn đúng overload, hãy dùng điều kiện chữ ký thích hợp. Nếu mục tiêu là “mọi lời gọi có đối số đầu tiên là một đối tượng String”, điều kiện runtime có thể là điều cần thiết. Hai yêu cầu không tương đương.

### 4.3. `null` và kiểm tra kiểu

Một phép kiểm tra kiểu runtime như `arg instanceof String` trả `false` khi `arg == null`. Vì thế không thể coi ràng buộc dữ liệu `null` là một đối tượng `String` runtime hợp lệ theo `instanceof`.

Ở phía Java, lời gọi `accept(null)` trên cặp overload `String` và `Object` chọn `String` vì cụ thể hơn; nhưng với hai overload `accept(String)` và `accept(Integer)`, `accept(null)` là mơ hồ trừ khi ép kiểu. Phân biệt **lựa chọn overload ở compiler** với **điều kiện binding lúc thực thi** giúp tránh suy luận sai.

### 4.4. Primitive, boxing và varargs

Một phương thức nhận `int count`, nhưng API reflection động như `Method.invoke` hoặc mảng `Object[]` có thể biểu diễn giá trị `int` bằng đối tượng `Integer`. Việc **boxing** này không thay đổi chữ ký đã khai báo: `run(int)` và `run(Integer)` vẫn là hai overload khác nhau.

Varargs như `log(String... messages)` được khai báo ở mức bytecode như một tham số `String[]`. Một lời gọi nguồn `log("a", "b")` không có nghĩa advice nhận hai tham số riêng ở chữ ký runtime. Khi binding cần biết cơ chế trình bày đối số theo **phương thức khai báo** hay **biểu thức lời gọi phía người gọi**.

### 4.5. Mảng, đối tượng mutable và snapshot

Nếu advice nhận `List<Item>` hoặc một đối tượng mutable, nó thường nhận **tham chiếu đến đối tượng**, không tự nhận một bản sao bất biến. Người gọi hoặc luồng khác có thể thay đổi đối tượng trong khi advice đang sử dụng.

Nếu phải ghi lại trạng thái phục vụ kiểm toán, hãy quyết định dữ liệu nào cần sao chụp, cách loại bỏ thông tin nhạy cảm và thời điểm snapshot phù hợp. Binding một biến vào đối tượng **không tự bảo đảm dữ liệu vẫn giữ nguyên** tới lúc bản ghi được xử lý.

## 5. Ràng buộc `this`, `target` và metadata của join point

Đôi khi đối số chưa đủ để phân biệt ngữ cảnh. Một aspect muốn lấy thành phần xử lý hoặc tên điểm can thiệp.

### 5.1. `this` và `target` không nhất thiết giống nhau

Trong những mô hình AOP phân biệt hai khái niệm:

- **`this`** gắn với đối tượng ở ngữ cảnh đang thực thi join point, tùy loại điểm nối.
- **`target`** gắn với đối tượng nhận thao tác chính mà điểm nối đang nói đến, nếu tồn tại.

Tại một điểm thực thi phương thức instance thông thường trong đối tượng đích, hai đối tượng có thể trùng nhau. Tại điểm **lời gọi phương thức**, đối tượng của phía người gọi và đối tượng nhận lời gọi có thể khác. Một mô hình AOP dựa trên proxy có thể quy định khác biệt riêng giữa proxy và đích.

Ví dụ mã trong `Checkout` gọi `gateway.charge(...)`:

```text
checkout.perform()
    |
    +-- gọi ---> paymentGateway.charge(...)
         ^
         |
       điểm call có ngữ cảnh mã phía người gọi,
       còn target là đối tượng nhận lời gọi
```

Không nên mặc định `this` luôn là proxy hoặc luôn là target trên mọi công nghệ, loại join point và kiểu phương thức. Static join point cũng có thể không có đối tượng nhận phù hợp để ràng buộc.

### 5.2. Metadata tĩnh khác dữ liệu từng lần gọi

Thông tin **tĩnh** như tên phương thức, kiểu khai báo, số tham số hoặc vị trí mã thường được mô tả bằng metadata của join point. Thông tin **động** như mã đơn, giá trị số lượng hoặc target instance xuất hiện theo từng lần thực thi.

Ràng buộc một `Method`, một mô tả chữ ký hoặc một đối tượng `JoinPoint` (trong công nghệ hỗ trợ) giúp advice truy cập metadata. Tuy nhiên, *metadata hiện diện* không chứng minh **đường can thiệp thật sự chạy** hoặc cách ánh xạ kiểu từ pointcut sang tham số đúng.

### 5.3. Phân biệt trạng thái đối tượng và ngữ cảnh lời gọi

Nếu advice cần tên loại hàng, nên cân nhắc ràng buộc **tham số của lời gọi**, thay vì ép advice tự gọi thêm getter trên target. Một getter có thể gây tác dụng phụ, phát sinh join point khác hoặc đọc trạng thái vừa bị luồng khác thay đổi.

Tận dụng dữ liệu pointcut cung cấp khi đủ; chỉ truy vấn thêm khi hợp đồng ngữ cảnh yêu cầu và đã tính đến khả năng recursion, đồng thời và bảo mật.

## 6. Ràng buộc dữ liệu kết quả và lỗi

Dữ liệu có thể ràng buộc **phụ thuộc giai đoạn lời gọi**. Không nên giả định trước khi gọi đích đã có kết quả, hoặc khi thao tác lỗi vẫn có một giá trị trả về thành công.

| Giai đoạn advice | Dữ liệu thường có thể dùng | Dữ liệu chưa/không có |
| --- | --- | --- |
| **Before** | Đối số, target, chữ ký, metadata phù hợp | Giá trị trả về của lần thực thi chưa diễn ra |
| **After returning** | Đối số và **giá trị hoàn tất thành công** | Ngoại lệ của đường thất bại đó |
| **After throwing** | Đối số và **ngoại lệ đã ném** | Giá trị trả về thành công |
| **After/finally** | Một số metadata và dữ liệu ngữ cảnh, tùy khả năng công cụ | Không mặc định có cả return và error cùng lúc |
| **Around** | Dữ liệu trước khi tiếp tục, và kết quả/lỗi sau `proceed` nếu thực thi tiếp | Kết quả không tồn tại nếu bỏ qua đích hoặc chưa chạy |

### 6.1. Ràng buộc giá trị trả về

Một advice thực thi sau khi `lookup(id)` trả kết quả có thể cần kiểu của kết quả để ghi trạng thái hoặc đo kích thước. Điểm cần xác định:

- Advice có nhận **kết quả trả về dưới kiểu tĩnh cụ thể** hay một kiểu tổng quát không?
- Nếu kết quả là `null`, điều kiện ràng buộc theo kiểu runtime có còn khớp không?
- Với phương thức `void`, cơ chế biểu diễn sự kiện hoàn tất thành công ra sao?
- Với generics, kiểu ở runtime có bị erasure ảnh hưởng không?

Trong nhiều công nghệ, cơ chế binding kết quả thành công được khai báo riêng với cơ chế lấy đối số. Không nên coi mọi dữ kiện trong `args` cũng có mặt trong `result`.

### 6.2. Ràng buộc ngoại lệ

Một advice theo nhánh lỗi cần biết đối tượng lỗi gốc. Khi reflection dùng `Method.invoke`, ngoại lệ của phương thức đích thường được bọc trong `InvocationTargetException`; muốn hiển thị/tiếp tục lỗi đích phải bóc `getCause()` đúng cách.

Advice thuần quan sát nên **không nuốt ngoại lệ**, không đổi thành kết quả thành công và không tự tạo một loại lỗi khác chỉ do việc ghi log. Nếu chính sách thực sự cần chuyển đổi lỗi, đó là thay đổi ngữ nghĩa có chủ đích và phải được kiểm thử.

### 6.3. `finally` và kết quả

Khối `finally` trong Java chạy dù lời gọi thành công hay thất bại (bỏ qua những trường hợp bất thường như kết thúc tiến trình). Nó thích hợp để ghi kết thúc hoặc thu dọn trạng thái. Nhưng `finally` không có nghĩa **luôn có kết quả trả về**; kết quả đã tính được ở đường thành công khác dữ kiện lỗi ở đường thất bại.

Cũng phải tránh ném lỗi mới trong `finally` khiến ngoại lệ nghiệp vụ ban đầu bị thay thế. Khía cạnh chỉ dùng theo dõi nên giảm tối đa khả năng phát sinh lỗi ở đường dọn dẹp.

## 7. Binding trong pointcut có tên và lời gọi advice

Nhiều ngôn ngữ AOP cho phép khai báo **pointcut có tham số**. Người viết pointcut mô tả dữ kiện lấy từ join point và khai báo tham số để chuyển tiếp cho advice dùng lại.

Có thể nhìn mô hình chung như sau:

```text
pointcut submitOrder(orderCode, quantity):
    chọn execution của submit(String, int)
    AND ràng buộc đối số 0 -> orderCode
    AND ràng buộc đối số 1 -> quantity

before(orderCode, quantity):
    áp dụng ở submitOrder(orderCode, quantity)
    dùng orderCode, quantity trong thân advice
```

Đây là **giả mã mô tả ý tưởng**, không phải cú pháp của Java hoặc của mọi ngôn ngữ AOP.

### 7.1. Ví dụ ký hiệu AspectJ để nhận diện cú pháp thật

AspectJ là một ngôn ngữ/công cụ AOP cho Java có biểu thức pointcut như `execution`, `args`, `this` và `target`. Ví dụ sau là **đoạn aspect minh họa riêng cho công nghệ này** và không được biên dịch trực tiếp bằng `javac`:

```java
public aspect SubmitAuditAspect {
    pointcut selectedSubmit(String code, int count):
        execution(* example.order.OrderService.submit(String, int))
        && args(code, count);

    before(String code, int count): selectedSubmit(code, count) {
        System.out.println("order=" + code + ", count=" + count);
    }
}
```

**Đọc từng thành phần:**

1. `execution(* example.order.OrderService.submit(String, int))` chọn **loại điểm thực thi** với chữ ký mong muốn trong mẫu đã định.
2. `args(code, count)` cho cơ chế ràng buộc **các đối số** tại điểm được chọn vào hai biến tương ứng, theo kiểu và vị trí.
3. `selectedSubmit(String code, int count)` công bố hai tham số của **named pointcut** để advice có thể dùng lại.
4. `before(String code, int count): selectedSubmit(code, count)` khai báo biến trong **phạm vi advice** và dùng được trong phần thân.

Trong Java source thông thường, việc có biến tên `code` ở phương thức đích **không tự chuyển tên biến ấy sang advice**. Tên binding là khai báo trong cấu trúc pointcut/advice; dữ liệu đến từ ngữ cảnh thực thi, không phải từ phép tìm tên biến cục bộ trong bytecode.

Để chạy một ví dụ AspectJ thật cần bộ biên dịch/weaver tương ứng, đường lớp và artifact phù hợp; **mã trên được dùng để phân tích cú pháp**, không phải một chương trình Java độc lập hay đầu ra chạy bằng JDK thuần.

### 7.2. Nguồn ràng buộc ảnh hưởng tập điểm được chọn

Một biểu thức binding có thể đồng thời **ràng buộc giá trị** và **đặt điều kiện khớp**. Ví dụ điều kiện theo kiểu của đối số có thể loại trừ những điểm có giá trị không thỏa mãn, tùy cách công cụ đối chiếu kiểu tĩnh/động và giá trị `null`.

Vì vậy, nếu thêm `args(...)` vào pointcut mà số điểm khớp thay đổi, đó không nhất thiết là lỗi. Phải đọc chính xác ngữ nghĩa biểu thức. Không nên trộn một wildcard “mọi tham số” với ràng buộc “hai tham số” rồi mặc định hai điều kiện giống nhau.

### 7.3. Tham số advice phải tương thích kiểu

Nếu pointcut ràng buộc `quantity` kiểu `int`, advice không được tự giả định giá trị là `String`. Công cụ có thể kiểm tra kiểu ở thời điểm weaving/biên dịch, hoặc lỗi có thể biểu hiện khi chạy nếu cơ chế hoàn toàn động.

Nếu ràng buộc đối tượng tham chiếu kiểu cha, việc ép xuống kiểu con phải có điều kiện đúng; đừng dùng cast vô điều kiện để tránh lỗi bằng cách nuốt ngoại lệ. Với một mảng `Object[]` trong cơ chế tự viết, **bạn chịu trách nhiệm kiểm tra** số phần tử và kiểu trước khi ép.

### 7.4. Một binding có thể dùng cho nhiều advice không?

Một named pointcut định nghĩa tập điểm và hợp đồng tham số có thể được nhiều advice tái sử dụng. Tuy nhiên, mỗi advice có **biến cục bộ và lần thực thi riêng**. Khi hai lời gọi chạy đồng thời, chúng không nên chia sẻ một biến `orderCode` có thể thay đổi nằm trong field instance của aspect chỉ vì pointcut cùng tên.

Tái sử dụng **định nghĩa ràng buộc** khác với dùng chung **trạng thái runtime**.

## 8. Ví dụ Java 17 độc lập: chuyển context vào bộ chặn lời gọi

Ví dụ này dùng **JDK dynamic proxy** để mô phỏng:

- Pointcut chọn chính xác phương thức `ship(String, int)` theo chữ ký khai báo;
- Binding nhận `orderId`, `quantity`, `Method` và target cho lần gọi đó;
- Advice ghi nhận đường thành công, đường lỗi và kết thúc;
- Lời gọi ngoài pointcut hoặc bypass proxy không chạy advice.

Đây là **minh họa cơ chế ở thời gian chạy với proxy**, không phải bộ weaving can thiệp mọi loại join point. Ví dụ không sử dụng thư viện AOP ngoài JDK.

Tạo tệp `ContextBindingDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

public class ContextBindingDemo {
    interface ShippingPort {
        String ship(String orderId, int quantity);
        String inspect(String orderId);
    }

    static final class ShippingService implements ShippingPort {
        @Override
        public String ship(String orderId, int quantity) {
            if (quantity <= 0) {
                throw new IllegalArgumentException("Số lượng phải dương");
            }
            return "SHIPPED:" + orderId + ":" + quantity;
        }

        @Override
        public String inspect(String orderId) {
            return "INSPECT:" + orderId;
        }
    }

    static Object invokeTarget(Method method, Object target, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    static boolean selected(Method method) {
        return method.getName().equals("ship")
                && Arrays.equals(method.getParameterTypes(),
                        new Class<?>[] { String.class, int.class });
    }

    static ShippingPort withAdvice(ShippingPort target) {
        return (ShippingPort) Proxy.newProxyInstance(
                ShippingPort.class.getClassLoader(),
                new Class<?>[] { ShippingPort.class },
                (proxy, method, args) -> {
                    if (!selected(method)) {
                        return invokeTarget(method, target, args);
                    }

                    // Binding minh họa: chuyển đối số và metadata vào tên biến.
                    String orderId = (String) args[0];
                    int quantity = (Integer) args[1];
                    String operation = method.getName();
                    String component = target.getClass().getSimpleName();

                    System.out.println("[BEFORE] method=" + operation
                            + " order=" + orderId
                            + " qty=" + quantity
                            + " target=" + component);
                    try {
                        Object result = invokeTarget(method, target, args);
                        System.out.println("[RETURN] value=" + result);
                        return result;
                    } catch (Throwable ex) {
                        System.out.println("[THROW] type="
                                + ex.getClass().getSimpleName());
                        throw ex;
                    } finally {
                        System.out.println("[FINALLY]");
                    }
                });
    }

    public static void main(String[] args) {
        ShippingPort port = withAdvice(new ShippingService());

        System.out.println("CALLER " + port.ship("SO-7", 2));

        try {
            port.ship("SO-8", 0);
        } catch (IllegalArgumentException ex) {
            System.out.println("CALLER_ERROR " + ex.getMessage());
        }

        System.out.println("OTHER " + port.inspect("SO-7"));
        System.out.println("DIRECT "
                + new ShippingService().ship("SO-9", 1));
    }
}
```

Chạy từ thư mục chứa tệp, với JDK 17 hoặc mới hơn:

```bash
javac -encoding UTF-8 ContextBindingDemo.java
java ContextBindingDemo
```

Đầu ra dự kiến:

```text
[BEFORE] method=ship order=SO-7 qty=2 target=ShippingService
[RETURN] value=SHIPPED:SO-7:2
[FINALLY]
CALLER SHIPPED:SO-7:2
[BEFORE] method=ship order=SO-8 qty=0 target=ShippingService
[THROW] type=IllegalArgumentException
[FINALLY]
CALLER_ERROR Số lượng phải dương
OTHER INSPECT:SO-7
DIRECT SHIPPED:SO-9:1
```

### 8.1. Những biến nào được ràng buộc?

| Biến trong handler | Nguồn của dữ liệu | Ý nghĩa |
| --- | --- | --- |
| `method` | Thông tin lời gọi giao diện do proxy cung cấp | Tên, danh sách kiểu tham số của phương thức được gọi |
| `args[0]` → `orderId` | Đối số vị trí 0 của lần gọi hiện tại | Mã đơn `String` |
| `args[1]` → `quantity` | Đối số vị trí 1, boxed thành `Integer` trong `Object[]` | Số lượng dạng `int` |
| `target` → `component` | Đối tượng được proxy chuyển tiếp tới | Tên thành phần thực hiện |
| `result` | Giá trị trả về của lần gọi đích sau khi thành công | Dữ liệu phục vụ after-returning |
| `ex` | Ngoại lệ được chuyển tiếp từ đích ở nhánh lỗi | Dữ liệu phục vụ after-throwing |

Ví dụ cố ý dùng `Arrays.equals` với **chữ ký khai báo**, không lọc theo `args[0] instanceof String`; nếu nhiều overload hoặc một phương thức nhận `Object`, kiểm tra kiểu giá trị runtime có thể chọn nhầm.

### 8.2. Vì sao `quantity` phải chuyển từ `Integer`?

`Method.getParameterTypes()` trả về `int.class` cho chữ ký đã khai báo. Nhưng handler nhận đối số trong `Object[]`, không lưu primitive trực tiếp; giá trị `int` được boxing thành `Integer`. Do đó `(Integer) args[1]` rồi unboxing sang `int` là hợp lệ trong ví dụ.

Đây là cách Java reflection/proxy mang dữ liệu. Một cơ chế AOP có hỗ trợ ràng buộc trực tiếp có thể cho advice khai báo tham số `int` mà không cần tự ép phần tử mảng như trên.

### 8.3. Nhánh lỗi vẫn giữ ngoại lệ gốc

`method.invoke` bọc ngoại lệ do phương thức đích ném trong `InvocationTargetException`. Hàm `invokeTarget` bóc `getCause()` rồi ném lại. Handler in loại lỗi và chuyển tiếp, nên người gọi vẫn bắt đúng `IllegalArgumentException`.

Đây là điều quan trọng khi advice chỉ dùng theo dõi: **binding ngoại lệ nhằm đọc/ghi thông tin**, không phải tự cho phép biến một thao tác thất bại thành thành công.

### 8.4. Hai đường không có advice

`port.inspect("SO-7")` vẫn qua proxy nhưng không khớp bộ lọc `selected`; handler chỉ chuyển tiếp. `new ShippingService().ship("SO-9", 1)` gọi thẳng đối tượng gốc, nên không qua proxy. Dù tên/chữ ký `ship(String,int)` khớp, đường gọi trực tiếp không được can thiệp.

Vì thế **“binding đúng”** và **“lời gọi thực sự đi qua cơ chế áp dụng pointcut”** là hai kiểm tra độc lập.

### 8.5. Giới hạn của ví dụ

- JDK dynamic proxy chặn lời gọi **qua giao diện được đại diện**; không tự chặn constructor, đọc/ghi field, phương thức `private` hoặc mọi lời gọi nội bộ.
- `selected` chỉ là bộ lọc Java viết tay, không phải một ngôn ngữ pointcut tổng quát.
- Ví dụ ghi mã đơn và số lượng để dễ quan sát; trong hệ thống thực cần xem quy tắc lưu trữ, che chắn thông tin nhạy cảm và độ tin cậy của kiểm toán.
- Nếu advice được yêu cầu sửa đối số hoặc bỏ qua thao tác đích, phải thiết kế riêng và không thể tự suy rằng ngữ nghĩa nghiệp vụ giữ nguyên.

## 9. Around advice và hành động tiếp tục

**Around advice** có thể dùng dữ liệu ngữ cảnh trước và sau việc chuyển tiếp tới hành vi chính. Nhiều mô hình AOP gọi bước tiếp tục đó là **`proceed`**.

```text
around(context):
    validate/observe(context)
    try:
        result = proceed()
        observe(result)
        return result
    catch error:
        observe(error)
        throw error
    finally:
        cleanup()
```

Đây là **giả mã**, không phải cú pháp Java/AspectJ độc lập.

### 9.1. Ràng buộc không đồng nghĩa quyền sửa dữ liệu

Một advice được cấp `orderId` và `quantity` có thể sử dụng chúng để ghi log hoặc đưa ra điều kiện lọc. Nhưng chuyện **thay giá trị tham số truyền tới đích** là năng lực riêng của cơ chế around/proceed và cần kiểm tra hợp đồng.

Một số công nghệ cho phép `proceed` với danh sách đối số thay thế; một số giới hạn kiểu, số lượng và vị trí. Với một lời gọi đã chọn overload cụ thể, việc thay dữ liệu **không tự chuyển sang overload khác** chỉ vì kiểu runtime mới giống kiểu tham số của overload kia.

### 9.2. Số lần `proceed` thay đổi nghiệp vụ

- Không gọi `proceed`: đích có thể không chạy.
- Gọi một lần: hành vi thường được chuyển tiếp một lần, nếu không có lớp can thiệp khác thay đổi điều đó.
- Gọi nhiều lần: đích có thể được thực thi nhiều lần, kéo theo tác dụng phụ lặp.

Nếu advice chỉ đo thời gian, gọi đúng một lần và bảo toàn kết quả/lỗi là nguyên tắc tốt. Nếu advice chủ ý thử lại, cần đánh giá idempotency, trạng thái không chắc chắn và ranh giới giao dịch.

### 9.3. Binding kết quả phụ thuộc nơi đặt lời gọi

Kết quả trả về có thể khác nhau khi advice bên trong đã chuyển đổi kết quả, cache trả lời mà không thực thi đích, hoặc các aspect khác thay đổi hành vi. Một `result` ở advice ngoài cùng là kết quả từ **chuỗi bên trong** sau khi tiếp tục, không nhất thiết chỉ là giá trị do phương thức nghiệp vụ trực tiếp trả về.

Tương tự, lỗi được around advice bắt có thể phát sinh từ đích hoặc một advice khác nằm bên trong. Nếu cần phân loại nguồn lỗi, chỉ nhìn tên ngoại lệ có thể chưa đủ.

## 10. Scope của biến ràng buộc và an toàn đồng thời

Điều kiện quan trọng: ngữ cảnh của một lời gọi phải thuộc **chính lời gọi ấy**, nhất là khi nhiều luồng chạy đồng thời.

### 10.1. Tham số advice là dữ liệu cục bộ

Một advice có tham số `orderId` nhận dữ liệu theo từng lần thực thi. Hai lời gọi song song có hai lần kích hoạt advice riêng. Đừng chuyển `orderId` vào một field dùng chung rồi giả định field đó luôn chứa mã đơn của “lời gọi hiện tại”.

Ví dụ thiết kế nguy hiểm:

```java
class BadRecorder {
    String currentOrderId;

    void before(String orderId) {
        currentOrderId = orderId;
    }

    void after() {
        System.out.println(currentOrderId);
    }
}
```

Đây là **minh họa vấn đề**, không phải một chương trình chạy độc lập. Nếu cùng một `BadRecorder` được nhiều luồng dùng, lời gọi A và B có thể ghi đè `currentOrderId` của nhau. Binding đúng ở `before` không khiến field dùng chung trở nên an toàn.

### 10.2. `ThreadLocal` cũng không phải binding tự động

`ThreadLocal` có thể lưu một ít trạng thái theo luồng nhưng cần quản lý vòng đời, truyền bối cảnh và xóa dữ liệu đúng lúc, nhất là với pool thread. Khi công việc chuyển sang luồng/tác vụ bất đồng bộ, dữ liệu không nhất thiết tự truyền theo.

Một advice muốn ghi trace ID cần biết trace ID được tạo và truyền bằng cơ chế nào. **Pointcut ràng buộc đối số không tự giải quyết propagation của ngữ cảnh xuyên luồng, tiến trình hoặc hàng đợi.**

### 10.3. Dữ liệu mutable không đồng nghĩa snapshot

Nếu advice nhận một đối tượng `Order`, hai advice ở hai thời điểm có thể nhìn trạng thái khác nhau của cùng tham chiếu. Cần biết có giao dịch/khóa hoặc snapshot không, trước khi tuyên bố dữ liệu ghi là “giá trị tại thời điểm bắt đầu”.

Trong kiểm toán nghiêm ngặt, có thể phải lấy dữ liệu đã xác nhận ở ranh giới nghiệp vụ hơn là đọc đối tượng ở một join point kỹ thuật tùy ý.

## 11. Điều kiện động, độ chính xác và chi phí

Binding có thể đi kèm bộ lọc động: ví dụ chỉ theo dõi khi số lượng vượt ngưỡng, chỉ với đối tượng thuộc loại nhất định, hoặc chỉ khi mã tương quan đã tồn tại.

Tuy nhiên, quyết định theo đối số runtime cần lưu ý:

- Điều kiện có thể khác nhau cho mỗi lần chạy cùng một chữ ký.
- `null` hoặc dữ liệu sai kiểu phải có hành vi rõ ràng.
- Kiểm tra lặp ở nhiều join point nóng có thể tăng thời gian thực thi.
- Lọc dữ liệu trước khi áp advice có thể tạo **vùng quan sát trống** khi chính sách kiểm toán cần đầy đủ.
- Việc dùng dữ liệu nhạy cảm để quyết định pointcut không biện minh cho ghi dữ liệu đó vào log.

Nếu chỉ cần xác định “tất cả lần gọi của `submit(String,int)`”, một điều kiện chữ ký tĩnh có thể đủ. Chỉ thêm điều kiện động khi yêu cầu thật sự cần.

### 11.1. Binding và overload

Cùng một tên phương thức có thể có nhiều danh sách tham số. Nếu advice viết `String code = (String) args[0]` nhưng pointcut lại chọn một overload `submit(int, String)`, cast sẽ lỗi. Vì vậy:

1. Trước tiên chọn **tập chữ ký chính xác** hoặc kiểm tra cấu trúc đối số đúng như yêu cầu.
2. Sau đó ràng buộc dữ liệu theo vị trí/kiểu thích hợp.
3. Thêm kiểm thử âm cho những overload không được chọn.

Đừng chỉ dùng `method.getName().equals("submit")` rồi ép kiểu mù quáng.

### 11.2. Binding và kiểm soát quyền

Một advice nhận `accountId` không chứng minh giá trị ấy thuộc người dùng đang đăng nhập. Nếu `accountId` đến từ yêu cầu bên ngoài, nó có thể bị giả mạo. Một chính sách bảo mật cần dùng danh tính đáng tin cậy, kiểm tra sở hữu tại ranh giới phù hợp và bao phủ các đường truy cập cần thiết.

Binding là **cơ chế chuyển dữ liệu**, không phải chứng thực độ tin cậy của dữ liệu.

## 12. Kiểm thử binding có hiệu lực

| Tình huống | Kỳ vọng kiểm tra |
| --- | --- |
| Phương thức thuộc pointcut, dữ liệu hợp lệ | Advice nhận đúng vị trí và kiểu dữ liệu |
| Thay dữ liệu mỗi lần gọi | Không lấy nhầm dữ liệu của lần trước |
| Phương thức ngoài pointcut | Advice không chạy |
| Overload cùng tên nhưng khác chữ ký | Không ép kiểu sai hoặc khớp ngoài ý muốn |
| Đối số `null` | Hành vi rõ theo điều kiện khớp và hợp đồng |
| Primitive được boxing qua API động | Dữ liệu đúng dù biểu diễn dưới `Object[]` |
| Đích ném ngoại lệ | Advice nhận đúng nhánh lỗi và giữ ngoại lệ gốc |
| Đích trả `null` hoặc phương thức `void` | Không giả định luôn có kết quả đối tượng |
| Hai lời gọi đồng thời | Context của mỗi lời gọi không bị lẫn |
| `this` và `target` khác nhau | Binding đúng đối tượng theo loại join point |
| Bỏ qua proxy hoặc lớp chưa được weaving | Phát hiện được đường không áp advice |
| Có nhiều aspect lồng nhau | Thứ tự và nguồn kết quả/lỗi rõ ràng |

### 12.1. Cách chẩn đoán khi advice nhận sai giá trị

1. **Xác nhận join point:** phương thức/điểm thực thi nào thực sự được chọn?
2. **Xác nhận chữ ký:** tên, số lượng và thứ tự kiểu tham số; có overload hoặc bridge method không?
3. **Xác nhận dữ liệu được cấp:** `Method`, `args`, target hoặc metadata thuộc phía nào?
4. **Xác nhận kiểu runtime:** `null`, boxed primitive, subclass, proxy hoặc generic erasure có liên quan không?
5. **Xác nhận binding:** ánh xạ từng vị trí nguồn vào tham số advice, có nhầm hai biến cùng kiểu không?
6. **Xác nhận nhánh thực thi:** lời gọi đã hoàn tất hay ném lỗi, có around advice khác chặn hoặc biến đổi không?
7. **Xác nhận đồng thời:** dữ liệu nằm trong tham số cục bộ hay bị lưu vào trạng thái dùng chung?
8. **Xác nhận đường kết hợp:** artifact/lớp đã weaving hoặc người gọi có thật sự đi qua proxy?
9. **Kiểm thử cả ca không khớp:** advice không được áp nhầm nơi ngoài phạm vi.
10. **Kiểm tra dữ liệu nhạy cảm:** log có vô tình ghi thông tin riêng tư hay token không?

## 13. Sai lầm phổ biến

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Coi `args` là chữ ký phương thức | Lẫn kiểu runtime với kiểu khai báo | Chọn đúng chữ ký trước, ràng buộc đối số sau |
| Nhầm tên biến advice với tên tham số ở mã nguồn đích | Tưởng binding tự động theo tên | Khai báo ánh xạ thật theo cú pháp công nghệ |
| Hoán đổi hai đối số cùng kiểu | Dữ liệu ghi sai nhưng vẫn chạy | Test dữ liệu khác nhau ở từng vị trí |
| Đọc kết quả trong advice trước lời gọi | Dùng dữ liệu chưa tồn tại | Dùng after-returning hoặc after `proceed` |
| Đọc ngoại lệ như một kết quả thành công | Sai trạng thái | Phân biệt đường throw và return |
| Gọi `proceed` nhiều lần để đo thời gian | Nhân đôi tác dụng phụ | Giữ một lần chuyển tiếp nếu chỉ quan sát |
| Lưu mã yêu cầu trong field aspect dùng chung | Lẫn dữ liệu giữa luồng | Dùng biến theo lần gọi hoặc cơ chế propagation đúng |
| Tự đọc target để lấy thêm dữ liệu | Có thể tạo join point mới hoặc cạnh tranh đồng thời | Ưu tiên dữ kiện đã cấp; giới hạn lời gọi phụ |
| Dùng dữ liệu request chưa kiểm chứng để phân quyền | Có thể cho phép truy cập sai | Dùng định danh đã xác minh và ranh giới an toàn |
| Tin rằng binding đúng thì advice chắc chắn chạy | Quên phạm vi weaving/proxy | Kiểm chứng đường thực thi thực |
| Log toàn bộ đối số khi debugging | Rò rỉ dữ liệu riêng tư | Chọn trường tối thiểu và che thông tin nhạy cảm |

## 14. Bài tập vận dụng

### Bài 1 — Ràng buộc hai giá trị cùng kiểu

Cho phương thức `transfer(String from, String to, long amount)`. Advice muốn ghi `source` và `destination`. Nếu vô tình ánh xạ vị trí 0 vào `destination` và vị trí 1 vào `source`, compiler Java có bắt được không?

**Đáp án:** Không nhất thiết, vì cả hai đều là `String`. Mã có thể hợp lệ nhưng ghi thông tin đảo chiều. Cần kiểm thử với hai giá trị khác nhau, kiểm tra mapping theo vị trí và giữ hợp đồng tên biến rõ.

### Bài 2 — Kết quả và ngoại lệ

Một advice muốn ghi “SUCCESS” với mã trả về hoặc “FAILURE” với loại lỗi. Có thể lấy cùng một biến kết quả trước khi đích chạy cho cả hai nhánh không?

**Đáp án:** Không. Giá trị trả về thành công chỉ có sau khi thao tác hoàn tất bình thường; ngoại lệ chỉ có khi đường thực thi ném lỗi. Cần dùng ngữ nghĩa after-returning/after-throwing hoặc around xử lý hai nhánh sau khi chuyển tiếp.

### Bài 3 — Proxy và kiểu

Một interface có `ship(String,int)` và `ship(Object,int)`. Người gọi thực thi `ship((Object) "SO-1", 2)`. Nếu pointcut chọn chữ ký `ship(String,int)`, có khớp không?

**Đáp án:** Không: overload đã chọn có tham số đầu khai báo là `Object`, dù đối tượng runtime là `String`. Chọn theo giá trị runtime và theo chữ ký là hai yêu cầu khác nhau.

### Bài 4 — An toàn đồng thời

Một aspect dùng field `currentOrderId` để advice before ghi mã đơn, advice after đọc field đó. Khi hai luồng gọi đồng thời, kết quả có chắc đúng không?

**Đáp án:** Không. Trạng thái chung có thể bị ghi đè. Cần giữ dữ kiện theo từng lần gọi, truyền qua tham số advice hoặc dùng cơ chế bối cảnh có vòng đời và an toàn đồng thời phù hợp.

### Bài 5 — `this` và `target`

Trong một mô hình có method-call join point, mã của `Checkout` gọi `PaymentGateway.charge()`. Vì sao đối tượng `this` và target có thể khác nhau?

**Đáp án:** Ngữ cảnh phía mã phát sinh lời gọi thuộc `Checkout`, còn đối tượng nhận thao tác là `PaymentGateway`. Ngữ nghĩa chính xác phụ thuộc mô hình join point và cơ chế AOP, nên phải xác minh bằng công cụ đang dùng.

### Bài 6 — Around advice

Advice chỉ có nhiệm vụ đo thời lượng nhưng gọi `proceed` hai lần để “đo lại”. Rủi ro là gì?

**Đáp án:** Đích có thể chạy hai lần, tạo đơn hàng, chuyển tiền hoặc cập nhật trạng thái trùng lặp. Với advice chỉ quan sát, nên chuyển tiếp một lần và giữ nguyên kết quả/ngoại lệ.

### Bài 7 — Dữ liệu tin cậy

Một advice được ràng buộc với `accountId` từ lời gọi `withdraw(accountId, amount)`. Có thể mặc định rằng người dùng hiện tại sở hữu tài khoản đó không?

**Đáp án:** Không. Binding chỉ đưa giá trị được truyền vào advice; tính xác thực và quyền sở hữu cần được kiểm tra bằng danh tính và trạng thái đáng tin cậy tại ranh giới nghiệp vụ/bảo mật thích hợp.

## 15. Ghi nhớ

- **Binding** đưa dữ kiện tại join point vào các biến có tên/kiểu mà advice sử dụng; **pointcut** quyết định điểm nào được chọn.
- Những ngữ cảnh thường gặp gồm **đối số, `this`, `target`, metadata, giá trị trả về và ngoại lệ**; không phải công nghệ AOP nào cũng hỗ trợ hết.
- **Kiểu khai báo, kiểu runtime và vị trí đối số** khác nhau; phải kiểm tra overload, `null`, primitive boxing và các đối tượng có thể thay đổi.
- **Kết quả/lỗi chỉ khả dụng ở nhánh tương ứng**. Around advice phải chuyển tiếp đúng theo hợp đồng để tránh biến đổi hành vi nghiệp vụ ngoài ý định.
- Biến ràng buộc nên gắn với **mỗi lần advice chạy**, không dựa vào field dùng chung để lưu ngữ cảnh từng yêu cầu.
- Binding không tự chứng thực dữ liệu, truyền dữ liệu qua luồng bất đồng bộ hay bảo đảm mọi đường gọi đều được weaving.
- Khi thiết kế pointcut theo ngữ cảnh, hãy kiểm thử **đúng giá trị, đúng kiểu, đúng thời điểm, đúng số lần và đúng phạm vi**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contextual-pointcuts-007">Kết hợp điều kiện ngữ cảnh với phạm vi cấu trúc</a>

<details>
<summary>Xem chi tiết</summary>

# Kết hợp điều kiện ngữ cảnh với phạm vi cấu trúc trong AOP

Một **pointcut theo ngữ cảnh** có thể trả lời câu hỏi “lời gọi này xảy ra với đối tượng, tham số hoặc hoàn cảnh thực thi nào?”. Trong khi đó, điều kiện **phạm vi cấu trúc** thường trả lời “đây là loại join point nào, nằm ở lớp/gói/thành phần nào và mang chữ ký phương thức gì?”. Kết hợp hai nhóm điều kiện giúp AOP chọn **đúng nơi, đúng lần thực thi**, thay vì áp dụng hành vi cắt ngang cho mọi phương thức cùng tên hoặc mọi lời gọi thỏa một giá trị dữ liệu.

Ví dụ, giả sử muốn đo riêng những lần xử lý đơn hàng **ưu tiên cao**, có số lượng lớn, tại một phương thức nghiệp vụ cụ thể:

- Điều kiện cấu trúc: đây là lần thực thi thao tác xử lý đơn hàng, thuộc phạm vi thành phần đã chọn.
- Điều kiện ngữ cảnh: đối tượng thực tế thuộc loại nghiệp vụ cần đo, tham số mức ưu tiên là “critical” và số lượng lớn hơn hoặc bằng một ngưỡng.
- Advice: chỉ ghi số liệu cho những lần **đồng thời thỏa hai nhóm điều kiện**.

Mục tiêu của bài là hiểu cách thiết kế, kết hợp, đánh giá và kiểm thử các điều kiện này trong **lập trình hướng khía cạnh (Aspect-Oriented Programming – AOP) nói chung**. Cú pháp biểu thức và tập join point được hỗ trợ có thể khác nhau giữa các cơ chế; những đoạn giả mã dưới đây minh họa **ngữ nghĩa**, không khẳng định là cú pháp dùng chung cho mọi công cụ.

## 1. Nhắc lại các thành phần tham gia

- **Join point (điểm nối):** điểm trong quá trình chạy chương trình mà cơ chế AOP có thể gắn hành vi, chẳng hạn lời gọi hoặc lần thực thi phương thức nếu được hỗ trợ.
- **Pointcut (bộ chọn điểm):** một quy tắc xác định tập join point cần áp dụng hành vi.
- **Advice:** hành vi bổ sung chạy tại điểm được chọn, như ghi log, đo thời gian hoặc kiểm tra chính sách.
- **Context binding / exposure:** cách cung cấp cho pointcut/advice thông tin của lần thực thi, ví dụ đối tượng đích, tham số, đối tượng bên gọi hoặc ngữ cảnh luồng gọi.
- **Weaving:** cách kết hợp advice với điểm được chọn, có thể bằng biến đổi mã/bytecode, proxy hoặc kỹ thuật khác.

Một pointcut có thể chứa điều kiện dựa trên **cấu trúc tĩnh**, **ngữ cảnh runtime**, hoặc **kết hợp cả hai**. “Tĩnh” ở đây nói về dữ liệu có thể suy ra từ cấu trúc/chữ ký, không có nghĩa mọi hệ thống AOP buộc phải tính chúng tại compile time. Tương tự, một điều kiện “ngữ cảnh” không nhất thiết chỉ xét tham số: nó có thể xét kiểu của target, caller, control flow hoặc dữ liệu khác mà mô hình join point công bố.

## 2. Hai nhóm điều kiện khác nhau ở đâu?

| Tiêu chí | Điều kiện phạm vi cấu trúc | Điều kiện ngữ cảnh |
| --- | --- | --- |
| Câu hỏi chính | Join point *nằm ở đâu, thuộc loại nào?* | Lần thực thi *diễn ra với dữ liệu và hoàn cảnh nào?* |
| Dữ liệu thường dùng | Loại call/execution, gói, lớp, chữ ký, tên/kiểu tham số khai báo, phạm vi mã | Target/caller runtime, đối số thực tế, quan hệ kiểu động, ngữ cảnh lời gọi, điều kiện đánh giá lúc chạy |
| Tần suất thay đổi | Thường ổn định theo vị trí mã/chữ ký | Có thể khác ở từng lần gọi |
| Ví dụ | “Các lần thực thi phương thức execute trong nhóm xử lý giao dịch” | “Target là OrderTask và đối số level là critical” |
| Lỗi điển hình | Chọn phạm vi quá rộng, sai call/execution, bỏ sót overload | Sai đối tượng, null, dữ liệu thiếu/tin nhầm đầu vào, điều kiện thay đổi theo thời gian |
| Tác dụng khi phối hợp | Giới hạn tập điểm ứng viên | Chọn những sự kiện cần xử lý *trong* tập ứng viên |

Ví dụ một phương thức `process(String category, int count)` có thể chạy hàng nghìn lần. Về cấu trúc, những lần thực thi này đều thuộc cùng một vị trí. Nhưng ngữ cảnh mỗi lần khác nhau: có lần `category="critical"`, có lần `category="normal"`, có lần đối tượng đích là triển khai khác. Một pointcut kết hợp có thể khớp chỉ vài lần thực thi trong số đó.

### 2.1 “Kiểu” có thể là cấu trúc hoặc ngữ cảnh

Đừng suy rằng mọi kiểm tra kiểu là tĩnh. **Kiểu khai báo ở chữ ký** là thông tin cấu trúc; **kiểu runtime của target hoặc đối số** là thông tin động.

Giả sử lời gọi được biên dịch qua một biến kiểu giao diện nhưng đối tượng thật thuộc lớp `OrderTask`. Điều kiện chữ ký theo giao diện có thể khớp nhiều triển khai; điều kiện `target instanceof OrderTask` chỉ khớp một nhóm đối tượng runtime. Chúng bổ sung cho nhau, không có ý nghĩa tương đương.

## 3. Mô hình toán học: giao của hai tập điểm

Gọi:

- `J`: tập join point mà cơ chế AOP hỗ trợ xem xét.
- `S(j)`: điều kiện cấu trúc đúng cho điểm `j`.
- `C(j, ctx)`: điều kiện ngữ cảnh đúng cho điểm `j` ở ngữ cảnh `ctx` của **lần chạy hiện tại**.

Điểm được chọn khi:

~~~text
selected(j, ctx) = S(j) AND C(j, ctx)
~~~

Tập điểm **có khả năng** được chọn trước hết bị giới hạn bởi `S`; trong số đó, `C` xác định từng lần thực thi có thực sự khớp hay không.

~~~text
Tất cả join point được hỗ trợ (J)
                  |
                  v
     [Kiểm tra phạm vi cấu trúc S]
          |                  |
        false               true
          |                  |
     Không áp dụng           v
                  [Lấy/ràng buộc context]
                            |
                            v
                  [Kiểm tra điều kiện C]
                     |              |
                   false           true
                     |              |
                 Không chạy         v
                    advice      Chạy advice
~~~

Sơ đồ diễn giải logic; nó không áp đặt thứ tự máy thực hiện mọi phép kiểm tra. Một trình biên dịch/weaver có thể tối ưu biểu thức mà vẫn giữ ngữ nghĩa quan sát được. Cần phân biệt **mô tả tập điểm** với **thứ tự hoạt động vật lý** của mã instrument.

### 3.1 Vì sao nên lấy giao S AND C?

Nếu chỉ dùng `S`, chính sách có thể tác động cả các yêu cầu bình thường không cần đo. Nếu chỉ dùng `C`, điều kiện “level=critical” có thể khớp cả phương thức thuộc thành phần khác, nơi cùng tham số mang ý nghĩa khác. **Giao của hai điều kiện** vừa giới hạn vị trí vừa giới hạn tình huống.

Ví dụ:

~~~text
S = execution(các phương thức xử lý đơn hàng)
C = target thuộc OrderTask AND level == "critical"

P = S AND C
~~~

Các tên biểu thức trên là **giả mã**, không phải cú pháp AOP phổ quát.

## 4. Khi nào dùng AND, OR và NOT?

Các biểu thức pointcut phong phú có thể cho phép **hội (AND)**, **tuyển (OR)** và **phủ định (NOT)**, cũng như nhóm điều kiện bằng dấu ngoặc.

### 4.1 AND: cả hai cùng đúng

~~~text
P = StructureScope AND RuntimeCondition
~~~

Dùng khi cần chính sách ở một phạm vi cụ thể *và* một hoàn cảnh cụ thể. Ví dụ đo thời gian các thao tác thanh toán khi chúng thuộc nhóm xử lý giao dịch và đang dùng phương thức thanh toán chậm cần theo dõi.

### 4.2 OR: chọn hợp của các nhánh

~~~text
P = (OrderStructure AND OrderContext)
    OR
    (ReportStructure AND ReportContext)
~~~

Hai nhóm nghiệp vụ có thể cần chung một advice nhưng tiêu chí riêng. **Nhóm ngoặc là bắt buộc về mặt diễn giải**, nhất là khi phối hợp nhiều toán tử.

### 4.3 NOT: loại trừ có chủ đích

~~~text
P = BusinessStructure
    AND RuntimeCondition
    AND NOT InfrastructureHelperStructure
~~~

Dùng để tránh pointcut rộng vô tình chạm vào logging helper, adapter hoặc mã phụ trợ. Tuy nhiên, phủ định cần được thử kỹ: loại trừ một nhóm phương thức chỉ vì tên lớp có thể bỏ sót trường hợp nghiệp vụ hợp lệ.

### 4.4 Hai biểu thức gần giống nhưng chọn khác

~~~text
P1 = (S1 OR S2) AND C

P2 = S1 OR (S2 AND C)
~~~

Với `P1`, **mọi điểm** thuộc `S1` hoặc `S2` đều phải thỏa `C`. Với `P2`, toàn bộ `S1` được chọn **ngay cả khi `C` sai**.

Giả sử `S1` là giao dịch thanh toán, `S2` là hoàn tiền và `C` là mức ưu tiên “critical”. Nếu yêu cầu chỉ tác động các giao dịch critical thì `P2` quá rộng: nó chọn cả thanh toán bình thường. Đây là lý do phải viết **ngoặc theo ý định**, dù một ngôn ngữ pointcut có quy tắc ưu tiên toán tử được công bố.

Bảng chân trị rút gọn:

| S1 | S2 | C | (S1 OR S2) AND C | S1 OR (S2 AND C) |
| --- | --- | --- | --- | --- |
| true | false | false | false | **true** |
| true | false | true | true | true |
| false | true | false | false | false |
| false | true | true | true | true |
| false | false | true | false | false |

Khi xây pointcut cho nhiều chức năng, một bảng chân trị nhỏ thường phát hiện lỗi phạm vi tốt hơn việc đọc lướt biểu thức dài.

## 5. Chọn ngữ cảnh nào? Không phải mọi dữ liệu đều giống nhau

Điều kiện ngữ cảnh có thể dựa vào những thành phần khác nhau:

| Thành phần | Ý nghĩa thường gặp | Lưu ý |
| --- | --- | --- |
| **Target** | Đối tượng nhận hoặc thực hiện thao tác đang quan sát | Khác proxy/caller; runtime class phụ thuộc đối tượng được xét |
| **This / caller context** | Đối tượng đang thực thi mã tại một điểm thích hợp | Ở call join point có thể khác target; static context có thể không có instance |
| **Arguments** | Đối số thực tế của lần gọi | Xử lý null, boxing, kiểu khai báo so với runtime và chính sách bảo mật |
| **Control-flow context** | Điểm này có nằm trong luồng điều khiển của thao tác được chọn không? | Phụ thuộc mô hình hỗ trợ; có thể phát sinh overhead |
| **Annotation/metadata** | Dấu hiệu khai báo trên phương thức/lớp hoặc metadata của đối tượng | Annotation tĩnh không tự đồng nghĩa điều kiện runtime |
| **Custom state/context** | Correlation ID, danh tính, cờ vận hành đã được cung cấp đúng cách | Phải xét tính tin cậy, vòng đời và truyền qua luồng bất đồng bộ |

Một **kiểu tham số trong chữ ký** là thông tin cấu trúc; giá trị của tham số ở một lời gọi là ngữ cảnh. Một **annotation khai báo** có thể là bộ chọn cấu trúc; đọc đối tượng annotation của một lần thực thi, nếu cơ chế có hỗ trợ, lại có cách biểu diễn/đánh giá khác. Vì vậy cần gọi tên đúng **dữ liệu** và **thời điểm** được dùng trong từng điều kiện.

### 5.1 Control flow là ngữ cảnh thực thi

Một số mô hình AOP cho phép điều kiện “chỉ khớp khi điểm này xảy ra trong luồng điều khiển của thao tác X”. Chẳng hạn cùng `repository.save()` nhưng chỉ muốn ghi một loại trace khi được gọi trong quá trình nhập dữ liệu.

Khác với giới hạn lớp/gói, điều kiện này phụ thuộc **chuỗi lời gọi đang hoạt động**. Tuy nhiên, không mặc định rằng control flow được truyền qua tác vụ bất đồng bộ, thread mới hoặc luồng xử lý rời rạc: khái niệm “đang nằm trong cflow” phải được xác định theo mô hình và ranh giới thực thi thật sự.

### 5.2 Context được dùng để *lọc* hay *truyền cho advice*?

Hai việc khác nhau:

- **Context condition** quyết định `true/false` để chọn điểm.
- **Context binding** đặt dữ liệu như `target`, `args` hoặc id vào phạm vi mà advice có thể sử dụng.

Một pointcut có thể chọn tất cả thao tác đặt hàng theo cấu trúc rồi binding tham số `amount` để advice đo hoặc ghi log; không nhất thiết phải lọc theo `amount`. Ngược lại, nếu chỉ muốn chọn các thao tác vượt ngưỡng, một điều kiện ngữ cảnh có thể kiểm tra `amount >= threshold`.

Hãy tránh nhầm **“advice nhận được tham số”** với **“tham số đó đã được kiểm tra trong pointcut”**.

## 6. Tính tĩnh, tính động và ngữ nghĩa đánh giá

Giả sử có 100 lớp nhưng chỉ 4 lớp nằm trong cấu trúc được chọn. Cơ chế AOP có thể loại trừ nhiều vị trí không phù hợp ngay khi phân tích mã, rồi chỉ cần đánh giá điều kiện ngữ cảnh cho những điểm còn lại. Đây là một hướng tối ưu hợp lý, nhưng khả năng thực thi cụ thể phụ thuộc công cụ.

Ba mức quyết định có thể gặp:

1. **Xác định từ cấu trúc:** thông tin loại join point, chữ ký, phạm vi kiểu và một số metadata.
2. **Xác định từ đối tượng lúc chạy:** instance nào đang nhận lời gọi, kiểu runtime nào, tham số thực tế là gì.
3. **Xác định từ trạng thái động khác:** quan hệ luồng điều khiển, ngữ cảnh giao dịch hoặc trạng thái được cung cấp riêng.

Không nên tự cho phép những biểu thức điều kiện tùy ý tạo **tác dụng phụ**. Nếu một pointcut phải gọi hàm có khả năng ghi dữ liệu, lấy khóa hoặc thay trạng thái, việc tối ưu hóa/thay đổi thứ tự đánh giá có thể làm hệ thống khó dự đoán. Nên ưu tiên các điều kiện thuần túy, ngắn, có nghĩa rõ ràng.

Khi kết hợp AND, ngôn ngữ có thể công bố **short-circuit** hoặc cho phép trình triển khai bố trí lại những phép thử phù hợp để tối ưu. Không dựa vào thứ tự của các phép thử pointcut để thực hiện hành vi nghiệp vụ. Nếu một hành vi phải xảy ra theo thứ tự, hãy tổ chức nó trong advice hoặc trong cơ chế có hợp đồng thứ tự rõ ràng.

## 7. Ví dụ Java 17 chạy độc lập: ghép S AND C tại một điểm bọc

Ví dụ dưới đây dùng **dynamic proxy của Java** để minh họa nguyên tắc chọn bằng hai lớp điều kiện. Đây là chương trình chạy độc lập, **không phải cú pháp pointcut chuẩn của một bộ AOP** và chỉ can thiệp lời gọi đi qua proxy.

Ta có giao diện `Task`, hai triển khai `OrderTask` và `ReportTask`. Điều kiện cấu trúc `S` chọn phương thức `execute(String,int)` của giao diện. Điều kiện ngữ cảnh `C` yêu cầu **đích là OrderTask**, `level="critical"` và `items >= 3`. Chỉ khi **S AND C** đúng, advice mới in sự kiện trước/sau/lỗi.

Lưu thành file `ContextAndStructureDemo.java`:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

public class ContextAndStructureDemo {
    public interface Task {
        String execute(String level, int items);
        String status();
    }

    public static final class OrderTask implements Task {
        @Override
        public String execute(String level, int items) {
            System.out.println("BUSINESS order level=" + level
                    + " items=" + items);
            if (items == 9) {
                throw new IllegalArgumentException("demo rejection");
            }
            return "order:" + items;
        }

        @Override
        public String status() {
            System.out.println("BUSINESS status");
            return "OK";
        }
    }

    public static final class ReportTask implements Task {
        @Override
        public String execute(String level, int items) {
            System.out.println("BUSINESS report level=" + level
                    + " items=" + items);
            return "report:" + items;
        }

        @Override
        public String status() {
            return "OK";
        }
    }

    // S: chọn phạm vi/chữ ký cấu trúc.
    static boolean matchesStructure(Method method) {
        return method.getDeclaringClass() == Task.class
                && method.getName().equals("execute")
                && Arrays.equals(method.getParameterTypes(),
                        new Class<?>[] { String.class, int.class });
    }

    // C: kiểm tra ngữ cảnh của lời gọi hiện tại.
    static boolean matchesContext(Task target, Object[] args) {
        return target instanceof OrderTask
                && args.length == 2
                && "critical".equals(args[0])
                && args[1] instanceof Integer count
                && count >= 3;
    }

    static Object invokeOriginal(Method method, Task target, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    static Task wrap(Task target) {
        return (Task) Proxy.newProxyInstance(
                Task.class.getClassLoader(),
                new Class<?>[] { Task.class },
                (proxy, method, args) -> {
                    Object[] actualArgs =
                            args == null ? new Object[0] : args.clone();

                    // Chỉ kích hoạt advice khi S AND C đúng.
                    if (!matchesStructure(method)
                            || !matchesContext(target, actualArgs)) {
                        return invokeOriginal(method, target, actualArgs);
                    }

                    System.out.println("ADVICE before execute");
                    try {
                        Object result =
                                invokeOriginal(method, target, actualArgs);
                        System.out.println("ADVICE after " + result);
                        return result;
                    } catch (Throwable error) {
                        System.out.println("ADVICE error "
                                + error.getClass().getSimpleName());
                        throw error;
                    }
                });
    }

    public static void main(String[] args) {
        Task order = wrap(new OrderTask());
        Task report = wrap(new ReportTask());

        System.out.println("RETURN " + order.execute("critical", 3));
        System.out.println("RETURN " + order.execute("normal", 5));
        System.out.println("RETURN " + order.execute("critical", 2));
        System.out.println("RETURN " + report.execute("critical", 4));
        System.out.println("RETURN " + order.status());

        try {
            order.execute("critical", 9);
        } catch (IllegalArgumentException ex) {
            System.out.println("CALLER " + ex.getMessage());
        }
    }
}
~~~

Biên dịch và chạy trên Java 17+:

~~~bash
javac ContextAndStructureDemo.java
java ContextAndStructureDemo
~~~

Stdout:

~~~text
ADVICE before execute
BUSINESS order level=critical items=3
ADVICE after order:3
RETURN order:3
BUSINESS order level=normal items=5
RETURN order:5
BUSINESS order level=critical items=2
RETURN order:2
BUSINESS report level=critical items=4
RETURN report:4
BUSINESS status
RETURN OK
ADVICE before execute
BUSINESS order level=critical items=9
ADVICE error IllegalArgumentException
CALLER demo rejection
~~~

### 7.1 Vì sao từng lần gọi có kết quả khác nhau?

| Lời gọi | S: `execute(String,int)` | C: OrderTask, critical, items ≥ 3 | Advice chạy? |
| --- | --- | --- | --- |
| `order.execute("critical", 3)` | true | true | Có, trước và sau thành công |
| `order.execute("normal", 5)` | true | false (level) | Không |
| `order.execute("critical", 2)` | true | false (items) | Không |
| `report.execute("critical", 4)` | true | false (target) | Không |
| `order.status()` | false | Không cần xét | Không |
| `order.execute("critical", 9)` | true | true | Có, trước và khi lỗi |

Trong ví dụ, `matchesStructure` nhìn vào `Method` được proxy cung cấp: một chữ ký của giao diện `Task`. `matchesContext` nhận **target thực**, `args` của từng lần gọi. Điều kiện và advice nằm ở các hàm Java minh họa để nhìn thấy ranh giới trách nhiệm.

Khi không khớp, chương trình **vẫn gọi nghiệp vụ** qua `invokeOriginal`, chỉ bỏ qua advice quan sát. Khi nghiệp vụ ném `IllegalArgumentException`, reflection bọc trong `InvocationTargetException`; hàm chuyển tiếp tháo lớp bọc bằng `getCause()` và advice ghi nhánh `error` rồi ném tiếp lỗi gốc.

### 7.2 Giới hạn của ví dụ

- Dynamic proxy chỉ chặn các lời gọi **đi qua proxy** và giao diện được cung cấp. Gọi trực tiếp `new OrderTask().execute(...)` không kích hoạt advice tại proxy này.
- Bộ chọn trong ví dụ là các hàm Java thông thường, **không phải** một ngôn ngữ pointcut phổ quát.
- Hàm `matchesStructure` quan sát chữ ký `Method` tại proxy, **không chứng minh** mọi mô hình method-call hay method-execution AOP đều có cùng declaring class.
- `args.clone()` tạo một bản sao nông của mảng đối số; bản sao không biến đối số thành dữ liệu bất biến hoặc đáng tin.
- Ví dụ quan sát đơn giản theo `level` và `items`, không dùng chúng để đưa ra quyết định an ninh. Nếu thay bằng phân quyền thật, phải lấy chủ thể từ nguồn xác thực đáng tin, kiểm tra quyền trên tài nguyên cụ thể và bao phủ mọi đường vào cần bảo vệ.

## 8. Thiết kế biểu thức kết hợp ở mức nâng cao

### 8.1 Phân nhóm theo mục đích, đặt tên rõ

Giả sử có ba phạm vi:

~~~text
paymentOperations = cấu trúc các thao tác thanh toán
refundOperations  = cấu trúc các thao tác hoàn tiền
sensitiveContext  = ngữ cảnh có mức rủi ro cao

highRiskOperations =
    (paymentOperations OR refundOperations)
    AND sensitiveContext
~~~

Đặt tên cho các thành phần tạo ra quy tắc dễ kiểm tra hơn một biểu thức dài chứa nhiều điều kiện lặp. Nếu các điều kiện cấu trúc có phần chung, tái sử dụng phần ấy để tránh thay đổi không nhất quán.

Tuy nhiên, **tên một pointcut không thay thế hợp đồng của nó**. Khi thay phạm vi từ “thanh toán” thành “giao dịch” rộng hơn, phải kiểm tra những điểm mới được đưa vào có thực sự cần chịu advice đó hay không.

### 8.2 Cùng cấu trúc, nhiều hoàn cảnh

~~~text
P1 = ServiceExecution AND CriticalRequest
P2 = ServiceExecution AND SlowTenantContext
~~~

Một join point cấu trúc có thể được dùng cho nhiều chính sách ngữ cảnh. Nhưng `CriticalRequest` và `SlowTenantContext` có ý nghĩa khác nhau: một điều kiện liên quan yêu cầu nghiệp vụ, một điều kiện liên quan cấu hình/đặc tính vận hành. Tránh gom cả hai thành một “context quan trọng” mơ hồ nếu hành vi thay đổi độc lập.

### 8.3 Nhiều cấu trúc, chung một hoàn cảnh

~~~text
P = (CreateOrderExecution OR CancelOrderExecution)
    AND ObservedEnvironment
~~~

Khi điều kiện `ObservedEnvironment` chỉ được phép áp dụng cho **cả hai nhóm**, hãy đặt nó ngoài ngoặc bao hai điều kiện cấu trúc. Nếu đặt thành `CreateOrderExecution OR (CancelOrderExecution AND ObservedEnvironment)`, mọi lần tạo đơn đều bị áp dụng advice bất kể môi trường. Đây là lỗi rất thường gặp khi thêm `OR` vào một pointcut đang có `AND`.

### 8.4 Loại trừ lời gọi hạ tầng hoặc mã sinh

Đôi khi điểm chọn theo tên/kiểu bắt cả wrapper, helper, adapter hoặc phương thức do compiler/runtime tạo. Cần hiểu mô hình join point và đặt điều kiện loại trừ phù hợp:

~~~text
P = BusinessScope
    AND RelevantRuntimeContext
    AND NOT InternalInstrumentationScope
~~~

Mục đích là tránh việc advice gọi helper, helper lại khớp pointcut và kích hoạt advice lần nữa. Nhưng loại trừ theo tên lớp chung chung có thể bỏ sót nghiệp vụ thật. Hãy kiểm tra *điểm cụ thể* thay vì chỉ xét tên trông giống hạ tầng.

## 9. Bẫy lớn: nhầm pointcut quan sát với rào chắn bảo mật

Kết hợp `S AND C` phù hợp khi một advice **chỉ cần chạy trong một số hoàn cảnh**, như log bổ sung cho yêu cầu ưu tiên cao hoặc phép đo được lấy mẫu. Nhưng phải cẩn thận nếu advice giữ vai trò **kiểm tra bắt buộc**.

**Thiết kế nguy hiểm:** đặt `C = user has permission` trong pointcut rồi chỉ chạy advice kiểm tra quyền khi `C` đúng. Các lời gọi không đủ quyền có thể không khớp pointcut và **bỏ qua advice**, khiến nghiệp vụ chạy nếu không có rào chắn khác.

**Ý định cần thể hiện:** chọn **toàn bộ điểm cần bảo vệ** bằng phạm vi tin cậy; trong advice (hoặc ranh giới bảo vệ thích hợp) kiểm tra quyền của chủ thể đã được xác thực rồi **cho phép hoặc chặn** trước tác dụng phụ. Chỉ dùng điều kiện ngữ cảnh để thu hẹp phạm vi kiểm soát khi đã chứng minh các trường hợp bị loại trừ không cần bảo vệ.

Cũng không nên nhầm một tham số tên `role` hay `userId` với danh tính đã được xác thực. **Binding cung cấp dữ liệu; nguồn và ý nghĩa tin cậy của dữ liệu phải được bảo đảm riêng.**

## 10. Null, kiểu runtime, overload và các trường hợp biên

### 10.1 Null và kiểm tra tham số

Một giá trị `null` không có runtime class: gọi `getClass()` trên nó ném `NullPointerException`. Nếu điều kiện chỉ cần so sánh chuỗi, đặt hằng bên trái như `"critical".equals(level)` giúp xử lý null an toàn.

Nếu một pointcut muốn chọn theo **kiểu tham số được khai báo**, thông tin chữ ký có thể vẫn đầy đủ khi đối số thực tế là null. Nhưng nếu muốn chọn theo **kiểu runtime của đối số**, phải xác định quy tắc đối với null và xem mô hình AOP hỗ trợ loại kiểm tra nào.

### 10.2 Overload và kiểu khai báo

Các phương thức `process(String)` và `process(Object)` là hai overload khác nhau. Chọn theo tên đơn thuần có thể áp dụng cả hai; chọn đúng chữ ký cần xét danh sách kiểu tham số. Trình biên dịch chọn overload theo kiểu tĩnh của biểu thức đối số và quy tắc ngôn ngữ, không đơn giản dựa trên runtime class của giá trị.

### 10.3 Call, execution và đường gọi thực tế

Phạm vi cấu trúc có thể chọn **điểm gọi phương thức (call)** hoặc **lần thực thi phần thân (execution)** nếu nền tảng hỗ trợ. Hai loại điểm có thể có chữ ký/đối tượng ngữ cảnh và phạm vi quan sát khác nhau.

Khi gọi qua interface, một pointcut theo chữ ký tại call site có thể mang kiểu tĩnh khác với phần thân thực thi trong lớp con. Với cơ chế proxy, gọi nội bộ qua `this` hoặc gọi trực tiếp đối tượng gốc có thể bỏ qua lớp bọc. Do đó “điều kiện S và C đều đúng” **chưa đủ** nếu điểm đó không nằm trong tập join point có thể can thiệp.

### 10.4 Generic, bridge và synthetic

Type erasure của Java có thể tạo chữ ký bytecode khác với khai báo generic trong nguồn. Compiler đôi khi sinh bridge/synthetic method để giữ đa hình. Một bộ quét Reflection thấy hai phương thức không đồng nghĩa hệ thống AOP có hai execution join point hoặc advice chắc chắn chạy hai lần.

Khi xây điều kiện cấu trúc theo chữ ký, cần biết công cụ match mã nguồn, descriptor sau erasure, hay một tập join point đã chuẩn hóa. Đừng tạo một điều kiện AND sai chỉ vì hiểu nhầm chữ ký của bridge là chữ ký nghiệp vụ.

### 10.5 Ngữ cảnh bất đồng bộ

Ngữ cảnh nằm trong lời gọi đồng bộ có thể không tồn tại nguyên trạng khi công việc chuyển sang thread, executor, queue hoặc một bước callback khác. Nếu điều kiện yêu cầu correlation ID hay danh tính, phải có cách truyền và khôi phục phù hợp, đồng thời tránh rò dữ liệu giữa các tác vụ tái sử dụng luồng.

Nếu đo thời gian một hàm chỉ **khởi phát** tác vụ bất đồng bộ rồi trả về, advice quanh hàm đó đo thời gian khởi phát; nó không mặc nhiên đo thời gian đến khi tác vụ thực sự hoàn thành.

## 11. Hiệu năng và sự ổn định của điều kiện

Điểm chạy thường xuyên có thể làm overhead của pointcut/advice tăng đáng kể. Những nguyên tắc thực tế:

- Giữ phạm vi cấu trúc rõ ràng để giảm số điểm phải xem xét.
- Ưu tiên điều kiện ngữ cảnh thuần túy, nhanh, không có tác dụng phụ và tránh thao tác I/O hay lấy thêm khóa.
- Cẩn thận với các phép kiểm tra đòi tra cứu mạng/cơ sở dữ liệu mỗi lần gọi: chúng có thể thay đổi cả hiệu năng lẫn hành vi nghiệp vụ.
- Tránh đọc dữ liệu nhạy cảm trong điều kiện hoặc log nếu không cần.
- Không dùng trạng thái global dễ thay đổi mà không có chính sách đồng bộ; kết quả match giữa các lần gọi có thể thay đổi khó dự đoán.
- Nếu áp dụng sampling, thiết kế rõ điều kiện chọn mẫu và kiểm chứng số liệu không bị thiên lệch ngoài mong muốn.

Không nên mô tả một pointcut động là “luôn được tối ưu sau khi lọc cấu trúc”. Điều này phụ thuộc implementation. Có những phép kiểm tra tĩnh có thể được tối ưu, nhưng thứ tự và mức độ tiết kiệm thực tế cần được xác minh ở công cụ đang dùng.

## 12. Kiểm thử theo ma trận cấu trúc và ngữ cảnh

Một bộ kiểm thử tốt nên tách ít nhất bốn nhóm kết quả:

| Nhóm | S | C | Kết quả cần có |
| --- | --- | --- | --- |
| Khớp cả hai | true | true | Advice chạy đúng một lần theo chính sách |
| Chỉ khớp cấu trúc | true | false | Nghiệp vụ chạy, advice có điều kiện không chạy |
| Không khớp cấu trúc | false | Không cần xét | Advice không chạy |
| Điểm không được nền tảng quan sát | Có thể đúng về ý tưởng | Có thể đúng về dữ liệu | Không được suy rằng advice chắc chắn chạy |

Với ví dụ Java ở trên, các lời gọi `order.execute("normal",5)` và `report.execute("critical",4)` giúp tách hai nguyên nhân khiến `C` sai. `order.status()` kiểm chứng `S` sai. `order.execute("critical",9)` kiểm chứng lời gọi vừa khớp vừa ném ngoại lệ; advice phải ghi lỗi, không được ghi thành công và không được nuốt lỗi.

Những kiểm tra bổ sung cần làm với chương trình thực:

1. **Mỗi nhánh AND/OR/NOT:** lập bảng chân trị, thử cả điều kiện giao nhau và loại trừ.
2. **Kiểu và chữ ký:** interface, lớp con, override, overload, generic, giá trị null, dữ liệu không đúng kiểu.
3. **Ngữ cảnh:** target, caller, args, control flow, metadata, và thời điểm dữ liệu được binding.
4. **Phạm vi weaving:** lời gọi qua proxy, lời gọi trực tiếp, lời gọi nội bộ, tác vụ nền và các lối vào khác.
5. **Ngoại lệ:** after-success không chạy trên lỗi; after-error và finally theo đúng hợp đồng, kết quả/lỗi nghiệp vụ được giữ.
6. **Nhiều advice cùng khớp:** thứ tự can thiệp, khả năng tái nhập và tác dụng phụ phải được xác minh.
7. **An ninh:** những thao tác cần bảo vệ vẫn phải được kiểm tra trên mọi đường vào, kể cả khi điều kiện ngữ cảnh của một advice quan sát là false.

## 13. Bài tập phân tích

Một dự án muốn ghi trace cho **mọi thao tác tạo hoặc hủy đơn hàng có mức ưu tiên cao**, nhưng bỏ qua getter và phương thức hạ tầng.

Có người đề xuất:

~~~text
P = CreateOrder OR (CancelOrder AND HighPriority)
~~~

**Vấn đề:** biểu thức chọn toàn bộ `CreateOrder`, kể cả các lần không có ưu tiên cao. Nó chỉ yêu cầu `HighPriority` đối với `CancelOrder`.

Biểu thức đúng với yêu cầu là:

~~~text
P = (CreateOrder OR CancelOrder)
    AND HighPriority
    AND NOT InfrastructureHelper
~~~

Ở đây, `CreateOrder`, `CancelOrder`, `InfrastructureHelper` là các điều kiện cấu trúc; `HighPriority` là điều kiện dựa trên ngữ cảnh của lần chạy. Nếu điều kiện “ưu tiên cao” phụ thuộc vào tham số, cần ràng buộc đúng tham số và kiểm tra null/giá trị không hợp lệ. Nếu nó đến từ một trạng thái nghiệp vụ được tính trong lời gọi, cần bảo đảm điểm can thiệp có thể quan sát dữ liệu tại thời điểm điều kiện được đánh giá.

**Câu hỏi:** Nếu thao tác tạo đơn được gọi trực tiếp qua đối tượng gốc nhưng hệ thống chỉ đặt advice tại proxy, biểu thức pointcut đúng có bảo đảm ghi trace không?

**Đáp:** không. Điều kiện khớp về mặt logic không thay thế khả năng can thiệp thực tế. Cần kiểm chứng mọi đường gọi mà chính sách phải bao phủ.

**Câu hỏi:** Nếu trace chỉ được yêu cầu cho ưu tiên cao, có được dùng cùng điều kiện để bỏ qua kiểm tra quyền của ưu tiên bình thường không?

**Đáp:** không thể tự suy như vậy. Trace có thể là quan sát có điều kiện; kiểm soát quyền là chính sách bắt buộc theo phạm vi hành động và phải được thiết kế độc lập.

## 14. Ghi nhớ

Có thể tự rà soát một pointcut kết hợp bằng bốn câu hỏi:

1. **Cấu trúc:** join point nào được chọn theo loại điểm, chữ ký, lớp/gói hoặc phạm vi mã?
2. **Ngữ cảnh:** dữ liệu runtime nào phải đúng trong chính lần chạy đó? Có cần phân biệt target/caller/arguments/control flow không?
3. **Logic:** điều kiện phải là `S AND C`, `(S1 OR S2) AND C`, hay có loại trừ `NOT`? Dấu ngoặc có bảo toàn yêu cầu?
4. **Thực thi:** cơ chế AOP có quan sát được điểm này không, binding có đúng và advice có giữ nguyên kết quả/ngoại lệ không?

**Kết luận:** phạm vi cấu trúc giúp trả lời **“ở đâu có thể áp dụng?”**, điều kiện ngữ cảnh giúp trả lời **“trong lần thực thi nào thật sự áp dụng?”**. Một pointcut tốt phải diễn tả chính xác **giao hoặc hợp các tập điểm**, được kiểm thử với các trường hợp không khớp và vẫn tôn trọng phạm vi join point của nền tảng. Advice mới quyết định hành vi sau khi điểm được chọn.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contextual-pointcuts-008">Giới hạn của ngữ cảnh khi công việc chuyển sang luồng hoặc tác vụ khác</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn của ngữ cảnh khi công việc chuyển sang luồng hoặc tác vụ khác

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming – AOP)**, pointcut có thể chọn điểm can thiệp dựa vào **ngữ cảnh thực thi** (*execution context*): phương thức đang chạy, đối tượng đích, đối số, luồng điều khiển, thông tin gắn với yêu cầu hoặc trạng thái được công cụ AOP hỗ trợ. Những điều kiện này dễ hiểu trong một chuỗi lời gọi đồng bộ, nhưng phát sinh giới hạn quan trọng khi công việc **được giao cho một thread, executor, callback, future hoặc tác vụ khác**.

**Nguyên tắc trung tâm:** *Ngữ cảnh của lời gọi hiện tại không tự động trở thành ngữ cảnh của công việc chạy ở nơi khác.* Một advice nhìn thấy thông tin tại thời điểm gửi tác vụ không có nghĩa advice ở lúc tác vụ chạy cũng nhìn thấy thông tin đó. Pointcut được đánh giá ở **join point nào, tại thời điểm nào, trên luồng nào**, và dữ liệu nào **được truyền qua ranh giới**, sẽ quyết định kết quả.

Ví dụ: khi một phương thức đang xử lý yêu cầu mang mã `REQ-17`, nó gửi tác vụ tới một thread pool. Một advice ở phương thức gửi có thể thấy `REQ-17`; advice chạy trong worker thread có thể không thấy mã này, dù tác vụ vẫn thuộc cùng yêu cầu về mặt nghiệp vụ. Nếu worker thread được dùng lại, nó còn có thể chứa **ngữ cảnh cũ** nếu người viết không dọn dẹp đúng.

> Bài này phân tích AOP ở mức mô hình tổng quát, gồm cơ bản và nâng cao. Không có cơ chế truyền ngữ cảnh hay ngôn ngữ pointcut nào bắt buộc chung cho mọi công cụ AOP. Ví dụ Java 17+ minh họa giới hạn thực tế bằng API Java SE và một interceptor nhỏ, rồi chỉ rõ điều kiện nào là pointcut logic, điều kiện nào chỉ là guard trong advice.

## 1. Trước tiên: “ngữ cảnh” trong AOP là gì?

**Join point** là điểm có thể được AOP can thiệp, ví dụ một lần gọi hoặc một lần thực thi phương thức, nếu công cụ hỗ trợ. **Pointcut** chọn tập join point; **advice** thực hiện hành vi bổ sung ở đó. Khi nói “pointcut theo ngữ cảnh”, ta muốn lựa chọn dựa trên dữ liệu gắn với một thời điểm/đường thực thi, chứ không chỉ metadata tĩnh như tên lớp và chữ ký.

Có thể phân biệt các nhóm thông tin sau:

| Nhóm ngữ cảnh | Ví dụ | Thay đổi khi chuyển tác vụ? |
| --- | --- | --- |
| **Tĩnh của join point** | Chữ ký `process(String)`, kiểu khai báo | Không tự thay đổi chỉ vì đổi thread, nhưng điểm thực thi mới có thể là phương thức khác |
| **Đối tượng/phương thức** | Target, receiver, method thực chạy | Có thể giữ nếu tham chiếu được truyền; không tự bảo đảm thread safety |
| **Đối số** | `OrderCommand`, mã đơn hàng | Có thể truyền cho tác vụ; dữ liệu mutable có thể đổi trước khi worker đọc |
| **Luồng điều khiển** | Đang nằm trong chuỗi gọi của `checkout()` | Chuỗi stack đồng bộ không tự nối sang thread khác |
| **Thread-local** | Trace ID, mã tương quan, danh tính đã xác thực | Mặc định gắn với thread; thread khác không tự nhìn thấy |
| **Ngữ cảnh tác vụ** | Deadline, cancellation, request context | Chỉ có khi API/runtime truyền hoặc khôi phục có chủ đích |
| **Ngữ cảnh phân tán** | Trace headers, correlation ID trên message | Phải tuần tự hóa/truyền qua ranh giới tiến trình; cần xác thực dữ liệu tin cậy |

Cần phân biệt **thông tin truyền như đối số** với **thông tin ngầm gắn trong môi trường thực thi**. Một hàm nhận `requestId` trực tiếp vẫn có thể biết request ID trên thread khác; nhưng `ThreadLocal.get()` sẽ chỉ truy cập giá trị của thread hiện tại.

### Ngữ cảnh của lời gọi không phải “toàn bộ dữ liệu đang có trong ứng dụng”

Một aspect không tự biết mọi biến local của caller, toàn bộ stack frame hay mọi object đang tồn tại. Nó chỉ thấy những gì **loại join point, API của công cụ và cơ chế truyền ngữ cảnh** cung cấp. Ngay cả với kiểu pointcut dựa trên *control flow*, việc “đang nằm trong luồng gọi” thường có ý nghĩa với chuỗi lời gọi đồng bộ hiện tại, không tự bao trùm công việc được lên lịch cho tương lai.

## 2. Ranh giới quan trọng: call stack, thread và task

Giả sử chương trình đồng bộ:

```text
Thread A
  Controller.handle()
    -> Service.process()
       -> Repository.save()
          -> return
       -> return
    -> return
```

Nếu một cơ chế AOP hỗ trợ lựa chọn theo **luồng điều khiển động** (*control flow*), một join point bên trong `Repository.save()` có thể được xem là nằm trong chuỗi gọi phát sinh từ `Controller.handle()`, tùy semantics của công cụ.

Bây giờ thay lời gọi trực tiếp bằng gửi việc:

```text
Thread A                               Thread B
Controller.handle()
  -> Executor.submit(task)
  <- Future returned
  -> tiếp tục hoặc kết thúc             task.run()
                                          -> Service.process()
                                             -> Repository.save()
```

Giữa hai bên không có **một call stack đồng bộ liên tục**. Thread A chạy lời gọi gửi tác vụ; Thread B chạy `task.run()` tại thời điểm riêng. Dù mục đích nghiệp vụ có quan hệ nhân quả, quan hệ ấy không tự biến thành quan hệ stack của AOP.

Vì vậy, ba khái niệm cần giữ riêng:

- **Call stack:** chuỗi frame lời gọi đang hoạt động trên một thread.
- **Execution context:** dữ liệu và điều kiện quan sát được tại một join point cụ thể.
- **Causal/request context:** thông tin dùng để liên kết những công việc thuộc cùng yêu cầu, có thể vượt qua nhiều thread hoặc tiến trình nếu được truyền rõ.

Một pointcut “đang chạy bên trong chuỗi gọi X” có thể không khớp ở worker, trong khi một điều kiện “request ID đã được chuyển đến worker có tiền tố Y” vẫn có thể khớp nếu runtime AOP hỗ trợ kiểm tra request context đó.

## 3. ThreadLocal giải quyết vấn đề gì và không giải quyết điều gì?

Trong Java, `ThreadLocal<T>` cho phép mỗi thread lưu một giá trị độc lập dưới cùng một biến `ThreadLocal`. Đây là cách giữ ngữ cảnh ngầm mà code trong **cùng thread** có thể đọc mà không phải truyền qua mọi tham số.

Ví dụ khái niệm:

```java
// Mã minh họa, đặt trong một class thích hợp.
static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

TRACE_ID.set("REQ-17");
String currentTrace = TRACE_ID.get(); // "REQ-17" trên thread hiện tại.
TRACE_ID.remove();
```

Điều cần hiểu:

1. Gọi `set` trên Thread A không tự đặt giá trị cho Thread B.
2. `get` trên Thread B có thể trả `null` nếu thread B chưa có giá trị.
3. Với thread pool, **thread B được tái sử dụng** cho nhiều tác vụ. Nếu tác vụ cũ không dọn `ThreadLocal`, tác vụ mới có thể đọc nhầm thông tin cũ.
4. `remove()` xóa binding của thread hiện tại; trong một scope tạm thời có giá trị cũ thì cần **khôi phục đúng giá trị cũ**, không đơn giản xóa vô điều kiện.
5. Lưu một object mutable vào `ThreadLocal` không khiến object ấy tự nhiên thread-safe nếu nó được chia sẻ với các thread khác bằng đường khác.

Vì thế, dùng `ThreadLocal` để hỗ trợ advice không có nghĩa đã giải quyết **context propagation**. Nó mới giải quyết truy cập ngữ cảnh **bên trong một thread**.

### Một câu hỏi kiểm tra nhanh

Nếu `AuditAspect` chọn hành vi dựa trên `TRACE_ID.get() != null`, chuyện gì xảy ra khi gọi cùng phương thức đích từ thread pool? Câu trả lời không phụ thuộc chỉ vào method signature: nó phụ thuộc **thread thực thi có binding đó hay không** tại chính lúc advice được đánh giá.

## 4. Context propagation: cần truyền cái gì qua ranh giới?

**Context propagation** là thao tác **nắm bắt, vận chuyển, khôi phục và dọn dẹp** những phần ngữ cảnh cần thiết khi công việc chuyển sang một môi trường thực thi khác.

Một vòng đời an toàn thường có bốn giai đoạn:

```text
Caller thread                 Task queue                Worker thread
     |                            |                           |
  Capture                      enqueue                  Restore context
  snapshot  --------------------> task ---------------------> enter advice
     |                                                        |
 caller continues                                        run target
                                                              |
                                                        finally restore
                                                        previous context
```

Cần quyết định rõ **chụp ngữ cảnh vào thời điểm nào**: khi tạo `Runnable`, khi `submit`, khi callback được đăng ký, hay lúc một sự kiện được phát đi? Nếu giá trị ngữ cảnh thay đổi giữa các thời điểm, lựa chọn capture sẽ ảnh hưởng kết quả.

**Không nên tự động sao chép toàn bộ biến ThreadLocal của một thread**. Nhiều giá trị không thích hợp để truyền: connection, transaction, resource chỉ có hiệu lực trong một thread, dữ liệu định danh nhạy cảm hoặc đối tượng có trạng thái mutable. Thường cần **snapshot nhỏ, có chủ đích, bất biến**, chỉ mang correlation ID, trace parent, deadline hoặc các thuộc tính được phép dùng.

### Snapshot hay chia sẻ tham chiếu?

Nếu context giữ tham chiếu tới một object mutable, “capture context” có thể chỉ sao chép **tham chiếu**, không phải trạng thái. Caller sửa object sau khi gửi việc, worker có thể thấy nội dung mới hoặc tình trạng race. Một `record` bất biến chứa dữ liệu đơn giản giúp semantics dễ hiểu hơn; với object lồng nhau mutable vẫn phải tạo snapshot thực sự hoặc đảm bảo không bị sửa.

## 5. Ví dụ Java 17+ chạy được: advice mất ngữ cảnh khi vào executor

Ví dụ này mô phỏng một aspect theo ngữ cảnh: khi một lời gọi `perform(...)` đi qua proxy, handler chỉ in dòng advice nếu **ThreadLocal của thread hiện tại** chứa trace ID bắt đầu bằng `REQ-`. Ví dụ có hai đường thực thi: gửi tác vụ thẳng vào executor và gửi tác vụ đã được bọc để truyền context.

Nó sử dụng **Java SE**, không cần framework. Handler là **interception có guard theo context**, không phải một engine hỗ trợ ngôn ngữ pointcut động đầy đủ.

Lưu tệp **`AopContextHandoffDemo.java`** (UTF-8):

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AopContextHandoffDemo {
    record RequestContext(String traceId) { }

    static final ThreadLocal<RequestContext> CURRENT = new ThreadLocal<>();

    interface TaskAction {
        void perform(String label);
    }

    static final class TaskActionImpl implements TaskAction {
        @Override
        public void perform(String label) {
            System.out.println("target " + label + " " + currentTrace());
        }
    }

    static String currentTrace() {
        RequestContext context = CURRENT.get();
        return context == null ? "NONE" : context.traceId();
    }

    static void setContext(RequestContext context) {
        if (context == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(context);
        }
    }

    // Minh họa advice phụ thuộc ngữ cảnh ở chính thread thực thi.
    static TaskAction withAdvice(TaskAction target) {
        return (TaskAction) Proxy.newProxyInstance(
                TaskAction.class.getClassLoader(),
                new Class<?>[] { TaskAction.class },
                (proxy, method, args) -> {
                    RequestContext context = CURRENT.get();
                    if (method.getDeclaringClass() == TaskAction.class
                            && method.getName().equals("perform")
                            && context != null
                            && context.traceId().startsWith("REQ-")) {
                        System.out.println(
                                "advice " + context.traceId() + " " + args[0]);
                    }

                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                });
    }

    // Capture khi tạo wrapper; restore context cũ sau tác vụ.
    static Runnable carryContext(Runnable task) {
        RequestContext captured = CURRENT.get();

        return () -> {
            RequestContext previous = CURRENT.get();
            try {
                setContext(captured);
                task.run();
            } finally {
                setContext(previous);
            }
        };
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();

        try {
            setContext(new RequestContext("REQ-17"));
            TaskAction action = withAdvice(new TaskActionImpl());

            System.out.println("caller " + currentTrace());
            action.perform("direct");

            // Không truyền context: worker có ThreadLocal riêng.
            pool.submit(() -> action.perform("no-transfer")).get();

            // Wrapper capture REQ-17 trên caller, restore ở worker.
            pool.submit(carryContext(
                    () -> action.perform("propagated"))).get();

            // Worker được dùng lại, nhưng wrapper đã dọn context.
            pool.submit(() -> action.perform("after")).get();
        } finally {
            setContext(null);
            pool.shutdown();
        }
    }
}
```

Biên dịch và chạy với JDK 17 hoặc mới hơn:

```bash
javac -encoding UTF-8 AopContextHandoffDemo.java
java AopContextHandoffDemo
```

Đầu ra chuẩn:

```text
caller REQ-17
advice REQ-17 direct
target direct REQ-17
target no-transfer NONE
advice REQ-17 propagated
target propagated REQ-17
target after NONE
```

### Vì sao có đúng những dòng này?

**Lời gọi `direct`:** main thread đã được đặt `REQ-17`. Handler và target đều chạy trong main thread; handler thấy context nên in `advice`, target in cùng trace ID.

**`no-transfer`:** tác vụ được gửi sang single-thread executor. Worker không tự nhận giá trị `ThreadLocal` từ main; handler không thấy context nên không in dòng `advice`. Target in `NONE` dù caller đã có `REQ-17`.

**`propagated`:** `carryContext(...)` được **tạo trên caller**, chụp `REQ-17` vào biến `captured`. Khi worker chạy wrapper, nó đặt context này cho chính thread worker trước khi gọi target. Handler và target đều nhìn thấy `REQ-17`.

**`after`:** wrapper đã chạy `finally` và khôi phục giá trị thread-local trước đó của worker (`null` trong ví dụ). Tác vụ tiếp theo trên cùng worker không thấy ngữ cảnh cũ, nên in `NONE`.

**Vì sao đầu ra ổn định?** `newSingleThreadExecutor()` dùng một worker tại một thời điểm; mỗi `Future.get()` chờ tác vụ trước hoàn tất rồi mới gửi tác vụ tiếp theo. Không in thread ID hoặc thời gian nên không có thành phần đầu ra biến thiên.

### Mô hình pointcut nào được mô phỏng?

Bộ lọc handler có hai lớp:

1. **Tĩnh:** chỉ quan tâm phương thức `TaskAction.perform`.
2. **Động theo môi trường:** chỉ in advice khi `CURRENT.get()` trên thread đang chạy chứa trace ID hợp lệ.

Trong ví dụ, handler vẫn được vào ở `no-transfer` và `after`, nhưng guard quyết định không in. Một công cụ AOP có dynamic pointcut thật có thể quyết định có kích hoạt advice ngay khi xét điểm; các hiệu ứng và overhead cụ thể phụ thuộc engine. Ví dụ không tự biến `ThreadLocal` thành một pointcut có cú pháp chuẩn.

### Điều gì được bảo đảm, điều gì chưa?

Ví dụ bảo đảm **tại đường gọi đi qua proxy** rằng worker có context đúng khi được bọc, và wrapper khôi phục trạng thái cũ khi hoàn tất hoặc ném ngoại lệ. Nó **không** bảo đảm lời gọi trực tiếp target có advice; không truyền dữ liệu qua tiến trình; không tự truyền transaction/resource; không triển khai cancellation/deadline; và không thay thế các biện pháp kiểm tra quyền truy cập.

## 6. Vì sao phải khôi phục context cũ thay vì chỉ remove?

Một worker có thể đã có ngữ cảnh hợp lệ của một *scope bên ngoài*. Nếu wrapper luôn `remove()` trong `finally`, nó sẽ phá mất binding trước đó.

Xét lồng nhau:

```text
Worker đã có context = OUTER
  -> bước chạy tạm cần context = INNER
      -> advice/target bên trong đọc INNER
  <- kết thúc bước chạy tạm
Worker phải trở lại context = OUTER
```

Cách đúng cho một scope tạm là:

1. Lưu giá trị context cũ của thread hiện tại.
2. Đặt giá trị dành cho tác vụ hiện tại.
3. Thực thi nội dung.
4. Trong `finally`, **khôi phục giá trị cũ** (hoặc remove nếu cũ không có).

Một hệ thống thực tế còn cần quản lý scope lồng nhau, ngoại lệ và trạng thái hủy. Gọi `remove()` ở **ranh giới vòng đời cuối cùng** của request/task vẫn quan trọng để thread pool không giữ context của request đã xong.

### Rò rỉ dữ liệu giữa các tác vụ

Nếu một worker chạy request A rồi request B, nhưng context A không được dọn, pointcut dựa trên context có thể áp dụng advice với trace ID của A khi đang xử lý B. Điều này gây sai log/audit, rò dữ liệu, metric giả, hoặc đánh giá nhầm chính sách. Đây là lỗi **cross-task contamination**; nó nghiêm trọng hơn việc “thiếu trace ID”.

Đừng chỉ kiểm thử trường hợp “đã truyền được context”. Phải kiểm tra thêm “sau khi hoàn tất, worker còn giữ sai ngữ cảnh không”.

## 7. InheritableThreadLocal có tự giải quyết được không?

`InheritableThreadLocal` cho phép một **thread mới được tạo** thừa hưởng giá trị từ parent thread khi thread đó được tạo, theo cơ chế Java. Nhưng nó **không phải giải pháp truyền ngữ cảnh tổng quát cho executor**:

- Thread pool thường **tái sử dụng** worker đã được tạo trước khi yêu cầu hiện tại xuất hiện.
- Thread mới thừa hưởng giá trị ở **thời điểm tạo thread**, không tự cập nhật theo từng lần `submit`.
- Giá trị được thừa hưởng có thể là cùng tham chiếu tới object mutable nếu không có cơ chế tạo bản sao thích hợp.
- Quy tắc thừa hưởng của từng loại thread và cách tạo thread có thể có cấu hình/giới hạn khác; không dùng nó để suy đoán mọi tác vụ chắc chắn có context.
- Context được thừa hưởng không mang một ranh giới sống/chết request rõ ràng nếu thiếu cleanup.

Nếu cần propagation qua task pool, hãy dựa vào **contract truyền context ở lúc nộp và lúc chạy tác vụ**, hoặc sử dụng một API runtime có semantics context rõ ràng. Không giả định `InheritableThreadLocal` sẽ tự làm đúng với mọi thread pool.

## 8. ExecutorService: task là đơn vị công việc, thread là nơi chạy

`ExecutorService.submit(...)` nhận một `Runnable` hoặc `Callable`, sau đó lịch làm việc có thể chạy nó trên một thread khác. `Future.get()` giúp caller chờ hoàn tất, nhưng **việc chờ không truyền ThreadLocal** từ caller cho worker.

Một số điểm dễ nhầm:

- `submit` có thể trả về `Future` trước khi tác vụ chạy hoặc hoàn tất.
- Tác vụ có thể nằm trong hàng đợi, nên context ở lúc worker thực thi có thể khác trạng thái caller ở lúc gửi.
- Hai tác vụ của cùng yêu cầu có thể chạy trên hai worker khác nhau.
- Hai yêu cầu khác nhau có thể nối tiếp chạy trên cùng một worker.
- Hủy `Future` hoặc ngắt thread không tự giải quyết việc dọn mọi resource/context nếu mã thiếu `finally`.
- `execute(Runnable)` và `submit(Runnable)` có cách báo lỗi khác nhau ở caller, nhưng đều không có cam kết chung tự truyền mọi context người dùng.

Muốn instrument AOP tại **điểm gửi tác vụ**, một advice có thể bao quanh lời gọi submit và tạo wrapper mang context nếu cơ chế cho phép. Nhưng phải hiểu **điểm gửi** và **điểm chạy** khác nhau. Chỉ đo thời gian ở submit không đồng nghĩa đo thời gian chạy task; chỉ ghi log ở worker không tự liên kết nó với caller.

### Thời điểm capture quan trọng

Giả sử:

```text
t0: traceId = A
t1: tạo wrapper và capture A
t2: caller đổi traceId = B
t3: submit wrapper
t4: worker chạy wrapper
```

Nếu wrapper capture ở `t1`, worker nhìn thấy **A**, bất kể caller đang có B ở `t3`. Nếu yêu cầu là capture tại thời điểm submit, wrapper phải được tạo tương ứng ở `t3`. Không có một thời điểm “tự đúng” trong mọi ứng dụng: contract của hệ thống phải xác định tác vụ thuộc context nào.

## 9. CompletableFuture và callback: nơi chạy không luôn là caller

Với `CompletableFuture` hoặc các API bất đồng bộ tương tự, callback có thể chạy:

- Trên thread hoàn tất stage trước đó.
- Trên thread đăng ký callback trong một số tình huống.
- Trên executor đã cung cấp.
- Trên executor mặc định đối với những biến thể bất đồng bộ, theo hợp đồng API.

Do vậy, **không đặt giả định cứng** rằng callback luôn chạy trên cùng thread từng tạo future.

Ví dụ **minh họa một đoạn độc lập về ý tưởng, không phải chương trình Java hoàn chỉnh**:

```java
CompletableFuture<String> stage =
        CompletableFuture.supplyAsync(() -> "data", executor);

CompletableFuture<Integer> mapped =
        stage.thenApplyAsync(String::length, executor);
```

Ngay cả khi `supplyAsync` và `thenApplyAsync` dùng cùng executor, callback vẫn có thể thực thi ở một worker đã được dùng cho công việc khác. Context trên caller không tự động được đọc từ `ThreadLocal` của worker.

Nếu cần propagation, có thể bọc **supplier, function, consumer hoặc runnable** bằng các wrapper phù hợp có capture/restore. Phải phân biệt lúc callback được **đăng ký**, lúc nó được **scheduled**, và lúc **chạy**. Đồng thời kiểm thử cả nhánh thành công, ngoại lệ và cancellation.

### Một chuỗi future có nhiều ranh giới

```text
Caller (context A)
  -> submit stage 1
       worker X: stage 1
          -> complete
       worker Y: stage 2 callback
          -> complete
       worker Z: stage 3 callback
```

Dù các stage thuộc cùng pipeline, không có bảo đảm đơn giản rằng `ThreadLocal` của worker X được chuyển sang Y hoặc Z. Một hệ thống context propagation phải xác định cách mang dữ liệu qua **từng ranh giới scheduler/callback**.

### Không nhầm kết thúc lời gọi và hoàn tất tác vụ

Một advice đo thời gian quanh method **trả về `CompletableFuture`** thường kết thúc khi future **được trả về**, không phải khi future **hoàn tất**. Nếu muốn đo end-to-end latency, cần liên kết hoạt động ở thời điểm hoàn tất future với mốc bắt đầu, đồng thời xử lý completion thành công, thất bại và hủy. Việc này không thể suy ra chỉ từ method execution ban đầu.

## 10. Pointcut theo luồng điều khiển động

Một số mô hình AOP cung cấp pointcut dựa trên **dynamic control flow**: chọn join point xảy ra trong luồng gọi của một join point khác. Ví dụ ý tưởng:

```text
Chọn lời gọi Repository.save(...)
nếu nó xảy ra trong luồng điều khiển đồng bộ
bắt đầu từ Checkout.placeOrder(...)
```

Khái niệm *control flow* thường được xác định theo chuỗi lời gọi trên thread hiện tại, tùy DSL và weaving. Nếu `Checkout.placeOrder` **gửi** một tác vụ rồi trở về, worker xử lý `Repository.save` ở stack khác. **Quan hệ “tác vụ do checkout tạo ra” không tự là quan hệ cflow**.

Nếu bài toán cần “mọi tác vụ được bắt nguồn từ checkout”, có thể cần mang một **causal marker** hay request context tường minh tới worker, rồi để advice ở worker lựa chọn theo marker đó nếu engine hỗ trợ. Không tự mở rộng semantics của một pointcut cflow chỉ vì nghiệp vụ có quan hệ nhân quả.

### Control flow khác trace context

- **Control flow:** quan hệ frame/lời gọi tại thời điểm đang chạy.
- **Trace context:** metadata mô tả quan hệ giữa hoạt động, có thể được truyền qua thread/process.
- **Business transaction:** phạm vi commit/rollback, có thể khác với cả hai khái niệm trên.

Một trace span nối tiếp ở worker không chứng minh worker vẫn nằm trong stack của caller hay cùng transaction quản lý tài nguyên. Tách bạch ba loại ngữ cảnh này giúp tránh đặt pointcut hoặc xử lý resource ở sai ranh giới.

## 11. Lời gọi qua proxy và lời gọi nội bộ

Một interceptor dùng **proxy** chỉ quan sát những lời gọi đi qua proxy. Khi worker đã nhận được context đúng, nhưng gọi **trực tiếp target**, advice trên proxy vẫn có thể không chạy.

```text
Thread A -> proxy -> target       [nếu khớp, interceptor có thể chạy]
Thread B -> proxy -> target       [phụ thuộc context của B khi đánh giá]
Thread B ----------> target       [bỏ qua proxy, dù B có context]
target -> this.internalMethod()   [có thể bỏ qua interception qua proxy]
```

Điều này cho thấy **propagation** và **weaving coverage** là hai vấn đề độc lập. Đúng request context không sửa được một đường gọi không đi qua điểm can thiệp; đúng pointcut không làm context tự hiện diện trên worker.

Một kỹ thuật AOP có thể weave trực tiếp bytecode và nhìn thấy những join point mà proxy không chặn được. Tuy nhiên, khả năng can thiệp chính xác ở đâu còn phụ thuộc việc class nào được weave, trình nạp class, phạm vi compile/load và các giới hạn của công cụ.

## 12. Ngữ cảnh đối số và mutable state khi gửi công việc

Không phải mọi “mất ngữ cảnh” đều là ThreadLocal. Giả sử một method nhận `MutableRequest` rồi gửi nó cho worker:

```java
// Minh họa ý tưởng, không phải chương trình Java hoàn chỉnh.
MutableRequest request = ...;
executor.submit(() -> process(request));
request.setPriority("LOW");
```

Worker giữ tham chiếu cùng object. Nếu request bị sửa trong khi worker đọc, giá trị dùng để xét pointcut theo tham số có thể khác giá trị tại lúc submit, thậm chí có race condition. Đây là sự khác biệt giữa:

- **Capture by reference:** giữ object rồi đọc trạng thái vào thời điểm sau.
- **Snapshot bất biến:** chụp các trường được phép truyền tại thời điểm được xác định.
- **Re-read trong worker:** quyết định bằng trạng thái mới nhất với contract và cơ chế đồng bộ phù hợp.

Nếu điều kiện AOP kiểm tra giá trị trước khi gửi, nhưng target kiểm tra lại sau khi nhận, hai bên có thể quan sát hai trạng thái khác nhau (**time-of-check/time-of-use**). Các bất biến nghiệp vụ/bảo mật phải có cơ chế bảo đảm tại ranh giới đáng tin cậy; pointcut không tự khóa object hoặc tạo tính nguyên tử.

## 13. Ngữ cảnh bảo mật và identity cần có ranh giới tin cậy

Dữ liệu như **trace ID** thường là metadata quan sát; dữ liệu **ủy quyền** quyết định ai được làm gì, có ý nghĩa nhạy cảm hơn. Không nên tự động copy một danh tính hoặc “đã xác thực” từ thread hiện tại sang mọi tác vụ rồi coi đó là bằng chứng đủ để thực hiện thao tác.

Khi một tác vụ hoạt động thay mặt người dùng, cần quy định:

- Tác vụ còn được phép dùng danh tính/ủy quyền đó tại thời điểm thực thi không?
- Phạm vi quyền và thời hạn hiệu lực là gì?
- Có cần xác minh lại ở boundary không?
- Nếu chạy qua hàng đợi lâu hoặc process khác, dữ liệu nhận được được xác thực thế nào?
- Có được đưa token, mật khẩu hoặc thông tin cá nhân vào context/log không?

**Propagation không tương đương authorization.** Một marker truyền được tới worker chỉ chứng minh ta đã vận chuyển một giá trị; nó không chứng minh giá trị ấy đáng tin cậy hoặc vẫn có hiệu lực. Không lấy ThreadLocal của caller làm cơ chế bảo mật duy nhất nếu còn đường thực thi khác không được bảo vệ.

## 14. Ngữ cảnh tracing/logging và giới hạn quan sát

Một aspect logging có thể đọc request ID để thêm vào mỗi dòng log. Nếu context không được truyền:

- Log ở caller có `REQ-17`.
- Log ở worker thiếu mã tương quan hoặc nhận nhầm mã của task trước.
- Hệ thống quan sát mất khả năng ghép sự kiện theo cùng yêu cầu.

Khi truyền context, nên chỉ chuyển metadata thực sự cần thiết, theo chính sách:

- Dùng correlation ID hoặc trace ID có định dạng và nguồn đáng tin cậy.
- Giới hạn kích thước context/baggage, tránh tăng payload không kiểm soát.
- Không tự ghi toàn bộ argument, token, session hoặc dữ liệu nhạy cảm.
- Tránh lấy ID người dùng/đơn hàng có cardinality rất lớn làm nhãn metric nếu không có chiến lược phù hợp.
- Dọn context ngay ở ranh giới tác vụ để tránh nhầm dữ liệu giữa các request.

Nếu logging framework dùng một map ngữ cảnh gắn theo thread (ví dụ mô hình *mapped diagnostic context*), quy tắc tương tự ThreadLocal thường được áp dụng: **capture các trường được phép, restore trên worker, rồi cleanup**. Không giả định mọi thư viện logging có cơ chế propagation như nhau.

## 15. Context qua hàng đợi và ranh giới tiến trình

Khi công việc được gửi đến một **message queue, background job hoặc dịch vụ khác**, giá trị ThreadLocal càng không thể tự xuất hiện ở nơi nhận. Cần xác định một **context envelope** hoặc metadata của thông điệp được truyền tường minh.

```text
Process A                               Process B
  request context
      |
      +-- encode allowed metadata
      |
      +--> message / protocol headers ------> decode / validate
                                               |
                                               +--> create local task context
                                               |
                                               +--> invoke target/advice
                                               |
                                               +--> cleanup
```

Các cân nhắc gồm:

1. **Serialization:** chỉ gửi những trường có thể biểu diễn, cần thiết và an toàn.
2. **Versioning:** trường mới/cũ phải có cách xử lý khi sender và receiver dùng phiên bản khác.
3. **Trust:** receiver không tin mù quáng mọi field do nguồn bên ngoài cung cấp.
4. **Lifetime:** deadline và quyền có thể hết hạn khi message nằm trong queue.
5. **Retries:** một message có thể chạy lại, nên hành vi nghiệp vụ cần idempotency hoặc cơ chế loại trùng phù hợp.
6. **Observability:** nối quan hệ nhân quả bằng trace/correlation metadata, không bằng giả định cùng stack/thread.
7. **Privacy:** giới hạn thông tin trong header, tránh để dữ liệu nhạy cảm lan qua nhiều dịch vụ.

Khôi phục context ở Process B là tạo **ngữ cảnh cục bộ mới** từ dữ liệu đã được xác nhận, không phải “khôi phục nguyên xi thread của Process A”.

## 16. Các cơ chế truyền ngữ cảnh phổ biến và sự đánh đổi

| Phương án | Ưu điểm | Giới hạn |
| --- | --- | --- |
| **Truyền tham số trực tiếp** | Contract rõ, dễ thấy ở call site và test | Có thể phải truyền qua nhiều lớp; cần thiết kế interface |
| **Snapshot context bất biến** | Giảm race, thuận tiện khi gửi task | Phải xác định thời điểm snapshot và trường được phép |
| **Wrapper quanh Runnable/Callable/callback** | Có thể capture/restore ở đúng boundary | Dễ bỏ sót một nhánh callback/executor nếu áp dụng không đồng bộ |
| **Thread-local có scope rõ** | Tiện cho lời gọi đồng bộ trong cùng thread | Không tự truyền qua task; dễ rò trên thread pool |
| **Runtime API có cơ chế context riêng** | Có thể hỗ trợ phạm vi, propagation và cleanup | Phải tuân thủ contract, không suy đoán có ở mọi API |
| **Metadata của message/protocol** | Đi qua tiến trình/máy khác | Cần serialize, validate, bảo mật và quản lý phiên bản |

AOP có thể giúp áp dụng wrapper ở các ranh giới được chọn hoặc đọc context để quyết định advice. Nhưng **context carrier** thuộc mô hình thực thi; điểm can thiệp chỉ là nơi có thể gắn hành vi ấy. Hãy tránh biến aspect thành nơi bí mật tự sao chép mọi biến của thread.

## 17. Cancellation, timeout và deadline cũng là ngữ cảnh

Ngoài trace ID, một tác vụ có thể cần **deadline**, **cancellation token** hoặc **giới hạn tài nguyên**. Các giá trị này ảnh hưởng điều kiện chạy advice và target, nhưng không nên gói bằng các giả định quá đơn giản.

Ví dụ, nếu caller gửi task rồi timeout:

- Worker có thể chưa bắt đầu hoặc vẫn đang chạy.
- `Future.cancel(true)` là yêu cầu hủy có thể cố gắng ngắt thread; không phải cam kết mọi phép toán dừng ngay.
- Nếu handler cleanup chỉ chạy ở nhánh thành công mà không có `finally`, context có thể tồn tại sau khi công việc thất bại/ngắt.
- Deadline cần được kiểm tra theo thời điểm thích hợp ở worker; capture một boolean “chưa hết hạn” ở caller không chứng minh tác vụ vẫn còn hạn khi bắt đầu.

Một aspect có thể ghi nhận deadline và trạng thái hủy, nhưng hành vi ngắt/hủy thực tế phụ thuộc API và mã thực thi. **Đừng dùng một pointcut theo context để thay thế toàn bộ chính sách hủy hoặc bảo đảm tài nguyên.**

## 18. Khi nào một pointcut theo ngữ cảnh không đáng tin?

Có những câu hỏi giúp phát hiện giả định sai:

- Pointcut có cần **caller stack** nhưng target thực thi ở worker khác không?
- Context có dựa vào **ThreadLocal** mà không có propagation không?
- Worker có được **reuse** cho tác vụ khác và có cleanup nhất quán không?
- Giá trị context được capture ở **đúng thời điểm nghiệp vụ** không?
- Argument là object mutable bị thay đổi sau khi đưa vào queue không?
- Advice có được weave ở **đường gọi thật** hay chỉ được khai báo?
- Một callback có thể chạy trong **executor khác** với executor dự kiến không?
- Có nhánh **exception, cancellation, retry** nào không khôi phục context không?
- Dữ liệu truyền có chứa **danh tính bảo mật** hoặc thông tin nhạy cảm không?
- Công cụ AOP có thật sự hỗ trợ predicate động/cflow ở loại join point đã chọn không?

Nếu một trong các câu trả lời chưa rõ, hãy kiểm thử bằng một boundary thực thay vì suy luận từ một lời gọi đồng bộ đơn giản.

## 19. Quy trình thiết kế một chính sách AOP qua ranh giới async

### Bước 1: Xác định concern

Ví dụ “mọi thao tác lưu tài liệu thuộc yêu cầu X cần log có correlation ID”, hoặc “đo trọn thời gian xử lý tác vụ hậu trường”. Hai mục tiêu này có thể cần các điểm quan sát khác nhau.

### Bước 2: Xác định join point thật

Bạn đang muốn can thiệp vào:

- Lời gọi `submit` từ caller?
- Lúc `Runnable.run` hoặc `Callable.call` bắt đầu?
- Method nghiệp vụ thực thi trong worker?
- Thời điểm một future/callback hoàn tất?
- Ranh giới gửi/nhận message giữa hai process?

Không phải công cụ nào cũng có mọi loại join point. **Một method name trùng nhau không khiến hai ranh giới trở thành một.**

### Bước 3: Liệt kê dữ liệu phải có tại điểm đó

Ví dụ trace ID, operation name và deadline. Với từng trường, chỉ rõ ai sở hữu, capture khi nào, có thể thay đổi không, có thể gửi tới thread/process khác không và có được tin cậy không.

### Bước 4: Chọn cách propagation

Dùng tham số trực tiếp, snapshot bất biến hoặc wrapper được tạo tại đúng boundary. Với nhiều nhánh callback hoặc queue, xác định nơi context tiếp tục được vận chuyển qua mỗi ranh giới.

### Bước 5: Định nghĩa scope và cleanup

Lưu context cũ của worker, set context mới, thực thi, khôi phục trong `finally`; đảm bảo công việc của request mới không đọc nhầm context cũ. Với process khác, xác thực metadata ở đầu nhận.

### Bước 6: Kiểm thử trên đường thực tế

Không chỉ unit test predicate. Phải gọi qua executor/pool/callback/queue thật (hoặc môi trường kiểm thử tương đương) và xác nhận log, pointcut, kết quả, ngoại lệ, cleanup, cancellation.

### Bước 7: Đo overhead

Một wrapper nhẹ có thể phù hợp; sao chép đồ thị object lớn, parse dữ liệu hoặc đồng bộ blocking trong advice mỗi lần submit có thể làm tăng độ trễ. Đo theo tải, số task và mức cạnh tranh thật.

## 20. Ma trận kiểm thử dành cho context-aware pointcut

| Tình huống | Điều cần chứng minh |
| --- | --- |
| Lời gọi đồng bộ cùng thread | Advice nhìn thấy context được set hợp lệ |
| Task gửi sang pool không có carrier | Không ngộ nhận caller ThreadLocal đã truyền |
| Task có carrier | Advice đọc đúng context tại worker |
| Task kế tiếp trên cùng worker | Không rò context từ task trước |
| Scope lồng nhau có giá trị trước đó | Restore về giá trị ban đầu sau scope trong |
| Target ném exception | `finally` vẫn khôi phục context |
| Callback nằm trên executor khác | Context chỉ có khi được tiếp tục truyền đúng |
| Task bị hủy hoặc timeout | Cleanup và trạng thái kết thúc tuân theo contract |
| Argument mutable đổi giữa submit/run | Predicate và target không dùng dữ liệu mâu thuẫn ngoài ý muốn |
| Caller đổi trace ID sau capture | Worker đọc đúng snapshot theo thời điểm đã định |
| Gọi trực tiếp target bỏ qua proxy | Coverage phản ánh đúng giới hạn interception |
| Có nhiều context đồng thời | Không lẫn dữ liệu request giữa worker |
| Message qua process khác | Metadata được serialize/validate và tạo local context |
| Retry tác vụ | Correlation giữ đúng ý nghĩa, side effect nghiệp vụ không bị lặp trái contract |

**Test quan trọng nhất không chỉ là “đã truyền được trace ID”, mà còn là “không có trace ID cũ khi task mới bắt đầu”.**

### Phân biệt ba loại kiểm thử

- **Predicate unit test:** context A/B/null có làm guard hoặc dynamic pointcut cho kết quả đúng không?
- **Integration test:** khi thật sự chuyển thread/callback, carrier và cơ chế AOP có hoạt động ở đường gọi đã thiết kế không?
- **Behavioral test:** target chạy đúng số lần, trả đúng kết quả/ngoại lệ, không bị thay đổi nghiệp vụ trái contract và không rò context.

## 21. Những lỗi phổ biến

1. **Giả định thread mới luôn nhận ThreadLocal của caller.** `ThreadLocal` thông thường gắn riêng với thread.
2. **Dùng InheritableThreadLocal để truyền theo từng submit vào pool.** Thread pool reuse làm giả định này không đáng tin.
3. **Đặt context vào worker nhưng quên `finally`.** Dẫn đến rò thông tin request cũ.
4. **Luôn remove sau scope trong.** Có thể xóa nhầm context hợp lệ ở scope ngoài.
5. **Capture object mutable nhưng gọi nó là snapshot.** Dữ liệu có thể đổi hoặc race trước khi worker đọc.
6. **Tưởng cflow xuyên qua executor.** Quan hệ stack đồng bộ không tự là quan hệ nhân quả giữa các task.
7. **Tưởng future trả về nghĩa là công việc xong.** Nó có thể chỉ đại diện một kết quả sẽ hoàn tất trong tương lai.
8. **Bọc submit nhưng bỏ sót callback.** Ngữ cảnh có thể bị mất ở chặng tiếp theo.
9. **Tưởng propagation sẽ làm proxy chặn mọi lời gọi.** Context đúng không thay thế weaving coverage.
10. **Sao chép authorization mà không xác thực tại boundary.** Có thể dùng quyền đã hết hạn hoặc từ nguồn không đáng tin cậy.
11. **Ghi toàn bộ request context vào log.** Dễ lộ token, PII và tạo metric cardinality rất lớn.
12. **Không kiểm thử task thất bại/cancel/retry.** Cleanup và số lần thực thi có thể khác happy path.
13. **Dùng ngữ cảnh trong thread để suy ra transaction.** Transaction/resource có vòng đời và semantics riêng.
14. **Chọn pointcut theo tên nhưng không tính đến context runtime.** Chữ ký giống nhau không bảo đảm điều kiện của request giống nhau.

## 22. Bài tập tự luyện

1. Chạy `AopContextHandoffDemo.java` và giải thích vì sao `no-transfer` không có dòng `advice` dù main thread đã đặt `REQ-17`.
2. Bỏ khối `finally` trong `carryContext`, chạy lại cùng single-thread executor. Dự đoán và quan sát `after`; từ đó giải thích nguy cơ rò context.
3. Thêm `carryContext` lồng nhau với hai trace ID khác nhau và xác nhận scope bên ngoài được khôi phục chính xác.
4. Thay `RequestContext` bằng một object mutable và sửa giá trị sau khi tạo wrapper; xác định phần nào là snapshot, phần nào vẫn là tham chiếu chia sẻ.
5. Tạo hai task bất đồng bộ nối tiếp bằng `CompletableFuture`. Bọc callback ở từng boundary, rồi chứng minh nếu bỏ một wrapper thì trace có thể mất.
6. Thiết kế một `Callable<T>` wrapper có capture/restore tương đương `Runnable`, không nuốt ngoại lệ và không đổi kiểu kết quả.
7. Thêm nhánh target ném `IllegalArgumentException`. Kiểm tra wrapper luôn restore context, còn handler giữ nguyên ngoại lệ từ target.
8. Phân biệt metric “thời gian `submit`” với metric “thời gian task hoàn tất”. Xác định hai điểm can thiệp/quan sát cần có.
9. Mô phỏng một message từ process A sang B với chỉ ba trường context được phép. Nêu cách B xác thực, khôi phục và cleanup.
10. Viết một bảng kiểm thử có ít nhất ba task của hai request dùng chung một worker, nhằm chứng minh không có cross-task contamination.

**Tự kiểm tra:** Nếu một pointcut thấy `ThreadLocal` trên caller, nó có mặc nhiên thấy cùng giá trị trên worker không? Vì sao `Future.get()` không phải cơ chế propagation? Khi nào nên khôi phục context cũ, khi nào remove? Một dynamic control-flow pointcut có tự mở rộng qua thread khác không? Nếu trace context truyền đúng, lời gọi trực tiếp target có tự chạy advice của proxy không?

## Kết luận

**Ngữ cảnh của AOP luôn gắn với một điểm thực thi cụ thể.** Khi công việc chuyển từ lời gọi đồng bộ sang executor, future, callback, task hoặc tiến trình khác, call stack và trạng thái gắn với thread **không tự đi theo**. Muốn một aspect tiếp tục lựa chọn đúng join point theo ngữ cảnh, cần hiểu semantics của loại pointcut, xác định dữ liệu cần mang theo, truyền có chủ đích bằng contract rõ, khôi phục ở nơi chạy và dọn sạch trong mọi nhánh kết thúc. Đồng thời phải kiểm chứng coverage thực sự của weaving/interception, thời điểm capture, trạng thái mutable và ranh giới bảo mật. Phân biệt các điều này giúp tránh cả **mất ngữ cảnh** lẫn **rò ngữ cảnh giữa các tác vụ**, hai nguồn lỗi đặc biệt khó phát hiện trong hệ thống bất đồng bộ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="contextual-pointcuts-009">Chi phí và khả năng kiểm thử của điều kiện động</a>

<details>
<summary>Xem chi tiết</summary>

# Chi phí và khả năng kiểm thử của điều kiện động trong pointcut AOP

## 1. Điều kiện động là gì, và vì sao cần quan tâm đến chi phí?

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP)**, một **pointcut** xác định các **join point** mà advice có thể được áp dụng. Join point là điểm trong mô hình thực thi có thể can thiệp, ví dụ một lần thực thi phương thức hoặc vị trí gọi phương thức nếu cơ chế AOP hỗ trợ.

Có những điều kiện chọn điểm có thể suy ra từ **cấu trúc chương trình**: loại join point, chữ ký phương thức, kiểu khai báo, tên package, annotation tĩnh. Nhưng có những điều kiện chỉ xác định được khi một **lần thực thi cụ thể** xảy ra: đối tượng thực sự nhận lời gọi, đối số runtime, trạng thái ngữ cảnh, hoặc việc luồng điều khiển hiện đang nằm trong một vùng thực thi nhất định.

Nhóm thứ hai là **điều kiện động (dynamic conditions)**. Chúng hữu ích vì cho phép một aspect can thiệp chính xác hơn theo ngữ cảnh, nhưng việc kiểm tra thường phải diễn ra trong đường chạy thực tế.

Ví dụ yêu cầu:

> Chỉ ghi nhận kiểm toán chuyên sâu khi thao tác chuyển khoản có số tiền lớn và thuộc nhóm tài khoản cần theo dõi.

Có thể lọc tĩnh theo chữ ký thao tác `transfer`. Nhưng một chữ ký `transfer(String,long)` không cho biết lần gọi hiện tại mang số tiền 50 hay 5.000. Quy tắc theo giá trị phải được đánh giá ở thời điểm chạy tương ứng, nếu công cụ và thiết kế cho phép.

Hai câu hỏi chính của bài:

1. **Chi phí:** việc lựa chọn động tốn bao nhiêu tài nguyên ở mỗi lần xuất hiện join point, và khi nào tổng chi phí trở nên đáng kể?
2. **Khả năng kiểm thử:** làm thế nào chứng minh advice chạy đúng trên các ngữ cảnh cần thiết, không chạy ở ngữ cảnh bị loại, và giữ đúng hành vi khi gặp lỗi, đồng thời hoặc bất đồng bộ?

Bài học áp dụng cho **AOP tổng quát**. Khi nhắc đến cú pháp của một công cụ cụ thể, ví dụ đó chỉ minh họa ngữ nghĩa tương ứng; không có giả định rằng mọi công cụ hỗ trợ cùng một tập join point và cách biểu đạt pointcut.

## 2. Tách điều kiện tĩnh và động

Xem một thao tác nghiệp vụ:

```java
Receipt transfer(String accountId, long amount);
```

Đây là **chữ ký Java minh họa**, chưa phải chương trình độc lập vì `Receipt` cần có định nghĩa. Những thông tin có thể nhận diện từ cấu trúc gồm tên `transfer`, kiểu trả về `Receipt`, kiểu tham số `String,long` và kiểu khai báo của phương thức.

Các dữ liệu như `accountId="VIP-42"`, `amount=5000` hoặc đối tượng runtime thuộc lớp triển khai nào lại liên quan đến **một lần gọi cụ thể**.

| Tiêu chí | Điều kiện tĩnh | Điều kiện động |
| --- | --- | --- |
| Nguồn thông tin | Loại join point, khai báo phương thức, metadata cấu trúc | Đối tượng, đối số, trạng thái và ngữ cảnh khi chạy |
| Thời điểm thường có thể đánh giá | Biên dịch, nạp mã hoặc bước chuẩn bị chọn điểm | Khi join point xuất hiện hoặc khi ngữ cảnh được cập nhật |
| Ví dụ | Tên phương thức là `transfer` | `amount > 1000` |
| Tần suất đánh giá | Có thể xử lý hoặc tối ưu sớm tùy công cụ | Có thể phải kiểm tra trên từng invocation tiềm năng |
| Khả năng thay đổi giữa hai lần gọi | Thường ổn định với cùng vị trí/định nghĩa mã | Có thể khác hoàn toàn |
| Rủi ro chính | Chọn sai tập điểm cấu trúc | Chi phí runtime, thay đổi trạng thái và kiểm thử nhiều nhánh |

Một pointcut có thể chứa **cả hai phần**. Thường nên dùng điều kiện tĩnh để giảm tập điểm có khả năng bị can thiệp, rồi áp dụng điều kiện động ở nơi còn cần thiết.

**Điều kiện động không nhất thiết chậm.** Một phép kiểm tra `amount > 1000` đơn giản có thể rất rẻ. Điều quan trọng là **số lần kiểm tra**, dữ liệu được truy cập, tác động lên đường chạy nóng và cách thực hiện của công cụ.

### 2.1. Join-point shadow và lần xuất hiện thực tế

Một vị trí mã có thể phát sinh nhiều lần thực thi:

```java
for (int i = 0; i < 100; i++) {
    gateway.transfer("VIP-42", i * 100L);
}
```

Đoạn trên là **mã minh họa** với `gateway` được giả định đã khai báo.

Một vị trí lời gọi tĩnh có thể phát sinh **100 lần gọi**. Nếu cơ chế AOP chọn được loại join point tương ứng và có điều kiện theo số tiền, việc đánh giá điều kiện có thể diễn ra trên các lần gọi này, dù cuối cùng chỉ một phần được advice xử lý.

Đừng suy ra chi phí chỉ từ **số phương thức khớp mẫu**. Một phương thức chạy 10 triệu lần có thể tạo nhiều phép kiểm tra hơn hàng trăm phương thức hiếm khi chạy.

## 3. Luồng xử lý pointcut có điều kiện động

Một mô hình khái niệm:

```text
Mã được cấu hình / weaving
         |
         v
Bộ lọc cấu trúc tĩnh
         |
         +-- Không thuộc phạm vi ----------> Không cần kiểm tra động cho điểm này
         |
         v
Join point xuất hiện khi chạy
         |
         v
Lấy ngữ cảnh cần thiết (args / target / control flow ...)
         |
         v
Đánh giá điều kiện động
         |
         +-- Không khớp -------------------> Tiếp tục xử lý theo ngữ nghĩa công cụ
         |
         +-- Khớp -------------------------> Áp dụng advice tương ứng
                                                 |
                                                 v
                                        Thao tác gốc / chuỗi advice
```

Sơ đồ mô tả **mục đích tách hai giai đoạn**, không khẳng định mọi bộ máy AOP thực thi chính xác thành một chuỗi hàm như hình. Một số công cụ có thể tối ưu, kết hợp, hoán đổi các kiểm tra thuần túy hoặc biểu diễn chúng trong mã được weaving.

Các tình huống cần phân biệt:

- **Không thuộc loại join point được hỗ trợ:** không có can thiệp tương ứng, dù biểu thức mô tả trên giấy có vẻ đúng.
- **Khớp phần tĩnh nhưng trượt điều kiện động:** lần gọi không nhận advice được ràng buộc với điều kiện đó.
- **Khớp toàn bộ:** advice có thể chạy, nhưng một advice bao ngoài cũng có thể thay đổi việc chuyển tiếp tới các phần bên trong.
- **Bản thân điều kiện ném lỗi:** cần biết hợp đồng công cụ và chính sách ứng dụng; không mặc định lỗi được bỏ qua.
- **Ngữ cảnh bị thay đổi trong chuỗi advice:** giá trị được kiểm tra lúc nào có thể ảnh hưởng kết quả lựa chọn.

**Pointcut được chọn** và **phần nghiệp vụ thực sự chạy** là hai sự kiện khác nhau. Một around advice có thể từ chối hoặc không tiếp tục thao tác gốc nếu được thiết kế cho phép.

## 4. Những nguồn chi phí của điều kiện động

Không chỉ có thời gian của phép so sánh. Chi phí có thể xuất hiện ở nhiều lớp:

| Thành phần | Nguồn chi phí | Ví dụ |
| --- | --- | --- |
| Kiểm tra predicate | So sánh giá trị, kiểm tra kiểu, nhánh điều kiện | `amount > threshold` |
| Lấy ngữ cảnh | Truy cập args, target, metadata runtime | Trích xuất đối số |
| Phân bổ bộ nhớ | Tạo mảng/đối tượng bọc ngữ cảnh | Mỗi lần gọi tạo bản sao args |
| Truy cập trạng thái | Map, cấu hình, đồng bộ hoặc thread context | Tra chính sách theo tenant |
| Điều kiện theo luồng điều khiển | Theo dõi phạm vi gọi lồng nhau | Kiểm tra đang ở trong một flow |
| Cơ chế interception | Chuyển qua proxy/handler hoặc mã đã weaving | Một tầng gọi gián tiếp |
| Advice sau khi khớp | Logging, audit, metrics, retry | Ghi log hoặc phát sự kiện |
| Ảnh hưởng gián tiếp | Cache miss, branch misprediction, GC, contention | Tăng độ trễ tail latency |

Phải tách **chi phí kiểm tra xem có khớp không** với **chi phí thực thi advice sau khi đã khớp**. Nếu advice gọi dịch vụ mạng, chi phí I/O có thể áp đảo phần predicate; nếu advice gần như rỗng trên một hot path, chính phần chọn điểm/interception lại trở nên đáng kể.

### 4.1. Mô hình chi phí gần đúng

Đối với một nhóm join point, có thể dùng mô hình giản lược:

```text
Total overhead xấp xỉ =
    chi phí chuẩn bị/weaving (phân bổ theo vòng đời)
  + số lần xem xét điểm * chi phí interception/kiểm tra tĩnh còn lại
  + số lần phải kiểm tra động * chi phí kiểm tra động trung bình
  + số lần khớp * chi phí advice trung bình
  + tác động phụ do cấp phát, đồng bộ, cache và I/O
```

Đây là **mô hình tư duy**, không phải công thức thời gian chính xác cho mọi công cụ. Hệ thống có thể tối ưu hoặc hợp nhất các bước, và chi phí thực tế phụ thuộc phân phối đầu vào cùng tải đồng thời.

Ví dụ giả định: một điều kiện tốn 100 ns mỗi lần và được đánh giá 1.000.000 lần mỗi giây sẽ tiêu thụ xấp xỉ 100 ms CPU mỗi giây (khoảng 0,1 CPU core tính lý tưởng), **chưa kể** overhead khác. Các con số chỉ nhằm chỉ ra tác động của tần suất; không phải benchmark của một công cụ AOP.

### 4.2. Chi phí theo số lần gọi khác chi phí theo số shadow

Một pointcut bao phủ 20 method executions hiếm khi chạy chưa chắc đắt hơn pointcut chỉ bao phủ một phương thức được gọi liên tục.

Để dự đoán có cơ sở, cần biết:

1. Có bao nhiêu điểm có thể bị can thiệp?
2. Mỗi điểm được đi qua bao nhiêu lần dưới tải thực?
3. Bao nhiêu lần cần kiểm tra điều kiện runtime?
4. Tỷ lệ khớp thực tế là bao nhiêu?
5. Advice khớp tốn bao nhiêu CPU, cấp phát, I/O hoặc khóa?

Phép đo cần phân biệt **số điểm tĩnh**, **số invocation** và **số advice thực sự chạy**.

## 5. Short-circuit và thứ tự điều kiện

Khi ngôn ngữ/công cụ pointcut hỗ trợ tổ hợp logic, có thể mô tả điều kiện dạng:

```text
isTransferOperation AND isVipAccount AND amountOverThreshold
```

Đây là **mã giả**, không phải một DSL pointcut cụ thể.

Trong các ngôn ngữ có đánh giá ngắn mạch, nếu điều kiện đầu sai thì các điều kiện sau có thể không cần đánh giá. Đưa bộ lọc **rẻ, có khả năng loại nhiều trường hợp và không phụ thuộc side effect** lên trước có thể giảm công việc.

Tuy nhiên cần thận trọng:

- Không phải mọi công cụ AOP đều cam kết đánh giá các pointcut con theo đúng **thứ tự văn bản** mà người viết nhập.
- Một engine có thể tự sắp xếp/ tối ưu các điều kiện theo metadata và semantics được phép.
- Nếu logic phụ thuộc vào side effect của một predicate, việc tối ưu hoặc thay đổi thứ tự có thể gây lỗi.
- `AND` và `OR` phải được nhóm đúng ý bằng dấu ngoặc, đặc biệt khi kết hợp loại trừ.

Vì thế, **đừng dùng pointcut như một workflow bắt buộc chạy từng kiểm tra theo thứ tự**. Điều kiện chọn điểm nên thuần, xác định được và không dựa vào số lần engine gọi predicate.

### 5.1. Ví dụ tối ưu hợp lý

Giả sử yêu cầu là chỉ audit khoản chuyển giá trị lớn ở một nhóm tài khoản. Một thiết kế có thể:

- Loại các phương thức không phải `transfer` từ giai đoạn cấu trúc.
- Kiểm tra kiểu/định dạng đối số cần thiết tại thời điểm chạy.
- Kiểm tra ngưỡng bằng phép so sánh đơn giản.
- Chỉ sau khi khớp mới thực thi phần audit có chi phí cao.

Nếu ngữ nghĩa chính sách cho phép và dữ liệu đã có tại thời điểm kiểm tra, đây là cách giảm tần suất xử lý đắt tiền mà vẫn bảo toàn tập điểm mong muốn.

## 6. Điều kiện theo đối tượng, tham số và luồng điều khiển

Một số mô hình AOP hỗ trợ chọn điểm dựa trên **kiểu đối tượng**, **đối số thực tế** hoặc **trạng thái luồng thực thi**. Ví dụ, **AspectJ** có các designator như `args`, `this`, `target`, `cflow` với ngữ nghĩa riêng của công cụ.

Ví dụ minh họa **cú pháp AspectJ**, không phải Java độc lập và không phải ngữ pháp chung cho mọi giải pháp:

```aspectj
execution(* example.TransferService.transfer(..))
    && args(java.lang.String, long)
```

Phần `execution` xác định dạng method execution theo mẫu. Phần `args` xét hình dạng/kiểu đối số theo ngữ nghĩa của designator. Cần phân biệt **kiểu đối số** với điều kiện **giá trị số tiền vượt ngưỡng**, vì ví dụ trên tự nó không diễn đạt phép so sánh `amount > 1000`.

Một ví dụ khác:

```aspectj
cflow(execution(* example.BatchService.runBatch(..)))
```

`cflow` chọn theo **phạm vi luồng điều khiển động** phù hợp với một điểm xuất phát. Nó có thể cần cơ chế theo dõi ngữ cảnh hơn một mẫu chỉ dựa trên chữ ký tĩnh. Tuy nhiên, chi phí và cách triển khai thực tế cần đo trên công cụ được dùng; không được suy rằng một designator có overhead cố định trên mọi môi trường.

### 6.1. Khác nhau giữa `within` và điều kiện theo luồng chạy

- Một điều kiện theo vị trí mã như `within` (trong ngữ nghĩa công cụ có designator này) mô tả **nơi join point được định nghĩa**.
- Một điều kiện như `cflow` mô tả **lần chạy có nằm trong một luồng điều khiển cụ thể** hay không.

Một lời gọi tới phương thức ở thư viện khác có thể xảy ra bên trong luồng thực thi của `runBatch`, mặc dù phần mã thư viện không được định nghĩa trong kiểu `BatchService`. Vì vậy hai loại điều kiện có thể chọn những tập join point khác nhau.

### 6.2. Phép kiểm tra trạng thái mutable

Giả sử điều kiện động đọc `policy.isEnabled(accountId)`. Nếu trạng thái chính sách có thể đổi đồng thời, kết quả có thể khác giữa hai invocation hoặc giữa hai giai đoạn của cùng một thao tác.

Nếu dùng trạng thái runtime trong điều kiện quan trọng, cần quyết định:

1. Thời điểm nào là ranh giới quyết định chính sách?
2. Dữ liệu đọc có nhất quán hoặc có snapshot tương ứng không?
3. Nếu dữ liệu thay đổi ngay sau lúc kiểm tra, điều gì phải xảy ra?
4. Khi đọc cấu hình thất bại, nên từ chối, cho phép hay báo lỗi theo hợp đồng?

Đây là vấn đề **tính nhất quán và thời điểm kiểm tra**, không chỉ hiệu năng.

## 7. Predicate động nên có tính chất gì?

Một điều kiện động tốt thường là:

- **Thuần về ý nghĩa:** không thay đổi dữ liệu nghiệp vụ chỉ để kiểm tra có khớp.
- **Dễ quyết định:** cùng một snapshot ngữ cảnh và chính sách cho kết quả nhất quán.
- **Rẻ tương đối với điểm can thiệp:** tránh I/O nặng trên đường gọi nóng khi có cách thiết kế khác.
- **Chịu được dữ liệu biên:** null, số âm, kiểu không phù hợp, đối tượng bị thiếu metadata.
- **An toàn đồng thời:** không dùng trạng thái mutable chung không bảo vệ.
- **Có thể kiểm thử độc lập:** có đầu vào/đầu ra rõ và có thể dựng được các trường hợp.
- **Không tạo vòng lặp can thiệp:** logic predicate/advice không vô ý kích hoạt lại cùng pointcut.

**Không nên đánh đồng “thuần” với “luôn được engine gọi đúng một lần”.** Nếu predicate có side effect như tăng bộ đếm nghiệp vụ hoặc ghi dữ liệu, số lần gọi do engine quyết định có thể làm thay đổi hệ thống theo cách khó dự đoán.

### 7.1. Vì sao không nên truy vấn dữ liệu ngoài trong mọi lần kiểm tra?

Giả sử kiểm tra `accountIsRisky(accountId)` gọi dịch vụ qua mạng ở mỗi invocation. Ngay cả khi chỉ 1% invocation nhận advice, 100% điểm tiềm năng có thể phải chịu chi phí mạng trước khi biết chúng không khớp.

Một thiết kế thay thế có thể chuyển dữ liệu chính sách tới một cấu trúc local được làm mới theo cơ chế rõ ràng, hoặc đặt việc kiểm tra ở ranh giới nghiệp vụ có thể quản lý được. Nhưng khi dùng cache phải xem xét **thời hạn hiệu lực, khả năng lỗi thời và yêu cầu nhất quán**; giảm latency không được âm thầm thay đổi quyết định chính sách.

## 8. Không thể cache mọi điều kiện động theo chữ ký

Một lỗi tối ưu hóa phổ biến là ghi nhớ kết quả `selected=true` cho một phương thức rồi áp dụng cho mọi lần gọi sau.

Nếu pointcut phụ thuộc `amount`, hai lần `transfer("A",50)` và `transfer("A",5000)` có thể cho kết quả khác nhau dù **cùng chữ ký**. Cache theo tên/chữ ký làm sai tập được chọn.

Các phân biệt cần thiết:

- **Metadata tĩnh:** có thể chia sẻ hoặc cache theo định danh kiểu/điểm khi hệ thống cho phép.
- **Kết quả kiểm tra động:** gắn với invocation hoặc tập dữ liệu làm nên quyết định.
- **Điều kiện ngữ cảnh theo control flow:** thường phụ thuộc ngăn xếp/luồng tác vụ hiện tại, không chỉ tham số.
- **Quy tắc thay đổi theo thời gian:** có thể phải có phiên bản chính sách và chiến lược invalidation.

Nếu caching thực sự cần thiết, phải chứng minh khóa cache chứa đầy đủ dữ liệu quyết định, có semantics về hết hạn/thay đổi và có kiểm thử chứng minh không lẫn giữa invocation.

## 9. Khả năng kiểm thử: cần kiểm tra những lớp nào?

Kiểm thử điều kiện động không dừng ở việc một predicate trả `true` cho vài đối số. Có ít nhất bốn lớp cần phân biệt:

1. **Predicate unit test:** logic theo args, kiểu runtime, ngữ cảnh hoặc trạng thái.
2. **Pointcut matching test:** mẫu tĩnh chọn đúng loại join point và tập vị trí phù hợp.
3. **Weaving/interception integration test:** đường gọi thực sự đi qua điểm có advice.
4. **Behavior test:** kết quả/ngoại lệ, thứ tự advice và side effect nghiệp vụ được bảo toàn theo yêu cầu.

Bảng:

| Lớp kiểm thử | Câu hỏi xác minh | Ví dụ lỗi |
| --- | --- | --- |
| Unit predicate | Đúng điều kiện biên? | Dùng `>=` thay cho `>` |
| Matching tĩnh | Đúng phương thức và loại điểm? | Chọn quá rộng vì wildcard |
| Integration | Điểm có được can thiệp thật? | Self-invocation bỏ qua proxy |
| Hành vi | Caller nhận đúng kết quả/ngoại lệ? | Advice nuốt lỗi hoặc gọi `proceed` hai lần |
| Hiệu năng | Latency và allocation có đạt yêu cầu? | Predicate gây GC hoặc I/O quá lớn |
| Đồng thời | Không trộn context giữa các lần gọi? | Dùng chung biến `currentAmount` |

Cần xác định **đáp án đúng độc lập với chính pointcut**. Nếu “tập điểm phải chọn” được tính bằng cách chạy chính pointcut cần kiểm tra, bài kiểm thử chỉ xác nhận nó nhất quán với bản thân, không phát hiện được sai phạm vi.

## 10. Các trường hợp biên cho điều kiện theo giá trị

Với yêu cầu “chỉ audit khoản chuyển có `amount > 1000` và `accountId` bắt đầu bằng `VIP-`”, nên kiểm thử:

| Ngữ cảnh | Có chọn? | Vì sao |
| --- | --- | --- |
| `("VIP-1",1001)` | Có | Vượt ngưỡng và đúng nhóm |
| `("VIP-1",1000)` | Không | Bằng ngưỡng, không lớn hơn |
| `("VIP-1",999)` | Không | Dưới ngưỡng |
| `("STD-1",9000)` | Không | Sai nhóm |
| `("VIP-",5000)` | Tùy hợp đồng tên | Prefix hợp cú pháp nhưng có thể thiếu mã thực |
| `(null,5000)` | Không hoặc lỗi được quy định | Không được gây `NullPointerException` ngoài ý muốn |
| `("VIP-1",-1)` | Không | Giá trị âm không vượt ngưỡng |
| Kiểu đối số không đúng | Theo hợp đồng công cụ/ứng dụng | Kiểm tra kiểu an toàn |
| Method execution khác tên | Không | Phải trượt bộ lọc tĩnh |

**Không được biến các ca giả định thành quy tắc nghiệp vụ phổ quát.** Ví dụ chính sách có thể yêu cầu null là lỗi bắt buộc thay vì “không chọn”. Điều quan trọng là **ghi rõ hợp đồng** rồi kiểm thử đúng hợp đồng ấy.

### 10.1. Dương tính và âm tính đều quan trọng

Nếu chỉ kiểm thử `("VIP-1",1001)` và thấy advice chạy, chưa thể biết pointcut có vô tình chạy với `("STD-1",9000)` hay không. Một bộ kiểm thử đầy đủ cần cả:

- **True positive:** điểm phải được chọn và đã được chọn.
- **False positive:** điểm không thuộc yêu cầu nhưng bị chọn.
- **False negative:** điểm cần chọn nhưng bị bỏ sót.
- **True negative:** điểm ngoài phạm vi và không được chọn.

Với advice có thể kiểm tra quyền hoặc thay đổi hành vi, FP và FN có thể tạo hậu quả khác nhau và phải được đánh giá theo mức độ rủi ro.

## 11. Ví dụ Java 17 chạy được: lọc tĩnh trước, kiểm tra động sau

Java thông thường không có một parser pointcut AOP tích hợp. Ta có thể dùng chương trình sau để **mô phỏng thứ tự lựa chọn**, đếm số lần đánh giá điều kiện và quan sát những lần được chọn.

Ví dụ chỉ là **Java thuần minh họa thuật toán chọn điểm**; nó không cung cấp weaving và không thay thế công cụ AOP thật. Điều kiện được viết trong mã nhằm minh họa quan hệ giữa chi phí đánh giá và số lần advice được chọn.

Lưu thành tệp `DynamicConditionDemo.java`:

```java
public class DynamicConditionDemo {

    record Invocation(String operation, String accountId, long amount) {}

    static final class Counters {
        int staticRejected;
        int dynamicChecks;
        int adviceRuns;
    }

    static boolean staticCandidate(Invocation call) {
        return "transfer".equals(call.operation());
    }

    static boolean dynamicMatches(Invocation call, Counters counters) {
        counters.dynamicChecks++;
        return call.accountId() != null
                && call.accountId().startsWith("VIP-")
                && call.amount() > 1000;
    }

    static void executeBusiness(Invocation call) {
        System.out.println("business:" + call.operation()
                + ":" + call.accountId() + ":" + call.amount());
    }

    static void dispatch(Invocation call, Counters counters) {
        if (!staticCandidate(call)) {
            counters.staticRejected++;
            executeBusiness(call);
            return;
        }

        if (dynamicMatches(call, counters)) {
            counters.adviceRuns++;
            System.out.println("advice:audit:" + call.accountId()
                    + ":" + call.amount());
        }

        executeBusiness(call);
    }

    public static void main(String[] args) {
        Counters counters = new Counters();

        dispatch(new Invocation("transfer", "VIP-7", 1500), counters);
        dispatch(new Invocation("transfer", "STD-8", 9000), counters);
        dispatch(new Invocation("lookup", "VIP-9", 5000), counters);
        dispatch(new Invocation("transfer", "VIP-1", 1000), counters);
        dispatch(new Invocation("transfer", null, 4000), counters);

        if (counters.staticRejected != 1
                || counters.dynamicChecks != 4
                || counters.adviceRuns != 1) {
            throw new AssertionError("unexpected selection counts");
        }

        System.out.println("summary:staticRejected="
                + counters.staticRejected
                + ",dynamicChecks=" + counters.dynamicChecks
                + ",adviceRuns=" + counters.adviceRuns);
    }
}
```

Chạy bằng JDK 17 hoặc mới hơn:

```bash
javac DynamicConditionDemo.java
java DynamicConditionDemo
```

Đầu ra **xác định**:

```text
advice:audit:VIP-7:1500
business:transfer:VIP-7:1500
business:transfer:STD-8:9000
business:lookup:VIP-9:5000
business:transfer:VIP-1:1000
business:transfer:null:4000
summary:staticRejected=1,dynamicChecks=4,adviceRuns=1
```

### 11.1. Đọc kết quả

- Có **5 lần gọi** mô phỏng.
- `lookup` không qua bộ kiểm tra động vì trượt điều kiện tĩnh, nên `staticRejected=1`.
- Bốn lần `transfer` phải qua điều kiện động, nên `dynamicChecks=4`.
- Chỉ `transfer("VIP-7",1500)` đạt cả prefix và ngưỡng, nên `adviceRuns=1`.
- Mọi thao tác vẫn chạy logic nghiệp vụ `executeBusiness` vì ví dụ chỉ mô phỏng advice quan sát; không từ chối thực thi.
- `null` được xử lý có chủ đích, tránh lỗi null ngoài ý muốn.

Các bộ đếm phản ánh **logic mô phỏng cụ thể**, không phải số lần predicate sẽ được một engine AOP bất kỳ gọi. Một công cụ thật có thể có chiến lược tối ưu khác.

### 11.2. Vì sao ví dụ không phải benchmark?

Mã in ra console, tạo record và chạy chỉ năm lần. Đo thời gian trực tiếp trên chương trình này chủ yếu phản ánh thời gian khởi động JVM, JIT chưa ổn định và I/O console. Nó phù hợp để **kiểm chứng kết quả lựa chọn**, nhưng không đủ để kết luận overhead AOP là bao nhiêu nanosecond hay phần trăm.

Để đo hiệu năng có ý nghĩa, phải dựng workload đại diện, tách chi phí instrumentation/advice và kiểm soát warm-up, tải đồng thời, phân bổ bộ nhớ cũng như môi trường chạy.

## 12. Kiểm thử predicate độc lập với weaving

Để giảm độ khó gỡ lỗi, nên tách logic chính sách đơn giản thành thành phần có thể kiểm thử độc lập trước khi đưa vào điểm can thiệp AOP.

Ví dụ một predicate tương ứng với quy tắc:

```java
static boolean requiresAudit(String accountId, long amount) {
    return accountId != null
            && accountId.startsWith("VIP-")
            && amount > 1000;
}
```

Đây là **mảnh mã Java minh họa**, không phải chương trình độc lập có `main`.

Có thể kiểm tra predicate bằng bộ giá trị biên, bao gồm null, giá trị đúng ngưỡng và sát ngưỡng. Sau đó cần **một lớp kiểm thử tích hợp riêng** để chứng minh công cụ AOP truyền đúng args/ngữ cảnh vào predicate và advice chỉ chạy tại đúng những join point.

Không nên gộp tất cả logic vào biểu thức có nhiều điều kiện không quan sát được, rồi chỉ kiểm tra kết quả cuối cùng. Khi có lỗi, ta cần biết:

1. Điểm có thuộc tập tĩnh không?
2. Ngữ cảnh động được lấy đúng chưa?
3. Predicate có trả đúng giá trị không?
4. Advice có chạy đúng số lần không?
5. Nghiệp vụ còn giữ đúng hợp đồng không?

### 12.1. Lập bảng quyết định độc lập

Một bảng ca kiểm thử nên xuất phát từ yêu cầu thực tế:

```text
RULE: AUDIT iff operation == transfer AND account begins VIP-
      AND amount > 1000.

POSITIVE:
  transfer(VIP-1,1001)
  transfer(VIP-2,5000)

NEGATIVE:
  lookup(VIP-1,5000)
  transfer(STD-1,5000)
  transfer(VIP-1,1000)
  transfer(null,5000)
```

Đây là **mẫu kiểm thử bằng văn bản**, không phải output chạy tự động. Nếu yêu cầu thay đổi, sửa bảng quyết định trước rồi cập nhật pointcut/advice cho khớp.

## 13. Kiểm thử ngữ cảnh đối tượng và kiểu runtime

Một số điều kiện động xét đối tượng đích (`target`) hoặc đối tượng hiện hành (`this` trong mô hình công cụ). Với đa hình, kiểu khai báo và kiểu runtime có thể khác nhau.

Ví dụ:

```java
interface PaymentGateway {
    void charge(long amount);
}

final class RemoteGateway implements PaymentGateway {
    @Override
    public void charge(long amount) {}
}
```

Đây là **đoạn khai báo minh họa**, chưa là ứng dụng độc lập. Nếu code gọi qua biến kiểu `PaymentGateway` nhưng đích runtime là `RemoteGateway`, điều kiện dựa trên kiểu khai báo có thể khác điều kiện dựa trên đối tượng runtime.

Các ca nên kiểm tra:

- Lời gọi trên nhiều lớp thực hiện cùng giao diện.
- Một subtype được thêm sau khi pointcut được viết.
- Lời gọi qua proxy hoặc đối tượng trung gian.
- Lời gọi trực tiếp tới đích.
- Phương thức overload/override liên quan.
- Trường hợp không có đối tượng instance (ví dụ một số điểm tĩnh), nếu loại join point hỗ trợ.

**Đừng tự suy rằng `this` và `target` luôn đồng nhất.** Ngữ nghĩa phụ thuộc công cụ và loại join point; một lớp trung gian có thể khiến đối tượng bên gọi, proxy và đối tượng đích khác nhau.

## 14. Kiểm thử điều kiện theo control flow

Một chính sách có thể yêu cầu advice chỉ áp dụng khi thao tác xảy ra **bên trong luồng thực thi** của một công việc lớn.

Ví dụ:

```text
runBatch()
   |
   +-- stepA() ----> repository.save()
   |
   +-- stepB() ----> repository.save()

interactiveRequest() --> repository.save()
```

Nếu mục tiêu là quan sát `save` khi đang ở trong `runBatch`, cần xác định rõ liệu “trong luồng” nghĩa là:

- Lời gọi lồng nhau đồng bộ theo call stack.
- Công việc bất đồng bộ do batch khởi phát rồi chạy ở thread khác.
- Một trace/correlation ID được truyền giữa các tác vụ.

Ba cách hiểu này **không tự tương đương**. Một điều kiện theo call stack không tự động theo tác vụ sang mọi luồng khác.

Kiểm thử nên bao gồm:

1. `save` được gọi đồng bộ từ `runBatch` — mong muốn được chọn.
2. `save` từ một yêu cầu tương tác độc lập — không được chọn.
3. `runBatch` lồng trong `runBatch` — ngữ cảnh bắt đầu/kết thúc phải cân bằng.
4. `runBatch` ném ngoại lệ — trạng thái control-flow phải được giải phóng.
5. Batch chuyển công việc sang thread khác — xác minh phạm vi mong muốn và ngữ nghĩa mà công cụ hỗ trợ.

Nếu tự hiện thực tracking, không được chỉ bật một cờ `boolean inBatch` toàn cục mà không phân biệt thread/lần gọi; điều đó có thể làm tất cả yêu cầu đồng thời bị đánh dấu sai.

## 15. Đồng thời, đệ quy và reentrancy

Điều kiện động thường cần dữ liệu theo từng invocation hoặc từng luồng điều khiển. Điều này tạo các trường hợp phức tạp khi ứng dụng đa luồng, đệ quy hoặc advice tự kích hoạt lại những điểm liên quan.

### 15.1. Đồng thời

Giả sử cùng một phương thức `transfer` chạy trên hai thread:

```text
Thread A: transfer("VIP-1",5000) --> selected
Thread B: transfer("STD-2",9000) --> not selected
```

Nếu aspect lưu biến instance `currentAccount` dùng chung rồi thay ở mỗi lần gọi, điều kiện có thể đọc dữ liệu của thread khác. Nên dùng ngữ cảnh gắn với invocation, giá trị bất biến hoặc cơ chế đồng bộ/truyền ngữ cảnh phù hợp.

### 15.2. Đệ quy

Một phương thức đệ quy có thể tạo nhiều lần xuất hiện cùng join point đang lồng nhau. Nếu điều kiện động phụ thuộc vào độ sâu, cần xác định ngữ nghĩa rõ và giảm độ sâu đúng khi thoát, kể cả trên nhánh ngoại lệ.

Sử dụng một biến `currentDepth` dùng chung không bảo vệ cho nhiều thread là không an toàn; một biến theo thread cũng chưa tự giải quyết việc truyền context qua ranh giới bất đồng bộ.

### 15.3. Advice tự kích hoạt lại advice

Nếu advice ghi log bằng cách gọi phương thức cũng khớp pointcut, có thể xuất hiện vòng gọi lặp ngoài ý muốn:

```text
advice -> logger.log() -> join point log được chọn -> advice -> ...
```

Cần giới hạn phạm vi pointcut, lựa chọn điểm logging ngoài tập cần can thiệp hoặc sử dụng cơ chế ngăn reentrancy thích hợp. Đừng chỉ giải quyết bằng một biến cờ global vì có thể chặn nhầm các invocation độc lập.

## 16. Bất đồng bộ: method return khác completion

Một phương thức bất đồng bộ có thể trả về `CompletableFuture<Result>` trước khi công việc hoàn tất.

```text
Caller
  |
  |-- startJob() ------> advice quanh method execution
  |                     -> tạo Future
  |<-- Future ---------- method đã trả về
  |
  |       ... công việc chạy ở thời điểm/luồng khác ...
  |
  +<-- Future complete(result/error)
```

Nếu điều kiện động dựa trên args của `startJob`, nó có thể được kiểm tra ở lúc gọi. Nhưng nếu chính sách dựa trên **kết quả công việc**, thông tin đó chưa tồn tại khi phương thức chỉ mới trả Future.

Một advice quan sát “method returned normally” không thể mặc định kết luận “job completed successfully”. Cần xác định đúng ranh giới hoàn tất và cách mang ngữ cảnh từ invocation ban đầu tới completion callback nếu chính sách yêu cầu.

Kiểm thử nên có:

- Future hoàn tất bình thường.
- Future hoàn tất thất bại.
- Future bị hủy.
- Completion xảy ra trên thread khác.
- Nhiều Future chạy đồng thời.
- Ngữ cảnh của từng request được truyền đúng và giải phóng sau completion.

Chi phí theo dõi context có thể bao gồm cấp phát callback, lưu correlation ID hoặc quản lý vòng đời. Nó khác với chi phí predicate trên phương thức khởi tạo.

## 17. Lỗi từ predicate và chiến lược xử lý

Điều kiện động không phải lúc nào cũng chỉ trả `true` hoặc `false`. Việc đọc trạng thái, parsing hoặc gọi helper có thể ném lỗi.

Cần quyết định rõ theo hợp đồng:

| Tình huống | Câu hỏi thiết kế |
| --- | --- |
| Đối số null/không hợp lệ | Loại điểm, hay thất bại rõ ràng? |
| Chính sách runtime không khả dụng | Cho phép, từ chối, hay lỗi tới caller? |
| Predicate ném ngoại lệ | Lỗi có được truyền tiếp hay chuyển đổi? |
| Predicate quá chậm | Có timeout/budget không? |
| Dữ liệu thay đổi trong khi kiểm tra | Dùng snapshot/phiên bản nào? |
| Advice lỗi sau khi predicate khớp | Có che ngoại lệ nghiệp vụ gốc không? |

Với telemetry tùy chọn, việc ghi nhận thất bại có thể được xử lý khác với kiểm soát truy cập bắt buộc. **Không có chính sách fail-open/fail-closed đúng cho mọi aspect.** Việc chọn phải dựa vào mục tiêu nghiệp vụ và ranh giới tin cậy.

Quan trọng hơn: nếu kiểm soát truy cập chỉ dựa vào một điều kiện động ở các điểm AOP, cần chứng minh **mọi đường vào cần bảo vệ** thực sự đi qua cơ chế can thiệp. Một đường gọi trực tiếp bỏ qua proxy có thể khiến mọi phép kiểm tra predicate đều vô nghĩa.

## 18. Cách benchmark điều kiện động có cơ sở

Không nên kết luận “điều kiện động chậm” chỉ từ một lần `System.nanoTime()` quanh vài lệnh. JVM có warm-up, JIT, GC, biến động tải và rất nhiều yếu tố nhiễu.

Một kế hoạch đo phù hợp:

1. **Xác định câu hỏi:** muốn đo overhead của pointcut, của predicate hay của toàn bộ advice?
2. **Dựng các biến thể:** baseline không interception; có interception nhưng điều kiện luôn trượt; điều kiện khớp với advice nhẹ; điều kiện khớp với advice thật.
3. **Giữ workload đại diện:** đúng phân phối tham số, tỷ lệ khớp và tần suất các phương thức nóng.
4. **Kiểm soát JVM và môi trường:** phiên bản JDK, warm-up, CPU, heap, mức tải và GC.
5. **Dùng công cụ benchmark phù hợp:** JMH hữu ích cho microbenchmark Java; kiểm tra thực tế thêm bằng profiling/load test.
6. **Đo nhiều chiều:** throughput, p50/p95/p99 latency, allocation rate, GC, CPU và contention.
7. **Lặp lại và báo độ biến thiên:** không suy từ một con số duy nhất.
8. **Đối chiếu correctness:** một biến thể nhanh nhưng bỏ sót advice cần chạy không phải tối ưu thành công.

Một cách phân tích:

```text
baseline                             : không có cơ chế AOP
interception + always-false condition : chủ yếu đo đường chặn/kiểm tra
interception + simple dynamic condition: thêm chi phí predicate
interception + matching advice         : thêm chi phí hành vi bổ sung
```

Mô hình này chỉ có ý nghĩa nếu các biến thể thực sự so sánh cùng công việc và kết quả vẫn đúng. Một điều kiện luôn sai có thể được tối ưu khác một điều kiện động thật; cần diễn giải số đo cẩn trọng.

### 18.1. Tỷ lệ khớp ảnh hưởng số đo thế nào?

Nếu 99% lần gọi không khớp, phần đáng chú ý có thể là chi phí kiểm tra ở các lần bị loại. Nếu 90% lần gọi khớp và advice ghi log đồng bộ, phần advice có thể chi phối latency.

Vì vậy báo cáo benchmark nên kèm **match rate** và số invocation/giây. Một số đo chỉ có “ns/op” nhưng không nêu cấu hình lọc và tỷ lệ khớp khó áp dụng sang tải thực.

## 19. Cách cải thiện hiệu năng mà vẫn giữ tính đúng đắn

Các hướng cải thiện thường đáng cân nhắc:

- **Thu hẹp tập điểm tĩnh:** chỉ áp dụng điều kiện động tại những phương thức/thành phần thật sự có liên quan.
- **Dùng dữ liệu đã có:** tránh lấy lại metadata hoặc chuyển đổi đối số nhiều lần nếu có thể sử dụng an toàn.
- **Giữ predicate đơn giản, thuần và ít cấp phát:** tránh regex phức tạp, phản xạ lặp lại hoặc I/O không cần thiết trên hot path.
- **Tối ưu luồng chọn điểm theo hợp đồng công cụ:** không phụ thuộc vào thứ tự đánh giá ngoài những điều engine cam kết.
- **Giảm chi phí advice riêng biệt:** batch metric/log, chọn dữ liệu cần thiết và tránh chặn I/O không cần thiết.
- **Kiểm soát tần suất quan sát:** sampling có thể phù hợp với telemetry, nhưng không được dùng để làm rơi các lần audit bắt buộc.
- **Dùng cache có điều kiện:** chỉ khi khóa/phiên bản phản ánh đủ dữ liệu quyết định.
- **Kiểm tra lại coverage sau tối ưu:** tăng tốc không được sinh false negative hoặc false positive.

Một ví dụ trade-off: chuyển từ điều kiện theo giá trị động sang chọn theo annotation tĩnh có thể giảm phép kiểm tra runtime, nhưng chỉ đúng nếu **ý định chính sách thực sự có thể biểu diễn bằng metadata tĩnh**. Nếu chính sách cần phân biệt `amount=50` và `amount=5000` trên cùng một phương thức, annotation đơn độc không thay thế được điều kiện theo giá trị.

## 20. Những sai lầm thường gặp

**“Điều kiện động luôn chậm.”** Sai. Phép so sánh rẻ có thể không đáng kể; tần suất chạy, cách lấy context và advice mới quyết định tác động.

**“Pointcut không khớp thì không có chi phí.”** Chưa chắc. Hệ thống có thể phải chạy interceptor hoặc đánh giá một phần điều kiện trước khi biết nó không khớp.

**“Đặt điều kiện rẻ đầu biểu thức đảm bảo engine kiểm tra trước.”** Không phải cam kết chung của mọi DSL/công cụ. Có thể có tối ưu hoặc sắp xếp lại.

**“Cache kết quả theo tên phương thức sẽ tăng tốc an toàn.”** Sai nếu điều kiện phụ thuộc args, target, control flow hoặc trạng thái thay đổi.

**“Unit test predicate đủ chứng minh pointcut.”** Không. Cần cả kiểm thử loại join point, weaving/interception và đường thực thi.

**“Một lần thấy advice chạy chứng minh không có bỏ sót.”** Sai. Cần các ca âm tính, đường gọi khác và trường hợp biên.

**“Điều kiện theo thread tự động theo tác vụ bất đồng bộ.”** Không. Context có thể cần được truyền qua ranh giới tác vụ bằng cơ chế thích hợp.

**“Ghi lại mọi tham số để kiểm thử sẽ an toàn.”** Không. Có thể làm lộ dữ liệu nhạy cảm; nên dùng dữ liệu tổng hợp và chọn lọc trường ghi nhận.

**“Đo trên vòng lặp in console là benchmark AOP.”** Không. I/O và JVM startup làm sai lệch phép đo, chưa kể chương trình mô phỏng không có weaving thật.

**“Điều kiện dynamic khớp thì nghiệp vụ chắc chắn thực thi.”** Không. Advice bao quanh có thể thay đổi việc chuyển tiếp, và các advice khác có thể thay đổi luồng.

## 21. Bài tập tự kiểm tra

**Bài 1.** Một phương thức được gọi 2 triệu lần mỗi giây. Predicate đơn giản tốn trung bình 50 ns/lần. Tổng chi phí CPU lý tưởng cỡ nào?

```text
2.000.000 * 50 ns = 100.000.000 ns = 100 ms CPU mỗi giây.
```

Đây là ước lượng lý tưởng theo giả định, chưa tính interception, JIT, phân bổ và contention. Không coi nó là đo thực nghiệm.

**Bài 2.** Một bộ lọc theo chữ ký `transfer` đi qua 1.000 lần; 100 lần vượt ngưỡng. Có thể tiết kiệm 900 lần kiểm tra động không?

Không chỉ từ thông tin “100 lần vượt ngưỡng”, vì muốn biết lần nào vượt ngưỡng hệ thống có thể phải **kiểm tra cả 1.000 lần**. Có thể giảm công việc nếu tồn tại bộ lọc trước rẻ hơn hoặc metadata/cấu trúc phân biệt được các nhóm, nhưng phải giữ đúng kết quả.

**Bài 3.** Tại sao cached `selected=true` theo chữ ký `transfer(String,long)` có thể sai?

Vì lần gọi sau có số tiền, đối tượng hoặc trạng thái khác. Kết quả động không chỉ phụ thuộc chữ ký.

**Bài 4.** Predicate `amount > 1000` được unit test đầy đủ nhưng advice không chạy ở một lời gọi nội bộ. Cần kiểm tra đâu?

Kiểm tra cơ chế weaving/interception và đường gọi: nếu dùng proxy, self-invocation có thể bỏ qua proxy. Kiểm tra tiếp loại join point và các điều kiện khác của pointcut.

**Bài 5.** Nếu một công việc được gửi sang executor, `cflow` theo call stack của thread gọi có chắc áp dụng cho công việc ở thread mới không?

Không thể mặc định. Phải xác minh ngữ nghĩa của công cụ; chuyển công việc bất đồng bộ không tự động giữ cùng call stack hoặc mọi loại context.

**Bài 6.** Một điều kiện động gọi mạng mỗi khi đánh giá nhưng chỉ 1% lần gọi được advice. Vì sao vẫn có thể chậm?

Vì 99% lần trượt cũng có thể phải chờ kiểm tra mạng. Cần đo **chi phí chọn điểm**, không chỉ chi phí advice chạy khi khớp.

**Bài 7.** Khi tối ưu pointcut, ca kiểm thử nào đảm bảo không làm hỏng tính đúng đắn?

Cần đối chiếu tập dương/âm của chính sách, điều kiện biên, ngoại lệ, đồng thời/bất đồng bộ và kiểm thử tích hợp chứng minh đúng phạm vi weaving. Benchmark nhanh hơn mà thay đổi tập chọn là thất bại về correctness.

## 22. Checklist thực hành

Trước khi đưa pointcut có điều kiện động vào hệ thống:

1. [ ] Có mô tả rõ **lần thực thi nào cần được advice xử lý**.
2. [ ] Đã phân biệt **điều kiện tĩnh** và **điều kiện runtime**.
3. [ ] Đã xác nhận loại join point và phạm vi công cụ hỗ trợ.
4. [ ] Có ca unit test đúng/sai/biên/null cho predicate.
5. [ ] Có ca kiểm thử dương và âm cho tập join point.
6. [ ] Có kiểm thử tích hợp cho đường gọi trực tiếp, qua trung gian và nội bộ khi liên quan.
7. [ ] Có chính sách rõ cho ngoại lệ và dữ liệu runtime không khả dụng.
8. [ ] Có kiểm thử đồng thời/đệ quy/context nếu điều kiện phụ thuộc trạng thái.
9. [ ] Có kiểm thử ranh giới bất đồng bộ nếu dùng context hoặc kết quả task.
10. [ ] Đã đo overhead trên tải phù hợp và tách chi phí predicate khỏi advice.
11. [ ] Không dùng caching, sampling hoặc tối ưu khác làm sai tập điểm bắt buộc.
12. [ ] Có quy tắc tránh log dữ liệu nhạy cảm và vòng gọi tái nhập không mong muốn.

**Kết luận:** điều kiện động giúp pointcut chính xác theo ngữ cảnh của từng invocation, đổi lại có thể yêu cầu kiểm tra runtime và làm tăng số trường hợp cần kiểm thử. Thiết kế tốt kết hợp **lọc cấu trúc tĩnh phù hợp**, **predicate động có hợp đồng rõ**, **kiểm thử trên đường thực thi thật** và **phép đo hiệu năng có cơ sở**. Không đánh đổi tính đúng đắn của phạm vi chỉ để có biểu thức ngắn hoặc số đo nhanh hơn.


</details>

- [Quay lại đầu trang](#back-to-top)
