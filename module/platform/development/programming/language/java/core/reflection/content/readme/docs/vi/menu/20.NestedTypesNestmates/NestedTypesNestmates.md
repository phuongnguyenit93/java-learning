<a id="back-to-top"></a>

# Kiểu lồng và nest

## Menu
- [getClasses khác getDeclaredClasses](#nested-types-nestmates-001)
- [getDeclaringClass khác getEnclosingClass](#nested-types-nestmates-002)
- [getEnclosingMethod và getEnclosingConstructor](#nested-types-nestmates-003)
- [isMemberClass, isLocalClass và isAnonymousClass](#nested-types-nestmates-004)
- [Static nested class khác non-static inner class](#nested-types-nestmates-005)
- [getNestHost, getNestMembers và isNestmateOf](#nested-types-nestmates-006)
- [Tham số hoặc field tổng hợp để giữ ngữ cảnh bao ngoài](#nested-types-nestmates-007)
- [Tên và cấu trúc do compiler sinh không phải hợp đồng ứng dụng](#nested-types-nestmates-008)
- [Hidden class, isHidden và giới hạn khám phá bằng Class.forName](#nested-types-nestmates-009)

## <a id="nested-types-nestmates-001">getClasses khác getDeclaredClasses</a>

<details>
<summary>Xem chi tiết</summary>

# `getClasses()` khác `getDeclaredClasses()` trong Java Reflection

## 1. Hai phương thức trả về điều gì?

Khi dùng Reflection, mỗi đối tượng `Class<?>` mô tả một kiểu Java. Hai phương thức sau đều trả về `Class<?>[]` đại diện cho **kiểu thành viên lồng bên trong** (member class/member interface) của kiểu đang xét:

- `clazz.getClasses()`: lấy **các kiểu thành viên `public`** được khai báo ở chính kiểu đó **và các kiểu thành viên `public` được kế thừa từ lớp cha**.
- `clazz.getDeclaredClasses()`: lấy **tất cả kiểu thành viên khai báo trực tiếp** trong kiểu đó, bất kể `public`, `protected`, package-private hay `private`; **không lấy kiểu thành viên được kế thừa**.

Tên phương thức có ý nghĩa quan trọng: `getClasses()` hướng tới *public members* nhìn theo lớp hiện tại; `getDeclaredClasses()` hướng tới *declarations* trực tiếp ở lớp hiện tại. Cả hai trả về **kiểu thành viên** (nested types), không phải mọi lớp mà chương trình sử dụng, mọi kiểu trong package hay tất cả lớp đã được JVM nạp.

| Tiêu chí | `getClasses()` | `getDeclaredClasses()` |
| --- | --- | --- |
| Điều kiện truy cập | Chỉ thành viên `public` | Mọi mức truy cập |
| Thành viên khai báo trực tiếp | Có, nếu `public` | Có, không phân biệt mức truy cập |
| Thành viên từ lớp cha | Có, nếu là thành viên `public` được kế thừa | Không |
| Lớp local trong phương thức | Không | Không |
| Lớp anonymous tạo trong phương thức | Không | Không |
| Kiểu lồng trong một kiểu thành viên khác | Không tự đệ quy | Không tự đệ quy |
| Kiểu dữ liệu trả về | `Class<?>[]` | `Class<?>[]` |

**Member type** là kiểu được khai báo trực tiếp trong phần thân của một class hoặc interface. Nhóm này có thể gồm nested class, nested interface, enum, record và annotation interface. Nested class có thể là `static` hoặc inner class không `static`; sự khác biệt giữa hai phương thức ở đây dựa vào **quyền truy cập và nơi khai báo**, không dựa vào việc lớp lồng có `static` hay không.

## 2. Ví dụ chạy được trên Java 17+

Lưu toàn bộ chương trình dưới tên **`NestedReflectionDemo.java`**:

```java
import java.util.Arrays;
import java.util.stream.Collectors;

public class NestedReflectionDemo {
    public static class Base {
        public static class BasePublic {}
        protected static class BaseProtected {}
        private static class BasePrivate {}
    }

    public static class Derived extends Base {
        public static class OwnPublic {}
        protected static class OwnProtected {}
        static class OwnPackage {}
        private static class OwnPrivate {}
        public interface Marker {}
        public enum State { READY }
        public record Key(int id) {}

        public void sample() {
            class Local {}
            Object anonymous = new Object() {};
        }
    }

    private static void show(String label, Class<?>[] types) {
        String names = Arrays.stream(types)
                .map(Class::getSimpleName)
                .sorted()
                .collect(Collectors.joining(", ", "[", "]"));
        System.out.println(label + " -> " + names);
    }

    public static void main(String[] args) {
        show("getClasses()", Derived.class.getClasses());
        show("getDeclaredClasses()", Derived.class.getDeclaredClasses());
        show("Base.getDeclaredClasses()", Base.class.getDeclaredClasses());
    }
}
```

Biên dịch và chạy:

```bash
javac NestedReflectionDemo.java
java NestedReflectionDemo
```

Kết quả:

```text
getClasses() -> [BasePublic, Key, Marker, OwnPublic, State]
getDeclaredClasses() -> [Key, Marker, OwnPackage, OwnPrivate, OwnProtected, OwnPublic, State]
Base.getDeclaredClasses() -> [BasePrivate, BaseProtected, BasePublic]
```

Chương trình cố ý dùng `sorted()` trước khi in vì **API không cam kết thứ tự phần tử** trong các mảng trả về. Kết quả đã được kiểm tra khi biên dịch và chạy; các tên được in theo thứ tự chữ cái.

### Giải thích kết quả

1. `Derived.class.getClasses()` có `OwnPublic`, `Marker`, `State`, `Key` vì chúng là kiểu thành viên `public` khai báo tại `Derived`. Nó còn có `BasePublic` vì kiểu này là thành viên `public` được kế thừa từ `Base`.
2. `Derived.class.getClasses()` **không có** `OwnProtected`, `OwnPackage`, `OwnPrivate`, `BaseProtected` hoặc `BasePrivate`: các kiểu đó không phải thành viên `public` được trả về.
3. `Derived.class.getDeclaredClasses()` có đầy đủ bảy kiểu thành viên khai báo trực tiếp tại `Derived`, từ `public` đến `private`; **không có** `BasePublic` vì `BasePublic` được khai báo ở `Base`.
4. `Base.class.getDeclaredClasses()` cho thấy cả ba kiểu được khai báo trực tiếp tại `Base`. Việc kiểu đó `private` hay `protected` không khiến nó bị loại khỏi kết quả của `getDeclaredClasses()`.
5. `Local` và lớp anonymous trong `sample()` không xuất hiện trong hai kết quả: chúng nằm trong phương thức, không phải member type khai báo trực tiếp trong thân `Derived`.

**Điểm dễ nhầm:** `getDeclaredClasses()` cho phép *nhìn thấy metadata* của kiểu `private`, nhưng điều này không tự động cho phép truy cập constructor, field hay method không công khai của kiểu đó. Quyền truy cập khi thao tác sâu hơn bằng Reflection vẫn chịu các quy tắc truy cập và giới hạn module của Java.

## 3. Phạm vi khai báo và phạm vi kế thừa

Xét cấu trúc:

```text
Base
├── public BasePublic
├── protected BaseProtected
└── private BasePrivate
     ↑
   Derived extends Base
   ├── public OwnPublic
   ├── protected OwnProtected
   ├── package-private OwnPackage
   ├── private OwnPrivate
   ├── public Marker (interface)
   ├── public State (enum)
   └── public Key (record)
```

- Muốn biết `Derived` **tự khai báo** những nested types nào, dùng `Derived.class.getDeclaredClasses()`.
- Muốn biết các **public member types của `Derived`**, bao gồm loại được kế thừa từ superclass, dùng `Derived.class.getClasses()`.
- Muốn khảo sát **mọi nested type được khai báo trong từng lớp của chuỗi kế thừa**, phải duyệt từng lớp rồi gọi `getDeclaredClasses()` riêng. Kết quả khảo sát như vậy **không đồng nghĩa** với các thành viên thực sự có thể kế thừa/truy cập ở lớp con; ví dụ, thành viên `private` của lớp cha vẫn xuất hiện khi khảo sát trực tiếp lớp cha.

Ví dụ minh họa cách duyệt lớp cha (đoạn dưới là fragment đặt trong một phương thức có sẵn biến `Class<?> target`):

```java
for (Class<?> current = target;
     current != null;
     current = current.getSuperclass()) {
    for (Class<?> nested : current.getDeclaredClasses()) {
        System.out.println(current.getName() + " declares " + nested.getName());
    }
}
```

Vòng lặp này chỉ đi theo **superclass chain** và in ra những khai báo trực tiếp của từng lớp; nó không tự duyệt superinterface hay các kiểu lồng nhiều tầng. Khi cần xử lý interface hoặc nested type đệ quy, phải xác định rõ tiêu chí tìm kiếm và bổ sung việc duyệt tương ứng.

## 4. Không nhầm với các API liên quan

- `getDeclaringClass()`: từ một kiểu thành viên, tìm kiểu **khai báo trực tiếp** nó. Ví dụ, `Derived.OwnPublic.class.getDeclaringClass()` trả về `Derived.class`.
- `getEnclosingClass()`: tìm lớp bao quanh về mặt khai báo; hữu ích cả với một số trường hợp local/anonymous class. Đây là câu hỏi khác với việc liệt kê member types.
- `getNestHost()` và `getNestMembers()`: nói về **nestmate** của JVM, liên quan đến quy tắc truy cập private giữa các lớp cùng nest. Danh sách nestmate không phải là kết quả tương đương của `getDeclaredClasses()` hay `getClasses()`.
- `getDeclaredMethods()`, `getDeclaredFields()` và `getDeclaredConstructors()`: liệt kê các loại thành viên khác, không thay thế cho việc tìm nested class/interface.

Hai phương thức chính đều chỉ xét **một cấp member type**. Nếu `Outer` khai báo `Inner` và `Inner` lại khai báo `Deep`, thì `Outer.class.getDeclaredClasses()` có thể chứa `Inner`, nhưng **không trực tiếp chứa `Deep`**. Muốn tìm `Deep`, tiếp tục xét `Inner.class.getDeclaredClasses()`.

## 5. Chọn phương thức trong thực tế

| Nhu cầu | Lựa chọn và lý do |
| --- | --- |
| Hiển thị các nested type `public` mà một lớp cung cấp cho người dùng API, kể cả loại kế thừa | `getClasses()` |
| Viết công cụ phân tích cấu trúc khai báo của một lớp, kể cả nested type `private` | `getDeclaredClasses()` |
| Tìm tất cả khai báo nested type ở nhiều lớp trong chuỗi kế thừa | Duyệt superclass và gọi `getDeclaredClasses()` trên từng lớp |
| Tìm lớp local, anonymous hoặc mọi class đã nạp | Hai phương thức trên không giải quyết trực tiếp |
| Tìm thành viên của nested type ở độ sâu lớn hơn một cấp | Tự duyệt đệ quy từ các member type tìm được |

Khi xây dựng bộ quét metadata, có thể cần xử lý kiểu trùng tên đơn giản ở nhiều lớp. Để định danh chắc chắn, nên dùng `Class.getName()` hoặc so sánh chính đối tượng `Class<?>`, thay vì chỉ dựa vào `getSimpleName()`. Cũng không nên dựa vào vị trí phần tử trong mảng vì thứ tự không được bảo đảm.

## 6. Những lỗi hiểu sai thường gặp

1. **“`getDeclaredClasses()` lấy cả kiểu của cha vì nó lấy mọi mức truy cập.”** Sai. “Declared” giới hạn vào những member types **khai báo trực tiếp tại kiểu nhận lệnh gọi**.
2. **“`getClasses()` lấy tất cả nested class của lớp hiện tại.”** Sai. Nó chỉ trả về **public member types**; `protected`, package-private và `private` bị loại.
3. **“Hai API chỉ nhận `static nested class`.”** Sai. Inner member class không `static` cũng là một kiểu thành viên và được xử lý theo cùng quy tắc về nơi khai báo/quyền truy cập.
4. **“`getDeclaredClasses()` trả về lớp local và anonymous.”** Sai. Chúng không phải member classes khai báo trực tiếp trong thân kiểu đang xét.
5. **“Phần tử đầu tiên luôn là kiểu khai báo đầu tiên.”** Sai. Không có hợp đồng về thứ tự; phải sắp xếp trước khi cần output ổn định.
6. **“Thấy `private nested class` trong kết quả có nghĩa gọi được constructor private.”** Sai. Liệt kê metadata và quyền truy cập để tạo đối tượng/gọi thành viên là hai vấn đề khác nhau.

## 7. Tự kiểm tra

**Câu hỏi:** Nếu `Parent` có `public static class Shared` và `private static class Secret`, còn `Child extends Parent` có `private static class Internal`, thì:

- `Child.class.getClasses()` có kiểu nào trong ba kiểu trên?
- `Child.class.getDeclaredClasses()` có kiểu nào trong ba kiểu trên?
- Cách nào tìm được `Secret` bằng Reflection?

**Đáp án:** `getClasses()` của `Child` có `Shared`; `getDeclaredClasses()` của `Child` có `Internal`; muốn liệt kê metadata của `Secret` thì gọi `Parent.class.getDeclaredClasses()`. Đây là ví dụ điển hình cho việc **`public` + kế thừa** khác với **khai báo trực tiếp + mọi mức truy cập**.

## Tài liệu tham khảo

- [Java SE 21 — `java.lang.Class`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html): hợp đồng của `getClasses()`, `getDeclaredClasses()` và các phương thức liên quan.
- [Java Language Specification, `8.5 — Member Class and Interface Declarations`](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.5): định nghĩa member classes và member interfaces.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nested-types-nestmates-002">getDeclaringClass khác getEnclosingClass</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt `getDeclaringClass()` và `getEnclosingClass()` trong Java Reflection

## 1. Vấn đề cần hiểu

Khi phản chiếu một kiểu Java bằng `Class<?>`, hai phương thức sau đều trả về một đối tượng `Class<?>` hoặc `null`:

- `getDeclaringClass()`: trả về **kiểu khai báo trực tiếp kiểu thành viên (member type) đang xét**. Nếu kiểu đang xét không phải thành viên của một class/interface khác, kết quả là `null`.
- `getEnclosingClass()`: trả về **class bao quanh trực tiếp** kiểu đang xét. Với member class, đó chính là declaring class; với local class và anonymous class, đó là class chứa vị trí khai báo biểu thức/lớp cục bộ.

Khác biệt quan trọng: **một local class hoặc anonymous class có lớp bao quanh nhưng không phải member class**. Vì vậy nó có thể nhận `getDeclaringClass() == null` và `getEnclosingClass() != null`.

Đây là quan hệ **cấu trúc khai báo trong mã nguồn**, không phải quan hệ kế thừa qua `extends`, không phải kiểu của đối tượng ở runtime và cũng không khẳng định class con đang giữ tham chiếu đến một instance của outer class.

## 2. Member type, local class và anonymous class

Xét cấu trúc khái niệm:

~~~text
Outer (top-level class)
├── StaticMember              // static nested member class
├── InnerMember               // non-static member (inner) class
└── method()
    ├── Local                 // local class trong thân method
    └── new Runnable() {...}  // anonymous class trong method
~~~

`StaticMember` và `InnerMember` là **member class** được khai báo ở mức thân `Outer`; còn `Local` và anonymous class nằm bên trong một khối lệnh/biểu thức. Cả bốn đều có `Outer` là enclosing class trong ví dụ này, nhưng **chỉ hai member class có declaring class là `Outer`**.

Một local class cũng có thể nằm trong constructor hoặc initializer. Anonymous class có thể xuất hiện tại chỗ khởi tạo field. Vị trí khác nhau ảnh hưởng đến `getEnclosingMethod()` / `getEnclosingConstructor()`, nhưng không biến chúng thành member class.

## 3. Bảng so sánh kết quả

Giả sử `Outer` là top-level class và các kiểu bên dưới được khai báo trực tiếp trong `Outer`:

| Kiểu đang kiểm tra | `getDeclaringClass()` | `getEnclosingClass()` | Giải thích |
| --- | --- | --- | --- |
| `Outer.class` | `null` | `null` | Top-level class không nằm trong class khác |
| `Outer.StaticMember.class` | `Outer.class` | `Outer.class` | Static nested member class |
| `Outer.InnerMember.class` | `Outer.class` | `Outer.class` | Non-static member class (inner class) |
| Một member interface bên trong `Outer` | `Outer.class` | `Outer.class` | Interface thành viên cũng có declaring/enclosing type |
| Local class khai báo trực tiếp trong method của `Outer` | `null` | `Outer.class` | Local class không là member class |
| Anonymous class tạo trực tiếp trong method của `Outer` | `null` | `Outer.class` | Anonymous class không là member class |
| Local class khai báo trực tiếp trong constructor của `Outer` | `null` | `Outer.class` | Nằm trong constructor nhưng vẫn có enclosing class |
| `Outer.StaticMember[].class` | `null` | `null` | Bản thân array class không là nested/member class |
| `int.class` hoặc `void.class` | `null` | `null` | Primitive/void không có quan hệ bao quanh |

**Lưu ý về độ sâu:** nếu `Nested` được khai báo là member class trong `Outer.InnerMember` thì cả hai phương thức của `Nested.class` đều trả về `Outer.InnerMember.class`, tức **kiểu bao quanh trực tiếp**, không nhảy thẳng về `Outer.class`.

## 4. Ví dụ Java 17+ có thể chạy độc lập

Lưu toàn bộ chương trình sau vào tệp **`DeclaringVsEnclosingDemo.java`**:

~~~java
public class DeclaringVsEnclosingDemo {
    static class StaticMember {}
    class InnerMember {}

    private final Class<?> localFromConstructor;

    DeclaringVsEnclosingDemo() {
        class ConstructorLocal {}
        localFromConstructor = ConstructorLocal.class;
    }

    static Class<?> localFromMethod() {
        class MethodLocal {}
        return MethodLocal.class;
    }

    static Class<?> anonymousFromMethod() {
        Runnable runnable = new Runnable() {
            @Override public void run() {}
        };
        return runnable.getClass();
    }

    static String shortName(Class<?> type) {
        return type == null ? "null" : type.getSimpleName();
    }

    static void show(String label, Class<?> type) {
        System.out.printf("%s: declaring=%s, enclosing=%s, member=%s, local=%s, anonymous=%s%n",
                label,
                shortName(type.getDeclaringClass()),
                shortName(type.getEnclosingClass()),
                type.isMemberClass(), type.isLocalClass(), type.isAnonymousClass());
    }

    public static void main(String[] args) {
        Class<?> local = localFromMethod();
        Class<?> anonymous = anonymousFromMethod();
        Class<?> fromConstructor = new DeclaringVsEnclosingDemo().localFromConstructor;

        show("top-level", DeclaringVsEnclosingDemo.class);
        show("static member", StaticMember.class);
        show("inner member", InnerMember.class);
        show("local in method", local);
        show("anonymous in method", anonymous);
        show("local in constructor", fromConstructor);
        show("array of member", StaticMember[].class);
        show("primitive", int.class);

        System.out.println("local enclosing method: " + local.getEnclosingMethod().getName());
        System.out.println("constructor enclosing constructor: "
                + fromConstructor.getEnclosingConstructor().getName());
        System.out.println("anonymous simple name: '" + anonymous.getSimpleName() + "'");
        System.out.println("local canonical name: " + local.getCanonicalName());
    }
}
~~~

Từ thư mục chứa tệp, biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac DeclaringVsEnclosingDemo.java
java DeclaringVsEnclosingDemo
~~~

**Kết quả stdout (đã kiểm tra với JDK 21):**

~~~text
top-level: declaring=null, enclosing=null, member=false, local=false, anonymous=false
static member: declaring=DeclaringVsEnclosingDemo, enclosing=DeclaringVsEnclosingDemo, member=true, local=false, anonymous=false
inner member: declaring=DeclaringVsEnclosingDemo, enclosing=DeclaringVsEnclosingDemo, member=true, local=false, anonymous=false
local in method: declaring=null, enclosing=DeclaringVsEnclosingDemo, member=false, local=true, anonymous=false
anonymous in method: declaring=null, enclosing=DeclaringVsEnclosingDemo, member=false, local=false, anonymous=true
local in constructor: declaring=null, enclosing=DeclaringVsEnclosingDemo, member=false, local=true, anonymous=false
array of member: declaring=null, enclosing=null, member=false, local=false, anonymous=false
primitive: declaring=null, enclosing=null, member=false, local=false, anonymous=false
local enclosing method: localFromMethod
constructor enclosing constructor: DeclaringVsEnclosingDemo
anonymous simple name: ''
local canonical name: null
~~~

### Đọc kết quả và lý do

1. **Hai member class:** `StaticMember` có từ khóa `static`, còn `InnerMember` không có; tuy vậy cả hai đều là member class. Hai API đang xét đều trả về `DeclaringVsEnclosingDemo.class`. **Không** dựa vào hai API này để suy ra thành viên có `static` hay không.
2. **Local class trong method:** `MethodLocal` được khai báo trong thân `localFromMethod()`. Nó có `isLocalClass() == true`, declaring class là `null`, enclosing class là `DeclaringVsEnclosingDemo.class`. `getEnclosingMethod()` xác định method `localFromMethod`.
3. **Anonymous class trong method:** biểu thức `new Runnable() { ... }` tạo anonymous class thực sự. Nó có `isAnonymousClass() == true`, declaring class `null`, enclosing class là `DeclaringVsEnclosingDemo.class`. Tên đơn giản của nó là chuỗi rỗng; đây không phải local class có tên.
4. **Local class trong constructor:** `ConstructorLocal` có enclosing class như hai trường hợp trên, nhưng `getEnclosingConstructor()` trả về constructor của `DeclaringVsEnclosingDemo` và `getEnclosingMethod()` trả về `null`.
5. **Array và primitive:** dù kiểu phần tử của `StaticMember[].class` là member class, **bản thân kiểu mảng** không nhận declaring/enclosing class. Muốn phân tích kiểu phần tử, dùng `getComponentType()` rồi kiểm tra lớp phần tử.

## 5. Các API liên quan: dùng đúng mục đích

| API | Dùng để biết điều gì? |
| --- | --- |
| `Class.isMemberClass()` | Kiểu có phải member class hay không |
| `Class.isLocalClass()` | Kiểu có phải local class hay không |
| `Class.isAnonymousClass()` | Kiểu có phải anonymous class hay không |
| `Class.getEnclosingMethod()` | Method trực tiếp chứa local/anonymous class, nếu có |
| `Class.getEnclosingConstructor()` | Constructor trực tiếp chứa local/anonymous class, nếu có |
| `Class.getSuperclass()` | Lớp cha trong quan hệ kế thừa, độc lập với enclosing class |
| `Class.getDeclaredClasses()` | Các member class/interface khai báo trong class đang xét; không dùng để liệt kê local/anonymous class |
| `Class.getComponentType()` | Kiểu phần tử của một array class |
| `Class.getCanonicalName()` | Tên chuẩn, thường `null` với local/anonymous class |

Nếu local/anonymous class được khai báo trong **instance initializer, static initializer hoặc phần khởi tạo field**, nó vẫn có thể có `getEnclosingClass() != null` trong khi **cả `getEnclosingMethod()` lẫn `getEnclosingConstructor()` đều trả về `null`**. Vì vậy, không được kết luận “không có enclosing class” chỉ từ hai phương thức sau.

Tên lấy bằng `getName()` cho local/anonymous class thường có ký hiệu `$` và số thứ tự phụ thuộc vào cách compiler tạo bytecode. Không phân loại bằng cách tách chuỗi tên đó: dùng những API phân loại của `Class`.

## 6. Cách áp dụng khi viết công cụ Reflection

Khi cần phân loại kiểu theo **vị trí khai báo**, nên kiểm tra loại trước rồi mới lấy ngữ cảnh:

~~~java
static String origin(Class<?> type) {
    if (type.isMemberClass()) {
        return "Member of " + type.getDeclaringClass().getName();
    }
    if (type.isLocalClass()) {
        return "Local in " + type.getEnclosingClass().getName();
    }
    if (type.isAnonymousClass()) {
        return "Anonymous in " + type.getEnclosingClass().getName();
    }
    return "Other kind of type";
}
~~~

Đây là **đoạn phương thức minh họa** để đặt vào một class Java khác; nó không phải chương trình độc lập và không tự in ra stdout. Nó thể hiện cách phân loại thông thường cho các kiểu do mã nguồn Java biên dịch. Nếu đang xử lý class được sinh bằng bytecode tooling, hidden class hoặc metadata không chuẩn, cần kiểm tra riêng hợp đồng của công cụ sinh class.

Để tìm **outer instance** của một inner class, cũng không thể lấy từ kết quả của hai API này: chúng trả về **đối tượng mô tả kiểu `Class<?>`**, không trả về object của outer instance. Việc class là member cũng không đồng nghĩa có outer instance; static nested class là ví dụ rõ nhất.

## 7. Đừng nhầm với quan hệ “nest” của JVM

Trong JVM, `getNestHost()` và `getNestMembers()` phản ánh **nest membership** phục vụ mô hình truy cập thành viên private giữa các nestmate (Java 11+). Đây là quan hệ metadata/runtime khác với “class được khai báo bên trong class nào”.

Ví dụ, với chuỗi khai báo `Outer → Inner → Deep`, enclosing/declaring class trực tiếp của `Deep` là `Inner`; còn nest host của các kiểu được Java compiler tạo trong nest đó thông thường là `Outer`. Do vậy, **`getNestHost()` không thay thế `getEnclosingClass()`** khi bạn cần biết lớp bao quanh gần nhất.

## 8. Lỗi thường gặp và cách tránh

- **Cho rằng hai API luôn tương đương:** chỉ đúng với các member type thông thường. Local và anonymous class là các phản ví dụ quan trọng.
- **Thấy `getDeclaringClass() == null` rồi coi là top-level:** sai; local, anonymous, array, primitive và `void` cũng có thể cho kết quả đó. Hãy kiểm tra `isLocalClass()`, `isAnonymousClass()`, `isArray()`, `isPrimitive()` khi cần.
- **Suy ra có outer object vì `getEnclosingClass() != null`:** sai. Static nested member class, local class trong static method vẫn có enclosing class nhưng không bắt buộc có một outer instance.
- **Dùng `getSuperclass()` để suy ra enclosing class:** lớp cha và lớp bao quanh là hai quan hệ khác nhau. Một local class có thể `extends` class bất kỳ nhưng vẫn được khai báo bên trong một class khác.
- **Dùng `getDeclaredClasses()` để tìm local class:** phương thức này liệt kê member types, không duyệt các khai báo local trong mọi method/constructor.
- **Cho rằng `getEnclosingMethod() == null` nghĩa là không có enclosing class:** local/anonymous class trong constructor hoặc initializer là các phản ví dụ.

## 9. Tự kiểm tra

**Câu 1.** Với `class Host { static class Item {} }`, `Item.class.getDeclaringClass()` và `Item.class.getEnclosingClass()` trả về gì?

**Đáp án:** cả hai đều trả về `Host.class`. `static` không loại bỏ quan hệ khai báo thành viên.

**Câu 2.** Trong method của `Host` có `class Local {}`. Có thể dùng `Local.class.getDeclaringClass() == null` để khẳng định `Local` là top-level class không?

**Đáp án:** không. `Local` là local class; declaring class bằng `null`, enclosing class bằng `Host.class` và `isLocalClass()` là `true`.

**Câu 3.** `getEnclosingClass()` trả về `Host.class` có chứng minh một object `Host` đang bao quanh object hiện tại không?

**Đáp án:** không. Kết quả mô tả vị trí khai báo của **kiểu**, không cung cấp outer instance.

## 10. Ghi nhớ

- **Declaring = ai khai báo mình như một member?** `getDeclaringClass()` có giá trị khi kiểu là member của một kiểu khác.
- **Enclosing = mình nằm trực tiếp trong class nào?** `getEnclosingClass()` còn hữu ích cho local và anonymous class.
- **Member:** thường cả hai bằng nhau. **Local/anonymous:** declaring `null`, enclosing thường là class chứa khai báo. **Top-level:** cả hai `null`.
- Khi cần biết “member/local/anonymous”, hỏi trực tiếp `isMemberClass()`, `isLocalClass()` và `isAnonymousClass()`, đừng suy diễn từ tên class.

**Tài liệu tham khảo chính thức:** [Java SE 17 — `java.lang.Class`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html), đặc biệt các phương thức `getDeclaringClass()`, `getEnclosingClass()`, `getEnclosingMethod()`, `getEnclosingConstructor()` và các hàm kiểm tra loại class.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nested-types-nestmates-003">getEnclosingMethod và getEnclosingConstructor</a>

<details>
<summary>Xem chi tiết</summary>

# getEnclosingMethod và getEnclosingConstructor

## 1. Hai API này trả lời câu hỏi gì?

Khi gặp một lớp cục bộ hoặc lớp vô danh, ta có thể muốn biết: **phần khai báo lớp đó nằm trong phương thức hay hàm khởi tạo nào?** Đây là thông tin về vị trí khai báo trong mã nguồn, được biểu diễn bằng metadata của lớp khi chạy.

Ví dụ, một phương thức tạo tác vụ bằng `new Runnable() { ... }`. Đối tượng tác vụ có thể được trả về và sử dụng sau khi phương thức đã kết thúc. Lớp vô danh của nó vẫn có thông tin về phương thức bao quanh phần khai báo.

Hai phương thức trên `Class<?>` phục vụ việc truy vấn này:

~~~java
// Chữ ký rút gọn của các API trên java.lang.Class:
public Method getEnclosingMethod();
public Constructor<?> getEnclosingConstructor();
~~~

`Method` và `Constructor` thuộc gói `java.lang.reflect`. Chúng mô tả một phương thức hoặc hàm khởi tạo; việc nhận các đối tượng này không tự thực thi mã được mô tả.

Trước khi đi tiếp, cần phân biệt ba khái niệm:

- **Lớp cục bộ (local class):** lớp có tên được khai báo trong một khối, chẳng hạn thân phương thức hoặc thân hàm khởi tạo.
- **Lớp vô danh (anonymous class):** lớp được khai báo ngay trong biểu thức tạo đối tượng, ví dụ `new Runnable() { ... }`.
- **Lớp thành viên (member class):** lớp được khai báo trực tiếp trong thân một lớp khác, ở cấp thành viên. Nó có thể có hoặc không có `static`.

Cả ba đều có thể liên quan đến lớp bao quanh, nhưng chỉ hai nhóm đầu có thể gắn trực tiếp với một phương thức hoặc hàm khởi tạo bao quanh. Không suy ra kết quả hai API chỉ từ việc tên lớp có ký tự `$`.

## 2. Quy tắc đọc kết quả

Với `Class<?> type`:

- `type.getEnclosingMethod()` trả về phương thức trực tiếp bao quanh nếu `type` biểu diễn lớp cục bộ hoặc lớp vô danh nằm trong phương thức đó; trường hợp khác trả về `null`.
- `type.getEnclosingConstructor()` trả về hàm khởi tạo trực tiếp bao quanh nếu `type` biểu diễn lớp cục bộ hoặc lớp vô danh nằm trong hàm khởi tạo đó; trường hợp khác trả về `null`.

Với một lớp thông thường do trình biên dịch Java sinh ra, hai kết quả không đồng thời khác `null`: một vị trí khai báo không đồng thời nằm trực tiếp trong cả phương thức và hàm khởi tạo.

| Vị trí khai báo của lớp đang xét | `getEnclosingMethod()` | `getEnclosingConstructor()` |
| --- | --- | --- |
| Lớp cục bộ hoặc vô danh trong phương thức | Phương thức bao quanh | `null` |
| Lớp cục bộ hoặc vô danh trong hàm khởi tạo | `null` | Hàm khởi tạo bao quanh |
| Lớp cục bộ hoặc vô danh trong khối khởi tạo đối tượng | `null` | `null` |
| Lớp cục bộ hoặc vô danh trong khối khởi tạo tĩnh | `null` | `null` |
| Lớp vô danh ngay trong biểu thức khởi tạo trường | `null` | `null` |
| Lớp thành viên, có hoặc không có `static` | `null` | `null` |
| Lớp ở cấp cao nhất | `null` | `null` |

**Hai kết quả cùng bằng `null` không chứng minh lớp nằm ở cấp cao nhất.** Lớp thành viên, lớp cục bộ trong khối khởi tạo và lớp vô danh trong biểu thức khởi tạo trường đều là những phản ví dụ.

Các token như `int.class`, `void.class` và `String[].class` cũng không có phương thức hoặc hàm khởi tạo khai báo bao quanh; cả hai API trả về `null`. Mảng của một lớp lồng không vì vậy mà có cùng kết quả với lớp phần tử.

Quy tắc trả về và những trường hợp khởi tạo được nêu trong tài liệu [Class.getEnclosingMethod](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#getEnclosingMethod()) và [Class.getEnclosingConstructor](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html#getEnclosingConstructor()).

## 3. Chương trình hoàn chỉnh: đối chiếu 11 trường hợp

Lưu mã sau vào tệp `EnclosingExecutableDemo.java`. Chương trình dùng Java 17 trở lên, không cần thư viện ngoài.

~~~java
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

public class EnclosingExecutableDemo {
    static class Member {
    }

    class InnerMember {
    }

    static Class<?> localInMethod() {
        class Local {
        }
        return Local.class;
    }

    static Class<?> anonymousInMethod() {
        return new Runnable() {
            @Override
            public void run() {
            }
        }.getClass();
    }

    static class Box {
        final Class<?> localType;
        final Class<?> anonymousType;

        Box(int id) {
            class Local {
            }
            localType = Local.class;
            anonymousType = new Runnable() {
                @Override
                public void run() {
                }
            }.getClass();
        }
    }

    static Class<?> staticBlockType;

    static {
        class StaticLocal {
        }
        staticBlockType = StaticLocal.class;
    }

    final Class<?> instanceBlockType;

    {
        class InstanceLocal {
        }
        instanceBlockType = InstanceLocal.class;
    }

    final Runnable fieldTask = new Runnable() {
        @Override
        public void run() {
        }
    };

    static final Runnable STATIC_FIELD_TASK = new Runnable() {
        @Override
        public void run() {
        }
    };

    static String className(Class<?> type) {
        return type == null ? "null" : type.getSimpleName();
    }

    static String parameters(Executable executable) {
        return Arrays.stream(executable.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", ", "(", ")"));
    }

    static String methodName(Method method) {
        return method == null ? "null"
                : method.getDeclaringClass().getSimpleName()
                  + "." + method.getName() + parameters(method);
    }

    static String constructorName(Constructor<?> constructor) {
        return constructor == null ? "null"
                : constructor.getDeclaringClass().getSimpleName()
                  + parameters(constructor);
    }

    static void inspect(String label, Class<?> type) {
        System.out.println(label
                + " -> enclosing=" + className(type.getEnclosingClass())
                + "; method=" + methodName(type.getEnclosingMethod())
                + "; constructor=" + constructorName(type.getEnclosingConstructor())
                + "; declaring=" + className(type.getDeclaringClass()));
    }

    public static void main(String[] args) {
        EnclosingExecutableDemo demo = new EnclosingExecutableDemo();
        Box box = new Box(7);

        inspect("top-level", EnclosingExecutableDemo.class);
        inspect("static member", Member.class);
        inspect("inner member", InnerMember.class);
        inspect("method local", localInMethod());
        inspect("method anonymous", anonymousInMethod());
        inspect("constructor local", box.localType);
        inspect("constructor anonymous", box.anonymousType);
        inspect("static block", staticBlockType);
        inspect("instance block", demo.instanceBlockType);
        inspect("instance field", demo.fieldTask.getClass());
        inspect("static field", STATIC_FIELD_TASK.getClass());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 EnclosingExecutableDemo.java
java EnclosingExecutableDemo
~~~

Kết quả chính xác:

~~~text
top-level -> enclosing=null; method=null; constructor=null; declaring=null
static member -> enclosing=EnclosingExecutableDemo; method=null; constructor=null; declaring=EnclosingExecutableDemo
inner member -> enclosing=EnclosingExecutableDemo; method=null; constructor=null; declaring=EnclosingExecutableDemo
method local -> enclosing=EnclosingExecutableDemo; method=EnclosingExecutableDemo.localInMethod(); constructor=null; declaring=null
method anonymous -> enclosing=EnclosingExecutableDemo; method=EnclosingExecutableDemo.anonymousInMethod(); constructor=null; declaring=null
constructor local -> enclosing=Box; method=null; constructor=Box(int); declaring=null
constructor anonymous -> enclosing=Box; method=null; constructor=Box(int); declaring=null
static block -> enclosing=EnclosingExecutableDemo; method=null; constructor=null; declaring=null
instance block -> enclosing=EnclosingExecutableDemo; method=null; constructor=null; declaring=null
instance field -> enclosing=EnclosingExecutableDemo; method=null; constructor=null; declaring=null
static field -> enclosing=EnclosingExecutableDemo; method=null; constructor=null; declaring=null
~~~

Chương trình chủ động in tên lớp và chữ ký theo cách ngắn gọn. Nó không dựa vào số thứ tự trong tên nhị phân của lớp vô danh, cũng không dựa vào cách `Method.toString()` ghép toàn bộ thông tin.

### 3.1. Lớp trong phương thức

`localInMethod()` khai báo lớp `Local` rồi trả về `Local.class`. Khi `main()` truy vấn token đó, kết quả vẫn là `EnclosingExecutableDemo.localInMethod()`.

Tương tự, lớp vô danh tạo ra trong `anonymousInMethod()` gắn với phương thức này. Phương thức bao quanh là phương thức chứa **phần khai báo lớp**, không phải phương thức đang gọi Reflection.

Do đó, hai dòng có nhãn `method local` và `method anonymous` đều có:

- `enclosing=EnclosingExecutableDemo`: lớp bao quanh.
- `method` khác `null`: phương thức chứa khai báo lớp cục bộ hoặc vô danh.
- `constructor=null`: khai báo không nằm trực tiếp trong hàm khởi tạo.
- `declaring=null`: lớp đang xét không phải lớp thành viên.

### 3.2. Lớp trong hàm khởi tạo

Hai lớp được khai báo trong `Box(int id)` trả về `Box(int)` qua `getEnclosingConstructor()`. Đó là hàm khởi tạo của **Box**, không phải hàm khởi tạo của chính lớp cục bộ hoặc lớp vô danh.

Trong chương trình, tham số `id` không được sử dụng để giữ ví dụ tập trung vào vị trí khai báo. Số `7` truyền khi tạo `Box` không xuất hiện trong kết quả Reflection: metadata mô tả kiểu tham số `int`, không lưu giá trị của lần gọi hàm khởi tạo đó.

`getEnclosingMethod()` trả về `null` trong hai trường hợp này. Dù hàm khởi tạo có mã thực thi và tham số, Java Reflection biểu diễn nó bằng `Constructor<?>`, không phải `Method`.

### 3.3. Khối khởi tạo và biểu thức khởi tạo trường

Bốn trường hợp cuối cùng đều có lớp bao quanh nhưng không có phương thức hoặc hàm khởi tạo bao quanh:

- `static block`: lớp cục bộ trong `static { ... }`.
- `instance block`: lớp cục bộ trong khối `{ ... }` dùng khi khởi tạo đối tượng.
- `instance field`: lớp vô danh trong biểu thức khởi tạo trường `fieldTask`.
- `static field`: lớp vô danh trong biểu thức khởi tạo trường `STATIC_FIELD_TASK`.

Đặc biệt, mã khởi tạo trường đối tượng và khối khởi tạo đối tượng có thể được trình biên dịch đưa vào mã khởi tạo của lớp. Điều đó **không làm thay đổi vị trí khai báo trong mã nguồn thành một thân constructor**. Vì thế không được suy luận rằng `getEnclosingConstructor()` phải trả về constructor đã thực hiện việc khởi tạo.

Đối với khối tĩnh, `getEnclosingMethod()` cũng không trả về một `Method` tên `<clinit>`. Hai API này không dùng để liệt kê mọi đơn vị thực thi ở cấp bytecode.

## 4. So sánh với getEnclosingClass và getDeclaringClass

Bốn truy vấn thường xuất hiện cạnh nhau, nhưng trả lời các câu hỏi khác nhau:

| Truy vấn trên `Class<?> type` | Câu hỏi |
| --- | --- |
| `getEnclosingClass()` | Lớp nào trực tiếp bao quanh lớp đang xét? |
| `getDeclaringClass()` | Lớp nào khai báo lớp đang xét như một thành viên? |
| `getEnclosingMethod()` | Phương thức nào trực tiếp bao quanh khai báo lớp cục bộ hoặc vô danh? |
| `getEnclosingConstructor()` | Hàm khởi tạo nào trực tiếp bao quanh khai báo lớp cục bộ hoặc vô danh? |

Trong ví dụ:

- `Member.class` và `InnerMember.class` đều có `getDeclaringClass()` là `EnclosingExecutableDemo.class`, vì đều được khai báo ở cấp thành viên.
- Lớp cục bộ trong `localInMethod()` có `getEnclosingClass()` là `EnclosingExecutableDemo.class`, nhưng `getDeclaringClass()` là `null`.
- Lớp cục bộ trong constructor của `Box` có `getEnclosingClass()` là `Box.class`, không phải lớp ngoài cùng `EnclosingExecutableDemo.class`.

Không nên đánh đồng `getEnclosingClass()` với lớp gốc của một nhóm nest. Quan hệ nest phục vụ một mục đích riêng liên quan đến truy cập thành viên; nếu cần khảo sát nó thì dùng các API nest tương ứng.

Cũng cần nhìn đúng **đối tượng nhận lời gọi**. Hai biểu thức sau không hỏi cùng một điều:

~~~java
// Đoạn minh họa, dùng các lớp/phương thức trong chương trình ở trên.
Class<?> localType = EnclosingExecutableDemo.localInMethod();
Method enclosing = localType.getEnclosingMethod();

System.out.println(localType.getDeclaringClass()); // null
System.out.println(enclosing.getDeclaringClass()
        == EnclosingExecutableDemo.class);        // true
~~~

`localType.getDeclaringClass()` hỏi lớp đang xét có phải một lớp thành viên hay không. `enclosing.getDeclaringClass()` hỏi lớp nào khai báo **phương thức** đã tìm được. Trùng tên API không có nghĩa trùng vai trò.

## 5. “Bao quanh” là quan hệ khai báo, không phải lịch sử chạy

Một lớp có thể được tạo đối tượng ở nhiều nơi, nhưng nơi tạo đối tượng không sửa lại quan hệ khai báo của lớp.

Trong ví dụ, `main()` gọi `localInMethod()`. Sau khi phương thức này đã trả về, `inspect()` mới đọc metadata. `getEnclosingMethod()` vẫn chỉ đến `localInMethod()`, không chỉ đến `main()` hoặc `inspect()`.

Từ kết quả này không thể biết:

- Lần gọi nào đã tạo ra một đối tượng cụ thể.
- Luồng nào đã thực hiện lần gọi đó.
- Giá trị biến cục bộ hoặc tham số khi đối tượng được tạo.
- Phương thức nào vừa gọi một phương thức khác trong ngăn xếp hiện tại.
- Một đối tượng đã được truyền qua những phương thức nào sau khi tạo.

Nếu cần ngăn xếp lời gọi hiện tại, đó là bài toán khác, chẳng hạn khảo sát bằng `StackWalker`. Nếu cần dữ liệu của một lần thực thi, hãy truyền và lưu dữ liệu đó bằng thiết kế chương trình thích hợp.

Từ “trực tiếp” cũng có ý nghĩa. Giả sử một phương thức `factory()` khai báo lớp cục bộ `Container`; trong phương thức `build()` của `Container` lại khai báo lớp cục bộ `Part`. Với `Part.class`, phương thức trực tiếp bao quanh là `Container.build()`. Không bỏ qua cấp này để kết luận `factory()` là kết quả.

## 6. Metadata đến từ đâu?

Class file của lớp cục bộ hoặc lớp vô danh có thuộc tính `EnclosingMethod`. Tên thuộc tính này có thể gây hiểu nhầm: nó biểu diễn cả trường hợp phương thức, hàm khởi tạo và trường hợp không có đơn vị nào thuộc hai loại đó trực tiếp bao quanh.

Có thể hình dung thông tin chính như sau:

~~~text
Lớp bao quanh
    +
Tên và descriptor của phương thức/constructor bao quanh
    hoặc
Không có phương thức/constructor bao quanh
~~~

Theo [JVMS §4.7.7](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.7.7), thuộc tính có `class_index` và `method_index`. `method_index = 0` khi lớp không được bao quanh trực tiếp bởi phương thức hoặc hàm khởi tạo; các khối khởi tạo và biểu thức khởi tạo trường là những trường hợp quan trọng.

Điều này giải thích ba điểm thực tế:

1. Reflection không cần mở lại tệp `.java` để tìm phương thức bao quanh.
2. Thông tin này không phải tên biến cục bộ trong bảng debug. Không cần thêm `-parameters` để hai API hoạt động; tùy chọn đó liên quan đến tên tham số.
3. Tên và descriptor giúp xác định đúng phương thức hoặc constructor khi có nạp chồng. Chỉ lấy `getName()` rồi bỏ kiểu tham số sẽ làm mất một phần thông tin cần phân biệt.

Ví dụ trong chương trình, `parameters(Executable)` đọc các kiểu tham số theo thứ tự và tạo chuỗi `(int)` cho constructor của `Box`. Không suy đoán constructor từ tên lớp và không tìm “constructor đầu tiên” trong một mảng kết quả.

Với bytecode được sinh hoặc biến đổi bởi công cụ khác, cần xem metadata của chính class file. Đừng dùng dấu `$` hoặc mẫu tên như `Outer$1` làm bằng chứng thay cho API. Cũng không nên phụ thuộc vào số đánh thứ tự mà một lần biên dịch gán cho lớp vô danh.

## 7. Cách dùng trong mã khảo sát hoặc công cụ

Một công cụ có thể muốn thống nhất phương thức và constructor bao quanh dưới kiểu chung `Executable`. Lớp này là lớp cha của `Method` và `Constructor`.

Đoạn sau là phương thức hỗ trợ; thêm các import `java.lang.reflect.Executable`, `java.lang.reflect.Method` và `java.util.Objects` vào lớp chứa nó:

~~~java
static Executable findEnclosingExecutable(Class<?> type) {
    Objects.requireNonNull(type, "type");

    Method method = type.getEnclosingMethod();
    if (method != null) {
        return method;
    }
    return type.getEnclosingConstructor();
}
~~~

Cách dùng minh họa khi đặt phương thức hỗ trợ trong cùng chương trình:

~~~java
Executable enclosing =
        findEnclosingExecutable(EnclosingExecutableDemo.localInMethod());

if (enclosing == null) {
    System.out.println("Khong co method/constructor bao quanh");
} else {
    System.out.println(enclosing.getDeclaringClass().getSimpleName());
    System.out.println(enclosing.getParameterCount());
}
~~~

Với lớp do `localInMethod()` trả về, hai dòng được in là:

~~~text
EnclosingExecutableDemo
0
~~~

Ở đây `null` là kết quả hợp lệ khi không có đơn vị khai báo tương ứng. Không biến nó thành lỗi “không tìm thấy lớp”, và không gọi tiếp `getName()` trên kết quả chưa kiểm tra.

Một vài nguyên tắc sử dụng:

- **Lấy đúng lớp cần khảo sát.** Nếu đang có đối tượng, `object.getClass()` lấy lớp thực tế của đối tượng; `Runnable.class` chỉ là token của interface và không đại diện cho lớp vô danh triển khai nó.
- **Giữ chữ ký khi cần nhận diện chính xác.** Ngoài tên phương thức, xem lớp khai báo và kiểu tham số. Với constructor, `getDeclaringClass()` thể hiện lớp được khởi tạo.
- **Tách truy vấn metadata khỏi thực thi.** Nhận một `Method` hoặc `Constructor` không cấp quyền gọi nó và không tự bỏ qua kiểm soát truy cập. Không thêm `setAccessible(true)` chỉ để đọc tên hoặc kiểu tham số.
- **Không yêu cầu đối tượng của lớp bao quanh để truy vấn.** Các API được gọi trên `Class<?>`. Việc một lớp bên trong cần đối tượng bao quanh để tạo instance là vấn đề khác.
- **Không dùng như bộ máy tìm tất cả lớp cục bộ.** Hai API nhận một lớp đã biết và đi từ lớp đó về nơi khai báo. Chúng không trả về danh sách mọi lớp được khai báo trong một phương thức.
- **Không suy diễn từ lambda.** Lambda không đồng nghĩa với khai báo lớp vô danh. Không thiết kế chức năng tìm phương thức nguồn của lambda dựa trên giả định lớp thực thi lambda có cùng metadata bao quanh như `new Runnable() { ... }`.

Khi chạy trên môi trường Java cũ có cấu hình Security Manager, các API có thể chịu kiểm tra bảo mật và ném `SecurityException` theo tài liệu phiên bản tương ứng. Ví dụ học tập ở trên không cần thay đổi chính sách bảo mật hoặc mở truy cập sâu vào JDK.

## 8. Lỗi thường gặp

### Gọi getEnclosingConstructor để tìm constructor của chính lớp

Đó là nhầm hướng truy vấn. Muốn xem constructor thuộc lớp đang xét, dùng `getDeclaredConstructors()` hoặc tìm chữ ký thích hợp bằng `getDeclaredConstructor(...)`.

`getEnclosingConstructor()` đi ra ngoài lớp đang xét, đến constructor chứa **khai báo** lớp cục bộ hoặc vô danh đó.

### Cho rằng mọi lớp không có static đều có enclosing constructor

`InnerMember` trong chương trình là lớp thành viên không có `static`, nhưng hai API đều trả về `null`. Quan hệ với một đối tượng bao quanh không tự tạo ra một constructor khai báo bao quanh.

### Thấy hai kết quả null rồi kết luận không có lớp bao quanh

Trường hợp lớp trong khối khởi tạo có thể có `getEnclosingClass()` khác `null` dù cả hai kết quả đang học đều bằng `null`. Muốn trả lời câu hỏi về lớp bao quanh thì phải hỏi `getEnclosingClass()`.

### Nghĩ lớp vô danh không có tên nên không có metadata khai báo

Lớp vô danh không có tên đơn giản trong mã nguồn, nhưng vẫn có một `Class<?>` và có thể được truy vấn. Hãy lấy token từ đối tượng, không tự dựng tên nhị phân.

### Mong getEnclosingMethod trả về run()

Với lớp vô danh triển khai `Runnable`, `run()` là phương thức **của lớp đó**. Nếu khai báo lớp vô danh nằm trong `anonymousInMethod()`, phương thức bao quanh là `anonymousInMethod()`. Muốn tìm `run()` trên lớp đang xét, đó là truy vấn thành viên như `getDeclaredMethod("run")`, không phải truy vấn enclosing.

## 9. Tự kiểm tra

1. Một lớp cục bộ được khai báo trong phương thức `create()`. Khi chương trình giữ `Class<?>` của nó sau khi `create()` kết thúc, `getEnclosingMethod()` còn trả về `create()` không?
2. Một lớp vô danh được khai báo trong constructor `Store(String name)`. Hai API trả về gì?
3. Một lớp vô danh nằm trong biểu thức khởi tạo trường đối tượng. Vì sao không thể mặc định constructor của đối tượng là kết quả của `getEnclosingConstructor()`?
4. Một lớp thành viên không có `static` được tạo đối tượng ngay trong `main()`. `getEnclosingMethod()` có trả về `main()` không?
5. Với lớp cục bộ trong phương thức, `Class.getDeclaringClass()` và `Method.getDeclaringClass()` trên phương thức bao quanh có nhất thiết giống nhau không?
6. Từ kết quả `getEnclosingConstructor()`, có đọc được giá trị tham số của lần tạo một đối tượng cụ thể không?

Đáp án:

1. Có. Đây là metadata về khai báo, không phụ thuộc phương thức còn trên ngăn xếp hay không.
2. `getEnclosingMethod()` trả về `null`; `getEnclosingConstructor()` mô tả `Store(String)`.
3. Vị trí khai báo thuộc biểu thức khởi tạo trường, không thuộc thân constructor trong mã nguồn; cả hai API trả về `null`.
4. Không. Lớp thành viên vẫn là lớp thành viên; nơi tạo instance không đổi vị trí khai báo của nó.
5. Không. Truy vấn trên lớp cục bộ trả về `null`; truy vấn trên phương thức cho lớp khai báo phương thức.
6. Không. Kết quả mô tả chữ ký và metadata của constructor, không lưu dữ liệu từng lần thực thi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nested-types-nestmates-004">isMemberClass, isLocalClass và isAnonymousClass</a>

<details>
<summary>Xem chi tiết</summary>

# isMemberClass(), isLocalClass() và isAnonymousClass() trong Java Reflection

Trong Java, không phải mọi lớp đều được khai báo trực tiếp ở cấp tệp. Một lớp có thể là thành viên của một lớp khác, được khai báo bên trong một khối lệnh, hoặc được tạo bằng biểu thức lớp vô danh. Bộ ba phương thức `Class.isMemberClass()`, `Class.isLocalClass()` và `Class.isAnonymousClass()` giúp nhận diện **kiểu khai báo** của một `Class<?>` lúc chạy.

Mục tiêu của bài học: phân biệt chính xác ba loại lớp, dùng các phương thức Reflection để truy tìm ngữ cảnh bao quanh, và tránh suy luận sai từ tên lớp hoặc quan hệ nestmate.

## 1. Ba phương thức trả lời ba câu hỏi khác nhau

Cả ba đều là phương thức instance của `java.lang.Class`, không nhận đối số và trả về `boolean`:

```java
boolean member = type.isMemberClass();
boolean local = type.isLocalClass();
boolean anonymous = type.isAnonymousClass();
```

| Phương thức | Trả về `true` khi | Ví dụ điển hình |
| --- | --- | --- |
| `isMemberClass()` | Lớp được khai báo là **thành viên** trong phần thân một lớp hoặc kiểu bao ngoài | `Outer.StaticNested`, `Outer.Inner` |
| `isLocalClass()` | Lớp có tên được khai báo **cục bộ trong một khối**, chẳng hạn trong phương thức hoặc constructor | `class Local {}` bên trong `main` |
| `isAnonymousClass()` | Lớp được khai báo qua **biểu thức tạo lớp vô danh**, không có tên khai báo riêng | `new Runnable() { ... }` |

Đây là ba cách phân loại khác nhau và không chồng lên nhau đối với một lớp cụ thể. Nếu cả ba trả về `false`, **không đủ kết luận đó là lớp top-level**: đối tượng `Class` còn có thể đại diện cho primitive, mảng và nhiều kiểu khác.

### 1.1. Member class: lớp thành viên

```java
class Outer {
    static class StaticNested {}  // Member class, đồng thời là static nested class
    class Inner {}                // Member class, đồng thời là non-static inner class
}
```

Cả `Outer.StaticNested.class.isMemberClass()` lẫn `Outer.Inner.class.isMemberClass()` đều là `true`. Từ khóa `static` quyết định lớp lồng có cần tham chiếu đến thể hiện của `Outer` hay không; nó **không** quyết định giá trị của `isMemberClass()`.

Lưu ý thuật ngữ: mọi inner class là một nested class, nhưng static nested class không phải inner class theo nghĩa của Java Language Specification. Trong ví dụ trên, cả hai vẫn là member classes.

### 1.2. Local class: lớp cục bộ có tên

```java
void process() {
    class Result {
        String value() { return "OK"; }
    }
    Class<?> type = Result.class;
    System.out.println(type.isLocalClass()); // true
}
```

`Result` có tên trong mã nguồn và phạm vi nhìn thấy cục bộ trong khối chứa khai báo. Local class có thể xuất hiện trong phương thức, constructor hoặc khối khởi tạo; không nhất thiết phải được định nghĩa trong một phương thức instance. Phạm vi tên và khả năng bắt biến cục bộ của lớp này tuân theo các quy tắc của ngôn ngữ Java. Khi dùng biến cục bộ từ khối bao ngoài, biến đó phải là `final` hoặc *effectively final*.

Một local class **không trở thành member class** chỉ vì bytecode có tên chứa dấu `$` hoặc vì `getEnclosingClass()` trả về một lớp bao ngoài.

### 1.3. Anonymous class: lớp vô danh

```java
Runnable job = new Runnable() {
    @Override
    public void run() {
        System.out.println("Running");
    }
};
System.out.println(job.getClass().isAnonymousClass()); // true
```

Biểu thức `new Runnable() { ... }` khai báo và khởi tạo một lớp không có tên khai báo riêng. `job.getClass()` trỏ đến lớp vô danh do biểu thức đó tạo ra; nó không phải `Runnable.class`.

Đừng nhầm anonymous class với biểu thức lambda:

```java
Runnable lambda = () -> System.out.println("Running");
```

Lambda được triển khai theo cơ chế riêng của JVM; không dùng `isAnonymousClass()` để xác định một đối tượng có được tạo từ lambda hay không. Hình dạng lớp runtime của lambda là chi tiết triển khai, không phải hợp đồng nhận diện bằng ba phương thức này.

## 2. Ví dụ đầy đủ, chạy được trên Java 17+

Tạo tệp `ClassKindsDemo.java`:

```java
public class ClassKindsDemo {
    static class StaticMember {}
    class InnerMember {}

    public static void main(String[] args) {
        class Local {}

        Runnable anonymous = new Runnable() {
            @Override
            public void run() {}
        };

        show("TopLevel", ClassKindsDemo.class);
        show("StaticMember", StaticMember.class);
        show("InnerMember", InnerMember.class);
        show("Local", Local.class);
        show("Anonymous", anonymous.getClass());

        System.out.println("Local declaring: " + Local.class.getDeclaringClass());
        System.out.println("Local enclosing class: " + Local.class.getEnclosingClass().getSimpleName());
        System.out.println("Local enclosing method: " + Local.class.getEnclosingMethod().getName());
        System.out.println("Anonymous simple name empty: " + anonymous.getClass().getSimpleName().isEmpty());
    }

    static void show(String label, Class<?> type) {
        System.out.println(label + " -> member=" + type.isMemberClass()
                + ", local=" + type.isLocalClass()
                + ", anonymous=" + type.isAnonymousClass());
    }
}
```

Biên dịch và chạy:

```bash
javac ClassKindsDemo.java
java ClassKindsDemo
```

Kết quả:

```text
TopLevel -> member=false, local=false, anonymous=false
StaticMember -> member=true, local=false, anonymous=false
InnerMember -> member=true, local=false, anonymous=false
Local -> member=false, local=true, anonymous=false
Anonymous -> member=false, local=false, anonymous=true
Local declaring: null
Local enclosing class: ClassKindsDemo
Local enclosing method: main
Anonymous simple name empty: true
```

Hai lớp `StaticMember` và `InnerMember` khác nhau ở ngữ nghĩa `static`/instance nhưng giống nhau về phân loại `member`. `Local` có tên riêng và được khai báo trong `main` nên `local=true`. Đối tượng `anonymous` có một lớp thực thi `Runnable` không được đặt tên trong mã nguồn nên `anonymous=true`.

Lưu ý rằng `ClassKindsDemo.class` đại diện cho lớp top-level của ví dụ. Ba kết quả `false` chỉ có ý nghĩa top-level trong bối cảnh ta **đã biết** đối tượng đó là lớp thường được khai báo ở cấp tệp.

## 3. Phân biệt declaring class và enclosing context

Để tìm hiểu một lớp nằm ở đâu trong mã nguồn, kết hợp bộ ba phân loại với các API khác của `Class<?>`:

| API | Ý nghĩa chính | Member class | Local class trong `main` | Anonymous class trong `main` |
| --- | --- | --- | --- | --- |
| `getDeclaringClass()` | Lớp trực tiếp khai báo kiểu **thành viên** | `Outer.class` | `null` | `null` |
| `getEnclosingClass()` | Lớp bao quanh khai báo | `Outer.class` | Lớp chứa `main` | Lớp chứa `main` |
| `getEnclosingMethod()` | Phương thức trực tiếp chứa khai báo, nếu có | Thường `null` | `main` | `main` |
| `getEnclosingConstructor()` | Constructor trực tiếp chứa khai báo, nếu có | Thường `null` | `null` trong ví dụ | `null` trong ví dụ |
| `getSimpleName()` | Tên đơn giản | Tên lớp thành viên | Tên lớp cục bộ | Chuỗi rỗng |
| `getCanonicalName()` | Tên chuẩn nếu có | Thường khác `null` | `null` | `null` |

Các cột cuối giả định chính ngữ cảnh khai báo của ví dụ ở mục 2. Với lớp cục bộ hay vô danh được khai báo trong constructor, `getEnclosingConstructor()` có thể trả về constructor đó. Nếu chúng được khai báo trong khối khởi tạo hoặc khởi tạo trường, `getEnclosingMethod()` và `getEnclosingConstructor()` có thể đều trả về `null` dù `getEnclosingClass()` không phải `null`.

Ví dụ đối chiếu:

```java
Class<?> member = ClassKindsDemo.StaticMember.class;
System.out.println(member.getDeclaringClass() == ClassKindsDemo.class); // true
System.out.println(member.getEnclosingMethod() == null);                 // true
```

Đây là đoạn minh họa bổ sung, có thể đặt trong `main` của ví dụ trước. Trong đó `getDeclaringClass()` trả về đối tượng `Class` đại diện cho lớp khai báo thành viên, còn `getEnclosingMethod()` trả về `null` bởi `StaticMember` không được khai báo bên trong một phương thức.

**Quy tắc sử dụng:** `getDeclaringClass()` hữu ích để tìm chủ thể khai báo một member class; `getEnclosingClass()` thích hợp hơn khi quan tâm đến lớp bao quanh của cả member, local và anonymous class.

## 4. Liên hệ với kiểu lồng và nestmate

Từ Java 11, khái niệm **nest** hỗ trợ các lớp thuộc cùng một nhóm truy cập thành viên private ở mức JVM. `Class` có các API như `getNestHost()`, `getNestMembers()` và `isNestmateOf(...)`.

Điểm cần phân biệt:

- `isMemberClass()`, `isLocalClass()`, `isAnonymousClass()` mô tả **hình thức khai báo** của chính lớp được phản chiếu.
- `getNestHost()` và `isNestmateOf(...)` mô tả **quan hệ nest** phục vụ quy tắc truy cập ở mức JVM.
- Một lớp local hoặc anonymous có thể thuộc cùng nest với lớp bao quanh mà vẫn có `isMemberClass() == false`. Vì thế không dùng quan hệ nestmate để suy ra member class.

Tương tự, tên nhị phân thường có dấu `$` cho nhiều loại lớp lồng, nhưng đây **không phải tiêu chí Reflection đáng tin** để phân biệt member, local và anonymous. Những thành phần số trong tên lớp được compiler sinh ra là chi tiết triển khai, không nên hard-code hoặc dùng làm hợp đồng API.

## 5. Áp dụng khi kiểm tra Class<?> động

Khi một framework hoặc công cụ nhận `Class<?>` từ bên ngoài, có thể phân nhánh theo loại khai báo:

```java
static String declaredKind(Class<?> type) {
    if (type.isAnonymousClass()) {
        return "anonymous class";
    }
    if (type.isLocalClass()) {
        return "local class";
    }
    if (type.isMemberClass()) {
        return "member class";
    }
    return "other";
}
```

Đây là một **phương thức trợ giúp**, không phải chương trình độc lập. Có thể đặt nó trong bất kỳ lớp Java thích hợp nào. Nhánh `other` được đặt cố ý để tránh mặc định mọi kiểu còn lại đều là top-level.

Các tình huống thực tế:

1. **Logging và diagnostics:** ghi loại khai báo bằng các cờ Reflection, thay vì trông chờ `getSimpleName()` luôn có giá trị (lớp vô danh có tên đơn giản rỗng).
2. **Công cụ quét metadata:** xử lý member classes theo quan hệ `getDeclaringClass()`; local/anonymous classes cần kiểm tra `getEnclosingClass()` hoặc ngữ cảnh phương thức/constructor.
3. **Sinh tài liệu hay tên hiển thị:** ưu tiên một nhãn có ý nghĩa do ứng dụng đặt nếu gặp anonymous class; tránh hiển thị trực tiếp tên nhị phân có số compiler sinh.

Nếu thực sự muốn kiểm tra một lớp có phải kiểu lồng hay không, hãy xác định rõ khái niệm cần dùng: **member**, **enclosed**, hay **nestmate**. Mỗi khái niệm cần API và điều kiện riêng.

## 6. Lỗi thường gặp và cách tự kiểm tra

- **Cho rằng static nested class không phải member class.** Sai: `static` không làm `isMemberClass()` thành `false`.
- **Đồng nhất local class và anonymous class.** Local class có tên khai báo (`class Local`); anonymous class dùng biểu thức tạo lớp không đặt tên (`new Interface() { ... }`).
- **Dùng `getDeclaringClass() == null` để khẳng định top-level.** Sai: local và anonymous class cũng thường trả về `null`.
- **Dựa vào `getName()` hoặc dấu `$` để phân loại.** Không cần thiết và dễ phụ thuộc compiler; hãy dùng trực tiếp ba phương thức `is...Class()`.
- **Xem lambda là anonymous class.** Không có bảo đảm đó; lambda và anonymous class khác nhau về ngữ nghĩa.
- **Coi ba cờ đều `false` là đủ để kết luận top-level.** Mảng và primitive cũng nằm ngoài ba loại; các `Class<?>` khác cũng có thể như vậy.

Tự kiểm tra: Với `new Thread() { }`, `isAnonymousClass()` của **đối tượng mới tạo** là gì? **`true`**. Với một lớp `static class Helper` nằm trong thân lớp khác, `isMemberClass()` là gì? **`true`**. Với `class Temp {}` được khai báo trong `main`, `getDeclaringClass()` thường là gì? **`null`**, còn `getEnclosingMethod()` là `main`.

Tài liệu tham khảo: [Javadoc `java.lang.Class` (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nested-types-nestmates-005">Static nested class khác non-static inner class</a>

<details>
<summary>Xem chi tiết</summary>

# Static nested class khác non-static inner class trong Java

## 1. Bản chất của hai loại member class

Trong Java, một lớp có thể được khai báo trực tiếp bên trong thân lớp khác. Hai hình thức **member class** thường gặp là:

- **Static nested class**: member class có từ khóa `static`. Mỗi đối tượng của nó **không cần gắn với một đối tượng** của lớp bao ngoài (`enclosing instance`).
- **Non-static inner class** (thường gọi ngắn là **inner class**): member class không có `static`. Khi được khởi tạo theo cách thông thường, mỗi đối tượng của nó gắn với **một đối tượng cụ thể** của lớp bao ngoài.

Ví dụ cú pháp:

```java
class Outer {
    static class Nested { }
    class Inner { }
}
```

Cả hai đều là kiểu thành viên của `Outer` và có tên nguồn `Outer.Nested`, `Outer.Inner`. Từ khóa `static` ở đây nói về **mối quan hệ với đối tượng bao ngoài**, không biến mọi thành viên hay mọi phương thức bên trong `Nested` thành `static`.

> Điểm cốt lõi: `Outer.Nested` được tạo độc lập với instance `Outer`; còn một instance `Outer.Inner` cần một enclosing instance `Outer`.

## 2. So sánh nhanh

| Tiêu chí | `static class Nested` | `class Inner` |
| --- | --- | --- |
| Có enclosing instance `Outer` ngầm định? | Không | Có, khi khởi tạo như member inner class |
| Khởi tạo ngoài `Outer` | `new Outer.Nested()` | `outer.new Inner()` |
| Đọc trực tiếp trường instance của `Outer` | Không; cần tham chiếu `Outer` tường minh | Có; qua enclosing instance |
| Đọc trực tiếp thành viên `static` của `Outer` | Có (theo phạm vi truy cập) | Có (theo phạm vi truy cập) |
| Truy cập thành viên `private` của `Outer` | Có thể, nếu có tham chiếu đối tượng khi cần | Có thể, gồm trường instance của enclosing instance |
| Tham chiếu `Outer.this` | Không hợp lệ | Hợp lệ trong ngữ cảnh có enclosing instance |
| Kiểu dùng trong Reflection | `isMemberClass()==true`, `Modifier.isStatic(... )==true` | `isMemberClass()==true`, `Modifier.isStatic(... )==false` |
| Constructor thể hiện qua Reflection | Không có tham số enclosing instance do tư cách static nested | Với member inner class thông thường, có thêm tham số `Outer` đứng đầu |

**Lưu ý về `private`:** Hai lớp trong cùng nest của Java có thể truy cập các thành viên private của nhau theo quy tắc ngôn ngữ/JVM. Vì thế, **khả năng truy cập private không phải điểm phân biệt** giữa static nested và inner; điểm phân biệt là có hay không có đối tượng bao ngoài ngầm định.

## 3. Khởi tạo và truy cập trạng thái — ví dụ chạy được

Lưu toàn bộ chương trình dưới tên `NestedVsInnerDemo.java`:

```java
import java.lang.reflect.Modifier;

public class NestedVsInnerDemo {
    private final String label;
    private static int created;

    public NestedVsInnerDemo(String label) {
        this.label = label;
        created++;
    }

    public static class StaticNested {
        public String count() {
            return "Đã tạo outer: " + created;
        }

        public String labelOf(NestedVsInnerDemo outer) {
            return outer.label; // private hợp lệ; outer truyền tường minh
        }
    }

    public class Inner {
        private final int id;

        public Inner(int id) {
            this.id = id;
        }

        public String describe() {
            return label + " #" + id; // label thuộc enclosing instance
        }
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        NestedVsInnerDemo a = new NestedVsInnerDemo("A");
        NestedVsInnerDemo b = new NestedVsInnerDemo("B");

        StaticNested nested = new StaticNested();
        Inner first = a.new Inner(1);
        Inner second = b.new Inner(2);

        System.out.println(nested.count());
        System.out.println(nested.labelOf(a));
        System.out.println(first.describe());
        System.out.println(second.describe());

        Class<?> staticType = StaticNested.class;
        Class<?> innerType = Inner.class;
        System.out.println(Modifier.isStatic(staticType.getModifiers()));
        System.out.println(Modifier.isStatic(innerType.getModifiers()));
        System.out.println(
                staticType.getDeclaredConstructor().getParameterCount());
        System.out.println(
                innerType.getDeclaredConstructor(
                        NestedVsInnerDemo.class, int.class)
                        .getParameterCount());
        System.out.println(
                innerType.getNestHost() == NestedVsInnerDemo.class);
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac -encoding UTF-8 NestedVsInnerDemo.java
java NestedVsInnerDemo
```

Kết quả chuẩn:

```text
Đã tạo outer: 2
A
A #1
B #2
true
false
0
2
true
```

Giải thích từng quan sát:

1. `new StaticNested()` không cần instance `NestedVsInnerDemo`. Phương thức `count()` đọc trường `created` mang tính lớp; `labelOf(a)` muốn đọc `label` phải nhận `a` một cách tường minh.
2. `a.new Inner(1)` và `b.new Inner(2)` tạo hai inner object gắn với hai outer object khác nhau. Cùng phương thức `describe()` nhưng kết quả mang nhãn `A` và `B`.
3. `Modifier.isStatic(Class#getModifiers())` cho kết quả `true` với static nested class, `false` với non-static inner class.
4. Trong Reflection, constructor của `StaticNested` không tham số; constructor `Inner(int)` xuất hiện với **hai** tham số: enclosing `NestedVsInnerDemo` đứng trước `int`. Đây là một chi tiết quan trọng khi gọi `getDeclaredConstructor(...)`.
5. `Inner.class.getNestHost()` trỏ đến lớp bao ngoài; điều này phản ánh **nest membership** của JVM và không có nghĩa mọi nested class đều giữ tham chiếu đến một outer instance.

## 4. Tại sao cách khởi tạo khác nhau?

Với static nested class:

```java
Outer.Nested n = new Outer.Nested();
```

Với non-static member inner class:

```java
Outer outer = new Outer();
Outer.Inner i = outer.new Inner();
```

Ở câu lệnh thứ hai, biểu thức `outer` chỉ rõ đối tượng bao ngoài nào được gắn với `i`. Trong một phương thức instance của `Outer`, ta cũng có thể viết `new Inner()`, vì `this` của `Outer` đang hiện diện. Trong `static` method của `Outer`, không có `Outer.this` nên không thể gọi `new Inner()` nếu chưa tạo/chỉ định outer instance.

Hai cách viết dễ nhầm:

```java
class Outer {
    static class Nested { }
    class Inner { }
}

// Hợp lệ:
Outer.Nested a = new Outer.Nested();
Outer host = new Outer();
Outer.Inner b = host.new Inner();

// Không hợp lệ (chỉ minh họa, KHÔNG đặt vào file chạy):
// Outer.Inner wrong1 = new Outer.Inner();
// Outer.Nested wrong2 = host.new Nested();
```

Một static nested class cũng có thể có **các instance field, instance method và constructor** của chính nó. `static` ở khai báo lớp lồng chỉ bỏ yêu cầu liên kết đến đối tượng `Outer`.

## 5. `this`, `Outer.this` và quyền truy cập

Bên trong `Inner`, `this` là đối tượng inner hiện tại; `Outer.this` là enclosing object. Cách truy cập được định vị theo phạm vi tên, nên có thể dùng qualified `this` để xử lý tên bị che khuất:

```java
class House {
    private String name = "nhà";

    class Room {
        private String name = "phòng";

        String describe() {
            return this.name + " trong " + House.this.name;
        }
    }
}
```

Trong `Room`, `House.this.name` đọc trực tiếp trạng thái outer object mà không cần truyền `House` vào constructor do người viết khai báo. Static nested class **không có** `House.this`; nếu cần `name` của một `House`, phải nhận `House` qua tham số, trường instance hoặc nguồn tham chiếu hợp lệ khác.

Cả hai loại member class vẫn chịu luật `public`, `protected`, package-private và `private` khi được dùng từ các vị trí ngoài enclosing class. Một static nested class có thể đọc `private static` member của `Outer`, và có thể đọc `private` instance member thông qua một `Outer` được truyền vào. Do đó không nên diễn đạt sai rằng static nested class “không truy cập được private của Outer”.

## 6. Góc nhìn bytecode và Reflection

Java source dùng `Outer.Inner`, nhưng tên nhị phân thông thường của member class là `Outer$Inner` (`Class#getName()` trả tên nhị phân). Hai file class sẽ thường là `Outer$Nested.class` và `Outer$Inner.class`. Ký tự `$` trong tên file **không đủ** để kết luận kiểu đó là static hay inner; hãy dùng metadata từ `Class`.

Có thể kiểm tra:

```java
Class<?> type = Outer.Inner.class;
boolean member = type.isMemberClass();
Class<?> owner = type.getDeclaringClass();
boolean staticMember =
        java.lang.reflect.Modifier.isStatic(type.getModifiers());
```

- `isMemberClass()` nhận diện **member class**, giúp phân biệt với local/anonymous class; cả static nested và member inner đều cho `true`.
- `getDeclaringClass()` trả `Outer.class` cho cả hai member class. `getEnclosingClass()` cũng cho biết lớp bao quanh trong trường hợp member class nhưng có ý nghĩa rộng hơn với local/anonymous class.
- `Modifier.isStatic(type.getModifiers())` giúp phân loại hai dạng member class đã nêu.
- `getDeclaredConstructors()` hoặc `getDeclaredConstructor(...)` cho thấy chữ ký constructor ở mức JVM. Constructor của member inner class thường có tham số outer đầu tiên; khi dùng Reflection để tạo nó phải truyền enclosing instance thích hợp.

Ví dụ cách tạo inner qua Reflection (đặt trong ngữ cảnh có `Outer` và `Outer.Inner` như trên):

```java
Outer outer = new Outer();
var ctor = Outer.Inner.class.getDeclaredConstructor(Outer.class);
Outer.Inner inner = ctor.newInstance(outer);
```

Ví dụ này áp dụng khi `Inner` được khai báo **không có tham số constructor tường minh** và constructor phản chiếu có thể truy cập từ nơi gọi. Nếu có constructor `Inner(int id)` như chương trình chính, cần tra `getDeclaredConstructor(Outer.class, int.class)` rồi gọi `newInstance(outer, id)`. Có thể cần `setAccessible(true)` khi thành viên không accessible, nhưng việc mở truy cập còn phụ thuộc cơ chế đóng gói của Java module system.

Đừng dựa vào tên trường do compiler tạo ra (ví dụ `this$0`) để tìm enclosing instance: tên, tối ưu hóa lưu trữ tham chiếu và cờ synthetic là chi tiết biên dịch có thể khác giữa compiler/JDK. Hãy dựa vào quan hệ ngữ nghĩa của Java, `getDeclaringClass()`, modifier và chữ ký constructor thực tế.

### “Nested” khác “nestmate” của JVM

Từ Java 11, JVM có khái niệm **nest host/nest members** phục vụ quyền truy cập private giữa những class cùng nest. `Class#getNestHost()` và `Class#getNestMembers()` phản ánh quan hệ này. **Static nested class cũng có thể là nestmate của Outer**; quan hệ nestmate không bắt buộc có enclosing instance. Ngược lại, `static` là thuộc tính của member class khi khai báo và tác động đến cách gắn outer instance. Vì vậy không thể nhìn riêng `getNestHost()` để suy ra một class có phải non-static inner hay không.

## 7. Một số ranh giới ngôn ngữ dễ nhầm

- **Inner class không đồng nghĩa với mọi lớp lồng.** Non-static member class là inner class; local class và anonymous class cũng thuộc nhóm inner class theo thuật ngữ Java. Bài này so sánh **hai member class**; dùng `isMemberClass()` trước khi suy luận bằng modifier.
- **Static nested class không kế thừa Outer.** `Outer.Nested` và `Outer.Inner` không tự động là subclass của `Outer`; chúng chỉ được khai báo trong không gian tên/phạm vi của `Outer`.
- **Generic type của Outer:** một member inner class có thể dùng type parameter của enclosing class, ví dụ `class Box<T> { class Entry { T value; } }`. Static nested class không tự dùng `T` của `Box<T>`; nó cần tự khai báo type parameter, ví dụ `static class Entry<U> { U value; }`.
- **Static member trong inner class:** trên Java 17, inner class có thể khai báo static member theo quy tắc hiện hành. Câu nói “inner class tuyệt đối không có static member” là kiến thức cũ và không nên áp dụng cho Java hiện đại (hạn chế đã được nới từ Java 16).
- **Class trong interface:** member class khai báo trong interface là `public static` ngầm định; nó không phải non-static inner class của một interface instance.
- **GC và vòng đời:** nếu một đối tượng inner còn tham chiếu đến enclosing instance, việc lưu inner trong cấu trúc sống lâu có thể giữ outer sống lâu ngoài dự định. Với callback/listener, cân nhắc static nested class nếu không cần trạng thái outer. Điều đó không có nghĩa cứ tạo inner class là gây memory leak.

## 8. Chọn loại nào trong thiết kế?

**Ưu tiên static nested class** khi kiểu lồng chỉ phục vụ tổ chức mã, builder, helper, result/DTO hoặc thuật toán và **không cần** dùng trực tiếp trạng thái của một đối tượng outer. Điều này khiến phụ thuộc vào outer instance rõ ràng hơn và tránh giữ outer reference vô tình.

**Dùng non-static inner class** khi mỗi đối tượng con có quan hệ tự nhiên, chặt chẽ với một outer instance và cần đọc/ghi trực tiếp trạng thái của đúng instance đó, ví dụ iterator hay view được gắn với trạng thái một collection. Khi dùng, chủ động cân nhắc tính đóng gói, vòng đời và số lượng instance được tạo.

Quy tắc nhận diện thực hành: hỏi **“Mỗi instance của lớp lồng có cần biết nó thuộc về instance Outer nào không?”** Nếu có, non-static inner có thể phù hợp; nếu không, static nested thường đơn giản hơn.

## 9. Tự kiểm tra

1. Vì sao `new Outer.Inner()` không hợp lệ bên ngoài instance context, nhưng `new Outer.Nested()` hợp lệ?
2. Hai object `a.new Inner()` và `b.new Inner()` sử dụng trạng thái outer khác nhau như thế nào?
3. Static nested class có được đọc trường `private` instance của `Outer` không? Cần điều kiện gì?
4. Vì sao Reflection thấy tham số `Outer` thêm vào constructor của member inner class?
5. `getNestHost() == Outer.class` có đủ chứng minh đó là non-static inner class không?

**Đáp án gợi ý:** (1) Inner cần enclosing instance; static nested thì không. (2) Mỗi inner gắn với đối tượng outer lúc tạo. (3) Có, nếu có tham chiếu `Outer` thích hợp và quyền truy cập hợp lệ. (4) Constructor cần biết enclosing object. (5) Không, static nested cũng có thể thuộc cùng nest.

**Ghi nhớ:** Hãy phân biệt ba câu hỏi độc lập khi đọc Reflection: *nó có được khai báo là member class không?* (`isMemberClass`), *member class đó có modifier static không?* (`Modifier.isStatic`), và *nó có cùng nest JVM với Outer không?* (`getNestHost`). Chỉ câu hỏi thứ hai phân biệt trực tiếp hai loại member class trong bài.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nested-types-nestmates-006">getNestHost, getNestMembers và isNestmateOf</a>

<details>
<summary>Xem chi tiết</summary>

# getNestHost, getNestMembers và isNestmateOf trong Java Reflection

## 1. Nest và nestmate là gì?

Từ Java 11, JVM hỗ trợ **nest-based access control**: một nhóm class/interface cùng *nest* có thể truy cập trực tiếp các thành viên `private` của nhau khi quy tắc truy cập Java cho phép. Trong nhóm này:

- **Nest host** là class/interface chủ của nest.
- **Nest members** gồm host và các class/interface được JVM xác nhận thuộc nest ấy.
- Hai kiểu là **nestmates** nếu chúng có cùng nest host.

Khi biên dịch mã Java thông thường, compiler thường chọn class ngoài cùng (*top-level class*) làm host; các class/interface lồng trong nó, kể cả nhiều tầng, được ghi nhận là thành viên. Đây là khái niệm ở **mức class file và JVM**, không đơn thuần là quan hệ thụt lề hoặc từ khóa `static` trong mã nguồn.

Ví dụ cấu trúc:

```text
Outer                         ← nest host
├── Outer$Inner               ← member
├── Outer$Inner$Deep          ← member, host vẫn là Outer
└── Outer$Helper              ← member

AnotherTopLevel              ← nest khác, dù ở cùng package
```

Mỗi class/interface thuộc đúng một nest. Một class độc lập thường là host của **nest chỉ có chính nó**. Các API trong bài nằm trên `java.lang.Class` và có từ **Java 11**.

### Nest tồn tại để làm gì?

Trước Java 11, compiler đôi khi cần sinh phương thức cầu nối tổng hợp (*synthetic accessor*) để bytecode của lớp lồng truy cập `private` của lớp bao ngoài. Từ Java 11, JVM nhận biết quan hệ nest và cho phép truy cập `private` giữa những kiểu thật sự là nestmates, giảm nhu cầu sinh accessor kiểu đó. Cơ chế này vẫn tuân theo kiểm tra truy cập của JVM; nó không khiến mọi class trong cùng package hoặc cùng tên ngoài trở thành nestmate.

Trong class file, thông tin thường được biểu diễn bởi:

- `NestMembers`: đặt trên host, khai báo các ứng viên thành viên được host cho phép.
- `NestHost`: đặt trên member, trỏ về host.

JVM kiểm tra sự phù hợp của thông tin này, bao gồm việc các kiểu thuộc cùng *runtime package* (tên package **và** class loader định nghĩa phù hợp) và host xác nhận member. Chỉ khai báo một chiều hoặc chỉ có cùng package là chưa đủ.

## 2. So sánh nhanh ba phương thức

| Phương thức | Kiểu trả về | Câu hỏi nó trả lời | Điểm cần nhớ |
|---|---|---|---|
| `Class<?> getNestHost()` | `Class<?>` | Kiểu này thuộc nest có host là ai? | Host của chính nó thì trả về chính `Class` ấy. |
| `Class<?>[] getNestMembers()` | Mảng `Class<?>` | JVM xác nhận những kiểu nào cùng nest? | Phần tử đầu là host; các phần tử sau không có thứ tự được bảo đảm. |
| `boolean isNestmateOf(Class<?> c)` | `boolean` | Kiểu hiện tại và `c` có cùng host không? | So sánh quan hệ nest giữa **hai kiểu**; tham số `null` gây `NullPointerException`. |

Cả ba là **instance method của đối tượng `Class`**; ví dụ `Reader.class.getNestHost()`. Chúng trả về metadata của kiểu tại runtime, không phải instance của kiểu đó.

## 3. `getNestHost()`: xác định nest host

Chữ ký:

```java
public Class<?> getNestHost()
```

- Nếu gọi trên member lồng thông thường, kết quả là class ngoài cùng làm host.
- Nếu gọi trên host, kết quả chính là host.
- Class/interface độc lập thông thường có nest chỉ gồm chính nó nên kết quả cũng là chính nó.
- Với `int.class`, `void.class` và các array class như `String[].class`, API trả về chính đối tượng `Class` được gọi. Mỗi kiểu như vậy được xem là nest một phần tử.

Ví dụ ngắn:

```java
class Box {
    static class Inner {}
}

class Check {
    public static void main(String[] args) {
        System.out.println(Box.Inner.class.getNestHost() == Box.class);
        System.out.println(Box.class.getNestHost() == Box.class);
        System.out.println(int[].class.getNestHost() == int[].class);
    }
}
```

Lưu thành `Check.java` (chỉ `Check` có `main`), chạy `javac Check.java` rồi `java Check`, kết quả:

```text
true
true
true
```

Kết quả host **không nhất thiết là** `getDeclaringClass()` hoặc `getEnclosingClass()`. Chẳng hạn `Outer.Inner.Deep.class.getDeclaringClass()` trả về `Outer.Inner.class`, nhưng host thường là `Outer.class`.

## 4. `getNestMembers()`: liệt kê các kiểu cùng nest

Chữ ký:

```java
public Class<?>[] getNestMembers()
```

Phương thức trước tiên xác định host `H`, rồi trả về mảng có `H` ở **vị trí 0** và các thành viên được `H` ghi nhận mà JVM xác minh được. Gọi phương thức từ host hoặc từ member cùng nest đều nhằm mô tả nest ấy.

Những chi tiết dễ bị bỏ sót:

1. `result[0]` là host, nhưng **thứ tự những phần tử còn lại không được bảo đảm**. API còn cho phép phần tử trùng lặp; đừng dùng vị trí hoặc số lượng như một giao ước ứng dụng.
2. Không phải mọi tên ghi trong metadata nhất thiết xuất hiện trong kết quả. JVM cố nạp/xác minh từng ứng viên và chỉ đưa vào những kiểu được xác nhận thuộc nest.
3. Nếu kiểu có nest một phần tử, mảng chỉ chứa chính kiểu đó. Với primitive, array và `void` cũng vậy.
4. **Hidden classes** được thêm vào nest bằng `MethodHandles.Lookup.defineHiddenClass` không xuất hiện trong mảng chỉ dựa trên `NestMembers`. Vì thế `getNestMembers()` không phải API liệt kê tuyệt đối mọi lớp động ở runtime.
5. Để hiển thị hoặc so sánh danh sách, nên dùng tên lớp, sắp xếp hoặc tập hợp khi phù hợp, thay vì phụ thuộc vào thứ tự gốc của mảng.

`getNestMembers()` khác `getDeclaredClasses()`. Phương thức thứ hai mô tả các member class/interface khai báo **trực tiếp** trong một class và không phải là phép liệt kê toàn bộ nest. Nest có thể chứa lớp lồng nhiều tầng, local class hoặc anonymous class do compiler đưa vào.

## 5. `isNestmateOf(Class<?> c)`: kiểm tra quan hệ giữa hai kiểu

Chữ ký:

```java
public boolean isNestmateOf(Class<?> c)
```

Kết quả là `true` nếu hai kiểu cùng nest host; nếu không thì `false`.

- Member với member cùng host: `true`.
- Host với member của nó: `true`.
- Một kiểu với chính nó: `true`.
- Hai top-level class khác nhau trong cùng package: thông thường `false` vì mỗi class có host riêng.
- Hai kiểu chỉ có quan hệ kế thừa cha–con: **không mặc nhiên** là nestmates.
- Gọi `SomeClass.class.isNestmateOf(null)` ném `NullPointerException`.

Điều kiện về nest được quyết định bởi metadata/JVM, không bằng phép so sánh `getSimpleName()`, vị trí trong package hoặc chuỗi chứa ký tự `$` của tên class.

## 6. Ví dụ tổng hợp chạy được trên Java 17+

Tạo file **`NestDemo.java`** với nội dung:

```java
import java.util.Arrays;

public class NestDemo {
    private static final String KEY = "host secret";

    static class Reader {
        String read() {
            return KEY; // Reader truy cập private của host
        }
    }

    static class Writer {
        private String key = "writer secret";
    }

    public static void main(String[] args) {
        Class<?> host = NestDemo.class;
        Class<?> reader = Reader.class;
        Class<?> writer = Writer.class;

        System.out.println("Host of Reader: "
                + reader.getNestHost().getSimpleName());
        System.out.println("Reader vs Writer: "
                + reader.isNestmateOf(writer));
        System.out.println("Host vs Reader: "
                + host.isNestmateOf(reader));
        System.out.println("Reader vs Unrelated: "
                + reader.isNestmateOf(Unrelated.class));
        System.out.println("Reader vs String: "
                + reader.isNestmateOf(String.class));
        System.out.println("Reader reads host private: "
                + new Reader().read());
        System.out.println("Host reads Writer private: "
                + new Writer().key);

        System.out.println("All declared nest members:");
        Arrays.stream(host.getNestMembers())
                .map(Class::getName)
                .sorted()
                .forEach(name -> System.out.println("- " + name));
    }
}

// Cùng file và cùng package, nhưng là top-level class khác.
class Unrelated {}
```

Biên dịch và chạy:

```bash
javac --release 17 NestDemo.java
java NestDemo
```

Kết quả:

```text
Host of Reader: NestDemo
Reader vs Writer: true
Host vs Reader: true
Reader vs Unrelated: false
Reader vs String: false
Reader reads host private: host secret
Host reads Writer private: writer secret
All declared nest members:
- NestDemo
- NestDemo$Reader
- NestDemo$Writer
```

**Giải thích kết quả:**

- `Reader` và `Writer` là hai member class của `NestDemo`, có chung nest host `NestDemo`; vì vậy `isNestmateOf` trả về `true`.
- `Reader.read()` truy cập trường `private static KEY` của host. Ngược lại, phương thức `main` trên host đọc trường `private key` của `Writer`; cả hai truy cập đều hợp lệ.
- `Unrelated` là một top-level class riêng. Việc nó được khai báo trong **cùng file `.java` và cùng package** không làm nó thuộc nest của `NestDemo`.
- `String` thuộc nest khác, nên kiểm tra cũng trả về `false`.
- Danh sách được sắp theo tên bằng `sorted()` để kết quả minh họa cố định. `getNestMembers()` **không tự bảo đảm** thứ tự sau host.
- Mỗi kiểu `NestDemo`, `Reader`, `Writer` là một `Class<?>` riêng, nhưng đều báo cùng host.

Để quan sát class file, chạy:

```bash
javap -v NestDemo
javap -v 'NestDemo$Reader'
```

Trong kết quả `javap`, tìm `NestMembers` trên `NestDemo` và `NestHost` trên `NestDemo$Reader`. Trên Windows PowerShell, giữ dấu nháy đơn quanh tên có `$` để shell không diễn giải thành biến. Đây là bằng chứng ở bytecode cho quan hệ nest; nội dung đầy đủ của `javap` phụ thuộc phiên bản compiler.

## 7. Nest so với cấu trúc lồng trong mã nguồn

Ba API dễ bị nhầm lẫn:

| API | Ý nghĩa |
|---|---|
| `getNestHost()` | Host theo quy tắc nest của JVM. |
| `getDeclaringClass()` | Kiểu trực tiếp **khai báo** một member class/interface; `null` với kiểu không phải member class. |
| `getEnclosingClass()` | Kiểu bao quanh về mặt ngữ cảnh khai báo, hữu ích cả với local/anonymous class. |
| `getDeclaredClasses()` | Những member class/interface khai báo trực tiếp bên trong kiểu đang xét. |

Với cấu trúc:

```java
class Outer {
    static class Inner {
        static class Deep {}
    }
}
```

Các biểu thức sau đúng khi biên dịch thông thường trên Java 17:

```java
Outer.Inner.Deep.class.getDeclaringClass() == Outer.Inner.class
Outer.Inner.Deep.class.getNestHost() == Outer.class
Outer.Inner.class.isNestmateOf(Outer.Inner.Deep.class)
```

Cả ba đều có giá trị `true`. Ví dụ trên là **các biểu thức minh họa** (không phải một file Java độc lập có `main`).

Local class và anonymous class cũng thường được compiler gán nest host là class ngoài cùng tương ứng. Để kiểm chứng bằng chương trình độc lập, tạo **`LocalNest.java`**:

```java
public class LocalNest {
    static Class<?> localType() {
        class Local {}
        return Local.class;
    }

    static Class<?> anonymousType() {
        return new Runnable() {
            @Override
            public void run() {}
        }.getClass();
    }

    public static void main(String[] args) {
        System.out.println(localType().getNestHost() == LocalNest.class);
        System.out.println(anonymousType().getNestHost() == LocalNest.class);
    }
}
```

Chạy `javac --release 17 LocalNest.java` rồi `java LocalNest`, kết quả:

```text
true
true
```

Do đó, không nên suy luận rằng `getNestMembers()` chỉ có nested class được viết bằng cú pháp member class. Danh sách thực tế dựa trên những class file đã được sinh và được JVM xác minh.

## 8. Các tình huống đặc biệt và giới hạn

**Primitive, array, `void`:** mỗi kiểu này được coi là nest riêng; ví dụ `String[].class.getNestHost() == String[].class` là `true`, còn `String[].class.isNestmateOf(String.class)` là `false`. Đừng hiểu “array của String” đồng nghĩa là nestmate của `String`.

**Class loader:** hai kiểu nhìn như cùng package theo tên văn bản vẫn có thể khác *runtime package* nếu được định nghĩa bởi class loader khác. Quy tắc nest không vượt qua ranh giới runtime package. Với framework nạp/biến đổi bytecode, kiểm tra quan hệ nest qua API thực tế an toàn hơn suy đoán từ tên class.

**Class file không nhất quán:** nếu bytecode bị biến đổi khiến `NestHost`/`NestMembers` không phù hợp, quá trình xác định nest/truy cập `private` có thể thất bại hoặc kiểu bị xem như singleton nest tùy điều kiện xác minh. Cần kiểm tra bytecode thực tế, class loader và thông báo JVM thay vì kết luận từ tên class.

**Hidden class:** một hidden class có thể được định nghĩa với quyền nestmate theo `Lookup.defineHiddenClass` và tùy chọn `NESTMATE`, nhưng không được bảo đảm xuất hiện trong `getNestMembers()` của host. Không dùng API này làm cơ chế khám phá đầy đủ mọi class động của một framework.

**Reflection không mở khóa truy cập vô điều kiện:** `isNestmateOf` chỉ cho biết quan hệ nest; nó không tự cấp quyền cho mã ở một class khác gọi mọi method `private` bằng `Method.invoke`. Quyền truy cập phản chiếu còn phụ thuộc caller, thành viên, `AccessibleObject` và quy tắc module/encapsulation. Nếu muốn gọi method private, cần kiểm tra quyền của chính thao tác reflection đó.

## 9. Cách dùng trong thực tế

1. **Phân tích cấu trúc class:** gọi `getNestHost()` khi cần biết một member class, local class hay anonymous class thuộc host nào trong runtime.
2. **Chẩn đoán công cụ sinh/biến đổi bytecode:** kết hợp `javap -v` với `getNestMembers()` và `isNestmateOf()` để xác nhận compiler hoặc enhancer đã duy trì thông tin nest hợp lệ.
3. **Điều tra lỗi truy cập `private`:** nếu hai kiểu dự kiến là nestmate nhưng kiểm tra trả về `false`, rà soát class file, package, class loader và cách tạo kiểu động.
4. **Hiển thị metadata:** dùng `getNestMembers()` rồi sắp xếp/deduplicate theo nhu cầu hiển thị; tránh dựa vào thứ tự và đếm phần tử gốc.

Ví dụ helper kiểm tra quan hệ theo API trực tiếp:

```java
static boolean shareNest(Class<?> first, Class<?> second) {
    if (first == null || second == null) {
        return false; // chính sách của helper; API isNestmateOf(null) ném NPE
    }
    return first.isNestmateOf(second);
}
```

Helper chỉ minh họa chính sách đầu vào `null` của ứng dụng và phải đặt trong một class để biên dịch; nó không thay đổi hợp đồng của API JVM.

## 10. Lỗi hiểu sai thường gặp

- **“Hai lớp cùng package là nestmates.”** Sai: cùng package chỉ là một điều kiện liên quan; thông tin host/member còn phải khớp.
- **“Class con luôn cùng nest với class cha.”** Sai: quan hệ kế thừa và quan hệ nest độc lập.
- **“`getNestHost()` luôn trả về class bao trực tiếp.”** Sai: lớp lồng nhiều tầng thường vẫn có host là top-level class.
- **“`getNestMembers()[1]` luôn là lớp lồng đầu tiên.”** Sai: chỉ phần tử 0 được bảo đảm là host; các phần tử sau có thứ tự không xác định.
- **“`getNestMembers()` liệt kê hết mọi hidden class.”** Sai: hidden class thêm qua `defineHiddenClass` không nằm trong danh sách `NestMembers` phản ánh bởi API.
- **“Kiểm tra `isNestmateOf()` bằng `true` là có thể bypass private trên mọi đối tượng.”** Sai: quan hệ nest không vô hiệu hóa quy tắc kiểm tra truy cập hoặc cơ chế đóng gói module.
- **“Mọi nested class có host khác nhau.”** Sai: các member của cùng nest chia sẻ một host.

## 11. Tự kiểm tra kiến thức

1. Vì sao `Outer.Inner.Deep.class.getNestHost()` thường trả về `Outer.class` thay vì `Outer.Inner.class`?
2. Tại sao hai top-level class trong cùng `.java` thường không phải nestmates?
3. Khi in `getNestMembers()`, vì sao cần sắp xếp nếu muốn kết quả tái lập?
4. Khi dùng runtime code generation, vì sao không thể dùng `getNestMembers()` để liệt kê chắc chắn mọi hidden class?
5. Nestmate liên quan thế nào đến truy cập `private`, và vì sao nó không thay cho kiểm tra quyền Reflection?

**Ghi nhớ:** `getNestHost()` hỏi **host là ai**; `getNestMembers()` hỏi **những member nào được xác nhận trong nest**; `isNestmateOf(c)` hỏi **hai kiểu có chung host không**. Cả ba phản ánh quan hệ nest ở JVM (Java 11+), không thể suy ra chỉ từ package, kế thừa hoặc tên class.

## Tài liệu chuẩn

- [Java SE 17 — `Class.getNestHost`, `Class.getNestMembers`, `Class.isNestmateOf`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html)
- [JVMS 17 — `NestHost`/`NestMembers` và quy tắc nestmate access control](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html)
- [JVMS 17 — Class File Format, Nest Attributes](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nested-types-nestmates-007">Tham số hoặc field tổng hợp để giữ ngữ cảnh bao ngoài</a>

<details>
<summary>Xem chi tiết</summary>

# Tham số hoặc field tổng hợp để giữ ngữ cảnh bao ngoài trong Java

## 1. Vấn đề cần hiểu

Khi biên dịch **inner class** (lớp lồng không `static`), **local class** (lớp khai báo trong một khối lệnh) hoặc **anonymous class** (lớp vô danh), trình biên dịch có thể tạo thêm **tham số constructor** và **field** không được lập trình viên khai báo trong mã nguồn. Chúng truyền hoặc giữ tham chiếu đến đối tượng bao ngoài (enclosing instance) và những biến cục bộ được lớp bên trong sử dụng.

Đây là chi tiết đặc biệt quan trọng khi làm việc với **Reflection**: danh sách tham số constructor hay danh sách field nhìn ở bytecode có thể khác những gì bạn đọc được từ khai báo Java. Từ khóa `synthetic` mô tả thành phần do trình biên dịch sinh ra để thực hiện ngôn ngữ, không phải thành phần được khai báo trực tiếp trong source.

Cần phân biệt ba khái niệm:

- **Lexical enclosing class**: lớp bao quanh về mặt khai báo, ví dụ `Outer` bao quanh `Outer.Inner`.
- **Enclosing instance**: một đối tượng cụ thể của `Outer` gắn với một đối tượng `Inner`; `Outer.this` trong `Inner` dùng để truy cập đối tượng ấy.
- **Captured local variable**: biến cục bộ hoặc tham số của phương thức/constructor ở phạm vi bao quanh, được local/anonymous class sử dụng. Biến phải `final` hoặc *effectively final*; việc lưu biến này không đồng nghĩa với việc lưu một enclosing instance.

Không phải cứ thấy ký hiệu `$` trong tên lớp là chắc chắn có field tổng hợp: tên nhị phân chỉ phản ánh quy ước biên dịch phổ biến. Muốn xác định chính xác, kiểm tra cấu trúc lớp thực tế.

## 2. Tại sao cần tham số và field bổ sung?

Xét khai báo:

~~~java
class Outer {
    private final String name = "outer";

    class Inner {
        String message() {
            return Outer.this.name;
        }
    }
}
~~~

Trong mã nguồn, ta tạo `Inner` bằng `outer.new Inner()`. Đối tượng `Inner` cần biết `outer` nào là đối tượng bao ngoài để thực hiện `Outer.this.name`. Một cách triển khai thường gặp của `javac` là:

1. **Constructor ở bytecode nhận thêm đối số `Outer`** (thường nằm trước tham số do người dùng khai báo).
2. **Một field tham chiếu tới `Outer`** được gán từ đối số constructor để các phương thức của `Inner` có thể dùng về sau. Với `javac`, tên hay gặp là `this$0`; đó chỉ là tên triển khai.
3. Field mang cờ **synthetic** (có thể quan sát qua `Field.isSynthetic()`). Cách ánh xạ và tên field là chi tiết trình biên dịch, không phải API được Java bảo đảm ổn định.

Một cách hình dung gần đúng cho bytecode là:

~~~text
Outer.Inner(Outer outer, ...) {
    this.<outer-reference> = outer;
    ...
}
~~~

Đoạn trên **chỉ mô tả ý tưởng**, không phải mã Java hợp lệ: `<outer-reference>` là field ẩn, không thể khai báo hoặc gọi trực tiếp bằng tên giả định từ mã ứng dụng.

Về **ngữ nghĩa ngôn ngữ**, mỗi instance của member inner class được tạo với một enclosing instance theo các quy tắc của Java. Về **biểu diễn vật lý**, Java không buộc mọi trình biên dịch và mọi phiên bản phải tạo field giống nhau: các bản `javac` hiện đại có thể bỏ field tham chiếu bao ngoài nếu không có chỗ nào cần sử dụng nó.

## 3. Ví dụ Java 17+: quan sát qua Reflection và gọi constructor

Lưu toàn bộ chương trình sau trong tệp **`EnclosingContextDemo.java`**:

~~~java
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;

public class EnclosingContextDemo {
    static class Outer {
        private final String name;

        Outer(String name) {
            this.name = name;
        }

        class Inner {
            private final int id;

            Inner(int id) {
                this.id = id;
            }

            String describe() {
                return Outer.this.name + ":" + id;
            }
        }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Outer outer = new Outer("CTX");
        Outer.Inner normal = outer.new Inner(7);
        System.out.println("normal=" + normal.describe());

        Class<Outer.Inner> type = Outer.Inner.class;

        // Tham số Outer là tham số biên dịch bổ sung.
        Constructor<Outer.Inner> ctor =
                type.getDeclaredConstructor(Outer.class, int.class);

        Class<?>[] parameterTypes = ctor.getParameterTypes();
        System.out.println("hasOuterParameter="
                + (parameterTypes.length == 2
                    && parameterTypes[0] == Outer.class
                    && parameterTypes[1] == int.class));

        Outer.Inner reflected = ctor.newInstance(outer, 8);
        System.out.println("reflected=" + reflected.describe());

        // Inner thực sự sử dụng Outer.this nên javac cần giữ tham chiếu.
        boolean foundSyntheticOuterField = Arrays.stream(type.getDeclaredFields())
                .anyMatch(field ->
                        field.isSynthetic() && field.getType() == Outer.class);
        System.out.println("hasSyntheticOuterField=" + foundSyntheticOuterField);

        for (Field field : type.getDeclaredFields()) {
            System.out.println("field=" + field.getName()
                    + ", type=" + field.getType().getSimpleName()
                    + ", synthetic=" + field.isSynthetic());
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac EnclosingContextDemo.java
java EnclosingContextDemo
~~~

**Bốn dòng đầu** khi dùng `javac` thông thường:

~~~text
normal=CTX:7
hasOuterParameter=true
reflected=CTX:8
hasSyntheticOuterField=true
~~~

Các dòng bắt đầu bằng `field=` liệt kê field thực tế. Với `javac`, bạn thường thấy field `id` có `synthetic=false` và một field tham chiếu `Outer` (thường `this$0`) có `synthetic=true`. **Không dựa vào thứ tự dòng hay chính tả tên field**: Reflection không cam kết thứ tự trả về của `getDeclaredFields()`, và chi tiết tổng hợp có thể thay đổi giữa trình biên dịch.

Vì phương thức `describe()` thực sự truy cập `Outer.this.name`, chương trình có một lý do cụ thể để giữ tham chiếu `Outer`. Nếu `Inner` không sử dụng dữ liệu của `Outer`, kết quả về field có thể khác; không nên biến sự hiện diện của `this$0` thành giả định phổ quát.

**Điểm mấu chốt của constructor:** trong source chỉ thấy `Inner(int id)`, còn Reflection trên lớp được `javac` biên dịch cần tìm `getDeclaredConstructor(Outer.class, int.class)`. Gọi nhầm `getDeclaredConstructor(int.class)` sẽ gây `NoSuchMethodException` trong ví dụ này. `Constructor.newInstance(outer, 8)` cần cả đối tượng bao ngoài và tham số `id`; gọi thiếu sẽ gây `IllegalArgumentException` do số lượng/kiểu đối số không khớp.

## 4. So sánh các kiểu lớp lồng

| Kiểu lớp | Có enclosing instance? | Điều thường thấy trong bytecode / Reflection |
| --- | --- | --- |
| **Member inner class**: `class Outer { class Inner {} }` | Có khi tạo instance; biểu thức tạo có thể là `outer.new Inner()` | Constructor `javac` thường có thêm tham số `Outer`; field giữ `Outer` khi cần truy cập đối tượng bao ngoài. |
| **Static nested class**: `static class Nested {}` | Không có enclosing instance mặc định | Không cần tham số `Outer` hoặc field `this$0` chỉ vì khai báo lồng; vẫn có thể có thành phần synthetic vì lý do khác. |
| **Local class** khai báo trong phương thức/block | Tùy ngữ cảnh khai báo và cách tạo; có thể dùng enclosing instance | Có thể có tham số/field cho outer instance và cho các biến cục bộ được capture. |
| **Anonymous class**: `new Runnable() { ... }` | Tùy ngữ cảnh tạo và biểu thức thân lớp | Có thể nhận/lưu outer instance, biến captured và các đối số cần thiết để khởi tạo superclass. |

Không nên đồng nhất **lớp lồng** với **inner class**: static nested class là nested class nhưng không phải inner class. Tương tự, local class trong ngữ cảnh `static` không tự có đối tượng bao ngoài chỉ vì được viết bên trong một lớp.

Trong inner class:

- `this` chỉ chính đối tượng inner.
- `Outer.this` chỉ đối tượng bao ngoài tương ứng, khi biểu thức này hợp lệ.
- `Outer.class` là đối tượng `Class` mô tả kiểu `Outer`, **không phải** một enclosing instance.
- `Outer.Inner.class.getDeclaringClass()` giúp xác định lớp khai báo `Inner`, nhưng **không trả về đối tượng `outer`** gắn với một instance.

## 5. Capture biến cục bộ: field khác với outer reference

Một local/anonymous class còn có thể cần giữ **giá trị của biến cục bộ** tại thời điểm tạo đối tượng. Ví dụ độc lập:

~~~java
public class LocalCaptureDemo {
    static Runnable makeTask(String label) {
        int copies = label.length(); // không phải hằng compile-time
        class Task implements Runnable {
            @Override
            public void run() {
                System.out.println(label.repeat(copies));
            }
        }
        return new Task();
    }

    public static void main(String[] args) {
        Runnable task = makeTask("Hi");
        task.run();
    }
}
~~~

Chạy:

~~~bash
javac LocalCaptureDemo.java
java LocalCaptureDemo
~~~

Stdout:

~~~text
HiHi
~~~

Sau khi `makeTask` đã kết thúc, `Task.run()` vẫn dùng được `label` và `copies` vì đối tượng local class có thể lưu các giá trị được capture trong các field do trình biên dịch thêm vào. Trong một số bytecode do `javac` tạo, tên field kiểu này có dạng `val$label` hoặc `val$copies`; tên và cách bố trí không cố định. Ví dụ nằm trong **phương thức static**, nên nó **không cần** enclosing instance của `LocalCaptureDemo`.

Những điểm cần nhớ:

- `label` và `copies` là *effectively final*: sau khi gán, không được gán lại nếu muốn capture chúng trong local/anonymous class.
- Capture biến cục bộ giữ **giá trị của biến**. Nếu giá trị là một tham chiếu tới object, cả local class và bên ngoài có thể quan sát trạng thái object thay đổi; không có nghĩa Java cho phép gán lại chính biến captured.
- Lambda (`() -> ...`) cũng có quy tắc capture, nhưng **không phải là anonymous class**. Triển khai lambda thường dùng `invokedynamic`; không áp đặt mô hình `this$0` hay `val$...` của local/anonymous class lên lambda.

## 6. Reflection: chọn đúng API và đọc đúng tín hiệu

Các API hữu ích khi phân tích cấu trúc đã biên dịch:

| API | Dùng để làm gì | Điều không nên suy luận |
| --- | --- | --- |
| `Class.getDeclaredFields()` | Lấy field khai báo trong class file, gồm field private và synthetic nếu hiện diện | Không cam kết thứ tự, không bao gồm field kế thừa. |
| `Field.isSynthetic()` | Kiểm tra cờ synthetic của một field | Không khẳng định mọi field synthetic đều là enclosing reference. |
| `Class.getDeclaredConstructors()` | Lấy constructor khai báo, kể cả non-public | Danh sách tham số không nhất thiết trùng danh sách tham số trong source. |
| `Constructor.getParameterTypes()` / `getParameters()` | Xem các tham số ở cấp Reflection | Không dùng chỉ số tham số trong source để suy ra tuyệt đối chỉ số tại bytecode. |
| `Parameter.isImplicit()` / `isSynthetic()` | Kiểm tra metadata về tham số ngầm định/tổng hợp khi có | Cờ và tên hiển thị phụ thuộc class file, phiên bản/trình biên dịch và metadata được phát hành. |
| `Class.isMemberClass()` / `isLocalClass()` / `isAnonymousClass()` | Xác định loại lớp | Không chứng minh một instance thực tế đang giữ field outer reference. |
| `Class.getDeclaringClass()` / `getEnclosingClass()` / `getEnclosingMethod()` / `getEnclosingConstructor()` | Xem quan hệ khai báo/bao quanh ở cấp kiểu | Không truy xuất trực tiếp giá trị tham chiếu bao ngoài của một object. |

**Phân biệt `synthetic` và `implicit`:** `Field.isSynthetic()` phản ánh cờ field trên bytecode. Với tham số constructor, `Parameter.isImplicit()` phản ánh tham số được ngôn ngữ khai báo ngầm; `Parameter.isSynthetic()` phản ánh tham số do trình biên dịch thêm không xuất hiện trong source theo phân loại của metadata. Đừng mặc định mọi tham số outer sẽ cho cùng giá trị các cờ trên mọi bản biên dịch. Hãy in kết quả thực tế và đối chiếu loại constructor, class file và cách build.

Khi Reflection dùng `getDeclaredConstructor(...)`, hãy xét **mảng kiểu tham số thực tế** bằng `getParameterTypes()` trước. Đừng tìm tham số chỉ bằng tên `this$0`, `arg0` hoặc một tên máy sinh khác. Việc biên dịch với `javac -parameters` giúp giữ nhiều thông tin tên tham số cho Reflection, nhưng **không xóa** những tham số do compiler bổ sung, cũng không bảo đảm mọi chi tiết nội bộ đều là API ổn định.

`getDeclaredFields()` cho phép *quan sát metadata* của field không public, nhưng việc **đọc giá trị** qua `Field.get(object)` còn phụ thuộc kiểm tra quyền truy cập. Từ Java 9, giới hạn mạnh của module có thể khiến `setAccessible(true)` không thành công đối với package không được mở; không nên viết tiện ích phụ thuộc đọc `this$0` bằng Reflection để làm logic ứng dụng.

## 7. Xem bytecode để đối chiếu

Sau khi biên dịch `EnclosingContextDemo.java`, có thể dùng:

~~~bash
javap -p 'EnclosingContextDemo$Outer$Inner'
javap -v 'EnclosingContextDemo$Outer$Inner'
~~~

Dấu nháy đơn giữ nguyên ký tự `$` trên shell có hỗ trợ dạng trích dẫn này; trên Windows PowerShell, có thể dùng dấu nháy đơn như ví dụ. Lệnh đầu in field và constructor kể cả private; lệnh `-v` hiển thị thông tin chi tiết class file, trong đó có cờ `ACC_SYNTHETIC` khi field được gắn synthetic. Với lớp trong ví dụ, tìm constructor mang tham số kiểu `EnclosingContextDemo$Outer` và một field có kiểu đó.

Nếu quan sát lớp được biên dịch từ **compiler khác** hoặc **JDK khác**, tên và số field không nhất thiết trùng. Đặc biệt, `javac` từ các bản JDK mới hơn đã có tối ưu hóa bỏ field outer-reference không dùng tới ở một số trường hợp; việc không thấy field không tự động kết luận lớp không có quan hệ enclosing instance theo ngôn ngữ.

`javap` là công cụ chẩn đoán, không phải hợp đồng cho code ứng dụng. Hãy thiết kế thao tác Reflection dựa trên cấu trúc được xác minh tại runtime, hoặc tốt hơn, tránh phụ thuộc vào các thành phần ẩn do compiler tạo.

## 8. Lỗi hay gặp và cách xử lý

**Lỗi 1 — Tìm constructor chỉ theo tham số viết trong source.**

~~~java
// Sai đối với Inner trong ví dụ phần 3:
Outer.Inner.class.getDeclaredConstructor(int.class);

// Đúng với class file javac của ví dụ:
Outer.Inner.class.getDeclaredConstructor(Outer.class, int.class);
~~~

Nếu cần khởi tạo class inner từ Reflection, trước hết có enclosing instance đúng kiểu, kiểm tra signature thực tế rồi truyền đủ đối số cho `newInstance`.

**Lỗi 2 — Coi tất cả field là dữ liệu nghiệp vụ.** Khi tự động liệt kê field để serialize, log, so sánh hoặc ánh xạ object, field synthetic có thể vô tình bị đưa vào, khiến object graph chứa thêm tham chiếu bao ngoài hoặc gây vòng tham chiếu. Hãy định nghĩa rõ chính sách field; thường cần kiểm tra `Field.isSynthetic()` và `Modifier.isStatic(field.getModifiers())`, đồng thời cân nhắc annotation/schema của nghiệp vụ. Không được xóa mù quáng field synthetic trong mọi công cụ phân tích bytecode, vì chúng có vai trò ở cấp runtime.

**Lỗi 3 — Mặc định inner class nào cũng làm outer không được GC.** Một outer reference **thực sự được giữ** bởi inner object sống lâu có thể kéo dài vòng đời outer object. Nhưng chi tiết field có thể được tối ưu nếu không dùng, nên phải xem bytecode hoặc heap thực tế trước khi chẩn đoán rò rỉ bộ nhớ. Nếu không cần outer instance, cân nhắc `static nested class` và truyền tường minh dữ liệu thiết yếu.

**Lỗi 4 — Phụ thuộc tên `this$0`/`val$...`.** Đây không phải tên ổn định trong đặc tả Java. Không hard-code chúng trong framework, unit test hay mapper; kiểm tra `isSynthetic()`, kiểu, constructor và bối cảnh cụ thể, hoặc dùng API nguồn dữ liệu rõ ràng thay vì đọc cấu trúc ẩn.

**Lỗi 5 — Nhầm capture local với outer capture.** Local class ở trong phương thức static vẫn có thể giữ biến cục bộ, nhưng không có enclosing instance ngầm. Quan sát constructor và field theo **ngữ cảnh tạo object**, không chỉ theo vị trí viết class.

**Lỗi 6 — Dựa vào thứ tự của Reflection.** Không nên dựa vào thứ tự `getDeclaredFields()`, `getDeclaredConstructors()` hoặc suy luận “tham số thứ 0 luôn là do người dùng khai báo”. Với constructor cần tương thích compiler cụ thể, định vị theo signature thực và xác minh mỗi tham số.

## 9. Câu hỏi tự kiểm tra

1. Với `Outer.Inner` dùng `Outer.this`, vì sao Reflection thường thấy constructor có thêm tham số `Outer` dù source chỉ khai báo `Inner(int)`?
2. Vì sao `static class Nested` không cần tham chiếu outer instance chỉ vì được khai báo bên trong `Outer`?
3. Một local class trong phương thức static sử dụng biến `String label` có thể có field synthetic không? Nó có cần một object `Outer` không?
4. Nếu một bản `javac` không sinh `this$0` khi inner class không truy cập outer, điều đó có cho phép giả định mọi inner class khác cũng không có field ấy không?
5. Khi dựng mapper Reflection, vì sao nên xem xét `Field.isSynthetic()` và tránh coi tên field do compiler đặt là API?

**Ghi nhớ:** tham số/field tổng hợp là một cách trình biên dịch hiện thực hóa enclosing instance và biến được capture. Phân biệt **quan hệ của source Java** với **chi tiết class file**, và khi dùng Reflection phải kiểm tra cấu trúc thực tế thay vì dựa vào tên nội bộ hoặc chữ ký constructor nhìn thấy trong source.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nested-types-nestmates-008">Tên và cấu trúc do compiler sinh không phải hợp đồng ứng dụng</a>

<details>
<summary>Xem chi tiết</summary>

# Tên và cấu trúc do compiler sinh không phải hợp đồng ứng dụng

Trong Java Reflection, chương trình có thể nhìn thấy kiểu lồng, tên lớp, constructor, field và method của bytecode đã nạp. Nhưng **không phải mọi thứ nhìn thấy qua Reflection đều là hợp đồng ứng dụng**. Compiler có thể thêm phương thức cầu nối, accessor, field giữ biến capture hoặc lớp phục vụ biểu thức anonymous. Tên/số lượng/cách bố trí nhiều thành phần này phụ thuộc compiler, target bytecode và cách sửa source.

Bài này thuộc chủ đề **Kiểu lồng và nest** của Java Reflection. Ta sẽ phân biệt metadata được đặc tả với chi tiết phát sinh trong quá trình biên dịch, rồi sử dụng `Class<?>` đúng cách để tránh gắn logic nghiệp vụ với tên như `Outer$1`, `access$000` hoặc `this$0`.

## 1. Ba tầng cần phân biệt

1. **Ngữ nghĩa source:** member class, local class, anonymous class, lambda, quy tắc truy cập private và tên kiểu Java. Java Language Specification (JLS) định nghĩa ý nghĩa các cấu trúc này.
2. **Biểu diễn runtime/class file:** binary name, constructor parameter, field, method, cờ `synthetic`/`bridge` và thuộc tính class file như `InnerClasses`, `EnclosingMethod`, `NestHost`/`NestMembers`. Một số chi tiết được đặc tả; một số do compiler lựa chọn.
3. **Hợp đồng ứng dụng:** kiểu/interface công bố, API ổn định, annotation/schema/registry do ứng dụng thiết kế. Đây mới là lớp mà logic nghiệp vụ, dữ liệu lưu lâu dài và các module khác nên phụ thuộc.

Điểm tinh tế: **binary name của member class có tên được JLS quy định** (ví dụ `pkg.Outer$Inner`), còn **số trong tên của local/anonymous class không phải ID nghiệp vụ ổn định**. Không nên gọi tất cả tên chứa `$` là “ngẫu nhiên”, và cũng không được suy ngược rằng tất cả tên đó có ý nghĩa ứng dụng.

## 2. Nhận diện đúng các loại class

| Loại | Khai báo/nguồn tạo | Reflection thích hợp | Không nên giả định |
| --- | --- | --- | --- |
| Member class hoặc interface | Khai báo trực tiếp bên trong một class/interface | `isMemberClass()`, `getDeclaringClass()` | Mọi class có dấu `$` đều là member |
| Local class | Khai báo trong block, thường ở phương thức | `isLocalClass()`, `getEnclosingMethod()` | Số xuất hiện trong binary name là thứ tự/ID ổn định |
| Anonymous class | Biểu thức tạo lớp vô danh | `isAnonymousClass()`, `getEnclosingClass()` | Luôn được phép gọi qua tên `$1` |
| Lambda | Biểu thức chức năng như `() -> ...` | Functional interface và hành vi của nó | Luôn có file `.class` hoặc tên runtime tái sử dụng được |
| Synthetic/bridge member | Compiler sinh hỗ trợ bytecode | `Member.isSynthetic()`, `Method.isBridge()` | Tên/số lượng method/field phát sinh là API |

`isSynthetic()` không phải phép thử nhận diện mọi local hay anonymous class. `getDeclaredClasses()` chỉ liệt kê member class/interface được khai báo trực tiếp trong một class, **không quét** mọi local/anonymous class từng xuất hiện trong các phương thức của nó.

### Ví dụ Java 17+: metadata thay cho cắt chuỗi tên

Lưu thành `ReflectionKindsDemo.java`:

```java
public class ReflectionKindsDemo {
    static class Member { }

    static Class<?> localClass() {
        class Local { }
        return Local.class;
    }

    static Class<?> anonymousClass() {
        Runnable task = new Runnable() {
            @Override
            public void run() { }
        };
        return task.getClass();
    }

    static void show(String label, Class<?> type) {
        System.out.printf(
            "%s: member=%s, local=%s, anonymous=%s, canonical=%s%n",
            label,
            type.isMemberClass(),
            type.isLocalClass(),
            type.isAnonymousClass(),
            type.getCanonicalName()
        );
    }

    public static void main(String[] args) {
        show("member", Member.class);
        show("local", localClass());
        show("anonymous", anonymousClass());
    }
}
```

```bash
javac ReflectionKindsDemo.java
java ReflectionKindsDemo
```

Kết quả:

```text
member: member=true, local=false, anonymous=false, canonical=ReflectionKindsDemo.Member
local: member=false, local=true, anonymous=false, canonical=null
anonymous: member=false, local=false, anonymous=true, canonical=null
```

Member `Member` có canonical name; local và anonymous class thì `getCanonicalName()` trả `null`. Các phương thức `isMemberClass()`, `isLocalClass()` và `isAnonymousClass()` trả lời chính xác ý nghĩa cần phân loại. Nếu in thêm `type.getName()`, một bản `javac` thường có thể tạo tên kiểu `ReflectionKindsDemo$Member`, `ReflectionKindsDemo$1Local` và `ReflectionKindsDemo$1`. Các số ở đây chỉ là ví dụ quan sát, **không phải output được kiểm thử hay ID bền vững**.

## 3. Binary name, canonical name và simple name

- `Class.getName()` phản ánh tên runtime, với class thông thường là binary name. Với member class `pkg.Outer.Inner` có tên, binary name theo JLS là `pkg.Outer$Inner`.
- `getCanonicalName()` thích hợp cho kiểu có canonical name; local/anonymous class trả `null`.
- `getSimpleName()` cho simple name; anonymous class có simple name rỗng.
- `getTypeName()` cho tên dạng dễ đọc hơn trong một số ngữ cảnh nhưng cũng không phải định danh nghiệp vụ.
- `Class.forName(...)` nhận binary name thích hợp, chịu ảnh hưởng của class loader và khả năng nạp lớp.

Ví dụ minh họa (giả sử đã có member type `pkg.Outer.Inner`):

```java
Class<?> member = Class.forName("pkg.Outer$Inner");
```

Dùng binary name của **một member class có tên đã biết và được công bố** là hợp lệ. Trái lại, đoán `pkg.Outer$1` để gọi một anonymous class là phụ thuộc vào cách compiler đánh số. Cũng đừng suy luận “tách tại dấu `$` cuối cùng” để xác định class bao ngoài: `$` có thể là ký tự hợp lệ trong tên định danh Java; hơn nữa class local, anonymous và synthetic có những ý nghĩa khác member class.

Ví dụ dễ vỡ:

```java
String binaryName = type.getName();
String guessedRole = binaryName.substring(binaryName.lastIndexOf('$') + 1);

// Không dùng guessedRole làm quyền truy cập, tên plugin hoặc khóa database.
```

Một định danh được lưu vào database, file cấu hình, sự kiện hoặc JSON nên là **khóa được ứng dụng định nghĩa**, không phải một mảnh tên do compiler gán.

## 4. Quan hệ khai báo, ngữ cảnh bao và tập kiểu lồng

Các API này trả lời những câu hỏi khác nhau:

| API | Đúng nghĩa | Giới hạn |
| --- | --- | --- |
| `getDeclaringClass()` | Class/interface khai báo kiểu này như **member** | Local/anonymous không có declaring class như member |
| `getEnclosingClass()` | Class bao gần nhất | Có thể có cho member, local, anonymous |
| `getEnclosingMethod()` | Phương thức bao trực tiếp local/anonymous class | Có thể `null` nếu nằm trong constructor/initializer |
| `getEnclosingConstructor()` | Constructor bao trực tiếp local/anonymous class | Không phải constructor được dùng để tạo mọi instance |
| `getDeclaredClasses()` | Member class/interface khai báo trực tiếp | Không gồm member kế thừa, local hoặc anonymous |
| `getClasses()` | Member type public, kể cả được kế thừa | Không giống `getDeclaredClasses()` |

Với `ReflectionKindsDemo`, `Member.class.getDeclaringClass() == ReflectionKindsDemo.class`. Với local/anonymous class tạo trong static method, `getDeclaringClass()` trả `null` nhưng `getEnclosingClass()` có thể cho biết `ReflectionKindsDemo.class`.

Nếu viết công cụ tạo tài liệu, hãy nêu rõ mục tiêu là **các member type được khai báo**, **các class cùng nest**, hay **mọi class đã nạp trong runtime**. Không API nào ở bảng trên tương đương với truy vấn “toàn bộ kiểu trong một file source”. Tập kết quả Reflection cũng không nên được coi là có thứ tự khai báo đảm bảo; khi cần trình bày ổn định, hãy sắp xếp theo quy tắc riêng và xử lý trường hợp trùng khóa.

## 5. Nestmates của JVM: cùng nest không có nghĩa cùng một cấu trúc tên

Từ Java 11, JVM hỗ trợ **nest-based access control**. Metadata class file như `NestHost` và `NestMembers` mô tả những lớp cùng nest để JVM kiểm tra quyền truy cập private. Cần phân biệt:

- `getNestHost()`: host của nest hiện tại.
- `isNestmateOf(other)`: kiểm tra hai class có chung nest host.
- `getNestMembers()`: trả các class mà nest host ghi nhận là thành viên. Thứ tự không được bảo đảm; API này cũng không liệt kê hidden class được thêm vào nest động qua `Lookup.defineHiddenClass`.

Lưu ví dụ thành `NestAccessDemo.java`:

```java
public class NestAccessDemo {
    private static String secret() {
        return "ready";
    }

    static class Helper {
        static String call() {
            return secret();
        }
    }

    public static void main(String[] args) {
        System.out.println(Helper.class.getNestHost() == NestAccessDemo.class);
        System.out.println(NestAccessDemo.class.isNestmateOf(Helper.class));
        System.out.println(Helper.call());
    }
}
```

```bash
javac NestAccessDemo.java
java NestAccessDemo
```

```text
true
true
ready
```

Kết quả thể hiện quan hệ nest và quyền gọi method `private` thông qua code hợp lệ. **Không kết luận hai lớp là nestmate từ việc cùng tiền tố trước dấu `$`.** JVM kiểm tra theo metadata và quy tắc của nó; `getNestMembers()` cũng không thay thế `getDeclaredClasses()`. Một local/anonymous class có thể là nestmate mà không phải member class khai báo trực tiếp.

Với target bytecode cũ hơn Java 11, compiler Java từng cần tạo synthetic accessor (thường nhìn thấy tên dạng `access$...`) cho một số truy cập private xuyên qua lớp lồng. Khi dùng nest metadata, nhu cầu sinh accessor kiểu đó thay đổi. Vì vậy, **không có API công khai mang tên `access$000` để logic ứng dụng được quyền phụ thuộc**.

## 6. Các thành phần compiler có thể bổ sung

### 6.1. Bridge method do erasure hoặc covariant return

Generics bị xóa kiểu ở bytecode. Để duy trì dispatch/override đúng, compiler có thể tạo **bridge method**. `Method.isBridge()` chỉ ra cờ bridge, còn `Method.isSynthetic()` chỉ ra cờ synthetic.

Lưu thành `MethodMetadataDemo.java`:

```java
import java.lang.reflect.Method;

public class MethodMetadataDemo {
    static class Parent<T> {
        T value() { return null; }
    }

    static class Child extends Parent<String> {
        @Override
        String value() { return "ok"; }
    }

    public static void main(String[] args) {
        for (Method method : Child.class.getDeclaredMethods()) {
            System.out.printf(
                "%s: bridge=%s, synthetic=%s%n",
                method.getName(),
                method.isBridge(),
                method.isSynthetic()
            );
        }
    }
}
```

Biên dịch bằng `javac MethodMetadataDemo.java` và chạy `java MethodMetadataDemo`. Trên `javac` JDK 17, thông thường sẽ quan sát được hai method tên `value`: method trả `String` được khai báo ở source (`bridge=false, synthetic=false`) và bridge method trả `Object` (`bridge=true, synthetic=true`). **Thứ tự các dòng không được bảo đảm**, nên bài không cố định output theo thứ tự. Cờ metadata hữu ích cho công cụ quan sát; code nghiệp vụ không nên gọi method theo “vị trí thứ hai” trong mảng Reflection.

### 6.2. Field giữ outer instance và biến capture

Non-static inner class có mối liên hệ với enclosing instance. Constructor phản chiếu có thể có tham số instance bao ngoài; với non-static inner class được khai báo trong ngữ cảnh như vậy, Java Reflection mô tả tham số này ở đầu danh sách constructor parameter. Local/anonymous class có thể cần giữ biến được capture.

Một compiler có thể biểu diễn state trợ giúp bằng field với tên dạng `this$0` hoặc `val$...`. Nhưng **tên field, cách tối ưu và việc field có tồn tại hay không không phải schema**. Đừng dùng `getDeclaredFields()` rồi mặc định serialize toàn bộ field, hay xem `this$0` như property nghiệp vụ.

### 6.3. Constructor, parameter name và synthetic members

`getDeclaredConstructors()` phản chiếu constructor thực có trong class file; constructor của inner/local/anonymous class có thể có tham số không được viết tường minh trong source. Metadata tên tham số còn phụ thuộc cách biên dịch, ví dụ tùy chọn `javac -parameters`. Khi cần, kiểm tra `Parameter.isNamePresent()`, `isImplicit()` và `isSynthetic()` thay vì đoán tên `arg0` hay dùng độ dài signature làm dữ liệu bất biến.

`getDeclaredFields()`, `getDeclaredMethods()`, `getDeclaredConstructors()` không hứa thứ tự source. Các công cụ ánh xạ schema hoặc tạo tài liệu cần chọn rõ member nào thuộc hợp đồng, sau đó tự sắp xếp. Nếu lọc hết `isSynthetic()`/`isBridge()`, phải xác định rằng mục tiêu của mình là API source-level; một công cụ phân tích bytecode hoặc dispatch có thể cần giữ thành phần bridge.

### 6.4. Lambda và lớp triển khai ẩn

Lambda nên được nhìn như một cách tạo thực thể thực thi **functional interface**, không phải nguồn sinh class có tên bền vững. JVM/compiler có nhiều chiến lược triển khai, gồm cơ chế hidden class trong các JDK hiện đại. Không cố `Class.forName` bằng tên lấy từ `someLambda.getClass().getName()` để phục hồi chức năng trong lần chạy khác. Hãy lưu cấu hình/loại nghiệp vụ riêng và tái tạo đối tượng qua API đã thiết kế.

## 7. Thiết kế hợp đồng Reflection chủ đích

Nếu ứng dụng cần xác định class nào đảm nhận vai trò `parser`, đừng nhận diện bằng hậu tố `$Parser` hoặc số thứ tự anonymous class. Dùng interface + annotation runtime để công bố ý nghĩa.

Lưu thành `StableDiscoveryDemo.java`:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class StableDiscoveryDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Role {
        String value();
    }

    @Role("parser")
    public static class Parser implements Runnable {
        @Override
        public void run() { }
    }

    public static void main(String[] args) {
        Class<?> type = Parser.class;
        if (Runnable.class.isAssignableFrom(type)) {
            Role role = type.getAnnotation(Role.class);
            System.out.println(role == null ? "unknown" : role.value());
        }
    }
}
```

```bash
javac StableDiscoveryDemo.java
java StableDiscoveryDemo
```

```text
parser
```

`Runnable` thể hiện hợp đồng hành vi; `@Role("parser")` công bố vai trò có thể truy vấn bằng Reflection. Việc *tìm* tất cả class ứng viên trong classpath là vấn đề discovery/registry khác: `getDeclaredClasses()` không phải chức năng quét package/classpath.

Áp dụng vào hệ thống thực tế:

1. **Phân loại kiểu:** dùng `isMemberClass()`, `isLocalClass()`, `isAnonymousClass()`; dùng `getDeclaringClass()` và `getEnclosingClass()` theo ý nghĩa tương ứng.
2. **Kiểm tra cùng nest:** dùng `getNestHost()`/`isNestmateOf()`, không so tiền tố chuỗi tên.
3. **Lập tài liệu API:** công bố type/member được thiết kế làm API, phân biệt compiler member bằng metadata khi phù hợp, kiểm soát thứ tự hiển thị.
4. **Định danh qua nhiều phiên bản:** định nghĩa khóa ổn định trong schema/annotation/cấu hình; không dùng số compiler sinh, thứ tự mảng Reflection hoặc tên triển khai lambda.
5. **Khởi tạo qua Reflection:** dựa vào constructor và kiểu tham số thực được công bố, xử lý ngoại lệ; tránh truy cập field/private accessor nội bộ.
6. **Kiểm thử tương thích:** kiểm tra hành vi và public contract. Chỉ kiểm tra tên/số lượng synthetic member nếu đang viết công cụ phân tích dành riêng cho compiler/bytecode đó.

## 8. Các lỗi thiết kế thường gặp

| Lỗi | Vấn đề | Cách sửa |
| --- | --- | --- |
| Dùng `getName().contains("$")` làm điều kiện “là inner class” | `$` không đủ nhận diện loại class | Dùng `isMemberClass()` hoặc API phân loại khác |
| Lưu `Outer$1` để nhận diện callback | Số có thể đổi theo source/compiler | Lưu registry key hoặc metadata tường minh |
| Dùng `getDeclaredClasses()` để tìm local class | Method chỉ trả member type khai báo trực tiếp | Phân biệt source analysis với member reflection |
| Dùng `getNestMembers()` làm sơ đồ khai báo class | Quan hệ nest phục vụ JVM access, phạm vi khác | Dùng `getDeclaringClass()`/`getDeclaredClasses()` |
| Phụ thuộc vào `access$000` hoặc `this$0` | Compiler/target có thể thay đổi thành phần | Thiết kế API và schema ổn định |
| Serialize toàn bộ `getDeclaredFields()` | Ghi cả state nội bộ, outer ref hoặc field hỗ trợ | Chọn DTO/record/property được chỉ định |
| Giả định phương thức đầu trong `getDeclaredMethods()` là public API | Thứ tự không được bảo đảm, có thể có bridge | Lọc và sắp xếp theo hợp đồng |
| Cho rằng tên triển khai lambda load lại được ở tiến trình khác | Kiểu triển khai runtime không có danh tính bền vững | Dựa vào functional interface, factory hoặc cấu hình |
| Lọc synthetic members một cách tuyệt đối | Có thể bỏ qua bridge cần nghiên cứu | Lọc theo mục đích công cụ, không tùy tiện |

### Tình huống: mapper làm rò rỉ state nội bộ

Một mapper tự động lấy mọi field của non-static inner class bằng `getDeclaredFields()` rồi ghi thẳng thành JSON. Nếu bytecode chứa field giữ outer instance hoặc biến capture, mapper có thể vô tình đưa vào JSON state không thuộc domain, gây vòng tham chiếu hoặc tạo output không nhất quán giữa các bản build.

Giải pháp đúng là **định nghĩa schema dữ liệu**: DTO, record hoặc danh sách thuộc tính được chỉ định rõ bằng annotation/cấu hình. `isSynthetic()` chỉ là thông tin hỗ trợ, không thay thế hợp đồng serialize.

## 9. Quan sát bytecode đúng cách

Sau khi đã tạo `NestAccessDemo.java`, có thể tự khảo sát:

```bash
javac --release 17 NestAccessDemo.java
javap -p NestAccessDemo
javap -v NestAccessDemo
```

`javap -p` hiển thị các thành viên kể cả private; `javap -v` cung cấp thêm attributes như metadata nest khi class file có chúng. Thử đổi compiler hoặc target để quan sát sự khác biệt, nhưng đừng biến một bản output cụ thể của `javap` thành luật bất biến.

Khi đọc kết quả, hãy luôn tự hỏi đó là:

- **Quy tắc được đặc tả** (chẳng hạn binary name của member type, semantics của nest access);
- **Hiện tượng ở một bản build** (tên số local/anonymous, accessor, bridge, field capture, thứ tự reflection);
- Hay **hợp đồng chính thức của ứng dụng** (interface, annotation, API, schema được công bố).

## 10. Câu hỏi tự kiểm tra

**1. Vì sao `Outer.class.getDeclaredClasses()` không thấy local class trong `work()`?** Vì `getDeclaredClasses()` dành cho member type khai báo trực tiếp. Điều đó không có nghĩa compiler không biên dịch local class.

**2. `Outer$A` và `Outer$B` chắc chắn cùng nest?** Không suy ra được chỉ từ tên. Dùng `isNestmateOf()` khi đã có hai đối tượng `Class<?>`.

**3. `Class.forName("pkg.Outer$Inner")` có sai không?** Không, nếu đó thực sự là binary name của member type có tên và có thể được nạp. Cách này khác việc đoán tên anonymous class `Outer$1`.

**4. Có nên bỏ mọi method synthetic khỏi công cụ phân tích?** Tùy mục đích. Công cụ làm tài liệu source-level có thể ẩn chúng; công cụ phân tích dispatch/bytecode có thể cần bridge methods.

**5. Có nên dùng `getSimpleName()` của anonymous class để tạo ID?** Không. Nó rỗng, và ngay cả `getName()` cũng không cung cấp một ID ứng dụng ổn định.

## 11. Tổng kết kiến thức

- Reflection cho biết cấu trúc class file/runtime; không phải toàn bộ cấu trúc đó đều do ứng dụng sở hữu.
- Member binary name được quy định, nhưng số của local/anonymous class, field/ accessor compiler sinh và tên triển khai lambda không phải hợp đồng nghiệp vụ.
- Member declaration, enclosing context và nestmate access là ba khái niệm riêng.
- Để ứng dụng bền vững qua nhiều bản build, hãy dựa vào **API, interface, annotation, schema hoặc registry do mình công bố**.

### Tài liệu chính thức

- [Java SE 17 — `Class` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): đặc tả các phương thức phân loại, enclosing và nest.
- [JLS 17, §13.1 — Binary compatibility](https://docs.oracle.com/javase/specs/jls/se17/html/jls-13.html#jls-13.1): quy tắc tên nhị phân của member/local/anonymous class.
- [JVMS 17, §4.7.28–29](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html): class-file attributes `NestHost` và `NestMembers`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="nested-types-nestmates-009">Hidden class, isHidden và giới hạn khám phá bằng Class.forName</a>

<details>
<summary>Xem chi tiết</summary>

# Hidden class, `Class.isHidden()` và giới hạn khám phá bằng `Class.forName()`

## 1. Mục tiêu và vị trí trong chủ đề kiểu lồng, nest

Sau bài này, bạn có thể:

- Phân biệt **hidden class** với lớp có tên thông thường, lớp lồng (nested class), lớp cục bộ (local class) và lớp vô danh (anonymous class).
- Tạo hidden class từ bytecode bằng `MethodHandles.Lookup.defineHiddenClass(...)` và nhận lại đối tượng `Class<?>` của nó.
- Dùng `Class.isHidden()` để nhận diện đúng hidden class; giải thích vì sao `Class.forName(hidden.getName())` không thể tìm ra lớp này.
- Hiểu mối liên hệ giữa **nest**, **nestmate**, tùy chọn `NESTMATE` và giới hạn của `getNestMembers()` khi có hidden class.
- Biết cách thiết kế mã reflection/framework khi lớp được tạo động không thể được tra cứu lại từ chuỗi tên.

**Phạm vi:** Java 17 trở lên (các API hidden class xuất hiện từ Java 15). Bài này tập trung vào cơ chế nhận diện và khám phá lớp trong Reflection, không đi sâu vào tự sinh mã bytecode hay viết class loader.

## 2. Hidden class là gì?

Một **hidden class** là lớp/interface được JVM định nghĩa tại thời điểm chạy từ một mảng bytecode thông qua API như `MethodHandles.Lookup.defineHiddenClass(...)`. Hidden class có đối tượng `Class<?>` thực sự, có thể có constructor, method, field, instance và có thể được thao tác bằng Reflection khi mã gọi có quyền truy cập phù hợp.

Điểm khác biệt quyết định: hidden class **không có binary name hợp lệ để các lớp khác tham chiếu tượng trưng**. Vì vậy, các thao tác tìm lớp theo tên thông thường, bao gồm `Class.forName(...)` và `ClassLoader.loadClass(...)`, không thể khám phá lại hidden class.

“Hidden” **không** có nghĩa là bí mật hay bất khả xâm phạm: ai đang giữ tham chiếu `Class<?>`, một `MethodHandles.Lookup` phù hợp hoặc instance có thể tương tác với lớp tùy theo quyền truy cập. Nó cũng không có nghĩa “khai báo `private`”, “lớp vô danh” hay “lớp lồng”. Các đặc điểm đó là những khái niệm độc lập.

| Kiểu | Có tên để `Class.forName` tra cứu? | `Class.isHidden()` | Ghi chú |
| --- | --- | --- | --- |
| Lớp top-level `Example` | Có, nếu class loader tìm được | `false` | Lớp thông thường |
| Lớp lồng `Outer.Inner` | Có, bằng **binary name** như `Outer$Inner` | `false` | Dấu chấm trong tên nguồn Java không phải tên binary của member class |
| Lớp local hoặc anonymous do `javac` tạo | Có thể có tên nhị phân do compiler phát sinh, không ổn định để làm API | `false` | Không tự động trở thành hidden class |
| Lớp do `defineHiddenClass` tạo | **Không** | `true` | Phải giữ `Class<?>`/lookup/handle được trả về |

Ví dụ, `Outer.Inner.class.getName()` thường là `"Outer$Inner"` (nếu cùng default package). Với hidden class, `getName()` thường có dạng `"Outer$Template/0x..."`: phần hậu tố do JVM sinh ra và **không làm chuỗi đó trở thành binary name có thể tra cứu**.

## 3. Cơ chế tạo và các API cốt lõi

```java
MethodHandles.Lookup callerLookup = MethodHandles.lookup();

MethodHandles.Lookup hiddenLookup = callerLookup.defineHiddenClass(
    classFileBytes,
    true,
    MethodHandles.Lookup.ClassOption.NESTMATE
);

Class<?> hiddenType = hiddenLookup.lookupClass();
boolean hidden = hiddenType.isHidden();
```

Đoạn trên minh họa lời gọi API; biến `classFileBytes` phải chứa **bytecode của một class file hợp lệ**, đã được biên dịch hoặc sinh ra trước đó.

- `defineHiddenClass(byte[] bytes, boolean initialize, ClassOption... options)` định nghĩa một lớp mới từ nội dung class file và trả về `Lookup` với **lookup class chính là hidden class** mới tạo. Nó không đưa lớp vào cơ chế tra cứu tên của class loader.
- `initialize = true` yêu cầu JVM khởi tạo lớp vừa định nghĩa; `false` không yêu cầu khởi tạo ngay tại bước này. Khởi tạo sau đó vẫn có thể xảy ra theo quy tắc JVM bình thường.
- Lookup gọi hàm phải có **full-privilege access**; lớp trong bytecode phải ở **cùng runtime package** với lookup class. Không thể lấy bytecode của một package bất kỳ rồi dùng lookup bất kỳ để định nghĩa.
- `Class.isHidden()` trả về `true` khi và chỉ khi `Class<?>` biểu diễn hidden class; việc tên trông lạ, có `$`, hay khai báo `private` **không phải** tiêu chí thay thế.
- `Class.getCanonicalName()` của hidden class trả về `null`. `getName()` vẫn trả về một chuỗi nhận diện ở runtime, nhưng không phải binary name dùng cho `forName`.
- `Class.forName(String)` hoặc phiên bản có `ClassLoader` tìm lớp bằng binary name. Không có overload nào biến hidden class thành lớp có thể tìm lại bằng tên.

Hai tùy chọn hữu ích của `Lookup.ClassOption`:

| Tùy chọn | Ý nghĩa |
| --- | --- |
| `NESTMATE` | Đưa hidden class vào **cùng nest** với lookup class; cho phép quyền truy cập `private` giữa nestmates theo quy tắc JVM |
| `STRONG` | Gắn vòng đời hidden class mạnh với defining class loader, tương tự lớp thông thường |

Mặc định nếu không có `NESTMATE`, hidden class tạo thành nest riêng có chính nó là nest host. Nếu không có `STRONG`, JVM *có thể* dỡ hidden class ngay cả khi defining loader còn sống, miễn không còn tham chiếu giữ lớp lại. Việc GC/unloading diễn ra lúc nào không được bảo đảm.

## 4. Ví dụ hoàn chỉnh: cùng bytecode, một lớp có tên và một hidden class

Tạo file **`HiddenClassDemo.java`** với nội dung:

```java
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;

public class HiddenClassDemo {
    private static String secret() {
        return "nest-secret";
    }

    public static class Template {
        public String greeting() {
            return "Hello from hidden class";
        }

        public String readHostSecret() {
            return HiddenClassDemo.secret();
        }
    }

    public static void main(String[] args) throws Exception {
        byte[] bytecode;
        try (InputStream in =
                     Template.class.getResourceAsStream("HiddenClassDemo$Template.class")) {
            if (in == null) {
                throw new IllegalStateException("Missing compiled Template.class");
            }
            bytecode = in.readAllBytes();
        }

        MethodHandles.Lookup hiddenLookup = MethodHandles.lookup().defineHiddenClass(
                bytecode, true, MethodHandles.Lookup.ClassOption.NESTMATE);
        Class<?> hidden = hiddenLookup.lookupClass();
        Object obj = hidden.getDeclaredConstructor().newInstance();

        System.out.println("Template hidden: " + Template.class.isHidden());
        System.out.println("Dynamic hidden: " + hidden.isHidden());
        System.out.println("Nestmate of host: "
                + hidden.isNestmateOf(HiddenClassDemo.class));
        System.out.println("Nest host is outer: "
                + (hidden.getNestHost() == HiddenClassDemo.class));
        System.out.println("In getNestMembers: "
                + Arrays.asList(HiddenClassDemo.class.getNestMembers()).contains(hidden));
        System.out.println("Has canonical name: " + (hidden.getCanonicalName() != null));
        System.out.println("Named template can be found: "
                + (Class.forName(Template.class.getName()) == Template.class));

        try {
            Class.forName(hidden.getName());
            System.out.println("Hidden class found unexpectedly");
        } catch (ClassNotFoundException expected) {
            System.out.println("Hidden class via Class.forName: NOT FOUND");
        }

        System.out.println("Method call: "
                + hidden.getDeclaredMethod("greeting").invoke(obj));
        System.out.println("Private host access: "
                + hidden.getDeclaredMethod("readHostSecret").invoke(obj));
    }
}
```

Trong thư mục chứa file, chạy bằng **JDK 17+**:

```bash
javac --release 17 HiddenClassDemo.java
java HiddenClassDemo
```

Kết quả:

```text
Template hidden: false
Dynamic hidden: true
Nestmate of host: true
Nest host is outer: true
In getNestMembers: false
Has canonical name: false
Named template can be found: true
Hidden class via Class.forName: NOT FOUND
Method call: Hello from hidden class
Private host access: nest-secret
```

**Giải thích từng hiện tượng:**

1. `javac` sinh `HiddenClassDemo.class` và `HiddenClassDemo$Template.class`. `Template` là một **static nested class thông thường**: `Template.class.isHidden() == false`; `Class.forName(Template.class.getName())` tìm đúng lớp có tên.
2. `getResourceAsStream(...)` đọc **bytecode** của `Template`, không chỉ chuỗi tên. `defineHiddenClass` dùng bytecode đó để JVM định nghĩa **một `Class<?>` khác**, có danh tính khác với `Template.class`.
3. `hidden.isHidden()` trả về `true` dù bytecode bắt nguồn từ class file vốn có tên. Danh tính của lớp được tạo mới do API định nghĩa hidden class quyết định.
4. `NESTMATE` đưa hidden class vào nest của `HiddenClassDemo`. Do đó `hidden.isNestmateOf(HiddenClassDemo.class)` và phép so `hidden.getNestHost() == HiddenClassDemo.class` đều `true`.
5. Hidden class **không xuất hiện** trong danh sách `HiddenClassDemo.class.getNestMembers()`. Hàm đó duyệt nest members ghi trong metadata `NestMembers` của class file; các hidden nestmate thêm ở runtime không nằm trong danh sách liệt kê này.
6. `hidden.getCanonicalName() == null`. `Class.forName(hidden.getName())` thất bại với `ClassNotFoundException`; đây là kết quả **mong đợi**, không phải lỗi cấu hình classpath.
7. Vẫn có thể tạo instance và gọi method qua `hidden.getDeclaredConstructor()`, `getDeclaredMethod(...)` vì chương trình **đang giữ trực tiếp** `Class<?> hidden`. Method `readHostSecret()` đọc thành viên `private` của host thành công nhờ quan hệ nestmate.

**Đã kiểm chứng:** mã trên biên dịch ở chế độ `--release 17` và chạy ra đúng các dòng kết quả trên bằng JDK 21. Chuỗi tên cụ thể từ `hidden.getName()` được cố ý không in ra vì hậu tố do JVM cấp có thể thay đổi mỗi lần chạy.

## 5. Phân biệt “biết lớp tồn tại” và “tìm lớp theo tên”

Với lớp thông thường:

```java
String binaryName = HiddenClassDemo.Template.class.getName();
Class<?> named = Class.forName(binaryName);
System.out.println(named == HiddenClassDemo.Template.class); // true
```

Với hidden class:

```java
Class<?> hidden = hiddenLookup.lookupClass(); // hiddenLookup từ phần trước
String runtimeName = hidden.getName();
Class<?> resolved = Class.forName(runtimeName); // ném ClassNotFoundException
```

Đoạn thứ hai **chỉ là minh họa sự thất bại**, không phải chương trình hoàn chỉnh để chạy độc lập. Không nên viết `Class.forName(...)` ngoài `try/catch` như một chiến lược khôi phục lớp hidden.

Quy tắc cần ghi nhớ:

- **Có tham chiếu `Class<?>`:** có thể dùng Reflection trên đối tượng đó, với kiểm tra quyền truy cập bình thường.
- **Chỉ có chuỗi `getName()`:** không thể truy xuất lại hidden class qua `Class.forName`, kể cả khi dùng đúng class loader.
- **Có `Lookup` trả về:** có thể thao tác với lớp vừa tạo qua `lookupClass()` và dùng method handle tương ứng, nếu quyền cho phép.
- **Chỉ quét `getDeclaredClasses()` hoặc `getNestMembers()` của host:** không thể suy luận rằng đã tìm đủ hidden classes được định nghĩa động. `getDeclaredClasses()` nói về member classes ghi trong class metadata; `getNestMembers()` bỏ qua hidden nestmates được thêm khi chạy.

Một hệ thống plugin/serializer/reflection scanner chỉ lưu `class.getName()` rồi gọi `Class.forName(name)` về sau sẽ không tương thích với hidden class. Nếu thực sự cần hỗ trợ loại này, hãy giữ tham chiếu runtime thích hợp trong vòng đời sử dụng (ví dụ `Class<?>`, `Lookup`, factory hoặc `MethodHandle`), thay vì thiết kế hợp đồng chỉ dựa vào tên.

## 6. Hidden class và nestmate: quan hệ chính xác

**Nest** là nhóm class/interface trong cùng runtime package có thể truy cập thành viên `private` của nhau. Với nested classes biên dịch thông thường, compiler ghi quan hệ qua thuộc tính `NestHost`/`NestMembers`. Với hidden class, `NESTMATE` thiết lập quan hệ động thông qua JVM mà **không sửa metadata `NestMembers` của host**.

Ba câu hỏi sau có đáp án khác nhau:

| Câu hỏi | API | Kết quả với hidden class dùng `NESTMATE` |
| --- | --- | --- |
| Đây có phải hidden class? | `hidden.isHidden()` | `true` |
| Đây có phải nestmate của host? | `hidden.isNestmateOf(Host.class)` | `true` nếu cùng nest |
| Host liệt kê được lớp này? | `Arrays.asList(Host.class.getNestMembers()).contains(hidden)` | `false` |

Không nên nhầm **quan hệ thành viên thực tế** (câu 2) với **khả năng liệt kê** (câu 3). `getNestMembers()` trả về những thành viên nest có tên được ghi trong class file, **không bao gồm hidden classes được thêm bằng `defineHiddenClass`**.

Nếu bỏ `NESTMATE` khỏi ví dụ, hidden class mặc định ở nest riêng. Đoạn bytecode gọi `HiddenClassDemo.secret()` (method `private`) không còn được cấp quyền nestmate, nên thao tác gọi method đó có thể gặp lỗi liên kết/truy cập như `IllegalAccessError`. Lưu ý: đây là lỗi quyền truy cập bytecode khi thực thi, không phải `ClassNotFoundException` của `forName`.

## 7. Ứng dụng thực tế và giới hạn

Hidden classes thích hợp cho hệ thống sinh mã lúc chạy, ví dụ lớp hỗ trợ triển khai lambda hoặc lớp nội bộ do framework sinh ra không cần được tham chiếu bằng tên nhị phân từ mã ứng dụng. Chúng giúp giảm nhu cầu giữ một namespace tên lớp có thể khám phá, đồng thời có thể hỗ trợ dỡ lớp linh hoạt hơn khi không dùng `STRONG`.

Khi thiết kế thư viện dùng Reflection:

1. **Chọn cơ chế khám phá phù hợp.** Với lớp công khai do người dùng cấu hình theo tên, dùng lớp có binary name ổn định. Với lớp nội bộ sinh động, trả lại `Class<?>`/factory/handle ngay từ nơi tạo.
2. **Không dùng tên hiển thị như khóa khôi phục.** `hidden.getName()` có thể hữu ích cho logging/chẩn đoán, nhưng không phải định danh để `Class.forName` tải lại.
3. **Không coi hidden là biện pháp bảo mật.** Kiểm soát quyền bằng các cơ chế access/module/security thích hợp, không dựa trên việc lớp “khó tìm”.
4. **Cân nhắc vòng đời.** Giữ `Class`, instance hay handle có thể giữ hidden class sống; `STRONG` liên kết mạnh với loader. Không xây tính đúng đắn dựa trên thời điểm GC tự động dỡ lớp.
5. **Xác định giới hạn tooling.** Những công cụ bắt buộc có binary name hoặc trông chờ enumerate toàn bộ nest có thể không bao quát hidden classes; agent/JVMTI có các hạn chế riêng đối với biến đổi hidden class.

Một hidden class vẫn có thể kế thừa lớp thường hoặc triển khai interface phù hợp với bytecode; “hidden” không đồng nghĩa “không dùng được đa hình”. Tuy nhiên, việc mã khác viết một tham chiếu biểu tượng đến chính tên hidden class là điều không thể.

## 8. Những nhầm lẫn và lỗi thường gặp

| Nhầm lẫn / triệu chứng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| Thấy dấu `$` trong tên nên cho rằng lớp hidden | Lớp lồng thường có binary name chứa `$` | Kiểm tra `Class.isHidden()` |
| `Class.forName(hidden.getName())` báo không tìm thấy | Hidden class không có binary name để class loader khám phá | Giữ `Class<?>`/`Lookup`/factory khi tạo |
| `getNestMembers()` không có hidden class dù `isNestmateOf()` đúng | Hidden nestmates không được ghi trong `NestMembers` của host | Dùng `isNestmateOf()` để kiểm tra quan hệ; lưu danh sách lớp đã định nghĩa nếu cần duyệt |
| `defineHiddenClass` ném `IllegalAccessException` | Lookup không có quyền full privilege phù hợp | Gọi từ ngữ cảnh có quyền thực sự; không giả định mọi lookup đều định nghĩa được |
| `defineHiddenClass` thất bại vì package của bytecode | Bytecode phải cùng runtime package với lookup class | Tạo/đọc bytecode trong đúng package và dùng lookup hợp lệ |
| `ClassFormatError` hoặc lỗi xác minh bytecode | Dữ liệu class file không hợp lệ/không phù hợp | Biên dịch hoặc sinh bytecode chuẩn; kiểm tra class file trước khi định nghĩa |
| Gọi member `private` của host thất bại | Không có quan hệ nestmate/quyền cần thiết | Chỉ dùng `NESTMATE` nếu thực sự cần và đáp ứng điều kiện API |
| Tưởng `initialize=false` nghĩa là không bao giờ khởi tạo | Nó chỉ không yêu cầu khởi tạo tại thời điểm định nghĩa | Hiểu quy tắc khởi tạo JVM khi dùng lớp về sau |

## 9. Bài tập tự kiểm tra

**Bài 1 — Nhận diện:** Với `Outer.Inner.class` (static member class thông thường), so sánh `isHidden()`, `getName()` và `getCanonicalName()` với hidden class ở ví dụ. Giải thích tại sao tên của cả hai có thể chứa `$` nhưng chỉ một lớp là hidden.

**Bài 2 — Danh tính:** Gọi `defineHiddenClass(bytecode, true, NESTMATE)` **hai lần** với cùng bytecode. So sánh `Class<?>` của hai lần bằng `==` và thử gọi `Class.forName` bằng tên của từng hidden class. Dự đoán: hai lần tạo ra hai đối tượng `Class<?>` khác nhau, cả hai đều `isHidden()==true` và không thể tìm lại bằng `forName`.

**Bài 3 — Nest:** Bỏ `NESTMATE` khi định nghĩa lớp, quan sát `isNestmateOf(HiddenClassDemo.class)` và thử gọi `readHostSecret()`. Tự giải thích mối liên hệ giữa quyền truy cập `private` và quan hệ nest; phân biệt lỗi của bước này với lỗi tìm lớp bằng tên.

**Câu hỏi phỏng vấn:** “Nếu `Class.forName(hidden.getName())` không tìm được hidden class, tại sao `hidden.getDeclaredMethods()` vẫn hoạt động?” — Vì `forName` **tìm một `Class` từ binary name**, còn `getDeclaredMethods()` **truy vấn metadata của một đối tượng `Class` đã có sẵn**. Hai thao tác sử dụng đầu vào và cơ chế khác nhau.

## 10. Tài liệu gốc

- [JEP 371 — Hidden Classes](https://openjdk.org/jeps/371): mục tiêu thiết kế, khả năng khám phá và vòng đời của hidden classes.
- [Java 17 — `Class`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `isHidden()`, `getName()`, `getCanonicalName()`, `getNestHost()`, `isNestmateOf()`, `getNestMembers()`.
- [Java 17 — `MethodHandles.Lookup`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandles.Lookup.html): `defineHiddenClass(...)`, quyền lookup, quy tắc bytecode/runtime package.
- [Java 17 — `Lookup.ClassOption`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandles.Lookup.ClassOption.html): `NESTMATE` và `STRONG`.


</details>

- [Quay lại đầu trang](#back-to-top)
