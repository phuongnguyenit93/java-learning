<a id="back-to-top"></a>

# Pointcut cơ bản

## Menu
- [Chọn điểm theo kiểu, tên và chữ ký phương thức](#pointcut-basics-001)
- [Giới hạn theo phạm vi gói, mô-đun hoặc thành phần](#pointcut-basics-002)
- [Khớp kiểu trả về, danh sách tham số và mức truy cập](#pointcut-basics-003)
- [Phân biệt kiểu khai báo và kiểu thực tế khi lựa chọn điểm can thiệp](#pointcut-basics-004)
- [Kế thừa, ghi đè phương thức và ảnh hưởng của đa hình](#pointcut-basics-005)
- [Ký tự đại diện và biểu thức mẫu theo cú pháp công cụ](#pointcut-basics-006)
- [Kết hợp điều kiện bằng phép và, hoặc và phủ định](#pointcut-basics-007)
- [Đặt tên pointcut để tái sử dụng và diễn đạt ý định](#pointcut-basics-008)
- [Phân biệt điều kiện bao gồm với điều kiện loại trừ](#pointcut-basics-009)
- [Chọn phương thức nạp chồng và tránh khớp ngoài ý muốn](#pointcut-basics-010)
- [Đánh đổi giữa biểu thức ngắn và độ chính xác của phạm vi](#pointcut-basics-011)
- [Kiểu tổng quát, mã sinh và phương thức cầu nối khi nền tảng có](#pointcut-basics-012)

## <a id="pointcut-basics-001">Chọn điểm theo kiểu, tên và chữ ký phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn điểm theo kiểu, tên và chữ ký phương thức

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**), **pointcut** là quy tắc chọn những **join point** (điểm can thiệp) mà advice được áp dụng. Một phương pháp nền tảng là lọc các điểm liên quan đến phương thức dựa trên ba yếu tố: **kiểu sở hữu (type)**, **tên phương thức (method name)** và **chữ ký phương thức (method signature)**.

Ví dụ, chính sách “ghi audit khi lưu tài liệu” có thể chọn đúng phương thức `DocumentOperations.save(String)`. Nó phải loại trừ `DocumentOperations.save(int)`, `DocumentOperations.find(String)` và `CustomerOperations.save(String)`, mặc dù các phương thức ấy trùng một phần tên, kiểu hoặc tham số.

**Ý tưởng trung tâm:** pointcut phải chọn đúng những điểm cần áp dụng hành vi xuyên suốt, không bao phủ các điểm khác ngoài ý muốn. Điều này ảnh hưởng trực tiếp đến tính đúng đắn của audit, logging, metrics, kiểm soát truy cập và các chính sách AOP khác.

> Bài này mô tả mô hình AOP **nói chung**. Các mẫu `type=...`, `name=...` và `parameters=...` chỉ là **ký pháp khái niệm**, không phải cú pháp pointcut thống nhất hoặc có thể chạy trong một thư viện cụ thể. Khả năng chọn một loại join point còn phụ thuộc kỹ thuật weaving/interception.

## 1. Pointcut đứng ở đâu trong mô hình AOP?

**Join point** là sự kiện/vị trí thực thi mà cơ chế AOP có thể can thiệp. Với phương thức, những kiểu thường được mô hình hóa là **method call** (vị trí phát lời gọi) và **method execution** (ranh giới phương thức đích bắt đầu chạy). Không phải công cụ nào cũng hỗ trợ cả hai.

**Pointcut** là tiêu chí chọn các join point. **Advice** là đoạn xử lý chạy trước, sau, khi lỗi hoặc bao quanh những điểm đã chọn. **Aspect** thường đóng gói pointcut cùng advice phục vụ một mối quan tâm cắt ngang.

```text
Các điểm có thể can thiệp
    |
    |-- DocumentOperations.save(String)
    |-- DocumentOperations.save(int)
    |-- DocumentOperations.find(String)
    |-- CustomerOperations.save(String)
    |-- CustomerOperations.find(String)
    |
    v
Pointcut:
    type       = DocumentOperations
    name       = save
    parameters = [String]
    |
    v
Chỉ chọn DocumentOperations.save(String)
    |
    v
Advice được áp dụng khi join point tương ứng thực sự xảy ra
```

Một phương thức thỏa quy tắc về metadata nhưng bị gọi qua đường mà công cụ không quan sát được thì advice vẫn có thể không chạy. **Matcher đúng** và **weaving/interception bao phủ đúng** là hai điều kiện riêng.

### Số vị trí tĩnh và số lần thực thi động

Một phương thức có thể được gọi nhiều lần, từ nhiều nơi; một call site nằm trong vòng lặp cũng có thể phát sinh nhiều lượt gọi. Nếu một pointcut khớp và cơ chế hỗ trợ mọi lượt đó, advice có thể chạy nhiều lần. Không được suy luận “một method được chọn” là “advice chỉ chạy một lần”.

## 2. Ba điều kiện lọc và phép giao

| Điều kiện | Câu hỏi | Ví dụ |
| --- | --- | --- |
| **Kiểu** | Phương thức thuộc API/implementation nào? | `DocumentOperations` |
| **Tên** | Phương thức thực hiện thao tác tên gì? | `save` |
| **Chữ ký** | Tên và danh sách kiểu tham số có phù hợp không? | `save(String)` |
| **Kết hợp** | Cả ba điều kiện có cùng đúng không? | `DocumentOperations.save(String)` |

Một bộ lọc có thể mô tả bằng pseudocode:

```text
matches(joinPoint) =
    typeMatches(joinPoint)
    AND nameMatches(joinPoint)
    AND signatureMatches(joinPoint)
```

Khi điều kiện kiểu đúng nhưng tên sai, hoặc tên đúng nhưng tham số sai, kết quả phải **không chọn**. Việc ghép các điều kiện bằng AND làm phạm vi hẹp hơn; OR làm phạm vi rộng hơn; NOT loại trừ một tập không mong muốn. Nếu công cụ hỗ trợ biểu thức phức hợp, cần có dấu ngoặc để thể hiện đúng ý định.

**Đừng nhầm việc dùng reflection để tìm các `Method` có metadata phù hợp với AOP đầy đủ.** AOP còn phải kết hợp advice vào join point trong luồng thực thi.

## 3. Lọc theo kiểu (type)

Đây là ranh giới để tránh chọn nhầm hai method cùng tên và tham số nhưng ở hai thành phần không liên quan.

```java
// Khai báo minh họa, chưa phải chương trình chạy độc lập.
interface DocumentOperations {
    void save(String documentId);
}

interface CustomerOperations {
    void save(String customerId);
}
```

Hai giao diện có `save(String)`. Nếu quan tâm audit lưu tài liệu, bộ lọc theo **type = DocumentOperations** loại phương thức của `CustomerOperations`.

### 3.1. “Type” có thể mang những nghĩa khác nhau

Phải xác định cơ chế của bạn xét loại nào:

- **Declaring type:** kiểu thực sự khai báo phương thức. Trong Java reflection, `Method.getDeclaringClass()` cho biết điều này.
- **Static receiver type:** kiểu mà mã caller biết ở compile time (ví dụ biến được khai báo là interface).
- **Runtime receiver/target type:** lớp của đối tượng thực tế nhận lời gọi.
- **Type hierarchy:** một loại chính xác, các subtype, các lớp triển khai interface hoặc một tập kiểu trong package.

Ví dụ:

```java
DocumentOperations operations = new FileDocumentService();
operations.save("D-1");
```

Đây là mã minh họa, giả định `FileDocumentService` triển khai interface. Lời gọi được viết qua kiểu tĩnh `DocumentOperations`, nhưng method chạy thật có thể nằm ở `FileDocumentService`.

Một pointcut chọn **call** ở phía caller có thể dựa vào thông tin phương thức tĩnh của lời gọi; một pointcut chọn **execution** có thể quan tâm đến implementation thực thi. Không tự giả định “type” là runtime class trong mọi ngôn ngữ pointcut.

### 3.2. Kế thừa khiến declaring type khác runtime type

```java
class BaseDocumentService {
    public void save(String id) { }
}

class CachedDocumentService extends BaseDocumentService {
    // Không override: kế thừa save(String).
}
```

Method `save(String)` được **khai báo ở `BaseDocumentService`** dù có thể gọi trên một instance `CachedDocumentService`. `getDeclaredMethods()` của lớp con không bao gồm phương thức kế thừa; `getMethods()` có thể tìm public method được kế thừa.

Do đó, “declaring type chính xác là lớp con” và “target là subtype của lớp cha” là hai quy tắc khác nhau. Chọn kiểu theo contract thật sự cần bao phủ, rồi kiểm thử trường hợp inherited method và override.

### 3.3. Package và subtype không đồng nghĩa

“Thuộc package `com.example.billing`” không có nghĩa “là subtype của `BillingOperations`”. Package là vị trí tổ chức tên; subtype là quan hệ kiểu. Một số DSL phân biệt wildcard cho đúng một package với mẫu bao gồm subpackage. Hãy xác minh cú pháp của công cụ đang dùng.

Một pointcut chọn kiểu theo interface ổn định thường thể hiện intent rõ hơn chỉ khớp class có tên chứa `Service`, nhưng interface cũng không tự bảo đảm mọi implementation sẽ được bao phủ bởi mọi kỹ thuật interception.

## 4. Lọc theo tên phương thức (method name)

Tên là tín hiệu dễ đọc nhưng chưa mô tả đầy đủ hành vi.

### 4.1. Khớp chính xác

`name = "save"` có nghĩa tên phải đúng `save`. Nó không tự khớp `saveAll` hoặc `resave`. Nhưng nó có thể chọn nhiều overload `save(...)` và nhiều type cùng tên method.

### 4.2. Khớp bằng mẫu

Các mục tiêu có thể là:

```text
name exactly "find"                   -> find
name starts with "find"              -> find, findAll, findById
name ends with "Async"               -> loadAsync, publishAsync
name in {"create", "update", "delete"} -> một tập tên rõ ràng
```

Đây là **mô tả ý định**, không phải biểu thức pointcut có thể chạy nguyên xi. Wildcard kiểu glob, biểu thức chính quy và ký pháp của một engine AOP có thể khác nhau. Trong Java, regex dùng các quy tắc khác glob, chẳng hạn `.*` chứ không đơn thuần `*` để đại diện một chuỗi tùy ý.

### 4.3. Tên là một phụ thuộc cần kiểm thử khi refactor

Nếu một pointcut chọn `save*`, nó có thể vô tình chọn `saveTemporary` vừa được thêm. Nếu `save` đổi thành `persist`, nó có thể ngừng khớp, dù hành vi nghiệp vụ không đổi. Cần kiểm thử cả phương thức *phải khớp* và *không được khớp* khi đổi tên, thêm overload hoặc chuyển loại.

## 5. Chữ ký phương thức trong Java gồm những gì?

Theo định nghĩa của ngôn ngữ Java, **method signature** gắn với **tên method** và **danh sách các kiểu tham số** theo quy tắc ngôn ngữ (có xét việc điều chỉnh kiểu/generic khi thích hợp). **Return type, modifiers và danh sách `throws` không thuộc chữ ký Java.**

Một **mẫu chọn AOP** vẫn có thể hỗ trợ ràng buộc thêm return type, visibility, static/instance, annotation hoặc type khai báo. Những ràng buộc ấy không nên được gọi lẫn thành “thành phần chữ ký Java”.

### 5.1. Overload: cùng tên, khác tham số

```java
// Mã khai báo minh họa.
class DocumentStore {
    void save(String id) { }
    void save(int id) { }
    void save(String id, boolean overwrite) { }
}
```

| Method | Số/thứ tự kiểu tham số | Có khớp `save(String)`? |
| --- | --- | --- |
| `save(String)` | Một `String` | Có |
| `save(int)` | Một `int` | Không |
| `save(String, boolean)` | `String` rồi `boolean` | Không |

Một pattern chỉ ràng buộc tên `save` sẽ rộng hơn nhiều. Pattern tham số “đúng một String” khác pattern “String đầu tiên, phía sau có thể còn tham số”.

### 5.2. Thứ tự tham số có ý nghĩa

```java
void transfer(String account, long amount);
void transfer(long amount, String account);
```

Hai method trên khác signature vì thứ tự kiểu tham số khác nhau. Nếu matcher chỉ kiểm tra cùng một *tập* kiểu mà quên thứ tự, có thể chọn nhầm. Khi dùng reflection, so sánh `Class<?>[]` theo thứ tự, chẳng hạn `Arrays.equals(...)`.

### 5.3. Primitive và wrapper khác nhau

```java
void setLimit(int max);
void setLimit(Integer max);
```

`int.class` và `Integer.class` là hai loại khác nhau trong reflection. Java có boxing/unboxing tại call site, nhưng matcher chữ ký theo parameter type không tự động coi chúng tương đương. Nếu muốn chọn cả hai, hãy diễn đạt một OR rõ ràng.

### 5.4. Array và varargs

```java
// Hai biến thể minh họa riêng biệt, không được đặt đồng thời
// trong cùng một lớp như hai overload độc lập.
void publish(String[] ids);
void publish(String... ids);
```

Cả hai mang tham số `String[]` ở mức JVM. Không thể overload chỉ dựa vào việc thay cú pháp array bằng varargs vì cùng dạng signature. Reflection `getParameterTypes()` trả `String[].class` trong cả hai trường hợp; `isVarArgs()` có thể giúp kiểm tra method khai báo dưới dạng varargs.

### 5.5. Kiểu trả về và throws là điều kiện *bổ sung*

```java
// Minh họa khai báo, không phải tệp chạy độc lập.
public String find(String id) throws java.io.IOException;
```

- **Signature Java:** `find(String)`.
- **Return type:** `String`; có thể là một điều kiện lọc khác.
- **Visibility:** `public`; có thể là điều kiện lọc khác.
- **Declared exception:** `IOException`; không tạo signature riêng và không bao quát mọi lỗi runtime.

Không thể tạo hai overload Java cùng tên/cùng tham số mà **chỉ khác return type** trong cùng lớp. Vì vậy một pointcut có điều kiện return type đang *lọc thêm*, không làm xuất hiện overload hợp lệ mới.

## 6. Ví dụ Java 17+ chạy được: chọn đúng một overload

Ví dụ này xây một matcher từ **type + name + parameter types**, thêm **return type** làm bộ lọc phụ. Nó dùng `java.lang.reflect.Proxy` trong Java SE để chạy một “before advice” khi một lời gọi đi qua proxy có method phù hợp.

Đây là ví dụ **interception có điều kiện**, chưa phải một công cụ AOP hỗ trợ mọi join point hoặc một cú pháp pointcut chuẩn. Nó cho thấy cả cách khớp lẫn giới hạn khi lời gọi không đi qua proxy.

Lưu file **`PointcutSelectionDemo.java`** bằng UTF-8:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

public class PointcutSelectionDemo {
    interface DocumentOperations {
        void save(String id);
        void save(int id);
        String find(String id);
    }

    static final class DocumentService implements DocumentOperations {
        @Override
        public void save(String id) {
            System.out.println("business.save(String): " + id);
        }

        @Override
        public void save(int id) {
            System.out.println("business.save(int): " + id);
        }

        @Override
        public String find(String id) {
            System.out.println("business.find(String): " + id);
            return "found:" + id;
        }
    }

    static boolean matches(Method method) {
        boolean typeMatches =
                method.getDeclaringClass() == DocumentOperations.class;
        boolean nameMatches = method.getName().equals("save");
        boolean parameterMatches = Arrays.equals(
                method.getParameterTypes(),
                new Class<?>[] { String.class });

        // Return type là điều kiện bổ sung, ngoài chữ ký Java.
        boolean returnMatches = method.getReturnType() == void.class;

        return typeMatches && nameMatches
                && parameterMatches && returnMatches;
    }

    static DocumentOperations withAdvice(DocumentOperations target) {
        return (DocumentOperations) Proxy.newProxyInstance(
                DocumentOperations.class.getClassLoader(),
                new Class<?>[] { DocumentOperations.class },
                (proxy, method, arguments) -> {
                    if (matches(method)) {
                        System.out.println(
                                "[advice] " + method.getName() + "(String)");
                    }
                    try {
                        return method.invoke(target, arguments);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                });
    }

    public static void main(String[] args) {
        DocumentOperations target = new DocumentService();
        DocumentOperations service = withAdvice(target);

        service.save("DOC-7");
        service.save(42);
        System.out.println(service.find("DOC-7"));

        // Gọi trực tiếp: không qua proxy.
        target.save("DIRECT");
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 PointcutSelectionDemo.java
java PointcutSelectionDemo
```

**Standard output chính xác:**

```text
[advice] save(String)
business.save(String): DOC-7
business.save(int): 42
business.find(String): DOC-7
found:DOC-7
business.save(String): DIRECT
```

### 6.1. Vì sao chỉ dòng đầu có advice?

`service.save("DOC-7")` gọi overload `save(String)` trên interface proxy, thỏa cả type, name, params và return. Handler in dòng `[advice]` trước khi `method.invoke(...)` chuyển tiếp sang target.

`service.save(42)` có tham số khai báo `int`, nên không thỏa `parameterMatches`. `service.find("DOC-7")` không thỏa `nameMatches`. `target.save("DIRECT")` khớp chữ ký về mặt metadata nhưng **đi trực tiếp tới target**, bỏ qua handler. Đây là bằng chứng việc định nghĩa bộ lọc đúng chưa đảm bảo mọi đường gọi đã được interception.

### 6.2. Vai trò từng API reflection

- `Method.getDeclaringClass()`: biết type **khai báo method**. Ở ví dụ, đối tượng `Method` được handler nhận từ interface proxy; không nên diễn giải nó là runtime class của target.
- `Method.getName()`: lấy tên method, không có thông tin số/kiểu tham số.
- `Method.getParameterTypes()`: lấy danh sách `Class<?>` của tham số sau erasure, có thứ tự.
- `Method.getReturnType()`: lấy kiểu trả về, dùng như bộ lọc bổ sung.
- `Method.invoke(target, arguments)`: gọi method trên target, và có thể bọc ngoại lệ do target ném trong `InvocationTargetException`; đoạn `catch` mở lớp bọc để giữ lỗi gốc.

Ví dụ chọn đúng một overload và có kết quả cố định, nên có thể kiểm chứng dễ dàng. Nó không minh họa interception của private/static method, weaving vào bytecode hay lời gọi nội bộ.

## 7. Wildcard và chọn một họ phương thức

Không phải lúc nào cần chọn chính xác một method. Một chính sách đo thời gian có thể muốn chọn toàn bộ các phương thức truy vấn trong cùng một API.

| Mục tiêu | Ký pháp ý tưởng | Phạm vi |
| --- | --- | --- |
| Một overload | `type=DocumentOperations; name=save; params=[String]` | Chỉ một chữ ký |
| Tất cả overload cùng tên | `type=DocumentOperations; name=save; params=ANY` | Mọi `save(...)` |
| Một họ tên | `type=DocumentOperations; name startsWith save` | `save`, `saveAll`,... |
| Tất cả method của type | `type=DocumentOperations; name=ANY; params=ANY` | Phạm vi rất rộng |
| Nhiều type | `type in [DocumentOperations, ArchiveOperations]` | Hai nhóm API |

Đây là **ký pháp minh họa**, không phải biểu thức có thể sao chép vào mọi AOP engine. Các cú pháp wildcard có thể khác về dấu chấm package, subtype, số tham số hoặc mẫu kiểu trả về.

### 7.1. Không có tham số khác với mọi tham số

- `params=[]`: chính xác **không có** tham số.
- `params=[String]`: đúng **một** `String`.
- `params=ANY`: mọi số lượng và kiểu tham số.
- `params=startsWith([String])`: tham số đầu là `String`, có thể có những tham số khác **nếu DSL thực sự hỗ trợ**.

Đây là những ý nghĩa khác nhau. Một lỗi phổ biến là dùng mẫu rộng “bất kỳ danh sách tham số” trong khi chủ ý chỉ chọn `run()`.

### 7.2. Wildcard type và wildcard name là hai phép lọc riêng

“Class thuộc một package” không tương đương “method bắt đầu bằng tên `save`”. Hai quy tắc nên ghép tường minh:

```text
type matches a defined set of storage APIs
AND method name starts with "save"
AND parameters exactly [String]
AND NOT type belongs to internal diagnostics
```

Khi dùng DSL thật, kiểm tra liệu pattern package bao gồm subpackage, class lồng nhau hoặc subtype hay không. Không suy ra ý nghĩa các ký hiệu glob/regex từ ngôn ngữ khác.

## 8. Type ở call site khác type ở execution

Giả sử:

```java
// Mã minh họa, không phải file hoàn chỉnh.
interface Processor {
    void process(String data);
}

class FileProcessor implements Processor {
    @Override
    public void process(String data) { }
}

class NetworkProcessor implements Processor {
    @Override
    public void process(String data) { }
}

void handle(Processor processor) {
    processor.process("x");
}
```

Một vị trí gọi `processor.process("x")` viết qua **kiểu tĩnh `Processor`** có thể dẫn đến execution trong **`FileProcessor` hoặc `NetworkProcessor`** tùy runtime object.

Do đó:

- Bộ lọc cho **method call join point** có thể dựa vào method được tham chiếu ở phía caller.
- Bộ lọc cho **method execution join point** có thể dựa vào class triển khai đang chạy.
- Nếu engine chỉ cung cấp một loại join point, không thể giả định có đầy đủ metadata của phía còn lại.
- Một interface contract không tự động làm mọi kiểu weaving/interception bao phủ chính xác mọi implementation và lời gọi.

**Khi thiết kế quy tắc theo type, luôn xác định rõ cần match declaring type, kiểu compile time, kiểu target runtime hay cả hierarchy.** Đây là quyết định về semantics của pointcut, không đơn giản là lựa chọn chuỗi tên class.

## 9. Overriding và method được thừa kế

```java
class BaseExporter {
    public String export(String id) {
        return "base:" + id;
    }
}

class CsvExporter extends BaseExporter {
    @Override
    public String export(String id) {
        return "csv:" + id;
    }
}
```

Cả hai method có tên `export` và tham số `String`, nhưng declaring type khác nhau. Dynamic dispatch có thể chọn override của `CsvExporter`. Pointcut chọn **declaring type chính xác là BaseExporter** không mặc nhiên đồng nghĩa với chọn **mọi method có chữ ký đó trên subtype của BaseExporter**.

Nếu một lớp con không override, method kế thừa có thể vẫn được khai báo ở lớp cha. Nếu interface chứa `default method`, cần kiểm tra cách cơ chế AOP xử lý phần thân default và method proxy. Không nên chỉ tìm trong `getDeclaredMethods()` của lớp con rồi kết luận không có execution nào liên quan.

## 10. Generics và type erasure: chữ ký nguồn khác metadata runtime

Một ví dụ:

```java
interface Sink<T> {
    void accept(T value);
}

class StringSink implements Sink<String> {
    @Override
    public void accept(String value) {
        System.out.println(value);
    }
}
```

Đây là khai báo minh họa. Do **type erasure**, tham số `T` không có upper bound riêng trong `Sink<T>` trở thành `Object` ở descriptor liên quan. Compiler có thể tạo **bridge method** trong `StringSink` để duy trì overriding, ngoài method `accept(String)` do người lập trình viết.

Hệ quả với pointcut:

- `Method.getParameterTypes()` trả kiểu sau erasure, có thể khác cách một người đọc type parameter trong source.
- `getGenericParameterTypes()` trả metadata `Type`, cần diễn giải type variable và các generic declaration.
- `isBridge()` và `isSynthetic()` giúp nhận diện method do compiler sinh.
- Nếu cơ chế can thiệp dùng bytecode, cần kiểm thử việc khớp bridge method và số lần advice chạy. Không tự khẳng định mọi engine có cùng cách loại hoặc gộp bridge method.

Trường hợp generics không phải lý do bỏ qua ràng buộc signature; nó cho thấy phải hiểu **signature đang xét được lấy từ cấp độ nào**.

## 11. Ranh giới kỹ thuật: proxy và bytecode weaving

Một rule chọn metadata có thể đúng hoàn toàn nhưng **công cụ không bắt được join point mong muốn**.

| Cơ chế | Có thể quan sát ở đâu? | Lưu ý |
| --- | --- | --- |
| Runtime proxy dựa trên interface | Lời gọi thực sự đi qua proxy | Gọi trực tiếp target và lời gọi nội bộ có thể bỏ qua advice |
| Proxy bằng subclass | Lời gọi qua lớp con trung gian | Các ràng buộc final/private/static có thể ngăn override |
| Compile-time weaving | Mã đã được biến đổi khi biên dịch | Phải đảm bảo build dùng artifact được weave |
| Load-time weaving | Lớp được biến đổi khi JVM nạp | Phụ thuộc class loader/cấu hình nạp |
| Post-compile weaving | Bytecode đã biên dịch được biến đổi | Phải kiểm chứng artifact chính xác được xử lý |

Đừng coi một proxy nhận lời gọi là “mọi method call join point của chương trình”, hoặc coi nó đã weave execution vào thân target. Những khái niệm liên quan nhưng phạm vi khác nhau.

### Self-invocation

```java
class DocumentWorker {
    void run() {
        save("A"); // lời gọi nội bộ
    }

    void save(String id) { }
}
```

Với proxy đứng ngoài target, `this.save("A")` trong target thường không đi vòng ra proxy để chạy handler lần nữa. Nhưng về mặt mô hình, vẫn có một biểu thức gọi và một lần execution. **Giới hạn này thuộc đường interception qua proxy**, không phải mệnh đề “self-invocation không thể là join point” trong mọi AOP.

### Private, static, final và constructor

Những thành phần này có metadata tên/chữ ký ở mức thích hợp nhưng **khả năng can thiệp** phụ thuộc loại join point và weaving. Dynamic proxy interface không thể tự chặn private method, static call hoặc constructor. Weaving bytecode có thể hỗ trợ loại điểm khác, nhưng cũng không mặc nhiên hỗ trợ tất cả.

## 12. Những bộ lọc phụ có giá trị

Bên cạnh type + name + Java signature, một số hệ thống còn hỗ trợ:

| Bộ lọc phụ | Ý nghĩa | Điều cần chú ý |
| --- | --- | --- |
| Return type | Chỉ method trả `String` hoặc `CompletionStage` | Trả generic hay raw type? |
| Visibility | Chỉ method `public` | Không đồng nghĩa mọi public method đều chặn được |
| Static/instance | Chọn method của instance | Static không có instance receiver |
| Annotation | Chọn method có metadata như `@Audited` | Annotation ở interface/implementation? |
| Runtime argument | Chọn theo kiểu/giá trị đối số lúc chạy | Khác declared parameter type |
| Context | Chọn theo ngữ cảnh lời gọi | Cần kiểm soát thread, async, bảo mật |
| Exclusions | Bỏ qua lớp hạ tầng hoặc method nội bộ | Tránh recursion và đo lường nhiễu |

Ví dụ “chọn method nhận `Object` nhưng runtime argument là `String`” khác “chọn method **khai báo** tham số `String`”. Cùng một lời gọi có thể thỏa một điều kiện mà không thỏa điều kiện kia. Đây là khác biệt giữa **kiểu khai báo** và **giá trị runtime**.

Annotation tự nó cũng không kích hoạt AOP. Chỉ khi pointcut đọc metadata và có cơ chế weaving/interception phù hợp, advice mới thực thi tại các điểm được chọn.

## 13. Thiết kế pointcut đúng theo yêu cầu

Thay vì viết một biểu thức thật rộng từ đầu, hãy xác định từng yếu tố:

1. **Concern:** Chính sách áp dụng cho hành vi nào (ví dụ audit lưu tài liệu)?
2. **Loại join point:** Phía caller (*method call*) hay phía callee (*method execution*)?
3. **Ranh giới type:** Interface, implementation, declaring type chính xác, hierarchy hay package?
4. **Method name:** Tên chính xác hay một tập/pattern? Đổi tên có làm rule mất hiệu lực?
5. **Parameter types:** Số lượng, thứ tự, primitive/wrapper, array, varargs, erasure?
6. **Điều kiện phụ:** Return type, visibility, annotation, runtime context có thực sự cần?
7. **Loại trừ:** Những phương thức hạ tầng nào không thuộc chính sách?
8. **Cơ chế weave:** Call path nào được bao phủ, call path nào bypass?
9. **Kiểm thử:** Ca phải khớp, không được khớp, ngoại lệ, overload, override, reflection và lời gọi nội bộ.
10. **Thay đổi về sau:** Kiểm chứng lại tập điểm được chọn sau refactor API.

Pointcut dùng tên và kiểu quá rộng thường gây **false positive**: advice chạy ở chỗ không nên. Pointcut quá hẹp hoặc phụ thuộc một signature đã đổi gây **false negative**: nơi cần advice lại bị bỏ lọt. Cả hai đều có thể nghiêm trọng nếu concern là audit hoặc kiểm soát truy cập.

### Ma trận kiểm thử ví dụ

Giả sử rule:

```text
declaring type = DocumentOperations
name           = save
parameterTypes = [String]
```

| Phương thức | Type | Name | Parameters | Kết quả |
| --- | --- | --- | --- | --- |
| `DocumentOperations.save(String)` | Đúng | Đúng | Đúng | **Chọn** |
| `DocumentOperations.save(int)` | Đúng | Đúng | Sai | Không chọn |
| `DocumentOperations.find(String)` | Đúng | Sai | Đúng | Không chọn |
| `DocumentOperations.save(String, boolean)` | Đúng | Đúng | Sai | Không chọn |
| `CustomerOperations.save(String)` | Sai | Đúng | Đúng | Không chọn |
| `DocumentOperations.save(String[])` | Đúng | Đúng | Sai | Không chọn |

Bảng mô tả kết quả **matcher logic**, chưa chứng minh những method tương ứng sẽ chạy advice trong runtime. Test tích hợp phải gọi qua đúng cơ chế AOP, đồng thời kiểm tra rằng đường không khớp không có advice.

### Khi nhiều aspect cùng khớp

Nếu aspect `Audit` và `Timing` cùng chọn `save(String)`, cả hai advice có thể được áp dụng. **Pointcut quyết định có chọn hay không; precedence quyết định thứ tự advice khi chúng cùng khớp.** Nếu quanh một lời gọi có nhiều around advice, chúng có thể lồng nhau. Thứ tự ảnh hưởng phạm vi đo thời gian, context và xử lý lỗi; không suy đoán thứ tự mặc định của mọi engine là giống nhau.

## 14. Các trường hợp nâng cao cần kiểm chứng

### 14.1. Đổi signature sau refactor

Đổi `save(String)` thành `save(DocumentRequest)` có thể khiến pointcut cũ không còn khớp. Thêm overload `save(String, boolean)` không làm rule chính xác tự động chọn overload mới. Đổi interface hoặc di chuyển method lên base type có thể làm thay đổi declaring type. Vì vậy các thay đổi API phải kích hoạt kiểm thử tập khớp.

### 14.2. Bytecode sinh tự động

Bridge và synthetic methods có thể xuất hiện trong bytecode dù không hiển thị như một method viết tay bình thường. Một matcher chỉ dựa tên/param nhưng không xét `isBridge()`/`isSynthetic()` có thể quan sát phạm vi khác dự kiến trong một số cơ chế. Cần kiểm chứng trên artifact biên dịch và runtime thật.

### 14.3. Reflection và method handles

Một thân method có thể thực thi qua reflection mà source caller không có biểu thức trực tiếp `target.save(...)` tương ứng. Pointcut ở execution của target và pointcut ở vị trí gọi `Method.invoke(...)` là hai ranh giới khác nhau. Việc hỗ trợ từng ranh giới phụ thuộc weaving.

### 14.4. Async và công việc hoàn tất sau lời gọi

Nếu method gửi tác vụ rồi trả về một `CompletableFuture`, advice đo quanh lời gọi **gửi tác vụ** chưa chắc đo được **thời gian hoàn thành công việc**. Chọn đúng type/name/signature của method submit không đủ nếu mục tiêu là đo task execution; cần chọn hoặc theo dõi đúng ranh giới hoàn tất.

### 14.5. Recursion, advice và side effect

Một method đệ quy tạo nhiều lượt execution; lời gọi nội bộ có thể không đi qua proxy. Nếu around advice gọi tiếp nhiều lần, một thao tác có side effect cũng có thể bị thực thi nhiều lần. Pointcut chọn **đúng method** chưa đảm bảo hành vi an toàn nếu advice có quyền bỏ qua hoặc lặp thao tác.

## 15. Các lỗi thường gặp

1. **Chỉ dùng tên method**, khiến nhiều overload/type không liên quan đều khớp.
2. **Nhầm return type là thành phần chữ ký Java**, dù nó chỉ có thể là một bộ lọc pointcut bổ sung.
3. **Quên thứ tự/số lượng tham số**, nên `process(String, int)` bị coi như `process(int, String)`.
4. **Coi `int` và `Integer` là cùng kiểu khai báo** vì Java hỗ trợ boxing/unboxing.
5. **Coi array và varargs là hai overload hợp lệ** với cùng kiểu phần tử.
6. **Nhầm declaring type với runtime target type**, làm bỏ lọt inherited/overridden method.
7. **Dùng wildcard quá rộng**, vô tình áp dụng advice cho method được thêm sau này.
8. **Giả định mọi method match metadata đều được advice chặn**, bỏ qua phạm vi weave/proxy.
9. **Bỏ qua generics erasure và bridge method**, dẫn đến matcher sai trên bytecode thật.
10. **Không kiểm thử đường gọi trực tiếp hoặc nội bộ**, nơi proxy có thể bị bỏ qua.
11. **Đổi tên/chữ ký method nhưng không cập nhật test pointcut**, tạo false negative.
12. **Không giới hạn hạ tầng của advice**, làm advice tự gọi vào chính join point và gây reentrancy.

## 16. Bài tập thực hành

1. Trong `PointcutSelectionDemo.java`, đổi `parameterMatches` từ `String.class` sang `int.class`. Dự đoán và chạy xác minh phương thức nào có dòng `[advice]`.
2. Xóa điều kiện tham số, chỉ giữ type và name. Vì sao cả `save(String)` và `save(int)` có advice?
3. Thêm `save(String, boolean)` vào interface và implementation; chứng minh rule gốc không chọn method mới.
4. Thêm interface `CustomerOperations` có `save(String)` và đề xuất matcher loại đúng bằng điều kiện type.
5. Đổi tên `save(String)` thành `persist(String)`. Thiết kế regression test phát hiện pointcut cũ đã không còn bao phủ hành vi lưu.
6. Dùng reflection so sánh `getDeclaredMethods()` và `getMethods()` trên một class kế thừa method public.
7. Kiểm tra `isVarArgs()` cho hai cách khai báo `String[]` và `String...` ở **hai method/class riêng**, để tránh lỗi trùng signature.
8. Với `Sink<T>`/`StringSink`, dùng reflection in `getParameterTypes()`, `isBridge()` và `isSynthetic()`; giải thích kết quả.
9. Gọi trực tiếp `target.save("DOC-7")` rồi gọi qua `service.save("DOC-7")`. Vì sao trường hợp đầu không có advice dù đúng chữ ký?
10. Viết bảng gồm ít nhất năm method “phải được chọn” và năm method “không được chọn” cho một chính sách cụ thể, rồi kiểm thử tại runtime để phát hiện cả false positive và false negative.

**Tự kiểm tra:** `save(String)` và `save(int)` có cùng signature Java không? `getDeclaringClass()` có luôn là runtime class của target không? Vì sao return type có thể là điều kiện của pointcut nhưng không thuộc signature Java? Một bộ matcher trả `true` có chứng minh proxy đã bắt được mọi lời gọi không?

## Kết luận

**Chọn join point theo kiểu, tên và chữ ký** cho phép aspect có phạm vi rõ ràng và có thể kiểm chứng. **Type** xác định API/implementation liên quan, **method name** xác định thao tác, còn **danh sách kiểu tham số** giúp phân biệt overload theo đúng quy tắc Java. Những điều kiện như return type, visibility hay annotation có thể bổ sung khi cơ chế hỗ trợ nhưng không thay đổi định nghĩa chữ ký Java. Để áp dụng an toàn, luôn xem xét thừa kế, đa hình, generics, ranh giới call/execution và đường đi weaving/interception, đồng thời duy trì kiểm thử các điểm khớp và không khớp sau mỗi lần refactor.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-basics-002">Giới hạn theo phạm vi gói, mô-đun hoặc thành phần</a>

<details>
<summary>Xem chi tiết</summary>

# Giới hạn pointcut theo phạm vi gói, mô-đun hoặc thành phần trong AOP

**Pointcut** là quy tắc lựa chọn những điểm can thiệp (*join points*) nơi hành vi bổ sung (*advice*) của một khía cạnh (*aspect*) được áp dụng. Bài toán thực tế không chỉ là “chạy advice khi nào?” mà còn là **“chính xác phần nào của chương trình được phép chịu ảnh hưởng?”**. Một chính sách đo thời gian chỉ dành cho tầng xử lý đơn hàng không nên vô tình chạy trong mã dựng đối tượng, thư viện ngoài hoặc thành phần tính toán chuyên sâu không liên quan.

Giới hạn pointcut theo **gói (package)**, **mô-đun (module)** hoặc **thành phần (component)** giúp biểu đạt biên áp dụng, giảm can thiệp ngoài ý muốn và làm rõ nơi sở hữu chính sách. Tuy nhiên, phạm vi **khớp theo quy tắc** và phạm vi **thực sự được cơ chế AOP can thiệp** là hai điều phải kiểm tra riêng. Cú pháp pointcut, loại join point hỗ trợ và cách weaving phụ thuộc công nghệ cụ thể.

## 1. Sau bài này cần hiểu gì?

- Vì sao pointcut càng rộng càng dễ tạo tác dụng phụ và vì sao pointcut quá hẹp dễ bỏ sót hành vi cần quan sát.
- Cách xây dựng điều kiện phạm vi theo tên package, lớp/kiểu, mô-đun logic hoặc thành phần có hợp đồng rõ ràng.
- Phân biệt package Java, mô-đun theo kiến trúc và mô-đun Java Platform Module System (JPMS); tránh suy luận nhầm cơ chế đóng gói thành ngôn ngữ pointcut.
- Phân biệt vị trí **mã chứa join point** với **kiểu/đối tượng đích** và **người gọi**, vì ba cách lọc có thể cho kết quả khác nhau.
- Ghép điều kiện phạm vi với điều kiện thao tác, chẳng hạn thực thi phương thức xử lý yêu cầu, thay vì chỉ dựa vào tên package.
- Hiểu các giới hạn của proxy, weaving bytecode, đường gọi nội bộ, quyền truy cập và khả năng kiểm thử.
- Tự kiểm tra phạm vi qua ví dụ Java 17 và ma trận ca thành công, ngoài phạm vi, đường gọi đi vòng và thay đổi kiến trúc.

## 2. Tư duy nền: chọn đúng điểm, đúng phần chương trình

Giả sử một ứng dụng gồm:

```text
app
├── catalog          (tra cứu sản phẩm)
├── order            (xử lý đơn hàng)
│   ├── application  (điều phối tác vụ)
│   └── domain       (quy tắc kinh doanh)
├── payment          (tương tác thanh toán)
├── reporting        (báo cáo)
└── observability    (theo dõi kỹ thuật)
```

Đây là **sơ đồ mô-đun kiến trúc**, chưa khẳng định mỗi nhánh phải là một Java package hoặc một JPMS module.

Ta muốn ghi thời lượng những thao tác thuộc `order.application`, không đo mọi phép tính trong `order.domain` và không ghi các thao tác báo cáo. Một pointcut phù hợp phải biểu đạt được hai lớp điều kiện:

1. **Loại join point nào?** Ví dụ, thực thi một phương thức mà cơ chế AOP hỗ trợ.
2. **Join point thuộc phạm vi nào?** Ví dụ, phương thức trong một nhóm kiểu xử lý tác vụ đơn hàng.

Chỉ biết một thành phần “thuộc order” chưa đủ: bên trong đó có thể có phương thức nội bộ chạy hàng triệu lần; áp advice cho mọi điểm có thể tạo chi phí và dữ liệu theo dõi rất lớn.

### 2.1. Pointcut, advice và weaving làm ba việc khác nhau

| Khái niệm | Trách nhiệm |
| --- | --- |
| **Join point** | Điểm thực thi có thể can thiệp theo mô hình AOP đang dùng |
| **Pointcut** | Chọn tập join point thông qua điều kiện về loại thao tác, kiểu, tên, phạm vi hoặc bối cảnh |
| **Advice** | Hành vi thực hiện trước, sau hoặc bao quanh điểm được chọn, tùy công nghệ |
| **Aspect** | Đóng gói chính sách gồm pointcut cùng hành vi bổ sung |
| **Weaving / kết hợp lúc chạy** | Đưa chính sách vào đường thực thi tại các điểm đã chọn và hỗ trợ được |

Ví dụ pointcut “các lần thực thi phương thức công khai trong nhóm xử lý đơn hàng” là **bộ lọc**. Nó chưa nói phải in log, đo thời gian hay kiểm tra quyền; điều đó thuộc advice. Đồng thời, chỉ khai báo pointcut không bảo đảm chương trình chạy advice: mã hoặc lời gọi phải thật sự đi qua cơ chế kết hợp.

### 2.2. Phạm vi áp dụng khác quyền truy cập

Một package hay JPMS module giúp tổ chức và kiểm soát một số quan hệ truy cập mã nguồn. Điều kiện pointcut chỉ mô tả **nơi chọn hành vi AOP**. Nó không tự trở thành ranh giới bảo mật, ranh giới giao dịch hoặc bộ kiểm soát quyền bắt buộc. Nếu có đường thực thi không được can thiệp, chính sách có thể không chạy ở đường đó.

## 3. Giới hạn theo package

Trong Java, câu lệnh `package` xác định gói của một kiểu. Ví dụ:

```java
package example.order.application;

public final class SubmitOrderService {
    public void submit() {
        // Logic điều phối đơn hàng.
    }
}
```

Một chính sách có thể chọn join point nằm trong các kiểu thuộc `example.order.application`. Điểm cần xác định là **chỉ gói chính xác đó** hay **cả các gói con** như `example.order.application.internal`.

### 3.1. Gói chính xác và cây gói con

Hai kiểu điều kiện thường cần phân biệt:

- **Exact package:** chỉ những kiểu nằm trực tiếp trong `example.order.application`.
- **Package subtree:** những kiểu thuộc `example.order.application` và các gói bắt đầu bằng `example.order.application.`.

Trong Java, `example.order.application.internal` là **gói riêng**, không phải thành viên của gói `example.order.application` theo nghĩa quyền truy cập package-private. Quan hệ tên theo dấu chấm có ích cho quy ước tổ chức và biểu thức chọn, nhưng không tạo tự động quan hệ kế thừa quyền giữa hai package.

Cũng đừng lọc bằng tiền tố thiếu dấu chấm:

```java
name.startsWith("example.order")
```

Biểu thức ấy còn chọn cả `example.ordersystem`. Nếu muốn gồm package gốc và gói con, mô tả đúng là:

```java
name.equals("example.order") || name.startsWith("example.order.")
```

Đây là **điều kiện chuỗi Java minh họa**, không phải cú pháp pointcut chuẩn của mọi công nghệ AOP.

### 3.2. Lọc nơi định nghĩa kiểu, nơi chạy hay nơi gọi?

Giả sử mã trong `example.order.application` gọi `example.payment.PaymentClient.charge()`.

Một điều kiện “join point nằm **bên trong mã của order.application**” có thể chọn điểm **lời gọi** `charge()` ở phía order, nếu mô hình hỗ trợ *method call join point*. Một điều kiện “thực thi phương thức của kiểu **PaymentClient**” lại xét phía phương thức đích, thuộc payment. Hai điều kiện khác nhau dù cùng tham gia một lời gọi.

Bởi vậy phải xác định:

- **Vị trí từ vựng của join point:** bytecode/mã chứa điểm thực thi thuộc loại nào và nằm ở đâu?
- **Kiểu khai báo phương thức/field:** thành phần nào định nghĩa thành viên đang được gọi hoặc truy cập?
- **Kiểu thực của đối tượng:** đối tượng được gọi có thể là lớp con, lớp ủy quyền hoặc proxy thuộc package khác.
- **Người gọi:** điểm phát sinh lời gọi có thể nằm ngoài package của thành phần đích.

Một biểu thức chỉ dựa trên `target.getClass().getPackageName()` lọc theo **lớp runtime đích**; nó không đồng nghĩa chọn mọi join point được viết bên trong một package. Nhiều lỗi phạm vi bắt đầu từ việc nhầm hai khái niệm này.

### 3.3. Ví dụ cú pháp điểm chọn trong một mô hình AOP cụ thể

Một số ngôn ngữ pointcut, chẳng hạn ngôn ngữ của AspectJ, có khái niệm như `within` và `execution`. Hai biểu thức dưới đây **chỉ là minh họa cú pháp của mô hình đó**, không phải Java độc lập và không phải cú pháp chung áp dụng cho mọi công cụ:

```text
within(example.order.application..*)
execution(* example.order.application..*.*(..))
```

Về ý tưởng:

- `within(example.order.application..*)` chọn join point có **vị trí từ vựng** bên trong những kiểu thuộc gói/cây gói được chỉ định, theo ngữ nghĩa của công cụ.
- `execution(* example.order.application..*.*(..))` chọn những join point **thực thi phương thức** khớp kiểu và mẫu tên/chữ ký. Đây là tập điểm có loại thao tác cụ thể hơn.

Ký hiệu `..` có ý nghĩa riêng trong ngôn ngữ mẫu kiểu này; không nên đem nó sang biểu thức Java `startsWith` hay một hệ pointcut khác mà chưa kiểm chứng cú pháp.

Điều kiện chọn theo package **không nhất thiết** chọn phương thức kế thừa hay những lớp proxy sinh động theo đúng ý định ban đầu. Hãy kiểm thử từng kiểu runtime và ranh giới gọi mà ứng dụng sử dụng.

### 3.4. Khi nào lọc theo package phù hợp?

Phù hợp khi package phản ánh **ranh giới kiến trúc được duy trì nhất quán**: ví dụ một gói chỉ chứa điểm vào ứng dụng mà mọi thao tác đều cần đo. Nếu cùng một chính sách áp cho các lớp ở nhiều package, một quy tắc theo vai trò hoặc hợp đồng có thể rõ hơn.

Không nên dùng tên package như điều kiện duy nhất nếu một gói vừa chứa API công khai, thuật toán nội bộ, mô hình dữ liệu và bộ phát sự kiện. Phạm vi đó quá rộng đối với nhiều loại advice.

## 4. Giới hạn theo mô-đun: cần định nghĩa “module”

Trong dự án thực tế, “mô-đun” có nhiều nghĩa:

| Cách dùng | Ví dụ | Ý nghĩa của ranh giới |
| --- | --- | --- |
| **Mô-đun logic/kiến trúc** | `order`, `payment`, `reporting` | Một nhóm trách nhiệm được tổ chức theo nghiệp vụ hoặc kỹ thuật |
| **Mô-đun build/artifact** | Một thư viện JAR riêng hoặc một dự án con build | Đơn vị biên dịch, đóng gói và phụ thuộc |
| **JPMS module** | `module example.orders { ... }` | Đơn vị module chính thức của Java với khai báo phụ thuộc và khả năng công khai package |
| **Đơn vị triển khai** | Một dịch vụ chạy thành tiến trình độc lập | Ranh giới tiến trình/mạng và vòng đời triển khai |

**Không mặc định** một điều kiện pointcut “theo module” hiểu được cả bốn nghĩa. Công cụ AOP có thể chỉ hiểu kiểu/package, hoặc cung cấp bộ lọc theo nguồn gốc lớp, module descriptor, class loader, artifact hay metadata riêng.

### 4.1. Mô-đun logic

Một mô-đun `order` có thể chứa nhiều package:

```text
example.order.api
example.order.application
example.order.domain
example.order.infrastructure
```

Giới hạn theo mô-đun logic có thể được hiện thực bằng:

- Một **nhóm package** có quy ước ổn định;
- Một **hợp đồng kiểu** chung cho các điểm vào được theo dõi;
- Metadata như annotation đánh dấu vai trò;
- Một danh sách rõ ràng các thành phần được phép áp chính sách;
- Quy tắc cấu hình của bộ weaving, nếu hỗ trợ.

Đây là những cách biểu đạt **cùng một biên kiến trúc**. Chọn cách phù hợp nhất với người quản lý module và độ ổn định của API. Không nên tạo một biểu thức khớp toàn bộ `example.order..` chỉ vì tên module là order, nếu thật ra chính sách chỉ áp cho API đầu vào.

### 4.2. JPMS module của Java

Java 9 trở lên cung cấp **Java Platform Module System (JPMS)**. Ví dụ một mô tả module đơn giản:

```java
module example.orders {
    exports example.orders.api;
}
```

Đây là tệp `module-info.java` **minh họa**, cần có package phù hợp và được biên dịch trong một bố cục module hợp lệ. `exports` cho phép các module khác truy cập các kiểu `public` trong package đã xuất theo luật JPMS; nó **không** có nghĩa mọi phương thức của package đó tự động được một aspect can thiệp.

Trong mã chạy trên JVM, có thể tra module runtime của một lớp bằng `SomeType.class.getModule()` và tên bằng `getName()`; tên có thể `null` với unnamed module. Đây là thông tin để xây dựng bộ lọc **khi cơ chế cho phép**, không phải một phép pointcut phổ quát.

Các khái niệm cần phân biệt:

- **`requires`** mô tả phụ thuộc module được đọc.
- **`exports`** công khai các kiểu `public` thuộc package cho module khác truy cập theo quy tắc module.
- **`opens`** liên quan đến khả năng reflection sâu đối với package được mở.
- **Weaving/instrumentation** có khả năng và điều kiện riêng; không suy ra “có exports là được weave” hoặc “không exports là bảo đảm không weave”.

Các công cụ biến đổi bytecode, reflection hay nạp lớp có thể chịu giới hạn quyền truy cập và module khác nhau. Cần kiểm tra tài liệu cụ thể và chạy thử với **cấu hình module path thực**, thay vì chỉ kiểm tra trong classpath.

### 4.3. Mô-đun build và ranh giới artifact

Một artifact độc lập giúp xác định lớp nào sẽ được biên dịch/đóng gói hoặc cung cấp cho weaver. Nhưng tên file JAR, tên dự án con hoặc thư mục nguồn không mặc định xuất hiện trong mô hình join point. Nếu cần áp aspect cho các lớp trong một artifact, phải cấu hình **phạm vi lớp được tải hoặc biến đổi**, rồi kiểm tra tập join point thực tế trong những lớp đó.

Có thể xảy ra tình huống một số lớp phụ thuộc được đóng gói chung, hoặc một lớp được nạp từ class loader khác, khiến “giới hạn theo JAR” và “giới hạn theo package” không trùng nhau.

### 4.4. Đơn vị triển khai

Nếu `payment` là dịch vụ khác tiến trình, weaving trong tiến trình `order` **không thể tự động can thiệp bytecode đang chạy trong tiến trình payment**. Ta có thể đo lời gọi HTTP hoặc RPC ở **phía client** của order, nhưng điều đó khác với việc đo trực tiếp mọi phương thức trong payment.

Khi qua ranh giới mạng, cần phân biệt pointcut trong mã của từng tiến trình với cơ chế truyền bối cảnh truy vết giữa các dịch vụ. Đây là lựa chọn kiến trúc; việc đặt tên hai service giống nhau không tạo một pointcut xuyên tiến trình.

## 5. Giới hạn theo thành phần hoặc vai trò

**Thành phần** ở đây là phần mềm có trách nhiệm/hợp đồng xác định: bộ xử lý lệnh, bộ kết nối ngoài, bộ tính giá, bộ ghi sự kiện. Một thành phần có thể là một lớp, nhóm lớp hoặc giao diện; không nhất thiết trùng với một package hoặc artifact.

Giới hạn theo vai trò thường bền hơn lọc tên gói khi kiến trúc hay tổ chức thư mục đổi.

### 5.1. Chọn theo giao diện hoặc kiểu

Giả sử các tác vụ đầu vào đều triển khai:

```java
public interface CommandHandler<C, R> {
    R handle(C command);
}
```

Đây là **đoạn mã minh họa**, không phải toàn bộ chương trình. Một cơ chế hỗ trợ lọc theo kiểu có thể chọn thực thi `handle` thuộc nhóm `CommandHandler` thay vì “mọi phương thức trong package application”. Ý nghĩa chính sách trở nên cụ thể: nó áp tại **ranh giới xử lý lệnh**.

Nhưng cần phân biệt:

- Cơ chế chọn theo **kiểu khai báo**, **kiểu runtime** hoặc quan hệ kế thừa nào?
- Nếu một lớp triển khai nhiều giao diện, phương thức nào được chọn?
- Khi có lớp ủy quyền/proxy, kiểu runtime có thay đổi điều kiện không?
- Nếu điểm được chọn là **method execution**, lời gọi nội bộ đến một phương thức khác có tự phát sinh điểm được hỗ trợ hay không?

Không nên suy ra chỉ từ việc `instanceof CommandHandler` là mọi lời gọi của đối tượng ấy đã được weave.

### 5.2. Chọn theo annotation/metadata

Một marker có thể biểu thị vai trò:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ObservedComponent {}
```

Đây là **minh họa cho một kiểu marker**, cần đặt trong tệp `ObservedComponent.java` nếu biên dịch. Công cụ AOP có thể có quy tắc chọn dựa trên annotation, miễn là loại join point và cơ chế đọc metadata đó được hỗ trợ.

Annotation đánh dấu **ý định** áp một chính sách; nó không tự biến đổi mã hoặc bảo đảm lời gọi đi qua advice. Một lớp con, proxy sinh động hoặc thành phần được tạo không qua tuyến sử dụng mong đợi có thể cần kiểm chứng riêng.

### 5.3. Chọn theo danh sách thành phần rõ ràng

Nếu chính sách rất nhạy cảm, danh sách chính xác tên kiểu, chức năng hoặc cổng giao tiếp có thể dễ kiểm toán hơn bộ lọc tự động quá rộng. Đánh đổi là cần cập nhật danh sách khi thêm thành phần mới. Cách này hữu ích khi điểm áp dụng phải được phê duyệt từng trường hợp.

Tóm lại, chọn theo **package** trả lời “nằm ở khu vực mã nào?”, chọn theo **module** trả lời “thuộc đơn vị quản lý nào?”, còn chọn theo **component** trả lời “đóng vai trò gì?”. Một pointcut tốt thường kết hợp các tiêu chí ấy với điều kiện thao tác.

## 6. Kết hợp các điều kiện: phạm vi + loại thao tác + loại trừ

Một điều kiện phạm vi riêng lẻ có thể chưa đủ. Với chính sách đo thời gian, quy tắc mong muốn thường là:

```text
Chọn join point khi:
  1. Đây là thực thi phương thức được cơ chế hỗ trợ;
  2. Phương thức thuộc nhóm xử lý lệnh của module đặt hàng;
  3. Phương thức đại diện cho điểm vào cần đo;
  4. Không thuộc mã của chính hệ thống theo dõi;
  5. Không phải thành phần kỹ thuật nội bộ không liên quan.
```

Đó là **đặc tả logic**, không phải cú pháp dành cho một AOP framework cụ thể. Điều kiện Boolean có thể được xem như:

```text
SELECT = JOIN_POINT_KIND
         AND MODULE_SCOPE
         AND COMPONENT_ROLE
         AND INCLUDED_OPERATION
         AND NOT EXCLUDED_INTERNAL
```

Nhờ kết hợp, cùng một module có thể chứa nhiều thành phần nhưng chỉ các **điểm vào công khai** được can thiệp. Các điều kiện loại trừ còn giúp tránh tự ghi log khi chính bộ ghi log hoạt động.

### 6.1. Quy tắc bao gồm và loại trừ

Điều kiện **bao gồm** chỉ rõ tập điểm cần đo. Điều kiện **loại trừ** có thể bỏ qua mã tracing, hàm thao tác nền, phương thức đối tượng cơ bản hoặc đường gọi nhạy cảm.

Nhưng không nên bắt đầu với “mọi thứ” rồi loại trừ một danh sách kéo dài mãi. Nếu tập cần áp biết rõ, một biểu thức bao gồm hẹp và đặt tên theo vai trò thường dễ kiểm tra hơn.

### 6.2. Điểm quyết định tĩnh và điều kiện động

Điều kiện theo package, kiểu hoặc chữ ký thường có thể được đánh giá ở giai đoạn cấu hình hoặc weaving, tùy công nghệ. Điều kiện dựa trên **đối số, trạng thái đối tượng hoặc bối cảnh chạy** cần kiểm tra tại lúc thực thi.

Ví dụ: cùng một `CommandHandler` chỉ đo những lệnh có tùy chọn chẩn đoán bật. Khi đó, phần chọn theo vai trò có thể tĩnh; phần “có bật chẩn đoán không?” là động. Điều kiện động có thể tăng chi phí, đồng thời tạo nguy cơ lựa chọn khác nhau qua các lần chạy. Cần xác định rõ những gì phải được quan sát *luôn luôn* và những gì chỉ đo có điều kiện.

### 6.3. Đường gọi và phạm vi weaving quyết định kết quả cuối

Một mô tả pointcut có thể chọn đúng lớp nhưng vẫn **không chạy advice** nếu:

- Lớp hoặc join point nằm ngoài phạm vi bytecode được weaver xử lý;
- Đường gọi đi thẳng tới đối tượng đích thay vì qua proxy;
- Lời gọi nội bộ không vòng qua proxy của chính đối tượng đó;
- Điểm thao tác (field access, constructor, lời gọi private/static...) không được công nghệ hỗ trợ;
- Lớp đã được nạp trước khi bộ biến đổi tải lớp có hiệu lực;
- Cấu hình module, class loader hoặc quyền truy cập không cho phép cơ chế hoạt động như mong muốn.

**Matched scope ≠ effective interception.** Đây là sự khác biệt phải kiểm chứng trong chương trình thật.

## 7. Ví dụ Java 17: chỉ áp chính sách cho gói thanh toán

Ví dụ dưới đây dùng **JDK dynamic proxy** để mô phỏng một chính sách chỉ áp cho các thành phần trong gói `example.billing`. Đây là cơ chế bọc **lời gọi giao diện ở thời gian chạy**. Nó có ích để học cách diễn đạt *phạm vi*, nhưng **không phải bộ weaving có thể can thiệp mọi join point**.

Tạo cấu trúc tệp:

```text
src/
└── example/
    ├── Work.java
    ├── ScopeDemo.java
    ├── billing/
    │   └── ChargeWork.java
    └── reporting/
        └── ReportWork.java
```

### 7.1. Giao diện chung

Tệp `src/example/Work.java`:

```java
package example;

public interface Work {
    String execute();
}
```

### 7.2. Thành phần thuộc gói cần theo dõi

Tệp `src/example/billing/ChargeWork.java`:

```java
package example.billing;

import example.Work;

public final class ChargeWork implements Work {
    @Override
    public String execute() {
        return "Đã xử lý thanh toán";
    }
}
```

### 7.3. Thành phần ngoài phạm vi

Tệp `src/example/reporting/ReportWork.java`:

```java
package example.reporting;

import example.Work;

public final class ReportWork implements Work {
    @Override
    public String execute() {
        return "Đã tạo báo cáo";
    }
}
```

### 7.4. Áp dụng có điều kiện theo tên package

Tệp `src/example/ScopeDemo.java`:

```java
package example;

import example.billing.ChargeWork;
import example.reporting.ReportWork;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public final class ScopeDemo {
    private static boolean inBillingPackage(Class<?> type) {
        String name = type.getPackageName();
        return name.equals("example.billing")
                || name.startsWith("example.billing.");
    }

    private static Work monitorWhenInBilling(Work target) {
        if (!inBillingPackage(target.getClass())) {
            return target;
        }

        return (Work) Proxy.newProxyInstance(
                Work.class.getClassLoader(),
                new Class<?>[]{Work.class},
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Work.class
                            && method.getName().equals("execute")) {
                        System.out.println("[TRACE] "
                                + target.getClass().getSimpleName());
                    }

                    try {
                        return method.invoke(target, arguments);
                    } catch (InvocationTargetException ex) {
                        throw ex.getCause();
                    }
                });
    }

    public static void main(String[] args) {
        Work billing = monitorWhenInBilling(new ChargeWork());
        Work reporting = monitorWhenInBilling(new ReportWork());

        System.out.println(billing.execute());
        System.out.println(reporting.execute());
    }
}
```

Biên dịch/chạy từ thư mục chứa `src`, với Java 17 trở lên:

```bash
javac -encoding UTF-8 -d out src/example/Work.java src/example/billing/ChargeWork.java src/example/reporting/ReportWork.java src/example/ScopeDemo.java
java -cp out example.ScopeDemo
```

Kết quả:

```text
[TRACE] ChargeWork
Đã xử lý thanh toán
Đã tạo báo cáo
```

### 7.5. Phân tích ví dụ

1. `inBillingPackage` chỉ kiểm tra **package của lớp runtime**. Nó chấp nhận đúng `example.billing` hoặc cây package dưới đó và từ chối `example.reporting`.
2. `monitorWhenInBilling` tạo proxy cho `ChargeWork`. Với `ReportWork`, hàm trả về chính đối tượng đích, không tạo proxy và không có log.
3. Bộ lọc tên `execute` chỉ đóng vai trò mô phỏng điều kiện loại thao tác. Nó không chứng minh mọi method execution/call join point đã được chọn.
4. `InvocationTargetException` được bóc để chuyển tiếp nguyên nhân lỗi thật của phương thức đích.
5. Gọi trực tiếp `new ChargeWork().execute()` **không có** log vì không đi qua proxy. Lời gọi nội bộ trong lớp đích cũng không tự vòng qua proxy này.
6. Nếu một proxy được bọc thêm hoặc thành phần runtime là lớp sinh động trong package khác, điều kiện lọc theo **lớp runtime** có thể khác ý định lọc theo **lớp nghiệp vụ gốc**. Cần chọn rõ loại thông tin dùng để phân loại.
7. Nếu điều kiện trong ví dụ cần chuyển từ package sang vai trò giao diện hoặc annotation, phải thay **hàm quyết định đối tượng được bọc**; bản chất đường đi của proxy vẫn không đổi.

Ví dụ này nêu bật: phạm vi được xác định **trước khi tạo proxy**, nên “lớp ở đúng package” chưa đủ nếu người gọi không sử dụng proxy. Một bộ weaving trực tiếp vào mã lớp có thể có phạm vi khác, tùy cấu hình và loại điểm nối hỗ trợ.

## 8. Từ ví dụ sang mô hình AOP có pointcut đầy đủ hơn

Trong một mô hình hỗ trợ điều kiện **execution** và **within**, pointcut theo nhóm phương thức có thể biểu đạt mục tiêu rõ hơn:

```text
execution(* example.billing..*.*(..))
```

Đây là cú pháp biểu diễn theo ngôn ngữ pointcut của AspectJ, dùng **để minh họa**: lựa chọn các điểm thực thi phương thức khớp mẫu kiểu, tên và tham số trong cây package. Nó không phải Java và không được chạy bằng `javac`.

Nếu muốn giới hạn tới một điểm vào rõ hơn, có thể diễn giải quy tắc theo ý tưởng:

```text
Phương thức execution
AND loại xử lý tác vụ thanh toán
AND tên thao tác cần đo
AND NOT thuộc mã giám sát
```

Điều kiện kết hợp cần dùng đúng cú pháp của công cụ hiện thực. Không có biểu thức duy nhất cho mọi công nghệ AOP.

### 8.1. Khi chuyển từ package sang module

Nếu một module logic phân tán trên các package không chung tiền tố, thay lọc một cây package bằng **tập package được liệt kê**, hợp đồng kiểu hoặc metadata vai trò. Nếu ứng dụng có JPMS module, việc gắn thêm tiêu chí module runtime chỉ có hiệu lực khi bộ chọn có thể truy cập module và hỗ trợ điều kiện đó.

Đừng nhầm giữa “lấy tên module qua Java Reflection” và “mô hình weaving có bộ lọc theo module”. Đọc được metadata không đồng nghĩa weaver tự động sử dụng metadata ấy.

### 8.2. Khi chuyển từ module sang thành phần

Một thành phần `BillingGateway` có thể di chuyển package mà vai trò vẫn giữ nguyên. Lúc này pointcut theo giao diện hoặc annotation phù hợp hơn một chuỗi tên package cố định. Nhưng nếu thành phần có nhiều phương thức, vẫn phải chọn **phương thức nào**: đo ở ranh giới `charge()`, hay cả các phương thức kiểm tra/trợ giúp bên trong? Ranh giới hợp đồng nên được ưu tiên hơn “mọi phương thức của lớp này”.

## 9. Những vấn đề nâng cao về phạm vi

### 9.1. Một đối tượng có nhiều vai trò

Một lớp có thể vừa thực thi `CommandHandler`, vừa thực thi `HealthCheck`. Nếu pointcut chọn **toàn bộ đối tượng theo kiểu**, advice có thể áp lên các thao tác kiểm tra sức khỏe không liên quan. Điều kiện tốt hơn là chọn **vai trò + thao tác**.

Ngược lại, nếu một phương thức của lớp vừa thuộc nhóm phương thức xử lý lệnh vừa thuộc nhóm bảo trì, cần làm rõ mục đích nghiệp vụ thay vì cố suy luận hoàn toàn từ tên lớp.

### 9.2. Phân cấp kế thừa và kiểu khai báo

Khi một lớp con ở package A kế thừa phương thức khai báo ở package B, điều kiện theo **kiểu khai báo phương thức** và điều kiện theo **kiểu runtime đối tượng** có thể không giống nhau. Với override, bridge method do compiler sinh, generic hoặc lớp đại diện, số điểm và chữ ký được mô hình AOP nhận diện cũng có thể khác kỳ vọng.

Hãy kiểm thử bằng kiểu thực sự được gọi và điểm can thiệp mà cơ chế hỗ trợ. Không suy ra từ tên package của lớp con rằng mọi phương thức kế thừa được áp cùng chính sách.

### 9.3. Package và ranh giới độ tin cậy

Một chính sách kiểm soát quyền áp theo package không tự bảo vệ mọi đường gọi nếu có dịch vụ nội bộ, truy cập dữ liệu trực tiếp, reflection hoặc đường kết nối khác bỏ qua advice. **Bảo mật phải nằm ở ranh giới đáng tin cậy**, với quy tắc bảo vệ phù hợp; AOP có thể là cách tổ chức một phần cơ chế kiểm tra, nhưng chỉ khi mọi đường truy cập cần thiết được bao phủ.

Tên package, annotation hoặc tên module không được xem là chứng cứ người gọi được phép thực hiện hành động.

### 9.4. Giao thoa của nhiều aspect

Một nhóm phương thức có thể khớp cả aspect theo dõi toàn hệ thống và aspect kiểm toán riêng của module thanh toán. Nếu đều có around advice, thứ tự lồng nhau ảnh hưởng kết quả đo đạc và cách xử lý lỗi:

```text
Observe(Audit(Operation))
Audit(Observe(Operation))
```

Hai cấu trúc không luôn tương đương. Khi có retry, timeout, transaction hoặc kiểm tra quyền, thay thứ tự có thể thay đổi rõ hành vi. Quy tắc về thứ tự (precedence) cần được xác định theo cơ chế thực thi và kiểm thử trên đường thành công/lỗi.

### 9.5. Phạm vi code đã được weaving

Với compile-time/binary weaving, phải chắc chắn những lớp trong phạm vi mong đợi thực sự nằm trong sản phẩm được xử lý. Với load-time weaving, phải xác nhận lớp được tải sau khi bộ biến đổi có hiệu lực và không bị loại trừ. Với proxy, phải xác nhận tất cả người gọi mong đợi đều sử dụng đại diện.

Các giới hạn class loader, JPMS và quyền truy cập có thể khiến một lớp thuộc package hợp lệ về tên nhưng vẫn **không được can thiệp**. Vì vậy báo cáo “pointcut khớp 20 lớp” chưa chứng minh đã thực hiện advice tại 20 lớp đó.

### 9.6. Chi phí và lượng sự kiện

Nếu pointcut khớp một phương thức nội bộ được gọi hàng triệu lần, mỗi lần ghi log/đếm/truy vết có thể gây ảnh hưởng lớn. Giới hạn theo package **không tự đặt giới hạn tần suất**. Nên cân nhắc đo ở ranh giới nghiệp vụ hoặc dùng sampling phù hợp nếu chất lượng dữ liệu cho phép.

Lưu ý: sampling có thể chấp nhận được với số liệu hiệu năng, nhưng không tự nhiên phù hợp với kiểm toán bắt buộc hoặc quyết định an toàn. Cần phân biệt mục đích trước khi giảm số sự kiện.

### 9.7. Mã sinh, tên lại package và tái cấu trúc

Đổi package hoặc tách module có thể vô tình thêm/bớt các join point đang được áp dụng, dù nội dung phương thức không thay đổi. Một bộ lọc chỉ dựa trên tên có thể trở nên sai sau tái cấu trúc. Nếu gắn chính sách với một **hợp đồng ổn định**, hãy kiểm tra liệu ranh giới component có mô tả tốt hơn hay không.

Ngoài kiểm thử phương thức nghiệp vụ, cần có ca kiểm thử xác nhận những thành phần quan trọng được bao phủ sau mỗi thay đổi kiến trúc.

## 10. Cách thiết kế và kiểm chứng một pointcut có phạm vi

Hãy đi theo thứ tự:

1. **Xác định hành vi cần bảo đảm.** Ví dụ “đo một lần cho mỗi lệnh thanh toán ở điểm vào” khác “đo mọi phương thức trong thư viện thanh toán”.
2. **Chọn loại join point hỗ trợ được.** Method execution, method call, truy cập field hoặc loại khác, phụ thuộc mô hình AOP.
3. **Chọn biên kiến trúc.** Package chính xác, package subtree, module logic/JPMS/artifact hoặc vai trò component.
4. **Chọn điều kiện thao tác.** Chữ ký, kiểu khai báo, interface/annotation, tên hoặc tiêu chí động nếu thật sự cần.
5. **Định nghĩa loại trừ.** Tránh mã advice, đo lường nội bộ, thành phần phụ trợ hoặc đường gọi không liên quan.
6. **Xác minh khả năng weaving.** Những lớp nào thực sự bị biến đổi hoặc những lời gọi nào đi qua proxy?
7. **Kiểm thử đường áp dụng và đường không áp dụng.** Cả gọi thành công, lỗi, tự gọi nội bộ, nhiều lớp triển khai, đồng thời và tái cấu trúc.
8. **Kiểm soát tác dụng phụ.** Không nuốt lỗi, đổi kết quả, làm lặp thao tác hoặc ghi dữ liệu nhạy cảm nếu chính sách chỉ để quan sát.

### 10.1. Ma trận kiểm thử mẫu

| Tình huống | Kỳ vọng |
| --- | --- |
| Phương thức điểm vào đúng module/role | Advice chạy đúng số lần |
| Phương thức cùng package nhưng khác role | Không áp nếu role là điều kiện bắt buộc |
| Kiểu ở package con trong phạm vi đã chọn | Áp hoặc không theo quy tắc exact/subtree được tuyên bố |
| Package có tiền tố gần giống | Không khớp nhầm |
| Thành phần báo cáo ngoài module | Không áp advice của module thanh toán |
| Lời gọi đi trực tiếp tới đích trong cơ chế proxy | Thể hiện đúng giới hạn bypass |
| Phương thức kế thừa/override | Đúng quy tắc kiểu khai báo/kiểu thực |
| Artifact/lớp không được weaving | Được phát hiện thay vì nhầm là pointcut sai |
| Hai aspect cùng khớp | Thứ tự và lỗi đúng như hợp đồng |
| Tác vụ chạy đồng thời | Bối cảnh và số đo không lẫn nhau |

### 10.2. Dấu hiệu pointcut quá rộng hoặc quá hẹp

**Quá rộng** nếu log xuất hiện ở phương thức tiện ích nội bộ không liên quan, lặp nhiều lần trên một yêu cầu, đụng vào thư viện ngoài hoặc chứa thông tin nhạy cảm không cần thiết.

**Quá hẹp** nếu một implementation mới không có log, một đường gọi khác bỏ qua proxy, một package con bị loại không chủ ý hoặc chỉ một phần artifact được weaving.

Đừng sửa pointcut bằng cách thêm ký tự wildcard tùy tiện. Trước tiên phải xác định giới hạn là do **quy tắc chọn** hay do **khả năng kết hợp thực tế**.

## 11. Bài tập vận dụng

### Bài tập 1: Package cùng tiền tố

Bạn muốn chọn mọi kiểu trong `com.shop.order` và các gói con, nhưng không chọn `com.shop.ordering`. Vì sao `startsWith("com.shop.order")` chưa đúng? Viết điều kiện tên package phù hợp.

**Đáp án:** Tiền tố chưa có dấu chấm cho phép khớp `com.shop.ordering`. Một điều kiện minh họa hợp lệ trong Java là `name.equals("com.shop.order") || name.startsWith("com.shop.order.")`.

### Bài tập 2: Module và package

Module logic `shipping` gồm `app.shipping.api`, `app.shipping.domain` và `app.integrations.carriers`. Có thể chọn trọn module bằng `app.shipping..` không?

**Đáp án:** Không đủ: `app.integrations.carriers` nằm ngoài tiền tố. Cần mô hình module rõ, hợp đồng vai trò hoặc nhóm điều kiện package được liệt kê. Mẫu tên package không tự hiểu module logic.

### Bài tập 3: Vị trí gọi khác vị trí thực thi

Mã trong `app.order.application` gọi phương thức `app.payment.Client.send()`. Pointcut theo phạm vi *mã chứa lời gọi* trong order có giống pointcut chọn *thực thi của Client* trong payment không?

**Đáp án:** Không. Điểm call phía người gọi khác điểm execution phía đích; chúng có vị trí và khả năng hỗ trợ khác nhau. Phải chọn đúng loại join point và điều kiện phạm vi.

### Bài tập 4: Proxy bypass

Một proxy được tạo cho `BillingWork` thuộc package cần theo dõi, nhưng một người gọi vẫn giữ tham chiếu đối tượng gốc. Vì sao `original.execute()` không có log dù tên package đúng?

**Đáp án:** Bộ lọc package chỉ quyết định **có tạo proxy hay không**. Lời gọi qua tham chiếu gốc bỏ qua proxy, nên không được chặn. Cần thống nhất đường cung cấp đối tượng hoặc chọn cơ chế phù hợp nếu mọi đường gọi đều bắt buộc được can thiệp.

### Bài tập 5: JPMS và bảo mật

Một package được `exports` trong `module-info.java`. Điều đó có nghĩa mọi lời gọi trong package đều tự động có advice và được kiểm soát quyền không?

**Đáp án:** Không. `exports` thuộc quy tắc truy cập kiểu của JPMS; pointcut và weaving là cơ chế riêng. Kiểm soát quyền còn cần ranh giới đáng tin cậy và kiểm thử đường truy cập.

### Bài tập 6: Giao thoa chính sách

Một pointcut theo `payment` đo toàn bộ thời gian, còn pointcut theo `PaymentGateway` thực hiện retry. Bạn đặt `Timing(Retry(Operation))` hay `Retry(Timing(Operation))` nếu muốn đo tổng thời gian của cả chuỗi thử?

**Đáp án:** Dùng cấu trúc ý tưởng `Timing(Retry(Operation))` để đo toàn bộ chuỗi. Đo bên trong retry cho ra thời lượng của từng lần thử nếu advice được gọi mỗi lần. Cần kiểm tra thứ tự thực tế của cơ chế AOP và không retry bừa thao tác có tác dụng phụ.

## 12. Ghi nhớ

- Giới hạn pointcut theo **package, module hoặc component** là cách nói rõ *ở đâu* chính sách AOP được áp dụng; vẫn cần xác định **loại join point và thao tác**.
- **Package chính xác** khác **cây package con**; tên gần giống không tự thuộc cùng phạm vi.
- **Module logic, artifact, JPMS module và tiến trình triển khai** có ranh giới khác nhau; không được đồng nhất chúng.
- **Thành phần theo vai trò** (interface, annotation, hợp đồng cụ thể) có thể ổn định hơn tên package khi tái cấu trúc, nhưng vẫn phải chọn phương thức và đường thực thi.
- **Điều kiện pointcut khớp** chưa đủ: cơ chế weaving/proxy phải thực sự can thiệp vào đúng đường gọi.
- Pointcut tốt phải **đủ hẹp, có chủ đích, kiểm thử được và bảo toàn hợp đồng nghiệp vụ**, đặc biệt khi cùng vị trí chịu ảnh hưởng của nhiều aspect.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-basics-003">Khớp kiểu trả về, danh sách tham số và mức truy cập</a>

<details>
<summary>Xem chi tiết</summary>

# Khớp kiểu trả về, danh sách tham số và mức truy cập trong Pointcut

## 1. Mục tiêu của việc khớp chữ ký phương thức

Trong lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP), **pointcut** mô tả tập những *join point* (điểm thực thi có thể can thiệp) mà một chính sách cắt ngang muốn chọn. Với join point liên quan đến phương thức, một cách chọn thường gặp là dựa vào **chữ ký phương thức**: tên phương thức, kiểu khai báo, kiểu trả về, danh sách tham số và mức truy cập.

Ví dụ, ta muốn đo thời gian của những thao tác trả về `String`, nhận đúng một tham số `String`, và có mức truy cập `public`. Điều kiện này khác với yêu cầu “chọn mọi phương thức có chữ `find` trong tên”. Kiểu trả về và danh sách tham số giúp phân biệt các nhóm phương thức có cùng tên, còn mức truy cập giúp xác định điểm cần quan sát theo giao diện sử dụng của đối tượng.

Điểm quan trọng: **khớp chữ ký không đồng nghĩa với thực thi advice**. Một biểu thức có thể mô tả đúng phương thức cần chọn nhưng cơ chế AOP đang dùng chưa chắc can thiệp được phương thức đó. Vì vậy cần hiểu hai việc độc lập:

1. **Matching:** điều kiện pointcut có khớp với mô tả của một join point hay không?
2. **Interception/weaving:** công cụ có thật sự đưa lời gọi hoặc lần thực thi ấy qua advice hay không?

Bài này nói về khớp kiểu trả về, tham số, mức truy cập trong AOP tổng quát; mọi biểu thức minh họa sẽ được đánh dấu rõ là **mẫu khái niệm**, bởi các ngôn ngữ pointcut thực tế không hoàn toàn giống nhau.

## 2. Chữ ký phương thức gồm những thông tin nào?

Xét một lớp Java:

~~~java
class ProductCatalog {
    public String find(String sku) { return sku; }
    public String find(String sku, int limit) { return sku + limit; }
    protected Number total(int quantity, long price) {
        return quantity * price;
    }
    private void refreshCache() { }
}
~~~

Ví dụ trên là mã minh họa cấu trúc (không chứa `main` để chạy trực tiếp).

Từ chữ ký của phương thức `find(String sku)`, ta nhận diện:

- **Mức truy cập:** `public`.
- **Kiểu trả về khai báo:** `String`.
- **Tên phương thức:** `find`.
- **Danh sách tham số:** một phần tử kiểu `String`.
- **Lớp khai báo:** `ProductCatalog`.
- **Tên biến tham số:** `sku`, chỉ giúp người đọc hiểu ý nghĩa; tên biến không phải thành phần dùng để overload phương thức Java và thông thường không phải dữ liệu bắt buộc của signature matching.

Để diễn tả điều kiện một cách trực quan, có thể tưởng tượng khuôn mẫu:

~~~text
[visibility] [return-type] [declaring-type].[method-name]([parameter-pattern])
~~~

Đây là **ký pháp giảng giải**, không phải cú pháp bảo đảm hoạt động trong mọi engine AOP. Tùy công nghệ, pointcut có thể dùng mẫu tên, toán tử điều kiện, annotation, biểu thức lọc thời điểm thực thi, hay một API lập trình để thực hiện cùng ý định chọn điểm.

Chữ ký phương thức cũng không cho ta biết toàn bộ hoạt động tại runtime. Một phương thức có thể gọi nhiều phương thức khác, ném ngoại lệ, hoặc được triển khai lại trong lớp con. Tránh nhầm “chữ ký mô tả điểm” với “nghiệp vụ bên trong điểm”.

## 3. Khớp kiểu trả về: chính xác hay theo quan hệ kiểu?

**Kiểu trả về khai báo** là kiểu đứng trước tên phương thức. Ví dụ:

~~~java
public String label() { return "ok"; }
public Object data() { return "ok"; }
public int quantity() { return 3; }
public Integer boxedQuantity() { return 3; }
public void reset() { }
~~~

Các phương thức trên đều có thể có cùng loại kết quả runtime trong một số lần gọi, nhưng kiểu trả về *khai báo* của chúng khác nhau. `label()` khai báo `String`, còn `data()` khai báo `Object` dù nó trả về đối tượng `String` ở ví dụ. Một pointcut khớp kiểu khai báo `String` không nên tự suy ra `data()` sẽ khớp chỉ vì giá trị trả về hiện tại là chuỗi.

### 3.1. Khớp kiểu chính xác

Một điều kiện khớp **chính xác** `String` chọn phương thức có kiểu trả về khai báo tương ứng, nếu engine áp dụng quy tắc này. Trong Java Reflection, ta có thể kiểm tra:

~~~java
method.getReturnType() == String.class
~~~

Với `void`, dùng `void.class`; với kiểu nguyên thủy, `int.class` khác `Integer.class`. Mẫu `Number` khác `int`, và `Integer` không tự đồng nhất với `int` khi so khớp chữ ký bằng `Class<?>`.

### 3.2. Khớp theo phân cấp kiểu

Một cơ chế khác có thể cho phép điều kiện theo subtype hoặc tính gán được. Ví dụ, `Number.class.isAssignableFrom(Integer.class)` là `true`; nhưng `Number.class.isAssignableFrom(int.class)` là `false`. Nếu công cụ hỗ trợ điều kiện kiểu rộng hơn, hãy kiểm tra *hướng quan hệ kiểu* và khả năng khớp khai báo của công cụ. Không tự chuyển quy tắc reflection thành cú pháp pointcut của mọi engine.

Điều kiện `Object` cũng không đồng nghĩa với “mọi kiểu trả về” dưới quy tắc gán được thông thường, bởi primitive và `void` cần xem xét riêng.

### 3.3. Kiểu khai báo so với giá trị runtime

`Object load()` có thể trả `new String("x")` trong lần gọi hiện tại và một kiểu khác lần sau. **Pointcut chọn theo chữ ký** thường quyết định dựa trên mô tả tĩnh của phương thức, không dựa vào loại đối tượng vừa trả về. Nếu chính sách muốn quyết định theo kết quả thực tế, đó có thể là điều kiện xử lý trong **advice sau khi thực thi**, không còn là cùng một điều kiện chỉ dựa vào chữ ký.

### 3.4. Covariant return và bridge method

Java cho phép phương thức override trả về kiểu con phù hợp:

~~~java
class Parent {
    Number value() { return 1; }
}
class Child extends Parent {
    @Override
    Integer value() { return 2; }
}
~~~

Khi xem chữ ký khai báo ở `Parent` và `Child`, kiểu trả về khác nhau. Trình biên dịch còn có thể sinh **bridge method** phục vụ đa hình hoặc generic erasure. Một công cụ phân tích bytecode có thể nhìn thấy phương thức tổng hợp mà người viết không chủ ý khai báo trực tiếp. Khi các pointcut chọn theo signature, cần xác minh engine xử lý bridge/synthetic method thế nào, và liệu một thao tác có bị ghi nhận hai lần không. Java Reflection cung cấp `Method.isBridge()` và `Method.isSynthetic()` để phân tích.

## 4. Khớp danh sách tham số: số lượng, thứ tự và kiểu

Tham số của một phương thức được phân biệt bởi **số lượng** và **kiểu theo thứ tự**. Ba phương thức sau là ba overload khác nhau:

~~~java
void send(String value) { }
void send(String value, int attempts) { }
void send(int attempts, String value) { }
~~~

Nếu pointcut yêu cầu đúng `(String, int)`, nó không khớp `(String)` và cũng không khớp `(int, String)`. Đây là một trong những lỗi chọn nhầm phương thức phổ biến khi tên phương thức trùng nhau.

Một số dạng điều kiện thường gặp:

| Ý định | Cách hiểu |
| --- | --- |
| Không tham số | Chọn phương thức có danh sách tham số rỗng `()` |
| Chính xác một `String` | Danh sách có một phần tử kiểu `String` |
| Chính xác `(String, int)` | Hai tham số theo đúng thứ tự và kiểu |
| Tham số đầu là `String`, còn lại tùy ý | Cần cơ chế biểu diễn phần đuôi có độ dài linh hoạt |
| Có bất kỳ danh sách tham số nào | Điều kiện không giới hạn số lượng/kiểu |
| Tham số thuộc phân cấp `Number` | Phải làm rõ khớp chính xác hay subtype |

Các ký hiệu wildcard như `*` hoặc `..` thường xuất hiện trong một số ngôn ngữ pointcut, nhưng **ý nghĩa và vị trí hợp lệ phụ thuộc công cụ**. Đừng mặc định `(..)` luôn có cùng nghĩa ở mọi công nghệ; ký pháp `String, ..` hay `*, String` cũng có thể được hỗ trợ khác nhau.

### 4.1. Primitive khác wrapper

`int` khác `Integer`; `long` khác `Long`. Java cho phép boxing/unboxing tại nhiều vị trí lời gọi, nhưng **kiểu tham số được khai báo** vẫn khác nhau. Chọn chữ ký `process(Integer)` không tự nhiên khớp mọi `process(int)` chỉ vì lời gọi Java có thể tự boxing.

### 4.2. Mảng và varargs

Khai báo `accept(String... values)` sử dụng varargs ở phía lời gọi, nhưng chữ ký phản chiếu của nó có **một tham số kiểu `String[]`**. Có thể kiểm tra `Method.isVarArgs()` nếu muốn phân biệt ý định varargs; dùng `getParameterTypes()` sẽ thấy kiểu mảng.

Tương tự, `accept(String[])` và `accept(String...)` có cùng biểu diễn tham số ở mức chữ ký JVM, nên không thể overload hai khai báo ấy trong cùng một lớp chỉ bằng dấu ba chấm.

### 4.3. Generic và type erasure

Với `List<String>` và `List<Integer>`, thông tin generic có thể còn trong metadata nhưng Java **erasure** khiến kiểu tham số runtime đều là `List` ở nhiều phép đối chiếu. Reflection `getParameterTypes()` trả về lớp thô; `getGenericParameterTypes()` cho thông tin generic nếu metadata được lưu. Một engine AOP có thể khớp signature sau erasure hoặc dùng metadata generic theo quy tắc riêng. Không khẳng định mọi pointcut phân biệt được `List<String>` với `List<Integer>`.

### 4.4. Tên tham số không phải khóa chính

`pay(int amount)` và `pay(int value)` có cùng chữ ký overload theo kiểu tham số; đổi tên biến không tạo overload mới. Trong thực tế, metadata tên tham số có thể không được bảo toàn như source nếu cách biên dịch khác nhau, nên chọn pointcut theo kiểu và thứ tự tham số đáng tin hơn chọn theo tên biến.

### 4.5. Signature pattern khác binding giá trị thực tế

Có sự khác nhau giữa yêu cầu **“chỉ các phương thức có tham số kiểu `String`”** và **“chỉ những lần gọi mà giá trị `String` thực tế thỏa điều kiện nào đó”**. Trường hợp thứ nhất dựa trên chữ ký; trường hợp thứ hai cần ngữ cảnh runtime hoặc điều kiện trong advice. Kết hợp cả hai phải xác định chính xác thời điểm chọn, đặc biệt với `null`, subtype và overload.

## 5. Khớp mức truy cập: public, protected, package-private, private

Java có bốn mức truy cập cần hiểu khi chọn join point:

| Mức truy cập | Khai báo | Ý nghĩa chính trong Java | Điều cần nhớ khi áp dụng pointcut |
| --- | --- | --- | --- |
| **public** | `public` | Truy cập rộng rãi theo các ràng buộc module/type | Thường xuất hiện ở hợp đồng API nhưng chưa chắc mọi lời gọi được proxy chặn |
| **protected** | `protected` | Truy cập trong package và ở subclass theo quy tắc Java | Có thể yêu cầu can thiệp trong class/subclass; phụ thuộc cơ chế |
| **package-private** | Không ghi modifier truy cập | Truy cập trong cùng package | Không phải từ khóa `package-private` trong source |
| **private** | `private` | Chỉ truy cập theo quy tắc private của Java | Proxy dựa trên override thường không thể override phương thức private |

**Mức truy cập** là một đặc điểm của khai báo Java; **khả năng intercept** còn phụ thuộc cơ chế AOP. Một engine chỉ quan sát lời gọi đi qua interface công khai không đồng nghĩa với việc nó thực thi được advice tại bất kỳ method `private` nào trong đối tượng gốc. Ngược lại, một hệ thống weaving trong bytecode có thể hỗ trợ những join point không thể quan sát bằng proxy, nếu mô hình join point và vùng weaving cho phép.

### 5.1. Không nhầm với modifier khác

`static`, `final`, `abstract` và `synchronized` không phải là bốn mức truy cập. Ví dụ, `public final boolean ready()` vẫn là **public**. Tuy nhiên `final` có thể hạn chế cơ chế proxy cần override phương thức; `static` không là lời gọi instance thông thường. Các chi tiết này quan trọng để đánh giá khả năng can thiệp, nhưng không được dùng thay cho phép lọc theo `public/protected/package-private/private`.

### 5.2. Lớp khai báo và thành viên thực sự được quan sát

Một phương thức public trong một lớp không public, một phương thức được kế thừa, hoặc một method được override có thể được biểu diễn khác nhau ở caller, interface và implementation. Khi áp dụng pointcut, phải xác định engine dùng **chữ ký được nhìn tại vị trí gọi**, **chữ ký implementation**, hay **join point tại lần thực thi**. Các kết quả không phải lúc nào cũng tương đồng.

Với Java Reflection:

- `Class.getMethods()` trả các phương thức public có thể truy cập theo API Reflection, gồm các thành viên kế thừa.
- `Class.getDeclaredMethods()` trả phương thức được khai báo trực tiếp trong lớp, ở mọi mức truy cập, nhưng không trả các phương thức chỉ được kế thừa.
- `Method.getModifiers()` kết hợp với `Modifier.isPublic()`, `isProtected()` và `isPrivate()` giúp kiểm tra mức truy cập. Nếu cả ba đều false, đó là package-private trong mô hình Java thông thường.

Dù Reflection nhận diện được một phương thức private, điều đó **không chứng minh** công cụ AOP có thể đặt advice vào mọi lần thực thi private.

## 6. Ví dụ Java 17+: kiểm tra pointcut theo cả ba trục

Ví dụ dưới đây sử dụng **Reflection để mô phỏng bước lựa chọn chữ ký**: mỗi hàm predicate đại diện cho một pointcut cụ thể; chương trình liệt kê phương thức khớp. Đây là ví dụ Java 17+ chạy độc lập, **không phải hệ thống weaving, cũng không chạy advice**. Việc không chạy advice là có chủ đích: trước hết ta cần kiểm chứng chính xác **bộ chọn** để tránh nhầm lẫn nó với cơ chế can thiệp.

Tạo tệp `SignaturePointcutDemo.java`:

~~~java
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SignaturePointcutDemo {
    static class Catalog {
        public String find(String code) { return code; }
        public String find(String code, int limit) { return code + limit; }
        protected Number total(int quantity, long price) {
            return quantity * price;
        }
        private void refresh() { }
        static int version() { return 1; }
        public final boolean ready() { return true; }
    }

    static boolean types(Method method, Class<?> result, Class<?>... parameters) {
        return method.getReturnType() == result
                && Arrays.equals(method.getParameterTypes(), parameters);
    }

    static String signature(Method method) {
        String parameters = Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "));
        return method.getName() + "(" + parameters + ")";
    }

    static void report(String rule, Predicate<Method> pointcut) {
        String matches = Arrays.stream(Catalog.class.getDeclaredMethods())
                .filter(pointcut)
                .map(SignaturePointcutDemo::signature)
                .sorted()
                .collect(Collectors.joining(", "));
        System.out.println(rule + ": "
                + (matches.isEmpty() ? "(none)" : matches));
    }

    public static void main(String[] args) {
        report("public String(String)", method ->
                Modifier.isPublic(method.getModifiers())
                        && types(method, String.class, String.class));
        report("public String(String,int)", method ->
                Modifier.isPublic(method.getModifiers())
                        && types(method, String.class, String.class, int.class));
        report("protected Number(int,long)", method ->
                Modifier.isProtected(method.getModifiers())
                        && types(method, Number.class, int.class, long.class));
        report("private void()", method ->
                Modifier.isPrivate(method.getModifiers())
                        && types(method, void.class));
        report("public int()", method ->
                Modifier.isPublic(method.getModifiers())
                        && types(method, int.class));
        report("package-private int()", method ->
                !Modifier.isPublic(method.getModifiers())
                        && !Modifier.isProtected(method.getModifiers())
                        && !Modifier.isPrivate(method.getModifiers())
                        && types(method, int.class));
        report("public boolean()", method ->
                Modifier.isPublic(method.getModifiers())
                        && types(method, boolean.class));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 SignaturePointcutDemo.java
java SignaturePointcutDemo
~~~

Đầu ra đã kiểm chứng:

~~~text
public String(String): find(String)
public String(String,int): find(String, int)
protected Number(int,long): total(int, long)
private void(): refresh()
public int(): (none)
package-private int(): version()
public boolean(): ready()
~~~

**Giải thích kết quả:**

1. `public String(String)` chỉ khớp `find(String)`; overload `find(String,int)` không có đúng một tham số.
2. `public String(String,int)` chọn overload hai tham số theo đúng thứ tự.
3. `protected Number(int,long)` chọn `total(int,long)`, không cần biết số thực tế được trả về.
4. `private void()` chọn `refresh()` dù phương thức private không thể được proxy chặn theo mọi cơ chế. **Matching thành công không chứng minh advice sẽ chạy.**
5. `public int()` không chọn `version()` vì `version()` là package-private.
6. `package-private int()` chọn `version()`; modifier `static` không làm nó trở thành public.
7. `public boolean()` chọn `ready()` dù phương thức có thêm modifier `final`.

`Arrays.equals(method.getParameterTypes(), parameters)` so kiểu khai báo và thứ tự tham số chính xác. `getDeclaredMethods()` được dùng để nhìn cả method private/protected của chính `Catalog`. Hàm `sorted()` giúp thứ tự in kết quả ổn định, tránh phụ thuộc thứ tự mà Reflection trả ra.

Nếu muốn dùng kết quả chọn của một predicate để làm pointcut trong cơ chế AOP thực, cần thêm bước gắn với **join point mà công cụ hỗ trợ** và kiểm thử advice ở đúng đường gọi. Chương trình này chỉ xác minh cú pháp Java và **logic lựa chọn chữ ký**.

## 7. Kết hợp ba trục trong một pointcut thực tế

Một pointcut có thể ghép nhiều điều kiện bằng logic AND:

~~~text
P(method) =
    method.access == PUBLIC
    AND method.declaredReturnType == String
    AND method.name == "find"
    AND method.parameterTypes == [String, int]
~~~

Đây là **mô tả thuật toán chọn**, không phải một cú pháp dùng được ngay trong engine bất kỳ. Điều kiện ấy sẽ chọn `public String find(String,int)`, đồng thời loại bỏ `find(String)` vì số tham số khác, `protected Number total(int,long)` vì tên, kiểu trả về và mức truy cập khác.

Muốn chọn nhiều chữ ký có thể dùng logic OR giữa các điều kiện đã định nghĩa; muốn loại trừ utility method có thể dùng NOT ở nơi công cụ hỗ trợ. Lưu ý: pointcut quá rộng (ví dụ mọi phương thức public trả `String`) có thể bao cả getter và phương thức tiện ích không liên quan; pointcut quá hẹp (chỉ một overload) có thể bỏ sót những lời gọi mà nghiệp vụ cần audit.

Một cách thực hành là lập **bảng phải khớp/không được khớp** trước khi hiện thực:

| Phương thức | Khớp `public String find(String,int)`? | Vì sao |
| --- | --- | --- |
| `public String find(String,int)` | Có | Tất cả điều kiện trùng |
| `public String find(String)` | Không | Thiếu tham số `int` |
| `protected String find(String,int)` | Không | Mức truy cập khác |
| `public Object find(String,int)` | Không với khớp chính xác | Kiểu trả về khai báo khác |
| `public String find(int,String)` | Không | Thứ tự tham số khác |
| `public String lookup(String,int)` | Không | Tên khác |

Bảng này là ví dụ cho bộ chọn **khớp chính xác tất cả thành phần**, không phải kết luận về một cú pháp pointcut cụ thể. Khi engine cho phép mẫu wildcard hoặc phép chọn theo subtype, các dòng có thể có kết quả khác theo chính sách đó.

## 8. Ranh giới của call và execution khi khớp chữ ký

AOP có thể mô tả hai điểm can thiệp khác nhau:

- **Method call join point:** nơi mã gọi một phương thức. Nó gắn với phía caller và thông tin về lời gọi.
- **Method execution join point:** nơi thân phương thức thực sự được thực thi. Nó gắn với phía callee/implementation.

Nếu một biến khai báo theo interface nhưng đối tượng thật là implementation, chữ ký người gọi thấy có thể khác với chữ ký được phản chiếu trên method implementation, đặc biệt khi có override và kiểu trả về covariant. Ngoài ra, dynamic dispatch có thể chọn một thân phương thức khác với điều người đọc đoán từ tên biến.

Ví dụ minh họa:

~~~java
interface Reader {
    Object read();
}
class JsonReader implements Reader {
    @Override
    public String read() { return "json"; }
}
~~~

Về khai báo, `Reader.read()` trả `Object`, còn `JsonReader.read()` trả `String`. Nếu công cụ phân biệt **call** với **execution**, dùng kiểu trả về trong bộ lọc phải hiểu nó gắn với join point nào, tránh tưởng rằng chỉ có một chữ ký duy nhất tồn tại ở mọi tầng.

Đây cũng là lý do không nên bê nguyên một bộ predicate Reflection và mặc định nó tương đương với cả hai dạng pointcut. Reflection xem một `Method` cụ thể, trong khi engine AOP có thể định nghĩa quy tắc resolution theo điểm gọi, điểm thực thi, declaring type hoặc kiểu runtime.

## 9. Các giới hạn can thiệp theo công cụ

### 9.1. Chọn được không có nghĩa là chặn được

Một chương trình Reflection có thể tìm `private void refresh()`. Một **proxy qua interface** lại chỉ can thiệp được lời gọi qua interface của nó. Nếu `refresh()` chỉ được gọi bên trong target thì proxy không nhìn thấy đường đó. Phương thức được đánh dấu `final` cũng có thể gây giới hạn với một proxy cần override, dù nó là `public`.

Một cơ chế **bytecode weaving** có thể can thiệp sâu hơn trong thân phương thức nếu nó thực sự hỗ trợ loại join point tương ứng và lớp chứa method nằm trong vùng được weaving. Không thể suy ra tính năng weaving từ việc một công cụ tự nhận là “AOP”.

### 9.2. Modifier và phạm vi truy cập khác biệt

Mức `private`, package-private, `protected` và `public` tuân theo quy tắc truy cập Java, nhưng khả năng phân giải chữ ký, gọi reflection, class visibility, module boundary, classloader và cách công cụ proxy/weaving hoạt động có thể giới hạn thêm. Trên JDK có module mạnh, “biết `Method` tồn tại” không đồng nghĩa “luôn được phép truy cập/instrument ở runtime”.

### 9.3. Overload, override, bridge, synthetic

Pointcut chỉ dùng tên dễ chọn nhầm overload. Với override, engine có thể xem xét chữ ký khai báo và kiểu implementation. Với bridge/synthetic methods, hãy kiểm tra luật lọc của công cụ trước khi tính metrics hay audit để tránh ghi trùng. Đây là chi tiết nâng cao, nhưng có thể gây lỗi nếu tác động cắt ngang đòi hỏi chính xác số lần gọi.

### 9.4. Generic và varargs không giữ nguyên hình thức source

Signature ở mức bytecode có thể khác cách người đọc hiểu từ mã nguồn có generic và varargs. Điều kiện pointcut cần dựa trên đúng loại metadata engine hỗ trợ. Nếu engine xử lý kiểu sau erasure, cố chọn `List<String>` chính xác có thể không có nghĩa như mong đợi.

### 9.5. Phân biệt lựa chọn tĩnh và quyết định động

Bộ lọc `return type` và `parameter types` dựa trên khai báo thường có thể kiểm tra trước khi lời gọi chạy. Kiểm tra nội dung runtime, như `quantity > 0`, cần dữ liệu đối số trong lúc thực thi, hoặc lời khuyên xử lý trong advice. Không đánh đồng **matching một phương thức có tham số `int`** với **lọc những lời gọi có giá trị `int` dương**.

## 10. Thiết kế và kiểm thử pointcut có thể bảo trì

Một quy trình có thể áp dụng cho việc lựa chọn theo ba trục:

1. **Nêu đúng hành vi cắt ngang:** muốn audit, tracing, metrics hay ràng buộc truy cập nào? Nếu chính sách chỉ áp dụng cho giao diện API, mô tả ranh giới ấy trước.
2. **Liệt kê các chữ ký mẫu:** bao gồm overload cùng tên, kiểu trả về khác, tham số đảo thứ tự và mức truy cập khác. Đây là bộ mẫu xác minh đầu tiên.
3. **Chọn quy tắc so kiểu:** khớp chính xác, subtype hay wildcard? Primitive, wrapper, mảng, generic có ý nghĩa thế nào?
4. **Chọn loại join point:** call hay execution, và phương thức thuộc interface hay implementation? Phạm vi can thiệp có hỗ trợ private/final/static hay không?
5. **Kiểm tra matching độc lập:** như chương trình Reflection ở trên để rõ logic mong muốn; khi chuyển sang engine AOP, thêm bài kiểm tra thật trên engine.
6. **Chạy qua ranh giới thực:** xác minh số lần advice chạy khi lời gọi đúng/không đúng điều kiện; kiểm tra trường hợp target được gọi trực tiếp, self-invocation và overload khác.
7. **Kiểm tra kết quả và tác dụng phụ:** advice chỉ quan sát không nên vô tình thay kết quả, ngoại lệ, số lần chạy nghiệp vụ hoặc dữ liệu nhạy cảm.

Bảng kiểm thử tối thiểu cho một pointcut chọn `public String find(String,int)`:

| Ca thử | Kỳ vọng matching | Kỳ vọng về advice khi engine hỗ trợ điểm tương ứng |
| --- | --- | --- |
| Gọi đúng overload với `("X", 2)` | Match | Chạy một lần theo chính sách |
| Gọi `find("X")` | Không match | Không chạy |
| Gọi phương thức cùng tên khác quyền truy cập | Không match | Không chạy |
| Gọi method cùng tên nhưng kiểu trả về khác | Không match với exact type | Không chạy |
| Gọi trực tiếp target bỏ qua proxy | Chữ ký vẫn match về mặt lý thuyết | Có thể không chạy vì bỏ qua ranh giới |
| Gọi nội bộ `this.find(...)` | Chữ ký có thể match | Tùy proxy/weaving, phải kiểm chứng |
| Có bridge method hoặc nhiều advice | Phụ thuộc bộ chọn và engine | Không được phát sinh audit trùng ngoài ý muốn |

## 11. Lỗi thường gặp khi khớp chữ ký

- **Nhầm `Object` với mọi kiểu trả về:** primitive và `void` cần xử lý riêng; phân biệt exact match với quan hệ assignability.
- **Dựa vào kiểu của giá trị trả về runtime:** chữ ký mô tả kiểu *khai báo*, không phải giá trị ngẫu nhiên trong một lần chạy.
- **Bỏ qua thứ tự tham số:** `(String,int)` khác `(int,String)`.
- **Lẫn primitive với wrapper:** `int` và `Integer` là hai kiểu khai báo khác nhau.
- **Coi varargs là nhiều tham số tùy ý:** `String...` tương ứng với một tham số mảng `String[]` ở JVM.
- **Quên generic erasure và bridge methods:** có thể chọn sai hoặc ghi trùng trong các mô hình áp dụng theo bytecode.
- **Coi package-private là một modifier viết trong source:** nó xuất hiện khi không ghi `public/protected/private`.
- **Cho rằng public luôn chặn được và private luôn giống nhau giữa mọi engine:** khả năng can thiệp phụ thuộc proxy/weaving và điểm thực thi.
- **Áp dụng mẫu wildcard mà chưa xác minh cú pháp:** ký hiệu và ý nghĩa phải dựa trên tài liệu của công cụ sử dụng.
- **Chỉ kiểm thử predicate, bỏ kiểm thử advice thực tế:** matching đúng chưa đủ chứng minh pointcut chạy trên đường gọi thực.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** `public String get(String)` và `public String get(String,int)` có cùng tên. Pointcut yêu cầu chính xác một tham số `String` chọn được phương thức nào?  
**Đáp án:** Chỉ `get(String)`; số lượng tham số của overload còn lại khác.

**Câu 2.** `public Object get()` luôn trả về đối tượng `String` trong chương trình đang chạy. Quy tắc khớp kiểu trả về khai báo chính xác `String` có chọn `get()` không?  
**Đáp án:** Không; kiểu trả về khai báo là `Object`.

**Câu 3.** `private void refresh()` có thể được Reflection tìm thấy bằng `getDeclaredMethods()`. Có đủ kết luận mọi engine proxy có thể chạy advice ở phương thức ấy không?  
**Đáp án:** Không. Reflection dùng để xem metadata; can thiệp phụ thuộc join point model và kỹ thuật proxy/weaving.

**Câu 4.** Khác biệt chính giữa `String... values` và `String[] values` khi đối chiếu signature JVM là gì?  
**Đáp án:** Cả hai đều có một tham số kiểu `String[]`; có thể dùng thông tin varargs riêng để nhận biết cách khai báo/hành vi gọi.

**Câu 5.** Muốn biết advice chạy đúng phải kiểm tra điều gì ngoài signature?  
**Đáp án:** Loại join point call/execution, phạm vi proxy/weaving, đường gọi thực, thứ tự advice và số lần kích hoạt.

## 13. Ghi nhớ

Pointcut khớp chữ ký là cách chọn phương thức theo **kiểu trả về, danh sách tham số và mức truy cập**, thường kết hợp thêm tên và kiểu khai báo. Khi viết quy tắc, phải phân biệt **khớp chính xác với khớp theo quan hệ kiểu**, **kiểu khai báo với dữ liệu runtime**, **primitive với wrapper**, **overload với override**, và **signature matching với interception thực tế**. Thiết kế một bảng những phương thức phải/không được chọn, rồi kiểm thử trên đúng cơ chế AOP được sử dụng, sẽ giúp advice áp dụng đúng phạm vi mà vẫn giữ mã nghiệp vụ rõ ràng.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-basics-004">Phân biệt kiểu khai báo và kiểu thực tế khi lựa chọn điểm can thiệp</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt kiểu khai báo và kiểu thực tế khi lựa chọn điểm can thiệp

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**), một pointcut chọn những điểm thực thi (join point) nơi hành vi bổ sung (advice) có thể chạy. Khi pointcut xét **kiểu**, cần xác định đang xét kiểu được biết ở lúc khai báo/biên dịch hay lớp của đối tượng thật ở thời điểm chạy. Hai loại thông tin này có thể khác nhau do **giao diện, kế thừa, đa hình, override, proxy và generic**.

Đây là kiến thức nền tảng của **pointcut cơ bản**, nhưng nếu nhầm loại kiểu được dùng để so khớp, một advice có thể áp dụng quá rộng, quá hẹp hoặc bỏ sót đường gọi quan trọng.

## 1. Phân biệt ba nguồn thông tin về kiểu

Ví dụ minh họa:

~~~java
Notifier notifier = new EmailNotifier();
notifier.send("Hello");
~~~

Đoạn trên giả định `Notifier` là interface, `EmailNotifier` là lớp triển khai; đây chưa phải chương trình độc lập. Ta có:

- **Declared/static type của biến** `notifier`: `Notifier`. Kiểu này được ghi trong mã nguồn và dùng để kiểm tra lời gọi hợp lệ tại lúc biên dịch.
- **Runtime/dynamic type của đối tượng** mà biến tham chiếu: `EmailNotifier`. Với một đối tượng khác null, `getClass()` trả về lớp runtime đó.
- **Declaring type của một phương thức cụ thể**: kiểu khai báo chính phương thức đang được mô tả. Nó có thể là interface, lớp cha hoặc lớp con override, tùy đang xét phương thức nào.

Điểm thứ ba rất quan trọng. `Method.getDeclaringClass()` của Java Reflection trả về lớp hoặc giao diện khai báo đối tượng `Method`, **không đồng nghĩa** lớp runtime của đối tượng đích. Khi phương thức có bản khai báo ở interface và bản triển khai tại lớp con, các đối tượng `Method` tương ứng có thể có `getDeclaringClass()` khác nhau.

| Câu hỏi | Ví dụ | Nguồn dữ liệu |
| --- | --- | --- |
| Biến/biểu thức có kiểu tĩnh gì? | `Notifier` | Khai báo và phân giải kiểu lúc biên dịch |
| Phương thức trong một chữ ký do ai khai báo? | `Notifier.send` hoặc `EmailNotifier.send` | Thông tin chữ ký/`Method` cụ thể |
| Đối tượng đích hiện thực thuộc lớp nào? | `EmailNotifier` | Đối tượng tại runtime |
| Thân phương thức nào thực sự chạy? | `EmailNotifier.send` | Quy tắc dispatch của Java và kiểu lời gọi |

**Không nên gom bốn câu trả lời thành một khái niệm “kiểu của phương thức”.** Khi đọc quy tắc pointcut, phải xác định chính xác trường thông tin nào được nó so khớp.

## 2. Tại sao pointcut cần phân biệt kiểu tĩnh và kiểu động?

Giả sử:

~~~java
Notifier first = new EmailNotifier();
Notifier second = new SmsNotifier();
~~~

Đây là mảnh minh họa, giả sử cả hai lớp đều triển khai `Notifier`.

Hai biến có **cùng kiểu tĩnh** nhưng các đối tượng trỏ tới có **kiểu runtime khác nhau**. Một quy tắc chọn các lời gọi theo hợp đồng `Notifier` có thể quan tâm tới cả hai. Một quy tắc chỉ theo dõi các đối tượng đích là `EmailNotifier` cần loại trường hợp thứ hai.

Ta có thể phát biểu hai điều kiện bằng **giả mã AOP trung lập với công cụ**:

~~~text
staticSelector:
    chọn join point có chữ ký tĩnh phù hợp hợp đồng Notifier

runtimeSelector:
    chọn join point nếu đối tượng đích tại thời điểm chạy
    là instance của EmailNotifier

advice:
    thực hiện ghi log, đo thời gian hoặc hành vi khác
    tại điểm được chọn
~~~

`staticSelector` và `runtimeSelector` chỉ minh họa hai ý nghĩa lựa chọn; chúng **không phải** cú pháp pointcut của một thư viện cụ thể. Cơ chế AOP khác nhau có tập join point, cú pháp mẫu kiểu và khả năng kiểm tra runtime khác nhau.

Quy tắc dựa trên chữ ký có thể được phân tích hoặc loại trừ từ sớm. Quy tắc phụ thuộc đối tượng runtime có thể phải kiểm tra mỗi lần gọi. Điều này ảnh hưởng hiệu năng, nhưng ưu tiên đầu tiên vẫn phải là **ngữ nghĩa chọn đúng**.

## 3. Điểm gọi và điểm thực thi có thể dùng thông tin chữ ký khác nhau

Một phương thức có thể được **gọi** qua tham chiếu kiểu interface rồi **thực thi** bằng phần thân override trong lớp cụ thể:

~~~java
interface Notifier {
    void send(String message);
}

class EmailNotifier implements Notifier {
    @Override
    public void send(String message) {
        System.out.println("EMAIL " + message);
    }
}

Notifier viaInterface = new EmailNotifier();
EmailNotifier viaConcrete = new EmailNotifier();

viaInterface.send("A");
viaConcrete.send("B");
~~~

Đây là một đoạn minh họa đủ khai báo liên quan, nhưng không có phương thức `main` độc lập.

Cả hai lời gọi cuối đều thực thi thân `EmailNotifier.send`. Tuy nhiên:

| Thuộc tính | `viaInterface.send("A")` | `viaConcrete.send("B")` |
| --- | --- | --- |
| Kiểu của biến bên gọi sử dụng | `Notifier` | `EmailNotifier` |
| Kiểu runtime của đối tượng đích | `EmailNotifier` | `EmailNotifier` |
| Thân phương thức thực thi | `EmailNotifier.send` | `EmailNotifier.send` |
| Chữ ký tĩnh tại điểm call | Có thể mang kiểu đủ điều kiện `Notifier` | Có thể mang kiểu đủ điều kiện `EmailNotifier` |
| Điều kiện runtime “target là EmailNotifier” | Đúng | Đúng |

**Method-call join point** nằm tại vị trí bên gọi phát sinh lời gọi. **Method-execution join point** nằm tại việc thực thi phần thân phương thức. Đó là hai loại điểm khác nhau, và không phải mọi cơ chế AOP đều hỗ trợ cả hai.

~~~text
Bên gọi có biến kiểu Notifier
              |
              v
         CALL join point
              |
              v
       Dispatch đa hình
              |
              v
   EXECUTION của EmailNotifier.send
              |
              v
          Thân send
~~~

Trong một mô hình AOP hỗ trợ cả call và execution với chữ ký tĩnh, kiểu đủ điều kiện ở chữ ký **call** có thể xuất phát từ kiểu dùng để truy cập phương thức ở mã bên gọi, còn chữ ký **execution** có thể gắn với kiểu khai báo phần thân đang thực thi. Đây là một ví dụ cho thấy “hai lời gọi cùng chạy một thân phương thức” **không bảo đảm** cùng được chọn bởi mọi pointcut dựa trên chữ ký call.

Ngược lại, nếu pointcut kiểm tra *đối tượng đích runtime* là `EmailNotifier`, cả hai lời gọi có thể thỏa, **miễn là join point tương ứng nằm trong khả năng và phạm vi can thiệp của cơ chế**.

## 4. Chọn theo chữ ký, nơi khai báo hay đối tượng thực tế

Khi mô tả “chọn phương thức của lớp `EmailNotifier`”, người viết cần làm rõ một trong các yêu cầu sau:

1. **Theo kiểu xuất hiện trong chữ ký tại điểm gọi:** bên gọi truy cập phương thức thông qua một kiểu tĩnh được chỉ định.
2. **Theo phương thức đang thực thi:** chỉ chọn phần thân được khai báo/thực thi ở một lớp nhất định.
3. **Theo đối tượng đích runtime:** chọn lời gọi nếu đối tượng thật là `EmailNotifier` hoặc một lớp con.
4. **Theo kiểu thực tế của tham số:** chọn nếu một đối số thực tế là `EmailNotifier`, dù nó chỉ là tham số chứ không phải đối tượng nhận lời gọi.
5. **Theo đối tượng bên gọi:** xét đối tượng đang thực thi mã ở nơi phát sinh lời gọi, có thể khác đối tượng đích.

Những điều kiện này không tương đương. Trong cách diễn đạt AOP, các thuật ngữ `this`, `target` và `args` thường chỉ những vai trò ngữ cảnh khác nhau. Ở điểm **call**, đối tượng đang thực thi mã tại bên gọi và đối tượng **nhận lời gọi** có thể là hai đối tượng. Ở một điểm thực thi thuộc ngữ cảnh `static`, có thể không có `this` là instance. Một số mô hình pointcut cho phép ràng buộc những giá trị này để advice sử dụng, nhưng việc truy xuất dữ liệu không tự làm thay đổi điểm được chọn.

**Quy tắc đọc pointcut:** trước khi kết luận, xác định **loại join point**, **loại kiểu đang xét**, **đối tượng tham gia** và **khả năng triển khai thực tế**.

## 5. Ví dụ Java 17 chạy độc lập: declared Method và runtime target

Chương trình sau sử dụng **Java dynamic proxy** để mô hình hóa hai điều kiện so khớp. Đây là ví dụ về **tư duy AOP** ở điểm lời gọi đi qua giao diện, không phải một bộ công cụ AOP hỗ trợ mọi loại join point.

Tạo file `DeclaredRuntimePointcutDemo.java`:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class DeclaredRuntimePointcutDemo {
    interface Handler {
        String handle(String value);
    }

    static class BasicHandler implements Handler {
        @Override
        public String handle(String value) {
            System.out.println("BUSINESS BasicHandler");
            return "basic:" + value;
        }
    }

    static class FastHandler extends BasicHandler {
        @Override
        public String handle(String value) {
            System.out.println("BUSINESS FastHandler");
            return "fast:" + value;
        }
    }

    // Ví dụ điều kiện trên Method do proxy cung cấp.
    static boolean declaredMatch(Method method) {
        return method.getDeclaringClass() == Handler.class
                && method.getName().equals("handle");
    }

    // Ví dụ điều kiện dựa trên đối tượng runtime.
    static boolean runtimeMatch(Handler target) {
        return target instanceof FastHandler;
    }

    static Handler wrap(Handler target) {
        return (Handler) Proxy.newProxyInstance(
                Handler.class.getClassLoader(),
                new Class<?>[] { Handler.class },
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(target, args);
                    }
                    boolean declared = declaredMatch(method);
                    boolean runtime = runtimeMatch(target);
                    System.out.println("MATCH declared=" + declared
                            + " runtime=" + runtime);

                    if (declared && runtime) {
                        System.out.println("ADVICE "
                                + target.getClass().getSimpleName());
                    }

                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException ex) {
                        throw ex.getCause();
                    }
                });
    }

    public static void main(String[] args) throws Exception {
        Handler fast = new FastHandler();

        Method contract = Handler.class.getMethod("handle", String.class);
        Method actualMethod =
                FastHandler.class.getMethod("handle", String.class);

        System.out.println("METHOD declared="
                + contract.getDeclaringClass().getSimpleName());
        System.out.println("TARGET runtime="
                + fast.getClass().getSimpleName());
        System.out.println("METHOD implementation="
                + actualMethod.getDeclaringClass().getSimpleName());

        Handler fastProxy = wrap(fast);
        Handler basicProxy = wrap(new BasicHandler());

        System.out.println("RESULT " + fastProxy.handle("hi"));
        System.out.println("RESULT " + basicProxy.handle("hi"));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac DeclaredRuntimePointcutDemo.java
java DeclaredRuntimePointcutDemo
~~~

Stdout:

~~~text
METHOD declared=Handler
TARGET runtime=FastHandler
METHOD implementation=FastHandler
MATCH declared=true runtime=true
ADVICE FastHandler
BUSINESS FastHandler
RESULT fast:hi
MATCH declared=true runtime=false
BUSINESS BasicHandler
RESULT basic:hi
~~~

Phân tích:

- `Handler.class.getMethod(...)` trả về `Method` của giao diện `Handler`; declaring class là `Handler`.
- `fast.getClass()` trả về `FastHandler`, lớp runtime của đối tượng.
- `FastHandler.class.getMethod(...)` tìm bản override khai báo tại `FastHandler`; declaring class là `FastHandler`.
- Hai lần gọi qua proxy đều chuyển `Method` của giao diện vào bộ xử lý, nên `declaredMatch` đúng cả hai.
- `runtimeMatch` đúng với `FastHandler` và sai với `BasicHandler`. Vì vậy chỉ một lần có dòng `ADVICE`.
- Gọi `method.invoke(target, args)` chuyển tiếp đến phương thức phù hợp trên đối tượng thật; ngoại lệ do reflection bọc trong `InvocationTargetException` được tháo để không thay đổi lỗi nghiệp vụ.

Hai hàm `declaredMatch` và `runtimeMatch` **chỉ là điều kiện minh họa do chương trình tự viết**. `Method` trong `InvocationHandler` của proxy là thông tin của cơ chế proxy này; đừng suy rằng mọi mô hình **call join point** đều dùng chính `Method` ấy để quyết định chữ ký tĩnh.

Nếu gọi trực tiếp `new FastHandler().handle("hi")`, phương thức nghiệp vụ vẫn chạy, nhưng **proxy và advice trong ví dụ này không chạy**. Việc đối tượng có kiểu runtime phù hợp không tự bảo đảm đường gọi nằm trong phạm vi đã được weave.

## 6. Kế thừa, override và phần thân thực thi

Xét:

~~~java
class Parent {
    void execute() {
        System.out.println("Parent");
    }
}

class Child extends Parent {
    @Override
    void execute() {
        System.out.println("Child");
    }
}

Parent ref = new Child();
ref.execute();
~~~

Đây là mã minh họa, chưa phải chương trình có `main`. Trong dispatch ảo thông thường, `Child` được in ra. Kiểu khai báo của `ref` vẫn là `Parent`, còn đối tượng runtime là `Child`.

Nếu `Child` không override `execute()`, phần thân được chạy có thể vẫn **được khai báo tại Parent**, trong khi đối tượng runtime là `Child`. Một pointcut xét declaring class của phần thân và một pointcut xét lớp runtime của đối tượng vì thế có thể khác kết quả.

Cần phân biệt thêm:

- **Override:** phương thức lớp con thay cách thực hiện, dispatch động chọn phương thức khi phù hợp.
- **Overload:** nhiều phương thức cùng tên nhưng khác danh sách tham số. Trình biên dịch chọn overload dựa trên các kiểu tĩnh và quy tắc phân giải lời gọi; không đơn giản chọn theo lớp runtime của đối số.
- **Static method:** không có dispatch ảo theo đối tượng như phương thức instance có thể override.
- **Private method và lời gọi `super`:** có quy tắc phân giải riêng, không nên suy rộng ví dụ override.
- **Constructor:** có join point và ngữ nghĩa riêng trong các mô hình hỗ trợ, không coi là một method-call thông thường.

Ví dụ overload:

~~~java
void send(CharSequence value) { /* trường hợp A */ }
void send(String value)       { /* trường hợp B */ }
~~~

Một pointcut chỉ lọc tên `send` có thể chọn quá rộng; điểm gọi sử dụng chữ ký `CharSequence` và chữ ký `String` có thể thuộc các tập điểm khác nhau.

## 7. Bằng đúng lớp hay bao gồm lớp con?

Một chính sách chọn “đích là `EmailNotifier`” có thể mang hai ý nghĩa:

| Mong muốn | Biểu thức kiểm tra lớp Java | Có chọn lớp con không? |
| --- | --- | --- |
| Chính xác lớp `EmailNotifier` | `target.getClass() == EmailNotifier.class` | Không |
| `EmailNotifier` hoặc bất kỳ lớp con | `target instanceof EmailNotifier` | Có |
| Mọi đối tượng triển khai `Notifier` | `target instanceof Notifier` | Có nếu thuộc hợp đồng đó |

Các biểu thức là ví dụ kiểm tra trên biến `target` khác null, giả sử các kiểu đã tồn tại. Mẫu kiểu trong ngôn ngữ pointcut có thể có cú pháp và quy tắc bao gồm lớp con riêng; **không mặc định** ký hiệu giống nhau giữa các công cụ.

Nếu chính sách là “mọi triển khai của một hợp đồng đều phải được kiểm tra quyền”, chọn theo quan hệ interface/lớp cha thường thể hiện ý định rõ hơn việc liệt kê tên từng lớp triển khai. Tuy vậy vẫn phải bảo đảm mọi đường gọi cần bảo vệ đi qua điểm can thiệp.

## 8. Các tình huống nâng cao

### 8.1 Proxy, wrapper và đối tượng đích

Một chương trình có thể tạo đối tượng proxy có runtime class khác đối tượng nghiệp vụ bên trong. Nếu truy vấn `getClass()` trên **proxy** thì kết quả khác truy vấn trên **đối tượng được proxy chuyển tiếp tới**. Trước khi so khớp kiểu runtime, cần hỏi rõ “đang xét đối tượng nào: proxy, caller, receiver hay target thật?”.

Đó cũng là lý do không nên suy `Method.getDeclaringClass()` của phương thức qua giao diện sẽ là lớp của đối tượng triển khai. Một bộ chặn lời gọi có thể thấy phương thức khai báo ở interface, trong khi thân phương thức chạy trên lớp cụ thể.

### 8.2 Generic và xóa kiểu

Trong Java, `List<String>` và `List<Integer>` có cùng kiểu thô runtime sau **type erasure** đối với các đối tượng `ArrayList` tương ứng. Thông tin generic trong khai báo không tạo hai runtime class `ArrayList<String>` và `ArrayList<Integer>` khác nhau.

Một cơ chế chọn điểm dựa trên chữ ký nguồn hoặc metadata generic có thể có thông tin khác cơ chế chỉ so khớp bytecode đã xóa kiểu. Khi các lớp override phương thức generic, trình biên dịch đôi khi tạo **bridge method** để giữ dispatch sau erasure. Những phương thức synthetic/bridge có thể làm việc liệt kê hoặc instrument phương thức khác mong đợi; cần kiểm tra cụ thể và tránh đếm trùng.

### 8.3 Annotation trên interface, lớp cha và override

Metadata annotation không tự động có cùng ý nghĩa ở mọi mức của cây kế thừa. Annotation đặt ở phương thức của interface không bảo đảm một phương thức override có cùng annotation trực tiếp. Trong Java, `@Inherited` chỉ hỗ trợ quy tắc kế thừa annotation ở **kiểu lớp** trong phạm vi được định nghĩa; nó không tự khiến annotation của phương thức được kế thừa trên mọi override.

Vì thế, pointcut so khớp annotation tại khai báo gốc có thể khác so khớp tại phương thức thực tế hoặc loại runtime của đối tượng. Phải xác định rõ nguồn metadata.

### 8.4 Tham số null và kiểm tra kiểu động

Đối số `null` không có runtime class; gọi `getClass()` trên `null` gây `NullPointerException`. Tuy nhiên, phương thức vẫn có **kiểu tham số được khai báo**. Cách match một tham số null khi sử dụng biểu thức kiểu động là quy tắc của ngôn ngữ pointcut cụ thể, không thể thay bằng một kết luận chung “null luôn khớp” hay “null không bao giờ khớp”.

### 8.5 Tối ưu pointcut và chi phí runtime

Điều kiện chữ ký có thể được tính hoặc loại trừ ở thời điểm biết cấu trúc chương trình; điều kiện kiểu runtime cần đọc đối tượng thực tế và có thể đánh giá trên từng lần gọi. Không nên đưa phép kiểm tra có tác dụng phụ hoặc quá tốn thời gian vào điểm nóng. Sự đúng đắn của tập join point được chọn vẫn là ưu tiên; chỉ tối ưu sau khi đo được chi phí.

## 9. Các lỗi thiết kế dễ gặp

| Lỗi | Hậu quả | Cách khắc phục |
| --- | --- | --- |
| Coi kiểu biến là kiểu runtime | Bỏ sót các lớp triển khai khác nhau | Phân biệt static reference type và dynamic target type |
| Coi `getDeclaringClass()` luôn trả lớp runtime | Chọn sai khi quan sát `Method` của interface hoặc lớp cha | Xác định đối tượng `Method` đang được dùng |
| Đồng nhất call với execution | Hiểu nhầm chữ ký và nơi can thiệp | Xác minh loại join point |
| Chọn đúng kiểu nhưng bỏ qua phạm vi proxy/weaving | Một số lời gọi không chạy advice | Kiểm thử toàn bộ đường gọi |
| Dùng so sánh lớp chính xác trong khi muốn gồm lớp con | Bỏ sót subclass | Dùng quan hệ subtype/instance phù hợp |
| Coi kiểu generic lúc biên dịch luôn tồn tại ở runtime | Pointcut không khớp như mong đợi | Kiểm tra erasure và bridge methods |
| Lẫn runtime target với runtime argument | Quy tắc chọn đối tượng khác mục tiêu | Chỉ rõ target, this và args |
| Giả định annotation trên hợp đồng tự xuất hiện ở override | Bỏ sót advice hoặc chọn lệch | Kiểm tra nơi khai báo annotation và quy tắc tìm kiếm |
| Chỉ thử trường hợp thành công của một lớp | Không biết phạm vi thật của pointcut | Kiểm thử cả nhánh khớp và không khớp |

## 10. Kiểm thử bằng ma trận kiểu

Giả sử có interface `Notifier`, hai triển khai `EmailNotifier`, `SmsNotifier`, và lớp con `FastEmailNotifier`:

1. Gọi qua **biến `Notifier`** tới `EmailNotifier`: đối chiếu chữ ký call tĩnh và kiểu đích runtime.
2. Gọi qua **biến `EmailNotifier`** tới cùng một đối tượng: kiểu tham chiếu khác, đích runtime không đổi.
3. Gọi qua **biến `Notifier`** tới `SmsNotifier`: chữ ký phía interface có thể giữ nguyên, đích runtime đổi.
4. Gọi tới `FastEmailNotifier`: thử điều kiện “đúng bằng EmailNotifier” và “là instance của EmailNotifier”.
5. Gọi phương thức **được kế thừa nhưng không override**: declaring class của phần thân có thể vẫn là lớp cha.
6. Gọi phương thức **qua proxy** và **trực tiếp**: phân biệt đúng điều kiện kiểu với khả năng advice thực sự được kích hoạt.
7. Thử **overload**, generic/bridge, annotation và các tham số null trong những điểm mà hệ thống thực tế có hỗ trợ.

Ở mỗi trường hợp, ghi lại **kiểu tĩnh**, **loại join point**, **declaring type của chữ ký liên quan**, **kiểu đối tượng runtime**, **kết quả match** và **advice có thực sự chạy hay không**. Một dòng log thành công tại một lời gọi không chứng minh pointcut đã bao phủ mọi đường gọi quan trọng.

## 11. Câu hỏi tự luyện

Xét:

~~~java
Payment p = new CardPayment();
p.pay();
~~~

Giả sử `Payment` là giao diện, `CardPayment` triển khai và override `pay()`.

**Kiểu khai báo của `p`?** — `Payment`.

**Kiểu runtime của đối tượng?** — `CardPayment`.

**Có thể mặc định điểm gọi mang chữ ký của `CardPayment` chỉ vì đối tượng là `CardPayment`?** — Không. Phải xem loại join point và quy tắc hình thành chữ ký.

**Một điều kiện chọn runtime target `instanceof CardPayment` có thể khớp khi gọi qua biến `Payment`?** — Có, nếu mô hình hỗ trợ kiểu chọn đó và đường gọi nằm trong phạm vi can thiệp.

**Nếu một proxy chỉ bọc vài đường gọi, điều kiện kiểu đúng có bảo đảm advice chạy với lời gọi trực tiếp không?** — Không.

**Nếu `PremiumCardPayment extends CardPayment`, phép `getClass()==CardPayment.class` có chọn đối tượng PremiumCardPayment không?** — Không; phép `instanceof CardPayment` có.

**Nếu muốn áp dụng kiểm tra chung cho mọi hiện thực của `Payment`, cần nghĩ theo loại thông tin nào?** — Theo hợp đồng chung và tập điểm cần bảo vệ, kết hợp kiểm chứng mọi đường gọi, thay vì phụ thuộc vào một tên lớp triển khai.

## Tài liệu tham khảo

- [AspectJ Programming Guide – Signatures and Matching](https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html): mô hình ví dụ về sự khác biệt giữa chữ ký method call và method execution.
- [AspectJ Language Semantics – Pointcuts](https://eclipse.dev/aspectj/doc/latest/progguide/semantics.html): ví dụ cơ chế AOP phân biệt điều kiện chữ ký và điều kiện kiểu runtime.
- [Java SE 17 – Method.getDeclaringClass()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html#getDeclaringClass()): quy tắc Reflection về kiểu khai báo phương thức.

**Ghi nhớ:** Khi đọc hoặc viết pointcut, hãy hỏi **“kiểu tĩnh của khai báo/chữ ký nào?”** và **“kiểu runtime của đối tượng nào?”**. Phân biệt chúng cùng với loại join point, quan hệ subtype và phạm vi weaving giúp chọn đúng điểm can thiệp trong chương trình đa hình.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-basics-005">Kế thừa, ghi đè phương thức và ảnh hưởng của đa hình</a>

<details>
<summary>Xem chi tiết</summary>

# Kế thừa, ghi đè phương thức và ảnh hưởng của đa hình đối với pointcut AOP

## 1. Vì sao pointcut trở nên khó dự đoán khi có kế thừa?

Trong **Aspect-Oriented Programming (AOP)**, pointcut là quy tắc chọn những **join point** mà advice sẽ can thiệp. Với một lớp độc lập, người học thường bắt đầu từ các ví dụ chọn thực thi phương thức theo tên, kiểu tham số hoặc lớp. Nhưng trong hệ thống hướng đối tượng, người gọi có thể biết một **interface** hoặc **lớp cha**, còn đối tượng thực là một **lớp con**. Lớp con có thể kế thừa nguyên một phương thức, ghi đè nó hoặc bổ sung các overload.

Từ đó xuất hiện những câu hỏi mà tên phương thức không đủ trả lời:

- Caller viết **Base ref = new Child()**, rồi gọi **ref.work()**: biểu thức pointcut nên xét **Base**, **Child** hay cả hai?
- **Child** không override **work()**: thân phương thức nào thực thi và phương thức được khai báo ở lớp nào?
- **Child** override **work()**: join point nào của chương trình đã đổi, và điểm gọi ở caller có thay đổi không?
- Một pointcut chọn **call** có tương đương pointcut chọn **execution** trong mọi tình huống đa hình không?
- Một annotation trên phương thức của lớp cha có tự áp dụng cho phương thức override ở lớp con không?
- Nếu hệ thống dùng **proxy**, mọi lần gọi qua interface và các lời gọi nội bộ có đều đi qua bộ chặn không?

Bài này dùng **quy tắc Java 17+** để xác định điều gì thực sự xảy ra ở runtime, rồi từ đó thiết kế pointcut và kiểm tra advice. Các mô hình AOP khác nhau hỗ trợ những loại join point khác nhau; cú pháp pointcut của một công cụ chỉ là cách biểu diễn cụ thể cho những khái niệm chung ấy.

**Nguyên tắc trung tâm:** phân biệt *kiểu tĩnh và vị trí lời gọi*, *kiểu thực của đối tượng*, *phương thức thực được chọn để chạy* và *cơ chế AOP đang can thiệp vào đâu*. Bốn thông tin có liên quan, nhưng không luôn giống nhau.

## 2. Những khái niệm Java phải hiểu trước khi viết pointcut

### 2.1. Kế thừa

Một lớp con có thể nhận được những phương thức instance có thể kế thừa từ lớp cha. Ví dụ:

~~~java
class Base {
    void inherited() {
        System.out.println("Base.inherited");
    }
}

class Child extends Base {
    // Không khai báo lại inherited()
}
~~~

Đây là **đoạn minh họa**, không phải chương trình độc lập. Khi một **Child** gọi **inherited()**, thân khai báo tại **Base** thực thi. Việc đối tượng là **Child** không tự sinh thêm một bản thân phương thức tương ứng trong **Child**.

Điều đó quan trọng cho pointcut: nếu chọn theo **lớp chứa khai báo phương thức/thân mã**, điều kiện có thể khớp **Base**; nếu chọn theo **kiểu runtime của receiver**, điều kiện có thể khớp **Child**. Hai kiểu chọn khác nhau.

### 2.2. Ghi đè (overriding)

Một phương thức instance ở lớp con có thể **override** phương thức instance tương ứng được kế thừa từ lớp cha nếu đáp ứng các quy tắc của Java.

~~~java
class Base {
    void process() {
        System.out.println("Base.process");
    }
}

class Child extends Base {
    @Override
    void process() {
        System.out.println("Child.process");
    }
}
~~~

Khi gọi bình thường qua một reference kiểu **Base** nhưng đối tượng thực là **Child**, JVM sử dụng cơ chế **dynamic dispatch** để chọn override thích hợp là **Child.process()**.

### 2.3. Đa hình (polymorphism)

**Kiểu của biến** tại compile time và **kiểu của đối tượng thực** tại runtime có thể khác nhau:

~~~java
Base service = new Child();
service.process();  // Thực thi Child.process()
~~~

Khi giải quyết lời gọi, Java dùng thông tin tĩnh để xác định một phương thức hợp lệ và dạng lời gọi; với lời gọi virtual/interface thích hợp, Java sử dụng lớp thực của đối tượng để chọn triển khai tại runtime.

Bởi vậy, **kiểu tĩnh ảnh hưởng việc biên dịch lời gọi**, còn **kiểu runtime ảnh hưởng phương thức override nào thực thi**.

### 2.4. Overriding khác overloading

**Overriding** thay cách thực hiện cùng một hợp đồng instance ở lớp con. **Overloading** thêm phương thức cùng tên nhưng danh sách tham số khác nhau; việc chọn overload chủ yếu được xác định bằng kiểu tại compile time.

Ví dụ **Base.select(Object)** và **Child.select(String)** là hai overload khác nhau, không phải Child override Base.select(Object). Với biến kiểu **Base**, lời gọi **select("hello")** vẫn có thể chọn **Base.select(Object)** vì compiler chỉ nhìn những overload khả dụng qua **Base**.

### 2.5. Method hiding khác overriding

Phương thức **static** của lớp con có cùng chữ ký với phương thức static của lớp cha là **method hiding** (che khuất), không phải dynamic overriding.

~~~java
class Base {
    static void kind() {
        System.out.println("Base");
    }
}

class Child extends Base {
    static void kind() {
        System.out.println("Child");
    }
}
~~~

**Base.kind()** chạy mã của Base; **Child.kind()** chạy mã của Child. Gọi static qua biến instance tuy Java có thể cho phép ở những ngữ cảnh nhất định nhưng dễ gây nhầm lẫn; nên gọi qua tên lớp. **Pointcut với static call không nên dựa vào suy luận dynamic dispatch.**

## 3. Bốn góc nhìn của một lời gọi phương thức

Xét các thông tin được quan sát tại một lời gọi instance:

| Góc nhìn | Ví dụ | Vai trò khi chọn pointcut |
|---|---|---|
| **Kiểu tĩnh ở call site** | Biến được khai báo là **Base** | Ảnh hưởng cách compiler phân giải biểu thức gọi và chữ ký ở call join point |
| **Đối tượng đích tại runtime** | Instance thực là **Child** | Có thể được lọc bằng điều kiện kiểu runtime như target trong những mô hình hỗ trợ |
| **Khai báo/thân thực thi** | **Child.process()** hoặc **Base.inherited()** | Quyết định execution join point của thân phương thức thực chạy |
| **Vị trí và kiểu mã chứa join point** | Lời gọi nằm trong **Controller**, thân nằm trong **Service** | Quy tắc lexical như within/withincode có thể khớp khác nhau |

Sơ đồ:

~~~text
Caller có reference kiểu Base
             │
             │ gọi ref.process()
             ▼
        CALL join point
        (chữ ký/vị trí gọi)
             │
             ▼
      Java dynamic dispatch
             │
             └──► object thực: Child
                         │
                         ▼
                 Child.process()
                 EXECUTION join point
                         │
                         ▼
                  Advice (nếu khớp)
~~~

Sơ đồ mô tả **một lời gọi instance được dispatch động** và phân biệt những đối tượng quan sát. Không phải mọi công cụ AOP đều phát lộ cả call lẫn execution join point; có công cụ chỉ chặn các lời gọi đi qua đối tượng đại diện.

## 4. Call pointcut và execution pointcut không đồng nghĩa

Một mô hình AOP phong phú, chẳng hạn AspectJ, phân biệt hai loại:

- **Method call join point:** điểm nơi một lời gọi phương thức được phát ra. Thông tin chữ ký ở đó có liên hệ với **kiểu tĩnh dùng để truy cập phương thức**.
- **Method execution join point:** điểm nơi thân một phương thức thực sự bắt đầu và thực thi. Thông tin chữ ký ở đó liên hệ với **kiểu khai báo phương thức đang chạy**.

Ví dụ cú pháp **minh họa theo AspectJ**, với tên kiểu được rút gọn:

~~~text
call(* Base.process(..))
execution(* Base.process(..))
~~~

Hai biểu thức trên **không thể xem là tương đương** trong mọi tình huống đa hình.

Với:

~~~java
Base ref = new Child();
ref.process();
~~~

Caller nhìn hợp đồng **Base.process()** tại biểu thức gọi, nhưng Java dispatch đến override **Child.process()**. Một **call** pointcut liên quan chữ ký Base ở phía caller có thể khớp nơi gọi, trong khi **execution** pointcut chỉ nhắm đích danh thân **Base.process()** sẽ không đồng nghĩa với “mọi lần gọi phương thức process qua reference Base”, bởi override ở Child có thể là thân thực sự chạy.

**Lưu ý về cú pháp và matching:** một bộ chọn chứa mẫu kiểu, ký hiệu subtype và quy tắc match phương thức kế thừa có thể làm tập điểm khớp rộng hơn so với so sánh chính xác tên lớp. Kết luận về một pointcut thực tế phải dựa trên ngữ nghĩa của chính bộ dệt đó và trên việc xác minh điểm khớp; không suy luận chỉ từ cách viết gần giống Java.

### 4.1. Cùng execution có thể được gọi từ nhiều caller

Khi nhiều caller với những kiểu reference khác nhau đều dispatch đến **Child.process()**, chúng có thể tạo ra **nhiều call join point khác nhau** nhưng cùng kích hoạt kiểu **execution join point** của Child.process tại mỗi lượt thực thi.

Nếu mục tiêu là “mỗi lần mã của Child.process chạy”, bắt ở biên thực thi có thể phù hợp. Nếu mục tiêu là “mọi lời gọi phát ra từ lớp ClientX”, chọn ở vị trí call và giới hạn lexical theo ClientX có thể phù hợp hơn.

### 4.2. Cùng call site có thể dispatch đến nhiều execution khác nhau

Một dòng:

~~~java
void invoke(Base service) {
    service.process();
}
~~~

Có thể được gọi với instance Base, Child hoặc một lớp con khác. Vị trí gọi trong **invoke** không đổi, nhưng thân thực thi thay đổi theo runtime receiver. Chính vì thế **chọn call site** khác với **chọn implementation đang chạy**.

### 4.3. Lời gọi super có ngữ nghĩa đặc biệt

Khi mã lớp con viết **super.process()**, Java gọi triển khai phù hợp của superclass theo quy tắc gọi super, thay vì dispatch virtual thông thường từ lớp con xuống chính override của nó.

Trong **mô hình AspectJ cụ thể**, method-call join point không bao gồm các lời gọi **super** đến phương thức instance theo cùng cách call join point thông thường. Tuy nhiên execution join point của phương thức cha vẫn có thể được quan sát nếu nó thực sự chạy. Không được áp đặt đặc điểm đó như giới hạn phổ quát cho tất cả AOP runtime, nhưng nó cho thấy tại sao việc biết loại join point là bắt buộc.

## 5. Kiểu tĩnh và kiểu runtime: chọn bằng mẫu chữ ký hay điều kiện đối tượng?

Một pointcut thường có thể biểu đạt một trong hai ý tưởng:

1. **Chọn theo chữ ký/mẫu kiểu tĩnh:** ví dụ phương thức mang tên process, khai báo trong một họ kiểu xác định.
2. **Chọn theo kiểu runtime của đối tượng:** ví dụ chỉ khi target thực sự là **Child** hoặc một subtype của nó.

Trong một số ngôn ngữ AOP, có thể kết hợp cả hai loại điều kiện.

Giả mã trung lập công cụ:

~~~text
pointcut interesting =
    method-execution named "process"
    AND runtime-target is-a Child

before interesting:
    log("a Child object runs process")
~~~

Ở dạng này, **runtime-target is-a Child** có thể đúng cả khi object Child đang chạy một phương thức được kế thừa nguyên từ Base, nếu loại join point hỗ trợ điều kiện đó và phương thức gốc được instrument.

Đó là khác biệt giữa:

- “**Thân phương thức khai báo ở Child** được thực thi”.
- “**Một đối tượng Child** đang thực hiện thao tác”.

Hai mô tả gần nhau trong hội thoại nhưng dẫn đến pointcut khác nhau.

### 5.1. this và target trong một mô hình AOP có chúng

Ví dụ các pointcut designator **this(Type)** và **target(Type)**:

- Ở **call join point**, **this** thông thường là object đang thực thi mã phía **caller**, còn **target** là object nhận lời gọi.
- Ở **execution join point** của phương thức instance, **this** và **target** thông thường đều gắn với object có phương thức đang thực thi.
- Ở ngữ cảnh static, không có một target instance thích hợp cho các điều kiện này.

Ví dụ giả mã biểu đạt ý tưởng:

~~~text
call to Base.process
AND target instance-of Child
AND this instance-of CommandHandler
~~~

Điều kiện trên hướng đến “**CommandHandler gọi phương thức mà receiver runtime là Child**”, chứ không đơn thuần “Child đang chạy một phương thức”.

**Chú ý:** ý nghĩa chính xác của this/target, tính khả dụng của object ở join point và cú pháp kết hợp phải đọc theo ngôn ngữ AOP cụ thể. Đặc biệt không được tự đồng nhất **this** với proxy, target hay caller trong mọi cơ chế interception.

### 5.2. Điều kiện subtype với ký hiệu + trong AspectJ

Trong type pattern của AspectJ, hậu tố **+** thể hiện kiểu được chỉ định cùng các subtype của nó. Một ví dụ mẫu chữ ký:

~~~text
execution(* example.Base+.process(..))
~~~

Nó truyền đạt ý định chọn execution của **process** trong phạm vi kiểu **Base và các subtype** theo quy tắc matching của AspectJ.

Đừng lẫn **Base+** với phép kiểm tra kiểu runtime của mọi object. Pattern trong **execution** xét chữ ký và phạm vi join point theo ngôn ngữ; khi một phương thức **kế thừa nhưng không override**, kiểu khai báo execution vẫn có thể là Base. Nếu yêu cầu thật sự phụ thuộc receiver runtime, hãy biểu đạt hoặc kiểm thử riêng điều kiện ấy, không suy từ dấu + rằng công cụ đã đọc mọi kiểu instance tại runtime.

Tương tự, **Base+** không tự giải quyết việc lớp cần quan sát có được đưa vào phạm vi weaving hoặc lời gọi có đi qua interceptor hay không.

## 6. Kế thừa phương thức nhưng không override

Xét:

~~~java
class Base {
    void inherited() {
        System.out.println("Base.inherited");
    }
}

class Child extends Base {
    // Không override inherited()
}
~~~

Nếu gọi **new Child().inherited()**, JVM có thể thực thi **thân Base.inherited()**. Không có thân **Child.inherited()** mới được tạo chỉ vì object thuộc Child.

Khi một pointcut giới hạn bằng lớp chứa mã thực thi, ví dụ “bên trong mã khai báo trực tiếp ở Child”, phương thức inherited có thể nằm ngoài tập chọn dù object runtime là Child.

Có ít nhất ba chính sách khác nhau:

| Yêu cầu | Cách nghĩ về điểm chọn |
|---|---|
| Quan sát mọi lần thực thi thân **Base.inherited()** | Chọn execution của phương thức được khai báo tại Base |
| Quan sát thao tác inherited khi receiver là **Child** | Chọn execution thích hợp và điều kiện runtime target thuộc Child nếu cơ chế hỗ trợ |
| Quan sát các caller trong một module gọi **inherited** | Chọn call join point theo vị trí caller, có thể thêm điều kiện target |

**Lưu ý quan trọng về static weaving:** nếu thân **Base.inherited()** không nằm trong tập lớp được dệt, pointcut kiểm tra runtime target cũng không tự làm advice xuất hiện từ hư không. Phải xác minh bytecode hoặc đường gọi mà bộ dệt thực sự kiểm soát.

### 6.1. Override làm thay đổi execution pointcut

Khi Child khai báo:

~~~java
@Override
void inherited() {
    System.out.println("Child.inherited");
}
~~~

Thân mới thuộc Child. Một pointcut trước đó chỉ gắn vào thân Base có thể không còn quan sát các lượt gọi dispatch đến override. Đây là một dạng **thay đổi mức bao phủ sau refactor** dù các caller không hề sửa lời gọi.

Khi chính sách cần áp dụng nhất quán cho cả họ class, nên khai báo rõ phạm vi subtype hoặc hợp đồng interface, và kiểm thử cả subclass có override lẫn subclass chỉ kế thừa.

## 7. Ví dụ Java 17 đầy đủ: đa hình, override, overload và proxy

Ví dụ sau tách biệt các hiện tượng có thể gây sai pointcut:

- **Base ref = new Child()** gọi override theo runtime.
- **Base.outer()** gọi **inner()**; khi object là Child, lời gọi nội bộ vẫn dispatch đến **Child.inner()**.
- **inherited()** và **final fixed()** chạy thân kế thừa từ Base.
- **select(Object)** và **select(String)** là overload; lựa chọn phụ thuộc kiểu reference/khả năng gọi ở compile time.
- **kind()** là static method hiding.
- Một **JDK dynamic proxy** chặn lời gọi qua interface **Action**. Trong **outer()**, lời gọi nội bộ **inner()** không đi lại qua proxy trong ví dụ này, dù JVM dispatch đúng đến Child.inner.

Tạo file **PolymorphismPointcutDemo.java**:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class PolymorphismPointcutDemo {
    interface Action {
        void outer();
        void inner();
    }

    static class Base implements Action {
        @Override
        public void outer() {
            System.out.println("Base.outer");
            inner();
        }

        @Override
        public void inner() {
            System.out.println("Base.inner");
        }

        public void inherited() {
            System.out.println("Base.inherited");
        }

        public final void fixed() {
            System.out.println("Base.fixed");
        }

        public static void kind() {
            System.out.println("Base.kind");
        }

        public void select(Object value) {
            System.out.println("Base.select(Object)");
        }
    }

    static class Child extends Base {
        @Override
        public void inner() {
            System.out.println("Child.inner");
        }

        public static void kind() {
            System.out.println("Child.kind");
        }

        public void select(String value) {
            System.out.println("Child.select(String)");
        }
    }

    static Action proxy(Action target) {
        return (Action) Proxy.newProxyInstance(
                Action.class.getClassLoader(),
                new Class<?>[]{Action.class},
                (proxy, method, args) -> {
                    System.out.println("intercept: " + method.getName());
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    public static void main(String[] args) {
        Base value = new Child();
        System.out.println("direct:");
        value.outer();
        value.inherited();
        value.fixed();
        value.select("text");
        ((Child) value).select("text");
        value.kind();
        Child.kind();

        System.out.println("proxy:");
        Action observed = proxy(value);
        observed.outer();
        observed.inner();
    }
}
~~~

Biên dịch và chạy trên JDK 17 trở lên:

~~~bash
javac --release 17 PolymorphismPointcutDemo.java
java PolymorphismPointcutDemo
~~~

Stdout chính xác:

~~~text
direct:
Base.outer
Child.inner
Base.inherited
Base.fixed
Base.select(Object)
Child.select(String)
Base.kind
Child.kind
proxy:
intercept: outer
Base.outer
Child.inner
intercept: inner
Child.inner
~~~

**Phân tích các dòng:**

1. **value.outer()** gọi thân **Base.outer()**, vì Child không override outer. Bên trong outer, **inner()** là instance method được Child override, nên Java dispatch đến **Child.inner()**.
2. **value.inherited()** và **value.fixed()** sử dụng thân của Base. **fixed** là final, không thể được Child override; điều đó không ngăn một cơ chế bytecode weaving hỗ trợ execution của final method quan sát thân đó.
3. **value.select("text")** chọn **Base.select(Object)** vì kiểu khai báo của value là Base và compiler không nhìn overload **Child.select(String)** qua Base.
4. Ép kiểu **(Child) value** làm overload **select(String)** trở nên khả dụng, nên gọi bản của Child.
5. **value.kind()** là lối gọi static qua biến nên vẫn chọn **Base.kind()** theo kiểu tĩnh; **Child.kind()** gọi bản Child. Trong mã thực tế nên viết **Base.kind()** để tránh hiểu nhầm.
6. **observed.outer()** đi qua proxy nên có **intercept: outer**. Sau khi handler ủy quyền cho Base.outer của object Child, lời gọi **inner()** bên trong đối tượng chạy trực tiếp và dispatch đến Child.inner nhưng không tạo thêm dòng **intercept: inner**.
7. **observed.inner()** là một lời gọi độc lập qua proxy, nên có dòng **intercept: inner** trước **Child.inner**.

**Ranh giới của ví dụ:** đây là Java thuần với JDK dynamic proxy, **không phải bộ dệt bytecode** và không phải khẳng định rằng mọi công cụ AOP chỉ có thể quan sát hai phương thức thuộc Action. Ví dụ chủ yếu kiểm chứng dispatch Java và sự phụ thuộc của interception vào đường đi thực tế.

## 8. Kế thừa interface và implementation

Interface có thể định nghĩa hợp đồng mà nhiều class implement:

~~~java
interface Payment {
    void pay();
}

class CardPayment implements Payment {
    @Override
    public void pay() {
        // Thực hiện thanh toán thẻ
    }
}

class BankPayment implements Payment {
    @Override
    public void pay() {
        // Thực hiện thanh toán ngân hàng
    }
}
~~~

Đây là đoạn minh họa, các thân method hiện chưa thực hiện nghiệp vụ thanh toán. Khi caller khai báo **Payment payment**, runtime có thể dispatch đến **CardPayment.pay** hoặc **BankPayment.pay**.

Có thể có ba cách đặt chính sách:

- **Theo hợp đồng:** mọi triển khai của Payment phải được ghi nhận.
- **Theo nhóm implementation:** chỉ CardPayment hoặc chỉ những class thuộc package cụ thể.
- **Theo ngữ cảnh runtime:** chỉ những object/argument thỏa điều kiện tại lần gọi cụ thể.

Nếu dùng **execution pointcut** chỉ khớp một implementation, việc bổ sung class implement khác có thể không tự được bao phủ. Nếu dùng một **call pointcut** tại các vị trí gọi qua interface, bạn có thể quan sát lời gọi xuất phát từ những caller đã được weave, nhưng các caller khác hoặc đường nội bộ vẫn phải được đánh giá riêng.

**Điểm cần kiểm chứng:** có công cụ nhận diện pointcut ở phương thức interface dựa trên metadata rồi suy ra implementation; có công cụ dựa theo bytecode/thân phương thức; có cơ chế proxy chỉ thấy method khai báo trên interface. Không nên mặc định rằng định nghĩa pointcut bằng tên interface sẽ tự động bao phủ mọi implementation trên mọi engine.

## 9. Khi lớp con ghi đè: annotation được kế thừa đến đâu?

Một trường hợp hay gây sai lệch là đặt annotation đánh dấu policy lên phương thức ở Base, sau đó một subclass override nhưng không sao chép annotation.

Trong Java, **method annotation không tự động được kế thừa như class annotation có @Inherited**. **@Inherited** có cơ chế dành cho annotation trên class (theo quy tắc Java); nó không tự khiến phương thức override nhận annotation của phương thức cha.

Ví dụ Java 17 chạy được:

Tạo file **OverrideAnnotationDemo.java**:

~~~java
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

public class OverrideAnnotationDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Observed {}

    static class Base {
        @Observed
        public void process() {}
    }

    static class Child extends Base {
        @Override
        public void process() {}
    }

    static class Inheriting extends Base {}

    public static void main(String[] args) throws NoSuchMethodException {
        var baseMethod = Base.class.getDeclaredMethod("process");
        var childMethod = Child.class.getDeclaredMethod("process");
        var inheritedMethod = Inheriting.class.getMethod("process");

        System.out.println("base declared annotated=" +
                baseMethod.isAnnotationPresent(Observed.class));
        System.out.println("child declared annotated=" +
                childMethod.isAnnotationPresent(Observed.class));
        System.out.println("inherited method owner=" +
                inheritedMethod.getDeclaringClass().getSimpleName());
        System.out.println("inherited method annotated=" +
                inheritedMethod.isAnnotationPresent(Observed.class));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 OverrideAnnotationDemo.java
java OverrideAnnotationDemo
~~~

Stdout chính xác:

~~~text
base declared annotated=true
child declared annotated=false
inherited method owner=Base
inherited method annotated=true
~~~

**Ý nghĩa đối với pointcut:**

- Nếu pointcut dựa trên **annotation trực tiếp ở phương thức đang được thực thi**, override **Child.process()** có thể không khớp khi Child không có annotation.
- Nếu một framework/công cụ chủ động đi ngược lên interface/superclass để tìm metadata chính sách, kết quả có thể khác. Đây là **quy tắc riêng của công cụ**, không phải do Java tự động gắn method annotation vào override.
- **Inheriting** không override; lời gọi **getMethod("process")** tìm được phương thức khai báo tại **Base**, và annotation vẫn nằm trên chính phương thức đó.

Khi chính sách dựa vào annotation, cần xác định nó có ý nghĩa **trên declaration**, **trên hợp đồng interface**, **trên implementation** hay **trên toàn bộ override chain**. Không nên dùng từ “kế thừa annotation” mà không xác định cơ chế đọc metadata.

## 10. Chọn theo nơi khai báo, nơi thực thi hay nơi caller đặt lệnh?

Trong mô hình pointcut giàu khả năng như AspectJ, một số bộ chọn lexical thường được diễn giải như sau:

- **within(TypePattern):** chọn join point có mã nằm **bên trong kiểu** phù hợp.
- **withincode(MethodPattern):** chọn join point nằm trong **thân phương thức/constructor chứa đoạn mã**, theo ngữ nghĩa cụ thể.
- **execution(MethodPattern):** chọn join point nơi thân method khớp được thực thi.
- **call(MethodPattern):** chọn join point tại vị trí thực hiện lời gọi khớp pattern.

Ví dụ ý tưởng:

~~~text
call(* *.process(..)) && within(example.client..*)
~~~

Biểu thức loại này chọn lời gọi đến process trong vùng caller thuộc **example.client** (với quy tắc wildcards của ngôn ngữ đó). Nó **không** tự nghĩa là thân **process()** phải được khai báo bên trong example.client.

Ngược lại:

~~~text
execution(* example.impl..*.process(..))
~~~

nhằm chọn execution phương thức ở vùng implementation được mô tả bởi chữ ký. Khi Child chỉ kế thừa thân Base, một điều kiện lexical yêu cầu thân mã nằm ở Child không tự đúng.

### 10.1. Cẩn thận khi kết hợp call và within

Một method call vào **Child.process()** có thể phát ra từ một method nằm trong **BaseCaller**, **ChildCaller** hoặc bất kỳ nơi nào khác. Tập caller hoàn toàn độc lập với việc phương thức đích được override.

Khi cần chặn “mọi caller của một API thuộc phạm vi X”, quyết định áp dụng theo call site thường mang ý nghĩa rõ. Khi cần chặn “mọi lần thực thi của một thân nghiệp vụ bất kể caller”, execution thường diễn tả mục đích tốt hơn nếu cơ chế hỗ trợ.

### 10.2. Một phương thức có thể vừa khớp mẫu kiểu vừa không khớp within

Ví dụ receiver runtime là Child, nhưng mã đang chạy là **Base.inherited()**. Điều kiện **target(Child)** có thể đúng ở execution của inherited theo mô hình hỗ trợ runtime target; điều kiện **within(Child)** không đương nhiên đúng, bởi thân method nằm ở Base.

Kết hợp quá nhiều điều kiện AND khi chưa hiểu từng điều kiện có thể làm pointcut vô tình thành rỗng. Thử từng điều kiện độc lập và ghép dần sẽ dễ xác minh.

## 11. Trường hợp final, private, static và super

Kế thừa và đa hình ở Java có những giới hạn khiến thuật ngữ “ghi đè” không áp dụng được cho mọi method:

| Loại phương thức/lời gọi | Ngữ nghĩa Java | Hệ quả cho pointcut |
|---|---|---|
| **Instance method có thể override** | Dynamic dispatch đến triển khai phù hợp theo runtime type | Execution có thể nằm ở subclass; call site có thể chỉ biết superclass/interface |
| **Final instance method** | Không thể bị subclass override | Vẫn có thân method để thực thi; weaving tại bytecode có thể quan sát nếu công cụ hỗ trợ, nhưng proxy tạo subclass không thể override final method |
| **Private method** | Không được override như virtual method kế thừa | Không dựa vào dynamic overriding; proxy interface không tự chặn private method |
| **Static method** | Method hiding, không có dynamic overriding | Chọn theo chữ ký static, không giả định target runtime instance |
| **Lời gọi super.method()** | Sử dụng quy tắc super invocation đặc biệt | Call join point có thể khác hoặc không được hỗ trợ giống lời gọi virtual; execution của thân cha vẫn có thể được quan sát |
| **Constructor** | Không được kế thừa/override như instance method | Cần join point constructor riêng khi muốn chặn khởi tạo |

**Final không đồng nghĩa tuyệt đối “mọi AOP đều không thể áp dụng”.** Giới hạn áp dụng khác nhau giữa tạo subclass proxy và bytecode weaving có khả năng can thiệp tại execution. Tương tự, private method không xuất hiện trong giao diện proxy, nhưng một bytecode weaver phù hợp có thể hỗ trợ execution join point ở đó.

Các nhận định như “AOP bỏ qua tất cả final method” hoặc “pointcut luôn bắt được private method” đều quá rộng. Cần nói cụ thể **cơ chế** và **loại join point**.

## 12. Java generics, bridge method và chữ ký có thể gây ngạc nhiên

Generics trong Java được biên dịch chủ yếu bằng **type erasure**. Khi override những phương thức có kiểu generic/covariant return, compiler có thể sinh ra **synthetic bridge method** để duy trì khả năng dispatch và tương thích chữ ký bytecode.

Ví dụ ý tưởng:

~~~java
interface Reader<T> {
    T read();
}

final class StringReader implements Reader<String> {
    @Override
    public String read() {
        return "value";
    }
}
~~~

Đây là một đoạn minh họa không có main. Trên bytecode, compiler có thể thêm bridge để đáp ứng hợp đồng đã xóa kiểu. Một công cụ quan sát bytecode ở mức thấp có thể nhìn thấy thêm method synthetic, còn một công cụ AOP cấp cao có thể diễn giải bridge và method gốc theo quy tắc riêng.

Từ đó có ba điều cần kiểm tra:

1. Pointcut chọn theo signature của **source Java**, signature đã qua erasure hay metadata của join point do công cụ cung cấp?
2. Advice có được áp dụng một lần cho một lời gọi nghiệp vụ hay có thể xuất hiện ở nhiều tầng method/bridge khi công cụ cho phép?
3. Sau khi đổi kiểu generic hoặc kiểu trả về covariant, tập join point được chọn có thay đổi không?

Không nên mặc định mỗi method hiển thị trong IDE tương ứng đúng một method vật lý ở bytecode và đúng một event advice cho mọi engine.

## 13. Proxy runtime với đa hình: ranh giới của đường đi

Một proxy làm trung gian nhận cuộc gọi, có thể chạy advice rồi ủy quyền cho target. Đó là **một hình thức interception**; không phải toàn bộ AOP và không có cùng năng lực với mọi kiểu weaving.

Trong ví dụ Java 17 phần 7, proxy được tạo cho **Action**:

~~~text
Caller
  │
  ▼
Action proxy
  │
  ├─ interceptor nhận outer()
  │
  ▼
Child object (target)
  │
  ▼
Base.outer()            ← thân kế thừa
  │
  └─ gọi inner() nội bộ
         │
         ▼
      Child.inner()     ← dynamic dispatch
      (không đi lại qua Action proxy)
~~~

Điều này minh họa hai quy tắc độc lập:

- **Java polymorphism vẫn hoạt động** bên trong target: Child.inner được chọn.
- **Proxy interception chỉ kích hoạt với lời gọi qua proxy**: inner nội bộ không quay lại handler trong ví dụ.

Nếu có một bytecode weaver chèn advice trực tiếp tại **Child.inner() execution**, lời gọi inner nội bộ vẫn có thể được quan sát, vì đường đi không nhất thiết phụ thuộc proxy. Nhưng phải xác nhận method thực sự được weave và pointcut khớp.

### 13.1. Interface method và implementation method nhìn khác nhau

Trong JDK dynamic proxy, đối tượng Method được gửi cho handler có thể biểu diễn method thuộc interface mà proxy triển khai; instance target vẫn có thể là một subclass cụ thể. Vì vậy thông tin reflection tại proxy handler không tự nói rằng bytecode execution đang nằm trong lớp chứa method interface.

Một policy chọn method dựa trên annotation ở interface và một policy chọn annotation ở implementation có thể cho kết quả khác nếu công cụ không tự hợp nhất metadata. Kiểm tra đối tượng Method, khai báo interface, override và quy tắc lookup của interceptor thực tế.

### 13.2. Một đường gọi không qua proxy không tạo event interceptor

Nếu code giữ cả tham chiếu **proxy** và tham chiếu **target**, người gọi trực tiếp target sẽ bỏ qua handler. Điều đó không làm thay đổi dynamic dispatch: override của subclass vẫn chạy bình thường.

Khi cần cam kết bao phủ, phải xác định **caller được phép giữ tham chiếu nào**, có đường truy cập trực tiếp nào tồn tại hay không và pointcut được thực thi bằng proxy hay weaving. Chỉ định nghĩa interface hoặc một aspect không tự bảo đảm tất cả đường đi đều chảy qua interceptor.

## 14. Những tác động khi thêm một subclass vào hệ thống

Giả sử ban đầu chỉ có **BaseService.process()** và pointcut quan sát thân Base. Sau này có **VipService extends BaseService** ghi đè process, thêm kiểm tra riêng.

Có thể xảy ra:

1. **Caller vẫn biên dịch như cũ:** nó gọi qua BaseService.
2. **Runtime dispatch đổi:** đối tượng VipService khiến thân **VipService.process()** chạy.
3. **Execution join point cần xét lại:** rule chỉ chọn chính xác thân Base có thể không quan sát hành vi mới.
4. **Call join point ở một số caller giữ nguyên:** nếu rule chọn nơi gọi theo kiểu Base, những caller đã được weave vẫn có thể khớp; nhưng caller mới/kiểu gọi mới cần kiểm tra.
5. **Annotation có thể khác:** override VipService không mặc nhiên nhận method annotation từ Base.
6. **Precedence giữa các advice có thể đổi:** policy chung và policy riêng cùng khớp có thể chạy chồng.

Vậy việc thêm subclass là **một thay đổi có thể ảnh hưởng phạm vi AOP**, kể cả khi API công khai không đổi. Đây là lý do nên gắn **kiểm thử mức bao phủ** với các lớp con quan trọng thay vì chỉ test code Base.

## 15. Cách thiết kế pointcut bền vững trước kế thừa và override

Trước khi chọn biểu thức pointcut, hãy mô tả concern bằng một câu chính xác:

- “Mọi lượt **thực thi** của hành vi thanh toán, bất kể implementation”.
- “Mọi lời gọi API thanh toán **từ một nhóm caller**”.
- “Chỉ theo dõi phương thức khai báo trực tiếp trong **PremiumPayment**”.
- “Chỉ theo dõi khi **receiver runtime** thuộc một subtype cụ thể”.
- “Chỉ theo dõi khi **method declaration** mang annotation được chỉ định”.

Những phát biểu này **không tương đương**, dù có thể đều được mô tả ngắn bằng câu “theo dõi Payment”.

### 15.1. Lựa chọn trục phân loại

Có thể tổ chức điều kiện trên các trục:

| Trục | Ví dụ | Câu hỏi kiểm tra |
|---|---|---|
| **Loại join point** | call, execution | Chặn ở nơi gọi hay nơi thực thi? |
| **Hợp đồng kiểu** | interface, base type, subtree | Class mới thêm vào có được bao phủ theo ý định không? |
| **Tên/chữ ký** | process, pay, tham số | Overload hoặc bridge có gây trùng/thiếu không? |
| **Kiểu runtime** | target là VipService | Với object kế thừa method, điều kiện vẫn đúng chứ? |
| **Vị trí lexical** | caller thuộc package A | Một lớp mới ở package khác có bị bỏ sót không? |
| **Annotation/metadata** | @Observed | Override có annotation trực tiếp không; engine tìm annotation ở đâu? |
| **Đường chặn thực tế** | proxy, weaving | Có đường gọi bỏ qua hoặc lớp không nằm trong phạm vi dệt không? |

### 15.2. Ưu tiên hợp đồng ổn định khi concern là chung

Nếu một policy áp dụng cho mọi implementation của một giao diện, hãy làm cho **ý định toàn họ kiểu** trở nên rõ qua thiết kế API, type pattern/hợp đồng hoặc metadata của công cụ. Nếu policy chỉ áp dụng cho subclass đặc biệt, biểu thức nên chỉ rõ subclass và lý do lựa chọn đó.

Mẫu tên package quá rộng có thể bắt thêm mã ngoài ý định. Mẫu quá cụ thể vào một implementation có thể bỏ sót subtype mới. Không có một pointcut “đúng nhất” cho mọi trường hợp; độ rõ của **chính sách nghiệp vụ/kỹ thuật** mới quyết định loại pointcut.

### 15.3. Điều kiện phủ định có thể khó hơn điều kiện khẳng định

Ví dụ ý tưởng:

~~~text
all payment executions
AND NOT methods declared in Base
~~~

Nếu subclass kế thừa method Base, điều kiện “declared in Base” vẫn có thể áp dụng dù receiver runtime là subclass. Nếu mục đích thật là “mọi instance của subclass, kể cả method kế thừa”, điều kiện phủ định theo declaring type có thể sai.

Khi loại trừ lớp hoặc method, nên test các trường hợp: Base trực tiếp, subclass override, subclass kế thừa, interface implementation và lời gọi qua proxy.

## 16. Phạm vi dệt là điều kiện độc lập với pointcut

Một pointcut diễn đạt đúng vẫn có thể không kích hoạt nếu mã liên quan **không nằm trong phạm vi được công cụ AOP áp dụng**.

Với weaving ở compile time hoặc load time, cần kiểm tra:

- Các class thuộc Base và Child có được đưa vào phạm vi dệt không?
- Một implementation được load bởi class loader khác có được instrument không?
- Aspect có chọn **execution** ở nơi mã phương thức thực chạy, hay chỉ có call site đã dệt?
- Có phương thức mới được compiler sinh ra (bridge/synthetic) mà policy chưa xem xét không?

Với runtime proxy, cần kiểm tra:

- Proxy được tạo ở interface/lớp nào?
- Caller nhận proxy hay target gốc?
- Lời gọi **self-invocation** hoặc lời gọi đến method không lộ trên proxy có được chặn không?
- Công cụ có giới hạn với final/private/static method không?

**Không có tư cách “được bảo đảm áp dụng” chỉ bằng cách một subclass extends đúng lớp cha.** Bảo đảm chỉ có ý nghĩa khi biết điểm join có được hỗ trợ, pattern có khớp, cơ chế dệt có kiểm soát đúng mã/đường gọi và test chứng minh hành vi.

## 17. Phân biệt advice chạy nhiều lần với nhiều lần thực thi nghiệp vụ

Một lời gọi instance có thể tạo ra nhiều join point lồng nhau:

~~~text
Caller gọi Child.process()
    ├─ method-call join point
    ├─ method-execution Child.process()
    │     └─ Child.process() gọi super.process()
    │             └─ execution Base.process()
    └─ caller nhận kết quả
~~~

Trong một bộ dệt hỗ trợ cả call/execution, nếu pointcut chọn rộng nhiều nhóm điểm, **một lượt nghiệp vụ** có thể kích hoạt advice ở nhiều giai đoạn.

Nếu mục tiêu metric là “một giao dịch thanh toán”, đếm cả call vào Child, execution Child, execution Base và lời gọi ở helper sẽ tạo số đếm lớn hơn một. Việc advice xuất hiện nhiều lần không nhất thiết là bug của weaver; có thể rule đã chọn nhiều join point khác nhau như thiết kế.

### 17.1. Kiểm soát overlap giữa các aspect

Giả sử có policy chung cho Base và policy riêng cho Child. Khi Child được tạo và override process:

- Rule chung có thể khớp theo phạm vi subtype, metadata interface hoặc runtime target.
- Rule riêng có thể khớp chính xác Child.
- Hai advice có thể chạy cùng một join point và tạo log trùng hoặc tương tác thứ tự.

Cần xác định policy nào chịu trách nhiệm: policy chung, policy riêng bổ sung hay policy riêng thay thế. Khai báo precedence nếu công cụ hỗ trợ, và dùng kiểm thử đếm invocation để phát hiện trùng bất ngờ.

### 17.2. Around advice có thể đổi dispatch bằng cách đổi đích gọi

Một **around advice** có thể điều phối việc chạy hành vi gốc, thậm chí thay target hoặc trả kết quả khác nếu cơ chế cho phép. Không nên giả định mọi around advice luôn gọi đúng implementation mà Java sẽ dispatch nếu không có aspect.

Khi advice thay đổi đường thực thi, hãy xem lại bảo toàn kết quả, ngoại lệ, tính bất biến và việc gọi lại override/super. Một retry không có idempotency hoặc một lệnh gọi thêm super có thể khiến side effect lặp lại.

## 18. Kiểm thử tập điểm khớp và thứ tự thực thi

Điểm quan trọng nhất là **kiểm thử đúng tính chất của pointcut**, không chỉ kiểm thử nội dung một advice khi gọi bằng tay.

Nên dựng fixture có ít nhất:

~~~text
Interface Contract
       ▲
       │
Base implements Contract
       ▲
       ├── InheritingChild      (không override)
       ├── OverridingChild      (override)
       └── SpecializedChild     (override + overload)
~~~

Với cùng caller, thử lần lượt các runtime target. Sau đó thay caller để kiểm tra call site: qua interface, qua base reference, qua child reference và qua proxy nếu ứng dụng dùng proxy.

### 18.1. Ma trận kiểm thử đề xuất

| Tình huống | Câu hỏi cần xác nhận |
|---|---|
| Base gọi trực tiếp | Advice có khớp thân/method Base như dự định không? |
| Child kế thừa không override | Pointcut có xét declaring type của Base hay runtime type của Child? |
| Child override | Execution advice có bám theo thân mới không? |
| Caller qua interface | Chữ ký call và implementation được chọn ra sao? |
| Caller qua Base reference | Runtime dispatch đến Child có được AOP quan sát đúng? |
| Caller qua Child reference | Tập overload ở compile time có thay đổi không? |
| Method static/final/private | Cơ chế AOP có hỗ trợ loại điểm muốn chặn không? |
| Child gọi super.method | Có phát sinh advice ở execution của superclass không? |
| Method annotation chỉ ở Base | Override có được engine coi là annotated không? |
| Child tự gọi method khác | Có self-invocation bỏ qua proxy không? |
| Hai pointcut cùng khớp | Advice có chạy trùng hoặc sai precedence không? |
| Compiler tạo bridge | Số lượt advice có giữ đúng kỳ vọng không? |

### 18.2. Xác minh số lượt và thứ tự

Khi thử, ghi log có ít nhất:

- Loại join point: **call** hay **execution**.
- Chữ ký method được mô hình AOP báo cáo.
- Kiểu khai báo, kiểu runtime của target nếu có.
- Nơi gọi hoặc enclosing method nếu công cụ cung cấp.
- Một id cho lượt gọi để phân biệt các invocations lồng nhau.
- Thứ tự **before**, **around/proceed**, **after returning/throwing**.

Không nên chỉ in tên phương thức: với override và overload, nhiều phương thức có thể cùng tên nhưng khác signature hoặc declaring type.

### 18.3. Kiểm thử cả trường hợp không khớp

Nếu pointcut chỉ được phép quan sát Child, hãy chứng minh Base thuần không bị advice chạy. Nếu chỉ được phép quan sát call từ package giao tiếp bên ngoài, hãy chứng minh call nội bộ không tự lọt vào tập.

Các test “không được chạy” thường phát hiện pointcut quá rộng; các test “phải chạy” phát hiện pointcut thiếu subtype hoặc bỏ qua override.

## 19. Những sai lầm thường gặp

**Sai lầm 1 — “Biến kiểu Base thì lúc nào cũng chạy Base.method.”** Với virtual/interface method có override, Java dispatch theo runtime receiver, nên thân Child có thể chạy.

**Sai lầm 2 — “Object Child thì mọi method execution được khai báo ở Child.”** Method kế thừa không override có thể thực thi thân khai báo ở Base.

**Sai lầm 3 — “Overload là override.”** Compiler giải quyết overload theo kiểu và phương thức khả dụng tại compile time; override là cơ chế chọn thân tại runtime.

**Sai lầm 4 — “call và execution bắt đúng một tập join point.”** Call nằm ở chỗ gọi, execution ở chỗ thực thi; đa hình khiến hai phía có thể gắn với các kiểu khai báo khác nhau.

**Sai lầm 5 — “Pointcut kiểm tra target Child tương đương pointcut within Child.”** Một điều kiện dựa vào runtime receiver, điều kiện khác dựa vào vị trí lexical trong mã. Method kế thừa thể hiện khác biệt đó.

**Sai lầm 6 — “Method annotation luôn truyền sang override.”** Java không tự kế thừa annotation của method khi override; công cụ có thể tìm siêu lớp theo quy tắc riêng, phải xác minh.

**Sai lầm 7 — “Final/private method không thể can thiệp bằng bất kỳ AOP nào.”** Khả năng tùy cơ chế. Proxy thông thường có giới hạn khác với bộ dệt bytecode có các join point tương ứng.

**Sai lầm 8 — “Pointcut Base+ nghĩa là mọi object Base/Child đều chắc chắn được quan sát.”** Pattern còn phụ thuộc join point, phạm vi weaving và đường gọi.

**Sai lầm 9 — “Đã đi qua proxy một lần thì mọi lời gọi bên trong target cũng qua proxy.”** Trong ví dụ JDK dynamic proxy, lời gọi nội bộ vẫn dispatch Java đúng nhưng không kích hoạt handler thêm lần nữa.

**Sai lầm 10 — “Log trùng nghĩa là runtime gọi nghiệp vụ hai lần.”** Một lượt gọi có thể phát sinh nhiều join point và nhiều advice; cần phân biệt sự kiện quan sát với số lần thân nghiệp vụ thực chạy.

**Sai lầm 11 — “Refactor kế thừa không ảnh hưởng AOP vì tên method không đổi.”** Thêm override có thể đổi declaring/execution method, annotation lookup, kiểu target và số advice khớp.

## 20. Cách ra quyết định với một pointcut có đa hình

Hãy trả lời tuần tự:

1. **Policy theo lời gọi hay theo thân thực thi?** Đó là quyết định giữa call/execution hoặc đường interception có sẵn.
2. **Policy thuộc hợp đồng interface, lớp cha hay chỉ một implementation?** Đó là cách chọn họ kiểu và phạm vi metadata.
3. **Policy phụ thuộc kiểu tĩnh hay runtime type?** Không trộn hai ý tưởng trong một điều kiện mơ hồ.
4. **Method có thể được kế thừa không override, override, overload hoặc sinh bridge không?** Mỗi trường hợp ảnh hưởng tập chữ ký và điểm thực thi.
5. **Có phải một lần nghiệp vụ cần đúng một event?** Kiểm tra chồng call/execution, super invocation, nested method và các aspect khác.
6. **Cơ chế AOP có thật sự can thiệp vào join point đó không?** Đánh giá bytecode weaving/proxy, class loader, phạm vi instrument và đường gọi.
7. **Đã kiểm thử cả trường hợp khớp và không khớp chưa?** Dùng fixture chứa Base, Child override, Child kế thừa và interface caller.

## 21. Tự kiểm tra

**Câu 1.** **Base ref = new Child()** và Child override **process()**. Tại sao một execution pointcut chọn chính xác thân Base.process có thể không quan sát lượt gọi này?

**Đáp:** Java dispatch đến thân Child.process; điều kiện chọn chính xác thân Base khác với việc chọn mọi lời gọi qua reference kiểu Base.

**Câu 2.** **Child extends Base** nhưng không override **inherited()**. Điều kiện dựa vào runtime target Child và điều kiện dựa vào declaring type Child có giống nhau không?

**Đáp:** Không. Đối tượng có runtime type Child nhưng thân được khai báo ở Base.

**Câu 3.** Với **Base value = new Child()**, Base có **select(Object)**, Child thêm **select(String)**. Vì sao **value.select("x")** không nhất thiết gọi overload String?

**Đáp:** Compiler giải quyết overload thông qua kiểu tĩnh Base; overload chỉ khai báo ở Child không có trong tập ứng viên qua Base.

**Câu 4.** Một Child override phương thức có **@Observed** tại Base nhưng không chép annotation. Dùng reflection của Java kiểm tra annotation trên chính phương thức Child có chắc thấy @Observed không?

**Đáp:** Không; method annotation không mặc nhiên kế thừa qua overriding. Engine có thể có logic lookup riêng nhưng phải xác minh.

**Câu 5.** Proxy intercept **outer()**, mà target.outer gọi trực tiếp **inner()**. Java vẫn chọn Child.inner, nhưng handler có bắt thêm inner không?

**Đáp:** Không trong mô hình dynamic proxy ở ví dụ; inner được gọi bên trong target và không đi qua proxy. Một công cụ weave trực tiếp execution của inner có thể có hành vi khác.

**Câu 6.** Bài test chỉ xác nhận output nghiệp vụ đúng sau khi thêm subclass. Đã đủ để chứng minh pointcut bao phủ subclass chưa?

**Đáp:** Chưa. Phải đo advice có chạy ở join point dự định, chạy đúng số lần, trên đúng implementation, kể cả ca không khớp và gọi lồng nhau.

**Câu 7.** Hai advice cùng log khi một caller gọi Child.process. Có thể khẳng định nghiệp vụ đã chạy hai lần không?

**Đáp:** Không. Có thể hai advice cùng chọn một join point, hoặc một advice chọn call và một advice chọn execution. Cần log loại join point và đếm riêng thân nghiệp vụ.

## 22. Mô hình ghi nhớ

~~~text
              LỜI GỌI TRONG MÃ NGUỒN
              reference type / call site
                         │
                         ▼
                Phân giải ở compile time
                (chữ ký, overload, khả năng gọi)
                         │
                         ▼
                CALL JOIN POINT (nếu hỗ trợ)
                         │
                         ▼
               RUNTIME RECEIVER OBJECT
               Base? Child? Subclass khác?
                         │
                         ▼
                 Dynamic method lookup
                         │
             ┌───────────┴───────────┐
             ▼                       ▼
       Body ở Base              Override ở Child
       (kế thừa)                (ghi đè)
             │                       │
             └───────────┬───────────┘
                         ▼
              EXECUTION JOIN POINT
              của thân thực sự chạy
                         │
                         ▼
                  Advice nếu khớp
                         │
                         ▼
                      Trả về
~~~

**Hai kết luận cần giữ đồng thời:** Java quyết định phương thức thực thi theo các quy tắc static resolution và dynamic dispatch; AOP quyết định có gắn advice hay không theo **loại join point, chữ ký, runtime context và cơ chế dệt/chặn lời gọi**. Kế thừa, override và đa hình không phải trường hợp ngoại lệ hiếm, mà là điều kiện thường trực khi viết pointcut cho ứng dụng hướng đối tượng.

## Tài liệu đối chiếu

- [Java Language Specification, Java SE 17, §15.12 — Method Invocation Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.12): giải quyết lời gọi phương thức, chọn overload và dynamic method lookup.
- [Java Language Specification, Java SE 17, §8.4.8 — Inheritance, Overriding, and Hiding](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.8): quan hệ khai báo phương thức, overriding và method hiding.
- [AspectJ Programming Guide — Language Semantics / Join Points](https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html): ví dụ mô hình AOP phân biệt call/execution, chữ ký static/declaring type và this/target. Cú pháp của AspectJ chỉ được dùng làm ví dụ về một ngôn ngữ AOP cụ thể.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-basics-006">Ký tự đại diện và biểu thức mẫu theo cú pháp công cụ</a>

<details>
<summary>Xem chi tiết</summary>

# Ký tự đại diện và biểu thức mẫu theo cú pháp công cụ trong pointcut AOP

## 1. Vì sao pointcut cần ký tự đại diện và biểu thức mẫu?

Trong **lập trình hướng khía cạnh (Aspect-Oriented Programming — AOP)**, một **pointcut** xác định những **join point** nào sẽ được chọn để áp dụng **advice**. Join point là điểm mà mô hình thực thi và cơ chế AOP cho phép can thiệp, chẳng hạn một lần gọi hoặc thực thi phương thức nếu công cụ hỗ trợ.

Nếu chỉ định từng phương thức bằng tên đầy đủ, pointcut có thể trở nên dài, lặp và khó duy trì. **Ký tự đại diện (wildcard)** và **biểu thức mẫu (pattern expression)** cho phép mô tả một tập các tên, kiểu, chữ ký hoặc vị trí chương trình bằng quy tắc tổng quát.

Ví dụ, một yêu cầu có thể được mô tả bằng lời:

> Ghi nhận thời gian cho tất cả phương thức công khai của các lớp dịch vụ trong một nhóm package, không phụ thuộc tên phương thức và số tham số.

Thay vì viết hàng chục biểu thức cho từng phương thức, một ngôn ngữ pointcut có thể cung cấp ký hiệu để mô tả tập tương ứng. Tuy nhiên, **không tồn tại một bộ ký tự đại diện và cú pháp pointcut thống nhất cho mọi giải pháp AOP**. Mỗi công cụ quyết định cú pháp, loại join point, cách khớp tên/kiểu, độ ưu tiên toán tử và cơ chế weaving của chính nó.

Bài này phân biệt **nguyên tắc chung** với **cú pháp cụ thể**, sau đó dùng **AspectJ** làm ví dụ cho một ngôn ngữ pointcut AOP có tài liệu chính thức. Các biểu thức AspectJ bên dưới chỉ có hiệu lực khi dùng công cụ và phạm vi weaving hỗ trợ ngữ nghĩa tương ứng; không phải mã Java thuần tự chạy được.

### Kết quả học tập

Sau bài này, bạn cần:

1. Hiểu vì sao wildcard hữu ích nhưng có rủi ro chọn nhầm join point.
2. Phân biệt mẫu tên, mẫu kiểu, mẫu chữ ký và điều kiện theo ngữ cảnh.
3. Đọc được ý nghĩa của `*`, `..`, `+` và các toán tử logic trong **cú pháp AspectJ**.
4. Phân biệt mẫu chọn *method call* và *method execution*, cùng ý nghĩa của `within`, `args`, `this`, `target`.
5. Tự xác minh tập join point được chọn bằng ca phải khớp/không được khớp, và không nhầm biểu thức pointcut với Java regex hay glob thông thường.

## 2. Từ mục tiêu chọn điểm đến một biểu thức pointcut

Hãy xem quy trình suy nghĩ:

```text
Mục tiêu chính sách
    |
    v
Cần can thiệp sự kiện nào?
    |        (call, execution, field access, ...)
    v
Cần lọc cấu trúc tĩnh nào?
    |        (package, kiểu khai báo, chữ ký, modifier, ...)
    v
Cần lọc ngữ cảnh động nào?
    |        (kiểu đối tượng thực tế, giá trị tham số, luồng chạy, ...)
    v
Biểu thức pointcut theo cú pháp công cụ
    |
    v
Weaving / interception tại các join point được hỗ trợ
    |
    v
Advice chạy cho những lần thực thi thực sự khớp
```

Có ba lớp thông tin dễ nhầm:

| Lớp | Ví dụ | Vai trò |
| --- | --- | --- |
| **Loại điểm** | Method execution | Chọn dạng sự kiện có thể can thiệp |
| **Mẫu tĩnh** | Phương thức tên `save*`, thuộc kiểu `*Service` | Chọn theo cấu trúc của chương trình |
| **Điều kiện động** | Đối tượng/đối số tại lần chạy cụ thể | Lọc những lần xuất hiện thực sự phù hợp |

Một wildcard như `*` thường hoạt động **trong một vị trí của ngữ pháp**, không phải tự động quét mọi hoạt động của chương trình. Trước khi đọc `*`, phải hỏi: **nó đang thay cho tên phương thức, kiểu trả về, tên kiểu, hay một kiểu tham số?**

## 3. Cú pháp ví dụ được dùng trong bài: AspectJ

Để có ngữ nghĩa kiểm chứng được, phần này dùng cú pháp biểu thức pointcut từ **AspectJ**. Đây là một ngôn ngữ/cơ chế AOP cho Java, có mô hình pointcut và weaving riêng.

Một biểu thức thường có dạng:

```aspectj
execution(public * com.example.service.OrderService.placeOrder(..))
```

Có thể đọc từ ngoài vào trong:

- `execution(...)`: chọn **lần thực thi phương thức**, khi phần thân phương thức tương ứng được chạy.
- `public`: yêu cầu modifier phù hợp.
- `*` sau `public`: chấp nhận **bất kỳ kiểu trả về**.
- `com.example.service.OrderService`: tên kiểu khai báo trong mẫu.
- `placeOrder`: tên phương thức.
- `(..)`: chấp nhận **không, một hoặc nhiều tham số** thuộc các kiểu bất kỳ.

Trong ví dụ, package/lớp chỉ là **tên giả định phục vụ giải thích**; đoạn trên là **biểu thức AspectJ**, không phải một dòng Java có thể đặt trực tiếp trong `main()`.

Hai giới hạn cần nhớ:

1. Biểu thức hợp lệ **chưa đủ** bảo đảm advice chạy. Phải có join point đúng loại và phạm vi mã được weaving.
2. Khi một công cụ khác cũng dùng từ `execution` hay `*`, không nên tự suy diễn rằng nó hỗ trợ mọi thành phần cú pháp và kiểu join point giống nhau.

## 4. Wildcard `*`: ý nghĩa phụ thuộc vào vị trí

Trong các mẫu kiểu, tên và chữ ký của AspectJ, `*` là ký hiệu đại diện cơ bản. Nhưng cách đọc chính xác thay đổi theo cấu trúc biểu thức.

### 4.1. Thay cho bất kỳ kiểu trả về nào

```aspectj
execution(* com.example.OrderService.find(..))
```

`*` trước tên kiểu có nghĩa là kiểu trả về của phương thức có thể là `void`, một kiểu nguyên thủy hoặc một kiểu tham chiếu, miễn các phần khác của mẫu khớp.

Nếu muốn chỉ phương thức trả `boolean`:

```aspectj
execution(boolean com.example.OrderService.isValid(..))
```

Đừng nhầm dấu `*` trong vị trí **kiểu trả về** với dấu `*` dùng làm **tên phương thức** trong ví dụ khác.

### 4.2. Thay cho tên phương thức

```aspectj
execution(* com.example.OrderService.*(..))
```

Dấu `*` ngay trước `(..)` nằm ở vị trí **tên phương thức**, nên mẫu này chọn các lần thực thi phương thức có tên bất kỳ trong kiểu tương ứng, với kiểu trả về và danh sách tham số bất kỳ.

Có thể dùng `*` như một phần của tên:

```aspectj
execution(* com.example.OrderService.find*(..))
```

Tên `find`, `findById`, `findAll` có thể khớp phần `find*`, còn `save` thì không. Ký tự `*` ở đây đại diện chuỗi ký tự có độ dài **từ không trở lên** trong phần tên được mẫu cho phép.

**Cẩn thận:** `find*` không có nghĩa chỉ chọn các phương thức *thật sự chỉ đọc*. Nếu mã nghiệp vụ có phương thức `findAndDelete`, nó vẫn có thể khớp theo tên. Mẫu tên không tự kiểm chứng ngữ nghĩa nghiệp vụ.

### 4.3. Mẫu tên kiểu

```aspectj
within(com.example.service.*Service)
```

Biểu thức trên dùng `*Service` để khớp tên kiểu kết thúc bằng `Service`, trong package phù hợp. Đây là mẫu **kiểu**, không phải mẫu tên phương thức.

Một dấu `*` trong mẫu tên kiểu không tự ý đi qua mọi dấu chấm phân cách package. Vì vậy, không nên suy từ `com.example.service.*Service` rằng nó luôn bao phủ cả các package con.

### 4.4. Một kiểu tham số bất kỳ

```aspectj
execution(* com.example.OrderService.find(*))
```

Dấu `*` bên trong danh sách tham số, trong ngữ nghĩa signature pattern của AspectJ, nghĩa là **chính xác một tham số thuộc kiểu bất kỳ**.

So sánh:

| Mẫu phần tham số | Ý nghĩa |
| --- | --- |
| `()` | Không có tham số |
| `(*)` | Chính xác một tham số, kiểu bất kỳ |
| `(*,*)` | Chính xác hai tham số, mỗi kiểu bất kỳ |
| `(java.lang.String)` | Chính xác một tham số `String` |
| `(..)` | Bất kỳ số lượng tham số, bao gồm không có |
| `(java.lang.String,..)` | Tham số đầu là `String`, theo sau có thể có thêm bất kỳ số lượng tham số |

Đây là một trong những sai lầm phổ biến nhất: **`(*)` không đồng nghĩa `(..)`**.

## 5. Ký hiệu `..`: hai vai trò quan trọng

Trong cú pháp AspectJ, `..` xuất hiện ở ít nhất hai ngữ cảnh khác nhau. Không nên đọc mọi cặp dấu chấm như “mọi thứ” một cách chung chung.

### 5.1. Bỏ qua không hoặc nhiều thành phần package trong mẫu kiểu

```aspectj
within(com.example.service..*)
```

Ở vị trí mẫu kiểu, `..` cho phép khớp qua các mức package hoặc kiểu lồng theo các quy tắc của AspectJ. Theo ý định thông thường, mẫu trên bao gồm các kiểu nằm dưới namespace `com.example.service`, kể cả những mức con phù hợp.

So sánh:

```aspectj
within(com.example.service.*)
within(com.example.service..*)
```

Mẫu thứ nhất chỉ có một wildcard tên kiểu trong package được viết. Mẫu thứ hai cho phép biểu diễn phạm vi rộng hơn qua các package con. Đây là lý do ký hiệu `..` rất hay xuất hiện trong các chính sách chọn theo package.

Ví dụ giả định:

| Tên kiểu | `com.example.service.*` | `com.example.service..*` |
| --- | --- | --- |
| `com.example.service.OrderService` | Khớp | Khớp |
| `com.example.service.payment.PaymentService` | Không | Khớp |
| `com.example.repository.OrderRepository` | Không | Không |

Ý nghĩa này được mô tả theo **cú pháp mẫu kiểu AspectJ**; cách xử lý package lồng của công cụ khác có thể khác.

### 5.2. Bỏ qua không hoặc nhiều tham số trong signature pattern

```aspectj
execution(* com.example.OrderService.update(..))
```

Trong `update(..)`, ký hiệu `..` có nghĩa **danh sách tham số dài tùy ý**, kể cả rỗng.

```aspectj
execution(* com.example.OrderService.update(java.lang.String,..))
```

Mẫu thứ hai yêu cầu **ít nhất một tham số**, trong đó tham số đầu là `String`, và cho phép có thêm tham số bất kỳ sau nó.

So sánh bốn chữ ký giả định:

| Phương thức | `update()` | `update(*)` | `update(..)` | `update(String,..)` |
| --- | --- | --- | --- | --- |
| `update()` | Có | Không | Có | Không |
| `update(String)` | Không | Có | Có | Có |
| `update(int)` | Không | Có | Có | Không |
| `update(String,int)` | Không | Không | Có | Có |

Ký hiệu `String` trong bảng viết gọn cho `java.lang.String`. Bảng giả định mọi phần khác của pointcut đều tương ứng đúng với phương thức.

### 5.3. Không nhầm `..` với `.*` của regular expression

Trong Java regex, `.*` có nghĩa **không hoặc nhiều ký tự bất kỳ** theo quy tắc của regular expression. Trong AspectJ, `..` nằm ở **mẫu package** hoặc **danh sách tham số** lại có ngữ nghĩa riêng.

Không nên biến chuỗi `com.example..*` thành Java regex rồi khẳng định kết quả khớp giống pointcut của công cụ. Có thể dùng regex trong mã ứng dụng, nhưng đó là một ngôn ngữ mẫu **khác**.

## 6. Ký hiệu `+`: kiểu cùng các kiểu con phù hợp

Một số biểu thức AspectJ dùng `+` **sau type pattern** để biểu diễn kiểu đó và các kiểu con của nó:

```aspectj
execution(* com.example.PaymentService+.*(..))
```

Theo ngữ nghĩa mẫu subtype của AspectJ, `PaymentService+` bao gồm `PaymentService` và các subtype phù hợp, bao gồm lớp triển khai giao diện nếu `PaymentService` là giao diện.

Điều này hữu ích khi chính sách phải áp dụng nhất quán cho cả một họ kiểu. Tuy nhiên, cần phân biệt:

- **Kiểu khai báo mà mẫu đang nhắm đến**, ví dụ `PaymentService+`.
- **Kiểu runtime cụ thể** của đối tượng lúc chạy, có thể là một lớp triển khai.
- **Loại join point được chọn**, ở đây là execution theo mẫu chữ ký.

Không suy rằng cứ có đối tượng thuộc subtype thì mọi lời gọi của nó chắc chắn được can thiệp: kết quả còn phụ thuộc phạm vi weaving, kiểu điểm và những điều kiện khác.

### 6.1. `+` không phải wildcard tên phương thức

`PaymentService+` là dạng **subtype type pattern**. Không dùng `+` tùy tiện sau tên phương thức như `find+` để diễn đạt một phần tên theo cách Java regex; điều đó không phải ngữ nghĩa tương ứng của ký hiệu này trong cú pháp AspectJ.

## 7. Phép toán logic để kết hợp nhiều điều kiện

Khi mẫu đơn chưa đủ chính xác, các phép toán logic kết hợp pointcut:

- `&&` — **AND**: phải thỏa cả hai điều kiện.
- `||` — **OR**: thỏa ít nhất một điều kiện.
- `!` — **NOT**: loại những điểm thỏa điều kiện bị phủ định.
- `(...)` — dùng **ngoặc** để biểu diễn rõ nhóm điều kiện.

Ví dụ:

```aspectj
execution(* com.example..*Service.*(..))
    && !within(com.example..*Generated*)
```

Đây là biểu thức AspectJ chia thành nhiều dòng để dễ đọc. Nó chọn điểm thực thi phương thức theo mẫu `*Service`, đồng thời loại những join point được định nghĩa bên trong các kiểu phù hợp với `*Generated*` theo điều kiện `within`.

Một ví dụ kết hợp hai nhóm:

```aspectj
execution(* com.example.order..*Service.*(..))
    || execution(* com.example.payment..*Service.*(..))
```

Nếu muốn loại các điểm được định nghĩa trong một tập kiểu khỏi **cả hai nhánh**, nên đặt ngoặc rõ ràng:

```aspectj
(
    execution(* com.example.order..*Service.*(..))
    || execution(* com.example.payment..*Service.*(..))
)
&& !within(com.example..*Generated*)
```

Ngoặc có thể quyết định kết quả chọn điểm. Thay vì dựa vào trí nhớ về độ ưu tiên toán tử ở từng DSL, viết ngoặc và đặt tên các pointcut con giúp người đọc kiểm tra mục đích dễ hơn.

### 7.1. Kết hợp mẫu rộng với điều kiện loại trừ

Một mẫu rộng có thể giúp phủ nhiều nơi, nhưng dễ tạo hiệu ứng không mong muốn. Mẫu loại trừ cải thiện độ chính xác **chỉ khi tập loại trừ có ý nghĩa và được duy trì**.

Nếu chính sách đặc biệt quan trọng, nên ưu tiên dấu hiệu cấu trúc ổn định — kiểu hoặc annotation có hợp đồng rõ — hơn là dựa hoàn toàn vào hậu tố tên như `*Service` hoặc `*Generated*`.

## 8. Mẫu chữ ký có cấu trúc: đừng đọc như một glob duy nhất

Trong AspectJ, một **method signature pattern** có thể chứa các vị trí như:

```text
[modifiers] return-type declaring-type.method-name(parameter-patterns)
```

Một ví dụ:

```aspectj
execution(public * com.example.api.*Service.find*(java.lang.String,..))
```

Đọc từng thành phần:

1. `execution`: loại join point.
2. `public`: modifier yêu cầu.
3. `*`: mọi kiểu trả về.
4. `com.example.api.*Service`: kiểu có tên phù hợp ở package được chỉ định.
5. `find*`: tên phương thức bắt đầu bằng `find`.
6. `java.lang.String,..`: tham số đầu là `String`, sau đó thêm không hoặc nhiều tham số.

Nếu một thành phần không khớp, toàn bộ biểu thức không khớp. Bởi vậy, khi debug biểu thức, nên tách thành từng câu hỏi về modifier, return type, declaring type, method name và parameters.

### 8.1. Ví dụ so sánh theo số tham số và kiểu trả về

Giả sử có các phương thức trong cùng kiểu `com.example.api.OrderService`:

```java
void save();
void save(String id);
boolean save(String id, int quantity);
String find(String id);
```

Đây là **mã minh họa chữ ký**, không phải Java class độc lập.

| Biểu thức AspectJ | Những phương thức khớp trong tập trên |
| --- | --- |
| `execution(* com.example.api.OrderService.save(..))` | Cả ba `save` |
| `execution(* com.example.api.OrderService.save())` | Chỉ `save()` |
| `execution(* com.example.api.OrderService.save(*))` | Chỉ `save(String)` |
| `execution(boolean com.example.api.OrderService.save(..))` | Chỉ `save(String,int)` trả `boolean` |
| `execution(* com.example.api.OrderService.find*(..))` | Chỉ `find(String)` |

Đừng chỉ đọc “save có wildcard” rồi bỏ qua vị trí của wildcard. Dấu `*` trước kiểu khai báo và dấu `*` trong tham số đang giải quyết **hai yêu cầu khác nhau**.

## 9. Mẫu tên và điều kiện theo vị trí khác nhau thế nào?

Một pointcut có thể chọn loại join point bằng `execution`, `call`, và lọc thêm ngữ cảnh tĩnh bằng `within` hoặc `withincode`, nếu công cụ cung cấp các designator tương ứng.

### 9.1. `execution(...)` và `call(...)`

```aspectj
execution(* com.example.PaymentGateway.pay(..))
call(* com.example.PaymentGateway.pay(..))
```

- `execution` chọn **việc thực thi thân phương thức phù hợp**, về phía được gọi.
- `call` chọn **vị trí gọi phương thức phù hợp**, về phía bên gọi.

Một phương thức có thể được gọi từ nhiều nơi. Nếu cần quan sát các vị trí gọi cụ thể, `call` có thể biểu đạt yêu cầu khác với `execution`. Một số cơ chế AOP chỉ hỗ trợ subset của các loại join point, vì vậy không thể thay designator rồi mặc định công cụ hoạt động.

### 9.2. `within(TypePattern)` giới hạn phạm vi từ vựng

```aspectj
within(com.example.checkout..*)
```

Trong ngữ nghĩa AspectJ, `within` chọn các join point thuộc **mã được định nghĩa trong các kiểu phù hợp**. Nó thiên về **vị trí trong cấu trúc mã**, không phải “mọi đối tượng runtime thuộc package này”.

Ví dụ chọn **lời gọi đến** `pay` xuất phát từ mã nằm trong nhóm kiểu checkout:

```aspectj
call(* com.example.PaymentGateway.pay(..))
    && within(com.example.checkout..*)
```

Đây không giống việc chọn tất cả **execution** của `pay`, vì call pointcut gắn với mã phía gọi.

### 9.3. `withincode(Signature)` lọc theo vị trí mã chứa điểm

```aspectj
call(* com.example.PaymentGateway.pay(..))
    && withincode(* com.example.CheckoutService.checkout(..))
```

Theo ngữ nghĩa AspectJ, ví dụ hướng tới những call join point của `pay` nằm trong phần mã được định nghĩa của `checkout(..)` tương ứng. `withincode` không đơn giản là “mọi lời gọi xảy ra trong cây lời gọi runtime bắt đầu từ `checkout`”.

### 9.4. `args`, `this`, `target` và `cflow` không chỉ là wildcard tên

Các designator sau có thể xét **ngữ cảnh động**:

```aspectj
args(java.lang.String,..)
this(com.example.CheckoutService)
target(com.example.PaymentGateway+)
cflow(execution(* com.example.CheckoutService.checkout(..)))
```

- `args` mô tả kiểu/khớp đối số trong ngữ cảnh join point; có thể hỗ trợ bind đối số tùy khai báo.
- `this` liên quan đến đối tượng hiện tại theo mô hình join point của AspectJ.
- `target` liên quan đến đối tượng đích.
- `cflow` liên quan đến **luồng điều khiển động**, kể cả các điểm nằm trong phạm vi một điểm gốc được chọn.

Không được nhầm các designator động với wildcard dựa hoàn toàn vào tên lớp trong mã. Ví dụ `within` xét **vị trí từ vựng**, trong khi `cflow` xét **diễn tiến lời gọi runtime**. Mức độ hỗ trợ và chi phí kiểm tra khác nhau theo công cụ và điểm được weaving.

## 10. Mẫu tên, regex và glob: ba ngôn ngữ khác nhau

Một điểm dễ gây lỗi là mang cú pháp từ ngôn ngữ này sang ngôn ngữ kia:

| Hệ biểu thức | Ví dụ | Ý nghĩa được minh họa |
| --- | --- | --- |
| **AspectJ name pattern** | `save*` | Tên có tiền tố `save` trong vị trí tên phù hợp |
| **Java regex** | `save.*` | Biểu thức chính quy khớp `save` theo sau là không hoặc nhiều ký tự |
| **Glob tệp** | `*.java` | Tên tệp có phần kết `.java` trong quy tắc glob tương ứng |

Có thể cả ba cùng khớp một vài chuỗi như `save` và `saveAll`, nhưng **cú pháp không hoán đổi được**. Trong regex, dấu chấm là ký tự đặc biệt; trong biểu thức tên kiểu AOP, dấu chấm thường phân tách thành phần package/kiểu và có ngữ nghĩa khác.

Những giả định sai thường thấy:

- **`*` không tự động là “mọi thứ qua nhiều package”**.
- **`(*)` không đồng nghĩa “mọi danh sách tham số”**.
- **`..` không phải `.*` của regex**.
- **`+` trong type pattern không có nghĩa regex “một hoặc nhiều lần lặp tên ký tự”**.
- **`?` không thể mặc định là wildcard một ký tự trong mọi DSL AOP**.
- **`**` không tự động mang ý nghĩa “đệ quy mọi package” theo thói quen glob tệp**.

Cần tra đúng tài liệu của ngôn ngữ pointcut, bao gồm phiên bản đang dùng. Ví dụ trong bài về AspectJ nhằm minh họa một bộ cú pháp **cụ thể**, không đại diện tuyệt đối cho tất cả công cụ.

## 11. Ví dụ Java 17 chạy được: chọn phương thức theo mẫu tên và số tham số

Để kiểm chứng ý tưởng **một biểu thức mẫu chọn một tập phương thức**, ta có thể dùng JDK thuần với `java.lang.reflect.Proxy`. Ví dụ này tự tạo một quy tắc tên dạng `save*` bằng cách kiểm tra tiền tố `save`, rồi kiểm tra số tham số bằng reflection.

**Đây là bộ lọc minh họa do ứng dụng tự viết**, không phải parser hay engine pointcut AspectJ. Một công cụ AOP có DSL riêng còn quyết định cú pháp, join point, weaving và điều kiện khớp của nó.

Tạo `PointcutPatternDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class PointcutPatternDemo {
    interface Store {
        void save(String id);
        void saveAll(String id);
        void delete(String id);
    }

    static final class SimpleStore implements Store {
        @Override
        public void save(String id) {
            System.out.println("business:save " + id);
        }

        @Override
        public void saveAll(String id) {
            System.out.println("business:saveAll " + id);
        }

        @Override
        public void delete(String id) {
            System.out.println("business:delete " + id);
        }
    }

    static Object dispatch(Store target, Method method, Object[] args)
            throws Throwable {
        boolean nameMatches = method.getName().startsWith("save");
        boolean oneParameter = method.getParameterCount() == 1;
        boolean selected = nameMatches && oneParameter;

        if (selected) {
            System.out.println("advice:before " + method.getName());
        }
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        } finally {
            if (selected) {
                System.out.println("advice:after " + method.getName());
            }
        }
    }

    public static void main(String[] args) {
        Store target = new SimpleStore();
        Store proxy = (Store) Proxy.newProxyInstance(
                Store.class.getClassLoader(),
                new Class<?>[] { Store.class },
                (ignored, method, values) -> dispatch(target, method, values)
        );

        proxy.save("A");
        proxy.saveAll("B");
        proxy.delete("C");
    }
}
```

Biên dịch/chạy với JDK 17 trở lên:

```bash
javac PointcutPatternDemo.java
java PointcutPatternDemo
```

Đầu ra dự kiến:

```text
advice:before save
business:save A
advice:after save
advice:before saveAll
business:saveAll B
advice:after saveAll
business:delete C
```

### 11.1. Phân tích kết quả

- `save` và `saveAll` đều có tiền tố `save`, tương ứng ý tưởng tên mẫu `save*` có wildcard độ dài từ không trở lên.
- Cả hai phương thức nhận chính xác **một tham số**, nên thỏa điều kiện bổ sung.
- `delete` cũng có một tham số nhưng **không** khớp tên; phương thức vẫn chạy nghiệp vụ nhưng không nhận hai dòng advice.
- Logic `before/after` nằm trong bộ chặn lời gọi, nên mã `SimpleStore` không cần gọi nó trực tiếp.

Nếu thêm `saveAll()` **không tham số** vào giao diện và đích, nó sẽ khớp điều kiện tên nhưng **không** thỏa điều kiện một tham số — tương tự việc một mẫu pointcut thực cần khớp **toàn bộ điều kiện**, không chỉ khớp tên.

### 11.2. Giới hạn cố ý của ví dụ

- Dấu `*` không hề được một trình phân tích DSL xử lý; chương trình dùng `startsWith("save")` để minh họa **một quy tắc đơn giản**.
- Proxy JDK chỉ can thiệp các lời gọi tới phương thức giao diện **đi qua proxy**. Lời gọi trực tiếp tới `target` hoặc một lời gọi nội bộ trên cùng đối tượng không mặc nhiên đi qua handler.
- `Method.getParameterCount()` phản ánh số tham số khai báo; nó khác việc áp dụng những điều kiện `args` động của ngôn ngữ pointcut.
- `finally` được dùng để minh họa phần xử lý sau lời gọi; với advice thật, thời điểm và cách xử lý kết quả/ngoại lệ thuộc ngữ nghĩa cụ thể của công cụ.

Ví dụ giúp nhìn thấy **điều kiện tên + điều kiện chữ ký + hành vi chặn lời gọi**, trong khi các biểu thức AspectJ trước đó giải thích cú pháp thực tế của một công cụ AOP cụ thể.

## 12. Viết pointcut tập trung và dễ bảo trì

Các mẫu càng ngắn và rộng thì càng dễ viết, nhưng cũng càng dễ áp dụng lời khuyên sai cho quá nhiều phương thức.

Giả sử mục tiêu là ghi metric cho phương thức public trong một nhóm service, đồng thời loại những điểm được định nghĩa trong kiểu nội bộ phục vụ thử nghiệm. Một biểu thức AspectJ minh họa:

```aspectj
execution(public * com.example.business..*Service.*(..))
    && !within(com.example.business..*Test*)
```

Nên thẩm định từng điều kiện:

1. **Loại join point:** có thực sự muốn method execution không?
2. **Modifier:** public có đúng ranh giới nghiệp vụ cần quan sát không?
3. **Kiểu khai báo:** các lớp cần theo dõi có nhất quán theo hậu tố `Service` không?
4. **Package:** có cần các package con? Có bỏ sót module liên quan?
5. **Loại trừ:** lớp có `Test` trong tên có thật sự là lớp cần loại? Có lớp dùng tên khác?
6. **Phạm vi weaving:** các lớp đã được công cụ xử lý chưa?
7. **Tác động advice:** nếu advice thay đổi kết quả hay từ chối thực thi, rủi ro có lớn hơn việc chỉ ghi metric không?

Một thiết kế có thể dùng **named pointcut** để biểu đạt ý nghĩa rõ hơn. Ví dụ sau là một **mảnh cú pháp AspectJ** đặt bên trong khai báo aspect phù hợp, chưa phải chương trình có thể biên dịch độc lập:

```aspectj
pointcut businessOperations():
    execution(public * com.example.business..*Service.*(..));

pointcut measuredOperations():
    businessOperations()
    && !within(com.example.business..*Test*);
```

Lợi ích của pointcut có tên là tách **mục đích** và **biểu thức kỹ thuật**, cho phép tái sử dụng và đọc lại dễ dàng. Nhưng named pointcut không tự động bảo đảm chọn đúng mọi phương thức: vẫn cần kiểm thử tập khớp.

## 13. Những tình huống mẫu thường gặp và cách diễn giải

Bảng sau dùng **cú pháp AspectJ**, với package và kiểu giả định:

| Mong muốn | Biểu thức minh họa | Lưu ý |
| --- | --- | --- |
| Mọi lần thực thi phương thức | `execution(* *(..))` | Rất rộng, tùy phạm vi weaving |
| Phương thức `save` có số tham số tùy ý | `execution(* *.save(..))` | Mẫu tên chính xác `save` |
| Phương thức bắt đầu bằng `find` | `execution(* *.find*(..))` | Khớp theo tên, không xác nhận tính chỉ đọc |
| Phương thức có một tham số kiểu bất kỳ | `execution(* *.*(*))` | Khác `(..)` |
| Các kiểu trong một package | `within(com.example.api.*)` | Không mặc định gồm mọi package con |
| Các kiểu trong package và package con | `within(com.example.api..*)` | Phạm vi từ vựng của kiểu |
| Method call tên `pay` | `call(* *.pay(..))` | Phía vị trí gọi |
| Chỉ method execution trả `boolean` | `execution(boolean *.*(..))` | Ràng buộc kiểu trả về |
| Các loại triển khai thuộc họ kiểu | `execution(* com.example.PaymentService+.*(..))` | `+` là subtype pattern |
| Loại trừ một vùng mã | `execution(* *(..)) && !within(com.example.generated..*)` | Phải giữ điều kiện loại trừ đúng ngữ nghĩa |

Không nên sao chép bảng vào dự án chỉ vì biểu thức nhìn hợp lý. Việc chọn đúng join point còn phụ thuộc loại điểm thực thi, giới hạn công cụ và quyền truy cập vào mã cần weaving.

### 13.1. Mẫu “rộng trước, hẹp sau” có rủi ro gì?

Biểu thức như `execution(* *(..))` có thể chạm tới rất nhiều phương thức trong mã được weaving, kể cả những điểm mà advice không nên chạy. Điều này gây overhead và có thể tạo log dày đặc, đệ quy không mong muốn nếu aspect ghi log bằng một phương thức cũng nằm trong tập khớp.

Cần định nghĩa ranh giới rõ: package, kiểu, dấu đánh dấu có ý nghĩa hoặc tập pointcut con đã được kiểm chứng. **Mẫu rộng không phải mặc định tốt nhất** chỉ vì nó giảm số ký tự.

## 14. Điều kiện điểm tĩnh và điều kiện runtime

Wildcard tên/chữ ký chủ yếu mô tả **cấu trúc tĩnh**. Nhưng một số pointcut còn dùng điều kiện runtime như kiểu đối tượng thực, đối số hoặc luồng điều khiển.

Ví dụ:

```aspectj
execution(* com.example.TransferService.transfer(..))
    && args(java.lang.String, long)
```

Biểu thức trên minh họa việc kết hợp **method execution** với điều kiện theo **kiểu/cấu trúc đối số** theo ngữ nghĩa `args` của AspectJ.

Đừng tự coi `args` luôn là cùng một phép so khớp chữ ký `(String,long)`: các designator xét đối tượng hoặc giá trị runtime có thể mang ngữ nghĩa khác với signature pattern; đặc biệt khi có overload, kiểu con, tham số kiểu tham chiếu và dữ liệu được bind.

Nếu yêu cầu là “chỉ khi số tiền vượt 1000”, điều kiện đó phụ thuộc **giá trị thực tế của lần gọi**. Không thể suy ra từ wildcard tên hoặc chữ ký. Cần dùng chức năng pointcut động/advice tương ứng nếu công cụ hỗ trợ và xác định rõ thời điểm kiểm tra.

### 14.1. Chi phí của điều kiện động

Một biểu thức có nhiều điều kiện tĩnh có thể được công cụ tối ưu sớm tại những vị trí không có khả năng khớp. Điều kiện runtime vẫn có thể phải được đánh giá ở mỗi invocation tiềm năng. Với điểm rất “nóng” (hot path), sự khác biệt này ảnh hưởng chi phí thực thi và tần suất advice.

Tránh đưa tác vụ I/O đắt hoặc gọi mạng vào điều kiện chọn điểm nếu không có lý do mạnh. Pointcut nên giữ mục đích rõ; logic phức tạp thường cần được cân nhắc ở lớp chính sách phù hợp và kiểm thử hiệu năng.

## 15. Kiểm thử pointcut: kiểm tra cả tập khớp lẫn tập loại trừ

Một pointcut đúng không chỉ cần bắt được phương thức mong muốn; nó còn phải **không bắt nhầm** những phương thức khác. Trước khi triển khai, lập một bảng các ca khớp và không khớp.

Ví dụ mục tiêu: các method execution `find*` của kiểu `*Repository` dưới `com.example.data`, có danh sách tham số bất kỳ.

Mẫu minh họa:

```aspectj
execution(* com.example.data..*Repository.find*(..))
```

Bảng dự đoán theo tên/chữ ký, giả định các join point có thể được weaving:

| Phương thức | Dự đoán | Lý do |
| --- | --- | --- |
| `com.example.data.OrderRepository.find(String)` | Khớp | Kiểu và tên phương thức phù hợp |
| `com.example.data.archive.ArchiveRepository.findAll()` | Khớp | Có package con, tên `find*` |
| `com.example.data.OrderRepository.save(String)` | Không | Tên không bắt đầu `find` |
| `com.example.api.OrderRepository.find(String)` | Không | Package nằm ngoài mẫu |
| `com.example.data.OrderReader.find(String)` | Không | Tên kiểu không có hậu tố `Repository` |

Bảng chỉ minh họa **điều kiện cấu trúc**, không tự chứng minh behavior runtime. Bước xác minh thực tế là thực hiện những ca gọi tương ứng, quan sát số lần advice chạy và đối chiếu với tập join point mà công cụ thông báo/ghi nhận.

### 15.1. Quy trình debug khi biểu thức không khớp

1. **Đọc chính xác thông báo từ compiler/weaver:** lỗi cú pháp khác với biểu thức hợp lệ nhưng không khớp.
2. **Rút gọn biểu thức:** bắt đầu từ một mẫu chữ ký cụ thể đã biết, sau đó thêm từng wildcard hoặc điều kiện.
3. **Kiểm tra package/declaring type thực tế:** chú ý lớp kế thừa, giao diện, lớp lồng, lớp sinh ra và package con.
4. **Kiểm tra overload/return type/modifier:** cùng tên phương thức chưa chắc cùng chữ ký.
5. **Kiểm tra loại join point:** `call` và `execution` không thay thế nhau.
6. **Kiểm tra phạm vi weaving/interception:** lời gọi đã đi qua điểm công cụ hỗ trợ chưa?
7. **Kiểm tra điều kiện động:** `args`, `this`, `target`, `cflow` nếu có.
8. **Kiểm tra hiệu ứng từ advice khác:** advice bên ngoài có thể từ chối lời gọi hoặc đổi đường thực thi.
9. **Thử ca âm tính:** đảm bảo mẫu không khớp vùng không mong muốn.

Đừng sửa wildcard bằng cách làm nó rộng tối đa ngay khi có lỗi thiếu điểm; điều đó có thể vô tình mở rộng chính sách sang những vùng không được phép.

## 16. Các lỗi phổ biến và cách sửa

**Lỗi 1 — Dùng `(*)` khi muốn mọi danh sách tham số.** Trong signature pattern AspectJ, `(*)` chọn đúng **một** tham số. Dùng `(..)` khi muốn không hoặc nhiều tham số.

**Lỗi 2 — Dùng `*` cho tên kiểu rồi cho rằng có cả package con.** Cần hiểu sự khác nhau giữa `package.*` và `package..*` trong cú pháp mẫu kiểu cụ thể.

**Lỗi 3 — Coi `save*` là Java regex `save.*`.** Có thể cùng mô tả một tiền tố trong trường hợp đơn giản, nhưng ngôn ngữ biểu thức khác nhau; không sao chép nguyên văn qua lại.

**Lỗi 4 — Áp dụng `+` cho mẫu tên phương thức.** `+` được dùng cho quan hệ subtype trong type pattern, không phải cú pháp chung để lặp ký tự của tên.

**Lỗi 5 — Lạm dụng `execution(* *(..))`.** Mẫu quá rộng có thể chạm tới nhiều phương thức, làm tăng chi phí và gây ảnh hưởng ngoài mong muốn.

**Lỗi 6 — Không đặt ngoặc khi kết hợp OR với điều kiện loại trừ.** Một điều kiện `!within(...)` có thể chỉ áp dụng cho một nhánh nếu biểu thức được nhóm sai. Dùng `(...)` rõ ràng.

**Lỗi 7 — Nhầm `within` với việc đối tượng runtime thuộc kiểu nào.** `within` mô tả phạm vi từ vựng của mã ở join point, còn `this/target` mang ngữ nghĩa theo đối tượng phù hợp.

**Lỗi 8 — Nhầm call và execution.** Cùng tên phương thức có thể tạo các điểm ở hai phía khác nhau; cần xác định thiết kế muốn chặn vị trí gọi hay phần thực thi.

**Lỗi 9 — Chỉ kiểm thử các điểm phải khớp.** Cần kiểm thử cả điểm **không được khớp**; wildcard rộng có thể gây tác động ngoài ý muốn.

**Lỗi 10 — Quên khả năng của công cụ.** Biểu thức cú pháp đúng không bảo đảm mọi lớp được weaving hoặc mọi đường gọi đi qua proxy; đặc biệt lưu ý self-invocation và mã thư viện.

## 17. Câu hỏi luyện tập có đáp án

**Câu 1. Trong cú pháp AspectJ, `execution(* *.save(*))` có chọn `save()` không?**

Không. Vùng tham số `(*)` yêu cầu đúng một tham số; `save()` có không tham số. Nếu muốn khớp mọi số tham số, dùng `(..)`.

**Câu 2. Khác biệt giữa `com.example.api.*` và `com.example.api..*` là gì?**

Trong mẫu kiểu AspectJ, dạng có `..` có thể bao phủ các mức package con phù hợp, còn dạng `.*` ở cuối chỉ diễn đạt một thành phần tên kiểu trực tiếp theo mẫu.

**Câu 3. Trong `execution(* com.example.*Service.find*(java.lang.String,..))`, dấu `*` đầu tiên nghĩa gì?**

Nó ở vị trí **kiểu trả về**, nên cho phép kiểu trả về bất kỳ. `*Service` lại là phần mẫu tên kiểu, `find*` là phần mẫu tên phương thức.

**Câu 4. `PaymentService+` dùng để làm gì?**

Đây là subtype type pattern của AspectJ, mô tả `PaymentService` và các subtype phù hợp. Nó không phải quy tắc “mọi tên bắt đầu bằng PaymentService”.

**Câu 5. Tại sao `execution(* *.pay(..))` và `call(* *.pay(..))` có thể quan sát khác nhau?**

Vì `execution` mô tả việc thực thi thân phương thức, còn `call` mô tả vị trí gọi. Một phương thức có thể có nhiều call sites, trong khi execution thuộc ranh giới phần thân được gọi.

**Câu 6. Viết một mẫu theo cú pháp AspectJ để chọn method execution có tên bắt đầu bằng `find`, trong các kiểu kết thúc bằng `Repository` dưới package `com.example.data` và các package con.**

```aspectj
execution(* com.example.data..*Repository.find*(..))
```

Mẫu này cần được kiểm tra với các kiểu/chữ ký thật và phạm vi weaving thực tế trước khi dùng cho chính sách quan trọng.

**Câu 7. Một pointcut theo tên đã khớp nhưng advice không chạy. Có thể kết luận cú pháp wildcard sai không?**

Không. Cần kiểm tra loại join point, điều kiện động, phạm vi mã được weaving, đường đi qua proxy và các advice bao ngoài. “Khớp mẫu chữ ký” mới là một phần của điều kiện để advice thực sự chạy.

## 18. Ghi nhớ và nguồn tham khảo cú pháp

- **Wildcard là thành phần của ngữ pháp, không phải lời hứa rằng mọi ký tự hay mọi hành vi đều được khớp.**
- Trong ví dụ AspectJ, `*` đại diện cho tên/kiểu theo vị trí, `..` dùng cho phạm vi package hoặc số tham số tùy ngữ cảnh, `+` mở rộng subtype, và `&&`, `||`, `!` kết hợp các điều kiện.
- **Mẫu tĩnh và điều kiện động bổ sung cho nhau**: một biểu thức có thể hợp lệ nhưng không áp dụng cho mọi lần chạy.
- **Công cụ quyết định cú pháp lẫn khả năng can thiệp**. Khi chuyển sang một công cụ AOP khác, cần đọc lại ngữ pháp và loại join point được hỗ trợ.
- **Đánh giá pointcut bằng cả ca dương tính lẫn âm tính**, quan sát hành vi khi chạy và kiểm tra đúng phạm vi weaving.

Tham khảo để xác minh **riêng cú pháp AspectJ** được minh họa trong bài:

- [AspectJ Quick Reference — Pointcuts, Type Patterns, Advice](https://eclipse.dev/aspectj/doc/latest/progguide/quickreference.html)
- [AspectJ Programming Guide — Pointcuts và Pattern Syntax](https://eclipse.dev/aspectj/doc/released/progguide/semantics-pointcuts.html)

Hai tài liệu này mô tả cú pháp/khả năng của **một công cụ AOP cụ thể**. Ý tưởng tổng quát cần giữ là: chọn đúng điểm, hiểu đúng cách biểu thức mẫu hoạt động, kiểm chứng được hành vi thực tế và tránh áp dụng advice ngoài phạm vi đã thiết kế.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-basics-007">Kết hợp điều kiện bằng phép và, hoặc và phủ định</a>

<details>
<summary>Xem chi tiết</summary>

# Kết hợp điều kiện bằng phép và, hoặc và phủ định trong Pointcut

## Mục tiêu bài học

Một **pointcut** mô tả **tập join point** mà tại đó hành vi xuyên suốt (*advice*) sẽ được áp dụng. Trên một hệ thống nhỏ, tiêu chí “mọi lần thực thi của phương thức được đánh dấu cần quan sát” có thể đủ. Trong chương trình thực tế, yêu cầu thường chi tiết hơn: “theo dõi các thao tác ghi **hoặc** các thao tác có marker, **nhưng không** theo dõi mã nội bộ của hệ thống đo lường”. Khi đó, cần **kết hợp điều kiện** bằng ba phép toán logic:

- **VÀ (AND):** Cả hai điều kiện đều đúng.
- **HOẶC (OR):** Ít nhất một trong các điều kiện đúng.
- **PHỦ ĐỊNH (NOT):** Điều kiện được phủ định không đúng.

Ví dụ khái niệm:

```text
(chọn thao tác ghi OR chọn thao tác có marker)
AND NOT chọn thao tác nội bộ
```

Đây là **giả mã** minh họa tư duy, không phải cú pháp pointcut có thể dán vào mọi công cụ AOP. Các công cụ có thể dùng `&&`, `||`, `!`, từ khóa `and/or/not`, API predicate hoặc cú pháp khác. Điều phải giữ nguyên là **ngữ nghĩa chọn tập sự kiện** và quy tắc nhóm bằng dấu ngoặc.

Sau bài này, bạn có thể tự thiết kế một pointcut ghép, dựng bảng chân trị, dùng De Morgan để diễn giải phủ định, phát hiện điều kiện mâu thuẫn/quá rộng, hiểu giới hạn của phép NOT, và kiểm thử một ví dụ Java 17 hoạt động ở runtime.

## 1. Điều kiện pointcut áp dụng lên cái gì?

AOP coi một **join point** là một điểm/sự kiện có thể can thiệp trong quá trình chạy, chẳng hạn một lần *method execution* hoặc một lần *method call*, tùy **join-point model** của công cụ. Một **pointcut** chọn các điểm trong phạm vi mà công cụ hỗ trợ.

Hãy ký hiệu:

- \(U\): tập các join point mà ta đang xét và cơ chế có khả năng nhận diện.
- \(A\): tập các join point thỏa quy tắc A.
- \(B\): tập các join point thỏa quy tắc B.
- \(C\): tập các join point thỏa quy tắc C.

Ví dụ:

- \(A\) = các lần thực thi phương thức thuộc nhóm *storage*.
- \(B\) = các lần thực thi phương thức được đánh dấu cần theo dõi.
- \(C\) = các lần thực thi phương thức thuộc nhóm *internal instrumentation*.

Mỗi quy tắc tương ứng với một mệnh đề đúng/sai tại **cùng một join point đang xét**. Điều kiện ghép có ý nghĩa là **lọc giao, hợp hoặc phần bù** của các tập trong \(U\).

Quan trọng: \(U\) **không phải tập mọi dòng mã trong dự án**. Nó chỉ bao gồm những điểm thuộc loại được mô hình AOP/cơ chế đang xét hỗ trợ. Một dynamic proxy theo interface có thể quan sát lời gọi đi **qua proxy**, nhưng không tự biến mọi method body và mọi vị trí gọi trong chương trình thành điểm có thể chặn. Vì vậy, `NOT A` không thể làm xuất hiện join point ở những nơi công cụ vốn không quan sát được.

### 1.1. Một biểu thức tĩnh có thể khớp nhiều lần thực thi

Đoạn mã:

```java
for (int i = 0; i < 4; i++) {
    repository.save(i);
}
```

Chỉ có **một vị trí gọi** trong source nhưng có thể có **bốn sự kiện gọi ở runtime**. Nếu loại join point và đường can thiệp phù hợp, pointcut ghép sẽ được áp dụng đối với những lần thực sự xảy ra. Điều kiện động (ví dụ đối số `i > 1`) có thể đúng ở một số lượt và sai ở những lượt còn lại.

Phép toán logic nói “**sự kiện nào được chọn**”, không tự quyết định “**sự kiện sẽ xảy ra bao nhiêu lần**”.

## 2. Phép VÀ (AND): lấy phần giao

Định nghĩa trên từng join point:

```text
A AND B = true   chỉ khi A=true và B=true
```

Theo tập hợp: \(A \land B\) chọn \(A \cap B\).

| A | B | A AND B |
| --- | --- | --- |
| false | false | false |
| false | true | false |
| true | false | false |
| true | true | true |

### 2.1. Ví dụ: vừa thuộc phạm vi, vừa có marker

Giả sử A là “method thuộc nhóm thao tác nhập dữ liệu”, B là “method có marker `@Observed`”. Khi dùng `A AND B`, chỉ những method **đồng thời** đáp ứng hai tiêu chí mới được chọn.

Điều này chặt hơn từng tiêu chí riêng:

- Một thao tác nhập dữ liệu **không** có marker → bị loại.
- Một method có marker nhưng thuộc nhóm đo đạc nội bộ → bị loại nếu nó không thuộc nhóm nhập dữ liệu.
- Một thao tác nhập dữ liệu có marker → được chọn, nếu đúng loại join point và đường can thiệp được hỗ trợ.

Giả mã:

```text
execution(method thuộc nhóm import)
AND methodHasMarker("Observed")
```

Từ `execution`, `methodHasMarker` ở đây là tên tự mô tả để minh họa; **không phải designator chuẩn cho mọi ngôn ngữ/công cụ AOP**.

### 2.2. Hai điều kiện có thể luôn mâu thuẫn

Nếu A chọn **method-call join point**, B chọn **method-execution join point**, trong một mô hình phân biệt hai loại sự kiện loại trừ nhau, thì `A AND B` không thể đúng với cùng một join point. Việc cả hai có liên quan đến “cùng method” không khiến chúng trở thành cùng loại sự kiện.

Tương tự:

```text
A AND NOT A
```

luôn sai với cùng một sự kiện nếu A là predicate logic nhất quán. Kết quả là **pointcut không chọn điểm nào**, không có nghĩa advice của công cụ đang bị hỏng.

### 2.3. Lưu ý về điều kiện động

Với yêu cầu “method được đánh dấu **và** giá trị đối số phải hợp lệ”, phần marker có thể nhận biết từ metadata, nhưng giá trị đối số chỉ biết theo từng lời gọi runtime. Không phải mọi hệ thống AOP đều hỗ trợ matcher đối số động theo cùng cách. Phép AND là tư duy logic chung; khả năng truy cập đối số và thời điểm đánh giá tùy công nghệ.

## 3. Phép HOẶC (OR): lấy phần hợp

Định nghĩa:

```text
A OR B = true   khi A=true, B=true, hoặc cả hai cùng true
```

Theo tập hợp: \(A \lor B\) chọn \(A \cup B\).

| A | B | A OR B |
| --- | --- | --- |
| false | false | false |
| false | true | true |
| true | false | true |
| true | true | true |

### 3.1. Ví dụ: chọn theo nhóm hoặc marker

Giả sử:

- A = các lần thực thi method có tên bắt đầu bằng `create`.
- B = các lần thực thi method được đánh dấu `@Tracked`.

Biểu thức:

```text
methodNameStartsWith("create")
OR methodHasMarker("Tracked")
```

sẽ chọn **cả** phương thức `createDraft` không có marker **lẫn** phương thức `submit` có marker. Một phương thức `createInvoice` đồng thời có marker chỉ được kết luận là **khớp biểu thức OR**; bản thân biểu thức Boolean không nói rằng advice phải chạy **hai lần**.

**Phân biệt hai việc:**

1. **Một pointcut ghép** `A OR B` gắn với **một advice**: nếu join point khớp cả A và B, về mặt logic nó vẫn là một điểm được chọn bởi pointcut ghép.
2. **Hai advice/aspect riêng biệt**, một gắn A và một gắn B: nếu cùng join point khớp cả hai, **cả hai advice đều có thể chạy**. Tần suất và thứ tự thực tế còn phụ thuộc engine và cấu hình.

Đếm “hai điều kiện true” rồi suy ra “hai lần chạy của một advice” là lỗi logic.

### 3.2. OR thường mở rộng phạm vi ngoài dự tính

Điều kiện `A OR B` chọn **nhiều hoặc bằng** từng tập A, B. Nếu B vô tình là “mọi method public”, OR gần như nuốt toàn bộ giới hạn ở A đối với method public. Cần rà soát nhánh OR có phạm vi rộng nhất.

Một tiêu chí OR cũng có thể đưa vào những method vốn phải loại trừ: nhánh mới thêm sau này có thể khớp chức năng nội bộ, mã chứa dữ liệu nhạy cảm hoặc đường cực kỳ nóng. Vì vậy, trong nhiều thiết kế, nên đặt điều kiện loại trừ **ngoài toàn bộ nhóm OR**.

## 4. Phép PHỦ ĐỊNH (NOT): lấy phần bù trong tập được xét

Định nghĩa:

```text
NOT A = true   khi A=false
```

| A | NOT A |
| --- | --- |
| false | true |
| true | false |

Theo tập hợp, NOT A là \(U \setminus A\), phần bù của A **trong tập \(U\)**.

### 4.1. Dùng NOT để loại trừ mã nội bộ

Ví dụ: cần ghi log cho thao tác có marker nhưng không ghi log cho chính bộ logging.

```text
methodHasMarker("Observed")
AND NOT belongsToInternalLogging()
```

Đây là biểu thức phạm vi có ích vì nó tránh một số vòng lặp reentrant:

```text
advice -> logger -> method nội bộ -> advice -> logger -> ...
```

Tuy nhiên, không phải chỉ cần một phép NOT là đủ bảo đảm tránh mọi đệ quy. Phải xác định logger gọi những method nào, công cụ đang chặn loại join point nào, và liệu có đường gọi phụ trợ khác vẫn lọt vào pointcut.

### 4.2. NOT đơn lẻ rất dễ quá rộng

```text
NOT methodHasMarker("Internal")
```

có thể chọn **mọi join point mà công cụ thấy được** không có marker nội bộ: method nghiệp vụ, utility, thao tác hệ thống, mã mới thêm sau này... Nếu mục đích chỉ là chọn phương thức xử lý lệnh ở module cụ thể, nên giới hạn dương trước:

```text
belongsToModule("command")
AND NOT methodHasMarker("Internal")
```

NOT thường có giá trị cao nhất khi dùng như **điều kiện loại trừ rõ ràng** sau một tập chọn dương đủ hẹp.

### 4.3. NOT không mở rộng năng lực can thiệp

Nếu công cụ chỉ bắt lời gọi qua proxy, `NOT withinInternalModule()` không làm nó chặn được lời gọi trực tiếp đến target hay field access. Nếu một loại join point chưa được hỗ trợ, phủ định một điều kiện trên loại đó cũng không “đưa loại đó vào \(U\)”. Mọi phép phủ định đều phải hiểu tương đối với **mô hình join point và phạm vi weaving**.

## 5. Kết hợp cả ba phép toán: dấu ngoặc quyết định tập kết quả

Xét ba điều kiện:

- **A:** Tên method bắt đầu bằng `create`.
- **B:** Method được gắn marker `@Tracked`.
- **C:** Method được gắn marker `@Internal`.

Yêu cầu: “Chọn method thuộc nhóm tạo **hoặc** có marker theo dõi, **trừ** method nội bộ”.

Viết đúng ý định:

```text
(A OR B) AND (NOT C)
```

Không đồng nghĩa với:

```text
A OR (B AND (NOT C))
```

Trong biểu thức thứ hai, nhánh A **không chịu** điều kiện loại trừ C: một method `createInternal` vừa thỏa A vừa thỏa C vẫn được chọn.

### 5.1. Bảng chân trị để phát hiện sai lầm

| A | B | C | (A OR B) AND NOT C | A OR (B AND NOT C) |
| --- | --- | --- | --- | --- |
| false | false | false | false | false |
| false | false | true | false | false |
| false | true | false | true | true |
| false | true | true | false | false |
| true | false | false | true | true |
| true | false | true | **false** | **true** |
| true | true | false | true | true |
| true | true | true | **false** | **true** |

Hai hàng có `A=true`, `C=true` cho thấy hậu quả: biểu thức thứ hai **bỏ qua loại trừ nội bộ** đối với cả nhóm create.

### 5.2. Độ ưu tiên và dấu ngoặc

Trong đại số Boolean và những cú pháp thông dụng có ưu tiên toán tử chuẩn, thứ tự thường là:

1. **NOT** áp lên biểu thức ngay sau nó.
2. **AND** liên kết hai mệnh đề.
3. **OR** hợp các mệnh đề.

Vì vậy `A OR B AND NOT C` thường được hiểu là `A OR (B AND (NOT C))`, **không phải** `(A OR B) AND (NOT C)`. Tuy nhiên, không giả định cú pháp hoặc độ ưu tiên của mọi ngôn ngữ pointcut đều giống Java. Đối với pointcut có nhiều toán tử, **luôn dùng dấu ngoặc tường minh** theo đúng tài liệu của công cụ.

Về ý nghĩa, hãy đọc từ ngoài vào trong: “**chọn (A hoặc B), sau đó loại trừ C**”. Cách diễn đạt này thường giúp nhận ra chỗ đặt ngoặc đúng.

### 5.3. Dấu ngoặc không quy định thứ tự chạy advice

`(A OR B) AND NOT C` là **điều kiện lựa chọn**; nó không nói “chạy A trước B”, “chạy B sau C” hay “AOP sẽ kiểm tra runtime theo thứ tự trái sang phải”. Thứ tự chạy nhiều advice do **precedence/order** và cơ chế weaving quyết định.

Trong một phép tính Boolean Java thuần dùng `&&`/`||`, ngôn ngữ có **short-circuit evaluation** từ trái qua phải. Nhưng không nên áp quy tắc đánh giá biểu thức Java đó cho mọi pointcut DSL hoặc engine tối ưu/weave matcher. Một công cụ có thể giản lược điều kiện tĩnh, sắp xếp lại bộ lọc nội bộ hoặc biến đổi thành cấu trúc khác miễn vẫn giữ **ngữ nghĩa matching** mà nó công bố.

**Không viết pointcut với ý định tạo side effect dựa trên thứ tự đánh giá predicate**, vì pointcut là công cụ chọn điểm, không phải luồng xử lý nghiệp vụ.

## 6. Các luật Boolean hữu ích khi thiết kế pointcut

Các luật dưới đây đúng với **predicate logic thuần**, tức điều kiện biểu diễn mệnh đề đúng/sai nhất quán trên cùng vũ trụ join point. Kết quả áp dụng thực tế vẫn phải xét năng lực matcher/công cụ.

| Luật | Biểu thức tương đương | Ý nghĩa với pointcut |
| --- | --- | --- |
| Giao hoán AND | `A AND B = B AND A` | Đổi thứ tự hai tiêu chí không đổi tập join point được chọn. |
| Giao hoán OR | `A OR B = B OR A` | Đổi nhánh OR không đổi tập được chọn. |
| Kết hợp AND | `(A AND B) AND C = A AND (B AND C)` | Có thể nhóm lại các tiêu chí bắt buộc. |
| Kết hợp OR | `(A OR B) OR C = A OR (B OR C)` | Có thể nhóm lại các tập chấp nhận. |
| Phân phối | `A AND (B OR C) = (A AND B) OR (A AND C)` | Một giới hạn A áp dụng lên cả hai nhánh B, C. |
| Hấp thụ | `A OR (A AND B) = A` | Nhánh `A AND B` thừa khi đã có A. |
| Lũy đẳng | `A AND A = A`; `A OR A = A` | Lặp lại tiêu chí không làm nhân số lần chạy advice. |
| Bù trừ | `A AND NOT A = false`; `A OR NOT A = true` | Điều kiện luôn sai hoặc luôn đúng trong vũ trụ đang xét. |
| Phủ định kép | `NOT (NOT A) = A` | Hai lần phủ định triệt tiêu nhau. |

### 6.1. Hai định luật De Morgan

```text
NOT (A OR B) = (NOT A) AND (NOT B)
NOT (A AND B) = (NOT A) OR (NOT B)
```

Đọc bằng lời:

- **Không thuộc A hoặc B** nghĩa là **không thuộc A và cũng không thuộc B**.
- **Không đồng thời thuộc A và B** nghĩa là **ít nhất một trong hai điều kiện không đúng**.

Ví dụ muốn loại trừ **cả hai nhóm** `Internal` và `Generated`, cách diễn đạt chính xác là:

```text
base
AND NOT (Internal OR Generated)
```

tương đương:

```text
base
AND NOT Internal
AND NOT Generated
```

Cách viết sai thường thấy:

```text
base
AND (NOT Internal OR NOT Generated)
```

Biểu thức sai chỉ loại khi **đồng thời** Internal và Generated, còn phần lớn trường hợp chỉ thuộc một nhóm vẫn được chọn.

### 6.2. Cần phân biệt tương đương logic và khả năng biên dịch

Hai biểu thức có thể tương đương trên giấy nhưng công cụ AOP có thể giới hạn vị trí xuất hiện của toán tử, kiểu designator, ràng buộc biến hoặc binding context. Ví dụ, một số matcher cần một binding nhất quán trong các nhánh OR; tên biến lấy từ một nhánh có thể không sẵn ở nhánh kia. Vì vậy:

1. Chứng minh hai biểu thức cùng tập matching về mặt logic.
2. Xác minh **cú pháp, binding và join-point model** cho phép biểu diễn chúng.
3. Kiểm thử bằng các điểm thuộc/không thuộc phạm vi mong muốn.

Không sửa một điểm cắt chỉ vì “rút gọn Boolean chắc chắn tương đương” mà bỏ qua quy tắc binding cụ thể.

## 7. Biểu thức ghép của pointcut không giống điều kiện nghiệp vụ

Một pointcut chọn **khi nào advice được kích hoạt**. Nó không thay thế quy tắc tính tiền, quyết định trạng thái hay kiểm tra toàn vẹn dữ liệu nghiệp vụ.

Ví dụ điều kiện nghiệp vụ:

```text
khách có quyền sửa tài liệu
AND tài liệu chưa khóa
AND hạn cập nhật chưa hết
```

Đây là **quy tắc cấp quyền/thay đổi trạng thái** của domain. Dù có thể biểu diễn bằng AND/OR/NOT, đặt nó hoàn toàn trong advice vô hình sẽ làm khó truy vết và dễ tạo lỗ hổng nếu có đường gọi bypass cơ chế AOP. Hãy dùng domain service/policy và kiểm tra tại biên tin cậy; nếu cần, AOP có thể bổ sung xử lý kỹ thuật ở những điểm đã chứng minh được phạm vi.

Một điểm cắt phù hợp hơn:

```text
(method thuộc nhóm quan sát OR có marker "Observed")
AND NOT method nội bộ của bộ đo
```

Biểu thức này nói về **phạm vi thu thập quan sát**, không phải cách quyết định một kết quả nghiệp vụ.

## 8. Ví dụ Java 17 chạy được: dùng OR rồi AND NOT tại runtime

Java tiêu chuẩn không tự cung cấp một engine pointcut tổng quát. Ta có thể mô phỏng cách **kết hợp predicate** thông qua `java.lang.reflect.Proxy`: mỗi lần caller gọi method qua proxy, handler đánh giá quy tắc:

```text
(A OR B) AND NOT C

A = tên method bắt đầu bằng "create"
B = method interface có @Tracked
C = method interface có @Internal
```

**Mục đích của ví dụ** là chứng minh kết hợp điều kiện, thứ tự chọn điểm và giới hạn call path. Nó **không** có ngôn ngữ pointcut đầy đủ, không hỗ trợ mọi join point và không biểu diễn được năng lực của các cơ chế weaving khác.

Tạo tệp `PointcutBooleanDemo.java`:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

public class PointcutBooleanDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Tracked { }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Internal { }

    interface JobApi {
        @Tracked
        String submit(String id);

        String createDraft(String id);

        @Tracked
        @Internal
        String internalSync();

        @Internal
        String createInternal(String id);

        String status();
    }

    static final class JobService implements JobApi {
        @Override
        public String submit(String id) {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("blank id");
            }
            System.out.println("TARGET submit " + id);
            return "ACCEPTED:" + id;
        }

        @Override
        public String createDraft(String id) {
            System.out.println("TARGET createDraft " + id);
            return "DRAFT:" + id;
        }

        @Override
        public String internalSync() {
            System.out.println("TARGET internalSync");
            return "SYNCED";
        }

        @Override
        public String createInternal(String id) {
            System.out.println("TARGET createInternal " + id);
            return "INTERNAL:" + id;
        }

        @Override
        public String status() {
            System.out.println("TARGET status");
            return "READY";
        }
    }

    static JobApi observed(JobApi target) {
        return (JobApi) Proxy.newProxyInstance(
                JobApi.class.getClassLoader(),
                new Class<?>[] { JobApi.class },
                (proxy, method, args) -> {
                    boolean a = method.getName().startsWith("create");
                    boolean b = method.isAnnotationPresent(Tracked.class);
                    boolean c = method.isAnnotationPresent(Internal.class);
                    boolean selected = (a || b) && !c;

                    System.out.println("SELECT " + method.getName()
                            + "=" + selected);
                    if (selected) {
                        System.out.println("ADVICE before " + method.getName());
                    }

                    try {
                        Object result = method.invoke(target, args);
                        if (selected) {
                            System.out.println("ADVICE returning "
                                    + method.getName());
                        }
                        return result;
                    } catch (InvocationTargetException ex) {
                        if (selected) {
                            System.out.println("ADVICE throwing "
                                    + method.getName());
                        }
                        throw ex.getCause();
                    }
                }
        );
    }

    static void show(String result) {
        System.out.println("RESULT " + result);
    }

    public static void main(String[] args) {
        JobApi target = new JobService();
        JobApi proxy = observed(target);

        show(proxy.submit("P1"));
        show(proxy.createDraft("D2"));
        show(proxy.internalSync());
        show(proxy.createInternal("D3"));
        show(proxy.status());

        try {
            proxy.submit("");
        } catch (IllegalArgumentException ex) {
            System.out.println("CALLER " + ex.getMessage());
        }

        show(target.submit("P2"));
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac PointcutBooleanDemo.java
java PointcutBooleanDemo
```

Kết quả dự kiến:

```text
SELECT submit=true
ADVICE before submit
TARGET submit P1
ADVICE returning submit
RESULT ACCEPTED:P1
SELECT createDraft=true
ADVICE before createDraft
TARGET createDraft D2
ADVICE returning createDraft
RESULT DRAFT:D2
SELECT internalSync=false
TARGET internalSync
RESULT SYNCED
SELECT createInternal=false
TARGET createInternal D3
RESULT INTERNAL:D3
SELECT status=false
TARGET status
RESULT READY
SELECT submit=true
ADVICE before submit
ADVICE throwing submit
CALLER blank id
TARGET submit P2
RESULT ACCEPTED:P2
```

### 8.1. Tính từng dòng của quy tắc

| Method | A: tên `create...` | B: `@Tracked` | C: `@Internal` | `(A OR B) AND NOT C` |
| --- | --- | --- | --- | --- |
| `submit` | false | true | false | **true** |
| `createDraft` | true | false | false | **true** |
| `internalSync` | false | true | true | **false** |
| `createInternal` | true | false | true | **false** |
| `status` | false | false | false | **false** |

- `submit` được chọn nhờ **B** dù không thỏa A.
- `createDraft` được chọn nhờ **A** dù không thỏa B.
- `internalSync` có B nhưng bị **NOT C** loại.
- `createInternal` có A nhưng vẫn bị **NOT C** loại. Đây chính là ca giúp phát hiện viết sai thành `A OR (B AND NOT C)`.
- `status` không thỏa A hay B nên không được chọn.

Handler in `SELECT` cho **mọi method gọi qua proxy** để phục vụ bài học. Trong ứng dụng thật, chi phí của một handler và cách bỏ qua điểm không khớp có thể khác.

### 8.2. Vì sao `submit("")` vẫn phát sinh advice?

Điều kiện ghép chỉ dựa vào tên method và annotation, **không xét giá trị `id`**. `submit("")` vẫn thỏa B và không thỏa C, nên advice trước chạy; target kiểm tra `id` và ném `IllegalArgumentException`; handler ghi `ADVICE throwing` rồi truyền lại nguyên nhân gốc từ `InvocationTargetException`.

Từ đây thấy rõ hai lớp logic:

- **Pointcut:** chọn nơi advice chạy.
- **Business validation:** quyết định dữ liệu `id` có hợp lệ hay không.

Đừng biến pointcut thành nơi ẩn tất cả quy tắc nghiệp vụ.

### 8.3. Vì sao `target.submit("P2")` không in SELECT/ADVICE?

Lời gọi này đi thẳng vào target, **không qua proxy**. Mặc dù phương thức `submit` trên interface có marker, handler không nhận được sự kiện. Đây là **giới hạn của cách dynamic proxy ở ví dụ**, không phải khẳng định mọi công cụ AOP chỉ quan sát được lời gọi qua proxy.

### 8.4. Annotation ở interface, không phải tự kế thừa lên implementation

`Method` được handler nhận trong ví dụ là method của interface proxy. Điều kiện `method.isAnnotationPresent(...)` tra metadata ở đó. Nếu chuyển `@Tracked` hoặc `@Internal` sang implementation mà **không sửa matcher**, kết quả có thể thay đổi. Cơ chế weaving hoặc matcher khác có thể có quy tắc tìm metadata khác. Khi kiểm thử pointcut theo marker, hãy xác định **nơi annotation được khai báo và nơi matcher tìm kiếm**.

### 8.5. Một bài thực hành sửa ngoặc

Thử đổi đúng một dòng:

```java
boolean selected = a || (b && !c);
```

Bây giờ `createInternal` thỏa A nên được chọn, mặc dù C=true. Bạn sẽ thấy `SELECT createInternal=true` cùng dòng advice trước/sau cho method ấy. Hãy giữ nguyên dữ liệu thử, so sánh kết quả và giải thích bằng hàng chân trị A=true, B=false, C=true. Đây là thử nghiệm minh họa **thay đổi tập matching khi đổi dấu ngoặc**, không phải khuyến khích dùng biểu thức sai.

## 9. Khi kết hợp điều kiện tĩnh và điều kiện động

Công cụ AOP có thể chọn điểm dựa trên:

- **Thông tin tĩnh:** chữ ký method, loại join point, package, tên lớp, visibility, annotation đã được nhận diện ở build/weaving.
- **Thông tin động:** receiver thực, đối số, trạng thái runtime, context hoặc cflow nếu công cụ hỗ trợ.

Giả sử muốn theo dõi nhóm method có marker **và** những lượt có flag `debug=true`:

```text
hasMarker("Tracked")
AND runtimeDebugFlagIsTrue
```

Trong một mô hình hỗ trợ cả hai, cấu trúc method có thể là ứng viên tĩnh nhưng điều kiện debug phải kiểm tra theo **từng lần thực thi**. Nếu có 20 lượt gọi cùng method và debug chỉ true ở 5 lượt, advice sẽ được kích hoạt theo điều kiện đó ở 5 lượt, giả sử mọi lời gọi đi qua cơ chế can thiệp và không có điều kiện phụ khác.

### 9.1. OR giữa điều kiện tĩnh và động

```text
hasMarker("Critical")
OR runtimeContext("sampled") == true
```

Nếu nhánh marker luôn true cho một method, ý nghĩa logic không đòi hỏi nhánh `sampled` ảnh hưởng kết quả chọn method đó. Nhưng bạn **không được giả định** engine đánh giá nhánh động bao nhiêu lần hoặc theo thứ tự nào, trừ khi công cụ có hợp đồng về hành vi ấy.

### 9.2. Binding giá trị cần thống nhất giữa các nhánh

Một số DSL pointcut cho phép “bind” đối số, target hoặc annotation vào tên biến được advice dùng. Khi kết hợp OR, giá trị chỉ được bind ở một nhánh có thể không tồn tại trong nhánh kia. Công cụ có thể từ chối biểu thức hoặc yêu cầu binding tương thích ở mọi nhánh; không phải chỉ ghép Boolean là hợp lệ.

Ví dụ ý tưởng nguy hiểm:

```text
( matchArgument(x) OR matchMarker("Tracked") )
-> advice dùng x
```

Nếu sự kiện chỉ khớp nhánh marker, `x` đến từ đâu? Phải xử lý theo quy tắc binding cụ thể, hoặc tách pointcut/advice để hợp đồng dữ liệu rõ ràng.

## 10. Kiểu dữ liệu của điều kiện: đừng ghép các loại join point không tương thích

Các điểm trong AOP có thể được định nghĩa là method call, method execution, field get/set, constructor execution hoặc sự kiện khác, **tùy khả năng công cụ**. Nếu cùng một sự kiện không thể vừa là “call” vừa là “execution” theo model đang dùng, biểu thức:

```text
callOf("save") AND executionOf("save")
```

thường có **tập giao rỗng** dù lời gọi và thực thi liên quan đến cùng method. Tên giống nhau không đồng nghĩa hai predicate đúng tại cùng một join point.

Trong khi đó:

```text
executionOf("save") AND insideModule("storage")
```

có thể là một tập giao có nghĩa nếu cả hai matcher áp lên cùng loại điểm.

Một quy tắc OR giữa hai loại điểm khác nhau có thể có nghĩa là “chọn điểm call **hoặc** execution” nếu công cụ cho phép. Nhưng điều đó có thể dẫn tới **nhiều điểm can thiệp cho một hành động logic** (một call và một execution); hãy đánh giá ý định ghi log và khả năng đếm trùng.

## 11. Thiết kế pointcut ghép theo quy trình có thể kiểm chứng

### Bước 1: Viết yêu cầu bằng lời trước

Ví dụ: “Ghi latency cho method có marker `@Observed` **hoặc** thuộc nhóm thao tác nhập dữ liệu, **ngoại trừ** method trong module logging nội bộ”.

Tách thành các mệnh đề:

```text
A = có marker Observed
B = thuộc nhóm import
C = thuộc module logging nội bộ

Kết quả cần: (A OR B) AND NOT C
```

Cách viết thành ngôn ngữ tự nhiên giúp tránh bỏ sót **phạm vi phủ định**.

### Bước 2: Chọn vũ trụ join point

Nêu rõ đang chọn **method execution** hay **method call** hoặc sự kiện khác. Xác nhận ngôn ngữ/công cụ hỗ trợ loại đó. Xác định liệu điểm mong muốn đi qua proxy, đã được weave hay thuộc cơ chế interception khác. Nếu không, dù Boolean đúng vẫn không có advice ở runtime.

### Bước 3: Chỉ rõ từng predicate

Mỗi mệnh đề cần một định nghĩa có thể kiểm tra:

- “Thuộc module import” có nghĩa gì: package, namespace, class, marker hay danh sách explicit?
- “Có marker” được kiểm tra ở declaration nào: interface, implementation hay inherited method?
- “Nội bộ” gồm cả generated code, logging helper và tác vụ background không?
- Có cần điều kiện runtime theo tham số, loại đối tượng hoặc request context không?

Định nghĩa mơ hồ tạo pointcut mơ hồ.

### Bước 4: Dùng dấu ngoặc thể hiện ý định

Viết đầy đủ `(A OR B) AND (NOT C)` thay vì `A OR B AND NOT C`. Với biểu thức dài, tách các nhóm con thành tên có nghĩa nếu DSL hoặc công cụ hỗ trợ; đừng nhân bản một điều kiện phức tạp khắp nơi.

### Bước 5: Dựng bảng ca kiểm thử tối thiểu

Một điểm cắt có OR và NOT thường cần ít nhất:

| A | B | C | Dự kiến |
| --- | --- | --- | --- |
| false | false | false | Loại vì không đạt nhánh dương. |
| true | false | false | Chọn nhờ A. |
| false | true | false | Chọn nhờ B. |
| true | true | false | Chọn; một pointcut Boolean đã khớp. |
| true | false | true | Loại vì C. |
| false | true | true | Loại vì C. |
| true | true | true | Loại vì C. |

Trường hợp `A=true, C=true` đặc biệt quan trọng vì dễ lộ lỗi dấu ngoặc.

### Bước 6: Kiểm chứng trên đường chạy thực tế

Chạy integration test qua cơ chế AOP thật. Gồm:

- **Positive:** điểm đúng phải nhận advice; ghi nhận lần và thứ tự phù hợp.
- **Negative:** điểm không thuộc rule không được tác động; đặc biệt các loại trừ.
- **Boundary:** overloaded/inherited method, marker tại interface hoặc implementation, lời gọi nội bộ, direct target, async.
- **Error path:** target ném lỗi; advice ghi nhận đúng nhưng không vô tình đổi exception.
- **Load:** tập điểm rộng có tạo logging quá nhiều hoặc overhead vượt ngân sách không?

Số dòng source khớp không chứng minh tần suất advice runtime; muốn đánh giá chi phí phải đo trên workload thực.

## 12. Những lỗi thường gặp khi dùng AND/OR/NOT

### 12.1. Quên ngoặc khi loại trừ

Sai mục tiêu:

```text
A OR B AND NOT C
```

Nếu ngữ nghĩa ưu tiên AND trước OR, nhánh A không bị loại bởi C. Viết:

```text
(A OR B) AND NOT C
```

để loại C khỏi **toàn bộ** tập được chọn.

### 12.2. Dùng OR thay AND

Muốn chọn “vừa nằm trong module thanh toán **vừa** có marker theo dõi” nhưng viết `A OR B` sẽ chọn cả các method chỉ thỏa một điều kiện. Đây là lỗi **mở rộng phạm vi**, có thể gây ảnh hưởng hệ thống nghiêm trọng nếu advice thay đổi hành vi.

### 12.3. Phủ định không có giới hạn dương

`NOT Internal` có thể chọn hầu như toàn bộ điểm khả dụng. Hãy có base rõ ràng, ví dụ `insideApiBoundary AND NOT Internal`.

### 12.4. Phủ định nhầm nhóm theo De Morgan

Muốn loại **cả hai loại** A và B nhưng viết `NOT A OR NOT B` là quá rộng. Đúng là `NOT (A OR B)`, tương đương `NOT A AND NOT B`.

### 12.5. Nhầm OR với hai advice độc lập

`A OR B` là một phép chọn tập, không phải chỉ dẫn chạy advice hai lần nếu cả A và B đúng. Nếu thực sự có hai aspect/advice độc lập, phân tích thứ tự và tần suất theo cấu hình riêng.

### 12.6. Tin rằng NOT “bắt” điểm chưa được engine hỗ trợ

Phủ định trên một predicate không biến field access, constructor hay self-invocation ngoài proxy thành sự kiện chặn được. **Join-point model và đường can thiệp là điều kiện tiên quyết.**

### 12.7. Cố ý tạo side effect trong predicate

Logic pointcut nên diễn tả tiêu chí; không nên dựa vào `A` được “chạy” trước `B` để cập nhật bộ đếm, mở tài nguyên hoặc quyết định nghiệp vụ. Cơ chế matcher có thể tối ưu, cache hoặc đánh giá theo phương pháp riêng.

### 12.8. Quá nhiều nhánh đặc biệt

Biểu thức `(A OR B OR C ...) AND NOT (X OR Y OR Z ...)` có thể khó hiểu, đặc biệt khi nhiều module cùng thêm quy tắc. Hãy định danh nhóm con, tài liệu hóa phạm vi, test riêng các nhánh và kiểm tra lại sau refactor. Không nên tăng độ phức tạp của pointcut chỉ để che giấu workflow nghiệp vụ.

## 13. Kiểm tra hiểu biết

**Câu 1.** A=true, B=false, C=true. Giá trị của `(A OR B) AND NOT C` và `A OR (B AND NOT C)` là gì?

**Đáp án:** Biểu thức thứ nhất false vì C loại toàn bộ; biểu thức thứ hai true vì A đã true.

**Câu 2.** Bạn muốn mọi điểm thuộc A hoặc B, trừ cả C và D. Chọn biểu thức nào?

```text
(A OR B) AND NOT (C OR D)
```

Biểu thức đó tương đương `(A OR B) AND NOT C AND NOT D` theo De Morgan.

**Câu 3.** Vì sao `A OR A` không làm một advice chạy hai lần?

**Đáp án:** Luật lũy đẳng: `A OR A` chọn cùng tập với A. Chạy nhiều advice hoặc lặp sự kiện runtime là câu chuyện khác.

**Câu 4.** Vì sao `NOT marker("Internal")` có thể nguy hiểm?

**Đáp án:** Nó chọn gần như mọi điểm trong vũ trụ công cụ quan sát được mà không có marker ấy; dễ ảnh hưởng quá nhiều chức năng và tạo overhead, logging dữ liệu không phù hợp.

**Câu 5.** Một method có marker phù hợp nhưng được gọi trực tiếp vào target, trong khi hệ thống chỉ dùng interface proxy. Advice có chắc chạy không?

**Đáp án:** Không. Cần đi qua proxy được cấu hình; Boolean match không tự tạo đường chặn.

**Câu 6.** Một method được gọi mười lần; nhánh điều kiện động A chỉ true trong bốn lượt, B false mọi lượt. Với `A OR B`, advice có thể chạy bao nhiêu lần?

**Đáp án:** Bốn nếu engine hỗ trợ matcher động, mọi lượt đi qua đường can thiệp và mỗi lượt khớp kích hoạt advice đúng một lần.

**Câu 7.** `callOf("save") AND executionOf("save")` có tự nhiên tương đương với “mọi save” không?

**Đáp án:** Không. Trong mô hình tách call/execution, hai loại join point có thể không cùng đúng trên một sự kiện. Phải chọn loại điểm phù hợp ý định.

## Kết luận

Kết hợp pointcut bằng **AND, OR, NOT** chính là bài toán **lấy giao, lấy hợp và loại trừ** trong một tập join point mà cơ chế AOP có thể quan sát. AND làm hẹp phạm vi; OR làm rộng phạm vi; NOT loại những sự kiện không mong muốn nhưng chỉ trong vũ trụ mà công cụ hỗ trợ. Dấu ngoặc giúp giữ đúng ý định, đặc biệt ở mẫu `(A OR B) AND NOT C`. Hãy luôn kiểm tra cả hai tầng: **logic chọn điểm có đúng không** và **đường thực thi có thật sự đi qua cơ chế can thiệp hay không**. Một pointcut ghép đáng tin là pointcut có bảng chân trị rõ, ca positive/negative và bằng chứng runtime xác thực.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-basics-008">Đặt tên pointcut để tái sử dụng và diễn đạt ý định</a>

<details>
<summary>Xem chi tiết</summary>

# Đặt tên pointcut để tái sử dụng và diễn đạt ý định

## 1. Vì sao nên đặt tên pointcut?

Trong lập trình hướng khía cạnh (**Aspect-Oriented Programming — AOP**), **pointcut** là một quy tắc chọn những *join point* (điểm trong luồng thực thi có thể được can thiệp) mà advice sẽ áp dụng. Với quy tắc nhỏ, người đọc có thể hiểu ngay một biểu thức kiểm tra chữ ký hoặc phạm vi. Nhưng khi cùng một quy tắc xuất hiện ở nhiều aspect, sao chép biểu thức thường tạo ra **sự trùng lặp và lệch phạm vi**.

**Đặt tên pointcut (named pointcut)** là đặt một **tên có ý nghĩa** cho một quy tắc lựa chọn, sau đó **tham chiếu và kết hợp** quy tắc ấy ở những nơi cần dùng. Thay vì yêu cầu người đọc giải mã nhiều điều kiện dài ở từng advice, tên sẽ nói rõ **vì sao nhóm join point được chọn**.

Ví dụ, các hành vi audit, ghi chỉ số và tracing cùng cần chọn “những thao tác làm thay đổi đơn hàng đã được đánh dấu cần theo dõi”. Thay vì viết lại ba lần điều kiện khớp chữ ký và annotation, ta đặt một quy tắc `auditableOrderCommands` rồi dùng lại trong cả ba chính sách. Khi ranh giới “thao tác đơn hàng cần audit” thay đổi, một định nghĩa nhất quán có thể cập nhật phạm vi của các nơi tham chiếu.

Cần phân biệt bốn thành phần:

| Thành phần | Vai trò | Câu hỏi nó trả lời |
| --- | --- | --- |
| **Join point** | Một điểm thực thi trong chương trình | “Có điểm nào để can thiệp?” |
| **Pointcut** | Quy tắc chọn tập join point | “Điểm nào được chọn?” |
| **Named pointcut** | Định danh một quy tắc chọn có thể tái sử dụng | “Nhóm điểm này có ý nghĩa gì?” |
| **Advice** | Hành vi chạy tại các điểm được chọn | “Khi được chọn, làm gì?” |

Một tên pointcut **không tự làm advice chạy**, không tự mở rộng tập join point mà engine AOP hỗ trợ, và không thay thế cơ chế weaving hoặc proxy. Nó là cách tổ chức **ý định lựa chọn**, còn hiệu lực thực thi phụ thuộc việc pointcut thực sự khớp và nằm trong vùng được can thiệp.

## 2. Từ biểu thức rời rạc đến một khái niệm có tên

Giả sử chính sách audit cần áp dụng cho phương thức thuộc hợp đồng dịch vụ đơn hàng, có mức truy cập `public` và được đánh dấu cần ghi nhận. Một biểu thức chọn bằng nhiều điều kiện có thể hình dung như sau:

~~~text
declaringType == OrderApi
AND visibility == public
AND hasMarker(Audited)
~~~

Đây là **ký pháp mô tả ý định**, không phải cú pháp thực thi của bất kỳ ngôn ngữ pointcut cụ thể nào. Nếu cùng ý định được lặp ở nhiều nơi, chỉ cần một người sửa thiếu điều kiện là audit, metrics và tracing có thể bắt đầu quan sát những tập lời gọi khác nhau.

Đặt tên cho ý định:

~~~text
pointcut publicOrderApi =
    declaringType == OrderApi AND visibility == public

pointcut markedForAudit =
    hasMarker(Audited)

pointcut auditableOrderCommands =
    publicOrderApi AND markedForAudit

advice audit    applies-to auditableOrderCommands
advice tracing  applies-to auditableOrderCommands
~~~

Các dòng trên là **pseudocode AOP**, không phải cú pháp Java hay một engine cụ thể. Điểm được nhấn mạnh là mối quan hệ: **hai pointcut nhỏ** → **một named pointcut thể hiện ý định** → **nhiều advice tái sử dụng**. Tên gắn với *chính sách áp dụng*, không gắn với việc advice đang làm gì; ví dụ `auditableOrderCommands` có thể dùng cho cả audit và tracing dù tên vốn bắt nguồn từ yêu cầu kiểm toán.

Nếu chỉ muốn ghi dấu một nhóm con nhạy cảm, ta tạo thêm:

~~~text
pointcut importantOrderCommands =
    auditableOrderCommands AND name == "place"
~~~

Ta **không sửa** `auditableOrderCommands` khi cần một nhóm hẹp cho một nhu cầu riêng; thay vào đó, tạo tên mới biểu thị rõ quan hệ “là tập con của”. Điều này tránh vô ý làm thay đổi các advice đang dùng pointcut chung.

## 3. Đặt tên theo ý định, không chỉ diễn đạt cú pháp

Hai tên sau cùng chỉ một điều kiện có thể rất khác nhau về khả năng bảo trì:

| Tên | Người đọc hiểu gì? | Đánh giá |
| --- | --- | --- |
| `publicMethodsWithMarker` | Các yếu tố kỹ thuật được kiểm tra | Hợp lý ở pointcut nền tảng, nhưng chưa nói vì sao chọn |
| `auditableOrderCommands` | Các thao tác đơn hàng thuộc chính sách audit | Phù hợp làm tên nghiệp vụ/kỹ thuật có chủ đích |
| `allMethods` | Một tập rất rộng, chưa có ý nghĩa áp dụng | Chỉ hữu ích nếu thật sự muốn mọi phương thức |
| `everythingForLogging` | Vừa mô tả quá rộng, vừa gắn chặt với một advice | Khó tái dùng cho chính sách khác |
| `pointcut1` hoặc `tempRule` | Không truyền đạt ranh giới hay ý định | Nên tránh |
| `sensitiveOperations` | Ý định rõ nếu thuật ngữ “sensitive” đã có định nghĩa | Cần tài liệu hoặc marker xác định thế nào là nhạy cảm |

Một tên tốt thường là **cụm danh từ mô tả tập join point**: `publicApiOperations`, `mutatingCommands`, `auditableTransactions`, `readOnlyQueries`, `externalBoundaryCalls`. Tên không nên gợi ý rằng **advice đã chạy**, vì việc matching và weaving vẫn là các bước khác nhau.

Hãy chọn độ trừu tượng phù hợp:

- **Pointcut nền tảng:** mô tả tiêu chí kỹ thuật nhỏ, chẳng hạn `publicServiceMethods`, `annotatedOperations` hoặc `inDomainPackage`.
- **Pointcut theo chính sách:** ghép các tiêu chí nền tảng thành `auditableCommands`, `measuredRequests`, `securedEntryPoints`.
- **Pointcut chuyên biệt:** thu hẹp một chính sách đã có cho phạm vi cần thiết, chẳng hạn `criticalAuditableCommands`.

Từ ngữ phải ổn định về mặt nghĩa. Nếu tên là `mutatingCommands` nhưng biểu thức chỉ khớp mọi phương thức có tiền tố `set`, nó có thể bỏ sót `delete` hoặc chọn nhầm phương thức không làm thay đổi dữ liệu. Khi đó tên là lời hứa vượt quá điều kiện thực tế.

## 4. Named pointcut không đồng nghĩa với một cú pháp chung

Các hệ thống AOP có thể cung cấp cách khai báo khác nhau: tên cho biểu thức pointcut, hàm hoặc macro tham chiếu, pointcut trừu tượng được hiện thực ở lớp con, quy tắc chọn bằng object/function, hoặc cấu hình do công cụ tải vào. Có công nghệ hỗ trợ tham số hóa và ghép biểu thức; có công nghệ chỉ cung cấp subset.

Cần xác minh từng khả năng:

1. **Định nghĩa tên:** pointcut được khai báo bằng phương thức, biểu thức, đối tượng hay cấu hình? Tên có nhất thiết trùng tên method không?
2. **Tham chiếu:** tên được dùng trực tiếp, có prefix theo type/namespace hay phải import?
3. **Phạm vi hiển thị:** `private`, package-local hoặc `public` trên pointcut mang ý nghĩa tái sử dụng nào? Nó khác với mức truy cập của phương thức target.
4. **Composition:** có hỗ trợ AND, OR, NOT, phép nhóm ngoặc hoặc đặt một named pointcut trong named pointcut khác không?
5. **Binding tham số:** tham chiếu named pointcut có truyền biến ngữ cảnh hoặc tham số không? Cách kiểm tra kiểu và tên biến ra sao?
6. **Độ phủ join point:** pointcut có khớp method call, method execution, constructor hoặc điểm khác không?
7. **Thời điểm xác định:** quy tắc chỉ theo metadata tĩnh hay có điều kiện runtime? Cách áp dụng khi value `null` hoặc target thay đổi?

**Không sao chép cú pháp pointcut từ hệ thống này sang hệ thống khác** và mặc định nó có cùng ngữ nghĩa. Một ký hiệu wildcard hoặc định dạng signature giống nhau vẫn có thể biểu diễn tập join point khác nhau.

## 5. Ghép và tái sử dụng: AND, OR, NOT và phép nhóm

Nhìn về mặt tập hợp, mỗi pointcut xác định một tập join point trong vũ trụ các điểm mà engine có thể quan sát. Tên giúp ta diễn đạt các phép biến đổi của tập:

| Phép logic | Ý nghĩa trên tập điểm | Trường hợp sử dụng |
| --- | --- | --- |
| `A AND B` | Chỉ điểm thuộc cả A và B | Public API **và** được đánh dấu audit |
| `A OR B` | Điểm thuộc A hoặc B | Lời gọi tới hai nhóm thao tác có cùng chính sách |
| `NOT A` | Điểm không thuộc A trong phạm vi nền | Loại nhóm tiện ích hoặc điểm bị loại trừ |
| `(A OR B) AND C` | Chọn A/B rồi giới hạn bởi C | Nhiều nhóm thao tác nhưng cùng giới hạn quyền |
| Tham chiếu `namedA` trong `namedB` | Tái sử dụng điều kiện đã định nghĩa | Pointcut chuyên biệt kế thừa phạm vi điểm gốc |

Ví dụ:

~~~text
pointcut writeCommands = hasMarker(ChangesState)
pointcut explicitAuditing = hasMarker(Audited)
pointcut entryPoints = publicApi AND notInternalHelpers

pointcut observableMutations =
    entryPoints AND (writeCommands OR explicitAuditing)
~~~

Đây là **pseudocode**, không phải một định dạng khai báo chạy được. Hai điều kiện `ChangesState` và `Audited` chỉ là nhãn khái niệm; ứng dụng có thể định nghĩa marker, metadata hoặc chính sách lựa chọn tương ứng.

Cần nhớ độ ưu tiên toán tử: `A OR B AND C` có thể bị đọc nhầm thành `(A OR B) AND C`, trong khi quy tắc ưu tiên thường cho nghĩa `A OR (B AND C)`. Khi nội dung quan trọng, **dùng ngoặc rõ ràng**, đặt named pointcut trung gian và kèm bảng kiểm thử.

**NOT cần được giới hạn theo phạm vi.** Một pointcut `NOT internalHelpers` nếu không giao với `publicApi` có thể chọn cực nhiều điểm ngoài mong đợi. Thường nên viết `publicApi AND NOT internalHelpers` để người đọc hiểu tập nền ngay từ đầu.

Đặt tên theo **hành vi ổn định** sẽ tránh hiện tượng quy tắc dài dần thành một chuỗi OR của nhiều ngoại lệ. Nếu logic quá phức tạp, cân nhắc chia thành các pointcut nhỏ và cung cấp tài liệu/kiểm thử cho từng tập.

## 6. Pointcut có tham số: tái sử dụng logic mà vẫn truyền ngữ cảnh

Trong một số mô hình AOP, named pointcut có thể nhận tham số hoặc binding ngữ cảnh. Ví dụ, một nhóm join point “mọi thao tác trên thực thể với đối số là tài khoản” có thể vừa được dùng cho audit vừa được dùng cho kiểm quyền. Ý định ở đây không chỉ là **chọn phương thức** mà còn là **đưa dữ liệu của lần gọi** vào advice.

Pseudocode:

~~~text
pointcut operationsOnAccount(account):
    selectedOperationWithAccountArg(account)

advice before operationsOnAccount(account):
    auditAccountId(account.id)
~~~

`account` chỉ là tên biến ngữ cảnh minh họa. Engine thật có thể yêu cầu khai báo kiểu, kiểm tra vị trí đối số, binding annotation hay một cấu trúc khác.

Khi tham số hóa, cần xác định:

- Tên và kiểu của tham số thuộc **khai báo pointcut**, còn giá trị thực tế thuộc **một lần thực thi**.
- Nếu nhiều overload có cấu trúc đối số khác nhau, pointcut phải diễn đạt rõ overload nào được phép khớp.
- `null` có thể làm các phép so kiểu động hoặc truy cập thuộc tính không thực hiện được như kỳ vọng; phải có quy tắc bảo vệ.
- Không sử dụng thông tin nhạy cảm làm khóa log mặc định; chỉ đưa những trường cần thiết vào advice.
- Một named pointcut có tham số không nên lén thay đổi input target nếu mục đích của advice chỉ là quan sát.

Khi công cụ không hỗ trợ binding tham số trong pointcut, có thể chọn join point ở mức tĩnh rồi để advice đọc đối số qua API được cung cấp. **Tái sử dụng pointcut** và **tái sử dụng chức năng xử lý ngữ cảnh** là hai vấn đề liên quan nhưng không đồng nhất.

## 7. Ví dụ Java 17+: đặt tên và ghép pointcut, dùng lại ở hai advice

Ví dụ sau dùng **Java Reflection, `Predicate<Method>` và JDK dynamic proxy** để hiện thực một *mô hình nhỏ* của named pointcut. Đây là ví dụ chạy độc lập trên Java 17+, nhằm chứng minh cách đặt tên, tham chiếu và ghép các bộ lọc; nó **không phải ngôn ngữ pointcut tổng quát** và proxy không hỗ trợ toàn bộ loại join point.

Ý định:

- `publicOrderApi()` chọn phương thức public khai báo trên interface `Orders`.
- `markedForAudit()` chọn các phương thức có annotation `@Audited`.
- `auditableCommands()` **tái sử dụng** hai named predicate và ghép bằng AND.
- `importantCommands()` thu hẹp `auditableCommands()` chỉ còn `place`.
- Hai wrapper `AUDIT` và `TRACE` cùng tham chiếu `auditableCommands()`; lời gọi `status()` không được chọn.

Tạo tệp `NamedPointcutDemo.java`:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.function.Predicate;

public class NamedPointcutDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Audited { }

    interface Orders {
        @Audited String place(String product, int quantity);
        @Audited String cancel(String id);
        String status();
    }

    static class OrderCore implements Orders {
        public String place(String product, int quantity) {
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
            System.out.println("BUSINESS place");
            return product + " x" + quantity;
        }

        public String cancel(String id) {
            System.out.println("BUSINESS cancel");
            return "cancelled " + id;
        }

        public String status() {
            return "READY";
        }
    }

    static final class Pointcuts {
        static Predicate<Method> publicOrderApi() {
            return m -> m.getDeclaringClass() == Orders.class
                    && Modifier.isPublic(m.getModifiers());
        }

        static Predicate<Method> markedForAudit() {
            return m -> m.isAnnotationPresent(Audited.class);
        }

        static Predicate<Method> auditableCommands() {
            return publicOrderApi().and(markedForAudit());
        }

        static Predicate<Method> importantCommands() {
            return auditableCommands().and(m -> m.getName().equals("place"));
        }
    }

    static Object invokeTarget(Orders target, Method method, Object[] args)
            throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException wrapper) {
            throw wrapper.getCause();
        }
    }

    static Orders wrap(Orders target, Predicate<Method> pointcut, String label) {
        return (Orders) Proxy.newProxyInstance(
                Orders.class.getClassLoader(),
                new Class<?>[] { Orders.class },
                (proxy, method, args) -> {
                    if (!pointcut.test(method)) {
                        return invokeTarget(target, method, args);
                    }
                    System.out.println(label + " begin " + method.getName());
                    try {
                        Object result = invokeTarget(target, method, args);
                        System.out.println(label + " success " + method.getName());
                        return result;
                    } catch (Throwable error) {
                        System.out.println(label + " failure " + method.getName());
                        throw error;
                    }
                });
    }

    public static void main(String[] args) throws Exception {
        Method place = Orders.class.getMethod("place", String.class, int.class);
        Method cancel = Orders.class.getMethod("cancel", String.class);
        System.out.println("IMPORTANT place: " + Pointcuts.importantCommands().test(place));
        System.out.println("IMPORTANT cancel: " + Pointcuts.importantCommands().test(cancel));

        Orders audited = wrap(new OrderCore(), Pointcuts.auditableCommands(), "AUDIT");
        Orders observed = wrap(audited, Pointcuts.auditableCommands(), "TRACE");

        System.out.println("STATUS " + observed.status());
        System.out.println("RESULT " + observed.place("Book", 2));
        System.out.println("RESULT " + observed.cancel("O-7"));
        try {
            observed.place("Book", 0);
        } catch (IllegalArgumentException error) {
            System.out.println("CALLER " + error.getMessage());
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 NamedPointcutDemo.java
java NamedPointcutDemo
~~~

Kết quả đầu ra đã được kiểm chứng:

~~~text
IMPORTANT place: true
IMPORTANT cancel: false
STATUS READY
TRACE begin place
AUDIT begin place
BUSINESS place
AUDIT success place
TRACE success place
RESULT Book x2
TRACE begin cancel
AUDIT begin cancel
BUSINESS cancel
AUDIT success cancel
TRACE success cancel
RESULT cancelled O-7
TRACE begin place
AUDIT begin place
AUDIT failure place
TRACE failure place
CALLER quantity must be positive
~~~

**Giải thích từng điểm chính:**

1. `importantCommands()` tham chiếu `auditableCommands()` rồi ghép thêm điều kiện tên `place`. Vì thế `place` trả `true` nhưng `cancel` trả `false`. Cả hai vẫn nằm trong tập `auditableCommands()`.
2. `status()` không có `@Audited`, do đó cả hai wrapper đều bỏ qua logic bổ sung và chuyển tiếp về target. Ta chỉ thấy `STATUS READY`.
3. `place("Book", 2)` khớp named pointcut của cả hai wrapper: `TRACE` ở ngoài → `AUDIT` ở trong → nghiệp vụ → `AUDIT` hoàn tất → `TRACE` hoàn tất. Chính vì có hai wrapper, điểm khớp được quan sát bởi **hai hành vi khác nhau** mà không cần lặp định nghĩa bộ chọn.
4. `cancel("O-7")` cũng được đánh dấu `@Audited` nên được cả `TRACE` và `AUDIT` quan sát, dù không khớp `importantCommands()`.
5. Với `place("Book", 0)`, target ném `IllegalArgumentException`. `invokeTarget()` bóc `InvocationTargetException` do reflection tạo ra, hai wrapper ghi nhánh thất bại rồi ném tiếp nguyên nhân gốc cho caller. Việc tái sử dụng pointcut không làm mất tính đúng của hợp đồng ngoại lệ.

Đây là **mô hình proxy đơn giản**. `Method` mà JDK proxy đưa vào là method của interface, vì vậy `@Audited` được gắn trực tiếp trên interface `Orders`. Một engine AOP thực có thể chọn phương thức trên interface, implementation hoặc join point khác và có quy tắc tra annotation khác. Không nên tự suy ra rằng annotation trên interface luôn được kế thừa và phát hiện như nhau trong mọi cơ chế.

## 8. Tái sử dụng tên không có nghĩa là tái sử dụng trạng thái runtime

Một pointcut có tên về bản chất là một **định nghĩa chọn điểm**. Nó không cần lưu bộ đếm sự kiện, kết quả lời gọi hoặc dữ liệu mutable của từng lần chạy. Ví dụ `auditableCommands()` có thể được gọi nhiều lần để tạo ra các predicate tương đương; hai wrapper đang sử dụng các predicate độc lập nhưng cùng có logic và ý định.

Nếu ứng dụng cần chia sẻ metrics hay cache, chúng là những **dịch vụ/trạng thái khác**, nên được thiết kế có quy tắc đồng thời và vòng đời riêng. Đừng nhét bộ đếm trạng thái vào phần định nghĩa pointcut rồi mặc định gọi ở bất kỳ thời điểm nào cũng có cùng kết quả: hàm chọn điểm bị phụ thuộc vào trạng thái ngẫu nhiên sẽ khó kiểm thử và có thể thay đổi phạm vi advice giữa các lời gọi.

**Named pointcut cũng không mặc nhiên giảm số lần advice chạy.** Nếu hai advice cùng tham chiếu một pointcut, chúng có thể đều chạy trên cùng join point. Tái sử dụng ở đây là tái sử dụng **định nghĩa tập điểm**, không phải tự động loại bỏ một trong các advice.

## 9. Phạm vi truy cập và tổ chức tên giữa nhiều mô-đun

Trong một hệ thống AOP có hỗ trợ named pointcut ở cấp module hoặc class, tác giả thường cần quyết định điểm nào là **API lựa chọn chung** và điểm nào là **chi tiết triển khai riêng**:

| Phạm vi ý niệm | Khi phù hợp | Lưu ý |
| --- | --- | --- |
| Pointcut private/internal | Dùng để ghép các quy tắc trong một aspect | Có thể thay đổi mà không ảnh hưởng tên được công khai |
| Pointcut chia sẻ trong package/module | Nhiều aspect cùng dùng cùng ranh giới | Cần một chủ sở hữu định nghĩa, quy ước thay đổi |
| Pointcut public/shared contract | Nhiều thành phần hoặc module khác nhau phụ thuộc | Tên và ý nghĩa chọn điểm trở thành hợp đồng đáng kiểm thử |

**Quyền truy cập của chính named pointcut** là câu chuyện khác với **modifier truy cập của phương thức target**. Ví dụ, một named pointcut có thể được công bố rộng rãi để mọi aspect dùng, nhưng điều kiện bên trong nó chỉ chọn phương thức target `public`. Ngược lại, một pointcut nội bộ có thể định nghĩa các join point khác nếu engine hỗ trợ.

Không phải công nghệ nào cũng có khái niệm `public/private` cho named pointcut. Khi công cụ chỉ có hàm, biến hoặc file cấu hình, hãy áp dụng cơ chế xuất/ẩn và quản lý dependency tương ứng. Điều quan trọng là **một nơi sở hữu tập điểm** và các nơi dùng hiểu rõ hợp đồng của tên đó.

Ví dụ, nếu `auditableCommands` là pointcut chia sẻ cho auditing và tracing, việc thêm `cancel` vào tập này sẽ tác động đến cả hai. Đó là một thay đổi về **phạm vi quan sát** đáng được code review và kiểm thử như sửa một API.

## 10. Thiết kế pointcut có tên để tránh coupling ẩn

Một biểu thức pointcut rất dài có thể gom nhiều điều kiện thuộc các tầng khác nhau: tên package, modifier, chữ ký, marker và điều kiện runtime. Khi mỗi nhóm điều kiện có tên, người đọc có thể nhận ra **phần nào là ranh giới kỹ thuật, phần nào là chính sách**.

Ví dụ:

~~~text
applicationEntryPoints
    = publicOperations AND inApplicationBoundary

trackedMutations
    = applicationEntryPoints AND changesState

auditableTrackedMutations
    = trackedMutations AND requiresAudit
~~~

Các tên trên là pseudocode thể hiện quan hệ tập hợp. Sự thay đổi ở `inApplicationBoundary` có thể lan tới cả hai tập dẫn xuất. Vì vậy, một chuỗi tham chiếu có tên tốt giúp thấy **phụ thuộc có chủ đích**; ngược lại, nhiều named pointcut lồng nhau nhưng không rõ người sở hữu cũng dễ khiến người sửa một chỗ làm ảnh hưởng phạm vi rộng.

Một điểm cân bằng:

- Đặt tên khi điều kiện **được tham chiếu ở nhiều nơi**, khi nó **diễn đạt một khái niệm ổn định**, hoặc khi biểu thức thô **khó đọc đủ để xác minh đúng**.
- Không cần tạo tên riêng cho mọi toán tử nhỏ nếu tên không giúp giải thích thêm ý nghĩa hoặc chỉ dùng đúng một nơi đơn giản.
- Tránh tạo **vòng tham chiếu** giữa các named pointcut. Hãy giữ các quy tắc như một đồ thị phụ thuộc rõ, dễ kiểm tra.
- Hạn chế đặt tên theo người dùng hiện tại của pointcut (`forLoggerOnly`) khi các chính sách khác có thể cần cùng ranh giới.
- Tài liệu nên nêu điều kiện **phải chọn** và **phải loại trừ**, kể cả ngoại lệ có chủ đích.

## 11. Kiểm thử ngữ nghĩa của named pointcut

Một named pointcut là một hợp đồng về **tập join point**, do đó kiểm thử cần tách **matching** với **advice thực sự chạy**.

| Tình huống | `auditableCommands` | `importantCommands` | Điều cần kiểm tra |
| --- | --- | --- | --- |
| `Orders.place(String,int)` có `@Audited` | Khớp | Khớp | Audit/trace có thể được áp dụng qua proxy |
| `Orders.cancel(String)` có `@Audited` | Khớp | Không | Chỉ thu hẹp ở tên chuyên biệt |
| `Orders.status()` không có `@Audited` | Không | Không | Không sinh sự kiện bổ sung |
| Method public nhưng không thuộc `Orders` | Không trong ví dụ | Không | Điều kiện declaring type phát huy tác dụng |
| Method cùng tên nhưng khác overload | Tùy marker và hợp đồng | Tùy tên/điều kiện khác | Kiểm tra trường hợp overmatching |
| Gọi trực tiếp `OrderCore` bỏ qua proxy | Bộ chọn vẫn có thể mô tả chữ ký | Tương tự | Advice trên proxy không chạy |
| Gọi thất bại qua proxy | Khớp nếu marker/kiểu hợp lệ | Theo tên | Bảo toàn ngoại lệ và kiểm đủ lời gọi |

Đối với công cụ AOP thực, cần kiểm thử cả các giới hạn mà chương trình Reflection mô phỏng không đủ: **join point call/execution, self-invocation, inheritance, bridge/synthetic methods, annotation trên interface hay implementation, thứ tự advice và vùng weaving**.

Có thể thiết lập hai nhóm test:

1. **Contract test cho named pointcut:** lập danh sách một số chữ ký/join point phải khớp, một số phải bị loại. Khi sửa định nghĩa dùng chung, những test này cho biết tập chọn bị mở rộng hay thu hẹp.
2. **Integration test cho aspect:** gọi thật qua cơ chế đã weaving/proxy; kiểm tra cả số lần advice chạy, kết quả, ngoại lệ, dữ liệu quan sát và đường không được chọn.

Khi cả audit và tracing dùng chung một pointcut, không nên chỉ kiểm tra một trong hai. Tập điểm có thể giống nhau về mặt định nghĩa, nhưng mỗi advice vẫn có thể khác nhau về thứ tự, điều kiện bổ sung hoặc đường lỗi.

## 12. Một số góc cạnh nâng cao

### 12.1. Tham chiếu quá rộng và độ ổn định của tên

Nếu `applicationOperations` được định nghĩa bằng “mọi method trong một package”, việc chuyển code sang package đó có thể làm phát sinh advice mà người viết method không dự đoán. Một marker có ngữ nghĩa và quy ước rõ có thể ổn định hơn quy tắc tên package, nhưng cũng đòi hỏi kiểm soát cách đánh dấu. Chọn điều kiện theo chính sách của dự án, rồi bảo vệ bằng contract test.

### 12.2. Compile-time và runtime matching

Một số điều kiện có thể quyết định từ signature khai báo, còn điều kiện khác phụ thuộc giá trị đối số/đối tượng thực trong lúc chạy. Nếu pointcut có tên chứa cả hai loại điều kiện, hãy nêu rõ phạm vi runtime, hiệu năng và dữ liệu nó được phép truy cập. Đừng hiểu việc chỉ ra được tên một method bằng Reflection là bằng chứng rằng mọi lần gọi sẽ có cùng kết quả matching trong một engine có điều kiện động.

### 12.3. Kiểu trả về và đường bất đồng bộ

Một named pointcut chọn “mọi phương thức trả `CompletableFuture`” có thể khớp **lần phương thức trả đối tượng Future**, không tự động quan sát khi tác vụ bên trong Future hoàn tất. Nếu chính sách mang tên `completedOperations` mà chỉ đo thời điểm return, tên và ngữ nghĩa đã lệch. Có thể cần một cơ chế quan sát completion riêng.

### 12.4. Tác dụng phụ và quyền thực thi

Một pointcut dùng lại cho audit và kiểm quyền không có nghĩa các advice đồng nhất. Advice kiểm quyền có thể chặn target; advice audit có thể cần ghi cả trường hợp từ chối. Nếu thứ tự không rõ, named pointcut đúng vẫn có thể dẫn đến log thiếu. Tên pointcut quyết định **tập điểm**, còn precedence/advice quyết định **trình tự và kết quả**.

### 12.5. Điều kiện phụ thuộc công nghệ

Tên pointcut có thể được kiểm tra tại thời điểm biên dịch/weaving hoặc được đánh giá khi chạy. Phạm vi tham chiếu, kiểu binding, wildcard, package/module visibility và xử lý generic/bridge khác nhau theo engine. Khi chuyển nền tảng, giữ **hợp đồng ý nghĩa** và tập trường hợp kiểm thử, sau đó viết lại cú pháp phù hợp thay vì sao chép nguyên biểu thức.

## 13. Sai lầm thường gặp

- **Dùng tên mơ hồ** như `commonMethods` mà không có hợp đồng tập điểm; không biết mở rộng đến đâu thì hợp lệ.
- **Đặt tên theo cú pháp nhưng hứa hẹn sai ý nghĩa:** `allWrites` chỉ chọn method `set*` và bỏ `save/delete`.
- **Sao chép biểu thức thay vì dùng chung tên:** ba advice có ba bộ chọn hơi khác nhau, khiến phạm vi bị lệch.
- **Sửa pointcut chia sẻ mà không đánh giá tác động:** thêm một điều kiện OR có thể khiến audit/tracing chạy ở những nơi ngoài dự kiến.
- **Lạm dụng phủ định rộng:** `NOT internal` có thể chọn gần toàn bộ ứng dụng nếu không giới hạn tập nền.
- **Dựa vào tên để suy ra engine có thể chặn mọi điểm:** proxy và weaving có phạm vi thực thi riêng.
- **Đánh đồng named pointcut với advice:** định nghĩa lựa chọn điểm không tự sinh hành vi.
- **Nhầm public/private của pointcut với access modifier của target:** hai phạm vi tiếp cận hoàn toàn khác.
- **Bỏ qua thứ tự khi nhiều advice dùng chung pointcut:** tập điểm đúng không bảo đảm kết quả quan sát đúng.
- **Không kiểm thử các trường hợp không được khớp:** các false positive đôi khi gây tác dụng phụ lớn hơn false negative.
- **Trông chờ generic, marker và overload được suy diễn giống nhau trong mọi công cụ:** cần kiểm tra mô hình join point và kiểu metadata.

## 14. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao `auditableCommands` thường là tên có ý nghĩa hơn `publicMarkedMethods` cho một chính sách chia sẻ?  
**Trả lời:** Nó thể hiện *mục đích* của tập điểm, còn tên thứ hai chủ yếu mô tả kỹ thuật khớp. Tên có ý nghĩa vẫn phải được bảo đảm bằng định nghĩa và kiểm thử.

**Câu 2.** Hai advice cùng tham chiếu một named pointcut. Một lời gọi khớp sẽ chỉ kích hoạt một advice phải không?  
**Trả lời:** Không. Tên được tái sử dụng cho **cùng tập điểm**; từng advice vẫn có thể chạy theo quy tắc áp dụng và thứ tự của cơ chế AOP.

**Câu 3.** Muốn chỉ lấy nhóm “thao tác quan trọng” từ một pointcut chung nên làm gì?  
**Trả lời:** Định nghĩa pointcut chuyên biệt bằng cách tham chiếu pointcut chung rồi ghép thêm điều kiện, thay vì tự ý thu hẹp pointcut chung làm ảnh hưởng các chính sách khác.

**Câu 4.** Nếu `auditableCommands` chọn đúng `privateMethod()` theo metadata, có chắc proxy sẽ chặn được không?  
**Trả lời:** Không. Matching và interception là hai bước khác nhau; kiểu proxy và weaving quyết định khả năng can thiệp.

**Câu 5.** Đâu là kiểm thử quan trọng nhất khi thay đổi một named pointcut chia sẻ?  
**Trả lời:** Kiểm tra tập **phải khớp/không được khớp** và chạy lại tích hợp các advice đang tham chiếu, bao gồm nhánh thành công, thất bại và đường bypass.

## 15. Tổng kết

**Đặt tên pointcut để tái sử dụng và diễn đạt ý định** giúp tập trung một quy tắc chọn join point thành một hợp đồng có nghĩa, dễ tham chiếu, dễ ghép và dễ kiểm thử. Nên phân biệt pointcut nền tảng với pointcut theo chính sách, đặt tên phản ánh **tập điểm cần áp dụng**, sử dụng composition để tạo tập con/tập hợp mới, và kiểm soát những nơi cùng phụ thuộc vào định nghĩa chia sẻ. Một named pointcut tốt chỉ là khởi đầu: muốn advice vận hành chính xác, vẫn phải xác minh quy tắc matching, phạm vi can thiệp, thứ tự advice, hành vi lỗi và sự khác biệt giữa các công nghệ AOP.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-basics-009">Phân biệt điều kiện bao gồm với điều kiện loại trừ</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt điều kiện bao gồm với điều kiện loại trừ trong pointcut AOP

> **Mục tiêu học tập:** Hiểu điều kiện **bao gồm** (*inclusion*) xác định phạm vi join point ứng viên mà một chính sách AOP muốn áp dụng, còn điều kiện **loại trừ** (*exclusion*) gỡ khỏi phạm vi đó những điểm không nên được áp dụng. Biết diễn đạt bằng logic tập hợp, kết hợp AND/OR/NOT, xử lý quy tắc chồng lấn, kiểm thử trường hợp biên và phân biệt một điểm bị loại với điểm vốn không được công cụ AOP hỗ trợ.

## 1. Vấn đề: “áp dụng ở đâu?” và “trừ những đâu?”

Giả sử một ứng dụng có nhiều thao tác xử lý đơn hàng. Ta cần đo thời gian những phương thức **gửi đơn hàng**, nhưng không muốn đo các thao tác **kiểm thử nội bộ** hoặc các lời gọi xuất phát từ một luồng tác vụ có mục đích khác.

Có thể mô tả chính sách như sau:

> **Bao gồm:** các điểm thực thi phương thức gửi đơn hàng. **Loại trừ:** điểm gửi đơn thuộc môi trường kiểm thử hoặc thao tác nội bộ không cần đo.

Hai vế phục vụ các câu hỏi khác nhau:

- **Điều kiện bao gồm** xác định: *“Những điểm nào là ứng viên cho chính sách?”*
- **Điều kiện loại trừ** xác định: *“Trong số các ứng viên đó, những điểm nào phải bỏ qua?”*

Một pointcut có thể biểu đạt cả hai bằng cách kết hợp điều kiện. Nếu viết thiếu vế bao gồm, phạm vi có thể quá rộng; nếu viết thiếu ngoại lệ loại trừ, advice có thể chạy ở những chỗ không mong muốn. Ngược lại, loại trừ quá nhiều có thể khiến chính sách mất độ bao phủ quan trọng.

**Pointcut** chỉ xác định tập điểm được chọn. Nó không tự quyết định hành vi advice là ghi log, đo thời gian hay kiểm toán; những hành vi đó thuộc một thành phần khác trong mô hình lập trình hướng khía cạnh (Aspect-Oriented Programming, AOP).

## 2. Ôn lại bốn thành phần liên quan

| Thành phần | Trách nhiệm | Ví dụ |
|---|---|---|
| **Join point** | Một điểm/sự kiện thực thi mà mô hình AOP có thể nhận diện | Một lần thực thi `submitOrder(...)` |
| **Pointcut** | Điều kiện lọc các join point | Chọn `submitOrder` trừ một số ngữ cảnh |
| **Advice** | Hành vi chạy tại các điểm được chọn | Ghi thời gian và kết quả |
| **Aspect** | Mô-đun hóa pointcut và advice cho mối quan tâm xuyên suốt | Khía cạnh đo lường thao tác đơn hàng |

Một hoạt động có thật trong chương trình không nhất thiết là join point được công cụ đang dùng hỗ trợ. Pointcut **chỉ lọc** những điểm nằm trong mô hình join point của công cụ. Sau đó, mức độ bao phủ thực tế còn phụ thuộc phạm vi weaving và đường thực thi.

Trong bài này, trước hết ta giả định có một **tập join point ứng viên hợp lệ** để hiểu logic bao gồm và loại trừ. Phần sau sẽ tách riêng những giới hạn khi đi từ lý thuyết sang thực thi.

## 3. Biểu thức cốt lõi: bao gồm VÀ không thuộc loại trừ

Ký hiệu:

- `J`: tập join point mà mô hình AOP có thể xem xét trong phạm vi đang xét.
- `I(j)`: điều kiện **bao gồm** tại join point `j`.
- `E(j)`: điều kiện **loại trừ** tại join point `j`.
- `P(j)`: kết quả pointcut cuối cùng.

Công thức thông dụng:

```text
P(j) = I(j) AND NOT E(j)
```

Hoặc dưới dạng tập hợp:

```text
Include = { j thuộc J | I(j) đúng }
Exclude = { j thuộc J | E(j) đúng }

Matched = Include \ Exclude
        = Include ∩ (J \ Exclude)
```

Cách đọc: bắt đầu bằng tất cả điểm thuộc **Include**, sau đó **bỏ** những điểm đồng thời thuộc **Exclude**. Một điểm chỉ được chọn khi nó **được bao gồm** và **không bị loại trừ**.

### 3.1. Bảng chân trị

| Bao gồm I | Loại trừ E | I AND NOT E | Quyết định |
|---|---|---|---|
| Sai | Sai | Sai | Không chọn: vốn không thuộc miền được bao gồm |
| Sai | Đúng | Sai | Không chọn: điều kiện bao gồm vẫn không thỏa |
| Đúng | Sai | **Đúng** | **Chọn** |
| Đúng | Đúng | Sai | Không chọn: bị ngoại lệ loại bỏ |

Hàng cuối rất quan trọng: **một join point khớp điều kiện bao gồm chưa chắc được chọn cuối cùng**. Nếu đồng thời khớp điều kiện loại trừ, nó bị loại.

Hàng thứ hai cũng có ý nghĩa: loại trừ một điểm vốn không thuộc Include **không làm thay đổi** tập Matched. Điều kiện loại trừ không cần thiết phải là tập con của Include để biểu thức có nghĩa, nhưng một phần Exclude ở ngoài Include sẽ không ảnh hưởng tới kết quả hiện tại.

### 3.2. Hình dung hai bước lọc

```text
Tập join point trong phạm vi mô hình J
                    |
                    v
           Điều kiện bao gồm I
                    |
                    v
          Tập ứng viên Include
                    |
                    v
      Bỏ những điểm thỏa E (NOT E)
                    |
                    v
              Matched
                    |
                    v
       Advice áp dụng ở điểm phù hợp
```

Sơ đồ mang ý nghĩa **logic**, không bắt buộc mô tả thứ tự đánh giá vật lý của mọi bộ máy AOP. Công cụ có thể tối ưu, phân tích phần cấu trúc trước, rút gọn hoặc đánh giá điều kiện động tại thời điểm chạy.

## 4. Điều kiện bao gồm thường có dạng nào?

Điều kiện bao gồm xác định **phạm vi có chủ đích** của mối quan tâm xuyên suốt. Một số tiêu chí mà các mô hình AOP có thể hỗ trợ là:

- **Loại join point:** thực thi phương thức, vị trí gọi phương thức, thực thi constructor, truy cập trường… nếu mô hình hỗ trợ.
- **Phạm vi mã:** lớp, nhóm kiểu, gói, thành phần hoặc mô-đun.
- **Tên/chữ ký:** tên phương thức, kiểu tham số, kiểu trả về hoặc đặc điểm khai báo.
- **Metadata:** dấu hiệu khai báo, chẳng hạn annotation, nếu công cụ đọc được.
- **Ngữ cảnh động:** kiểu/giá trị đối số, đối tượng liên quan hoặc trạng thái luồng điều khiển, nếu được hỗ trợ.

Ví dụ diễn đạt độc lập công cụ:

```text
I = METHOD_EXECUTION
    AND module = "orders"
    AND methodName STARTS_WITH "submit"
```

Ta chỉ quan tâm đến **các điểm thực thi phương thức gửi đơn ở mô-đun orders**. Nếu có method `cancelOrder()`, dù xảy ra cùng mô-đun, nó không thuộc Include. Nếu có `submitReport()` ở mô-đun reports, nó cũng không thuộc Include.

**Không nên dùng điều kiện bao gồm “mọi method” nếu yêu cầu thật sự chỉ liên quan một nhóm chức năng.** Phạm vi càng rộng, càng khó biết có bao nhiêu điểm ngoài dự tính được áp dụng khi hệ thống mở rộng.

## 5. Điều kiện loại trừ nhằm giải quyết vấn đề gì?

Điều kiện loại trừ là **ngoại lệ có chủ đích** đối với phạm vi bao gồm. Những ví dụ có giá trị:

- Bỏ qua phương thức chỉ phục vụ thao tác nội bộ không cần quan sát.
- Bỏ qua thao tác thuộc một chế độ thử nghiệm có phạm vi đo riêng.
- Bỏ qua nhóm phương thức tần suất cao, nơi advice gây chi phí vượt lợi ích.
- Bỏ qua điểm có cơ chế xử lý riêng nhằm tránh ghi trùng.
- Bỏ qua hoạt động mà dữ liệu không nên đi vào chính sách ghi nhận.

Ví dụ:

```text
E = isInternalOperation
    OR tier = "TEST"
```

Kết hợp:

```text
P = I AND NOT E
  = I AND NOT (isInternalOperation OR tier = "TEST")
```

Điểm cần chú ý: đây là **hai điều kiện loại trừ** nối bằng OR, nên chỉ cần **một trong hai đúng** là điểm bị loại. Nếu dùng AND bên trong E:

```text
E = isInternalOperation AND tier = "TEST"
```

thì chỉ loại trường hợp **vừa nội bộ vừa thuộc TEST**; nhiều điểm mà người viết định loại có thể vẫn được chọn. Phải đọc đúng từng toán tử, không chỉ hiểu chung chung “có phủ định”.

## 6. Bao gồm không đồng nghĩa với “mọi thứ không bị loại trừ”

So sánh:

```text
P1 = includeOrders AND NOT excludeTests
P2 = NOT excludeTests
```

Hai biểu thức **không tương đương**. `P1` chỉ chọn nhóm orders không bị loại. `P2` là phép phủ định trên toàn bộ **miền J mà biểu thức được đánh giá**, nên có thể chọn rất nhiều join point khác nhóm orders.

Ví dụ J gồm các lần thực thi method của orders, reports và inventory. Nếu `excludeTests` chỉ kiểm tra dấu hiệu TEST, thì `P2` có thể chọn cả reports/inventory không thuộc TEST. Trong một hệ thống lớn, đó thường là phạm vi ngoài ý định.

**Nguyên tắc thiết kế:** ưu tiên một miền bao gồm có ý nghĩa rõ, rồi thêm ngoại lệ cần thiết. Một điều kiện chỉ có `NOT E` đôi khi hợp lệ, nhưng phải chủ động chấp nhận phần bù **rất rộng** của E trên J.

### 6.1. Phép phủ định phụ thuộc miền

Trong toán học:

```text
NOT E = J \ E
```

Điều này không có nghĩa “mọi sự kiện trên thế giới trừ E”. Nó chỉ là phần bù **trong tập join point mà mô hình AOP đang xét**. Nếu công cụ không hỗ trợ field-set join point, dùng `NOT excludeFieldWrites` không biến các thao tác ghi trường thành join point mới.

Đây là điểm dễ nhầm khi dùng wildcard kết hợp phủ định.

## 7. Thứ tự ưu tiên toán tử và dấu ngoặc

Giả sử:

- `A`: điểm thuộc mô-đun orders.
- `B`: điểm thuộc mô-đun invoices.
- `E`: điểm được đánh dấu nội bộ.

Ta muốn bao gồm cả A hoặc B, **nhưng loại các điểm nội bộ ở cả hai nhóm**:

```text
P = (A OR B) AND NOT E
```

Nếu viết sai nhóm:

```text
P_wrong = A OR (B AND NOT E)
```

thì mọi điểm thuộc A vẫn được chọn dù thuộc E; chỉ B chịu điều kiện loại trừ. Hai biểu thức tạo ra kết quả khác nhau.

| A | B | E | (A OR B) AND NOT E | A OR (B AND NOT E) |
|---|---|---|---|---|
| Đúng | Sai | Đúng | **Sai** | **Đúng** |
| Sai | Đúng | Đúng | Sai | Sai |
| Đúng | Sai | Sai | Đúng | Đúng |
| Sai | Đúng | Sai | Đúng | Đúng |

Các ngôn ngữ biểu thức pointcut có thể quy định thứ tự ưu tiên khác nhau hoặc hạn chế phép toán. **Đặt dấu ngoặc tường minh** giúp thể hiện ý định và tránh phụ thuộc vào việc người đọc nhớ một quy tắc cú pháp cụ thể.

## 8. Phép kết hợp nhiều điều kiện: AND, OR, NOT

Với `I1`, `I2` là các tập bao gồm con và `E1`, `E2` là các tập loại trừ:

```text
P = (I1 OR I2) AND NOT (E1 OR E2)
```

Biểu thức trên nghĩa là:

1. Chọn ứng viên thuộc **ít nhất một** trong hai nhóm `I1`, `I2`.
2. Loại những điểm thỏa **bất kỳ** điều kiện loại trừ nào, `E1` hoặc `E2`.

Theo luật De Morgan:

```text
NOT (E1 OR E2) = (NOT E1) AND (NOT E2)
NOT (E1 AND E2) = (NOT E1) OR (NOT E2)
```

Vì vậy có thể viết tương đương về logic:

```text
P = (I1 OR I2) AND (NOT E1) AND (NOT E2)
```

Nhưng điều này chỉ bảo đảm tương đương khi các phần là **predicate logic có cùng cách đánh giá**. Nếu một cơ chế cho phép điều kiện gây tác dụng phụ hoặc có semantics lỗi/giá trị không thông thường, cần kiểm chứng riêng. Về thiết kế, nên giữ biểu thức chọn mang tính dự đoán và hạn chế tác dụng phụ.

### 8.1. Bao gồm rộng hơn có xu hướng mở rộng tập kết quả

Nếu `I1` là tập con của `I2` và Exclude giữ nguyên:

```text
(I1 \ E) ⊆ (I2 \ E)
```

Mở rộng Include không làm mất những điểm đã chọn theo mô hình tập hợp. Nó có thể làm tăng các điểm được chọn.

### 8.2. Loại trừ rộng hơn có xu hướng thu hẹp tập kết quả

Nếu `E1` là tập con của `E2` và Include giữ nguyên:

```text
(I \ E2) ⊆ (I \ E1)
```

Thêm ngoại lệ loại trừ có thể giảm độ bao phủ, nhưng không tự tăng nó. Đây là cách kiểm tra định hướng khi sửa pointcut: nếu vừa mở rộng Exclude mà số điểm khớp tăng bất thường, cần xem lại logic, cú pháp hoặc điều kiện động.

## 9. Ví dụ Java 17: lọc bao gồm/loại trừ trên tập join point giả lập

Để tập trung vào **logic lựa chọn**, chương trình sau mô hình hóa mỗi sự kiện ứng viên bằng một `record`. Nó dùng `Predicate` để thể hiện Include, Exclude và các yếu tố bao phủ. Đây là **mô phỏng bộ lọc bằng Java thuần**, không phải một công cụ weaving AOP thực sự.

Ví dụ cố ý kiểm tra thêm:

- `model`: sự kiện có thuộc **loại join point được cơ chế giả định hỗ trợ** không?
- `woven`: phần mã liên quan có được **giả định** nằm trong phạm vi can thiệp không?
- `include`: sự kiện có thuộc nhóm được chọn?
- `exclude`: sự kiện có thuộc nhóm ngoại lệ?

Lưu file `PointcutIncludeExcludeDemo.java`:

```java
import java.util.List;
import java.util.function.Predicate;

public class PointcutIncludeExcludeDemo {
    enum Kind {
        METHOD_EXECUTION,
        CONSTRUCTOR_EXECUTION
    }

    record Candidate(
            String id,
            Kind kind,
            String module,
            String action,
            boolean internal,
            String tier,
            boolean woven
    ) {}

    static String flag(boolean value) {
        return value ? "Y" : "N";
    }

    public static void main(String[] args) {
        Predicate<Candidate> model =
                c -> c.kind() == Kind.METHOD_EXECUTION;

        Predicate<Candidate> include =
                c -> "orders".equals(c.module())
                        && c.action().startsWith("submit");

        Predicate<Candidate> exclude =
                c -> c.internal() || "TEST".equals(c.tier());

        Predicate<Candidate> selected =
                model.and(include)
                        .and(exclude.negate())
                        .and(Candidate::woven);

        List<Candidate> cases = List.of(
                new Candidate("orderVIP", Kind.METHOD_EXECUTION,
                        "orders", "submitOrder", false, "VIP", true),
                new Candidate("orderTest", Kind.METHOD_EXECUTION,
                        "orders", "submitOrder", false, "TEST", true),
                new Candidate("internal", Kind.METHOD_EXECUTION,
                        "orders", "submitOrder", true, "VIP", true),
                new Candidate("cancel", Kind.METHOD_EXECUTION,
                        "orders", "cancelOrder", false, "VIP", true),
                new Candidate("constructor", Kind.CONSTRUCTOR_EXECUTION,
                        "orders", "submitOrder", false, "VIP", true),
                new Candidate("unwoven", Kind.METHOD_EXECUTION,
                        "orders", "submitOrder", false, "VIP", false)
        );

        for (Candidate c : cases) {
            System.out.printf(
                    "%s: model=%s include=%s exclude=%s woven=%s -> advice=%s%n",
                    c.id(),
                    flag(model.test(c)),
                    flag(include.test(c)),
                    flag(exclude.test(c)),
                    flag(c.woven()),
                    flag(selected.test(c))
            );
        }
    }
}
```

Chạy bằng Java 17 hoặc mới hơn:

```bash
javac PointcutIncludeExcludeDemo.java
java PointcutIncludeExcludeDemo
```

Kết quả stdout:

```text
orderVIP: model=Y include=Y exclude=N woven=Y -> advice=Y
orderTest: model=Y include=Y exclude=Y woven=Y -> advice=N
internal: model=Y include=Y exclude=Y woven=Y -> advice=N
cancel: model=Y include=N exclude=N woven=Y -> advice=N
constructor: model=N include=Y exclude=N woven=Y -> advice=N
unwoven: model=Y include=Y exclude=N woven=N -> advice=N
```

**Diễn giải:**

- **`orderVIP`** thuộc mô hình, được bao gồm, không bị loại trừ và nằm trong giả định weaving scope, nên được chọn.
- **`orderTest`** thỏa Include nhưng có tier TEST, nên Exclude đúng và không được chọn.
- **`internal`** thỏa Include nhưng là thao tác nội bộ, nên Exclude đúng và không được chọn.
- **`cancel`** không thỏa Include; không cần có ngoại lệ loại trừ vẫn không được chọn.
- **`constructor`** có thuộc tính được Include chọn theo tên/mô-đun, nhưng **khác loại join point** mà mô hình giả định hỗ trợ nên không đủ điều kiện.
- **`unwoven`** thỏa điều kiện pointcut khái niệm, nhưng bị đánh dấu ngoài phạm vi weaving nên không có advice được kích hoạt trong mô phỏng.

Hàm `selected` của ví dụ là phép kết hợp **điều kiện pointcut và điều kiện giả lập về mô hình/triển khai** để giải thích kết quả cuối. Trong một công cụ AOP thực, những ranh giới như join point model và weaving scope có thể được giải quyết bên ngoài biểu thức pointcut; chúng **không nhất thiết là những predicate người dùng phải tự viết**.

### 9.1. Sự khác nhau giữa hàm chọn và advice

Chương trình trên chỉ tính toán nhãn `advice=Y/N` để minh họa. Nó **không có aspect weaving** và không gắn hành vi vào những phương thức thật. Trong AOP, pointcut xác định điểm; advice mới là phần thực hiện việc đo thời gian, ghi vết hoặc kiểm toán khi điểm được chọn và cơ chế can thiệp được kích hoạt.

Chức năng học tập của đoạn Java là làm rõ **logic bao gồm/loại trừ** và lý do một điểm không được áp dụng. Không nên dùng việc hàm `Predicate.test()` trả true như bằng chứng một runtime AOP bất kỳ chắc chắn chạy advice.

## 10. Nhận diện sự khác nhau giữa “không bao gồm” và “bị loại trừ”

Hai trường hợp cùng dẫn đến kết quả **không chọn**, nhưng nguyên nhân và hướng sửa khác nhau.

**Không bao gồm:** `cancelOrder()` không thuộc quy tắc bao gồm `submit*`. Nếu yêu cầu thay đổi để áp dụng cả `cancelOrder`, cần **mở rộng Include** hoặc thêm một nhóm bao gồm phù hợp.

**Bị loại trừ:** `submitOrder("TEST")` đã thuộc Include nhưng bị chính sách Exclude gạt ra. Nếu cần áp dụng ở TEST, phải **thu hẹp hoặc sửa Exclude**.

Đây là sự khác biệt quan trọng trong chẩn đoán. Nếu chỉ biết “không có log”, người phát triển có thể sửa sai phần của pointcut và tạo ra vùng bao phủ ngoài ý định.

| Tình trạng | Include | Exclude | Hướng xem xét |
|---|---|---|---|
| Ngoài phạm vi định trước | Sai | Có thể sai/đúng | Kiểm tra định nghĩa Include |
| Trúng ngoại lệ | Đúng | Đúng | Kiểm tra định nghĩa Exclude |
| Đủ điều kiện logic | Đúng | Sai | Kiểm tra mô hình join point, weaving và runtime path nếu advice không chạy |
| Loại join point không hỗ trợ | Có thể đúng về mô tả | Có thể sai | Chọn loại join point/cơ chế hỗ trợ phù hợp |

Điều kiện loại trừ thường được đặt với mục đích xử lý **ngoại lệ bên trong miền bao gồm**, chứ không phải làm công cụ thay thế cho việc xác định miền đó.

## 11. Quy tắc loại trừ có phải luôn ưu tiên hơn bao gồm?

Với **một biểu thức pointcut** được định nghĩa đúng là:

```text
P = I AND NOT E
```

thì một điểm vừa thuộc I vừa thuộc E **không được chọn**. Theo logic, E thắng ở **biểu thức này** vì toàn bộ P trả sai.

Tuy nhiên, không nên mở rộng kết luận thành “một exclusion pointcut có thể tắt mọi aspect trong hệ thống”. Giả sử có **hai pointcut độc lập**:

```text
P_A = I AND NOT E
P_B = B
```

Một điểm bị loại khỏi `P_A` vẫn **có thể khớp `P_B`** và kích hoạt advice gắn với `P_B`. Trong một hệ thống có nhiều aspect, mỗi chính sách có miền chọn riêng.

Một số môi trường có thêm cấu hình enable/disable, ordering hoặc các cơ chế điều phối riêng. Chúng không phải là phép phủ định logic mặc nhiên. Vì vậy phải nói rõ **ngoại lệ thuộc pointcut/aspect nào**.

## 12. Exclude rộng quá và exclude hẹp quá

### 12.1. Exclude quá rộng (*over-exclusion*)

Ví dụ:

```text
I = mọi thao tác submit của đơn hàng
E = mọi thao tác được gọi từ phần xử lý nội bộ
```

Nếu gần như mọi thao tác submit đều đi qua một lớp điều phối nội bộ, E có thể loại **gần như toàn bộ Include**, làm chính sách quan sát trở nên vô dụng.

**Cách kiểm soát:** đo tập khớp thực tế, có bảng trường hợp phải chọn và phải loại; chỉ định nghĩa ngoại lệ theo đúng ý nghĩa thay vì dùng một tên lớp/phương thức quá chung.

### 12.2. Exclude quá hẹp (*under-exclusion*)

Ví dụ một chính sách cần bỏ qua cả `submitDraft()` và `submitTestOrder()`, nhưng E chỉ mô tả `submitDraft()`. Khi đó `submitTestOrder()` vẫn được chọn nếu thuộc I.

**Cách kiểm soát:** tổng hợp đầy đủ các nhóm ngoại lệ và kiểm thử từng nhóm. Không phụ thuộc vào việc một ví dụ thử duy nhất đã bị loại.

### 12.3. Include quá rộng và Exclude chắp vá

Có những biểu thức bắt đầu từ “mọi phương thức” rồi loại trừ hàng loạt tên, gói và ngoại lệ. Chúng có thể khó bảo trì khi thêm chức năng mới; một phương thức mới rất dễ lọt vào Include vì chưa có điều kiện Exclude tương ứng.

Thiết kế vững hơn thường bắt đầu từ **vùng bao gồm đủ cụ thể**, sau đó loại các ngoại lệ thực sự. Nếu chỉ dùng một danh sách loại trừ ngày càng dài, cần xem lại liệu điều kiện bao gồm có đang thể hiện đúng chủ đích của chính sách hay không.

## 13. Kiểm thử pointcut phải có ca dương và ca âm

Kiểm thử một pointcut chỉ bằng một điểm “được áp dụng thành công” là chưa đủ. Cần cả:

- **Ca dương (positive):** điểm phải được bao gồm và không bị loại.
- **Ca âm ngoài Include:** điểm vốn không thuộc miền chính sách.
- **Ca âm do Exclude:** điểm thuộc Include nhưng phải bị gỡ.
- **Ca biên:** tên gần giống, overload, giá trị `null`, metadata khác vị trí và điều kiện động.
- **Ca bao phủ kỹ thuật:** điểm đúng logic nhưng bị hạn chế bởi loại join point, phạm vi weaving hoặc đường gọi.

Ví dụ bảng kiểm thử:

| Join point/điều kiện | Kỳ vọng | Lý do |
|---|---|---|
| `orders.submit("VIP")` | Chọn | Thỏa Include, không thỏa Exclude |
| `orders.submit("TEST")` | Không chọn | Thuộc Include nhưng Exclude đúng |
| `orders.cancel("VIP")` | Không chọn | Không thuộc Include |
| Thao tác submit nội bộ | Không chọn | Exclude đúng |
| `reports.submit("VIP")` | Không chọn | Khác phạm vi mô-đun |
| Thao tác có dữ liệu `null` | Phụ thuộc điều kiện đã định nghĩa | Cần quy tắc rõ cho `null` |
| Điểm được chọn nhưng lời gọi không đi qua ranh giới can thiệp | Có thể không kích hoạt advice | Kiểm tra cơ chế và runtime path |

Không được đánh đồng “`P(j)` đúng” với “advice chắc chắn đã chạy”. Một lần thực thi chỉ kích hoạt advice nếu mô hình và cơ chế can thiệp **thực sự quan sát được** điểm đó.

## 14. Các trường hợp biên: overload, metadata, giá trị động

### 14.1. Phương thức overload

```java
void submit(String id);
void submit(String id, boolean dryRun);
```

Đây là **đoạn khai báo minh họa**, không phải chương trình độc lập. Nếu điều kiện Include chỉ xét tên `submit`, nó có thể khớp **cả hai chữ ký**. Nếu muốn loại `dryRun=true`, điều kiện Exclude cần xét ngữ cảnh thích hợp (nếu công cụ hỗ trợ), hoặc hợp đồng thiết kế nên phân biệt rõ mục đích hai phương thức.

Một pointcut chọn đúng chữ ký cần dựa trên loại join point và kiểu đối số, không chỉ dựa vào chữ cái đầu trong tên.

### 14.2. Metadata có thể nằm ở những khai báo khác nhau

Một dấu hiệu dùng làm điều kiện Include/Exclude có thể xuất hiện trên giao diện, lớp triển khai hoặc phương thức cụ thể. Không phải cơ chế nào cũng đọc metadata theo cùng một cách. Nếu điểm “được đánh dấu ngoại lệ” vẫn bị chọn, cần xác định **công cụ đang xét metadata ở đâu** trước khi kết luận biểu thức NOT sai.

### 14.3. Đối số động và `null`

```text
E = tier == "TEST"
```

Điều kiện này không tự định nghĩa cách xử lý `null`, thiếu đối số, hoặc đối tượng con có kiểu khác. Với Java, một cách kiểm tra giá trị chuỗi an toàn là `"TEST".equals(tier)`. Nhưng không nên cho rằng mọi pointcut DSL đều dùng cú pháp Java hoặc có cùng quy tắc so sánh.

### 14.4. Điều kiện về caller, callee và đối tượng đích

Một thao tác có thể được gọi từ nhiều nơi. Nếu Exclude là “bỏ qua lời gọi xuất phát từ caller nội bộ”, cần làm rõ đó là điều kiện tại **method call join point**, **method execution join point có ngữ cảnh luồng điều khiển**, hay một quan sát khác. Cùng tên phương thức không bảo đảm hai mô hình lựa chọn có cùng thông tin caller.

## 15. Khi nào nên dùng điều kiện loại trừ?

Điều kiện loại trừ có ích khi:

1. Chính sách bao gồm có **miền chính ổn định** và chỉ có một số ngoại lệ nhỏ, rõ nghĩa.
2. Ngoại lệ có **lý do kiến trúc hoặc hợp đồng** cụ thể, chẳng hạn tránh quan sát trùng một ranh giới đã được đo riêng.
3. Các điểm bị loại có thể được mô tả bằng điều kiện **ổn định, kiểm thử được**, ít phụ thuộc tên ngẫu nhiên.
4. Mọi ngoại lệ đều được xem xét khi thêm đường gọi hoặc thay đổi chức năng.

Nếu ngoại lệ được dùng để sửa một Include quá chung, làm pointcut khó đọc, hoặc để che một đường thực thi chưa được weaving, cần xem lại thiết kế. Exclude **không chữa** được việc loại join point không được hỗ trợ hoặc cách can thiệp không đi qua điểm mong muốn.

### 15.1. Tên gọi mô tả ý định

Các tên như `orderSubmissionOperations` và `internalDiagnosticOperations` (chỉ là tên khái niệm) thường dễ hiểu hơn những chuỗi wildcard không giải thích ý nghĩa. Một biểu thức có tên nên trả lời được:

- “Chính sách này bao phủ nghiệp vụ/ranh giới gì?”
- “Vì sao nhóm bị loại không cần nhận advice?”
- “Một người thêm phương thức mới sẽ biết quy tắc này áp dụng hay không bằng cách nào?”

## 16. Tương tác giữa nhiều pointcut và nhiều advice

Một join point có thể được nhiều pointcut lựa chọn đồng thời. Ví dụ:

```text
P_metrics = submitOperations AND NOT testOperations
P_audit   = sensitiveOperations
```

Một thao tác `submit` của nhóm nhạy cảm có thể khớp cả hai. Việc nó bị loại khỏi `P_metrics` do thuộc TEST **không nhất thiết làm nó bị loại khỏi `P_audit`**.

Ngoài ra, các advice chạy trên cùng join point có thể ảnh hưởng thứ tự quan sát, giá trị trả về, ngoại lệ hoặc số lần thực thi gốc (đặc biệt với advice bao quanh có khả năng điều phối lời gọi). **Bao gồm/loại trừ là quy tắc lựa chọn, không phải quy tắc tự động xác định thứ tự advice.**

Khi chính sách bao phủ có yêu cầu an toàn hoặc kiểm toán, cần kiểm tra từng aspect và sự phối hợp giữa chúng, không chỉ đếm một log xuất hiện.

## 17. Phân biệt “bị loại trừ” với “không thuộc mô hình join point”

Xét ba trường hợp cùng cho kết quả “không có advice”:

**Trường hợp A — Ngoài Include:** pointcut chọn execution của `submit*`, nhưng thực thi là `cancelOrder()`.

**Trường hợp B — Bị Exclude:** pointcut chọn `submit*` ngoại trừ tier TEST; thực thi là `submitOrder("TEST")`.

**Trường hợp C — Ngoài mô hình/weaving:** pointcut có vẻ chọn mọi `submit*`, nhưng thao tác thực tế là gán trường trực tiếp mà công cụ chỉ chặn được lời gọi phương thức qua proxy; hoặc lời gọi method đi thẳng đến đối tượng đích, không qua proxy.

Cách khắc phục khác nhau:

| Trường hợp | Tầng cần thay đổi nếu muốn có advice |
|---|---|
| Ngoài Include | Điều kiện bao gồm |
| Bị Exclude | Điều kiện loại trừ |
| Loại join point không hỗ trợ | Mô hình/công cụ hoặc ranh giới thiết kế |
| Điểm có hỗ trợ nhưng không được weaving | Phạm vi xử lý mã/cấu hình weaving |
| Lời gọi không đi qua cơ chế can thiệp | Đường thực thi, cách truy cập đối tượng hoặc kỹ thuật weaving |

Một biểu thức `NOT excluded` dù đúng cũng không làm cho một hoạt động ngoài mô hình trở thành join point. Đây là giới hạn **độc lập** với logic tập hợp của Include/Exclude.

## 18. Static matching và dynamic matching

Một số điều kiện có thể dựa trên cấu trúc biết trước, chẳng hạn tên phương thức hoặc lớp khai báo. Những điều kiện khác cần dữ liệu tại thời điểm chạy, chẳng hạn giá trị đối số, trạng thái đối tượng hoặc luồng điều khiển.

Ví dụ:

```text
I_static  = methodName bắt đầu bằng "submit"
E_static  = phương thức thuộc nhóm diagnostic
E_dynamic = tier thực tế bằng "TEST"

P = I_static AND NOT (E_static OR E_dynamic)
```

Khi đánh giá thực tế, một công cụ có thể loại trước những điểm không thỏa điều kiện cấu trúc rồi mới kiểm tra dữ liệu động. Nhưng **thời điểm, mức độ tối ưu và khả năng biểu diễn điều kiện** tùy công cụ.

Điều kiện loại trừ động không được xem là một thuộc tính bất biến của phương thức: cùng `submitOrder(String)`, lời gọi với `"VIP"` và `"TEST"` có thể có kết quả chọn khác nhau.

Cũng phải cẩn thận với môi trường bất đồng bộ: ngữ cảnh theo luồng có thể thay đổi hoặc không được truyền qua tác vụ khác. Nếu Exclude phụ thuộc ngữ cảnh, cần xác định chính xác **nó được đọc tại join point nào**.

## 19. Tối ưu biểu thức mà không thay đổi ngữ nghĩa

Một pointcut nhiều điều kiện có thể được diễn đạt theo các dạng tương đương:

```text
P = I AND NOT (E1 OR E2)
P = I AND NOT E1 AND NOT E2
```

Với predicate thuần, hai cách có cùng kết quả logic. Người đọc có thể chọn cách trực quan hơn. Tuy nhiên:

- Không giả định ngôn ngữ pointcut có cùng ký hiệu/độ ưu tiên với Java.
- Không viết điều kiện chọn có tác dụng phụ để dựa vào thứ tự đánh giá.
- Không mở rộng miền bao gồm chỉ để “tối ưu” nếu làm mất ý nghĩa chính sách.
- Không loại bớt điều kiện Exclude mà chưa chứng minh chúng dư thừa trong **miền Include hiện tại**.

Nếu `E2` chỉ có ý nghĩa ở ngoài Include, nó có thể **không ảnh hưởng ở thời điểm này**; nhưng loại nó khỏi chính sách có thể làm thay đổi hành vi khi Include được mở rộng trong tương lai. Cần dựa trên hợp đồng và kiểm thử, không chỉ trên một tập dữ liệu mẫu.

## 20. Bảo toàn kết quả nghiệp vụ và quan sát đúng

Một pointcut chọn đúng chưa có nghĩa một aspect triển khai đúng. Khi advice tham gia vào luồng xử lý, cần kiểm chứng:

- **Return value** của thao tác gốc có được giữ đúng theo hợp đồng không?
- **Ngoại lệ** được bảo toàn, phân loại hay chuyển đổi có chủ đích?
- **Số lần thực thi** phần nghiệp vụ gốc có đúng không?
- **Kết quả khi Exclude đúng** có hoàn toàn bỏ qua advice mong muốn?
- **Dữ liệu được ghi nhận** có phù hợp và tránh lộ thông tin nhạy cảm?
- **Chi phí** đánh giá điều kiện động và advice có chấp nhận được?

Đặc biệt, không nên đặt một kiểm tra bắt buộc ở pointcut có rủi ro bỏ sót đường đi mà không xác minh vùng bao phủ. Exclude được mô tả để giới hạn chính sách, nhưng một ngoại lệ thiếu chặt chẽ có thể vô tình tạo ra một “lỗ hổng” kiểm soát nếu chính sách liên quan đến tính đúng đắn hoặc an toàn.

## 21. Quy trình xây dựng pointcut có Include và Exclude

1. **Viết một câu mục đích:** ví dụ “Đo thời gian thao tác gửi đơn trong mô-đun orders, ngoại trừ chẩn đoán nội bộ và chế độ TEST”.
2. **Xác định loại join point:** execution hay call, loại method hoặc sự kiện nào? Công cụ có hỗ trợ không?
3. **Định nghĩa Include độc lập:** trước hết chọn chính xác vùng nghiệp vụ cần áp dụng.
4. **Liệt kê Exclude với lý do rõ:** mỗi ngoại lệ nhằm tránh hành vi không mong muốn nào?
5. **Ghép theo logic có dấu ngoặc:** `P = I AND NOT (E1 OR E2)` nếu yêu cầu là bỏ các điểm thuộc bất kỳ ngoại lệ nào.
6. **Liệt kê ca dương và âm:** có điểm khớp toàn bộ, không khớp Include, khớp cả Include/Exclude và các ca biên.
7. **Xác minh mô hình và weaving:** pointcut có thể đúng về logic nhưng không áp dụng ở một đường thực thi thực tế.
8. **Kiểm thử hành vi advice và nghiệp vụ:** bảo toàn kết quả, ngoại lệ, số lần gọi và quan sát đúng điểm.
9. **Rà soát khi đổi API hoặc thêm đường gọi:** một phương thức mới có thể lọt vào Include hoặc Exclude ngoài ý định.

Các bước này giúp pointcut trở thành **một hợp đồng lựa chọn có thể giải thích và kiểm chứng**, không chỉ là một biểu thức wildcard khó đọc.

## 22. Bài tập tự kiểm tra

### Bài 1: Đọc công thức

Cho:

```text
I = methodName STARTS_WITH "process"
E = isInternal OR tier = "TEST"
P = I AND NOT E
```

Điểm nào được chọn?

| Điểm | isInternal | tier | Kết quả |
|---|---|---|---|
| `processOrder` | false | VIP | **Chọn** |
| `processOrder` | false | TEST | Không chọn |
| `processOrder` | true | VIP | Không chọn |
| `cancelOrder` | false | VIP | Không chọn |

**Giải thích:** chỉ hàng đầu thỏa Include và không thỏa Exclude.

### Bài 2: Dấu ngoặc

Một pointcut cần chọn thao tác thuộc A hoặc B nhưng loại tất cả các thao tác nội bộ E. Chọn biểu thức nào?

```text
(1) (A OR B) AND NOT E
(2) A OR (B AND NOT E)
```

**Đáp án:** (1). Với (2), một điểm thuộc A luôn được chọn, kể cả khi E đúng.

### Bài 3: Phủ định quá rộng

Cho `P = NOT isTest`. Vì sao chính sách có thể trở nên quá rộng?

**Đáp án:** đó là phần bù của `isTest` trên toàn miền join point J. Các điểm không phải TEST thuộc nhiều nhóm nghiệp vụ cũng có thể được chọn. Cần một Include xác định miền chính sách.

### Bài 4: Hai aspect độc lập

Điểm `j` vừa khớp `I` vừa khớp `E` trong `P_A = I AND NOT E`. Nhưng `j` đồng thời khớp `P_B` của aspect khác. Advice B có thể chạy không?

**Đáp án:** có thể. Exclude trong `P_A` làm điểm không được chọn **bởi P_A**, không phải cơ chế tắt mọi advice của những pointcut khác.

### Bài 5: Phân tích thiếu log

`submitOrder("VIP")` thỏa Include và không thuộc Exclude, nhưng advice không chạy. Nêu ba khả năng khác.

**Gợi ý:** loại join point cần thiết không nằm trong mô hình công cụ; mã/đối tượng nằm ngoài phạm vi weaving; đường gọi không đi qua cơ chế chặn đang dùng. Cũng cần kiểm tra ca thử có phát sinh join point thật không.

### Bài 6: Thay đổi phạm vi

Trước kia chỉ đo `submitOrder`. Nay phải đo cả `cancelOrder`, nhưng không đo TEST. Phải thay đổi phần nào?

**Đáp án:** mở rộng Include sang `cancelOrder` và giữ Exclude cho nhóm TEST, nếu không có ngoại lệ mới. Sau đó kiểm thử cả hai nhóm.

### Bài 7: Exclude làm chính sách rỗng

Nếu mọi điểm trong Include cũng thuộc Exclude, còn điểm nào được chọn?

**Đáp án:** không có; `Include \ Exclude = ∅`. Đây có thể là cấu hình cố ý, nhưng thường là tín hiệu cần rà soát nếu chính sách được kỳ vọng có độ bao phủ.

## 23. Ghi nhớ

**Include** trả lời “những join point nào nằm trong phạm vi chính sách?”. **Exclude** trả lời “trong phạm vi đó, những join point nào phải bỏ qua?”. Công thức chuẩn để đọc hai nhóm điều kiện là:

```text
Selected = Include AND NOT Exclude
SelectedSet = IncludeSet \ ExcludeSet
```

Khi có nhiều nhóm, dùng **AND/OR/NOT với dấu ngoặc rõ ràng**, kiểm thử cả trường hợp được chọn và bị loại, và bảo đảm ý nghĩa của các điều kiện tĩnh/động phù hợp.

Cuối cùng, phải nhớ rằng một điểm **không được chọn** có thể do **ngoài Include**, **nằm trong Exclude**, **không thuộc mô hình join point**, **không được weaving** hoặc **đường chạy không đi qua cơ chế can thiệp**. Phân biệt đúng nguyên nhân là chìa khóa để thiết kế và chẩn đoán pointcut chính xác.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-basics-010">Chọn phương thức nạp chồng và tránh khớp ngoài ý muốn</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn phương thức nạp chồng và tránh khớp ngoài ý muốn bằng pointcut trong AOP

**Nạp chồng phương thức (method overloading)** cho phép nhiều phương thức mang cùng tên nhưng khác danh sách tham số. Đây là cơ chế phổ biến trong Java để cung cấp nhiều cách gọi một chức năng. Tuy nhiên, đối với lập trình hướng khía cạnh (Aspect-Oriented Programming, **AOP**), việc lựa chọn pointcut chỉ bằng **tên phương thức** dễ vô tình can thiệp vào nhiều phương thức nạp chồng vốn có mục đích, dữ liệu, điều kiện và tác dụng phụ khác nhau.

Ví dụ, lớp xử lý thanh toán có cả `pay(String orderId)` và `pay(String orderId, boolean retry)`. Nếu một khía cạnh kiểm toán được yêu cầu áp dụng **chỉ với thao tác một tham số**, pointcut kiểu “mọi phương thức có tên `pay`” quá rộng: nó có thể khớp cả phiên bản hai tham số. Một chính sách thử lại, giới hạn tần suất, bảo mật hoặc chuyển đổi kết quả được áp nhầm còn có thể làm sai hợp đồng nghiệp vụ.

Bài này trình bày cách **chọn đúng overload theo chữ ký**, nhận diện những tình huống khó như `null`, kiểu nguyên thủy, varargs, generics, kế thừa, phương thức cầu nối và khác biệt giữa **điểm gọi** với **điểm thực thi**. Cú pháp pointcut minh họa bằng một mô hình AOP có điều kiện chọn phương thức; các đặc điểm không thuộc Java chuẩn sẽ được ghi rõ. Ví dụ Java 17 độc lập giúp kiểm tra hành vi thực tế.

## 1. Kết quả cần đạt

Sau bài học, bạn có thể:

1. Giải thích thế nào là overload trong Java và phân biệt với override.
2. Xác định tập phương thức mà một pointcut dự kiến chọn, dựa trên **tên + số lượng + thứ tự + kiểu tham số**, thay vì chỉ nhìn tên.
3. Phân biệt **chữ ký khai báo của phương thức** với **kiểu thực của giá trị truyền vào**, nhất là khi đối số là `null` hoặc một đối tượng đa hình.
4. Hiểu lý do điểm **method call** và **method execution** có thể đòi hỏi điều kiện khác nhau trong mô hình AOP.
5. Nhận diện rủi ro khi dùng wildcard, varargs, generic erasure, bridge method, method inheritance hoặc proxy.
6. Viết bộ lọc chữ ký tường minh và kiểm tra cả những phương thức **phải khớp** lẫn **không được khớp**.
7. Giữ nguyên kết quả, ngoại lệ và tác dụng phụ của các overload khi advice chỉ có mục đích quan sát.

## 2. Hiểu đúng nạp chồng trong Java

Xét một bộ chuyển đổi thông tin:

```java
class Formatter {
    String format(String text) { return "text=" + text; }
    String format(Object value) { return "object=" + value; }
    String format(String text, int times) { return "batch=" + text + ":" + times; }
}
```

Đây là **đoạn minh họa**, chưa phải chương trình độc lập. Ba phương thức có cùng tên `format` nhưng khác **danh sách tham số**:

| Chữ ký minh họa | Số tham số | Kiểu theo thứ tự |
| --- | ---: | --- |
| `format(String)` | 1 | `String` |
| `format(Object)` | 1 | `Object` |
| `format(String, int)` | 2 | `String`, `int` |

Trong Java, để xác định overload cần quan tâm đến **kiểu và thứ tự các tham số**. Tên tham số không phân biệt overload. Chỉ đổi **kiểu trả về** cũng không tạo được một overload hợp lệ khi tên và danh sách tham số trùng nhau.

Ví dụ sau không hợp lệ nếu hai khai báo nằm trong cùng một lớp:

```java
int count(String item) { return 1; }
long count(String item) { return 1L; } // Lỗi biên dịch: trùng chữ ký.
```

Cả hai cùng có `count(String)`. Kiểu trả về quan trọng khi kiểm tra hợp đồng/cách gắn advice, nhưng **không phải khóa phân biệt overload** của hai khai báo này.

### 2.1. Nạp chồng (overload) và ghi đè (override) khác nhau

- **Overload:** cùng tên, danh sách tham số khác; Java lựa chọn phiên bản thích hợp chủ yếu ở **thời điểm biên dịch** theo kiểu biểu thức và các quy tắc giải quyết overload.
- **Override:** lớp con cung cấp hiện thực cho phương thức có quan hệ ghi đè với phương thức lớp cha/giao diện; đối với phương thức instance có khả năng dispatch động, **hiện thực được chạy** được quyết định tại thời gian chạy theo đối tượng nhận.

Ví dụ:

```java
class Parent {
    String use(Object value) { return "parent-object"; }
    String use(String value) { return "parent-string"; }
}
class Child extends Parent {
    @Override
    String use(Object value) { return "child-object"; }
}
```

Với `Parent item = new Child()`, lời gọi `item.use((Object) "x")` chọn chữ ký `use(Object)` rồi thực thi phiên bản ghi đè ở `Child`. Còn `item.use("x")` chọn `use(String)` và sử dụng hiện thực được kế thừa từ `Parent`. Nếu dùng pointcut, phải hỏi đang chọn **chữ ký nào**, **loại join point nào** và **mã nào thực sự chạy**.

## 3. Vì sao pointcut theo tên dễ khớp sai?

Giả sử một dịch vụ có:

```java
void submit(String orderId);
void submit(String orderId, int copies);
void submit(Object payload);
```

Một pointcut với ý tưởng “mọi phương thức tên `submit`” sẽ không phân biệt ba hành vi:

- `submit(String)` có thể là đường xử lý một đơn hàng đã được xác minh;
- `submit(String, int)` có thể là thao tác tạo nhiều bản hoặc thực hiện theo lô;
- `submit(Object)` có thể là cổng tiếp nhận chung và cần kiểm tra kiểu đầu vào khác.

Áp **ghi log kỹ thuật** nhầm có thể dẫn tới log dư. Áp **kiểm toán bắt buộc**, **retry**, **kiểm soát quyền** hoặc **giới hạn tần suất** nhầm có thể nghiêm trọng hơn: bỏ sót sự kiện, nhân đôi tác dụng phụ hoặc chặn thao tác hợp lệ.

Vấn đề không nằm ở tính năng overload của Java; nó nằm ở việc **pointcut biểu đạt thiếu điều kiện lựa chọn** so với yêu cầu chính sách.

### 3.1. Ba tập cần tách biệt

Khi đánh giá một pointcut, hãy phân biệt ba tập:

1. **Tập mong muốn:** những thao tác nghiệp vụ mà chính sách cần áp dụng theo thiết kế.
2. **Tập khớp theo biểu thức:** những join point thực tế được cú pháp pointcut lựa chọn trong mô hình AOP.
3. **Tập có hiệu lực khi chạy:** những join point vừa khớp vừa nằm trong phạm vi được weaving hoặc thực sự đi qua proxy/interceptor.

Một biểu thức đúng về chữ ký nhưng được áp ở đường gọi không qua proxy vẫn không chạy advice. Ngược lại, một cơ chế weaving hoạt động tốt vẫn có thể áp nhầm phương thức khi pointcut dùng mẫu tham số quá rộng. Cần kiểm chứng **cả bộ lọc lẫn cơ chế thực thi**.

## 4. Cách chọn overload bằng chữ ký chính xác

Một điều kiện cơ bản có thể gồm:

```text
loại join point
    AND phạm vi kiểu/lớp/gói
    AND tên phương thức
    AND số lượng tham số
    AND kiểu từng tham số theo đúng thứ tự
    AND điều kiện bổ sung (nếu cần)
```

Với yêu cầu “chỉ đo việc xử lý một mã đơn hàng kiểu String”, điều kiện lý tưởng cần chỉ ra `submit(String)`. Đừng tự mở rộng sang `submit(Object)` dù tại thời gian chạy đối số có thể cũng là một `String`.

### 4.1. Ví dụ cú pháp pointcut dành riêng cho AspectJ

Trong một mô hình AOP có bộ chỉ định `execution` như AspectJ, hai biểu thức minh họa sau có ý nghĩa khác nhau:

```text
execution(* example.orders.OrderService.submit(java.lang.String))
execution(* example.orders.OrderService.submit(..))
```

- Biểu thức **đầu** chọn điểm thực thi có **một tham số được khai báo là `String`** tại mẫu kiểu và tên đã chỉ định.
- Biểu thức **sau** dùng `..` để chấp nhận **danh sách tham số bất kỳ**, nên có thể chọn cả các overload `submit()`, `submit(String)`, `submit(Object)`, `submit(String, int)` nếu các điểm thực thi đó thuộc tập kiểu khớp.

Ký hiệu ở trên **không phải Java thuần**, không chạy bằng `javac`, và không phải lời hứa công nghệ AOP nào cũng dùng cú pháp đó. Khi áp dụng công cụ cụ thể, cần kiểm chứng cách nó hiểu kiểu, phạm vi, wildcard và các phương thức kế thừa.

Điều kiện khớp chính xác hai tham số có thể biểu đạt:

```text
execution(* example.orders.OrderService.submit(java.lang.String, int))
```

Đổi thứ tự tham số hoặc đổi `int` thành `java.lang.Integer` sẽ tạo **chữ ký khác**. Sự giống nhau về ý nghĩa nghiệp vụ không làm chúng tương đương khi lọc theo kiểu khai báo.

### 4.2. Chọn đúng lớp hoặc vai trò

Một biểu thức tên/phương thức có thể khớp cùng tên ở các lớp khác nhau. Vì vậy ngoài danh sách tham số, thường cần xác định **lớp khai báo**, **kiểu đích**, **giao diện nghiệp vụ**, **package** hoặc **vai trò thành phần**.

Ví dụ, hai lớp `OrderService` và `ReportService` đều có `submit(String)`, nhưng chỉ `OrderService` cần kiểm toán đơn hàng. Biểu thức dùng wildcard cho mọi kiểu có phương thức `submit(String)` sẽ quá rộng.

Cần phân biệt giữa một tên kiểu **được viết trong biểu thức** và khả năng khớp của công nghệ đối với kiểu kế thừa, kiểu runtime hoặc proxy. Không nên suy từ văn bản biểu thức rằng mọi lời gọi trên đối tượng đúng tên đều được chặn.

### 4.3. Chọn theo hợp đồng thay vì tình cờ trùng tên

Nếu nhiều lớp cùng triển khai một giao diện `AuditedCommand` với `execute(Command)`, việc giới hạn theo **vai trò của giao diện và chữ ký** có thể rõ hơn chọn tên `execute` trên toàn hệ thống.

Tuy nhiên, một đối tượng có thể đồng thời triển khai nhiều giao diện hoặc sở hữu nhiều overload `execute`. Khi dùng kiểu/giao diện làm điều kiện, vẫn cần quyết định **điểm gọi hay điểm thực thi**, **chữ ký nào** và **đường nào được cơ chế AOP hỗ trợ**.

## 5. Sự lựa chọn overload của trình biên dịch không phụ thuộc hoàn toàn kiểu runtime

Điểm dễ nhầm là một biến mang đối tượng `String` **không đồng nghĩa** mọi lời gọi trên biến đó chọn overload `String`.

Xét:

```java
void accept(String value);
void accept(Object value);
```

Các lời gọi sau biểu đạt những lựa chọn khác nhau:

```java
accept("A");             // Chọn accept(String).
accept((Object) "A");    // Chọn accept(Object).
Object value = "A";
accept(value);           // Chọn accept(Object).
accept(null);            // Chọn accept(String): cụ thể hơn Object.
```

Đây là **mã minh họa** bên trong một ngữ cảnh đã khai báo các phương thức. Lựa chọn overload trong Java dựa trên **kiểu của biểu thức ở thời điểm biên dịch**, độ áp dụng của các ứng viên và quy tắc chọn phương thức cụ thể nhất. Sau khi chữ ký được chọn, dispatch động có thể chọn **hiện thực override** thích hợp ở thời gian chạy.

### 5.1. `null` có thể chọn overload hoặc làm lời gọi mơ hồ

Với `accept(String)` và `accept(Object)`, `accept(null)` hợp lệ và chọn `String` vì `String` cụ thể hơn `Object`.

Nhưng nếu có hai overload không có quan hệ cụ thể hơn:

```java
void check(String value);
void check(Integer value);

// check(null);  // Lỗi biên dịch: cả check(String) và check(Integer) đều phù hợp.
check((String) null);    // Chọn check(String).
check((Integer) null);   // Chọn check(Integer).
```

Các khai báo và lời gọi là **minh họa**, phải đặt trong ngữ cảnh Java phù hợp. Việc ép kiểu rõ ràng giải quyết mơ hồ tại **điểm gọi**. Pointcut chọn chữ ký `check(String)` không cần suy đoán giá trị `null` là kiểu nào: nó lựa chọn theo **phương thức khai báo**.

### 5.2. `int`, `Integer`, widening và boxing

Những chữ ký sau là khác nhau:

```java
void record(int code);
void record(Integer code);
void record(long code);
```

Java có những quy tắc áp dụng overload theo chuyển đổi kiểu, mở rộng kiểu nguyên thủy và boxing/unboxing. Không được đoán chỉ từ chữ `record(1)` rằng mọi overload liên quan số nguyên đều được gọi. Trong trường hợp có đúng ba khai báo trên, `record(1)` chọn `record(int)` vì khớp trực tiếp.

Còn `Integer boxed = 1; record(boxed)` chọn `record(Integer)` trong ngữ cảnh này. Nếu một pointcut chỉ định `int`, nó **không đồng nghĩa** chọn cả phiên bản `Integer`.

Phân tích **overload được chọn** phải dựa trên quy tắc ngôn ngữ và tập ứng viên cụ thể. Phân tích **join point được chọn** tiếp theo dựa trên cú pháp pointcut và loại điểm can thiệp.

### 5.3. Thứ tự tham số là một phần quan trọng của mẫu

`send(String destination, int priority)` và `send(int priority, String destination)` là hai overload khác nhau. Mẫu chỉ kiểm tra “có String và int” nhưng bỏ thứ tự sẽ không biểu đạt đầy đủ chữ ký.

Tên biến `destination` hoặc `priority` không tham gia quyết định overload; phải kiểm tra **kiểu ở đúng vị trí**.

## 6. Ví dụ Java 17 chạy được: chỉ ghi log cho `submit(String)`

Để quan sát hậu quả của một pointcut rộng/hẹp mà không phải cài thư viện ngoài, ví dụ này dùng **JDK dynamic proxy** làm bộ chặn lời gọi qua giao diện. Bộ lọc ở đây dùng reflection để chọn **đúng chữ ký phương thức**; nó minh họa tư duy pointcut, nhưng không phải bộ weaving chặn mọi điểm thực thi trong JVM.

Tạo tệp `OverloadPointcutDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

public class OverloadPointcutDemo {
    interface SubmissionPort {
        String submit(String code);
        String submit(Object payload);
        String submit(String code, int copies);
    }

    static final class SubmissionService implements SubmissionPort {
        @Override
        public String submit(String code) {
            return "text:" + code;
        }

        @Override
        public String submit(Object payload) {
            return "object:" + payload;
        }

        @Override
        public String submit(String code, int copies) {
            return "batch:" + code + ":" + copies;
        }
    }

    static boolean selected(Method method) {
        return method.getName().equals("submit")
                && Arrays.equals(
                        method.getParameterTypes(),
                        new Class<?>[] { String.class });
    }

    static SubmissionPort observe(SubmissionPort target) {
        return (SubmissionPort) Proxy.newProxyInstance(
                SubmissionPort.class.getClassLoader(),
                new Class<?>[] { SubmissionPort.class },
                (proxy, method, args) -> {
                    if (selected(method)) {
                        System.out.println("[AUDIT] submit(String)");
                    }

                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException ex) {
                        throw ex.getCause();
                    }
                });
    }

    public static void main(String[] args) {
        SubmissionPort port = observe(new SubmissionService());

        System.out.println(port.submit("A"));
        System.out.println(port.submit((Object) "A"));
        System.out.println(port.submit("B", 2));
        System.out.println(port.submit(null));
        System.out.println(new SubmissionService().submit("C"));
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac -encoding UTF-8 OverloadPointcutDemo.java
java OverloadPointcutDemo
```

Đầu ra chính xác:

```text
[AUDIT] submit(String)
text:A
object:A
batch:B:2
[AUDIT] submit(String)
text:null
text:C
```

**Phân tích các dòng:**

1. `port.submit("A")` chọn `submit(String)`: bộ lọc khớp, in `[AUDIT]`, sau đó chương trình trả `text:A`.
2. `port.submit((Object) "A")` chọn `submit(Object)` dù đối tượng thực là `String`: bộ lọc theo **tham số khai báo** không khớp, chỉ in `object:A`.
3. `port.submit("B", 2)` chọn overload hai tham số; `Arrays.equals` trả `false` vì danh sách tham số không đúng một `String`.
4. `port.submit(null)` chọn `submit(String)` vì `String` cụ thể hơn `Object`; bộ lọc khớp. Phép nối chuỗi tạo `text:null`.
5. `new SubmissionService().submit("C")` gọi **trực tiếp** đối tượng đích, không đi qua proxy nên không in `[AUDIT]`, dù chữ ký là `submit(String)`.

**Điểm đáng chú ý:** `method.getParameterTypes()` trả các **kiểu tham số được khai báo trong chữ ký**, không phải kiểu của giá trị trong mảng `args`. Đó là lý do chính sách quan sát một overload không nên quyết định bằng `args[0] instanceof String`. Với `submit(Object)` và đối tượng `"A"`, kiểm tra theo giá trị runtime sẽ sai mục tiêu.

Cách kiểm tra bằng `Arrays.equals` thích hợp cho ví dụ reflection này. Một ngôn ngữ pointcut thật có cú pháp và quy tắc khớp riêng; phải hiểu **đặc tả của nó**, không coi đoạn mã Java này là trình phân tích pointcut phổ quát.

### 6.1. Điều gì xảy ra nếu chỉ kiểm tra tên?

Nếu thay `selected` bằng:

```java
return method.getName().equals("submit");
```

thì cả ba overload sẽ được chọn khi đi qua proxy. Trong ví dụ này có thêm hai dòng `[AUDIT]` tương ứng với `submit(Object)` và `submit(String, int)`. Tuy nhiên, lời gọi trực tiếp `new SubmissionService().submit("C")` vẫn **không đi qua proxy**. Hai vấn đề độc lập là:

- **Pointcut quá rộng:** khớp nhầm overload.
- **Đường can thiệp không bao phủ:** lời gọi bỏ qua proxy.

Cần giải quyết từng vấn đề đúng nguyên nhân; việc làm pointcut hẹp không tự khắc phục đường bypass, và việc đưa mọi người gọi qua proxy không tự làm pointcut chính xác.

### 6.2. Bảo toàn hợp đồng khi advice chỉ quan sát

Ví dụ chỉ in một dòng trước khi chuyển lời gọi và chuyển tiếp kết quả. Khi reflection bọc lỗi đích trong `InvocationTargetException`, handler ném lại `getCause()` để giữ ngoại lệ gốc.

Trong hệ thống thật, advice theo dõi nên tránh:

- Gọi `method.invoke` nhiều lần khi chưa thiết kế retry an toàn.
- Thay kết quả trả về giữa các overload có hợp đồng khác nhau.
- Chuyển đổi ngoại lệ thành thành công hoặc nuốt lỗi.
- Ghi toàn bộ đối số nhạy cảm vào log.
- Áp nhầm chính sách thử lại lên overload có tác dụng phụ khác với overload định áp dụng.

## 7. Nhiều kiểu khớp: kiểu khai báo, giá trị runtime và bối cảnh

“Phương thức nhận String” có thể được hiểu theo vài cách không tương đương:

| Cách hiểu | Điều kiện muốn kiểm tra | Trường hợp `submit(Object)` nhận `"A"` |
| --- | --- | --- |
| **Chữ ký khai báo** | Tham số đầu khai báo `String` | Không khớp |
| **Đối số runtime** | Giá trị được truyền thực tế là một `String` | Có thể khớp theo điều kiện runtime |
| **Kiểu đối tượng đích** | Đối tượng nhận thuộc loại hoặc vai trò nhất định | Phụ thuộc đối tượng, không phụ thuộc riêng overload |
| **Vị trí từ vựng** | Điểm gọi/phương thức nằm trong phần mã đã chỉ định | Phụ thuộc nơi chứa join point |
| **Tên phương thức** | Tên là `submit` | Khớp nếu không thêm điều kiện tham số |

Nhiều mô hình AOP phân biệt điều kiện dựa trên **chữ ký tĩnh** với điều kiện theo **đối số runtime**. Trong một ngôn ngữ pointcut cụ thể, cú pháp lấy/so khớp đối số có thể khác cú pháp chọn chữ ký phương thức. Nếu mục tiêu của bài là “chọn overload `submit(String)`”, hãy dựa vào chữ ký; dùng kiểu giá trị runtime làm tiêu chí sẽ chọn nhầm `submit(Object)` trong những lần người gọi đưa vào chuỗi.

### 7.1. Kiểu tham số khai báo và kiểu trả về

Không phải mọi mẫu phương thức đều chỉ quan tâm danh sách tham số. Một pointcut có thể thêm điều kiện kiểu trả về, phạm vi truy cập, annotation, kiểu khai báo và nhiều thuộc tính khác tùy công cụ.

Cần nhớ hai câu khác nhau:

- **Java phân biệt các overload hợp lệ bằng danh sách tham số**, không thể chỉ đổi kiểu trả về để khai báo thêm overload.
- **Một biểu thức pointcut có thể dùng kiểu trả về như một điều kiện lọc bổ sung** nếu mô hình AOP hỗ trợ.

Không nên vì kiểu trả về không phân biệt overload mà kết luận nó vô nghĩa đối với mọi pointcut.

### 7.2. Tham số được khai báo ở giao diện hay lớp hiện thực

Trong ví dụ proxy, đối tượng `Method` được handler nhận thường mô tả phương thức giao diện được gọi. Khi làm việc với weaving tại điểm thực thi lớp đích, join point có thể gắn với phương thức khai báo hoặc hiện thực ở một lớp khác.

Nếu công cụ chọn theo `declaring type`, `target type`, kiểu cha hoặc kiểu của đối tượng runtime, tập điểm sẽ khác nhau khi có lớp con, giao diện, nhiều cấp kế thừa hoặc proxy. Cần thử các lớp hiện thực thực tế và ghi rõ **đang khớp theo kiểu nào**.

## 8. Method call và method execution: chọn overload ở điểm nào?

Giả sử:

```java
class Receiver {
    void process(String text) { }
    void process(Object value) { }
}
```

Một đoạn mã phía người gọi thực hiện:

```java
Receiver receiver = new Receiver();
Object input = "A";
receiver.process(input); // Chọn chữ ký process(Object).
```

Đây là **đoạn minh họa**.

Trong một mô hình AOP hỗ trợ cả hai:

- **Method call join point:** điểm lời gọi ở phía mã của người gọi. Có thể lọc theo nơi phát sinh lời gọi, chữ ký được chọn và các điều kiện khác.
- **Method execution join point:** điểm thực thi phương thức ở phía đích. Có thể lọc theo kiểu/thành viên được thực thi và phạm vi mã đích.

Trong ví dụ, lời gọi mang chữ ký `process(Object)`, bất kể `input` đang giữ đối tượng `String`. Nếu lớp con override `process(Object)`, dispatch động có thể chuyển tới hiện thực lớp con. Điều kiện pointcut tại phía call và execution vì thế **không nhất thiết tương đương**, nhất là khi xét cả nơi gọi, nơi khai báo, kế thừa và nhiều lớp hiện thực.

Một cơ chế dựa trên proxy có thể chỉ nhìn thấy cuộc gọi qua đối tượng đại diện. Một cơ chế weaving trực tiếp vào mã lớp có thể hỗ trợ loại join point khác. Không suy ra mọi kiểu advice đều có thể được cài theo cùng một cách.

### 8.1. Tránh nhầm lời gọi nội bộ

Khi một phương thức của chính đối tượng gọi `this.process("A")`, compiler vẫn chọn overload dựa trên kiểu biểu thức. Nhưng với proxy bao quanh bên ngoài đối tượng, đường gọi nội bộ này thường **không quay lại proxy**; nó có thể bỏ qua advice dù chữ ký `process(String)` hoàn toàn khớp.

Vấn đề ở đây là **phạm vi có hiệu lực**, không phải cách giải overload. Nếu cần can thiệp lời gọi nội bộ, phải chọn cơ chế có join point phù hợp hoặc đưa chính sách vào ranh giới rõ ràng mà mọi đường đều đi qua.

## 9. Varargs: một nguồn khớp quá rộng

Java cho phép tham số biến độ dài:

```java
void send(String... messages) { }
```

Ở mức khai báo JVM/reflection, varargs được biểu diễn qua **tham số mảng** (trường hợp này là `String[]`) kèm metadata varargs. Một lời gọi như `send("A", "B")` là cách gọi thuận tiện do compiler xử lý; nó không có nghĩa runtime có một overload hai tham số `String, String`.

Nếu có thêm `send(String)`, một lời gọi `send("A")` thông thường sẽ ưu tiên overload cố định phù hợp trong quá trình giải overload; còn `send("A", "B")` dùng varargs.

Đối với pointcut:

- **Chữ ký varargs** có dạng tham số mảng tại mức method descriptor.
- **Số đối số viết tại điểm gọi** có thể khác số tham số được khai báo của phương thức.
- Một wildcard cho danh sách tham số có thể chọn cả `send(String)` và `send(String...)` dù hai API có ý nghĩa khác nhau.

Nếu cần chính xác, phải tìm hiểu công cụ dùng ngữ nghĩa varargs ở cấp nguồn hay chữ ký bytecode, rồi kiểm thử cách biểu thức khớp phương thức tương ứng.

## 10. Generics, type erasure và bridge methods

Java thực hiện **xóa kiểu tổng quát (type erasure)** khi biên dịch phần lớn thông tin kiểu generic. Ví dụ:

```java
interface Handler<T> {
    T handle(T value);
}

class TextHandler implements Handler<String> {
    @Override
    public String handle(String value) {
        return value.trim();
    }
}
```

Đây là đoạn minh họa. Tại cấp bytecode, sự tương thích giữa phương thức generic của giao diện và phương thức chuyên biệt của lớp hiện thực có thể khiến compiler tạo **bridge method** tổng hợp để giữ ngữ nghĩa đa hình sau xóa kiểu.

Hệ quả với AOP:

1. Trong mã nguồn, bạn thấy `handle(String)`, nhưng reflection hoặc một bộ weaving ở bytecode có thể nhìn thấy **thêm phương thức tổng hợp/cầu nối** liên quan `handle(Object)`, tùy hiện thực và điểm đang quan sát.
2. Một pointcut chỉ lọc tên `handle` hoặc chọn quá rộng theo kiểu tham số có thể ghi nhận không đúng số lần nếu cơ chế chọn cả đường cầu nối và hiện thực.
3. Một số công cụ có xử lý đặc biệt cho bridge/synthetic methods; một số biểu lộ chúng như các điểm thực thi riêng. **Không có quy tắc chung rằng một lời gọi chắc chắn tạo hai lần advice.**

Khi dùng reflection để kiểm tra, `Method.isBridge()` và `Method.isSynthetic()` giúp nhận diện một số phương thức do compiler sinh. Nhưng quyết định bỏ chúng khỏi pointcut vẫn phải xét ngữ nghĩa cần theo dõi và khả năng của cơ chế đang dùng.

### 10.1. Không overload các kiểu generic chỉ khác tham số kiểu bị xóa giống nhau

Hai khai báo như:

```java
void inspect(java.util.List<String> values) { }
void inspect(java.util.List<Integer> values) { } // Không hợp lệ: cùng erasure.
```

không thể cùng xuất hiện như hai overload trong một lớp Java. Cả hai xóa kiểu thành danh sách tham số tương đương `List` ở cấp JVM. Một pointcut cũng không thể dựa vào một overload không tồn tại trong chương trình hợp lệ.

Nếu chương trình thực sự có các overload mang danh sách tham số bytecode khác nhau, phải chọn đúng chữ ký sau khi cân nhắc quá trình biên dịch và các phương thức do compiler tạo.

## 11. Kế thừa, ghi đè và nhiều kiểu điểm nhận

Một phương thức có thể được kế thừa không cần khai báo lại. Ví dụ lớp con kế thừa `process(String)` từ lớp cha, nhưng tự ghi đè `process(Object)`. Người đọc chỉ nhìn lớp con có thể bỏ sót overload được kế thừa.

Ngoài ra, nếu interface khai báo `submit(String)` và lớp hiện thực cung cấp cùng chữ ký, pointcut theo **phạm vi interface**, **kiểu lớp hiện thực** hay **vị trí từ vựng của mã** có thể khớp khác nhau tùy loại join point.

Khi đánh giá:

- Xác định tập phương thức mà Java coi là khả dụng trên **kiểu biên dịch của biến nhận**.
- Xác định chữ ký được chọn cho từng lời gọi.
- Xác định phương thức đích thực sự được dispatch.
- Xác định loại join point mà công nghệ hỗ trợ và vị trí đoạn mã được weaving.
- Xác nhận lời gọi đi qua cơ chế AOP cần thiết.

Một ứng dụng có nhiều lớp con, lớp trang trí hoặc proxy càng cần kiểm tra những đường này. **Tên phương thức + tên lớp hiển thị** không đủ chứng minh advice khớp đúng overload.

## 12. Overload và annotation

Một số hệ thống tổ chức chính sách theo annotation đặt trên phương thức. Đây là cách mô tả ý định áp một chính sách:

```java
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Observed { }

class TransferPort {
    @Observed
    void transfer(String account) { }

    void transfer(String account, int retries) { }
}
```

Đây là đoạn **minh họa**, không phải ví dụ đầy đủ về weaving. Annotation `@Observed` chỉ được đặt trên `transfer(String)`; overload hai tham số không tự mang annotation đó.

Nếu một pointcut chọn theo annotation phương thức, cần kiểm tra **annotation có được lấy từ phương thức khai báo trên interface, từ lớp hiện thực hay theo một quy tắc tìm kiếm khác**. Không thể mặc định annotation ở một overload, interface hoặc lớp cha tự áp sang overload khác.

Annotation có thể khiến phạm vi dễ đọc hơn, nhưng vẫn phải kiểm tra cơ chế thực thi và các đường gọi bỏ qua proxy hoặc chưa được weaving.

## 13. Dựng một pointcut an toàn theo từng bước

### Bước 1 — Viết yêu cầu ở mức nghiệp vụ

Ví dụ: “Ghi sự kiện kiểm toán khi thực thi `approve(String id)` của cổng phê duyệt, không ghi `approve(String id, boolean force)` và không ghi những phương thức cùng tên trong báo cáo.”

Đây là yêu cầu cụ thể hơn “ghi mọi lời gọi `approve`”.

### Bước 2 — Liệt kê các overload có liên quan

Bảng mẫu:

| Phương thức | Có áp dụng? | Lý do |
| --- | --- | --- |
| `ApprovalPort.approve(String)` | Có | Đúng thao tác cốt lõi cần kiểm toán |
| `ApprovalPort.approve(String, boolean)` | Không | Đường thao tác đặc biệt, chính sách khác |
| `ApprovalPort.approve(Object)` | Không | Cổng chung khác hợp đồng |
| `ReportPort.approve(String)` | Không | Trùng tên nhưng khác thành phần |
| `ApprovalPort.approve(Integer)` | Không | Khác kiểu khai báo |

Nếu yêu cầu thật sự là “mọi thao tác phê duyệt đều phải được kiểm toán”, danh sách **phải được mở rộng có chủ đích**, không thu hẹp tùy tiện để giảm log.

### Bước 3 — Chọn loại join point

Bạn cần quan sát **phương thức thực thi** hay **điểm gọi**? Nếu công nghệ chỉ chặn phương thức qua proxy, bạn có chấp nhận mọi người gọi phải dùng proxy không? Nếu muốn can thiệp ở call join point trong một package của người gọi, công cụ có hỗ trợ loại điểm đó không?

Đây là lựa chọn về cơ chế, không thể tự giải bằng wildcard.

### Bước 4 — Chọn bộ lọc hẹp

Kết hợp loại join point, phạm vi kiểu, tên, số và thứ tự kiểu tham số. Chỉ sử dụng wildcard ở những chiều được phép mở rộng. Nếu lọc theo annotation hoặc interface, vẫn cần xác minh overload và kiểu runtime.

### Bước 5 — Thêm loại trừ có lý do

Loại trừ mã của chính khía cạnh, các bộ giám sát nội bộ hoặc các thành phần phụ trợ không cần chính sách. Nhưng tránh dùng pointcut “mọi thứ” rồi loại trừ không ngừng; một điều kiện bao gồm nhỏ và rõ ràng thường dễ kiểm toán hơn.

### Bước 6 — Kiểm thử khớp và không khớp

Không chỉ thử trường hợp `approve("A")` có advice. Phải chứng minh `approve("A", true)`, `approve((Object) "A")`, phương thức trùng tên lớp khác và đường bypass **không được áp nhầm**, trừ khi yêu cầu thiết kế ghi khác.

### Bước 7 — Kiểm tra kết quả và tác dụng phụ

Một bộ lọc đúng vẫn có thể làm sai hành vi nếu advice chặn lời gọi, thay tham số hoặc thực thi tác vụ nhiều lần. Với chính sách chỉ quan sát, chuyển tiếp kết quả và lỗi gốc thường là lựa chọn phù hợp. Với chính sách bảo mật hoặc giao dịch, cần xác minh tất cả đường truy cập bắt buộc được bao phủ.

## 14. Những lỗi rất thường gặp

| Sai lầm | Vì sao sai | Cách sửa |
| --- | --- | --- |
| Chỉ lọc tên phương thức | Mọi overload cùng tên có thể bị chọn | Lọc đủ danh sách kiểu tham số và phạm vi kiểu |
| Dùng wildcard `..` vô điều kiện | Khớp bất kỳ số và kiểu tham số trong mô hình có cú pháp đó | Chỉ dùng khi chủ ý bao gồm mọi overload |
| Dựa vào `args[0] instanceof String` để chọn `submit(String)` | `submit(Object)` vẫn có thể nhận đối tượng String | Chọn theo chữ ký khai báo |
| Coi `null` là một overload riêng | `null` không mang kiểu phương thức độc lập | Phân tích quy tắc compiler và ép kiểu rõ khi cần |
| Xem `int` và `Integer` là một chữ ký | Đây là hai kiểu khác nhau | Xác minh primitive/boxed và thứ tự tham số |
| Xem varargs là mọi số tham số của method descriptor | Varargs được biểu diễn dạng mảng | Kiểm tra chữ ký thực và cách công cụ xử lý varargs |
| Bỏ qua generic erasure và bridge methods | Bytecode có thể có phương thức tổng hợp | Kiểm tra phương thức thực và ngữ nghĩa công cụ |
| Nhầm overload với override | Chọn chữ ký và chọn hiện thực là hai giai đoạn | Kiểm tra kiểu biểu thức người gọi, kiểu runtime và join point |
| Chỉ test overload được chọn | Không phát hiện trường hợp bị khớp ngoài ý muốn | Viết ca âm cho các overload còn lại |
| Tin rằng khớp pointcut là advice chắc chắn chạy | Proxy/bytecode weaving có phạm vi riêng | Kiểm tra lời gọi thực tế và artifact đã kết hợp |
| Dùng chính sách retry chung cho mọi overload | Có thể thử lại thao tác có tác dụng phụ không an toàn | Phân loại hợp đồng và idempotency riêng |

## 15. Kiểm thử pointcut: ma trận khẳng định dương và âm

Một bộ test hữu ích phải kiểm tra **chính sách áp đúng nơi** và **không áp ngoài nơi**.

| Ca | Kỳ vọng nếu chỉ chọn `submit(String)` |
| --- | --- |
| `submit("A")` | Có advice |
| `submit((Object) "A")` | Không có advice |
| `submit("A", 2)` | Không có advice |
| `submit(null)` khi chỉ tồn tại overload `String` và `Object` | Có advice vì chọn `String` |
| `submit((Object) null)` | Không có advice |
| `ReportService.submit(String)` ngoài phạm vi kiểu | Không có advice |
| Gọi thẳng đối tượng gốc khi cơ chế là proxy | Không có advice qua proxy, cần ghi nhận giới hạn |
| Lời gọi `this.submit("A")` từ bên trong đối tượng đích khi chỉ có proxy ngoài | Thường không đi qua proxy, cần kiểm tra |
| Lớp con override `submit(String)` | Kiểm tra ngữ nghĩa của join point và cơ chế triển khai |
| Điều kiện aspect động hoặc class được tải lại | Xác minh pointcut và đường can thiệp vẫn đúng |

Một số ca kiểm thử cũng cần kiểm chứng **giá trị trả về, ngoại lệ** và số lần thực thi tác vụ đích, bởi “có advice” không đủ chứng minh chính sách không gây tác dụng phụ.

### 15.1. Nếu xuất hiện log ở overload ngoài phạm vi

Chẩn đoán theo thứ tự:

1. Chụp chữ ký phương thức thực sự được gọi bằng cách kiểm tra tên, kiểu tham số và kiểu khai báo; không chỉ đọc loại dữ liệu runtime.
2. Kiểm tra wildcard trong pointcut: có phải đã chọn danh sách tham số tùy ý?
3. Kiểm tra điều kiện lọc theo `args`, kiểu đích hoặc annotation có mở rộng tập khớp hơn dự kiến?
4. Kiểm tra khác biệt giữa overload và override, kể cả phương thức kế thừa.
5. Kiểm tra có nhiều aspect hoặc hai lớp proxy cùng can thiệp không.
6. Kiểm tra bridge/synthetic methods và quá trình chuyển tiếp lời gọi nếu công nghệ liên quan.
7. Sửa lại quy tắc, sau đó kiểm thử **cả những phương thức không được chọn**.

### 15.2. Nếu thiếu log ở overload đúng chữ ký

Đừng lập tức thêm wildcard. Trước tiên hãy kiểm tra:

- Lời gọi có thực sự chọn chữ ký mong đợi ở thời điểm biên dịch?
- Loại join point được yêu cầu có được công nghệ hỗ trợ?
- Phương thức được gọi qua proxy hay trực tiếp đối tượng gốc?
- Lớp chứa điểm thực thi có nằm trong phạm vi weaving không?
- Phương thức có thuộc kiểu/gói/annotation mà bộ lọc thực sự dùng không?
- Có lời gọi nội bộ, đường phản chiếu hoặc lớp khác đang được sử dụng không?
- Advice trước đó có chặn chuỗi, đổi luồng thực thi hoặc ném lỗi không?

Việc nới rộng pointcut có thể che giấu nguyên nhân thiếu hiệu lực và tạo thêm các kết quả khớp ngoài ý muốn.

## 16. Tình huống nâng cao: khi thay đổi API làm pointcut đổi nghĩa

Giả sử lúc đầu có `save(String id)`. Một phiên bản sau bổ sung `save(String id, boolean overwrite)`. Nếu pointcut cũ là “mọi `save(..)`”, chính sách lập tức có thể áp thêm phương thức mới mà không ai chủ động sửa pointcut. Đây là **thay đổi phạm vi do phát triển API**, dù bản thân aspect không đổi.

Ngược lại, nếu pointcut chọn chính xác `save(String)`, phiên bản mới không tự được kiểm toán. Nếu tất cả hành vi ghi dữ liệu bắt buộc phải có kiểm toán, cần đánh giá lại yêu cầu, cập nhật chính sách có chủ đích và bổ sung kiểm thử.

Vì thế, khi thêm overload hãy hỏi:

- Overload mới có cùng hợp đồng về bảo mật, đo lường, kiểm toán, timeout và retry không?
- Nó có cùng ranh giới thành công/thất bại và khả năng thực thi lặp không?
- Pointcut hiện tại mở rộng tự động hay cần cập nhật? Sự mở rộng ấy có đúng chính sách không?
- Có ca kiểm thử nào bảo vệ danh sách những phương thức phải/không được can thiệp không?

Một **pointcut hẹp** giúp giảm khớp nhầm nhưng không tự bảo đảm đầy đủ chính sách khi hệ thống phát triển. Cần quản lý phạm vi AOP như một phần của hợp đồng API.

## 17. Bài tập vận dụng

### Bài 1 — Dự đoán overload Java

Cho:

```java
void print(String value);
void print(Object value);
```

Phân tích `print("x")`, `print((Object) "x")`, `print(null)` và `Object x = "x"; print(x)`. Phương thức nào được chọn?

**Đáp án:** Lần lượt `print(String)`, `print(Object)`, `print(String)`, `print(Object)`. Kiểu của biểu thức khi biên dịch quan trọng hơn kiểu runtime của đối tượng đang nằm trong biến `Object`.

### Bài 2 — Kiểm tra pointcut quá rộng

Một lớp có `charge(String)`, `charge(String, int)`, `charge(Object)`. Pointcut chọn mọi phương thức tên `charge` với danh sách tham số bất kỳ. Yêu cầu chỉ ghi log phiên bản một `String`. Điều gì sai và nên sửa thế nào?

**Đáp án:** Pointcut quá rộng vì nó chọn cả hai overload không mong muốn. Cần thêm điều kiện khớp đúng kiểu khai báo, số lượng và thứ tự tham số, giới hạn kiểu/lớp và kiểm thử âm cho hai overload còn lại.

### Bài 3 — Kiểu runtime đánh lừa

Một bộ lọc kiểm tra `args[0] instanceof String`. Người gọi thực thi `send((Object) "A")`. Nếu lớp có cả `send(String)` và `send(Object)`, bộ lọc có đảm bảo chỉ chọn `send(String)` không?

**Đáp án:** Không. Lời gọi được giải thành `send(Object)` nhưng đối số runtime là `String`, nên phép `instanceof` có thể đúng. Cần chọn theo chữ ký khai báo nếu mục tiêu là overload `String`.

### Bài 4 — `null` và mơ hồ

Một lớp có `verify(String)` và `verify(Integer)`. Vì sao `verify(null)` không biên dịch? Làm thế nào để chọn một overload xác định?

**Đáp án:** Hai kiểu không có quan hệ cụ thể hơn nhau nên lời gọi mơ hồ. Ép kiểu `(String) null` hoặc `(Integer) null` tại điểm gọi sẽ chọn một chữ ký cụ thể.

### Bài 5 — Kế thừa và bridge

Một lớp triển khai `Handler<String>` và override `handle(String)`. Bộ đo ở cấp bytecode phát hiện hai phương thức có tên `handle`, một phương thức cầu nối. Có nên khẳng định ngay hai lần advice tương ứng mỗi lần gọi không?

**Đáp án:** Không. Cần kiểm tra phương thức nào là bridge/synthetic, kiểu join point, cơ chế weaving và đường gọi thực tế. Một số cơ chế xử lý/loại trừ bridge khác nhau; không có kết luận phổ quát chỉ từ số phương thức reflection nhìn thấy.

### Bài 6 — Bảo toàn hợp đồng

Một advice được áp nhầm `transfer(Account, int)` và tự retry khi timeout, trong khi chính sách dự kiến chỉ áp `transfer(String requestId)`. Vì sao lỗi này nguy hiểm hơn log dư?

**Đáp án:** Phiên bản `Account, int` có thể thực hiện chuyển tiền; retry không bảo đảm an toàn nếu tác dụng phụ của lần trước đã xảy ra. Cần sửa phạm vi pointcut, xác định điều kiện idempotency và kiểm soát lỗi ở ranh giới nghiệp vụ phù hợp.

## 18. Ghi nhớ

- **Overload khác nhau bởi danh sách kiểu tham số; chỉ trùng tên không có nghĩa cùng hợp đồng.**
- Pointcut chọn overload an toàn cần xét **loại join point + phạm vi kiểu + tên + số lượng/thứ tự/kiểu tham số**; wildcard phải có mục đích rõ.
- **Kiểu tham số khai báo không phải kiểu đối số runtime.** Chữ ký `submit(Object)` có thể nhận một đối tượng `String`, nhưng vẫn là overload `Object`.
- Lựa chọn overload khi biên dịch khác với chọn hiện thực override khi chạy; method call và method execution cũng là các điểm can thiệp khác nhau.
- `null`, primitive/boxed, varargs, type erasure, bridge methods và kế thừa có thể làm việc chọn điểm phức tạp hơn dự kiến.
- **Khớp pointcut** và **advice thật sự chạy** là hai phép kiểm tra riêng; proxy, weaving và đường gọi giới hạn hiệu lực.
- Luôn có **ca dương và ca âm** để tránh khớp ngoài ý muốn; một chính sách chỉ quan sát phải bảo toàn kết quả, lỗi gốc và số lần thực thi nghiệp vụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-basics-011">Đánh đổi giữa biểu thức ngắn và độ chính xác của phạm vi</a>

<details>
<summary>Xem chi tiết</summary>

# Đánh đổi giữa biểu thức pointcut ngắn và độ chính xác của phạm vi

## 1. Vấn đề: viết ít ký tự có thật sự đơn giản hơn?

Trong **Aspect-Oriented Programming (AOP)**, một **pointcut** mô tả tập **join point** được chọn để áp dụng **advice**. Một join point là điểm trong mô hình thực thi mà công cụ AOP có thể can thiệp, chẳng hạn một lần thực thi phương thức hoặc một vị trí gọi phương thức, tùy cơ chế hỗ trợ.

Khi mới học pointcut, biểu thức thật ngắn thường có vẻ hấp dẫn:

```text
Chọn tất cả phương thức thuộc nhóm Service.
```

Chỉ một quy tắc có thể bao phủ hàng chục phương thức. Nhưng “tất cả Service” có thể gồm cả phương thức chẩn đoán, thao tác quản trị, getter, helper và những đường gọi không nằm trong chính sách. Khi thêm một phương thức mới, nó có thể bị aspect tác động mà không ai chủ động lựa chọn.

Ngược lại, một danh sách dài gồm các phương thức cụ thể có vẻ chính xác hơn, nhưng dễ **bỏ sót** phương thức mới, khó bảo trì và đòi hỏi cập nhật mỗi lần đổi tên hoặc chuyển kiểu.

Vì vậy, đánh đổi cần xem xét là:

- **Độ ngắn và khả năng diễn đạt:** biểu thức có dễ đọc, dễ giải thích ý định không?
- **Độ chính xác:** những điểm được chọn có đúng phạm vi cần thiết không?
- **Độ bao phủ:** điểm nào thuộc chính sách nhưng bị bỏ qua?
- **Tính ổn định:** thêm/chuyển/đổi tên phương thức có làm phạm vi thay đổi bất ngờ không?
- **Chi phí thực thi và kiểm thử:** bao nhiêu lần advice được chạy, phải xác minh những gì?
- **Tác động khi sai:** advice chỉ đo thời gian, hay có thể từ chối/đổi luồng nghiệp vụ?

**Biểu thức ngắn không đồng nghĩa phạm vi chính xác; biểu thức dài cũng không tự động đúng.** Mục tiêu là một pointcut có ý nghĩa rõ, đủ bao phủ và có thể chứng minh tập khớp phù hợp với chính sách.

Bài này bàn về **AOP nói chung**. Những biểu thức kiểu AspectJ được dùng làm ví dụ cho **cú pháp một công cụ cụ thể**; chúng không phải cú pháp mặc định của mọi ngôn ngữ hay cơ chế AOP.

## 2. Ba tập hợp phải phân biệt

Giả sử hệ thống có tập `U` gồm các join point **thực sự thuộc phạm vi mà công cụ có thể nhận diện/can thiệp**.

Ta xác định:

- **`D` (desired):** tập điểm **phải** có advice theo yêu cầu.
- **`S` (selected):** tập điểm mà pointcut **thực sự chọn**.
- **`U` (universe):** các điểm có thể xem xét trong mô hình và phạm vi weaving đang dùng.

Khi so sánh `S` với `D`:

| Kết quả | Ký hiệu | Ý nghĩa |
| --- | --- | --- |
| Đúng và được chọn | `TP = S ∩ D` | Điểm cần áp dụng và đã được chọn |
| Chọn thừa | `FP = S \ D` | Điểm không thuộc chính sách nhưng lại được chọn |
| Bỏ sót | `FN = D \ S` | Điểm phải được chọn nhưng pointcut không chọn |
| Đúng và không được chọn | `TN = U \ (S ∪ D)` | Điểm ngoài chính sách và không bị áp dụng |

Đây là **mô hình đánh giá phạm vi**, không phải thuật ngữ bắt buộc của một engine AOP.

Hình dung:

```text
                   TẬP PHẢI CAN THIỆP (D)
                 +--------------------------+
                 |  TP: chọn đúng           |
                 |                          |
     +-----------+--------------------------+------+
     |           | TẬP ĐƯỢC CHỌN (S)        |      |
     | FP:       |                          |      |
     | chọn thừa |       TP                 | FN:  |
     |           |                          | bỏ   |
     +-----------+--------------------------+ sót  |
                 |                          |      |
                 +--------------------------+------+
```

Sơ đồ chỉ là minh họa bằng nhãn, không biểu diễn chính xác hình học các tập. Hãy dùng công thức tập hợp ở bảng để suy luận.

Nếu `S = D`, phạm vi đúng theo yêu cầu đã mô hình hóa. Nếu `S` là tập con thực sự của `D`, có điểm bị bỏ sót. Nếu `S` chứa thêm điểm ngoài `D`, có chọn thừa. Một pointcut còn có thể mắc **cả hai lỗi cùng lúc**.

### 2.1. Precision và recall cho phạm vi pointcut

Có thể mượn hai thước đo để nói về chất lượng chọn điểm:

\[
\mathrm{precision} = \frac{TP}{TP + FP}
\]

\[
\mathrm{recall} = \frac{TP}{TP + FN}
\]

- **Precision cao:** phần lớn điểm được chọn đều thật sự cần advice.
- **Recall cao:** phần lớn điểm thật sự cần advice đã được chọn.

Nếu mẫu chọn **mọi điểm trong vũ trụ** thì recall có thể rất cao, nhưng precision thấp do quá nhiều điểm thừa. Nếu mẫu chỉ chọn **một phương thức thật chắc chắn**, precision có thể cao nhưng recall thấp vì còn các phương thức khác phải được xử lý.

Trong tập rỗng khiến mẫu số bằng 0, không nên báo bừa một tỷ lệ: cần xác định quy ước báo cáo “không áp dụng/không đo được” và kiểm tra trực tiếp số lượng điểm.

**Lưu ý về mô hình:** `D` phải được xác định từ chính sách độc lập với pointcut đang kiểm tra. Nếu dùng chính pointcut làm “đáp án đúng”, số liệu trở nên vòng tròn và không phát hiện lỗi.

## 3. Một ví dụ nhỏ: một chính sách kiểm toán có ba điểm hợp lệ

Giả sử hệ thống có tám phương thức cần xem xét trong một phạm vi weaving đã xác minh:

| STT | Kiểu và phương thức | Phải audit? | Lý do |
| ---: | --- | --- | --- |
| 1 | `CheckoutService.checkout()` | Có | Thao tác quan trọng |
| 2 | `CheckoutService.cancel()` | Có | Thao tác quan trọng |
| 3 | `CheckoutService.calculatePreview()` | Không | Chỉ tính xem trước |
| 4 | `CheckoutService.cleanup()` | Không | Công việc dọn dẹp nội bộ |
| 5 | `InventoryService.commit()` | Có | Thay đổi dữ liệu thực |
| 6 | `InventoryService.count()` | Không | Thao tác truy vấn |
| 7 | `InventoryService.cancel()` | Không | Không phải ranh giới kiểm toán theo chính sách giả định |
| 8 | `HealthProbe.check()` | Không | Chẩn đoán hệ thống |

Tập mong muốn `D` gồm ba phương thức ở dòng 1, 2 và 5. Yêu cầu này là **giả định của bài**, không phải quy tắc chung rằng mọi phương thức mang các tên đó đều phải kiểm toán.

### 3.1. Mẫu rộng và ngắn

Một mẫu “mọi phương thức trong kiểu kết thúc bằng Service” chọn bảy phương thức đầu.

```text
S_broad = tất cả phương thức của các kiểu *Service
TP = 3, FP = 4, FN = 0
precision = 3/7 ≈ 0,43
recall    = 3/3 = 1,00
```

Mẫu này **bao phủ đủ** nhưng **chọn thừa nhiều**. Nếu advice chỉ ghi một metric nhẹ, có thể chấp nhận mức độ rộng này trong một số trường hợp. Nếu advice kiểm tra quyền, từ chối thao tác hoặc thử lại, bốn điểm thừa có thể làm thay đổi hành vi ngoài mong muốn.

### 3.2. Mẫu hẹp theo danh sách rõ ràng

Một pointcut chỉ chọn `CheckoutService.checkout()` và `CheckoutService.cancel()`:

```text
S_narrow = { CheckoutService.checkout, CheckoutService.cancel }
TP = 2, FP = 0, FN = 1
precision = 2/2 = 1,00
recall    = 2/3 ≈ 0,67
```

Tập đã chọn **rất chính xác** nhưng bỏ sót `InventoryService.commit()`. Với audit bắt buộc, thiếu sót này có thể nghiêm trọng hơn việc ghi vài dòng log thừa.

### 3.3. Mẫu biểu đạt đúng ranh giới chính sách

Giả sử các phương thức phải audit được đánh dấu bằng metadata do chính sách quản lý, và công cụ AOP hỗ trợ chọn điểm bằng metadata ấy. Khi đó:

```text
S_policy = các method execution có dấu @Audited
TP = 3, FP = 0, FN = 0
precision = 1,00
recall    = 1,00
```

Mẫu thứ ba ngắn ở phía biểu thức chọn điểm, nhưng công sức đã được chuyển sang **quản trị metadata**: ai gắn dấu, ai xem xét thay đổi và có bảo đảm dấu xuất hiện trên mọi thao tác cần audit không? Một dấu gắn nhầm hoặc quên gắn vẫn tạo ra FP/FN. Không có cách chọn nào tự miễn phí về vận hành.

## 4. Sự đánh đổi thật sự: độ ngắn, độ bao phủ và tính ổn định

Có thể nhìn các chiến lược phổ biến:

| Chiến lược | Điểm mạnh | Rủi ro | Khi nào cân nhắc |
| --- | --- | --- | --- |
| Mẫu rộng theo package/tên kiểu | Gọn, bao phủ tự động khi có thành phần mới | Chọn thừa, bị ảnh hưởng bởi thay đổi tên/package | Telemetry chung ít tác động |
| Danh sách chữ ký tường minh | Dễ thấy ngay từng điểm, phạm vi hẹp | Bỏ sót điểm mới, dài và tốn bảo trì | Chính sách nhạy cảm, số điểm ít và ổn định |
| Mẫu theo quy ước đặt tên | Biểu thức ngắn, dễ áp dụng theo convention | Convention không chứng minh ngữ nghĩa | Quy ước được kiểm tra tự động |
| Mẫu kết hợp điều kiện và loại trừ | Cân bằng độ phủ và phạm vi | Biểu thức logic phức tạp, ưu tiên toán tử khó đọc | Có biên giới rõ, trường hợp loại trừ cụ thể |
| Metadata/annotation có ý nghĩa | Mục đích chính sách rõ, ít phụ thuộc tên | Phải quản lý độ chính xác của metadata | Hệ thống có quy trình quản trị |
| Named pointcut kết hợp | Tái sử dụng, dễ đặt tên ý nghĩa | Vẫn có thể che giấu độ rộng thực tế | Chính sách lớn hoặc nhiều advice |

Một biểu thức hai dòng có tên rõ và phạm vi kiểm thử được có thể **dễ bảo trì hơn** một biểu thức một dòng quá tổng quát. Ngược lại, liệt kê hàng trăm chữ ký không phải lúc nào cũng tốt hơn một quy tắc ngắn nhưng ổn định theo miền nghiệp vụ.

### 4.1. Số ký tự không phải thước đo độ phức tạp

Hai biểu thức cùng ngắn có thể khác rất nhiều:

```text
Chọn mọi phương thức.
Chọn mọi điểm thao tác có dấu audit đã được quản trị.
```

Câu đầu tiên ngắn vì **bỏ qua điều kiện biên**, câu thứ hai ngắn vì **ủy quyền tiêu chí rõ ràng** cho metadata và quy trình duy trì nó.

Do đó khi đánh giá phải tính cả chi phí để hiểu những điều kiện nằm **ngoài** chuỗi pointcut: metadata, quy ước tên, sơ đồ package, cấu hình weaving, thứ tự advice và thỏa thuận nhóm phát triển.

## 5. Phân biệt “pointcut đúng” với “advice thật sự chạy”

Tập `S` không chỉ là các tên khớp một biểu thức trên giấy. Một chương trình AOP thực sự chịu giới hạn bởi:

1. **Mô hình join point:** công cụ hỗ trợ method execution, method call, field access hay loại nào?
2. **Phạm vi weaving/interception:** những lớp và đường gọi nào được biến đổi hoặc đi qua đối tượng trung gian?
3. **Ngữ nghĩa pointcut:** mẫu tĩnh, điều kiện động và thứ tự đánh giá.
4. **Luồng advice:** lớp advice bao ngoài có thể không tiếp tục đến thao tác gốc.
5. **Dữ liệu thực tế:** một điều kiện động có thể chỉ khớp một số invocation.

Một pointcut có thể mô tả đúng chữ ký nhưng không có advice khi chạy nếu đường gọi đi trực tiếp qua target bên ngoài proxy. Một công cụ weaving khác có thể can thiệp tại phần thân phương thức. Vì vậy **độ chính xác của phạm vi** phải được xem xét cả ở mức **tập join point lý thuyết** lẫn **lần xuất hiện động thực tế**.

### 5.1. Method call và method execution không thể hoán đổi tùy tiện

Một phương thức `pay()` có thể được gọi từ nhiều nơi:

```text
CheckoutService.checkout() ---+
                              +---> PaymentGateway.pay()
RetryService.retry() ---------+
```

Một mẫu **method execution** hướng tới phần thực thi `pay()`; một mẫu **method call** xét các vị trí gọi tương ứng. Nếu chính sách nhằm đo mọi lần chạy phần thân thì chọn nhầm call pointcut có thể tạo tập điểm khác mong muốn. Nếu chính sách cần kiểm soát **chỉ lời gọi từ CheckoutService**, execution pointcut đơn độc có thể quá rộng.

Điều này độc lập với độ dài biểu thức: chỉ đổi một loại join point có thể thay đổi ý nghĩa rất lớn.

## 6. Ví dụ cú pháp công cụ: độ rộng theo kiểu và phương thức

Phần dưới minh họa với **cú pháp AspectJ**. Đây là ngôn ngữ pointcut cụ thể cho Java, không phải cú pháp chung bắt buộc của AOP.

Một biểu thức ngắn:

```aspectj
execution(* com.example..*Service.*(..))
```

Có thể hiểu là chọn method execution theo mẫu kiểu `*Service`, phương thức bất kỳ, kiểu trả về bất kỳ và danh sách tham số bất kỳ trong phạm vi package phù hợp. Biểu thức ngắn vì có nhiều wildcard.

Một biểu thức cụ thể hơn:

```aspectj
execution(* com.example.checkout.CheckoutService.checkout(..))
|| execution(* com.example.checkout.CheckoutService.cancel(..))
|| execution(* com.example.inventory.InventoryService.commit(..))
```

Mẫu này biểu đạt ba nhóm thực thi theo tên và kiểu rõ ràng. Nhưng cần kiểm tra **chữ ký overload**: `(..)` vẫn cho phép mọi danh sách tham số, nên nếu có nhiều overload cùng tên thì phạm vi có thể lớn hơn ba **chữ ký chính xác** dự kiến.

Có thể thêm ràng buộc tham số khi cần:

```aspectj
execution(* com.example.checkout.CheckoutService.checkout(java.lang.String))
```

Một hướng khác dùng dấu đánh dấu nghiệp vụ được quản trị:

```aspectj
execution(* com.example..*.*(..))
&& @annotation(com.example.policy.Audited)
```

Đây là ví dụ cú pháp của một công cụ hỗ trợ lựa chọn dựa trên annotation của phương thức tương ứng. Tên `com.example.policy.Audited` chỉ là giả định, cần có annotation và weaving phù hợp khi triển khai thật.

**Không nên sao chép nguyên văn các biểu thức trên vào bất kỳ công cụ AOP nào khác** rồi cho rằng wildcard, annotation matching hoặc loại join point có cùng ngữ nghĩa.

### 6.1. Wildcard rút ngắn ở vị trí nào?

| Cấu phần trong mẫu AspectJ | Ý nghĩa phổ biến | Rủi ro |
| --- | --- | --- |
| `*` ở kiểu trả về | Không ràng buộc kiểu trả về | Chọn cả `void`/giá trị khi chỉ muốn một kiểu |
| `*Service` ở kiểu | Chọn theo hậu tố tên kiểu | Tên kiểu không luôn phản ánh chính sách |
| `*` ở tên phương thức | Chọn mọi tên phương thức | Bao gồm cả helper/admin/getter |
| `(..)` ở danh sách tham số | Không ràng buộc số và kiểu tham số | Chọn các overload không mong muốn |
| `..` trong mẫu package | Mở phạm vi qua các cấp package phù hợp | Bao phủ namespace ngoài ý định |
| `+` trong type pattern | Chọn kiểu và các subtype phù hợp | Phạm vi thay đổi khi thêm lớp con |

Những ký hiệu có ngữ nghĩa theo **vị trí trong ngữ pháp**, khác Java regex hay file glob. Ví dụ `(*)` theo mẫu chữ ký AspectJ yêu cầu một tham số kiểu bất kỳ; `(..)` chấp nhận số tham số bất kỳ, bao gồm 0. Nếu nhầm hai ký hiệu này, pointcut có thể bỏ sót ngay cả khi tên lớp và tên phương thức đúng.

### 6.2. Biểu thức ngắn nhưng dễ gây lỗi khi refactor

Các quy ước `*Service` và `com.example..` liên kết pointcut với **tên và cấu trúc package**. Khi đổi tên lớp, di chuyển thành phần hoặc sinh thêm lớp helper trong package, tập điểm có thể thay đổi mà không xuất hiện lỗi biên dịch.

Đặc biệt nguy hiểm là sự thay đổi **vẫn hợp cú pháp**. Công cụ không thể tự biết rằng `CleanupService` mới **không thuộc ranh giới audit** nếu biểu thức chỉ yêu cầu hậu tố `Service`.

## 7. Logic AND/OR/NOT: tăng độ chính xác nhưng có chi phí đọc hiểu

Một cách hạn chế FP là kết hợp điều kiện:

```aspectj
execution(* com.example..*Service.*(..))
&& !within(com.example.internal..*)
```

Về ý tưởng, biểu thức vẫn chọn tập rộng theo `*Service` nhưng loại những join point được định nghĩa trong vùng `internal` được chỉ định. Điều này hữu ích khi **ranh giới loại trừ ổn định và có ý nghĩa**.

Nếu muốn ghép hai phạm vi rồi áp dụng cùng một điều kiện loại trừ:

```aspectj
(
    execution(* com.example.checkout..*Service.*(..))
    || execution(* com.example.inventory..*Service.*(..))
)
&& !within(com.example.internal..*)
```

Dấu ngoặc có tác dụng thực tế: nếu điều kiện loại trừ bị ghép vào **chỉ một nhánh OR** thì các điểm ngoài ý muốn ở nhánh còn lại vẫn có thể được chọn.

### 7.1. Khi nào nên tách named pointcut?

Khi một biểu thức đã có nhiều phần `&&`, `||`, `!` và lọc theo loại/kiểu, việc đặt tên các tập con giúp giảm tải đọc hiểu:

```text
businessOperations = các thao tác nghiệp vụ theo ranh giới đã quy định
excludedOperations = các điểm nội bộ/không thuộc chính sách
auditedOperations = businessOperations AND NOT excludedOperations
```

Đây là **mã giả ngôn ngữ trung lập**, không phải cú pháp khai báo pointcut thực tế. Mục tiêu là đặt tên theo **nghĩa chính sách**, sau đó triển khai bằng cú pháp tương ứng của công cụ.

Tách tên giúp biểu thức dễ đọc hơn, nhưng không làm một quy tắc sai trở nên đúng. Cần kiểm tra tập khớp sau khi ghép named pointcut.

### 7.2. Dấu NOT có thể che giấu thiếu sót trong chính sách

Giả sử loại mọi phương thức `get*` khỏi logging để giảm lưu lượng. Sau đó xuất hiện `getAndDelete()` thực hiện thay đổi dữ liệu. Nếu chỉ dựa vào tên, chính sách có thể **bỏ sót** thao tác nhạy cảm.

Do đó điều kiện loại trừ nên dựa vào ranh giới ngữ nghĩa đủ chắc, không chỉ dựa trên phỏng đoán rằng một tiền tố tên luôn mang nghĩa “đọc”.

## 8. Annotation và metadata: biểu thức ngắn nhưng chuyển gánh nặng sang thiết kế

Một cách làm cho pointcut vừa ngắn vừa chính xác là gắn **dấu có ý nghĩa chính sách** lên các điểm cần can thiệp:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Audited {}
```

Đây là **đoạn Java minh họa**, phải thêm các import của `java.lang.annotation` để dùng như mã độc lập. Công cụ AOP cũng phải hỗ trợ điều kiện chọn điểm tương ứng.

Về thiết kế:

- **Ưu điểm:** ranh giới được thể hiện gần nơi khai báo thao tác; đổi tên phương thức không nhất thiết làm mất dấu.
- **Nhược điểm:** người viết phương thức có thể quên gắn dấu; chính sách dùng metadata phải có quy trình review.
- **Thách thức inheritance/interface:** dấu trên interface, lớp cha hay phương thức override có thể được xử lý khác nhau theo cách công cụ đọc metadata. Không nên đoán rằng dấu luôn được kế thừa tới mọi method execution.
- **Thách thức thêm mới:** nếu một thao tác mới cần audit mà chưa được đánh dấu, pointcut có thể không phát hiện và tạo **false negative**.

Có thể coi việc dùng metadata là chuyển từ **quy tắc nhận diện suy đoán theo tên** sang **khai báo chủ đích**, nhưng vẫn phải kiểm soát tính đầy đủ và độ tin cậy của khai báo.

### 8.1. Đặt metadata ở ranh giới nào?

Nếu chính sách liên quan đến một **thao tác nghiệp vụ được công khai**, dấu nên phản ánh ranh giới thực mà người gọi sử dụng. Nếu đặt dấu trên helper nội bộ, aspect có thể chạy nhiều lần cho một thao tác, tạo audit dư thừa.

Mức độ đúng của metadata phải được xem xét cùng với mô hình join point: chọn **method execution** trên implementation có thể khác việc chọn **method call** ở các vị trí bên gọi.

## 9. Cân nhắc hậu quả theo loại advice

Cùng một sai lệch FP/FN có thể ít hoặc rất nghiêm trọng tùy advice.

| Loại chính sách | Khi chọn thừa (FP) | Khi bỏ sót (FN) |
| --- | --- | --- |
| Ghi metric hiệu năng | Overhead, tăng cardinality/noise | Thiếu số liệu cho thao tác cần đo |
| Logging kỹ thuật | Log rác, có nguy cơ lộ dữ liệu | Không đủ dấu vết khi debug |
| Kiểm toán bắt buộc | Audit sự kiện không thuộc chính sách, tăng chi phí | Thiếu bản ghi bắt buộc |
| Kiểm tra quyền | Chặn nhầm chức năng hoặc tăng độ trễ | Đường truy cập cần bảo vệ có thể bị bỏ qua |
| Retry | Thực thi lại thao tác không an toàn, tác dụng phụ trùng | Bỏ qua thao tác cần chống lỗi tạm thời |
| Chuyển đổi ngoại lệ | Caller nhận lỗi bị đổi ngoài mong muốn | Mã lỗi không được chuẩn hóa như yêu cầu |

**Không có một “mức precision/recall tốt nhất” chung cho mọi aspect.** Một chính sách kiểm soát quyền có thể không chấp nhận bất kỳ FN nào ở các đường vào cần bảo vệ; một chính sách đo thời gian có thể chấp nhận vài FP nếu đủ nhẹ và có ngân sách tài nguyên.

Với những thao tác quan trọng, đừng giải quyết rủi ro bằng một wildcard rộng một cách máy móc. Cần bảo đảm **mọi đường thực thi bắt buộc** đều đi qua cơ chế can thiệp và không còn đường gọi ngoài phạm vi.

## 10. Tập điểm tĩnh và số lần xuất hiện động không giống nhau

Một mẫu khớp mười phương thức chưa chắc tốn chi phí hơn mẫu khớp hai phương thức.

Giả sử:

- Pointcut A chọn mười phương thức hiếm khi chạy.
- Pointcut B chọn đúng hai phương thức được gọi hàng triệu lần mỗi phút.

Nếu cùng một advice tốn chi phí (c) trên mỗi lần chạy, chi phí gần đúng phụ thuộc vào **tần suất các invocation được advice thực sự xử lý**, không chỉ số lượng tên phương thức:

\[
\mathrm{overhead} \approx \sum_{j \in S} \mathrm{invocations}(j) \times \mathrm{costOfAdvice}(j)
\]

Công thức là mô hình đơn giản: chưa bao gồm chi phí đánh giá điều kiện động, khởi tạo/cấu hình, contention, ảnh hưởng cache, JIT hoặc I/O bất đồng bộ.

Vì vậy nên đo hai khía cạnh:

1. **Coverage theo join-point shadow:** bao nhiêu vị trí/chữ ký cần được chọn; có FP/FN ở cấu trúc tĩnh không?
2. **Coverage theo invocation thực tế:** những đường chạy được quan sát có qua advice đúng số lần, đúng ngữ cảnh hay không?

Một pointcut có vẻ chính xác ở mức tên vẫn có thể tạo overhead lớn nếu đi qua thao tác rất nóng, hoặc tạo thiếu sót nếu invocation đi theo đường mà proxy không can thiệp được.

### 10.1. Điều kiện động làm tăng độ chính xác có thể tốn chi phí

Một pointcut có thể kết hợp điều kiện cấu trúc và điều kiện theo kiểu đối tượng hay giá trị tham số ở runtime. Điều này có thể giảm FP theo **từng invocation** nhưng đòi hỏi đánh giá điều kiện khi chạy.

Không nên đặt một phép gọi mạng hoặc truy vấn dữ liệu nặng vào logic chọn điểm chỉ để thu được một biểu thức nhìn “thật ngắn”. Độ chính xác phải được cân nhắc với khả năng đo lường và chi phí thực thi.

## 11. Ví dụ Java 17 chạy được: tính TP, FP, FN cho ba quy tắc chọn

Để thấy rõ đánh đổi, ta sẽ dùng **reflection của JDK** để mô phỏng quá trình phân loại các phương thức. Ví dụ này **không phải engine AOP** và không thực hiện weaving: nó chỉ tính xem ba quy tắc chọn theo metadata có khớp tập mong muốn hay không.

Tạo tệp `PointcutScopeDemo.java`:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class PointcutScopeDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Audited {}

    static class CheckoutService {
        @Audited public void checkout() {}
        @Audited public void cancel() {}
        public void calculatePreview() {}
        public void cleanup() {}
    }

    static class InventoryService {
        @Audited public void commit() {}
        public void count() {}
        public void cancel() {}
    }

    static class HealthProbe {
        public void check() {}
    }

    record Rule(String name, Predicate<Method> match) {}

    static void report(Rule rule, List<Method> methods) {
        int truePositive = 0;
        int falsePositive = 0;
        int falseNegative = 0;

        for (Method method : methods) {
            boolean desired = method.isAnnotationPresent(Audited.class);
            boolean selected = rule.match().test(method);

            if (desired && selected) truePositive++;
            if (!desired && selected) falsePositive++;
            if (desired && !selected) falseNegative++;
        }

        int selected = truePositive + falsePositive;
        int required = truePositive + falseNegative;
        double precision = selected == 0 ? 0 : (double) truePositive / selected;
        double recall = required == 0 ? 0 : (double) truePositive / required;

        System.out.printf(
                Locale.ROOT,
                "%s: selected=%d, TP=%d, FP=%d, FN=%d, precision=%.2f, recall=%.2f%n",
                rule.name(), selected, truePositive, falsePositive,
                falseNegative, precision, recall
        );
    }

    public static void main(String[] args) {
        List<Method> methods = Stream.of(
                CheckoutService.class,
                InventoryService.class,
                HealthProbe.class
        ).flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
         .sorted(Comparator.comparing(
                 m -> m.getDeclaringClass().getSimpleName() + "." + m.getName()
         ))
         .toList();

        Rule broad = new Rule(
                "broad",
                m -> m.getDeclaringClass().getSimpleName().endsWith("Service")
        );

        Rule narrow = new Rule(
                "narrow",
                m -> m.getDeclaringClass() == CheckoutService.class
                        && Set.of("checkout", "cancel").contains(m.getName())
        );

        Rule explicit = new Rule(
                "explicit",
                m -> m.isAnnotationPresent(Audited.class)
        );

        report(broad, methods);
        report(narrow, methods);
        report(explicit, methods);
    }
}
```

Biên dịch và chạy với JDK 17 hoặc mới hơn:

```bash
javac PointcutScopeDemo.java
java PointcutScopeDemo
```

Đầu ra:

```text
broad: selected=7, TP=3, FP=4, FN=0, precision=0.43, recall=1.00
narrow: selected=2, TP=2, FP=0, FN=1, precision=1.00, recall=0.67
explicit: selected=3, TP=3, FP=0, FN=0, precision=1.00, recall=1.00
```

### 11.1. Vì sao có kết quả đó?

- Danh sách phương thức gồm bốn phương thức của `CheckoutService`, ba của `InventoryService` và một của `HealthProbe`.
- Chính sách minh họa coi ba phương thức có `@Audited` là tập mong muốn.
- `broad` chọn toàn bộ bảy phương thức trong hai lớp có hậu tố `Service`, nên chọn đúng ba và thừa bốn.
- `narrow` chỉ chọn `checkout` và `cancel` của `CheckoutService`; bỏ sót `InventoryService.commit`.
- `explicit` đọc dấu `@Audited` đã gắn trên ba phương thức, nên khớp đúng theo tiêu chuẩn của ví dụ.

**Giới hạn đặc biệt của ví dụ:** `explicit` đạt kết quả hoàn hảo vì định nghĩa desired cũng lấy từ cùng một annotation. Trong hệ thống thật, **không được dùng cách này để chứng minh annotation đã được gắn đủ**. Đáp án đúng cần đến từ yêu cầu nghiệp vụ hoặc bản kiểm kê được kiểm chứng độc lập. Ví dụ chỉ chứng minh quan hệ giữa các tập lựa chọn đã định nghĩa, không chứng minh chính sách audit trên hệ thống thực đã được đáp ứng.

### 11.2. Chương trình Java thuần có gì khác pointcut AOP?

- `Predicate<Method>` ở đây chỉ là một hàm kiểm tra `Method`, không định nghĩa advice hay weave join point.
- `getDeclaredMethods()` chỉ trả những phương thức được khai báo trong các lớp được duyệt; không phải toàn bộ call sites hay mọi điểm runtime có thể can thiệp.
- Mẫu hậu tố `Service` được thực hiện bằng `endsWith` của Java, **không phải parser wildcard của AspectJ**.
- `@Audited` là metadata được đọc bằng reflection; việc một công cụ AOP thật nhận diện dấu trên các loại join point ra sao cần xác minh theo công cụ đó.

Điều nên mang sang AOP thực tế là **cách lập bảng yêu cầu và đo chọn thừa/bỏ sót**, chứ không sao chép bộ mô phỏng này thành một cơ chế weaving.

## 12. Độ chính xác còn thay đổi khi mã tiến hóa

Một pointcut có thể đúng hoàn toàn hôm nay nhưng lệch sau một lần refactor.

### 12.1. Thêm phương thức mới

Ban đầu `*Service.*(..)` bao phủ đúng các thao tác cần đo. Sau đó một kỹ sư thêm `DebugService.dumpRawState()`. Mẫu có thể tự chọn luôn phương thức mới, dù người viết chính sách chưa xem xét. Đây là **FP do mở rộng phạm vi tự động**.

Với danh sách tường minh, phương thức mới `InventoryService.adjustStock()` cần audit có thể bị bỏ qua. Đây là **FN do thiếu cập nhật**.

### 12.2. Đổi tên hoặc di chuyển package

Một phương thức chuyển từ `com.example.business` sang `com.example.internal` có thể không còn khớp mẫu cũ, dù chức năng nghiệp vụ không đổi. Ngược lại, đổi tên một helper thành `*Service` có thể khiến nó bị áp dụng advice. Vì vậy việc refactor nên được xem như một thay đổi **có thể ảnh hưởng tới tập chọn**.

### 12.3. Overload, override và phân cấp kiểu

Một mẫu cho `save(..)` có thể tự chọn overload mới `save(List<Item>)` dù chỉ overload `save(Item)` thuộc chính sách. Mẫu theo kiểu `BaseService+` có thể bao phủ subtype được thêm mới.

Với override, interface và phương thức sinh từ compiler, cách nhận diện declaring type và annotation có thể khác nhau theo mô hình AOP. Không nên suy từ chữ ký nhìn trong source rằng mọi cơ chế interception đều chọn cùng một điểm.

### 12.4. Từ truy vết tĩnh tới kiểm thử hồi quy

Muốn giảm rủi ro, nên có một báo cáo hoặc phép kiểm tra tái tạo được danh sách các join point khớp với pointcut quan trọng sau mỗi thay đổi. Khi số điểm thay đổi, nhóm phát triển phải thấy được điểm nào được thêm/bỏ và vì sao.

Cần phân biệt việc so sánh **danh sách tĩnh** với việc xác minh **đường chạy thực tế**. Cả hai hữu ích vì một công cụ chỉ chặn lời gọi qua proxy có thể bỏ qua những đường gọi dù mẫu chữ ký khớp.

## 13. Khi nào chọn ngắn, khi nào chọn rõ từng điểm?

Không có công thức áp dụng chung. Có thể dựa vào **mức độ tác động** của advice:

### 13.1. Chính sách quan sát kỹ thuật, chi phí thấp

Ví dụ đo thời gian hoặc đếm số lần gọi. Một mẫu tương đối rộng có thể hữu ích nếu:

- Những điểm bổ sung không làm đổi kết quả hoặc ngoại lệ.
- Overhead đã được đo và chấp nhận.
- Dữ liệu nhạy cảm không bị log tự động.
- Có khả năng loại trừ những hot path hoặc vùng mã đặc biệt.
- Thêm phương thức mới được theo dõi trong báo cáo coverage.

Điều này **không có nghĩa** logging luôn an toàn: ghi tham số nhạy cảm, gọi `toString()` trên đối tượng lớn hoặc ghi log I/O nặng vẫn có thể tạo rủi ro.

### 13.2. Chính sách có tác động hành vi hoặc kiểm soát

Với advice có thể từ chối thao tác, đổi kết quả/ngoại lệ, khởi tạo giao dịch hoặc thử lại, cần ưu tiên ranh giới có chủ đích và khả năng kiểm chứng:

- Tập các thao tác được phê duyệt rõ ràng.
- Định nghĩa những đường vào bắt buộc.
- Kiểm thử FP và FN nghiêm ngặt.
- Xác minh thứ tự khi nhiều aspect cùng khớp.
- Đánh giá side effect nếu advice gọi lại phần nghiệp vụ.
- Kiểm chứng hoạt động đúng trên cơ chế weaving thực tế, nhất là lời gọi nội bộ và các đường vào khác.

Một pointcut “đủ ngắn để nhớ” nhưng không thể giải thích bằng chính sách là điểm bắt đầu của rủi ro.

## 14. Tách độ phức tạp biểu thức khỏi độ phức tạp vận hành

Giả sử hai thiết kế:

**Thiết kế A:** một biểu thức wildcard duy nhất, không có quy trình xác minh tự động. Người mới đọc chỉ biết nó chọn các lớp có hậu tố `Service`.

**Thiết kế B:** named pointcut `auditedOperations`, tập được chọn xác định bằng dấu đánh dấu có tài liệu, có kiểm thử bảo đảm các thao tác thuộc danh sách yêu cầu được gắn dấu và được weaving.

B có thể viết dài hơn về tổng hệ thống, nhưng **dễ kiểm chứng**. Ngược lại, với một chương trình chỉ có hai phương thức cố định, danh sách tường minh có thể thực tế hơn cả metadata và kiểm thử phức tạp.

Một “biểu thức tối ưu” là biểu thức giảm **chi phí hiểu và duy trì toàn bộ chính sách**, không đơn thuần giảm độ dài chuỗi pointcut.

### 14.1. Bốn câu hỏi khi review

1. **Ý định:** ta muốn chọn nhóm thao tác nào, theo quy tắc nghiệp vụ hay kỹ thuật nào?
2. **Phạm vi:** biểu thức chọn những điểm nào hôm nay, và sẽ tự chọn những điểm nào khi thêm mã?
3. **Bỏ sót:** điểm nào phải chạy advice nhưng không nằm trong tập chọn?
4. **Chứng cứ:** bảng ca kiểm thử và kết quả weaving/runtime nào chứng minh hai câu trả lời trên?

Nếu không trả lời được, nên thay đổi ranh giới hoặc bổ sung chứng cứ trước khi rút ngắn biểu thức.

## 15. Quy trình thiết kế một pointcut có phạm vi chính xác

### Bước 1 — Viết chính sách bằng ngôn ngữ nghiệp vụ

Ví dụ: “Ghi audit một lần cho mỗi thao tác xác nhận thay đổi đơn hàng; không ghi audit cho các phép tính xem trước.”

Cách viết này rõ hơn “chọn `*Service` và loại `get*`”, vì nó chỉ ra **lý do** lựa chọn, không chỉ dấu hiệu tên.

### Bước 2 — Xác định loại join point

Chọn điểm method call hay method execution? Với một thao tác lồng nhau, advice cần chạy ở boundary ngoài cùng hay trên mọi lời gọi nội bộ? Loại điểm là một phần của yêu cầu.

### Bước 3 — Liệt kê tập dương tính và âm tính

Ghi một danh sách độc lập:

- Những phương thức, đường gọi hoặc thao tác **bắt buộc** có advice.
- Những thao tác **không được** có advice.
- Trường hợp biên: overload, subclass, helper, self-invocation, public/private, async và lỗi.

Không nhất thiết phải kiểm kê mọi method trong hệ thống, nhưng điểm quan trọng phải có chứng cứ rõ.

### Bước 4 — Chọn tiêu chí tương đối ổn định

Ưu tiên tiêu chí gắn với hợp đồng được quản trị: nhóm chức năng, annotation, tên kiểu/package theo convention có kiểm thử, hoặc chữ ký tường minh khi cần.

### Bước 5 — Viết pointcut dễ đọc

Kết hợp điều kiện bằng named pointcut, ghi ngoặc `AND/OR/NOT` rõ và chú thích lý do loại trừ. Tránh kéo dài vô hạn các ngoại lệ để cứu một wildcard quá rộng; khi đó có thể cần thay đổi cách xác định ranh giới.

### Bước 6 — Kiểm tra kết quả khớp tĩnh

Nếu công cụ hỗ trợ thống kê/danh sách điểm weaving, so sánh với tập dương và âm. Một biểu thức hợp cú pháp nhưng **không khớp điểm nào** không phải thành công.

### Bước 7 — Kiểm tra đường chạy động

Thực thi các thao tác thật và kiểm tra advice chạy đúng lúc. Đặc biệt quan tâm lời gọi qua proxy, tự gọi phương thức, lớp nạp động, ngữ cảnh đa luồng và điều kiện phụ thuộc tham số.

### Bước 8 — Đo overhead và kiểm soát thay đổi

Với hot path, đo tần suất và chi phí advice; với chính sách quan trọng, bảo đảm các lần thay đổi source và weaving làm chạy lại kiểm thử coverage. Đừng cố loại hết FP bằng điều kiện runtime rất tốn kém mà không đo tác động.

## 16. Những ca kiểm thử nên có

| Ca | Mục tiêu | Lỗi phát hiện |
| --- | --- | --- |
| Phương thức thuộc tập bắt buộc | Advice chạy đúng một lần tại boundary quy định | FN |
| Phương thức ngoài chính sách | Advice không chạy | FP |
| Overload mới | Chọn đúng chữ ký/kiểu tham số | FN hoặc FP do `(..)` |
| Thêm lớp cùng hậu tố tên | Không mở phạm vi trái ý | FP do wildcard |
| Đổi package/rename | Phạm vi vẫn phản ánh hợp đồng | FN/FP khi refactor |
| Lời gọi nội bộ | Chạy đúng theo cơ chế weaving đã chọn | FN ở proxy boundary |
| Hai aspect cùng chọn | Thứ tự và tác động đúng | Sai ngữ nghĩa dù điểm khớp |
| Ngoại lệ từ nghiệp vụ | Advice giữ/đổi lỗi theo hợp đồng | Nuốt hoặc che lỗi |
| Nhiều invocation đồng thời | Trạng thái theo lần gọi độc lập | Nhiễu context |
| Phương thức trả future | Đo đúng ranh giới kết thúc | Nhầm method return với task completion |

**Một bài kiểm thử chỉ xác nhận advice đã chạy ở ít nhất một nơi là chưa đủ.** Cần chứng minh những nơi không mong muốn không bị ảnh hưởng và những đường bắt buộc không bỏ sót.

## 17. Các sai lầm thường gặp

**“Biểu thức ngắn hơn tức là pointcut tốt hơn.”** Sai. Độ ngắn không đo được FP/FN, khả năng bảo trì hoặc hậu quả khi aspect chạy sai.

**“Dùng wildcard thật rộng để chắc chắn không bỏ sót.”** Có thể tăng recall nhưng tạo FP lớn. Với advice có tác dụng phụ, lỗi chọn thừa nguy hiểm.

**“Liệt kê mọi tên phương thức sẽ không bao giờ sai.”** Danh sách vẫn có thể thiếu phương thức mới, khớp quá rộng do overload hoặc sai loại join point.

**“`*Service` bảo đảm toàn bộ phương thức là nghiệp vụ.”** Không. Tên kiểu chỉ là quy ước; các helper hoặc thao tác quản trị có thể nằm trong cùng lớp.

**“Annotation khiến phạm vi đúng tuyệt đối.”** Không. Dấu có thể thiếu, thừa, bị khai báo ở vị trí không được công cụ nhận diện hoặc không được weaving trên một đường chạy.

**“Chỉ cần kiểm thử kết quả trả về.”** Chưa đủ. Advice có thể chạy thừa mà không đổi giá trị nhưng gây log nhạy cảm, overhead hoặc thứ tự sai.

**“Pointcut khớp trên lý thuyết thì advice luôn chạy.”** Sai. Cần đúng mô hình join point, phạm vi weaving, đường gọi và điều kiện động.

**“Mọi điểm được chọn đều tốn chi phí ngang nhau.”** Sai. Tần suất invocation, mức độ nóng, loại advice và chi phí đánh giá điều kiện có thể khác nhau rất lớn.

**“Loại `get*` có nghĩa đã loại các thao tác chỉ đọc.”** Không thể suy luận ngữ nghĩa nghiệp vụ từ tiền tố; một phương thức có thể có side effect dù tên bắt đầu bằng `get`.

## 18. Bài tập tự đánh giá

**Bài 1.** Tập bắt buộc gồm bốn phương thức. Pointcut A chọn cả bốn cộng sáu phương thức khác. Hãy tính precision và recall.

Có `TP=4`, `FP=6`, `FN=0`; precision bằng `4/10 = 0,40`, recall bằng `4/4 = 1,00`. Mẫu bao phủ đủ nhưng chọn thừa.

**Bài 2.** Pointcut B chỉ chọn ba trong bốn phương thức bắt buộc và không chọn thừa. Nó tốt hơn pointcut A không?

Precision của B bằng `1,00`, recall bằng `3/4 = 0,75`. Không thể quyết định chỉ bằng một con số; phải xem **hậu quả bỏ sót** và **hậu quả chọn thừa** theo chính sách. Nếu bốn phương thức đều bắt buộc kiểm toán, FN có thể không chấp nhận được.

**Bài 3.** Một biểu thức `*Service.*(..)` bất ngờ áp dụng advice cho helper mới. Nên sửa thế nào?

Trước hết xác định helper có thuộc chính sách không, sau đó chọn ranh giới thích hợp: kiểu/package chính xác hơn, một dấu metadata được quản trị, named pointcut có điều kiện hợp lý hoặc danh sách điểm tường minh. Thêm ca âm tính để tránh tái phát.

**Bài 4.** Một mẫu hẹp chỉ chọn `save(String)`, nhưng chương trình thêm `save(List<Item>)` cũng cần kiểm toán. Vì sao có FN?

Mẫu ràng buộc chữ ký cũ; overload mới chưa thuộc tập khớp. Nếu muốn cả hai, sửa pointcut theo yêu cầu và kiểm thử, không mặc định mở thành `save(..)` nếu các overload khác có ý nghĩa khác.

**Bài 5.** Pointcut chọn đúng `checkout()` theo chữ ký nhưng một đường gọi không chạy advice. Cần kiểm tra gì?

Kiểm tra join point là call hay execution, phạm vi weaving, lời gọi có đi qua proxy không, điều kiện động có khớp không và advice bao ngoài có chặn chuyển tiếp không. Đó có thể là **lỗi coverage runtime** dù mẫu chữ ký đúng.

**Bài 6.** Một chính sách logging có precision cao nhưng khiến ứng dụng chậm. Có mâu thuẫn không?

Không. Precision đo tỷ lệ chọn đúng, không đo **tần suất** các join point hay chi phí của advice. Một điểm được chọn đúng nhưng cực kỳ nóng vẫn có thể chiếm nhiều tài nguyên.

## 19. Những nguyên tắc thực hành đáng giữ

1. **Bắt đầu từ chính sách, không bắt đầu từ wildcard.** Xác định tập cần can thiệp độc lập với biểu thức.
2. **Xác định loại join point và phạm vi weaving.** Một mẫu tĩnh đúng chưa bảo đảm mọi đường chạy được bao phủ.
3. **Tối ưu cả chọn đúng lẫn tránh chọn nhầm.** Luôn kiểm tra TP, FP, FN theo mức độ rủi ro.
4. **Ưu tiên ranh giới có ý nghĩa và được quản trị.** Annotation, package convention và danh sách tường minh đều cần kiểm chứng.
5. **Giữ biểu thức dễ giải thích.** Dùng điều kiện và named pointcut rõ ràng thay vì chỉ cố tiết kiệm ký tự.
6. **Xem refactor là thay đổi có thể ảnh hưởng chính sách.** So sánh tập điểm được chọn trước/sau khi thay đổi.
7. **Đo chi phí runtime.** Số join point tĩnh không phản ánh hoàn toàn overhead của advice.
8. **Bảo vệ hành vi nghiệp vụ.** Với advice có thể ngăn, thử lại hoặc đổi lỗi, phạm vi sai có thể tạo side effect nghiêm trọng.

Một pointcut tốt là pointcut có **ranh giới đúng, có thể giải thích, kiểm thử được và ổn định đủ lâu trước thay đổi mã**. Độ ngắn chỉ là một thuộc tính phụ trợ. Giá trị thật của biểu thức đến từ khả năng biểu đạt đúng chính sách và chứng minh được điều gì sẽ xảy ra tại các join point khi hệ thống vận hành.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="pointcut-basics-012">Kiểu tổng quát, mã sinh và phương thức cầu nối khi nền tảng có</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu tổng quát, mã sinh và phương thức cầu nối khi nền tảng có

Khi chọn điểm can thiệp bằng **pointcut** trong lập trình hướng khía cạnh (**Aspect-Oriented Programming – AOP**), mã nguồn thường không phải toàn bộ chương trình mà công cụ can thiệp thật sự nhìn thấy. Trình biên dịch hoặc runtime có thể tạo thêm phương thức, lớp đại diện, điểm chuyển tiếp và chữ ký đã biến đổi. Đặc biệt trong Java, **generics**, **type erasure**, **bridge methods** và **synthetic/generated members** có thể làm một quy tắc chọn phương thức hoạt động khác dự đoán.

Bài này tập trung vào một câu hỏi rất cụ thể của **pointcut cơ bản**: **khi xuất hiện kiểu tổng quát hoặc mã được tạo ra, những phương thức và chữ ký nào là đối tượng so khớp, và khi nào cần tính đến phương thức cầu nối?** Phần “khi nền tảng có” là điều kiện quan trọng: mỗi cơ chế AOP hỗ trợ một tập **join point** và quy tắc so khớp khác nhau; không được mặc định mọi phương thức trong bytecode đều được nhận diện thành execution join point hoặc đều được weave advice.

## 1. Điểm xuất phát: mã nguồn khác với biểu diễn sau biên dịch

Trong AOP:

- **Join point (điểm nối)** là điểm thực thi mà cơ chế AOP hỗ trợ can thiệp, chẳng hạn lời gọi hoặc phần thực thi phương thức.
- **Pointcut** là quy tắc chọn những join point được quan tâm.
- **Advice** là hành vi chạy tại những điểm được chọn.
- **Weaving** là quá trình kết hợp hành vi khía cạnh vào chương trình, có thể dựa trên mã nguồn, bytecode, lớp được nạp hoặc cơ chế chuyển tiếp lúc chạy.

Khi đọc một pointcut kiểu “chọn tất cả `handle(...)`”, ta thường nhìn vào phương thức Java do lập trình viên viết. Nhưng sau biên dịch, một lớp đôi khi có nhiều phương thức mang tên `handle` hơn số khai báo trong nguồn. Một trong số đó có thể là **bridge method** do compiler tạo để duy trì cơ chế gọi đa hình trên JVM.

Ba câu hỏi cần tách biệt:

1. **Mã nguồn mô tả gì?** Ví dụ `Handler<String>.handle(String)`.
2. **Bytecode/reflection thực tế có gì?** Ví dụ một phương thức `handle(String)` và một bridge `handle(Object)`.
3. **Mô hình join point của cơ chế AOP chọn được gì?** Nó có thể cố ý bỏ qua phương thức compiler-generated, chỉ quan sát một loại điểm nối, hoặc có các quy tắc ánh xạ riêng.

**Có hai phương thức xuất hiện trong Reflection không đồng nghĩa một lời gọi nghiệp vụ luôn có hai advice execution.** Đây là điểm quan trọng xuyên suốt bài.

## 2. Generics và type erasure tác động lên chữ ký ra sao?

Java generics giúp kiểm tra kiểu lúc biên dịch. Ví dụ:

~~~java
interface Handler<T> {
    T handle(T input);
}

final class TextHandler implements Handler<String> {
    @Override
    public String handle(String input) {
        return input.toUpperCase();
    }
}
~~~

Đây là các khai báo minh họa, chưa phải chương trình có `main`. Trong nguồn, khi `T=String`, người đọc có thể hình dung hợp đồng `handle(String):String`. Tuy nhiên, Java biên dịch generics bằng **type erasure**:

- Tham số kiểu chưa bị ràng buộc như `T` được xóa về `Object`.
- Tham số kiểu có bound được xóa về bound thích hợp (ví dụ `T extends Number` về `Number`).
- Compiler chèn các phép ép kiểu khi cần để giữ kiểm tra kiểu nguồn.
- Compiler **có thể tạo bridge method** để giữ hành vi override/đa hình sau khi chữ ký bytecode bị xóa kiểu.

Vì thế, chữ ký đã xóa kiểu của phương thức trong giao diện `Handler<T>` là dạng `Object handle(Object)`, trong khi phương thức chuyên biệt mà `TextHandler` viết là `String handle(String)`.

| Mức quan sát | Dạng ví dụ | Ý nghĩa |
| --- | --- | --- |
| Khai báo generic ở nguồn | `T handle(T)` | Hợp đồng tham số kiểu `T` |
| Khi gắn kiểu cụ thể | `String handle(String)` | Cách người dùng `Handler<String>` hiểu hợp đồng tại compile time |
| Chữ ký JVM sau erasure của hợp đồng | `Object handle(Object)` | Tên và descriptor mà JVM cần để liên kết lời gọi đã xóa kiểu |
| Phương thức chuyên biệt của lớp triển khai | `String handle(String)` | Phần thân nghiệp vụ do người viết |
| Phương thức cầu nối được compiler có thể tạo | `Object handle(Object)` | Bộ chuyển tiếp tương thích với chữ ký đã erasure |

**Không có lớp runtime riêng** `Handler<String>` và `Handler<Integer>` chỉ vì dùng hai đối số kiểu. Các thông tin generic có thể còn được giữ trong metadata chữ ký và được Reflection trả về bằng những API như `getGenericParameterTypes()`, nhưng điều đó không có nghĩa mọi pointcut hay mọi kiểu join point đều so khớp trực tiếp theo tham số kiểu nguồn.

### 2.1 “Generic type” có ít nhất ba lớp nghĩa

Khi một pointcut chọn theo kiểu, hãy phân biệt:

- **Declared generic signature:** ví dụ `Handler<T>`; có thể đọc được một phần qua metadata generic.
- **Erased JVM signature:** ví dụ tham số `Object`; đây là phần của descriptor sau biên dịch.
- **Runtime class của đối tượng/đối số:** ví dụ `TextHandler` và `String`; kiểm tra runtime target/args không đồng nghĩa đọc chữ ký generic.

Ví dụ một biến `List<String>` và một biến `List<Integer>` đều có thể trỏ tới đối tượng `ArrayList` cùng loại runtime class. Dựa vào `getClass()` không thể phân biệt hai tham số generic đó theo cách phân biệt hai runtime class thực sự khác nhau.

## 3. Phương thức cầu nối (bridge method) là gì?

**Bridge method** là phương thức do Java compiler sinh, thường được đánh dấu **bridge** và **synthetic**, để nối chênh lệch giữa ngữ nghĩa phương thức của Java ở mã nguồn và chữ ký/cơ chế dispatch ở JVM.

Ví dụ trước có thể được hình dung bằng **giả mã gần với bytecode**:

~~~java
// Chỉ là mô hình ý nghĩa; không được viết chung hai phương thức này
// như mã nguồn Java thường để thay compiler.
class TextHandler implements Handler<String> {
    public String handle(String input) {
        return input.toUpperCase();
    }

    // Bridge do compiler tạo cho descriptor đã xóa kiểu:
    public Object handle(Object input) {
        return handle((String) input);
    }
}
~~~

Phương thức cầu nối nhận `Object`, ép sang `String` rồi chuyển tiếp đến phần thân chuyên biệt. Nhờ đó, lời gọi được biên dịch thông qua hợp đồng generic đã xóa kiểu vẫn có thể đến đúng mã override.

**Vì sao cần?** Sau erasure, `Handler<T>.handle(T)` có descriptor dựa trên `Object`, nhưng phần triển khai `TextHandler.handle(String)` có descriptor dựa trên `String`. Nếu thiếu bước tương thích, việc gọi qua tham chiếu `Handler<String>` có thể không đi đúng phần thân chuyên biệt theo cơ chế dispatch của JVM.

### 3.1 Bridge không chỉ xuất hiện với generic

Bridge còn có thể được sinh để hỗ trợ **covariant return type** (override dùng kiểu trả về hẹp hơn) hoặc các khác biệt chữ ký cần giữ tính tương thích. Vì vậy không nên kết luận “`isBridge()==true` có nghĩa phương thức chắc chắn xuất phát từ một generic `T`” hoặc “mọi bridge đều có một tham số `Object`”.

### 3.2 Bridge và synthetic không phải cùng một khái niệm

Reflection của Java cung cấp:

- `Method.isBridge()`: cho biết phương thức được đánh dấu là bridge method.
- `Method.isSynthetic()`: cho biết phương thức là một phần tử synthetic, do công cụ/compiler tạo thay vì là khai báo nguồn thông thường theo cách mô tả của API.

Một bridge thường là synthetic, nhưng **không phải mọi phương thức synthetic đều là bridge**. Compiler cũng có thể tạo các thành phần phụ trợ khác. Khi muốn lọc bridge, hãy xét `isBridge()` cụ thể; khi muốn lọc mọi mã compiler-generated, `isSynthetic()` là một tín hiệu nhưng cũng cần hiểu giới hạn và chính sách của cơ chế đang dùng.

## 4. Vì sao bridge ảnh hưởng lựa chọn pointcut?

Một điểm quan trọng: **Reflection liệt kê phương thức trong class file**, còn **AOP lựa chọn join point theo mô hình riêng**. Hai danh sách không nhất thiết giống nhau.

Giả sử một lớp có hai descriptor thực tế:

~~~text
TextHandler.handle(String) -> String    (phương thức nghiệp vụ)
TextHandler.handle(Object) -> Object    (bridge synthetic)
~~~

Khi xây bộ chọn, cần phân biệt ít nhất bốn ý định:

1. **Chọn hành vi nghiệp vụ “handle” một lần trên mỗi lần gọi hợp lệ.** Có thể cần chuẩn hóa phương thức gốc/bridge tùy mô hình.
2. **Chọn mọi phương thức theo tên `handle`.** Bộ tìm kiếm Reflection không lọc bridge có thể trả cả hai, nhưng advice thực tế có được gắn cả hai hay không còn phụ thuộc AOP.
3. **Chọn theo tham số `String` hay `Object`.** Hai descriptor tồn tại khác nhau ở JVM; một mẫu quá hẹp có thể bỏ lỡ điểm được cơ chế thực sự expose.
4. **Chọn theo chữ ký nguồn generic.** Cần xem cơ chế AOP hiểu generic signature, erasure, quan hệ subtype và bridge như thế nào.

Nếu công cụ **instrument bytecode trên mọi entry method** mà không phân biệt bridge, một lời gọi qua bridge có thể đi qua cả bridge và phần thân chuyên biệt. Nếu advice được gắn ở cả hai entry, nó *có khả năng* được thực hiện hai lần cho một thao tác nghiệp vụ. Đây là **rủi ro của một số cách instrument**, không phải kết luận đúng cho mọi ngôn ngữ AOP.

Ngược lại, **một số hệ thống AOP chủ động loại bridge synthetic khỏi tập execution join point**. Chẳng hạn tài liệu về generic của một mô hình AOP có ngôn ngữ join point riêng ghi nhận rằng các bridge không có execution join point trong mô hình đó, dù các lời gọi có thể liên quan tới bridge theo ngữ nghĩa riêng. Do vậy, không nên khẳng định “cứ thấy bridge là sẽ có hai execution advice”.

### 4.1 Phân biệt điểm gọi và điểm thực thi

- **Call join point:** quan sát vị trí một lời gọi được phát sinh; chữ ký tĩnh tại call site và lớp nhận lời gọi có thể khác chữ ký của phương thức thực thi chuyên biệt.
- **Execution join point:** quan sát lúc một phần thân phương thức được thực hiện, theo cách mô hình AOP định nghĩa.

Ví dụ:

~~~text
Handler<String> h = new TextHandler()
                 |
                 v
      CALL theo hợp đồng đã erasure
                 |
                 v
        Bridge (nếu được dùng)
                 |
                 v
      TextHandler.handle(String)
                 |
                 v
       Phần thân nghiệp vụ
~~~

Sơ đồ mô tả một tuyến dispatch thường gặp sau biên dịch; nó **không khẳng định có join point AOP tại tất cả các mũi tên**. Có thể tồn tại bridge trong class file, nhưng mô hình AOP chỉ định nghĩa execution point cho phần thân chuyên biệt. Hoặc công cụ chặn ở ranh giới lời gọi qua proxy và không thấy chuyển tiếp nội bộ. Quy tắc thực tế phải kiểm tra theo phiên bản/cơ chế triển khai.

## 5. Ví dụ Java 17 chạy độc lập: nhìn thấy bridge bằng Reflection

Chương trình sau **không dùng một công cụ AOP cụ thể**. Nó dùng Reflection để thấy phương thức compiler-generated, từ đó minh họa vì sao một bộ phân tích pointcut cần phân biệt descriptor và cờ bridge. Ví dụ biên dịch với `javac` của JDK 17.

Lưu toàn bộ dưới tên `BridgePointcutDemo.java`:

~~~java
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class BridgePointcutDemo {
    interface Handler<T> {
        T handle(T value);
    }

    static final class TextHandler implements Handler<String> {
        @Override
        public String handle(String value) {
            System.out.println("BUSINESS " + value);
            return value.toUpperCase(Locale.ROOT);
        }
    }

    public static void main(String[] args) throws Exception {
        Method contract = Handler.class.getMethod("handle", Object.class);
        System.out.println("CONTRACT "
                + contract.getParameterTypes()[0].getSimpleName()
                + " generic="
                + contract.getGenericParameterTypes()[0].getTypeName());

        List<Method> methods = Arrays.stream(
                    TextHandler.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("handle"))
                .sorted(Comparator.comparing(Method::isBridge))
                .toList();

        for (Method method : methods) {
            System.out.println("METHOD "
                    + method.getParameterTypes()[0].getSimpleName()
                    + "->" + method.getReturnType().getSimpleName()
                    + " bridge=" + method.isBridge()
                    + " synthetic=" + method.isSynthetic());
        }

        long nameCandidates = methods.size();
        long businessCandidates =
                methods.stream().filter(m -> !m.isBridge()).count();
        System.out.println("CANDIDATES all=" + nameCandidates
                + " nonBridge=" + businessCandidates);

        TextHandler target = new TextHandler();
        Handler<String> throughContract = target;

        System.out.println("RESULT interface="
                + throughContract.handle("a"));
        System.out.println("RESULT direct=" + target.handle("b"));

        Method bridge = methods.stream()
                .filter(Method::isBridge)
                .findFirst().orElseThrow();
        System.out.println("RESULT reflectiveBridge="
                + bridge.invoke(target, "c"));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac BridgePointcutDemo.java
java BridgePointcutDemo
~~~

Đầu ra dự kiến với `javac` JDK 17:

~~~text
CONTRACT Object generic=T
METHOD String->String bridge=false synthetic=false
METHOD Object->Object bridge=true synthetic=true
CANDIDATES all=2 nonBridge=1
BUSINESS a
RESULT interface=A
BUSINESS b
RESULT direct=B
BUSINESS c
RESULT reflectiveBridge=C
~~~

### 5.1 Phân tích output

**`CONTRACT Object generic=T`**: `Handler<T>.handle(T)` đã có tham số erasure là `Object`, nhưng Reflection vẫn đọc được metadata generic `T` của khai báo.

**Hai dòng `METHOD`**: `TextHandler` có một phương thức nghiệp vụ với tham số/kiểu trả về `String`, và một bridge `Object -> Object` sinh để thích ứng hợp đồng đã erasure. `isBridge()` và `isSynthetic()` giúp phân biệt hai phương thức. Chương trình sắp xếp theo cờ bridge để đầu ra không phụ thuộc thứ tự `getDeclaredMethods()` trả về.

**`CANDIDATES all=2 nonBridge=1`**: nếu một bộ quét Reflection chỉ lọc tên `handle`, nó thấy hai candidate; nếu lọc `!isBridge()`, nó chỉ giữ phương thức chuyên biệt. Đó là **kết quả quét Reflection**, không phải số lần advice thực sự chạy.

**Ba lần `BUSINESS`**: mỗi lời gọi từ giao diện, lời gọi trực tiếp và lời gọi bridge bằng Reflection cuối cùng chỉ thực hiện phần thân chuyên biệt **một lần cho mỗi lời gọi trong ví dụ**. Một lời gọi qua bridge có thể bao gồm bước chuyển tiếp nội bộ nhưng không tự động in hai dòng `BUSINESS`.

**`RESULT reflectiveBridge=C`**: Reflection gọi bridge với đối số phù hợp `String`; bridge ép kiểu và chuyển tới thân `handle(String)`.

Các descriptor, cờ và việc sinh bridge trong output dựa trên **`javac` JDK 17** với đoạn nguồn đã cho. Một compiler, chế độ build hoặc môi trường instrument khác có thể sinh thêm/thay đổi mã tổng hợp; luôn đối chiếu class file hoặc Reflection thực tế trước khi đặt giả định cho pointcut.

### 5.2 Tại sao ví dụ không được gọi là một aspect hoàn chỉnh?

Chương trình chỉ liệt kê thành phần class file và gọi Java thông thường. Nó **không định nghĩa pointcut thực thi bởi một hệ thống AOP**, không weave advice và không chứng minh bộ AOP sẽ tác động vào cả bridge hay chỉ phương thức chuyên biệt. Mối liên hệ AOP nằm ở cách người thiết kế phải xử lý **phân loại candidate** và **quy tắc join point** trước khi áp dụng hành vi cắt ngang.

## 6. Mã sinh ngoài bridge: có những dạng nào?

Ngoài bridge, “mã sinh” có thể xuất hiện ở nhiều tầng. Không nên xem tất cả đều đồng nghĩa synthetic method.

| Nguồn tạo mã | Ví dụ | Vấn đề khi chọn pointcut |
| --- | --- | --- |
| **Compiler Java** | Bridge, một số phương thức phụ trợ, chi tiết triển khai lambda | Có thể xuất hiện tên/chữ ký không viết trong nguồn |
| **Proxy runtime** | Lớp đại diện triển khai interface và chuyển lời gọi qua handler | Kiểu runtime của proxy khác kiểu runtime của đối tượng nghiệp vụ |
| **Biến đổi bytecode** | Mã instrument được chèn vào lớp | Cơ chế AOP có thể nhìn thấy mã trước/sau biến đổi khác nhau |
| **Công cụ sinh mã khi build** | Lớp triển khai hoặc adapter được tạo từ mô hình nguồn | Mã sinh có thể là phương thức bình thường, **không nhất thiết synthetic** |
| **Lambda và method reference** | Cấu trúc thực thi do runtime/compiler triển khai | Danh tính lớp và thành phần phụ trợ có thể không ổn định để làm chính sách theo tên |

### 6.1 Proxy và đối tượng nghiệp vụ

Giả sử một proxy triển khai interface `Handler`, rồi chuyển lời gọi tới `TextHandler`. Lúc này có thể có hai đối tượng:

- **Proxy:** đối tượng mà bên gọi nắm giữ.
- **Target:** đối tượng `TextHandler` thật sự xử lý nghiệp vụ.

`proxy.getClass()` và `target.getClass()` không nhất thiết giống nhau. Pointcut theo runtime type phải xác định rõ nó kiểm tra **proxy**, **target**, **bên gọi** hay **đối số**. Một bộ chọn theo tên lớp proxy do runtime sinh có thể mong manh vì tên/lớp được tạo không phải hợp đồng nghiệp vụ.

Proxy qua giao diện cũng chỉ quan sát các lời gọi **thật sự đi qua proxy**. Lời gọi nội bộ trên đối tượng đích và lời gọi trực tiếp bỏ qua proxy có thể không chạy advice của proxy. Tập join point quan sát được khác với một hệ thống biến đổi bytecode có khả năng can thiệp các điểm sâu trong phần thân.

### 6.2 Synthetic không đồng nghĩa “cần bỏ qua”

Bridge synthetic có thể chỉ là adapter kỹ thuật. Nhưng trong một số chương trình, một phương thức được sinh có thể là điểm cần quan sát khi điều tra lỗi hoặc đo hạ tầng. Vì vậy không nên áp dụng quy tắc toàn cục “bỏ tất cả synthetic” cho mọi mục tiêu.

Ngược lại, các lớp do công cụ sinh ra có thể chứa phương thức **không được đánh dấu synthetic**. Nếu chỉ dựa vào `isSynthetic()`, bộ lọc không thể nhận diện tất cả mã sinh. Cần dựa vào mục đích pointcut, nguồn tạo mã và khả năng của mô hình join point.

## 7. Lựa chọn theo generic signature, erased signature và runtime args

Bốn cách chọn có thể tạo ra các tập điểm khác nhau:

| Ý định chọn | Thông tin cần xét | Nguy cơ nếu nhầm |
| --- | --- | --- |
| Chọn các phương thức có hợp đồng generic `Handler<T>` | Metadata generic và mô hình kiểu của công cụ | Erasure che mất tham số kiểu nguồn |
| Chọn phương thức có JVM parameter `Object` | Descriptor sau erasure | Có thể match bridge thay vì phần thân chuyên biệt |
| Chọn phần thân chuyên biệt `handle(String)` | Chữ ký thực của phương thức triển khai | Có thể bỏ sót các điểm gọi đi qua hợp đồng khi cơ chế dùng chữ ký khác |
| Chọn lần gọi có đối số runtime là `String` | Runtime argument binding/typing | `null` không có runtime class; dữ liệu runtime khác chữ ký khai báo |

Một số ngôn ngữ pointcut hỗ trợ diễn tả mẫu generic type và ràng buộc tham số phong phú; số khác chỉ làm việc với descriptor/Method của bytecode hoặc với chữ ký interface. Không được suy khả năng từ cú pháp Java generic ở mã nguồn.

### 7.1 Kiểu generic có bound

Với `T extends Number`, erasure của `T` có thể là `Number`, không phải `Object`. Một pointcut được viết dựa trên giả định “mọi generic đều bị xóa thành Object” sẽ sai cho generic có bound.

Cũng cần phân biệt **phương thức generic** `<T> T convert(T)` với **phương thức của lớp/giao diện generic**. Chữ ký generic có thể được lưu lại qua metadata; cách công cụ AOP đọc metadata đó phụ thuộc vào implementation.

### 7.2 Generic không luôn xác định được từ đối tượng runtime

Một đối số là `ArrayList<String>` tại vị trí gọi không mang riêng một runtime class có tên `ArrayList<String>`. Nếu pointcut muốn dựa trên kiểu phần tử `String`, có thể cần phân tích thông tin khai báo, kiểm tra dữ liệu một cách an toàn, hoặc chọn một thiết kế khác thay vì dựa vào `getClass()`.

Với **giá trị `null`**, không thể truy vấn runtime class; nhưng chữ ký phương thức vẫn có thông tin tham số được khai báo. Cách ngôn ngữ pointcut xử lý `null` trong biểu thức match kiểu runtime phải được kiểm tra riêng.

## 8. Method signature, overriding và bridge: những điểm tinh tế

### 8.1 Bridge do covariant return

Ví dụ một lớp con override phương thức trả về `Object` thành phương thức trả về `String`:

~~~java
class Parent {
    Object value() {
        return "parent";
    }
}

class Child extends Parent {
    @Override
    String value() {
        return "child";
    }
}
~~~

Đây là **hai lớp minh họa**, không phải chương trình chạy độc lập. Ở mức Java, đây là override hợp lệ vì kiểu trả về `String` là kiểu con của `Object`. JVM phân biệt descriptor có return type; compiler có thể sinh bridge `Object value()` trong `Child` để giữ hành vi gọi đa hình cho mã dùng chữ ký của `Parent`.

Do đó bridge không chỉ gắn với trường hợp tham số kiểu `T` bị xóa thành `Object`. Nếu mục tiêu pointcut phụ thuộc vào kiểu trả về hoặc declaring class, phải xét cả trường hợp này.

### 8.2 Bridge không phải overload nghiệp vụ

Một lớp có thể có hai phương thức cùng tên vì **overload** do người viết chủ đích:

~~~java
void log(String message) { /* xử lý String */ }
void log(Object value)   { /* xử lý Object */ }
~~~

Đây là mảnh minh họa trong một lớp giả định. Cả hai đều là thành phần nghiệp vụ bình thường, không nên loại `log(Object)` chỉ vì nó có tham số `Object`. Phải dùng metadata `isBridge()` hoặc quy tắc nguồn được chứng minh, **không đoán bridge theo tên hoặc tham số**.

### 8.3 Annotation có thể nằm ở khai báo khác

Một annotation có thể nằm trên phương thức của interface, lớp cha, override chuyên biệt hoặc một thành phần generated. Không được tự giả định bridge mang mọi annotation của phương thức gốc, hoặc mọi cơ chế AOP tự đi tìm annotation ở interface/override giống nhau.

Trong Java, `@Inherited` áp dụng cho một phạm vi kế thừa annotation kiểu lớp; nó không tự tạo quy tắc kế thừa annotation phương thức qua mọi override. Pointcut dựa trên annotation phải xác định **đang quan sát Method nào** và **cơ chế tìm metadata nào**.

## 9. Nguy cơ thực tiễn: đếm trùng, bỏ sót và lựa chọn sai

### 9.1 Đếm trùng thao tác logic

Giả sử mục tiêu là “mỗi lần xử lý tin nhắn ghi đúng một sự kiện nghiệp vụ”. Nếu công cụ instrument cả bridge và phương thức chuyên biệt, một lời gọi qua bridge có thể phát sinh hai sự kiện. Từ đó thống kê request count, latency hoặc audit có thể tăng sai.

Cách xử lý có thể là chọn join point nghiệp vụ đã chuẩn hóa, loại bridge khi điều đó phù hợp, hoặc thiết kế nhận diện sự kiện sao cho không đếm trùng. **Nhưng chỉ lọc bridge khi đã chứng minh bridge thực sự là nguyên nhân**; một số mô hình AOP không tạo execution join point cho bridge, nên tự thêm bộ lọc có thể vô ích hoặc gây mất tín hiệu ở call join point.

### 9.2 Bỏ sót đường gọi

Ngược lại, chọn chỉ `handle(String)` có thể không khớp một loại **call join point** vốn biểu thị chữ ký `handle(Object)` do erasure, nếu bộ chọn làm việc ở lớp chữ ký đó. Cần xem nó phân tích nơi gọi, nơi thực thi, kiểu tĩnh hay runtime target; đừng sửa pointcut chỉ dựa trên một danh sách phương thức Reflection.

### 9.3 Nguy cơ khi dùng generated class làm điều kiện bảo mật

Nếu muốn kiểm tra quyền cho mọi thao tác theo interface `Handler`, việc chọn theo **tên lớp runtime được sinh** có thể bỏ sót đường gọi hoặc thay đổi khi cách tạo proxy đổi. Nên biểu diễn phạm vi theo hợp đồng nghiệp vụ/rành giới cần bảo vệ, xác minh tất cả đường gọi quan trọng, và thực hiện quyết định quyền trên danh tính đã được xác thực.

Một phép đếm phương thức hoặc log “advice đã chạy” ở một đường gọi không chứng minh toàn bộ đường truy cập dữ liệu đã được bảo vệ.

### 9.4 Lồng các advice và mã instrument tự sinh

Nếu một advice gọi hàm ghi log hoặc thống kê mà các hàm đó cũng khớp pointcut, mã bổ sung có thể **tự kích hoạt lại**. Mã được sinh bởi framework/runtime cũng có thể đi qua cùng phương thức nếu chọn quá rộng. Cần làm rõ ranh giới, điểm loại trừ và thứ tự can thiệp, đồng thời kiểm thử các đường phát sinh từ generated code.

## 10. Thiết kế pointcut chịu được biến đổi compiler/runtime

Một quy trình đánh giá phù hợp:

1. **Xác định mục tiêu:** muốn quan sát mỗi *lời gọi API*, mỗi *lần chạy thân phương thức*, hay mỗi *sự kiện nghiệp vụ*?
2. **Xác định loại join point:** call, execution, hoặc điểm chuyên biệt khác có được nền tảng hỗ trợ không?
3. **Xác định dữ liệu so khớp:** generic source metadata, JVM erased descriptor, lớp đích runtime, kiểu tham số runtime, annotation hay tên lớp generated?
4. **Xem lớp thực tế:** nếu làm việc ở JVM, kiểm tra `getDeclaredMethods()`, `isBridge()`, `isSynthetic()`, `getParameterTypes()`, `getGenericParameterTypes()` và bytecode khi cần.
5. **Đọc ngữ nghĩa công cụ:** nó có nhận bridge làm join point? Có bỏ qua synthetic? Có xử lý proxy, method override hoặc generic pattern đặc biệt không?
6. **Thử từng đường gọi:** qua interface, qua lớp cụ thể, qua proxy, trực tiếp, với giá trị null nếu hợp lệ, và với phương thức kế thừa/override.
7. **Kiểm tra kết quả advice:** mỗi sự kiện cần quan sát được ghi đúng số lần, vẫn giữ kết quả và ngoại lệ nghiệp vụ, không tạo overhead bất ngờ.

Nếu một nền tảng chỉ hỗ trợ proxy method calls, không nên đòi nó chọn các execution join point của mọi phương thức synthetic trong bytecode. Nếu một nền tảng hỗ trợ biến đổi bytecode, vẫn phải xác minh chính sách loại trừ mã sinh để tránh đếm trùng hoặc tác động ngoài phạm vi.

## 11. Ma trận kiểm thử pointcut

| Tình huống | Nguy cơ | Kết quả mong muốn cần xác minh |
| --- | --- | --- |
| `Handler<String>` gọi `TextHandler` qua interface | Descriptor sau erasure và bridge | Một sự kiện nghiệp vụ, số advice theo chính sách |
| Gọi `TextHandler.handle(String)` trực tiếp | Điểm gọi có chữ ký khác | Được chọn hoặc loại trừ đúng chủ đích |
| Reflection gọi bridge `handle(Object)` | Chuyển tiếp đến `handle(String)` | Không vô tình đếm hai lần nếu chỉ cần một |
| Override covariant return | Bridge có thể xuất hiện dù không có generic `T` | Không phân loại sai chỉ theo tên và tham số |
| Lớp được sinh nhưng không synthetic | Bộ lọc synthetic bỏ sót mã sinh | Phạm vi được quyết định theo chính sách, không chỉ một flag |
| Proxy chuyển tiếp vào đối tượng đích | Proxy class khác target class | Rõ runtime type nào được xét |
| Generic có bound `T extends Number` | Erasure không phải Object | Điểm match theo đúng descriptor/metadata |
| Annotation trên interface/override | Metadata ở nhiều nơi | Đúng khai báo và quy tắc lookup của công cụ |
| Lời gọi bỏ qua proxy | Advice proxy không chạy | Có rào chắn tại điểm bắt buộc nếu là chính sách an ninh |

## 12. Bài tập suy luận

**Bài tập 1.** Một ứng dụng có `Processor<T>.process(T)` và `TextProcessor implements Processor<String>`. Reflection thấy hai phương thức `process` trong lớp triển khai, một có `isBridge()==true`. Có được kết luận mọi lời gọi đều kích hoạt hai advice không?

**Đáp:** không. Reflection cho biết class file có hai phương thức; số join point và advice còn phụ thuộc mô hình AOP, loại pointcut và vị trí weaving. Có công cụ cố ý không tạo execution join point cho bridge.

**Bài tập 2.** Một pointcut theo chữ ký sau erasure mong tham số `Object`, nhưng code nghiệp vụ viết `process(String)`. Có thể xuất hiện phương thức `Object` ở đâu?

**Đáp:** từ hợp đồng generic đã erasure và có thể từ bridge do compiler sinh trên lớp triển khai. Không tự giả định tất cả các điểm đó đều là execution join point.

**Bài tập 3.** Nếu mục tiêu là đếm số thao tác nghiệp vụ xử lý tin nhắn, nên lấy `getDeclaredMethods().length` làm số nghiệp vụ không?

**Đáp:** không. Danh sách ấy có thể chứa bridge, synthetic và phương thức phụ trợ khác. Nên xác định một sự kiện nghiệp vụ và điểm quan sát ổn định theo hợp đồng.

**Bài tập 4.** Nếu `T extends Number`, erasure có phải luôn là Object?

**Đáp:** không; bound có thể tạo descriptor dựa trên `Number`.

**Bài tập 5.** Nếu một wrapper tạo ra lớp đại diện mới, `wrapper.getClass()` và `target.getClass()` có chắc giống nhau?

**Đáp:** không. Kiểu runtime phụ thuộc đối tượng nào được quan sát.

**Bài tập 6.** Nếu compiler không sinh bridge trong trường hợp đang xét, bài toán generic không còn ảnh hưởng pointcut?

**Đáp:** vẫn có thể ảnh hưởng. Erasure, kiểu tĩnh tại call join point, kiểu thực tế tại execution join point và metadata generic vẫn có thể tạo sự khác nhau về so khớp.

## Tài liệu tham khảo

- [Oracle Java Tutorials — Type Erasure](https://docs.oracle.com/javase/tutorial/java/generics/erasure.html): cơ chế xóa kiểu và các hệ quả khi biên dịch.
- [Oracle Java Tutorials — Effects of Type Erasure and Bridge Methods](https://docs.oracle.com/javase/tutorial/java/generics/bridgeMethods.html): ví dụ compiler sinh bridge để giữ đa hình.
- [Java SE 17 — Method Reflection API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html): `isBridge()`, `isSynthetic()`, `getGenericParameterTypes()`.
- [AspectJ Programming Guide — Join Points and Pointcuts](https://eclipse.dev/aspectj/doc/latest/progguide/progguide.html): mô hình tham khảo về call/execution join point.
- [Generics in AspectJ 5 — Bridge Methods](https://eclipse.dev/aspectj/doc/released/adk15notebook/generics-inAspectJ5.html): ví dụ cụ thể cho việc bridge không tự động trở thành execution join point trong một mô hình AOP. Cần xem phiên bản tài liệu khớp nền tảng đang dùng.

**Ghi nhớ:** **generic trong mã nguồn → erasure ở bytecode → bridge/synthetic hoặc mã sinh khi cần → quy tắc join point/pointcut của nền tảng** là các tầng khác nhau. Một pointcut chính xác phải nêu rõ nó chọn **lời gọi, phần thực thi hay sự kiện nghiệp vụ nào**, theo **chữ ký và đối tượng nào**, và kiểm tra thực tế nền tảng có hỗ trợ những điểm đó trước khi áp dụng advice.


</details>

- [Quay lại đầu trang](#back-to-top)
