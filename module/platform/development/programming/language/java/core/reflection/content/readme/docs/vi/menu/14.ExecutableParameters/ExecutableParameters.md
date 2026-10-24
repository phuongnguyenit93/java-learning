<a id="back-to-top"></a>

# Tham số executable

## Menu
- [Executable dùng chung cho Method và Constructor](#executable-parameters-001)
- [getParameters và Parameter](#executable-parameters-002)
- [getParameterTypes khác getGenericParameterTypes](#executable-parameters-003)
- [Tên tham số và tùy chọn biên dịch -parameters](#executable-parameters-004)
- [isNamePresent và tên tổng hợp arg0, arg1](#executable-parameters-005)
- [isImplicit, isSynthetic và isVarArgs](#executable-parameters-006)
- [Modifier của tham số và giới hạn metadata có sẵn](#executable-parameters-007)
- [Annotation trên từng tham số](#executable-parameters-008)
- [getAnnotatedParameterTypes và getAnnotatedReceiverType](#executable-parameters-009)
- [Không dùng tên tham số như hợp đồng ổn định khi build không bảo đảm metadata](#executable-parameters-010)

## <a id="executable-parameters-001">Executable dùng chung cho Method và Constructor</a>

<details>
<summary>Xem chi tiết</summary>

# Executable dùng chung cho Method và Constructor trong Java Reflection

Trong Reflection của Java, **Method** đại diện một phương thức, còn **Constructor<T>** đại diện một constructor. Cả hai cùng kế thừa lớp trừu tượng **java.lang.reflect.Executable**, được giới thiệu từ Java 8, vì chúng có rất nhiều thông tin chung: danh sách tham số, kiểu generic, annotation, ngoại lệ khai báo, modifiers, biến kiểu và cờ varargs/synthetic.

Executable hữu ích nhất khi viết công cụ xử lý metadata của cả method lẫn constructor, chẳng hạn framework dependency injection, validation, binding, serialization, lập tài liệu API hoặc bộ phân tích chữ ký. Thay vì viết hai vòng lặp gần giống nhau cho Method và Constructor, ta có thể viết một hàm nhận Executable và đọc những thuộc tính chung.

**Giới hạn cốt lõi:** Executable là lớp nền dùng để **mô tả và phân tích** một thành phần có thể thực thi. Nó không có một phương thức invoke/newInstance chung: để gọi thực tế, phải phân biệt Method.invoke(...) và Constructor.newInstance(...).

## 1. Quan hệ kiểu và phạm vi trách nhiệm

Sơ đồ lớp đơn giản:

~~~text
java.lang.Object
    └── java.lang.reflect.AccessibleObject
          └── java.lang.reflect.Executable
                ├── java.lang.reflect.Method
                └── java.lang.reflect.Constructor<T>
~~~

Executable còn triển khai các interface **Member**, **AnnotatedElement**, **GenericDeclaration** và kế thừa chức năng truy cập reflective của **AccessibleObject**. Trên Java 17, đây là abstract sealed class chỉ cho phép Method và Constructor trực tiếp kế thừa.

| Khái niệm | Vai trò |
| --- | --- |
| **Method** | Đại diện phương thức khai báo bởi class/interface; có tên, kiểu trả về, receiver và phương thức invoke |
| **Constructor<T>** | Đại diện constructor của class T; gọi newInstance để tạo instance của class khai báo |
| **Executable** | Cung cấp API metadata chung cho Method và Constructor |
| **Parameter** | Đại diện một tham số trong chữ ký runtime của một Executable |
| **AccessibleObject** | Cung cấp các API như setAccessible/trySetAccessible để yêu cầu điều chỉnh kiểm tra truy cập |

Ví dụ về đa hình:

~~~java
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;

Method method = String.class.getMethod("substring", int.class);
Constructor<StringBuilder> constructor = StringBuilder.class.getConstructor();
Executable e1 = method;
Executable e2 = constructor;

System.out.println(e1.getParameterCount()); // 1
System.out.println(e2.getParameterCount()); // 0
~~~

Đoạn này có thể đặt trong một method khai báo throws ReflectiveOperationException. Hai đối tượng đều gọi được **getParameterCount()**, vì phương thức đó nằm ở Executable. Nhưng chỉ **method** có **invoke(receiver, args...)**, và chỉ **constructor** có **newInstance(args...)**.

Không cố ép Executable thành Method nếu chưa kiểm tra kiểu. Có thể phân nhánh bằng:

~~~java
if (executable instanceof Method m) {
    // Có thể dùng Method.getReturnType(), Method.invoke(...)...
} else if (executable instanceof Constructor<?> c) {
    // Có thể dùng Constructor.newInstance(...)...
}
~~~

Đây là fragment: biến executable phải tồn tại trong ngữ cảnh.

## 2. Những API metadata dùng chung

Các nhóm dưới đây là trọng tâm của lớp Executable.

| API | Kiểu kết quả | Chức năng |
| --- | --- | --- |
| **getDeclaringClass()** | Class<?> | Lớp/interface khai báo thành phần |
| **getName()** | String | Tên phương thức hoặc tên constructor theo quy ước Reflection |
| **getModifiers()** | int | Modifiers như public/private/protected; giải mã bằng Modifier |
| **getParameterCount()** | int | Tổng tham số runtime, kể cả một số tham số implicit/synthetic |
| **getParameterTypes()** | Class<?>[] | Kiểu tham số sau erasure, theo thứ tự |
| **getGenericParameterTypes()** | Type[] | Thông tin kiểu generic được khai báo khi có metadata |
| **getParameters()** | Parameter[] | Đối tượng Parameter cho từng tham số runtime |
| **getParameterAnnotations()** | Annotation[][] | Annotation trên khai báo từng tham số |
| **getAnnotatedParameterTypes()** | AnnotatedType[] | Annotation đặt ở vị trí sử dụng kiểu tham số (TYPE_USE) |
| **getExceptionTypes()** | Class<?>[] | Các kiểu exception trong throws theo runtime Class |
| **getGenericExceptionTypes()** | Type[] | Các kiểu exception trong throws có thông tin generic |
| **getAnnotatedExceptionTypes()** | AnnotatedType[] | Annotation TYPE_USE trên kiểu exception |
| **getTypeParameters()** | TypeVariable<?>[] | Type variables được khai báo bởi chính method/constructor |
| **isVarArgs()** | boolean | Có khai báo tham số cuối dạng varargs hay không |
| **isSynthetic()** | boolean | Thành phần có do compiler sinh ra dạng synthetic hay không |
| **toGenericString()** | String | Biểu diễn chữ ký có generic và modifiers |
| **getAnnotatedReturnType()** | AnnotatedType | Thông tin kiểu trả về có annotation theo hợp đồng của từng subtype |
| **getAnnotatedReceiverType()** | AnnotatedType hoặc null | Kiểu receiver được khai báo tại vị trí phù hợp; không phải mọi executable đều có receiver |

Một số phương thức như **getParameterCount()**, **getParameters()**, **getGenericParameterTypes()** có phần triển khai chung trong Executable; một số phương thức khác là abstract, được Method và Constructor cài đặt. Từ góc độ người dùng API, vẫn có thể gọi chúng thông qua biến kiểu Executable.

### Chữ ký không phải là lời gọi thực thi

Executable cung cấp **getParameterCount()**, nhưng không tự cung cấp **invoke()**. Để thực thi:

- **Method.invoke(Object receiver, Object... args)** gọi method đã chọn; với static method, receiver có thể là null.
- **Constructor.newInstance(Object... initargs)** tạo đối tượng của declaring class.
- Cả hai đều có thể tạo **InvocationTargetException** nếu phần thân method hoặc constructor ném ngoại lệ.
- **IllegalAccessException** liên quan tới quyền gọi; **IllegalArgumentException** thường liên quan receiver hoặc arguments không phù hợp. Các chi tiết kiểm tra access thuộc Method/Constructor và môi trường module.

Khi viết một metadata scanner, tốt nhất giữ biến kiểu **Executable** trong phần phân tích chung, và chỉ phân nhánh đến subtype khi cần invoke hoặc API đặc thù.

## 3. Parameter: đối tượng đại diện từng tham số

API **getParameters()** trả mảng **Parameter[]**, mỗi phần tử có:

- **getName()**: tên tham số đọc từ class file nếu có, hoặc tên tổng hợp như arg0, arg1.
- **isNamePresent()**: class file có chứa tên tham số trong metadata thích hợp hay không.
- **getType()**: Class<?> sau type erasure.
- **getParameterizedType()**: Type biểu diễn khai báo generic.
- **getAnnotations()**, **getAnnotation(...)**: annotation gắn trực tiếp lên khai báo tham số.
- **getAnnotatedType()**: AnnotatedType cho các annotation trên vị trí dùng kiểu.
- **isVarArgs()**: tham số có đại diện danh sách biến số đối số hay không.
- **isImplicit()** và **isSynthetic()**: tham số ngầm định trong source hoặc do compiler tạo.
- **getDeclaringExecutable()**: trở lại Method/Constructor khai báo tham số.

**Tên tham số không luôn được giữ trong file .class.** Với javac, hãy biên dịch cùng **-parameters** khi công cụ dựa vào tên như name, amount hoặc requestId. Không có tùy chọn đó, tên có thể trở thành arg0, arg1; không được coi các tên này là tên nguồn ổn định.

~~~bash
javac --release 17 -parameters -encoding UTF-8 ExecutableDemo.java
java ExecutableDemo
~~~

Mặc dù **-g** thêm một số thông tin debug, nó không thay thế mục đích của **-parameters** để các API Reflection về tên tham số có thể dùng đúng theo hợp đồng. Tên tham số của một số cấu trúc đặc biệt như record có thể được compiler lưu theo quy tắc riêng; đừng vì thế suy ra mọi method đều có tên.

### Erasure: Class<?> khác Type

Giả sử method có chữ ký:

~~~java
public <E extends Number> void submit(List<E> values, E limit) {}
~~~

Thông tin thông thường:

| Tham số | getParameterTypes() | getGenericParameterTypes() |
| --- | --- | --- |
| values | List.class | ParameterizedType biểu diễn List<E> |
| limit | Number.class | TypeVariable E |

Lệnh tìm method phải dùng kiểu tham số sau erasure:

~~~java
clazz.getDeclaredMethod("submit", java.util.List.class, Number.class);
~~~

clazz là biến kiểu Class<?> trong ví dụ. Không thể truyền một ParameterizedType biểu diễn List<E> vào **getDeclaredMethod**, bởi phương thức đó nhận **Class<?>...**.

**getTypeParameters()** của method submit có một TypeVariable tên E; điều này khác việc **getGenericParameterTypes()** trả E ở vị trí tham số. Với Constructor của generic class, getTypeParameters() chỉ mô tả type variable được khai báo **riêng ở constructor**, không tự bao gồm tất cả type variable của class.

## 4. Ví dụ Java 17+ đầy đủ: phân tích Method và Constructor qua Executable

Chương trình sau minh họa một hàm **inspect(Executable)** xử lý đồng thời constructor và method, in thông tin tên/kiểu tham số, generic, throws, varargs, annotations và sau đó gọi từng thành phần bằng API riêng.

Lưu toàn bộ mã vào file **ExecutableDemo.java**:

~~~java
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;

public class ExecutableDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface Label { String value(); }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    @interface Marked {}

    public static class Service {
        private final List<String> names;

        public Service(@Label("ctor") @Marked List<String> names) throws IOException {
            this.names = names;
        }

        public <E extends Number> @Marked List<E> process(
                @Label("key") String key, @Marked List<E> values, int... limits)
                throws IOException {
            return values;
        }

        public int size() { return names.size(); }
    }

    private static void inspect(Executable executable) {
        System.out.println("kind=" + (executable instanceof Method ? "method" : "constructor"));
        System.out.println("owner=" + executable.getDeclaringClass().getSimpleName());
        System.out.println("count=" + executable.getParameterCount());
        System.out.println("varargs=" + executable.isVarArgs());
        Parameter[] parameters = executable.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            System.out.println("p" + i + "=" + parameter.getName()
                    + ":" + parameter.getType().getSimpleName()
                    + ",namePresent=" + parameter.isNamePresent()
                    + ",generic=" + parameter.getParameterizedType().getTypeName());
        }
        System.out.println("throws=" + executable.getExceptionTypes()[0].getSimpleName());
        System.out.println("typeVariables=" + executable.getTypeParameters().length);
    }

    public static void main(String[] args) throws Exception {
        Constructor<Service> constructor = Service.class.getConstructor(List.class);
        Method method = Service.class.getMethod("process", String.class, List.class, int[].class);

        inspect(constructor);
        inspect(method);

        System.out.println("constructor label="
                + constructor.getParameters()[0].getAnnotation(Label.class).value());
        System.out.println("method p1 type annotation="
                + method.getAnnotatedParameterTypes()[1].isAnnotationPresent(Marked.class));
        System.out.println("method return type annotation="
                + method.getAnnotatedReturnType().isAnnotationPresent(Marked.class));
        System.out.println("method type variable=" + method.getTypeParameters()[0].getName());
        System.out.println("return=" + method.getReturnType().getSimpleName());

        Service service = constructor.newInstance(List.of("A", "B"));
        Object result = method.invoke(service, "ok", List.of(3, 4), new int[] {1, 2});
        System.out.println("invoke=" + result);
        System.out.println("service size=" + service.size());
    }
}
~~~

Biên dịch/chạy (JDK 17 trở lên):

~~~bash
javac --release 17 -parameters -encoding UTF-8 ExecutableDemo.java
java ExecutableDemo
~~~

Stdout đã được kiểm tra trên JDK 21 sau khi biên dịch với **--release 17 -parameters**:

~~~text
kind=constructor
owner=Service
count=1
varargs=false
p0=names:List,namePresent=true,generic=java.util.List<java.lang.String>
throws=IOException
typeVariables=0
kind=method
owner=Service
count=3
varargs=true
p0=key:String,namePresent=true,generic=java.lang.String
p1=values:List,namePresent=true,generic=java.util.List<E>
p2=limits:int[],namePresent=true,generic=int[]
throws=IOException
typeVariables=1
constructor label=ctor
method p1 type annotation=true
method return type annotation=true
method type variable=E
return=List
invoke=[3, 4]
service size=2
~~~

### Phân tích kết quả

1. **Cùng một hàm inspect** nhận cả Constructor<Service> và Method qua **Executable**. Đoạn chương trình chỉ cần phân biệt method/constructor để in kind; các API còn lại trong inspect đều nằm ở phần chung.
2. Constructor khai báo một tham số List<String> nên **count=1**, kiểu erased là List nhưng generic giữ được **java.util.List<java.lang.String>**. Không có type variable khai báo riêng, nên **typeVariables=0**.
3. Method process có ba tham số: String, List<E> và int...; erasure của List<E> thành List. Varargs **int...** có kiểu tham số runtime **int[]**, vì vậy **varargs=true** nhưng getParameterTypes() của tham số cuối là mảng.
4. Type variable E extends Number được khai báo trên chính method, do đó **typeVariables=1** và tên là **E**.
5. **@Label** là annotation ở vị trí PARAMETER, đọc bằng Parameter.getAnnotation(Label.class). **@Marked** là TYPE_USE annotation, đọc qua AnnotatedType của tham số hoặc kiểu trả về. Hai nhóm annotation có nguồn thông tin khác nhau.
6. getExceptionTypes() thấy **IOException** vì cả constructor và method đều khai báo throws IOException. Nó mô tả chữ ký khai báo; không khẳng định mỗi lần gọi sẽ ném IOException.
7. Việc khởi tạo dùng **Constructor.newInstance**; gọi method dùng **Method.invoke**. Kết quả trả về từ Method.invoke là Object và được in như một List gồm 3, 4.

### Thử bỏ -parameters

Biên dịch lại trong thư mục riêng hoặc xóa class cũ và chạy:

~~~bash
javac --release 17 -encoding UTF-8 ExecutableDemo.java
java ExecutableDemo
~~~

Khi không có metadata tham số được giữ bởi -parameters, phần tên trong output thường đổi thành **arg0**, **arg1**, **arg2**, và **isNamePresent()** thành false, ví dụ:

~~~text
p0=arg0:List,namePresent=false,generic=java.util.List<java.lang.String>
~~~

Thông tin generic như List<String> và type variable E **không phụ thuộc vào -parameters giống tên biến**: nó đến từ thông tin generic signature của class file. Hãy phân biệt rõ các loại metadata để chẩn đoán.

## 5. Declaration annotations và type-use annotations

Một annotation gắn **trực tiếp lên khai báo tham số** khác một annotation gắn lên **kiểu** được sử dụng tại vị trí tham số.

Ví dụ mã ngắn (không phải chương trình độc lập):

~~~java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@interface Input {}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface Checked {}

void save(@Input @Checked List<String> values) {}
~~~

Trong một đoạn mã thực tế cần import java.lang.annotation.* và java.util.List.

- **getParameterAnnotations()** trả mảng hai chiều: mỗi vị trí tương ứng tập các annotation khai báo của một tham số.
- **getParameters()[i].getAnnotations()** cũng cho các declaration annotations trên Parameter đó.
- **getAnnotatedParameterTypes()[i].getAnnotations()** cho các annotation TYPE_USE ở vị trí kiểu tham số tương ứng.
- **getAnnotatedReturnType()** dùng cho type-use annotation tại kiểu trả về; với Method, có thể đọc cùng **getReturnType()** hoặc **getGenericReturnType()** tùy mục tiêu.
- **getAnnotatedExceptionTypes()** mô tả type-use annotation của các exception trong throws.
- **getAnnotatedReceiverType()** mô tả receiver type ở nơi hợp lệ (chẳng hạn receiver parameter được khai báo tường minh); có thể trả null nếu không có receiver thích hợp.

Không ép mọi annotation vào cùng một loại. Một validation framework muốn biết annotation trên tham số nên đọc cả loại **declaration** lẫn loại **type-use** tùy meta-annotation @Target của chúng.

**Chú ý:** mảng annotation không được đảm bảo phản ánh tất cả thông tin source nếu annotation không có RetentionPolicy.RUNTIME. Các annotation SOURCE/CLASS có thể không hiển thị theo các API phản chiếu runtime như các annotation RUNTIME.

## 6. Implicit, synthetic parameters và sự khác nhau giữa các mảng

Một executable không chỉ có các tham số hiển thị tường minh trong source. Compiler có thể đưa thêm **implicit** hoặc **synthetic** parameters. Hai trường hợp dễ gặp:

- Constructor của **non-static inner class** thường có thêm tham số tham chiếu tới enclosing instance ở vị trí đầu.
- Constructor liên quan một số cấu trúc compiler như enum có thể xuất hiện những tham số được bổ sung ngoài danh sách người lập trình viết.

Ví dụ:

~~~java
class Outer {
    class Inner {
        Inner(int number) {}
    }
}
~~~

Đối với kiểu inner không static, constructor trong bytecode cần nhận một instance của Outer. Khi Reflection tra constructor, trong trường hợp javac thông thường sẽ phải dùng:

~~~java
Outer outer = new Outer();
Constructor<Outer.Inner> c =
        Outer.Inner.class.getDeclaredConstructor(Outer.class, int.class);
Outer.Inner instance = c.newInstance(outer, 10);
~~~

Đây là fragment cần import java.lang.reflect.Constructor và đặt trong method xử lý checked exceptions. Với **static nested class**, không có outer instance bổ sung theo cơ chế này.

**Quan trọng khi xử lý metadata:**

- **getParameterCount()** tính cả tham số implicit/synthetic hiện diện ở runtime.
- **getParameterTypes()** cho các kiểu thực tế dùng trong descriptor, cần thiết cho lookup.
- **getParameters()** cho Parameter và các cờ **isImplicit()/isSynthetic()** khi metadata cho phép diễn giải.
- **getGenericParameterTypes()** có thể trả danh sách có số lượng khác trong một số tình huống có implicit/synthetic parameters và generic signature; chính tài liệu API cảnh báo điều này.
- Các mảng annotation cũng có thể phản ánh tham số bổ sung theo quy tắc riêng; đừng máy móc ghép mảng từ hai API khác nhau bằng index mà không kiểm tra quan hệ và độ dài.

Khi làm thư viện mapping tham số, phải kiểm thử trên class thực tế và trên compiler/JDK mục tiêu. Nếu cần xem descriptor ở bytecode, có thể dùng **javap -v** với file .class để xem MethodParameters, Signature và RuntimeVisibleParameterAnnotations/RuntimeVisibleTypeAnnotations.

## 7. isVarArgs() và cách truyền đối số thực tế

Với method:

~~~java
void log(String label, int... numbers) {}
~~~

Compiler biến kiểu tham số cuối thành mảng int[]. Một Method tìm được theo:

~~~java
Method method = clazz.getDeclaredMethod("log", String.class, int[].class);
~~~

là hợp lệ nếu clazz mô tả class khai báo log.

**isVarArgs()** chỉ cho biết phương thức được **khai báo bằng cú pháp biến số đối số**. Nó không có nghĩa Reflection tự luôn thực hiện overload resolution hay tự gom các số thành mảng như cú pháp gọi Java ở source:

~~~java
method.invoke(receiver, "sum", new int[] {1, 2, 3});
~~~

Trong đó receiver là instance thích hợp; tham số thứ ba được truyền như một đối số mảng. Nếu method chỉ nhận một tham số kiểu String[] và cần truyền mảng làm **một phần tử** của varargs Object... của Method.invoke, ép rõ sang Object:

~~~java
method.invoke(receiver, (Object) new String[] {"a", "b"});
~~~

Dòng sau chỉ đúng khi method nhận **một String[]**, khác với method log(String,int...) phía trên. Cast (Object) đặc biệt có ích để tránh Java tự trải mảng tham chiếu String[] thành các phần tử varargs ở **chính lệnh Method.invoke**.

Tương tự, **Constructor.newInstance(Object... args)** cũng nhận mảng arguments theo runtime signature, chứ không chạy bộ chọn overload như javac. Không dùng isVarArgs() để giả định mọi lệnh gọi reflective sẽ tự chuyển số phần tử tùy ý thành một mảng.

## 8. Quyền truy cập qua AccessibleObject và JPMS

Method và Constructor kế thừa khả năng yêu cầu bỏ qua một số kiểm tra Java language access từ AccessibleObject:

~~~java
if (executable.trySetAccessible()) {
    // Caller có thể yêu cầu tắt kiểm tra access trong giới hạn cho phép.
}
~~~

Đây là fragment giả định đã có biến Executable.

Trên Java Platform Module System (JPMS), private member của class thuộc một named module khác có thể nằm trong package **không mở cho module đang thực hiện Reflection**. Lệnh **setAccessible(true)** có thể ném **InaccessibleObjectException**, còn **trySetAccessible()** có thể trả false. Package được **exports** cho public API không tự cho phép deep reflection vào private method/constructor; thông thường cần **opens** phù hợp.

Nếu mục đích chỉ là đọc metadata, nhiều API như getDeclaredMethods/getDeclaredConstructors/getParameters vẫn sử dụng được dù sau đó không có quyền invoke non-public member. Vì vậy:

1. Phân tích metadata với Executable trước.
2. Chỉ yêu cầu deep reflective access khi thực sự cần invoke/newInstance.
3. Kiểm tra module/package và caller chính xác nếu bị chặn.
4. Không dùng setAccessible để vượt quyền đối với thành viên nội bộ khi API công khai đã đáp ứng yêu cầu.

Việc **setAccessible(true) thành công** cũng không làm thay đổi chữ ký Method/Constructor, generic return types, số tham số hoặc annotation. Đây là thay đổi kiểm tra quyền truy cập khi dùng Reflection, không phải sửa class file.

## 9. Dùng Executable để viết scanner chung an toàn

Một scanner dùng cho cả Method và Constructor có thể nhận danh sách Executable rồi in những trường chung:

~~~java
static void describe(Executable e) {
    System.out.println("member: " + e.getName());
    for (Parameter p : e.getParameters()) {
        System.out.println(p.getType().getTypeName()
                + " " + p.getName()
                + " (namePresent=" + p.isNamePresent() + ")");
    }
}
~~~

Đây là một method fragment cần import java.lang.reflect.Executable và java.lang.reflect.Parameter. Có thể gọi với Method hoặc Constructor. Khi quét toàn class, có thể lấy:

~~~java
for (Method m : type.getDeclaredMethods()) {
    describe(m);
}
for (Constructor<?> c : type.getDeclaredConstructors()) {
    describe(c);
}
~~~

Biến type là Class<?> đã xác định; hai lệnh getDeclared... không duyệt superclass theo cùng cách getMethods(), và constructor **không được kế thừa**.

Khi nâng cấp scanner cho framework thực tế:

- Kiểm tra **Modifier** cùng **getDeclaringClass()**; không dựa vào thứ tự của getDeclaredMethods()/getDeclaredConstructors().
- Kiểm tra **Method.isBridge()** (chỉ Method có) và **Executable.isSynthetic()** để giảm trường hợp compiler-generated members khi xuất tài liệu source API.
- Dùng **isNamePresent()** trước khi lấy tên tham số làm tên property/JSON key/argument binding.
- Phân biệt **getParameterTypes()** khi cần descriptor runtime với **getGenericParameterTypes()** khi cần phân tích T, List<T> hoặc wildcard.
- Phân biệt **declaration annotation** và **type-use annotation**.
- Khi có implicit/synthetic parameters, kiểm tra độ dài và metadata trước khi ghép nhiều mảng theo index.
- Dùng subtype-specific API để xử lý **kiểu trả về của Method** hoặc **khởi tạo của Constructor**.

Một framework không nên mặc định lấy tên tham số, annotation và generic type làm toàn bộ hợp đồng xác thực: nhiều dữ liệu có thể thiếu vì cách build, retention, obfuscation hoặc compiler. Hãy có đường fallback và thông báo lỗi rõ ràng.

## 10. Lỗi phổ biến và câu hỏi tự kiểm tra

### Lỗi thường gặp

1. **Cố gọi executable.invoke(...)**: Executable không khai báo invoke/newInstance chung. Phân biệt Method và Constructor bằng instanceof, rồi dùng đúng API.
2. **Cho rằng generic Type luôn là Class<?>**: với List<String>, Type có thể là ParameterizedType; với T có thể là TypeVariable. Không ép kiểu bừa.
3. **Lấy getParameters()[i].getName() làm tên source chắc chắn**: thiếu -parameters thường sinh arg0/arg1, isNamePresent() là false.
4. **Nhầm isVarArgs với tự gom arguments**: reflective invocation cần đối số đúng descriptor; last parameter là array.
5. **Nhầm annotation trên tham số và annotation trên kiểu**: getParameterAnnotations() và getAnnotatedParameterTypes() phục vụ mục đích khác nhau.
6. **Giả định tham số runtime luôn bằng số tham số viết trong source**: implicit/synthetic parameters có thể thay đổi parameter count và mô hình generic metadata.
7. **Tin rằng getExceptionTypes() liệt kê mọi exception có thể xảy ra khi gọi**: nó chỉ phản ánh throws declaration, còn runtime exception/error vẫn có thể phát sinh.
8. **Gọi setAccessible(true) để sửa metadata**: API chỉ thay đổi khả năng bỏ qua một số kiểm tra access; JPMS vẫn bảo vệ các package chưa opens phù hợp.
9. **Bỏ qua bridge/synthetic methods** khi duyệt toàn class; method do compiler tạo có thể làm kết quả scanner khác với source.
10. **Dựa vào thứ tự array getDeclaredMethods() hoặc getDeclaredConstructors()**: không có thứ tự ổn định theo source được bảo đảm.

### Tự kiểm tra

**Câu 1.** Biến Executable e đang trỏ tới Constructor<?>. Có thể gọi e.getParameters() không? Có thể gọi e.newInstance() trực tiếp không?

**Đáp án:** getParameters() được vì thuộc API Executable; newInstance() không phải API chung nên cần cast/kiểm tra Constructor<?>.

**Câu 2.** Method nhận List<String> và int... . Những kiểu nào dùng khi getDeclaredMethod?

**Đáp án:** List.class và int[].class, theo thứ tự. Với varargs, tham số cuối có runtime type là mảng.

**Câu 3.** Một Parameter có name là arg1 và isNamePresent() trả false. Có nên dùng arg1 làm tên dữ liệu API cố định không?

**Đáp án:** Không. Đây thường là tên tự sinh do thiếu metadata. Dùng -parameters hoặc cấu hình ánh xạ tên rõ ràng.

**Câu 4.** Khi đọc một parameter vừa có @Label khai báo vừa có @Marked TYPE_USE, nên dùng API gì?

**Đáp án:** Parameter.getAnnotation(Label.class) cho declaration annotation và getAnnotatedType()/getAnnotatedParameterTypes() cho type-use annotation.

**Câu 5.** Method có generic type parameter E extends Number; getParameterTypes() cho tham số kiểu E trả gì, và getGenericParameterTypes() trả gì?

**Đáp án:** Thường là Number.class và TypeVariable E; type erasure được dùng để lookup, còn generic metadata phục vụ phân tích khai báo.

**Câu 6.** Constructor của non-static inner class có thể có thêm tham số gì?

**Đáp án:** Tham số enclosing instance của outer class, thường ở đầu descriptor runtime. Dùng đúng Class<?> khi tra cứu và kiểm tra Parameter flags nếu metadata có.

**Ghi nhớ:** **Executable là API metadata chung cho Method và Constructor**. Dùng nó để phân tích tham số, generic, annotations, exceptions, varargs và modifiers; khi cần thực thi phải dùng **Method.invoke** hoặc **Constructor.newInstance**. Khi phụ thuộc vào tên tham số, build với **-parameters** và xác nhận **isNamePresent()**.

### Tài liệu tham khảo

- Java SE 17 — Executable: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Executable.html
- Java SE 17 — Parameter: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Parameter.html
- Java SE 17 — Method: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html
- Java SE 17 — Constructor<T>: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Constructor.html
- Java SE 17 — AccessibleObject: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html
- Oracle Java Tutorials — Obtaining Names of Method Parameters: https://docs.oracle.com/javase/tutorial/reflect/member/methodparameterreflection.html


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executable-parameters-002">getParameters và Parameter</a>

<details>
<summary>Xem chi tiết</summary>

# getParameters và Parameter trong Java Reflection

## 1. Mục tiêu: đọc tham số của method và constructor bằng Reflection

Khi xây dựng framework định tuyến, dependency injection, validation, serializer hoặc công cụ phân tích API, việc tìm được một `Method` hay `Constructor<?>` chưa đủ: ta còn cần biết nó nhận bao nhiêu tham số, kiểu của từng tham số, thứ tự, annotation, tên tham số (nếu có metadata) và liệu tham số cuối cùng có phải varargs.

Java đặt những thông tin đó tại `java.lang.reflect.Executable` và `java.lang.reflect.Parameter`:

- `Method` và `Constructor<?>` đều kế thừa `Executable`.
- `Executable.getParameters()` trả về một mảng `Parameter[]`, theo đúng thứ tự tham số của executable ở cấp Reflection.
- Mỗi `Parameter` đại diện cho **một tham số của một executable cụ thể**. `getDeclaringExecutable()` cho phép truy ngược về method/constructor sở hữu tham số.
- `Parameter` cung cấp `getName()`, `isNamePresent()`, `getType()`, `getParameterizedType()`, `getAnnotatedType()`, `getAnnotations()`, `getAnnotation(...)`, `getModifiers()`, `isImplicit()`, `isSynthetic()` và `isVarArgs()`.

Một tham số tên `id`, có kiểu `List<String>` và annotation `@NotNull` là ba lớp metadata **khác nhau**. Nếu class được biên dịch thiếu thông tin tên, Reflection vẫn có thể biết kiểu, thứ tự và annotation; không nên suy luận rằng `getParameters()` trả mảng rỗng.

## 2. Phân biệt những API tra cứu thường gặp

| API | Kết quả | Khi nào dùng |
| --- | --- | --- |
| `Executable.getParameterCount()` | Số tham số được Reflection thấy. | Kiểm tra số lượng trước khi ghép argument. |
| `Executable.getParameters()` | `Parameter[]`, thứ tự từ trái sang phải. | Cần tên, annotation, modifier, varargs và generic type cho từng tham số. |
| `Executable.getParameterTypes()` | `Class<?>[]` chứa kiểu đã erasure. | Xây dựng chữ ký Reflection hoặc đối chiếu argument runtime. |
| `Executable.getGenericParameterTypes()` | `Type[]`, có thể là `ParameterizedType`/`TypeVariable`/`GenericArrayType`. | Cần khai báo generic. |
| `Executable.getParameterAnnotations()` | `Annotation[][]`, các annotation được khai báo trên từng formal parameter. | Khi xử lý nhiều tham số bằng chỉ số; `Parameter.getAnnotations()` tiện hơn. |
| `Executable.isVarArgs()` | Executable khai báo varargs ở cấp bytecode hay không. | Phân biệt `String[]` thông thường và `String...`. |
| `Parameter.getDeclaringExecutable()` | `Method` hoặc `Constructor<?>` sở hữu tham số. | Xây báo lỗi có ngữ cảnh đầy đủ. |
| `Parameter.getName()` | Tên lấy từ metadata nếu có, hoặc tên thay thế như `arg0`. | Hiển thị; chỉ coi là tên source khi `isNamePresent() == true`. |
| `Parameter.getParameterizedType()` | `Type` của riêng tham số, bảo toàn khai báo generic. | Phân tích `List<String>`, `T`, v.v. |
| `Parameter.getAnnotatedType()` | `AnnotatedType` cho annotation trên **cách dùng kiểu** của tham số. | Kiểm tra `@Target(TYPE_USE)`. |

`getParameterTypes()` không có tên hoặc annotation; `getParameters()` cung cấp từng đối tượng `Parameter` nhưng **không tự đọc mã nguồn Java**. Metadata được tạo từ class file/runtime.

## 3. Cờ biên dịch `-parameters` và tên tham số

Mặc định, `javac` **không bắt buộc** ghi tên tham số trong class file dưới dạng metadata Reflection. Khi không có thuộc tính `MethodParameters` chứa tên, `getParameters()` vẫn trả về các `Parameter`, còn:

- `getName()` thường là `arg0`, `arg1`, `arg2`… theo vị trí.
- `isNamePresent()` trả `false`.
- `getType()`, `getParameterizedType()` và parameter annotation vẫn có thể truy cập được, nếu metadata cần thiết tương ứng được lưu.

Biên dịch bằng `javac -parameters` yêu cầu lưu tên và modifier của formal parameters trong class file:

~~~bash
javac -parameters -encoding UTF-8 ParameterMetadataDemo.java
java ParameterMetadataDemo
~~~

Khi class có metadata hợp lệ, `getName()` trả tên thực được biên dịch (chẳng hạn `recipient`), `isNamePresent() == true`. Cờ `-parameters` là lựa chọn **lúc build**, không phải `java -parameters` ở lúc chạy.

Phân biệt `-parameters` và `-g`:

- `-parameters` ghi thuộc tính `MethodParameters` để Core Reflection lấy tên tham số.
- `-g`/`-g:vars` điều khiển debug information như local-variable tables; tên biến debug có thể phục vụ debugger, nhưng **không** tự thay thế `-parameters` cho `Parameter.getName()`.
- Sau obfuscation, transformation hoặc build bằng compiler khác, metadata có thể bị thay đổi hay loại bỏ. Runtime phải kiểm tra `isNamePresent()` thật, không đoán từ môi trường phát triển.

Ví dụ thực tế: một router muốn tự ánh xạ query parameter vào tên Java có thể dùng `isNamePresent()` để quyết định; nếu `false`, yêu cầu annotation nêu tên rõ ràng hoặc cấu hình mapping, thay vì lấy chuỗi `arg0` làm tên public API.

### Metadata tên có phải điều kiện bắt buộc?

Không phải. Một executable vẫn gọi được khi không lưu tên source. Tên tham số **không phải** thành phần của signature JVM; ví dụ hai method có cùng tên và kiểu tham số nhưng chỉ khác tên biến Java không được overload. Vì vậy tên tham số được xem là metadata phục vụ reflection/tooling, không phải định danh hành vi gọi method.

`getParameters()` cũng có thể ném `MalformedParametersException` khi thông tin `MethodParameters` trong class file không hợp lệ. Khi xử lý bytecode được biến đổi hoặc class từ nguồn không đáng tin cậy, hãy có nhánh báo lỗi metadata thay vì âm thầm giả định tên hợp lệ.

## 4. Ví dụ Java 17+ chạy độc lập: method, constructor, varargs, annotation và generic

Tạo file `ParameterMetadataDemo.java`. Không cần Spring hay thư viện ngoài.

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;

public class ParameterMetadataDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface Name {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    @interface TypeMark {}

    static class Service {
        Service(String title) {}

        static void dispatch(
                @Name("user") @TypeMark String recipient,
                final int retries,
                String... labels) {
        }

        static <T extends Number> void aggregate(List<T> numbers, T seed) {
        }
    }

    public static void main(String[] args) throws Exception {
        Method dispatch = Service.class.getDeclaredMethod(
                "dispatch", String.class, int.class, String[].class);

        System.out.println("dispatch parameters="
                + dispatch.getParameterCount());
        Parameter[] parameters = dispatch.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Name annotation = parameter.getAnnotation(Name.class);
            System.out.println(i + ": name=" + parameter.getName()
                    + ", present=" + parameter.isNamePresent()
                    + ", type=" + parameter.getType().getSimpleName()
                    + ", generic=" + parameter.getParameterizedType().getTypeName()
                    + ", varargs=" + parameter.isVarArgs()
                    + ", label=" + (annotation == null ? "-" : annotation.value())
                    + ", typeMark=" + parameter.getAnnotatedType()
                            .isAnnotationPresent(TypeMark.class));
        }

        Method aggregate = Service.class.getDeclaredMethod(
                "aggregate", List.class, Number.class);
        Parameter[] generic = aggregate.getParameters();
        System.out.println("generic first="
                + generic[0].getParameterizedType().getTypeName());
        System.out.println("generic second="
                + generic[1].getParameterizedType().getTypeName());
        System.out.println("erased second="
                + generic[1].getType().getTypeName());

        Constructor<Service> constructor =
                Service.class.getDeclaredConstructor(String.class);
        Parameter title = constructor.getParameters()[0];
        System.out.println("constructor name=" + title.getName()
                + ", present=" + title.isNamePresent());
        System.out.println("owner is constructor="
                + (title.getDeclaringExecutable() == constructor));
    }
}
~~~

Biên dịch **không có** `-parameters`:

~~~bash
javac -encoding UTF-8 ParameterMetadataDemo.java
java ParameterMetadataDemo
~~~

Đầu ra điển hình với `javac` Java 17/21 không lưu thuộc tính tên tham số:

~~~text
dispatch parameters=3
0: name=arg0, present=false, type=String, generic=java.lang.String, varargs=false, label=user, typeMark=true
1: name=arg1, present=false, type=int, generic=int, varargs=false, label=-, typeMark=false
2: name=arg2, present=false, type=String[], generic=java.lang.String[], varargs=true, label=-, typeMark=false
generic first=java.util.List<T>
generic second=T
erased second=java.lang.Number
constructor name=arg0, present=false
owner is constructor=true
~~~

Sau đó **biên dịch lại cùng source** (ghi đè class file vừa sinh), nhưng có `-parameters`:

~~~bash
javac -parameters -encoding UTF-8 ParameterMetadataDemo.java
java ParameterMetadataDemo
~~~

Đầu ra trở thành:

~~~text
dispatch parameters=3
0: name=recipient, present=true, type=String, generic=java.lang.String, varargs=false, label=user, typeMark=true
1: name=retries, present=true, type=int, generic=int, varargs=false, label=-, typeMark=false
2: name=labels, present=true, type=String[], generic=java.lang.String[], varargs=true, label=-, typeMark=false
generic first=java.util.List<T>
generic second=T
erased second=java.lang.Number
constructor name=title, present=true
owner is constructor=true
~~~

Điều quan trọng từ ví dụ:

- `getParameters()` luôn thấy ba tham số `dispatch` trong cả hai lần biên dịch; **chỉ metadata tên thay đổi**.
- `@Name("user")` trên formal parameter lấy qua `Parameter.getAnnotation(Name.class)`; không phụ thuộc việc có tên source.
- `@TypeMark` là annotation trên **type use**, phải lấy qua `getAnnotatedType()`; không đồng nhất với annotation gắn trực tiếp vào declaration của parameter.
- `String... labels` ở bytecode là `String[]`, nên `getType().getSimpleName()` hiển thị `String[]`; `Parameter.isVarArgs()` xác nhận tham số cuối của executable varargs.
- `aggregate(List<T>, T)` trả `List<T>` và `T` ở generic metadata; erasure của `T extends Number` là `Number`. Khi tìm method bằng `getDeclaredMethod`, sử dụng `List.class` và `Number.class`, không truyền `T` hay `List<T>`.
- `getDeclaringExecutable()` trả lại constructor tương ứng; trong ví dụ, object phản chiếu constructor được so bằng `==` và kết quả đúng cho đối tượng `Parameter` thuộc cùng `Constructor` đó.

**Lưu ý reproducibility:** Hai lệnh `javac` trên chạy tuần tự, lần sau sinh lại class file với thuộc tính `MethodParameters`. Nếu dùng công cụ build có incremental compilation, hãy bảo đảm toàn bộ code cần metadata thật sự được biên dịch với tùy chọn tương ứng.

## 5. `Parameter` cho biết những gì ngoài tên?

### 5.1. Kiểu khai báo, kiểu generic, và chú thích trên kiểu

Các API này trả thông tin khác nhau:

- `getType()`: `Class<?>` đã erasure; ví dụ `List<String>` thành `List.class`, `T extends Number` thành `Number.class`, `String...` thành `String[].class`.
- `getParameterizedType()`: `java.lang.reflect.Type` có thể chứa `ParameterizedType` (ví dụ `List<T>`), `TypeVariable` (`T`), `WildcardType`, `GenericArrayType`. Nó không nhất thiết là một `Class<?>`.
- `getAnnotatedType()`: mang các annotation loại `TYPE_USE` trong khai báo kiểu, có thể kết hợp với các kiểu annotated chuyên biệt cho generic/nested components.

Không chuyển ép thẳng `(Class<?>) parameter.getParameterizedType()`; phép cast có thể gây `ClassCastException` nếu `Type` thực tế là `ParameterizedType` hoặc `TypeVariable`.

### 5.2. Annotation trên parameter và annotation trên type

Hai dạng có thể trông gần giống nhau trong source:

~~~java
// Ví dụ khai báo chú thích minh họa, không phải class độc lập.
void process(@ParamLabel("id") String id) {}
void processType(@TypeConstraint String id) {}
~~~

Khi annotation có `@Target(PARAMETER)`, `Parameter.getAnnotation(...)` hoặc `getDeclaredAnnotations()` đọc annotation trên declaration của formal parameter. Khi annotation có `@Target(TYPE_USE)`, dùng `Parameter.getAnnotatedType()` rồi `getAnnotation(...)` trên `AnnotatedType`. Annotation có thể được thiết kế cho cả hai vị trí; khi đó phải hiểu cấu trúc đặt annotation và ý nghĩa của framework. `@Retention(RUNTIME)` cần thiết để Reflection có thể truy cập annotation tương ứng lúc chạy.

`getAnnotations()` và `getDeclaredAnnotations()` trên `Parameter` trả metadata annotation của chính parameter; `getAnnotationsByType(...)` xử lý cơ chế repeatable annotation thích hợp. Không nhầm với annotation trên executable (`Method.getAnnotations()`) hoặc trên lớp khai báo.

### 5.3. Modifier, implicit/synthetic và varargs

- `Parameter.getModifiers()` và `Modifier.isFinal(...)` cho biết modifier của formal parameter **nếu metadata phù hợp hiện diện**; không giả định compiler nào cũng lưu giống nhau khi thiếu `-parameters`.
- `isImplicit()` mô tả một parameter **được khai báo ngầm trong source** theo quy tắc ngôn ngữ; `isSynthetic()` là tham số không xuất hiện trực tiếp/không được khai báo ngầm theo mô hình source, do compiler tạo. Những cờ này phụ thuộc thông tin class file và cách compiler phát sinh bytecode.
- `isVarArgs()` mô tả parameter varargs (thường là tham số cuối của method/constructor có `isVarArgs()==true`). Nó không chỉ kiểm tra xem kiểu có phải array: một method nhận `String[]` thường có thể cho `isVarArgs()==false`.

**Không suy luận `isImplicit()==false` hay `isSynthetic()==false` chứng minh tham số tồn tại nguyên văn trong source.** Một số compiler/bytecode transformer không lưu metadata đầy đủ; cũng có các tham số ẩn ở constructor class lồng, enum và các cấu trúc đặc biệt.

## 6. Tham số của constructor: không phải mọi thứ đều thấy giống source

`Constructor<?>` cũng là `Executable`, nên gọi `getParameters()` giống như với `Method`. Tuy nhiên, constructor của một số dạng class có tham số ngầm định/được compiler thêm vào:

1. **Non-static inner member class:** constructor thường nhận một tham số bổ sung để mang enclosing instance. Với `Outer.Inner`, danh sách kiểu của constructor qua Reflection có thể bắt đầu bằng `Outer.class` dù source không viết tên biến như vậy.
2. **Enum:** constructor ở bytecode có thêm tham số do runtime/compiler quản lý, ví dụ tên và ordinal; không coi đó là API constructor người dùng có thể gọi để sinh enum mới. Reflection cấm dùng `Constructor.newInstance` để tạo enum constant.
3. **Record canonical constructor:** danh sách parameter tương ứng với record components; compiler/JLS có quy tắc metadata riêng cho canonical constructor. Đừng gộp trường hợp này với “compiler luôn có tên tham số nguồn hay không”.
4. **Anonymous/local class:** constructor bytecode có thể có tham số cho captured variables hoặc enclosing context. Chi tiết phụ thuộc compiler.

Ví dụ mảnh source hợp lệ:

~~~java
class Outer {
    class Inner {
        Inner(int number) {}
    }
}
~~~

Với `javac`, tìm constructor Reflection của inner class thường phải dùng `getDeclaredConstructor(Outer.class, int.class)`. `getParameterCount()` sẽ bao gồm tham số outer, nên kết quả có thể khác số formal parameters được viết trong constructor source. Để ánh xạ parameter vào dữ liệu người dùng, phải có chính sách nhận dạng và loại trừ các tham số runtime đặc biệt; **không cứng hóa** rằng luôn bỏ tham số index 0 cho mọi constructor.

## 7. Dùng `Parameter` trong thiết kế framework

Giả sử một controller có method:

~~~java
// Mảnh minh họa API framework, annotation giả định đã được định nghĩa.
void find(@RequestKey("id") String id, int page) {}
~~~

Một binder có thể sử dụng thứ tự kiểm tra sau:

1. Đọc `Method.getParameters()`; mỗi phần tử có thứ tự và kiểu trực tiếp.
2. Nếu parameter có annotation nêu tên request rõ ràng, dùng giá trị của annotation vì đó là hợp đồng API.
3. Nếu không có annotation, chỉ dùng `getName()` khi `isNamePresent()==true`. Nếu metadata thiếu, báo lỗi cấu hình hoặc yêu cầu người dùng khai báo tên. Không coi `arg0` là tên HTTP/JSON công khai.
4. Xác định kiểu qua `getType()` và `getParameterizedType()`; dùng converter tường minh cho `String -> int`, `List<T>`, enum, nullability. `Parameter` **không** tự thực hiện chuyển đổi.
5. Xét `isVarArgs()` khi chuẩn bị argument cho method varargs; ở mức Reflection, tham số varargs có kiểu array (`String[]`), và phải chuẩn bị đối tượng array phù hợp khi gọi `Method.invoke`.
6. Ghép mảng arguments theo thứ tự parameter. Bắt lỗi phân giải, chuyển kiểu, truy cập và lời gọi riêng biệt.

Không sử dụng tên tham số như khóa cache độc lập với class/method: hai overload có thể cùng tên `value` nhưng khác kiểu; metadata source có thể thay đổi sau refactor. Khóa method đáng tin cậy phải dựa vào executable, class khai báo và kiểu tham số.

### Khi nào không cần `getParameters()`?

Nếu chỉ cần định danh overload `run(String, int)`, thường có thể dùng `getDeclaredMethod("run", String.class, int.class)` hoặc `getParameterTypes()`. Nếu chỉ cần số lượng, `getParameterCount()` rõ ràng hơn. Chỉ tạo/duyệt `Parameter[]` khi cần các tính chất ở cấp **từng tham số** (tên, annotation, generic, modifier, varargs).

## 8. Những giới hạn và lỗi phổ biến

| Hiểu nhầm | Hệ quả và cách sửa |
| --- | --- |
| “Không bật `-parameters` thì `getParameters()` trả `[]`.” | Sai: vẫn thấy tham số theo chữ ký; thường tên chỉ là `arg0`… |
| “`getName()` luôn là tên source.” | Sai: phải kiểm tra `isNamePresent()`. |
| “`-g:vars` đủ cho Core Reflection tên tham số.” | Sai: dùng `-parameters` cho metadata `MethodParameters`. |
| “`getType()` giữ đủ `List<String>`.” | Sai: dùng `getParameterizedType()` cho generic metadata. |
| “Parameter annotation và type-use annotation là một.” | Sai: phân biệt `getAnnotation(...)` với `getAnnotatedType()`. |
| “Varargs trong Reflection không phải array.” | Sai: `String...` mang kiểu `String[]`, kiểm tra thêm `isVarArgs()`. |
| “Số parameter ở constructor luôn bằng số viết trong source.” | Không đúng cho một số inner/enum/local/anonymous class. |
| “Method được override luôn giữ nguyên tên tham số của cha.” | Sai: khai báo ở lớp con có thể dùng tên khác; tên không tham gia override/signature. |
| “Parameter tự ép kiểu dữ liệu truyền vào.” | Sai: `Parameter` chỉ mô tả metadata, converter/invoker phải xử lý argument. |
| “`isSynthetic()==false` chứng minh người lập trình đã viết tham số.” | Sai: các cờ metadata có giới hạn và phụ thuộc compiler. |

### Xử lý ngoại lệ có ý nghĩa

- `getDeclaredMethod(...)` / `getDeclaredConstructor(...)` có thể ném `NoSuchMethodException` nếu sai kiểu tham số sau erasure.
- `getParameters()` có thể ném `MalformedParametersException` do thuộc tính tên/modifier tham số trong class file bị lỗi.
- Khi phân tích `getParameterizedType()`, có thể gặp `GenericSignatureFormatError`, `TypeNotPresentException` hoặc `MalformedParameterizedTypeException` với generic signature không hợp lệ/không giải được.
- Khi gọi executable sau phân tích metadata, kiểm tra cả truy cập, số lượng/kiểu argument và exception do chính method/constructor ném; đó là tầng khác với việc đọc `Parameter`.

Không catch tất cả rồi coi metadata thiếu đồng nghĩa constructor/method không tồn tại. Trong công cụ phân tích bytecode, báo riêng nguồn lớp, executable, index tham số và loại lỗi để điều tra.

## 9. Bài tập kiểm chứng

1. Biên dịch demo hai lần, không và có `-parameters`; so sánh chỉ `getName()` và `isNamePresent()`, trong khi kiểu, generic và annotation vẫn giữ nguyên.
2. Thay `dispatch(String... labels)` bằng `dispatch(String[] labels)`, điều chỉnh chữ ký cho phù hợp và quan sát `isVarArgs()`.
3. Thêm một parameter `List<? extends Number> amounts` và in `getParameterizedType().getTypeName()`; kiểm tra khác biệt so với `getType()`.
4. Tạo hai overload `load(String id)` và `load(int id)`; chứng minh tên `id` không đủ để phân biệt chúng.
5. Đặt annotation `@Target(PARAMETER)` trên một parameter, annotation `@Target(TYPE_USE)` trên kiểu của parameter; đọc từng loại qua đúng API.
6. Tạo inner member class `Outer.Inner` có constructor `Inner(int id)`; kiểm tra danh sách kiểu và số lượng tham số runtime.
7. Viết binder nhỏ: ưu tiên annotation nêu tên, dùng `getName()` chỉ khi `isNamePresent()`, còn thiếu tên thì phát hiện lỗi rõ ràng.
8. Thử phương thức generic có `<T extends Number>`; so sánh `getType()` và `getParameterizedType()` của `T`.

## 10. Tài liệu tham khảo

- [Java SE 17 — Executable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Executable.html): `getParameters()`, `getParameterTypes()`, `getGenericParameterTypes()` và `getParameterAnnotations()`.
- [Java SE 17 — Parameter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Parameter.html): tên, annotation, generic type, implicit/synthetic/varargs, declaring executable.
- [javac Java 17 — Options](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html): tùy chọn `-parameters` ghi formal parameter names để Reflection truy cập.
- [Java Language Specification 17 — `8.4.1 Formal Parameters](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.1): formal parameters, modifier, varargs.
- [JVM Specification 17 — MethodParameters Attribute](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.24): metadata tên/tham số trên class file.

**Nguyên tắc cốt lõi:** `getParameters()` cho biết **tham số runtime của executable**, còn `Parameter.getName()` chỉ đáng tin là tên source khi có metadata và `isNamePresent()` xác nhận. Dùng `getType()` cho kiểu erasure, `getParameterizedType()` cho generic, `getAnnotatedType()` cho annotation trên kiểu, và `getAnnotation(...)` cho annotation của parameter.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executable-parameters-003">getParameterTypes khác getGenericParameterTypes</a>

<details>
<summary>Xem chi tiết</summary>

# `getParameterTypes()` khác `getGenericParameterTypes()` trong Java Reflection

## 1. Mục tiêu: phân biệt kiểu tham số runtime và chữ ký generic

Khi dùng Reflection trên `Method` hoặc `Constructor<?>`, hai phương thức cùng mô tả tham số của một *executable* nhưng trả về các biểu diễn khác nhau:

- **`getParameterTypes()`** trả `Class<?>[]`: các lớp/kiểu thực tế theo **chữ ký đã erasure** (kiểu mà JVM dùng làm parameter descriptor). Nó phù hợp để tra cứu method/constructor theo chữ ký và xử lý kiểu runtime.
- **`getGenericParameterTypes()`** trả `java.lang.reflect.Type[]`: các kiểu tham số theo **thông tin generic trong khai báo** nếu metadata hiện diện. Nó có thể phản ánh `List<String>`, `T`, `T[]`, `? extends Number`…, thay vì chỉ kiểu erasure.

Cả hai là thành viên của lớp cha `java.lang.reflect.Executable`, nên dùng được cho **`Method` lẫn `Constructor`**. Các mảng được trả theo thứ tự tham số, nhưng có một ngoại lệ quan trọng ở constructor có tham số ngầm (mục 7).

| Tiêu chí | `getParameterTypes()` | `getGenericParameterTypes()` |
| --- | --- | --- |
| Kiểu trả về | `Class<?>[]` | `Type[]` |
| `List<String>` | `List.class` | `ParameterizedType` biểu diễn `List<String>` |
| `T extends Number` | `Number.class` (erasure của `T`) | `TypeVariable` biểu diễn `T` |
| `T[]` | `Number[].class` nếu erasure `T` là `Number` | `GenericArrayType` cho `T[]` |
| `String[]` | `String[].class` | Thường là `Class` biểu diễn `String[]` |
| Nhận biết wildcard | Không còn wildcard trong `Class<?>` | Wildcard nằm trong `ParameterizedType`, truy cập qua `WildcardType` |
| Chọn overload / lookup | **Đúng API**: dùng các `Class<?>` erasure để tìm phương thức | Không truyền trực tiếp `Type[]` vào `getDeclaredMethod(...)` |
| Mô tả generic cho schema/serialization | Không có generic arguments | Có thể lấy generic arguments/bounds từ chữ ký |
| Phụ thuộc chữ ký generic metadata | Không cần metadata `Signature` để có erased parameter types | Thông tin generic chi tiết phụ thuộc vào generic signature được lưu trong class file |

**Không nên hiểu `getGenericParameterTypes()` là “lấy kiểu thật của object đang truyền vào”**. Cả hai đọc **khai báo của method/constructor**, không đọc dữ liệu của các instance tham số lúc ứng dụng chạy.

## 2. Căn nguyên: type erasure trong Java

Generics của Java chủ yếu thực hiện kiểm tra kiểu lúc biên dịch. Sau khi biên dịch, JVM method descriptor sử dụng các kiểu đã xóa phần lớn thông tin generic (**type erasure**). Ví dụ các khai báo:

```java
void save(java.util.List<String> values) {}
void accept(T number) {}
```

Đây là **mảnh minh họa**, và `T` phải được khai báo ở cấp class hoặc method. Nếu `T extends Number`, kiểu erasure của `T` là `Number`. `List<String>` có kiểu erasure là `List`. Phần `String` và biến kiểu `T` có thể được ghi vào thuộc tính generic signature của class file, giúp Reflection tái tạo `Type` với nhiều chi tiết hơn.

Các quy tắc cốt lõi:

1. `List<String>` và `List<Integer>` có **cùng kiểu erasure `List`**; do đó không thể có hai overload chỉ khác nhau ở hai generic arguments này.
2. Với `<T extends Number & Comparable<T>>`, erasure của `T` là **bound đầu tiên** `Number`. Với biến kiểu không có bound tường minh, bound ngầm là `Object`.
3. Nếu kiểu parameter là `T[]`, sau erasure `T` thành bound đầu tiên, nên parameter descriptor là mảng của kiểu bound ấy. Generic form vẫn có thể là `GenericArrayType`.
4. `String...` là cú pháp tham số varargs; ở JVM, kiểu tham số tương ứng là **`String[]`**. `Executable.isVarArgs()` cho biết executable có khai báo varargs hay không.
5. `getGenericParameterTypes()` không bảo đảm từng `Type` đều là một `ParameterizedType`: kiểu không generic như `int`, `String` hay `String[]` thường vẫn được biểu diễn bởi `Class<?>`.

Vì vậy hai API không mâu thuẫn: một API cung cấp danh sách kiểu cho **chữ ký runtime**, API kia giữ lại **ngữ nghĩa generic của khai báo** khi có dữ liệu.

## 3. Ví dụ Java 17+ đầy đủ, biên dịch và chạy được

Tạo tệp **`ParameterKindsDemo.java`** với mã sau (không khai báo `package`):

```java
import java.lang.reflect.*;
import java.util.*;

public class ParameterKindsDemo {
    public ParameterKindsDemo(List<Integer> values) {}

    public static <T extends Number & Comparable<T>> void analyze(
            List<String> names,
            Map<String, ? extends Number> scores,
            T value,
            T[] history,
            List<? super Integer> sink,
            String... tags) {}

    private static String kind(Type type) {
        if (type instanceof Class<?>) return "Class";
        if (type instanceof ParameterizedType) return "ParameterizedType";
        if (type instanceof TypeVariable<?>) return "TypeVariable";
        if (type instanceof GenericArrayType) return "GenericArrayType";
        if (type instanceof WildcardType) return "WildcardType";
        return "Other";
    }

    private static void describe(Executable executable) {
        System.out.println("member: " + (executable instanceof Method
                ? executable.getName() : "<init>"));
        System.out.println("varargs: " + executable.isVarArgs());
        Class<?>[] erased = executable.getParameterTypes();
        Type[] generic = executable.getGenericParameterTypes();
        for (int i = 0; i < erased.length; i++) {
            System.out.println(i + ": " + erased[i].getTypeName()
                    + " | " + generic[i].getTypeName()
                    + " | " + kind(generic[i]));
        }
    }

    public static void main(String[] args) throws Exception {
        Method method = ParameterKindsDemo.class.getDeclaredMethod(
                "analyze", List.class, Map.class, Number.class,
                Number[].class, List.class, String[].class);
        describe(method);
        Constructor<ParameterKindsDemo> constructor =
                ParameterKindsDemo.class.getDeclaredConstructor(List.class);
        describe(constructor);

        ParameterizedType map = (ParameterizedType)
                method.getGenericParameterTypes()[1];
        WildcardType wildcard = (WildcardType) map.getActualTypeArguments()[1];
        System.out.println("upper bound: "
                + Arrays.toString(wildcard.getUpperBounds()));
        TypeVariable<Method> t = method.getTypeParameters()[0];
        System.out.println("T bounds: " + Arrays.toString(t.getBounds()));
    }
}
```

Trong thư mục chứa file, chạy:

```bash
javac ParameterKindsDemo.java
java ParameterKindsDemo
```

**Kết quả đã xác minh:**

```text
member: analyze
varargs: true
0: java.util.List | java.util.List<java.lang.String> | ParameterizedType
1: java.util.Map | java.util.Map<java.lang.String, ? extends java.lang.Number> | ParameterizedType
2: java.lang.Number | T | TypeVariable
3: java.lang.Number[] | T[] | GenericArrayType
4: java.util.List | java.util.List<? super java.lang.Integer> | ParameterizedType
5: java.lang.String[] | java.lang.String[] | Class
member: <init>
varargs: false
0: java.util.List | java.util.List<java.lang.Integer> | ParameterizedType
upper bound: [class java.lang.Number]
T bounds: [class java.lang.Number, java.lang.Comparable<T>]
```

**Phân tích từng dòng:**

- Tham số 0: `List<String>` bị erasure thành `List`. `getParameterTypes()[0]` trả `List.class`; `getGenericParameterTypes()[0]` là `ParameterizedType` giữ đối số kiểu `String`.
- Tham số 1: `Map<String, ? extends Number>` bị erasure thành `Map`. Generic form giữ cả hai type arguments; phần thứ hai là `WildcardType` với upper bound `Number`.
- Tham số 2: `T` bị erasure thành `Number` bởi bound đầu tiên. Generic form là `TypeVariable` có tên `T`.
- Tham số 3: `T[]` có erasure `Number[]`, còn generic form là `GenericArrayType` mà component type là `T`.
- Tham số 4: `List<? super Integer>` bị erasure thành `List`, nhưng generic form giữ wildcard với lower bound `Integer`.
- Tham số 5: `String...` là `String[]` trên cả hai kết quả, và `isVarArgs()` là `true`. Không phải mọi `Type` ở generic array đều là cấu trúc generic phức tạp.
- Constructor nhận `List<Integer>`: hai API hoạt động giống như trên method; constructor có `ParameterizedType` cho tham số này.
- `method.getTypeParameters()[0]` lấy **biến kiểu khai báo bởi method `<T ...>`**. `getBounds()` cho biết hai bounds `Number` và `Comparable<T>`, trong khi erasure lấy bound đầu tiên.

Tên lớp triển khai cụ thể của `ParameterizedType` hay `TypeVariable` do JDK quyết định; ví dụ chỉ in các **giao diện** `Type` ổn định thay vì phụ thuộc vào tên nội bộ như `ParameterizedTypeImpl`.

## 4. Làm việc đúng với mô hình `Type`

`java.lang.reflect.Type` là giao diện chung. Các hình thái cần hiểu:

| Hình thái `Type` | Ví dụ kiểu khai báo | Cách phân tích |
| --- | --- | --- |
| `Class<?>` | `String`, `int`, `String[]`, kiểu raw `List` | `Class#getName()`, `isArray()`, `getComponentType()` |
| `ParameterizedType` | `List<String>`, `Map<String, List<Integer>>` | `getRawType()`, `getActualTypeArguments()`, `getOwnerType()` |
| `TypeVariable<?>` | `T` trong `<T extends Number>` | `getName()`, `getBounds()`, `getGenericDeclaration()` |
| `GenericArrayType` | `T[]`, `List<String>[]` trong chữ ký hợp lệ | `getGenericComponentType()` |
| `WildcardType` | `? extends Number`, `? super Integer` | `getUpperBounds()`, `getLowerBounds()` |

Một `WildcardType` thường xuất hiện **bên trong đối số kiểu của `ParameterizedType`** (ví dụ `List<? super Integer>`), không nhất thiết là `Type` trả trực tiếp cho một formal parameter; Java không cho khai báo tham số có kiểu đứng một mình là `?`.

Ví dụ kiểm tra generic arguments trong `Map<String, ? extends Number>` dựa trên `method` của chương trình ở mục 3:

```java
Type parameter = method.getGenericParameterTypes()[1];

if (parameter instanceof ParameterizedType p) {
    System.out.println(p.getRawType().getTypeName()); // java.util.Map
    for (Type argument : p.getActualTypeArguments()) {
        System.out.println(argument.getTypeName());
    }
}
```

Đây là **mảnh mã đặt trong method**. Nội dung hai argument lần lượt là `java.lang.String` và `? extends java.lang.Number`. Nếu cần đọc bound, kiểm tra `argument instanceof WildcardType` rồi dùng `getUpperBounds()`/`getLowerBounds()`. Không ép trực tiếp wildcard thành `Class<?>`.

Với `T[]`, nếu `type instanceof GenericArrayType array`, hãy kiểm tra `array.getGenericComponentType()`: ở đây là biến kiểu `T`. Nếu kiểu tham số vốn là mảng thông thường như `String[]`, `type instanceof Class<?> clazz` và `clazz.isArray()` đúng; đây là hai đường xử lý khác nhau.

### Kiểu raw và khi không có thông tin generic chi tiết

Nếu parameter được khai báo **`List values`** (raw type), `getGenericParameterTypes()` có thể trả một `Class` tương ứng `List.class`. Không có `String`/`Integer` nào để suy ra. Nếu class file thiếu hoặc không giữ được metadata generic thích hợp, API không thể tự phục hồi type arguments từ dữ liệu runtime. Cần cung cấp schema, `Type` token hoặc metadata riêng nếu logic phụ thuộc vào generic argument.

Không dùng chuỗi `type.getTypeName()` như đầu vào duy nhất để phân tích generic: định dạng chuỗi thuận tiện để hiển thị, nhưng để xây dựng schema đáng tin nên đi theo cấu trúc `ParameterizedType`, `TypeVariable`, `WildcardType` và `GenericArrayType`.

## 5. Tra cứu executable: dùng kiểu sau erasure

`Class.getDeclaredMethod(String, Class<?>...)` và `Class.getMethod(String, Class<?>...)` nhận **`Class<?>` của formal parameter types**, không nhận đối tượng `ParameterizedType`. Trong ví dụ mục 3, lệnh tra cứu đúng là:

```java
Method method = ParameterKindsDemo.class.getDeclaredMethod(
        "analyze",
        List.class,
        Map.class,
        Number.class,
        Number[].class,
        List.class,
        String[].class);
```

Đây là **mảnh mã** có thể đặt trong phương thức khai báo `throws NoSuchMethodException`. Việc truyền `Number.class`/`Number[].class` cho `T`/`T[]` tương ứng với erasure của bounded variable; `String[].class` là descriptor đúng cho varargs `String...`. `List<String>.class` hoặc `List<Integer>.class` **không tồn tại trong cú pháp class literal Java**.

Ví dụ hai khai báo sau trong cùng class **không hợp lệ**, do có cùng name và erasure:

```java
// Minh họa lỗi biên dịch: name clash (không đặt cả hai trong class thực tế)
void handle(List<String> values) {}
void handle(List<Integer> values) {}
```

Nếu đang viết bộ chọn method overload, đừng so sánh generic arguments như thể JVM phân biệt hai overload ở cùng chữ ký erasure. Dùng `Class<?>[]` để lookup, rồi kiểm tra `Type[]` cho chính sách nghiệp vụ (chẳng hạn validation/serialization).

### Chữ ký generic không phải kiểm tra kiểu động của dữ liệu

Giả sử khai báo `void save(List<String> values)`. `getGenericParameterTypes()[0]` có thể mô tả `List<String>`, nhưng không có nghĩa runtime kiểm chứng rằng từng phần tử trong một `List` luôn là `String`. Với raw types, unchecked casts hoặc reflection, dữ liệu thực tế có thể không tuân theo ý nghĩa generic ở nguồn Java. Một thư viện serializer muốn xác minh dữ liệu cần kiểm tra giá trị và chính sách riêng.

## 6. Tham số `Parameter`, tên tham số và type-use annotations

Ngoài hai mảng ở trên, `Executable` còn cung cấp:

- `getParameters()` → `Parameter[]`: metadata của từng formal parameter, tên (nếu có), modifiers, tình trạng implicit/synthetic.
- `Parameter.getType()` → `Class<?>` (erasure).
- `Parameter.getParameterizedType()` → `Type` (thông tin generic của parameter đó).
- `getAnnotatedParameterTypes()` → `AnnotatedType[]`: type-use annotations tại vị trí kiểu tham số (ví dụ chú thích `@NonNull` đặt trên type use nếu annotation hỗ trợ `ElementType.TYPE_USE` và được giữ ở runtime).
- `getParameterCount()` → số lượng formal parameters theo mô hình executable.
- `isVarArgs()` → phân biệt khai báo varargs với tham số mảng thông thường.
- `getTypeParameters()` → `TypeVariable<?>[]` được khai báo trên executable (khác với mảng `Type[]` của các tham số).

**Biên dịch bằng `javac -parameters`** giúp ghi tên tham số nguồn vào class file để `Parameter.getName()` có thể cung cấp tên thực (trong điều kiện phù hợp), và `isNamePresent()` cho biết tên có mặt. Nó **không phải là cờ bật generic signatures**. `getGenericParameterTypes()` vẫn có thể đọc generic signature của bytecode được biên dịch thông thường khi metadata đó tồn tại.

Ví dụ, nếu `method.getParameters()[0].getName()` trả tên như `arg0` thì không thể tự kết luận generic type của parameter đã biến mất; metadata tên và metadata generic là hai cơ chế khác nhau.

## 7. Bẫy tinh vi: tham số ngầm ở constructor inner class

Một số constructor có **tham số ngầm hoặc synthetic** do trình biên dịch tạo ra, đặc biệt constructor của non-static inner class. Javadoc của `Executable.getGenericParameterTypes()` cảnh báo rằng **số lượng phần tử có thể khác `getParameterTypes()`**, tùy class file có generic information hay không: khi generic information hiện diện, API có thể chỉ báo các tham số được khai báo tường minh trong source; những tham số implicit/synthetic có thể không xuất hiện trong mảng generic.

Đây là ví dụ Java 17+ độc lập. Tạo tệp **`InnerReflectionDemo.java`**:

```java
import java.lang.reflect.*;
import java.util.*;

public class InnerReflectionDemo {
    class Inner {
        Inner(List<String> names) {}
    }

    public static void main(String[] args) {
        Constructor<?> c = Inner.class.getDeclaredConstructors()[0];
        System.out.println("raw count: " + c.getParameterTypes().length);
        System.out.println("generic count: " + c.getGenericParameterTypes().length);

        for (Class<?> type : c.getParameterTypes()) {
            System.out.println("raw: " + type.getTypeName());
        }
        for (Type type : c.getGenericParameterTypes()) {
            System.out.println("generic: " + type.getTypeName());
        }
    }
}
```

Chạy:

```bash
javac InnerReflectionDemo.java
java InnerReflectionDemo
```

Kết quả đã xác minh với `javac` thông thường:

```text
raw count: 2
generic count: 1
raw: InnerReflectionDemo
raw: java.util.List
generic: java.util.List<java.lang.String>
```

Lớp `Inner` là non-static inner class nên JVM constructor nhận **tham chiếu đến instance của `InnerReflectionDemo`** để xác định enclosing instance; tham số này không được viết trong source constructor `Inner(List<String> names)`. Vì vậy `getParameterTypes()` liệt kê hai tham số runtime (`InnerReflectionDemo` và `List`), trong khi generic signature mô tả tham số khai báo tường minh là `List<String>`.

**Hệ quả:** Không mặc định rằng `getParameterTypes()[i]` luôn tương ứng với `getGenericParameterTypes()[i]` ở mọi executable; vòng `for` trong ví dụ mục 3 **được viết cho method/constructor không có tham số ngầm**, không phải một bộ phân tích generic tổng quát cho mọi constructor. Trước khi ghép chỉ số, kiểm tra độ dài và tham số implicit/synthetic; nếu cần metadata theo từng tham số, cân nhắc `getParameters()` cùng `Parameter.getParameterizedType()` và đặc điểm biên dịch của class. Các trường hợp constructor của enum, inner class hoặc bytecode được tạo bởi công cụ cần được xem xét riêng thay vì đoán tương quan chỉ số.

Chính vì sự khác biệt metadata này, một chương trình khai thác Reflection tổng quát cần tránh giả thiết hai mảng có độ dài bằng nhau. Lỗi `ArrayIndexOutOfBoundsException` hay gán nhầm generic type cho enclosing instance là hậu quả có thể xảy ra nếu cứ lặp theo chiều dài `getParameterTypes()` rồi đọc `generic[i]`.

## 8. Trường hợp khó và những ngoại lệ khi đọc generic signature

Các API generic Reflection phụ thuộc metadata chữ ký được biên dịch/cung cấp. Theo Javadoc `Executable.getGenericParameterTypes()`, một số lỗi có thể xảy ra khi đọc chữ ký của bytecode không hợp lệ hoặc không còn nhất quán:

| Ngoại lệ / Error | Nguyên nhân |
| --- | --- |
| `GenericSignatureFormatError` | Generic signature trong class file không tuân định dạng JVM |
| `TypeNotPresentException` | Generic signature tham chiếu kiểu không thể tìm thấy |
| `MalformedParameterizedTypeException` | Một parameterized type trong chữ ký không thể tạo dưới mô hình phản chiếu |

Đây không phải kết quả thông thường khi biên dịch Java chuẩn trên classpath/module path nhất quán, nhưng hữu ích khi xử lý bytecode cũ, bytecode generator, shading, version mismatch hoặc thư viện bị thiếu. Không nên dùng `catch (Exception)` để ngầm coi mọi dữ liệu signature lỗi là “không generic”; `GenericSignatureFormatError` là `Error`, không phải `Exception`.

Các lưu ý khác:

- `getDeclaredMethods()` có thể thấy **bridge/synthetic methods** do compiler tạo. Khi suy luận về API nguồn, kiểm tra `Method.isBridge()`/`isSynthetic()`; bản bridge có chữ ký sau erasure khác method nguồn.
- Raw type trong signature có thể chỉ được biểu diễn bằng `Class<?>`; `getGenericParameterTypes()` không hứa trả `ParameterizedType` cho mọi parameter.
- Với `TypeVariable`, bounds của method/class được khai báo độc lập; cùng tên `T` trong hai method không nhất thiết cùng declaration. Có thể dùng `getGenericDeclaration()` để biết biến kiểu thuộc nơi khai báo nào.
- `getGenericParameterTypes()` giữ những thông tin generic còn có trong **declaration**, không thể dựng `List<String>` từ `List<?>` do caller truyền hoặc suy ra `String` từ phần tử hiện tại.

## 9. Quy trình áp dụng khi viết framework

1. **Xác định mục đích:** cần tìm executable/gọi method theo chữ ký JVM, hay cần hiểu generic contract để chuyển đổi JSON, binding, validation?
2. **Tìm executable bằng erased types:** dùng `getDeclaredMethod(name, Class<?>...)`, `getDeclaredConstructor(Class<?>...)` hoặc liệt kê danh sách rồi lọc. Với overload, số/loại erased formal parameters là dữ kiện quan trọng.
3. **Đọc generic contract:** gọi `getGenericParameterTypes()` (hoặc `Parameter.getParameterizedType()` nếu cần metadata theo từng tham số) và phân tích `Type` theo loại thực tế.
4. **Xử lý type variables, wildcards và generic arrays:** duyệt cấu trúc `Type`; không ép mọi `Type` về `Class<?>`. Nếu cần resolve `T` trong subclass cụ thể, phải có **binding ngữ cảnh** từ generic superclass/interfaces hoặc type token; Reflection không tự thay `T` bằng type argument của mọi lời gọi.
5. **Cẩn trọng với tham số ngầm:** nếu là constructor đặc biệt, không căn theo chỉ số hai mảng trước khi xác nhận tương ứng.
6. **Kiểm tra đầu vào thật ở runtime:** chữ ký generic mô tả khai báo, còn dữ liệu thực tế cần validation riêng.
7. **Báo lỗi metadata minh bạch:** xử lý trường hợp signature không hợp lệ hoặc thiếu dependency; đừng tạo schema sai khi thông tin generic không đủ.

Ví dụ chọn đúng API:

| Mục tiêu | Ưu tiên |
| --- | --- |
| Tìm method có tham số `List<String>` trong class | `getDeclaredMethod("name", List.class)` |
| Xác định list element type khai báo | `getGenericParameterTypes()` rồi đọc `ParameterizedType.getActualTypeArguments()` |
| Tìm bound của `T extends Number` | `TypeVariable.getBounds()` |
| Kiểm tra phương thức có varargs | `Executable.isVarArgs()` |
| Phân tích type-use annotation | `getAnnotatedParameterTypes()` |
| Lấy tên tham số nguồn nếu còn metadata | `getParameters()` và `Parameter.isNamePresent()` |
| Thực hiện method với tham số tại runtime | Chuẩn bị object đúng erased types rồi gọi `Method.invoke(...)`; không dùng `ParameterizedType` làm runtime `Class` |

## 10. Sai lầm phổ biến và bài tập tự kiểm tra

| Quan niệm sai | Cách hiểu đúng |
| --- | --- |
| “Hai API chỉ khác kiểu trả về mảng, thông tin giống nhau.” | Một bên là `Class<?>` erasure; bên kia có cấu trúc `Type` mô tả generic declaration. |
| “`getGenericParameterTypes()[0]` luôn là `ParameterizedType`.” | Có thể là `Class`, `TypeVariable` hoặc `GenericArrayType`; tùy khai báo. |
| “`getParameterTypes()` cho `List<String>`.” | Kết quả là `List.class`; generic argument đã erasure. |
| “Có thể ép `Type` thành `Class<?>` cho mọi tham số.” | Cast sai với parameterized type/type variable/generic array. |
| “Hai mảng luôn dài bằng nhau.” | Constructor inner class có tham số ngầm có thể khiến kích thước khác nhau. |
| “`-parameters` mới làm generic Reflection hoạt động.” | Cờ này chủ yếu lưu **tên tham số**, không phải điều kiện chung để lưu generic signature. |
| “Metadata `List<String>` chứng minh mọi phần tử trong List runtime đều là String.” | Generic type ở khai báo không thay thế kiểm tra dữ liệu runtime. |
| “Có thể overload `handle(List<String>)` và `handle(List<Integer>)`.” | Hai chữ ký có erasure giống nhau nên clash khi cùng một phạm vi khai báo. |

**Bài tập 1.** Cho `void f(List<? extends CharSequence> x, int[] y)`. Dự đoán hai mảng từ `getParameterTypes()` và `getGenericParameterTypes()`, và giải thích vì sao `int[]` thường là `Class<?>` trong cả hai.

**Bài tập 2.** Cho `<T extends Serializable & Comparable<T>> void f(T x, T[] xs)`. Dự đoán erasure của hai parameter và cách lấy cả hai bounds. `Serializable` là bound đầu tiên trong khai báo này.

**Bài tập 3.** Dùng `ParameterizedType` để lấy `String` từ `List<String>`, rồi thay tham số bằng `List<? super Integer>` và xem lower bounds của `WildcardType`.

**Bài tập 4.** Tạo một non-static inner class có constructor nhận `List<String>`, chạy ví dụ mục 7 và lý giải sự khác biệt về số phần tử.

**Bài tập 5.** Viết logic tra cứu method `analyze` ở mục 3 bằng `getDeclaredMethod` mà không dùng bất kỳ generic `Type` nào, sau đó dùng `getGenericParameterTypes` để hiển thị danh sách kiểu khai báo đầy đủ.

**Ghi nhớ:** `getParameterTypes()` cho **`Class<?>[]` theo chữ ký đã erasure**, còn `getGenericParameterTypes()` cho **`Type[]` phản ánh generic signature khi có metadata**. Cần chọn API theo mục đích, phân tích các dạng `Type` đúng cách, và đặc biệt không mặc định hai mảng luôn tương ứng 1:1 trong constructor có tham số ngầm.

## 11. Tham khảo chính thức

- [Javadoc Java SE 17 — `Executable.getParameterTypes` và `getGenericParameterTypes`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Executable.html) — ngữ nghĩa, lưu ý tham số ngầm và các ngoại lệ khi đọc chữ ký.
- [Javadoc Java SE 17 — `ParameterizedType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/ParameterizedType.html) — `getActualTypeArguments`, `getRawType`.
- [Javadoc Java SE 17 — `TypeVariable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/TypeVariable.html) — `getName`, `getBounds` và declaration.
- [Javadoc Java SE 17 — `GenericArrayType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/GenericArrayType.html) — generic component type.
- [Javadoc Java SE 17 — `WildcardType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/WildcardType.html) — upper/lower bounds.
- [Javadoc Java SE 17 — `Class.getDeclaredMethod`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getDeclaredMethod(java.lang.String,java.lang.Class...)) — tra cứu method bằng danh sách `Class<?>`.
- [Java Language Specification `4.6 Erasure`](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.6) — quy tắc xóa generic types.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executable-parameters-004">Tên tham số và tùy chọn biên dịch -parameters</a>

<details>
<summary>Xem chi tiết</summary>

# Tên tham số và tùy chọn biên dịch -parameters trong Java Reflection

## 1. Vấn đề: vì sao Method.getParameters() thường cho arg0, arg1?

Trong mã nguồn Java, tên tham số giúp con người hiểu ý nghĩa của API:

```java
public void greet(String recipient, int repeatCount) { }
```

Ta có thể dùng Reflection lấy kiểu, số lượng và thứ tự tham số của phương thức này kể cả khi biên dịch mặc định. Nhưng **tên `recipient` và `repeatCount` không nhất thiết được lưu trong class file theo cách Reflection đọc được**.

Khi trình biên dịch không lưu tên vào thuộc tính `MethodParameters`, `java.lang.reflect.Parameter.getName()` trả tên do Reflection **tự sinh** theo dạng `argN` (ví dụ `arg0`, `arg1`), còn `Parameter.isNamePresent()` trả `false`. Khi biên dịch với **`javac -parameters`**, trình biên dịch chuẩn sẽ lưu tên tham số của các method và constructor phù hợp để Reflection lấy lại:

```bash
javac -parameters Example.java
```

Khi class file chứa metadata tên hợp lệ, `getName()` trả tên theo metadata và `isNamePresent()` trả `true`. Đây là **tùy chọn lúc biên dịch source**, không phải tham số JVM truyền vào lệnh `java` khi chạy ứng dụng.

Những điểm cần phân biệt:

- **Tên nguồn** như `recipient` là dữ liệu mô tả, không tham gia kiểm tra chữ ký method ở JVM.
- **Kiểu và thứ tự** như `(String, int)` vẫn tồn tại trong method descriptor và vẫn đọc được qua `getParameterTypes()` dù thiếu tên.
- `-parameters` không tự làm thay đổi chữ ký JVM, thứ tự, kiểu tham số hay hành vi của phương thức; nó bổ sung metadata cần cho công cụ runtime.
- `getName()` **luôn trả một chuỗi tên** nếu metadata hợp lệ, kể cả tên giả `argN`. Muốn biết tên lấy từ class file hay được tổng hợp, kiểm tra **`isNamePresent()`**, không đoán theo nội dung chuỗi.

## 2. Các API cần dùng

Cả `Method` và `Constructor<?>` đều kế thừa `Executable`. API chung `Executable.getParameters()` trả mảng `Parameter[]` mô tả các formal parameter của executable.

| API | Ý nghĩa |
| --- | --- |
| `method.getParameters()` / `constructor.getParameters()` | Mảng `Parameter[]` theo đúng thứ tự formal parameter trong metadata executable |
| `executable.getParameterCount()` | Số formal parameters ở mức JVM/Reflection |
| `executable.getParameterTypes()` | Kiểu khai báo sau type erasure dưới dạng `Class<?>[]` |
| `executable.getGenericParameterTypes()` | Kiểu generic khai báo khi có dữ liệu signature thích hợp |
| `parameter.getName()` | Tên từ class file hoặc tên tổng hợp dạng `argN` |
| `parameter.isNamePresent()` | Có tên được lưu trong `MethodParameters` hay không |
| `parameter.getType()` | Kiểu khai báo ở runtime, ví dụ `String.class` hoặc `int.class` |
| `parameter.getParameterizedType()` | Kiểu tham số có thông tin generic theo chữ ký khai báo |
| `parameter.getDeclaringExecutable()` | `Method` hay `Constructor` sở hữu tham số |
| `parameter.isImplicit()` | Tham số được khai báo ngầm theo quy tắc Java |
| `parameter.isSynthetic()` | Tham số do compiler sinh, không được khai báo tường minh hay ngầm trong source |
| `parameter.isVarArgs()` | Tham số này là formal varargs hay không |

Các API `Parameter` và `Executable.getParameters()` đã có từ **Java 8**. Bài minh họa sử dụng cú pháp Java 17+ và các API đã ổn định từ lâu; không cần JDK 20 mới đọc được parameter names.

**Mấu chốt:** `getParameterTypes()` và `getParameterCount()` vẫn làm việc khi `isNamePresent() == false`. Không được nhầm thiếu *tên* với thiếu *tham số* hoặc mất thông tin *kiểu*.

## 3. Chương trình Java hoàn chỉnh và kết quả trước/sau -parameters

Lưu toàn bộ mã vào tệp `ParameterNamesDemo.java`:

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

public class ParameterNamesDemo {
    public ParameterNamesDemo(String title, int level) {}

    public void greet(String recipient, int repeatCount) {}

    public static void main(String[] args) throws Exception {
        Method method = ParameterNamesDemo.class.getDeclaredMethod(
                "greet", String.class, int.class);
        Constructor<ParameterNamesDemo> ctor =
                ParameterNamesDemo.class.getConstructor(String.class, int.class);

        System.out.println("method:");
        for (Parameter p : method.getParameters()) {
            System.out.println(p.getName() + " | " + p.isNamePresent()
                    + " | " + p.getType().getSimpleName());
        }

        System.out.println("constructor:");
        for (Parameter p : ctor.getParameters()) {
            System.out.println(p.getName() + " | " + p.isNamePresent()
                    + " | " + p.getType().getSimpleName());
        }

        System.out.println("count = " + method.getParameterCount());
        System.out.println("parameter types = "
                + Arrays.toString(method.getParameterTypes()));
    }
}
```

**Thử nghiệm A: biên dịch không có `-parameters`.** Giả sử đang đứng tại thư mục chứa file nguồn:

```bash
javac --release 17 -g -d plain ParameterNamesDemo.java
java -cp plain ParameterNamesDemo
```

Stdout của chương trình (đã kiểm tra thực tế bằng JDK 24 khi biên dịch target Java 17):

```text
method:
arg0 | false | String
arg1 | false | int
constructor:
arg0 | false | String
arg1 | false | int
count = 2
parameter types = [class java.lang.String, int]
```

**Thử nghiệm B: biên dịch có `-parameters`.** Ghi class files sang **thư mục khác** để không bị file từ bước A làm nhiễu:

```bash
javac --release 17 -g -parameters -d named ParameterNamesDemo.java
java -cp named ParameterNamesDemo
```

Stdout chính xác:

```text
method:
recipient | true | String
repeatCount | true | int
constructor:
title | true | String
level | true | int
count = 2
parameter types = [class java.lang.String, int]
```

Thử nghiệm cho thấy:

1. **Cùng source**, cùng chữ ký và cùng kiểu tham số, nhưng metadata tên trong class file khác nhau khi thay đổi cờ compiler.
2. `-g` đã được bật trong **cả hai** lệnh biên dịch. Vì kết quả A vẫn là `arg0`/`arg1`, có thể thấy **`-g` không thay cho `-parameters`** để `Parameter.getName()` đọc tên thật.
3. Hai dòng `count` và `parameter types` **không đổi**. Java vẫn biết có hai tham số `String` và `int` ngay cả khi tên vắng mặt.
4. Constructor cũng thuộc nhóm executable; `getParameters()` trên constructor hưởng metadata tên tương tự method.
5. Các kết quả trong ví dụ được xác nhận trong môi trường thử nghiệm. Với chương trình khác, tên và mức độ có metadata phụ thuộc thực tế class file và compiler đã tạo nó.

**Chú ý classpath:** `java -cp plain ParameterNamesDemo` đọc chính `ParameterNamesDemo.class` trong `plain`, còn `java -cp named ...` đọc file đã biên dịch trong `named`. Nếu chạy từ classpath không đúng, bạn có thể tưởng cờ đã bật nhưng Reflection vẫn thấy bytecode cũ.

## 4. MethodParameters và LocalVariableTable: hai loại metadata khác nhau

Class file chứa **method descriptor** cho từng executable, đủ để JVM biết kiểu và thứ tự tham số. Tên tham số có thể được lưu thêm trong attribute **`MethodParameters`**. `javac -parameters` là cơ chế chuẩn yêu cầu javac lưu metadata cần thiết này.

Một thuộc tính khác là **`LocalVariableTable`** (LVT), dùng cho thông tin debug, thường có khi bật `-g` hoặc `-g:vars`. LVT mô tả local variables và tham số theo phạm vi bytecode/slot cho debugger hoặc công cụ đọc bytecode; **`Parameter.getName()` không dùng LVT làm nguồn tên dự phòng**. `Parameter.isNamePresent()` dựa trên sự có mặt của tên theo `MethodParameters`.

| Tùy chọn biên dịch | Metadata trọng tâm | Tác động đối với `Parameter.getName()` |
| --- | --- | --- |
| Không truyền `-parameters` | Có method descriptor, có thể có một số debug metadata | Tên có thể không có; API sẽ sinh `argN` |
| `-g` / `-g:vars` | Thông tin debug, gồm LVT theo tùy chọn và khả năng method có code | Không bảo đảm tên thật qua core Reflection |
| `-parameters` | Lưu formal parameter names vào `MethodParameters` | Cho phép `isNamePresent() == true` và `getName()` trả tên đã lưu |
| `-g -parameters` | Vừa debug metadata vừa MethodParameters | Có thể phục vụ cả debugger và Reflection |

`-g` là công cụ phục vụ debug; `-parameters` phục vụ nhu cầu **runtime reflection trên tên tham số**. LVT không nhất thiết xuất hiện với abstract/interface methods không có code; `MethodParameters` có thể mô tả parameters của cả method không có phần body.

### Tự kiểm tra bằng javap

Trong ví dụ trên:

```bash
javap -v -p plain/ParameterNamesDemo.class
javap -v -p named/ParameterNamesDemo.class
```

Tìm các section `MethodParameters` ở output bản `named` và xem các tên `recipient`, `repeatCount`, `title`, `level`. Bản `plain` biên dịch không có `-parameters` trong thử nghiệm này không có các section tương ứng cho method/constructor ấy. Vì cả hai bản dùng `-g`, bạn vẫn có thể thấy thông tin debug trong `Code` và `LocalVariableTable` dù Reflection trả tên `argN` ở bản `plain`.

`javap -v` là công cụ hỗ trợ kiểm tra **bytecode thực sự đã build**; khi hành vi Reflection trái mong đợi, hãy kiểm tra file ở output/classpath đang được chạy, không chỉ kiểm tra source file.

## 5. Chi tiết hành vi của Parameter.getName và isNamePresent

### arg0 không phải bằng chứng duy nhất về thiếu metadata

Khi thiếu tên, Java Reflection đặt tên tổng hợp `argN` với `N` là chỉ số của tham số trong descriptor của executable. Tuy nhiên, trong source code hoàn toàn có thể **cố ý khai báo một tham số tên `arg0`** và biên dịch bằng `-parameters`. Do đó:

```java
String effectiveName = parameter.getName();
boolean reliableFromClassFile = parameter.isNamePresent();
```

Đây là đoạn minh họa, biến `parameter` cần là `java.lang.reflect.Parameter` lấy từ `getParameters()`. **Dùng `isNamePresent()` để kiểm tra nguồn tên**, không dùng `getName().startsWith("arg")` hoặc regex `arg[0-9]+`.

Kể cả khi tên có trong class file, không nên mặc định đó là “tên gốc” của mọi ngôn ngữ và quy trình build: công cụ bytecode, compiler ngôn ngữ khác, obfuscator và code generator có thể ghi hoặc đổi metadata. API cho biết **tên hiện có trong class file**, không chứng nhận lịch sử biên dịch.

### Class file có thể không mang tên đủ tin cậy

Khi xây framework cần ánh xạ argument theo tên, hãy có cách xử lý nếu `isNamePresent()` trả `false`. Chẳng hạn:

```java
static String requireName(Parameter parameter) {
    if (!parameter.isNamePresent()) {
        throw new IllegalStateException(
                "Thiếu tên tham số: hãy biên dịch module bằng -parameters");
    }
    return parameter.getName();
}
```

Đây là một phương thức Java hợp lệ có thể đặt trong class đang xử lý Reflection; các thông số khác của phương thức chưa được nêu, như chính sách fallback, tùy sản phẩm. Không dùng tên giả `arg0` để âm thầm map JSON key hoặc query parameter thành tên “arg0” nếu hợp đồng ứng dụng yêu cầu tên source cụ thể.

### Trường hợp tham số sinh ra khi biên dịch

`Executable.getParameters()` mô tả các tham số tồn tại ở mức executable runtime. Một số construct Java, như inner-class constructor không static hoặc compiler-generated code, có thể xuất hiện tham số ẩn/implicit/synthetic trong bytecode, làm danh sách khác với cách đọc thoáng qua source.

Các API `Parameter.isImplicit()`, `isSynthetic()`, `getModifiers()` giúp giải thích metadata tương ứng, **nếu class file cung cấp các dấu hiệu cần thiết**. Đừng cố đồng nhất số tham số source với số tham số runtime trong mọi trường hợp.

Nếu cần tìm **tên một tham số của một method xác định**, nên tra cứu method với exact signature (`getDeclaredMethod("name", types...)`) rồi duyệt `getParameters()`. Đối với constructor, tra `getDeclaredConstructor(types...)` theo descriptor thực tế. Với class file bị biến đổi không hợp lệ, reflection có thể phát sinh lỗi về metadata tham số (ví dụ `MalformedParametersException`); không suy rằng compiler bình thường luôn tạo metadata lỗi.

## 6. Cách ứng dụng -parameters trong build thực tế

### Biên dịch trực tiếp bằng javac

Với JDK 17 trở lên và cấu trúc ví dụ đơn giản:

```bash
javac -parameters -d out src/com/example/App.java
java -cp out com.example.App
```

Đây là mẫu lệnh: đường dẫn `src/com/example/App.java` và fully-qualified name `com.example.App` phải khớp source/project thực tế. Trong hệ thống nhiều module, **mỗi module cần metadata tên** phải được biên dịch với tùy chọn phù hợp. Không phải chỉ bật `-parameters` ở ứng dụng chính là khiến các JAR phụ thuộc sẵn có tự có tên.

Nếu cần vừa target Java 17 vừa lưu tên:

```bash
javac --release 17 -parameters -d out ParameterNamesDemo.java
```

`--release 17` điều khiển API/language/class-file target, còn `-parameters` điều khiển lưu metadata tên. Hai cờ phục vụ mục đích khác nhau và có thể dùng cùng nhau.

### Maven

Trong `pom.xml`, dùng cấu hình của Maven Compiler Plugin:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-compiler-plugin</artifactId>
      <configuration>
        <release>17</release>
        <parameters>true</parameters>
      </configuration>
    </plugin>
  </plugins>
</build>
```

Đây là **đoạn cấu hình để lồng trong `<project>` của POM**, không phải toàn bộ `pom.xml` độc lập. Với build cần tái lập, nên khai báo phiên bản plugin thích hợp với môi trường trong project. Sau khi cập nhật, thực hiện compile lại toàn bộ module có liên quan và chạy kiểm thử kiểm tra `isNamePresent()`.

### Gradle (Groovy DSL)

Trong `build.gradle` của Java project, có thể thêm:

```groovy
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs.add("-parameters")
}
```

Đây là đoạn cấu hình trong Gradle Groovy DSL; với Kotlin DSL (`build.gradle.kts`), có cú pháp khác. Kết hợp toolchain hoặc `options.release` để đặt target Java riêng. Quan trọng là tác vụ `JavaCompile` thực sự chạy với `-parameters` và mọi module/JAR đang được kiểm tra đã được build mới.

Nếu một library được phát hành bởi bên thứ ba, cờ của **dự án tiêu thụ library** không làm thay đổi class file của dependency ấy. Cần library build với metadata, hoặc framework dùng cơ chế ánh xạ khác như annotation có tên tường minh.

## 7. Ứng dụng thực tế: framework ánh xạ theo tên tham số

Tên tham số phản chiếu đặc biệt có ích khi framework ánh xạ dữ liệu theo **tên**, ví dụ constructor injection, binding tham số HTTP, named SQL parameter mapping hoặc serializing/deserializing vào constructor. Nếu thiết kế dựa trên `getName()`:

1. Tra cứu `Method` hoặc `Constructor<?>` đã biết đúng.
2. Lấy `Parameter[]` và kiểm tra `isNamePresent()` cho các tham số cần dùng.
3. Nếu thiếu metadata, báo lỗi build/config rõ ràng hoặc dùng nguồn tên khác có hợp đồng tường minh (ví dụ annotation).
4. Dùng `getType()`/`getParameterizedType()` để kiểm tra và chuyển đổi **giá trị**; tên đơn thuần không bảo đảm type safety.
5. Duy trì đúng thứ tự `Object[]` khi gọi `Constructor.newInstance(...)` hoặc `Method.invoke(receiver, args)`. Những API gọi method/constructor dùng **thứ tự đối số**, không tự reorder theo tên.

Ví dụ helper đọc tên có yêu cầu metadata:

```java
import java.lang.reflect.Executable;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

public final class ParameterNames {
    private ParameterNames() {}

    public static List<String> requiredNames(Executable executable) {
        List<String> names = new ArrayList<>();
        for (Parameter p : executable.getParameters()) {
            if (!p.isNamePresent()) {
                throw new IllegalStateException(
                        "Missing MethodParameters name: " + executable);
            }
            names.add(p.getName());
        }
        return List.copyOf(names);
    }
}
```

Đây là class Java 17+ hợp lệ để tái sử dụng; nó không có `main`. Nếu thiếu tên của bất kỳ tham số nào, nó báo lỗi thay vì silently coi `argN` là một tên business hợp lệ. Một sản phẩm có thể thay chiến lược này bằng annotation hoặc schema tên tường minh, nhưng cần quyết định rõ.

### API không yêu cầu tên thì không cần -parameters

Không phải mọi code dùng Reflection đều phải bật `-parameters`. Ví dụ:

```java
Method method = type.getDeclaredMethod(
        "greet", String.class, int.class);
Object result = method.invoke(target, "Mai", 2);
```

Đây là mảnh code: khi đã có `type` và `target` phù hợp cùng quyền truy cập, việc tìm/call method dựa trên **tên method và kiểu/thứ tự tham số**, không cần tên từng tham số `recipient` và `repeatCount`. Nếu code chỉ làm invocation theo signature hoặc kiểm tra parameter types, thêm `-parameters` có thể không mang lợi ích chức năng.

## 8. Phân biệt tham số, annotation, generic và các metadata khác

`-parameters` tác động **tên** tham số; các thông tin khác có cơ chế class-file riêng:

- `Parameter.getType()` đọc kiểu sau erasure theo descriptor; không phụ thuộc `-parameters`.
- `Parameter.getParameterizedType()` đọc thông tin generic khi có `Signature` thích hợp, chẳng hạn `List<String>`. Bật `-parameters` **không chống được type erasure** và không khiến runtime biết generic type argument của mọi object.
- `Parameter.getAnnotations()` và `getAnnotation(...)` đọc runtime-visible parameter annotations theo cơ chế giữ annotation (`@Retention(RUNTIME)`). `-parameters` không biến mọi annotation `SOURCE`/`CLASS` thành runtime annotation.
- `Parameter.isVarArgs()` liên quan varargs của executable, không được suy ra từ tên tham số. Một formal `String... args` có runtime type `String[]`.
- `Parameter.getDeclaringExecutable()` chỉ đến owner `Method`/`Constructor`, không phải object argument mà một lần gọi nhận được.
- `Executable.getParameterCount()` báo số formal parameters ở mức runtime; debug flags không làm thay đổi method descriptor.
- `java.lang.reflect.AccessFlag` là API được thêm từ JDK 20 phục vụ flags khác; **đọc tên tham số và `-parameters` đã tồn tại từ Java 8**.

Tên tham số được đọc từ class file không bảo đảm là tên ổn định xuyên bản release của thư viện. Nếu người dùng bên ngoài phụ thuộc vào tên để binding thì việc đổi tên source rồi build lại với `-parameters` có thể làm thay đổi behavior framework, dù method descriptor và JVM binary signature vẫn y hệt.

## 9. Lỗi phổ biến và cách khắc phục

| Triệu chứng | Nguyên nhân/kiểm tra |
| --- | --- |
| `getName()` là `arg0`, `isNamePresent()` là `false` | Class file đang chạy không có tên trong `MethodParameters`; biên dịch lại module thực sự cần dùng bằng `-parameters` |
| Đã bật `-g` mà tên vẫn `arg0` | `LocalVariableTable` không phải nguồn của `Parameter.getName()`; bật `-parameters` |
| Đã bật `-parameters` nhưng vẫn false | Có thể chạy bytecode cũ, nhầm classpath/module, dependency chưa rebuild, hoặc bytecode đã bị công cụ khác biến đổi |
| Bật `-parameters` ở ứng dụng nhưng thư viện vẫn false | Thư viện JAR có class file riêng; cần metadata từ phía thư viện |
| Dùng `java -parameters ...` | Sai vị trí: đây là tùy chọn của **javac**, không phải runtime switch của launcher `java` |
| Truy vấn `getMethod("f", String.class)` sai | Tìm kiếm dùng formal parameter types; tên tham số không ảnh hưởng overload lookup |
| Reflection trả nhiều tham số hơn hình dung từ source | Có thể là implicit/synthetic parameters; kiểm tra signature, flags và construct Java tương ứng |
| Thấy `getName()` giống `arg0` nhưng `isNamePresent()` true | Có thể tên đã được biên dịch thật là `arg0`; luôn tin `isNamePresent()` thay vì pattern chuỗi |
| Tên có mặt nhưng binding vẫn sai | Kiểm tra tên annotation/schema, tên bị đổi sau obfuscation, type conversion và thứ tự gắn giá trị; `-parameters` chỉ lo metadata tên |

Cách chẩn đoán nhanh:

```bash
javap -v -p path/to/YourClass.class
```

Tìm `MethodParameters` của method/constructor thực tế đang gặp lỗi. Nếu có nhiều bản class trên classpath, trước hết xác minh ứng dụng thực sự nạp file/JAR nào. Với build tool, thực hiện clean/rebuild phù hợp để loại stale bytecode rồi chạy test dựa trên `isNamePresent()`.

## 10. Câu hỏi tự kiểm tra

**Câu 1.** Biên dịch `void f(String city)` bằng `javac -g`, không có `-parameters`, `Parameter.getName()` có bắt buộc là `city` không? **Không**. Trong trường hợp thông thường của javac, tên tổng hợp sẽ là `arg0` và `isNamePresent()` là `false`.

**Câu 2.** Bật `-parameters` có đổi `getParameterTypes()` hoặc thứ tự formal parameters không? **Không**. Chỉ bổ sung metadata tên; method descriptor không thay đổi bởi riêng cờ này.

**Câu 3.** Tại sao không kiểm tra `p.getName().startsWith("arg")` để biết thiếu metadata? Vì tên nguồn **có thể thật sự là `arg0`**; `p.isNamePresent()` mới là kiểm tra chuẩn.

**Câu 4.** Muốn tham số constructor xuất hiện tên gốc trong Reflection thì chỉ bật `-parameters` ở file có `main` là đủ? **Không**. Phải biên dịch **chính class khai báo constructor** với metadata, trừ các trường hợp đặc thù khác do compiler/class-file specification đảm bảo.

**Câu 5.** Đã có `Method m` có hai tham số. Khi biên dịch không có `-parameters`, `m.getParameterCount()` có thể vẫn là 2 không? **Có**. Kiểu/số lượng dựa vào descriptor, không vào tên.

**Câu 6.** `-parameters` có làm `@Retention(SOURCE)` annotation trên parameter đọc được bằng Reflection không? **Không**. Retention annotation có cơ chế riêng.

**Câu 7.** Framework gọi `Method.invoke` cần tên `recipient` để sắp xếp thứ tự args không? **Không, nếu nó đã biết thứ tự tham số từ schema/metadata**. Invocation nhận arguments theo vị trí; tên chỉ cần khi nguồn dữ liệu bên ngoài phải map theo tên.

**Câu 8.** Có thể xem metadata lưu tên bằng công cụ nào trong JDK? **`javap -v -p`** trên đúng class file đã build, tìm section `MethodParameters`.

## 11. Tài liệu chính thức

- [Oracle Java SE 17 — Parameter](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Parameter.html): `getName()`, `isNamePresent()`, `getType()`, `getParameterizedType()`.
- [Oracle Java SE 17 — Executable](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Executable.html): `getParameters()`, `getParameterCount()`, `getParameterTypes()`.
- [Oracle Java SE 17 — javac options](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html): mô tả `-parameters`, `-g`, `--release`.
- [Java Virtual Machine Specification 17 — `MethodParameters` attribute](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.24): class-file representation.
- [Java Virtual Machine Specification 17 — `LocalVariableTable` attribute](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.13): debug metadata.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executable-parameters-005">isNamePresent và tên tổng hợp arg0, arg1</a>

<details>
<summary>Xem chi tiết</summary>

# `Parameter.isNamePresent()` và tên tổng hợp `arg0`, `arg1` trong Java Reflection

## 1. Cùng một method, vì sao Reflection lúc thấy tên thật, lúc chỉ thấy `arg0`?

Một method có thể được khai báo như sau:

~~~java
void send(String recipient, int attempts) {
    // ...
}
~~~

Trong mã nguồn, tham số có tên `recipient` và `attempts`. Tuy nhiên, **tên tham số trong mã nguồn không mặc nhiên được ghi vào bytecode dưới dạng metadata Reflection có thể truy cập**. Khi dùng `Method.getParameters()` để lấy các đối tượng `java.lang.reflect.Parameter`, chúng ta có hai khả năng:

- Class file có metadata tên tham số phù hợp: `Parameter.getName()` trả `recipient`, `attempts` và `Parameter.isNamePresent()` trả `true`.
- Class file không có metadata tên tham số: Java Reflection tạo tên thay thế theo dạng **`arg0`, `arg1`, …**, và `Parameter.isNamePresent()` trả `false`.

Điểm then chốt: **`getName()` vẫn trả một chuỗi kể cả khi tên thật không có trong class file**. Vì vậy chỉ gọi `getName()` rồi thấy `arg0` không chứng minh tác giả đã đặt tham số tên `arg0`, và không phải một cơ chế phục hồi tên gốc từ bytecode.

Hai nhóm thông tin độc lập:

1. **Thông tin chữ ký của executable:** số lượng tham số, loại tham số, thứ tự; JVM cần chúng để kiểm tra chữ ký và thực hiện lời gọi.
2. **Thông tin tên nguồn của tham số:** tên như `recipient` hay `attempts` để hỗ trợ tooling, framework, binding; Java Reflection chỉ biết chúng khi metadata được phát hành và còn nguyên vẹn.

`isNamePresent()` là cách chính thức để phân biệt hai tình huống trên.

## 2. API trực tiếp phục vụ quyết định tên tham số

`Method` và `Constructor<?>` cùng kế thừa `Executable`. `getParameters()` trả các `Parameter` theo thứ tự của executable; `getParameterCount()` và `getParameterTypes()` vẫn hoạt động khi không có metadata tên.

| API | Câu hỏi trả lời |
| --- | --- |
| `Parameter.getName()` | Class file cung cấp tên nào, hoặc Reflection đã tạo tên thay thế `argN` nào? |
| `Parameter.isNamePresent()` | Riêng tham số này có tên trong metadata `MethodParameters` không? |
| `Parameter.getDeclaringExecutable()` | Method/constructor nào khai báo tham số này? |
| `Parameter.getType()` | Kiểu runtime sau type erasure là gì? |

Vì tên không tham gia chữ ký overload, hãy tra method bằng tên method và danh sách `Class<?>`, sau đó kiểm tra **từng** `Parameter.isNamePresent()` nếu cần tên để binding. `Parameter` mô tả khai báo, không lưu giá trị của một lần gọi thực tế.

## 3. Thực hành Java 17: cùng source, hai kết quả

Tạo đúng một file `ParameterNamesDemo.java`:

~~~java
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class ParameterNamesDemo {
    public static void main(String[] args) throws Exception {
        Class<Submission> type = Submission.class;

        Constructor<Submission> ctor =
                type.getDeclaredConstructor(String.class, long.class);
        Method method =
                type.getDeclaredMethod("send", String.class, int.class);

        inspect("constructor", ctor);
        inspect("method", method);
    }

    private static void inspect(String label, Executable executable) {
        System.out.println(label + ": " + executable.getParameterCount());

        for (Parameter p : executable.getParameters()) {
            System.out.println("  " + p.getName()
                    + " | present=" + p.isNamePresent()
                    + " | type=" + p.getType().getSimpleName());
        }
    }
}

class Submission {
    Submission(String label, long createdAt) {
    }

    void send(String recipient, int attempts) {
    }
}
~~~

### Lần 1 — Biên dịch mặc định, không giữ tên tham số

Trong thư mục chứa file, chạy:

~~~bash
javac --release 17 ParameterNamesDemo.java
java ParameterNamesDemo
~~~

Kết quả:

~~~text
constructor: 2
  arg0 | present=false | type=String
  arg1 | present=false | type=long
method: 2
  arg0 | present=false | type=String
  arg1 | present=false | type=int
~~~

Cả constructor và method đều có hai tham số với đúng **kiểu** và đúng **thứ tự**, nhưng tên nguồn không được lưu bằng metadata tên tham số được Reflection nhận biết. `getName()` do đó tạo ra `arg0`, `arg1` và `isNamePresent()` trả `false`.

### Lần 2 — Biên dịch lại cùng source với `-parameters`

Chạy tiếp (giữ nguyên file `.java`):

~~~bash
javac --release 17 -parameters ParameterNamesDemo.java
java ParameterNamesDemo
~~~

Kết quả:

~~~text
constructor: 2
  label | present=true | type=String
  createdAt | present=true | type=long
method: 2
  recipient | present=true | type=String
  attempts | present=true | type=int
~~~

Chỉ khác lựa chọn biên dịch, `getName()` giờ lấy được tên khai báo, `isNamePresent()` trả `true` cho từng tham số. Method và constructor vẫn có chữ ký Java, số lượng và kiểu tham số như trước.

**Thí nghiệm này đã được xác minh** với Java Compiler qua API `javax.tools.JavaCompiler` ở chế độ `--release 17`, so sánh hai lượt có/không có `-parameters`. Khác biệt không cần sửa logic method hay constructor.

### Phân tích kỹ hơn

- `Submission(String label, long createdAt)` là constructor package-private, nhưng `getDeclaredConstructor(String.class, long.class)` vẫn tìm thấy metadata.
- `send(String recipient, int attempts)` cũng không cần là method public: `getDeclaredMethod(String, Class<?>...)` lấy khai báo trong class tương ứng.
- Việc đọc tên/type qua `getParameters()` không đòi gọi constructor/method; đây là metadata. Nếu sau đó dùng `newInstance` hoặc `invoke` mới phát sinh các access check riêng.
- Ví dụ chỉ sử dụng **top-level class** thông thường và tham số được khai báo tường minh. Với constructor của enum, non-static inner class hay những hình thức compiler-generated khác, có thể có tham số implicit/synthetic cần phân tích riêng.

## 4. Chẩn đoán chính xác tên tổng hợp

`javac -parameters` yêu cầu ghi tên tham số trong thuộc tính `MethodParameters` của class file. `-g` có thể tạo `LocalVariableTable` cho debugger, nhưng Core Reflection không dùng bảng debug đó để quyết định `isNamePresent()`. Kiểm tra class file đang thực thi bằng `javap -v -p` và tìm `MethodParameters` tại đúng method/constructor.

**Không nhận diện tên tổng hợp bằng regex.** Source hoàn toàn có thể cố ý khai báo tham số tên `arg0`; khi biên dịch với `-parameters`, `getName()` vẫn là `arg0` nhưng `isNamePresent()` là `true`. Ngược lại, thiếu tên metadata thì `argN` chỉ là nhãn Reflection tự tạo.

## 5. Kiểm tra bản build thực sự được nạp

Chạy thí nghiệm ở mục 3 trong hai thư mục output tách biệt (ví dụ `plain/` và `named/`) để tránh đọc lại `.class` cũ. Với `named/`, dùng `javac --release 17 -parameters -d named ParameterNamesDemo.java`; với `plain/`, bỏ `-parameters`. Chọn classpath tương ứng khi chạy rồi so `getName()` và `isNamePresent()`.

Trong ứng dụng nhiều module, chỉ những class **đã được chính compiler/build pipeline phát hành metadata** mới có tên. Nếu thư viện JAR, CI, obfuscator hoặc bytecode transformer xử lý riêng, cần kiểm tra artifact cuối. Cấu hình Maven/Gradle chi tiết và vai trò `-g` đã được trình bày ở bài **Tên tham số và tùy chọn biên dịch `-parameters`**; ở đây trọng tâm là kiểm tra kết quả trên từng `Parameter`.

## 6. Tên tổng hợp `arg0`, `arg1` có dùng làm tên nghiệp vụ được không?

Không nên. Khi metadata tên không có, `getName()` trả tên theo thứ tự **`arg0`, `arg1`, …** cho các tham số; đó là cơ chế dự phòng để Reflection vẫn có chuỗi tên, **không phải tên khai báo đáng tin cậy từ source**.

Ví dụ một framework ánh xạ request JSON:

~~~java
void createUser(String email, int age)
~~~

Nếu framework định bind tham số theo tên `email` và `age` nhưng class file không có tên, nó có thể chỉ quan sát `arg0` và `arg1`. Nếu framework không có cơ chế định danh thay thế, mapping có thể thất bại hoặc gây nhầm lẫn. Framework có thể dùng annotation như `@RequestParam("email")`, `@JsonProperty("age")`, hoặc metadata riêng; hành vi thực tế phụ thuộc framework.

**Cách đúng:** nếu cần tên tham số nguồn, chỉ tin `getName()` khi `isNamePresent()` là true (và metadata đúng với artifact mong muốn). Nếu false, xử lý như tình huống **thiếu metadata**: yêu cầu cấu hình `-parameters`, dùng annotation đặt tên tường minh, hoặc sử dụng contract nhận diện tham số khác. Không tự ép `arg0` thành tên business.

Đây cũng là lý do một API/framework có thể chạy bình thường trong IDE nhưng lỗi ở bản đóng gói: cấu hình compiler hay bước tối ưu bytecode của hai môi trường có thể khác nhau.

## 7. Ngoại lệ và trường hợp ít hiển nhiên

### a) Constructor có tham số ngầm/nhân tạo

Một `non-static inner class` có thể mang tham số ẩn trỏ về instance outer class, dù người đọc source chỉ thấy constructor khai báo một vài tham số. Constructor của `enum` cũng có tham số do compiler/runtime bổ sung; các executable synthetic/bridge có thể xuất hiện trong bytecode.

Đừng mặc định tham số index 0 trong Reflection luôn tương ứng với **tham số đầu tiên người viết nhìn thấy trong mã nguồn**. Khi nghi ngờ, kiểm tra `getParameterTypes()`, `getParameters()`, `Parameter.isImplicit()`, `Parameter.isSynthetic()` và metadata của class file. Giá trị các flag phụ thuộc compiler/bytecode, nên không tự suy diễn dựa vào tên `arg0`.

### b) `isNamePresent()` là kiểm tra trên từng `Parameter`

Chữ ký API là `parameter.isNamePresent()`, không phải thuộc tính boolean duy nhất trên toàn class. Khi xử lý tổng quát, kiểm tra **mỗi tham số** của executable. Tránh mặc định rằng nếu một tham số có tên thì tất cả sẽ có tên, nhất là khi làm việc với class file từ compiler, code generator hoặc công cụ biến đổi bytecode khác nhau.

~~~java
for (Parameter p : executable.getParameters()) {
    if (p.isNamePresent()) {
        System.out.println("Known name: " + p.getName());
    } else {
        System.out.println("Unnamed parameter, type: " + p.getType());
    }
}
~~~

Đây là mảnh code dùng lại `executable` trong phương thức `inspect` của bài, không phải chương trình độc lập.

### c) Record, enum, lambda và code compiler tạo

Với `record`, canonical constructor và thành phần record có cơ chế metadata đặc thù; một số compiler/JDK có thể lưu tên cho các thành phần đặc biệt ngay cả khi cách build thông thường không dùng `-parameters`. Lambda, bridge method, enum constructor và synthetic executable cũng có thể không phản ánh tên nguồn theo cách bạn kỳ vọng.

Vì vậy kết luận chính xác là: **đối với method/constructor thông thường như bài thực hành, bỏ `-parameters` dẫn tới `argN` và `isNamePresent=false`; với executable đặc biệt, phải kiểm tra metadata thực tế của class file**. Không tuyên bố mọi đối tượng `Parameter` ở mọi class file sẽ đều có `isNamePresent=false` nếu không truyền cờ.

### d) Annotation và kiểu tham số không biến mất khi tên biến mất

Dù `isNamePresent=false`, Java Reflection vẫn có thể biết `getType()`, số lượng tham số và annotation runtime (nếu annotation có retention thích hợp). Ví dụ annotation gắn tên nghiệp vụ tường minh là một đường metadata độc lập với `-parameters`. `getParameterizedType()` còn có thể cung cấp thông tin generic nếu metadata chữ ký được giữ.

Đừng nhầm “không thấy **tên**” với “không còn **tham số**”. Một method có hai tham số tiếp tục có hai `Parameter` được xây dựng từ descriptor/chữ ký phù hợp.

## 8. Lỗi thường gặp khi dùng `isNamePresent()`

- **Dùng `getName() != null` để xác nhận tên source:** tên tổng hợp cũng không null; phải kiểm tra `isNamePresent()`.
- **Dùng `startsWith("arg")` để phát hiện tên giả:** source có thể khai báo chính tên `arg0`; kiểm tra boolean từ API.
- **Kiểm tra một tham số rồi tin cả method:** xét từng phần tử `Parameter[]` riêng.
- **Cho rằng thiếu tên là thiếu kiểu/annotation:** descriptor, generic signature và runtime annotations là các nguồn metadata riêng.
- **Dùng tên reflection làm public key ngay khi `isNamePresent=true`:** tên tồn tại trong artifact nhưng có thể đổi bởi refactor hoặc quá trình build; contract bên ngoài cần quy tắc riêng.

## 9. Câu hỏi tự kiểm tra

**Câu 1:** `getName()` trả `arg0`. Có chắc tên trong source là `arg0` không? **Không.** Nếu `isNamePresent=false` thì đây là tên thay thế do Reflection cung cấp.

**Câu 2:** Một method có hai tham số nhưng cả hai `isNamePresent=false`. JVM có mất kiểu tham số không? **Không.** `getParameterTypes` và descriptor vẫn mô tả kiểu, số lượng và thứ tự.

**Câu 3:** Cờ nào lưu tên tham số cho Java Reflection: `-g` hay `-parameters`? **`-parameters`.** Thông tin debug của `-g` không thay thế được metadata này.

**Câu 4:** Có thể dùng tên tham số để tìm một overloaded method qua `getDeclaredMethod` không? **Không.** Tra cứu dùng **tên method** và danh sách **kiểu tham số**, không dùng tên từng parameter.

**Câu 5:** Tại sao một executable compiler-generated có thể cho metadata khác ví dụ trên? **Vì compiler có thể thêm tham số implicit/synthetic hoặc phát hành metadata đặc biệt.** Cần xem `Parameter.isImplicit`, `isSynthetic` và class file thực tế.

**Câu 6:** Nếu framework bắt buộc biết tên source nhưng `isNamePresent=false`, nên làm gì? **Bật `-parameters` cho build cần thiết, kiểm tra artifact cuối, hoặc dùng annotation/contract nêu tên tường minh.**

**Ghi nhớ:** `Parameter.getName()` **luôn cung cấp tên để hiển thị**, nhưng `Parameter.isNamePresent()` mới giúp phân biệt **tên có metadata thực** với **tên tổng hợp `arg0`, `arg1`**. Để framework dựa vào tên tham số hoạt động ổn định, phải quản lý compiler option và chất lượng class file cuối cùng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executable-parameters-006">isImplicit, isSynthetic và isVarArgs</a>

<details>
<summary>Xem chi tiết</summary>

# isImplicit, isSynthetic và isVarArgs trong Java Reflection

## 1. Ba câu hỏi khác nhau về tham số của executable

Khi dùng Reflection để phân tích tham số của **method** hoặc **constructor** (gọi chung là *executable*), Java cung cấp ba phương thức trên `java.lang.reflect.Parameter`:

- `isImplicit()`: Tham số có được **khai báo ngầm định theo quy tắc ngôn ngữ Java** không?
- `isSynthetic()`: Tham số có phải là **thành phần do compiler tạo ra**, không được khai báo tường minh cũng không được ngôn ngữ xem là khai báo ngầm định không?
- `isVarArgs()`: Tham số có biểu diễn **danh sách đối số biến độ dài** (cú pháp `...` trong source) không?

Đó là **ba thuộc tính có ý nghĩa riêng**; không thể dùng một cờ để thay thế cờ kia. Tham số ngầm định có thể không phải synthetic; tham số varargs thông thường là tham số lập trình viên khai báo tường minh, không phải “tham số ẩn”.

Ví dụ về những điều Reflection cần phân biệt:

```java
class Outer {
    class Inner {
        Inner(String text) {}
    }

    static void log(String tag, Object... values) {}
}
```

Trong source, `Inner` có một tham số `text`. Nhưng constructor ở classfile còn cần **enclosing instance** kiểu `Outer` để tạo non-static member class. Slot `Outer` này có thể được Reflection nhận diện bằng `isImplicit()`. Ngược lại, `values` được lập trình viên viết rõ trong source bằng `Object...`: nó có `isVarArgs() == true`, không phải một tham số compiler “tự phát sinh”.

Còn với `enum Color { RED; }`, compiler tạo các tham số nội bộ biểu diễn tên hằng và ordinal của enum constructor. Khi classfile giữ thông tin thích hợp, Reflection có thể báo hai slot đó là synthetic.

**Phạm vi:** các cờ này thuộc `Parameter`. `Method` và `Constructor` là những kiểu `Executable`, có các API cấp executable riêng như `isVarArgs()` hoặc `isSynthetic()`. Hãy luôn xác định ta đang xét **một tham số** hay **toàn bộ executable**.

## 2. API cần biết và ý nghĩa chính xác

Để lấy metadata:

```java
for (java.lang.reflect.Constructor<?> ctor
        : SomeType.class.getDeclaredConstructors()) {
    for (java.lang.reflect.Parameter parameter : ctor.getParameters()) {
        System.out.println(parameter.getName());
        System.out.println(parameter.isImplicit());
        System.out.println(parameter.isSynthetic());
        System.out.println(parameter.isVarArgs());
    }
}
```

Đây là **đoạn code minh họa**: cần có kiểu `SomeType` trong chương trình đang xét.

| API | Trả về | Cần hiểu |
| --- | --- | --- |
| `Executable.getParameters()` | `Parameter[]` | Danh sách tham số thực tế theo thứ tự của executable |
| `Executable.getParameterCount()` | `int` | Số tham số của executable trong metadata |
| `Parameter.isImplicit()` | `boolean` | Tham số được khai báo ngầm định trong source theo JLS |
| `Parameter.isSynthetic()` | `boolean` | Tham số không được khai báo tường minh hoặc ngầm định trong source |
| `Parameter.isVarArgs()` | `boolean` | Vị trí tham số đại diện cho phần biến độ dài |
| `Executable.isVarArgs()` | `boolean` | Method/constructor được khai báo nhận số đối số biến đổi |
| `Parameter.getType()` | `Class<?>` | Kiểu runtime của tham số, ví dụ `String[].class` |
| `Parameter.getParameterizedType()` | `Type` | Kiểu có thông tin generic ở mức khai báo khi có |
| `Parameter.getName()` | `String` | Tên từ metadata hoặc tên được tổng hợp như `arg0` |
| `Parameter.isNamePresent()` | `boolean` | Tên thực có mặt trong metadata classfile hay không |
| `Parameter.getDeclaringExecutable()` | `Executable` | Method/constructor chứa tham số đó |

`getParameters()` có thể gặp `MalformedParametersException` nếu thuộc tính metadata `MethodParameters` của classfile bị lỗi. Trong code hạ tầng đọc bytecode từ nguồn không tin cậy, cần xử lý trường hợp metadata không hợp lệ.

### Cùng cờ nhưng không cùng tầng

- `parameter.isSynthetic()` hỏi **một tham số** có synthetic không.
- `method.isSynthetic()` hoặc `constructor.isSynthetic()` hỏi **bản thân executable** có synthetic không.
- `parameter.isVarArgs()` xác định **vị trí tham số biến độ dài**; `executable.isVarArgs()` xác định **toàn executable** dùng variable arity.

Một constructor do lập trình viên khai báo có thể **không synthetic**, nhưng chứa một số tham số synthetic do compiler chèn vào. Đừng lấy `constructor.isSynthetic() == false` để suy luận tất cả tham số đều explicit.

## 3. `isImplicit()`: tham số ngầm định theo Java Language Specification

Một ví dụ quen thuộc là **enclosing instance** của non-static member inner class:

```java
public class Outer {
    class Inner {
        Inner(String value) {}
    }
}
```

Trong Java source, tạo object bằng `outer.new Inner("hi")`. Biểu thức `outer.new` cung cấp enclosing instance, nhưng constructor trong classfile được biểu diễn tương đương nhận `(Outer, String)`. Với class do `javac` biên dịch và metadata cần thiết, Reflection có thể thấy:

| Vị trí | Kiểu | `isImplicit()` | `isSynthetic()` | Vì sao |
| --- | --- | --- | --- | --- |
| 0 | `Outer` | `true` | `false` | Enclosing instance được ngôn ngữ hiểu ngầm |
| 1 | `String` | `false` | `false` | `value` được khai báo tường minh |

Từ “implicit” ở đây không có nghĩa là “có thể bỏ qua khi gọi Reflection”. Nếu chọn constructor bằng `Inner.class.getDeclaredConstructor(Outer.class, String.class)`, bạn vẫn phải truyền **đủ hai đối số** khi gọi `newInstance`: enclosing `Outer` trước, `String` sau.

Một **static nested class** không có enclosing instance như vậy, nên constructor không tự có thêm tham số kiểu `Outer` chỉ vì nó nằm trong class ngoài.

**Lưu ý giới hạn:** đây là trường hợp non-static *member* class theo mã nguồn Java. Local/anonymous class, inner class trong ngữ cảnh đặc biệt hoặc bytecode do compiler khác tạo có thể có cách biểu diễn khác; đừng dùng quy tắc “tham số 0 luôn là Outer” cho mọi class lồng nhau.

## 4. `isSynthetic()`: tham số phục vụ mã do compiler tạo

### Enum constructor

Ví dụ:

```java
enum Level {
    HIGH(10);
    Level(int priority) {}
}
```

Mã nguồn khai báo một tham số `priority`. Nhưng bytecode của enum thường cần **hai tham số đầu** là tên hằng enum `String` và ordinal `int`, rồi mới đến `priority`. Trên bản `javac` kiểm chứng trong ví dụ sau, Reflection thu được:

| Tham số | Kiểu | `isImplicit()` | `isSynthetic()` |
| --- | --- | --- | --- |
| `$enum$name` | `String` | `false` | `true` |
| `$enum$ordinal` | `int` | `false` | `true` |
| `priority` | `int` | `false` | `false` |

Không nên phụ thuộc vào tên `$enum$name` hay `$enum$ordinal` trong logic ứng dụng: đó là chi tiết biểu diễn của compiler đang dùng, **không phải tên public API ổn định**.

Tra cứu bằng `Level.class.getDeclaredConstructor(int.class)` sẽ không tìm đúng chữ ký bytecode trong ví dụ này vì thiếu các slot nội bộ. Việc tra cứu thành công constructor enum cũng **không** cho phép tạo hằng enum mới: `Constructor.newInstance(...)` từ chối instantiate enum bằng Reflection.

### Captured variables của local/anonymous class

Compiler có thể cần lưu giá trị của biến cục bộ được local class capture và bổ sung tham số constructor để truyền giá trị ấy:

```java
static void example() {
    int offset = 5; // Effectively final.

    class Local {
        Local(String label) {
            System.out.println(label + offset);
        }
    }

    // Compiler có thể thêm tham số để mang offset vào Local.
}
```

Đây là **mảnh code minh họa**, đặt trong một class Java. Trên một số bản `javac`, slot capture có cờ `synthetic`. **Thứ tự, tên và cách biểu diễn** có thể phụ thuộc compiler, phiên bản và ngữ cảnh capture. Không được lọc hoặc gán giá trị theo tên `val$offset` một cách mặc định.

### Synthetic parameter khác synthetic method

Compiler cũng có thể sinh *bridge method* để duy trì tính đúng của override khi erasure generic. Nhưng synthetic/bridge **method** là một khái niệm khác: đừng suy luận từ `Method.isBridge()` hoặc `Method.isSynthetic()` rằng các `Parameter` bên trong sẽ đều trả `isSynthetic() == true`. Phải đọc metadata đúng ở từng tầng.

## 5. `isVarArgs()`: tham số biểu diễn dấu ba chấm `...`

Varargs là cú pháp lập trình viên có thể chủ động khai báo:

```java
static void log(String tag, String... messages) {}
```

Ở cấp kiểu runtime, `String...` được biểu diễn dưới dạng **`String[]`**. Tuy vậy, Reflection vẫn biết executable được khai báo variable arity:

- `Executable.isVarArgs() == true` đối với method `log`.
- `Parameter.isVarArgs() == true` đối với **tham số cuối `messages`**.
- `Parameter.isVarArgs() == false` đối với `tag`.
- `messages.getType() == String[].class`.
- Một method khai báo **`String[] messages`** thông thường có thể có cùng kiểu bytecode của tham số, nhưng `isVarArgs() == false` vì không được đánh dấu là variable arity.

**Không được kết luận varargs chỉ từ `getType().isArray()`.** Kiểu mảng và varargs không đồng nghĩa. Cũng đừng kết luận tham số varargs là synthetic: trong ví dụ thông thường, `messages` được khai báo tường minh nên `isImplicit() == false` và `isSynthetic() == false`.

Theo cú pháp Java, phần varargs của method/constructor là **tham số cuối cùng** trong khai báo; nhiều tham số đứng trước vẫn được phép. Do đó, chỉ vị trí cuối đại diện phần biến độ dài.

## 6. Ví dụ Java 17+ chạy độc lập: so sánh cả ba cờ

Tạo file **`ParameterFlagsDemo.java`**:

```java
import java.lang.reflect.*;

public class ParameterFlagsDemo {
    class Member {
        Member(String value) {}
    }

    static class Helpers {
        Helpers(String label, int... numbers) {}
        void audit(String tag, String... entries) {}
        void arrayOnly(String[] entries) {}
    }

    enum Level {
        HIGH(10);
        Level(int priority) {}
    }

    static void describe(String label, Executable executable) {
        System.out.println(label + " | executableVarArgs=" + executable.isVarArgs());
        for (Parameter p : executable.getParameters()) {
            System.out.printf(
                    "  %s:%s implicit=%s synthetic=%s varArgs=%s namePresent=%s%n",
                    p.getName(), p.getType().getSimpleName(),
                    p.isImplicit(), p.isSynthetic(), p.isVarArgs(), p.isNamePresent()
            );
        }
    }

    public static void main(String[] args) throws Exception {
        describe("Member", Member.class.getDeclaredConstructor(
                ParameterFlagsDemo.class, String.class));
        describe("Level", Level.class.getDeclaredConstructors()[0]);
        describe("Helpers", Helpers.class.getDeclaredConstructor(
                String.class, int[].class));
        describe("audit", Helpers.class.getDeclaredMethod(
                "audit", String.class, String[].class));
        describe("arrayOnly", Helpers.class.getDeclaredMethod(
                "arrayOnly", String[].class));
    }
}
```

Dùng JDK 17+ tại thư mục chứa file:

```bash
javac --release 17 -parameters ParameterFlagsDemo.java
java ParameterFlagsDemo
```

**Stdout đã được đối chiếu bằng JDK 21 / `javac --release 17 -parameters`:**

```text
Member | executableVarArgs=false
  this$0:ParameterFlagsDemo implicit=true synthetic=false varArgs=false namePresent=true
  value:String implicit=false synthetic=false varArgs=false namePresent=true
Level | executableVarArgs=false
  $enum$name:String implicit=false synthetic=true varArgs=false namePresent=true
  $enum$ordinal:int implicit=false synthetic=true varArgs=false namePresent=true
  priority:int implicit=false synthetic=false varArgs=false namePresent=true
Helpers | executableVarArgs=true
  label:String implicit=false synthetic=false varArgs=false namePresent=true
  numbers:int[] implicit=false synthetic=false varArgs=true namePresent=true
audit | executableVarArgs=true
  tag:String implicit=false synthetic=false varArgs=false namePresent=true
  entries:String[] implicit=false synthetic=false varArgs=true namePresent=true
arrayOnly | executableVarArgs=false
  entries:String[] implicit=false synthetic=false varArgs=false namePresent=true
```

Giải thích kết quả:

1. `Member`: constructor có hai slot runtime. `this$0` là enclosing instance kiểu `ParameterFlagsDemo`, `implicit=true`; `value` là explicit parameter.
2. `Level`: hai slot đầu của enum constructor có `synthetic=true`. Slot `priority` do người viết khai báo có các cờ đều false.
3. `Helpers`: constructor nhận `String label` và `int... numbers`. Trong metadata, tham số cuối có kiểu `int[]` và `varArgs=true`. Bản thân constructor có `executableVarArgs=true`.
4. `audit`: method nhận `String tag` trước khi đến `String... entries`. Chỉ `entries` có `isVarArgs() == true`.
5. `arrayOnly`: method nhận `String[]` dưới dạng **mảng thông thường**. Mặc dù `getType()` vẫn biểu diễn là `String[]`, cả method và parameter đều có `isVarArgs() == false`.

Đây là kết quả có điều kiện rõ ràng: các **tên compiler-generated** và metadata cho implicit/synthetic parameter được quan sát từ `javac` trong cấu hình trên. Không khẳng định mọi compiler/JDK hoặc bytecode generator đều xuất cùng tên nội bộ.

## 7. Biên dịch thiếu `-parameters` sẽ thay đổi gì?

Nếu chạy:

```bash
javac --release 17 ParameterFlagsDemo.java
java ParameterFlagsDemo
```

với bản `javac` đã kiểm chứng, các giá trị `getName()` chuyển thành `arg0`, `arg1`, ... và `isNamePresent()` trả `false`. Nhưng các **kiểu/số lượng tham số** cùng cờ varargs, implicit, synthetic trong ví dụ vẫn phản ánh cấu trúc executable đã biên dịch.

Ví dụ một phần kết quả khi bỏ `-parameters`:

```text
Member | executableVarArgs=false
  arg0:ParameterFlagsDemo implicit=true synthetic=false varArgs=false namePresent=false
  arg1:String implicit=false synthetic=false varArgs=false namePresent=false
Level | executableVarArgs=false
  arg0:String implicit=false synthetic=true varArgs=false namePresent=false
  arg1:int implicit=false synthetic=true varArgs=false namePresent=false
  arg2:int implicit=false synthetic=false varArgs=false namePresent=false
```

**Rút ra:**

- `-parameters` yêu cầu `javac` lưu metadata tên của parameters; **không tạo hoặc xóa** enclosing-instance slot, enum slot hay varargs slot.
- `isNamePresent() == true` không có nghĩa tham số được khai báo tường minh: tham số implicit hoặc synthetic cũng có thể được compiler lưu tên.
- `isNamePresent() == false` không có nghĩa tham số synthetic: tham số explicit bình thường cũng có thể thiếu tên metadata.
- Việc các cờ implicit/synthetic có được ghi nhận nhất quán phụ thuộc các thuộc tính classfile mà toolchain tạo ra. Nếu classfile đến từ bytecode generator hoặc compiler khác, cần kiểm tra kết quả Reflection trên artifact thực tế.
- Thông tin debug như `LocalVariableTable` khác với metadata tên mà `Parameter.getName()` sử dụng. Đừng dựa vào `-g` thay cho `-parameters`.

Trong code mapping theo tên (ví dụ injection hoặc routing), cần kiểm tra `isNamePresent()` và có chiến lược thay thế khi metadata không được lưu, chẳng hạn annotation đặt tên tường minh hoặc cấu hình khác.

## 8. Cách sử dụng thông tin cờ khi viết framework

Giả sử viết công cụ hiển thị signature của method/constructor cho người dùng, các bước an toàn là:

1. Lấy `Executable` cụ thể (method hoặc constructor) và đọc `getParameters()`; không tự giả định số tham số chỉ theo source.
2. Với từng `Parameter`, giữ **index** của vị trí thực tế, kiểu `getType()` và kiểu generic `getParameterizedType()` nếu cần.
3. Dùng `isImplicit()` để nhận diện các tham số được khai báo ngầm định theo ngôn ngữ, chẳng hạn enclosing-instance slot của member inner class.
4. Dùng `isSynthetic()` để nhận diện những tham số compiler bổ sung không thuộc khai báo tường minh hoặc ngầm định theo ngôn ngữ.
5. Dùng `isVarArgs()` để đánh dấu tham số cuối kiểu variable arity trong UI. Không dùng `type.isArray()` thay thế.
6. Khi hiển thị “tham số do lập trình viên viết”, có thể tách riêng các tham số implicit/synthetic **nếu metadata đáng tin cậy**, nhưng vẫn phải giữ thông tin đầy đủ để gọi constructor hay method bằng Reflection.
7. Khi truyền đối số vào `Constructor.newInstance(...)`/`Method.invoke(...)`, tuân thủ **chữ ký bytecode thực tế** và đúng thứ tự. Việc ẩn slot nội bộ trên giao diện người dùng không làm slot đó biến mất khỏi lời gọi Reflection.
8. Tránh dùng tên `arg0`/`this$0`/`$enum$name` làm khóa logic; dùng tên thật có kiểm chứng, annotation hoặc hợp đồng explicit.
9. Tôn trọng quyền truy cập constructor/method và module Java. Đọc metadata không có nghĩa là có quyền gọi một executable private/package-private.

### Lưu ý thực hành với varargs khi `Method.invoke`

Giả sử trong class có method:

```java
void send(String... messages) {}
```

Khi gọi bằng Reflection, vị trí `messages` là một **tham số mảng `String[]`** trong chữ ký runtime. Phải cung cấp giá trị đúng cho vị trí này, ví dụ:

```java
Method m = SomeClass.class.getDeclaredMethod("send", String[].class);
m.invoke(receiver, (Object) new String[] {"one", "two"});
```

Đây là **mảnh code minh họa**: `SomeClass` có method `send` tương ứng, `receiver` đúng kiểu và caller có quyền truy cập. Ép `(Object)` nhằm giúp `Method.invoke(Object, Object...)` nhận cả mảng `String[]` như **một đối số của method đích**, thay vì gây nhập nhằng với cơ chế varargs của chính lời gọi `invoke`.

Không suy luận rằng vì `send` là varargs thì `m.invoke(receiver, "one", "two")` sẽ tự đóng gói các chuỗi thành `String[]`. Reflection yêu cầu danh sách đối số khớp kiểu/chữ ký runtime; không tự áp dụng đầy đủ cách đóng gói varargs của cú pháp gọi Java source thông thường.

## 9. Phân biệt lỗi thường gặp

| Nhầm lẫn | Điều đúng cần nhớ |
| --- | --- |
| “`isImplicit` và `isSynthetic` tương đương.” | Implicit là khai báo ngầm định theo JLS; synthetic không phải khai báo explicit/implicit |
| “Varargs luôn là synthetic parameter.” | Varargs thông thường được lập trình viên khai báo tường minh |
| “Cứ thấy `String[]` là varargs.” | `String[]` thường và `String...` có kiểu runtime giống nhau nhưng cờ `isVarArgs` khác |
| “`isNamePresent=true` nghĩa là explicit parameter.” | Tên của implicit/synthetic parameter cũng có thể được lưu |
| “`-parameters` thay đổi số tham số runtime.” | Nó yêu cầu lưu metadata tên, không thay đổi signature thực tế |
| “`Method.isSynthetic` cho biết tất cả tham số synthetic.” | Cần xét `Parameter.isSynthetic` trên từng vị trí |
| “`Executable.isVarArgs` chỉ rõ mọi tham số varargs.” | Cờ cấp executable chỉ nói cả lời khai báo dùng variable arity; cần `Parameter.isVarArgs` để biết slot |
| “Có thể bỏ qua implicit/synthetic khi `newInstance`.” | Constructor Reflection vẫn cần đối số theo chữ ký runtime, nếu kiểu đó được phép instantiate |
| “Nhìn tên `arg0` suy ra không có hidden parameter.” | Tên `argN` chỉ phản ánh thiếu metadata tên, không phản ánh nguồn gốc tham số |
| “Các cờ luôn được compiler khác nhau sinh giống `javac`.” | Metadata có thể khác; đọc đúng classfile và xác minh môi trường xây dựng |

## 10. Tự kiểm tra

1. Với `class Outer { class Inner { Inner(int n) {} } }`, vì sao Reflection có thể báo constructor gồm `Outer, int`?
2. Tham số enclosing instance đầu tiên của member inner class trong ví dụ `javac` có cờ `isImplicit` và `isSynthetic` lần lượt là gì?
3. Enum constructor khai báo `E(int score)` có thể chứa hai synthetic parameter đầu để làm gì?
4. `void f(String[] x)` và `void g(String... x)` có cùng `getType()` của tham số không? Cờ `isVarArgs()` có giống nhau không?
5. Một `Parameter` có `isNamePresent() == false` có chắc là synthetic không?
6. `method.isSynthetic() == false` có đủ để khẳng định không có synthetic parameter không?
7. Nếu cần truyền varargs qua `Method.invoke`, tại sao cần quan tâm giá trị mảng cuối và cơ chế `Object...` của chính `invoke`?
8. Nếu phát hiện implicit/synthetic parameters trên constructor thì có thể tùy ý loại chúng khi gọi `newInstance` không?

**Đáp án định hướng:** (1) cần enclosing instance; (2) `true` và `false` trong ví dụ; (3) tên hằng và ordinal; (4) cùng kiểu mảng, cờ varargs khác; (5) không; (6) không; (7) phải truyền đúng một đối số mảng ở slot runtime; (8) không.

## 11. Kết luận và tài liệu chính thức

Ba API `Parameter.isImplicit()`, `Parameter.isSynthetic()` và `Parameter.isVarArgs()` trả lời **ba câu hỏi độc lập**: nguồn gốc ngầm định theo ngôn ngữ, nguồn gốc compiler-generated, và vai trò tham số biến độ dài. Khi viết reflection tooling, luôn kết hợp chúng với `Executable.getParameters()`, `getParameterTypes()`, `isVarArgs()` của executable, `Parameter.getType()` và `isNamePresent()`. Quan sát **signature và metadata thực tế của classfile** đáng tin cậy hơn suy đoán từ tên, cờ riêng lẻ hay hình dạng mảng.

Tài liệu:

- [Java SE 21 — `java.lang.reflect.Parameter`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Parameter.html): hợp đồng của `isImplicit`, `isSynthetic`, `isVarArgs`, `isNamePresent`.
- [Java SE 21 — `java.lang.reflect.Executable`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Executable.html): `getParameters` và `isVarArgs` cấp method/constructor.
- [Java SE 21 — `java.lang.reflect.Method`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Method.html): cách gọi method bằng Reflection và cờ executable.
- [JLS 17 `8.4.1 — Formal Parameters](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.1): cú pháp và điều kiện variable arity parameter.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executable-parameters-007">Modifier của tham số và giới hạn metadata có sẵn</a>

<details>
<summary>Xem chi tiết</summary>

# Modifier của tham số và giới hạn metadata có sẵn trong Java Reflection

## 1. Vấn đề cần giải quyết

Java Reflection không chỉ cho phép tìm lớp, phương thức và constructor. Một `Method` hoặc `Constructor` còn có thể được khảo sát ở mức **tham số hình thức** (formal parameter) qua `java.lang.reflect.Parameter`. Điều này hữu ích khi viết framework ánh xạ request, dependency injection, serializer, trình tạo tài liệu API hoặc công cụ kiểm tra chữ ký.

Tuy nhiên, không phải mọi thông tin từng xuất hiện trong tệp nguồn `.java` đều được giữ lại trong tệp `.class`. Đặc biệt, cần tách ba loại dữ liệu:

1. **Cấu trúc chữ ký**: số lượng, thứ tự, kiểu runtime của tham số. JVM lưu chúng trong *method descriptor*; thông tin này không phụ thuộc tùy chọn `javac -parameters`.
2. **Modifier/cờ và metadata của tham số**: `final`, `synthetic`, `mandated/implicit` và tên tham số có thể được phản ánh từ attribute `MethodParameters`, nếu class file cung cấp thông tin tương ứng.
3. **Cú pháp nguồn**: cách viết từ khóa, tên biến cục bộ, chú thích và formatting ban đầu không thể được tái tạo đầy đủ chỉ từ `Parameter`.

Ví dụ nguồn:

```java
public void send(final String topic, int retries) {
}
```

Reflection luôn có thể tìm chữ ký tham số theo kiểu `String` và `int` trong một class file hợp lệ. Nhưng nó **không bảo đảm** biết tên `topic`, `retries` hoặc biết `topic` được khai báo `final` nếu metadata cần thiết đã bị loại bỏ.

**Mục tiêu bài học:** đọc đúng `Parameter.getModifiers()` và các API liên quan; hiểu `MethodParameters`, `-parameters`, những tham số implicit/synthetic và giới hạn khi metadata không tồn tại.

## 2. Cách lấy `Parameter` từ một executable

`java.lang.reflect.Executable` là superclass chung của `Method` và `Constructor<?>`. Cả hai đều có:

- `getParameterCount()`: số lượng tham số trong chữ ký phản ánh.
- `getParameterTypes()`: mảng `Class<?>[]` biểu diễn kiểu runtime đã erasure.
- `getGenericParameterTypes()`: các `Type` biểu diễn kiểu generic nếu còn metadata phù hợp.
- `getParameters()`: mảng `Parameter[]` theo thứ tự tham số.
- `getParameterAnnotations()`: annotation trên các parameter theo khả năng hiển thị ở runtime.
- `isVarArgs()`: executable được đánh dấu varargs hay không.

Mỗi `Parameter` có thể cung cấp:

| API | Ý nghĩa |
| --- | --- |
| `getDeclaringExecutable()` | `Method` hoặc `Constructor` sở hữu tham số |
| `getType()` | Kiểu runtime dạng `Class<?>`, ví dụ `String[].class` |
| `getParameterizedType()` | Kiểu generic dạng `Type` nếu thông tin tồn tại |
| `getName()` | Tên từ metadata hoặc tên thay thế như `arg0` |
| `isNamePresent()` | Có tên tham số được cung cấp theo class file hay không |
| `getModifiers()` | Bit modifier Java của tham số |
| `isImplicit()` | Tham số được khai báo ngầm định theo ngôn ngữ? |
| `isSynthetic()` | Tham số là cấu trúc sinh bởi compiler, không khai báo tường minh hay ngầm định trong nguồn? |
| `isVarArgs()` | Tham số biểu diễn danh sách đối số biến độ dài? |
| `getAnnotations()` / `getAnnotatedType()` | Metadata annotation và type-use annotation nếu được lưu, giữ retention phù hợp |

Ví dụ minh họa cách lấy `Parameter[]`:

```java
Method m = Service.class.getDeclaredMethod(
        "send", String.class, int.class);
Parameter[] parameters = m.getParameters();

for (int i = 0; i < parameters.length; i++) {
    Parameter p = parameters[i];
    System.out.println(i + ": " + p.getType().getTypeName());
}
```

Đây là **đoạn code minh họa**: cần import `java.lang.reflect.Method`, `java.lang.reflect.Parameter`, định nghĩa `Service.send(String,int)` và xử lý `NoSuchMethodException`. Ví dụ độc lập đầy đủ nằm ở mục 5.

## 3. `Parameter.getModifiers()` thực sự biểu diễn gì?

`Parameter.getModifiers()` trả về một `int` chứa các bit modifier của tham số theo mô hình Reflection. Với tham số Java được **khai báo tường minh**, modifier Java nguồn thông dụng và hợp lệ tại vị trí này là `final`. Các từ khóa `public`, `private`, `protected`, `static`, `abstract` không phải modifier hợp lệ của một formal parameter.

Kiểm tra tham số có modifier `final`:

```java
boolean isFinal = Modifier.isFinal(parameter.getModifiers());
```

`Modifier.isFinal(int)` kiểm tra bit `final`. Tránh gọi `Modifier.isPublic(parameter.getModifiers())` rồi từ đó suy luận về quyền truy cập của tham số; bản thân formal parameter không có access level giống field hay method.

Ví dụ:

```java
void save(final String name, int count) {
}
```

Nếu compiler đã lưu metadata tương ứng, Reflection có thể phát hiện `final` ở tham số `name`, còn `count` không có `final`. Nhưng **kết quả `getModifiers() == 0` không chứng minh trong tệp nguồn tham số không có modifier nào**: có thể `MethodParameters` đã bị bỏ hoặc thông tin bị mất qua công cụ sinh/chỉnh bytecode.

### Modifier khác với tên và kiểu

- `getType()` thường vẫn trả về kiểu runtime có thể sử dụng dù tên/modifier bị mất; kiểu nằm trong descriptor.
- `getName()` có thể trả `arg0` khi thiếu tên; nó **không đồng nghĩa tên nguồn là `arg0`**.
- `getModifiers()` không thay thế `isImplicit()`, `isSynthetic()` hay `isVarArgs()` khi cần phân loại tham số.
- `Modifier.toString(parameter.getModifiers())` chỉ diễn giải các modifier mà tiện ích `Modifier` biết hiển thị; đừng dùng nó để kết luận toàn bộ loại cờ trong `MethodParameters`.

Với Java 20+, `Parameter.accessFlags()` cung cấp một tập `AccessFlag` được phản ánh cho tham số. Khi viết chương trình tương thích **Java 17**, hãy dùng các API `getModifiers()`, `isImplicit()` và `isSynthetic()`; không đưa `accessFlags()` vào chương trình Java 17 vì API đó chỉ xuất hiện sau.

## 4. Class file lưu những gì trong `MethodParameters`?

JVM lưu chữ ký của method/constructor qua descriptor. `MethodParameters` là **attribute tùy chọn**, được định nghĩa trong Java Virtual Machine Specification. Khi có mặt, attribute này chứa dữ liệu từng tham số theo thứ tự tương ứng với descriptor:

- `name_index`: chỉ mục tên tham số trong constant pool, hoặc `0` nếu không có tên.
- `access_flags`: tập bit mô tả một số đặc tính của tham số.

Ba cờ quan trọng:

| Cờ trong `MethodParameters` | Giá trị | Ý nghĩa |
| --- | --- | --- |
| `ACC_FINAL` | `0x0010` | Tham số được khai báo `final` |
| `ACC_SYNTHETIC` | `0x1000` | Tham số được sinh như chi tiết triển khai compiler; không khai báo trực tiếp hoặc ngầm định trong nguồn theo quy tắc ngôn ngữ |
| `ACC_MANDATED` | `0x8000` | Tham số được ngôn ngữ quy định ngầm định; compiler cần tạo tham số đó |

`ACC_MANDATED` và `ACC_SYNTHETIC` mang ý nghĩa **khác nhau**: một tham số do đặc tả ngôn ngữ bắt buộc có thể là *implicit*, còn tham số synthetic có thể chỉ phục vụ triển khai của compiler. Không ghép chúng thành một nhãn “tham số tự sinh” rồi mất sự phân biệt.

Ánh xạ API thường dùng:

```java
parameter.isImplicit();   // Liên quan trạng thái implicit / mandated
parameter.isSynthetic();  // Liên quan trạng thái synthetic
parameter.getModifiers(); // Các bit modifier của tham số
```

Đây chỉ là lời gọi minh họa (chưa khai báo biến `parameter`). Với file class thiếu `MethodParameters`, các cờ này **không có đủ dữ liệu** để thuật lại chính xác tất cả đặc điểm khi viết mã nguồn. Đặc biệt, `false` ở `isImplicit()` hoặc `isSynthetic()` không nên được coi là bằng chứng tuyệt đối rằng compiler không tạo tham số.

**Không nhầm vị trí các cờ:** `ACC_VARARGS` ở `method_info.access_flags` mô tả đặc tính varargs của *phương thức*, không phải một cờ `ACC_VARARGS` trong `MethodParameters`. `Parameter.isVarArgs()` nhận diện tham số cuối thích hợp qua metadata executable.

### Vì sao `javac -parameters` quan trọng?

Lệnh:

```bash
javac -parameters Example.java
```

yêu cầu `javac` ghi metadata về **tên formal parameter** của constructor và method để Reflection có thể lấy bằng `Executable.getParameters()`. Trên bytecode được `javac` sinh, tùy chọn này cũng giúp công cụ thấy `MethodParameters` cùng các cờ parameter hiện diện.

Nếu biên dịch không có `-parameters`, nhiều parameter tường minh sẽ không còn tên thật và các cờ gắn với `MethodParameters` có thể không quan sát được. JVM vẫn cần descriptor kiểu tham số để xác minh và gọi method, nên `getParameterTypes()`/`getParameterCount()` tiếp tục hoạt động.

Lưu ý sự khác biệt:

- `-parameters`: tạo metadata tham số dành cho Java Reflection.
- `-g`: tạo debug information, ví dụ `LocalVariableTable`/`LineNumberTable`. Có thể giúp debugger hoặc công cụ bytecode, nhưng **không thay thế bảo đảm của `-parameters` cho `Parameter.isNamePresent()`**.
- `-g:none`: bỏ debug information, không có nghĩa bắt buộc loại bỏ `MethodParameters` nếu đã sử dụng `-parameters`.

Không giả định mọi compiler, obfuscator hoặc bytecode transformer đều giữ các attribute như `javac`. Nếu framework cần tên tham số đáng tin, hãy quy định rõ build config hoặc cung cấp annotation tên tường minh làm phương án thay thế.

## 5. Ví dụ Java 17+ runnable: modifier và metadata

Tạo tệp **`ParameterFlagsDemo.java`**:

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;

public class ParameterFlagsDemo {
    public static void send(
            final String topic, int count, String... tags) {
        // Ví dụ chỉ dùng để quan sát chữ ký.
    }

    static class Host {
        class Member {
            Member(int number) {
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Method send = ParameterFlagsDemo.class.getDeclaredMethod(
                "send", String.class, int.class, String[].class);
        Parameter[] params = send.getParameters();

        System.out.println("method params: " + params.length);
        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            System.out.println(i + " type=" + p.getType().getTypeName()
                    + " name=" + p.getName()
                    + " present=" + p.isNamePresent()
                    + " final=" + Modifier.isFinal(p.getModifiers())
                    + " implicit=" + p.isImplicit()
                    + " synthetic=" + p.isSynthetic()
                    + " varargs=" + p.isVarArgs());
        }

        System.out.println("method varargs: " + send.isVarArgs());

        Constructor<?> memberCtor =
                Host.Member.class.getDeclaredConstructor(
                        Host.class, int.class);
        System.out.println("member constructor params: "
                + memberCtor.getParameterCount());
        System.out.println("first parameter is Host: "
                + (memberCtor.getParameterTypes()[0] == Host.class));
    }
}
```

Biên dịch với metadata tên tham số:

```bash
javac -parameters ParameterFlagsDemo.java
java ParameterFlagsDemo
```

**Stdout mong đợi với `javac` JDK 17+ và class file do chính lệnh trên biên dịch:**

```text
method params: 3
0 type=java.lang.String name=topic present=true final=true implicit=false synthetic=false varargs=false
1 type=int name=count present=true final=false implicit=false synthetic=false varargs=false
2 type=java.lang.String[] name=tags present=true final=false implicit=false synthetic=false varargs=true
method varargs: true
member constructor params: 2
first parameter is Host: true
```

Giải thích:

1. `send` có ba tham số, lấy `Class<?>` và thứ tự từ descriptor; kiểu thứ ba là `String[]` ở runtime dù nguồn viết `String...`.
2. `-parameters` làm `isNamePresent()` là `true` cho những tham số có tên trong class file. `getName()` trả đúng `topic`, `count`, `tags`.
3. `Modifier.isFinal(...)` là `true` cho tham số `topic`; hai tham số sau không `final`.
4. Ba tham số `send` đều được khai báo tường minh nên `isImplicit()` và `isSynthetic()` là `false`.
5. `isVarArgs()` chỉ đúng với tham số cuối `tags`; `Method.isVarArgs()` đúng cho method `send`.
6. `Host.Member` là non-static member inner class, vì vậy constructor mà Reflection thấy có thêm tham số đầu kiểu `Host` và tổng cộng hai tham số, dù nguồn chỉ ghi `Member(int)`. Chương trình **không giả định** tên hay cờ implicit của tham số ẩn là cố định trên mọi compiler.

### Kiểm chứng khi không có `-parameters`

Để thấy sự khác biệt, biên dịch lại (đè các tệp `.class` được tạo từ chính ví dụ này):

```bash
javac ParameterFlagsDemo.java
java ParameterFlagsDemo
```

Điều quan trọng cần kiểm tra là `getParameterCount()` và các kiểu của `send` vẫn giữ nguyên, trong khi ba tên tham số tường minh có thể không còn được lưu. Với `javac` thông thường, `isNamePresent()` ở các tham số ấy là `false` và `getName()` thường trả `arg0`, `arg1`, `arg2`. Những cờ phụ thuộc `MethodParameters` cũng có thể không còn được phản ánh.

Không trình bày một mẫu stdout thứ hai như thể mọi compiler/bytecode transformer đều đưa ra cùng cờ: phần nào trong metadata bị giữ lại là đặc tính của class file thực tế.

### Xem bytecode bằng `javap`

```bash
javap -v -p ParameterFlagsDemo
```

Tìm các mục `MethodParameters`, `descriptor` và `flags` của method `send`. Khi có `-parameters`, bạn có thể tìm tên và cờ `final` trong dữ liệu tham số. Descriptor của `send` thể hiện `String`, `int` và `String[]`; không cần tên để mô tả các kiểu này.

Nếu không có `MethodParameters`, đó là giới hạn dữ liệu ở **class file**; Reflection không thể đoán lại chính xác tên hay modifier nguồn chỉ dựa vào số lượng và kiểu tham số.

## 6. `isImplicit`, `isSynthetic` và tham số do compiler tạo

### 6.1. Inner class và enclosing instance

Non-static inner member class gắn với một instance của lớp bao ngoài. Constructor của nó thường có một tham số runtime đứng đầu để nhận đối tượng bao ngoài.

```java
class House {
    class Room {
        Room(int floor) {
        }
    }
}
```

Từ Reflection:

```java
Constructor<?> ctor =
        House.Room.class.getDeclaredConstructor(House.class, int.class);
Class<?>[] runtimeTypes = ctor.getParameterTypes();
```

Đây là đoạn minh họa cần import `java.lang.reflect.Constructor` và xử lý checked exception. `runtimeTypes[0]` là `House.class`; `runtimeTypes[1]` là `int.class`.

Tham số cho enclosing instance được sinh để thể hiện ngữ nghĩa của inner class. Khi có metadata cần thiết, compiler có thể ghi cờ mô tả nó là implicit/mandated. Nhưng việc `getParameters()` có trả đúng danh sách kiểu **không đồng nghĩa** `isImplicit()` hay `isSynthetic()` luôn báo đúng ý nghĩa nguồn khi metadata bị loại bỏ. Bài toán gọi constructor phải dựa vào signature và một enclosing instance thật, không chỉ vào một flag.

### 6.2. Enum constructor

Constructor của enum nhận các tham số hỗ trợ tên và ordinal ở mức bytecode, ngoài các tham số được viết trong nguồn. Đây là dữ liệu liên quan tới cách JVM biểu diễn enum và có thể được phản ánh ở chữ ký constructor.

```java
enum Mode {
    FAST(1), SAFE(2);

    private final int speed;

    Mode(int speed) {
        this.speed = speed;
    }
}
```

Không được kết luận rằng chỉ có duy nhất một tham số runtime vì nguồn viết `Mode(int)`. Tuy nhiên, **không cố gọi enum constructor bằng Reflection**: `Constructor.newInstance(...)` không cho tạo enum object theo cách này và có thể ném `IllegalArgumentException`.

Khi phân tích `Parameter[]` của enum, cần tách các tham số ngầm định hoặc do compiler thêm với những tham số người viết khai báo. Với metadata không đầy đủ, đừng xây công cụ chỉ bằng bộ lọc `!p.isSynthetic() && !p.isImplicit()` rồi tin rằng nó tái tạo chữ ký nguồn hoàn hảo.

### 6.3. Local/anonymous class và biến captured

Các local/anonymous classes có thể capture biến local effectively final hoặc cần truyền ngữ cảnh bao ngoài vào constructor. Compiler có thể thêm các tham số kỹ thuật, chẳng hạn để chuyển những giá trị captured.

Vì dạng chữ ký phụ thuộc context và compiler, hãy kiểm tra `getDeclaredConstructors()`, `getParameterTypes()` và metadata thực tế. Không áp đặt “tham số đầu luôn là outer class” cho mọi local/anonymous class; quy tắc về enclosing instance của **non-static member inner class** không bao phủ toàn bộ trường hợp.

### 6.4. Không đồng nhất implicit, synthetic với `final`

Một tham số có thể:

- Được viết tường minh và có `final`.
- Được viết tường minh mà không có `final`.
- Được ngôn ngữ quy định ngầm định (implicit/mandated).
- Do compiler tạo như một chi tiết triển khai (synthetic).

Những khía cạnh này **không cùng một trục phân loại**. `Modifier.isFinal(p.getModifiers())` nói về cờ `final`; `p.isImplicit()` và `p.isSynthetic()` nói về nguồn gốc parameter theo thông tin phản ánh. Không suy ra `final` chỉ vì tham số được compiler sinh, và cũng không cho rằng `final` nghĩa là synthetic.

## 7. Tên tham số: `getName` khác `isNamePresent`

Ví dụ:

```java
public void register(String username, int attempts) {
}
```

Nếu thiếu metadata tên:

```java
Parameter[] ps = method.getParameters();
System.out.println(ps[0].getName());        // Có thể là arg0
System.out.println(ps[0].isNamePresent());   // false nếu không có tên
```

Đây là đoạn minh họa cần `method` đại diện cho đúng method. `getName()` không trả `null` chỉ vì metadata tên thiếu; nó cung cấp một tên thay thế để lập trình viên vẫn có thể sử dụng `Parameter` API.

**Quy tắc cho framework:** muốn dùng tên parameter làm khóa dependency injection hoặc tên query argument, hãy kiểm tra `isNamePresent()`. Nếu false, không tự ý dùng `arg0` làm tên API thật của người sử dụng. Có thể yêu cầu build bật `-parameters` hoặc sử dụng annotation chứa tên đã khai báo tường minh.

Kể cả `isNamePresent() == true`, metadata có thể đã bị **obfuscate** hoặc sửa bởi bytecode transformer. Điều đó chứng minh class file hiện tại cung cấp một tên, không chứng minh đó chắc chắn là token trong source trước khi biến đổi.

## 8. `getType`, `getParameterizedType` và varargs không phải modifier thông thường

### 8.1. Generic erasure

Cho method:

```java
void consume(java.util.List<String> names) {
}
```

- `Parameter.getType()` trả `java.util.List.class`: kiểu runtime sau type erasure.
- `Parameter.getParameterizedType()` có thể cho thấy `List<String>` khi `Signature` attribute được lưu phù hợp.
- `getModifiers()` không có nhiệm vụ chứa thông tin `<String>`.
- Nếu mất `Signature`, metadata class file không đủ để khôi phục một cách chắc chắn type argument `String`.

Điều tương tự áp dụng cho generic type variable, wildcard và kiểu parameterized trên constructor/method.

### 8.2. Varargs và tham số mảng

`String... values` được biểu diễn như `String[]` trong descriptor JVM. Không thể chỉ nhìn `getType() == String[].class` rồi khẳng định nguồn viết `String...` thay vì `String[]`.

```java
void accept(String[] values) {}
void collect(String... values) {}
```

`accept` không được đánh dấu varargs; `collect` được đánh dấu varargs. `Executable.isVarArgs()` và `Parameter.isVarArgs()` trên tham số cuối giúp phân biệt thuộc tính runtime. **Varargs không phải một bit modifier thông thường của từng parameter trong `MethodParameters`**.

### 8.3. Receiver parameter trong cú pháp nguồn

Java còn hỗ trợ cú pháp **receiver parameter** tường minh ở một số khai báo method/constructor, ví dụ:

```java
class Point {
    void check(Point this) {
    }
}
```

Receiver parameter là cách biểu đạt về receiver/các annotation liên quan ở ngôn ngữ, **không phải** một formal parameter bình thường trong danh sách đối số runtime. `Executable.getParameters()` không sinh thêm một `Parameter` chỉ vì nguồn ghi `Point this`. Nếu cần metadata annotation trên receiver, xem `Executable.getAnnotatedReceiverType()` khi class file cung cấp.

Do đó, không đồng nhất cụm từ **receiver parameter** với tham số enclosing instance do compiler đưa vào constructor của non-static member inner class.

## 9. Annotation trên parameter và giới hạn retention

Annotation không phải modifier bit:

```java
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
@interface Label {
    String value();
}

class Example {
    void process(@Label("user") String name) {
    }
}
```

Dùng Reflection có thể đọc annotation từ `Parameter.getAnnotation(Label.class)`, nhưng chỉ khi retention phù hợp và metadata có mặt trong class file. Nếu annotation có `RetentionPolicy.SOURCE`, nó không có mặt trong class file chuẩn; nếu retention là `CLASS`, thông thường không được expose qua core Reflection runtime.

Chú ý sự khác biệt giữa **declaration annotation** trên formal parameter và **type-use annotation** trên kiểu của parameter. `Parameter.getAnnotations()` và `Parameter.getAnnotatedType()` trả các góc nhìn khác nhau. Cả hai đều không biến `getModifiers()` thành nơi lưu mọi annotation.

## 10. Quy trình phân tích có khả năng chịu thiếu metadata

Khi một framework muốn liệt kê tham số executable, có thể dùng quy trình sau:

1. Xác định đúng `Method` hoặc `Constructor<?>`. Không suy đoán constructor của inner class chỉ từ số tham số nguồn.
2. Dùng `getParameterCount()`/`getParameterTypes()` làm nền tảng vì chúng phản ánh descriptor.
3. Lấy `getParameters()` để đọc tên và modifier. Kiểm tra `isNamePresent()` **trước khi** phụ thuộc `getName()`.
4. Đọc `getModifiers()` và kiểm tra `final` bằng `Modifier.isFinal()`. Nếu chỉ nhận bit 0, chưa thể biết nguồn có `final` hay không khi metadata thiếu.
5. Đọc `isImplicit()`/`isSynthetic()` để nhận diện các trường hợp compiler/language thêm tham số **nếu metadata hỗ trợ**. Đừng mặc định false đồng nghĩa “được người viết khai báo”.
6. Với varargs, hỏi `Executable.isVarArgs()` và `Parameter.isVarArgs()`; với generics, dùng `getParameterizedType()`.
7. Nếu tên/cờ không đáng tin, kiểm tra quy trình build và class file với `javap -v -p`, đặc biệt `MethodParameters`, `Signature`, `RuntimeVisibleParameterAnnotations` và descriptor.
8. Nếu không tìm đủ metadata, báo dữ liệu **không xác định** hoặc dùng hợp đồng đặt tên khác đã thiết kế trước. Không đoán lại nguyên văn mã nguồn.

### Ví dụ quyết định trong code

```java
for (Parameter p : executable.getParameters()) {
    String name = p.isNamePresent()
            ? p.getName()
            : "<unknown>";
    boolean declaredFinal = Modifier.isFinal(p.getModifiers());
    // Các cờ phản ánh class file hiện có; đừng coi false
    // là bằng chứng không thể sai về cú pháp nguồn.
    System.out.println(name + " / final-flag=" + declaredFinal);
}
```

Đây là đoạn minh họa, trong đó `executable` là một `Executable` đã được xác định và cần import `Executable`, `Parameter`, `Modifier`. Ý tưởng là bộc lộ ranh giới giữa thông tin đã biết và metadata thiếu, thay vì âm thầm suy diễn.

## 11. Các lỗi thường gặp

| Nhầm lẫn | Vì sao sai | Cách xử lý |
| --- | --- | --- |
| Không dùng `-parameters` nhưng vẫn coi `getName()` là tên nguồn | `getName()` có thể trả `arg0`/`arg1` do thiếu metadata | Kiểm tra `isNamePresent()` |
| `getModifiers() == 0` nghĩa là chắc chắn nguồn không ghi `final` | Có thể không có `MethodParameters` | Xem build flags/class file; báo thiếu dữ liệu |
| Mọi tham số do compiler sinh đều `isSynthetic()` | Có nhóm `implicit/mandated` khác `synthetic`, và metadata có thể thiếu | Kiểm tra phân loại và hợp đồng API |
| `-g` tương đương `-parameters` | Debug attribute và MethodParameters có mục đích khác nhau | Dùng `-parameters` khi cần tên qua Reflection |
| `getParameterTypes()` cần `-parameters` | Descriptor lưu kiểu runtime riêng | Dùng API này kể cả khi tên bị mất |
| Mảng `String[]` chứng minh parameter nguồn là varargs | Cả tham số mảng thường lẫn varargs có thể có kiểu `String[]` | Dùng `isVarArgs()` |
| `List<String>` luôn dựng lại được từ `getType()` | Type erasure làm `getType()` là `List.class` | Xem `getParameterizedType()` và `Signature` |
| `getParameters()` tương ứng 1-1 với các tham số viết tường minh | Constructor inner/enum, local/anonymous có thể thêm tham số | Phân tích executable và metadata thực tế |
| `getModifiers()` chứa annotation parameter | Annotation lưu theo attributes/retention khác | Dùng API annotation |
| Thấy `isImplicit() == false` thì suy ra nguồn chắc chắn khai báo tham số | Flag có thể không tồn tại trong class file | Không suy luận chắc chắn khi thiếu metadata |

## 12. Bài tự kiểm tra

**Câu 1.** Một method được khai báo `process(final String item, int retries)`. Nếu Reflection trả `Modifier.isFinal(first.getModifiers()) == false`, có được kết luận nguồn không ghi `final` không?

**Đáp án:** Không. Cần xác định `MethodParameters` có ghi cờ tương ứng và liệu class file bị biến đổi hay không. Giá trị false đơn lẻ chỉ mô tả metadata Reflection hiện quan sát được.

**Câu 2.** `getParameterTypes()` có thể hoạt động khi không bật `javac -parameters` không?

**Đáp án:** Có. Method descriptor giữ kiểu runtime; `-parameters` chủ yếu phục vụ metadata tên/parameter flags.

**Câu 3.** `p.getName()` trả `arg0` và `p.isNamePresent()` trả `false`. `arg0` có phải chắc chắn là tên đã viết trong `.java` không?

**Đáp án:** Không. Đây là tên thay thế do Reflection cung cấp khi class file không chứa tên formal parameter.

**Câu 4.** `ACC_MANDATED` và `ACC_SYNTHETIC` có đồng nghĩa không?

**Đáp án:** Không. `mandated` liên quan tham số ngầm định theo yêu cầu ngôn ngữ; `synthetic` liên quan chi tiết triển khai compiler.

**Câu 5.** Vì sao `Parameter.isVarArgs()` có thể trả true dù `getModifiers()` không có cờ varargs?

**Đáp án:** Varargs thuộc metadata method-level `ACC_VARARGS` và vị trí tham số cuối; không phải một flag thuộc danh sách ba cờ của `MethodParameters`.

**Câu 6.** Một constructor non-static inner class có hai `Parameter` trong khi nguồn khai báo một tham số. Có phải Reflection sai không?

**Đáp án:** Không. Chữ ký runtime có thể thêm tham số đầu để truyền enclosing instance.

## 13. Ghi nhớ và nguồn chuẩn

**Không đồng nhất chữ ký runtime với toàn bộ cú pháp nguồn.** `Executable.getParameters()` luôn mô tả tham số hiện có ở executable; kiểu tham số lấy từ descriptor, còn tên, modifier `final` và cờ implicit/synthetic có thể phụ thuộc `MethodParameters`. Sử dụng `-parameters` khi cần tên, kiểm tra `isNamePresent()` trước khi tin `getName()`, và phân biệt `ACC_FINAL`, `ACC_SYNTHETIC`, `ACC_MANDATED`.

Tài liệu chuẩn:

- [Java SE 21 — `java.lang.reflect.Parameter`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Parameter.html)
- [Java SE 21 — `java.lang.reflect.Executable`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Executable.html)
- [JVM Specification 21 — `MethodParameters` attribute (`4.7.24)](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html)
- [`javac` option `-parameters`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javac.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executable-parameters-008">Annotation trên từng tham số</a>

<details>
<summary>Xem chi tiết</summary>

# Annotation trên từng tham số của Method và Constructor trong Java Reflection

Khi dùng Java Reflection để kiểm tra **tham số của một phương thức hoặc constructor**, ta không chỉ biết số lượng, tên và kiểu dữ liệu; ta còn có thể đọc **annotation được gắn trên từng tham số**. Điều này rất quan trọng trong những framework dùng annotation để khai báo cách lấy dữ liệu đầu vào, kiểm tra hợp lệ, dependency injection, ánh xạ HTTP/JSON hoặc cấu hình cơ chế tạo object.

Ví dụ:

```java
public void createUser(@Named("username") String name,
                       @Positive int age) {
    // Logic xử lý.
}
```

Đây là **đoạn minh họa cú pháp**, trong đó `@Named` và `@Positive` là các annotation do ứng dụng hoặc thư viện định nghĩa, không phải annotation chuẩn sẵn có trong Java SE. Hai annotation tương ứng với **hai tham số khác nhau**. Runtime có thể đọc metadata này nếu kiểu annotation được định nghĩa với `@Retention(RetentionPolicy.RUNTIME)`.

Bài học tập trung vào cách Reflection xác định annotation **của từng parameter** trên `java.lang.reflect.Method` và `java.lang.reflect.Constructor`, cách dùng `Executable.getParameterAnnotations()` và `Parameter`, sự khác nhau giữa annotation trên **khai báo tham số** và **kiểu sử dụng (type-use)**, cùng các vấn đề `RUNTIME` retention, `-parameters`, repeatable annotation và synthetic/implicit parameters.

## 1. Các lớp Reflection liên quan

Một phương thức hoặc constructor là một **executable**. Java có lớp nền `java.lang.reflect.Executable`; hai lớp triển khai quan trọng là:

- `Method`: metadata của một phương thức.
- `Constructor<T>`: metadata của constructor.
- `Parameter`: metadata của **một tham số** trong một `Method` hoặc `Constructor`.
- `Annotation`: interface nền `java.lang.annotation.Annotation`, đại diện cho một annotation runtime.
- `AnnotatedType`: metadata annotation gắn vào **việc sử dụng kiểu** của một tham số, không đồng nhất với annotation gắn vào khai báo tham số.

Các API thường dùng:

| API | Trả về | Dùng khi nào |
|---|---|---|
| `executable.getParameterCount()` | `int` | Biết số tham số trong metadata của executable |
| `executable.getParameters()` | `Parameter[]` | Cần xử lý từng tham số cùng tên, kiểu và annotation |
| `executable.getParameterAnnotations()` | `Annotation[][]` | Cần danh sách annotation **theo chỉ số tham số** |
| `parameter.getAnnotations()` | `Annotation[]` | Đọc các annotation runtime được gắn trực tiếp lên một parameter |
| `parameter.getAnnotation(MyAnnotation.class)` | `MyAnnotation` hoặc `null` | Tìm một annotation cụ thể trên parameter |
| `parameter.isAnnotationPresent(MyAnnotation.class)` | `boolean` | Kiểm tra nhanh sự tồn tại của annotation |
| `parameter.getAnnotationsByType(MyAnnotation.class)` | `MyAnnotation[]` | Lấy cả annotation **repeatable** cùng loại |
| `parameter.getType()` | `Class<?>` | Xem kiểu khai báo sau type erasure |
| `parameter.getParameterizedType()` | `Type` | Xem thông tin generic type của parameter |
| `executable.getAnnotatedParameterTypes()` | `AnnotatedType[]` | Đọc annotation `TYPE_USE` trên kiểu của từng parameter |

`Method` và `Constructor` có chung nhiều phương thức kể trên vì đều là `Executable`. Do đó, cách đọc annotation của tham số thường có thể tái sử dụng cho cả hai.

## 2. Khai báo annotation dành cho tham số

Annotation là một kiểu được khai báo bằng `@interface`. Hai meta-annotation quan trọng nhất trong tình huống này là:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Named {
    String value();
}
```

Đoạn code này có thể lưu thành **`Named.java`** và biên dịch độc lập nếu có JDK; đây là annotation mẫu, không phải chương trình có `main`.

- `@Target(ElementType.PARAMETER)` cho phép dùng `@Named` trên **khai báo tham số** của method, constructor (và các vị trí parameter phù hợp khác).
- `@Retention(RetentionPolicy.RUNTIME)` làm cho annotation được lưu lại và có thể được Reflection đọc lúc chạy.
- `String value();` là một **phần tử của annotation**. Nếu annotation được khai báo `@Named("id")`, gọi `named.value()` nhận chuỗi `"id"`.
- Nếu annotation không có `@Retention`, mặc định là **`RetentionPolicy.CLASS`**; class file có thể mang metadata nhưng core Reflection **không cung cấp annotation đó ở runtime**.

Ví dụ:

```java
public User(@Named("id") String id) {
    // Constructor của User.
}
```

Annotation trên parameter có thể đóng vai trò hướng dẫn framework đọc dữ liệu đầu vào. Tuy nhiên, Java **không tự động validate hoặc bind dữ liệu** chỉ vì parameter có `@Named`: framework/ứng dụng phải chủ động đọc và xử lý annotation.

### Ba mức retention

| Retention | Được ghi vào class file? | Core Reflection đọc annotation lúc runtime? |
|---|---|---|
| `SOURCE` | Không; compiler thường bỏ sau khi xử lý source | Không |
| `CLASS` | Có | Không, theo cơ chế runtime annotation Reflection chuẩn |
| `RUNTIME` | Có | Có, khi loại annotation và metadata có thể truy cập |

Nếu `parameter.getAnnotation(Named.class)` trả `null`, **chưa chắc** annotation đó không từng xuất hiện trong source: nó có thể có retention không phù hợp, nằm trên type-use chứ không phải parameter declaration, bị đặt vào parameter khác hoặc không tồn tại trong bytecode runtime.

## 3. `getParameterAnnotations()`: mảng hai chiều căn theo tham số

Ví dụ có method sau:

```java
public void submit(@Named("user") String username,
                   @Positive int count,
                   String note) {
}
```

Giả định `@Named` và `@Positive` đều là annotation runtime, có target `PARAMETER`. Khi tra cứu method:

```java
Method method = Example.class.getMethod(
        "submit", String.class, int.class, String.class);

Annotation[][] annotations = method.getParameterAnnotations();
```

Các đoạn trên là **minh họa**, `Example` và `Positive` phải được định nghĩa riêng, và code có import `java.lang.reflect.Method`, `java.lang.annotation.Annotation`. Mảng được tổ chức theo cấu trúc:

```text
annotations
  [0] -> annotation của username: @Named("user")
  [1] -> annotation của count:    @Positive
  [2] -> annotation của note:     (mảng rỗng)
```

Quy tắc quan trọng:

1. **Chiều ngoài** có một phần tử cho mỗi tham số theo thứ tự khai báo/chữ ký của executable. `annotations.length` tương ứng với `method.getParameterCount()`.
2. **Chiều trong** chứa annotation của **tham số ở chỉ số đó**. Một parameter có thể có không, một hoặc nhiều annotation.
3. Tham số không có annotation runtime được biểu diễn bằng **mảng annotation rỗng**, không phải bỏ hẳn phần tử ở chiều ngoài.
4. Với executable không có tham số, mảng ngoài có độ dài **`0`**.
5. Không dựa vào **thứ tự các annotation khác loại** trong mảng ở cùng một parameter để làm logic nghiệp vụ; hãy tìm theo annotation type. Đặc biệt, repeatable annotations có thể được lưu qua **container annotation**.

Mẫu duyệt đáng tin cậy:

```java
Annotation[][] matrix = method.getParameterAnnotations();
for (int index = 0; index < matrix.length; index++) {
    System.out.println("Parameter " + index
            + " has " + matrix[index].length + " runtime annotations");
}
```

Đây là đoạn minh họa đặt trong code đã có `method` cùng import `Annotation`. Nếu muốn đọc metadata ngoài annotation, ta lấy `Parameter[]` và ghép bằng **cùng chỉ số**.

## 4. `Parameter`: truy vấn từng tham số theo loại annotation

Thay vì tự duyệt mảng hai chiều, `Parameter` cung cấp API gần với việc xử lý từng parameter hơn:

```java
Parameter[] parameters = method.getParameters();

for (Parameter parameter : parameters) {
    Named named = parameter.getAnnotation(Named.class);
    if (named != null) {
        System.out.println(named.value());
    }
}
```

Đây là ví dụ dùng kiểu `Named` tự định nghĩa và `Method method` đã có. Điểm mạnh là ta xử lý tên, kiểu và annotation của **một tham số** trong cùng một đối tượng.

- `getAnnotation(T.class)` trả một annotation phù hợp hoặc `null`.
- `isAnnotationPresent(T.class)` cho biết có annotation cùng loại theo API kiểm tra không.
- `getAnnotations()` trả các annotation runtime có trên parameter; với repeatable annotations, có thể thấy container thay vì từng annotation lặp.
- `getDeclaredAnnotations()` trên `Parameter` đọc annotation được khai báo trực tiếp. Parameter **không có cơ chế kế thừa annotation như lớp**; vì vậy không nên thiết kế logic dựa vào annotation parameter của superclass tự động truyền xuống.
- `getAnnotationsByType(T.class)` là cách thích hợp khi `T` có thể được đánh dấu `@Repeatable`.

Một annotation như `@Named("id")` là metadata runtime được đọc qua interface `Named`. Gọi `named.value()` để nhận giá trị annotation; không nhầm với việc **lấy giá trị của argument** được truyền vào lúc method thực sự chạy. Reflection metadata mô tả khai báo, không ghi lại argument từ một invocation cụ thể.

## 5. Ví dụ Java 17+ hoàn chỉnh: Method, Constructor, RUNTIME, TYPE_USE, Repeatable

Tạo một tệp **`ParameterAnnotationDemo.java`** với nội dung sau. Mọi annotation và class thử nghiệm đều được đặt trong một public outer class, nên không cần thư viện ngoài Java SE:

```java
import java.lang.annotation.*;
import java.lang.reflect.*;

public class ParameterAnnotationDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface Label {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @Repeatable(Tags.class)
    public @interface Tag {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface Tags {
        Tag[] value();
    }

    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.PARAMETER)
    public @interface CompileOnly {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    public @interface NonNullType {}

    public static class Service {
        public Service(@Label("id") String id,
                       @Tag("create") @Tag("audit") int limit) {}

        public void send(@Label("to") @Tag("email") String recipient,
                         @CompileOnly String token,
                         @NonNullType String body) {}

        public void empty() {}
    }

    public static void main(String[] args) throws Exception {
        Method method = Service.class.getMethod(
                "send", String.class, String.class, String.class);
        Annotation[][] matrix = method.getParameterAnnotations();

        System.out.println("method parameters: "
                + method.getParameterCount());
        for (int i = 0; i < matrix.length; i++) {
            System.out.println("send[" + i + "] annotation count: "
                    + matrix[i].length);
        }

        Parameter[] params = method.getParameters();
        System.out.println("label: "
                + params[0].getAnnotation(Label.class).value());
        System.out.println("tag: "
                + params[0].getAnnotationsByType(Tag.class)[0].value());
        System.out.println("runtime CLASS annotation present: "
                + params[1].isAnnotationPresent(CompileOnly.class));

        System.out.println("type-use annotation on type: "
                + method.getAnnotatedParameterTypes()[2]
                        .isAnnotationPresent(NonNullType.class));
        System.out.println("type-use annotation on declaration: "
                + params[2].isAnnotationPresent(NonNullType.class));

        System.out.println("name present: "
                + params[0].isNamePresent());
        System.out.println("name: " + params[0].getName());

        Constructor<Service> constructor =
                Service.class.getConstructor(String.class, int.class);
        Parameter[] cp = constructor.getParameters();

        System.out.println("constructor label: "
                + cp[0].getAnnotation(Label.class).value());
        Tag[] tags = cp[1].getAnnotationsByType(Tag.class);
        System.out.println("constructor repeatable count: " + tags.length);
        for (Tag tag : tags) {
            System.out.println("constructor tag: " + tag.value());
        }
        System.out.println("constructor raw annotation count: "
                + constructor.getParameterAnnotations()[1].length);
        System.out.println("empty annotation slots: "
                + Service.class.getMethod("empty")
                        .getParameterAnnotations().length);
    }
}
```

Chạy với JDK 17+ từ thư mục chứa tệp:

```bash
javac -parameters ParameterAnnotationDemo.java
java ParameterAnnotationDemo
```

**Stdout đã được đối chiếu khi biên dịch và chạy:**

```text
method parameters: 3
send[0] annotation count: 2
send[1] annotation count: 0
send[2] annotation count: 0
label: to
tag: email
runtime CLASS annotation present: false
type-use annotation on type: true
type-use annotation on declaration: false
name present: true
name: recipient
constructor label: id
constructor repeatable count: 2
constructor tag: create
constructor tag: audit
constructor raw annotation count: 1
empty annotation slots: 0
```

### Phân tích từng nhóm output

1. `send` có ba tham số. Ở `send[0]`, `@Label("to")` và `@Tag("email")` đều có `RUNTIME` retention và target `PARAMETER`, vì vậy `getParameterAnnotations()[0]` có **2 annotation**.
2. `send[1]` được đánh dấu `@CompileOnly`, nhưng annotation có retention `CLASS`, nên **không hiển thị bằng core Reflection**; mảng annotation runtime của tham số này rỗng.
3. `send[2]` có `@NonNullType` với target `TYPE_USE`. Nó được lưu trên **kiểu `String` được sử dụng ở tham số**, không được trả như parameter-declaration annotation. `getAnnotatedParameterTypes()[2]` nhìn thấy nó, trong khi `Parameter.isAnnotationPresent(NonNullType.class)` trả `false`.
4. `Label.value()` trả `to`, và `getAnnotationsByType(Tag.class)` trả annotation `Tag` để đọc `email`.
5. Chạy `javac -parameters` giữ **tên tham số ở mức Reflection**: `isNamePresent() == true`, `getName()` trả `recipient`. Nếu biên dịch **không có `-parameters`** trong trường hợp Java source thông thường này, `isNamePresent()` có thể là `false`, tên do Reflection suy ra có thể là `arg0`. Việc đọc các annotation runtime **không cần** `-parameters`.
6. Constructor `Service(String,int)` cũng có `Parameter[]`. Tham số đầu tiên chứa `@Label("id")`. Tham số thứ hai có hai `@Tag` lặp; `getAnnotationsByType(Tag.class)` trả **2** phần tử, còn `getParameterAnnotations()[1]` thông thường thấy **1 container annotation `@Tags`**.
7. Method `empty()` không tham số nên `getParameterAnnotations()` trả mảng ngoài độ dài `0`.

**Quan trọng:** ví dụ chỉ **đọc metadata**. Không có chỗ nào Java tự kích hoạt validate `@Label`, `@Tag` hoặc `@NonNullType`. Annotation type-use ở đây chỉ là nhãn tùy chỉnh; nó **không tự ngăn** truyền `null` vào tham số.

## 6. Annotation trên parameter khác annotation trên type-use

Java hỗ trợ hai kiểu vị trí có thể nhìn gần giống nhau trong source:

```java
void process(@Label("input") String value) {
}

void processType(@NonNullType String value) {
}
```

Cả hai dòng là đoạn minh họa được khai báo trong một class thích hợp, với `@Label` có target `PARAMETER`, `@NonNullType` có target `TYPE_USE`.

- `@Label` là **annotation của khai báo tham số**. Dùng `Parameter.getAnnotation(Label.class)` hoặc `Executable.getParameterAnnotations()`.
- `@NonNullType` là **annotation của việc sử dụng kiểu `String`**. Dùng `Executable.getAnnotatedParameterTypes()` rồi đọc `AnnotatedType`.
- Nếu một annotation type được phép target cả `PARAMETER` lẫn `TYPE_USE`, compiler có thể ghi metadata cho cả các ngữ cảnh phù hợp; không nên phỏng đoán bằng cách nhìn vị trí chữ `@` trong source. Hãy đọc `@Target` và kiểm tra API phù hợp.

`getAnnotatedParameterTypes()` trả mảng `AnnotatedType[]`, tương ứng theo thứ tự với các parameter types. Điều này hữu ích khi mô tả các kiểu có annotation gắn sâu:

```java
void store(java.util.List<@NonNullType String> names) {
}
```

Đây là **đoạn minh họa** về annotation trên **generic type argument**, không phải annotation của parameter declaration. Để đọc `@NonNullType` ở `String` bên trong `List`, có thể xem `AnnotatedParameterizedType.getAnnotatedActualTypeArguments()` sau khi xác định `AnnotatedType` bên ngoài là `AnnotatedParameterizedType`.

Phân biệt với annotation chỉ có target `PARAMETER`: các API `Parameter.getAnnotations()` không tự “đào sâu” vào annotation trên generic type arguments, các lớp phần tử của mảng hoặc annotated wildcard.

## 7. Repeatable annotation: tại sao số đếm có thể khác?

Giả sử annotation được đánh dấu:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@Repeatable(Tags.class)
public @interface Tag {
    String value();
}
```

và khai báo container tương ứng:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Tags {
    Tag[] value();
}
```

Hai đoạn là khai báo minh họa; cần đặt trong file/class phù hợp và import `java.lang.annotation.*`. Khi sử dụng:

```java
public void authorize(@Tag("reader") @Tag("writer") String user) {
}
```

Java compiler có thể lưu hai `@Tag` thông qua container `@Tags`. Vì thế:

- `parameter.getAnnotationsByType(Tag.class)` trả **hai `Tag`**, phù hợp cho việc xử lý annotation repeatable.
- `parameter.getAnnotation(Tag.class)` **không nên** được giả định sẽ trả một trong hai `Tag` khi chúng được lưu dưới dạng container; có thể trả `null`.
- `parameter.getAnnotations()` và `getParameterAnnotations()` có thể phản ánh **container annotation** `Tags` thay vì hai `Tag` riêng lẻ. Số phần tử thô trong mảng khác với số annotation `Tag` thực tế về mặt lặp.

Ví dụ hoàn chỉnh ở mục 5 xác nhận `getAnnotationsByType(Tag.class).length == 2` trong khi số annotation thô trên cùng constructor parameter là `1`. Đừng dùng `getAnnotations().length` để khẳng định “chỉ có một tag” mà không hiểu cấu trúc repeatable annotation.

## 8. Tên tham số và annotation là hai loại metadata độc lập

Hai API thường xuất hiện cạnh nhau:

```java
Parameter parameter = method.getParameters()[0];
String name = parameter.getName();
Annotation[] annotations = parameter.getAnnotations();
```

Đây là đoạn minh họa trong code có `Method method` tương ứng và import `Parameter`, `Annotation`. Nhưng có khác biệt quan trọng:

- Tên tham số thông thường được compiler giữ lại cho Reflection nhờ tùy chọn **`javac -parameters`**, để `Parameter.isNamePresent()` trả `true` và `getName()` phản ánh tên đã biên dịch.
- Annotation muốn có mặt trong core Reflection cần **`@Retention(RUNTIME)`** và đúng target; `-parameters` không làm cho annotation retention `CLASS` xuất hiện ở runtime.
- Một framework dùng annotation như `@Named("userId")` có thể hoạt động dù method parameter ở runtime chỉ có tên `arg0`, vì nó đọc phần tử annotation `value()`, không phụ thuộc vào `getName()`.
- Ngược lại, code sử dụng tên tham số để bind mà không kiểm tra `isNamePresent()` có thể xử lý sai nếu class được build không có `-parameters`.

Khuyến nghị: khi cần bind theo tên, ưu tiên annotation tường minh nếu thiết kế API yêu cầu tính ổn định trước thay đổi compiler flags; nếu dùng tên thực của parameter, hãy quy định và kiểm tra rõ `-parameters` trong quy trình build.

## 9. Method, Constructor và các tham số do compiler tạo

Thông thường, mảng `Parameter[]` và annotation matrix được căn theo thứ tự của parameter types trong executable:

```java
Executable executable = /* Method hoặc Constructor được truyền vào */;
Parameter[] parameters = executable.getParameters();
Annotation[][] annotations = executable.getParameterAnnotations();

for (int i = 0; i < parameters.length; i++) {
    System.out.println(i + ": " + parameters[i].getType().getTypeName()
            + ", annotations=" + annotations[i].length);
}
```

Đây là đoạn minh họa, không phải một chương trình độc lập. Nếu không có metadata parameter names, việc duyệt theo **index** vẫn hoạt động.

Tuy nhiên, một số constructor đặc biệt có **parameter bổ sung ở bytecode**:

- Constructor của một **non-static inner class** có thể nhận tham chiếu tới instance của enclosing class.
- Constructor của **enum** có tham số nội bộ do compiler/JVM sử dụng, ngoài những tham số hiển thị trong khai báo source.
- Compiler có thể sinh tham số **synthetic** hoặc đánh dấu tham số **implicit/mandated**, tùy construct và compiler.

Vì vậy, khi viết framework tổng quát, không nên suy rằng parameter thứ 0 trong mọi constructor luôn tương ứng với tham số đầu tiên do lập trình viên trực tiếp viết. Có thể kiểm tra `Parameter.isSynthetic()`, `Parameter.isImplicit()` và metadata compiler khi cần; đồng thời đối chiếu `Executable.getParameterTypes()`, `getParameters()` và annotation array theo **cùng executable**.

Trong trường hợp constructor do compiler sinh đặc biệt, metadata về annotation/parameter đôi khi cần xử lý thận trọng theo bytecode thực tế và phiên bản compiler. Đừng dùng tên `arg0` hoặc số parameter giả định thay cho việc kiểm tra reflection metadata.

### Overriding không tự kế thừa annotation của tham số

Nếu interface hoặc superclass khai báo một method với parameter annotation, method override ở subclass **không tự động nhận** annotation của parameter cũ. `@Inherited` của Java chỉ áp dụng cho một số **class-level annotations**, không truyền parameter annotations qua override.

Ví dụ:

```java
interface Handler {
    void handle(@Label("command") String command);
}

class HandlerImpl implements Handler {
    @Override
    public void handle(String command) {
    }
}
```

Đây là đoạn minh họa với `Label` runtime annotation thích hợp. Nếu tra cứu parameter annotations trên `HandlerImpl.handle(String)`, không được mặc định nhìn thấy `@Label("command")` của `Handler.handle(String)`. Một framework muốn kế thừa quy ước annotation từ interface phải **tự xác định method tương ứng và đọc metadata từ interface**.

Đây là điểm khác biệt quan trọng giữa “method cùng chữ ký ở nhiều cấp type” và “annotation thực sự gắn vào parameter của Method cụ thể được trả về từ Reflection”.

## 10. Tra cứu đúng method/constructor trước khi đọc annotation

Trước khi lấy annotations, cần chọn đúng executable:

```java
Method m = Service.class.getMethod(
        "send", String.class, String.class, String.class);

Constructor<Service> c =
        Service.class.getConstructor(String.class, int.class);
```

Đây là hai lệnh minh họa trùng với class ví dụ ở mục 5. `getMethod(name, parameterTypes)` tra cứu public method theo tên + **chữ ký kiểu tham số**; `getConstructor(parameterTypes)` tra cứu constructor public. Nếu cần metadata của private executable, dùng `getDeclaredMethod` hoặc `getDeclaredConstructor`.

Lưu ý:

1. Annotation **không tham gia phân biệt overload**. Hai method không thể được overload chỉ vì annotation trên parameter khác nhau; điều quan trọng là tên method và dãy parameter types theo Java.
2. `int.class` và `Integer.class` là **hai kiểu chữ ký khác nhau**. Khi lookup, Java Reflection không tự chọn overload theo boxing của argument runtime.
3. Varargs parameter `String... names` xuất hiện trong reflection parameter types như kiểu **`String[]`**.
4. Generic parameter như `List<String>` thường có erased runtime type `List.class`; muốn metadata generic dùng `getGenericParameterTypes()` hoặc `Parameter.getParameterizedType()`.
5. Tìm được metadata executable không có nghĩa có thể **invoke** nó. Access checks, module encapsulation và tham số truyền vào vẫn có thể ảnh hưởng việc gọi. Đọc annotation thông thường không yêu cầu thực thi executable.

## 11. Ứng dụng thực tế: annotation-driven validation và binding

Giả sử một framework muốn đọc annotation `@Label` để biết tên trường JSON hoặc tên HTTP parameter:

```java
Method endpoint = Controller.class.getMethod(
        "handle", String.class, int.class);
Parameter[] params = endpoint.getParameters();

for (int i = 0; i < params.length; i++) {
    Label label = params[i].getAnnotation(Label.class);
    if (label == null) {
        continue;
    }
    String externalName = label.value();
    Class<?> targetType = params[i].getType();

    // Ánh xạ externalName -> dữ liệu đầu vào -> chuyển đổi kiểu targetType,
    // sau đó kiểm tra điều kiện nghiệp vụ trước khi Method.invoke.
}
```

Đây là **đoạn minh họa thiết kế**, giả định có `Controller` với public method phù hợp và annotation `Label`. Không phải chương trình chạy độc lập. Framework thực tế cần bổ sung xử lý kiểu, field missing/null, số lượng argument, security, validate và exception.

Quy trình hợp lý:

1. Tra cứu chính xác `Method` hoặc `Constructor`.
2. Lấy `Parameter[]` theo thứ tự executable.
3. Với mỗi parameter, lấy annotation có `RUNTIME` retention mà framework hiểu, hoặc type-use annotations khi đúng mục tiêu.
4. Đọc thành phần của annotation (`value()`, `required()`, ... nếu annotation đó định nghĩa) để xây dựng quy tắc bind/validate.
5. Chuyển dữ liệu ngoài thành kiểu Java phù hợp với `getType()`/`getParameterizedType()`.
6. Kiểm tra lỗi và quyền truy cập; cuối cùng mới gọi `Method.invoke` hoặc `Constructor.newInstance` nếu cần.

**Không có bước nào Java tự thực thi annotation giúp bạn.** Ví dụ `@Positive` do người học tự định nghĩa không tự ngăn một số âm được truyền vào method. Với Bean Validation như Jakarta Validation, các thư viện chuyên dụng cung cấp engine và quy tắc; core Reflection chỉ là một phần cơ chế khám phá metadata.

## 12. Lỗi thường gặp và cách khắc phục

| Triệu chứng | Nguyên nhân khả dĩ | Kiểm tra hoặc hướng giải quyết |
|---|---|---|
| `parameter.getAnnotation(MyAnno.class)` là `null` | Annotation không có `RUNTIME` retention | Kiểm tra khai báo `@Retention(RUNTIME)`; build lại class |
| `getParameterAnnotations()[i]` rỗng nhưng source có annotation | Annotation là `TYPE_USE`, `CLASS`/`SOURCE` hoặc không đúng parameter được đọc | Dùng `getAnnotatedParameterTypes()` nếu là `TYPE_USE`; xem retention |
| `getAnnotations().length` bằng 1 dù source ghi hai annotation cùng loại | Repeatable annotations được lưu qua container | Dùng `getAnnotationsByType(Tag.class)` |
| `Parameter.getName()` là `arg0` | Không có metadata tên param runtime | Biên dịch với `javac -parameters`, kiểm tra `isNamePresent()` |
| `getMethod(...)` ném `NoSuchMethodException` | Tên, số lượng hoặc kiểu param lookup không đúng; method không public | Kiểm tra đúng overload và visibility; cân nhắc `getDeclaredMethod` |
| Annotation có ở interface nhưng không thấy trên override class | Parameter annotation không tự kế thừa qua override | Framework cần đọc annotation từ method ở interface/supertype |
| Duyệt annotations theo tên param rồi bị lệch | Tên param không đáng tin nếu không có `-parameters` hoặc có compiler-generated params | Ưu tiên chỉ số kết hợp `getParameters()` từ cùng executable |
| Code dùng `getAnnotation(...).value()` gây `NullPointerException` | Không có annotation runtime đúng loại | Kiểm tra khác `null` hoặc dùng `isAnnotationPresent` |
| Annotation được đọc nhưng không có validation nào chạy | Reflection chỉ đọc metadata; không cung cấp validation engine | Viết logic xử lý hoặc tích hợp framework chuyên dụng |
| Lỗi khi đọc phần tử của annotation runtime bất thường | Annotation type hoặc class metadata thay đổi, type không hiện diện, giá trị không phù hợp | Kiểm tra runtime classpath/module và compatibility; một số API có thể ném lỗi như `TypeNotPresentException`, `AnnotationTypeMismatchException` hoặc `IncompleteAnnotationException` |

## 13. Câu hỏi tự kiểm tra

**Câu 1.** Method có ba tham số nhưng chỉ tham số thứ hai gắn `@Label` runtime. `getParameterAnnotations()` có bao nhiêu phần tử chiều ngoài?

**Đáp án:** Ba phần tử. Parameter thứ nhất và thứ ba tương ứng mảng annotation rỗng; phần tử thứ hai chứa annotation.

**Câu 2.** Khai báo `@Target(PARAMETER)` nhưng không ghi `@Retention`. Reflection có chắc đọc được annotation này không?

**Đáp án:** Không. Retention mặc định là `CLASS`; core Reflection không hiển thị nó như runtime annotation. Muốn đọc bằng `Parameter.getAnnotation` cần `RetentionPolicy.RUNTIME`.

**Câu 3.** Tại sao `javac -parameters` không làm `@CompileOnly` trong ví dụ hiển thị?

**Đáp án:** `-parameters` lưu metadata **tên tham số**. `@CompileOnly` có retention `CLASS`, nên không được trả về trong runtime annotation APIs; đây là hai cơ chế khác nhau.

**Câu 4.** `@NonNullType` có target `TYPE_USE` được gắn trước `String body`. API nào phù hợp để đọc?

**Đáp án:** `method.getAnnotatedParameterTypes()[index]`, sau đó `AnnotatedType.getAnnotation(NonNullType.class)` hoặc `isAnnotationPresent`. Không mặc định nó là parameter-declaration annotation.

**Câu 5.** Một parameter có `@Tag("a") @Tag("b")`. Để lấy tất cả tag nên dùng gì?

**Đáp án:** `parameter.getAnnotationsByType(Tag.class)`, vì `Tag` là repeatable; đừng dùng `getAnnotation(Tag.class)` để mong lấy ngẫu nhiên một tag.

**Câu 6.** Interface khai báo parameter `@Label("id")`, còn class implement override method mà không ghi lại annotation. Class implement có tự nhận annotation đó khi gọi Reflection không?

**Đáp án:** Không. Parameter annotations không tự kế thừa khi method được override. Framework phải tìm method của interface và đọc metadata ở đó nếu muốn áp dụng quy tắc từ interface.

**Câu 7.** Constructor có thể tra cứu annotation parameter giống method không?

**Đáp án:** Có. `Method` và `Constructor` đều kế thừa `Executable`, nên có `getParameters()`, `getParameterAnnotations()`, `getAnnotatedParameterTypes()`; cần lưu ý implicit/synthetic parameters trong một số constructor đặc biệt.

## Tài liệu chuẩn để tra cứu

- Java SE API: `java.lang.reflect.Executable` — `getParameters`, `getParameterCount`, `getParameterAnnotations`, `getAnnotatedParameterTypes`.
- Java SE API: `java.lang.reflect.Parameter` — `getName`, `isNamePresent`, `getType`, `getParameterizedType`, `getAnnotationsByType`, `isSynthetic`, `isImplicit`.
- Java SE API: `java.lang.reflect.AnnotatedType`, `AnnotatedParameterizedType`.
- Java SE API: `java.lang.annotation.Retention`, `RetentionPolicy`, `Target`, `ElementType`, `Repeatable`.
- Java Language Specification: phần về annotation types, type annotations, method/constructor parameters; Java Virtual Machine Specification: runtime-visible parameter/type annotations.
- Công cụ `javac`: tùy chọn `-parameters` để lưu tên tham số được Reflection sử dụng.

**Ghi nhớ:** Annotation trên từng tham số được đọc qua **`Executable.getParameterAnnotations()`** hoặc **`Parameter.getAnnotation(s)`**, căn theo đúng parameter index. Chỉ annotation có **`RUNTIME` retention** mới hiện qua core Reflection; annotation `TYPE_USE` được đọc qua **`AnnotatedType`**; annotation lặp nên lấy bằng **`getAnnotationsByType`**. Tên tham số có cần `-parameters` hay không là vấn đề khác với việc giữ annotation, và việc có annotation **không tự chạy validation**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executable-parameters-009">getAnnotatedParameterTypes và getAnnotatedReceiverType</a>

<details>
<summary>Xem chi tiết</summary>

# getAnnotatedParameterTypes và getAnnotatedReceiverType trong Java Reflection

## 1. Hai phương thức trên `Executable` đọc những gì?

`Method` và `Constructor` đều kế thừa `java.lang.reflect.Executable`. Ngoài các API trả kiểu thô hoặc kiểu generic, `Executable` có hai API đọc **annotation tại vị trí sử dụng kiểu** (*type-use annotations*):

~~~java
AnnotatedType[] types = executable.getAnnotatedParameterTypes();
AnnotatedType receiver = executable.getAnnotatedReceiverType();
~~~

Đây là mã minh họa khi đã có biến `executable` kiểu `Executable`; cần import các lớp từ `java.lang.reflect`.

- **`getAnnotatedParameterTypes()`** trả mảng `AnnotatedType` theo thứ tự các kiểu tham số hình thức của executable. Nó phản ánh annotation đặt trực tiếp lên kiểu hoặc nằm sâu trong cấu trúc generic/mảng.
- **`getAnnotatedReceiverType()`** trả `AnnotatedType` của kiểu receiver (`this` đối với instance method, enclosing instance với constructor của non-static inner member class), hoặc `null` khi executable không thể có receiver parameter.
- Đây là **metadata**, không phải thao tác gọi phương thức, tạo instance, cấp quyền truy cập hay kiểm tra dữ liệu tự động.

Để đọc được annotation lúc chạy, annotation phải có `@Retention(RetentionPolicy.RUNTIME)`. Annotation với retention `SOURCE` hoặc `CLASS` không xuất hiện trong truy vấn runtime tương ứng.

## 2. Declaration annotation và type-use annotation khác nhau

Java cho phép annotation trên **khai báo tham số** (`ElementType.PARAMETER`) và trên **chính vị trí sử dụng một kiểu** (`ElementType.TYPE_USE`).

~~~java
import java.lang.annotation.*;
import java.util.List;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@interface InputTag {}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface TypeTag {
    String value();
}

class Sample {
    void accept(@InputTag @TypeTag("list")
                List<@TypeTag("element") String> names) {}
}
~~~

Ví dụ trên là các khai báo có thể đặt cùng một file Java; chúng chưa có hàm `main` độc lập.

- `@InputTag` gắn vào **khai báo tham số `names`**. Đọc bằng `Parameter.getAnnotation(InputTag.class)` hoặc `Executable.getParameterAnnotations()`.
- `@TypeTag("list")` gắn lên **kiểu `List` ở vị trí tham số**; đọc qua `getAnnotatedParameterTypes()[0].getAnnotation(TypeTag.class)`.
- `@TypeTag("element")` gắn lên **kiểu đối số `String` trong `List<String>`**; phải đi xuống cây `AnnotatedParameterizedType`.
- `getParameterTypes()[0]` trả raw class `List.class`, còn `getGenericParameterTypes()[0]` cho kiểu generic `List<String>`. Không API nào trong hai API đó thay thế `getAnnotatedParameterTypes()` khi muốn xem type-use annotations.

Annotation đặt tên `@NonNull`, `@Readonly` hoặc tên tương tự **không tự biến Reflection thành trình kiểm tra runtime**. Compiler plugin, static analyzer hoặc framework riêng phải hiểu và thực thi quy tắc đó.

## 3. Cấu trúc dữ liệu `AnnotatedType`

`getAnnotatedParameterTypes()` trả một phần tử cho mỗi tham số của executable. Mỗi phần tử là `AnnotatedType` và cung cấp `getType()`, `getAnnotations()`, `getAnnotation(...)`.

| API/kiểu | Thông tin phản ánh |
| --- | --- |
| `Class<?>[] getParameterTypes()` | Kiểu runtime/raw class của tham số |
| `Type[] getGenericParameterTypes()` | Kiểu generic, ví dụ `List<String>`, `T`, wildcard |
| `AnnotatedType[] getAnnotatedParameterTypes()` | Type-use annotations trên kiểu tham số và cấu trúc lồng |
| `Parameter[] getParameters()` | Metadata từng tham số, gồm declaration annotations và tên nếu có |
| `Annotation[][] getParameterAnnotations()` | Declaration annotations trên các tham số |
| `AnnotatedType getAnnotatedReceiverType()` | Receiver type và annotation của receiver |
| `AnnotatedType getAnnotatedReturnType()` | Type-use annotations ở kiểu trả về |
| `AnnotatedType[] getAnnotatedExceptionTypes()` | Type-use annotations trên kiểu trong `throws` |

Các subtype dùng khi đi sâu:

- `AnnotatedParameterizedType` cho `List<@Tag String>`: `getAnnotatedActualTypeArguments()`.
- `AnnotatedArrayType` cho kiểu mảng: `getAnnotatedGenericComponentType()`.
- `AnnotatedWildcardType` cho wildcard: `getAnnotatedUpperBounds()`, `getAnnotatedLowerBounds()`.
- `AnnotatedTypeVariable` cho `T`: `getAnnotatedBounds()`.

Luôn dùng `instanceof` trước khi ép kiểu. Ví dụ, `int` có thể có type-use annotation nhưng không phải `AnnotatedParameterizedType`. `AnnotatedType.getType()` trả `Type`, có thể là `ParameterizedType` hoặc `TypeVariable`, không phải lúc nào cũng ép được thành `Class<?>`.

### Annotation bên trong generic không tự trồi lên ngoài

~~~java
AnnotatedType outer = method.getAnnotatedParameterTypes()[0];

if (outer instanceof AnnotatedParameterizedType p) {
    AnnotatedType inner = p.getAnnotatedActualTypeArguments()[0];
    TypeTag tag = inner.getAnnotation(TypeTag.class);
    if (tag != null) {
        System.out.println(tag.value());
    }
}
~~~

Đây là đoạn trích dùng `method` và `TypeTag` trong ngữ cảnh ví dụ đầy đủ bên dưới. Annotation trên `String` bên trong `List<String>` nằm ở **nút con của cây kiểu**, không xuất hiện chung trong `outer.getAnnotations()`.

Với mảng, `String @TypeTag("array") [] values` đặt annotation trên **mức mảng**. Vị trí annotation trước kiểu thành phần và trước ngoặc `[]` có ý nghĩa khác nhau; khi cần phân tích chuẩn xác phải duyệt `AnnotatedArrayType`.

## 4. Receiver parameter là gì?

Instance method chạy trên một đối tượng `this`. Java cho phép khai báo tường minh **receiver parameter** để chú thích kiểu của `this`:

~~~java
class Service {
    void process(@TypeTag("receiver") Service this,
                 @TypeTag("value") String value) {
        System.out.println(value);
    }
}
~~~

Đây là đoạn minh họa khi annotation `TypeTag` đã được định nghĩa với `@Target(TYPE_USE)`.

`Service this`:

- Phải xuất hiện trước các tham số hình thức còn lại.
- **Không phải tham số gọi method bổ sung**: phương thức `process` chỉ có **một** formal parameter là `String value`.
- Không thay đổi `getParameterCount()`, `getParameterTypes()` hay chữ ký tra cứu `getDeclaredMethod("process", String.class)`.
- Không tạo cơ chế gán lại `this`, truyền receiver mới qua danh sách `args`, hoặc thay đổi overload/dispatch.
- Cung cấp vị trí viết type-use annotation để `getAnnotatedReceiverType()` có thể đọc.

### Instance method không viết receiver tường minh

**Điểm rất quan trọng:** một instance method thông thường, dù **không có `Service this` trong source**, vẫn có receiver type theo API `Executable`. `getAnnotatedReceiverType()` của nó trả **một `AnnotatedType` khác `null`**, thường không mang annotation nào.

Do đó, phải phân biệt:

- `receiver == null`: executable **không thể có** receiver parameter, như static method.
- `receiver != null` và `receiver.getAnnotations().length == 0`: có receiver type nhưng **không có type-use annotation** được lưu trên receiver.
- `receiver.getAnnotation(TypeTag.class) != null`: receiver có annotation runtime được truy vấn.

Không thể suy “không viết receiver tường minh” chỉ từ việc kết quả không có annotation.

## 5. Receiver của method và constructor khác nhau

| Loại executable | Kết quả `getAnnotatedReceiverType()` |
| --- | --- |
| Instance method với receiver annotation | `AnnotatedType` mang annotation trên kiểu `this` |
| Instance method không có receiver annotation | `AnnotatedType` không `null`, thường rỗng annotation |
| Static method | `null` |
| Constructor top-level class | `null` |
| Constructor static nested/member class | `null` |
| Constructor local class hoặc anonymous class | `null` theo hợp đồng Javadoc |
| Constructor **non-static inner member class** | `AnnotatedType` đại diện enclosing instance receiver |

Với inner class, có cú pháp `Outer Outer.this` trong danh sách constructor để đặt annotation lên **kiểu của enclosing instance receiver**:

~~~java
class Outer {
    class Inner {
        Inner(@TypeTag("owner") Outer Outer.this,
              @TypeTag("text") String text) {}
    }
}
~~~

Đây là mã minh họa có `TypeTag` ở phạm vi truy cập phù hợp. Inner class non-static cần một **enclosing instance** để khởi tạo. Compiler thường đưa một tham số ẩn kiểu `Outer` vào **chữ ký constructor ở bytecode**. Vì vậy khi Reflection tra constructor, có thể cần `Outer.class` trước tham số `String.class`.

**Không đồng nhất hai việc:** receiver parameter tường minh `Outer Outer.this` là nơi để **chú thích kiểu**, không tự tạo một tham số gọi mới; sự hiện diện của outer-instance argument ở constructor là kết quả của mô hình inner class.

## 6. Ví dụ Java 17+ hoàn chỉnh

Tạo file **`AnnotatedExecutableDemo.java`**:

~~~java
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

public class AnnotatedExecutableDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    public @interface TypeTag {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface InputTag {}

    public static class Service {
        public Service() {}

        public void process(@TypeTag("receiver") Service this,
                            @InputTag @TypeTag("list")
                            List<@TypeTag("element") String> values,
                            @TypeTag("count") int count) {}

        public void plain(String value) {}

        public static void staticAction(
                @TypeTag("static") String value) {}
    }

    public static class Outer {
        public class Inner {
            public Inner(@TypeTag("owner") Outer Outer.this,
                         @TypeTag("label") String label) {}
        }
    }

    public static void main(String[] args) throws Exception {
        Method method = Service.class.getDeclaredMethod(
                "process", List.class, int.class);

        AnnotatedType[] parameters =
                method.getAnnotatedParameterTypes();

        System.out.println("parameters=" + parameters.length);
        System.out.println("raw0="
                + method.getParameterTypes()[0].getTypeName());
        System.out.println("declaration0="
                + method.getParameters()[0].isAnnotationPresent(
                        InputTag.class));
        System.out.println("type0="
                + parameters[0].getAnnotation(TypeTag.class).value());

        AnnotatedParameterizedType list =
                (AnnotatedParameterizedType) parameters[0];
        System.out.println("element="
                + list.getAnnotatedActualTypeArguments()[0]
                      .getAnnotation(TypeTag.class).value());

        System.out.println("type1="
                + parameters[1].getAnnotation(TypeTag.class).value());
        System.out.println("receiver="
                + method.getAnnotatedReceiverType()
                        .getAnnotation(TypeTag.class).value());

        Method plain = Service.class.getDeclaredMethod(
                "plain", String.class);
        System.out.println("plainReceiverPresent="
                + (plain.getAnnotatedReceiverType() != null));
        System.out.println("plainReceiverTags="
                + plain.getAnnotatedReceiverType().getAnnotations().length);

        Method staticMethod = Service.class.getDeclaredMethod(
                "staticAction", String.class);
        System.out.println("staticReceiverNull="
                + (staticMethod.getAnnotatedReceiverType() == null));

        Constructor<Service> ctor = Service.class.getConstructor();
        System.out.println("staticNestedCtorReceiverNull="
                + (ctor.getAnnotatedReceiverType() == null));

        Constructor<?> inner = Outer.Inner.class.getConstructor(
                Outer.class, String.class);
        System.out.println("innerReceiver="
                + inner.getAnnotatedReceiverType()
                       .getAnnotation(TypeTag.class).value());
        System.out.println("innerParameters="
                + inner.getParameterCount());
        System.out.println("innerParam0="
                + inner.getParameterTypes()[0].getSimpleName());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 AnnotatedExecutableDemo.java
java AnnotatedExecutableDemo
~~~

Stdout:

~~~text
parameters=2
raw0=java.util.List
declaration0=true
type0=list
element=element
type1=count
receiver=receiver
plainReceiverPresent=true
plainReceiverTags=0
staticReceiverNull=true
staticNestedCtorReceiverNull=true
innerReceiver=owner
innerParameters=2
innerParam0=Outer
~~~

**Giải thích từng nhóm kết quả:**

1. `parameters=2` khẳng định receiver `Service this` không được tính là formal parameter của `process`. Hai kiểu dùng để tra method là `List.class` và `int.class`.
2. `raw0=java.util.List` đến từ `getParameterTypes()`; `declaration0=true` là kết quả `@InputTag` trên khai báo tham số.
3. `type0=list` là annotation trên `List` ở vị trí dùng kiểu. `element=element` là annotation trên kiểu `String` lồng sâu trong generic; đọc qua `AnnotatedParameterizedType`.
4. `type1=count` cho thấy ngay cả primitive `int` cũng có thể mang type-use annotation.
5. `receiver=receiver` là annotation trên receiver parameter khai báo tường minh.
6. `plainReceiverPresent=true` cùng `plainReceiverTags=0`: instance method không viết receiver tường minh vẫn có `AnnotatedType`, nhưng không có annotation.
7. `staticReceiverNull=true` và `staticNestedCtorReceiverNull=true`: static method và constructor của static nested class không có receiver type theo API.
8. `innerReceiver=owner` cho thấy annotation trên enclosing-instance receiver của inner constructor.
9. `innerParameters=2`, `innerParam0=Outer` cho thấy tham số `Outer` do compiler đưa vào constructor bytecode, rồi tới tham số `String` khai báo trong source.

Ví dụ dùng API có mặt ở Java 17. Mã đã được kiểm tra thực tế bằng `javac --release 17` và chạy trên JDK 24 với đúng stdout phía trên.

## 7. Không nhầm receiver metadata với đối tượng truyền cho `Method.invoke`

Khi dùng Reflection để gọi instance method, chữ ký:

~~~java
Object result = method.invoke(receiver, args...);
~~~

nhận **đối tượng đích thực tế** ở `receiver`, còn `args` là danh sách các đối số ứng với formal parameters của method.

Nếu `process(@TypeTag Service this, List<String> values, int count)` thì một lời gọi có thể là:

~~~java
Method method = Service.class.getDeclaredMethod(
        "process", List.class, int.class);

Service service = new Service();
List<String> values = List.of("a", "b");
method.invoke(service, values, 5);
~~~

Các đoạn này minh họa trong ngữ cảnh có định nghĩa `Service` và xử lý checked exceptions; chúng không cần truyền thêm đối số nào cho receiver parameter `Service this`. `service` là giá trị đích runtime của `invoke`, còn `getAnnotatedReceiverType()` chỉ nói về **kiểu receiver được annotate**, không trả về đối tượng `service`.

Receiver annotation cũng **không thay đổi** quyền truy cập, không khiến `Method.invoke` tự xác thực dữ liệu theo annotation và không thay đổi lựa chọn overload.

## 8. Cách dùng trong framework đọc schema/annotation

Một thư viện tự phân tích executable có thể theo quy trình:

1. Lấy đúng `Method` hoặc `Constructor` cần phân tích.
2. Duyệt `getAnnotatedParameterTypes()` theo thứ tự, đọc annotation trên từng kiểu và đi xuống generic/mảng/wildcard nếu cần.
3. Nếu muốn annotation khai báo trên tham số, dùng `getParameters()` hoặc `getParameterAnnotations()` **riêng**.
4. Gọi `getAnnotatedReceiverType()` rồi **kiểm tra `null` trước khi lấy annotation**.
5. Với constructor của non-static inner member class, kiểm tra `getParameterTypes()` vì có thể xuất hiện outer-instance argument do compiler thêm.
6. Nếu cần annotation trên kiểu trả về hoặc `throws`, dùng `getAnnotatedReturnType()` / `getAnnotatedExceptionTypes()`.
7. Áp dụng các quy tắc ứng dụng (validation, schema, DI) ở tầng xử lý tiếp theo. Reflection chỉ trả metadata.

### Những lỗi thường gặp

- **Coi `null` và `AnnotatedType` rỗng annotation là một:** static method thường trả `null`, instance method thông thường trả `AnnotatedType` không `null`.
- **Đếm `this` như một formal parameter:** receiver parameter tường minh không làm tăng `getParameterCount()` của instance method.
- **Dùng `getParameterAnnotations()` để đọc `List<@Tag String>`:** phải dùng `getAnnotatedParameterTypes()` rồi duyệt `AnnotatedParameterizedType`.
- **Không kiểm tra subtype:** ép mọi `AnnotatedType` thành `AnnotatedParameterizedType` sẽ sai với primitive, mảng hay kiểu thường.
- **Đọc kiểu lồng bằng mỗi `getAnnotations()` ở ngoài:** annotation trên type argument nằm ở node con.
- **Quên retention:** runtime Reflection không đọc được annotation đã bị bỏ ở giai đoạn biên dịch.
- **Nhầm `getAnnotatedReceiverType()` với API quyền truy cập:** nó không liên quan đến `canAccess` hay `trySetAccessible`.
- **Nghĩ annotation tự ràng buộc dữ liệu:** framework hoặc trình phân tích phải định nghĩa ý nghĩa và thực thi kiểm tra.
- **Đoán constructor inner class từ source:** outer-instance argument có thể nằm trong chữ ký runtime; luôn kiểm tra parameter metadata.

## 9. Tự kiểm tra

**Câu 1.** Một instance method khai báo `void f(@Tag X this, String value)` có bao nhiêu formal parameters?  
**Một** (`String value`). Receiver parameter không nằm trong `getAnnotatedParameterTypes()`.

**Câu 2.** Instance method không khai báo receiver tường minh có `getAnnotatedReceiverType() == null` không?  
**Thông thường không.** Nó trả `AnnotatedType` đại diện receiver, với annotation rỗng nếu không có type-use annotation.

**Câu 3.** Static method có receiver type không?  
**Không**, `getAnnotatedReceiverType()` trả `null`.

**Câu 4.** Muốn lấy annotation trên `String` bên trong `List<@Tag String>`, đọc ở đâu?  
**`getAnnotatedParameterTypes()` → `AnnotatedParameterizedType.getAnnotatedActualTypeArguments()`** rồi tra annotation của phần tử.

**Câu 5.** `getParameterAnnotations()` trả cùng thông tin với `getAnnotatedParameterTypes()`?  
**Không.** Cái đầu đọc declaration annotations, cái sau đọc type-use annotations.

**Câu 6.** Constructor non-static inner class có thể trả receiver type?  
**Có**; receiver đại diện enclosing instance của lớp bao, dù constructor source có thể không ghi tường minh receiver annotation.

**Ghi nhớ:** `getAnnotatedParameterTypes()` trả annotation trên **kiểu của các tham số**, còn `getAnnotatedReceiverType()` trả annotation trên **kiểu receiver**. Receiver không phải một tham số gọi method bổ sung, và kết quả `null` phải được phân biệt với receiver type không có annotation.

### Tài liệu chính thức

- [JDK 17 — `java.lang.reflect.Executable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Executable.html)
- [JDK 17 — `java.lang.reflect.AnnotatedType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedType.html)
- [JDK 17 — `AnnotatedParameterizedType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedParameterizedType.html)
- [JDK 17 — `Parameter`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Parameter.html)
- [JLS 17 §8.4.1 — Formal Parameters, Receiver Parameters](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.1)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="executable-parameters-010">Không dùng tên tham số như hợp đồng ổn định khi build không bảo đảm metadata</a>

<details>
<summary>Xem chi tiết</summary>

# Không dùng tên tham số Reflection như hợp đồng ổn định khi build không bảo đảm metadata

## 1. Vấn đề: tên trong mã Java chưa chắc tồn tại ở runtime

Giả sử một phương thức được viết trong source code:

```java
public void send(String recipient, int retryCount) {
    // Xử lý gửi dữ liệu.
}
```

Hai tên `recipient` và `retryCount` giúp người viết code hiểu ý nghĩa đối số. Nhưng sau khi biên dịch thành `.class`, **Java Reflection không luôn lấy lại được hai tên này**. Điều đó phụ thuộc class file thực sự chứa metadata về tên formal parameter hay không.

Nếu một framework tự suy luận khóa JSON, query parameter, tên biến CLI hoặc tên trường cấu hình từ `Parameter.getName()`, chương trình có thể đột ngột xuất hiện những khóa như `arg0`, `arg1` khi dùng artifact được biên dịch với tùy chọn khác.

**Nguyên tắc cốt lõi:** *Không dùng tên tham số Java được đọc bằng Reflection làm hợp đồng API ổn định nếu quy trình build không bảo đảm metadata; ngay cả khi metadata hiện diện, sự hiện diện của tên chưa tự biến tên source thành một hợp đồng được phiên bản hóa.*

Bài học này thuộc chủ đề **tham số executable**: các đối tượng `Method` và `Constructor` đều kế thừa `java.lang.reflect.Executable`; chúng cung cấp `getParameters()`, trả về các đối tượng `java.lang.reflect.Parameter` để đọc tên, kiểu và thuộc tính từng formal parameter.

## 2. Tách metadata có tên và hợp đồng bên ngoài

`Parameter.getName()` luôn cho một chuỗi, kể cả tên tổng hợp `arg0`, `arg1` khi thiếu tên trong `MethodParameters`. `Parameter.isNamePresent()` mới xác định class file có cung cấp tên cho **tham số này** hay không.

`getType()` và `getParameterizedType()` đọc mô tả kiểu bằng các metadata khác; chữ ký lookup của JVM không dựa vào tên formal parameter. Kể cả khi `isNamePresent()` là `true`, một lần đổi `recipient` thành `target` có thể giữ nguyên chữ ký Java/JVM mà lại đổi tên do framework suy ra cho request.

Vì vậy có hai kiểm tra độc lập: **có metadata tên trong artifact không?** và **tên ấy có được công bố, quản trị và kiểm thử như một hợp đồng bên ngoài không?**

## 3. Vì sao hai bản build có thể có hợp đồng khác nhau?

Với `javac`, tùy chọn biên dịch `-parameters` ghi formal parameter names vào `MethodParameters`; tùy chọn `-g` phục vụ debug information và không thay thế cờ này cho Core Reflection. CI, JAR dependency, obfuscator và bytecode transformer có thể tạo artifact giữ metadata khác bản chạy trong IDE.

Kiểm tra `javap -v -p` trên **chính class file hoặc JAR được phát hành**, rồi kiểm tra `isNamePresent()` trong test. Metadata về generic types và runtime annotations có cơ chế độc lập; không nên lấy sự có mặt của chúng làm bằng chứng rằng tên tham số cũng được giữ.

## 4. Thí nghiệm Java 17: tên source thay đổi, tên external vẫn ổn định

Bài toán khác với việc chỉ in `argN`: ta thử một annotation xác định tên contract độc lập với tên biến Java. Tạo file `StableContractDemo.java`:

```java
import java.lang.annotation.*;
import java.lang.reflect.*;

public class StableContractDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface ExternalName {
        String value();
    }

    static class Endpoint {
        public void send(@ExternalName("recipient") String target,
                         @ExternalName("retryCount") int attempts) {}
    }

    public static void main(String[] args) throws Exception {
        Method method = Endpoint.class.getDeclaredMethod(
                "send", String.class, int.class);
        for (Parameter parameter : method.getParameters()) {
            ExternalName contract = parameter.getAnnotation(ExternalName.class);
            System.out.println("name=" + parameter.getName()
                    + ", present=" + parameter.isNamePresent()
                    + ", external=" + contract.value());
        }
    }
}
```

Biên dịch và chạy hai bản tách biệt:

```bash
javac --release 17 -d plain StableContractDemo.java
javac --release 17 -parameters -d named StableContractDemo.java
java -cp plain StableContractDemo
java -cp named StableContractDemo
```

Bản `plain` in:

```text
name=arg0, present=false, external=recipient
name=arg1, present=false, external=retryCount
```

Bản `named` in:

```text
name=target, present=true, external=recipient
name=attempts, present=true, external=retryCount
```

Hai bản build khác nhau ở tên phản chiếu, nhưng `external` không đổi vì annotation có `@Retention(RUNTIME)` và chứa tên công bố trực tiếp. Tên `target`/`attempts` có thể được refactor mà contract `recipient`/`retryCount` vẫn giữ nguyên khi annotation không đổi. Đây là cách biểu diễn thiết kế: annotation tùy chỉnh **không tự thực hiện binding**, framework phải chủ động đọc và sử dụng tên đã khai báo.

## 5. Vì sao đây là rủi ro với hợp đồng API?

Một **hợp đồng ổn định** là những gì người gọi bên ngoài có quyền trông đợi qua các bản phát hành, chẳng hạn tên query parameter `recipient`, khóa JSON `retryCount`, tên command-line option, field trong configuration hoặc tên trường được framework bind từ request.

Tên biến trong Java source không tự động là một hợp đồng như vậy:

- **Môi trường build khác nhau:** máy phát triển, CI, module phụ hoặc project consumer có thể cấu hình `javac` khác nhau. Có bản class có `MethodParameters`, bản khác không.
- **Dependency bên ngoài:** bạn không kiểm soát tùy chọn compile của thư viện bên thứ ba; `isNamePresent()` có thể trả `false`.
- **Công cụ sau biên dịch:** obfuscator, bytecode transformer, minifier hoặc quá trình đóng gói có thể đổi, loại bỏ hoặc tái tạo metadata.
- **Refactor hợp lệ về mặt Java:** đổi tên formal parameter từ `recipient` thành `destination` thường **không đổi JVM method descriptor**; lời gọi Java dựa trên vị trí và kiểu vẫn hợp lệ, nhưng một framework tự rút khóa API từ `getName()` có thể đổi tên khóa từ bên ngoài.
- **Tên tổng hợp trông hợp lệ:** `arg0`, `arg1` là chuỗi không rỗng, dễ bị framework dùng nhầm làm khóa thật nếu không kiểm tra `isNamePresent()`.
- **Thông tin vị trí khác ý nghĩa:** chỉ số tham số là một phần của chữ ký thực thi, nhưng không nói tự thân tham số dùng cho nghiệp vụ gì. Đổi thứ tự/số tham số cũng thay đổi hợp đồng mà adapter của bạn cần quản lý.

Ví dụ framework tự động đặt khóa map:

```java
for (Parameter parameter : method.getParameters()) {
    String externalKey = parameter.getName();
    // Rủi ro: externalKey có thể là arg0/arg1.
}
```

Đây là mảnh mã minh họa, giả định đã có `Method method` và import `Parameter`. Trong build thiếu metadata, API vốn muốn công bố `recipient` và `retryCount` có thể đột ngột công bố `arg0` và `arg1` — vi phạm hợp đồng bên ngoài mà không cần bất kỳ thay đổi logic Java nào.

**Ngay cả khi `isNamePresent() == true`, tên đó vẫn có thể bị đổi giữa hai bản class**. Chỉ nên coi nó là giao diện bên ngoài khi dự án **đã công bố** sự ổn định của tên, build được kiểm soát và kiểm thử tương thích có mặt. Metadata chỉ là điều kiện cần để quan sát đúng tên, không phải bằng chứng tự động về tính bất biến nghiệp vụ.

## 6. Chiến lược thiết kế an toàn hơn

### A. Khai báo tên giao tiếp bên ngoài một cách tường minh

Với endpoint, message consumer, CLI, serializer hoặc dependency injection, nên xác định những khóa bên ngoài bằng cấu hình/schema hoặc annotation **có giá trị rõ ràng**, thay vì ngầm rút từ tên biến Java.

Ví dụ giả định một framework cung cấp annotation lưu tên bên ngoài:

```java
void send(
        @RequestKey("recipient") String target,
        @RequestKey("retryCount") int attempts
) {
    // Hai tên external không phụ thuộc tên target/attempts trong source.
}
```

Đây là **mã minh họa, không độc lập**, `@RequestKey` là annotation ví dụ và phải được định nghĩa hoặc thay bằng annotation thật của framework đang dùng. Điểm quan trọng là tên bên ngoài `recipient` và `retryCount` đã được khai báo **trực tiếp**, không còn bị suy ra từ `target` hay `attempts`.

Nếu tự xây framework, annotation phải có retention phù hợp để runtime đọc được, chẳng hạn `@Retention(RetentionPolicy.RUNTIME)` trên annotation parameter. Annotation không có runtime retention sẽ không trở thành metadata runtime hợp lệ chỉ vì đã có `-parameters`.

Những lựa chọn tương đương:

- Schema JSON/OpenAPI/protobuf hoặc contract file định nghĩa khóa ở tầng giao tiếp.
- Một mapping tường minh giữa **vị trí/kiểu formal parameter** và tên external được duy trì/test.
- Một annotation runtime với tên external cố định, có versioning và validation.
- Các interface/DTO có property public được quản trị bằng hợp đồng phiên bản hóa thay vì phụ thuộc vào tên local variable.

### B. Nếu ứng dụng thực sự muốn suy tên, kiểm tra và thất bại rõ ràng

Có những thư viện chủ động xem tên tham số Java là một tiện ích — ví dụ framework nội bộ cần tự bind đối số theo tên. Khi đó hãy coi metadata hiện diện là **precondition bắt buộc** và kiểm tra nó lúc khởi động, không chấp nhận tên tổng hợp trong im lặng:

```java
static String[] requireParameterNames(Executable executable) {
    Parameter[] parameters = executable.getParameters();
    String[] names = new String[parameters.length];

    for (int i = 0; i < parameters.length; i++) {
        if (!parameters[i].isNamePresent()) {
            throw new IllegalStateException(
                    "Missing parameter name metadata for "
                    + executable + " parameter #" + i
                    + ". Compile with -parameters."
            );
        }
        names[i] = parameters[i].getName();
    }
    return names;
}
```

Đoạn trên là **phương thức minh họa**, giả định đã import `Executable` và `Parameter`; đặt vào class Java thích hợp để sử dụng. Nó chỉ giải quyết **thiếu metadata**, chứ **không bảo đảm** rằng tên được hiện diện sẽ ổn định qua thay đổi source/build. Cần thêm kiểm thử và quy ước versioning nếu tên trở thành API.

Việc kiểm tra có tên phải thực hiện **từng `Parameter`**, không chỉ nhìn một phần tử rồi giả định tất cả có dữ liệu đúng. Với executable đặc biệt hoặc class files từ các công cụ khác nhau, cần đặc biệt cẩn thận về nguồn gốc từng parameter.

### C. Khi chỉ cần thao tác phương thức, đừng hỏi tên tham số

Nếu mục tiêu là tìm và gọi method bằng Reflection, hãy dùng:

```java
Method method = type.getDeclaredMethod(
        "send", String.class, int.class
);
method.invoke(receiver, "alice", 3);
```

Đây là mã minh họa, giả định `type`, `receiver` tương thích và đã import `Method`; caller phải xử lý các ngoại lệ Reflection. **Tra cứu và gọi dựa trên thứ tự cùng kiểu tham số**, không cần tên `recipient`/`retryCount` có được lưu hay không.

Với công cụ cần biết kiểu generic, có `getGenericParameterTypes()` và `Parameter.getParameterizedType()`; với chú thích tham số có `getParameterAnnotations()` hoặc `Parameter.getAnnotation()`. Những metadata đó có hợp đồng riêng và không nên bị nhập làm một với tên.

## 7. Quy tắc build và kiểm thử contract

Nếu hệ thống chủ động sử dụng tên formal parameter làm API, hãy bật `-parameters` trên **mọi module định nghĩa executable cần đọc tên**, kiểm tra artifact sau tối ưu/đóng gói và kiểm thử công khai hóa tên. Việc bật cờ trong ứng dụng gọi không thêm metadata vào JAR của dependency đã biên dịch sẵn.

Một kiểm thử tốt cần hai loại assertion: `isNamePresent()` cho tham số mà framework dựa vào và **tên external được mong đợi** của endpoint/schema qua các phiên bản. Chỉ kiểm tra metadata present chưa phát hiện được thay đổi contract do refactor tên source. Nếu tên external nằm trong annotation/schema, test phải so giá trị annotation/schema đó và behavior binding thật.

## 8. Cẩn trọng với executable đặc biệt

Constructor của non-static inner class hoặc enum, cùng các executable compiler-generated, có thể chứa tham số implicit/synthetic ngoài source. Khi xây binder tổng quát, kiểm tra `getParameters()`, `getParameterTypes()`, `isImplicit()` và `isSynthetic()` theo artifact thay vì giả định index 0 luôn là tham số nghiệp vụ. Những cờ này giúp mô tả nguồn gốc tham số; chúng **không bảo đảm** tên được công bố ổn định.

## 9. Lỗi thiết kế thường gặp

| Lỗi | Hậu quả | Cách khắc phục |
| --- | --- | --- |
| `getName()` dùng ngay làm HTTP/JSON key | Thiếu metadata có thể xuất hiện `argN` | Annotation/schema nêu tên hoặc fail fast khi thiếu tên |
| `isNamePresent=true` được xem là bảo đảm ổn định | Rename source vẫn đổi contract | Kiểm thử tên API public và quản lý thay đổi phiên bản |
| Bật `-parameters` chỉ ở module consumer | Dependency đã build không được bổ sung metadata | Cấu hình compiler ở module khai báo executable |
| Fallback âm thầm sang `argN` | Lỗi binding khó phát hiện | Báo lỗi cấu hình có method và parameter index |
| Tên annotation được đọc nhưng binder không sử dụng | API vẫn bind theo tên Java | Viết/test code mapping dựa trên giá trị annotation |

## 10. Câu hỏi tự kiểm tra

1. Một tham số source tên `recipient` nhưng class file thiếu `MethodParameters`. `getName()` có nhất thiết trả `recipient` không? **Không.** Có thể là `arg0`; `isNamePresent()` là `false`.
2. Nếu `getName()` trả `arg0`, có nên kết luận chắc chắn metadata bị thiếu chỉ dựa vào chuỗi không? **Không.** Dùng `isNamePresent()`, vì không kiểm tra thông qua hình dạng tên.
3. Bật tùy chọn nào khi dùng `javac` để lưu formal parameter names? **`-parameters`**.
4. `javac -g` có phải bảo đảm `Parameter.isNamePresent() == true` không? **Không.** Debug information và `MethodParameters` là các khái niệm khác.
5. Vì sao framework tạo API key từ `getName()` có nguy cơ? **Thiếu/đổi metadata, obfuscation hoặc refactor** có thể đổi key.
6. Nếu `isNamePresent()` là `true`, tên đã trở thành public API ổn định chưa? **Chưa.** Phải quản trị tên và tương thích ở mức thiết kế/version.
7. `Executable.getParameters()` sử dụng cho cả method và constructor không? **Có.** Cả `Method` và `Constructor` đều kế thừa `Executable`.
8. `getDeclaredMethod("send", String.class, int.class)` có cần lưu tên `recipient` và `retryCount` không? **Không.** Tra cứu dùng tên method và formal parameter types.

## 11. Tài liệu chính thức

- [Oracle Java SE 21 — `java.lang.reflect.Parameter`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Parameter.html): hợp đồng `getName()`, `isNamePresent()`, `isImplicit()`, `isSynthetic()`.
- [Oracle Java SE 21 — `java.lang.reflect.Executable`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Executable.html): truy vấn `getParameters()`, số tham số, kiểu và annotation của method/constructor.
- [Oracle Java SE 21 — công cụ `javac`](https://docs.oracle.com/en/java/javase/21/docs/specs/man/javac.html): ý nghĩa tùy chọn `-parameters` và các tùy chọn debug.
- [The Java Virtual Machine Specification SE 21 — §4.7.24 `MethodParameters`](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.7.24): cấu trúc class file dành cho metadata formal parameters.
- [Dev.java — Invoking Methods](https://dev.java/learn/reflection/methods/): ví dụ và lưu ý chính thức về Reflection, tên formal parameter và compiler option.

**Ghi nhớ:** `getName()` có thể trả `arg0`/`arg1` nếu thiếu metadata; `isNamePresent()` xác nhận **có tên trong class file**, không xác nhận **tên ấy là hợp đồng ổn định**. Muốn công bố tên ra bên ngoài, hãy dùng schema/annotation tường minh hoặc kiểm soát build `-parameters` cộng với kiểm thử và chính sách tương thích.

</details>

- [Quay lại đầu trang](#back-to-top)
