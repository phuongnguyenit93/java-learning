<a id="back-to-top"></a>

# Quan hệ kiểu

## Menu
- [getSuperclass và các trường hợp trả về null](#type-relationships-001)
- [getInterfaces trả về các interface trực tiếp](#type-relationships-002)
- [Duyệt toàn bộ chuỗi lớp cha](#type-relationships-003)
- [Duyệt đồ thị interface và tránh lặp](#type-relationships-004)
- [Phân biệt quan hệ khai báo với kiểu thực tế của đối tượng](#type-relationships-005)
- [Kiểu mảng với Object, Cloneable và Serializable](#type-relationships-006)
- [Primitive và wrapper không có quan hệ kế thừa thay cho boxing](#type-relationships-007)
- [Chọn kiểu khai báo hoặc kiểu thực tế khi khám phá thành viên](#type-relationships-008)

## <a id="type-relationships-001">getSuperclass và các trường hợp trả về null</a>

<details>
<summary>Xem chi tiết</summary>

# `Class.getSuperclass()`: lớp cha trực tiếp và các trường hợp trả về `null`

## 1. Ý nghĩa của `getSuperclass()`

Trong Java Reflection, đối tượng `Class<?>` mô tả một kiểu tại runtime. Gọi `type.getSuperclass()` để lấy đối tượng `Class` đại diện cho **lớp cha trực tiếp** được xác định qua quan hệ `extends`. Ví dụ, nếu `class Dog extends Animal` thì `Dog.class.getSuperclass() == Animal.class`. Phương thức không tự duyệt toàn bộ chuỗi tổ tiên và không liệt kê các interface.

Khai báo phương thức trong `java.lang.Class<T>`:

```java
public Class<? super T> getSuperclass()
```

Khi làm việc với `Class<?>`, có thể gán kết quả cho `Class<?> parent`. Giá trị `null` là **kết quả hợp lệ theo hợp đồng API**, không phải bằng chứng Reflection gặp lỗi hoặc ném ngoại lệ.

Java phân biệt superclass với superinterface: class chỉ có tối đa một superclass trực tiếp, có thể triển khai nhiều interface; interface có thể mở rộng nhiều interface nhưng không có superclass theo `getSuperclass()`.

## 2. Chính xác khi nào trả `null`?

| Kiểu được hỏi | Kết quả | Giải thích |
| --- | --- | --- |
| `Object.class` | `null` | `java.lang.Object` là gốc chuỗi kế thừa của các class thông thường, không có lớp cha. |
| Mọi interface, chẳng hạn `Runnable.class` | `null` | Ngay cả khi interface có `extends` interface khác, quan hệ đó được tra bằng `getInterfaces()`. |
| Mọi primitive: `boolean`, `byte`, `short`, `char`, `int`, `long`, `float`, `double` (ví dụ `int.class`) | `null` | Primitive không phải class kế thừa từ `Object`. |
| `void.class` hoặc `Void.TYPE` | `null` | `void` biểu thị không có giá trị trả về; không có superclass. |

Một annotation type khai báo bằng `@interface` cũng là interface, nên `MyAnnotation.class.getSuperclass()` bằng `null`. Kể cả khi interface kế thừa nhiều interface, kết quả vẫn `null`.

**Các trường hợp dễ nhầm:**

- `int.class.getSuperclass() == null`, trong khi `Integer.class.getSuperclass() == Number.class`. Primitive và wrapper là các kiểu khác nhau.
- `void.class.getSuperclass() == null`, nhưng `Void.class.getSuperclass() == Object.class`. `java.lang.Void` là một class tham chiếu khác với `void`.
- `String[].class.getSuperclass() == Object.class`, `int[].class.getSuperclass() == Object.class`. Mảng là kiểu tham chiếu; **mọi array class đều có superclass phản chiếu là `Object`**, bất kể kiểu phần tử hay số chiều.
- Class bình thường không khai báo `extends` có superclass là `Object` (ngoại trừ chính `Object`); enum có superclass `Enum`, còn record có superclass `Record`.

## 3. Ví dụ Java 17 hoàn chỉnh

Tạo tệp `SuperclassDemo.java` với nội dung:

```java
public class SuperclassDemo {
    interface Named extends Runnable {}

    static class Parent {}

    static class Child extends Parent implements Named {
        @Override
        public void run() {}
    }

    enum Status { READY }

    record Point(int x, int y) {}

    private static void show(Class<?> type) {
        Class<?> parent = type.getSuperclass();
        System.out.println(type.getSimpleName() + " -> "
                + (parent == null ? "null" : parent.getSimpleName()));
    }

    public static void main(String[] args) {
        show(Object.class);
        show(Named.class);
        show(int.class);
        show(void.class);
        show(String[].class);
        show(String[][].class);
        show(Parent.class);
        show(Child.class);
        show(Status.class);
        show(Point.class);

        System.out.println("Named extends -> "
                + Named.class.getInterfaces()[0].getSimpleName());
        System.out.println("Child implements -> "
                + Child.class.getInterfaces()[0].getSimpleName());
        System.out.println("Void.class -> "
                + Void.class.getSuperclass().getSimpleName());
    }
}
```

Biên dịch/chạy với JDK 17 trở lên:

```bash
javac SuperclassDemo.java
java SuperclassDemo
```

Kết quả:

```text
Object -> null
Named -> null
int -> null
void -> null
String[] -> Object
String[][] -> Object
Parent -> Object
Child -> Parent
Status -> Enum
Point -> Record
Named extends -> Runnable
Child implements -> Named
Void.class -> Object
```

**Đọc kết quả:**

- `Named` vẫn có superinterface `Runnable`, nhưng `getSuperclass()` bằng `null`; `getInterfaces()` cho biết interface mà `Named` mở rộng trực tiếp.
- `Child` vừa `extends Parent` vừa `implements Named`. `getSuperclass()` trả `Parent`, trong khi `getInterfaces()` trả interface khai báo trực tiếp là `Named`.
- `Parent` mặc định kế thừa `Object`. `Status` kế thừa `Enum`, `Point` kế thừa `Record`; cả hai vẫn có superclass.
- `String[]` và `String[][]` đều trả `Object`, không trả class biểu diễn kiểu phần tử. `Void.class` là class tham chiếu nên trả `Object`.

## 4. Đi qua nhiều cấp superclass

Vì mỗi lần gọi chỉ trả **lớp cha gần nhất**, cần lặp đến `null` để đi hết chuỗi. Đặt đoạn sau trong `main()` của chương trình phía trên:

```java
Class<?> current = Child.class;
while (current != null) {
    System.out.println(current.getSimpleName());
    current = current.getSuperclass();
}
```

Kết quả:

```text
Child
Parent
Object
```

Vòng lặp in kiểu đầu vào, rồi từng lớp cha; sau `Object`, `getSuperclass()` là `null` và vòng lặp dừng. Nếu bắt đầu bằng interface hoặc primitive, vòng lặp chỉ in chính kiểu đó. Cần nhớ vòng lặp này **không đi theo các nhánh interface**.

Khi bài toán là kiểm tra quan hệ **có thể gán kiểu**, `Base.class.isAssignableFrom(Candidate.class)` thường phù hợp hơn tự duyệt superclass. Ví dụ, `Named.class.isAssignableFrom(Child.class)` là `true` dù `Named` không nằm trong chuỗi superclass của `Child`. Chiều hỏi ở đây là: một giá trị kiểu `Candidate` có thể gán cho biến kiểu `Base` không?

## 5. Phân biệt các API liên quan

| Câu hỏi | API | Ý nghĩa |
| --- | --- | --- |
| Lớp cha trực tiếp? | `getSuperclass()` | Trả `Class` hoặc `null` theo hợp đồng ở mục 2. |
| Lớp cha và thông tin generic trong khai báo? | `getGenericSuperclass()` | Trả `Type`, có thể là `ParameterizedType`. |
| Interface được triển khai/mở rộng trực tiếp? | `getInterfaces()` | Trả mảng `Class<?>[]`; có thể là mảng rỗng. |
| Interface trực tiếp cùng đối số generic? | `getGenericInterfaces()` | Trả `Type[]`, không tự duyệt tổ tiên. |
| Kiểm tra nhóm kiểu? | `isInterface()`, `isPrimitive()`, `isArray()` | Phân loại kiểu trước khi xử lý kết quả `null`. |
| Kiểu thành phần của mảng? | `getComponentType()` | Ví dụ: `String[][].class.getComponentType() == String[].class`. |
| Có thể gán kiểu, bao gồm interface? | `isAssignableFrom(...)` | Kiểm tra quan hệ thay vì chỉ lấy superclass gần nhất. |

Do cơ chế xóa kiểu generic (*type erasure*), `getSuperclass()` trả `Class` cho lớp cha thô. Nếu khai báo `class TextBox extends Box<String>`, thì `TextBox.class.getSuperclass() == Box.class`; muốn đọc phần `<String>` trong khai báo phải dùng `getGenericSuperclass()`. Thông tin đó cũng không có nghĩa có thể khôi phục mọi đối số generic của đối tượng bất kỳ tại runtime.

Một class có thể kế thừa interface thông qua class cha; `getInterfaces()` trên chính class đó chỉ đưa ra interface trực tiếp mà class khai báo. Nếu cần lập danh sách mọi interface tổ tiên, phải tiếp tục đọc `getInterfaces()` ở các interface, đồng thời đi qua các superclass liên quan.

## 6. Lỗi thường gặp

**Không kiểm tra `null` trước khi gọi tiếp.** Dòng `type.getSuperclass().getName()` có thể ném `NullPointerException` với `Object.class`, interface, primitive hoặc `void.class`. Cách an toàn:

```java
Class<?> parent = type.getSuperclass();
String name = parent == null ? "(không có superclass)" : parent.getName();
```

**Hiểu `null` là “kiểu không có quan hệ kế thừa nào”.** Interface vẫn có thể `extends` interface khác; `getSuperclass()` không phản ánh nhánh đó. Dùng `getInterfaces()` để đọc trực tiếp, hoặc duyệt đệ quy khi muốn đủ tất cả superinterface.

**Cho rằng array hoặc wrapper cũng phải trả `null`.** Array có superclass là `Object`. `Integer` là class nên có superclass `Number`. Kết quả của primitive khác kết quả của lớp bao tương ứng.

**Nhầm `void.class` với `Void.class`.** `void.class` trả `null`; `Void.class` trả `Object.class`. Tên gần giống không đồng nghĩa hai `Class` giống nhau.

**Dùng `getSuperclass()` để hỏi class có triển khai interface hay không.** API này không đi qua `implements`; hãy dùng `isAssignableFrom` theo đúng chiều hoặc duyệt các interface nếu cần giải thích đường quan hệ.

Quy tắc cần nhớ: **`Object`, interface, primitive và `void` cho `null`; array cho `Object.class`; class, enum và record trả superclass trực tiếp tương ứng.**

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-relationships-002">getInterfaces trả về các interface trực tiếp</a>

<details>
<summary>Xem chi tiết</summary>

# Class.getInterfaces() — chỉ trả về các interface trực tiếp

## 1. Ý nghĩa và chữ “trực tiếp”

Trong Java Reflection, `Class<?>[] getInterfaces()` là phương thức của `java.lang.Class`, cho biết các **interface được khai báo trực tiếp** bởi kiểu đang xét. Phương thức trả về **mảng các đối tượng `Class<?>`** đại diện cho interface; nó không trả về đối tượng triển khai interface, không trả về tên dưới dạng `String`, và không tự duyệt toàn bộ cây kế thừa.

- Với **class**, đó là các interface xuất hiện trực tiếp trong mệnh đề `implements` của chính class.
- Với **interface**, đó là các superinterface xuất hiện trực tiếp trong mệnh đề `extends` của chính interface.
- Interface có được **gián tiếp** qua superclass hoặc qua một interface cha khác không tự động xuất hiện trong kết quả của kiểu đang xét.
- Kết quả là mảng rỗng `Class<?>[0]` nếu không có interface trực tiếp, chứ không phải `null`. Phần tử trong mảng đi theo thứ tự khai báo trong `implements` hoặc `extends`.

Ví dụ nhanh:

~~~java
interface A {}
interface B extends A {}
class Parent implements A {}
class Child extends Parent implements B {}
~~~

Ở đây:

| Biểu thức | Interface trả về trực tiếp | Điều quan trọng |
| --- | --- | --- |
| `A.class.getInterfaces()` | `[]` | `A` không `extends` interface nào. |
| `B.class.getInterfaces()` | `[A]` | `B extends A` trực tiếp. |
| `Parent.class.getInterfaces()` | `[A]` | `Parent implements A` trực tiếp. |
| `Child.class.getInterfaces()` | `[B]` | `Child` chỉ khai báo `implements B`; `A` vẫn là một quan hệ kiểu hợp lệ nhưng không nằm trong mảng trực tiếp của `Child`. |

Hãy tách hai câu hỏi: **“Kiểu này trực tiếp khai báo interface nào?”** dùng `getInterfaces()`; **“Kiểu này có thể được xem là một interface cụ thể hay không?”** dùng kiểm tra quan hệ kiểu như `SomeInterface.class.isAssignableFrom(Target.class)`.

## 2. Ví dụ chạy được với Java 17+

Lưu toàn bộ chương trình sau thành `DirectInterfacesDemo.java`:

~~~java
import java.io.Serializable;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Collectors;

interface Root {}
interface Middle extends Root {}
interface Marker {}

class Parent implements Serializable {}
class Child extends Parent implements Middle, Marker {}

class GenericSample implements Supplier<String> {
    @Override
    public String get() {
        return "ok";
    }
}

public class DirectInterfacesDemo {
    private static String directNames(Class<?> type) {
        return Arrays.stream(type.getInterfaces())
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    public static void main(String[] args) {
        System.out.println("Child: " + directNames(Child.class));
        System.out.println("Parent: " + directNames(Parent.class));
        System.out.println("Middle: " + directNames(Middle.class));
        System.out.println("Root: " + directNames(Root.class));
        System.out.println("String[]: " + directNames(String[].class));
        System.out.println("int: " + directNames(int.class));

        System.out.println("Root assignable from Child: "
                + Root.class.isAssignableFrom(Child.class));
        System.out.println("Serializable assignable from Child: "
                + Serializable.class.isAssignableFrom(Child.class));

        System.out.println("Generic direct: "
                + GenericSample.class.getInterfaces()[0].getTypeName());
        System.out.println("Generic signature: "
                + GenericSample.class.getGenericInterfaces()[0].getTypeName());
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac DirectInterfacesDemo.java
java DirectInterfacesDemo
~~~

Kết quả chuẩn:

~~~text
Child: [Middle, Marker]
Parent: [Serializable]
Middle: [Root]
Root: []
String[]: [Cloneable, Serializable]
int: []
Root assignable from Child: true
Serializable assignable from Child: true
Generic direct: java.util.function.Supplier
Generic signature: java.util.function.Supplier<java.lang.String>
~~~

Phân tích từng điểm:

1. `Child` **trực tiếp** `implements Middle, Marker`, nên `getInterfaces()` trả `Middle` trước `Marker`, đúng thứ tự khai báo. `Root` không nằm trong mảng này dù `Middle extends Root`.
2. `Serializable` thuộc mệnh đề `implements` của `Parent`, nên `Parent.class.getInterfaces()` có `Serializable` còn `Child.class.getInterfaces()` không có. Tuy vậy, một `Child` cũng là `Serializable` nhờ kế thừa `Parent`.
3. `Middle` là một **interface**, nên kết quả `[Root]` đến từ `extends Root`. `Root` không extends interface nào nên kết quả rỗng.
4. Các kiểu mảng và kiểu nguyên thủy có quy tắc riêng, xem phần tiếp theo.
5. `isAssignableFrom` trả `true` cho cả quan hệ gián tiếp qua superinterface hoặc superclass; do đó khác mục đích `getInterfaces()`.
6. `Supplier<String>` được trả về dưới dạng `Class` thô `Supplier` khi gọi `getInterfaces()`; thông tin `<String>` chỉ thấy ở `getGenericInterfaces()`.

## 3. Các trường hợp đặc biệt

| Kiểu của `Class` | Kết quả `getInterfaces()` |
| --- | --- |
| Class khai báo `implements I, J` | `[I, J]` theo thứ tự khai báo. |
| Class không khai báo `implements` | Mảng rỗng, kể cả khi superclass triển khai interface. |
| Interface khai báo `extends I, J` | `[I, J]` theo thứ tự khai báo. |
| Interface không khai báo `extends` | Mảng rỗng. |
| Kiểu mảng, chẳng hạn `String[]` hoặc `int[]` | `[Cloneable, java.io.Serializable]`, đúng thứ tự này, dù không có mã nguồn `implements`. |
| Primitive như `int`, `boolean` hoặc `void.class` | Mảng rỗng. |

Đặc biệt, `String[].class.getInterfaces()` không tìm các interface do `String` triển khai; nó phản ánh các interface của **chính kiểu mảng**. Tương tự, `int.class.getInterfaces()` rỗng nhưng `int[].class.getInterfaces()` có hai phần tử.

## 4. Phân biệt với các API liên quan

| API | Câu hỏi mà API trả lời |
| --- | --- |
| `type.getInterfaces()` | Interface nào được class trực tiếp triển khai hoặc được interface trực tiếp mở rộng? |
| `type.getGenericInterfaces()` | Các interface trực tiếp ấy được khai báo với tham số kiểu generic nào, nếu có? Trả về `Type[]`, không phải `Class<?>[]`. |
| `type.getSuperclass()` | Superclass trực tiếp của class là gì? Không dùng để liệt kê interface. |
| `I.class.isAssignableFrom(type)` | Biến kiểu `I` có thể nhận đối tượng thuộc `type` hay không, xét cả quan hệ kế thừa và triển khai gián tiếp? |
| `I.class.isInstance(object)` | Đối tượng thực tế có tương thích với interface `I` không? |

Hai phương thức `isAssignableFrom` và `isInstance` phù hợp hơn khi muốn **kiểm tra khả năng gán kiểu**. Ví dụ `Root.class.isAssignableFrom(Child.class)` là `true` dù `Root` không nằm trong `Child.class.getInterfaces()`. Với `isInstance`, truyền một object; với `isAssignableFrom`, truyền một đối tượng `Class<?>`.

`getGenericInterfaces()` vẫn chỉ liệt kê interface **trực tiếp**, nhưng giữ mô tả generic: trong ví dụ, kết quả chứa `java.util.function.Supplier<java.lang.String>`. Không ép mọi phần tử của `Type[]` sang `ParameterizedType` vì một interface có thể xuất hiện dưới dạng `Class` thô; trước khi ép kiểu phải kiểm tra `instanceof`.

## 5. Khi cần biết toàn bộ interface tổ tiên

Nếu cần liệt kê các interface có được qua nhiều cấp, `getInterfaces()` chỉ là bước khởi đầu. Với `Child` trong ví dụ, thuật toán phải đọc các interface trực tiếp của `Child`, đi tiếp qua `getInterfaces()` của từng interface đã tìm thấy, xét cả `Parent` qua `getSuperclass()`, và ghi nhận mỗi `Class<?>` đúng một lần trong `Set<Class<?>>` để tránh trùng khi nhiều nhánh hội tụ.

Ví dụ, `Child` có hai interface trực tiếp `[Middle, Marker]`, `Middle extends Root`, còn `Parent implements Serializable`. Nếu duyệt sâu từng nhánh interface trước khi chuyển sang nhánh kế tiếp và cuối cùng mới đi lên superclass, thứ tự kết quả là `[Middle, Root, Marker, Serializable]`. Đây là thứ tự do **thuật toán tự chọn**, không phải danh sách mà một lần gọi `Child.class.getInterfaces()` trả về.

Khi chỉ cần trả lời đúng/sai cho một interface, `Root.class.isAssignableFrom(Child.class)` rõ ràng và đơn giản hơn. Chỉ cần tự duyệt đồ thị khi phải thu thập toàn bộ interface; lúc đó nên dùng danh tính `Class<?>` làm khóa khử trùng lặp, xác định rõ thứ tự duyệt và quyết định có tính chính interface đầu vào hay chỉ tổ tiên. Những lựa chọn thuật toán và tình huống đồ thị hình thoi được triển khai chi tiết trong bài “Duyệt đồ thị interface và tránh lặp”.

## 6. Lỗi phổ biến và cách tự kiểm tra

- **Nhầm trực tiếp với toàn bộ cây kế thừa:** `Child.class.getInterfaces().length == 0` không chứng minh `Child` không triển khai interface nào; class cha có thể đã triển khai chúng.
- **Bỏ sót superinterface:** class `implements Middle` không khiến `Root` xuất hiện trực tiếp trong mảng của class, dù `Middle extends Root`.
- **Áp dụng máy móc cho array:** mọi kiểu mảng đều trả về `Cloneable` và `Serializable` trong thứ tự quy định.
- **Đòi thông tin generic từ `Class<?>[]`:** dùng `getGenericInterfaces()` khi phải đọc `Supplier<String>` hay `Comparable<T>` từ khai báo trực tiếp.
- **Truy cập `getInterfaces()[0]` khi chưa kiểm tra:** mảng có thể rỗng; kiểm tra `length` trước để tránh `ArrayIndexOutOfBoundsException`.
- **Nhầm kết quả với instance:** `getInterfaces()` trả metadata về *kiểu*, không tạo instance, không gọi phương thức interface.

Tự kiểm tra với `class D extends Parent {}` trong ví dụ: `D.class.getInterfaces()` phải là `[]`, nhưng `Serializable.class.isAssignableFrom(D.class)` phải là `true`. Giải thích được cả hai kết quả nghĩa là đã nắm đúng ý nghĩa của **“trực tiếp”**.

**Tài liệu tham khảo:** [Java SE 17 API — java.lang.Class](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html), phần `getInterfaces()`, `getGenericInterfaces()` và `isAssignableFrom()`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-relationships-003">Duyệt toàn bộ chuỗi lớp cha</a>

<details>
<summary>Xem chi tiết</summary>

# Duyệt toàn bộ chuỗi lớp cha trong Java Reflection

## 1. Mục tiêu và mô hình kế thừa

Khi biết một lớp, ta có thể dùng Reflection để lần lượt tìm **lớp cha trực tiếp**, **lớp ông**, rồi tiếp tục đến `java.lang.Object`. Với khai báo `Leaf extends Middle` và `Middle extends Base`, còn `Base` không viết `extends`, chuỗi đầy đủ từ lớp con là:

```text
Leaf -> Middle -> Base -> Object -> null
```

`null` chỉ là dấu hiệu đã hết chuỗi, **không phải một lớp**. Java cho một class kế thừa trực tiếp tối đa một class, vì vậy đường đi qua các **superclass** là tuyến tính. Đây là cơ sở để kiểm tra metadata kế thừa, tìm field hoặc method do lớp cha khai báo, xây dựng serializer/mapper và chẩn đoán cấu trúc runtime.

Phân biệt ngay từ đầu: quan hệ class `extends` khác với quan hệ interface `implements`/`extends`. Một class có thể triển khai nhiều interface. Các interface **không xuất hiện** trong chuỗi lấy bằng `getSuperclass()`.

## 2. API `Class.getSuperclass()` hoạt động thế nào?

Một lớp hoặc kiểu Java có đối tượng metadata `Class<?>` ở runtime. Gọi:

```java
Class<?> parent = current.getSuperclass();
```

sẽ nhận đối tượng `Class` của **superclass trực tiếp** của `current`. Khi không có superclass theo hợp đồng Reflection, phương thức trả `null`.

| Biểu thức | Kết quả |
| --- | --- |
| `Leaf.class.getSuperclass()` | `Middle.class` |
| `Middle.class.getSuperclass()` | `Base.class` |
| `Base.class.getSuperclass()` | `Object.class`, nếu `Base` không khai báo `extends` |
| `Object.class.getSuperclass()` | `null` |
| `Runnable.class.getSuperclass()` | `null`, do đây là interface |
| `int.class.getSuperclass()` và `void.class.getSuperclass()` | `null` |
| `String[].class.getSuperclass()` | `Object.class` |

`Class<?>` được dùng khi ta cần duyệt metadata mà không biết tham số kiểu cụ thể. Không cần khởi tạo object của từng lớp, và việc gọi `getSuperclass()` không chạy constructor của chúng.

Đừng viết liên tiếp `type.getSuperclass().getSuperclass()` mà thiếu kiểm tra, bởi khi tới `Object`, kết quả kế tiếp là `null`; gọi tiếp trên `null` gây `NullPointerException`. Vòng lặp phải luôn kiểm tra điều kiện dừng.

## 3. Thuật toán: đi từ lớp hiện tại đến `Object`

Các bước:

1. Chọn `Class<?>` bắt đầu, chẳng hạn `Leaf.class` hoặc `someObject.getClass()`.
2. Nếu muốn kể cả lớp đó, xử lý/lưu nó ngay; nếu chỉ muốn các lớp cha, bắt đầu ở `start.getSuperclass()`.
3. Lấy superclass của lớp hiện tại bằng `getSuperclass()`, chuyển sang lớp đó.
4. Lặp cho đến khi `current == null`.

Thời gian O(h), trong đó h là độ dài chuỗi lớp. Bộ nhớ phụ O(1) khi chỉ in/xử lý từng lớp; nếu trả về một danh sách h lớp thì dùng O(h) bộ nhớ.

### Chương trình Java 17+ hoàn chỉnh

Lưu chương trình dưới tên **`SuperclassChainDemo.java`**:

```java
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SuperclassChainDemo {
    static class Base {}
    static class Middle extends Base {}
    static class Leaf extends Middle {}

    // includeStart = true: có lớp đầu vào; false: chỉ các lớp cha.
    public static List<Class<?>> classChain(Class<?> start, boolean includeStart) {
        if (start == null) {
            throw new IllegalArgumentException("start không được null");
        }

        List<Class<?>> result = new ArrayList<>();
        for (Class<?> current = includeStart ? start : start.getSuperclass();
             current != null;
             current = current.getSuperclass()) {
            result.add(current);
        }
        return List.copyOf(result);
    }

    private static String names(List<Class<?>> classes) {
        return classes.stream()
                .map(Class::getSimpleName)
                .collect(Collectors.joining(" -> "));
    }

    public static void main(String[] args) {
        System.out.println("Gồm lớp bắt đầu: " + names(classChain(Leaf.class, true)));
        System.out.println("Chỉ các lớp cha: " + names(classChain(Leaf.class, false)));
        System.out.println("Từ Object: " + names(classChain(Object.class, true)));
    }
}
```

Chạy tại thư mục chứa file:

```bash
javac SuperclassChainDemo.java
java SuperclassChainDemo
```

Kết quả stdout:

```text
Gồm lớp bắt đầu: Leaf -> Middle -> Base -> Object
Chỉ các lớp cha: Middle -> Base -> Object
Từ Object: Object
```

Giải thích: vòng lặp bắt đầu tại `Leaf.class`, sau đó chuyển qua `Middle.class`, `Base.class`, `Object.class`; lần gọi `Object.class.getSuperclass()` nhận `null` nên dừng. Khi `includeStart=false`, lớp đầu tiên đưa vào danh sách là `Middle`. `List.copyOf` làm cho danh sách kết quả không thể bị thay đổi qua các thao tác thêm/xóa phần tử; đây là lựa chọn thiết kế, không phải điều kiện bắt buộc để duyệt.

Nếu chỉ cần thực thi một thao tác trên mỗi lớp, không phải tích lũy `List`, vòng `while` sau là đủ. Đây là **đoạn code minh họa** sử dụng `Leaf` trong ví dụ trên, không phải chương trình độc lập:

```java
Class<?> current = Leaf.class;
while (current != null) {
    System.out.println(current.getSimpleName());
    current = current.getSuperclass();
}
```

Chọn `getSimpleName()` để hiển thị tên ngắn; `getName()` trả tên nhị phân của lớp, thích hợp hơn khi cần biết package hoặc phân biệt nested class (nested class có thể chứa dấu `$` trong tên nhị phân). Tên ngắn có thể trùng nhau, vì vậy đừng dùng riêng `getSimpleName()` làm khóa định danh duy nhất.

## 4. Chọn điểm bắt đầu và điểm dừng

| Nhu cầu | Cách thực hiện |
| --- | --- |
| Gồm chính lớp được truyền vào | `current = start` |
| Chỉ duyệt các lớp cha | `current = start.getSuperclass()` |
| Gồm cả `Object` | Lặp khi `current != null` |
| Bỏ `Object` khỏi kết quả | Lặp khi `current != null && current != Object.class` |
| Theo lớp khai báo rõ trong code | Dùng `MyType.class` |
| Theo kiểu thực tế của một object | Dùng `instance.getClass()` sau khi chắc chắn `instance != null` |

Ví dụ một biến có kiểu khai báo `Base` nhưng thực tế chứa `new Leaf()`: `instance.getClass()` trả `Leaf.class`, nên thuật toán đi từ `Leaf`. Đây là điều quan trọng khi làm việc với subclass do framework hoặc thư viện sinh ở runtime. Với `Object.class` và lựa chọn **chỉ lớp cha**, kết quả hợp lệ là danh sách rỗng.

## 5. Ứng dụng thực tế: quét metadata qua từng superclass

Các API Reflection có phạm vi rất khác nhau. `getDeclaredFields()` chỉ trả những field **khai báo trực tiếp trong lớp đang xét**, bất kể phạm vi truy cập; nó không tự gom field được khai báo ở tổ tiên. Vì vậy, nếu công cụ cần tìm field trên toàn chuỗi lớp, hãy duyệt từ lớp con lên từng lớp cha rồi gọi `getDeclaredFields()` ở **mỗi** cấp.

Ví dụ minh họa đặt trong ngữ cảnh có biến `Class<?> type`:

```java
for (Class<?> current = type;
     current != null;
     current = current.getSuperclass()) {
    System.out.println("Khai báo tại " + current.getName());

    for (java.lang.reflect.Field field : current.getDeclaredFields()) {
        System.out.println("  field: " + field.getName());
    }
}
```

Đoạn này liệt kê metadata kể cả field `private` trong superclass. **Đọc được metadata không có nghĩa có quyền truy cập giá trị** của field. Việc gọi `Field.get(...)` hoặc yêu cầu bỏ qua kiểm tra truy cập còn phụ thuộc quy tắc Java và hệ thống module JPMS. Không mặc định mọi field đều được `setAccessible(true)` thành công.

Bảng so sánh quan trọng:

| API | Ý nghĩa phạm vi |
| --- | --- |
| `getDeclaredFields()` | Field khai báo tại **chính lớp đó**, không tự gom superclass |
| `getFields()` | Field `public` theo hợp đồng API, bao gồm field public kế thừa |
| `getDeclaredMethods()` | Method khai báo tại **chính lớp đó**, không tự gom superclass |
| `getMethods()` | Method `public` theo hợp đồng API, gồm các method công khai được kế thừa |

Cần quyết định rõ chính sách đối với metadata trùng tên. Một subclass có thể khai báo field cùng tên với field của superclass (**field hiding**). Khi quét từng cấp, cả hai field có thể được thấy; nếu xây mapper, cần chọn ưu tiên khai báo gần lớp con nhất hay giữ tất cả. Với method, còn phải xét override, overload, method synthetic/bridge, quyền truy cập và interface. Vòng lặp superclass chỉ cung cấp đường duyệt đúng; cách hợp nhất thành viên là một bài toán riêng.

## 6. Những trường hợp đặc biệt

### 6.1. Interface là một nhánh quan hệ khác

Với `class Worker extends Base implements Runnable`, duyệt `Worker.class.getSuperclass()` sẽ đi qua `Base` và `Object`, không qua `Runnable`. Gọi `getInterfaces()` để lấy interface trực tiếp của class; với một interface, API này trả những superinterface trực tiếp của nó.

Nếu nhiệm vụ là tìm **tất cả** interface trên các cấp lớp, cần duyệt interface ở từng superclass, đi tiếp qua những superinterface, đồng thời đánh dấu interface đã thăm để không lặp kết quả khi nhiều nhánh hội tụ. Đó là duyệt một đồ thị quan hệ kiểu, khác với chuỗi superclass của bài này.

Khi chỉ cần hỏi một kiểu có gán được sang kiểu khác hay không, hãy cân nhắc `Target.class.isAssignableFrom(Source.class)`; đây là phép kiểm tra quan hệ kiểu do Java cung cấp, thường không cần viết thuật toán duyệt thủ công.

### 6.2. `Object`, primitive, `void` và array

- `Object.class.getSuperclass()` là `null` và là điểm dừng tự nhiên của các chuỗi class thông thường.
- Kiểu nguyên thủy như `int.class`, kiểu `void.class` và kiểu interface đều có `getSuperclass() == null`. Đừng tự thêm `Object` vào kết quả của chúng.
- Với class của mảng như `String[].class` hoặc `int[].class`, `getSuperclass()` trả `Object.class`. Mảng cũng triển khai `Cloneable` và `java.io.Serializable`, nhưng các interface này không xuất hiện trong chuỗi superclass.
- Các kiểu enum kế thừa lớp `java.lang.Enum`; record kế thừa `java.lang.Record`. Vòng lặp chung vẫn đi đúng lên `Object`, không cần mã riêng.

### 6.3. Generic superclass và type erasure

Với khai báo `class StringBox extends Box<String>`, `getSuperclass()` trả metadata lớp cha thô tương ứng `Box.class`. Nó không cung cấp trực tiếp toàn bộ biểu thức kiểu `Box<String>`. Khi cần phân tích thông tin generic trong khai báo `extends`, dùng `getGenericSuperclass()` (kiểu trả về là `java.lang.reflect.Type`, có thể là `ParameterizedType`) và kiểm tra loại `Type` trước khi ép kiểu. Đừng nhầm metadata generic được lưu trong chữ ký khai báo với khả năng phục hồi tùy ý tham số generic của mọi object ở runtime.

### 6.4. Danh tính lớp và class loader

`Class<?>` biểu diễn đúng kiểu đã được nạp. Hai class cùng tên nhưng được nạp bởi hai class loader khác nhau có thể có danh tính kiểu khác nhau. Khi xử lý kết quả Reflection cho plugin/proxy/framework, nên giữ đối tượng `Class<?>` (hoặc bổ sung thông tin loader khi cần), thay vì dựa riêng vào chuỗi tên ngắn. Việc duyệt chỉ đọc cấu trúc lớp ở runtime; nó không khẳng định đối tượng có thể được khởi tạo hay một thành viên private có thể truy cập.

## 7. Lỗi thường gặp

| Lỗi | Vì sao sai và cách sửa |
| --- | --- |
| Gọi tiếp `getSuperclass()` trên `null` | Gây `NullPointerException`; kiểm tra `current != null` trước mỗi lượt |
| Dừng ngay trước `Object` khi yêu cầu toàn bộ chuỗi | Mất lớp gốc; chỉ loại `Object` khi bài toán yêu cầu |
| Mong `getSuperclass()` trả cả interface | API này chỉ xét superclass; dùng `getInterfaces()` cho interface |
| Chỉ đọc `Leaf.class.getDeclaredFields()` | Không thấy field khai báo ở superclass; quét mỗi cấp riêng |
| Nghĩ `getSuperclass()` cho biết `Box<String>` | API trả `Class`; xét `getGenericSuperclass()` nếu cần chữ ký generic |
| Dùng `getSimpleName()` làm khóa duy nhất | Có thể trùng giữa package hoặc nested class; giữ `Class<?>` hoặc định danh đầy đủ thích hợp |
| Coi `Class` được tìm ra là có thể truy cập mọi thành viên | Quy tắc Java/JPMS vẫn được áp dụng khi truy cập thành viên |

## 8. Câu hỏi tự kiểm tra

1. Cho `C extends B`, `B extends A`, `A` không ghi `extends`. `classChain(C.class, true)` có những phần tử nào và thứ tự ra sao?
2. Vì sao `classChain(C.class, false)` không có `C` nhưng vẫn có `Object`?
3. Với `class C implements Runnable`, làm thế nào để lấy `Runnable`? Có thể dùng `getSuperclass()` không?
4. Cần tìm các field `private` được **khai báo** trong cả các superclass: nên dùng `getFields()` trên lớp con hay `getDeclaredFields()` cho từng cấp? Vì sao?
5. `Object.class.getSuperclass()` và `String[].class.getSuperclass()` trả kết quả giống nhau hay khác nhau?

**Đáp án:** (1) `C, B, A, Object`; (2) khởi tạo từ superclass của `C` nhưng vẫn duyệt đến trước `null`; (3) dùng `getInterfaces()` cho interface trực tiếp, không dùng `getSuperclass()`; (4) dùng `getDeclaredFields()` tại từng lớp, vì API đó nhìn thấy khai báo của lớp tương ứng; (5) `Object` trả `null`, mảng trả `Object.class`.

Cốt lõi của bài là giữ biến `Class<?> current`, liên tục cập nhật bằng `current.getSuperclass()` cho tới `null`. Cách làm này thu được đầy đủ và đúng thứ tự **chuỗi lớp cha**; muốn bao gồm interface, generic hoặc kết hợp metadata thành viên phải áp dụng thêm API và quy tắc riêng cho từng mục đích.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-relationships-004">Duyệt đồ thị interface và tránh lặp</a>

<details>
<summary>Xem chi tiết</summary>

# Duyệt đồ thị interface và tránh lặp bằng Java Reflection

## 1. Mục tiêu và bài toán

Khi dùng Reflection, một lớp Java có thể triển khai nhiều interface; mỗi interface lại có thể mở rộng nhiều interface khác. Ngoài ra, lớp còn kế thừa interface gián tiếp qua các lớp cha. Vì thế, gọi `getInterfaces()` một lần thường **chưa đủ** nếu muốn tìm **tất cả interface mà một kiểu có quan hệ kế thừa/triển khai**.

Sau bài này, bạn có thể:

- Phân biệt interface trực tiếp với interface kế thừa gián tiếp qua `extends` và `implements`.
- Xây thuật toán duyệt đồ thị interface bằng `Class<?>`, `getInterfaces()` và `getSuperclass()`.
- Dùng tập `visited` để mỗi interface chỉ xuất hiện một lần, kể cả khi có cấu trúc hình thoi.
- Chọn thứ tự duyệt phù hợp và hiểu các ngoại lệ: đầu vào là interface, lớp, kiểu mảng hoặc kiểu nguyên thủy.
- Phân biệt **liệt kê** interface với **kiểm tra** quan hệ kiểu bằng `isAssignableFrom()`.

Ở đây, “tất cả interface” có nghĩa: **các interface tổ tiên** của kiểu đầu vào; nếu đầu vào chính là một interface, **không tính interface đầu vào vào danh sách kết quả**. Định nghĩa này quyết định cách khởi tạo thuật toán.

## 2. Mô hình đồ thị: vì sao cần duyệt?

Xem khai báo sau:

```java
interface Root {}
interface Left extends Root {}
interface Right extends Root {}
interface Diamond extends Left, Right {}

class Base implements Left {}
class Child extends Base implements Diamond, Right {}
```

Các quan hệ chính:

```text
                  Child
                 /     \
       implements       extends
        /        \          \
    Diamond     Right       Base
     /   \        |           |
   Left  Right   Root         Left
     \     /                    |
       Root                    Root
```

Sơ đồ biểu diễn nhiều đường có thể dẫn đến cùng một interface; đây là **đồ thị có hướng**, không phải cây. Chẳng hạn, `Root` có thể được tìm thấy qua `Diamond → Left → Root`, `Diamond → Right → Root` hoặc `Base → Left → Root`. Nếu chỉ ghép các kết quả đệ quy mà không ghi nhớ nút đã gặp, `Left`, `Right` và `Root` có thể bị liệt kê nhiều lần.

Trong mã Java hợp lệ, một interface không thể tạo chu trình kế thừa `A extends B` rồi `B extends A`. Dù vậy, `visited` vẫn cần thiết để xử lý **nhiều đường tới cùng một nút**; tính đúng đắn của thuật toán không dựa vào giả định “mọi nút chỉ xuất hiện trên một đường đi”.

## 3. Những API Reflection cần biết

| API | Trả về | Vai trò |
| --- | --- | --- |
| `type.getInterfaces()` | `Class<?>[]` | Các interface **trực tiếp** được khai báo trong `implements` của lớp hoặc `extends` của interface; với lớp con, không tự kèm mọi interface của lớp cha. |
| `type.getSuperclass()` | `Class<?>` hoặc `null` | Lớp cha trực tiếp của một lớp; trả `null` với interface, `Object`, primitive và `void`. |
| `type.isInterface()` | `boolean` | Phân biệt nút interface với nút lớp để chỉ đưa interface vào kết quả. |
| `iface.isAssignableFrom(type)` | `boolean` | Kiểm tra `type` có thể gán cho biến kiểu `iface` hay không, bao gồm chính cùng một kiểu. |
| `type.getGenericInterfaces()` | `Type[]` | Đọc dạng generic của interface trực tiếp, ví dụ `Comparable<String>`; đây là bài toán khác với khử trùng lặp theo `Class<?>`. |

**Phân biệt hai hướng truy vấn:**

- `Child.class.getInterfaces()` chỉ cho biết `Diamond` và `Right` khai báo trực tiếp trong `Child`.
- Muốn có `Left` và `Root`, phải đi tiếp qua các interface cha và qua `Base`.
- `Root.class.isAssignableFrom(Child.class)` trả `true` nếu chỉ muốn biết `Child` có thực thi hợp đồng của `Root`, không cần tự dựng danh sách interface.

`Class<?>` đại diện cho danh tính kiểu ở runtime. Hai lớp/interface cùng tên nhị phân nhưng được định nghĩa bởi hai class loader khác nhau có thể là hai đối tượng `Class<?>` khác nhau. Vì vậy hãy lưu **`Class<?>`**, không dùng riêng `getSimpleName()` hoặc `getName()` làm khóa loại trùng.

## 4. Thuật toán BFS tránh lặp

Ta dùng **Breadth-First Search (BFS)** với hàng đợi `ArrayDeque` và tập giữ thứ tự `LinkedHashSet`:

1. Nếu đầu vào là **lớp**, đưa lớp đó vào hàng đợi để khám phá interface trực tiếp và lớp cha. Nếu đầu vào là **interface**, chỉ đưa các *interface cha trực tiếp* vào hàng đợi để loại chính đầu vào khỏi kết quả.
2. Lấy phần tử đầu hàng đợi. Nếu là interface, thử thêm nó vào `visited`. Nếu đã tồn tại, bỏ qua việc mở rộng nút này; nếu mới, thêm các interface cha của nó vào hàng đợi.
3. Nếu là lớp, thêm các interface trực tiếp vào hàng đợi, sau đó thêm lớp cha khi khác `null`. Không thêm nút lớp vào kết quả.
4. Dừng khi hàng đợi rỗng. Chuyển `visited` thành danh sách kết quả.

**Bất biến quan trọng:** một interface chỉ được mở rộng khi lần đầu được đưa vào `visited`. Những đường gặp lại interface đó không tạo thêm phần tử kết quả và không duyệt lại toàn bộ tổ tiên của nó.

`LinkedHashSet` giữ thứ tự **lần đầu gặp** theo BFS. Thứ tự trong ví dụ là thứ tự duyệt có chủ đích; nó không phải một thứ tự phân cấp duy nhất mà mọi công cụ Reflection phải trả về. Nếu sản phẩm cần thứ tự hiển thị cố định theo tên, hãy sắp xếp riêng sau khi thu thập, bằng tên đầy đủ cùng thông tin loader khi cần.

## 5. Ví dụ Java 17+ chạy độc lập

Lưu toàn bộ mã sau vào file **`InterfaceGraphDemo.java`**:

```java
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class InterfaceGraphDemo {

    interface Root {}
    interface Left extends Root {}
    interface Right extends Root {}
    interface Diamond extends Left, Right {}

    static class Base implements Left {}
    static class Child extends Base implements Diamond, Right {}

    /**
     * Liệt kê mọi interface tổ tiên của type theo BFS,
     * không gồm chính type khi type là interface.
     */
    public static List<Class<?>> allInterfaces(Class<?> type) {
        Objects.requireNonNull(type, "type");

        Set<Class<?>> visited = new LinkedHashSet<>();
        Deque<Class<?>> pending = new ArrayDeque<>();

        if (type.isInterface()) {
            pending.addAll(Arrays.asList(type.getInterfaces()));
        } else {
            pending.addLast(type);
        }

        while (!pending.isEmpty()) {
            Class<?> current = pending.removeFirst();

            if (current.isInterface()) {
                // Đã gặp qua nhánh khác: không thêm và không duyệt lại.
                if (!visited.add(current)) {
                    continue;
                }
                pending.addAll(Arrays.asList(current.getInterfaces()));
            } else {
                // Interface trực tiếp của lớp hiện tại.
                pending.addAll(Arrays.asList(current.getInterfaces()));

                // Lớp con có thể kế thừa interface qua lớp cha.
                Class<?> parent = current.getSuperclass();
                if (parent != null) {
                    pending.addLast(parent);
                }
            }
        }

        return new ArrayList<>(visited);
    }

    private static String names(Collection<Class<?>> types) {
        return types.stream()
                .map(Class::getSimpleName)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    public static void main(String[] args) {
        System.out.println("Direct Child: "
                + names(Arrays.asList(Child.class.getInterfaces())));
        System.out.println("All Child: " + names(allInterfaces(Child.class)));
        System.out.println("All Base: " + names(allInterfaces(Base.class)));
        System.out.println("All Diamond: " + names(allInterfaces(Diamond.class)));
        System.out.println("All Root: " + names(allInterfaces(Root.class)));
        System.out.println("All int: " + names(allInterfaces(int.class)));
        System.out.println("Assignable: "
                + Root.class.isAssignableFrom(Child.class));
    }
}
```

Biên dịch và chạy với JDK 17 trở lên:

```bash
javac InterfaceGraphDemo.java
java InterfaceGraphDemo
```

Kết quả chuẩn của chương trình:

```text
Direct Child: [Diamond, Right]
All Child: [Diamond, Right, Left, Root]
All Base: [Left, Root]
All Diamond: [Left, Right, Root]
All Root: []
All int: []
Assignable: true
```

**Giải thích kết quả:**

- `Direct Child` có đúng hai interface khai báo trong `implements` của `Child`.
- BFS của `Child` gặp `Diamond` rồi `Right`. Sau đó, thuật toán đi vào `Left` (từ `Diamond`) và `Root`. Các lần gặp lại `Right`, `Left`, `Root` qua nhánh khác đều bị bỏ qua.
- `Base` chỉ khai báo `Left` nhưng cũng có `Root` qua `Left extends Root`.
- Với đầu vào `Diamond.class`, ta chủ động bắt đầu từ `Left` và `Right` nên **không đưa `Diamond` vào kết quả**.
- `Root` không có interface cha. `int` không có interface. Cả hai cho danh sách rỗng, nhưng lý do khác nhau.
- `isAssignableFrom` trả `true` vì `Child` gián tiếp thực thi `Root`.

## 6. Vì sao thuật toán không mất và không lặp interface?

**Đủ:** mọi interface tổ tiên đều có một chuỗi cạnh hợp lệ đi từ kiểu đầu vào qua `getInterfaces()` hoặc qua `getSuperclass()` rồi `getInterfaces()`. Thuật toán lần lượt đưa các nút kề vào hàng đợi, nên sẽ đến mỗi interface tổ tiên có thể tới được.

**Không trùng:** chỉ một thao tác `visited.add(current)` trả `true` cho mỗi đối tượng `Class<?>`. Mọi lần gặp tiếp theo đều gặp `false` và bị `continue`, nên kết quả chứa mỗi interface đúng một lần.

**Kết thúc:** số lớp trên chuỗi `getSuperclass()` và số interface tổ tiên của một kiểu Java đã nạp là hữu hạn. Một lớp cha chỉ được đưa vào hàng đợi qua lớp con của nó; mỗi interface chỉ mở rộng một lần. Hàng đợi cuối cùng sẽ rỗng.

Nếu gọi `V` là số nút lớp/interface gặp được và `E` là số cạnh kế thừa/triển khai được xét, thời gian là **O(V + E)**. Bộ nhớ dành cho hàng đợi và tập đã thăm tối đa **O(V + E)** trong cách cài đặt này, bởi một interface có thể được đưa vào hàng đợi từ nhiều cạnh trước khi được xử lý. Chuyển `visited` thành `ArrayList` tốn thêm O(V).

BFS tránh giới hạn độ sâu lời gọi của cách đệ quy thuần. Một bản DFS với `HashSet` hoặc `LinkedHashSet` cũng đúng nếu kiểm tra `visited.add(...)` **trước** khi đi sâu vào các interface cha; điểm khác chủ yếu là thứ tự kết quả và việc dùng call stack.

## 7. Những trường hợp dễ nhầm

### 7.1. Chỉ lấy `getInterfaces()` của lớp con

Sai với mục tiêu “tất cả interface”: `Child.class.getInterfaces()` không tự trả `Left` qua `Base` và cũng không tự khai triển `Root`. Cần duyệt cả hai loại cạnh: **interface → superinterface** và **class → superclass**.

### 7.2. Dùng `List` rồi thêm vô điều kiện

Nếu chỉ cộng dồn interface theo các nhánh, cấu trúc hình thoi sinh phần tử lặp. Hãy dùng `Set<Class<?>>` để kiểm tra danh tính trước khi duyệt tiếp. Không chỉ lọc `distinct()` ở cuối: làm vậy không ngăn các nhánh chung bị xử lý nhiều lần trong lúc duyệt.

### 7.3. Dùng tên interface làm khóa

`getSimpleName()` có thể trùng giữa các package. `getName()` phân biệt package nhưng chưa đủ khi cùng tên được định nghĩa trong các class loader khác nhau. Khóa `Class<?>` bám đúng kiểu runtime; chỉ dùng tên khi **hiển thị**.

### 7.4. Nhầm kiểm tra quan hệ với liệt kê tổ tiên

Nếu chỉ cần hỏi “`Service` có triển khai `Runnable` không?”, dùng:

```java
boolean implementsRunnable = Runnable.class.isAssignableFrom(Service.class);
```

Đây là **đoạn minh họa**, giả sử `Service` là lớp đã được định nghĩa. Khi cần danh sách tất cả interface để xây tài liệu, kiểm tra annotation, phân tích kiểu hoặc tạo báo cáo Reflection, mới cần thuật toán duyệt.

Đặc biệt, `SomeInterface.class.isAssignableFrom(SomeInterface.class)` là `true`, trong khi `allInterfaces(SomeInterface.class)` theo **định nghĩa của bài** không tự chứa `SomeInterface.class`.

### 7.5. Bỏ qua kiểu mảng và kiểu đặc biệt

- `String[].class` có interface trực tiếp `Cloneable` và `java.io.Serializable` theo mô hình kiểu mảng của Java, dù không có mệnh đề `implements` trong mã nguồn.
- Primitive như `int.class` và `void.class` không có interface và không có superclass.
- Interface có `getSuperclass() == null`, nên phải khám phá interface cha bằng `getInterfaces()`.
- `Object.class` không có superclass; vòng lặp phải xử lý `null` an toàn.

### 7.6. Lẫn lộn raw interface và dạng generic

Ví dụ `class X implements Comparable<String>`: `getInterfaces()` trả đối tượng `Class` của `Comparable`, còn `getGenericInterfaces()` có thể biểu diễn `Comparable<String>` dưới dạng `ParameterizedType`. Thuật toán ở đây tìm **danh tính interface**, không suy luận hay hợp nhất các tham số kiểu khi đi qua chuỗi generic. Nếu cần mô hình `Type` chính xác, phải giải bài toán ánh xạ type variable riêng, không chỉ đổi `getInterfaces()` thành `getGenericInterfaces()`.

### 7.7. Tưởng thứ tự là hợp đồng nghiệp vụ

`LinkedHashSet` giữ thứ tự gặp theo BFS, còn độ ưu tiên nhánh chịu ảnh hưởng thứ tự interface trực tiếp do API cung cấp và thứ tự đưa lớp cha vào hàng đợi. Nếu cần danh sách theo tên, sắp xếp rõ ràng; nếu cần độ sâu gần nhất, tính và lưu độ sâu. Đừng dùng chỉ số trong kết quả làm “thứ bậc kế thừa” mặc định.

## 8. Checklist khi dùng trong mã thật

- **Chốt định nghĩa đầu ra:** chỉ tổ tiên, hay có bao gồm chính interface đầu vào? Ví dụ này chọn **không bao gồm**.
- **Đi đủ cạnh:** interface trực tiếp, interface cha của interface và lớp cha của lớp.
- **Thăm một lần:** chỉ mở rộng interface lần đầu `visited.add` trả `true`.
- **Đúng danh tính:** dùng `Class<?>` làm khóa, không dùng chuỗi tên.
- **Xử lý đầu vào:** kiểm tra `null`, kiểu interface, lớp, mảng, primitive; không dựa vào một câu lệnh `implements` trong mã nguồn.
- **Tách mục đích:** cần kiểm tra một quan hệ kiểu thì ưu tiên `isAssignableFrom()`; cần liệt kê toàn bộ quan hệ mới cần duyệt đồ thị.
- **Kiểm chứng cấu trúc hình thoi:** ví dụ `Diamond` phải cho `Root` đúng một lần; trường hợp kế thừa qua `Base` cũng không tạo bản sao.

**Ghi nhớ:** mô hình interface của Java được nhìn từ Reflection như một đồ thị có các nhánh hội tụ. Duyệt mọi cạnh cần thiết, nhưng dùng `visited` theo danh tính `Class<?>` để chỉ xử lý mỗi interface đúng một lần.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-relationships-005">Phân biệt quan hệ khai báo với kiểu thực tế của đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt quan hệ khai báo với kiểu thực tế của đối tượng trong Java Reflection

## 1. Vì sao cần phân biệt hai loại thông tin kiểu?

Trong Java, một biểu thức tham chiếu có **kiểu khai báo** (declared/static type) do mã nguồn xác định tại thời điểm biên dịch, còn đối tượng mà tham chiếu trỏ tới có **lớp thực tế khi chạy** (runtime class). Hai thông tin này có thể khác nhau nhưng phải tương thích về phép gán.

```java
Animal animal = new Dog();
```

Giả sử `Dog extends Animal`:

- `Animal` là kiểu khai báo của biến cục bộ `animal`. Trình biên dịch dùng kiểu này để kiểm tra lời gọi phương thức, truy cập thành viên và lựa chọn overload.
- `Dog` là lớp thực tế của đối tượng được tạo bởi `new Dog()`. `animal.getClass()` trả về đối tượng `Class` đại diện cho `Dog`.
- `Dog` phải là subtype phù hợp của `Animal` để phép gán hợp lệ. **Kiểu khai báo không tự đổi thành `Dog`** sau khi gán.
- Đối tượng chứa thông tin lớp thực tế của chính nó; nó không chứa thông tin rằng một biến cụ thể được khai báo là `Animal`. Nhiều biến có kiểu khai báo khác nhau có thể cùng tham chiếu đến một đối tượng.

**Bẫy phổ biến:** `animal.getClass()` **không** trả về `Animal.class` trong ví dụ trên. Nó trả về `Dog.class`. Reflection chuẩn trên `Object` không cung cấp một API để lấy kiểu khai báo của biến cục bộ `animal` từ tham chiếu đó.

## 2. Đọc đúng dữ liệu bằng Reflection

| Cần trả lời câu hỏi gì? | API / nguồn thông tin | Ý nghĩa |
| --- | --- | --- |
| Đối tượng này thực sự thuộc lớp nào? | `obj.getClass()` | Lớp khi chạy của đối tượng khác `null`. |
| Trường được **khai báo** nhận loại giá trị nào? | `Field.getType()` | `Class<?>` đại diện cho kiểu khai báo của trường; với generic là kiểu đã bị erasure. |
| Trường hiện đang chứa đối tượng lớp nào? | `Field.get(instance)` rồi `value.getClass()` | Phải đọc giá trị thực tế và kiểm tra `null`. |
| Phương thức khai báo kiểu trả về nào? | `Method.getReturnType()` | Kiểu trả về trong khai báo phương thức; không phải lớp của giá trị mà một lần gọi trả về. |
| Phương thức khai báo tham số loại nào? | `Method.getParameterTypes()` | Các kiểu tham số khai báo. Đối số thực tế có thể là instance của subtype. |
| Thành viên được khai báo ở lớp nào? | `Field.getDeclaringClass()`, `Method.getDeclaringClass()` | **Lớp sở hữu khai báo thành viên**, không phải kiểu của trường, đối số hay kết quả. |
| Một lớp khai báo extends/implements gì? | `Class.getSuperclass()`, `Class.getInterfaces()` | Quan hệ thừa kế được khai báo trên lớp đang xét. |
| Kiểu có thể nhận giá trị thuộc kiểu khác không? | `Target.class.isAssignableFrom(Source.class)` | Kiểm tra tương thích kiểu theo hướng `Source` → `Target`. |
| Đối tượng có là instance của một kiểu không? | `Target.class.isInstance(obj)` | Tương đương kiểm tra kiểu động như `obj instanceof Target` trong trường hợp kiểu tham chiếu. |

Tên `getDeclaredField()`, `getDeclaredFields()` và `getDeclaredMethod()` có nghĩa là chỉ tìm thành viên **được khai báo trực tiếp trong lớp được hỏi**, thay vì truy tìm thành viên kế thừa. Chữ “Declared” ở đây **không** có nghĩa là trả về kiểu khai báo của *biến tham chiếu* đến đối tượng.

Ví dụ, `Box.class.getDeclaredField("pet").getType()` lấy kiểu trường `pet` trong `Box`, còn `box.pet.getClass()` lấy lớp của đối tượng hiện đang nằm trong trường ấy.

## 3. Ví dụ Java 17+ có thể biên dịch và chạy

Lưu nguyên văn chương trình sau vào tệp `DeclaredVsRuntime.java`:

```java
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.List;

public class DeclaredVsRuntime {
    public static class Animal {
        public String speak() {
            return "Animal";
        }
    }

    public static class Dog extends Animal {
        @Override
        public String speak() {
            return "Woof";
        }

        public void fetch() {}
    }

    public static class Box {
        public Animal pet = new Dog();
        public List<Animal> friends = List.of(new Dog());
    }

    public static void inspect(Animal input) {
        System.out.println("parameter actual = "
                + input.getClass().getSimpleName());
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Animal animal = new Dog();
        System.out.println("reference runtime = "
                + animal.getClass().getSimpleName());

        Box box = new Box();
        Field petField = Box.class.getDeclaredField("pet");
        Object petValue = petField.get(box);
        System.out.println("field declared = "
                + petField.getType().getSimpleName());
        System.out.println("field actual = "
                + petValue.getClass().getSimpleName());
        System.out.println("field owner = "
                + petField.getDeclaringClass().getSimpleName());

        Method speak = Animal.class.getDeclaredMethod("speak");
        System.out.println("method owner = "
                + speak.getDeclaringClass().getSimpleName());
        System.out.println("return declared = "
                + speak.getReturnType().getSimpleName());
        System.out.println("invoke result = " + speak.invoke(animal));

        Method inspect = DeclaredVsRuntime.class
                .getDeclaredMethod("inspect", Animal.class);
        System.out.println("parameter declared = "
                + inspect.getParameterTypes()[0].getSimpleName());
        inspect(animal);

        System.out.println("Animal accepts Dog = "
                + Animal.class.isAssignableFrom(Dog.class));
        System.out.println("Dog accepts Animal = "
                + Dog.class.isAssignableFrom(Animal.class));
        System.out.println("is Animal instance = "
                + Animal.class.isInstance(animal));

        Field friendsField = Box.class.getDeclaredField("friends");
        ParameterizedType genericType =
                (ParameterizedType) friendsField.getGenericType();
        System.out.println("generic argument is Animal = "
                + (genericType.getActualTypeArguments()[0] == Animal.class));
    }
}
```

Chạy trên JDK 17 trở lên:

```bash
javac DeclaredVsRuntime.java
java DeclaredVsRuntime
```

Kết quả stdout:

```text
reference runtime = Dog
field declared = Animal
field actual = Dog
field owner = Box
method owner = Animal
return declared = String
invoke result = Woof
parameter declared = Animal
parameter actual = Dog
Animal accepts Dog = true
Dog accepts Animal = false
is Animal instance = true
generic argument is Animal = true
```

Giải thích các điểm mấu chốt:

1. `Animal animal = new Dog()` hợp lệ vì `Dog` kế thừa `Animal`. `getClass()` đọc lớp của đối tượng nên hiện `Dog`.
2. `petField.getType()` cố định ở `Animal.class` vì đó là kiểu khai báo trong `Box`; `petField.get(box)` hiện trả về một đối tượng `Dog`. Nếu mã khác gán một subtype khác vào `box.pet`, kiểu khai báo trường vẫn là `Animal`.
3. `petField.getDeclaringClass()` trả về `Box.class`: `Box` là nơi khai báo trường, **không phải** kiểu của giá trị trường.
4. `Animal.class.getDeclaredMethod("speak")` cho `Method` có chủ khai báo `Animal` và kiểu trả về khai báo `String`. Khi `speak.invoke(animal)` chạy, Java vẫn áp dụng **dynamic dispatch** cho instance method được override, nên thực thi `Dog.speak()` và cho `Woof`. `Method.invoke` không làm mất tính đa hình.
5. `inspect` khai báo tham số `Animal`; lần gọi này truyền đối tượng `Dog`. `getParameterTypes()` chỉ biết kiểu khai báo của tham số, trong khi `input.getClass()` thấy đối tượng thực tế.
6. `Animal.class.isAssignableFrom(Dog.class)` trả `true` theo hướng “biến `Animal` có thể nhận đối tượng `Dog`”. Đảo hai vế không tương đương.
7. `friendsField.getGenericType()` đọc thông tin `List<Animal>` được giữ trong metadata khai báo trường; việc ấy không chứng minh rằng mọi phần tử được thêm lúc chạy đều mang lớp `Animal` chính xác (phần tử có thể là `Dog`).

## 4. Kiểu khai báo quyết định gì, kiểu thực tế quyết định gì?

### Truy cập thành viên và overload được xét tại thời điểm biên dịch

```java
Animal a = new Dog();
a.speak();   // Hợp lệ: speak được khai báo trong Animal.
// a.fetch(); // Lỗi biên dịch: Animal không khai báo fetch.
```

Dù đối tượng là `Dog`, tên phương thức `fetch` không xuất hiện trên kiểu tĩnh `Animal`. Muốn gọi chức năng riêng của subtype, kiểm tra kiểu động trước rồi dùng biến đã thu hẹp kiểu:

```java
if (a instanceof Dog dog) {
    dog.fetch();
}
```

Overloading (`choose(Animal)` và `choose(Dog)`) được chọn chủ yếu bằng kiểu của biểu thức truyền vào tại thời điểm biên dịch; overriding (`Animal.speak()` được `Dog` ghi đè) được chọn động theo lớp đối tượng khi chạy. Vì vậy, không thể dùng `obj.getClass()` để suy ra Java đã chọn overload nào từ cú pháp lời gọi ban đầu.

### Gọi phương thức override tuân theo kiểu thực tế

Với `Animal a = new Dog()`, biểu thức `a.speak()` sử dụng khai báo nhìn thấy qua `Animal` nhưng hành vi của `Dog`. Trong Reflection, nếu có `Method` của instance method trên lớp cha, `method.invoke(a)` cũng dispatch tới implementation override phù hợp (trừ các trường hợp có quy tắc riêng như `static`, `private`; `Method.invoke` không thể làm override những phương thức không được override).

### Một trường có kiểu khai báo ổn định nhưng giá trị thay đổi

`Field.getType()` chỉ phản ánh signature của trường, không phụ thuộc instance cụ thể. Một trường `Animal pet` có thể lần lượt chứa `new Dog()`, subtype `Cat`, hoặc `null`. Dùng `Field.get(obj)` để lấy **giá trị tại thời điểm đọc**; nếu không phải `null` mới gọi `getClass()` được. Với trường `static`, `Field.get(null)` là cách đọc hợp lệ nếu quyền truy cập cho phép; `null` trong trường hợp này là tham số `get`, không phải lớp động của giá trị.

## 5. Kiểm tra quan hệ kiểu mà không đảo chiều

`Class.isAssignableFrom` có hướng đặc biệt dễ nhầm:

```java
boolean canStoreDogInAnimal = Animal.class.isAssignableFrom(Dog.class); // true
boolean canStoreAnimalInDog = Dog.class.isAssignableFrom(Animal.class); // false
```

Cách đọc: `Target.class.isAssignableFrom(Source.class)` nghĩa là **tham chiếu kiểu Target có thể được gán một giá trị kiểu Source** theo các quy tắc tương thích kiểu của API. Điều này hữu ích khi framework muốn xác nhận một class implementation có đáp ứng một interface/superclass hay không.

Khi đã có **đối tượng** thay vì cặp `Class`, dùng `Target.class.isInstance(value)` để kiểm tra (trả `false` với `null`). Nếu cần cast qua Reflection, `Target.class.cast(value)` tạo tham chiếu đã kiểm tra kiểu, ném `ClassCastException` nếu giá trị không tương thích. Trong Java thông thường, `instanceof` và pattern matching thường rõ ràng hơn:

```java
Object candidate = new Dog();
if (candidate instanceof Animal animal) {
    System.out.println(animal.speak()); // Woof
}
```

`getSuperclass()` và `getInterfaces()` mô tả **quan hệ trực tiếp được khai báo** của một lớp, không nhất thiết liệt kê toàn bộ tổ tiên/transitive interfaces. Muốn hỏi khả năng gán và hiện thực interface qua nhiều cấp, `isAssignableFrom` thường phù hợp hơn việc duyệt mỗi cấp thủ công.

## 6. Các trường hợp dễ gây nhầm

**`null` không có lớp thực tế để phản chiếu.** Một biến `Animal a = null` vẫn có kiểu khai báo `Animal` trong mã nguồn, nhưng `a.getClass()` ném `NullPointerException`. Luôn kiểm tra giá trị trước khi hỏi lớp runtime.

**Generics bị type erasure.** Trường `List<Animal> friends` có `Field.getType() == List.class`. `Field.getGenericType()` có thể cho `ParameterizedType` mang tham số `Animal` từ chữ ký khai báo. Nhưng `friends.getClass()` là lớp implementation của danh sách; nó không cung cấp `List<Animal>` hay lớp runtime chính xác của từng phần tử. Kiểu phần tử chỉ có thể kiểm tra trên từng phần tử hiện có, nếu khác `null`. `getGenericType()` không phải máy “giải” mọi type argument thực tế từ instance.

**Primitive khác object.** Với trường `int count`, `Field.getType() == int.class`. Khi gọi `Field.get(instance)`, Reflection boxing kết quả thành `Integer`; gọi `getClass()` trên giá trị boxing thấy `Integer.class`, không phải `int.class`. `int.class` không có instance của kiểu nguyên thủy để gọi `getClass()`.

**Mảng và interface.** `Object o = new String[2]` có `o.getClass() == String[].class`. Dù mảng `String[]` có thể được gán vào `Object[]` (array covariance), khi gán phần tử không tương thích vào mảng thật Java ném `ArrayStoreException`. Quan hệ assignability và lớp runtime của mảng đều phải được xét.

**Proxy và lớp được sinh động.** Nếu một đối tượng là dynamic proxy, `getClass()` trả về lớp proxy được tạo lúc chạy, thay vì interface mà tham chiếu được khai báo. Kiểm tra nó có triển khai interface không bằng `SomeInterface.class.isInstance(proxy)` hoặc `isAssignableFrom`; không so tên lớp proxy với tên interface.

**Metadata lớp không đồng nghĩa metadata biến.** `Class<?> runtimeClass = value.getClass()` chỉ mang `Class` của đối tượng. Reflection tiêu chuẩn chủ yếu tra cấu trúc class, field, method, constructor; không có `getDeclaredType()` trên `Object` để hỏi kiểu nguồn của từng tham chiếu cục bộ. Có thể dùng `Field`, `Method` hoặc `Parameter` để đọc khai báo **thành viên tương ứng**, khi đã biết phần tử chương trình cần xem xét.

**Quyền truy cập vẫn áp dụng.** `getDeclaredField` có thể tìm thấy trường `private`, nhưng `Field.get` hoặc `Method.invoke` có thể ném `IllegalAccessException` nếu chưa được phép truy cập. Java modules có thể giới hạn deep reflection; việc biết lớp runtime không tự cấp quyền mở thành viên nội bộ.

## 7. Cách chọn API trong bài toán thực tế

Khi xử lý một đối tượng trong framework, đi từ câu hỏi cụ thể:

1. **Hợp đồng API**: cần biết property, tham số hay kiểu trả về đã *khai báo*? Đọc `Field.getType()`, `Method.getParameterTypes()`, `Method.getReturnType()`, và `getGenericType()`/`getGenericReturnType()` khi cần metadata generic. Đây là nền tảng để validate schema, binding và lựa chọn converter.
2. **Giá trị ở thời điểm chạy**: cần biết object hiện tại là lớp nào? Đọc `value.getClass()` sau kiểm tra `null`. Đừng dùng nó thay cho signature được khai báo.
3. **Tính tương thích**: cần hỏi class implementation có thể gán cho kiểu API hay không? Dùng `targetType.isAssignableFrom(candidateType)`; nếu đã có value, dùng `targetType.isInstance(value)`.
4. **Hành vi đa hình**: cần gọi instance method đã override? Dùng lời gọi Java thường hoặc `Method.invoke` khi thực sự cần reflection; class thực tế quyết định implementation được dispatch.
5. **Nguồn khai báo thành viên**: cần biết một `Field`/`Method` thuộc lớp nào? Dùng `getDeclaringClass()`, không suy diễn từ `getType()` hay `getClass()` của giá trị.

**Tự kiểm tra:** với khai báo `Number n = Integer.valueOf(7)`, kiểu biến là `Number`, `n.getClass()` là `Integer.class`, và `Number.class.isAssignableFrom(Integer.class)` là `true`. Nếu đổi `n` thành `null`, kiểu khai báo vẫn là `Number` nhưng không có đối tượng runtime để gọi `getClass()`. Đây là phép phân biệt cần giữ nhất quán khi đọc và viết mã Reflection.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-relationships-006">Kiểu mảng với Object, Cloneable và Serializable</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu mảng với `Object`, `Cloneable` và `Serializable` trong Java

## 1. Mục tiêu và mô hình kiểu

Trong Java, mảng (`array`) là **đối tượng** được tạo ở runtime, đồng thời mỗi mảng có một **kiểu lớp mảng** riêng. Dù phần tử là kiểu nguyên thủy như `int` hay kiểu tham chiếu như `String`, bản thân biến mảng luôn chứa tham chiếu đến một đối tượng mảng (hoặc `null`).

Một lớp mảng bất kỳ đều có các quan hệ sau:

- Có `java.lang.Object` là **lớp cha trực tiếp**.
- Triển khai trực tiếp hai giao diện đánh dấu `java.lang.Cloneable` và `java.io.Serializable`.
- Có thể gán cho biến kiểu `Object`, `Cloneable` hoặc `Serializable` mà không cần ép kiểu.
- Có thể truy vấn qua `Class<?>`, chẳng hạn `isArray()`, `getComponentType()`, `getSuperclass()`, `getInterfaces()` và `isAssignableFrom()`.

Ví dụ, các phép gán sau đều hợp lệ:

```java
int[] numbers = {1, 2, 3};
String[] names = {"An", "Bình"};

Object first = numbers;
Object second = names;
Cloneable cloneable = numbers;
Serializable serializable = names;
```

Để biên dịch ví dụ này trong một lớp độc lập, thêm `import java.io.Serializable;`. `Cloneable` thuộc `java.lang` nên không cần import.

Điểm cần nhớ: *mảng là đối tượng* không đồng nghĩa *mảng nào cũng là `Object[]`*. `Object` là lớp cha của mọi lớp mảng; còn `Object[]` là một **kiểu mảng có phần tử kiểu `Object`**, nên tuân theo các quy tắc tương thích phần tử khác.

## 2. Hiểu đúng quan hệ với `Object`

Khai báo `int[]`, `String[]` và `int[][]` đều biểu thị kiểu tham chiếu. Vì vậy, chúng đều là subtype của `Object`:

```java
Object a = new int[3];
Object b = new String[3];
Object c = new int[2][3];

System.out.println(a instanceof Object); // true
System.out.println(b instanceof Object); // true
System.out.println(c instanceof Object); // true
```

Ví dụ trên là **đoạn mã minh họa**, có thể đặt trong phương thức `main`.

Ở runtime, `new int[3]` tạo một đối tượng mảng và điền phần tử bằng giá trị mặc định `0`. `new String[3]` tạo một đối tượng mảng mà từng phần tử ban đầu là `null`. Việc đối tượng mảng thuộc `Object` không làm biến đổi kiểu phần tử.

Mọi lớp mảng đều trả về `Object.class` khi gọi `getSuperclass()`. Tuy nhiên, lớp mảng không phải lớp Java do lập trình viên khai báo bằng `extends Object`, và không thể khai báo một lớp con tùy ý kế thừa từ `int[]` hay `String[]`. JVM tạo các lớp mảng theo nhu cầu và Java không cho phép khai báo lớp kế thừa từ kiểu mảng.

### Vì sao `int[]` không gán được cho `Object[]`?

`Object[]` chứa các **tham chiếu đối tượng**; `int[]` chứa các giá trị `int` nguyên thủy. Do đó:

```java
int[] values = {1, 2};
Object anything = values;        // Hợp lệ
// Object[] bad = values;       // LỖI BIÊN DỊCH
```

Phép chuyển từ `int[]` sang `Object[]` không trở nên hợp lệ bằng cách ép kiểu trực tiếp. Nếu đưa một `int[]` vào biến `Object` rồi ép thành `Object[]`, chương trình sẽ ném `ClassCastException` tại runtime. Muốn một mảng phần tử tham chiếu, phải tạo kiểu phù hợp, ví dụ `Integer[]`, và chuyển các phần tử một cách tường minh khi cần.

Ngược lại, `String[]` **có thể** gán cho `Object[]`, vì `String` là subtype của `Object`:

```java
String[] strings = {"a", "b"};
Object[] objects = strings;      // Hợp lệ
System.out.println(objects[0]); // a
```

Quy tắc này được gọi là **hiệp biến của mảng tham chiếu** (array covariance), được xét kỹ ở mục 5.

## 3. Quan hệ với `Cloneable`: sao chép mảng

Mọi đối tượng mảng triển khai `Cloneable` và hỗ trợ gọi `clone()` trên biểu thức kiểu mảng. Phép clone tạo **đối tượng mảng mới cùng kiểu runtime, cùng độ dài**, sau đó sao chép giá trị của từng phần tử.

- Với mảng nguyên thủy như `int[]`, mỗi giá trị nguyên thủy được sao chép.
- Với mảng tham chiếu như `StringBuilder[]`, từng **tham chiếu** được sao chép; đối tượng được tham chiếu không được sao chép đệ quy.
- Với mảng nhiều chiều như `int[][]`, chỉ **mảng ngoài** được clone; các mảng con vẫn được hai mảng ngoài cùng tham chiếu. Đây là **shallow copy**.
- `clone()` của mảng trả về đúng kiểu mảng ở thời điểm biên dịch, ví dụ `int[] copy = original.clone();`, không cần ép kiểu.

Ví dụ:

```java
int[] original = {1, 2};
int[] copy = original.clone();
copy[0] = 99;

System.out.println(original[0]); // 1
System.out.println(copy[0]);     // 99

int[][] matrix = {{1, 2}, {3, 4}};
int[][] shallow = matrix.clone();
shallow[0][0] = 99;

System.out.println(matrix[0][0]); // 99
System.out.println(matrix == shallow); // false
System.out.println(matrix[0] == shallow[0]); // true
```

Đây là đoạn minh họa đặt trong `main`. Để sao chép độc lập từng hàng của `int[][]`, cần tự clone từng mảng con (hoặc áp dụng thuật toán sao chép sâu phù hợp với cấu trúc dữ liệu).

**Giới hạn của `Cloneable`:** Gán `int[]` cho biến `Cloneable` chỉ chứng minh đối tượng có giao diện đánh dấu. Giao diện `Cloneable` **không khai báo phương thức `clone()`**. Vì vậy `Cloneable marker = numbers; marker.clone();` không biên dịch; cần giữ hoặc lấy lại kiểu mảng thích hợp. `Object.clone()` là phương thức `protected`, còn cú pháp gọi `clone()` trên kiểu mảng được ngôn ngữ Java cho phép một cách đặc biệt.

## 4. Quan hệ với `Serializable`: khả năng tuần tự hóa

`Serializable` cũng là **marker interface**, không khai báo phương thức. Các kiểu mảng như `int[]`, `String[]` và `Object[]` đều triển khai `Serializable`, bất kể kiểu phần tử của chúng.

Điều này cho phép cơ chế Java Object Serialization xử lý một đối tượng mảng. Tuy nhiên, để **ghi thành công một mảng tham chiếu có phần tử không null**, mỗi đối tượng phần tử được ghi cùng đồ thị đối tượng cần hỗ trợ tuần tự hóa theo hợp đồng của cơ chế này:

- `int[]` có thể tuần tự hóa các giá trị `int`.
- `String[]` chứa các `String` có thể tuần tự hóa vì `String` triển khai `Serializable`.
- Một mảng chứa đối tượng của lớp không triển khai `Serializable` có thể ném `NotSerializableException` trong quá trình `ObjectOutputStream.writeObject(...)`.
- Mảng có kiểu thành phần là lớp không serializable **vẫn là một đối tượng `Serializable`**; chẳng hạn mảng rỗng hoặc các phần tử đều `null` có thể ghi thành công vì không có đối tượng phần tử không serializable phải ghi.

```java
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;

public class ArraySerializationDemo {
    static class PlainItem {
        int value = 1;
    }

    private static void write(Object value) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
    }

    public static void main(String[] args) throws IOException {
        write(new int[] {1, 2});
        System.out.println("int[]: OK");

        write(new PlainItem[0]);
        System.out.println("PlainItem[0]: OK");

        try {
            write(new PlainItem[] {new PlainItem()});
        } catch (NotSerializableException e) {
            System.out.println("PlainItem[1]: NotSerializableException");
        }
    }
}
```

Lưu tệp `ArraySerializationDemo.java`, chạy bằng Java 17 trở lên:

```bash
javac ArraySerializationDemo.java
java ArraySerializationDemo
```

Kết quả:

```text
int[]: OK
PlainItem[0]: OK
PlainItem[1]: NotSerializableException
```

`Serializable` chỉ thể hiện khả năng tham gia cơ chế serialization; **không bảo đảm mọi nội dung cụ thể đều tuần tự hóa được**. Serialization gốc của Java còn liên quan đến tương thích phiên bản, an toàn dữ liệu và rủi ro giải tuần tự hóa dữ liệu không tin cậy; không nên dùng nó làm định dạng trao đổi dữ liệu mở mặc định.

## 5. Quy tắc gán kiểu giữa các loại mảng

### 5.1. Mảng nguyên thủy và mảng tham chiếu

| Kiểu nguồn | Kiểu đích | Gán trực tiếp? | Lý do |
| --- | --- | --- | --- |
| `int[]` | `Object` | Có | Mọi mảng là đối tượng |
| `int[]` | `Cloneable` | Có | Mọi mảng triển khai marker interface này |
| `int[]` | `Serializable` | Có | Mọi mảng triển khai marker interface này |
| `int[]` | `Object[]` | Không | `int` không phải kiểu tham chiếu `Object` |
| `String[]` | `Object[]` | Có | Mảng tham chiếu hiệp biến; `String <: Object` |
| `Integer[]` | `Number[]` | Có | Mảng tham chiếu hiệp biến; `Integer <: Number` |
| `int[]` | `long[]` | Không | Không có phép mở rộng kiểu nguyên thủy áp dụng cho **toàn mảng** |
| `int[][]` | `Object[]` | Có | Phần tử của mảng ngoài là `int[]`, một kiểu tham chiếu thuộc `Object` |
| `int[][]` | `Cloneable[]` | Có | Phần tử `int[]` triển khai `Cloneable` |
| `int[][]` | `Object[][]` | Không | `int[]` không phải subtype của `Object[]` |

Ký hiệu `A <: B` ở đây nghĩa là `A` là subtype của `B`.

### 5.2. Hiệp biến không làm mất kiểm tra kiểu runtime

Java cho phép `String[]` được nhìn qua biến `Object[]`. Nhưng đối tượng thực tế vẫn là `String[]`. Nếu cố lưu `Integer` vào đó, JVM ném `ArrayStoreException`:

```java
String[] words = {"Java"};
Object[] array = words;          // Được phép
// array[0] = Integer.valueOf(7); // Biên dịch được, nhưng ném ArrayStoreException
```

Chính xác hơn, câu lệnh ghi ở trên **biên dịch được** do kiểu tĩnh của `array` là `Object[]`; lỗi chỉ phát sinh khi thực thi, do phần tử của mảng runtime chỉ nhận `String` (và `null`).

Mảng là reifiable: thông tin kiểu phần tử của một đối tượng mảng được runtime giữ để kiểm tra phép ghi. Quy tắc này khác với cơ chế xóa kiểu của Java generics; không được suy luận rằng `List<String>` hoạt động giống `String[]`. Việc tạo trực tiếp mảng có kiểu phần tử parameterized không reifiable như `new List<String>[10]` cũng bị Java từ chối khi biên dịch.

### 5.3. Mảng nhiều chiều là mảng chứa mảng

`int[][]` là mảng mà **mỗi phần tử có kiểu `int[]`**. Vì thế, bản thân `int[][]` là một mảng tham chiếu và có thể gán cho `Object[]`, dù các phần tử cuối cùng là giá trị nguyên thủy.

```java
int[][] grid = {{10, 20}, {30}};
Object[] outer = grid;

System.out.println(outer[0] instanceof int[]); // true
System.out.println(((int[]) outer[0])[1]);       // 20
```

Điều đó **không** khiến `int[][]` trở thành `Object[][]`. Để là `Object[][]`, phần tử của mảng ngoài phải tương thích với `Object[]`; còn `int[]` không phải subtype của `Object[]`.

## 6. Kiểm tra bằng Java Reflection

Các API của `java.lang.Class` giúp kiểm tra quan hệ kiểu mà không cần tạo đối tượng:

- `SomeArray.class.isArray()`: kiểm tra đối tượng `Class` biểu diễn kiểu mảng.
- `getComponentType()`: trả về `Class` của kiểu phần tử trực tiếp; với `int[][]`, kết quả là `int[].class`, không phải `int.class`.
- `getSuperclass()`: trả về `Object.class` đối với mọi lớp mảng.
- `getInterfaces()`: đối với lớp mảng trả về `Cloneable` và `Serializable`. Không nên phụ thuộc vào **thứ tự** trả về khi in kết quả.
- `Destination.class.isAssignableFrom(Source.class)`: `true` nếu giá trị có kiểu `Source` gán được cho biến `Destination` bằng phép gán mở rộng hoặc đồng nhất cho kiểu tham chiếu; thứ tự đối số là **đích.isAssignableFrom(nguồn)**.
- `getTypeName()`: hiển thị tên thân thiện, ví dụ `int[]`, `java.lang.String[]`. `getName()` của lớp mảng dùng dạng ký hiệu JVM như `[I` và `[Ljava.lang.String;`, không phải cú pháp mã nguồn thông thường.

Ví dụ hoàn chỉnh:

```java
import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.Collectors;

public class ArrayTypeRelationsDemo {
    public static void main(String[] args) {
        Class<?> arrayClass = int[].class;

        System.out.println("Superclass: " + arrayClass.getSuperclass().getTypeName());
        String interfaces = Arrays.stream(arrayClass.getInterfaces())
                .map(Class::getSimpleName)
                .sorted()
                .collect(Collectors.joining(", "));
        System.out.println("Interfaces: " + interfaces);

        System.out.println("int[] -> Object: "
                + Object.class.isAssignableFrom(int[].class));
        System.out.println("int[] -> Cloneable: "
                + Cloneable.class.isAssignableFrom(int[].class));
        System.out.println("int[] -> Serializable: "
                + Serializable.class.isAssignableFrom(int[].class));
        System.out.println("int[] -> Object[]: "
                + Object[].class.isAssignableFrom(int[].class));
        System.out.println("String[] -> Object[]: "
                + Object[].class.isAssignableFrom(String[].class));
        System.out.println("int[][] -> Object[]: "
                + Object[].class.isAssignableFrom(int[][].class));
        System.out.println("int[][] -> Object[][]: "
                + Object[][].class.isAssignableFrom(int[][].class));
        System.out.println("int[][] component: "
                + int[][].class.getComponentType().getTypeName());

        String[] words = {"Java"};
        Object[] aliases = words;
        try {
            aliases[0] = Integer.valueOf(7);
        } catch (ArrayStoreException e) {
            System.out.println("Checked store: ArrayStoreException");
        }

        int[][] matrix = {{1, 2}};
        int[][] shallow = matrix.clone();
        shallow[0][0] = 99;
        System.out.println("Shallow clone shares row: " + (matrix[0][0] == 99));
    }
}
```

Lưu tệp `ArrayTypeRelationsDemo.java`. Trong thư mục chứa tệp, thực hiện:

```bash
javac ArrayTypeRelationsDemo.java
java ArrayTypeRelationsDemo
```

Stdout mong đợi trên Java 17+:

```text
Superclass: java.lang.Object
Interfaces: Cloneable, Serializable
int[] -> Object: true
int[] -> Cloneable: true
int[] -> Serializable: true
int[] -> Object[]: false
String[] -> Object[]: true
int[][] -> Object[]: true
int[][] -> Object[][]: false
int[][] component: int[]
Checked store: ArrayStoreException
Shallow clone shares row: true
```

Vì đã sắp xếp tên giao diện trước khi in, ví dụ không dựa vào thứ tự `Class.getInterfaces()`.

## 7. Phân biệt các câu hỏi Reflection dễ nhầm

| Câu hỏi | API nên dùng | Ví dụ |
| --- | --- | --- |
| Đây có phải kiểu mảng? | `type.isArray()` | `int[].class.isArray() == true` |
| Phần tử trực tiếp có kiểu nào? | `type.getComponentType()` | `int[][].class.getComponentType() == int[].class` |
| Lớp cha trực tiếp của mảng là gì? | `type.getSuperclass()` | `String[].class.getSuperclass() == Object.class` |
| Mảng triển khai trực tiếp giao diện nào? | `type.getInterfaces()` | `Cloneable` và `Serializable` |
| Một giá trị kiểu A có gán được vào biến kiểu B? | `B.class.isAssignableFrom(A.class)` | `Object[].class.isAssignableFrom(String[].class) == true` |
| Một đối tượng cụ thể có thuộc kiểu cho trước? | `type.isInstance(value)` | `Object[].class.isInstance(new int[1]) == false` |

`isAssignableFrom` hoạt động với đối tượng `Class`, còn `isInstance` kiểm tra một **đối tượng runtime**. Đừng đảo chiều `isAssignableFrom`: `Object.class.isAssignableFrom(int[].class)` là `true`, nhưng `int[].class.isAssignableFrom(Object.class)` là `false`.

Cũng cần tách biệt `instanceof Object` với `instanceof Object[]`. Ví dụ `new int[1] instanceof Object` là đúng, nhưng phép kiểm tra xem đó có phải `Object[]` thì sai. Khi cần kiểm tra động mà kiểu biểu thức cố định khiến phép `instanceof` không hợp lệ ở biên dịch, hãy dùng `Class.isInstance(...)` trên một giá trị đã được nhìn qua `Object`.

## 8. Lỗi thường gặp và cách tự kiểm tra

1. **Cho rằng mảng nguyên thủy không phải đối tượng.** Thực tế, `int[]` là đối tượng và có thể gán cho `Object`, `Cloneable` và `Serializable`.
2. **Đồng nhất `Object` với `Object[]`.** `Object[]` có quy tắc tương thích kiểu phần tử; `int[]` không chuyển thành `Object[]`.
3. **Xem `Cloneable` là cam kết sao chép sâu.** Clone của mảng là sao chép nông; mảng con và các đối tượng tham chiếu vẫn được dùng chung.
4. **Gọi `clone()` qua biến `Cloneable`.** Giao diện đánh dấu này không khai báo `clone()`.
5. **Cho rằng `Serializable` bảo đảm luôn ghi mảng thành công.** Khi serializing, các phần tử không serializable vẫn có thể gây `NotSerializableException`.
6. **Bỏ qua `ArrayStoreException`.** Hiệp biến cho phép phép gán kiểu rộng ở compile time, nhưng JVM kiểm tra kiểu phần tử khi ghi.
7. **Đảo chiều `isAssignableFrom`.** Luôn đọc `ĐÍCH.isAssignableFrom(NGUỒN)`.
8. **Nhầm mảng nhiều chiều với mảng nguyên thủy một chiều.** `int[][]` là mảng ngoài chứa các tham chiếu `int[]`, nên có thể là `Object[]`; còn `int[]` thì không.
9. **Cho rằng `getComponentType()` trả về kiểu phần tử sâu nhất.** Với `String[][][]`, gọi một lần chỉ được `String[][]`; lặp lại mới đi sâu tới `String`.

### Bài tự kiểm tra

Không chạy code ngay: hãy dự đoán rồi dùng `Class.isAssignableFrom` hoặc `Class.isInstance` để kiểm chứng.

```java
Object.class.isAssignableFrom(byte[].class)          // ?
Object[].class.isAssignableFrom(byte[][].class)      // ?
Serializable.class.isAssignableFrom(int[][].class)  // ?
Cloneable[].class.isAssignableFrom(int[][].class)    // ?
Object[][].class.isAssignableFrom(String[][].class)  // ?
Object[][].class.isAssignableFrom(int[][].class)     // ?
```

Đáp án lần lượt: **true, true, true, true, true, false**. Giải thích: mọi mảng là `Object` và triển khai hai marker interface; mảng ngoài của `byte[][]`/`int[][]` chứa đối tượng mảng `byte[]`/`int[]`; `String[]` là subtype của `Object[]`, còn `int[]` thì không.

## 9. Ghi nhớ

Quy tắc gốc là **mọi lớp mảng đều có superclass `Object` và triển khai `Cloneable`, `Serializable`**. Khi hỏi mảng có gán được cho một **kiểu mảng khác** hay không, phải xét thêm kiểu phần tử, phân biệt mảng nguyên thủy, mảng tham chiếu và từng chiều của mảng nhiều chiều. Khi thao tác với dữ liệu, nhớ `clone()` là shallow copy, serialization còn phụ thuộc đối tượng phần tử, và việc ghi vào mảng hiệp biến luôn được JVM kiểm tra ở runtime.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-relationships-007">Primitive và wrapper không có quan hệ kế thừa thay cho boxing</a>

<details>
<summary>Xem chi tiết</summary>

# Primitive và wrapper không có quan hệ kế thừa thay cho boxing

Khi làm việc với Java Reflection, ta thường gặp hai kiểu biểu diễn có vẻ liên quan: kiểu nguyên thủy như `int` và lớp bao như `Integer`. Cả hai đều có đối tượng `Class<?>` tương ứng, nhưng **boxing/unboxing là quy tắc chuyển đổi giá trị của ngôn ngữ Java, không phải quan hệ kế thừa giữa hai kiểu**. Vì vậy, API kiểm tra quan hệ kiểu và API gọi phương thức bằng reflection có thể cho kết quả khác nhau mà vẫn hoàn toàn nhất quán.

Bài này tập trung vào việc đọc chính xác `Class`, `isAssignableFrom`, `isInstance`, tra cứu chữ ký phương thức, truyền đối số qua `Method.invoke` và truy cập `Field`.

## 1. Phân biệt primitive, wrapper và boxing

Java có các kiểu primitive như `boolean`, `byte`, `short`, `char`, `int`, `long`, `float`, `double`. Mỗi primitive có một lớp wrapper tương ứng: `Boolean`, `Byte`, `Short`, `Character`, `Integer`, `Long`, `Float`, `Double`.

| Góc nhìn | `int` | `Integer` |
| --- | --- | --- |
| Loại kiểu | Primitive | Reference type (class) |
| Giá trị | Giá trị số nguyên trực tiếp | Tham chiếu đến đối tượng chứa số nguyên |
| `Class` token | `int.class` hay `Integer.TYPE` | `Integer.class` |
| `isPrimitive()` | `true` | `false` |
| Kế thừa từ `Number`/`Object` | Không | `Integer` kế thừa `Number`, và `Number` kế thừa `Object` |
| Có thể nhận `null` | Không | Có |
| `instanceof` | Không dùng trực tiếp trên giá trị primitive | Có thể dùng trên đối tượng wrapper |

Ví dụ Java biên dịch tự động chèn chuyển đổi thích hợp:

```java
int primitive = 7;
Integer boxed = primitive;   // boxing: int -> Integer
int restored = boxed;       // unboxing: Integer -> int
Number number = primitive;  // boxing int -> Integer, rồi widening reference -> Number
Object object = primitive;  // boxing int -> Integer, rồi widening reference -> Object
```

`Integer` không `extends int`; primitive không phải lớp trong cây kế thừa. **Gán được nhờ chuyển đổi** không có nghĩa **có quan hệ subtype**.

Cũng cần tách hai khái niệm khác: `int` có thể *widen* thành `long` trong ngữ cảnh chuyển đổi số, nhưng `long` không phải lớp cha của `int`. Và không có quy tắc tổng quát tự động chuyển `Integer` thành `Long` chỉ vì giá trị số có thể đổi từ `int` sang `long`.

## 2. Class token biểu diễn danh tính kiểu, không thực hiện boxing

`int.class` và `Integer.class` là hai đối tượng `Class` khác nhau. `Integer.TYPE` chỉ là hằng tham chiếu tới `int.class`, **không** phải `Integer.class`:

```java
System.out.println(int.class == Integer.TYPE);       // true
System.out.println(int.class == Integer.class);      // false
System.out.println(int.class.isPrimitive());         // true
System.out.println(Integer.class.isPrimitive());     // false
```

Bản thân `Class` có thể mô tả primitive dù primitive không phải đối tượng thuộc hệ kế thừa `Object`. Chẳng hạn `int.class.getSuperclass()` trả về `null`; `Integer.class.getSuperclass()` trả về `Number.class`.

`value.getClass()` luôn được gọi trên một **đối tượng**. Với `int n = 7;`, biểu thức `((Object) n).getClass()` trả về `Integer.class` vì compiler đã boxing `n` khi ép sang `Object`. Không thể suy ra từ đó rằng đối tượng lúc chạy có lớp `int.class`.

## 3. `isAssignableFrom` kiểm tra quan hệ kiểu, không mô phỏng phép gán có chuyển đổi

Đọc biểu thức `Destination.class.isAssignableFrom(Source.class)` là: một giá trị có **kiểu Source** có thể được coi là **kiểu Destination** bằng quan hệ cùng kiểu hoặc quan hệ mở rộng kiểu thích hợp hay không? Với reference types, kiểm tra này xét kế thừa/lớp và interface. Với primitive, chỉ chính kiểu primitive đó mới tương thích theo phép kiểm tra này. API **không** tính boxing, unboxing hay widening primitive như `int → long`.

| Biểu thức | Kết quả | Lý do |
| --- | --- | --- |
| `Integer.class.isAssignableFrom(int.class)` | `false` | `int` không là subtype của `Integer` |
| `int.class.isAssignableFrom(Integer.class)` | `false` | `Integer` không là subtype của `int` |
| `Object.class.isAssignableFrom(int.class)` | `false` | Primitive không kế thừa `Object` |
| `Object.class.isAssignableFrom(Integer.class)` | `true` | `Integer` là reference subtype của `Object` |
| `Number.class.isAssignableFrom(Integer.class)` | `true` | `Integer extends Number` |
| `long.class.isAssignableFrom(int.class)` | `false` | Primitive widening không phải assignability theo API |
| `int.class.isAssignableFrom(int.class)` | `true` | Cùng một kiểu |

Lưu ý: mô tả «có thể gán» ở tên API dễ gây hiểu nhầm nếu suy diễn rằng nó thực hiện mọi conversion mà cú pháp gán Java chấp nhận. Ví dụ `Integer boxed = 7;` hợp lệ nhờ boxing, nhưng `Integer.class.isAssignableFrom(int.class)` vẫn `false`.

## 4. `isInstance` kiểm tra đối tượng lúc chạy

`someClass.isInstance(value)` kiểm tra xem `value` là một instance của reference type được đại diện hay không; `null` luôn cho `false`. `int.class.isInstance(...)` luôn `false` vì không có đối tượng Java nào thuộc lớp runtime `int`:

```java
Integer value = Integer.valueOf(7);

System.out.println(Integer.class.isInstance(value)); // true
System.out.println(Number.class.isInstance(value));  // true
System.out.println(Object.class.isInstance(value));  // true
System.out.println(int.class.isInstance(value));     // false
System.out.println(Integer.class.isInstance(null));  // false
```

Ở đây `value` là đối tượng `Integer`. Khi viết `int.class.isInstance(7)`, số `7` được boxing trước để đưa vào tham số kiểu `Object` của `isInstance`, nhưng token `int.class` vẫn không đại diện cho kiểu runtime của đối tượng đó.

**Quy tắc chọn API:** `isPrimitive()` để nhận diện loại `Class`; `isAssignableFrom` để kiểm tra quan hệ kiểu; `isInstance` để kiểm tra một đối tượng thực tại runtime. Không API nào thay thế đầy đủ bộ quy tắc conversion của compiler.

## 5. Tra cứu phương thức bằng chữ ký chính xác; gọi phương thức có chuyển đổi đối số

Đây là điểm dễ nhầm nhất của Reflection: **tra cứu phương thức** và **gọi phương thức** là hai bước khác nhau.

Giả sử lớp có `void consume(int n)`:

- `getMethod("consume", int.class)` tìm đúng chữ ký và thành công; `getMethod("consume", Integer.class)` không tìm được chữ ký đó và ném `NoSuchMethodException`.
- Sau khi có `Method` tương ứng với `int`, `method.invoke(target, Integer.valueOf(5))` có thể chạy: đối số của `Method.invoke` đi qua `Object...` và khi gọi phương thức đích, reflection unbox `Integer` thành `int`.
- Nếu phương thức đích nhận `long`, một đối số `Integer` có thể được **unbox sang `int` rồi widening sang `long`**.
- Reflection không tự chọn overload từ đối tượng đối số: bạn phải tra cứu đúng `int.class`, `long.class` hoặc `Integer.class` theo phương thức muốn gọi.

Các quy tắc trên không biến `Integer` thành subtype của `int` hoặc `long`; đây là **invocation conversion tại thời điểm gọi**. Một `Long` truyền cho tham số `int` bị từ chối vì cần narrowing; `Integer` truyền cho tham số `Long` cũng bị từ chối vì không có conversion wrapper-sang-wrapper như thế. Với tham số primitive, `null` không unbox được và bị từ chối.

### Ví dụ Java 17+ chạy độc lập

Lưu đoạn sau vào `BoxingAndReflectionDemo.java`. Chương trình phân biệt rõ kiểm tra `Class`, tìm chữ ký, `Method.invoke`, và đọc/ghi field primitive.

```java
public class BoxingAndReflectionDemo {
    public static class Target {
        public int count = 3;

        public void takeInt(int x) {
            System.out.println("takeInt=" + x);
        }

        public void takeLong(long x) {
            System.out.println("takeLong=" + x);
        }

        public void takeInteger(Integer x) {
            System.out.println("takeInteger=" + x);
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("int.class.isPrimitive(): "
                + int.class.isPrimitive());
        System.out.println("Integer.class.isPrimitive(): "
                + Integer.class.isPrimitive());
        System.out.println("Integer.class.isAssignableFrom(int.class): "
                + Integer.class.isAssignableFrom(int.class));
        System.out.println("int.class.isAssignableFrom(Integer.class): "
                + int.class.isAssignableFrom(Integer.class));
        System.out.println("Number.class.isAssignableFrom(Integer.class): "
                + Number.class.isAssignableFrom(Integer.class));
        System.out.println("Object.class.isAssignableFrom(int.class): "
                + Object.class.isAssignableFrom(int.class));
        System.out.println("int.class.isInstance(Integer.valueOf(1)): "
                + int.class.isInstance(Integer.valueOf(1)));
        System.out.println("Integer.class.isInstance(Integer.valueOf(1)): "
                + Integer.class.isInstance(Integer.valueOf(1)));

        try {
            Target.class.getMethod("takeInt", Integer.class);
        } catch (NoSuchMethodException e) {
            System.out.println("getMethod(Integer.class): NOT_FOUND");
        }

        Target target = new Target();
        Target.class.getMethod("takeInt", int.class)
                .invoke(target, Integer.valueOf(5));
        Target.class.getMethod("takeLong", long.class)
                .invoke(target, Integer.valueOf(8));

        try {
            Target.class.getMethod("takeInt", int.class)
                    .invoke(target, Long.valueOf(5));
        } catch (IllegalArgumentException e) {
            System.out.println("Long -> int: REJECTED");
        }

        java.lang.reflect.Field field = Target.class.getField("count");
        Object read = field.get(target);
        System.out.println("field.get returns: "
                + read.getClass().getSimpleName() + "=" + read);

        field.set(target, Integer.valueOf(9));
        System.out.println("field int after set: " + target.count);
        try {
            field.set(target, null);
        } catch (IllegalArgumentException e) {
            System.out.println("null -> primitive: REJECTED");
        }
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac BoxingAndReflectionDemo.java
java BoxingAndReflectionDemo
```

Output:

```text
int.class.isPrimitive(): true
Integer.class.isPrimitive(): false
Integer.class.isAssignableFrom(int.class): false
int.class.isAssignableFrom(Integer.class): false
Number.class.isAssignableFrom(Integer.class): true
Object.class.isAssignableFrom(int.class): false
int.class.isInstance(Integer.valueOf(1)): false
Integer.class.isInstance(Integer.valueOf(1)): true
getMethod(Integer.class): NOT_FOUND
takeInt=5
takeLong=8
Long -> int: REJECTED
field.get returns: Integer=3
field int after set: 9
null -> primitive: REJECTED
```

Giải thích ba kết quả quan trọng:

1. `getMethod("takeInt", Integer.class)` thất bại vì khai báo dùng `int`; chữ ký Java không «tương đương» chỉ vì compiler có boxing.
2. `takeInt=5` và `takeLong=8` xảy ra vì API gọi reflection nhận giá trị wrapper rồi áp dụng **unboxing**, kết hợp **widening primitive** khi hợp lệ. Đây không phải kết quả của `isAssignableFrom`.
3. `Field.get(target)` trả về `Object`; khi đọc field `int count`, reflection tạo/đưa ra wrapper `Integer`. Ngược lại, `Field.set(target, Integer.valueOf(9))` unbox để ghi field `int`. `null` không thể gán cho primitive.

## 6. Bảng chuyển đổi đối số cần nhớ

Bảng này mô tả **đối số truyền tới tham số phương thức đích bằng `Method.invoke`**, không phải kết quả `Class.isAssignableFrom` và không phải quy tắc tra cứu `getMethod`.

| Kiểu đối tượng đối số thực tế | Tham số khai báo | Khi gọi bằng reflection | Lý do |
| --- | --- | --- | --- |
| `Integer` | `int` | Chấp nhận | Unboxing |
| `Integer` | `long` | Chấp nhận | Unboxing rồi widening primitive |
| `Byte` | `double` | Chấp nhận | Unboxing rồi widening primitive |
| `Long` | `int` | Từ chối | Không implicit narrowing primitive |
| `Integer` | `Long` | Từ chối | Không có conversion ngầm từ wrapper này sang wrapper kia |
| `Integer` | `Number` hoặc `Object` | Chấp nhận | Widening reference |
| `null` | `int` | Từ chối | Primitive không nhận `null` |
| `null` | `Integer` | Chấp nhận | Reference type nhận `null` |

Vì khai báo `Method.invoke(Object obj, Object... args)` nhận đối số qua `Object...`, nếu ở mã gọi bạn viết `invoke(target, 5)`, compiler Java trước hết boxing `5` thành `Integer` để đưa vào mảng `Object[]`. Reflection sau đó có thể unbox lại `Integer` cho tham số đích `int`. **Hai thời điểm chuyển đổi khác nhau**, không phải hai kiểu có quan hệ kế thừa.

Nếu truyền sai kiểu ở tầng reflection, thường nhận `IllegalArgumentException`. Nếu *mã bên trong phương thức đích* tự ném exception thì `Method.invoke` bọc nguyên nhân trong `InvocationTargetException`; đừng nhầm hai trường hợp khi chẩn đoán lỗi.

## 7. Field và mảng primitive cũng theo nguyên tắc tương tự

Với `Field`, kiểu khai báo được mô tả chính xác bằng `field.getType()`. Nếu field khai báo `int`, kết quả là `int.class`, **không** phải `Integer.class`:

```java
java.lang.reflect.Field field =
        BoxingAndReflectionDemo.Target.class.getField("count");
System.out.println(field.getType() == int.class); // true
```

`Field.get(Object)` luôn trả về `Object`, nên giá trị primitive được boxing trong kết quả. Khi ghi bằng `Field.set(Object, Object)`, API có thể unbox wrapper và thực hiện widening primitive được phép; `Field.setInt`, `Field.getInt` và các getter/setter chuyên biệt cho primitive là lựa chọn khi muốn biểu đạt loại dữ liệu rõ hơn.

Mảng `int[]` và `Integer[]` vẫn khác kiểu:

```java
System.out.println(int[].class.isAssignableFrom(Integer[].class)); // false
System.out.println(Integer[].class.isAssignableFrom(int[].class)); // false
```

Phản chiếu `java.lang.reflect.Array.get(intArray, index)` trả về `Object` nên phần tử `int` được boxing. Điều đó không làm `int[]` kế thừa `Integer[]` hoặc ngược lại.

## 8. Những lỗi thường gặp khi thiết kế code Reflection

**Lỗi 1 — Dùng `isAssignableFrom` để kiểm tra xem một đối số có thể truyền tới primitive hay không.** Ví dụ `long.class.isAssignableFrom(Integer.class)` là `false`, nhưng một `Integer` có thể được unbox rồi widen để truyền cho tham số `long` khi gọi bằng reflection. Muốn tự xây dựng bộ kiểm tra đối số, phải xét rõ `null`, reference subtype, wrapper tương ứng và bảng widening primitive thay vì chỉ dùng một phép `isAssignableFrom`.

**Lỗi 2 — Thay `int.class` bằng `Integer.class` khi tìm `Method`/`Constructor`.** Các API `getMethod`, `getDeclaredMethod` và tra cứu constructor dùng kiểu tham số khai báo cụ thể; sự tồn tại của boxing không đổi chữ ký. Ví dụ `void save(int id)` phải được tìm với `int.class`. Nếu có cả `save(int)` và `save(Integer)`, đó là **hai overload riêng biệt**.

**Lỗi 3 — Tưởng `getClass()` trên giá trị đã boxing cho biết kiểu primitive gốc.** `Object obj = 10;` chứa tham chiếu tới một `Integer`, nên `obj.getClass() == Integer.class`. Thông tin «ban đầu từng là `int`» không còn được giữ trong runtime class của đối tượng.

**Lỗi 4 — Gộp numeric widening với inheritance.** `int → long` là phép chuyển đổi primitive, không làm `int.class.isAssignableFrom(long.class)` hoặc chiều ngược lại thành `true`. Tương tự, widening `int → long` không tạo ra phép `Integer → Long`.

**Lỗi 5 — Bỏ qua `null` và exception.** Unboxing biến `null` thành lỗi; qua reflection, `null` tới primitive bị từ chối ở lớp gọi. Đừng tự động bắt mọi exception rồi coi như «không tương thích», vì lỗi do thân phương thức ném ra có ý nghĩa khác lỗi truyền đối số.

## 9. Quy trình xử lý khi viết thư viện Reflection

Khi nhìn thấy một giá trị và một kiểu đích, hãy phân biệt câu hỏi trước khi chọn API:

1. **Đang phân tích khai báo?** Dùng `getParameterTypes()`, `getReturnType()`, `Field.getType()` và lưu nguyên token primitive như `int.class`.
2. **Đang kiểm tra quan hệ kiểu thuần túy?** Dùng `isAssignableFrom` cho đúng ngữ nghĩa subtype/identity; đừng thêm boxing vào kết luận.
3. **Đang kiểm tra một instance hiện có?** Dùng `isInstance` cho reference object, xử lý `null` riêng.
4. **Đang truyền giá trị vào lời gọi?** Xác định primitive hay reference; với primitive, xét wrapper/unboxing và widening; với reference, xét `null` và quan hệ reference.
5. **Đang tra cứu overload?** Chỉ định đúng danh sách `Class<?>` theo chữ ký; `Method.invoke` không tự chọn overload thay bạn.
6. **Đang đọc kết quả hoặc field?** Nhớ rằng giao diện reflection trả về `Object` có thể boxing giá trị primitive; đừng kết luận ngược rằng kiểu khai báo là wrapper.

**Tự kiểm tra nhanh:** Nếu `Method m` biểu diễn `void f(long n)`, cả `m.getParameterTypes()[0] == long.class` và `long.class.isAssignableFrom(Integer.class) == false` đều đúng; song `m.invoke(instance, Integer.valueOf(3))` vẫn có thể gọi `f` thành công. Khả năng gọi đó đến từ *unboxing + widening primitive*, tuyệt đối không đến từ kế thừa.

## Ghi nhớ

- **Quan hệ kiểu:** `int` và `Integer` là hai kiểu khác nhau, không kế thừa lẫn nhau; `Integer` nằm trong cây lớp `Number → Object`.
- **Chuyển đổi giá trị:** boxing/unboxing và widening là conversion tại những ngữ cảnh được Java cho phép.
- **API Reflection:** `isAssignableFrom`/`isInstance` mô tả kiểu và đối tượng; `getMethod` yêu cầu chữ ký chính xác; `Method.invoke` và `Field.set` có quy tắc chuyển đổi đối số riêng.

Phân biệt ba tầng **type identity → method lookup → invocation conversion** sẽ giúp giải thích đúng mọi kết quả tưởng như «mâu thuẫn» giữa primitive và wrapper trong Java Reflection.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="type-relationships-008">Chọn kiểu khai báo hoặc kiểu thực tế khi khám phá thành viên</a>

<details>
<summary>Xem chi tiết</summary>

# Chọn kiểu khai báo hoặc kiểu thực tế khi khám phá thành viên bằng Java Reflection

Khi một biến được khai báo bằng lớp cha hoặc interface nhưng đang tham chiếu đến một đối tượng thuộc lớp con, Reflection có thể khám phá **hai tập thành viên khác nhau** tùy theo đối tượng `Class<?>` được chọn. Quyết định này ảnh hưởng đến phương thức, trường dữ liệu, kết quả tra cứu và tính ổn định của công cụ sử dụng Reflection.

## 1. Phân biệt ba khái niệm về kiểu

Xét:

```java
Animal animal = new Dog();
```

- **Kiểu khai báo (declared/static type)** của biến `animal` là `Animal`. Trình biên dịch kiểm tra những lời gọi thông thường như `animal.speak()` dựa trên hợp đồng của `Animal`. Kiểu này được biết từ khai báo trong mã nguồn, hoặc từ metadata của trường, tham số, kiểu trả về.
- **Kiểu thực tế (runtime type)** của đối tượng là `Dog`. Biểu thức `animal.getClass()` trả về đối tượng `Class` mô tả `Dog`, bất kể biến đang mang kiểu `Animal`.
- **Lớp khai báo thành viên (declaring class)** là nơi một `Method` hoặc `Field` thực sự được khai báo. Kiểm tra bằng `member.getDeclaringClass()`; lớp này có thể khác lớp `Class` dùng để tìm kiếm.

Hai cách bắt đầu Reflection vì vậy mang ý nghĩa khác nhau:

```java
Class<?> contractType = Animal.class;       // Khám phá từ kiểu khai báo
Class<?> runtimeType = animal.getClass();   // Khám phá từ kiểu thực tế: Dog
```

`Animal.class` là lựa chọn tường minh của lập trình viên; Java không tự suy ra kiểu khai báo của biến từ một đối tượng đã được truyền vào phương thức chỉ nhận `Object`. Còn `getClass()` luôn dựa trên đối tượng thực tế và sẽ ném `NullPointerException` nếu tham chiếu là `null`.

**Điểm mấu chốt:** chọn `Class<?>` nào quyết định **điểm xuất phát để khám phá metadata**. Điều đó không đồng nghĩa với việc chỉ định phương thức thực thi khi gọi một phương thức instance có thể override.

## 2. Chọn kiểu nào dẫn đến tập thành viên nào?

Giả sử `Parent` khai báo `parentOnly()`, còn `Child extends Parent` khai báo thêm `childOnly()` và override `describe()`.

| Cách lấy `Class<?>` | Ý nghĩa khi khám phá | Thành viên `childOnly()` |
| --- | --- | --- |
| `Parent.class` | Khám phá API xuất phát từ `Parent` | Không xuất hiện |
| `Child.class` | Khám phá API xuất phát từ `Child` | Có thể xuất hiện |
| `parentRef.getClass()`, khi `parentRef = new Child()` | Khám phá đúng lớp runtime `Child` | Có thể xuất hiện |
| `method.getDeclaringClass()` | Xác định nơi phương thức được khai báo, không tự khám phá lại | Phụ thuộc chính `method` |

Tuy nhiên, kết quả còn phụ thuộc **họ API Reflection** được gọi trên `Class<?>`:

| API | Khám phá gì? | Có bao gồm thành viên kế thừa? |
| --- | --- | --- |
| `getDeclaredMethods()` / `getDeclaredMethod(name, parameterTypes...)` | Phương thức khai báo trực tiếp ở kiểu đang xét, bất kể `public`, `protected`, package-private hoặc `private` | Không |
| `getMethods()` / `getMethod(name, parameterTypes...)` | Phương thức `public` của kiểu và phương thức `public` được kế thừa; có thể gồm phương thức từ interface và `Object` | Có, theo quy tắc kế thừa |
| `getDeclaredFields()` / `getDeclaredField(name)` | Trường khai báo trực tiếp ở kiểu đang xét, với mọi mức truy cập | Không |
| `getFields()` / `getField(name)` | Các trường `public` của kiểu và những trường `public` kế thừa theo quy tắc Java | Có |
| `getDeclaredConstructors()` / `getDeclaredConstructor(...)` | Constructor khai báo trên chính lớp, mọi mức truy cập | Không; constructor không được kế thừa |
| `getConstructors()` / `getConstructor(...)` | Constructor `public` của chính lớp | Không; constructor không được kế thừa |

Lưu ý rằng từ `declared` trong tên API nghĩa là “**được khai báo trực tiếp trên kiểu đang xét**”, **không** có nghĩa là “theo kiểu khai báo của biến”. Ví dụ, `object.getClass().getDeclaredMethods()` vẫn liệt kê các phương thức khai báo tại lớp runtime của `object`.

Các phương thức `getDeclared*` vẫn có thể trả về metadata của thành viên không public; **nhìn thấy metadata không đồng nghĩa có quyền truy cập hoặc gọi thành viên đó**. Việc sử dụng `setAccessible(true)` có thể bị giới hạn bởi cơ chế module (JPMS), quyền truy cập hoặc môi trường chạy.

## 3. Ví dụ Java 17+ chạy được: lớp cha, lớp con và lời gọi thực tế

Tạo tệp `ReflectionTypeChoiceDemo.java`:

```java
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectionTypeChoiceDemo {
    static class Parent {
        public String describe() {
            return "Parent";
        }

        public String parentOnly() {
            return "parent-only";
        }

        private String secret = "parent-secret";
    }

    static final class Child extends Parent {
        @Override
        public String describe() {
            return "Child";
        }

        public String childOnly() {
            return "child-only";
        }

        private int childCount = 3;
    }

    private static boolean declaresMethod(Class<?> type, String name) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean declaresField(Class<?> type, String name) {
        for (Field field : type.getDeclaredFields()) {
            if (field.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Parent reference = new Child();

        Class<?> declaredType = Parent.class;
        Class<?> runtimeType = reference.getClass();

        System.out.println("Declared type: " + declaredType.getSimpleName());
        System.out.println("Runtime type: " + runtimeType.getSimpleName());

        System.out.println("Parent declares childOnly: "
                + declaresMethod(declaredType, "childOnly"));
        System.out.println("Child declares parentOnly: "
                + declaresMethod(runtimeType, "parentOnly"));

        Method inherited = runtimeType.getMethod("parentOnly");
        System.out.println("parentOnly declared in: "
                + inherited.getDeclaringClass().getSimpleName());

        Method parentMethod = declaredType.getMethod("describe");
        System.out.println("Parent.describe invoked on Child: "
                + parentMethod.invoke(reference));

        Method childMethod = runtimeType.getMethod("childOnly");
        System.out.println("Child.childOnly invoked: "
                + childMethod.invoke(reference));

        System.out.println("Child declares childCount: "
                + declaresField(runtimeType, "childCount"));
        System.out.println("Child declares secret: "
                + declaresField(runtimeType, "secret"));
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac ReflectionTypeChoiceDemo.java
java ReflectionTypeChoiceDemo
```

Kết quả:

```text
Declared type: Parent
Runtime type: Child
Parent declares childOnly: false
Child declares parentOnly: false
parentOnly declared in: Parent
Parent.describe invoked on Child: Child
Child.childOnly invoked: child-only
Child declares childCount: true
Child declares secret: false
```

Giải thích các điểm dễ nhầm:

1. `declaredType` đại diện cho `Parent`; nó không có metadata của `childOnly()`. Trong mã Java thông thường, `reference.childOnly()` cũng không biên dịch được vì biến `reference` mang kiểu `Parent`.
2. `runtimeType.getDeclaredMethods()` chỉ có phương thức được **khai báo trực tiếp** tại `Child`, không chứa `parentOnly()`. Nhưng `runtimeType.getMethod("parentOnly")` tìm thấy phương thức `public` kế thừa, và `getDeclaringClass()` trả về `Parent`.
3. `Parent.class.getMethod("describe")` trả về metadata của `Parent.describe()`. Khi `invoke(reference)` trên đối tượng `Child`, cơ chế dispatch động của phương thức instance dẫn tới phiên bản override `Child.describe()`, nên in ra `Child`. Phương thức `static` hoặc `private` không tham gia override theo cách này.
4. `Child.class.getDeclaredField("childCount")` tồn tại; `Child.class.getDeclaredField("secret")` sẽ ném `NoSuchFieldException` vì `secret` được khai báo ở `Parent`. Ví dụ dùng vòng lặp để kiểm tra thay vì ném exception.

Hai hàm `declaresMethod` và `declaresField` trong ví dụ chỉ kiểm tra **tên**, phù hợp vì các tên đang xét không quá tải. Khi tra cứu một phương thức cụ thể trong ứng dụng thật, phải xét cả danh sách kiểu tham số, ví dụ `getMethod("find", String.class, int.class)`.

## 4. Phân biệt khám phá metadata và cách Java dispatch lời gọi

Có hai câu hỏi độc lập:

- **Có tìm ra `Method` hay không?** Phụ thuộc `Class<?>` bắt đầu tìm và việc dùng `getMethod` hay `getDeclaredMethod`.
- **Khi `Method.invoke(receiver, args)`, phần thân nào chạy?** Với phương thức instance có thể override, Java áp dụng dispatch động theo lớp thực tế của `receiver`; đối tượng nhận vẫn phải tương thích với phương thức được khai báo và phải thỏa điều kiện truy cập.

Do đó:

```java
Parent instance = new Child();
Method method = Parent.class.getMethod("describe");
Object value = method.invoke(instance); // "Child"
```

Không nên hiểu `Parent.class` là cách “ép chạy thân `Parent.describe()`” khi có override. Nếu yêu cầu thực sự là gọi implementation cụ thể của lớp cha, đó là bài toán cơ chế gọi phương thức khác, không giải được chỉ bằng việc đổi `Class<?>` cho `getMethod()`.

Ngược lại, Reflection trên lớp runtime có thể nhìn thấy method riêng của lớp con, dù biến trong Java chỉ là `Parent`. Điều này hữu ích cho chẩn đoán hoặc framework, nhưng có thể làm code phụ thuộc vào implementation không nằm trong hợp đồng public.

## 5. Khám phá trường kế thừa: đi qua cây lớp khi cần

Các trường (fields) **không có dispatch động như phương thức instance**. Khi lớp con che khuất tên trường ở lớp cha (field hiding), hai khai báo vẫn là hai `Field` riêng và `getDeclaringClass()` giúp phân biệt nguồn gốc của chúng.

Để biết những trường được khai báo **trên từng lớp** từ lớp runtime đến `Object`, duyệt cây lớp:

```java
Class<?> type = instance.getClass();

while (type != null) {
    for (java.lang.reflect.Field field : type.getDeclaredFields()) {
        System.out.println(type.getName() + " -> " + field.getName());
    }
    type = type.getSuperclass();
}
```

Đây là **đoạn minh họa** sử dụng biến `instance` đã tồn tại, không phải một chương trình độc lập. Kết quả thứ tự của `getDeclaredFields()` không được API bảo đảm; nếu cần thứ tự ổn định thì sắp xếp trước khi xử lý.

Vòng lặp `getSuperclass()` chỉ đi theo **chuỗi lớp cha**, không tự đi sâu vào các interface. Nếu cần khám phá interface, xét `getInterfaces()`, cây interface và quy tắc thành viên tương ứng. Không sử dụng vòng lặp này để mặc định bỏ qua kiểm tra quyền truy cập; liệt kê tên trường khác với đọc giá trị trường.

Khi đã có đối tượng `Field`, hãy xem `field.getDeclaringClass()` và `field.getType()` để biết lớp khai báo và kiểu khai báo của trường. `field.getType()` cũng không tự biến thành kiểu thực tế của giá trị đang nằm trong trường; nếu cần phân tích giá trị, phải đọc giá trị hợp lệ trước rồi mới cân nhắc `value.getClass()` khi `value != null`.

## 6. Khi nào nên chọn kiểu khai báo, khi nào chọn kiểu thực tế?

### Chọn kiểu khai báo hoặc interface khi làm việc theo hợp đồng

Thích hợp khi tạo bộ gọi API, kiểm tra annotation trên một hợp đồng đã xác định, xác minh phương thức mà người dùng được phép gọi, hoặc cần kết quả không phụ thuộc vào lớp implementation có thể thay đổi.

```java
Class<?> apiType = Runnable.class;
Method method = apiType.getMethod("run");
```

`Runnable.class` thể hiện đúng hợp đồng `Runnable`. Việc đối tượng runtime có thêm phương thức phụ không làm mở rộng danh sách API mà thành phần của bạn dự định hỗ trợ. Nhưng nếu nghiệp vụ cần annotation khai báo trên implementation, Reflection chỉ trên interface có thể **không đủ**: phải thiết kế chính sách tìm annotation cụ thể và kiểm tra vị trí khai báo phù hợp.

### Chọn kiểu thực tế khi cần biết đối tượng thực sự có gì

Thích hợp khi viết công cụ debug, serializer có chính sách rõ ràng, diagnostic inspector hoặc code dùng metadata riêng của implementation:

```java
Object value = obtainValue(); // Hàm ứng dụng cung cấp, chỉ là minh họa
if (value != null) {
    Class<?> runtimeType = value.getClass();
    for (Method method : runtimeType.getDeclaredMethods()) {
        System.out.println(method.getName());
    }
}
```

Đây cũng là **đoạn minh họa**, chưa phải chương trình chạy độc lập do `obtainValue()` là hàm do ứng dụng định nghĩa. `getDeclaredMethods()` ở đây chỉ thu phương thức khai báo trực tiếp của implementation. Nếu cần danh sách API `public` mà đối tượng có thể sử dụng, xem xét `getMethods()` thay cho `getDeclaredMethods()`.

**Trường hợp proxy/framework:** đối tượng runtime có thể là proxy hoặc lớp sinh tự động. `getClass()` khi đó trả về kiểu proxy, không nhất thiết là lớp nghiệp vụ bạn dự kiến. Với code dựa trên hợp đồng, ưu tiên `interface.class` hoặc `Class<?>` được chỉ định rõ; với việc bóc tách kiểu đích của proxy, sử dụng cơ chế mà framework thực tế cung cấp thay vì đoán lớp cha.

### Muốn tìm đúng thành viên đang được khai báo ở đâu

Dùng `getDeclaringClass()` sau khi tìm thấy `Method` hoặc `Field`. Một phương thức trả về từ `Child.class.getMethod("parentOnly")` có thể thuộc `Parent`; đây không phải lỗi hay một bản sao của phương thức trên `Child`.

### Muốn xử lý lớp con an toàn từ biến kiểu cha

Nếu thực sự cần một hành vi chỉ `Child` có, trước hết xác định đối tượng có đúng kiểu không:

```java
if (Child.class.isInstance(reference)) {
    Child child = Child.class.cast(reference);
    System.out.println(child.childOnly());
}
```

Đây là đoạn minh họa dùng các lớp và biến của ví dụ phía trên. `isInstance` cho biết đối tượng có tương thích với kiểu được kiểm tra hay không; `cast` thực hiện ép kiểu có kiểm tra, giúp tránh `ClassCastException`. Đừng chọn `getClass()` chỉ để lách thiết kế API khi có thể biểu diễn hợp đồng bằng interface hoặc lớp cha.

## 7. Lỗi phổ biến và cách kiểm tra

| Nhầm lẫn | Vì sao sai | Cách làm đúng |
| --- | --- | --- |
| `reference.getClass()` trả về kiểu khai báo của biến | Nó trả về kiểu runtime | Dùng `DeclaredType.class`, hoặc metadata của chính khai báo nếu đang phân tích source/field/parameter |
| `getDeclaredMethods()` lấy tất cả phương thức có thể gọi, kể cả kế thừa | Nó chỉ lấy khai báo trực tiếp trên `Class<?>` được chọn | Dùng `getMethods()` nếu cần phương thức public kế thừa; hoặc duyệt hierarchy theo chính sách |
| `getMethods()` cho thấy cả phương thức private | API này chỉ liệt kê phương thức public phù hợp | Với metadata khai báo riêng, dùng `getDeclaredMethods()` và xử lý quyền truy cập |
| `Child.class.getDeclaredField("baseField")` tìm được trường của cha | Trường khai báo tại lớp cha không nằm trong `getDeclaredFields()` của con | Tra tại `Parent.class` hoặc duyệt `getSuperclass()` |
| Phương thức lấy từ `Parent.class` luôn chạy phiên bản `Parent` | Override instance được dispatch động khi `invoke` | Phân biệt phương thức được tìm thấy với phần thân được thực thi |
| Chỉ lọc `Method.getName()` là đủ trong mọi trường hợp | Một tên có thể có nhiều overload; trình biên dịch có thể tạo bridge/synthetic method | Kèm kiểu tham số, kiểm tra `isBridge()`/`isSynthetic()` khi phù hợp |
| Tin vào thứ tự của `getDeclaredMethods()` hay `getDeclaredFields()` | API không cam kết thứ tự ổn định | Sắp xếp bằng tiêu chí minh bạch nếu thứ tự có ý nghĩa |
| Xem được một `private Field` nghĩa là đọc được giá trị | Metadata không tự cấp quyền truy cập | Tôn trọng truy cập Java và giới hạn module; chỉ truy cập khi có quyền và lý do hợp lệ |

Một quy trình lựa chọn ngắn cho công cụ Reflection:

1. Xác định mục tiêu: **kiểm tra hợp đồng** hay **khám phá implementation thực tế**.
2. Chọn nguồn `Class<?>`: `Contract.class` khi theo API, hoặc `obj.getClass()` khi theo đối tượng và `obj != null`.
3. Chọn phạm vi thành viên: `getDeclared*` để xem khai báo tại chính lớp; `get*` khi cần thành viên public, bao gồm các thành viên có thể kế thừa.
4. Kiểm tra `getDeclaringClass()` để không nhầm thành viên kế thừa với thành viên tự khai báo. Nếu phải khám phá private/protected ở cha, duyệt hierarchy có chủ đích.
5. Nếu chuẩn bị gọi bằng `invoke`, kiểm tra signature, receiver, quyền truy cập và lưu ý dispatch động; không suy luận execution chỉ từ đối tượng `Class<?>` dùng để tìm.

## 8. Tự kiểm tra

Với `Parent parent = new Child();` trong chương trình ở mục 3, thử tự trả lời:

- `parent.getClass() == Child.class` có đúng không? **Đúng**, vì đối tượng thực tế là `Child`.
- `Child.class.getDeclaredMethod("parentOnly")` có thành công không? **Không**, sẽ ném `NoSuchMethodException` do phương thức được khai báo ở `Parent`.
- `Child.class.getMethod("parentOnly").getDeclaringClass()` trả về gì? **`Parent.class`**, do đây là phương thức public được kế thừa.
- `Parent.class.getMethod("describe").invoke(parent)` cho kết quả gì? **`"Child"`**, do dispatch động chọn phần override ở lớp runtime.
- Nếu cần kiểm tra tất cả trường được khai báo trực tiếp trên từng lớp trong hệ phân cấp, nên dùng gì? **`getDeclaredFields()` trên từng `Class<?>`**, kết hợp duyệt `getSuperclass()` và chính sách xử lý interface nếu cần.

Kết luận cho bài này: **kiểu khai báo trả lời câu hỏi về hợp đồng; kiểu thực tế trả lời câu hỏi về đối tượng hiện có; lớp khai báo thành viên trả lời câu hỏi về nguồn gốc metadata**. Chọn đúng điểm xuất phát rồi mới quyết định `get*` hay `getDeclared*` sẽ giúp tránh bỏ sót thành viên kế thừa, nhầm method override và vô tình ràng buộc code vào chi tiết implementation.

</details>

- [Quay lại đầu trang](#back-to-top)
