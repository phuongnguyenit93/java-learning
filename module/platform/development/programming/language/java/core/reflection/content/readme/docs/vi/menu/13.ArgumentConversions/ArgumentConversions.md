<a id="back-to-top"></a>

# Đối số và chuyển đổi

## Menu
- [Phân biệt quy tắc tra cứu với quy tắc chuyển đổi khi gọi](#argument-conversions-001)
- [Unboxing và widening primitive trong invoke hoặc newInstance](#argument-conversions-002)
- [Vì sao narrowing hoặc đổi String thành số không được tự thực hiện](#argument-conversions-003)
- [Chuyển tham chiếu theo quan hệ assignability](#argument-conversions-004)
- [Số lượng đối số và IllegalArgumentException](#argument-conversions-005)
- [Receiver null khi gọi phương thức instance](#argument-conversions-006)
- [Varargs của phương thức đích được biểu diễn bằng một tham số mảng](#argument-conversions-007)
- [Varargs Object... của API reflection và cách truyền một mảng như một đối số](#argument-conversions-008)
- [Phân biệt không có đối số với một đối số null](#argument-conversions-009)
- [Chuyển đổi giá trị trả về và ClassCastException ở mã gọi](#argument-conversions-010)
- [Mảng primitive không thay thế mảng wrapper](#argument-conversions-011)

## <a id="argument-conversions-001">Phân biệt quy tắc tra cứu với quy tắc chuyển đổi khi gọi</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt quy tắc tra cứu phương thức với quy tắc chuyển đổi đối số khi gọi bằng Reflection

Trong Java Reflection, **tìm một phương thức** và **gọi phương thức đã tìm thấy** là hai giai đoạn có quy tắc kiểu dữ liệu khác nhau:

1. **Tra cứu:** `Class.getMethod(name, parameterTypes...)` hoặc `Class.getDeclaredMethod(name, parameterTypes...)` xác định một `Method` theo **tên và danh sách kiểu tham số được khai báo**, trong phạm vi public/declared phù hợp. Kiểu tham số truyền cho hàm tra cứu là các đối tượng `Class<?>`. Java không tự tìm overload bằng phép chuyển đổi `byte -> long`, boxing `int -> Integer`, hoặc quan hệ kế thừa `Integer -> Number`.
2. **Gọi:** Khi đã có đối tượng `Method`, `Method.invoke(receiver, args...)` thực hiện kiểm tra quyền truy cập, receiver, số đối số và **khả năng chuyển đổi đối số thực tế** sang các kiểu formal parameter. Trong những điều kiện phù hợp, Reflection cho phép **unboxing** giá trị wrapper sang primitive, **widening primitive conversion**, và gán một đối tượng reference cho kiểu tham chiếu tương thích. Nó không cho phép narrowing primitive conversion.

Sự khác biệt này giải thích vì sao `getMethod("work", byte.class)` không tìm được `work(long)`, trong khi `getMethod("work", long.class).invoke(obj, Byte.valueOf((byte) 7))` có thể chạy thành công.

## 1. Giai đoạn tra cứu: chọn phương thức theo khai báo

Ví dụ một lớp có hai overload:

```java
class Service {
    public void process(int value) {}
    public void process(long value) {}
}
```

Đây là đoạn mã minh họa định nghĩa lớp. Với `Service.class`:

```java
Method intMethod = Service.class.getMethod("process", int.class);
Method longMethod = Service.class.getMethod("process", long.class);
```

Hai lời gọi yêu cầu chính xác `process(int)` và `process(long)`. Nếu thay `int.class` hoặc `long.class` bằng `byte.class`, `getMethod` không chọn “ứng viên gần nhất” để tiện cho caller. Nếu không có `process(byte)`, kết quả là **`NoSuchMethodException`**.

**Kiểu trong lệnh tra cứu là kiểu formal parameter**, không phải một đối số runtime đang được chuẩn bị truyền vào phương thức. Vì vậy:

- `int.class` khác `Integer.class`: không có boxing/unboxing để tìm phương thức.
- `long.class` khác `int.class`: không có widening để tìm phương thức.
- `Number.class` khác `Integer.class`: tra cứu không tự thay kiểu trong danh sách tham số dựa trên kế thừa.
- `String[].class` khác `String.class`: tham số varargs khai báo `String...` có kiểu mảng `String[]` trong metadata.
- Thứ tự và **số lượng** tham số đều cần đúng: `calculate(int, long)` khác `calculate(long, int)`.

### getMethod và getDeclaredMethod khác nhau về phạm vi

| API | Phương thức được tra cứu |
| --- | --- |
| `getMethod(name, parameterTypes...)` | Các phương thức `public` có thể nhìn thấy qua kiểu đang xét, bao gồm phương thức kế thừa phù hợp |
| `getDeclaredMethod(name, parameterTypes...)` | Phương thức được khai báo trực tiếp tại chính class/interface ấy, kể cả `private` hoặc package-private; không duyệt lên superclass |

Cả hai vẫn dùng tên và danh sách kiểu tham số khai báo để tìm method, không thực hiện overload resolution như khi compiler xử lý một lời gọi Java thông thường. `getDeclaredMethod` có thể **lấy metadata** của private method, nhưng lấy được metadata không tự cấp quyền `invoke`; quyền truy cập được xét sau.

**Không truyền kiểu trả về vào `getMethod` hay `getDeclaredMethod`.** Kết quả `getReturnType()` là metadata đọc được *sau khi tra cứu*. Với một số class file có bridge methods hoặc các method có cùng tên/danh sách tham số mà khác kiểu trả về, API có quy tắc lựa chọn riêng theo độ cụ thể của kiểu trả về; do đó công cụ phân tích bytecode nâng cao còn cần kiểm tra `Method.isBridge()` và `isSynthetic()`. Điều này không biến tra cứu Reflection thành cơ chế tự ép kiểu đối số.

### Đừng nhầm với overload resolution lúc biên dịch

Với mã Java thông thường:

```java
byte b = 7;
new Service().process(b);
```

Đây là đoạn mã trong ngữ cảnh có `Service` ở trên. **Compiler** xét các overload có thể áp dụng theo Java Language Specification; trường hợp `process(int)` và `process(long)` ở đây, nó chọn `process(int)` vì `int` là ứng viên cụ thể hơn cho `byte`.

Reflection không được truyền một biểu thức `b` vào `getMethod` để compiler tự chọn. Người dùng Reflection **phải quyết định method cần lấy** bằng `Class<?>...` trước, rồi `invoke` trên method đã được chọn.

## 2. Giai đoạn gọi: Method.invoke thực hiện conversion cho đối số

Cú pháp chính:

```java
Object result = method.invoke(receiver, arg1, arg2);
```

Đây là đoạn mã minh họa, giả định đã có `Method method`, `receiver`, `arg1`, `arg2` hợp lệ; code cần xử lý các ngoại lệ Reflection.

`Method.invoke(Object obj, Object... args)` có hai loại tham số ở tầng Reflection:

- `obj` là **receiver** của instance method; đối với static method, receiver bị bỏ qua nên thường truyền `null`.
- `args` là các giá trị dùng cho các formal parameter của method. Số lượng giá trị phải phù hợp, và mỗi giá trị phải chuyển đổi được sang kiểu formal parameter tương ứng.

Vì `invoke` nhận `Object...`, khi truyền giá trị primitive trực tiếp trong mã Java gọi `invoke`, **compiler sẽ boxing giá trị đó** để đưa vào mảng `Object[]`. Sau đó `Method.invoke` xử lý đối tượng wrapper và áp dụng các conversion được cho phép để chuyển sang kiểu tham số primitive của phương thức đích.

Ví dụ, nếu `method` đại diện cho `process(long)`, các cách gọi sau đều có thể hợp lệ về kiểu:

```java
method.invoke(receiver, Byte.valueOf((byte) 7));
method.invoke(receiver, Integer.valueOf(8));
method.invoke(receiver, Long.valueOf(9L));
```

Chúng có thể dùng giá trị wrapper `Byte`, `Integer`, `Long` vì việc unboxing và, nếu cần, widening primitive đưa chúng tới `long`. Nhưng `Double` hoặc `Float` không thể được truyền tới tham số `long` qua narrowing.

Điều quan trọng là **`invoke` không tìm lại overload khác** khi nhận đối số. Nếu `Method` đã chỉ tới `process(long)`, chính phương thức đó được gọi (có xét dynamic dispatch đối với override), dù trong cùng lớp còn `process(int)` phù hợp hơn với một kiểu giá trị nào đó.

## 3. Ví dụ Java 17+ đầy đủ: chứng minh tra cứu và gọi khác nhau

Tạo tệp `ReflectionLookupDemo.java`:

```java
import java.lang.reflect.Method;

public class ReflectionLookupDemo {
    public static class Service {
        public String describe(int v) {
            return "int:" + v;
        }

        public String describe(long v) {
            return "long:" + v;
        }

        public String accept(Number value) {
            return "Number:" + value;
        }

        public String join(String... values) {
            return String.join("/", values);
        }

        public int twice(int value) {
            return value * 2;
        }
    }

    public static void main(String[] args) throws Exception {
        Service s = new Service();
        Class<?> type = Service.class;

        Method m = type.getMethod("describe", long.class);
        System.out.println(
                "lookup(long)=" + m.getParameterTypes()[0].getSimpleName()
        );

        try {
            type.getMethod("describe", byte.class);
        } catch (NoSuchMethodException e) {
            System.out.println("lookup(byte)=not found");
        }

        System.out.println(
                "invoke Byte->long=" + m.invoke(s, Byte.valueOf((byte) 7))
        );
        System.out.println(
                "invoke Integer->long=" + m.invoke(s, Integer.valueOf(8))
        );

        try {
            m.invoke(s, Double.valueOf(9.0));
        } catch (IllegalArgumentException e) {
            System.out.println("invoke Double->long=rejected");
        }

        Method n = type.getMethod("accept", Number.class);
        System.out.println(
                "invoke Integer->Number=" + n.invoke(s, Integer.valueOf(5))
        );

        try {
            type.getMethod("accept", Integer.class);
        } catch (NoSuchMethodException e) {
            System.out.println("lookup accept(Integer)=not found");
        }

        Method v = type.getMethod("join", String[].class);
        System.out.println(
                "join(array)=" + v.invoke(s, (Object) new String[] {"a", "b"})
        );

        try {
            v.invoke(s, "a", "b");
        } catch (IllegalArgumentException e) {
            System.out.println("join(elements)=rejected");
        }

        Method twice = type.getMethod("twice", int.class);
        Object result = twice.invoke(s, Integer.valueOf(11));
        System.out.println(
                "return=" + result
                + ",type=" + result.getClass().getSimpleName()
        );
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac ReflectionLookupDemo.java
java ReflectionLookupDemo
```

Đầu ra:

```text
lookup(long)=long
lookup(byte)=not found
invoke Byte->long=long:7
invoke Integer->long=long:8
invoke Double->long=rejected
invoke Integer->Number=Number:5
lookup accept(Integer)=not found
join(array)=a/b
join(elements)=rejected
return=22,type=Integer
```

Phân tích:

1. `getMethod("describe", long.class)` tìm đúng overload `describe(long)`. `getMethod("describe", byte.class)` **không** tự áp dụng `byte -> int` hay `byte -> long`; cả hai vẫn là kiểu tham số khác `byte`.
2. Sau khi đã lấy đúng `describe(long)`, `invoke` với `Byte` hoặc `Integer` thành công nhờ unboxing cộng widening; method được chọn vẫn là overload nhận **`long`**, như chuỗi `long:7` và `long:8` chứng minh.
3. `Double` không phù hợp với formal parameter `long`; `invoke` ném `IllegalArgumentException`, chương trình bắt và in thông báo rõ ràng.
4. `getMethod("accept", Number.class)` tìm chính xác method khai báo `Number`, nhưng `invoke` nhận `Integer` vì đó là subtype phù hợp. Tra cứu với `Integer.class` lại không chọn thay `accept(Number)`.
5. Tham số `String... values` của method `join` được phản chiếu dưới dạng **`String[]`**. `invoke(s, (Object) new String[] {"a","b"})` đưa một đối số mảng vào method và trả `a/b`; `invoke(s, "a", "b")` đưa hai đối số riêng nên bị từ chối vì method chỉ khai báo **một** formal parameter kiểu `String[]`.
6. `twice(int)` trả kiểu primitive `int`, nhưng `Method.invoke` trả về `Object`, vì vậy giá trị trả về được **boxing** thành `Integer`. Đây là **quy tắc kết quả của invoke**, khác với việc chọn method và chuyển đổi đối số.

Ví dụ chủ động bắt hai loại lỗi khác nhau: `NoSuchMethodException` phát sinh khi **tra cứu không khớp**, còn `IllegalArgumentException` phát sinh khi đã có `Method` nhưng **đối số gọi không phù hợp**.

## 4. Quy trình chọn method và gọi đúng

Khi tạo công cụ gọi method động, hãy tách hẳn quyết định *chọn phương thức* khỏi bước *thực hiện phép gọi*:

1. **Xác định phạm vi:** tìm chỉ method declared hay cả public methods được kế thừa; kiểm tra ngữ cảnh module/quyền truy cập.
2. **Chọn method đích:** dựa trên tên và danh sách **formal parameter types**. Nếu phải tự xử lý overload, hãy xác định chính sách ứng viên và mức ưu tiên conversion; đừng phụ thuộc thứ tự `getMethods()`.
3. **Chuẩn bị receiver:** `null` cho static method hoặc instance phù hợp cho instance method.
4. **Chuẩn bị đối số:** số lượng khớp formal parameters; primitive nhận wrapper có thể unbox/widen; reference nhận object assignment-compatible hoặc `null`; formal varargs của method đích nhận một mảng thích hợp.
5. **Gọi `invoke` và xử lý lỗi:** phân biệt tra cứu sai, không có quyền, conversion không hợp lệ, và exception từ method đích.
6. **Xử lý kết quả:** `invoke` trả `Object`; primitive result được boxing, `void` trả `null`. Nếu cần kiểu cụ thể, cast/kiểm tra trước khi dùng.

Khi implement một overload resolver riêng, cần cân nhắc `null` với nhiều reference overload, interface/superclass distances, primitive widening, boxing/unboxing, varargs và ambiguity. Java compiler có quy tắc chọn overload tương đối phức tạp; Reflection không tự “chạy lại compiler” với đối số runtime, nên việc viết một resolver thủ công chỉ từ `getName()` và `getParameterCount()` rất dễ chọn sai.

## 5. Câu hỏi tự kiểm tra

1. Lớp chỉ khai báo `work(long)`. `getMethod("work", int.class)` có tìm được không? **Không.** Tra cứu yêu cầu formal parameter `long.class` chính xác.
2. Sau khi có `Method` của `work(long)`, có thể `invoke` với `Integer.valueOf(3)` không? **Có.** `Integer` unbox thành `int`, rồi widening sang `long`.
3. Có thể `invoke` với `Double.valueOf(3.0)` cho `work(long)` không? **Không.** `double -> long` là narrowing.
4. Method khai báo `save(Number)`. Tra cứu bằng `Integer.class` có tìm được không? **Không.** Phải tìm `Number.class`; sau đó mới có thể invoke với `Integer`.
5. Method khai báo `String... names`. Kiểu để tra cứu là gì? **`String[].class`**.
6. Có thể dùng `invoke(receiver, "A", "B")` để tự động gói thành `String[]` cho method đích varargs không? **Không.** Cần một đối số mảng.
7. Nếu method đích ném `IllegalStateException`, `invoke` báo qua ngoại lệ nào? **`InvocationTargetException`**; kiểm tra `getCause()`.
8. Nếu method đích trả `int`, kiểu của `Method.invoke` là gì? **`Object`**, chứa `Integer` đã boxing. Method đích trả `void` thì `invoke` trả `null`.

## 6. Tài liệu tham khảo

- [Oracle Java SE 21 — `Class`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html): tài liệu `getMethod`, `getDeclaredMethod`, kiểu tham số, phạm vi và quy tắc tra cứu.
- [Oracle Java SE 21 — `Method`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Method.html): tài liệu `invoke`, khả năng chuyển đổi đối số, varargs, return boxing và ngoại lệ.
- [Java Language Specification SE 21 — §5.1 Conversions and Contexts](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html): widening/narrowing primitive, boxing/unboxing và những ngữ cảnh conversion.
- [Java Language Specification SE 21 — §15.12 Method Invocation Expressions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.12): cơ chế chọn overload trong **lời gọi Java thông thường**, giúp phân biệt với tra cứu Reflection.

**Ghi nhớ:** `Class.getMethod/getDeclaredMethod` tìm **khai báo khớp tên và kiểu tham số**, còn `Method.invoke` kiểm tra và **chuyển đổi các đối số thật** trong giới hạn được API cho phép. Trộn hai giai đoạn sẽ dẫn đến `NoSuchMethodException`, `IllegalArgumentException` hoặc chọn sai overload.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="argument-conversions-002">Unboxing và widening primitive trong invoke hoặc newInstance</a>

<details>
<summary>Xem chi tiết</summary>

# Unboxing và widening primitive khi gọi `Method.invoke` hoặc `Constructor.newInstance`

Khi gọi một phương thức hay constructor qua Java Reflection, tham số mà ứng dụng cung cấp đi qua một API nhận **`Object` hoặc `Object...`**, trong khi chữ ký gốc có thể đòi `int`, `long`, `double` hoặc các kiểu primitive khác. Java Reflection có quy tắc chuyển đổi tương ứng để đưa các đối số runtime vào đúng formal parameter của phương thức/constructor đích:

1. Nếu formal parameter là primitive, đối số phù hợp thường là một **wrapper object** như `Integer`, `Byte`, `Character`. Reflection có thể **unbox** wrapper thành primitive.
2. Sau khi unbox, Reflection có thể thực hiện **widening primitive conversion** — ví dụ `byte → long` hoặc `int → double`.
3. Reflection **không thực hiện narrowing primitive conversion** như `long → int` hoặc `double → long`, ngay cả khi giá trị thực tế trông có vẻ vừa phạm vi đích.
4. Khi số lượng đối số, receiver, quyền truy cập hoặc kiểu dữ liệu không phù hợp, Reflection phát sinh exception thích hợp; đặc biệt chuyển đổi đối số không hợp lệ thường gây `IllegalArgumentException` **trước khi body của method/constructor được thực thi**.

Hai API trọng tâm:

~~~java
Object result = method.invoke(receiver, arg1, arg2);
Object created = constructor.newInstance(arg1, arg2);
~~~

Đây là các biểu thức minh họa: `method` phải là `java.lang.reflect.Method`, `constructor` phải là `java.lang.reflect.Constructor<?>`; các reflection checked exceptions cần được xử lý. Bài học phân biệt rõ **kiểu formal khai báo**, **kiểu runtime của đối số**, **unboxing**, **widening**, và các lỗi dễ gặp khi dùng API varargs.

## 1. Bối cảnh: primitive, wrapper và API nhận `Object...`

Primitive không phải object, nhưng Java có wrapper tương ứng:

| Primitive | Wrapper | Ví dụ object |
| --- | --- | --- |
| `boolean` | `Boolean` | `Boolean.TRUE` |
| `byte` | `Byte` | `Byte.valueOf((byte) 5)` |
| `short` | `Short` | `Short.valueOf((short) 5)` |
| `char` | `Character` | `Character.valueOf('A')` |
| `int` | `Integer` | `Integer.valueOf(5)` |
| `long` | `Long` | `Long.valueOf(5L)` |
| `float` | `Float` | `Float.valueOf(2.5f)` |
| `double` | `Double` | `Double.valueOf(2.5)` |

Ví dụ lời gọi source:

~~~java
someMethod.invoke(receiver, 5, 2.5);
~~~

Tại **lời gọi tới `invoke(Object, Object...)`**, biểu thức `5` và `2.5` được Java compiler **boxing** thành các object có kiểu tương ứng `Integer` và `Double` để đưa vào mảng `Object[]`. Sau đó, khi Reflection gọi **phương thức đích**, các wrapper có thể được **unbox** và **widen** theo chữ ký formal thực tế.

Cần phân biệt ba lớp:

- **Compile time của mã gọi Reflection:** compiler nhìn thấy `invoke(Object, Object...)` hay `newInstance(Object...)`, chứ không tự chọn overload của method đích dựa trên từng argument.
- **Runtime argument matching:** Reflection kiểm tra số lượng formal parameters, kiểu tham số và giá trị object thật, rồi áp dụng chuyển đổi được phép.
- **Invocation của body đích:** chỉ xảy ra nếu các kiểm tra và phép chuyển đổi cần thiết thành công; exception bên trong body được xử lý theo cơ chế `InvocationTargetException`.

`Constructor.newInstance` không phải `Class.newInstance()`. Trong Java hiện đại, nên dùng `getDeclaredConstructor(...).newInstance(...)` hoặc `getConstructor(...).newInstance(...)`; `Class.newInstance()` đã deprecated.

## 2. Unboxing trong Reflection thực sự là gì?

Giả sử phương thức đích:

~~~java
public static void record(int code) { /* ... */ }
~~~

Đây là phương thức minh họa, cần đặt trong class Java. Nếu ta tra được `Method` cho `record(int)`, các đối số sau tương thích:

~~~java
method.invoke(null, Integer.valueOf(7)); // Integer unbox -> int
method.invoke(null, Byte.valueOf((byte) 7)); // Byte unbox -> byte, widen -> int
method.invoke(null, Character.valueOf('A')); // Character unbox -> char, widen -> int
~~~

`null` receiver ở đây hợp lệ vì `record` là **static method**. Với instance method, receiver phải là object phù hợp kiểu khai báo method; truyền `null` receiver gây `NullPointerException`.

Điểm quan trọng: Reflection **không phải** một converter tổng quát. Nó không parse `"7"` thành `int`, không gọi `Number.intValue()` cho mọi subtype của `Number` và không chọn “kiểu số gần nhất” theo giá trị.

~~~java
method.invoke(null, "7");      // Sai kiểu -> IllegalArgumentException
method.invoke(null, null);     // Cú pháp mơ hồ do varargs; nên ép kiểu rõ nghĩa.
method.invoke(null, (Object) null); // Một đối số null cho int -> IllegalArgumentException
~~~

Trong lời gọi trực tiếp `method.invoke(null, null)`, Java có thể hiểu `null` là **cả mảng `Object[]` của varargs** và compiler cảnh báo; do đó dùng `(Object) null` hoặc `new Object[] { null }` khi chủ ý truyền **một null argument**. Với một method đích có một primitive parameter, null không thể unbox và Reflection báo `IllegalArgumentException`.

### Wrapper phải khớp kiểu nguyên thủy nguồn

Với formal type `int`, `Integer`, `Byte`, `Short` và `Character` đều có lộ trình unbox rồi widen hợp lệ. Nhưng `Long` và `Double` không có lộ trình widening về `int`:

~~~java
method.invoke(null, Long.valueOf(7L));   // Không có long -> int widening
method.invoke(null, Double.valueOf(7.0)); // Không có double -> int widening
~~~

Cả hai đều không hợp lệ dù giá trị số học là 7. Muốn “ép hẹp” có chủ đích, ứng dụng phải quyết định rõ chính sách tràn số/độ chính xác và **chuyển đổi trước khi gọi Reflection**.

## 3. Bảng widening primitive được phép

Java cho phép **identity** (giữ nguyên primitive type) và các hướng *widening primitive conversion* sau:

| Primitive nguồn sau unbox | Primitive đích cho phép ngoài chính nó |
| --- | --- |
| `byte` | `short`, `int`, `long`, `float`, `double` |
| `short` | `int`, `long`, `float`, `double` |
| `char` | `int`, `long`, `float`, `double` |
| `int` | `long`, `float`, `double` |
| `long` | `float`, `double` |
| `float` | `double` |
| `double` | Không có kiểu primitive rộng hơn |
| `boolean` | Không chuyển đổi số; chỉ tương thích `boolean` |

Ví dụ nếu formal parameter là `double`, có thể đưa `Byte`, `Short`, `Character`, `Integer`, `Long`, `Float` hoặc `Double` sau các chuyển đổi tương ứng. Nếu formal parameter là `char` thì `Character` mới khớp trong số các wrapper primitive này; `Byte`/`Short` không widen thành `char`.

**Widening không đồng nghĩa luôn bảo toàn chính xác giá trị số học.** Chẳng hạn `long → float` có thể mất một phần độ chính xác biểu diễn; tuy vậy phép widening vẫn được Java cho phép. Reflection tuân thủ quy tắc kiểu, không chặn một phép widening chỉ vì có nguy cơ mất precision.

### Chỉ unbox rồi widen, không “widen wrapper”

`Integer` **không** là subtype của `Long` và Reflection không tự áp chuỗi `Integer → int → long → Long` để điền formal parameter **`Long`**:

~~~java
public static void boxed(Long value) { /* ... */ }
~~~

Nếu truyền `Integer.valueOf(5)` cho formal parameter `Long`, lời gọi Reflection không phù hợp. Formal parameter là reference type `Long`, nên phép hợp lệ cơ bản là tương thích tham chiếu; **không có conversion giữa hai wrapper class khác nhau chỉ vì primitive tương ứng có widening**.

Ngược lại, nếu formal parameter là reference `Number` hoặc `Object` thì `Integer` có thể được truyền bằng **widening reference**:

~~~java
public static void accept(Number value) { /* ... */ }
~~~

`Integer` là subclass của `Number`; không cần unbox để gọi `accept(Number)`. Đây là lý do phải luôn đọc **formal parameter type** trước khi kết luận một conversion có áp dụng hay không.

## 4. `Method.invoke`: xác định formal parameter và chuyển đổi đối số

Ví dụ method đích:

~~~java
public String describe(int code, double score, boolean enabled) {
    return code + "," + score + "," + enabled;
}
~~~

Nếu nằm trong class `Target`, ta tra `Method` bằng **đúng** formal parameter types:

~~~java
Method describe = Target.class.getMethod(
        "describe", int.class, double.class, boolean.class);
~~~

`getMethod`/`getDeclaredMethod` **không tự tìm method theo wrapper type hoặc chọn overload bằng widening**. Ví dụ, `getMethod("describe", Integer.class, Double.class, Boolean.class)` không phải cách tìm method có descriptor `(int, double, boolean)`. Phải truyền các `Class` đúng với chữ ký method.

Sau khi đã lấy `Method`, đối số runtime mới được unbox/widen:

~~~java
Object value = describe.invoke(
        target,
        Character.valueOf('A'), // char -> int (giá trị mã ký tự 65)
        Integer.valueOf(7),     // int -> double (7.0)
        Boolean.TRUE            // boolean -> boolean
);
~~~

`target` phải là instance của class khai báo `describe` (hoặc subtype tương thích). `Method.invoke` trả `Object`: nếu method trả primitive, Reflection sẽ **box** kết quả thành wrapper; nếu trả `void`, kết quả là `null`; nếu trả reference, nhận chính object kết quả theo ngữ nghĩa method.

### Những gì `Method.invoke` không tự làm

- Không đổi `String` sang primitive bằng parsing.
- Không tự làm narrowing `double → int` hay `long → short`.
- Không tự gọi một overload khác khi argument hiện tại không phù hợp.
- Không tự biến `Integer[]` thành `int[]` hoặc chuyển từng phần tử array. Một tham số mảng `int[]` phải nhận chính một object mảng kiểu tương thích.
- Không tự “sửa” kiểu generic bị erasure; nếu method nhận `List<Integer>` thì runtime parameter class vẫn thường là `List.class` và việc đúng loại phần tử cần được kiểm tra riêng.

## 5. `Constructor.newInstance` dùng quy tắc chuyển đổi tương tự

Giả sử constructor:

~~~java
public Target(long count, double amount) {
    this.count = count;
    this.amount = amount;
}
~~~

Với một class `Target` thích hợp, lấy constructor theo chữ ký formal:

~~~java
Constructor<Target> ctor =
        Target.class.getConstructor(long.class, double.class);
~~~

Khi tạo object:

~~~java
Target target = ctor.newInstance(
        Byte.valueOf((byte) 5), // byte -> long
        Float.valueOf(1.25f)    // float -> double
);
~~~

Hợp lệ vì `Byte` unbox thành `byte` rồi widen đến `long`; `Float` unbox thành `float` rồi widen đến `double`. Nếu chuyển đối số đầu thành `Double.valueOf(5.0)`, formal type vẫn là `long` nên **không thể dùng narrowing `double → long`**; `newInstance` ném `IllegalArgumentException` mà không chạy body constructor tương ứng.

Cách tra constructor:

- `getConstructor(Class<?>...)` tìm **public constructor** phù hợp.
- `getDeclaredConstructor(Class<?>...)` tìm constructor khai báo trên class (kể cả private); việc tìm thấy metadata không tự cấp quyền gọi. Nếu constructor không accessible, có thể gặp `IllegalAccessException`; với named modules, khả năng `setAccessible` còn phụ thuộc export/open.
- Tham số constructor cần có số lượng chính xác; `Constructor.newInstance` không tự chọn overload dựa trên argument value.
- Constructor của non-static inner class có thể có tham số ẩn biểu diễn instance enclosing class; cần hiểu descriptor thật khi dùng Reflection.

`Constructor.newInstance` trả object mới thuộc class khai báo constructor. Nó **không** trả giá trị primitive, nên không có bước boxing của *return value* như ở `Method.invoke`.

## 6. Ví dụ chạy hoàn chỉnh (Java 17+)

Tạo file duy nhất tên **`ReflectiveConversionDemo.java`**. Chương trình quan sát đồng thời:

- Wrapper `Byte` được unbox/widen tới constructor formal `long`.
- Wrapper `Float` được unbox/widen tới constructor formal `double`.
- Wrapper `Character` được unbox/widen tới method formal `int`.
- Wrapper `Integer` được unbox/widen tới method formal `double`.
- `Long → int`, `Double → long` và `null → int` bị từ chối.
- Tham số `int[]` được đưa vào như một `Object` duy nhất.
- Method trả `int` được Reflection box thành `Integer`; exception do target tự ném được bọc bằng `InvocationTargetException`.

~~~java
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectiveConversionDemo {
    public static final class Target {
        final long count;
        final double amount;

        public Target(long count, double amount) {
            this.count = count;
            this.amount = amount;
        }

        public String describe(int code, double score, boolean enabled) {
            return count + "," + amount + "," + code + "," + score + "," + enabled;
        }

        public static int sum(int[] values) {
            int total = 0;
            for (int value : values) total += value;
            return total;
        }

        public static void fail(int value) {
            throw new IllegalStateException("inside");
        }
    }

    public static void main(String[] args) throws Exception {
        Constructor<Target> ctor = Target.class.getConstructor(long.class, double.class);
        Target target = ctor.newInstance(Byte.valueOf((byte) 5), Float.valueOf(1.25f));
        Method describe = Target.class.getMethod(
                "describe", int.class, double.class, boolean.class);

        Object result = describe.invoke(
                target, Character.valueOf('A'), Integer.valueOf(7), Boolean.TRUE);
        System.out.println("invoke: " + result);

        Method sum = Target.class.getMethod("sum", int[].class);
        Object answer = sum.invoke(null, (Object) new int[] {1, 2, 3});
        System.out.println("primitive return: " + answer + " (" + answer.getClass().getSimpleName() + ")");

        try {
            describe.invoke(target, Long.valueOf(65L), Integer.valueOf(7), Boolean.TRUE);
        } catch (IllegalArgumentException e) {
            System.out.println("Long to int: " + e.getClass().getSimpleName());
        }

        try {
            describe.invoke(target, null, Integer.valueOf(7), Boolean.TRUE);
        } catch (IllegalArgumentException e) {
            System.out.println("null to int: " + e.getClass().getSimpleName());
        }

        try {
            ctor.newInstance(Double.valueOf(5.0), Float.valueOf(1.25f));
        } catch (IllegalArgumentException e) {
            System.out.println("Double to long: " + e.getClass().getSimpleName());
        }

        try {
            Target.class.getMethod("fail", int.class).invoke(null, Integer.valueOf(9));
        } catch (InvocationTargetException e) {
            System.out.println("target threw: " + e.getCause().getClass().getSimpleName());
        }
    }
}
~~~

Lệnh biên dịch/chạy (JDK 17 trở lên):

~~~bash
javac --release 17 -Xlint:all ReflectiveConversionDemo.java
java ReflectiveConversionDemo
~~~

**Stdout chính xác** (đã biên dịch với `--release 17 -Xlint:all` và chạy thành công):

~~~text
invoke: 5,1.25,65,7.0,true
primitive return: 6 (Integer)
Long to int: IllegalArgumentException
null to int: IllegalArgumentException
Double to long: IllegalArgumentException
target threw: IllegalStateException
~~~

Giải thích theo thứ tự:

1. `Byte(5)` đi vào constructor `long` và `Float(1.25)` vào `double`: hai chuỗi unbox + widen hợp lệ, object `Target` được khởi tạo.
2. `Character('A')` được unbox thành `char` và widen tới `int` cho giá trị mã ký tự `65`; `Integer(7)` thành `double 7.0`; `Boolean.TRUE` hợp kiểu `boolean`.
3. Phương thức `sum` nhận **một `int[]`**; kết quả kiểu primitive `int` có giá trị `6` được Reflection trả về qua `Object` dưới dạng `Integer`.
4. `Long → int`, `null → int` và `Double → long` không thỏa conversion nên bị từ chối bằng `IllegalArgumentException`.
5. `fail` nhận đối số `Integer(9)` hợp lệ, chạy body và ném `IllegalStateException`. Chính **`Method.invoke` bọc exception target** trong `InvocationTargetException`; chương trình in tên kiểu của `getCause()`.

Đừng suy từ các dòng `IllegalArgumentException` rằng method body đích đã chạy: chúng là lỗi **argument matching/conversion**. Trong khi đó, dòng `target threw` chứng minh body đã chạy và phát sinh ngoại lệ.

## 7. Thiết kế lớp chuyển đổi dữ liệu trước khi gọi Reflection

Trong các framework hoặc chương trình nạp phương thức theo cấu hình, dữ liệu nhập thường ở dạng `String`, JSON value hay kiểu số tổng quát. Nếu cần chuyển `"42"` sang `int`, hoặc `Long(42)` sang `int`, hãy thực hiện ở **lớp adaptation/validation riêng**, rồi mới gọi Reflection:

1. Lấy formal types qua `method.getParameterTypes()` hoặc `constructor.getParameterTypes()`.
2. Kiểm tra số lượng argument và xử lý varargs theo descriptor thật; chuyển dữ liệu có chính sách rõ ràng.
3. Với narrowing, kiểm tra phạm vi, phép làm tròn và mất precision trước khi tạo wrapper đích; ví dụ `Math.toIntExact(long)` có thể từ chối giá trị vượt miền `int`.
4. Đưa object/wrapper hay array tương thích vào `invoke` / `newInstance`; xử lý `IllegalArgumentException` cho mismatch, `InvocationTargetException` cho lỗi phát sinh từ target.
5. Đảm bảo truy cập đúng constructor/method và tuân thủ module access, không tự mở private internals ngoài nhu cầu.

Ví dụ **đoạn chuyển đổi ứng dụng**, không thuộc tự động Reflection:

~~~java
long input = 42L;
int checked = Math.toIntExact(input); // Rõ ràng chấp nhận giá trị vừa miền int.
method.invoke(receiver, Integer.valueOf(checked)); // Dành cho method nhận int.
~~~

Với numeric domain cần độ chính xác cao hoặc dữ liệu tài chính, quy tắc kiểu `double` và float widening chưa đủ để đảm bảo số học mong muốn; cần chọn mô hình dữ liệu/validation phù hợp (chẳng hạn `BigDecimal` khi có yêu cầu chính xác thập phân).

## Tài liệu tham khảo

- [Java SE 21 — `java.lang.reflect.Method.invoke`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Method.html#invoke(java.lang.Object,java.lang.Object...)).
- [Java SE 21 — `java.lang.reflect.Constructor.newInstance`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Constructor.html#newInstance(java.lang.Object...)).
- [Java Language Specification §5.1.2 — Widening Primitive Conversion](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.1.2), [§5.1.8 — Unboxing Conversion](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.1.8), [§5.3 — Method Invocation Contexts](https://docs.oracle.com/javase/specs/jls/se21/html/jls-5.html#jls-5.3).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="argument-conversions-003">Vì sao narrowing hoặc đổi String thành số không được tự thực hiện</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao Reflection không tự narrowing hay đổi String thành số?

## 1. Vấn đề: Reflection gọi phương thức, không phải bộ chuyển đổi dữ liệu tùy ý

Trong Java Reflection, lời gọi `Method.invoke(receiver, args...)` phải truyền các đối số phù hợp với **kiểu tham số đã khai báo** của phương thức đích. Reflection hỗ trợ một tập chuyển đổi cụ thể, gồm **unboxing** từ wrapper tương ứng và **widening primitive conversion** được phép. Nó **không tự thực hiện narrowing primitive conversion** (ví dụ `long → int` hoặc `int → short`) và **không parse `String` thành số** (ví dụ `"42" → 42`).

Ví dụ, nếu phương thức đích có chữ ký:

~~~java
public static int doubleIt(int number) {
    return number * 2;
}
~~~

thì `Method.invoke(null, Integer.valueOf(7))` có thể gọi phương thức với tham số `int` bằng cách unbox `Integer`; `Method.invoke(null, Byte.valueOf((byte) 7))` cũng có thể gọi bằng cách unbox `byte` rồi widening `byte → int`. Nhưng truyền `Long.valueOf(7L)` hoặc `"7"` tới tham số `int` sẽ không tự chuyển thành số nguyên; lời gọi thất bại với `IllegalArgumentException`.

Đây là **thiết kế kiểm tra kiểu**, tránh suy đoán giá trị hoặc tự ý thực hiện chuyển đổi có thể gây mất dữ liệu, sai ý nghĩa hay phụ thuộc định dạng chuỗi.

## 2. Java cho phép những chuyển đổi nào khi `Method.invoke()`?

Chữ ký Reflection:

~~~java
Object Method.invoke(Object obj, Object... args)
        throws IllegalAccessException, InvocationTargetException
~~~

Trong runtime, các đối số truyền vào `Object... args` là tham chiếu: khi viết `method.invoke(null, 7)`, trình biên dịch Java sẽ boxing literal `7` thành `Integer` để truyền qua varargs. Reflection sau đó đối chiếu **giá trị runtime** với kiểu tham số method đích.

Đối với tham số **primitive**, Reflection có thể unbox từ wrapper thích hợp rồi widening primitive nếu cần. Nó không narrow giá trị primitive. Đối với tham số **reference**, giá trị phải tương thích về quan hệ kiểu tham chiếu; Reflection không tự “biến” một `Integer` thành `Long` wrapper và không chuyển chuỗi chữ số thành bất cứ kiểu số nào.

| Tham số đích | Giá trị truyền cho `invoke()` | Kết quả | Lý do |
| --- | --- | --- | --- |
| `int` | `Integer.valueOf(7)` | Được | Unboxing `Integer → int`. |
| `int` | `Byte.valueOf((byte) 7)` | Được | Unbox `Byte → byte`, widening `byte → int`. |
| `int` | `Character.valueOf('A')` | Được | Unbox `char`, widening `char → int` (mã Unicode UTF-16 của `'A'` là 65). |
| `long` | `Integer.valueOf(7)` | Được | Unbox `int`, widening `int → long`. |
| `int` | `Long.valueOf(7L)` | **Không** | Cần narrowing `long → int`. |
| `short` | `Integer.valueOf(7)` | **Không** | Cần narrowing `int → short`, kể cả giá trị 7 có thể biểu diễn bằng short. |
| `int` | `"7"` | **Không** | `String` không phải wrapper số; không có tự động parse. |
| `int` | `null` | **Không** | Không thể unbox `null` thành primitive. |
| `Long` (reference) | `Integer.valueOf(7)` | **Không** | `Integer` không phải subtype của `Long`; không tự unbox–widen–rebox. |
| `Number` (reference) | `Integer.valueOf(7)` | Được | `Integer extends Number`: widening reference. |
| `String` | `"7"` | Được | Đã là `String`, không cần chuyển kiểu. |

Với widening, có thể mất **độ chính xác số học** trong một vài chuyển đổi (chẳng hạn `long → float`); “widening” mô tả quan hệ kiểu/miền biểu diễn, **không phải lời hứa rằng mọi giá trị được giữ nguyên từng bit**. Điểm chính là Java có quy tắc widening được định nghĩa sẵn, còn narrowing đòi hỏi quyết định tường minh của người viết.

## 3. Ví dụ độc lập Java 17+: các trường hợp thành công và thất bại

Tạo file `ReflectionConversionDemo.java`:

~~~java
import java.lang.reflect.Method;

public class ReflectionConversionDemo {
    public static int acceptInt(int n) {
        return n * 2;
    }

    public static short acceptShort(short n) {
        return n;
    }

    public static long acceptLong(long n) {
        return n + 1;
    }

    private static void test(String label, Method method, Object arg)
            throws Exception {
        try {
            System.out.println(label + " => " + method.invoke(null, arg));
        } catch (IllegalArgumentException e) {
            System.out.println(label + " => " + e.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) throws Exception {
        Method intMethod =
                ReflectionConversionDemo.class.getMethod("acceptInt", int.class);
        Method shortMethod =
                ReflectionConversionDemo.class.getMethod("acceptShort", short.class);
        Method longMethod =
                ReflectionConversionDemo.class.getMethod("acceptLong", long.class);

        test("Integer -> int", intMethod, Integer.valueOf(7));
        test("Byte -> int", intMethod, Byte.valueOf((byte) 3));
        test("Character -> int", intMethod, Character.valueOf('A'));
        test("Integer -> long", longMethod, Integer.valueOf(7));
        test("Long -> int", intMethod, Long.valueOf(7));
        test("Integer -> short", shortMethod, Integer.valueOf(7));
        test("String -> int", intMethod, "7");
        test("null -> int", intMethod, null);
        test("explicit parse", intMethod, Integer.parseInt("7"));
        test("explicit narrow", shortMethod, (short) 7);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 ReflectionConversionDemo.java
java ReflectionConversionDemo
~~~

Stdout, đã chạy và xác nhận bằng JDK biên dịch tương thích Java 17:

~~~text
Integer -> int => 14
Byte -> int => 6
Character -> int => 130
Integer -> long => 8
Long -> int => IllegalArgumentException
Integer -> short => IllegalArgumentException
String -> int => IllegalArgumentException
null -> int => IllegalArgumentException
explicit parse => 14
explicit narrow => 7
~~~

**Cách đọc chương trình:**

- `getMethod("acceptInt", int.class)` tra cứu **chữ ký khai báo**, không gọi method; kiểu mong đợi là `int.class`.
- `test(...)` nhận `Object arg`; tại `method.invoke(null, arg)`, Reflection xét **kiểu runtime của arg**. Ví dụ `Byte.valueOf((byte) 3)` mang primitive nguồn `byte` khi unbox, có thể widening thành `int`; `acceptInt(3)` trả `6`.
- `Character.valueOf('A')` unbox thành `char`; widening sang `int` tạo `65`; method nhân đôi nên trả `130`. Đây là xử lý mã ký tự, **không phải parse chuỗi**.
- `Long.valueOf(7)` bị từ chối đối với `int` mặc dù `7L` đủ nhỏ để vừa `int`. Reflection không tự narrowing theo giá trị.
- `"7"` là `String`. Reflection không biết người viết muốn hệ cơ số 10, 16, cách xử lý khoảng trắng, số âm hay lỗi input, nên phải parse trước khi invoke.
- `null` không thể trở thành primitive `int`; ví dụ bắt `IllegalArgumentException` từ việc truyền đối số không hợp lệ.
- `Integer.parseInt("7")` thực hiện chuyển đổi có **quy tắc parsing rõ ràng** trước khi gọi; kết quả `int 7` khi đưa vào `Object...` sẽ boxing thành `Integer` rồi Reflection unbox khi gọi.
- `(short) 7` là narrowing tường minh trong Java source; sau khi boxing thành `Short`, Reflection có thể gọi `acceptShort(short)`. Lưu ý cast với số vượt phạm vi có thể làm mất dữ liệu.

## 4. Tại sao `String` không tự động thành số?

Chuỗi chỉ lưu **ký tự**. Dữ liệu người dùng, dòng CSV, tham số HTTP và nội dung JSON có thể chứa các giá trị như `"15"`, `" 15 "`, `"1,500"`, `"0xFF"`, `""` hoặc `"abc"`. Chúng có ý nghĩa số hay không tùy quy ước dữ liệu:

- `"15"` có thể parse theo hệ 10 thành `15`.
- `" 15 "` cần quyết định có `trim()` hay `strip()` trước không; `Integer.parseInt` thuần không chấp nhận dấu cách ở hai đầu.
- `"1,500"` có thể là một nghìn năm trăm hoặc một phẩy năm tùy locale; không thể đoán bằng Reflection.
- `"0xFF"` có thể muốn hiểu là hệ 16 nhưng `Integer.parseInt(text)` mặc định parse hệ 10; muốn hệ 16 phải chỉ định xử lý thích hợp.
- `"abc"` hoặc giá trị vượt miền `int` phải được xử lý như dữ liệu không hợp lệ.

Reflection không phải parser hoặc data-binding framework. Để gọi method nhận `int`, mã của bạn phải có **quy tắc chuyển String thành int** được chọn rõ ràng:

~~~java
String input = " 42 ";
int parsed = Integer.parseInt(input.strip());
Object result = intMethod.invoke(null, parsed);
~~~

Đoạn này phụ thuộc biến `intMethod` từ phần 3 và không phải chương trình độc lập. `parsed` là `int`; khi truyền vào `Object...` của `invoke`, trình biên dịch boxing rồi Reflection tự unbox theo API. Kết quả method `acceptInt` sẽ là `Integer(84)`.

Các API parse phổ biến:

| Kiểu cần tạo | Ví dụ chuyển đổi tường minh | Sai dữ liệu |
| --- | --- | --- |
| `int` | `Integer.parseInt(text)` | `NumberFormatException` |
| `long` | `Long.parseLong(text)` | `NumberFormatException` |
| `double` | `Double.parseDouble(text)` | `NumberFormatException` với cú pháp không hợp lệ; có giá trị đặc biệt như `NaN` nếu cú pháp hỗ trợ. |
| `BigDecimal` | `new BigDecimal(text)` | `NumberFormatException`; phù hợp hơn binary floating-point khi cần biểu diễn thập phân chính xác. |
| `short` | `Short.parseShort(text)` | `NumberFormatException` nếu không parse được hoặc ngoài miền `short`. |

Với dữ liệu phân biệt locale, dùng parser phù hợp như `NumberFormat` và quản lý `ParsePosition`/kiểm tra chuỗi đã được tiêu thụ hết nếu yêu cầu nghiêm ngặt. Đừng gọi `Double.parseDouble` rồi cast `int` để parse mã định danh hay số nguyên: có nguy cơ mất phần thập phân và vượt miền.

## 5. Khi bắt buộc narrowing, hãy quyết định rõ về mất dữ liệu

Narrowing primitive là chuyển sang kiểu có miền biểu diễn nhỏ hơn, ví dụ:

~~~java
long source = 3_000_000_000L;
int narrowed = (int) source; // Có thể mất dữ liệu
~~~

Cú pháp cast ở trên hợp lệ trong Java source, nhưng kết quả không nhất thiết bằng giá trị ban đầu. Nếu muốn **từ chối overflow** thay vì làm mất dữ liệu, dùng:

~~~java
long source = 3_000_000_000L;
int safe = Math.toIntExact(source); // Ném ArithmeticException nếu ngoài miền int
~~~

Với giá trị thực sự thuộc miền, chẳng hạn `long source = 7L;`, `Math.toIntExact(source)` trả `int 7`; đem số `int` này truyền cho method có tham số `int` là hợp lệ.

Cân nhắc hai cách:

- **Chấp nhận mất dữ liệu có chủ đích:** dùng cast `(int) someLong`, `(short) someInt` theo thiết kế dữ liệu và giải thích giới hạn.
- **Không chấp nhận mất dữ liệu:** kiểm tra miền bằng `Math.toIntExact` hoặc điều kiện `Short.MIN_VALUE <= n && n <= Short.MAX_VALUE` trước khi cast.

Reflection không thể chọn hộ giữa hai chính sách này; caller phải quyết định trước khi đưa đối số cho `invoke()`.

## 6. Quy trình chuyển đổi dữ liệu an toàn trước khi gọi Reflection

Khi xây hệ thống đọc cấu hình, CLI, HTTP request hay CSV và gọi các phương thức động:

1. **Xác định signature:** Dùng `Method.getParameterTypes()` hoặc `getGenericParameterTypes()` khi có generic; hiểu rõ method yêu cầu `int`, `Long`, `String` hay một kiểu khác.
2. **Xác định schema input:** Biết định dạng, locale, khả năng null, giới hạn miền, đơn vị, và cách báo lỗi. Đừng suy kiểu số chỉ từ chuỗi hiển thị.
3. **Parse nếu dữ liệu dạng text:** Dùng parser đúng kiểu. Xử lý `NumberFormatException`, chuỗi trống, khoảng trắng, overflow và giá trị không hợp lệ.
4. **Chuyển số tường minh khi cần narrowing:** Cân nhắc cast có chủ đích hoặc kiểm tra miền bằng hàm/điều kiện phù hợp. Đừng truyền `Long` vào `int` rồi mong Reflection tự xử lý.
5. **Kiểm tra null và kiểu tham chiếu:** Primitive không nhận null; reference có thể nhận null nếu logic method cho phép. Không có phép “chuyển thành null hợp lệ” cho primitive.
6. **Chỉ sau đó gọi `invoke(...)`:** Truyền đúng receiver và đối số theo thứ tự. Xử lý `IllegalArgumentException` cho lỗi đối số; `InvocationTargetException.getCause()` cho lỗi phát sinh từ method đích.

Ví dụ xử lý một chuỗi số trước khi gọi phương thức nhận `int`:

~~~java
static Object invokeIntFromText(Method method, Object target, String text)
        throws ReflectiveOperationException {
    int value = Integer.parseInt(text.strip());
    return method.invoke(target, value);
}
~~~

Đây là **method minh họa** phải đặt trong class có import `java.lang.reflect.Method`. Nó dành cho tình huống đã biết chắc method đích nhận đúng một `int`. `Integer.parseInt` có thể ném `NumberFormatException` (unchecked) khi input sai; chữ ký trên không thay thế validation ở biên hệ thống. Với kiểu mục tiêu khác, phải chọn parser/converter tương ứng.

## 7. Tự luyện và kiểm tra

1. `acceptInt(int)` nhận `Short.valueOf((short) 4)` qua Reflection hay không? Dự đoán output nếu logic method nhân đôi.
2. `acceptInt(int)` nhận `Float.valueOf(4.0f)` không? Dù giá trị bằng một số nguyên, lý do từ chối là gì?
3. Với `acceptLong(long)`, truyền `Long.valueOf(9)` và `Integer.valueOf(9)` có khác nhau không? Trường hợp nào phải widening?
4. Nếu dữ liệu text là `" 2147483648 "` và đích là `int`, cần xử lý điều gì ngoài `strip()`? Có nên cast trực tiếp từ `long`?
5. Nếu đích là `Long` reference (không phải `long` primitive), vì sao truyền `Integer.valueOf(9)` không được?
6. Hãy sửa code ở phần 3 để sau khi `Long -> int` bị từ chối, dùng `Math.toIntExact(7L)` và gọi lại. Giải thích vì sao giờ tham số đã phù hợp.

**Ghi nhớ:** Reflection giữ quy tắc chuyển đổi **có định nghĩa rõ**: nhận unboxing và widening thích hợp, nhưng **không đoán parsing String, không tự narrowing, không đổi wrapper này sang wrapper khác tùy ý**. Caller chịu trách nhiệm chọn cách chuyển và xác thực input trước khi gọi.

## Tài liệu chuẩn

- [Java SE 17 — `Method.invoke()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html#invoke(java.lang.Object,java.lang.Object...))
- [Java SE 17 — `Constructor.newInstance()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Constructor.html#newInstance(java.lang.Object...))
- [Java SE 17 — `Field.set()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html#set(java.lang.Object,java.lang.Object))
- [Java Language Specification §5.1 — Kinds of Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1)
- [Java Language Specification §5.3 — Method Invocation Contexts](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.3)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="argument-conversions-004">Chuyển tham chiếu theo quan hệ assignability</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển tham chiếu theo quan hệ assignability trong Java Reflection

## 1. Mục tiêu

Khi gọi một phương thức hay constructor bằng Java Reflection, lập trình viên truyền đối số qua các API như `Method.invoke(...)` hoặc `Constructor.newInstance(...)`. Các API này nhận `Object` ở phía người gọi, nhưng **tham số thật** của phương thức đích có thể là `Animal`, `Serializable`, `Object[]`, một interface, hoặc một kiểu tham chiếu bất kỳ. JVM phải xác định đối tượng thực tế có thể được sử dụng tại vị trí tham số đó hay không.

Quy tắc cốt lõi của bài là **assignability**: một giá trị tham chiếu có thể đi vào nơi yêu cầu kiểu tham chiếu đích nếu đối tượng có kiểu runtime tương thích với kiểu đích theo quan hệ kế thừa/triển khai; `null` cũng hợp lệ cho tham số kiểu tham chiếu. Đây là cơ sở của **widening reference conversion** và kiểm tra đối số phản chiếu.

Sau bài này, bạn sẽ:

- Hiểu chính xác chiều kiểm tra `Target.class.isAssignableFrom(Source.class)`.
- Phân biệt chuyển tham chiếu mở rộng (*widening*), ép kiểu thu hẹp (*narrowing*) và chuyển đổi primitive.
- Áp dụng vào `Method.invoke`, `Constructor.newInstance` và `Field.set`.
- Xử lý interface, mảng, `null`, generic type erasure, class loader và ngoại lệ đúng cách.
- Tránh nhầm lẫn giữa đối số runtime của phương thức đích và mảng varargs `Object...` của chính API Reflection.

## 2. “Có thể gán” nghĩa là gì?

Giả sử có hệ kiểu:

```java
interface Pet {}

class Animal {}

class Dog extends Animal implements Pet {}

class Cat extends Animal {}
```

Đây là **các khai báo minh họa**, chưa phải chương trình độc lập.

Ở mã Java thông thường:

```java
Dog dog = new Dog();
Animal animal = dog; // Hợp lệ: Dog -> Animal
Pet pet = dog;       // Hợp lệ: Dog -> Pet
Object object = dog; // Hợp lệ: Dog -> Object
```

Khi truyền `dog` tới một phương thức khai báo `accept(Animal animal)`, không cần tạo bản sao đối tượng và cũng không cần thay đổi runtime class. Vẫn là **cùng một đối tượng `Dog`**, chỉ được sử dụng qua một kiểu tham chiếu rộng hơn.

Chiều ngược lại:

```java
Animal animal = new Animal();
// Dog dog = animal; // Lỗi biên dịch: không thể thu hẹp ngầm.
```

Một phép cast viết rõ `(Dog) animal` mới là yêu cầu kiểm tra thu hẹp tại runtime; với đối tượng có kiểu thực `Animal`, phép cast thất bại bằng `ClassCastException`. **Reflection không tự đoán, không tự downcast một đối tượng không tương thích và không tạo một `Dog` mới.**

Phân biệt ba ý:

| Tình huống | Có thể truyền vào tham số `Dog`? | Lý do |
| --- | --- | --- |
| Giá trị runtime là `new Dog()` | Có | Đối tượng đúng kiểu đích |
| Biến khai báo `Animal` nhưng đang trỏ tới `new Dog()` | Có, khi truyền qua Reflection | Kiểm tra đối số runtime thấy đối tượng thực sự là `Dog` |
| Giá trị runtime là `new Cat()` | Không | `Cat` không là subtype của `Dog` dù có cùng lớp cha `Animal` |
| `null` | Có | `null` hợp lệ với mọi tham số kiểu tham chiếu |
| Chuỗi `"Dog"` | Không | Không có phép chuyển chuỗi thành đối tượng `Dog` tự động |

**Điểm đặc biệt của Reflection:** tham số của `invoke` được nhận dưới dạng `Object` nên mã nguồn gọi Reflection không có kiểm tra tĩnh như một lời gọi `accept(Dog)` thông thường. Khả năng tương thích của **giá trị thực** được kiểm tra khi Reflection chuẩn bị gọi phương thức đích.

## 3. `Class.isAssignableFrom`: chiều kiểm tra không được đảo

```java
boolean allowed = TargetType.class.isAssignableFrom(SourceType.class);
```

Phát biểu tương đương: “Một giá trị có kiểu `SourceType` có thể được đặt vào biến/tham số có kiểu `TargetType` hay không?” Với kiểu tham chiếu, kiểm tra dựa vào cùng kiểu, quan hệ superclass, interface được triển khai và một số quan hệ mảng.

Ví dụ:

```java
Animal.class.isAssignableFrom(Dog.class); // true
Pet.class.isAssignableFrom(Dog.class);    // true
Object.class.isAssignableFrom(Dog.class); // true
Dog.class.isAssignableFrom(Animal.class); // false
Dog.class.isAssignableFrom(Cat.class);    // false
Dog.class.isAssignableFrom(Dog.class);    // true
```

Đoạn này dùng những kiểu đã khai báo ở mục 2, mang tính minh họa.

### 3.1. Ba API rất dễ nhầm

| API | Câu hỏi thực tế | Lưu ý |
| --- | --- | --- |
| `targetType.isAssignableFrom(sourceType)` | Quan hệ giữa **hai `Class<?>`** có cho phép gán kiểu nguồn vào kiểu đích không? | `sourceType` phải khác `null`; truyền `null` làm phát sinh `NullPointerException`. |
| `targetType.isInstance(value)` | **Đối tượng runtime** này có phải instance của kiểu đích không? | Trả `false` khi `value == null`, dù `null` vẫn được truyền hợp lệ vào tham số tham chiếu. |
| `targetType.cast(value)` | Cast đối tượng theo kiểu `Class` tại runtime | Trả lại `null` nếu `value == null`; ném `ClassCastException` nếu đối tượng không tương thích. |

Vì thế, nếu muốn **kiểm tra một đối số trước khi gọi Reflection**, phép kiểm tra kiểu tham chiếu thường có dạng:

```java
static boolean acceptsReference(Class<?> targetType, Object value) {
    if (targetType.isPrimitive()) {
        throw new IllegalArgumentException("Cần tham số tham chiếu");
    }
    return value == null || targetType.isInstance(value);
}
```

Đây là **hàm minh họa**, không phải chương trình chạy độc lập. Nó xử lý `null` tường minh thay vì dùng mỗi `isInstance`. Cũng có thể lấy `value.getClass()` rồi gọi `targetType.isAssignableFrom(...)` khi `value` khác `null`.

Không được lấy kiểu khai báo của **biến Java phía gọi** để quyết định, vì Reflection nhận giá trị đối tượng thật. Ví dụ biến `Animal candidate = new Dog();` vẫn có runtime class là `Dog`.

### 3.2. Quan hệ primitive khác quan hệ tham chiếu

`Class.isAssignableFrom` đối với primitive đòi hỏi **cùng đúng kiểu primitive**:

```java
int.class.isAssignableFrom(int.class);   // true
long.class.isAssignableFrom(int.class);  // false
Object.class.isAssignableFrom(int.class);// false
```

Điều này không có nghĩa Reflection không hỗ trợ primitive widening trong mọi API: `Method.invoke` và `Constructor.newInstance` có thể **unbox wrapper** và áp dụng **widening primitive conversion** thích hợp cho tham số primitive. Đó là một cơ chế khác, cần học riêng. **Không dùng `isAssignableFrom` để mô phỏng toàn bộ quy tắc đối số primitive**.

Trong bài này, ta chỉ phân tích **chuyển kiểu tham chiếu** giữa object/array/interface và đối số `null`.

## 4. Assignability ở ba nơi sử dụng Reflection

### 4.1. `Method.invoke`

```java
Method method = Example.class.getDeclaredMethod("accept", Animal.class);
method.invoke(null, new Dog());
```

Đây là **mã minh họa**, giả định `Example.accept(Animal)` là static, đã import `java.lang.reflect.Method` và các kiểu `Animal`, `Dog` có sẵn. Đối số `new Dog()` phù hợp với tham số khai báo `Animal`; Reflection thực hiện lời gọi.

Nếu truyền đối tượng `new Object()` vào tham số `Animal` thì đối số không hợp lệ: `Method.invoke` ném `IllegalArgumentException` **trước khi chạy thân phương thức đích**. Không có chuyển đổi tự động `Object` thành `Animal`.

Nếu một phương thức đích **tự ném ngoại lệ sau khi được gọi**, `Method.invoke` thường bao ngoại lệ đó trong `InvocationTargetException`; đừng nhầm với `IllegalArgumentException` do **không khớp đối số** tại bước chuẩn bị gọi.

### 4.2. `Constructor.newInstance`

```java
Constructor<Example> constructor =
        Example.class.getDeclaredConstructor(Animal.class);
Example instance = constructor.newInstance(new Dog());
```

Đoạn này minh họa, giả sử `Example` có constructor thích hợp và đã import `java.lang.reflect.Constructor`. Quy tắc tương thích đối số tương tự lời gọi method: `Dog` có thể đáp ứng `Animal`; một đối tượng không tương thích sẽ bị từ chối.

Lưu ý: `getDeclaredConstructor(...)` tìm constructor bằng **kiểu tham số khai báo chính xác**. Nó không tìm constructor theo quan hệ assignability:

```java
// Nếu lớp chỉ có constructor Example(Animal), truy vấn dưới đây
// không tìm được constructor Example(Dog):
// Example.class.getDeclaredConstructor(Dog.class);
```

**Tra cứu thành viên** và **kiểm tra đối số lúc invoke** là hai giai đoạn tách biệt.

### 4.3. `Field.set`

```java
Field field = Example.class.getDeclaredField("animal");
field.setAccessible(true);
field.set(example, new Dog());
```

Đây là đoạn minh họa với field khai báo `Animal animal` trong object `example`. `Field.set` kiểm tra rằng đối tượng gán hợp lệ với **kiểu field**. Nếu field là `Animal`, nhận `Dog` hoặc `Cat`, nhưng từ chối `String` bằng `IllegalArgumentException`.

`setAccessible(true)` chỉ giải quyết một phần quyền truy cập; nó **không làm kiểu của field thành `Object`**. Ngoài ra, field `final` hoặc ranh giới module có thể mang ràng buộc riêng về khả năng sửa, độc lập với assignability. Đừng dùng `Field.set` như một lối tắt để ép kiểu không hợp lệ.

## 5. Chương trình Java 17+ hoàn chỉnh và kết quả chính xác

Lưu mã sau vào file **`AssignabilityDemo.java`**:

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class AssignabilityDemo {
    interface Pet { String sound(); }
    static class Animal {}

    static final class Dog extends Animal implements Pet {
        public String sound() { return "woof"; }
    }

    static final class Cat extends Animal {}

    static final class Receiver {
        private Animal stored;

        Receiver(Animal animal) {
            System.out.println("constructor=" + animal.getClass().getSimpleName());
        }

        static void describe(Animal animal) {
            System.out.println("method=" + animal.getClass().getSimpleName());
        }

        static void speak(Pet pet) {
            System.out.println("interface=" + pet.sound());
        }

        static void describeNullable(Animal animal) {
            System.out.println("null=" + (animal == null));
        }

        static void array(Object[] objects) {
            System.out.println("array=" + objects.getClass().getSimpleName());
        }

        static void requireDog(Dog dog) {
            System.out.println("dog=" + dog.sound());
        }

        static void generic(List<String> values) {
            System.out.println("generic-size=" + values.size());
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Animal<-Dog="
                + Animal.class.isAssignableFrom(Dog.class));
        System.out.println("Pet<-Dog="
                + Pet.class.isAssignableFrom(Dog.class));
        System.out.println("Dog<-Animal="
                + Dog.class.isAssignableFrom(Animal.class));
        System.out.println("Object[]<-String[]="
                + Object[].class.isAssignableFrom(String[].class));
        System.out.println("String[]<-Object[]="
                + String[].class.isAssignableFrom(Object[].class));
        System.out.println("Object<-int="
                + Object.class.isAssignableFrom(int.class));
        System.out.println("Animal.isInstance(null)="
                + Animal.class.isInstance(null));

        Constructor<Receiver> constructor =
                Receiver.class.getDeclaredConstructor(Animal.class);
        Receiver receiver = constructor.newInstance(new Dog());

        Method describe = Receiver.class.getDeclaredMethod(
                "describe", Animal.class);
        describe.invoke(null, new Dog());

        Method speak = Receiver.class.getDeclaredMethod("speak", Pet.class);
        speak.invoke(null, new Dog());

        Method nullable = Receiver.class.getDeclaredMethod(
                "describeNullable", Animal.class);
        nullable.invoke(null, new Object[] { null });

        Method array = Receiver.class.getDeclaredMethod("array", Object[].class);
        array.invoke(null, (Object) new String[] { "a", "b" });

        Field stored = Receiver.class.getDeclaredField("stored");
        stored.setAccessible(true);
        stored.set(receiver, new Dog());
        System.out.println("field="
                + stored.get(receiver).getClass().getSimpleName());

        try {
            Method onlyDog = Receiver.class.getDeclaredMethod(
                    "requireDog", Dog.class);
            onlyDog.invoke(null, new Cat());
        } catch (IllegalArgumentException exception) {
            System.out.println("wrong-argument="
                    + exception.getClass().getSimpleName());
        }

        try {
            stored.set(receiver, "not an Animal");
        } catch (IllegalArgumentException exception) {
            System.out.println("wrong-field="
                    + exception.getClass().getSimpleName());
        }

        Method generic = Receiver.class.getDeclaredMethod(
                "generic", List.class);
        generic.invoke(null, List.of(10, 20));
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac --release 17 AssignabilityDemo.java
java AssignabilityDemo
```

**Stdout đã được kiểm chứng bằng JDK 21 với `--release 17`:**

```text
Animal<-Dog=true
Pet<-Dog=true
Dog<-Animal=false
Object[]<-String[]=true
String[]<-Object[]=false
Object<-int=false
Animal.isInstance(null)=false
constructor=Dog
method=Dog
interface=woof
null=true
array=String[]
field=Dog
wrong-argument=IllegalArgumentException
wrong-field=IllegalArgumentException
generic-size=2
```

### 5.1. Diễn giải các kết quả

- `Animal<-Dog=true` và `Pet<-Dog=true` cho thấy một object `Dog` có thể phục vụ cả tham số lớp cha và tham số interface mà nó triển khai.
- `Dog<-Animal=false` cho thấy hướng ngược lại không được phép khi chỉ biết **kiểu `Animal`**. Tuy nhiên, giá trị runtime thực tế là `Dog` vẫn có thể đi vào tham số `Dog` khi truyền qua Reflection.
- `Object[]<-String[]=true` cho thấy **array covariance** đối với kiểu phần tử tham chiếu.
- `Animal.isInstance(null)=false` không mâu thuẫn với `null=true`: API `isInstance` nhận xét về một **instance**, còn `null` được phép vào **tham số tham chiếu**.
- Constructor và hai lời gọi method đều nhận `Dog` tương thích với `Animal`/`Pet`; không cần tự chuyển đối tượng thành lớp khác.
- `Field.set` nhận `Dog` khi field khai báo kiểu `Animal`. Với `String`, nó từ chối bằng `IllegalArgumentException`.
- Method `requireDog(Dog)` không nhận được `Cat`, dù `Cat` cũng là `Animal`.
- `generic-size=2` cố ý chứng minh giới hạn type erasure, được giải thích ở phần 7.

## 6. Ba tình huống dễ bỏ sót: `null`, interface và mảng

### 6.1. Đối số `null`

`null` tương thích với **mọi kiểu tham chiếu**, kể cả `String`, interface, array và class do người dùng khai báo. Nó **không thể truyền vào tham số primitive** `int` hay `boolean` theo cơ chế invoke chuẩn.

Đối với `Method.invoke`, bản thân signature của API là `invoke(Object obj, Object... args)`. Để tránh nhập nhằng giữa một đối số `null` và mảng tham số varargs `null`, hãy truyền rõ:

```java
method.invoke(targetObject, new Object[] { null });
```

Đây là mã minh họa: `method` có **đúng một tham số kiểu tham chiếu**, còn `targetObject` là receiver hợp lệ; nếu method static, target có thể là `null`. Nếu truyền mảng đối số `null` trực tiếp vào varargs của API, Reflection có thể hiểu là **không có mảng chứa đối số**, dẫn đến sai số lượng tham số. Khai báo `new Object[]{null}` làm ý định rõ ràng.

### 6.2. Interface và nhiều nhánh kế thừa

Một lớp có thể implement nhiều interface. Vì thế một object có thể assignable tới nhiều kiểu đích **không có quan hệ lớp cha/con trực tiếp với nhau**. Ví dụ `Dog implements Pet`: kiểm tra `Pet.class.isAssignableFrom(Dog.class)` trả `true`.

Với `Class<?>`, sử dụng quan hệ subtype/runtime type. Tránh so sánh chuỗi `getSimpleName()` hoặc giả định chỉ cần nhìn `getSuperclass()`; đường đi hợp lệ có thể xuất phát từ **interface**.

### 6.3. Array covariance và kiểm tra phần tử lúc ghi

Với mảng phần tử tham chiếu, `String[]` là subtype của `Object[]`, nên:

```java
Object[] values = new String[1];
values[0] = "ok";  // hợp lệ
// values[0] = 42; // biên dịch được nhưng runtime ném ArrayStoreException
```

Đây là mã minh họa. Mảng **vẫn là `String[]` thật ở runtime**, nên JVM kiểm tra mỗi thao tác lưu phần tử. Ghi một `Integer` vào mảng `String[]` gây `ArrayStoreException` dù biến có kiểu `Object[]`.

**Mảng primitive khác:** `int[]` là subtype của `Object`, `Cloneable` và `java.io.Serializable`, nhưng **không phải** subtype của `Object[]` (vì phần tử `int` không phải tham chiếu). Tức là:

```java
Object.class.isAssignableFrom(int[].class);      // true
Object[].class.isAssignableFrom(int[].class);    // false
```

## 7. Generic type erasure: assignability không kiểm tra `T`

Java generic phần lớn bị xóa tham số kiểu ở runtime (*type erasure*). Ví dụ phương thức:

```java
static void generic(List<String> values) {
    System.out.println(values.size());
}
```

Đây là đoạn cắt từ chương trình `AssignabilityDemo`. Kiểu tham số thô ở runtime của phương thức này là **`List.class`**, nên lệnh:

```java
Method m = Receiver.class.getDeclaredMethod("generic", List.class);
m.invoke(null, List.of(10, 20));
```

được Reflection chấp nhận, dù danh sách giá trị chứa `Integer`. Ở ví dụ này, method chỉ gọi `size()` nên in `generic-size=2` mà không truy cập phần tử dưới giả định `String`.

**Hậu quả quan trọng:**

- `List.class.isAssignableFrom(ArrayList.class)` có thể trả `true`, nhưng `Class<?>` không mang thông tin `List<String>` hay `List<Integer>` để kiểm tra từng phần tử.
- `Method.getParameterTypes()` cho kiểu raw `Class<?>`; `Method.getGenericParameterTypes()` có thể cho metadata `ParameterizedType` mô tả khai báo `List<String>`.
- Metadata generic **không tự biến thành kiểm tra runtime đầy đủ** cho từng phần tử trong lời gọi Reflection.
- Nếu thân method đọc phần tử dưới dạng `String`, phép cast do compiler chèn có thể ném `ClassCastException` **bên trong** method. Khi gọi qua Reflection, ngoại lệ đó thường bị bọc bởi `InvocationTargetException`.

Nếu chương trình đòi hỏi xác minh phần tử, phải có kiểm tra nghiệp vụ/runtime thích hợp; `isAssignableFrom` trên mỗi raw `Class<?>` không đủ.

## 8. Class loader và danh tính kiểu

Assignability kiểm tra **đối tượng `Class<?>` runtime thật**, không đơn thuần chuỗi tên lớp. Hai kiểu có cùng binary name do **hai class loader khác nhau định nghĩa** không tự động là một kiểu hoặc subtype của nhau.

Ví dụ lý thuyết: plugin A và plugin B đều nạp một định nghĩa `com.example.PluginData` nhưng qua các class loader độc lập. Nếu class ở B không có quan hệ kế thừa hợp lệ với class ở A, thì:

```text
typeFromA.getName().equals(typeFromB.getName()) -> true
typeFromA.isAssignableFrom(typeFromB)           -> false
```

Đây là **kịch bản minh họa**, không phải chương trình đầy đủ để dựng hai class loader. Trong framework plugin, app server hoặc môi trường hot reload, kiểm tra tên class là chưa đủ. Hãy xem cả `getClassLoader()`, nguồn nạp kiểu và các loader delegation boundaries.

## 9. Bài tập tự kiểm tra

1. Thêm `interface Trainable {}` rồi để `Dog implements Pet, Trainable`. Dự đoán `Trainable.class.isAssignableFrom(Dog.class)` và thử lời gọi method nhận `Trainable`.
2. Giữ biến `Animal value = new Dog();`, gọi method đích nhận `Dog` bằng Reflection. Giải thích tại sao **runtime value** phù hợp dù static type của biến là `Animal`.
3. Gọi `Receiver.array(Object[])` bằng `String[]` rồi so sánh ba cách: ép `(Object)`, bọc `new Object[]{...}` và truyền array trực tiếp.
4. Đổi `generic(List<String>)` để đọc `values.get(0)` vào biến `String`; thử truyền `List.of(10)` qua Reflection và quan sát `InvocationTargetException.getCause()`.
5. Tạo field khai báo `Pet` rồi thử `Field.set` với `new Dog()`, `new Cat()` và `null`. Dự đoán hành vi theo assignability.
6. Tự giải thích tại sao `Object.class.isAssignableFrom(int.class)` là `false` nhưng `Object.class.isAssignableFrom(Integer.class)` là `true`.

## 10. Checklist trước khi gọi Reflection

- Xác định **formal parameter type** bằng `getParameterTypes()` hoặc `Field.getType()`.
- Với giá trị khác `null`, kiểm tra `targetType.isInstance(value)` hoặc `targetType.isAssignableFrom(value.getClass())` **khi target là reference type**.
- Với `null`, xác nhận tham số đích là kiểu tham chiếu và đóng gói `Object[]` đúng số lượng.
- Không tự thêm downcast, parse, conversion primitive hoặc generic verification vào phạm vi **reference assignability**.
- Với một đối số là array, kiểm tra cách Java đóng gói `Object...` của `invoke`.
- Khi có lỗi, phân biệt **tra cứu thành viên**, **quyền truy cập**, **sai đối số**, và **ngoại lệ do thân method**.
- Trong môi trường plugin/multi-class-loader, dựa vào danh tính `Class<?>` thật thay vì chuỗi tên.

**Ghi nhớ:** Chuyển tham chiếu qua Reflection dựa trên **kiểu runtime thực sự** và quan hệ **assignability** với kiểu tham số/field đích. `Target.class.isAssignableFrom(Source.class)` là câu hỏi về hai kiểu; `Target.class.isInstance(value)` là câu hỏi về một giá trị khác `null`. Nếu kiểm tra đúng chiều, xử lý `null` rõ ràng và phân biệt tầng varargs của `Method.invoke`, bạn có thể chẩn đoán hầu hết lỗi chuyển tham chiếu trong Reflection.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="argument-conversions-005">Số lượng đối số và IllegalArgumentException</a>

<details>
<summary>Xem chi tiết</summary>

# Số lượng đối số và IllegalArgumentException khi gọi Java Reflection

Khi gọi method hoặc constructor bằng Reflection, số lượng và kiểu đối số phải khớp **đúng chữ ký đã chọn**. Trong mã nguồn Java thông thường, trình biên dịch có thể phân giải overload, đóng gói varargs và kiểm tra nhiều phép chuyển đổi. Nhưng với `java.lang.reflect.Method.invoke(...)` và `Constructor.newInstance(...)`, bạn đã chọn một executable cụ thể rồi mới truyền dữ liệu. Reflection không tự tìm lại overload, không tự parse String sang số, không bỏ đối số thừa và không tự thêm đối số bị thiếu.

Bài này tập trung vào cách kiểm tra **số lượng đối số**, nguyên nhân dẫn đến `IllegalArgumentException`, quy tắc chuyển đổi cho từng formal parameter và cách phân biệt lỗi của Reflection với lỗi ném từ thân method đích.

## 1. Bước tìm method khác bước invoke

Ví dụ method đích:

```java
class Calculator {
    public long add(int left, long right) {
        return left + right;
    }
}
```

Bước tra cứu:

```java
java.lang.reflect.Method method =
        Calculator.class.getMethod("add", int.class, long.class);
```

Bước thực thi:

```java
Calculator calculator = new Calculator();
Object value = method.invoke(calculator, 2, 5L); // Long(7)
```

Các đoạn trên là code minh họa ghép với nhau, cần xử lý `NoSuchMethodException`, `IllegalAccessException` và `InvocationTargetException` tại phương thức gọi. Hai kiểu formal parameter là `int` và `long`, vì vậy method nhận **chính xác hai đối số** sau receiver. `Method.invoke` có chữ ký:

```java
Object invoke(Object obj, Object... args)
        throws IllegalAccessException, InvocationTargetException;
```

Trong đó:

- `obj` là **receiver** cho instance method; nó **không tính vào** số formal parameter của method. Với static method có thể truyền `null` làm receiver.
- `args` là một mảng các đối số thực tế; mỗi phần tử ứng với một formal parameter theo thứ tự. Kiểu return là `Object`; giá trị primitive được boxed, method trả `void` cho kết quả `null`.
- `Method.getParameterCount()` trả về số formal parameter, còn `Method.getParameterTypes()` trả về các kiểu theo thứ tự. Với `add(int,long)`, hai giá trị này tương ứng `2` và mảng `[int.class,long.class]`.

Nếu không tìm thấy chữ ký ở bước `getMethod(...)`, ngoại lệ là `NoSuchMethodException`. Khi đã tìm ra method nhưng truyền sai đối số cho `invoke(...)`, ngoại lệ thường là `IllegalArgumentException`. Hai lỗi thuộc hai bước khác nhau.

## 2. Sai số lượng: thiếu một, thừa một hoặc nhầm receiver

Với `add(int,long)`:

| Lời gọi | Kết quả | Lý do |
| --- | --- | --- |
| `method.invoke(calculator, 2, 5L)` | Hợp lệ, trả `Long(7)` | Hai slot đúng kiểu |
| `method.invoke(calculator, 2)` | `IllegalArgumentException` | Thiếu một slot |
| `method.invoke(calculator, 2, 5L, 8L)` | `IllegalArgumentException` | Thừa một slot |
| `method.invoke(new Object(), 2, 5L)` | `IllegalArgumentException` | Receiver không tương thích với declaring class |
| `method.invoke(null, 2, 5L)` | `NullPointerException` | Receiver null cho instance method |
| `method.invoke(calculator, 2, "5")` | `IllegalArgumentException` | String không tự parse sang `long` |

Các lời gọi trên đều là code minh họa khi `method` và `calculator` đã được khai báo. **Số lượng receiver + số đối số không phải số formal parameters**: `getParameterCount()` của `add` vẫn là 2, mặc dù `Method.invoke` xuất hiện thêm receiver ở vị trí đầu.

Method đích không có parameter cần `method.invoke(receiver)` hoặc cung cấp mảng actual args rỗng. Trong khi đó, `method.invoke(receiver, (Object) null)` **truyền một slot chứa null**, không phải zero slot.

## 3. Ví dụ Java 17+ chạy được, có đầu ra chính xác

Lưu toàn bộ chương trình sau dưới tên `ReflectionArgumentDemo.java`:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectionArgumentDemo {
    public static class Calculator {
        public long add(int left, long right) {
            return left + right;
        }

        public String join(String prefix, String... items) {
            return prefix + ":" + String.join(",", items);
        }

        public int fail(int value) {
            throw new IllegalArgumentException("business rule");
        }
    }

    private static void attempt(
            String label, Method method, Object receiver, Object... args) {
        try {
            Object result = method.invoke(receiver, args);
            System.out.println(label + " -> " + result);
        } catch (IllegalArgumentException ex) {
            System.out.println(label + " -> IllegalArgumentException");
        } catch (IllegalAccessException ex) {
            System.out.println(label + " -> IllegalAccessException");
        } catch (InvocationTargetException ex) {
            System.out.println(label + " -> target "
                    + ex.getCause().getClass().getSimpleName());
        }
    }

    public static void main(String[] args) throws NoSuchMethodException {
        Calculator calculator = new Calculator();

        Method add = Calculator.class.getMethod(
                "add", int.class, long.class);

        System.out.println("parameter count: " + add.getParameterCount());
        attempt("correct", add, calculator, 2, 5L);
        attempt("missing", add, calculator, 2);
        attempt("extra", add, calculator, 2, 5L, 8L);
        attempt("string", add, calculator, 2, "5");
        attempt("null primitive", add, calculator, null, 5L);
        attempt("narrowing", add, calculator, 2L, 5L);
        attempt("widening", add, calculator, (byte) 2, 5);
        attempt("wrong receiver", add, new Object(), 2, 5L);

        Method join = Calculator.class.getMethod(
                "join", String.class, String[].class);
        System.out.println("join varargs: " + join.isVarArgs());
        attempt("packed", join, calculator, "tag",
                (Object) new String[] {"A", "B"});
        attempt("spread", join, calculator, "tag", "A", "B");

        Method fail = Calculator.class.getMethod("fail", int.class);
        attempt("target throws", fail, calculator, 4);
    }
}
```

Chạy:

```bash
javac ReflectionArgumentDemo.java
java ReflectionArgumentDemo
```

Stdout:

```text
parameter count: 2
correct -> 7
missing -> IllegalArgumentException
extra -> IllegalArgumentException
string -> IllegalArgumentException
null primitive -> IllegalArgumentException
narrowing -> IllegalArgumentException
widening -> 7
wrong receiver -> IllegalArgumentException
join varargs: true
packed -> tag:A,B
spread -> IllegalArgumentException
target throws -> target IllegalArgumentException
```

Giải thích kết quả:

1. `correct`: Integer unbox thành int, Long unbox thành long; method trả primitive long và Reflection boxing kết quả vào `Long`.
2. `missing`, `extra`: không đúng số lượng formal parameters, nên Reflection không gọi method.
3. `string`: Reflection không chạy `Long.parseLong("5")`.
4. `null primitive`: không thể unbox null thành primitive int.
5. `narrowing`: truyền `Long` vào vị trí `int` cần phép thu hẹp không được hỗ trợ.
6. `widening`: Byte unbox rồi widen đến int; Integer unbox rồi widen đến long; trả 7.
7. `wrong receiver`: `new Object()` không là instance `Calculator`.
8. `packed`: slot cuối chứa một `String[]` đúng kiểu formal parameter của method varargs.
9. `spread`: đưa ba slot cho method thực tế có hai formal parameters; Reflection không tự pack.
10. `target throws`: thân method `fail` **đã được gọi** và tự ném `IllegalArgumentException`, được `Method.invoke` bọc trong `InvocationTargetException`. Vì thế helper in thêm tiền tố `target` để phân biệt.

## 4. Constructor.newInstance có quy tắc tương tự

Đối với constructor được chọn qua `Class.getConstructor(...)` hoặc `getDeclaredConstructor(...)`, gọi `Constructor.newInstance(Object... initargs)` cần đúng số lượng và kiểu theo formal parameters của constructor.

Ví dụ minh họa:

```java
public static class Ticket {
    private final int number;

    public Ticket(int number) {
        this.number = number;
    }

    @Override
    public String toString() {
        return "Ticket(" + number + ")";
    }
}
```

Lớp lồng static này cần đặt trong một outer class khi biên dịch. Gọi:

```java
java.lang.reflect.Constructor<Ticket> ctor =
        Ticket.class.getConstructor(int.class);

Ticket ticket = ctor.newInstance(7);
System.out.println(ticket); // Ticket(7)
```

`ctor.newInstance()` thiếu đối số; `ctor.newInstance("7")` sai kiểu; `ctor.newInstance(7L)` cần thu hẹp long xuống int. Những trường hợp đó đều dẫn tới `IllegalArgumentException` trực tiếp từ Reflection.

Nếu code trong constructor **tự ném exception**, `Constructor.newInstance` bọc exception gốc bằng `InvocationTargetException`. Nếu class là abstract, khởi tạo bằng constructor có thể gây `InstantiationException`; nếu không được phép truy cập constructor, có thể gặp `IllegalAccessException`.

Trường hợp **non-static inner class** đáng chú ý: một constructor có thể cần tham số ẩn biểu diễn enclosing instance. Vì vậy công cụ tổng quát không nên đếm parameter chỉ bằng mắt trên source; hãy dùng `Constructor.getParameterCount()` và `getParameterTypes()` của executable thực tế.

## 5. Phân biệt ngoại lệ khi khám phá, chuẩn bị và gọi

| Ngoại lệ | Nguồn điển hình | Cách xử lý |
| --- | --- | --- |
| `NoSuchMethodException` | Tra cứu bằng tên/chữ ký thất bại trước lúc invoke | Kiểm tra tên method, `Class<?>`, mức truy cập và formal parameter types |
| `IllegalArgumentException` trực tiếp | Sai số slot, kiểu, unboxing/widening hoặc receiver sai loại | So sánh actual args với metadata executable |
| `NullPointerException` | Receiver null của instance method hoặc lỗi khác ở mã người gọi | Kiểm tra receiver; không nhầm với lỗi do thân method |
| `IllegalAccessException` | Thành viên không có quyền truy cập hợp lệ | Kiểm tra accessibility và JPMS |
| `InaccessibleObjectException` | Cố bật deep reflection bằng `setAccessible(true)` nhưng module không mở | Dùng API public hoặc khai báo `opens` hợp lệ cho code sở hữu |
| `InvocationTargetException` | Method/constructor **đã thực thi** và ném exception | Xem `getCause()` |
| `ExceptionInInitializerError` | Khởi tạo class trong lúc gọi thất bại | Kiểm tra static initializer và cause |
| `ClassCastException` | Code người gọi ép sai kiểu `Object` đã trả về | Kiểm tra return type và cast |

Nhấn mạnh khác biệt quan trọng:

```java
// Truyền sai tham số: Reflection trực tiếp ném IllegalArgumentException.
method.invoke(receiver, "not-an-int");

// Đúng tham số nhưng code của method đích tự ném IllegalArgumentException:
// Method.invoke sẽ ném InvocationTargetException chứa cause đó.
```

Đây là code minh họa, giả định `method` yêu cầu một tham số int. Nếu chỉ bắt `IllegalArgumentException` trực tiếp từ `invoke`, bạn không tự động bắt được `IllegalArgumentException` do method đích ném. Muốn ghi nhận lỗi nghiệp vụ, bắt `InvocationTargetException` và xem `getCause()`.

Với `Method.invoke`, code xử lý cơ bản:

```java
try {
    Object result = method.invoke(receiver, actualArgs);
} catch (IllegalArgumentException ex) {
    // Lỗi receiver hoặc danh sách actual arguments.
} catch (IllegalAccessException ex) {
    // Lỗi quyền truy cập Reflection.
} catch (java.lang.reflect.InvocationTargetException ex) {
    Throwable targetFailure = ex.getCause();
    // Lỗi xảy ra bên trong method đã được gọi.
}
```

Đây là đoạn minh họa, phụ thuộc `method`, `receiver`, `actualArgs` do ứng dụng cung cấp. Code production nên giữ stack trace/cause thay vì chỉ in tên exception.

## 6. Kiểm tra trước khi invoke nhưng không tái hiện bừa bãi compiler

Có thể kiểm tra arity sớm:

```java
int formalCount = method.getParameterCount();
Class<?>[] formalTypes = method.getParameterTypes();
boolean varargs = method.isVarArgs();

Object[] actualArgs = {2, 5L};
if (actualArgs.length != formalCount) {
    throw new IllegalArgumentException(
            "Expected " + formalCount + " args, got " + actualArgs.length);
}
```

Đây là đoạn minh họa dùng method `add`. Thực tế phải xử lý mảng `actualArgs` có thể null, kiểm tra từng vị trí và xác nhận receiver tương thích. Khi formal type là reference, có thể xét `expectedType.isInstance(value)`; khi formal type là primitive, `Class.isInstance(wrapper)` không thay cho quy tắc unboxing/widening, nên cần mapping nếu chủ động validate.

Kiểm tra trước quan trọng nhưng không thay thế hoàn toàn `invoke`: một receiver có thể null, quyền truy cập có thể bị từ chối, hoặc method đích có thể ném exception. Một adapter nhận dữ liệu từ CLI/HTTP/JSON nên tự parse và validate trước, sau đó **chọn overload rõ ràng** theo chữ ký mong muốn.

Khi tạo thông báo lỗi cho người dùng, nên ghi cụ thể tên method, số lượng cần/có, vị trí tham số và kiểu mong đợi. Không nên phụ thuộc chính xác `IllegalArgumentException.getMessage()` của JDK, vì thông điệp có thể khác giữa các bản runtime.

## 7. Câu hỏi tự kiểm tra

Giả sử `Method m` đại diện `Calculator.add(int,long)` ở mục 3:

- `m.invoke(c, 1, 2L)`: **hợp lệ**, kết quả `Long(3)`.
- `m.invoke(c, 1)`: **IllegalArgumentException**, vì thiếu một đối số.
- `m.invoke(c, 1, 2L, 3L)`: **IllegalArgumentException**, vì thừa một đối số.
- `m.invoke(c, 1, "2")`: **IllegalArgumentException**, vì String không tự parse thành long.
- `m.invoke(c, (byte)1, 2)`: **hợp lệ** nhờ primitive widening.
- `m.invoke(c, 1L, 2L)`: **IllegalArgumentException**, vì formal parameter đầu cần int và không tự narrow long.
- `m.invoke(null, 1, 2L)`: **NullPointerException**, do method không static mà receiver null.
- `Calculator.fail(4)` tự ném `IllegalArgumentException`: Reflection bọc trong **InvocationTargetException**.
- `Calculator.join(String,String...)` cần mấy formal parameters? **Hai**: String và String[].
- Muốn truyền array cho method đích varargs bằng Reflection, cách nào rõ ràng? **`(Object) new String[] {"A","B"}`** ở slot array cuối.

**Ghi nhớ:** xác định đúng executable, đếm đúng **formal parameters**, kiểm tra **receiver**, tính tương thích của từng đối số và tự đóng gói mảng varargs. Khi xảy ra lỗi, phân biệt `IllegalArgumentException` trực tiếp do Reflection phát hiện đối số không hợp lệ với `InvocationTargetException` chứa lỗi từ method/constructor đích.

### Tài liệu chính thức

- [Java SE 17 — Method.invoke](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html)
- [Java SE 17 — Constructor.newInstance](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Constructor.html)
- [Java SE 17 — Executable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Executable.html)
- [JLS 17 — Method Invocation Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.3)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="argument-conversions-006">Receiver null khi gọi phương thức instance</a>

<details>
<summary>Xem chi tiết</summary>

# Receiver `null` khi gọi phương thức instance bằng Java Reflection

Khi dùng Java Reflection để gọi một phương thức, cần phân biệt **đối tượng nhận lời gọi** (receiver) với **các đối số của phương thức**. Trong `Method.invoke(Object obj, Object... args)`, tham số `obj` là receiver; còn `args` là các giá trị truyền cho những tham số do phương thức khai báo.

Với **instance method**, Java phải có một đối tượng thực để thực hiện lời gọi. Nếu receiver là `null`, `Method.invoke` ném **`NullPointerException` trực tiếp**. Với **static method**, không cần receiver nên đối số `obj` được bỏ qua và có thể là `null`.

Sự khác biệt này quan trọng trong nhóm **Đối số và chuyển đổi**: mã gọi Reflection thường truyền cả receiver, tham số và các giá trị `null` động. Nếu đặt sai vị trí, lỗi xuất hiện trước khi logic của phương thức đích chạy, hoặc xảy ra một kiểu lỗi khác với dự đoán.

## 1. Phân biệt receiver và đối số phương thức

Chữ ký cần nhớ:

```java
Object Method.invoke(Object obj, Object... args)
        throws IllegalAccessException, InvocationTargetException;
```

Đây là **chữ ký minh họa API**, không phải khai báo một phương thức mới có thể đặt trực tiếp vào class để biên dịch. Ý nghĩa các thành phần:

| Thành phần | Vai trò | Với phương thức instance | Với phương thức static |
| --- | --- | --- | --- |
| `Method` | Metadata của phương thức đã tra cứu | Xác định phương thức instance sẽ gọi | Xác định phương thức static sẽ gọi |
| `obj` | Receiver | Phải là instance tương thích; **không được `null`** | Bị bỏ qua; thường truyền `null` cho rõ ý |
| `args` | Các đối số thực tế của method đích | Khớp số lượng/kiểu với tham số khai báo | Cũng phải khớp số lượng/kiểu |
| Giá trị trả về | Kết quả sau khi method thực thi | Trả giá trị đóng hộp nếu primitive; `null` nếu return type là `void` | Cùng quy tắc |

Giả sử:

```java
public class Calculator {
    public int add(int a, int b) { return a + b; }
    public static int twice(int value) { return value * 2; }
}
```

Các lời gọi Reflection tương ứng:

```java
Calculator calculator = new Calculator();

Method add = Calculator.class.getMethod(
        "add", int.class, int.class);
Object result = add.invoke(calculator, 3, 4); // Integer(7)

Method twice = Calculator.class.getMethod(
        "twice", int.class);
Object doubled = twice.invoke(null, 5);      // Integer(10)
```

Hai khối trên là **mã minh họa** cần được đặt trong ngữ cảnh có `import java.lang.reflect.Method;` và xử lý checked exceptions. Ví dụ hoàn chỉnh có thể biên dịch/chạy ở mục 4. Điểm quyết định là `calculator` nằm ở **vị trí receiver**, còn `3` và `4` là tham số thật của method đích.

## 2. Vì sao receiver `null` gây `NullPointerException`?

Instance method sử dụng một đối tượng làm `this`, dù nội dung method có đọc field của object hay không. Phương thức phản chiếu thực hiện cơ chế gọi instance trên receiver được đưa vào. Không có receiver hợp lệ thì method đích không thể được gọi.

Ví dụ:

```java
Method add = Calculator.class.getMethod(
        "add", int.class, int.class);
add.invoke(null, 3, 4); // NullPointerException trực tiếp
```

Lời gọi này **không chuyển sang gọi static**, không tạo một `Calculator` mới, và không suy đoán receiver từ tham số `3` hay `4`. Dù đối số khác đúng kiểu, receiver vẫn không hợp lệ.

Các trường hợp tương ứng:

| Phương thức đích | Gọi qua Reflection | Kết quả |
| --- | --- | --- |
| `int add(int, int)` (instance) | `add.invoke(calculator, 3, 4)` | Hợp lệ nếu có quyền và kiểu phù hợp |
| `int add(int, int)` (instance) | `add.invoke(null, 3, 4)` | `NullPointerException` |
| `int add(int, int)` (instance) | `add.invoke(new Object(), 3, 4)` | `IllegalArgumentException`: sai loại receiver |
| `static int twice(int)` | `twice.invoke(null, 5)` | Hợp lệ; receiver không cần thiết |
| `static int twice(int)` | `twice.invoke(calculator, 5)` | Hợp lệ; receiver được bỏ qua |

Một subclass cũng có thể dùng làm receiver nếu tương thích với lớp/interface khai báo. Khi gọi instance method bằng Reflection, **dynamic dispatch** vẫn có thể chọn bản override trên lớp runtime của receiver. Nhưng một receiver `null` không có runtime class để dispatch.

## 3. Kiểm tra `static` trước khi truyền receiver

Khi code chỉ có một `Method` lấy từ cấu hình, không nên suy đoán static/instance qua tên hay giá trị đầu vào. Dùng modifier:

```java
boolean isStatic = java.lang.reflect.Modifier.isStatic(
        method.getModifiers());

Object receiver = isStatic ? null : target;
Object value = method.invoke(receiver, arguments);
```

Đây là **đoạn minh họa**, giả định `method`, `target`, `arguments` đã khai báo và có xử lý exceptions phù hợp. Với instance method, cần kiểm tra `target` không `null` và có kiểu tương thích trước khi gọi.

**Cách kiểm tra an toàn hơn:**

```java
if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
    java.util.Objects.requireNonNull(target, "instance receiver");
    if (!method.getDeclaringClass().isInstance(target)) {
        throw new IllegalArgumentException("Wrong receiver type");
    }
}
Object receiver = java.lang.reflect.Modifier.isStatic(
        method.getModifiers()) ? null : target;
Object value = method.invoke(receiver, arguments);
```

Đây cũng là đoạn minh họa, áp dụng khi receiver được dùng cho phương thức instance. Thực tế có thể cần xét ràng buộc quyền truy cập, method của interface, proxy hoặc subclass, và chuẩn bị mảng đối số đúng số lượng.

Phương thức `canAccess(receiver)` của `AccessibleObject` có quy tắc receiver khác **kiểu lỗi** của `Method.invoke`: với instance method, `canAccess(null)` ném **`IllegalArgumentException`**, trong khi `invoke(null, ...)` ném **`NullPointerException`**. Với static method, `canAccess` yêu cầu đối số **đúng bằng `null`**, còn `invoke` bỏ qua receiver và chấp nhận cả giá trị khác `null`. Hai API không dùng cùng quy tắc kiểm tra tham số.

## 4. Ví dụ Java 17+ chạy độc lập: mọi nhánh quan trọng

Tạo tệp **`NullReceiverReflectionDemo.java`** với toàn bộ mã sau:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class NullReceiverReflectionDemo {
    public static class Counter {
        private int total = 10;

        public int add(int step) {
            total += step;
            return total;
        }

        public int current() {
            return total;
        }

        public String describe(String text) {
            return "length=" + text.length();
        }

        public static String label(String text) {
            return "label=" + text;
        }
    }

    public static void main(String[] args) throws Exception {
        Counter counter = new Counter();

        Method add = Counter.class.getMethod("add", int.class);
        try {
            add.invoke(null, 2);
        } catch (NullPointerException e) {
            System.out.println("nullReceiver=NullPointerException");
        }

        System.out.println("afterNullAttempt=" + counter.current());
        System.out.println("validInstance=" + add.invoke(counter, 2));

        try {
            add.invoke(new Object(), 3);
        } catch (IllegalArgumentException e) {
            System.out.println("wrongReceiver=IllegalArgumentException");
        }

        Method label = Counter.class.getMethod("label", String.class);
        System.out.println("isStatic="
                + Modifier.isStatic(label.getModifiers()));
        System.out.println("staticNull=" + label.invoke(null, "A"));
        System.out.println("staticIgnored="
                + label.invoke("not a Counter", "B"));

        Method describe = Counter.class.getMethod(
                "describe", String.class);
        try {
            describe.invoke(counter, (Object) null);
        } catch (InvocationTargetException e) {
            System.out.println("methodThrew="
                    + e.getCause().getClass().getSimpleName());
        }

        try {
            describe.invoke(null, (Object) null);
        } catch (NullPointerException e) {
            System.out.println("nullReceiverBeforeBody="
                    + e.getClass().getSimpleName());
        }

        Method current = Counter.class.getMethod("current");
        try {
            current.canAccess(null);
        } catch (IllegalArgumentException e) {
            System.out.println("canAccessNull="
                    + e.getClass().getSimpleName());
        }
        System.out.println("canAccessValid="
                + current.canAccess(counter));
        System.out.println("currentValue=" + current.invoke(counter));
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac NullReceiverReflectionDemo.java
java NullReceiverReflectionDemo
```

Kết quả dự kiến chính xác:

```text
nullReceiver=NullPointerException
afterNullAttempt=10
validInstance=12
wrongReceiver=IllegalArgumentException
isStatic=true
staticNull=label=A
staticIgnored=label=B
methodThrew=NullPointerException
nullReceiverBeforeBody=NullPointerException
canAccessNull=IllegalArgumentException
canAccessValid=true
currentValue=12
```

**Diễn giải:**

1. `add.invoke(null, 2)` ném `NullPointerException` **trực tiếp**, vì `add` là instance method. Phương thức chưa chạy nên `afterNullAttempt` vẫn là 10.
2. `add.invoke(counter, 2)` có receiver hợp lệ nên tăng `total` thành 12 và trả `Integer` được tự động nối thành chuỗi.
3. `new Object()` khác `null` nhưng vẫn **sai loại receiver**, nên nhận `IllegalArgumentException`.
4. `Counter.label` là static method. Receiver `null` được phép; thậm chí đối tượng chuỗi `"not a Counter"` cũng được **bỏ qua**, vì không có instance receiver.
5. `describe.invoke(counter, (Object) null)` có receiver đúng nhưng truyền **một đối số `null`** cho tham số `String text`. Method đích bắt đầu chạy rồi `text.length()` ném `NullPointerException`. Reflection bọc lỗi này trong `InvocationTargetException`.
6. Với `describe.invoke(null, (Object) null)`, **receiver** đã `null`, nên `NullPointerException` xảy ra **trước khi body method đích được thực thi**.
7. `current.canAccess(null)` ném `IllegalArgumentException`; `current.canAccess(counter)` trả `true` do đây là phương thức public và caller có quyền truy cập.

Ví dụ không dùng `setAccessible(true)` vì các method đang được gọi đều `public`. Việc bật bỏ qua kiểm tra truy cập không cần thiết để minh họa receiver.

## 5. Phân biệt `NullPointerException` trực tiếp với lỗi từ method đích

Đây là bẫy quan trọng nhất khi debug Reflection:

| Nguồn lỗi | Biểu hiện | Method đích đã chạy chưa? |
| --- | --- | --- |
| Instance receiver `null` | `Method.invoke` ném trực tiếp `NullPointerException` | Chưa |
| Receiver hợp lệ nhưng method đích dereference đối số `null` | `InvocationTargetException` có `getCause()` là `NullPointerException` | Rồi |
| Receiver khác `null` nhưng không phải instance phù hợp | `IllegalArgumentException` | Chưa |
| Số lượng/kiểu đối số truyền vào không phù hợp | `IllegalArgumentException` | Chưa |
| Caller không đủ quyền truy cập method | Có thể là `IllegalAccessException` | Chưa |

Một cách xử lý được thiết kế phù hợp:

```java
try {
    Object result = method.invoke(receiver, values);
    System.out.println(result);
} catch (InvocationTargetException e) {
    Throwable actualFailure = e.getCause();
    System.out.println("Method body failed: " + actualFailure);
} catch (NullPointerException e) {
    System.out.println("Check instance receiver: " + e);
} catch (IllegalArgumentException e) {
    System.out.println("Check receiver type and method arguments: " + e);
} catch (IllegalAccessException e) {
    System.out.println("Reflective access denied: " + e);
}
```

Đây là **đoạn minh họa**, không phải chương trình độc lập; các biến `method`, `receiver`, `values` cần được khai báo. Nếu method đích ném một exception, `InvocationTargetException.getCause()` là thông tin quan trọng để debug. Ngoài các loại trên, method static có thể kích hoạt việc khởi tạo class; nếu khởi tạo class thất bại, một lỗi như `ExceptionInInitializerError` có thể phát sinh.

Không nên chỉ bắt `InvocationTargetException` rồi tin rằng đã xử lý được `null` receiver. Lỗi receiver `null` của instance method **không phải** exception do method đích ném.

## 6. Receiver đúng kiểu: lớp cha, interface và override

Giả sử Reflection lấy metadata của instance method từ lớp cha:

```java
class Parent {
    public String name() { return "parent"; }
}
class Child extends Parent {
    @Override
    public String name() { return "child"; }
}
```

Khi dùng `Method m = Parent.class.getMethod("name");`, lời gọi `m.invoke(new Child())` thực thi override trên `Child` và trả `"child"`. Receiver vẫn phải **khác `null`** và là instance tương thích với declaring class hoặc subclass. Với interface method, receiver có thể là object của class triển khai interface.

Đoạn trên là minh họa, không có `main`. Điều quan trọng: receiver là một **instance**, không nhất thiết phải đúng class một cách tuyệt đối; việc `obj.getClass() == method.getDeclaringClass()` không bắt buộc. Do đó, kiểm tra kiểu hợp lý thường dùng `getDeclaringClass().isInstance(obj)` thay vì so sánh hai đối tượng `Class` bằng `==`.

Nếu method là `private`, việc gọi cần có quyền truy cập thích hợp (và khả năng bật override theo JPMS nếu cần) **ngoài** yêu cầu receiver hợp lệ. `setAccessible(true)` không bao giờ “biến instance method thành static” hoặc làm `null` trở thành một receiver hợp lệ.

## 7. Cách tránh lỗi trong framework và công cụ gọi động

Một reflection dispatcher hoặc plugin loader thường lưu tên method, loại method, target object và đối số ở các vị trí khác nhau. Chỉ cần quên gán target object, instance invocation sẽ hỏng dù tra cứu method và tham số chính xác.

Một quy trình hữu ích:

1. **Tra cứu đúng method:** dùng `getMethod` hoặc `getDeclaredMethod` với tên và kiểu tham số chính xác. Bước tra cứu không tự cung cấp receiver.
2. **Xác định static/instance:** kiểm tra `Modifier.isStatic(method.getModifiers())`. Với static có thể truyền receiver `null`; với instance phải có object đang sống và kiểu tương thích.
3. **Chuẩn bị đối số của method đích:** kiểm tra số lượng, kiểu, boxing/unboxing và chuyển đổi hợp lệ. Tách riêng khái niệm **receiver** khỏi mảng **arguments**.
4. **Kiểm tra quyền truy cập:** `canAccess(receiver)` có thể dùng để kiểm tra trước khi gọi khi truyền receiver đúng chuẩn API; còn `getDeclaredMethod` không tự cấp quyền.
5. **Gọi và phân loại lỗi:** phân biệt `NullPointerException` do receiver với `InvocationTargetException` bọc lỗi của code đích; phân biệt sai receiver type với sai argument.
6. **Không “chữa” null receiver bằng `setAccessible(true)`**. Nếu thiếu object, cần khởi tạo đúng hoặc lấy đúng instance từ container/factory/registry.

Các lỗi thường gặp khi debug:

| Sai lầm | Cách hiểu đúng |
| --- | --- |
| Gọi `instanceMethod.invoke(null)` như gọi static | Instance method cần receiver hợp lệ; `null` ném NPE |
| Cho rằng `null` cho receiver là `null` cho tham số đầu tiên | Đây là hai vị trí khác nhau trong `Method.invoke` |
| Chỉ catch `InvocationTargetException` | NPE do receiver xảy ra ngoài method đích, không bọc kiểu này |
| Coi `canAccess(null)` và `invoke(null)` là cùng một phép kiểm tra | Với instance, API đầu ném IAE, API sau ném NPE |
| Tạo một object bất kỳ làm receiver của instance method | Receiver phải là instance tương thích của declaring class/interface |
| Cho rằng static method bắt buộc truyền instance | Receiver của static method bị bỏ qua; `null` là cách truyền rõ ràng nhất |
| Cho rằng `setAccessible(true)` giải quyết receiver `null` | Accessible flag chỉ liên quan kiểm tra quyền truy cập, không tạo receiver |
| Dùng varargs của `invoke` mà không kiểm tra mảng và ép kiểu | Có thể truyền nhầm số lượng đối số; bọc/ép kiểu rõ ý |

## 8. Ghi nhớ và tài liệu đối chiếu

**Quy tắc cốt lõi:** Với `Method.invoke(obj, args)`, **instance method → `obj` phải là instance hợp lệ, không được `null`**; **static method → `obj` được bỏ qua, thường truyền `null`**. Giá trị `null` nằm trong `args` là chuyện khác: đó là đối số method đích và có thể hợp lệ tùy kiểu/thao tác.

Tài liệu chuẩn Java 17:

- [Method.invoke(Object, Object...)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html#invoke(java.lang.Object,java.lang.Object...)) — quy tắc receiver, chuyển đổi arguments và ngoại lệ.
- [AccessibleObject.canAccess(Object)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html#canAccess(java.lang.Object)) — kiểm tra quyền truy cập và yêu cầu receiver tương ứng.

Khi lỗi xuất hiện, luôn xác định **method có phải static không**, **receiver có đúng object không**, **đối số có đúng chữ ký không** và **exception phát sinh từ Reflection hay chính method đích**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="argument-conversions-007">Varargs của phương thức đích được biểu diễn bằng một tham số mảng</a>

<details>
<summary>Xem chi tiết</summary>

# Varargs của phương thức đích được biểu diễn bằng một tham số mảng

## 1. Ý chính: `String...` là một tham số `String[]` ở runtime

Trong mã nguồn Java, khai báo **varargs** cho phép người gọi truyền số lượng đối số biến đổi ở vị trí cuối:

~~~java
void send(String channel, String... messages) {
    // messages có kiểu String[]
}
~~~

Trình biên dịch cho phép:

~~~java
send("news");
send("news", "A");
send("news", "A", "B");
send("news", new String[]{"A", "B"});
~~~

Đây là các đoạn minh họa bên trong lớp có phương thức `send`, chưa phải chương trình độc lập. Trong những lời gọi sử dụng số phần tử biến đổi, compiler **đóng gói phần cuối thành một mảng `String[]`** để truyền tới phương thức đích.

Tại runtime, chữ ký đã khai báo của `send` chỉ có **hai tham số**:

1. `String channel`.
2. `String[] messages`.

Varargs là **cú pháp hỗ trợ tại vị trí gọi trong mã Java**; nó không có nghĩa phương thức đích thực sự có bất kỳ số lượng tham số runtime nào. JVM dùng kiểu mảng ở cuối chữ ký và metadata đánh dấu phương thức nhận varargs.

**Đặc biệt với Reflection:** `Method.invoke` **không tự gom các đối số rời thành mảng cuối của phương thức đích**. Khi gọi qua Reflection, bạn phải chuẩn bị mảng cuối cùng đúng kiểu và đúng vị trí.

## 2. `getMethod` tìm chữ ký mảng, không tìm các phần tử rời

Cho phương thức public:

~~~java
public void send(String channel, String... messages) { }
~~~

Tra cứu:

~~~java
Method m = Messenger.class.getMethod(
        "send", String.class, String[].class);
~~~

Đây là đoạn minh họa, cần có lớp `Messenger`, import `java.lang.reflect.Method` và xử lý checked exception.

Không dùng:

~~~java
// Sai: tìm ba tham số String riêng biệt, không tồn tại.
Messenger.class.getMethod(
        "send", String.class, String.class, String.class);
~~~

Một phương thức khai báo `send(String, String...)` có đúng dạng tham số runtime `(String, String[])`. Vì vậy:

| Phép tra cứu | Kết quả |
| --- | --- |
| `getMethod("send", String.class, String[].class)` | Tìm thấy phương thức khai báo varargs. |
| `getMethod("send", String.class)` | Không tìm thấy overload này nếu lớp chỉ khai báo `(String, String[])`. |
| `getMethod("send", String.class, String.class)` | Không khớp vì tham số thứ hai yêu cầu `String[]`. |
| `getMethod("send", String.class, String.class, String.class)` | Không khớp vì số tham số không đúng. |

`getMethod` yêu cầu **kiểu tham số khai báo chính xác**; nó không làm overload resolution như trình biên dịch, không tự mở rộng hay gom varargs. Tra sai chữ ký dẫn tới `NoSuchMethodException`, chứ chưa tới bước `invoke`.

### Kiểm tra metadata bằng Reflection

~~~java
Method method = Messenger.class.getMethod(
        "send", String.class, String[].class);

System.out.println(method.isVarArgs());           // true
System.out.println(method.getParameterCount());   // 2
System.out.println(method.getParameterTypes()[1].isArray()); // true
System.out.println(method.getParameterTypes()[1].getComponentType()); // class java.lang.String
~~~

`isVarArgs()` cho biết phương thức **được khai báo** nhận số đối số biến đổi. `getParameterTypes()` vẫn trả mảng các **kiểu tham số hình thức**, và kiểu cuối là `String[].class`.

Nếu một phương thức khai báo `send(String, String[])` (viết mảng trực tiếp) thay vì `send(String, String...)`, chữ ký kiểu runtime vẫn là `(String, String[])` nhưng `isVarArgs()` sẽ trả `false`. Không thể khai báo cả hai dạng này như hai overload khác nhau trong cùng lớp, vì chúng có cùng chữ ký sau khi biên dịch.

## 3. Hai tầng varargs rất dễ nhầm

Với Reflection, có **hai dấu `...` với hai ý nghĩa khác nhau**:

~~~java
// Phương thức đích:
void send(String channel, String... messages);

// API Reflection:
Object Method.invoke(Object receiver, Object... args);
~~~

Tầng thứ nhất: `messages` là **tham số thứ hai của `send`**, kiểu `String[]`.  
Tầng thứ hai: `args` là **mảng `Object[]` chứa toàn bộ đối số của lời gọi `send`**, ngoại trừ receiver của instance method.

Một lời gọi đúng có cấu trúc:

~~~text
Method.invoke(
    receiver,                               <-- instance gọi method
    new Object[]{
        "news",                              <-- tham số #1: String
        new String[]{"A", "B"}               <-- tham số #2: String[]
    }
)
~~~

Số lượng phần tử của `Object[]` là **2**, không phải 3. Mảng `String[]` có thể chứa 0, 1, 2 hoặc nhiều phần tử, nhưng vẫn là **một phần tử** trong mảng các đối số của Reflection.

Do đó:

~~~java
method.invoke(receiver, "news",
        (Object) new String[]{"A", "B"});

method.invoke(receiver,
        new Object[]{"news", new String[]{"A", "B"}});
~~~

Hai dạng trên đều gọi `send` với đúng hai tham số. Dạng thứ hai chỉ rõ toàn bộ mảng đối số Reflection. Dạng thứ nhất dùng `(Object)` để khẳng định mảng `String[]` là **một đối số** của `Method.invoke`, đặc biệt hữu ích với phương thức đích chỉ có một tham số mảng.

## 4. Chương trình Java 17+ chạy đầy đủ

Tạo file **`ReflectVarargsLesson.java`**:

~~~java
import java.lang.reflect.Method;
import java.util.Arrays;

public class ReflectVarargsLesson {
    public static class Messenger {
        public void send(String channel, String... messages) {
            System.out.println(channel + " -> " + Arrays.toString(messages));
        }

        public void only(String... messages) {
            System.out.println("only -> " + Arrays.toString(messages));
        }

        public static void launch(String... args) {
            System.out.println("launch -> " + Arrays.toString(args));
        }
    }

    public static void main(String[] args) throws Exception {
        Messenger receiver = new Messenger();

        // 1. Java compiler tự đóng gói varargs ở lời gọi trực tiếp.
        receiver.send("direct", "A", "B");

        // 2. Reflection thấy chữ ký gồm String và String[].
        Method method = Messenger.class.getMethod(
                "send", String.class, String[].class);
        System.out.println("isVarArgs=" + method.isVarArgs());
        System.out.println("parameterCount=" + method.getParameterCount());
        System.out.println("second="
                + method.getParameterTypes()[1].getTypeName());
        System.out.println("component="
                + method.getParameterTypes()[1].getComponentType().getTypeName());

        // 3. Hai đối số của phương thức đích: String và String[].
        method.invoke(receiver, "reflect",
                (Object) new String[]{"C", "D"});
        method.invoke(receiver,
                new Object[]{"packed", new String[]{"E", "F"}});

        // 4. Mảng rỗng khác mảng null.
        method.invoke(receiver, "empty", (Object) new String[0]);
        method.invoke(receiver, "null", (Object) null);

        // 5. Reflection không tự gom chuỗi rời thành String[].
        try {
            method.invoke(receiver, "wrong", "G", "H");
        } catch (IllegalArgumentException e) {
            System.out.println("expanded: IllegalArgumentException");
        }

        // 6. Phương thức đích chỉ có một tham số String[].
        Method only = Messenger.class.getMethod("only", String[].class);
        only.invoke(receiver, (Object) new String[]{"x", "y"});

        try {
            // Cố ý trải mảng thành 2 args của Method.invoke.
            only.invoke(receiver, (Object[]) new String[]{"x", "y"});
        } catch (IllegalArgumentException e) {
            System.out.println("array spread: IllegalArgumentException");
        }

        // 7. Method static: receiver là null.
        Method launch = Messenger.class.getMethod(
                "launch", String[].class);
        launch.invoke(null, (Object) new String[]{"one", "two"});
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac ReflectVarargsLesson.java
java ReflectVarargsLesson
~~~

Stdout được kiểm chứng:

~~~text
direct -> [A, B]
isVarArgs=true
parameterCount=2
second=java.lang.String[]
component=java.lang.String
reflect -> [C, D]
packed -> [E, F]
empty -> []
null -> null
expanded: IllegalArgumentException
only -> [x, y]
array spread: IllegalArgumentException
launch -> [one, two]
~~~

### Đọc kết quả đúng cách

- `direct` là lời gọi Java bình thường: compiler tự dựng `String[]`.
- `isVarArgs=true` và `parameterCount=2` xác nhận varargs là một tham số mảng trong chữ ký runtime.
- `reflect` và `packed` cho cùng kiểu lời gọi đích dù hai cách chuẩn bị `Object[]` khác nhau.
- `empty` truyền **mảng độ dài 0**; `null` truyền **tham chiếu mảng null**. Phương thức đích nhận hai giá trị khác nhau; ở đây `Arrays.toString(String[])` hiển thị `[]` và `null`.
- `expanded` đưa ba đối số `String` vào lời gọi đích có hai tham số hình thức; `Method.invoke` không tự gộp `"G", "H"` thành mảng.
- `array spread` cố ý đưa một `String[]` làm **Object[] đối số của API Reflection**, tức hai chuỗi rời. Kết quả không khớp phương thức `only(String[])`.
- `launch` chứng minh quy tắc tương tự áp dụng cho phương thức `static`; receiver truyền vào `Method.invoke` là `null`.

## 5. `main(String[])` và các phương thức static varargs

Khai báo:

~~~java
public static void main(String... args) {
    System.out.println(java.util.Arrays.toString(args));
}
~~~

có tham số runtime duy nhất là `String[]`, giống dạng `main(String[])`. Muốn gọi bằng Reflection:

~~~java
Method main = App.class.getMethod("main", String[].class);
main.invoke(null, (Object) new String[]{"first", "second"});
~~~

Đây là ví dụ minh họa trong chương trình có lớp `App` và xử lý ngoại lệ Reflection. Vì phương thức static không cần receiver, `invoke` truyền `null` ở đối số đầu tiên. `(Object)` giữ **toàn bộ `String[]` làm một đối số của phương thức đích**, tránh việc các chuỗi bị diễn giải thành nhiều phần tử của `Object... args`.

Nếu bạn tạo hệ thống chạy plugin, CLI launcher hoặc test runner gọi `main` động, đây là cách truyền args rõ ràng.

## 6. Cách viết tiện ích gọi phương thức varargs động

Nếu đầu vào đến từ mảng giá trị riêng lẻ, **hãy tự dựng mảng cuối theo `componentType`** của tham số varargs. Không phải mọi varargs đều là `String...`; có thể là `int...`, `Number...`, `Object...` hoặc mảng nhiều chiều.

Giả sử đã tìm đúng `Method` có `isVarArgs() == true` và biết `fixedCount = method.getParameterCount() - 1`. Một thuật toán chuẩn bị đối số (mức mô tả) gồm:

1. Xác thực số lượng giá trị đầu vào đủ cho các tham số cố định.
2. Giữ nguyên các đối số tương ứng `parameterTypes[0..fixedCount-1]`, kiểm tra tính tương thích.
3. Lấy kiểu mảng cuối bằng `parameterTypes[fixedCount]`, kiểu phần tử bằng `getComponentType()`.
4. Dùng `java.lang.reflect.Array.newInstance(componentType, extraCount)` để tạo mảng đúng kiểu, gán từng giá trị dư qua `Array.set` nếu chuyển đổi hợp lệ.
5. Xây `Object[] invocationArgs` có đúng `method.getParameterCount()` phần tử, vị trí cuối là mảng vừa tạo.
6. Gọi `method.invoke(receiver, invocationArgs)`.

Ví dụ minh họa khâu đóng gói mảng `String[]`:

~~~java
String[] trailing = new String[]{"alpha", "beta"};
Object[] invocationArgs = new Object[]{"news", trailing};
method.invoke(receiver, invocationArgs);
~~~

Đây là đoạn code trong ngữ cảnh có `method` và `receiver` tương thích; `invocationArgs` có hai phần tử. Với `int...`, mảng cuối phải là `int[]`, **không phải** `Integer[]`. `Array.set` xử lý các phép chuyển đổi thích hợp hoặc báo lỗi khi giá trị không tương thích; ứng dụng cần kiểm tra dữ liệu trước khi gọi.

Với thư viện nhận đầu vào động, phải quy định cả trường hợp đã được cung cấp sẵn mảng cuối: lựa chọn **giữ mảng như một đối số** hay **đóng gói phần tử vào mảng mới** tùy thuộc hợp đồng API. Đừng vừa giữ mảng vừa đóng gói lần nữa vô ý.

## 7. Câu hỏi tự kiểm tra

**Câu 1.** `void log(int level, String... parts)` có bao nhiêu tham số trong `getParameterTypes()`?  
**Hai:** `int.class` và `String[].class`.

**Câu 2.** `logMethod.invoke(receiver, 2, "a", "b")` có tự đóng `"a", "b"` thành một `String[]`?  
**Không.** Phải truyền `logMethod.invoke(receiver, 2, (Object) new String[]{"a", "b"})` hoặc `new Object[]{2, new String[]{"a","b"}}`.

**Câu 3.** Phương thức `void print(String[] values)` (không `...`) có cần một đối số mảng khi gọi qua Reflection?  
**Có.** Cách gọi qua Reflection xét danh sách tham số runtime; mảng vẫn là một tham số. `isVarArgs()` chỉ khác về metadata khai báo.

**Câu 4.** Mã nguồn khai báo `int...` thì `getMethod` truyền `int.class`, `Integer[].class`, hay `int[].class`?  
**`int[].class`**, vì chữ ký có một tham số mảng nguyên thủy.

**Câu 5.** `Method.invoke(receiver, (Object) new String[]{"x", "y"})` truyền bao nhiêu đối số cho phương thức đích?  
**Một**, là toàn bộ mảng `String[]`.

**Câu 6.** Một phương thức static khai báo `String... args`; receiver của `invoke` nên là gì?  
**`null`**; đối số của phương thức đích vẫn là một `String[]`.

**Ghi nhớ:** có **hai lớp varargs**: compiler đóng gói varargs ở lời gọi Java bình thường; còn `Method.invoke(Object, Object...)` chỉ đóng gói các đối số của **chính Reflection API**. Phương thức đích khai báo `T...` vẫn có đúng một tham số `T[]`, và bạn phải truyền mảng đó đúng vị trí.

### Tài liệu chính thức

- [JDK 17 — `Method.invoke`, `getParameterTypes`, `isVarArgs`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html)
- [Oracle Java Tutorials — Invoking Methods with a Variable Number of Arguments](https://docs.oracle.com/javase/tutorial/reflect/member/methodInvocation.html)
- [Dev.java — Invoking Methods](https://dev.java/learn/api/reflection-method-handles/reflection/methods/)
- [Java Language Specification §8.4.1 — Formal Parameters](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.1)
- [Java Language Specification §15.12.4.2 — Evaluate Arguments](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.12.4.2)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="argument-conversions-008">Varargs Object... của API reflection và cách truyền một mảng như một đối số</a>

<details>
<summary>Xem chi tiết</summary>

# Varargs Object... của API reflection và cách truyền một mảng như một đối số

## 1. Bài toán: truyền String[] vào Method.invoke nhưng Java lại hiểu thành nhiều đối số

Java Reflection cung cấp `java.lang.reflect.Method.invoke` để gọi một phương thức được xác định ở thời điểm chạy:

~~~java
Object invoke(Object receiver, Object... args)
        throws IllegalAccessException, IllegalArgumentException,
               java.lang.reflect.InvocationTargetException;
~~~

Tham số `receiver` là đối tượng nhận lời gọi (hoặc `null` cho phương thức static). Phần `Object... args` là **danh sách các đối số mà phương thức đích nhận được**, không phải một đối số duy nhất tên `args`. Ở bytecode, tham số thứ hai của `invoke` thực chất là `Object[]`. Ký hiệu ba chấm `...` khiến **trình biên dịch tại nơi gọi** có thể tự tạo mảng `Object[]` chứa từng đối số.

Điều này dễ tạo bẫy khi bản thân phương thức đích cũng cần một đối số là **một mảng**:

~~~java
public void printAll(String[] items) { /* ... */ }
~~~

Nếu gọi:

~~~java
String[] items = {"red", "blue"};
Method method = Printer.class.getMethod("printAll", String[].class);
method.invoke(printer, items); // DỄ SAI: items có thể bị dùng như Object[] đối số
~~~

Vì `String[]` là một kiểu con của `Object[]`, trình biên dịch có thể dùng **chính `items` làm mảng chứa các đối số của `invoke`**. Reflection khi ấy thấy hai đối số `"red"` và `"blue"`, trong khi `printAll` chỉ khai báo **một tham số kiểu `String[]`**. Kết quả là `IllegalArgumentException` do số lượng đối số không khớp.

**Cách truyền chắc chắn một mảng thành một đối số duy nhất:**

~~~java
method.invoke(printer, (Object) items);
method.invoke(printer, new Object[] { items });
~~~

Trong trường hợp trên, cả hai hình thức đều khiến `args` của `Method.invoke` có **một phần tử**, và phần tử đó chính là đối tượng mảng `String[]` cần đưa cho `printAll`.

Bài này phân biệt hai lớp varargs, giải thích trường hợp `String[]` và `int[]`, xử lý `null`, chỉ ra sự khác nhau giữa phương thức đích nhận mảng thường và khai báo `String...`, đồng thời thực hành những lỗi được báo bằng Reflection.

## 2. Hai lớp đối số: Object[] bên ngoài và mảng dữ liệu bên trong

Xét API:

~~~java
Method method = Printer.class.getMethod("printAll", String[].class);
String[] items = {"red", "blue"};
Object result = method.invoke(printer, new Object[] { items });
~~~

Ta có **hai mảng ở hai cấp**:

~~~text
Method.invoke(printer, new Object[] { items })
                      |
                      +-- Object[] args của chính Method.invoke
                          length = 1
                          args[0] --------------+
                                                 |
                                                 v
                                 String[] items = ["red", "blue"]
                                 (một đối số của printAll)
~~~

- **Mảng bên ngoài** có kiểu `Object[]`, biểu diễn *số lượng và thứ tự tham số* cần truyền cho phương thức đích.
- **Mảng bên trong** có kiểu `String[]`, là *giá trị của đúng một tham số* mà `printAll(String[])` mong đợi.
- `args.length == 1` không có nghĩa `items.length == 1`. `items` vẫn chứa hai chuỗi, hoặc có thể chứa hàng nghìn phần tử.
- Phương thức đích nhận đúng một tham số `String[]`. Reflection không tự duyệt `items` và coi từng phần tử là một tham số của `printAll` khi nó đã được bọc đúng cách.

Đối chiếu hai biểu thức:

~~~java
method.invoke(printer, items);
method.invoke(printer, new Object[] { items });
~~~

Chúng **không tương đương** nếu `items` có kiểu `String[]` hoặc một kiểu mảng tham chiếu khác có thể chuyển sang `Object[]`. Biểu thức thứ nhất có thể gửi trực tiếp `items` làm mảng `args` của API `invoke`; biểu thức thứ hai tạo một `Object[]` chứa một phần tử duy nhất là `items`.

Một cách tự kiểm tra trước khi chạy: nếu phương thức đích khai báo `printAll(String[])`, câu hỏi cần hỏi là **`Method.invoke` sẽ nhìn thấy bao nhiêu phần tử trong `Object[] args`?** Câu trả lời phải là một, và phần tử đó phải tương thích với `String[]`.

## 3. Varargs ở Java hoạt động ở nơi gọi như thế nào?

Ký hiệu `Object... args` là cú pháp cho tham số biến độ dài. Ở góc nhìn chữ ký JVM, nó là một tham số mảng `Object[]`. Java cho phép hai cách gọi phổ biến:

~~~java
method.invoke(receiver, value1, value2); // dạng variable arity
method.invoke(receiver, new Object[] { value1, value2 }); // mảng args tường minh
~~~

Trong dạng thứ nhất, trình biên dịch thường tạo `new Object[] { value1, value2 }` để truyền tới phương thức `invoke`. Dạng thứ hai cung cấp trực tiếp mảng `Object[]`, nên trình biên dịch không cần tạo mảng khác.

### Vì sao String[] gây bẫy?

~~~java
String[] words = {"a", "b"};
method.invoke(receiver, words);
~~~

`String[]` là một **reference array** và có quan hệ kiểu con với `Object[]`. Vì vậy, tại vị trí đối số cuối của phương thức `invoke(Object, Object...)`, trình biên dịch có thể chọn cách gọi với tham số mảng `Object[]` đã có (fixed arity). Nó không nhất thiết bọc cả `words` vào một `Object[]` mới.

Đọc theo nghĩa tương đương ở tầng đối số truyền cho Reflection:

~~~text
method.invoke(receiver, words)
    -> args của Method.invoke là words
    -> args.length = words.length
    -> từng phần tử String bị xem là một đối số của phương thức đích
~~~

Khi biên dịch bằng `javac`, lời gọi kiểu này thường xuất hiện cảnh báo *non-varargs call for last parameter; cast to Object for a varargs call or to Object[] for a non-varargs call*. Cảnh báo này nói về **cách biên dịch lời gọi `Method.invoke`**, không phải tuyên bố rằng phương thức đích có hoặc không có varargs.

### Vì sao ép (Object) sửa được?

~~~java
method.invoke(receiver, (Object) words);
~~~

Phép ép làm **kiểu tĩnh** của biểu thức đối số cuối trở thành `Object`, không còn là `Object[]`. Trình biên dịch phải dùng dạng variable arity và bọc giá trị này vào một `Object[]` chứa **một phần tử**.

Đáng chú ý, phép ép `(Object) words` **không thay bản chất runtime của mảng `words`**. Phần tử bên trong vẫn là `String[]` và vẫn có thể chuyển sang `String[]` tại phương thức đích. Phép ép chỉ tác động đến cách trình biên dịch đóng gói tham số cho lời gọi `invoke`.

### Vì sao ép (Object[]) KHÔNG sửa được?

~~~java
method.invoke(receiver, (Object[]) words);
~~~

Với `String[]`, ép kiểu này hợp lệ nhưng vẫn làm lời gọi nhận **một `Object[]` làm container của danh sách đối số**, nên sẽ trải các chuỗi thành nhiều tham số của phương thức đích. Nó chọn *đúng kiểu mảng bên ngoài*, trái với mục tiêu truyền chính mảng dữ liệu như một đối số.

Do đó, khi muốn truyền **một mảng dữ liệu** đến method đích, hãy ưu tiên `(Object) array` hoặc `new Object[]{array}`. Đừng tùy tiện đổi sang `(Object[]) array`.

## 4. Bảng tình huống cần phân biệt

Giả sử phương thức đích là `void accept(String[] values)`, biến `String[] values = {"red", "blue"};`, đối tượng `m` là `Method` mô tả đúng phương thức:

| Cách gọi | Số đối số mà method đích nhận | Kết quả |
| --- | --- | --- |
| `m.invoke(target, (Object) values)` | 1: chính mảng `String[]` | ✅ Đúng |
| `m.invoke(target, new Object[] { values })` | 1: chính mảng `String[]` | ✅ Đúng |
| `Object[] args = { values }; m.invoke(target, args)` | 1: chính mảng `String[]` | ✅ Đúng |
| `m.invoke(target, values)` | 2: `"red"` và `"blue"` | ❌ Sai số tham số |
| `m.invoke(target, (Object[]) values)` | 2: `"red"` và `"blue"` | ❌ Sai số tham số |
| `m.invoke(target, "red", "blue")` | 2 đối số String | ❌ Sai số tham số |
| `m.invoke(target, (Object) null)` | 1 đối số `null` | ✅ Đúng nếu phương thức đích cho phép nhận mảng `null` |
| `m.invoke(target, new Object[] { null })` | 1 đối số `null` | ✅ Đúng |
| `m.invoke(target, (Object[]) null)` | Danh sách đối số ở API `invoke` là `null`, dùng được cho method **không có** tham số | ❌ Không tương thích với method cần một tham số |

Để gọi phương thức nhận **hai `String` riêng lẻ**, ví dụ `void pair(String left, String right)`, cách `m.invoke(target, new Object[]{"red", "blue"})` lại **đúng** vì hai phần tử là hai tham số. Việc mảng `Object[]` bên ngoài chứa bao nhiêu phần tử phải khớp với **số tham số thật sự của phương thức đích**, chứ không dựa vào số lượng phần tử của một dữ liệu mảng bên trong.

Lưu ý với `null`: `(Object) null` bảo đảm **một đối số có giá trị null**; `(Object[]) null` là **một biến mảng đối số bị null** đưa cho `Method.invoke`, không phải một phần tử null. Nếu target không có tham số, Java cho phép `args` là `null` hoặc mảng độ dài 0; với target cần một tham số, hãy dùng dạng chứa một phần tử.

## 5. Ví dụ Java 17+ hoàn chỉnh: mảng String[], primitive int[], varargs và null

Tạo tệp **`ReflectionVarargsDemo.java`**:

~~~java
import java.lang.reflect.Method;
import java.util.Arrays;

public class ReflectionVarargsDemo {
    public static class Receiver {
        public String accept(String[] values) {
            return values == null
                    ? "array=null"
                    : "array=" + Arrays.toString(values);
        }

        public String collect(String... values) {
            return "varargs=" + Arrays.toString(values);
        }

        public String pair(String first, String second) {
            return "pair=" + first + "+" + second;
        }

        public String numbers(int[] values) {
            return "numbers=" + Arrays.toString(values);
        }

        public String noArguments() {
            return "no-arguments";
        }
    }

    @FunctionalInterface
    interface ReflectCall {
        Object execute() throws Exception;
    }

    private static void attempt(String label, ReflectCall call) {
        try {
            System.out.println(label + ": " + call.execute());
        } catch (Exception error) {
            System.out.println(label + ": "
                    + error.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) throws Exception {
        Receiver receiver = new Receiver();
        String[] words = {"red", "blue"};

        Method accept = Receiver.class.getMethod(
                "accept", String[].class);
        Method collect = Receiver.class.getMethod(
                "collect", String[].class);
        Method pair = Receiver.class.getMethod(
                "pair", String.class, String.class);
        Method numbers = Receiver.class.getMethod(
                "numbers", int[].class);
        Method none = Receiver.class.getMethod("noArguments");

        System.out.println("accept parameters="
                + accept.getParameterCount());
        System.out.println("collect isVarArgs="
                + collect.isVarArgs());

        attempt("(Object) String[]",
                () -> accept.invoke(receiver, (Object) words));
        attempt("Object[]{String[]}",
                () -> accept.invoke(receiver, new Object[] { words }));
        attempt("(Object[]) String[]",
                () -> accept.invoke(receiver, (Object[]) words));

        attempt("target String...",
                () -> collect.invoke(receiver, (Object) words));
        attempt("target String... separately",
                () -> collect.invoke(receiver, "red", "blue"));

        attempt("two String parameters",
                () -> pair.invoke(receiver, (Object[]) words));

        attempt("primitive int[]",
                () -> numbers.invoke(receiver, (Object) new int[] {1, 2}));

        attempt("(Object) null",
                () -> accept.invoke(receiver, (Object) null));
        attempt("(Object[]) null for one parameter",
                () -> accept.invoke(receiver, (Object[]) null));
        attempt("no-arg method",
                () -> none.invoke(receiver));
    }
}
~~~

Biên dịch và chạy từ thư mục chứa tệp, với JDK 17 trở lên:

~~~bash
javac --release 17 ReflectionVarargsDemo.java
java ReflectionVarargsDemo
~~~

**Stdout được suy ra theo hợp đồng Java SE 17, có thể tái hiện bằng đoạn mã trên:**

~~~text
accept parameters=1
collect isVarArgs=true
(Object) String[]: array=[red, blue]
Object[]{String[]}: array=[red, blue]
(Object[]) String[]: IllegalArgumentException
target String...: varargs=[red, blue]
target String... separately: IllegalArgumentException
two String parameters: pair=red+blue
primitive int[]: numbers=[1, 2]
(Object) null: array=null
(Object[]) null for one parameter: IllegalArgumentException
no-arg method: no-arguments
~~~

### Giải thích code và output

- `accept.getParameterCount()` là `1` vì phương thức khai báo đúng một tham số `String[]`. Phần tử trong mảng dữ liệu không làm số tham số của phương thức tăng lên.
- `collect.isVarArgs()` là `true` vì khai báo nguồn là `String... values`. Trong bytecode, tham số cuối là `String[]`; dấu varargs được ghi trong metadata.
- `accept.invoke(receiver, (Object) words)` và `accept.invoke(receiver, new Object[]{words})` đều truyền một mảng `String[]` duy nhất.
- `(Object[]) words` làm `words` trở thành container `args` của Reflection, nên có hai chuỗi riêng; trong khi `accept(String[])` chỉ yêu cầu một tham số.
- `collect(String... values)` là **varargs của phương thức đích**. Tuy nhiên, `Method.invoke` **không tự gom `"red", "blue"` lại thành một `String[]` chỉ vì `collect.isVarArgs()` trả `true`**. Vì vậy truyền riêng hai chuỗi vẫn báo sai số tham số; cần truyền một `String[]` duy nhất.
- `pair(String,String)` có hai tham số riêng, nên trải `words` (qua `(Object[])`) lại vừa đúng.
- `int[]` là một đối tượng, nhưng **không phải** `Object[]`. Cách `(Object) new int[]{1,2}` truyền chính mảng nguyên thủy thành một đối số; không có auto-boxing từng phần tử `int` thành `Integer`.
- `accept.invoke(receiver, (Object) null)` có **một đối số null** tương thích với `String[]`, còn `accept.invoke(receiver, (Object[]) null)` truyền **mảng danh sách đối số null**, không cung cấp một phần tử như phương thức đích yêu cầu.
- Phương thức không tham số `noArguments()` được gọi bằng `none.invoke(receiver)`; nó không cần cung cấp mảng `args` ở mã nguồn.

Hàm `attempt` cố ý in **tên lớp ngoại lệ**, tránh phụ thuộc thông báo lỗi khác nhau giữa các phiên bản JDK. Khi Reflection gọi được method nhưng method đích tự ném ngoại lệ, `Method.invoke` bọc lỗi gốc thành `InvocationTargetException`; ví dụ này không làm phương thức đích ném ngoại lệ.

## 6. Array covariance, primitive arrays và Object[]

### 7.1. Mảng tham chiếu có tính hiệp biến

Trong Java, `String[]` là subtype của `Object[]`. Điều này giải thích tại sao:

~~~java
String[] words = {"a", "b"};
Object[] asObjects = words;
~~~

là hợp lệ, và tại sao `method.invoke(receiver, words)` có thể bị hiểu như đưa vào toàn bộ mảng đối số `Object[]`. Khi định truyền **mảng như dữ liệu**, hãy dùng `(Object) words` hoặc tạo wrapper `new Object[]{words}`.

Không nên cố “giải quyết” bằng cách đổ `String[]` vào một `Object[]` không có lớp bọc. Ví dụ:

~~~java
Object[] args = words; // mảng args có 2 phần tử, không phải 1
method.invoke(receiver, args); // vẫn hai đối số "a", "b"
~~~

Muốn mảng args có một phần tử:

~~~java
Object[] args = { words }; // length == 1; args[0] là String[]
method.invoke(receiver, args);
~~~

### 7.2. Mảng primitive không phải Object[]

`int[]`, `long[]`, `char[]` đều là object và có thể gán cho `Object`, nhưng **không** là subtype của `Object[]`:

~~~java
int[] nums = {1, 2, 3};
Object asOneObject = nums; // hợp lệ
// Object[] notAllowed = nums; // lỗi biên dịch
~~~

Vì vậy, lời gọi `method.invoke(receiver, nums)` thường được trình biên dịch đóng gói như một đối số `Object` duy nhất do `int[]` không thể trở thành `Object[]` trực tiếp. Tuy nhiên, dùng `(Object) nums` hoặc `new Object[]{nums}` cho rõ ý định và nhất quán với code xử lý nhiều loại mảng.

Một mảng số nguyên **không** tự biến thành `Integer[]`, và `Method.invoke` không tự boxing các phần tử bên trong mảng. Nếu phương thức đích nhận `Integer[]` thì truyền `int[]` vẫn sai kiểu; phải chủ động tạo/chuyển mảng phù hợp trước.

### 7.3. Mảng đa chiều cũng là một giá trị đối số

Ví dụ phương thức đích `void process(String[][] matrix)`:

~~~java
String[][] matrix = { {"a", "b"}, {"c"} };
Method m = Handler.class.getMethod("process", String[][].class);
m.invoke(handler, (Object) matrix);
~~~

`String[][]` cũng là một subtype của `Object[]` (vì mỗi phần tử là `String[]`), nên nếu không ép `(Object)`, nó có thể bị dùng làm container đối số của Reflection. Khi cần chuyển **toàn bộ ma trận là một đối số**, nguyên tắc vẫn giống `String[]`.

Ví dụ này cần lớp `Handler` chứa method `process(String[][])`; đây là minh họa cách truyền, không phải chương trình độc lập.

## 7. Truyền null và mảng rỗng chính xác

Ba khái niệm sau là khác nhau:

1. **Không có đối số cho phương thức đích**: `m.invoke(receiver)` hoặc `m.invoke(receiver, new Object[0])`.
2. **Có đúng một đối số mang giá trị null**: `m.invoke(receiver, (Object) null)` hoặc `m.invoke(receiver, new Object[]{null})`.
3. **Có đúng một đối số là mảng rỗng**: `m.invoke(receiver, (Object) new String[0])` khi phương thức đích nhận `String[]`.

Các giá trị này không thể thay thế cho nhau:

| Phương thức đích | Lời gọi reflection | Ý nghĩa |
| --- | --- | --- |
| `void noArgs()` | `invoke(receiver)` | Không đối số, hợp lệ |
| `void noArgs()` | `invoke(receiver, (Object[]) null)` | `args` null được chấp nhận cho phương thức không tham số |
| `void accept(String[] values)` | `invoke(receiver, (Object) null)` | Một tham số kiểu tham chiếu, giá trị null |
| `void accept(String[] values)` | `invoke(receiver, (Object) new String[0])` | Một tham số String[] hợp lệ, độ dài 0 |
| `void accept(String[] values)` | `invoke(receiver, new Object[]{null})` | Một tham số null hợp lệ |
| `void accept(String[] values)` | `invoke(receiver)` | Thiếu một đối số; lỗi số tham số |
| `void accept(String[] values)` | `invoke(receiver, (Object[]) null)` | Không cung cấp phần tử đối số cần thiết; lỗi số tham số |

Viết `m.invoke(receiver, null)` không có ép kiểu sẽ dễ gây nhầm lẫn ở **thời điểm biên dịch**: literal `null` có thể được coi là mảng `Object[]` của lời gọi varargs. Để truyền **một null đến phương thức đích**, luôn dùng `(Object) null` hoặc `new Object[]{null}`. Để truyền **một mảng rỗng**, tạo mảng **đúng kiểu của tham số đích**; `new Object[0]` bên ngoài có nghĩa không đối số, khác `new Object[]{new String[0]}`.

## 8. Bài tập tự kiểm tra kèm đáp án

**Bài 1.** Cho `void process(String[] names)`, `String[] names = {"An", "Binh"};` và `Method m` tương ứng. `m.invoke(receiver, names)` nhận mấy đối số ở cấp Reflection?

**Đáp án:** Hai phần tử của `names` có thể trở thành hai đối số của `Method.invoke` do `String[]` tương thích `Object[]`. Method đích cần đúng một `String[]` nên lời gọi không đúng. Dùng `m.invoke(receiver, (Object) names)`.

**Bài 2.** Với cùng phương thức đích, `m.invoke(receiver, new Object[]{names})` truyền bao nhiêu tham số?

**Đáp án:** Một tham số là mảng `String[]`, phù hợp chữ ký.

**Bài 3.** `void aggregate(String... names)` được lấy bằng `getMethod("aggregate", String[].class)`. `method.isVarArgs()` trả `true`. Có thể gọi `method.invoke(receiver, "An", "Binh")` để Reflection tự gom hai chuỗi không?

**Đáp án:** Không. Metadata varargs không khiến `Method.invoke` tự đóng gói `String[]` cho method đích. Cần `method.invoke(receiver, (Object) new String[]{"An","Binh"})` hoặc container `new Object[]{new String[]{"An","Binh"}}`.

**Bài 4.** Một method đích nhận đúng hai đối số `String left, String right`. Có một biến `String[] names` chứa đúng hai chuỗi. Viết hai cách gọi hợp lệ.

**Đáp án:** `m.invoke(receiver, names[0], names[1])` hoặc `m.invoke(receiver, (Object[]) names)`; ở trường hợp này trải hai phần tử thành hai đối số mới là điều cần.

**Bài 5.** Với method đích `void accept(int[] values)`, vì sao `int[]` không có cùng bẫy covariance như `String[]`?

**Đáp án:** `int[]` không phải `Object[]`. Nó là một `Object` duy nhất; với `Method.invoke`, có thể bọc thành một phần tử. Vẫn nên dùng `(Object) values` cho rõ ràng và tránh sự khác biệt khi thay kiểu mảng.

**Bài 6.** Phân biệt `m.invoke(receiver, (Object) null)` với `m.invoke(receiver, (Object[]) null)` nếu method đích nhận một `String[]`.

**Đáp án:** Cách thứ nhất truyền một đối số null, hợp lệ về kiểu tham chiếu; cách thứ hai gửi một mảng danh sách đối số null, không thỏa một tham số bắt buộc, nên bị lỗi số đối số.

**Bài 7.** Cho `void consume(Object[] values)`, bạn có `Object[] values = { "a", "b" };`. `consumeMethod.invoke(receiver, values)` có đúng không?

**Đáp án:** Không. Với mục tiêu truyền cả `values` làm một tham số kiểu `Object[]`, dùng `consumeMethod.invoke(receiver, (Object) values)` hoặc `new Object[]{values}`. Nếu bỏ cast, `Object[] values` bị dùng trực tiếp làm container hai đối số.

**Bài 8.** Khi `Method.invoke` ném `InvocationTargetException`, có thể kết luận là do mảng varargs bị trải không?

**Đáp án:** Không. `InvocationTargetException` có nghĩa method đích đã được thực thi và ném ngoại lệ. Lỗi đếm đối số/kiểu đối số ở cơ chế gọi Reflection thường là `IllegalArgumentException` trước khi method được gọi.

## 9. Ghi nhớ

**Đối với `Method.invoke(receiver, Object... args)`, mảng `Object[]` ngoài cùng luôn là danh sách tham số của phương thức đích.** Nếu đích cần **một tham số là mảng**, hãy đưa mảng dữ liệu vào **một phần tử** của danh sách đó:

~~~java
method.invoke(receiver, (Object) array);
method.invoke(receiver, new Object[] { array });
~~~

Đừng nhầm `String...` của phương thức đích với `Object...` của API Reflection; mỗi cơ chế đóng gói diễn ra trong **một lời gọi khác nhau**. Đừng nhầm `(Object) array` (một đối số) với `(Object[]) array` (toàn bộ mảng đối số). Khi chưa chắc, hãy kiểm tra `getParameterTypes()` và tạo `Object[]` bên ngoài tường minh.

**Tài liệu chuẩn:**

- [Java SE 17: `Method.invoke` và `Method.isVarArgs`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html).
- [Dev.java: Invoking Methods with Reflection](https://dev.java/learn/reflection/methods/).
- [JLS §15.12.2.4 và §15.12.4.2 — Variable Arity Method Invocation](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="argument-conversions-009">Phân biệt không có đối số với một đối số null</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt không có đối số với một đối số `null` khi gọi Java Reflection

Trong Java Reflection, hai cách gọi sau **khác nhau hoàn toàn**:

```java
method.invoke(target);                 // Không truyền đối số nào cho method đích.
method.invoke(target, (Object) null); // Truyền MỘT đối số, giá trị là null.
```

Sự khác biệt bắt nguồn từ khai báo `Method.invoke(Object obj, Object... args)`: tham số `obj` là **receiver** (đối tượng mà phương thức được gọi trên đó), còn `args` là **danh sách đối số của phương thức đích**. Vì `args` là Java varargs kiểu `Object...` (thực chất `Object[]`), biểu thức `null` ở vị trí này có thể mang hai nghĩa: **mảng đối số `null`** hoặc **một phần tử `null` bên trong mảng đối số**.

Nếu nhầm hai nghĩa, một lời gọi đến phương thức chỉ nhận một tham số tham chiếu có thể phát sinh `IllegalArgumentException` vì Reflection thực tế nhìn thấy **0 đối số**; ngược lại, một phương thức không có tham số có thể bị gọi với **1 đối số thừa**.

Bài học này giải thích chính xác cách Java xử lý varargs ở bên ngoài `Method.invoke`, cách Reflection kiểm tra chữ ký của method đích và các trường hợp với `null`, array, primitive, overload và method khai báo varargs.

## 1. Nhận diện đúng ba tầng của lời gọi

Giả sử có hai phương thức:

```java
public void ping() { /* không có tham số */ }

public void label(String text) { /* có đúng một tham số String */ }
```

Đây là đoạn minh họa nằm trong một class; chương trình chạy độc lập được cung cấp ở phần sau.

**Tầng 1 — Lookup:** phải lấy đúng `Method` theo chữ ký khai báo:

```java
Method ping = Target.class.getMethod("ping");
Method label = Target.class.getMethod("label", String.class);
```

Khi tìm method, `ping` có `getParameterCount() == 0`; `label` có `getParameterCount() == 1`. Giá trị `null` lúc gọi không thay đổi chữ ký này và không khiến Reflection tự chọn một overload khác.

**Tầng 2 — Đóng gói đối số cho `invoke`:** compiler Java xử lý lời gọi đến API `Method.invoke(Object, Object...)`. Đối số sau `target` có thể được đóng gói thành mảng `Object[]`, hoặc một mảng `Object[]` có sẵn có thể được truyền nguyên làm tham số varargs.

**Tầng 3 — Reflection gọi method đích:** `Method.invoke` diễn giải danh sách đối số và so với chữ ký phương thức đích. Với API này, **mảng đối số bằng `null` được xử lý như danh sách không có đối số**. Nhưng mảng `new Object[] { null }` chứa **một** đối số có giá trị `null`.

Vì vậy:

```text
invoke(target)                      -> args: 0 phần tử
invoke(target, new Object[0])       -> args: 0 phần tử
invoke(target, (Object[]) null)     -> args: null -> coi là 0 đối số
invoke(target, (Object) null)       -> args: [null] -> 1 đối số
invoke(target, new Object[]{null})  -> args: [null] -> 1 đối số
```

Đừng nhầm `null` của biến receiver `target` với một đối số `null` của phương thức đích. Chúng thuộc hai vị trí khác nhau trong chữ ký của `Method.invoke`.

## 2. Bảng so sánh lời gọi quan trọng nhất

Giả sử `target` là đối tượng `Target` hợp lệ; `ping` đại diện `void ping()` và `label` đại diện `void label(String text)`.

| Lời gọi Reflection | Số đối số method đích nhìn thấy | Kết quả |
| --- | ---: | --- |
| `ping.invoke(target)` | 0 | Thành công |
| `ping.invoke(target, new Object[0])` | 0 | Thành công |
| `ping.invoke(target, (Object[]) null)` | 0 | Thành công |
| `ping.invoke(target, (Object) null)` | 1 | `IllegalArgumentException` (thừa đối số) |
| `label.invoke(target)` | 0 | `IllegalArgumentException` (thiếu đối số) |
| `label.invoke(target, (Object[]) null)` | 0 | `IllegalArgumentException` (thiếu đối số) |
| `label.invoke(target, (Object) null)` | 1, giá trị `null` | Thành công: `text == null` |
| `label.invoke(target, new Object[] { null })` | 1, giá trị `null` | Thành công: `text == null` |
| `label.invoke(target, (Object) "Java")` | 1, giá trị `"Java"` | Thành công: `text.equals("Java")` |

Bảng trên mô tả số đối số đến **phương thức đích**, không tính receiver `target` là một đối số của method đích.

**Cú pháp rõ ràng nên dùng:** không có đối số → `invoke(target)`; một đối số `null` → `invoke(target, (Object) null)` hoặc `invoke(target, new Object[] { null })`. Khi cần tạo đối số động, nên chủ động tạo mảng `Object[]` với số phần tử khớp chữ ký.

## 3. Vì sao `invoke(target, null)` dễ gây lỗi?

Một trong những lỗi tinh vi là viết:

```java
label.invoke(target, null);
```

Với khai báo `invoke(Object, Object...)`, tham số cuối cùng là `Object[]`. Ở Java, literal `null` tương thích với cả `Object` và `Object[]`, nhưng khi viết lời gọi trên, compiler có thể sử dụng **lời gọi varargs ở dạng truyền thẳng mảng** (fixed-arity invocation) vì `Object[]` là kiểu cụ thể của tham số cuối. Compiler cũng có thể phát cảnh báo tương tự:

```text
non-varargs call for last parameter of varargs method;
cast to Object for a varargs call
cast to Object[] for a non-varargs call
```

Hệ quả là `null` được hiểu thành **mảng `Object[]` bằng `null`**, và đối với `Method.invoke`, nó tương đương với không có đối số. Nếu method đích `label(String)` cần một tham số, Reflection sẽ ném `IllegalArgumentException` trước khi method đích được chạy.

So sánh ba biểu thức:

```java
label.invoke(target, null);             // Mơ hồ với người đọc; thường là 0 đối số.
label.invoke(target, (Object[]) null);  // Rõ ràng là 0 đối số.
label.invoke(target, (Object) null);    // Rõ ràng là 1 đối số null.
```

**Ép kiểu `(Object) null` là cách buộc compiler tạo mảng varargs một phần tử.** Ép kiểu `(Object[]) null` là cách nói rõ rằng toàn bộ mảng varargs bằng `null`. Hai cú pháp không đồng nghĩa.

Đây là quyết định tại **thời điểm biên dịch lời gọi Java tới `Method.invoke`**, chứ không phải thuật toán Reflection cố đoán người viết định truyền gì.

## 4. Ví dụ Java 17+ chạy độc lập

Lưu toàn bộ chương trình sau vào tệp `NullArgumentDemo.java`. Chương trình kiểm tra lời gọi không đối số, một đối số `null`, tham số primitive, tham số mảng và method đích khai báo `String...`.

```java
import java.lang.reflect.Method;

public class NullArgumentDemo {
    public static class Target {
        public void ping() {
            System.out.println("ping: zero");
        }

        public void label(String text) {
            System.out.println("label: "
                    + (text == null ? "<null>" : text));
        }

        public void count(int n) {
            System.out.println("count: " + n);
        }

        public void array(String[] value) {
            System.out.println("array: "
                    + (value == null ? "<null>" : value.length));
        }

        public void join(String... value) {
            System.out.println("join: "
                    + (value == null ? "<null>" : value.length));
        }
    }

    public static void main(String[] args) throws Exception {
        Target target = new Target();

        Method ping = Target.class.getMethod("ping");
        Method label = Target.class.getMethod("label", String.class);
        Method count = Target.class.getMethod("count", int.class);
        Method array = Target.class.getMethod("array", String[].class);
        Method join = Target.class.getMethod("join", String[].class);

        // Ba cách biểu thị lời gọi method không có đối số.
        ping.invoke(target);
        ping.invoke(target, (Object[]) null);
        ping.invoke(target, new Object[0]);

        // Hai cách truyền đúng một giá trị null cho label(String).
        label.invoke(target, (Object) null);
        label.invoke(target, new Object[] {null});

        // Không có đối số -> thiếu tham số String.
        try {
            label.invoke(target, (Object[]) null);
        } catch (IllegalArgumentException e) {
            System.out.println(
                    "label + zero args: IllegalArgumentException");
        }

        // Một đối số null -> thừa tham số với ping().
        try {
            ping.invoke(target, (Object) null);
        } catch (IllegalArgumentException e) {
            System.out.println(
                    "ping + one null: IllegalArgumentException");
        }

        // Một đối số null không hợp lệ với primitive int.
        try {
            count.invoke(target, (Object) null);
        } catch (IllegalArgumentException e) {
            System.out.println(
                    "count + null: IllegalArgumentException");
        }

        // String[] phải được truyền như MỘT đối số.
        array.invoke(target, (Object) new String[] {"A", "B"});
        array.invoke(target, (Object) null);

        // Method đích khai báo varargs vẫn có chữ ký String[].
        System.out.println("join isVarArgs: " + join.isVarArgs());
        join.invoke(target, (Object) new String[] {"A", "B"});

        // Reflection không tự gom hai String thành String[] cho join.
        try {
            join.invoke(target, "A", "B");
        } catch (IllegalArgumentException e) {
            System.out.println(
                    "join + two direct strings: IllegalArgumentException");
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa file:

```bash
javac NullArgumentDemo.java
java NullArgumentDemo
```

Kết quả:

```text
ping: zero
ping: zero
ping: zero
label: <null>
label: <null>
label + zero args: IllegalArgumentException
ping + one null: IllegalArgumentException
count + null: IllegalArgumentException
array: 2
array: <null>
join isVarArgs: true
join: 2
join + two direct strings: IllegalArgumentException
```

Giải thích kết quả:

1. Ba dòng `ping: zero` đến từ danh sách đối số có 0 phần tử: bỏ hẳn varargs, truyền mảng `null`, hoặc truyền mảng rỗng. Cách đầu là dễ đọc nhất.
2. Hai dòng `label: <null>` chứng minh `label(String)` thật sự được gọi, với một đối số tham chiếu có giá trị `null`.
3. `label + zero args` báo lỗi **thiếu đối số**, dù trong mã gọi có xuất hiện từ khóa `null`; vì `(Object[]) null` đại diện cho **cả mảng đối số**, không phải một phần tử.
4. `ping + one null` báo lỗi **thừa đối số** do chữ ký `ping()` không nhận tham số.
5. `count + null` báo lỗi **kiểu đối số**: `int` là primitive, không thể nhận giá trị `null` để unbox. Với `Method.invoke`, lỗi này được báo là `IllegalArgumentException`.
6. `array: 2` chứng minh `String[]` được truyền như **một đối số chứa mảng**. Nếu thiếu ép kiểu `(Object)`, compiler có thể hiểu `String[]` là chính mảng varargs `Object[]` bên ngoài và chia các phần tử thành nhiều đối số.
7. `join.isVarArgs() == true` xác nhận khai báo method đích là `join(String...)`. Tuy nhiên, `Method.invoke` không tự đóng hai đối số `"A", "B"` thành mảng `String[]` của method đích. Phải truyền chính mảng `String[]` như một đối số.

Những kết quả này minh họa hành vi ổn định của Reflection, không phụ thuộc vào việc method đích có body phức tạp hay không.

## 5. Hai loại `null` khác nhau về mặt cấu trúc

Để hiểu rõ điều quan trọng nhất, hãy tưởng tượng các đối số của method đích nằm trong một container:

```java
Object[] noArguments = new Object[0];         // []
Object[] oneNullArgument = new Object[] {null}; // [null]
Object[] nullArray = null;                     // không có mảng
```

Hai mảng đầu **không bằng nhau về độ dài**: `noArguments.length == 0` và `oneNullArgument.length == 1`. Giá trị `nullArray` không có thuộc tính `length`, nhưng API `Method.invoke` **định nghĩa `args == null` tương đương không có đối số**.

Đây là quy tắc **riêng ở tầng Reflection**: không có nghĩa Java tự xem mọi tham số varargs bằng `null` như mảng rỗng trong mọi hàm tự viết. Ví dụ, một hàm bình thường `void f(Object... values)` nếu được gọi bằng `f((Object[]) null)` thì bên trong `f`, biến `values` thực sự bằng `null`; truy cập `values.length` sẽ ném `NullPointerException`. Với `Method.invoke`, việc diễn giải `null` là danh sách đối số trống xảy ra bên trong API này.

Một chương trình muốn biểu diễn hợp đồng số lượng đối số rõ ràng nên ưu tiên:

```java
Object[] zero = {};
Object[] oneNull = {null};

ping.invoke(target, zero);
label.invoke(target, oneNull);
```

Cách này đặc biệt hữu ích khi đối số đến từ parser, router HTTP, JSON hoặc các nguồn dữ liệu động. Mảng chứa giá trị `null` vẫn là **mảng có phần tử**, không được xóa phần tử chỉ vì giá trị của nó là `null`.

## 6. Null của receiver khác null của đối số

Cú pháp Reflection là:

```java
method.invoke(receiver, args...);
```

Với method **instance**:

- `receiver` phải là một đối tượng hợp lệ của class khai báo method (hoặc subtype phù hợp).
- Nếu `receiver == null`, không có đối tượng để gọi instance method và có thể phát sinh `NullPointerException`.
- Nếu `receiver != null` nhưng một đối số của method đích bằng `null`, đây là vấn đề **tương thích tham số**, không phải receiver.

Với method **static**, đối số receiver bị bỏ qua và có thể truyền `null`:

```java
public static void notifyMessage(String message) {
    System.out.println(message == null ? "<null>" : message);
}
```

Đoạn trên là minh họa khai báo method trong một class. Nếu lấy đúng `Method` static:

```java
Method m = Demo.class.getMethod("notifyMessage", String.class);
m.invoke(null, (Object) null);
```

Trong minh họa này, giả định `Demo` là class công khai chứa method `notifyMessage` và đã import `Method`. `null` thứ nhất là **receiver của static method** (được phép), `(Object) null` thứ hai là **một đối số truyền vào method đích**. Nếu chỉ gọi `m.invoke(null)`, Reflection sẽ nhìn thấy **0 đối số** và method đích `notifyMessage(String)` thiếu tham số.

Đừng dùng số lượng literal `null` có mặt trong câu lệnh để suy ra số lượng tham số của method đích. Phải xác định **vị trí** và **kiểu biên dịch** của từng biểu thức.

## 7. Tham số primitive không thể nhận một giá trị null

Một method khai báo:

```java
public void count(int n) {
    System.out.println(n);
}
```

Chữ ký của nó là một tham số `int.class`; Reflection có thể nhận `Integer.valueOf(3)` và unbox thành `int` trong lúc gọi. Nhưng `null` không thể unbox thành `int`:

```java
Method m = Target.class.getMethod("count", int.class);
m.invoke(target, Integer.valueOf(3)); // hợp lệ: Integer -> int
m.invoke(target, (Object) null);      // IllegalArgumentException
```

Đây là **đoạn minh họa với `Target` chứa method `count(int)` và `target` là instance hợp lệ**. Lưu ý sự khác biệt giữa lời gọi trực tiếp `count((Integer) null)`, vốn có thể gây `NullPointerException` ở bước unboxing do compiler sinh ra, và `Method.invoke(target, (Object) null)`, nơi Reflection kiểm tra đối số và ném `IllegalArgumentException`.

Với tham số reference type như `String`, `Integer`, `List<?>`, một phần tử `null` có thể được truyền nếu method đích chấp nhận giá trị đó về mặt logic. Reflection chỉ kiểm tra tính hợp lệ của lời gọi theo chữ ký, không bảo đảm phần thân method sẽ không ném `NullPointerException` khi tự dereference biến tham số.

## 8. Tự kiểm tra

**Câu 1.** Method đích khai báo `void save(String code)`. Lệnh nào truyền **một giá trị `null`**?

```java
saveMethod.invoke(target, (Object) null);
```

Đúng. Nếu dùng `saveMethod.invoke(target, (Object[]) null)`, Reflection diễn giải 0 đối số và báo thiếu.

**Câu 2.** Method đích khai báo `void ping()`. `pingMethod.invoke(target, (Object) null)` có hợp lệ không?

Không. Đây là một đối số dù giá trị của nó bằng `null`; method `ping()` không nhận tham số nào. Dùng `pingMethod.invoke(target)`.

**Câu 3.** Method đích khai báo `void sum(int value)`. Tại sao `sumMethod.invoke(target, (Object) null)` sai?

Vì danh sách đối số có đúng một phần tử, nhưng `null` không thể chuyển thành `int`; lỗi là kiểu đối số, không phải số lượng.

**Câu 4.** Với `void send(String... items)`, `sendMethod.invoke(target, "a", "b")` có tự đóng gói thành `String[]` không?

Không. `Method.invoke` kiểm tra tham số đích là một `String[]`. Phải dùng `sendMethod.invoke(target, (Object) new String[]{"a", "b"})` hoặc mảng ngoài `new Object[]{new String[]{"a","b"}}`.

**Câu 5.** Một method `static void report(String s)` có thể gọi với receiver `null` và tham số `null` không?

Có: `reportMethod.invoke(null, (Object) null)`. `null` ở receiver được bỏ qua với static method, còn `(Object) null` trong danh sách đối số là giá trị truyền cho `s`.

## Ghi nhớ

- **0 đối số:** `method.invoke(target)`, `method.invoke(target, new Object[0])` hoặc `method.invoke(target, (Object[]) null)`.
- **1 đối số có giá trị `null`:** `method.invoke(target, (Object) null)` hoặc `method.invoke(target, new Object[] {null})`.
- **`invoke(target, null)` dễ bị hiểu nhầm:** với tham số varargs `Object[]`, compiler có thể truyền một mảng `null` chứ không phải một phần tử.
- **Mảng cũng là giá trị của một đối số:** dùng `(Object) myArray` nếu method đích có một tham số array.
- **Reflection không chọn lại overload, cũng không tự gom varargs của method đích.** Người gọi phải chọn đúng `Method` và tạo danh sách đối số phù hợp với chữ ký đã khai báo.

Phân biệt rõ **receiver**, **mảng `Object[]` của `Method.invoke`** và **từng phần tử trong mảng đó** sẽ loại bỏ phần lớn nhầm lẫn giữa “không có đối số” và “một đối số `null`” khi sử dụng Java Reflection.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="argument-conversions-010">Chuyển đổi giá trị trả về và ClassCastException ở mã gọi</a>

<details>
<summary>Xem chi tiết</summary>

# Chuyển đổi giá trị trả về và ClassCastException ở mã gọi khi dùng Java Reflection

## 1. Phân biệt kiểu khai báo, giá trị runtime và kiểu ở mã gọi

Khi dùng Java Reflection để gọi phương thức, API trung tâm là `java.lang.reflect.Method.invoke(Object receiver, Object... args)`. Dù phương thức đích có khai báo trả về `int`, `String`, `Number`, `List<String>` hay `void`, **kiểu trả về tĩnh của `Method.invoke` vẫn là `Object`**.

Đây là nguồn gốc của một số lỗi chuyển đổi:

- **Declared return type**: kiểu trong chữ ký phương thức, đọc qua `Method.getReturnType()`. Ví dụ `int.class` hoặc `String.class`.
- **Runtime result type**: lớp thực của đối tượng do `Method.invoke` trả về. Phương thức trả primitive `int` sẽ được boxing thành `Integer`; phương thức khai báo `Object` có thể thực sự trả `String` hoặc `Integer`.
- **Expected type của caller**: kiểu mã gọi muốn nhận khi ép kiểu, chẳng hạn `(String) result`, `(Long) result` hay `(List<String>) result`. Cast tham chiếu chỉ thành công nếu đối tượng thực tương thích.

Đề bài này tập trung vào **chuyển đổi giá trị trả về**, nguyên nhân và vị trí `ClassCastException` ở mã gọi, cách phân biệt với `InvocationTargetException`, xử lý primitive/`void`/`null`/generic, cùng chiến lược kiểm tra kiểu an toàn.

Một ví dụ ngắn:

```java
Method method = service.getClass().getMethod("count");
Object raw = method.invoke(service);     // count() khai báo int, trả 12
Integer boxed = (Integer) raw;          // Đúng: raw là Integer
long widened = boxed.longValue();      // Chuyển đổi số hợp lệ
// Long wrong = (Long) raw;            // ClassCastException
```

Đoạn trên là **minh họa bên trong phương thức** có `service`, có xử lý/khai báo ngoại lệ Reflection và import `java.lang.reflect.Method`; không phải chương trình Java độc lập.

## 2. Method.invoke thực sự trả lại loại giá trị gì?

| Return type khai báo của method đích | Giá trị `Method.invoke` trả về khi thành công | Lưu ý |
| --- | --- | --- |
| `String` | `String` hoặc `null` | Chỉ cast sang `String` khi runtime object phù hợp |
| `Object`, superclass hoặc interface | Object thuộc subtype cụ thể, hoặc `null` | Kiểu khai báo không nói chắc runtime subtype |
| `int` | `Integer` | Primitive được boxing |
| `long` | `Long` | Không chuyển `Integer` thành `Long` theo mong muốn của caller |
| `boolean` | `Boolean` | Wrapper đại diện primitive |
| `byte`, `short`, `char` | `Byte`, `Short`, `Character` | Wrapper tương ứng |
| `float`, `double` | `Float`, `Double` | Wrapper tương ứng |
| `void` | `null` | Method vẫn chạy và có thể tạo side effect |
| Kiểu tham chiếu trả `null` | `null` | Nhìn result đơn lẻ chưa biết method khai báo `void` hay return null |
| Mảng như `String[]` | Đối tượng mảng phù hợp | Không chuyển mảng thành danh sách hoặc tách thành nhiều giá trị |

`Method.getReturnType()` trả về `Class<?>` mô tả return type **khai báo sau type erasure**. Nếu cần chữ ký generic, dùng `getGenericReturnType()` trả về `Type`; metadata generic không tự kiểm chứng kiểu phần tử của dữ liệu runtime.

Một quy tắc quan trọng: phép cast tham chiếu `(TargetType) value` **không biến đổi dữ liệu**. Nếu `value` là `Integer`, `(String) value` không gọi `toString()` mà ném `ClassCastException`. Nếu `value` là `null`, cast sang một kiểu tham chiếu hợp lệ trả `null`; lỗi thường xuất hiện khi code tiếp tục dereference hoặc unbox.

## 3. Ví dụ Java 17+ hoàn chỉnh

Lưu toàn bộ chương trình dưới đây vào **`ReturnConversionDemo.java`**:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReturnConversionDemo {
    static class Api {
        public String name() {
            return "Ada";
        }

        public int count() {
            return 12;
        }

        public long total() {
            return 100L;
        }

        public void ping() {
            System.out.println("ping invoked");
        }

        public Object payload() {
            return Integer.valueOf(42);
        }

        public String explode() {
            throw new IllegalStateException("bad state");
        }
    }

    public static void main(String[] args) throws Exception {
        Api api = new Api();

        Method name = Api.class.getMethod("name");
        System.out.println("name declared: "
                + name.getReturnType().getSimpleName());
        String nameValue = (String) name.invoke(api);
        System.out.println("name value: " + nameValue);

        Method count = Api.class.getMethod("count");
        Object countResult = count.invoke(api);
        System.out.println("count declared: "
                + count.getReturnType().getSimpleName());
        System.out.println("count runtime: "
                + countResult.getClass().getSimpleName());

        Number number = (Number) countResult;
        System.out.println("count as long: " + number.longValue());

        try {
            Long bad = (Long) countResult;
        } catch (ClassCastException ex) {
            System.out.println("wrong wrapper: ClassCastException");
        }

        Object longResult = Api.class.getMethod("total").invoke(api);
        System.out.println("total runtime: "
                + longResult.getClass().getSimpleName());

        Method payload = Api.class.getMethod("payload");
        Object unknown = payload.invoke(api);
        System.out.println("payload declared: "
                + payload.getReturnType().getSimpleName());
        System.out.println("payload runtime: "
                + unknown.getClass().getSimpleName());

        try {
            String wrong = (String) unknown;
        } catch (ClassCastException ex) {
            System.out.println("payload wrong String: ClassCastException");
        }
        System.out.println("payload matches String: "
                + String.class.isInstance(unknown));

        Object resultOfVoid = Api.class.getMethod("ping").invoke(api);
        System.out.println("void returns null: " + (resultOfVoid == null));

        try {
            int countValue = (Integer) resultOfVoid;
        } catch (NullPointerException ex) {
            System.out.println("null unboxing: NullPointerException");
        }

        try {
            Api.class.getMethod("explode").invoke(api);
        } catch (InvocationTargetException ex) {
            System.out.println("target threw: "
                    + ex.getCause().getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac ReturnConversionDemo.java
java ReturnConversionDemo
```

Output:

```text
name declared: String
name value: Ada
count declared: int
count runtime: Integer
count as long: 12
wrong wrapper: ClassCastException
total runtime: Long
payload declared: Object
payload runtime: Integer
payload wrong String: ClassCastException
payload matches String: false
ping invoked
void returns null: true
null unboxing: NullPointerException
target threw: IllegalStateException
```

### Giải thích từng đoạn

1. `name()` khai báo `String`, nên `Method.getReturnType()` là `String.class`. `name.invoke(api)` trả đối tượng `String` thực sự; phép `(String)` hợp lệ.
2. `count()` khai báo primitive `int`, nhưng `invoke` đưa kết quả vào `Object` nên boxing thành `Integer`. `getReturnType()` vẫn trả `int.class`, còn `countResult.getClass()` là `Integer.class`.
3. `Number number = (Number) countResult` đúng do `Integer extends Number`. `number.longValue()` trả `12L`. Ngược lại `(Long) countResult` đòi đối tượng là `Long`, trong khi nó là `Integer`: `ClassCastException` xảy ra **ở caller**.
4. `total()` khai báo `long` và result runtime là `Long`. Wrapper phải tương ứng với primitive đã trả, không theo yêu cầu tùy ý của mã gọi.
5. `payload()` khai báo `Object` nhưng trả `Integer(42)`. Đây là return value hoàn toàn hợp lệ. Khi caller ép `(String) unknown`, cast mới thất bại; `String.class.isInstance(unknown)` giúp phát hiện trước.
6. `ping()` thực hiện side effect in dòng `ping invoked`, sau đó `Method.invoke` trả `null` do return type là `void`. Ép `(Integer) null` hợp lệ, nhưng unboxing tiếp sang `int` làm phát sinh `NullPointerException`.
7. `explode()` ném `IllegalStateException` **bên trong method đích**. `Method.invoke` chuyển lỗi này thành `InvocationTargetException`, và caller lấy nguyên nhân bằng `getCause()`.

Ví dụ sử dụng đúng chữ ký API thực; phần output được đối chiếu bằng thực thi Java 17+.

## 4. ClassCastException ở caller xuất hiện sau khi invoke hoàn thành

Xem hai câu lệnh:

```java
Object raw = method.invoke(receiver);
String text = (String) raw;
```

Giả sử phương thức đích trả về `Integer.valueOf(42)`. Câu lệnh **đầu tiên đã thực thi thành công**: method chạy, Reflection trả lại một `Integer` dưới kiểu tĩnh `Object`. Câu lệnh thứ hai mới ném `ClassCastException` vì JVM kiểm tra object thực khi thực hiện ép kiểu tham chiếu.

Nếu viết gọn:

```java
String text = (String) method.invoke(receiver);
```

thì thứ tự vẫn như vậy: gọi method → nhận `Object` → cast sang `String`. Phép cast là phần mã do **caller** yêu cầu. Trong bytecode, compiler thường sinh `checkcast` tại điểm nhận giá trị.

Đừng kết luận lỗi thuộc thư viện Reflection chỉ vì stack trace chỉ đến dòng có `invoke` và cast chung một biểu thức. Tách thành hai câu lệnh để biết chính xác ranh giới.

### Cast không đồng nghĩa chuyển đổi

```java
Object raw = Integer.valueOf(42);

Number asNumber = (Number) raw; // Thành công
Integer asInteger = (Integer) raw; // Thành công
// String bad = (String) raw;   // ClassCastException
String text = raw.toString();     // Chuỗi biểu diễn "42"
```

`toString()` là chuyển đổi thành representation dạng chuỗi, có thể có hành vi tùy kiểu và không dùng được trực tiếp với `null`. `String.valueOf(raw)` hỗ trợ `null` nhưng trả chuỗi chữ `"null"`, không phải giá trị tham chiếu `null`. Một API mong đợi **đúng đối tượng String** không nên tự tiện thay bằng chuỗi biểu diễn của object khác.

## 5. Primitive boxing, unboxing và widening

Khi gọi phương thức thông thường:

```java
int small = api.count();
long bigger = small;
```

compiler có thể widening primitive `int` sang `long` khi gán. Nhưng sau Reflection:

```java
Object raw = countMethod.invoke(api);
```

giá trị `raw` là `Integer`. Vì `Integer` và `Long` là hai class không có quan hệ kế thừa, `Long result = (Long) raw` thất bại.

Các cách đúng khi `raw` chứa `Integer`:

```java
Integer boxed = (Integer) raw;
int n = boxed;
long widened = n;

long alsoWidened = (Integer) raw; // Unbox int rồi widen long
long viaNumber = ((Number) raw).longValue();
```

Đây là các đoạn code minh họa bên trong một method có `raw` đã được nhận và bảo đảm khác `null`.

**Không đồng nhất các cách chuyển đổi:** `Number.longValue()` được hỗ trợ bởi nhiều kiểu số, nhưng với kiểu có phần thập phân hoặc số vượt khoảng biểu diễn, kết quả có thể bị cắt/mất chính xác. Cần định nghĩa cách xử lý overflow và precision nếu dữ liệu runtime không chắc chắn.

Một lỗi tương tự là cast `Double` sang `Integer` chỉ vì cả hai đại diện cho số: `(Integer) Double.valueOf(1.5)` không hợp lệ. Muốn chuyển giá trị, dùng API số hoặc converter có quy tắc cụ thể.

### Primitive return và Class.cast

Khi kiểm tra result được boxing qua `Class.cast`, dùng wrapper:

```java
Integer count = Integer.class.cast(countMethod.invoke(api));
```

`int.class.cast(result)` **không** tương đương, vì `int.class` biểu diễn primitive type, không phải loại object boxed có thể được kiểm tra bằng cast tham chiếu.

## 6. Void, null và NullPointerException

Hai trường hợp khác nhau cùng cho `raw == null`:

- Method khai báo `void`: `Method.invoke` luôn trả `null` sau khi method chạy thành công.
- Method khai báo kiểu tham chiếu như `String`: bản thân method có thể trả `null`.

Nếu muốn phân biệt, hãy đọc metadata:

```java
if (method.getReturnType() == void.class) {
    // Method không khai báo trả về một giá trị.
}
```

Còn nếu caller cần chuyển result sang primitive thì phải xử lý null trước:

```java
Object raw = method.invoke(receiver);
if (raw == null) {
    throw new IllegalStateException("Expected a non-null number");
}
if (!(raw instanceof Number number)) {
    throw new IllegalArgumentException("Expected Number");
}
int value = number.intValue();
```

Đoạn trên là mẫu minh họa trong phương thức với `method`, `receiver` hợp lệ. `(Integer) null` tự nó trả `null`, nhưng việc gán vào `int` gọi unboxing và ném `NullPointerException`. Đó là **lỗi khác `ClassCastException`**.

Không gọi `raw.getClass()` khi raw có thể bằng null. Khi chuẩn đoán, dùng `raw == null ? "null" : raw.getClass().getName()`.

## 7. Phân biệt lỗi ở caller và InvocationTargetException

Các lỗi quanh Reflection xảy ra ở những giai đoạn khác nhau:

| Tình huống | Nơi phát sinh | Ngoại lệ |
| --- | --- | --- |
| `getMethod` không tìm được chữ ký | Tra cứu metadata | `NoSuchMethodException` |
| Không có quyền gọi phương thức | Kiểm tra truy cập | Thường `IllegalAccessException` |
| Đối số truyền cho `invoke` không phù hợp | Giai đoạn chuẩn bị thực thi | Thường `IllegalArgumentException` |
| Method đích tự ném `IllegalStateException` | Bên trong method đích | `InvocationTargetException` bọc nguyên nhân |
| Method đích tự ném `ClassCastException` | Bên trong method đích | `InvocationTargetException` bọc nguyên nhân |
| Cast `(String) raw` sau khi `invoke` trả `Integer` | Mã gọi sau khi method trả về | `ClassCastException` trực tiếp |
| Unboxing result `null` thành `int` | Mã gọi sau khi method trả về | `NullPointerException` |

Điểm cần nhớ: **`InvocationTargetException` chỉ biểu diễn exception/error thoát ra từ code của method đích** (trong ngữ cảnh invocation). Nếu caller tự cast sai **sau khi** method đã trả về, Reflection không thể quay lại bọc lỗi đó.

Một cách phân tách trách nhiệm:

```java
Object raw;
try {
    raw = method.invoke(receiver);
} catch (java.lang.reflect.InvocationTargetException ex) {
    Throwable targetCause = ex.getCause();
    throw new IllegalStateException("Target method failed", targetCause);
}

// Sau khi invoke thành công, chuyển đổi là trách nhiệm caller.
if (!(raw instanceof String text)) {
    throw new IllegalArgumentException("Expected a String result");
}
System.out.println(text);
```

Đây là **đoạn minh họa**, đặt trong một phương thức đã xử lý hoặc khai báo các exception khác như `IllegalAccessException`. Chính sách ở ví dụ yêu cầu String khác null; hệ thống cho phép return null phải có nhánh riêng.

### Hai ví dụ dễ bị đánh đồng

- `Integer actual = ...; String text = (String) (Object) actual;` gây `ClassCastException` trực tiếp trong mã thực hiện cast.
- Nếu method đích chứa cast sai tương tự rồi bị gọi bằng `Method.invoke`, caller sẽ thấy `InvocationTargetException` với `getCause() instanceof ClassCastException`.

Nhìn **stack trace và chuỗi nguyên nhân** để xác định nơi lỗi xuất phát.

## 8. Kiểu khai báo không phải kiểu runtime

Xét:

```java
class Base {
    public Object value() { return "base"; }
}
class Derived extends Base {
    @Override
    public String value() { return "derived"; }
}
```

Một đoạn trong phương thức có xử lý ngoại lệ:

```java
Method m = Base.class.getMethod("value");
Object raw = m.invoke(new Derived());

System.out.println(m.getReturnType().getSimpleName()); // Object
System.out.println(raw.getClass().getSimpleName());    // String
```

`Method.getReturnType()` mô tả metadata của method được lấy từ `Base`; runtime thực hiện dispatch theo instance `Derived` nên kết quả là `String`. Cast sang `String` hợp lệ vì **đối tượng thực** là String, mặc dù return type khai báo ở `Base` là `Object`.

Tương tự, khi return type là interface hoặc superclass, có thể nhận subtype hợp lệ. Vì vậy, nếu mục tiêu là cast xuống subtype cụ thể, luôn xem xét `instanceof` hoặc `Class.isInstance`.

### Bridge method và generic return

Với generic hoặc covariant return, compiler có thể sinh bridge method; Reflection có thể liệt kê các method này. `Method.isBridge()` và `Method.isSynthetic()` cho biết trường hợp tương ứng. Khi có nhiều method cùng tên, không chọn bừa phần tử đầu tiên từ `getDeclaredMethods()`: thứ tự không được bảo đảm, và chữ ký hoặc return metadata có thể khác.

Nếu method đích hoặc bridge tự cast sai, lỗi đi qua cơ chế wrapper của `Method.invoke`; nếu caller cast kết quả sai, lỗi nằm sau lời gọi. Phân biệt bằng stack trace và `InvocationTargetException.getCause()`.

## 9. ClassCastException và generic type erasure

Một method có thể khai báo `List<String>` và `getGenericReturnType()` mô tả được generic signature, nhưng runtime không tự xác minh tất cả phần tử thực sự là String.

Ví dụ đoạn code minh họa:

```java
Object raw = method.invoke(receiver);

if (raw instanceof java.util.List<?> list) {
    System.out.println("Items: " + list.size());
}
```

Cách kiểm tra trên biết `raw` thực sự là List, nhưng không bảo đảm mỗi item là String. Không thể viết `raw instanceof List<String>` vì parameterized type đó không reifiable tại runtime.

Một cast không kiểm tra phần tử có thể thành công ban đầu rồi lỗi xuất hiện muộn:

```java
@SuppressWarnings("unchecked")
java.util.List<String> values =
        (java.util.List<String>) raw;

// Khi đọc phần tử, compiler có thể chèn cast thành String.
String first = values.get(0);
```

Nếu phần tử đầu tiên thực là Integer, lỗi `ClassCastException` có thể xuất hiện ở dòng nhận `first`, **không phải** ở phép cast nguyên List. Vì thế với dữ liệu từ plugin, JSON mapper hoặc API runtime, nên dùng `List<?>` rồi validate từng phần tử khi cần bảo đảm kiểu.

## 10. Generic helper trả T chưa chắc kiểm tra T

Hàm tiện ích sau trông có vẻ trả đúng kiểu:

```java
@SuppressWarnings("unchecked")
static <T> T call(Method method, Object target)
        throws ReflectiveOperationException {
    return (T) method.invoke(target);
}
```

Nhưng type erasure khiến `T` không được kiểm tra như `String` ở thân hàm. Nếu mã gọi viết:

```java
String result = call(method, target);
```

compiler có thể đưa phép `checkcast String` ở **vị trí mã gọi sử dụng kết quả**. Khi method trả Integer, `ClassCastException` vẫn có thể xảy ra phía caller, bất chấp generic helper đã return.

Một thiết kế rõ ràng hơn là truyền cả runtime type token:

```java
static <T> T callAs(
        Method method, Object target, Class<T> expected)
        throws ReflectiveOperationException {
    Object raw = method.invoke(target);
    return expected.cast(raw);
}
```

Mẫu này yêu cầu caller chọn type cụ thể, ví dụ `String.class` hoặc `Integer.class` (đối với primitive int đã boxing). `Class.cast(null)` vẫn trả null; nếu không chấp nhận null thì thêm kiểm tra riêng. Nếu object có generic parameter như `List<String>`, chỉ một `Class<List>` không kiểm tra được toàn bộ phần tử.

## 11. Kiểm tra kiểu an toàn bằng instanceof và Class.cast

### 11.1. Pattern matching cho instanceof (Java 17)

```java
static String requireString(Object raw) {
    if (raw instanceof String text) {
        return text;
    }
    throw new IllegalArgumentException(
            "Expected String; actual="
                    + (raw == null ? "null" : raw.getClass().getName()));
}
```

Đây là một phương thức tiện ích đầy đủ về cú pháp Java khi đặt trong một class. Nó coi null là không hợp lệ theo chủ ý. Một API cho phép null cần quy tắc khác.

### 11.2. Class.isInstance và Class.cast

```java
static <T> T checkedCast(Object value, Class<T> expected) {
    if (!expected.isInstance(value)) {
        throw new IllegalArgumentException("Unexpected return type");
    }
    return expected.cast(value);
}
```

Đây là phương thức tiện ích có thể đặt trong class. `expected.isInstance(null)` là false, nên nó cũng từ chối null. `Class.cast` kiểm tra type thực và có thể ném `ClassCastException` khi giá trị không thuộc class mong muốn.

Khi muốn **chuyển đổi** một kiểu số sang kiểu số khác (ví dụ Integer → long), chỉ kiểm tra class không đủ; phải định nghĩa rõ conversion, overflow, null, độ chính xác.

### 11.3. Xử lý String khác biểu diễn String

Nếu hợp đồng yêu cầu chính xác return type String, dùng cast đã kiểm tra. Nếu hợp đồng là “hiển thị thông tin” và chấp nhận bất cứ object nào, dùng converter riêng hoặc `String.valueOf` với hành vi null đã được thống nhất. Không âm thầm biến số, collection hoặc object nghiệp vụ thành String khi phía gọi yêu cầu một thực thể có kiểu.

## 12. Mảng, class loader và các tình huống đặc biệt

### Mảng là object

Method trả `String[]` sẽ làm `Method.invoke` trả một đối tượng mảng `String[]`:

```java
Object raw = arrayMethod.invoke(receiver);
if (raw instanceof String[] values) {
    System.out.println(values.length);
}
```

Đoạn code minh họa giả định method đích phù hợp. Mảng `int[]` không phải `Integer[]`; Reflection không tự boxing toàn bộ phần tử của mảng trả về thành một mảng wrapper mới. Kiểu mảng thực quyết định cast có hợp lệ hay không.

### Hai class cùng tên có thể vẫn không cast được

Trong môi trường plugin, class loader hoặc hot reload, hai class có **cùng binary name** nhưng được load bởi hai class loader định nghĩa khác nhau có thể là hai kiểu runtime không tương thích. `ClassCastException` khi đó không nhất thiết do tên hiển thị khác nhau.

Để chẩn đoán, có thể xem `raw.getClass()`, `raw.getClass().getClassLoader()` và class loader của expected type. Cách sửa là thống nhất ranh giới type/class loader (chẳng hạn API/interface dùng chung), không dùng cast mạnh hơn.

### Method.invoke, Field.get và Constructor.newInstance khác nhau

| API | Giá trị trả về | Ý nghĩa |
| --- | --- | --- |
| `Method.invoke(...)` | `Object` | Primitive method return được boxing; `void` thành null |
| `Field.get(...)` | `Object` | Field primitive được boxing |
| `Field.getInt(...)` | `int` | API đọc primitive chuyên biệt khi chuyển đổi hợp lệ |
| `Constructor<T>.newInstance(...)` | `T` | Tạo object của lớp khai báo khi được phép, không có void return |

Không tổng quát hóa rằng mọi API Reflection có chung quy tắc chuyển đổi. Bài này tập trung vào **return value của Method.invoke**.

## 13. Quy trình chẩn đoán lỗi trong thực tế

1. **Tách lệnh invoke khỏi phép cast** để xác định điểm ném lỗi: `Object raw = method.invoke(target);` rồi mới cast.
2. **Kiểm tra method thực sự được chọn** qua `getDeclaringClass()`, `getName()`, `getParameterTypes()` và `getReturnType()`. Overload hoặc bridge có thể làm caller dùng sai metadata.
3. **Xem giá trị null trước khi gọi getClass()**. Nếu `getReturnType() == void.class`, không mong đợi giá trị.
4. **Nếu khác null, kiểm tra runtime type** và quan hệ subtype/interface với expected type. Với primitive, nhớ wrapper tương ứng.
5. **Nếu cần số kiểu khác, chuyển đổi rõ ràng** thay vì cast wrapper sai. Kiểm soát overflow/precision.
6. **Xem wrapper của exception**. `InvocationTargetException.getCause()` biểu diễn lỗi ở method đích; `ClassCastException` sau phép cast là lỗi caller.
7. **Nếu generic hoặc class loader phức tạp**, kiểm tra element type, bridge method và class loader định nghĩa type.

Ví dụ log chẩn đoán chỉ dựa trên metadata/kiểu (tránh log dữ liệu nhạy cảm):

```java
Object raw = method.invoke(receiver);
String declared = method.getReturnType().getTypeName();
String actual = raw == null ? "null" : raw.getClass().getName();
System.out.println("declared=" + declared + ", actual=" + actual);
```

Đây là đoạn minh họa trong method đã có biến phù hợp và xử lý ngoại lệ. Nếu kết quả là Integer khi mã cần String, bước sửa nằm ở **hợp đồng kiểu/chuyển đổi của caller**, không nằm ở việc sửa `Method.invoke`.

## 14. Lỗi phổ biến

| Sai lầm | Hậu quả | Cách sửa |
| --- | --- | --- |
| Tin rằng method trả int thì `invoke` trả primitive int | Nhầm wrapper | Chờ Integer và unbox |
| Cast `Integer` sang `Long` để widen | ClassCastException | Unbox rồi widening hoặc dùng Number |
| Cast mọi object thành String | ClassCastException | Kiểm tra instanceof; dùng converter nếu thật sự cần |
| Unbox return của void | NullPointerException | Xét `getReturnType() == void.class` |
| Không kiểm tra null trước `getClass()` | NullPointerException | Nhánh null rõ ràng |
| Chỉ nhìn return type khai báo Object để đoán String | Sai runtime type | Kiểm tra giá trị thực |
| Xem mọi ClassCastException là lỗi của method đích | Chẩn đoán sai | Phân biệt lỗi trực tiếp và cause của InvocationTargetException |
| Dùng `(T)` generic và nghĩ runtime type được validate | ClassCastException muộn ở caller | Dùng runtime type token và kiểm tra |
| Cast thành `List<String>` mà không kiểm tra phần tử | ClassCastException khi đọc phần tử | Dùng `List<?>` và validate |
| Giả định hai class cùng tên luôn cast được | Lỗi với class loader khác nhau | Kiểm tra class identity và loader |

## 15. Câu hỏi tự kiểm tra

**1.** Method `int count()` trả 12. Đối tượng runtime từ `Method.invoke()` có class nào?

**Đáp án:** `Integer.class`. Primitive `int` được boxing vào kết quả kiểu `Object`.

**2.** Tại sao `Long number = (Long) method.invoke(target)` lỗi khi method trả `int`?

**Đáp án:** Runtime value là Integer, không phải Long. Cast tham chiếu giữa hai wrapper không thực hiện widening primitive. Hãy unbox Integer rồi widen sang long.

**3.** Method `void ping()` vẫn in thông báo. `invoke` trả gì?

**Đáp án:** `null`, mặc dù method đã chạy và có side effect.

**4.** Vì sao `int n = (Integer) raw` ném NullPointerException nếu raw bằng null?

**Đáp án:** Cast tham chiếu null sang Integer hợp lệ; lỗi phát sinh ở bước unboxing Integer null thành int.

**5.** Khi nào một `ClassCastException` bị bọc trong `InvocationTargetException`?

**Đáp án:** Khi chính method đích (hoặc code nó gọi) ném ClassCastException và lỗi đó thoát ra trong quá trình invoke. Nếu caller tự cast sai sau khi invoke trả về, lỗi xuất hiện trực tiếp ở caller.

**6.** `Method.getReturnType()` trả Object nhưng `raw.getClass()` trả String. Có mâu thuẫn không?

**Đáp án:** Không. String là subtype của Object, nên object thực có thể cụ thể hơn return type đã khai báo.

**7.** `List<?>` có chứng minh tất cả phần tử là String không?

**Đáp án:** Không. Generic bị erasure; cần kiểm tra từng phần tử hoặc hợp đồng dữ liệu đáng tin cậy.

**8.** Một helper `<T> T call(...)` có bảo đảm giá trị đã được cast đúng String bên trong helper không?

**Đáp án:** Không. T bị type erasure; compiler có thể đặt runtime cast ở điểm caller nhận kết quả. Có thể truyền `Class<T>`, kiểm tra bằng `Class.cast` và xử lý null/generic riêng.

**Ghi nhớ:** `Method.invoke` trả `Object`, boxing primitive và trả null cho void. Java không tự chuyển object sang kiểu caller muốn nhận. `ClassCastException` sau khi invoke trả về thường xuất phát từ **phép cast ở caller**. Hãy đối chiếu declared return type, runtime result type và expected type, đồng thời phân biệt nó với `InvocationTargetException` khi method đích thật sự ném lỗi.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="argument-conversions-011">Mảng primitive không thay thế mảng wrapper</a>

<details>
<summary>Xem chi tiết</summary>

# Mảng primitive không thay thế mảng wrapper trong Java Reflection

## 1. Bản chất: boxing từng phần tử không biến đổi kiểu của cả mảng

Trong Java, các kiểu mảng `int[]` và `Integer[]` là **hai kiểu runtime khác nhau**:

- `int[]` lưu các phần tử **primitive `int`**, có giá trị mặc định `0`.
- `Integer[]` lưu **tham chiếu tới đối tượng `Integer`**, có thể chứa `null` và có giá trị mặc định `null`.
- Java hỗ trợ **boxing/unboxing từng giá trị** giữa `int` và `Integer`, nhưng **không tự động chuyển nguyên mảng** `int[]` thành `Integer[]` hoặc ngược lại.
- Vì khác kiểu mảng, chúng **không thể thay thế nhau** trong tham số, biến gán, kiểu trường dữ liệu hoặc chữ ký tra cứu Reflection.

```java
int value = 7;
Integer boxedValue = value; // Boxing phần tử: hợp lệ.
int unboxedValue = boxedValue; // Unboxing phần tử: hợp lệ.

int[] primitives = {1, 2, 3};
Integer[] wrappers = {1, 2, 3}; // Từng phần tử literal được boxing.

// MÃ MINH HỌA KHÔNG BIÊN DỊCH ĐƯỢC:
// Integer[] wrong1 = primitives;
// int[] wrong2 = wrappers;
```

Không có quy tắc Java nào cho phép `Integer[] wrappers = primitives` bằng một chuyển đổi boxing ngầm định ở cấp mảng. Kể cả ép kiểu thông qua `Object` cũng không thể biến đổi dữ liệu:

```java
// Ví dụ minh họa: câu lệnh này biên dịch nhưng thất bại lúc chạy.
// Integer[] invalid = (Integer[]) (Object) primitives;
// Runtime: ClassCastException.
```

Đặc biệt trong Reflection, việc tìm đúng tên phương thức nhưng truyền **kiểu mảng sai** thường dẫn tới `NoSuchMethodException` lúc tra cứu hoặc `IllegalArgumentException` lúc gọi.

## 2. Nhận diện hai kiểu bằng Class

```java
Class<?> primitiveArrayType = int[].class;
Class<?> wrapperArrayType = Integer[].class;

System.out.println(primitiveArrayType.getTypeName()); // int[]
System.out.println(wrapperArrayType.getTypeName());   // java.lang.Integer[]
System.out.println(primitiveArrayType.isArray());     // true
System.out.println(wrapperArrayType.isArray());       // true
System.out.println(primitiveArrayType.getComponentType().isPrimitive()); // true
System.out.println(wrapperArrayType.getComponentType().isPrimitive());   // false
```

Đây là **đoạn minh họa trong một phương thức Java**, không phải chương trình chạy độc lập. So sánh `primitiveArrayType.equals(wrapperArrayType)` trả `false`. Cần lưu ý rằng **bản thân `int[]` là một kiểu tham chiếu** (do đó `int[].class.isPrimitive()` là `false`), còn **kiểu thành phần** `int.class` mới là primitive.

| Kiểu | `Class.getTypeName()` | `getComponentType()` | Chứa `null` như một phần tử? |
| --- | --- | --- | --- |
| `int[]` | `int[]` | `int.class` | Không |
| `Integer[]` | `java.lang.Integer[]` | `Integer.class` | Có |
| `long[]` | `long[]` | `long.class` | Không |
| `Long[]` | `java.lang.Long[]` | `Long.class` | Có |
| `boolean[]` | `boolean[]` | `boolean.class` | Không |
| `Boolean[]` | `java.lang.Boolean[]` | `Boolean.class` | Có |

Mọi cặp primitive/wrapper tương ứng đều có cùng nguyên tắc: `double[]` khác `Double[]`, `char[]` khác `Character[]`, v.v.

`Class.getName()` dùng định dạng tên nội bộ khác với `getTypeName()`, ví dụ `int[].class.getName()` trả `"[I"`, còn `Integer[].class.getName()` trả `"[Ljava.lang.Integer;"`. Dùng `getTypeName()` để hiển thị dễ đọc; không suy diễn hai mảng tương thích chỉ vì tên thành phần có vẻ tương ứng.

## 3. Reflection tra cứu phương thức: chữ ký phải đúng loại mảng

Giả sử lớp có:

```java
public static int sum(int[] values) {
    return java.util.Arrays.stream(values).sum();
}
```

Cách tra cứu chính xác:

```java
// Phần minh họa trong chương trình có lớp Target.
java.lang.reflect.Method sum =
        Target.class.getMethod("sum", int[].class);
```

Cách sai:

```java
// Ném NoSuchMethodException: method sum(Integer[]) không tồn tại.
Target.class.getMethod("sum", Integer[].class);
```

`getMethod(name, Class<?>... parameterTypes)` và `getDeclaredMethod(...)` khớp **chính xác từng `Class` của tham số đã khai báo**. Khi tham số là `int[]`, Reflection yêu cầu `int[].class`; không phân tích phần tử rồi áp dụng boxing thành `Integer[].class`. Ngược lại, với phương thức nhận `Integer[]`, phải tra bằng `Integer[].class`.

Tham số đa chiều cũng tương tự: `int[][].class` khác `Integer[][].class`; không tra cứu `process(int[][])` bằng `Integer[][].class`.

### Phân biệt lookup với Method.invoke

Sau khi lấy được `Method`, đoạn gọi đúng là:

```java
int[] values = {1, 2, 3};
Object answer = sum.invoke(null, (Object) values); // Phương thức static.
```

Ở đây `(Object) values` bảo đảm `values` là **một đối số duy nhất** của phương thức đích vì `Method.invoke(Object, Object...)` chính nó là API varargs. Với một `Integer[]` không được cast thành `Object`, Java có thể xử lý mảng `Object[]` ấy như danh sách đối số varargs, gây ra lỗi số lượng đối số ngoài lỗi kiểu. Cast rõ ràng giúp tách biệt hai vấn đề.

Nếu dùng `Integer[] wrappers = {1, 2, 3}` rồi gọi:

```java
// Có Method sum đại diện cho sum(int[]).
sum.invoke(null, (Object) wrappers); // IllegalArgumentException.
```

**Không có chuyển đổi cả mảng** ở bước `invoke`: dù `Integer` có thể unbox thành `int` khi một tham số trực tiếp là `int`, tham số `int[]` không nhận `Integer[]`. `invoke` không tự chọn overload khác và không tự dựng mảng mới.

## 4. Constructor và Field cũng không đổi mảng primitive thành wrapper

Quy tắc tương tự áp dụng khi Reflection làm việc với constructor và trường dữ liệu.

### Constructor

```java
public Target(int[] values) {
    this.primitiveField = values;
}
```

Tra cứu:

```java
Constructor<Target> constructor =
        Target.class.getConstructor(int[].class); // Đúng.
```

Dùng `Target.class.getConstructor(Integer[].class)` sẽ ném `NoSuchMethodException` nếu không có constructor `(Integer[])`. Sau khi tìm đúng, `constructor.newInstance((Object) new int[] {1, 2})` hợp lệ. Truyền `Integer[]` vào constructor nhận `int[]` sẽ ném `IllegalArgumentException` do đối số sai kiểu.

### Field

Giả sử:

```java
public int[] primitiveField;
```

`Field.getType()` trả `int[].class`. Lệnh `field.set(target, new int[] {4, 5})` hợp lệ nếu caller được quyền ghi field đó, nhưng `field.set(target, new Integer[] {4, 5})` không hợp lệ và ném `IllegalArgumentException`. Reflection không thể gán mảng wrapper vào slot chứa mảng primitive.

Trong chương trình ở phần tiếp theo, cả tra cứu Method, Constructor và gán Field đều được minh họa bằng một lớp `Target` duy nhất.

## 5. Chương trình hoàn chỉnh Java 17+: đối chiếu các trường hợp

Lưu nguyên mã vào tệp **`PrimitiveWrapperArraysDemo.java`**:

```java
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

public class PrimitiveWrapperArraysDemo {
    public static class Target {
        public int[] primitiveField;

        public Target(int[] input) {
            primitiveField = input;
        }

        public static int sum(int[] values) {
            return Arrays.stream(values).sum();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("same class: "
                + int[].class.equals(Integer[].class));
        System.out.println("int[]: " + int[].class.getTypeName());
        System.out.println("Integer[]: " + Integer[].class.getTypeName());
        System.out.println("int component primitive: "
                + int[].class.getComponentType().isPrimitive());
        System.out.println("Integer component primitive: "
                + Integer[].class.getComponentType().isPrimitive());
        System.out.println("Object[] accepts int[]: "
                + Object[].class.isAssignableFrom(int[].class));

        int[] ints = {1, 2, 3};
        Integer[] wrappers = {1, 2, 3};
        Method method = Target.class.getMethod("sum", int[].class);

        System.out.println("method: "
                + method.getParameterTypes()[0].getTypeName());
        System.out.println("sum(int[]): "
                + method.invoke(null, (Object) ints));

        try {
            Target.class.getMethod("sum", Integer[].class);
        } catch (NoSuchMethodException e) {
            System.out.println("lookup Integer[]: NoSuchMethodException");
        }
        try {
            method.invoke(null, (Object) wrappers);
        } catch (IllegalArgumentException e) {
            System.out.println("invoke Integer[]: IllegalArgumentException");
        }

        Constructor<Target> constructor =
                Target.class.getConstructor(int[].class);
        Target target =
                constructor.newInstance((Object) new int[] {4, 5});
        System.out.println("constructor: "
                + Arrays.toString(target.primitiveField));
        try {
            Target.class.getConstructor(Integer[].class);
        } catch (NoSuchMethodException e) {
            System.out.println(
                    "constructor Integer[]: NoSuchMethodException");
        }

        Field field = Target.class.getField("primitiveField");
        try {
            field.set(target, wrappers);
        } catch (IllegalArgumentException e) {
            System.out.println(
                    "field.set(Integer[]): IllegalArgumentException");
        }
        field.set(target, ints);
        System.out.println("field after set(int[]): "
                + Arrays.toString(target.primitiveField));

        System.out.println("Array.get int[] element class: "
                + Array.get(ints, 0).getClass().getSimpleName());
        Array.set(ints, 0, Integer.valueOf(9));
        System.out.println("Array.set int[] from Integer: "
                + Arrays.toString(ints));
        try {
            Array.set(ints, 0, Long.valueOf(9));
        } catch (IllegalArgumentException e) {
            System.out.println(
                    "Array.set int[] from Long: IllegalArgumentException");
        }

        System.out.println("Array.newInstance int[]: "
                + Array.newInstance(int.class, 2).getClass().getTypeName());
        System.out.println("Array.newInstance Integer[]: "
                + Array.newInstance(Integer.class, 2)
                        .getClass().getTypeName());

        Integer[] boxed =
                Arrays.stream(ints).boxed().toArray(Integer[]::new);
        System.out.println("boxed: " + Arrays.toString(boxed));
        int[] unboxed =
                Arrays.stream(boxed).mapToInt(Integer::intValue).toArray();
        System.out.println("unboxed: " + Arrays.toString(unboxed));

        wrappers[0] = null;
        try {
            Arrays.stream(wrappers)
                    .mapToInt(Integer::intValue).toArray();
        } catch (NullPointerException e) {
            System.out.println("unbox null: NullPointerException");
        }

        Number[] numbers = new Integer[1];
        try {
            numbers[0] = Double.valueOf(2.5);
        } catch (ArrayStoreException e) {
            System.out.println("covariance: ArrayStoreException");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac PrimitiveWrapperArraysDemo.java
java PrimitiveWrapperArraysDemo
```

Stdout chính xác trong môi trường Java 17+ thông thường:

```text
same class: false
int[]: int[]
Integer[]: java.lang.Integer[]
int component primitive: true
Integer component primitive: false
Object[] accepts int[]: false
method: int[]
sum(int[]): 6
lookup Integer[]: NoSuchMethodException
invoke Integer[]: IllegalArgumentException
constructor: [4, 5]
constructor Integer[]: NoSuchMethodException
field.set(Integer[]): IllegalArgumentException
field after set(int[]): [1, 2, 3]
Array.get int[] element class: Integer
Array.set int[] from Integer: [9, 2, 3]
Array.set int[] from Long: IllegalArgumentException
Array.newInstance int[]: int[]
Array.newInstance Integer[]: java.lang.Integer[]
boxed: [9, 2, 3]
unboxed: [9, 2, 3]
unbox null: NullPointerException
covariance: ArrayStoreException
```

**Đọc kết quả theo từng cơ chế:**

1. `int[].class.equals(Integer[].class)` trả `false`, và `getComponentType().isPrimitive()` phân biệt hai phần tử.
2. `getMethod("sum", int[].class)` đúng; `getMethod("sum", Integer[].class)` không tìm thấy chữ ký.
3. `Method.invoke` nhận `int[]` làm một đối số nhưng từ chối `Integer[]`, không unbox toàn bộ mảng.
4. Constructor nhận `int[]` và field khai báo `int[]` cũng từ chối thay thế bằng `Integer[]`.
5. `Array.get(ints, 0)` **boxing giá trị phần tử riêng lẻ** thành `Integer`; `Array.set(ints, 0, Integer.valueOf(9))` có thể unbox phần tử rồi ghi vào mảng `int[]`.
6. `Array.set(ints, 0, Long.valueOf(9))` không thực hiện chuyển đổi **narrowing `long` sang `int`**. Hỗ trợ boxing/unboxing ở **vị trí phần tử** không chứng minh có khả năng đổi kiểu của **toàn bộ mảng**.
7. `Array.newInstance(int.class, 2)` tạo `int[]`; `Array.newInstance(Integer.class, 2)` tạo `Integer[]`. Loại phần tử đưa vào quyết định loại mảng được tạo.
8. `IntStream.boxed()` và `Stream.mapToInt(...)` là hai cách **chuyển từng phần tử có chủ đích** để tạo mảng mới.
9. Mảng wrapper có thể chứa `null`, nên unbox `null` có thể ném `NullPointerException`.

## 6. Vì sao Array.get và Array.set có vẻ “boxing được”?

API `java.lang.reflect.Array` thao tác trên **phần tử**, không đổi loại mảng:

```java
int[] values = {3, 4};

Object element = Array.get(values, 0); // Integer(3) do boxing.
Array.set(values, 1, Integer.valueOf(7)); // Unbox Integer thành int.
```

`Array.get` trả `Object` nên phần tử primitive phải được boxing thành wrapper phù hợp; các phương thức `Array.getInt`, `Array.getLong`, `Array.setInt`, ... cung cấp các thao tác chuyên biệt cho primitive, với các quy tắc chuyển đổi widening được hỗ trợ. `Array.set` nhận đối tượng phần tử và có thể unbox/widen khi tương thích; nó **không chấp nhận mọi wrapper** và không tự làm narrowing tùy ý.

Các trường hợp cần tách biệt:

| Thao tác | Kết quả |
| --- | --- |
| `Array.get(new int[] {5}, 0)` | Một `Integer` biểu diễn `5` |
| `Array.set(intArray, 0, Integer.valueOf(5))` | Hợp lệ |
| `Array.set(intArray, 0, Short.valueOf((short) 5))` | Hợp lệ: unbox `short`, widening sang `int` |
| `Array.set(intArray, 0, Long.valueOf(5L))` | `IllegalArgumentException` vì không narrowing `long` về `int` |
| `Array.set(intArray, 0, null)` | `IllegalArgumentException` vì `int` không thể chứa `null` |
| `Array.set(integerArray, 0, null)` | Hợp lệ: `Integer[]` có thể chứa `null` |
| `Array.set(integerArray, 0, Long.valueOf(5L))` | `IllegalArgumentException`: `Long` không gán được cho `Integer` |

**Kết luận của phần này:** phần tử có thể được boxing/unboxing theo quy tắc API, còn **`int[]` và `Integer[]` vẫn là hai đối tượng mảng thuộc hai lớp khác nhau**.

## 7. Chuyển mảng đúng cách khi cần tương tác với API khác

Nếu bên cung cấp trả `int[]` nhưng API nhận `Integer[]`, hãy **chuyển đổi tường minh** bằng cách tạo mảng mới và boxing từng phần tử.

### Từ int[] sang Integer[]

```java
int[] source = {10, 20, 30};
Integer[] boxed = java.util.Arrays.stream(source)
        .boxed()
        .toArray(Integer[]::new);
```

### Từ Integer[] sang int[]

```java
Integer[] source = {10, 20, 30};
int[] primitives = java.util.Arrays.stream(source)
        .mapToInt(Integer::intValue)
        .toArray();
```

Hai đoạn trên là **mảnh mã đặt trong phương thức**, không phải chương trình độc lập. Kết quả là **mảng mới** và sao chép giá trị phần tử; không có phép cast thay thế.

Với `Integer[]` có `null`, cần xác định quy tắc rõ ràng: báo lỗi, loại bỏ phần tử hoặc thay bằng giá trị mặc định có ý nghĩa với nghiệp vụ. Ví dụ `mapToInt(Integer::intValue)` sẽ thất bại khi gặp `null`; không nên ngầm biến mọi `null` thành `0` nếu ứng dụng phân biệt hai trạng thái.

Khi dùng Reflection để gọi method nhận `int[]`, hãy chuyển đổi **trước** rồi truyền đúng mảng:

```java
// Minh họa trong phương thức đã có Integer[] source và Method method.
int[] converted = java.util.Arrays.stream(source)
        .mapToInt(Integer::intValue)
        .toArray();
Object result = method.invoke(null, (Object) converted);
```

Đây là lựa chọn có chủ đích và có chi phí phân bổ bộ nhớ/O(n) theo số phần tử; Reflection **không tự động thực hiện** bước này.

## 8. Mảng primitive không có cùng covariance với mảng wrapper

Mảng kiểu tham chiếu trong Java là *covariant*: vì `Integer` extends `Number`, một `Integer[]` có thể gán cho biến `Number[]`. JVM vẫn kiểm tra khi ghi phần tử:

```java
Number[] array = new Integer[1];
array[0] = Double.valueOf(3.14); // ArrayStoreException lúc chạy.
```

Ngược lại:

```java
int[] primitive = {1, 2};

// Không biên dịch:
// Object[] elements = primitive;
// Number[] numbers = primitive;

Object singleObject = primitive; // Hợp lệ: mọi mảng đều là Object.
```

Một `int[]` có thể lưu như **một đối tượng** trong biến `Object` hoặc trong **một phần tử** của `Object[]`, nhưng **bản thân nó không phải `Object[]`**: các giá trị `int` không phải tham chiếu `Object`.

Ví dụ:

```java
Object[] container = {new int[] {1, 2}, new Integer[] {3, 4}};
// container có hai phần tử, mỗi phần tử là một đối tượng mảng.
```

Tương tự, `List<int[]>` là danh sách các mảng `int[]`; nó không tương đương `List<Integer>` và không chuyển thành `Integer[]`.

## 9. Varargs, Arrays.asList và các bẫy liên quan

### 9.1. Parameter `int...` vẫn là `int[]`

```java
public static void printAll(int... numbers) {
    System.out.println(java.util.Arrays.toString(numbers));
}
```

Ở runtime, `int...` là một tham số có kiểu `int[]`; tra cứu:

```java
// Trong ngữ cảnh lớp Example có static method printAll(int...).
Method method = Example.class.getMethod("printAll", int[].class);
```

`getMethod("printAll", Integer[].class)` không khớp. `Method.isVarArgs()` có thể trả `true`, nhưng `Method.invoke` **không tự trải/đóng gói phần tử theo cú pháp gọi Java thông thường**; hãy truyền mảng phù hợp như một đối số.

### 9.2. Arrays.asList khác nhau với primitive và wrapper

```java
int[] ints = {1, 2, 3};
Integer[] wrappers = {1, 2, 3};

var a = java.util.Arrays.asList(ints);
var b = java.util.Arrays.asList(wrappers);

System.out.println(a.size()); // 1: phần tử duy nhất là cả int[].
System.out.println(b.size()); // 3: mỗi Integer là một phần tử.
```

Lý do: `Arrays.asList(T... a)` nhận `T[]` thuộc kiểu tham chiếu; `int[]` không phải mảng `T[]` chứa các đối tượng `Integer`, nên nó được xem như **một đối tượng phần tử**.

### 9.3. System.arraycopy không boxing một mảng

`System.arraycopy(intArray, 0, integerArray, 0, n)` **không** sao chép bằng boxing từng phần tử; do kiểu phần tử primitive và reference không tương thích, nó ném `ArrayStoreException`. Hãy dùng vòng lặp/stream chuyển đổi thật sự khi cần.

### 9.4. Mảng nhiều chiều

`int[][]` là mảng của các `int[]`, trong khi `Integer[][]` là mảng của `Integer[]`. Dù hai loại nhiều chiều đều là mảng các đối tượng ở cấp ngoài, **các mảng con vẫn không thể thay thế nhau**. Việc chuyển đổi phải đi qua từng hàng và từng phần tử, xử lý cả hàng `null` nếu dữ liệu cho phép.

## 10. Các lỗi phổ biến và cách khắc phục

| Hiện tượng | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| `NoSuchMethodException` cho `process(java.lang.Integer[])` | Method thực tế nhận `int[]` | Tra đúng `int[].class` |
| `Method.invoke` ném `IllegalArgumentException` dù số phần tử đúng | Truyền `Integer[]` thay `int[]` | Chuyển từng phần tử thành `int[]`, rồi truyền `(Object) converted` |
| Lệnh `Integer[] array = primitiveArray` không biên dịch | Java không boxing ở cấp mảng | Dùng `Arrays.stream(primitiveArray).boxed().toArray(Integer[]::new)` |
| `Constructor.newInstance` từ chối `Integer[]` | Constructor khai báo tham số `int[]` | Tra/gọi đúng chữ ký `int[].class` và giá trị `int[]` |
| `Field.set` từ chối wrapper array | Field khai báo `int[]` | Kiểm tra `getType()`, tạo đúng loại mảng |
| `Array.get(intArray, 0)` trả `Integer`, tưởng cả mảng là `Integer[]` | API boxing **một phần tử** | Xem `array.getClass()` và `getComponentType()` |
| `mapToInt(Integer::intValue)` ném NPE | `Integer[]` có phần tử `null` | Quy định xử lý `null` trước khi unbox |
| `Arrays.asList(intArray).size()` bằng `1` | Cả `int[]` trở thành một phần tử của list | Dùng `Arrays.stream(intArray).boxed().toList()` để có `List<Integer>` |
| `System.arraycopy` báo `ArrayStoreException` | API không boxing cả mảng khi copy | Chuyển đổi bằng loop hoặc stream |
| Ép `(Integer[]) (Object) intArray` ném `ClassCastException` | Runtime class thật vẫn là `int[]` | Không dùng cast thay conversion |

## 11. Tự kiểm tra

1. **Có thể tra `void handle(int[] values)` bằng `getMethod("handle", Integer[].class)` không?** Không. Phải dùng `int[].class` vì chữ ký được khớp theo kiểu mảng đã khai báo.
2. **Vì sao `Array.get(new int[]{5}, 0)` trả `Integer`?** Vì `Array.get` trả `Object`, nên boxing **phần tử `int`**; điều đó không biến `int[]` thành `Integer[]`.
3. **`Object obj = new int[]{1,2}` có hợp lệ không? Còn `Object[] arr = new int[]{1,2}`?** Câu đầu hợp lệ; câu sau không biên dịch được.
4. **`Integer[]` có thể có `null` trong khi `int[]` có không?** `Integer[]` có, `int[]` không thể chứa `null` như một phần tử.
5. **Cách chuyển đổi tường minh `Integer[]` sang `int[]`?** Xác minh phần tử `null` theo nghiệp vụ, sau đó unbox từng phần tử, ví dụ bằng `Arrays.stream(values).mapToInt(Integer::intValue).toArray()`.
6. **Có thể thay `int[][]` bằng `Integer[][]` trong Reflection không?** Không; kiểu mảng nhiều chiều vẫn khác và không có chuyển đổi boxing cấp mảng.

**Ghi nhớ:** Boxing/unboxing hoạt động với **giá trị phần tử**, không chuyển đổi **kiểu của nguyên mảng**. Trong Reflection, luôn dùng đúng `Class` của tham số/field (`int[].class` hay `Integer[].class`) và thực hiện chuyển đổi mảng tường minh khi thật sự cần.

Tài liệu tham khảo: [Java Language Specification §5 — Conversions and Contexts](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html), [Java SE 17 `java.lang.reflect.Array`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Array.html), [Java SE 17 `Method.invoke`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html).

</details>

- [Quay lại đầu trang](#back-to-top)
