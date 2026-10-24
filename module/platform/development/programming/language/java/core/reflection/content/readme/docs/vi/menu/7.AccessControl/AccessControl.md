<a id="back-to-top"></a>

# Quyền truy cập

## Menu
- [Tra cứu thấy thành viên không đồng nghĩa được quyền sử dụng](#access-control-001)
- [Kiểm tra truy cập Java khi get, set, invoke hoặc newInstance](#access-control-002)
- [AccessibleObject và trạng thái bỏ qua kiểm tra truy cập](#access-control-003)
- [canAccess và đối tượng đích phù hợp](#access-control-004)
- [setAccessible khác trySetAccessible](#access-control-005)
- [Ý nghĩa thực sự của isAccessible và lý do API này lỗi thời](#access-control-006)
- [Package, lớp khai báo và thành viên public trong kiểu không public](#access-control-007)
- [Nestmate và quyền truy cập private giữa các lớp liên quan](#access-control-008)
- [Không xem setAccessible là bảo đảm sửa được mọi field](#access-control-009)

## <a id="access-control-001">Tra cứu thấy thành viên không đồng nghĩa được quyền sử dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Tra cứu thấy thành viên không đồng nghĩa được quyền sử dụng

## 1. Ý tưởng cốt lõi: tìm thấy metadata khác với được truy cập

Trong Java Reflection, một chương trình có thể **tìm thấy mô tả của thành viên** (`Field`, `Method`, `Constructor`) mà vẫn **không có quyền đọc trường, ghi trường, gọi phương thức hoặc tạo đối tượng qua constructor**. Đây là điểm dễ nhầm khi bắt đầu dùng `getDeclaredField()`, `getDeclaredMethod()` và `getDeclaredConstructor()`.

Ví dụ tưởng tượng:

```java
Field secret = Account.class.getDeclaredField("passwordHash");
```

Nếu lớp `Account` có trường `private` tên `passwordHash`, lệnh trên vẫn có thể trả một đối tượng `Field`. Điều đó chỉ chứng minh rằng trường được **khai báo** và được Reflection **phát hiện**. Lệnh tiếp theo như `secret.get(account)` vẫn có thể ném `IllegalAccessException` vì mã gọi không có quyền truy cập.

Nên phân biệt ít nhất ba bước:

1. **Tra cứu thành viên:** Xác định lớp, tên, chữ ký và nhận metadata.
2. **Kiểm tra quyền:** Quyết định mã gọi có thể sử dụng thành viên đó hay có được phép yêu cầu bỏ qua kiểm tra quyền Java thông thường hay không.
3. **Thao tác thực sự:** `Field.get/set`, `Method.invoke` hoặc `Constructor.newInstance`, với đối tượng đích và đối số hợp lệ.

```text
Class<?> và tên/chữ ký thành viên
              |
              v
     getDeclaredXxx / getXxx
              |
      Có tìm thấy không?
         /           \
      Không           Có
       |               |
NoSuch...Exception     v
              Đã có Field/Method/Constructor
                         |
                         v
              Kiểm tra quyền truy cập
                   /            \
             Không đủ          Đủ quyền
                 |                |
        Không thể sử dụng     get/set/invoke/
        trực tiếp             newInstance
```

**Khả năng tra cứu và khả năng truy cập là hai câu hỏi độc lập.** Có metadata không làm biến mất `private`, giới hạn package, quan hệ kế thừa hoặc quy tắc Java Platform Module System (JPMS).

## 2. Tìm thành viên bằng `getXxx` và `getDeclaredXxx`

`Class<?>` cung cấp hai họ API quan trọng:

| Thành viên | Tra cứu dạng public | Tra cứu do chính kiểu khai báo |
|---|---|---|
| Trường | `getField(name)`, `getFields()` | `getDeclaredField(name)`, `getDeclaredFields()` |
| Phương thức | `getMethod(name, parameterTypes...)`, `getMethods()` | `getDeclaredMethod(name, parameterTypes...)`, `getDeclaredMethods()` |
| Constructor | `getConstructor(parameterTypes...)`, `getConstructors()` | `getDeclaredConstructor(parameterTypes...)`, `getDeclaredConstructors()` |

Cách hiểu:

- `getField` và `getMethod` tìm các thành viên **public** theo quy tắc của API, có thể bao gồm thành viên thừa kế.
- `getDeclaredField` và `getDeclaredMethod` xem các thành viên **khai báo trong chính lớp/interface đang xét**, kể cả `private`, `protected` và package-private. Các API declared không tự cộng thêm thành viên kế thừa.
- Constructor không được kế thừa; sự khác biệt giữa `getConstructor` và `getDeclaredConstructor` chủ yếu là phạm vi `public` hay mọi mức truy cập.
- `getDeclaredXxx` không có nghĩa “cho phép dùng private member”. Từ *declared* mô tả **phạm vi tìm kiếm**, không phải quyền.
- `getXxx` tìm được một thành viên `public` cũng chưa bảo đảm thao tác phản chiếu thành công: lớp khai báo có thể không truy cập được từ mã gọi, package có thể không được module xuất, hoặc đối tượng đích/đối số không hợp lệ.

Ví dụ minh họa (giả sử `Profile` có trường `private String token`):

```java
Field f = Profile.class.getDeclaredField("token");
System.out.println(f.getName()); // token

// Dòng dưới đây KHÔNG được bảo đảm thành công:
Object value = f.get(profile);
```

Đoạn này chỉ minh họa ý nghĩa API; `Profile` và `profile` là thành phần giả định, chưa phải chương trình độc lập. Chương trình đầy đủ có thể chạy được trình bày ở mục 3.

Lỗi `NoSuchFieldException` hoặc `NoSuchMethodException` có nghĩa **không tìm thấy thành viên theo tên/chữ ký/phạm vi tra cứu đã chọn**. Nó khác `IllegalAccessException`, khi metadata đã tồn tại nhưng thao tác bị từ chối. Ví dụ `getDeclaredMethod("pay", int.class)` không tìm `pay(Integer)`, dù Java có cơ chế boxing trong lời gọi mã nguồn thông thường.

## 3. Ví dụ chạy được trên Java 17+: chứng minh “tìm thấy ≠ được dùng”

Chương trình dưới đây định nghĩa **hai lớp top-level riêng biệt trong cùng một file**. `Vault` chứa private field và private method; `AccessLookupDemo` là caller khác lớp và khác nest, vì vậy **không có quyền truy cập private theo ngôn ngữ Java**. Tuy nhiên cả hai nằm cùng unnamed module, nên `trySetAccessible` có thể bật suppression và minh họa sự khác nhau.

Lưu nguyên văn vào file **`AccessLookupDemo.java`**:

```java
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class AccessLookupDemo {
    public static void main(String[] args)
            throws ReflectiveOperationException {

        Vault vault = new Vault("abc123");

        Field field = Vault.class.getDeclaredField("secret");
        System.out.println("field found: " + field.getName());
        System.out.println("field public: "
                + Modifier.isPublic(field.getModifiers()));
        System.out.println("field canAccess before: "
                + field.canAccess(vault));

        try {
            field.get(vault);
            System.out.println("field read unexpectedly succeeded");
        } catch (IllegalAccessException ex) {
            System.out.println("field get: "
                    + ex.getClass().getSimpleName());
        }

        boolean fieldOpened = field.trySetAccessible();
        System.out.println("field trySetAccessible: " + fieldOpened);
        System.out.println("field canAccess after: "
                + field.canAccess(vault));

        if (fieldOpened) {
            System.out.println("field value: " + field.get(vault));
        }

        Method method = Vault.class.getDeclaredMethod("formatSecret");
        System.out.println("method found: " + method.getName());
        System.out.println("method canAccess before: "
                + method.canAccess(vault));

        try {
            method.invoke(vault);
            System.out.println("method invoke unexpectedly succeeded");
        } catch (IllegalAccessException ex) {
            System.out.println("method invoke: "
                    + ex.getClass().getSimpleName());
        }

        boolean methodOpened = method.trySetAccessible();
        System.out.println("method trySetAccessible: " + methodOpened);

        if (methodOpened) {
            System.out.println("method result: "
                    + method.invoke(vault));
        }
    }
}

class Vault {
    private final String secret;

    Vault(String secret) {
        this.secret = secret;
    }

    private String formatSecret() {
        return "[" + secret + "]";
    }
}
```

Biên dịch/chạy bằng JDK 17+:

```bash
javac AccessLookupDemo.java
java AccessLookupDemo
```

Kết quả kỳ vọng khi chạy thông thường, không có agent/cấu hình thay đổi quyền Reflection:

```text
field found: secret
field public: false
field canAccess before: false
field get: IllegalAccessException
field trySetAccessible: true
field canAccess after: true
field value: abc123
method found: formatSecret
method canAccess before: false
method invoke: IllegalAccessException
method trySetAccessible: true
method result: [abc123]
```

Giải thích từng mốc:

1. `getDeclaredField("secret")` và `getDeclaredMethod("formatSecret")` **tìm thấy** private member. Không có ngoại lệ lookup.
2. `Modifier.isPublic(...)` trả `false`; `canAccess(vault)` ban đầu cũng trả `false` vì caller không có quyền private.
3. `field.get(vault)` và `method.invoke(vault)` ban đầu ném `IllegalAccessException` **trước khi truy cập nội dung/thi hành phương thức**. Việc bắt ngoại lệ giúp chương trình tiếp tục.
4. `trySetAccessible()` trả `true` trong ví dụ này vì hai top-level classes cùng unnamed module và không bị một ràng buộc access khác từ môi trường ngăn cản.
5. Sau khi bật suppression trên từng metadata object, `field.get(vault)` và `method.invoke(vault)` thành công.
6. `Vault` là lớp top-level thứ hai không có modifier `public`. Java cho phép khai báo nhiều lớp top-level trong cùng một `.java` nhưng chỉ một lớp `public` phù hợp tên file.

Ví dụ cho thấy rõ **không phải metadata khiến private member sử dụng được**, mà là caller sau đó đã được runtime cho phép bật suppression trên đối tượng Reflection cụ thể.

## 4. Chẩn đoán một lỗi truy cập theo đúng giai đoạn

| Giai đoạn | Triệu chứng | Cần kiểm tra |
| --- | --- | --- |
| Tra cứu | `NoSuchFieldException`, `NoSuchMethodException` | Đúng tên, chữ ký, lớp khai báo, phạm vi `getXxx` hay `getDeclaredXxx` chưa? |
| Kiểm tra quyền | `canAccess(receiver) == false` | Caller có quyền theo Java không? Lớp khai báo và package/module có truy cập được không? |
| Mở quyền | `trySetAccessible() == false` hoặc `InaccessibleObjectException` | Named module có `opens` package cho caller không? Có thực sự cần deep reflection không? |
| Thực thi | `IllegalAccessException` | Metadata đã tìm được, nhưng caller chưa có quyền sử dụng member |
| Chạy mã đích | `InvocationTargetException` | Method/constructor đã chạy và ném exception; đọc `getCause()` |
| Kiểm tra đối số | `IllegalArgumentException` | Receiver, kiểu/số đối số hoặc giá trị gán có hợp lệ không? |

Một member `public` vẫn có thể bị chặn nếu *lớp khai báo* không truy cập được. Một member `private` có thể được gọi bình thường từ chính lớp hoặc nestmate hợp lệ; kết quả kiểm tra phụ thuộc caller. `trySetAccessible()` chỉ thử bỏ qua kiểm tra truy cập và vẫn chịu ranh giới JPMS; thành công không bảo đảm có thể ghi mọi trường `final`.

## 5. Tự kiểm tra

1. Vì sao `getDeclaredField("secret")` tìm được member dù `Field.get` có thể ném `IllegalAccessException`?
2. Vì sao `getMethod` tìm được một method `public` vẫn chưa bảo đảm `Method.invoke` được phép?
3. Hai lớp top-level ở cùng tệp nguồn có trở thành nestmate không? Vì sao ví dụ dùng hai lớp riêng?
4. Vì sao `exports` và `opens` ảnh hưởng khác nhau đến public API và deep reflection?
5. Khi `Method.invoke` trả `InvocationTargetException`, bạn cần điều tra quyền Reflection hay lỗi từ code đã chạy?

**Bài học cần giữ:** lookup xác định metadata **tồn tại**; quyền sử dụng được quyết định riêng tại điểm thao tác. Chọn đúng họ `getXxx`/`getDeclaredXxx` trước, rồi mới đánh giá caller, receiver và module.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="access-control-002">Kiểm tra truy cập Java khi get, set, invoke hoặc newInstance</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm tra truy cập Java khi `get`, `set`, `invoke` hoặc `newInstance`

## 1. Tìm thấy member khác với có quyền sử dụng member

Reflection có hai công đoạn riêng:

1. **Tra cứu metadata**: `Class.getField`, `getMethod`, `getConstructor` lấy các public member thích hợp; `getDeclaredField`, `getDeclaredMethod`, `getDeclaredConstructor` lấy member khai báo trực tiếp tại kiểu đang xét, bất kể modifier.
2. **Thực hiện thao tác**: `Field.get`/`set`, `Method.invoke` và `Constructor.newInstance` phải thỏa điều kiện truy cập của caller theo Java và, nếu có, hệ thống module JPMS.

Vì vậy `SomeType.class.getDeclaredField("secret")` có thể trả về đối tượng `Field` cho một field private, nhưng `field.get(target)` hoặc `field.set(target, value)` vẫn có thể ném `IllegalAccessException`. Tương tự, lấy được metadata của method/constructor private không tự mở quyền thực thi chúng.

Cần phân biệt thêm:

- **Member được khai báo ở đâu**: `getDeclaringClass()` cho biết kiểu khai báo thực sự. Member public được thừa kế có thể xuất hiện qua `getMethods()` dù không hiện diện trong `getDeclaredMethods()` của lớp con.
- **Member có modifier gì**: `getModifiers()` kết hợp `Modifier.isPublic`, `isPrivate`… mô tả modifier nhưng không trả lời đầy đủ caller hiện tại có được dùng hay không.
- **Caller có thể truy cập lúc này không**: `canAccess(receiver)` kiểm tra quyền hiện tại trên reflection object (bao gồm trạng thái override); thao tác thực tế vẫn có thể có lỗi bổ sung.

## 2. Các thao tác áp dụng kiểm tra truy cập

| Reflection object | Thao tác | Điều kiện receiver | Nếu bị chặn truy cập |
| --- | --- | --- | --- |
| `Field` | `get`, `set`, cùng biến thể `getInt`, `setInt`, … | Instance thích hợp với field thường; `null` với field static | `IllegalAccessException` |
| `Method` | `invoke` | Instance thích hợp với method thường; `null` với method static | `IllegalAccessException` |
| `Constructor<T>` | `newInstance` | Không nhận receiver; constructor tạo object mới | `IllegalAccessException` |

Khi access-check override chưa được bật, Java sử dụng các quy tắc quyền truy cập ngôn ngữ (bao gồm khả năng truy cập **lớp khai báo** và bản thân member): `public`, `protected`, package-private, `private`, ngữ cảnh package, superclass/subclass và vị trí của caller. Với named module, còn phải xét package được `exports`/`opens` cho ai.

**Lưu ý:** API tra cứu metadata có thể ném `NoSuchFieldException` hoặc `NoSuchMethodException` nếu tên/signature không khớp; đó khác lỗi `IllegalAccessException` xảy ra khi dùng member đã tìm thấy. Các thao tác `get`/`set`/`invoke`/`newInstance` còn kiểm tra kiểu receiver, kiểu đối số, ràng buộc gán và các điều kiện chạy khác. Không gom tất cả lỗi vào “không có quyền”.

## 3. Ví dụ Java 17+ đầy đủ, kết quả xác định

Tạo một file `AccessCheckDemo.java`:

~~~java
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class AccessCheckDemo {
    public static void main(String[] args) throws Exception {
        AccessTarget target = new AccessTarget();
        Class<AccessTarget> type = AccessTarget.class;

        Field publicField = type.getField("label");
        Field privateField = type.getDeclaredField("secret");
        Method privateMethod = type.getDeclaredMethod("reveal");
        Constructor<AccessTarget> privateConstructor =
                type.getDeclaredConstructor(String.class);

        System.out.println("public canAccess: " + publicField.canAccess(target));
        System.out.println("public get: " + publicField.get(target));

        System.out.println("private field canAccess: "
                + privateField.canAccess(target));
        try {
            privateField.get(target);
        } catch (IllegalAccessException e) {
            System.out.println("private get blocked: "
                    + e.getClass().getSimpleName());
        }

        try {
            privateField.set(target, "changed");
        } catch (IllegalAccessException e) {
            System.out.println("private set blocked: "
                    + e.getClass().getSimpleName());
        }

        try {
            privateMethod.invoke(target);
        } catch (IllegalAccessException e) {
            System.out.println("private invoke blocked: "
                    + e.getClass().getSimpleName());
        }

        try {
            privateConstructor.newInstance("from private ctor");
        } catch (IllegalAccessException e) {
            System.out.println("private ctor blocked: "
                    + e.getClass().getSimpleName());
        }

        System.out.println("enable field: " + privateField.trySetAccessible());
        System.out.println("enable method: " + privateMethod.trySetAccessible());
        System.out.println("enable ctor: " + privateConstructor.trySetAccessible());

        privateField.set(target, "changed");
        System.out.println("private get after enable: "
                + privateField.get(target));
        System.out.println("private invoke after enable: "
                + privateMethod.invoke(target));
        System.out.println("ctor after enable: "
                + privateConstructor.newInstance("from private ctor").label);
    }
}

class AccessTarget {
    public String label = "visible";
    private String secret = "before";

    public AccessTarget() {
    }

    private AccessTarget(String value) {
        label = value;
        secret = "new-secret";
    }

    private String reveal() {
        return secret;
    }
}
~~~

Hai lớp là hai **top-level class** trong cùng package mặc định. Cách sắp xếp này có chủ đích: nếu đặt `AccessTarget` là nested class bên trong `AccessCheckDemo` thì quan hệ *nestmate* có thể làm phép truy cập private thành hợp lệ và không còn minh họa đúng tình huống bị chặn quyền.

Biên dịch và chạy:

~~~bash
javac --release 17 AccessCheckDemo.java
java AccessCheckDemo
~~~

Output trên classpath/unnamed module thông thường:

~~~text
public canAccess: true
public get: visible
private field canAccess: false
private get blocked: IllegalAccessException
private set blocked: IllegalAccessException
private invoke blocked: IllegalAccessException
private ctor blocked: IllegalAccessException
enable field: true
enable method: true
enable ctor: true
private get after enable: changed
private invoke after enable: changed
ctor after enable: from private ctor
~~~

### Đọc kết quả

- `label` là field `public`; code gọi nằm trong cùng package với top-level class `AccessTarget`. `canAccess(target)` trả `true` và `get(target)` đọc được giá trị.
- `secret` là field `private` của một top-level class khác. Dù `getDeclaredField` tìm được metadata, cả `get` và `set` đều ném `IllegalAccessException` trước khi thao tác.
- Method `reveal` và constructor nhận `String` cũng private. `getDeclaredMethod` và `getDeclaredConstructor` tìm được member, nhưng `invoke` và `newInstance` ban đầu bị chặn.
- Trong môi trường classpath của ví dụ, `trySetAccessible` bật override thành công cho từng reflection object. Sau đó `privateField.set`, `privateField.get`, `privateMethod.invoke` và `privateConstructor.newInstance` chạy được.
- Constructor private gán tham số cho `label`, nên đối tượng mới in ra `from private ctor`. Đối tượng `target` trước đó vẫn được dùng để đọc method `reveal()` sau khi field `secret` được đặt thành `changed`.

Lệnh `javac --release 17` trong ví dụ dùng cú pháp và API có từ Java 17. Kết quả bật override có thể khác ở ứng dụng named module vì ranh giới module.

## 4. Ngoại lệ: chặn truy cập hay lỗi thực thi?

| Exception / kết quả | Khi nào có thể xảy ra | Ý nghĩa thực tế |
| --- | --- | --- |
| `IllegalAccessException` | `get`, `set`, `invoke`, `Constructor.newInstance` khi caller không có quyền | Chưa được phép truy cập member; xem access modifier, declaring class, package và JPMS |
| `InaccessibleObjectException` | `setAccessible(true)` khi không thể bật override | Deep reflection bị ranh giới module ngăn |
| `trySetAccessible() == false` | Override không được cấp | Phải xử lý nhánh thất bại trước khi thao tác private member |
| `NoSuchFieldException` / `NoSuchMethodException` | Tra cứu sai tên, chữ ký hoặc phạm vi `getX`/`getDeclaredX` | Member không được tìm thấy; chưa phải kết quả access check tại thao tác |
| `IllegalArgumentException` | Receiver sai, kiểu/số đối số sai, giá trị gán không tương thích | Sửa receiver, parameter types và giá trị |
| `InvocationTargetException` | `Method.invoke` hoặc `Constructor.newInstance` gọi code đích và code đích ném exception | Lấy `getCause()` để phân biệt lỗi logic của method/constructor |
| `InstantiationException` | Constructor reflection yêu cầu tạo đối tượng của class không thể instantiate (ví dụ abstract class) | Loại class không thể tạo instance theo contract |
| Vấn đề `final field` | Field final có các ràng buộc ghi riêng, kể cả khi override được bật | Không dựa vào reflective write để thay đổi trạng thái bất biến |

`InvocationTargetException` không phải dấu hiệu thiếu quyền truy cập: thường method hoặc constructor **đã được gọi** và exception xuất phát từ thân code đích. Nếu gọi `Method.invoke` trên instance method, dispatch động vẫn có thể xảy ra theo quy tắc Java; dùng `getDeclaringClass()` không có nghĩa ép gọi implementation của lớp cha.

Với final field, cần phân biệt quyền đọc với quyền ghi. Một số final field như `static final`, field final của record và field final trong hidden class là **non-modifiable final fields**; `setAccessible(true)` có thể cho phép đọc nhưng không làm field thành writable. Ngay cả khi có cơ chế ghi các final field khác, việc phụ thuộc vào đó gây sai lệch bất biến/optimization và không nên dùng cho business logic.

## 5. Bài tự kiểm tra

**Câu 1.** Vì sao `getDeclaredField("secret")` trả object `Field` nhưng `get` bị `IllegalAccessException`? Vì `getDeclaredField` chỉ tra cứu metadata của field khai báo trực tiếp; `get` là thao tác sử dụng member và cần quyền truy cập.

**Câu 2.** `trySetAccessible()` trả `true` trong ví dụ nhưng `false` tại ứng dụng khác, có mâu thuẫn không? Không. Điều kiện package/module và caller khác nhau; unnamed module trên classpath và named module đóng gói có ranh giới khác nhau.

**Câu 3.** `Method.invoke` ném `InvocationTargetException`, có phải thiếu quyền không? Không nên kết luận như vậy. Kiểm tra `getCause()` để hiểu lỗi do method được gọi ném ra; thiếu quyền truy cập phản chiếu được biểu diễn bằng `IllegalAccessException`.

**Câu 4.** Vì sao bật `setAccessible(true)` chưa chắc cho phép sửa `static final`? Vì override chỉ tác động lên access check; `final` còn có ràng buộc riêng về quyền ghi và một số final field hoàn toàn không thể sửa qua Reflection.

Khi làm việc với Reflection, hãy xét độc lập **tìm member → kiểm tra quyền thực tế → thực hiện thao tác → xử lý lỗi của thao tác và code đích**; điều này tránh nhầm lỗi tra cứu, lỗi quyền truy cập, lỗi đối số và lỗi nghiệp vụ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="access-control-003">AccessibleObject và trạng thái bỏ qua kiểm tra truy cập</a>

<details>
<summary>Xem chi tiết</summary>

# AccessibleObject và trạng thái bỏ qua kiểm tra truy cập trong Java Reflection

Reflection cho phép tìm `Field`, `Method` và `Constructor` ngay cả khi thành viên không phải `public`. Tuy nhiên, **tìm thấy metadata không đồng nghĩa được phép đọc trường, gọi phương thức hay tạo đối tượng**. `java.lang.reflect.AccessibleObject` là lớp cha của ba loại đối tượng Reflection trên, cung cấp API kiểm tra quyền truy cập và, khi được phép, bật trạng thái **bỏ qua kiểm tra truy cập của ngôn ngữ Java** (suppress access checks).

Chủ đề này thuộc nhóm **Quyền truy cập**. Mục tiêu là hiểu chính xác accessible flag, biết khi nào dùng `canAccess`, `setAccessible`, `trySetAccessible`, và vì sao ranh giới module của Java 9+ có thể chặn việc bật flag. Các ví dụ dùng Java 17+.

## 1. Vai trò và các API chính

```text
java.lang.reflect.AccessibleObject
  ├── java.lang.reflect.Field
  └── java.lang.reflect.Executable
        ├── java.lang.reflect.Method
        └── java.lang.reflect.Constructor
```

| API | Ý nghĩa |
| --- | --- |
| `boolean canAccess(Object obj)` | Cho biết caller hiện tại có thể truy cập thành viên được biểu diễn hay không, xét receiver và trạng thái hiện có |
| `void setAccessible(boolean flag)` | Đặt flag yêu cầu bỏ qua kiểm tra truy cập; bật `true` chỉ thành công nếu quy tắc Java/module cho phép |
| `boolean trySetAccessible()` | Thử bật flag; trả `true` nếu thành công, `false` nếu không thể bật vì quyền truy cập |
| `boolean isAccessible()` | API cũ, deprecated từ Java 9; **chỉ** phản ánh flag bỏ qua kiểm tra, không trả lời đầy đủ câu hỏi caller có thể truy cập không |
| `static void setAccessible(AccessibleObject[] array, boolean flag)` | Đặt flag cho nhiều đối tượng Reflection, vẫn chịu kiểm tra quyền/module |

Các phương thức `Class.getDeclaredField`, `getDeclaredMethod` và `getDeclaredConstructor` có thể trả về member `private`, `protected` hoặc package-private của chính lớp đó, nhưng **không tự bật flag**. Các API `getField`, `getMethod`, `getConstructor` tìm các thành viên `public` thuộc phạm vi tương ứng; ngay cả với member `public`, module hoặc lớp khai báo vẫn ảnh hưởng khả năng truy cập từ caller.

## 2. Phân biệt ba điều thường bị nhập nhằng

**Visibility của thành viên** là `public`, `protected`, package-private hoặc `private` trong mã nguồn và bytecode. Reflection không đổi visibility này.

**Trạng thái accessible flag** là lựa chọn trên đối tượng `Field`, `Method` hoặc `Constructor` cụ thể nhằm bỏ qua kiểm tra truy cập khi dùng chính đối tượng Reflection ấy. Mặc định flag là `false`. `setAccessible(true)` hoặc `trySetAccessible()` thành công sẽ bật flag; `setAccessible(false)` tắt flag.

**Quyền truy cập thực tế** được kiểm tra theo caller, loại thành viên, receiver, flag và ranh giới module. Đây là ý nghĩa của `canAccess(obj)`. Một phương thức `public` có thể có flag `false` mà `canAccess(receiver) == true`, vì caller đã có quyền theo quy tắc thông thường. Ngược lại, tìm được member `private` bằng `getDeclaredMethod` không có nghĩa `invoke` sẽ được cho phép.

Do vậy:

- `isAccessible() == false` **không** có nghĩa “thành viên không thể truy cập”.
- `canAccess(obj)` không bật quyền truy cập; nó chỉ kiểm tra.
- `setAccessible(true)` không đổi member thành `public` và không vượt qua mọi giới hạn Java Platform Module System (JPMS).
- `setAccessible(false)` không cấm caller sử dụng member mà caller vốn đã có quyền truy cập.

## 3. Ví dụ Java 17+ có thể biên dịch và chạy

Tạo đúng một tệp `AccessibleObjectDemo.java` như sau. `Vault` là lớp **top-level riêng**, nằm trong cùng tệp/package với lớp demo nhưng không phải nested class. Cách tổ chức này tránh hiệu ứng **nestmates**: các nested class cùng nest có thể truy cập thành viên `private` của nhau theo quy tắc Java hiện đại, làm sai kỳ vọng khi minh họa thiếu quyền.

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class AccessibleObjectDemo {
    public static void main(String[] args) throws Exception {
        Constructor<Vault> ctor = Vault.class.getDeclaredConstructor();
        System.out.println("constructor.before=" + ctor.canAccess(null));
        System.out.println("constructor.try=" + ctor.trySetAccessible());

        Vault vault = ctor.newInstance();
        System.out.println("constructor.after=" + ctor.canAccess(null));

        Field secret = Vault.class.getDeclaredField("secret");
        System.out.println("field.before=" + secret.canAccess(vault));

        try {
            secret.get(vault);
        } catch (IllegalAccessException e) {
            System.out.println("field.get.before=IllegalAccessException");
        }

        secret.setAccessible(true);
        System.out.println("field.after=" + secret.canAccess(vault));
        System.out.println("field.value=" + secret.get(vault));

        secret.setAccessible(false);
        System.out.println("field.reset=" + secret.canAccess(vault));

        Method reveal = Vault.class.getDeclaredMethod(
                "reveal", String.class);
        System.out.println("method.before=" + reveal.canAccess(vault));
        System.out.println("method.try=" + reveal.trySetAccessible());
        System.out.println("method.result="
                + reveal.invoke(vault, "key="));

        Method status = Vault.class.getDeclaredMethod("status");
        System.out.println("public.canAccess=" + status.canAccess(vault));
    }
}

class Vault {
    private final String secret;

    private Vault() {
        this.secret = "s3";
    }

    private String reveal(String prefix) {
        return prefix + secret;
    }

    public String status() {
        return "READY";
    }
}
```

Dùng terminal tại thư mục chứa tệp:

```bash
javac AccessibleObjectDemo.java
java AccessibleObjectDemo
```

Kết quả chuẩn trong ví dụ chạy trên classpath (unnamed module):

```text
constructor.before=false
constructor.try=true
constructor.after=true
field.before=false
field.get.before=IllegalAccessException
field.after=true
field.value=s3
field.reset=false
method.before=false
method.try=true
method.result=key=s3
public.canAccess=true
```

**Giải thích:**

1. `Vault()` là constructor `private`. `getDeclaredConstructor()` vẫn tìm được metadata nhưng `canAccess(null)` ban đầu bằng `false` đối với caller `AccessibleObjectDemo`. Sau khi `trySetAccessible()` trả `true`, `newInstance()` tạo được đối tượng.
2. `secret` là trường `private final`. `getDeclaredField` tìm thấy nó nhưng `Field.get` bị chặn trước khi bật override, nên ném `IllegalAccessException`.
3. `setAccessible(true)` bật flag trên đối tượng `Field secret`, cho phép **đọc** giá trị `s3`. Ví dụ không thử sửa một trường `final`.
4. `setAccessible(false)` tắt override; vì caller khác class khai báo, `canAccess(vault)` trở về `false`.
5. Phương thức `reveal(String)` là `private`, cần bật flag trước khi gọi `invoke`. Phương thức `status()` là `public`, và hai lớp nằm cùng package, nên có thể truy cập bình thường mà không bật flag.

Lưu ý: hành vi chính xác phụ thuộc quan hệ class, package, module và caller. Không lấy kết quả của ví dụ chạy trên classpath để kết luận mọi member `private` trong module khác đều mở được.

## 4. setAccessible, trySetAccessible và việc hoàn nguyên flag

### setAccessible(true)

`member.setAccessible(true)` yêu cầu bỏ qua kiểm tra truy cập Java khi thao tác qua **member đó**. Nếu được phép, lần thao tác sau như `Field.get`, `Method.invoke` hay `Constructor.newInstance` có thể sử dụng override; nếu bị JPMS chặn, thường nhận `java.lang.reflect.InaccessibleObjectException`, một **unchecked exception**.

```java
// Đoạn minh họa, giả định method và receiver đã được khai báo,
// method không nhận tham số, và code nằm trong ngữ cảnh xử lý exception.
try {
    method.setAccessible(true);
    Object result = method.invoke(receiver);
} catch (java.lang.reflect.InaccessibleObjectException e) {
    System.out.println("Package/module không mở cho deep reflection");
} catch (ReflectiveOperationException e) {
    System.out.println("Thao tác Reflection thất bại: " + e);
}
```

Đoạn trên không phải chương trình chạy độc lập. `invoke` còn có thể ném `IllegalArgumentException` khi receiver/tham số không hợp lệ; `InvocationTargetException` bọc exception do phương thức đích ném ra. Không nên xem mọi lỗi tại `invoke` là lỗi permission.

### trySetAccessible()

`trySetAccessible()` phù hợp khi ứng dụng muốn fallback có kiểm soát:

```java
// Minh họa trong method có xử lý các ngoại lệ của invoke.
if (method.trySetAccessible()) {
    Object result = method.invoke(receiver);
    System.out.println(result);
} else {
    System.out.println("Không thể bật quyền truy cập Reflection");
}
```

Kết quả `true` nghĩa là bật flag thành công hoặc flag đã bật. Kết quả `false` nghĩa là không thể bật theo điều kiện truy cập hiện tại, chẳng hạn package của module khác không mở cho caller. `trySetAccessible()` **vẫn có thể ném `SecurityException`** trong môi trường có kiểm soát bảo mật tương ứng; không nên diễn giải nó thành API “không bao giờ ném lỗi”.

### setAccessible(false)

Gọi `member.setAccessible(false)` tắt flag override trên đối tượng Reflection đó. Khi tiếp tục thao tác, các quy tắc kiểm tra truy cập bình thường lại áp dụng. Member `public` vốn truy cập được thì vẫn có thể truy cập được. Không nên kỳ vọng thao tác này đổi visibility hay thu hồi quyền của mã khác.

### isAccessible() đã deprecated

`isAccessible()` chỉ trả về trạng thái **suppress access checks flag**, vì thế tên phương thức dễ gây hiểu lầm. Nó đã deprecated kể từ Java 9. Dùng `canAccess(receiver)` để hỏi caller hiện tại có thể truy cập hay không; dùng `trySetAccessible()` để hỏi và thử bật flag khi cần. Một member `public` với `isAccessible() == false` vẫn có thể có `canAccess(receiver) == true`.

## 5. Ranh giới của accessible flag trong ứng dụng

Cờ bỏ qua kiểm tra truy cập chỉ áp dụng cho đối tượng Reflection cụ thể; nó không đổi modifier của lớp hoặc mở package trên phạm vi toàn JVM. Nếu caller thuộc một named module khác và package không mở đúng phạm vi, `trySetAccessible()` có thể trả `false` còn `setAccessible(true)` có thể ném `InaccessibleObjectException`. `exports` cho public API khác `opens` cho deep reflection.

Việc đã bật cờ cũng không giải quyết lỗi receiver, kiểu đối số, logic method hoặc giới hạn ghi của field `final`. Với nhu cầu thông thường, ưu tiên public API. Khi cần deep reflection, xác định package/module sở hữu và xử lý nhánh bị từ chối có chủ đích.

**Tự kiểm tra:** Vì sao một method `public` có thể `canAccess(receiver) == true` khi accessible flag vẫn `false`? Vì sao `setAccessible(false)` không làm method public trở thành private? Vì sao đọc được private final field chưa chứng minh rằng bạn có thể sửa nó?

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="access-control-004">canAccess và đối tượng đích phù hợp</a>

<details>
<summary>Xem chi tiết</summary>

# `canAccess` và đối tượng đích phù hợp trong Java Reflection

## 1. Vấn đề cần giải quyết

Khi dùng Reflection, lấy được một `Field`, `Method` hoặc `Constructor` **không có nghĩa là mã đang chạy được phép truy cập thành viên đó**. Bước tra cứu (`getDeclaredField`, `getDeclaredMethod`, `getDeclaredConstructor`) và bước kiểm tra quyền truy cập là hai việc khác nhau.

Từ Java 9, `AccessibleObject.canAccess(Object obj)` giúp kiểm tra **mã gọi hiện tại** có thể truy cập thành viên được phản chiếu hay không, trong ngữ cảnh **đối tượng đích phù hợp**. Ba nhóm đối tượng Reflection hỗ trợ phương thức này là:

- `Field`: trường dữ liệu, thuộc instance hoặc static;
- `Method`: phương thức, thuộc instance hoặc static;
- `Constructor`: hàm tạo, không có instance đích trước khi khởi tạo.

Điểm mấu chốt: đối số `obj` của `canAccess` là **đối tượng đích để xét quyền truy cập**, không phải giá trị trường, tham số phương thức, hoặc tên lớp. Với thành viên static và constructor, phải truyền `null`.

## 2. Chọn `obj` đúng với loại thành viên

| Đối tượng Reflection | Đối số `canAccess(obj)` hợp lệ | Ví dụ |
| --- | --- | --- |
| `Field` instance | Một đối tượng khác `null`, là instance của lớp khai báo trường hoặc lớp con | `field.canAccess(customer)` |
| `Method` instance | Một đối tượng khác `null`, là instance của lớp khai báo phương thức hoặc lớp con | `method.canAccess(customer)` |
| `Field` static | **`null`** | `field.canAccess(null)` |
| `Method` static | **`null`** | `method.canAccess(null)` |
| `Constructor` | **`null`** | `ctor.canAccess(null)` |

**Quan trọng:** đây là quy tắc riêng cho `canAccess`. Một số API gọi Reflection khác có thể có quy tắc cho tham số đối tượng đích khác (ví dụ `Field.get` có thể bỏ qua đối tượng truyền vào đối với trường static); không được suy ngược quy tắc ấy cho `canAccess`.

Một instance của **lớp con** vẫn có thể là đối tượng đích hợp lệ cho thành viên instance được khai báo ở lớp cha. Điều kiện kiểu đích hợp lệ chỉ là **điều kiện đầu vào**; chưa đủ để kết luận có quyền truy cập. Đặc biệt với `protected` qua các package, kiểu receiver còn có thể ảnh hưởng đến kết quả kiểm tra quyền theo quy tắc Java.

### Khi truyền sai đối tượng đích

`canAccess` **ném `IllegalArgumentException`**, thay vì trả `false`, nếu:

1. Thành viên là **instance field / instance method** nhưng `obj == null`;
2. Thành viên là instance field / method nhưng `obj` không phải instance của lớp khai báo (hoặc lớp con phù hợp);
3. Thành viên là **static field / static method / constructor** nhưng `obj != null`.

Vì vậy, cần phân biệt:

- `false` = đối số đích đã đúng dạng, nhưng mã gọi **không có quyền truy cập** theo trạng thái/quy tắc hiện tại;
- `IllegalArgumentException` = **đối tượng đích đưa vào `canAccess` không hợp lệ**. Đây không phải một kết quả kiểm tra quyền `false`.

## 3. `canAccess` kiểm tra quyền của ai?

`canAccess` trả lời câu hỏi: **caller hiện tại** có được phép truy cập thành viên được phản chiếu trên đối tượng đích này không?

Phép kiểm tra liên quan đến nhiều yếu tố:

- `public`, `protected`, package-private, `private` của thành viên và khả năng truy cập lớp khai báo;
- vị trí của mã gọi: lớp, package, quan hệ kế thừa;
- với ứng dụng dùng JPMS (Java Platform Module System): ranh giới module, package được `exports` hoặc `opens`, tùy loại quyền đang dùng;
- cờ tắt kiểm tra quyền Java language access control của chính đối tượng `Field`/`Method`/`Constructor` đó, được bật khi `trySetAccessible()` hoặc `setAccessible(true)` thành công;
- kiểu đối tượng đích, nhất là quy tắc receiver của thành viên `protected` trong một số trường hợp kế thừa khác package.

Nếu cờ bỏ qua kiểm tra quyền đang được bật thành công, `canAccess` trả `true` khi `obj` hợp lệ. Nếu cờ chưa bật, nó xét quyền của caller theo cơ chế kiểm soát truy cập Java (Reflection có điều chỉnh riêng về module readability theo Javadoc).

**Không nên coi `canAccess` là đặc điểm cố định của riêng một `Field` hay `Method`.** Khi gọi nó từ các lớp khác nhau, kết quả có thể khác, vì caller thay đổi. Chuyển một `Method` qua helper rồi gọi `canAccess` trong helper cũng khiến ngữ cảnh kiểm tra gắn với helper, không tự chuyển quyền của caller ban đầu.

## 4. Ví dụ Java 17+ chạy độc lập

Lưu thành file `CanAccessDemo.java`. Hai lớp `CanAccessDemo` và `AccessTarget` là **hai lớp top-level khác nhau** trong cùng file và cùng package mặc định; nhờ đó ví dụ về `private` không bị ảnh hưởng bởi quan hệ nestmate của các lớp lồng nhau.

~~~java
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class CanAccessDemo {
    public static void main(String[] args) throws Exception {
        AccessTarget target = new AccessTarget();

        Field publicField =
                AccessTarget.class.getDeclaredField("count");
        Field privateField =
                AccessTarget.class.getDeclaredField("secret");
        Field staticField =
                AccessTarget.class.getDeclaredField("total");
        Method instanceMethod =
                AccessTarget.class.getDeclaredMethod("ping");
        Method staticMethod =
                AccessTarget.class.getDeclaredMethod("reset");
        Constructor<AccessTarget> constructor =
                AccessTarget.class.getDeclaredConstructor();

        // Instance member: truyền instance đích hợp lệ.
        System.out.println("public field: " + publicField.canAccess(target));
        System.out.println("instance method: " + instanceMethod.canAccess(target));

        // Static member và constructor: truyền null.
        System.out.println("static field: " + staticField.canAccess(null));
        System.out.println("static method: " + staticMethod.canAccess(null));
        System.out.println("constructor: " + constructor.canAccess(null));

        // Đích hợp lệ nhưng private: caller không có quyền mặc định.
        System.out.println("private before: " + privateField.canAccess(target));

        try {
            publicField.canAccess(null);
        } catch (IllegalArgumentException e) {
            System.out.println("instance + null: IllegalArgumentException");
        }

        try {
            publicField.canAccess(new Object());
        } catch (IllegalArgumentException e) {
            System.out.println("instance + wrong type: IllegalArgumentException");
        }

        try {
            staticField.canAccess(target);
        } catch (IllegalArgumentException e) {
            System.out.println("static + non-null: IllegalArgumentException");
        }

        try {
            constructor.canAccess(target);
        } catch (IllegalArgumentException e) {
            System.out.println("constructor + non-null: IllegalArgumentException");
        }

        // Hai lớp cùng unnamed module: có thể bật quyền trong ví dụ này.
        boolean enabled = privateField.trySetAccessible();
        System.out.println("opened private: " + enabled);
        System.out.println("private after: " + privateField.canAccess(target));

        if (privateField.canAccess(target)) {
            System.out.println("secret value: " + privateField.get(target));
        }
    }
}

class AccessTarget {
    public int count = 3;
    private String secret = "demo";
    public static int total = 9;

    public AccessTarget() { }

    public void ping() { }

    public static void reset() { }
}
~~~

Chạy:

~~~bash
javac CanAccessDemo.java
java CanAccessDemo
~~~

Kết quả trên JDK 17+ khi hai lớp được chạy trong cùng unnamed module (như lệnh trên):

~~~text
public field: true
instance method: true
static field: true
static method: true
constructor: true
private before: false
instance + null: IllegalArgumentException
instance + wrong type: IllegalArgumentException
static + non-null: IllegalArgumentException
constructor + non-null: IllegalArgumentException
opened private: true
private after: true
secret value: demo
~~~

**Giải thích:**

1. Trường `count` và phương thức `ping` là public, mã `CanAccessDemo` ở cùng package với `AccessTarget`, và `target` là instance hợp lệ: `canAccess(target)` trả `true`.
2. `total` và `reset` là static; constructor không có instance đích: phải dùng `canAccess(null)`.
3. `secret` là `private` của **một lớp top-level khác**. Caller `CanAccessDemo` không được truy cập mặc định, nên kết quả `false` dù `target` đúng kiểu.
4. Bốn lời gọi trong khối `try` cố tình vi phạm quy tắc của tham số `obj`. Mỗi lời gọi phát sinh `IllegalArgumentException` trước khi đưa ra kết luận có quyền hay không.
5. `trySetAccessible()` thành công trong ví dụ cùng unnamed module; trạng thái cờ cho phép bỏ qua kiểm tra truy cập ngôn ngữ, nên `canAccess(target)` đổi thành `true` và `Field.get(target)` đọc được trường private. Điều đó **không bảo đảm thành công với mọi module hoặc mọi thành viên**.

## 5. Dùng `canAccess` cùng `trySetAccessible` như thế nào?

`canAccess(obj)` là **phép kiểm tra**; nó không tự thay đổi quyền truy cập. `trySetAccessible()` là **yêu cầu bật cờ bỏ qua kiểm tra truy cập** nếu được phép:

~~~java
// Minh họa trong ngữ cảnh đã có:
// Field field = ...; Object receiver = ...; receiver đúng kiểu.
if (!field.canAccess(receiver)) {
    if (!field.trySetAccessible()) {
        throw new IllegalAccessException("Không thể bật quyền Reflection");
    }
}
Object value = field.get(receiver);
~~~

Đây là đoạn code **minh họa trong phương thức có xử lý/khai báo checked exception**; nó không phải chương trình độc lập. Chỉ thực hiện yêu cầu mở quyền đối với thành viên mà ứng dụng được phép và thật sự cần truy cập.

- `canAccess` không mở quyền; `false` không đồng nghĩa với `trySetAccessible` chắc chắn sẽ thành công.
- `trySetAccessible` trả `true` nếu bật được cờ; trả `false` khi không thể bật do giới hạn truy cập thông thường, chẳng hạn package của module khác không mở thích hợp cho caller. Nó vẫn có thể ném `SecurityException` trong môi trường áp dụng Security Manager.
- `setAccessible(true)` có mục tiêu tương tự, nhưng có thể ném `InaccessibleObjectException` nếu không thể bật cờ do hạn chế đóng gói module.
- `isAccessible()` đã deprecated từ Java 9. Tên của nó dễ gây hiểu nhầm: nó chỉ phản ánh cờ bỏ qua kiểm tra quyền, **không** trả lời đầy đủ caller có quyền truy cập hay không. Dùng `canAccess` cho câu hỏi kiểm tra quyền hiện tại.
- `canAccess` trả `true` không có nghĩa thao tác kế tiếp chắc chắn thành công vì còn các điều kiện khác: đối số của `Method.invoke`, kiểu dữ liệu khi `Field.set`, trạng thái `final`, ngoại lệ từ phương thức đích và các yếu tố runtime khác.

### Các trạng thái dễ nhầm lẫn

| Trường hợp | Kết quả thường thấy | Lý do |
| --- | --- | --- |
| Public instance field, receiver đúng kiểu, lớp/package truy cập được | `true` | Caller có quyền thông thường. |
| Private instance field của lớp top-level khác, receiver đúng kiểu | `false` | Receiver hợp lệ nhưng quyền bị hạn chế. |
| Instance field, receiver `null` | `IllegalArgumentException` | Sai loại đối tượng đích. |
| Instance method, receiver không thuộc lớp khai báo hoặc lớp con | `IllegalArgumentException` | Sai loại đối tượng đích. |
| Static method, receiver là một object | `IllegalArgumentException` | Static đòi `null`. |
| Constructor, receiver là object | `IllegalArgumentException` | Constructor đòi `null`. |
| Private field, đã `trySetAccessible()` thành công, receiver đúng kiểu | `true` | Đã bật cờ bỏ qua kiểm tra truy cập. |

## 6. Bẫy trong code thực tế

### Nhầm đối số của `canAccess` với đối số của `invoke`

Với instance method:

~~~java
Method m = String.class.getMethod("substring", int.class);
String receiver = "reflection";

System.out.println(m.canAccess(receiver)); // true
System.out.println(m.invoke(receiver, 3));  // lection
~~~

`canAccess(receiver)` nhận **receiver**, không nhận `3`. `invoke(receiver, 3)` nhận receiver **và** đối số truyền cho `substring`. Nếu gọi `m.canAccess(3)`, đây là `Integer` chứ không phải `String`, nên ném `IllegalArgumentException`. Đoạn code trên minh họa cần đặt trong hàm có xử lý/khai báo ngoại lệ checked.

### Nhầm `null` ở constructor với chưa được phép tạo instance

`constructor.canAccess(null)` chỉ kiểm tra quyền đối với constructor. `null` là giá trị bắt buộc của tham số kiểm tra, **không phải tham số truyền vào constructor**. Khi muốn tạo instance, dùng `constructor.newInstance(args...)` với các tham số thật và xử lý ngoại lệ thích hợp.

### Bỏ qua kiểm tra kiểu trước khi kiểm tra quyền

Nếu viết tiện ích chung để gọi Reflection, trước hết xác định có phải static hay constructor không, rồi kiểm tra receiver:

~~~java
// Mã phác họa áp dụng khi đã có AccessibleObject member.
if (member instanceof Constructor<?>) {
    // canAccess(null)
} else if (member instanceof Field field) {
    // Modifier.isStatic(field.getModifiers()) ? null : receiver
} else if (member instanceof Method method) {
    // Modifier.isStatic(method.getModifiers()) ? null : receiver
}
~~~

Đoạn trên là mô tả logic, không phải hàm hoàn chỉnh. Với instance member, cần kiểm tra `receiver != null` và `declaringClass.isInstance(receiver)` trước khi gọi `canAccess(receiver)`, để lỗi dữ liệu đích được báo rõ ràng. `Class.isInstance` phù hợp với instance lớp con, thay vì chỉ so sánh chính xác `receiver.getClass() == declaringClass`.

### Hiểu sai module và `public`

Một phương thức `public` không tự động truy cập được từ mọi module nếu lớp hoặc package khai báo không được xuất phù hợp. Ngược lại, có thể lấy metadata bằng `getDeclaredMethod` nhưng chưa có quyền `invoke`. Khi ứng dụng ở các named module khác nhau, `exports` liên quan đến quyền sử dụng API công khai; `opens` liên quan đến khả năng thực hiện deep reflection. Quyền mở bằng `trySetAccessible` không được xem như một cách vượt mọi ranh giới module.

## 7. Checklist quyết định `canAccess` theo từng bước

1. **Xác định loại thành viên:** `Field`, `Method` hay `Constructor`.
2. **Xác định static hay instance:** `Modifier.isStatic(member.getModifiers())` áp dụng cho field/method.
3. **Chọn receiver:** static/constructor → `null`; instance → object thuộc lớp khai báo hoặc lớp con.
4. **Gọi `canAccess(receiver)`:** xử lý riêng trường hợp sai receiver gây `IllegalArgumentException`.
5. **Nếu trả `false`:** xem quyền lớp/package/module và chính sách ứng dụng; chỉ cân nhắc `trySetAccessible()` khi cần và được phép.
6. **Thực thi Reflection:** `Field.get/set`, `Method.invoke` hoặc `Constructor.newInstance`; tiếp tục xử lý các lỗi đặc thù của thao tác.

### Tự kiểm tra

- Có một `Field` private thuộc instance; truyền một instance đúng lớp vào `canAccess` nhưng chưa mở quyền. Kết quả là gì? **Thường là `false` nếu caller không có quyền**, không phải `IllegalArgumentException`.
- Có một `Method` static public; gọi `canAccess(new Object())` thì sao? **`IllegalArgumentException`**, vì static phải truyền `null`.
- Có `Field` instance khai báo trong `Base`, đối tượng thực tế là `Derived extends Base`. Receiver hợp lệ không? **Có**; sau đó mới xét quyền truy cập của caller.
- `trySetAccessible()` trả `false` thì có nên gọi `get` trên private field như thể đã mở quyền? **Không**; cần xử lý thiếu quyền hoặc thay đổi thiết kế/API.

**Kết luận:** `canAccess(obj)` cần **đúng đối tượng đích trước**, rồi mới đánh giá **quyền của caller**. `false` biểu thị không được phép truy cập với đối tượng hợp lệ; đối tượng sai loại gây `IllegalArgumentException`. Static member và constructor phải truyền `null`.

### Tài liệu chính thức

- [JDK 17 — AccessibleObject.canAccess(Object)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html#canAccess(java.lang.Object))
- [JDK 17 — AccessibleObject.trySetAccessible()](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html#trySetAccessible())
- [JDK 17 — java.lang.reflect.Modifier](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Modifier.html)
- [Java Language Specification §6.6 — Access Control](https://docs.oracle.com/javase/specs/jls/se17/html/jls-6.html#jls-6.6)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="access-control-005">setAccessible khác trySetAccessible</a>

<details>
<summary>Xem chi tiết</summary>

# setAccessible khác trySetAccessible trong Java Reflection

## 1. Vì sao cần hai API này?

Trong Reflection, các đối tượng `Field`, `Method`, `Constructor` đều kế thừa `AccessibleObject`. Bình thường, khi đọc/ghi field, gọi method hoặc khởi tạo đối tượng qua những đối tượng này, JVM kiểm tra quy tắc truy cập Java (`public`, `protected`, package-private, `private`) và ranh giới module.

`setAccessible(true)` và `trySetAccessible()` cùng nhằm **yêu cầu bỏ qua việc kiểm tra quyền truy cập Java tại thời điểm sử dụng đối tượng Reflection** khi JVM cho phép. Khác biệt chính là **cách thông báo khi không thể bật quyền truy cập**:

- `setAccessible(true)`: không trả về giá trị; nếu JVM không cho phép bật quyền, thường ném `InaccessibleObjectException`.
- `trySetAccessible()`: trả về `true` nếu đã bật được (hoặc vốn đã bật), `false` nếu JVM không cho phép bật quyền vì giới hạn truy cập/module.
- Cả hai vẫn tuân thủ quy tắc bảo vệ của Java Platform Module System (JPMS); không có API nào tự vượt qua package không mở của một module khác.

Việc **tìm thấy thành viên** và việc **được phép sử dụng thành viên** là hai bước riêng biệt. Ví dụ `getDeclaredField("secret")` có thể trả về metadata của một field `private`, nhưng `field.get(object)` có thể ném `IllegalAccessException` nếu chưa được phép truy cập.

## 2. So sánh chính xác

| Tiêu chí | `setAccessible(true)` | `trySetAccessible()` |
| --- | --- | --- |
| Khai báo API | `void setAccessible(boolean flag)` | `boolean trySetAccessible()` |
| Giá trị trả về | Không có | `true` hoặc `false` |
| Khi có thể bật | Đặt cờ bỏ qua access check thành `true` | Đặt cờ thành `true`, trả `true` |
| Khi bị chặn bởi giới hạn module | Ném `InaccessibleObjectException` (unchecked) | Trả `false` |
| Khi đã bật từ trước | Gọi `true` tiếp vẫn cho phép | Trả `true` mà không cần bật lại |
| Khi muốn tắt cờ | `setAccessible(false)` | Không có tham số để tắt |
| Khả dụng | API cũ, có từ các phiên bản Java trước | Có từ Java 9 |
| Khi Security Manager cũ từ chối quyền | Có thể ném `SecurityException` | Có thể ném `SecurityException`, **không bảo đảm trả `false`** |

**Không nhầm `trySetAccessible()` với `canAccess(receiver)`:**

- `trySetAccessible()` **thử thay đổi** cờ cho đối tượng Reflection.
- `canAccess(receiver)` **kiểm tra hiện tại** liệu caller có thể sử dụng đối tượng Reflection với receiver phù hợp hay không; bản thân nó không bật quyền.
- `isAccessible()` đã deprecated từ Java 9 vì tên dễ gây hiểu lầm: nó phản ánh cờ override, không nhất thiết phản ánh việc caller thực sự truy cập được thành viên. Khi cần hỏi quyền truy cập hiện tại, ưu tiên `canAccess(...)`.

## 3. Cơ chế bên trong: cờ của đối tượng Reflection

Giả sử có field khai báo là `private String secret` trong lớp khác:

```text
getDeclaredField("secret")
    |
    v
Nhận metadata Field (chưa được tự động cấp quyền truy cập)
    |
    +--> field.get(instance) --> có thể IllegalAccessException
    |
    +--> field.trySetAccessible()
    |          |
    |          +--> true: dùng field.get(instance)
    |          +--> false: chọn cách truy cập khác / xử lý giới hạn
    |
    +--> field.setAccessible(true)
               |
               +--> thành công: dùng field.get(instance)
               +--> bị module chặn: InaccessibleObjectException
```

Cờ này gắn với **đối tượng `Field`/`Method`/`Constructor` đang dùng**; nó không sửa từ khóa `private` trong mã nguồn, không biến member thành `public` và không cấp quyền cho mọi nơi trong chương trình. Khi gọi `setAccessible(false)`, lần sử dụng sau lại tuân theo access check thông thường. Nếu một thành viên vốn có thể truy cập công khai, `canAccess(...)` vẫn có thể trả `true` dù cờ override đang là `false`.

## 4. Ví dụ đầy đủ: cả hai API đều thành công khi được phép

Lưu dưới tên `AccessibleDemo.java`. Hai lớp top-level được khai báo **trong cùng một tệp**; `Vault.secret` là `private` đối với `AccessibleDemo`.

```java
import java.lang.reflect.Field;

public class AccessibleDemo {
    public static void main(String[] args) throws ReflectiveOperationException {
        Vault vault = new Vault("abc123");
        Field field = Vault.class.getDeclaredField("secret");

        System.out.println("Before: " + field.canAccess(vault));

        System.out.println("Try: " + field.trySetAccessible());
        System.out.println("After try: " + field.canAccess(vault));
        System.out.println("Read: " + field.get(vault));

        field.setAccessible(false);
        System.out.println("After reset: " + field.canAccess(vault));
        try {
            field.get(vault);
        } catch (IllegalAccessException e) {
            System.out.println("Read after reset: IllegalAccessException");
        }

        field.setAccessible(true);
        System.out.println("After set: " + field.canAccess(vault));
        System.out.println("Read again: " + field.get(vault));
    }
}

class Vault {
    private final String secret;

    Vault(String secret) {
        this.secret = secret;
    }
}
```

Biên dịch/chạy trên Java 17+ bằng classpath thông thường:

```bash
javac AccessibleDemo.java
java AccessibleDemo
```

Stdout:

```text
Before: false
Try: true
After try: true
Read: abc123
After reset: false
Read after reset: IllegalAccessException
After set: true
Read again: abc123
```

Giải thích:

1. `getDeclaredField` tìm được field `private`, nhưng chưa mở quyền; `canAccess(vault)` trả `false`.
2. `trySetAccessible()` trả `true` vì hai lớp ở cùng unnamed module khi chạy trên classpath; `field.get(vault)` đọc thành công.
3. `setAccessible(false)` bỏ cờ override; lần đọc tiếp theo ném `IllegalAccessException` như quy tắc truy cập Java.
4. `setAccessible(true)` bật lại quyền và không trả giá trị; `canAccess(vault)` xác nhận có thể đọc.
5. Field là `final` nhưng ở đây **chỉ đọc**, không thay đổi giá trị.

**Lưu ý receiver:** Với field/method instance, `canAccess(obj)` cần một đối tượng đúng kiểu khai báo hoặc kiểu con. Với field/method `static` và constructor, truyền `null` vào `canAccess(null)`. Truyền sai receiver có thể ném `IllegalArgumentException`.

## 5. Ví dụ khi JPMS không cho phép truy cập sâu

Từ Java 9, một **named module** có thể xuất khẩu package cho API công khai mà vẫn giữ kín các member nội bộ. Ví dụ, trong JDK 17+ mặc định, package `java.lang` của module `java.base` được export nhưng **không mở để truy cập sâu** cho ứng dụng trên classpath.

Lưu dưới tên `JdkModuleAccessDemo.java`:

```java
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;

public class JdkModuleAccessDemo {
    public static void main(String[] args) throws NoSuchFieldException {
        Field field = String.class.getDeclaredField("value");

        System.out.println("trySetAccessible: " + field.trySetAccessible());

        try {
            field.setAccessible(true);
            System.out.println("setAccessible: enabled");
        } catch (InaccessibleObjectException e) {
            System.out.println("setAccessible: InaccessibleObjectException");
        }
    }
}
```

Chạy trong môi trường JDK 17+ thông thường, **không** thêm tùy chọn mở package và không có agent/cấu hình mở quyền khác:

```bash
javac JdkModuleAccessDemo.java
java JdkModuleAccessDemo
```

Stdout điển hình:

```text
trySetAccessible: false
setAccessible: InaccessibleObjectException
```

Cả hai API được gọi trên cùng một `Field`: lần thử đầu thất bại không tự mở quyền; lần gọi `setAccessible(true)` sau đó ném exception vì `java.base/java.lang` chưa được mở cho unnamed module.

Nếu **chủ động mở đúng package** khi khởi động để minh họa cơ chế (không phải cấu hình khuyến nghị cho ứng dụng sản xuất):

```bash
java --add-opens java.base/java.lang=ALL-UNNAMED JdkModuleAccessDemo
```

Khi đó stdout trở thành:

```text
trySetAccessible: true
setAccessible: enabled
```

`--add-opens` tác động tới quyền mở package tại lúc khởi động JVM; bản thân `trySetAccessible()` không làm package được mở. Ví dụ dùng field nội bộ `String.value` **chỉ để quan sát access control**, không nên dựa vào cấu trúc riêng của JDK để viết logic ứng dụng.

## 6. Quy tắc module cần hiểu

| Quan hệ/thiết lập | Có thể bật Reflection để truy cập member `private`? |
| --- | --- |
| Caller và lớp khai báo ở cùng module | Thông thường có thể |
| Lớp ở unnamed module hoặc open module | Thông thường có thể, nếu không có ràng buộc bảo mật khác |
| Lớp ở named module khác, package chỉ được `exports` | **Không** đủ để truy cập sâu member `private` |
| Lớp ở named module khác, package được `opens` đến module caller | Có thể |
| Chạy với `--add-opens source.module/package=target.module` đúng đối tượng | Có thể theo phạm vi package đã mở |

`exports` chủ yếu phục vụ sử dụng API công khai từ module khác; `opens` cho phép truy cập Reflection sâu khi được cấp tới module caller. `--add-exports` riêng lẻ không thay thế `--add-opens` cho field `private`.

Một số trường hợp member `public` và lớp `public` trong package đã export cũng có thể được `setAccessible(true)` theo quy định API; không cần mở package cho **mọi** thao tác Reflection. Sự khác biệt quan trọng ở đây là quyền truy cập sâu tới member không công khai của module khác.

## 7. Chọn API phù hợp trong chương trình

Khi quyền truy cập sâu là **tùy chọn**, ưu tiên thử có điều kiện và có đường xử lý khi thất bại:

```java
// Giả sử field và instance đã được lấy đúng kiểu.
if (field.canAccess(instance) || field.trySetAccessible()) {
    Object value = field.get(instance);
    // Dùng value theo nghiệp vụ.
} else {
    // Không có quyền: dùng public API hoặc thông báo cấu hình module cần thiết.
}
```

Đây là **đoạn minh họa đặt trong phương thức xử lý**, không phải một chương trình độc lập. `field.get(instance)` vẫn có thể ném `IllegalAccessException` theo khai báo API; caller cần `throws` hoặc bắt exception đúng cách. Không dùng `trySetAccessible()` như cách che đi mọi lỗi bảo mật: nếu chương trình phụ thuộc bắt buộc vào field riêng tư, cần thông báo thiếu `opens` hoặc thiết kế lại giao diện công khai.

Khi quyền này là **bắt buộc** và muốn thất bại sớm, có thể dùng:

```java
// Minh họa trong ngữ cảnh đã có biến Field field.
field.setAccessible(true); // Có thể ném InaccessibleObjectException.
```

Nếu cần xử lý thất bại có chủ đích, bắt `InaccessibleObjectException` và cung cấp thông báo cấu hình cụ thể; không nuốt exception rồi tiếp tục truy cập như thể thành công.

## 8. Lỗi thường gặp

| Lỗi hoặc ngộ nhận | Điều thực tế xảy ra | Hướng xử lý |
| --- | --- | --- |
| Nghĩ `trySetAccessible()` chỉ kiểm tra, không đổi trạng thái | Nó **thử bật** cờ `accessible` thành `true` | Dùng `canAccess(...)` nếu chỉ cần kiểm tra |
| Nghĩ `trySetAccessible()` không bao giờ ném exception | Với Security Manager có hiệu lực trên một số JDK cũ, vẫn có thể ném `SecurityException` | Phân biệt từ chối module (`false`) với từ chối bảo mật (exception) |
| Bắt `IllegalAccessException` quanh `setAccessible(true)` | Khi bị JPMS chặn ở bước bật quyền, ngoại lệ là `InaccessibleObjectException` | Bắt ngoại lệ ở đúng giai đoạn |
| `getDeclaredField` thành công nên nghĩ `get` luôn thành công | Metadata tồn tại không đồng nghĩa có quyền sử dụng | Kiểm tra `canAccess`, bật quyền khi được phép |
| Package đã `exports` mà field `private` vẫn bị chặn | `exports` không đồng nghĩa với `opens` | Ưu tiên public API; nếu thật sự cần deep reflection, cấu hình `opens` rõ phạm vi |
| Gọi `canAccess(null)` cho field instance | Receiver sai, gây `IllegalArgumentException` | Truyền instance thuộc kiểu khai báo |
| Dùng `isAccessible()` để kết luận member gọi được | API deprecated, chỉ phản ánh cờ override | Dùng `canAccess(receiver)` |
| Nghĩ `setAccessible(true)` cho phép sửa mọi `final` field | Không thể bật quyền ghi với những final field không thể sửa như `static final`, final field của record hoặc hidden class | Không phụ thuộc vào việc sửa các field này |

Không khuyến khích mở rộng hàng loạt package nội bộ JDK chỉ để làm cho Reflection chạy được. Framework cần truy cập sâu thường nên có cơ chế cấu hình `opens` chính xác hoặc sử dụng API công khai và các hợp đồng mở rộng phù hợp.

## 9. Câu hỏi tự kiểm tra

1. Nếu `trySetAccessible()` trả `true`, lời gọi có làm thay đổi từ khóa `private` của member không? **Không**; chỉ thay đổi cờ trên đối tượng Reflection.
2. Vì sao `trySetAccessible()` có thể trả `false` trong khi `getDeclaredField` vẫn tìm thấy field? Vì tra cứu metadata khác với quyền truy cập sâu.
3. `setAccessible(false)` có luôn khiến `canAccess(...)` thành `false` không? **Không**; member `public` đang truy cập hợp lệ vẫn có thể đọc/gọi được.
4. Với named module khác không mở package, nên xử lý thế nào? Ưu tiên API công khai hoặc yêu cầu module chủ sở hữu mở chính xác package cần thiết; `trySetAccessible()` không tự vượt giới hạn module.

**Ghi nhớ:** `setAccessible(true)` phù hợp khi muốn yêu cầu bật quyền và xử lý **exception** nếu không thể; `trySetAccessible()` phù hợp khi muốn **thử bật quyền với kết quả boolean** để rẽ nhánh. Cả hai cùng chịu ràng buộc truy cập/module của JVM.

Tham khảo API chính thức: [`java.lang.reflect.AccessibleObject` trong Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="access-control-006">Ý nghĩa thực sự của isAccessible và lý do API này lỗi thời</a>

<details>
<summary>Xem chi tiết</summary>

# Ý nghĩa thực sự của isAccessible() và lý do API này lỗi thời

Trong Java Reflection, `isAccessible()` là phương thức được kế thừa từ `java.lang.reflect.AccessibleObject`, lớp cơ sở cho đối tượng `Field`, `Method` và `Constructor`. Tên hàm khiến nhiều người tưởng rằng nó trả lời: “Thành viên này có truy cập được từ mã đang chạy hay không?”. **Đó không phải ý nghĩa của `isAccessible()`.**

Hàm này chỉ đọc **accessible flag** của một *đối tượng Reflection*. Cờ cho biết chương trình đã yêu cầu **bỏ qua các kiểm tra quyền truy cập theo ngôn ngữ Java** (*suppress Java language access checks*) cho đối tượng đó hay chưa. Cờ không phản ánh đầy đủ việc người gọi hiện tại vốn đã có quyền truy cập thông thường hay không.

Từ **Java 9**, `isAccessible()` được đánh dấu `@Deprecated(since = "9")` vì chính tên hàm gây hiểu lầm. Java cung cấp `canAccess(Object obj)` để hỏi xem mã gọi hiện tại có truy cập được thành viên không, và `trySetAccessible()` để thử bật cờ bỏ qua kiểm tra khi thật sự cần. Hiểu rõ ba câu hỏi này là trọng tâm của bài học.

## 1. Accessible flag thực sự có nghĩa gì?

Reflection có thể **tìm thấy metadata** của một thành viên chưa chắc người gọi đã **được phép sử dụng** thành viên ấy.

Ví dụ, `SomeClass.class.getDeclaredField("secret")` có thể trả về đối tượng `Field` đại diện cho trường `private`. Tuy nhiên, khi gọi `field.get(instance)`, Java vẫn kiểm tra quyền truy cập của mã đang thực hiện lời gọi, trừ khi đối tượng `Field` đã được bật cờ bỏ qua kiểm tra một cách hợp lệ.

Mặc định, đối tượng `Field`, `Method` hoặc `Constructor` mới lấy bằng Reflection có accessible flag là `false`. Điều này có nghĩa **kiểm tra quyền truy cập vẫn được áp dụng**, chứ không có nghĩa mọi truy cập đều bị từ chối.

Đặc biệt:

- Một thành viên `public` có thể truy cập hợp lệ dù `isAccessible() == false`.
- Một thành viên `private` có thể truy cập hợp lệ từ chính lớp khai báo hoặc từ ngữ cảnh nestmate ngay cả khi cờ là `false`.
- Nếu cờ đã được bật thành `true` hợp lệ, đối tượng Reflection được dùng để bỏ qua các kiểm tra truy cập mà cờ đó điều khiển.
- Cờ là trạng thái của **đối tượng Reflection cụ thể**; nó không thay đổi modifier của lớp/thành viên, không làm thành viên trở thành `public`, cũng không tự cấp quyền cho mọi đối tượng Reflection khác.

So sánh ngắn các API:

| API | Ý nghĩa | Có thay đổi trạng thái không? |
| --- | --- | --- |
| `isAccessible()` | Đọc cờ bỏ qua kiểm tra quyền truy cập | Không; deprecated từ Java 9 |
| `canAccess(obj)` | Kiểm tra quyền truy cập của *caller hiện tại* với receiver hợp lệ | Không |
| `setAccessible(true)` | Yêu cầu bật cờ bỏ qua kiểm tra quyền truy cập | Có, hoặc ném ngoại lệ nếu bị từ chối |
| `trySetAccessible()` | Thử bật cờ bỏ qua kiểm tra, báo thành công/thất bại | Có thể bật; có thể trả `false` |
| `setAccessible(false)` | Tắt cờ, dùng lại kiểm tra quyền truy cập thông thường | Có |

Không nên hiểu “accessible flag” là “thành viên có thể truy cập từ bất cứ đâu”; tên lịch sử của cờ không tương đương thuật ngữ “accessible to caller”.

## 2. Ví dụ Java 17+: isAccessible false nhưng canAccess true

Tạo tệp `AccessFlagDemo.java` với **hai lớp top-level riêng biệt** trong cùng package. Điều này có chủ ý: dùng lớp lồng của cùng một top-level class có thể tạo quan hệ *nestmates* với quyền truy cập `private` khác trường hợp đang minh họa.

```java
import java.lang.reflect.Field;

public class AccessFlagDemo {
    @SuppressWarnings("deprecation") // Chỉ dùng isAccessible để minh họa API cũ.
    public static void main(String[] args) throws ReflectiveOperationException {
        DemoTarget target = new DemoTarget();

        Field visible = DemoTarget.class.getDeclaredField("visible");
        Field secret = DemoTarget.class.getDeclaredField("secret");

        System.out.println("visible.isAccessible = " + visible.isAccessible());
        System.out.println("visible.canAccess = " + visible.canAccess(target));
        System.out.println("secret.isAccessible = " + secret.isAccessible());
        System.out.println("secret.canAccess.before = " + secret.canAccess(target));

        boolean enabled = secret.trySetAccessible();
        System.out.println("secret.trySetAccessible = " + enabled);
        System.out.println("secret.isAccessible.after = " + secret.isAccessible());
        System.out.println("secret.canAccess.after = " + secret.canAccess(target));

        if (enabled) {
            System.out.println("secret.value = " + secret.get(target));
        }
    }
}

class DemoTarget {
    public String visible = "hello";
    private String secret = "hidden";
}
```

Biên dịch/chạy với JDK 17 trở lên:

```bash
javac AccessFlagDemo.java
java AccessFlagDemo
```

Đầu ra kỳ vọng khi hai lớp chạy trong cùng unnamed module trên classpath:

```text
visible.isAccessible = false
visible.canAccess = true
secret.isAccessible = false
secret.canAccess.before = false
secret.trySetAccessible = true
secret.isAccessible.after = true
secret.canAccess.after = true
secret.value = hidden
```

Diễn giải:

1. `visible.isAccessible() == false` vì chưa ai bật cờ suppress của đối tượng `Field visible`. Nhưng trường `visible` là `public`, lớp khai báo cùng package với người gọi nên `visible.canAccess(target) == true`.
2. `secret.isAccessible() == false` lúc đầu, và `secret.canAccess(target) == false` vì `AccessFlagDemo` là lớp top-level **khác** `DemoTarget`, không có quyền truy cập trực tiếp trường `private secret`.
3. `secret.trySetAccessible() == true` trong ví dụ này vì hai lớp ở cùng unnamed module; Java cho phép bật suppress trong phạm vi ấy.
4. Sau đó `secret.isAccessible() == true` báo cờ đã bật; `secret.canAccess(target) == true` báo có thể truy cập qua `Field` này; `secret.get(target)` trả về `hidden`.

Annotation `@SuppressWarnings("deprecation")` chỉ ngăn cảnh báo biên dịch của lời gọi `isAccessible()` được dùng có chủ ý để dạy API cũ. Nó không thay đổi hành vi, không gỡ trạng thái deprecated, và **không phải** khuyến nghị dùng `isAccessible()` trong mã mới.

Ví dụ không dựa vào thứ tự Reflection trả về thành viên và không nhầm lẫn quyền truy cập của một lớp lồng với một lớp top-level độc lập.

## 3. Lý do cụ thể isAccessible() bị deprecated

Theo tài liệu chính thức của `AccessibleObject`, `isAccessible()` deprecated vì tên gọi gợi ý rằng phương thức kiểm tra *reflected object có accessible đối với caller hay không*, trong khi thực tế chỉ cho biết **các phép kiểm tra quyền truy cập đã bị suppress hay chưa**.

Bảng tình huống (giả định receiver đúng loại):

| Tình huống | `isAccessible()` | `canAccess(receiver)` |
| --- | --- | --- |
| Field `public` và caller có quyền thường, cờ chưa bật | `false` | `true` |
| Field `private` ngoài phạm vi caller, cờ chưa bật | `false` | `false` |
| Field `private` đã bật cờ suppress thành công | `true` | `true` |
| Field `private` trong ngữ cảnh chính lớp khai báo hoặc nestmate, cờ chưa bật | `false` | Có thể `true` |

Do đó đoạn kiểm tra sau có thể sai về nghiệp vụ:

```java
if (field.isAccessible()) {
    // SAI giả định: chỉ những field truy cập được mới được xử lý.
}
```

Đây là mảnh mã **minh họa sai lầm**, không phải chương trình độc lập. Nó có thể bỏ sót field `public` mà caller hoàn toàn có quyền truy cập.

Đối chiếu đúng mục đích:

- “**Caller hiện tại** có thể truy cập thành viên không?” → `canAccess(receiver)`.
- “Có thể **thử bật** suppress cho thành viên bị giới hạn không?” → `trySetAccessible()`.
- “Cờ suppress hiện bật chưa?” → trạng thái mà `isAccessible()` cũ trả về, hiếm khi cần hỏi trực tiếp trong mã ứng dụng mới.

**Deprecated không đồng nghĩa removed.** Trên Java 17/21, `isAccessible()` vẫn có mặt và có thể được biên dịch, dù không nên sử dụng như phép kiểm tra quyền truy cập. Cũng **không được suy diễn** rằng `setAccessible(boolean)` bị deprecated chỉ vì phương thức getter `isAccessible()` bị deprecated.

## 4. canAccess(Object) yêu cầu receiver phù hợp

`canAccess(Object obj)` xuất hiện từ Java 9. Nó xét quyền truy cập của **caller thực hiện `canAccess`**, thông qua đối tượng Reflection hiện tại và ngữ cảnh Java language access control. Nếu flag đã bật thành công, `canAccess` có thể trả về `true` nhờ việc suppress; nếu flag chưa bật, caller vẫn có thể được quyền theo các quy tắc truy cập thông thường.

Đối số `obj` không tùy ý:

| Loại thành viên | Đối số của `canAccess` |
| --- | --- |
| Instance field | Instance thuộc declaring class hoặc subtype phù hợp |
| Instance method | Instance thuộc declaring class hoặc subtype phù hợp |
| Static field | `null` |
| Static method | `null` |
| Constructor | `null` |

Ví dụ `field.canAccess(target)` dùng cho instance field, còn `staticField.canAccess(null)` dùng cho static field. Nếu truyền `null` cho instance member hoặc một instance không tương thích với declaring class, Java có thể ném `IllegalArgumentException`. Static member và constructor yêu cầu `null`; truyền object không phải `null` là không hợp lệ.

`canAccess` là thao tác **chỉ kiểm tra**, không chạy method, không đọc giá trị field và không bật suppress flag. Kết quả gắn với caller cụ thể; đừng giả định một helper class kiểm tra `canAccess` được `true` thì mọi mã khác cũng sẽ được quyền như vậy.

Ngoài ra, `canAccess() == true` **không bảo đảm** thao tác `Method.invoke` không ném ngoại lệ từ method, `Constructor.newInstance` không thất bại vì lý do khác hoặc mọi field đều ghi được. Khả năng truy cập theo Java chỉ là một phần điều kiện của thao tác thực tế.

## 5. trySetAccessible và setAccessible: khi muốn thay đổi cờ

`trySetAccessible()` và `setAccessible(true)` là các thao tác **đề nghị bỏ qua kiểm tra quyền truy cập**. Chúng không phải phép kiểm tra thuần túy như `canAccess`.

- `trySetAccessible()` cố gắng đặt cờ thành `true`. Nếu đã bật từ trước, trả `true`. Nếu không thể bật vì giới hạn truy cập/module, trả `false` thay vì ném `InaccessibleObjectException` tương ứng. Tuy nhiên API vẫn có thể ném `SecurityException` khi chính sách an ninh từ chối.
- `setAccessible(true)` cũng yêu cầu đặt cờ thành `true`, nhưng khi Java không cho phép thì có thể ném `java.lang.reflect.InaccessibleObjectException`; các lỗi kiểm tra an ninh phù hợp cũng có thể phát sinh.
- `setAccessible(false)` yêu cầu sử dụng lại việc kiểm tra quyền truy cập; nó **không đổi** modifier `private`/`public` trong bytecode.

Mã minh họa trong ngữ cảnh đã có `field` và `receiver` hợp lệ, kèm xử lý ngoại lệ theo ứng dụng:

```java
if (field.canAccess(receiver)) {
    // Đã có quyền: không cần thay đổi accessible flag.
    Object value = field.get(receiver);
} else if (field.trySetAccessible()) {
    // Được phép bật suppress flag.
    Object value = field.get(receiver);
} else {
    // Không được phép bật: dùng public API hoặc xử lý hạn chế truy cập.
}
```

Đoạn trên **không phải file Java độc lập**: cần `field`, `receiver` và xử lý/khai báo `IllegalAccessException`. Nó minh họa phân biệt nhánh “đã có quyền thường” với nhánh “thử suppress khi có nhu cầu”.

### Giới hạn của Java Platform Module System (JPMS)

Từ Java 9, việc một thư viện có thể bật suppress phụ thuộc thêm vào ranh giới module. Trong Java 21, API nêu các trường hợp được phép, gồm caller và declaring class ở cùng module, một số trường hợp thành viên `public`/`protected static` trong package `exports` phù hợp, hoặc declaring package `opens` cho module gọi. Unnamed/open modules áp dụng quy tắc mở package tương ứng.

Cần phân biệt:

- `exports`: cho phép truy cập public API của package bởi những module được cho phép.
- `opens`: cho phép deep reflection vào package cho những module được cho phép, khi ứng dụng/framework thật sự cần xem thành viên không công khai.

Nếu `consumer.module` thử bật suppress cho `private` member trong `internal.pkg` của `library.module` mà package đó không được `opens` cho caller, `trySetAccessible()` có thể trả `false`. Đừng mặc định có thể bỏ qua giới hạn bằng `setAccessible(true)`.

Ưu tiên public API và chỉ mở package theo phạm vi cần thiết nếu thiết kế thư viện/framework đòi hỏi deep reflection. Cờ JVM mở rộng quyền truy cập bừa bãi không phải giải pháp mặc định cho lỗi thiết kế.

### Không phải mọi final field đều ghi được

Cờ suppress dù được bật thành công cũng **không** cho phép ghi vào mọi trường `final`. Tài liệu Java 21 liệt kê những **non-modifiable final fields** như `static final` fields, final fields của hidden class và final fields của record; bật cờ có thể cho phép **đọc** nhưng không cấp quyền **ghi** cho các trường ấy. Quyền Reflection và tính chất bất biến của thành viên là hai vấn đề khác nhau.

## 6. Checklist ra quyết định và lỗi phổ biến

Dùng quy trình sau nếu đang viết code cần Reflection:

1. Xác định muốn **tra cứu metadata**, **kiểm tra quyền** hay **yêu cầu bỏ qua kiểm tra**.
2. Chọn `getDeclaredField`/`getDeclaredMethod` hoặc các API tương ứng đúng phạm vi; tìm thấy metadata không có nghĩa dùng được.
3. Xác định receiver: instance cho thành viên instance, `null` cho static/constructor.
4. Nếu chỉ muốn biết quyền hiện tại, gọi `canAccess(receiver)`.
5. Chỉ khi thực sự cần deep reflection và đã cân nhắc module/security mới thử `trySetAccessible()`, sau đó xử lý nhánh `false`.
6. Thao tác `get`/`set`/`invoke`/`newInstance` vẫn cần xử lý các lỗi riêng của nó. Không xem giá trị `true` là cam kết thành công vô điều kiện.

Lỗi thường gặp:

- **Dùng `isAccessible()` như `canAccess()`:** bỏ sót những thành viên có thể truy cập dù suppress flag đang `false`.
- **Cho rằng `isAccessible() == true` biến field thành `public`:** cờ chỉ áp dụng cho đối tượng Reflection, không sửa metadata class.
- **Cho rằng `private` luôn khiến `canAccess() == false`:** quên quyền của chính declaring class hoặc nestmates.
- **Nhầm `trySetAccessible()` với một phép kiểm tra không tác dụng phụ:** thực tế có thể thay đổi cờ.
- **Giả định mọi package của JDK/module khác có thể mở bằng `setAccessible(true)`:** quên ranh giới `opens` và JPMS.
- **Dùng receiver sai:** `canAccess(null)` trên instance method/field hoặc receiver không đúng kiểu có thể ném `IllegalArgumentException`.
- **Tưởng suppress mở khóa mọi `final` field:** bỏ qua giới hạn của non-modifiable final fields.

### Tự kiểm tra

1. Một `public` field có thể `isAccessible() == false` và `canAccess(instance) == true` không? **Có.** Truy cập thường đã hợp lệ mà không cần suppress.
2. Vì sao `isAccessible()` deprecated? **Tên hàm gây hiểu nhầm:** đọc flag, không trả lời đầy đủ quyền truy cập của caller.
3. API nào kiểm tra quyền mà không đổi trạng thái? **`canAccess(obj)`.**
4. API nào thử bật suppress và có thể trả `false` khi module ngăn chặn? **`trySetAccessible()`.**
5. `setAccessible(true)` có đổi modifier của field thành `public` không? **Không.**
6. `isAccessible()` đã bị xóa khỏi JDK 17/21 chưa? **Chưa.** Deprecated và removed là hai trạng thái khác nhau.
7. Vì sao ví dụ dùng hai lớp top-level khác nhau? **Tránh đặc quyền nestmate với trường private**, giúp phân biệt hai tình huống rõ ràng.

## 7. Tài liệu chính thức

- [Oracle Java SE 21 — `AccessibleObject`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AccessibleObject.html): tài liệu về `isAccessible`, `canAccess`, `trySetAccessible`, `setAccessible`, module và final fields.
- [Oracle Java SE 21 — `Field`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Field.html): `get`, `set` và các ngoại lệ khi truy cập trường.
- [Java Language Specification 21 — §6.6 Access Control](https://docs.oracle.com/javase/specs/jls/se21/html/jls-6.html#jls-6.6): nền tảng quyền truy cập trong ngôn ngữ Java.

**Ghi nhớ:** `isAccessible()` chỉ đọc **cờ suppress**, `canAccess(obj)` hỏi **caller hiện tại có quyền**, `trySetAccessible()` **thử thay đổi cờ**. Tách ba ý nghĩa này sẽ giúp sử dụng Reflection đúng và giải thích chính xác vì sao API cũ bị deprecated.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="access-control-007">Package, lớp khai báo và thành viên public trong kiểu không public</a>

<details>
<summary>Xem chi tiết</summary>

# Package, lớp khai báo và thành viên public trong kiểu không public (Java Reflection)

## 1. Vấn đề: `public` của thành viên chưa đủ để truy cập được

Trong Java, từ khóa `public` đặt trên **method hoặc field** mô tả mức truy cập của **thành viên đó**. Nó không tự biến **lớp khai báo thành viên** thành một lớp có thể nhìn thấy từ mọi package.

Ví dụ, Java cho phép khai báo lớp cấp cao nhất không có `public`:

```java
package demo.api;

class HiddenGreeting {
    public String greet() {
        return "Hello";
    }
}
```

`HiddenGreeting` có mức truy cập **package-private** vì khai báo lớp không có từ khóa `public`. Method `greet()` là `public`, nhưng code trong `demo.client` không thể trực tiếp khai báo biến kiểu `demo.api.HiddenGreeting` hay import lớp đó. Khi dùng Reflection, code bên ngoài có thể **tìm thấy metadata** của `greet()` mà vẫn bị chặn lúc **gọi method**.

Ba câu hỏi phải tách bạch:

1. **Package nào là ngữ cảnh của code gọi?** Hai lớp thuộc cùng package hay khác package? Có ranh giới module hoặc class loader không?
2. **Thành viên được khai báo trên lớp/interface nào?** Đọc `Method.getDeclaringClass()` hoặc `Field.getDeclaringClass()`; đừng mặc nhiên coi kiểu runtime là lớp khai báo.
3. **Cả lớp khai báo lẫn thành viên có thể truy cập từ nơi gọi không?** `Modifier.isPublic(method.getModifiers())` chỉ trả lời phần **member**, chưa kiểm tra visibility của lớp, package/module và toàn bộ quy tắc truy cập.

Nguyên tắc: **Có thể tra cứu metadata không đồng nghĩa được quyền sử dụng thành viên qua Reflection.**

## 2. Package và mức truy cập của lớp

Một *top-level class* (lớp khai báo trực tiếp trong file, không nằm trong lớp khác) chỉ có thể là `public` hoặc **package-private** ở góc độ access modifier; Java không cho khai báo top-level `private` hay `protected`.

| Khai báo lớp | Code trong cùng package | Code trong package khác |
| --- | --- | --- |
| `public class Api` | Có thể tham chiếu nếu đáp ứng ranh giới module | Có thể tham chiếu nếu package được xuất phù hợp và API tiếp cận được |
| `class Implementation` | Có thể tham chiếu theo quy tắc package | Không thể trực tiếp tham chiếu lớp bằng mã Java thông thường |

Java dùng tên package được khai báo bằng `package ...;` để xác định quan hệ truy cập, không dựa vào tên thư mục tình cờ giống nhau. Ở runtime, danh tính package còn phụ thuộc class loader và module; **hai lớp có cùng chuỗi tên package không mặc nhiên được hưởng package-private access** nếu chúng ở các runtime package khác nhau.

Một thành viên `public` có thể nằm trong lớp package-private; điều này hợp lệ. Trong cùng package, code gọi thường có thể sử dụng lớp và method ấy. Ngoài package, việc truy cập trực tiếp bằng tên lớp sẽ thất bại ngay ở bước biên dịch. Với Reflection, lỗi có thể chỉ xuất hiện khi `Method.invoke(...)` thực hiện kiểm tra truy cập.

Với **nested class**, cần xem cả chuỗi lớp bao ngoài và modifier của nested class. Ví dụ `private static class Impl` có thể chứa `public void run()`, nhưng chữ `public` của `run()` không làm `Impl` thành kiểu có thể tham chiếu từ code bên ngoài. Khả năng truy cập không chỉ là modifier của method.

## 3. Ví dụ Java 17+ hoàn chỉnh: method public nhưng gọi Reflection bị chặn

Ví dụ sau có **hai package** để thể hiện đúng ranh giới: `demo.api` giữ lớp triển khai kín bên trong, còn `demo.client` là người dùng API. Lớp triển khai là package-private, method triển khai là public; giao diện cung cấp hợp đồng public để gọi an toàn.

Tạo cấu trúc:

```text
src/
  demo/
    api/
      GreetingService.java
      Services.java
    client/
      Main.java
```

### Tệp `src/demo/api/GreetingService.java`

```java
package demo.api;

public interface GreetingService {
    String greet();
}
```

### Tệp `src/demo/api/Services.java`

```java
package demo.api;

public final class Services {
    private Services() {}

    public static GreetingService create() {
        return new HiddenGreeting();
    }
}

class HiddenGreeting implements GreetingService {
    @Override
    public String greet() {
        return "Hello";
    }
}
```

`Services` là factory public; `HiddenGreeting` không có `public` nên chỉ là một kiểu package-private. `GreetingService` công khai hành vi cần dùng mà không phơi bày tên lớp triển khai.

### Tệp `src/demo/client/Main.java`

```java
package demo.client;

import demo.api.GreetingService;
import demo.api.Services;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class Main {
    public static void main(String[] args) throws ReflectiveOperationException {
        GreetingService service = Services.create();
        Method impl = service.getClass().getMethod("greet");

        System.out.println("Runtime class: " + service.getClass().getName());
        System.out.println("Class public: "
                + Modifier.isPublic(service.getClass().getModifiers()));
        System.out.println("Method public: "
                + Modifier.isPublic(impl.getModifiers()));
        System.out.println("Declared by: " + impl.getDeclaringClass().getName());
        System.out.println("Normal call: " + service.greet());

        try {
            System.out.println("Reflect impl: " + impl.invoke(service));
        } catch (IllegalAccessException e) {
            System.out.println("Reflect impl: IllegalAccessException");
        }

        Method api = GreetingService.class.getMethod("greet");
        System.out.println("Reflect API: " + api.invoke(service));
    }
}
```

Tại thư mục gốc chứa `src`, tạo thư mục `out` trước khi chạy; các lệnh sau sử dụng đường dẫn tường minh, phù hợp cả shell thông thường và PowerShell:

```bash
mkdir out
javac --release 17 -d out src/demo/api/GreetingService.java src/demo/api/Services.java src/demo/client/Main.java
java -cp out demo.client.Main
```

Kết quả stdout (đã kiểm chứng bằng biên dịch và chạy):

```text
Runtime class: demo.api.HiddenGreeting
Class public: false
Method public: true
Declared by: demo.api.HiddenGreeting
Normal call: Hello
Reflect impl: IllegalAccessException
Reflect API: Hello
```

**Phân tích chính xác từng kết quả:**

- `service.getClass()` trả `HiddenGreeting.class` vì đó là lớp runtime của object mà factory tạo ra, dù biến được khai báo theo interface `GreetingService`.
- `Modifier.isPublic(service.getClass().getModifiers())` trả `false` vì lớp triển khai là package-private.
- `getMethod("greet")` **tìm thấy method public**: nó chỉ tìm theo metadata của các method public trong phạm vi API, không bảo đảm caller có quyền gọi method.
- `impl.getDeclaringClass()` là `HiddenGreeting.class`. Method có modifier `public` nhưng lớp khai báo không accessible từ `demo.client.Main`, nên `impl.invoke(service)` ném `IllegalAccessException`.
- Lời gọi Java thông thường `service.greet()` hợp lệ vì static type `GreetingService` là interface public, method trong interface là public; JVM thực hiện dispatch tới implementation.
- `GreetingService.class.getMethod("greet")` lấy `Method` **khai báo trên interface public**. Gọi `api.invoke(service)` có thể truy cập method của interface rồi dispatch tới implementation; kết quả là `Hello`.

Đây là ví dụ chỉ dùng **classpath/unnamed module**, không cần `module-info.java`. Với named module, cần xét thêm quy tắc `exports`/`opens` ở phần sau.

## 4. “Lớp runtime” và “lớp khai báo” là hai khái niệm khác nhau

Xét một biến `GreetingService service`:

- **Kiểu của biến tại compile time:** `GreetingService` (public interface).
- **Kiểu runtime của object:** `HiddenGreeting` (package-private class).
- **Lớp khai báo của một đối tượng `Method`:** thứ được trả về bởi `method.getDeclaringClass()`. Nếu lấy method từ `HiddenGreeting` thì đó là `HiddenGreeting`; nếu lấy từ `GreetingService` thì đó là `GreetingService`.

Đặc biệt khi có kế thừa, `SomeSubclass.class.getMethod("action")` có thể trả `Method` khai báo ở một superclass hoặc interface, thay vì ở `SomeSubclass`. Mã biên dịch và công cụ sinh lớp cũng có thể bổ sung method chuyển tiếp, nên **không suy đoán declaring class chỉ từ tên lớp dùng khi gọi `getMethod`**. Luôn xem `getDeclaringClass()` của đối tượng `Method` thực tế.

Đừng nhầm hai API sau:

- `method.getDeclaringClass()`: kiểu **khai báo method**.
- `someClass.getDeclaringClass()`: khi gọi trên đối tượng `Class` của nested/member class, cung cấp thông tin về lớp chứa khai báo kiểu đó (không phải “lớp khai báo một method”).

Đối với `Field` và `Constructor` cũng có `getDeclaringClass()`. Lớp khai báo có thể không public dù member constructor/method/field trông như public trong metadata.

## 5. `getMethod`, `getDeclaredMethod` và kiểm tra truy cập

| API | Nó trả về cái gì? | Nó có tự bỏ qua kiểm tra access không? |
| --- | --- | --- |
| `type.getMethod("name", ...)` | Method public trong phạm vi tra cứu công khai, có thể kế thừa | Không |
| `type.getMethods()` | Mảng các method public theo hợp đồng Reflection | Không |
| `type.getDeclaredMethod("name", ...)` | Method **khai báo trực tiếp** tại `type`, không phụ thuộc public/private của method | Không |
| `type.getDeclaredMethods()` | Mảng các method khai báo trực tiếp, gồm private/protected/package-private | Không |
| `method.invoke(receiver, args...)` | Thực thi method, nếu receiver và quyền truy cập hợp lệ | **Có kiểm tra** truy cập khi chưa bật suppression hợp lệ |
| `method.canAccess(receiver)` | Kiểm tra tại nơi gọi hiện tại có thể truy cập member trên receiver phù hợp hay không | Chỉ kiểm tra, không tự cấp quyền |

Việc `getDeclaredMethod` nhìn thấy một method `private` không làm method đó trở thành public. Ngược lại, `getMethod` tìm thấy method public trong lớp không public cũng không khiến `invoke` mặc nhiên thành công.

**Thứ tự kiểm tra khi gặp sự cố** nên là: (1) kiểu runtime; (2) `getDeclaringClass()`; (3) modifier của declaring class và member; (4) package của caller; (5) module và chính sách `exports`/`opens`; (6) object nhận lời gọi, đối số và ngoại lệ thực thi.

Đối tượng `Method` được lưu/cached có thể được gọi ở những vị trí code khác nhau. Kiểm tra quyền truy cập thông thường được thực hiện đối với caller phản chiếu ở thời điểm gọi; đừng kết luận rằng một `Method` đã tìm thấy ở nơi có quyền thì tự mang quyền ấy tới mọi vị trí sử dụng.

## 6. Các cách giải quyết đúng theo thiết kế

### 6.1. Ưu tiên hợp đồng public

Nếu bạn kiểm soát API, hãy khai báo method trong **public interface** hoặc public class có thể truy cập và truyền object triển khai qua hợp đồng đó. Ví dụ `GreetingService.class.getMethod("greet")` trong bài chạy được mà không cần can thiệp kiểm tra truy cập.

Cách này cũng giữ được khả năng thay implementation mà không buộc phía dùng phải biết tên lớp package-private. Đối với public method trong public interface/public class, vẫn cần package được export thích hợp nếu chạy giữa các named module.

### 6.2. Công bố phương thức qua một public facade

Một public facade có thể **tự khai báo** phương thức public rồi ủy quyền cho implementation nội bộ:

```java
package demo.api;

public final class GreetingFacade {
    public String greet() {
        return new HiddenGreeting().greet();
    }
}
```

Đây là **mảnh code thêm vào ví dụ ở mục 3**, sử dụng `HiddenGreeting` trong cùng package; lưu tên file `GreetingFacade.java` nếu muốn dùng thực tế. Khi phía client lấy `GreetingFacade.class.getMethod("greet")`, declaring class là một kiểu public. Đây là cách thích hợp nếu public class là hình thức API mong muốn.

Lưu ý nếu facade **chỉ kế thừa** method từ một superclass không public, không thể khẳng định method Reflection nhận được có declaring class public. Kết quả có thể chịu ảnh hưởng method kế thừa/forwarder được biên dịch. Muốn hợp đồng nhất quán, tự khai báo/override method trên public facade và kiểm tra `getDeclaringClass()`.

### 6.3. Đưa code gọi vào cùng package khi đó là quyền nội bộ

Code trong cùng runtime package với lớp package-private thường được quyền truy cập lớp và public member của nó. Dùng cách này cho **mã nội bộ** khi thiết kế package cho phép; không nên thay đổi package của client tùy tiện chỉ để vượt ranh giới API. Cùng chuỗi tên package nhưng class loader/module khác có thể không tạo cùng runtime package.

### 6.4. `setAccessible`/`trySetAccessible` chỉ khi thực sự cần deep reflection

`AccessibleObject.trySetAccessible()` cố bật bỏ qua kiểm tra truy cập theo khả năng được phép và trả boolean; `setAccessible(true)` có thể ném `InaccessibleObjectException` nếu Java module system không cho phép. Việc suppression thành công phụ thuộc lớp khai báo, module của caller, các package `opens`/`exports` và trường hợp cụ thể.

- Trên **classpath/unnamed module**, deep reflection đối với lớp của ứng dụng thường được phép, nên có thể quan sát thấy `trySetAccessible()` thành công.
- Giữa **named module**, một package chỉ `exports` chưa đủ để tự do deep-reflect member/lớp không public; `opens` tới module gọi (hoặc cấu hình mở tương đương) mới cho phép một số trường hợp suppression.
- Không lấy việc thử `setAccessible(true)` làm phương án sửa mặc định khi đã có public interface/facade đúng thiết kế. Không có bảo đảm có thể vượt mọi ranh giới access trên mọi JDK/module.

## 7. Vai trò của JPMS: `exports` khác `opens`

Từ Java 9, ngoài quyền truy cập theo Java language, named module còn có ranh giới package.

- `exports demo.api;` trong module cung cấp: cho các module đọc được module ấy truy cập **public types và public members** của package theo quy tắc Java. Nó **không làm `HiddenGreeting` thành public**.
- `opens demo.api;` hoặc `opens demo.api to some.module;`: cho phép deep reflection theo phạm vi mở; không đồng nghĩa mở quyền compile-time reference như `exports`.
- Package **không export** của named module có thể làm Reflection từ module khác bị chặn ngay cả với public class/method, nếu thiếu cấu hình truy cập tương ứng.
- `--add-exports` và `--add-opens` có thể dùng trong tình huống tích hợp/phân tích được kiểm soát, nhưng phải xác định đúng source module, package và target module; không sử dụng bừa bãi làm giải pháp API lâu dài.

Ví dụ, nếu `demo.api` nằm trong named module `demo.library` và `demo.client` trong `demo.app`, cần quan hệ module thích hợp (`requires` ở caller, `exports demo.api` ở thư viện) để mã Java ở phía client dùng `GreetingService`. Kể cả khi package được export, `HiddenGreeting` vẫn package-private, nên dùng `Method` khai báo trực tiếp tại lớp ấy để gọi từ module khác vẫn có thể bị `IllegalAccessException`. Một **public interface được export** vẫn là bề mặt API phù hợp.

## 8. Phân biệt lỗi tra cứu, lỗi quyền và lỗi khi method chạy

| Hiện tượng | Ý nghĩa thường gặp | Hướng xử lý |
| --- | --- | --- |
| `NoSuchMethodException` tại `getMethod(...)` | Không có method public phù hợp theo tên/chữ ký được tra cứu | Kiểm tra tham số `Class<?>...`, overload, và `getDeclaredMethod` nếu cần |
| `IllegalAccessException` tại `invoke(...)` | Reflection không có quyền gọi method theo kiểm tra truy cập hiện hành | Xác định `getDeclaringClass()`, package/module, chọn public API thích hợp |
| `InaccessibleObjectException` tại `setAccessible(true)` | Không thể bật suppression do giới hạn encapsulation | Kiểm tra cấu hình `opens` và sự cần thiết của deep reflection |
| `InvocationTargetException` tại `invoke(...)` | Method đích đã được gọi và tự ném exception; nguyên nhân nằm trong `getCause()` | Xử lý lỗi logic/runtime của method đích, không quy ngay về visibility |
| `IllegalArgumentException` tại `invoke(...)` | Receiver sai kiểu hoặc đối số/số lượng/kiểu không phù hợp | Kiểm tra object và chữ ký method |

Ví dụ câu sau là sai về lập luận:

> “Method có modifier `public` và `getMethod` trả về thành công, vậy `invoke` chắc chắn thành công.”

Bài chạy ở mục 3 là phản ví dụ: cả hai điều kiện đúng nhưng `impl.invoke(service)` vẫn bị `IllegalAccessException` vì `HiddenGreeting` không public đối với caller ở package khác.

## 9. Quy trình thực hành khi viết công cụ Reflection

1. Xác định **public API contract** mà phía gọi được phép dùng (interface hoặc facade).
2. Nếu phải truy vấn một class runtime, ghi nhận `type.getName()` và `Modifier.isPublic(type.getModifiers())`.
3. Với mỗi method/field, kiểm tra `getDeclaringClass()` và modifier của cả member lẫn kiểu khai báo; cân nhắc mọi lớp bao ngoài nếu là nested type.
4. Phân biệt rõ thao tác **khám phá metadata** và thao tác **gọi/đọc thành viên**. Bản ghi metadata không thay cho một kiểm tra quyền hợp lệ.
5. Giữ nguyên ranh giới package/module khi có thể. Với plugin/framework cần deep reflection, tài liệu hóa package phải `opens` và xử lý trường hợp không được cấp quyền.
6. Bắt chính xác `IllegalAccessException` khi invoke, và tách khỏi `InvocationTargetException` vốn là lỗi phát sinh từ method đích.

## 10. Tự kiểm tra

1. `class Worker { public void run() {} }` nằm ở `demo.impl` và là top-level class package-private. Tại sao `demo.client` không thể trực tiếp viết `new Worker()`?
2. Tại sao `Modifier.isPublic(method.getModifiers()) == true` chưa đủ để kết luận `method.invoke(...)` thành công?
3. Với ví dụ `HiddenGreeting`, đối tượng `Method` của `GreetingService.class.getMethod("greet")` và của `service.getClass().getMethod("greet")` có cùng `getDeclaringClass()` không? Kết quả `invoke` khác nhau như thế nào?
4. Đối với named module, `exports` có làm một top-level package-private class thành public không? Khi nào `opens` có tác dụng?
5. `InvocationTargetException` có chứng minh caller bị từ chối truy cập method không?

**Đáp án định hướng:** (1) class package-private không accessible từ package khác; (2) quyền truy cập còn phụ thuộc lớp khai báo, caller và module; (3) lần lượt là public `GreetingService` và package-private `HiddenGreeting`, chỉ lời gọi qua interface thành công trong chương trình mẫu; (4) không, `opens` cho deep reflection có kiểm soát; (5) không, nó bọc exception do method đích ném.

**Ghi nhớ:** Khi Reflection xử lý một thành viên `public` nhưng lớp khai báo không public, hãy đặt câu hỏi **“`Method.getDeclaringClass()` là ai, và caller có thể truy cập lớp đó không?”**. Tách riêng modifier của member, accessibility của declaring type, package và JPMS sẽ giúp hiểu chính xác vì sao đọc được metadata nhưng gọi bị chặn; public interface/facade là lời giải sạch cho API được dùng từ package khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="access-control-008">Nestmate và quyền truy cập private giữa các lớp liên quan</a>

<details>
<summary>Xem chi tiết</summary>

# Nestmate và quyền truy cập private giữa các lớp liên quan trong Java Reflection

## 1. Nestmate giải quyết vấn đề gì?

Từ **Java 11**, JVM hỗ trợ khái niệm **nest** (nhóm lớp cùng tổ): một *nest host* và các lớp thành viên (*nest members*) có quan hệ được ghi trong class file. Các lớp **nestmate** có thể truy cập trực tiếp thành viên `private` của nhau theo quy tắc kiểm tra quyền truy cập của JVM, miễn là cùng một nest hợp lệ.

Vấn đề gốc: trong Java, một lớp bên ngoài và những lớp lồng bên trong nó được phép sử dụng thành viên `private` của nhau ngay ở mức ngôn ngữ.

```java
class Library {
    private String code = "BOOK";

    static class Checker {
        String check(Library library) {
            return library.code; // Hợp lệ theo luật của Java
        }
    }
}
```

Trước Java 11, bytecode không biểu diễn được trực tiếp toàn bộ quyền riêng tư giữa các lớp này theo cơ chế truy cập `private` cũ. Trình biên dịch thường phải sinh các phương thức cầu nối `synthetic` dạng `access$...` để lớp lồng đọc thành viên `private` của lớp bao. Java 11 đưa ra **nest-based access control** (JEP 181): JVM hiểu quan hệ nest và có thể cho phép truy cập `private` trực tiếp mà không cần cầu nối đó.

> **Phân biệt quan trọng:** “Cùng nest” là quan hệ được JVM công nhận giữa các `Class`, không đơn thuần là “cùng package”, “cùng superclass”, “có tên lớp giống nhau” hoặc “đang được một framework load cùng lúc”.

## 2. Những lớp nào thuộc một nest?

Trong mã Java thông thường, các lớp lồng (*nested classes*), gồm `static nested class`, *inner class*, các lớp local/anonymous được biên dịch tương ứng, có thể cùng nest với lớp bao ngoài cùng. Những lớp lồng cấp sâu vẫn có cùng nest host ngoài cùng. Các lớp **top-level độc lập** dù nằm cùng file nguồn hoặc cùng package vẫn không tự trở thành nestmate.

```java
public class House {
    private int secret = 7;

    static class Room {
        private String note = "private-room";

        static class Box {
            private long id = 1;
        }
    }

    class Window {
        private boolean open;
    }
}
```

Trong cấu trúc này, các lớp `House`, `House.Room`, `House.Room.Box` và `House.Window` có thể thuộc cùng nest. Không có “nest con” tách riêng chỉ vì `Box` được khai báo sâu hơn.

**Nest host** là lớp đại diện cho tổ. Trong ví dụ trên, khi biên dịch Java 11+ theo cách thông thường, `House.class` là host; `House.Room.class.getNestHost()` và `House.Room.Box.class.getNestHost()` trả về `House.class`. Chính lớp host cũng là một thành viên của nest.

**Được phép truy cập `private`** là quyền giữa các nestmate, theo cả hai chiều:

- Lớp lồng có thể đọc field `private` hoặc gọi method `private` của lớp bao.
- Lớp bao có thể truy cập member `private` của lớp lồng.
- Hai lớp lồng là “anh em” cùng host có thể truy cập member `private` của nhau, khi có tham chiếu/phương thức phù hợp.

Sự tồn tại của quan hệ nest **không làm member trở thành `public`**, cũng không khiến tất cả lớp ở cùng package được truy cập.

## 3. Ba API Reflection nhận diện nestmate

Các phương thức của `java.lang.Class` có từ Java 11:

| API | Trả về | Cách hiểu |
| --- | --- | --- |
| `clazz.getNestHost()` | `Class<?>` | Lớp host của nest chứa `clazz`; với lớp không tham gia tổ nhiều lớp, thường là chính nó |
| `clazz.getNestMembers()` | `Class<?>[]` | Các lớp thành viên của nest, gồm cả host; không phụ thuộc thứ tự phần tử |
| `a.isNestmateOf(b)` | `boolean` | `true` nếu hai đối tượng `Class` được JVM xác định là thuộc cùng nest |

Ví dụ khi đã có các lớp trong mục 2:

```java
Class<?> host = House.Room.Box.class.getNestHost();
boolean related = House.Room.class.isNestmateOf(House.Window.class);
Class<?>[] members = House.class.getNestMembers();
```

Các dòng này là đoạn **minh họa nằm trong một phương thức Java**, không phải chương trình độc lập. `host` tương ứng `House.class` và `related` là `true` nếu ví dụ được biên dịch/chạy trên JVM hỗ trợ nestmate.

Lưu ý sử dụng:

1. Không dựa vào thứ tự phần tử từ `getNestMembers()`. Nếu cần hiển thị ổn định, lấy tên rồi sắp xếp.
2. `getNestMembers()` nói về nest, **không đồng nghĩa** với `getDeclaredClasses()`: phương thức sau dùng để lấy các lớp/interface là thành viên khai báo trực tiếp, không nhằm liệt kê đầy đủ mọi nestmate.
3. `isNestmateOf` là phép kiểm tra quan hệ giữa **class**, không phải một phép thử toàn bộ các rào cản khi truy cập một field/method cụ thể.
4. Đừng suy luận quan hệ nest bằng cách cắt chuỗi từ dấu `$` trong tên class; tên binary không đủ xác lập quyền truy cập.

## 4. Chương trình Java 17+: kiểm chứng private qua Reflection

Lưu toàn bộ nội dung bên dưới vào **`NestmateDemo.java`** (không tách các lớp ra tệp khác). `NestmateDemo` và hai lớp lồng `MemberReader`, `Peer` thuộc cùng nest; `Outside` là một lớp top-level độc lập, được đặt cùng tệp để chứng minh **cùng package và cùng source file vẫn không đủ**.

```java
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

public class NestmateDemo {
    private String secret = "ALPHA";

    private String combine(String suffix) {
        return secret + suffix;
    }

    private static class MemberReader {
        static void inspect(NestmateDemo obj) throws Exception {
            // Quyền private được cấp vì MemberReader và NestmateDemo cùng nest.
            System.out.println("direct: " + obj.secret);

            // getDeclared* chỉ tìm metadata, không tự tắt access check.
            Field field = NestmateDemo.class.getDeclaredField("secret");
            Method method = NestmateDemo.class.getDeclaredMethod(
                    "combine", String.class);

            // Không gọi setAccessible(true).
            System.out.println("field canAccess: " + field.canAccess(obj));
            System.out.println("field value: " + field.get(obj));
            System.out.println("method canAccess: " + method.canAccess(obj));
            System.out.println("method result: " + method.invoke(obj, "!"));
        }
    }

    private static class Peer {}

    public static void main(String[] args) throws Exception {
        System.out.println("same nest: "
                + MemberReader.class.isNestmateOf(Peer.class));
        System.out.println("outside nest: "
                + MemberReader.class.isNestmateOf(Outside.class));
        System.out.println("peer listed: "
                + Arrays.asList(NestmateDemo.class.getNestMembers())
                        .contains(Peer.class));

        NestmateDemo obj = new NestmateDemo();
        MemberReader.inspect(obj);
        Outside.inspect(obj);
    }
}

// Lớp top-level độc lập: không tự thuộc nest của NestmateDemo.
class Outside {
    static void inspect(NestmateDemo obj) throws Exception {
        Field field = NestmateDemo.class.getDeclaredField("secret");

        System.out.println("outside canAccess: "
                + field.canAccess(obj));
        try {
            field.get(obj);
        } catch (IllegalAccessException ex) {
            System.out.println("outside read: IllegalAccessException");
        }
    }
}
```

Biên dịch và chạy bằng **JDK 17+**, với target mặc định của JDK hiện đại:

```bash
javac NestmateDemo.java
java NestmateDemo
```

Kết quả:

```text
same nest: true
outside nest: false
peer listed: true
direct: ALPHA
field canAccess: true
field value: ALPHA
method canAccess: true
method result: ALPHA!
outside canAccess: false
outside read: IllegalAccessException
```

### Phân tích từng nhóm kết quả

1. `MemberReader.class.isNestmateOf(Peer.class)` là `true` dù hai lớp lồng không nằm trong quan hệ kế thừa: cả hai có chung nest host.
2. `Outside.class` không thuộc nest chỉ vì cùng package/source file; `isNestmateOf` trả về `false`.
3. `getNestMembers()` của `NestmateDemo` chứa `Peer.class`. Không dùng vị trí trong mảng để khẳng định thành viên.
4. Dòng `direct: ALPHA` là truy cập `private` thông qua cú pháp Java thường. Hai dòng `canAccess` và hai kết quả Reflection cho thấy khi **caller là `MemberReader` cùng nest**, kiểm tra truy cập thông thường cũng cho phép Reflection đọc field và gọi method `private` **mà không dùng `setAccessible(true)`**.
5. `Outside` vẫn lấy được đối tượng `Field` bằng `getDeclaredField`: truy vấn metadata thành công nhưng `canAccess(obj)` là `false`. Khi dùng `Field.get`, JVM/Reflection ném `IllegalAccessException` vì caller không có quyền.
6. `Method.invoke` trong ví dụ trả về `ALPHA!`. Đây là lúc method được *gọi thực sự*, khác với việc chỉ tra cứu `Method` bằng `getDeclaredMethod`.

Chương trình cố ý dùng `throws Exception` ở ví dụ tập trung để dễ đọc. Trong ứng dụng, hãy xử lý các ngoại lệ Reflection đúng ngữ nghĩa: `NoSuchFieldException`, `NoSuchMethodException` cho tra cứu; `IllegalAccessException` cho truy cập; `InvocationTargetException` khi method đích ném lỗi; và các trường hợp không đúng kiểu/đối số.

## 5. Có quyền private qua nestmate khác gì setAccessible?

Có ba thao tác độc lập dễ bị nhầm:

**(1) Tìm metadata.** `getDeclaredField("secret")` và `getDeclaredMethod("combine", String.class)` trả về đối tượng mô tả thành viên, kể cả `private`. `getField`/`getMethod` dành cho tập member `public` theo quy tắc tra cứu tương ứng.

**(2) Kiểm tra quyền truy cập thông thường.** `field.canAccess(obj)` hoặc `method.canAccess(obj)` cho biết caller hiện tại có quyền thực hiện thao tác trên đối tượng cụ thể theo trạng thái truy cập Reflection hiện tại hay không. Caller của phép kiểm tra có ý nghĩa: trong ví dụ, chạy trong `MemberReader` trả `true`, còn chạy trong `Outside` trả `false` đối với field `private` đó. Đối số phải hợp lệ: với instance field/method, truyền đúng receiver; với static member, dùng `null`. Tham số sai có thể gây `IllegalArgumentException`.

**(3) Yêu cầu bỏ qua kiểm tra truy cập Java thông thường.** `setAccessible(true)` hoặc `trySetAccessible()` là thao tác riêng, chịu ràng buộc module của JPMS. Dùng chúng để bỏ qua access check không phải điều kiện tiên quyết khi caller có quyền truy cập nhờ quan hệ nest.

| Caller và hành động | Tìm metadata `private` bằng `getDeclared...` | Truy cập thực sự khi chưa bật accessible override |
| --- | --- | --- |
| Một lớp nestmate hợp lệ | Có | Có, nếu kiểu thành viên/receiver/điều kiện khác phù hợp |
| Lớp top-level cùng package nhưng ngoài nest | Có thể | Không có quyền `private` chỉ vì cùng package |
| Một subclass nhưng ngoài nest | Có thể | Kế thừa **không** cấp quyền `private` |
| Thư viện ngoài module | Có thể tra cứu metadata tùy API | Truy cập `private` không tự được cấp; `setAccessible` còn chịu quy tắc `opens` |

**JPMS:** Trên Java 9+, một framework có thể bị chặn khi cố gọi `setAccessible(true)` vào thành viên không public của package trong một module khác chưa `opens` package đó. `trySetAccessible()` có thể trả về `false`; `setAccessible(true)` có thể ném `InaccessibleObjectException`. Đừng mặc định thêm `--add-opens` hoặc bỏ qua lỗi. Thiết kế API public hoặc thiết lập `opens` có chủ đích khi framework thật sự cần deep reflection.

Bản thân nest hợp lệ không được trải tùy tiện giữa các module: các member phải thỏa điều kiện lớp/runtime package/class loader theo quy định JVM. Do đó không thể dùng nestmate để tự động vượt qua ranh giới bảo vệ của một module khác.

## 6. Cơ chế ở bytecode/JVM

### Java 8 và trước đó: compiler thường sinh accessor

Vì JVM cũ chỉ cho phép truy cập thành viên `private` bên trong **chính class sở hữu** (trong khi mã Java hỗ trợ truy cập qua lớp lồng), compiler phải phát sinh cầu nối `synthetic`, ví dụ tên dạng `access$000` hoặc tương tự. Lớp lồng gọi cầu nối thay cho truy cập `private` trực tiếp. Tên/cách sinh cầu nối là **chi tiết của compiler**, không phải API ổn định.

### Java 11+: thông tin nest trong class file

JEP 181 bổ sung thuộc tính class file:

- **`NestHost`:** class member chỉ ra host của mình.
- **`NestMembers`:** host liệt kê các class member cùng nest.

Khi thực hiện kiểm tra quyền truy cập `private`, JVM xét hai class có thuộc cùng nest **được xác minh hợp lệ** hay không. Việc chỉ ghi tên một lớp khác vào class file chưa đủ: JVM còn kiểm tra tính hợp lệ của quan hệ, gồm sự đồng ý từ host và các ràng buộc runtime package/class loader. Dữ liệu class file giả mạo hoặc không nhất quán có thể làm quá trình phân giải/kiểm tra thất bại; không nên tạo quan hệ nest bằng cách sửa tên class thủ công.

**Phiên bản biên dịch rất quan trọng.** Chạy chương trình trên JDK mới **không** tự biến bytecode target Java 8 thành bytecode nestmate: compiler tạo và ghi thông tin nest khi target/class-file version cho phép. Nếu cần hỗ trợ JVM cũ, chọn `--release` phù hợp và hiểu rằng compiler có thể dùng synthetic accessors. Java 8 không nhận diện các thuộc tính nest của class file Java 11.

Có thể kiểm tra class file **sau khi biên dịch**:

```bash
javap -v NestmateDemo
javap -v 'NestmateDemo$MemberReader'
```

Lệnh trên dùng cú pháp trích dẫn single quote phù hợp với shell Unix. Trong PowerShell cũng có thể đặt tên lớp chứa `$` trong single quotes. Hãy tìm thuộc tính `NestMembers` ở host và `NestHost` ở member. Kết quả của `javap -v` rất dài, khác nhau theo compiler/phiên bản và không được dùng như output cố định của bài.

Không nhầm nest metadata với `InnerClasses` hoặc `EnclosingMethod`: những thuộc tính này phục vụ thông tin cấu trúc lớp lồng/ngữ cảnh bao, nhưng không thay thế quy tắc kiểm tra nestmate của JVM.

## 7. Nestmate không đồng nghĩa với kế thừa, package hay lexical access ở mọi ngữ cảnh

### 7.1. Nestmate so với subclass

```java
class Parent {
    private int hidden = 5;
}

class Child extends Parent {
    // Không thể viết: int n = hidden;
}
```

`Child` không có quyền truy cập `private` của `Parent` chỉ vì `extends`. Một subclass bên ngoài nest cũng không thể gọi `getDeclaredField("hidden").get(instance)` mà tự nhiên được phép. Nếu một nested class đồng thời là subclass và nestmate thì quyền `private` bắt nguồn từ **nest**, không phải inheritance.

### 7.2. Cùng package hoặc cùng tệp .java

Hai top-level class cùng package có thể truy cập các thành viên package-private hợp lệ nhưng không được truy cập `private` của nhau. Lớp `Outside` trong chương trình trên cho thấy **cùng tệp nguồn cũng không tạo nest**. Không dùng `Class.getPackageName()` hay tên binary làm phép thử nestmate; dùng `isNestmateOf`.

### 7.3. Nested class không luôn là inner class

- `static nested class` không tự giữ tham chiếu đến instance của lớp ngoài. Nó vẫn có thể là nestmate và truy cập `private` của đối tượng ngoài nếu **đã có tham chiếu**.
- `inner class` (non-static) thường có ngữ cảnh đối tượng bao ngoài, nhưng việc có tham chiếu đó và việc được JVM cấp quyền `private` là hai vấn đề riêng.
- Nest membership không cấp quyền đọc field instance khi không có một receiver thích hợp; `private` access permission và sự tồn tại của đối tượng không phải một.

### 7.4. Reflection metadata và synthetic members

Reflection có thể quan sát những member `synthetic` do compiler sinh. Khi chạy mã biên dịch target cũ, có thể thấy các method `access$...`; khi dùng target Java 11+, chúng thường không còn cần thiết cho truy cập xuyên các nestmate. Đừng dựa vào số lượng hoặc tên synthetic members để kết luận rằng ứng dụng hoạt động giống nhau giữa các compiler.

Các công cụ introspection có thể dùng `Method.isSynthetic()`, `Field.isSynthetic()` và `Method.isBridge()` để phân tích chi tiết. Chỉ lọc chúng nếu quy tắc nghiệp vụ cho phép; đối với chẩn đoán bytecode, thông tin synthetic có thể rất quan trọng.

## 8. Lưu ý khi dùng Reflection và xử lý lỗi

1. **Không dùng `getFields()` để tìm field `private`.** Cần `getDeclaredField()`; phương thức đó tìm metadata được khai báo ngay trong class, không tự đi qua superclass.
2. **Không đồng nhất “tìm thấy” với “được phép dùng”.** `getDeclaredMethod()` tìm metadata, `canAccess()` kiểm tra quyền với caller và receiver hiện tại, `invoke()` mới thực thi. Đừng bỏ qua kiểm tra ngoại lệ.
3. **Không kiểm tra quyền từ sai vị trí.** Kết quả `canAccess` có thể khác giữa code trong nestmate và framework ngoài nest; gọi phép kiểm tra ở nơi khác có thể cho kết luận khác.
4. **Không cố truy cập sâu chỉ vì quan hệ source code.** Chỉ các quan hệ nest hợp lệ ở runtime mới hỗ trợ quyền đặc biệt. Các class loader/module, target bytecode và công cụ instrument bytecode có thể ảnh hưởng đến metadata thực tế.
5. **Không dùng quan hệ nest để vượt JPMS.** `setAccessible` vẫn theo module boundary/`opens` và phải được xem xét riêng.
6. **Không giả định mọi field/method đều là instance member.** `canAccess(null)` thích hợp cho static member, còn instance member cần receiver đúng kiểu.
7. **Không so sánh thứ tự từ `getNestMembers()`.** Nếu hiển thị tài liệu, sắp xếp theo tên; nếu kiểm tra sự hiện diện, dùng `contains` hoặc so sánh đối tượng `Class`.
8. **Không dùng `getNestMembers()` thay cho phân tích mọi lớp có quan hệ kế thừa.** Nest mô tả quyền riêng tư giữa các lớp cùng tổ, không mô tả toàn bộ type hierarchy.

Khi thiết kế framework, bước thực tế thường là: xác định `Class<?>` chủ sở hữu, đọc member qua `getDeclared...`, kiểm tra cần metadata hay cần thực thi, xét quyền truy cập tại điểm thực thi, và chỉ cân nhắc `trySetAccessible()` khi thực sự phải gọi private từ ngoài nest và cấu hình module cho phép.

## 9. Tự kiểm tra và thực hành

**Câu 1.** Vì sao `MemberReader` đọc được `secret` trực tiếp, trong khi `Outside` không thể?
**Đáp án:** `MemberReader` thuộc cùng nest với `NestmateDemo`; `Outside` là top-level class độc lập ngoài nest. Cùng tệp nguồn không tạo quyền `private`.

**Câu 2.** `getDeclaredField("secret")` trong `Outside` có trả về `Field` không? Vì sao đọc field vẫn thất bại?
**Đáp án:** Có, vì tra cứu metadata là bước khác kiểm tra quyền; `Field.get` áp dụng quyền truy cập từ caller ngoài nest và ném `IllegalAccessException`.

**Câu 3.** Có phải mọi subclass đều là nestmate của superclass?
**Đáp án:** Không. Kế thừa và nest là hai quan hệ độc lập.

**Câu 4.** Cách kiểm tra hai class có cùng nest là gì?
**Đáp án:** `A.class.isNestmateOf(B.class)`. Dùng `getNestHost()` để biết host; `getNestMembers()` để xem các member.

**Câu 5.** Vì sao không thể nhìn thấy `NestHost`/`NestMembers` khi biên dịch class theo target Java 8?
**Đáp án:** Cơ chế nestmate nằm trong class file Java 11+, còn target Java 8 dùng cơ chế tương thích cũ khi cần truy cập private giữa lớp lồng.

**Bài thực hành:** Thêm một field `private int value = 42;` vào `Peer` và một phương thức `private static int read(Peer peer)` vào `MemberReader` để truy cập `peer.value`. Kiểm tra có thể gọi từ `main` và so sánh với việc gọi qua Reflection từ `Outside`. Chỉ sử dụng quyền nestmate, không thêm `setAccessible(true)`; giải thích kết quả dựa trên caller, host và quyền `private`.

**Điểm cốt lõi:** Nestmate là quy tắc truy cập `private` ở cấp JVM được hỗ trợ từ Java 11. Reflection giúp **quan sát nest** (`getNestHost`, `getNestMembers`, `isNestmateOf`), **tìm metadata** (`getDeclared...`) và **thực hiện thao tác** (`get`/`invoke`). Ba việc đó có ý nghĩa khác nhau, vì thế hãy kiểm tra đúng quan hệ nest và quyền tại điểm sử dụng member.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="access-control-009">Không xem setAccessible là bảo đảm sửa được mọi field</a>

<details>
<summary>Xem chi tiết</summary>

# Không xem `setAccessible` là bảo đảm sửa được mọi field trong Java Reflection

## 1. Vấn đề: mở kiểm tra truy cập khác với có quyền ghi

Trong Java Reflection, ta có thể nhận được đối tượng `Field` đại diện cho một trường, kể cả trường `private`, qua `getDeclaredField(...)`. Sau đó, `setAccessible(true)` yêu cầu Reflection **bỏ qua một số kiểm tra quyền truy cập của ngôn ngữ Java** khi đọc/ghi trường đó. Điều này **không phải** cam kết rằng `Field.set(...)` sẽ luôn thành công.

Có ít nhất bốn câu hỏi khác nhau:

1. **Tìm được field không?** `getField` chỉ tra cứu thành viên public phù hợp, còn `getDeclaredField` tìm thành viên trực tiếp được khai báo bất kể visibility; không có nghĩa thao tác truy cập hợp lệ.
2. **Có thể tắt kiểm tra truy cập không?** `setAccessible(true)` có thể thành công hoặc bị chặn bởi ranh giới module/runtime. `trySetAccessible()` cho biết thành công bằng kết quả boolean trong trường hợp thường gặp.
3. **Field có được phép ghi không?** `static final`, field `final` trong record và field `final` trong hidden class là những trường không thể đổi bằng `Field.set` dù thao tác mở quyền đã thành công.
4. **Lời gọi ghi có hợp lệ không?** Đối tượng đích, kiểu giá trị, quy tắc chuyển đổi primitive và trạng thái khởi tạo lớp phải phù hợp. Khi một kiểm tra không đạt, API có thể ném exception.

Sự khác nhau này quan trọng với mapper, framework dependency injection, serializer, công cụ migration, unit test và mọi đoạn mã định “sửa field private bằng Reflection”. Hãy luôn xử lý kết quả mở quyền và kết quả ghi như **hai bước độc lập**.

## 2. `setAccessible`, `trySetAccessible` và `canAccess` thực sự nói gì?

| API | Kết quả có ý nghĩa | Điều **không** được suy ra |
| --- | --- | --- |
| `field.setAccessible(true)` | Yêu cầu Reflection bỏ qua kiểm tra truy cập theo phạm vi runtime cho phép; nếu không thể, có thể ném `InaccessibleObjectException`. | Field chắc chắn ghi được hoặc toàn bộ module đã mở. |
| `field.trySetAccessible()` | Trả `true` khi đã bật được quyền bỏ qua kiểm tra; thường trả `false` khi không thể mở do module. Có thể ném `SecurityException` nếu bị từ chối theo cơ chế security áp dụng. | `Field.set` chắc chắn thành công. |
| `field.canAccess(target)` | Kiểm tra liệu **caller tại thời điểm gọi** có thể truy cập phản xạ trên target hợp lệ theo trạng thái hiện tại không. | Field có thể sửa; không thay thế kiểm tra `final`, target và giá trị. |
| `field.get(target)` | Đọc được giá trị khi thỏa điều kiện truy cập và target. | Việc đọc được giá trị đồng nghĩa có quyền ghi. |
| `field.set(target, value)` | Yêu cầu cập nhật giá trị; chỉ thành công khi mọi điều kiện về quyền, tính ghi được và kiểu đều thỏa. | Chỉ cần gọi `setAccessible(true)` trước là đủ. |

`setAccessible(false)` bật lại việc thực thi kiểm tra truy cập cho đối tượng Reflection đó; lời gọi `setAccessible(true)` không sửa modifier của field trong class file, không đổi định nghĩa module và không phá vỡ mọi giới hạn của JVM.

**Một lưu ý thuật ngữ:** “accessible flag” trên đối tượng `Field` biểu thị việc bỏ qua kiểm tra truy cập bằng Reflection. Nó khác với modifier `public`/`private`/`protected`, và hoàn toàn khác với modifier `final`. `canAccess` là phép kiểm tra quyền truy cập trong hoàn cảnh cụ thể, không phải API dự báo khả năng ghi.

## 3. Quyền mở reflection chịu ràng buộc của Java Platform Module System

Từ Java 9, ranh giới module có ý nghĩa đối với deep reflection. Với caller nằm trong class `C` muốn bật quyền truy cập thành viên thuộc class `D`, API nêu những trường hợp có thể bật, trong đó có:

- `C` và `D` thuộc **cùng module**.
- Thành viên và `D` đều `public`; package của `D` được **exports** đến module gọi.
- Thành viên `protected static` trong `D` public; package được exports và `C` là subclass của `D`.
- Package của `D` được **opens** đến module gọi; các package thuộc unnamed module hoặc open module được mở theo ngữ nghĩa tương ứng.

Đối với private, package-private hoặc protected instance member ở **module khác**, `exports` đơn thuần không cấp deep reflection; cần package được `opens` phù hợp. Nếu không, `setAccessible(true)` có thể ném `InaccessibleObjectException` và `trySetAccessible()` thường trả `false`. Các quy tắc chính xác phụ thuộc caller, loại thành viên, vị trí class và cách mở module.

Ví dụ minh họa trong `module-info.java` của module sở hữu model (mảnh cấu hình, không phải chương trình Java độc lập):

~~~java
module app.domain {
    exports app.domain.model;          // Cho truy cập API public.
    opens app.domain.model to app.mapper; // Cho app.mapper deep reflection.
}
~~~

`exports` và `opens` có mục đích khác nhau. Một chương trình trên classpath (unnamed module) chỉ muốn mở package cụ thể cho hoạt động kiểm thử có thể chạy với tùy chọn VM có phạm vi rõ ràng, ví dụ:

~~~bash
java --add-opens java.base/java.lang=ALL-UNNAMED -cp out ReflectiveWriteDemo
~~~

Tùy chọn trên mở việc phản xạ vào `java.lang` cho unnamed modules và có thể khiến `trySetAccessible` của một field private bên trong `java.lang.String` trả `true`, nhưng **không** tạo quyền ghi vào field `final` vốn bị cấm sửa. Hãy hạn chế `--add-opens` cho nhu cầu hợp lệ, ưu tiên cấu hình mở package ở module sở hữu mã; tránh mở rộng toàn bộ module chỉ để thử vận may.

## 4. Trường nào vẫn không thể sửa dù mở quyền thành công?

Javadoc Java 17 định nghĩa nhóm **non-modifiable final fields** mà `setAccessible(true)` chỉ có thể hỗ trợ đọc, không tạo write access:

| Dạng field | Mở truy cập (khi được phép) | `Field.set(...)` |
| --- | --- | --- |
| Field instance bình thường, không `final` | Có thể bật | Có thể ghi nếu target và value hợp lệ. |
| Field `private final` instance của class thường | Có thể bật nếu module cho phép | Trong Java 17, API có thể cho ghi nếu các điều kiện đặc biệt được đáp ứng, nhưng không nên dựa vào kết quả dùng được sau đó. |
| Field `static final` của bất kỳ class/interface | Có thể bật để đọc | **Không có write access** qua `Field.set`. |
| Field `final` do record khai báo | Có thể bật để đọc | **Không có write access** qua `Field.set`. |
| Field `final` trong hidden class | Có thể bật để đọc | **Không có write access** qua `Field.set`. |
| Field private thuộc package không mở ở module khác | Có thể không bật được | Không thể bỏ qua module boundary bằng cách chỉ gọi `setAccessible`. |

Không nhầm `static` với `static final`: field `static` **không final** vẫn có thể thay đổi nếu quy tắc quyền và kiểu cho phép; khi gọi `Field.set` với static field, tham số target không dùng đến và có thể truyền `null`.

**Rủi ro của instance `final` trong class thường:** Theo hợp đồng `Field.set` của Java 17, việc sửa qua Reflection chỉ có ý nghĩa trong một số quy trình khôi phục/deserialization được kiểm soát trước khi đối tượng được công bố. Các lần đọc khác có thể vẫn quan sát giá trị cũ do giả định/optimization dựa vào `final`. Không dùng việc thấy một lần ghi thành công làm bằng chứng invariants của đối tượng đã được cập nhật an toàn. Hành vi và hạn chế với `final` cũng có thể khác ở phiên bản JDK mới; kiểm tra tài liệu của phiên bản thực tế.

## 5. Ví dụ Java 17+ chạy được: bật quyền thành công nhưng ghi thất bại

Chương trình sau tách riêng bước mở quyền, ghi và báo lỗi. Nó kiểm tra field mutable bình thường, `static final`, record component, sai kiểu giá trị và giới hạn module của JDK.

Lưu vào `ReflectiveWriteDemo.java`:

~~~java
import java.lang.reflect.Field;

public class ReflectiveWriteDemo {

    static class MutableModel {
        private String name = "old";
        private int count = 1;
        private static final String CATEGORY = "fixed";
    }

    record Snapshot(String code) {}

    static void attempt(String label, Field field,
                        Object target, Object newValue)
            throws ReflectiveOperationException {
        boolean opened = field.trySetAccessible();
        System.out.println(label + " | opened=" + opened);
        if (!opened) {
            System.out.println(label + " | write=SKIPPED");
            return;
        }

        try {
            field.set(target, newValue);
            System.out.println(label + " | write=OK");
        } catch (IllegalAccessException | IllegalArgumentException ex) {
            System.out.println(label + " | write="
                    + ex.getClass().getSimpleName());
        }
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        MutableModel model = new MutableModel();
        attempt("mutable private",
                MutableModel.class.getDeclaredField("name"),
                model, "updated");
        attempt("static final",
                MutableModel.class.getDeclaredField("CATEGORY"),
                null, "other");
        attempt("record final",
                Snapshot.class.getDeclaredField("code"),
                new Snapshot("original"), "other");
        attempt("wrong value type",
                MutableModel.class.getDeclaredField("count"),
                model, "not a number");

        Field jdkPrivate = String.class.getDeclaredField("value");
        System.out.println("JDK private | opened="
                + jdkPrivate.trySetAccessible());
    }
}
~~~

Biên dịch và chạy trên Java 17+ trong cấu hình JDK thông thường, không dùng `--add-opens`:

~~~bash
javac -encoding UTF-8 ReflectiveWriteDemo.java
java ReflectiveWriteDemo
~~~

Đầu ra trên JDK 17 hoặc 21 với `java.base/java.lang` chưa được mở cho unnamed module:

~~~text
mutable private | opened=true
mutable private | write=OK
static final | opened=true
static final | write=IllegalAccessException
record final | opened=true
record final | write=IllegalAccessException
wrong value type | opened=true
wrong value type | write=IllegalArgumentException
JDK private | opened=false
~~~

Giải thích từng trường hợp:

- `mutable private`: field private của model trong cùng unnamed module cho phép bật bỏ qua access check; target đúng kiểu và giá trị `String` hợp lệ nên ghi được.
- `static final`: mở quyền trả `true`, nhưng `CATEGORY` là field không được sửa. `Field.set(null, ...)` ném `IllegalAccessException`.
- `record final`: `Snapshot.code` do record khai báo là field final không được sửa; cũng thất bại với `IllegalAccessException`.
- `wrong value type`: `count` là `int`. Truyền `String` không được unbox/widen thành `int`; `Field.set` ném `IllegalArgumentException` dù đã mở quyền.
- `JDK private`: `String.value` là field implementation private trong module `java.base`. Trong cấu hình JDK nêu trên, package `java.lang` không mở cho chương trình, nên `trySetAccessible()` trả `false`. Chạy với `--add-opens` tương ứng có thể thay đổi dòng này; ví dụ chỉ minh họa trạng thái mặc định, không viết mã phụ thuộc cấu trúc nội bộ `String`.

Việc lấy `Field` ở đây thành công không chứng minh rằng thao tác đọc hoặc ghi sẽ được phép. Kết quả cụ thể của một field nội bộ JDK phụ thuộc cả phiên bản JVM và cờ dòng lệnh, vì vậy không nên dựa vào những chi tiết đó trong ứng dụng.

## 6. Vì sao `Field.set` còn có thể thất bại?

Sau khi đã bật truy cập, `Field.set(target, value)` vẫn phải tuân các điều kiện của Javadoc:

1. **Sai đối tượng đích:** Với field instance, `target == null` gây `NullPointerException`. Nếu target không là instance của lớp khai báo field (hoặc subtype tương thích), có thể ném `IllegalArgumentException`. Với field static, target được bỏ qua.
2. **Sai kiểu dữ liệu:** Java Reflection cho phép các chuyển đổi tự động có giới hạn: unboxing và widening primitive hợp lệ, không ép kiểu thu hẹp tùy ý; ví dụ truyền `Long` vào field `int` không phải conversion widening. Cần tránh giả định nó hoạt động như converter của JSON mapper.
3. **Chưa có quyền truy cập:** Nếu checks còn được thực thi và field không thể truy cập, `Field.set` có thể ném `IllegalAccessException`. `trySetAccessible()==false` cần được xử lý trước khi gọi.
4. **Field không có write access:** Nhóm non-modifiable final đã nêu khiến `Field.set` ném `IllegalAccessException` dù accessible flag có thể là `true`.
5. **Khởi tạo class static:** Ghi static field có thể kích hoạt class initialization; lỗi khởi tạo có thể dẫn đến `ExceptionInInitializerError`.
6. **Chính sách runtime/security:** `setAccessible(true)` có thể ném `InaccessibleObjectException`; nếu có cơ chế `SecurityManager` và quyền bị từ chối, `SecurityException` cũng có thể phát sinh. Không bắt `Exception` chung rồi coi đó là lỗi field không tồn tại.

Cần phân biệt lỗi **tìm metadata** (`NoSuchFieldException`), lỗi **bật truy cập** (`InaccessibleObjectException` hoặc `SecurityException`), lỗi **không được ghi** (`IllegalAccessException`) và lỗi **tham số** (`IllegalArgumentException` / `NullPointerException`). Mỗi loại báo một nguyên nhân khác nhau.

## 7. Quy trình triển khai an toàn cho công cụ dùng Reflection

Khi một công cụ buộc phải sửa field, nên biến những quyết định ẩn thành điều kiện tường minh:

1. **Ưu tiên API bình thường:** constructor, builder, setter, factory, phương thức nghiệp vụ hoặc tạo object mới. Với record, tạo record mới thay vì cố gắng thay field cũ.
2. **Chọn đúng field:** chỉ ra `declaringClass`, `name`, `type`; dùng `getDeclaredField` khi cần tìm field private trực tiếp; khi duyệt lớp cha cần chính sách rõ để xử lý field trùng tên. Không chọn ngẫu nhiên theo tên duy nhất.
3. **Kiểm tra modifier và cấu trúc:** `Modifier.isFinal(field.getModifiers())`, `Modifier.isStatic(...)`, `field.getDeclaringClass().isRecord()` và phạm vi model. Nếu nghiệp vụ không hỗ trợ `final`, từ chối sớm; đừng dựa vào việc thử ghi.
4. **Kiểm tra ngữ cảnh module:** nếu cần deep reflection, yêu cầu package mở đúng module; không mặc định caller được quyền. Dùng `trySetAccessible` và kiểm tra boolean, hoặc bắt lỗi từ `setAccessible(true)`.
5. **Kiểm tra target/value:** field instance có target phù hợp; static field không cần target; xác định conversion đúng thay vì thử ép tùy tiện.
6. **Ghi trong phạm vi được kiểm soát:** bắt các exception có ý nghĩa, trả thông báo chỉ ra `class.field` và nguyên nhân. Không tự fallback qua `Unsafe`, JNI hay hack modifier để cố viết vào field bị cấm.
7. **Kiểm tra kết quả theo hợp đồng nghiệp vụ:** có thể đọc lại field mutable trong trường hợp phù hợp, nhưng đừng dựa trên đọc lại Reflection để khẳng định việc sửa `final` có hiệu lực với mọi luồng hay mọi code đã biên dịch.

Một hàm tiện ích có thể diễn đạt chính sách cấm ghi vào field final ngay từ đầu, tránh xem `trySetAccessible` là phép thử quyền ghi:

~~~java
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

static void setMutableField(Field field, Object target, Object value)
        throws IllegalAccessException {
    if (Modifier.isFinal(field.getModifiers())) {
        throw new IllegalArgumentException("Refuse final field: "
                + field.getDeclaringClass().getName() + "."
                + field.getName());
    }
    if (!field.trySetAccessible()) {
        throw new IllegalAccessException("Cannot open field: "
                + field.getDeclaringClass().getName() + "."
                + field.getName());
    }
    field.set(target, value);
}
~~~

Đây là **đoạn minh họa** để đặt trong một class thích hợp, không phải source độc lập thứ hai. Ngay cả khi từ chối `final` và đã bật access, `Field.set` vẫn có thể thất bại nếu target/value sai hoặc runtime có điều kiện khác. Nếu công cụ muốn tránh phản xạ private, chỉ hỗ trợ field public hoặc yêu cầu API setter trong module sở hữu model sẽ dễ kiểm chứng hơn.

## 8. Những hiểu nhầm phổ biến và cách sửa

| Nhận định dễ nhầm | Cách hiểu đúng |
| --- | --- |
| “Lấy được `getDeclaredField` là sửa được field.” | Tra cứu metadata không quyết định quyền đọc/ghi. |
| “`setAccessible(true)` luôn mở được private.” | Module/package/security có thể ngăn deep reflection. |
| “`trySetAccessible()==true` chứng minh `Field.set` thành công.” | Chỉ xác nhận trạng thái bỏ qua access check; tính ghi được và target/value vẫn cần kiểm tra. |
| “`final` chỉ là rào cản cú pháp.” | Có nhóm `final` hoàn toàn không có write access và các quy tắc về tính bất biến/bộ nhớ cần tôn trọng. |
| “Mở module bằng `--add-opens` thì sửa được `static final`.” | Mở truy cập không thay đổi quy tắc non-modifiable final. |
| “Đọc lại thấy giá trị mới tức hệ thống đã cập nhật an toàn.” | Đặc biệt với `final`, những đoạn code khác có thể tiếp tục quan sát giá trị cũ; nên dùng mô hình cập nhật object có hợp đồng rõ. |
| “Một lỗi trong `Field.set` nên bị bỏ qua im lặng.” | Phải phân biệt lỗi module, quyền ghi, target, kiểu và initialization để chẩn đoán đúng. |

## 9. Bài tập kiểm chứng

- Bỏ `trySetAccessible` ở ví dụ và thử `Field.set` vào `MutableModel.name` từ một class ở top-level không có quyền private; so sánh `IllegalAccessException`.
- Giữ `trySetAccessible` nhưng thay `newValue` cho `count` bằng `Integer.valueOf(5)`, dự đoán vì sao unboxing từ `Integer` sang `int` hợp lệ.
- Chạy demo với và không với `--add-opens java.base/java.lang=ALL-UNNAMED`; so sánh **chỉ khả năng mở truy cập** vào `String.value`, tránh thay đổi cấu trúc nội bộ của JDK.
- Viết constructor hoặc method `withCode(...)` trả về record `Snapshot` mới để cập nhật logic mà vẫn giữ tính bất biến.
- Tạo một ứng dụng nhiều module riêng và thực nghiệm sự khác nhau giữa `exports` và `opens` đối với truy cập `private`. Giữ phạm vi module mở càng hẹp càng tốt.

## 10. Nguồn tham khảo và nguyên tắc cần nhớ

- [Java SE 17 — `AccessibleObject`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html): ý nghĩa accessible flag, quy tắc JPMS, `trySetAccessible`, nhóm non-modifiable final.
- [Java SE 17 — `Field`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html): điều kiện `Field.set`, lỗi target/type, ghi `final` và cảnh báo về kết quả quan sát.
- [dev.java — Reading and Writing Fields](https://dev.java/learn/api/reflection-method-handles/reflection/fields/): quy trình đọc/ghi field và lưu ý khi thao tác field final.

**Điểm cốt lõi:** `setAccessible` giải quyết **kiểm tra truy cập**, không đại diện cho **quyền ghi không giới hạn**. Mọi đường ghi Reflection cần xác minh ranh giới module, modifier, khả năng ghi thật sự, target, value và xử lý lỗi rõ ràng. Với các giá trị bất biến, việc thiết kế API cập nhật hợp lệ luôn an toàn hơn cố phá bất biến.


</details>

- [Quay lại đầu trang](#back-to-top)
