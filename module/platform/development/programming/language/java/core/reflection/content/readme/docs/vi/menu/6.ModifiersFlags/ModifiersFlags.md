<a id="back-to-top"></a>

# Modifier và cờ

## Menu
- [getModifiers và cách giải mã bằng Modifier](#modifiers-flags-001)
- [public, protected, private và package-private](#modifiers-flags-002)
- [static, final, abstract, synchronized, native và strictfp](#modifiers-flags-003)
- [Phân biệt modifier có ý nghĩa trên lớp, field hoặc executable](#modifiers-flags-004)
- [volatile, transient và thành viên dữ liệu](#modifiers-flags-005)
- [isSynthetic và cấu trúc do trình biên dịch sinh](#modifiers-flags-006)
- [Bridge method và isBridge](#modifiers-flags-007)
- [AccessFlag, accessFlags và khác biệt theo phiên bản JDK](#modifiers-flags-008)
- [Không suy ra toàn bộ cú pháp nguồn từ cờ trong class file](#modifiers-flags-009)

## <a id="modifiers-flags-001">getModifiers và cách giải mã bằng Modifier</a>

<details>
<summary>Xem chi tiết</summary>

# `getModifiers()` và cách giải mã cờ bằng `Modifier` trong Java Reflection

Khi dùng Java Reflection để xem khai báo của một lớp, phương thức, constructor hoặc field, ta có thể cần trả lời: thành phần có `public` không, có `static`/`final` không, field có `volatile` không, method có `synchronized` không? Java không cung cấp một đối tượng riêng cho từng từ khóa này trong API `getModifiers()`. Thay vào đó, nó trả về **một số nguyên `int` chứa tập hợp các bit cờ**. Lớp tiện ích `java.lang.reflect.Modifier` dùng để giải mã các bit ấy.

Điều quan trọng là **cờ mô tả khai báo**, không tự động cho biết liệu một lời gọi hoặc truy cập reflective thực sự được phép, cũng không biểu diễn mọi đặc điểm của class file hay mọi từ khóa Java mới.

## 1. `getModifiers()` trả về gì?

Các API `getModifiers()` xuất hiện ở:

| Đối tượng reflection | Cách lấy cờ | Thành phần được mô tả |
| --- | --- | --- |
| `Class<?>` | `SomeClass.class.getModifiers()` | Class hoặc interface |
| `Field` | `field.getModifiers()` | Trường dữ liệu |
| `Method` | `method.getModifiers()` | Phương thức |
| `Constructor<?>` | `constructor.getModifiers()` | Constructor |
| `Parameter` | `parameter.getModifiers()` | Các cờ của tham số hình thức, ví dụ `final` |

Giá trị trả về là **bitmask**, nghĩa là mỗi bit được dùng để biểu thị sự hiện diện của một cờ. Nếu nhiều modifier cùng tồn tại, chúng được ghép bằng phép OR bitwise `|`; ví dụ:

```java
int flags = Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL;
System.out.println(flags); // 25
```

Trong ví dụ này, `PUBLIC = 0x0001`, `STATIC = 0x0008` và `FINAL = 0x0010`; OR tạo ra `0x0019`, tức `25` trong hệ thập phân.

**Không nên coi số nguyên `25` là hợp đồng định dạng để hiển thị cho người dùng.** Giải mã bằng `Modifier` sẽ vừa rõ ý nghĩa vừa tránh các phép so sánh số nguyên sai.

Ví dụ một khai báo `public static final int LIMIT` có thể được kiểm tra bằng:

```java
Field field = SomeClass.class.getDeclaredField("LIMIT");
int modifiers = field.getModifiers();

boolean publicField = Modifier.isPublic(modifiers);
boolean staticField = Modifier.isStatic(modifiers);
boolean finalField = Modifier.isFinal(modifiers);
String humanReadable = Modifier.toString(modifiers);
```

Đoạn trên là **minh họa API**, với `SomeClass` giả định có field `LIMIT`; chương trình độc lập đầy đủ ở phần 5.

## 2. `Modifier`: kiểm tra từng cờ

`java.lang.reflect.Modifier` là lớp tiện ích chứa hằng bit và các phương thức `static`. Các nhóm thường gặp:

| Nhóm | Hằng cờ | Phương thức kiểm tra | Nơi thường gặp |
| --- | --- | --- | --- |
| Truy cập | `PUBLIC`, `PROTECTED`, `PRIVATE` | `isPublic`, `isProtected`, `isPrivate` | Class/member |
| Thành viên cấp lớp | `STATIC` | `isStatic` | Field, method, nested type |
| Hạn chế thay đổi/kế thừa | `FINAL` | `isFinal` | Class, field, method |
| Yêu cầu cài đặt | `ABSTRACT` | `isAbstract` | Class, method |
| Dấu hiệu interface | `INTERFACE` | `isInterface` | Class biểu diễn interface |
| Đồng bộ | `SYNCHRONIZED` | `isSynchronized` | Method |
| Kết nối native | `NATIVE` | `isNative` | Method |
| Field không tham gia mặc định vào Java object serialization | `TRANSIENT` | `isTransient` | Field |
| Ngữ nghĩa bộ nhớ chia sẻ | `VOLATILE` | `isVolatile` | Field |
| Ngữ nghĩa dấu phẩy động | `STRICT` | `isStrict` | `strictfp` (lịch sử) |

Một số lưu ý về ngữ nghĩa:

- Các cờ truy cập `public`, `protected`, `private` không thể đồng thời có trên cùng một khai báo hợp lệ. **Không có cờ `PACKAGE_PRIVATE`**: không có cả ba cờ trên nghĩa là *package-private* đối với class/member thích hợp.
- `Modifier.isInterface(classFlags)` nhận biết flag interface ở `Class`; thông thường `Class.isInterface()` diễn đạt trực tiếp ý định này hơn.
- `synchronized` thuộc về method; `volatile` và `transient` thuộc về field. Không kiểm tra những cờ này ngoài ngữ cảnh thích hợp.
- `final` không đồng nghĩa với đối tượng bất biến; với `final List<String> list`, tham chiếu không được gán lại bình thường nhưng nội dung `List` có thể thay đổi.
- `volatile` đảm bảo những tính chất visibility và ordering được Java Memory Model quy định; nó **không** làm phép `count++` trở nên atomic.
- Trên Java 17+, `strictfp` không còn thay đổi kết quả biểu thức floating-point theo cách trước kia do ngữ nghĩa floating-point luôn strict; khi đọc dữ liệu cũ vẫn có thể gặp khái niệm này.

## 3. Hai cách giải mã: predicate và toán tử bit

### Cách ưu tiên — `Modifier.isXxx(int)`

```java
int flags = field.getModifiers();

if (Modifier.isPrivate(flags)) {
    System.out.println("Field được khai báo private");
}
if (Modifier.isStatic(flags) && Modifier.isFinal(flags)) {
    System.out.println("Field mang đồng thời static và final");
}
```

Mỗi `isXxx` trả về `true` nếu cờ tương ứng có mặt, bất kể những cờ khác. Đây là cách dễ đọc khi kiểm tra chính sách hoặc tạo metadata.

### Cách bitwise — dùng hằng `Modifier.Xxx`

Các hằng là các bit khác nhau; kiểm tra một cờ:

```java
boolean isStatic = (flags & Modifier.STATIC) != 0;
```

Kiểm tra **đồng thời tất cả** các cờ yêu cầu:

```java
int required = Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL;
boolean containsAll = (flags & required) == required;
```

Kiểm tra **ít nhất một** trong số cờ yêu cầu:

```java
int either = Modifier.PUBLIC | Modifier.PROTECTED;
boolean containsAny = (flags & either) != 0;
```

Tính package-private cho member đang xét:

```java
boolean packagePrivate = !Modifier.isPublic(flags)
        && !Modifier.isProtected(flags)
        && !Modifier.isPrivate(flags);
```

**Sai lầm kinh điển:** viết `flags == Modifier.PUBLIC` để hỏi thành phần có `public` không. Nếu field có `public static final`, kết quả đó sai vì số nguyên còn có các bit `STATIC` và `FINAL`. Tương tự, `(flags & required) != 0` chỉ chứng minh *ít nhất một cờ* xuất hiện, không chứng minh *tất cả*.

Không dùng phép phủ định bit `~flags` rồi giải mã như thể đó là tập các modifier Java hợp lệ; các bit ngoài nhóm được định nghĩa có thể xuất hiện.

## 4. Hiển thị cờ bằng `Modifier.toString()`

`Modifier.toString(int)` chuyển bitmask thành chuỗi các từ khóa có thứ tự, cách nhau bằng dấu cách:

```java
int flags = Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL;
System.out.println(Modifier.toString(flags));
// public static final
```

Nếu không có cờ thuộc tập mà `toString` biểu diễn, chuỗi có thể rỗng. Điều đó **không** nói class/member «không tồn tại» hay «không có đặc điểm nào».

`Modifier.toString()` là bộ **giải mã và định dạng**, không phải bộ xác thực tính hợp lệ của một khai báo Java. Bạn có thể OR những cờ xung đột như `PUBLIC | PRIVATE` và vẫn nhận được chuỗi, dù mã nguồn Java hợp lệ không thể khai báo một member vừa `public` vừa `private`.

Các phương thức `Modifier.classModifiers()`, `interfaceModifiers()`, `fieldModifiers()`, `methodModifiers()`, `constructorModifiers()`, `parameterModifiers()` trả về mask các source modifier áp dụng cho *loại khai báo tương ứng*. Chúng hữu ích khi cần lọc nhóm cờ trước khi hiển thị:

```java
int fieldWords = field.getModifiers() & Modifier.fieldModifiers();
System.out.println(Modifier.toString(fieldWords));
```

Đừng dùng `Modifier.toString()` như bản ghi đầy đủ mọi access flag ở class file: một số flag nội bộ, `synthetic`, `bridge`, `varargs`, `enum`, và thuộc tính mới không nhất thiết được biểu diễn bằng từ khóa của `Modifier`.

## 5. Ví dụ Java 17+ hoàn chỉnh

Lưu nội dung sau vào tệp `ModifierDemo.java`, rồi chạy bằng `javac` và `java`. Chương trình có class lồng nhau, nhiều field/method khác modifier, constructor và một default method trong interface.

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class ModifierDemo {
    public static class Sample {
        public static final int MAX = 10;
        private transient String token;
        protected volatile long revision;

        public Sample() {}

        public synchronized void refresh() {}

        private native void nativeHook();
    }

    public interface Processor {
        default void process() {}
    }

    public static void main(String[] args) throws Exception {
        Class<?> type = Sample.class;
        System.out.println("class: "
                + Modifier.toString(type.getModifiers()));

        Field max = type.getDeclaredField("MAX");
        int maxFlags = max.getModifiers();
        System.out.println("MAX: " + Modifier.toString(maxFlags));
        System.out.println("MAX public/static/final: "
                + (Modifier.isPublic(maxFlags)
                && Modifier.isStatic(maxFlags)
                && Modifier.isFinal(maxFlags)));

        int required = Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL;
        System.out.println("MAX bitmask: "
                + ((maxFlags & required) == required));

        Field token = type.getDeclaredField("token");
        System.out.println("token: "
                + Modifier.toString(token.getModifiers()));

        Field revision = type.getDeclaredField("revision");
        System.out.println("revision: "
                + Modifier.toString(revision.getModifiers()));

        Method refresh = type.getDeclaredMethod("refresh");
        System.out.println("refresh: "
                + Modifier.toString(refresh.getModifiers()));
        System.out.println("refresh synchronized: "
                + Modifier.isSynchronized(refresh.getModifiers()));

        Method hook = type.getDeclaredMethod("nativeHook");
        System.out.println("nativeHook: "
                + Modifier.toString(hook.getModifiers()));

        Constructor<?> ctor = type.getDeclaredConstructor();
        System.out.println("constructor: "
                + Modifier.toString(ctor.getModifiers()));

        Method process = Processor.class.getDeclaredMethod("process");
        System.out.println("default method modifiers: "
                + Modifier.toString(process.getModifiers()));
        System.out.println("isDefault(): " + process.isDefault());

        System.out.println("interface flag: "
                + Modifier.isInterface(Processor.class.getModifiers()));
    }
}
```

Lệnh:

```bash
javac ModifierDemo.java
java ModifierDemo
```

Đầu ra:

```text
class: public static
MAX: public static final
MAX public/static/final: true
MAX bitmask: true
token: private transient
revision: protected volatile
refresh: public synchronized
refresh synchronized: true
nativeHook: private native
constructor: public
default method modifiers: public
isDefault(): true
interface flag: true
```

Giải thích:

1. `Sample` là **member class** khai báo `public static` nên `Class.getModifiers()` phản ánh cả hai cờ. Với top-level class, modifier `static` không thể xuất hiện như trong ví dụ member class.
2. `MAX` mang ba cờ `public static final`; dùng ba predicate hoặc phép AND với mask đều trả kết quả đúng.
3. `token` khai báo `private transient`; `revision` khai báo `protected volatile`. Các cờ phản ánh khai báo chứ chưa chứng minh logic serialization hay thread safety của cả chương trình.
4. `refresh` có cờ `synchronized`; `nativeHook` có cờ `native`, **không cần gọi** phương thức native để đọc metadata. Ví dụ chỉ đọc tên và modifier nên không cần thư viện native.
5. Default method `process()` có `getModifiers()` hiển thị `public`, còn `isDefault() == true`. Từ khóa `default` của interface method **không có hằng `Modifier.DEFAULT`**.

Cả `getDeclaredField`, `getDeclaredMethod` và `getDeclaredConstructor` trong ví dụ chỉ **lấy metadata**. Lấy metadata của thành viên `private` không đồng nghĩa đã được phép đọc/ghi/gọi thành viên đó.

## 6. Phân biệt *declared* và *public/inherited* trước khi đọc modifier

Các phương thức chọn member ảnh hưởng đến việc bạn sẽ giải mã cờ của **thành viên nào**:

| API | Phạm vi tìm kiếm quan trọng |
| --- | --- |
| `getDeclaredFields()` | Các field được khai báo trực tiếp, mọi mức truy cập; không bao gồm field kế thừa |
| `getFields()` | Các field `public` trong phạm vi thành viên có thể thấy, kể cả kế thừa |
| `getDeclaredMethods()` | Các method khai báo trực tiếp, mọi mức truy cập; không bao gồm method kế thừa |
| `getMethods()` | Các method `public` được phản ánh từ class/interface và phần kế thừa |
| `getDeclaredConstructors()` | Các constructor khai báo trong class, mọi mức truy cập |
| `getConstructors()` | Các constructor `public` của class; constructor không được kế thừa |

Vì vậy, nếu viết bộ quét metadata và muốn tìm field `private transient`, thường phải dùng `getDeclaredFields()`. Nếu chỉ quét `getFields()`, bạn sẽ bỏ qua field `private`.

Ví dụ phác thảo một bộ lọc (giả định `type` là `Class<?>` cần quét):

```java
for (Field field : type.getDeclaredFields()) {
    int mod = field.getModifiers();
    if (Modifier.isStatic(mod) && Modifier.isFinal(mod)) {
        System.out.println(field.getName() + ": "
                + Modifier.toString(mod));
    }
}
```

Với API mở rộng, cần cân nhắc field/method do compiler sinh ra và method bridge; đừng suy ra mọi phần tử `getDeclaredXxx()` đều tương ứng một khai báo hiển thị nguyên văn trong mã nguồn.

## 7. Những đặc điểm không nên suy ra chỉ từ `Modifier`

Java có các trạng thái hoặc dấu hiệu cần API chuyên biệt:

| Đặc điểm cần hỏi | API phù hợp | Vì sao `Modifier` chưa đủ |
| --- | --- | --- |
| Default method trong interface | `Method.isDefault()` | Không có `Modifier.DEFAULT` |
| Method bridge do compiler sinh ra | `Method.isBridge()` | Bridge là đặc tính method/class file |
| Phương thức có varargs | `Executable.isVarArgs()` | Không có `Modifier.VARARGS` |
| Member do compiler sinh ra | `Member.isSynthetic()` | Không có predicate `Modifier.isSynthetic` |
| Class là enum | `Class.isEnum()` | Không có `Modifier.ENUM` |
| Class là annotation type | `Class.isAnnotation()` | Không có `Modifier.ANNOTATION` |
| Class là record | `Class.isRecord()` | Không có `Modifier.RECORD` |
| Class/interface là sealed | `Class.isSealed()` | Không có `Modifier.SEALED` |

Chú ý: những API trên không có nghĩa tất cả đều là «modifier» trong cú pháp Java; một số đại diện cho thông tin class file hoặc đặc điểm mô hình ngôn ngữ.

Từ Java 20, API `Class.accessFlags()` và `Member.accessFlags()` cung cấp một tập `AccessFlag` có tính kiểu hóa, thuận tiện để đọc cả access flags ngoài tập modifier cổ điển. Nhưng **trong dự án phải chạy Java 17**, hãy dùng `getModifiers()` + `Modifier` và những phép kiểm tra chuyên biệt có sẵn; không gọi `accessFlags()` nếu cần hỗ trợ Java 17.

`Modifier.STRICT`/`isStrict` cũng đòi hỏi đọc theo phiên bản Java: từ Java 17 biểu thức dấu phẩy động mặc định đã strict, vì vậy sự hiện diện hay vắng mặt của cờ này không phải phép thử «mã có tính toán số thực chính xác hay không».

## 8. Modifier không đồng nghĩa với quyền truy cập có hiệu lực

Đây là hai câu hỏi khác nhau:

1. **Thành viên khai báo gì?** Đọc `field.getModifiers()` rồi `Modifier.isPrivate(...)` hoặc `Modifier.isPublic(...)`.
2. **Caller này có thể truy cập thành viên trên receiver cụ thể không?** Dùng `AccessibleObject.canAccess(receiver)`, cân nhắc quy tắc Java access checks và giới hạn module.

Ví dụ:

```java
Field field = type.getDeclaredField("token");

boolean declaredPrivate =
        Modifier.isPrivate(field.getModifiers());

// 'instance' là đối tượng phù hợp với field instance 'token'.
boolean currentlyAccessible = field.canAccess(instance);
```

`declaredPrivate` không thay đổi khi accessibility checks được suppress. Nếu cố truy cập `private` field từ caller không có quyền, chỉ biết cờ `private` không làm API truy cập thành công. `trySetAccessible()` có thể yêu cầu suppress Java language access checks **khi module system cho phép**; nó có thể trả `false`. `setAccessible(true)` có thể ném `InaccessibleObjectException`. Các thiết lập này cũng không biến `private` thành `public` và không khiến `getModifiers()` trả giá trị khác.

Ngược lại, member có cờ `public` vẫn có thể không truy cập được từ module khác nếu class chứa nó không accessible hoặc package không được module `exports` thích hợp. Vì vậy, **không dùng `Modifier.isPublic(member.getModifiers())` làm quyết định duy nhất rằng reflective invocation sẽ thành công**.

Nếu mục tiêu là kiểm tra khả năng truy cập trước lời gọi, hãy xác định receiver, loại member (static/instance), class khai báo và module/caller context. `canAccess(null)` phù hợp với static member hoặc constructor, còn instance member cần receiver phù hợp; truyền sai receiver sẽ gây lỗi.

## 9. Lỗi phổ biến và cách sửa

### Lỗi A — So sánh nguyên số mask

Sai:

```java
boolean isPublic = field.getModifiers() == Modifier.PUBLIC;
```

Một field `public static` cũng là `public` nhưng biểu thức sai. Đúng: `Modifier.isPublic(field.getModifiers())` hoặc `(mask & Modifier.PUBLIC) != 0`.

### Lỗi B — Nhầm «có bất kỳ cờ» với «có đủ mọi cờ»

Sai khi phải kiểm tra đồng thời `static` và `final`:

```java
boolean staticAndFinal =
        (field.getModifiers() & (Modifier.STATIC | Modifier.FINAL)) != 0;
```

Biểu thức này cũng đúng khi chỉ có `static`. Sửa:

```java
int required = Modifier.STATIC | Modifier.FINAL;
boolean staticAndFinal = (field.getModifiers() & required) == required;
```

### Lỗi C — Suy luận truy cập từ cờ `public`

`public` chỉ là một phần điều kiện của cơ chế Java access control. Với reflection, caller, package, declaring class và Java Platform Module System đều liên quan. Kiểm tra `canAccess` hoặc `trySetAccessible` khi thật sự cần thao tác member.

### Lỗi D — Nghĩ `final`/`volatile` là giải pháp chung cho bất biến hoặc thread safety

`final` bảo vệ ràng buộc nhất định của biến, phương thức hoặc lớp tùy ngữ cảnh. `volatile` quy định visibility/ordering cho field; với thao tác read-modify-write như `count++`, cần đồng bộ hoặc công cụ atomic phù hợp.

### Lỗi E — Hiển thị `Modifier.toString(mask)` như toàn bộ metadata

Một method có thể là `bridge`, `synthetic` hoặc `default` mà chuỗi modifier không ghi đủ. Kiểm tra `isBridge()`, `isSynthetic()`, `isDefault()` tương ứng; với `sealed` và `record` dùng API của `Class`.

### Lỗi F — Chọn sai tập member

Dùng `getMethods()` rồi kết luận class không có method `private` là sai vì hàm đó chỉ trả method `public`. Dùng `getDeclaredMethods()` để quét các method khai báo trực tiếp, và xử lý riêng việc kế thừa khi cần.

## 10. Checklist khi viết công cụ Reflection

Khi viết bộ phân tích class, framework đăng ký method hay công cụ kiểm tra field:

1. Xác định bạn muốn metadata của **lớp, field, method, constructor hay parameter**.
2. Chọn nhóm `getDeclaredXxx` hay `getXxx` phù hợp với phạm vi thành viên.
3. Lưu `int modifiers = member.getModifiers()` và dùng `Modifier.isXxx` cho các điều kiện đơn; dùng bitmask khi cần kiểm tra kết hợp.
4. In `Modifier.toString(modifiers)` để hỗ trợ đọc; đừng dùng chuỗi này thay toàn bộ thông tin API.
5. Bổ sung các API chuyên biệt: `isSynthetic`, `isBridge`, `isDefault`, `isVarArgs`, `isRecord`, `isSealed` tùy mục tiêu và phiên bản Java.
6. Trước khi truy cập/gọi thực tế, thực hiện **access check** theo đúng caller/receiver/module; cờ khai báo chưa phải quyền thao tác.

**Ghi nhớ:** `getModifiers()` trả về **các bit mô tả khai báo**; `Modifier.isXxx()` là cách kiểm tra ý nghĩa của từng bit; `Modifier.toString()` là cách hiển thị quen thuộc. Đọc modifier chính xác đòi hỏi biết **member nào** đang xét và **câu hỏi thuộc metadata hay quyền truy cập thực tế**.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="modifiers-flags-002">public, protected, private và package-private</a>

<details>
<summary>Xem chi tiết</summary>

# public, protected, private và package-private trong Java Reflection

## 1. Mục tiêu và bối cảnh

Khi dùng Java Reflection để khám phá một lớp ở thời điểm chạy, hai câu hỏi cần tách bạch:

1. **Thành viên được khai báo với mức truy cập nào?** Đọc cờ của lớp, phương thức, trường hoặc constructor qua `getModifiers()` và `java.lang.reflect.Modifier`.
2. **Mã hiện tại có thể sử dụng thành viên đó không?** Quy tắc truy cập của Java vẫn được kiểm tra khi gọi phương thức, đọc/ghi trường hoặc tạo đối tượng qua reflection. Việc tìm thấy một thành viên không tự động cấp quyền sử dụng nó.

Ví dụ, `getDeclaredMethod("secretCode")` có thể tìm một phương thức `private`; lời gọi `Method.invoke(...)` từ một lớp bên ngoài vẫn có thể ném `IllegalAccessException`.

Bài này tập trung vào **bốn mức truy cập**, cách đọc chúng qua reflection, khác biệt giữa API lấy thành viên `get...` và `getDeclared...`, cùng giới hạn của việc vượt kiểm tra truy cập. Các cờ khác như `static`, `final`, `abstract` chỉ được nhắc khi cần phân biệt với access modifier.

## 2. Quy tắc truy cập của ngôn ngữ Java

| Mức truy cập | Khai báo | Ai có thể truy cập theo quy tắc ngôn ngữ? |
| --- | --- | --- |
| `public` | Có từ khóa `public` | Mã ở mọi package, miễn chính kiểu khai báo cũng có thể truy cập; module có thể bổ sung ranh giới `exports`. |
| `protected` | Có từ khóa `protected` | Mã trong cùng package; ngoài package, mã của lớp con được phép trong những điều kiện của quy tắc `protected`, bao gồm giới hạn về đối tượng nhận đối với thành viên instance. |
| package-private | **Không** viết từ khóa truy cập | Mã trong cùng package. “Default access” đôi khi được dùng để gọi mức này nhưng không tồn tại từ khóa `package-private` trong Java. |
| `private` | Có từ khóa `private` | Chỉ trong phạm vi kiểu khai báo và các kiểu lồng nhau có quyền truy cập tương ứng theo quy tắc Java. |

Các điểm dễ nhầm:

- **Cùng package không có nghĩa là cùng thư mục bất kỳ:** package xác định bởi khai báo `package`; `com.example` và `com.example.internal` là hai package khác nhau.
- **`protected` không có nghĩa là mọi mã ngoài package đều dùng được:** quan hệ kế thừa và kiểu của biểu thức tham chiếu đến đối tượng đều có thể ảnh hưởng tới quyền truy cập.
- **`private` không có nghĩa reflection không nhìn thấy:** Reflection có thể đọc thông tin khai báo; thao tác sử dụng lại là bước riêng.
- **Mức truy cập của lớp và của thành viên cùng có tác dụng:** một phương thức `public` nằm trong kiểu top-level package-private không tự biến kiểu đó thành công khai đối với mã ở package khác.
- Kiểu top-level chỉ có thể khai báo `public` hoặc package-private. Các kiểu thành viên lồng trong kiểu khác có thể khai báo `protected` hoặc `private`; lớp cục bộ và lớp vô danh có quy tắc riêng.

## 3. Đọc mức truy cập bằng Modifier

Các đối tượng `Class<?>`, `Method`, `Field` và `Constructor<?>` đều cung cấp `getModifiers()`, trả về một **bit mask `int`**. Dùng phương thức kiểm tra bit thay vì so sánh số cờ với một hằng duy nhất:

~~~java
int flags = method.getModifiers();

if (Modifier.isPublic(flags)) {
    // public
} else if (Modifier.isProtected(flags)) {
    // protected
} else if (Modifier.isPrivate(flags)) {
    // private
} else {
    // package-private đối với access modifier của method
}
~~~

Đoạn trên là mã minh họa trong một ngữ cảnh đã có biến `method` và import `java.lang.reflect.Modifier`; chương trình hoàn chỉnh nằm ở phần 5.

- `Modifier.isPublic(flags)`, `isProtected(flags)`, `isPrivate(flags)` kiểm tra đúng từng bit truy cập.
- **Không có `Modifier.isPackagePrivate(...)`.** Với một phương thức/trường/constructor/lớp mà mức truy cập được xét, package-private nghĩa là *không có cả ba bit* `public`, `protected`, `private`.
- `Modifier.toString(flags)` biểu diễn chuỗi từ khóa mà mask mang theo. Nếu chỉ có quyền package-private và không có cờ khác, chuỗi có thể rỗng (`""`). Nếu có cờ `static` hoặc `final`, chuỗi có thể chứa các từ đó; do vậy không kết luận rằng `toString(flags)` rỗng mới là package-private.
- `flags == Modifier.PUBLIC` dễ sai: một phương thức `public static` có cả bit `PUBLIC` và `STATIC`; hãy dùng `Modifier.isPublic(flags)`.

Ví dụ:

~~~java
Method method = SomeClass.class.getDeclaredMethod("run");
System.out.println(Modifier.toString(method.getModifiers()));
~~~

Đây cũng là mã minh họa, giả sử `SomeClass` khai báo `run()` không tham số. Nếu phương thức có khai báo `public static void run()`, chuỗi in ra là `public static`. Không dựa vào thứ tự của các phần tử `getDeclaredMethods()` khi sinh báo cáo; API không cam kết thứ tự.

## 4. Tìm thành viên khác với được quyền truy cập

| API của `Class` | Thành viên có thể tìm thấy | Phạm vi kế thừa |
| --- | --- | --- |
| `getMethod(name, parameterTypes...)` / `getMethods()` | Phương thức `public` | Bao gồm các phương thức public được kế thừa theo quy tắc của API |
| `getDeclaredMethod(name, parameterTypes...)` / `getDeclaredMethods()` | Mọi mức truy cập, miễn khai báo trong chính lớp/interface đó | Không tự tìm trong lớp cha |
| `getField(name)` / `getFields()` | Trường `public` | Có tính đến các trường public được kế thừa |
| `getDeclaredField(name)` / `getDeclaredFields()` | Mọi mức truy cập của trường khai báo trực tiếp | Không tìm trong lớp cha |
| `getConstructor(parameterTypes...)` / `getConstructors()` | Constructor `public` | Constructor không được kế thừa |
| `getDeclaredConstructor(parameterTypes...)` / `getDeclaredConstructors()` | Constructor khai báo trực tiếp, mọi mức truy cập | Constructor không được kế thừa |

Ví dụ: với một phương thức `private void check()`, `getDeclaredMethod("check")` có thể trả về `Method`, trong khi `getMethod("check")` ném `NoSuchMethodException`. Ngoại lệ *không tìm thấy* (`NoSuchMethodException`) khác với ngoại lệ *không có quyền gọi* (`IllegalAccessException`).

Chữ ký phương thức còn chứa danh sách kiểu tham số: nếu có `void check(String value)`, cần gọi `getDeclaredMethod("check", String.class)`; chỉ truyền `"check"` sẽ không tìm thấy phương thức một tham số. Với phương thức overload, luôn truyền đúng kiểu tham số.

Sau khi đã tìm được `Method`, `Field` hoặc `Constructor`, Java vẫn kiểm tra tính hợp lệ khi `invoke(...)`, `get(...)`, `set(...)` hoặc `newInstance(...)`. Đọc metadata không đồng nghĩa với quyền gọi/đọc/ghi.

## 5. Chương trình Java 17 chạy được: đọc và thử truy cập cả bốn mức

Ta đặt lớp chứa thành viên trong package `lesson.api`, còn mã reflection trong package `lesson.client` để thấy rõ việc vượt ranh giới package.

**Tệp 1 — `src/lesson/api/MemberBox.java`:**

~~~java
package lesson.api;

public class MemberBox {
    public String publicCode() { return "public value"; }
    protected String protectedCode() { return "protected value"; }
    String packageCode() { return "package value"; }
    private String secretCode() { return "private value"; }
}
~~~

**Tệp 2 — `src/lesson/client/ReflectAccess.java`:**

~~~java
package lesson.client;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import lesson.api.MemberBox;

public class ReflectAccess {
    public static void main(String[] args) throws Exception {
        Class<MemberBox> type = MemberBox.class;
        for (String name : new String[] {
                "publicCode", "protectedCode", "packageCode", "secretCode" }) {
            Method method = type.getDeclaredMethod(name);
            System.out.println(name + ": " + visibility(method.getModifiers()));
        }

        System.out.println("getMethod(publicCode): "
                + type.getMethod("publicCode").getName());
        try {
            type.getMethod("secretCode");
        } catch (NoSuchMethodException ex) {
            System.out.println("getMethod(secretCode): not found");
        }

        MemberBox box = new MemberBox();
        Method secret = type.getDeclaredMethod("secretCode");
        try {
            secret.invoke(box);
        } catch (IllegalAccessException ex) {
            System.out.println("invoke before override: IllegalAccessException");
        }
        boolean opened = secret.trySetAccessible();
        System.out.println("trySetAccessible(secretCode): " + opened);
        if (opened) {
            System.out.println("invoke after override: " + secret.invoke(box));
        }
    }

    private static String visibility(int flags) {
        if (Modifier.isPublic(flags)) return "public";
        if (Modifier.isProtected(flags)) return "protected";
        if (Modifier.isPrivate(flags)) return "private";
        return "package-private";
    }
}
~~~

**Biên dịch và chạy từ thư mục chứa `src/` (Java 17 trở lên, dùng classpath):**

~~~bash
mkdir -p out
javac --release 17 -d out src/lesson/api/MemberBox.java src/lesson/client/ReflectAccess.java
java -cp out lesson.client.ReflectAccess
~~~

Trên PowerShell, có thể thay `mkdir -p out` bằng `New-Item -ItemType Directory -Force out`. Thư mục `out` chứa các file `.class` đúng cấu trúc package.

**Stdout chính xác trong ví dụ classpath trên:**

~~~text
publicCode: public
protectedCode: protected
packageCode: package-private
secretCode: private
getMethod(publicCode): publicCode
getMethod(secretCode): not found
invoke before override: IllegalAccessException
trySetAccessible(secretCode): true
invoke after override: private value
~~~

**Giải thích kết quả:**

1. `getDeclaredMethod` truy xuất được cả bốn khai báo, nên ta đọc được bốn mức bằng `getModifiers()`.
2. `getMethod("publicCode")` tìm thành viên công khai; `getMethod("secretCode")` không tìm thành viên private và ném `NoSuchMethodException`.
3. `ReflectAccess` ở package khác và không thuộc phạm vi được phép truy cập `private secretCode()`; lời gọi `invoke` ban đầu ném `IllegalAccessException`.
4. `trySetAccessible()` trả về `true` ở đây vì cả hai lớp là mã trên classpath, thuộc unnamed module, và không có rào cản module ngăn truy cập sâu. Khi thành công, cùng đối tượng `Method` có thể được gọi bằng `invoke`. Điều này **không bảo đảm** sẽ thành công với mọi lớp trong named module hoặc thư viện khác.

Nếu muốn thử phương thức `protectedCode` hoặc `packageCode`, đổi tên khi lấy `getDeclaredMethod`. Từ `lesson.client.ReflectAccess`, thao tác `invoke` thông thường cũng bị kiểm tra truy cập; có metadata không làm mã trở thành lớp con hay cùng package.

## 6. `canAccess`, `trySetAccessible` và hệ thống module

Java cung cấp ba thao tác thường gặp trên đối tượng reflective:

- `method.canAccess(receiver)`: hỏi tại thời điểm đó caller hiện tại có thể truy cập bằng đối tượng `Method` này không; dùng `receiver` là instance đúng loại cho phương thức instance, hoặc `null` cho phương thức static. Đối với trường hoặc constructor, tham số nhận cũng phải đúng theo hợp đồng API tương ứng.
- `method.trySetAccessible()`: **thử** tắt việc kiểm tra quyền truy cập của ngôn ngữ Java đối với đối tượng reflection; trả về `true` nếu thành công và `false` khi không thể bật quyền này vì giới hạn truy cập. Trong một số tình huống có thể xuất hiện ngoại lệ bảo mật.
- `method.setAccessible(true)`: yêu cầu tắt kiểm tra truy cập; nếu rào cản module không cho phép, có thể ném `java.lang.reflect.InaccessibleObjectException`.

Từ Java 9, JPMS (Java Platform Module System) phân biệt:

| Khai báo module | Ý nghĩa thực tế |
| --- | --- |
| `exports p;` | Cho phép module khác sử dụng các kiểu `public` và thành viên `public` có thể truy cập của package `p` qua API thông thường; không mặc nhiên cho phép reflection mở thành viên private. |
| `opens p;` | Cho phép truy cập sâu bằng reflection vào package `p` theo cơ chế mở package, bao gồm thành viên không public khi thao tác hợp lệ. |
| Không `exports` hay `opens` | Mã ở module khác thường bị hạn chế hơn, kể cả khi reflection thấy tên thành viên. |

Có thể mở có mục tiêu một package khi chạy để chẩn đoán, ví dụ tùy cấu hình: `--add-opens java.base/java.lang=ALL-UNNAMED`. Đây là một cơ chế cấp phép mạnh; không nên thêm rộng khắp hoặc dựa vào nó như thiết kế API bình thường. Với mã ở các named module khác nhau, kết quả `trySetAccessible()` phụ thuộc quan hệ module, package và quyền mở thực tế.

**Không nhầm `AccessibleObject` với thay đổi khai báo:** gọi `trySetAccessible` không biến phương thức private thành public trong class file. `method.getModifiers()` vẫn báo `private`. Nó chỉ ảnh hưởng việc kiểm tra quyền trên đối tượng reflection được điều chỉnh trong phạm vi được phép. Các giới hạn khác như tính bất biến của một số trường `final` vẫn tồn tại.

## 7. Sai lầm thường gặp và cách xử lý

| Hiểu nhầm / lỗi | Vì sao sai | Cách làm |
| --- | --- | --- |
| `getMethods()` trả về mọi phương thức | Nó chỉ chọn phương thức public theo quy tắc API | Dùng `getDeclaredMethods()` khi cần xem các khai báo không public, và duyệt lớp cha riêng nếu cần |
| `getDeclaredMethods()` chứa cả khai báo lớp cha | Nó chỉ trả về phương thức khai báo trong chính lớp/interface đang hỏi | Duyệt `getSuperclass()` và `getDeclaredMethods()` từng cấp nếu đó là mục tiêu |
| Tìm được `Method` tức là gọi được | Khám phá metadata và quyền thực thi là hai bước khác nhau | Phân biệt `NoSuchMethodException`, `IllegalAccessException`, `InvocationTargetException` |
| `Modifier.toString(flags).isEmpty()` là cách duy nhất nhận package-private | Một khai báo package-private vẫn có thể mang cờ như `static` hoặc `final` | Kiểm tra cả ba bit `public/protected/private` đều không có |
| `flags == Modifier.PUBLIC` nhận diện mọi public method | Một method có thể đồng thời là `public static` hoặc `public final` | Dùng `Modifier.isPublic(flags)` |
| `protected` luôn truy cập được từ package khác nếu có subclass ở đâu đó | Quy tắc kiểm tra dựa trên vị trí mã truy cập và đối tượng nhận, không chỉ sự tồn tại của subclass | Kiểm tra ngữ cảnh package/kế thừa của chính caller |
| `setAccessible(true)` luôn thành công | Module có thể không mở package cho caller | Ưu tiên API public; nếu cần, thiết kế `opens` có chủ đích và xử lý thất bại |
| `private` là “bảo mật dữ liệu tuyệt đối” | Modifier chủ yếu là ranh giới đóng gói API ở cấp ngôn ngữ, không phải cơ chế bảo mật mọi môi trường chạy | Dùng các cơ chế bảo mật phù hợp cho dữ liệu nhạy cảm, đồng thời giữ đóng gói tốt |

Khi tìm phương thức trên lớp kế thừa, hãy phân biệt `getMethod` có thể tìm public method kế thừa với `getDeclaredMethod` chỉ tìm khai báo trực tiếp. Khi in danh sách `getDeclaredMethods()`, sắp xếp theo tên/chữ ký nếu cần đầu ra ổn định vì thứ tự mảng reflection không được bảo đảm.

## 8. Bài tập tự kiểm tra

1. Thêm `public static String sharedCode()` vào `MemberBox`. Dự đoán kết quả `Modifier.isPublic(method.getModifiers())` và `Modifier.toString(method.getModifiers())`. **Đáp án:** `true`; chuỗi chứa `public static`.
2. Thêm `private MemberBox(int seed)` bên cạnh constructor không tham số được khai báo tường minh là public. Hỏi `getConstructor(int.class)` và `getDeclaredConstructor(int.class)` khác nhau ra sao? **Đáp án:** API thứ nhất ném `NoSuchMethodException`, API thứ hai tìm được constructor private nhưng việc gọi còn phải kiểm tra quyền truy cập.
3. Chuyển `ReflectAccess` vào package `lesson.api`. Với `packageCode()`, thao tác `invoke` có được phép không? **Đáp án:** được phép nếu lớp gọi đúng cùng package và nhận đúng đối tượng; với `private`, ranh giới private vẫn còn.
4. Chuyển `MemberBox` sang named module có package không mở. Có thể kết luận `trySetAccessible()` chắc chắn trả về `true` như ví dụ classpath không? **Đáp án:** không; cần xét package được mở cho module của caller hay chưa.

**Ghi nhớ:** Reflection có thể *quan sát* các mức truy cập qua `getModifiers()`, nhưng thao tác thực tế vẫn phải vượt qua phép kiểm tra quyền của Java và ranh giới module. Hãy chọn `getDeclared...` để khám phá khai báo nội bộ, `get...` để tìm thành viên public phù hợp, và chỉ yêu cầu truy cập sâu khi thực sự cần thiết.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="modifiers-flags-003">static, final, abstract, synchronized, native và strictfp</a>

<details>
<summary>Xem chi tiết</summary>

# Reflection: đọc các cờ static, final, abstract, synchronized, native và strictfp

Trong Java, các từ khóa `static`, `final`, `abstract`, `synchronized`, `native` và `strictfp` biểu đạt đặc tính của lớp, phương thức hoặc trường dữ liệu. Khi sử dụng Reflection, ta không tìm các từ khóa trong chuỗi source code; ta đọc **modifier bitmask** từ đối tượng `Class`, `Method`, `Field` hoặc `Constructor`, rồi dùng `java.lang.reflect.Modifier` để kiểm tra từng cờ.

Bài này tập trung vào câu hỏi: **modifier đang hiện diện trên phần tử nào, mang ý nghĩa gì và Reflection kiểm tra nó như thế nào?** Cần phân biệt đặc tính được ghi trong metadata với hiệu ứng lúc JVM chạy; đặc biệt, `strictfp` đã thay đổi ý nghĩa trong Java 17+.

## 1. Modifier bitmask trong Reflection hoạt động ra sao?

Những API tiêu biểu:

```java
Class<?> type = SomeClass.class;
int classFlags = type.getModifiers();

java.lang.reflect.Method method =
        type.getDeclaredMethod("someMethod");
int methodFlags = method.getModifiers();

java.lang.reflect.Field field =
        type.getDeclaredField("someField");
int fieldFlags = field.getModifiers();
```

Đây là đoạn minh họa: `SomeClass`, `someMethod` và `someField` phải được khai báo trong chương trình thực tế. Cả ba `getModifiers()` trả về kiểu `int`, nhưng mỗi giá trị thuộc một **ngữ cảnh phần tử** khác nhau.

Mỗi hằng của `Modifier` đại diện cho một bit riêng, ví dụ `Modifier.STATIC`, `Modifier.FINAL`, `Modifier.ABSTRACT`, `Modifier.SYNCHRONIZED`, `Modifier.NATIVE` và `Modifier.STRICT`. Một phần tử có thể mang nhiều bit, kết hợp bằng toán tử OR bit `|`.

```java
int flags = Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL;

boolean isStatic = Modifier.isStatic(flags);   // true
boolean isFinal = Modifier.isFinal(flags);     // true
boolean isAbstract = Modifier.isAbstract(flags); // false

boolean sameStaticCheck = (flags & Modifier.STATIC) != 0; // true
String labels = Modifier.toString(flags); // "public static final"
```

Đoạn minh họa cần `import java.lang.reflect.Modifier;`. `isXxx(flags)` giúp mã rõ ý nghĩa hơn biểu thức `&`; phép `&` vẫn hữu ích khi cần kết hợp hoặc lọc bitmask.

Điểm quan trọng:

- **`getModifiers()` không trả về một tên modifier duy nhất.** Nó trả về tập cờ mã hóa trong số nguyên.
- **`Modifier.toString(flags)` dùng để trình bày**, không phải kiểm tra quy tắc hợp lệ của khai báo. Nó vẫn có thể in chuỗi từ một bitmask tổng hợp vô lý.
- **Ngữ cảnh quyết định ý nghĩa bit.** Một số bit của class file có ý nghĩa khác nhau ở loại phần tử khác nhau (ví dụ cờ biểu diễn `synchronized` của method khác cờ liên quan lớp). Không đưa modifier của một loại phần tử vào hàm kiểm tra của loại khác rồi suy diễn kết quả.
- Khi cần lọc theo tập modifier phù hợp với loại phần tử, có thể dùng `Modifier.classModifiers()`, `interfaceModifiers()`, `fieldModifiers()`, `methodModifiers()` hoặc `constructorModifiers()`. Đây là **mask cho modifier của ngôn ngữ nguồn**, không phải bộ kiểm tra tự động tất cả quy tắc kết hợp và mọi cờ kỹ thuật của class file.

### API lấy metadata trước khi xét modifier

| Nguồn Reflection | Modifier được đọc | Lưu ý |
| --- | --- | --- |
| `Class.getModifiers()` | Modifier của kiểu (class/interface/nested class) | `static` có ý nghĩa đối với member nested class; top-level class không được khai báo `static` |
| `Method.getModifiers()` | Modifier của một phương thức cụ thể | Dùng để kiểm tra `static`, `final`, `abstract`, `synchronized`, `native`; `strictfp` cần hiểu phiên bản class file |
| `Field.getModifiers()` | Modifier của một trường cụ thể | Thường quan tâm `static`, `final` cùng các cờ khác như `volatile` hoặc `transient` |
| `Constructor.getModifiers()` | Modifier của constructor | Chủ yếu kiểm tra mức truy cập; constructor không thể `static`, `final`, `abstract`, `synchronized` hoặc `native` |

Chọn `getDeclaredMethod(...)`/`getDeclaredField(...)` khi muốn thành viên **khai báo trực tiếp tại lớp**, bao gồm thành viên không public; chọn `getMethod(...)`/`getField(...)` nếu muốn tìm thành viên **public** theo quy tắc kế thừa. Việc có metadata không tự cấp quyền gọi hoặc truy cập thành viên.

## 2. Sáu modifier cần hiểu

| Modifier | Áp dụng thường gặp | Kiểm tra bằng Reflection | Ý nghĩa |
| --- | --- | --- | --- |
| `static` | Field, method, member nested class | `Modifier.isStatic(flags)` | Thành viên gắn với lớp; với nested class, không cần đối tượng của enclosing class |
| `final` | Class, method, field | `Modifier.isFinal(flags)` | Không cho kế thừa class, không cho override method, hoặc không cho gán lại biến field sau khi đã được khởi tạo phù hợp |
| `abstract` | Class, method | `Modifier.isAbstract(flags)` | Class có thể chưa hoàn chỉnh; abstract method khai báo hợp đồng mà không có thân |
| `synchronized` | Method | `Modifier.isSynchronized(flags)` | Khi gọi method, JVM thực hiện đồng bộ monitor trong phạm vi toàn bộ method |
| `native` | Method | `Modifier.isNative(flags)` | Method không có thân mã Java, implementation gắn với native code |
| `strictfp` | Class, interface, method trong mã nguồn lịch sử | `Modifier.isStrict(flags)` | Cờ lịch sử liên quan số thực; từ Java 17 không còn tác dụng điều chỉnh tính toán và không được lưu như trước trong class file mới |

Cột “áp dụng” mô tả phạm vi cú pháp thông dụng; không phải mọi tổ hợp trong bảng đều hợp lệ. Ví dụ, method `abstract` không thể đồng thời là `static`, `final`, `synchronized` hoặc `native`.

## 3. static: thành viên lớp và lớp lồng tĩnh

**`static` field** có một giá trị cấp lớp (cho mỗi `Class` được nạp tương ứng), không phải một bản riêng trên mỗi instance. **`static` method** được gọi theo tên lớp và không có receiver `this`; nó bị **hiding** khi lớp con khai báo method static trùng chữ ký, không override đa hình như instance method.

```java
class Counter {
    static int total;
    static void reset() {
        total = 0;
    }
}
```

Kiểm tra:

```java
java.lang.reflect.Field total =
        Counter.class.getDeclaredField("total");
java.lang.reflect.Method reset =
        Counter.class.getDeclaredMethod("reset");

System.out.println(Modifier.isStatic(total.getModifiers())); // true
System.out.println(Modifier.isStatic(reset.getModifiers())); // true
```

Đây là đoạn minh họa phụ thuộc lớp `Counter` ở trên và khai báo `import java.lang.reflect.Modifier;`; mã gọi cần xử lý hoặc khai báo các checked exception từ `getDeclaredField`/`getDeclaredMethod`.

Nếu class là **member nested class** khai báo `static`:

```java
class Envelope {
    static class Item {
    }
}
```

thì `Modifier.isStatic(Envelope.Item.class.getModifiers())` trả về `true`. Một lớp top-level như `Envelope` không mang modifier `static`. Interface lồng nhau có thể **ngầm định static**, do đó đừng chỉ tìm chữ `static` trong source để suy ra giá trị Reflection. Ngược lại, `static` initializer block không được đại diện bằng đối tượng `Method` thông thường mà ứng dụng tìm với `getDeclaredMethod("<clinit>")`.

Đối với `Method.invoke`, method static không cần instance receiver: có thể truyền `null` cho tham số đối tượng; với instance method, cần receiver phù hợp và có quyền truy cập.

## 4. final: ý nghĩa khác nhau trên class, method và field

**Trên class:** `final class X` không thể có subclass trong Java. **Trên instance method:** `final` cấm lớp con override phương thức đó. **Trên field:** giá trị field không được gán lại sau khi hoàn tất khởi tạo theo quy tắc ngôn ngữ Java.

```java
final class ServiceConfig {
    static final int RETRY_LIMIT = 3;

    final String identity() {
        return "service";
    }
}
```

Với Reflection, cùng một hàm `Modifier.isFinal` kiểm tra cờ `final` ở ba vị trí:

```java
Class<?> t = ServiceConfig.class;
boolean finalClass = Modifier.isFinal(t.getModifiers());

java.lang.reflect.Method m =
        t.getDeclaredMethod("identity");
boolean finalMethod = Modifier.isFinal(m.getModifiers());

java.lang.reflect.Field f =
        t.getDeclaredField("RETRY_LIMIT");
boolean finalField = Modifier.isFinal(f.getModifiers());
```

Đây là đoạn minh họa cần import và xử lý exception tương tự mục 3.

**Không suy ra “đối tượng bất biến” chỉ từ `final`.** Một `final List<String>` không cho gán lại tham chiếu field theo Java thông thường, nhưng nội dung `List` vẫn có thể thay đổi. `static final` không tự động biến mọi field thành compile-time constant; việc có thể inline phụ thuộc loại và biểu thức khởi tạo hằng.

Với Reflection, `final` là dấu hiệu cần tôn trọng trong mô hình dữ liệu; không nên thiết kế logic dựa vào việc tự ý sửa `final` field bằng truy cập phản chiếu. Quy tắc truy cập, inlining, module và JDK hiện đại có thể làm hành vi không khả dụng hoặc khó dự đoán.

## 5. abstract: hợp đồng chưa có thân và kiểu chưa thể new trực tiếp

Một class `abstract` không thể được khởi tạo trực tiếp bằng `new`; nó có thể có cả method abstract lẫn method đã cài đặt. Một method `abstract` khai báo tên, tham số, kiểu trả về, ngoại lệ theo chữ ký, nhưng không cung cấp thân Java.

```java
abstract class Shape {
    abstract double area();

    String category() {
        return "shape";
    }
}
```

`Modifier.isAbstract(Shape.class.getModifiers())` trả về `true`, còn `Modifier.isAbstract(Shape.class.getDeclaredMethod("area").getModifiers())` cũng trả về `true`.

Một số điểm kiểm tra:

- Một class có thể được đánh dấu `abstract` dù không khai báo method abstract nào; `Modifier.isAbstract(classFlags)` và “có method abstract trực tiếp” là hai câu hỏi khác nhau.
- Method abstract không có thân; không thể gọi trực tiếp phương thức abstract khi không có concrete implementation trên receiver. Khi một lớp con cài đặt method, một `Method` tìm từ class cha có thể được invoke trên instance lớp con phù hợp và dispatch đến implementation.
- Một method không thể vừa `abstract` vừa `private`, `static`, `final`, `synchronized`, `native` hoặc `strictfp`.
- Interface vốn là kiểu abstract, nhưng **không phải mọi method của interface đều abstract**: interface có thể có `default`, `static` và `private` methods. Hãy đọc modifier **từng method** thay vì suy từ `isInterface()` của lớp.

## 6. synchronized: chỉ phản ánh khai báo đồng bộ trên method

**Instance `synchronized` method** khóa monitor của receiver (`this`) trong khi method chạy. **Static `synchronized` method** khóa monitor gắn với đối tượng `Class` khai báo phương thức. Đây là hai monitor khác nhau; synchronized chỉ có ý nghĩa nếu các luồng tranh chấp cùng monitor thích hợp.

```java
class BankAccount {
    private int balance;

    public synchronized void deposit(int amount) {
        balance += amount;
    }

    public static synchronized void audit() {
        // Đồng bộ trên BankAccount.class
    }

    public void withdraw(int amount) {
        synchronized (this) {
            balance -= amount;
        }
    }
}
```

Reflection:

```java
java.lang.reflect.Method deposit =
        BankAccount.class.getDeclaredMethod("deposit", int.class);
java.lang.reflect.Method audit =
        BankAccount.class.getDeclaredMethod("audit");
java.lang.reflect.Method withdraw =
        BankAccount.class.getDeclaredMethod("withdraw", int.class);

System.out.println(Modifier.isSynchronized(deposit.getModifiers())); // true
System.out.println(Modifier.isSynchronized(audit.getModifiers()));   // true
System.out.println(Modifier.isSynchronized(withdraw.getModifiers())); // false
```

Đây là đoạn minh họa cần import và xử lý checked exception từ tra cứu. Dòng cuối `false` **không có nghĩa là `withdraw` không đồng bộ gì**: synchronized block trong thân method không khiến method mang cờ `ACC_SYNCHRONIZED`. Reflection modifier chỉ nắm metadata khai báo method, không phân tích mọi lệnh bytecode hay chính sách khóa.

Một method `synchronized` không tự làm mọi thao tác xung quanh thành thread-safe: tính đúng đắn còn phụ thuộc các đường truy cập khác, invariant và việc dùng cùng monitor. Khi `Method.invoke` chạy một synchronized method, lời gọi vẫn chịu semantics đồng bộ của method đó.

## 7. native: phương thức không có phần thân Java

Một method `native` thường là điểm nối tới phần cài đặt ngoài Java, ví dụ qua Java Native Interface (JNI). Nó chỉ có khai báo method, không có thân Java:

```java
class NativeBridge {
    public static native int calculate(int input);
}
```

Sau khi lấy `Method`:

```java
java.lang.reflect.Method method =
        NativeBridge.class.getDeclaredMethod("calculate", int.class);

boolean nativeMethod = Modifier.isNative(method.getModifiers()); // true
boolean staticMethod = Modifier.isStatic(method.getModifiers()); // true
```

Đây là đoạn minh họa cần import và xử lý checked exception từ tra cứu. Chỉ **đọc metadata** của `NativeBridge.calculate` không buộc native implementation phải có sẵn. Nhưng **invoke method native mà chưa liên kết được implementation** có thể dẫn đến `UnsatisfiedLinkError` (khi đi qua Reflection, lỗi từ method có thể được bọc trong `InvocationTargetException`).

`native` không có nghĩa method chắc chắn nhanh hơn Java, an toàn hơn hay tự được phép truy cập mọi tài nguyên. Nó chỉ mô tả hình thức implementation. Các giới hạn tải thư viện, liên kết native và cấu hình JDK vẫn cần được xử lý độc lập.

## 8. strictfp: di sản cũ và thay đổi quan trọng từ Java 17

Trước Java 17, `strictfp` giúp buộc các phép tính `float`/`double` trong phạm vi nhất định tuân theo ngữ nghĩa floating-point strict. Java 17 khôi phục ngữ nghĩa **always-strict** theo JEP 306: phép toán floating-point có cùng quy tắc strict bất kể từ khóa `strictfp` có xuất hiện hay không.

Hệ quả khi đọc modifier bằng Reflection:

1. `Modifier.STRICT` và `Modifier.isStrict(int)` vẫn là API giải mã **bit**. Nếu tự xây bitmask `Modifier.PUBLIC | Modifier.STRICT`, `isStrict` sẽ trả về `true`, vì bit có mặt.
2. **Điều đó không có nghĩa method được biên dịch bởi JDK 17+ sẽ mang cờ strict.** Theo JVMS 17, bit `ACC_STRICT` chỉ được diễn giải là `strictfp` trong class file có major version từ **46 đến 60**. Với class file major **61+** (Java 17+), bit đó không còn ý nghĩa `ACC_STRICT`; mã biên dịch mới không cần ghi nó.
3. Vì thế, dù trong mã nguồn Java 17+ viết `strictfp`, `Modifier.isStrict(method.getModifiers())` thường trả về `false`. **Không thể dùng giá trị `false` để chứng minh rằng source không có từ khóa `strictfp`.**
4. `strictfp` là từ khóa lỗi thời, compiler hiện đại có thể cảnh báo; không nên thêm vào mã Java mới để “tăng độ chính xác” của phép tính số thực. Khi đọc class file cũ, luôn xét phiên bản class file trước khi suy diễn cờ strict.

Ví dụ minh họa **mã nguồn lịch sử**, không yêu cầu chạy:

```java
strictfp class OldCalculation {
    strictfp double half(double input) {
        return input / 2.0;
    }
}
```

Trên Java 17+, từ khóa trên không làm thay đổi kết quả tính `input / 2.0`. Dùng Reflection thuần chỉ thấy metadata được lưu trong class file; nếu muốn biết chính xác **từ khóa từng xuất hiện trong source**, phải phân tích source hoặc metadata chuyên dụng phù hợp, không suy ngược chỉ từ `getModifiers()`.

## 9. Chương trình Java 17+ hoàn chỉnh: kiểm tra sáu cờ

Tạo tệp `ModifierFlagsDemo.java`:

```java
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class ModifierFlagsDemo {
    static abstract class Task {
        abstract int calculate(int number);

        final String kind() {
            return "task";
        }
    }

    static final class IncrementTask extends Task {
        public static final int STEP = 1;

        @Override
        int calculate(int number) {
            return number + STEP;
        }

        public synchronized int run(int number) {
            return calculate(number);
        }

        // Chỉ khai báo native: ví dụ không bao giờ gọi method này.
        public static native void nativeEntry();
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Class<?> taskClass = Task.class;
        Class<?> implClass = IncrementTask.class;

        Method abstractMethod =
                taskClass.getDeclaredMethod("calculate", int.class);
        Method finalMethod = taskClass.getDeclaredMethod("kind");
        Method synchronizedMethod =
                implClass.getDeclaredMethod("run", int.class);
        Method nativeMethod =
                implClass.getDeclaredMethod("nativeEntry");
        Field stepField = implClass.getDeclaredField("STEP");

        System.out.println("Task abstract: "
                + Modifier.isAbstract(taskClass.getModifiers()));
        System.out.println("Task.calculate abstract: "
                + Modifier.isAbstract(abstractMethod.getModifiers()));
        System.out.println("Task.kind final: "
                + Modifier.isFinal(finalMethod.getModifiers()));
        System.out.println("IncrementTask final: "
                + Modifier.isFinal(implClass.getModifiers()));
        System.out.println("IncrementTask nested static: "
                + Modifier.isStatic(implClass.getModifiers()));

        int stepFlags = stepField.getModifiers();
        System.out.println("STEP static final: "
                + (Modifier.isStatic(stepFlags) && Modifier.isFinal(stepFlags)));

        int runFlags = synchronizedMethod.getModifiers();
        System.out.println("run synchronized: "
                + Modifier.isSynchronized(runFlags));
        System.out.println("run static: "
                + Modifier.isStatic(runFlags));
        System.out.println("run modifiers: "
                + Modifier.toString(runFlags));

        int nativeFlags = nativeMethod.getModifiers();
        System.out.println("nativeEntry native: "
                + Modifier.isNative(nativeFlags));
        System.out.println("nativeEntry static: "
                + Modifier.isStatic(nativeFlags));

        // Chỉ giải mã bitmask minh họa, không phải cờ trong class file mới.
        int legacyStrictMask = Modifier.PUBLIC | Modifier.STRICT;
        System.out.println("legacy strict bit: "
                + Modifier.isStrict(legacyStrictMask));

        Object result = synchronizedMethod.invoke(new IncrementTask(), 4);
        System.out.println("run(4): " + result);
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac ModifierFlagsDemo.java
java ModifierFlagsDemo
```

Kết quả stdout:

```text
Task abstract: true
Task.calculate abstract: true
Task.kind final: true
IncrementTask final: true
IncrementTask nested static: true
STEP static final: true
run synchronized: true
run static: false
run modifiers: public synchronized
nativeEntry native: true
nativeEntry static: true
legacy strict bit: true
run(4): 5
```

Giải thích:

- `Task` mang cờ `abstract`; method `calculate` cũng mang cờ riêng `abstract`. Ngược lại, method `kind` mang cờ `final` chứ không `abstract`.
- `IncrementTask` vừa là lớp lồng `static` vừa là lớp `final`; cờ trên class phản ánh đặc tính class, không tự động suy ra mọi method đều `static` hoặc `final`.
- `STEP` có cả cờ `static` và `final`. Hãy kiểm tra **cả hai bit**, vì không phải `static` luôn đồng nghĩa `final`.
- `run` là instance method `public synchronized`, nên `isSynchronized` là `true` còn `isStatic` là `false`. `Modifier.toString` trình bày các cờ của **method này** theo thứ tự chuẩn.
- `nativeEntry` được khai báo `static native`; chương trình **chỉ lấy metadata**, không gọi native method nên không cần thư viện JNI để chạy ví dụ.
- `legacyStrictMask` do chương trình chủ động tạo từ `Modifier.STRICT` để minh họa cách kiểm tra bit. Nó **không** đại diện cho một method được biên dịch từ `strictfp` bằng JDK 17+.
- `Method.invoke` trên `run` tuân theo cơ chế instance method và monitor; kết quả phép tính là `4 + 1 = 5`, được Reflection trả về dưới dạng `Object` chứa `Integer`.

## 10. Thiết kế bộ lọc Reflection theo modifier

Giả sử chỉ muốn liệt kê những **method public, static, không native** được khai báo trực tiếp trong một class. Có thể lọc như sau:

```java
for (java.lang.reflect.Method method : SomeClass.class.getDeclaredMethods()) {
    int flags = method.getModifiers();
    if (Modifier.isPublic(flags)
            && Modifier.isStatic(flags)
            && !Modifier.isNative(flags)
            && !method.isSynthetic()
            && !method.isBridge()) {
        System.out.println(method.getName());
    }
}
```

Đây là đoạn minh họa, cần một `SomeClass` thực tế và `import java.lang.reflect.Modifier;`. Nó xét **method tự khai báo** nhờ `getDeclaredMethods()`; muốn xét cả method public kế thừa cần cân nhắc `getMethods()`. Không giả định thứ tự mảng method được JVM trả về; sắp xếp nếu công cụ cần đầu ra ổn định. Các bộ lọc `isSynthetic()`/`isBridge()` là tùy nhu cầu công cụ, bởi compiler có thể sinh method phụ trợ.

Thứ tự ra quyết định:

1. **Chọn đúng đối tượng `Class<?>`**: kiểu khai báo hoặc kiểu runtime theo mục tiêu kiểm tra.
2. **Chọn phạm vi khám phá**: `getDeclared*` đối với khai báo riêng của lớp hoặc `get*` để tìm thành viên public theo quy tắc API.
3. **Đọc `getModifiers()`** từ chính class, method hoặc field đang xét; không sử dụng một cờ của class như chứng cứ cho mọi thành viên.
4. **Giải mã từng cờ bằng `Modifier.isXxx(flags)`**, kết hợp điều kiện khi cần (ví dụ `static && final`). Dùng `Modifier.toString` để hiển thị, không lấy đó làm bộ xác thực toàn diện.
5. **Diễn giải theo ngữ nghĩa Java**: `final` không bảo đảm deep immutability; `synchronized` flag không nhìn thấy synchronized block; `native` metadata không chứng minh thư viện native đã sẵn sàng; `strictfp` trong class file mới không thể suy như class file cũ.

## 11. Các lỗi thường gặp

| Ngộ nhận | Sự thật/cách xử lý |
| --- | --- |
| Một số nguyên từ `getModifiers()` biểu thị đúng một từ khóa | Đó là nhiều bit có thể cùng bật; kiểm tra `Modifier.isXxx` hoặc `&` |
| `Modifier.isFinal(SomeClass.class.getModifiers())` cho biết mọi field đều final | Nó chỉ kiểm tra **class**; hãy kiểm tra modifier của từng `Field` |
| `final` field làm đối tượng được tham chiếu bất biến | Nó giới hạn việc gán lại field; trạng thái đối tượng được tham chiếu có thể vẫn thay đổi |
| `static` method được override như instance method | Static method được chọn theo quy tắc hiding; không có dynamic dispatch override theo receiver như instance method |
| Không có cờ `synchronized` nghĩa là không dùng khóa | Method vẫn có thể chứa synchronized block hoặc cơ chế khóa khác |
| `Modifier.isNative(flags)` là chứng cứ native library đã được nạp | Chỉ chứng minh method mang cờ native; khả năng liên kết/chạy là chuyện khác |
| Java 17+ `isStrict() == false` nghĩa là source không có `strictfp` | Java 17+ luôn dùng ngữ nghĩa FP strict và không cần lưu ACC_STRICT như class file cũ |
| `getMethods()` và `getDeclaredMethods()` giống nhau | Một API thiên về public và kế thừa, API kia chỉ khai báo trực tiếp, bất kể access |
| Có metadata private nghĩa là được tùy ý gọi/đọc | Quyền truy cập và giới hạn module vẫn phải được tuân thủ |
| `Modifier.toString(flags)` bảo đảm các cờ hợp lệ với phần tử | Hàm chỉ render bitmask; ngữ cảnh và tổ hợp hợp lệ vẫn phải được xét riêng |

## 12. Câu hỏi tự kiểm tra

1. **Một method `public static synchronized` có thể có cả `isStatic` và `isSynchronized` bằng `true` không?** Có. Method static synchronized đồng bộ trên monitor của đối tượng `Class`.
2. **Một class `final` có thể chứa method `abstract` không?** Không. Class `final` không thể kế thừa để hoàn thiện abstract method; `final` và `abstract` không thể kết hợp trên class.
3. **`Modifier.isSynchronized` phát hiện được `synchronized(this)` trong thân method thường không?** Không. Nó chỉ đọc flag khai báo method.
4. **Chưa load thư viện JNI, có thể đọc `getDeclaredMethod("nativeEntry")` và `isNative` không?** Có, miễn class và metadata liên quan đọc được; ví dụ không gọi method native.
5. **Tại sao `Modifier.isStrict` không đáng tin để phát hiện từ khóa trong mã nguồn Java 17+?** Vì mã FP luôn strict và `ACC_STRICT` chỉ được định nghĩa cho class-file major 46–60, nên mã class file mới không phải giữ bit đó.
6. **Một field `static final List<?>` có chắc list bất biến không?** Không. Không gán lại tham chiếu field không đồng nghĩa chặn thay đổi nội dung list.

**Ghi nhớ:** `Modifier` giúp giải mã **cờ metadata của đúng phần tử**. Muốn hiểu tác động, luôn xét loại phần tử, phạm vi Reflection và phiên bản Java/class file; bản thân cờ không thay thế phân tích ngữ nghĩa Java hoặc hành vi runtime.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="modifiers-flags-004">Phân biệt modifier có ý nghĩa trên lớp, field hoặc executable</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt modifier có ý nghĩa trên lớp, field hoặc executable trong Java Reflection

## 1. Cùng một `int` modifier nhưng ý nghĩa phụ thuộc phần tử

Trong Java Reflection, nhiều thành phần có phương thức `getModifiers()` trả về một số nguyên `int` chứa các bit cờ:

- `Class<?>.getModifiers()`: cờ của lớp hoặc interface.
- `Field.getModifiers()`: cờ của field.
- `Method.getModifiers()`: cờ của phương thức.
- `Constructor<?>.getModifiers()`: cờ của constructor.
- `Parameter.getModifiers()`: cờ liên quan tham số hình thức, nếu cần phân tích sâu hơn.

Thư viện `java.lang.reflect.Modifier` cung cấp các hằng như `PUBLIC`, `STATIC`, `FINAL` và các hàm `isPublic(int)`, `isStatic(int)`, `isFinal(int)`, `toString(int)`. Mỗi bit có thể được kiểm tra bằng phép AND:

```java
int flags = someField.getModifiers();
boolean isStatic = (flags & Modifier.STATIC) != 0;

// Tương đương:
boolean alsoStatic = Modifier.isStatic(flags);
```

Đây là **mảnh mã** cần đặt trong ngữ cảnh đã có biến `someField` và import `java.lang.reflect.Modifier`.

**Quy tắc quan trọng:** Phải biết `flags` lấy từ loại phần tử nào trước khi giải thích. Ví dụ, `final` trên class cấm kế thừa, trên method cấm override, trên field giới hạn phép gán; `static` trên nested class khác `static` trên field hoặc method. Một cờ chỉ có nghĩa khi cấu trúc Java tương ứng cho phép cờ đó.

## 2. Bảng phân loại theo nơi xuất hiện

Ký hiệu: ✓ là có ý nghĩa ở loại phần tử tương ứng, — là không phải modifier áp dụng cho loại đó. Từng trường hợp ✓ còn phụ thuộc vị trí khai báo (top-level, member, interface, v.v.).

| Modifier / đặc tính | `Class<?>` (lớp/interface) | `Field` | `Method` | `Constructor<?>` | Ý nghĩa khi hợp lệ |
| --- | --- | --- | --- | --- | --- |
| `public` | ✓ | ✓ | ✓ | ✓ | Phạm vi truy cập công khai theo quy tắc Java và module |
| `protected` | ✓ với kiểu thành viên phù hợp | ✓ | ✓ | ✓ | Truy cập trong package và theo quy tắc subclass |
| `private` | ✓ với kiểu thành viên phù hợp | ✓ | ✓ | ✓ | Giới hạn truy cập theo ngữ cảnh bao quanh |
| `abstract` | ✓ | — | ✓ | — | Lớp chưa hoàn chỉnh / method chưa có phần triển khai |
| `static` | ✓ với kiểu lồng thành viên | ✓ | ✓ | — | Thành phần gắn với kiểu chứa thay vì từng instance; nested static class không cần instance bao ngoài |
| `final` | ✓ | ✓ | ✓ | — | Cấm subclass / hạn chế gán lại field / cấm override method |
| `transient` | — | ✓ | — | — | Field bị bỏ qua bởi cơ chế tuần tự hóa mặc định của Java |
| `volatile` | — | ✓ | — | — | Cơ chế đọc/ghi field có bảo đảm bộ nhớ theo Java Memory Model |
| `synchronized` | — | — | ✓ | — | Method được đồng bộ hóa bằng monitor |
| `native` | — | — | ✓ | — | Method có phần triển khai native |
| `strictfp` | ✓ trong cú pháp lịch sử | — | ✓ trong cú pháp lịch sử | — | Từng kiểm soát quy tắc số thực; từ Java 17 là thừa theo ngữ nghĩa ngôn ngữ |
| `interface` (bit `Modifier.INTERFACE`) | ✓ cho interface | — | — | — | Phân loại interface ở mức phản chiếu; không phải cờ áp dụng cho field/method/constructor |

Các điểm phải nhớ khi đọc bảng:

- **Top-level class** chỉ có thể khai báo `public` hoặc để mức truy cập package-private; không khai báo trực tiếp `private`, `protected` hay `static`. Nhưng **member class** nằm trong một kiểu khác có thể có các modifier đó nếu phù hợp.
- Interface có quy tắc riêng: ví dụ, method trong interface có thể là `public`, `private`, `abstract`, `static`, hoặc `default` theo cú pháp hợp lệ; không phải mọi tổ hợp trong bảng đều hợp lệ.
- Constructor chỉ nhận modifier truy cập (`public`, `protected`, `private`) hoặc để package-private trong mã nguồn thông thường. Không có `static constructor`, `final constructor` hay `abstract constructor`.
- `transient`/`volatile` dành cho field; `synchronized`/`native` dành cho method. Không kết luận loại một phần tử chỉ bằng tên cờ mà không kiểm tra kiểu metadata.

**Package-private** không có hằng `Modifier.PACKAGE_PRIVATE`: nếu cả `public`, `protected` và `private` đều vắng mặt, cấp truy cập ngôn ngữ là package-private. Đây không phải một bit thứ tư.

## 3. Modifier của kiểu: `Class<?>`

### `public`, `protected`, `private` trên lớp

Một lớp ở mức top-level chỉ được `public` hoặc package-private. Với **member class**, thông tin truy cập có thể là `private`, `protected` hoặc `public`. Phản chiếu bằng:

```java
Class<?> type = java.util.Map.Entry.class;
int m = type.getModifiers();

System.out.println(Modifier.isPublic(m));
System.out.println(Modifier.isStatic(m));
System.out.println(Modifier.isInterface(m));
```

Đây là **mảnh mã trong phương thức** có import `Modifier`. `Map.Entry` là nested interface (một member type) nên có những bit kiểu không thể áp dụng tương tự cho top-level class. Đừng đọc `Modifier.isStatic(type.getModifiers())` thành “mọi class có thể khai báo static”.

### `final`, `abstract`, `static` có ý nghĩa gì?

- `final class`: không cho khai báo subclass. Ví dụ `String.class` là final. Không thể suy ra mọi field hoặc method của lớp đều mang cờ `final` trong metadata.
- `abstract class`: không thể trực tiếp `new` và có thể chứa abstract method; **không phải** cứ abstract class là tất cả method đều abstract. Interface thông thường có tính trừu tượng ở cấp kiểu, nhưng còn có default/static/private methods.
- `static` trên member class: lớp lồng không cần một instance bao ngoài theo cơ chế inner class thông thường. Đây là một đặc tính cấu trúc của nested type, không phải trạng thái tĩnh của mọi đối tượng.
- `Modifier.isInterface(m)` nhận diện bit `interface`; nếu cần phân biệt annotation interface, dùng `type.isAnnotation()`. Interface không đồng nghĩa abstract class dù nhiều API modifier có vẻ gần nhau.

Các loại kiểu mới/cụ thể có API chuyên dụng:

| Cần nhận biết | Reflection API thích hợp | Ghi chú |
| --- | --- | --- |
| Interface | `Class.isInterface()`; hoặc `Modifier.isInterface(type.getModifiers())` | Bit `INTERFACE` thuộc metadata kiểu |
| Annotation interface | `Class.isAnnotation()` | Không chỉ dùng `isInterface()` vì annotation cũng là interface |
| Enum | `Class.isEnum()` | Không có `Modifier.isEnum(int)` |
| Record (Java 16+) | `Class.isRecord()` | Không có `Modifier.isRecord(int)` |
| Sealed type (Java 17+) | `Class.isSealed()`; `getPermittedSubclasses()` | Không có `Modifier.isSealed(int)` |
| Mảng, primitive | `Class.isArray()`, `Class.isPrimitive()` | `Class.getModifiers()` có quy tắc đặc thù; không coi chúng như khai báo class thông thường |

Đặc biệt, `Class.getModifiers()` đối với **array, primitive và `void`** có quy tắc do API định nghĩa; ví dụ các kiểu này được xem là `final` tại phương thức này. Đó không có nghĩa ta có thể viết khai báo nguồn `final int` để mô tả kiểu nguyên thủy. Cần phân biệt metadata của kiểu và modifier có thể gõ trong khai báo nguồn.

### `sealed`, `non-sealed` và `strictfp`

`sealed`/`non-sealed` thuộc cú pháp loại kiểu nhưng **không có** `Modifier.SEALED` hay `Modifier.NON_SEALED` công khai; dùng `Class.isSealed()` nếu cần biết một kiểu có bị niêm phong hay không. Đối với `non-sealed`, riêng `!type.isSealed()` không đủ chứng minh khai báo có chữ `non-sealed`, vì lớp bình thường cũng không sealed.

`strictfp` là modifier lịch sử cho lớp/interface/method. Từ Java 17, đánh giá biểu thức floating-point luôn theo strict semantics, vì vậy `strictfp` dư về ý nghĩa hành vi; tránh xây dựng nghiệp vụ phụ thuộc vào sự hiện diện của bit này trên JDK hiện đại.

## 4. Modifier của field: `Field`

Field đại diện một biến thành viên của lớp hoặc interface, có thể là instance field hoặc static field:

- `static`: field thuộc lớp, không thuộc từng instance. `static` không đồng nghĩa hằng số.
- `final`: sau giai đoạn khởi tạo hợp lệ, không thể gán lại qua mã Java thông thường. **`final` của một reference không làm object được trỏ đến trở thành bất biến sâu**. Việc sửa final field qua Reflection chịu các hạn chế riêng; không suy diễn quyền ghi từ một bit.
- `volatile`: thiết lập quan hệ hiển thị/thứ tự bộ nhớ cho các thao tác đọc ghi field. **`counter++` trên volatile field vẫn không phải một phép tăng nguyên tử.**
- `transient`: loại field khỏi dạng serialization mặc định bằng `ObjectOutputStream`; không đồng nghĩa field bị xóa, không thể đọc, hoặc sẽ bị loại bỏ trong mọi cơ chế JSON/ORM.
- `public`, `protected`, `private`: mô tả phạm vi truy cập khai báo.

Ví dụ mảnh field:

```java
private transient volatile int state;
public static final int LIMIT = 3;
```

Nếu Reflection lấy `getModifiers()` của `state`, `Modifier.toString(...)` hiển thị `"private transient volatile"`; với `LIMIT`, trả `"public static final"`. Thứ tự trả ra từ `toString` là thứ tự chuẩn hóa, không nhất thiết là thứ tự từ khóa đã gõ trong mã nguồn.

Đối với một enum, `Field.isEnumConstant()` cho biết field có phải hằng enum hay không; `Field.isSynthetic()` cho biết field do compiler tạo theo định nghĩa JVM/JLS. Không có `Modifier.isEnumConstant(int)` hay `Modifier.isSynthetic(int)` công khai.

## 5. Modifier của executable: `Method` và `Constructor<?>`

`java.lang.reflect.Executable` là lớp cha chung của `Method` và `Constructor<?>`; cả hai đều có `getModifiers()`. **Executable không bao gồm `Field` hay `Class<?>`**.

### Với `Method`

- `public`, `protected`, `private`: phạm vi truy cập method.
- `static`: method thuộc cấp class; không cần instance để gọi theo cách thông thường.
- `final`: method không thể bị override (nếu method có thể được kế thừa theo các quy tắc khác).
- `abstract`: method không có body ở nơi khai báo; cần phần triển khai ở lớp concrete thích hợp.
- `synchronized`: tự động lấy monitor khi vào method, giải phóng khi thoát kể cả khi ném exception. Instance method dùng monitor của receiver; static synchronized method dùng `Class` object.
- `native`: method có phần triển khai do mã native cung cấp, không có body Java.
- `strictfp`: cú pháp lịch sử, từ Java 17 không thay đổi kết quả số thực.

**Method trong interface** có trường hợp `default` có body, nhưng `Modifier` không có hằng `DEFAULT`. Dùng **`Method.isDefault()`** để nhận biết; không suy ra default method chỉ từ việc không có `abstract` vì interface còn có static và private methods.

### Với `Constructor<?>`

Constructor chỉ dùng các bit truy cập của Java. Không kiểm tra ý nghĩa của `static`, `abstract`, `synchronized`, `final`, `native` trên constructor. Dùng:

```java
Constructor<?> ctor = type.getDeclaredConstructor();
int m = ctor.getModifiers();

boolean privateCtor = Modifier.isPrivate(m);
```

Đây là **mảnh phương thức**, biến `type` là `Class<?>`, cần import `Constructor` và `Modifier`. `getDeclaredConstructor()` có thể lấy metadata constructor private, nhưng **không tự cấp quyền gọi** constructor đó. Java access checks và cơ chế module vẫn có hiệu lực.

### Những thông tin của executable không nên đọc qua `Modifier`

| Điều cần hỏi | API đúng | Loại |
| --- | --- | --- |
| Method là default method? | `Method.isDefault()` | Method |
| Method là bridge method do compiler tạo? | `Method.isBridge()` | Method |
| Executable nhận varargs? | `Executable.isVarArgs()` | Method và constructor |
| Executable được đánh dấu synthetic? | `Executable.isSynthetic()` | Method và constructor |
| Parameter khai báo `final`? | `Modifier.isFinal(parameter.getModifiers())` | Parameter |
| Parameter là implicit/synthetic? | `Parameter.isImplicit()`, `Parameter.isSynthetic()` | Parameter |

Các cờ ở JVM/class file như `bridge`, `varargs`, `synthetic` có thể nằm trong metadata riêng hoặc dùng bit value trùng với cờ ở ngữ cảnh khác. **Không kiểm tra `Modifier.isVolatile(method.getModifiers())` để tìm bridge method, hoặc `Modifier.isTransient(method.getModifiers())` để tìm varargs**, dù có thể gặp trùng giá trị bit ở mức JVM. Phải gọi API có ngữ nghĩa đúng như `isBridge()` và `isVarArgs()`.

`@Override`, `@Deprecated` và nhiều annotations khác cũng không phải cờ của `Modifier`. Khi cần annotations, dùng `getDeclaredAnnotations()`, `isAnnotationPresent(...)` hoặc API thích hợp.

## 6. Ví dụ hoàn chỉnh, chạy với Java 17+

Tạo **`ModifierByContextDemo.java`** với nội dung:

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class ModifierByContextDemo {
    public static final class Box {
        private transient volatile int state;
        public static final int LIMIT = 3;

        private Box() {}

        public final synchronized void bump() {
            state++;
        }

        public void log(String... messages) {}
    }

    interface Action {
        default void perform() {}
    }

    private static void show(String label, int modifiers) {
        System.out.println(label + ": " + Modifier.toString(modifiers));
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Class<?> type = Box.class;
        Field state = type.getDeclaredField("state");
        Field limit = type.getDeclaredField("LIMIT");
        Constructor<?> ctor = type.getDeclaredConstructor();
        Method bump = type.getDeclaredMethod("bump");
        Method log = type.getDeclaredMethod("log", String[].class);
        Method perform = Action.class.getDeclaredMethod("perform");

        show("type", type.getModifiers());
        show("state", state.getModifiers());
        show("LIMIT", limit.getModifiers());
        show("ctor", ctor.getModifiers());
        show("bump", bump.getModifiers());

        System.out.println("type final? "
                + Modifier.isFinal(type.getModifiers()));
        System.out.println("state volatile? "
                + Modifier.isVolatile(state.getModifiers()));
        System.out.println("state transient? "
                + Modifier.isTransient(state.getModifiers()));
        System.out.println("bump synchronized? "
                + Modifier.isSynchronized(bump.getModifiers()));
        System.out.println("ctor private? "
                + Modifier.isPrivate(ctor.getModifiers()));
        System.out.println("log varargs? " + log.isVarArgs());
        System.out.println("perform default? " + perform.isDefault());
    }
}
```

Biên dịch/chạy:

```bash
javac --release 17 ModifierByContextDemo.java
java ModifierByContextDemo
```

Kết quả:

```text
type: public static final
state: private transient volatile
LIMIT: public static final
ctor: private
bump: public final synchronized
type final? true
state volatile? true
state transient? true
bump synchronized? true
ctor private? true
log varargs? true
perform default? true
```

**Giải thích kết quả:**

1. `Box` là **public static final member class**, không phải top-level class. Do đó có cả `static` và `final` trong kết quả type.
2. `state` là **instance field** với hai chính sách độc lập: `transient` và `volatile`. Không có `static`/`final`.
3. `LIMIT` là **static final field**, có thể đọc như hằng số nhưng semantics `final` không đồng nhất với `final class`.
4. `ctor` có mỗi `private`. Không gọi constructor, vì việc đọc metadata không cần khởi tạo instance.
5. `bump` là **final synchronized method**. Hai bit nói về override và lock, không về field hay class.
6. `log` có varargs: `isVarArgs()` trả true dù `Modifier.toString(log.getModifiers())` không in chữ `varargs`.
7. `perform` là default method của interface: `isDefault()` trả true dù không tồn tại `Modifier.DEFAULT`.

Ví dụ này chỉ tra metadata; không cần `setAccessible(true)`, không sửa đối tượng bằng Reflection và không phụ thuộc giá trị tên do compiler tự sinh.

## 7. Bộ lọc modifier theo loại phần tử

`Modifier` còn cung cấp các hàm trả về **mask các modifier được phép có trong khai báo nguồn theo loại phần tử**:

```java
Modifier.classModifiers();
Modifier.interfaceModifiers();
Modifier.fieldModifiers();
Modifier.methodModifiers();
Modifier.constructorModifiers();
Modifier.parameterModifiers();
```

Ví dụ, `Modifier.toString(Modifier.fieldModifiers())` trên JDK 17+ hiển thị các modifier nguồn có thể áp dụng cho field: `public protected private static final transient volatile`. `Modifier.toString(Modifier.constructorModifiers())` chỉ hiển thị `public protected private`.

Muốn giữ lại những bit modifier nguồn thuộc một loại thành phần:

```java
int sourceFieldFlags =
        field.getModifiers() & Modifier.fieldModifiers();
String display = Modifier.toString(sourceFieldFlags);
```

Đây là **mảnh mã trong phương thức** có biến `field` thuộc kiểu `Field`. Việc AND mask **không kiểm chứng** mọi kết hợp có hợp lệ trong ngữ cảnh cụ thể hay không. Ví dụ, mask class cho phép `private` vì có member classes, nhưng `private` vẫn không hợp lệ với top-level class. Các hàm mask cũng không thay thế `isBridge()`, `isSynthetic()`, `isVarArgs()` hay truy vấn khác về metadata không biểu diễn bằng modifier nguồn.

Nếu thư viện xử lý metadata nhận nhiều loại phần tử, có thể phân nhánh kiểu một cách tường minh:

```java
static String describeMember(java.lang.reflect.Member member) {
    int flags = member.getModifiers();

    if (member instanceof java.lang.reflect.Field) {
        return "field: static=" + Modifier.isStatic(flags)
                + ", volatile=" + Modifier.isVolatile(flags)
                + ", transient=" + Modifier.isTransient(flags);
    }

    if (member instanceof java.lang.reflect.Method method) {
        return "method: static=" + Modifier.isStatic(flags)
                + ", abstract=" + Modifier.isAbstract(flags)
                + ", synchronized=" + Modifier.isSynchronized(flags)
                + ", varargs=" + method.isVarArgs();
    }

    if (member instanceof java.lang.reflect.Constructor<?>) {
        return "constructor: private=" + Modifier.isPrivate(flags);
    }

    return "member type not handled";
}
```

Đây là **mảnh phương thức trong class** với `import java.lang.reflect.Modifier;`. `Class<?>` không triển khai interface `Member`, vì vậy xử lý modifier của class ở nhánh riêng bằng `Class.getModifiers()`. Mẫu này ngăn việc áp dụng nhầm một phép kiểm tra chỉ có nghĩa trên field sang method hoặc constructor.

## 8. Những nhầm lẫn dễ gặp

| Nhầm lẫn | Cách kiểm tra đúng |
| --- | --- |
| Thấy `final` thì kết luận “không thể sửa giá trị” trên mọi phần tử | Hỏi `final` nằm trên class, field hay method; ba nghĩa khác nhau |
| `Modifier.isStatic(clazz.getModifiers())` nghĩa là instance của class là static | Chỉ ra kiểu khai báo là member type lồng, nếu cờ này có ý nghĩa |
| `Modifier.isVolatile(method.getModifiers())` để đoán method được compiler tạo | Dùng `Method.isBridge()`/`isSynthetic()`; volatile là modifier dành cho field |
| Constructor có `synchronized`/`static` giống method | Constructor chỉ có các modifier truy cập |
| `Modifier.isPublic(field.getModifiers())` bảo đảm thao tác Reflection sẽ thành công | Access còn phụ thuộc class chứa, nơi gọi, modules và chính sách truy cập |
| Không có cờ `public`/`protected`/`private` nghĩa là `public` | Không có ba bit này nghĩa là package-private theo quy tắc truy cập nguồn |
| `Modifier.toString(...)` hiển thị mọi đặc tính của class/method | Cần thêm các API `isSealed`, `isRecord`, `isDefault`, `isVarArgs`, `isBridge`, `isSynthetic`... |
| `transient` đồng nghĩa “không lưu ở bất cứ đâu” | Chỉ có ý nghĩa đặc biệt đối với serialization mặc định; framework khác có quy tắc riêng |
| `volatile` biến `++` thành nguyên tử | Không; tăng có nhiều thao tác, nên dùng atomics hoặc đồng bộ phù hợp |
| `getDeclaredField("x")`/`getDeclaredConstructor()` đã bỏ qua Java access control | Chúng lấy metadata khai báo; quyền truy cập thực tế được kiểm tra riêng |

Cũng không nên giả định `getModifiers()` mô tả đầy đủ nguyên văn các token trong tệp `.java`: compiler có thể thêm thông tin ngầm định và các chi tiết khác của class file. Kết quả là mô hình Reflection theo đặc tả API, không phải bản ghi lại cú pháp nguồn.

## 9. Cách lựa chọn API khi kiểm tra modifier

1. **Xác định đối tượng đang kiểm tra**: `Class<?>`, `Field`, `Method`, `Constructor<?>` hay `Parameter`.
2. **Chọn tính chất thật sự cần biết**: access level, static/final/abstract; volatile/transient của field; synchronized/native của method; loại interface/record/sealed; hay varargs/default/bridge.
3. **Dùng `Modifier.isXxx` khi tính chất là modifier đúng ngữ cảnh**, và dùng API chuyên dụng khi tính chất không thuộc các bit nguồn công khai.
4. **Diễn giải trong ngữ cảnh khai báo**: top-level khác member class, method interface khác method class, constructor khác method.
5. **Không dùng bit để suy luận xa hơn đặc tả**: kiểm tra access/invocation riêng và xem thêm metadata cần thiết khi định xây dựng công cụ Reflection.

## 10. Bài tập tự kiểm tra

1. Giải thích sự khác nhau của `final` trên `String.class`, field `private final List<String> items` và method `public final void close()`. Trường hợp nào ngăn subclass, trường hợp nào ngăn override, trường hợp nào hạn chế gán lại reference?
2. Viết một member class `private static class Helper {}`. Dự đoán `Modifier.toString(Helper.class.getModifiers())`. Sau đó thử chuyển nó thành top-level class: các modifier nào không còn được phép?
3. Với field `volatile int count`, kiểm tra `Modifier.isVolatile`; giải thích vì sao `count++` vẫn có race condition.
4. So sánh `Method.isDefault()` và `Modifier.isAbstract(method.getModifiers())` cho hai method của một interface: một method khai báo `void run();` và một method `default void stop() {}`.
5. Lấy `Constructor<?>` private qua `getDeclaredConstructor()`; giải thích sự khác nhau giữa “Reflection nhìn thấy metadata” và “Reflection được phép gọi constructor”.
6. Tạo method `void send(String... values) {}`; dùng `isVarArgs()` để nhận diện, sau đó giải thích vì sao không nên kiểm tra `Modifier.isTransient(method.getModifiers())` cho cùng mục đích.

## 11. Tài liệu tham khảo chính thức

- [Java SE 17 — `java.lang.reflect.Modifier`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Modifier.html): hằng bit, `isXxx`, `toString` và các mask theo loại phần tử.
- [Java SE 17 — `Class.getModifiers()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getModifiers()): hành vi với class/interface, mảng, primitive, `void`.
- [Java SE 17 — `Field`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html): modifier của field, `isEnumConstant()` và `isSynthetic()`.
- [Java SE 17 — `Executable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Executable.html): nền tảng chung của method/constructor, `isVarArgs()` và `isSynthetic()`.
- [Java SE 17 — `Method`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html): `getModifiers()`, `isDefault()`, `isBridge()`.
- [Dev.java — Reading Modifiers](https://dev.java/learn/api/reflection-method-handles/reflection/modifiers/): hướng dẫn Reflection về ý nghĩa cờ theo thành phần.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="modifiers-flags-005">volatile, transient và thành viên dữ liệu</a>

<details>
<summary>Xem chi tiết</summary>

# `volatile`, `transient` và thành viên dữ liệu trong Java Reflection

Khi quan sát một class bằng Java Reflection, **trường dữ liệu** (*field*) không chỉ có tên và kiểu. Mỗi field còn có tập **modifier** mô tả phạm vi truy cập (`public`/`protected`/`private`), cách gắn với đối tượng (`static`), khả năng gán lại (`final`) và những hành vi đặc biệt như **`volatile`** hoặc **`transient`**. Hai từ khóa cuối cùng cùng áp dụng cho field nhưng giải quyết **hai vấn đề hoàn toàn khác nhau**.

| Modifier | Quan tâm đến | Ảnh hưởng cốt lõi | Cách kiểm tra qua reflection |
| --- | --- | --- | --- |
| `volatile` | Truy cập dữ liệu được chia sẻ giữa nhiều luồng | Quy tắc đọc/ghi và quan hệ *happens-before* của Java Memory Model | `Modifier.isVolatile(field.getModifiers())` |
| `transient` | Trạng thái được lưu khi tuần tự hóa đối tượng | Loại field khỏi **default Java object serialization** | `Modifier.isTransient(field.getModifiers())` |
| `static` | Sở hữu dữ liệu ở cấp class | Một field dùng chung cho class thay vì mỗi instance | `Modifier.isStatic(field.getModifiers())` |
| `final` | Giới hạn phép gán trong mã Java | Field chỉ có thể được gán theo các quy tắc khởi tạo `final` | `Modifier.isFinal(field.getModifiers())` |

Bài học tập trung vào **ý nghĩa thực thi của `volatile` và `transient`**, cách đọc cờ modifier trên `java.lang.reflect.Field`, và sự khác biệt giữa *thông tin metadata* và *hành vi của JVM*. Không nên suy ra ngữ nghĩa đồng bộ hay serialization chỉ từ việc thấy hai từ khóa xuất hiện trong một tên field.

## 1. Field là gì và vì sao có modifier?

Một field là biến được khai báo trong class/interface; nó khác biến cục bộ trong phương thức và tham số phương thức. Field có thể là:

- **Instance field:** mỗi đối tượng có trạng thái riêng, ví dụ `private String name`.
- **Static field:** thuộc class, dùng chung theo định danh class, ví dụ `static int total`.
- **Field chứa primitive hoặc reference:** `int count` giữ giá trị nguyên; `List<String> entries` giữ tham chiếu đến đối tượng (generic arguments không còn được JVM kiểm tra theo cách compile-time).
- **Field với nhiều modifier:** ví dụ `private transient volatile int cacheHits` hợp lệ; mỗi modifier mang một ý nghĩa độc lập.

Ví dụ:

~~~java
class SessionState {
    private String owner;               // instance field thông thường
    private volatile boolean cancelled; // tín hiệu giữa các luồng
    private transient String token;     // bỏ qua trong Java serialization mặc định
    private static int activeSessions;  // trạng thái cấp class
}
~~~

Ở runtime, mỗi field được mô tả bằng một `Field`. Phương thức `Field.getModifiers()` trả về một số `int`, trong đó mỗi bit tương ứng một cờ modifier. Dùng các phương thức của `java.lang.reflect.Modifier` để giải mã bit thay vì so sánh bằng một số cố định.

## 2. `volatile`: thứ tự và khả năng quan sát giữa các luồng

Khai báo:

~~~java
private volatile boolean ready;
private static volatile boolean shutdownRequested;
~~~

`volatile` quy định rằng việc đọc và ghi field tham gia các *synchronization actions* của Java Memory Model (JMM). **Một lần ghi volatile** tạo quan hệ *synchronizes-with* với **các lần đọc cùng field đứng sau nó trong synchronization order**. Kết hợp thứ tự chương trình (*program order*), ta có quan hệ *happens-before*: các ghi thông thường của luồng A **trước** lần ghi volatile có thể được luồng B quan sát đúng sau khi B đọc volatile theo quan hệ đồng bộ đó.

Mô hình thường gặp là phát tín hiệu rằng dữ liệu đã sẵn sàng:

~~~java
class Signal {
    private int answer;
    private volatile boolean published;

    void publish() {       // Luồng A
        answer = 42;       // (1) Ghi dữ liệu
        published = true;  // (2) Ghi volatile, diễn ra sau (1)
    }

    int readAfterPublished() {   // Luồng B
        if (!published) {
            throw new IllegalStateException("Not yet published");
        }
        return answer;           // Nếu đã quan sát true, thấy answer = 42
    }
}
~~~

Đây là **đoạn minh họa**, không phải chương trình có `main`. Giả sử `published` chỉ chuyển từ `false` sang `true` theo quy trình trên, và không có tác vụ khác ghi `answer` sau khi phát tín hiệu. Khi B đọc thấy `published == true`, nó quan sát được ghi `answer = 42` đã xảy ra trước lần công bố ấy. Nếu B đọc `false`, phương thức báo chưa có dữ liệu; `volatile` không ép producer phải chạy sớm hơn.

### `volatile` bảo đảm gì?

1. **Visibility và ordering:** đọc/ghi volatile tạo những ràng buộc bộ nhớ cần thiết cho luồng giao tiếp qua field đó. Cách giải thích chính xác là dựa trên *happens-before*, không dựa vào giả định “luôn đọc trực tiếp từ RAM”.
2. **Tính nguyên tử của một lần đọc/ghi field volatile:** kể cả `volatile long` và `volatile double`, JVM không được để các luồng quan sát nửa giá trị từ hai lần ghi khác nhau.
3. **Không bảo đảm nguyên tử của thao tác gồm nhiều bước:** `count++` là đọc giá trị, tính `+1`, ghi lại. Hai luồng có thể cùng đọc cùng một giá trị rồi ghi đè kết quả, dù `count` là volatile.
4. **Không thay thế khóa:** khi phải bảo vệ invariant liên quan nhiều field, đọc–sửa–ghi hoặc nhiều thay đổi cần diễn ra cùng nhau, dùng `synchronized`, `Lock`, các lớp atomic hoặc cơ chế đồng bộ phù hợp.

Ví dụ **không an toàn** khi nhiều luồng tăng chung:

~~~java
volatile int count;

void increment() {
    count++; // Không atomic: có thể mất lần tăng.
}
~~~

Thay bằng:

~~~java
java.util.concurrent.atomic.AtomicInteger count =
        new java.util.concurrent.atomic.AtomicInteger();

void increment() {
    count.incrementAndGet(); // Thao tác tăng atomic.
}
~~~

Hai đoạn trên mô tả các thành viên trong class, không phải chương trình Java độc lập. Nếu dùng `synchronized` để bảo vệ cùng dữ liệu, mọi đường truy cập liên quan cũng phải tuân thủ cùng chính sách khóa.

### Những giới hạn thường bị bỏ qua

- `volatile MyObject current` bảo đảm truy cập **tham chiếu** `current` theo JMM; **không làm mọi field bên trong `MyObject` tự động trở thành volatile**. Muốn phối hợp cập nhật trạng thái đối tượng mutable sau khi publish, cần thêm cơ chế thread-safety phù hợp.
- `volatile int[] values` làm volatile **tham chiếu mảng**, không biến `values[0]` thành một phần tử volatile. Dùng `AtomicIntegerArray`, khóa hoặc cấu trúc concurrent nếu cần đồng bộ phần tử.
- `volatile` không bảo đảm fairness hay thời điểm luồng được lên lịch. Busy-wait kéo dài có thể lãng phí CPU; trong tác vụ chờ thực tế, cân nhắc `CountDownLatch`, `BlockingQueue`, `Condition` hoặc công cụ concurrency khác.
- `volatile` có thể khai báo trên **instance field hoặc static field**. Không khai báo trực tiếp trên biến cục bộ hay tham số.
- Java **cấm** tổ hợp `final volatile` trên cùng một field (`compile-time error`), vì quy tắc final và volatile ở trường này không thể khai báo đồng thời.

## 3. `transient`: trạng thái không lưu khi Java serialization mặc định

`transient` thường xuất hiện trên instance field mà ứng dụng không muốn lưu thành một phần của trạng thái bền vững:

~~~java
class AccountState implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    String username;
    transient String oneTimeToken;
    transient int cacheHits;
}
~~~

Khi dùng **`ObjectOutputStream` với default serialization**, trường non-`static`, non-`transient` là các field được đưa vào mô tả trạng thái mặc định. Trường `transient` **không được ghi** như một field mặc định. Khi phục hồi bằng `ObjectInputStream`, phần trạng thái transient không được lấy lại từ stream theo cách mặc định:

- Primitive nhận giá trị mặc định phù hợp, ví dụ `int` → `0`, `boolean` → `false`.
- Reference nhận `null`.
- Field **không transient**, ví dụ `username`, được lưu/khôi phục nếu giá trị của nó và đồ thị đối tượng đáp ứng quy tắc Java serialization.
- **Static field** thuộc class và không tham gia trạng thái instance được lưu mặc định, dù có khai báo `transient` hay không.

Đáng chú ý: khi deserialization một class `Serializable` thông thường, **constructor và field initializer của chính class Serializable đó không được chạy theo cách tạo object bình thường**. Vì vậy `transient int cacheHits = 7` có thể trở thành `0` sau khi deserialize, thay vì quay lại `7`. Constructor không tham số của superclass đầu tiên không phải `Serializable` vẫn có quy tắc gọi riêng trong quá trình khôi phục.

`transient` chỉ là quy tắc cho cơ chế serializing phù hợp, **không phải “xóa field khỏi class”**: field vẫn tồn tại trong reflection, vẫn đọc/ghi được theo quyền truy cập, vẫn có giá trị khi object hoạt động bình thường.

### `transient` có phải biện pháp bảo mật tuyệt đối?

Không. `transient` không tự mã hóa/xóa dữ liệu trong RAM, không buộc mọi thư viện JSON, ORM, mapping hoặc persistence bên ngoài phải bỏ qua field. Cơ chế custom serialization, `Externalizable` hoặc code tự ghi dữ liệu có thể chủ động lưu giá trị theo cách khác. Không mặc định tin rằng đánh dấu `transient` là đủ để ngăn lọt dữ liệu nhạy cảm ở mọi kênh xuất dữ liệu.

Nếu cần khởi tạo lại transient state, thiết kế bước phục hồi có kiểm soát (chẳng hạn custom `readObject` hợp lệ, factory tái thiết lập, hoặc lazy initialization). Việc deserialize dữ liệu không đáng tin cậy cũng cần biện pháp riêng, không được bảo vệ chỉ bởi `transient`.

## 4. `volatile` và `transient` có thể kết hợp không?

**Có.** Hai modifier tác động lên hai khía cạnh độc lập nên khai báo sau hợp lệ:

~~~java
transient volatile int runtimeCounter;
~~~

Nó biểu thị:

- **Tại runtime:** các lần đọc/ghi `runtimeCounter` tuân thủ ngữ nghĩa `volatile`; `runtimeCounter++` vẫn không atomic.
- **Khi Java serialization mặc định:** field bị bỏ qua khỏi serialized instance state, và khi deserialize, nó trở về `0` nếu không có logic phục hồi khác.

Bảng tổ hợp thường gặp:

| Khai báo | Hợp lệ? | Ý nghĩa |
| --- | --- | --- |
| `volatile boolean stop` | Có | Cờ giao tiếp giữa luồng |
| `transient String sessionToken` | Có | Bỏ qua trong default serialization |
| `transient volatile int runtimeCounter` | Có | Volatile lúc chạy, transient khi ghi stream |
| `static volatile boolean stopAll` | Có | Cờ chia sẻ ở cấp class giữa luồng |
| `static transient String cache` | Có | `transient` không thay đổi việc static vốn đã không được serialize mặc định |
| `final transient String localKey` | Có | Hợp lệ, nhưng cần cân nhắc cách phục hồi dữ liệu sau deserialize |
| `final volatile int count` | **Không** | Lỗi biên dịch: Java cấm kết hợp `final` và `volatile` |

## 5. Đọc các cờ trên `Field` bằng Reflection

Lớp `java.lang.reflect.Field` cung cấp `getModifiers()`. Kết hợp với `java.lang.reflect.Modifier`:

~~~java
Field f = SomeType.class.getDeclaredField("state");
int mask = f.getModifiers();

boolean isVolatile  = Modifier.isVolatile(mask);
boolean isTransient = Modifier.isTransient(mask);
boolean isStatic    = Modifier.isStatic(mask);
boolean isFinal     = Modifier.isFinal(mask);
String labels       = Modifier.toString(mask);
~~~

Đoạn trên là **minh họa**, cần import `java.lang.reflect.Field` và `java.lang.reflect.Modifier`, đồng thời `SomeType` phải có field tên `state`; `getDeclaredField` có thể ném `NoSuchFieldException`.

Có thể tự kiểm tra bit:

~~~java
boolean isVolatile = (f.getModifiers() & Modifier.VOLATILE) != 0;
boolean isTransient = (f.getModifiers() & Modifier.TRANSIENT) != 0;
~~~

Tuy nhiên, dùng `Modifier.isVolatile` và `Modifier.isTransient` thường giúp người đọc hiểu rõ ý định hơn. **Không** viết `mask == Modifier.VOLATILE` vì một field có thể đồng thời chứa cờ `private`, `static` hoặc `transient`.

### `getDeclaredFields()` và `getFields()`

- `getDeclaredField(name)` / `getDeclaredFields()` lấy field **được khai báo trực tiếp** trong class/interface đang xét, bao gồm cả private; **không** lấy field kế thừa.
- `getField(name)` / `getFields()` tìm field **public** có thể truy cập qua kiểu, bao gồm các public field kế thừa theo quy tắc Reflection.
- Thứ tự mảng do `getDeclaredFields()` hoặc `getFields()` trả về **không được API bảo đảm** theo thứ tự nguồn; muốn báo cáo ổn định, hãy sort theo tên/khóa phù hợp.
- Đọc **metadata modifier** với `getModifiers()` không yêu cầu `setAccessible(true)`. Truy cập **giá trị thực** qua `Field.get` / `Field.set` là thao tác khác, phụ thuộc kiểm tra quyền, giới hạn module và các điều kiện của field.
- Field do compiler tạo có thể mang cờ `synthetic`; `field.isSynthetic()` giúp phân biệt với thành viên do người viết khai báo. Không tự mặc định mọi field reflect được đều thuộc mô hình dữ liệu nghiệp vụ.

### Reflection chỉ quan sát cờ, không thay đổi luật Java

`Modifier.isVolatile(mask)` trả true khi metadata có cờ đó; gọi hàm không làm một field bình thường biến thành volatile. Tương tự, `Modifier.isTransient(mask)` không trực tiếp thực hiện serialize hay quyết định toàn bộ chính sách của thư viện serialization khác.

Một công cụ audit có thể duyệt metadata để cảnh báo: field đánh dấu `volatile` nhưng bị tăng `++` từ nhiều luồng, field cần loại khỏi object stream nhưng quên `transient`, hoặc field `transient` chưa có chiến lược khởi tạo lại. **Chỉ từ reflection metadata chưa đủ để chứng minh** chương trình đang có race condition hoặc an toàn bảo mật; phải xét cách code truy cập field và cơ chế serialize thực tế.

## 6. Ví dụ hoàn chỉnh: kiểm tra modifier và serialize/deserialize (Java 17+)

Tạo tệp **`FieldModifierDemo.java`** với toàn bộ mã sau. Chương trình phản ánh đúng tác dụng của `volatile` và `transient`, chứng minh hai cờ có thể tồn tại đồng thời, đồng thời sử dụng Java serialization mặc định để quan sát giá trị được phục hồi.

~~~java
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class FieldModifierDemo {
    static final class State implements Serializable {
        private static final long serialVersionUID = 1L;
        volatile boolean ready = true;
        transient String token = "temporary";
        transient volatile int cacheHits = 7;
        String name = "lesson";
        static String region = "before";
    }

    public static void main(String[] args) throws Exception {
        for (String name : new String[] {"ready", "token", "cacheHits", "name", "region"}) {
            Field field = State.class.getDeclaredField(name);
            int modifiers = field.getModifiers();
            System.out.printf("%s: volatile=%s, transient=%s, static=%s%n",
                    name,
                    Modifier.isVolatile(modifiers),
                    Modifier.isTransient(modifiers),
                    Modifier.isStatic(modifiers));
        }

        State original = new State();
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(data)) {
            out.writeObject(original);
        }

        State.region = "after";
        State restored;
        try (ObjectInputStream in =
                     new ObjectInputStream(new ByteArrayInputStream(data.toByteArray()))) {
            restored = (State) in.readObject();
        }

        System.out.println("restored.ready=" + restored.ready);
        System.out.println("restored.name=" + restored.name);
        System.out.println("restored.token=" + restored.token);
        System.out.println("restored.cacheHits=" + restored.cacheHits);
        System.out.println("State.region=" + State.region);
    }
}
~~~

Lệnh chạy (JDK 17 trở lên):

~~~bash
javac --release 17 FieldModifierDemo.java
java FieldModifierDemo
~~~

**Stdout chính xác**:

~~~text
ready: volatile=true, transient=false, static=false
token: volatile=false, transient=true, static=false
cacheHits: volatile=true, transient=true, static=false
name: volatile=false, transient=false, static=false
region: volatile=false, transient=false, static=true
restored.ready=true
restored.name=lesson
restored.token=null
restored.cacheHits=0
State.region=after
~~~

Giải thích:

1. `ready` có modifier `volatile`, **không** có `transient`; `true` được lưu và khôi phục mặc định. `volatile` không làm field bị bỏ khỏi stream.
2. `token` có `transient`, nên chuỗi `"temporary"` không được lưu trong default serialized state, và giá trị sau khôi phục là `null`.
3. `cacheHits` kết hợp **`transient volatile`**; reflection thấy cả hai cờ, nhưng giá trị khởi tạo `7` không được phục hồi, do đó thành `0`.
4. `name` là field instance thông thường nên được khôi phục thành `"lesson"`.
5. `region` là static field. Việc đổi `"before"` thành `"after"` sau khi ghi stream vẫn được giữ lại vì serialization này chỉ lưu **instance state**, không khôi phục static state.
6. Mảng tên field cố định giúp stdout có thứ tự rõ ràng; chương trình không phụ thuộc thứ tự không được bảo đảm của `getDeclaredFields()`.

Ví dụ đã biên dịch bằng `javac --release 17` và chạy với JDK tương thích. Đoạn demo serialization chỉ phục vụ hiểu ngữ nghĩa; không nên coi `ObjectInputStream` trên dữ liệu không tin cậy là thao tác vô hại.

## 7. Lỗi thường gặp và cách kiểm tra đúng

**Nhầm visibility với serialization.** `volatile` không ẩn field khỏi serialized stream. `transient` không cung cấp giao tiếp an toàn giữa các luồng.

**Dùng `volatile` cho biến đếm và tưởng đã atomic.** Mỗi lần đọc/ghi volatile là thao tác riêng; `++`, kiểm tra-đổi hoặc cập nhật nhiều field vẫn cần cơ chế đồng bộ/atomic phù hợp.

**Cho rằng `volatile` trên tham chiếu làm object bên trong thread-safe.** Chỉ truy cập tham chiếu volatile có ràng buộc JMM; cập nhật bên trong object mutable phải được thiết kế riêng.

**Dựa vào field initializer của Serializable sau deserialization.** `transient int retry = 3` có thể trở thành `0`; phải khởi tạo lại khi cần.

**Cho rằng `transient` áp dụng với mọi định dạng lưu trữ.** JSON serializer, ORM và thư viện mapping có quy tắc riêng; hãy kiểm tra tài liệu của cơ chế thực tế và thử nghiệm dữ liệu đầu ra.

**Quên static field.** Static không thuộc trạng thái instance được Java default serialization lưu. `static volatile` vẫn rất hữu ích cho chia sẻ tín hiệu giữa luồng; `static transient` thường không có thêm ý nghĩa đối với cơ chế mặc định.

**So sánh nguyên số bitmask.** `field.getModifiers() == Modifier.TRANSIENT` không đúng khi field còn chứa cờ khác. Dùng `Modifier.isTransient(mask)` hoặc kiểm tra bitwise-AND khác 0.

**Cho rằng `getDeclaredFields()` chỉ lấy field public hoặc trả về đúng thứ tự mã nguồn.** Nó lấy cả private trực tiếp, và thứ tự không cố định; sắp xếp trước khi trình bày dữ liệu.

**Chỉ nhìn tên và không kiểm tra field thật.** Hai class khác nhau có thể cùng tên field nhưng modifier và hành vi khác nhau; reflection cần token class chính xác và xét metadata của field cụ thể.

## 8. Gợi ý lựa chọn trong thực tế

Khi thiết kế một **cờ dừng hoặc thông báo trạng thái nhỏ giữa các luồng**, `volatile` phù hợp nếu chỉ cần phát và đọc trạng thái theo thứ tự, không yêu cầu read-modify-write nhiều bước. Khi cần **bộ đếm atomic hoặc thống nhất nhiều field**, ưu tiên `AtomicInteger`, khóa hoặc cấu trúc concurrent phù hợp. Khi cần **loại cache tạm/thông tin phiên khỏi Java serialized state mặc định**, dùng `transient` và xác định cách khởi tạo lại sau deserialize. Khi cần **cả hai yêu cầu**, `transient volatile` hợp lệ và không mâu thuẫn.

Trong công cụ reflection, hãy đi từ **`Class<?>` → `Field` → `getModifiers()` → các hàm `Modifier.is...`**. Metadata cho biết cách field được khai báo; muốn suy luận tính đúng đắn phải kết hợp thêm **luồng truy cập, Java Memory Model, serialization protocol và hành vi framework**.

### Tài liệu đối chiếu

- [JLS §8.3.1 — Field Modifiers](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.3.1) và [JLS §17.4 — Memory Model](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4).
- [Java SE 21 — `java.lang.reflect.Field`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Field.html), [`Modifier`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Modifier.html).
- [Java SE 21 — Object Serialization Specification](https://docs.oracle.com/en/java/javase/21/docs/specs/serialization/serial-arch.html) và [`ObjectOutputStream`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/ObjectOutputStream.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="modifiers-flags-006">isSynthetic và cấu trúc do trình biên dịch sinh</a>

<details>
<summary>Xem chi tiết</summary>

# `isSynthetic()` và các cấu trúc do trình biên dịch sinh trong Java Reflection

## 1. `synthetic` nghĩa là gì?

Khi biên dịch Java, trình biên dịch đôi khi phải tạo thêm class, field, method hoặc thành phần khác để thực hiện đúng ngữ nghĩa Java trên JVM. Một số thành phần này không tương ứng với khai báo trực tiếp hoặc ngầm định trong mã nguồn: chúng được gọi là **synthetic** và được đánh dấu trong dữ liệu của class file.

Qua Reflection, `isSynthetic()` giúp phân biệt những cấu trúc được đánh dấu *synthetic* với cấu trúc thông thường. Phương thức trả `boolean`:

- `true`: cấu trúc được class file/JVM phản ánh là synthetic.
- `false`: không có chỉ dấu synthetic tương ứng; **không đồng nghĩa** với “chắc chắn do lập trình viên tự gõ”. Java có những thành phần được sinh ra nhưng hợp lệ ở cấp ngôn ngữ và không được đánh dấu synthetic.

Ví dụ điển hình: để bảo đảm một phương thức ghi đè sau **type erasure** vẫn được gọi đúng, `javac` có thể sinh ra một *bridge method* không viết trong mã nguồn. Ngược lại, constructor mặc định do compiler cấp khi class không khai báo constructor thường **không synthetic**. Vì vậy không thể dùng `isSynthetic()` làm bộ phát hiện tuyệt đối mọi thứ do compiler tạo ra.

**Phạm vi quan trọng:** `isSynthetic()` phản ánh đặc tính ở mức **binary/runtime**, không kiểm tra văn bản mã nguồn và không phân tích body của một method. Tên có ký tự `$` cũng không đủ chứng minh một thành phần là synthetic.

## 2. Gọi `isSynthetic()` ở đâu?

| Thành phần Reflection | API | Dùng để hỏi |
| --- | --- | --- |
| Kiểu (class/interface) | `Class<?>.isSynthetic()` | Bản thân kiểu có synthetic modifier hay không? |
| Trường dữ liệu | `Field.isSynthetic()` | Trường đó có được đánh dấu synthetic không? |
| Phương thức | `Method.isSynthetic()` | Phương thức đó có được đánh dấu synthetic không? |
| Constructor | `Constructor<?>.isSynthetic()` | Constructor đó có được đánh dấu synthetic không? |
| Tham số được mô tả qua Reflection | `Parameter.isSynthetic()` | Tham số có phải thành phần synthetic không? |

`Field`, `Method`, `Constructor` đều triển khai giao diện `java.lang.reflect.Member`, giao diện có `isSynthetic()`; `Class` và `Parameter` cung cấp phương thức riêng. Với các method, `Method.isBridge()` còn kiểm tra một tính chất khác: phương thức có phải *bridge method* hay không.

Để quan sát các phần tử compiler có thể thêm, dùng các API **declared**:

~~~java
Class<?> type = SomeType.class;
var methods = type.getDeclaredMethods();
var fields = type.getDeclaredFields();
var constructors = type.getDeclaredConstructors();

for (var m : methods) {
    System.out.println(m.getName() + ": " + m.isSynthetic());
}
~~~

Đoạn này chỉ minh họa cách sử dụng Reflection (`SomeType` là tên lớp phải thay bằng class có thật); nó **không phải chương trình độc lập**. `getDeclaredMethods()` nhìn các method khai báo trong binary của chính class, bao gồm cả private và synthetic nếu có. `getMethods()` chỉ xét các public method của kiểu và các phương thức public kế thừa, nên có thể bỏ sót method synthetic không public. Tương tự, `getDeclaredFields()` hữu ích hơn `getFields()` khi kiểm tra các field do compiler sinh.

## 3. Ví dụ đầy đủ: bridge method, default constructor, anonymous class

Lưu chương trình Java 17+ dưới đây thành `SyntheticReflectionDemo.java`:

~~~java
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;

public class SyntheticReflectionDemo {
    static class Box<T> {
        T value() {
            return null;
        }
    }

    static class TextBox extends Box<String> {
        @Override
        String value() {
            return "hello";
        }
    }

    static class Outer {
        private int seed = 7;

        class Inner {
            int read() {
                return seed;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Arrays.stream(TextBox.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("value"))
                .sorted(Comparator.comparing(
                        m -> m.getReturnType().getSimpleName()))
                .forEach(m -> System.out.printf(
                        "return=%s, bridge=%s, synthetic=%s%n",
                        m.getReturnType().getSimpleName(),
                        m.isBridge(), m.isSynthetic()));

        System.out.println("default constructor synthetic: "
                + TextBox.class.getDeclaredConstructor().isSynthetic());

        Class<?> anonymous = new Runnable() {
            @Override
            public void run() { }
        }.getClass();

        System.out.println("anonymous: " + anonymous.isAnonymousClass()
                + ", synthetic: " + anonymous.isSynthetic());

        for (Field field : Outer.Inner.class.getDeclaredFields()) {
            if (field.isSynthetic()) {
                System.out.println("synthetic inner field: " + field.getName());
            }
        }
    }
}
~~~

Biên dịch bằng `javac` và chạy:

~~~bash
javac --release 17 SyntheticReflectionDemo.java
java SyntheticReflectionDemo
~~~

Kết quả quan sát được với `javac` và JDK hỗ trợ Java 17 trở lên (tên field do compiler sinh là chi tiết triển khai, xem bên dưới):

~~~text
return=Object, bridge=true, synthetic=true
return=String, bridge=false, synthetic=false
default constructor synthetic: false
anonymous: true, synthetic: false
synthetic inner field: this$0
~~~

### Phân tích bridge method

`Box<T>.value()` sau khi xóa kiểu generic có mô tả JVM tương ứng với `Object value()`. Trong `TextBox`, phương thức ghi đè mà lập trình viên viết là `String value()`. Để giữ hành vi gọi đa hình tương thích với dạng đã xóa kiểu, `javac` còn phát sinh một phương thức chuyển tiếp gần tương đương ý tưởng sau:

~~~java
// Mô tả ý tưởng ở mức bytecode; KHÔNG phải mã nguồn Java hợp lệ
// để khai báo cùng lớp với String value().
Object value() {
    return value(); // ủy quyền tới phương thức trả String
}
~~~

Phần minh họa trên **không thể dán làm method thứ hai trong Java source**: ngôn ngữ Java không cho hai method cùng tên và danh sách tham số chỉ khác kiểu trả về. JVM phân biệt method descriptor gồm cả kiểu trả về và compiler có thể sinh bridge. Ở binary, phương thức trả `Object` mang các cờ `ACC_BRIDGE` và `ACC_SYNTHETIC`. Do đó Reflection báo `isBridge() == true` và `isSynthetic() == true` cho phương thức chuyển tiếp; method `String value()` vốn khai báo trong source có cả hai kết quả `false`.

**Tác dụng thực tế:** Khi tìm method có thể dùng làm endpoint, handler hoặc mô tả API, đừng mù quáng xem tất cả method từ `getDeclaredMethods()` là những khai báo độc lập trong source. Bridge có thể dẫn đến hai `Method` cùng tên và cùng tham số nhưng kiểu trả về khác nhau. Cần chọn đúng method nghiệp vụ và cân nhắc loại bridge/synthetic nếu bài toán đòi hỏi khai báo do người viết.

### Phân tích constructor và anonymous class

- `TextBox` không khai báo constructor, nhưng compiler tạo constructor mặc định; `getDeclaredConstructor().isSynthetic()` trả `false` trong ví dụ. Điều này cho thấy **compiler-generated không luôn đồng nghĩa synthetic**.
- Cú pháp `new Runnable() { ... }` thật sự khai báo một anonymous class trong mã nguồn. Reflection nhận ra `isAnonymousClass() == true`, còn `isSynthetic() == false` theo chương trình `javac` này. `isAnonymousClass()`, `isLocalClass()`, `isMemberClass()` và `isSynthetic()` trả lời các câu hỏi khác nhau.
- `Outer.Inner` đọc `seed` của outer object, nên `javac` trong ví dụ lưu tham chiếu outer vào một field tổng hợp thường đặt tên `this$0`. Tên và cách triển khai field này **không phải hợp đồng ngôn ngữ**; compiler/JDK khác có thể tạo cấu trúc khác. Từ các bản Java hiện đại, compiler còn có thể bỏ field outer khi không cần giữ tham chiếu. Phải xem `Field.isSynthetic()` thay vì đoán từ tên.

## 4. Những cấu trúc synthetic thường gặp

| Tình huống | Cấu trúc có thể xuất hiện | Cách kiểm tra và lưu ý |
| --- | --- | --- |
| Override phương thức generic sau type erasure | Bridge method | `Method.isBridge()` và `Method.isSynthetic()`; có ví dụ xác định ở trên. |
| Ghi đè với kiểu trả về covariant | Bridge method khi binary cần giữ descriptor gốc | Xét `isBridge()`; không phải mọi override đều cần bridge. |
| Inner/local class dùng trạng thái từ outer hoặc biến cục bộ được capture | Field giữ tham chiếu/giá trị captured | `Field.isSynthetic()`; sự tồn tại và tên field phụ thuộc compiler. |
| Lambda expression | Cấu trúc hỗ trợ do compiler/JVM tạo, ví dụ method bổ trợ hoặc lớp runtime | Không dựa vào tên `lambda$...`, số lượng method hay hình dạng class như giao kèo ổn định. |
| Truy cập private giữa các lớp lồng nhau | Trình biên dịch cũ có thể thêm accessor tổng hợp | Từ Java 11 có cơ chế nest-based access control; không mặc định chờ phương thức `access$...` xuất hiện. |
| Enum và record | Một số member ngầm định, cùng các chi tiết triển khai khác | Member ngầm định hợp lệ ở cấp ngôn ngữ không nhất thiết synthetic; xét từng member cụ thể thay vì gắn nhãn toàn bộ. |

Phân biệt **quy tắc ngôn ngữ** và **chiến lược hiện thực của compiler**. Các tên như `this$0`, `val$...`, `lambda$...`, `$VALUES` phổ biến trong bytecode do `javac` sinh, nhưng không có cam kết rằng mọi JDK hoặc compiler khác đều dùng đúng các tên này.

### Với tham số constructor/method

`Executable.getParameters()` trả các đối tượng `Parameter`. Ba loại cần phân biệt:

1. **Explicit:** tham số được viết rõ trong khai báo nguồn, ví dụ `void send(String text)`.
2. **Implicit (mandated):** tham số ngầm định theo quy tắc ngôn ngữ; kiểm tra `Parameter.isImplicit()`.
3. **Synthetic:** tham số không được khai báo tường minh hay ngầm định ở cấp source; kiểm tra `Parameter.isSynthetic()`.

Ví dụ, một inner class không static thường cần truyền tham chiếu tới đối tượng outer khi khởi tạo. Tham số bổ sung đó là thành phần constructor ở cấp binary, nhưng việc Reflection ghi nhận `isImplicit()`/`isSynthetic()` phụ thuộc metadata và cách compiler sinh class file. Khi cần kiểm tra thông tin tham số, cân nhắc biên dịch với `javac -parameters` để giữ metadata `MethodParameters`; đừng suy ra mọi tham số bổ sung đều synthetic, cũng đừng coi kết quả `false` khi thiếu metadata là bằng chứng về source.

## 5. Liên hệ cờ class file và `Modifier`

JVM class file có cờ `ACC_SYNTHETIC` (giá trị hex `0x1000`) dành cho class/field/method tương ứng, và cờ `ACC_BRIDGE` (`0x0040`) cho method. Class file cũng có thể biểu diễn synthetic bằng thuộc tính `Synthetic` theo quy tắc của phiên bản class file.

Đây là các khái niệm từ **class file format**, không đơn giản là các từ khóa mà có thể viết trong Java source. Thư viện `java.lang.reflect.Modifier` chủ yếu diễn giải những modifier thông thường (public/private/static/final...); API Reflection có `isSynthetic()` và `isBridge()` riêng, nên hãy sử dụng các phương thức đó thay vì tự diễn giải magic bit từ `getModifiers()` để kết luận về synthetic.

Có thể đối chiếu với bytecode của chương trình ví dụ:

~~~bash
javap -v 'SyntheticReflectionDemo$TextBox'
~~~

Dùng dấu nháy quanh tên có `$` để tránh shell thay thế ký tự này (đặc biệt trong Bash). Trong output, tìm hai method tên `value`:

- Phương thức trả `java.lang.String` được viết trong mã nguồn.
- Bridge trả `java.lang.Object` có dòng flags chứa `ACC_BRIDGE, ACC_SYNTHETIC`.

Khi kiểm tra class file thực tế, công cụ `javap -v` giúp xác minh dấu cờ và descriptor, còn Reflection giúp xử lý metadata của class được nạp lúc chạy. Không dùng `getName().contains("$")` để thay cho `isSynthetic()`: tên không phải bộ phân loại đáng tin cậy.

## 6. Dùng `isSynthetic()` khi xây framework/tool

Một tình huống thường gặp là thu thập các method do lập trình viên khai báo để dựng metadata. Có thể bắt đầu bằng bộ lọc sau:

~~~java
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

static List<Method> userFacingMethods(Class<?> type) {
    return Arrays.stream(type.getDeclaredMethods())
            .filter(m -> !m.isSynthetic())
            .filter(m -> !m.isBridge())
            .toList();
}
~~~

Đây là **đoạn method tiện ích**, cần đặt trong một class Java và có các import tương ứng; `toList()` có từ Java 16. Nó trả về method khai báo trong binary của class đã qua hai tiêu chí lọc, chứ **không khôi phục chính xác tập phương thức có mặt trong mã nguồn**. Method do compiler sinh nhưng không synthetic vẫn có thể lọt qua; method synthetic thực sự cần thiết cho công cụ phân tích bytecode lại bị loại.

Quy trình lựa chọn tùy mục tiêu:

- **Tạo danh sách API nghiệp vụ:** lọc hoặc xử lý riêng bridge/synthetic, rồi xét visibility, annotation, override và quan hệ kế thừa theo yêu cầu thực tế. Không chỉ dựa vào `isSynthetic()`.
- **Phân tích bytecode, debug, profiler, serializer cấp thấp:** có thể **cần giữ** các member synthetic vì chúng thể hiện cấu trúc runtime đang tồn tại.
- **Đối chiếu phương thức sau type erasure:** xem cả `getDeclaredMethods()`, `isBridge()`, `getReturnType()` và signature; đừng suy ra một `Method` Reflection luôn tương ứng một khai báo Java nguồn.

## 7. Lỗi thường gặp và bài tự kiểm tra

1. **“Mọi compiler-generated đều synthetic.”** Sai: constructor mặc định, một số member ngầm định của enum/record và class do cú pháp ngôn ngữ tạo ra là phản ví dụ quan trọng.
2. **“Synthetic nghĩa là không gọi được.”** Sai: bridge synthetic vẫn có vai trò thực thi, có thể được gọi khi JVM điều phối dispatch.
3. **“Tên chứa `$` chắc chắn synthetic.”** Sai: `$` có thể xuất hiện trong tên Java hợp lệ và compiler không bắt buộc giữ quy tắc tên nội bộ.
4. **“Một method source = một method Reflection.”** Không luôn đúng: bridge tạo thêm phần tử trong `getDeclaredMethods()`.
5. **“`getMethods()` đủ để soi các thành phần compiler sinh.”** Không luôn đúng: các thành phần private/package thường cần `getDeclaredMethods()` hoặc `getDeclaredFields()`.
6. **“Mọi phiên bản javac sinh cùng accessor/field/lambda method.”** Không có bảo đảm: kiến trúc nestmates, phiên bản class file và tối ưu hóa của compiler có thể làm kết quả khác.
7. **“`isSynthetic() == false` khẳng định member xuất hiện tường minh trong source.”** Sai: đó chỉ là thiếu chỉ dấu synthetic, không chứng minh nguồn gốc.

**Tự kiểm tra:** Thay `TextBox extends Box<String>` thành `TextBox extends Box<Integer>` và override `Integer value()`; dự đoán các method `value()` Reflective sẽ có kiểu trả về nào và method nào là bridge. Sau đó thử loại `isBridge()` trong bộ lọc để thấy method `Object value()` trở lại danh sách.

**Tài liệu chuẩn:**
- [Java SE 17 — `Class.isSynthetic()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#isSynthetic())
- [Java SE 17 — `Method.isBridge()` và `Method.isSynthetic()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html)
- [Java SE 17 — `Parameter`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Parameter.html)
- [JLS 17 §13.1 — The Form of a Binary](https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html#jls-13.1)
- [JVMS 17 — Class File Format](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="modifiers-flags-007">Bridge method và isBridge</a>

<details>
<summary>Xem chi tiết</summary>

# Bridge method và `Method.isBridge()` trong Java Reflection

## 1. Bridge method là gì và tại sao cần quan tâm?

**Bridge method** là phương thức bổ sung do trình biên dịch Java tạo ra trong một số trường hợp để giữ đúng quy tắc ghi đè phương thức (overriding) ở mức mã máy ảo JVM. Nó đặc biệt xuất hiện khi **generic type erasure** làm chữ ký bytecode của phương thức ở lớp con khác với chữ ký mà mã đã biên dịch hoặc interface gốc cần gọi, và khi **covariant return type** làm kiểu trả về của phương thức ghi đè trở nên cụ thể hơn.

Từ mã nguồn Java, ta chỉ thấy phương thức do lập trình viên khai báo. Nhưng ở runtime, Java Reflection có thể nhìn thấy **cả phương thức thật lẫn bridge method do compiler sinh**. Nếu không nhận diện đúng, công cụ quét API có thể hiển thị hai phương thức tưởng như trùng nhau, bộ lọc annotation có thể xử lý sai, hoặc framework chọn nhầm phương thức để phản chiếu.

Phương thức `java.lang.reflect.Method.isBridge()` cho biết một `Method` có được đánh dấu là **bridge method** hay không. Đây là một **cờ của phương thức**, vì vậy nội dung này thuộc nhóm học về *Modifier và cờ* trong Reflection.

**Kết quả học tập:** hiểu lý do bridge tồn tại, nhìn được bridge trong bytecode và Reflection, phân biệt `isBridge()` với `isSynthetic()`, xử lý đúng khi quét phương thức và biết khi nào cần giữ hoặc loại bridge.

## 2. Cơ chế từ Java source đến JVM

Ở cấp độ mã nguồn Java, generic giúp kiểm tra kiểu lúc biên dịch:

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

Đoạn trên **minh họa**, chưa phải chương trình độc lập vì chưa có phương thức `main`. Người đọc Java thấy một lời cài đặt `accept(String)` hợp lệ của `Sink<String>`.

Nhưng generic trong Java chủ yếu dựa trên **type erasure**. Sau khi xóa biến kiểu không có bound, chữ ký của phương thức khai báo trong `Sink<T>` trở thành:

```text
Sink.accept(T)              -> bytecode descriptor: (Ljava/lang/Object;)V
StringSink.accept(String)    -> bytecode descriptor: (Ljava/lang/String;)V
```

JVM liên kết lời gọi dựa trên tên và descriptor; descriptor của phương thức trong JVM bao gồm các kiểu tham số và kiểu trả về. Vì vậy, chỉ phương thức `accept(String)` không cung cấp đúng entry point `accept(Object)` mà lời gọi thông qua kiểu `Sink` đã biên dịch có thể sử dụng.

Để duy trì hợp đồng ghi đè, `javac` tạo thêm phương thức tương đương về ý tưởng:

```java
// Mã mô phỏng hành vi bridge do javac sinh, KHÔNG được tự khai báo
// song song với accept(String) trong ví dụ để thay compiler.
public void accept(Object value) {
    accept((String) value);
}
```

Bridge chuyển tiếp lời gọi tới phương thức `accept(String)` và chèn phép ép kiểu cần thiết. Bridge giữ tính đa hình của lời gọi qua interface; nó không làm thay đổi ý nghĩa kiểu generic được kiểm tra ở thời điểm biên dịch.

**Lưu ý:** `accept(Object)` ở trên chỉ là cách giải thích chức năng. Trong mã nguồn `StringSink` ta **không viết** phương thức đó. Trình biên dịch phát sinh bridge vào bytecode với cờ `ACC_BRIDGE`, và với `javac` thường thêm cả `ACC_SYNTHETIC`.

### 2.1. Trường hợp thứ hai: kiểu trả về hiệp biến

Java cho phép phương thức ghi đè trả về một subtype của kiểu trả về ở lớp cha:

```java
class Parent {
    public Number read() {
        return 1;
    }
}

class Child extends Parent {
    @Override
    public Integer read() {
        return 2;
    }
}
```

Ở mức source, `read()` là phương thức ghi đè hợp lệ. Nhưng descriptor của JVM chứa **cả kiểu trả về**:

```text
Parent.read()  -> ()Ljava/lang/Number;
Child.read()   -> ()Ljava/lang/Integer;
```

Để lời gọi được liên kết theo descriptor `()Ljava/lang/Number;` vẫn tới phần cài đặt của `Child`, `javac` có thể tạo bridge `Number read()` gọi đến `Integer read()`. Hai phương thức cùng tên và cùng tham số nhưng khác kiểu trả về có thể đồng tồn tại trong bytecode do compiler sinh; **không được khai báo hai phương thức chỉ khác kiểu trả về trong mã nguồn Java thông thường**.

Như vậy, bridge **không chỉ xuất hiện vì generic**. Khi phân tích Reflection, hãy xét cả quan hệ ghi đè với kiểu trả về hiệp biến.

## 3. Các API và cờ liên quan

| API / thành phần | Ý nghĩa |
| --- | --- |
| `Method.isBridge()` | `true` khi phương thức mang thuộc tính bridge; đây là API Reflection trực tiếp để nhận diện bridge. |
| `Method.isSynthetic()` | `true` khi phương thức được đánh dấu synthetic (không xuất hiện như một khai báo nguồn thông thường); synthetic **không đồng nghĩa** bridge. |
| `Method.getModifiers()` | Lấy tập bit modifier của phương thức; nên dùng `isBridge()` để hỏi riêng về bridge. |
| `Class.getDeclaredMethods()` | Liệt kê các phương thức khai báo trong chính kiểu, bao gồm cả bridge do compiler sinh; không tự bao gồm phương thức chỉ được kế thừa. |
| `Class.getMethods()` | Liệt kê các phương thức `public` nhìn thấy trên kiểu, bao gồm phương thức được kế thừa; có thể chứa bridge phù hợp điều kiện. |
| `Method.getParameterTypes()` / `getReturnType()` | Đọc kiểu runtime của tham số/giá trị trả về, hữu ích để so sánh bridge và phương thức đích. |
| `Method.getGenericParameterTypes()` / `getGenericReturnType()` | Đọc metadata kiểu generic được biểu diễn bởi `Type`; cần hiểu generic và erasure khi phân tích. |
| `javap -p -c -v` | Công cụ JDK xem phương thức, descriptor, cờ `ACC_BRIDGE`/`ACC_SYNTHETIC` và lệnh bytecode. |

Có hai phân biệt quan trọng:

- **Bridge là một loại nhiệm vụ chuyên biệt**: tạo đường chuyển tiếp để bảo toàn ngữ nghĩa ghi đè/liên kết nhị phân sau quá trình biên dịch. **Synthetic là thuộc tính rộng hơn**: compiler có thể sinh các phương thức phụ trợ khác không phải bridge. Vì vậy, điều kiện `isSynthetic()` đơn lẻ **không xác định chính xác** bridge.
- `Modifier` cung cấp các phép hỏi phổ biến như `isPublic`, `isStatic`, `isFinal`, nhưng **không có `Modifier.isBridge(...)`**. Hơn nữa, một số bit class-file có ý nghĩa khác nhau theo loại thành viên: không nên tự giải mã bit của `getModifiers()` bằng những phép kiểm tra vốn dành cho field. Với đối tượng `Method`, sử dụng `method.isBridge()`.

## 4. Ví dụ hoàn chỉnh Java 17+: quan sát bridge bằng Reflection

Lưu mã nguồn sau thành **`BridgeMethodDemo.java`**:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

public class BridgeMethodDemo {
    interface Sink<T> {
        void accept(T value);
    }

    static class StringSink implements Sink<String> {
        @Override
        public void accept(String value) {
            System.out.println("accept(String): " + value);
        }
    }

    static class Parent {
        public Number read() {
            return 1;
        }
    }

    static class Child extends Parent {
        @Override
        public Integer read() {
            return 2;
        }
    }

    private static String signature(Method method) {
        String parameters = Arrays.stream(method.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "));
        return method.getName() + "(" + parameters + ") -> "
                + method.getReturnType().getSimpleName();
    }

    private static void inspect(Class<?> type, String methodName) {
        System.out.println(type.getSimpleName() + ":");
        Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName))
                .sorted(Comparator.comparing(BridgeMethodDemo::signature))
                .forEach(method -> System.out.println(
                        "  " + signature(method)
                                + " | bridge=" + method.isBridge()
                                + " | synthetic=" + method.isSynthetic()));
    }

    public static void main(String[] args) throws Exception {
        inspect(StringSink.class, "accept");
        inspect(Child.class, "read");

        Method bridge = StringSink.class.getDeclaredMethod("accept", Object.class);
        System.out.println("invoke accepted:");
        bridge.invoke(new StringSink(), "hello");
        try {
            bridge.invoke(new StringSink(), 42);
        } catch (InvocationTargetException exception) {
            System.out.println("cause: "
                    + exception.getCause().getClass().getSimpleName());
        }
    }
}
```

Chạy bằng JDK 17+:

```bash
javac --release 17 BridgeMethodDemo.java
java BridgeMethodDemo
```

Kết quả với `javac` và Java 17+:

```text
StringSink:
  accept(Object) -> void | bridge=true | synthetic=true
  accept(String) -> void | bridge=false | synthetic=false
Child:
  read() -> Integer | bridge=false | synthetic=false
  read() -> Number | bridge=true | synthetic=true
invoke accepted:
accept(String): hello
cause: ClassCastException
```

Ví dụ này đã được biên dịch với tùy chọn `--release 17` và chạy trên JDK 21; stdout trên khớp từng dòng.

### 4.1. Phân tích `StringSink`

`getDeclaredMethods()` thấy hai phương thức `accept`:

1. `accept(String)`: phương thức nguồn thật do lập trình viên viết; `isBridge() == false`.
2. `accept(Object)`: bridge của `javac` để tương thích với descriptor đã xóa kiểu của `Sink<T>`; `isBridge() == true` và `isSynthetic() == true`.

Cả hai đều có thể tìm được qua Reflection bằng danh sách kiểu tham số thích hợp:

```java
Method source = StringSink.class.getDeclaredMethod("accept", String.class);
Method bridge = StringSink.class.getDeclaredMethod("accept", Object.class);
```

Đoạn tra cứu này chỉ minh họa, dùng các kiểu `StringSink` và `Method` đã khai báo/import trong chương trình phía trên.

Cách sắp xếp các phương thức bằng `Comparator` trong hàm `inspect` là cố ý: **`Class.getDeclaredMethods()` không đảm bảo thứ tự kết quả**. Không dựa vào vị trí đầu tiên của mảng để kết luận phương thức nào là bridge.

### 4.2. Phân tích `Child`

Ở lớp `Child`, tên và danh sách tham số không giúp phân biệt hai `read()`. Cần xét `getReturnType()` và cờ bridge. Phương thức `read() -> Integer` là phương thức do người dùng khai báo; phương thức `read() -> Number` là bridge do compiler sinh để duy trì liên kết với chữ ký ở `Parent`.

Đừng tự “khử trùng lặp” theo *chỉ* `method.getName()` hoặc tên cộng danh sách tham số khi phân tích các phương thức JVM: như vậy có thể gộp nhầm bridge với phương thức nguồn và đánh mất thông tin quan trọng về descriptor hoặc kiểu trả về.

### 4.3. Vì sao `ClassCastException` xuất hiện?

Gọi:

```java
bridge.invoke(new StringSink(), "hello");
```

là hợp lệ vì tham số runtime của bridge là `Object`; bên trong bridge, giá trị `String` ép kiểu thành công và được chuyển tới `accept(String)`.

Ngược lại, `bridge.invoke(new StringSink(), 42)` vẫn vượt qua bước kiểm tra tham số dạng `Object` của Reflection, nhưng bytecode bridge phải ép `Integer` sang `String`. Phép ép thất bại; `Method.invoke` bao bọc ngoại lệ do phương thức được gọi ném ra trong `InvocationTargetException`. Vì thế đoạn `catch` lấy `exception.getCause()` và in `ClassCastException`.

Đây cũng là lý do **không nên suy luận rằng gọi bridge qua Reflection sẽ an toàn kiểu giống như gọi generic method trong mã nguồn Java đã được biên dịch**.

## 5. Kiểm tra bytecode: xác minh cờ `ACC_BRIDGE`

Sau khi biên dịch `BridgeMethodDemo.java`, chạy:

```bash
javap -p -c -v 'BridgeMethodDemo$StringSink'
javap -p -c -v 'BridgeMethodDemo$Child'
```

Ví dụ lệnh dùng dấu nháy đơn để bảo vệ ký tự `$` trong tên binary của nested class khi chạy bằng shell như PowerShell hoặc Bash. Đọc phần mô tả method và tìm:

- `descriptor: (Ljava/lang/Object;)V` của `accept(Object)`.
- `flags: ... ACC_BRIDGE, ACC_SYNTHETIC` trên bridge do `javac` tạo.
- Lệnh ép kiểu `checkcast java/lang/String` trong bridge và lời gọi tiếp tới `accept:(Ljava/lang/String;)V`.
- Với `Child`, method trả về `Number` được đánh dấu bridge, rồi chuyển tiếp tới method trả về `Integer`.

Lệnh `javap` phục vụ mục tiêu **kiểm chứng biên dịch**, không phải bước bắt buộc khi viết ứng dụng Reflection. Khi cần hỏi một đối tượng `Method`, `isBridge()` dễ đọc và ít phụ thuộc chi tiết biểu diễn bit hơn.

Lưu ý về compiler: ngôn ngữ Java quy định tính tương thích khi ghi đè, nhưng chi tiết cách trình biên dịch sinh bridge, thứ tự các method trong class file và cờ synthetic bổ sung có thể khác theo compiler hoặc công cụ biến đổi bytecode. Vì vậy, đừng đưa giả định “mọi phương thức synthetic đều là bridge” hay “mỗi lớp có đúng một bridge” vào mã dùng chung.

## 6. Chọn phương thức khi quét bằng Reflection

### 6.1. Chỉ hiển thị API do người dùng khai báo

Khi sinh tài liệu hoặc bảng mô tả phương thức dễ đọc từ `getDeclaredMethods()`, thường chỉ muốn hiển thị phương thức chính và loại các đường chuyển tiếp bridge:

```java
Method[] visibleMethods = Arrays.stream(type.getDeclaredMethods())
        .filter(method -> !method.isBridge())
        .toArray(Method[]::new);
```

Đây là **đoạn minh họa**, giả sử có biến `Class<?> type` và import `Method`, `Arrays`; không phải chương trình độc lập. Nếu mục tiêu còn cần bỏ phương thức compiler sinh *khác*, có thể thêm quy tắc `!method.isSynthetic()`, nhưng cần xét ứng dụng có muốn giữ các phương thức synthetic hay không.

Không có một bộ lọc chung đúng cho mọi framework. Công cụ phân tích bytecode, debugger, trình biên dịch, AOP, mô hình gọi động hoặc giải quyết phương thức ghi đè có thể **cần giữ bridge** để hiểu đường dispatch thực tế.

### 6.2. Cần thực thi hoặc ánh xạ một phương thức runtime

Không mặc định rằng mọi bridge chỉ có một phương thức đích đơn giản suy ra bằng tên. Để ánh xạ một bridge tới phương thức nghiệp vụ, có thể phải xét:

- Lớp khai báo và chuỗi lớp cha/interface, không chỉ `getDeclaredMethods()` trên một lớp.
- Danh sách tham số, kiểu trả về và generic type variables sau erasure.
- Quy tắc override, visibility, các phương thức ứng viên và quan hệ covariant return.
- Compiler hoặc thư viện sinh/biến đổi bytecode, khi có.

**Ví dụ quyết định theo mục đích:**

| Nhu cầu | Xử lý bridge |
| --- | --- |
| Sinh tài liệu API người viết khai báo | Thường ẩn `isBridge() == true` để tránh mục lặp. |
| Khảo sát kết quả biên dịch/JVM | Giữ bridge và hiển thị cờ cùng descriptor. |
| Tìm method để xử lý annotation | Xác định method nguồn/đích và chính sách annotation trước khi lọc. |
| Kiểm tra tương thích ghi đè hoặc đường dispatch | Xem bridge là dữ liệu quan trọng. |
| Gọi method qua `Method.invoke` | Kiểm tra chữ ký của method được chọn và ngoại lệ có thể phát sinh trong bridge. |

### 6.3. `getDeclaredMethods()` so với `getMethods()`

`getDeclaredMethods()` tiện để quan sát bridge **sinh trực tiếp trong class đang xét**, kể cả phương thức không public. `getMethods()` chỉ trả về các public method có thể nhìn thấy, kể cả inherited methods, nên phạm vi khác và có thể khiến việc xác định method được khai báo ở đâu kém rõ ràng.

Nếu cần phân biệt bridge của lớp con và phương thức kế thừa, hãy kiểm tra `method.getDeclaringClass()` và phân tích hệ phân cấp lớp/interface. Đừng cho rằng mọi `Method` trả bởi `getMethods()` đều được khai báo trong chính lớp được hỏi.

## 7. Những nhầm lẫn phổ biến

**Nhầm 1 — “Bridge là overload bình thường do người viết định nghĩa”.** Bridge thường không có khai báo nguồn riêng. Nó là một artifact để bảo toàn overriding ở runtime; việc hai entry point xuất hiện khi Reflection không chứng minh lập trình viên chủ ý tạo overload đó.

**Nhầm 2 — “Chỉ generic mới sinh bridge”.** Generic erasure là trường hợp rất thường gặp, nhưng ví dụ `Child.read()` cho thấy covariant return cũng có thể tạo bridge.

**Nhầm 3 — “`isSynthetic()` chính là `isBridge()`”.** Với bytecode do `javac` tạo trong ví dụ, bridge cũng là synthetic. Nhưng synthetic bao gồm các phương thức compiler sinh vì mục đích khác. Hãy hỏi `isBridge()` khi cần biết riêng bridge.

**Nhầm 4 — “Modifier của bridge sẽ hiển thị nhãn bridge”.** `Modifier.toString(method.getModifiers())` không phải API phù hợp để giải thích `ACC_BRIDGE`. Cờ bridge ở JVM có thể dùng giá trị bit trùng với cờ mang nghĩa khác ở loại thành viên khác. Dùng `Method.isBridge()` hoặc kiểm tra `javap -v`.

**Nhầm 5 — “Chỉ cần so tên và tham số là tìm đúng method”.** Bridge của covariant return có thể chỉ khác kiểu trả về ở descriptor. Phải xét `getReturnType()`, cờ bridge và nguồn gốc khai báo.

**Nhầm 6 — “`Method.invoke` qua bridge luôn tương đương gọi method kiểu cụ thể ở source”.** Reflection chấp nhận đối số dựa trên chữ ký runtime được nhìn thấy. Bridge có thể tự ép kiểu và ném `ClassCastException` từ bên trong, như ví dụ `accept(Object)`.

**Nhầm 7 — “Mọi compiler đều sinh đúng cùng một bộ method”.** Sự có mặt, số lượng bridge, cờ synthetic và bytecode chi tiết phụ thuộc quan hệ kiểu và cách compiler phát hành mã. Công cụ xử lý nên kiểm tra metadata thực tế thay vì cố định số lượng method.

## 8. Bài tập tự kiểm tra

1. Thay `Sink<String>` bằng `Sink<Integer>` và viết `IntegerSink.accept(Integer)`. Dự đoán descriptor bridge sau erasure rồi kiểm tra `getDeclaredMethods()` và `isBridge()`.
2. Thử gọi bridge `accept(Object)` bằng một `Integer` trong ví dụ gốc. Giải thích tại sao Reflection nhận tham số `Object` nhưng vẫn có `ClassCastException` từ lời gọi bên trong.
3. Đổi `Parent.read()` và `Child.read()` để trả về các kiểu tham chiếu khác có quan hệ subtype, rồi quan sát bridge với `javap`.
4. Viết bộ lọc hiển thị public API bỏ bridge; sau đó viết chế độ debug giữ bridge và in `getDeclaringClass()`, `getReturnType()`, `isBridge()`, `isSynthetic()`.
5. Tự trả lời: tại sao `!method.isSynthetic()` không đủ để **phân loại** bridge, dù thường loại được bridge do `javac` sinh?

## 9. Ghi nhớ

- **Bridge method** là phương thức chuyển tiếp do compiler sinh nhằm duy trì tương thích overriding/liên kết ở mức bytecode, đặc biệt với generic erasure và covariant return.
- **`Method.isBridge()`** là cách Reflection chuẩn để nhận diện phương thức mang cờ bridge.
- **`isSynthetic()` không đồng nghĩa `isBridge()`**; hai khái niệm mang mục đích khác nhau.
- **`getDeclaredMethods()` có thể trả cả method nguồn lẫn bridge**; luôn kiểm tra cờ trước khi sinh tài liệu hoặc chọn phương thức.
- **Đường dispatch vẫn có thể ép kiểu và ném ngoại lệ**; quan sát được điều đó qua `Method.invoke` và `InvocationTargetException`.
- **Có nên lọc bridge hay không tùy mục đích**: hiển thị API thường lọc, phân tích JVM/override thường giữ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="modifiers-flags-008">AccessFlag, accessFlags và khác biệt theo phiên bản JDK</a>

<details>
<summary>Xem chi tiết</summary>

# AccessFlag, accessFlags() và khác biệt theo phiên bản JDK trong Java Reflection

## 1. Từ modifier của Java đến access flag của JVM

Một khai báo Java có thể có các từ khóa như `public`, `private`, `static`, `final`, `abstract`, `synchronized`, `volatile`. Khi biên dịch, JVM class file lưu nhiều đặc tính của class, field, method và các cấu trúc khác dưới dạng **bit flag**. Tuy nhiên, không phải mọi khái niệm trong cú pháp Java đều ứng với một bit flag, và có flag chỉ xuất hiện ở cấp JVM.

Cần tách hai góc nhìn:

- **Java language modifier**: điều mà `java.lang.reflect.Modifier` và `getModifiers()` chủ yếu mô hình hóa, phù hợp khi cần đọc quyền truy cập, `static`, `final`, `volatile`, v.v. theo ngôn ngữ Java.
- **JVM access/module flag**: các cờ tại vị trí cụ thể của class file, gồm cả `SYNTHETIC`, `BRIDGE`, `VARARGS`, `ANNOTATION`, `ENUM`, `MANDATED`, `MODULE` và các cờ liên quan module. Từ **JDK 20**, API `java.lang.reflect.AccessFlag` cùng `accessFlags()` đưa cách nhìn này vào core reflection.

Ví dụ, phương thức bridge do compiler tạo có cờ `ACC_BRIDGE` mà từ khóa Java không tương ứng trực tiếp; trong khi `sealed` được biểu diễn bằng metadata `PermittedSubclasses` của class file, **không có** cờ `ACC_SEALED`.

Không được hiểu mọi flag chỉ là “modifier viết trước khai báo”. Một class, field, method, method parameter hoặc mục trong module có **tập cờ hợp lệ khác nhau**.

## 2. Hai API: getModifiers() và accessFlags()

### Cách cũ và vẫn hữu ích: getModifiers() + Modifier

`Class.getModifiers()`, `Field.getModifiers()`, `Method.getModifiers()`, `Constructor.getModifiers()` và `Parameter.getModifiers()` trả về một `int` dạng bit mask. Các phương thức tĩnh trong `Modifier` giải mã theo mục đích của ngôn ngữ:

```java
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

class Demo {
    public static final int LIMIT = 10;
}

Field field = Demo.class.getDeclaredField("LIMIT");
int modifiers = field.getModifiers();

boolean isPublic = Modifier.isPublic(modifiers);
boolean isStatic = Modifier.isStatic(modifiers);
boolean isFinal = Modifier.isFinal(modifiers);
String readable = Modifier.toString(modifiers);
```

Đoạn trên là **mảnh mã minh họa** để đặt trong một phương thức có khai báo `throws ReflectiveOperationException`; riêng tệp này chưa phải chương trình chạy độc lập. `Modifier.toString(modifiers)` có ích khi hiển thị modifier nguồn, nhưng `getModifiers()` không phải trình đọc toàn bộ flag trong class file.

Ưu điểm của `getModifiers()`: có từ các bản JDK cũ, dễ kiểm tra các modifier thông dụng, tương thích thư viện chạy trên JDK 8/11/17. Nếu chỉ cần `public/static/final` cho một field, nó thường là lựa chọn hợp lý.

### Từ JDK 20: accessFlags()

`accessFlags()` trả về `Set<AccessFlag>` không thể thay đổi, ví dụ kiểm tra:

```java
Set<AccessFlag> flags = field.accessFlags(); // JDK 20+
boolean isVolatile = flags.contains(AccessFlag.VOLATILE);
```

Tập có thể rỗng; đừng suy luận rằng phần tử xuất hiện theo thứ tự cố định khi in `Set`. So sánh bằng `contains`/`containsAll` thay vì chuyển sang chuỗi. `AccessFlag` là `enum` nên tên cờ phân biệt rõ `VOLATILE` (field) với `BRIDGE` (method) dù trùng bit mask.

Các điểm gọi thông dụng từ JDK 20:

| Đối tượng Reflection | Cách đọc cờ | Chú ý |
| --- | --- | --- |
| `Class<?>` | `clazz.accessFlags()` | Cờ của class/interface; xử lý riêng array, primitive, `void` theo quy tắc `Class`. |
| `Field` | `field.accessFlags()` | Ví dụ `PUBLIC`, `STATIC`, `FINAL`, `VOLATILE`, `TRANSIENT`, `ENUM`, `SYNTHETIC` tùy field. |
| `Method` | `method.accessFlags()` | Ví dụ `SYNCHRONIZED`, `NATIVE`, `ABSTRACT`, `BRIDGE`, `VARARGS`, `SYNTHETIC`. |
| `Constructor<?>` | `constructor.accessFlags()` | Thừa kế triển khai từ `Executable`; áp dụng các cờ hợp lệ với constructor. |
| `Parameter` | `parameter.accessFlags()` | Metadata tham số; cần xem xét khả năng dữ liệu tham số hiện diện trong class file. |

`Member` định nghĩa `default accessFlags()` để hỗ trợ tương thích API, nhưng triển khai mặc định ném `UnsupportedOperationException`. Các implementation core reflection tiêu chuẩn của `Field`/`Method`/`Constructor` cung cấp phương thức này; không suy đoán mọi implementation `Member` tự viết đều hỗ trợ.

**Nhớ:** `accessFlags()` không trả về modifier nguồn dưới dạng chuỗi, không tự kiểm tra bạn có quyền `setAccessible`, và không thay thế mọi API đặc trưng như `Method.isBridge()`, `Class.isRecord()` hoặc `Class.isSealed()`.

## 3. Chương trình Java 20+ có thể chạy để đối chiếu hai API

Lưu vào tệp `AccessFlagLessonDemo.java`:

```java
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public class AccessFlagLessonDemo {
    static class Sample {
        public volatile int state;
        public synchronized void log(String... values) { }
    }

    interface Formatter<T> { T format(T value); }

    static final class TextFormatter implements Formatter<String> {
        public String format(String value) { return value; }
    }

    public static void main(String[] args) throws Exception {
        Field field = Sample.class.getDeclaredField("state");
        Method method = Sample.class.getDeclaredMethod("log", String[].class);
        Method bridge = Arrays.stream(TextFormatter.class.getDeclaredMethods())
                .filter(Method::isBridge)
                .findFirst()
                .orElseThrow();

        System.out.println("field Modifier.isVolatile = "
                + Modifier.isVolatile(field.getModifiers()));
        System.out.println("field AccessFlag.VOLATILE = "
                + field.accessFlags().contains(AccessFlag.VOLATILE));
        System.out.println("method VARARGS = "
                + method.accessFlags().contains(AccessFlag.VARARGS));
        System.out.println("method SYNCHRONIZED = "
                + method.accessFlags().contains(AccessFlag.SYNCHRONIZED));
        System.out.println("bridge BRIDGE = "
                + bridge.accessFlags().contains(AccessFlag.BRIDGE));
        System.out.println("field mask 0x0040 = "
                + AccessFlag.maskToAccessFlags(0x0040, AccessFlag.Location.FIELD));
        System.out.println("method mask 0x0040 = "
                + AccessFlag.maskToAccessFlags(0x0040, AccessFlag.Location.METHOD));
    }
}
```

Biên dịch/chạy với JDK 20 trở lên; ví dụ đã được biên dịch và chạy thành công bằng JDK 24:

```bash
javac AccessFlagLessonDemo.java
java AccessFlagLessonDemo
```

Stdout chính xác:

```text
field Modifier.isVolatile = true
field AccessFlag.VOLATILE = true
method VARARGS = true
method SYNCHRONIZED = true
bridge BRIDGE = true
field mask 0x0040 = [VOLATILE]
method mask 0x0040 = [BRIDGE]
```

Vì `Sample.state` được khai báo `volatile`, cả `Modifier.isVolatile` và tập `AccessFlag` đều nhận diện thuộc tính này. Method `log(String... values)` là method varargs đồng thời `synchronized`. Generic interface `Formatter<T>` khi bị type erasure yêu cầu một adapter `format(Object)`: compiler sinh **bridge method** trong `TextFormatter`. `Method.isBridge()` và `accessFlags().contains(AccessFlag.BRIDGE)` xác nhận nó. Đây là cờ JVM mà người viết Java thông thường không gõ thành modifier trong nguồn.

Hai dòng cuối đặc biệt quan trọng: cùng bit `0x0040` nhưng ở vị trí `FIELD` nó nghĩa là `VOLATILE`, còn ở vị trí `METHOD` nó nghĩa là `BRIDGE`. Việc decode mà bỏ qua ngữ cảnh có thể tạo kết quả sai nghiêm trọng.

## 4. AccessFlag enum và cách diễn giải theo Location

`AccessFlag` biểu diễn các cờ như `PUBLIC` (`0x0001`), `STATIC` (`0x0008`), `FINAL` (`0x0010`), `VOLATILE` (`0x0040`), `BRIDGE` (`0x0040`), `TRANSIENT` (`0x0080`), `VARARGS` (`0x0080`), `SYNTHETIC` (`0x1000`), `ANNOTATION` (`0x2000`), `ENUM` (`0x4000`).

Các nhóm điển hình:

| Vị trí trong class file | Một số flag có thể gặp | Giải thích |
| --- | --- | --- |
| `CLASS` / `INNER_CLASS` | `PUBLIC`, `FINAL`, `INTERFACE`, `ABSTRACT`, `ANNOTATION`, `ENUM`, `SYNTHETIC`; `STATIC` ở thông tin nested class | Class metadata và thông tin về kiểu lồng nhau có quy tắc khác nhau. |
| `FIELD` | `PUBLIC`, `PRIVATE`, `STATIC`, `FINAL`, `VOLATILE`, `TRANSIENT`, `SYNTHETIC`, `ENUM` | `VOLATILE` và `TRANSIENT` chỉ có ý nghĩa tương ứng ở field. |
| `METHOD` | `PUBLIC`, `PRIVATE`, `STATIC`, `FINAL`, `SYNCHRONIZED`, `BRIDGE`, `VARARGS`, `NATIVE`, `ABSTRACT`, `STRICT`, `SYNTHETIC` | `STRICT` phụ thuộc đời class file. |
| `METHOD_PARAMETER` | `FINAL`, `SYNTHETIC`, `MANDATED` | Mô hình cờ trong mục `MethodParameters`, không đồng nhất với `METHOD`. |
| `MODULE`, `MODULE_REQUIRES`, `MODULE_EXPORTS`, `MODULE_OPENS` | `OPEN`, `TRANSITIVE`, `STATIC_PHASE`, `SYNTHETIC`, `MANDATED` tùy chỗ | Các cờ của module descriptor; không phải `public/private` của Java field. |

**Không phải mọi tổ hợp trên bảng đều hợp lệ.** Đây là các ví dụ cờ theo nhóm, còn JVMS quyết định vị trí, tổ hợp và đời class file cụ thể.

Dùng `AccessFlag.Location` để nêu **vị trí** khi giải mã bit:

```java
AccessFlag.maskToAccessFlags(0x0040, AccessFlag.Location.FIELD);
AccessFlag.maskToAccessFlags(0x0040, AccessFlag.Location.METHOD);
```

Hai lời gọi lần lượt trả tập chỉ gồm `VOLATILE` và tập chỉ gồm `BRIDGE`. Đối với mask chứa bit không được định nghĩa ở vị trí yêu cầu, `maskToAccessFlags` có thể ném `IllegalArgumentException`. Không nên tự viết vòng lặp trên mọi enum constant để đoán ý nghĩa từ `(mask & flag.mask()) != 0` mà không kiểm tra location.

Các phương thức cốt lõi:

- `flag.mask()`: trả int mask, **không phải định danh duy nhất** của flag.
- `flag.sourceModifier()`: cho biết cờ có modifier tương ứng trực tiếp trong ngôn ngữ Java hay không. Điều này không nói cờ đang có trên một đối tượng cụ thể.
- `flag.locations()`: tập các vị trí hợp lệ trong phiên bản class file hiện hành mà API đang mô hình hóa. Tập có thể rỗng với cờ đã hết hiệu lực.
- `flag.locations(ClassFileFormatVersion version)`: xác định vị trí hợp lệ theo đời class file được chọn.
- `AccessFlag.maskToAccessFlags(mask, location)`: diễn giải bit đúng ngữ cảnh theo phiên bản hiện hành; từ JDK 25 có overload nhận thêm `ClassFileFormatVersion`.

Ví dụ tại sao không thể coi `mask` là danh tính:

```java
System.out.println(AccessFlag.VOLATILE.mask() == AccessFlag.BRIDGE.mask());
// true

System.out.println(AccessFlag.TRANSIENT.mask() == AccessFlag.VARARGS.mask());
// true
```

Đây là hai dòng minh họa độc lập về biểu thức, không phải tệp Java hoàn chỉnh.

## 5. Khác biệt theo các phiên bản JDK

| Dòng JDK | API và hệ quả |
| --- | --- |
| **JDK 8, 11, 17, 19 và cũ hơn 20** | **Không có** `java.lang.reflect.AccessFlag` và `accessFlags()`. Dùng `getModifiers()` + `Modifier`, các hàm `isBridge()`/`isSynthetic()`/`isVarArgs()`, và công cụ đọc class file khi cần cờ JVM đầy đủ. |
| **JDK 20** | Bổ sung `AccessFlag`, `AccessFlag.Location`, `Class.accessFlags()`, `Member.accessFlags()` và `Parameter.accessFlags()`. Có `mask()`, `sourceModifier()`, `locations()`, `locations(ClassFileFormatVersion)`, `maskToAccessFlags(mask, location)`. |
| **JDK 21** | Các API trên sẵn có ở bản LTS; ví dụ code trong bài chạy bằng JDK 21. Phù hợp nếu dự án cần LTS và muốn truy cập cờ dạng enum. |
| **JDK 24** | Bổ sung **Class-File API** chuẩn ở package `java.lang.classfile` (JEP 484), trong đó có giao diện `AccessFlags` để đọc/biến đổi file class. Giao diện `java.lang.classfile.AccessFlags` và enum `java.lang.reflect.AccessFlag` là **hai loại API khác mục đích**. |
| **JDK 25** | Thêm overload `AccessFlag.maskToAccessFlags(int, AccessFlag.Location, ClassFileFormatVersion)` để diễn giải mask theo một phiên bản class-file được chỉ định. API có thể còn phát triển ở các bản JDK mới; xem Javadoc đúng phiên bản bạn chạy. |

**Đọc đúng chữ “Since”:** `AccessFlag` có `Since: 20`. Khi trang Javadoc JDK 25 trình bày một overload có `Since: 25`, đó là năm xuất hiện của **overload ấy**, không phải thời điểm toàn bộ enum mới được tạo. Tránh kết luận rằng API chỉ có từ JDK 21 vì đang đọc tài liệu Java 21.

**Phiên bản của JDK đang chạy không đồng nghĩa phiên bản mọi class file được nạp.** Chẳng hạn, có thể chạy JDK 21 để nạp class được biên dịch với `javac --release 17`. Cần xem **class file major version** nếu muốn phân tích flag đã tồn tại vào thời điểm class file được tạo. `AccessFlag.locations(cffv)` mô tả quy tắc của đời class file được hỏi, không tự đổi phiên bản của bytecode đang được xem.

### Trường hợp lịch sử: ACC_STRICT và strictfp

Cờ `AccessFlag.STRICT` (mask `0x0800`) tương ứng `ACC_STRICT` trên **method** và chỉ được JVMS định nghĩa cho class file major version **46–60**, tương ứng Java SE 1.2–16. Từ Java 17, ngữ nghĩa phép toán dấu phẩy động đã luôn strict theo JEP 306; đừng suy rằng method compiled trên JDK hiện đại vẫn phải có `ACC_STRICT` khi có ý tưởng tương tự `strictfp`.

Để phân tích định dạng cũ trên JDK 20+, tra:

```java
import java.lang.reflect.AccessFlag;
import java.lang.reflect.ClassFileFormatVersion;

boolean oldVersionAllowsStrict =
        AccessFlag.STRICT.locations(ClassFileFormatVersion.RELEASE_16)
                .contains(AccessFlag.Location.METHOD);

boolean newVersionAllowsStrict =
        AccessFlag.STRICT.locations(ClassFileFormatVersion.RELEASE_17)
                .contains(AccessFlag.Location.METHOD);
```

Đây là **mảnh mã minh họa** trong một class/method. `oldVersionAllowsStrict` là `true`, `newVersionAllowsStrict` là `false`. Điều này mô tả **định dạng class file**; không nên dùng nó để kết luận hành vi số học của ứng dụng hiện tại.

### Biên dịch và tương thích nhị phân

Một ứng dụng target JDK 17 **không thể biên dịch source** có import `java.lang.reflect.AccessFlag` bằng `javac --release 17`, kể cả bạn đang cài JDK 24. API này chưa tồn tại trong Java 17. Nếu mục tiêu runtime là Java 17, hãy giữ `Modifier` và các API tương thích hoặc dùng build/module đặc thù để đọc class file.

Nếu code được biên dịch với JDK 21 và có gọi `accessFlags()`, việc sao chép class đã biên dịch sang JRE 17 không trở thành tương thích: JRE 17 thiếu API (và bytecode class target mới có thể bị từ chối trước với `UnsupportedClassVersionError`). Đừng chỉ thử “bỏ import” để chữa lỗi tương thích.

## 6. AccessFlag, Modifier và các API kiểm tra trạng thái khác không đồng nghĩa

### Synthetic, bridge và varargs

- `Class.isSynthetic()`, `Field.isSynthetic()`, `Method.isSynthetic()` hoặc `Member.isSynthetic()` trả lời câu hỏi tổng quát về synthetic; `AccessFlag.SYNTHETIC` phản ánh access flag ở vị trí tương ứng. Synthetic không phải một modifier người dùng gõ trước khai báo Java.
- `Method.isBridge()` là cách lâu đời để nhận diện bridge method; `method.accessFlags().contains(AccessFlag.BRIDGE)` là cách biểu diễn enum trên JDK 20+.
- `Executable.isVarArgs()` cho biết method/constructor dùng variable arity; `AccessFlag.VARARGS` là cờ class file tương ứng. **Một `String[]` tham số cuối không tự động biến method thành varargs**; phải có khai báo `String...` hoặc cờ từ class file.
- `Parameter.isImplicit()`, `Parameter.isSynthetic()` và `Parameter.accessFlags()` phục vụ metadata tham số. Một phần thông tin tên/cờ tham số phụ thuộc `MethodParameters` attribute do compiler phát ra, nên đừng giả định mọi class file cũ đều lưu đủ.

### Sealed và record không có cờ AccessFlag tương ứng

Với Java 17+:

```java
boolean isSealed = SomeType.class.isSealed();
boolean isRecord = SomeType.class.isRecord();
```

Đây là mảnh mã minh họa: `SomeType` đại diện class mà dự án định nghĩa. Không có `AccessFlag.SEALED` hay `AccessFlag.RECORD` tương ứng. `sealed` dùng metadata `PermittedSubclasses`, còn record dùng `Record` attribute và các quy ước biên dịch. Đừng kết luận kiểu record dựa vào `FINAL`, hay kiểu sealed dựa vào `ABSTRACT`.

### Access flag khác với quyền truy cập reflection đang được phép

`AccessFlag.PRIVATE` cho biết member là private về mặt metadata. Nó không tự cấp quyền đọc/ghi field private, gọi method private hay mở module. `AccessibleObject.canAccess`, `trySetAccessible()` và các quy tắc module/JPMS vẫn quyết định có truy cập phản chiếu sâu được hay không. Tương tự, cờ `OPEN` trong module là metadata module chứ không tương đương `private` trên field.

### Class, nested class, primitive và array có ngoại lệ

`Class.accessFlags()` của array và primitive/`void` có quy tắc suy diễn riêng theo Javadoc; không giả định lúc nào cũng đọc trực tiếp danh sách bit thô từ `ClassFile` tương ứng (primitive/void và array không có class file độc lập giống class thông thường). Với nested type, `STATIC` có thể được ghi trong `InnerClasses` attribute; cần phân biệt `CLASS` và `INNER_CLASS` khi tự giải mã class file.

Ví dụ, `String[].class.accessFlags()` không có cùng ý nghĩa kiểm tra cấu trúc file với việc đọc thô một `String[].class` trên đĩa: không tồn tại file `String[].class` như một khai báo class riêng để mở.

## 7. Khi cần xem class file thật: công cụ và API phù hợp

Reflection làm việc trên `Class`/`Field`/`Method` **đã được load**; nó trình bày cái nhìn đã chuẩn hóa theo API Java. Nếu nhiệm vụ là phân tích bytecode, byte-level flags, version hoặc attributes, lựa chọn phù hợp hơn là:

1. `javap -v SomeType`: nhìn major version, `flags: (0x...)`, phương thức synthetic/bridge và các attributes. Với ví dụ `AccessFlagLessonDemo`, có thể chạy `javap -v AccessFlagLessonDemo$TextFormatter`; trong shell có quy tắc escape ký tự `$`, vì vậy phải quote tên class phù hợp. Kết quả có thể dài, chủ động tìm các dòng `flags`.
2. Từ **JDK 24**, dùng `java.lang.classfile` và `AccessFlags` khi cần phân tích/biến đổi class file có cấu trúc bằng mã Java. Đây là Class-File API, không phải method `accessFlags()` trên đối tượng reflection.
3. Với hệ thống vẫn chạy JDK 17, có thể dùng `javap` hoặc thư viện bytecode tương thích (ví dụ ASM đúng phiên bản class file) khi thật sự cần bridge/synthetic flags và attributes ngoài khả năng `Modifier`.

Không dùng `getModifiers()` làm raw flag mask cho mọi `AccessFlag.Location`. Trường hợp có bit trùng nghĩa khác nhau, hoặc method của JVM có bridge/synthetic, chuyển đổi “chung” như vậy không bảo đảm chuẩn xác. Cũng không coi `AccessFlag.maskToAccessFlags` như một API kiểm tra quyền theo `SecurityManager` hay module access.

## 8. Quy trình chọn cách đọc flag

**Nếu chỉ kiểm tra modifier quen thuộc và còn hỗ trợ JDK 17:** dùng `Modifier.isPublic(field.getModifiers())`, `Modifier.isStatic(...)`, `Modifier.isFinal(...)`. Với bridge/varargs/synthetic, dùng API riêng như `isBridge()`/`isVarArgs()`/`isSynthetic()`.

**Nếu chạy JDK 20+ và muốn tên cờ rõ ràng:** dùng `field.accessFlags().contains(AccessFlag.VOLATILE)`, `method.accessFlags().contains(AccessFlag.BRIDGE)`. Đây là cách dễ tránh lỗi trùng mask.

**Nếu có số bit thô:** trước hết xác định `AccessFlag.Location` chính xác; tiếp theo xác định đời class file. Với JDK 25+ có thể dùng overload ba tham số để decode đúng phiên bản; ở các JDK trước đó cần kiểm tra giới hạn API đang có, cùng tài liệu JVMS nếu phân tích class file khác đời.

**Nếu cần cấu trúc record/sealed hoặc metadata không phải access flags:** hỏi API chuyên biệt (`isRecord()`, `isSealed()`) hoặc đọc class file đầy đủ. Đừng cố “sáng tạo” cờ không được định nghĩa.

**Nếu đọc field/method từ framework:** phân biệt “đã khai báo flag gì” với “có thể phản chiếu truy cập được không”. Chúng là hai câu hỏi độc lập.

## 9. Lỗi phổ biến và bài tập tự kiểm tra

1. **Gọi `accessFlags()` ở JDK 17:** không biên dịch; API được giới thiệu ở JDK 20.
2. **Đảo nghĩa `0x0040`:** field dùng `VOLATILE`, method dùng `BRIDGE`.
3. **Tin vào `getModifiers()` như toàn bộ raw class flags:** API thiên về modifier Java và không thay các API cờ/attribute đặc thù.
4. **Tưởng `sealed`, `record` là `AccessFlag`:** thực tế phải dùng metadata/API khác.
5. **Đoán flag chỉ từ JDK runtime:** class file đang xét có thể được build bằng `--release` cho đời cũ.
6. **Phụ thuộc thứ tự in `Set`:** đọc bằng `contains` hoặc so sánh tập, không so thứ tự.
7. **Hiểu `SYNTHETIC` là “bất kỳ thứ gì compiler tạo ra”:** JVMS đặt cờ/attribute với quy tắc cụ thể; chỉ dùng metadata được đọc từ class file/API, không đoán qua tên `$` hoặc pattern sinh mã.
8. **Quên sai khác giữa đọc metadata và truy cập private:** flag không mở quyền Reflection và không vượt quy tắc module.

Tự kiểm tra:

- `AccessFlag.TRANSIENT.mask() == AccessFlag.VARARGS.mask()` là `true`, vậy chỉ từ một số mask ta có kết luận được flag không? **Không**, cần location.
- `AccessFlag.STRICT.locations(ClassFileFormatVersion.RELEASE_17)` có chứa `METHOD` không? **Không**, vì `ACC_STRICT` chỉ áp dụng đến major version 60.
- `method.accessFlags().contains(AccessFlag.BRIDGE)` và `method.isBridge()` đều có thể sử dụng trên JDK 17? **Không**: `isBridge()` có từ trước, `accessFlags()` chỉ từ JDK 20.
- Class là `record` nhưng không có cờ `AccessFlag.RECORD` thì có mâu thuẫn không? **Không**: đặc tính record được biểu diễn ngoài tập access flags.

## 10. Tài liệu tham khảo chính thức

- [Oracle Javadoc Java 20 — AccessFlag (Since: 20)](https://docs.oracle.com/en/java/javase/20/docs/api/java.base/java/lang/reflect/AccessFlag.html)
- [Oracle Javadoc Java 21 — AccessFlag và Location](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AccessFlag.html)
- [Oracle Javadoc Java 21 — Class.accessFlags()](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#accessFlags())
- [Oracle Javadoc Java 21 — Modifier](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Modifier.html)
- [Oracle Javadoc Java 25 — AccessFlag và overload theo ClassFileFormatVersion](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/reflect/AccessFlag.html)
- [Dev.java — Reading Modifiers](https://dev.java/learn/api/reflection-method-handles/reflection/modifiers/)
- [JEP 484 — Class-File API (JDK 24)](https://openjdk.org/jeps/484)
- [JEP 306 — Restore Always-Strict Floating-Point Semantics (JDK 17)](https://openjdk.org/jeps/306)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="modifiers-flags-009">Không suy ra toàn bộ cú pháp nguồn từ cờ trong class file</a>

<details>
<summary>Xem chi tiết</summary>

# Không suy ra toàn bộ cú pháp nguồn từ cờ trong class file

## 1. Vấn đề cần hiểu

Khi dùng Java Reflection, bạn có thể gọi `Class.getModifiers()`, `Field.getModifiers()`, `Method.getModifiers()` hoặc `Constructor.getModifiers()`, rồi kiểm tra bằng `java.lang.reflect.Modifier`. Khi đọc tệp `.class` qua `javap -v`, bạn cũng nhìn thấy các giá trị `access_flags`, chẳng hạn `ACC_PUBLIC`, `ACC_FINAL`, `ACC_STATIC`, `ACC_ABSTRACT`.

Tuy nhiên, **modifier và flag chỉ mô tả một phần đặc tính của chương trình đã biên dịch; chúng không phải bản chụp nguyên văn mã nguồn Java**. Không thể chỉ dựa vào các cờ này để dựng lại đầy đủ câu lệnh khai báo, biểu thức, cách viết tham số, từ khóa xuất hiện tường minh hay toàn bộ cú pháp nguồn ban đầu.

Ví dụ, hai khai báo sau có thể dẫn đến cùng những modifier hiệu lực cho phương thức:

```java
interface First {
    void run();                 // public abstract được ngầm hiểu
}

interface Second {
    public abstract void run(); // Viết tường minh hai modifier
}
```

Trong Reflection, `getDeclaredMethod("run").getModifiers()` ở cả hai trường hợp đều mang `public` và `abstract`. Từ kết quả ấy không thể biết tác giả đã viết tường minh `public abstract` hay để trình biên dịch suy ra theo quy tắc ngôn ngữ.

**Phạm vi bài này:** cách đọc và giới hạn diễn giải modifier/flag của *lớp, trường, phương thức, constructor và tham số* trong bối cảnh Java Reflection và class file; phân biệt dữ liệu thật sự có thể quan sát với cú pháp nguồn không được lưu giữ.

## 2. Quá trình biên dịch làm mất thông tin cú pháp như thế nào?

```text
Mã nguồn .java
  ├── từ khóa modifier, kiểu, tham số, chú thích
  ├── biểu thức, câu lệnh, import, định dạng, tên biến cục bộ
  └── đường đi cú pháp: khai báo tường minh / ngầm định
             │ javac / compiler / bytecode transformation
             ▼
Tệp .class
  ├── constant pool và access_flags
  ├── mô tả kiểu JVM (descriptors)
  ├── field_info, method_info, bytecode
  └── attributes: Signature, Exceptions, InnerClasses, ...
             │ Reflection / javap / class-file reader
             ▼
Thông tin cấu trúc + hành vi đã biên dịch
             ≠
Bản gốc đầy đủ của cú pháp .java
```

Một tệp `.class` lưu những gì JVM cần để nạp, xác minh và chạy mã, cùng các metadata mà trình biên dịch hoặc công cụ tạo bytecode chọn ghi thêm. Đây là **biểu diễn đã được chuyển đổi**, không phải cây cú pháp nguồn Java có thể đảo ngược hoàn toàn.

Những khác biệt phải phân biệt:

- **Nguồn:** có thể có cú pháp khác nhau nhưng cùng ý nghĩa và cùng modifier hiệu lực, ví dụ `void run();` và `public abstract void run();` trong interface.
- **Class file:** có nhiều vùng thông tin. `access_flags` chỉ là tập bit; descriptors, bytecode và các attributes mang các loại thông tin riêng biệt.
- **Reflection:** là API mức cao diễn giải cấu trúc đã nạp. Kết quả `getModifiers()` không tương ứng một-một với danh sách từ khóa từng xuất hiện trong nguồn; một số chi tiết khác cần API riêng.
- **Compiler / bytecode tools:** có thể bổ sung hoặc biến đổi cấu trúc, như tạo phương thức `bridge` và các phần tử `synthetic`. Không thể mặc định mọi phần tử quan sát được đều có một khai báo tường minh trong mã nguồn.

Từ một số thuộc tính, ta có thể **suy ra hợp lệ về kiểu thành viên ở runtime**. Nhưng suy luận đó khác với khẳng định tác giả đã viết chính xác chuỗi token nào trong tệp `.java`.

## 3. `access_flags` mô tả điều gì, và ở đâu?

Theo định dạng class file của JVM, nhiều cấu trúc có trường `access_flags`: phần định nghĩa class/interface, mỗi `field_info`, mỗi `method_info`; riêng metadata khác có thể chứa các tập flag riêng. **Cùng một vị trí bit có thể mang ý nghĩa khác nhau tùy cấu trúc.**

| Vị trí | Ví dụ cờ | Thông tin được mô tả |
| --- | --- | --- |
| Class/interface header | `ACC_PUBLIC`, `ACC_FINAL`, `ACC_INTERFACE`, `ACC_ABSTRACT`, `ACC_ANNOTATION`, `ACC_ENUM`, `ACC_MODULE` | Đặc tính của kiểu trong class file |
| `field_info` | `ACC_PUBLIC`, `ACC_PRIVATE`, `ACC_STATIC`, `ACC_FINAL`, `ACC_VOLATILE`, `ACC_TRANSIENT`, `ACC_SYNTHETIC`, `ACC_ENUM` | Khả năng truy cập và tính chất của field |
| `method_info` | `ACC_PUBLIC`, `ACC_PRIVATE`, `ACC_STATIC`, `ACC_FINAL`, `ACC_SYNCHRONIZED`, `ACC_BRIDGE`, `ACC_VARARGS`, `ACC_NATIVE`, `ACC_ABSTRACT`, `ACC_SYNTHETIC` | Tính chất phương thức ở JVM |
| `InnerClasses` attribute | Các cờ dành cho thông tin lớp lồng, gồm `ACC_STATIC` | Metadata về lớp thành viên/lớp lồng trong quan hệ bao chứa |
| `MethodParameters` attribute | `ACC_FINAL`, `ACC_SYNTHETIC`, `ACC_MANDATED` | Metadata của *tham số*, nếu attribute này hiện diện |

**Lưu ý về bảng:** đây là các ví dụ, không phải danh sách đầy đủ cho mọi phiên bản JVM. Không hoán đổi bảng cờ giữa các loại cấu trúc. Ví dụ, `ACC_STATIC` có thể xuất hiện ở field hoặc method, còn thông tin một *lớp thành viên* là `static` được mô tả thông qua `InnerClasses`, chứ không phải một bit `ACC_STATIC` trong class header.

Những cờ như `ACC_SUPER` mang ý nghĩa liên quan tới cách JVM xử lý class file; chúng **không phải từ khóa modifier trong khai báo Java**. Ngược lại, không phải mọi từ khóa hoặc đặc điểm Java đều có một bit tương ứng trong `access_flags`.

### 3.1. Phân biệt flag của class file với `Modifier` của Reflection

`java.lang.reflect.Modifier` cung cấp các phép kiểm tra như:

```java
Modifier.isPublic(mods)
Modifier.isPrivate(mods)
Modifier.isProtected(mods)
Modifier.isStatic(mods)
Modifier.isFinal(mods)
Modifier.isAbstract(mods)
Modifier.isSynchronized(mods)
Modifier.isTransient(mods)
Modifier.isVolatile(mods)
Modifier.isNative(mods)
Modifier.isInterface(mods)
Modifier.toString(mods)
```

`mods` là số nguyên trả về từ API `getModifiers()` tương ứng. `Modifier.toString(mods)` hiển thị những modifier mà API đó nhận diện theo một thứ tự biểu diễn chuẩn; nó **không** bảo đảm tái tạo thứ tự hoặc sự hiện diện tường minh của từ khóa trong mã nguồn.

Không lấy một số nguyên `access_flags` tùy ý từ tệp `.class` rồi coi nó hoàn toàn tương đương với `getModifiers()`. Lý do gồm: phạm vi flag khác nhau giữa class/field/method, các bit có ý nghĩa riêng của JVM, và việc Reflection có thể sử dụng metadata khác để mô tả kiểu. Chẳng hạn, với lớp thành viên `static`, `Class.getModifiers()` có thể báo `static` dựa trên thông tin lớp lồng.

### 3.2. Modifier không phải tất cả metadata

Vẫn có những chi tiết quan sát được qua các API chuyên biệt:

| Cần kiểm tra | API hoặc metadata phù hợp | Vì sao `getModifiers()` chưa đủ |
| --- | --- | --- |
| Phương thức varargs | `Method.isVarArgs()` / `ACC_VARARGS` | `varargs` không có phép thử `Modifier.isVarArgs` |
| Phương thức bridge | `Method.isBridge()` / `ACC_BRIDGE` | Bridge có thể do compiler sinh ra |
| Phần tử synthetic | `isSynthetic()` / `ACC_SYNTHETIC` | Không tương đương một từ khóa khai báo Java |
| Phương thức `default` trong interface | `Method.isDefault()` | Không có `Modifier.isDefault` |
| Class là record | `Class.isRecord()`, `getRecordComponents()`, `Record` attribute | `record` không được phục hồi chỉ từ `getModifiers()` |
| Class là sealed | `Class.isSealed()`, `getPermittedSubclasses()`, `PermittedSubclasses` attribute | Danh sách lớp được phép kế thừa nằm ngoài tập modifier cơ bản |
| Kiểu generic | `getGenericType()` / `getGenericReturnType()`; `Signature` attribute nếu có | JVM descriptor chủ yếu ghi kiểu đã xóa, không phản ánh đầy đủ generics |
| Annotation | API annotation và annotation attributes phù hợp với retention | Không phải cờ access |
| Tên tham số | `Parameter.getName()` và `isNamePresent()`; `MethodParameters` nếu có | Tên tham số không nằm trong `access_flags` |

Các thông tin bổ sung này vẫn **không biến class file thành mã nguồn đầy đủ**. Ví dụ, `Signature` có thể giúp tìm kiểu generic khai báo nhưng không phục hồi cách sắp dòng hoặc tên import; `Record` mô tả thành phần record nhưng không tái tạo chính xác mọi biểu thức ban đầu.

## 4. Ví dụ runnable: cùng ý nghĩa, cú pháp nguồn khác nhau

Chương trình sau dùng Java 17 trở lên để minh họa ba giới hạn: modifier ngầm định trong interface, kiểu tham số varargs và cách Reflection tách modifier khỏi những thuộc tính khác.

Tạo tệp `FlagSourceDemo.java`:

```java
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

interface Action {
    void execute(); // public abstract ngầm định
}

public class FlagSourceDemo {
    private final int counter = 7;

    public void print(String... values) {
    }

    public void printArray(String[] values) {
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Method action = Action.class.getDeclaredMethod("execute");
        Field field = FlagSourceDemo.class.getDeclaredField("counter");
        Method varargs = FlagSourceDemo.class
                .getDeclaredMethod("print", String[].class);
        Method ordinaryArray = FlagSourceDemo.class
                .getDeclaredMethod("printArray", String[].class);

        System.out.println("interface method: "
                + Modifier.toString(action.getModifiers()));
        System.out.println("field: "
                + Modifier.toString(field.getModifiers()));
        System.out.println("varargs parameter: "
                + varargs.getParameterTypes()[0].getTypeName());
        System.out.println("print is varargs: " + varargs.isVarArgs());
        System.out.println("printArray is varargs: "
                + ordinaryArray.isVarArgs());
        System.out.println("class is public: "
                + Modifier.isPublic(FlagSourceDemo.class.getModifiers()));
    }
}
```

Biên dịch và chạy:

```bash
javac FlagSourceDemo.java
java FlagSourceDemo
```

Stdout:

```text
interface method: public abstract
field: private final
varargs parameter: java.lang.String[]
print is varargs: true
printArray is varargs: false
class is public: true
```

Cách đọc:

1. Trong mã nguồn, `Action.execute()` **không viết** `public abstract`. Nhưng hai thuộc tính đó là ngầm định theo quy tắc interface; khi xem phương thức đã biên dịch, Reflection trả về `public abstract`. Vì vậy, `Modifier.toString` không chứng minh đây là các từ khóa tác giả đã gõ.
2. `counter` thật sự được khai báo `private final`, nên Reflection báo `private final`. Nhưng từ hai cờ ấy không suy ra biểu thức khởi tạo `7`, vị trí trong tệp hay khoảng trắng/cách viết khác của field.
3. `print(String... values)` có dạng tham số nguồn `...`; `printArray(String[] values)` dùng `[]`. Reflection thấy kiểu tham số trực tiếp của cả hai là `String[]`. Phải hỏi `isVarArgs()` mới thấy khác biệt metadata varargs.
4. `Class.isPublic` (qua `Modifier.isPublic`) trả về thuộc tính hiệu lực của lớp. Nó không cho biết toàn bộ body, danh sách import hoặc cách triển khai.

### 4.1. Đối chiếu bằng `javap`

Từ thư mục có các tệp `.class` vừa biên dịch, chạy:

```bash
javap -p FlagSourceDemo
javap -v -p FlagSourceDemo
javap -v -p Action
```

Trong bản `javap -v -p FlagSourceDemo`, hãy tìm `print` và `printArray`:

- Cả hai có **descriptor JVM** `([Ljava/lang/String;)V`, nghĩa là một tham số là mảng `String[]` và trả về `void`.
- `print` có `ACC_VARARGS` trong flag của phương thức.
- `printArray` không có `ACC_VARARGS`.
- `javap -p` có thể trình bày chữ ký ở dạng gần mã Java để dễ đọc, nhưng đây là bản **diễn giải lại từ class file**, không phải bằng chứng về toàn bộ biểu thức, cú pháp hay định dạng nguồn ban đầu.

Trong `Action.class`, phương thức `execute` được biểu diễn là `public abstract` dù khai báo Java chỉ viết `void execute();`. Các modifier hiệu lực và các token thực tế trong source là hai câu hỏi khác nhau.

`javap -v` cũng liệt kê nhiều thuộc tính và thông tin JVM khác. Không nên lấy riêng `flags: ...` để suy luận ra toàn bộ khai báo nguồn, cũng không nên xem đầu ra `javap` là bản Java có thể dựng lại chính xác nguyên văn.

## 5. Các trường hợp dễ suy diễn sai

### 5.1. Modifier ngầm định, từ khóa thừa về nghĩa và cách viết tương đương

Trong interface, các field ngầm định là `public static final`; phương thức thông thường không có body và không đánh dấu `private`, `static` hay `default` sẽ là `public abstract`. Trong interface lồng nhau hoặc khai báo nested phù hợp cũng có các quy tắc ngầm định về `static`. Reflection quan sát **thuộc tính hiệu lực**, không chỉ modifier được ghi ra.

```java
interface Settings {
    int LIMIT = 10; // ngầm định public static final
}
```

Dù tác giả viết `int LIMIT` hay `public static final int LIMIT`, field trong class file có cùng các modifier liên quan. Không dùng `Modifier.toString(field.getModifiers())` để phân biệt hai cách viết.

**Cảnh báo:** quy tắc ngầm định thay đổi theo loại khai báo (class, interface, enum, record, annotation type, field, method...). Không mặc định mọi thành viên thiếu từ khóa đều có cùng quyền truy cập.

### 5.2. `varargs` không tương đương chỉ nhìn `String[]`

`String...` ở vị trí tham số cuối của phương thức có **kiểu mảng** ở JVM. Do đó, chỉ đọc `getParameterTypes()` hoặc descriptor không phân biệt được nó với một tham số `String[]` thông thường. `ACC_VARARGS` và `Method.isVarArgs()` cho biết một thuộc tính runtime bổ sung.

Ngay cả khi metadata varargs hiện diện, điều đó chỉ giúp xác định phương thức **được đánh dấu varargs trong class file**. Một class file có thể được sinh hoặc chỉnh sửa bởi công cụ không phải `javac`; không được khẳng định bạn đã tìm lại được chính xác token hay toàn bộ định dạng nguồn của tác giả.

### 5.3. Generic types và type erasure

Trong Java:

```java
java.util.List<String> names;
java.util.List<Integer> ages;
```

Các field có **descriptor kiểu thô `java.util.List`** do type erasure. Trình biên dịch Java thường ghi thêm `Signature` attribute để giữ thông tin generic mà Reflection dùng với `Field.getGenericType()`. Nếu metadata này thiếu hoặc bị loại bỏ, class file không đủ để khôi phục tham số kiểu gốc chỉ từ descriptor và flags.

Ngay cả khi đọc được `List<String>` qua `Signature`, bạn vẫn không thể xác định chắc chắn tác giả dùng `import java.util.List;` rồi viết `List<String>`, hay viết đầy đủ `java.util.List<String>`.

Cùng nguyên tắc áp dụng cho cú pháp generic method, wildcard và nhiều chi tiết của khai báo kiểu.

### 5.4. Local variable, tên tham số và dấu vết debug

Tên biến cục bộ, line numbers hoặc tên tham số không nằm trong modifier của method. Một số thông tin có thể được ghi vào `LocalVariableTable`, `LineNumberTable` hoặc `MethodParameters` tùy cấu hình biên dịch.

Dùng `javac -parameters` khi muốn lưu tên tham số nguồn theo cơ chế `MethodParameters` phổ biến với `javac`. Có thể kiểm tra `Parameter.isNamePresent()`. Nếu không có tên thật, `Parameter.getName()` có thể trả tên tổng hợp như `arg0`. Việc thiếu metadata **không có nghĩa mã nguồn không có tên tham số**.

Tương tự, `javac -g` có thể tạo metadata debug; nó không lưu đầy đủ mọi token, chú thích và biểu thức nguồn. Với tối ưu hóa hoặc bytecode transformation, quan hệ giữa bytecode và từng dòng nguồn có thể càng khó tái dựng.

### 5.5. Compiler-generated, synthetic và bridge methods

Khi generics hoặc các đặc điểm ngôn ngữ cần bảo đảm tương thích lời gọi ở JVM, compiler có thể sinh thêm method `bridge`. Nó có thể xuất hiện trong `getDeclaredMethods()` dù lập trình viên không khai báo trực tiếp một method riêng tương ứng trong source.

```java
class Parent<T> {
    T value() {
        return null;
    }
}

class Child extends Parent<String> {
    @Override
    String value() {
        return "ok";
    }
}
```

Ở cơ chế biên dịch Java điển hình, `Child` có thể có một method sinh ra để nối chữ ký erased của `Parent.value()` với `Child.value()`. Kiểm tra `Method.isBridge()` và `Method.isSynthetic()` thay vì suy ra rằng mọi `Method` có một khai báo viết tay. Bridge/synthetic là những thuộc tính cấu trúc, không phải các từ khóa bắt buộc tồn tại ở nguồn.

Lưu ý: không tự áp dụng các quy tắc suy luận về nguồn `javac` cho mọi bytecode được tạo bởi compiler/công cụ khác; sự có mặt/vắng mặt của các member tổng hợp phụ thuộc cấu trúc và quá trình sinh mã.

### 5.6. Annotation, record, sealed và đặc tính không mã hóa bằng một modifier

- Annotation có cơ chế **retention**. `@Override` có `RetentionPolicy.SOURCE`, không tồn tại trong class file đầu ra do `javac` tạo; không thể lấy lại bằng cách nhìn access flags. Các annotation được lưu ở retention khác có metadata riêng và Reflection thường chỉ cho phép truy vấn trực tiếp các annotation có retention `RUNTIME`.
- `record` (Java 16+) có metadata về record components, có thể hỏi `Class.isRecord()` và `getRecordComponents()`. `Modifier.isFinal(...)` không chứng minh một class là record; class `final` thông thường cũng final.
- `sealed` (Java 17+) liên quan đến danh sách subtype được phép, đọc qua `isSealed()` và `getPermittedSubclasses()`, tương ứng metadata `PermittedSubclasses`. Không thể dựng lại chính xác chuỗi từ khóa `permits` hoặc cách viết `non-sealed` chỉ bằng `getModifiers()`.
- Method `default` của interface có body và Reflection hỗ trợ `Method.isDefault()`. Không có `Modifier.isDefault(...)`; chỉ xem những bit modifier phổ biến là chưa đủ.

Một attribute riêng có thể giữ lại **một phần** ý nghĩa nguồn, nhưng vẫn không cung cấp toàn bộ cú pháp nguyên văn.

### 5.7. Nested class và nguồn gốc của `static`

Xét:

```java
class Outer {
    static class Nested {
    }
}
```

`Nested` là lớp thành viên `static` trong Java. `Nested.class.getModifiers()` có thể báo `static` vì Reflection xét thông tin thành viên lớp lồng. Trong class file, thông tin này liên quan tới `InnerClasses` attribute; đừng tưởng rằng mọi flag mà `Class.getModifiers()` trả về đều nằm trực tiếp trong `access_flags` của class header.

Đây là ví dụ điển hình cho việc phải xác định **đang đọc cấu trúc class-file nào** và **API Reflection nào đã diễn giải cấu trúc đó**.

## 6. Những gì không được kết luận từ flags

| Quan sát được | Có thể kết luận | Không đủ dữ liệu để kết luận |
| --- | --- | --- |
| `Modifier.isPublic(...) == true` | Thành viên/kiểu có quyền truy cập `public` theo mô hình Reflection | `public` đã được gõ tường minh trong source |
| `Modifier.isAbstract(...) == true` | Phần tử được phản ánh có tính chất `abstract` | Từ khóa `abstract` xuất hiện ở đúng vị trí/cách viết đó |
| `Modifier.isFinal(field.getModifiers())` | Field có thuộc tính `final` | Biểu thức khởi tạo, thứ tự field, ý nghĩa business |
| `Method.isVarArgs() == true` | Method có dấu hiệu varargs ở class file/runtime | Mọi token hoặc vị trí xuống dòng của khai báo nguồn |
| `Method.isSynthetic() == true` | Member được đánh dấu synthetic | Có một khai báo viết tay tương ứng |
| `Method.isBridge() == true` | Đây là bridge method theo Reflection | Người viết source đã tạo một method riêng với chính chữ ký bridge |
| `Field.getType() == List.class` | Kiểu field sau erasure là `List` | Tham số generic chính xác nếu thiếu `Signature` |
| `Class.isRecord() == true` | Runtime nhận diện kiểu record | Toàn bộ văn bản khai báo record gốc |
| `Class.isSealed() == true` | Runtime nhận diện kiểu sealed | Cách sắp xếp, viết tên, import và formatting của `permits` |
| `javap -c` in bytecode | Những chỉ thị JVM của phương thức đã biên dịch | Chính xác cấu trúc `if`/`switch`, tên biến và comment nguồn |

Ví dụ về tính chất nhiều-đến-một: một tác giả có thể viết `import java.util.List; List<String> values;`, còn tác giả khác viết `java.util.List<String> values;`. Hai cách viết thể hiện cùng kiểu thành viên và hoàn toàn có thể biên dịch thành metadata kiểu tương đương. Access flags không chứa lựa chọn cú pháp này.

## 7. Cách thực hành đúng với Reflection và class file

Khi cần khảo sát API Java hoặc xây công cụ documentation/static analysis, hãy phân biệt mục tiêu:

1. **Cần quyền truy cập hoặc thuộc tính runtime?** Dùng `getModifiers()`, `Modifier.isXxx(...)` và `Modifier.toString(...)`; đọc đúng loại member.
2. **Cần cấu trúc JVM?** Dùng `javap -v -p` hoặc thư viện đọc class file để phân tích class header, fields, methods, descriptors, flags và attributes. Kiểm tra nơi chứa metadata thay vì tìm tất cả trong một số nguyên.
3. **Cần thuộc tính nằm ngoài modifier?** Sử dụng API riêng như `isVarArgs()`, `isBridge()`, `isSynthetic()`, `isDefault()`, `isRecord()`, `isSealed()`, `getGenericType()` và `isNamePresent()` khi phù hợp.
4. **Cần tái tạo chính xác Java source?** Truy cập chính **tệp `.java`** và sử dụng parser Java (AST) nếu cần danh sách token, biểu thức, import, comment, annotation và trật tự nguồn. Bytecode decompiler chỉ cho mã nguồn *gần đúng*, không bảo đảm bản gốc.

Có thể dựng tài liệu mang nghĩa như “field được công khai”, “method là varargs”, hoặc “class là record”; đừng tuyên bố “tác giả đã viết nguyên văn khai báo này” khi chỉ có `.class`.

### Kiểm tra tự học

Phân loại các phát biểu sau là **đúng** hay **sai**:

1. `Modifier.toString(method.getModifiers())` tái hiện đúng tất cả từ khóa tác giả đã gõ trong khai báo.
2. Một method interface viết `void run();` có thể được Reflection báo là `public abstract`.
3. Nếu một parameter có `Class<?>` là `String[].class` thì đủ để biết tác giả đã dùng `String...`.
4. Một member xuất hiện trong `getDeclaredMethods()` có thể là method do compiler tạo.
5. `javap -v` đọc được class-file flags và attributes nhưng không bảo đảm phục hồi chính xác comment/import/spacing trong `.java`.
6. Nếu `getModifiers()` thiếu thông tin record hoặc sealed thì `Class` không có API nào khác để kiểm tra các thuộc tính đó.

**Đáp án:** (1) Sai; (2) Đúng; (3) Sai, cần `isVarArgs()` để nhận biết metadata varargs; (4) Đúng; (5) Đúng; (6) Sai, dùng `isRecord()` và `isSealed()` tương ứng.

## 8. Ghi nhớ

**Modifier/flag phản ánh trạng thái đã biên dịch, không phải bản ghi đầy đủ cú pháp nguồn.** Class file có flags, descriptors, bytecode và các attributes riêng; Reflection diễn giải một phần dữ liệu đó thành API thuận tiện. Cùng một thuộc tính hiệu lực có thể xuất phát từ từ khóa viết tường minh hoặc quy tắc ngầm định; một số đặc điểm dùng metadata ngoài `getModifiers()`; compiler còn có thể sinh thành viên không xuất hiện trực tiếp trong source. Khi cần chính xác cú pháp, thứ tự từ khóa, import, tên biến cục bộ, biểu thức hoặc comment, phải đọc nguồn `.java` và phân tích AST tương ứng.

</details>

- [Quay lại đầu trang](#back-to-top)
