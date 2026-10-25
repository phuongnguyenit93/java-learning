<a id="back-to-top"></a>

# Tra cứu thành viên

## Menu
- [Member, Field, Method, Constructor và Executable](#member-lookup-001)
- [getField, getMethod, getConstructor](#member-lookup-002)
- [getDeclaredField, getDeclaredMethod, getDeclaredConstructor](#member-lookup-003)
- [Public member kế thừa khác member được khai báo tại chính lớp](#member-lookup-004)
- [Tra cứu một thành viên và liệt kê nhiều thành viên](#member-lookup-005)
- [Tên, kiểu tham số và điều kiện khớp chính xác khi tra cứu](#member-lookup-006)
- [Vì sao getMethod không tự giải overload như trình biên dịch](#member-lookup-007)
- [Primitive parameter type khác wrapper parameter type](#member-lookup-008)
- [Tra cứu theo kiểu sau type erasure](#member-lookup-009)
- [Thứ tự thành viên được trả về không phải thứ tự khai báo được bảo đảm](#member-lookup-010)
- [Xây dựng chính sách tìm kiếm xuyên phân cấp một cách tường minh](#member-lookup-011)

## <a id="member-lookup-001">Member, Field, Method, Constructor và Executable</a>

<details>
<summary>Xem chi tiết</summary>

# Tra cứu thành viên bằng Java Reflection: `Member`, `Field`, `Method`, `Constructor` và `Executable`

## 1. Mục tiêu và bức tranh tổng quan

Java Reflection cho phép chương trình **khám phá cấu trúc lớp tại thời điểm chạy**: lớp khai báo những trường nào, phương thức nào, constructor nào, kiểu tham số và kiểu trả về là gì, thành viên có `public` hay `private`, có thể gọi hoặc đọc giá trị ra sao. Chủ đề này tập trung vào **tra cứu thành viên** thông qua `java.lang.Class` và các kiểu trong `java.lang.reflect`.

Sơ đồ quan hệ API:

```text
java.lang.reflect.Member (interface)
├── Field implements Member
└── Executable (abstract class) implements Member
    ├── Method extends Executable
    └── Constructor<T> extends Executable
```

- `Member` diễn tả một **thành viên được khai báo bởi lớp/giao diện** ở mức thông tin chung: tên, lớp khai báo, modifiers, trạng thái synthetic.
- `Field` đại diện cho trường dữ liệu, bao gồm biến instance và biến `static`.
- `Method` đại diện cho phương thức, bao gồm phương thức instance, `static`, abstract, default của interface.
- `Constructor<T>` đại diện cho constructor của lớp, dùng để tạo instance.
- `Executable` gom API chung cho hai thứ **có danh sách tham số và có thể được thực thi** là `Method` và `Constructor`.

Điểm cần phân biệt: đối tượng `Method` hoặc `Field` là **metadata và cổng thao tác phản chiếu**, không phải giá trị của một trường hay lời gọi phương thức được thực thi sẵn. Bạn cần một đối tượng đích phù hợp cho thành viên instance.

## 2. Bảng đối chiếu nhanh các kiểu

| Kiểu | Cách lấy thường gặp | Thông tin đặc trưng | Thao tác chính |
|---|---|---|---|
| `Member` | Kiểu cha/interface của `Field`, `Method`, `Constructor` | `getName()`, `getDeclaringClass()`, `getModifiers()`, `isSynthetic()` | Chuẩn hóa thao tác tra cứu metadata |
| `Field` | `getField` / `getDeclaredField` | `getType()`, `getGenericType()`, `isEnumConstant()` | `get(object)`, `set(object, value)`, getter/setter kiểu nguyên thủy |
| `Method` | `getMethod` / `getDeclaredMethod` | `getReturnType()`, `getGenericReturnType()`, `isBridge()`, `isDefault()` | `invoke(target, args...)` |
| `Constructor<T>` | `getConstructor` / `getDeclaredConstructor` | `getDeclaringClass()`, danh sách tham số | `newInstance(args...)` |
| `Executable` | Ép/nhận từ `Method` hoặc `Constructor` | `getParameterTypes()`, `getParameters()`, `getExceptionTypes()`, `isVarArgs()` | Phân tích chữ ký và tham số chung |

`Method` và `Constructor` kế thừa `Executable`, vì vậy có thể xử lý cùng một luồng để liệt kê chữ ký. `Field` **không** phải `Executable` vì trường dữ liệu không có danh sách tham số. `Class<?>` là điểm khởi đầu: `SomeType.class`, `obj.getClass()` hoặc `Class.forName("package.Type")` khi cần nạp theo tên.

## 3. Chọn họ API để tìm metadata

Khi bắt đầu từ một `Class<?>`, hãy phân biệt **tra cứu một thành viên** theo tên/chữ ký với **liệt kê nhiều thành viên** để kiểm kê hoặc xây công cụ.

| Thành viên | Public lookup | Declared lookup |
|---|---|---|
| Field | `getField(name)` / `getFields()` | `getDeclaredField(name)` / `getDeclaredFields()` |
| Method | `getMethod(name, types...)` / `getMethods()` | `getDeclaredMethod(name, types...)` / `getDeclaredMethods()` |
| Constructor | `getConstructor(types...)` / `getConstructors()` | `getDeclaredConstructor(types...)` / `getDeclaredConstructors()` |

Với field và method, họ public lookup có thể tìm thành viên công khai được kế thừa; họ declared chỉ xem khai báo **ngay tại kiểu đang xét** nhưng bao gồm mọi mức truy cập. Constructor không kế thừa, nên cả hai họ chỉ tìm trên lớp đích. Method và constructor đòi dãy kiểu tham số khai báo chính xác; không áp dụng boxing hoặc giải overload khi **lookup**. Không dựa vào thứ tự mảng của các API liệt kê.

Các bài kế tiếp minh họa chi tiết phạm vi public/declared, chữ ký chính xác và chính sách duyệt phân cấp; phần còn lại của bài này tập trung vào các **đối tượng metadata** mà những API ấy trả về.

## 4. `Member`: thông tin chung về một thành viên

`Member` cung cấp:

- `getName()`: tên của thành viên; đối với `Constructor`, tên thường là tên đầy đủ của lớp khai báo, không phải ký hiệu `<init>`.
- `getDeclaringClass()`: **lớp hoặc interface thực sự khai báo thành viên**, có thể khác lớp dùng để tra cứu nó. Ví dụ tìm phương thức thừa kế trên `Child.class` nhưng `getDeclaringClass()` trả `Parent.class`.
- `getModifiers()`: một `int` chứa các bit biểu diễn modifiers. Dùng `java.lang.reflect.Modifier` như `Modifier.isPublic(flags)`, `isPrivate`, `isStatic`, `isFinal`, `isAbstract` thay vì so sánh trực tiếp các giá trị bit.
- `isSynthetic()`: trình biên dịch/JVM tạo thành viên phục vụ triển khai thay vì thành viên viết trực tiếp theo nghĩa thông thường trong mã nguồn.

```java
Member member = String.class.getMethod("length");
System.out.println(member.getName());                    // length
System.out.println(member.getDeclaringClass());          // class java.lang.String
System.out.println(Modifier.isPublic(member.getModifiers())); // true
```

Đoạn trên giả định import `java.lang.reflect.Member` và `java.lang.reflect.Modifier`, và chạy trong ngữ cảnh chấp nhận `NoSuchMethodException`.

Đừng nhầm `getDeclaringClass()` với `getClass()`. Ví dụ `method.getClass()` trả kiểu runtime của **đối tượng metadata**, còn `method.getDeclaringClass()` trả lớp chứa phương thức đó.

## 5. `Field`: tra cứu, đọc và ghi trường

`Field` cho biết:

- `getType()`: kiểu trường ở dạng `Class<?>`, ví dụ `int.class` hoặc `List.class`.
- `getGenericType()`: kiểu tổng quát ở dạng `Type` khi signature generic có sẵn, ví dụ `List<String>` được phản ánh qua `ParameterizedType`; cơ chế này **không** khôi phục kiểu tham số đã bị xóa của mọi đối tượng tại runtime.
- `getAnnotations()` / `getDeclaredAnnotations()`: metadata annotation có retention tại runtime.
- `isEnumConstant()`: trường đại diện một hằng của enum.
- `get(obj)`, `set(obj, value)`: đọc và ghi giá trị trường. Có các biến thể `getInt`, `setInt`, `getBoolean`... cho primitive.

Với **instance field**, truyền đối tượng chứa trường; với **static field**, truyền `null` trong `get(null)` hoặc `set(null, ...)`. Java kiểm tra quyền truy cập khi thực hiện thao tác.

```java
Field name = Student.class.getDeclaredField("name");
boolean allowed = name.trySetAccessible();
if (!allowed) {
    throw new IllegalStateException("Không được phép mở quyền truy cập");
}
Object current = name.get(student);
name.set(student, "An");
```

Đây là đoạn minh họa cần có lớp `Student` và biến `student` phù hợp. `trySetAccessible()` có thể trả `false` khi access không được phép theo cơ chế module; `setAccessible(true)` có thể ném `InaccessibleObjectException` trong tình huống tương đương. Nhiều trường hợp chỉ cần public API/getter/setter và không cần mở quyền.

Đối với trường `final`, đặc biệt `static final`, đừng xem Reflection là cơ chế cập nhật cấu hình runtime: việc sửa có hạn chế và liên quan tối ưu hóa/JVM, không bảo đảm hành vi mong muốn.

## 6. `Method` và `Executable`: phân tích chữ ký và thực thi

### 6.1. Thông tin của `Method`

`Method` kế thừa metadata chung rồi bổ sung:

- `getReturnType()`: kiểu trả về đã erasure ở dạng `Class<?>`; `void` được biểu diễn bởi `void.class`.
- `getGenericReturnType()`: biểu diễn tổng quát của kiểu trả về; nên kiểm tra `instanceof ParameterizedType` trước khi ép kiểu.
- `isBridge()`: có phải bridge method do compiler tạo để duy trì đa hình sau generic erasure hoặc covariant return không.
- `isDefault()`: có phải phương thức `default` của interface không.
- `getDefaultValue()`: giá trị mặc định của phần tử annotation, nếu có; không phải “giá trị trả về mặc định” của mọi method.
- `invoke(receiver, args...)`: gọi phản chiếu; với phương thức `static` truyền `null` làm receiver, với instance truyền đúng đối tượng.

`Method.invoke` trả `Object`: primitive return được boxing; phương thức trả `void` cho `null`. Nếu code bên trong phương thức bị lỗi, ngoại lệ gốc được **bọc** trong `InvocationTargetException`; xem `getCause()` để biết nguyên nhân thực.

### 6.2. API chung của `Executable`

Áp dụng cho cả `Method` và `Constructor`:

| API | Ý nghĩa |
|---|---|
| `getParameterCount()` | Số lượng tham số theo chữ ký phản chiếu |
| `getParameterTypes()` | Mảng `Class<?>[]` của kiểu tham số (sau erasure) |
| `getGenericParameterTypes()` | Mảng `Type[]` của kiểu tham số generic |
| `getParameters()` | Mảng `Parameter[]` để đọc tên, annotation, modifiers và metadata tham số |
| `getExceptionTypes()` | Mảng `Class<?>[]` các exception khai báo trong `throws` |
| `getGenericExceptionTypes()` | Kiểu generic của exception khai báo |
| `isVarArgs()` | Có khai báo tham số cuối ở dạng varargs (`...`) hay không |
| `getAnnotatedParameterTypes()` | Thông tin type-use annotations của các kiểu tham số |
| `getAnnotatedExceptionTypes()` | Type-use annotations của các kiểu exception khai báo |

`Parameter.getName()` **không mặc định bảo toàn tên viết trong source**. Khi biên dịch thông thường không có `-parameters`, tên có thể là `arg0`, `arg1`; dùng `javac -parameters` khi thật sự cần tên tham số runtime và nhớ kiểm tra `Parameter.isNamePresent()`. Tên tham số không phải thành phần để `getMethod` phân biệt overload.

Ví dụ tiện ích tái sử dụng:

```java
static void printSignature(Executable executable) {
    System.out.println("Tên: " + executable.getName());
    for (Class<?> type : executable.getParameterTypes()) {
        System.out.println("  Tham số: " + type.getTypeName());
    }
}
```

Hàm minh họa này cần import `java.lang.reflect.Executable`; truyền vào một `Method` hoặc `Constructor<?>` đều hợp lệ.

### 6.3. Varargs và mảng tham số

`String... args` được biểu diễn thành kiểu mảng `String[]` trong metadata; `isVarArgs()` là chỉ dấu cho cú pháp varargs ban đầu. Không giả định rằng `Method.invoke` luôn tự áp dụng cùng quy tắc varargs như lời gọi Java được compiler kiểm tra. Hãy cung cấp số đối số và kiểu theo API phản chiếu; với varargs, khi cần hãy đóng gói tham số cuối thành mảng đúng kiểu và tránh nhầm giữa mảng là *một argument* và mảng chứa *toàn bộ arguments*.

```java
Method join = Example.class.getMethod("join", String[].class);
Object result = join.invoke(example, (Object) new String[] {"A", "B"});
```

Ép mảng thành `Object` trong ví dụ minh họa là để truyền nó dưới dạng **một tham số** cho `invoke`; `Example` và `example` là đối tượng trong ngữ cảnh giả định.

## 7. `Constructor<T>`: khám phá và tạo instance

Constructor không có kiểu trả về và không được kế thừa. `Class<T>.getDeclaredConstructor(Class<?>... parameterTypes)` tìm đúng constructor do lớp đó khai báo, bất kể mức truy cập; `getConstructor(...)` chỉ tìm constructor `public`.

```java
Constructor<Student> ctor = Student.class.getDeclaredConstructor(String.class);
Student student = ctor.newInstance("Lan");
```

`newInstance` thực thi constructor thật và có thể bọc exception do constructor ném trong `InvocationTargetException`. Nếu constructor `private` nằm ngoài phạm vi được phép truy cập, cần xử lý quyền truy cập giống `Field`/`Method`; một module khác có thể từ chối mở quyền ngay cả khi gọi `setAccessible(true)`. Không thể dùng constructor của lớp trừu tượng để tạo instance; enum cũng không được tạo tùy tiện qua `Constructor.newInstance`.

Đối với chương trình mới, thường ưu tiên lời gọi constructor trực tiếp, factory method hoặc dependency injection; Reflection phù hợp khi lựa chọn kiểu/constructor chỉ được biết tại runtime, chẳng hạn serializer hoặc framework test.

## 8. Ví dụ Java 17+ đầy đủ: tra cứu, kế thừa, gọi method và đọc field

Lưu đoạn sau thành **`ReflectionMembersDemo.java`**. Chương trình minh họa đầy đủ: `Class`, `Member`, `Field`, `Method`, `Constructor` và `Executable`; đồng thời kiểm chứng khác biệt giữa lớp dùng để tra cứu và lớp khai báo method kế thừa.

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class ReflectionMembersDemo {
    interface Greeter {
        default String salutation() {
            return "Xin chào";
        }
    }

    static class Parent {
        public String inherited() {
            return "parent";
        }
    }

    static class Student extends Parent implements Greeter {
        private String name;

        public Student(String name) {
            this.name = name;
        }

        public String greet(String prefix) {
            return prefix + ", " + name;
        }

        public static int twice(int number) {
            return number * 2;
        }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Class<Student> type = Student.class;

        Constructor<Student> ctor = type.getDeclaredConstructor(String.class);
        Method inherited = type.getMethod("inherited");
        Method greet = type.getDeclaredMethod("greet", String.class);
        Method twice = type.getDeclaredMethod("twice", int.class);
        Field name = type.getDeclaredField("name");

        System.out.println("Constructor thuộc: "
                + ctor.getDeclaringClass().getSimpleName());
        System.out.println("Tên Field: " + name.getName());
        System.out.println("Method thuộc: "
                + inherited.getDeclaringClass().getSimpleName());

        Student student = ctor.newInstance("Lan");
        System.out.println("Gọi Method: " + greet.invoke(student, "Chào"));
        System.out.println("Method static: " + twice.invoke(null, 21));

        if (!name.trySetAccessible()) {
            throw new IllegalStateException("Không thể truy cập field name");
        }
        System.out.println("Field private: " + name.get(student));

        Executable executable = greet;
        System.out.println("Khả năng public: "
                + Modifier.isPublic(greet.getModifiers()));
        System.out.println("Số tham số: " + executable.getParameterCount());
        System.out.println("Kế thừa interface: "
                + type.getMethod("salutation").invoke(student));
    }
}
```

Biên dịch và chạy ở thư mục chứa file:

```bash
javac ReflectionMembersDemo.java
java ReflectionMembersDemo
```

Kết quả chuẩn:

```text
Constructor thuộc: Student
Tên Field: name
Method thuộc: Parent
Gọi Method: Chào, Lan
Method static: 42
Field private: Lan
Khả năng public: true
Số tham số: 1
Kế thừa interface: Xin chào
```

Giải thích các dòng đáng chú ý:

1. `getDeclaredConstructor(String.class)` tìm constructor được khai báo trong `Student`, rồi `newInstance("Lan")` tạo đối tượng.
2. `getMethod("inherited")` tìm được phương thức public từ `Parent`. Metadata trả `getDeclaringClass() == Parent.class` dù việc tra cứu bắt đầu tại `Student.class`.
3. `getDeclaredMethod("greet", String.class)` tìm phương thức ngay trong `Student`; `Method.invoke` gọi nó trên `student` với đối số `"Chào"`.
4. Phương thức `static` `twice` được gọi với receiver `null`, nên trả 42.
5. `name` là trường `private`, nên dùng `getDeclaredField`; `trySetAccessible()` kiểm tra mở quyền trước `get`. Ví dụ khai báo lớp lồng nhau trong cùng một chương trình, tránh phụ thuộc vào module ngoài.
6. Biến `Executable executable = greet` cho thấy method chia sẻ API chữ ký qua `Executable`. `Modifier.isPublic` xử lý bit modifier đúng cách.
7. Phương thức `default` của `Greeter` có thể tìm qua `getMethod` trên `Student.class`.

## 9. Quyền truy cập, module và giới hạn thực tế

Reflection **không đồng nghĩa quyền truy cập vô hạn**. Ba câu hỏi phải tách biệt:

1. **Có tìm thấy metadata không?** `getDeclaredField` có thể tìm được field `private`.
2. **Có được phép truy cập/thực thi không?** `get`, `set`, `invoke` hoặc `newInstance` vẫn kiểm tra quyền.
3. **Có được phép *mở rộng* quyền Reflection không?** `trySetAccessible()`/`setAccessible(true)` bị ràng buộc bởi Java module system (JPMS) và khả năng truy cập giữa module/package.

Từ Java 9+, module có strong encapsulation. Với module có tên, việc truy cập sâu vào thành viên non-public từ module khác thường cần package được `opens` đúng đích. Ví dụ trong `module-info.java` của module sở hữu lớp:

```java
module school.model {
    opens school.model.internal to school.tooling;
}
```

`opens` phục vụ deep reflection vào package; `exports` phục vụ truy cập thông thường vào kiểu public và **không thay thế** `opens` cho mọi tình huống Reflection private. Lựa chọn khả dụng tùy module và quyền sở hữu package. Cân nhắc API public trước khi mở rộng quyền reflection.

Các ngoại lệ và lỗi thường gặp:

| Tình huống | Ngoại lệ / kết quả |
|---|---|
| Tra cứu sai tên hoặc sai signature method | `NoSuchMethodException` |
| Tra cứu tên field không tồn tại | `NoSuchFieldException` |
| Thao tác thành viên không có quyền | `IllegalAccessException`, hoặc lỗi truy cập tương ứng |
| Cố `setAccessible(true)` khi package không thể mở | `InaccessibleObjectException` (runtime) |
| Receiver không phù hợp hoặc đối số không khớp | `IllegalArgumentException` |
| Method/constructor bên trong ném ngoại lệ | `InvocationTargetException`; truy nguyên bằng `getCause()` |
| Gọi phương thức instance với receiver `null` | `NullPointerException` |
| Tạo instance qua constructor của kiểu trừu tượng | `InstantiationException` |
| `trySetAccessible()` không mở được quyền | Trả `false`; có thể có ngoại lệ khác theo API và bối cảnh |

`ReflectiveOperationException` là lớp cha hữu ích cho nhiều checked exceptions Reflection như `NoSuchMethodException`, `NoSuchFieldException`, `IllegalAccessException`, `InstantiationException` và `InvocationTargetException`, nhưng **không** bao quát mọi lỗi runtime ở bảng trên.

Với lỗi từ method đích, bắt ngoại lệ phù hợp và xem nguyên nhân:

```java
try {
    method.invoke(target);
} catch (java.lang.reflect.InvocationTargetException ex) {
    Throwable original = ex.getCause();
    System.err.println("Lỗi bên trong method: " + original);
}
```

Đây là ví dụ xử lý lỗi trong ngữ cảnh đã có `method` và `target`, không phải mã độc lập. Trong sản phẩm thực tế, chọn chiến lược log/propagate phù hợp và tránh làm mất stack trace.

## 10. Những bẫy cần nhớ khi viết công cụ Reflection

- **Nhầm declared với inherited:** metadata declared chỉ phản ánh kiểu được chọn; cần quyết định có xét lớp cha, interface hay không.
- **Nhầm public với accessible:** thành viên `public` không tự động có thể gọi từ mọi module/ngữ cảnh; lớp khai báo và chính sách module cũng quan trọng.
- **Sai signature vì autoboxing/widening:** `getDeclaredMethod("f", int.class)` không thể tùy tiện đổi thành `Integer.class`; hãy xác định đúng parameter types.
- **Quên generic erasure:** `getType()` trả kiểu thô như `List.class`; `getGenericType()` trả `Type` và có thể cần xử lý `ParameterizedType`, `TypeVariable`, `WildcardType`.
- **Phụ thuộc tên tham số:** source parameter names thường không tồn tại nếu thiếu `-parameters`.
- **Coi mọi method phản chiếu là do người viết:** có thể xuất hiện method bridge/synthetic do compiler; kiểm tra `isBridge()`, `isSynthetic()` khi xây API scanner.
- **Dựa vào thứ tự liệt kê:** nếu cần hiển thị ổn định, tự sắp xếp theo quy tắc rõ ràng, ví dụ tên, số tham số và tên đầy đủ các parameter types.
- **Gọi nhầm receiver:** method/field instance cần object tương thích; static dùng receiver `null`.
- **Đọc/ghi private hoặc final tràn lan:** ưu tiên contract public; Reflection có thể phá vỡ tính đóng gói và khiến nâng cấp JDK/module khó hơn.
- **Cho phép input không tin cậy tùy ý chọn class/method:** công cụ động phải giới hạn danh sách lớp, thành viên, quyền thao tác và kiểu đối số được phép; tránh tạo API thực thi tùy ý.
- **Bỏ qua chi phí:** tra cứu metadata lặp lại trong đường chạy nóng có thể gây overhead; cache các `Method`/`Field`/`Constructor` phù hợp khi có lý do đo được, không cache bất cẩn qua nhiều class loader.

## 11. Checklist áp dụng và câu hỏi tự kiểm tra

Một quy trình thực tế để xây trình tra cứu:

1. Xác định `Class<?>` của đối tượng/lớp cần nghiên cứu.
2. Chọn `getXxx` nếu muốn thành viên public có thể thừa kế; chọn `getDeclaredXxx` nếu muốn thành viên khai báo ngay tại lớp, bất kể mức truy cập.
3. Với Method/Constructor, xác định **chính xác** các `Class<?>` tham số trước khi tra cứu; với Field, xác định tên khai báo.
4. Xem `getDeclaringClass()`, `getModifiers()`, `getType()`/`getReturnType()` và `Executable.getParameterTypes()` để kiểm tra chữ ký.
5. Chỉ thực thi `invoke`/`newInstance` hay đọc/ghi `get`/`set` sau khi biết receiver, đối số và điều kiện truy cập.
6. Xử lý riêng lỗi tra cứu, lỗi quyền truy cập, lỗi đối số và lỗi phát sinh **bên trong** method/constructor.
7. Nếu dùng scanner/framework, xử lý bridge/synthetic, generic metadata và thứ tự hiển thị một cách có chủ đích.

Tự kiểm tra:

- Vì sao `Child.class.getMethod("parentPublic")` có thể thành công nhưng `Child.class.getDeclaredMethod("parentPublic")` thất bại?
- Khi nào dùng `Field.get(null)`? Khi nào `Method.invoke(null, ...)`?
- `getReturnType()` khác `getGenericReturnType()` như thế nào với `List<String>`?
- Tại sao `getDeclaredField("secret")` thành công vẫn chưa bảo đảm `field.get(instance)` thành công?
- Ngoại lệ nào bao bọc exception do method thật ném, và tìm nguyên nhân gốc ra sao?
- `Executable` cung cấp điểm chung nào để viết một hàm in chữ ký dùng được cho cả `Method` và `Constructor`?

Tài liệu chuẩn để đối chiếu API: [`Class`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html), [`Member`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Member.html), [`Field`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html), [`Method`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html), [`Constructor`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Constructor.html), [`Executable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Executable.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="member-lookup-002">getField, getMethod, getConstructor</a>

<details>
<summary>Xem chi tiết</summary>

# Tra cứu thành viên bằng `getField`, `getMethod`, `getConstructor` trong Java Reflection

Trong Java Reflection, đối tượng `Class<?>` cho phép tra cứu **field (trường)**, **method (phương thức)** và **constructor (hàm tạo)** từ thông tin kiểu tại runtime. Ba API `getField`, `getMethod`, `getConstructor` thường được sử dụng khi tên thành viên hoặc chữ ký constructor chỉ được xác định lúc chương trình chạy, chẳng hạn trong serializer, framework dependency injection, bộ kiểm tra API hoặc cơ chế plugin.

Điểm chung quan trọng: cả ba API đều tìm **thành viên `public`**. Tuy nhiên, **field và method có thể được kế thừa**, còn **constructor không được kế thừa**. Nếu cần cả thành viên `private`, `protected`, package-private, hoặc cần biết chính xác thành viên **khai báo ngay trên lớp**, dùng các API `getDeclaredField`, `getDeclaredMethod`, `getDeclaredConstructor` phù hợp.

## 1. Bức tranh tổng thể

| API của `Class` | Giá trị trả về | Tiêu chí tìm | Có xét thành viên kế thừa? | Phạm vi truy cập khi tra cứu |
|---|---|---|---|---|
| `getField(String name)` | `Field` | Tên trường | Có, từ superclass hoặc interface theo quy tắc tra cứu field | Chỉ `public` |
| `getMethod(String name, Class<?>... parameterTypes)` | `Method` | Tên phương thức và dãy kiểu tham số | Có, gồm method được kế thừa từ superclass/interface | Chỉ `public` |
| `getConstructor(Class<?>... parameterTypes)` | `Constructor<T>` | Dãy kiểu tham số của constructor | **Không**; constructor chỉ thuộc lớp khai báo | Chỉ `public` |
| `getDeclaredField(String name)` | `Field` | Tên trường khai báo trực tiếp | Không | Tất cả mức truy cập |
| `getDeclaredMethod(String name, Class<?>... parameterTypes)` | `Method` | Tên và kiểu tham số của method khai báo trực tiếp | Không | Tất cả mức truy cập |
| `getDeclaredConstructor(Class<?>... parameterTypes)` | `Constructor<T>` | Kiểu tham số constructor khai báo trực tiếp | Không | Tất cả mức truy cập |

Ba API `get...` trả về **một** thành viên. Để liệt kê nhiều thành viên, sử dụng `getFields()`, `getMethods()`, `getConstructors()` hoặc những phiên bản `getDeclared...` số nhiều. Thứ tự các phần tử do các API liệt kê trả về **không được bảo đảm**; không dựa vào vị trí trong mảng để chọn thành viên.

Một lookup thành công chỉ chứng minh rằng Reflection **tìm được metadata**. Nó chưa đồng nghĩa thành viên có thể được đọc, ghi, gọi hoặc khởi tạo từ mọi caller: ở thời điểm sử dụng, Java vẫn áp dụng quy tắc access và giới hạn của module.

## 2. `getField(String name)`: tìm trường `public`

Chữ ký:

```java
Field field = SomeType.class.getField("fieldName");
```

`getField` tìm trường `public` có đúng tên được yêu cầu. Trường đó có thể được **khai báo trực tiếp** trên lớp đích hoặc được **kế thừa** từ superclass/interface. Trả về `java.lang.reflect.Field`, cung cấp metadata như `getName()`, `getType()`, `getModifiers()`, `getDeclaringClass()`.

Ví dụ giả định:

```java
class Base {
    public int count;
}

class Derived extends Base {
    private String secret;
}
```

- `Derived.class.getField("count")` **thành công**: `count` là trường `public` kế thừa từ `Base`.
- `Derived.class.getField("secret")` ném `NoSuchFieldException`: `secret` không phải `public`.
- `Derived.class.getDeclaredField("secret")` **thành công**: trường này được khai báo trực tiếp trên `Derived`, bất kể `private`.
- `Derived.class.getDeclaredField("count")` ném `NoSuchFieldException`: `count` được khai báo ở `Base`, không phải `Derived`.

Đừng nhầm **phát hiện một `Field`** với **đọc giá trị field**:

```java
Field field = Derived.class.getField("count");
Derived object = new Derived();
int value = field.getInt(object);
field.setInt(object, 42);
```

Đây là đoạn minh họa độc lập về thao tác `Field`, sử dụng hai class vừa khai báo ở trên. `field.getInt(object)` đọc giá trị trường kiểu `int` của instance; `setInt` gán lại giá trị. Với trường `static`, receiver cho `Field.get(null)` có thể là `null`. Quy tắc access và giới hạn ghi trường `final` vẫn áp dụng.

**Field hiding:** nếu subclass khai báo một trường mới **trùng tên** với trường ở superclass, hai trường vẫn là hai thành viên riêng; đây không phải cơ chế overriding như method. Khi cần chọn đúng trường gốc, hãy tra cứu trên `Base.class` hoặc dùng `getDeclaredField` trên lớp thực sự khai báo trường. Khi các interface cung cấp nhiều trường `public` trùng tên, đừng dựa vào một lookup không rõ ràng; chọn interface khai báo cụ thể.

## 3. `getMethod(String name, Class<?>... parameterTypes)`: tìm phương thức `public`

Chữ ký:

```java
Method method = SomeType.class.getMethod("methodName", String.class, int.class);
```

Lookup dùng **tên method** và **dãy kiểu tham số theo đúng thứ tự**. Phương thức `public` có thể thuộc lớp hiện tại hoặc được kế thừa từ superclass/interface. `getMethod("save", String.class)` khác `getMethod("save", Object.class)` và khác `getMethod("save")`.

Ví dụ:

```java
class Service {
    public void run() { }
    public void run(String text) { }
    public void run(String text, int count) { }
    private void reset() { }
}
```

```java
Method m0 = Service.class.getMethod("run");
Method m1 = Service.class.getMethod("run", String.class);
Method m2 = Service.class.getMethod("run", String.class, int.class);
Method hidden = Service.class.getDeclaredMethod("reset");
```

Các lệnh trên là **đoạn minh họa**, không phải chương trình có `main`. `m0`, `m1`, `m2` ứng với ba overload. `getMethod("reset")` không tìm thấy vì `reset` là `private`; `getDeclaredMethod("reset")` tìm thấy metadata.

### Kiểu tham số phải khớp chữ ký khai báo

- `int.class` **khác** `Integer.class`; tra cứu không tự boxing/unboxing để tìm một chữ ký khác.
- `String.class` **khác** `Object.class`; tra cứu không chọn overload chỉ vì một đối số runtime có thể gán cho kiểu tham số đó.
- Varargs `String... values` được khai báo dưới dạng mảng trong bytecode; tra cứu bằng `String[].class`.
- Với `List<String>`, chữ ký runtime thông thường dùng kiểu đã **erasure**: `List.class`, không có `List<String>.class`.
- Phân biệt thứ tự tham số: `(String, int)` khác `(int, String)`.
- Không truyền kiểu trả về vào `getMethod`; Java không overload method chỉ bằng return type ở mức mã nguồn thông thường.

Ví dụ minh họa cho hai chữ ký đặc biệt:

```java
// Giả sử Example khai báo:
// public void accept(int value) { }
// public void collect(String... values) { }

Method accept = Example.class.getMethod("accept", int.class);
Method collect = Example.class.getMethod("collect", String[].class);
```

`Integer.class` không thể thay `int.class` ở lookup `accept`. Đối với generic methods, bridge/synthetic methods có thể xuất hiện trong metadata; khi kiểm tra tập methods, có thể dùng `isBridge()` và `isSynthetic()` tùy mục đích.

### Tra cứu khác thực thi

`getMethod` chỉ lấy metadata. Khi cần thực thi:

```java
Method method = Service.class.getMethod("run", String.class);
Service service = new Service();
method.invoke(service, "hello");
```

`Method.invoke` mới gọi logic của method. Đối với method `static`, có thể dùng receiver `null`. Exception được method gọi ném ra thường được bọc trong `InvocationTargetException`; kiểm tra `getCause()` để tìm lỗi gốc. Kiểu tham số của đối số truyền vào `invoke` vẫn phải phù hợp; một lookup đúng không bảo đảm lời gọi có arguments đúng.

**Method overriding:** khi subclass override method `public`, `getMethod` thường trả về method tương ứng với phiên bản override cụ thể. Khi lớp chỉ kế thừa method và không override, `getDeclaringClass()` chỉ về lớp khai báo ở superclass/interface. Trong bytecode có thể tồn tại bridge methods hoặc những method cùng tên và tham số nhưng return type khác nhau; Reflection có quy tắc lựa chọn riêng, nên với trường hợp compiler sinh bridge hãy kiểm tra metadata thực tế thay vì suy từ một chữ ký Java nguồn đơn giản.

## 4. `getConstructor(Class<?>... parameterTypes)`: tìm constructor `public`

Chữ ký:

```java
Constructor<User> constructor = User.class.getConstructor(String.class);
```

Constructor không có tên phương thức riêng khi tra cứu; chỉ truyền **dãy kiểu tham số**. Nếu constructor không tham số, gọi `getConstructor()`. API này chỉ tìm constructor **`public` do chính lớp đích khai báo**. Constructor của superclass không được kế thừa.

```java
class Account {
    public Account() { }
    public Account(String id) { }
    private Account(int internalCode) { }
}
```

```java
Constructor<Account> a = Account.class.getConstructor();
Constructor<Account> b = Account.class.getConstructor(String.class);
Constructor<Account> internal = Account.class.getDeclaredConstructor(int.class);
```

Đây là đoạn minh họa. `Account.class.getConstructor(int.class)` thất bại vì constructor đó là `private`; `getDeclaredConstructor(int.class)` trả về metadata nhưng không tự cấp quyền gọi.

Tạo đối tượng bằng `Constructor.newInstance`:

```java
Constructor<Account> ctor = Account.class.getConstructor(String.class);
Account instance = ctor.newInstance("A-001");
```

Constructor thực sự chạy ở bước `newInstance`. Nếu class chưa được khởi tạo, JVM khởi tạo class trước khi tạo instance. Việc một class không khai báo constructor trong **mã nguồn** có thể dẫn đến constructor mặc định do compiler sinh, nhưng **mức truy cập của constructor mặc định phụ thuộc mức truy cập của class**. Đừng giả định `getConstructor()` luôn có kết quả: với một số class, không tồn tại constructor không tham số `public`.

Đối với interface, abstract class, array và primitive type, không thể suy rằng có constructor có thể tạo instance theo cách này. `getConstructor(...)` có thể báo không tìm thấy constructor phù hợp; ngay cả khi tìm được constructor của abstract class, `newInstance` vẫn không thể khởi tạo một instance của abstract class.

## 5. Ví dụ Java 17+ hoàn chỉnh: public, kế thừa, overload, constructor

Tạo tệp **`MemberLookupDemo.java`**. Các class lồng nhau được khai báo `static` để ví dụ có thể chạy từ một tệp độc lập:

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class MemberLookupDemo {
    public interface Tagged {
        String TAG = "interface-tag";
    }

    public static class Parent {
        public int inherited = 10;

        public String origin() {
            return "Parent.origin";
        }
    }

    public static class Child extends Parent implements Tagged {
        public int own = 20;
        private String secret = "hidden";

        public Child() {
        }

        public Child(String text) {
            own = text.length();
        }

        private Child(int value) {
            own = value;
        }

        public String echo() {
            return "echo:empty";
        }

        public String echo(String text) {
            return "echo:" + text;
        }

        private void hidden() {
        }
    }

    public static void main(String[] args) throws Exception {
        Class<Child> type = Child.class;

        Field inherited = type.getField("inherited");
        Field fromInterface = type.getField("TAG");
        Field own = type.getField("own");

        System.out.println(inherited.getDeclaringClass().getSimpleName());
        System.out.println(fromInterface.getDeclaringClass().getSimpleName());
        System.out.println(own.getDeclaringClass().getSimpleName());

        Method origin = type.getMethod("origin");
        Method echoString = type.getMethod("echo", String.class);
        System.out.println(origin.getDeclaringClass().getSimpleName());
        System.out.println(echoString.getDeclaringClass().getSimpleName());
        System.out.println(type.getMethod("echo").getParameterCount());

        Constructor<Child> empty = type.getConstructor();
        Constructor<Child> withText = type.getConstructor(String.class);
        System.out.println(empty.getParameterCount());

        Child child = withText.newInstance("Java");
        System.out.println(child.own);
        System.out.println(inherited.getInt(child));
        System.out.println(origin.invoke(child));
        System.out.println(echoString.invoke(child, "Reflection"));

        try {
            type.getField("secret");
        } catch (NoSuchFieldException e) {
            System.out.println("secret: not public");
        }
        System.out.println(type.getDeclaredField("secret").getName());

        try {
            type.getConstructor(int.class);
        } catch (NoSuchMethodException e) {
            System.out.println("int constructor: not public");
        }
        System.out.println(type.getDeclaredConstructor(int.class).getParameterCount());

        try {
            type.getMethod("hidden");
        } catch (NoSuchMethodException e) {
            System.out.println("hidden: not public");
        }
        System.out.println(type.getDeclaredMethod("hidden").getName());
    }
}
```

Chạy trong thư mục chứa tệp:

```bash
javac MemberLookupDemo.java
java MemberLookupDemo
```

Stdout:

```text
Parent
Tagged
Child
Parent
Child
0
0
4
10
Parent.origin
echo:Reflection
secret: not public
secret
int constructor: not public
1
hidden: not public
hidden
```

Đối chiếu output:

1. `getField("inherited")` trả `Field` khai báo ở `Parent`; `getField("TAG")` tìm trường `public static final` của interface `Tagged`; `getField("own")` thuộc `Child`. `getDeclaringClass()` giúp xác định **nơi khai báo thực tế**, không nhất thiết là `Class` dùng để tra cứu.
2. `getMethod("origin")` nhận method từ `Parent`; `getMethod("echo", String.class)` nhận method của `Child`. Overload `echo()` có `0` tham số.
3. `getConstructor()` thành công vì `Child` có constructor không tham số `public`. Constructor nhận `String` được gọi với `"Java"`, độ dài 4, nên `own` trở thành `4`.
4. `Field.getInt(child)` đọc trường được kế thừa `inherited = 10`; `Method.invoke` gọi thực sự hai method và nhận về `Parent.origin` cùng `echo:Reflection`.
5. Các thao tác `getField("secret")`, `getConstructor(int.class)`, `getMethod("hidden")` đều thất bại vì thành viên là `private`. Ba thao tác `getDeclared...` tương ứng **tra cứu được metadata**, ngay cả khi thành viên không `public`.
6. Ví dụ **không gọi** thành viên private. Tìm thấy bằng `getDeclared...` và được phép sử dụng bằng Reflection là hai vấn đề khác nhau.

## 6. Khi nào dùng `getDeclared...`?

Có hai câu hỏi khác nhau:

- **“Một caller có thể nhìn thấy những thành viên public nào qua kiểu này?”** → `getField`, `getMethod`, `getConstructor`.
- **“Chính class này đã khai báo những thành viên nào, bao gồm private?”** → `getDeclaredField`, `getDeclaredMethod`, `getDeclaredConstructor`.

Ví dụ, framework ánh xạ thuộc tính có thể cần tìm field `private` được khai báo trong class; bộ kiểm tra hợp đồng API có thể chỉ quan tâm method `public` kể cả được kế thừa.

Không suy ra một thành viên `private` lấy bằng `getDeclaredField` sẽ đọc được ngay:

```java
// Đoạn minh họa: type là một Class<?> đã được xác định.
Field privateField = type.getDeclaredField("secret");
boolean accessible = privateField.trySetAccessible();
if (!accessible) {
    throw new IllegalStateException("Private field is not reflectively accessible");
}
```

`trySetAccessible()` yêu cầu bỏ qua một số kiểm tra access của Java Reflection nếu nền tảng và quan hệ module cho phép; nó có thể trả `false`. Với named modules, việc có `private` metadata **không xóa** cơ chế encapsulation của Java Platform Module System. Trong ứng dụng thực, ưu tiên API public, constructor/factory, hoặc module `opens` có chủ đích khi cần truy cập phản chiếu sâu. Tránh tự động bật quyền cho mọi member.

**Lưu ý về tìm trong superclass:** `getDeclaredField("x")` không tự đi ngược cây kế thừa. Nếu ứng dụng phải tìm cả các trường non-public ở nhiều cấp, nó cần chủ động duyệt `getSuperclass()` và tra cứu `getDeclaredField` tại từng cấp, có quy tắc rõ ràng khi tên bị che khuất. Đây là thao tác khác với `getField`.

## 7. Xử lý thất bại và phân biệt lỗi

| Trường hợp | Exception hoặc hiện tượng | Cách xử lý |
|---|---|---|
| Không có field `public` đúng tên | `NoSuchFieldException` từ `getField` | Kiểm tra tên, nơi khai báo, mức truy cập; cân nhắc `getDeclaredField` nếu cần metadata non-public. |
| Không có method `public` đúng tên + kiểu tham số | `NoSuchMethodException` từ `getMethod` | Kiểm tra overload, `int.class` vs `Integer.class`, kiểu mảng của varargs và erasure generic. |
| Không có constructor `public` đúng dãy kiểu | `NoSuchMethodException` từ `getConstructor` | Kiểm tra constructor khai báo tại chính lớp và mức truy cập. |
| Đã tìm được method nhưng caller không được phép gọi | `IllegalAccessException` từ `Method.invoke` | Xem visibility và module exports/opens; dùng API public hoặc quyền phù hợp. |
| Method/constructor được gọi ném exception | `InvocationTargetException` | Kiểm tra `getCause()` để thấy exception gốc từ code được thực thi. |
| Constructor không thể tạo instance của class abstract | `InstantiationException` khi `newInstance` | Sử dụng concrete implementation có constructor phù hợp. |
| Đối số của `invoke`/`newInstance` không phù hợp | Có thể có `IllegalArgumentException` | So sánh số lượng, kiểu và receiver thực tế với parameter types. |
| Cố vượt giới hạn reflective access của module | Có thể nhận `InaccessibleObjectException` với `setAccessible(true)` | Xem module boundaries; không giả định mọi member đều có thể mở truy cập. |

Ở thời điểm **lookup**, đa số lỗi “không tồn tại đúng chữ ký” là checked exception `NoSuchFieldException`/`NoSuchMethodException`. Ở thời điểm **sử dụng** `Field`, `Method`, `Constructor`, các exception liên quan quyền truy cập, tham số và code thực thi mới quan trọng.

Ví dụ xử lý một method bắt buộc (đoạn code có `Class<?> type` đã xác định):

```java
try {
    Method method = type.getMethod("execute", String.class);
    // Quy trình tiếp theo có thể kiểm tra modifiers và quyền truy cập
    // trước khi gọi method với receiver/argument phù hợp.
} catch (NoSuchMethodException e) {
    throw new IllegalArgumentException(
            "Expected public execute(String) method on " + type.getName(), e);
}
```

Không dùng `catch (Exception)` để che mọi lỗi tra cứu rồi coi như class không hỗ trợ tính năng. Cần phân biệt **không tìm thấy member** với **đã tìm thấy nhưng việc invoke thất bại** để chẩn đoán đúng.

## 8. Các lỗi tư duy phổ biến

1. **“`getDeclaredMethod` bao gồm method kế thừa.”** Sai: `declared` chỉ những method được khai báo trực tiếp trong class/interface này.
2. **“`getConstructor` tự tìm constructor của superclass.”** Sai: constructor không được kế thừa.
3. **“`getMethod` tự chọn overload từ argument sẽ truyền vào sau.”** Sai: cần đưa đúng `Class<?>...` của parameter types **ngay tại lookup**.
4. **“`getField` chỉ tìm trường của chính class hiện tại.”** Sai: có thể tìm field `public` từ superclass/interface.
5. **“Tìm được member private nghĩa là gọi được member private.”** Sai: metadata retrieval và reflective access là hai bước riêng.
6. **“Truyền `Integer.class` sẽ tìm được method khai báo `int`.”** Sai: kiểu trong chữ ký reflection là chính xác, không boxing khi lookup.
7. **“`getMethod("work")` nghĩa là gọi method `work`.”** Sai: chỉ trả về `Method`; gọi bằng `invoke` hoặc Java bình thường.
8. **“`getMethods()[0]` luôn là method đầu tiên trong mã nguồn.”** Sai: API không bảo đảm thứ tự liệt kê.
9. **“Một field có getter nên `getField("name")` tìm được property đó.”** Sai: getter `getName()` là method; Java Reflection cơ bản không tự biến JavaBean property thành field cùng tên.
10. **“Tra cứu constructor không tham số phải viết `getConstructor(null)`.”** Không nên: dùng **`getConstructor()`** để truyền dãy kiểu tham số rỗng, rõ ràng và an toàn với varargs.

## 9. Bài tập tự kiểm tra

**Câu 1.** `Child extends Parent`, `Parent` khai báo `public void show()`, `Child` không override. Tra cứu nào tìm được `show` từ `Child.class`?

**Đáp án:** `Child.class.getMethod("show")` tìm được; `Child.class.getDeclaredMethod("show")` ném `NoSuchMethodException`.

**Câu 2.** `Service` khai báo `public void process(int n)`. Cần gọi `getMethod` với kiểu nào?

**Đáp án:** `Service.class.getMethod("process", int.class)`. `Integer.class` là chữ ký khác.

**Câu 3.** Một `Customer` có `private Customer(String id)`. `Customer.class.getConstructor(String.class)` và `Customer.class.getDeclaredConstructor(String.class)` có cùng kết quả không?

**Đáp án:** Không. Phiên bản không có `Declared` không tìm thấy constructor `private`; phiên bản `Declared` trả về metadata nhưng quyền gọi constructor còn tùy access.

**Câu 4.** Vì sao nên gọi `getDeclaringClass()` trên `Field`/`Method` đã tìm được?

**Đáp án:** Để biết thành viên thực sự được khai báo ở class/interface nào, đặc biệt khi tra cứu qua subclass và có inheritance hoặc field hiding.

**Câu 5.** Có thể tạo object bằng `getConstructor()` không?

**Đáp án:** `getConstructor()` chỉ trả metadata của một constructor `public` không tham số **nếu tồn tại**. Phải gọi `Constructor.newInstance()` để yêu cầu tạo đối tượng, và class phải có khả năng được khởi tạo.

## Tài liệu tra cứu chuẩn

- Java SE API, `java.lang.Class`: `getField`, `getMethod`, `getConstructor`; `getDeclaredField`, `getDeclaredMethod`, `getDeclaredConstructor`.
- Java SE API, `java.lang.reflect.Field`: `get`, `getInt`, `set`, `getDeclaringClass`.
- Java SE API, `java.lang.reflect.Method`: `invoke`, `getParameterTypes`, `isBridge`, `isSynthetic`.
- Java SE API, `java.lang.reflect.Constructor`: `newInstance`, `getParameterTypes`.
- Java Language Specification, phần về inheritance, access control, method signatures và constructor declarations.

**Ghi nhớ:** `getField` tra cứu **field public theo tên**; `getMethod` tra cứu **method public theo tên và tham số**; `getConstructor` tra cứu **constructor public của chính lớp theo tham số**. Thêm `Declared` khi muốn metadata thành viên khai báo trực tiếp bất kể mức truy cập, và luôn phân biệt **lookup** với **truy cập/thực thi**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="member-lookup-003">getDeclaredField, getDeclaredMethod, getDeclaredConstructor</a>

<details>
<summary>Xem chi tiết</summary>

# Java Reflection: tra cứu thành viên bằng `getDeclaredField()`, `getDeclaredMethod()` và `getDeclaredConstructor()`

Khi đã có một đối tượng `Class<?>`, Reflection cho phép tra cứu chính xác **field**, **method** và **constructor** được **khai báo trực tiếp** trong kiểu đó. Ba phương thức `getDeclaredField()`, `getDeclaredMethod()` và `getDeclaredConstructor()` là công cụ chính cho công việc này. Chúng có thể tìm thấy cả thành viên `private`, `protected`, package-private và `public`, nhưng **việc tìm được thành viên không tự động cho phép đọc, gọi hoặc khởi tạo qua thành viên ấy**.

Bài học tập trung vào tra cứu thành viên theo tên/chữ ký, khác biệt giữa thành viên khai báo và thành viên kế thừa, cách xử lý chữ ký phương thức chính xác, kết quả trả về, ngoại lệ, và quyền truy cập khi sử dụng đối tượng Reflection đã tìm được.

## 1. Từ `Class<?>` tới ba loại thành viên

Ba API nằm trên `java.lang.Class`:

| API | Đối tượng trả về | Điều kiện tra cứu | Không bao gồm |
| --- | --- | --- | --- |
| `getDeclaredField(String name)` | `Field` | Field có tên `name`, khai báo trực tiếp trong lớp/interface đang xét | Field chỉ được kế thừa từ superclass |
| `getDeclaredMethod(String name, Class<?>... parameterTypes)` | `Method` | Method khai báo trực tiếp, đúng tên và đúng **dãy kiểu tham số** | Method chỉ được kế thừa; phương thức cùng tên nhưng sai kiểu/số lượng tham số |
| `getDeclaredConstructor(Class<?>... parameterTypes)` | `Constructor<T>` | Constructor được khai báo bởi lớp, với dãy kiểu tham số yêu cầu | Constructor của lớp cha; constructor có chữ ký khác |

Cú pháp điển hình:

~~~java
Class<?> type = Example.class;

Field field = type.getDeclaredField("value");
Method method = type.getDeclaredMethod("convert", String.class, int.class);
Constructor<?> constructor = type.getDeclaredConstructor(String.class);
~~~

Đây là **đoạn mã minh họa API**; nó chỉ hoạt động khi lớp `Example` thực sự có các khai báo tương ứng. Nếu không tìm thấy thành viên, phương thức sẽ ném checked exception (`NoSuchFieldException` hoặc `NoSuchMethodException`).

Cụm từ *declared* có hai ý nghĩa cần nhớ:

1. **Không lọc theo mức truy cập:** thành viên `private` vẫn có thể được tìm thấy dưới dạng `Field`, `Method` hoặc `Constructor`.
2. **Chỉ xét khai báo trực tiếp:** field và method nằm ở lớp cha không xuất hiện khi tra cứu `getDeclaredField`/`getDeclaredMethod` trên lớp con. Constructor vốn không được kế thừa.

Các phương thức có dạng số nhiều là `getDeclaredFields()`, `getDeclaredMethods()`, `getDeclaredConstructors()`. Chúng dùng để khám phá toàn bộ thành viên được khai báo; khi đã biết tên/chữ ký thì nên dùng API số ít để tra cứu đúng mục tiêu. Thứ tự phần tử trong mảng kết quả `getDeclared*()` số nhiều không được đảm bảo.

## 2. `getDeclaredField(String name)`: tìm field theo tên

`getDeclaredField("code")` tìm trường tên `code` trực tiếp trên đối tượng `Class` đang xét. Nó trả về `java.lang.reflect.Field`, từ đó có thể đọc metadata:

- `field.getName()`: tên field.
- `field.getType()`: kiểu runtime của field dưới dạng `Class<?>`.
- `field.getGenericType()`: metadata của kiểu generic khi có.
- `field.getDeclaringClass()`: lớp/interface thực sự khai báo field.
- `field.getModifiers()`: cờ truy cập và các modifier; sử dụng `Modifier.isPrivate(...)`, `Modifier.isStatic(...)` khi cần.
- `field.get(target)` hoặc các phương thức như `field.getInt(target)`: đọc giá trị **sau khi thỏa quy tắc truy cập**.

Field không có danh sách tham số hay overload theo chữ ký. Một lớp Java không thể khai báo hai field trùng tên trong cùng một khai báo lớp hợp lệ; nhưng lớp con có thể **che khuất** field cùng tên của lớp cha. Khi đó `Child.class.getDeclaredField("code")` tìm field của `Child`, còn `Parent.class.getDeclaredField("code")` tìm field của `Parent` nếu mỗi lớp tự khai báo field ấy.

**Sai lầm thường gặp:** cho rằng `Child.class.getDeclaredField("inherited")` tìm được field của `Parent`. Đây là yêu cầu ngoài phạm vi *declared*. Nếu field là `public`, `Child.class.getField("inherited")` có thể tìm thấy field kế thừa; nếu không phải public, phải duyệt `getSuperclass()` theo đúng yêu cầu nghiệp vụ rồi tra cứu trên lớp khai báo.

## 3. `getDeclaredMethod(String name, Class<?>... parameterTypes)`: tên + chữ ký tham số

Để tìm một method cụ thể, truyền tên phương thức và các `Class<?>` biểu diễn **kiểu tham số khai báo theo đúng thứ tự**:

~~~java
type.getDeclaredMethod("save", String.class, int.class);
type.getDeclaredMethod("refresh"); // Method không có tham số
~~~

Những quy tắc quan trọng:

- Phân biệt **số lượng và thứ tự tham số**: `save(String,int)` khác `save(int,String)`.
- Phân biệt **primitive và wrapper**: `int.class` khác `Integer.class`.
- Phân biệt **mảng và phần tử**: `String[].class` khác `String.class`.
- Tham số `String... values` ở mã nguồn được biểu diễn là `String[]` ở chữ ký Reflection; khi tra cứu phải dùng `String[].class`, không truyền nhiều đối số `String.class`.
- Không áp dụng tự động *autoboxing*, *widening* (`int` sang `long`), hay chuyển kiểu khi **tra cứu**. Những quy tắc này của lời gọi Java thông thường không dùng để dò ra method ở đây.
- Với tham số generic như `List<String>`, chữ ký runtime chịu *type erasure*: tra cứu bằng `List.class`. Dùng `getGenericParameterTypes()` trên `Method` nếu cần phân tích metadata generic.
- **Không truyền kiểu trả về** vào `getDeclaredMethod`. API nhận tên và kiểu tham số; sau khi lấy `Method`, đọc `method.getReturnType()` hoặc `getGenericReturnType()` để kiểm tra kiểu kết quả. Trong bytecode có thể tồn tại bridge/synthetic method; không nên suy đoán danh sách `getDeclaredMethods()` chỉ gồm các method tự viết trong mã nguồn.

Nếu hai overload là `parse(String)` và `parse(int)`, tên giống nhau nhưng đối số `String.class` và `int.class` giúp chọn đúng method. Method `static` vẫn tra cứu bằng cách tương tự, rồi có thể kiểm tra `Modifier.isStatic(method.getModifiers())`.

## 4. `getDeclaredConstructor(Class<?>... parameterTypes)`: tra cứu constructor

Constructor không có tên method riêng trong lời gọi này: chỉ cần truyền danh sách kiểu tham số của constructor.

~~~java
Constructor<?> noArg = type.getDeclaredConstructor();
Constructor<?> withName = type.getDeclaredConstructor(String.class);
~~~

`getDeclaredConstructor()` không tham số yêu cầu **constructor thật sự không tham số**; nó không có nghĩa là "lấy constructor bất kỳ" hay "tạo constructor mặc định". Nếu lớp chỉ khai báo `Example(String)`, tra cứu `getDeclaredConstructor()` sẽ ném `NoSuchMethodException`. Nếu Java tự tạo constructor mặc định, constructor đó có thể tìm thấy khi chữ ký khớp.

Khác với method/field, constructor **không được kế thừa**. Vì vậy `getDeclaredConstructor` và `getConstructor` đều chỉ áp dụng cho constructor của chính lớp đang được xét; sự khác biệt lớn là `getConstructor` chỉ tìm constructor `public`, còn `getDeclaredConstructor` có thể tìm constructor không `public`.

Một số trường hợp cần biết:

- Lớp `abstract` vẫn có constructor để lớp con gọi qua `super(...)`, nhưng không thể khởi tạo trực tiếp bằng `Constructor#newInstance`.
- Lớp thành viên **không `static`** có thể có tham số ẩn biểu diễn đối tượng lớp bao ngoài. Ví dụ constructor mã nguồn `Outer.Inner(int)` thường xuất hiện trong Reflection dưới dạng tham số `Outer.class, int.class`. Khi không thấy chữ ký dự đoán, xem `getParameterTypes()` của các constructor được khai báo.
- Enum và một số lớp do compiler sinh có chi tiết constructor đặc biệt; không giả định mọi chữ ký runtime giống hệt mã nguồn.

## 5. Ví dụ đầy đủ: declared khác inherited thế nào?

Tạo tệp **`DeclaredLookupDemo.java`**. Trong cùng tệp này, chỉ `DeclaredLookupDemo` là lớp `public`; hai lớp hỗ trợ dùng tên riêng để tránh phụ thuộc cấu trúc package:

~~~java
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

class LookupParent {
    public String inheritedField = "parent";

    public void inheritedMethod() {
    }
}

class LookupChild extends LookupParent {
    private String code;

    private LookupChild(String code) {
        this.code = code;
    }

    private String convert(String input) {
        return input.toUpperCase();
    }

    public int convert(int number) {
        return number * 2;
    }
}

public class DeclaredLookupDemo {
    public static void main(String[] args) throws ReflectiveOperationException {
        Class<LookupChild> type = LookupChild.class;

        Field field = type.getDeclaredField("code");
        Method stringMethod = type.getDeclaredMethod("convert", String.class);
        Method intMethod = type.getDeclaredMethod("convert", int.class);
        Constructor<LookupChild> constructor =
                type.getDeclaredConstructor(String.class);

        System.out.println("Field: " + field.getName()
                + " -> " + field.getType().getSimpleName());
        System.out.println("Method 1: " + stringMethod.getName()
                + "(String) -> " + stringMethod.getReturnType().getSimpleName());
        System.out.println("Method 2: " + intMethod.getName()
                + "(int) -> " + intMethod.getReturnType().getSimpleName());
        System.out.println("Constructor: "
                + constructor.getDeclaringClass().getSimpleName()
                + "(String)");

        try {
            type.getDeclaredField("inheritedField");
        } catch (NoSuchFieldException expected) {
            System.out.println("Declared inheritedField: not found");
        }

        System.out.println("Public inherited field: "
                + type.getField("inheritedField")
                        .getDeclaringClass().getSimpleName());

        try {
            type.getDeclaredMethod("inheritedMethod");
        } catch (NoSuchMethodException expected) {
            System.out.println("Declared inheritedMethod: not found");
        }

        System.out.println("Public inherited method: "
                + type.getMethod("inheritedMethod")
                        .getDeclaringClass().getSimpleName());
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac DeclaredLookupDemo.java
java DeclaredLookupDemo
~~~

**Stdout:**

~~~text
Field: code -> String
Method 1: convert(String) -> String
Method 2: convert(int) -> int
Constructor: LookupChild(String)
Declared inheritedField: not found
Public inherited field: LookupParent
Declared inheritedMethod: not found
Public inherited method: LookupParent
~~~

Vì sao kết quả như trên?

1. `code`, hai overload `convert` và constructor `LookupChild(String)` đều **được khai báo trực tiếp** trong `LookupChild`, nên ba API `getDeclared...` tìm được chúng bất kể `private` hay `public`.
2. `inheritedField` và `inheritedMethod` **chỉ khai báo tại `LookupParent`**; tìm bằng `getDeclared...` trên `LookupChild` thất bại.
3. Cả hai thành viên kế thừa là `public`, nên `getField`/`getMethod` trên `LookupChild` trả về thành viên có `getDeclaringClass() == LookupParent.class`.

Chú ý: ví dụ này **chỉ tra cứu và in metadata**, chưa đọc trực tiếp field `code`, chưa gọi `convert`, chưa tạo `LookupChild` từ private constructor. Đây là ranh giới quan trọng giữa *lookup* và *access*.

## 6. Tra cứu không đồng nghĩa quyền truy cập: `trySetAccessible()`

Một `Field`, `Method` hoặc `Constructor` được tìm bằng `getDeclared...` vẫn chịu quy tắc truy cập khi dùng `Field#get`/`set`, `Method#invoke` hoặc `Constructor#newInstance`. Với thành viên không thể truy cập từ mã gọi, một số thao tác có thể ném `IllegalAccessException` nếu không được cấp quyền thích hợp.

Lớp cơ sở `AccessibleObject` hỗ trợ:

- `canAccess(target)`: kiểm tra quyền truy cập hiện tại đối với thành viên; truyền đối tượng đích cho thành viên instance, `null` cho thành viên static và constructor.
- `trySetAccessible()`: thử bật quyền bỏ qua kiểm tra truy cập Java và trả `true` nếu thành công, `false` nếu không thể cấp. Việc thử này **không bảo đảm thành công**.
- `setAccessible(true)`: yêu cầu bật quyền bỏ qua kiểm tra; có thể ném `InaccessibleObjectException` trong trường hợp Java modules không cho phép.

Ví dụ sau chạy dưới dạng ứng dụng không khai báo `module-info.java` (unnamed module), nên mã ứng dụng trong ví dụ có thể truy cập sâu vào chính các lớp của nó. Tạo tệp **`DeclaredAccessDemo.java`**:

~~~java
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

class SecretBox {
    private String value;

    private SecretBox(String value) {
        this.value = value;
    }

    private String reveal(String suffix) {
        return value + suffix;
    }
}

public class DeclaredAccessDemo {
    public static void main(String[] args) throws ReflectiveOperationException {
        Class<SecretBox> type = SecretBox.class;

        Constructor<SecretBox> constructor =
                type.getDeclaredConstructor(String.class);
        System.out.println("Constructor access: "
                + constructor.trySetAccessible());

        SecretBox box = constructor.newInstance("data");

        Field field = type.getDeclaredField("value");
        System.out.println("Field access: " + field.trySetAccessible());
        System.out.println("Field value: " + field.get(box));

        Method method = type.getDeclaredMethod("reveal", String.class);
        System.out.println("Method access: " + method.trySetAccessible());
        System.out.println("Method result: " + method.invoke(box, "!"));
    }
}
~~~

~~~bash
javac DeclaredAccessDemo.java
java DeclaredAccessDemo
~~~

**Stdout trong điều kiện chạy được nêu:**

~~~text
Constructor access: true
Field access: true
Field value: data
Method access: true
Method result: data!
~~~

**Giải thích luồng thực thi:**

1. Tìm private constructor theo đúng `String.class` rồi gọi `trySetAccessible()` trước khi `newInstance("data")`.
2. Tìm private field `value`, thử cấp quyền, sau đó đọc bằng `field.get(box)`.
3. Tìm private method `reveal(String)`, thử cấp quyền, rồi gọi `method.invoke(box, "!")`. Kết quả trả về kiểu `Object`; ở đây chuỗi kết quả là `data!`.

Trong chương trình thực tế, phải **kiểm tra** kết quả `trySetAccessible()` trước khi thao tác. Nếu trả `false`, không được giả định thao tác sẽ thành công. Tránh mở quyền phản chiếu cho dữ liệu không tin cậy và không cần thiết; ưu tiên API `public` nếu có thể.

### Java Platform Module System (JPMS) từ Java 9

Quyền truy cập Reflection còn bị giới hạn bởi ranh giới module. Một package được khai báo `exports` thường cho phép các module đọc thành viên `public` theo quy tắc truy cập thông thường; để thực hiện **deep reflection** tới thành viên không `public` ở module khác, package thường phải được `opens` phù hợp cho module thực hiện Reflection (hoặc có cơ chế cấp phép tương đương). `getDeclaredMethod()` vẫn có thể trả về metadata nhưng `trySetAccessible()` có thể trả `false`, còn `setAccessible(true)` có thể ném `InaccessibleObjectException`. Không dùng `--add-opens` như giải pháp mặc định cho mã ứng dụng; việc mở package cần quyết định rõ ràng ở ranh giới module.

## 7. Ví dụ chữ ký chính xác: varargs, primitive/wrapper, generic erasure

Tạo tệp **`ExactSignatureDemo.java`**:

~~~java
import java.lang.reflect.Method;
import java.util.List;

class SignatureTarget {
    private void run(int count, String... tags) {
    }

    private void accept(List<String> values) {
    }
}

public class ExactSignatureDemo {
    public static void main(String[] args) throws NoSuchMethodException {
        Class<SignatureTarget> type = SignatureTarget.class;

        Method run = type.getDeclaredMethod(
                "run", int.class, String[].class);
        Method accept = type.getDeclaredMethod("accept", List.class);

        System.out.println("Found: " + run.getName());
        System.out.println("Varargs: " + run.isVarArgs());
        System.out.println("Generic erased parameter: "
                + accept.getParameterTypes()[0].getName());

        try {
            type.getDeclaredMethod("run", Integer.class, String[].class);
        } catch (NoSuchMethodException expected) {
            System.out.println("Integer.class instead of int.class: not found");
        }
    }
}
~~~

~~~bash
javac ExactSignatureDemo.java
java ExactSignatureDemo
~~~

**Stdout:**

~~~text
Found: run
Varargs: true
Generic erased parameter: java.util.List
Integer.class instead of int.class: not found
~~~

Điều này cho thấy `String...` là `String[]` ở chữ ký Reflection; `int.class` không tương đương `Integer.class` khi **dò phương thức**; và `List<String>` dùng `List.class` sau type erasure. Nếu cần tham số `List<String>` ở mức khai báo, có thể tiếp tục đọc `accept.getGenericParameterTypes()`.

Các overload không được xét theo khả năng chuyển đổi ở lúc gọi. Chẳng hạn mã Java có thể chọn một overload nhờ chuyển `int` thành `long`, nhưng `getDeclaredMethod("run", long.class, ...)` **không** tìm được `run(int, ...)`.

## 8. Xác định đúng lớp khai báo khi cần đi lên phân cấp

Khi `Child.class.getDeclaredField("id")` không tìm thấy field nằm ở `Parent`, việc đổi sang `getField("id")` chỉ giải quyết được nếu field ấy là `public`. Với field non-public, hãy tra cứu trên đúng kiểu khai báo như `Parent.class.getDeclaredField("id")` hoặc tự duyệt `getSuperclass()` theo chính sách nghiệp vụ. Phân biệt tương tự áp dụng cho method, trong khi constructor không được kế thừa.

Nếu lớp con che field cùng tên của lớp cha, hai `Field` vẫn tồn tại riêng; dùng `getDeclaringClass()` để biết chủ sở hữu. Việc tìm thấy metadata vẫn chưa bảo đảm được phép đọc/ghi/gọi: hãy kiểm tra quyền truy cập như ở mục 6. Chủ đề duyệt nhiều tầng và giải quyết xung đột được phát triển ở bài xây dựng chính sách tìm kiếm xuyên phân cấp.

## 9. Ngoại lệ và quy trình xử lý lỗi

| Thao tác | Tình huống | Cần xử lý |
| --- | --- | --- |
| `getDeclaredField("x")` | Không có field tên `x` trực tiếp trên kiểu | `NoSuchFieldException` (checked) |
| `getDeclaredMethod("m", ...)` | Sai tên hoặc sai kiểu/số lượng/thứ tự tham số | `NoSuchMethodException` (checked) |
| `getDeclaredConstructor(...)` | Không có constructor đúng chữ ký | `NoSuchMethodException` (checked) |
| `Field#get`, `Method#invoke`, `Constructor#newInstance` | Không có quyền truy cập thành viên | Có thể `IllegalAccessException` |
| `Method#invoke` | Chính method được gọi ném ngoại lệ | `InvocationTargetException` bọc ngoại lệ gốc; dùng `getCause()` |
| `Constructor#newInstance` | Constructor chạy và ném ngoại lệ | `InvocationTargetException` bọc ngoại lệ gốc |
| `setAccessible(true)` | JPMS hoặc giới hạn khác từ chối mở quyền | Có thể `InaccessibleObjectException` (runtime) |

Lỗi sai chữ ký thường nên được xử lý ở **bước tra cứu**, thay vì chỉ bắt ngoại lệ chung sau khi gọi. Ví dụ:

~~~java
try {
    Method method = type.getDeclaredMethod("process", String.class);
    // Kiểm tra modifiers, quyền truy cập, kiểu trả về trước khi invoke.
} catch (NoSuchMethodException e) {
    // Tên "process" hoặc chữ ký (String) không được khai báo tại type.
}
~~~

Đoạn trên giả sử biến `type` là một `Class<?>` hợp lệ. Trong chương trình lớn, nên ghi rõ tên lớp, tên thành viên và danh sách kiểu tham số trong thông báo lỗi (cẩn thận không log dữ liệu bí mật); điều này giúp phân biệt "không tìm thấy" với "tìm thấy nhưng không đủ quyền".

### Checklist khi tra cứu thất bại

1. **Đúng `Class<?>` chưa?** `getDeclared...` không tự đi lên lớp cha.
2. **Đúng chính tả và hoa/thường?** Tên thành viên phân biệt chữ hoa/chữ thường.
3. **Đúng số lượng/thứ tự/loại tham số?** `int.class`, `Integer.class`, `String.class` và `String[].class` là các kiểu khác nhau.
4. **Có constructor không tham số thật không?** `getDeclaredConstructor()` không tạo constructor mới.
5. **Lớp lồng không `static` có tham số lớp bao ngoài không?** Khám phá `getDeclaredConstructors()` và `getParameterTypes()`.
6. **Generic có bị xóa kiểu không?** `List<String>` trong tham số runtime tương ứng `List.class`.
7. **Đã tìm thấy nhưng không sử dụng được?** Kiểm tra `canAccess`, `trySetAccessible`, ranh giới module và ngoại lệ khi invoke/newInstance.

## 10. Mở rộng: metadata và các thành viên compiler sinh

Các đối tượng tìm được tiếp tục cung cấp thông tin hữu ích:

- `Field#getDeclaringClass()`, `Method#getDeclaringClass()` và `Constructor#getDeclaringClass()` xác định lớp/interface khai báo.
- `Method#getParameterTypes()` và `Constructor#getParameterTypes()` trả về dãy kiểu `Class<?>` theo chữ ký runtime. `getGenericParameterTypes()` cung cấp dạng `Type[]` để quan sát metadata generic.
- `Method#isBridge()` nhận biết bridge method do compiler sinh cho một số trường hợp generic/override; `Member#isSynthetic()` có thể giúp nhận biết thành viên do compiler tạo. Không suy ra rằng mọi thành viên xuất hiện trong Reflection đều là một khai báo viết tay trong nguồn.
- `Modifier.toString(member.getModifiers())` cho mô tả modifier. Ví dụ `private static`, `public final`; không nên đối chiếu modifier bằng phép so sánh chuỗi nếu chỉ cần kiểm tra một cờ cụ thể.

Đối với framework đọc metadata, một quy trình rõ ràng là: xác định `Class<?>` → lựa chọn `getDeclared...` hoặc `get...` theo phạm vi → dùng tên/chữ ký chính xác → kiểm tra loại và modifier → kiểm tra quyền truy cập → mới thao tác với giá trị hay gọi thành viên. Đừng mặc định thêm `setAccessible(true)` ở mọi nơi; thao tác này có thể thất bại hoặc không được phép theo ranh giới module.

## 11. Tự kiểm tra hiểu bài

**Câu hỏi 1.** Lớp `Child` kế thừa `public void ping()` từ `Parent` nhưng không override. `Child.class.getDeclaredMethod("ping")` thành công không?

**Đáp án:** Không; nó ném `NoSuchMethodException`. `Child.class.getMethod("ping")` có thể tìm phương thức `public` được kế thừa.

**Câu hỏi 2.** Vì sao `getDeclaredMethod("send", Integer.class)` không tìm được method khai báo `send(int)`?

**Đáp án:** Lookup so khớp `Class<?>` tham số chính xác, không áp dụng autoboxing; phải truyền `int.class`.

**Câu hỏi 3.** Nếu một class chỉ có constructor `private Item(String)`, phương thức nào tìm được constructor này?

**Đáp án:** `Item.class.getDeclaredConstructor(String.class)`. `getConstructor(String.class)` yêu cầu constructor `public`; `getDeclaredConstructor()` lại yêu cầu không có tham số.

**Câu hỏi 4.** Tìm được `private Field` bằng `getDeclaredField` đã có thể đọc giá trị ngay chưa?

**Đáp án:** Chưa chắc. Reflection lookup trả metadata; khi đọc field vẫn có quy tắc truy cập và JPMS. Chỉ thử `trySetAccessible()` khi thật sự cần và xử lý trường hợp bị từ chối.

**Câu hỏi 5.** Phương thức khai báo `void add(String... tags)` được tra cứu bằng những tham số nào?

**Đáp án:** `type.getDeclaredMethod("add", String[].class)` vì varargs được biểu diễn thành một tham số kiểu mảng ở chữ ký runtime.

**Ghi nhớ:** ba API `getDeclared...` trả lời câu hỏi *"Thành viên được khai báo trực tiếp trên kiểu này là gì?"*. Độ chính xác nằm ở tên field, tên cộng danh sách kiểu tham số method, hoặc danh sách kiểu tham số constructor. Sau lookup, hãy kiểm tra metadata và quyền truy cập trước khi làm việc với giá trị hoặc thực thi thành viên.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="member-lookup-004">Public member kế thừa khác member được khai báo tại chính lớp</a>

<details>
<summary>Xem chi tiết</summary>

# Public member kế thừa khác member được khai báo tại chính lớp

## 1. Hai câu hỏi khác nhau khi tra cứu member

Trong Java Reflection, **public** mô tả khả năng truy cập theo access modifier, còn **declared** mô tả **nơi member được khai báo**. Đây là hai tiêu chí khác nhau. Với một lớp con, có thể có member `public` được thừa kế từ lớp cha nhưng không xuất hiện trong tập member được khai báo trực tiếp tại lớp con.

Ví dụ, `Parent` khai báo phương thức `public void read()` và `Child extends Parent` không override `read()`. Khi nhìn từ `Child.class`:

- `getMethod("read")` tìm thấy phương thức `read` vì đây là phương thức public được thừa kế.
- `getDeclaredMethod("read")` ném `NoSuchMethodException` vì `Child` không khai báo phương thức đó.
- `getMethod("read").getDeclaringClass()` trả về `Parent.class`, cho biết lớp thực sự khai báo method.

**Quy tắc cốt lõi:** API không có chữ `Declared` dùng phép tra cứu công khai có xét thành viên thừa kế (đối với method/field); API có chữ `Declared` chỉ xét member khai báo tại `Class` đang khảo sát, bất kể public/protected/package-private/private. Quy tắc này cần áp dụng **đúng theo loại member**, vì constructor là trường hợp đặc biệt không được thừa kế.

## 2. Chọn phép tra cứu phù hợp với nơi khai báo

- **Cần public method/field đang nhìn thấy qua lớp con:** `getMethod`/`getMethods` hoặc `getField`/`getFields`, có thể bao gồm thành viên kế thừa từ lớp cha hoặc interface.
- **Cần đúng phần khai báo của lớp con, kể cả private:** `getDeclaredMethod`/`getDeclaredMethods` hoặc `getDeclaredField`/`getDeclaredFields`; kết quả không cộng thêm phần khai báo ở superclass.
- **Cần constructor:** `getConstructor`/`getConstructors` chỉ lấy constructor `public` của chính lớp; họ `getDeclaredConstructor`/`getDeclaredConstructors` lấy mọi mức truy cập của chính lớp. Constructor không kế thừa.

Đối với method, tra tên và danh sách **kiểu tham số chính xác**, không truyền kiểu trả về. Những API lấy một thành viên ném `NoSuchMethodException` hoặc `NoSuchFieldException` nếu không tìm được. Khi `getMethods()` có thêm method từ `Object` hoặc `getDeclaredMethods()` chứa bridge/synthetic methods, hãy xét `getDeclaringClass()` và các cờ metadata trước khi kết luận nguồn khai báo. Mảng kết quả không có thứ tự bảo đảm.

## 3. Ví dụ Java 17+ có thể biên dịch và chạy

Tạo tệp `MemberLookupDemo.java`:

~~~java
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

public class MemberLookupDemo {
    public static class Parent {
        public String inheritedField = "from parent";
        public void inheritedMethod() {}
        public void overridden() {}
        protected void protectedMethod() {}
        private void hiddenMethod() {}
    }

    public static class Child extends Parent {
        public int localField = 42;
        private String secret = "child secret";
        public void localMethod() {}
        @Override public void overridden() {}
        private void internalMethod() {}
    }

    private static boolean containsMethod(Method[] methods, String name) {
        return Arrays.stream(methods).anyMatch(m -> m.getName().equals(name));
    }

    private static boolean containsField(Field[] fields, String name) {
        return Arrays.stream(fields).anyMatch(f -> f.getName().equals(name));
    }

    public static void main(String[] args) throws Exception {
        Class<Child> type = Child.class;
        System.out.println("getMethod inherited: "
                + type.getMethod("inheritedMethod").getDeclaringClass().getSimpleName());
        System.out.println("getDeclaredMethod overridden: "
                + type.getDeclaredMethod("overridden").getDeclaringClass().getSimpleName());
        System.out.println("getMethods inherited: "
                + containsMethod(type.getMethods(), "inheritedMethod"));
        System.out.println("getDeclaredMethods inherited: "
                + containsMethod(type.getDeclaredMethods(), "inheritedMethod"));
        System.out.println("getDeclaredMethods private: "
                + containsMethod(type.getDeclaredMethods(), "internalMethod"));
        System.out.println("getField inherited: "
                + type.getField("inheritedField").getDeclaringClass().getSimpleName());
        System.out.println("getDeclaredFields inherited: "
                + containsField(type.getDeclaredFields(), "inheritedField"));
        System.out.println("getDeclaredFields private: "
                + containsField(type.getDeclaredFields(), "secret"));
        System.out.println("getMethods protected: "
                + containsMethod(type.getMethods(), "protectedMethod"));
    }
}
~~~

Biên dịch và chạy trong thư mục chứa tệp:

~~~bash
javac --release 17 MemberLookupDemo.java
java MemberLookupDemo
~~~

Kết quả chuẩn (đã kiểm tra với Java 21 và chế độ biên dịch Java 17):

~~~text
getMethod inherited: Parent
getDeclaredMethod overridden: Child
getMethods inherited: true
getDeclaredMethods inherited: false
getDeclaredMethods private: true
getField inherited: Parent
getDeclaredFields inherited: false
getDeclaredFields private: true
getMethods protected: false
~~~

**Giải thích từng nhóm kết quả:**

1. `inheritedMethod` được khai báo ở `Parent` và có modifier `public`. `Child` thừa kế nó nên `getMethod` và `getMethods` tìm được; `getDeclaredMethods` của `Child` không có.
2. `overridden` ban đầu ở `Parent` nhưng `Child` override bằng một method của chính mình. Vì vậy `Child.class.getDeclaredMethod("overridden")` thành công và `getDeclaringClass()` là `Child`. Một method có thể **vừa public vừa declared**.
3. `internalMethod` là `private` và được khai báo ở `Child`. `getDeclaredMethods` nhìn thấy metadata của nó, còn `getMethods` không đưa nó vào tập public.
4. `inheritedField` là field public khai báo ở `Parent`, nên `getField` từ `Child` tìm ra nhưng `getDeclaredFields` của `Child` không chứa nó. Field `secret` là private nhưng vẫn có trong `getDeclaredFields`.
5. `protectedMethod` không phải public nên không xuất hiện trong `getMethods()`; nó có thể được xem qua `Parent.class.getDeclaredMethod("protectedMethod")`.

Mảng Reflection không cam kết một thứ tự member cố định. Ví dụ trên kiểm tra **sự tồn tại theo tên**, không phụ thuộc vị trí phần tử; trong code thực tế, nếu cần trình bày có thứ tự thì hãy tự sắp xếp.

## 4. Phân biệt “thừa kế”, “được khai báo” và “có thể truy cập”

- **Inherited:** member của superclass hoặc interface được kế thừa theo quy tắc ngôn ngữ Java. Method/field public của cha thường là trường hợp dễ thấy nhất. Constructor **không bao giờ được kế thừa**.
- **Declared:** member có khai báo nằm trực tiếp trong kiểu đang được phản chiếu. Một method override tại `Child` là declared tại `Child`, dù cùng signature với method cha.
- **Accessible:** khả năng code hiện tại đọc, ghi hoặc invoke một member. Việc Reflection **liệt kê được** member private qua `getDeclared...` không đồng nghĩa code được phép sử dụng member đó.

Vì vậy, đừng suy từ `getDeclaredMethods()` rằng “đây là danh sách các method public”, và cũng đừng suy từ `getMethods()` rằng “toàn bộ method này được viết trong lớp đang xét”.

### Override và hiding thay đổi kết quả như thế nào?

- **Override instance method:** nếu `Child` khai báo method override public của `Parent`, method của `Child` nằm trong `getDeclaredMethods()`; dùng `getMethod(...)` thường cho method áp dụng ở `Child`. Quan sát `getDeclaringClass()` để xác định nguồn khai báo.
- **Field hiding:** nếu `Child` khai báo field trùng tên với field của `Parent`, đó là **hai field khác nhau**, không phải override field. `getDeclaredFields()` của từng lớp cho thấy field riêng; khi xử lý field trùng tên phải xét `getDeclaringClass()`, đừng giả định tên field là duy nhất trong cả hierarchy.
- **Static method hiding:** method static cũng có thể bị che khuất theo quy tắc Java; static method không tham gia polymorphic override như instance method.
- **Interface:** method public (kể cả default method phù hợp) có thể tham gia tập phương thức public của lớp triển khai, nhưng không vì thế trở thành member “declared” trực tiếp trong lớp.

Phân biệt này đặc biệt quan trọng với thư viện tạo JSON, mapper, dependency injection, framework quét annotation, hoặc công cụ sinh tài liệu: lựa chọn API tra cứu sẽ quyết định có nhìn thấy member từ superclass/interface hay không.

## 5. Khi cần toàn bộ member ở nhiều tầng kế thừa

`getDeclaredMethods()` không tự duyệt superclass. Nếu ứng dụng cần thống kê **mọi khai báo của từng lớp** (ví dụ private method ở lớp cha), duyệt lần lượt `getSuperclass()` và gọi `getDeclaredMethods()` trên từng lớp:

~~~java
for (Class<?> current = Child.class;
     current != null;
     current = current.getSuperclass()) {
    for (Method method : current.getDeclaredMethods()) {
        System.out.println(current.getSimpleName() + ": " + method.getName());
    }
}
~~~

Đây là **đoạn code minh họa dùng các lớp `Child`/`Parent` ở ví dụ trên**, không phải một chương trình độc lập. Nó duyệt chuỗi superclass; nếu cần cả khai báo thuộc interface, phải duyệt thêm `getInterfaces()` và xử lý các đường kế thừa trùng lặp. Nếu cần duy nhất các phương thức public của kiểu, `getMethods()` thường phù hợp và ngắn hơn.

Khi đọc metadata và lọc member, có thể dùng `Modifier.isPublic(member.getModifiers())` để kiểm tra modifier, `member.getDeclaringClass()` để tìm kiểu sở hữu, cùng `Method.isBridge()`/`isSynthetic()` hoặc `Field.isSynthetic()` để xử lý artifact do compiler tạo. Kiểm tra từng trường hợp thay vì suy đoán bằng tên hay số lượng phần tử.

## 6. Lưu ý về invoke và đóng gói module

`getDeclaredMethod("internalMethod")` có thể trả về đối tượng `Method` đại diện cho method private của `Child`. Tuy nhiên, thao tác `invoke(...)` vẫn phải tuân thủ kiểm tra quyền truy cập. `setAccessible(true)` không phải đường tắt được bảo đảm luôn thành công: Java Platform Module System có thể chặn deep reflection qua package chưa `opens`, dẫn đến `InaccessibleObjectException`. Hãy ưu tiên API public hợp lệ, chỉ mở truy cập khi thiết kế và quyền của module cho phép.

Đừng nhầm kết quả “không tìm thấy bằng `getMethod`” với “method không tồn tại”: method protected/private có thể tồn tại ở `getDeclaredMethods` của đúng lớp khai báo, còn method public kế thừa có thể tồn tại ở `getMethods` nhưng không ở `getDeclaredMethods` của lớp con.

## 7. Những lỗi thường gặp và cách chọn API

| Tình huống | Chọn API/cách xử lý |
| --- | --- |
| Cần method public mà object của `Child` có thể cung cấp, kể cả từ cha | `Child.class.getMethod(name, parameterTypes...)` hoặc `getMethods()` |
| Cần đúng method khai báo tại `Child`, dù private | `getDeclaredMethod(...)` hoặc `getDeclaredMethods()` |
| Cần field public được thừa kế từ cha | `getField(name)` hoặc `getFields()` |
| Cần field private của `Parent` | `Parent.class.getDeclaredField(name)`, kèm kiểm tra quyền nếu đọc/ghi |
| Cần toàn bộ khai báo từng lớp trong hierarchy | Duyệt `getSuperclass()`, gọi `getDeclaredFields()/getDeclaredMethods()` từng lớp; xử lý interface nếu cần |
| Cần constructor kế thừa của cha | Không có: constructor không kế thừa; tra cứu constructor trên đúng lớp muốn khởi tạo |
| Cần gọi method private sau khi tìm bằng Reflection | Kiểm tra access checks và giới hạn của module, không mặc định `setAccessible(true)` sẽ thành công |

**Tự kiểm tra:** `Child.class.getMethod("inheritedMethod")` thành công, nhưng `Child.class.getDeclaredMethod("inheritedMethod")` ném `NoSuchMethodException`; ngược lại `Child.class.getDeclaredMethod("internalMethod")` tìm thấy metadata method private nhưng `Child.class.getMethod("internalMethod")` không tìm được. Cả hai khác biệt đều xuất phát từ lựa chọn **public lookup** hay **declared-only lookup**, không phải mâu thuẫn của Reflection.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="member-lookup-005">Tra cứu một thành viên và liệt kê nhiều thành viên</a>

<details>
<summary>Xem chi tiết</summary>

# Tra cứu một thành viên và liệt kê nhiều thành viên bằng Java Reflection

## 1. Mục tiêu và phạm vi

Khi đã có đối tượng `Class<?>`, Java Reflection cho phép **tra cứu một thành viên theo tên/chữ ký** hoặc **liệt kê các thành viên** của một kiểu ngay tại thời điểm chạy. “Thành viên” ở đây chủ yếu gồm:

- **Field**: trường dữ liệu, được mô tả bằng `java.lang.reflect.Field`.
- **Method**: phương thức, được mô tả bằng `java.lang.reflect.Method`.
- **Constructor**: hàm tạo, được mô tả bằng `java.lang.reflect.Constructor`. Hàm tạo không được kế thừa và được tra cứu bằng API riêng.

Bài học tập trung vào **tìm và đọc metadata**, chưa cần gọi `Method.invoke()`, đọc/ghi `Field.get()/set()` hay tạo đối tượng bằng `Constructor.newInstance()`. Việc lấy được metadata của thành viên `private` **không đồng nghĩa** được phép truy cập hoặc thực thi thành viên đó.

Giả sử có `Class<?> type = SomeClass.class;`. Hai họ phương thức quan trọng nhất là:

- `getXxx(...)`: tìm **thành viên `public`**, trong nhiều trường hợp bao gồm thành viên `public` được kế thừa.
- `getDeclaredXxx(...)`: tìm thành viên **khai báo trực tiếp trong chính kiểu**, bất kể mức truy cập (`public`, `protected`, package-private, `private`), nhưng **không tự đi lên lớp cha**.

Lưu ý: khác biệt về kế thừa của constructor được nêu riêng bên dưới.

## 2. Bảng API tra cứu nhanh

| Nhu cầu | Tra cứu một thành viên | Liệt kê nhiều thành viên | Phạm vi |
| --- | --- | --- | --- |
| Phương thức `public` | `getMethod(name, parameterTypes...)` | `getMethods()` | `public` của kiểu và các phương thức `public` kế thừa/phơi bày qua phân cấp kiểu |
| Phương thức khai báo trực tiếp | `getDeclaredMethod(name, parameterTypes...)` | `getDeclaredMethods()` | Khai báo ngay trong kiểu; mọi mức truy cập |
| Trường `public` | `getField(name)` | `getFields()` | `public` của kiểu và trường `public` được kế thừa |
| Trường khai báo trực tiếp | `getDeclaredField(name)` | `getDeclaredFields()` | Khai báo ngay trong kiểu; mọi mức truy cập |
| Hàm tạo `public` | `getConstructor(parameterTypes...)` | `getConstructors()` | Chỉ constructor `public` của chính lớp |
| Hàm tạo khai báo trực tiếp | `getDeclaredConstructor(parameterTypes...)` | `getDeclaredConstructors()` | Constructor của chính lớp; mọi mức truy cập |

Chữ `Declared` nghĩa là **được khai báo ở kiểu đang xét**, không phải “cho phép truy cập riêng tư”. Với `getMethod()`/`getMethods()`, phương thức `public` từ superclass, interface hoặc `Object` có thể xuất hiện; `getDeclaredMethods()` không tự kèm chúng. Khi liệt kê, không được mặc định rằng phần tử đầu tiên là thành viên mong muốn: **thứ tự trả về không được bảo đảm**.

### 2.1. Vì sao phương thức cần cả kiểu tham số?

Java cho phép overload:

```java
void process(String text) {}
void process(int count) {}
```

Muốn tra cứu chính xác phải chỉ ra chữ ký tham số:

```java
Method byText = type.getDeclaredMethod("process", String.class);
Method byCount = type.getDeclaredMethod("process", int.class);
```

Đối số sau tên phương thức là các đối tượng `Class<?>` của **kiểu tham số theo đúng thứ tự**. Không truyền kiểu trả về: `getDeclaredMethod`/`getMethod` chỉ nhận tên và kiểu tham số. Phương thức không có tham số được tìm bằng `getDeclaredMethod("run")` hoặc `getDeclaredMethod("run", new Class<?>[0])`.

Tra cứu dùng kiểu chính xác ở mức reflection: `int.class` khác `Integer.class`; không tự boxing, unboxing hay widening khi **tìm chữ ký**. Với `String...`, chữ ký runtime là `String[]`, nên dùng `String[].class`. Với tham số `List<String>`, do type erasure, phần tham số dùng để tìm thường là `List.class`, không có `List<String>.class`.

### 2.2. Field và constructor khác method ở đâu?

- `getField("id")` và `getDeclaredField("id")` tra cứu theo **tên trường**, không có danh sách kiểu tham số. Trường có thể che khuất trường cùng tên ở lớp cha; kiểm tra `Field.getDeclaringClass()` để biết trường thực sự thuộc lớp nào.
- `getConstructor(String.class)` chỉ tìm constructor `public`; `getDeclaredConstructor(String.class)` tìm constructor được khai báo ngay trong lớp, kể cả `private`. Không có “constructor kế thừa” nên cả hai API đều không đi lên superclass.
- Nếu `Class` biểu diễn interface, primitive hoặc array, tập constructor có thể rỗng; không giả định mọi `Class` đều có constructor không tham số.

## 3. Ví dụ đầy đủ: tìm một thành viên và liệt kê nhiều thành viên

Lưu đoạn sau vào tệp `MemberLookupDemo.java`. Ví dụ cố ý có kế thừa, overload, field `private` và constructor `private` để so sánh hai họ API.

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

public class MemberLookupDemo {
    static class Parent {
        public int id = 10;
        protected void protectedHook() {}
        public void inheritedAction() {}
    }

    static class Child extends Parent {
        public String label;
        private int counter;

        public Child() {}
        private Child(int counter) { this.counter = counter; }

        public String show(String prefix) { return prefix + label; }
        public String show(int times) { return label + times; }
        private void reset() { counter = 0; }
    }

    static String params(Class<?>[] types) {
        return Arrays.stream(types)
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", "));
    }

    public static void main(String[] args)
            throws NoSuchMethodException, NoSuchFieldException {
        Class<Child> type = Child.class;

        // Tra cứu một thành viên theo tên/chữ ký chính xác.
        Method one = type.getDeclaredMethod("show", String.class);
        Field field = type.getDeclaredField("counter");
        Constructor<Child> constructor = type.getDeclaredConstructor(int.class);

        System.out.println("one method: " + one.getName() + "("
                + params(one.getParameterTypes()) + ") -> "
                + one.getReturnType().getSimpleName());
        System.out.println("one field: " + field.getName()
                + ": " + field.getType().getSimpleName());
        System.out.println("one constructor: "
                + constructor.getDeclaringClass().getSimpleName() + "("
                + params(constructor.getParameterTypes()) + ")");

        // Thành viên public của lớp cha tìm được qua getMethod/getField.
        System.out.println("inherited field owner: "
                + type.getField("id").getDeclaringClass().getSimpleName());
        System.out.println("inherited method owner: "
                + type.getMethod("inheritedAction")
                      .getDeclaringClass().getSimpleName());

        // Liệt kê và sắp xếp để kết quả không phụ thuộc thứ tự của Reflection.
        System.out.println("declared fields: "
                + Arrays.stream(type.getDeclaredFields())
                        .map(Field::getName).sorted().toList());
        System.out.println("public fields: "
                + Arrays.stream(type.getFields())
                        .map(Field::getName).sorted().toList());
        System.out.println("declared methods: "
                + Arrays.stream(type.getDeclaredMethods())
                        .map(Method::getName).distinct().sorted().toList());
        System.out.println("public constructors: "
                + Arrays.stream(type.getConstructors())
                        .map(c -> c.getDeclaringClass().getSimpleName()
                                + "(" + params(c.getParameterTypes()) + ")")
                        .sorted().toList());
        System.out.println("declared constructors: "
                + Arrays.stream(type.getDeclaredConstructors())
                        .map(c -> c.getDeclaringClass().getSimpleName()
                                + "(" + params(c.getParameterTypes()) + ")")
                        .sorted().toList());

        // Declared không duyệt lên superclass.
        try {
            type.getDeclaredMethod("inheritedAction");
        } catch (NoSuchMethodException ex) {
            System.out.println("declared inheritedAction: not found");
        }
    }
}
```

Biên dịch/chạy bằng JDK 17 trở lên:

```bash
javac MemberLookupDemo.java
java MemberLookupDemo
```

Kết quả chuẩn (đã kiểm tra bằng JDK 21):

```text
one method: show(String) -> String
one field: counter: int
one constructor: Child(int)
inherited field owner: Parent
inherited method owner: Parent
declared fields: [counter, label]
public fields: [id, label]
declared methods: [reset, show]
public constructors: [Child()]
declared constructors: [Child(), Child(int)]
declared inheritedAction: not found
```

**Giải thích kết quả:**

1. `getDeclaredMethod("show", String.class)` chọn đúng overload nhận `String`; overload nhận `int` là một `Method` khác. Việc lấy `getName()` khi liệt kê rồi gọi `distinct()` chỉ dùng để trình bày **tên duy nhất**; thao tác đó **không thể hiện được hai chữ ký overload**.
2. `getDeclaredField("counter")` thấy trường `private` trong `Child`. `getFields()` chỉ cho ra `id` (`public` từ `Parent`) và `label` (`public` ở `Child`).
3. `getDeclaredConstructor(int.class)` tìm được constructor `private`. `getConstructors()` chỉ có `Child()` vì constructor đó `public`.
4. `getMethod("inheritedAction")` thành công vì phương thức được khai báo `public` ở `Parent`; `getDeclaredMethod("inheritedAction")` trên `Child.class` ném `NoSuchMethodException` vì `Child` không khai báo phương thức ấy.
5. Việc `getDeclaredMethods()` thấy `reset` không trao quyền chạy phương thức `private`. Ví dụ chỉ xem metadata. `Parent.protectedHook()` không xuất hiện trong `Child.getMethods()` vì không phải `public`, cũng không nằm trong `Child.getDeclaredMethods()` vì khai báo ở lớp cha.

## 4. Đọc metadata sau khi đã tìm được thành viên

Các đối tượng `Field`, `Method`, `Constructor` cho biết **tên, kiểu, nơi khai báo, modifier và chữ ký**:

| Đối tượng | API nên biết | Ý nghĩa |
| --- | --- | --- |
| `Field` | `getName()`, `getType()`, `getDeclaringClass()`, `getModifiers()` | Tên trường, kiểu trường, lớp/interface khai báo, cờ truy cập/đặc tính |
| `Method` | `getName()`, `getParameterTypes()`, `getReturnType()`, `getDeclaringClass()`, `getModifiers()` | Tên, danh sách kiểu tham số, kiểu trả về, nơi khai báo, modifier |
| `Constructor<?>` | `getParameterTypes()`, `getDeclaringClass()`, `getModifiers()` | Chữ ký tham số, lớp sở hữu và modifier của constructor |
| Cả ba | `isSynthetic()` | Có thể là thành viên do compiler sinh ra; phải hiểu mục tiêu liệt kê trước khi lọc |

Ví dụ, sau khi có `Method one` và `Field field` trong chương trình trên:

```java
System.out.println(one.getDeclaringClass().getSimpleName()); // Child
System.out.println(field.getType() == int.class);            // true
System.out.println(java.lang.reflect.Modifier.isPrivate(
        field.getModifiers()));                               // true
```

Đoạn ngắn này **được chèn vào trong `main` của ví dụ trước**, không phải chương trình Java độc lập. `getParameterTypes()` trả về mảng `Class<?>[]` theo thứ tự khai báo; với phương thức generic, có thể dùng thêm `getGenericParameterTypes()` khi cần thông tin khai báo generic (không được bảo đảm phản ánh kiểu cụ thể sau erasure).

Để hiển thị **đầy đủ chữ ký từng overload** khi liệt kê, thay vì `map(Method::getName).distinct()`, dùng biểu diễn gồm cả tham số:

```java
Arrays.stream(type.getDeclaredMethods())
        .filter(m -> m.getName().equals("show"))
        .map(m -> m.getName() + "(" + params(m.getParameterTypes()) + ")")
        .sorted()
        .forEach(System.out::println);
```

Đoạn này cũng đặt trong `main` của chương trình trước; kết quả:

```text
show(String)
show(int)
```

Ở đây dùng `sorted()` vì reflection không cam kết thứ tự của các phương thức. Đối với công cụ tài liệu/debug, nên hiển thị `getDeclaringClass()`, các kiểu tham số và modifier thay vì chỉ tên.

## 5. Tìm thành viên kế thừa nhưng không public

Khi cần tìm một trường hoặc phương thức `protected`/package-private/`private` nằm trên một superclass, `Child.class.getDeclaredXxx(...)` **không tự tìm lớp cha**; `getXxx(...)` cũng không tìm thành viên không `public`. Nếu nghiệp vụ thực sự yêu cầu duyệt superclass, thực hiện vòng lặp tường minh.

Ví dụ tiện ích tìm **field khai báo ở lớp gần nhất** trong chuỗi superclass:

```java
static Field findFieldInHierarchy(Class<?> start, String name)
        throws NoSuchFieldException {
    for (Class<?> current = start; current != null;
         current = current.getSuperclass()) {
        try {
            return current.getDeclaredField(name);
        } catch (NoSuchFieldException ignored) {
            // Chưa được khai báo ở lớp này: tiếp tục lên superclass.
        }
    }
    throw new NoSuchFieldException(name);
}
```

Đây là **phương thức tiện ích**, cần đặt trong một lớp Java và import `java.lang.reflect.Field`. Nếu gọi `findFieldInHierarchy(Child.class, "id")`, nó trả về `Field` được khai báo ở `Parent`; nếu gọi với `"counter"`, nó chọn trường ở `Child`. Vòng lặp này chỉ xét **chuỗi superclass** và không tìm field khai báo trong các interface; đó là phạm vi chủ ý của ví dụ.

Với method, có thể dùng tương tự `getDeclaredMethod(name, parameterTypes)` ở từng superclass khi cần kiểm tra khai báo cụ thể. Tuy nhiên **method của interface không nằm trong chuỗi `getSuperclass()`**; muốn quét đầy đủ interface và các superinterface cần duyệt riêng `getInterfaces()`. Trường hợp đó thường nên bắt đầu bằng `getMethods()` nếu chỉ quan tâm API `public`.

Cũng phải phân biệt: **tìm metadata theo phân cấp** và **quyết định phương thức nào sẽ chạy khi dispatch động** là hai vấn đề khác nhau. `getDeclaringClass()` nói nơi đối tượng metadata được khai báo; không thay thế việc hiểu overriding, hiding hoặc quyền truy cập.

## 6. Những tình huống dễ nhầm

### 6.1. Không tìm thấy member không nhất thiết là member không tồn tại

- `getMethod("reset")` trên `Child.class` thất bại vì `reset` là `private`; `getDeclaredMethod("reset")` tìm thấy.
- `getDeclaredMethod("inheritedAction")` thất bại trên `Child.class` vì method nằm ở superclass; `getMethod("inheritedAction")` tìm thấy.
- `getConstructor(int.class)` thất bại trên `Child.class` vì constructor nhận `int` là `private`; `getDeclaredConstructor(int.class)` tìm thấy.
- `getDeclaredMethod("show", Integer.class)` thất bại vì overload tương ứng nhận `int`, không phải `Integer`.

Các phương thức tìm một thành viên thường ném `NoSuchMethodException` hoặc `NoSuchFieldException` khi không tìm được. Nếu không có member là trường hợp bình thường, hãy bắt ngoại lệ **đúng phạm vi** hoặc trả về `Optional` từ một hàm tiện ích; tránh bắt tràn lan `Exception` rồi che lỗi lập trình.

### 6.2. `getDeclared*` không có nghĩa là được tự do dùng `private`

Reflection có thể **phát hiện** member không public, nhưng hành động đọc/ghi/gọi member phải qua kiểm tra quyền truy cập riêng. Trên Java 9+ với Java Platform Module System, việc cố `setAccessible(true)` có thể bị từ chối (`InaccessibleObjectException`), ngay cả khi `getDeclaredField()` đã trả về `Field`. `trySetAccessible()` cũng có thể trả `false`. Vì vậy không thiết kế chương trình với giả định “tìm thấy là gọi được”; khi làm thư viện/framework, ưu tiên API public hoặc cơ chế `opens` có chủ đích.

### 6.3. Compiler có thể sinh thêm member

Trong chương trình thực, `getDeclaredMethods()` có thể thấy **bridge method** do generics/covariant return sinh ra, hoặc method/field synthetic. `Method.isBridge()` và `Member.isSynthetic()` giúp phân biệt. Nếu đang tạo bảng tài liệu hướng đến developer, có thể cân nhắc lọc; nếu đang phân tích runtime/bytecode, không nên lọc tùy tiện.

Không coi số phần tử, thứ tự, hay biểu diễn `toString()` của các `Member` là giao diện ổn định cho kiểm thử. Cách an toàn hơn là lấy thông tin có cấu trúc, sắp xếp và so sánh đúng trường cần thiết. Constructor có thể không tồn tại theo chữ ký mong muốn; **Java không tự sinh constructor không tham số** khi lớp đã khai báo constructor khác.

### 6.4. Khác nhau giữa khai báo và kế thừa

- `getDeclaredFields()` không có field ở superclass; `getFields()` chỉ bao gồm field `public` được hiển thị theo quan hệ kiểu.
- `getDeclaredMethods()` không tự có method ở superclass/interface; `getMethods()` phục vụ việc khám phá method `public` và có thể bao gồm method từ `Object`.
- Constructor không được kế thừa, nên `getConstructors()` không bao giờ có constructor của `Parent` khi gọi trên `Child.class`.
- Tên method không duy nhất khi có overload; khi phân loại, hãy dùng **tên + kiểu tham số**, bổ sung kiểu trả về/chủ sở hữu khi kiểm tra các trường hợp cầu nối hoặc runtime phức tạp.

## 7. Chọn API đúng trong thực tế

| Tình huống | Lựa chọn khởi đầu | Vì sao |
| --- | --- | --- |
| Framework cần tìm method `public` có tên và chữ ký xác định trên object | `type.getMethod(name, parameterTypes)` | Chấp nhận method `public` kế thừa |
| Công cụ audit cần xem mọi field khai báo trong một lớp | `type.getDeclaredFields()` | Bao gồm non-public của đúng lớp |
| Debug danh sách method được khai báo trực tiếp | `type.getDeclaredMethods()` | Không lẫn toàn bộ method `public` kế thừa |
| Kiểm tra factory constructor `public` | `type.getConstructor(parameterTypes)` | Chỉ constructor `public` |
| Xem các constructor không public để phân tích metadata | `type.getDeclaredConstructors()` | Không yêu cầu đã được cấp quyền gọi chúng |
| Tìm trường khai báo ở superclass, bất kể modifier | Duyệt `getSuperclass()` + `getDeclaredField()` | Cần kiểm soát chính xác phạm vi duyệt |

Khi đã biết kiểu tại compile time và chỉ cần gọi method thông thường, gọi trực tiếp thường đơn giản, an toàn kiểu hơn và dễ bảo trì hơn. Reflection hữu ích nhất khi tên/chữ ký được quyết định tại runtime, chẳng hạn mapper, serializer, container, plugin loader, test/discovery tool và công cụ introspection. Với lượt tra cứu lặp rất nhiều, có thể lưu cache `Method`/`Field` theo lớp và chữ ký; cần tính đến vòng đời của `ClassLoader` để tránh giữ class không cần thiết.

## 8. Tự kiểm tra

1. `Child.class.getDeclaredField("id")` và `Child.class.getField("id")` khác nhau thế nào? **Đáp án:** lời gọi đầu ném `NoSuchFieldException` vì `id` khai báo ở `Parent`; lời gọi sau trả về field `public` kế thừa.
2. Vì sao `Child.class.getDeclaredMethod("show")` thất bại dù trong lớp có hai method `show`? **Đáp án:** cả hai đều nhận một tham số; lời gọi đang yêu cầu method `show()` không tham số.
3. Làm sao lấy overload `show(int)`? **Đáp án:** `Child.class.getDeclaredMethod("show", int.class)`.
4. Vì sao `Child.class.getConstructors()` chỉ có một phần tử trong ví dụ? **Đáp án:** chỉ `Child()` là `public`; `Child(int)` là `private`, và constructor của `Parent` không kế thừa.
5. Nên dùng API nào khi muốn đếm các method khai báo ngay trong lớp, kể cả `private`? **Đáp án:** `getDeclaredMethods()`; nếu đếm theo nghiệp vụ, xem xét synthetic/bridge và overload trước khi thống kê.

**Ghi nhớ:** Trước khi tra cứu một thành viên, hãy trả lời ba câu hỏi: **(1)** cần method, field hay constructor; **(2)** chỉ cần `public` hay mọi mức truy cập; **(3)** cần cả thành viên kế thừa hay chỉ phần khai báo trực tiếp. Ba quyết định đó dẫn đến đúng cặp API `get...`/`getDeclared...` và giúp tránh phần lớn lỗi `NoSuchMethodException`/`NoSuchFieldException`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="member-lookup-006">Tên, kiểu tham số và điều kiện khớp chính xác khi tra cứu</a>

<details>
<summary>Xem chi tiết</summary>

# Tra cứu thành viên bằng tên và kiểu tham số chính xác trong Java Reflection

Khi dùng Reflection, chương trình có thể tìm phương thức (`Method`), constructor (`Constructor`) hoặc trường (`Field`) từ đối tượng `Class<?>` tại thời điểm chạy. Điểm mấu chốt của việc **tra cứu chính xác** là phải truyền đúng tên thành viên, đúng số lượng và thứ tự kiểu tham số, đồng thời chọn API phù hợp với phạm vi hiển thị. Java Reflection **không tự chọn overload theo cách trình biên dịch Java chọn khi gọi phương thức**.

## 1. Những API nào dùng tên và kiểu tham số?

| Thành viên cần tìm | Chỉ lấy `public` (kể cả được kế thừa khi áp dụng) | Chỉ xét thành viên khai báo ngay trong lớp (mọi mức truy cập) | Tiêu chí tra cứu |
| --- | --- | --- | --- |
| Phương thức | `getMethod(String name, Class<?>... parameterTypes)` | `getDeclaredMethod(String name, Class<?>... parameterTypes)` | Tên + danh sách kiểu tham số |
| Constructor | `getConstructor(Class<?>... parameterTypes)` | `getDeclaredConstructor(Class<?>... parameterTypes)` | Danh sách kiểu tham số; không truyền tên |
| Trường | `getField(String name)` | `getDeclaredField(String name)` | Chỉ tên trường; không truyền kiểu tham số |

Ví dụ, nếu `UserService` khai báo `public User find(String key, int limit)`:

```java
Method method = UserService.class.getMethod(
        "find", String.class, int.class);
```

Hai `Class` trong danh sách phải tương ứng **chính xác** với kiểu của tham số thứ nhất và thứ hai. Giá trị truyền vào là đối tượng mô tả kiểu (ví dụ `int.class`), không phải đối tượng tham số thực tế (ví dụ `42`). Trường hợp phương thức không có tham số, chỉ cần `getMethod("name")`.

## 2. Điều kiện khớp của phương thức

Một lời gọi `getMethod` hoặc `getDeclaredMethod` chỉ khớp khi đồng thời thỏa các điều kiện sau:

1. **Tên đúng hoàn toàn:** `"find"` khác `"Find"`; tên phân biệt chữ hoa và chữ thường.
2. **Đúng số lượng tham số:** `find(String)` khác `find(String, int)`.
3. **Đúng thứ tự:** `find(String, int)` khác `find(int, String)`.
4. **Đúng từng kiểu `Class`:** `int.class` khác `Integer.class`; `long.class` khác `int.class`; `Object.class` khác `String.class`. Không có tự động boxing/unboxing, mở rộng kiểu số hay xét quan hệ cha–con **ở bước tra cứu**.
5. **Đúng phạm vi API:** `getMethod` chỉ tìm phương thức `public` thích hợp, kể cả phương thức công khai được kế thừa; `getDeclaredMethod` tìm phương thức do chính lớp đó khai báo, kể cả `private`, nhưng **không tìm phương thức chỉ được kế thừa**.

Tên và kiểu tham số là khóa tra cứu; **kiểu trả về không được truyền** cho `getMethod`. Sau khi tìm được, dùng `method.getReturnType()` để kiểm tra kiểu trả về nếu nghiệp vụ cần. Tương tự, các modifier như `static`, `final` hoặc annotation không nằm trong tham số tra cứu; kiểm tra chúng sau bằng `getModifiers()`, `Modifier` hoặc `getAnnotations()`.

Ví dụ, với chữ ký `void process(int id, String name)`:

| Lời gọi | Kết quả |
| --- | --- |
| `getMethod("process", int.class, String.class)` | Khớp nếu phương thức `public` thuộc phạm vi tìm kiếm |
| `getMethod("process", String.class, int.class)` | `NoSuchMethodException`: sai thứ tự |
| `getMethod("process", Integer.class, String.class)` | `NoSuchMethodException`: `Integer` không phải `int` |
| `getMethod("process", int.class)` | `NoSuchMethodException`: thiếu một tham số |
| `getMethod("Process", int.class, String.class)` | `NoSuchMethodException`: sai tên |

Lưu ý: khi **gọi** một `Method` đã tìm được bằng `invoke`, Java có thể thực hiện một số chuyển đổi giá trị tham số theo quy tắc riêng của Reflection. Việc đó không làm cho `getMethod` chấp nhận chữ ký gần đúng.

## 3. Ví dụ Java 17+ chạy độc lập

Lưu toàn bộ mã dưới đây vào tệp `ExactMemberLookupDemo.java`:

```java
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

public class ExactMemberLookupDemo {
    public static class Parent {
        public String inherited(String value) {
            return "parent:" + value;
        }

        public String code = "P";
    }

    public static class Example extends Parent {
        public Example() {}

        private Example(int size) {}

        public String combine(String text, int count) {
            return text + "-" + count;
        }

        public String combine(int value) {
            return "#" + value;
        }

        private void internal(long id) {}

        public void publish(String... labels) {}

        public void save(List<String> names) {}

        private String token = "secret";
    }

    public static void main(String[] args) throws Exception {
        Class<Example> type = Example.class;
        Example example = new Example();

        Method exact = type.getMethod(
                "combine", String.class, int.class);
        System.out.println("exact=" + exact.getName()
                + Arrays.toString(exact.getParameterTypes()));
        System.out.println("invoke=" + exact.invoke(example, "A", 2));
        System.out.println("overload=" + type.getMethod(
                "combine", int.class).invoke(example, 7));

        System.out.println("inherited=" + type.getMethod(
                "inherited", String.class)
                .getDeclaringClass().getSimpleName());
        System.out.println("declaredPrivate=" + Modifier.isPrivate(
                type.getDeclaredMethod(
                        "internal", long.class).getModifiers()));

        System.out.println("varargs=" + type.getMethod(
                "publish", String[].class)
                .getParameterTypes()[0].getTypeName());
        System.out.println("erased=" + type.getMethod(
                "save", List.class).getName());

        System.out.println("ctorPublic=" + type.getConstructor()
                .getParameterCount());
        System.out.println("ctorPrivate=" + Modifier.isPrivate(
                type.getDeclaredConstructor(int.class).getModifiers()));

        System.out.println("fieldInherited=" + type.getField("code")
                .getDeclaringClass().getSimpleName());
        System.out.println("fieldPrivate=" + Modifier.isPrivate(
                type.getDeclaredField("token").getModifiers()));

        try {
            type.getMethod("combine", String.class, Integer.class);
        } catch (NoSuchMethodException e) {
            System.out.println("wrapperMismatch=NoSuchMethodException");
        }

        try {
            type.getDeclaredMethod("inherited", String.class);
        } catch (NoSuchMethodException e) {
            System.out.println("declaredNotInherited=NoSuchMethodException");
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac ExactMemberLookupDemo.java
java ExactMemberLookupDemo
```

Kết quả chuẩn:

```text
exact=combine[class java.lang.String, int]
invoke=A-2
overload=#7
inherited=Parent
declaredPrivate=true
varargs=java.lang.String[]
erased=save
ctorPublic=0
ctorPrivate=true
fieldInherited=Parent
fieldPrivate=true
wrapperMismatch=NoSuchMethodException
declaredNotInherited=NoSuchMethodException
```

Diễn giải: `combine` có hai overload nên danh sách `Class` xác định chính xác overload cần tìm. Phương thức `inherited` được kế thừa từ `Parent`, vì vậy `getMethod` tìm được còn `Example.class.getDeclaredMethod` thì không. Constructor `Example(int)` và phương thức `internal(long)` là `private` nhưng vẫn có thể **tìm metadata** bằng API `getDeclared...`. `code` là trường `public` được kế thừa, còn `token` được khai báo `private` ngay trong `Example`.

## 4. Những kiểu tham số thường gây nhầm lẫn

### Primitive và wrapper

`int.class` là đối tượng `Class` của primitive `int`; `Integer.class` là đối tượng `Class` của lớp bao `java.lang.Integer`. Chúng khác nhau ngay cả khi một lời gọi Java thông thường có thể tự boxing:

```java
// Phương thức được khai báo: void setCount(int count)
type.getMethod("setCount", int.class);      // Đúng nếu public.
type.getMethod("setCount", Integer.class);  // NoSuchMethodException.
```

Tương tự, `byte`, `short`, `int`, `long`, `float` và `double` không thể hoán đổi khi tra cứu. `Object.class` cũng không thay thế được `String.class` chỉ vì `String` là con của `Object`.

### Mảng và varargs

Ở cấp chữ ký, tham số khai báo `String... names` có kiểu thực là `String[]`; phương thức `process(int[] values)` có tham số `int[].class`:

```java
getMethod("publish", String[].class); // public void publish(String... labels)
getMethod("sum", int[].class);        // public int sum(int[] values)
```

Đây là minh họa chữ ký, không phải chương trình độc lập: hai phương thức tương ứng phải tồn tại trên `Class` đang tra cứu. `getMethod("publish", String.class)` không khớp với varargs; Reflection không tự trải rộng hay gom các kiểu tham số tại bước tìm.

### Generic và type erasure

Java thường xóa đối số kiểu generic trong chữ ký phương thức ở runtime. Với `void save(List<String> names)`, phải tra cứu bằng `List.class`, **không có** cú pháp `List<String>.class`:

```java
Method save = Example.class.getMethod("save", List.class);
```

`save.getParameterTypes()` trả về kiểu đã erasure; `save.getGenericParameterTypes()` có thể cung cấp thêm thông tin generic được lưu trong metadata. Với type variable như `T`, kiểu dùng để tra cứu là kiểu sau erasure (thường là `Object` hoặc bound đầu tiên).

### Lớp được nạp bởi ClassLoader khác nhau

Hai lớp có cùng tên đầy đủ nhưng do hai class loader khác nhau định nghĩa có thể là **hai kiểu runtime khác nhau**. Vì việc đối chiếu kiểu tham số dựa trên danh tính `Class`, chỉ so tên từ `getName()` là chưa đủ để khẳng định chữ ký sẽ khớp.

## 5. Public, declared, kế thừa và quyền truy cập

`getMethod` và `getField` xét thành viên `public`, bao gồm thành viên công khai được kế thừa theo quy tắc Java. `getDeclaredMethod` và `getDeclaredField` xét thành viên khai báo trực tiếp trong lớp đang cầm `Class`, bất kể `public`, `protected`, package-private hay `private`; chúng **không duyệt lên superclass**. Nếu muốn tìm thành viên `private` khai báo trong superclass, phải lấy `Class` của superclass đó (hoặc tự duyệt chuỗi `getSuperclass()` khi bài toán đòi hỏi).

Constructor không được kế thừa. `getConstructor(int.class)` đòi constructor `public` với đúng một tham số `int`; `getDeclaredConstructor(int.class)` xét constructor do lớp khai báo ở mọi mức truy cập. Không truyền tên lớp khi tìm constructor. Constructor mặc định không phải lúc nào cũng tồn tại: khi lớp khai báo constructor khác, Java không tự sinh constructor không tham số.

**Tra cứu được không đồng nghĩa với gọi hoặc đọc/ghi được.** Ví dụ, tìm `private Method` bằng `getDeclaredMethod` không tự cấp quyền gọi `method.invoke(...)`. Java có thể ném `IllegalAccessException` khi truy cập; `setAccessible(true)` cũng có thể bị từ chối, đặc biệt ở ranh giới module (ví dụ `InaccessibleObjectException`). Không lạm dụng mở truy cập để che đi thiết kế API chưa phù hợp.

## 6. Xử lý lỗi và kiểm tra chữ ký thực tế

Các API tra cứu đơn lẻ không trả về `null` khi không tìm thấy:

- `getMethod`, `getDeclaredMethod`, `getConstructor` và `getDeclaredConstructor` ném `NoSuchMethodException`.
- `getField` và `getDeclaredField` ném `NoSuchFieldException`.
- Một số đầu vào không hợp lệ (ví dụ tên `null` hoặc phần tử kiểu tham số `null`) có thể gây `NullPointerException`; kiểm tra đầu vào trước khi tra cứu động.
- Ràng buộc bảo mật hoặc module có thể gây lỗi khác tại thời điểm tìm hoặc sử dụng thành viên; cần đọc loại ngoại lệ thực tế.

Nếu nhận `NoSuchMethodException`, hãy kiểm tra lần lượt: đối tượng `Class` đang tra cứu có đúng không; tên có đúng ký tự/case; số lượng, thứ tự và từng kiểu có đúng không; đã phân biệt primitive/wrapper, mảng/varargs và erasure; sau cùng chọn đúng `getMethod` hay `getDeclaredMethod`. Khi cần chẩn đoán, có thể liệt kê chữ ký từ chính lớp mục tiêu:

```java
for (Method method : Example.class.getDeclaredMethods()) {
    System.out.println(method.getName()
            + Arrays.toString(method.getParameterTypes()));
}
```

Đoạn trên là mã minh họa đặt trong `main` của ví dụ và dùng `java.lang.reflect.Method` cùng `java.util.Arrays`. Thứ tự do `getDeclaredMethods()` trả về **không được bảo đảm**, nên không dựa vào thứ tự in để viết logic hoặc test. Các phương thức tổng hợp (`synthetic`) hoặc `bridge` do compiler tạo có thể xuất hiện trong kết quả; có thể xem `isSynthetic()` và `isBridge()` để hiểu chúng. Khi thao tác với bytecode đặc biệt có nhiều phương thức cùng tên và tham số nhưng khác kiểu trả về, đừng giả định API tìm một phương thức theo kiểu trả về vì API không nhận tiêu chí đó.

## 7. Tình huống áp dụng và nguyên tắc ghi nhớ

Giả sử framework đọc tên phương thức và các kiểu tham số từ cấu hình, ví dụ `run(String, int)`. Quy trình đáng tin cậy là phân giải **từng tên kiểu** thành đúng đối tượng `Class` (primitive phải xử lý riêng), quyết định tìm `public` hay thành viên khai báo trực tiếp, gọi `getMethod("run", String.class, int.class)`, sau đó mới kiểm tra return type, modifier, quyền truy cập và thực hiện `invoke` nếu được phép. Nếu lấy kiểu bằng `args[i].getClass()`, hãy cẩn thận: giá trị `Integer` không tương đương tham số khai báo `int`; giá trị `null` cũng không có `getClass()`. Cấu hình nên mô tả **chữ ký khai báo**, không suy đoán chữ ký từ các giá trị truyền vào.

Cần nhớ ba quy tắc cốt lõi: **phương thức = tên + dãy kiểu tham số chính xác; constructor = dãy kiểu tham số chính xác; trường = tên chính xác**. Sau đó chọn đúng phạm vi `public`/`declared` và phân biệt bước **tìm thành viên** với bước **truy cập hoặc gọi thành viên**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="member-lookup-007">Vì sao getMethod không tự giải overload như trình biên dịch</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao `getMethod` không tự giải overload như trình biên dịch?

## 1. Hai thao tác có mục đích khác nhau

**Overload (nạp chồng phương thức)** là nhiều phương thức cùng tên nhưng khác danh sách kiểu tham số. Với lời gọi `obj.send(value)` trong mã Java, **trình biên dịch** phải xác định một overload thích hợp dựa trên kiểu *tại thời điểm biên dịch* của đối số và quy tắc của ngôn ngữ.

Ngược lại, `Class.getMethod(String name, Class<?>... parameterTypes)` là API Reflection để **tra cứu một phương thức public có tên và danh sách kiểu tham số khai báo được chỉ rõ**. Những `Class<?>` truyền vào mô tả chữ ký phương thức **cần tìm**; chúng không phải dữ liệu đầu vào để tự chọn overload.

Giả sử có `send(int)` và `send(long)`:

- `send((short) 7)` trong mã nguồn chọn `send(int)`, nhờ chuyển đổi mở rộng kiểu nguyên thủy (`short` sang `int`).
- `SomeClass.class.getMethod("send", short.class)` chỉ tìm đúng `send(short)`. Nếu không khai báo chữ ký ấy, nó ném `NoSuchMethodException`, dù `send(int)` nhận được giá trị `short` khi gọi trực tiếp.

**Tra cứu chữ ký chính xác** và **giải lời gọi overload** là hai bài toán khác nhau.

## 2. Trình biên dịch giải overload như thế nào?

Trình biên dịch có thông tin về tên phương thức, số lượng đối số, **kiểu tĩnh của từng biểu thức**, những phương thức có thể nhìn thấy, thông tin generic và ngữ cảnh lời gọi. Theo Java Language Specification, quy trình chọn phương thức có những giai đoạn chính:

1. Thử các phương thức với số tham số cố định, **chưa cần boxing/unboxing**, nhưng có thể dùng widening primitive (`short → int`) hoặc widening reference (`Integer → Number`).
2. Nếu chưa tìm được phương thức áp dụng được, xét thêm boxing/unboxing và các phép chuyển đổi được cho phép trong lời gọi.
3. Nếu vẫn chưa được, xét lời gọi `varargs` theo dạng số đối số biến đổi.
4. Ở giai đoạn đầu tiên có ứng viên phù hợp, chọn overload *most specific* theo quy tắc Java; nếu không chọn duy nhất được, báo lỗi **ambiguous**.

Quy tắc thực tế còn xét kế thừa, generic type inference và khả năng truy cập; không thể thay bằng mỗi phép `Class.isAssignableFrom()`.

**Kiểu tĩnh không luôn giống kiểu runtime.** Nếu có `send(Object)`, `send(String)` và khai báo `Object x = "hello";`, lời gọi `send(x)` được biên dịch sang `send(Object)`, vì kiểu tĩnh của `x` là `Object`. Trong khi đó, `x.getClass()` trả `String.class`: dùng lớp runtime để đoán overload có thể cho kết quả khác trình biên dịch.

## 3. `Class.getMethod` so khớp những gì?

Dạng API:

~~~java
Method method = SomeClass.class.getMethod(
    "methodName",
    FirstParameterType.class,
    SecondParameterType.class
);
~~~

Đây là đoạn **minh họa cú pháp**, không phải chương trình Java hoàn chỉnh. `getMethod` so khớp **tên** và **các kiểu tham số khai báo theo đúng thứ tự**. Không thực hiện chuyển đổi kiểu, không xem giá trị đối số, không tự chọn overload “gần nhất”. Ví dụ:

| Tra cứu | Kết quả yêu cầu |
| --- | --- |
| `getMethod("send", int.class)` | Chính xác `send(int)`. |
| `getMethod("send", Integer.class)` | Chính xác `send(Integer)`, khác `send(int)`. |
| `getMethod("send", Number.class)` | Chính xác `send(Number)`, không tự chuyển sang `send(Object)`. |
| `getMethod("batch", String[].class)` | Khớp `batch(String...)`, vì tham số varargs thực chất là mảng. |
| `getMethod("batch", String.class, String.class)` | Không khớp `batch(String...)`, dù mã nguồn có thể gọi `batch("a", "b")`. |

`getMethod` tìm các phương thức **public**, bao gồm phương thức public đủ điều kiện được kế thừa. `getDeclaredMethod` vẫn yêu cầu kiểu tham số chính xác, nhưng chỉ tìm phương thức khai báo ngay trong lớp/interface ấy, bao gồm cả thành viên không public. Không tìm thấy chữ ký thì ném `NoSuchMethodException`.

Với generic, Reflection nhìn chữ ký sau **type erasure**: phương thức `accept(List<String>)` thường phải tra bằng `List.class`; Java không có biểu thức `List<String>.class`. API `getMethod` cũng không nhận kiểu trả về làm tham số lựa chọn. Đôi khi compiler sinh bridge/synthetic methods; nếu duyệt danh sách phương thức, hãy xét `isBridge()` và `isSynthetic()`.

## 4. Chương trình Java 17+ minh họa đầy đủ

Lưu thành `ReflectionOverloadDemo.java`:

~~~java
import java.lang.reflect.Method;
import java.util.Arrays;

public class ReflectionOverloadDemo {
    public void send(int value) {
        System.out.println("send(int): " + value);
    }

    public void send(long value) {
        System.out.println("send(long): " + value);
    }

    public void send(Integer value) {
        System.out.println("send(Integer): " + value);
    }

    public void send(Number value) {
        System.out.println("send(Number): " + value);
    }

    public void send(Object value) {
        System.out.println("send(Object): " + value);
    }

    public void batch(String... items) {
        System.out.println("batch: " + Arrays.toString(items));
    }

    public static void main(String[] args) throws Exception {
        ReflectionOverloadDemo target = new ReflectionOverloadDemo();
        short n = 7;

        // Compiler chọn overload từ kiểu tĩnh của lời gọi.
        target.send(n);
        target.send(Integer.valueOf(7));
        target.send((Number) Integer.valueOf(7));

        // Không có đúng chữ ký send(short).
        try {
            ReflectionOverloadDemo.class.getMethod("send", short.class);
        } catch (NoSuchMethodException e) {
            System.out.println("Không có send(short)");
        }

        // Reflection yêu cầu ta chỉ rõ chữ ký.
        Method intMethod =
                ReflectionOverloadDemo.class.getMethod("send", int.class);
        intMethod.invoke(target, 7);

        Method numberMethod =
                ReflectionOverloadDemo.class.getMethod("send", Number.class);
        numberMethod.invoke(target, Integer.valueOf(7));

        try {
            ReflectionOverloadDemo.class.getMethod("send", String.class);
        } catch (NoSuchMethodException e) {
            System.out.println("Không có send(String)");
        }

        // String... được khai báo dưới dạng String[].
        Method batchMethod =
                ReflectionOverloadDemo.class.getMethod("batch", String[].class);
        batchMethod.invoke(target, (Object) new String[]{"a", "b"});
    }
}
~~~

Biên dịch/chạy bằng JDK 17 trở lên:

~~~bash
javac ReflectionOverloadDemo.java
java ReflectionOverloadDemo
~~~

Stdout chính xác:

~~~text
send(int): 7
send(Integer): 7
send(Number): 7
Không có send(short)
send(int): 7
send(Number): 7
Không có send(String)
batch: [a, b]
~~~

Các quan sát:

- Biến `n` có kiểu `short`, nên lời gọi Java thường chọn `send(int)`, nhưng tra `getMethod("send", short.class)` vẫn thất bại.
- `int.class` khác `Integer.class`. Giá trị `7` khi truyền cho `Method.invoke(Object, Object...)` được boxing vào `Integer`, rồi Reflection có thể unboxing cho tham số `int` đã chọn.
- `numberMethod` đã cố định `send(Number)`. Truyền đối số `Integer` cho `invoke` không làm nó chuyển sang overload `send(Integer)`.
- `String... items` trở thành một tham số `String[]`. Ép `(Object) new String[]{...}` buộc lời gọi `invoke` nhận **một đối số là mảng** thay vì nhầm các phần tử với danh sách đối số của `invoke`.

## 5. Vì sao `Method.invoke` nhận được một số chuyển đổi?

Có hai bước khác nhau:

~~~text
Class.getMethod("send", int.class)
            |
            v
 Method đại diện send(int)     (đã chọn chính xác overload)
            |
            v
 method.invoke(target, value) (gọi Method đã chọn)
~~~

`Method.invoke` **không chạy overload resolution**. Nó chỉ kiểm tra/chuyển đổi các đối số so với kiểu tham số của **Method đang cầm**. Theo JDK, Reflection có thể unboxing và mở rộng kiểu nguyên thủy phù hợp khi thực thi; chuyển đổi thu hẹp kiểu nguyên thủy (*narrowing*) không hợp lệ dẫn tới `IllegalArgumentException`. Method đích có varargs cũng không khiến `Method.invoke` tự gom các đối số của người gọi theo cú pháp Java. Nếu chính phương thức đích ném ngoại lệ, Reflection thường bọc trong `InvocationTargetException`.

Do đó, `invoke` có thể gọi `send(int)` với một `Integer` chứa giá trị `7`, **nhưng `getMethod("send", Integer.class)` không vì thế mà tìm thành `send(int)`**.

## 6. Cách dùng đúng trong thực tế

**Khi đã biết API:** ghi rõ danh sách kiểu tham số khai báo. Phương thức nhận `int` thì dùng `int.class`; nhận `Number` thì `Number.class`; nhận `String...` thì `String[].class`. Sau đó gọi `invoke` với đúng số lượng đối số và giá trị tương thích.

**Khi phải điều phối từ dữ liệu động:** có thể duyệt `getMethods()`, lọc tên và tự xây bộ chọn overload. Tuy nhiên bộ chọn đủ chính xác phải hiểu primitive/wrapper, widening, reference hierarchy, `null`, varargs, generic/bridge methods và overload mơ hồ. Chỉ dùng `arg.getClass()` hay `isAssignableFrom()` là chưa đủ. Với JSON, HTTP hoặc cấu hình, cách dễ kiểm soát hơn là định nghĩa rõ tên thao tác và chữ ký hợp lệ trong hợp đồng dữ liệu.

**Khi dùng Method Handles:** `MethodHandles.Lookup.findVirtual` hay `findStatic` cũng đòi hỏi `MethodType` đã xác định; đây không phải bộ giải overload ngầm tương đương compiler.

### Những lỗi phổ biến

1. Dùng `Integer.class` để tìm `method(int)`, hoặc dùng `int.class` để tìm `method(Integer)`.
2. Lấy `arg.getClass()` như thể đó là kiểu tĩnh của biến: primitive bị boxing; đối số `null` không có `getClass()`.
3. Tìm `method(String...)` bằng nhiều `String.class` thay vì một `String[].class`.
4. Dùng `getMethod` để tìm `private` method. `getDeclaredMethod` có thể tìm phương thức khai báo không public, nhưng muốn gọi có thể cần quyền truy cập; `setAccessible` còn chịu giới hạn của Java Platform Module System.
5. Lấy “phương thức đầu tiên” từ `getMethods()`: thứ tự mảng không được dùng như thứ tự ưu tiên overload.

## 7. Câu hỏi tự kiểm tra

- Chỉ có `f(long)`: `f(3)` có biên dịch? **Có**, do `int → long`. Vậy `getMethod("f", int.class)` có tìm thấy không? **Không**, vì không hề có `f(int)`.
- Có `f(String)` và `f(Integer)`: `f(null)` có chọn được overload duy nhất? **Không**, lời gọi mơ hồ vì hai kiểu tham số này không có quan hệ đặc hiệu; Reflection phải chỉ rõ `String.class` hoặc `Integer.class`.
- Lấy `getMethod("f", String.class)` rồi gọi `invoke(instance, new Object[]{null})`: có thể chuyển sang `f(Integer)` không? **Không**. `Method` đã đại diện `f(String)`.

**Ghi nhớ:** compiler giải **lời gọi** dựa vào kiểu biểu thức và quy tắc Java; `getMethod` tra **chữ ký phương thức chính xác** từ danh sách `Class` bạn cung cấp. `invoke` chỉ thực thi phương thức đã được chọn.

### Đọc thêm tài liệu chuẩn

- [JDK 17: `Class.getMethod` và `getDeclaredMethod`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getMethod(java.lang.String,java.lang.Class...))
- [JDK 17: `Method.invoke`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html#invoke(java.lang.Object,java.lang.Object...))
- [Java Language Specification §15.12: Method Invocation Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.12)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="member-lookup-008">Primitive parameter type khác wrapper parameter type</a>

<details>
<summary>Xem chi tiết</summary>

# Primitive parameter type khác wrapper parameter type khi tra cứu thành viên bằng Reflection

## 1. Vấn đề cốt lõi

Trong Java, một tham số kiểu nguyên thủy (*primitive*) và một tham số kiểu lớp bao (*wrapper*) có **kiểu khai báo khác nhau**, dù mã Java thông thường có thể tự động boxing/unboxing khi gọi phương thức.

Ví dụ, hai khai báo sau là **hai overload hợp lệ**:

```java
public void handle(int count) { /* ... */ }
public void handle(Integer count) { /* ... */ }
```

Khi tra cứu bằng Reflection, chữ ký tham số được đối chiếu theo đối tượng `Class` tương ứng:

| Kiểu khai báo | Biểu diễn Reflection | `getTypeName()` | `isPrimitive()` |
| --- | --- | --- | --- |
| `int` | `int.class` | `int` | `true` |
| `Integer` | `Integer.class` | `java.lang.Integer` | `false` |
| `boolean` | `boolean.class` | `boolean` | `true` |
| `Boolean` | `Boolean.class` | `java.lang.Boolean` | `false` |
| `long` | `long.class` | `long` | `true` |
| `Long` | `Long.class` | `java.lang.Long` | `false` |

**Bất biến cần nhớ:** `int.class != Integer.class`. Thuật toán tìm thành viên theo danh sách `parameterTypes` không tự boxing/unboxing, không tự mở rộng kiểu số và không tự chọn overload tương tự trình biên dịch Java.

`Integer.TYPE` là hằng `Class<Integer>` đại diện cho kiểu primitive `int`, vì vậy `Integer.TYPE == int.class` là `true`. Tuy nhiên `Integer.TYPE` **không phải** `Integer.class`.

## 2. API nào chịu ảnh hưởng?

| API | Loại thành viên được tra cứu | Quy tắc với tham số |
| --- | --- | --- |
| `type.getMethod(name, parameterTypes...)` | Phương thức `public`, kể cả được kế thừa | Các `Class` tham số phải khớp chính xác |
| `type.getDeclaredMethod(name, parameterTypes...)` | Phương thức khai báo trực tiếp trong lớp, kể cả `private` | Khớp chính xác; không tìm ở lớp cha |
| `type.getConstructor(parameterTypes...)` | Constructor `public` của lớp | Khớp chính xác |
| `type.getDeclaredConstructor(parameterTypes...)` | Constructor khai báo trong lớp, kể cả không `public` | Khớp chính xác |

Giả sử lớp chỉ có `void onlyInt(int value)`:

```java
Example.class.getMethod("onlyInt", int.class);      // Tìm thấy
Example.class.getMethod("onlyInt", Integer.class);  // NoSuchMethodException
Example.class.getMethod("onlyInt", short.class);    // NoSuchMethodException
```

Không thể dùng việc Java cho phép truyền một `Integer` vào lời gọi `onlyInt(5)` hoặc có thể chuyển `short` thành `int` để suy ra rằng tra cứu Reflection sẽ tìm được phương thức.

## 3. Ví dụ hoàn chỉnh, chạy được trên Java 17+

Lưu mã sau thành tệp `PrimitiveVsWrapperReflection.java`:

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class PrimitiveVsWrapperReflection {
    public static class Example {
        public Example(int initialValue) {
        }

        public void handle(int count) {
            System.out.println("int: " + count);
        }

        public void handle(Integer count) {
            System.out.println("Integer: " + count);
        }

        public void onlyInt(int count) {
        }

        private void hidden(long count) {
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("int == Integer: " + (int.class == Integer.class));
        System.out.println("Integer.TYPE == int.class: " + (Integer.TYPE == int.class));

        Method primitiveMethod = Example.class.getMethod("handle", int.class);
        Method wrapperMethod = Example.class.getMethod("handle", Integer.class);

        System.out.println("Primitive parameter: "
                + primitiveMethod.getParameterTypes()[0].getTypeName());
        System.out.println("Wrapper parameter: "
                + wrapperMethod.getParameterTypes()[0].getTypeName());

        Example example = new Example(0);
        primitiveMethod.invoke(example, Integer.valueOf(3));
        wrapperMethod.invoke(example, Integer.valueOf(4));

        try {
            Example.class.getMethod("onlyInt", Integer.class);
        } catch (NoSuchMethodException e) {
            System.out.println("No method onlyInt(Integer)");
        }

        Constructor<Example> constructor = Example.class.getConstructor(int.class);
        System.out.println("Constructor parameter: "
                + constructor.getParameterTypes()[0].getTypeName());

        try {
            Example.class.getConstructor(Integer.class);
        } catch (NoSuchMethodException e) {
            System.out.println("No constructor(Integer)");
        }

        Method privateMethod = Example.class.getDeclaredMethod("hidden", long.class);
        System.out.println("Private method parameter: "
                + privateMethod.getParameterTypes()[0].getTypeName());
    }
}
```

Biên dịch và chạy tại thư mục chứa tệp:

```bash
javac PrimitiveVsWrapperReflection.java
java PrimitiveVsWrapperReflection
```

Kết quả chuẩn:

```text
int == Integer: false
Integer.TYPE == int.class: true
Primitive parameter: int
Wrapper parameter: java.lang.Integer
int: 3
Integer: 4
No method onlyInt(Integer)
Constructor parameter: int
No constructor(Integer)
Private method parameter: long
```

Ý nghĩa từng bước:

1. `int.class` và `Integer.class` là hai đối tượng `Class` không đồng nhất; `Integer.TYPE` chính là `int.class`.
2. Hai lời gọi `getMethod("handle", ...)` trả về hai `Method` khác nhau, vì chữ ký khai báo `(int)` và `(Integer)` khác nhau.
3. `getParameterTypes()` phản ánh **kiểu tham số trong khai báo**, không phản ánh kiểu dữ liệu truyền vào khi gọi.
4. `getMethod("onlyInt", Integer.class)` ném `NoSuchMethodException`; tên có thật nhưng chữ ký `(Integer)` không tồn tại.
5. Constructor `Example(int)` tra được bằng `int.class`, còn `Integer.class` không khớp.
6. `getDeclaredMethod("hidden", long.class)` tìm thấy phương thức `private` khai báo tại lớp; ví dụ chỉ tra cứu metadata, không gọi phương thức riêng tư.

## 4. Phân biệt **tra cứu** và **gọi thực thi**

Đây là hai giai đoạn với quy tắc khác nhau:

```text
Tra cứu: Class.getMethod("handle", int.class)
    → tìm đúng thành viên có tham số khai báo int

Thực thi: Method.invoke(instance, Integer.valueOf(3))
    → sau khi đã tìm thấy Method, đối số Integer có thể được unbox thành int
```

- **Lúc tra cứu:** `getMethod`/`getConstructor` đối chiếu kiểu khai báo chính xác; không boxing/unboxing, không primitive widening (chẳng hạn `short.class` không khớp `int.class`).
- **Lúc `Method.invoke`/`Constructor.newInstance`:** Reflection có thể unbox giá trị wrapper cho tham số primitive và áp dụng **widening primitive** hợp lệ (ví dụ một giá trị `Integer` cho tham số `long`). Không áp dụng narrowing primitive tùy ý (ví dụ `Long` sang `int`).
- **Với tham số wrapper:** `Integer` là kiểu tham chiếu, nhận `null` và các giá trị tham chiếu phù hợp. Nếu khai báo tham số là `int`, truyền `null` tại lúc invoke là không hợp lệ và gây `IllegalArgumentException`. Không có `null` nào thuộc kiểu primitive.
- **Với overload:** sau khi lấy được một đối tượng `Method`, `invoke` thực thi đúng phương thức đã chọn. Nó không tự chọn lại overload `handle(Integer)` chỉ vì đối số runtime là `Integer`.

Vì vậy, lời gọi Java `example.onlyInt(Integer.valueOf(3))` có thể biên dịch nhờ unboxing, nhưng `Example.class.getMethod("onlyInt", Integer.class)` vẫn thất bại.

## 5. Kiểm tra và xây dựng chữ ký đúng

Khi làm việc với code không rõ khai báo, liệt kê và quan sát `Class` của từng tham số:

```java
for (Method method : Example.class.getDeclaredMethods()) {
    if (method.getName().equals("handle")) {
        for (Class<?> parameter : method.getParameterTypes()) {
            System.out.println(parameter.getTypeName()
                    + ", primitive=" + parameter.isPrimitive());
        }
    }
}
```

Hai dòng dữ liệu tương ứng sẽ cho biết một tham số là `int, primitive=true` và tham số còn lại là `java.lang.Integer, primitive=false` (không dựa vào thứ tự của mảng phương thức trả về).

Nếu biết chữ ký nguyên thủy, hãy truyền `int.class` hoặc `Integer.TYPE`; nếu chữ ký dùng wrapper, hãy truyền `Integer.class`. Tương tự với `long.class`/`Long.TYPE` và `Long.class`, `boolean.class`/`Boolean.TYPE` và `Boolean.class`.

Không dùng `Class.forName("int")` để lấy `Class` cho primitive: tên `int` không phải tên nhị phân của lớp có thể nạp bằng cách đó. Dùng trực tiếp `int.class` hoặc `Integer.TYPE`.

## 6. Các tình huống gần giống dễ nhầm

- **Mảng primitive và mảng wrapper:** `int[].class` khác `Integer[].class`; `int[]` không được tự chuyển thành `Integer[]`. Mặc dù bản thân cả hai mảng đều là kiểu tham chiếu, `getComponentType()` của chúng lần lượt là `int.class` và `Integer.class`.
- **Varargs trong khai báo:** `void accept(int... values)` có một tham số kiểu `int[]`; tra cứu bằng `getMethod("accept", int[].class)`, không dùng `int.class`.
- **Kiểu trả về:** `getMethod` không nhận kiểu trả về làm tiêu chí tìm kiếm; `getReturnType()` giúp kiểm tra sau khi tìm thấy phương thức. Phân biệt primitive và wrapper ở đây đang xét **tham số**.
- **Phạm vi truy cập:** đổi từ `getMethod` sang `getDeclaredMethod` chỉ điều chỉnh phạm vi tìm kiếm `public`/khai báo trực tiếp, không làm `Integer.class` tự khớp `int.class`.

## 7. Những lỗi thường gặp và cách sửa

| Hiện tượng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| `NoSuchMethodException` dù thấy `method(int)` trong mã nguồn | Tra bằng `Integer.class` | Dùng `int.class` |
| Không tìm được constructor `Example(int)` | Tra bằng `Integer.class` hoặc `short.class` | Dùng `getConstructor(int.class)` |
| Đã tìm thấy `Method` nhưng `invoke` lỗi đối số | Giá trị runtime không chuyển đổi hợp lệ sang kiểu khai báo; ví dụ `null` cho `int` | Kiểm tra `getParameterTypes()`, giá trị và luật chuyển đổi khi invoke |
| Tra `private void hidden(long)` bằng `getMethod` bị lỗi | `getMethod` chỉ xét thành viên `public` | Dùng `getDeclaredMethod("hidden", long.class)` nếu cần tra cứu khai báo |
| Nhầm `Integer.TYPE` là `Integer.class` | `TYPE` trỏ tới primitive `int` | Phân biệt `Integer.TYPE == int.class` với `Integer.class` |

**Tự kiểm tra:** Với lớp chỉ có `void run(long value)`, cả `getDeclaredMethod("run", Long.class)` lẫn `getDeclaredMethod("run", int.class)` đều không tìm thấy. Chỉ `getDeclaredMethod("run", long.class)` khớp; sau đó, lời gọi `invoke(target, Integer.valueOf(5))` có thể thành công nhờ unboxing `Integer` thành `int` rồi widening thành `long`.

**Ghi nhớ:** Tra cứu Reflection dựa trên **kiểu tham số đã khai báo**, còn chuyển đổi boxing/unboxing của **đối số khi gọi** là một cơ chế khác.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="member-lookup-009">Tra cứu theo kiểu sau type erasure</a>

<details>
<summary>Xem chi tiết</summary>

# Tra cứu thành viên theo kiểu sau type erasure trong Java Reflection

Khi tìm method, constructor hoặc field của lớp generic bằng Java Reflection, cần phân biệt **kiểu runtime để tra cứu** với **khai báo generic trong mã nguồn**. Java dùng *type erasure*: type variable như T thường bị thay bằng Object hoặc bound đầu tiên, còn List<String> chỉ mang raw type List trong chữ ký dùng để tìm method. Thông tin generic được khai báo vẫn có thể nằm trong thuộc tính Signature của class file để các API getGeneric... đọc lại.

Bài này giải thích cách chọn đúng Class<?> khi lookup, cách xem Type để phân tích generic, cách generic hoạt động qua kế thừa và vì sao compiler sinh bridge method.

## 1. Quy tắc xóa kiểu liên quan đến lookup

| Kiểu tham số được khai báo | Class<?> dùng để tìm sau erasure | Nguyên nhân |
| --- | --- | --- |
| T (không có bound) | Object.class | Erasure của type variable không bị ràng buộc |
| T extends Number | Number.class | Erasure bằng bound |
| T extends Number & Comparable<T> | Number.class | Chọn bound đầu tiên |
| List<String> | List.class | Xóa type argument String |
| List<? extends Number> | List.class | Wildcard không làm đổi raw type |
| T[] với T không có bound | Object[].class | Xóa kiểu phần tử thành Object |
| String... | String[].class | Varargs là một tham số mảng |

Xét khai báo:

~~~java
class Holder<T extends Number> {
    void set(T value) {}
    void setAll(java.util.List<String> values) {}
}
~~~

Đây là hai cách tra cứu đúng:

~~~java
Holder.class.getDeclaredMethod("set", Number.class);
Holder.class.getDeclaredMethod("setAll", java.util.List.class);
~~~

Không tìm set bằng Integer.class dù trong ứng dụng bạn có thể truyền Integer cho T; kiểu khai báo đã xóa là Number. Cũng không truyền một ParameterizedType biểu diễn List<String> vào getDeclaredMethod vì API này nhận mảng Class<?> mô tả **kiểu tham số sau erasure**.

Hai overload chỉ khác generic type argument dẫn đến cùng erasure sẽ xung đột lúc biên dịch. Đoạn sau **cố ý không biên dịch được**:

~~~java
void process(java.util.List<String> items) {}
void process(java.util.List<Integer> items) {} // name clash: same erasure
~~~

Đối tượng new ArrayList<String>() và new ArrayList<Integer>() đều có runtime class java.util.ArrayList; gọi getClass() không khôi phục được tham số kiểu String/Integer từ instance thông thường.

## 2. API tìm thành viên và phạm vi tra cứu

**Method**

- getDeclaredMethod(name, Class<?>...) tìm method được khai báo **ngay trong lớp** ở mọi mức truy cập, kể cả private; không tìm method kế thừa.
- getMethod(name, Class<?>...) tìm method **public**, gồm cả public method kế thừa.
- getDeclaredMethods() / getMethods() trả danh sách để tìm overload, lọc bridge hoặc kiểm tra thông tin generic.
- getDeclaredMethod/getMethod đối sánh tên và danh sách parameter types sau erasure; **không yêu cầu kiểu trả về**. Với bridge method có cùng tên và tham số, dùng isBridge()/isSynthetic() khi duyệt danh sách.
- Đối với void update(int id, List<String> values), cần int.class và List.class theo đúng thứ tự. int.class khác Integer.class; String[].class khác String.class trong varargs.

~~~java
clazz.getDeclaredMethod("update", int.class, java.util.List.class);
~~~

**Constructor**

- getDeclaredConstructor(Class<?>...) tìm constructor khai báo trong lớp ở mọi mức truy cập.
- getConstructor(Class<?>...) chỉ tìm constructor public.
- Nếu constructor nhận T extends Number thì tra cứu bằng Number.class, dù dữ liệu truyền khi tạo instance là Integer.

**Field**

- getDeclaredField(name) tìm field khai báo trực tiếp, kể cả private; getField(name) tìm field public và có thể thấy field kế thừa.
- Field được tìm bằng **tên**, không có tham số kiểu trong lệnh tìm. Sau đó Field.getType() trả kiểu đã xóa, còn Field.getGenericType() trả thông tin generic được khai báo khi có metadata.
- Field T value của class Holder<T extends Number> sẽ có getType() là Number.class, và getGenericType() thường là TypeVariable tên T.

Tra cứu được một thành viên chưa đồng nghĩa có thể truy cập/gọi thành công. Private access và module encapsulation vẫn áp dụng; type erasure không cho phép vượt qua kiểm soát truy cập.

## 3. Ví dụ Java 17+ có thể biên dịch và chạy

Tạo file **ErasureLookupDemo.java** với toàn bộ mã sau:

~~~java
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

public class ErasureLookupDemo {
    static class Store<T extends Number> {
        private T lastValue;
        public Store() {}
        public Store(T initial) { lastValue = initial; }
        public void put(T value) {}
        public void putList(List<String> values) {}
        public T first() { return null; }
        public void labels(String... values) {}
    }

    static class Base<T> {
        public T get() { return null; }
    }

    static class InheritsText extends Base<String> {}

    static class TextStore extends Base<String> {
        @Override
        public String get() { return "hello"; }
    }

    public static void main(String[] args) throws Exception {
        Constructor<?> constructor = Store.class.getDeclaredConstructor(Number.class);
        System.out.println("ctor/erased = " + constructor.getParameterTypes()[0].getTypeName());

        Field field = Store.class.getDeclaredField("lastValue");
        System.out.println("field/erased = " + field.getType().getTypeName());
        System.out.println("field/generic = " + field.getGenericType().getTypeName());

        Method put = Store.class.getDeclaredMethod("put", Number.class);
        System.out.println("put/erased = " + put.getParameterTypes()[0].getTypeName());
        System.out.println("put/generic = " + put.getGenericParameterTypes()[0].getTypeName());

        Method putList = Store.class.getDeclaredMethod("putList", List.class);
        System.out.println("putList/erased = " + putList.getParameterTypes()[0].getTypeName());
        Type declaredType = putList.getGenericParameterTypes()[0];
        if (declaredType instanceof ParameterizedType parameterized) {
            System.out.println("putList/typeArg = "
                    + parameterized.getActualTypeArguments()[0].getTypeName());
        }

        Method first = Store.class.getDeclaredMethod("first");
        System.out.println("first/erased = " + first.getReturnType().getTypeName());
        System.out.println("first/generic = " + first.getGenericReturnType().getTypeName());

        Method labels = Store.class.getDeclaredMethod("labels", String[].class);
        System.out.println("labels/isVarArgs = " + labels.isVarArgs());

        try {
            Store.class.getDeclaredMethod("put", Integer.class);
        } catch (NoSuchMethodException ex) {
            System.out.println("put(Integer) = not found");
        }

        Method inherited = InheritsText.class.getMethod("get");
        System.out.println("InheritsText.get/erased = " + inherited.getReturnType().getSimpleName());
        System.out.println("InheritsText.get/generic = " + inherited.getGenericReturnType().getTypeName());

        Method[] declared = TextStore.class.getDeclaredMethods();
        Arrays.sort(declared, (left, right) -> Boolean.compare(left.isBridge(), right.isBridge()));
        for (Method method : declared) {
            if (method.getName().equals("get")) {
                System.out.println("TextStore.get = " + method.getReturnType().getSimpleName()
                        + ", bridge=" + method.isBridge());
            }
        }

        Type parent = TextStore.class.getGenericSuperclass();
        if (parent instanceof ParameterizedType parameterized) {
            System.out.println("TextStore<Base> arg = "
                    + parameterized.getActualTypeArguments()[0].getTypeName());
        }
    }
}
~~~

Biên dịch và chạy từ thư mục chứa file:

~~~bash
javac -encoding UTF-8 ErasureLookupDemo.java
java ErasureLookupDemo
~~~

Output stdout đã được kiểm chứng bằng JDK 21 và sử dụng các tính năng tương thích Java 17:

~~~text
ctor/erased = java.lang.Number
field/erased = java.lang.Number
field/generic = T
put/erased = java.lang.Number
put/generic = T
putList/erased = java.util.List
putList/typeArg = java.lang.String
first/erased = java.lang.Number
first/generic = T
labels/isVarArgs = true
put(Integer) = not found
InheritsText.get/erased = Object
InheritsText.get/generic = T
TextStore.get = String, bridge=false
TextStore.get = Object, bridge=true
TextStore<Base> arg = java.lang.String
~~~

Giải thích kết quả:

1. **Kiểu sau erasure**: Store<T extends Number> khai báo constructor và method nhận T, nhưng getDeclaredConstructor/getDeclaredMethod phải nhận Number.class. Field và kiểu trả về T cũng có raw Class là Number.
2. **Khai báo generic được lưu lại**: getGenericType/getGenericParameterTypes/getGenericReturnType cho TypeVariable T; putList có ParameterizedType với actual type argument String dù lookup chỉ dùng List.class.
3. **Phương thức kế thừa**: InheritsText extends Base<String> nhưng không override get(); Method kế thừa từ Base vẫn có erased return type Object và generic return T. Muốn biết String là đối số kiểu của Base tại lớp con cần phân tích getGenericSuperclass().
4. **Bridge method**: TextStore override get() trả String. Trình biên dịch thêm bridge get() trả Object (isBridge() == true) để lệnh gọi qua Base tiếp tục dispatch đúng sau erasure. Thứ tự getDeclaredMethods() không được bảo đảm; ví dụ chủ động sắp xếp trước khi in để stdout ổn định.

## 4. Class<?> để tìm; Type để đọc chữ ký generic

| Việc cần làm | API | Dạng kết quả |
| --- | --- | --- |
| Tìm method hoặc constructor | getMethod/getDeclaredMethod/getConstructor/getDeclaredConstructor | Truyền Class<?>... đã xóa kiểu |
| Đọc kiểu tham số runtime | Method.getParameterTypes(), Constructor.getParameterTypes() | Class<?>[] |
| Đọc kiểu trả về/field runtime | Method.getReturnType(), Field.getType() | Class<?> |
| Đọc generic của method | Method.getGenericParameterTypes(), Method.getGenericReturnType() | Type[], Type |
| Đọc generic của constructor | Constructor.getGenericParameterTypes() | Type[] |
| Đọc generic của field | Field.getGenericType() | Type |
| Đọc generic superclass/interfaces | Class.getGenericSuperclass(), Class.getGenericInterfaces() | Type, Type[] |

Type là một interface tổng quát; không phải Type nào cũng là Class. Các trường hợp thường gặp:

- **Class<?>**: kiểu không parameterized tại vị trí đang xem.
- **ParameterizedType**: List<String>, Map<String, Integer>; dùng getRawType() và getActualTypeArguments().
- **TypeVariable<?>**: T; dùng getName() và getBounds().
- **WildcardType**: dấu ? trong List<? extends Number>; dùng getUpperBounds()/getLowerBounds().
- **GenericArrayType**: kiểu mảng còn chứa generic type variable, ví dụ T[].

Không cast Type trực tiếp sang ParameterizedType mà thiếu kiểm tra instanceof. Metadata generic nói về **chữ ký khai báo**. Nó không phải cơ chế runtime kiểm tra tất cả phần tử thực tế trong List đã đúng String, đặc biệt khi có raw types hoặc unchecked conversion.

Dùng lớp Store từ ví dụ trên, đoạn sau minh họa việc tách raw type và type argument (đây là **đoạn trích phụ thuộc Store**, không phải file chạy độc lập):

~~~java
Method m = Store.class.getDeclaredMethod("putList", List.class);
Type t = m.getGenericParameterTypes()[0];
if (t instanceof ParameterizedType p) {
    System.out.println(p.getRawType());               // interface java.util.List
    System.out.println(p.getActualTypeArguments()[0]); // class java.lang.String
}
~~~

Để suy ra ràng buộc T = String từ subclass extends Base<String>, phải duyệt generic superclass/interfaces, lấy các actual type arguments rồi ánh xạ chúng tới type variables ở lớp khai báo. getMethod() của phương thức kế thừa không tự thay mọi TypeVariable trong method đó bằng kiểu cụ thể của subclass.

## 5. Quy trình xử lý một yêu cầu lookup

1. **Xác định nơi khai báo và visibility.** Chọn getDeclaredMethod/getDeclaredConstructor/getDeclaredField khi cần member khai báo trực tiếp (kể cả non-public); chọn getMethod/getConstructor/getField khi yêu cầu public tương ứng.
2. **Đổi từng parameter type sang erasure.** Bỏ type arguments của parameterized type; thay T bằng Object hoặc erasure của bound đầu tiên; với mảng giữ dạng array class; với primitive dùng chính xác int.class/long.class...
3. **Tra tên + tham số đúng thứ tự.** Ví dụ foo(T, List<String>, int[]) với T extends Number cần Number.class, List.class, int[].class. Field chỉ tra theo tên.
4. **Đọc generic metadata khi bài toán cần.** Dùng getGeneric... và instanceof để phân biệt ParameterizedType, TypeVariable, WildcardType, GenericArrayType; khi liên quan kế thừa thì thêm ánh xạ type variables qua superclass/interfaces.
5. **Nếu liệt kê method sau override generic**, kiểm tra isBridge()/isSynthetic(); tùy nhu cầu có thể loại bridge khi tạo tài liệu hướng đến source API, nhưng giữ bridge khi phân tích dispatch JVM.

## 6. Lỗi phổ biến và cách nhận diện

- **NoSuchMethodException**: truyền Integer.class cho T extends Number; truyền Integer.class thay int.class; truyền String.class thay String[].class cho varargs; hoặc nhầm getDeclaredMethod với getMethod khi member thực ra được kế thừa.
- **Nhầm type argument với runtime parameter type**: getDeclaredMethod dùng List.class dù source ghi List<String> hay List<Integer>. Không có List<String>.class.
- **Nhầm generic superclass với chữ ký của method kế thừa**: extends Base<String> không làm Base.get() đột nhiên có erased return type String.
- **Không kiểm tra bridge methods**: getDeclaredMethods() có thể chứa method compiler sinh ra; không dựa vào thứ tự mảng Method khi tìm một phiên bản cụ thể.
- **Lạm dụng getGeneric... để xác nhận giá trị runtime**: thông tin kiểu generic phần lớn chỉ mô tả khai báo. Các phép cast/gọi invoke vẫn có thể gặp lỗi và còn bị ràng buộc bởi quyền truy cập hoặc module boundary.
- **Đọc return type như tham số tra cứu**: getMethod/getDeclaredMethod không nhận return type; nếu cần phân tích return type hãy đọc Method.getReturnType()/getGenericReturnType() sau khi lấy Method.

## 7. Bài tập tự kiểm tra

Với method khai báo **<T extends CharSequence> void consume(T[] data, List<? super String> output)**, nó được tìm bằng:

~~~java
clazz.getDeclaredMethod("consume", CharSequence[].class, java.util.List.class);
~~~

T[] được xóa thành CharSequence[] do bound của T; List<? super String> được xóa thành List. Khi đọc getGenericParameterTypes()[0], bạn thường nhận GenericArrayType chứa TypeVariable T; tham số thứ hai là ParameterizedType chứa WildcardType có lower bound String. Tra cứu bằng String[].class là sai dù một lời gọi thực tế có thể truyền mảng String[].

**Ghi nhớ:** dùng các Class<?> đã erasure để **tìm** thành viên; dùng Type để **phân tích** thông tin generic đã khai báo. Với generic inheritance và override, kiểm tra thêm generic superclass cùng bridge methods.

### Tài liệu tham khảo

- Java Language Specification 17, §4.6 — Type Erasure: https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.6
- JDK 17 Class API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- JDK 17 Method API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html
- JDK 17 ParameterizedType API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/ParameterizedType.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="member-lookup-010">Thứ tự thành viên được trả về không phải thứ tự khai báo được bảo đảm</a>

<details>
<summary>Xem chi tiết</summary>

# Reflection: Thứ tự thành viên trả về không được bảo đảm là thứ tự khai báo

Khi tra cứu thành viên của một lớp bằng Java Reflection, các API như `Class.getDeclaredMethods()`, `getDeclaredFields()` hay `getDeclaredConstructors()` trả về **mảng các đối tượng mô tả thành viên**, nhưng **không cam kết các phần tử xuất hiện theo thứ tự khai báo trong mã nguồn**. Tương tự, các API lấy thành viên `public` cũng không bảo đảm thứ tự. Nếu chương trình cần kết quả nhất quán, phải tự định nghĩa quy tắc sắp xếp hoặc tra cứu bằng danh tính cụ thể của thành viên.

## 1. Điều gì được bảo đảm, điều gì không?

Giả sử lớp có các phương thức được viết theo thứ tự `zeta`, `alpha(int)`, `alpha()`. Không được mặc định rằng `getDeclaredMethods()[0]` là `zeta` hoặc hai phương thức `alpha` đứng liền nhau theo thứ tự trong tệp Java. Việc chạy thử thấy kết quả giống mã nguồn chỉ phản ánh một lần chạy trong một môi trường, **không tạo thành hợp đồng API**.

| API trên `Class<?>` | Phạm vi thành viên được tìm | Thứ tự mảng trả về |
| --- | --- | --- |
| `getDeclaredMethods()` | Phương thức được khai báo trong chính lớp/interface, kể cả không `public`; không lấy phương thức kế thừa | Không xác định |
| `getMethods()` | Phương thức `public` nhìn thấy từ kiểu, bao gồm các phương thức kế thừa theo quy tắc API | Không xác định |
| `getDeclaredFields()` | Trường được khai báo tại chính kiểu, kể cả `private`; không gồm trường kế thừa | Không xác định |
| `getFields()` | Các trường `public` nhìn thấy, có thể bao gồm trường kế thừa | Không xác định |
| `getDeclaredConstructors()` | Các constructor khai báo tại lớp, kể cả không `public` | Không xác định |
| `getConstructors()` | Các constructor `public` của lớp | Không xác định |

“Không xác định” ở đây có nghĩa là **không có bảo đảm về thứ tự của mảng**; không đồng nghĩa rằng mỗi lần gọi sẽ bị xáo ngẫu nhiên. Trên một JDK cụ thể, bạn có thể quan sát cùng thứ tự rất nhiều lần. Kết quả quan sát đó vẫn không đủ để viết logic phụ thuộc vào vị trí.

Cần phân biệt **tập thành viên** mà mỗi API mô tả với **vị trí các phần tử** trong tập đó. Chọn đúng `getMethods()` hay `getDeclaredMethods()` là quyết định về phạm vi/khả năng truy cập; nó không giải quyết chuyện thứ tự. Việc `getDeclaredFields()` nhìn thấy metadata của trường `private` cũng không tự cấp quyền đọc giá trị trường đó.

## 2. Vì sao không nên suy luận thứ tự từ mã nguồn?

Reflection truy vấn metadata của kiểu đã nạp và tuân theo hợp đồng API của JDK. API tra cứu thành viên không hứa sẽ bảo toàn thứ tự xuất hiện trong tệp `.java`. Cách trình biên dịch tạo bytecode, các thành viên tổng hợp/bridge, cách JVM và thư viện Reflection triển khai, hoặc thay đổi phiên bản công cụ có thể làm thứ tự quan sát được khác đi. Không cần chứng minh một lần chạy bị đảo thứ tự mới kết luận rằng chương trình không được dựa vào thứ tự ấy: **điều quyết định là tài liệu API không bảo đảm**.

Ví dụ, hai cách sau đều dễ sai:

```java
Method first = Example.class.getDeclaredMethods()[0];
// SAI về giả định: first là phương thức xuất hiện đầu tiên trong file .java.

Field firstField = Example.class.getDeclaredFields()[0];
// SAI về giả định: firstField là trường được khai báo đầu tiên.
```

Đây là các *đoạn minh họa giả định sai*, không phải chương trình độc lập. Phần tử `[0]` chỉ có nghĩa là phần tử đầu tiên **trong mảng trả về lần đó**, không mang ý nghĩa “khai báo đầu tiên”. Nếu mảng rỗng, truy cập `[0]` còn gây `ArrayIndexOutOfBoundsException`.

## 3. Ví dụ Java 17+: sắp xếp trước khi sử dụng

Ví dụ sau khai báo phương thức theo một thứ tự, nhưng **không in mảng Reflection thô** để tránh gắn một output cố định cho thứ tự không được bảo đảm. Thay vào đó, chương trình định nghĩa một khóa hiển thị gồm tên phương thức và các kiểu tham số, rồi sắp xếp theo khóa đó. Lớp ví dụ không có phương thức overload với cùng tên và danh sách tham số nhưng khác kiểu trả về, nên khóa hiển thị đủ để xác định thứ tự trong ví dụ này.

Tạo tệp `MemberOrderDemo.java`:

```java
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

public class MemberOrderDemo {
    static class Example {
        public void zeta() {}

        public void alpha(int value) {}

        public void alpha() {}
    }

    private static String signature(Method method) {
        String parameters = Arrays.stream(method.getParameterTypes())
                .map(Class::getTypeName)
                .collect(Collectors.joining(", "));
        return method.getName() + "(" + parameters + ")";
    }

    public static void main(String[] args) {
        Method[] methods = Example.class.getDeclaredMethods();

        Arrays.sort(methods, Comparator.comparing(MemberOrderDemo::signature));

        for (Method method : methods) {
            System.out.println(signature(method));
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac MemberOrderDemo.java
java MemberOrderDemo
```

Output sau khi đã sắp xếp:

```text
alpha()
alpha(int)
zeta()
```

Vì sao output này ổn định trong ví dụ?

1. `getDeclaredMethods()` tìm ba phương thức do `Example` khai báo, bất kể thứ tự ban đầu của chúng trong mảng.
2. `signature()` tạo khóa rõ ràng: tên phương thức kết hợp danh sách kiểu tham số. Nhờ vậy hai overload `alpha()` và `alpha(int)` được phân biệt.
3. `Arrays.sort(...)` sắp xếp theo khóa chuỗi trước khi vòng `for` in ra. Thứ tự ở output là **quy tắc do ứng dụng lựa chọn**, không phải thứ tự nguồn được Reflection tự cung cấp.

Mã này chỉ phục vụ cho việc hiển thị/duyệt có thứ tự; việc sắp xếp mảng không thay đổi khai báo của lớp hay metadata của JVM. Nếu muốn thứ tự theo tên trường, có thể sử dụng `Arrays.sort(fields, Comparator.comparing(Field::getName))` sau khi lấy `Field[] fields = SomeClass.class.getDeclaredFields()`; nhớ import `java.lang.reflect.Field`.

## 4. Khi muốn tìm đúng thành viên: tra cứu theo danh tính

Nếu đã biết tên và kiểu tham số, **tránh chọn phương thức bằng chỉ số mảng**. Dùng API tra cứu theo chữ ký:

```java
Method noArgs = Example.class.getDeclaredMethod("alpha");
Method withInt = Example.class.getDeclaredMethod("alpha", int.class);
```

Hai câu lệnh này là **đoạn mã nằm trong ngữ cảnh ví dụ ở mục 3**, có thể đặt vào `main` (và cần xử lý hoặc khai báo `NoSuchMethodException`). Chúng không phụ thuộc `getDeclaredMethods()` sắp xếp ra sao.

- Phương thức: `getDeclaredMethod(name, parameterTypes...)` hoặc `getMethod(name, parameterTypes...)`; cần xét cả tên lẫn danh sách kiểu tham số vì Java hỗ trợ overload.
- Trường: `getDeclaredField(name)` hoặc `getField(name)`.
- Constructor: `getDeclaredConstructor(parameterTypes...)` hoặc `getConstructor(parameterTypes...)`; constructor được phân biệt bằng kiểu tham số, không bằng tên tùy ý.

Các bản `getDeclared...` truy vấn khai báo của chính kiểu, còn bản không có `Declared` dùng quy tắc thành viên `public` của API tương ứng. Tra cứu sai tên/chữ ký có thể ném `NoSuchMethodException` hoặc `NoSuchFieldException`; đó là lỗi tra cứu, không liên quan đến thứ tự. Với các class có phương thức bridge/synthetic hay các trường hợp bytecode đặc biệt, nên kiểm tra thêm `isBridge()`, `isSynthetic()` và metadata liên quan thay vì giả định nguồn Java đơn giản luôn mô tả trọn bộ thành viên được phản chiếu.

## 5. Chọn tiêu chí thứ tự phù hợp với mục đích

Một quy tắc sắp xếp là hợp lệ khi **được công bố rõ và đủ để phân biệt các phần tử cần phân biệt**. Một số mục đích phổ biến:

| Mục đích | Quy tắc nên chọn |
| --- | --- |
| Danh sách trường trong giao diện hoặc tài liệu | Sắp xếp `Field::getName` nếu chỉ cần thứ tự tên |
| Danh sách phương thức có overload | Sắp xếp bằng tên + danh sách kiểu tham số; bổ sung kiểu trả về/kiểu khai báo khi cần phân biệt các phương thức đặc biệt |
| So sánh kết quả Reflection trong test | Chuẩn hóa thành set/map theo khóa, hoặc sắp xếp theo khóa xác định trước khi so sánh |
| Chạy các handler/plugin theo ưu tiên nghiệp vụ | Dùng metadata ưu tiên riêng (ví dụ annotation `@Order` do ứng dụng định nghĩa), kết hợp khóa phụ để xử lý trường hợp trùng ưu tiên |
| Cần chính xác thứ tự xuất hiện trong mã nguồn | Thu thập từ AST/source hoặc metadata được ghi nhận lúc build; không suy ra chỉ từ vị trí trong mảng Reflection |

Với ứng dụng thực tế, một `Comparator` chỉ so sánh tên phương thức có thể trả kết quả bằng nhau cho các overload. Khi đó các overload vẫn có thể giữ vị trí theo thứ tự đầu vào vốn không được bảo đảm. Muốn một **thứ tự tổng rõ ràng** cho danh sách phương thức, hãy thêm các khóa phụ theo yêu cầu, chẳng hạn danh sách tên kiểu tham số, kiểu trả về và tên kiểu khai báo. Nếu cần một danh tính ổn định qua quá trình build, hãy thống nhất định dạng tên kiểu/chữ ký trong hệ thống thay vì phụ thuộc vào `toString()` như một chuẩn lưu trữ tự chế.

**Chú ý:** tên phương thức không thể hiện thứ tự khai báo. Sắp xếp theo tên chỉ tạo thứ tự bảng chữ cái, **không phục hồi thứ tự mã nguồn**. Nếu nghiệp vụ yêu cầu `validate` chạy trước `save`, mã phải thể hiện quy tắc ấy bằng thứ tự thao tác rõ ràng hoặc metadata ưu tiên, không dựa vào việc phương thức nào xuất hiện trước trong `getDeclaredMethods()`.

## 6. Những bẫy thực tế

**Bẫy 1 — Test chạy xanh trên máy này nên thứ tự là đúng.** Test so sánh mảng theo vị trí có thể hoạt động trên JDK hiện tại nhưng thiếu cơ sở theo hợp đồng API. Hãy so sánh tập chữ ký khi chỉ quan tâm thành viên tồn tại, hoặc sắp xếp trước khi kiểm tra thứ tự đầu ra.

**Bẫy 2 — `getDeclaredMethods()` nghĩa là theo thứ tự declared.** Chữ `Declared` chỉ nói **phạm vi tìm kiếm** là thành viên khai báo tại chính kiểu; không nói về **thứ tự khai báo**.

**Bẫy 3 — Sắp xếp chỉ theo `Method::getName`.** Khi overload cùng tên, comparator đó không phân biệt được `load()` và `load(String)`. Thêm tham số (và khóa phụ khác nếu cần) để tránh kết quả còn phụ thuộc thứ tự ban đầu.

**Bẫy 4 — Nhầm thành viên kế thừa với thành viên trực tiếp.** `getMethods()` có thể trả phương thức `public` được kế thừa, còn `getDeclaredMethods()` không dùng để liệt kê toàn bộ phương thức kế thừa. Việc đổi API sẽ thay đổi *thành phần* của danh sách, không làm thứ tự trở nên đáng tin hơn.

**Bẫy 5 — Cố gắng lấy thứ tự nguồn từ `.class` bằng chỉ số.** Class file hoặc thư viện phân tích bytecode có thể có thứ tự riêng nhưng Reflection không cam kết phản ánh thứ tự đó. Thậm chí thứ tự metadata trong class file cũng không nhất thiết tương ứng thứ tự phát biểu trong mã nguồn gốc sau các bước sinh/chuyển đổi mã.

**Bẫy 6 — Áp dụng kết luận cho mọi API Reflection.** Quy tắc ở đây nói về các mảng thành viên như `getDeclaredMethods()`, `getMethods()`, `getDeclaredFields()` và constructor. Một API khác có thể có hợp đồng thứ tự riêng: ví dụ `getRecordComponents()` của record trả các component theo thứ tự khai báo trong record header. Luôn đọc tài liệu của **đúng phương thức API** đang sử dụng.

## 7. Checklist áp dụng

1. Xác định cần *tìm một thành viên* hay *duyệt nhiều thành viên*.
2. Nếu tìm một thành viên, dùng API theo tên/chữ ký; không lấy phần tử theo index rồi đoán ý nghĩa.
3. Nếu duyệt nhiều thành viên, chọn `getDeclared...` hay bản `public` theo đúng phạm vi nghiệp vụ.
4. Quy định thứ tự ở tầng ứng dụng (tên, chữ ký, ưu tiên annotation, v.v.) rồi sort **trước** khi hiển thị, xử lý theo thứ tự hoặc xuất tài liệu.
5. Kiểm tra overload, inherited members, bridge/synthetic members và tiêu chí xử lý khóa bằng nhau khi liên quan.
6. Trong test, chỉ khẳng định thứ tự do **chính ứng dụng** thiết lập, không khẳng định thứ tự mảng thô của Reflection.

### Tự kiểm tra

- Nếu `getDeclaredFields()` trả về `[b, a]` trong một lần chạy nhưng mã nguồn khai báo `a` rồi `b`, JDK có vi phạm hợp đồng không? **Không.** API không hứa thứ tự khai báo.
- Có thể tin `getDeclaredMethods()[0]` là phương thức đầu tiên trong file Java khi đã chạy thử mười lần cùng kết quả không? **Không.** Số lần quan sát không thay thế hợp đồng.
- Khi muốn danh sách phương thức luôn theo tên và chữ ký, nên làm gì? **Tạo khóa so sánh gồm tên + danh sách kiểu tham số (cùng khóa phụ khi cần), rồi sắp xếp rõ ràng.**
- Có thể dùng `getDeclaredMethod("process", String.class)` để tìm phương thức overload tương ứng mà không cần biết vị trí mảng không? **Có**, miễn phương thức đó thực sự tồn tại với chữ ký tương ứng trong phạm vi API.

### Tài liệu tham khảo

- Oracle Java SE 21, [`Class` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html): mô tả phạm vi của `getMethods()`, `getDeclaredMethods()`, `getFields()`, `getDeclaredFields()`, `getConstructors()`, `getDeclaredConstructors()` và điều khoản phần tử trong các mảng này không được sắp xếp theo bất kỳ thứ tự cụ thể nào.
- Oracle Java SE 21, [`Method` API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Method.html): chữ ký, tham số, phương thức bridge và synthetic.

**Ghi nhớ:** Reflection cho biết *thành viên nào được tìm thấy* theo hợp đồng của từng API. Khi thứ tự có ý nghĩa, **ứng dụng phải tự xác lập thứ tự đó**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="member-lookup-011">Xây dựng chính sách tìm kiếm xuyên phân cấp một cách tường minh</a>

<details>
<summary>Xem chi tiết</summary>

# Xây dựng chính sách tìm kiếm xuyên phân cấp một cách tường minh với Java Reflection

## 1. Vì sao phải có chính sách tra cứu?

Khi viết công cụ dựa trên Java Reflection (mapper, serializer, framework kiểm thử, công cụ kiểm tra metadata), câu hỏi “tìm field/method có tên X” chưa đủ rõ. Ta còn phải quyết định: chỉ xét lớp hiện tại hay cả lớp cha; có đi qua interface không; có lấy thành viên `private`/`protected` không; có giữ các khai báo bị che khuất/ghi đè không; và ưu tiên khai báo nào khi nhiều nơi cùng tên.

Ví dụ, `Child` khai báo field `id` trùng với `Parent.id`. Hai field **cùng tồn tại** và mỗi field có `getDeclaringClass()` khác nhau. Với method `score()` được override, ta thường muốn khai báo gần lớp được hỏi nhất. Nhưng công cụ kiểm toán có thể cần **mọi khai báo**, kể cả khai báo bị che. Vì vậy, kết quả phải đến từ một chính sách có tên, phạm vi và thứ tự ưu tiên được viết rõ trước khi triển khai.

Phân biệt ba yêu cầu thường gặp:

- **Tra cứu thành viên công khai có thể nhìn thấy:** tận dụng `getField` / `getMethod` / `getFields` / `getMethods`, với ngữ nghĩa của API.
- **Kiểm kê khai báo theo từng tầng:** đi qua từng `Class<?>` và dùng `getDeclaredFields` / `getDeclaredMethods`; không gộp các khai báo trùng.
- **Chọn một thành viên theo chính sách ứng dụng:** tự định nghĩa các tầng được thăm, bộ lọc, khóa trùng và quy tắc ưu tiên; ví dụ mã bên dưới thực hiện cách này.

“Được Reflection tìm thấy” **không đồng nghĩa** với “được ngôn ngữ Java kế thừa”, “có quyền truy cập” hay “gọi được”. Đây là bốn vấn đề riêng.

## 2. Các API và ranh giới cần hiểu

| API | Phạm vi và ý nghĩa |
| --- | --- |
| `getDeclaredField(name)` / `getDeclaredFields()` | Field được **khai báo trực tiếp** tại lớp/interface đang xét, bất kể mức truy cập; không tự đi lớp cha. |
| `getField(name)` / `getFields()` | Field `public` của lớp và thành viên `public` được kế thừa/hiển thị từ các lớp cha, interface theo quy tắc Reflection. |
| `getDeclaredMethod(name, parameterTypes...)` / `getDeclaredMethods()` | Method khai báo trực tiếp, kể cả `private`; tìm method theo tên **và kiểu tham số**, không theo kiểu trả về. |
| `getMethod(name, parameterTypes...)` / `getMethods()` | Method `public` được cung cấp trong tập public của kiểu, bao gồm các method kế thừa thích hợp; có thể bao gồm method từ `Object`. |
| `getSuperclass()` | Lấy lớp cha trực tiếp; với `Object` hoặc interface thì trả về `null`. |
| `getInterfaces()` | Lấy các interface trực tiếp được khai báo; muốn thăm cả interface cha, phải tự duyệt tiếp `getInterfaces()`. |
| `getDeclaringClass()` | Xác định chính xác nơi một `Field`/`Method` được khai báo, khác với lớp ban đầu yêu cầu tìm. |

`getDeclared...` là thành phần quan trọng của thuật toán xuyên phân cấp: mỗi lần gọi chỉ xem **một** kiểu, sau đó thuật toán chủ động đi sang kiểu khác. Không nên giả định thứ tự phần tử trả về bởi `getDeclaredFields()` hoặc `getDeclaredMethods()` ổn định; hãy sắp xếp nếu kết quả cần tái lập được.

Constructor không được kế thừa; nếu bài toán yêu cầu constructor, dùng `getDeclaredConstructors()` trên đúng kiểu, không áp dụng thuật toán tìm kiếm constructor dọc lớp cha.

## 3. Hợp đồng chính sách tra cứu

Ví dụ sau định nghĩa ba chế độ:

1. `DECLARED_ONLY`: chỉ kiểu đầu vào.
2. `CLASS_HIERARCHY`: kiểu đầu vào → lớp cha gần nhất → lớp cha tiếp theo; `Object` chỉ xuất hiện khi bật `includeObject`.
3. `WITH_INTERFACES`: trước hết toàn bộ các lớp như trên; sau đó các interface được khai báo từ lớp gần nhất đến lớp xa hơn, đi sâu theo interface cha, bỏ các interface đã thăm.

Hai cờ độc lập:

- `publicOnly`: chỉ nhận thành viên có modifier `public`. Nếu `false`, thu thập cả khai báo không public; **không** tự nâng quyền truy cập.
- `includeObject`: có đưa `Object` vào vòng duyệt lớp cha hay không (mặc định trong ví dụ sử dụng `false`).

Ví dụ chọn thành viên theo quy tắc **first match wins**: với field, khóa trùng là tên; với method, khóa trùng là tên cùng danh sách kiểu tham số. Vì lớp gần được duyệt trước, field bị che và method bị override ưu tiên khai báo gần nhất. Với nhiều interface trùng chữ ký/tên, thứ tự duyệt interface đã định sẽ chọn ứng viên đầu tiên. Nếu nghiệp vụ không cho phép nhập nhằng, thay lựa chọn đầu tiên bằng kiểm tra và báo lỗi rõ ràng. Đây là **chính sách của công cụ**, không tự nhận là thuật toán phân giải tất cả trường hợp gọi method của trình biên dịch Java.

Các kết quả dùng `List.copyOf`/`Optional` để người gọi nhận kết quả dễ kiểm tra và không sửa trực tiếp tập kết quả.

## 4. Ví dụ Java 17+ chạy độc lập

Lưu thành `HierarchyLookupDemo.java`. Chương trình dùng lớp lồng nhau nhằm thể hiện field hiding, covariant return/bridge method, interface mặc định và mức truy cập private.

~~~java
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class HierarchyLookupDemo {
    enum Mode {
        DECLARED_ONLY, CLASS_HIERARCHY, WITH_INTERFACES
    }

    record Policy(Mode mode, boolean publicOnly, boolean includeObject) {
        Policy {
            Objects.requireNonNull(mode);
        }
    }

    record Signature(String name, List<Class<?>> parameters) {}

    interface Named {
        default String label() {
            return "named";
        }
    }

    interface Audited extends Named {
        default void audit() {}
    }

    static class Parent {
        public String id;
        private String secret;

        public Number score() {
            return 1;
        }
    }

    static class Middle extends Parent implements Audited {
        public String id; // Che Parent.id, không xóa Parent.id.
        protected String state;

        @Override
        public Integer score() {
            return 2;
        }
    }

    static final class Leaf extends Middle {
        private String local;

        public void setLocal(String value) {
            local = value;
        }
    }

    // Thứ tự: kiểu đầu vào -> superclass -> ... -> interface trực tiếp,
    // rồi các interface cha (không duyệt trùng trong cấu trúc hình thoi).
    static List<Class<?>> owners(Class<?> start, Policy policy) {
        Objects.requireNonNull(start);
        if (policy.mode() == Mode.DECLARED_ONLY) {
            return List.of(start);
        }

        List<Class<?>> classes = new ArrayList<>();
        for (Class<?> current = start;
             current != null;
             current = current.getSuperclass()) {
            if (current == Object.class && !policy.includeObject()) {
                break;
            }
            classes.add(current);
        }

        if (policy.mode() == Mode.CLASS_HIERARCHY) {
            return List.copyOf(classes);
        }

        Set<Class<?>> ordered = new LinkedHashSet<>(classes);
        for (Class<?> type : classes) {
            for (Class<?> iface : type.getInterfaces()) {
                addInterface(iface, ordered);
            }
        }
        return List.copyOf(ordered);
    }

    static void addInterface(Class<?> iface, Set<Class<?>> ordered) {
        if (ordered.add(iface)) {
            for (Class<?> parent : iface.getInterfaces()) {
                addInterface(parent, ordered);
            }
        }
    }

    static List<Field> fields(Class<?> type, Policy policy) {
        Map<String, Field> chosen = new LinkedHashMap<>();
        for (Class<?> owner : owners(type, policy)) {
            Field[] declared = owner.getDeclaredFields();
            Arrays.sort(declared, Comparator.comparing(Field::getName));
            for (Field field : declared) {
                if (field.isSynthetic()) {
                    continue;
                }
                if (policy.publicOnly()
                        && !Modifier.isPublic(field.getModifiers())) {
                    continue;
                }
                chosen.putIfAbsent(field.getName(), field);
            }
        }
        return List.copyOf(chosen.values());
    }

    static Signature signature(Method method) {
        return new Signature(
                method.getName(),
                List.copyOf(Arrays.asList(method.getParameterTypes())));
    }

    static List<Method> methods(Class<?> type, Policy policy) {
        Map<Signature, Method> chosen = new LinkedHashMap<>();
        for (Class<?> owner : owners(type, policy)) {
            Method[] declared = owner.getDeclaredMethods();
            Arrays.sort(declared,
                    Comparator.comparing(Method::getName)
                            .thenComparing(m -> Arrays.toString(
                                    m.getParameterTypes()))
                            .thenComparing(m -> m.getReturnType().getName()));
            for (Method method : declared) {
                // Compiler có thể sinh bridge cho kiểu trả về hiệp biến.
                if (method.isBridge() || method.isSynthetic()) {
                    continue;
                }
                if (policy.publicOnly()
                        && !Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                chosen.putIfAbsent(signature(method), method);
            }
        }
        return List.copyOf(chosen.values());
    }

    static Optional<Field> field(
            Class<?> type, String name, Policy policy) {
        return fields(type, policy).stream()
                .filter(f -> f.getName().equals(name))
                .findFirst();
    }

    static Optional<Method> method(
            Class<?> type, String name, Policy policy,
            Class<?>... parameterTypes) {
        Signature expected = new Signature(
                name, List.copyOf(Arrays.asList(parameterTypes)));
        return methods(type, policy).stream()
                .filter(m -> signature(m).equals(expected))
                .findFirst();
    }

    public static void main(String[] args) {
        Policy deep = new Policy(Mode.WITH_INTERFACES, false, false);
        Policy publicOnly = new Policy(Mode.WITH_INTERFACES, true, false);

        System.out.println("field id: "
                + field(Leaf.class, "id", deep).orElseThrow()
                        .getDeclaringClass().getSimpleName());
        System.out.println("method score(): "
                + method(Leaf.class, "score", deep).orElseThrow()
                        .getDeclaringClass().getSimpleName());
        System.out.println("method label(): "
                + method(Leaf.class, "label", deep).orElseThrow()
                        .getDeclaringClass().getSimpleName());
        System.out.println("private local (deep): "
                + field(Leaf.class, "local", deep).isPresent());
        System.out.println("private local (public-only): "
                + field(Leaf.class, "local", publicOnly).isPresent());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 HierarchyLookupDemo.java
java HierarchyLookupDemo
~~~

Kết quả chuẩn:

~~~text
field id: Middle
method score(): Middle
method label(): Named
private local (deep): true
private local (public-only): false
~~~

Lý do kết quả: `Middle.id` được xét trước `Parent.id`; `Middle.score()` được xét trước `Parent.score()`; `label()` khai báo tại `Named`, được tìm thấy khi duyệt `Middle → Audited → Named` trong nhánh interface; `local` là `private` nên chỉ tồn tại trong chế độ cho phép thành viên không public. Method bridge sinh cho `Middle.score()` được lọc để không ảnh hưởng quy tắc chọn chữ ký.

Ví dụ trên **chỉ tìm và trả metadata** `Field`/`Method`. Nó không gọi `setAccessible`, `trySetAccessible`, `Field.get` hay `Method.invoke`.

## 5. Biến thể chính sách theo mục đích thực tế

| Mục tiêu | Quy tắc phù hợp |
| --- | --- |
| Chỉ đọc metadata do kiểu hiện tại định nghĩa | `DECLARED_ONLY`; lưu toàn bộ kết quả nếu cần phân biệt khai báo. |
| Mapping dữ liệu theo field của lớp con và lớp cha | `CLASS_HIERARCHY`; định nghĩa xử lý field cùng tên; thường bỏ static/synthetic/transient theo yêu cầu mapper. |
| Tìm annotation trên method của superclass/interface | `WITH_INTERFACES`; duyệt khai báo theo tầng và dùng `getDeclaredAnnotation` tại từng method; xác định rõ quy tắc kế thừa annotation. |
| Liệt kê API công khai của kiểu | Cân nhắc `getMethods()`/`getFields()` hoặc `publicOnly=true`; so sánh vì policy tùy chỉnh không có nghĩa chính xác như Java member resolution. |
| Công cụ phân tích/kiểm toán | Không dùng `putIfAbsent` để loại khai báo trùng; trả về cặp `(declaringClass, member)` và các vị trí xung đột. |

Một điều chỉnh phổ biến khi viết mapper là lọc `Modifier.isStatic(...)` và `Modifier.isTransient(...)` đối với field. Với scanner method phục vụ nghiệp vụ, có thể bỏ method `static`, method trên `Object`, getter không hợp lệ, hoặc method đã bị override, tùy hợp đồng. Không áp dụng các bộ lọc này một cách ngầm định vì chúng thay đổi kết quả.

**Annotation có quy tắc riêng:** `@Inherited` chỉ liên quan đến việc truy vấn annotation trên *class* theo lớp cha, không khiến annotation trên method hoặc interface tự được kế thừa. Muốn hợp nhất annotation từ method của cha/interface cần định nghĩa thuật toán tìm method tương ứng và chính sách ưu tiên tường minh.

## 6. Những giới hạn và bẫy cần tránh

1. **Tên method chưa phải chữ ký:** overload `load(String)` và `load(int)` là hai method khác nhau. Kiểu tham số trong Reflection phản ánh erasure của generic và kiểu primitive khác kiểu wrapper (`int.class` khác `Integer.class`). Method có kiểu trả về hiệp biến có thể sinh bridge; ví dụ loại bridge/synthetic để phục vụ tra cứu nghiệp vụ. Với công cụ phân tích bytecode, cần giữ chúng.
2. **Field hiding khác method overriding:** Java không có field đa hình theo kiểu method; `Parent.id` vẫn tồn tại khi `Middle` khai báo `id`. Nếu mục đích là kiểm kê, việc gộp bằng tên sẽ mất thông tin. Method `private` hoặc `static` cũng không tuân cùng quy tắc override như method instance có thể override.
3. **Thành viên của interface:** interface có thể có default, static và private method. Việc thuật toán duyệt `getDeclaredMethods()` của interface chỉ nói rằng nó *phát hiện khai báo*, không khẳng định mọi method đó đều được lớp implement kế thừa hay gọi trực tiếp. Nếu cần mô phỏng chính xác khả năng gọi theo ngôn ngữ, phải bổ sung điều kiện về tính thừa kế, static/private, access, override và trường hợp xung đột.
4. **Phạm vi truy cập và module:** tìm thấy thành viên `private` không cấp quyền đọc/gọi. `trySetAccessible()` có thể trả về `false`, còn `setAccessible(true)` có thể ném `InaccessibleObjectException` khi Java Platform Module System không mở package tương ứng; `IllegalAccessException` có thể xảy ra khi thao tác. Chỉ nâng quyền khi có nhu cầu và quyền hợp lệ, không tự mở module để “sửa lỗi tìm kiếm”.
5. **Thứ tự và xung đột:** thứ tự thành viên do Reflection trả về không bảo đảm phù hợp thứ tự nguồn; ví dụ sắp xếp để ổn định. Chế độ first-match cần công bố cách chọn khi nhiều interface cùng tên. Trường hợp cần độ chính xác cao, trả về danh sách ứng viên kèm `declaringClass` rồi báo xung đột thay vì âm thầm chọn.
6. **Hiệu năng và class loader:** Reflection lặp lại trên nhiều đối tượng có chi phí. Cache metadata theo `Class<?>` khi cần (ví dụ `ClassValue`), nhưng phải tránh giữ tham chiếu không cần thiết đến các `ClassLoader` có vòng đời ngắn. Không cache kết quả phụ thuộc chính sách mà quên đưa chính sách vào khóa.
7. **Kiểu đặc biệt:** array, primitive, interface, record và proxy có cấu trúc khai báo khác lớp thông thường. Cần kiểm thử riêng khi ứng dụng hỗ trợ chúng; thuật toán minh họa tập trung vào chuỗi kế thừa class/interface thông thường.

## 7. Tình huống kiểm thử tối thiểu cho chính sách

- `DECLARED_ONLY` tại `Leaf` tìm được `local` nhưng không tìm được `id`.
- `CLASS_HIERARCHY` tại `Leaf` tìm `id` từ `Middle`; không tìm `Named.label()` vì không đi interface.
- `WITH_INTERFACES` tại `Leaf` tìm `Named.label()`; thăm interface cha đúng một lần dù gặp cấu trúc kim cương.
- `publicOnly=true` không trả về `Leaf.local`, `Middle.state` hoặc `Parent.secret`.
- `includeObject=false` không lấy các method khai báo tại `Object` như `toString()` khi chưa override; bật cờ thì có thể tìm thấy.
- Trường hợp overload phân biệt tham số; trường hợp override/bridge ưu tiên khai báo đúng kiểu theo chính sách; trường hợp field cùng tên xác minh `getDeclaringClass()`.
- Khi nghiệp vụ cần giữ mọi khai báo, kiểm thử rằng thuật toán kiểm kê **không** dùng dedup như thuật toán chọn một kết quả.

**Nguyên tắc triển khai:** viết rõ tập kiểu cần duyệt, thứ tự, điều kiện lọc, khóa đồng nhất, cách xử lý xung đột và giới hạn truy cập; sau đó hiện thực các lựa chọn đó thành code có thể kiểm thử. Reflection là công cụ đọc metadata, còn ý nghĩa của việc “tìm xuyên phân cấp” do hợp đồng của ứng dụng quyết định.


</details>

- [Quay lại đầu trang](#back-to-top)
