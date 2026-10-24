<a id="back-to-top"></a>

# Constructor

## Menu
- [Constructor&lt;T&gt; và constructor không được kế thừa](#constructors-001)
- [Tra cứu constructor public hoặc declared](#constructors-002)
- [newInstance với danh sách đối số đúng kiểu](#constructors-003)
- [Constructor mặc định khác constructor không tham số được khai báo tường minh](#constructors-004)
- [Constructor private và điều kiện truy cập](#constructors-005)
- [Không khởi tạo abstract class hoặc interface bằng constructor thông thường](#constructors-006)
- [Constructor của non-static inner class và tham số đối tượng bao ngoài](#constructors-007)
- [Tham số ngầm định hoặc synthetic của constructor](#constructors-008)
- [Constructor ném ngoại lệ và lớp bọc InvocationTargetException](#constructors-009)
- [Vì sao không dùng Class.newInstance đã lỗi thời](#constructors-010)
- [Khởi tạo lớp và lỗi xảy ra trước hoặc trong constructor](#constructors-011)

## <a id="constructors-001">Constructor&lt;T&gt; và constructor không được kế thừa</a>

<details>
<summary>Xem chi tiết</summary>

# Constructor<T> và constructor không được kế thừa trong Java Reflection

Trong Java Reflection, **java.lang.reflect.Constructor<T>** biểu diễn một constructor được khai báo bởi một class, cho phép kiểm tra tham số, mức truy cập, annotation và tạo instance qua **newInstance(...)**. Ký hiệu `<T>` ở `Constructor<T>` biểu thị **kiểu đối tượng mà constructor tạo ra**, thường gắn với `Class<T>` đang được tra cứu.

Điểm dễ nhầm là **constructor của superclass không được subclass kế thừa**. Khi tạo subclass, constructor superclass vẫn **được gọi** trong chuỗi khởi tạo (`super(...)`), nhưng không trở thành một constructor thuộc subclass. Reflection phản ánh quy tắc này: `Child.class.getConstructors()` không liệt kê constructor của `Parent`; `Child.class.getConstructor(...)` cũng không tìm constructor chỉ được khai báo trên `Parent`.

Bài học phân tích cả hai phần: cách hiểu `Constructor<T>` cùng generic constructor, và cách tra cứu/khởi tạo đúng theo quan hệ superclass–subclass.

## 1. Constructor<T> đại diện cho điều gì?

Các kiểu liên quan trong Java 17:

~~~java
Class<StringBuilder> type = StringBuilder.class;
Constructor<StringBuilder> constructor = type.getConstructor();
StringBuilder value = constructor.newInstance();
~~~

Đây là mảnh code minh họa đặt trong method có `throws ReflectiveOperationException` (hoặc xử lý checked exceptions); cần `import java.lang.reflect.Constructor;`.

- `StringBuilder.class` có kiểu `Class<StringBuilder>`.
- `getConstructor()` trả `Constructor<StringBuilder>` vì constructor được yêu cầu từ `Class<StringBuilder>`.
- `constructor.newInstance()` trả đối tượng kiểu `StringBuilder`.
- Đối số kiểu `T` trong `Constructor<T>` **không phải là kiểu từng tham số constructor** và **không phải kiểu tham số generic riêng của constructor**.
- `Constructor<T>` extends `Executable`; có API như `getParameterTypes()`, `getGenericParameterTypes()`, `getTypeParameters()`, `getModifiers()`, `getExceptionTypes()`, `isVarArgs()`, `getDeclaringClass()`.

Ví dụ `Constructor<Child>` biểu diễn constructor của **Child**; nó không mô tả một constructor kế thừa từ Parent và không thể dùng để tạo loại đối tượng khác tùy ý.

| Biểu thức | Ý nghĩa |
| --- | --- |
| `Class<T>` | Đối tượng mô tả một runtime class; `T` là kiểu class trong hệ thống generic Java |
| `Constructor<T>` | Constructor của class mang kiểu `T`, dùng để tạo `T` qua `newInstance` |
| `Constructor<?>` | Có constructor nhưng chưa biết chính xác class ở compile time; `newInstance` chỉ suy ra Object |
| `Constructor.getTypeParameters()` | Khai báo type variables **riêng của constructor** như `<U>`, không phải `T` của `Constructor<T>` |
| `Class.getTypeParameters()` | Type variables khai báo bởi class, ví dụ `E` trong `Box<E>` |

Trong generic class `Box<E>`, biểu thức `Box.class` đại diện runtime class raw `Box`; không tồn tại `Box<String>.class` tách biệt với `Box<Integer>.class`. Các argument `String` và `Integer` của instance thường bị **type erasure**.

## 2. Constructor không được kế thừa: phân biệt “gọi” và “sở hữu”

Theo **Java Language Specification §8.2 và §8.8**, constructor **không phải class member** theo nghĩa được kế thừa; constructor không inherited, không hidden và không overridden.

~~~java
class Parent {
    protected Parent(int code) {}
}

class Child extends Parent {
    public Child(String name) {
        super(7);
    }
}
~~~

- `Child(String)` là constructor được **Child khai báo**.
- `Parent(int)` thuộc **Parent**, không phải overload `Child(int)`.
- `super(7)` là **constructor invocation** dùng để khởi tạo phần trạng thái superclass trong cùng đối tượng Child; nó không biến `Parent(int)` thành constructor của Child.
- Việc `Parent(int)` là `protected` cho phép gọi qua `super` từ subclass phù hợp, nhưng không đồng nghĩa nó là `public Child(int)`.
- Constructor không có kiểu trả về, không tham gia overriding/polymorphic dispatch như instance method.

Một đối tượng Child thường được khởi tạo theo chuỗi constructor của các superclass trước khi chạy phần thân constructor Child. Nhưng **kết quả của `Parent.class.getDeclaredConstructor(int.class)` luôn mô tả constructor của Parent**.

### Vì sao không tự xuất hiện no-arg constructor?

Java chỉ cung cấp một **default constructor** khi class **không khai báo constructor nào**. Nếu class đã tự khai báo `Child(String)`, compiler **không sinh thêm `Child()`**. Khi subclass không khai báo constructor, default constructor của subclass sẽ cố gọi `super()` theo quy tắc ngôn ngữ; nếu superclass không có constructor không tham số khả dụng, việc biên dịch thất bại.

Ví dụ cố ý không biên dịch được:

~~~java
class Base {
    Base(int id) {}
}
class MissingConstructor extends Base {
    // Không có constructor được khai báo.
    // Default constructor sẽ cố gọi super(), nhưng Base() không tồn tại.
}
~~~

Cách sửa là khai báo constructor của subclass và gọi super bằng tham số tương ứng:

~~~java
class Base {
    Base(int id) {}
}
class ValidChild extends Base {
    ValidChild() {
        super(10);
    }
}
~~~

Đoạn trên chỉ minh họa quan hệ constructor trong một compilation unit; không phải chương trình độc lập có main. Việc superclass có public/protected/package-private constructor không làm subclass kế thừa chúng.

## 3. API Reflection để tra cứu constructor

Các phương thức trên `Class<T>` khác nhau ở mức truy cập, **không có nhánh tìm constructor kế thừa**:

| API trên Class<T> | Kết quả | Có tìm ở superclass không? |
| --- | --- | --- |
| `getConstructor(Class<?>... parameterTypes)` | `Constructor<T>`: constructor **public** của chính class, khớp danh sách tham số | **Không** |
| `getConstructors()` | `Constructor<?>[]`: tất cả constructor public của chính class | **Không** |
| `getDeclaredConstructor(Class<?>... parameterTypes)` | `Constructor<T>`: constructor của chính class, gồm private/protected/package-private/public | **Không** |
| `getDeclaredConstructors()` | `Constructor<?>[]`: tất cả constructor được khai báo trong class, mọi mức truy cập | **Không** |

Đối với `Child` ở mục trước:

~~~java
Child.class.getConstructor(String.class);       // đúng: public Child(String)
Child.class.getDeclaredConstructor(String.class); // đúng
Child.class.getConstructor(int.class);          // NoSuchMethodException
Parent.class.getDeclaredConstructor(int.class); // đúng: Parent(int)
~~~

Trong API này `parameterTypes` được so khớp theo **thứ tự, số lượng và chính xác runtime parameter Class<?>**. Java Reflection không áp dụng cơ chế overload resolution của javac để tự tìm constructor “gần đúng”; ví dụ `int.class` khác `Integer.class`, và `List<String>` bị xóa còn `List.class`. Khi không khớp, `getConstructor/getDeclaredConstructor` ném `NoSuchMethodException`.

Khi danh sách rỗng, `getConstructor()` tìm một constructor **public không tham số**. Không thể giả định class nào cũng có constructor này. `getDeclaredConstructor()` có thể tìm constructor không tham số private nếu được khai báo/thực sự tồn tại; có thể cần quyền để gọi.

### Đọc thông tin của Constructor

~~~java
Constructor<?> c = Child.class.getDeclaredConstructor(String.class);
System.out.println(c.getDeclaringClass()); // class Child
System.out.println(c.getParameterCount()); // 1
System.out.println(c.isVarArgs());         // false
~~~

Đây là đoạn trích sử dụng `Child` từ mục 2, sau khi import `java.lang.reflect.Constructor`; các comment nêu ý nghĩa, output khi chạy trong default package có thể mang tên class khác tùy nesting. `getDeclaringClass()` là cách xác nhận constructor **thuộc class nào**; kết quả không tự đổi thành subclass khi bạn dùng constructor của Parent.

## 4. Ví dụ Java 17+ chạy độc lập

Ví dụ sau minh họa chuỗi `super(...)`, lookup thất bại vì constructor không kế thừa, và khác biệt giữa type variable của generic class với generic constructor.

Lưu nguyên văn vào **ConstructorInheritanceDemo.java**:

~~~java
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

public class ConstructorInheritanceDemo {
    public static class Parent {
        public Parent() { this(0); }

        protected Parent(int id) {
            System.out.println("Parent(" + id + ")");
        }
    }

    public static class Child extends Parent {
        public Child(String name) {
            super(7);
            System.out.println("Child(" + name + ")");
        }
    }

    public static class Box<E> {
        private final E value;

        public <U> Box(E value, U marker) {
            this.value = value;
        }

        public E value() { return value; }
    }

    public static void main(String[] args) throws Exception {
        Constructor<Child> childCtor = Child.class.getConstructor(String.class);
        Child child = childCtor.newInstance("Ada");
        System.out.println("child type=" + child.getClass().getSimpleName());
        System.out.println("child public constructors=" + Child.class.getConstructors().length);

        try {
            Child.class.getConstructor(int.class);
        } catch (NoSuchMethodException e) {
            System.out.println("Child(int)=not found");
        }

        Constructor<Parent> parentCtor = Parent.class.getDeclaredConstructor(int.class);
        System.out.println("parent int protected="
                + Modifier.isProtected(parentCtor.getModifiers()));
        System.out.println("parent declaring=" + parentCtor.getDeclaringClass().getSimpleName());

        Constructor<Box> boxCtor = Box.class.getConstructor(Object.class, Object.class);
        Box<?> box = boxCtor.newInstance("payload", 123);
        System.out.println("box value=" + box.value());
        Type[] generic = boxCtor.getGenericParameterTypes();
        System.out.println("box erased=" + boxCtor.getParameterTypes()[0].getSimpleName()
                + "," + boxCtor.getParameterTypes()[1].getSimpleName());
        System.out.println("box generic=" + generic[0].getTypeName()
                + "," + generic[1].getTypeName());
        System.out.println("constructor type parameter="
                + boxCtor.getTypeParameters()[0].getName());
        System.out.println("class type parameter=" + Box.class.getTypeParameters()[0].getName());
        System.out.println("box declaring=" + boxCtor.getDeclaringClass().getSimpleName());
    }
}
~~~

Từ thư mục có file, biên dịch bằng Java 17 và chạy:

~~~bash
javac --release 17 -encoding UTF-8 ConstructorInheritanceDemo.java
java ConstructorInheritanceDemo
~~~

Stdout (đã thực thi với `javac --release 17` trên JDK 21):

~~~text
Parent(7)
Child(Ada)
child type=Child
child public constructors=1
Child(int)=not found
parent int protected=true
parent declaring=Parent
box value=payload
box erased=Object,Object
box generic=E,U
constructor type parameter=U
class type parameter=E
box declaring=Box
~~~

Cách diễn giải:

1. `childCtor.newInstance("Ada")` tạo **Child**, chạy `Parent(int)` do `super(7)`, sau đó chạy phần thân constructor Child. Điều đó **không phải thừa kế constructor**.
2. `Child.class.getConstructors().length` là 1 vì Child chỉ khai báo một constructor public `Child(String)`; public `Parent()` không được liệt kê cho Child.
3. `Child.class.getConstructor(int.class)` thất bại vì Child không sở hữu `Child(int)`, dù Parent có `Parent(int)`.
4. `Parent.class.getDeclaredConstructor(int.class)` tìm được constructor protected do Parent khai báo. `getDeclaringClass()` trả Parent.
5. `Box<E>` có constructor generic `<U> Box(E value, U marker)`. Sau erasure, hai kiểu tham số runtime đều là `Object`, nên lookup nhận `Object.class, Object.class`.
6. `getGenericParameterTypes()` phản ánh chữ ký khai báo `E,U`; `getTypeParameters()` **trên Constructor** chỉ trả `U`, trong khi `Box.class.getTypeParameters()` trả `E`. `Constructor<Box>` biểu diễn kiểu class được tạo (raw Box trong ví dụ), **không biểu diễn U**.

Ví dụ dùng `Constructor<Box>` raw để truy vấn trực tiếp `Box.class`; trong code thư viện generic nghiêm ngặt, hãy quản lý type safety riêng và tránh giả định Reflection biết `Box<String>` từ runtime `Box.class`. Việc `newInstance("payload", 123)` chấp nhận String/Integer không làm thay đổi erased parameter types.

## 5. Generic class và generic constructor: ba kiểu khác nhau

Xét:

~~~java
class Packet<E> {
    public <U extends Number> Packet(E payload, U version) {}
}
~~~

Có ba khái niệm:

- `E` thuộc về **Packet<E>**, được truy vấn bởi `Packet.class.getTypeParameters()`.
- `U` thuộc về **chính constructor**, được truy vấn bởi `Constructor.getTypeParameters()`; bound của U là Number.
- `T` trong API `Constructor<T>` là **class được tạo** (chẳng hạn Packet), không liên quan đến tên biến kiểu E/U ở mã khai báo.

Với `Packet.class.getConstructor(Object.class, Number.class)`, lookup dùng **erasure**: E thành Object vì E không có bound, U thành Number do có bound. Truy vấn `getGenericParameterTypes()` đọc được ký hiệu E, U nếu metadata được bảo toàn trong class file. Lưu ý `Class<Packet>` và `Constructor<Packet>` dùng **raw Packet** vì Java không có literal `Packet<String>.class`.

Nếu cần mô tả một type parameterized của class tại runtime, ta phải nắm giữ `Type`/`ParameterizedType` từ trường, phương thức, superclass, hoặc token kiểu thích hợp. Chỉ `Constructor<T>` không thể tái tạo type argument của mọi instance mà constructor tạo.

### Từ chữ ký generic sang lookup thực tế

| Khai báo constructor | `getDeclaredConstructor(...)` nhận |
| --- | --- |
| `Box(E value, U tag)` với E, U không bound | `Object.class, Object.class` |
| `Packet(E value, U num)`, U extends Number | `Object.class, Number.class` |
| `Child(String name)` | `String.class` |
| `Sample(int count, List<String> values)` | `int.class, List.class` |
| `Logger(String... messages)` | `String[].class` |

Đây là ví dụ về quy tắc tìm constructor; các class `Packet`, `Sample`, `Logger` là tên mô tả tình huống tương ứng chứ không phải một chương trình đã định nghĩa đầy đủ.

Không dùng wildcard hoặc `ParameterizedType` làm tham số cho `getDeclaredConstructor`: API này nhận `Class<?>...`, phù hợp kiểu sau erasure.

## 6. Gọi constructor và phân biệt các nhóm lỗi

Sau khi tìm được `Constructor<T>`, `newInstance(Object... initargs)` tạo object của declaring class; Reflection không truyền receiver như `Method.invoke`. Việc tìm thấy metadata chưa đủ để bảo đảm quyền truy cập hoặc đối số hợp lệ.

| Bước | Lỗi điển hình | Ý nghĩa |
| --- | --- | --- |
| Lookup | `NoSuchMethodException` | Sai danh sách kiểu runtime hoặc không có constructor phù hợp |
| Truy cập | `IllegalAccessException`, `InaccessibleObjectException` | Caller không có quyền gọi hoặc JPMS không cho phép mở quyền |
| Khởi tạo | `InstantiationException`, `IllegalArgumentException` | Kiểu không thể tạo trực tiếp hoặc đối số không hợp lệ |
| Thực thi constructor | `InvocationTargetException` | Mã trong quá trình khởi tạo instance ném lỗi; xem `getCause()` |
| Khởi tạo class | `ExceptionInInitializerError` | Static initialization thất bại trước khi chạy constructor |

Với constructor `private`, `getDeclaredConstructor` chỉ lấy metadata. `trySetAccessible()` có thể thất bại nếu module không mở package cho deep reflection; `exports` không thay thế `opens`. `Class.newInstance()` là API đã deprecated, nên ưu tiên chọn chữ ký qua `getDeclaredConstructor(...).newInstance(...)` và xử lý lỗi theo giai đoạn.

## 7. Các trường hợp đặc biệt cần nhận biết

- **Abstract class** có constructor để subclass gọi, nhưng không thể tạo trực tiếp instance của abstract class; interface không có constructor tạo object.
- **Enum** có constructor nội bộ nhưng `Constructor.newInstance` từ chối tạo thêm enum constant; record có canonical constructor tương ứng với các component, không kế thừa từ `Record`.
- **Non-static inner class** nhận enclosing instance dưới dạng tham số constructor đầu tiên ở runtime. Ví dụ `Outer.Inner(Outer, int)` có thể tương ứng với khai báo source `Inner(int)`.
- **Varargs** được biểu diễn bằng tham số mảng (như `String[].class`); khi gọi `newInstance`, truyền nguyên mảng thành một argument bằng cast `(Object)` khi cần.
- **Compiler-generated parameters** có thể xuất hiện với enum, local/anonymous class hoặc inner class. Đọc `getParameterTypes()` và metadata `Parameter`, không suy luận từ thứ tự liệt kê constructor hay tên tham số compiler sinh.

Các bài tiếp theo phân tích từng trường hợp với ví dụ chạy được. Trong bài này, điểm chính vẫn là `Constructor<T>` gắn với class khai báo và constructor của superclass không trở thành constructor của subclass.

## 8. Quy trình áp dụng thực tế

Khi một DI container, serializer, CLI parser hoặc factory dựa Reflection cần tạo instance:

1. **Chọn class thực sự định tạo.** Nếu cần Child, tra trên `Child.class`, không lấy `Constructor<Parent>` rồi mong tạo Child.
2. **Chọn phạm vi quyền.** `getConstructor` khi cần public API; `getDeclaredConstructor` khi code có lý do hợp lệ để dùng constructor non-public.
3. **Xác định tham số runtime chính xác.** Phân biệt primitive/boxed, varargs array, kiểu đã erasure và enclosing instance của inner class.
4. **Xem danh sách constructor nếu chưa biết signature.** Dùng `getDeclaredConstructors()`, `getParameterTypes()`, `getModifiers()`, `getGenericParameterTypes()` và lọc theo tiêu chí đã xác định; không đoán ngẫu nhiên qua thứ tự.
5. **Đối chiếu module access.** Nếu private/package-private nằm trong named module khác, cân nhắc factory API hoặc `opens` đúng package/caller thay vì mở rộng toàn bộ module.
6. **Gọi `newInstance` và bắt lỗi chính xác.** `InvocationTargetException.getCause()` giúp tìm lỗi do constructor; `NoSuchMethodException` thường là lỗi chọn signature.
7. **Không nhầm chuỗi constructor với inheritance.** Java sẽ chạy superclass constructor trong quá trình tạo subclass, nhưng Reflection vẫn giữ mỗi constructor thuộc đúng declaring class.

## 9. Câu hỏi tự kiểm tra

**1.** Parent có `public Parent(int id)`, Child extends Parent và chỉ có `public Child()` gọi `super(5)`. `Child.class.getConstructor(int.class)` tìm được không?

**Đáp án:** Không. Parent(int) không được kế thừa; Child chỉ khai báo Child().

**2.** `Constructor<Child> ctor = Child.class.getConstructor()`; `ctor.newInstance()` có thể trả Parent thay vì Child không?

**Đáp án:** Không. Generic type T và declaring class của Constructor là Child. Nếu tạo được, đối tượng là Child; constructor Parent có thể chạy như một phần khởi tạo đối tượng Child.

**3.** `class Box<E> { public <U extends Number> Box(E value, U version) {} }`. Có thể tìm bằng `Box.class.getConstructor(String.class, Integer.class)` không?

**Đáp án:** Không theo chữ ký được khai báo. Erasure là `Object.class, Number.class`. `String` và `Integer` là kiểu argument của một lần gọi cụ thể, không phải parameter types trong constructor descriptor.

**4.** `Constructor.getTypeParameters()` trên constructor ở câu 3 có trả E không?

**Đáp án:** Không. Nó trả U của constructor; E là type variable thuộc Box, xem `Box.class.getTypeParameters()`.

**5.** `getDeclaredConstructor()` tìm private no-arg constructor rồi: có luôn gọi được `newInstance()` không?

**Đáp án:** Không. Tra cứu metadata và quyền gọi khác nhau; cần Java access/JMPS opens phù hợp, hoặc dùng factory công khai.

**6.** Vì sao `Outer.Inner.class.getDeclaredConstructor(int.class)` có thể thất bại dù source có `Inner(int)`?

**Đáp án:** Non-static inner class có tham số enclosing instance được thêm vào constructor runtime; cần `Outer.class, int.class`.

**Ghi nhớ:** `Constructor<T>` mang kiểu class được khởi tạo; một generic constructor còn có type variables riêng, tra cứu theo erased parameter types. Constructor của superclass **được gọi khi khởi tạo subclass nhưng không bao giờ được kế thừa**. Luôn tra cứu constructor trên class bạn thực sự muốn tạo.

### Tài liệu tham khảo

- Java Language Specification 17, §8.2 (Class Members) và §8.8 (Constructor Declarations): https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html
- Java SE 17, `Class.getConstructor/getDeclaredConstructor`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html
- Java SE 17, `Constructor<T>` và `newInstance`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Constructor.html
- Java SE 17, `Executable` (type parameters, generic parameter types): https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Executable.html
- dev.java — Invoking Constructors: https://dev.java/learn/api/reflection-method-handles/reflection/constructors/


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="constructors-002">Tra cứu constructor public hoặc declared</a>

<details>
<summary>Xem chi tiết</summary>

# Tra cứu constructor public hoặc declared trong Java Reflection

## 1. Vì sao cần tra cứu Constructor?

Trong Java, `Constructor` là thành phần dùng để tạo instance của một lớp. Java Reflection cung cấp `java.lang.reflect.Constructor<T>` để tìm, liệt kê và đọc thông tin constructor tại runtime. Nhờ vậy, thư viện dependency injection, serializer, ORM, test framework và công cụ chẩn đoán có thể chọn constructor dựa trên class nhận được lúc chạy.

Ví dụ, khi chỉ có `Class<?> type`, chương trình không thể luôn viết trực tiếp `new SomeClass(...)` vì chưa biết trước tên lớp hoặc chữ ký constructor. Reflection cho phép khám phá constructor qua `type.getConstructor(...)`, `type.getDeclaredConstructor(...)` và các biến thể lấy danh sách. Tuy nhiên, **tra cứu metadata** khác với **được phép gọi constructor**: tìm thấy constructor `private` không tự cấp quyền tạo đối tượng.

Bài học tập trung vào sự khác nhau giữa hai họ API **public** và **declared**, cách chọn đúng overload, phạm vi truy cập và những trường hợp đặc biệt dễ gây `NoSuchMethodException` hoặc `IllegalAccessException`. Ví dụ sử dụng Java 17 trở lên.

## 2. Bốn API quan trọng nhất

| Mục đích | API trong `Class<T>` | Kết quả | Phạm vi |
| --- | --- | --- | --- |
| Tìm một constructor public | `getConstructor(Class<?>... parameterTypes)` | `Constructor<T>` | Constructor `public` với chính xác danh sách kiểu tham số |
| Liệt kê constructor public | `getConstructors()` | `Constructor<?>[]` | Toàn bộ constructor `public` khai báo ở lớp |
| Tìm một constructor được khai báo trực tiếp | `getDeclaredConstructor(Class<?>... parameterTypes)` | `Constructor<T>` | Constructor đúng chữ ký, bất kể `public`, `protected`, package-private hay `private` |
| Liệt kê toàn bộ constructor được khai báo trực tiếp | `getDeclaredConstructors()` | `Constructor<?>[]` | Mọi constructor của lớp, bất kể access modifier |

Các dạng tra cứu đơn lẻ thường ném `NoSuchMethodException` nếu không tìm được constructor phù hợp; các dạng lấy danh sách trả về mảng (có thể rỗng) và **không bảo đảm thứ tự phần tử**.

**Khác biệt cốt lõi:** từ `Declared` ở đây có nghĩa *được khai báo trong class hiện tại*, không có nghĩa “constructor chỉ dành cho private” hay “tắt kiểm tra truy cập”. Chẳng hạn, `getDeclaredConstructor()` vẫn tìm được constructor `public` nếu class khai báo constructor không tham số đó.

### Constructor không được kế thừa

Khác với method hay field, **constructor không được kế thừa** từ superclass. Do đó `Child.class.getConstructors()` không bao giờ liệt kê constructor của `Parent`. Cả hai họ API luôn xét constructor của chính lớp mà đối tượng `Class` đại diện.

Nói cách khác, đối lập `getConstructor()` và `getDeclaredConstructor()` chủ yếu là **phạm vi modifier public** so với **mọi modifier**, không phải lựa chọn “kế thừa” hay “không kế thừa”.

## 3. Tra cứu đúng chữ ký: tên constructor không cần truyền vào

Không có tham số tên khi tra cứu constructor. Class đã xác định tên kiểu sở hữu; phương thức chỉ nhận **danh sách kiểu tham số theo đúng thứ tự**.

```java
Constructor<User> noArgs = User.class.getConstructor();
Constructor<User> byName =
        User.class.getDeclaredConstructor(String.class);
Constructor<User> byNameAndAge =
        User.class.getDeclaredConstructor(String.class, int.class);
```

Đây là đoạn minh họa bên trong phương thức có xử lý hoặc khai báo `NoSuchMethodException`; giả định `User` thực sự khai báo các constructor tương ứng, và đã import `java.lang.reflect.Constructor`.

Các nguyên tắc chọn chữ ký:

- `getConstructor()` hoặc `getDeclaredConstructor()` không truyền đối số yêu cầu constructor **không tham số**.
- `String.class, int.class` **khác** `int.class, String.class`. Thứ tự tham số phải trùng.
- `int.class` khác `Integer.class`, và `long.class` khác `int.class`. Reflection **không tự boxing/unboxing hoặc primitive widening trong giai đoạn tìm chữ ký**.
- Với `String... args`, signature ở runtime dùng `String[]`; tra cứu bằng `String[].class`.
- Với kiểu generic `List<String>`, metadata chữ ký đã qua **type erasure** dùng `List.class`. Không có biểu thức `List<String>.class` hợp lệ.
- Kiểu tham số là các đối tượng `Class<?>`; không truyền chuỗi `"String"` hay `"int"` vào API `getConstructor`.

Hai constructor overload này khác chữ ký:

```java
public User(String name) { /* ... */ }
public User(String name, int age) { /* ... */ }
```

Không được suy luận “Java tự chọn constructor khớp nhất” cho phương thức tra cứu: đây là phép tìm **chữ ký chính xác**, không phải phân giải overload khi compiler biên dịch lệnh `new User(...)`.

## 4. Ví dụ hoàn chỉnh Java 17: public so với declared

Ví dụ sau cố ý có bốn constructor: hai `public`, một `protected` và một `private`. Một lớp `Child` cũng được thêm vào để chứng minh constructor không được kế thừa.

Lưu **toàn bộ** vào tệp `ConstructorLookupDemo.java`:

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Collectors;

public class ConstructorLookupDemo {
    private static String signature(Constructor<?> ctor) {
        return "(" + Arrays.stream(ctor.getParameterTypes())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", ")) + ")";
    }

    private static String signatures(Constructor<?>[] ctors) {
        return Arrays.stream(ctors).map(ConstructorLookupDemo::signature)
                .sorted().collect(Collectors.joining(", ", "[", "]"));
    }

    public static void main(String[] args) throws Exception {
        Class<Account> type = Account.class;
        System.out.println("public: " + signatures(type.getConstructors()));
        System.out.println("declared: " + signatures(type.getDeclaredConstructors()));

        Constructor<Account> protectedOne =
                type.getDeclaredConstructor(String.class);
        System.out.println("declared String: "
                + Modifier.toString(protectedOne.getModifiers()));

        Constructor<Account> privateTwo =
                type.getDeclaredConstructor(String.class, int.class);
        System.out.println("private accessible: " + privateTwo.canAccess(null));
        try {
            privateTwo.newInstance("Ada", 3);
        } catch (IllegalAccessException expected) {
            System.out.println("private call: IllegalAccessException");
        }

        Constructor<Account> publicVarargs = type.getConstructor(int[].class);
        System.out.println("varargs marker: " + publicVarargs.isVarArgs());
        Account account = type.getConstructor().newInstance();
        System.out.println("created: " + account.label());

        try {
            type.getConstructor(String.class);
        } catch (NoSuchMethodException expected) {
            System.out.println("public String lookup: NoSuchMethodException");
        }

        System.out.println("child public: "
                + signatures(Child.class.getConstructors()));
    }
}

class Account {
    private final String owner;
    private final int level;

    public Account() { this("guest", 0); }
    protected Account(String owner) { this(owner, 1); }
    private Account(String owner, int level) {
        this.owner = owner;
        this.level = level;
    }
    public Account(int... levels) { this("levels", levels.length); }

    public String label() { return owner + ":" + level; }
}

class Parent {
    public Parent(int value) {}
}

class Child extends Parent {
    public Child() { super(0); }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac ConstructorLookupDemo.java
java ConstructorLookupDemo
```

Kết quả chuẩn, đã kiểm tra bằng JDK 21 với `javac --release 17`:

```text
public: [(), (int[])]
declared: [(), (String), (String, int), (int[])]
declared String: protected
private accessible: false
private call: IllegalAccessException
varargs marker: true
created: guest:0
public String lookup: NoSuchMethodException
child public: [()]
```

### 4.1. Tại sao danh sách public và declared khác nhau?

`Account.class.getConstructors()` thấy đúng hai constructor `public`: `Account()` và `Account(int... levels)`. Tại bytecode level, varargs biểu diễn bằng mảng `int[]` nên chương trình in chữ ký `(int[])`.

`Account.class.getDeclaredConstructors()` thấy **cả bốn** constructor khai báo ngay trong `Account`: hai `public`, một `protected`, một `private`. Mỗi thành viên trả về dưới dạng đối tượng `Constructor<?>`.

Để kết quả dễ đọc và có thể kiểm thử, hàm `signatures` dùng `sorted()`. **Không dùng thứ tự mảng do Reflection trả về làm thứ tự ưu tiên**, ví dụ không chọn `getDeclaredConstructors()[0]` rồi giả định đó là constructor không tham số.

### 4.2. Vì sao getDeclaredConstructor thành công nhưng không gọi được?

`getDeclaredConstructor(String.class, int.class)` tìm được constructor `private`. Tuy vậy, chương trình `ConstructorLookupDemo` là **lớp top-level độc lập** với `Account`, nên không có quyền gọi constructor `private` thông thường.

- `privateTwo.canAccess(null)` trả `false`.
- `privateTwo.newInstance("Ada", 3)` ném `IllegalAccessException`.
- Không có lời gọi `setAccessible(true)` trong chương trình.

Điểm này minh họa hai khía cạnh độc lập: **tra cứu metadata** và **quyền sử dụng metadata để tạo đối tượng**. Nếu đặt caller bên trong một nestmate hợp lệ của lớp khai báo, hoặc có cấu hình quyền truy cập khác, kết quả kiểm tra có thể khác.

### 4.3. Vì sao getConstructor(String.class) báo không có constructor?

`Account` có `protected Account(String owner)`, nên phiên bản `getDeclaredConstructor(String.class)` thành công. `getConstructor(String.class)` chỉ tìm constructor `public` và ném `NoSuchMethodException`, dù chữ ký tồn tại. Đây là lỗi lựa chọn **API tra cứu**, không phải do Java không nhận ra `String.class`.

### 4.4. Vì sao constructor của Parent không xuất hiện ở Child?

`Parent` khai báo `public Parent(int value)`; `Child` chỉ khai báo `public Child()`. `Child.class.getConstructors()` do đó chỉ có `Child()`. Mỗi constructor khởi tạo một lớp cụ thể; lệnh `super(0)` bên trong `Child()` gọi constructor cha theo cơ chế khởi tạo đối tượng thông thường, **không làm constructor cha trở thành thành viên kế thừa**.

## 5. Đọc metadata từ Constructor<?>

Sau khi tìm được đối tượng `Constructor<?>`, có thể xem các thông tin sau:

| API | Ý nghĩa |
| --- | --- |
| `getDeclaringClass()` | Lớp khai báo và sở hữu constructor |
| `getParameterCount()` | Số tham số trong chữ ký runtime |
| `getParameterTypes()` | Mảng kiểu tham số dạng `Class<?>[]` theo thứ tự |
| `getGenericParameterTypes()` | Kiểu tham số dạng `Type[]`, hỗ trợ phân tích generic signature khi có |
| `getParameters()` | Mảng `Parameter[]` để xem tên/annotation/modifier của tham số |
| `getExceptionTypes()` | Các kiểu checked/unchecked exception được khai báo trong `throws` |
| `getModifiers()` | Modifier của constructor, kết hợp `Modifier.isPublic`, `isPrivate`, `isProtected` |
| `isVarArgs()` | Constructor có khai báo biến tham số `...` hay không |
| `isSynthetic()` | Constructor có được compiler sinh dưới dạng synthetic hay không |
| `isAnnotationPresent(...)` | Kiểm tra annotation runtime trên constructor |

Ví dụ đoạn sau đặt **bên trong một phương thức**, giả định `Account` giống phần trước:

```java
Constructor<Account> ctor =
        Account.class.getDeclaredConstructor(String.class, int.class);

System.out.println(ctor.getDeclaringClass().getSimpleName()); // Account
System.out.println(ctor.getParameterCount());                 // 2
System.out.println(Modifier.isPrivate(ctor.getModifiers())); // true
```

Nếu dùng `getParameters()[i].getName()` để làm tài liệu API, cần nhớ **tên tham số thực tế không luôn được giữ trong class file**. Trong ví dụ Java thuần, thêm cờ `javac -parameters ...` nếu cần lưu tên tham số, nếu không có thể nhận tên tổng hợp như `arg0`, `arg1`. Để phân tích annotation hoặc generic type thực sự, ưu tiên API metadata tương ứng thay vì suy luận từ `toString()`.

Có thể dùng `Constructor.toGenericString()` để xem biểu diễn dễ đọc; nhưng nếu cần output bền vững giữa môi trường, hãy trích các trường metadata một cách có cấu trúc và tự định dạng.

## 6. Tra cứu metadata khác với tạo object

`getConstructor(...)` và `getDeclaredConstructor(...)` chỉ tìm chữ ký; chưa thực thi code khởi tạo. Sau lookup, `ctor.newInstance(args)` mới kiểm tra quyền, đối số và tạo instance. Chẳng hạn, từ ví dụ `Account` phía trên: `Account.class.getConstructor().newInstance().label()` trả `guest:0`.

Constructor khai báo varargs như `Account(int... levels)` có chữ ký runtime là `int[]`. Muốn truyền một mảng như một argument, dùng `getConstructor(int[].class).newInstance((Object) new int[]{1, 2, 3})`. Đối số phải khớp chữ ký đã chọn; Reflection không phân giải overload khi lookup.

## 7. Đã tìm thấy constructor nhưng không gọi được

`getDeclaredConstructor` thấy cả constructor `private`; nó không bỏ qua Java access checks. `canAccess(null)` kiểm tra trạng thái truy cập hiện tại với `Constructor`. Nếu framework cần deep reflection, `trySetAccessible()` có thể trả `false` vì JPMS: package trong named module thường phải được `opens` thích hợp. Vì vậy cần xem cả quyền truy cập của declaring class, không chỉ modifier constructor.

Ưu tiên public API/factory khi có thể. Nếu buộc mở quyền, kiểm tra kết quả và xử lý `IllegalAccessException` hoặc `InaccessibleObjectException` một cách minh bạch.

## 8. Những chữ ký khác cú pháp Java thông thường

- Không có lời gọi `getConstructor()` thành công nếu class chỉ khai báo constructor có tham số; compiler không tự sinh no-arg constructor trong trường hợp này.
- Non-static inner class có thêm tham số enclosing instance ở đầu, khác với static nested class.
- Generic `List<String>` bị erasure còn `List.class` khi lookup; varargs `String...` trở thành `String[].class`.
- Abstract class có thể có constructor nhưng không tạo được object trực tiếp; interface, array và primitive không có constructor class thông thường. Constructor enum có giới hạn tạo instance riêng.
- Constructor của superclass không xuất hiện trong danh sách constructor của subclass, dù code subclass gọi `super(...)`.

Những trường hợp đó không thay đổi quy tắc chính: `Class.get[Declared]Constructor` khớp **chính xác số lượng, thứ tự, và kiểu runtime** của từng tham số.

## 9. Chẩn đoán đúng bước thất bại

| Kết quả | Kiểm tra đầu tiên |
| --- | --- |
| `NoSuchMethodException` | Chữ ký và phạm vi public/declared của API tra cứu |
| `IllegalAccessException` | Caller, declaring class, visibility và module |
| `IllegalArgumentException` | Số lượng và kiểu đối số thực tế của `newInstance` |
| `InstantiationException` | Class có abstract không |
| `InvocationTargetException` | `getCause()` để tìm throwable từ quá trình chạy constructor |
| `ExceptionInInitializerError` | Static initialization xảy ra trước constructor |

Không `catch (Exception ignored)` rồi suy luận mọi lỗi đều là tra cứu thất bại. Việc phân biệt lookup, access, khởi tạo class và khởi tạo instance giúp sửa đúng nguyên nhân.

## 10. Chọn chiến lược tra cứu trong ứng dụng

Nếu một framework muốn tạo đối tượng từ metadata, nên định nghĩa **quy tắc chọn constructor** minh bạch:

1. Biết rõ `Class<?>` đích, phạm vi constructor được hỗ trợ và liệu có cần các constructor không public.
2. Nếu có chữ ký cụ thể, ưu tiên `getConstructor(types...)` hoặc `getDeclaredConstructor(types...)` theo chính sách quyền.
3. Nếu cần khám phá nhiều constructor, lấy danh sách với `getConstructors()` hoặc `getDeclaredConstructors()`; **lọc theo loại tham số/annotation** và sắp xếp theo tiêu chí của ứng dụng.
4. Trước khi tạo đối tượng, kiểm tra kiểu tham số, khả năng truy cập, module và ngữ nghĩa nghiệp vụ; không sử dụng constructor ngẫu nhiên vì `array[0]` xuất hiện đầu tiên.
5. Khi chạy `newInstance`, phân biệt ngoại lệ từ cơ chế Reflection với lỗi phát sinh bên trong constructor.

Ví dụ, nếu đang tìm constructor `public` nhận một chuỗi, có thể gọi:

```java
Constructor<?> selected =
        type.getConstructor(String.class);
```

Đoạn trên phù hợp khi đã biết chính xác chữ ký; không cần duyệt toàn bộ danh sách. Ngược lại, nếu framework hỗ trợ injection nhiều tham số theo annotation và có quy tắc chọn overload, cần đọc toàn bộ metadata liên quan và xác định chiến lược chọn dứt khoát, thay vì thử từng constructor cho đến khi không ném lỗi.

Với class là một loại runtime có thể được tạo động, nhớ rằng sự tồn tại của constructor phù hợp chỉ chứng minh điều kiện **cần**, không phải toàn bộ điều kiện **đủ**: constructor có thể bị giới hạn quyền, khởi tạo lớp thất bại hoặc phần thân có side effect.

## 11. Tự kiểm tra

**Câu 1.** Vì sao `Account.class.getConstructor(String.class)` không tìm được constructor dù lớp khai báo `Account(String)`?

**Đáp án:** Constructor đó là `protected`. `getConstructor` chỉ tìm constructor `public`; `getDeclaredConstructor(String.class)` tìm được vì xét mọi modifier.

**Câu 2.** Khác biệt giữa `Account.class.getConstructors()` và `Account.class.getDeclaredConstructors()` là gì?

**Đáp án:** Cách đầu liệt kê constructor `public`; cách thứ hai liệt kê toàn bộ constructor khai báo ngay trong `Account`, kể cả non-public.

**Câu 3.** Có thể lấy constructor `Account(int... values)` bằng `getConstructor(int.class)` không?

**Đáp án:** Không. Runtime signature dùng `int[]`, nên phải `getConstructor(int[].class)`.

**Câu 4.** `Child.class.getConstructors()` có chứa `Parent(int)` hay không?

**Đáp án:** Không. Constructor không kế thừa; `Child` chỉ có constructor của chính nó.

**Câu 5.** Vì sao tìm được một constructor `private` không bảo đảm `ctor.newInstance()` thành công?

**Đáp án:** Metadata lookup và kiểm tra quyền thực thi là hai việc độc lập. Caller ngoài quyền truy cập thông thường có thể bị `IllegalAccessException`, hoặc bị giới hạn bởi module khi cố bỏ qua kiểm tra.

**Câu 6.** Vì sao `getDeclaredConstructor(int.class)` có thể thất bại với `Outer.Inner` dù source viết `Inner(int)`?

**Đáp án:** Inner class không static có tham số outer-instance ngầm định trong chữ ký runtime, thường cần tìm với `(Outer.class, int.class)`.

**Câu 7.** Có thể dùng `Constructor.newInstance` để tạo enum constant mới không?

**Đáp án:** Không. Reflection không cho phép tạo enum mới qua constructor theo cách này.

**Ghi nhớ:** `getConstructor(s)` tra cứu **constructor public của chính class**, còn `getDeclaredConstructor(s)` tra cứu **mọi constructor khai báo trực tiếp trong class**. Cả hai đều dựa trên **đúng danh sách kiểu tham số**; việc có metadata chưa đồng nghĩa có quyền gọi. Với code runtime linh hoạt, hãy xem modifier, declaring class, varargs/generic signature và giới hạn của `newInstance` trước khi khởi tạo đối tượng.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="constructors-003">newInstance với danh sách đối số đúng kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# `Constructor.newInstance` với danh sách đối số đúng kiểu

## 1. Từ chữ ký constructor đến đối số khởi tạo

Trong Java Reflection, `java.lang.reflect.Constructor<T>` biểu diễn **một constructor đã được xác định**, còn `newInstance(Object... initargs)` dùng constructor đó để tạo và khởi tạo một đối tượng.

Quy trình cơ bản:

1. Xác định đúng constructor của lớp bằng **danh sách kiểu tham số được khai báo**, theo thứ tự.
2. Chuẩn bị danh sách **giá trị đối số** với số lượng và kiểu tương thích với constructor đó.
3. Gọi `constructor.newInstance(args...)`; xử lý lỗi về số đối số, kiểu, quyền truy cập và ngoại lệ phát sinh khi khởi tạo.

Ví dụ một lớp có constructor `Order(String, long)`. Để tra cứu, cần truyền `String.class` và `long.class`. Để gọi, cần truyền một giá trị kiểu `String` và một giá trị có thể chuyển đổi hợp lệ sang `long`.

~~~java
Constructor<Order> ctor =
        Order.class.getConstructor(String.class, long.class);

Order order = ctor.newInstance("Notebook", 3L);
~~~

Đoạn trên chỉ minh họa cú pháp trong ngữ cảnh có class `Order` và khai báo/xử lý ngoại lệ. **`getConstructor` nhận kiểu; `newInstance` nhận giá trị.** Không được nhầm hai danh sách này.

## 2. Chọn constructor: phải khớp đúng kiểu đã khai báo

Các phương thức tra cứu thường dùng:

| API | Phạm vi | Cách so khớp |
| --- | --- | --- |
| `Class.getConstructor(Class<?>... parameterTypes)` | Constructor `public` của lớp | Khớp chính xác danh sách kiểu tham số được khai báo |
| `Class.getDeclaredConstructor(Class<?>... parameterTypes)` | Constructor khai báo trong lớp, kể cả non-public | Cũng khớp chính xác kiểu tham số |
| `Class.getConstructors()` | Các constructor `public` | Trả metadata để duyệt, không tự lựa chọn |
| `Class.getDeclaredConstructors()` | Các constructor khai báo trong lớp | Trả metadata để duyệt, kể cả non-public |

Ví dụ với một lớp có hai constructor `Order(String, int)` và `Order(String, long)`:

- `getConstructor(String.class, int.class)` lấy constructor thứ nhất.
- `getConstructor(String.class, long.class)` lấy constructor thứ hai.
- `getConstructor(String.class, Integer.class)` **không tự tìm** `Order(String, int)`. Nếu không có constructor đúng kiểu `Integer`, nó ném `NoSuchMethodException`.

Trình biên dịch có cơ chế **overload resolution** khi xử lý biểu thức `new Order("A", 1)`. Reflection không chạy lại thuật toán ấy khi tra cứu constructor: lời gọi `getConstructor` đòi hỏi chữ ký chính xác.

Tương tự, `getConstructor()` tìm constructor **không tham số**; không tìm thấy thì ném `NoSuchMethodException`, kể cả lớp có constructor nhận tham số hoặc constructor no-arg không `public`.

Các kiểu tham số dùng khi tra cứu là kiểu đã được **erasure** ở runtime. Nếu constructor khai báo `Order(List<String> items)`, phần kiểu tham số tra cứu là `List.class`, không tồn tại `List<String>.class`.

## 3. `newInstance` kiểm tra và chuyển đổi đối số như thế nào?

Chữ ký:

~~~java
public T newInstance(Object... initargs)
        throws InstantiationException,
               IllegalAccessException,
               IllegalArgumentException,
               InvocationTargetException
~~~

Đây là chữ ký API để giải thích, không phải đoạn khai báo phương thức cần chép vào lớp ứng dụng.

Sau khi đã có đúng `Constructor<T>`, `newInstance` yêu cầu các đối số thực tế **tương thích theo từng vị trí** với tham số hình thức của constructor. Runtime có thể tự **unboxing** wrapper và thực hiện một số **widening conversions** thích hợp; nó không tự co hẹp kiểu nguyên thủy (*narrowing*) và không chạy thuật toán chọn overload lần nữa.

| Tham số khai báo | Đối số truyền cho `newInstance` | Kết quả |
| --- | --- | --- |
| `long` | `Long.valueOf(3)` | Hợp lệ: unboxing `Long → long` |
| `long` | `Integer.valueOf(3)` | Hợp lệ: unboxing `Integer → int`, rồi widening `int → long` |
| `int` | `Long.valueOf(3)` | Không hợp lệ: phải narrowing `long → int` |
| `Number` | `Integer.valueOf(3)` | Hợp lệ: tham chiếu `Integer` là một `Number` |
| `Integer` | `Long.valueOf(3)` | Không hợp lệ: wrapper khác loại, không tự đổi sang `Integer` |
| `String` | `null` | Hợp lệ: tham chiếu có thể nhận `null` |
| `long` | `null` | Không hợp lệ: không unbox được `null` |
| `long` | `"3"` | Không hợp lệ: Reflection không parse chuỗi thành số |

Các kiểu nguyên thủy truyền trong mã gọi Java sẽ được **boxing vào Object** bởi cú pháp varargs `newInstance(Object...)`. Việc nhận wrapper ấy rồi chuyển sang tham số nguyên thủy là bước riêng của Reflection.

Lỗi do số lượng đối số sai, vị trí sai, kiểu không tương thích hoặc phép narrowing không được cho phép thường gây `IllegalArgumentException`, **không phải** `NoSuchMethodException`. `NoSuchMethodException` thuộc giai đoạn *tra cứu constructor*, trước lúc gọi `newInstance`.

### Kiểu tĩnh của biến và giá trị runtime

Nếu bạn có `Object quantity = Integer.valueOf(3)`, khi gọi `ctor.newInstance("Notebook", quantity)` thì Reflection nhìn giá trị runtime là `Integer`. Nếu constructor đã chọn nhận `long`, nó có thể unbox và widening. Tuy nhiên `getConstructor(String.class, Object.class)` không thể dùng để tìm constructor `(String, long)`, bởi tra cứu cần đúng kiểu khai báo.

Không nên lấy `arg.getClass()` rồi coi đó luôn là danh sách kiểu constructor cần tìm. Cách này thất bại với `null`, primitive (đã boxing) và overload nhận superclass hoặc interface.

## 4. Ví dụ Java 17+ chạy được

Tạo file **`ConstructorArgumentsDemo.java`**:

~~~java
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

public class ConstructorArgumentsDemo {
    public static class Order {
        private final String summary;

        public Order(String name, long quantity) {
            if (quantity < 0) {
                throw new IllegalArgumentException("quantity must be >= 0");
            }
            summary = name + " x" + quantity;
        }

        public Order(String name, String... labels) {
            summary = name + " " + Arrays.toString(labels);
        }

        @Override
        public String toString() {
            return summary;
        }
    }

    public static void main(String[] args) throws Exception {
        Constructor<Order> numeric =
                Order.class.getConstructor(String.class, long.class);

        System.out.println("exact lookup: "
                + Arrays.toString(numeric.getParameterTypes()));
        System.out.println("widening: "
                + numeric.newInstance("Notebook", Integer.valueOf(3)));

        try {
            Order.class.getConstructor(String.class, Integer.class);
        } catch (NoSuchMethodException e) {
            System.out.println("lookup(Integer): NoSuchMethodException");
        }

        try {
            numeric.newInstance("Notebook", "3");
        } catch (IllegalArgumentException e) {
            System.out.println(
                    "invoke(String quantity): IllegalArgumentException");
        }

        try {
            numeric.newInstance("Notebook", (Object) null);
        } catch (IllegalArgumentException e) {
            System.out.println(
                    "invoke(null primitive): IllegalArgumentException");
        }

        Constructor<Order> labels =
                Order.class.getConstructor(String.class, String[].class);

        System.out.println("isVarArgs: " + labels.isVarArgs());
        System.out.println("array argument: "
                + labels.newInstance(
                        "Notebook", (Object) new String[]{"A", "B"}));

        try {
            labels.newInstance("Notebook", "A", "B");
        } catch (IllegalArgumentException e) {
            System.out.println(
                    "expanded arguments: IllegalArgumentException");
        }

        try {
            numeric.newInstance("Notebook", -1L);
        } catch (InvocationTargetException e) {
            System.out.println(
                    "constructor threw: " + e.getCause().getMessage());
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17 hoặc mới hơn:

~~~bash
javac ConstructorArgumentsDemo.java
java ConstructorArgumentsDemo
~~~

Kết quả chính xác:

~~~text
exact lookup: [class java.lang.String, long]
widening: Notebook x3
lookup(Integer): NoSuchMethodException
invoke(String quantity): IllegalArgumentException
invoke(null primitive): IllegalArgumentException
isVarArgs: true
array argument: Notebook [A, B]
expanded arguments: IllegalArgumentException
constructor threw: quantity must be >= 0
~~~

Giải thích:

1. `getConstructor(String.class, long.class)` tìm đúng chữ ký khai báo; `Integer.valueOf(3)` là **đối số gọi được** cho constructor nhận `long` vì có unboxing rồi widening.
2. `getConstructor(String.class, Integer.class)` thất bại ở **tra cứu**: không có constructor nhận `Integer`; lỗi này xảy ra trước khi tạo đối tượng.
3. `numeric.newInstance("Notebook", "3")` và `numeric.newInstance("Notebook", (Object) null)` đã xác định được constructor, nhưng **đối số sai kiểu** cho `long`, nên phát sinh `IllegalArgumentException`.
4. Constructor khai báo `String... labels` được phản chiếu như tham số **`String[]`**. Khi gọi `newInstance`, phải đưa mảng đó vào **một vị trí đối số**; `(Object) new String[]{...}` diễn đạt rõ điều này.
5. Gọi `labels.newInstance("Notebook", "A", "B")` đưa **ba đối số thực tế** cho constructor chỉ có **hai tham số khai báo** (`String`, `String[]`). Reflection không tự gom hai chuỗi vào mảng varargs của constructor đích.
6. Constructor `Order(String, long)` tự ném `IllegalArgumentException` khi số lượng âm. Reflection bọc ngoại lệ **phát sinh bên trong constructor** trong `InvocationTargetException`; lấy ngoại lệ gốc qua `getCause()`.

Ví dụ dùng lớp lồng `static` và constructor `public`, nên không cần thao tác `setAccessible` và không cần truyền enclosing instance.

## 5. Varargs: phân biệt hai lớp `...`

Điểm dễ nhầm nhất là có **hai nơi xuất hiện varargs**:

- Constructor đích: `Order(String name, String... labels)`, được biểu diễn ở runtime bằng danh sách kiểu `(String, String[])`.
- API Reflection: `Constructor.newInstance(Object... initargs)`, nhận một mảng `Object[]` chứa **các đối số dùng để gọi constructor đích**.

Mô hình:

~~~text
Constructor cần gọi: Order(String, String[])
                               ^
                               | mảng String[] là một đối số
newInstance(Object... initargs)
    Object[0] = "Notebook"
    Object[1] = new String[]{"A", "B"}
~~~

Vì vậy:

~~~java
Constructor<Order> ctor =
        Order.class.getConstructor(String.class, String[].class);

// Đúng: hai đối số của constructor đích, vị trí 2 là String[].
Order a = ctor.newInstance(
        "Notebook", (Object) new String[]{"A", "B"});

// Sai cho constructor đích: ba đối số, không tự gom thành String[].
// ctor.newInstance("Notebook", "A", "B");
~~~

Đoạn trên minh họa với class `Order` đã định nghĩa. `(Object)` giúp tránh nhầm tầng varargs của chính API `newInstance`; việc đóng gói thủ công bằng `new Object[]{"Notebook", new String[]{"A", "B"}}` cũng cho ra danh sách đối số đúng.

**Không tự mở rộng varargs đích** là quy tắc đặc biệt cần ghi nhớ: tính chất `isVarArgs() == true` của `Constructor` không khiến Reflection thực hiện cú pháp lời gọi biến số lượng như compiler.

Với constructor **không tham số**, gọi `ctor.newInstance()` hoặc `ctor.newInstance(new Object[0])` đều phù hợp. Javadoc còn cho phép mảng `initargs` là `null` khi constructor không có tham số, nhưng `newInstance()` rõ ràng và ít gây nhầm lẫn hơn.

## 6. `null`, mảng đối số và đúng thứ tự

Mỗi đối số gắn với **vị trí tham số** tương ứng:

~~~java
// Giả sử có constructor User(String name, Integer age):
Constructor<User> ctor =
        User.class.getConstructor(String.class, Integer.class);

User u = ctor.newInstance("Mai", null);
~~~

Đây là đoạn minh họa; cần có lớp `User` tương ứng. `null` hợp lệ tại vị trí `Integer`, bởi đó là kiểu tham chiếu. Nhưng `null` không hợp lệ với `int` hoặc `long`.

Phân biệt:

- `ctor.newInstance((Object) null)`: có **một đối số** thực tế với giá trị `null`.
- `ctor.newInstance((Object[]) null)`: truyền **mảng đối số `initargs` là null** vào API Reflection; chỉ dùng dạng này nếu hiểu rõ quy tắc của API, đặc biệt với constructor không tham số.
- `ctor.newInstance(new Object[]{"Mai", null})`: truyền **hai đối số** cụ thể.
- `ctor.newInstance("Mai", null)`: cú pháp tiện lợi tương ứng hai đối số cho constructor nhận `String`, `Integer`.

Nếu các đối số bị đảo vị trí, chẳng hạn `(Integer, String)` thay vì `(String, Integer)`, Reflection không tự sắp xếp lại. Nó kiểm tra lần lượt vị trí 1, 2, ... của đúng constructor đã chọn.

Với dữ liệu động (JSON, form hoặc cấu hình), **đừng suy constructor chỉ bằng các giá trị runtime**. Nên thiết kế schema ánh xạ rõ từng tham số và chuyển dữ liệu chuỗi/số sang kiểu mong muốn trước khi gọi Reflection.

## 7. Constructor không public và Java Platform Module System

`getDeclaredConstructor(...)` có thể lấy metadata cho constructor `private`, `protected` hoặc package-private, nhưng metadata không đồng nghĩa quyền gọi:

~~~java
Constructor<Example> ctor =
        Example.class.getDeclaredConstructor(String.class);

if (!ctor.canAccess(null) && !ctor.trySetAccessible()) {
    throw new IllegalAccessException(
            "Không thể mở quyền truy cập constructor");
}

Example value = ctor.newInstance("demo");
~~~

Đây là đoạn minh họa cho lớp `Example`, không phải file Java độc lập. **`Constructor.canAccess` phải nhận `null`**, bởi chưa có đối tượng đích trước khi tạo instance. `trySetAccessible()` trả `false` khi không thể bật cờ bỏ qua kiểm tra truy cập theo điều kiện của JPMS; `setAccessible(true)` có thể ném `InaccessibleObjectException`.

Khi một package thuộc **named module** không được mở thích hợp, mã Reflection từ module khác không thể tùy ý truy cập thành viên private. Các quyền `opens` hoặc `--add-opens` cần được cấu hình một cách có chủ ý; `exports` chỉ cho phép các đường truy cập public theo phạm vi export và không tự cho phép deep reflection.

Khuyến nghị: ưu tiên constructor public/factory method được hỗ trợ. Chỉ mở quyền Reflection ở nơi thực sự cần, và kiểm tra kết quả xin quyền trước khi gọi.

## 8. Constructor của inner class không static

Với non-static inner class:

~~~java
class Outer {
    class Inner {
        Inner(String message) { }
    }
}
~~~

Một instance `Outer.Inner` luôn gắn với một instance `Outer`. Vì vậy, compiler bổ sung **tham số enclosing instance** vào constructor phản chiếu. Ví dụ tra cứu thường là:

~~~java
Outer owner = new Outer();

Constructor<Outer.Inner> innerCtor =
        Outer.Inner.class.getDeclaredConstructor(
                Outer.class, String.class);

// Trong ví dụ các class cùng ngữ cảnh truy cập hợp lệ:
Outer.Inner value = innerCtor.newInstance(owner, "hello");
~~~

Đoạn này minh họa trong một ngữ cảnh Java có quyền truy cập hợp lệ; hãy đặt mã trong vị trí tương thích với visibility của các class. Truyền `"hello"` mà quên `owner` sẽ khiến số lượng/kiểu đối số không khớp. Với lớp lồng **`static`**, không có tham số enclosing instance này.

Một số constructor do compiler sinh còn có tham số ẩn/synthetic, nên khi metadata thực tế khác với mã nguồn, hãy kiểm tra `getParameterTypes()`, `getParameterCount()` và `getParameters()` thay vì chỉ đoán từ chữ ký hiển thị trong IDE.

## 9. Các nhóm lỗi phải phân biệt

| Lỗi | Xảy ra ở đâu | Cách nhận diện |
| --- | --- | --- |
| `NoSuchMethodException` | Tra cứu `getConstructor` / `getDeclaredConstructor` | Không có đúng danh sách kiểu tham số yêu cầu |
| `IllegalArgumentException` | `newInstance` | Sai số lượng/kiểu đối số, narrowing không hợp lệ, hoặc cố tạo enum bằng Reflection |
| `IllegalAccessException` | `newInstance` | Caller không có quyền truy cập constructor đang được kiểm soát |
| `InvocationTargetException` | `newInstance` | **Chính constructor đích** ném ngoại lệ; xem `getCause()` |
| `InstantiationException` | `newInstance` | Không thể tạo instance do đặc tính của lớp, ví dụ abstract class |
| `ExceptionInInitializerError` | Khởi tạo lớp lúc dùng `newInstance` | Static initializer thất bại |
| `InaccessibleObjectException` | Có thể tại `setAccessible(true)` | Không thể bật quyền deep reflection do ranh giới module |

**Phân biệt đặc biệt:** nếu đối số `newInstance` không đúng kiểu, `IllegalArgumentException` đi ra trực tiếp từ API. Nhưng nếu constructor chạy và **bản thân constructor** ném `IllegalArgumentException`, Reflection bọc nó trong `InvocationTargetException`. Đây là hai trường hợp có hướng xử lý khác nhau.

Không cố gọi reflective constructor của `enum`: `Constructor.newInstance` cấm tạo enum theo cách này, kể cả có metadata constructor. Interface, kiểu primitive và mảng cũng không có constructor thông thường để tra cứu như lớp concrete.

## 10. Trường hợp `Class.newInstance()` cũ

`Class.newInstance()` đã **deprecated từ Java 9**. Hạn chế chính:

- Chỉ gọi constructor không tham số.
- Có thể truyền thẳng ngoại lệ do constructor ném, kể cả checked exception, khiến hợp đồng ngoại lệ kém rõ ràng.
- Khả năng xử lý constructor không public không tương đương với cách lấy `Constructor` rồi kiểm soát quyền rõ ràng.

Thay:

~~~java
T value = clazz.getDeclaredConstructor().newInstance();
~~~

Đây là cú pháp minh họa khi `clazz` có kiểu `Class<T>` và caller xử lý các ngoại lệ Reflection. Nếu constructor không public, vẫn phải đáp ứng quyền truy cập theo đúng quy tắc.

## 11. Checklist để gọi `newInstance` đúng

1. **Xác định lớp có thể tạo instance**: một concrete class, không phải enum được gọi bằng Reflection; chú ý static initialization.
2. **Tra đúng constructor**: dùng `getConstructor` cho public, `getDeclaredConstructor` khi cần metadata mọi mức truy cập; truyền `Class<?>...` đúng số lượng và thứ tự.
3. **Kiểm tra chữ ký runtime**: `getParameterTypes()` và `getParameterCount()`; nếu `isVarArgs()`, tham số cuối là **mảng**.
4. **Chuẩn bị Object[] đối số**: chuyển chuỗi dữ liệu sang kiểu nghiệp vụ thật; đánh giá wrapper, unboxing, widening, `null`, inner enclosing instance.
5. **Kiểm tra quyền truy cập**: `canAccess(null)`; chỉ xin quyền mở rộng khi cần và được phép.
6. **Gọi `newInstance` rồi phân loại lỗi**: lỗi lookup, lỗi đối số, lỗi truy cập và lỗi từ constructor đích là các nhóm khác nhau.
7. **Không tự giải overload bằng thứ tự mảng**: nếu cần chọn constructor động, xây luật chọn rõ ràng và xử lý ambiguous cases; tránh lấy constructor đầu tiên từ `getConstructors()`.

### Tự kiểm tra

**Câu 1:** Constructor khai báo `(String, long)`. `getConstructor(String.class, Integer.class)` có tìm được không?  
**Không.** Tra cứu cần kiểu đúng `long.class`.

**Câu 2:** Đã tìm đúng constructor `(String, long)`. `newInstance("A", Integer.valueOf(2))` có hợp lệ không?  
**Có.** `Integer` được unbox thành `int`, sau đó widen sang `long`.

**Câu 3:** Constructor đích khai báo `(String, String...)`. Truyền ba đối số `"A", "B", "C"` vào `newInstance` có tự gom `"B", "C"` thành `String[]` không?  
**Không.** Chuẩn bị đúng hai đối số: `"A"` và một `String[]`.

**Câu 4:** `newInstance` ném `InvocationTargetException`. Có luôn là do đối số sai kiểu không?  
**Không.** Nó thường cho biết **mã constructor đã chạy và ném ngoại lệ**; lấy `getCause()` để xem lỗi gốc.

**Câu 5:** Constructor private có thể tìm bằng `getDeclaredConstructor` nghĩa là luôn gọi được?  
**Không.** Phải đáp ứng Java access control và giới hạn module.

**Ghi nhớ:** `getConstructor(...)` tìm **chữ ký chính xác**; `Constructor.newInstance(...)` thực thi **constructor đã chọn** với số lượng và kiểu giá trị phù hợp. Hiểu rõ hai bước này sẽ tránh các lỗi phổ biến về primitive/wrapper, `null`, varargs và ngoại lệ.

### Tài liệu tham khảo

- [JDK 17 — `Constructor<T>`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Constructor.html)
- [JDK 17 — `Class.getConstructor` và `getDeclaredConstructor`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html)
- [Dev.java — Invoking Constructors](https://dev.java/learn/api/reflection-method-handles/reflection/constructors/)
- [Java Language Specification §15.9 — Class Instance Creation Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.9)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="constructors-004">Constructor mặc định khác constructor không tham số được khai báo tường minh</a>

<details>
<summary>Xem chi tiết</summary>

# Constructor mặc định khác constructor không tham số được khai báo tường minh

## 1. Hai khái niệm dễ nhầm

Trong Java, **default constructor** (constructor mặc định theo Java Language Specification, JLS `8.8.9) là constructor được **trình biên dịch khai báo ngầm định** khi một class không khai báo **bất kỳ constructor nào**. Đối với class top-level hoặc class lồng `static` thông thường, nó không nhận tham số và gọi constructor không tham số của superclass bằng `super()`. Trường hợp inner member class không `private` có thêm tham số ngầm định cho enclosing instance, sẽ xét riêng.

**Constructor không tham số được khai báo tường minh** (explicit no-argument constructor) là constructor do người lập trình **thực sự viết trong source** với danh sách tham số rỗng, ví dụ `User() {}`. Nó cũng có thể được gọi bằng `new User()`, nhưng **không phải default constructor do compiler tự sinh**. Khi đã có một khai báo constructor bất kỳ, Java không tự sinh default constructor cho class đó nữa.

Ví dụ tối giản:

~~~java
// Trích đoạn minh họa: ba class top-level trong cùng package.
class Auto {
    // Không khai báo constructor: compiler tạo default Auto().
}

class Written {
    Written() {
        // Constructor không tham số được viết tường minh.
    }
}

class ParamsOnly {
    ParamsOnly(int value) {
        // Đã khai báo một constructor => không có default ParamsOnly().
    }
}
~~~

Do đó:

- `new Auto()` hợp lệ nhờ default constructor ngầm định.
- `new Written()` hợp lệ nhờ constructor không tham số được khai báo tường minh.
- `new ParamsOnly()` **không hợp lệ khi biên dịch**; chỉ `new ParamsOnly(5)` hợp lệ trong phạm vi ví dụ.
- Constructor không được kế thừa từ superclass. `Child` không tự “có” constructor của `Parent`.

**Phân biệt thêm hai nghĩa của “default”:** Trong cụm “default constructor”, từ “default” là thuật ngữ JLS dành cho việc compiler tạo constructor. Trong cụm “default access” hoặc “package-private”, “default” nói về **mức truy cập không ghi modifier**. Một constructor `Written() {}` ở class package-private có *package access*, nhưng vẫn là constructor **tường minh** chứ không phải constructor mặc định do compiler sinh.

## 2. Điều kiện hình thành và tính chất của default constructor

Quy tắc cho class thông thường (không xét đặc thù enum/record/anonymous class):

1. Nếu **không có constructor declaration** nào trong phần thân class, compiler ngầm khai báo một default constructor.
2. Nếu class có **dù chỉ một** constructor được khai báo (`A()`, `A(int)`, `A(String)`, private constructor…), compiler **không** tạo default constructor.
3. Default constructor không có mệnh đề `throws`. Đối với class thông thường không phải `Object`, thân constructor tương đương lời gọi `super()` tới superclass không tham số.
4. Quyền truy cập của default constructor được suy từ modifier truy cập của class: ví dụ class `public` thì constructor mặc định `public`; class package-private thì constructor mặc định package-private. Member class có thể là `protected` hoặc `private` và quy tắc tương ứng được áp dụng. Không suy rằng tất cả default constructors đều `public`.
5. Với **non-private inner member class**, default constructor có thêm tham số ngầm định đại diện cho enclosing instance. Vì thế ở mức Reflection/bytecode, không thể mặc định rằng mọi constructor ngầm sinh đều có `getParameterCount() == 0`.

Bảng đối chiếu:

| Thuộc tính | Default constructor ngầm định | Constructor không tham số tường minh |
| --- | --- | --- |
| Ai tạo khai báo? | Compiler, khi class không có constructor declaration. | Lập trình viên viết `ClassName()`. |
| Có trong source? | Không có constructor declaration. | Có declaration trong source. |
| Có thể tùy chọn modifier truy cập? | Suy từ mức truy cập của class theo JLS. | `public`/`protected`/package-private/`private` theo khai báo hợp lệ. |
| Có thể viết thân tùy ý? | Không; hành vi ngầm định gọi `super()` (cùng cơ chế khởi tạo instance). | Có thể viết logic, kiểm tra đầu vào, `this(...)` hoặc `super(...)`. |
| Có thể khai báo `throws`? | Không. | Có. |
| Có thể gắn annotation constructor trong source? | Không có vị trí khai báo constructor để gắn trực tiếp. | Có thể dùng annotation có `@Target(CONSTRUCTOR)`. |
| Có thể dùng `new X()`? | Thường có nếu constructor truy cập được và class khởi tạo được. | Có nếu constructor truy cập được và phần thân hoàn thành bình thường. |
| `getDeclaredConstructor()` tìm thấy? | Có, với class thông thường có đúng zero-arg ở cấp Reflection. | Có, nếu thật sự không có tham số ở cấp Reflection. |
| `isSynthetic()` phân biệt được? | Không: mặc định không nên coi nó là dấu nhận dạng “do compiler sinh”. | Không: cùng có thể trả `false`. |

**Điểm chung:** cả hai dẫn đến một phương thức khởi tạo thực tế trong class file mà JVM dùng; chỉ nhìn số tham số rỗng không chứng minh nguồn gốc constructor.

## 3. Compiler còn tự làm gì khi có constructor tường minh?

Đối với constructor tường minh không bắt đầu bằng lời gọi constructor khác (`this(...)` hoặc `super(...)`), compiler cũng chèn lời gọi `super()` ngầm định theo quy tắc constructor invocation. Vì vậy, **có `super()` ngầm trong body không đồng nghĩa là default constructor được sinh**.

~~~java
// Hai constructor minh họa, cú pháp tương đương ở phần gọi superclass.
class A {
    A() {
        // compiler chèn super() trước phần body này
    }
}

class B {
    B() {
        super(); // được viết tường minh
    }
}
~~~

Trong cả hai class, `A()` và `B()` là các constructor **đã được khai báo tường minh**. Không có default constructor khác được bổ sung. Java cho phép chỉ một constructor invocation dẫn đầu phù hợp, và `this(...)`/`super(...)` không thể tùy tiện gọi ở cuối body.

### Field initializer và instance initializer vẫn chạy

Việc constructor mặc định không chứa logic tùy biến trong source **không có nghĩa** đối tượng không được khởi tạo giá trị.

~~~java
class Counter {
    int count = 12; // instance field initializer
    {
        count++;     // instance initializer block
    }
    // Compiler tạo constructor mặc định.
}
~~~

`new Counter().count` bằng `13`. Field initializers và instance initializer blocks thực thi trong quá trình tạo đối tượng theo thứ tự khởi tạo do ngôn ngữ quy định (sau chuỗi khởi tạo superclass, trước phần thân constructor của class). Vì thế không kết luận mọi field có giá trị mặc định `0` chỉ vì không có constructor tường minh.

Constructor tường minh có thể chứa thêm logic khác:

~~~java
class Account {
    private int balance;
    Account() {
        this.balance = 100; // Không thể thêm lệnh tùy ý vào constructor mặc định.
    }
}
~~~

`Account()` vẫn là zero-arg constructor, nhưng vì được viết trực tiếp nên không phải default constructor.

## 4. Ví dụ Java 17+ biên dịch và chạy được

Tạo một file `ConstructorKindsDemo.java`:

~~~java
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

public class ConstructorKindsDemo {
    public static class Auto {
        int value = 10; // Có field initializer, không khai báo constructor.
    }

    public static class Written {
        int value;
        public Written() {
            value = 20;
        }
    }

    public static class ParamsOnly {
        int value;
        public ParamsOnly(int value) {
            this.value = value;
        }
    }

    public static class Overloaded {
        public Overloaded() {}
        public Overloaded(String text) {}
    }

    public static class Base {
        protected Base(int value) {}
    }

    public static class Child extends Base {
        public Child() {
            super(99); // Phải gọi constructor hiện có của Base.
        }
    }

    public static class Outer {
        public class Inner {
            // Constructor mặc định cần enclosing instance.
        }
    }

    static void inspect(String label, Class<?> type)
            throws ReflectiveOperationException {
        Constructor<?> constructor = type.getDeclaredConstructor();
        System.out.println(label
                + ": count=" + type.getDeclaredConstructors().length
                + ", public=" + Modifier.isPublic(constructor.getModifiers())
                + ", synthetic=" + constructor.isSynthetic());
    }

    public static void main(String[] args) throws Exception {
        inspect("Auto", Auto.class);
        inspect("Written", Written.class);

        System.out.println("ParamsOnly: count="
                + ParamsOnly.class.getDeclaredConstructors().length
                + ", noArg=" + hasNoArg(ParamsOnly.class));
        System.out.println("Overloaded: count="
                + Overloaded.class.getDeclaredConstructors().length
                + ", noArg=" + hasNoArg(Overloaded.class));

        System.out.println("Auto.value=" + new Auto().value);
        System.out.println("Written.value=" + new Written().value);
        System.out.println("Child created=" + (new Child() != null));

        Constructor<?> innerConstructor =
                Outer.Inner.class.getDeclaredConstructor(Outer.class);
        Object created = innerConstructor.newInstance(new Outer());
        System.out.println("Inner: parameterCount="
                + innerConstructor.getParameterCount()
                + ", enclosingType="
                + innerConstructor.getParameterTypes()[0].getSimpleName()
                + ", created=" + (created instanceof Outer.Inner));
    }

    static boolean hasNoArg(Class<?> type) {
        try {
            type.getDeclaredConstructor();
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 ConstructorKindsDemo.java
java ConstructorKindsDemo
~~~

Kết quả với JDK 17/21:

~~~text
Auto: count=1, public=true, synthetic=false
Written: count=1, public=true, synthetic=false
ParamsOnly: count=1, noArg=false
Overloaded: count=2, noArg=true
Auto.value=10
Written.value=20
Child created=true
Inner: parameterCount=1, enclosingType=Outer, created=true
~~~

Giải thích:

- `Auto` không khai báo constructor nên compiler tạo một default constructor `public` do `Auto` là `public static class`. `Written` có đúng một constructor `public Written()` do lập trình viên viết.
- Reflection thấy `count=1` và constructor `public` cho cả hai. `isSynthetic() == false` trong trường hợp này cho thấy **không thể** dùng `isSynthetic` để phát hiện constructor default.
- `ParamsOnly` chỉ có `ParamsOnly(int)`, vì vậy gọi `getDeclaredConstructor()` (không truyền parameter types) không tìm thấy.
- `Overloaded` có constructor zero-arg **tường minh** và một constructor một tham số; compiler không tạo thêm bản khác.
- Field initializer làm `Auto.value == 10` dù không viết body constructor. `Written` gán `20` trong body tường minh.
- `Child` gọi `Base(int)` một cách tường minh nên biên dịch được dù `Base` không có constructor zero-arg.
- `Outer.Inner` là **non-private inner member class**. Ở Reflection, constructor nhận tham số `Outer` là enclosing instance. `Outer.Inner.class.getDeclaredConstructor()` không phải phép tìm kiếm đúng ở ví dụ này.

## 5. Vì sao subclass có thể không biên dịch khi trông như “chẳng cần constructor”?

Xét fragment **cố ý không biên dịch** sau (để hiểu quy tắc, không đưa chung vào file demo):

~~~java
class Parent {
    Parent(int id) {}
}

class Child extends Parent {
    // Không khai báo constructor.
}
~~~

Compiler muốn sinh default constructor `Child()`, mà constructor mặc định đó gọi `super()`. Nhưng `Parent` chỉ có `Parent(int)`; không tồn tại `Parent()` thích hợp, nên `Child` bị lỗi biên dịch. Java **không** tự biến `super()` thành `super(0)`.

Cách sửa là khai báo constructor cho `Child` và chọn đúng constructor superclass:

~~~java
class Parent {
    Parent(int id) {}
}

class Child extends Parent {
    Child() {
        super(42);
    }
}
~~~

Lúc này `Child()` là constructor không tham số **tường minh**, không còn default constructor ngầm định.

### Trường hợp superclass constructor ném checked exception

Default constructor không khai báo `throws`, vì thế nếu constructor zero-arg được chọn ở superclass ném checked exception, class con không tự giải quyết được:

~~~java
// Fragment minh họa lỗi biên dịch do checked exception.
import java.io.IOException;

class Parent {
    Parent() throws IOException {}
}

class Child extends Parent {
    // Default Child() ngầm định không có throws IOException.
}
~~~

Có thể sửa bằng cách viết constructor rõ ràng và khai báo/giải quyết checked exception:

~~~java
// Fragment thay thế hợp lệ khi đặt vào file Java có import IOException.
class Child extends Parent {
    Child() throws IOException {
        super();
    }
}
~~~

Điều này chỉ có thể làm trong constructor **tường minh**, không thể “thêm `throws`” vào default constructor do compiler sinh.

## 6. Tác động của access modifier

Default constructor **kế thừa mức truy cập của class theo quy tắc JLS**, trong khi constructor tường minh có thể dùng modifier khác miễn hợp lệ.

~~~java
public class PublicAuto {
    // Compiler tạo public PublicAuto().
}
~~~

~~~java
public class PublicLocked {
    private PublicLocked() {
        // Ngăn việc khởi tạo trực tiếp từ bên ngoài.
    }
}
~~~

Đây là hai source minh họa **ở hai file riêng** (không để cả hai public top-level classes trong một file). `PublicAuto` có default public constructor; `PublicLocked` có constructor private được viết tường minh và không có default constructor. Kể cả Reflection nhìn thấy `PublicLocked()` qua `getDeclaredConstructors()`, việc gọi `newInstance()` phải tuân quyền truy cập/module.

Một class `abstract` cũng có thể có default constructor nhưng không thể tạo instance trực tiếp bằng `new AbstractClass()`, dù Reflection có thể đọc metadata constructor. “Constructor tồn tại” và “class khởi tạo được” là hai điều khác nhau.

## 7. Quan sát bằng Reflection: điều gì biết được, điều gì không?

Các API quan trọng:

| API | Ý nghĩa |
| --- | --- |
| `getDeclaredConstructors()` | Trả tất cả constructor được class khai báo ở cấp Reflection, kể cả private và constructor mặc định do compiler tạo; **không** trả constructor kế thừa vì constructor không được kế thừa. Thứ tự mảng không được bảo đảm. |
| `getConstructors()` | Trả các constructor `public` của class, không phải constructor kế thừa từ cha. |
| `getDeclaredConstructor(Class<?>... types)` | Tìm constructor theo đúng danh sách kiểu tham số ở mức Reflection. Không tham số `getDeclaredConstructor()` tìm zero-arg constructor nếu có. |
| `getConstructor(Class<?>... types)` | Tìm constructor `public` theo danh sách kiểu tham số. |
| `Constructor.getParameterCount()` | Số tham số Reflection quan sát; inner member class có thể có enclosing-instance parameter. |
| `Constructor.isSynthetic()` | Cho biết constructor có đánh dấu synthetic theo metadata JVM hay không; **không** trả lời chắc chắn constructor được viết trong source hay là default. |
| `Constructor.newInstance(args...)` | Gọi constructor và tạo instance khi class/constructor có quyền, tham số hợp lệ và quá trình khởi tạo thành công; có thể ném exception. |

**Không có API Reflection chuẩn dạng `isDefaultConstructor()` hoặc `isDeclaredExplicitlyInSource()`.** Một constructor ngầm định `public Auto()` và một constructor tường minh `public Written() {}` có thể trông giống nhau ở metadata: cùng số tham số, modifier, tên `<init>` trong bytecode và `isSynthetic()==false`. Không nên suy đoán nguồn gốc chỉ từ Reflection.

Biên dịch sang class file vẫn tạo phương thức khởi tạo `<init>`. Dùng `javap -p -c` để xem bytecode (ví dụ với các nested classes trong demo) có thể thấy các lệnh khởi tạo và gọi superclass, nhưng **sự hiện diện của `<init>()V` không chứng minh** constructor được viết hay được compiler sinh. Một số chi tiết như annotation hoặc debug metadata có thể cung cấp gợi ý trong trường hợp cụ thể, không phải dấu nhận dạng tổng quát. Muốn phân biệt một cách đáng tin cậy, cần biết **source declaration** hoặc metadata riêng của công cụ biên dịch/source parser.

Đối với framework, đa số nhu cầu chỉ là “có constructor phù hợp để gọi hay không?” thay vì “nó xuất phát từ source hay compiler?”. Khi chọn constructor, hãy xét accessibility, module, signature, annotation, exception và mục đích nghiệp vụ, thay vì tự đoán nguồn gốc.

## 8. Ngoại lệ quan trọng: inner class, enum, record, anonymous class

Quy tắc default constructor phải được hiểu trong đúng ngữ cảnh loại class.

### Non-static inner member class

Một inner member class không `private` cần enclosing instance. Constructor mà `Outer.Inner.class.getDeclaredConstructor(...)` nhìn thấy có thể nhận tham số `Outer` mặc dù source không viết tham số nào. Vì vậy, công cụ Reflection không nên suy ra “không có default constructor” chỉ do `getDeclaredConstructor()` không tồn tại khi truyền zero parameters. Ví dụ chạy ở trên đã chứng minh constructor có `parameterCount=1`.

### Enum

Enum có quy tắc constructor đặc biệt trong JLS `8.9.2. Constructor khai báo trong enum không thể là `public` hoặc `protected`; các giá trị enum được tạo theo cơ chế do compiler/JVM quản lý. Không áp dụng trực tiếp quy tắc class thường để dùng `Constructor.newInstance()` tạo thêm enum constant: Reflection không cho tạo enum object bằng cách đó. Các tham số nội bộ trong constructor enum cũng không nên được diễn giải như API constructor thông thường.

### Record (Java 16+)

Record có **canonical constructor** gắn với record components. Khi không viết canonical constructor, Java có thể sinh **implicit canonical constructor** theo quy tắc record, **khác khái niệm default constructor của class thông thường**. Ví dụ `record Point(int x, int y) {}` có canonical constructor nhận `int, int`, chứ không tự có `Point()`. Với `record Empty() {}`, canonical constructor tình cờ có zero parameters nhưng bản chất vẫn là **canonical constructor**.

~~~java
// Các record declaration minh họa, có thể đặt trong file Java 17 thích hợp.
record Point(int x, int y) {}
record Empty() {}
~~~

Lời gọi `new Point()` không hợp lệ trừ khi người lập trình có thêm constructor không tham số hợp lệ tự ủy quyền cho canonical constructor.

### Anonymous class

Anonymous class không có cú pháp đặt tên constructor trong source; constructor của nó tuân quy tắc riêng ở JLS `15.9.5.1. Đừng lấy metadata constructor của anonymous class làm ví dụ đơn giản để xác định default constructor của một class đặt tên.

## 9. Sai lầm thường gặp

1. **“Class nào cũng có default constructor.”** Sai: có một constructor tường minh bất kỳ là compiler không sinh default constructor khác.
2. **“Constructor zero-arg là default constructor.”** Sai: zero-arg có thể do lập trình viên khai báo; record zero-component còn có canonical constructor zero-arg.
3. **“Nếu khai báo `A(int)` thì Java vẫn giữ `A()`.”** Sai: muốn cả hai phải viết cả `A()` lẫn `A(int)`.
4. **“Default constructor luôn public.”** Sai: mức truy cập phụ thuộc class (và các quy tắc riêng của loại class).
5. **“Default constructor không chạy khởi tạo field.”** Sai: instance field initializers và instance initializer blocks vẫn chạy.
6. **“Có `super()` ngầm tức constructor là default.”** Sai: constructor viết tường minh không gọi `this(...)`/`super(...)` cũng nhận một lời gọi `super()` ngầm định.
7. **“Con tự dùng constructor có tham số của cha.”** Sai: constructors không được kế thừa, và default constructor ở con chỉ cố gọi `super()`.
8. **“Reflection tự phân biệt constructor do compiler sinh.”** Sai: `isSynthetic` không phải bộ phân loại này, và constructor mặc định vẫn nằm trong `getDeclaredConstructors()`.
9. **“`getDeclaredConstructor()` luôn tìm đúng default constructor.”** Sai với trường hợp non-private inner member class: có tham số enclosing instance ở Reflection.
10. **“Record và enum giống class thường trong mọi trường hợp.”** Sai: loại class này có quy tắc constructor đặc thù.

## 10. Tình huống thực hành và cách kiểm thử

- Bỏ hết constructor declarations khỏi `ParamsOnly` rồi gọi `getDeclaredConstructor()`: quan sát compiler bắt đầu sinh default constructor.
- Thêm một constructor `ParamsOnly(String)` và thử `new ParamsOnly()`: nêu rõ tại sao bị lỗi biên dịch.
- Đổi constructor zero-arg trong `Written` thành `private Written()`: dự đoán thay đổi ở `getConstructors()` và `getDeclaredConstructors()`, sau đó thử gọi từ một class không có quyền.
- Tạo superclass chỉ có constructor `Parent(int)` rồi khai báo subclass không có constructor: giải thích vì sao default `Child()` không biên dịch.
- Tạo superclass zero-arg constructor ném checked exception: giải thích vì sao cần constructor tường minh `throws` ở lớp con.
- So sánh `Auto.class.getDeclaredConstructor().isSynthetic()` và `Written.class.getDeclaredConstructor().isSynthetic()`, tránh kết luận nguồn gốc theo boolean này.
- Với `Outer.Inner`, thử lấy constructor bằng `getDeclaredConstructor(Outer.class)` và gọi `newInstance(new Outer())`.
- Với `record Point(int x, int y) {}`, kiểm tra danh sách constructor Reflection và phân biệt canonical constructor với default constructor.

## 11. Tài liệu tham khảo

- [Java Language Specification 17 — `8.8.9 Default Constructor](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.8.9): điều kiện sinh, quyền truy cập, lời gọi superclass, `throws` và ngoại lệ inner class.
- [Java Language Specification 17 — `8.8.7 Constructor Body](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.8.7): cơ chế `this(...)`/`super(...)` và lời gọi ngầm.
- [Java SE 17 — Class#getDeclaredConstructors và getDeclaredConstructor](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): constructor mặc định xuất hiện trong Reflection, phạm vi declared/public, inner member class parameter.
- [Java Language Specification 17 — `8.10 Record Classes](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.10): canonical constructor của record và quy tắc sinh riêng.
- [Java Language Specification 17 — `8.9 Enum Classes](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.9): constructor enum và giới hạn riêng.

**Ghi nhớ:** “mặc định” mô tả **nguồn gốc do compiler ngầm khai báo khi không có constructor nào**, còn “không tham số” mô tả **hình dạng tham số của một constructor**. Đừng dùng hai khái niệm thay thế nhau khi giải thích mã nguồn, thiết kế subclass hoặc sử dụng Reflection.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="constructors-005">Constructor private và điều kiện truy cập</a>

<details>
<summary>Xem chi tiết</summary>

# Constructor private và điều kiện truy cập trong Java Reflection

## 1. Private constructor giải quyết vấn đề gì?

Constructor là thành viên đặc biệt dùng để khởi tạo một instance. Khai báo một constructor `private` giúp **hạn chế nơi có thể gọi constructor theo ngôn ngữ Java**, thường được dùng để kiểm soát quá trình tạo đối tượng, buộc đi qua factory method hoặc ngăn tạo đối tượng của lớp tiện ích.

~~~java
final class SecretBox {
    private final String value;

    private SecretBox(String value) {
        this.value = value;
    }

    public static SecretBox of(String value) {
        return new SecretBox(value);
    }
}
~~~

Đây là đoạn minh họa ngắn: code thông thường ở ngoài phạm vi được phép không thể viết `new SecretBox("abc")` vì constructor là private; thay vào đó gọi `SecretBox.of("abc")`. Tuy nhiên Java Reflection **có thể tra cứu metadata của private constructor**, và trong một số điều kiện có thể yêu cầu bỏ qua kiểm tra truy cập khi gọi. Khả năng đó phụ thuộc caller, phạm vi package/module và quyền deep reflection.

Vì vậy cần phân biệt rõ ba câu hỏi:

1. **Constructor có được khai báo trong lớp không?** Kiểm tra bằng `getDeclaredConstructor(...)` hoặc `getDeclaredConstructors()`.
2. **Caller có quyền gọi constructor đang tìm được không?** Kiểm tra quyền khi dùng `Constructor.newInstance(...)`; `Constructor.canAccess(null)` giúp thăm dò quyền hiện tại.
3. **Có được phép bật access-check override không?** `trySetAccessible()` hoặc `setAccessible(true)` chỉ hoạt động khi những ranh giới truy cập cho phép.

**Tìm thấy constructor không đồng nghĩa gọi được constructor.** Đây là trọng tâm khi làm việc với private constructor bằng Reflection.

## 2. `getConstructor` khác `getDeclaredConstructor`

| API của `Class<T>` | Phạm vi tra cứu | Có lấy private constructor không? |
| --- | --- | --- |
| `getConstructor(Class<?>... parameterTypes)` | Chỉ public constructor được khai báo trên chính lớp | Không |
| `getConstructors()` | Mảng các public constructor của chính lớp | Không |
| `getDeclaredConstructor(Class<?>... parameterTypes)` | Constructor được khai báo trên chính lớp, bất kể modifier | Có |
| `getDeclaredConstructors()` | Mọi constructor khai báo trên chính lớp: public, protected, package-private và private | Có |

Khác với nhiều public method/field, **constructor không được kế thừa**. Ví dụ `Child extends Parent` không làm constructor `Parent(int)` trở thành constructor `Child(int)`. Cả hai nhóm API trên đều tra cứu constructor của **lớp đang xét**, không tìm constructor kế thừa.

Hai API tra cứu một constructor yêu cầu **danh sách kiểu tham số chính xác**, theo đúng thứ tự, bằng đối tượng `Class`:

~~~java
Constructor<SecretBox> ctor =
        SecretBox.class.getDeclaredConstructor(String.class);
~~~

Nếu constructor thực tế nhận `String` thì truyền `String.class`; nếu nhận primitive `int` thì truyền `int.class`, không thay bằng `Integer.class`. Tra cứu không thực hiện overload resolution giống cú pháp `new` và không tự chuyển đổi kiểu cho bạn. Sai chữ ký sẽ ném `NoSuchMethodException`, còn `getDeclaredConstructor` không cần constructor phải public.

**Modifier và vị trí khai báo:** Có thể kiểm tra `Modifier.isPrivate(ctor.getModifiers())`, `ctor.getDeclaringClass()`, `ctor.getParameterTypes()` và `ctor.isVarArgs()` để biết metadata. Những phép đọc metadata này tự thân không cấp quyền chạy constructor.

## 3. Ví dụ Java 17+: tra cứu, bị chặn, mở quyền, khởi tạo

Tạo tệp **`PrivateCtorDemo.java`**, trong đó có **hai top-level class tách biệt**. Đặt `PrivateCtorDemo` và `SecretBox` trong cùng tệp/package giúp ví dụ đơn giản, nhưng chúng **không phải nested class/nestmate** nên private constructor của `SecretBox` không tự nhiên truy cập được từ `PrivateCtorDemo`.

~~~java
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

public class PrivateCtorDemo {
    public static void main(String[] args) throws Exception {
        Class<SecretBox> type = SecretBox.class;

        try {
            type.getConstructor(String.class);
        } catch (NoSuchMethodException e) {
            System.out.println("public lookup: " + e.getClass().getSimpleName());
        }

        Constructor<SecretBox> ctor =
                type.getDeclaredConstructor(String.class);

        System.out.println("private: "
                + Modifier.isPrivate(ctor.getModifiers()));
        System.out.println("canAccess before: " + ctor.canAccess(null));

        try {
            ctor.newInstance("alpha");
        } catch (IllegalAccessException e) {
            System.out.println("invoke before: "
                    + e.getClass().getSimpleName());
        }

        System.out.println("opened: " + ctor.trySetAccessible());
        System.out.println("canAccess after: " + ctor.canAccess(null));
        System.out.println("created: " + ctor.newInstance("alpha").code());

        try {
            ctor.newInstance("");
        } catch (InvocationTargetException e) {
            System.out.println("target error: "
                    + e.getCause().getClass().getSimpleName());
        }

        ctor.setAccessible(false);
        System.out.println("canAccess reset: " + ctor.canAccess(null));
    }
}

class SecretBox {
    private final String code;

    private SecretBox(String code) {
        if (code.isBlank()) {
            throw new IllegalArgumentException("empty code");
        }
        this.code = code;
    }

    public String code() {
        return code;
    }
}
~~~

Biên dịch bằng trình biên dịch tương thích Java 17, rồi chạy trên classpath:

~~~bash
javac --release 17 PrivateCtorDemo.java
java PrivateCtorDemo
~~~

Output dự kiến của ví dụ khi chạy trong unnamed module/classpath thông thường:

~~~text
public lookup: NoSuchMethodException
private: true
canAccess before: false
invoke before: IllegalAccessException
opened: true
canAccess after: true
created: alpha
target error: IllegalArgumentException
canAccess reset: false
~~~

### Giải thích từng giai đoạn

**Giai đoạn 1 — Chỉ tìm constructor công khai.** `type.getConstructor(String.class)` ném `NoSuchMethodException` vì constructor `SecretBox(String)` là private. Điều này **không nói** constructor không tồn tại, mà nói nó không nằm trong tập public constructor theo cách tra cứu đó.

**Giai đoạn 2 — Tìm đúng khai báo.** `type.getDeclaredConstructor(String.class)` trả về `Constructor<SecretBox>`. `Modifier.isPrivate(...)` là `true`. `canAccess(null)` lúc này là `false` do caller ở một top-level class khác và constructor private chưa được mở quyền.

**Giai đoạn 3 — Gọi khi chưa có quyền.** `ctor.newInstance("alpha")` thực hiện access check và ném `IllegalAccessException`. Constructor body chưa chạy, nên không có object nào được tạo từ lời gọi bị chặn này.

**Giai đoạn 4 — Bật override hợp lệ.** `ctor.trySetAccessible()` trả `true` trong môi trường classpath của ví dụ. Khi đó access check ngôn ngữ Java được bỏ qua **cho reflection object `ctor`**, `canAccess(null)` trở thành `true` và `ctor.newInstance("alpha")` tạo đối tượng, truy cập `code()` nhận `alpha`.

**Giai đoạn 5 — Lỗi từ logic constructor.** `ctor.newInstance("")` đã qua bước kiểm tra quyền, nhưng constructor body tự ném `IllegalArgumentException` khi chuỗi trống. Reflection bọc lỗi của target trong `InvocationTargetException`; `getCause()` cho thấy nguyên nhân gốc là `IllegalArgumentException`. Trường hợp này **khác** lỗi `IllegalAccessException` ở giai đoạn 3.

**Giai đoạn 6 — Trả về kiểm tra truy cập bình thường.** `ctor.setAccessible(false)` tắt override trên object `Constructor`; `canAccess(null)` trở lại `false` với caller này.

Đây là bài tập phân tích hành vi, **không phải lời khuyên luôn bật private access trong mã sản xuất**. Kết quả `opened: true` chỉ là kết quả trong điều kiện chạy của ví dụ, không phải bảo đảm cho tất cả named module hoặc thư viện.

## 4. Khi nào `Constructor.newInstance` được gọi mà không cần override?

Điều kiện của `Constructor.newInstance` tùy **ngữ cảnh caller thực sự**, không chỉ tùy modifier:

- `public` constructor của kiểu truy cập được từ caller có thể được gọi trực tiếp hoặc qua Reflection mà không mở override.
- Constructor `private` thường chỉ truy cập được từ code bên trong chính lớp khai báo hoặc những **nestmates** được ngôn ngữ Java cho phép; caller bên ngoài như `PrivateCtorDemo` trong ví dụ trên bị chặn.
- Constructor package-private có thể được code cùng package gọi khi các điều kiện khác thỏa; `protected` có quy tắc về package/subclass và ngữ cảnh truy cập.
- Class bản thân không truy cập được (ví dụ top-level class package-private thuộc package khác, hoặc public class ở package không được export cho named module gọi) có thể ngăn truy cập dù constructor có modifier public.

Vì thế, `getDeclaredConstructor` chỉ chứng minh tìm được metadata. **Không suy từ `getModifiers()` rằng việc gọi luôn thành công.** Access check còn xét lớp khai báo, quan hệ caller, module và trạng thái override.

Một chi tiết dễ gây nhầm trong bài thực hành: nếu đặt private constructor trong **nested class** và gọi Reflection từ outer class cùng nest, Java có thể coi các lớp đó là **nestmates** với quyền truy cập private theo ngôn ngữ. Khi đó `canAccess(null)` có thể đã `true`, và lời gọi không bị `IllegalAccessException`. Muốn minh họa lời gọi bị chặn rõ ràng, dùng caller ở một **top-level class tách biệt** như chương trình trên.

## 5. `canAccess(null)`, `trySetAccessible` và `setAccessible`

`Constructor<T>` kế thừa `AccessibleObject` nên cung cấp các API:

| API | Ý nghĩa | Hành vi chính |
| --- | --- | --- |
| `ctor.canAccess(null)` | Kiểm tra quyền dùng constructor từ caller hiện tại | Trả `boolean`, không tự bật quyền |
| `ctor.trySetAccessible()` | Thử bật override access check ngôn ngữ | Trả `true` nếu bật được, `false` nếu ranh giới truy cập không cho phép |
| `ctor.setAccessible(true)` | Yêu cầu bật override | Có thể ném `InaccessibleObjectException` khi không mở được |
| `ctor.setAccessible(false)` | Tắt override | Lần gọi tiếp theo phải tuân thủ kiểm tra quyền thông thường |

Constructor **không có receiver đã tồn tại**; vì vậy với `canAccess` dùng đúng `null`. Không nhầm với `Method.canAccess(receiver)` hoặc `Field.canAccess(receiver)` của instance member. `Constructor.newInstance(...)` nhận **đối số truyền vào constructor**, không nhận receiver; số lượng/kiểu đối số phải tương thích với signature.

Quy tắc kiểm tra trước khi gọi:

~~~java
Constructor<SecretBox> constructor =
        SecretBox.class.getDeclaredConstructor(String.class);
if (!constructor.canAccess(null) && !constructor.trySetAccessible()) {
    throw new IllegalStateException(
            "Không được phép gọi constructor của SecretBox");
}
SecretBox instance = constructor.newInstance("alpha");
~~~

Đây là **mảnh code minh họa** dùng lại định nghĩa `SecretBox` ở ví dụ; không phải một file độc lập. Nó thể hiện thao tác kiểm tra và xử lý nhánh mở quyền bị từ chối. Đoạn code không giải quyết mọi lỗi: `newInstance` vẫn có thể lỗi nếu constructor tự ném exception, tham số sai kiểu hoặc khởi tạo lớp gặp vấn đề.

`setAccessible(true)`/`trySetAccessible()` **không sửa modifier `private` trong class file**, không biến constructor thành public cho mọi code và không bỏ qua kiểm tra kiểu đối số hoặc logic validation bên trong constructor. Override thuộc reflection object đang được sử dụng.

## 6. JPMS: vì sao cùng một code hoạt động trên classpath nhưng thất bại ở named module?

Khi Java Platform Module System (Java 9+) được dùng với **named module**, public API và deep reflection có ranh giới riêng. Một private constructor nằm trong package của named module không nhất thiết được mở cho một module khác chỉ vì có thể tìm metadata.

Xét hai module `com.example.domain` (chứa class đích) và `com.example.framework` (cần Reflection). Descriptor của module domain có thể công bố:

~~~java
module com.example.domain {
    exports com.example.domain.api;
    opens com.example.domain.internal to com.example.framework;
}
~~~

Ý nghĩa:

- `exports com.example.domain.api;` cho phép các module phù hợp truy cập public type/member trong package API theo quy tắc Java. **`exports` không tự cho phép deep reflection vào private constructor**.
- `opens com.example.domain.internal to com.example.framework;` cho phép module framework dùng deep reflection trong package internal, nếu các điều kiện khác phù hợp. Package được `opens` không vì vậy trở thành public API có thể import tùy ý ở compile time.
- Nếu package không được mở cho module gọi và caller không thuộc trường hợp JPMS cho phép, `ctor.trySetAccessible()` có thể trả `false`; `ctor.setAccessible(true)` có thể ném `java.lang.reflect.InaccessibleObjectException` (unchecked).
- Caller ở **cùng module** với declaring class có những khả năng khác caller xuyên module. Với các class trên classpath thuộc unnamed module, package thường mở cho deep reflection nên ví dụ đơn giản có thể bật override.

Nếu chương trình thật sự cần framework được gọi private constructor ở module khác, người thiết kế module có thể chủ động mở đúng package, ví dụ `opens ... to ...`, hoặc cung cấp một public factory API để tránh phụ thuộc vào private implementation. Trong quá trình triển khai, JVM option `--add-opens module/package=caller-module` cũng có thể cấp quyền mở tương ứng, nhưng cấu hình đó phải được quản lý rõ ràng và không nên dùng như cách “sửa nhanh” cho một API thiếu thiết kế.

**Đừng chuyển logic từ ví dụ trên thành “private constructor luôn gọi được bằng setAccessible(true)”.** Đó không phải cam kết của JPMS.

## 7. Các ngoại lệ thường gặp theo từng bước

| Bước | Lỗi/kết quả | Cách hiểu |
| --- | --- | --- |
| `getConstructor(String.class)` | `NoSuchMethodException` | Không có **public** constructor với signature yêu cầu; private constructor có thể vẫn tồn tại |
| `getDeclaredConstructor(...)` | `NoSuchMethodException` | Không có constructor khai báo trực tiếp với danh sách kiểu tham số chính xác |
| `ctor.newInstance(...)` khi access check từ chối | `IllegalAccessException` | Caller không được phép gọi constructor trong trạng thái hiện tại |
| `ctor.trySetAccessible()` | `false` | Không thể bật access-check override cho caller, thường do ranh giới JPMS |
| `ctor.setAccessible(true)` | `InaccessibleObjectException` | Yêu cầu mở quyền vượt khả năng được JPMS cho phép |
| `ctor.newInstance(...)` sai số lượng/kiểu tham số | `IllegalArgumentException` | Không tương thích signature hoặc receiver/đối số theo contract |
| `ctor.newInstance(...)` khi constructor body ném | `InvocationTargetException` | Xem `getCause()` để lấy lỗi gốc do constructor đích ném |
| `ctor.newInstance(...)` với abstract class | `InstantiationException` | Kiểu không thể trực tiếp khởi tạo thành instance |
| Khởi tạo class thất bại trong quá trình thực thi | Có thể có `ExceptionInInitializerError` | Lỗi xảy ra khi JVM chạy phần khởi tạo static, tách khỏi quyền constructor |

`IllegalAccessException` và `InvocationTargetException` là hai tình huống khác nhau: tình huống đầu tiên thường có nghĩa chưa được phép chạy constructor; tình huống thứ hai cho biết constructor đích đã được gọi và logic bên trong ném lỗi. Một chương trình xử lý Reflection tốt không nên bắt hết `Exception` rồi báo “không có quyền”.

Các constructor của **enum** còn có ràng buộc riêng: Reflection không được dùng `Constructor.newInstance` để tạo enum constant mới, ngay cả khi đã tìm được metadata của constructor. Constructor thuộc **non-static inner class** cũng có thể chứa tham số ẩn đại diện instance của outer class; signature tra cứu phải phù hợp với thực tế do compiler tạo. Đây là lý do xem `getParameterTypes()` thay vì đoán signature.

## 8. Private constructor trong singleton, utility class và framework

### Utility class

Một lớp chỉ có các static method thường dùng private constructor để ngăn khởi tạo thông thường:

~~~java
public final class TextUtils {
    private TextUtils() {
        throw new AssertionError("Utility class");
    }

    public static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
~~~

Mẫu này không biến lớp thành “bất khả khởi tạo dưới mọi hình thức”. Nếu code có quyền Reflection hợp lệ để vào constructor private, lời gọi `newInstance()` sẽ chạy thân constructor và ném `AssertionError`, được bọc qua `InvocationTargetException`. Vì vậy private constructor không nên được xem như một ranh giới bảo mật độc lập.

### Singleton/factory

Một lớp singleton hoặc factory có thể dựa vào private constructor để kiểm soát số instance và validation. Deep reflection có thể vượt qua **rào cản truy cập ngôn ngữ** khi package được mở, nhưng không tự bỏ qua validation bên trong constructor. Nếu dự án cần bảo đảm invariant hoặc identity tuyệt đối, phải thiết kế bất biến, lifecycle và cơ chế quản lý phù hợp; không đặt toàn bộ tính đúng đắn vào modifier `private`.

### Framework và dependency injection

Một số framework cần tạo object từ constructor non-public. Chúng thường:

1. Tìm constructor phù hợp qua `getDeclaredConstructors()` hoặc `getDeclaredConstructor(...)`.
2. Chọn constructor theo annotation/signature hoặc quy tắc thiết kế cụ thể.
3. Kiểm tra khả năng truy cập; nếu cần deep reflection thì yêu cầu `opens` cho framework module.
4. Gọi `Constructor.newInstance(...)` với đối số đúng và báo lỗi có phân biệt quyền truy cập, chữ ký sai, hoặc exception từ body.

Không nên mặc định framework nào cũng có thể mở private constructor của mọi library/JDK class; trong named module, cấu hình mở package là một phần của hợp đồng vận hành.

## 9. So sánh `Constructor.newInstance` với `Class.newInstance`

`Class.newInstance()` là API cũ và đã **deprecated**. Nó yêu cầu constructor không đối số phù hợp và cách xử lý exception từ constructor không rõ ràng như API `Constructor.newInstance`.

Cách hiện đại nên dùng khi có yêu cầu Reflection:

~~~java
Constructor<MyType> ctor = MyType.class.getDeclaredConstructor();
MyType item = ctor.newInstance();
~~~

Đây là mảnh mã minh họa, giả định `MyType` tồn tại và có constructor không đối số; không phải chương trình độc lập. Nếu constructor đó private và caller không có quyền, vẫn cần xét `canAccess(null)` và khả năng mở quyền. `Constructor.newInstance` bọc exception từ constructor body trong `InvocationTargetException`, giúp phân biệt lỗi logic đích với lỗi truy cập.

Trong mã ứng dụng bình thường, nếu public factory hoặc cú pháp `new` đáp ứng được mục tiêu, cách gọi trực tiếp vẫn dễ đọc và dễ kiểm soát hơn. Chỉ dùng Reflection khi có lý do kỹ thuật rõ ràng như DI, serialization, testing hoặc tooling.

## 10. Checklist thực hành và tự kiểm tra

1. **Biết chính xác class đích:** constructor không kế thừa; dùng đúng `Class<?>` của lớp muốn tạo.
2. **Biết signature:** dùng `getDeclaredConstructor(int.class, String.class)` nếu constructor thực tế nhận `int, String`; `Integer.class` không tương đương `int.class` trong bước tìm.
3. **Phân biệt public lookup và declared lookup:** `getConstructor` không thấy private; `getDeclaredConstructor` có thể thấy nhưng không mở quyền.
4. **Kiểm tra quyền từ caller thực tế:** `ctor.canAccess(null)` và khả năng `trySetAccessible()`; không đoán kết quả chỉ từ modifier.
5. **Tôn trọng package và JPMS:** nếu cần truy cập non-public xuyên module, kiểm tra `opens` có đích và dùng API được phép.
6. **Xử lý lỗi tách biệt:** `NoSuchMethodException` là tra cứu; `IllegalAccessException` là truy cập; `InvocationTargetException.getCause()` là lỗi từ constructor body; `IllegalArgumentException` thường liên quan signature/arguments.
7. **Tránh coi private là bảo mật tuyệt đối:** Reflection hợp lệ có thể vượt rào cản truy cập ngôn ngữ; invariants của object cần được bảo vệ ngay trong thiết kế.

**Câu hỏi 1.** `getDeclaredConstructor(String.class)` trả về `Constructor` của private constructor. Có được gọi `newInstance("x")` ngay không? **Chưa chắc**: phải xét quyền của caller và trạng thái access-check override.

**Câu hỏi 2.** Vì sao `getConstructor(String.class)` ném `NoSuchMethodException` còn `getDeclaredConstructor(String.class)` thành công? **Vì thứ nhất chỉ tìm public constructor, thứ hai tìm constructor khai báo trực tiếp ở mọi mức truy cập.**

**Câu hỏi 3.** `trySetAccessible()` trả `false` khi framework khác module truy cập private constructor. Cách xử lý? **Dùng API hợp lệ hoặc mở chính xác package qua `opens` theo thiết kế module; không tiếp tục gọi như thể override đã bật.**

**Câu hỏi 4.** Constructor tự ném `IllegalArgumentException`. Lúc `Constructor.newInstance` chạy, caller quan sát loại exception nào? **`InvocationTargetException`**, và exception gốc được lấy qua `getCause()`.

**Câu hỏi 5.** Có cần `canAccess(someObject)` trước khi gọi constructor không? **Không.** Constructor chưa có receiver; gọi `canAccess(null)`.

Khi thao tác private constructor bằng Java Reflection, hãy theo đúng trình tự **tìm constructor → xác định quyền và ranh giới module → cân nhắc mở quyền → gọi với tham số hợp lệ → xử lý exception từ constructor**. Sự phân tách này giúp code Reflection đúng về ngữ nghĩa, minh bạch về quyền và dễ bảo trì.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="constructors-006">Không khởi tạo abstract class hoặc interface bằng constructor thông thường</a>

<details>
<summary>Xem chi tiết</summary>

# Không khởi tạo abstract class hoặc interface bằng constructor thông thường trong Java Reflection

## 1. Hiểu đúng giới hạn của constructor

Trong Java, **abstract class** là lớp chưa được phép tạo đối tượng trực tiếp; **interface** là hợp đồng kiểu, không phải lớp có constructor thông thường. Reflection có thể đọc metadata của các kiểu này nhưng **không làm chúng trở thành kiểu có thể khởi tạo trực tiếp**.

```java
abstract class Animal {
    protected Animal() { }
    abstract String sound();
}

interface Movable {
    void move();
}
```

Những lệnh sau đều không hợp lệ tại thời điểm biên dịch:

```java
// MÃ MINH HỌA KHÔNG BIÊN DỊCH ĐƯỢC
// Animal animal = new Animal();    // Animal là abstract class.
// Movable movable = new Movable(); // Movable là interface.
```

Tương tự, dùng Reflection để tìm `Class` hay constructor **không thể bỏ qua** hạn chế này. Phải tạo một **lớp cụ thể (concrete class)** có thể khởi tạo, rồi tham chiếu đối tượng đó qua kiểu abstract class/interface nếu cần.

**Phân biệt hai trường hợp:**

- Abstract class **có thể khai báo constructor**; constructor đó được thực thi trong quá trình xây dựng một đối tượng của lớp con cụ thể. Tuy nhiên, `Constructor` của abstract class **không thể** tự tạo instance của chính abstract class.
- Interface **không có constructor**. Không thể tìm `Constructor<Interface>` của interface, kể cả bằng `getDeclaredConstructor()`.

## 2. Những API Reflection liên quan

| Thao tác | Abstract class | Interface |
| --- | --- | --- |
| `SomeType.class`, `Class.forName(...)` | Lấy metadata `Class` bình thường | Lấy metadata `Class` bình thường |
| `Class.isInterface()` | `false` | `true` |
| `Modifier.isAbstract(type.getModifiers())` | `true` | Cũng thường `true` vì interface có cờ `abstract` |
| `getDeclaredConstructors()` | Có thể trả về các constructor khai báo trong abstract class | Mảng rỗng |
| `getDeclaredConstructor(parameterTypes...)` | Có thể tìm constructor khớp chữ ký | Ném `NoSuchMethodException` |
| `getConstructor(parameterTypes...)` | Có thể tìm constructor `public` của abstract class | Ném `NoSuchMethodException` |
| `Constructor.newInstance(args...)` | Không tạo được abstract class; có thể ném `InstantiationException` | Không có constructor interface để gọi |
| Khởi tạo lớp triển khai cụ thể | Được, qua constructor của **lớp con concrete** | Được, qua constructor của **lớp implement concrete** |

`getDeclaredConstructor(...)` chỉ **tra cứu khai báo**, còn `Constructor.newInstance(...)` mới **yêu cầu JVM tạo đối tượng**. Tìm được constructor không đồng nghĩa việc gọi constructor đó để tạo instance là hợp lệ.

Các constructor **không được kế thừa**: `Child.class.getConstructor(...)` chỉ tra cứu constructor công khai được khai báo cho `Child` theo quy tắc API, không dùng constructor của `Parent` để tạo `Child`. Đối tượng lớp con được xây dựng bằng constructor của lớp con và constructor lớp cha được gọi trong chuỗi `super(...)`.

## 3. Tại sao abstract class vẫn có constructor?

Constructor của abstract class phục vụ phần trạng thái chung cần khởi tạo cho mọi lớp con.

```java
abstract class Job {
    protected final String name;

    protected Job(String name) {
        this.name = name;
    }

    abstract String run();
}

class EmailJob extends Job {
    EmailJob(String name) {
        super(name); // Gọi constructor của phần Job trong đối tượng EmailJob.
    }

    @Override
    String run() {
        return "EmailJob: " + name;
    }
}
```

Tại `new EmailJob("daily")`, JVM tạo đối tượng có **runtime class `EmailJob`**, đồng thời thực thi `Job(String)` trong chuỗi khởi tạo. Không hề có đối tượng độc lập với runtime class chính xác là abstract `Job`.

Reflection tìm được `Job.class.getDeclaredConstructor(String.class)`, nhưng **gọi `newInstance("daily")` trên chính `Constructor<Job>` đó không tạo một `EmailJob`**. JVM không đoán giúp lớp con. Nó từ chối tạo instance của kiểu abstract.

## 4. Ví dụ Java 17+ đầy đủ và chạy được

Lưu nguyên chương trình sau dưới tên `AbstractConstructorDemo.java`:

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

public class AbstractConstructorDemo {
    public static abstract class Job {
        protected final String name;

        public Job(String name) {
            this.name = name;
        }

        public abstract String run();
    }

    public interface Work {
        String run();
    }

    public static final class RealJob extends Job implements Work {
        public RealJob(String name) {
            super(name);
        }

        @Override
        public String run() {
            return "run:" + name;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Job abstract: "
                + Modifier.isAbstract(Job.class.getModifiers()));

        Constructor<Job> baseCtor =
                Job.class.getDeclaredConstructor(String.class);
        System.out.println("Job constructor parameters: "
                + baseCtor.getParameterCount());

        try {
            baseCtor.newInstance("x");
        } catch (InstantiationException e) {
            System.out.println("Job newInstance: InstantiationException");
        }

        System.out.println("Work interface: " + Work.class.isInterface());
        System.out.println("Work constructors: "
                + Work.class.getDeclaredConstructors().length);

        try {
            Work.class.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            System.out.println("Work lookup: NoSuchMethodException");
        }

        Constructor<RealJob> realCtor =
                RealJob.class.getConstructor(String.class);
        Job asJob = realCtor.newInstance("demo");
        Work asWork = (Work) asJob;

        System.out.println("concrete class: "
                + asJob.getClass().getSimpleName());
        System.out.println("job call: " + asJob.run());
        System.out.println("interface call: " + asWork.run());
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac AbstractConstructorDemo.java
java AbstractConstructorDemo
```

Stdout:

```text
Job abstract: true
Job constructor parameters: 1
Job newInstance: InstantiationException
Work interface: true
Work constructors: 0
Work lookup: NoSuchMethodException
concrete class: RealJob
job call: run:demo
interface call: run:demo
```

**Giải thích kết quả:**

1. `Modifier.isAbstract(Job.class.getModifiers())` xác nhận `Job` là abstract.
2. `Job.class.getDeclaredConstructor(String.class)` vẫn tìm thấy constructor có một tham số.
3. `baseCtor.newInstance("x")` thất bại với `InstantiationException` vì kiểu khai báo là abstract; constructor có tồn tại và được tra cứu đúng cũng không thay đổi điều đó.
4. `Work.class.isInterface()` trả `true`; `getDeclaredConstructors()` có kích thước bằng `0`. Tra cứu constructor không tham số của interface ném `NoSuchMethodException`.
5. `RealJob` là lớp cụ thể với constructor `public RealJob(String)`. `realCtor.newInstance("demo")` tạo một đối tượng thật sự có runtime class `RealJob`.
6. Đối tượng `RealJob` có thể được tham chiếu bằng `Job` và `Work`. Kiểu của biến tham chiếu **không phải** bằng chứng rằng JVM đã tạo trực tiếp instance của abstract class/interface.

Trong ví dụ, constructor của `Job` được khai báo `public` để việc truy cập không che lấp lỗi cần học. Nếu constructor abstract class là `protected` hoặc `private`, quy tắc truy cập Reflection có thể gây `IllegalAccessException` hay lỗi quyền khác **trước khi** chạm đến bước kiểm tra tính khởi tạo được.

## 5. Ba nhóm lỗi cần phân biệt

Khi làm việc với constructor, lỗi có thể xảy ra ở **những giai đoạn khác nhau**:

| Giai đoạn | Trường hợp | Kết quả thường gặp |
| --- | --- | --- |
| Tra cứu | `Interface.class.getDeclaredConstructor()` | `NoSuchMethodException` vì interface không có constructor |
| Tra cứu | Tìm sai chữ ký constructor của một lớp, ví dụ `RealJob.class.getConstructor(int.class)` | `NoSuchMethodException` vì chỉ có `RealJob(String)` |
| Tạo instance | `Job.class.getDeclaredConstructor(String.class).newInstance("x")` | `InstantiationException` vì `Job` là abstract |
| Quyền truy cập | Có constructor nhưng caller không được phép dùng nó | `IllegalAccessException` hoặc lỗi truy cập tùy ngữ cảnh |
| Kiểm tra đối số | Số lượng/kiểu đối số không hợp lệ khi gọi `newInstance` | `IllegalArgumentException` |
| Chạy constructor | Constructor concrete thực thi và ném exception | `InvocationTargetException` chứa nguyên nhân gốc |

Đừng bắt duy nhất `NoSuchMethodException` cho toàn bộ quy trình. Lỗi đó thuộc **bước tìm**; `InstantiationException` xảy ra ở **bước tạo**, khi metadata constructor đã có rồi.

`Constructor.newInstance(...)` có thể ném các checked exceptions như `InstantiationException`, `IllegalAccessException`, `InvocationTargetException`, bên cạnh exception do đối số/quyền truy cập. Khi viết code thư viện, hãy xử lý theo ý nghĩa của từng lỗi thay vì cố thử đi thử lại cùng một constructor trên abstract class.

## 6. Kiểm tra kiểu trước khi chọn constructor

Khi class được cung cấp qua cấu hình, annotation scanning hoặc `Class.forName`, hãy kiểm tra tính cụ thể của kiểu trước khi thử khởi tạo:

```java
// Hàm độc lập đặt trong một lớp Java; chỉ minh họa bước kiểm tra.
static boolean isConcreteClass(Class<?> type) {
    int modifiers = type.getModifiers();
    return !type.isInterface()
            && !java.lang.reflect.Modifier.isAbstract(modifiers)
            && !type.isPrimitive()
            && !type.isArray()
            && !type.isAnnotation();
}
```

Bước này phân biệt abstract class và interface với lớp bình thường nhưng **không đủ để bảo đảm mọi `Class<?>` còn lại tạo instance được**. Ví dụ: lớp không có constructor phù hợp; constructor không truy cập được; enum bị hạn chế tạo instance bằng Reflection; `void.class` cũng không biểu diễn lớp có thể khởi tạo. `Class.isInterface()` là tín hiệu trực tiếp để phân biệt interface, vì chỉ kiểm tra `Modifier.isAbstract(...)` sẽ gộp interface với abstract class.

Một quy trình thực tế hơn:

1. Kiểm tra `type.isInterface()` và `Modifier.isAbstract(type.getModifiers())`. Nếu một trong hai đúng, **không dùng constructor trên chính kiểu đó**.
2. Xác định concrete class cần tạo từ cấu hình hay contract đã biết; kiểm tra `expectedType.isAssignableFrom(concreteType)` để xác nhận tương thích.
3. Dùng `concreteType.getDeclaredConstructor(parameterTypes...)` hoặc `getConstructor(...)` theo phạm vi cần tra, và truyền chính xác kiểu tham số.
4. Kiểm tra khả năng truy cập constructor; không mặc định có quyền mở module/package nội bộ.
5. Gọi `newInstance(args...)` trên constructor **của concrete class** và xử lý các lỗi thực thi.

**Không suy diễn** rằng chọn lớp con chỉ bằng cách gọi `getConstructor` trên abstract class: việc tìm subtype cụ thể là trách nhiệm của ứng dụng hoặc container/DI, không phải của `Constructor.newInstance`.

## 7. Dùng abstract class/interface như kiểu tham chiếu hoàn toàn hợp lệ

Một số đoạn code có thể khiến người mới hiểu nhầm rằng “đã tạo interface” hoặc “đã tạo abstract class”.

### 7.1. Concrete subclass

```java
// Khi đã có lớp RealJob trong chương trình đầy đủ:
Job job = new RealJob("billing");
Work work = (Work) job;
```

`new` khởi tạo `RealJob`. Hai biến chỉ giữ tham chiếu được gán theo quy tắc kế thừa/implements.

### 7.2. Anonymous subclass của abstract class

```java
// Đặt trong phương thức có thể truy cập AbstractConstructorDemo.Job:
AbstractConstructorDemo.Job anonymous =
        new AbstractConstructorDemo.Job("one-off") {
            @Override
            public String run() {
                return "anonymous:" + name;
            }
        };
System.out.println(anonymous.run()); // anonymous:one-off
```

Cú pháp `new AbstractConstructorDemo.Job(...) { ... }` tạo **lớp con ẩn danh cụ thể**, có phần cài đặt phương thức abstract. Nó **không** tạo trực tiếp đối tượng có runtime class là `Job`. Mảnh code này minh họa, không phải một chương trình độc lập.

### 7.3. Lambda cho functional interface

```java
// Work là interface có đúng một abstract method như chương trình ở mục 4.
AbstractConstructorDemo.Work action = () -> "lambda";
System.out.println(action.run()); // lambda
```

Lambda cung cấp một đối tượng triển khai functional interface theo cơ chế của Java; **interface `Work` không tự có constructor**. Nếu interface có nhiều abstract methods và không phải functional interface, cú pháp lambda sẽ không hợp lệ.

### 7.4. Dynamic Proxy

`java.lang.reflect.Proxy.newProxyInstance(...)` có thể tạo một **đối tượng proxy thực thi interface** theo `InvocationHandler`, nhưng đối tượng có runtime class là lớp proxy được runtime tạo ra, không phải chính interface. Đây là kỹ thuật tạo implementation động, **khác** với việc gọi constructor của interface. Không dùng proxy như cách mặc định để thay thế thiết kế concrete implementation khi không cần hành vi động.

## 8. Vai trò của constructor cha khi có kế thừa

Thứ tự khởi tạo chuẩn khi gọi `new RealJob("demo")`:

```text
RealJob(String) được chọn làm constructor đích
    |
    v
super(name) gọi Job(String)
    |
    v
Job khởi tạo name
    |
    v
RealJob hoàn tất xây dựng
    |
    v
Đối tượng có runtime class RealJob
```

Đó là lý do abstract class cần và có thể có constructor: **lớp cha phải thiết lập phần trạng thái chung** cho đối tượng lớp con. Java cấm **trực tiếp khởi tạo abstract class**, nhưng không cấm constructor của nó tham gia chuỗi khởi tạo hợp lệ.

Một lưu ý thiết kế: constructor abstract class không nên gọi các phương thức overridable nếu các phương thức ấy phụ thuộc trạng thái mà lớp con chưa khởi tạo. Quy tắc dispatch động có thể kích hoạt implementation của lớp con quá sớm trong lúc constructor cha đang chạy.

## 9. Lỗi thường gặp và cách xử lý

| Hiện tượng / ngộ nhận | Nguyên nhân | Cách xử lý |
| --- | --- | --- |
| `getDeclaredConstructor` tìm thấy nhưng `newInstance` ném `InstantiationException` | Đang dùng `Constructor` của abstract class | Chọn constructor của concrete subclass |
| `Interface.class.getDeclaredConstructor()` ném `NoSuchMethodException` | Interface không khai báo constructor | Chọn lớp implement hoặc cơ chế tạo implementation phù hợp |
| Nghĩ abstract class không được có constructor | Nhầm giới hạn tạo object với khai báo constructor | Constructor abstract class phục vụ xây dựng subclass |
| Nghĩ gọi `abstractType.getConstructor(...)` sẽ tự tìm subclass | Reflection lookup không tự chọn lớp con | Xác định concrete class tường minh |
| Nghĩ `Class.forName("...")` khởi tạo được interface | `Class.forName` lấy `Class`/nạp và có thể khởi tạo lớp theo ngữ nghĩa API, không tạo instance của kiểu | Phân biệt metadata `Class` với đối tượng triển khai |
| Kiểm tra `Modifier.isAbstract` để phân biệt hai loại | Interface cũng mang modifier `abstract` | Kiểm tra `isInterface()` trước |
| Constructor lớp con không gọi constructor cha | Constructor Java bắt buộc dẫn tới gọi `super(...)` hoặc `this(...)`, trực tiếp hay ngầm định | Xem chuỗi constructor và chữ ký constructor cha |
| Dùng `Class.newInstance()` cũ để tránh lỗi | API cũ đã deprecated; không vượt được abstract/interface | Dùng `getDeclaredConstructor(...).newInstance(...)` trên kiểu concrete |
| Tạo được proxy rồi kết luận interface có constructor | Proxy tạo lớp triển khai động | Kiểm tra `object.getClass()`; hiểu proxy là implementation |

**Ghi nhớ:** **Abstract class có thể có constructor nhưng không được tạo instance trực tiếp; interface không có constructor.** Reflection phản ánh đúng hai quy tắc ấy. Luôn lấy `Constructor` của một **lớp cụ thể có thể khởi tạo**, rồi dùng abstract class/interface làm kiểu tham chiếu nếu phù hợp.

Tham khảo: [Java SE 17 `Class`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html), [`java.lang.reflect.Constructor`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Constructor.html), [JLS §8.1.1.1 — abstract Classes](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.1.1.1).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="constructors-007">Constructor của non-static inner class và tham số đối tượng bao ngoài</a>

<details>
<summary>Xem chi tiết</summary>

# Constructor của non-static inner class và tham số đối tượng bao ngoài

## 1. Bản chất vấn đề

Trong Java, một **non-static member class** (lớp thành viên không có `static`), còn gọi là *non-static inner class*, gắn mỗi đối tượng của nó với một **immediately enclosing instance**: một đối tượng của lớp bao ngoài trực tiếp. Đối tượng bao ngoài cho phép inner class dùng `Outer.this` và truy cập các thành viên instance của outer class, kể cả `private` theo các quy tắc ngôn ngữ.

Ví dụ nguồn:

```java
class Outer {
    private final String prefix;

    Outer(String prefix) {
        this.prefix = prefix;
    }

    class Inner {
        private final int id;

        Inner(int id) {
            this.id = id;
        }

        String description() {
            return Outer.this.prefix + "-" + id;
        }
    }
}
```

Cách tạo thông thường:

```java
Outer outer = new Outer("A");
Outer.Inner inner = outer.new Inner(5);
```

Constructor trong mã nguồn viết `Inner(int id)` và lời gọi Java dùng `outer.new Inner(5)`. Nhưng khi Reflection tìm constructor tương ứng, danh sách kiểu tham số còn có **tham số đứng đầu đại diện cho `Outer`**:

```java
Constructor<Outer.Inner> constructor =
        Outer.Inner.class.getDeclaredConstructor(Outer.class, int.class);
Outer.Inner reflected = constructor.newInstance(outer, 5);
```

Những đoạn code trên là **mã minh họa**: cần `import java.lang.reflect.Constructor;` và khai báo checked exceptions cho lời gọi Reflection. Ví dụ hoàn chỉnh có lệnh biên dịch ở mục 4.

Ý chính: **mã nguồn chỉ viết một tham số `int`, nhưng Reflection nhận diện constructor của inner class này có hai tham số runtime: `Outer` trước, rồi `int`**. `outer` phải là một instance phù hợp, không phải `Outer.class` hay tên class dưới dạng chuỗi.

## 2. Hai cách viết tương ứng

| Góc nhìn | Ví dụ | Enclosing instance đi đâu? |
| --- | --- | --- |
| Java source | `outer.new Inner(5)` | Gắn với biểu thức `outer` đứng trước `.new` |
| Reflection — tra cứu | `getDeclaredConstructor(Outer.class, int.class)` | Kiểu `Outer` trở thành kiểu tham số thứ nhất |
| Reflection — tạo đối tượng | `constructor.newInstance(outer, 5)` | Giá trị `outer` trở thành đối số thứ nhất |
| Reflection — lấy danh sách kiểu | `constructor.getParameterTypes()` | Trả kiểu `Outer` rồi `int` |
| Reflection — đếm tham số | `constructor.getParameterCount()` | Trả `2` trong ví dụ |

**Đừng nhầm hai tham số của hai API:**

- `getDeclaredConstructor(Outer.class, int.class)` nhận **kiểu của các tham số** (đối tượng `Class<?>`) để tìm đúng constructor.
- `newInstance(outer, 5)` nhận **giá trị của các đối số** (object reference `outer` và giá trị `int` được boxing khi truyền vào `Object...`).

`getDeclaredConstructor(int.class)` sẽ **không tìm được** constructor này, dù nguồn ghi `Inner(int)`. Reflection không tự thêm đối tượng bao ngoài khi tìm constructor hoặc gọi `newInstance`.

### Vì sao compiler cần tham số bổ sung?

Cú pháp `outer.new Inner(5)` đã chỉ rõ đối tượng bao ngoài. Ở biểu diễn runtime, cần cách để chuyển tham chiếu này vào lúc khởi tạo. Constructor của non-static inner class nhận tham chiếu outer như đối số bổ sung ở đầu danh sách, rồi mới tới những tham số được khai báo trong nguồn. Cơ chế này được định nghĩa bởi hợp đồng Reflection và ngữ nghĩa inner-class của Java.

Một Java compiler thường dùng field ẩn, chẳng hạn `this$0`, để giữ tham chiếu outer. Tuy nhiên, **`this$0` là chi tiết biên dịch không được bảo đảm là tên field ổn định**. Compiler/version/việc inner class có thực sự dùng outer state hay không có thể ảnh hưởng bytecode. Khi tra cứu và gọi constructor bằng Reflection, dựa trên **constructor signature và hành vi chuẩn**, không tự phụ thuộc tên field mà compiler sinh ra.

## 3. Phân biệt non-static inner với static nested class

`static nested class` cũng được khai báo bên trong một class nhưng **không cần enclosing instance**. Nó không có tham số constructor ẩn chỉ để mang object của outer.

```java
class Outer {
    class Inner {
        Inner(int number) {}
    }

    static class Nested {
        Nested(int number) {}
    }
}
```

Cách gọi thông thường:

```java
Outer outer = new Outer();
Outer.Inner inner = outer.new Inner(10);
Outer.Nested nested = new Outer.Nested(10);
```

Cách tra cứu bằng Reflection:

```java
Constructor<?> innerCtor =
        Outer.Inner.class.getDeclaredConstructor(Outer.class, int.class);
Constructor<?> nestedCtor =
        Outer.Nested.class.getDeclaredConstructor(int.class);
```

| Khía cạnh | `Outer.Inner` | `Outer.Nested` |
| --- | --- | --- |
| Có modifier `static`? | Không | Có |
| Liên kết với `Outer` instance? | Có | Không |
| Cách tạo bằng Java | `outer.new Inner(10)` | `new Outer.Nested(10)` |
| Tham số khai báo nguồn | `int` | `int` |
| Tham số constructor qua Reflection | `Outer, int` | `int` |
| Có cần truyền object `Outer` vào `newInstance`? | Có | Không |

**Cả hai đều là nested member classes.** `Class.getEnclosingClass()` và `Class.getDeclaringClass()` có thể trả `Outer.class` cho cả `Inner` lẫn `Nested`; chỉ dựa vào hai API đó **không đủ** để suy luận constructor cần tham số outer. Với member class, kết hợp `Class.isMemberClass()` và `Modifier.isStatic(clazz.getModifiers())`. Sau đó luôn kiểm tra signature constructor thực tế.

Một lớp top-level không có enclosing class/member relationship; một local/anonymous class có quy tắc compiler-generated parameter riêng. Không đồng nhất tất cả dạng nested class thành một kiểu constructor.

## 4. Chương trình Java 17+ chạy được

Tạo tệp **`InnerConstructorDemo.java`** với nội dung:

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

public class InnerConstructorDemo {

    public static class Outer {
        private final String prefix;

        public Outer(String prefix) {
            this.prefix = prefix;
        }

        public class Inner {
            private final int number;

            public Inner(int number) {
                this.number = number;
            }

            public String label() {
                return prefix + "-" + number;
            }
        }

        public static class Nested {
            private final int number;

            public Nested(int number) {
                this.number = number;
            }

            public String label() {
                return "N-" + number;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        Outer outerA = new Outer("A");
        Outer.Inner direct = outerA.new Inner(5);
        System.out.println("direct: " + direct.label());

        Class<Outer.Inner> innerType = Outer.Inner.class;
        System.out.println("inner is member: " + innerType.isMemberClass());
        System.out.println("inner static: "
                + Modifier.isStatic(innerType.getModifiers()));
        System.out.println("inner declaring class: "
                + (innerType.getDeclaringClass() == Outer.class));

        Constructor<Outer.Inner> innerCtor =
                innerType.getDeclaredConstructor(Outer.class, int.class);

        System.out.println("inner parameter count: "
                + innerCtor.getParameterCount());
        System.out.println("first is Outer: "
                + (innerCtor.getParameterTypes()[0] == Outer.class));
        System.out.println("second is int: "
                + (innerCtor.getParameterTypes()[1] == int.class));

        try {
            innerType.getDeclaredConstructor(int.class);
        } catch (NoSuchMethodException e) {
            System.out.println("missing Outer lookup: NoSuchMethodException");
        }

        Outer outerB = new Outer("B");
        Outer.Inner reflected = innerCtor.newInstance(outerB, 9);
        System.out.println("reflected: " + reflected.label());

        Constructor<Outer.Nested> nestedCtor =
                Outer.Nested.class.getDeclaredConstructor(int.class);
        System.out.println("nested static: "
                + Modifier.isStatic(Outer.Nested.class.getModifiers()));
        System.out.println("nested parameter count: "
                + nestedCtor.getParameterCount());
        Outer.Nested nested = nestedCtor.newInstance(4);
        System.out.println("nested: " + nested.label());
    }
}
```

Biên dịch, chạy từ thư mục chứa file bằng JDK 17 trở lên:

```bash
javac InnerConstructorDemo.java
java InnerConstructorDemo
```

**Stdout:**

```text
direct: A-5
inner is member: true
inner static: false
inner declaring class: true
inner parameter count: 2
first is Outer: true
second is int: true
missing Outer lookup: NoSuchMethodException
reflected: B-9
nested static: true
nested parameter count: 1
nested: N-4
```

**Đọc kết quả:**

1. `outerA.new Inner(5)` sử dụng implicit enclosing instance `outerA`, do đó trả `A-5`.
2. `Outer.Inner.class` là member class, không có modifier `static`; lớp khai báo bao ngoài là `Outer`.
3. Constructor mà Reflection thấy là `(Outer, int)`, nên `getParameterCount()` bằng `2`. `getDeclaredConstructor(int.class)` ném `NoSuchMethodException`; đây là lỗi **tra cứu chữ ký**, chưa phải lỗi gọi constructor.
4. `innerCtor.newInstance(outerB, 9)` tạo inner object mới gắn với `outerB`. Method `label()` sử dụng `prefix` của `outerB` và in `B-9`, chứng minh tham số đầu là **giá trị enclosing instance**, không phải chỉ một marker trong signature.
5. `Outer.Nested` có `static`, không cần enclosing instance. Reflection tìm constructor với duy nhất `int.class` và gọi `newInstance(4)`.

Không cần `setAccessible(true)` trong ví dụ vì các nested types và constructors được khai báo `public` trong ngữ cảnh truy cập này. Cũng không cần dựng một `Outer` object chỉ để tạo `Outer.Nested`.

## 5. Constructor không có tham số tường minh vẫn cần outer

Một hiểu nhầm phổ biến là inner class có constructor không đối số trong nguồn thì Reflection dùng `getDeclaredConstructor()` trống.

```java
class Book {
    class Page {
        Page() {}
    }
}
```

Để tra cứu constructor của `Page`:

```java
Constructor<Book.Page> constructor =
        Book.Page.class.getDeclaredConstructor(Book.class);
Book book = new Book();
Book.Page page = constructor.newInstance(book);
```

Đây là **đoạn minh họa** phải đặt trong scope có quyền truy cập package-private constructor và có xử lý checked exceptions. Dù mã nguồn viết `Page()`, Reflective constructor vẫn có tham số đầu `Book`.

Nếu gọi `Book.Page.class.getDeclaredConstructor()`, bạn sẽ gặp `NoSuchMethodException` đối với chương trình này. Nếu tìm đúng constructor nhưng gọi `constructor.newInstance()` thiếu đối số outer, kết quả là `IllegalArgumentException` vì không đủ argument. Hai lỗi xảy ra ở **hai bước khác nhau**.

### Ví dụ inner class với nhiều tham số

```java
class Server {
    class Connection {
        Connection(String host, int port) {}
    }
}
```

Chữ ký constructor qua Reflection có dạng:

```java
Server.Connection.class.getDeclaredConstructor(
        Server.class, String.class, int.class);
```

Đầu tiên là **lớp bao ngoài trực tiếp**, tiếp đó là `String` và `int` được khai báo trong nguồn, giữ đúng thứ tự. Bên gọi phải truyền `Server` instance, `String` và `int`.

## 6. Enclosing instance khác gì `Class` metadata?

Ba cặp thông tin rất dễ bị lẫn:

- `Inner.class.getEnclosingClass()` cho biết **kiểu lớp bao quanh về mặt cấu trúc**. Nó trả đối tượng `Class<Outer>`, không phải một instance cụ thể của `Outer`.
- `Inner.class.getDeclaringClass()` cho biết **lớp khai báo member class** (đối với member class); cũng là metadata `Class`.
- Đối tượng outer thật mà inner object gắn vào là **giá trị tại runtime**, ví dụ `outerB`; có thể có vô số `Outer` instances và mỗi inner object gắn với một instance phù hợp.

`getEnclosingClass() == Outer.class` **không có nghĩa** Reflection có thể tự chọn instance outer. Bạn phải cung cấp đúng object để `Constructor.newInstance` tạo inner object. Hai `Outer` instance có thể cùng kiểu `Outer.class` nhưng mang trạng thái khác nhau, vì vậy không thể lấy `Outer.class` để thay cho reference thật.

**Không truyền `null` để giả lập outer object hợp lệ.** Hợp đồng constructor của non-static inner class yêu cầu một enclosing instance. Một số cách biên dịch/cơ chế thực thi có thể báo lỗi ở các bước khác nhau khi đưa `null`; đó không phải cách tạo một inner object liên kết hợp lệ và không nên dựa vào phản ứng ngoại lệ cụ thể của mọi compiler.

## 7. API Reflection nên dùng và các bước chính xác

### 7.1. `getDeclaredConstructor` hay `getConstructor`?

- `Class.getDeclaredConstructor(parameterTypes...)` tìm constructor được **khai báo trực tiếp**, bất kể modifier của constructor, theo đúng danh sách kiểu tham số.
- `Class.getConstructor(parameterTypes...)` tìm **public constructor** theo chữ ký. Với inner member class không static, danh sách kiểu vẫn phải có outer class ở vị trí đầu.
- `Class.getDeclaredConstructors()` trả toàn bộ constructor khai báo trực tiếp, hữu ích khi bạn chưa chắc signature. Đừng dựa vào thứ tự mảng constructors; hãy kiểm tra `getParameterTypes()` cho từng phần tử.
- `Constructor.getParameterTypes()` trả `Class<?>[]` theo thứ tự runtime; `getParameterCount()` cho số lượng tham số; `getGenericParameterTypes()` có thể giúp phân tích thông tin generic nếu metadata tương ứng được lưu và phản ánh phù hợp.

Mẫu tra cứu đáng tin:

```java
Class<?> inner = Outer.Inner.class;
for (Constructor<?> ctor : inner.getDeclaredConstructors()) {
    System.out.println(ctor.getParameterCount());
    // Xem ctor.getParameterTypes() để nhận diện đúng chữ ký.
}
```

Đây là đoạn minh họa, cần `import java.lang.reflect.Constructor;` và một lớp `Outer.Inner` hợp lệ. Nếu các constructor có overload, khớp **đầy đủ kiểu và thứ tự**, không chỉ đếm.

### 7.2. `Constructor.newInstance` thực sự làm gì?

`Constructor<T>.newInstance(Object... initargs)` tạo và khởi tạo một instance mới bằng constructor phản ánh. Với non-static inner member class, **đối số đầu tiên là enclosing instance**; các đối số kế tiếp tương ứng tham số được khai báo.

```java
Outer outer = new Outer("P");
Constructor<Outer.Inner> ctor =
        Outer.Inner.class.getDeclaredConstructor(Outer.class, int.class);
Outer.Inner child = ctor.newInstance(outer, 3);
```

Khác với `Class.newInstance()` cũ (đã deprecated), API `Constructor.newInstance` cho phép lựa chọn constructor theo tham số, cung cấp đúng đối số và bọc exception của constructor đích trong `InvocationTargetException`. Nó cũng giúp biểu diễn rõ ràng các lựa chọn truy cập.

### 7.3. Không tự động vượt qua quyền truy cập

Nếu inner class hoặc constructor không public, gọi `getDeclaredConstructor` để lấy metadata **không bảo đảm** `newInstance` có quyền sử dụng. Khi cần:

```java
Constructor<?> ctor = innerClass.getDeclaredConstructor(
        enclosingClass, int.class);

if (ctor.trySetAccessible()) {
    Object instance = ctor.newInstance(actualOuter, 3);
}
```

Đây là mẫu minh họa; `innerClass`, `enclosingClass` và `actualOuter` là các biến đã có sẵn. `trySetAccessible()` chỉ được cấp phép nếu Java access rules và JPMS cho phép. Với named module và deep reflection từ module khác, package có thể phải được `opens` đúng module của caller. Không xem việc truyền outer hợp lệ là quyền bỏ qua Java access checks.

## 8. `getParameters()`, tham số ẩn và metadata compiler

Một số chương trình mong có thể xác định tham số outer bằng:

```java
Parameter[] parameters = ctor.getParameters();
for (Parameter parameter : parameters) {
    System.out.println(parameter.getType());
    System.out.println(parameter.isImplicit());
    System.out.println(parameter.isSynthetic());
}
```

Đoạn minh họa này cần `java.lang.reflect.Parameter` và biến `ctor`. Các cờ như `isImplicit()`/`isSynthetic()` và tên tham số phụ thuộc metadata trong class file, đặc biệt `MethodParameters`, cũng như cấu trúc lớp và cách compiler tạo bytecode. **Không thể suy ra bảo đảm mọi compiler/phiên bản gắn cùng cờ hoặc cùng tên cho tham số outer chỉ từ việc thấy non-static inner class.**

Với `javac -parameters`, compiler thường ghi thêm tên/thuộc tính tham số để Reflection đọc được. Không có metadata tên, `Parameter.getName()` có thể trả tên tổng hợp như `arg0`; `isNamePresent()` cho biết có tên thật được lưu theo hợp đồng API hay không. Tên tham số outer có thể xuất hiện khác nhau, nên không làm công cụ dựa vào một tên như `this$0` hoặc `arg0`.

**Cách ổn định khi cần gọi constructor:** lấy danh sách `getParameterTypes()`, đối chiếu phần đầu với enclosing class và đưa đúng instance vào `newInstance`. Chỉ dùng metadata tham số để trình bày bổ sung, không thay cho signature.

### Kiểm tra bằng `javap`

Biên dịch `InnerConstructorDemo.java` rồi, trong thư mục có class file, có thể xem cấu trúc bằng lệnh:

```bash
javap -p 'InnerConstructorDemo$Outer$Inner'
javap -p 'InnerConstructorDemo$Outer$Nested'
```

Đây là ví dụ tham khảo cú pháp terminal hỗ trợ trích dẫn bằng dấu nháy đơn để ký tự `$` không bị diễn giải thành biến môi trường. **Trên Windows PowerShell**, cũng có thể dùng cách đặt tên lớp trong dấu nháy đơn. Với cấu trúc này, bạn sẽ thấy constructor của inner có tham số outer ở đầu, còn constructor của nested static không có tham số đó. Có thể chạy `javap -v -p` để xem metadata chi tiết hơn, nhưng không phụ thuộc tên field compiler tự sinh hoặc thứ tự hiển thị.

Công cụ đọc class file và Reflection phục vụ việc quan sát bytecode đã biên dịch; chúng không dựng lại nguyên vẹn cách viết mã nguồn.

## 9. Nested nhiều cấp và các lớp local/anonymous

### 9.1. Nested nhiều cấp

Giả sử:

```java
class Building {
    class Floor {
        class Room {
            Room(int number) {}
        }
    }
}
```

`Room` là inner member class trực tiếp nằm trong `Floor`. Khi dùng Reflection tìm constructor, tham số enclosing instance đầu tiên có kiểu **`Building.Floor`**:

```java
Constructor<Building.Floor.Room> ctor =
        Building.Floor.Room.class.getDeclaredConstructor(
                Building.Floor.class, int.class);
```

Muốn tạo `Room`, cần một `Floor` instance phù hợp; còn bản thân `Floor` instance gắn với một `Building` instance. Reflection không tự thêm lần lượt `Building` và `Floor` thành hai tham số riêng trong constructor của `Room` chỉ vì có hai tầng lexical nesting.

### 9.2. Local class và anonymous class không theo một khuôn

Local class nằm trong block và anonymous class có thể liên quan đến:

- Enclosing instance của non-static context;
- Các giá trị local hoặc tham số được capture (theo quy tắc effectively-final);
- Tham số bổ sung do compiler dùng để gọi superclass constructor hoặc thực hiện khởi tạo.

Chữ ký constructor thực tế của chúng có thể phức tạp và **phụ thuộc việc class được khai báo trong static hay non-static context, những giá trị được capture và compiler**. Không áp dụng máy móc công thức `Outer.class + các tham số nguồn` cho mọi local/anonymous class.

Trong bài này, công thức trọng tâm nhắm tới **non-static member inner class** như `Outer.Inner`. Đối với loại khác, kiểm tra `getEnclosingClass()`, `getEnclosingMethod()`, `getEnclosingConstructor()`, `isLocalClass()`, `isAnonymousClass()` và danh sách constructor thực tế; không giả định một chữ ký khi chưa quan sát.

### 9.3. Generic outer class

Ví dụ `Outer<String>.Inner` khi runtime vẫn có outer instance là object của `Outer`; kiểu tham số constructor phản ánh là `Outer.class` do type erasure, không thể tra cứu bằng cú pháp `Outer<String>.class` (Java không có class literal cho kiểu parameterized đó). Nếu cần kiểm tra generic metadata, hãy tìm `Signature`/Reflection generic APIs nếu được compiler lưu.

## 10. Lỗi thường gặp và hướng xử lý

| Hiện tượng | Nguyên nhân thường gặp | Cách sửa |
| --- | --- | --- |
| `NoSuchMethodException` khi tìm `Inner(int)` | Quên tham số outer đứng đầu signature | Tìm `Inner(Outer, int)` |
| `IllegalArgumentException` từ `newInstance(5)` | Thiếu enclosing instance khi gọi | `newInstance(outer, 5)` |
| `IllegalArgumentException` do sai kiểu đầu tiên | Đưa object khác `Outer` vào tham số outer | Truyền instance `Outer` đúng kiểu |
| `IllegalAccessException` | Caller thiếu quyền gọi constructor | Kiểm tra modifier, package, module; dùng cơ chế access hợp lệ |
| `InaccessibleObjectException` khi bật access | JPMS không cho deep reflection | Kiểm tra package `opens` tới module caller |
| `InvocationTargetException` | Constructor đích ném exception | Xem `getCause()` để tìm lỗi trong constructor |
| `InstantiationException` | Lớp khai báo constructor không thể khởi tạo trong điều kiện API | Kiểm tra kiểu/constructor đích; không dùng với abstract class |
| Nhận nhầm `Nested` là `Inner` | Chỉ kiểm tra `getEnclosingClass()`, bỏ qua `static` | Kiểm tra `isMemberClass()` và `Modifier.isStatic` |
| Đọc field `this$0` rồi gắn chặt logic vào tên này | Phụ thuộc compiler-generated implementation detail | Dựa vào constructor signature và `newInstance` |
| Cho rằng mọi anonymous/local class có tham số đầu giống member inner class | Khái quát hóa quá mức | Quan sát constructors thực tế và bối cảnh khai báo |

**Phân biệt thời điểm lỗi:** `getDeclaredConstructor(...)` có thể thất bại trong bước **tìm chữ ký**; `newInstance(...)` có thể thất bại trong bước **kiểm tra đối số/quyền hoặc chạy constructor**. Gặp lỗi nào hãy kiểm tra đúng bước đó trước.

## 11. Bài tự kiểm tra

Cho lớp sau:

```java
class Company {
    class Employee {
        Employee(String name, int level) {}
    }

    static class Department {
        Department(String name) {}
    }
}
```

Hãy dự đoán trước khi chạy:

1. `Company.Employee.class.getDeclaredConstructor(String.class, int.class)` có tìm được constructor không?
2. Danh sách kiểu tham số đúng cho `Company.Employee` là gì?
3. `Company.Department.class.getDeclaredConstructor(String.class)` có cần `Company.class` đứng đầu không?
4. Khi gọi constructor của `Employee` qua `newInstance`, tham số đầu tiên cần **kiểu** hay **giá trị**?
5. Nếu có hai `Company` instances khác nhau, Reflection tự chọn instance nào?
6. `Company.Employee.class.getDeclaringClass() == Company.class` có đủ để tạo `Employee` không?

**Đáp án:**

1. Không; thiếu `Company.class` trong lookup, nên `NoSuchMethodException`.
2. `(Company.class, String.class, int.class)`.
3. Không; `Department` là `static nested class`.
4. Đưa **giá trị `Company` instance**; `Company.class` chỉ dùng khi tra cứu signature.
5. Không tự chọn; caller truyền đối tượng bao ngoài cụ thể vào `newInstance`.
6. Không. `getDeclaringClass()` chỉ cung cấp class metadata; vẫn phải có `Company` instance phù hợp và cung cấp các tham số constructor.

## 12. Ghi nhớ

**Non-static member inner class có enclosing instance**. Khi Reflection lấy constructor của nó, hãy tính **kiểu enclosing class là tham số đứng đầu**, rồi nối tiếp các kiểu tham số mà mã nguồn khai báo; khi gọi `newInstance`, cung cấp **đối tượng outer thật** trước các đối số tường minh.

Phân biệt với `static nested class` không cần đối tượng outer. Dùng `getParameterTypes()` để xác minh signature, `Modifier.isStatic()` để hỗ trợ phân loại member class, xử lý đúng quyền truy cập và exception. Không phụ thuộc vào tên field/parameter compiler tự sinh; với local/anonymous classes, cần khảo sát bytecode cụ thể thay vì áp dụng một công thức duy nhất.

Tài liệu chuẩn để đối chiếu:

- [Java SE 17 — `Class.getDeclaredConstructor` và `Class.getConstructor`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html)
- [Java SE 17 — `Constructor.newInstance`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Constructor.html)
- [Java Language Specification `8.8.1 và `15.9.3](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="constructors-008">Tham số ngầm định hoặc synthetic của constructor</a>

<details>
<summary>Xem chi tiết</summary>

# Tham số ngầm định hoặc synthetic của constructor trong Java Reflection

## 1. Vì sao chữ ký constructor khi Reflection có thể khác phần ta viết?

Khi nhìn mã nguồn, ta thường nghĩ constructor có đúng những tham số được viết giữa cặp dấu ngoặc. Ví dụ:

```java
class Outer {
    class Inner {
        Inner(String message) {}
    }
}
```

Trong mã Java, ta viết `outer.new Inner("Hello")`: danh sách đối số hiển thị của `Inner` chỉ có một `String`. Tuy nhiên, Java còn cần biết **instance `Outer` bao quanh** đối tượng `Inner`. Bộ biên dịch thể hiện mối liên hệ này trong constructor của classfile bằng **một tham số đầu bổ sung**. Khi Reflection tra cứu constructor, chữ ký có thể là `(Outer, String)`, chứ không phải chỉ `(String)`.

Những tham số không xuất hiện trực tiếp như một khai báo thông thường trong source còn có thể xuất hiện ở **enum constructor** hoặc constructor do compiler sinh cho **local/anonymous class** có captured variables. Reflection cho ta thấy chữ ký thực tế của executable trong classfile; một framework xây dựng đối số chỉ dựa vào khai báo source có thể tìm sai constructor hoặc truyền sai thứ tự giá trị.

Điều cần phân biệt là:

- **Explicit parameter:** tham số được lập trình viên khai báo tường minh, ví dụ `String message`.
- **Implicit parameter:** tham số được ngôn ngữ xem là được khai báo ngầm định; ví dụ tham chiếu enclosing instance của một non-static member inner class.
- **Synthetic parameter:** tham số do compiler đưa vào mà không được khai báo tường minh hay ngầm định theo định nghĩa của Java; ví dụ hai tham số nội bộ của enum, hoặc captured local value trong bytecode do `javac` sinh.

Tên tham số, cờ `implicit`/`synthetic` và cách compiler lưu classfile cần được kiểm tra đúng ở metadata thay vì đoán theo tên như `this$0`, `arg0` hoặc `val$...`.

## 2. API Reflection dành cho constructor và từng tham số

Đối tượng `java.lang.reflect.Constructor<?>` có các API quan trọng:

| API | Ý nghĩa |
| --- | --- |
| `Class.getDeclaredConstructors()` | Lấy mọi constructor khai báo trong kiểu, bất kể access modifier |
| `Class.getDeclaredConstructor(Class<?>...)` | Tìm một constructor theo **toàn bộ** các kiểu tham số thực tế và đúng thứ tự |
| `Class.getConstructors()` | Chỉ các constructor public của class |
| `Constructor.getParameterCount()` | Tổng số tham số của executable, bao gồm các vị trí compiler bổ sung xuất hiện trong chữ ký |
| `Constructor.getParameterTypes()` | Mảng kiểu tham số `Class<?>[]` theo đúng thứ tự |
| `Constructor.getParameters()` | Mảng `java.lang.reflect.Parameter` có metadata của từng vị trí |
| `Parameter.getType()` | Kiểu tham số dạng `Class<?>` |
| `Parameter.getName()` | Tên metadata hoặc tên tổng hợp như `arg0` |
| `Parameter.isNamePresent()` | Tên có được lưu trong metadata của classfile không |
| `Parameter.isImplicit()` | Tham số được khai báo ngầm định theo Java Language Specification |
| `Parameter.isSynthetic()` | Tham số do compiler sinh, không được khai báo tường minh hoặc ngầm định |

Hai method `isImplicit()` và `isSynthetic()` diễn đạt **hai loại khác nhau** theo đặc tả `Parameter`. Đừng xem chúng là hai tên gọi tương đương cho mọi tham số “ẩn”. Cả hai cùng có thể là `false` với tham số explicit bình thường.

Phân biệt thêm: `constructor.isSynthetic()` hỏi **bản thân constructor** có synthetic không; `constructor.getParameters()[i].isSynthetic()` hỏi **tham số thứ i** có synthetic không. Một constructor không phải synthetic vẫn có thể chứa synthetic parameters.

## 3. Trường hợp 1: non-static member inner class cần enclosing instance

### 3.1. Tại sao có tham số `Outer` ở đầu?

Một **non-static member class** gắn với một enclosing instance. Cú pháp Java thông thường:

```java
Outer outer = new Outer();
Outer.Inner inner = outer.new Inner("Hello");
```

đã cung cấp `outer` thông qua biểu thức `outer.new`. Trong biểu diễn constructor của classfile, enclosing instance thường là **tham số thứ nhất**, rồi mới đến tham số source `String`. Ví dụ:

```text
Constructor source: Inner(String)
Constructor Reflection: Inner(Outer, String)
```

Để tra cứu bằng Reflection, ta cần dùng:

```java
Constructor<Outer.Inner> ctor =
        Outer.Inner.class.getDeclaredConstructor(Outer.class, String.class);
```

Đây là **đoạn minh họa**, giả sử `Outer.Inner` và constructor tương ứng được khai báo với mức truy cập cho phép. Nếu constructor không public, cần kiểm tra access; `getDeclaredConstructor` tìm được metadata nhưng không tự cấp quyền gọi.

Không phải mọi class lồng nhau đều cần enclosing instance: **static nested class** không có yêu cầu này. Một class được khai báo `static class Nested` có constructor `Nested(String)` sẽ không tự nhận thêm tham số kiểu `Outer` chỉ vì nó được đặt trong source của `Outer`.

### 3.2. Ví dụ Java 17+ độc lập, có thể biên dịch

Tạo file **`ConstructorParametersDemo.java`**:

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.util.Arrays;

public class ConstructorParametersDemo {
    class Inner {
        final String value;
        Inner(String value) {
            this.value = value;
        }
    }

    static class Nested {
        Nested(String value) {}
    }

    enum Level {
        HIGH(10);
        Level(int priority) {}
    }

    private static void show(Class<?> type) {
        for (Constructor<?> ctor : type.getDeclaredConstructors()) {
            System.out.println("Type: " + type.getSimpleName());
            System.out.println("Parameter count: " + ctor.getParameterCount());
            for (Parameter p : ctor.getParameters()) {
                System.out.printf(
                        "  %s | %s | implicit=%s synthetic=%s namePresent=%s%n",
                        p.getName(), p.getType().getSimpleName(),
                        p.isImplicit(), p.isSynthetic(), p.isNamePresent()
                );
            }
        }
    }

    public static void main(String[] args) throws Exception {
        show(Inner.class);
        show(Nested.class);
        show(Level.class);

        Constructor<Inner> ctor = Inner.class.getDeclaredConstructor(
                ConstructorParametersDemo.class, String.class
        );
        ctor.setAccessible(true);
        Inner instance = ctor.newInstance(new ConstructorParametersDemo(), "ok");
        System.out.println("Inner value: " + instance.value);
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 -parameters ConstructorParametersDemo.java
java ConstructorParametersDemo
```

Kết quả stdout với **`javac 21 --release 17 -parameters`**:

```text
Type: Inner
Parameter count: 2
  this$0 | ConstructorParametersDemo | implicit=true synthetic=false namePresent=true
  value | String | implicit=false synthetic=false namePresent=true
Type: Nested
Parameter count: 1
  value | String | implicit=false synthetic=false namePresent=true
Type: Level
Parameter count: 3
  $enum$name | String | implicit=false synthetic=true namePresent=true
  $enum$ordinal | int | implicit=false synthetic=true namePresent=true
  priority | int | implicit=false synthetic=false namePresent=true
Inner value: ok
```

**Đọc kết quả theo từng lớp:**

- `Inner` có **hai** tham số Reflection, dù mã nguồn khai báo một tham số `value`. Vị trí đầu là enclosing instance kiểu `ConstructorParametersDemo`. `isImplicit() == true`; ở bản build đã kiểm tra, `isSynthetic() == false`.
- `Nested` là static nested class nên chỉ có `String value`, không cần object bên ngoài.
- `Level` là enum: trước tham số explicit `int priority` có hai tham số nội bộ `String` và `int`; với `javac` đang dùng, chúng được đánh dấu `synthetic=true`.
- Lời gọi `ctor.newInstance(new ConstructorParametersDemo(), "ok")` cung cấp **đúng thứ tự**: enclosing instance trước, `String` sau, nên tạo `Inner` thành công.

Ví dụ cố ý gọi `setAccessible(true)` vì constructor `Inner` có package-private access và được tra cứu bằng `getDeclaredConstructor`. Trong ngữ cảnh ví dụ này, code được chạy trên classpath trong unnamed module và cùng class bao ngoài; không được suy luận rằng `setAccessible(true)` luôn được phép xuyên qua mọi module.

Tên `this$0` và `$enum$name` là chi tiết sinh bởi compiler trong phiên bản được kiểm tra; **không nên dùng tên ấy làm hợp đồng API lâu dài**. Metadata ổn định hơn cho thao tác lựa chọn constructor là **vị trí, kiểu tham số và đặc tính của executable**.

## 4. Trường hợp 2: enum constructor có tham số nội bộ

Xét:

```java
enum Level {
    HIGH(10);
    Level(int priority) {}
}
```

Trong source, constructor chỉ nhận `int priority`. Nhưng classfile của enum có thêm hai giá trị nội bộ cần thiết cho `java.lang.Enum`:

1. Tên hằng enum (kiểu `String`).
2. Vị trí thứ tự, ordinal (kiểu `int`).

Bởi vậy, Reflection trên mã `javac` có thể thấy chữ ký tương đương `Level(String, int, int)`; hai tham số đầu là compiler-generated synthetic parameters. Đây là lý do `Level.class.getDeclaredConstructor(int.class)` không tìm được constructor mong đợi, còn `getDeclaredConstructor(String.class, int.class, int.class)` có thể tìm được metadata.

**Nhưng không thể dùng Reflection để tự tạo thêm enum constant.** Theo hợp đồng `Constructor.newInstance`, nếu declaring class là enum thì lệnh tạo sẽ ném `IllegalArgumentException`; `setAccessible(true)` không xóa giới hạn ấy. Dùng `Level.HIGH`, `Level.values()` hay `Enum.valueOf(Level.class, "HIGH")` để truy cập các hằng enum hợp lệ đã được khai báo.

Đừng nhầm việc **nhìn thấy constructor signature** với quyền **tạo instance**. Đây là hai phần riêng của hợp đồng Reflection.

## 5. Trường hợp 3: local hoặc anonymous class và giá trị bị capture

Compiler có thể thêm dữ liệu vào constructor để lưu **local variables được capture**. Java chỉ cho phép capture biến cục bộ có giá trị final hoặc *effectively final*; nếu biến bị gán lại làm mất tính effectively final sau khai báo, source sẽ không biên dịch khi lambda/local class đang capture nó.

Ví dụ sau dùng local class trong một static method nên không có enclosing instance kiểu class ngoài; compiler chỉ cần truyền biến `offset` được capture.

Tạo file **`LocalCaptureDemo.java`**:

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;

public class LocalCaptureDemo {
    public static void main(String[] args) {
        int offset = 8;

        class Local {
            Local(String label) {
                System.out.println(label + offset);
            }
        }

        for (Constructor<?> ctor : Local.class.getDeclaredConstructors()) {
            System.out.println("Constructor: " + ctor);
            for (Parameter p : ctor.getParameters()) {
                System.out.printf(
                        "%s %s implicit=%s synthetic=%s present=%s%n",
                        p.getName(), p.getType(),
                        p.isImplicit(), p.isSynthetic(), p.isNamePresent()
                );
            }
        }
    }
}
```

Chạy:

```bash
javac --release 17 -parameters LocalCaptureDemo.java
java LocalCaptureDemo
```

Kết quả được kiểm chứng với **`javac 21 --release 17 -parameters`**:

```text
Constructor: LocalCaptureDemo$1Local(java.lang.String,int)
label class java.lang.String implicit=false synthetic=false present=true
val$offset int implicit=false synthetic=true present=true
```

Tham số `label` là tham số explicit. `val$offset` được compiler dùng để lưu giá trị của local variable `offset`; trong bản classfile thử nghiệm nó là **synthetic parameter** và được xếp sau `label`.

**Giới hạn cần nhớ:** cách lower local/anonymous class, vị trí tham số capture, tên synthetic và số tham số bổ sung có thể phụ thuộc compiler, phiên bản ngôn ngữ, class được đặt trong static/instance context và biến nào thực sự được dùng. Không được viết thư viện giả định mọi captured parameter đều nằm cuối hoặc đều mang tiền tố `val$`. Hãy kiểm tra metadata thực tế trước khi gọi.

Nếu một local/anonymous class cần enclosing instance và có superclass constructor đặc biệt, cách truyền tham số có thể khác với non-static member class trong mục 3. Không nên lấy quy tắc “tham số đầu luôn là Outer” để áp dụng máy móc cho tất cả local/anonymous classes.

## 6. `-parameters` ảnh hưởng cái gì?

Tùy chọn biên dịch:

```bash
javac --release 17 -parameters ConstructorParametersDemo.java
```

yêu cầu `javac` lưu metadata tên tham số thích hợp trong classfile. API `Parameter.getName()` khi có tên trả tên đã được lưu; `isNamePresent()` cho biết metadata tên có thật hay không.

Nếu biên dịch lại không có `-parameters`, trên bản `javac` đã kiểm tra, các tên ở ví dụ chuyển thành `arg0`, `arg1`... và `isNamePresent()` trở thành `false`. Tuy vậy, **số tham số và loại tham số trong constructor không biến mất**: `Inner` vẫn cần enclosing instance, và enum vẫn có các slot nội bộ. Trong thí nghiệm với `javac 21 --release 17`, cờ implicit/synthetic cho các slot đặc biệt vẫn được phản ánh.

Lưu ý quan trọng:

- `-parameters` **không thêm** các đối số runtime mới; chúng vốn là một phần biểu diễn constructor cần thiết.
- Tên như `arg0` chỉ là tên được tổng hợp khi thiếu metadata; đừng dùng để map tham số nghiệp vụ.
- Đừng dựa vào `isNamePresent()` làm cách kiểm tra rằng một tham số là explicit: synthetic parameter cũng có thể có tên được lưu, như `$enum$name` trong ví dụ.
- Các compiler và build pipelines khác nhau có thể lưu metadata khác nhau; `Parameter.isImplicit()/isSynthetic()` phụ thuộc thông tin classfile đủ để báo cáo chính xác. Nếu metadata không đầy đủ, cần đối chiếu với loại class và constructor signature, không suy luận quá mức từ một cờ `false`.
- Debug information như `LocalVariableTable` **không tương đương** metadata MethodParameters dành cho `Parameter.getName()`.

`javap -v` có thể hữu ích khi nghiên cứu classfile do compiler sinh, nhưng đó là công cụ phân tích; runtime code nên dựa vào API Reflection và hợp đồng của mô hình dữ liệu mình xử lý.

## 7. Vì sao `getDeclaredConstructor(...)` báo `NoSuchMethodException`?

Sai lầm thường gặp:

```java
// Chỉ khai báo một tham số trong source của Inner.
Constructor<?> ctor =
        ConstructorParametersDemo.Inner.class
                .getDeclaredConstructor(String.class);
```

Đây là **mảnh code minh họa lỗi**, dùng class trong chương trình mục 3. Lệnh tìm theo `(String)` không khớp chữ ký thực tế `(ConstructorParametersDemo, String)`, vì vậy `getDeclaredConstructor` ném `NoSuchMethodException`.

Cách tra cứu đúng trong ví dụ:

```java
Constructor<?> ctor =
        ConstructorParametersDemo.Inner.class.getDeclaredConstructor(
                ConstructorParametersDemo.class, String.class
        );
```

Một framework hoặc công cụ không kiểm soát class được nạp nên **đọc danh sách constructor trước**:

```java
for (Constructor<?> ctor : targetClass.getDeclaredConstructors()) {
    System.out.println(ctor);
    for (Parameter parameter : ctor.getParameters()) {
        System.out.printf(
                "  %s, implicit=%s, synthetic=%s%n",
                parameter.getType().getName(),
                parameter.isImplicit(),
                parameter.isSynthetic()
        );
    }
}
```

Đây là **đoạn minh họa nằm trong phương thức có `Class<?> targetClass`**, không phải một chương trình Java độc lập. Sau khi quan sát constructor thực tế, chọn signature và đối số phù hợp; chỉ lọc synthetic/implicit khi đã xác định **hợp đồng khởi tạo** thực sự của loại class tương ứng.

## 8. Gọi `Constructor.newInstance(...)` đúng số lượng, thứ tự và quyền truy cập

`Constructor.newInstance(Object... initargs)` phải nhận đối số cho **tất cả** vị trí tham số runtime phù hợp. Với `Inner` trong ví dụ:

```java
Constructor<?> ctor =
        ConstructorParametersDemo.Inner.class.getDeclaredConstructor(
                ConstructorParametersDemo.class, String.class
        );
ctor.setAccessible(true);

Object obj = ctor.newInstance(new ConstructorParametersDemo(), "ready");
```

Nếu chỉ truyền `"ready"`, Java thiếu enclosing instance: `IllegalArgumentException` là kết quả có thể xảy ra vì số đối số không đúng. Nếu đảo vị trí, receiver/kiểu đối số không tương thích sẽ dẫn tới kiểm tra tham số thất bại. `newInstance` không tự tạo enclosing instance thay cho caller.

Đừng tùy tiện dùng `getDeclaredConstructors()[0]` trong code production: thứ tự các constructor do Reflection trả về không phải một hợp đồng để chọn constructor nghiệp vụ. Nên chỉ định signature, annotation hoặc quy tắc lựa chọn rõ ràng.

Phân biệt thêm:

| Lỗi hoặc giới hạn | Nguyên nhân tiêu biểu |
| --- | --- |
| `NoSuchMethodException` khi tra cứu | Signature `Class<?>...` không khớp đủ tham số, kể cả slot bổ sung |
| `IllegalArgumentException` khi `newInstance` | Sai số đối số, sai kiểu không chuyển đổi được, hoặc cố tạo enum |
| `IllegalAccessException` khi `newInstance` | Constructor/class không accessible và chưa có quyền bỏ qua kiểm tra |
| `InvocationTargetException` | Constructor đã được gọi nhưng code trong constructor ném exception |
| `InstantiationException` | Declaring class là abstract class, không thể tạo instance bình thường |
| `InaccessibleObjectException` khi thử `setAccessible(true)` | Deep reflection bị giới hạn bởi JPMS/module |
| `MalformedParametersException` khi đọc `getParameters()` | Metadata parameter trong classfile không hợp lệ |

**Chú ý về JPMS:** `getDeclaredConstructor` có thể trả metadata của constructor không public; để thực sự gọi nó từ module khác, caller vẫn chịu quy tắc package accessibility và module encapsulation. Việc có synthetic/implicit parameters không thay đổi các quy tắc access ấy.

## 9. Nguyên tắc thiết kế framework xử lý constructor

Nếu viết serializer, dependency-injection container, mapper hoặc plugin loader, nên theo trình tự sau:

1. **Nhận diện loại class:** `Class.isEnum()`, `Class.isMemberClass()`, `Class.isLocalClass()`, `Class.isAnonymousClass()`; kiểm tra `Modifier.isStatic(type.getModifiers())` khi cần.
2. **Lấy metadata constructor thật:** dùng `getDeclaredConstructors()`, `getParameterCount()`, `getParameterTypes()` và `getParameters()`.
3. **Tách tham số framework tạo được và tham số cần người dùng cung cấp:** enclosing instance phải là đúng object bên ngoài; captured state trong local/anonymous class thường không có cách cung cấp ổn định như constructor công khai.
4. **Không dùng chỉ tên tham số:** các tên compiler-generated thiếu tính ổn định; kể cả explicit names cũng phụ thuộc `-parameters`.
5. **Giữ thứ tự thực tế khi invoke:** nếu một constructor có signature `(Outer, String)`, phải truyền đủ hai đối số đúng thứ tự. Việc lọc slot ẩn khỏi UI hiển thị không có nghĩa được bỏ chúng khỏi invocation.
6. **Không cố instantiate enum bằng Reflection:** enum có quy tắc tạo constant riêng; dùng các hằng đã khai báo.
7. **Tôn trọng quyền truy cập:** chỉ dùng `trySetAccessible()/setAccessible` khi có nhu cầu deep reflection hợp pháp và module cho phép. Ưu tiên public factory hoặc API công khai khi khả thi.
8. **Kiểm thử với compiler/build thực tế:** đặc biệt nếu có Kotlin/Scala/bytecode generator hoặc cơ chế compile khác `javac`, cách biểu diễn và metadata có thể khác. Không định nghĩa một quy tắc chung cho mọi ngôn ngữ JVM.

Nếu mục tiêu là sinh đối tượng từ dữ liệu đầu vào theo contract ổn định, nên thiết kế **public factory**, explicit constructor hoặc builder để tránh buộc caller tự dựng enclosing instance/captured state.

## 10. Những nhầm lẫn thường gặp

| Nhận định sai | Sửa lại |
| --- | --- |
| “Constructor Reflection luôn có số tham số bằng source.” | Inner/enum/local class có thể có slot bổ sung trong classfile |
| “Tất cả tham số ẩn đều là synthetic.” | Enclosing instance của member inner class có thể là **implicit**, không synthetic |
| “`Parameter.isNamePresent() == true` thì là tham số user viết.” | Tham số synthetic/implicit cũng có thể có tên trong metadata |
| “Có `-parameters` mới có các tham số bổ sung.” | Tùy chọn này chủ yếu liên quan lưu metadata tên; runtime signature không phụ thuộc nó |
| “Cứ bỏ hết tham số synthetic trước khi gọi `newInstance`.” | `newInstance` cần đối số cho mọi slot trong signature đã chọn |
| “Một inner class luôn có constructor chỉ như mã nguồn.” | Non-static member inner class cần enclosing instance |
| “Static nested class cũng nhận `Outer`.” | Không; static nested class không tự yêu cầu enclosing instance |
| “Tìm thấy enum constructor thì tạo enum mới được.” | `Constructor.newInstance` cấm tạo enum instance |
| “Tên `this$0` hoặc `val$x` luôn giống nhau.” | Đó là chi tiết compiler, không phải public API ổn định |
| “`Constructor.isSynthetic()` cho biết mọi tham số có synthetic không.” | Cần kiểm tra `Parameter.isSynthetic()` trên từng tham số |

## 11. Câu hỏi tự kiểm tra

1. Với `class Outer { class Inner { Inner(int x) {} } }`, nếu `javac` sinh enclosing instance parameter thì `getDeclaredConstructor(...)` cần những `Class<?>` nào, theo thứ tự?
2. Với `static class Nested { Nested(int x) {} }` bên trong `Outer`, có tự động thêm tham số kiểu `Outer` không?
3. Một `Parameter` có `isImplicit() == true` có nhất thiết `isSynthetic() == true` không?
4. Enum có constructor trong source `E(String label)` thì tại sao metadata constructor có thể có thêm hai tham số đầu?
5. Với `-parameters`, vì sao `getName()` hữu ích hơn nhưng vẫn không nên dựa vào tên compiler-generated để định danh logic?
6. Nếu framework lọc hai synthetic parameters của enum khỏi giao diện hiển thị, framework có thể truyền chỉ tham số explicit vào `Constructor.newInstance` để tự tạo enum không?
7. Captured local variable có nhất thiết trở thành parameter được Reflection báo theo cùng một thứ tự ở mọi compiler không?

**Đáp án ngắn:** (1) `Outer.class, int.class`; (2) không; (3) không, là hai phân loại khác nhau; (4) compiler cần tên constant và ordinal; (5) metadata tên phụ thuộc compiler/build, tên bổ sung không ổn định; (6) không, enum không được tạo bằng Reflection; (7) không, phải kiểm tra classfile thực tế.

## 12. Ghi nhớ và tài liệu tham khảo

**Chữ ký constructor qua Reflection mô tả cấu trúc thực tế của executable**, bao gồm những tham số compiler thêm vào để đáp ứng semantics của Java. Khi gặp `NoSuchMethodException` hoặc `IllegalArgumentException` bất ngờ với constructor, hãy kiểm tra declaring class, constructor signature, `Parameter.isImplicit()`, `Parameter.isSynthetic()`, tên có thực sự được lưu hay không, và đầy đủ các đối số runtime cần cung cấp.

Tài liệu chính thức:

- [Java SE 21 — `java.lang.reflect.Parameter`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Parameter.html): ý nghĩa `isImplicit`, `isSynthetic`, `isNamePresent`.
- [Java SE 21 — `java.lang.reflect.Constructor`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Constructor.html): cách `newInstance` xử lý enclosing instance, đối số và enum.
- [Java Language Specification `8.8` — Constructor Declarations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.8): cấu trúc constructor trong ngôn ngữ Java.
- [Java Language Specification `15.9` — Class Instance Creation Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.9): enclosing instance và đối số constructor khi tạo object.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="constructors-009">Constructor ném ngoại lệ và lớp bọc InvocationTargetException</a>

<details>
<summary>Xem chi tiết</summary>

# Constructor ném ngoại lệ và lớp bọc `InvocationTargetException` trong Java Reflection

Khi tạo đối tượng thông qua Java Reflection, lệnh `Constructor.newInstance(...)` có thể thất bại vì **hai nhóm nguyên nhân rất khác nhau**:

1. **Constructor của ứng dụng thực sự được gọi rồi ném exception/error**. Reflection bọc đối tượng lỗi gốc trong `java.lang.reflect.InvocationTargetException`. Người gọi cần xem `getCause()` để biết vấn đề thực sự.
2. **Reflection chưa thể gọi constructor hợp lệ**, chẳng hạn sai kiểu đối số, thiếu quyền truy cập, kiểu đích là abstract class hoặc class initialization thất bại. Các trường hợp này thường được báo bằng **exception/error riêng**, không được coi mặc định là `InvocationTargetException`.

Phân biệt đúng hai nhóm này giúp lập trình viên sửa lỗi ở đúng nơi: kiểm tra dữ liệu đầu vào của constructor, giải quyết cấu hình access/module hoặc sửa logic bên trong constructor.

Mục tiêu của bài là hiểu chính xác khi nào `InvocationTargetException` xuất hiện, cách mở lớp bọc để xử lý checked/unchecked exceptions, cách nhận diện những lỗi không bị bọc, và cách viết code Java 17+ có thể biên dịch/chạy và chẩn đoán được.

## 1. Phân biệt tra cứu constructor và gọi constructor

Đối tượng `Class<T>` cung cấp:

```java
Constructor<T> ctor = type.getConstructor(parameterTypes);
Constructor<T> declared = type.getDeclaredConstructor(parameterTypes);
```

Hai lệnh trên chỉ lấy **metadata về constructor**. Chúng chưa tạo đối tượng và chưa chạy thân constructor. `getConstructor` chỉ tìm constructor **public** có đúng dãy kiểu tham số; `getDeclaredConstructor` tìm constructor khai báo trực tiếp ở lớp, kể cả constructor private.

Việc tạo instance xảy ra khi gọi:

```java
T instance = ctor.newInstance(arguments);
```

Đây là mẫu code mô tả ý tưởng; `T`, `type`, `parameterTypes` và `arguments` phải được định nghĩa trong chương trình thực. Hai loại vấn đề cần tách riêng:

- **Lookup thất bại:** `getConstructor(String.class)` không tìm được chữ ký public phù hợp thì ném `NoSuchMethodException`. Lúc đó constructor chưa chạy.
- **Invocation thất bại:** sau khi tìm được metadata, `newInstance("abc")` mới thử khởi tạo class (nếu cần), kiểm tra quyền/đối số và thực thi constructor. Khi **chính constructor** ném lỗi, Reflection phát ra `InvocationTargetException` bọc lỗi gốc.

Đối tượng `Constructor<T>` thuộc gói `java.lang.reflect`. Phương thức `newInstance(Object... initargs)` có kiểu trả về `T`, nhưng không có nghĩa là lời gọi chắc chắn thành công hoặc mọi vấn đề đều có cùng loại exception.

## 2. Vì sao Java Reflection cần `InvocationTargetException`?

Một constructor có thể khai báo và ném checked exception:

```java
public Person(String name) throws java.io.IOException {
    if (name == null) {
        throw new java.io.IOException("name is required");
    }
}
```

Nếu gọi trực tiếp `new Person(null)`, caller phải xử lý hoặc khai báo `IOException` theo cơ chế checked exception của Java. Nhưng khi Reflection gọi `Constructor.newInstance(...)`, chữ ký API không thể liệt kê trước mọi checked exception mà **bất kỳ constructor runtime nào** có thể ném ra.

Vì vậy, nếu constructor thực thi rồi ném `IOException`, `IllegalArgumentException`, `AssertionError` hoặc một `Throwable` khác từ thân constructor, Reflection báo một **`InvocationTargetException`**, trong đó giữ lỗi ban đầu làm **cause**.

Mô hình:

```text
Constructor.newInstance(arguments)
          |
          +-- chuẩn bị/check access, tham số, kiểu, class initialization
          |
          +-- gọi constructor <init>
                    |
                    +-- chạy thành công ----------> trả instance
                    |
                    +-- ném IOException ----------> InvocationTargetException
                    |                                  cause = IOException
                    +-- ném IllegalStateException -> InvocationTargetException
                    |                                  cause = IllegalStateException
                    +-- ném AssertionError --------> InvocationTargetException
                                                       cause = AssertionError
```

Sơ đồ mô tả **lỗi phát sinh trong constructor đã được gọi**. Exception hoặc error phát sinh trong **bước Reflection trước đó**, đặc biệt lỗi class initialization, có quy tắc riêng và không được gộp vào nhánh bọc trên.

### Hai thuộc tính của lớp bọc

`InvocationTargetException` là **checked exception**. Nó có:

- `getCause()`: trả `Throwable` gốc gây ra invocation thất bại.
- `getTargetException()`: phương thức tương thích lịch sử, cũng cho phép truy cập exception gốc. Trong code hiện đại, thường ưu tiên `getCause()`.

Ví dụ mẫu:

```java
try {
    Object instance = constructor.newInstance(values);
} catch (java.lang.reflect.InvocationTargetException e) {
    Throwable original = e.getCause();
    // Phân tích original để biết lỗi thực tế từ constructor.
}
```

Đây là đoạn minh họa; `constructor` và `values` có sẵn từ caller, và các checked exception khác của `newInstance` cần được xử lý hoặc khai báo bổ sung. Không chỉ in `InvocationTargetException` rồi bỏ qua `cause`, vì lỗi nghiệp vụ chính thường nằm ở lớp trong.

## 3. Ví dụ Java 17+ hoàn chỉnh: phân biệt nhiều loại lỗi

Lưu chương trình thành **`ConstructorExceptionDemo.java`**:

```java
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class ConstructorExceptionDemo {
    public static class Worker {
        private final String name;

        public Worker(String name) throws IOException {
            if (name == null) {
                throw new IOException("name is required");
            }
            if (name.isBlank()) {
                throw new IllegalArgumentException("blank name");
            }
            if (name.equals("error")) {
                throw new AssertionError("fatal problem");
            }
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public abstract static class AbstractWorker {
        public AbstractWorker() { }
    }

    public static class BrokenInit {
        static {
            if (shouldFail()) {
                throw new IllegalStateException("init failed");
            }
        }

        static boolean shouldFail() {
            return true;
        }

        public BrokenInit() { }
    }

    private static void printCause(String label, InvocationTargetException e) {
        Throwable cause = e.getCause();
        System.out.println(label + ": "
                + cause.getClass().getSimpleName()
                + " -> " + cause.getMessage());
    }

    public static void main(String[] args) throws Exception {
        Constructor<Worker> ctor = Worker.class.getConstructor(String.class);

        System.out.println("throws declared: "
                + ctor.getExceptionTypes()[0].getSimpleName());
        System.out.println("success: " + ctor.newInstance("Lan").getName());

        try {
            ctor.newInstance((Object) null);
        } catch (InvocationTargetException e) {
            printCause("checked", e);
        }

        try {
            ctor.newInstance(" ");
        } catch (InvocationTargetException e) {
            printCause("unchecked", e);
        }

        try {
            ctor.newInstance("error");
        } catch (InvocationTargetException e) {
            printCause("error", e);
        }

        try {
            ctor.newInstance(5);
        } catch (IllegalArgumentException e) {
            System.out.println("argument mismatch: "
                    + e.getClass().getSimpleName());
        }

        try {
            Worker.class.getConstructor(int.class);
        } catch (NoSuchMethodException e) {
            System.out.println("lookup failure: "
                    + e.getClass().getSimpleName());
        }

        Constructor<AbstractWorker> abstractCtor =
                AbstractWorker.class.getConstructor();
        try {
            abstractCtor.newInstance();
        } catch (InstantiationException e) {
            System.out.println("abstract: "
                    + e.getClass().getSimpleName());
        }

        Constructor<BrokenInit> brokenCtor =
                BrokenInit.class.getConstructor();
        try {
            brokenCtor.newInstance();
        } catch (ExceptionInInitializerError e) {
            Throwable root = e.getCause();
            System.out.println("class initialization: "
                    + root.getClass().getSimpleName()
                    + " -> " + root.getMessage());
        }
    }
}
```

Chạy từ thư mục chứa tệp với JDK 17 hoặc mới hơn:

```bash
javac ConstructorExceptionDemo.java
java ConstructorExceptionDemo
```

**Stdout đã đối chiếu bằng JDK 21:**

```text
throws declared: IOException
success: Lan
checked: IOException -> name is required
unchecked: IllegalArgumentException -> blank name
error: AssertionError -> fatal problem
argument mismatch: IllegalArgumentException
lookup failure: NoSuchMethodException
abstract: InstantiationException
class initialization: IllegalStateException -> init failed
```

### Phân tích từng kết quả

1. `getExceptionTypes()` liệt kê những loại exception có trong khai báo `throws` của constructor. `Worker(String)` khai báo `IOException`, nên dòng đầu in `IOException`. Metadata này **không có nghĩa constructor luôn ném exception**.
2. `ctor.newInstance("Lan")` gọi constructor thành công; chương trình nhận một instance và in `success: Lan`.
3. `ctor.newInstance((Object) null)` truyền **một đối số `null`** vào tham số `String`. Constructor được gọi, kiểm tra `name == null` và ném `IOException`. Reflection bọc nó trong `InvocationTargetException`. `getCause()` lấy lại `IOException` cùng thông điệp.
4. `ctor.newInstance(" ")` gọi được constructor; `isBlank()` trả `true`, nên constructor ném unchecked `IllegalArgumentException`. Vì lỗi xuất phát **bên trong constructor**, Reflection vẫn bọc trong `InvocationTargetException`.
5. `ctor.newInstance("error")` khiến chính constructor ném `AssertionError`. Trong ví dụ này, `InvocationTargetException` cũng bọc `AssertionError`: lớp bọc không chỉ áp dụng cho checked exceptions. Không nên tự động bắt rồi bỏ qua mọi `Error` trong ứng dụng sản xuất.
6. `ctor.newInstance(5)` có **kiểu đối số không phù hợp** với constructor `Worker(String)`. Reflection ném `IllegalArgumentException` ngay ở tầng invocation; thân constructor không chạy. Đây **không phải** `InvocationTargetException`.
7. `Worker.class.getConstructor(int.class)` thất bại tại **bước tra cứu** vì không tồn tại constructor public `Worker(int)`. Kết quả là `NoSuchMethodException`.
8. `AbstractWorker` có constructor public nhưng là **abstract class**; Reflection không thể tạo instance trực tiếp nên `Constructor.newInstance()` ném `InstantiationException`.
9. `BrokenInit` có khối static initializer ném `IllegalStateException`. Class initialization thất bại **trước khi thân constructor chạy**. JVM phát ra `ExceptionInInitializerError`; không nên nhầm với `InvocationTargetException`.

Trong ví dụ, các class con được khai báo `public static` và constructor cần thiết là public, tránh nhiễu từ lỗi visibility. Lỗi quyền truy cập sẽ được giải thích riêng.

## 4. Bảng nhận diện: lỗi đến từ lớp nào?

| Điểm xảy ra lỗi | Tình huống | Lỗi quan sát được | Có `InvocationTargetException` bọc không? |
|---|---|---|---|
| Tra cứu trên `Class` | Constructor phù hợp không tồn tại | `NoSuchMethodException` | Không |
| Chuẩn bị invocation | Số lượng/kiểu đối số không phù hợp | `IllegalArgumentException` | Không |
| Chuẩn bị invocation | Caller không có quyền gọi constructor | `IllegalAccessException` | Không |
| Chuẩn bị invocation | Không thể tạo instance của abstract class | `InstantiationException` | Không |
| Khởi tạo class | Static initializer thất bại do exception không phải `Error` | `ExceptionInInitializerError` | Không |
| Trong thân constructor | `throw new IOException(...)` | `InvocationTargetException`, cause = `IOException` | Có |
| Trong thân constructor | `throw new IllegalStateException(...)` | `InvocationTargetException`, cause = `IllegalStateException` | Có |
| Trong thân constructor | `throw new AssertionError(...)` | `InvocationTargetException`, cause = `AssertionError` | Có |

Bảng phản ánh các trường hợp thông thường qua **core Reflection**. Trường hợp class initialization ném trực tiếp một `Error`, JVM có thể truyền tiếp error đó theo quy tắc initialization; lỗi khởi tạo lặp lại của cùng runtime class về sau thường là `NoClassDefFoundError`. Không có bảo đảm rằng mọi lỗi trước constructor đều là `ExceptionInInitializerError`.

**Đặc biệt về `IllegalArgumentException`:** tên exception giống nhau nhưng nguồn lỗi khác nhau tạo ra hai cách bắt khác nhau:

```java
// Sai kiểu đối số: Reflection tự ném IllegalArgumentException.
ctor.newInstance(Integer.valueOf(5));

// Đối số hợp lệ là String nhưng constructor tự ném
// IllegalArgumentException: Reflection bọc trong InvocationTargetException.
ctor.newInstance(" ");
```

Hai dòng trên là ví dụ trích từ chương trình hoàn chỉnh, không được chạy nối tiếp mà thiếu xử lý ngoại lệ. Để kết luận lỗi đến từ đâu, hãy dựa vào **kiểu exception bên ngoài** và **cause**, không chỉ nhìn tên `IllegalArgumentException` xuất hiện trong stack trace.

## 5. `getCause()`: xem lỗi gốc mà không làm mất thông tin

Khi bắt `InvocationTargetException`, gọi:

```java
catch (InvocationTargetException e) {
    Throwable cause = e.getCause();
    cause.printStackTrace();
}
```

Đây là nhánh `catch` minh họa đặt trong một khối `try` thích hợp. `printStackTrace()` giúp thấy **đường đi lỗi bên trong constructor**; không cần in đồng thời nhiều bản stack trace giống nhau vào log. Trong ứng dụng thực tế, có thể log `e` đầy đủ để JVM hiển thị chuỗi `Caused by`.

Dùng `Throwable` cho cause vì code constructor có thể ném **checked exception, unchecked exception hoặc `Error`**. Không cast cứng mọi cause thành `Exception` nếu chưa kiểm tra kiểu.

`InvocationTargetException.getTargetException()` cũng trả exception gốc, nhưng `getCause()` tuân theo mô hình exception chaining chuẩn của Java, thuận tiện cho logging và các hệ thống xử lý `Throwable`.

### Giữ lại chuỗi cause khi báo lỗi lên tầng trên

Nếu ứng dụng muốn đổi lỗi thành một exception có ngữ cảnh:

```java
try {
    return ctor.newInstance(value);
} catch (InvocationTargetException e) {
    throw new IllegalStateException(
            "Failed to create " + ctor.getDeclaringClass().getName(),
            e.getCause());
}
```

Đây là đoạn **minh họa nằm trong một phương thức factory** với `ctor` và `value` đã có, cùng xử lý/khai báo các exception Reflection khác. Constructor `IllegalStateException(String, Throwable)` giữ **original cause**; khi đọc stack trace vẫn truy ngược được lỗi gốc.

Việc wrap lại là **quyết định hợp đồng API**. Với lỗi nghiệp vụ dự kiến (ví dụ dữ liệu người dùng không hợp lệ), một số API muốn truyền lại đúng loại exception đã được kiểm tra. Với `Error` nghiêm trọng, thường không nên biến nó thành lỗi nghiệp vụ bình thường rồi tiếp tục chạy.

### Không tự tin rằng `getCause()` là lỗi cuối cùng

Constructor có thể gọi service khác và nhận một exception vốn đã bọc exception sâu hơn, ví dụ `IllegalStateException` có cause là `IOException`. Khi ấy:

```text
InvocationTargetException
└── IllegalStateException
    └── IOException
```

`getCause()` một lần chỉ mở **lớp bọc Reflection**. Muốn điều tra tận cùng, xem toàn bộ cause chain và cả suppressed exceptions nếu có. Không mặc định rằng vòng lặp bóc đến cuối luôn là cách xử lý nghiệp vụ đúng; có thể lớp exception trung gian mang thông tin quan trọng.

## 6. Checked exception và unchecked exception trong constructor

Sự khác nhau giữa **khai báo `throws`** và **lỗi thực tế** là điểm rất quan trọng:

```java
public Worker(String name) throws IOException {
    if (name == null) throw new IOException("name is required");
    if (name.isBlank()) throw new IllegalArgumentException("blank name");
    this.name = name;
}
```

- `IOException` là checked exception: khai báo trong `throws IOException`, được phản ánh bởi `Constructor.getExceptionTypes()`.
- `IllegalArgumentException` là unchecked exception: không bắt buộc phải xuất hiện trong `throws`, nhưng constructor vẫn có thể ném nó.
- Cả hai loại đều thành `InvocationTargetException` ở **lớp ngoài** khi constructor được gọi bằng `Constructor.newInstance`.
- `getExceptionTypes()` chỉ là **metadata khai báo**, không phải danh sách bảo đảm đầy đủ mọi lỗi thực sự có thể xuất hiện. Nó không dự đoán đường đi code nào sẽ chạy.

Một constructor cũng có thể gọi một constructor khác qua `this(...)` hoặc `super(...)`. Nếu constructor ở chuỗi ấy ném exception, việc khởi tạo instance thất bại. Khi lời gọi Reflection bắt nguồn từ `Constructor.newInstance` và ngoại lệ được phát sinh trong luồng thực thi constructor, exception được đưa ra trong lớp bọc `InvocationTargetException`.

Vì thế, khi chẩn đoán **tại sao `newInstance` thất bại**, cần xem phần code thực sự chạy trong constructor, bao gồm field initializers, instance initializer blocks và các lời gọi constructor liên quan, thay vì chỉ nhìn dòng `return` hoặc `throws` ở lớp ngoài.

## 7. Phân biệt lỗi class initialization và lỗi constructor

Java có hai loại initialization dễ nhầm:

- **Class initialization:** thực hiện static field initializers và các khối `static` (`<clinit>`). Nó diễn ra theo quy tắc JVM khi class được sử dụng chủ động, trước khi tạo instance của class.
- **Instance/constructor initialization:** thực hiện quy trình khởi tạo object, instance field initializers và constructor (`<init>`) trong thứ tự Java quy định.

Ví dụ `BrokenInit` trong chương trình có static initializer thất bại. Chỉ lấy metadata:

```java
Constructor<BrokenInit> constructor =
        BrokenInit.class.getConstructor();
```

thông thường **chưa chủ động khởi tạo `BrokenInit`**. Nhưng:

```java
constructor.newInstance();
```

yêu cầu tạo instance, nên JVM phải bảo đảm class được khởi tạo trước. Nếu static initializer thất bại do `IllegalStateException`, JVM phát ra `ExceptionInInitializerError`; constructor `BrokenInit()` **chưa chạy**. Đây là lỗi class initialization, không phải exception từ thân constructor đã gọi.

Lần truy cập chủ động tiếp theo vào cùng class sau khi initialization đã thất bại có thể gây `NoClassDefFoundError` vì class ở trạng thái erroneous. Không nên retry liên tục cùng runtime class và kỳ vọng khối static sẽ tự chạy lại như chưa từng thất bại.

Một tình huống khác: constructor bắt đầu chạy nhưng instance field initializer hoặc superclass constructor ném lỗi; thông thường Reflection sẽ bọc lỗi đó trong `InvocationTargetException` vì lỗi phát sinh trong quá trình thực thi khởi tạo instance. Khi chẩn đoán cần xem **stack trace của cause** để xác định vị trí thực.

## 8. `Class.newInstance()` khác `Constructor.newInstance()` ở điểm gì?

Đừng dùng tên giống nhau để suy ra hành vi exception giống nhau:

| API | Tình trạng | Cách xử lý exception từ constructor |
|---|---|---|
| `Constructor<T>.newInstance(Object... initargs)` | API Reflection hiện hành, cho phép chọn constructor theo chữ ký | Bọc exception do constructor ném trong `InvocationTargetException` |
| `Class<T>.newInstance()` | **Deprecated** từ Java 9; không nên dùng cho code mới | Có thể truyền trực tiếp exception từ constructor, kể cả checked exception không được thể hiện rõ trong `throws` của API |

`Class.newInstance()` là API cũ chỉ dùng constructor không tham số có thể truy cập và có nhiều hạn chế. Java API documentation khuyến nghị dùng `getDeclaredConstructor().newInstance()` thay thế để có lựa chọn constructor và xử lý exception rõ ràng.

Điều đó có nghĩa:

```java
Sample obj = Sample.class.getDeclaredConstructor().newInstance();
```

là cách thường dùng cho code mới khi cần constructor không tham số (giả định có constructor phù hợp và caller được phép sử dụng). Nhưng `getDeclaredConstructor()` tìm được metadata **không đồng nghĩa** mọi constructor private đều được phép gọi qua các module; access rules vẫn được áp dụng.

Khi nâng cấp framework cũ đang dùng `Class.newInstance()`, cần rà soát cách xử lý exception vì lớp bọc của API mới khác API cũ. Không chỉ đổi tên hàm mà giữ nguyên logic catch một cách máy móc.

## 9. Quyền truy cập, constructor private và ranh giới module

`InvocationTargetException` không có nghĩa “constructor bị private”:

- `getConstructor(...)` chỉ tìm constructor `public`. Với constructor private, lookup có thể ném `NoSuchMethodException`.
- `getDeclaredConstructor(...)` có thể tìm constructor private, nhưng khi gọi `newInstance`, caller có thể gặp `IllegalAccessException`.
- `trySetAccessible()` hoặc `setAccessible(true)` có thể cho phép suppress access checks **khi quan hệ class/module cho phép**. Với Java named modules, deep reflection qua package có thể yêu cầu `opens` phù hợp; `setAccessible(true)` có thể ném `InaccessibleObjectException` nếu không được phép mở.
- `abstract` class không thể được tạo instance chỉ vì constructor của nó là public; `InstantiationException` vẫn là lỗi phù hợp.
- Constructor có thể nhận số lượng đối số đúng nhưng **sai kiểu**. Khi không có conversion Reflection hợp lệ, `IllegalArgumentException` phát sinh trước khi thân constructor được gọi.

Vì thế, catch `InvocationTargetException` phải dành cho **lỗi từ quá trình thực thi constructor**, còn lỗi lookup/access/type/instantiation cần chẩn đoán riêng.

### Với constructor varargs, tránh nhầm một đối số `null` và mảng varargs `null`

`Constructor.newInstance` có chữ ký `Object... initargs`. Để truyền **một đối số `null`** cho constructor `Worker(String)`, dùng:

```java
ctor.newInstance((Object) null);
```

Cast `(Object)` làm rõ rằng đây là **một argument**, thay vì một giá trị `null` có thể được hiểu như toàn bộ mảng varargs. Vì ví dụ `Worker(String)` nhận `String` reference, `null` là argument đúng kiểu; constructor sẽ chạy và tự ném `IOException`.

Nếu truyền đối số primitive là `null` hoặc số lượng/kiểu đối số không phù hợp với chữ ký constructor, Reflection có thể báo `IllegalArgumentException` trước khi chạy thân constructor. Không tự suy ra lỗi ấy đã được bọc.

## 10. Thiết kế xử lý lỗi trong factory/framework

Một factory có thể cần quy tắc rõ ràng:

1. Chọn constructor bằng parameter types **chính xác**.
2. Kiểm tra quyền truy cập của caller/module.
3. Validate dữ liệu trước khi invoke nếu có hợp đồng đầu vào.
4. Gọi `newInstance`.
5. Nếu gặp `InvocationTargetException`, xem `getCause()` để xử lý lỗi từ constructor.
6. Xử lý riêng các lỗi lookup, access, illegal argument, abstract class và class initialization.

Mẫu xử lý có kiểm soát cho một constructor public nhận `String` (đoạn này là **phương thức minh họa**, có thể đặt vào class phù hợp):

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

static <T> T construct(Class<T> type, String name) {
    final Constructor<T> ctor;
    try {
        ctor = type.getConstructor(String.class);
    } catch (NoSuchMethodException e) {
        throw new IllegalArgumentException(
                "No public constructor(String) in " + type.getName(), e);
    }

    try {
        return ctor.newInstance(name);
    } catch (InvocationTargetException e) {
        Throwable cause = e.getCause();

        if (cause instanceof Error error) {
            throw error;
        }
        if (cause instanceof RuntimeException runtime) {
            throw runtime;
        }
        throw new IllegalStateException(
                "Checked exception from constructor of " + type.getName(),
                cause);
    } catch (InstantiationException | IllegalAccessException e) {
        throw new IllegalStateException(
                "Cannot instantiate " + type.getName(), e);
    }
}
```

**Lưu ý:** Khối code trên là **mẫu phương thức đặt trong một class**, không phải chương trình độc lập; phần import cần ở đầu tệp. Đoạn mẫu định nghĩa một hợp đồng cụ thể: phát lại `Error` và unchecked `RuntimeException`, bọc checked exception vào `IllegalStateException`. Đây là một lựa chọn thiết kế, **không phải quy tắc bắt buộc** cho mọi framework. `IllegalArgumentException` do đối số Reflection sai có thể đi ra ngoài; nếu cần thông báo riêng, thêm nhánh bắt và ngữ cảnh phù hợp.

Đối với API có checked exceptions được công bố công khai, có thể muốn giữ đúng loại checked exception theo hợp đồng thay vì bọc thành `IllegalStateException`. Trong trường hợp đó, phải xử lý type/throws của phương thức gọi theo ngôn ngữ Java, không cast `Throwable` tùy tiện.

Không nên:

```java
catch (Exception e) {
    return null;
}
```

Đây là **mẫu sai chỉ để minh họa**. Nó gom cả lỗi không tìm được constructor, lỗi quyền truy cập và lỗi nghiệp vụ vào cùng một giá trị `null`, làm mất thông tin chẩn đoán. Nếu một factory có semantics trả optional/nullable khi không tạo được object, nó vẫn cần phân biệt lỗi nào thuộc diện xử lý dự kiến.

## 11. Kiểm thử constructor thất bại

Khi viết test cho Reflection, hãy kiểm chứng **lớp ngoài và cause**:

```java
// Minh họa dạng assertion với JUnit 5;
// đặt trong test method, có import và dependency JUnit phù hợp.
InvocationTargetException thrown = assertThrows(
        InvocationTargetException.class,
        () -> ctor.newInstance((Object) null)
);
assertInstanceOf(java.io.IOException.class, thrown.getCause());
assertEquals("name is required", thrown.getCause().getMessage());
```

Đây là đoạn test minh họa, **không thuộc chương trình Java chạy độc lập ở mục 3**. `ctor` là `Constructor<Worker>` đã tra cứu, còn `assertThrows`, `assertInstanceOf`, `assertEquals` là API của JUnit Jupiter. Test giúp phân biệt chính xác “Reflection không gọi được” với “constructor đã chạy và báo lỗi”.

Các test có giá trị thực tế gồm: constructor thành công; checked exception từ thân constructor; unchecked exception; lỗi sai argument Reflection; constructor không tồn tại; access bị chặn; class initialization thất bại. Đối với `Error`, tránh nuốt lỗi một cách vô tình và giữ khả năng đọc cause chain.

## 12. Những nhầm lẫn thường gặp

1. **“Mọi exception từ `Constructor.newInstance` đều nằm trong `InvocationTargetException`.”** Sai. Lỗi tham số, access, abstract type và class initialization có đường báo lỗi riêng.
2. **“`InvocationTargetException` là lỗi gốc của constructor.”** Sai. Nó là lớp bọc Reflection; dùng `getCause()` để xem lỗi gốc.
3. **“Constructor ném unchecked exception thì Reflection ném thẳng exception đó.”** Sai với `Constructor.newInstance`: lỗi trong thân constructor thường được bọc dù là checked hay unchecked.
4. **“`IOException` nằm trong `getExceptionTypes()` nghĩa là constructor chắc chắn thất bại.”** Sai. Đó chỉ là các loại được khai báo trong `throws`.
5. **“Sai đối số cũng được bọc như lỗi constructor.”** Sai. Sai kiểu/số lượng đối số có thể gây `IllegalArgumentException` từ tầng Reflection trước khi constructor chạy.
6. **“`NoSuchMethodException` và `InvocationTargetException` giống nhau.”** Sai. Một lỗi là **không tìm được metadata**, một lỗi là **constructor được gọi rồi ném lỗi**.
7. **“`getDeclaredConstructor` luôn gọi được private constructor.”** Sai. Metadata không tự vượt qua Java/JPMS access checks.
8. **“Khối static ném exception thì lỗi đó nằm trong `InvocationTargetException`.”** Không mặc định. Class initialization thất bại xảy ra trước constructor, thường cho `ExceptionInInitializerError` hoặc error theo quy tắc JVM.
9. **“`Class.newInstance()` và `Constructor.newInstance()` có cùng xử lý checked exceptions.”** Sai. `Class.newInstance()` là API deprecated với hành vi lan truyền exception khác.
10. **“Catch lỗi rồi trả `null` là đủ.”** Dễ làm mất nguyên nhân và che lỗi quan trọng. Giữ type/cause hoặc cung cấp lỗi API rõ ràng.

## 13. Câu hỏi tự kiểm tra

**Câu 1.** Constructor `public Service(String x)` ném `IllegalStateException("bad")` trong thân. Caller gọi qua `Constructor.newInstance("x")` gặp exception ngoài cùng nào?

**Đáp án:** `InvocationTargetException`. `getCause()` cho `IllegalStateException` chứa thông điệp `bad`.

**Câu 2.** `Service.class.getConstructor(int.class)` không tìm thấy constructor tương ứng. Lỗi nào phát sinh?

**Đáp án:** `NoSuchMethodException` tại lookup. Không có constructor nào được gọi; không có `InvocationTargetException`.

**Câu 3.** `Constructor<Service>` nhận `String`, nhưng người gọi truyền `Integer`. Có bắt buộc vào constructor rồi ném lỗi không?

**Đáp án:** Không. Reflection nhận diện argument không tương thích và có thể ném `IllegalArgumentException` trước khi gọi constructor.

**Câu 4.** Constructor khai báo `throws IOException` nhưng nhận dữ liệu hợp lệ và hoàn tất. Reflection có tự tạo `InvocationTargetException` vì có khai báo `throws` không?

**Đáp án:** Không. Constructor chạy thành công thì `newInstance` trả instance. `InvocationTargetException` chỉ phát sinh khi constructor thực sự ném lỗi.

**Câu 5.** Static initializer thất bại trước khi constructor chạy. Có chắc `InvocationTargetException` là lớp ngoài cùng không?

**Đáp án:** Không. Nếu static initializer ném một exception không thuộc `Error`, JVM thường phát `ExceptionInInitializerError`; class initialization khác với thực thi thân constructor.

**Câu 6.** Khi bắt `InvocationTargetException`, muốn log đúng lỗi gốc và vẫn giữ cause chain thì làm thế nào?

**Đáp án:** Đọc `getCause()`, log đầy đủ Throwable/stack trace và khi bọc lại hãy truyền cause vào constructor của exception mới. Không chỉ log `e.getMessage()` rồi bỏ cause.

## Tài liệu chuẩn

- Java SE API — `java.lang.reflect.Constructor.newInstance(Object...)`: exception contract cho `InstantiationException`, `IllegalAccessException`, `IllegalArgumentException`, `InvocationTargetException`, `ExceptionInInitializerError`.
- Java SE API — `java.lang.reflect.InvocationTargetException`: `getCause()`, `getTargetException()`.
- Java SE API — `java.lang.reflect.Constructor.getExceptionTypes()`, `getGenericExceptionTypes()`, `getDeclaringClass()`.
- Java SE API — `java.lang.Class.getConstructor`, `getDeclaredConstructor`, và `Class.newInstance()` (deprecated).
- Java Language Specification — §12.4 *Initialization of Classes and Interfaces*, §12.5 *Creation of New Class Instances*, §11 *Exceptions*.
- Java Virtual Machine Specification — §5.5 *Initialization*, quy tắc class initialization failure.

**Ghi nhớ:** `Constructor.newInstance` bọc **lỗi do constructor thực thi ném ra** trong `InvocationTargetException`; hãy đọc `getCause()` để thấy nguyên nhân. Lỗi tra cứu constructor, kiểm tra đối số, access, abstract class và class initialization là **những trường hợp khác**, phải chẩn đoán theo exception thực tế thay vì gộp chung thành lỗi constructor.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="constructors-010">Vì sao không dùng Class.newInstance đã lỗi thời</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao không dùng `Class.newInstance()` đã lỗi thời?

## 1. Mục tiêu học tập

Khi học Reflection, chúng ta thường gặp đoạn mã tạo object theo tên class:

```java
Class<?> type = Class.forName("com.example.Plugin");
Object instance = type.newInstance();
```

Lệnh `Class.newInstance()` ở dòng thứ hai **đã bị đánh dấu deprecated từ Java 9**. Java 17 vẫn có phương thức này để tương thích mã cũ, nhưng code mới nên dùng `Constructor<T>.newInstance(...)` qua một đối tượng `java.lang.reflect.Constructor<T>`.

Điểm cốt lõi không chỉ là cảnh báo deprecated. `Class.newInstance()` có ba hạn chế quan trọng:

1. **Chỉ gọi constructor không đối số**; không có cách truyền tham số khởi tạo.
2. **Có thể phát tán trực tiếp checked exception từ constructor**, trong khi chữ ký `Class.newInstance()` không khai báo exception đó. Đây là vấn đề an toàn kiểm tra ngoại lệ tại compile time.
3. **Không cho phép quản lý một constructor cụ thể**: khó kiểm tra signature, parameter types, annotations, modifiers và cân nhắc truy cập non-public constructor. API `Constructor` diễn đạt rõ hơn quy trình tìm, kiểm tra quyền rồi gọi constructor.

Cách thay thế cơ bản theo tài liệu Java 17:

```java
T object = clazz.getDeclaredConstructor().newInstance();
```

Ở đây `clazz` có kiểu `Class<T>`. `getDeclaredConstructor()` tìm constructor không tham số **khai báo bởi chính lớp**, `newInstance()` mới thực sự tạo instance. Cần xử lý `NoSuchMethodException` và `InvocationTargetException` cùng các lỗi Reflection có thể phát sinh. Không nên hiểu đoạn thay thế là mở quyền cho mọi constructor private; vấn đề truy cập vẫn phải xử lý riêng.

## 2. `Class.newInstance()` thực sự làm gì?

Chữ ký của phương thức cũ:

```java
@Deprecated(since = "9")
public T newInstance()
        throws InstantiationException, IllegalAccessException
```

`Class<T>.newInstance()` cố tạo một đối tượng thuộc class được biểu diễn bởi `Class<T>` thông qua **constructor không đối số** có thể truy cập được từ caller. Nếu lớp chưa được khởi tạo, lời gọi sẽ kích hoạt quá trình khởi tạo lớp khi cần.

Điều cần nhớ:

- `Class.newInstance()` **không phải** `Constructor.newInstance()`. Hai phương thức cùng tên nhưng thuộc hai kiểu khác nhau và có quy tắc exception khác nhau.
- Không thể truyền tên, id, service, configuration hoặc các constructor arguments khác vào `Class.newInstance()`.
- Nếu lớp không có constructor không đối số thích hợp, phương thức cũ thường ném `InstantiationException`. Với API mới, thiếu constructor khi tra cứu sẽ được biểu diễn rõ bằng `NoSuchMethodException`.
- Nếu lớp hoặc constructor không thể truy cập, phương thức cũ có thể ném `IllegalAccessException`.
- Nếu constructor tự ném checked exception, phương thức cũ **phát tán trực tiếp ngoại lệ đó** dù chữ ký chỉ khai báo hai loại checked exception ở trên.

`@Deprecated(since = "9")` là thông báo **không nên tiếp tục sử dụng API này trong code mới**. Deprecated không đồng nghĩa nó đã bị xóa khỏi JDK 17, cũng không có nghĩa mọi lời gọi cũ lập tức thất bại. Nhưng code mới nên dùng phương án được Java SE khuyến nghị.

## 3. Vấn đề nghiêm trọng nhất: phá vỡ kiểm tra checked exception

### 3.1. Checked exception thông thường

Java yêu cầu code xử lý hoặc khai báo checked exception mà compiler nhận biết được từ chữ ký phương thức và constructor.

Ví dụ một constructor hợp lệ:

```java
import java.io.IOException;

class Loader {
    public Loader() throws IOException {
        throw new IOException("resource unavailable");
    }
}
```

Lời gọi trực tiếp `new Loader()` yêu cầu caller xử lý hoặc khai báo `IOException`. Đây là bảo đảm quan trọng: compiler buộc chương trình nhận biết trường hợp tạo đối tượng có thể thất bại vì I/O.

### 3.2. Lỗ hổng của phương thức cũ

Giả sử code sau chạy với `Class<Loader>`:

```java
@SuppressWarnings("deprecation")
static Loader legacyCreate()
        throws InstantiationException, IllegalAccessException {
    return Loader.class.newInstance();
}
```

**Đây là ví dụ minh họa cần ghép với khai báo `Loader` phía trên.** Nó có thể biên dịch dù không `catch` và không `throws IOException`, bởi compiler chỉ thấy chữ ký `Class.newInstance()` khai báo `InstantiationException` và `IllegalAccessException`.

Khi constructor thực thi và ném `IOException`, `Class.newInstance()` có thể phát tán chính đối tượng exception đó ra ngoài. Nghĩa là runtime có thể ném **`IOException` mà phương thức `legacyCreate()` không khai báo**.

Kết quả không phải `InvocationTargetException`. Chính hành vi này khiến code có thể bỏ qua cơ chế kiểm tra checked exception của Java. Nó làm thiết kế hợp đồng lỗi của factory/helper khó dự đoán và dễ gây nhầm lẫn khi đọc code.

Một lưu ý về kỹ thuật thử lỗi: Nếu bạn muốn chứng minh trực tiếp việc phát tán exception từ code cũ, có thể dùng `catch (Exception e)` hoặc xử lý `Throwable` ở tầng thích hợp; compiler có thể từ chối một `catch (IOException e)` đặt quanh một lời gọi mà theo chữ ký không khai báo `IOException`, nếu trong khối `try` không có thao tác nào khác được khai báo ném `IOException`.

### 3.3. API mới giải quyết như thế nào?

`Constructor.newInstance(...)` có hợp đồng rõ ràng hơn: **ngoại lệ do chính constructor đích ném được bọc trong `InvocationTargetException`**.

```java
Constructor<Loader> constructor = Loader.class.getDeclaredConstructor();

try {
    Loader loader = constructor.newInstance();
} catch (InvocationTargetException ex) {
    Throwable original = ex.getCause();
    // original là IOException("resource unavailable")
}
```

Đoạn ví dụ này cần import `java.lang.reflect.Constructor`, `InvocationTargetException` và `Loader` phía trên; các checked exceptions khác của tra cứu/khởi tạo vẫn phải được xử lý hoặc khai báo.

Kết quả là caller phân biệt được hai nhóm lỗi:

- **Reflection không tìm được/không được quyền/không tạo được đối tượng**: các exception thuộc lớp Reflection.
- **Constructor đã chạy nhưng chính logic constructor thất bại**: `InvocationTargetException`, trong đó `getCause()` là lỗi gốc.

`InvocationTargetException` là checked exception, nên compiler buộc caller xử lý hoặc khai báo nó. Đây là điểm cải thiện trực tiếp về an toàn hợp đồng API.

## 4. So sánh `Class.newInstance()` và `Constructor.newInstance()`

| Tiêu chí | `Class.newInstance()` (deprecated) | `Constructor.newInstance(args...)` |
|---|---|---|
| Điểm xuất phát | Đối tượng `Class<T>` | Đối tượng `Constructor<T>` |
| Kể từ Java 9 | Được đánh dấu `@Deprecated` | API được khuyến nghị cho khởi tạo qua Reflection |
| Constructor hỗ trợ | Chỉ constructor không đối số | Constructor được chọn theo **signature**, có hoặc không có tham số |
| Chọn overloaded constructor | Không | Có, thông qua `getConstructor` / `getDeclaredConstructor` với đúng `Class<?>...` |
| Kiểm tra metadata constructor | Không qua đối tượng constructor riêng | Có `getParameterTypes()`, `getModifiers()`, `getAnnotations()`, `isVarArgs()` ... |
| Constructor non-public | Cần constructor truy cập được theo ngữ cảnh caller; không có đối tượng để yêu cầu suppression | Có thể tìm bằng `getDeclaredConstructor` rồi xét `canAccess`, `trySetAccessible` trong giới hạn module |
| Exception từ constructor | Phát tán trực tiếp, kể cả checked exception | Bọc trong `InvocationTargetException` |
| Thiếu constructor không đối số | Thường `InstantiationException` | `getDeclaredConstructor()` ném `NoSuchMethodException` |
| Constructor thực thi khi nào? | Ngay trong `Class.newInstance()` | Khi gọi `Constructor.newInstance()`, sau bước tra cứu |

Cột bên phải **không hứa** rằng mọi constructor đều được phép gọi. Kiểu abstract, enum, lỗi khởi tạo lớp, sai arguments, quyền module và những điều kiện runtime khác vẫn tồn tại.

## 5. Mẫu thay thế phù hợp cho từng nhu cầu

### 5.1. Tạo object bằng constructor không đối số

Nếu biết rõ class có public no-arg constructor:

```java
Class<MyService> type = MyService.class;

// Cũ (không nên tiếp tục viết):
// MyService service = type.newInstance();

// Mới:
MyService service = type.getDeclaredConstructor().newInstance();
```

Đây là snippet minh họa trong ngữ cảnh có lớp `MyService` và cơ chế xử lý checked exceptions; không phải file runnable độc lập.

`getDeclaredConstructor()` tìm constructor không đối số **kể cả private**. Nó chỉ trả metadata; việc gọi vẫn có thể bị chặn. Khi chủ đích chỉ hỗ trợ **public constructor**, sử dụng:

```java
MyService service = type.getConstructor().newInstance();
```

`getConstructor()` chỉ tìm public constructor, còn `getDeclaredConstructor()` tìm constructor ở mọi mức truy cập. **Không tự động bật `setAccessible(true)` cho mọi constructor** khi chuyển đổi API.

### 5.2. Tạo object có constructor tham số

`Class.newInstance()` không thể thực hiện tình huống này. Với constructor `public Worker(String name, int retries)`:

```java
Constructor<Worker> constructor =
        Worker.class.getConstructor(String.class, int.class);

Worker worker = constructor.newInstance("report", 3);
```

Đây là snippet cần khai báo `Worker` và import `Constructor` trong ngữ cảnh sử dụng.

Khi tra cứu constructor, các parameter types cần **khớp đúng chữ ký**. Ví dụ `int.class` khác `Integer.class`. Chọn đúng constructor trước khi gọi, thay vì mong `getConstructor` tự chọn overload theo đối số runtime. Khi thực thi `Constructor.newInstance`, Java có một số phép chuyển đổi đối số hợp lệ, nhưng điều này không làm `getConstructor` tự thay đổi luật tìm kiếm chữ ký.

### 5.3. Constructor private và kiểm tra module

Một số framework có nhu cầu tạo instance qua non-public constructor. Khi thiết kế thật sự đòi hỏi điều này, thực hiện có điều kiện:

```java
Constructor<SecretConfig> ctor =
        SecretConfig.class.getDeclaredConstructor(String.class);

if (!ctor.canAccess(null) && !ctor.trySetAccessible()) {
    throw new IllegalStateException(
            "Constructor không được phép mở quyền Reflection");
}

SecretConfig instance = ctor.newInstance("configured");
```

Đoạn này cần lớp `SecretConfig` được định nghĩa và xử lý checked exceptions, không phải chương trình độc lập. Với constructor, receiver cho `canAccess` luôn là `null`.

`trySetAccessible()` có thể trả `false` khi module chứa lớp đích không mở package tương ứng. `setAccessible(true)` có thể ném `InaccessibleObjectException`. Việc `getDeclaredConstructor` tìm thấy constructor private **không** đồng nghĩa đã được quyền sử dụng nó.

Với ứng dụng thông thường, public factory method, constructor injection hoặc dependency injection là lựa chọn dễ bảo trì hơn so với tự bỏ qua kiểm tra quyền truy cập private.

### 5.4. Tạo theo loại được chọn tại runtime

Khi nhận một `Class<? extends Handler>` từ registry tin cậy:

```java
static Handler create(Class<? extends Handler> implementation)
        throws ReflectiveOperationException {
    return implementation.getDeclaredConstructor().newInstance();
}
```

Hàm này cần các định nghĩa `Handler`/implementations ở dự án. Nó minh họa cách giữ kiểu trả về mà không ép kiểu không kiểm soát. Nếu lớp đã biết khi biên dịch, hãy ưu tiên `new`, constructor reference hoặc factory hơn Reflection.

Đối với tên class hoặc dữ liệu đầu vào từ nguồn không tin cậy, không tự động cho phép tạo bất kỳ class nào chỉ dựa trên chuỗi. Tạo whitelist/registry những implementation hợp lệ, xác nhận chúng triển khai interface mong đợi, và chỉ dùng constructor thuộc contract được cho phép.

## 6. Chương trình Java 17+ chạy được: so sánh hành vi cũ và mới

Ví dụ sau kiểm chứng bốn tình huống trong cùng một file:

1. Tạo bằng public constructor không đối số với API mới.
2. Thiếu constructor không đối số nhưng có constructor tham số.
3. Constructor ném checked `IOException`: API mới bọc lỗi, API cũ phát tán trực tiếp.
4. Constructor private: tìm metadata bằng `getDeclaredConstructor`, kiểm tra và yêu cầu mở quyền có kiểm soát.

### 6.1. Mã nguồn đầy đủ

Lưu vào file **`ConstructorMigrationDemo.java`**:

```java
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public class ConstructorMigrationDemo {
    public static class Basic {
        public Basic() {}
        @Override public String toString() { return "basic"; }
    }

    public static class NeedsArgument {
        private final String value;
        public NeedsArgument(String value) { this.value = value; }
        public String value() { return value; }
    }

    public static class Exploding {
        public Exploding() throws IOException {
            throw new IOException("disk unavailable");
        }
    }

    @SuppressWarnings("deprecation")
    static <T> T oldCreate(Class<T> type)
            throws InstantiationException, IllegalAccessException {
        return type.newInstance();
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Basic basic = Basic.class.getDeclaredConstructor().newInstance();
        System.out.println("zero-arg: " + basic);

        try {
            NeedsArgument.class.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            System.out.println("missing zero-arg: " + e.getClass().getSimpleName());
        }

        NeedsArgument withArg = NeedsArgument.class
                .getDeclaredConstructor(String.class)
                .newInstance("alpha");
        System.out.println("with-arg: " + withArg.value());

        try {
            Exploding.class.getDeclaredConstructor().newInstance();
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            System.out.println("modern error: " + e.getClass().getSimpleName());
            System.out.println("root cause: " + cause.getClass().getSimpleName()
                    + ": " + cause.getMessage());
        }

        try {
            oldCreate(Exploding.class);
        } catch (Exception e) {
            System.out.println("legacy error: " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
        }

        Constructor<LockedWidget> locked =
                LockedWidget.class.getDeclaredConstructor(String.class);
        System.out.println("private canAccess: " + locked.canAccess(null));
        boolean opened = locked.trySetAccessible();
        System.out.println("private opened: " + opened);
        if (opened) {
            LockedWidget widget = locked.newInstance("secret");
            System.out.println("private created: " + widget.code());
        }
    }
}

class LockedWidget {
    private final String code;
    private LockedWidget(String code) { this.code = code; }
    public String code() { return code; }
}
```

Trong chương trình, `LockedWidget` là **top-level class riêng**, không cùng nest với `ConstructorMigrationDemo`. Vì constructor private, `locked.canAccess(null)` ban đầu trả `false`. Các lớp cùng file nằm trong unnamed module khi chạy trên classpath; ở môi trường minh họa thông thường, `trySetAccessible()` được phép mở quyền. Nếu chuyển sang named modules có ràng buộc `opens`, hành vi có thể khác.

Code cũ chỉ xuất hiện **cố ý để minh họa sự khác biệt về exception**, có `@SuppressWarnings("deprecation")` chỉ tại helper `oldCreate`. Không dùng helper này trong code production mới.

### 6.2. Biên dịch và chạy

Dùng JDK 17 trở lên tại thư mục chứa file:

```bash
javac --release 17 ConstructorMigrationDemo.java
java ConstructorMigrationDemo
```

Kết quả đã được kiểm chứng khi biên dịch với `--release 17` và chạy trên OpenJDK 21:

```text
zero-arg: basic
missing zero-arg: NoSuchMethodException
with-arg: alpha
modern error: InvocationTargetException
root cause: IOException: disk unavailable
legacy error: IOException: disk unavailable
private canAccess: false
private opened: true
private created: secret
```

### 6.3. Giải thích kết quả

- `Basic.class.getDeclaredConstructor().newInstance()` tạo đối tượng hợp lệ bằng public no-arg constructor.
- `NeedsArgument` chỉ có constructor nhận `String`, nên `getDeclaredConstructor()` (không tham số) ném `NoSuchMethodException`; tra cứu theo `String.class` rồi khởi tạo thành công.
- `Exploding` ném `IOException("disk unavailable")` ngay trong constructor. `Constructor.newInstance()` báo `InvocationTargetException`; `getCause()` xác nhận `IOException` là lỗi gốc.
- `oldCreate(Exploding.class)` dùng API deprecated và thực tế ném **trực tiếp `IOException`**, dù chữ ký `oldCreate` chỉ khai báo `InstantiationException` và `IllegalAccessException`.
- `LockedWidget` có constructor private. `getDeclaredConstructor(String.class)` lấy được metadata; `canAccess(null)` cho biết hiện chưa được truy cập; `trySetAccessible()` thành công trong môi trường ví dụ, sau đó `newInstance("secret")` tạo đối tượng.

Điểm chứng minh quan trọng nhất: **API mới tách việc tìm constructor và gọi constructor, làm rõ điểm phát sinh lỗi, đồng thời bao bọc exception do constructor thực thi.**

## 7. Cách bắt lỗi và chuyển exception hợp lý

Đoạn mã sau là một helper tạo object bằng constructor không đối số có thể tái sử dụng:

```java
import java.lang.reflect.InvocationTargetException;

public final class Constructors {
    private Constructors() {}

    public static <T> T create(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    "Thiếu constructor không đối số: " + type.getName(), e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException(
                    "Constructor của " + type.getName() + " đã thất bại",
                    e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Không thể khởi tạo: " + type.getName(), e);
        }
    }
}
```

Đây là một **lớp tiện ích Java có thể biên dịch**, không có `main` riêng. Người dùng code gọi `Constructors.create(MyType.class)` khi lớp đích có zero-arg constructor hợp lệ. Helper này cố ý **không mở quyền private constructor**, và có thể thất bại nếu class/method không accessible theo luật Java/module.

Cách bắt này phân biệt:

1. `NoSuchMethodException`: không tìm thấy đúng constructor cần dùng.
2. `InvocationTargetException`: constructor có chạy và ném lỗi; chuyển lỗi gốc qua `getCause()`.
3. Các lỗi `ReflectiveOperationException` khác như `IllegalAccessException`, `InstantiationException`: khởi tạo bị cản bởi quyền hoặc tính chất của kiểu.

Chính sách wrap như `IllegalArgumentException`/`IllegalStateException` trong helper là **lựa chọn của ứng dụng**, không phải thứ Java Reflection bắt buộc. Nếu caller cần xử lý lỗi gốc theo từng loại, hãy giữ nguyên checked exceptions hoặc khai báo một domain exception có `cause`. Đừng nuốt lỗi hoặc chỉ log rồi trả `null` vì điều đó che giấu khởi tạo thất bại.

`InvocationTargetException` không phải exception duy nhất có thể gặp. Một lỗi từ quá trình **class initialization** có thể xuất hiện dưới dạng `ExceptionInInitializerError`; lỗi Java `Error` khác cũng không thuộc `ReflectiveOperationException` và cần được đánh giá theo chính sách ứng dụng.

## 8. Không có constructor mặc định? Đừng đoán

Đây là lỗi phổ biến khi di chuyển code cũ:

```java
class Job {
    Job(String id) {
    }
}
```

Vì `Job` khai báo constructor có tham số, compiler **không tự sinh** no-arg constructor. Kết quả:

```java
Job.class.getDeclaredConstructor(); // NoSuchMethodException
```

Không nên “sửa” bằng cách thêm constructor rỗng nếu domain bắt buộc `id`. Hãy khai báo chính xác constructor cần gọi:

```java
Job job = Job.class
        .getDeclaredConstructor(String.class)
        .newInstance("J-001");
```

Hai đoạn `Job` ở trên dùng làm minh họa ngắn trong cùng ngữ cảnh và cần bắt/khai báo checked exceptions khi chạy thực tế.

### 8.1. Constructor implicit và phạm vi truy cập

Nếu class không khai báo constructor nào, compiler có thể cung cấp **default constructor** không đối số theo luật Java. Tuy nhiên mức truy cập của constructor và khả năng truy cập lớp vẫn cần được xét. Một package-private class không tự trở thành public chỉ vì default constructor tồn tại.

Không nên dùng `Class.newInstance()` để đoán xem một class có thể khởi tạo hay không. Dùng `getDeclaredConstructor(...)` để xác định đúng chữ ký, kiểm tra quyền và xử lý rõ kết quả.

### 8.2. Record, enum và class đặc biệt

- **Record có components:** canonical constructor thường nhận các components theo đúng kiểu và thứ tự. Không thể mặc nhiên đòi có no-arg constructor. Có thể thêm constructor không đối số delegating hợp lệ trong một số thiết kế, nhưng không phải tự nhiên luôn có.
- **Enum:** không thể tùy ý tạo thêm enum constants qua `Constructor.newInstance`; Reflection có giới hạn riêng, và sẽ ném lỗi thích hợp.
- **Abstract class/interface:** không thể tạo instance trực tiếp bằng constructor Reflection.
- **Non-static inner class:** constructor ở mức bytecode thường cần thêm đối số enclosing instance; phải hiểu đúng chữ ký phản chiếu. `Class.newInstance()` không hỗ trợ việc truyền enclosing instance.
- **Array hoặc primitive `Class`:** không tạo như instance class thông thường bằng constructor Reflection; với array, dùng các cơ chế phù hợp như `java.lang.reflect.Array.newInstance` khi thực sự cần.

Một API khởi tạo động tốt phải có contract rõ về **những kiểu được phép tạo**, **constructor nào được hỗ trợ**, **tham số nào hợp lệ** và **exception nào được trả về**.

## 9. Bảng xử lý ngoại lệ khi dùng phương pháp mới

| Trường hợp | Bước điển hình | Kết quả |
|---|---|---|
| Không tồn tại constructor đúng parameter types | `getDeclaredConstructor(...)` | `NoSuchMethodException` |
| Constructor có tồn tại nhưng không được quyền gọi | `constructor.newInstance(...)` | `IllegalAccessException` |
| Cố mở deep reflection qua package chưa mở | `setAccessible(true)` / `trySetAccessible()` | Có thể gặp `InaccessibleObjectException` / trả `false` |
| Thiếu, thừa hoặc sai kiểu argument ở thời điểm gọi | `constructor.newInstance(args...)` | `IllegalArgumentException` |
| Đích abstract, không thể khởi tạo | `constructor.newInstance(...)` | `InstantiationException` |
| Constructor đích tự ném lỗi | `constructor.newInstance(...)` | `InvocationTargetException`, nguyên nhân trong `getCause()` |
| Class initialization thất bại | Trước hoặc lúc thực thi constructor | `ExceptionInInitializerError` |
| Cố tạo enum bằng constructor Reflection | `constructor.newInstance(...)` | `IllegalArgumentException` |
| Kiểm soát truy cập trong môi trường hạn chế | Tra cứu hoặc thao tác | Có thể có `SecurityException` |

Lưu ý: lỗi do class loader/linkage hoặc quyền truy cập module có thể xuất hiện từ bước lấy `Class` hoặc khi nạp class; bảng không nhằm liệt kê mọi `Error` của JVM.

`ReflectiveOperationException` là cha chung hữu ích của nhiều **checked exceptions** như `NoSuchMethodException`, `IllegalAccessException`, `InstantiationException` và `InvocationTargetException`; nó không bao phủ `IllegalArgumentException`, `InaccessibleObjectException` và `ExceptionInInitializerError`.

## 10. Những sai lầm thường gặp khi chuyển code cũ

**Sai lầm 1: Đổi tên API nhưng giữ nguyên cách bắt exception.** Sau khi chuyển sang `getDeclaredConstructor().newInstance()`, cần xử lý thêm `NoSuchMethodException` và `InvocationTargetException`. Nếu bỏ qua, code không biên dịch hoặc mất nguyên nhân từ constructor đích.

**Sai lầm 2: Bắt `Exception` rồi thông báo “constructor không tồn tại”.** `NoSuchMethodException` khác `IllegalAccessException` và khác `InvocationTargetException`. Hãy phân loại lỗi đúng bước.

**Sai lầm 3: Nghĩ `Class.newInstance()` chỉ deprecated vì “cú pháp cũ”.** Vấn đề checked exception bị phát tán trực tiếp là một lý do kỹ thuật thiết yếu, không đơn thuần là đổi tên phương thức.

**Sai lầm 4: Cứ dùng `getDeclaredConstructor()` là có quyền gọi private constructor.** API này tìm cả private constructor nhưng không tự cấp quyền truy cập. Bật suppression còn bị ràng buộc bởi module opens và ngữ cảnh caller.

**Sai lầm 5: Tự động `setAccessible(true)` cho mọi class.** Điều này xâm phạm ranh giới thiết kế, và có thể thất bại với JPMS. Nếu cần public contract, ưu tiên `getConstructor(...)`, public factory hoặc DI.

**Sai lầm 6: Dùng sai tham số tra cứu.** `getDeclaredConstructor(int.class)` khác `getDeclaredConstructor(Integer.class)`; cả số lượng lẫn thứ tự parameter types đều quan trọng.

**Sai lầm 7: Dùng Reflection khi không cần.** Nếu kiểu đã biết tại compile time, `new Widget(...)`, `Widget::new` hoặc `Supplier<Widget>` rõ ràng hơn, có compile-time type checking và không cần xử lý lỗi Reflection.

**Sai lầm 8: Mở rộng phạm vi kiểu tùy ý từ dữ liệu ngoài.** Khởi tạo class qua tên hoặc class loader bằng input không tin cậy có thể kích hoạt constructor/static initializer với side effects. Dùng registry hoặc danh sách implementation được cho phép.

**Sai lầm 9: Dùng một helper zero-arg cho record/class chỉ có constructor có tham số.** Nên xác định constructor phù hợp với domain và tạo object với các arguments bắt buộc, thay vì giả định mọi class đều có constructor mặc định.

## 11. Chọn phương án khởi tạo hợp lý

| Bối cảnh | Phương án nên ưu tiên | Lý do |
|---|---|---|
| Biết type/constructor tại compile time | `new SomeType(args)` | Rõ ràng, được compiler kiểm tra |
| Muốn truyền hành vi tạo object | `Supplier<T>` hoặc constructor reference | Giữ type safety, tránh Reflection |
| Class được chọn từ registry/plugin runtime | `getConstructor(...)` / `getDeclaredConstructor(...)` + `Constructor.newInstance(...)` | Hỗ trợ constructor có tham số, metadata và kiểm soát lỗi |
| Cần truy cập non-public constructor vì framework | `getDeclaredConstructor` rồi kiểm tra quyền rõ ràng | Tôn trọng Java access và JPMS |
| Ứng dụng dùng Spring/DI container | Cơ chế container/factory phù hợp | Giảm coupling vào chi tiết private constructor |
| Legacy code dùng `Class.newInstance()` | Di chuyển sang `Constructor.newInstance()` | Tránh API deprecated và checked-exception bypass |

### Checklist khi sửa code hiện có

1. Tìm các lời gọi `Class.newInstance()` thực tế, không nhầm với `Constructor.newInstance()`.
2. Xác định lớp đích và chữ ký constructor cần dùng. Nếu no-arg constructor không có, chọn overload đúng với thiết kế.
3. Dùng `getConstructor(...)` khi chỉ hỗ trợ public constructor; dùng `getDeclaredConstructor(...)` khi có lý do xét mọi mức truy cập.
4. Kiểm tra quyền truy cập/JPMS trước khi yêu cầu deep reflection.
5. Chuyển xử lý lỗi: phân biệt lỗi lookup, access, instantiation, arguments và exception phát sinh từ constructor đích.
6. Với `InvocationTargetException`, bảo tồn nguyên nhân gốc bằng `getCause()` (hoặc `getTargetException()`).
7. Kiểm thử những trường hợp khó: không có constructor đúng signature, constructor private, constructor ném checked exception, class abstract, tham số sai.
8. Xóa `@SuppressWarnings("deprecation")` cũ nếu không còn gọi API deprecated; không giữ suppression thừa để che cảnh báo khác.

## 12. Câu hỏi tự kiểm tra

1. Vì sao `Class.newInstance()` bị deprecated từ Java 9 dù vẫn tồn tại trong Java 17?
2. Tại sao constructor `Loader() throws IOException` có thể khiến `Class.newInstance()` ném trực tiếp `IOException` mà caller không khai báo?
3. `InvocationTargetException` giúp phân biệt lỗi ở giai đoạn nào? Cách lấy nguyên nhân gốc?
4. `getConstructor` và `getDeclaredConstructor` khác nhau về phạm vi visibility như thế nào?
5. Với `Worker(String, int)`, vì sao `getDeclaredConstructor()` không tham số thất bại? Viết signature đúng.
6. `trySetAccessible()` có bảo đảm truy cập được constructor private của named module khác không?
7. Khi nào dùng `new` hoặc `Supplier` thay cho cả hai dạng Reflection?
8. Vì sao việc đổi `Class.newInstance()` sang `getDeclaredConstructor().newInstance()` đòi hỏi xem lại catch/throws?

**Ghi nhớ:** Hãy dùng `Constructor.newInstance(...)` khi thật sự cần khởi tạo động bằng Reflection. Nó cho phép chọn constructor có tham số, kiểm tra metadata và quyền rõ ràng, và **đóng gói exception do constructor ném vào `InvocationTargetException`**. `Class.newInstance()` đã deprecated từ Java 9, chỉ hỗ trợ constructor không đối số và có thể bỏ qua cơ chế kiểm tra checked exception của compiler.

Tài liệu chuẩn: [Java SE 17 — `Class.newInstance()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#newInstance()), [`Class.getDeclaredConstructor`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html#getDeclaredConstructor(java.lang.Class...)), [`Constructor.newInstance`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Constructor.html#newInstance(java.lang.Object...)), [`InvocationTargetException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationTargetException.html).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="constructors-011">Khởi tạo lớp và lỗi xảy ra trước hoặc trong constructor</a>

<details>
<summary>Xem chi tiết</summary>

# Khởi tạo lớp và lỗi xảy ra trước hoặc trong constructor khi dùng Java Reflection

Khi tạo đối tượng bằng Reflection, nhiều lập trình viên thấy `Constructor.newInstance(...)` ném lỗi rồi kết luận rằng *constructor bị lỗi*. Kết luận này có thể sai: **JVM phải bảo đảm lớp đã được khởi tạo (class initialization) trước khi thực sự chạy constructor**. Lỗi có thể xảy ra trong static field initializer, static block, quá trình khởi tạo superclass, kiểm tra quyền truy cập, khớp tham số, instance initializer, superclass constructor hoặc thân constructor đang gọi.

Bài này tập trung phân biệt **khởi tạo lớp (`<clinit>`)** với **khởi tạo đối tượng (`<init>`)** và xác định lỗi ở giai đoạn nào khi dùng `Class`, `Constructor` và `Constructor.newInstance(...)`. Các ví dụ chạy được bằng Java 17+.

## 1. Hai khái niệm thường bị gọi chung là "khởi tạo"

### Class initialization: khởi tạo lớp

*Class initialization* là quá trình JVM thực thi các static field initializer và static initializer block thuộc một lớp, theo thứ tự văn bản khai báo và theo các quy tắc của Java Language Specification. JVM cần khởi tạo superclass liên quan trước khi khởi tạo lớp con, đồng thời bảo đảm quá trình khởi tạo một lớp được điều phối giữa các thread.

Ví dụ:

~~~java
class Service {
    static String config = loadConfig();

    static {
        System.out.println("Service static block");
    }

    private static String loadConfig() {
        System.out.println("Service static field");
        return "ready";
    }
}
~~~

Đây là **mã minh họa**, chưa có `main`. Trong lần khởi tạo `Service` đầu tiên, lời gọi `loadConfig()` in trước, rồi static block in sau. Hai hành động này thuộc **khởi tạo lớp**, không phải thân constructor. Trong bytecode, phần việc đó được JVM tổ chức trong phương thức khởi tạo lớp đặc biệt `<clinit>`.

**Không đồng nhất ba trạng thái:**

1. **Loading (nạp):** JVM tìm và định nghĩa cấu trúc lớp.
2. **Linking (liên kết):** xác minh, chuẩn bị và có thể phân giải các tham chiếu.
3. **Initialization (khởi tạo):** thực thi logic khởi tạo static nếu chưa thực thi xong.

Lấy được đối tượng `Class<?>` không tự chứng minh rằng initialization đã diễn ra.

### Object initialization: khởi tạo đối tượng

Khi thực sự tạo instance của một class thông thường, JVM cấp phát đối tượng rồi thực hiện chuỗi constructor theo quy tắc Java:

- Superclass constructor được gọi trong quy trình tạo instance trước khi chạy phần thân constructor của subclass.
- Các instance field initializer và instance initializer block của mỗi class chạy vào thời điểm thích hợp sau khi gọi superclass constructor và trước phần thân constructor tương ứng.
- Thân constructor có thể gán field, kiểm tra tham số, gọi service và ném exception.

Constructor xuất hiện trong bytecode với tên đặc biệt `<init>`. Đây **không phải** `<clinit>`; một class có thể được khởi tạo một lần rồi tạo nhiều instance bằng nhiều lời gọi constructor.

## 2. Reflection kích hoạt giai đoạn nào?

| Thao tác | Tự nó có bắt buộc khởi tạo class đích không? | Điều cần nhớ |
| --- | --- | --- |
| `SomeClass.class` | Không | Lấy class literal, chưa chạy static initializer chỉ vì thao tác này |
| `SomeClass.class.getDeclaredConstructor(...)` | Không | Chỉ tra cứu metadata/chữ ký constructor |
| `getDeclaredConstructors()`, `getConstructor(...)` | Không | Khám phá constructor; không tạo instance |
| `Class.forName("pkg.SomeClass")` | Có, mặc định yêu cầu khởi tạo | Lỗi static initializer có thể nổ **ngay tại `forName`** |
| `Class.forName(name, false, loader)` | Không yêu cầu khởi tạo class đích | Hữu ích khi muốn nạp/tra cứu metadata trước |
| `constructor.newInstance(args...)` | Có, nếu đủ điều kiện đi tới tạo instance và class chưa khởi tạo | JVM phải khởi tạo class trước khi thực thi constructor |
| `new SomeClass(...)` | Có | Tương tự về nhu cầu khởi tạo class, nhưng đường ngoại lệ khác Reflection |
| Truy cập/gọi một static member đủ điều kiện gây active use | Có thể | Có ngoại lệ về constant variables và quy tắc `JLS 12.4.1` |

**Chú ý phạm vi:** "không khởi tạo class đích" ở bảng không có nghĩa API không thể kích hoạt bất cứ hoạt động nạp/liên kết kiểu liên quan nào. Điều chắc chắn có ích trong bài là `getDeclaredConstructor` không chạy static block của class đích như hành vi mặc định của `Constructor.newInstance`.

Ví dụ hai lời gọi khác nhau:

~~~java
Class<?> metadataOnly = Class.forName(
        "com.example.Service", false,
        Thread.currentThread().getContextClassLoader());

Class<?> initialized = Class.forName("com.example.Service");
~~~

Đây là **mã minh họa**; cần class `com.example.Service` tồn tại và được class loader nhìn thấy. Lời gọi đầu không yêu cầu khởi tạo class đích; lời gọi thứ hai yêu cầu khởi tạo. Nếu class đã được khởi tạo từ trước, JVM không lặp lại static initializer chỉ vì có lời gọi mới.

## 3. Thứ tự class initialization và constructor trong một lần tạo object

Xét một lớp con kế thừa lớp cha. Trong tình huống class chưa được khởi tạo và gọi constructor thành công, thứ tự dễ quan sát như sau:

~~~text
Tìm Constructor bằng getDeclaredConstructor()
    ↓ không thực thi static initializer
Gọi Constructor.newInstance()
    ↓ kiểm tra điều kiện, chuẩn bị gọi
Khởi tạo superclass cần thiết
    ↓ static fields/static blocks của superclass
Khởi tạo class đích
    ↓ static fields/static blocks của class đích
Tạo instance và thực hiện chuỗi constructor
    ↓ superclass constructor; instance initializers
Chạy thân constructor của class đích
    ↓
newInstance() trả về đối tượng
~~~

Sơ đồ này mô tả đường thành công; một lỗi ở bất kỳ giai đoạn liên quan có thể cắt ngang các bước phía sau. Trình tự kiểm tra quyền, đối số và một số lỗi xảy ra trước khi class initialization còn phụ thuộc điều kiện cụ thể của API, nên đừng dùng sơ đồ để khẳng định thứ tự tất cả các lỗi kiểm tra đầu vào.

### Ví dụ đầy đủ 1: metadata không khởi tạo, `newInstance` mới khởi tạo

Tạo tệp **`ConstructorInitOrderDemo.java`**:

~~~java
import java.lang.reflect.Constructor;

class ParentStage {
    static {
        System.out.println("ParentStage.<clinit>");
    }

    ParentStage() {
        System.out.println("ParentStage.<init>");
    }
}

class ChildStage extends ParentStage {
    static {
        System.out.println("ChildStage.<clinit>");
    }

    ChildStage() {
        System.out.println("ChildStage.<init>");
    }
}

public class ConstructorInitOrderDemo {
    public static void main(String[] args)
            throws ReflectiveOperationException {
        System.out.println("Metadata lookup start");

        Class<ChildStage> type = ChildStage.class;
        Constructor<ChildStage> ctor = type.getDeclaredConstructor();

        System.out.println("Constructor found");
        ChildStage created = ctor.newInstance();

        System.out.println("Instance created: "
                + (created != null));
    }
}
~~~

Chạy trong cùng thư mục:

~~~bash
javac --release 17 ConstructorInitOrderDemo.java
java ConstructorInitOrderDemo
~~~

**Stdout:**

~~~text
Metadata lookup start
Constructor found
ParentStage.<clinit>
ChildStage.<clinit>
ParentStage.<init>
ChildStage.<init>
Instance created: true
~~~

Giải thích:

1. `ChildStage.class` và `getDeclaredConstructor()` cho phép lấy metadata mà chưa chạy static block của `ChildStage`.
2. `ctor.newInstance()` yêu cầu khởi tạo `ChildStage`. `ParentStage` cần được khởi tạo trước; hai dòng `<clinit>` xuất hiện theo thứ tự superclass rồi subclass.
3. Khi bắt đầu thực thi chuỗi constructor, `ParentStage()` chạy trước `ChildStage()`, nên hai dòng `<init>` xuất hiện sau static initializers.
4. `newInstance` hoàn tất và trả object, nên dòng `Instance created: true` xuất hiện cuối.

Trong một chương trình có nhiều lần tạo object, static block thường chỉ chạy **một lần cho mỗi lần khởi tạo thành công của class** tương ứng trong một runtime/class loader; constructor chạy **mỗi lần tạo instance**.

## 4. Lỗi **trước khi** thân constructor bắt đầu: static initialization

Nếu một static field initializer hoặc static block ném exception trong quá trình class initialization, thân constructor của class đích **chưa được chạy**. Với lỗi thuộc nhóm exception thông thường không phải `Error`, JVM biểu diễn lỗi khởi tạo bằng `ExceptionInInitializerError`. Ngoại lệ gây ra lỗi có thể đọc qua `getCause()`.

Ví dụ:

~~~java
class Broken {
    static final String VALUE = fail();

    private static String fail() {
        throw new IllegalStateException("cannot load configuration");
    }

    Broken() {
        System.out.println("Broken constructor entered");
    }
}
~~~

Đoạn này chỉ minh họa. Nếu tạo instance của `Broken` lần đầu và `fail()` ném `IllegalStateException`, JVM không tới được dòng in trong constructor.

### Ví dụ đầy đủ 2: lần đầu `ExceptionInInitializerError`, lần sau `NoClassDefFoundError`

Tạo tệp **`ConstructorStaticFailureDemo.java`**:

~~~java
import java.lang.reflect.Constructor;

class BrokenInitialization {
    static final String VALUE = fail();

    private static String fail() {
        System.out.println("BrokenInitialization.<clinit>");
        throw new IllegalStateException("static boom");
    }

    BrokenInitialization() {
        System.out.println("Constructor entered");
    }
}

public class ConstructorStaticFailureDemo {
    public static void main(String[] args)
            throws ReflectiveOperationException {
        Constructor<BrokenInitialization> ctor =
                BrokenInitialization.class.getDeclaredConstructor();

        System.out.println("Broken constructor found");

        try {
            ctor.newInstance();
        } catch (ExceptionInInitializerError error) {
            System.out.println("First: "
                    + error.getClass().getSimpleName());
            System.out.println("Cause: "
                    + error.getCause().getClass().getSimpleName());
        }

        try {
            ctor.newInstance();
        } catch (NoClassDefFoundError error) {
            System.out.println("Second: "
                    + error.getClass().getSimpleName());
        }
    }
}
~~~

~~~bash
javac --release 17 ConstructorStaticFailureDemo.java
java ConstructorStaticFailureDemo
~~~

**Stdout:**

~~~text
Broken constructor found
BrokenInitialization.<clinit>
First: ExceptionInInitializerError
Cause: IllegalStateException
Second: NoClassDefFoundError
~~~

**Vì sao có hai lỗi khác nhau?**

- Dòng đầu xác nhận lấy `Constructor` **không kích hoạt** static initialization.
- Lần `newInstance` đầu tiên đưa JVM vào khởi tạo `BrokenInitialization`; `fail()` ném `IllegalStateException`. JVM báo `ExceptionInInitializerError` tại ranh giới khởi tạo class.
- Dòng `Constructor entered` **không bao giờ xuất hiện**, vì class chưa hoàn tất initialization.
- Class thất bại initialization bị đánh dấu ở trạng thái **erroneous** đối với runtime đang xét. Lần active use tiếp theo cùng class thường gặp `NoClassDefFoundError` do class không thể được khởi tạo lại theo cách đơn giản trong cùng class loader.

`NoClassDefFoundError` ở đây **không** có nghĩa bytecode hoặc file `.class` tự nhiên biến mất. Nó có thể biểu thị class đã rơi vào trạng thái lỗi khởi tạo. Để tìm nguyên nhân, phải truy ngược log/stack trace về lỗi `ExceptionInInitializerError` lần đầu, thay vì chỉ truy tìm đường dẫn classpath.

**Ngoại lệ quan trọng:** nếu static initializer ném trực tiếp một `Error` (ví dụ một `Error` cụ thể do ứng dụng tạo hoặc lỗi VM), JVM có thể truyền chính `Error` đó thay vì bọc nó trong `ExceptionInInitializerError`. Vì vậy không nên viết logic bắt lỗi class initialization chỉ dựa vào một loại wrapper trong mọi tình huống.

## 5. Lỗi **trong** constructor: `InvocationTargetException`

Khác với static initialization trước khi vào constructor, khi code bên trong constructor ném exception, `Constructor.newInstance(...)` phản ánh lỗi đó qua `java.lang.reflect.InvocationTargetException`. Exception gốc nằm trong:

~~~java
Throwable original = invocationTargetException.getCause();
~~~

Đây là **đoạn mã minh họa**. `InvocationTargetException` cho biết phép gọi phản chiếu đã đi tới code thực thi, và một throwable xuất hiện từ code được gọi; nó không mô tả đầy đủ vị trí sâu nhất của lỗi nếu constructor gọi tiếp nhiều method khác.

### Ví dụ đầy đủ 3: class khởi tạo thành công nhưng constructor ném exception

Tạo tệp **`ConstructorBodyFailureDemo.java`**:

~~~java
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

class FailsInConstructor {
    static {
        System.out.println("FailsInConstructor.<clinit>");
    }

    FailsInConstructor() {
        System.out.println("FailsInConstructor.<init>");
        throw new IllegalArgumentException("ctor boom");
    }
}

public class ConstructorBodyFailureDemo {
    public static void main(String[] args)
            throws ReflectiveOperationException {
        Constructor<FailsInConstructor> ctor =
                FailsInConstructor.class.getDeclaredConstructor();

        try {
            ctor.newInstance();
        } catch (InvocationTargetException error) {
            System.out.println("Wrapper: "
                    + error.getClass().getSimpleName());
            System.out.println("Cause: "
                    + error.getCause().getClass().getSimpleName());
        }
    }
}
~~~

~~~bash
javac --release 17 ConstructorBodyFailureDemo.java
java ConstructorBodyFailureDemo
~~~

**Stdout:**

~~~text
FailsInConstructor.<clinit>
FailsInConstructor.<init>
Wrapper: InvocationTargetException
Cause: IllegalArgumentException
~~~

Phân tích: static initializer chạy thành công trước. Dòng `<init>` xác nhận constructor đã bắt đầu. Chính thân constructor ném `IllegalArgumentException`, vì vậy Reflection báo `InvocationTargetException` và giữ lỗi gốc làm `cause`.

**Một constructor ném exception không nhất thiết khiến class ở trạng thái erroneous.** Class trong ví dụ đã khởi tạo static thành công; những lời gọi constructor tiếp theo có thể được thử lại, dù constructor vẫn tiếp tục ném exception do logic của nó. Điều này khác với static initialization thất bại ở phần trước.

### Lỗi trong instance field initializer hoặc constructor của superclass

"Trong constructor" theo góc nhìn `Constructor.newInstance` bao gồm cả code được chạy trong quá trình tạo đối tượng, **không chỉ** những dòng trực tiếp trong thân constructor của class đích:

- Superclass constructor ném lỗi trước khi thân subclass constructor được thực hiện.
- Instance field initializer/instance initializer block của class đích ném lỗi.
- Constructor gọi một method khác và method đó ném lỗi.
- Constructor kích hoạt lần đầu một class phụ thuộc có static initializer bị lỗi.

Trong những trường hợp throwable phát sinh sau khi việc gọi constructor phản chiếu đã bắt đầu thực thi code khởi tạo đối tượng, `InvocationTargetException` có thể bọc throwable sâu hơn, bao gồm một `ExceptionInInitializerError` từ **class phụ thuộc được khởi tạo trong constructor**. Vì vậy **phải xem toàn bộ cause chain và stack trace**, không chỉ nhìn một wrapper ngoài cùng để đoán vị trí.

## 6. Bảng phân biệt các lỗi theo thời điểm

| Lỗi | Giai đoạn điển hình | Ý nghĩa và hướng xử lý |
| --- | --- | --- |
| `NoSuchMethodException` | Tra cứu `getDeclaredConstructor(...)` | Không có constructor đúng danh sách kiểu tham số; chưa đến bước gọi |
| `IllegalAccessException` | Gọi `Constructor.newInstance(...)` khi thiếu quyền | Constructor có nhưng caller không có quyền truy cập phù hợp |
| `InaccessibleObjectException` | Gọi `setAccessible(true)` | Module đích không cho phép mở quyền sâu trong điều kiện hiện tại |
| `IllegalArgumentException` | Xác thực đối số khi `newInstance(...)` | Số lượng/loại đối số không phù hợp với signature hoặc thao tác không hợp lệ |
| `InstantiationException` | Tạo instance một kiểu không thể instantiate (ví dụ abstract class) | Constructor metadata có thể tồn tại, nhưng không thể tạo instance trực tiếp |
| `ExceptionInInitializerError` | Class initialization trước constructor, khi exception thông thường xảy ra | Xem cause của lỗi static initialization, constructor chưa bắt đầu |
| `NoClassDefFoundError` | Sử dụng class đã khởi tạo thất bại hoặc không định nghĩa được class | Xem cause/stack trace, đặc biệt log lần đầu class initialization thất bại |
| `InvocationTargetException` | Throwable từ code được gọi trong quá trình chạy constructor | Xem `getCause()` để tìm lỗi thực tế |
| `LinkageError` và một số `Error` khác | Nạp/liên kết/khởi tạo class hoặc thực thi | Có thể truyền trực tiếp; phân tích theo loại lỗi và nguyên nhân gốc |

**Đừng nhầm `NoSuchMethodException` với `NoClassDefFoundError`:** cái đầu nói lookup constructor không có chữ ký đã hỏi; cái sau liên quan đến việc class không thể được định nghĩa/khởi tạo/thực thi như yêu cầu. Cũng đừng tự động bắt `Exception` rồi cho rằng đã bắt đủ class initialization errors vì `ExceptionInInitializerError` và `NoClassDefFoundError` đều thuộc nhánh `Error` của `Throwable`.

Cả `InvocationTargetException` và `ExceptionInInitializerError` có thể xuất hiện trong cùng một chuỗi nguyên nhân ở chương trình phức tạp. Hãy phân biệt lỗi *khởi tạo chính class đang new* với lỗi *khởi tạo class phụ thuộc trong thân constructor*.

## 7. Vai trò của `Class.forName`: lỗi có thể xảy ra sớm hơn bạn nghĩ

Nếu trình tự mã là:

~~~java
Class<?> type = Class.forName("com.example.Broken");
Constructor<?> ctor = type.getDeclaredConstructor();
Object value = ctor.newInstance();
~~~

Đây là **mã minh họa** với class `com.example.Broken` giả định. `Class.forName(String)` mặc định yêu cầu khởi tạo class, nên static initializer có thể lỗi **ngay ở dòng đầu**, trước khi `getDeclaredConstructor` được gọi. Nếu mục tiêu là phân tích metadata trước khi active use, dùng overload:

~~~java
ClassLoader loader = Thread.currentThread().getContextClassLoader();

Class<?> type = Class.forName(
        "com.example.Broken", false, loader);

Constructor<?> ctor = type.getDeclaredConstructor();
// Chỉ khi gọi ctor.newInstance() mới yêu cầu class đích khởi tạo.
~~~

Đoạn này cũng là **mã minh họa**, cần class có thật, quyền Reflection hợp lệ và xử lý ngoại lệ. `initialize=false` trì hoãn **initialization** chứ không biến class lỗi thành class có thể khởi tạo thành công. Việc nạp/liên kết hoặc kiểm tra class loader vẫn có thể gặp lỗi riêng.

Không dùng phương án `Class.forName(name, false, loader)` như cơ chế bảo mật để chạy code không tin cậy; nó chỉ thay đổi thời điểm class initialization chứ không loại bỏ các tác động khi class được sử dụng về sau.

## 8. Constructor Reflection còn có thể thất bại trước khi thực thi mã ứng dụng

### Chữ ký phải khớp tuyệt đối

~~~java
Constructor<?> ctor = Example.class
        .getDeclaredConstructor(String.class, int.class);
~~~

Đoạn này chỉ hợp lệ khi `Example` khai báo constructor đúng danh sách tham số `String, int`. Reflection **không** dùng autoboxing hoặc widening để tìm constructor theo "chữ ký gần đúng":

- `int.class` khác `Integer.class`.
- `String[].class` khác `String.class`; varargs `String...` được biểu diễn là tham số mảng `String[]`.
- Parameterized type như `List<String>` chịu type erasure ở runtime và thường tra theo `List.class`.
- Constructor không được kế thừa, nên không thể tra constructor của superclass trên `Class<?>` của subclass.

Sai chữ ký làm `getDeclaredConstructor` ném `NoSuchMethodException`, ngay cả khi constructor khác có thể tạo instance hợp lệ.

### Quyền truy cập và ranh giới module

`getDeclaredConstructor` có thể trả metadata cho constructor `private`, nhưng `newInstance` còn kiểm tra truy cập. Nếu cần deep reflection, `trySetAccessible()` có thể trả `false` khi module không cho phép; `setAccessible(true)` có thể ném `InaccessibleObjectException`. Nếu caller và target cùng module, thường có thể mở quyền sâu theo quy tắc JPMS, nhưng chỉ nên làm khi có mục tiêu chính đáng và đã xử lý điều kiện thất bại.

Một pattern an toàn:

~~~java
Constructor<?> ctor = type.getDeclaredConstructor(String.class);

if (!ctor.canAccess(null) && !ctor.trySetAccessible()) {
    throw new IllegalStateException("Cannot access constructor");
}

Object instance = ctor.newInstance("demo");
~~~

Đây là **đoạn mã minh họa**, với `type` là `Class<?>` hợp lệ. `Constructor#canAccess` dùng đối số `null`. Với field/method instance sẽ dùng receiver tương ứng. Nếu code dừng ở bước kiểm tra quyền, đó **không phải** lỗi trong thân constructor.

### Kiểu không được tạo instance trực tiếp

Một abstract class có thể có constructor để subclass gọi `super(...)`, và Reflection có thể tìm metadata của constructor đó, nhưng `Constructor.newInstance` vẫn không thể tạo object trực tiếp của abstract class: có thể ném `InstantiationException`. Interface không có constructor instance để tra cứu theo cách thông thường. Constructor enum cũng chịu hạn chế riêng khi khởi tạo qua Reflection; đừng dùng `Constructor.newInstance` để tạo enum constant mới.

## 9. Cách đọc stack trace để tìm gốc lỗi

Một quy trình chẩn đoán hữu ích:

1. **Đánh dấu giai đoạn:** log sau khi có `Class<?>`, sau khi tìm `Constructor<?>`, ngay trước và ngay sau `newInstance`. Thông tin này giúp biết lỗi xảy ra ở lookup, initialization hay invocation.
2. **Phân loại throwable:** `NoSuchMethodException` ở lookup; lỗi truy cập/đối số trước khi vào code đích; `ExceptionInInitializerError` liên quan initialization; `InvocationTargetException` liên quan throwable trong việc thực thi constructor.
3. **Đi sâu vào cause:** in class name, message và `getCause()` lặp lại có giới hạn hoặc ghi đầy đủ stack trace vào log an toàn.
4. **Xác định class thực sự thất bại:** có thể là superclass, class đích hoặc class phụ thuộc được active-use trong constructor.
5. **Nếu gặp `NoClassDefFoundError` ở lần sau:** tìm log **lần sử dụng đầu tiên**; lỗi thứ hai thường chỉ là hậu quả của init failure đầu.
6. **Sửa nguyên nhân gốc:** cấu hình static, file, classpath/module path, dependency, quyền truy cập hoặc logic constructor; hạn chế retry mù quáng nếu class đã ở trạng thái erroneous.

Ví dụ đoạn mã phân loại trong ứng dụng (giả sử `ctor` đã xác định và quyền truy cập hợp lệ):

~~~java
try {
    Object instance = ctor.newInstance();
    System.out.println("Created: " + instance.getClass().getName());
} catch (java.lang.reflect.InvocationTargetException e) {
    Throwable actual = e.getCause();
    System.err.println("Constructor path failed: " + actual);
} catch (ExceptionInInitializerError e) {
    System.err.println("Class initialization failed: " + e.getCause());
} catch (NoClassDefFoundError e) {
    System.err.println("Class cannot be initialized/defined: " + e);
}
~~~

Đây là **mã minh họa chẩn đoán**, cần được đặt trong phương thức khai báo/xử lý thêm checked exceptions như `InstantiationException` và `IllegalAccessException`; nó không phải chương trình độc lập. Log thực tế phải tránh tiết lộ bí mật từ exception message hoặc stack trace nếu dữ liệu nhạy cảm.

## 10. Khởi tạo thất bại có thể được chạy lại không?

Nếu static initialization của class **thất bại**, JVM đánh dấu class ở trạng thái erroneous trong runtime định nghĩa class đó. Một lần `newInstance` thứ hai trên **chính class ấy** thường sẽ thất bại với `NoClassDefFoundError`; không có API Reflection chuẩn để "reset" trạng thái class và chạy lại static initializer của cùng `Class<?>`.

Muốn phục hồi, thường phải xử lý nguyên nhân trước khi JVM khởi tạo class (ví dụ cấu hình hợp lệ), khởi động lại tiến trình, hoặc trong kiến trúc được thiết kế đặc biệt, nạp một bản định nghĩa class mới bằng class loader khác và xử lý lifecycle tương ứng. Đừng dùng loader mới làm giải pháp chữa cháy chung vì nó có thể thay đổi danh tính kiểu và quản lý tài nguyên phức tạp hơn.

Ngược lại, nếu **constructor** ném exception sau khi class đã init thành công, class không bị đánh dấu erroneous chỉ vì constructor thất bại. Lời gọi constructor tiếp theo có thể chạy lại và có thể thành công nếu điều kiện đầu vào/trạng thái cho phép.

### Vấn đề đa luồng

Initialization của một class được JVM đồng bộ theo quy tắc riêng. Một thread khởi tạo class và thất bại thì những thread khác cần sử dụng class đó có thể nhận lỗi do class không khởi tạo được. Không nên viết logic chỉ nhìn `ExceptionInInitializerError` để xác nhận tất cả các thread đều quan sát cùng một exception wrapper. Giữ nguyên cause chain và log sự kiện đầu giúp điều tra chính xác hơn.

## 11. Liên hệ với `Class.newInstance()` cũ

`Class.newInstance()` đã deprecated từ Java 9 và có thể để ngoại lệ checked từ constructor thoát ra theo cách gây khó khăn cho kiểm tra ngoại lệ. Ưu tiên `getDeclaredConstructor().newInstance()` để chọn constructor và nhận `InvocationTargetException#getCause()` cho lỗi trong quá trình khởi tạo instance. Cả hai API vẫn tuân theo quy tắc **class phải khởi tạo thành công trước khi constructor có thể chạy**; vì vậy phải phân biệt lỗi static initialization với lỗi trong constructor.

## 12. Các lỗi suy luận thường gặp

1. **Tưởng `getDeclaredConstructor` chạy static block.** Đây là lookup metadata; active use thông qua `newInstance` hoặc `Class.forName(name)` mới có thể yêu cầu initialization.
2. **Đồng nhất `<clinit>` với `<init>`.** `<clinit>` là class initialization; `<init>` là constructor của mỗi instance.
3. **Thấy `ExceptionInInitializerError` rồi sửa constructor body.** Trước hết phải kiểm tra static field initializer/static block và initialization của superclass.
4. **Thấy `InvocationTargetException` rồi chỉ log wrapper.** Lấy `getCause()` để thấy throwable thực sự từ constructor path.
5. **Thấy `NoClassDefFoundError` rồi kết luận class file bị xóa.** Có thể class init đã thất bại trước đó; tìm log ban đầu.
6. **Cho rằng mọi init failure đều bọc `ExceptionInInitializerError`.** Lỗi thuộc nhánh `Error` có thể truyền trực tiếp.
7. **Cho rằng constructor ném exception khiến class unusable mãi mãi.** Điều này khác class initialization thất bại; một constructor call lỗi không tự biến class thành erroneous.
8. **Bắt mỗi `Exception` để xử lý mọi loại thất bại.** `ExceptionInInitializerError`, `NoClassDefFoundError` thuộc `Error`; phân loại rõ rồi để lỗi nghiêm trọng đi lên khi phù hợp.
9. **Tự động mở toàn bộ module vì constructor `private`.** Quy tắc JPMS và quyền truy cập phải được xử lý cụ thể; mở module không sửa lỗi static initialization.
10. **Retry liên tục cùng `Constructor` sau khi class init thất bại.** Việc gọi lại không reset static state; có thể chỉ tạo chuỗi `NoClassDefFoundError`.
11. **Lẫn lỗi sai tham số với lỗi bên trong constructor.** `IllegalArgumentException` do `newInstance` kiểm tra argument khác với `InvocationTargetException` có `cause` là `IllegalArgumentException` do constructor tự ném.
12. **Không để ý superclass/instance initializer.** Lỗi ở code khởi tạo object của superclass hay initializer có thể xảy ra trước dòng đầu thân constructor của subclass, dù class initialization của subclass đã thành công.

## 13. Bài tự kiểm tra

**Câu 1.** `SomeType.class.getDeclaredConstructor()` có bảo đảm static block của `SomeType` đã chạy không?

**Đáp án:** Không. Lấy metadata constructor không tự khởi tạo class đích.

**Câu 2.** Static field initializer của class đích ném `IllegalStateException` ở lần active use đầu. `Constructor.newInstance` thường biểu hiện lỗi gì?

**Đáp án:** `ExceptionInInitializerError` có `cause` là `IllegalStateException`, phát sinh trước khi constructor body bắt đầu.

**Câu 3.** Sau init failure, vì sao gọi lại cùng `Constructor.newInstance` thường ném `NoClassDefFoundError`?

**Đáp án:** JVM đánh dấu class đã khởi tạo thất bại ở trạng thái erroneous, nên không chạy lại static initializer như một lần thử mới bình thường.

**Câu 4.** Static initializer chạy thành công, nhưng constructor ném `IllegalArgumentException`. Reflection trả lỗi gì?

**Đáp án:** `InvocationTargetException`; gọi `getCause()` sẽ thấy `IllegalArgumentException`.

**Câu 5.** Nếu `Class.forName("pkg.Broken")` được gọi trước lookup constructor thì init failure xảy ra ở đâu?

**Đáp án:** Có thể nổ ngay tại `Class.forName(String)`, vì overload này mặc định yêu cầu initialization.

**Câu 6.** Constructor của class con ném lỗi từ superclass constructor trước khi subclass body bắt đầu. Có thể thấy `InvocationTargetException` không?

**Đáp án:** Có. Đường thực thi gọi constructor bao gồm bước gọi superclass constructor; `InvocationTargetException#getCause()` giúp truy ngược throwable từ quá trình khởi tạo đối tượng.

**Câu 7.** `NoSuchMethodException` khi gọi `getDeclaredConstructor(int.class)` có đồng nghĩa lỗi class initialization không?

**Đáp án:** Không. Đây là thất bại của tra cứu chữ ký constructor, xảy ra trước việc gọi constructor.

**Câu 8.** Class đã init thành công, constructor thất bại vì một input cụ thể. Có thể khởi tạo instance khác không?

**Đáp án:** Có thể nếu sửa đầu vào/điều kiện và gọi constructor lại; class không trở thành erroneous chỉ vì một constructor call thất bại.

## 14. Checklist phân tích nhanh

Khi lỗi Reflection liên quan constructor, hãy ghi lại:

- **Class đích:** tên `Class#getName()`, class loader và module khi liên quan.
- **Metadata:** constructor tìm được chưa? Chữ ký `getParameterTypes()` đúng chưa?
- **Quyền:** `canAccess(null)`, `trySetAccessible()`, khả năng giới hạn bởi JPMS.
- **Điểm lỗi:** `Class.forName`, `getDeclaredConstructor`, `newInstance` hay một lời gọi bên trong constructor?
- **Throwable ngoài cùng và gốc:** `getCause()`, stack trace đầy đủ; có `ExceptionInInitializerError`/`NoClassDefFoundError` hay `InvocationTargetException`?
- **Thời điểm:** lần active use đầu hay lần dùng tiếp theo? Static initialization đã thành công chưa?
- **Superclass và dependency:** exception thực sự bắt nguồn từ initializer/constructor nào?

**Ghi nhớ:** `getDeclaredConstructor()` tìm metadata; `Constructor.newInstance()` yêu cầu class sẵn sàng, vì vậy JVM có thể chạy `<clinit>` **trước** khi vào `<init>`. Static initialization failure thường hiện ra qua `ExceptionInInitializerError` lần đầu và `NoClassDefFoundError` sau đó; throwable từ code của constructor path thường được Reflection bọc thành `InvocationTargetException`. Luôn đọc **cause chain** để sửa đúng nơi xảy ra lỗi.


</details>

- [Quay lại đầu trang](#back-to-top)
