<a id="back-to-top"></a>

# MethodHandle và VarHandle

## Menu
- [Phân biệt core reflection với java.lang.invoke](#method-handles-var-handles-001)
- [MethodHandles.Lookup và quyền truy cập được kiểm tra khi tạo handle](#method-handles-var-handles-002)
- [findVirtual, findStatic, findConstructor và findSpecial](#method-handles-var-handles-003)
- [unreflect Method, Constructor hoặc Field](#method-handles-var-handles-004)
- [MethodType và kiểm tra chữ ký](#method-handles-var-handles-005)
- [invoke, invokeExact và khác biệt chuyển đổi đối số](#method-handles-var-handles-006)
- [privateLookupIn và điều kiện mở truy cập theo module](#method-handles-var-handles-007)
- [VarHandle cho field hoặc phần tử mảng](#method-handles-var-handles-008)
- [Access mode thường, volatile, acquire-release và atomic](#method-handles-var-handles-009)
- [Khi nào chọn Method, MethodHandle hoặc VarHandle](#method-handles-var-handles-010)
- [Không mặc định một cơ chế luôn nhanh hơn cơ chế còn lại](#method-handles-var-handles-011)

## <a id="method-handles-var-handles-001">Phân biệt core reflection với java.lang.invoke</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt core reflection với `java.lang.invoke`

## 1. Hai API cùng làm việc với thành phần Java tại runtime nhưng có vai trò khác nhau

**Core reflection** là tập API chủ yếu trong `java.lang.Class` và `java.lang.reflect` (`Method`, `Constructor`, `Field`, `Parameter`, `Modifier`, `RecordComponent`...). Nó phù hợp khi chương trình cần **khám phá metadata** của một kiểu: tìm phương thức, trường, constructor, annotation, tham số, modifier, kiểu generic; sau đó có thể gọi phương thức, tạo đối tượng hoặc đọc/ghi trường qua các đối tượng metadata.

**`java.lang.invoke`** cung cấp các đối tượng thao tác được định kiểu như `MethodHandle`, `VarHandle`, cùng `MethodHandles.Lookup`, `MethodType`, `CallSite` và các công cụ kết hợp handle. Nó phù hợp khi đã xác định thao tác cần thực hiện và muốn **biểu diễn lời gọi hoặc quyền truy cập dưới dạng handle có chữ ký cụ thể**, dễ kết hợp/chuyển đổi cho dynamic invocation; `VarHandle` còn hỗ trợ các chế độ truy cập bộ nhớ và thao tác nguyên tử.

Chúng có phần giao nhau, nhưng không phải API thay thế toàn bộ cho nhau: `MethodHandle` không cung cấp kho metadata annotation/generic như `Method`, còn `Method` không thay thế các access mode và compare-and-set của `VarHandle`. Cả hai vẫn tuân thủ kiểm soát truy cập của ngôn ngữ và hệ thống module.

| Câu hỏi | Core reflection | `java.lang.invoke` |
| --- | --- | --- |
| Đối tượng chính | `Class<?>`, `Method`, `Constructor<?>`, `Field` | `MethodHandle`, `VarHandle`, `MethodType`, `MethodHandles.Lookup` |
| Tìm thành viên | `getMethod`, `getDeclaredMethods`, `getDeclaredField`… | `Lookup.findVirtual`, `findStatic`, `findConstructor`, `findVarHandle`… |
| Khám phá annotation/generic/parameter | Mạnh và trực tiếp qua metadata | Không phải nhiệm vụ chính; thường dùng reflection nếu cần |
| Gọi method | `Method.invoke(receiver, Object...)` | `handle.invokeExact(...)`, `handle.invoke(...)`, `invokeWithArguments(...)` |
| Tạo instance | `Constructor.newInstance(Object...)` | Constructor `MethodHandle` có chữ ký kiểu `(args)Instance` |
| Trường | `Field.get/set` cùng các biến thể primitive | `VarHandle.get/set`, CAS, atomic update, memory ordering |
| Kiểu tại điểm gọi | Tham số/giá trị trả về đi qua `Object`, có boxing/unboxing | `MethodType` và chữ ký tại điểm gọi, đặc biệt nghiêm ngặt với `invokeExact` |
| Ngoại lệ do method đích ném | `Method.invoke` gói trong `InvocationTargetException` | `MethodHandle` truyền trực tiếp ngoại lệ của đích |
| Quyền truy cập | Java access checks, `setAccessible` trong giới hạn module | Quyền nằm ở `Lookup`; tra cứu handle phải thỏa access checks |

## 2. Core reflection: khám phá trước, gọi qua đối tượng metadata

Ví dụ đoạn code đặt trong một phương thức (chưa phải chương trình độc lập):

```java
Method method = String.class.getMethod("substring", int.class);
Object answer = method.invoke("java", 2);
System.out.println(answer); // va
```

`getMethod` tìm phương thức public theo **tên và các kiểu tham số**, còn `Method.invoke` yêu cầu receiver với phương thức instance. Kết quả có kiểu biên dịch `Object` dù `substring` khai báo trả `String`; đối số nguyên thủy `2` được boxing trong lời gọi varargs. Với phương thức `static`, receiver truyền cho `Method.invoke` có thể là `null`.

Reflection hữu ích khi chưa biết cấu trúc kiểu lúc biên dịch. Ví dụ framework kiểm tra annotation để ánh xạ HTTP route, công cụ serialize cần đọc field/generic type, test runner quét method có annotation, hoặc trình khám phá class liệt kê constructor và modifier. `getMethods()` chỉ lấy các public method (kể cả được kế thừa), còn `getDeclaredMethods()` lấy method khai báo trực tiếp trong class, bất kể modifier nhưng không lấy method kế thừa. Tìm thấy thành viên private **không đồng nghĩa có quyền gọi**.

Các ràng buộc cần nhớ:

- `Method.invoke` có thể thực hiện các chuyển đổi được reflection cho phép, như unboxing và một số widening primitive; nó không ép kiểu tùy ý.
- `Method.invoke` gói exception do code đích ném vào `InvocationTargetException`; lấy `getCause()` để xem lỗi gốc. Lỗi tra cứu và lỗi truy cập phát sinh theo các nhánh ngoại lệ khác.
- `Field.getInt`/`setInt` thao tác với trường `int` mà không cần tự ép `Object`; tuy nhiên `Field` không tự cung cấp compare-and-set nguyên tử và các access mode của `VarHandle`.
- `setAccessible(true)` không luôn thành công: strong encapsulation của Java Platform Module System vẫn đặt giới hạn.

## 3. `MethodHandle`: tham chiếu lời gọi có chữ ký rõ ràng

Một `MethodHandle` biểu diễn một phép gọi có kiểu. `MethodType` mô tả chính xác danh sách tham số và kiểu trả về của handle; `handle.type()` cho biết chữ ký **sau khi handle được tạo**, không nhất thiết giống chữ ký khai báo ban đầu của method.

```java
MethodHandles.Lookup lookup = MethodHandles.lookup();
MethodHandle substring = lookup.findVirtual(
        String.class, "substring", MethodType.methodType(String.class, int.class));
System.out.println(substring.type()); // (String,int)String
String answer = (String) substring.invokeExact("java", 2);
System.out.println(answer); // va
```

Đây là fragment cần đặt trong method khai báo `throws Throwable` (hoặc bắt exception) và import `java.lang.invoke.*`. Đối với `findVirtual`, `MethodType.methodType(String.class, int.class)` mô tả **method đích** `substring(int) -> String`; handle thu được có thêm receiver `String` ở vị trí đầu tiên, nên kiểu thực tế là `(String,int)String`.

Những cách tạo handle thường dùng:

| Phương thức `Lookup` | Phép gọi thu được |
| --- | --- |
| `findVirtual(Owner.class, name, methodType)` | Instance method; receiver đứng đầu tham số của handle |
| `findStatic(Owner.class, name, methodType)` | Static method; không có receiver |
| `findConstructor(Owner.class, methodType(void.class, ...))` | Gọi constructor; handle trả về instance `Owner` |
| `findGetter` / `findSetter` | Handle thao tác trường tương ứng, theo quyền lookup |
| `unreflect(Method)` / `unreflectConstructor(Constructor)` | Chuyển đối tượng reflection thành handle nếu có quyền cần thiết |
| `findSpecial(...)` | Lời gọi mang ngữ nghĩa `invokespecial`, cần ngữ cảnh và quyền đặc biệt |

Các phương thức kết hợp như `bindTo(receiver)`, `asType(newType)`, `filterArguments` hoặc `guardWithTest` có thể tạo handle mới từ handle đang có. Điều này hữu ích trong bộ điều phối lời gọi động khi muốn chuẩn bị đường gọi một lần rồi tái sử dụng.

**`invokeExact` và `invoke` khác nhau ở việc khớp chữ ký:**

- `invokeExact`: chữ ký tại call site, gồm receiver, số lượng, kiểu tham số **và kiểu trả về**, phải khớp chính xác `handle.type()`; sai sẽ gây `WrongMethodTypeException`. Vì thế thường phải ép kiểu kết quả, ví dụ `int n = (int) handle.invokeExact(obj, 3);`.
- `invoke`: cho phép các chuyển đổi theo quy tắc `asType` khi tương thích, chẳng hạn boxing/unboxing và reference cast phù hợp. Nó vẫn kiểm tra kiểu, không tự biến mọi `Object` thành kiểu tùy ý.
- `invokeWithArguments`: chấp nhận danh sách đối số động thông qua `Object`/`List`, tiện khi chưa có signature tĩnh nhưng thêm overhead và chuyển đổi; không phải lúc nào cũng nên dùng.

Ví dụ `MethodHandle` xử lý target exception trực tiếp, không tự đóng gói nó trong `InvocationTargetException` như `Method.invoke`. Khi thiết kế API public, không nhất thiết lan truyền `throws Throwable` khắp ứng dụng; có thể bắt/ánh xạ lỗi tại lớp adapter.

## 4. `VarHandle`: truy cập biến có semantics bộ nhớ/atomic

`VarHandle` mô tả quyền truy cập một **biến**: instance field, static field hoặc phần tử mảng. Khác `Field`, nó có các nhóm access mode và có thể tham gia thuật toán đồng bộ không khóa khi loại biến và access mode hỗ trợ. Một handle có kiểu giá trị (`varType()`) và danh sách tọa độ (`coordinateTypes()`); ví dụ instance field `Counter.value` có kiểu giá trị `int` và tọa độ `Counter`, còn phần tử `int[]` có tọa độ `int[]` cùng chỉ số `int`.

| Nhóm access mode | Ví dụ | Ý nghĩa chủ đạo |
| --- | --- | --- |
| Plain | `get`, `set` | Truy cập thông thường, không yêu cầu ordering bổ sung cho đồng bộ giữa thread |
| Opaque | `getOpaque`, `setOpaque` | Ordering yếu hơn acquire/release, nhưng có tính nhất quán riêng của opaque access |
| Acquire / Release | `getAcquire`, `setRelease` | Công bố/tiếp nhận dữ liệu theo ordering acquire/release khi phối hợp đúng |
| Volatile | `getVolatile`, `setVolatile` | Ngữ nghĩa truy cập volatile trong Java Memory Model |
| Atomic | `compareAndSet`, `compareAndExchange`, `getAndSet`, `getAndAdd` | Thao tác read-modify-write nguyên tử, với ngữ nghĩa ordering cụ thể theo access mode |

**Không phải mọi mode đều hỗ trợ cho mọi biến.** Chẳng hạn `getAndAdd` không có ý nghĩa cho mọi kiểu reference; dùng `isAccessModeSupported(VarHandle.AccessMode.GET_AND_ADD)` nếu cần kiểm tra động. Với field `final`, các mode ghi hay cập nhật nguyên tử không được hỗ trợ. Không suy luận rằng `get` hay `set` thông thường bảo đảm đồng bộ giữa thread; chọn mode và thiết kế happens-before phù hợp.

Đối với mảng, có thể tạo handle mà không có field:

```java
VarHandle element = MethodHandles.arrayElementVarHandle(int[].class);
int[] values = {4, 8};
boolean swapped = element.compareAndSet(values, 1, 8, 9);
System.out.println(swapped + ", " + values[1]); // true, 9
```

Đây là fragment cần import `java.lang.invoke.*`; `compareAndSet` dùng tọa độ `(array, index)` rồi tới expected/new value. Trong bài toán concurrency, tránh suy nghĩ rằng một CAS đơn lẻ bảo đảm mọi invariants của cấu trúc dữ liệu nhiều trường: còn cần phân tích tranh chấp, visibility, vòng lặp retry và trường hợp ABA nếu có.

## 5. Ví dụ Java 17+ hoàn chỉnh: cùng một lớp, ba cách tương tác

Lưu mã sau thành tệp **`ReflectionInvokeDemo.java`**. Ví dụ dùng một nested class `Counter` có constructor, instance method, static method và private field. Code ở cùng nest nên lookup có quyền truy cập private field trong ví dụ; với class nằm ở module/package khác phải xét lại access control.

```java
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectionInvokeDemo {
    public static final class Counter {
        private int value;

        public Counter(int initial) { this.value = initial; }

        public int add(int delta) {
            value += delta;
            return value;
        }

        public static String format(String label, int value) {
            return label + "=" + value;
        }
    }

    public static void main(String[] args) throws Throwable {
        // Core reflection: tìm metadata rồi gọi/đọc/ghi.
        Constructor<Counter> constructor = Counter.class.getConstructor(int.class);
        Counter a = constructor.newInstance(5);
        Method method = Counter.class.getMethod("add", int.class);
        Object reflectResult = method.invoke(a, 2);
        Field field = Counter.class.getDeclaredField("value");
        field.setAccessible(true);
        field.setInt(a, 9);
        System.out.println("reflect invoke: " + reflectResult);
        System.out.println("reflect field: " + field.getInt(a));

        // MethodHandle: tạo handle có chữ ký rõ ràng rồi gọi.
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodHandle mhCtor = lookup.findConstructor(
                Counter.class, MethodType.methodType(void.class, int.class));
        Counter b = (Counter) mhCtor.invokeExact(10);
        MethodHandle mhAdd = lookup.findVirtual(
                Counter.class, "add", MethodType.methodType(int.class, int.class));
        int result = (int) mhAdd.invokeExact(b, 3);
        MethodHandle mhStatic = lookup.findStatic(
                Counter.class, "format",
                MethodType.methodType(String.class, String.class, int.class));
        String description = (String) mhStatic.invokeExact("counter", result);
        System.out.println("handle invokeExact: " + result);
        System.out.println("static handle: " + description);

        // VarHandle: truy cập field và cập nhật nguyên tử.
        VarHandle var = lookup.findVarHandle(Counter.class, "value", int.class);
        int before = (int) var.get(b);
        var.set(b, 20);
        boolean changed = var.compareAndSet(b, 20, 25);
        int previous = (int) var.getAndAdd(b, 5);
        System.out.println("var before: " + before);
        System.out.println("var CAS: " + changed);
        System.out.println("var getAndAdd previous: " + previous);
        System.out.println("var volatile read: " + (int) var.getVolatile(b));
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 ReflectionInvokeDemo.java
java ReflectionInvokeDemo
```

Stdout:

```text
reflect invoke: 7
reflect field: 9
handle invokeExact: 13
static handle: counter=13
var before: 13
var CAS: true
var getAndAdd previous: 25
var volatile read: 30
```

Diễn giải: biến `a` tăng từ `5` lên `7` bằng reflection rồi bị đặt thành `9`. Biến `b` được tạo qua constructor handle với `10`, tăng thành `13` qua instance method handle. `VarHandle` đọc `13`, đặt `20`, đổi có điều kiện `20 -> 25`, sau đó tăng `25 -> 30`; `getAndAdd` trả **giá trị cũ `25`**. Các thao tác chạy tuần tự để minh họa contract; ví dụ không tuyên bố chứng minh tính đúng đắn của thuật toán nhiều thread.

## 6. Lỗi thường gặp và câu hỏi tự kiểm tra

- Dùng `getDeclaredMethod` rồi nghĩ đã được phép gọi member private: **sai**; tìm thấy metadata và có quyền thực thi là hai bước riêng.
- Đưa toàn bộ tham số vào `Object[]` khi dùng `invokeExact` và kỳ vọng tự thích ứng: **sai**; kiểu tại call site phải khớp handle, hoặc chủ động dùng `asType`/`invoke`/`invokeWithArguments` theo mục đích.
- Nghĩ `MethodType` của `findVirtual` đã gồm receiver: **sai**; receiver xuất hiện trong kiểu của handle kết quả, còn `MethodType` truyền vào mô tả phương thức đích.
- Coi `VarHandle.set` thông thường và `setVolatile` là có semantics bộ nhớ như nhau: **sai**; ordering là một phần contract.
- Bỏ qua `Lookup`/JPMS hoặc xem `privateLookupIn` như cửa hậu tự động: **sai**; kiểm tra quyền vẫn được áp dụng.

Tự kiểm tra:

1. Muốn lấy annotation của một method chưa biết tên lúc biên dịch, dùng `Method` hay `MethodHandle`? **`Method`** qua core reflection.
2. Với `findVirtual` của method `int inc(int x)`, handle có cần tham số receiver không? **Có**, receiver đứng trước `int x`.
3. Muốn cập nhật một field `int` có điều kiện nguyên tử, dùng `Field.setInt` hay `VarHandle.compareAndSet`? **`VarHandle.compareAndSet`**.
4. Target ném `IOException`: `Method.invoke` và `invokeExact` biểu hiện giống hệt nhau không? **Không**; reflection gói target exception trong `InvocationTargetException`, method handle chuyển tiếp trực tiếp exception.
5. `VarHandle.getAndAdd(receiver, 5)` trả về gì? **Giá trị trước phép cộng**, khi mode đó được hỗ trợ.

Đọc thêm: [Javadoc `java.lang.reflect.Method` (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html), [Javadoc `MethodHandle`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandle.html), [Javadoc `MethodHandles.Lookup`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandles.Lookup.html), [Javadoc `VarHandle`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/VarHandle.html), [JEP 416](https://openjdk.org/jeps/416).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-handles-var-handles-002">MethodHandles.Lookup và quyền truy cập được kiểm tra khi tạo handle</a>

<details>
<summary>Xem chi tiết</summary>

# `MethodHandles.Lookup`: quyền được kiểm tra khi tạo handle

Trong Java, `MethodHandle` là một tham chiếu có kiểu đến một phương thức hoặc constructor; `VarHandle` là một đối tượng cung cấp những thao tác truy cập biến/trường/mảng theo kiểu được kiểm soát. Để lấy handle đến **thành viên đã đặt tên** (method, constructor, field), ta thường dùng `java.lang.invoke.MethodHandles.Lookup`. Điểm mấu chốt: **quyền truy cập được kiểm tra lúc tạo handle; khi đã có handle hợp lệ, việc gọi nó không lặp lại kiểm tra quyền truy cập Java vào thành viên đích**. Việc gọi vẫn chịu kiểm tra kiểu, những giới hạn thao tác và ngoại lệ khi mã đích chạy.

Đây là lý do `Lookup` vừa là công cụ tìm thành viên, vừa là một **khả năng được ủy quyền (capability)**: nó mang ngữ cảnh và mức quyền của mã đã tạo ra nó. Nắm rõ ranh giới này giúp viết thư viện, framework, mã Reflection/MethodHandle và mã tương tác với Java Platform Module System (JPMS) đúng và an toàn.

## 1. `Lookup` chứa ngữ cảnh truy cập nào?

`MethodHandles.Lookup` là đối tượng dùng làm “nhà máy” tạo handle. Nó ghi nhận:

- **Lookup class**: lớp mà quyền truy cập được xét theo; lấy bằng `lookup.lookupClass()`.
- **Lookup modes**: các mức quyền còn hiệu lực, chẳng hạn `PUBLIC`, `PACKAGE`, `PROTECTED`, `PRIVATE`, `MODULE`; có thể xem bitmask qua `lookup.lookupModes()`. Các bit này không có nghĩa là được phép truy cập mọi lớp/thành viên ở bất kỳ module nào.
- **Ngữ cảnh module**: quan hệ đọc module, các package được `exports`/`opens` và, với lookup được chuyển sang module khác, cả ngữ cảnh lookup trước đó có thể ảnh hưởng phạm vi truy cập.

Quyền được quyết định từ **lớp gọi đã tạo `Lookup`**, chế độ lookup, khả năng truy cập lớp đích và thành viên đích. Đặt `Lookup` trong một biến tên `admin` hoặc truyền một `Class<?>` khác vào phương thức tìm kiếm không làm tăng quyền.

### Ba cách lấy lookup phổ biến

| Cách | Phạm vi và tác dụng | Giới hạn cần nhớ |
| --- | --- | --- |
| `MethodHandles.lookup()` | Lookup gắn với **lớp đang gọi lệnh** này; thông thường có đầy đủ quyền tương ứng lớp đó, bao gồm private của chính lớp/nestmates và quyền được Java cho phép | Không tự đọc được `private` của một lớp top-level khác chỉ vì cùng package; không vượt qua encapsulation của module khác |
| `MethodHandles.publicLookup()` | Lookup chỉ dành cho các lớp/thành viên `public` đủ điều kiện; hữu ích khi thư viện chỉ cần API công khai | Không lấy được `private`/`package-private`; trong JPMS, đối tượng này chỉ thấy các API công khai ở package được export vô điều kiện |
| `MethodHandles.privateLookupIn(Target.class, callerLookup)` | Tạo lookup có khả năng truy cập private của `Target` **nếu** `callerLookup` được phép thực hiện deep reflection đối với lớp này | Lookup nguồn phải có `PRIVATE` và `MODULE`; giữa các module còn phải đáp ứng điều kiện đọc module và package đích phải được `opens` đến module của bên gọi |

`lookup.in(Target.class)` chỉ **chuyển ngữ cảnh lookup với quyền có thể bị thu hẹp**, không phải lệnh cấp quyền private. Chuyển sang nest/package/module khác có thể làm mất các mode tương ứng. `dropLookupMode(...)` cũng có thể giảm quyền có chủ đích. Muốn truy cập private của lớp khác, dùng `privateLookupIn` khi đủ điều kiện, không coi `in` là lối tắt.

## 2. Quyền được xét ở thời điểm nào?

Xét luồng tìm một method thông qua `findVirtual`:

1. Mã gọi tạo hoặc nhận một `Lookup`; lookup giữ danh tính lớp và các mode truy cập.
2. Mã yêu cầu `lookup.findVirtual(Target.class, "work", MethodType.methodType(ReturnType.class, ...))`.
3. Java phân giải lớp/thành viên và **kiểm tra truy cập**: lớp `Target` có khả kiến không, thành viên có hợp lệ với mode của lookup không, và các ràng buộc module/protected khác có thỏa không.
4. Nếu hợp lệ, API trả một `MethodHandle` có kiểu xác định. Nếu không, thao tác **tạo handle** thất bại, thường bằng `IllegalAccessException` cho trường hợp không đủ quyền hoặc `NoSuchMethodException` cho tìm kiếm method không phù hợp.
5. Bất kỳ mã nào nhận được handle có thể gọi nó theo kiểu và điều kiện invocation của handle, kể cả khi mã đó không thể tự tạo một handle tương đương. **Không có kiểm tra quyền private mới mỗi lần gọi.**

Các API thường kiểm tra truy cập tại lúc tạo gồm `findStatic`, `findVirtual`, `findSpecial`, `findConstructor`, `findGetter`, `findSetter`, `findVarHandle`, `findStaticVarHandle` và nhiều dạng `unreflect`. Khi gọi `unreflect` từ một đối tượng Reflection đã chỉnh trạng thái accessible, hành vi còn phụ thuộc quy tắc tương ứng của API; không nên suy ra rằng mọi `Lookup` đều được vượt quyền.

Đối với `VarHandle`, kiểm tra quyền lúc lấy handle không có nghĩa **mọi access mode** đều khả dụng: ví dụ việc `set` qua VarHandle của một trường `final` thường không được hỗ trợ, và có thể ném `UnsupportedOperationException`. Khả năng get/set/atomic operation và tính đúng kiểu của từng thao tác vẫn là ràng buộc lúc sử dụng.

### Tách lỗi tạo handle khỏi lỗi gọi handle

| Thời điểm | Ví dụ | Ý nghĩa |
| --- | --- | --- |
| **Tạo** | `findVirtual` yêu cầu private member mà lookup không được phép thấy | `IllegalAccessException` |
| **Tạo** | Tên, loại method hoặc chữ ký `MethodType` không tìm thấy | `NoSuchMethodException` (đối với method); tìm field sai có thể là `NoSuchFieldException` |
| **Gọi** | `invokeExact` được gọi với kiểu tham số/trả về không khớp chính xác kiểu của handle | `WrongMethodTypeException` |
| **Gọi** | Giá trị tham số cần ép kiểu tham chiếu nhưng không tương thích | Có thể xảy ra `ClassCastException` |
| **Gọi** | Chính phương thức đích ném ngoại lệ | Ngoại lệ của phương thức đích được truyền ra theo cách gọi handle |

`invokeExact` yêu cầu descriptor tại call site khớp chính xác kiểu của handle; `invoke` cho phép một số phép điều chỉnh kiểu hợp lệ. Sự khác nhau đó là **kiểm tra kiểu khi gọi**, không phải cấp lại quyền đọc private. Ngoài ra, `MethodHandle.asType(...)` tạo handle điều chỉnh kiểu mà không tự đi tìm lại thành viên private, nên không cần `Lookup` mới cho lần điều chỉnh ấy.

## 3. Ví dụ Java 17+: được phép tạo handle, không được phép tự tìm private

Lưu **nguyên vẹn** ví dụ sau vào tệp `LookupAccessDemo.java`. Tệp chứa hai lớp top-level; `SecretBox` **không** phải lớp lồng/nestmate của `LookupAccessDemo`. Điều này quan trọng vì nestmates trong Java hiện đại có thể chia sẻ quyền private theo quy tắc riêng.

~~~java
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

public class LookupAccessDemo {
    public static String greeting() {
        return "hello";
    }

    private static void consume(MethodHandle handle, SecretBox box)
            throws Throwable {
        // consume không có quyền tự tìm SecretBox.message().
        // Nhưng nó được phép sử dụng handle đã được trao.
        String value = (String) handle.invokeExact(box);
        System.out.println("delegated: " + value);
    }

    public static void main(String[] args) throws Throwable {
        MethodType noArgsString = MethodType.methodType(String.class);

        MethodHandles.Lookup publicView = MethodHandles.publicLookup();
        MethodHandle greetingHandle = publicView.findStatic(
                LookupAccessDemo.class, "greeting", noArgsString);
        System.out.println("public: " + (String) greetingHandle.invokeExact());

        MethodHandles.Lookup caller = MethodHandles.lookup();
        try {
            caller.findVirtual(SecretBox.class, "message", noArgsString);
            System.out.println("unexpected success");
        } catch (IllegalAccessException expected) {
            System.out.println("normal lookup: denied");
        }

        MethodHandles.Lookup privateView =
                MethodHandles.privateLookupIn(SecretBox.class, caller);

        MethodHandle secretHandle = privateView.findVirtual(
                SecretBox.class, "message", noArgsString);

        SecretBox box = new SecretBox();
        System.out.println(
                "private lookup: " + (String) secretHandle.invokeExact(box));

        consume(secretHandle, box);

        VarHandle countHandle = privateView.findVarHandle(
                SecretBox.class, "count", int.class);
        System.out.println("private VarHandle: " + (int) countHandle.get(box));

        try {
            publicView.findVirtual(SecretBox.class, "message", noArgsString);
            System.out.println("unexpected success");
        } catch (IllegalAccessException expected) {
            System.out.println("public lookup: denied");
        }

        System.out.println(
                "receiver count: " + secretHandle.type().parameterCount());
    }
}

class SecretBox {
    private int count = 7;

    private String message() {
        return "secret=42";
    }
}
~~~

Chạy trong terminal có JDK 17 hoặc mới hơn:

~~~bash
javac LookupAccessDemo.java
java LookupAccessDemo
~~~

Kết quả tiêu chuẩn:

~~~text
public: hello
normal lookup: denied
private lookup: secret=42
delegated: secret=42
private VarHandle: 7
public lookup: denied
receiver count: 1
~~~

Giải thích từng điểm:

- `publicLookup().findStatic(...)` thành công vì `LookupAccessDemo` là lớp public và `greeting()` là phương thức public static. Handle nhận được có kiểu `()String`.
- `caller` là lookup của `LookupAccessDemo`, có thể truy cập phần phù hợp với quyền của lớp này, **không được truy cập private của `SecretBox`** dù hai lớp cùng package.
- `privateLookupIn(SecretBox.class, caller)` thành công trong ví dụ cùng unnamed module, với lookup nguồn đủ quyền. Sau đó `findVirtual` tạo được handle cho `SecretBox.message()` và `findVarHandle` tạo được handle cho trường `count`.
- Phương thức `consume` chỉ **nhận** handle. Nó không cần tự tra cứu hoặc được kiểm tra lại quyền private khi `invokeExact(box)`. Đó chính là việc **trao capability** cho mã khác.
- `publicView` không đủ quyền tìm `SecretBox.message()`; lớp `SecretBox` còn là kiểu package-private, nên bản thân lớp cũng không đáp ứng điều kiện nhìn thấy của public lookup.
- `MethodType.methodType(String.class)` mô tả **các tham số khai báo** của method `message()` (không có tham số). Nhưng handle được tạo bởi `findVirtual` cần receiver `SecretBox` ở vị trí đầu, do đó handle có kiểu `(SecretBox)String` và `parameterCount()` bằng `1`.

Lưu ý: khối `catch (IllegalAccessException ...)` ở đây chủ ý thử **truy cập không được phép khi tạo handle**. Không dùng exception như cơ chế phân quyền chung của ứng dụng; nếu thành viên không tồn tại hoặc chữ ký sai thì phải xử lý loại lỗi tìm kiếm tương ứng.

## 4. Phân biệt với Reflection và phương án cấp quyền khác

Với core Reflection, `Method.invoke` nhìn chung thực hiện kiểm tra quyền truy cập lúc gọi theo cơ chế của Reflection (trạng thái `AccessibleObject` có thể ảnh hưởng); với handle đã được tạo thành công qua `Lookup`, việc invoke về sau không lặp lại bước xét quyền Java cho thành viên gốc. Vì thế:

- `getDeclaredMethod("hidden")` có thể tìm metadata của một method private, nhưng **tìm được method** khác với **được quyền gọi** hoặc **được quyền tạo MethodHandle** cho nó.
- `setAccessible(true)` không phải chìa khóa vượt JPMS tùy ý: truy cập sâu còn bị giới hạn bởi module, và `InaccessibleObjectException` có thể xuất hiện.
- `lookup.findVirtual(...)` có kiểm tra quyền lúc lấy handle; `handle.invokeExact(...)` kiểm tra descriptor và thực hiện mã đích. Đừng trộn lẫn hai thời điểm.
- Trao một `MethodHandle` tới private method hoặc `VarHandle` tới private field cho plugin/callback tương đương với **trao khả năng sử dụng thành viên đó**. Giấu tên method/field sau khi trao handle không thu hồi được capability.
- Trao nguyên đối tượng `Lookup` có nhiều quyền còn rộng hơn: người nhận có thể yêu cầu tạo **những handle khác** trong phạm vi quyền lookup. Nếu chỉ muốn gọi một hành vi được chọn, cấp handle hẹp hoặc một API wrapper thay vì lookup đặc quyền.
- Nếu cần chạy với quyền ít hơn, chủ động dùng `publicLookup()`, `dropLookupMode(...)` hoặc API công khai được thiết kế cho phía tiêu thụ; cần cân nhắc module `exports`/`opens` chính xác.

Khi dùng `MethodHandle` trong framework, hãy xác định ai tạo lookup, lớp nào là lookup class, package của lớp đích có đọc/mở/xuất đúng không, và handle được trao cho ai. Khả năng tạo handle và khả năng sử dụng handle là hai ranh giới quyền khác nhau.

## 5. Những nhầm lẫn phổ biến

| Nhầm lẫn | Điều đúng |
| --- | --- |
| “`MethodHandles.lookup()` cho phép lấy private của mọi lớp cùng package.” | Lookup có quyền gắn với **lớp gọi**; private của lớp top-level khác cần cơ chế hợp lệ như `privateLookupIn`. |
| “`publicLookup()` đọc được mọi member `public` bất kể lớp/module.” | Lớp chứa member phải có thể truy cập và module/package phải đáp ứng ràng buộc công khai. |
| “`lookup.in(Target.class)` giúp nâng quyền lên private của `Target`.” | `in` có thể **làm mất** mode; không làm tăng quyền. |
| “`exports` đủ để lấy private member xuyên module.” | `exports` phục vụ API public; deep reflection xuyên module cần `opens` và các điều kiện lookup khác. |
| “Mỗi `invokeExact` lại kiểm tra xem caller có quyền private hay không.” | Access check dành cho thành viên diễn ra khi **tạo** handle; invocation kiểm tra kiểu và chạy mã. |
| “Tạo được VarHandle là lúc nào cũng `set` được.” | Access mode còn phụ thuộc loại biến, `final` và tính năng được hỗ trợ. |
| “Trao handle private cho bên thứ ba là vô hại.” | Bên nhận có thể dùng handle đã được trao; cần kiểm soát phạm vi ủy quyền. |

### Câu hỏi tự kiểm tra

1. Nếu `A` và `B` là hai lớp top-level trong cùng package, vì sao `MethodHandles.lookup()` từ `A` không thể tự dùng `findVirtual` để mở private method của `B`?
2. Tại sao `privateLookupIn` thành công trong cùng module nhưng có thể thất bại giữa hai named module dù lớp đích là `public`?
3. Khi `findVirtual` thành công và truyền handle cho mã không biết lớp đích, quyền thành viên được kiểm tra lúc nào? Còn kiểu đối số được kiểm tra lúc nào?
4. Vì sao `findVirtual(SomeClass.class, "f", MethodType.methodType(String.class))` tạo handle có thêm một tham số receiver so với `findStatic` cho phương thức không tham số?

**Kết luận:** `Lookup` quyết định **bên nào được tạo handle đến thành viên nào**; handle được tạo thành công là capability có thể được sử dụng hoặc chuyển giao. Kiểm tra quyền truy cập xảy ra khi tạo handle, còn kiểm tra kiểu, hỗ trợ thao tác và ngoại lệ đích vẫn áp dụng khi dùng handle. Nắm ranh giới này là chìa khóa để dùng `MethodHandle`/`VarHandle` đúng với Java 17+ và JPMS.

*Tài liệu đối chiếu: [Java SE 21 — MethodHandles.Lookup](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/invoke/MethodHandles.Lookup.html) và [Java SE 21 — MethodHandle](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/invoke/MethodHandle.html).*

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-handles-var-handles-003">findVirtual, findStatic, findConstructor và findSpecial</a>

<details>
<summary>Xem chi tiết</summary>

# `findVirtual`, `findStatic`, `findConstructor` và `findSpecial` trong Java MethodHandle

## 1. Bốn cách tìm lời gọi và mô hình tư duy

`java.lang.invoke.MethodHandle` là một đối tượng đại diện cho thao tác có thể gọi bằng Java: gọi phương thức instance, phương thức static, tạo đối tượng hoặc gọi một phương thức theo ngữ nghĩa đặc biệt. Ta không gọi trực tiếp tên phương thức qua `MethodHandle`; trước tiên dùng `MethodHandles.Lookup` để **tra cứu** thao tác hợp lệ, sau đó gọi handle.

Một `MethodHandle` có kiểu chạy được mô tả bởi `MethodType`: danh sách kiểu tham số và kiểu kết quả. Kiểu này phải được hiểu chính xác, bởi kiểu của handle sau tra cứu **có thể khác** kiểu khai báo của phương thức Java. Đặc biệt, handle cho phương thức instance có thêm **receiver** (đối tượng nhận lời gọi) ở vị trí tham số đầu tiên; handle constructor nhận tham số constructor nhưng trả về đối tượng mới, mặc dù khi tra cứu phải khai báo kiểu trả về `void`.

Bốn lời gọi được học ở đây đều nằm trên `MethodHandles.Lookup`:

| API | Tìm thao tác | `MethodType` truyền vào | Kiểu handle nhận được | Điểm phân biệt |
| --- | --- | --- | --- | --- |
| `findVirtual(refc, name, type)` | Phương thức instance | Kiểu kết quả và tham số **không gồm receiver** | `(Receiver, tham_số...)Kết_quả` | Dùng cơ chế dispatch instance phù hợp khi gọi |
| `findStatic(refc, name, type)` | Phương thức `static` | Đúng kiểu kết quả và tham số đã khai báo | `(tham_số...)Kết_quả` | Không có receiver |
| `findConstructor(refc, type)` | Constructor | **`void`** và tham số constructor | `(tham_số...)refc` | Tạo và trả về instance mới |
| `findSpecial(refc, name, type, specialCaller)` | Gọi instance theo ngữ nghĩa `invokespecial` | Kiểu kết quả và tham số **không gồm receiver** | `(specialCaller, tham_số...)Kết_quả`, theo quy tắc truy cập | Có thể gọi phiên bản ở lớp cha mà bỏ qua override, nếu lookup đủ quyền |

Trong bảng, `refc` là lớp hoặc interface dùng để phân giải thành viên; `name` là tên phương thức; `type` có kiểu `MethodType`. `specialCaller` là ngữ cảnh lớp đặc biệt được phép thực hiện `invokespecial`, không phải một chuỗi tên tùy ý. Java kiểm tra quyền truy cập tại thời điểm tìm handle; quyền đó không tự nhiên được nâng lên chỉ vì ta dùng API động.

## 2. `MethodType` và `Lookup`: chuẩn bị đúng trước khi tìm

`MethodType.methodType(returnType, parameterTypes...)` mô tả kiểu phương thức **được tìm**. Ví dụ:

```java
MethodType.methodType(String.class, int.class)          // (int)String
MethodType.methodType(void.class, String.class)          // (String)void
MethodType.methodType(boolean.class)                     // ()boolean
```

- `MethodHandles.lookup()` cấp lookup gắn với **lớp gọi nó** và các quyền hiện có của lớp đó. Trong một lớp có quyền truy cập phù hợp, lookup có thể nhìn thấy cả thành viên không public.
- `MethodHandles.publicLookup()` chỉ dành cho những truy cập public mà lookup công khai cho phép; không thể dùng nó để tự do gọi thành viên private hoặc package-private.
- `MethodHandles.privateLookupIn(Target.class, lookup)` có thể tạo lookup mang quyền private của `Target` **khi** lookup đầu vào, module và quy tắc mở package cho phép. Đây không phải cách vượt mọi rào cản `private`/module.
- Signature của phương thức và kiểu receiver phải chính xác. `MethodHandle.type()` giúp xem kiểu handle **sau** khi tìm, rất hữu ích khi chẩn đoán `WrongMethodTypeException`.

Tên phương thức có phân biệt chữ hoa/chữ thường; overload được phân biệt bằng danh sách tham số và kiểu trả về thích hợp. Với constructor, không có tên phương thức vì API chỉ nhận `refc` và `type`.

## 3. `findVirtual` — tìm phương thức instance

Chữ ký:

```java
MethodHandle handle = lookup.findVirtual(
    SomeClass.class,
    "methodName",
    MethodType.methodType(ReturnType.class, ParamType.class)
);
```

Cho phương thức Java `String greet(String prefix)` của `Person`:

- Kiểu **tìm** là `(String)String`.
- Kiểu **handle** là `(Person,String)String`, vì `Person` là receiver.
- Khi gọi, truyền instance của `Person` trước chuỗi `prefix`.

Đối với phương thức instance có thể override, dispatch vẫn dựa trên đối tượng nhận lời gọi: handle tìm qua lớp cha có thể thực thi override của lớp con. Đừng hiểu “virtual” là lời hứa override cho mọi phương thức: `final` không thể override; private có các quy tắc gọi riêng.

`findVirtual` không được dùng để tìm constructor hay phương thức `static`. Nhầm loại thành viên sẽ khiến tra cứu thất bại, thay vì tự chuyển sang `findStatic`.

## 4. `findStatic` — tìm phương thức cấp lớp

Chữ ký:

```java
MethodHandle handle = lookup.findStatic(
    Utility.class,
    "parse",
    MethodType.methodType(int.class, String.class)
);
```

Một hàm `static int parse(String text)` có kiểu tìm và kiểu handle đều là `(String)int`. **Không truyền đối tượng `Utility`** khi gọi handle. Tên lớp trong `refc` phục vụ tra cứu, không biến thành tham số runtime.

Đừng lẫn `static` với việc gọi một phương thức instance qua biến tham chiếu lớp: chỉ phương thức thực sự khai báo `static` mới phù hợp với `findStatic`. Các kiểm tra quyền truy cập cũng áp dụng như đối với `findVirtual`.

## 5. `findConstructor` — tạo đối tượng có kiểm soát kiểu

Chữ ký:

```java
MethodHandle ctor = lookup.findConstructor(
    Person.class,
    MethodType.methodType(void.class, String.class)
);
```

Giả sử `Person(String name)` là constructor. Phần `MethodType` khi **tra cứu** có kiểu `(String)void`, vì constructor trong bytecode không khai báo kiểu trả về. Nhưng `ctor.type()` có kiểu `(String)Person` và khi gọi sẽ trả về instance `Person` mới.

Điểm cần nhớ:

- Không viết `MethodType.methodType(Person.class, String.class)` vào `findConstructor`: tham số `type` bắt buộc trả về `void`.
- Không có đối tượng receiver khi tạo object; constructor handle trả về instance mới.
- Nếu lớp không thể khởi tạo hoặc constructor không tồn tại/không truy cập được theo điều kiện tương ứng, quá trình tra cứu hoặc lời gọi sẽ thất bại. Ví dụ một lớp trừu tượng không thể được khởi tạo bằng cách dùng handle này.

## 6. `findSpecial` — gọi phiên bản “đặc biệt” theo quyền của lớp gọi

Chữ ký:

```java
MethodHandle handle = lookup.findSpecial(
    Parent.class,
    "methodName",
    MethodType.methodType(String.class),
    Child.class
);
```

`findSpecial` phản ánh cách JVM thực hiện một số lời gọi `invokespecial`, ví dụ lời gọi `super.method()` hợp lệ trong ngữ cảnh lớp con. Nó **không** chỉ là biến thể có thêm một tham số của `findVirtual`.

Giả sử `Child extends Parent` và override `label()`:

- Gọi thông thường `child.label()` chạy `Child.label()`.
- Nếu mã trong ngữ cảnh `Child` có quyền tra cứu đặc biệt với `Parent.class` và `specialCaller = Child.class`, handle phù hợp có thể chạy `Parent.label()` trên chính `Child` đó mà không dispatch sang override.
- Đối số đầu tiên khi gọi handle này là receiver kiểu `Child`; `specialCaller` ảnh hưởng đến **ngữ nghĩa và kiểm soát truy cập** chứ không phải tham số tùy chọn khi invoke.
- Tạo lookup từ lớp không có special-caller access phù hợp thường dẫn đến `IllegalAccessException`; không thể tùy tiện chọn một lớp bất kỳ làm `specialCaller`.

`findSpecial` cũng xuất hiện trong các tình huống gọi phương thức private/default interface theo ngữ nghĩa đặc biệt, nhưng chi tiết quyền truy cập phụ thuộc lớp, interface và module. Với mục tiêu gọi instance thông thường, dùng `findVirtual`; chỉ dùng `findSpecial` khi cần và được phép giữ rõ ngữ nghĩa gọi từ lớp cụ thể.

## 7. Ví dụ đầy đủ chạy được trên Java 17+

Lưu đoạn sau vào file `LookupDemo.java` theo UTF-8. Chương trình dùng cả bốn API, bao gồm một lời gọi `super` thực sự được giải quyết bằng `findSpecial` ngay trong lớp `Child`.

```java
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class LookupDemo {
    static class Person {
        private final String name;

        Person(String name) {
            this.name = name;
        }

        String greet(String prefix) {
            return prefix + ", " + name;
        }

        static String badge(int id) {
            return "ID-" + id;
        }
    }

    static class Base {
        String label() {
            return "Base";
        }
    }

    static class Child extends Base {
        @Override
        String label() {
            return "Child";
        }

        String callBase() throws Throwable {
            // Lookup được tạo bên trong Child: đúng ngữ cảnh special caller.
            MethodHandle parent = MethodHandles.lookup().findSpecial(
                Base.class,
                "label",
                MethodType.methodType(String.class),
                Child.class
            );
            return (String) parent.invokeExact((Child) this);
        }
    }

    public static void main(String[] args) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        // Constructor: tìm (String)void, nhận handle (String)Person.
        MethodHandle constructor = lookup.findConstructor(
            Person.class,
            MethodType.methodType(void.class, String.class)
        );

        // Virtual: tìm (String)String, nhận (Person,String)String.
        MethodHandle virtual = lookup.findVirtual(
            Person.class,
            "greet",
            MethodType.methodType(String.class, String.class)
        );

        // Static: tìm và nhận (int)String.
        MethodHandle fixed = lookup.findStatic(
            Person.class,
            "badge",
            MethodType.methodType(String.class, int.class)
        );

        Person person = (Person) constructor.invokeExact("Lan");
        System.out.println((String) virtual.invokeExact(person, "Chào"));
        System.out.println((String) fixed.invokeExact(7));

        Child child = new Child();
        System.out.println(child.label());
        System.out.println(child.callBase());
    }
}
```

Biên dịch và chạy trong thư mục có file nguồn:

```bash
javac -encoding UTF-8 LookupDemo.java
java LookupDemo
```

Đầu ra:

```text
Chào, Lan
ID-7
Child
Base
```

Giải thích từng bước:

1. `constructor.invokeExact("Lan")` tạo `Person`, yêu cầu Java compiler thấy kiểu trả về chính xác là `Person`.
2. `virtual.invokeExact(person, "Chào")` truyền receiver trước tham số. Kết quả là lời chào dùng tên của đối tượng.
3. `fixed.invokeExact(7)` chỉ nhận `int` vì `badge` là phương thức static.
4. `child.label()` theo dispatch thông thường cho ra `Child`, nhưng `child.callBase()` tạo handle trong đúng ngữ cảnh `Child` để gọi `Base.label()` và cho ra `Base`.

Ví dụ đặt `Person`, `Base` và `Child` trong cùng lớp bao nên quyền truy cập dễ kiểm soát. Nếu chuyển chúng sang package/module khác, cần xét lại modifier của lớp/phương thức và quyền của `Lookup`, không giả định mọi lookup vẫn hợp lệ.

## 8. `invokeExact`, `invoke` và thay đổi kiểu

Tra cứu thành công mới là bước một; lời gọi handle cần thỏa kiểu.

- `invokeExact` yêu cầu kiểu tham số **và kiểu trả về nhìn từ vị trí gọi** khớp chính xác với `handle.type()`. Với lời gọi có kết quả, ép kiểu tường minh `(String)` hoặc `(Person)` như ví dụ giúp compiler xác lập expected return type. Nếu chữ ký không trùng sẽ có `WrongMethodTypeException` khi thực thi.
- `invoke` cho phép một số phép thích nghi kiểu tương đương `asType` (ví dụ boxing/unboxing hoặc ép kiểu tham chiếu hợp lệ); không có nghĩa là mọi kiểu đều chuyển đổi được.
- `handle.asType(targetType)` tạo handle thích nghi theo quy tắc chuyển đổi cho phép; nó có thể báo `WrongMethodTypeException` nếu không thể thích nghi.
- `invokeWithArguments` hữu ích với danh sách đối số động và có thể thực hiện boxing/chuyển đổi; bù lại, nó không thay thế việc thiết kế chữ ký chính xác khi hiệu năng và an toàn kiểu là ưu tiên.

Ví dụ xử lý lời gọi `virtual` trong chương trình trên:

```java
// virtual.type() là (LookupDemo.Person,String)String
String result = (String) virtual.invokeExact(person, "Chào");

// Với invokeExact, không thể tùy ý bỏ receiver:
// virtual.invokeExact("Chào"); // Sai chữ ký ở vị trí gọi.
```

Đoạn này **chỉ minh họa khi được đặt trong `main` ở ví dụ đầy đủ**, nơi các biến `virtual` và `person` đã tồn tại; không phải file Java độc lập.

## 9. Lỗi thường gặp, nguyên nhân và cách xử lý

| Hiện tượng | Nguyên nhân tiêu biểu | Cách kiểm tra/sửa |
| --- | --- | --- |
| `NoSuchMethodException` lúc `find*` | Sai tên, overload, kiểu tham số/kết quả; nhầm static với instance | Đối chiếu khai báo thực tế và `MethodType`; chọn đúng API |
| `IllegalAccessException` lúc tra cứu | Lookup không có quyền của lớp/thành viên hoặc không có special-caller access | Xét vị trí tạo `lookup()`, modifier, package và module; chỉ dùng `privateLookupIn` khi có quyền |
| `WrongMethodTypeException` khi gọi | Chữ ký ở `invokeExact` khác `handle.type()`, kể cả kiểu kết quả | In `handle.type()`; kiểm tra receiver, `int`/`Integer`, kiểu kết quả và cast |
| `ClassCastException`/lỗi chuyển đổi lúc invoke | Đối số runtime không khớp phép chuyển đổi được yêu cầu | Kiểm tra kiểu runtime, `asType` và đường chuyển đổi thực sự |
| Gọi override thay vì lớp cha | Dùng `findVirtual` nhưng mong hành vi `super` | Dùng `findSpecial` trong special-caller context hợp lệ |
| Constructor lookup thất bại | Đặt return type là lớp thay vì `void`, hoặc nhầm danh sách tham số | Tra cứu constructor bằng `MethodType.methodType(void.class, ...)` |
| Lời gọi thiếu/thừa đối số | Quên receiver của virtual/special hoặc thêm receiver vào static/constructor | So sánh `handle.type()` với bảng ở mục 1 |

Các API tra cứu nêu trên khai báo `NoSuchMethodException` và `IllegalAccessException`. `MethodHandle.invokeExact`/`invoke` có thể ném `Throwable`, nên ví dụ sử dụng `throws Throwable` để dễ quan sát; trong ứng dụng thật hãy chọn cách xử lý ngoại lệ phù hợp.

## 10. Truy cập, kế thừa và module Java

`Lookup` là **capability có kiểm tra quyền**. Một reference đến `Class<?>` hoặc tên phương thức không tự cấp quyền. Vài nguyên tắc thực tế:

1. Tạo `MethodHandles.lookup()` trong đúng lớp/ngữ cảnh cần tra cứu. Di chuyển đoạn code sang helper khác sẽ đổi lớp sở hữu lookup, có thể đổi khả năng truy cập thành viên.
2. Phương thức public của public class chưa chắc đủ khi lớp nằm trong package không được module export cho bên gọi. Truy cập private qua module khác còn liên quan đến package `opens` và các điều kiện của `privateLookupIn`.
3. Với phương thức override, `findVirtual` thường là lựa chọn phản ánh polymorphism. Với `super` có chủ đích, `findSpecial` phải được kiểm tra bằng một case cụ thể trong hệ phân cấp lớp đang dùng.
4. Không dùng `setAccessible(true)` của Reflection như giả định rằng lookup của MethodHandle tự động có cùng quyền. Đây là hai cơ chế truy cập khác nhau.
5. Khi tra cứu thất bại, nên chẩn đoán đúng modifier, module và chữ ký trước khi thay đổi cấu hình JVM; không mở quyền module rộng chỉ để tránh hiểu nguyên nhân.

## 11. Chọn API qua các tình huống

- Gọi `user.getName()` trên nhiều instance: tra `findVirtual(User.class, "getName", methodType(String.class))`; truyền từng `User` làm đối số đầu của handle.
- Gọi hàm tiện ích `Math.max(int,int)`: tra `findStatic(Math.class, "max", methodType(int.class, int.class, int.class))`; truyền trực tiếp hai số nguyên.
- Tạo `new ArrayList<>(initialCapacity)`: tra `findConstructor(ArrayList.class, methodType(void.class, int.class))`; kết quả của handle là một `ArrayList`.
- Gọi phiên bản ở lớp cha dù lớp con có override: cân nhắc `findSpecial` **bên trong đúng ngữ cảnh lớp con được phép**; không dùng nó như lối tắt gọi thành viên tùy ý từ code ngoài lớp.

Một trình tự giải bài thực hành hiệu quả: **xác định loại thành viên → viết chữ ký khai báo → chọn `find*` → in `handle.type()` → kiểm tra quyền lookup → gọi đúng receiver và kiểu kết quả → đối chiếu output**. Nếu người học nắm được sự khác nhau giữa kiểu tìm và kiểu handle, phần lớn lỗi thực tế sẽ trở nên dễ xác định hơn.

## 12. Tự kiểm tra

1. Vì sao `findVirtual` nhận `MethodType` không chứa receiver nhưng handle lại chứa receiver?
2. Tại sao `findConstructor` yêu cầu return type `void` trong lúc handle trả về object?
3. Khi một lớp con override `label()`, làm sao phân biệt lời gọi virtual với lời gọi special đến bản của lớp cha?
4. `WrongMethodTypeException` có thể xảy ra vì kiểu kết quả ở vị trí `invokeExact` khác với handle hay không?
5. Khi cùng một lời tra cứu chạy được trong lớp `Child` nhưng thất bại sau khi chuyển sang một helper ở module khác, cần kiểm tra những quyền nào?

**Kết quả cần đạt:** phân biệt được cả bốn hình thức tra cứu, tự viết đúng `MethodType`, đọc được `MethodHandle.type()`, thực hiện lời gọi và giải thích được các lỗi về chữ ký cũng như quyền truy cập.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-handles-var-handles-004">unreflect Method, Constructor hoặc Field</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển `Method`, `Constructor` và `Field` thành handle bằng `unreflect`

## 1. Tại sao cần `unreflect`?

Trong Java, `java.lang.reflect` giúp khám phá cấu trúc lớp tại runtime. `Method`, `Constructor<?>` và `Field` là các đối tượng **metadata** mô tả thành viên; bản thân việc tìm được metadata chưa gọi method, chưa tạo instance và chưa đọc/ghi field.

`java.lang.invoke` cung cấp `MethodHandle` để biểu diễn thao tác gọi có chữ ký xác định và `VarHandle` để truy cập biến qua các access mode. Khi framework đã có metadata reflection (ví dụ sau khi tìm bằng tên, annotation hoặc cấu hình), đối tượng `MethodHandles.Lookup` có thể chuyển metadata ấy thành handle:

| Metadata đầu vào | API của `MethodHandles.Lookup` | Handle nhận được |
| --- | --- | --- |
| `Method` | `unreflect(Method)` | `MethodHandle` gọi method |
| `Constructor<?>` | `unreflectConstructor(Constructor<?>)` | `MethodHandle` tạo instance |
| `Field` cần đọc | `unreflectGetter(Field)` | `MethodHandle` đọc giá trị |
| `Field` cần ghi | `unreflectSetter(Field)` | `MethodHandle` ghi giá trị nếu được phép |
| `Field` cần access mode | `unreflectVarHandle(Field)` | `VarHandle` (Java 9+) |

Không có overload chung `unreflect(Field)`: cần chọn getter, setter hoặc VarHandle theo mục đích. Từ “unreflect” có nghĩa **chuyển biểu diễn reflection thành handle có thể gọi**, không có nghĩa “bỏ kiểm soát quyền truy cập”.

Luồng sử dụng:

```text
Class<?> -> Method / Constructor<?> / Field
                 |
                 v
         MethodHandles.Lookup
                 |
        kiểm tra quyền truy cập
                 |
                 v
     MethodHandle hoặc VarHandle
                 |
                 v
       invokeExact / invoke / get / set ...
```

**Hai thời điểm cần phân biệt:** `lookup.unreflect(method)` tạo handle; `handle.invokeExact(...)` mới chạy method đích. Tương tự `unreflectConstructor` chưa tạo object cho đến lúc handle được gọi.

## 2. Chữ ký của handle và vai trò của receiver

`MethodHandle.type()` trả về `MethodType` mô tả kiểu trả về cùng các kiểu tham số thực tế khi gọi handle. Instance member cần đối tượng nhận lời gọi (**receiver**) ở vị trí tham số đầu tiên; static member không cần receiver. Constructor cũng không cần receiver vì chính nó tạo instance mới.

Giả sử có:

```java
public static class Person {
    public String name;

    public Person(String name) {
        this.name = name;
    }

    public String greet(String prefix) {
        return prefix + ", " + name;
    }

    public static String language() {
        return "Java";
    }
}
```

Đây là đoạn khai báo minh họa, sẽ có một chương trình độc lập hoàn chỉnh ở mục tiếp theo.

| Thành viên | Chữ ký handle sau khi `unreflect` |
| --- | --- |
| `Person.greet(String)` | `(Person, String)String` |
| `Person.language()` static | `()String` |
| `Person(String)` constructor | `(String)Person` |
| Getter của `Person.name` | `(Person)String` |
| Setter của `Person.name` | `(Person, String)void` |

Việc reflection tìm method dùng **chữ ký khai báo** như `getMethod("greet", String.class)`, không có tham số receiver. Ngược lại, khi gọi MethodHandle của method instance, `person` phải là đối số đầu tiên. Setter trả `void`, còn constructor handle trả về instance mới. Với `Field` static, getter không có tham số và setter chỉ nhận giá trị mới.

## 3. Chương trình hoàn chỉnh Java 17+: Method, Constructor, Field

Lưu tệp **`UnreflectDemo.java`**:

```java
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class UnreflectDemo {
    public static class Person {
        public String name;

        public Person(String name) {
            this.name = name;
        }

        public String greet(String prefix) {
            return prefix + ", " + name;
        }
    }

    public static void main(String[] args) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        Constructor<Person> constructor =
                Person.class.getConstructor(String.class);
        Method method = Person.class.getMethod("greet", String.class);
        Field field = Person.class.getField("name");

        MethodHandle create = lookup.unreflectConstructor(constructor);
        MethodHandle greet = lookup.unreflect(method);
        MethodHandle getter = lookup.unreflectGetter(field);
        MethodHandle setter = lookup.unreflectSetter(field);
        VarHandle variable = lookup.unreflectVarHandle(field);

        Person person = (Person) create.invokeExact("Lan");
        System.out.println((String) greet.invokeExact(person, "Xin chào"));

        setter.invokeExact(person, "Mai");
        System.out.println((String) getter.invokeExact(person));

        variable.set(person, "An");
        System.out.println((String) variable.get(person));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac UnreflectDemo.java
java UnreflectDemo
```

Stdout:

```text
Xin chào, Lan
Mai
An
```

Giải thích theo thứ tự:

1. `getConstructor(String.class)`, `getMethod("greet", String.class)` và `getField("name")` lấy metadata **public**. Các lệnh này không chạy constructor hay method.
2. `unreflectConstructor` tạo handle `(String)Person`. Lệnh `create.invokeExact("Lan")` mới tạo `Person` chứa tên `Lan`.
3. `unreflect(method)` tạo handle `(Person, String)String`. `person` là receiver, `"Xin chào"` là tham số khai báo của method.
4. `unreflectGetter(field)` tạo `(Person)String`. `unreflectSetter(field)` tạo `(Person, String)void`. Sau setter, tên của `person` đổi thành `Mai`.
5. `unreflectVarHandle(field)` cũng lấy handle cho field `name`. Gọi `variable.set(person, "An")` rồi `variable.get(person)` khiến dòng cuối in `An`.
6. Ví dụ dùng class, constructor, method và field `public` để hoạt động trên classpath mà không cần mở truy cập private.

`main` khai báo `throws Throwable` vì các lời gọi `MethodHandle.invokeExact` có thể lan truyền lỗi từ thao tác đích. Khi viết sản phẩm, nên xử lý những loại lỗi phù hợp thay vì bỏ qua tất cả.

## 4. `invokeExact`, `invoke` và `MethodType`

`MethodHandle.invokeExact` là phương thức **signature-polymorphic**. Kiểu tham số **và kiểu trả về được kỳ vọng tại vị trí gọi** phải tương thích *chính xác* với `handle.type()`. Trong chương trình trên:

```java
Person person = (Person) create.invokeExact("Lan");
String text = (String) greet.invokeExact(person, "Xin chào");
```

Đây là hai dòng minh họa đặt trong `main` của ví dụ đầy đủ.

Phép ép kiểu `(Person)` và `(String)` không chỉ mang tính trình bày: chúng tạo ngữ cảnh kiểu trả về phù hợp cho `invokeExact`. Nếu cố nhận kết quả constructor thành `Object` trong khi handle trả `Person`, hoặc truyền kiểu `Integer` cho tham số `String`, có thể xảy ra `WrongMethodTypeException`.

`invoke(...)` cho phép một số phép biến đổi kiểu có kiểm tra, theo cơ chế method handle; `asType(...)` dùng để tạo handle đã thích nghi chữ ký khi cần. `invokeWithArguments(...)` phục vụ lời gọi với danh sách đối số động, thuận tiện hơn nhưng thường có thêm chuyển đổi/boxing. Không hình thức nào cho phép biến một tham số không tương thích thành kiểu bất kỳ.

Trong đoạn minh họa khác, static method không cần receiver:

```java
Method m = Math.class.getMethod("abs", int.class);
MethodHandle abs = MethodHandles.lookup().unreflect(m);
int result = (int) abs.invokeExact(-7);  // 7
```

Đoạn này cần đặt trong method có xử lý các checked exception tương ứng và `throws Throwable` cho `invokeExact`. Chữ ký của `abs` là `(int)int`, không phải `(Math, int)int`.

Với method overload, trước khi chuyển đổi phải tìm `Method` mang **đúng các kiểu tham số được khai báo**; `getMethod("f", int.class)` không tự chuyển sang `f(Integer)`. `handle.type()` giúp kiểm tra cấu trúc lời gọi cuối cùng. Với method varargs, handle có thể mang cơ chế variable-arity; nếu cần chữ ký cố định, cân nhắc `asFixedArity()`.

## 5. `unreflect(Method)` khác `Method.invoke` thế nào?

Cả hai đều có thể gọi một method runtime, nhưng mô hình vận hành khác nhau:

| `Method.invoke(receiver, args...)` | `MethodHandle.invokeExact(args...)` |
| --- | --- |
| Gọi qua đối tượng metadata reflection | Gọi qua handle có `MethodType` cụ thể |
| Tham số động theo cơ chế reflection; giá trị trả về thường là `Object` | Receiver (nếu là instance member) nằm ở đầu danh sách tham số của handle |
| Exception do method đích ném được bọc trong `InvocationTargetException` | Exception do code đích ném thường truyền trực tiếp qua handle |
| Xử lý kết quả có thể cần ép kiểu và unboxing | `invokeExact` đòi chữ ký lời gọi chính xác |

Việc chuyển từ `Method` sang `MethodHandle` **không tự đảm bảo nhanh hơn**. Hiệu quả còn phụ thuộc JVM/JIT, khả năng inline, nơi gọi, khối lượng thao tác, warm-up và cách benchmark. Với các đường gọi lặp lại, tránh liên tục `getDeclaredMethod` và `unreflect` nếu có thể tìm một lần, giữ handle và tái sử dụng.

## 6. `unreflectConstructor(Constructor<?>)`: tạo instance từ constructor

Nếu đã tìm đúng constructor:

```java
Constructor<Person> constructor =
        Person.class.getConstructor(String.class);
MethodHandle create =
        MethodHandles.lookup().unreflectConstructor(constructor);
Person person = (Person) create.invokeExact("Lan");
```

Đây là đoạn dựa trên `Person` trong chương trình `UnreflectDemo.java`.

`getConstructor(...)` chỉ tìm constructor `public`. `getDeclaredConstructor(...)` tìm constructor khai báo trực tiếp, kể cả non-public; **tìm được không đồng nghĩa có quyền gọi**. `unreflectConstructor(...)` tạo handle với kiểu trả về là kiểu được khởi tạo, ở đây `Person`, dù constructor trong Java không khai báo kiểu trả về.

Constructor không được kế thừa. Abstract class và interface không thể tạo instance trực tiếp theo cách thông thường; constructor phù hợp có thể vắng mặt hoặc không truy cập được. Việc chuyển handle có thể thất bại vì access, còn thao tác khởi tạo có thể ném exception do constructor đích phát sinh.

Đừng nhầm `Constructor.newInstance(...)` của Reflection với `MethodHandle.invokeExact(...)`. Cả hai có thể tạo instance, nhưng cách truyền đối số/kiểu và cách truyền lỗi khác nhau. `Class.newInstance()` đã deprecated; nên chọn constructor cụ thể và cơ chế xử lý lỗi rõ ràng.

## 7. `unreflectGetter`, `unreflectSetter` và `unreflectVarHandle`

`Field` mô tả trường dữ liệu; API cần chọn theo thao tác:

```java
Field field = Person.class.getField("name");
MethodHandles.Lookup lookup = MethodHandles.lookup();

MethodHandle getter = lookup.unreflectGetter(field);
MethodHandle setter = lookup.unreflectSetter(field);
VarHandle variable = lookup.unreflectVarHandle(field);
```

Đây là snippet minh họa trên `Person` của chương trình `UnreflectDemo.java`; lời gọi `getField` có thể ném `NoSuchFieldException`, còn unreflect có thể ném `IllegalAccessException`.

**Instance vs static:** getter instance dùng receiver; setter instance dùng receiver và giá trị. Getter static không dùng receiver; setter static chỉ dùng giá trị. Kiểm tra `getter.type()` / `setter.type()` trước khi xây adapter gọi động.

**Final field:** Không suy ra có getter là có setter. Việc tạo setter cho final field có thể bị từ chối tùy quyền truy cập và điều kiện JVM. `VarHandle` đối với final field thường chỉ hỗ trợ mode đọc, và mode ghi có thể ném `UnsupportedOperationException`. Không dùng thủ thuật deep reflection để xây cơ chế cập nhật object bất biến.

**VarHandle và memory ordering:** `VarHandle` có `varType()`, `coordinateTypes()` và các access mode như `get`/`set`, `getVolatile`/`setVolatile` hay `compareAndSet` nếu hỗ trợ. Không coi mode thông thường mặc nhiên là volatile hoặc atomic. Có thể kiểm tra `isAccessModeSupported(...)`; một mode không hỗ trợ đối với kiểu field nhất định sẽ gây lỗi khi gọi.

`VarHandle` phù hợp với thao tác trên biến có nhu cầu rõ về access mode và concurrency. `unreflectGetter`/`unreflectSetter` phù hợp khi chỉ cần handle đọc/ghi với `MethodType` cụ thể.

## 8. Quyền truy cập của `Lookup`, private member và JPMS

`MethodHandles.lookup()` tạo `Lookup` mang quyền truy cập theo **lớp gọi**; `publicLookup()` hạn chế hơn. `unreflect*` vẫn kiểm tra quyền phù hợp với member và `Lookup`. Ví dụ `getDeclaredField("secret")` có thể trả `Field` của field private, nhưng `lookup.unreflectGetter(field)` có thể ném `IllegalAccessException` nếu không có quyền truy cập.

Khi thực sự cần deep access, `MethodHandles.privateLookupIn(Target.class, callerLookup)` có thể tạo `Lookup` với quyền private phù hợp nếu các điều kiện được đáp ứng. Trên Java 17+, JPMS còn yêu cầu module đích mở package thích hợp cho module gọi khi khác module: `exports` không thay thế `opens`. Nên cấp quyền tối thiểu cần thiết.

Ví dụ độc lập sau dùng cùng nest và cùng module để minh họa truy cập private hợp lệ. Tạo **`PrivateLookupDemo.java`**:

```java
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class PrivateLookupDemo {
    private static class Vault {
        private String secret;

        private Vault(String secret) {
            this.secret = secret;
        }
    }

    public static void main(String[] args) throws Throwable {
        MethodHandles.Lookup lookup =
                MethodHandles.privateLookupIn(
                        Vault.class, MethodHandles.lookup());

        Constructor<Vault> constructor =
                Vault.class.getDeclaredConstructor(String.class);
        Field field = Vault.class.getDeclaredField("secret");

        MethodHandle create = lookup.unreflectConstructor(constructor);
        MethodHandle read = lookup.unreflectGetter(field);

        Vault vault = (Vault) create.invokeExact("đã bảo vệ");
        System.out.println((String) read.invokeExact(vault));
    }
}
```

```bash
javac PrivateLookupDemo.java
java PrivateLookupDemo
```

Stdout:

```text
đã bảo vệ
```

`privateLookupIn` trong ví dụ không phải đường tắt vượt quyền: do `Vault` nằm trong cùng nest, caller có quyền cần thiết. Với class ở module khác, package phải mở hợp lệ; nếu thiếu quyền, lời gọi có thể bị từ chối.

Ví dụ khai báo module **minh họa**, không phải chương trình chạy độc lập:

```java
module com.example.domain {
    opens com.example.domain.model to com.example.framework;
}
```

Trong trường hợp này, module `com.example.domain` mở package nêu trên cho module `com.example.framework`, ngoài các điều kiện lookup/module khác. Tránh mở toàn bộ module chỉ để xử lý một field.

`Method`/`Field` còn có `trySetAccessible()` và `setAccessible(true)`. Accessibility override có thể ảnh hưởng cách `unreflect` kiểm tra member, nhưng vẫn tuân thủ JPMS và các ràng buộc đặc biệt như final field; không nên giả định rằng `setAccessible(true)` luôn thành công hoặc biến mọi private member thành public.

## 9. Lỗi và cách xử lý

| Giai đoạn | Lỗi thường gặp | Cách nhận biết |
| --- | --- | --- |
| Tìm `Method`/constructor | Tên hoặc signature sai | `NoSuchMethodException` |
| Tìm `Field` | Tên field sai | `NoSuchFieldException` |
| Chuyển metadata | Lookup thiếu quyền | `IllegalAccessException` |
| Gọi handle | Sai chữ ký mong đợi | `WrongMethodTypeException` |
| Gọi handle | Receiver/ép kiểu không tương thích | Có thể có `ClassCastException` hoặc lỗi runtime tương ứng |
| Gọi method/constructor đích | Code đích ném exception | Lỗi đích có thể được truyền trực tiếp |
| Dùng `VarHandle` | Access mode không hỗ trợ | `UnsupportedOperationException` |
| Deep reflection qua `setAccessible` | Bị module từ chối | Có thể gặp `InaccessibleObjectException` |

Không nên dùng `catch (Exception ignored)` quanh toàn bộ pipeline. `invokeExact` có thể truyền `Throwable` từ code đích. Khi xử lý, phân biệt lỗi **tìm thành viên**, lỗi **liên kết handle** và lỗi **thực thi**; ghi ngữ cảnh tên method, kiểu tham số, class loader và module khi chẩn đoán.

Ba nhầm lẫn phổ biến:

1. “Tìm được `Method` thì `unreflect` luôn thành công.” Sai, vì `Lookup` còn phải có quyền.
2. “`invokeExact` giống `Method.invoke`, truyền `Object[]` bất kỳ.” Sai, vì chữ ký phải khớp `MethodType`.
3. “`unreflectGetter(field)` sẽ đọc field ngay.” Sai, vì thao tác thật xảy ra khi gọi handle.

## 10. Chọn `unreflect` hay `findVirtual`/`findStatic`?

`unreflect` thích hợp khi đã có metadata `Method`, `Constructor` hoặc `Field`, ví dụ framework scan annotation hoặc chọn member động từ cấu hình. Nếu đã biết tên, kiểu và muốn handle trực tiếp, `Lookup` có những hàm như `findVirtual`, `findStatic`, `findConstructor`, `findGetter` và `findSetter`.

Ví dụ minh họa với class `Person` của `UnreflectDemo.java`:

```java
MethodHandles.Lookup lookup = MethodHandles.lookup();
MethodHandle direct = lookup.findVirtual(
        Person.class,
        "greet",
        java.lang.invoke.MethodType.methodType(
                String.class, String.class));
```

`direct` cũng biểu diễn handle dạng `(Person, String)String`. Hai cách tiếp cận vẫn chịu kiểm tra quyền. Khi dùng nhiều lần, có thể cache handle thích hợp; lưu ý cache giữ `Class<?>` lâu dài có thể giữ class loader của plugin. Kiểm chứng lợi ích hiệu năng bằng benchmark có warm-up và workload thật; không khẳng định mọi lời gọi method handle nhanh hơn reflection.

## 11. Quy trình thực hành và tự kiểm tra

1. Xác định member đúng tên/chữ ký, biết đó là instance hay static và dùng loader/module nào.
2. Chọn `Lookup` với quyền cần thiết; nếu gặp non-public thì kiểm tra `privateLookupIn` và `opens`.
3. Dùng đúng `unreflect*` theo `Method`/`Constructor`/`Field` và nhu cầu đọc/ghi.
4. Xem `MethodHandle.type()` hoặc `VarHandle.varType()` / `coordinateTypes()` để biết chính xác đối số.
5. Gọi handle khi cần thao tác; dùng `invokeExact` với kiểu cố định, `invoke` nếu phép thích nghi hợp lệ là có chủ đích.
6. Xử lý lỗi lookup khác lỗi code đích, cân nhắc tái sử dụng handle và đo hiệu năng trước khi tối ưu.

Tự kiểm tra:

- Vì sao `unreflect(Method)` không làm method chạy ngay?
- Với instance method nhận một `String`, vì sao handle cần thêm receiver?
- `unreflectConstructor` tạo handle hay tạo instance? Instance được tạo vào lúc nào?
- Muốn đọc/ghi một `Field` thì dùng API nào? Khác nhau thế nào với `VarHandle`?
- Vì sao `getDeclaredField` có thể thành công nhưng `unreflectGetter` thất bại?
- `invokeExact` khác `invoke` ở đâu và vì sao các ví dụ ép kiểu kết quả?
- `exports` trong JPMS đã đủ cho deep reflection vào private field chưa?

**Ghi nhớ:** Reflection cung cấp metadata của thành viên; `MethodHandles.Lookup.unreflect*` chuyển metadata ấy thành handle với quyền truy cập tương ứng. Handle chỉ thực hiện thao tác khi được gọi, theo đúng chữ ký, receiver và access mode.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-handles-var-handles-005">MethodType và kiểm tra chữ ký</a>

<details>
<summary>Xem chi tiết</summary>

# `MethodType` và kiểm tra chữ ký trong Java

## 1. `MethodType` mô tả điều gì?

Trong Java, `java.lang.invoke.MethodType` là một **mô tả bất biến của chữ ký kiểu phương thức**: đúng một kiểu trả về và danh sách các kiểu tham số có thứ tự. Nó thuộc nhóm API `java.lang.invoke`, được sử dụng khi tra cứu và gọi `MethodHandle`, khi thích nghi lời gọi động, và khi tra chữ ký truy cập của `VarHandle`.

Ví dụ, chữ ký `String label(String name, int count)` được biểu diễn bởi:

```java
import java.lang.invoke.MethodType;

MethodType signature = MethodType.methodType(
        String.class, String.class, int.class);
System.out.println(signature); // (String,int)String
```

Trong lời gọi `methodType`, **đối số đầu tiên là kiểu trả về**; hai đối số kế tiếp lần lượt là kiểu của `name` và `count`. Chữ ký `(String,int)String` được đọc là "nhận `String` và `int`, trả `String`". Nó **không** bao gồm tên phương thức `label`.

`MethodType` không lưu lớp khai báo, tên phương thức, tên tham số, modifier (`public`/`static`), generic type arguments, annotation hay danh sách `throws`. Chẳng hạn, `List<String>` và `List<Integer>` đều có thể được biểu diễn bằng `List.class` vì type erasure; `MethodType` không thể thay thế một `ParameterizedType`.

So sánh các khái niệm:

| API | Dữ liệu chính | Công dụng |
| --- | --- | --- |
| `MethodType` | Kiểu trả về và kiểu tham số runtime | Mô tả, so khớp, tạo/chỉnh chữ ký kiểu |
| `MethodHandle` | Một thao tác có thể gọi, kèm `type()` | Thực thi lời gọi động |
| `java.lang.reflect.Method` | Phương thức có tên, lớp khai báo, modifiers và metadata | Tìm và gọi phương thức bằng Core Reflection |
| `VarHandle` | Truy cập đến biến/field/phần tử mảng | `GET`, `SET`, CAS và những access mode khác |

**Lưu ý:** `MethodType` chỉ mô tả kiểu, bản thân nó không chứa mã thực thi và không có thao tác gọi phương thức như `invokeExact`.

## 2. Cách tạo và đọc một `MethodType`

Một số overload của factory `MethodType.methodType(...)`:

```java
MethodType none = MethodType.methodType(void.class);
MethodType unary = MethodType.methodType(int.class, String.class);
MethodType binary = MethodType.methodType(
        String.class, String.class, int.class);
MethodType fromArray = MethodType.methodType(
        String.class, new Class<?>[] { String.class, int.class });
```

Đây là các đoạn mã minh họa để đặt trong class/phương thức và import `java.lang.invoke.MethodType`; chương trình đầy đủ có ở phần sau.

Các hàm truy vấn thường dùng:

- `returnType()` trả `Class<?>` mô tả kiểu kết quả; `void.class` được phép khi không có kết quả.
- `parameterCount()` đếm tham số; `parameterType(i)` truy cập tham số thứ `i`, đánh số từ 0.
- `parameterArray()` và `parameterList()` liệt kê các kiểu tham số theo đúng thứ tự; việc thay đổi mảng kết quả không thay đổi `MethodType`.
- `toString()` hiển thị dạng dễ đọc, ví dụ `(String,int)String`.
- `toMethodDescriptorString()` chuyển sang JVM descriptor, ví dụ `(Ljava/lang/String;I)Ljava/lang/String;` (`I` biểu diễn primitive `int`).
- `MethodType.fromMethodDescriptorString(descriptor, loader)` đọc JVM descriptor với class loader được cung cấp để phân giải kiểu tham chiếu.

**Kiểu trả về `void` hợp lệ, kiểu tham số `void` không hợp lệ.** `MethodType.methodType(String.class, void.class)` ném `IllegalArgumentException`. Nếu kiểu trả về hoặc kiểu tham số bị truyền `null`, API ném `NullPointerException`. Đối với `parameterType(i)`, chỉ số ngoài khoảng sẽ phát sinh `IndexOutOfBoundsException`.

Kiểu primitive và wrapper là **hai kiểu khác nhau**: `int.class` khác `Integer.class`. Kiểu mảng là một kiểu riêng: `String[].class` khác `String.class`. `MethodType` xét đối tượng `Class` thực tế, kể cả class loader, không chỉ chuỗi tên lớp.

## 3. So sánh chính xác và biến đổi chữ ký

`MethodType` bất biến. Các phương thức điều chỉnh trả về một `MethodType` khác (hoặc có thể dùng lại đối tượng hiện tại nếu nội dung không đổi), không sửa đối tượng ban đầu:

| Phương thức | Tác dụng |
| --- | --- |
| `changeReturnType(Class<?>)` | Thay kiểu trả về |
| `changeParameterType(index, Class<?>)` | Thay kiểu một tham số |
| `insertParameterTypes(index, Class<?>...)` | Chèn các tham số vào vị trí xác định |
| `appendParameterTypes(Class<?>...)` | Nối thêm các tham số |
| `dropParameterTypes(start, end)` | Bỏ đoạn tham số từ `start` đến trước `end` |
| `wrap()` / `unwrap()` | Chuyển primitive sang wrapper hoặc chiều ngược lại trong mô tả |
| `equals(Object)` | Kiểm tra kiểu trả về và từng kiểu tham số trùng chính xác |

Ví dụ:

```java
MethodType oldType = MethodType.methodType(
        String.class, String.class, int.class);
MethodType newType = oldType.changeReturnType(Object.class)
        .insertParameterTypes(1, long.class);

// oldType: (String,int)String
// newType: (String,long,int)Object
```

Dùng `equals` để so sánh chữ ký theo nội dung; **không dùng `==` để thay cho phép kiểm tra tương đương**. Kiểu, vị trí và số tham số đều quan trọng. Nếu chỉ đổi `int` thành `Integer` hoặc đổi kiểu trả về, hai `MethodType` không bằng nhau.

Biến đổi `MethodType` **không tự động biến đổi hành vi hay kiểu của một `MethodHandle` đang có**. Để tạo handle có kiểu mới và bộ chuyển đổi phù hợp, dùng `handle.asType(newType)` khi chuyển đổi được cho phép.

## 4. Kiểu của `MethodHandle`, receiver và `invokeExact`

`MethodHandle.type()` trả về `MethodType` mà handle yêu cầu tại lời gọi. Với phương thức `static` được tra bằng `Lookup.findStatic`, kiểu handle thường trùng kiểu phương thức mô tả tại lookup.

Với `Lookup.findVirtual`, có hai mức chữ ký cần phân biệt:

```java
// Chữ ký khai báo của String.substring(int, int):
MethodType declared = MethodType.methodType(
        String.class, int.class, int.class);
MethodHandle handle = MethodHandles.lookup()
        .findVirtual(String.class, "substring", declared);

// handle.type(): (String,int,int)String
//              ^ receiver String được đưa vào đầu
```

Ví dụ trên cần import `MethodHandle`, `MethodHandles`, `MethodType` và đặt trong phương thức xử lý checked exceptions. `findVirtual` nhận kiểu theo khai báo, không có receiver. Nhưng khi **gọi handle**, receiver trở thành tham số đầu tiên. `bindTo(receiver)` có thể gắn sẵn receiver trên một handle thích hợp và giảm số tham số của handle kết quả.

`MethodHandle.invokeExact(...)` yêu cầu chữ ký tại **call site** khớp chính xác `handle.type()`: số lượng, thứ tự, kiểu tham số **và kiểu trả về**. Compiler xác định kiểu trả về của call site từ ngữ cảnh của biểu thức. Chẳng hạn:

```java
// handle.type() là (String,int,int)String:
String yes = (String) handle.invokeExact("abcdef", 1, 3);
// Object no = (Object) handle.invokeExact("abcdef", 1, 3);
// Dòng thứ hai có thể biên dịch nhưng ném WrongMethodTypeException.
```

Trường hợp sau yêu cầu **Object** tại call site, còn handle trả **String**. Dù `String` là subtype của `Object`, `invokeExact` không chấp nhận chuyển đổi đó. Khi lời gọi đứng riêng như statement và giá trị bị bỏ qua, call site có kiểu trả về `void`; điều này cũng có thể không khớp.

`MethodHandle.invoke(...)` linh hoạt hơn: nếu chữ ký khác, JVM có thể thực hiện chuyển đổi tương đương `asType` (chẳng hạn ép kiểu tham chiếu, boxing/unboxing trong phạm vi được hỗ trợ). `invokeExact` không làm bước thích nghi này. Khi không thích nghi được, có thể nhận `WrongMethodTypeException`; lỗi cast cụ thể có thể gây `ClassCastException`.

## 5. Ví dụ Java 17+ chạy độc lập: so chữ ký, kiểm tra lỗi và thích nghi

Lưu thành `MethodTypeSignatureDemo.java`:

```java
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;

public class MethodTypeSignatureDemo {
    public static String label(String name, int index) {
        return name + "-" + index;
    }

    static void requireType(MethodHandle handle, MethodType expected) {
        if (!handle.type().equals(expected)) {
            throw new IllegalArgumentException(
                    "Expected " + expected + " but found " + handle.type());
        }
    }

    public static void main(String[] args) throws Throwable {
        MethodType signature = MethodType.methodType(
                String.class, String.class, int.class);

        System.out.println("type=" + signature);
        System.out.println("descriptor=" +
                signature.toMethodDescriptorString());
        System.out.println("count=" + signature.parameterCount());
        System.out.println("param0=" +
                signature.parameterType(0).getSimpleName());
        System.out.println("return=" +
                signature.returnType().getSimpleName());

        MethodType changed = signature.changeReturnType(Object.class);
        MethodType inserted = signature.insertParameterTypes(1, long.class);
        System.out.println("changed=" + changed);
        System.out.println("inserted=" + inserted);
        System.out.println("original=" + signature);

        MethodType decoded = MethodType.fromMethodDescriptorString(
                signature.toMethodDescriptorString(),
                MethodTypeSignatureDemo.class.getClassLoader());
        System.out.println("roundtrip=" + decoded.equals(signature));

        MethodHandle handle = MethodHandles.lookup().findStatic(
                MethodTypeSignatureDemo.class, "label", signature);
        System.out.println("handle=" + handle.type());
        requireType(handle, signature);
        System.out.println("checked=true");

        String exact = (String) handle.invokeExact("id", 7);
        System.out.println("exact=" + exact);

        try {
            Object ignored = (Object) handle.invokeExact("id", 7);
            System.out.println("unexpected=" + ignored);
        } catch (WrongMethodTypeException e) {
            System.out.println("wrongReturn=WrongMethodTypeException");
        }

        try {
            requireType(handle, signature.changeReturnType(Object.class));
        } catch (IllegalArgumentException e) {
            System.out.println("precheck=rejected");
        }

        MethodType adaptedType = MethodType.methodType(
                Object.class, Object.class, Integer.class);
        MethodHandle adapted = handle.asType(adaptedType);
        Object boxed = (Object) adapted.invokeExact(
                (Object) "box", Integer.valueOf(5));
        System.out.println("adaptedType=" + adapted.type());
        System.out.println("adaptedResult=" + boxed);

        MethodHandle substring = MethodHandles.lookup().findVirtual(
                String.class, "substring",
                MethodType.methodType(String.class, int.class, int.class));
        System.out.println("virtualType=" + substring.type());
        String part = (String) substring.invokeExact("methodtype", 0, 6);
        System.out.println("substring=" + part);
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 -encoding UTF-8 MethodTypeSignatureDemo.java
java MethodTypeSignatureDemo
```

Stdout chính xác:

```text
type=(String,int)String
descriptor=(Ljava/lang/String;I)Ljava/lang/String;
count=2
param0=String
return=String
changed=(String,int)Object
inserted=(String,long,int)String
original=(String,int)String
roundtrip=true
handle=(String,int)String
checked=true
exact=id-7
wrongReturn=WrongMethodTypeException
precheck=rejected
adaptedType=(Object,Integer)Object
adaptedResult=box-5
virtualType=(String,int,int)String
substring=method
```

Giải thích các điểm then chốt:

1. `changeReturnType` và `insertParameterTypes` giữ nguyên `signature` ban đầu; `roundtrip=true` chứng minh descriptor giải mã trở về chữ ký tương đương.
2. `handle.type()` của `findStatic` khớp chữ ký `(String,int)String` đã dùng tra cứu. Hàm `requireType` kiểm tra trước khi dùng để phát hiện việc kết nối nhầm contract.
3. `String exact = (String) handle.invokeExact(...)` thành công. Cast kết quả `Object` làm thay đổi kiểu trả về của call site nên ném `WrongMethodTypeException`.
4. `asType` tạo adapter có kiểu `(Object,Integer)Object`, ép `Object` chứa chuỗi thành `String`, unbox `Integer` sang `int` rồi nâng kết quả `String` thành `Object`. Adapter không sửa handle ban đầu.
5. `findVirtual` yêu cầu receiver `String` đứng đầu. Hàm `substring("methodtype", 0, 6)` trả về `method`.

Hàm `requireType` là kiểm tra contract chủ động; JVM vẫn thực thi kiểm tra riêng khi gặp lời gọi `invokeExact`.

## 6. Liên hệ `VarHandle`: chữ ký phụ thuộc access mode

`VarHandle` không có một chữ ký chung cho mọi thao tác. `GET` đọc giá trị, `SET` ghi giá trị và nhận thêm đối số mới, `COMPARE_AND_SET` có hợp đồng riêng. Có thể tra:

- `vh.accessModeType(VarHandle.AccessMode.GET)` — kiểu call site của `GET`.
- `vh.accessModeType(VarHandle.AccessMode.SET)` — kiểu call site của `SET`.
- `vh.toMethodHandle(mode).type()` — kiểu của MethodHandle đại diện access mode.
- `vh.isAccessModeSupported(mode)` — xác định access mode có được loại VarHandle đó hỗ trợ hay không.

Với phần tử `int[]`, tọa độ gồm **mảng** và **chỉ số**. Một ví dụ độc lập lưu vào `VarHandleSignatureDemo.java`:

```java
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

public class VarHandleSignatureDemo {
    public static void main(String[] args) {
        VarHandle vh = MethodHandles.arrayElementVarHandle(int[].class);
        MethodType getType = vh.accessModeType(VarHandle.AccessMode.GET);
        MethodType setType = vh.accessModeType(VarHandle.AccessMode.SET);
        MethodHandle getHandle = vh.toMethodHandle(VarHandle.AccessMode.GET);

        System.out.println("get=" + getType);
        System.out.println("set=" + setType);
        System.out.println("methodHandle=" + getHandle.type());
        System.out.println("getMatches=" + getHandle.type().equals(getType));

        int[] values = { 10, 20 };
        int value = (int) vh.get(values, 1);
        vh.set(values, 1, 42);
        System.out.println("before=" + value);
        System.out.println("after=" + values[1]);
    }
}
```

```bash
javac --release 17 -encoding UTF-8 VarHandleSignatureDemo.java
java VarHandleSignatureDemo
```

Stdout:

```text
get=(int[],int)int
set=(int[],int,int)void
methodHandle=(int[],int)int
getMatches=true
before=20
after=42
```

`GET` có kiểu `(int[],int)int`; `SET` có kiểu `(int[],int,int)void` vì nhận thêm giá trị mới. Lời gọi access mode của `VarHandle` cũng có ngữ nghĩa signature polymorphic; kiểm tra `accessModeType` giúp tránh nhầm số và kiểu tọa độ, giá trị cần ghi và kiểu kết quả.

## 7. Cách kiểm tra chữ ký khi tích hợp code động

Trong hệ thống ánh xạ lời gọi runtime, plugin, adapter hoặc thư viện tạo MethodHandle, nên thực hiện:

1. Xây `MethodType expected` dựa trên hợp đồng của thao tác cần gọi. Ghi rõ **kiểu trả về**, không chỉ tham số.
2. Đọc kiểu thực tế bằng `handle.type()`. Với instance method, tính cả receiver, còn với VarHandle chọn `accessModeType` theo đúng mode.
3. Nếu yêu cầu trùng tuyệt đối, dùng `expected.equals(actual)` và đưa ra lỗi giải thích khi không khớp.
4. Nếu chủ định chấp nhận chuyển đổi, tạo adapter `handle.asType(expected)` và kiểm tra logic cast/unboxing; adapter có thể thất bại ngay hoặc lúc gọi tùy kiểu chuyển đổi.
5. Khi đọc descriptor từ bytecode hay cấu hình, sử dụng class loader phù hợp. Chữ ký đúng **không bảo đảm** target method tồn tại hoặc được truy cập: `Lookup.findStatic`/`findVirtual` còn thực hiện kiểm tra quyền truy cập và có thể ném `NoSuchMethodException` hoặc `IllegalAccessException`.

Với API mà kiểu đích biết tại compile time, ưu tiên lời gọi Java thông thường; `MethodType` hữu ích khi thực sự cần mô tả và xử lý chữ ký ở runtime.

## 8. Lỗi thường gặp

| Sai sót | Biểu hiện và cách tránh |
| --- | --- |
| Nhầm tham số đầu của `methodType` | Nó là **kiểu trả về**, còn các kiểu tham số theo sau |
| Cho `void.class` vào tham số | `IllegalArgumentException`; `void` chỉ phù hợp với kiểu trả về |
| Dùng `==` để so nội dung chữ ký | Dùng `equals` |
| Nhầm `int.class` và `Integer.class` | Chữ ký khác nhau; `invokeExact` không tự boxing |
| Bỏ receiver của `findVirtual` | `handle.type()` có thêm receiver ở vị trí đầu |
| Bỏ qua kiểu trả về tại `invokeExact` | Call site có thể sai dù mọi đối số nhìn đúng |
| Tưởng `asType` sửa nguyên handle | Nó trả về adapter có kiểu mới |
| Dùng `toString()` làm JVM descriptor | Dùng `toMethodDescriptorString()` |
| Tưởng `MethodType` lưu generic/throws/tên method | Chỉ mô tả kiểu runtime của tham số/trả về |
| Dùng chữ ký `get` cho `set` của `VarHandle` | Kiểm tra `accessModeType(mode)` cho từng mode |
| Trộn kiểu từ class loader khác nhau | Danh tính `Class` phụ thuộc loader; cần giải mã descriptor với loader đúng |

`invokeExact` và `invoke` khai báo `throws Throwable`; các ví dụ dùng `main throws Throwable` để code chạy độc lập. `WrongMethodTypeException` thường cho biết sai hợp đồng kiểu tại call site, trong khi `ClassCastException` có thể là lỗi chuyển giá trị khi dùng adapter.

## 9. Tự kiểm tra

**1.** `MethodType.methodType(void.class, String.class)` có hợp lệ không? — **Có**: nhận `String` và trả `void`. Ngược lại, `methodType(String.class, void.class)` không hợp lệ.

**2.** `findVirtual(String.class, "substring", methodType(String.class, int.class, int.class))` tạo `handle.type()` nào? — **`(String,int,int)String`**, vì có receiver `String`.

**3.** Handle có kiểu `(String,int)String`; ép kết quả `invokeExact` sang `Object` có phù hợp không? — **Không**: call site muốn trả `Object`, không trùng `String`.

**4.** Vì sao `VarHandle` cần `accessModeType` thay vì dùng một `MethodType` duy nhất? — Vì các mode có số tham số và kiểu trả về khác nhau.

**Ghi nhớ:** `MethodType` là hợp đồng kiểu bất biến. Khi kiểm tra lời gọi động, đối chiếu đầy đủ **return type + danh sách tham số theo thứ tự + receiver**; dùng `handle.type()` và `VarHandle.accessModeType(mode)` để xác nhận trước khi thực thi.

Tham khảo: [Javadoc `MethodType` Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodType.html), [Javadoc `MethodHandle` Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandle.html), [Javadoc `VarHandle` Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/VarHandle.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-handles-var-handles-006">invoke, invokeExact và khác biệt chuyển đổi đối số</a>

<details>
<summary>Xem chi tiết</summary>

# `MethodHandle.invoke`, `invokeExact` và khác biệt chuyển đổi đối số

## 1. Mục tiêu bài học

`java.lang.invoke.MethodHandle` là một tham chiếu có kiểu tới một thao tác có thể gọi, như phương thức, constructor hoặc truy cập field. Mỗi handle có **`MethodType` riêng**. Khi gọi handle, điều quan trọng là chữ ký tại điểm gọi (call site) có khớp chữ ký của handle không.

Bài học giúp bạn:

- Đọc `handle.type()` để biết chính xác kiểu đối số, số lượng tham số và kiểu trả về.
- Dùng đúng `invokeExact(...)` (khớp hoàn toàn) và `invoke(...)` (có thể chuyển đổi kiểu).
- Giải thích boxing/unboxing, widening kiểu số, cast tham chiếu và lỗi chuyển đổi.
- Nhận diện vì sao **kiểu trả về tại call site** cũng khiến `invokeExact` thất bại.
- Chủ động xây dựng adapter với `asType(...)` khi muốn đổi chữ ký.
- Phân biệt `MethodHandle` với access mode của `VarHandle`.

Các ví dụ dưới đây chạy trên Java 17+ mà không cần thư viện ngoài.

## 2. `MethodType` là hợp đồng của MethodHandle

Ví dụ có phương thức:

```java
public static long sum(long a, long b) {
    return a + b;
}
```

Tạo handle tới phương thức tĩnh đó bằng `MethodHandles.Lookup.findStatic` (đoạn dưới được đặt trong lớp `MethodHandleInvokeDemo` có phương thức `sum`):

```java
MethodHandle sum = MethodHandles.lookup().findStatic(
        MethodHandleInvokeDemo.class, "sum",
        MethodType.methodType(long.class, long.class, long.class));
System.out.println(sum.type()); // (long,long)long
```

`MethodType.methodType(returnType, parameterTypes...)` nhận kiểu **kết quả đầu tiên**, sau đó là từng kiểu tham số. `(long,long)long` nghĩa là nhận `long, long` và trả `long`. `MethodType` không lưu tên phương thức, chỉ mô tả kiểu cần thiết để gọi.

**Hai kiểu cần phân biệt:**

| Khái niệm | Ý nghĩa |
| --- | --- |
| **Target type** | Chữ ký thực tế của handle, xem qua `sum.type()` |
| **Call-site type** | Chữ ký phát sinh từ kiểu tĩnh của các biểu thức đối số và kiểu kết quả mà code tại điểm gọi mong đợi |

`invoke` và `invokeExact` là các phương thức **signature-polymorphic**: mỗi lời gọi được biên dịch với kiểu đối số và kiểu trả về thực tế tại đó. Dù khai báo API trông như `Object...`, lời gọi thông thường không tự đóng gói đối số vào `Object[]` theo cơ chế Java varargs. JVM kiểm tra chữ ký phát sinh từ bytecode.

## 3. `invokeExact` yêu cầu khớp chính xác, không chuyển đổi

Với handle `(long,long)long`, đây là lời gọi đúng:

```java
long answer = (long) sum.invokeExact(10L, 20L); // 30
```

Điểm gọi có kiểu `(long,long)long`, trùng hoàn toàn với `sum.type()`.

Ngược lại:

```java
long answer = (long) sum.invokeExact(10, 20L);
```

Lời gọi trên vẫn **biên dịch được** vì đây là API signature-polymorphic, nhưng khi chạy sẽ ném `WrongMethodTypeException`: số `10` có kiểu `int`; `invokeExact` không tự widen `int` thành `long`. Cách sửa: đổi thành `10L` hoặc `(long) 10`.

### Kiểu kết quả cũng phải khớp

```java
Object result = sum.invokeExact(10L, 20L);
```

Đoạn này cũng ném `WrongMethodTypeException`. Dù giá trị `long` có thể boxing thành `Long` ở nhiều ngữ cảnh Java khác, `invokeExact` không tự động boxing kết quả cho call site yêu cầu `Object`.

Ba trường hợp thường nhầm:

- `long x = (long) sum.invokeExact(10L, 20L);`: đúng; kiểu kết quả được yêu cầu là `long`.
- `Object x = sum.invokeExact(10L, 20L);`: sai; call site yêu cầu kiểu kết quả `Object`.
- `sum.invokeExact(10L, 20L);` viết thành câu lệnh độc lập: call site kỳ vọng `void`, nên cũng sai nếu handle trả `long`.

Muốn bỏ qua kết quả thì có thể gán vào một biến `long` phù hợp. `invokeExact` phải khớp **arity, kiểu mọi tham số và kiểu trả về**; không có ép kiểu ngầm theo nghĩa của `invoke`.

## 4. `invoke`: có thể thích nghi chữ ký bằng `asType`

Nếu call-site type trùng target type, `invoke` hoạt động như `invokeExact`. Khi khác, `invoke` thử thích nghi handle tương đương `handle.asType(callSiteType)` rồi thực thi. JVM có thể thực hiện tối ưu mà không tạo một adapter hữu hình.

```java
long answer = (long) sum.invoke(10, 20L); // 30
```

Call site có kiểu `(int,long)long`. `invoke` cho phép chuyển `int` sang `long` trước khi gọi `sum`.

Ví dụ khác:

```java
Object result = sum.invoke(10, 20);
System.out.println(result.getClass().getSimpleName()); // Long
```

Hai tham số `int` được widen thành `long`; kết quả primitive `long` được boxing thành `Long` để dùng như `Object`.

Các chuyển đổi điển hình có thể xảy ra khi `invoke` thích nghi kiểu gồm: primitive widening (ví dụ `int -> long`); boxing/unboxing giữa primitive và wrapper; cast giữa các reference type (có kiểm tra tại runtime); chuyển đổi kết quả tương thích. Các phép này tuân theo các quy tắc của `MethodHandle.asType`, **không phải phép chuyển đổi dữ liệu tùy ý**.

| Tình huống (target là kiểu ghi bên phải) | `invokeExact` | `invoke` |
| --- | --- | --- |
| `long` -> `long` | Được | Được |
| `int` -> `long` | `WrongMethodTypeException` | Có thể widen |
| `Integer` -> `int` | Sai kiểu tĩnh | Có thể unbox |
| `Object` chứa `String` -> `String` | Sai kiểu tĩnh | Có thể cast lúc chạy |
| `long` trả về -> `Object` tại call site | Sai kiểu trả về | Có thể boxing |
| Chuỗi `"123"` -> `long` | Sai kiểu | Không tự parse chuỗi |
| Thiếu hoặc thừa đối số | Sai | Sai cho handle có arity cố định |

**Lưu ý:** handle dạng *varargs collector* có thêm quy tắc thu gom đối số cuối. Với handle thông thường, `invoke` không tự thêm hoặc bỏ tham số; cần adapter phù hợp cho các thay đổi như vậy.

## 5. Chương trình Java 17+ hoàn chỉnh

Tạo tệp `MethodHandleInvokeDemo.java`:

```java
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;

public class MethodHandleInvokeDemo {
    public static long sum(long a, long b) {
        return a + b;
    }

    public static String decorate(String text) {
        return "[" + text + "]";
    }

    public static void main(String[] args) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodHandle sum = lookup.findStatic(
                MethodHandleInvokeDemo.class, "sum",
                MethodType.methodType(long.class, long.class, long.class));
        System.out.println(sum.type());

        long exact = (long) sum.invokeExact(10L, 20L);
        long flexible = (long) sum.invoke(10, 20L);
        Object boxed = sum.invoke(10, 20);
        System.out.println("exact=" + exact);
        System.out.println("invoke=" + flexible);
        System.out.println("boxed=" + boxed.getClass().getSimpleName());

        MethodHandle adapted = sum.asType(
                MethodType.methodType(long.class, int.class, long.class));
        long adaptedExact = (long) adapted.invokeExact(10, 20L);
        System.out.println("adaptedExact=" + adaptedExact);

        try {
            long ignored = (long) sum.invokeExact(10, 20L);
            System.out.println(ignored);
        } catch (WrongMethodTypeException e) {
            System.out.println("exact arguments mismatch");
        }

        try {
            Object ignored = sum.invokeExact(10L, 20L);
            System.out.println(ignored);
        } catch (WrongMethodTypeException e) {
            System.out.println("exact return mismatch");
        }

        MethodHandle decorate = lookup.findStatic(
                MethodHandleInvokeDemo.class, "decorate",
                MethodType.methodType(String.class, String.class));
        System.out.println((String) decorate.invoke((Object) "Java"));
        try {
            String ignored = (String) decorate.invoke((Object) Integer.valueOf(7));
            System.out.println(ignored);
        } catch (ClassCastException e) {
            System.out.println("bad runtime argument");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac MethodHandleInvokeDemo.java
java MethodHandleInvokeDemo
```

Kết quả chính xác đã kiểm chứng:

```text
(long,long)long
exact=30
invoke=30
boxed=Long
adaptedExact=30
exact arguments mismatch
exact return mismatch
[Java]
bad runtime argument
```

**Phân tích:** `sum.invokeExact(10L, 20L)` có chữ ký đúng. `sum.invoke(10, 20L)` widen `int` thành `long`. Khi gán kết quả `invoke` cho `Object`, `long` được boxing nên tên lớp là `Long`. Hai lời gọi exact cố ý sai lần lượt ở tham số `int` và ở kiểu kết quả `Object`, nên đều phát sinh `WrongMethodTypeException`. Cuối cùng, `decorate.invoke((Object) "Java")` chuyển `Object` thành `String` thành công vì đối tượng thật là chuỗi; giá trị thật là `Integer` gây `ClassCastException` khi cast.

Chương trình dùng `main(String[] args) throws Throwable` bởi `MethodHandle.invoke` và `invokeExact` có chữ ký `throws Throwable`; ngoại lệ do phương thức đích ném có thể đi xuyên qua handle. Trong sản phẩm thật, hãy có chính sách xử lý ngoại lệ rõ ràng.

## 6. Tạo adapter với `asType` để gọi exact

Nếu ứng dụng thường xuyên gọi theo chữ ký `(int,long)long` nhưng handle gốc mang kiểu `(long,long)long`, có thể tạo một handle mới:

```java
MethodHandle adapted = sum.asType(
        MethodType.methodType(long.class, int.class, long.class));

System.out.println(adapted.type()); // (int,long)long
long answer = (long) adapted.invokeExact(10, 20L); // 30
```

`adapted.type()` đã là `(int,long)long` nên `invokeExact` trên adapter không cần chuyển đổi nữa tại call site. Bên trong adapter, giá trị `int` được widen thành `long` để chuyển tới handle gốc. MethodHandle là đối tượng bất biến; `sum.type()` vẫn giữ nguyên.

Chọn cách gọi theo yêu cầu: dùng `invokeExact` khi biết chữ ký chính xác và muốn lỗi mismatch được phát hiện rõ; dùng `invoke` khi cần các phép chuyển đổi tương thích; dùng `asType` để biểu diễn adapter rõ ràng, tái sử dụng cùng chữ ký mới. `MethodHandles.explicitCastArguments` cho các trường hợp cố ý cần quy tắc cast rộng hơn `asType`, nhưng phải kiểm soát nguy cơ mất dữ liệu.

**Không đánh đồng với** `invokeWithArguments(...)`: API này chấp nhận `Object[]` hoặc `List<?>` và gọi theo dạng tổng quát, phù hợp lúc phải lấy danh sách đối số động. Ví dụ sau là mảnh code trong chương trình ở mục 5:

```java
Object result = sum.invokeWithArguments(10L, 20L);
System.out.println(result); // 30
```

`invokeWithArguments` có đặc điểm boxing/unboxing và xử lý danh sách đối số; không yêu cầu exact như `invokeExact`.

## 7. Kiểu tĩnh và kiểu runtime khác nhau

`invokeExact` căn cứ vào **kiểu tĩnh của biểu thức tại điểm gọi**, không dựa vào lớp thật mà một biến `Object` đang trỏ tới.

```java
Object value = Long.valueOf(10L);
```

Đối với handle `(long,long)long`:

- `(long) sum.invokeExact(value, 20L)`: sai vì call site có đối số đầu tiên kiểu `Object`.
- `(long) sum.invoke(value, 20L)`: có thể thành công nhờ runtime cast/unboxing từ `Object` chứa `Long` sang `long`.
- `long unboxed = ((Long) value).longValue();` rồi `(long) sum.invokeExact(unboxed, 20L)`: khớp exact.

Điều này cũng áp dụng cho biến khai báo bằng interface hoặc superclass; việc gán một instance cụ thể không làm thay đổi kiểu tĩnh của biến. Với generic, Java dùng kiểu sau **erasure** trong chữ ký lời gọi.

Khi nhận kết quả, kiểu mong đợi cũng quan trọng. Trong nhiều bài toán, một cast ngay trên biểu thức gọi `(long) handle.invokeExact(...)` không đơn thuần là cast giá trị *sau* lời gọi; nó giúp xác định **chính kiểu trả về của call site**.

## 8. Các ngoại lệ thường gặp

| Ngoại lệ | Tình huống | Cách xử lý |
| --- | --- | --- |
| `WrongMethodTypeException` với `invokeExact` | Chữ ký call site khác `handle.type()`, kể cả phần trả về | So sánh arity, kiểu tham số và return, thêm cast hoặc đổi literal |
| `WrongMethodTypeException` với `invoke` | Không thể tạo thích nghi `asType` hợp lệ hoặc arity không hợp lệ | Dùng kiểu tương thích hoặc adapter đúng mục đích |
| `ClassCastException` với `invoke` | Adapter cho phép cast tham chiếu, nhưng đối tượng thật không có kiểu phù hợp | Kiểm tra kiểu runtime trước khi gọi |
| `NullPointerException` khi chuyển kiểu | Unboxing `null` sang primitive | Chặn giá trị null hoặc chọn hợp đồng nhận reference |
| Ngoại lệ từ phương thức đích | Target thật ném ngoại lệ | Xử lý theo hợp đồng target |

Quy trình tìm lỗi: đọc `handle.type()`; liệt kê **kiểu tĩnh** mỗi đối số; xác định kiểu trả về tại call site (cast hay sử dụng như câu lệnh `void`); kiểm tra khả năng chuyển đổi `asType`; cuối cùng kiểm tra giá trị runtime và ngoại lệ target. Đừng “sửa” bằng cách cast mọi thứ thành `Object` khi gọi `invokeExact`, vì cách này thường làm chữ ký càng sai.

Một handle `findVirtual` còn đòi hỏi receiver phù hợp ở **đối số đầu tiên** (ví dụ gọi `String.length` qua handle có chữ ký `(String)int`). Đừng bỏ quên receiver khi đếm arity.

## 9. Liên hệ với `VarHandle`

`VarHandle` cùng thuộc gói `java.lang.invoke` nhưng cách dùng thông thường là qua các **access mode** như `get`, `set`, `compareAndSet`, thay vì trực tiếp gọi `MethodHandle.invoke` hay `MethodHandle.invokeExact`. Access mode cũng dùng signature-polymorphic invocation và có quy tắc kiểm tra kiểu.

Trong Java 17, có thể tạo VarHandle với **exact behavior**:

```java
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class VarHandleBehaviorDemo {
    private static int number;

    public static void main(String[] args) throws ReflectiveOperationException {
        VarHandle handle = MethodHandles.lookup().findStaticVarHandle(
                VarHandleBehaviorDemo.class, "number", int.class);
        handle.set(7);
        System.out.println((int) handle.get());

        VarHandle exactBehavior = handle.withInvokeExactBehavior();
        exactBehavior.set(9);
        System.out.println((int) exactBehavior.get());
    }
}
```

Lưu thành `VarHandleBehaviorDemo.java`, chạy:

```bash
javac VarHandleBehaviorDemo.java
java VarHandleBehaviorDemo
```

Kết quả đã kiểm chứng:

```text
7
9
```

`withInvokeExactBehavior()` đặt chế độ kiểm tra **chữ ký chính xác của access mode**, không tạo ra MethodHandle mới. Muốn lấy MethodHandle ứng với một access mode, tìm hiểu `VarHandle.toMethodHandle(VarHandle.AccessMode)`.

## 10. Câu hỏi tự kiểm tra

1. Với handle `(long,long)long`, `(long) h.invokeExact(1, 2L)` chạy không? **Không**, đối số `1` là `int`. Dùng `1L`.
2. `Object x = h.invoke(1, 2)` có thể chạy không? **Có** khi phép thích nghi hợp lệ: widen hai `int` thành `long` và boxing kết quả `long` thành `Long`.
3. Vì sao `Object x = h.invokeExact(1L, 2L)` sai? **Kiểu kết quả `Object` của call site khác `long` của handle**.
4. `asType` có sửa `h` ban đầu không? **Không**; trả handle mới với chữ ký mới.
5. Vì sao `invoke` có thể ném `ClassCastException` dù adapter hợp lệ? **Giá trị runtime** không thỏa cast tham chiếu.
6. Nếu các đối số chỉ có sẵn trong `List<?>` tại runtime, nên tìm hiểu API nào? `invokeWithArguments(List<?>)`.
7. Lệnh `h.invokeExact(...)` được dùng như statement trong khi target trả `long` có thể thất bại vì đâu? Vì kiểu kết quả tại call site trở thành `void`.

## 11. Ghi nhớ và tài liệu tham khảo

- `invokeExact`: khớp 100% kiểu tại call site với `handle.type()`, gồm tham số **và return**.
- `invoke`: thích nghi chữ ký thông qua quy tắc `asType` nếu hợp lệ; không tự parse mọi dữ liệu.
- `asType`: tạo adapter có kiểu mới, giúp dùng `invokeExact` với chữ ký được chủ động xác định.
- `WrongMethodTypeException` là dấu hiệu cần kiểm tra kiểu của **cả hai phía**, còn `ClassCastException` thường liên quan giá trị reference ở runtime.
- `VarHandle` dùng access modes, có `withInvokeExactBehavior()` để chọn yêu cầu exact khi truy cập biến.

Tài liệu JDK 17 chính thức:

- [MethodHandle](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandle.html)
- [MethodType](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodType.html)
- [MethodHandles](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandles.html)
- [VarHandle](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/VarHandle.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-handles-var-handles-007">privateLookupIn và điều kiện mở truy cập theo module</a>

<details>
<summary>Xem chi tiết</summary>

# `MethodHandles.privateLookupIn` và điều kiện mở truy cập theo module (Java 17+)

## 1. Vì sao cần `privateLookupIn`?

`MethodHandle` và `VarHandle` cung cấp cách truy cập phương thức, constructor và biến thông qua một **đối tượng năng lực truy cập** (`MethodHandles.Lookup`). Khi dùng `findVirtual`, `findStatic`, `findVarHandle`, v.v., Java kiểm tra quyền của `Lookup` ngay lúc tạo handle. Việc giữ được `Class<?>` hoặc biết tên thành viên private **không tự cấp quyền** để truy cập thành viên đó.

Phương thức:

~~~java
MethodHandles.Lookup targetLookup =
        MethodHandles.privateLookupIn(Target.class, callerLookup);
~~~

tạo một `Lookup` có **ngữ cảnh tra cứu là `Target`** và có thể truy cập thành viên `private` của `Target`, nhưng **chỉ khi `callerLookup` đã đủ quyền và ranh giới module cho phép truy cập sâu** (*deep reflection*). API này xuất hiện từ Java 9, phù hợp với hệ thống module JPMS. Nó không mở package, không vô hiệu hóa module system và cũng chưa tạo `MethodHandle` hay `VarHandle`; muốn sử dụng một thành viên, phải tìm thành viên bằng `targetLookup` ở bước kế tiếp.

Mục tiêu của bài:

- Hiểu sự khác nhau giữa `MethodHandles.lookup()`, `publicLookup()`, `Lookup.in(...)` và `privateLookupIn(...)`.
- Nắm chính xác yêu cầu `PRIVATE`, `MODULE`, quan hệ `reads` và chỉ thị `opens`.
- Phân biệt `exports` với `opens` và biết dùng `--add-opens` để thử nghiệm/chẩn đoán.
- Gọi được method private và field private bằng MethodHandle/VarHandle, xử lý lỗi truy cập đúng chỗ.
- Nhận ra việc `Lookup` đi từ module này sang module khác làm mất mode `MODULE` và không thể tùy ý “chuyển tiếp” quyền private.

## 2. `Lookup` là quyền truy cập có thể truyền đi

`MethodHandles.lookup()` là **caller-sensitive**: lời gọi nằm trong lớp nào thì thông thường trả về `Lookup` với `lookupClass()` là lớp đó, có quyền đầy đủ của lớp gọi (trong một lời gọi thông thường, gồm các mode `PRIVATE` và `MODULE`).

~~~java
MethodHandles.Lookup caller = MethodHandles.lookup();
System.out.println(caller.lookupClass().getName());
~~~

`MethodHandles.publicLookup()` chỉ cho phép nhóm truy cập public theo quy tắc của nó, **không có `PRIVATE`/`MODULE` để gọi `privateLookupIn`**. `caller.in(Other.class)` thay lớp làm ngữ cảnh tìm kiếm nhưng quyền có thể bị giảm, đặc biệt khi đổi class/package/module; nó không phải một cách tự nâng quyền private.

Các method quan trọng:

| API | Ý nghĩa |
| --- | --- |
| `MethodHandles.lookup()` | Tạo lookup gắn với **lớp thực sự đang gọi** |
| `MethodHandles.publicLookup()` | Lookup public, không đủ quyền để yêu cầu private lookup |
| `lookup.lookupClass()` | Lớp mà lookup hiện đại diện |
| `lookup.lookupModes()` | Bitmask quyền, gồm `PUBLIC`, `PRIVATE`, `PROTECTED`, `PACKAGE`, `MODULE` ... |
| `lookup.in(SomeClass.class)` | Đổi ngữ cảnh với quyền không tăng lên |
| `MethodHandles.privateLookupIn(Target.class, lookup)` | Yêu cầu một lookup mới có quyền private trên `Target`, sau kiểm tra điều kiện |
| `lookup.findVirtual`, `findStatic`, `findVarHandle` | Tạo handle trên thành viên theo quyền lookup đang có |

`Lookup` là một **capability**: nếu code được nhận một lookup đủ mạnh hoặc một handle tới method private thì code đó có thể thực thi khả năng đã được cấp. Hạn chế chia sẻ các đối tượng này cho plugin/thành phần không đáng tin; đừng coi việc giấu tên method là hàng rào bảo mật.

## 3. Các điều kiện của `privateLookupIn` theo Java 17

Xét `M1` là module chứa `callerLookup.lookupClass()` và `M2` là module chứa `Target.class`.

### 3.1. Điều kiện chung

1. `callerLookup` phải có **cả `Lookup.PRIVATE` và `Lookup.MODULE`**. Một `Lookup` đã bị giới hạn bằng `publicLookup()` hay `dropLookupMode(...)` có thể không đủ quyền. `privateLookupIn` không tự lấy lại mode đã mất.
2. `Target` phải là một **class/interface thực**, không phải primitive, `void` hoặc array class; dùng `int.class`, `void.class`, `String[].class` làm target sẽ gây `IllegalArgumentException`.
3. `targetClass` và `callerLookup` không được `null`; nếu không sẽ có `NullPointerException`.
4. Với các kiểm tra quyền không đạt, lời gọi có thể ném **`IllegalAccessException`**. Trên môi trường Java 17 còn bật Security Manager, chính sách tương ứng cũng có thể gây `SecurityException`.

### 3.2. Khi `M1 == M2`

Khi lookup có đủ `PRIVATE`/`MODULE` và target nằm **cùng module**, `privateLookupIn` cho lookup có full-privilege access đến target, kể cả khác package trong module đó. Không cần thêm `opens` chỉ để làm private lookup **nội bộ cùng module**.

Điều này không có nghĩa cú pháp Java thông thường trong một lớp được phép gọi method private của lớp khác. Quyền mở rộng này được cấp qua API có kiểm tra `Lookup` và phải được người sở hữu lookup quyết định sử dụng.

### 3.3. Khi `M1 != M2`

Phải thỏa **đồng thời**:

- **`M1 reads M2`:** module gọi có quan hệ đọc đến module đích, ví dụ `requires vault.core;` trong `module-info.java`, hoặc cấu hình tương ứng khi chạy.
- **`M2 opens package chứa Target cho M1`:** ví dụ `opens demo.vault to demo.client;` trong descriptor của M2, hoặc thêm quyền mở đúng package qua `--add-opens vault.core/demo.vault=demo.client`.

Ngoài ra caller lookup vẫn phải có `PRIVATE` và `MODULE` như điều kiện chung. Chỉ có quyền đọc module hoặc chỉ có `exports` **chưa đủ** để truy cập thành viên private.

| Tình huống | `privateLookupIn(Target, caller)` |
| --- | --- |
| Cùng module, caller lookup đủ mode | Có thể tạo private lookup |
| Khác module, có `reads` và package được `opens` cho caller | Có thể tạo private lookup |
| Khác module, package chỉ `exports` | Không được phép private lookup |
| Khác module, package `opens` nhưng module caller không đọc module đích | Không đủ điều kiện |
| `publicLookup()` dù package đã `opens` | Không đủ `PRIVATE`/`MODULE` |
| Target là `int.class` hay `String[].class` | `IllegalArgumentException` |

> **Nguyên tắc:** kiểm tra module được thực hiện theo **module của lookup class**, không phải theo tên module do ứng dụng muốn giả định hoặc chỉ theo vị trí của file gọi API. Chuyển một `Lookup` qua API không làm nó có thêm quyền.

## 4. `exports` khác `opens` như thế nào?

`module-info.java` có hai khái niệm bổ sung nhau:

~~~java
module vault.core {
    exports demo.vault;
    opens demo.internal to demo.client;
}
~~~

- `exports demo.vault;`: cho các module đọc được `vault.core` sử dụng **kiểu public và thành viên public** trong package đó theo cơ chế truy cập thông thường. Không cho phép mở private members.
- `opens demo.internal to demo.client;`: cho phép **deep reflection** vào package `demo.internal` từ module `demo.client` (vẫn cần các điều kiện lookup/module phù hợp). Không phải lời tuyên bố rằng toàn bộ kiểu package đó có thể được import trực tiếp khi biên dịch.
- `opens demo.internal;`: mở cho mọi module; phạm vi rộng hơn cần thiết.
- `open module vault.core { ... }`: mở tất cả package trong module; chỉ chọn khi thực sự phù hợp.
- Một package có thể được `exports` và `opens` đồng thời nếu cần cả sử dụng API public lẫn reflection private.

Nếu không sở hữu mã nguồn module đích, JVM hỗ trợ tùy chọn khi chạy:

~~~bash
--add-opens vault.core/demo.vault=demo.client
~~~

Phần trước dấu `=` là `module/package` cần mở; phần sau là **module được cấp quyền**, không phải tên package. Với code chạy từ **classpath**, module đích có thể mở cho `ALL-UNNAMED`:

~~~bash
--add-opens vault.core/demo.vault=ALL-UNNAMED
~~~

`--add-exports` và `--add-opens` là hai cơ chế khác nhau: thêm `--add-exports` không cho phép truy cập private. `--add-opens` cũng không tự cấp mode `PRIVATE`/`MODULE` cho một caller lookup yếu, và không tạo một quan hệ `reads` còn thiếu. Với module có tên, `--add-reads source.module=target.module` có thể bổ sung quan hệ đọc khi thực sự cần.

## 5. Ví dụ 1 — gọi method private, thao tác field private trong cùng module

Chương trình Java 17+ sau gồm hai **top-level class** trong cùng tệp, đặt trên classpath (unnamed module). Chúng **không phải nestmate**; do đó lookup trực tiếp của `PrivateLookupDemo` không được truy cập member private của `TokenBox`.

Tạo tệp `PrivateLookupDemo.java`:

~~~java
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

class TokenBox {
    private int count = 1;

    private String reveal(String prefix) {
        return prefix + "ok";
    }
}

public class PrivateLookupDemo {
    public static void main(String[] args) throws Throwable {
        MethodHandles.Lookup caller = MethodHandles.lookup();

        try {
            caller.findVirtual(
                    TokenBox.class, "reveal",
                    MethodType.methodType(String.class, String.class));
        } catch (IllegalAccessException ex) {
            System.out.println("Direct lookup denied");
        }

        MethodHandles.Lookup privileged =
                MethodHandles.privateLookupIn(TokenBox.class, caller);

        MethodHandle reveal = privileged.findVirtual(
                TokenBox.class, "reveal",
                MethodType.methodType(String.class, String.class));

        TokenBox box = new TokenBox();
        System.out.println((String) reveal.invokeExact(box, "ID:"));

        VarHandle count = privileged.findVarHandle(
                TokenBox.class, "count", int.class);
        int before = (int) count.get(box);
        count.set(box, 5);
        int after = (int) count.get(box);
        System.out.println("count: " + before + " -> " + after);
    }
}
~~~

Chạy:

~~~bash
javac PrivateLookupDemo.java
java PrivateLookupDemo
~~~

Kết quả mong đợi:

~~~text
Direct lookup denied
ID:ok
count: 1 -> 5
~~~

**Phân tích:** `caller` được tạo trong `PrivateLookupDemo` nên `findVirtual` trực tiếp không thể đọc `TokenBox.reveal` private. `privateLookupIn` đổi ngữ cảnh có quyền sang `TokenBox`. `findVirtual` tạo handle có chữ ký logic gồm receiver `TokenBox` rồi tới tham số `String`; `invokeExact(box, "ID:")` đòi hỏi kiểu Java ở điểm gọi khớp chính xác. `findVarHandle` tạo quyền truy cập tới trường `int count`; `get` và `set` trên instance cũng thực hiện theo kiểu được khai báo.

Nếu đặt `TokenBox` thành **nested class** cùng nest với lớp gọi, Java có thể cho phép truy cập private bằng nestmate access ngay cả khi không dùng `privateLookupIn`. Vì vậy, ví dụ cố ý dùng hai top-level class để chỉ ra khác biệt.

## 6. Ví dụ 2 — hai module có tên: bị từ chối rồi mở package

Ta sẽ tạo module `vault.core` chứa `demo.vault.Vault` và module `demo.client` chứa chương trình thử nghiệm. `vault.core` **xuất API public** để `demo.client` biên dịch, nhưng ban đầu **chưa mở** package cho deep reflection.

Cấu trúc thư mục:

~~~text
src/
├── vault.core/
│   ├── module-info.java
│   └── demo/vault/Vault.java
└── demo.client/
    ├── module-info.java
    └── demo/client/Main.java
~~~

Tệp `src/vault.core/module-info.java`:

~~~java
module vault.core {
    exports demo.vault;
}
~~~

Tệp `src/vault.core/demo/vault/Vault.java`:

~~~java
package demo.vault;

public final class Vault {
    public Vault() {}

    private String secret() {
        return "ACCESS_GRANTED";
    }
}
~~~

Tệp `src/demo.client/module-info.java`:

~~~java
module demo.client {
    requires vault.core;
}
~~~

Tệp `src/demo.client/demo/client/Main.java`:

~~~java
package demo.client;

import demo.vault.Vault;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class Main {
    public static void main(String[] args) throws Throwable {
        MethodHandles.Lookup caller = MethodHandles.lookup();

        System.out.println("reads: "
                + Main.class.getModule().canRead(Vault.class.getModule()));
        System.out.println("open: "
                + Vault.class.getModule().isOpen(
                        "demo.vault", Main.class.getModule()));

        try {
            MethodHandles.Lookup target =
                    MethodHandles.privateLookupIn(Vault.class, caller);

            MethodHandle secret = target.findVirtual(
                    Vault.class, "secret",
                    MethodType.methodType(String.class));

            Vault vault = new Vault();
            System.out.println((String) secret.invokeExact(vault));
        } catch (IllegalAccessException ex) {
            System.out.println("Private lookup denied");
        }
    }
}
~~~

Tại thư mục chứa `src`, biên dịch và chạy (Java 17+):

~~~bash
javac --module-source-path src -d out -m vault.core,demo.client
java --module-path out -m demo.client/demo.client.Main
~~~

Kết quả khi `vault.core` chỉ có `exports`:

~~~text
reads: true
open: false
Private lookup denied
~~~

Quan hệ đọc có do `requires vault.core;`. Nhưng `exports demo.vault;` **không đồng nghĩa** `opens demo.vault;`; `privateLookupIn` ném `IllegalAccessException` trước khi tìm method.

Hãy chạy lại với quyền mở package chỉ cho `demo.client`:

~~~bash
java --module-path out \
  --add-opens vault.core/demo.vault=demo.client \
  -m demo.client/demo.client.Main
~~~

Dấu `\` ở cuối dòng là cú pháp nối dòng thường dùng trong shell Unix. Trên Windows PowerShell, có thể viết cùng lệnh **trên một dòng**:

~~~powershell
java --module-path out --add-opens vault.core/demo.vault=demo.client -m demo.client/demo.client.Main
~~~

Kết quả:

~~~text
reads: true
open: true
ACCESS_GRANTED
~~~

Để cấu hình bằng mã nguồn module thay vì flag chạy, đổi `src/vault.core/module-info.java` thành:

~~~java
module vault.core {
    exports demo.vault;
    opens demo.vault to demo.client;
}
~~~

Sau đó **biên dịch lại cả hai module** và chạy lệnh thông thường, không cần `--add-opens`. Đích mở là **module client**, không phải package `demo.client`; tên trùng chỉ là lựa chọn của ví dụ.

> Ví dụ hai module này đòi hỏi tổ chức thư mục và biên dịch bằng JPMS. Đặt mọi class lên classpath rồi chạy sẽ chuyển chúng vào unnamed module, nơi quy tắc mở package khác với một named module được đóng kín. Vì vậy không dùng một demo classpath để “chứng minh” named module đã mở hay chưa.

## 7. Điều gì xảy ra với lookup khi “teleport” qua module?

`privateLookupIn(Target.class, caller)` được mô tả là **teleport lookup** đến target:

- Nếu hai lớp thuộc **cùng module**, kết quả có full-privilege access trên target và `previousLookupClass()` là `null`.
- Nếu chuyển từ module `M1` sang module `M2` khác, kết quả có `PRIVATE` nhưng **mất `MODULE`**; `previousLookupClass()` ghi nhận lớp lookup ban đầu trong `M1`. Java 17 còn quy định lookup kết quả không có `ORIGINAL`.
- Vì không còn `MODULE`, không thể lấy kết quả đó làm `caller` cho một `privateLookupIn` thứ hai sang class/module khác. Không thể xâu chuỗi việc cấp quyền sâu tới các module theo cách này.

Để kiểm tra trong `Main` của ví dụ 2, sau khi tạo thành công `target` có thể thêm:

~~~java
System.out.println("target lookup class: "
        + target.lookupClass().getName());
System.out.println("previous lookup class: "
        + target.previousLookupClass().getName());
System.out.println("has MODULE: "
        + ((target.lookupModes() & MethodHandles.Lookup.MODULE) != 0));
~~~

Đoạn trên là **fragment**, chỉ chạy khi `target` được tạo từ hai module khác nhau; kết quả của `previousLookupClass()` khi đó không phải `null`:

~~~text
target lookup class: demo.vault.Vault
previous lookup class: demo.client.Main
has MODULE: false
~~~

Không áp dụng đoạn này máy móc vào trường hợp cùng module vì `previousLookupClass()` lúc đó có thể `null`. Sau khi đã có `MethodHandle` hợp lệ, Java chủ yếu kiểm tra kiểu gọi và khả năng thực thi của handle; không thực hiện lại phép cấp quyền `privateLookupIn` ở mỗi lần `invoke`. Vì vậy handle tới member private là đối tượng cần quản lý cẩn thận.

## 8. So sánh với Reflection `setAccessible(true)`

Java Reflection truyền thống có thể dùng `getDeclaredMethod`, `getDeclaredField` và `setAccessible(true)`; Method Handles tạo trực tiếp một handle từ `Lookup`. Cả hai chịu ràng buộc **strong encapsulation** của module system, nhưng thời điểm và API kiểm tra khác nhau.

| Nội dung | Reflection truyền thống | Method Handles |
| --- | --- | --- |
| Tìm member | `Class.getDeclaredMethod` / `getDeclaredField` | `Lookup.findVirtual` / `findVarHandle` |
| Yêu cầu truy cập sâu | `setAccessible(true)` hoặc `trySetAccessible()` | `MethodHandles.privateLookupIn(...)` |
| Module đóng package | Có thể bị `InaccessibleObjectException` / không mở được accessible | `privateLookupIn` có thể ném `IllegalAccessException` |
| Thực thi | `Method.invoke`, `Field.get/set` | `MethodHandle.invokeExact/invoke`, `VarHandle.get/set` |
| Kiểu lỗi khi gọi | `InvocationTargetException` có thể bọc lỗi từ method | MethodHandle thường truyền trực tiếp throwable của method; cũng cần xử lý sai kiểu gọi |

Không nên diễn giải `privateLookupIn` như một “bypass” tương đương `setAccessible(true)`. Cả hai chỉ hoạt động trong phạm vi quyền được Java cho phép. Một thư viện cần tương thích nhiều phiên bản nên yêu cầu module sở hữu dữ liệu **mở có chọn lọc** đúng package, thay vì khuyến nghị `open module` hoặc `--add-opens` tất cả package theo mặc định.

## 9. Lỗi thường gặp và cách chẩn đoán

| Triệu chứng | Nguyên nhân khả dĩ | Kiểm tra / xử lý |
| --- | --- | --- |
| `IllegalAccessException` tại `privateLookupIn` | Caller thiếu `PRIVATE`/`MODULE`, không có `reads`, hoặc target package không `opens` | In `lookupModes()`, `canRead`, `isOpen`; kiểm tra descriptor/flag |
| `IllegalArgumentException` tại `privateLookupIn` | Target là primitive, void hoặc array | Dùng `Class` của class/interface thực |
| `IllegalAccessException` tại `findVirtual` sau khi có lookup | Thành viên không truy cập được bởi lookup hoặc chọn sai quyền/ngữ cảnh | Kiểm tra lookup thực sự dùng và declaring class |
| `NoSuchMethodException` tại `findVirtual` | Tên/`MethodType` không đúng, overload sai | Đối chiếu **kiểu trả về lẫn danh sách tham số** của MethodType |
| `NoSuchFieldException` tại `findVarHandle` | Tên field hoặc kiểu field không khớp | Đối chiếu kiểu field chính xác |
| `WrongMethodTypeException` lúc gọi handle | Sai kiểu ở điểm gọi `invokeExact` hay adapter type không phù hợp | So sánh `handle.type()` với kiểu receiver/argument/return |
| `ClassCastException` khi gọi handle | Đối tượng tại runtime không phù hợp khi có chuyển kiểu | Kiểm tra receiver và kiểu đối số |
| `InaccessibleObjectException` từ `setAccessible` | Package chưa mở cho deep reflection | Mở package đúng target hoặc chọn API public |
| Chạy được trên classpath nhưng lỗi trên module path | Khác biệt giữa unnamed module và named module | Chạy mẫu JPMS thật, kiểm tra `module-info.java` |

Checklist chẩn đoán ngắn (các dòng sau là **fragment** trong class đã import `MethodHandles` và biết `Target`):

~~~java
MethodHandles.Lookup caller = MethodHandles.lookup();
Module from = caller.lookupClass().getModule();
Module to = Target.class.getModule();
String pkg = Target.class.getPackageName();

System.out.println("PRIVATE: "
        + ((caller.lookupModes() & MethodHandles.Lookup.PRIVATE) != 0));
System.out.println("MODULE: "
        + ((caller.lookupModes() & MethodHandles.Lookup.MODULE) != 0));
System.out.println("reads: " + from.canRead(to));
System.out.println("opens: " + to.isOpen(pkg, from));
~~~

Ở đây `Target` là lớp đích có sẵn trong chương trình, không phải tên API giả định. Kiểm tra thuộc tính này giúp xác định thiếu quyền nào **trước khi** dùng thử một thao tác truy cập private.

## 10. Thiết kế an toàn với module và Method Handle

1. **Sở hữu quyền mở package tại module đích.** Ưu tiên `opens package to trusted.module;` nếu thư viện thực sự cần deep reflection. Đối với tương tác thông thường, cung cấp public API qua `exports` hoặc qua service interface.
2. **Tạo lookup tại vị trí đáng tin.** `MethodHandles.lookup()` phải được gọi trong mã của module caller có quyền phù hợp; một lookup nhận từ `publicLookup` hoặc plugin không đáng tin không thể tự nâng cấp.
3. **Chỉ yêu cầu member cần thiết.** Sau khi có private lookup, tạo đúng `MethodHandle`/`VarHandle` cần dùng, không lưu và phát tán lookup đầy đủ nếu chỉ cần thao tác hẹp.
4. **Tách kiểm tra quyền và lỗi nghiệp vụ.** `IllegalAccessException` khi tạo lookup khác với `WrongMethodTypeException` khi gọi handle và lỗi do chính method đích ném.
5. **Không dựa vào tên package để suy ra quyền.** Module identity, `reads` và `opens` là các điều kiện runtime. Một package cùng tên trong hai module cũng không đương nhiên được cùng quyền truy cập.
6. **Không mở rộng vô tội vạ package của JDK.** Nếu chẩn đoán lỗi `java.base`, cân nhắc API được hỗ trợ thay vì dùng `--add-opens java.base/...=ALL-UNNAMED` trong sản phẩm.

`Lookup` mạnh là một quyền có thể ủy quyền. Khi một module mở package cho module khác, module nhận quyền có thể tạo handle tới member private trong package được mở; quyết định này có tác động đến ranh giới đóng gói của ứng dụng.

## 11. Câu hỏi tự kiểm tra

1. `publicLookup()` có đủ quyền gọi `privateLookupIn` không? **Không**, vì thiếu `PRIVATE` và `MODULE`.
2. Hai class khác package nhưng cùng named module có bắt buộc `opens` không? **Không chỉ vì khác package**; `privateLookupIn` với caller full-privilege có thể tạo lookup private cùng module.
3. Module client đã `requires vault.core;` và target package được `exports`, vậy đã gọi member private được chưa? **Chưa**; package còn phải `opens` cho client nếu khác module.
4. `--add-opens vault.core/demo.vault=demo.client` trao quyền cho ai? **Module `demo.client`**, không phải mọi module; vẫn cần caller lookup đủ mode.
5. Điều gì mất khi `privateLookupIn` chuyển giữa hai module có tên? **Mode `MODULE`** trên lookup kết quả; `previousLookupClass()` ghi nhận lớp lookup trước đó.
6. Vì sao cùng mã nguồn chạy trên classpath đôi khi khác module path? **Unnamed module mở package**, còn named module có mô tả `exports`/`opens` và các quan hệ đọc được kiểm tra.
7. Một `MethodHandle` đã được tạo thành công có cần gọi lại `privateLookupIn` trước mỗi `invokeExact` không? **Không**; phải dùng handle đúng chữ ký và giữ an toàn capability đã được cấp.

## 12. Tài liệu chính thức

- [Java SE 17 — MethodHandles và privateLookupIn](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandles.html)
- [Java SE 17 — MethodHandles.Lookup](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandles.Lookup.html)
- [Java SE 17 — Module](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Module.html)
- [Oracle Java 17 Migration Guide — --add-opens](https://docs.oracle.com/en/java/javase/17/migrate/migrating-jdk-8-later-jdk-releases.html)

**Ghi nhớ:** `privateLookupIn` chỉ thành công khi caller lookup có **`PRIVATE` + `MODULE`**; nếu target ở module khác, cần thêm **`reads`** và **`opens`** đúng package/đúng module. `exports` chỉ giúp sử dụng API public, không mở private member. Sau khi được cấp quyền, `MethodHandle`/`VarHandle` là capability thực sự có thể thực thi và cần được quản lý cẩn thận.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-handles-var-handles-008">VarHandle cho field hoặc phần tử mảng</a>

<details>
<summary>Xem chi tiết</summary>

# VarHandle cho field hoặc phần tử mảng trong Java

## 1. VarHandle giải quyết vấn đề gì?

Khi làm việc với Java Reflection, ta có thể lấy và sửa giá trị của field thông qua `java.lang.reflect.Field`. Với code đồng thời (concurrency), ta còn cần chỉ định **cách đọc/ghi bộ nhớ** và đôi khi phải thực hiện các thao tác **nguyên tử** như tăng biến đếm hoặc đổi giá trị nếu nó vẫn bằng giá trị mong đợi. Từ Java 9, `java.lang.invoke.VarHandle` cung cấp một cách truy cập biến có kiểu rõ ràng và nhiều chế độ truy cập (access mode).

Một `VarHandle` đại diện cho **vị trí lưu biến**: một instance field, static field, phần tử mảng hoặc dạng view trên vùng nhớ được các factory tương ứng hỗ trợ. Bài này tập trung vào **field và phần tử mảng**. Khác với `MethodHandle` đại diện cho thao tác gọi, `VarHandle` đại diện cho **biến được truy cập** cùng tập thao tác hợp lệ trên biến đó.

Điểm cần phân biệt:

- **Biến nào?** Một field cụ thể hoặc một phần tử của một loại mảng.
- **Định vị biến ra sao?** Instance field cần đối tượng receiver; static field không cần receiver; phần tử mảng cần đối tượng mảng và chỉ số.
- **Thao tác nào?** Đọc/ghi thường (`get`/`set`), đọc/ghi với quy tắc memory ordering (`getVolatile`/`setVolatile`, `getAcquire`/`setRelease`, v.v.), hoặc thao tác nguyên tử như `compareAndSet` và `getAndAdd`.
- **Có quyền hay không?** Tra cứu field vẫn tuân thủ kiểm soát truy cập Java/module. Có handle không phải giấy phép sửa mọi field: access mode không được hỗ trợ vẫn có thể bị từ chối.

## 2. Mô hình variable type và coordinate types

Một `VarHandle` có hai thông tin đặc trưng:

1. `varType()`: kiểu dữ liệu của biến được truy cập, ví dụ `int.class`, `String.class`.
2. `coordinateTypes()`: danh sách kiểu để **xác định vị trí** của biến. Đây không phải danh sách tham số của phương thức Java thông thường.

| Vị trí biến | Cách lấy handle | `varType()` | `coordinateTypes()` | Ví dụ đọc |
| --- | --- | --- | --- | --- |
| Instance field `Counter.value: int` | `lookup.findVarHandle(Counter.class, "value", int.class)` | `int` | `[Counter]` | `(int) vh.get(counter)` |
| Static field `Config.level: int` | `lookup.findStaticVarHandle(Config.class, "level", int.class)` | `int` | `[]` | `(int) vh.get()` |
| Phần tử `int[]` | `MethodHandles.arrayElementVarHandle(int[].class)` | `int` | `[int[], int]` | `(int) vh.get(numbers, index)` |

Các lời gọi bên phải được minh họa dưới giả định `vh` là handle phù hợp với dòng tương ứng. Với `arrayElementVarHandle`, handle đại diện cho **cách truy cập phần tử của loại mảng**, chưa gắn với một mảng hoặc một vị trí cố định nào: truyền mảng và index vào mỗi lần gọi.

Cách xác định đối số cho một access mode:

```text
instance field:  get(receiver)                     set(receiver, value)
static field:    get()                             set(value)
array element:   get(array, index)                 set(array, index, value)

compareAndSet:   compareAndSet(coordinates..., expected, update)
getAndAdd:       getAndAdd(coordinates..., delta)
```

`coordinates...` là toàn bộ tọa độ phù hợp với loại handle. Với `get`, kết quả có kiểu `varType`; với `set`, lời gọi không có kết quả; với `compareAndSet`, kết quả kiểu `boolean`; với `getAndAdd`, kết quả là giá trị **trước** khi cộng.

## 3. Tạo VarHandle cho instance field

Dùng `MethodHandles.Lookup.findVarHandle`:

```java
MethodHandles.Lookup lookup = MethodHandles.lookup();
VarHandle valueHandle = lookup.findVarHandle(
    Counter.class,
    "value",
    int.class
);
```

Trong ví dụ này, `Counter` có field instance tên `value` kiểu `int`. Field **không** phải static. Khi tra cứu, loại `int.class` phải khớp kiểu khai báo của field; khi truy cập, cần receiver là một `Counter` hợp lệ.

Nếu field khai báo `private`, lookup phải có quyền private tương ứng. `MethodHandles.lookup()` được gắn với lớp nơi nó được gọi; việc di chuyển mã lookup sang lớp khác có thể làm thay đổi quyền. `MethodHandles.publicLookup()` không tự cung cấp quyền private. Các quy tắc package và Java module cũng áp dụng; `privateLookupIn` chỉ hoạt động khi thỏa điều kiện truy cập cần thiết.

Ví dụ thao tác trên cùng một handle:

```java
valueHandle.set(counter, 10);                            // Ghi plain.
int current = (int) valueHandle.get(counter);             // Đọc plain.
int old = (int) valueHandle.getAndAdd(counter, 5);        // Cộng nguyên tử, trả giá trị cũ.
boolean changed = (boolean) valueHandle.compareAndSet(
    counter, 15, 20                                      // Chỉ đổi 15 -> 20 nếu còn là 15.
);
```

Đây là **đoạn mã minh họa**, dùng biến `valueHandle` và `counter` đã tạo ở nơi khác; chương trình độc lập chạy được nằm ở mục 7. Lưu ý `get` và `set` là plain access, không nên dựa vào chúng thay cho quy tắc đồng bộ giữa các thread.

## 4. VarHandle cho static field

Với field `static`, phải dùng `findStaticVarHandle`:

```java
VarHandle sharedHandle = lookup.findStaticVarHandle(
    Config.class,
    "level",
    int.class
);

sharedHandle.setVolatile(7);
int level = (int) sharedHandle.getVolatile();
```

Dòng đầu giả định `Config` có static field `level` kiểu `int` và lookup có quyền truy cập; đoạn này minh họa trong ngữ cảnh có biến `lookup`. Khác với instance field, `sharedHandle.coordinateTypes()` rỗng, nên **không** truyền đối tượng `Config` vào `getVolatile` hoặc `setVolatile`.

Không tráo `findVarHandle` với `findStaticVarHandle`: hai API tra cứu hai loại field khác nhau. Tên field, lớp khai báo và kiểu field phải đúng. Đây là khác biệt về **định vị biến**, còn các access mode được hỗ trợ vẫn phụ thuộc loại field và kiểu giá trị.

## 5. VarHandle cho phần tử mảng

Dùng factory:

```java
VarHandle elementHandle = MethodHandles.arrayElementVarHandle(int[].class);
int[] scores = {10, 20, 30};

int first = (int) elementHandle.get(scores, 0);      // 10
elementHandle.set(scores, 1, 25);                    // scores[1] = 25
int old = (int) elementHandle.getAndAdd(scores, 2, 5); // old = 30; scores[2] = 35
```

Đoạn này là minh họa độc lập về cú pháp khi được đặt trong method có import tương ứng. Một handle được tạo từ `int[].class` có thể làm việc với nhiều đối tượng `int[]` khác nhau; mỗi lần gọi đều cần truyền **mảng** và **chỉ số**.

Các lưu ý:

- `arrayElementVarHandle` nhận **loại mảng**, ví dụ `int[].class` hoặc `String[].class`, không nhận một instance mảng làm tham số factory.
- Với mảng `int[]`, `varType()` là `int.class`; `coordinateTypes()` chứa kiểu mảng `int[].class` và `int.class` cho chỉ số.
- Một index ngoài giới hạn sẽ gây `ArrayIndexOutOfBoundsException`; receiver mảng `null` gây `NullPointerException`.
- Những phép toán như `getAndAdd` chỉ có ý nghĩa trên kiểu dữ liệu được access mode đó hỗ trợ. Không mặc định áp dụng phép cộng cho `String[]`.
- Nếu là mảng tham chiếu, quy tắc tương thích kiểu phần tử của Java vẫn có hiệu lực, kể cả kiểm tra lưu phần tử vào mảng thực tế.

## 6. Định vị biến khác với chọn access mode
Khi đã tạo `VarHandle` cho đúng vị trí, mỗi phép toán còn có **quy tắc truy cập** riêng. `get`/`set` có ngữ nghĩa plain, `getVolatile`/`setVolatile` có ngữ nghĩa volatile, `getAcquire`/`setRelease` dùng trong giao thức publish/consume, còn `compareAndSet` và `getAndAdd` là các phép cập nhật nguyên tử khi được hỗ trợ. Phải dùng `isAccessModeSupported(VarHandle.AccessMode.GET_AND_ADD)` nếu công cụ xử lý động không biết trước kiểu biến.

Hai phép `get` rồi `set` vẫn là hai thao tác rời, vì vậy không thay thế `getAndAdd` khi nhiều luồng cùng tăng biến. Tương tự, `get` hoặc `set` plain không đủ để tự tạo quan hệ happens-before giữa các luồng. Khi nghiên cứu đồng thời, phải xác định đúng access mode và giao thức chia sẻ dữ liệu; bài về memory ordering trong cùng chương phân tích các điều kiện này đầy đủ hơn.

## 7. Chương trình đầy đủ: instance field, static field và mảng

Lưu toàn bộ code dưới đây thành `VarHandleDemo.java`, sử dụng JDK 17+.

```java
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;

public class VarHandleDemo {
    private static class Counter {
        private volatile int value;
    }

    private static volatile int shared;

    public static void main(String[] args) throws ReflectiveOperationException {
        MethodHandles.Lookup lookup = MethodHandles.lookup();

        VarHandle field = lookup.findVarHandle(Counter.class, "value", int.class);
        VarHandle staticField = lookup.findStaticVarHandle(
            VarHandleDemo.class, "shared", int.class
        );
        VarHandle element = MethodHandles.arrayElementVarHandle(int[].class);

        Counter counter = new Counter();
        field.setVolatile(counter, 10);
        System.out.println("field initial = " + (int) field.getVolatile(counter));
        System.out.println("field old = " + (int) field.getAndAdd(counter, 5));
        System.out.println("field CAS = "
                + (boolean) field.compareAndSet(counter, 15, 21));
        System.out.println("field final = " + (int) field.getVolatile(counter));

        staticField.setVolatile(7);
        System.out.println("static = " + (int) staticField.getVolatile());

        int[] numbers = {3, 4, 5};
        element.set(numbers, 1, 40);
        System.out.println("array old = " + (int) element.getAndAdd(numbers, 1, 2));
        System.out.println("array CAS = "
                + (boolean) element.compareAndSet(numbers, 2, 5, 50));
        System.out.println("array = " + Arrays.toString(numbers));

        System.out.println("field coordinates = " + field.coordinateTypes().size());
        System.out.println("static coordinates = " + staticField.coordinateTypes().size());
        System.out.println("array coordinates = " + element.coordinateTypes().size());
        System.out.println("variable type = " + field.varType().getSimpleName());
    }
}
```

Biên dịch và chạy từ thư mục chứa file:

```bash
javac -encoding UTF-8 VarHandleDemo.java
java VarHandleDemo
```

**Đầu ra xác định** (đã được kiểm tra trên JDK 21):

```text
field initial = 10
field old = 10
field CAS = true
field final = 21
static = 7
array old = 40
array CAS = true
array = [3, 42, 50]
field coordinates = 1
static coordinates = 0
array coordinates = 2
variable type = int
```

Phân tích từng nhóm:

1. `field.setVolatile(counter, 10)` đặt giá trị ban đầu. `getAndAdd(counter, 5)` trả về **10** rồi biến đổi giá trị thành **15**; `compareAndSet(counter, 15, 21)` thành công và đưa giá trị thành **21**.
2. `staticField` không cần truyền receiver: `setVolatile(7)` và `getVolatile()` truy cập trực tiếp field static.
3. `element.set(numbers, 1, 40)` chỉnh phần tử thứ hai. `getAndAdd(numbers, 1, 2)` trả **40** và cập nhật phần tử thành **42**. CAS đổi phần tử index 2 từ **5** sang **50**.
4. Ba kết quả tọa độ lần lượt là **1**, **0**, **2**, đúng với instance field, static field, array element; tất cả đều có `varType` là `int` trong ví dụ.

Lớp `Counter` là lớp lồng nhau của `VarHandleDemo` và cả hai cùng *nest*, vì vậy lookup trong lớp ngoài có quyền truy cập field private của `Counter`. Khi chuyển code sang những lớp/module khác, cần xét lại quyền truy cập thay vì sao chép nguyên cú pháp lookup rồi giả định kết quả vẫn thành công.

## 8. Lỗi phổ biến và cách chẩn đoán

| Lỗi / triệu chứng | Nguyên nhân thường gặp | Cách sửa |
| --- | --- | --- |
| `NoSuchFieldException` khi tìm | Sai tên field, kiểu khai báo hoặc lựa chọn instance/static | Xem field thực tế, phân biệt `findVarHandle` với `findStaticVarHandle` |
| `IllegalAccessException` khi tìm | Lookup không có quyền tới field hoặc module/package | Tạo lookup ở lớp có quyền thích hợp; xem modifier và `exports`/`opens`; không tùy tiện mở module |
| `WrongMethodTypeException` lúc gọi | Thiếu receiver, thiếu index, sai kiểu dữ liệu hoặc kiểu kết quả tại chỗ gọi | Kiểm tra `varType()`, `coordinateTypes()` và cast kết quả |
| `ClassCastException` hoặc lỗi runtime khác | Đối tượng receiver hoặc giá trị runtime không tương thích | Kiểm tra kiểu đối tượng và kiểu biến thực tế |
| `ArrayIndexOutOfBoundsException` / `NullPointerException` | Index không hợp lệ / mảng hoặc receiver null | Kiểm tra mảng, receiver và index trước khi gọi |
| `UnsupportedOperationException` | Mode không hỗ trợ, ví dụ ghi qua handle của field `final` hoặc arithmetic trên kiểu không thích hợp | Dùng `isAccessModeSupported(...)`; chọn mode/kiểu dữ liệu khác |
| Đếm sai khi nhiều thread cập nhật | Dùng cặp `get` rồi `set` dẫn tới lost update | Dùng thao tác nguyên tử như `getAndAdd` hoặc giải pháp đồng bộ phù hợp |
| Hiểu nhầm dữ liệu giữa các thread | Dùng plain access mà mong hiệu ứng volatile | Chọn access mode và giao thức đồng bộ đúng Java Memory Model |

Đừng dùng `VarHandle` như cách bỏ qua mọi quy tắc truy cập. Nó cung cấp cơ chế thao tác biến động và memory ordering, nhưng không miễn trừ quyền Java, kiểu dữ liệu, hay tính đúng đắn của thuật toán đồng thời.

## 9. Bài luyện tập

1. Viết class `Book` chứa `private int copies`. Tạo `VarHandle` hợp lệ cho field, đọc và cập nhật số bản sao. Nêu `coordinateTypes()` của handle.
2. Tạo `String[] names` và handle cho phần tử mảng. Vì sao `getAndAdd(names, 0, ...)` không phải thao tác phù hợp?
3. Nếu `Config.active` là `static boolean`, cần dùng factory nào, và số coordinate là bao nhiêu?
4. Hai thread cùng chạy `x = get(x) + 1; set(x)` thì vì sao kết quả cuối có thể nhỏ hơn tổng số lần tăng? Thay bằng API nào?
5. Với `compareAndSet`, xác định expected/update và giải thích giá trị trả về. Nếu field là `final`, thao tác nào phải tránh?
6. Thử dùng `isAccessModeSupported(VarHandle.AccessMode.GET_AND_ADD)` với một handle có kiểu biến không hỗ trợ phép cộng, rồi giải thích kết quả.

**Mục tiêu hoàn thành:** người học xác định được kiểu biến và tọa độ cho instance/static field và phần tử mảng; tạo đúng handle; gọi đúng `get`, `set`, `getAndAdd`, `compareAndSet`; chọn access mode theo yêu cầu đồng thời và nhận diện giới hạn truy cập.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-handles-var-handles-009">Access mode thường, volatile, acquire-release và atomic</a>

<details>
<summary>Xem chi tiết</summary>

# VarHandle trong Java: Plain, Volatile, Acquire–Release và Atomic Access Modes

`VarHandle` (từ Java 9) cho phép thao tác lên **một biến cụ thể** như field của đối tượng, static field hoặc phần tử mảng, đồng thời chỉ định **quy tắc đọc/ghi bộ nhớ**. Các quy tắc này gọi là *access modes*: `get`/`set` (plain), `getVolatile`/`setVolatile`, `getAcquire`/`setRelease` và những thao tác cập nhật nguyên tử như `compareAndSet`, `getAndAdd`.

Điều quan trọng cần phân biệt ngay:

- **Memory ordering/visibility**: một luồng có được phép quan sát thao tác của luồng khác theo thứ tự nào? Thao tác khác trên các field có được xuất bản an toàn không?
- **Atomicity**: một phép cập nhật có diễn ra như một bước không thể chen vào hay không? Chẳng hạn hai luồng tăng cùng bộ đếm có thể mất lần tăng không?
- **Access mode** chỉ định một phần hoặc cả hai thuộc tính trên, tùy phép toán. `volatile` không tự biến biểu thức `x++` thành nguyên tử; `getAndAdd` mới là một phép *read-modify-write* nguyên tử.

Bài này tập trung vào cách chọn mode và lý do lựa chọn đó đúng theo Java Memory Model (JMM), không coi `VarHandle` là công cụ thay thế vô điều kiện cho `synchronized`, `Lock` hay `AtomicInteger`.

## 1. VarHandle trỏ tới biến nào?

Một `VarHandle` là một tham chiếu có kiểu động mạnh (*dynamically strongly typed*) tới vị trí lưu trữ. Nó không lưu giá trị của biến. Khi gọi access mode, chương trình cung cấp **tọa độ** để chỉ ra biến cần thao tác.

Ví dụ minh họa:

```java
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

class Holder {
    int count;
}

class Example {
    static void demo() throws ReflectiveOperationException {
        VarHandle countHandle = MethodHandles.lookup()
                .findVarHandle(Holder.class, "count", int.class);
        Holder holder = new Holder();

        countHandle.set(holder, 10);              // Tọa độ: holder
        int n = (int) countHandle.get(holder);      // n = 10
    }
}
```

- `findVarHandle` tra cứu một **instance field**, nên lời gọi cần đối tượng `holder` làm tọa độ; `findStaticVarHandle` áp dụng cho static field và không yêu cầu instance.
- `MethodHandles.arrayElementVarHandle(int[].class)` tạo handle cho phần tử mảng, với tọa độ là `(mảng, chỉ số)`.
- `VarHandle` không bỏ qua quyền truy cập. `MethodHandles.lookup()` và các cơ chế lookup khác phải có quyền phù hợp khi tạo handle.
- `get`/`set`, `getAcquire`/`setRelease` và các phép atomic có chữ ký thực tế theo kiểu biến và tọa độ, dù tài liệu API biểu diễn bằng `Object...`. Sai kiểu/tọa độ có thể gây `WrongMethodTypeException` hoặc lỗi ép kiểu.
- Handle trỏ đến field `final` không hỗ trợ các mode ghi hoặc atomic update; gọi mode không hỗ trợ ném `UnsupportedOperationException`.

## 2. So sánh các nhóm access mode

| Nhóm | Đọc | Ghi/cập nhật | Bảo đảm cần nhớ | Khi phù hợp |
| --- | --- | --- | --- | --- |
| **Plain (thường)** | `get` | `set` | Không tạo ràng buộc thứ tự liên luồng bổ sung như `volatile` | Dữ liệu chỉ dùng trong một luồng hoặc đã đồng bộ bằng cơ chế khác |
| **Opaque** (mức trung gian) | `getOpaque` | `setOpaque` | Atomic đối với giá trị biến và có tính nhất quán theo thứ tự truy cập cùng biến; **không** xuất bản các field khác bằng release/acquire | Trường hợp rất đặc thù chỉ cần quan sát biến riêng lẻ |
| **Acquire–Release** | `getAcquire` | `setRelease` | Có thứ tự một chiều cho các truy cập trước/sau khi một giá trị được chuyển giao phù hợp | Publish/consume dữ liệu qua cờ trạng thái |
| **Volatile** | `getVolatile` | `setVolatile` | Hiệu ứng đọc/ghi như field `volatile`; các thao tác volatile có thứ tự tổng thể tương ứng trong mô hình bộ nhớ | Cờ chia sẻ, trạng thái cần ngữ nghĩa volatile |
| **Atomic update** | Đọc và sửa trong cùng phép toán | `compareAndSet`, `compareAndExchange`, `getAndSet`, `getAndAdd`… | Cập nhật không bị xen vào ở giữa; mỗi API có memory ordering riêng | Bộ đếm, state machine, cập nhật có cạnh tranh |

Đừng hiểu bảng như một trục duy nhất mà ở đó “atomic mạnh hơn volatile”. **Atomicity và ordering là hai chiều khác nhau**: `compareAndSet` có hiệu ứng volatile, nhưng `compareAndExchangeAcquire` hoặc `getAndAddRelease` chủ động dùng ordering yếu hơn ở một phía.

### 2.1. Plain: `get` và `set`

`get(receiver)`/`set(receiver, value)` có ngữ nghĩa như đọc/ghi một field thông thường không `volatile`.

```java
// Giả sử COUNT là VarHandle tới field int count của đối tượng counter.
COUNT.set(counter, 10);
int x = (int) COUNT.get(counter);
```

Nếu `counter` chỉ được một luồng dùng, hoặc mọi truy cập của các luồng đều nằm trong cùng một giao thức đồng bộ đúng, plain thường đủ. Nếu hai luồng trao đổi dữ liệu không đồng bộ bằng plain read/write, JMM **không bảo đảm** luồng đọc thấy cập nhật mới hoặc quan sát những field khác theo thứ tự mong muốn.

Về tính atomic của thao tác đơn lẻ: tham chiếu đối tượng và primitive tối đa 32 bit có bảo đảm đọc/ghi nguyên vẹn; với `long`/`double` plain trên một số nền tảng 32 bit có ngoại lệ. Dù một `get` đơn lẻ là atomic, phép ghép `get` rồi `set` **không** trở thành atomic read-modify-write.

**Lưu ý khó bỏ qua:** mode điều khiển ngữ nghĩa truy cập **ngay cả khi field khai báo `volatile`**. Dùng `get` qua VarHandle tới một volatile field không đồng nghĩa với `getVolatile`. Pha trộn những mode khác nhau trên cùng biến mà không có thiết kế rõ ràng dễ tạo lỗi bộ nhớ rất khó tái hiện.

### 2.2. Opaque chỉ là điểm tham chiếu

`getOpaque`/`setOpaque` có bảo đảm mạnh hơn plain cho riêng biến được truy cập nhưng không thiết lập giao thức publish/consume cho dữ liệu liên quan. Đừng thay `setRelease` bằng `setOpaque` khi cần xuất bản payload ở một field khác. Bài này chỉ nhắc opaque để đặt acquire/release và volatile vào đúng bối cảnh.

## 3. Volatile: quan sát trạng thái và thứ tự liên luồng

`setVolatile` và `getVolatile` có hiệu ứng bộ nhớ tương ứng ghi/đọc field khai báo `volatile`:

```java
// FLAG là VarHandle của field boolean ready.
FLAG.setVolatile(message, true);
boolean ready = (boolean) FLAG.getVolatile(message);
```

Tương tự cặp ghi/đọc volatile trên cùng biến, ghi volatile đứng trước một lần đọc volatile phù hợp trong *synchronization order* tạo quan hệ đồng bộ để những ghi xảy ra trước được quan sát đúng theo happens-before. Nó rất hữu ích cho cờ `shutdown`, `ready` hoặc một giá trị trạng thái được thay mới nguyên khối.

Nhưng:

- `volatile` **không** làm cho `value++` hoặc `getVolatile` rồi `setVolatile` trở thành một phép atomic. Hai luồng có thể cùng đọc 4 rồi cùng ghi 5, làm mất một lần tăng.
- Volatile không bảo đảm một luồng luôn “thấy ngay lập tức” giá trị khác tại một thời điểm giới hạn cụ thể, cũng không giải quyết race logic kiểu kiểm tra rồi hành động.
- Dùng volatile cho **mọi** field không tạo thành một transaction có nhiều field nhất quán; muốn duy trì bất biến giữa nhiều biến có thể vẫn phải dùng khóa hoặc cách gói trạng thái phù hợp.

Ví dụ tăng sai với tranh chấp:

```java
int current = (int) COUNT.getVolatile(counter);
COUNT.setVolatile(counter, current + 1); // Hai thao tác riêng; có thể mất cập nhật.
```

Phép thay thế đúng, nếu handle hỗ trợ kiểu `int`, là `COUNT.getAndAdd(counter, 1)`.

## 4. Acquire–Release: xuất bản và tiếp nhận dữ liệu

`setRelease` có hiệu ứng **release**: các phép đọc/ghi trước nó trong cùng luồng không được đẩy xuống sau nó theo các ràng buộc bộ nhớ được bảo đảm. `getAcquire` có hiệu ứng **acquire**: những phép đọc/ghi sau nó không được kéo lên trước nó. Khi một luồng acquire quan sát giá trị được xuất bản qua release **trên cùng biến đồng bộ**, các ghi payload trước release có thể được tiếp nhận an toàn sau acquire theo giao thức giao tiếp đó.

Sơ đồ một thông điệp chỉ xuất bản một lần:

```text
Producer                         Consumer
---------                        ---------------------------
message.payload = 42             while (!READY.getAcquire(message))
READY.setRelease(message, true)       chờ cờ true
                                 int x = message.payload
                                 // x = 42 theo giao thức
```

Vì sao phải có cả hai phía?

1. Producer ghi `payload` trước rồi release cờ `ready`.
2. Consumer kiểm tra cờ bằng acquire. Khi đọc được giá trị do release xuất bản, nó có được thứ tự bộ nhớ cần thiết để đọc `payload`.
3. `payload` có thể là field bình thường (**plain**) nếu nó được ghi trước release và không bị sửa đồng thời bên ngoài giao thức. Đây là trường hợp *safe publication*, không phải quyền đọc mọi field không đồng bộ.

Một số giới hạn:

- `getAcquire` **không** có tác dụng “lấy bản mới nhất” của biến tùy ý; nó chỉ tạo những ordering effect tương ứng. Lần đọc cờ có thể thấy `false` và phải thử lại.
- `setRelease` không làm dữ liệu tự động sẵn sàng ở mọi luồng nếu không có phía đọc phù hợp; cũng không bắt buộc phía đọc được lập lịch trong thời gian hữu hạn.
- Khi flag được dùng lại cho nhiều vòng đời thông điệp, cần thêm giao thức để tránh ghi đè payload khi consumer đang đọc. Ví dụ đơn giản ở đây chỉ publish **một lần**.
- Một lệnh đọc release hay ghi acquire không phải cặp cần dùng: trong mô hình này, **ghi release** phối hợp với **đọc acquire**.
- Spin loop chỉ nhằm trình diễn memory ordering; production thường dùng `BlockingQueue`, `CountDownLatch`, `Lock`/`Condition`, hoặc cấu trúc đồng bộ có sẵn để tránh đốt CPU và dễ quản lý hủy/chờ.

### Acquire–release khác volatile như thế nào?

Một cặp release/acquire chủ yếu ràng buộc thứ tự quanh việc **trao thông tin theo một chiều** (publish → consume); volatile bổ sung những bảo đảm mạnh hơn, bao gồm thứ tự tổng thể giữa các truy cập volatile. Vì vậy, nếu chỉ cần producer công bố một payload rồi consumer nhận, acquire/release có thể là lựa chọn phù hợp. Không nên tự suy ra chúng tương đương volatile trong thuật toán có nhiều biến và nhiều kiểu thao tác xen kẽ.

## 5. Atomic access modes: đọc–sửa–ghi thành một phép

`VarHandle` có các phương thức atomic update. Các phương thức phổ biến:

| Phương thức | Giá trị trả về | Ý nghĩa |
| --- | --- | --- |
| `compareAndSet(receiver, expected, update)` | `boolean` | Chỉ ghi nếu giá trị hiện tại bằng `expected` theo phép so sánh của biến; có hiệu ứng volatile |
| `compareAndExchange(receiver, expected, update)` | Giá trị đã quan sát (*witness*) | Trả giá trị thực tế tại thời điểm CAS; ghi khi witness bằng expected |
| `getAndSet(receiver, update)` | Giá trị cũ | Đổi giá trị nguyên tử và trả bản cũ |
| `getAndAdd(receiver, delta)` | Giá trị cũ | Cộng nguyên tử vào kiểu số được hỗ trợ, như `int`/`long` |
| `getAndBitwiseOr` / `getAndBitwiseAnd` / `getAndBitwiseXor` | Giá trị cũ | Cập nhật bit nguyên tử cho kiểu hỗ trợ |
| `weakCompareAndSetPlain` và các biến thể `weakCompareAndSet...` | `boolean` | CAS yếu, **có thể thất bại giả** dù giá trị so sánh khớp; yêu cầu kiểm tra/retry phù hợp |

**Đọc đúng kết quả CAS:**

```java
boolean updated = COUNT.compareAndSet(counter, 4, 5); // true nếu đang là 4
int observed = (int) COUNT.compareAndExchange(counter, 5, 6);
// observed = 5 nếu CAS thành công; nếu đang là giá trị khác thì không ghi và trả giá trị đó
```

Với biến kiểu tham chiếu, phép so sánh expected của CAS dùng **đồng nhất tham chiếu (`==`)**, không gọi `equals()`. Ví dụ, hai đối tượng khác nhau dù `equals` trả true vẫn không khớp trong CAS.

### Vì sao CAS thường cần vòng lặp?

Khi nhiều luồng cùng cập nhật một trạng thái phụ thuộc giá trị cũ, một luồng có thể đọc giá trị cũ rồi bị luồng khác thay đổi trước khi CAS. `compareAndSet` thất bại là tín hiệu thử lại với trạng thái mới:

```java
static void addTen(VarHandle handle, Counter counter) {
    for (;;) {
        int before = (int) handle.getVolatile(counter);
        int after = before + 10;
        if (handle.compareAndSet(counter, before, after)) {
            return;
        }
    }
}
```

Đây là **đoạn minh họa**, giả định đã có lớp `Counter` với field `int value` và một `VarHandle` trỏ đúng field đó. Với phép cộng đơn giản, `getAndAdd(counter, 10)` ngắn gọn và thường phù hợp hơn. Với phép tính phức tạp, cần cân nhắc side effect, contention và việc biểu thức tính lại sau thất bại CAS.

**ABA:** CAS chỉ biết giá trị hiện tại có bằng expected hay không. Nếu một biến lần lượt đổi A → B → A, CAS so với A vẫn có thể thành công dù đã có thay đổi trung gian. Thuật toán cần phát hiện chuyện đó phải lưu thêm version/stamp hoặc áp dụng cơ chế thích hợp khác.

### Memory ordering của các phép atomic

- `compareAndSet`, `compareAndExchange`, `getAndSet` và `getAndAdd` (bản không hậu tố) có hiệu ứng đọc/ghi volatile theo đặc tả riêng.
- Các biến thể `...Acquire` thường chỉ áp dụng ordering acquire ở phía đọc; `...Release` thường chỉ áp dụng ordering release ở phía ghi. Không giả định mọi biến thể mang đầy đủ hiệu ứng volatile.
- `weakCompareAndSetPlain` dùng ngữ nghĩa plain và có thể thất bại giả; những biến thể khác có hiệu ứng tương ứng trong tên.
- Atomicity không đồng nghĩa thuật toán không bao giờ bị tranh chấp, tự bảo đảm fairness, hay luôn kết thúc sau số bước cố định.

Nếu không rõ mode thấp hơn có đủ cho bất biến và quan hệ happens-before của thuật toán hay không, ưu tiên API atomic tiêu chuẩn hoặc phép có ngữ nghĩa volatile dễ lý giải hơn, rồi tối ưu theo bằng chứng.

## 6. Chương trình Java 17+: so sánh mode và chạy đa luồng

Tạo file **`VarHandleModesDemo.java`**:

```java
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class VarHandleModesDemo {
    static class Cell {
        int value;
    }

    static class Message {
        int payload;
        boolean ready;
    }

    public static void main(String[] args)
            throws ReflectiveOperationException, InterruptedException {
        VarHandle value = MethodHandles.lookup()
                .findVarHandle(Cell.class, "value", int.class);
        VarHandle ready = MethodHandles.lookup()
                .findVarHandle(Message.class, "ready", boolean.class);

        Cell cell = new Cell();

        // 1. Plain: thao tác đơn luồng, chưa có chia sẻ.
        value.set(cell, 3);
        System.out.println("plain=" + (int) value.get(cell));

        // 2. Volatile: đọc/ghi riêng lẻ với hiệu ứng volatile.
        value.setVolatile(cell, 4);
        System.out.println("volatile=" + (int) value.getVolatile(cell));

        // 3. Atomic compare-and-set và read-modify-write.
        boolean updated = value.compareAndSet(cell, 4, 5);
        System.out.println("CAS=" + updated
                + ", value=" + (int) value.getVolatile(cell));

        int old = (int) value.getAndAdd(cell, 2);
        System.out.println("getAndAdd old=" + old
                + ", value=" + (int) value.getVolatile(cell));

        int witness = (int) value.compareAndExchange(cell, 7, 9);
        System.out.println("exchange witness=" + witness
                + ", value=" + (int) value.getVolatile(cell));

        // 4. Nhiều luồng tăng cùng biến bằng phép atomic.
        value.setVolatile(cell, 0);
        Thread[] workers = new Thread[4];
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Thread(() -> {
                for (int j = 0; j < 10_000; j++) {
                    value.getAndAdd(cell, 1);
                }
            });
            workers[i].start();
        }
        for (Thread worker : workers) {
            worker.join();
        }
        System.out.println("atomic counter="
                + (int) value.getVolatile(cell));

        // 5. Producer/consumer: publish một payload bằng release/acquire.
        Message message = new Message();
        Thread reader = new Thread(() -> {
            while (!(boolean) ready.getAcquire(message)) {
                Thread.onSpinWait(); // Chỉ dùng spin để minh họa.
            }
            System.out.println("published payload=" + message.payload);
        });
        reader.start();

        message.payload = 42;
        ready.setRelease(message, true);
        reader.join();
    }
}
```

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

```bash
javac VarHandleModesDemo.java
java VarHandleModesDemo
```

Kết quả:

```text
plain=3
volatile=4
CAS=true, value=5
getAndAdd old=5, value=7
exchange witness=7, value=9
atomic counter=40000
published payload=42
```

**Vì sao kết quả đúng?**

1. Plain và volatile ở đầu ví dụ chạy tuần tự trong `main`, nên lần đọc thấy giá trị vừa ghi. Không dùng những thao tác plain đó để suy ra quy tắc chia sẻ giữa các luồng.
2. CAS thấy `4` rồi đổi thành `5`. `getAndAdd` thêm `2`, trả lại giá trị cũ `5`; `compareAndExchange` thấy `7` rồi đổi thành `9`.
3. Bốn worker, mỗi worker tăng `10_000` lần bằng `getAndAdd` trên cùng field. Các phép tăng không bị mất cập nhật, nên tổng là `40_000` sau khi `join` cả bốn luồng.
4. Producer ghi `message.payload = 42` trước `ready.setRelease(message, true)`. Reader chỉ đọc payload sau khi `getAcquire` đã thấy cờ `true`. Đây là một giao thức publish/consume một lần; cờ ban đầu mặc định `false`.
5. `Thread.join()` khiến luồng main đợi kết thúc worker và reader, nhờ đó kết quả in không bị bỏ sót. Vòng chờ trong reader không nhằm làm mẫu cho production.

Đoạn code dùng `Thread.onSpinWait()` từ Java 9, `VarHandle` từ Java 9 và không cần thư viện bên ngoài.

## 7. Lựa chọn mode theo bài toán

| Tình huống | Gợi ý | Lý do |
| --- | --- | --- |
| Dữ liệu cục bộ trong một luồng | `get`/`set` (hoặc truy cập field trực tiếp) | Không cần synchronization bổ sung |
| Flag dừng hoặc trạng thái chia sẻ đơn giản | `getVolatile`/`setVolatile`, hoặc field `volatile` | Dễ diễn giải hiệu ứng volatile |
| Producer xuất bản payload một lần | `setRelease` + `getAcquire` trên flag | Tạo ordering cần thiết khi consumer thấy flag được publish |
| Nhiều luồng cộng chung một bộ đếm | `getAndAdd` hoặc `AtomicInteger` | Mỗi lần cộng nguyên tử, không mất cập nhật |
| Cập nhật nếu trạng thái chưa đổi | `compareAndSet`, hoặc `compareAndExchange` nếu cần witness | Kiểm tra và ghi trong một phép atomic |
| Phải đồng bộ bất biến của nhiều field hoặc một critical section | `synchronized`/`Lock` hoặc thiết kế trạng thái bất biến | Atomic một field không đủ bảo vệ cả nhóm |

`VarHandle` hữu ích trong mã thư viện hiệu năng thấp tầng và xây dựng primitive đồng bộ. Ứng dụng thông thường nên ưu tiên `AtomicInteger`, `AtomicReference`, `BlockingQueue`, `Lock`, `synchronized` hoặc các cấu trúc `java.util.concurrent` khi chúng đáp ứng trực tiếp nhu cầu. Chúng dễ review hơn và giảm rủi ro sử dụng sai ngữ nghĩa bộ nhớ.

## 8. Lỗi phổ biến và cách kiểm tra

**Lỗi 1 — Tưởng `volatile` làm `++` nguyên tử.** Truy cập volatile riêng lẻ chỉ bảo đảm ngữ nghĩa cho lần đọc/ghi đó. Để tăng an toàn khi tranh chấp, dùng `getAndAdd` hoặc cập nhật CAS hợp lệ.

**Lỗi 2 — Dùng plain để báo hiệu giữa các luồng.** Nếu consumer chỉ gọi `get` để xem flag mà không có một cơ chế đồng bộ khác, chương trình thiếu bảo đảm visibility/order mong muốn. Dùng acquire/release hoặc volatile theo đúng giao thức.

**Lỗi 3 — Viết payload sau release.** Nếu producer gọi `setRelease(ready, true)` trước rồi mới gán payload, consumer có thể quan sát cờ mà chưa có giá trị payload mới. Luôn thiết lập dữ liệu cần xuất bản **trước** release.

**Lỗi 4 — Acquire từ biến A nhưng release ở biến B.** Không tự động có quan hệ đồng bộ mong muốn. Cần một giao thức kết nối trên đúng vị trí và đúng sự kiện publish/consume.

**Lỗi 5 — Trộn mode trên cùng biến theo cảm tính.** `get` qua `VarHandle` không tự kế thừa volatile từ khai báo field. Hãy chọn một giao thức nhất quán và xem lại đặc tả khi phối hợp modes.

**Lỗi 6 — Cho rằng CAS thành công nghĩa là trạng thái chưa từng đổi.** Hiện tượng ABA cho thấy trạng thái có thể thay đổi trung gian rồi quay về giá trị dự kiến.

**Lỗi 7 — Gọi mode không được hỗ trợ.** Handle cho `final` không cho phép ghi; không phải mọi thao tác số/bit đều hợp lệ với mọi kiểu. Kiểm tra `isAccessModeSupported(VarHandle.AccessMode.XXX)` khi dùng handle trừu tượng.

**Lỗi 8 — Sai kiểu kết quả do các lời gọi signature-polymorphic.** Với field `int`, viết `(int) handle.get(obj)`; với field `boolean`, viết `(boolean) handle.getAcquire(obj)`. Sai kiểu/tọa độ có thể khiến chương trình lỗi lúc chạy.

### Tự kiểm tra

1. Vì sao hai luồng cùng dùng `getVolatile` + `setVolatile` để tăng counter vẫn có thể mất cập nhật?
2. Khi consumer thấy `ready == true` bằng `getAcquire`, những ghi nào của producer được publish qua `setRelease`?
3. `compareAndSet` và `compareAndExchange` khác nhau về giá trị trả về như thế nào?
4. Nếu một thuật toán cần bảo vệ đồng thời `balance` và `version`, cập nhật atomic một field riêng lẻ đã đủ chưa?
5. Vì sao có thể dùng field `payload` bình thường trong ví dụ producer/consumer, nhưng không được ghi lại payload tùy ý sau khi đã publish?
6. Nếu không cần tối ưu thấp tầng, vì sao `AtomicInteger` thường dễ dùng hơn tự quản lý `VarHandle`?

### Tài liệu tra cứu

- [Java SE 17 — `VarHandle` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/VarHandle.html): danh sách các access mode, tính atomic và memory ordering.
- [Java SE 17 — `MethodHandles` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandles.html): tạo handle và yêu cầu quyền lookup.
- [Java Language Specification `17.4 — Memory Model](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.4): `happens-before`, `synchronization order` và các quy tắc bộ nhớ liên quan.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-handles-var-handles-010">Khi nào chọn Method, MethodHandle hoặc VarHandle</a>

<details>
<summary>Xem chi tiết</summary>

# Khi nào chọn `Method`, `MethodHandle` hoặc `VarHandle` trong Java Reflection

## 1. Bài toán: một thành viên Java, ba cách làm việc khác nhau

Khi chương trình đã biết kiểu và phương thức cần gọi ở lúc biên dịch, cách đơn giản nhất thường là **gọi trực tiếp** qua kiểu Java hoặc interface. Nhưng framework, công cụ ánh xạ dữ liệu, bộ xử lý biểu thức, thư viện serialization hoặc hệ thống plugin đôi khi chỉ biết tên lớp, tên phương thức hoặc tên trường dữ liệu (*field*) trong lúc chạy. Khi đó Java cung cấp các công cụ khác nhau:

- `java.lang.reflect.Method`: đối tượng **mô tả một phương thức** để kiểm tra metadata (tên, tham số, annotation, modifiers...) và có thể gọi bằng `invoke`.
- `java.lang.invoke.MethodHandle`: một **tham chiếu có thể gọi** đến phương thức, constructor hoặc thao tác khác với kiểu lời gọi được biểu diễn rõ bằng `MethodType`. Phù hợp khi đã phân giải mục tiêu và cần tổ chức lời gọi động có kiểu, biến đổi chữ ký hoặc tái sử dụng.
- `java.lang.invoke.VarHandle`: tham chiếu có kiểu đến **vị trí chứa giá trị** như field hoặc phần tử mảng, với các chế độ đọc/ghi và một số phép **nguyên tử/đồng bộ bộ nhớ**. Phù hợp khi bài toán thực sự là truy cập biến, đặc biệt trong mã đồng thời.

Ba công cụ không phải ba phiên bản có thể thay thế trực tiếp. Nếu cần *khám phá metadata*, `Method` là điểm khởi đầu tự nhiên. Nếu cần *gọi một hành vi đã chọn*, `MethodHandle` cung cấp cách gọi và phối hợp linh hoạt. Nếu cần *đọc/ghi hoặc cập nhật nguyên tử một biến*, `VarHandle` diễn tả trực tiếp bài toán đó.

> Quy tắc đầu tiên: nếu mã ứng dụng đã biết chính xác API và có thể gọi trực tiếp an toàn, hãy ưu tiên lời gọi Java bình thường. Reflection chỉ có ích khi chương trình phải lựa chọn hoặc thao tác động ở runtime.

## 2. Bảng quyết định nhanh

| Câu hỏi chính | `Method` | `MethodHandle` | `VarHandle` |
| --- | --- | --- | --- |
| Đối tượng này mô tả gì? | Metadata của **phương thức** | Mục tiêu **có thể gọi** với `MethodType` | **Vị trí biến** và các chế độ truy cập |
| Lấy bằng cách nào? | `Class.getMethod` / `getDeclaredMethod` | `MethodHandles.Lookup.findVirtual`, `findStatic`, `unreflect`... | `Lookup.findVarHandle`, `findStaticVarHandle`, `arrayElementVarHandle`... |
| Thao tác chính | `invoke(receiver, Object...)` | `invokeExact(...)` / `invoke(...)` | `get`, `set`, `getVolatile`, `compareAndSet`, `getAndAdd`... |
| Có truy vấn annotation, tên và modifiers? | **Có**, qua API Reflection | **Không** như một `Method`; có thông tin kiểu lời gọi | Không dùng để khám phá annotation của field |
| Kiểu tham số/giá trị trả về | Đi qua `Object`, boxing/unboxing khi cần | Kiểu lời gọi rõ trong `MethodType` và call site | Kiểu tọa độ và giá trị của variable access mode |
| Xử lý ngoại lệ từ phương thức đích | `Method.invoke` gói ngoại lệ đích trong `InvocationTargetException` | Ngoại lệ của đích truyền ra trực tiếp theo luồng thực thi | Lỗi phụ thuộc access mode, kiểu và thao tác |
| Hữu ích cho đồng bộ bộ nhớ/CAS? | Không phải mục đích của `Method` | Có thể gọi mã đồng bộ có sẵn, không tự cung cấp CAS cho field | **Có**, các access mode hỗ trợ ngữ nghĩa bộ nhớ và cập nhật nguyên tử |
| Tình huống tiêu biểu | Framework duyệt annotation rồi gọi phương thức hiếm khi | Bộ gọi động tái sử dụng, adapter cho phương thức đã tìm | Bộ đếm, trạng thái, truy cập field/mảng cần quy tắc concurrent rõ ràng |

**Đừng chọn chỉ theo khẩu hiệu “API mới luôn nhanh hơn”.** Chi phí phụ thuộc dạng gọi, việc tái sử dụng kết quả tra cứu, sự tối ưu của JVM, tần suất thao tác và mức độ cạnh tranh giữa các thread. Nếu hiệu năng là yêu cầu thực tế, hãy đo trong điều kiện tương ứng.

## 3. `Method`: cần thông tin về phương thức và lời gọi động đơn giản

### 3.1. Điều `Method` làm tốt

`Class.getMethod(name, parameterTypes...)` tra cứu phương thức `public` phù hợp (kể cả phương thức `public` được kế thừa theo quy tắc API). `Class.getDeclaredMethod(...)` tra cứu phương thức khai báo **trực tiếp** trong lớp, bất kể modifier, nhưng không tự tìm trong superclass. Kết quả là `Method`, có thể dùng để:

- Đọc `getName()`, `getParameterTypes()`, `getReturnType()`, `getModifiers()`, `getDeclaringClass()`.
- Xem annotation với `getAnnotations()` hoặc `getAnnotation(...)`, xem metadata generic khi cần.
- Chọn đúng phương thức trong framework dựa trên quy ước hay annotation.
- Gọi phương thức qua `invoke(target, args...)` nếu có quyền truy cập.

Ví dụ **fragment**, giả định lớp `Service` công khai có phương thức `public String greet(String name)`:

~~~java
Method method = Service.class.getMethod("greet", String.class);
Object result = method.invoke(service, "Minh");
String message = (String) result;
~~~

Cần `import java.lang.reflect.Method;`, biến `service` có kiểu phù hợp, và xử lý `ReflectiveOperationException` cùng ngoại lệ liên quan quyền truy cập. Đối với phương thức instance, đối số đầu tiên của `invoke` là **receiver**; với static method, có thể truyền `null`. Kết quả primitive được **boxing** thành wrapper tương ứng; phương thức trả `void` cho kết quả `null`.

### 3.2. Chi phí và lỗi cần hiểu

- Mỗi lần `Method.invoke` nhận đối số trong mảng `Object[]` (dạng varargs); boxing/unboxing và chuyển kiểu có thể phát sinh. API tự kiểm tra receiver/arguments và có thể ném `IllegalArgumentException` nếu sai.
- Nếu phương thức đích ném ngoại lệ, `Method.invoke` ném `InvocationTargetException`; **ngoại lệ gốc** nằm ở `getCause()`.
- `getDeclaredMethod` tìm được private method **không đồng nghĩa** với việc được quyền gọi. Java kiểm tra quyền truy cập; `setAccessible(true)` có thể bị từ chối bởi ranh giới module. Khi có thể, thiết kế API công khai hoặc cấu hình `opens` phù hợp thay vì tìm cách phá quyền truy cập.
- Không nên gọi `getMethod`/`getDeclaredMethod` lặp lại trong một vòng lặp nóng nếu đã biết chính xác phương thức; có thể lưu `Method` đã được phân giải.

**Khi nên chọn:** mã cần metadata hoặc phải gọi một số phương thức dựa trên cấu hình/annotation, tần suất không cao và độ đơn giản quan trọng hơn khả năng tối ưu bộ gọi động.

## 4. Kiểm tra kiểu và quyền trước khi chọn cơ chế
Khi đã chọn đích, `MethodHandle` được lấy qua `MethodHandles.Lookup` và có chữ ký `handle.type()`. Với `findVirtual`, kiểu dùng để tra cứu mô tả phương thức chưa có receiver, còn handle kết quả nhận receiver ở đối số đầu; `invokeExact` yêu cầu khớp cả tham số **và kiểu trả về** tại call site. `Method` cho phép khám phá annotation; khi cần gọi lặp, có thể chuyển metadata thành handle bằng `Lookup.unreflect(method)` sau khi kiểm tra quyền.

`VarHandle` cần `varType()` cùng `coordinateTypes()` (receiver cho instance field; không tọa độ cho static field; mảng và chỉ số cho phần tử mảng). `getAndAdd` trả giá trị cũ trước phép cộng; `compareAndSet` trả boolean. Các phép thường `get`/`set` không tự đồng bộ giữa thread và hai phép riêng lẻ không hợp thành phép tăng nguyên tử.

Cả ba cơ chế đều có kiểm tra quyền. Với member private xuyên named module, `privateLookupIn` cần lookup nguồn có quyền phù hợp và package đích mở đúng module, nên hãy chọn API public khi có thể.

## 5. Ví dụ Java 17+ chạy độc lập: cùng chương trình, ba vai trò

Tạo tệp `ChoosingMemberAccessDemo.java`:

~~~java
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ChoosingMemberAccessDemo {
    public static class Counter {
        private int count;

        public int twice(int x) {
            return x * 2;
        }

        public void fail() {
            throw new IllegalStateException("boom");
        }
    }

    public static void main(String[] args) throws Throwable {
        Counter counter = new Counter();

        // Method: tra cứu metadata và gọi phương thức phản chiếu.
        Method reflected = Counter.class.getMethod("twice", int.class);
        System.out.println("Method name=" + reflected.getName());
        int fromMethod = (Integer) reflected.invoke(counter, 3);
        System.out.println("Method result=" + fromMethod);

        // MethodHandle: gọi phương thức đã phân giải với kiểu chính xác.
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodHandle twice = lookup.findVirtual(
                Counter.class,
                "twice",
                MethodType.methodType(int.class, int.class));
        int fromHandle = (int) twice.invokeExact(counter, 4);
        System.out.println("MethodHandle result=" + fromHandle);

        // VarHandle: truy cập field instance và cập nhật nguyên tử.
        VarHandle count = lookup.findVarHandle(
                Counter.class, "count", int.class);
        System.out.println("VarHandle initial=" + (int) count.get(counter));
        count.set(counter, 10);
        boolean updated = count.compareAndSet(counter, 10, 15);
        int oldValue = (int) count.getAndAdd(counter, 5);
        int current = (int) count.getVolatile(counter);
        System.out.println("CAS success=" + updated);
        System.out.println("getAndAdd previous=" + oldValue);
        System.out.println("VarHandle current=" + current);

        // VarHandle có thể truy cập cả phần tử mảng.
        VarHandle elements = MethodHandles.arrayElementVarHandle(int[].class);
        int[] numbers = {1};
        int previousElement = (int) elements.getAndAdd(numbers, 0, 4);
        System.out.println("array previous=" + previousElement
                + ", current=" + numbers[0]);

        // Cùng một lỗi đích, hai cơ chế ném ngoại lệ khác nhau.
        Method failMethod = Counter.class.getMethod("fail");
        try {
            failMethod.invoke(counter);
        } catch (InvocationTargetException ex) {
            System.out.println("Method exception wrapper="
                    + ex.getClass().getSimpleName());
            System.out.println("Method cause="
                    + ex.getCause().getClass().getSimpleName());
        }

        MethodHandle failHandle = lookup.findVirtual(
                Counter.class, "fail", MethodType.methodType(void.class));
        try {
            failHandle.invokeExact(counter);
        } catch (IllegalStateException ex) {
            System.out.println("MethodHandle exception="
                    + ex.getClass().getSimpleName());
        }
    }
}
~~~

Vì `Counter` và lớp chứa `main` là **nestmates**, `MethodHandles.lookup()` trong lớp ngoài có thể tìm field `private count` của lớp lồng bên trong. Ví dụ này không có nghĩa Lookup của một lớp bất kỳ được quyền truy cập mọi field private ở nơi khác.

Lệnh biên dịch và chạy:

~~~bash
javac ChoosingMemberAccessDemo.java
java ChoosingMemberAccessDemo
~~~

Kết quả:

~~~text
Method name=twice
Method result=6
MethodHandle result=8
VarHandle initial=0
CAS success=true
getAndAdd previous=15
VarHandle current=20
array previous=1, current=5
Method exception wrapper=InvocationTargetException
Method cause=IllegalStateException
MethodHandle exception=IllegalStateException
~~~

Những điểm cần quan sát:

1. `Method` cung cấp metadata `getName()` và thực hiện `invoke`. Kết quả trả qua `Object` nên ở đây phải chuyển từ wrapper `Integer` về `int`.
2. `MethodHandle` giữ kiểu lời gọi (`Counter`, `int`) → `int`. Kết quả `8` đến từ `twice(4)` chứ không liên quan field `count`.
3. `VarHandle` trỏ đến chính field `count`. `compareAndSet` đổi `10` thành `15`; `getAndAdd` trả giá trị cũ `15` rồi cập nhật thành `20`.
4. `arrayElementVarHandle` lấy một VarHandle dùng tọa độ (`int[]`, chỉ số `int`), cho phép thao tác nguyên tử trên một phần tử mảng.
5. Phương thức `fail()` ném cùng `IllegalStateException` nhưng `Method.invoke` bọc lỗi trong `InvocationTargetException`, còn handle truyền ngoại lệ từ đích trực tiếp ra.

Đây là ví dụ đơn luồng để kiểm chứng API và stdout. Nó **không phải benchmark** và cũng không chứng minh tính đúng đắn của một thuật toán đa luồng chỉ qua một lần chạy.

## 6. Quyền truy cập và module: chọn đúng API chưa đủ

Ba API đều chịu mô hình truy cập Java. Sự khác nhau nằm ở **cách phát hiện và nơi kiểm tra quyền**:

- `Method` có thể tìm private method bằng `getDeclaredMethod`. Lúc `invoke`, quyền truy cập vẫn được kiểm tra; `trySetAccessible` trả `false` khi không thể nới quyền, còn `setAccessible(true)` có thể ném `InaccessibleObjectException`.
- `MethodHandle` thường được lấy qua `Lookup.findVirtual` / `findStatic`; quyền truy cập được đánh giá dựa trên đối tượng `Lookup` tại thời điểm tìm kiếm. `publicLookup()` và `lookup()` không tương đương.
- `VarHandle` được tìm qua `Lookup.findVarHandle` / `findStaticVarHandle`; quyền truy cập field cũng tuân theo `Lookup` và giới hạn của module.
- `MethodHandles.privateLookupIn(target, callerLookup)` có thể tạo Lookup đủ quyền với một target cụ thể khi **người gọi có quyền thích hợp và module đích cho phép mở package**. Với một named module khác, điều kiện `opens` phải được thỏa; không mặc định được quyền đọc/ghi private members của mọi module.

Ví dụ dùng `privateLookupIn` khi package đích được mở hợp lệ:

~~~java
MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(
        Target.class, MethodHandles.lookup());
MethodHandle handle = privateLookup.findVirtual(
        Target.class, "hidden", MethodType.methodType(void.class));
~~~

*Fragment này giả định `Target` tồn tại và có instance method `private void hidden()`. Vẫn phải truyền instance `Target` khi gọi handle, xử lý `IllegalAccessException` / `NoSuchMethodException` lúc tra cứu và `Throwable` khi gọi.*

Một `VarHandle` tham chiếu field `final` có thể cho phép đọc nhưng **không hỗ trợ thao tác ghi/cập nhật** phù hợp. Việc lấy được handle không có nghĩa gọi `set` hay CAS sẽ thành công: truy cập không được hỗ trợ sẽ ném `UnsupportedOperationException`. Với `Method`, tên field không liên quan: nếu cần metadata field, dùng `java.lang.reflect.Field`; nếu cần thao tác nguyên tử với field, xem `VarHandle`.

## 7. Khi có cả `Method` và `MethodHandle`

Nhiều framework phải **khám phá trước, gọi sau**, nên kết hợp hai API là hợp lý:

~~~java
Method discovered = SomeApi.class.getMethod("process", String.class);
MethodHandle callable = MethodHandles.lookup().unreflect(discovered);
~~~

Đây là fragment giả định `SomeApi` tồn tại và method có thể truy cập từ Lookup hiện tại. `unreflect` chuyển metadata thành callable handle, nhưng **không tự mở khóa truy cập**. Nếu cần annotation, giữ `discovered` để truy vấn. Nếu cần gọi nhiều lần, giữ `callable` để tránh tra cứu lặp.

Một handle cho phương thức instance vẫn nhận receiver trừ khi được **bind** vào một đối tượng:

~~~java
MethodHandle bound = callable.bindTo(apiInstance);
~~~

`bindTo` cố định đối số đầu tiên là receiver (khi kiểu handle phù hợp). Sau đó lời gọi nhận các đối số còn lại. Cần phân biệt rõ `MethodType` của handle **trước và sau khi bind**, và đừng trộn `invokeExact` với những call site dùng sai kiểu kết quả.

### Không thay `VarHandle` bằng `MethodHandle` một cách máy móc

`Lookup.findGetter` / `findSetter` cũng tạo được `MethodHandle` để đọc/ghi field khi hợp lệ. Nhưng một getter-handle bình thường không tự cung cấp `compareAndSet` hay các chế độ `getAcquire`, `setRelease`, `getVolatile`. Nếu yêu cầu là bộ nhớ và cập nhật nguyên tử của một vị trí dữ liệu, hãy dùng `VarHandle` hoặc abstraction đồng thời phù hợp.

`VarHandle.toMethodHandle(AccessMode)` còn có thể tạo `MethodHandle` cho một access mode cụ thể khi cần tích hợp vào pipeline gọi động. **Ngữ nghĩa truy cập** vẫn đến từ access mode của `VarHandle`, không phải do bất kỳ `MethodHandle` nào cũng có thuộc tính đó.

## 8. Chọn theo tình huống dự án

| Tình huống | Lựa chọn đầu tiên | Lý do |
| --- | --- | --- |
| Biết interface/phương thức từ source code và gọi thường xuyên | Gọi Java trực tiếp | Dễ đọc, compiler kiểm tra kiểu, dễ refactor |
| Quét phương thức có annotation `@Route` / `@Test` / `@Handler` | `Method` | API truy vấn metadata khai báo |
| Cấu hình chỉ định method bằng tên, chỉ gọi vài lần | `Method` | Đơn giản; không cần thêm tầng adapter |
| Framework chọn handler một lần rồi gọi lặp lại ở nhiều request | `MethodHandle` (có thể tra cứu bằng `Method` trước) | Kiểu gọi rõ; hỗ trợ adapter/combination, có tiềm năng JVM tối ưu |
| Dynamic invocation phải đổi/bind/reorder tham số | `MethodHandle` | `asType`, `bindTo`, các combinator |
| Đọc annotation của field và chính sách serialization | `Field` để khám phá metadata | `VarHandle` không thay API metadata |
| Cập nhật field `int` bằng CAS hoặc lấy giá trị cũ rồi cộng | `VarHandle` hoặc `AtomicInteger` | Có primitive atomic access mode, không cần tự dựng read-modify-write |
| Truy cập phần tử mảng bằng thuật toán đồng thời | `arrayElementVarHandle` | Có các access mode trên phần tử mảng |
| Muốn thay đổi value của một field `final` | Thiết kế lại trạng thái | `VarHandle` không cho ghi field `final` chỉ vì tìm được handle |
| Cần xây khóa, hàng đợi, cấu trúc đồng thời phức tạp | Cấu trúc `java.util.concurrent` đã kiểm chứng trước | Dễ duy trì hơn thuật toán dùng CAS/memory ordering tự viết |

**Cùng một hệ thống có thể sử dụng cả ba:** `Method` khám phá annotation; `MethodHandle` thực hiện lời gọi; `VarHandle` quản lý trạng thái điều phối của framework. Quyết định phụ thuộc trách nhiệm của từng đoạn mã.

## 9. Hiệu năng: hiểu đúng trước khi tối ưu

Một lựa chọn có thể ảnh hưởng đến tốc độ nhưng không thể kết luận từ tên API:

1. **Tra cứu và thực thi là hai giai đoạn khác nhau.** So sánh “`getMethod` rồi `invoke` mỗi vòng lặp” với “một `MethodHandle` đã cache” là so sánh hai chiến lược khác nhau; hãy tách chi phí tra cứu khỏi chi phí gọi.
2. **Loại call site quan trọng.** `MethodHandle.invokeExact` với kiểu ổn định có tiềm năng tối ưu tốt, nhưng pipeline adapter, lời gọi đa hình, boxing, visibility và warmup JVM làm kết quả thay đổi.
3. **VarHandle access modes có chi phí và ý nghĩa khác nhau.** `getVolatile` hoặc CAS thường có chi phí đồng bộ/atomic khác `get`. Không dùng chế độ yếu hơn chỉ để có kết quả benchmark đẹp nếu thuật toán yêu cầu memory ordering mạnh hơn.
4. **Đo với JMH khi đây là nút thắt hiệu năng thực tế.** Có warmup, đủ lượt đo, tránh JVM loại bỏ tính toán, đo nhiều thread nếu ứng dụng cạnh tranh tài nguyên. Không suy từ một ví dụ chạy một lần thành kết luận tổng quát “MethodHandle luôn nhanh hơn Method”.

Đặc biệt, nếu mục tiêu chỉ là một bộ đếm an toàn đồng thời, sử dụng `AtomicInteger` với `incrementAndGet()` thường rõ nghĩa và giảm nguy cơ sai khi viết mã đồng thời bằng `VarHandle`.

## 10. Lỗi và ngộ nhận thường gặp

**“MethodHandle chỉ là Method đổi tên.”** Sai. `Method` lưu metadata của khai báo, trong khi `MethodHandle` là một callable có kiểu lời gọi, có thể được bind/adapt/combine. Có thể chuyển `Method` thành handle nhưng vai trò khác nhau.

**“MethodHandle.invokeExact sẽ tự đổi int thành long nếu cần.”** Sai. `invokeExact` đòi hỏi kiểu chính xác, kể cả kiểu kết quả; dùng `invoke`/`asType` khi quy tắc chuyển đổi phù hợp.

**“Tìm được private Method/VarHandle thì chắc chắn gọi hoặc ghi được.”** Sai. Quyền truy cập, module và các access mode được hỗ trợ đều có giới hạn riêng.

**“VarHandle.get rồi VarHandle.set là thao tác tăng nguyên tử.”** Sai. Hai thao tác riêng lẻ có thể bị xen giữa bởi thread khác. Dùng `getAndAdd`, CAS loop hợp lệ hoặc abstraction đồng thời phù hợp.

**“Chỉ cần sử dụng volatile là mọi trạng thái gồm nhiều field tự nhất quán.”** Sai. Volatile/CAS tác động lên biến được dùng và quan hệ bộ nhớ theo memory model; bất biến liên quan nhiều biến còn phải được thiết kế.

**“Bắt InvocationTargetException cho MethodHandle.invokeExact.”** Thường là sai trọng tâm. Lớp bọc này là đặc trưng của `Method.invoke` khi phương thức đích ném; MethodHandle chuyển ngoại lệ của đích ra trực tiếp.

**“Cứ thay mọi Method bằng MethodHandle thì sẽ nhanh hơn.”** Không có bảo đảm như vậy. Cache đích, kiểu call site, JVM, môi trường triển khai và số lượng lời gọi quyết định hiệu quả thực tế.

## 11. Tự kiểm tra

1. Bạn cần tìm mọi phương thức gắn một annotation của controller. API nào phù hợp? **`Method`**, vì cần metadata.
2. Sau khi chọn một handler theo annotation, server sẽ gọi handler nhiều lần. Có thể tối ưu cấu trúc như thế nào? **Giữ `Method` để phân tích metadata rồi tạo/cache `MethodHandle` dùng khi gọi; đo hiệu năng nếu cần.**
3. Bạn muốn tăng `int counter` một cách nguyên tử từ nhiều thread. `MethodHandle.findGetter` có đủ không? **Không.** Cân nhắc `VarHandle.getAndAdd` hoặc `AtomicInteger`.
4. Một `Method` dùng `invoke` gọi phương thức đích ném `IllegalStateException`. Nên tìm nguyên nhân gốc ở đâu? **`InvocationTargetException.getCause()`.**
5. Với `MethodHandle.invokeExact`, tại sao gán kết quả vào biến kiểu khác có thể lỗi? **Kiểu kết quả tại call site là phần của chữ ký phải khớp.**
6. `VarHandle` có hỗ trợ `getAndAdd` cho mọi field không? **Không.** Phụ thuộc loại biến và access mode; kiểm tra API/hỗ trợ.
7. Nếu xử lý một map thường, không có truy cập động hay yêu cầu atomic custom, cần một trong ba công cụ trên không? **Thường không.** Ưu tiên API Java trực tiếp và cấu trúc đồng thời cấp cao khi cần.

## 12. Kết luận và tài liệu tham khảo

Hãy xác định **bài toán đang là gì** trước khi chọn công cụ: cần biết *phương thức nào* và annotation gì thì dùng `Method`; cần *gọi hành vi đã chọn* với một chữ ký rõ, có thể thích nghi thì dùng `MethodHandle`; cần *truy cập/cập nhật biến* với chế độ bộ nhớ hoặc CAS thì dùng `VarHandle`. Luôn kiểm tra quyền truy cập, kiểu lời gọi, xử lý ngoại lệ và tính đúng đắn khi có nhiều thread. Khi code tĩnh đủ rõ, lời gọi Java trực tiếp hoặc API đồng thời cấp cao vẫn là lựa chọn nên cân nhắc đầu tiên.

Tài liệu chính thức:

- [Java SE 21 — `java.lang.reflect.Method`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Method.html)
- [Java SE 21 — `java.lang.invoke.MethodHandle`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/invoke/MethodHandle.html)
- [Java SE 21 — `java.lang.invoke.MethodHandles.Lookup`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/invoke/MethodHandles.Lookup.html)
- [Java SE 21 — `java.lang.invoke.VarHandle`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/invoke/VarHandle.html)
- [Java SE 21 — `java.util.concurrent.atomic`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/atomic/package-summary.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="method-handles-var-handles-011">Không mặc định một cơ chế luôn nhanh hơn cơ chế còn lại</a>

<details>
<summary>Xem chi tiết</summary>

# Không mặc định một cơ chế luôn nhanh hơn cơ chế còn lại: Reflection, MethodHandle và VarHandle

## 1. Kết luận cần hiểu đúng

Trong Java, `MethodHandle` và `VarHandle` cung cấp cách truy cập thành viên/biến với chữ ký được JVM xử lý trực tiếp, nhưng **không có quy tắc chung** rằng chúng luôn nhanh hơn `Method.invoke` hoặc `Field.get/set` của Core Reflection. Và cũng không thể nói `MethodHandle` luôn nhanh hơn `VarHandle`: chúng **không giải quyết chính xác cùng một bài toán**.

Tốc độ thực tế phụ thuộc đồng thời vào:
- **Thao tác**: gọi method, đọc field, ghi field, truy cập `volatile`, hay `compareAndSet`.
- **Độ ổn định của kiểu và call site**: đối tượng `MethodHandle`, `VarHandle`, `Method`, `Field` có được cache, có ổn định qua mỗi lần gọi, có thể được JIT nhận biết và tối ưu không.
- **Khả năng inline và profile runtime**: call site đơn hình (monomorphic) hay đa hình, lớp receiver và target thay đổi đến đâu.
- **Chi phí chuyển đổi**: boxing/unboxing, mảng `Object[]` cho arguments, chuyển đổi kiểu của `invoke` so với `invokeExact`, adapters như `asType`, `bindTo` và `filterArguments`.
- **Ràng buộc bộ nhớ**: plain/opaque/acquire/release/volatile có semantics và chi phí khác nhau; so sánh khác semantics sẽ sai mục tiêu.
- **Phiên bản JDK/JVM, kiến trúc CPU, tier biên dịch, GC, cấu hình và tải thực tế**.

Một benchmark tốt phải xác định **“nhanh hơn khi làm việc gì, trên runtime nào, với semantics gì?”** trước khi đưa ra quyết định. Ưu tiên tính đúng, quyền truy cập và khả năng bảo trì; dùng dữ liệu hiệu năng đo trên đường chạy quan trọng để cân nhắc đổi cơ chế.

## 2. Chúng làm gì và không thay thế nhau ra sao?

| Cơ chế | Nhiệm vụ phù hợp | Cách gọi đặc trưng | Điều cần lưu ý khi so hiệu năng |
| --- | --- | --- | --- |
| Gọi Java thông thường | Đã biết kiểu/method ở thời điểm biên dịch | `obj.work(arg)` | Mốc so sánh có ích; JIT có thể inline và loại bỏ phần việc không quan sát được. |
| `java.lang.reflect.Method` | Tìm/gọi method linh hoạt từ metadata runtime | `method.invoke(obj, args...)` | Trả về `Object`; có thể boxing/unboxing, xử lý argument và kiểm tra truy cập. |
| `MethodHandle` | Tham chiếu có kiểu tới method/constructor/field, kết hợp thành chuỗi xử lý động | `mh.invokeExact(...)`, `mh.invoke(...)` | Chi phí và khả năng tối ưu phụ thuộc chữ ký, adapter, call site, handle có ổn định hay không. |
| `java.lang.reflect.Field` | Đọc/ghi field qua metadata Reflection | `field.getInt(obj)`, `field.setInt(obj, v)` | Các API primitive như `getInt` giúp tránh boxing do `get` trả `Object`. |
| `VarHandle` | Truy cập biến/field/array với các access mode và memory ordering rõ ràng | `vh.get(obj)`, `vh.set(obj,v)`, `vh.compareAndSet(obj,a,b)` | Phải chọn mode tương đương khi so; `getVolatile` không đồng nghĩa `get`. |

`MethodHandle` có thể đại diện cho **getter/setter** của field, còn `VarHandle` có thể chuyển một access mode thành `MethodHandle` qua `toMethodHandle(...)`. Như vậy có phần giao nhau nhưng `VarHandle` được thiết kế để biểu diễn **nhiều chế độ truy cập biến** và atomic operations, không phải một cơ chế gọi arbitrary method.

Không nên so thời gian của `MethodHandle.invokeExact` để gọi phép cộng với `VarHandle.compareAndSet` để cập nhật biến: hai thao tác tạo ra kết quả và bảo đảm khác nhau. Nếu cần so field read, hãy so `obj.field`, `Field.getInt`, `MethodHandle` getter và `VarHandle.get` cùng một field, trên điều kiện tương đương.

## 3. Điều gì quyết định hiệu năng ở call site?

### 3.1. Lookup khác invocation

`Class.getMethod`, `MethodHandles.Lookup.findVirtual`, `findGetter` và `findVarHandle` dùng để **tìm và kiểm tra khả năng truy cập**. Chi phí lookup không phải chi phí gọi method/đọc field nhiều lần.

- Nếu ứng dụng chỉ thực hiện vài lần truy cập, chi phí khởi tạo/cấu hình một handle có thể quan trọng hơn thời gian gọi.
- Nếu chạy hàng triệu lần, cache `Method`/`Field`/`MethodHandle`/`VarHandle` bên ngoài vòng lặp thường giúp đánh giá đúng hot path.
- Đừng chỉ đặt lookup *bên trong* một phiên bản benchmark còn phiên bản khác đã cache sẵn. Muốn so tổng chi phí khởi tạo + sử dụng thì phải đo **cùng vòng đời** cho tất cả.

### 3.2. JIT optimization và hằng hóa handle

Trong điều kiện JIT nhận diện được target/handle ổn định (ví dụ một trường `static final` hoặc đường truyền dữ liệu đủ rõ), JIT có **cơ hội** inline, chuyên biệt hóa hoặc loại một số bước trung gian. Khi thay target liên tục, lấy từ phần tử mảng hoặc một call site có nhiều loại target, tối ưu hóa có thể khác.

Không được suy luận “`static final` bảo đảm inline” hay “không `final` thì chắc chắn chậm”: đây là kết quả phụ thuộc profile và triển khai JIT. `final` là tín hiệu hữu ích để JVM thấy tham chiếu ổn định, không phải lời hứa về thứ hạng hiệu năng.

### 3.3. `invokeExact`, `invoke` và adapters

- `MethodHandle.invokeExact` đòi hỏi **chữ ký tại call site khớp chính xác** với `MethodHandle.type()`, gồm kiểu tham số **và kiểu trả về**. Sai kiểu có thể gây `WrongMethodTypeException`.
- `MethodHandle.invoke` cho phép một số chuyển đổi kiểu như `asType`. JVM có thể tối ưu các chuyển đổi nếu đủ thông tin, nhưng adaptation cũng có thể tạo thêm công việc.
- `invokeWithArguments` thuận tiện với tham số động dạng `Object[]`/`List<?>`, không đại diện cho cùng cách gọi tối ưu như `invokeExact` cố định chữ ký.
- `VarHandle` có các access mode dạng signature-polymorphic; `withInvokeExactBehavior()` yêu cầu đối chiếu chữ ký chính xác, còn dạng mặc định có thể cho phép điều chỉnh tương đương `invoke` theo quy tắc API.

Khi benchmark, ghi chính xác variant được dùng và kiểu tại call site. Đổi `invokeExact` sang `invokeWithArguments` rồi quy chụp toàn bộ `MethodHandle` là kết luận không hợp lệ.

### 3.4. Core Reflection thay đổi qua các bản Java

Từ **JDK 18**, [JEP 416](https://openjdk.org/jeps/416) tái triển khai phần lõi Core Reflection (`Method`, `Constructor`, `Field`) dựa trên method handles. Điều này **không** có nghĩa `Method.invoke` và `MethodHandle.invokeExact` là cùng một public API hay có overhead giống hệt nhau. Nhưng nó cho thấy cách tổ chức runtime thay đổi: những so sánh dựa trên cơ chế Reflection cũ không tự áp dụng cho JDK mới.

JEP 416 cũng nêu benchmark cho thấy hiệu năng thay đổi **theo mẫu giữ tham chiếu** (hằng, biến, truy cập mảng...) và theo loại thao tác. Những con số trong một đề xuất JDK chỉ mô tả môi trường và workload của phép đo đó; không phải một tỷ lệ tăng tốc cố định cho ứng dụng khác. **Không suy diễn từ benchmark của một phiên bản sang mọi JDK.**

## 4. Ví dụ Java 17+ chạy được: giống kết quả, chưa nói ai nhanh hơn

Lưu toàn bộ chương trình sau thành **`AccessChoicesDemo.java`**. Nó dùng cùng một phương thức `add(int)` và cùng một field `value` để kiểm chứng kết quả tương đương trước khi bàn tới hiệu năng.

```java
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class AccessChoicesDemo {
    public static final class Counter {
        public int value = 2;

        public int add(int delta) {
            return value + delta;
        }
    }

    public static void main(String[] args) throws Throwable {
        Counter counter = new Counter();
        Method reflectedAdd = Counter.class.getMethod("add", int.class);
        Field reflectedValue = Counter.class.getField("value");

        MethodHandles.Lookup lookup = MethodHandles.lookup();
        MethodHandle addHandle = lookup.findVirtual(
                Counter.class, "add", MethodType.methodType(int.class, int.class));
        MethodHandle getterHandle = lookup.findGetter(
                Counter.class, "value", int.class);
        VarHandle valueHandle = lookup.findVarHandle(
                Counter.class, "value", int.class);

        System.out.println("Direct call: " + counter.add(3));
        System.out.println("Method.invoke: " + reflectedAdd.invoke(counter, 3));
        System.out.println("MethodHandle.invokeExact: "
                + (int) addHandle.invokeExact(counter, 3));

        System.out.println("Direct field read: " + counter.value);
        System.out.println("Field.getInt: " + reflectedValue.getInt(counter));
        System.out.println("MethodHandle field getter: "
                + (int) getterHandle.invokeExact(counter));
        System.out.println("VarHandle.get: " + (int) valueHandle.get(counter));

        System.out.println("VarHandle.compareAndSet: "
                + valueHandle.compareAndSet(counter, 2, 9));
        System.out.println("Field after CAS: " + reflectedValue.getInt(counter));
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 AccessChoicesDemo.java
java AccessChoicesDemo
```

Kết quả đã được kiểm tra thực tế bằng JDK 24 với mã biên dịch ở mức Java 17:

```text
Direct call: 5
Method.invoke: 5
MethodHandle.invokeExact: 5
Direct field read: 2
Field.getInt: 2
MethodHandle field getter: 2
VarHandle.get: 2
VarHandle.compareAndSet: true
Field after CAS: 9
```

Phân tích:

1. Ba cách gọi `add(3)` đều trả `5`. `Method.invoke` trả `Object` nên khi nối chuỗi sẽ thấy giá trị từ một `Integer` đã được boxing; `invokeExact` ở đây trả nguyên thủy `int` đúng `MethodType`.
2. Bốn cách đọc field đọc cùng giá trị `2`. `Field.getInt` và `MethodHandle` getter là lựa chọn trực tiếp để so **plain read** với `VarHandle.get`. `MethodHandle getter` có chữ ký tương đương `(Counter)int`.
3. `compareAndSet` thực hiện một thao tác **nguyên tử** kiểm tra `2` rồi đổi sang `9`; đây là tính năng khác với plain field read và không nên đưa chung vào phép đo “chi phí đọc”.
4. Tất cả lookup được thực hiện trước phần gọi; **chương trình là kiểm tra correctness chứ không phải benchmark tốc độ**. Thêm `System.nanoTime()` quanh từng dòng này sẽ không cho ra bảng xếp hạng có giá trị.

Ví dụ dùng thành viên `public` để không cần `setAccessible(true)`. Với private member, quyền truy cập của `MethodHandles.Lookup` và Reflection phải được cấu hình theo module/package tương ứng; đừng đem một bản thất bại vì thiếu quyền so với một bản đã mở quyền.

## 5. Memory semantics: so cùng công việc rồi mới so thời gian

`VarHandle` có các kiểu thao tác phân biệt rõ:
- `get` / `set`: truy cập **plain** (ngữ nghĩa đọc/ghi thông thường, không tự thêm cam kết đồng bộ của `volatile`).
- `getOpaque` / `setOpaque`: opaque access; cung cấp ràng buộc thứ tự yếu hơn acquire/release/volatile.
- `getAcquire` / `setRelease`: acquire/release ordering, phù hợp một số giao thức publication/consumption.
- `getVolatile` / `setVolatile`: semantics tương tự đọc/ghi `volatile`.
- `compareAndSet`, `compareAndExchange`, `getAndAdd`...: các primitive nguyên tử hoặc read-modify-write khi access mode và kiểu biến hỗ trợ.

`Field.getInt` trên field thông thường và `VarHandle.get` cùng field là cặp hợp lý để bắt đầu so **đọc plain**. Trên field khai báo `volatile`, hãy phân tích lại semantics rồi mới chọn API/mode; đừng so plain read với volatile read và cho rằng phần nhanh hơn vẫn giải quyết được điều kiện đồng bộ. `VarHandle` cũng không tự biến một thuật toán có data race thành an toàn: phải chọn memory ordering/atomicity phù hợp và thiết kế protocol chính xác.

Không phải mọi access mode đều có trên mọi loại biến (ví dụ các thao tác ghi/atomic trên field `final` có hạn chế). Kiểm tra `isAccessModeSupported` và hợp đồng `VarHandle` khi cần thao tác chuyên biệt.

## 6. Đo đúng: JMH, warmup, forks và kiểm soát sai lệch

### 6.1. Vì sao một vòng lặp với `nanoTime` chưa đủ?

Nếu viết:

```java
long start = System.nanoTime();
for (int i = 0; i < 1_000_000; i++) {
    handle.invokeExact(counter, 3); // chú ý kiểu trả về!
}
long elapsed = System.nanoTime() - start;
```

đoạn minh họa này **không phải một chương trình benchmark đúng**: lời gọi `invokeExact` dưới dạng statement có kiểu trả về `void` tại call site trong khi handle ví dụ trả `int`, nên có thể ném `WrongMethodTypeException`. Ngay cả khi ép kiểu/tiêu thụ kết quả đúng, phép đo vẫn có vấn đề như JIT chưa nóng, code bị tối ưu bỏ, phép đo bị nhiễu từ hệ điều hành/GC hoặc thuật toán đang thực hiện các tác vụ không tương đương.

Sửa tối thiểu cho mặt chữ ký là:

```java
int result = (int) addHandle.invokeExact(counter, 3);
```

nhưng một lần đo bằng `nanoTime` vẫn **không** thay thế benchmark có thiết kế nghiêm túc.

### 6.2. Cách tổ chức benchmark với JMH

[JMH (Java Microbenchmark Harness)](https://github.com/openjdk/jmh) là bộ công cụ chuyên dụng của OpenJDK cho microbenchmark JVM. Khi cần quyết định tối ưu trên hot path:

1. **Định nghĩa cùng semantics** và các cặp so sánh: (a) gọi method trực tiếp / `Method.invoke` / `MethodHandle.invokeExact`; (b) field direct read / `Field.getInt` / getter `MethodHandle` / `VarHandle.get`. Đo riêng CAS/volatile operations.
2. **Chuyển lookup ra pha setup** khi đo invocation ổn định, và tạo benchmark **khác** nếu nghiệp vụ cần đo cả setup + sử dụng một lần.
3. **Thiết lập warmup, nhiều measurement iterations và nhiều forks**. Ghi JDK vendor/version, JVM flags, CPU/OS, môi trường tải, GC và JMH version.
4. **Tiêu thụ kết quả** (trả về từ `@Benchmark` hoặc dùng `Blackhole`), cung cấp đầu vào thay đổi đúng nhu cầu và tránh dead-code elimination/constant folding làm workload biến mất. Không in console trong phương thức benchmark.
5. **Đảm bảo cùng mức đa hình**: một target cố định không thể đem so trực tiếp với target chọn ngẫu nhiên trong từng iteration.
6. **Xem phân phối kết quả và độ bất định**, độ ổn định giữa forks và profiling. Nếu chênh lệch nhỏ so với nhiễu hoặc không làm thay đổi bottleneck ứng dụng, không có cơ sở để thay API.

Mẫu *phác thảo cấu trúc*, cần đặt trong class benchmark có các field `MethodHandle addHandle`, `Method reflectedAdd`, `Counter counter` và phụ thuộc JMH; **không phải file Java độc lập**:

```java
@Benchmark
public int direct() {
    return counter.add(3);
}

@Benchmark
public int viaReflection() throws ReflectiveOperationException {
    return (Integer) reflectedAdd.invoke(counter, 3);
}

@Benchmark
public int viaHandle() throws Throwable {
    return (int) addHandle.invokeExact(counter, 3);
}
```

Trước khi chạy mẫu này cần khai báo đầy đủ `@State`, setup handle/method/receiver, import `org.openjdk.jmh.annotations.Benchmark` và cấu hình JMH cùng annotation processor trong công cụ build. Hãy biến `Counter` thành workload có trạng thái/đầu vào phù hợp, để phép tính thật không bị JIT chứng minh là hằng số. **Không tự điền “ns/op dự kiến”**: con số phải xuất phát từ phép đo cụ thể và có thông tin hệ thống đi kèm.

### 6.3. Bảng kiểm soát phép đo

| Câu hỏi | Nếu bỏ qua thì sao? |
| --- | --- |
| Lookup/cache giống nhau? | Đang đo thêm setup bên này nhưng không đo bên kia. |
| Chữ ký và chuyển đổi tương đương? | Đang đo overhead boxing/adapters khác nhau mà không nói rõ. |
| Thao tác field cùng memory ordering? | Đang so hai semantics khác nhau. |
| Input, target, số kiểu receiver giống nhau? | Profile JIT không thể so được. |
| Warmup/forks và trạng thái hệ thống hợp lý? | Số liệu ngẫu nhiên, nhiễu hoặc chủ yếu đo JIT startup. |
| Return value được tiêu thụ và có side effect thật? | JIT có thể loại hoàn toàn công việc. |
| Chạy trên JDK/CPU tương ứng sản phẩm? | Kết quả thí nghiệm không đại diện deployment. |
| Hiệu năng end-to-end có cải thiện không? | Tối ưu microbenchmark có thể không đem lợi ích thực tế. |

## 7. Tình huống chọn API dựa trên yêu cầu thực tế

**Framework discovery/plugin/serialization:** Nếu phải khám phá lớp/phương thức theo tên ở runtime và số lượt gọi ít, Core Reflection thường là lựa chọn đơn giản và dễ diễn đạt. Khi một thành viên được gọi cực nhiều lần với chữ ký ổn định, có thể thử cache `MethodHandle` rồi **đo** lợi ích; kết quả còn phụ thuộc JDK và call site.

**Cơ chế gọi hàm động với phép biến đổi chữ ký:** `MethodHandle` là lựa chọn có API thích hợp cho composition (`asType`, `bindTo`, `filterArguments`...), dù mỗi adapter có thể ảnh hưởng tối ưu hóa. Nếu phải tạo adapter một lần và dùng lặp lại, hãy đo mô hình đó thay vì xây lại chuỗi adapter mỗi invocation.

**Truy cập field với concurrency protocol:** Chọn `VarHandle` khi cần `getAcquire`/`setRelease`/`compareAndSet` hoặc các memory access mode rõ ràng, bởi vì **đúng semantics** là điều kiện bắt buộc. Sau đó tối ưu trong phạm vi các cách cài đặt cùng bảo đảm. Không thay một CAS nguyên tử bằng plain write chỉ để giảm thời gian benchmark.

**Mã thông thường biết kiểu tại compile time:** Gọi trực tiếp hoặc dùng cấu trúc Java chuẩn thường dễ hiểu và là mốc so sánh đầu tiên. Không có lý do mặc định chuyển mọi getter thành `MethodHandle`/`VarHandle` với hy vọng tăng tốc.

**Sau khi nâng JDK:** Đo lại nếu đây là điểm nóng quan trọng. JIT, implementation của Reflection, memory barriers và optimization thay đổi giữa các bản runtime. “Năm trước framework A nhanh hơn B” không phải hợp đồng hiệu năng.

## 8. Những kết luận sai phổ biến

| Nhận định sai | Diễn giải đúng |
| --- | --- |
| “`MethodHandle` luôn nhanh hơn Reflection.” | Khả năng JIT tối ưu và chi phí phụ thuộc loại thao tác, dạng handle, call site và phiên bản JDK. |
| “`VarHandle` luôn nhanh hơn `MethodHandle`.” | Chúng khác vai trò; chỉ so khi thực hiện đúng cùng field operation và cùng semantics. |
| “`invokeExact` luôn nhanh hơn `invoke`.” | `invokeExact` tránh nhu cầu adaptation về mặt semantics, nhưng runtime có thể tối ưu `invoke` trong trường hợp thích hợp; phải đo workload cụ thể. |
| “Đo `Field.get` rồi so `VarHandle.get` là hoàn toàn công bằng.” | `Field.get` có thể boxing primitive; cân nhắc `Field.getInt` nếu mục tiêu là plain int read. |
| “Một lần `nanoTime` đủ để kết luận.” | Cần warmup, forks, kiểm soát nhiễu và chống tối ưu mất công việc. |
| “JDK 18 dùng MethodHandle triển khai Reflection nên hai API có tốc độ bằng nhau.” | Chung một phần cơ chế bên trong không xóa khác biệt API, caller shape và overhead. |
| “CAS chậm hơn plain set nên dùng plain set cho đồng bộ.” | CAS bảo đảm atomicity khác plain store; phải giữ đúng yêu cầu đồng bộ. |
| “Kết quả benchmark vượt trội nghĩa là ứng dụng chắc chắn nhanh hơn.” | Bottleneck có thể nằm ở I/O, allocation, locks, GC hoặc thuật toán; cần đo end-to-end. |

## 9. Câu hỏi tự kiểm tra

1. Khi so thời gian `Method.invoke` và `MethodHandle.invokeExact` gọi cùng method, tại sao phải đưa bước tìm `Method`/`MethodHandle` ra ngoài benchmark hoặc đo riêng?
2. Khi đọc `int field`, vì sao `Field.getInt` là đối chứng hữu ích hơn `Field.get` đối với `VarHandle.get`?
3. `MethodHandle.invokeExact` trả `int`. Việc gọi như một statement `mh.invokeExact(...)` không ép kiểu trả về có thể tạo vấn đề gì?
4. Nếu biến chỉ có thể được cập nhật an toàn bằng `compareAndSet`, có hợp lệ dùng `VarHandle.set` làm đối chứng trực tiếp về tốc độ không?
5. Vì sao `static final MethodHandle` có thể giúp JIT nhưng không bảo đảm nhanh hơn một `Method` được cache?
6. Sau khi nâng từ JDK 17 lên JDK mới, vì sao các con số microbenchmark cũ phải được đánh giá lại?

**Nguyên tắc áp dụng:** Chọn API theo thao tác và semantics; thử một bản triển khai đúng; cache hợp lý; dùng benchmark tái lập trên runtime thực và kiểm tra ảnh hưởng end-to-end. Nếu chưa có số liệu đáng tin, **không khẳng định bất kỳ cơ chế nào luôn thắng**.

## 10. Nguồn tham khảo chính thức

- [Java SE 17 — `MethodHandle`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandle.html): `invokeExact`, `invoke`, adaptation và quy tắc kiểm tra kiểu.
- [Java SE 17 — `VarHandle`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/VarHandle.html): các access mode, memory ordering và kiểu lời gọi.
- [Java SE 17 — `Method`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html): `invoke` và hợp đồng Core Reflection.
- [JEP 416 — Reimplement Core Reflection with Method Handles](https://openjdk.org/jeps/416): thay đổi từ JDK 18 cùng kết quả đo theo từng pattern.
- [OpenJDK JMH](https://github.com/openjdk/jmh): harness cho JVM microbenchmark, cách dựng benchmark và công cụ chạy.


</details>

- [Quay lại đầu trang](#back-to-top)
