<a id="back-to-top"></a>

# Thiết kế công cụ

## Menu
- [Tách khám phá metadata khỏi thực thi thao tác](#tooling-design-001)
- [Định nghĩa chính sách field, property hoặc accessor cần dùng](#tooling-design-002)
- [Không mặc định mọi field đều là dữ liệu nghiệp vụ](#tooling-design-003)
- [Lọc static, synthetic, bridge và thành viên không phù hợp](#tooling-design-004)
- [Xử lý field trùng tên hoặc method ghi đè theo chính sách nhất quán](#tooling-design-005)
- [Sắp xếp metadata để đầu ra có thứ tự ổn định](#tooling-design-006)
- [Xác thực kiểu và khả năng truy cập trước khi chạy hàng loạt](#tooling-design-007)
- [Ánh xạ giá trị đầu vào theo quy tắc chuyển đổi tường minh](#tooling-design-008)
- [JavaBeans Introspector và khác biệt với duyệt field trực tiếp](#tooling-design-009)
- [Quét classpath khác khám phá một Class đã biết](#tooling-design-010)
- [Giữ constructor, setter và bất biến đối tượng làm ranh giới thiết kế](#tooling-design-011)

## <a id="tooling-design-001">Tách khám phá metadata khỏi thực thi thao tác</a>

<details>
<summary>Xem chi tiết</summary>

# Tách khám phá metadata khỏi thực thi thao tác trong Java Reflection

Trong các công cụ dựa trên Java Reflection (plugin registry, command runner, hệ thống đăng ký tool, framework kiểm thử), có hai nhiệm vụ riêng:

- **Discovery / khám phá metadata:** tìm các thao tác *có thể* được cung cấp, đọc tên, annotation, tham số, kiểu trả về, quyền truy cập, rồi xây danh mục.
- **Execution / thực thi:** nhận yêu cầu gọi một thao tác cụ thể, kiểm tra quyền và đầu vào, chuẩn bị receiver, rồi mới chạy code thật.

Hai việc thường cùng dùng đối tượng `Class<?>` và `Method`, nhưng không có cùng mức tác động. Việc người dùng bấm “Xem danh sách công cụ” phải không tự trở thành lời gọi xóa dữ liệu, gửi email hoặc cập nhật trạng thái. Tách hai giai đoạn giúp kiểm soát side effect, kiểm thử dễ hơn và ghi dấu việc ai đã thực thi thao tác nào.

## 1. Phân biệt lấy metadata với kích hoạt hành vi

| Hoạt động | API minh họa | Kết quả | Có yêu cầu chạy method nghiệp vụ? |
|---|---|---|---|
| Tham chiếu kiểu | `SomeType.class` | `Class<?>` | Không |
| Tải mà chưa yêu cầu khởi tạo | `Class.forName(name, false, loader)` | `Class<?>` | Không |
| Liệt kê method | `type.getDeclaredMethods()` | `Method[]` | Không |
| Đọc signature/annotation | `getName()`, `getParameterTypes()`, `getAnnotation(...)` | Metadata | Không |
| Đăng ký action | Kiểm tra annotation/chữ ký, tạo descriptor | Catalog | Không |
| Gọi method | `method.invoke(receiver, args...)` | Kết quả/exception/side effect | **Có** |
| Gọi constructor | `constructor.newInstance(...)` | Đối tượng mới | **Có** |
| Đọc/ghi field | `field.get(...)` / `field.set(...)` | Giá trị/thay đổi | Có thao tác thật trên trạng thái |

Ví dụ:

```java
Method action = Service.class.getDeclaredMethod("process", String.class);
// Mới tìm thấy thông tin về method.

Object result = action.invoke(service, "payload");
// Đã thực thi process(...) trên đối tượng service.
```

Đây là đoạn minh họa, giả định `Service`, `service`, kiểu trả về, method và quyền truy cập đều phù hợp. Với static method, receiver cho `Method.invoke` có thể là `null`.

**Lưu ý chính xác:** discovery không đồng nghĩa tuyệt đối “JVM không làm gì”. Nạp lớp hoặc đọc signature có thể khiến JVM tải/resolve class phụ thuộc hay phát sinh `LinkageError`; custom class loader có thể chạy logic của nó. Tuy nhiên, những API metadata trên **không tự gọi các method nghiệp vụ đang được liệt kê**. Nếu muốn tránh yêu cầu class initialization chỉ để đọc metadata, xem xét `Class.forName(name, false, loader)` thay vì `Class.forName(name)`.

## 2. Một kiến trúc tách biệt rõ ràng

```text
Đăng ký các kiểu / plugin được cho phép
                 |
                 v
            DISCOVER
   - Class<?> / getDeclaredMethods()
   - đọc annotation, tên, chữ ký
   - không invoke, không tạo instance để "thử"
                 |
                 v
             VALIDATE
   - allowlist, ID duy nhất, visibility
   - signature, mô tả, mức tác động
                 |
                 v
       IMMUTABLE REGISTRY
      /                  \
     v                    v
LIST/DESCRIBE           EXECUTE
Trả metadata       1. Resolve action ID
Không chạy code    2. Xác thực và phân quyền
                   3. Kiểm tra input / policy
                   4. Chuẩn bị receiver nếu cần
                   5. Invoke, audit, trả kết quả
```

**Registry** là ranh giới giữa danh mục và thực thi. Mỗi entry nên có ID ổn định, mô tả, input schema, output schema, yêu cầu quyền, mức ảnh hưởng (đọc/ghi/gửi ngoài), và một handle hoặc executor **nội bộ**.

Phía client chỉ nên nhận các trường mô tả an toàn, không nhận đối tượng `Method`, receiver hoặc quyền tự gọi Reflection. Khi request gửi `actionId="publish"`, máy chủ tìm entry đã đăng ký, kiểm tra quyền tại **thời điểm thực thi**, rồi mới dispatch.

Những điều cần tách ra:

1. **Discovery không phải authorization:** nhìn thấy action không có nghĩa được quyền chạy.
2. **Annotation không phải sự thật bảo mật:** `mutates=false` là khai báo do lập trình viên cung cấp; code implementation vẫn phải được đánh giá.
3. **Approval của thao tác có tác động** là trạng thái đáng tin cậy do server quản lý, không phải boolean mà caller tùy ý tự chọn.

## 3. Ví dụ Java 17+: xem metadata không kích hoạt static initializer

Chương trình sau giới thiệu một annotation `@ToolAction`, tìm các method hợp lệ trong một lớp, lưu registry và chỉ thực thi khi có lời gọi tường minh. `Actions` có static initializer để quan sát chính xác **lúc nào class được khởi tạo**.

Lưu thành tệp **`ReflectionToolBoundaryDemo.java`**:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;

public class ReflectionToolBoundaryDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface ToolAction {
        String name();
        boolean mutates() default false;
    }

    public static class Actions {
        static {
            System.out.println("Actions initialized");
        }

        @ToolAction(name = "preview")
        public static String preview(String payload) {
            return "Preview: " + payload;
        }

        @ToolAction(name = "publish", mutates = true)
        public static String publish(String payload) {
            System.out.println("Published: " + payload);
            return "OK";
        }

        public static String hidden(String payload) {
            throw new AssertionError("not registered");
        }
    }

    record ActionDescriptor(String name, boolean mutates, Method method) {}

    static Map<String, ActionDescriptor> discover(Class<?> type) {
        Map<String, ActionDescriptor> actions = new TreeMap<>();
        for (Method method : type.getDeclaredMethods()) {
            ToolAction config = method.getAnnotation(ToolAction.class);
            if (config == null) continue;

            if (!Modifier.isPublic(method.getModifiers())
                    || !Modifier.isStatic(method.getModifiers())
                    || method.getParameterCount() != 1
                    || method.getParameterTypes()[0] != String.class
                    || method.getReturnType() != String.class) {
                throw new IllegalArgumentException(
                        "Invalid action signature: " + method.getName());
            }

            ActionDescriptor previous = actions.putIfAbsent(config.name(),
                    new ActionDescriptor(config.name(), config.mutates(), method));
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate action: " + config.name());
            }
        }
        return Map.copyOf(actions);
    }

    static String execute(Map<String, ActionDescriptor> actions,
                          String name, String payload, boolean permitMutation)
            throws ReflectiveOperationException {
        ActionDescriptor descriptor = actions.get(name);
        if (descriptor == null) {
            throw new IllegalArgumentException("Unknown action: " + name);
        }
        if (descriptor.mutates() && !permitMutation) {
            throw new SecurityException("requires authorization");
        }

        try {
            return (String) descriptor.method().invoke(null, payload);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw e;
        }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        System.out.println("1. Discovery");
        Class<?> clazz = Actions.class;
        Map<String, ActionDescriptor> registry = discover(clazz);
        registry.keySet().stream().sorted().forEach(
                name -> System.out.println("found: " + name));

        System.out.println("2. Try without authorization");
        try {
            execute(registry, "publish", "Ada", false);
        } catch (SecurityException ex) {
            System.out.println("blocked: " + ex.getMessage());
        }

        System.out.println("3. Execute preview");
        System.out.println(execute(registry, "preview", "Ada", false));

        System.out.println("4. Execute permitted publish");
        System.out.println(execute(registry, "publish", "Ada", true));
    }
}
```

Lệnh biên dịch và chạy:

```bash
javac --release 17 -encoding UTF-8 ReflectionToolBoundaryDemo.java
java ReflectionToolBoundaryDemo
```

**Stdout thực tế đã kiểm tra với mã này:**

```text
1. Discovery
found: preview
found: publish
2. Try without authorization
blocked: requires authorization
3. Execute preview
Actions initialized
Preview: Ada
4. Execute permitted publish
Published: Ada
OK
```

**Giải thích từng mốc:**

1. `Actions.class` tạo tham chiếu kiểu, `getDeclaredMethods()` cùng `getAnnotation(...)` đọc mô tả, nên chưa có dòng `Actions initialized`.
2. Chỉ hai method có `@ToolAction` được đăng ký. `hidden(String)` dù public vẫn **không** trở thành action. `TreeMap` và lệnh `sorted()` làm thứ tự output ổn định vì thứ tự `getDeclaredMethods()` không được bảo đảm.
3. `publish` chưa được cho phép bị chặn **trước** `Method.invoke`. Class `Actions` vẫn chưa khởi tạo.
4. `preview` được gọi thật bằng `invoke(null, "Ada")`. Vì đây là static method, JVM khởi tạo `Actions` nếu chưa khởi tạo, in `Actions initialized` đúng một lần.
5. `publish` sau khi được cho phép in `Published: Ada`. Lần gọi này không chạy lại static initializer.
6. `Map.copyOf` làm registry trả về không thể bị sửa cấu trúc qua các API `Map` bình thường; việc kiểm soát quyền invoke vẫn thuộc executor. `ActionDescriptor` giữ `Method` để sử dụng nội bộ, không xuất ra phía client.

**Giới hạn chủ đích:** mã mẫu chỉ chấp nhận public static method `String f(String)`, nên không cần tạo receiver. Với instance method, executor phải xác định lifecycle target và dùng receiver đúng kiểu, không tạo instance ngay trong discovery. `permitMutation` ở đây chỉ dùng để chứng minh điểm chặn, **không phải mô hình xác thực**: hệ thống thật phải suy quyền từ context đã xác thực ở server, tuyệt đối không tin cờ do client gửi.

## 4. API nào nên gọi trong mỗi giai đoạn?

### Discovery: chỉ cần tra cứu và kiểm tra

- `Class.getDeclaredMethods()`: lấy cả method khai báo private/package-private/protected/public, nhưng không tự cấp quyền gọi chúng.
- `Class.getMethods()`: lấy các method public, gồm method kế thừa theo ngữ nghĩa API. Chọn một trong hai theo nhu cầu và điều kiện discovery.
- `Method.getName()`, `getModifiers()`, `getParameterTypes()`, `getReturnType()`: đọc chữ ký.
- `AnnotatedElement.getAnnotation(...)`: đọc annotation có thể truy cập tại runtime; annotation cần `@Retention(RUNTIME)` nếu muốn phát hiện ở runtime.
- `Class.getDeclaredConstructors()` / `getDeclaredFields()`: lấy mô tả constructor/field, chưa chạy chúng.

Không biến `getDeclaredMethods()` thành danh sách công cụ tự động. Một class có thể có method nội bộ, callback, getter gây tác động, method quản trị và method `Object`. Discovery cần **allowlist** và bộ kiểm tra kiểu/annotation.

### Execution: hành động có thể có side effect

- `Method.invoke(...)` chạy method; invocation của static method có thể yêu cầu initialization của lớp khai báo.
- `Constructor.newInstance(...)` thực thi constructor, tạo đối tượng và có thể chạy initialization trước đó.
- `Field.get(...)` / `Field.set(...)` truy cập **giá trị**, khác hẳn `getDeclaredField(...)` chỉ tìm metadata. Với static field, có thể kích hoạt initialization.
- `MethodHandle.invoke(...)` / `invokeExact(...)` gọi handle đã tìm, khác với lúc lookup handle.

Nếu muốn tải lớp chỉ để kiểm kê, `Class.forName(binaryName, false, loader)` tránh yêu cầu class initialization của kiểu đích trong lời gọi đó. Tuy nhiên, việc nạp lớp vẫn có thể thất bại vì classpath, class loader, modules hoặc dependency bị thiếu.

## 5. Đăng ký công cụ đúng cách

Một entry registry hữu ích thường có các trường:

| Trường | Mục đích |
|---|---|
| `actionId` | Tên/ID ổn định, không trùng |
| `description` | Giải thích hành động để người gọi hiểu |
| `inputSchema` | Kiểu dữ liệu, ràng buộc, giới hạn kích thước |
| `outputSchema` | Định dạng kết quả được công bố |
| `effect` | Đọc dữ liệu, ghi/sửa, xóa hoặc gửi ra ngoài |
| `requiredPermissions` | Quyền executor phải kiểm tra |
| `method/handler` nội bộ | Điểm thực thi sau khi được cho phép |

Nên:

1. Chỉ đọc những class/package/plugin đã đăng ký hoặc được phép. Nếu plugin đến từ nguồn không đáng tin cậy, việc chỉ dùng Reflection **không tạo sandbox**; cần cơ chế cô lập tương ứng.
2. Dùng annotation marker và kiểm tra schema/signature **trước khi** đưa vào registry. Không công bố method chỉ vì nó có `public`.
3. Từ chối `actionId` trùng thay vì âm thầm ghi đè method; sắp xếp danh mục theo ID để có kết quả ổn định.
4. Không dùng `setAccessible(true)` vô điều kiện để vượt encapsulation; Java module system có thể giới hạn truy cập. Ưu tiên entrypoint public có chủ đích.
5. Tạo snapshot registry bất biến sau khi validate. Nếu hỗ trợ hot reload, xuất bản registry mới bằng một bước thay thế có kiểm soát để request không nhìn thấy danh mục nửa chừng.
6. Khi xuất metadata thành JSON, không serialize raw `Method`, receiver, ClassLoader, secret hay thông tin nội bộ không cần thiết.

## 6. Thực thi đúng cách và xử lý lỗi

Một executor đáng tin cậy nên xử lý theo thứ tự:

1. **Resolve** action ID từ registry đã validate. Không lấy trực tiếp tên class/method từ input của client rồi gọi Reflection tùy ý.
2. **Authenticate và authorize** theo danh tính, vai trò, tenant, đối tượng, phạm vi dữ liệu và trạng thái hiện tại. Quyền phải được kiểm tra **mỗi lần gọi**.
3. **Validate/bind** input theo schema. Phân biệt input không hợp lệ với lỗi thực thi.
4. **Kiểm tra tác động:** thao tác ghi, xóa, gửi ra ngoài cần policy hoặc xác nhận thích hợp; thực thi có retry cần cơ chế idempotency phù hợp. Không coi method tên `preview` hay annotation `readOnly` là bảo đảm vô hại.
5. **Chuẩn bị receiver** cho instance method theo lifecycle service/container; không tạo đối tượng chỉ để xem metadata.
6. **Invoke** và bắt/log lỗi có kiểm soát; audit action ID, caller, quyết định cấp quyền, kết quả và correlation ID, tránh ghi secret trong log.

| Exception/vấn đề | Thường phát sinh ở đâu | Cách xử lý |
|---|---|---|
| `NoSuchMethodException` | Tìm sai tên hoặc signature trong discovery | Báo lỗi cấu hình/đăng ký |
| `ClassNotFoundException`, `LinkageError` | Nạp/resolve lớp hay dependency | Kiểm tra class loader, module path, dependency |
| `IllegalAccessException`, `InaccessibleObjectException` | Truy cập method bị giới hạn | Sửa visibility hoặc module boundary theo thiết kế |
| `IllegalArgumentException` | Bind receiver/tham số sai hoặc validate input | Kiểm tra schema và loại đối số |
| `InvocationTargetException` | Method được gọi ném exception | Đọc `getCause()`, phân loại lỗi nghiệp vụ |
| `ExceptionInInitializerError` | Static initialization bị lỗi | Xác định code khởi tạo, trạng thái class và lỗi gốc |
| Timeout, lặp lại request | Thực thi, đặc biệt thao tác có side effect | Giới hạn tài nguyên, audit, idempotency; cân nhắc cô lập |

**Chú ý:** deadline hoặc timeout không tự bảo đảm một method đang chạy sẽ dừng ngay hay rollback side effect. Không tự retry hành động có tác động nếu không có bảo đảm an toàn phù hợp.

## 7. Các tình huống thực tế

**CLI/command runner:** lệnh `list` và `help` chỉ dùng registry; lệnh `run <action>` mới đi qua executor. Người dùng có thể xem khả năng hệ thống mà chưa thay đổi trạng thái.

**Plugin host:** lúc khởi động đăng ký handler và kiểm tra metadata; lúc người dùng chọn plugin mới tạo instance/gọi method theo lifecycle. Constructor, đăng ký callback hoặc DI có thể có side effect, nên phải được quản lý ở đúng phase.

**Hệ thống tool-calling:** bước “describe tools” chỉ trả tên, mô tả, input schema và ràng buộc. Khi caller yêu cầu gọi một tool, backend xác thực, phân quyền và dispatch thông qua registry; bản thân việc gửi danh mục cho caller **không cấp quyền Reflection**.

**Framework kiểm thử:** discovery liệt kê method được đánh dấu `@Test`; execution mới xây fixture, chạy before-hook, gọi test và thu kết quả. Nếu khởi tạo test class trong discovery, ranh giới hai bước bị mờ và khó kiểm soát side effect.

## 8. Sai lầm cần tránh và câu hỏi tự kiểm tra

- **“Có `Method` là method đã chạy.”** Sai: `Method` chỉ chứa metadata; `invoke` là một đường thực thi.
- **“`getDeclaredMethods()` tự sắp xếp theo thứ tự mã nguồn.”** Sai: không nên phụ thuộc thứ tự này.
- **“Có annotation nghĩa là được phép gọi.”** Sai: annotation giúp đăng ký hoặc mô tả, còn phân quyền thuộc executor.
- **“Chỉ đọc tên class bằng `Class.forName(name)` thì static block chắc chưa chạy.”** Sai: overload một tham số yêu cầu class initialization.
- **“Không gọi nghiệp vụ trong discovery nghĩa là mọi thao tác bên dưới đều tuyệt đối không có tác động.”** Sai: class loading/linkage hay custom loader vẫn có hành vi riêng, nên cần xác định trust boundary.
- **“Tool chỉ đọc luôn vô hại.”** Chưa chắc: getter có thể thay state hoặc gọi I/O; phải xem implementation và policy.
- **“Báo `InvocationTargetException` cho người dùng là đủ.”** Chưa đủ: nên phân loại cause để biết lỗi thực tế từ code được gọi.

**Câu hỏi 1:** Vì sao code mẫu không in `Actions initialized` ngay trong discovery?

Vì thao tác `Actions.class` và đọc các `Method`/annotation không yêu cầu initialization của `Actions`. Static method `preview` được gọi bằng `invoke` mới kích hoạt lần khởi tạo đầu tiên.

**Câu hỏi 2:** Có cần gọi `Constructor.newInstance` để lấy tên các method instance không?

**Không.** Có thể đọc method metadata từ `Class<?>` mà chưa tạo instance. Việc tạo receiver là công việc khi thực thi.

**Câu hỏi 3:** Vì sao vẫn kiểm tra quyền sau khi đã xây registry an toàn?

Vì registry trả lời **“những gì có thể được cung cấp”**, còn quyền thực thi phụ thuộc caller, tài nguyên và thời điểm. Điều kiện đó có thể thay đổi giữa các lần gọi.

## Tài liệu tham khảo

- [Java SE 17 — Class](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): tra cứu class, method, field, constructor và `forName`.
- [Java SE 17 — Method](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html): metadata và `invoke`.
- [Java SE 17 — AccessibleObject](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html): quyền truy cập Reflection.
- Java Language Specification §12.4.1 và JVM Specification §5: quy tắc loading/linking/initialization.

**Ghi nhớ:** Discovery mô tả **có những thao tác nào**; execution quyết định **ai được phép gọi thao tác nào với input nào** và thực hiện nó có kiểm soát. Không biến việc quét metadata thành việc chạy code nghiệp vụ.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tooling-design-002">Định nghĩa chính sách field, property hoặc accessor cần dùng</a>

<details>
<summary>Xem chi tiết</summary>

# Định nghĩa chính sách field, property hoặc accessor cần dùng trong công cụ Java Reflection

## 1. Vì sao phải có chính sách thay vì quét mọi member?

Khi xây một công cụ dùng reflection (serializer, object inspector, mapper, validator, form generator, exporter...), cùng một giá trị có thể xuất hiện dưới nhiều hình thức:

- **Field**: vùng lưu trữ như `private String name` hoặc `public int count`, được mô tả bởi `java.lang.reflect.Field`.
- **JavaBeans property**: thuộc tính logic xác định qua cặp phương thức theo quy ước, ví dụ `getName()` / `setName(String)`, hoặc `isActive()` cho boolean; có thể **không có field cùng tên**. Metadata thường được đọc qua `java.beans.Introspector` và `PropertyDescriptor`.
- **Record component**: thành phần khai báo trong header của record, ví dụ `record User(String name) {}`, có accessor `name()` và `RecordComponent` riêng.
- **Accessor tùy biến**: phương thức có ngữ nghĩa lấy dữ liệu như `displayName()` hoặc `fetchBalance()`; không phải mọi method không tham số đều nên trở thành property.

Nếu công cụ vừa quét field, vừa quét getter, nó có thể xuất `name` hai lần; nếu ép gọi tất cả getter, có thể làm phát sinh truy vấn cơ sở dữ liệu, lỗi nghiệp vụ hoặc làm lộ thông tin. Nếu tùy tiện dùng `setAccessible(true)`, công cụ có thể vi phạm ranh giới module hoặc đọc dữ liệu không được phép.

**Chính sách truy cập (access policy)** là quy tắc xác định rõ *nguồn metadata*, *đối tượng nào được chọn*, *cách đặt tên*, *quyền đọc/ghi*, *ưu tiên khi trùng*, *thứ tự đầu ra*, *xử lý lỗi* và *ranh giới bảo mật*. Chính sách cần được quyết định trước khi quét/đọc object; không để hành vi vô tình phụ thuộc thứ tự trả về của `getDeclaredFields()` hay `getDeclaredMethods()`.

### Một số tình huống

| Bối cảnh | Chính sách có thể phù hợp | Điểm cần kiểm soát |
| --- | --- | --- |
| Inspector đơn giản trên DTO có public fields | Public instance fields | Không suy đoán private fields cũng được phép đọc |
| Framework dùng hợp đồng JavaBeans | Getter/setter hợp lệ từ `Introspector` | Loại getter lỗi, getter có tác dụng phụ; kiểm tra quyền |
| Export dữ liệu record | Record components theo header | Dùng `getRecordComponents()` và accessor `name()`, không giả định tên `getName()` |
| API serializer dữ liệu nhạy cảm | Explicit allowlist + annotation/config | Chỉ xuất trường được chấp thuận; bảo vệ secret |
| Framework có domain object hỗn hợp | Chính sách rõ ràng theo loại hoặc theo annotation | Tránh tự động gộp/ghi đè field và property trùng tên |

## 2. Phân biệt đúng field, property và accessor

### Field: lưu trữ trực tiếp

Với:

```java
class Account {
    private String name;
    public String alias;
}
```

`Account.class.getDeclaredFields()` tìm field **khai báo ngay trên lớp** (bao gồm private), còn `Account.class.getFields()` tìm public fields khả dụng, gồm các public fields được kế thừa. Hai API này có phạm vi khác nhau; `getDeclaredFields()` không tự lấy field kế thừa.

Phát hiện field bằng metadata không có nghĩa chương trình có quyền đọc giá trị. `Field.get(target)` cần receiver hợp lệ và quyền truy cập thích hợp. Với static field, đối số target được bỏ qua; với instance field, target phải là instance tương thích. Java reflection cũng có `Field.getInt`, `getBoolean`... cho primitive.

**Field không tương đương property**: một field có thể là dữ liệu nội bộ; một property có thể tính toán giá trị hoặc ủy quyền vào hệ thống khác. Không tự động serialize mọi private field chỉ vì công cụ đủ quyền kỹ thuật để làm vậy.

### JavaBeans property: hợp đồng phương thức

`Introspector.getBeanInfo(type, Object.class).getPropertyDescriptors()` trả metadata property theo JavaBeans convention, bỏ qua phần đến từ `Object` theo mức stop class. Một `PropertyDescriptor` có thể cung cấp:

- `getName()`: tên logic, chẳng hạn `active`.
- `getReadMethod()`: getter nếu có.
- `getWriteMethod()`: setter nếu có.
- `getPropertyType()`: kiểu property khi xác định được.

Ví dụ `boolean isActive()` theo quy ước JavaBeans thường tạo property `active`; `getBalance()` có thể là property chỉ đọc nếu không có setter. Một setter-only property có `getReadMethod() == null` và **không thể đọc** chỉ dựa trên descriptor đó.

`Introspector.decapitalize` có quy tắc tên riêng, ví dụ `getURL()` thường ánh xạ property `URL` chứ không phải `uRL`. Đừng tự chuyển tên bằng cách hạ chữ cái đầu trong mọi trường hợp. Các JavaBeans descriptor đến từ inheritance và `BeanInfo` tùy biến có thể khác suy luận đơn giản từ tên method.

### Record component: hợp đồng header record

Ví dụ:

```java
public record Receipt(String code, int total) {}
```

`Receipt.class.isRecord()` là `true`. `Receipt.class.getRecordComponents()` trả các `RecordComponent` theo thứ tự khai báo: `code`, `total`. Accessor là `code()`, `total()`, **không phải** `getCode()` hay `getTotal()`. `getRecordComponents()` trả `null` với class không phải record, vì vậy phải kiểm tra `isRecord()` trước.

Record components không tự đồng nhất với JavaBeans properties trong mọi công cụ: JavaBeans introspection truyền thống dựa vào `getX()/isX()`, trong khi record accessor được đặt theo tên thành phần. Nếu công cụ phải hỗ trợ record, hãy quy định riêng nhánh record.

### Accessor tùy biến: chỉ nhận khi có hợp đồng

Một `public String displayName()` không có tham số **chưa chắc** là property. Nếu công cụ chọn các accessor không theo JavaBeans, cần có điều kiện minh bạch như annotation `@Expose`, metadata do người dùng khai báo hoặc interface chuẩn của ứng dụng. Không quét tất cả zero-argument methods rồi gọi thử: có thể gặp `close()`, `reset()`, `delete()` hoặc thao tác tốn kém, mặc dù một số có kiểu trả về phù hợp.

## 3. Những quyết định chính sách cần chốt

Trước khi viết bộ quét, ghi rõ câu trả lời cho các câu hỏi sau:

| Quyết định | Các lựa chọn thường gặp | Khuyến nghị triển khai |
| --- | --- | --- |
| Nguồn thành viên | `PUBLIC_FIELD`, `BEAN_PROPERTY`, `RECORD_COMPONENT`, `ANNOTATED_ACCESSOR` hoặc phối hợp | Chọn một mặc định xác định; có override có chủ đích |
| Thành viên được chọn | Public-only, allowlist, annotation, visibility do module cho phép | Public + allowlist mặc định cho dữ liệu xuất ra ngoài |
| Instance/static | Chỉ instance hay gồm cả static | Thông thường loại static vì không thuộc trạng thái một object |
| Synthetic/bridge | Có tính thành viên do compiler tạo không | Loại synthetic field/method và bridge method nếu quét method thô |
| Mutable/read-only | Đọc, ghi hay cả hai | Phân tách `readable` và `writable`; không suy từ tồn tại field |
| Kế thừa | Lấy member inherited hay chỉ declared | Ghi rõ từng API; xử lý override/hiding |
| Tên public | Field name, bean property name, record component name hoặc annotation alias | Chuẩn hóa tên đầu ra và kiểm tra trùng |
| Thứ tự | Theo header record, theo tên, theo annotation `order` | Quy định rõ, không dựa vào thứ tự reflection trả về |
| Null & lỗi getter | Giữ null, bỏ qua, ghi lỗi theo đường dẫn | Không im lặng đổi null thành chuỗi `"null"` |
| Trùng tên | Ưu tiên nào? lỗi? | Mặc định báo ambiguity; chỉ ưu tiên khi policy công bố |
| Quyền truy cập | Public, `privateLookupIn`, `setAccessible` có điều kiện | Tôn trọng module boundary, hạn chế deep reflection |
| Hiệu năng | Introspect mỗi lần hoặc cache metadata | Cache descriptor theo `Class<?>`; không cache giá trị object tùy tiện |

**Policy phải dựa vào mục tiêu đầu ra:** serializer phục vụ API public thường có ranh giới khác debugger nội bộ; policy đọc (export) cũng khác policy ghi (binding/deserialization). Tránh một cờ `includeAll=true` gom tất cả trường hợp với nhiều hệ quả khó dự đoán.

## 4. Mô hình policy và quy trình duyệt đề xuất

Một bản thiết kế có thể gồm ba lớp:

1. **Discovery**: từ `Class<?>` lấy danh sách field, property, record component hoặc accessor đã đăng ký theo policy.
2. **Selection & normalization**: áp dụng allowlist/denylist/annotation, loại static/synthetic, xác định tên logic, visibility, nullability, readable/writable, xử lý trùng và sắp xếp ổn định.
3. **Execution**: chỉ khi được yêu cầu, gọi `Field.get` hoặc getter/record accessor; ghi kết quả, phân loại exception và không để lỗi một property làm hỏng toàn bộ dữ liệu nếu hợp đồng cho phép partial result.

Ví dụ descriptor trung gian (fragment, chưa phải chương trình đầy đủ):

```java
enum SourceKind { FIELD, BEAN, RECORD, EXPLICIT_ACCESSOR }

record MemberDescriptor(
        String externalName,
        Class<?> valueType,
        SourceKind sourceKind,
        boolean readable,
        boolean writable
) {}
```

Trong hệ thống thật, descriptor nên có strategy thực thi có kiểm soát, ví dụ hàm đọc/ghi hoặc tham chiếu `Field`/`Method`/`MethodHandle`, và metadata về annotation/ownership. **Đừng tự chọn cách đọc bằng chuỗi tên** sau khi đã nhận descriptor: tên có thể bị alias, override hoặc trùng.

Quy tắc đề xuất cho `AUTO`: nếu target là record thì dùng record components; nếu là bean do ứng dụng định nghĩa thì dùng JavaBeans; nếu muốn public fields thì chọn rõ `PUBLIC_FIELD`. Đây chỉ là **một chính sách do công cụ tự định nghĩa**, không phải hành vi tự động của JVM hay reflection API. Muốn phối hợp nhiều nguồn, thiết kế precedence riêng, ví dụ explicit annotation → record → JavaBeans → public field, và báo trùng khi hai nguồn không cùng ngữ nghĩa.

Luồng quyết định:

```text
target + policy
    |
    +-- record && policy=AUTO ? RECORD_COMPONENT
    |
    +-- policy=AUTO ? BEAN_PROPERTY
    |
    +-- policy explicit ? nguồn tương ứng
    |
    v
metadata -> filter -> normalize names -> collision check
    -> stable order -> access check -> execute accessor -> result
```

## 5. Ví dụ Java 17+ hoàn chỉnh: cùng object, ba cách đọc

Chương trình sau cố ý hỗ trợ **ba chế độ tách biệt**:

- `PUBLIC_FIELD`: đọc public, non-static, non-synthetic fields.
- `BEAN_PROPERTY`: đọc getter được phát hiện bằng JavaBeans `Introspector`; bỏ property chỉ ghi, chỉ đọc `getReadMethod() != null`.
- `RECORD_COMPONENT`: đọc chính các component của record.
- `AUTO` là chính sách do chương trình chọn: record dùng record component; object khác dùng JavaBeans. Nó không cố hợp nhất hai nguồn.

Lưu thành **`AccessPolicyDemo.java`**:

```java
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AccessPolicyDemo {
    enum Strategy { PUBLIC_FIELD, BEAN_PROPERTY, RECORD_COMPONENT, AUTO }

    public static final class Account {
        public String alias = "alpha";
        private int credits = 7;

        public int getCredits() { return credits; }
        public void setCredits(int credits) { this.credits = credits; }
        public boolean isActive() { return true; }
    }

    public record Receipt(String code, int total) {}

    record Entry(String name, Object value) {}

    static List<Entry> read(Object target, Strategy requested)
            throws ReflectiveOperationException, IntrospectionException {
        Objects.requireNonNull(target, "target");
        Class<?> type = target.getClass();

        Strategy mode = requested == Strategy.AUTO
                ? (type.isRecord()
                        ? Strategy.RECORD_COMPONENT
                        : Strategy.BEAN_PROPERTY)
                : requested;

        List<Entry> entries = new ArrayList<>();
        switch (mode) {
            case PUBLIC_FIELD -> {
                for (Field field : type.getFields()) {
                    if (field.isSynthetic()
                            || Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    entries.add(new Entry(field.getName(), field.get(target)));
                }
            }
            case BEAN_PROPERTY -> {
                for (PropertyDescriptor p :
                        Introspector.getBeanInfo(type, Object.class)
                                .getPropertyDescriptors()) {
                    Method getter = p.getReadMethod();
                    if (getter == null
                            || Modifier.isStatic(getter.getModifiers())) {
                        continue;
                    }
                    entries.add(new Entry(p.getName(), getter.invoke(target)));
                }
            }
            case RECORD_COMPONENT -> {
                if (!type.isRecord()) {
                    throw new IllegalArgumentException(
                            "RECORD_COMPONENT requires a record");
                }
                for (RecordComponent component : type.getRecordComponents()) {
                    entries.add(new Entry(
                            component.getName(),
                            component.getAccessor().invoke(target)));
                }
            }
            default -> throw new IllegalStateException(
                    "Unsupported mode: " + mode);
        }

        entries.sort(Comparator.comparing(Entry::name));
        return entries;
    }

    static String describe(Object target, Strategy strategy)
            throws ReflectiveOperationException, IntrospectionException {
        return read(target, strategy).stream()
                .map(entry -> entry.name() + "=" + entry.value())
                .collect(Collectors.joining(", "));
    }

    public static void main(String[] args) throws Exception {
        Account account = new Account();
        Receipt receipt = new Receipt("R1", 45);

        System.out.println("FIELD(Account): "
                + describe(account, Strategy.PUBLIC_FIELD));
        System.out.println("BEAN(Account): "
                + describe(account, Strategy.BEAN_PROPERTY));
        System.out.println("AUTO(Account): "
                + describe(account, Strategy.AUTO));
        System.out.println("RECORD(Receipt): "
                + describe(receipt, Strategy.RECORD_COMPONENT));
        System.out.println("AUTO(Receipt): "
                + describe(receipt, Strategy.AUTO));
    }
}
```

Biên dịch và chạy với JDK 17+:

```bash
javac --release 17 AccessPolicyDemo.java
java AccessPolicyDemo
```

Stdout chính xác:

```text
FIELD(Account): alias=alpha
BEAN(Account): active=true, credits=7
AUTO(Account): active=true, credits=7
RECORD(Receipt): code=R1, total=45
AUTO(Receipt): code=R1, total=45
```

Giải thích kết quả:

- `Account.alias` là public field nên được chọn bởi `PUBLIC_FIELD`. `credits` là private field, không xuất hiện trong danh sách `getFields()`.
- JavaBeans nhìn thấy `getCredits()` và `isActive()`, nên đọc `credits=7` và `active=true`. Nó không suy ra một JavaBeans property từ riêng field `alias`.
- `Receipt` là record: `getRecordComponents()` nhận `code` và `total`; accessor tương ứng là `code()` và `total()`. Chính sách `AUTO` dùng nhánh record.
- Thứ tự đầu ra ổn định vì chương trình **chủ động sort theo tên**. Thứ tự xuất hiện trong phản hồi của các API reflection nói chung không nên được xem là hợp đồng sắp xếp.
- Ví dụ dùng các nested type thuộc chương trình kiểm soát, không cần `setAccessible(true)`. Đối tượng chứa bên thứ ba có thể đặt ra yêu cầu quyền/module khác.

Đây là ví dụ **read-only**, chưa xử lý alias, annotation, deep reflection, duplicate-name hay lỗi từng getter. Trong công cụ thực tế, các quy tắc bên dưới cần được cấu hình thêm trước khi đưa vào xử lý dữ liệu người dùng.

## 6. Chính sách lọc, đặt tên và giải quyết xung đột

### 6.1. Whitelist có chủ đích

Không nên mặc định xuất tất cả field/getter tìm được. Ví dụ entity có `getPasswordHash()`, `getApiToken()` hoặc `getInternalAuditTrail()`. Dù method là public, điều đó không tự làm thông tin thích hợp cho API hay file xuất.

Một chính sách chắc chắn hơn:

- Có allowlist tên property hoặc annotation riêng của công cụ (ví dụ `@Exportable`) được xem xét trong phạm vi khai báo và nơi đặt annotation hợp lệ.
- Có denylist cho dữ liệu nhạy cảm như secret, nhưng **allowlist mới là cơ chế kiểm soát chính** khi dữ liệu đi ra ranh giới tin cậy.
- Nếu một property không thuộc allowlist, bỏ qua **trước khi gọi getter**, tránh chạy code không cần thiết.
- Định nghĩa điều kiện annotation nằm trên field, getter, record component hay parameter, và việc kế thừa/override được xử lý ra sao. Không giả định annotation trên field tự chuyển sang getter hoặc ngược lại.

Ví dụ pseudocode cho chính sách đọc (không phải API có sẵn trong JDK):

```java
// Minh họa thuật toán; selected và policy do công cụ của bạn định nghĩa.
for (MemberDescriptor member : selected) {
    if (!policy.isAllowed(member.externalName())) continue;
    if (!member.readable()) continue;
    // Chỉ lúc này mới thực thi accessor và đưa vào kết quả.
}
```

### 6.2. Tên trùng và ưu tiên nguồn

Giả sử có `private String name`, `getName()` và record component `name`. Nếu quét nhiều nguồn, ba mục này có thể đại diện cùng một thuộc tính logic hoặc có ngữ nghĩa khác nhau (getter trả giá trị tính toán). Nếu gộp tùy tiện bằng `Map.put`, thứ tự duyệt quyết định giá trị nào thắng, dễ gây lỗi khó tái hiện.

Chốt một trong hai hợp đồng:

1. **Chỉ một nguồn mỗi class** theo policy, như ví dụ `AccessPolicyDemo`: tránh phần lớn xung đột.
2. **Kết hợp nhiều nguồn theo precedence rõ ràng**, ví dụ annotation khai báo cụ thể có quyền ưu tiên, rồi record component, JavaBeans property, public field. Nếu cùng cấp có hai ứng viên khác nhau hoặc alias đụng nhau, báo `AMBIGUOUS_PROPERTY` với tên, declaring class và source.

Không dựa vào `getDeclaredMethods()` hay `getDeclaredFields()` để xác định precedence. Kiểm tra field hiding giữa cha/con, getter override và inheritance để tránh chọn nhầm member cũ. Nếu cần hỗ trợ alias, kiểm tra trùng **sau khi đã áp dụng alias**, không chỉ trên Java identifier ban đầu.

### 6.3. Thứ tự cố định

Serializer hoặc công cụ sinh schema có thể cần kết quả ổn định để snapshot test, hash và diff không thay đổi ngẫu nhiên. Quy định thứ tự: alphabet theo external name; hoặc theo `order` explicit; hoặc thứ tự record components trong header khi cần tương thích schema. Ví dụ đang sort alphabet cho tất cả để nhất quán.

## 7. Read policy khác write policy

Một property đọc được chưa chắc ghi được:

| Nguồn | Đọc | Ghi | Ghi chú |
| --- | --- | --- | --- |
| Public non-final field | Thường có thể bằng `Field.get` | Có thể bằng `Field.set` nếu quyền và kiểu hợp lệ | Không suy ra atomic/thread safety |
| Private field | Tùy quyền reflection/module | Tùy quyền, có thể bị giới hạn | Không tự deep-reflect vì tiện |
| JavaBeans getter-only | Có | Không có setter theo descriptor | Getter có thể tính toán/có side effect |
| JavaBeans setter-only | Không có getter | Có setter phù hợp | Không đoán giá trị hiện tại |
| Record component | Accessor có | Không thể ghi như mutable property | Tạo record mới qua constructor khi cần thay đổi giá trị |
| Static field | Không thuộc state instance theo policy thông thường | Cần chính sách riêng | Nên loại khỏi serializer instance |

Với JavaBeans, kiểm tra `getReadMethod()` và `getWriteMethod()` riêng. Với field, xét `Modifier.isFinal` và quyền truy cập. Record có các field thành phần `private final`, nhưng quan trọng hơn, public API record thể hiện một cấu trúc giá trị không hỗ trợ sửa component sau khi tạo; mutation của object được tham chiếu bên trong vẫn có thể xảy ra (shallow immutability).

**Việc validate type** phải dựa vào hợp đồng của API dùng để gán: `Field.set` và `Method.invoke` có ràng buộc kiểu/primitive conversion riêng; không tự chuyển chuỗi `"42"` thành `int` nếu không có conversion policy đã định nghĩa. Khi binding dữ liệu người dùng, validation, coercion và access policy là các quyết định tách biệt.

## 8. Quyền truy cập và lỗi runtime

### Giới hạn của Java Platform Module System

Tìm được `getDeclaredField("secret")` không chứng tỏ có thể đọc field đó. `setAccessible(true)` không luôn hợp lệ dưới strong encapsulation; có thể ném `InaccessibleObjectException`. `trySetAccessible()` có thể trả false khi không được phép. `MethodHandles.privateLookupIn` cũng yêu cầu caller có quyền và điều kiện module phù hợp; nó không phải đường vòng qua Java access control.

Cho công cụ dùng trong sản phẩm, ưu tiên public contract/annotation rõ ràng, mở package chính xác nếu bắt buộc và được chủ sở hữu module cho phép. Không tự đặt `--add-opens ALL-UNNAMED` tràn lan chỉ để vượt quyền đọc dữ liệu.

### Xử lý lỗi getter, field và record accessor

Một getter có thể ném ngoại lệ do logic nghiệp vụ, side effect hoặc I/O. `Method.invoke` bọc ngoại lệ từ code đích trong `InvocationTargetException`; còn `Field.get` có thể ném `IllegalAccessException` hay `IllegalArgumentException` khi receiver không hợp lệ. `RecordComponent.getAccessor().invoke` cũng là gọi một `Method`, nên cần xử lý ngoại lệ tương tự. Đừng nuốt exception rồi ghi một giá trị `null` khó phân biệt.

Ví dụ thiết kế result (fragment):

```java
record ReadIssue(
        String propertyName,
        String sourceKind,
        String errorCode,
        String message
) {}
```

Có thể có các chính sách lỗi như fail-fast cho hệ thống giao dịch, hoặc collect-errors khi làm object inspector nội bộ. Nếu trả partial result, phải đánh dấu rõ mục nào lỗi và không được xem nó là giá trị `null` thực sự. Cần tránh log full value/stack trace ra API response vì getter có thể chạm tới dữ liệu cá nhân hoặc secret.

## 9. Hiệu năng, ổn định và khả năng mở rộng

Phần tốn công của một công cụ reflection thường là discovery, chuẩn hóa và quyết định quyền, nên có thể cache **metadata descriptors** theo `Class<?>` và policy version; không cache giá trị của từng instance dưới cùng key vì giá trị thay đổi theo object.

- **Cache theo class/policy**: `ClassValue<T>` là lựa chọn đáng cân nhắc khi muốn cache gắn với lifecycle của `Class`; đối với `Introspector`, JDK đã có cơ chế cache `BeanInfo` riêng. Nếu công cụ dùng classloader động hoặc hot reload, đánh giá nguy cơ giữ tham chiếu classloader.
- **Cache decision**: lưu các member đã lọc và thứ tự chuẩn hóa để tránh lặp lại thao tác trên mỗi object; invalidation khi policy/annotation mapping thay đổi.
- **MethodHandle**: có thể cân nhắc khi có lời gọi lặp lại đủ nhiều và biết chữ ký; việc chuyển từ `Method` sang handle không thay thế discovery policy. Không khẳng định `MethodHandle` luôn nhanh hơn `Method.invoke`, vì JDK, warm-up và workload ảnh hưởng kết quả.
- **Thread-safety**: cache metadata bất biến và cấu trúc concurrent phù hợp; không lẫn cache với đồng bộ giá trị mutable của bean.
- **Cycle detection**: nếu serializer duyệt object graph, phải tách chính sách chọn property khỏi cơ chế xử lý tham chiếu vòng và giới hạn độ sâu. Không phải property nào cũng được phép recursively expand.

Tạo test dựa trên các trường hợp như getter-only, setter-only, record, inherited getter, field hiding, private field, static/synthetic, annotation allowlist, duplicate alias, getter throwing, JPMS, classloader. Thứ tự ổn định làm kết quả dễ đối chiếu giữa các phiên chạy.

## 10. Checklist quyết định và lỗi thường gặp

Trước khi chốt policy của công cụ, trả lời:

1. Công cụ này phục vụ mục tiêu gì: serialization, binding, diagnostics, schema generation hay test introspection?
2. Nguồn mặc định là public field, JavaBeans property, record component hay accessor đăng ký riêng?
3. Ai được phép tham gia: mọi public member hay chỉ allowlist được duyệt?
4. Có cho private access không, và module chủ sở hữu có cho phép không?
5. Static, synthetic, bridge, transient, final, inherited được xử lý ra sao?
6. Getter-only/setter-only, alias, null, duplicate name và exception được biểu diễn thế nào?
7. Kết quả có yêu cầu thứ tự ổn định và backward compatibility của schema không?
8. Có cache metadata theo class/policy và xử lý unload/reload classloader không?

Các sai lầm cần tránh:

- **“Có field là có property”**: JavaBeans có thể không có field; getter có thể tính toán.
- **“Record accessor luôn tên `getX`”**: thực tế accessor mặc định theo component name, như `code()`.
- **“Method public nào không tham số cũng an toàn để gọi”**: getter có thể side effect, và nhiều method không phải accessor.
- **“Quét `getDeclaredFields` là đã lấy toàn bộ inheritance”**: nó chỉ lấy field khai báo trực tiếp.
- **“`setAccessible(true)` luôn mở khóa được”**: còn access checks và module boundaries.
- **“Dùng cả field lẫn getter luôn đầy đủ hơn”**: có thể sinh duplicate, dữ liệu nội bộ và giá trị khác nhau; cần precedence và policy.
- **“Có getter thì chắc chắn ghi được”**: write contract là chuyện riêng; record component về bản chất không phải mutable property.
- **“Đầu ra reflection có thứ tự cố định”**: phải định nghĩa/sort rõ nếu cần ổn định.

Tự kiểm tra:

- Một lớp có `private int age` cùng `getAge()`: `PUBLIC_FIELD` và `BEAN_PROPERTY` cùng chọn `age` không? **Không**; public-field policy không chọn private field, bean policy có thể chọn getter.
- `record Point(int x, int y)`, chỉ dùng `Introspector` mặc định liệu có bảo đảm lấy được `x`/`y`? **Không**; nên dùng `getRecordComponents()` khi chủ đích hỗ trợ record.
- Getter `getSecret()` là public, liệu nên tự động xuất secret? **Không**; API public không tự đồng nghĩa được xuất ra bên ngoài.
- Khi hai accessor cùng alias `userId`, liệu chọn phần tử duyệt sau có an toàn? **Không**; cần deterministic precedence hoặc báo collision.
- Một inspector cần hiển thị 100 bean nhiều lần: tối ưu bước nào trước? **Cache metadata/selection policy**, rồi đo hiệu năng execution nếu thực sự cần.

Tài liệu gốc: [`Class`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html), [`Field`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html), [`Introspector`](https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/java/beans/Introspector.html), [`PropertyDescriptor`](https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/java/beans/PropertyDescriptor.html), [`RecordComponent`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/RecordComponent.html) và [JavaBeans Specification](https://www.oracle.com/java/technologies/javase/javabeans-spec.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tooling-design-003">Không mặc định mọi field đều là dữ liệu nghiệp vụ</a>

<details>
<summary>Xem chi tiết</summary>

# Không mặc định mọi field đều là dữ liệu nghiệp vụ khi thiết kế công cụ Java Reflection

## 1. Vấn đề: nhìn thấy field không có nghĩa field thuộc mô hình nghiệp vụ

`type.getDeclaredFields()` chỉ trả **khai báo vật lý**: trong `Order`, các field `orderId`, `totalCents`, `internalCache`, `traceContext`, `LOG` có thể cùng được phát hiện, nhưng không cùng thuộc một schema nghiệp vụ. Exporter cần `orderId` và `totalCents`; công cụ chẩn đoán có thể cần `traceContext`; cả hai đều phải xét quyền dữ liệu trước khi đọc giá trị.

Vì vậy phải tách **khám phá metadata**, **chọn field theo hợp đồng nghiệp vụ** và **truy cập giá trị thật**. `private`, `static`, `transient`, `synthetic` giúp phân loại cấu trúc, nhưng không tự trả lời một trường có được công bố cho người dùng hay không. Bài này tập trung vào ranh giới schema/allowlist và bảo vệ thông tin khi một công cụ dùng `Field`.

## 2. Các loại field và ý nghĩa đối với công cụ

| Dấu hiệu quan sát được | Có mặc nhiên là dữ liệu nghiệp vụ? | Cách quyết định |
| --- | --- | --- |
| `private String orderId` | **Không thể quyết định từ `private`** | Có thể là dữ liệu nghiệp vụ; dùng hợp đồng/annotation hoặc schema đã định. |
| `static final String VERSION` | Thường **không** là trạng thái của từng object | Thông thường loại khỏi projection của instance; nhưng có thể thuộc metadata chẩn đoán. |
| `static` mutable field | Thường không | Đây là trạng thái cấp class, không phải field riêng của từng instance; đọc có thể khởi tạo class. |
| `transient` | **Không tự động** loại khỏi mọi mục đích | `transient` ảnh hưởng cơ chế Java serialization mặc định, không định nghĩa hợp đồng nghiệp vụ, JSON hay persistence. |
| `final` | **Không tự động** loại | Nhiều giá trị nghiệp vụ bất biến dùng `final`, bao gồm field nền của record. |
| `synthetic` (`Field.isSynthetic()`) | Thường loại khỏi projection do ứng dụng kiểm soát | Có thể là hiện vật do compiler sinh, không nên dựa vào tên sinh ra cụ thể. |
| `enum` constant (`Field.isEnumConstant()`) | Không tự coi như field dữ liệu của mỗi đối tượng enum | Tùy công cụ muốn liệt kê hằng enum hay xuất state. |
| Field chứa cache, lazy state, lock, metric, logger, dependency | Không mặc định | Xem hợp đồng domain, role của công cụ và khả năng lộ nội bộ. |
| Field mang annotation riêng như `@BusinessData` | Có thể chọn theo **chính sách đã định** | Annotation có ý nghĩa khi công cụ định nghĩa, đọc và thực thi chính sách đó. |
| Field kế thừa từ superclass | Có thể | `getDeclaredFields()` trên subclass không tự bao gồm nó; phải duyệt hierarchy có chủ ý. |
| Field có dữ liệu nhạy cảm | Có thể **vẫn là** dữ liệu nghiệp vụ | Quyết định redaction/quyền truy cập trước khi hiển thị; không đánh đồng “nghiệp vụ” với “được phép tiết lộ”. |

**Hai lưu ý quan trọng:**

- `transient` là tín hiệu về **Java Object Serialization**, không phải bộ lọc phổ quát cho Jackson, JPA, CSV, JSON hay audit; các công nghệ đó có quy tắc và annotation riêng.
- Không nên dùng `private`, `final`, tên bắt đầu bằng dấu gạch dưới, hay tên có chứa `cache` làm tiêu chí duy nhất. Những heuristic này dễ bỏ sót dữ liệu cần thiết hoặc lộ thông tin không nên xuất.

## 3. Reflection cho ta metadata gì và không cho ta điều gì?

### 3.1. Chọn API quét đúng phạm vi

- `Class.getDeclaredFields()`: các field khai báo **trực tiếp** trên class/interface; gồm private và static, không gom field kế thừa.
- `Class.getFields()`: các field **public** có thể truy cập theo quy tắc API, gồm public field kế thừa. Dùng nó không phải cách chắc chắn để chọn business data.
- `Field.getName()`, `getType()`, `getGenericType()`, `getModifiers()`, `isSynthetic()`, `isEnumConstant()` và `getDeclaredAnnotations()`: cung cấp metadata để phân loại.
- `Modifier.isStatic(field.getModifiers())`, `Modifier.isTransient(...)`, `Modifier.isFinal(...)`: kiểm tra modifier theo quy tắc Java.
- `Class.getSuperclass()`: duyệt superclass nếu chính sách muốn chọn field kế thừa.
- `Class.isRecord()`, `Class.getRecordComponents()`: nếu xử lý record như dữ liệu mang tính cấu trúc, record components thường là điểm xuất phát phù hợp hơn quét field tùy ý.

Java **không bảo đảm thứ tự** các field trả về bởi `getDeclaredFields()`. Nếu output dùng cho snapshot, file xuất, hash hoặc kiểm thử, phải định nghĩa thứ tự riêng (ví dụ sắp xếp theo tên kèm declaring class hoặc thứ tự schema).

### 3.2. Từ cấu trúc tới ý nghĩa nghiệp vụ cần một hợp đồng

`Field.getType() == String.class` chỉ cho biết **kiểu khai báo** của field, không cho biết chuỗi đó là mã đơn hàng, access token hay metadata tạm. Tương tự, `Field.isSynthetic() == false` chỉ nói field không được đánh dấu synthetic; nó vẫn có thể thuộc hạ tầng.

Các cách làm rõ hợp đồng:

1. **Allowlist schema**: liệt kê tên và kiểu được xuất; thay đổi có review và versioning.
2. **Annotation opt-in**: dùng `@BusinessData` hoặc annotation của chính công cụ; mặc định field không được đánh dấu sẽ bị bỏ.
3. **DTO/record projection**: chuyển domain object thành kiểu công khai riêng; thường dễ kiểm soát hơn đọc trực tiếp field.
4. **Metadata từ framework/domain**: khi xử lý entity, bean hoặc serialization, đọc metadata/hợp đồng thực tế của framework đó thay vì tự suy luận chỉ từ JVM field.

Nhiều công cụ nên dùng **kết hợp**: chọn field theo schema/annotation, xác thực access policy, kiểm tra nhạy cảm, rồi mới đọc giá trị và chuyển đổi.

## 4. Quy trình chọn field có thể bảo vệ và kiểm chứng

Đề xuất một pipeline khi viết field inspector hoặc exporter:

1. **Chốt mục đích đầu ra**: xuất dữ liệu nghiệp vụ, debug, audit, migration hay validation? Mỗi mục đích có chính sách riêng.
2. **Xác định phạm vi kiểu**: kiểu gốc, superclass nào cần xét, interface/record component/property hay field trực tiếp? Tránh duyệt vô hạn vào thư viện/JDK.
3. **Lọc metadata trước**: `isSynthetic`, `static`, modifier, annotation, kiểu, declaring class, các điều kiện loại trừ rõ ràng. Không gọi `Field.get` trong pha lọc metadata.
4. **Áp dụng allowlist và quyền dữ liệu**: mặc định không xuất field chưa được chấp thuận; dữ liệu nhạy cảm có thể bị che hoặc bị bỏ tùy người nhận.
5. **Kiểm tra truy cập một cách tường minh**: dùng API public, accessor được phép hoặc `trySetAccessible()` nếu thật sự cần và được module cho phép; thất bại thì xử lý/ghi nhận có kiểm soát.
6. **Đọc/biến đổi giá trị và bảo vệ output**: phân biệt `null`, chuẩn hóa kiểu/đơn vị, che secrets, giới hạn độ sâu của object graph, chống vòng lặp và định dạng đầu ra ổn định.
7. **Kiểm thử thay đổi schema**: thêm field mới không nên làm nó tự xuất hiện trong API/audit ngoài ý muốn. Dùng test cụ thể đối với allowlist, redaction và field kế thừa.

**Fail closed** cho dữ liệu có quyền riêng tư cao: nếu một field mới chưa được phê duyệt, đừng xuất nó. Nhưng không nhất thiết ném lỗi cho mọi field kỹ thuật bị bỏ; cách xử lý phải phản ánh hợp đồng và mức nghiêm ngặt của pipeline.

## 5. Ví dụ Java 17+: kiểm tra metadata và xuất các field được chọn

Chương trình minh họa một **chính sách của ứng dụng mẫu**, không phải quy tắc có sẵn của Reflection:

- Chọn field có `@BusinessData` (`RUNTIME`, `FIELD`).
- Bỏ `static` và `synthetic`; ở **demo này** còn bỏ `transient`.
- Bỏ field không có `@BusinessData`.
- Field có `@Sensitive` vẫn là thông tin nghiệp vụ nhưng **không đọc hoặc in giá trị**.
- Duyệt superclass có chủ ý và sắp xếp từng nhóm field theo tên để đầu ra ổn định.
- Chỉ sau các bước lọc mới gọi `trySetAccessible()` rồi `Field.get(target)`. Ví dụ đặt các class trong cùng file, chạy từ classpath nên thao tác này được phép.

Lưu thành file **`BusinessFieldSelectionDemo.java`**:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;

public class BusinessFieldSelectionDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface BusinessData {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @interface Sensitive {}

    static class OrderBase {
        @BusinessData private final String tenantId = "tenant-a";
        private final String traceSource = "batch";
    }

    static class Order extends OrderBase {
        private static final String CACHE_VERSION = "v1";
        @BusinessData private final String orderId = "A-17";
        @BusinessData private final int totalCents = 2500;
        @BusinessData @Sensitive private final String customerEmail = "a@example.com";
        private transient String draftNote = "review";
        private String internalCache = "ready";
    }

    static void inspect(Object target) throws IllegalAccessException {
        for (Class<?> type = target.getClass();
             type != null && type != Object.class;
             type = type.getSuperclass()) {
            Field[] fields = type.getDeclaredFields();
            Arrays.sort(fields, Comparator.comparing(Field::getName));
            for (Field field : fields) {
                String label = type.getSimpleName() + "." + field.getName();
                int modifiers = field.getModifiers();
                if (field.isSynthetic()) {
                    System.out.println(label + " -> skip: synthetic");
                } else if (Modifier.isStatic(modifiers)) {
                    System.out.println(label + " -> skip: static");
                } else if (Modifier.isTransient(modifiers)) {
                    System.out.println(label + " -> skip: transient (demo policy)");
                } else if (!field.isAnnotationPresent(BusinessData.class)) {
                    System.out.println(label + " -> skip: not annotated");
                } else if (field.isAnnotationPresent(Sensitive.class)) {
                    System.out.println(label + " -> REDACTED");
                } else if (!field.trySetAccessible()) {
                    System.out.println(label + " -> inaccessible");
                } else {
                    System.out.println(label + " -> " + field.get(target));
                }
            }
        }
    }

    public static void main(String[] args) throws IllegalAccessException {
        inspect(new Order());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac --release 17 BusinessFieldSelectionDemo.java
java BusinessFieldSelectionDemo
```

**Đầu ra đã kiểm chứng**:

```text
Order.CACHE_VERSION -> skip: static
Order.customerEmail -> REDACTED
Order.draftNote -> skip: transient (demo policy)
Order.internalCache -> skip: not annotated
Order.orderId -> A-17
Order.totalCents -> 2500
OrderBase.tenantId -> tenant-a
OrderBase.traceSource -> skip: not annotated
```

**Phân tích đầu ra:**

1. `CACHE_VERSION` là field của class, không phải state riêng của `Order`; trình xuất instance bỏ nó.
2. `customerEmail` mang cả `@BusinessData` và `@Sensitive`. Thông tin đó **có ý nghĩa nghiệp vụ nhưng không được tiết lộ**. Demo không gọi `Field.get` trên field này.
3. `draftNote` bị bỏ bởi **chính sách demo**, không phải vì mọi công cụ Java đều phải bỏ `transient`.
4. `internalCache` có thể hữu ích cho debug, nhưng không có `@BusinessData` nên không đi vào projection nghiệp vụ.
5. `orderId` và `totalCents` được chọn, truy cập và in ra. `final` không ngăn việc đọc, cũng không làm mất ý nghĩa nghiệp vụ.
6. `tenantId` là private field thuộc `OrderBase`; duyệt superclass giúp tìm nó. `traceSource` được nhìn thấy ở metadata nhưng không được xuất vì thiếu annotation.
7. Chương trình hiển thị quyết định cho từng field để phục vụ học tập; trong hệ thống thực tế, **đừng log tên hoặc metadata nhạy cảm nếu chính sách cấm**. Thông thường exporter chỉ trả các trường được phê duyệt, còn việc từ chối được ghi trong kênh kiểm toán phù hợp.

Annotation `@BusinessData` trong mã không tự thực thi hành vi; **phương thức `inspect` mới là bộ xử lý**. Nếu bỏ `@Retention(RetentionPolicy.RUNTIME)`, annotation không nhất thiết tồn tại trong metadata runtime để `isAnnotationPresent` kiểm tra; nếu bỏ `@Target(ElementType.FIELD)` thì annotation có thể không đúng mục tiêu thiết kế.

## 6. Field và property không phải lúc nào cũng tương ứng 1–1

### 6.1. JavaBeans và getter

Một *property* có thể được công khai qua `getTotal()`, `isActive()` hoặc accessor khác dù không có field cùng tên. Ngược lại, một field cache có thể không có accessor nào trong public API. Framework dựa trên getter/property và framework dựa trên field access có thể xây ra hai schema khác nhau.

Không nên tùy tiện gọi tất cả phương thức bắt đầu bằng `get` để “kiểm tra dữ liệu”: getter có thể truy vấn database, tải lazy association, tính toán tốn kém, thay đổi cache hoặc ném exception. Nếu cần introspection property, phải đọc đúng hợp đồng JavaBeans/framework và kiểm soát lúc thực thi accessor.

### 6.2. Record từ Java 16+

Với:

```java
record OrderView(String orderId, int totalCents) {}
```

Đây là **khai báo kiểu minh họa**, không phải ứng dụng độc lập. `OrderView.class.isRecord()` là `true` và `getRecordComponents()` cho metadata của `orderId`, `totalCents` theo **thứ tự khai báo trong record header**. Nếu chính sách muốn tạo projection từ record components, nên bắt đầu ở API `RecordComponent` và hợp đồng record, thay vì mặc định mọi field bên trong record là trường dữ liệu.

Chú ý annotation của *record component*, field nền và accessor có các quy tắc về `@Target` riêng. **Không giả định** một annotation đặt trên record component luôn xuất hiện đúng như mong muốn trên `Field`; phải kiểm tra thiết kế annotation và metadata mà công cụ thực sự đọc.

### 6.3. ORM, serializer và framework

Các trường hợp như JPA entity, JSON model, dependency injection hoặc object do framework sinh thường có hợp đồng riêng:

- Một entity có thể dùng **field access** hoặc **property access**; không suy đoán mapping chỉ từ danh sách field.
- Annotation `@Transient` của JPA, từ khóa Java `transient` và cách Jackson quyết định serialize là các cơ chế khác nhau.
- Proxy/lazy-loading object có thể chứa state hạ tầng; Reflection trên **runtime class** của proxy có thể trả field khác so với domain class được dự định.
- Các thư viện serializer có thể xử lý annotation, getter, record component, accessor visibility và naming strategy; quét `getDeclaredFields()` không tái tạo đầy đủ hành vi đó.

Nếu mục đích là xuất JSON hoặc dữ liệu persistence, ưu tiên **chính schema/mapper của công nghệ được dùng** thay vì dựng lại một hệ thống mapping khác từ field thô.

## 7. Kế thừa, field hiding và định danh ổn định

`getDeclaredFields()` không nhận field của superclass; ví dụ đã duyệt chuỗi `getSuperclass()`. Nhưng việc duyệt này đưa ra thêm câu hỏi:

- **Field hiding:** subclass và superclass có thể cùng khai báo một field tên `code`, đây là **hai field khác nhau** với `getDeclaringClass()` khác nhau; sơ đồ chỉ dựa vào tên `"code"` có thể ghi đè một field. Hãy dùng định danh gồm declaring class + field name, hoặc đặt quy tắc ưu tiên rõ ràng trong schema.
- **Interface constants:** interface có field `public static final`. Một inspector state của instance không nên tự coi chúng là thuộc tính nghiệp vụ.
- **Phạm vi superclass:** đối tượng ứng dụng có thể kế thừa lớp framework với nhiều field kỹ thuật. Không quét không giới hạn vào mọi superclass nếu hợp đồng chỉ thuộc một miền kiểu được quản lý.
- **Thứ tự:** sắp xếp tùy mục đích; nếu schema có thứ tự đã công bố, hãy dùng thứ tự của schema thay vì alphabetical để tránh breaking change.

`Field.getDeclaringClass()` chỉ nơi field được khai báo; `target.getClass()` xác định runtime type. Cả hai hữu ích khi kết quả quét cần audit rõ ràng.

## 8. Quyền truy cập, an toàn dữ liệu và tác dụng phụ

### 8.1. Không dựa vào `setAccessible(true)` vô điều kiện

`Field.trySetAccessible()` trả `false` khi không thể bật quyền truy cập theo mô hình module; `setAccessible(true)` có thể ném `InaccessibleObjectException`. Trong named module, package chưa `opens` phù hợp có thể chặn Reflection vào thành viên non-public. Đừng tự động đề xuất `--add-opens` chỉ để xuất dữ liệu nếu đã có API công khai/DTO.

Trong ví dụ, cả các lớp nằm cùng một file classpath, nên `trySetAccessible()` thành công. Một thư viện reusable cần xử lý trường hợp không thành công (bỏ field có kiểm soát, báo thiếu quyền hoặc chuyển sang accessor được phép), đồng thời không nuốt lỗi khiến báo cáo sai âm thầm.

### 8.2. Đọc giá trị phải có lý do

- `Field.get(target)` đọc **giá trị runtime**, khác hẳn `getType()` hoặc `getModifiers()` chỉ đọc metadata.
- Đọc `static` field có thể kích hoạt **class initialization**. Ví dụ chọn bỏ static trước khi đọc giúp tránh rủi ro không cần thiết này.
- Thông tin có thể đổi đồng thời do thread khác; một lần quét nhiều field không tự là snapshot nguyên tử.
- Giá trị là object tham chiếu, collection hoặc graph có vòng lặp. Nếu tiếp tục truy sâu phải có giới hạn độ sâu, visited identity set, timeout/budget và quy tắc loại nội dung nhạy cảm.
- Đừng gọi `toString()` bừa bãi trên object chưa tin cậy: implementation có thể thực hiện I/O, tạo log, kích hoạt lazy loading hoặc in secrets. Trong demo, các giá trị được chọn chỉ là `String` và primitive `int`.

Một chính sách tốt lọc **trước khi đọc**, tránh giữ plaintext nhạy cảm trong bộ đệm/log trung gian rồi mới “redact” ở cuối.

### 8.3. Quyền đọc và quyền xuất là hai cấp khác nhau

Dù Reflection được phép đọc field, điều đó **không** khẳng định người nhận được phép xem nó. Phải xác định vai trò/ngữ cảnh truy cập, tập dữ liệu cho phép, redaction, audit, lưu trữ và thời hạn giữ dữ liệu ở layer ứng dụng.

Ví dụ `email` hoặc `tenantId` có thể là dữ liệu nghiệp vụ hợp lệ, nhưng endpoint xuất công khai cần ẩn/bỏ, còn một công cụ quản trị có thể được cấp quyền khác. Đây là quyết định domain/security, không phải kết quả tự động của `Field.isSynthetic()` hay modifier.

## 9. Những anti-pattern cần tránh

| Mẫu sai | Vấn đề | Thay thế |
| --- | --- | --- |
| `for (Field f : c.getDeclaredFields()) out.put(f.getName(), f.get(obj));` | Có thể lộ field riêng tư/kỹ thuật, có access error, tên field trùng và output không ổn định. | Allowlist/annotation/schema, lọc metadata, truy cập đúng quyền, redaction. |
| Bỏ mọi field `private` | Mất dữ liệu nghiệp vụ đang được đóng gói. | Chọn theo contract; lấy giá trị bằng public accessor khi phù hợp. |
| Chỉ bỏ `static` rồi coi phần còn lại là domain data | Bao gồm cache, transient, token, synthetic và state framework. | Áp dụng chính sách nhiều bước, mặc định opt-in. |
| Coi `transient` là “không thuộc nghiệp vụ” trong mọi trường hợp | Trộn hợp đồng Java serialization với domain mapping. | Chính sách riêng cho từng đầu ra/framework. |
| Coi `!isSynthetic()` là bằng chứng an toàn | Compiler flag không mô tả sensitivity hay ownership. | Dùng allowlist và metadata nghiệp vụ. |
| Tự động gọi mọi getter và duyệt object graph vô hạn | Có thể gây side effect, N+1 queries, vòng lặp, lộ secrets. | DTO hoặc API schema riêng, budget và cycle detection. |
| Sắp xếp mặc định dựa vào thứ tự `getDeclaredFields()` | Reflection không bảo đảm thứ tự. | Sort hoặc schema order. |
| Bật `setAccessible(true)` cho mọi field | Va chạm module boundary, mở quá mức, không cần thiết. | Chọn field trước, dùng accessor/DTO hoặc truy cập có kiểm soát. |

## 10. Bài tập áp dụng

1. Thêm `@BusinessData private final String currency = "VND";` vào `Order`, dự đoán nơi nó xuất hiện trong output và kiểm tra thứ tự do `Arrays.sort`.
2. Gắn `@BusinessData` vào `internalCache`. Tại sao annotation opt-in lúc này khiến nó được xuất, dù tên cho thấy có thể là hạ tầng? Thiết kế thêm một kiểm tra schema/owner review để ngăn sai sót người khai báo.
3. Gỡ `@Sensitive` khỏi `customerEmail` và quan sát chính sách demo thay đổi như thế nào. Vì sao tác động này cần review security?
4. Thêm một field tên `orderId` vào `OrderBase`. Nếu exporter dùng map chỉ có key là tên field, điều gì xảy ra? Hãy sử dụng `getDeclaringClass()` để tránh trùng.
5. Thử tạo `record OrderView(String orderId, int totalCents) {}` và in các `RecordComponent`. So sánh cách chọn schema record với duyệt `getDeclaredFields()`.
6. Thiết kế hai projection khác nhau: **audit nội bộ** và **JSON công khai**. Nêu rõ allowlist, quyền, mức redaction và chính sách xử lý field chưa biết.

**Checklist trước khi đưa một field vào đầu ra:** Ai sở hữu ý nghĩa của field? Nó thuộc schema/contract phiên bản nào? Có annotation/allowlist phê duyệt không? Là field instance hay state hạ tầng? Có nhạy cảm không? Có thể đọc hợp lệ không? Việc đọc có side effect hoặc snapshot không nhất quán không? Đối tượng sẽ được biểu diễn bằng giá trị nguyên thủy, DTO hay traversal an toàn? Có kiểm thử khi lớp thêm field mới không?

## 11. Tài liệu tham khảo

- [Java SE 17 — Class.getDeclaredFields(), getFields(), getRecordComponents()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html)
- [Java SE 17 — java.lang.reflect.Field](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html)
- [Java SE 17 — java.lang.reflect.Modifier](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Modifier.html)
- [Java SE 17 — AccessibleObject.trySetAccessible()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html#trySetAccessible())
- [Java SE 17 — java.lang.reflect.RecordComponent](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/RecordComponent.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tooling-design-004">Lọc static, synthetic, bridge và thành viên không phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# Lọc `static`, `synthetic`, `bridge` và thành viên không phù hợp khi thiết kế công cụ Reflection

## 1. Vì sao một công cụ Reflection cần lọc thành viên?

Một class có thể chứa hằng `static`, field do compiler sinh, bridge method phục vụ generic dispatch, method `Object`, hoặc API quản trị mà công cụ không được công bố. `getDeclaredMethods()` và `getDeclaredFields()` liệt kê ứng viên theo phạm vi truy vấn; chúng **không áp dụng hợp đồng nghiệp vụ**.

Bộ hiển thị hành vi của instance có thể bỏ `static` và `bridge`; trình phân tích bytecode lại cần giữ chính những member ấy. Vì vậy lọc dựa trên **vai trò đọc/ghi/gọi**, chữ ký, visibility, annotation và nguồn khai báo, với quyết định rõ khi gặp `synthetic`. Ví dụ Java 17+ trong bài minh họa cả hai bộ lọc: method hiển thị và field có thể ghi.

## 2. Đọc đúng metadata trước khi lọc

`Class<?>` trả các nhóm thành viên sau:

| API | Phạm vi | Điểm cần chú ý |
| --- | --- | --- |
| `getDeclaredMethods()` | Method khai báo trực tiếp trong class/interface, mọi visibility | Không tự lấy method kế thừa; có thể thấy bridge/synthetic |
| `getMethods()` | Method `public` của kiểu và các method `public` được kế thừa | Có thể bao gồm method `Object`; không chỉ method do kiểu khai báo |
| `getDeclaredFields()` | Field khai báo trực tiếp, mọi visibility | Có thể bao gồm field do compiler sinh |
| `getFields()` | Field `public` của kiểu và field `public` được kế thừa | Field có thể bị che khuất, nên xem declaring class |
| `getDeclaredConstructors()` | Constructor khai báo trực tiếp, mọi visibility | Constructor không kế thừa |
| `getConstructors()` | Constructor `public` của chính kiểu | Không có “constructor kế thừa” |

**Không có thứ tự trả về được bảo đảm** từ các mảng Reflection nói trên. Không dùng vị trí `methods[0]` hoặc `fields[0]` làm ý nghĩa nghiệp vụ; nếu cần đầu ra ổn định, tự sắp xếp theo khóa rõ ràng.

Một metadata member có thể trả:

- `member.getName()`: tên.
- `member.getDeclaringClass()`: class/interface khai báo trực tiếp member.
- `member.getModifiers()`: tập cờ modifier, giải mã bằng `java.lang.reflect.Modifier`.
- `member.isSynthetic()`: member được đánh dấu synthetic trong class file.
- Với `Method`: `isBridge()`, `isVarArgs()`, `getParameterTypes()`, `getReturnType()`, `getAnnotations()`.
- Với `Field`: `getType()`, `getGenericType()` và `isEnumConstant()`.
- Với `Constructor<?>`: `getParameterTypes()`, `isVarArgs()` và `isSynthetic()`.

Tên và modifier **không đủ** để quyết định có thể truy cập hoặc gọi: phải xét declaring class, package, module, receiver và quy tắc access tại nơi gọi.

## 3. Lọc `static` chính xác

`static` có ý nghĩa khác nhau với field và method:

- **Static method** thuộc về class, không đòi instance receiver.
- **Static field** thuộc về class, không đòi instance receiver; có thể là hằng, biến cấu hình hoặc dữ liệu dùng chung.
- **Instance method/field** làm việc trên một đối tượng nhận lời gọi.

Kiểm tra bằng `Modifier.isStatic(member.getModifiers())`:

```java
boolean instanceMethod = !Modifier.isStatic(method.getModifiers());
boolean instanceField = !Modifier.isStatic(field.getModifiers());
```

Đây là đoạn minh họa; `method`, `field` và import `java.lang.reflect.Modifier` phải có trong ngữ cảnh.

Bộ ánh xạ trạng thái của *một đối tượng* thường bỏ static field. Công cụ liệt kê hàm tiện ích lại có thể **chỉ** chọn static method. Không áp bộ lọc “loại static” cho mọi công cụ một cách máy móc.

**Lưu ý:** Class lồng khai báo `static` không có nghĩa constructor của nó là “static constructor”. Constructor không phải method static; lọc `Modifier.isStatic` trên method/field khác với quyết định loại constructor.

## 4. `synthetic` là gì?

`Member.isSynthetic()` cho biết member được đánh dấu **`ACC_SYNTHETIC`** trong metadata class file (theo cách JVM/Reflection biểu diễn). Compiler có thể tạo những member này để hiện thực các chi tiết ngôn ngữ như truy cập nội bộ, capture, bridge hoặc dạng hỗ trợ khác.

Ví dụ có thể gặp:

- Field tổng hợp chứa tham chiếu tới enclosing instance trong một số inner class.
- Field compiler tạo để chứa giá trị bắt từ ngữ cảnh xung quanh.
- Method trợ giúp compiler sinh ra.
- Bridge method được tạo để bảo toàn đa hình khi có generic erasure hoặc covariant return.

**Không dựa vào tên bắt đầu bằng `$`** để nhận biết synthetic: `$` là ký tự hợp lệ trong identifier Java, và quy ước tên compiler thay đổi theo trình biên dịch/phiên bản. Đồng thời, **không phải mọi member compiler tạo đều `isSynthetic() == true`**: chẳng hạn một accessor hợp lệ của record hoặc constructor mặc định có thể là API nguồn của kiểu.

`isSynthetic()` phù hợp để loại member kỹ thuật ra khỏi giao diện nghiệp vụ, nhưng với công cụ phân tích bytecode hoặc debugger thì chính member synthetic lại là dữ liệu cần quan sát.

## 5. `bridge` xuất hiện vì sao, khác `synthetic` ra sao?

**Bridge method** là phương thức do compiler có thể tạo để bảo toàn quy tắc override/dispatch của Java khi chữ ký bytecode chịu ảnh hưởng của type erasure hoặc covariant return type.

Ví dụ nguồn:

```java
class Parent<T> {
    public T convert(T input) {
        return input;
    }
}

class Child extends Parent<String> {
    @Override
    public String convert(String input) {
        return input.toUpperCase();
    }
}
```

Ở bytecode, `Parent<T>.convert(T)` sử dụng dạng đã erasure tương ứng `convert(Object)`. `Child.convert(String)` có chữ ký khác tại mức JVM, nên compiler có thể sinh method bridge `convert(Object)` trong `Child` để chuyển kiểu đầu vào và gọi `convert(String)` thật.

Trong Reflection:

- `Method.isBridge()` nhận biết method bridge.
- `Method.isSynthetic()` thường cũng `true` trên bridge do `javac` tạo, nhưng **hai khái niệm không đồng nhất**. Đánh giá hai cờ riêng nếu chính sách cần.
- Nếu công cụ hiển thị cả method triển khai và bridge như hai operation nghiệp vụ, người dùng có thể thấy trùng lặp hoặc hai chữ ký khó hiểu.
- **Không xóa hoặc “vô hiệu hóa” bridge khi chạy chương trình.** Bridge là thành phần hợp lệ của hành vi đa hình Java. “Lọc” ở đây chỉ là quyết định *có đưa vào catalog API của công cụ hay không*.

Một bộ liệt kê method hướng tới lập trình viên thường lọc:

```java
!method.isBridge() && !method.isSynthetic()
```

Nhưng đây vẫn là chính sách của công cụ. Công cụ phân tích tương thích binary, debugger hoặc bytecode inspector có thể phải giữ cả hai.

## 6. Ví dụ Java 17+ hoàn chỉnh: lọc method và field theo mục tiêu

Ví dụ thiết kế hai danh sách:

1. **Method hiển thị:** chỉ method `public`, thuộc instance, được khai báo trực tiếp trong class, không synthetic/bridge, không tham số, có giá trị trả về.
2. **Field có thể ghi qua giao diện dữ liệu:** chỉ field `public`, thuộc instance, không synthetic, không `final`.

Lưu thành **`MemberFilterDemo.java`**:

```java
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MemberFilterDemo {
    public static class Parent<T> {
        public T convert(T value) {
            return value;
        }
    }

    public static class Sample extends Parent<String> {
        public String label = "initial";
        public final String id = "fixed";
        public static String category = "demo";
        private String secret = "hidden";

        public static String version() {
            return "v1";
        }

        public String describe() {
            return "sample";
        }

        public void reset() {
            label = "";
        }

        @Override
        public String convert(String value) {
            return value.toUpperCase(Locale.ROOT);
        }

        private String internal() {
            return secret;
        }
    }

    public static void main(String[] args) {
        Method[] declaredMethods = Sample.class.getDeclaredMethods();
        Field[] declaredFields = Sample.class.getDeclaredFields();

        List<String> visibleMethods = Arrays.stream(declaredMethods)
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !Modifier.isStatic(m.getModifiers()))
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .filter(m -> m.getParameterCount() == 0)
                .filter(m -> m.getReturnType() != void.class)
                .map(Method::getName)
                .sorted()
                .toList();

        List<String> writableFields = Arrays.stream(declaredFields)
                .filter(f -> Modifier.isPublic(f.getModifiers()))
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .filter(f -> !Modifier.isFinal(f.getModifiers()))
                .filter(f -> !f.isSynthetic())
                .map(Field::getName)
                .sorted()
                .toList();

        long bridgeCount = Arrays.stream(declaredMethods)
                .filter(Method::isBridge)
                .count();

        System.out.println("methods=" + visibleMethods);
        System.out.println("writableFields=" + writableFields);
        System.out.println("bridges=" + bridgeCount);
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac MemberFilterDemo.java
java MemberFilterDemo
```

Kết quả khi biên dịch bằng `javac` của JDK 17:

```text
methods=[describe]
writableFields=[label]
bridges=1
```

**Vì sao kết quả này đúng?**

- `describe()` được chọn: method `public`, instance, không tham số, trả `String` và không phải bridge/synthetic.
- `version()` bị loại vì `static`; `reset()` bị loại vì trả `void`; `convert(String)` bị loại bởi chính sách method không tham số; `internal()` bị loại vì `private`.
- Khi override `Parent<String>.convert(String)`, `javac` tạo một bridge kiểu `convert(Object)` trong `Sample`. Bộ lọc phát hiện bridge/synthetic và không đưa method này vào danh sách nghiệp vụ.
- `label` được chọn vì là field `public`, instance, không `final`.
- `category` bị loại vì `static`; `id` bị loại vì `final`; `secret` bị loại vì `private`.
- `sorted()` làm kết quả đầu ra ổn định ngay cả khi `getDeclaredMethods()`/`getDeclaredFields()` không bảo đảm thứ tự.

**Giới hạn của ví dụ:** Đây là chính sách tạo một danh sách hiển thị và một danh sách field có thể ghi, **không phải** bộ lọc đúng cho mọi serializer, ORM, test framework hoặc API generator. Ví dụ dùng `getDeclared...` nên chỉ xét member khai báo trực tiếp trong `Sample`, không đưa inherited method `Object` vào kết quả. Nó kiểm tra modifier nhưng **chưa thực hiện kiểm tra quyền truy cập khi gọi trên module khác**: điều đó phải được giải quyết riêng khi tích hợp công cụ thực.

## 7. Định nghĩa “không phù hợp” theo vai trò, chữ ký và visibility

Bên cạnh `static`, `synthetic` và `bridge`, công cụ thường phải kiểm tra thêm:

| Tiêu chí | Khi nào cần lọc? | Điều không được suy diễn |
| --- | --- | --- |
| `public` / `protected` / package-private / `private` | Khi chỉ công bố public API hoặc muốn giới hạn truy cập | Có modifier `public` chưa đảm bảo gọi được qua mọi module |
| `abstract` | Nếu công cụ đòi thao tác tự có implementation trong class | Method abstract vẫn có thể được dispatch trên instance subclass cụ thể |
| `final` trên field | Nếu công cụ muốn *ghi* field | Field `final` vẫn có thể đọc; không đồng nghĩa synthetic |
| `transient` trên field | Khi serialization policy chọn bỏ transient | Không tự động vô nghĩa với mọi framework |
| `volatile` trên field | Khi cần quy tắc truy cập đồng bộ thích hợp | `volatile` không đồng nghĩa field phải loại khỏi mọi danh sách |
| `isVarArgs()` | Nếu bộ gọi không biết dựng mảng varargs | Varargs vẫn là Java method hợp lệ |
| Danh sách tham số và kiểu trả về | Khi tool chỉ hỗ trợ một hình dạng API nhất định | Không suy ra method sai nếu chữ ký không khớp chính sách |
| Annotation | Khi muốn chỉ công bố member được đánh dấu | Annotation chỉ đọc được ở runtime khi retention phù hợp |
| Declaring class và kế thừa | Khi chỉ liệt kê API của class đang xét | Inherited/default methods có thể là API cần giữ |
| Khả năng truy cập thực tế | Trước khi gọi method, constructor hoặc đọc/ghi field | Metadata tìm được chưa đồng nghĩa được phép sử dụng |
| Cặp tên và chữ ký overload | Khi xây operation registry | Chỉ dùng tên method làm key có thể gây ghi đè overload |

Ví dụ trường `transient` có ý nghĩa với Java serialization truyền thống nhưng một thư viện ánh xạ dữ liệu có thể có hợp đồng riêng. Một method `void reset()` là API hợp lệ; nó chỉ bị loại nếu **công cụ đang xây danh sách hàm đọc có kết quả** như ví dụ trên.

Nếu dùng danh sách method để tự động gọi, cần xác định rõ loại tham số có thể cấp được: `Class<?>[] getParameterTypes()`, `getReturnType()`, `getExceptionTypes()`, `isVarArgs()` và annotation của method/parameter. Không nên gọi ngẫu nhiên mọi method không tham số vì chúng có thể gây tác dụng phụ, ghi file, gửi request hoặc làm thay đổi state.

## 8. Chú ý khi duyệt kế thừa, overload và method của `Object`

`getDeclaredMethods()` chỉ xét những method khai báo trực tiếp. `getMethods()` xét public method theo phạm vi đã nói và có thể trả các method quen thuộc như `toString()`, `hashCode()` hoặc `getClass()` khi phù hợp.

Nếu chỉ muốn operation nghiệp vụ, có thể sử dụng chính sách công khai theo annotation thay vì tự suy luận từ tên. Khi cho phép method inherited:

1. Xác định rõ có chấp nhận method thừa kế từ superclass hay interface mặc định (`default method`) không.
2. Dùng `getDeclaringClass()` để biết nơi khai báo; đừng mặc định mọi `Method` trả về từ `Child.class.getMethods()` đều thuộc `Child`.
3. Tránh coi mọi method cùng tên là trùng: `process(int)` và `process(String)` là **hai overload**.
4. Khi tạo khóa operation, nên chứa tên và **danh sách kiểu tham số**; nếu tích hợp JVM signature/covariant return/bridge thì xác định chính sách biểu diễn đầy đủ.
5. Có thể cần giải quyết method override theo quy tắc “triển khai phù hợp nhất” nếu làm catalog tổng hợp của hierarchy. Không lấy method đầu tiên được trả về để làm lựa chọn mặc định.

Ví dụ minh họa key phân biệt overload:

```java
static String signature(Method method) {
    String params = Arrays.stream(method.getParameterTypes())
            .map(Class::getTypeName)
            .collect(java.util.stream.Collectors.joining(", "));
    return method.getName() + "(" + params + ")";
}
```

Đoạn này phải đặt trong class có import `java.lang.reflect.Method` và `java.util.Arrays`; nó không phải chương trình độc lập. Trong hệ thống nhiều class, nên bổ sung identity của declaring class/loader/module cho những mục đích cần phân biệt thực thể. Với dữ liệu ổn định để so sánh, đừng chỉ dựa vào `Method.toString()` hoặc thứ tự tự nhiên của API Reflection.

**Ví dụ đặc thù:** Record có accessor hợp lệ như `name()`; enum có các trường hằng enum và method do ngôn ngữ cung cấp; những thứ ấy có thể không mang cờ synthetic. Nếu một tool không muốn công bố chúng, phải có quy tắc theo **loại class/role/annotation** riêng, chứ không suy ra từ `isSynthetic()`.

## 9. Annotation whitelist và chính sách tường minh

Với một tool thực hiện hành động thực tế, cách an toàn hơn việc tự động gọi mọi public method có thể là yêu cầu annotation định danh operation được hỗ trợ. Annotation cần `@Retention(RetentionPolicy.RUNTIME)` để đọc bằng Reflection tại runtime.

Ví dụ định hướng:

```java
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@interface ToolAction {}

class ExampleActions {
    @ToolAction
    public String status() {
        return "OK";
    }

    public void deleteAll() {
        // Không tự động công bố vì thiếu @ToolAction.
    }
}
```

Đây là snippet minh họa, không có `main`. Khi duyệt `Method`, có thể thêm `method.isAnnotationPresent(ToolAction.class)` để chỉ nhận operation được chủ ý khai báo. Một hệ thống thật còn phải xác thực quyền của người dùng, giới hạn tham số, xử lý lỗi và side effect; annotation **không tự cung cấp quyền truy cập hay bảo mật**.

Cần quyết định trước về annotation kế thừa/override. `@Inherited` chỉ có quy tắc kế thừa đối với annotation trên **class**; không mặc nhiên khiến annotation trên method của superclass được kế thừa theo ý muốn của framework. Nếu cần quy tắc riêng cho method bị override, phải duyệt hierarchy và định nghĩa logic đó công khai.

## 10. Trường hợp constructor và nested class

Constructor không dùng `Method.isBridge()`; có thể kiểm tra `Constructor.isSynthetic()`, visibility, annotation và danh sách tham số. Constructor không kế thừa, vì vậy `getDeclaredConstructors()` và `getConstructors()` có semantics khác hẳn các phương thức.

Ví dụ công cụ dependency injection thường chọn một constructor có annotation thích hợp hoặc theo một quy ước xác định. Không mặc định chọn **constructor đầu tiên** trong mảng; thứ tự Reflection không ổn định. Không tự động gọi mọi constructor tìm được: constructor có thể làm công việc tốn kém, tạo tài nguyên hoặc có side effect.

Đối với nested class, cần phân biệt:

- Nested class `static` thường không cần enclosing instance để tạo instance.
- Non-static inner class thường có tham chiếu tới enclosing instance và constructor ở bytecode có thể mang tham số bổ sung liên quan tới enclosing instance.
- Compiler có thể tạo field synthetic hỗ trợ mối liên hệ đó.

Do đó, công cụ tự dựng constructor arguments cần quan sát **parameter types thực tế**, không suy từ hình thức mã nguồn và cũng không bỏ mọi tham số synthetic theo giả định cứng.

## 11. Quyền truy cập: “đủ điều kiện hiển thị” chưa phải “gọi được”

Trước khi gọi `Method.invoke(...)`, `Constructor.newInstance(...)` hoặc `Field.get/set(...)`, phải xét quyền của caller và module. Lớp `public` ở package không exported cho module gọi vẫn có thể không truy cập được theo cách kỳ vọng. `getDeclaredMethod` tìm private member không tự làm private member thành public.

Các điểm thực hành:

- `method.canAccess(receiver)`, `field.canAccess(receiver)` hoặc `constructor.canAccess(null)` có thể hỗ trợ kiểm tra truy cập ở ngữ cảnh thích hợp; instance member cần receiver phù hợp, static member dùng `null`.
- Không lạm dụng `setAccessible(true)` để vượt bảo vệ; Java Platform Module System (JPMS) có thể từ chối deep reflection, ví dụ ném `InaccessibleObjectException`. `opens` và `exports` có mục đích khác nhau.
- Nếu chuyển sang `MethodHandle` hoặc `VarHandle`, các thao tác `MethodHandles.Lookup` vẫn mang mô hình quyền riêng; việc đã có `Method` không bảo đảm `unreflect` sẽ thành công.
- Khi registry cache metadata/handle theo class, cân nhắc vòng đời class loader: cache toàn cục có thể giữ class của plugin không được giải phóng.

Bộ lọc metadata có thể xác định *ứng viên*. Trước khi thực hiện, công cụ phải xác thực capability của thao tác thực tế và chính sách quyền truy cập trong bối cảnh người dùng/chương trình.

## 12. Kiểm tra chất lượng bộ lọc trong hệ thống thật

Một bộ lọc đáng tin cậy phải có những kiểm tra sau:

- **Generic bridge:** một override do erasure tạo bridge không xuất hiện hai lần trong danh sách nghiệp vụ, nhưng cơ chế dispatch Java vẫn hoạt động.
- **Static:** field/method static được chọn hay loại theo chính sách đã công bố.
- **Synthetic:** những member compiler sinh không lọt vào catalog nghiệp vụ nếu chính sách cấm, và một member không synthetic cũng không mặc nhiên được chấp nhận.
- **Final, private, transient, varargs:** được xét riêng theo mục tiêu đọc, ghi, gọi hay serialize.
- **Overload:** `f(int)` và `f(String)` có danh tính khác nhau.
- **Inheritance/default method:** không bị bỏ hoặc nhân đôi ngoài chủ đích.
- **Enum/record/nested class:** được kiểm tra với loại class cụ thể, không chỉ với tên field/method.
- **Module/class loader:** cùng tên kiểu nhưng do loader khác định nghĩa không bị coi là một class duy nhất; lỗi quyền được xử lý minh bạch.
- **Determinism:** kết quả được sắp xếp bằng comparator/key nhất quán, không phụ thuộc thứ tự Reflection.
- **Side effects:** chỉ gọi thành viên mà hợp đồng và quyền đã cho phép; công cụ liệt kê metadata không cần thử chạy mọi method.

Một thiết kế tốt nên tách ba bước **thu thập metadata → quyết định hợp lệ → thực thi hoặc công bố**. Nhờ đó có thể kiểm tra predicate lọc độc lập, giải thích vì sao một member bị bỏ và thay đổi chính sách theo từng tác vụ mà không làm biến đổi hành vi của Java runtime.

### Câu hỏi tự kiểm tra

1. Vì sao `getDeclaredMethods()` có thể trả nhiều method hơn số method viết trực tiếp trong source của subclass?
2. `Method.isBridge()` dùng để phát hiện điều gì? Nếu bridge là synthetic, có nên bỏ kiểm tra bridge trong mọi công cụ không?
3. Với một bộ serialize object, khi nào `Modifier.isStatic` là tiêu chí hợp lý? Khi nào là tiêu chí sai?
4. Vì sao field `final` có thể được đọc nhưng thường không nên nằm trong danh sách có thể ghi?
5. Vì sao dùng mỗi tên method làm khóa registry có thể làm mất overload?
6. `getMethods()` và `getDeclaredMethods()` khác nhau thế nào về inherited member và visibility?
7. Nếu `Method` là public nhưng package đích không exported cho module gọi, có thể bỏ bước kiểm tra truy cập không?
8. Vì sao annotation whitelist cần `RetentionPolicy.RUNTIME`, và vì sao annotation vẫn chưa đủ để bảo đảm một lời gọi an toàn?

**Ghi nhớ:** `static`, `synthetic` và `bridge` là những đặc điểm kỹ thuật có ý nghĩa riêng. Công cụ Reflection nên lọc chúng theo **hợp đồng sử dụng cụ thể**, kết hợp với visibility, chữ ký, quyền truy cập, kế thừa, annotation và khả năng thực thi; không coi bất kỳ một cờ nào là tiêu chí chất lượng duy nhất.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tooling-design-005">Xử lý field trùng tên hoặc method ghi đè theo chính sách nhất quán</a>

<details>
<summary>Xem chi tiết</summary>

# Xử lý field trùng tên và method ghi đè theo chính sách nhất quán khi dùng Reflection

Khi quét một hệ phân cấp class, **field trùng tên** và **method override** cần hai chính sách riêng. Field ở cha/con vẫn là hai vị trí lưu trữ khác nhau; method override sử dụng quy tắc dispatch động theo receiver. `getDeclaredFields()`/`getDeclaredMethods()` chỉ cho biết khai báo ở từng class và không cam kết thứ tự, nên việc giữ phần tử đầu tiên có thể thay đổi kết quả.

Bài này xây registry có khóa chữ ký rõ ràng, thứ tự duyệt từ lớp cụ thể lên superclass và lựa chọn *closest declaration wins* có chủ đích. Nếu ứng dụng phải bảo toàn mọi field cùng tên, nó phải dùng khóa kèm declaring class hoặc báo collision, thay vì gộp im lặng.

## 1. Field hiding khác method overriding

### 1.1. Field cùng tên là hai trường dữ liệu riêng

~~~java
class Parent {
    public String id = "parent";
}

class Child extends Parent {
    public String id = "child";
}
~~~

`Child.id` **che khuất** (*hide*) `Parent.id` theo cách phân giải tên trong mã nguồn. Nhưng đối tượng `Child` vẫn **chứa cả hai trường**:

- `child.id` đọc trường `Child.id`.
- `((Parent) child).id` đọc trường `Parent.id`.
- `Child.class.getDeclaredField("id")` trả về khai báo ở `Child`.
- `Parent.class.getDeclaredField("id")` trả về khai báo ở `Parent`.
- `Field.get(child)` đọc đúng **trường mà đối tượng `Field` biểu diễn**, kể cả khi receiver là một đối tượng lớp con; nó không tự chuyển sang field cùng tên ở lớp con.

Vì vậy, chọn trường của lớp con làm đại diện cho khóa `id` là **chính sách flatten schema**, không có nghĩa trường ở lớp cha biến mất. Nếu một serializer cần giữ cả hai giá trị, phải dùng khóa có thêm lớp khai báo (ví dụ `Parent#id`, `Child#id`) hoặc báo xung đột; không gộp cả hai một cách im lặng.

### 1.2. Method override là một hành vi được phân phối động

~~~java
class Parent {
    public String label() { return "parent"; }
}

class Child extends Parent {
    @Override
    public String label() { return "child"; }
}
~~~

`Child.label()` **override** `Parent.label()`. Lời gọi instance bình thường `new Child().label()` sử dụng triển khai của `Child`. Kể cả khi lấy một `Method` từ `Parent.class.getDeclaredMethod("label")` rồi `invoke(child)`, lời gọi instance này vẫn sử dụng **dynamic dispatch** nên trả `"child"` (theo quy tắc override của Java).

Ngược lại, `private` method không bị override theo nghĩa thông thường; `static` method được **hide**, không dispatch như instance method; và hai method cùng tên nhưng **khác tập kiểu tham số** là *overload*, không phải override. Một bộ quét thành viên không được gộp tất cả các method chỉ theo tên.

| Tình huống | Bản chất | Quy tắc xử lý phù hợp với registry công khai |
| --- | --- | --- |
| `Parent.id` và `Child.id` | Hai field độc lập; field của lớp con hide field cha | Nếu cần một thuộc tính theo tên: chọn lớp con và báo đã che khuất; nếu cần dữ liệu đầy đủ: giữ cả hai với khóa khác nhau |
| `Parent.label()` và `Child.label()` | Instance method override | Một khóa method theo tên + danh sách kiểu tham số; chọn khai báo hợp lệ ở lớp cụ thể hơn |
| `set(String)` và `set(int)` | Overload | Giữ **hai** khóa khác nhau |
| `Parent.staticMethod()` và `Child.staticMethod()` | Static method hiding | Xử lý riêng theo chính sách static; không coi là override |
| Private method cùng tên/chữ ký trong hai lớp | Không có quan hệ override thông thường | Nếu quét private, định danh theo lớp khai báo và áp dụng access control riêng |
| Method do compiler sinh (`bridge`/`synthetic`) | Thành viên hỗ trợ bytecode, có thể trùng khóa nguồn | Chọn rõ có loại khỏi mô hình API nguồn hay cần giữ cho phân tích bytecode |

## 2. Reflection trả về gì và những giả định cần bỏ

- `getDeclaredFields()` và `getDeclaredMethods()` chỉ lấy thành viên **được khai báo trực tiếp** trong kiểu đang xét; chúng không tự thêm thành viên thừa kế. Mảng kết quả **không có thứ tự được đảm bảo**.
- `getFields()` và `getMethods()` trả các thành viên `public` phù hợp, kể cả thành viên kế thừa từ superclass/superinterface. Thứ tự của các mảng này cũng **không được đảm bảo**. Riêng `getMethods()` còn có quy tắc chọn các phương thức cụ thể hơn theo JDK, không nên suy luận chỉ bằng “phần tử đầu tiên”.
- `getDeclaredMethods()` có thể trả **bridge/synthetic method** do compiler tạo, đôi khi cùng tên + kiểu tham số nhưng khác kiểu trả về.
- `getDeclaringClass()` cho biết thành viên được **khai báo ở lớp nào**. Thông tin này rất quan trọng khi xử lý hiding, override, log hoặc cache metadata.
- `getParameterTypes()` phản ánh **kiểu tham số runtime sau type erasure**; `getGenericParameterTypes()` dành cho nhu cầu giữ metadata kiểu tổng quát. Không dùng riêng `getReturnType()` để phân biệt overload theo ngữ nghĩa Java.
- `getModifiers()`, `Modifier.isPublic(...)`, `Field.isSynthetic()`, `Method.isBridge()` và `Method.isSynthetic()` giúp lọc; nhưng tính `public` của member chưa đảm bảo có quyền gọi nếu **lớp khai báo/package/module** không truy cập được.

Ví dụ về kiểu trả về covariant: `Parent.score(): Number` và `Child.score(): Integer` có thể cần compiler tạo một **bridge method** ở `Child` để giữ tương thích sau biên dịch. Nếu registry lấy ngẫu nhiên phần tử đầu tiên của `Child.getDeclaredMethods()`, bộ dữ liệu có thể chứa cả method nguồn và bridge, hoặc chọn kiểu trả về không mong muốn.

## 3. Định nghĩa chính sách trước khi triển khai

Một chính sách minh họa cho **registry thuộc tính và hành vi công khai của class thông thường**:

1. **Đầu vào**: một `Class<?>` đại diện cho lớp cụ thể.
2. **Phạm vi**: chỉ thành viên `public instance` do class hiện tại và các superclass (trừ `Object`) khai báo; bỏ `static`, `synthetic` và `bridge`. Bỏ qua interface/default methods trong lượt quét đơn giản này.
3. **Thứ tự class**: đi từ lớp cụ thể lên superclass gần nhất rồi tiếp tục tới tổ tiên. *Closest declaration wins*.
4. **Thứ tự trong từng class**: sắp xếp field theo `Field.getName()` và method theo khóa chữ ký ổn định, tuyệt đối không dựa thứ tự mảng Reflection.
5. **Khóa của field**: `field.getName()`. Nếu tên trùng, giữ field của class cụ thể hơn. Quyết định này làm mất field cùng tên ở lớp cha **khỏi góc nhìn registry**, không xóa field trong object.
6. **Khóa của method**: `methodName(parameterType1,parameterType2,...)`, dùng danh sách **kiểu tham số runtime theo đúng thứ tự**. Không gộp các overload; không đưa return type vào khóa API nguồn. Nếu key trùng, giữ khai báo ở class cụ thể hơn.
7. **Xử lý xung đột**: ghi nhận cả “khai báo được chọn” lẫn “khai báo bị che/ghi đè” khi sản phẩm cần audit hoặc cảnh báo; quyết định rõ đây là chính sách cho schema, mapper hay dispatcher.

Đây là **quy ước của công cụ**, không phải một thuật toán tổng quát thay thế mọi quy tắc kế thừa của Java. Giới hạn `public instance` khiến ví dụ tránh nhiều trường hợp phức tạp về private, package-private khác package, protected, accessibility xuyên module, interface default/diamond và generics. Nếu công cụ thực sự phải xử lý các trường hợp đó, hãy bổ sung chính sách riêng; không mở rộng quyền một cách ngầm định.

## 4. Ví dụ Java 17+ chạy độc lập: quét có thứ tự và khóa rõ ràng

Lưu nội dung sau thành **`ToolPolicyDemo.java`**:

~~~java
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class ToolPolicyDemo {
    public interface Feature {
        default String tag() { return "interface"; }
    }

    public static class Parent {
        public String id = "parent";
        public String label() { return "parent"; }
        public Number score() { return 10; }
        public void update(String text) {}
    }

    public static class Child extends Parent implements Feature {
        public String id = "child";
        @Override public String label() { return "child"; }
        @Override public Integer score() { return 20; }
        public void update(int number) {}
    }

    static Map<String, Field> fields(Class<?> leaf) {
        Map<String, Field> result = new LinkedHashMap<>();

        for (Class<?> c = leaf; c != null && c != Object.class;
                c = c.getSuperclass()) {
            Arrays.stream(c.getDeclaredFields())
                    .filter(f -> Modifier.isPublic(f.getModifiers())
                            && !Modifier.isStatic(f.getModifiers())
                            && !f.isSynthetic())
                    .sorted(Comparator.comparing(Field::getName))
                    .forEach(f -> result.putIfAbsent(f.getName(), f));
        }
        return result;
    }

    static String key(Method m) {
        String params = Arrays.stream(m.getParameterTypes())
                .map(Class::getTypeName)
                .collect(Collectors.joining(","));
        return m.getName() + "(" + params + ")";
    }

    static Map<String, Method> methods(Class<?> leaf) {
        Map<String, Method> result = new LinkedHashMap<>();

        for (Class<?> c = leaf; c != null && c != Object.class;
                c = c.getSuperclass()) {
            Arrays.stream(c.getDeclaredMethods())
                    .filter(m -> Modifier.isPublic(m.getModifiers())
                            && !Modifier.isStatic(m.getModifiers())
                            && !m.isBridge() && !m.isSynthetic())
                    .sorted(Comparator.comparing(ToolPolicyDemo::key))
                    .forEach(m -> result.putIfAbsent(key(m), m));
        }
        return result;
    }

    public static void main(String[] args) throws Exception {
        Child child = new Child();
        Map<String, Field> fieldMap = fields(Child.class);
        Map<String, Method> methodMap = methods(Child.class);

        System.out.println("field owner: " +
                fieldMap.get("id").getDeclaringClass().getSimpleName());
        System.out.println("field values: " +
                fieldMap.get("id").get(child) + "/" + ((Parent) child).id);
        System.out.println("label owner: " +
                methodMap.get("label()").getDeclaringClass().getSimpleName());
        System.out.println("label call: " +
                methodMap.get("label()").invoke(child));
        System.out.println("score return type: " +
                methodMap.get("score()").getReturnType().getSimpleName());
        System.out.println("overloads kept: " +
                methodMap.containsKey("update(int)") + "/" +
                methodMap.containsKey("update(java.lang.String)"));
        System.out.println("bridge count: " +
                Arrays.stream(Child.class.getDeclaredMethods())
                        .filter(Method::isBridge).count());
        System.out.println("base Method.invoke: " +
                Parent.class.getDeclaredMethod("label").invoke(child));
        System.out.println("class-only sees interface default: " +
                methodMap.containsKey("tag()"));
        System.out.println("getMethod sees default: " +
                Child.class.getMethod("tag").invoke(child));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac ToolPolicyDemo.java
java ToolPolicyDemo
~~~

Kết quả khi biên dịch bằng `javac` Java 17+:

~~~text
field owner: Child
field values: child/parent
label owner: Child
label call: child
score return type: Integer
overloads kept: true/true
bridge count: 1
base Method.invoke: child
class-only sees interface default: false
getMethod sees default: interface
~~~

Đọc kết quả theo chính sách đã chọn:

- `field owner: Child`: registry lấy field gần lớp đích nhất theo tên `id`. Dòng kế tiếp chứng minh field `Parent.id` vẫn tồn tại, có giá trị khác; `Field.get(child)` đọc field của đúng lớp khai báo.
- `label owner: Child`: `putIfAbsent` giữ method override ở lớp con, vì quét `Child` trước `Parent`. `label call: child` phản ánh hành vi override.
- `score return type: Integer`: khai báo nguồn ở `Child` được chọn; bộ lọc `!m.isBridge()` bỏ method bridge mà `javac` tạo do kiểu trả về covariant.
- `overloads kept: true/true`: `update(int)` và `update(String)` có khóa khác nhau nên cả hai được giữ.
- `bridge count: 1`: demo làm hiện rõ bridge trong metadata của `Child`; đây là kết quả của ví dụ cụ thể khi dùng `javac`, không phải lời khẳng định mọi compiler hay mọi override đều phải sinh chính xác một bridge.
- `base Method.invoke: child`: chọn `Method` khai báo tại `Parent` **không** cố định triển khai `Parent` khi gọi instance method trên một `Child`; dynamic dispatch vẫn áp dụng.
- `class-only sees interface default: false`: thuật toán có chủ ý chỉ đi dọc superclass, chưa duyệt `Feature`; nhưng `Child.class.getMethod("tag")` tìm được default method public thừa kế từ interface và gọi được.

### Vì sao dùng `LinkedHashMap` và `putIfAbsent`?

`LinkedHashMap` giữ thứ tự chèn của các entry để kết quả lặp lại ổn định khi các đầu vào và chính sách không đổi. Quét từ lớp con lên lớp cha, dùng `putIfAbsent(key, member)` để khai báo đầu tiên (lớp cụ thể nhất) chiếm khóa. Sắp xếp **trước khi chèn** loại bỏ sự phụ thuộc vào thứ tự `getDeclaredFields()`/`getDeclaredMethods()` vốn không được đảm bảo.

Trong ví dụ, khóa chữ ký method là chuỗi để dễ quan sát. Ở một hệ thống xử lý nhiều class loader, hai lớp khác nhau có thể cùng tên nhị phân. Nếu tính duy nhất phải phản ánh **identity của `Class<?>`**, hãy dùng một key cấu trúc (ví dụ record chứa method name và `List<Class<?>>` parameter types) thay vì chỉ dựa vào `Class.getTypeName()`; chuẩn hóa thêm module/loader nếu lưu cache bền vững hoặc xuất metadata liên tiến trình.

## 5. Mở rộng chính sách theo nhu cầu của công cụ

### 5.1. Muốn giữ tất cả field bị hide

Nếu mục tiêu là debugger, ORM có metadata rõ ràng, serializer lossless hoặc công cụ kiểm tra dữ liệu ở mọi tầng kế thừa, chính sách “một tên một field” có thể **không đủ**.

Giải pháp: lưu `Map<DeclaringClassAndName, Field>` (hoặc danh sách các field cùng tên) với khóa cấu trúc gồm `field.getDeclaringClass()` và `field.getName()`. Sau đó ánh xạ sang tên hiển thị có namespace rõ ràng: `Parent#id` và `Child#id`. Nếu format đầu ra chỉ chấp nhận một khóa `id`, cần chính sách loại trừ hoặc báo collision có chủ đích, tránh âm thầm ghi đè giá trị cha.

Khi dùng annotation để đặt tên logic (ví dụ hai field vật lý khác tên nhưng cùng `@JsonProperty`), **collision của tên logic** còn khác collision của tên Java. Khóa, chính sách ưu tiên và bước phát hiện xung đột phải được xác định ở đúng tầng mapping.

### 5.2. Muốn hỗ trợ method từ interface và default method

Một registry chỉ quét superclass như ví dụ sẽ bỏ `default` method của interface. Có hai hướng:

- Với API public đã kế thừa, xem xét `Class.getMethods()` hoặc `getMethod(name, parameterTypes...)` để dựa vào phép phân giải public method mà Reflection cung cấp, rồi lọc/sắp xếp và xác định chính sách nhóm.
- Nếu cần biết **vị trí khai báo trong interface graph**, chủ động duyệt `getInterfaces()`, loại trùng interface, ưu tiên class method so với interface default theo quy tắc Java, rồi xử lý trường hợp nhiều interface liên quan. Sắp xếp tên/interface cho deterministic output, nhưng việc chọn triển khai phải tuân thủ quan hệ kế thừa/most-specific, **không dùng thứ tự alphabet thay cho ngữ nghĩa override**.

Ví dụ ở mục 4 cố tình cho thấy `methodMap` không chứa `tag()` còn `getMethod("tag")` thì chứa: đây là **giới hạn công khai của chính sách minh họa**, không phải lỗi của Reflection.

### 5.3. Static, private, protected, package-private và module

- Method `static` cùng chữ ký trong hai lớp được **hide**, không có dynamic dispatch. Nếu registry xử lý static factory, dùng nhóm và ưu tiên riêng; không nhập vào registry instance.
- Method `private` của cha không bị override; nếu công cụ cần đọc private, khóa phải giữ lớp khai báo hoặc phân biệt metadata, không cho method cùng tên ở con “thay thế” private cha theo suy luận override.
- `protected` và package-private phải xét **package, quan hệ kiểu và khả năng thực sự truy cập**. Package-private giữa hai package khác nhau không tự trở thành một quan hệ override.
- Ngoài `Modifier.isPublic`, module `exports`/`opens`, visibility của lớp khai báo và lookup context có thể quyết định khả năng sử dụng. `setAccessible(true)` có giới hạn trong Java 17+ với JPMS và có thể ném `InaccessibleObjectException`; không xem việc tìm được metadata là đủ quyền gọi.
- Constructor **không được kế thừa**, vì vậy không đưa `getDeclaredConstructors()` vào cùng thuật toán override/hiding.

Với công cụ xử lý rộng hơn ví dụ, cần kiểm tra accessibility bằng chính API được dùng (Reflection, `MethodHandles.Lookup`, `canAccess` khi có đối tượng phù hợp) và theo chính sách của sản phẩm. Nếu không đủ quyền, báo lỗi rõ ràng thay vì chọn một thành viên khác ngẫu nhiên.

### 5.4. Generic, bridge và kiểu trả về covariant

Java xác định overload chủ yếu từ **tên method và danh sách kiểu tham số**, không tạo overload mới chỉ nhờ kiểu trả về khác nhau. Ở bytecode/Reflection, compiler có thể thêm bridge sau type erasure hoặc khi return type covariant. Vì vậy:

1. Đối với **mô hình API ở cấp Java source**, thường nên lọc `isBridge()`/`isSynthetic()` trước khi nhóm.
2. Đối với **bytecode analyzer**, có thể cần giữ cả bridge, nhưng khóa lúc đó có thể bao gồm **return type** và thông tin declaring class, kèm cờ bridge/synthetic.
3. Với generic type metadata, phân biệt `getParameterTypes()` (erased runtime types) và `getGenericParameterTypes()`. Dữ liệu generic là phần mô tả cần lưu thêm; nó không phải lý do để chọn tùy ý một method trùng runtime signature.

Nếu library yêu cầu hỗ trợ bytecode không thông thường hoặc class sinh động, đừng giả định mọi trùng lặp đều là bridge; kiểm tra nhóm đã lọc và đưa ra quyết định rõ ràng khi vẫn còn collision.

## 6. Kiểm tra tính nhất quán trước khi đưa vào framework

Một bộ quét đáng tin cậy nên có các đặc điểm có thể kiểm chứng:

| Tiêu chí | Cách kiểm chứng |
| --- | --- |
| Kết quả độc lập với thứ tự mảng Reflection | Hoán vị danh sách đầu vào trước khi sort/group; kết quả chọn vẫn giống nhau |
| Field cùng tên không làm mất dữ liệu ngoài ý muốn | Có test `Parent.id`/`Child.id`, kiểm tra chính sách “flatten” hay “keep all” |
| Override khác overload | `label()` chỉ có một entry ở class gần nhất; `update(String)` và `update(int)` cùng tồn tại |
| Bridge không làm sai kiểu trả về của API nguồn | Thử override covariant; kiểm tra `isBridge()` và kiểu trả về của entry được chọn |
| Interface default được xử lý theo quy tắc đã công bố | Thử `getMethod`, hoặc kiểm tra rõ phạm vi class-only |
| Quyền truy cập được xử lý | Thử public trên kiểu không public, private across modules, và xử lý lỗi truy cập |
| Kết quả ổn định khi cache | Khóa cache gắn `Class<?>`/ClassLoader và chính sách lọc; không dùng chỉ tên class nếu có nhiều loader |
| Thành viên bị bỏ được giải thích | Với dữ liệu quan trọng, log khai báo gốc/chọn/bị bỏ theo chính sách, tránh silent collision |

Lỗi phổ biến cần tránh là đặt `Map.put(name, member)` trong vòng lặp không định nghĩa thứ tự: “lần cuối thắng” tùy thuộc thứ tự JVM trả về. Ngay cả `putIfAbsent` cũng chỉ đúng khi **đầu vào đã được sắp xếp và thứ tự lớp đã được thiết kế**.

**Ghi nhớ:** field hiding giữ **nhiều ô lưu trữ**; method overriding xác định **triển khai instance được gọi**. Registry Reflection phải nêu rõ sẽ giữ tất cả hay chọn một đại diện, với khóa và thứ tự ổn định. Chính sách minh họa dùng “lớp con trước, cùng class sort, field theo tên, method theo tên + tham số, lọc bridge/synthetic/static” để tạo một kết quả dễ dự đoán; khi thêm interface hoặc non-public member, phải mở rộng chính sách tương ứng.

**Tham khảo tài liệu Java SE 21:**
- [`Class`: `getDeclaredFields`, `getDeclaredMethods`, `getMethods`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html)
- [`Method`: `invoke`, `isBridge`, `isSynthetic`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Method.html)
- [`Field`: `get` và `getDeclaringClass`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Field.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tooling-design-006">Sắp xếp metadata để đầu ra có thứ tự ổn định</a>

<details>
<summary>Xem chi tiết</summary>

# Sắp xếp metadata Reflection để đầu ra có thứ tự ổn định

## 1. Vì sao cần sắp xếp metadata?

Công cụ sinh tài liệu, schema hay snapshot thường cần đầu ra lặp lại được. Tuy nhiên các mảng member từ `getDeclaredMethods()`, `getDeclaredFields()` và nhiều API Reflection khác **không cam kết thứ tự**. Hai lần sinh có thể cho danh sách giống nhau nhưng thứ tự khác, khiến bản diff báo nhầm thay đổi.

Đây là bài về **canonical output**: trước hết chọn đúng tập member, sau đó sắp xếp theo khóa đủ phân biệt overload, chuẩn hóa định dạng/encoding và bảo toàn những thứ tự có ngữ nghĩa (ví dụ vị trí tham số hay thứ tự thực thi được cấu hình). Sort metadata hiển thị **không tự xác định** thứ tự chạy các method.

## 2. Reflection trả về tập thành viên, không phải thứ tự trình bày

Các API nên phân biệt:

| API | Loại metadata | Phạm vi và điểm cần nhớ |
| --- | --- | --- |
| `getDeclaredFields()` | `Field[]` | Field khai báo ngay trong lớp, gồm cả non-public; không gồm field kế thừa |
| `getFields()` | `Field[]` | Field `public` nhìn thấy theo quy tắc Reflection, có thể gồm field kế thừa |
| `getDeclaredMethods()` | `Method[]` | Method khai báo ngay trong lớp, có thể gồm bridge/synthetic; không gồm method kế thừa |
| `getMethods()` | `Method[]` | Các method `public` của lớp/interface và method kế thừa theo quy tắc API |
| `getDeclaredConstructors()` | `Constructor<?>[]` | Constructor khai báo trong lớp; constructor không được kế thừa |
| `getInterfaces()` | `Class<?>[]` | Interface **trực tiếp** của lớp; Java phản ánh thứ tự khai báo, nhưng công cụ cần canonical order có thể chọn sort |
| `getDeclaredClasses()` | `Class<?>[]` | Các lớp/interface thành viên được khai báo, không bao gồm mọi lớp local/anonymous |
| `getDeclaredAnnotations()` | `Annotation[]` | Annotation khai báo trực tiếp, chịu quy tắc của Annotation API |

**Không phải mọi thông tin đều vô thứ tự:** chẳng hạn danh sách kiểu tham số trong chữ ký method và constructor phải giữ **đúng thứ tự khai báo**, vì `f(int, String)` khác `f(String, int)`. Khi muốn sắp xếp **các phương thức**, chỉ thay vị trí của *các bản ghi method*, không đảo các parameter bên trong từng chữ ký.

Đối với `getInterfaces()`, Java Reflection ghi nhận thứ tự interface trong phần `implements` / `extends`; nếu mục đích là báo lại cấu trúc source, có thể giữ thứ tự này. Nếu mục đích là so sánh *tập interface* không xét thứ tự khai báo, có thể sort theo tên như một quy tắc thiết kế của công cụ. **Chỉ sắp xếp khi ngữ nghĩa của dữ liệu cho phép.**

## 3. Quy tắc thiết kế khóa sắp xếp ổn định

Một bộ so sánh (comparator) tốt có ba đặc tính:

1. **Được xác định rõ:** key tạo từ metadata có nội dung ổn định trong cùng phạm vi đầu vào, ví dụ `Field.getName()`, `Method.getName()` và các kiểu tham số.
2. **Đủ phân biệt:** thêm khóa phụ nếu khóa đầu có thể trùng. Đặc biệt, tên phương thức **không đủ** vì Java cho phép overload.
3. **Có chính sách rõ về trường hợp còn trùng:** nếu hai mục xuất có cùng khóa, cần xác định chúng thực sự tương đương khi xuất hay bổ sung khóa phân biệt; đừng âm thầm dựa vào thứ tự mảng đầu vào.

Một lựa chọn thông dụng khi trình bày:

| Metadata | Khóa sắp xếp gợi ý | Lý do |
| --- | --- | --- |
| Field trong một lớp | tên → kiểu field → lớp khai báo | Tên dễ tìm; bổ sung khóa khi gộp nhiều lớp |
| Method | tên → danh sách **kiểu tham số có thứ tự** → kiểu trả về → lớp khai báo | Phân biệt overload và trường hợp bridge/covariant return |
| Constructor | danh sách kiểu tham số có thứ tự → lớp khai báo | Constructor không có tên riêng cho từng overload |
| Class hoặc interface | tên nhị phân (`Class.getName()`) | Dễ so sánh có phân biệt package và dạng tên lồng nhau |
| Annotation (khi chỉ liệt kê các loại) | tên kiểu annotation | Không phụ thuộc thứ tự mảng annotation |
| Tham số **bên trong** một method | giữ thứ tự gốc | Thứ tự tham số là thành phần của chữ ký |

### 3.1. Vì sao dùng `Class.getName()` trong khóa kỹ thuật?

`Class.getName()` cung cấp tên nhị phân với kiểu tham chiếu (ví dụ `java.util.Map$Entry`), và dạng được mã hóa với mảng (ví dụ `[Ljava.lang.String;`). Trong một phạm vi class loader xác định, nó phù hợp để xây key nhất quán. `Class.getSimpleName()` thì dễ trùng giữa các package; `getCanonicalName()` có thể trả `null` với lớp local/anonymous.

Khi **hiển thị cho người học/người dùng**, có thể dùng `Class.getTypeName()` để có dạng dễ đọc như `java.lang.String[]`. Nên **tách khóa kỹ thuật và chuỗi hiển thị**, tránh sửa cách sắp xếp chỉ vì định dạng giao diện thay đổi.

Hai đối tượng `Class` có cùng tên nhưng được **định nghĩa bởi hai class loader khác nhau** vẫn là hai kiểu runtime khác nhau. Nếu công cụ gộp dữ liệu của nhiều loader, chỉ dùng `getName()` không đủ để định danh toàn cục; cần cung cấp một **định danh namespace/loader ổn định từ cấu hình của công cụ**, không dùng `ClassLoader.toString()` hay `System.identityHashCode(...)` như khóa xuất tái lập giữa các lần chạy.

### 3.2. Vì sao method cần nhiều tiêu chí?

Một lớp có thể khai báo:

~~~java
void print(int number) {}
void print(String text) {}
~~~

Nếu chỉ dùng `Comparator.comparing(Method::getName)`, hai method đều có khóa `print`. Java `Arrays.sort(Object[], comparator)` là một phép sort ổn định: các phần tử có comparator kết quả `0` giữ thứ tự tương đối của input. Nhưng **input lấy từ Reflection vốn không được bảo đảm thứ tự**. Do đó kết quả vẫn không thể tái lập tuyệt đối.

Khóa gồm `getName()` + `getParameterTypes()` theo **thứ tự tham số** sẽ phân biệt hai overload. Thêm `getReturnType()` giúp phân biệt metadata liên quan bridge method hoặc kiểu trả về hiệp biến trong bytecode, vì JVM có thể có các method cùng tên/tham số nhưng khác return type, dù source Java không cho khai báo overload chỉ bằng return type. Khi gộp từ nhiều lớp, thêm `getDeclaringClass()`. Nếu bản ghi thực sự còn trùng khóa và bạn vẫn muốn xuất cả hai, phải thiết kế thêm bước canonical hóa/deduplication theo mục tiêu.

## 4. Ví dụ hoàn chỉnh Java 17+: sắp xếp field, method, constructor

Chương trình minh họa một lớp chứa field không theo thứ tự bảng chữ cái, method overload và nhiều constructor. Chúng ta **không in mảng Reflection thô** rồi suy đoán thứ tự. Thay vào đó dùng `Comparator` và chỉ in các bản ghi đã sort.

Tạo tệp **`StableReflectionReport.java`**:

~~~java
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

public class StableReflectionReport {
    public static class Sample {
        private int zeta;
        private String alpha;

        public Sample() {}

        public Sample(int initial) {
            zeta = initial;
        }

        public void ping() {}

        public String transform(String value) {
            return value;
        }

        public int transform(int value) {
            return value * 2;
        }
    }

    static String parameterKey(Class<?>[] parameterTypes) {
        return Arrays.stream(parameterTypes)
                .map(Class::getName)
                .collect(Collectors.joining(","));
    }

    static String parameterDisplay(Class<?>[] parameterTypes) {
        return Arrays.stream(parameterTypes)
                .map(Class::getTypeName)
                .collect(Collectors.joining(", "));
    }

    static final Comparator<Field> FIELD_ORDER =
            Comparator.comparing(Field::getName)
                    .thenComparing(f -> f.getType().getName())
                    .thenComparing(f -> f.getDeclaringClass().getName());

    static final Comparator<Method> METHOD_ORDER =
            Comparator.comparing(Method::getName)
                    .thenComparing(m -> parameterKey(m.getParameterTypes()))
                    .thenComparing(m -> m.getReturnType().getName())
                    .thenComparing(m -> m.getDeclaringClass().getName());

    static final Comparator<Constructor<?>> CONSTRUCTOR_ORDER =
            Comparator.<Constructor<?>, String>comparing(
                    c -> parameterKey(c.getParameterTypes()))
                    .thenComparing(c -> c.getDeclaringClass().getName());

    public static void main(String[] args) {
        Field[] fields = Sample.class.getDeclaredFields();
        Arrays.sort(fields, FIELD_ORDER);

        Method[] methods = Sample.class.getDeclaredMethods();
        Arrays.sort(methods, METHOD_ORDER);

        Constructor<?>[] constructors = Sample.class.getDeclaredConstructors();
        Arrays.sort(constructors, CONSTRUCTOR_ORDER);

        System.out.println("Fields:");
        for (Field field : fields) {
            System.out.println("  " + field.getName()
                    + ": " + field.getType().getTypeName());
        }

        System.out.println("Methods:");
        for (Method method : methods) {
            System.out.println("  " + method.getName() + "("
                    + parameterDisplay(method.getParameterTypes()) + ") -> "
                    + method.getReturnType().getTypeName());
        }

        System.out.println("Constructors:");
        for (Constructor<?> constructor : constructors) {
            System.out.println("  Sample("
                    + parameterDisplay(constructor.getParameterTypes()) + ")");
        }
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac StableReflectionReport.java
java StableReflectionReport
~~~

Kết quả:

~~~text
Fields:
  alpha: java.lang.String
  zeta: int
Methods:
  ping() -> void
  transform(int) -> int
  transform(java.lang.String) -> java.lang.String
Constructors:
  Sample()
  Sample(int)
~~~

**Giải thích:**

- `getDeclaredFields()`, `getDeclaredMethods()` và `getDeclaredConstructors()` là nguồn dữ liệu. Không trông chờ thứ tự mà chúng trả về.
- `Arrays.sort(fields, FIELD_ORDER)` đặt `alpha` trước `zeta`; `FIELD_ORDER` có khóa phụ là kiểu và lớp khai báo để có thể tái sử dụng cùng chính sách khi mở rộng bộ xuất.
- `METHOD_ORDER` đặt `ping` trước `transform`; hai overload `transform` được phân biệt nhờ **kiểu tham số**, nên `transform(int)` đứng trước `transform(String)` theo so sánh chuỗi tên kiểu.
- `CONSTRUCTOR_ORDER` so sánh danh sách tham số, trong đó chuỗi rỗng cho constructor không tham số xếp trước `int`.
- Dạng tên trong đầu ra dùng `getTypeName()` để người đọc dễ hiểu, còn comparator dùng `getName()` như key.
- Sort chỉ sắp xếp các **mảng `Field[]` / `Method[]` / `Constructor[]`** vừa lấy ra để xuất; không thay đổi thứ tự thành viên trong bytecode hay cách JVM gọi method.

Ví dụ giữ mọi method được khai báo trong lớp `Sample`. Trong công cụ thực tế cần một chính sách rõ về `private`, `public`, `synthetic` hoặc `bridge` trước khi sắp xếp.

## 5. Từ khóa sắp xếp đến đầu ra có thể tái lập

Thứ tự metadata là một phần của bài toán, nhưng pipeline xuất báo cáo cần xác định đủ các bước:

~~~text
Chọn đối tượng Class/nguồn dữ liệu
              ↓
Khám phá metadata đúng phạm vi
              ↓
Lọc/canonical hóa theo chính sách
              ↓
Tạo khóa sắp xếp có tiêu chí phụ
              ↓
Sort các bản ghi sẽ xuất
              ↓
Định dạng thống nhất (kiểu, annotation, ký tự)
              ↓
Ghi đầu ra với encoding/newline thống nhất
              ↓
So sánh snapshot hoặc tạo tài liệu
~~~

### 5.1. Chọn phạm vi trước khi sort

Câu hỏi đầu tiên là **công cụ cần khai báo của lớp hay API public nhìn thấy được?**

- `getDeclaredMethods()` thích hợp khi kiểm tra điều gì được khai báo **trực tiếp** trong lớp, kể cả private method và bridge/synthetic metadata.
- `getMethods()` thích hợp hơn khi trình bày các method `public` có thể nhìn thấy, kể cả kế thừa.
- Nếu muốn xem một hệ phân cấp có tính đến method `private` của superclass, phải duyệt superclass theo chính sách riêng; `getDeclaredMethods()` trên lớp con không tự đưa chúng vào.

Hai tập metadata khác nhau có thể có thứ tự ổn định nhưng **đầu ra mang ngữ nghĩa khác nhau**. Sort không bù được cho việc chọn sai tập nguồn.

### 5.2. Lọc bridge và synthetic với sự thận trọng

Compiler có thể tạo **bridge method** để phục vụ generics/covariant returns, và một số class có synthetic member. `Method.isBridge()` và `Method.isSynthetic()` giúp phát hiện các trường hợp đó:

~~~java
Method[] visible = Arrays.stream(type.getDeclaredMethods())
        .filter(m -> !m.isSynthetic() && !m.isBridge())
        .sorted(METHOD_ORDER)
        .toArray(Method[]::new);
~~~

*Đây là fragment giả định có `Class<?> type` và comparator `METHOD_ORDER` như ví dụ mục 4.*

Bộ lọc trên hữu ích nếu muốn tài liệu gần với **API mà lập trình viên thường chủ động viết**. Nhưng đối với công cụ kiểm tra bytecode, phân tích quá trình dispatch hoặc nghiên cứu generic bridge, việc loại bridge/synthetic sẽ **làm mất thông tin cần thiết**. Quyết định lọc phải gắn với mục đích, không dùng như công thức bắt buộc.

Đừng dựa vào `Method.toString()` hoặc `toGenericString()` làm khóa duy nhất khi bạn cần định danh có cấu trúc, vì định dạng hiển thị có thể thay đổi theo phiên bản/chi tiết API. Hãy truy xuất các thành phần bằng getter rồi tự xác định canonical representation.

### 5.3. Metadata generic và annotation

`Method.getParameterTypes()` cho các `Class<?>` sau khi xét erasure; `getGenericParameterTypes()` và `getGenericReturnType()` có thể biểu diễn `ParameterizedType`, `TypeVariable` hoặc `GenericArrayType`.

- Nếu mục đích là **khóa lời gọi runtime/JVM**, dùng các kiểu tham số đã erasure thường phù hợp.
- Nếu mục đích là **tạo tài liệu generic chính xác**, đưa thông tin generic vào **phần hiển thị**, và nếu cần so sánh thay đổi generic thì bổ sung cách chuẩn hóa `Type` để phân biệt.
- Khi xử lý annotation, việc chỉ sort các annotation theo `annotationType().getName()` có thể đủ cho bảng **liệt kê các loại annotation**. Nếu xuất cả giá trị phần tử annotation, cần canonical hóa giá trị theo tên phần tử; với array value phải giữ thứ tự có ngữ nghĩa. Không dựa vào chuỗi `annotation.toString()` như giao thức xuất dữ liệu ổn định.
- `Class.getDeclaredAnnotations()` khác `getAnnotations()` ở phạm vi kế thừa; trước khi sắp xếp phải quyết định có lấy annotation kế thừa theo `@Inherited` hay không.

Đối với cùng một annotation có nhiều phần tử (*elements*), các phương thức mô tả element được Reflection trả về trong mảng không cam kết thứ tự. Có thể sắp xếp `annotation.annotationType().getDeclaredMethods()` theo `Method.getName()` trước khi xuất tên và giá trị. Cần xử lý các giá trị là `Class`, enum, annotation lồng, mảng và kiểu primitive bằng quy tắc riêng để tránh ghi ra giá trị không chuẩn hóa.

## 6. Khi dữ liệu gồm nhiều lớp hoặc nhiều module

Một chương trình quét nhiều lớp rồi lấy metadata từ từng lớp cần ổn định **cả danh sách lớp đầu vào** lẫn metadata của từng lớp:

~~~java
List<Class<?>> orderedTypes = classes.stream()
        .sorted(Comparator.comparing(Class::getName))
        .toList();
~~~

*Fragment giả định `classes` là `Collection<Class<?>>` và đã có import của `List` / `Comparator`.*

Nếu danh sách lớp đến từ quét classpath, quét directory, ZIP/JAR hay `ServiceLoader`, thứ tự nguồn ban đầu có thể phụ thuộc môi trường. Hãy xác định rõ chính sách chọn lớp và sort tập đầu vào nếu thứ tự không có ý nghĩa nghiệp vụ. Nhưng **không** được tự thay đổi thứ tự có ý nghĩa, chẳng hạn các bộ xử lý thực thi theo priority, các bước migration hoặc danh sách handler yêu cầu precedence. Ổn định thứ tự tài liệu và ổn định thứ tự **thực thi** là hai mục tiêu khác nhau.

Đối với các module trong JPMS, có thể cần cùng với `Class.getName()` một định danh module ổn định (ví dụ tên module được khai báo, nếu có). Trong mô hình nhiều class loader, phải ghi thêm định danh *namespace* do chính hệ thống của bạn cấp và duy trì qua lần chạy. Một mô tả module/classloader **chỉ dựa trên địa chỉ đối tượng** hoặc thứ tự loader được tạo thường không ổn định cho snapshot.

Khi xuất thành danh sách JSON, thứ tự phần tử array là quan sát được nên nên xuất theo thứ tự đã định. Với object/map, cần quy định cách canonical hóa tên key nếu phụ thuộc vào thứ tự serialize; không mặc định mọi serializer hay mọi map giữ cùng thứ tự. Ví dụ dùng `TreeMap` với comparator xác định để quản lý key khi ngữ nghĩa cho phép, hoặc dùng một serializer hỗ trợ canonical output.

## 7. Những nguyên nhân khiến snapshot vẫn thay đổi sau khi sort

Sort metadata giúp loại bỏ khác biệt do thứ tự duyệt. Nó **không đảm bảo** đầu ra byte-for-byte giống nhau khi các yếu tố khác đã thay đổi:

| Nguồn biến động | Cách xử lý |
| --- | --- |
| Khác JDK/compiler làm phát sinh bridge/synthetic khác | Quy định môi trường xây dựng, chính sách lọc/hiển thị và phạm vi so sánh |
| Dữ liệu quét classpath/JAR đầu vào khác | Cố định nguồn dữ liệu và sort danh sách class khi phù hợp |
| `hashCode`, `identityHashCode`, `toString` của loader/đối tượng | Không sử dụng để định danh xuất báo cáo |
| Timestamp, đường dẫn tuyệt đối, UUID ngẫu nhiên | Bỏ khỏi phần dữ liệu canonical hoặc chuẩn hóa theo yêu cầu |
| Format chịu `Locale.getDefault()` | Dùng định dạng độc lập locale hoặc chỉ rõ `Locale.ROOT` khi thích hợp |
| Charset mặc định, CRLF/LF, dấu xuống dòng cuối | Ghi UTF-8 và quy định newline nhất quán |
| Annotation có mảng hoặc giá trị lồng nhau | Giữ thứ tự có ngữ nghĩa, chuẩn hóa key và cách serialize |
| Cùng tên class nhưng khác class loader | Bổ sung namespace ID ổn định khi hệ thống cần phân biệt |
| Thay đổi thực sự trong API, annotation, method signature | **Giữ lại để diff phát hiện**, không làm mất bằng sort |

Ví dụ khi ghi file một báo cáo đã chuẩn hóa:

~~~java
Files.writeString(
        outputPath,
        report.replace("\r\n", "\n"),
        StandardCharsets.UTF_8);
~~~

*Fragment cần import `java.nio.file.Files` và `java.nio.charset.StandardCharsets`; `outputPath` là `Path` và `report` là `String` đã được định nghĩa. Với quy trình cần chuẩn hóa mọi dạng newline, xử lý cả `\r` đơn lẻ tùy nguồn dữ liệu.*

Quy định cả **trailing newline** và encoding trong hợp đồng xuất sẽ giảm những diff không liên quan nội dung. Nếu báo cáo là định dạng dùng lâu dài, hãy công bố version của quy tắc xuất; đổi key sort là đổi hành vi tiêu thụ dữ liệu dù API Java bên dưới không đổi.

## 8. Kiểm tra tính ổn định một cách có ý nghĩa

Đừng kiểm thử bằng giả định rằng `getDeclaredMethods()` luôn trả đúng một chuỗi cố định. Hãy kiểm thử **tính bất biến dưới phép hoán vị**: dù nhận danh sách các bản ghi theo thứ tự đầu vào nào, output chuẩn hóa phải giống nhau.

Ví dụ cách kiểm tra tổng quát:

~~~java
List<Method> input = new ArrayList<>(
        Arrays.asList(Sample.class.getDeclaredMethods()));

List<String> first = input.stream()
        .sorted(METHOD_ORDER)
        .map(StableReflectionReport::methodKeyForTest)
        .toList();

Collections.reverse(input);

List<String> reversed = input.stream()
        .sorted(METHOD_ORDER)
        .map(StableReflectionReport::methodKeyForTest)
        .toList();

if (!first.equals(reversed)) {
    throw new AssertionError("Metadata output depends on input order");
}
~~~

*Đây là minh họa kiểm thử tích hợp; cần các import `List`, `ArrayList`, `Arrays`, `Collections`, `Method`. `methodKeyForTest` là hàm do công cụ định nghĩa để xuất khóa chuẩn hóa; không phải phương thức sẵn có của Java. Với ví dụ hoàn chỉnh ở mục 4, có thể xây hàm này từ tên method, danh sách kiểu tham số, kiểu trả về và declaring class.*

Nên bổ sung trường hợp:

1. Hai overload cùng tên (`f(int)` và `f(String)`), để bảo đảm key không chỉ là tên.
2. Method bridge và method gốc, nếu công cụ xuất cả hai.
3. Hai field cùng tên khi dữ liệu gộp superclass và subclass.
4. Mảng parameter và lớp lồng nhau trong key.
5. Annotation element xuất từ một mảng method không có thứ tự được cam kết.
6. Hai nguồn metadata có tên giống nhau nhưng khác namespace/loader, nếu hệ thống của bạn hỗ trợ nhiều loader.

Phép kiểm tra đảo ngược đầu vào chứng minh một phần quan trọng: comparator đủ phân biệt các mục đang test. Nó **không thay thế** việc kiểm soát phiên bản Java, classpath, cấu trúc JSON, encoding hay chính sách lọc.

## 9. Các lỗi thường gặp

**“getDeclaredMethods() trả về thứ tự khai báo trong source.”** Không có bảo đảm đó. Đừng dùng chỉ số của mảng trả về làm số thứ tự tài liệu ổn định.

**“Chỉ sort theo method name là đủ.”** Không đủ với overload hoặc bridge. Khi key trùng, sort ổn định vẫn giữ thứ tự không ổn định của input.

**“Sort `getParameterTypes()` của từng method cho chắc.”** Sai. Thứ tự parameter **là một phần của chữ ký**; `f(int, String)` khác `f(String, int)`.

**“Dùng `Class.getSimpleName()` có thể định danh duy nhất.”** Không thể nếu có `a.User` và `b.User`. `Class.getName()` an toàn hơn trong một namespace nhưng cần thêm chính sách với nhiều loader.

**“Bỏ mọi synthetic method luôn đúng.”** Không đúng khi công cụ có trách nhiệm quan sát bytecode hoặc bridge cần thiết cho dispatch.

**“Sắp xếp metadata sẽ làm chương trình chạy đúng thứ tự method.”** Sort chỉ tác động tới trình bày/duyệt dữ liệu của công cụ. Nếu thứ tự thực thi có ngữ nghĩa, phải định nghĩa thứ tự đó riêng bằng cấu hình, annotation priority hoặc chính sách điều phối rõ ràng.

**“Cứ gọi `Method.toString()` rồi sort là canonical.”** Có thể dùng cho log đơn giản, nhưng khi xây giao thức ổn định nên dùng các getter và quy ước key có cấu trúc, tự kiểm soát định dạng.

**“Snapshot lệch sau khi sort nghĩa là comparator lỗi.”** Chưa chắc: classpath, compiler/JDK, annotation, thời gian, locale hay newline cũng có thể làm khác đầu ra.

## 10. Tự kiểm tra

1. Vì sao không in trực tiếp `Class.getDeclaredMethods()` để làm snapshot? **API không cam kết thứ tự.**
2. Vì sao khóa `Method.getName()` chưa đủ? **Overload có thể trùng tên; cần thêm kiểu tham số theo thứ tự, và thường thêm return/declaring type.**
3. Khi sắp xếp các method, có nên sort lại danh sách tham số bên trong từng method? **Không**, như vậy đổi ý nghĩa chữ ký.
4. Tại sao dùng `Method.isBridge()` / `isSynthetic()` phải phụ thuộc mục đích? **Vì một số công cụ muốn báo cáo API source-level, còn công cụ bytecode cần giữ metadata do compiler sinh.**
5. Hai `Class` cùng `getName()`, khác loader, có chắc là một kiểu không? **Không.**
6. Để tạo output ổn định, ngoài comparator cần kiểm soát gì? **Nguồn dữ liệu, chính sách lọc, format/generic/annotation, locale, encoding, newline và dữ liệu phụ thuộc môi trường.**
7. Nếu đầu vào đảo ngược mà output được sort vẫn khác, bạn nên kiểm tra gì đầu tiên? **Có các phần tử khác nhau nhưng comparator trả `0` hay không, và đã chuẩn hóa phần hiển thị chưa.**

## 11. Kết luận

Reflection giúp khám phá metadata ở runtime nhưng không hứa thứ tự trả về của nhiều API member. Để thiết kế công cụ xuất **ổn định và dễ kiểm chứng**, hãy xác định tập dữ liệu đúng, lọc theo mục đích, tạo key sort đủ phân biệt (đặc biệt với method overload), giữ nguyên thứ tự parameter mang ngữ nghĩa, rồi chuẩn hóa format và môi trường xuất. Một comparator tốt loại bỏ nhiễu do thứ tự liệt kê; tính tái lập đầy đủ còn cần một **hợp đồng canonical output** cho toàn pipeline.

### Tài liệu tham khảo

- [Java SE 21 — `Class`: các phương thức khám phá member](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html)
- [Java SE 21 — `Method`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Method.html)
- [Java SE 21 — `Field`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Field.html)
- [Java SE 21 — `Constructor`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Constructor.html)
- [Java SE 21 — `Comparator`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Comparator.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tooling-design-007">Xác thực kiểu và khả năng truy cập trước khi chạy hàng loạt</a>

<details>
<summary>Xem chi tiết</summary>

# Xác thực kiểu và khả năng truy cập trước khi chạy hàng loạt bằng Java Reflection

Một công cụ batch dùng Reflection có thể nhận nhiều class/method từ cấu hình. Nếu vừa tìm thấy đã `invoke`, mục thứ bảy sai chữ ký có thể bị phát hiện **sau khi sáu mục trước đã tạo side effect**. Bài này xây bước **preflight toàn lô**: kiểm tra contract, receiver, chữ ký, quyền truy cập và đầu vào; tạo kế hoạch đã xác thực rồi mới chạy.

Preflight chỉ chặn những lỗi có thể phát hiện sớm. Nó không bảo đảm method đích không ném lỗi, không tự cung cấp transaction hay rollback và không thay thế authorization tại thời điểm thực thi. Các phần dưới tập trung vào độ chính xác của kiểm tra `isAssignableFrom`, `canAccess`, và cách giữ plan/caller nhất quán.

## 1. Bài toán: tránh thực thi nửa chừng vì lỗi có thể phát hiện trước

Giả sử một batch nhận ba bước:

1. `TextNormalizer.normalize(String)` trả về `String`.
2. `EmailValidator.validate(String)` trả về `String`.
3. `ReportExporter.export(int)` nhưng runner sẽ truyền `String`.

Cách thực thi ngay khi duyệt:

```text
for từng mục:
    tìm method từ metadata
    gọi method ngay
    nếu lỗi thì dừng
```

Khi mục thứ ba sai chữ ký, chương trình mới phát hiện lỗi sau khi hai mục đầu đã chạy. Hậu quả có thể là gửi email, ghi file, thay đổi DB hoặc phát sinh sự kiện mà không thể đảo ngược dễ dàng.

Cách **lập kế hoạch trước khi gọi**:

```text
[Input/cấu hình]
       |
       v
[Tra cứu class, method, constructor]
       |
       v
[Kiểm tra kiểu + chữ ký + receiver + quyền truy cập]
       |
       +---- Có lỗi? ----> [Từ chối cả lô, báo mục lỗi, chưa invoke]
       |
       v
[Tạo danh sách lời gọi đã xác thực]
       |
       v
[Thực thi theo chính sách: tuần tự / song song / retry / transaction]
       |
       v
[Thu thập kết quả và xử lý lỗi runtime]
```

**Nguyên tắc:** lỗi cấu hình hoặc mismatch Reflection có thể biết trước nên được phát hiện trước lời gọi đầu tiên. Lỗi bên trong code người dùng, race condition, I/O, timeout hoặc thay đổi hệ thống vẫn phải xử lý ở pha thực thi.

## 2. Các tầng kiểm tra cần có

| Tầng | Điều cần chứng minh | API gợi ý | Nếu sai |
| --- | --- | --- | --- |
| Kiểu đầu vào | Giá trị không `null`; class thuộc nhóm được phép | `Objects.requireNonNull`, `Class.isInstance`, allowlist | Từ chối item |
| Quan hệ kế thừa / interface | Class là một implementation của contract cần thiết | `RequiredType.class.isAssignableFrom(candidateClass)` | Từ chối item |
| Metadata thành viên | Method/constructor tồn tại và được chọn duy nhất | `getMethod`, `getDeclaredMethod`, `getDeclaredMethods`, `getDeclaredConstructor` | Thiếu/trùng thì từ chối |
| Chữ ký | Số lượng và kiểu tham số, return type, static/instance, varargs khớp contract | `getParameterTypes`, `getReturnType`, `Modifier`, `isVarArgs` | Từ chối trước `invoke` |
| Quyền truy cập | Caller có thể gọi method với receiver thực tế không? | `Method.canAccess(receiver)` | Từ chối hoặc áp dụng chính sách mở quyền có kiểm soát |
| Quy tắc nghiệp vụ | Class, package, annotation, hành động có được phép chạy không? | allowlist, registry, annotation được giữ tại runtime, quyền người dùng | Từ chối dù Java reflection có thể gọi |
| Kế hoạch thực thi | Mọi mục được xác thực trước khi side effect bắt đầu | danh sách bất biến chứa `target` và `Method` | Không tạo kế hoạch nếu có lỗi |

Quan trọng: **“có thể truy cập theo ngôn ngữ Java” không đồng nghĩa “được phép chạy theo nghiệp vụ”**. Một method `public` có thể hợp lệ với Reflection nhưng vẫn nguy hiểm nếu nhận từ cấu hình không đáng tin cậy. Phải xác thực cả quyền và danh sách chức năng được cho phép.

## 3. Xác thực kiểu: hiểu đúng `isInstance`, `isAssignableFrom` và chữ ký

### 3.1. Kiểu đối tượng thực tế

- `Expected.class.isInstance(value)` kiểm tra một **đối tượng** có phải instance của kiểu yêu cầu không; với `null` kết quả là `false`.
- `Expected.class.isAssignableFrom(Candidate.class)` kiểm tra một **kiểu** có thể gán vào biến khai báo `Expected` hay không, kể cả quan hệ extends/implements.
- Hướng gọi không được đảo: `Runnable.class.isAssignableFrom(MyRunnable.class)` có thể đúng, nhưng `MyRunnable.class.isAssignableFrom(Runnable.class)` thường sai.
- Khi chỉ cần kiểm tra một đối tượng theo interface có sẵn, `value instanceof MyInterface` hoặc `Expected.class.isInstance(value)` thường rõ nghĩa hơn.

Ví dụ:

```java
interface Task {}
class EmailTask implements Task {}

boolean a = Task.class.isAssignableFrom(EmailTask.class); // true
boolean b = EmailTask.class.isAssignableFrom(Task.class); // false
boolean c = Task.class.isInstance(new EmailTask());       // true
```

Khi định chạy nhiều plugin, hãy định nghĩa contract ổn định như interface marker, base class hoặc registry, rồi kiểm tra contract trước khi tìm thành viên động. Trường hợp API nghiệp vụ đã có interface với phương thức phù hợp, **gọi trực tiếp qua interface thường đơn giản và an toàn kiểu hơn Reflection**; chỉ dùng Reflection cho phần khám phá/cấu hình động thật sự cần.

### 3.2. Kiểu tham số và return type

Phương thức cùng tên không nhất thiết cùng chữ ký:

```java
void process(String text) {}
void process(Integer number) {}
String process(String text, int retry) { return text; }
```

Đoạn trên chỉ minh họa overload hợp lệ; runner yêu cầu `process(String)` trả `String` thì phải kiểm tra **cả parameter types lẫn return type**, bởi Reflection không tự buộc kiểu kết quả cho kế hoạch:

```java
// Giả sử method là java.lang.reflect.Method đã tìm được.
boolean takesExactlyOneString =
        java.util.Arrays.equals(
                method.getParameterTypes(),
                new Class<?>[]{String.class});
boolean returnsString = method.getReturnType() == String.class;
```

Trong contract có chữ ký **chính xác**, so sánh `Class<?>` bằng `==` là đúng vì đối tượng `Class` đại diện cho runtime type; điều này khác so sánh nội dung đối tượng dữ liệu.

Trong contract cho phép kiểu con, sử dụng `isAssignableFrom` theo chiều phù hợp và xét cả primitive, boxing/unboxing, `null`, varargs. Đừng dùng `String.class.isAssignableFrom(paramType)` một cách vô thức: API nhận `CharSequence` vẫn có thể tiếp nhận chuỗi `String`, dù hướng kiểm tra đó trả `false`.

Với generic như `List<String>`, `getParameterTypes()` nhìn thấy `List.class` do **type erasure**. Nếu batch cần ràng buộc generic, có thể xem `getGenericParameterTypes()` nhưng metadata generic **không tự kiểm tra từng phần tử lúc runtime**; cần validator riêng.

### 3.3. Static / instance, receiver và varargs

`Method.invoke` có cách truyền receiver khác nhau:

| Dạng thành viên | Receiver cho `invoke` / `canAccess` | Cần kiểm tra |
| --- | --- | --- |
| Instance method | Một object đúng kiểu declaring class hoặc subtype | Receiver không `null`, `declaringClass.isInstance(receiver)` |
| Static method | `null` | `Modifier.isStatic(method.getModifiers())` |
| Constructor | Không dùng `Method.invoke`; dùng `Constructor.newInstance` | Kiểu, đối số và quyền constructor |
| Varargs method | Có tham số mảng cuối | `method.isVarArgs()` và chính sách đóng gói đối số |

Ví dụ, `canAccess(null)` không hợp lệ với một instance method. Đối với static method, `canAccess(nonNullReceiver)` cũng sai. Hãy xác định loại thành viên trước khi gọi kiểm tra quyền.

**Varargs** cần thiết kế rõ: ở Reflection, `Method.invoke` có tham số varargs của chính API đó, và method đích có thể là varargs. Nếu runner không hỗ trợ, hãy từ chối `isVarArgs() == true`; nếu có hỗ trợ thì chủ động đóng gói thành mảng đúng kiểu để tránh truyền sai.

### 3.4. Kiểm tra một hay nhiều method khi quét annotation

Đối với công cụ quét `@BatchAction`:

- `getDeclaredMethods()` chỉ lấy method **khai báo trực tiếp trên class đó**; bao gồm non-public, không tự tìm inherited methods.
- `getMethods()` trả các method `public` (kể cả được kế thừa), theo chính sách khác.
- Khi quét annotation, cần quyết định áp dụng annotation trên declared hay inherited method, xử lý overload ra sao, có cho nhiều entry point trên cùng class không.
- Chú ý method `synthetic` hoặc `bridge` do compiler sinh, nhất là class generic. Có thể loại bỏ bằng `isSynthetic()`/`isBridge()` khi quét entry point.
- Annotation dùng để tra cứu tại runtime phải được khai báo `@Retention(RetentionPolicy.RUNTIME)`; mặc định không giữ đủ cho kiểu quét này.

Nếu contract yêu cầu **đúng một** method có annotation, cần xác nhận `count == 1` trước khi thực thi. Không chọn ngẫu nhiên “method đầu tiên” vì thứ tự `getDeclaredMethods()` không phải contract có thứ tự ổn định.

## 4. Xác thực khả năng truy cập: `canAccess` và giới hạn JPMS

Tìm được metadata không có nghĩa là gọi được member.

### 4.1. `public` là điều kiện đơn giản, chưa phải toàn bộ câu chuyện

`Modifier.isPublic(method.getModifiers())` chỉ nhìn access modifier của method. Với method thực tế, khả năng gọi còn phụ thuộc declaring class, package, module và caller.

`Method.canAccess(receiver)` hỏi quyền truy cập **từ class đang gọi API kiểm tra đó**, với receiver cụ thể. Nên gọi nó trong cùng lớp/tầng executor sau này thực hiện `invoke`:

```java
// Giả sử method là instance method và target đã xác minh đúng kiểu.
if (!method.getDeclaringClass().isInstance(target)
        || !method.canAccess(target)) {
    throw new IllegalArgumentException("Method không truy cập được");
}
```

Không nên áp dụng `canAccess` của một helper ở module khác rồi mặc nhiên cho rằng executor có quyền như vậy. Với phương thức static, phải truyền `null`. `canAccess` không chạy nội dung method đích.

### 4.2. `getMethod` và `getDeclaredMethod` khác nhau

- `getMethod("run", String.class)` tìm method `public` theo tên và kiểu tham số, bao gồm method phù hợp được kế thừa.
- `getDeclaredMethod("run", String.class)` tìm method khai báo trong class hiện tại, bất kể private/protected/package-private/public; **không** tự bao gồm inherited.
- Cả hai có thể ném `NoSuchMethodException`; nên chuyển thành lỗi cấu hình có ngữ cảnh rõ “class nào, method nào, chữ ký nào”.

Dùng `getDeclaredMethod` để tìm private method **không cấp thêm quyền** để gọi nó.

### 4.3. `trySetAccessible`, `setAccessible` và module

`trySetAccessible()` có thể bật chế độ bỏ qua access checks **nếu** môi trường/module cho phép; nếu không cho phép, thường trả `false`. `setAccessible(true)` có thể ném `InaccessibleObjectException` khi không thể mở quyền. Đây không phải các API để “phá” mọi ranh giới module.

Với module có tên (Java Platform Module System):

- `exports p` cho phép API public trong package `p` được truy cập thông thường bởi module đọc nó.
- `opens p` hoặc `opens p to other.module` cho phép các cơ chế deep reflection phù hợp trên package đó.
- `privateLookupIn` với `MethodHandles.Lookup` cũng phải tuân theo các điều kiện access/module; không có quyền tự động chỉ vì biết tên method.

Với **công cụ chạy batch**, mặc định nên từ chối nếu `canAccess` trả `false`, và mô tả cách sửa bằng public API, module descriptor hoặc plugin contract. Chỉ cân nhắc `trySetAccessible` khi đó là chính sách đã được chủ động phê duyệt, đối tượng nằm trong phạm vi được tin cậy, và có kiểm soát quyền rõ ràng.

## 5. Ví dụ Java 17+: xác thực cả lô rồi mới thực thi

Ví dụ tạo một batch runner đọc danh sách object theo contract `Job`, tìm đúng **một** method `@BatchAction` trên mỗi class, yêu cầu chữ ký chính xác `public String execute(String)` và chỉ thực thi **sau khi mọi bước đã được xác minh**.

Đặc điểm thiết kế:

- `preflight` chỉ kiểm tra metadata và tạo `List<Invocation>`; **không gọi** business method.
- Một method có return type `int` sẽ bị từ chối trước khi bất kỳ `execute` nào chạy.
- `List.copyOf` tạo danh sách kế hoạch bất biến về cấu trúc; điều này **không** làm các object mục tiêu trở thành immutable.
- `runs` minh chứng rõ lô sai không có side effect từ business method.

Tạo file **`BatchPreflightDemo.java`**:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class BatchPreflightDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface BatchAction {}

    public interface Job {}

    public static class Upper implements Job {
        @BatchAction
        public String execute(String text) {
            runs++;
            return text.toUpperCase(Locale.ROOT);
        }
    }

    public static class Prefix implements Job {
        @BatchAction
        public String execute(String text) {
            runs++;
            return "OK: " + text;
        }
    }

    public static class Wrong implements Job {
        @BatchAction
        public int execute(String text) {
            runs++;
            return text.length();
        }
    }

    private record Invocation(Object target, Method method) {}
    private static int runs;

    private static List<Invocation> preflight(List<?> targets) {
        List<Invocation> plan = new ArrayList<>();
        for (Object target : targets) {
            if (target == null) {
                throw new IllegalArgumentException("Đối tượng null");
            }
            Class<?> type = target.getClass();
            if (!Job.class.isAssignableFrom(type)) {
                throw new IllegalArgumentException(type.getSimpleName()
                        + " không implements Job");
            }
            List<Method> methods = Arrays.stream(type.getDeclaredMethods())
                    .filter(m -> m.isAnnotationPresent(BatchAction.class))
                    .toList();
            if (methods.size() != 1) {
                throw new IllegalArgumentException(type.getSimpleName()
                        + " phải có đúng một @BatchAction");
            }
            Method method = methods.get(0);
            if (!Modifier.isPublic(method.getModifiers())
                    || Modifier.isStatic(method.getModifiers())
                    || method.isSynthetic()
                    || method.isBridge()
                    || method.isVarArgs()) {
                throw new IllegalArgumentException(type.getSimpleName()
                        + ".execute phải là instance method public hợp lệ");
            }
            if (!Arrays.equals(method.getParameterTypes(),
                    new Class<?>[]{String.class})) {
                throw new IllegalArgumentException(type.getSimpleName()
                        + ".execute phải nhận String");
            }
            if (method.getReturnType() != String.class) {
                throw new IllegalArgumentException(type.getSimpleName()
                        + ".execute phải trả về String");
            }
            if (!method.getDeclaringClass().isInstance(target)
                    || !method.canAccess(target)) {
                throw new IllegalArgumentException(type.getSimpleName()
                        + ".execute không thể truy cập");
            }
            plan.add(new Invocation(target, method));
        }
        return List.copyOf(plan);
    }

    private static void execute(List<Invocation> plan, String input)
            throws IllegalAccessException {
        for (Invocation step : plan) {
            try {
                String result = (String) step.method().invoke(
                        step.target(), input);
                System.out.println(step.target().getClass().getSimpleName()
                        + " -> " + result);
            } catch (InvocationTargetException ex) {
                throw new IllegalStateException("Lỗi khi thực thi "
                        + step.target().getClass().getSimpleName(),
                        ex.getCause());
            }
        }
    }

    public static void main(String[] args) throws IllegalAccessException {
        try {
            List<Invocation> invalid =
                    preflight(List.of(new Upper(), new Wrong()));
            execute(invalid, "xin chào");
        } catch (IllegalArgumentException ex) {
            System.out.println("Từ chối lô: " + ex.getMessage());
        }
        System.out.println("Số lần đã chạy: " + runs);

        List<Invocation> valid =
                preflight(List.of(new Upper(), new Prefix()));
        System.out.println("Preflight: " + valid.size() + " bước hợp lệ");
        execute(valid, "xin chào");
        System.out.println("Số lần đã chạy: " + runs);
    }
}
```

Biên dịch và chạy:

```bash
javac BatchPreflightDemo.java
java BatchPreflightDemo
```

Kết quả chạy:

```text
Từ chối lô: Wrong.execute phải trả về String
Số lần đã chạy: 0
Preflight: 2 bước hợp lệ
Upper -> XIN CHÀO
Prefix -> OK: xin chào
Số lần đã chạy: 2
```

Giải thích:

1. `Wrong.execute(String)` có return type `int`. `getReturnType() != String.class` nên `preflight` ném `IllegalArgumentException` **trước** `execute(invalid, ...)`. Biến `runs` vẫn bằng 0.
2. Lô thứ hai gồm `Upper` và `Prefix`: mỗi class có đúng một method được đánh dấu annotation với signature đã yêu cầu, và `canAccess(target)` trả `true`.
3. Chỉ sau khi `preflight` trả đầy đủ kế hoạch, `execute` mới gọi `Method.invoke` từng bước. Kết quả đúng với chương trình đã biên dịch và chạy bằng JDK.
4. Trường hợp này cố ý dùng các nested class công khai cùng chương trình để ví dụ độc lập, nên không minh họa lỗi truy cập giữa hai named modules. Khi tích hợp plugin thật, hãy xác minh `canAccess` trong môi trường triển khai đó.
5. Ví dụ coi các method có annotation **khai báo trực tiếp** là entry point, không xét method được kế thừa. Nếu muốn hỗ trợ inherited methods, phải thay đổi chính sách tra cứu rõ ràng.

## 6. Khi dữ liệu cấu hình là tên class hoặc tên method

Một số công cụ nhận cấu hình kiểu:

```text
implementation = "com.acme.plugins.ExportJob"
method         = "execute"
parameterType  = "java.lang.String"
```

Đây là **dữ liệu đầu vào cần xác thực**, không phải giấy phép gọi phương thức bất kỳ.

Trình tự gợi ý:

1. **Xác thực người yêu cầu, nguồn cấu hình và allowlist**: chỉ cho những plugin/class/action được chấp nhận; không cho người dùng tùy ý nhập class hệ thống hoặc method nguy hiểm.
2. **Nạp class có kiểm soát**: chọn `ClassLoader` phù hợp; `Class.forName(name, false, loader)` có thể nạp class mà chưa yêu cầu khởi tạo ngay. Đừng hiểu bước nạp class là hoàn toàn không có rủi ro.
3. **Kiểm tra class**: `expectedInterface.isAssignableFrom(candidate)`, không abstract/interface nếu cần tạo instance, loại bỏ class trái chính sách.
4. **Kiểm tra constructor**: tìm chữ ký phù hợp bằng `getDeclaredConstructor(...)`; kiểm tra quyền với `constructor.canAccess(null)`. Nếu tạo instance bằng constructor, cần biết bản thân `newInstance` có thể chạy code và có side effect; tùy công cụ, nên trì hoãn tạo instance tới pha execution hoặc thiết kế nhà máy plugin riêng.
5. **Kiểm tra method**: tên, overload, tham số, return type, modifier, annotation, receiver, quyền truy cập, context module.
6. **Xây dựng kế hoạch**: lưu metadata và đối số đã chuyển đổi/hợp lệ. Đánh dấu nguồn gốc cấu hình để báo lỗi chính xác.
7. **Execute**: chạy theo thứ tự xác định, xử lý failure policy, audit/log, transaction khi cần.

**Lưu ý về constructor:** Nếu muốn thật sự “không side effect trước khi mọi mục qua preflight”, không nên gọi constructor của plugin trong quá trình kiểm tra: constructor hoặc static initializer có thể thực hiện hành vi nghiệp vụ. Ví dụ ở mục 5 nhận **instance đã tạo sẵn** và chỉ bảo đảm chưa gọi method `@BatchAction` trước khi kiểm tra đủ; quá trình tạo những instance ấy nằm ngoài preflight.

### Nên kiểm tra trước bao nhiêu?

Preflight nên xác minh các điều kiện **có thể quyết định từ metadata và input**: thiếu method, sai chữ ký, quyền access không phù hợp, input không thể chuyển đổi, plugin chưa đăng ký, cấu hình xung đột, method bị cấm. Nó không thể dự đoán mọi exception của code đích hay tình trạng tài nguyên lúc thực thi.

Nếu batch liên quan file/DB/network, có thể bổ sung bước kiểm tra resource và quyền nghiệp vụ, nhưng đó cũng chỉ là kiểm tra tại một thời điểm. Tài nguyên có thể biến mất ngay sau khi kiểm tra; executor vẫn phải xử lý lỗi đầy đủ.

## 7. Preflight không phải transaction và cũng không loại bỏ mọi lỗi Reflection

### Những gì có thể sai sau khi kiểm tra thành công

- Method có thể ném exception do logic bên trong; `Method.invoke` bọc exception từ target trong `InvocationTargetException`. Muốn xem lỗi gốc dùng `getCause()`.
- Receiver bị thay đổi trạng thái hoặc resource không còn hợp lệ.
- Method `public` vẫn có thể ném `IllegalArgumentException` do đối số truyền tại thời điểm chạy khác với bản đã xác thực.
- Ứng dụng có thể chạy sai thứ tự, bị hủy, timeout hoặc thực thi trùng khi retry.
- Class loader/redeployment tạo ra class cùng tên nhưng **khác identity**; không coi chuỗi tên class là bảo chứng rằng object thuộc đúng `Class<?>` đã kiểm tra.
- Các instance có thể giữ trạng thái nội bộ thay đổi trong lúc chờ. Kế hoạch bất biến chỉ khóa danh sách bước, không khóa thế giới bên ngoài.

Vì vậy, khi batch cập nhật dữ liệu cần có chính sách rõ ràng: **fail-fast**, ghi nhận lỗi từng item, retry có giới hạn, idempotency key, bù trừ (*compensation*) hoặc transaction database nếu thích hợp. Transaction nhiều tài nguyên phân tán không được tạo ra tự động chỉ vì preflight hoàn tất.

### Phân loại lỗi để báo cáo chính xác

| Tại bước nào | Lỗi/API thường gặp | Cách báo phù hợp |
| --- | --- | --- |
| Tìm class | `ClassNotFoundException` hoặc lỗi liên quan class loading | Nêu tên/loader và cấu hình sai |
| Tìm member | `NoSuchMethodException`, `NoSuchFieldException` | Nêu tên và signature mong muốn |
| Quyền truy cập | `canAccess` trả `false`, `IllegalAccessException`, `InaccessibleObjectException` khi xin mở | Nêu class, package, module và contract quyền |
| Gọi sai đối số | `IllegalArgumentException`, `WrongMethodTypeException` khi dùng API method handle | Nêu kiểu thực tế và kiểu yêu cầu |
| Code đích ném lỗi | `InvocationTargetException` | Phân biệt lỗi business, log cause gốc |
| Không thể tạo instance | `InstantiationException`, lỗi constructor, class abstract | Nêu loại instance cần tạo và nguyên nhân |

Không nên in toàn bộ dữ liệu đầu vào nhạy cảm vào log; khi có batch nhiều mục, báo vị trí, mã bước và nguyên nhân đủ để sửa.

## 8. Thiết kế kế hoạch đã kiểm tra cho hệ thống thật

Đối với batch lớn, xây dựng kiểu dữ liệu ghi rõ thông tin của mỗi bước, ví dụ:

```java
// Đoạn phác thảo mô hình, không phải chương trình độc lập.
record CheckedCall(
        String stepId,
        Object target,
        java.lang.reflect.Method method,
        Object[] arguments) {}
```

Mỗi `CheckedCall` có thể mang thêm thông tin về policy, thứ tự, mục tiêu log, context quyền, deadline và kết quả validation. Chỉ đưa vào kế hoạch cuối khi mọi điều kiện hợp lệ. Trường hợp cần xử lý nhiều lỗi cùng lúc, gom tất cả diagnostics trong một lần quét rồi từ chối chạy nếu có ít nhất một lỗi; tránh sửa lỗi từng mục bằng chu kỳ chạy–fail–sửa tốn kém.

Các điểm cần cân nhắc:

- **Snapshot cấu hình**: không cho một thread khác thay danh sách method/arguments sau khi preflight. Nếu argument là object mutable, cần snapshot hoặc biện pháp đồng bộ phù hợp; `List.copyOf` không đóng băng trạng thái object bên trong.
- **Đúng caller của `canAccess`**: kiểm tra quyền tại chính tầng executor (hoặc thiết kế quyền tương đương), vì quyền Java có ngữ cảnh class/module.
- **Sự khác biệt giữa metadata và chính sách**: annotation biểu thị ý định đăng ký, nhưng allowlist/authorization mới quyết định người dùng cụ thể được chạy.
- **Các lời gọi lặp lại**: cache metadata hoặc plan khi thích hợp; vô hiệu hóa cache khi plugin đổi class loader/phiên bản. `Method` có thể tái sử dụng trên nhiều receiver đúng kiểu.
- **Thứ tự xác định**: đừng dựa vào thứ tự phản hồi từ `getDeclaredMethods()`; sắp xếp hoặc lấy thứ tự từ cấu hình rõ ràng.
- **Giới hạn tài nguyên**: xác minh kích thước lô, timeout, độ dài input, số luồng, quyền file/network trước khi chạy.
- **Quan sát và báo lỗi**: diagnostic phải chỉ đúng bước nào, expected/actual signature, quyền truy cập và trạng thái đã/chưa thực thi.

Nếu bài toán chỉ yêu cầu các thao tác chuẩn hóa và kiểm tra qua interface thống nhất, có thể thiết kế `List<Processor>` với lời gọi `process(...)` trực tiếp. Reflection phù hợp hơn khi framework phải **khám phá và ghép nối metadata động**, chứ không phải để thay thế mọi lời gọi có kiểu rõ ràng.

## 9. Checklist trước khi bấm “Run batch”

1. Đã xác thực nguồn cấu hình và giới hạn danh sách class/method được phép?
2. Mọi class/receiver/constructor đúng contract và không có `null` hoặc kiểu sai?
3. Method được tìm theo chính sách rõ ràng (declared/public/inherited, annotation, overload)?
4. Đã kiểm tra **chính xác** số/kiểu tham số, return type, modifier, varargs, bridge/synthetic?
5. Quyền thực sự từ executor đã được xác minh bằng `canAccess` với receiver đúng loại; JPMS có cho phép?
6. Mọi đối số đã được xác thực hoặc chuyển đổi an toàn, không để lỗi có thể phát hiện trước dồn sang execute?
7. Đã kiểm tra **tất cả bước** trước lời gọi nghiệp vụ đầu tiên? Kế hoạch có bị thay đổi sau preflight không?
8. Có xử lý `InvocationTargetException`, timeout, retry, lỗi từng bước và side effects đã xảy ra?
9. Cần transaction, idempotency hoặc rollback/compensation để bảo đảm tính toàn vẹn không?
10. Log có đủ ngữ cảnh để sửa cấu hình và không lộ dữ liệu nhạy cảm?

### Câu hỏi tự kiểm tra

- Vì sao `getDeclaredMethod` tìm được một method private chưa chứng minh `invoke` sẽ được phép?
- `isAssignableFrom` phải gọi theo chiều nào để kiểm tra plugin thực thi một interface?
- Nếu method yêu cầu `String` nhưng runner truyền `Integer`, nên phát hiện ở pha nào?
- `canAccess(null)` phù hợp với static method hay instance method?
- Nếu preflight chạy xong nhưng lời gọi bước thứ 4 ném exception, hệ thống có tự rollback các bước trước không?
- Trường hợp constructor plugin có side effect, tạo instance trong preflight có còn giữ được cam kết “chưa chạy nghiệp vụ” không?

### Tham khảo chính thức

- [`Class` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `isInstance`, `isAssignableFrom`, tìm method và constructor.
- [`Method` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html): chữ ký, `invoke` và metadata.
- [`AccessibleObject` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html): `canAccess`, `trySetAccessible` và giới hạn access.
- [`InvocationTargetException` — Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationTargetException.html): lỗi được bọc khi target method hoặc constructor ném exception.
- [Java Language Specification `6.6](https://docs.oracle.com/javase/specs/jls/se17/html/jls-6.html#jls-6.6): quy tắc access control.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tooling-design-008">Ánh xạ giá trị đầu vào theo quy tắc chuyển đổi tường minh</a>

<details>
<summary>Xem chi tiết</summary>

# Ánh xạ giá trị đầu vào theo quy tắc chuyển đổi tường minh

## 1. Bài toán trong công cụ dùng Java Reflection

Một công cụ gọi phương thức bằng reflection có thể nhận đầu vào từ CLI, HTTP request, cấu hình hoặc biểu mẫu. Nguồn dữ liệu thường đưa cho công cụ **chuỗi** (ví dụ `"3"`, `"true"`, `"2026-10-24"`), nhưng phương thức Java cần các kiểu thực tế như `int`, `boolean`, `LocalDate` hoặc enum.

Ví dụ phương thức đích:

```java
public String create(int count, boolean urgent, LocalDate due,
                     Priority priority, BigDecimal budget) {
    // Xử lý nghiệp vụ.
}
```

Công cụ không thể chỉ đưa các chuỗi thẳng vào `Method.invoke`:

```java
// SAI nếu method nhận int, boolean, LocalDate, Priority, BigDecimal:
method.invoke(target, "3", "true", "2026-10-24", "HIGH", "125.50");
```

`Method.invoke` **không có chức năng parse chuỗi thành kiểu nghiệp vụ**. Với đối số được cung cấp, reflection có thể áp dụng một số **unboxing/widening** primitive phù hợp, nhưng không tự biết `"3"` nghĩa là số nguyên, `"HIGH"` nghĩa là enum hay một chuỗi ngày phải theo định dạng nào. Để hệ thống đoán ngầm làm hành vi khó kiểm tra, khó báo lỗi và dễ thiếu an toàn.

**Mục tiêu bài học:** xây dựng cơ chế chuyển đổi rõ ràng từ **giá trị đầu vào** sang **kiểu tham số đích**, kiểm tra và báo lỗi trước khi thực sự gọi method. Đây là kỹ thuật thiết kế công cụ reflection, không phải hướng dẫn ghi đè hệ thống chuyển đổi của Java.

Sau bài học, bạn có thể:

- Lấy kiểu tham số bằng `Method.getParameterTypes()`, kiểm tra số lượng và thứ tự.
- Khai báo bảng quy tắc chuyển đổi được cho phép (allowlist), thay vì đoán bằng chuỗi tên lớp.
- Phân biệt dữ liệu `null`, chuỗi rỗng, chuỗi `"null"` và giá trị đã được parse.
- Tách **lỗi chuyển đổi** khỏi **ngoại lệ do nghiệp vụ ném**.
- Xử lý phạm vi phức tạp hơn như overloaded methods, varargs, generic, enum và phân quyền gọi phương thức.
- Thiết kế kết quả và lỗi có thể kiểm thử, dễ mở rộng mà không tạo những phép chuyển đổi khó đoán.

## 2. Quy trình ánh xạ: từ input đến tham số Method

Một pipeline rõ ràng nên có các bước:

```text
Input thô (chuỗi hoặc dữ liệu đã parse)
        |
        v
Xác định phương thức được phép gọi
        |
        v
Method.getParameterTypes() -> kiểu theo từng vị trí
        |
        v
Kiểm tra số lượng đối số, null và quy tắc cho từng kiểu
        |
        v
Chuyển đổi + kiểm tra lỗi có ngữ cảnh
        |
        v
Object[] đã chuyển đổi
        |
        v
Method.invoke(target, convertedArgs)
```

Mỗi vị trí `i` tương ứng đúng một `parameterTypes[i]` và một giá trị đầu vào `rawValues[i]`. Trong Java, vị trí tham số bắt đầu từ **0**. Nếu ứng dụng muốn hiển thị “tham số thứ nhất”, có thể chuyển `i + 1` khi tạo thông điệp.

Hai API cần biết:

```java
Class<?>[] types = method.getParameterTypes();
int count = method.getParameterCount();
```

`getParameterTypes()` trả về các kiểu **sau type erasure**. Ví dụ `List<String>` và `List<Integer>` đều có kiểu tham số runtime là `List.class`. `getGenericParameterTypes()` có thể giúp đọc khai báo generic ở mức metadata, nhưng không tự parse hoặc kiểm chứng nội dung generic.

`method.getParameters()` có thể cung cấp metadata về tham số. Tuy nhiên, tên có ý nghĩa trong source thường chỉ sẵn có nếu biên dịch với `-parameters`; nếu không, tên như `arg0` có thể xuất hiện. Công cụ không được phụ thuộc mù quáng vào tên tham số khi chưa xác nhận metadata.

## 3. Quy tắc chuyển đổi phải được khai báo rõ

Đầu tiên hãy quyết định **loại input hỗ trợ**. Ví dụ minh họa dưới đây nhận `String[]` từ nguồn dữ liệu dạng text. Đây là lựa chọn có chủ đích; nếu nhận JSON, công cụ cần phân biệt token number/boolean/null của JSON với chuỗi JSON trước khi ánh xạ.

Bảng chính sách mẫu:

| Kiểu đích | Quy tắc được phép | Ví dụ hợp lệ | Ví dụ bị từ chối |
| --- | --- | --- | --- |
| `String` | Giữ nguyên văn bản, không tự trim | `"  A  "` | Không tự đổi định dạng |
| `int` / `Integer` | `Integer.valueOf(raw)`; kiểm tra phạm vi `int` | `"3"`, `"-8"` | `"3.5"`, số vượt miền |
| `long` / `Long` | `Long.valueOf(raw)`; kiểm tra phạm vi `long` | `"9876543210"` | Giá trị vượt `long` |
| `boolean` / `Boolean` | Chỉ chấp nhận **chính xác** `"true"` hoặc `"false"` | `"true"` | `"yes"`, `"TRUE"` |
| `LocalDate` | `LocalDate.parse(raw)` theo ISO-8601 mặc định | `"2026-10-24"` | `"24/10/2026"` |
| Một enum được cho phép, ví dụ `Priority` | `Priority.valueOf(raw)`; khớp tên constant | `"HIGH"` | `"High"` |
| `BigDecimal` | `new BigDecimal(raw)` theo cú pháp số thập phân Java | `"125.50"` | `"12,50"` với dấu phẩy |

Đây là **chính sách mẫu**, không phải giới hạn của Java. Ứng dụng có thể chọn quy tắc khác như không phân biệt hoa thường hoặc chấp nhận dấu cách, nhưng phải ghi rõ, kiểm tra và áp dụng thống nhất. Với tiền tệ, `BigDecimal` thường thích hợp hơn `double` để tránh sai số nhị phân trong nhiều phép toán thập phân; các quy định về scale, làm tròn và tiền tệ cần được xác định riêng ở tầng nghiệp vụ.

**Tránh các chuyển đổi đoán ngầm:**

- Không gọi `String.valueOf(x)` trên mọi dữ liệu để rồi đoán lại kiểu.
- Không coi bất kỳ chuỗi ngoài `"true"` là `false`. `Boolean.parseBoolean("yes")` trả `false`, nên không đáp ứng chính sách strict của bài.
- Không tự parse ngày với nhiều định dạng mà không có thứ tự ưu tiên rõ ràng.
- Không tạo mọi lớp bằng `Class.forName(raw)` hay gọi mọi phương thức theo tên do người dùng tùy ý cung cấp.
- Không lấy “có thể cast” làm đồng nghĩa với “ngữ nghĩa đúng”; ví dụ chuyển `long` sang `int` có thể mất dữ liệu.

## 4. Ví dụ Java 17+ hoàn chỉnh: chuyển đổi tường minh và gọi Method

Tạo tệp `ExplicitMappingDemo.java`:

```java
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public class ExplicitMappingDemo {
    enum Priority { LOW, HIGH }

    public static class TaskService {
        public String create(int count, boolean urgent, LocalDate due,
                             Priority priority, BigDecimal budget) {
            return String.format(Locale.ROOT,
                    "count=%d urgent=%s due=%s priority=%s budget=%s",
                    count, urgent, due, priority, budget.toPlainString());
        }
    }

    static Object[] mapArguments(Method method, String[] rawValues) {
        Class<?>[] types = method.getParameterTypes();
        if (rawValues.length != types.length) {
            throw new IllegalArgumentException(
                    "expected " + types.length + " arguments, got " + rawValues.length);
        }

        Object[] converted = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            try {
                converted[i] = convert(rawValues[i], types[i]);
            } catch (IllegalArgumentException | DateTimeParseException ex) {
                throw new IllegalArgumentException(
                        "argument " + i + " must be " + types[i].getTypeName(), ex);
            }
        }
        return converted;
    }

    static Object convert(String raw, Class<?> type) {
        if (raw == null) {
            if (type.isPrimitive()) {
                throw new IllegalArgumentException("primitive cannot be null");
            }
            return null;
        }
        if (type == String.class) return raw;
        if (type == int.class || type == Integer.class) return Integer.valueOf(raw);
        if (type == long.class || type == Long.class) return Long.valueOf(raw);
        if (type == boolean.class || type == Boolean.class) {
            if (raw.equals("true")) return true;
            if (raw.equals("false")) return false;
            throw new IllegalArgumentException("boolean must be true or false");
        }
        if (type == LocalDate.class) return LocalDate.parse(raw);
        if (type == Priority.class) return Priority.valueOf(raw);
        if (type == BigDecimal.class) return new BigDecimal(raw);
        throw new IllegalArgumentException("unsupported type: " + type.getTypeName());
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Method method = TaskService.class.getMethod("create",
                int.class, boolean.class, LocalDate.class,
                Priority.class, BigDecimal.class);
        String[] raw = {"3", "true", "2026-10-24", "HIGH", "125.50"};
        Object result = method.invoke(new TaskService(), mapArguments(method, raw));
        System.out.println(result);

        try {
            mapArguments(method,
                    new String[]{"3", "yes", "2026-10-24", "HIGH", "125.50"});
        } catch (IllegalArgumentException ex) {
            System.out.println("rejected: " + ex.getMessage());
        }

        try {
            mapArguments(method, new String[]{null, "true", "2026-10-24", "HIGH", "125.50"});
        } catch (IllegalArgumentException ex) {
            System.out.println("rejected: " + ex.getMessage());
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac ExplicitMappingDemo.java
java ExplicitMappingDemo
```

Stdout **đã được kiểm chứng bằng chạy chương trình Java**:

```text
count=3 urgent=true due=2026-10-24 priority=HIGH budget=125.50
rejected: argument 1 must be boolean
rejected: argument 0 must be int
```

Phân tích:

1. `getMethod("create", int.class, boolean.class, LocalDate.class, Priority.class, BigDecimal.class)` định vị **một chữ ký chính xác** trên lớp `TaskService`, tránh đoán overload từ các chuỗi người dùng.
2. `getParameterTypes()` trả năm kiểu đúng theo thứ tự khai báo; `rawValues.length` phải bằng năm.
3. `convert` có nhánh riêng theo **`Class<?>` đích**, không dựa vào tên class dạng chuỗi.
4. `"3"` được parse thành `Integer`; khi `Method.invoke` gọi phương thức nhận `int`, wrapper được unbox hợp lệ.
5. Boolean chỉ nhận `"true"/"false"`; `"yes"` bị báo lỗi tại tham số có index `1`, trước khi gọi business method.
6. `LocalDate.parse` xử lý ngày ISO; `Priority.valueOf` nhận một enum constant cụ thể; `BigDecimal` lưu giá trị `125.50` và `toPlainString()` giữ cách hiển thị thập phân.
7. Tham số đầu tiên kiểu `int` không thể nhận `null`; lỗi được phát hiện trước reflection invocation.

**Giới hạn có chủ đích của ví dụ:** hiện chỉ cho phép `String`, `int/Integer`, `long/Long`, `boolean/Boolean`, `LocalDate`, enum `Priority` và `BigDecimal`. Kiểu khác sẽ bị từ chối. Có thể mở rộng theo chính sách cụ thể, nhưng không nên âm thầm “nhận mọi lớp”. Ví dụ cũng coi `raw == null` là **vắng giá trị thật**; chuỗi `"null"` vẫn là chuỗi có bốn ký tự, không tự biến thành null.

## 5. Kiểu primitive và wrapper: kiểm tra khả năng null

Reflection phân biệt `int.class` với `Integer.class`:

- `int.class.isPrimitive()` cho `true`; không thể truyền `null` vào tham số primitive `int`.
- `Integer.class.isPrimitive()` cho `false`; `null` có thể được dùng ở mức lời gọi, nhưng phương thức nghiệp vụ vẫn có thể từ chối nếu không chấp nhận giá trị vắng.
- `Method.invoke` thường nhận một `Object[]`; giá trị `int` được biểu diễn qua `Integer` ở mảng đó rồi được reflection unbox khi cần.
- Có một số phép **widening primitive** hợp lệ khi reflection invoke, nhưng phép chuyển đổi có ngữ nghĩa nghiệp vụ vẫn nên do lớp ánh xạ quy định. Reflection không thực hiện narrowing tùy ý hoặc parse `String` thành `int`.

Bảng rủi ro:

| Đầu vào | Kiểu đích | Quyết định |
| --- | --- | --- |
| `null` | `int` | Từ chối ngay |
| `null` | `Integer` | Cho phép ở tầng converter ví dụ; nghiệp vụ xác định có hợp lệ không |
| `"0"` | `int` | Parse thành `Integer.valueOf("0")`, unbox lúc gọi |
| `"2147483648"` | `int` | Từ chối do vượt phạm vi |
| `""` | `int` | Parse thất bại, không tự đặt mặc định `0` |
| `""` | `String` | Giữ chuỗi rỗng |
| `"null"` | `String` | Giữ chuỗi đúng nghĩa, không tự biến thành `null` |

Đừng dùng giá trị mặc định `0`, `false` hoặc `LocalDate.now()` để thay thế đầu vào lỗi mà không có hợp đồng yêu cầu; điều đó có thể khiến hệ thống làm sai thao tác.

## 6. Thiết kế lỗi tốt và kiểm thử từng quy tắc

Một converter tốt nên trả về **kết quả thành công có kiểu rõ ràng**, hoặc một lỗi chứa tối thiểu:

- Vị trí tham số (`index`) hay tên tham số đã được xác nhận.
- Kiểu đích yêu cầu (`Class.getTypeName()`).
- Mã lỗi ngắn gọn: sai số lượng, không hỗ trợ kiểu, format không hợp lệ, vượt phạm vi, null không được phép.
- Nguyên nhân gốc được giữ lại (cause) để phục vụ chẩn đoán nội bộ.

Ví dụ sử dụng `IllegalArgumentException` bọc nguyên nhân gốc:

```java
try {
    converted[i] = convert(rawValues[i], types[i]);
} catch (IllegalArgumentException | DateTimeParseException ex) {
    throw new IllegalArgumentException(
            "argument " + i + " must be " + types[i].getTypeName(), ex);
}
```

Đây là mảnh code trích từ ví dụ đầy đủ. Việc giữ `cause` giúp phân biệt `NumberFormatException` với `DateTimeParseException` mà không làm người dùng cuối nhận một stack trace nội bộ.

**Không tự động đưa nội dung đầu vào nhạy cảm vào thông báo lỗi**: công cụ có thể đang xử lý mã truy cập, email hoặc dữ liệu cá nhân. Ghi đủ thông tin vị trí/kiểu, đồng thời áp dụng chính sách log phù hợp.

Nên kiểm thử bằng các nhóm:

| Nhóm | Ca kiểm tra |
| --- | --- |
| Hợp lệ | Giá trị biên của `int/long`, boolean `true/false`, ngày ISO, enum hợp lệ |
| Sai định dạng | `"12.5"` vào `int`, `"yes"` vào boolean, ngày `"31/12/2026"` |
| Ngoài miền | Số lớn hơn `Integer.MAX_VALUE` hoặc nhỏ hơn `Integer.MIN_VALUE` |
| Null/rỗng | `null` sang primitive, chuỗi rỗng sang số, chuỗi `"null"` |
| Sai số lượng | Thiếu/thừa đối số |
| Không hỗ trợ kiểu | `UUID`, `List`, class tự định nghĩa khi chưa đăng ký converter |
| Sai nghiệp vụ | Đã parse đúng kiểu nhưng vi phạm điều kiện như `count < 0` |
| Ngoại lệ từ target | Phương thức đích ném lỗi sau bước chuyển đổi |

Lưu ý `convert` chỉ chịu trách nhiệm kiểm tra kiểu/cú pháp đã quy định; **validation nghiệp vụ** như giá tiền phải dương, số lượng tối đa hay deadline hợp lệ cần một bước riêng. Parse thành công không đồng nghĩa input đã được phép sử dụng.

## 7. Tách lỗi chuyển đổi khỏi lỗi Method.invoke

Sau khi mapper trả về một `Object[]` hợp lệ, lời gọi reflection vẫn có thể thất bại vì:

- Không có quyền truy cập phương thức, phát sinh `IllegalAccessException`.
- Đối tượng `target` không phù hợp với phương thức instance hoặc đối số không tương thích tại lúc gọi, dẫn đến `IllegalArgumentException`.
- Phương thức đích thật sự ném ngoại lệ, được bọc thành `InvocationTargetException`.

Một mảnh code minh họa xử lý ở tầng gọi (cần import `java.lang.reflect.InvocationTargetException` và `Method`):

```java
try {
    Object[] args = mapArguments(method, rawValues);
    return method.invoke(target, args);
} catch (InvocationTargetException ex) {
    // Lỗi từ chính phương thức nghiệp vụ.
    throw ex.getCause();
}
```

Mảnh code này thuộc một phương thức khai báo `throws Throwable`; các ngoại lệ truy cập/chuyển đổi cần được xử lý ở tầng gọi phù hợp. **Đừng coi `InvocationTargetException` là lỗi parse**: target đã chạy và tự ném ngoại lệ. Phân biệt hai loại lỗi giúp xác định nên báo người dùng sửa input hay sửa logic nghiệp vụ.

Trong code sản phẩm, thay vì bắt tất cả `Exception` rồi trả cùng một thông điệp, hãy phân loại lỗi theo mã, ghi log đúng lớp và chỉ hiển thị nội dung phù hợp với người dùng.

## 8. Overload, varargs, enum và generic

### 8.1. Overloaded methods

`getMethod` chọn theo tên và **danh sách kiểu tham số chính xác**:

```java
// Nếu có create(int) và create(String):
Method m = SomeService.class.getMethod("create", int.class);
```

Đừng quyết định overload bằng cách “chọn phương thức đầu tiên có cùng tên” từ `getMethods()`: thứ tự phản hồi không phải hợp đồng để chọn overload, và cùng chuỗi `"123"` có thể hợp lệ với cả `String` và `int`. Nếu buộc phải giải quyết overload động, hãy định nghĩa thứ tự ưu tiên và quy tắc phát hiện mơ hồ; nếu hai phương thức cùng phù hợp mà không có quy tắc rõ, **báo ambiguity** thay vì đoán.

### 8.2. Varargs

Một phương thức `send(String... messages)` có tham số cuối kiểu `String[]` ở cấp reflection. `Method.isVarArgs()` giúp nhận biết declaration, nhưng `getParameterTypes()` vẫn cho mảng ở vị trí cuối.

Khi dùng `Method.invoke`, cần xây dựng mảng đúng kiểu cho tham số varargs và đặt nó thành **một phần tử** của `Object[]` đối số gọi phương thức. Mapper đơn giản trong ví dụ không tự thu gom nhiều chuỗi thành mảng; nếu cần, phải định nghĩa riêng cách ánh xạ varargs, chặn thiếu tham số bắt buộc và phân biệt một mảng với nhiều đối số.

### 8.3. Enum

`Enum.valueOf(...)` phân biệt hoa thường và ném `IllegalArgumentException` khi không có constant tương ứng. Ví dụ chỉ hỗ trợ `Priority`, nên không mở rộng quyền chuyển đổi sang bất kỳ enum có trong classpath.

Nếu muốn hỗ trợ các enum khác, nên đăng ký những enum được phép và, khi cần, dùng ánh xạ tên thân thiện sang constant thông qua bảng cấu hình rõ ràng. Không nạp lớp hoặc constant tùy tiện theo dữ liệu đầu vào không đáng tin.

### 8.4. Collections và generic

`List<String>` không thể được xác định đầy đủ chỉ từ `getParameterTypes()` vì type erasure. Muốn ánh xạ JSON list vào danh sách các phần tử có kiểu, cần có quy tắc đọc `getGenericParameterTypes()` hoặc schema riêng, kiểm tra từng phần tử, chính sách null và giới hạn kích thước.

Trong thiết kế công cụ thực tế, schema/DTO thường rõ ràng hơn việc suy đoán generic của mọi trường hợp reflection. Đặc biệt, không nên cho phép phản chiếu vào mọi field private chỉ để tạo đối tượng từ input.

## 9. Mở rộng bằng converter registry có kiểm soát

Khi số kiểu hỗ trợ tăng, thay chuỗi `if` bằng registry kết hợp **kiểu đích** và **hàm chuyển đổi**:

```java
// Mảnh code minh họa trong lớp đã import Map, Class, Function.
Map<Class<?>, java.util.function.Function<String, ?>> converters = Map.of(
        String.class, raw -> raw,
        Integer.class, Integer::valueOf,
        Long.class, Long::valueOf,
        LocalDate.class, LocalDate::parse
);
```

Registry minh họa trên chỉ đăng ký kiểu wrapper/reference; khi nhận primitive, phải có bước ánh xạ `int.class -> Integer.class`, `long.class -> Long.class` theo chính sách. Tương tự, enum hoặc converter có tham số bổ sung có thể cần API giàu ngữ cảnh hơn. Đoạn code này **không phải** mapper đầy đủ và chưa xử lý null/boolean/error; ví dụ mục 4 là chương trình chạy độc lập.

Một thiết kế ổn định nên có các thành phần:

1. **Danh sách method/class được phép truy cập** độc lập với converter. Được parse một kiểu không đồng nghĩa được quyền gọi mọi method nhận kiểu đó.
2. **Converter registry** chỉ hỗ trợ những kiểu đã đăng ký, có test riêng và quy tắc format được tài liệu hóa.
3. **Validation layer** kiểm tra ràng buộc nghiệp vụ sau khi parse, trước khi gọi.
4. **Invocation layer** chỉ nhận dữ liệu đã được chuẩn hóa và phân biệt lỗi reflection với lỗi do target ném.
5. **Error reporting** cung cấp vị trí, tên kiểu, mã lỗi và cause để debug, nhưng không lộ dữ liệu nhạy cảm.

Có thể sử dụng schema chung cho command line, request body và cấu hình, nhưng mỗi nguồn vẫn cần parser ban đầu riêng (ví dụ JSON number khác JSON string).

## 10. Các lỗi thiết kế thường gặp

1. **Dùng `Method.invoke(target, strings...)` rồi chờ JVM parse chuỗi:** reflection không làm điều đó.
2. **Dựa vào `Object.toString()` để chuyển đổi mọi kiểu:** mất cấu trúc dữ liệu và format có thể phụ thuộc locale.
3. **Dùng `Boolean.parseBoolean` cho yêu cầu strict:** giá trị bất kỳ ngoài `"true"` có thể âm thầm thành `false`.
4. **Tự động trim hoặc thay dấu thập phân:** dữ liệu `String` gốc có thể cần giữ nguyên; phép chuẩn hóa phải được khai báo riêng.
5. **Tự chọn overload đầu tiên theo tên:** gây lỗi không ổn định khi thêm overload hoặc chạy JVM khác.
6. **Cho phép `null` vào primitive:** phải phát hiện trước khi invoke.
7. **Không phân biệt parse với validation nghiệp vụ:** `"-1"` có thể parse hợp lệ thành `int`, nhưng `count = -1` vẫn sai.
8. **Đưa trực tiếp raw value vào log lỗi:** có nguy cơ lộ dữ liệu nhạy cảm.
9. **Tự động chấp nhận mọi class/constructor/field có thể truy cập:** biến converter thành điểm mở rộng quá quyền và khó kiểm toán.
10. **Coi generic runtime có đầy đủ type argument:** cần schema hoặc đọc generic metadata có kiểm soát.

## 11. Tự kiểm tra

**Câu 1.** Đích nhận `int`, raw value là `"007"`. Phép chuyển đổi nên diễn ra thế nào? 

Nếu chính sách chọn `Integer.valueOf` như mẫu, chuỗi được parse thành số nguyên `7`; không có phép “giữ số 0 đầu” với `int`. Nếu các số 0 đầu mang ý nghĩa nghiệp vụ, kiểu đích có thể phải là `String`.

**Câu 2.** Vì sao `Boolean.parseBoolean("yes")` không phù hợp với chính sách boolean strict?

Vì nó trả `false` cho giá trị không bằng `"true"` theo cơ chế của API; dữ liệu sai bị hiểu là giá trị hợp lệ. Cần kiểm tra danh sách literal cho phép rồi mới chuyển đổi.

**Câu 3.** Nếu người dùng nhập `"2026-02-30"`, `LocalDate.parse` sẽ ra sao?

Ngày không tồn tại nên parse thất bại (thường là `DateTimeParseException`); mapper phải báo lỗi vị trí/kiểu và không gọi phương thức nghiệp vụ.

**Câu 4.** Hai overload `save(int)` và `save(String)` cùng nhận được chuỗi `"123"` theo hai chính sách. Nên gọi hàm nào?

Không thể quyết định chỉ từ input. Phải có định nghĩa lựa chọn overload; nếu thiếu thì báo ambiguity hoặc yêu cầu người gọi chỉ rõ chữ ký.

**Câu 5.** `getParameterTypes()` cho `List.class`, có đủ để khẳng định phần tử phải là `String`?

Không. Java erasure loại bỏ phần lớn type argument ở cấp `Class<?>`; dùng schema hoặc generic metadata riêng nếu cần.

**Câu 6.** Vì sao phải giữ nguyên cause khi bọc lỗi mapping?

Để phân biệt nguyên nhân như out-of-range integer, sai tên enum hoặc sai cú pháp ngày, nhưng giao diện người dùng vẫn có thể trả thông điệp ổn định không chứa dữ liệu nhạy cảm.

## 12. Ghi nhớ

**Ánh xạ tường minh = biết trước kiểu đích, khai báo phép chuyển đổi được phép, kiểm tra đầu vào, rồi mới gọi reflection.** Công cụ vững chắc không cố “đoán đúng mọi input”; nó cung cấp một hợp đồng chuyển đổi có thể giải thích, kiểm thử và mở rộng. `Method.getParameterTypes()` cung cấp các kiểu tham số tại runtime; converter chịu trách nhiệm parse; validation chịu trách nhiệm ràng buộc nghiệp vụ; `Method.invoke` chỉ thực hiện lời gọi.

Tài liệu JDK 17:

- [Method](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html)
- [Method.getParameterTypes](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Executable.html#getParameterTypes())
- [Integer](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html)
- [Boolean](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Boolean.html)
- [LocalDate](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/LocalDate.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tooling-design-009">JavaBeans Introspector và khác biệt với duyệt field trực tiếp</a>

<details>
<summary>Xem chi tiết</summary>

# JavaBeans Introspector và khác biệt với duyệt field trực tiếp

## 1. Vì sao công cụ Java cần phân biệt property và field?

`Field` biểu diễn biến lưu trữ thật; **JavaBeans property** biểu diễn một hợp đồng logic được phát hiện chủ yếu từ getter/setter. `getSummary()` có thể công bố một property tính toán mà không có field `summary`, còn `internalCache` có thể là field thật nhưng không có property. Dùng `getDeclaredFields()` để mô phỏng JavaBeans sẽ làm sai schema.

Khác với bài về **lựa chọn chiến lược field/property/accessor**, bài này đi sâu vào JDK `java.beans.Introspector`, `BeanInfo`, `PropertyDescriptor`: quy tắc `getX`/`isX`/`setX`, kế thừa, custom BeanInfo, read/write method và giới hạn truy cập. Trong named module, `java.beans` thuộc module `java.desktop`; mã modular cần quan hệ đọc phù hợp.

## 2. JavaBeans property được suy ra thế nào?

Các mẫu cơ bản:

| Phương thức public | Property được nhận diện | Vai trò |
| --- | --- | --- |
| **String getName()** | **name** | Getter của property **name** |
| **void setName(String value)** | **name** | Setter của property **name** |
| **boolean isActive()** | **active** | Getter boolean theo dạng **isX** |
| **int getCount()** | **count** | Getter với kiểu primitive |
| **String getSummary()** | **summary** | Read-only property nếu không có setter tương ứng |
| **void setPassword(String value)** | **password** | Write-only property nếu không có getter tương ứng |
| **String name()** | Không tự nhận thành property **name** bằng quy ước getter JavaBeans truyền thống | Method bình thường; thường gặp ở record |
| **public String raw;** | Không tự nhận thành property **raw** chỉ vì có field | Field công khai không phải getter/setter |

Một property có thể là:
- **Read-write**: getter và setter tương thích.
- **Read-only**: có getter nhưng thiếu setter.
- **Write-only**: có setter nhưng thiếu getter.
- **Computed/derived**: getter tính giá trị từ dữ liệu khác, không cần field cùng tên.
- **Inherited**: getter hoặc setter công khai được thừa kế từ lớp cha.

Những quy ước quen thuộc là getter không có tham số và trả một giá trị; setter có một tham số, trả **void** theo JavaBeans convention. Với getter dạng **isX()**, loại trả về kiểu **boolean nguyên thủy** là quy tắc an toàn; đừng giả định mọi công cụ sẽ chấp nhận **Boolean** hoặc signature không chuẩn như nhau. Với các overload getter/setter phức tạp, kiểu property được suy ra có thể không như dự kiến; cần kiểm tra kết quả **PropertyDescriptor** thực tế.

Tên property dùng quy tắc **Introspector.decapitalize**:
- **getName()** → **name**.
- **getURL()** → **URL**, vì hai ký tự đầu của phần “URL” đều viết hoa.
- **getEMail()** → **EMail**, không phải luôn **eMail**.
- **getX()** → **x**.

Việc đặt tên method theo convention là điều kiện quan trọng khi viết bean để thư viện khác có thể khám phá một cách nhất quán.

## 3. API chính: Introspector, BeanInfo, PropertyDescriptor

### 3.1. Lấy thông tin JavaBeans

Cách sử dụng đơn giản:

~~~java
BeanInfo info = Introspector.getBeanInfo(Person.class);
PropertyDescriptor[] properties = info.getPropertyDescriptors();
~~~

Hai câu lệnh trên là **đoạn minh họa**, cần import **java.beans.BeanInfo**, **java.beans.Introspector**, **java.beans.PropertyDescriptor** và có class **Person** thật.

Những thành phần quan trọng:

| API | Nhiệm vụ |
| --- | --- |
| **Introspector.getBeanInfo(Class<?>)** | Khám phá BeanInfo của class và hệ kế thừa theo cơ chế JavaBeans |
| **Introspector.getBeanInfo(Class<?>, Class<?> stopClass)** | Bỏ qua thông tin từ **stopClass** và các lớp cha của nó |
| **Introspector.getBeanInfo(Class<?>, Class<?> stopClass, int flags)** | Thêm tùy chọn dùng/bỏ qua lớp BeanInfo cung cấp tường minh |
| **BeanInfo.getPropertyDescriptors()** | Danh sách các property descriptor |
| **PropertyDescriptor.getName()** | Tên property logic |
| **PropertyDescriptor.getPropertyType()** | Kiểu của property thông thường |
| **PropertyDescriptor.getReadMethod()** | Getter **Method**, có thể **null** |
| **PropertyDescriptor.getWriteMethod()** | Setter **Method**, có thể **null** |
| **BeanInfo.getMethodDescriptors()** | Metadata method liên quan đến JavaBean |
| **BeanInfo.getEventSetDescriptors()** | Metadata sự kiện theo JavaBeans event convention |

**getBeanInfo(Person.class)** thường có thể bao gồm property **class**, vì public **Object.getClass()** trông giống một getter. Khi công cụ chỉ cần property nghiệp vụ, dùng:

~~~java
BeanInfo info = Introspector.getBeanInfo(
        Person.class,
        Object.class,
        Introspector.IGNORE_ALL_BEANINFO);
~~~

**Object.class** là điểm dừng: không đưa property/method/event thuộc Object và các lớp phía trên vào kết quả. Cờ **IGNORE_ALL_BEANINFO** bảo Introspector bỏ qua các BeanInfo tường minh có thể có trong class hoặc lớp cha; điều này làm ví dụ học quy ước getter/setter dễ dự đoán hơn. Trong công cụ sản xuất, bạn **không nên mặc định bỏ qua BeanInfo tường minh** nếu sản phẩm muốn tôn trọng cấu hình metadata do tác giả bean cung cấp.

**JavaBeans là metadata ở mức public API.** Lấy descriptor không tự động trao quyền gọi private method hoặc truy cập private field.

### 3.2. Lớp BeanInfo tường minh và bộ nhớ đệm

Ngoài suy luận theo tên method, JavaBeans cho phép tác giả cung cấp metadata **BeanInfo** tường minh, thường là class tên **FooBeanInfo** đi cùng **Foo**. Metadata này có thể điều chỉnh những property được xuất ra cho các công cụ. Chính vì vậy, kết quả introspection không nhất thiết chỉ là kết quả áp quy tắc tên getter/setter cơ học.

**Introspector** có cache để giảm chi phí phân tích lại. API **flushFromCaches(Class<?>)** hoặc **flushCaches()** phục vụ trường hợp cần xóa dữ liệu đã cache có chủ đích. Đừng xóa toàn bộ cache ở mỗi lần inspect; hãy dùng cache của JDK bình thường và tránh introspection quá thường xuyên trên đường chạy nóng.

## 4. Duyệt field trực tiếp bằng Reflection khác gì?

Với class **Person**, Reflection có các họ API:

| Reflection API | Trả về | Quy tắc |
| --- | --- | --- |
| **getDeclaredFields()** | Field được khai báo trực tiếp trên class, gồm cả private | Không tự thêm field chỉ kế thừa từ lớp cha |
| **getFields()** | Các field **public** có thể truy cập theo quy tắc kế thừa | Không liệt kê toàn bộ private field |
| **getDeclaredField("name")** | Một field khai báo trực tiếp có tên chính xác | Nếu không tồn tại sẽ ném **NoSuchFieldException** |
| **Field.getType()** | Kiểu runtime thô của field | Với generic, có thể cần **getGenericType()** |
| **Field.get(obj)** | Giá trị field khi có quyền truy cập | Không gọi getter JavaBeans |
| **Field.set(obj, value)** | Thử gán field theo quyền và quy tắc Reflection | Không gọi setter/validation nghiệp vụ của bean |

Vì vậy:

- Một field **private String internalCode** xuất hiện trong **getDeclaredFields()**, nhưng chưa chắc là JavaBeans property.
- Một method **getSummary()** tính chuỗi từ các dữ liệu khác có thể tạo property **summary**, dù class không có field **summary**.
- Field **public String raw** hiển thị trong Reflection field scan, nhưng sẽ không tự tạo JavaBeans property **raw** nếu không có accessor phù hợp.
- Property **id** do getter từ lớp cha có thể xuất hiện qua Introspector, trong khi **getDeclaredFields()** của lớp con không chứa field **id**.
- Hai API đều cần xử lý thứ tự sắp xếp tường minh nếu muốn báo cáo cố định; không coi thứ tự field trả về là thứ tự viết trong source đã được bảo đảm.

**Không đánh đồng quyền nhìn thấy metadata với quyền sử dụng:** **getDeclaredFields()** có thể mô tả private field, nhưng gọi **Field.get** hoặc **Field.set** vẫn chịu kiểm tra truy cập và Java module encapsulation. **setAccessible(true)** có thể bị từ chối; đây không phải cách mặc định để xây một công cụ JavaBeans inspector.

## 5. Ví dụ Java 17+ chạy được: cùng class, hai cách nhìn

Lưu mã sau vào **BeanVsFieldDemo.java**. Đây là chương trình độc lập, dùng API JDK và không cần thư viện bên ngoài.

~~~java
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;

public class BeanVsFieldDemo {
    public static class Base {
        private final String id;

        public Base(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }
    }

    public static class Profile extends Base {
        public String raw = "raw-only";
        private String displayName = "Lan";
        private boolean active = true;
        private String internalCode = "hidden";

        public Profile() {
            super("P-1");
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        public String getSummary() {
            return displayName + " (" + getId() + ")";
        }
    }

    public static void main(String[] args) throws Exception {
        // 1. Lấy field được khai báo trực tiếp trong Profile.
        Field[] fields = Profile.class.getDeclaredFields();
        Arrays.sort(fields, Comparator.comparing(Field::getName));
        System.out.println("FIELDS: " + String.join(", ",
                Arrays.stream(fields).map(Field::getName).toList()));

        // 2. Khám phá property theo getter/setter, lấy cả kế thừa.
        BeanInfo beanInfo = Introspector.getBeanInfo(
                Profile.class,
                Object.class,
                Introspector.IGNORE_ALL_BEANINFO);

        PropertyDescriptor[] props = beanInfo.getPropertyDescriptors();
        Arrays.sort(props, Comparator.comparing(PropertyDescriptor::getName));

        System.out.println("PROPERTIES: " + String.join(", ",
                Arrays.stream(props).map(PropertyDescriptor::getName).toList()));

        for (PropertyDescriptor p : props) {
            System.out.println(p.getName() + ": read="
                    + (p.getReadMethod() == null
                        ? "-" : p.getReadMethod().getName())
                    + ", write="
                    + (p.getWriteMethod() == null
                        ? "-" : p.getWriteMethod().getName()));
        }

        // 3. Ghi property qua setter, đọc computed property qua getter.
        Profile profile = new Profile();

        for (PropertyDescriptor p : props) {
            if (p.getName().equals("displayName")) {
                p.getWriteMethod().invoke(profile, "Mai");
            }
            if (p.getName().equals("summary")) {
                System.out.println(
                        "summary=" + p.getReadMethod().invoke(profile));
            }
        }
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~bash
javac -encoding UTF-8 BeanVsFieldDemo.java
java BeanVsFieldDemo
~~~

Đầu ra kiểm chứng với JDK 17+:

~~~text
FIELDS: active, displayName, internalCode, raw
PROPERTIES: active, displayName, id, summary
active: read=isActive, write=setActive
displayName: read=getDisplayName, write=setDisplayName
id: read=getId, write=-
summary: read=getSummary, write=-
summary=Mai (P-1)
~~~

**Giải thích điều quan trọng nhất:**

1. **getDeclaredFields()** thấy bốn field **active**, **displayName**, **internalCode**, **raw** trên **Profile**, dù private hay public. Field **id** được khai báo trong **Base**, nên không nằm trong kết quả này.
2. **Introspector** nhận **active** từ **isActive/setActive**, nhận **displayName** từ **getDisplayName/setDisplayName**.
3. **id** là property kế thừa qua **Base.getId()**, không cần **Profile** có field **id**.
4. **summary** là property chỉ đọc từ **getSummary()**, **không có field summary**; kết quả phụ thuộc trạng thái hiện tại của object.
5. **internalCode** là field private không có getter/setter; **raw** là field public không có accessor. Cả hai **không trở thành property** theo convention.
6. **PropertyDescriptor.getReadMethod/getWriteMethod** có thể trả về **null**. Ví dụ **id** và **summary** không có setter nên in dấu **-**.
7. **setDisplayName("Mai")** được gọi qua **Method.invoke**; sau đó getter **getSummary()** tạo ra chuỗi **Mai (P-1)**. Ví dụ không cần truy cập private field.

**Giới hạn của ví dụ:** chỉ thao tác với property đã xác định và các method public. Trong công cụ xử lý input thực, cần xác minh loại property, quyền cho phép, khả năng write, kiểu dữ liệu và nguy cơ side effect trước khi gọi setter.

## 6. Chọn Introspector hay Field Reflection cho từng mục đích

| Bài toán | Nên bắt đầu bằng | Lý do |
| --- | --- | --- |
| Hiển thị biểu mẫu chỉnh sửa thông qua API công khai của bean | **PropertyDescriptor** | Biết property nào đọc/ghi được bằng getter/setter |
| Công cụ phát hiện property kế thừa | **Introspector** | Nhận getter/setter public ở hệ lớp cha theo quy tắc JavaBeans |
| Kiểm tra cấu trúc field trong class hiện tại | **getDeclaredFields()** | Có metadata về field kể cả private |
| Đọc annotation đặt trực tiếp lên field | **Field.getDeclaredAnnotations()** | Chú thích có thể gắn ở field, không ở getter |
| Đọc annotation trên getter | **PropertyDescriptor.getReadMethod()** rồi kiểm tra method | Annotation ở getter không tự xuất hiện trên Field |
| Mapping qua các setter công khai có kiểm tra nghiệp vụ | **Introspector** và gọi setter có kiểm soát | Cho phép logic trong setter chạy; cần xét side effect |
| Công cụ debug/trình kiểm tra cấp thấp đối tượng | **Reflection Field**, chỉ khi thật sự cần | Có thể phân tích trạng thái vật lý không được public API cung cấp |
| Lấy dữ liệu computed như **summary** | **Introspector** | Không cần có field tên **summary** |

Một công cụ có thể dùng **cả hai cách** nếu mỗi nguồn metadata có ý nghĩa riêng, nhưng cần chỉ rõ đang hiển thị **field vật lý**, **property logic** hay hai danh sách tương ứng. Ghép chúng theo tên rồi xem là đồng nhất dễ làm mất hoặc gán nhầm thông tin.

### Những trường hợp không nên dùng quy tắc tên như bộ suy đoán tuyệt đối

- Java record thường có accessor kiểu **name()** thay vì **getName()**; JavaBeans Introspector tiêu chuẩn không nên được giả định tự coi mọi accessor của record là property JavaBeans.
- API fluent như **Person name(String value)** hoặc **Person withName(...)** không theo setter **void setName(...)** truyền thống; các framework hiện đại có thể hỗ trợ quy ước mở rộng riêng.
- Getter có thể trả kết quả tính toán, gọi database hoặc ném lỗi. **Thấy getter** không đồng nghĩa **gọi getter luôn rẻ và an toàn**.
- Cùng tên property có thể xuất hiện từ nhiều method overload hoặc kế thừa; thay vì tự ghép chuỗi method, nên lấy **PropertyDescriptor** thực tế và ghi nhận lựa chọn của Introspector.

## 7. Lọc property và đọc/ghi an toàn

Một quy trình phù hợp cho công cụ cấu hình hoặc inspector:

1. Gọi **Introspector.getBeanInfo(type, Object.class)** để tránh property **class** của Object nếu không cần.
2. Lấy **PropertyDescriptor[]** và sắp xếp theo tên nếu muốn kết quả ổn định.
3. Lọc property theo **allowlist** rõ ràng; không tự động gọi getter/setter cho mọi property tìm được.
4. Với thao tác đọc, kiểm tra **getReadMethod() != null**; với thao tác ghi, kiểm tra **getWriteMethod() != null**.
5. Trước khi ghi, kiểm tra **getPropertyType()**, kiểu input, tính tương thích, quy tắc validation và quyền của caller.
6. Bắt và phân loại lỗi thao tác như **IllegalAccessException**, **InvocationTargetException**, **IllegalArgumentException**; nếu dùng **Method.invoke**, ngoại lệ do getter/setter ném thường nằm trong **InvocationTargetException.getCause()**.
7. Ghi log thông tin đủ để chẩn đoán nhưng tránh lộ nội dung nhạy cảm từ các property như password, token, secret.

Ví dụ lọc property theo tên:

~~~java
for (PropertyDescriptor p : info.getPropertyDescriptors()) {
    if (!p.getName().equals("displayName")) {
        continue;
    }
    if (p.getWriteMethod() == null) {
        throw new IllegalStateException("Property is read-only");
    }
    // Chỉ gọi setter sau khi đã xác minh object, input và quyền.
}
~~~

Đây là **đoạn minh họa**, giả sử biến **info** là **BeanInfo** đã thu được. Trong hệ thống thực, không nên lấy tên property tùy ý từ request rồi cho phép ghi trên mọi object: đó có thể là đường đi sửa trạng thái không mong muốn.

**Lưu ý về side effect:** gọi một getter JavaBeans qua Reflection là *thực thi code*. Nó có thể tải dữ liệu, sửa cache, thực hiện logic nghiệp vụ hoặc ném exception. Trái lại, chỉ lấy **PropertyDescriptor** để đọc tên/getter metadata về cơ bản là bước khám phá, chưa chủ động gọi getter của bean.

## 8. Những lỗi phổ biến khi làm công cụ introspection

### Nhầm field với property

Một class có field **private String name** nhưng không có getter/setter không bảo đảm Introspector trả property **name**. Ngược lại, một property có thể tồn tại mà không có field trùng tên. Cách sửa là xác định rõ công cụ cần **biểu diễn trạng thái nội bộ** hay **giao diện property**.

### Quên loại property class

**Object.getClass()** phù hợp hình thức getter, vì thế khi gọi **getBeanInfo(SomeClass.class)** bạn có thể thấy property **class**. Nếu không muốn nó trong biểu mẫu nghiệp vụ, sử dụng overload với **stopClass=Object.class** hoặc lọc cẩn thận; không xem nó là field người dùng cần chỉnh.

### Gọi setter khi write method bằng null

Read-only property như **summary** không có setter. **getWriteMethod()** trả **null**, nên gọi **invoke** không kiểm tra gây **NullPointerException**. Tương tự, write-only property có thể không có getter.

### Dựa vào thứ tự mặc định

API liệt kê field, method và property không nên được sử dụng như hợp đồng thứ tự xuất ra trong tài liệu/JSON. Dùng **Comparator.comparing(PropertyDescriptor::getName)** hoặc tiêu chí xác định riêng.

### Quên kế thừa hoặc coi JavaBeans chỉ dựa trên field

**getDeclaredFields()** không đưa field của Base vào, trong khi Introspector vẫn có thể thấy getter thừa kế. Nếu dùng **getFields()**, kết quả chỉ có field public được kế thừa/khai báo theo quy tắc Reflection, vẫn không tương đương property.

### Bỏ qua BeanInfo tường minh

**Introspector** có thể sử dụng **FooBeanInfo** nếu lớp cung cấp metadata. **IGNORE_ALL_BEANINFO** chỉ phù hợp nếu bạn chủ động muốn suy luận theo convention và bỏ thông tin đó. Khi tích hợp công cụ hỗ trợ JavaBeans đầy đủ, hãy tôn trọng hợp đồng BeanInfo của thư viện.

### Dùng setAccessible để vượt qua encapsulation

Nếu mục tiêu là gọi getter/setter công khai, việc truy cập trực tiếp private field thường làm mất validation và phá hợp đồng public. Trong Java module system, **setAccessible(true)** còn có thể thất bại với **InaccessibleObjectException**. Ưu tiên API đúng mục đích.

### Quên module java.desktop

**java.beans** thuộc module **java.desktop**. Ở ứng dụng classpath thông thường trên JDK đầy đủ, API có thể dùng bằng import bình thường. Với **named module** có **module-info.java**, cần khai báo phụ thuộc:

~~~java
module com.example.inspector {
    requires java.desktop;
}
~~~

Đoạn trên chỉ minh họa cấu hình module; đặt tên module/package theo dự án thực tế.

## 9. Bài tập thực hành

**Bài tập 1 — Field và property không trùng tên.** Thêm **private String token** vào **Profile**, không thêm accessor. Dự đoán: **getDeclaredFields()** sẽ thấy **token**, nhưng **Introspector** không có property **token** chỉ vì field đó tồn tại.

**Bài tập 2 — Computed property.** Thêm getter **getGreeting()** trả về chuỗi dựa trên **displayName**; không khai báo field **greeting**. Chạy lại inspector: danh sách property có **greeting**, còn danh sách field không có tên đó.

**Bài tập 3 — Read-only vs write-only.** Thêm **setApiKey(String)** nhưng không có **getApiKey()**. Kiểm tra **PropertyDescriptor** thực tế, kết quả **getReadMethod()** và **getWriteMethod()**. Không in hoặc ghi lại bí mật trong bài thực hành.

**Bài tập 4 — Kế thừa.** Thêm getter **getCode()** ở **Base**; so sánh **Profile.class.getDeclaredFields()**, **Profile.class.getFields()** và Introspector trên **Profile** để giải thích khi nào kết quả gồm thành viên kế thừa.

**Bài tập 5 — Tên viết hoa.** Tạo **getURL()**, **setURL(String)** rồi chạy **Introspector.decapitalize("URL")** và kiểm tra tên property. Giải thích vì sao không nên tự viết thuật toán luôn đổi ký tự đầu thành chữ thường.

**Bài tập 6 — Phân biệt method và field annotation.** Gắn một annotation runtime lên field, một annotation lên getter tương ứng. Truy vấn annotation qua **Field** và qua **PropertyDescriptor.getReadMethod()**. Chứng minh chúng là hai vị trí metadata khác nhau.

## 10. Tự kiểm tra và kết luận kỹ thuật

**Câu 1.** Có **private String name** nhưng không có getter/setter. Introspector chắc chắn liệt kê property **name** không?  
**Đáp án:** Không. Field nội bộ không tự tạo property theo JavaBeans convention.

**Câu 2.** Không có field **fullName**, nhưng có **getFullName()** public đúng signature. Introspector có thể trả property **fullName** không?  
**Đáp án:** Có, đó có thể là computed read-only property.

**Câu 3.** Vì sao **getDeclaredFields()** của lớp con không đủ để biết toàn bộ property?  
**Đáp án:** Nó chỉ quét field khai báo trực tiếp, trong khi JavaBeans property đến từ getter/setter và có thể được kế thừa.

**Câu 4.** Khi nào **getWriteMethod()** trả null?  
**Đáp án:** Khi property descriptor không có setter được nhận diện. Công cụ phải xem nó là read-only trong ngữ cảnh đó.

**Câu 5.** Getter public có bảo đảm không gây side effect không?  
**Đáp án:** Không. Nó là code bình thường, chỉ khác ở tên và signature theo convention.

**Câu 6.** Vì sao có thể cần **getBeanInfo(type, Object.class)**?  
**Đáp án:** Để bỏ phần metadata từ **Object**, đặc biệt property **class** do **getClass()**.

**Câu 7.** Property trong JavaBeans và Spring bean có phải cùng khái niệm?  
**Đáp án:** Không. Property mô tả interface đọc/ghi của object; Spring bean là đối tượng được container quản lý.

**Tiêu chí hoàn thành:** bạn giải thích được output chương trình mẫu, chủ động chọn **PropertyDescriptor** hoặc **Field**, kiểm tra getter/setter null, xử lý tên property và kế thừa chính xác, biết giới hạn quyền truy cập và side effect khi triển khai inspector thật.

**Tài liệu tham khảo chính thức:** Oracle Java SE 21 API — **java.beans.Introspector**, **BeanInfo**, **PropertyDescriptor**, **java.lang.reflect.Field**, **java.lang.reflect.Method** và *JavaBeans Specification*.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tooling-design-010">Quét classpath khác khám phá một Class đã biết</a>

<details>
<summary>Xem chi tiết</summary>

# Quét classpath khác khám phá một `Class` đã biết trong Java Reflection

## 1. Câu hỏi nền tảng: đã biết *lớp nào* chưa?

Khi xây công cụ dùng reflection, có hai tác vụ thường bị gọi chung là “quét class”, nhưng đầu vào và phạm vi hoàn toàn khác nhau:

- **Khám phá (introspection) một `Class<?>` đã biết:** chương trình đã có đối tượng `Class`, ví dụ `Order.class`, `object.getClass()` hay kết quả của `Class.forName("com.example.Order")`. Công việc là đọc **metadata của đúng kiểu đó**: tên, interface, constructor, method, field, annotation, superclass và một số thông tin kiểu tổng quát.
- **Quét classpath (classpath scanning):** chương trình **chưa có danh sách đầy đủ các tên lớp ứng viên**. Nó cần duyệt một hoặc nhiều nguồn chứa class như thư mục biên dịch, JAR, hoặc những cơ chế cung cấp lớp khác; từ mỗi resource/class được tìm thấy, xác định ứng viên nào đáp ứng tiêu chí rồi có thể dùng reflection để kiểm tra kỹ hơn.

**Reflection không tự động biến một `Class<?>` thành danh sách tất cả class của ứng dụng.** `Class.getDeclaredMethods()` chỉ trả về những method được khai báo bởi kiểu đang xem; `Class.getDeclaredClasses()` chỉ trả về các member class/interface được khai báo trực tiếp trong kiểu đó, không phải mọi class ở cùng package. Không có phương thức chuẩn `Class.getAllClassesInPackage()` hoặc `ClassLoader.listAllClasses()` cho toàn bộ classpath.

Ví dụ: nếu biết `PaymentService.class`, ta có thể đọc các annotation của `PaymentService` ngay. Nếu muốn tìm **tất cả class có `@Handler`** trong ứng dụng mà chưa biết tên, trước hết phải xác định nguồn ứng viên (qua scan, registry, metadata build-time hoặc cơ chế plugin), rồi mới introspect từng kiểu.

## 2. So sánh đầu vào, kết quả và chi phí

| Khía cạnh | Khám phá một `Class` đã biết | Quét classpath tìm lớp ứng viên |
| --- | --- | --- |
| Đầu vào | Một đối tượng `Class<?>` hoặc tên kiểu đã xác định | Một tập root: thư mục, JAR, package prefix, class loader, module hoặc index |
| Mục tiêu | Hỏi “lớp này có gì?” | Hỏi “trong phạm vi chọn, có lớp nào phù hợp?” |
| API cốt lõi | `getDeclaredMethods`, `getMethods`, `getDeclaredFields`, `getAnnotations`, `getSuperclass` | `Files.walk`, duyệt JAR/resource, module reader, index/framework scanner |
| Phạm vi | Một kiểu, hoặc các kiểu được truy cập có chủ đích từ nó | Có thể rất nhiều file/entry và nhiều kiểu |
| Điều kiện cần | Kiểu có thể được tham chiếu hoặc nạp dưới loader phù hợp | Biết nơi tìm bytecode/resource; xử lý từng định dạng packaging |
| Kết quả | Metadata, `Method`/`Field`/`Constructor`, điều kiện truy cập | Tập *ứng viên*; muốn phân loại thường cần đọc metadata hoặc load class |
| Chi phí | Thường tương ứng số thành viên cần khảo sát | I/O, duyệt entry, lọc, xử lý class loader và đôi khi load nhiều class |
| Lỗi đặc thù | `NoSuchMethodException`, quyền truy cập, lỗi resolution phụ thuộc | Bỏ sót JAR/loader, class hỏng, duplicate names, classpath sai, quét quá rộng |

Quét classpath **thường có thể dẫn đến reflection**, nhưng hai giai đoạn không tương đương. Một scanner tốt nên hạn chế tập ứng viên trước khi cố nạp class; còn khi lớp đã được xác định thì thao tác trên đúng `Class<?>` là đủ, không cần quét lại.

## 3. Khám phá một `Class<?>` đã biết hoạt động thế nào?

Ba cách lấy đối tượng `Class` phổ biến:

```java
Class<String> known = String.class;       // Biết kiểu khi viết mã.
Class<?> runtime = "abc".getClass();      // Biết đối tượng, hỏi kiểu runtime.
Class<?> byName = Class.forName("java.lang.String"); // Biết tên nhị phân.
```

Đây là **các đoạn minh họa** cần đặt trong một method; `Class.forName` cần xử lý hoặc khai báo `ClassNotFoundException`. Dạng một tham số cũng yêu cầu khởi tạo class; muốn hoãn initialization ở tình huống phù hợp có thể dùng `Class.forName(name, false, loader)`.

Khi đã có `Class<?>`, ta có thể:

- `type.getDeclaredMethods()`: các method khai báo trực tiếp trong kiểu đó, gồm cả non-public; không tự gồm method kế thừa.
- `type.getMethods()`: những public method khả kiến theo quy tắc API, gồm cả method public kế thừa.
- `type.getDeclaredFields()` và `getDeclaredConstructors()`: metadata thành viên khai báo trực tiếp.
- `type.getAnnotations()` / `getDeclaredAnnotations()` / `isAnnotationPresent(...)`: xem annotation có `RetentionPolicy.RUNTIME` theo quy tắc kế thừa của API.
- `type.getInterfaces()`, `getSuperclass()`, `isAssignableFrom(...)`: quan hệ kiểu mà JVM mô tả.

Kết quả là metadata của **một kiểu và những mối liên hệ được hỏi cụ thể**. Đọc được metadata không đồng nghĩa có quyền gọi private method hoặc truy cập field qua ranh giới Java Platform Module System (JPMS). Ngoài ra, các API như `getDeclaredMethods()` đôi khi phải resolve những kiểu phụ thuộc trong chữ ký; lỗi liên kết có thể xuất hiện dù đã có `Class<?>`.

## 4. Quét classpath thực chất phải làm những gì?

### Bước A — Xác định phạm vi tìm kiếm

Định nghĩa nguồn ứng viên rõ ràng: một package prefix trong các JAR do ứng dụng sở hữu, thư mục output build, bộ plugin riêng, hay cấu hình module. Không mặc định quét mọi thứ từ JDK, dependency, ứng dụng và tất cả custom loader. Một classpath có thể bao gồm nhiều entry; **module path không đồng nhất với classpath**, còn custom class loader có thể không công bố nơi lưu bytecode.

### Bước B — Liệt kê class resource

Trong thư mục lớp, scanner có thể dùng `Files.walk(root)` để tìm file `.class` rồi chuyển đường dẫn tương đối thành tên nhị phân. Trong JAR, có thể duyệt entries bằng `JarFile`; phải xử lý thư mục, `module-info.class`, `package-info.class` và tên lớp lồng có dấu `$` theo mục đích. Với các cơ chế đóng gói khác (nested JAR, ứng dụng chạy qua launcher riêng, Java modules, runtime image), cần adapter tương ứng.

**`ClassLoader.getResources("package/path")` không phải wildcard quét tất cả class.** API tìm những resource khớp **tên được chỉ định** từ nguồn mà loader thấy. Không nên dựa vào việc liệt kê URL của package resource để kết luận đã bao phủ mọi class có thể nạp. Tương tự, `ClassLoader.getDefinedPackages()` không trả danh sách toàn bộ tên class.

### Bước C — Lọc ứng viên

Scanner có thể lọc path/tên lớp trước, rồi chọn một trong hai hướng:

1. **Đọc metadata bytecode mà không load từng class**, qua thư viện phân tích classfile hoặc chỉ mục có sẵn. Cách này có thể giảm side effect và số class bị load; vẫn phải quan tâm phiên bản bytecode và lớp hỗ trợ.
2. **Load class để kiểm tra bằng reflection**, ví dụ `Class.forName(binaryName, false, chosenLoader)` rồi `isAnnotationPresent` hoặc `isAssignableFrom`. `initialize=false` tránh yêu cầu chạy static initializer ngay lúc đó, **không cam kết không có mọi tác động từ loading/linking**, và class vẫn có thể thất bại vì dependency thiếu, binary mismatch hoặc loader sai.

Annotation dùng để tìm khi chạy phải có `@Retention(RetentionPolicy.RUNTIME)` nếu scanner lọc bằng reflection. Annotation có retention `SOURCE` hoặc `CLASS` không nhất thiết hiện diện qua `Class.getAnnotation(...)` khi chạy.

### Bước D — Quyết định cách dùng kết quả

Sau khi chọn lớp phù hợp, công cụ có thể lưu metadata, tạo registry, kết nối provider hoặc khởi tạo đối tượng (nếu thực sự cần). **Phát hiện một class không có nghĩa nên tạo instance ngay**: class có thể abstract, không có constructor thích hợp, bị giới hạn module hoặc có side effect khi khởi tạo. Nên xác định lifecycle và điều kiện hợp lệ của ứng viên trước khi instantiate.

## 5. Ví dụ Java 17+ chạy được: introspection rồi quét một thư mục

Ví dụ này cố ý dùng **một thư mục output được biết rõ** để thấy sự khác biệt. Nó không giả vờ là một scanner hỗ trợ JAR/module đầy đủ.

Tạo tệp duy nhất `ScanVsInspectDemo.java`, không khai báo `package`:

```java
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface ToolComponent {}

interface Tool {
    String run();
}

@ToolComponent
final class FormatterTool implements Tool {
    public String run() {
        return "format";
    }
}

@ToolComponent
final class ParserTool implements Tool {
    public String run() {
        return "parse";
    }
}

final class NotATool {}

public class ScanVsInspectDemo {
    static void inspectKnownClass() throws NoSuchMethodException {
        Class<FormatterTool> known = FormatterTool.class;
        System.out.println("Known: " + known.getSimpleName());
        System.out.println("Method: "
                + known.getDeclaredMethod("run").getName());
        System.out.println("Implements Tool: "
                + Tool.class.isAssignableFrom(known));
    }

    static void scanOwnedDirectory() throws IOException, ClassNotFoundException {
        Path root = Path.of("out");
        List<String> found = new ArrayList<>();

        try (Stream<Path> files = Files.list(root)) {
            for (Path file : files
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".class"))
                    .toList()) {
                String fileName = file.getFileName().toString();
                String binaryName =
                        fileName.substring(0, fileName.length() - 6);

                Class<?> candidate = Class.forName(
                        binaryName, false,
                        ScanVsInspectDemo.class.getClassLoader());

                if (candidate.isAnnotationPresent(ToolComponent.class)
                        && Tool.class.isAssignableFrom(candidate)
                        && !candidate.isInterface()) {
                    found.add(candidate.getSimpleName());
                }
            }
        }

        Collections.sort(found);
        System.out.println("Discovered: " + String.join(", ", found));
    }

    public static void main(String[] args) throws Exception {
        inspectKnownClass();
        scanOwnedDirectory();
    }
}
```

Chạy từ thư mục có tệp nguồn. Tạo thư mục output rồi biên dịch (Linux/macOS):

```bash
mkdir -p out
javac -encoding UTF-8 -d out ScanVsInspectDemo.java
java -cp out ScanVsInspectDemo
```

PowerShell (Windows):

```powershell
New-Item -ItemType Directory -Force out | Out-Null
javac -encoding UTF-8 -d out ScanVsInspectDemo.java
java -cp out ScanVsInspectDemo
```

Kết quả chuẩn:

```text
Known: FormatterTool
Method: run
Implements Tool: true
Discovered: FormatterTool, ParserTool
```

**Giải thích từng phần:**

1. `FormatterTool.class` cho ta đối tượng `Class` đã biết ngay từ mã nguồn; `getDeclaredMethod("run")` tìm đúng method thuộc kiểu đó. Không có bước nào cần liệt kê tất cả file `.class`.
2. `Files.list(Path.of("out"))` **chỉ liệt kê trực tiếp** các file của một thư mục output; code lọc tên `.class` và chuyển phần tên tệp thành tên lớp vì ví dụ ở **default package**. Với package thực tế, phải duyệt thư mục con và đổi đường dẫn tương đối thành tên nhị phân đầy đủ.
3. `Class.forName(binaryName, false, loader)` lấy `Class<?>` cho từng ứng viên qua class loader ứng dụng, không yêu cầu chạy initializer ngay. Với bộ lớp của ví dụ, tên class trùng tên file cơ bản vì không dùng các định dạng hay layout đặc biệt.
4. `isAnnotationPresent(ToolComponent.class)` loại các lớp không đánh dấu `@ToolComponent`; `Tool.class.isAssignableFrom(candidate)` chỉ nhận kiểu triển khai hợp đồng `Tool`; điều kiện `!candidate.isInterface()` loại interface khỏi tập ứng viên.
5. `Collections.sort(found)` bảo đảm output ổn định, độc lập thứ tự hệ thống tệp. Hai lớp được phát hiện là `FormatterTool` và `ParserTool`. `NotATool` không được đánh dấu và không triển khai `Tool`.

**Giới hạn có chủ ý:** ví dụ không quét JAR, package lồng, module path, các class loader khác, multi-release JAR, và không xử lý class hỏng. Nó chỉ minh họa dòng xử lý `liệt kê tệp → chuyển tên → lấy Class → introspection → lọc`. Trong công cụ thực tế, phải chọn scanner/thư viện phù hợp và thiết kế xử lý lỗi theo từng nguồn.

## 6. Thiết kế scanner dùng thực tế: các giới hạn khó

### Classpath không phải một thư mục duy nhất

Một ứng dụng có thể nạp class từ nhiều thư mục và JAR. `java.class.path` chỉ phản ánh một phần cấu hình trong nhiều môi trường, không định nghĩa đầy đủ khả năng của custom class loader, module layer hay nested JAR. Class có thể đến từ runtime image (`jrt:`), module path, nguồn động hoặc cơ chế container/framework. Không có API reflection phổ quát để “liệt kê toàn bộ class mà JVM có thể nạp trong tương lai”.

### Cùng tên không nhất thiết cùng kiểu runtime

Hai class loader có thể định nghĩa hai class cùng binary name nhưng khác định danh runtime. Truy vấn `candidate.getName()` bằng nhau chưa đủ để kết luận `candidateA == candidateB`. Khi scanner nạp ứng viên, phải dùng loader đúng biên plugin/framework; không tùy tiện dùng system class loader cho mọi loại ứng dụng.

### Metadata, bytecode và annotation

Nếu chỉ muốn lập **chỉ mục tên class**, đọc bytecode mà không thực hiện `Class.forName` có thể phù hợp hơn. Nếu cần kiểm tra quan hệ subtype hoặc annotation runtime, reflection hữu ích sau khi đã lọc tên. Với annotation metadata, cần xét `RetentionPolicy`, `@Inherited` (chỉ có hiệu lực theo quy tắc đối với annotation trên superclass của class), và những trường hợp annotation trên interface không tự động trở thành annotation của class triển khai.

### Thứ tự, lỗi và độ tin cậy

- Một JAR hỏng, class biên dịch bằng JDK mới hơn, superclass vắng mặt hoặc một class không thể link có thể làm `Class.forName` thất bại. Ghi ngữ cảnh ứng viên, loader và cause; đừng lặng lẽ coi scan là đầy đủ nếu có lỗi.
- Scan cần có phạm vi package/JAR cho phép, giới hạn tài nguyên, chống duyệt đường dẫn ngoài root; một thư mục/JAR do bên không tin cậy cung cấp không nên được coi như nguồn plugin có quyền chạy code.
- Sắp xếp kết quả theo quy tắc xác định và có chính sách duplicate name/classloader. Tránh dựa vào thứ tự `Files.walk` hoặc entries JAR để quyết định ưu tiên provider.
- Nếu classpath ổn định, cache index hoặc dùng metadata build-time; đừng quét lại mọi JAR trên mỗi request. Quét startup có thể chậm và tốn I/O, nhất là trong ứng dụng lớn.
- Không tự động `newInstance` mọi class vừa phát hiện. Chọn constructor, quản lý vòng đời, quyền truy cập, class visibility và lỗi khởi tạo theo hợp đồng rõ ràng.

### JPMS, đóng gói đặc biệt và native image

Khi dùng JPMS, các module được resolve trong layer có thông tin riêng; `ModuleReader` có API để liệt kê resource trong một module theo phạm vi được mở, nhưng đó không tương đương quét mọi classpath. `exports`/`opens` ảnh hưởng quyền truy cập; việc tìm được tên class không đồng nghĩa thực hiện deep reflection được.

Spring Boot executable JAR hoặc framework khác có thể chứa thư viện trong JAR lồng. Một scanner chỉ hiểu `Files.walk` hoặc URL `file:` sẽ bỏ sót các lớp đó. Với native-image/closed-world runtime, kiểu reachable và metadata phản chiếu có thể cần được đăng ký hoặc khai báo ở build-time; quét động như trên JVM thường không được đảm bảo sẽ hoạt động nguyên trạng.

## 7. Khi nào nên quét, khi nào chọn giải pháp khác?

| Nhu cầu thực tế | Cách phù hợp |
| --- | --- |
| Đọc method/field/annotation của một kiểu đã biết | Lấy `Class<?>` rồi dùng reflection; không quét classpath |
| Đã biết các implementation lúc phát triển | Registry/factory/DI cấu hình tường minh, có thể tạo bằng compile-time annotation processor |
| Tìm implementation plugin đã đăng ký theo Java SPI | `ServiceLoader<ServiceType>`; provider khai báo qua `META-INF/services` hoặc `provides ... with` của module |
| Xây framework tìm mọi class có annotation theo package | Scanner có phạm vi rõ, hỗ trợ packaging/class loader và metadata cần đọc; cân nhắc index ở build-time |
| Cần danh sách class hiện có trong một JAR tự quản | Liệt kê entry JAR hoặc classfile; sau đó chọn tiêu chí lọc; đừng mặc định load mọi class |
| Cần quan sát class đã được JVM load | Dùng công cụ chẩn đoán/runtime instrumentation phù hợp; **khác** việc tìm tất cả class có thể tải từ classpath |

`ServiceLoader` có thể làm plugin discovery mà không cần tự quét mọi `.class`, nhưng **nó chỉ phát hiện các provider được đăng ký theo hợp đồng SPI**. Dependency injection framework có cơ chế component scanning/metadata/index riêng; không nên nhầm các cơ chế ấy với API reflection `Class` thuần.

## 8. Cách quyết định và tự kiểm tra

Hãy hỏi theo thứ tự:

1. **Đầu vào đã có `Class<?>` hoặc object cụ thể chưa?** Nếu có, truy vấn metadata của chính kiểu đó trước.
2. **Có cần tìm tên lớp chưa biết trước không?** Nếu có, xác định source được phép và cách khám phá (SPI, registry, build index, scanner).
3. **Phải hỗ trợ thư mục, JAR, nested JAR hay module?** Chọn cơ chế phù hợp môi trường triển khai; không coi một ví dụ `Files.list` là giải pháp tổng quát.
4. **Có cần load/initialize lớp khi scan không?** Ưu tiên lọc metadata trước; nếu dùng `Class.forName`, cân nhắc `initialize=false` và chính sách xử lý lỗi.
5. **Kết quả sẽ dùng để làm gì?** Đọc metadata, đăng ký handler hay tạo instance? Mỗi bước có yêu cầu về constructor, quyền truy cập, lifecycle và an toàn khác nhau.

**Câu 1.** Có `UserService.class` và chỉ muốn lấy danh sách method `public`. Cần classpath scanning không?

**Đáp án:** Không. Dùng `UserService.class.getMethods()`; nếu chỉ muốn method khai báo trực tiếp, dùng `getDeclaredMethods()`.

**Câu 2.** Gọi `MyType.class.getDeclaredClasses()` có tìm mọi class trong package `com.example` không?

**Đáp án:** Không. Nó mô tả những member type được khai báo trực tiếp bên trong `MyType`, không duyệt package hay classpath.

**Câu 3.** Code gọi `ClassLoader.getResources("com/example")` rồi nhận hai URL. Có thể khẳng định tất cả class trong package đã được tìm hết?

**Đáp án:** Không. API tìm resource theo tên được yêu cầu; loader và cách đóng gói quyết định khả năng liệt kê. Hai URL không chứng minh đã bao phủ các class trong nested JAR, custom loader hoặc module khác.

**Câu 4.** Muốn phát hiện các implementation được phát hành dưới dạng provider SPI, bắt buộc tự duyệt tất cả file `.class`?

**Đáp án:** Không. `ServiceLoader` tận dụng đăng ký provider chuẩn của Java. Chỉ khi yêu cầu là tìm những lớp chưa đăng ký theo tiêu chí riêng mới cân nhắc scanner thích hợp.

**Câu 5.** `Class.forName(name, false, loader)` có bảo đảm tuyệt đối không có lỗi và không có tác động runtime?

**Đáp án:** Không. Nó tránh yêu cầu khởi tạo class ở lời gọi đó, nhưng class loading/linking và resolution có thể phát sinh lỗi; về sau class vẫn có thể phải khởi tạo.

**Kết luận:** **Introspection trả lời “kiểu này có những gì?”**, còn **classpath scanning trả lời “trong những nguồn đã chọn có kiểu ứng viên nào?”**. Reflection là công cụ kiểm tra metadata sau khi có `Class<?>`; scanner/registry/SPI là cơ chế xác định tập kiểu cần kiểm tra. Chọn đúng tầng giúp công cụ nhanh, kiểm soát được phạm vi và dễ chạy ổn định qua các kiểu đóng gói Java.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="tooling-design-011">Giữ constructor, setter và bất biến đối tượng làm ranh giới thiết kế</a>

<details>
<summary>Xem chi tiết</summary>

# Giữ constructor, setter và bất biến đối tượng làm ranh giới thiết kế khi dùng Reflection

Một `Field` có thể ghi được bằng Reflection nhưng điều đó chưa có nghĩa thao tác ghi là **hợp lệ về nghiệp vụ**. Ví dụ `Registration` yêu cầu sức chứa từ 1 đến 10, số người không vượt sức chứa và danh sách không bị sửa từ bên ngoài. Gán trực tiếp `capacity = 0` phá invariant dù JVM chấp nhận thao tác.

Bài này tập trung vào **ranh giới trạng thái**: tạo qua constructor/factory có kiểm tra, cập nhật qua setter hoặc domain method duy trì invariant, chỉ công bố object sau validation. Với quan hệ nhiều field, hai setter độc lập có thể để lộ trạng thái trung gian sai; một thao tác atomic theo hợp đồng domain thường phù hợp hơn. Ví dụ phản chứng dùng `Field.setInt` để cho thấy vì sao khả năng truy cập không thay thế domain contract.

## 1. Constructor, setter và invariant có vai trò khác nhau

**Invariant** là điều kiện mà một đối tượng được thiết kế để duy trì trong suốt vòng đời quan sát được của nó. Ví dụ một lớp `Registration` có:

- `code` không phải `null` hoặc chuỗi trắng; định danh không thay đổi sau khi tạo.
- `capacity` nằm trong khoảng `1..10`.
- `attendees.size() <= capacity`.
- Danh sách người tham dự không được để mã gọi bên ngoài sửa lén mà không thông qua phương thức kiểm tra.

Những quy tắc này được kiểm tra ở những ranh giới sau:

| Ranh giới | Trách nhiệm | Ví dụ |
|---|---|---|
| Constructor hoặc static factory | Nhận đủ giá trị tối thiểu, kiểm tra trước khi đối tượng được sử dụng | `new Registration("JAVA", 3)` |
| Setter có chủ đích | Kiểm tra một phép thay đổi có hợp lệ so với trạng thái hiện tại | `setCapacity(2)` |
| Phương thức nghiệp vụ | Bảo vệ quy tắc phụ thuộc nhiều field và diễn đạt ý định rõ | `register("Lan")`, `reschedule(start,end)` |
| Getter / view | Cung cấp cách quan sát trạng thái mà không lộ mutable internals | `List.copyOf(...)`, danh sách không sửa được |
| Reflection adapter | Tìm chữ ký, chuyển đối số, **gọi constructor/method công khai** rồi giữ nguyên lỗi | `Constructor.newInstance(...)`, `Method.invoke(...)` |

Một setter công khai **không tự động tốt** chỉ vì có tiền tố `set`. Setter cho phép tùy ý sửa `id` hoặc sửa từng field của một invariant liên trường có thể khiến đối tượng luôn có lúc ở trạng thái sai. Những dữ liệu không nên đổi hãy giữ `final` hoặc chỉ cung cấp một phép cập nhật nguyên khối có validation. Với domain model, ý nghĩa của phương thức thường quan trọng hơn chuẩn tên JavaBeans.

## 2. Ba đường đi qua Reflection và hậu quả

Giả sử runtime đã có `Class<?>` và dữ liệu cấu hình:

~~~text
Đầu vào ngoài (cấu hình / JSON / plugin metadata)
                     |
                     v
       Chuẩn hóa + validate cấu trúc
                     |
          Chọn ranh giới domain
           /                  \
          v                    v
 Constructor/factory      Setter/method
          |                    |
          +---------+----------+
                    v
       Đối tượng với invariant hợp lệ
                    |
                    v
             Hoạt động tiếp
~~~

### Đường nên ưu tiên: gọi constructor phù hợp

~~~java
Constructor<Registration> constructor =
        Registration.class.getConstructor(String.class, int.class);
Registration registration = constructor.newInstance("JAVA", 3);
~~~

Đây là **đoạn trích** từ chương trình đầy đủ ở mục 3. `getConstructor(...)` chỉ tìm **constructor public** có đúng danh sách **kiểu tham số**. `int.class` khác `Integer.class` trong bước tìm chữ ký, dù `newInstance(...)` có thể nhận đối số `Integer` được unbox khi gọi constructor nhận `int`.

Khi constructor tự ném `IllegalArgumentException` vì dữ liệu sai, `Constructor.newInstance` ném `InvocationTargetException` có `getCause()` là exception gốc. Công cụ không được bỏ qua sự từ chối rồi cố gán field bằng đường khác.

### Đường cập nhật: gọi setter hoặc method công khai

~~~java
Method method = Registration.class.getMethod("setCapacity", int.class);
method.invoke(registration, 2);
~~~

`getMethod(...)` tìm method public với tên và kiểu tham số tương ứng. `Method.invoke(...)` thực thi **logic trong setter**: khoảng hợp lệ, tương quan với các field khác, cập nhật và những hậu điều kiện mà lớp tự đảm bảo. Nếu setter từ chối, lỗi target được bọc trong `InvocationTargetException`; việc unwrap cause phục vụ chẩn đoán, không tạo lý do để bỏ validation.

### Đường rủi ro: ghi thẳng field

~~~java
Field field = Registration.class.getDeclaredField("capacity");
field.setAccessible(true); // Chỉ minh họa trong môi trường có quyền phù hợp
field.setInt(registration, 0);
~~~

**Không dùng đoạn này làm chiến lược cập nhật dữ liệu ứng dụng.** Nếu quyền truy cập sâu được cho phép, `Field.setInt(...)` ghi giá trị vào field mà **không gọi `setCapacity`**. Đối tượng có thể mang `capacity = 0` dù setter cấm. Đây là lỗi ở cấp **hợp đồng domain**, ngay cả khi thao tác Reflection thành công về mặt kỹ thuật.

`getDeclaredField` chỉ giúp tìm field được khai báo trực tiếp trên lớp, kể cả field private. Nó không có nghĩa hệ thống cho phép đọc/ghi field đó trong mọi cấu hình Java Platform Module System (JPMS). `setAccessible(true)` có thể bị từ chối nếu package trong named module khác không được mở (`opens`). Quyền truy cập sâu cũng không có nghĩa có quyền sửa mọi `final`; đặc biệt record fields, static final fields và final fields của hidden classes có giới hạn riêng. Đừng coi `setAccessible` là bước mặc định của mọi công cụ.

## 3. Chương trình Java 17+ hoàn chỉnh, có cả ví dụ phản chứng

Tạo file **`ConstructionBoundaryDemo.java`** với toàn bộ mã sau. Trong chương trình, `Registration` là lớp `public static final` lồng trong một chương trình độc lập; invariant được định nghĩa trong constructor, `setCapacity` và `register`. Cuối chương trình có **một thử nghiệm cố ý ghi thẳng field** để chứng minh setter đã bị bypass; sau đó chương trình phục hồi trạng thái qua setter.

~~~java
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class ConstructionBoundaryDemo {
    public static final class Registration {
        private final String code;
        private int capacity;
        private List<String> attendees = List.of();

        public Registration(String code, int capacity) {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("code required");
            }
            this.code = code;
            setCapacity(capacity);
        }

        public void setCapacity(int newCapacity) {
            if (newCapacity < 1 || newCapacity > 10) {
                throw new IllegalArgumentException("capacity must be 1..10");
            }
            if (newCapacity < attendees.size()) {
                throw new IllegalArgumentException("capacity below attendees");
            }
            capacity = newCapacity;
        }

        public void register(String name) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name required");
            }
            if (attendees.size() == capacity) {
                throw new IllegalStateException("full");
            }
            List<String> next = new ArrayList<>(attendees);
            next.add(name);
            attendees = List.copyOf(next);
        }

        public String getCode() {
            return code;
        }

        public int getCapacity() {
            return capacity;
        }

        public List<String> getAttendees() {
            return attendees;
        }

        public boolean validState() {
            return capacity >= 1 && capacity <= 10
                    && attendees.size() <= capacity;
        }
    }

    @FunctionalInterface
    interface ReflectionAction {
        void run() throws ReflectiveOperationException;
    }

    static void showRejection(String label, ReflectionAction action)
            throws ReflectiveOperationException {
        try {
            action.run();
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            System.out.println(label + " -> " +
                    cause.getClass().getSimpleName() + ": " + cause.getMessage());
        }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Constructor<Registration> constructor =
                Registration.class.getConstructor(String.class, int.class);
        Method register = Registration.class.getMethod("register", String.class);
        Method setCapacity = Registration.class.getMethod("setCapacity", int.class);

        Registration r = constructor.newInstance("JAVA", 3);
        register.invoke(r, "Lan");
        register.invoke(r, "Minh");
        System.out.println("created = " + r.getCode() + ", " + r.getCapacity()
                + ", " + r.getAttendees() + ", valid=" + r.validState());

        showRejection("bad setter", () -> setCapacity.invoke(r, 1));
        showRejection("bad constructor", () -> constructor.newInstance(" ", 3));

        try {
            r.getAttendees().add("An");
        } catch (UnsupportedOperationException ex) {
            System.out.println("external list mutation -> " +
                    ex.getClass().getSimpleName());
        }

        setCapacity.invoke(r, 2);
        System.out.println("setter success = " + r.getCapacity()
                + ", valid=" + r.validState());

        Field capacityField = Registration.class.getDeclaredField("capacity");
        capacityField.setAccessible(true);  // DEMO ONLY: breaks encapsulation.
        capacityField.setInt(r, 0);          // Deliberately bypasses setter.
        System.out.println("field bypass = " + r.getCapacity()
                + ", valid=" + r.validState());

        setCapacity.invoke(r, 2);
        System.out.println("restored = " + r.validState());
    }
}
~~~

Biên dịch và chạy trong thư mục có file:

~~~bash
javac --release 17 -encoding UTF-8 ConstructionBoundaryDemo.java
java ConstructionBoundaryDemo
~~~

**Stdout đã kiểm chứng khi chạy Java 17-compatible trong unnamed module:**

~~~text
created = JAVA, 3, [Lan, Minh], valid=true
bad setter -> IllegalArgumentException: capacity below attendees
bad constructor -> IllegalArgumentException: code required
external list mutation -> UnsupportedOperationException
setter success = 2, valid=true
field bypass = 0, valid=false
restored = true
~~~

**Diễn giải từng nhóm kết quả:**

1. Constructor kiểm tra `code` rồi gọi `setCapacity`. Hai lần `register.invoke(...)` thêm người vào danh sách mới, cập nhật lại bằng `List.copyOf(...)`; trạng thái ban đầu hợp lệ.
2. `setCapacity.invoke(r, 1)` chạy setter, nhưng một nhóm đã có hai người nên bị từ chối. `InvocationTargetException.getCause()` là `IllegalArgumentException("capacity below attendees")`. Không có thay đổi một phần vào `capacity`.
3. `constructor.newInstance(" ", 3)` thực thi constructor; mã rỗng khiến constructor từ chối. Đây là trường hợp **target constructor** ném exception, được wrapper Reflection giữ cause.
4. `getAttendees().add("An")` thất bại vì danh sách trả về không sửa được. Nếu getter lộ `ArrayList` nội bộ có thể sửa, caller đã có cách phá invariant **không cần Reflection**.
5. `setCapacity.invoke(r, 2)` thành công vì `2` đủ cho hai người, sau đó `validState()` tiếp tục bằng `true`.
6. Ví dụ phản chứng `Field.setInt(r, 0)` ghi trực tiếp vào `capacity`, không gọi logic setter, kết quả `valid=false`. Đoạn cuối khôi phục bằng setter hợp lệ.
7. Chương trình chạy trong một unnamed module. Trong named-module application, đoạn `setAccessible(true)` có thể thất bại; điều đó càng nhấn mạnh rằng công cụ không nên thiết kế dựa trên việc luôn có quyền mở private field.

`showRejection` chỉ bắt `InvocationTargetException` của ví dụ. Trong một binder thực tế, cần xử lý riêng `NoSuchMethodException`, `IllegalAccessException`, `InstantiationException`, `IllegalArgumentException` do sai đối số gọi Reflection, và những lỗi liên quan đến hệ thống module. Không gom mọi lỗi vào một nhãn “validation thất bại”.

## 4. Ranh giới thiết kế cho công cụ Reflection

Khi viết mapper, DI container nhỏ, plugin loader hoặc tool nhập cấu hình, nên ra quyết định theo **hợp đồng khởi tạo/cập nhật**.

| Nhu cầu | Hướng ưu tiên | Tránh |
|---|---|---|
| Tạo đối tượng có trường bắt buộc | Constructor tham số, named factory, builder có bước `build()` kiểm tra | Tạo object rỗng rồi nhồi private fields |
| Đổi một giá trị độc lập | Method công khai có kiểm tra, ví dụ `setCapacity(...)` | `Field.set(...)` bỏ qua setter |
| Đổi nhiều field phụ thuộc nhau | Method cập nhật nguyên khối, tạo immutable object mới | Gọi nhiều setter làm lộ trạng thái trung gian sai |
| Nạp một value object bất biến | Constructor/canonical constructor của record | Ghi trực tiếp `final` field sau khi dựng |
| Điền collection | Chuyển đổi dữ liệu đầu vào và sao chép bảo vệ | Giữ tham chiếu `List` mutable của caller |
| Schema thiếu method cần thiết | Báo mapping không hỗ trợ, hoặc dùng adapter do chủ lớp định nghĩa | Thử `setAccessible(true)` để “lách” hợp đồng |
| Sai giá trị domain | Trả validation error giữ cause và tên property | Bỏ validation để cố tạo object |
| DTO chứa dữ liệu thuần | Có thể dùng binder/Bean convention đã thống nhất | Áp dụng cùng giả định cho mọi domain entity |

**Metadata không phải hợp đồng đủ mạnh.** Biết field có kiểu `int` chỉ bảo đảm điều kiện kiểu ở mức Java; nó không nói rằng số chỗ phải thuộc khoảng nào hoặc phụ thuộc bao nhiêu người đã đăng ký. Kể cả annotation `@NotNull` hoặc `@Min`, tool phải biết rõ lúc nào và bằng cơ chế nào validator được chạy. Đọc annotation bằng Reflection **không tự kích hoạt validation**. Nếu công cụ quyết định dùng Bean Validation, nó cần gọi validator theo kiến trúc thực tế và kiểm soát thời điểm object trở nên visible.

Một mapping pipeline hợp lý:

~~~text
Untrusted input
    |
    v
Parse theo schema -> chuyển kiểu -> lỗi cấu trúc nếu sai
    |
    v
Chọn constructor/factory theo contract đã cho phép
    |
    v
Gọi constructor -> validation ở domain
    |
    +-- Reject -> trả lỗi, giữ cause, không publish object
    |
    v
Gọi các method cập nhật cho phép (nếu có)
    |
    +-- Reject -> bỏ object chưa publish / rollback theo kiến trúc
    |
    v
Xác nhận object hợp lệ, sau đó mới publish
~~~

Nếu một tool tạo object thành công rồi nhiều setter về sau gây lỗi, tool phải đảm bảo **đối tượng chưa được công bố hoặc lưu vào hệ thống** trước khi hoàn tất. Không có transaction tự động chỉ nhờ dùng Reflection. Với update trên object đã tồn tại và được chia sẻ, cần thiết kế atomic operation hoặc transaction/domain command phù hợp; nếu fail giữa chừng có thể còn trạng thái trung gian.

## 5. Bất biến phụ thuộc nhiều field: setter rời rạc có thể chưa đủ

Xét khoảng thời gian:

~~~java
public record TimeWindow(LocalDate start, LocalDate end) {
    public TimeWindow {
        if (start == null || end == null || end.isBefore(start)) {
            throw new IllegalArgumentException("invalid window");
        }
    }
}
~~~

Đây là **mảnh mã minh họa** cần đặt trong một tệp Java hợp lệ và `import java.time.LocalDate;` nếu chạy riêng. Record có canonical constructor kiểm tra rằng `start <= end`. Khi cần cập nhật, tạo một record mới qua constructor hợp lệ:

~~~java
TimeWindow next = new TimeWindow(newStart, newEnd);
~~~

Nếu thiết kế một mutable class có cả `setStart` và `setEnd` độc lập, thứ tự gọi có thể tạm thời vi phạm `start <= end`. Ví dụ đang là ngày 10 đến ngày 20, muốn đổi thành ngày 21 đến ngày 30: gọi `setStart(21)` trước sẽ có lúc `start > end`; gọi `setEnd(30)` trước có thể được nhưng phụ thuộc vào dữ liệu. Một phương thức `reschedule(newStart, newEnd)` kiểm tra cặp giá trị **trước khi commit cả hai** sẽ rõ ràng hơn.

Bài học cho công cụ tạo object từ nhiều field: **“Có setter” không đồng nghĩa “có thể set theo thứ tự tùy ý”.** Công cụ phải biết protocol được định nghĩa bởi chủ lớp. Constructor nhận đủ tham số, immutable value object hoặc builder thường phù hợp hơn khi invariant là quan hệ liên trường.

### Đừng gọi method có thể override từ constructor một cách vô ý

Trong demo, `Registration` là `final` nên không có subclass override `setCapacity`. Nhưng ở một lớp **không `final`**, gọi setter có thể override từ constructor sẽ dispatch sang method của subclass trước khi phần subclass được khởi tạo đầy đủ. Đây có thể là nguồn lỗi hoặc rò rỉ `this`.

Thiết kế tổng quát an toàn hơn: constructor dùng **private helper không override được** để validate, rồi gán field; setter gọi lại cùng helper ấy. Ví dụ mảnh minh họa:

~~~java
private static int requireCapacity(int value) {
    if (value < 1 || value > 10) {
        throw new IllegalArgumentException("capacity must be 1..10");
    }
    return value;
}

// Bên trong constructor hoặc setter:
this.capacity = requireCapacity(value);
~~~

Đoạn trên không biên dịch độc lập vì giả sử nằm trong lớp có field `capacity` và biến `value`. Không nên xây abstraction Reflection buộc mọi class phải có no-args constructor chỉ để gán field sau đó.

## 6. Setter, field, accessor và Java Platform Module System

Các API Reflection thường gặp:

| API | Phạm vi tìm | Đảm bảo gì? |
|---|---|---|
| `type.getConstructor(paramTypes)` | Constructor **public** với chữ ký chính xác | Có metadata constructor; việc gọi còn chịu access rules |
| `type.getDeclaredConstructor(paramTypes)` | Constructor được khai báo trong type, bất kể modifier | Có metadata; có thể không gọi được nếu non-public |
| `type.getMethod(name, paramTypes)` | Method **public** có thể thấy, bao gồm inherited phù hợp | Có metadata method; `invoke` vẫn có thể thất bại |
| `type.getDeclaredMethod(name, paramTypes)` | Method khai báo trực tiếp trong type | Bao gồm non-public; không tự cấp quyền gọi |
| `type.getField(name)` | Field public có thể thấy | Không chạy setter khi `Field.set` |
| `type.getDeclaredField(name)` | Field khai báo trực tiếp, gồm private | Không tự cấp quyền đọc/ghi hoặc validation |
| `AccessibleObject.trySetAccessible()` | Thử mở quyền truy cập nếu runtime cho phép | Trả `false` khi không thể mở, không biến member thành domain API |

**JPMS:** `exports` và `opens` là hai khái niệm khác nhau. Package `exports` cho phép sử dụng public API từ module khác theo điều kiện tương ứng; deep Reflection vào non-public members của module khác thường cần package `opens` tới module công cụ. Thêm `--add-opens` cho JVM có thể giúp một trường hợp tích hợp cụ thể, nhưng là quyết định triển khai/module policy, **không phải cách sửa mặc định** khi công cụ không tìm thấy setter.

**`final` không phải giấy phép ghi ngược:** Từ `Field` Java 17, một số loại final field không thể sửa qua Reflection, chẳng hạn `static final`, final fields trong record và final fields trong hidden class. Với những final instance field khác, Javadoc cảnh báo việc sửa sau khi object đã được công bố có thể cho kết quả không dự đoán được. Thiết kế công cụ không nên dựa vào sửa final field để “re-hydrate” domain entity.

**`private` không tự làm object bất biến:** Nếu getter trả lại một `List` có thể sửa do object giữ nội bộ, caller vẫn có thể thay đổi dữ liệu mà không cần Reflection. Trong demo, `List.copyOf` tạo danh sách không sửa được để giữ ranh giới; khi nhận input mutable, có thể chủ động tạo snapshot thay vì giữ tham chiếu của caller.

### Method signature phải đúng, không suy luận từ tên

Reflection tìm method theo **tên và mảng kiểu tham số**, ví dụ `getMethod("setCapacity", int.class)`. `getMethod("setCapacity", Integer.class)` sẽ không tự khớp với tham số `int`. Giá trị đưa cho `Method.invoke` có thể trải qua invocation conversions như unboxing nhưng bước tra cứu chữ ký là riêng biệt. Với overload, tự lấy method đầu tiên có tên “set...” có thể dẫn tới chọn sai contract. Một binder nên chọn method theo schema rõ ràng hoặc fail với thông báo có ích.

## 7. Validation và exception không được phép biến mất

Nếu constructor hay setter từ chối dữ liệu, thao tác phản chiếu sẽ có ngoại lệ ở tầng gọi:

~~~java
try {
    method.invoke(target, value);
} catch (InvocationTargetException ex) {
    Throwable cause = ex.getCause();
    throw new IllegalStateException("Could not update property", cause);
}
~~~

Đây là **đoạn xử lý minh họa**: các biến `method`, `target`, `value` giả định đã có và cần xử lý thêm `IllegalAccessException`. Cách bọc trong `IllegalStateException(String, Throwable)` giữ cause, giúp log và caller truy ngược lỗi domain. Có thể ném lại chính loại ngoại lệ gốc nếu hợp đồng API cho phép. Không lấy mỗi `cause.getMessage()` rồi tạo một exception mới không có cause.

Phân biệt:

- **Sai schema hoặc conversion:** JSON string `"abc"` không đổi được thành `int`, method không tồn tại hoặc receiver sai kiểu. Đây là lỗi adapter/binding; `Method.invoke` có thể ném `IllegalArgumentException` trực tiếp vì đối số sai.
- **Đầu vào đúng kiểu nhưng trái domain invariant:** `setCapacity(0)` tồn tại, đối số `int` hợp lệ về mặt kiểu, nhưng setter ném `IllegalArgumentException("capacity must be 1..10")`; Reflection bọc lỗi target trong `InvocationTargetException`.
- **Thiếu quyền truy cập:** `IllegalAccessException` hoặc `InaccessibleObjectException` liên quan đến accessibility/module boundary. Không sửa bằng cách bỏ validator.
- **Lỗi hệ thống:** `LinkageError`, `ExceptionInInitializerError` hoặc `Error` có thể không được xử lý như validation errors; đừng bắt `Throwable` rồi giả như chỉ một property không hợp lệ.

Nếu binder trả danh sách lỗi cho người dùng, nên ghi **tên property, kiểu mong đợi, giá trị được phép hoặc message domain an toàn**, đồng thời giữ cause cho log nội bộ khi thích hợp. Không công khai stack trace hoặc bí mật cấu hình ra phản hồi bên ngoài.

## 8. Lựa chọn thiết kế theo loại đối tượng

**DTO / JavaBean thuần:** Có thể phù hợp với no-args constructor và setters, nếu chính lớp đó chủ ý là cấu trúc nhận dữ liệu. Nếu áp dụng validation, cần chạy rõ bước validate sau binding và trước publish/execute.

**Domain entity:** Ưu tiên constructor/factory cho trạng thái bắt buộc và domain methods thay vì setter đại trà. Entity có thể bị ràng buộc bởi vòng đời, transaction, aggregate invariants; một generic Reflection binder ít khi biết hết.

**Immutable value object / record:** Constructor/canonical constructor và defensive copy bảo đảm mỗi object được tạo với trạng thái đúng. Khi cần đổi, tạo object mới; không “chọc” field `final`. Với record có component là `List`, cần chủ động `List.copyOf` trong constructor nếu muốn chống sửa collection.

**Third-party class không có setter:** Đọc API của thư viện, tìm constructor/builder/factory hoặc adapter chính thức. Không coi thiếu setter là lỗi cần khắc phục bằng deep field access.

**Serialization và ORM:** Một số framework có cơ chế khởi tạo đặc thù, proxy, bytecode enhancement hoặc field access theo mapping công bố. Đây là những lựa chọn có đánh đổi, **không suy ra thành quy tắc cho mọi công cụ Reflection**. Nếu framework chủ động sử dụng field access, trách nhiệm bảo đảm invariant ở điểm hợp lệ hóa/lifecycle được chỉ định phải được thiết kế, tài liệu hóa và kiểm thử.

**Plugin loader / configurable service:** Ưu tiên interface/factory contract hoặc `ServiceLoader` khi có thể; chỉ dùng Reflection tạo lớp từ tên runtime nếu kiểm soát nguồn class và xác minh contract. Gọi constructor công khai đáng tin cậy giúp tránh tạo instance ở trạng thái nửa vời.

## 9. Checklist cho người viết công cụ Reflection

Trước khi hỗ trợ một loại đối tượng mới, xác định:

1. **Đối tượng hợp lệ là gì?** Liệt kê invariant của từng field và quan hệ giữa các field.
2. **Ai sở hữu validation?** Constructor, factory, domain method, validator ngoài, hay một kết hợp được định nghĩa rõ?
3. **Bước nào cho phép publish object?** Chỉ công bố sau khi mọi bước tạo/cập nhật và validation cần thiết thành công.
4. **Tool dùng API nào?** Ưu tiên public constructor/method hợp lệ; không fallback sang private field writes khi bị từ chối.
5. **Có alias mutable không?** Kiểm tra defensive copies của collections, arrays, date/time mutable types và nested objects nếu cần.
6. **Có nhiều field phụ thuộc nhau không?** Dùng method atomic, constructor đủ tham số hoặc builder thay vì setter theo thứ tự ngẫu nhiên.
7. **Có quyền JPMS không?** Xác minh access trước khi gọi, thông báo rõ thiếu access; không dựa vào `setAccessible` mặc định.
8. **Ngoại lệ có giữ cause không?** Tách lỗi schema/Reflection và validation domain, không nuốt lỗi target.
9. **Cần tránh áp đặt JavaBeans không?** Kiểu domain không nhất thiết phải lộ setter cho mọi property.
10. **Có kiểm thử trạng thái xấu không?** Xác nhận dữ liệu sai bị từ chối và object đã công bố không chuyển sang trạng thái bất hợp lệ.

### Bài tập áp dụng

1. Trong ví dụ `Registration`, thử `constructor.newInstance("JAVA", 0)`. Phân biệt exception ngoài cùng với cause và giải thích tại sao constructor gọi `setCapacity`.
2. Đổi `setCapacity.invoke(r, 1)` thành `Field.setInt(r, 1)` trong một test tách biệt. Bất biến nào bị vi phạm và vì sao?
3. Thay `getAttendees()` bằng cách trả trực tiếp một `ArrayList` mutable bên trong. Hãy mô tả cách caller có thể phá invariant mà không sử dụng Reflection.
4. Thiết kế lệnh `replaceAttendees(List<String>)` bảo đảm cả danh sách và số chỗ vẫn hợp lệ, chỉ commit khi kiểm tra xong.
5. Từ một cặp ngày `start/end`, viết một phương thức `reschedule(start,end)` kiểm tra cả hai trước khi cập nhật. Giải thích điểm yếu của hai setter độc lập.
6. Khi chạy trong named module, hãy mô tả vì sao `getDeclaredField` có thể thành công nhưng `trySetAccessible()` lại trả `false`.
7. Đối với một record chứa `List<String>`, viết canonical constructor kiểm tra dữ liệu và dùng `List.copyOf`. Thử sửa collection từ caller.

**Điểm cần nhớ:** Reflection giúp tìm và gọi code tại runtime; nó **không thay thế** contract của lớp. Với object có bất biến, constructor/factory và các phương thức cập nhật có kiểm tra là ranh giới chính. Tránh biến `Field.set` thành lối tắt khi model từ chối một thay đổi, và thiết kế thời điểm publish, tính nguyên tử, defensive copy, access policy cùng chẩn đoán exception một cách có chủ đích.

### Tài liệu tham khảo

- [Javadoc Java 17 — `Constructor.newInstance`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Constructor.html#newInstance(java.lang.Object...))
- [Javadoc Java 17 — `Method.invoke`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html#invoke(java.lang.Object,java.lang.Object...))
- [Javadoc Java 17 — `Field` và giới hạn ghi final field](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html)
- [Javadoc Java 17 — `AccessibleObject` và giới hạn JPMS](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html)
- [Javadoc Java 17 — `List.copyOf`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/List.html#copyOf(java.util.Collection))


</details>

- [Quay lại đầu trang](#back-to-top)
