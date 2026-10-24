<a id="back-to-top"></a>

# Đối tượng Class

## Menu
- [Lấy Class bằng class literal](#class-objects-001)
- [Lấy kiểu thực tế bằng Object.getClass](#class-objects-002)
- [Class.forName với tên nhị phân](#class-objects-003)
- [Class.forName(name, initialize, loader)](#class-objects-004)
- [Nạp lớp khác khởi tạo lớp như thế nào](#class-objects-005)
- [Class&lt;T&gt;, Class&lt;?&gt; và giới hạn kiểm tra kiểu](#class-objects-006)
- [Class đại diện cho primitive, void, mảng, interface, enum và record](#class-objects-007)
- [Vì sao int.class khác Integer.class](#class-objects-008)
- [Không tạo Class bằng constructor thông thường](#class-objects-009)
- [Phân biệt đối tượng Class với instance của kiểu được mô tả](#class-objects-010)

## <a id="class-objects-001">Lấy Class bằng class literal</a>

<details>
<summary>Xem chi tiết</summary>

# Lấy đối tượng `Class` bằng class literal trong Java

## 1. Class literal là gì?

Trong Java, **class literal** là biểu thức `TênKiểu.class`, dùng để lấy đối tượng `java.lang.Class` đại diện cho **một kiểu đã biết ngay trong mã nguồn**. Nó mô tả một lớp, interface, enum, annotation, kiểu nguyên thủy hoặc kiểu mảng. Toán tử `.class` không tạo instance của lớp.

```java
Class<String> stringClass = String.class;
Class<Integer> integerClass = Integer.class;
Class<?> primitiveClass = int.class;
Class<?> arrayClass = String[].class;
```

Với cùng một kiểu do cùng class loader định nghĩa, JVM sử dụng cùng một đối tượng `Class`. Class literal hữu ích khi cần truyền một kiểu đã biết cho API, thay vì tìm lớp từ chuỗi ký tự tại runtime.

- `new String("Java")` tạo **đối tượng dữ liệu** thuộc lớp `String`.
- `String.class` lấy **đối tượng mô tả kiểu** `String`; không tạo `String` mới.
- `text.getClass()` lấy **kiểu runtime của instance `text`**; cần `text` không phải `null`.

## 2. Cú pháp và loại kiểu có thể dùng

| Biểu thức | Kiểu được mô tả | Lưu ý |
| --- | --- | --- |
| `String.class` | Lớp `java.lang.String` | Gán cho `Class<String>`. |
| `Runnable.class` | Interface `Runnable` | Interface cũng có đối tượng `Class`. |
| `Override.class` | Annotation `Override` | Annotation là kiểu interface đặc biệt. |
| `Thread.State.class` | Enum `Thread.State` | Áp dụng cho kiểu enum và kiểu lồng. |
| `int.class`, `boolean.class` | Kiểu nguyên thủy | Khác wrapper `Integer.class`, `Boolean.class`. |
| `void.class` | Kiểu `void` | Dùng để biểu diễn kiểu trả về `void`. |
| `String[].class` | Mảng `String[]` | Khác kiểu phần tử `String.class`. |
| `int[][].class` | Mảng hai chiều `int[][]` | Mỗi số chiều là một kiểu mảng khác nhau. |
| `java.util.List.class` | Interface `List` | Không mang tham số generic `String`. |

**Không hợp lệ:** `List<String>.class`, vì đối số kiểu generic không tạo một `Class` riêng ở runtime. Dùng `List.class`; nếu cần mô tả tham số `String`, cần cơ chế `Type`/`ParameterizedType` tương ứng, không thể lấy từ `List.class` đơn thuần.

Cũng không thể dùng `myObject.class` để lấy kiểu của biến. Dùng `myObject.getClass()` đối với instance, hoặc `MyType.class` khi biết tên kiểu từ trước.

## 3. Phân biệt class literal với cách lấy `Class` khác

| Cách lấy `Class` | Kiểu được quyết định khi nào? | Cần instance? | Đặc điểm |
| --- | --- | --- | --- |
| `Animal.class` | Lúc biên dịch | Không | Luôn là `Animal`, đúng kiểu ghi trong source. |
| `animal.getClass()` | Lúc chạy | Có | Có thể là lớp con `Dog` nếu `animal` đang trỏ đến `Dog`. |
| `Class.forName("pkg.Animal")` | Lúc chạy, theo tên chuỗi | Không | Có thể ném `ClassNotFoundException`; bản một tham số mặc định yêu cầu khởi tạo lớp. |

Với `Animal animal = new Dog();`, `Animal.class` đại diện `Animal`, còn `animal.getClass()` đại diện `Dog`. Chúng đều là đối tượng `Class`, nhưng chỉ trường hợp thứ hai phụ thuộc instance thực tế.

Một điểm quan trọng: **chỉ tham chiếu `SomeType.class` không kích hoạt khởi tạo lớp**, nên không chạy `static initializer` chỉ vì lấy literal. Những hành động khác, như gọi phương thức `static`, có thể kích hoạt khởi tạo lớp. Phân biệt ba việc: lấy metadata kiểu, khởi tạo lớp và tạo instance.

## 4. Ví dụ Java 17+ biên dịch/chạy độc lập

Lưu mã sau thành `ClassLiteralDemo.java`:

```java
import java.util.List;

public class ClassLiteralDemo {
    static class Animal {}

    static class Dog extends Animal {}

    static class Lazy {
        static {
            System.out.println("Lazy đã khởi tạo");
        }

        static void ping() {}
    }

    public static void main(String[] args) {
        Animal animal = new Dog();

        Class<Animal> declaredType = Animal.class;
        Class<?> runtimeType = animal.getClass();

        System.out.println("declared=" + declaredType.getSimpleName());
        System.out.println("runtime=" + runtimeType.getSimpleName());
        System.out.println("isInstance=" + declaredType.isInstance(animal));
        System.out.println("assignable="
                + declaredType.isAssignableFrom(Dog.class));

        System.out.println("string=" + (String.class == "Java".getClass()));
        System.out.println("primitive=" + (int.class == Integer.TYPE));
        System.out.println("wrapper=" + (int.class == Integer.class));
        System.out.println("array=" + String[][].class.getTypeName());
        System.out.println("interface=" + List.class.getTypeName());

        Class<?> lazyType = Lazy.class;
        System.out.println("literal=" + lazyType.getSimpleName());
        Lazy.ping();
    }
}
```

Chạy từ thư mục chứa tệp:

```bash
javac ClassLiteralDemo.java
java ClassLiteralDemo
```

Stdout dự kiến:

```text
declared=Animal
runtime=Dog
isInstance=true
assignable=true
string=true
primitive=true
wrapper=false
array=java.lang.String[][]
interface=java.util.List
literal=Lazy
Lazy đã khởi tạo
```

Giải thích:

1. `declared=Animal` và `runtime=Dog` chứng minh `.class` chỉ rõ kiểu ở source, còn `getClass()` phản ánh lớp runtime của instance.
2. `isInstance=true`: đối tượng `Dog` là một `Animal`. `isAssignableFrom=true`: một `Dog` có thể gán cho biến kiểu `Animal`. Luôn chú ý hướng `Animal.class.isAssignableFrom(Dog.class)`.
3. `String.class == "Java".getClass()` là `true` vì cùng đại diện `String`.
4. `int.class == Integer.TYPE` là `true` vì `TYPE` đại diện primitive `int`. Nhưng `int.class == Integer.class` là `false`, bởi primitive khác wrapper.
5. `String[][].class.getTypeName()` mô tả kiểu mảng hai chiều; `List.class` mô tả interface `List`.
6. `Lazy.class` chưa chạy khối `static`. Dòng `Lazy đã khởi tạo` chỉ xuất hiện sau khi gọi `Lazy.ping()`.

## 5. Tính an toàn kiểu: `Class<T>` và `Class<?>`

`Class<T>` ghi nhận kiểu `T` ở mức khai báo, còn `Class<?>` thích hợp khi kiểu cụ thể chưa biết hoặc không cần ràng buộc.

```java
Class<String> exact = String.class;
Class<?> unknown = int[].class;
Class<? extends Number> numeric = Integer.class;

// Lỗi biên dịch vì kiểu không tương thích:
// Class<String> wrong = Integer.class;
```

Ví dụ `Class<String>` giúp compiler ngăn gán nhầm metadata của `Integer` cho biến muốn đại diện `String`.

**Bẫy primitive:** về generic, Java gán cho `int.class` kiểu `Class<Integer>` ở compile time, nhưng đối tượng đó **vẫn đại diện primitive `int`**. Vì vậy `int.class` và `Integer.class` không phải cùng `Class`. Có thể phân biệt bằng `int.class.isPrimitive()` (`true`) và `Integer.class.isPrimitive()` (`false`).

## 6. Ứng dụng khi dùng API reflection

**Truyền type token cho API.** Một hàm nhận `Class<T>` để biết rõ kiểu kết quả có thể được gọi bằng `String.class`, `User.class`, v.v. Compiler kiểm tra tên lớp, IDE hỗ trợ điều hướng và đổi tên.

Ví dụ dưới đây chỉ minh họa cách dùng `Class.cast` để kiểm tra/ép kiểu đối tượng đã phù hợp; đây **không phải** bộ chuyển đổi dữ liệu giữa hai kiểu bất kỳ:

```java
static <T> T asType(Object value, Class<T> targetType) {
    return targetType.cast(value);
}

// Đặt trong một phương thức khi sử dụng:
String result = asType("Java", String.class);
```

Nếu `value` không tương thích với `targetType`, `cast` ném `ClassCastException`. Ví dụ trên có minh họa từng đoạn, **không phải** một chương trình `main` độc lập.

**Kiểm tra quan hệ kiểu và metadata.** Sau khi có `Class`, bạn có thể gọi:

- `SomeType.class.isInstance(object)` để kiểm tra một instance thuộc kiểu hoặc kiểu con phù hợp.
- `Base.class.isAssignableFrom(Sub.class)` để kiểm tra kiểu `Sub` có thể gán cho biến kiểu `Base`.
- `SomeType.class.getDeclaredMethods()` để lấy các phương thức khai báo trực tiếp trong lớp, không mặc định gồm phương thức kế thừa.

Class literal cung cấp **đối tượng mô tả kiểu**; các API sử dụng nó để thực hiện tra cứu hay kiểm tra. Các kỹ thuật truy cập thành viên và thực thi reflection chi tiết thuộc các bài khác.

## 7. Lỗi hay gặp

- **Nhầm `.class` với `new`:** `Person.class` không gọi constructor, không tạo `Person` mới.
- **Nhầm kiểu khai báo với kiểu runtime:** `Base.class` không thay đổi theo instance được gán vào biến `Base`; `base.getClass()` thì có.
- **Nhầm primitive và wrapper:** `int.class != Integer.class` dù `Integer.TYPE == int.class`.
- **Dùng tham số generic trong literal:** `List<String>.class` không hợp lệ; `List.class` chỉ mô tả `List` chưa mang `<String>`.
- **Gọi `getClass()` trên `null`:** phát sinh `NullPointerException`; `SomeType.class` không cần instance.
- **Suy luận tên mảng từ `getName()`:** `String[].class.getName()` cho dạng JVM `[Ljava.lang.String;`, còn `getTypeName()` cho `java.lang.String[]`.
- **So sánh tên mà bỏ qua class loader:** danh tính class trong JVM phụ thuộc tên nhị phân **và class loader định nghĩa**. Hai loader khác nhau có thể định nghĩa hai class khác nhau cùng tên.
- **Tưởng class literal chạy khối `static`:** lấy `SomeType.class` không tự khởi tạo lớp.

## 8. Tự kiểm tra

**Câu 1.** Với `Object value = "abc";`, `Object.class` và `value.getClass()` có giống nhau không?

**Đáp án:** Không. Cái thứ nhất đại diện `Object`, cái thứ hai là `String`.

**Câu 2.** `int[].class == int.class` trả về `true` hay `false`?

**Đáp án:** `false`, vì mảng `int[]` và primitive `int` là hai kiểu khác nhau.

**Câu 3.** Vì sao không thể viết `List<String>.class`?

**Đáp án:** Class literal mô tả `Class` của một kiểu được biểu diễn trực tiếp tại runtime; tham số generic `<String>` không tạo lớp runtime riêng vì type erasure. Dùng `List.class` và cơ chế `Type` khi cần thông tin tham số hóa.

**Ghi nhớ:** Dùng `TênKiểu.class` khi **biết sẵn tên kiểu trong mã nguồn** và cần metadata của chính kiểu đó. Dùng `instance.getClass()` khi cần **kiểu runtime thực tế của đối tượng**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-objects-002">Lấy kiểu thực tế bằng Object.getClass</a>

<details>
<summary>Xem chi tiết</summary>

# Lấy kiểu thực tế của đối tượng bằng `Object.getClass()`

## 1. Mục tiêu

Sau bài học, bạn có thể:

- Gọi `getClass()` để xác định **lớp thực tế ở thời điểm chạy** của một đối tượng.
- Phân biệt lớp thực tế với kiểu khai báo của biến tham chiếu, đặc biệt khi dùng kế thừa và interface.
- Đọc một số thông tin cơ bản từ đối tượng `Class<?>` được trả về và so sánh chính xác các kiểu.
- Xử lý đúng trường hợp `null`, kiểu nguyên thủy, mảng, generic và các lớp được tạo động.

Phạm vi của bài là cách lấy **kiểu runtime từ một đối tượng đã có**. Các thao tác phản chiếu sâu như gọi constructor, truy cập field private hay gọi method bằng reflection là các chủ đề riêng.

## 2. `getClass()` là gì?

Mọi đối tượng Java đều kế thừa phương thức `public final Class<?> getClass()` từ `java.lang.Object`. Khi gọi `obj.getClass()`, JVM trả về đối tượng `java.lang.Class` đại diện cho **lớp thực tế đã tạo ra `obj`**, không đơn thuần là kiểu ghi trước tên biến.

Ví dụ:

```java
Object value = "Xin chào";
Class<?> actualType = value.getClass();

System.out.println(actualType == String.class); // true
```

- Kiểu khai báo của biến `value` là `Object`.
- Đối tượng mà `value` trỏ tới là một `String`.
- Kết quả `getClass()` là đối tượng `Class` của `java.lang.String`.

`Class<?>` không phải là chính đối tượng `value`, mà là đối tượng mô tả lớp của nó. Có thể dùng kết quả để hỏi tên lớp, lớp cha, interface, annotation và nhiều metadata khác. Phương thức `getClass()` là `final`, nên lớp con không thể override để giả mạo kết quả.

**Quy tắc cốt lõi:** với biến tham chiếu không `null`, `x.getClass()` mô tả lớp của **đối tượng hiện được tham chiếu**, bất kể biến `x` được khai báo rộng đến đâu.

## 3. Ví dụ Java 17+ có thể biên dịch và chạy

Tạo tệp `GetClassDemo.java`:

```java
import java.util.ArrayList;
import java.util.List;

public class GetClassDemo {
    static class Animal {
    }

    static final class Dog extends Animal {
    }

    public static void main(String[] args) {
        Animal pet = new Dog();
        Class<?> actualType = pet.getClass();

        System.out.println(actualType.getName());
        System.out.println(actualType.getSimpleName());
        System.out.println(actualType == Animal.class);
        System.out.println(actualType == Dog.class);
        System.out.println(actualType.getSuperclass() == Animal.class);

        List<String> names = new ArrayList<>();
        System.out.println(names.getClass().getName());
        System.out.println(names.getClass() == ArrayList.class);
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac GetClassDemo.java
java GetClassDemo
```

Kết quả chuẩn:

```text
GetClassDemo$Dog
Dog
false
true
true
java.util.ArrayList
true
```

Giải thích:

1. `Animal pet = new Dog()`: kiểu khai báo của `pet` là `Animal` nhưng đối tượng được tạo bằng `new Dog()`.
2. `pet.getClass()` trả về `Class` đại diện cho `Dog`, do đó so sánh với `Animal.class` cho `false` và với `Dog.class` cho `true`.
3. `getSuperclass()` cho biết `Class` của lớp cha trực tiếp, vì vậy `Dog` có superclass là `Animal`.
4. `getName()` in `GetClassDemo$Dog` vì `Dog` là lớp thành viên tĩnh nằm trong `GetClassDemo`; ký tự `$` thuộc tên nhị phân của lớp lồng nhau. `getSimpleName()` chỉ in `Dog`.
5. `names` được khai báo là `List<String>` nhưng được tạo bởi `new ArrayList<>()`; lớp runtime của nó là `java.util.ArrayList`.

Đoạn chương trình không phụ thuộc Spring hay thư viện bên ngoài; sử dụng JDK là đủ.

## 4. Kiểu khai báo và lớp runtime khác nhau như thế nào?

Xét hai khái niệm:

| Khái niệm | Câu hỏi trả lời | Ví dụ |
| --- | --- | --- |
| Kiểu khai báo (compile-time type) | Trình biên dịch cho phép gọi những thành viên nào qua biến này? | `Animal` của biến `pet` |
| Lớp thực tế (runtime class) | Đối tượng cụ thể được JVM tạo từ lớp nào? | `Dog` do `new Dog()` |
| Đối tượng `Class` | Metadata đại diện cho lớp runtime đó | `pet.getClass()` là `Dog.class` |

Một biến được gán lại có thể trỏ tới đối tượng thuộc lớp khác mà vẫn phù hợp với kiểu khai báo:

```java
Object item = "Java";
System.out.println(item.getClass() == String.class); // true

item = Integer.valueOf(42);
System.out.println(item.getClass() == Integer.class); // true
```

Hai biểu thức `item.getClass()` lần lượt trả về hai `Class` khác nhau vì đối tượng được tham chiếu đã thay đổi.

Điều này đặc biệt hữu ích khi nhận giá trị dưới kiểu cha như `Object`, một interface như `List`, hay một lớp cơ sở. Tuy nhiên, **không nên ép thiết kế hướng đối tượng thành chuỗi kiểm tra kiểu** nếu có thể dùng đa hình hoặc interface để thực hiện hành vi.

## 5. Làm việc với `Class<?>` được trả về

Các phương thức thông dụng:

| Biểu thức | Ý nghĩa | Ví dụ với đối tượng `new ArrayList<>()` |
| --- | --- | --- |
| `obj.getClass()` | Lấy đối tượng `Class` của lớp runtime | `ArrayList.class` |
| `obj.getClass().getName()` | Tên nhị phân, thường có package | `java.util.ArrayList` |
| `obj.getClass().getSimpleName()` | Tên ngắn của lớp | `ArrayList` |
| `obj.getClass().getCanonicalName()` | Tên chuẩn nếu kiểu có tên chuẩn | `java.util.ArrayList` |
| `obj.getClass().getSuperclass()` | `Class` của lớp cha trực tiếp, nếu có | `AbstractList.class` |
| `obj.getClass().getInterfaces()` | Danh sách interface được lớp khai báo triển khai trực tiếp | Phụ thuộc khai báo lớp |

Lưu ý về tên: `getSimpleName()` có thể trả chuỗi rỗng cho lớp vô danh; `getCanonicalName()` có thể trả `null` cho lớp vô danh hoặc lớp cục bộ. Không giả định các phương thức tên luôn cho kết quả có thể dùng làm định danh duy nhất. Tên của lớp được tạo động, chẳng hạn proxy, không phải API ổn định để ghi cứng.

Khi chưa cần ràng buộc kiểu cụ thể, khai báo biến như sau:

```java
Class<?> type = someObject.getClass();
```

Dấu `?` nghĩa là chưa biết chính xác kiểu được mô tả, giúp tránh ép kiểu `Class` không an toàn. Lớp runtime không phụ thuộc vào việc biến tham chiếu được khai báo với generic nào.

## 6. So sánh đúng lớp hay chấp nhận cả lớp con?

Có ba phép kiểm tra dễ bị nhầm:

| Cách kiểm tra | Ý nghĩa |
| --- | --- |
| `obj.getClass() == Dog.class` | Đúng **chính xác** lớp runtime là `Dog` |
| `obj instanceof Animal` | Đối tượng tương thích với `Animal`, kể cả lớp con |
| `Animal.class.isAssignableFrom(obj.getClass())` | Lớp runtime có thể gán cho biến kiểu `Animal` |

Ví dụ minh họa bên trong một phương thức có biến `pet` được khai báo `Animal pet = new Dog();`:

```java
System.out.println(pet.getClass() == Animal.class);                 // false
System.out.println(pet instanceof Animal);                         // true
System.out.println(Animal.class.isAssignableFrom(pet.getClass())); // true
```

Ở đây `Dog` **không phải chính xác** `Animal` nhưng **là một kiểu con** của `Animal`. Nếu nghiệp vụ cần hỗ trợ tất cả lớp con hoặc đối tượng thực thi interface, `instanceof` hoặc `Class.isInstance(...)` thường diễn đạt đúng ý hơn phép so sánh `getClass() == ...`.

Trong cùng ngữ cảnh nạp lớp, có thể so sánh các đối tượng `Class` bằng `==` để kiểm tra danh tính lớp. Đừng dùng riêng `getName().equals(...)` làm bằng chứng hai lớp là một: hai class loader khác nhau có thể nạp các lớp có cùng tên nhị phân nhưng không cùng danh tính runtime.

## 7. Các trường hợp cần lưu ý

### 7.1. Tham chiếu `null`

`getClass()` là phương thức của **đối tượng**. Khi biến bằng `null` thì không có đối tượng để lấy lớp:

```java
Object value = null;
// value.getClass(); // Ném NullPointerException tại runtime.

if (value != null) {
    System.out.println(value.getClass().getName());
} else {
    System.out.println("Không có đối tượng");
}
```

Đoạn trên là một **mảnh code đặt trong phương thức**, và nhánh `else` in `Không có đối tượng`. Đừng gọi `value.getClass()` trước khi kiểm tra `null` nếu đầu vào có thể vắng mặt. Dùng `instanceof` với `null` sẽ cho `false` thay vì ném `NullPointerException`.

### 7.2. Kiểu nguyên thủy, boxing và mảng

Các giá trị nguyên thủy như `int`, `boolean`, `double` không phải đối tượng nên không gọi được `getClass()` trực tiếp. Có thể dùng literal kiểu (`int.class`) hoặc boxing thành đối tượng:

```java
int count = 3;
// count.getClass(); // Lỗi biên dịch: int không phải reference type.

Object boxed = count; // Autoboxing sang Integer.
System.out.println(boxed.getClass() == Integer.class); // true
System.out.println(int.class.getName());                // int

Object numbers = new int[] {1, 2, 3};
System.out.println(numbers.getClass().isArray());                    // true
System.out.println(numbers.getClass().getComponentType() == int.class); // true
```

Mảng trong Java **là đối tượng**, kể cả mảng phần tử nguyên thủy. `numbers.getClass()` biểu diễn kiểu mảng `int[]`; `getComponentType()` trả về `int.class`. Đối với mảng, `getName()` sử dụng tên nhị phân đặc biệt, chẳng hạn `[I` cho `int[]`; khi cần trình bày dễ đọc, cân nhắc `getTypeName()`.

### 7.3. Generic và type erasure

`getClass()` mô tả lớp runtime, **không tái tạo type argument** của từng biến generic:

```java
List<String> words = new ArrayList<>();
List<Integer> counts = new ArrayList<>();

System.out.println(words.getClass() == counts.getClass()); // true
```

Cả hai đối tượng là `ArrayList`. Không thể dùng `words.getClass()` để kết luận danh sách mang `String` hay `Integer`. Java có thể lưu một số thông tin generic ở **khai báo** (ví dụ signature của field hoặc superclass) để reflection đọc bằng các API riêng, nhưng đó không phải thông tin về type argument của hai đối tượng `ArrayList` này.

### 7.4. Proxy và các lớp được tạo động

Trong framework hoặc môi trường có proxy, `getClass()` có thể trả về **lớp proxy được tạo tại runtime** thay vì lớp nghiệp vụ mà bạn mong đợi. Đừng so sánh cứng tên lớp được tạo động hoặc cho rằng `getClass()` luôn trùng với kiểu interface của đối tượng. Nếu mục tiêu là kiểm tra đối tượng đáp ứng hợp đồng nào, dùng `instanceof` hoặc `Class.isInstance(...)` sẽ phù hợp hơn.

## 8. Dùng khi nào trong thực tế?

Các tình huống hợp lý gồm ghi nhận loại đối tượng phục vụ chẩn đoán, phân loại chính xác các loại giá trị đã biết, truy xuất metadata phục vụ reflection hoặc kiểm tra nguyên nhân vì sao một biến kiểu interface đang chứa một implementation cụ thể.

Ví dụ đoạn code sau nằm **bên trong một phương thức**:

```java
Object payload = "hello";
Class<?> type = payload.getClass();

if (type == String.class) {
    System.out.println("Đúng lớp String");
}
System.out.println("Runtime type: " + type.getName());
```

Output:

```text
Đúng lớp String
Runtime type: java.lang.String
```

Trong logic nghiệp vụ thông thường, ưu tiên đa hình, interface hoặc pattern matching `instanceof` thay vì dùng `getClass()` và downcast liên tục. Sử dụng phép so sánh `==` với `Class` khi yêu cầu thật sự là **khớp đúng một implementation**.

## 9. Lỗi thường gặp và cách tránh

1. **Nhầm kiểu khai báo với lớp runtime:** `Animal pet = new Dog()` không làm `pet.getClass()` trở thành `Animal.class`.
2. **Gọi trên `null`:** kiểm tra tham chiếu trước khi gọi, nếu `null` là trường hợp hợp lệ.
3. **Dùng so sánh chính xác trong bài toán cần đa hình:** `getClass() == Base.class` loại bỏ tất cả subclass; cân nhắc `instanceof`.
4. **Cho rằng runtime class chứa type argument generic:** `new ArrayList<String>()` và `new ArrayList<Integer>()` có cùng lớp runtime.
5. **Gọi `getClass()` trên primitive:** dùng `int.class` hoặc boxing khi thật sự cần `Class` của wrapper.
6. **Dựa vào tên class để quyết định danh tính:** tên có thể chứa `$`, ký hiệu mảng hoặc tên lớp được framework sinh ra; danh tính lớp còn phụ thuộc class loader.
7. **Lấy tên hiển thị làm khóa dữ liệu bền vững:** `getSimpleName()` không đủ duy nhất và có thể rỗng với lớp vô danh.

## 10. Tự kiểm tra

- Với `Number number = Integer.valueOf(7);`, `number.getClass() == Number.class` là `false`; `number.getClass() == Integer.class` là `true`.
- Với `Object x = new String[2];`, `x.getClass().isArray()` là `true` và `x.getClass().getComponentType() == String.class` là `true`.
- Với `Object x = null;`, biểu thức `x.getClass()` ném `NullPointerException`; không có “lớp của null”.
- Với hai biến `List<String>` và `List<Integer>` đều trỏ tới `new ArrayList<>()`, `getClass()` trả cùng đối tượng `Class` khi chúng được nạp từ cùng lớp runtime.

**Điểm cần nhớ:** `getClass()` trả về `Class` của **đối tượng thực sự đang tồn tại**, còn kiểu khai báo của biến quyết định khả năng kiểm tra kiểu lúc biên dịch. Hãy chọn so sánh chính xác `Class` hay kiểm tra quan hệ kiểu tùy yêu cầu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-objects-003">Class.forName với tên nhị phân</a>

<details>
<summary>Xem chi tiết</summary>

# Class.forName với tên nhị phân trong Java Reflection

## 1. Mục tiêu và ngữ cảnh

Trong Java Reflection, **`Class.forName(String name)`** tìm một lớp hoặc interface theo tên tại thời điểm chạy và trả về đối tượng `Class<?>` biểu diễn kiểu đó. Điểm quan trọng là chuỗi truyền vào phải theo **tên nhị phân (binary name)** của lớp/interface thông thường; tên bạn viết trong mã Java đôi khi khác tên này. Riêng mảng có cú pháp biểu diễn đặc biệt, còn primitive không tra cứu bằng `Class.forName("int")`.

Sau bài học, bạn có thể phân biệt tên đơn giản, canonical name và binary name; tra cứu chính xác top-level class, member class và array; giải thích quá trình nạp/khởi tạo; chọn overload/class loader phù hợp; và xử lý những lỗi tra cứu điển hình.

## 2. Binary name khác canonical name như thế nào?

**Binary name** là tên dùng để định danh lớp/interface trong biểu diễn nhị phân của Java, được quy định trong JLS §13.1. Với một lớp top-level, tên thường là tên package cộng tên lớp, ví dụ `java.lang.String`. Với một **lớp hoặc interface thành viên** nằm bên trong kiểu khác, binary name dùng dấu **`$`** giữa các cấp lớp, ví dụ `java.util.Map$Entry`, trong khi source Java viết `java.util.Map.Entry`.

| Kiểu | Tên trong Java source / canonical name | Tên phù hợp với `Class.forName` |
| --- | --- | --- |
| Top-level class | `java.lang.String` | `java.lang.String` |
| Top-level interface | `java.util.List` | `java.util.List` |
| Member interface | `java.util.Map.Entry` | `java.util.Map$Entry` |
| Nested member class | `com.example.Outer.Inner` | `com.example.Outer$Inner` |
| Lồng nhiều cấp | `com.example.Outer.Middle.Inner` | `com.example.Outer$Middle$Inner` |
| Mảng `String[]` | `java.lang.String[]` | `[Ljava.lang.String;` |
| Mảng `String[][]` | `java.lang.String[][]` | `[[Ljava.lang.String;` |
| Mảng `int[]` | `int[]` | `[I` |
| Primitive `int` | `int` | Dùng `int.class`, không dùng `Class.forName("int")` |

Ba API lấy tên từ đối tượng `Class`:

- `getSimpleName()`: tên ngắn, chẳng hạn `Inner`; không mang đủ thông tin package/outer class.
- `getCanonicalName()`: tên theo cú pháp source, chẳng hạn `com.example.Outer.Inner`. Local class và anonymous class thường không có canonical name, nên có thể trả về `null`.
- `getName()`: với class/interface thường trả về binary name, chẳng hạn `com.example.Outer$Inner`; với mảng trả về tên dạng đặc biệt như `[Ljava.lang.String;`; với primitive trả về `int`, `boolean`...

**Quy tắc thực hành:** Khi đã có `SomeType.class`, lấy `SomeType.class.getName()` để tạo chuỗi cho các kiểu class/interface và array, thay vì tự biến đổi dấu chấm thành `$`. Ngoại lệ là primitive hoặc `void`, vì chuỗi `getName()` của chúng không dùng để nạp lại qua `Class.forName`.

## 3. Cú pháp chính xác theo loại kiểu

### 3.1. Lớp/interface top-level

```java
Class<?> stringType = Class.forName("java.lang.String");
Class<?> listType = Class.forName("java.util.List");
```

Chuỗi là tên đầy đủ có package. Khác với compiler xử lý tên `String` trong mã nguồn, `Class.forName("String")` không tự thêm package `java.lang`. Nó thử tìm lớp đúng tên đã truyền.

### 3.2. Member class và member interface

```java
Class<?> entry = Class.forName("java.util.Map$Entry");
Class<?> nested = Class.forName("com.example.Outer$Inner");
```

Dòng thứ hai chỉ thành công khi class `com.example.Outer$Inner` thực sự tồn tại và thấy được bởi loader. Ví dụ này minh họa cú pháp, không giả định project có class đó. Trong `com.example.Outer$Inner`, dấu chấm phân cách package và kiểu top-level; dấu `$` phân cách outer/inner type. Không thay tất cả dấu chấm thành `$`.

Quy tắc đặt tên trên cũng áp dụng cho **non-static inner class**. Tuy vậy, tra cứu được `Class` không đồng nghĩa có thể tạo instance inner class bằng constructor không tham số: constructor của inner class thường cần tham chiếu ngầm đến đối tượng outer.

### 3.3. Kiểu mảng sử dụng tên dạng descriptor

Với mảng, chuỗi `Class.forName` chấp nhận gồm một ký tự `[` cho mỗi chiều và mã của kiểu thành phần:

- Với thành phần là class/interface: `L` + binary name + `;`, ví dụ `[Ljava.lang.String;`.
- Với primitive: dùng mã `Z` = boolean, `B` = byte, `C` = char, `D` = double, `F` = float, `I` = int, `J` = long, `S` = short.
- Với mảng hai chiều, thêm một dấu `[`: `[[I` cho `int[][]`, `[[Ljava.lang.String;` cho `String[][]`.

```java
Class<?> strings = Class.forName("[Ljava.lang.String;");
Class<?> ints = Class.forName("[I");
Class<?> matrix = Class.forName("[[I");
Class<?> entries = Class.forName("[Ljava.util.Map$Entry;");
```

Không dùng `"java.lang.String[]"` hoặc `"int[]"` với overload nhận tên. Đây là **dạng tên mảng**, không phải tên nhị phân của top-level class; cách lấy tên chắc chắn nhất là `String[].class.getName()` hay `int[].class.getName()`. Nạp lớp mảng không tự khởi tạo lớp thành phần của mảng.

### 3.4. Primitive, generic, local và anonymous class

`Class.forName("int")`, `Class.forName("void")` không phải cách lấy đối tượng `Class` của primitive/void; dùng `int.class`, `void.class`, `Integer.TYPE`. Cũng không truyền cú pháp generic `"java.util.List<java.lang.String>"`: tham số kiểu generic không nằm trong binary name của raw class `java.util.List`.

Tên local class hoặc anonymous class có thể chứa số thứ tự do compiler sinh, chẳng hạn `Outer$1Local` và `Outer$1`. Các tên này có thể thay đổi khi sửa/biên dịch lại code. Đừng lưu cứng chúng vào cấu hình; nếu cần reflection với loại class này, ưu tiên `instance.getClass()` và sử dụng `Class` hiện có.

## 4. Chương trình Java 17+ minh họa binary name

Tạo tệp **`BinaryNameDemo.java`** với nội dung đầy đủ:

```java
public class BinaryNameDemo {
    static class Nested {}

    private static void show(String label, Class<?> type) {
        System.out.println(label + ": " + type.getName());
    }

    public static void main(String[] args) throws ClassNotFoundException {
        show("Top-level", Class.forName("java.lang.String"));
        show("Member", Class.forName("java.util.Map$Entry"));
        show("Nested", Class.forName("BinaryNameDemo$Nested"));
        show("Object array", Class.forName("[Ljava.lang.String;"));
        show("Primitive array", Class.forName("[I"));

        System.out.println("Canonical: " + Nested.class.getCanonicalName());

        try {
            Class.forName("BinaryNameDemo.Nested");
        } catch (ClassNotFoundException exception) {
            System.out.println("Canonical lookup: ClassNotFoundException");
        }

        try {
            Class.forName("int");
        } catch (ClassNotFoundException exception) {
            System.out.println("Primitive lookup: ClassNotFoundException");
        }
    }
}
```

Tại thư mục chứa tệp, chạy:

```bash
javac BinaryNameDemo.java
java BinaryNameDemo
```

**Output:**

```text
Top-level: java.lang.String
Member: java.util.Map$Entry
Nested: BinaryNameDemo$Nested
Object array: [Ljava.lang.String;
Primitive array: [I
Canonical: BinaryNameDemo.Nested
Canonical lookup: ClassNotFoundException
Primitive lookup: ClassNotFoundException
```

Ở đây `BinaryNameDemo.Nested` là canonical name có ích trong source Java, còn `BinaryNameDemo$Nested` là binary name để tìm lớp. Vì chương trình không khai báo package, phần đầu binary name chỉ là `BinaryNameDemo`. Nếu file nằm trong `package demo;`, chuỗi tra cứu sẽ là `demo.BinaryNameDemo$Nested`.

Ví dụ cũng minh họa `Class.forName` **không tự biến** tên array trong mã nguồn `String[]` thành dạng JVM và **không tự biến** chuỗi `int` thành primitive `Class`.

## 5. Nạp kiểu bằng tên và thời điểm khởi tạo

`Class.forName(String)` tìm theo tên và **yêu cầu khởi tạo** lớp đích khi cần. `Class.forName(name, false, loader)` vẫn nạp/liên kết lớp nhưng **không chủ động khởi tạo** nó; `ClassLoader.loadClass(name)` thông thường cũng chưa yêu cầu khởi tạo. Đây là các thao tác khác với gọi constructor để tạo instance.

| Lời gọi | Kết quả cần lưu ý |
| --- | --- |
| `Class.forName("demo.Widget")` | Tìm theo tên và yêu cầu chạy static initialization của `Widget` nếu chưa diễn ra. |
| `Class.forName("demo.Widget", false, loader)` | Lấy metadata để khám phá kiểu trước khi chủ động sử dụng. |
| `Class.forName("[Ldemo.Widget;")` | Tìm kiểu mảng; không vì thế mà tự khởi tạo lớp phần tử `Widget`. |

Tên đúng vẫn có thể không tìm được nếu loader không thấy định nghĩa. Lớp tìm thấy cũng có thể lỗi khi liên kết hoặc khởi tạo. Bài về overload ba đối số đi sâu vào việc chọn loader và cờ `initialize`; bài về vòng đời lớp phân tích riêng các thời điểm JVM chạy `static`.

## 6. Tìm được `Class` chưa phải đã có đối tượng

`Class.forName` chỉ trả về metadata của kiểu, không tự tạo instance. Khi muốn khởi tạo đối tượng bằng reflection, cần tìm constructor phù hợp:

```java
Class<?> type = Class.forName("java.util.ArrayList");
Object instance = type.getDeclaredConstructor().newInstance();
System.out.println(instance.getClass().getName());
```

Đây là **fragment** đặt trong phương thức đã xử lý/khai báo `ReflectiveOperationException`; output là `java.util.ArrayList`. `ArrayList` có constructor không tham số; không phải lớp nào cũng có. Một constructor có thể không public hoặc bị hạn chế truy cập bởi module system; `getDeclaredConstructor().newInstance()` không bảo đảm thành công cho mọi kiểu vừa nạp.

Với kiểu biết sẵn lúc biên dịch, `SomeType.class` thường ngắn gọn và an toàn kiểu hơn; `Class.forName` phù hợp khi tên kiểu đến từ cấu hình hoặc cơ chế plugin, framework hay lập trình động.

## 7. Chẩn đoán lỗi

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| `ClassNotFoundException` với `java.util.Map.Entry` | Nhầm canonical name với binary name | Dùng `java.util.Map$Entry`. |
| `ClassNotFoundException` với `String` | Thiếu tên package | Dùng `java.lang.String`. |
| `ClassNotFoundException` với `java.lang.String[]` | Nhầm cách viết array trong source | Dùng `[Ljava.lang.String;` hoặc `String[].class`. |
| `ClassNotFoundException` với `int` | Primitive không được loader tìm như class | Dùng `int.class`. |
| Tên đúng vẫn không tìm thấy | Sai classpath, module path hoặc class loader | Kiểm tra dependency runtime và loader. |
| `ExceptionInInitializerError` | Static initialization thất bại | Xem nguyên nhân gốc trong static initializer. |
| `NoClassDefFoundError` | Lớp/dependency cần khi liên kết hoặc sử dụng bị thiếu, hoặc lớp từng khởi tạo thất bại | Xem lỗi đầu tiên trong log và chuỗi nguyên nhân. |
| `LinkageError` khác | Lỗi xác minh/liên kết hoặc bytecode không tương thích | Kiểm tra dependency và phiên bản thư viện. |

`ClassNotFoundException` là **checked exception**, thường liên quan tới tra cứu lớp theo tên. `ExceptionInInitializerError`, `NoClassDefFoundError` là các **Error**, không được xử lý chỉ bằng `catch (ClassNotFoundException)`. Tránh bắt `Error` một cách đại trà để che lỗi triển khai/môi trường.

## 8. Áp dụng an toàn với tên lớp động

Khi đọc tên class từ cấu hình, hãy lưu binary name chính xác nếu có thể, ví dụ dùng `Plugin.class.getName()` ở nơi tạo dữ liệu. Không tự chuyển toàn bộ `.` thành `$`, vì dấu chấm trong package phải giữ nguyên. Nếu cần class loader riêng (plugin hoặc application server), dùng overload ba tham số và giữ đúng loader của plugin.

Việc nạp và khởi tạo lớp có thể thực thi mã trong static initializer. Nếu tên đến từ dữ liệu bên ngoài, cần giới hạn lớp cho phép, kiểm tra kiểu cha/interface dự kiến và chỉ tạo instance theo hợp đồng rõ ràng. Chẳng hạn, sau khi xác định `binaryName` đã được phép:

```java
Class<?> loaded = Class.forName(binaryName, false, pluginClassLoader);
if (!Runnable.class.isAssignableFrom(loaded)) {
    throw new IllegalArgumentException("Expected Runnable");
}
Class<? extends Runnable> taskType = loaded.asSubclass(Runnable.class);
```

Đây là **fragment** dùng hai biến có sẵn `String binaryName` và `ClassLoader pluginClassLoader`; nó mới kiểm tra subtype, chưa tạo instance. Dù đã dùng `initialize=false`, việc thao tác tiếp theo với class có thể làm JVM khởi tạo nó. Chỉ kiểm tra subtype không thay thế danh sách class được phép và đánh giá mức độ tin cậy của nguồn.

## 9. Câu hỏi tự kiểm tra

1. Source có `package demo;` và `Outer` chứa `static class Inner`. Chuỗi nào để tra cứu inner type? **`"demo.Outer$Inner"`**, không phải `"demo.Outer.Inner"`.
2. Để tìm `String[][]`, dùng tên nào? **`"[[Ljava.lang.String;"`**: hai dấu `[` cho hai chiều, `Ljava.lang.String;` cho thành phần reference type.
3. Vì sao `Class.forName("int")` không thay `int.class`? **Primitive không phải lớp được loader định nghĩa theo binary name**.
4. Muốn lấy `Class` mà chưa yêu cầu chạy static initializer, dùng gì? **`Class.forName(binaryName, false, loader)`**, đồng thời hiểu các thao tác về sau có thể khởi tạo lớp.
5. Tại sao tên đúng vẫn tìm thất bại? **Class loader hoặc classpath/module path runtime không chứa/không nhìn thấy class phù hợp**.

**Ghi nhớ:** Đối với lớp/interface thông thường, dùng **binary name**; với member type sử dụng dấu `$`. Đối với mảng, dùng tên kiểu `[L...;` hoặc `[I`. Đối với primitive, dùng class literal. Luôn phân biệt tra cứu, khởi tạo và tạo instance để hiểu đúng hành vi của `Class.forName`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-objects-004">Class.forName(name, initialize, loader)</a>

<details>
<summary>Xem chi tiết</summary>

# Class.forName(name, initialize, loader) trong Java Reflection

## 1. Mục tiêu và ngữ cảnh

`java.lang.Class` là đối tượng đại diện cho một kiểu tại thời điểm chạy: lớp, interface, enum, record, annotation, kiểu mảng hoặc kiểu nguyên thủy. Khi đã có `Class<?>`, ta có thể đọc metadata qua Reflection như constructor, method, field và annotation.

`Class.forName(String name, boolean initialize, ClassLoader loader)` giải quyết tình huống **chỉ biết tên kiểu dưới dạng chuỗi tại runtime** và cần lấy đối tượng `Class` bằng một class loader được chọn. Điểm quan trọng nhất của overload này là tách biệt hai lựa chọn: **dùng class loader nào** và **có yêu cầu khởi tạo lớp ngay không**.

Ví dụ các hệ thống cấu hình plugin, framework, driver hoặc bộ xử lý nhiều implementation chỉ nhận tên kiểu như `"com.example.payment.CardProcessor"` trong cấu hình. `Class.forName(...)` giúp tra cứu kiểu mà không viết cứng `CardProcessor.class` vào mã nguồn. Tuy nhiên, việc tìm ra lớp chưa đồng nghĩa với tạo object hay gọi bất kỳ method nào.

## 2. Cú pháp và ý nghĩa từng đối số

```java
public static Class<?> forName(
        String name,
        boolean initialize,
        ClassLoader loader
) throws ClassNotFoundException;
```

| Đối số | Ý nghĩa | Điều dễ nhầm |
|---|---|---|
| `name` | **Tên nhị phân (binary name)** của lớp/interface cần tìm; với kiểu mảng dùng tên dạng descriptor như `[Ljava.lang.String;`. | Không phải đường dẫn file, không có `.class`, không phải tên generic trong mã nguồn. Nested class thường dùng dấu `$`. |
| `initialize` | `true` yêu cầu khởi tạo lớp nếu chưa khởi tạo; `false` yêu cầu nạp và liên kết mà **chưa khởi tạo lớp đích**. | `false` không có nghĩa là chỉ tra cứu một chuỗi không có hoạt động nạp lớp, cũng không bảo đảm custom loader không có side effect. |
| `loader` | Class loader dùng để tìm định nghĩa kiểu, theo chính sách ủy quyền/nạp lớp của loader đó. | `null` nghĩa là dùng **bootstrap class loader**, không có nghĩa "hãy tự chọn loader mặc định". |

**Giá trị trả về:** một `Class<?>` ứng với kiểu mà loader đã xác định. Không có instance của kiểu đó được tạo ra. Nếu không tìm thấy lớp với loader đã chọn, phương thức ném `ClassNotFoundException`.

Khác biệt giữa các overload:

- `Class.forName("com.example.Foo")`: tương đương về mục đích với việc nạp và **khởi tạo** lớp bằng class loader của lớp gọi phương thức; không cho chọn trực tiếp `loader` hoặc trì hoãn khởi tạo.
- `Class.forName("com.example.Foo", false, loader)`: cho chọn loader và trì hoãn khởi tạo.
- `Class.forName(module, "com.example.Foo")` (Java 9+): tìm lớp trong ngữ cảnh `Module`, **không khởi tạo** và có thể trả `null` khi không tìm thấy trong module đã chỉ định; không có đối số boolean nên không thay thế trực tiếp overload ba đối số.
- `loader.loadClass("com.example.Foo")`: thao tác thông thường của `ClassLoader` là tải lớp mà chưa chủ động khởi tạo; hành vi nạp và ủy quyền cụ thể tùy loader.

## 3. `initialize` tác động đến giai đoạn nào?

JVM thực hiện **loading** để tìm định nghĩa kiểu, **linking** để xác minh/chuẩn bị và có thể trì hoãn một số bước resolution, sau đó **initialization** mới chạy các biểu thức khởi tạo trường `static` và khối `static` theo quy tắc JVM. Việc tạo instance và chạy constructor là bước sử dụng kiểu riêng biệt.

- `initialize=false`: vẫn yêu cầu tìm, nạp và liên kết, nhưng lời gọi này không chủ động thực thi static initializer của lớp đích.
- `initialize=true`: yêu cầu khởi tạo lớp đích nếu trước đó chưa được khởi tạo; initializer đã thành công không chạy lại vì gọi `forName` lần nữa.
- Nếu lớp đã khởi tạo, `false` không đảo ngược trạng thái. Nếu sau đó gọi static method, đọc trường static không phải constant variable hoặc tạo instance, JVM có thể khởi tạo lớp.

Custom `ClassLoader` vẫn có thể chạy logic riêng trong quá trình nạp; không nên hiểu `false` là thao tác tuyệt đối không có side effect. Trình tự initialization chi tiết, constant variables và thao tác Reflection gây khởi tạo được phân tích trong bài vòng đời lớp.

## 4. Ví dụ Java 17+ chạy độc lập: thấy rõ cờ initialize

Tạo file `ForNameDemo.java`:

```java
public class ForNameDemo {
    static class Target {
        static {
            System.out.println("Target initialized");
        }
    }

    public static void main(String[] args) throws ClassNotFoundException {
        String name = "ForNameDemo$Target";
        ClassLoader loader = ForNameDemo.class.getClassLoader();

        Class<?> type = Class.forName(name, false, loader);
        System.out.println("Loaded: " + type.getName());
        System.out.println("After initialize=false");

        Class<?> sameType = Class.forName(name, true, loader);
        System.out.println("After initialize=true");

        Class.forName(name, true, loader);
        System.out.println("After second initialize=true");
        System.out.println("Same Class object: " + (type == sameType));
    }
}
```

Biên dịch và chạy từ thư mục chứa file:

```bash
javac ForNameDemo.java
java ForNameDemo
```

Kết quả chuẩn:

```text
Loaded: ForNameDemo$Target
After initialize=false
Target initialized
After initialize=true
After second initialize=true
Same Class object: true
```

Giải thích theo từng mốc:

- `ForNameDemo$Target` là **binary name** của nested class `Target`. Dấu `$` ở đây thể hiện cách đặt tên binary cho member class.
- `Class.forName(name, false, loader)` trả về `Class<?>` nhưng chưa in `Target initialized`. Việc lấy `type.getName()` không kích hoạt khởi tạo `Target`.
- Lời gọi với `true` lần đầu chạy khối `static` nên dòng `Target initialized` xuất hiện **trước** dòng `After initialize=true`.
- Lần gọi `true` kế tiếp không chạy lại khối `static`; cùng tên và cùng defining loader sẽ trỏ về cùng một đối tượng `Class`.

Ví dụ giả định `Target` chưa bị mã khác khởi tạo trước đó; nếu nó đã khởi tạo rồi, bạn sẽ không quan sát được lần in đầu tiên.

## 5. Kiểm tra tên đầu vào trước khi chọn loader

`name` phải xác định đúng kiểu runtime: lớp top-level dùng tên đầy đủ như `java.util.ArrayList`, member type dùng binary name như `java.util.Map$Entry`, và mảng dùng dạng descriptor như `[Ljava.lang.String;` hoặc `[[I`. `"int"`, `"java.lang.String[]"` và `"java.util.List<String>"` không phải tên lớp hợp lệ để tra cứu theo API này.

```java
Class<?> entry = Class.forName("java.util.Map$Entry", false, loader);
Class<?> numbers = Class.forName("[I", false, loader);
```

Đây là fragment giả định biến `loader` đã được chọn và lời gọi xử lý `ClassNotFoundException`. Khi có sẵn `Class` của kiểu, `type.getName()` cung cấp đúng chuỗi tra cứu cho lớp và mảng thông thường; riêng primitive/`void` dùng class literal. Binary name, canonical name và mọi mã descriptor được giải thích kỹ trong bài về tên nhị phân.

## 6. Chọn ClassLoader: vì sao cùng tên chưa chắc cùng kiểu?

JVM xác định identity của một lớp theo **binary name và defining class loader**. Nếu hai defining loader độc lập tạo định nghĩa cho cùng tên `com.example.Plugin`, đó là **hai runtime type khác nhau**. Vì thế dù `getName()` giống nhau, `classA == classB` có thể là `false` và ép kiểu giữa chúng có thể thất bại với `ClassCastException`.

Có vài lựa chọn loader phổ biến:

```java
// Loader đã nạp chính lớp hiện tại (khi muốn ở cùng không gian kiểu)
ClassLoader definingLoader = MyApplication.class.getClassLoader();

// Thread context class loader: thường được framework/host điều chỉnh cho plugin
ClassLoader contextLoader =
        Thread.currentThread().getContextClassLoader();

// null: bootstrap loader, chỉ tìm được những kiểu do bootstrap loader cung cấp
Class<?> stringType = Class.forName("java.lang.String", false, null);
```

Đoạn này minh họa cách chọn loader và giả định có lớp `MyApplication` trong dự án; nó không phải chương trình chạy độc lập. `getContextClassLoader()` có thể trả `null`. Không mặc định rằng context loader trùng với loader của code đang gọi: ứng dụng server, hệ plugin và bộ chạy kiểm thử có thể thiết lập chúng khác nhau.

Quy trình delegation thường đưa yêu cầu nạp tới parent loader trước, nhưng custom loader có thể có chính sách riêng. `loader` truyền vào là **loader được yêu cầu bắt đầu tra cứu**; lớp thực tế có thể được định nghĩa bởi loader cha. Không thể suy ra defining loader chỉ từ biến `loader` đầu vào; hãy dùng `type.getClassLoader()` nếu cần kiểm tra (có thể trả `null` với bootstrap).

## 7. Xử lý lỗi: không gộp tất cả thành "không tìm thấy"

Các tình huống chính:

| Tình huống | Ngoại lệ/lỗi có thể gặp | Ý nghĩa |
|---|---|---|
| Không tìm được định nghĩa với loader đã chọn | `ClassNotFoundException` (checked) | Chuỗi tên sai, dependency không có trong classpath/module path hoặc loader không thấy. |
| Tìm được lớp nhưng liên kết bị hỏng | `LinkageError` hoặc subclass, ví dụ `NoClassDefFoundError`, `UnsupportedClassVersionError` | Dependency thiếu ở thời điểm liên kết, bytecode không tương thích hoặc xung đột định nghĩa. |
| Static initialization phát sinh exception | `ExceptionInInitializerError` | JVM đóng gói lỗi khởi tạo không phải `Error` xảy ra trong static initialization. |
| Khởi tạo lần trước đã thất bại | Thường là `NoClassDefFoundError` ở lần chủ động dùng tiếp theo | Lớp đã vào trạng thái erroneous trong JVM; gọi lại `forName` không sửa được nguyên nhân. |
| Không đủ quyền cần thiết trong một số bối cảnh có kiểm tra bảo mật | `SecurityException` | Không phải lỗi sai tên; tùy môi trường bảo mật/loader. |

Không cố `catch (Exception e)` để giải thích tất cả. `LinkageError` và `ExceptionInInitializerError` là `Error`, không phải `Exception`. Trong dịch vụ thực, cần log **class name, loader mong đợi và nguyên nhân gốc**, đồng thời quyết định rõ đây là lỗi cấu hình, lỗi dependency hay lỗi khởi tạo.

### Ví dụ riêng: initialization thất bại

Lưu `InitializationFailureDemo.java`:

```java
public class InitializationFailureDemo {
    static class Broken {
        static int value = crash();

        private static int crash() {
            throw new IllegalStateException("boom");
        }
    }

    public static void main(String[] args) throws ClassNotFoundException {
        String name = "InitializationFailureDemo$Broken";
        ClassLoader loader = InitializationFailureDemo.class.getClassLoader();

        Class.forName(name, false, loader);
        System.out.println("Loaded without initialization");

        try {
            Class.forName(name, true, loader);
        } catch (ExceptionInInitializerError e) {
            System.out.println("First attempt: " + e.getClass().getSimpleName());
        }

        try {
            Class.forName(name, true, loader);
        } catch (NoClassDefFoundError e) {
            System.out.println("Second attempt: " + e.getClass().getSimpleName());
        }
    }
}
```

Chạy:

```bash
javac InitializationFailureDemo.java
java InitializationFailureDemo
```

```text
Loaded without initialization
First attempt: ExceptionInInitializerError
Second attempt: NoClassDefFoundError
```

Lời gọi với `false` không thực thi `crash()`. Lần `true` đầu thất bại trong static initializer nên nhận `ExceptionInInitializerError`; lớp bị đánh dấu khởi tạo lỗi. Lần tiếp theo thường báo `NoClassDefFoundError` thay vì thử chạy lại initializer. Đây là lý do việc nạp lớp thành công chưa đảm bảo có thể khởi tạo và sử dụng lớp.

## 8. Dùng Reflection sau khi lấy Class<?>

`Class.forName(...)` chỉ trả metadata runtime. Nếu cần tạo instance, phải thực hiện thao tác riêng và kiểm tra hợp đồng kiểu:

```java
// Giả định implementation có constructor không tham số truy cập được
// và tên lớp được cấu hình/trusted trong ứng dụng.
Class<?> rawType = Class.forName(implementationName, true, chosenLoader);

Class<? extends Runnable> runnableType = rawType.asSubclass(Runnable.class);
Runnable runnable = runnableType.getDeclaredConstructor().newInstance();
runnable.run();
```

Đây là **mảnh code minh họa**, không phải chương trình độc lập: `implementationName` và `chosenLoader` phải được cung cấp; lời gọi tạo instance cần xử lý `ReflectiveOperationException`. Ý nghĩa từng bước:

1. Tải và yêu cầu khởi tạo implementation.
2. `asSubclass(Runnable.class)` kiểm tra runtime type thật sự gán được cho `Runnable`; nếu không, ném `ClassCastException`.
3. `getDeclaredConstructor().newInstance()` mới tạo đối tượng, có thể thất bại nếu không có constructor phù hợp, constructor không truy cập được hoặc chính constructor ném lỗi (thường gói trong `InvocationTargetException`).
4. `run()` là lời gọi business logic, **không** xảy ra tự động trong `Class.forName`.

Với hệ module Java, việc tìm thấy `Class` không có nghĩa mọi constructor/method private hoặc package-private đều được phép truy cập bằng Reflection. Các quy tắc visibility và module exports/opens vẫn áp dụng; đừng coi `setAccessible(true)` luôn thành công.

## 9. Khi nào dùng true, false và loader nào?

**Chọn `initialize = false` khi:**

- Chỉ kiểm tra lớp có thể được tải, đọc tên/annotation/method/field metadata hoặc quyết định xem lớp có thích hợp cho bước xử lý tiếp theo hay không.
- Muốn trì hoãn static initialization vốn có thể tốn chi phí hoặc phát sinh side effect cho tới khi thật sự cần kích hoạt lớp.

**Chọn `initialize = true` khi:**

- Muốn JVM thực hiện khởi tạo lớp ngay tại bước này và biết chắc lỗi khởi tạo trước khi đi tiếp.
- Quy trình bootstrap/plugin có hợp đồng yêu cầu static initialization diễn ra tại thời điểm nạp tên lớp.

**Chọn `loader` theo không gian class thực tế:** loader của lớp gọi phù hợp với ứng dụng bình thường; context loader phù hợp khi host/framework đã đặt nó cho plugin; custom loader khi chính ứng dụng quản lý tập JAR hay lớp động; `null` chỉ khi chủ đích dùng bootstrap loader. Không mặc định đổi tất cả loader sang system class loader vì plugin có thể không nằm trên system classpath.

**Tình huống thực tế:** dịch vụ nhận cấu hình `processor.class=com.example.FastProcessor`. Nó có thể dùng `Class.forName(name, false, pluginLoader)` để đọc metadata hoặc kiểm tra `implements Processor` trước. Khi đã xác thực cấu hình, dịch vụ mới chủ động kích hoạt và tạo implementation. Lưu ý `asSubclass(Processor.class)` chỉ hợp lệ khi cả hai phía thấy **cùng một runtime identity** của `Processor`; nạp hai bản `Processor` bởi hai loader khác nhau sẽ làm việc kiểm tra thất bại.

## 10. Những lỗi phổ biến và cách khắc phục

1. **Dùng `true` khi chỉ muốn introspection:** vô tình kích hoạt static initialization, kết nối tài nguyên hoặc đăng ký thành phần. Dùng `false` nếu không có yêu cầu khởi tạo.
2. **Tưởng `false` làm việc tra cứu "không thực thi mã nào":** custom class loader vẫn có thể có logic riêng, và bước resolution/loading vẫn phát sinh lỗi. Coi lớp được tải là input cần tin cậy, nhất là plugin bên ngoài.
3. **Sai binary name:** viết `Outer.Inner` thay cho `Outer$Inner` đối với member class, hoặc `Foo[]` thay cho descriptor mảng. Kiểm tra `SomeType.class.getName()` trong một chương trình mẫu khi cần biết tên chuẩn.
4. **Dùng sai loader:** tên lớp đúng nhưng `ClassNotFoundException` do JAR chỉ hiển thị trong loader khác. Xác minh vị trí dependency và loader tổ chức plugin, không sửa bằng cách thêm tên package tùy tiện.
5. **Nhầm `ClassNotFoundException` với `NoClassDefFoundError`:** lớp đích có thể đã được tìm ra nhưng dependency của nó bị thiếu hoặc initialization thất bại. Đọc nguyên nhân gốc/stack trace.
6. **Tưởng trả `Class` là đã tạo object:** constructor chưa chạy chỉ vì nhận được `Class<?>`. Muốn instance, gọi constructor/factory thích hợp.
7. **Dùng class name do người dùng không tin cậy nhập để khởi tạo tùy ý:** `initialize=true` có thể thực thi mã tĩnh của lớp được chỉ định. Dùng whitelist implementation, classpath/plugin kiểm soát được và nguyên tắc cấp quyền phù hợp; không coi class name là dữ liệu vô hại.

## 11. Ghi nhớ nhanh

- `Class.forName(name, initialize, loader)` = **tìm lớp bằng binary name, bằng loader chỉ định, có/không yêu cầu initialization**.
- `initialize=false`: nhận `Class<?>` để quan sát/chuẩn bị mà không chủ động chạy static initializer của lớp đích; `true`: yêu cầu initialization nếu chưa diễn ra.
- Class identity gắn với **tên + defining loader**, không chỉ với chuỗi tên.
- Tra cứu lớp, khởi tạo lớp, tạo instance và gọi method là **bốn hoạt động khác nhau**.
- Kiểm tra riêng `ClassNotFoundException`, lỗi liên kết, lỗi khởi tạo và các ràng buộc truy cập Reflection.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-objects-005">Nạp lớp khác khởi tạo lớp như thế nào</a>

<details>
<summary>Xem chi tiết</summary>

# Nạp lớp khác khởi tạo lớp như thế nào trong Java?

Khi làm việc với **đối tượng `Class<?>`** và Reflection, hai thao tác rất dễ bị nhầm là **nạp lớp (class loading)** và **khởi tạo lớp (class initialization)**. Một lớp có thể đã được JVM nhận diện, có đối tượng `Class` và có thể được kiểm tra metadata mà **chưa thực thi bất kỳ khối `static` nào** của chính lớp đó.

Mục tiêu bài học: phân biệt loading, linking, initialization và tạo đối tượng; dự đoán thời điểm khối `static` chạy; chọn đúng API `Class.forName`/`ClassLoader.loadClass`; và nhận biết các tình huống Reflection vô tình kích hoạt khởi tạo lớp.

## 1. Bốn việc khác nhau trong vòng đời lớp

| Hoạt động | JVM làm gì? | Có bắt buộc chạy `static { ... }` của lớp đích? |
|---|---|---|
| **Loading (nạp)** | Tìm định nghĩa nhị phân của lớp, đọc/diễn giải dữ liệu class và tạo biểu diễn runtime tương ứng, trong đó có đối tượng `Class`. | **Không**. |
| **Linking (liên kết)** | Verify bytecode; prepare bộ nhớ cho các biến `static` và gán giá trị mặc định; resolve tham chiếu ký hiệu khi thích hợp. Resolution có thể thực hiện muộn. | **Không**. |
| **Initialization (khởi tạo lớp)** | Chạy phương thức khởi tạo lớp `<clinit>`: các biểu thức khởi tạo trường `static` và khối `static`, theo thứ tự trong mã nguồn. | **Có**: đây chính là giai đoạn thực thi phần khởi tạo tĩnh. |
| **Instance initialization (khởi tạo đối tượng)** | Tạo một instance, thực hiện quy trình khởi tạo instance và constructor `<init>`. | Lớp thích hợp phải được khởi tạo trước khi tạo instance; constructor là công việc **riêng**, có thể chạy nhiều lần. |

Các giá trị mặc định trong bước **preparation** không đồng nghĩa với việc đã chạy biểu thức khởi tạo trong mã nguồn. Ví dụ, với `static int count = calculate();`, bước preparation chuẩn bị giá trị mặc định `0`; lời gọi `calculate()` thuộc initialization. Một số trường là **constant variable** có cách xử lý đặc biệt; xem mục 4.

Một điểm quan trọng về thứ tự: JVM có thể chủ động nạp, verify hoặc resolve lớp sớm hơn thời điểm chương trình chủ động dùng lớp. Vì vậy, **thấy một đối tượng `Class` tồn tại không chứng minh khối `static` đã chạy**. Quy tắc về thời điểm initialization được ràng buộc chặt hơn bởi ngữ nghĩa ngôn ngữ.

### Đối tượng `Class` đại diện cho điều gì?

`Class<?>` là đối tượng runtime đại diện cho kiểu: có thể đọc tên, superclass, interface, annotation, constructor, field và method. Các đường lấy tham chiếu thường gặp là:

- `MyType.class`: lấy đối tượng `Class` cho kiểu đã biết khi biên dịch; **không tự kích hoạt initialization** của `MyType`.
- `object.getClass()`: lấy lớp runtime của **một đối tượng đã tồn tại**; thao tác lấy metadata này không tự khởi tạo lại lớp.
- `ClassLoader.loadClass("pkg.MyType")`: yêu cầu loader nạp lớp theo **binary name**; bản thân lời gọi thông thường không khởi tạo lớp.
- `Class.forName("pkg.MyType")`: phiên bản một tham số **yêu cầu khởi tạo lớp** sau khi tìm/nạp lớp thành công.
- `Class.forName(name, false, loader)`: tìm/nạp lớp với yêu cầu **chưa khởi tạo**.
- `Class.forName(name, true, loader)`: tìm/nạp và **yêu cầu khởi tạo** lớp.

Tên nhị phân của nested class dùng ký tự `$`, ví dụ `demo.Outer$Inner`, không dùng `demo.Outer.Inner` trong lời gọi `Class.forName` thông thường.

## 2. Ví dụ Java 17+: nhìn thấy chính xác lúc khối static chạy

Lưu toàn bộ chương trình sau thành tệp **`ClassLifecycleDemo.java`**:

```java
import java.lang.reflect.Method;

public class ClassLifecycleDemo {
    static class Target {
        static {
            System.out.println("Target <clinit>");
        }

        Target() {
            System.out.println("Target constructor");
        }

        static void hello() {
            System.out.println("Target.hello()");
        }
    }

    public static void main(String[] args) throws Exception {
        String binaryName = ClassLifecycleDemo.class.getName() + "$Target";
        ClassLoader loader = ClassLifecycleDemo.class.getClassLoader();

        System.out.println("1. class literal");
        Class<?> fromLiteral = Target.class;
        System.out.println(fromLiteral.getSimpleName());

        System.out.println("2. loadClass");
        Class<?> fromLoader = loader.loadClass(binaryName);
        System.out.println(fromLoader == fromLiteral);

        System.out.println("3. Class.forName(false)");
        Class<?> fromForName = Class.forName(binaryName, false, loader);
        System.out.println(fromForName == fromLiteral);

        System.out.println("4. getDeclaredMethod");
        Method method = fromForName.getDeclaredMethod("hello");
        System.out.println(method.getName());

        System.out.println("5. Class.forName(true)");
        Class.forName(binaryName, true, loader);

        System.out.println("6. Method.invoke");
        method.invoke(null);

        System.out.println("7. Constructor.newInstance");
        fromForName.getDeclaredConstructor().newInstance();
    }
}
```

Biên dịch và chạy từ thư mục chứa tệp:

```bash
javac ClassLifecycleDemo.java
java ClassLifecycleDemo
```

**Stdout dự kiến (chạy trên Java 17+):**

```text
1. class literal
Target
2. loadClass
true
3. Class.forName(false)
true
4. getDeclaredMethod
hello
5. Class.forName(true)
Target <clinit>
6. Method.invoke
Target.hello()
7. Constructor.newInstance
Target constructor
```

**Giải thích từng mốc:**

1. `Target.class` cho phép lấy tên lớp, nhưng chưa chạy `Target <clinit>`.
2. `loadClass(binaryName)` trả về cùng đối tượng `Class` do cùng kiểu và cùng defining class loader, vẫn chưa có initializer tĩnh được chạy.
3. `Class.forName(..., false, ...)` cũng lấy đối tượng `Class` mà chưa yêu cầu initialization.
4. `getDeclaredMethod("hello")` đọc thông tin method, chưa **gọi** method.
5. `Class.forName(..., true, ...)` là điểm đầu tiên **kích hoạt khởi tạo lớp**; `<clinit>` in ra đúng một lần.
6. `method.invoke(null)` gọi method `static` (đối số receiver `null`); lúc này lớp đã được khởi tạo nên không chạy lại `<clinit>`.
7. `Constructor.newInstance()` tạo một đối tượng và gọi constructor, vì vậy xuất hiện `Target constructor`, **không phải** một lần khởi tạo lớp nữa.

Lưu ý: `Target` ở đây là **static nested class**. Vì mã gọi và `Target` cùng một nest, ví dụ có thể gọi method/constructor bằng Reflection mà không cần thay đổi quyền truy cập. Với lớp ở package/module khác, kiểm tra khả năng truy cập riêng.

## 3. Những thao tác thực sự kích hoạt initialization

Theo quy tắc về initialization của Java (JLS §12.4.1), các tình huống chủ động sử dụng thường gặp gồm:

- Tạo instance bằng `new SomeClass()`.
- Gọi phương thức `static` được khai báo trong lớp (`SomeClass.run()`).
- Gán hoặc đọc một trường `static` được khai báo trong lớp, **ngoại trừ** trường là *constant variable* theo định nghĩa của Java.
- Yêu cầu khởi tạo qua `Class.forName(name)` hoặc `Class.forName(name, true, loader)`.
- Một số thao tác Reflection tương ứng với việc sử dụng chủ động, chẳng hạn `Method.invoke` cho static method, `Field.get`/`Field.set` cho static field, hoặc `Constructor.newInstance`.

Những thao tác **không tự yêu cầu khởi tạo lớp đích**:

- Lấy `SomeClass.class`.
- Dùng `ClassLoader.loadClass(name)` thông thường.
- Dùng `Class.forName(name, false, loader)`.
- Chỉ kiểm tra metadata qua các phương thức như `getName()`, `getDeclaredMethods()`, `getDeclaredFields()`, `getDeclaredConstructors()`, `getAnnotations()`.
- Khai báo biến tham chiếu kiểu `SomeClass`, ép kiểu, kiểm tra `instanceof` hoặc tạo mảng `new SomeClass[3]` mà chưa tạo instance `SomeClass`.

**Cẩn thận với sự khác nhau giữa “tìm method” và “gọi method”:** `getDeclaredMethod` chỉ cung cấp đối tượng `Method`; `invoke` mới thực hiện invocation. Khi invocation là phương thức `static`, lớp khai báo method sẽ được khởi tạo trước nếu chưa khởi tạo.

**Cẩn thận với quyền sở hữu trường `static`:** truy cập `Child.inheritedField` có thể chỉ khởi tạo `Parent` nếu trường thật sự **được khai báo** ở `Parent`. Không suy ra “tên lớp xuất hiện trước dấu chấm” luôn là lớp cần khởi tạo.

Khi khởi tạo một class, JVM bảo đảm superclass cần thiết được khởi tạo trước; các superinterface có khai báo default method cũng có quy tắc khởi tạo liên quan. Không được suy rộng rằng mọi interface mà class implements đều sẽ được khởi tạo ngay.

## 4. Ngoại lệ quan trọng: hằng compile-time không kích hoạt initialization

Một `static final` chưa chắc là hằng lúc biên dịch. Một *constant variable* phải có kiểu primitive hoặc `String`, được khai báo `final`, và được khởi tạo bằng *constant expression*.

Lưu chương trình độc lập sau thành **`ConstantsDemo.java`**:

```java
public class ConstantsDemo {
    static class Config {
        static {
            System.out.println("Config initialized");
        }

        static final String LABEL = "DEV"; // constant variable
        static final Integer PORT = 8080;  // KHÔNG là constant variable
    }

    public static void main(String[] args) {
        System.out.println(Config.LABEL);
        System.out.println("after constant");
        System.out.println(Config.PORT);
    }
}
```

Chạy:

```bash
javac ConstantsDemo.java
java ConstantsDemo
```

Stdout:

```text
DEV
after constant
Config initialized
8080
```

`Config.LABEL` là compile-time constant nên code sử dụng có thể được compiler thay trực tiếp bằng `"DEV"`; cách truy cập này **không khởi tạo** `Config`. Trong khi đó `Config.PORT` là một đối tượng `Integer`, không phải constant variable; đọc trường `static` này kích hoạt `Config` initialization trước khi in `8080`.

Vì vậy, câu “đọc một `static final` sẽ khởi tạo class” **không đúng cho mọi trường hợp**. Đừng dựa riêng vào hai từ khóa `static final`; phải xét kiểu và biểu thức khởi tạo.

## 5. Cách dùng đúng trong Reflection và hệ thống plugin

### Trường hợp chỉ muốn khám phá metadata

Ví dụ loader đang có một tên lớp plugin và cần kiểm tra interface hoặc constructor trước khi quyết định sử dụng:

```java
Class<?> candidate = Class.forName(pluginClassName, false, pluginLoader);

if (!Runnable.class.isAssignableFrom(candidate)) {
    throw new IllegalArgumentException("Plugin must implement Runnable");
}

var constructor = candidate.getDeclaredConstructor();
```

Đây là **đoạn minh họa** đặt trong luồng xử lý đã có biến `pluginClassName` và `pluginLoader`, không phải chương trình độc lập. Kiểm tra `isAssignableFrom` và lấy metadata constructor không tự yêu cầu chạy khối `static` của candidate. Việc nạp/link có thể vẫn xảy ra, nên các lỗi class/dependency vẫn có thể xuất hiện trước initialization.

### Trường hợp thực sự cần tạo plugin

Sau khi xác thực metadata và bảo đảm quyền truy cập:

```java
Runnable plugin = (Runnable) constructor.newInstance();
plugin.run();
```

`newInstance()` là bước có khả năng kích hoạt **initialization class**, sau đó mới tạo instance. Ví dụ giả định class có constructor không tham số mà mã gọi được phép truy cập. Nếu plugin không có constructor thích hợp, bị giới hạn module/package hoặc có lỗi trong constructor, cần xử lý riêng.

Nếu chỉ cần tra cứu cấu trúc class, hãy chủ động chọn `Class.forName(name, false, loader)`. Nếu mục tiêu là thực thi logic khởi tạo, phiên bản `true` hoặc một thao tác sử dụng chủ động là thích hợp hơn. Tránh dùng `Class.forName(name)` chỉ để “lấy metadata” trong ứng dụng có static initializer gây side effect (đăng ký driver, mở kết nối, nạp cấu hình, thao tác file...).

## 6. Lỗi và hiện tượng thường gặp

| Hiện tượng / exception | Nguyên nhân thường gặp | Hướng kiểm tra |
|---|---|---|
| `ClassNotFoundException` | Loader không tìm được tên nhị phân được yêu cầu qua API nạp lớp. | Kiểm tra binary name, classpath/module path và loader được truyền vào. |
| `NoClassDefFoundError` | Định nghĩa cần thiết không sẵn sàng ở runtime hoặc JVM đã đánh dấu lớp bị lỗi khởi tạo ở lần trước. | Kiểm tra dependency runtime và lỗi gốc xuất hiện trước đó. |
| `ExceptionInInitializerError` | Logic khởi tạo tĩnh ném exception không phải `Error`; lỗi được JVM bọc khi initialization thất bại. | Xem cause và code trong field initializer / `static` block. |
| `IllegalAccessException` hoặc `InaccessibleObjectException` | Reflection truy cập member không phù hợp access rules hay module encapsulation. | Kiểm tra visibility, module `exports`/`opens`; không mặc định rằng `setAccessible(true)` luôn thành công. |
| `InvocationTargetException` | Method hoặc constructor được gọi qua Reflection đã ném exception. | Đọc `getCause()` để biết lỗi của code được gọi. |

Nếu `<clinit>` thất bại, JVM coi class ở trạng thái khởi tạo lỗi; lần sử dụng tiếp theo có thể nhận `NoClassDefFoundError`, chứ không tự chạy lại toàn bộ `static` initializer như chưa có chuyện gì xảy ra.

JVM phối hợp initialization giữa các thread: khi một thread đang khởi tạo lớp, thread khác cần dùng chủ động thường phải chờ. Với một đối tượng `Class` đã khởi tạo thành công, `<clinit>` không chạy lại ở mỗi lần gọi method hay tạo instance. Tuy nhiên, cùng **binary name** nhưng được định nghĩa bởi **hai class loader khác nhau** là hai runtime class khác nhau và mỗi class có vòng đời khởi tạo riêng.

Để chẩn đoán thời điểm JVM nạp/khởi tạo lớp, Java hiện đại có unified logging:

```bash
java -Xlog:class+load=info ClassLifecycleDemo
```

Lệnh sẽ tạo **rất nhiều** dòng log từ JVM/JDK; hãy lọc theo tên class quan tâm. Log `class+load` thể hiện sự kiện nạp, **không nên dùng riêng nó để kết luận lúc `<clinit>` thực thi**. Dòng `Target <clinit>` do ví dụ chủ động in ra là bằng chứng trực tiếp cho initialization của `Target`.

## 7. Những nhầm lẫn cần tránh

1. **“Đã gọi `SomeClass.class` nghĩa là khối `static` đã chạy.”** Sai: đó là thao tác lấy `Class` reference.
2. **“`ClassLoader.loadClass` và `Class.forName(String)` giống nhau.”** Sai về tác dụng mặc định: lời gọi `forName` một tham số yêu cầu initialization.
3. **“Đọc metadata bằng Reflection luôn thực thi code của class.”** Sai: tra cứu `Method`/`Field` khác với invocation hoặc truy cập giá trị trường tĩnh.
4. **“Mỗi lần tạo đối tượng thì khối `static` chạy lại.”** Sai: initializer tĩnh chạy theo vòng đời khởi tạo của runtime class; constructor chạy cho từng lần tạo instance.
5. **“Mọi `static final` đều kích hoạt class khi được đọc.”** Sai: constant variable có thể được inline, như `Config.LABEL`.
6. **“Tên class giống nhau nghĩa là cùng `Class`.”** Chưa đủ: định danh runtime phụ thuộc **binary name và defining class loader**.

## 8. Tự kiểm tra nhanh

**Câu 1.** `Class<?> c = Service.class;` rồi `c.getDeclaredMethods();` có bắt buộc chạy `Service` static block không?

**Trả lời:** Không. Hai bước này lấy đối tượng `Class` và metadata; chúng không phải hành động chủ động khởi tạo `Service`.

**Câu 2.** `Class.forName("demo.Service", false, loader)` trả về `Class` thành công. Khi nào static initializer sẽ chạy?

**Trả lời:** Khi có yêu cầu initialization sau đó, như `Class.forName(..., true, loader)`, gọi static method hay tạo instance; lớp cũng có thể được khởi tạo bởi hành động chủ động khác theo JLS.

**Câu 3.** Vì sao `Constructor.newInstance()` có thể in hai nhóm log: static initializer và constructor?

**Trả lời:** Nếu class chưa được khởi tạo, JVM thực hiện class initialization trước; tiếp theo Reflection tạo instance và gọi constructor. Các lần tạo instance kế tiếp vẫn gọi constructor nhưng không lặp lại static initializer thành công.

**Câu 4.** Cùng tên `demo.Plugin` được nạp bởi hai class loader độc lập có dùng chung một lần khởi tạo không?

**Trả lời:** Không, nếu mỗi loader thực sự **định nghĩa** một class riêng. Mỗi runtime class có đối tượng `Class` và trạng thái khởi tạo riêng; nếu loader chỉ ủy quyền và cùng nhận một class đã được loader cha định nghĩa thì chúng vẫn tham chiếu cùng runtime class.

## Tài liệu chuẩn

- **Java Language Specification §12.2–§12.4**: loading, linking, initialization; đặc biệt §12.4.1 về thời điểm initialization.
- **Java Virtual Machine Specification §5**: loading, linking, initialization và trạng thái runtime.
- Java SE API: `java.lang.Class` (đặc biệt `forName`), `java.lang.ClassLoader`, `java.lang.reflect.Method`, `java.lang.reflect.Constructor`.

**Ghi nhớ:** **Loading giúp JVM biết lớp là gì; linking chuẩn bị và kiểm tra định nghĩa; initialization mới chạy khối/biểu thức `static`; tạo instance mới chạy constructor.** Reflection có thể làm việc với metadata trước khi class được khởi tạo, nên hãy chọn API theo đúng mục đích.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-objects-006">Class&lt;T&gt;, Class&lt;?&gt; và giới hạn kiểm tra kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# `Class<T>`, `Class<?>` và giới hạn kiểm tra kiểu trong Java Reflection

## 1. Mục tiêu học tập

Sau bài này, bạn có thể:

- Hiểu đối tượng `java.lang.Class` đại diện cho một kiểu ở thời gian chạy như thế nào, và tham số `T` trong `Class<T>` giúp kiểm tra kiểu ở thời gian biên dịch ra sao.
- Phân biệt `Class<String>`, `Class<?>`, `Class<? extends Number>`, và cách dùng đúng khi viết API Reflection.
- Dùng `Class.isInstance`, `Class.cast`, `Class.isAssignableFrom`, `Class.asSubclass` để kiểm tra kiểu lúc chạy; hiểu chiều quan hệ kiểu và giới hạn của từng phép kiểm tra.
- Nhận biết chính xác phần thông tin kiểu bị xóa (*type erasure*) và lý do không thể kiểm chứng `List<String>` chỉ bằng `List.class`.
- Áp dụng *class token* trong một API generic mà không giả định JVM có thể xác minh mọi tham số kiểu.

## 2. `Class` là gì và `T` có ý nghĩa gì?

Mỗi `Class` object mô tả một kiểu được JVM nhận biết tại thời gian chạy: lớp, interface, enum, record, mảng, kiểu nguyên thủy hoặc `void`. `String.class` trả về đối tượng `Class` mô tả `java.lang.String`; `someObject.getClass()` cho biết lớp thực tế của đối tượng. Trong phạm vi một class loader định nghĩa một lớp, JVM duy trì một `Class` object tương ứng; hai lớp cùng tên do các class loader khác nhau định nghĩa có thể là hai kiểu khác nhau.

Khai báo `Class<T>` được *generic hóa* để trình biên dịch theo dõi quan hệ giữa token lớp và kiểu `T`:

```java
Class<String> stringType = String.class;
Class<Integer> integerType = Integer.class;
Class<?> unknownType = String.class;
```

`T` trong `Class<T>` là tham số kiểu ở **compile time**, không phải một phiên bản khác của lớp `Class` ở runtime. Cả ba biến trên đều tham chiếu đối tượng `Class`; khác nhau ở những phép gán và kiểu kết quả mà trình biên dịch chấp nhận.

Ví dụ sau **không biên dịch**:

```java
Class<Object> objectType = String.class; // Lỗi: Class<String> không phải Class<Object>
Class<String> textType = Integer.class; // Lỗi: hai type token không tương thích
```

Vì generic trong Java *bất biến* (invariant), việc `String` kế thừa `Object` không làm cho `Class<String>` kế thừa `Class<Object>`. Nếu chỉ muốn giữ một token bất kỳ, dùng `Class<?>`.

## 3. `Class<T>` so với `Class<?>`

| Khai báo | Điều đã biết tại compile time | Trường hợp phù hợp |
| --- | --- | --- |
| `Class<String>` | Token đại diện cho `String` | API cần đúng kiểu `String` |
| `Class<T>` | Token tương ứng với tham số kiểu `T` được suy luận/khai báo | Phương thức hoặc lớp generic cần trả về `T` an toàn |
| `Class<?>` | Có một token lớp, nhưng kiểu cụ thể chưa biết | Duyệt metadata, đọc Reflection, lưu các loại token khác nhau |
| `Class<? extends Number>` | Token của một lớp là `Number` hoặc lớp con | Giới hạn token thuộc một hệ phân cấp |
| `Class<? super Integer>` | Token của `Integer` hoặc một kiểu cha của nó | Làm việc với token có giới hạn kiểu cha |

Ví dụ:

```java
Class<?> a = String.class;
Class<?> b = Integer.class;
Class<? extends Number> numberType = Double.class;

// Không hợp lệ: Double.class có kiểu Class<Double>, không phải Class<Number>.
// Class<Number> exactNumber = Double.class;
```

`Class<?>` **không có nghĩa là** `Class<Object>`. Dấu `?` biểu diễn một kiểu chưa biết (*wildcard capture*). Ta vẫn có thể gọi `getName()`, `getDeclaredMethods()`, `isInterface()` hay `isInstance(...)`; nhưng kết quả của `type.cast(value)` khi `type` là `Class<?>` chỉ được dùng an toàn như `Object` nếu không có thêm thông tin.

```java
Class<?> type = String.class;
Object value = type.cast("hello"); // Hợp lệ; compile time chưa chứng minh là String
// String s = type.cast("hello");   // Không biên dịch: thiếu bằng chứng kiểu String
```

Ngược lại, với `Class<String>`, compiler biết phương thức `cast` trả về `String`:

```java
Class<String> type = String.class;
String value = type.cast("hello"); // Không cần ép kiểu tường minh
```

Không nên đổi `Class<?>` sang `Class<String>` chỉ bằng ép kiểu không được kiểm tra: việc đó làm mất lợi ích của hệ thống kiểu và có thể dẫn đến lỗi lúc chạy.

## 4. Cách lấy `Class` object

| Cách lấy | Kiểu biểu thức/kết quả | Lưu ý |
| --- | --- | --- |
| `String.class` | `Class<String>` | Biết lớp tại lúc biên dịch; không cần tạo instance |
| `Integer.class` | `Class<Integer>` | Token của wrapper `Integer` |
| `int.class` | `Class<Integer>` theo quy tắc kiểu của class literal | Token vẫn mô tả **primitive `int`**, khác `Integer.class` |
| `String[].class` | `Class<String[]>` | Mảng còn thông tin component type lúc chạy |
| `obj.getClass()` | `Class<? extends ...>` dựa trên kiểu tĩnh đã xóa của biểu thức | Trả về lớp thực tế; `obj == null` gây `NullPointerException` |
| `Class.forName("java.lang.String")` | `Class<?>` | Tìm lớp theo tên; có thể ném `ClassNotFoundException` và mặc định kích hoạt khởi tạo lớp |

Ví dụ:

```java
String text = "Java";
Class<?> runtimeType = text.getClass();
Class<?> loadedType = Class.forName("java.lang.String");

System.out.println(runtimeType == loadedType); // true trong trường hợp class loader thông thường
System.out.println(int.class == Integer.class); // false
```

Tên đơn giản của lớp (`getSimpleName()`) và tên đầy đủ (`getName()`) là metadata mô tả kiểu; bản thân chúng không xác minh cấu trúc generic của một object. Với `Class.forName`, chỉ nạp tên lớp từ cấu hình/đầu vào đã được kiểm soát nếu chương trình sử dụng Reflection để tạo đối tượng hoặc gọi thành viên; không coi việc nạp lớp là xác nhận rằng lớp thỏa mãn một interface.

## 5. Những phép kiểm tra kiểu mà `Class` hỗ trợ ở runtime

### 5.1. `isInstance(Object)`: đối tượng có thuộc kiểu này?

`type.isInstance(value)` tương đương về ý nghĩa kiểm tra runtime với phép `value instanceof SomeKnownType`, nhưng `type` có thể được xác định động.

```java
System.out.println(Number.class.isInstance(Integer.valueOf(7))); // true
System.out.println(String.class.isInstance(7));                  // false
System.out.println(String.class.isInstance(null));               // false
```

Lệnh gọi trả về `boolean`, không chuyển kiểu của biến theo `T`. Riêng token primitive: `int.class.isInstance(Integer.valueOf(7))` trả về `false` vì một đối tượng `Integer` không phải instance của kiểu primitive `int`.

### 5.2. `cast(Object)`: kiểm tra và chuyển kiểu với một token

Phương thức `Class<T>.cast(Object)` có chữ ký trả về `T`. Khi đối tượng không tương thích, nó ném `ClassCastException`. Đối với `null`, `cast(null)` trả về `null`.

```java
static <T> T requireType(Object value, Class<T> expected) {
    return expected.cast(value);
}

String ok = requireType("Java", String.class);
// String bad = requireType(123, String.class); // Biên dịch được, ném ClassCastException
```

Vì tham số `value` chỉ có kiểu `Object`, compiler **không thể đảm bảo** rằng dữ liệu đầu vào thuộc kiểu mong đợi. Compiler đảm bảo *kiểu trả về của API* là `T`, còn `Class.cast` thực hiện bước kiểm tra lớp thực tế lúc chạy. Nếu muốn báo lỗi rõ ràng, có thể kiểm tra `expected.isInstance(value)` trước khi gọi `cast`; lưu ý xử lý `null` riêng theo yêu cầu nghiệp vụ.

### 5.3. `isAssignableFrom(Class<?>)`: hướng quan hệ gán

`Target.class.isAssignableFrom(Source.class)` trả về `true` nếu một giá trị thuộc `Source` có thể gán cho biến kiểu `Target` theo quan hệ runtime giữa các lớp/interface (bao gồm cùng kiểu). **Đọc từ trái sang phải: đích nhận nguồn**.

```java
System.out.println(Number.class.isAssignableFrom(Integer.class)); // true
System.out.println(Integer.class.isAssignableFrom(Number.class)); // false
System.out.println(CharSequence.class.isAssignableFrom(String.class)); // true
```

Phép kiểm tra này làm việc trên `Class` object, không kiểm tra một instance và không xác minh tham số kiểu của `List<String>`. Các token primitive chỉ tương thích với chính kiểu primitive đó trong phép này; không có quy tắc tự động boxing như phép gọi hàm Java thông thường.

### 5.4. `asSubclass(Class<U>)`: giới hạn token về một kiểu cha

`candidate.asSubclass(Base.class)` kiểm tra `candidate` có phải `Base` hoặc lớp con/implementation của `Base` hay không. Thành công trả về `Class<? extends Base>`; thất bại ném `ClassCastException`.

```java
Class<?> discovered = Integer.class;
Class<? extends Number> numericType = discovered.asSubclass(Number.class);

// discovered.asSubclass(Runnable.class); // Ném ClassCastException tại runtime
```

Đây là cách hợp lý để lọc các lớp được phát hiện bằng Reflection trước khi xử lý như plugin, strategy hoặc implementation của một API. `asSubclass` xác nhận **quan hệ kế thừa/implements**, không xác nhận constructor tồn tại, quyền truy cập thành viên, hay điều kiện chạy của ứng dụng.

## 6. Ví dụ hoàn chỉnh: type token và runtime check (Java 17+)

Tạo file `ClassTokenDemo.java`:

```java
public class ClassTokenDemo {
    static <T> T requireType(Object value, Class<T> expected) {
        return expected.cast(value);
    }

    static void describe(Class<?> type, Object value) {
        System.out.println(type.getSimpleName() + ": " + type.isInstance(value));
    }

    public static void main(String[] args) {
        Class<String> textType = String.class;
        Class<?> unknownType = Integer.class;
        Class<? extends Number> numberType =
                unknownType.asSubclass(Number.class);

        System.out.println(textType.getSimpleName());
        System.out.println(unknownType.getName());
        System.out.println(numberType.getSimpleName());
        System.out.println(Number.class.isAssignableFrom(Integer.class));

        describe(String.class, "Java");
        describe(String.class, 42);

        String name = requireType("HELLO", textType);
        System.out.println(name.toLowerCase());

        try {
            requireType(42, textType);
        } catch (ClassCastException ex) {
            System.out.println("ClassCastException");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac ClassTokenDemo.java
java ClassTokenDemo
```

Kết quả chuẩn:

```text
String
java.lang.Integer
Integer
true
String: true
String: false
hello
ClassCastException
```

Ở đây `textType` giúp compiler suy luận `T = String` cho `requireType`. Token `unknownType` chỉ biết có một lớp; `asSubclass(Number.class)` mới xác minh lớp đó thuộc họ `Number` và trả về kiểu token có giới hạn phù hợp. Chú ý dòng `requireType(42, textType)` vẫn hợp lệ ở compile time: lỗi chỉ được phát hiện ở runtime, đúng nơi `Class.cast` kiểm tra đối tượng.

## 7. Giới hạn quan trọng: *type erasure* và generic không được xác minh đầy đủ

Thông tin mà `Class` giữ ở runtime là kiểu *reified* như `String`, `Integer`, `List`, `String[]`. Đối với phần lớn kiểu generic, JVM không lưu đối số kiểu cụ thể của từng instance `List<String>` hoặc `List<Integer>` trong `Class` object. Hai danh sách đó đều có lớp runtime là `ArrayList` nếu cùng được tạo bằng `new ArrayList<>()`.

Ví dụ cú pháp sau **không hợp lệ trong Java**:

```java
// Class<List<String>> tokens = List<String>.class; // Không tồn tại class literal này
// boolean ok = obj instanceof List<String>;         // Không kiểm tra được đối số kiểu String
```

Có thể dùng `List.class` hoặc `obj instanceof List<?>` để kiểm tra object là một `List`; không thể từ đó kết luận tất cả phần tử là `String`. Ép `(List<String>) object` với giá trị chỉ biết là `Object` thường gây cảnh báo *unchecked cast*, vì compiler không thể kiểm chứng đối số kiểu.

Ví dụ runnable `ErasureDemo.java`:

```java
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ErasureDemo {
    List<String> declaredNames;

    public static void main(String[] args) throws Exception {
        List<String> names = new ArrayList<>();
        names.add("Ada");

        List<Integer> scores = new ArrayList<>();
        scores.add(10);

        System.out.println(names.getClass() == scores.getClass());
        System.out.println(List.class.isInstance(names));
        System.out.println(List.class.isInstance(scores));

        Field field = ErasureDemo.class.getDeclaredField("declaredNames");
        Type declaredType = field.getGenericType();
        System.out.println(declaredType.getTypeName());
        System.out.println(declaredType instanceof ParameterizedType);
    }
}
```

Chạy:

```bash
javac ErasureDemo.java
java ErasureDemo
```

Kết quả:

```text
true
true
true
java.util.List<java.lang.String>
true
```

Có hai lớp thông tin phải tách biệt:

1. `names.getClass()` và `scores.getClass()` chỉ cho biết lớp runtime của danh sách, nên giống nhau. `List.class.isInstance(...)` chỉ xác nhận interface `List`, **không kiểm tra kiểu phần tử**.
2. `Field.getGenericType()` có thể đọc khai báo `List<String>` từ metadata chữ ký generic của **trường** `declaredNames`; đó là thông tin về khai báo của thành viên, **không phải** bằng chứng mọi object gán vào trường/danh sách luôn chứa `String` ở runtime. `Type` và `ParameterizedType` mô tả kiểu khai báo; chúng không biến một `Class<List>` thành token có khả năng validate mọi phần tử.

Nếu nghiệp vụ cần xác minh `List<String>` lúc nhận dữ liệu không tin cậy, phải kiểm tra container **và từng phần tử**, hoặc dùng cơ chế giải mã/schema validation có kiểm tra cấu trúc. Ví dụ:

```java
static boolean isStringList(Object candidate) {
    if (!(candidate instanceof java.util.List<?> list)) {
        return false;
    }
    for (Object item : list) {
        if (!(item instanceof String)) {
            return false;
        }
    }
    return true;
}
```

Đoạn trên là phương thức minh họa để đặt trong một lớp Java 17+; quy ước của nó là **không chấp nhận phần tử `null`**, vì `null instanceof String` là `false`. Nếu `null` được cho phép, điều kiện cần đổi theo yêu cầu miền dữ liệu. Danh sách rỗng trả về `true` theo quy tắc “mọi phần tử đều là `String`”, không hàm ý có thể suy ra đối số generic từ danh sách rỗng.

## 8. Dùng `Class<T>` để thiết kế API an toàn hơn

Một lớp tình huống điển hình là lấy một object từ kho lưu trữ chung, rồi kiểm tra bằng class token trước khi trả về kiểu cụ thể:

```java
import java.util.Map;

public class TypedLookupDemo {
    static <T> T lookup(Map<String, Object> values,
                        String key,
                        Class<T> expectedType) {
        Object value = values.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing value: " + key);
        }
        return expectedType.cast(value);
    }

    public static void main(String[] args) {
        Map<String, Object> values = Map.of("port", 8080, "host", "localhost");
        Integer port = lookup(values, "port", Integer.class);
        String host = lookup(values, "host", String.class);
        System.out.println(host + ":" + port);
    }
}
```

```bash
javac TypedLookupDemo.java
java TypedLookupDemo
```

```text
localhost:8080
```

Lợi ích: lời gọi `lookup(values, "port", Integer.class)` cho kết quả kiểu `Integer` mà không cần unchecked cast của caller. Giới hạn: kho `Map<String,Object>` vẫn có thể chứa giá trị sai kiểu; `cast` sẽ ném `ClassCastException` tại runtime. Phương pháp này phù hợp với kiểu cụ thể như `Integer`, `String` hoặc một interface/lớp cha; nó không tự kiểm chứng tham số generic lồng nhau như `Map<String, List<Integer>>`.

Trong một framework/plugin dùng Reflection, mẫu an toàn tương tự là nhận `Class<?>` sau khi tìm lớp, rồi gọi `asSubclass(ExpectedInterface.class)` để chứng minh giới hạn loại trước khi tiếp tục. Nếu còn cần khởi tạo và gọi phương thức, phải kiểm tra thêm constructor, quyền truy cập, exception và hợp đồng API tương ứng.

## 9. Sai lầm thường gặp và cách tránh

| Sai lầm | Vì sao sai | Cách xử lý |
| --- | --- | --- |
| Coi `Class<?>` là `Class<Object>` | Generic bất biến; wildcard là kiểu chưa biết | Dùng `Class<?>` cho khám phá lớp, `Class<T>` khi cần gắn với kết quả `T` |
| Nghĩ `Class<T>` xác nhận dữ liệu là `T` ngay ở compile time | Đầu vào có thể là `Object`/dữ liệu động | Dùng `cast` hoặc `isInstance` kiểm tra tại runtime |
| Đảo chiều `isAssignableFrom` | `Target.isAssignableFrom(Source)` kiểm tra đích nhận nguồn | Đặt kiểu đích bên trái và kiểm thử cả hai chiều |
| Dùng `List.class` để kết luận `List<String>` | Erasure làm mất đối số kiểu trên từng instance | Kiểm tra phần tử hoặc dùng schema validation |
| Tưởng `getGenericType()` là runtime validation | Nó trả về metadata của khai báo | Phân biệt metadata của field/method với nội dung object |
| Xem `int.class` và `Integer.class` như cùng token | Primitive và wrapper là hai kiểu runtime khác nhau | Chọn token theo kiểu dữ liệu thực sự cần kiểm tra |
| Ép `Class<?>` sang `Class<X>` thiếu căn cứ | Unchecked cast có thể làm sai cam kết kiểu với caller | Dùng `asSubclass` để kiểm tra subtype; dùng `cast` để kiểm tra instance |
| Coi `asSubclass` đảm bảo có thể khởi tạo | Quan hệ kế thừa không nói gì về constructor hay truy cập | Kiểm tra riêng điều kiện instantiation và Reflection |

## 10. Tự kiểm tra

1. Tại sao `Class<String>` không gán được cho `Class<Object>` dù `String` là `Object`? Vì `Class<T>` invariant; muốn giữ token bất kỳ, chọn `Class<?>`.
2. `Number.class.isAssignableFrom(Integer.class)` có kết quả gì? `true`, vì object `Integer` gán được vào biến kiểu `Number`; chiều ngược lại là `false`.
3. `String.class.cast(42)` khác `String.class.isInstance(42)` thế nào? Phép đầu ném `ClassCastException`; phép sau trả về `false`.
4. Tại sao `List.class.isInstance(List.of(1, 2))` không chứng minh danh sách là `List<String>`? Vì chỉ kiểm tra kiểu `List` ở runtime, không biết đối số kiểu của các phần tử.
5. Sau `Class<?> candidate = Integer.class;`, làm sao có token giới hạn trong `Number` mà không unchecked cast? Dùng `Class<? extends Number> sub = candidate.asSubclass(Number.class);`; method kiểm tra subtype trước khi trả về.

**Ghi nhớ:** `Class<T>` nối **kiểu tĩnh `T`** với token kiểu runtime, `Class<?>` thể hiện token chưa biết kiểu cụ thể, còn `Class.isInstance/cast/isAssignableFrom/asSubclass` chỉ xác minh những quan hệ kiểu thực sự còn tồn tại ở runtime. Tham số của phần lớn kiểu generic như `List<String>` bị erasure; đừng suy luận kiểm tra được `String` chỉ vì đã có `List.class`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-objects-007">Class đại diện cho primitive, void, mảng, interface, enum và record</a>

<details>
<summary>Xem chi tiết</summary>

# Class đại diện cho primitive, void, mảng, interface, enum và record

## 1. Vì sao các kiểu này đều có đối tượng Class?

Trong Java Reflection, `java.lang.Class<?>` là đối tượng mô tả **một kiểu tại runtime**. Không chỉ lớp thông thường như `String`, Java còn có đối tượng `Class` cho tám kiểu nguyên thủy, `void`, mọi kiểu mảng, interface, enum và record. Ta dùng nó để hỏi một kiểu thuộc nhóm nào, có thành phần gì và có quan hệ kế thừa/triển khai ra sao.

Đối tượng `Class` **không phải** instance của kiểu đang được mô tả. Ví dụ `int.class` mô tả kiểu `int`, nhưng bản thân nó là một đối tượng thuộc `Class`; `void.class` tồn tại dù không thể tạo giá trị `void`. Kiểu khai báo của biến chứa metadata thường là `Class<?>` để chấp nhận nhiều loại kiểu khác nhau.

| Loại được mô tả | Ví dụ lấy `Class` | Phép kiểm tra tiêu biểu | Ý nghĩa |
| --- | --- | --- | --- |
| Primitive | `int.class`, `boolean.class` | `isPrimitive()` | Một trong tám kiểu nguyên thủy |
| `void` | `void.class` | `isPrimitive()` trả `true`; so riêng với `void.class` | Biểu diễn việc không có giá trị trả về |
| Mảng | `String[].class`, `int[][].class` | `isArray()` | Kiểu mảng, độc lập với kiểu thành phần |
| Interface | `Runnable.class` | `isInterface()` | Kiểu giao diện; annotation cũng là một interface |
| Enum | `Level.class` với `enum Level` | `isEnum()` | Lớp enum khai báo trong mã nguồn |
| Record | `Person.class` với `record Person(...)` | `isRecord()` | Lớp record và danh sách thành phần record |

Một lớp thông thường như `String.class` có thể trả `false` cho **tất cả** các phép kiểm tra trong bảng; điều đó không có nghĩa `Class` không nhận diện được nó. Những phép kiểm tra này phân loại đặc điểm của kiểu, không phải phép thử “có phải đối tượng `Class` hay không”.

## 2. Cách lấy đối tượng Class

- **Class literal**: `int.class`, `void.class`, `int[].class`, `Runnable.class`, `Level.class`, `Person.class`. Đây là cách rõ ràng nhất khi biết kiểu ở thời điểm biên dịch.
- **Từ một object**: `value.getClass()` cho biết lớp runtime của đối tượng. `getClass()` không gọi được trên giá trị primitive hay `void`; khi primitive bị boxing, kết quả là lớp wrapper (`Integer`, `Boolean`...), không phải kiểu nguyên thủy.
- **Từ tên kiểu khi chạy**: `Class.forName("java.lang.String")` tìm theo tên nhị phân. Với mảng, tên nhận vào phải theo cú pháp JVM, chẳng hạn `"[I"` cho `int[]` và `"[[Ljava.lang.String;"` cho `String[][]`. `Class.forName("int")` và `Class.forName("java.lang.String[]")` đều không phải cách tìm tương ứng và gây `ClassNotFoundException`.

Ví dụ:

~~~java
Class<?> primitive = int.class;
Class<?> noValue = void.class;
Class<?> array = String[][].class;
Class<?> wrapper = Integer.valueOf(42).getClass();

System.out.println(primitive == Integer.TYPE);    // true
System.out.println(noValue == Void.TYPE);          // true
System.out.println(wrapper == Integer.class);      // true
System.out.println(wrapper == int.class);          // false
~~~

Đoạn trên là **fragment đặt bên trong một phương thức Java**, không phải chương trình độc lập. `Integer.TYPE` và `Void.TYPE` lần lượt tham chiếu đối tượng `Class` của `int` và `void`. `Class.forName(String)` có thể kích hoạt khởi tạo lớp; nếu chỉ cần tải metadata mà không khởi tạo lớp, có overload `Class.forName(name, false, loader)` (vẫn cần tên hợp lệ và class loader thích hợp).

## 3. Primitive và void: cùng isPrimitive(), khác ngữ nghĩa

Java có tám primitive: `boolean`, `byte`, `short`, `char`, `int`, `long`, `float` và `double`. Từng kiểu có `Class` riêng do JVM cung cấp. **`void.class.isPrimitive()` cũng trả `true`** theo hợp đồng API `Class`, dù `void` không phải kiểu cho biến hoặc object có giá trị; đó là trường hợp đặc biệt cần xử lý trước khi phân loại “primitive có giá trị”.

~~~java
System.out.println(int.class.isPrimitive());     // true
System.out.println(void.class.isPrimitive());    // true
System.out.println(Integer.class.isPrimitive()); // false
System.out.println(int[].class.isPrimitive());   // false
System.out.println(void.class.getName());        // void
~~~

Chú ý:

- `int.class != Integer.class`: primitive và wrapper là hai kiểu khác nhau; autoboxing không làm chúng thành cùng đối tượng `Class`.
- `Class#isInstance(Object)` không thay được việc kiểm tra giá trị primitive: giá trị truyền cho tham số `Object` phải là reference, và `int.class.isInstance(Integer.valueOf(1))` trả `false`.
- Không có `void[]` hợp lệ, không thể tạo đối tượng bằng `void.class`. Khi phản chiếu phương thức, `Method#getReturnType() == void.class` cho biết phương thức trả về `void`.
- `getSuperclass()` của primitive và `void` trả `null`; `isInterface()` và `isArray()` đều trả `false`.

## 4. Mảng: Class của mảng khác Class của thành phần

Mỗi kiểu mảng có `Class` của chính nó: `int[].class` khác `int.class`, còn `int[][].class` khác `int[].class`. Mảng luôn là **reference type** dù chứa các phần tử primitive. Java cung cấp `isArray()` và `getComponentType()` để bóc tách từng chiều:

~~~java
Class<?> matrix = int[][].class;
System.out.println(matrix.isArray());                           // true
System.out.println(matrix.getComponentType() == int[].class);  // true
System.out.println(matrix.getComponentType()
                         .getComponentType() == int.class);     // true
System.out.println(String.class.getComponentType() == null);    // true
~~~

`getComponentType()` chỉ bỏ **một** chiều mảng mỗi lần. `Class#getTypeName()` thuận tiện để hiển thị tên dễ đọc, còn `getName()` của mảng trả cú pháp descriptor JVM:

| Biểu thức | Giá trị |
| --- | --- |
| `int[].class.getName()` | `[I` |
| `int[][].class.getName()` | `[[I` |
| `String[].class.getName()` | `[Ljava.lang.String;` |
| `String[][].class.getName()` | `[[Ljava.lang.String;` |
| `String[][].class.getTypeName()` | `java.lang.String[][]` |

Dấu `[` biểu thị một chiều; `I` là descriptor của `int`, còn `L...;` bao tên nhị phân của lớp tham chiếu. Với một đối tượng mảng, `Array.getLength(array)` đọc số phần tử và `Array.newInstance(componentType, length)` tạo mảng bằng reflection. `int[].class.getSuperclass() == Object.class` là `true`; mọi kiểu mảng cũng triển khai `Cloneable` và `java.io.Serializable`. Chúng **không** được phân loại là primitive vì phần tử là primitive.

## 5. Interface: phát hiện và xác định khả năng gán kiểu

`SomeInterface.class.isInterface()` trả `true` cho interface; `getSuperclass()` của interface trả `null`. Muốn biết một lớp có thể gán vào biến của interface hay không, dùng `isAssignableFrom` với đúng chiều:

~~~java
interface Reader { void read(); }
final class FileReaderImpl implements Reader {
    public void read() {}
}

System.out.println(Reader.class.isInterface());                    // true
System.out.println(Reader.class.isAssignableFrom(
        FileReaderImpl.class));                                     // true
System.out.println(FileReaderImpl.class.isAssignableFrom(
        Reader.class));                                             // false
~~~

Đây là fragment mô tả các kiểu và câu lệnh trong ngữ cảnh hợp lệ; chương trình độc lập hoàn chỉnh có ở phần 8. Đọc `A.class.isAssignableFrom(B.class)` là: “Một đối tượng kiểu `B` có thể gán cho biến kiểu `A` không?”. `Reader.class.isInstance(new FileReaderImpl())` cũng trả `true`; nó kiểm tra **một object**, trong khi `isAssignableFrom` so sánh **hai kiểu**.

Một annotation khai báo bằng `@interface` cũng là một loại interface. Vì vậy, với annotation type, cả `isInterface()` lẫn `isAnnotation()` đều có thể là `true`. Nếu chương trình phải phân biệt annotation với interface thường, kiểm tra `isAnnotation()` trước.

## 6. Enum: nhận diện lớp enum và lấy các hằng

Với `enum Level { LOW, HIGH }`, `Level.class.isEnum()` trả `true`. `Level.class.getEnumConstants()` trả mảng `Level[]` chứa các hằng theo thứ tự khai báo; với kiểu không phải enum, `getEnumConstants()` trả `null`. Mảng kết quả của một enum không chứa phần tử là điều hợp lệ nếu enum khai báo không có hằng nào.

~~~java
enum Level { LOW, HIGH }

System.out.println(Level.class.isEnum());                    // true
System.out.println(java.util.Arrays.toString(
        Level.class.getEnumConstants()));                     // [LOW, HIGH]
System.out.println(String.class.getEnumConstants() == null); // true
System.out.println(Enum.class.isEnum());                     // false
~~~

`Enum` là **lớp cơ sở** của các enum, bản thân nó không được khai báo bằng `enum` nên `Enum.class.isEnum()` là `false`. Một enum constant có class body riêng có thể có `constant.getClass()` là lớp con ẩn danh, khiến `constant.getClass().isEnum()` trả `false`; muốn xác định **enum khai báo hằng**, dùng `constant.getDeclaringClass()`. Không giả định mọi đối tượng enum đều trả cùng một `getClass()` trong trường hợp này.

## 7. Record: metadata theo record components

Record (được chuẩn hóa từ Java 16, dùng ổn định trên Java 17+) khai báo tập thành phần dữ liệu ngay trong header:

~~~java
record Person(String name, int age) {}
~~~

`Person.class.isRecord()` trả `true`. `Person.class.getRecordComponents()` trả `RecordComponent[]` theo **thứ tự khai báo trong header**. Mỗi `RecordComponent` cho biết tên (`getName()`), kiểu (`getType()`), kiểu tổng quát (`getGenericType()`) và phương thức truy cập (`getAccessor()`).

~~~java
for (java.lang.reflect.RecordComponent component
        : Person.class.getRecordComponents()) {
    System.out.println(component.getName()
            + ": " + component.getType().getSimpleName());
}
// name: String
// age: int
~~~

`getRecordComponents()` trả `null` nếu kiểu **không phải record**, nhưng trả mảng không-null, có thể rỗng, nếu kiểu **là record**. Do đó, nên kiểm tra `isRecord()` hoặc `null` trước khi duyệt. `Record.class.isRecord()` là `false` vì `java.lang.Record` chỉ là lớp cơ sở; lớp record cụ thể có superclass trực tiếp là `Record` và là `final`.

**Record component khác với Java field.** `getDeclaredFields()` trả metadata field do JVM mô tả, còn `getRecordComponents()` phản ánh hợp đồng thành phần khai báo trong header và giữ thứ tự của header. Record có các field thành phần là `private final`, accessor công khai và các phương thức cơ bản do trình biên dịch hỗ trợ, nhưng tính bất biến là **nông**: nếu một component giữ reference đến đối tượng mutable, dữ liệu bên trong vẫn có thể thay đổi.

## 8. Ví dụ Java 17+ hoàn chỉnh, có thể biên dịch và chạy

Tạo tệp `ReflectionClassKindsDemo.java` với nội dung sau. Ví dụ gom sáu nhóm kiểu vào một chương trình và in những đặc điểm đáng kiểm chứng:

~~~java
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

public class ReflectionClassKindsDemo {
    interface Payment { void pay(); }

    static final class Card implements Payment {
        public void pay() {}
    }

    enum Level { LOW, HIGH }

    record Person(String name, int age) {}

    public static void main(String[] args) throws ClassNotFoundException {
        System.out.println("int/void: " + int.class.isPrimitive() + "/" + void.class.isPrimitive());
        System.out.println("int vs Integer.TYPE: " + (int.class == Integer.TYPE));
        System.out.println("wrapper distinct: " + (int.class != Integer.class));

        Class<?> grid = String[][].class;
        System.out.println("array: " + grid.isArray() + ", name=" + grid.getName());
        System.out.println("component: " + grid.getComponentType().getTypeName());
        System.out.println("loaded int[]: " + (Class.forName("[I") == int[].class));

        System.out.println("interface: " + Payment.class.isInterface());
        System.out.println("implemented: " + Payment.class.isAssignableFrom(Card.class));
        System.out.println("enum: " + Level.class.isEnum());
        System.out.println("constants: " + Arrays.toString(Level.class.getEnumConstants()));
        System.out.println("record: " + Person.class.isRecord());
        for (RecordComponent component : Person.class.getRecordComponents()) {
            System.out.println("component: " + component.getName() + ":" + component.getType().getSimpleName()
                    + ", accessor=" + component.getAccessor().getName());
        }
        System.out.println("Enum base: " + Enum.class.isEnum());
        System.out.println("Record base: " + Record.class.isRecord());
    }
}
~~~

Biên dịch và chạy tại thư mục chứa tệp:

~~~bash
javac ReflectionClassKindsDemo.java
java ReflectionClassKindsDemo
~~~

Stdout chính xác (đã đối chiếu bằng Java 21, mã tương thích Java 17):

~~~text
int/void: true/true
int vs Integer.TYPE: true
wrapper distinct: true
array: true, name=[[Ljava.lang.String;
component: java.lang.String[]
loaded int[]: true
interface: true
implemented: true
enum: true
constants: [LOW, HIGH]
record: true
component: name:String, accessor=name
component: age:int, accessor=age
Enum base: false
Record base: false
~~~

Vì `Person` là record lồng bên trong lớp demo, tên nhị phân đầy đủ của nó có ký tự `$` (`ReflectionClassKindsDemo$Person`), nhưng `Person.class.getSimpleName()` vẫn là `Person`. Ví dụ cố ý dùng `getSimpleName()` cho component để output ổn định, tránh phụ thuộc package.

## 9. Áp dụng: phân loại kiểu trong công cụ reflection

Một công cụ khám phá schema, serializer hoặc trình hiển thị metadata có thể nhận `Class<?>` bất kỳ và quyết định cách xử lý. Thứ tự kiểm tra giúp tránh nhầm `void` với primitive có giá trị và annotation với interface thường:

~~~java
static String classify(Class<?> type) {
    if (type == void.class) return "void";
    if (type.isPrimitive()) return "primitive";
    if (type.isArray()) return "array";
    if (type.isAnnotation()) return "annotation";
    if (type.isInterface()) return "interface";
    if (type.isEnum()) return "enum";
    if (type.isRecord()) return "record";
    return "ordinary/other class";
}
~~~

Đặt phương thức này trong một class; đây là **fragment minh họa**, còn phần 8 là chương trình chạy độc lập. Nếu nhận `int[][]`, có thể duyệt `getComponentType()` nhiều lần để lấy cả số chiều lẫn kiểu phần tử. Nếu nhận enum, `getEnumConstants()` giúp lấy danh sách giá trị hợp lệ. Nếu nhận record, `getRecordComponents()` giúp lấy đúng danh sách thành phần theo thứ tự định nghĩa. Với interface, dùng `isAssignableFrom()` để kiểm tra các implementation phù hợp.

Không tự suy ra một kiểu là record chỉ vì nó có phương thức `name()` hoặc nhiều field `final`: phải dùng `isRecord()`. Tương tự, đừng xem mọi subclass của `Enum` là một enum declaration, và đừng nhận diện interface chỉ bằng modifier `abstract`.

## 10. Các nhầm lẫn thường gặp và tự kiểm tra

1. **Primitive khác wrapper:** `int.class` và `Integer.class` khác nhau. `new Integer(...)` cũng không cần thiết; boxing thành `Integer` không biến metadata thành `int.class`.
2. **`void` đặc biệt:** `void.class.isPrimitive()` là `true`, nhưng `void` không có instance hay mảng hợp lệ; so `type == void.class` khi cần phân biệt riêng.
3. **Mảng là một kiểu riêng:** `int[].class.isArray()` là `true`, `int[].class.isPrimitive()` là `false` và `getComponentType() == int.class` là `true`.
4. **Tên kiểu để load:** `Class.forName("int")` thất bại; `Class.forName("[I")` tìm được `int[]`. `getTypeName()` dành cho hiển thị dễ đọc; `getName()` của mảng mang dạng descriptor JVM.
5. **Hướng của quan hệ gán:** `Interface.class.isAssignableFrom(Implementation.class)` kiểm tra implementation có gán được cho interface; đảo chiều thường là `false`.
6. **Enum/record là kiểu khai báo cụ thể:** `Enum.class.isEnum()` và `Record.class.isRecord()` đều là `false`; muốn có constants hay components thì kiểm tra đúng class khai báo.
7. **Giá trị trả về của API:** `getEnumConstants()` và `getRecordComponents()` có thể trả `null` khi không đúng loại. Không duyệt trực tiếp trước khi kiểm tra.

Tự trả lời:

- `int[][].class.getComponentType() == int[].class` là đúng hay sai? **Đúng** vì mỗi lần lấy component chỉ bỏ một chiều.
- `void.class.isPrimitive()` có thể dùng để quyết định kiểu có thể chứa một giá trị primitive không? **Không**; `void` là ngoại lệ dù API trả `true`.
- Một class có các field `final` và accessor trông giống record thì `isRecord()` có chắc là `true`? **Không**; chỉ class thực sự khai báo bằng `record` mới thỏa điều kiện.
- Nếu nhận `Class<?>` từ plugin bên ngoài, cần phân biệt annotation interface với interface thường bằng gì? **`isAnnotation()` trước `isInterface()`**.

Tài liệu chuẩn để tra cứu thêm: [Javadoc `Class` (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html), [Javadoc `RecordComponent` (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/RecordComponent.html) và [JLS 17 — Record Classes](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.10).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-objects-008">Vì sao int.class khác Integer.class</a>

<details>
<summary>Xem chi tiết</summary>

# Vì sao `int.class` khác `Integer.class`?

Trong Java, `int` là **kiểu nguyên thủy** (primitive type), còn `Integer` là **lớp tham chiếu** (reference type) dùng để biểu diễn một giá trị số nguyên dưới dạng đối tượng. Vì vậy, `int.class` và `Integer.class` trả về **hai đối tượng `Class` khác nhau**, mô tả hai kiểu Java khác nhau. Cơ chế tự động đóng hộp (autoboxing) không biến chúng thành cùng một kiểu.

## 1. `.class` thực sự đại diện cho điều gì?

Trong Java, biểu thức `TênKiểu.class` là *class literal*: nó trả về đối tượng `java.lang.Class` đại diện cho kiểu được nêu. Cú pháp này áp dụng được cả cho lớp, interface, mảng và kiểu nguyên thủy.

- `int.class`: đối tượng `Class` đại diện cho **kiểu nguyên thủy `int`**. Dù tên API là `Class`, `int` không phải một lớp thông thường, không có đối tượng kiểu `int`, không có constructor hay superclass.
- `Integer.class`: đối tượng `Class` đại diện cho **lớp `java.lang.Integer`**. `Integer` là một lớp `final` kế thừa `Number`, dùng với các đối tượng và cung cấp phương thức như `parseInt`, `valueOf`, `compare`.
- Cả hai có kiểu biểu thức tương thích với `Class<?>`, nhưng mỗi đối tượng `Class` lưu thông tin về **một kiểu được biểu diễn riêng biệt**.

Điều này tương tự việc `String.class` khác `Object.class`: cùng được biểu diễn bằng API `Class` không có nghĩa là cùng một kiểu.

## 2. So sánh qua Reflection

| Phép kiểm tra | `int.class` | `Integer.class` |
| --- | --- | --- |
| Kiểu Java được mô tả | `int` nguyên thủy | `java.lang.Integer` tham chiếu |
| `getName()` | `"int"` | `"java.lang.Integer"` |
| `isPrimitive()` | `true` | `false` |
| `getSuperclass()` | `null` | `Number.class` |
| `isInstance(Integer.valueOf(10))` | `false` | `true` |
| Có thể dùng làm kiểu đối tượng trong `List<T>` | Không: `List<int>` không hợp lệ | Có: `List<Integer>` hợp lệ |
| Dùng trong chữ ký phương thức | Tham số/giá trị trả về `int` | Tham số/giá trị trả về `Integer` |

Một chi tiết thường gây nhầm lẫn là hằng `Integer.TYPE`: **`Integer.TYPE == int.class`**, trong khi **`Integer.TYPE != Integer.class`**. Trường `TYPE` của các lớp wrapper lưu đối tượng `Class` của kiểu nguyên thủy tương ứng; nó không phải một bí danh của `Integer.class`.

### Ví dụ Java 17+ chạy độc lập

Lưu toàn bộ mã sau trong tệp `IntVsIntegerClass.java`:

~~~java
import java.lang.reflect.Method;

public class IntVsIntegerClass {
    public static void accept(int value) {
    }

    public static void accept(Integer value) {
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Class<?> primitive = int.class;
        Class<?> wrapper = Integer.class;

        System.out.println(primitive == wrapper);
        System.out.println(primitive.getName());
        System.out.println(wrapper.getName());
        System.out.println(primitive.isPrimitive());
        System.out.println(wrapper.isPrimitive());
        System.out.println(primitive.getSuperclass());
        System.out.println(wrapper.getSuperclass().getName());
        System.out.println(Integer.TYPE == primitive);
        System.out.println(wrapper.isInstance(Integer.valueOf(10)));
        System.out.println(primitive.isInstance(Integer.valueOf(10)));

        Method primitiveMethod =
                IntVsIntegerClass.class.getMethod("accept", int.class);
        Method wrapperMethod =
                IntVsIntegerClass.class.getMethod("accept", Integer.class);

        System.out.println(primitiveMethod.equals(wrapperMethod));
        System.out.println(primitiveMethod.getParameterTypes()[0].getName());
        System.out.println(wrapperMethod.getParameterTypes()[0].getName());
    }
}
~~~

Biên dịch và chạy trong terminal có JDK 17 trở lên:

~~~bash
javac IntVsIntegerClass.java
java IntVsIntegerClass
~~~

Kết quả chuẩn:

~~~text
false
int
java.lang.Integer
true
false
null
java.lang.Number
true
true
false
false
int
java.lang.Integer
~~~

Giải thích những dòng trọng tâm:

1. `primitive == wrapper` là `false` vì hai đối tượng `Class` biểu diễn hai kiểu khác nhau.
2. `isPrimitive()` chỉ đúng cho `int.class`; `Integer.class` mô tả một lớp đối tượng thực sự.
3. `getSuperclass()` của kiểu nguyên thủy trả `null`; superclass trực tiếp của `Integer` là `Number`.
4. `isInstance(Object)` kiểm tra liệu **đối tượng truyền vào** có là instance của kiểu được mô tả hay không. Một đối tượng `Integer` không thể là instance của một kiểu nguyên thủy, nên `int.class.isInstance(...)` trả `false`.
5. Reflection tìm hai phương thức `accept` khác nhau: một nhận `int`, một nhận `Integer`. Chúng không trùng chữ ký.

## 3. Vì sao autoboxing không làm hai `Class` bằng nhau?

**Autoboxing** cho phép trình biên dịch chuyển một giá trị `int` thành một tham chiếu `Integer` khi ngữ cảnh cần kiểu wrapper; **unboxing** chuyển ngược lại thành giá trị nguyên thủy. Ví dụ:

~~~java
int number = 42;
Integer boxed = number;      // autoboxing, tương đương về ý nghĩa với Integer.valueOf(number)
int again = boxed;           // unboxing, tương đương boxed.intValue()
~~~

Đây là **phép chuyển đổi giá trị khi biên dịch/thực thi**, không phải quan hệ kế thừa, không tạo sự đồng nhất giữa hai kiểu và không thay đổi đối tượng `Class`. Bởi vậy:

- `int.class == Integer.class` vẫn là `false`.
- `Integer.class.isAssignableFrom(int.class)` là `false` và `int.class.isAssignableFrom(Integer.class)` cũng là `false`; API này kiểm tra khả năng gán theo quan hệ kiểu tham chiếu, không xem autoboxing là kế thừa.
- Biến `int` không có phương thức `getClass()`; viết `number.getClass()` khi `number` là `int` gây lỗi biên dịch. Muốn truy vấn kiểu nguyên thủy, dùng `int.class` hoặc `Integer.TYPE`.
- Khi `boxed` là một đối tượng `Integer`, `boxed.getClass() == Integer.class` là `true` (nếu `boxed` khác `null`).

Cần chú ý `Integer` có thể chứa `null`, còn `int` không thể. Unboxing giá trị `null` ném `NullPointerException`:

~~~java
Integer missing = null;
// int value = missing; // Nếu thực thi sẽ ném NullPointerException.
~~~

Ví dụ chú thích ở trên minh họa rủi ro, không phải một chương trình riêng.

## 4. Tác động trực tiếp khi dùng Reflection

### 4.1. Tra cứu method phải dùng đúng kiểu tham số đã khai báo

`Class.getMethod(String, Class<?>...)` và `Class.getDeclaredMethod(String, Class<?>...)` tra cứu **đúng chữ ký tham số**. Nếu có phương thức `process(int)`, lời gọi `getMethod("process", Integer.class)` không tự chuyển sang `getMethod("process", int.class)`.

Trong ví dụ độc lập ở mục 2, hai cách tìm dưới đây trỏ đến hai overload riêng:

~~~java
Method primitiveMethod =
        IntVsIntegerClass.class.getMethod("accept", int.class);
Method wrapperMethod =
        IntVsIntegerClass.class.getMethod("accept", Integer.class);
~~~

Nếu lớp chỉ khai báo overload `accept(int)`, truy vấn bằng `Integer.class` sẽ ném `NoSuchMethodException`. Quy tắc tương tự áp dụng khi so kiểu trả về bằng `Method.getReturnType()`, kiểu trường bằng `Field.getType()` và kiểu tham số bằng `Method.getParameterTypes()`.

**Phân biệt lúc tra cứu với lúc gọi:** `Method.invoke` có thể tự unbox một đối tượng `Integer` để truyền cho tham số `int` (và thực hiện một số chuyển đổi primitive hợp lệ) khi gọi phương thức đã tìm thấy. Điều đó không làm thay đổi chữ ký hay kết quả của `getMethod`.

### 4.2. Không dùng `Class.forName("int")` để lấy kiểu nguyên thủy

`Class.forName("java.lang.Integer")` tra cứu được lớp wrapper theo tên đầy đủ. Nhưng `Class.forName("int")` không nhận tên nguyên thủy theo cách đó và ném `ClassNotFoundException`. Khi cần `Class` của primitive, hãy lấy trực tiếp bằng `int.class` hoặc `Integer.TYPE`. Khi đọc một kiểu từ Reflection, dùng `isPrimitive()` để phân biệt.

### 4.3. Kiểu mảng cũng không giống nhau

`int[].class` và `Integer[].class` đều có `isArray() == true`, nhưng là hai lớp mảng khác nhau. `getComponentType()` lần lượt cho `int.class` và `Integer.class`. Một mảng `int[]` chứa phần tử primitive; một mảng `Integer[]` chứa tham chiếu đến các đối tượng `Integer` (có thể có phần tử `null`).

Không thể chuyển trực tiếp `int[]` thành `Integer[]` bằng autoboxing; muốn đổi phải chuyển từng phần tử.

## 5. Cách chọn đúng `Class` trong mã thực tế

- **Chữ ký khai báo primitive** (ví dụ `void setAge(int age)`, `int getCount()`, `Field` có kiểu `int`): dùng `int.class` khi so hoặc tra cứu qua Reflection.
- **Chữ ký khai báo wrapper** (ví dụ `void setAge(Integer age)`, `Integer findCount()`, trường có kiểu `Integer`): dùng `Integer.class`.
- **Framework/mapper xử lý cả hai dạng:** có thể viết logic quy đổi chủ động từ `int.class` sang `Integer.class` (và ngược lại) nếu bài toán muốn xem chúng tương ứng; đừng mặc định `Class.equals` hoặc `isAssignableFrom` thực hiện việc quy đổi này.
- **Cấu trúc dữ liệu generic:** dùng `Integer` vì type argument của generics phải là kiểu tham chiếu. Với dữ liệu số nguyên lớn cần tránh overhead đối tượng, mảng `int[]` có thể phù hợp hơn `Integer[]`; chọn theo yêu cầu về `null`, API và hiệu năng thực tế.

## 6. Lỗi thường gặp và cách tự kiểm tra

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “`int.class` là `Integer.class` sau boxing.” | Boxing chuyển **giá trị**, không thay danh tính kiểu. |
| “`Integer.TYPE` là `Integer.class`.” | `Integer.TYPE` chính là `int.class`. |
| “`getMethod` tự áp dụng boxing để tìm overload.” | Phải chỉ rõ `int.class` hoặc `Integer.class` theo chữ ký đã khai báo. |
| “Mọi giá trị `int` đều có `getClass()`.” | Primitive không phải object; dùng class literal. |
| “`isAssignableFrom` trả true vì Java tự boxing.” | Kiểm tra này không coi boxing là quan hệ gán kiểu tham chiếu. |
| “`int[].class` và `Integer[].class` giống nhau.” | Thành phần lần lượt là primitive và wrapper; hai loại mảng độc lập. |

Hãy tự dự đoán trước khi thử trên Java 17:

~~~java
System.out.println(int.class == Integer.TYPE);              // true
System.out.println(int.class.isPrimitive());                // true
System.out.println(Integer.class.isPrimitive());            // false
System.out.println(int[].class.getComponentType() == int.class); // true
System.out.println(Integer.class.isAssignableFrom(int.class));  // false
~~~

Đoạn cuối là các lệnh minh họa để đặt **bên trong một phương thức** (như `main`); nó không phải tệp Java độc lập.

**Ghi nhớ:** `int.class` nói về **kiểu giá trị nguyên thủy `int`**; `Integer.class` nói về **lớp đối tượng `Integer`**. Java hỗ trợ chuyển đổi giữa giá trị của chúng trong một số ngữ cảnh, nhưng Reflection vẫn phân biệt chính xác hai kiểu.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-objects-009">Không tạo Class bằng constructor thông thường</a>

<details>
<summary>Xem chi tiết</summary>

# Không tạo `Class` bằng constructor thông thường

## 1. Vấn đề nằm ở đâu?

Trong Java Reflection, `java.lang.Class<T>` là **đối tượng mô tả một kiểu tại runtime**: tên kiểu, modifier, superclass, interface, method, field, constructor, annotation, v.v. Mỗi biến kiểu `Class<?>` giữ một tham chiếu đến metadata của kiểu, không phải một đối tượng nghiệp vụ thuộc kiểu đó.

Ví dụ, ba biểu thức dưới đây có ý nghĩa khác nhau:

```java
Person person = new Person();        // Tạo một instance Person
Class<Person> type = Person.class;    // Lấy đối tượng Class mô tả Person
Class<?> runtimeType = person.getClass(); // Lấy Class của instance có sẵn
```

Nếu viết:

```java
Class<?> type = new Class<>(); // Lỗi biên dịch
```

thì Java không cho phép. **`java.lang.Class` không cung cấp constructor `public` để mã ứng dụng tự tạo một đối tượng `Class` bằng `new`.** JVM quản lý các đối tượng này trong quá trình nạp/định nghĩa kiểu. Tự tạo một `Class` tùy ý sẽ phá vỡ mối liên hệ giữa metadata và định nghĩa kiểu mà JVM thực sự biết, kiểm tra và thực thi.

Đây là điểm khác biệt quan trọng: **không được `new Class(...)`** không có nghĩa là **không được `new Person(...)`**. Ta vẫn khởi tạo instance của một lớp bình thường bằng constructor của chính lớp đó khi constructor cho phép.

## 2. Vì sao JVM quản lý đối tượng `Class`?

Khi runtime cần biết một kiểu, JVM sử dụng cơ chế nạp, liên kết và (khi điều kiện thích hợp xảy ra) khởi tạo lớp. Đối tượng `Class` là cửa ngõ phản ánh định nghĩa kiểu mà runtime đã nhận diện.

- `Class` biểu diễn **kiểu**, còn `new Person()` tạo **instance** của kiểu `Person`.
- Mã nguồn không thể tự cung cấp các thông tin nội bộ và tính hợp lệ của kiểu bằng cách gọi `new Class()`.
- Với cùng một kiểu được định nghĩa bởi cùng một **defining class loader**, các cách lấy `Class` thông thường tham chiếu cùng một đối tượng `Class`. Hai class loader khác nhau có thể định nghĩa hai kiểu riêng biệt dù cùng tên nhị phân.
- Nạp lớp (*loading*), liên kết (*linking*) và khởi tạo lớp (*initialization*) là các bước khác nhau; việc nhận được `Class` không luôn đồng nghĩa static initializer của kiểu đã chạy.

Hãy hiểu cụm “`Class` không có constructor thông thường” theo hợp đồng API: **không có constructor công khai cho ứng dụng gọi**. Không cần dựa vào số lượng hoặc chữ ký constructor nội bộ của implementation JDK để giải thích cơ chế này.

## 3. Các cách hợp lệ để lấy một đối tượng `Class`

| Cách | Ví dụ | Khi nào dùng? |
| --- | --- | --- |
| Class literal | `Person.class` | Đã biết kiểu tại thời điểm biên dịch; an toàn và rõ ràng. |
| `Object.getClass()` | `person.getClass()` | Đã có đối tượng và cần kiểu runtime thực tế của nó. |
| `Class.forName(String)` | `Class.forName("com.example.Person")` | Chỉ có tên nhị phân của kiểu dưới dạng chuỗi, thường gặp ở cấu hình/plugin; có thể ném `ClassNotFoundException`. |
| `Class.forName(String, boolean, ClassLoader)` | `Class.forName(name, false, loader)` | Cần chọn class loader và kiểm soát việc khởi tạo lớp. |
| `ClassLoader.loadClass(String)` | `loader.loadClass(name)` | Muốn yêu cầu class loader đã chọn tìm/nạp kiểu; thông thường không chủ động khởi tạo lớp ở bước này. |

Cần phân biệt:

- `Person.class` là **class literal**, không phải lời gọi constructor, không tạo `Person` và bản thân biểu thức đó không kích hoạt khởi tạo `Person`.
- `person.getClass()` trả về kiểu runtime thực sự. Với `Person` có subclass, giá trị này có thể khác `Person.class`. Nếu `person == null`, lời gọi gây `NullPointerException`.
- `Class.forName(String)` dùng tên nhị phân (*binary name*) và mặc định yêu cầu khởi tạo lớp được tìm thấy; tùy trường hợp có thể phát sinh lỗi nạp/khởi tạo. Dạng ba tham số với `initialize = false` tránh yêu cầu khởi tạo tại lời gọi đó.
- `ClassLoader.loadClass` và `Class.forName` xử lý tên kiểu và loader; chúng **không gọi constructor instance** của lớp được mô tả.
- Khi biết kiểu qua chuỗi, cần xử lý trường hợp không tìm thấy lớp; không suy luận rằng một tên chuỗi bất kỳ đều hợp lệ.

## 4. Ví dụ chạy được: ba đường dẫn về cùng metadata

Lưu nội dung sau thành **`ClassMetadataDemo.java`**:

```java
public class ClassMetadataDemo {
    static class Person {
        final String name;

        Person(String name) {
            this.name = name;
        }
    }

    public static void main(String[] args) throws ClassNotFoundException {
        Class<Person> fromLiteral = Person.class;

        Person person = new Person("Lan");
        Class<?> fromObject = person.getClass();

        // getName() trả về tên nhị phân chính xác,
        // kể cả khi Person là nested class.
        Class<?> fromName = Class.forName(Person.class.getName());

        System.out.println(fromLiteral == fromObject);
        System.out.println(fromLiteral == fromName);
        System.out.println(fromLiteral.getName());
        System.out.println(person.name);
    }
}
```

Biên dịch và chạy bằng Java 17+:

```bash
javac ClassMetadataDemo.java
java ClassMetadataDemo
```

Kết quả chuẩn:

```text
true
true
ClassMetadataDemo$Person
Lan
```

Giải thích theo thứ tự:

1. `Person.class` lấy metadata của `Person`; không thực thi `new Person(...)`.
2. `new Person("Lan")` tạo instance. `person.getClass()` lấy metadata của instance ấy.
3. `Class.forName(Person.class.getName())` tìm đúng kiểu bằng tên nhị phân (`$` phân tách nested class trong tên nhị phân).
4. Ba biến `fromLiteral`, `fromObject` và `fromName` đều tham chiếu cùng một đối tượng `Class` trong ví dụ này, vì cùng kiểu và cùng defining class loader. Toán tử `==` kiểm tra đồng nhất tham chiếu.
5. Dòng cuối đọc trường `name` từ **instance `Person`**, không phải từ `Class<Person>`.

Nếu `Person` được khai báo trong package, tên nhị phân có thêm package ở đầu; dùng `Person.class.getName()` giúp tránh nhầm tên nguồn và tên nhị phân.

## 5. Nhận `Class` không phải là tạo instance của kiểu được mô tả

Reflection cho phép lấy constructor **của kiểu được mô tả** rồi gọi constructor đó để tạo instance, nếu quyền truy cập và kiểu cho phép. Đây là một thao tác hoàn toàn khác với `new Class(...)`.

Lưu thành **`ReflectiveConstructionDemo.java`**:

```java
public class ReflectiveConstructionDemo {
    public static class Greeting {
        public Greeting() {
        }

        public String say() {
            return "Xin chào";
        }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Class<Greeting> type = Greeting.class;

        // Constructor<Greeting> mô tả constructor của Greeting.
        Greeting instance = type.getDeclaredConstructor().newInstance();

        System.out.println(type.getSimpleName());
        System.out.println(instance.say());
        System.out.println(instance.getClass() == type);
    }
}
```

```bash
javac ReflectiveConstructionDemo.java
java ReflectiveConstructionDemo
```

Kết quả:

```text
Greeting
Xin chào
true
```

Ở đây:

- `Greeting.class` trả về `Class<Greeting>`.
- `getDeclaredConstructor()` lấy metadata của constructor không tham số thuộc `Greeting`.
- `Constructor.newInstance()` tạo **một `Greeting` mới**, không tạo một `Class` mới.
- Ví dụ dùng constructor `public` để không vướng hạn chế truy cập.

Khi áp dụng thực tế, cần có chữ ký constructor phù hợp; constructor có thể không tồn tại, không truy cập được, ném exception, hoặc kiểu có thể không khởi tạo trực tiếp được (chẳng hạn interface/abstract class). Tránh dùng `Class.newInstance()`: API này đã deprecated; dùng `getDeclaredConstructor(...).newInstance(...)` để xử lý constructor và exception rõ ràng hơn.

## 6. Lấy metadata không đồng nghĩa với khởi tạo lớp

Class literal như `LazyType.class` chỉ lấy đối tượng mô tả kiểu; nó không tự chạy static initializer của `LazyType`. Trái lại, `Class.forName(LazyType.class.getName())` yêu cầu khởi tạo lớp nếu chưa khởi tạo. Nếu cần lấy metadata theo tên mà chưa chủ động khởi tạo, dùng `Class.forName(name, false, loader)` với loader phù hợp.

Cả hai cách vẫn trả về **đối tượng `Class`**, và việc khởi tạo lớp (chạy mã `static`) vẫn khác với **tạo instance** bằng `new LazyType()` hoặc gọi constructor qua Reflection. Các trình tự và ngoại lệ khởi tạo thuộc bài riêng về vòng đời lớp.

## 7. `Class` cũng mô tả kiểu không có constructor instance thông thường

`Class` không chỉ dùng cho lớp có thể `new`:

```java
Class<?> primitiveType = int.class;
Class<?> voidType = void.class;
Class<?> arrayType = String[].class;
Class<?> interfaceType = Runnable.class;
```

- `int.class`, `void.class` và `String[].class` đều là các đối tượng `Class` hợp lệ.
- `Runnable.class` mô tả một interface; interface không có instance constructor để gọi bằng `new Runnable()`.
- Không được suy ra từ việc có `Class<?>` rằng kiểu đó có một constructor không tham số hay có thể tạo instance trực tiếp.
- `Class.forName("int")` không phải cách lấy `int.class`; tên primitive không được tìm theo cách đó.
- Do type erasure, `List<String>.class` là cú pháp không hợp lệ; dùng `List.class`. Thông tin type argument cụ thể có thể cần phân tích ở `Type`/`ParameterizedType` trong ngữ cảnh thích hợp, không nằm trong một `Class<List<String>>` riêng biệt.

Điều này càng cho thấy `Class` là đại diện cho **một kiểu runtime**, không phải một lớp do ứng dụng tự dựng bằng constructor.

## 8. Lỗi hay gặp và cách sửa

| Viết sai hoặc hiểu sai | Vấn đề | Cách đúng |
| --- | --- | --- |
| `new Class<>()` | `Class` không có constructor công khai cho ứng dụng. | Dùng `SomeType.class`, `obj.getClass()` hoặc `Class.forName(...)`. |
| Dùng `SomeType.class` rồi mong đã có instance `SomeType` | Class literal chỉ lấy metadata kiểu. | Gọi `new SomeType(...)` hoặc constructor reflection khi cần instance. |
| Dùng `Class.forName("Person")` cho nested/packaged type mà không có tên nhị phân phù hợp | Không tìm thấy class theo tên cung cấp. | Dùng tên nhị phân đầy đủ hoặc lấy `Person.class.getName()`. |
| Gọi `value.getClass()` khi `value` null | `NullPointerException`. | Kiểm tra null hoặc dùng `SomeType.class` nếu biết kiểu. |
| Giả định `Class.forName(...)` luôn an toàn và không có side effect | Bản một tham số có thể kích hoạt khởi tạo lớp và exception liên quan. | Xử lý lỗi, cân nhắc overload ba tham số khi cần kiểm soát initialization. |
| Gọi `getDeclaredConstructor().newInstance()` với mọi `Class<?>` | Có thể không có constructor phù hợp, không đủ quyền truy cập hoặc kiểu không thể được tạo trực tiếp. | Kiểm tra loại kiểu, constructor, quyền truy cập và xử lý `ReflectiveOperationException` thích hợp. |
| Dùng `Class.newInstance()` | API cũ deprecated, kém rõ ràng về constructor và lỗi phát sinh. | Dùng `getDeclaredConstructor(...).newInstance(...)`. |
| Xem cùng tên class là cùng một kiểu ở mọi class loader | Danh tính runtime của kiểu còn phụ thuộc defining class loader. | Kiểm tra loader/kiểu thực tế khi làm plugin, module runtime hoặc custom loading. |

## 9. Cách chọn API trong thực tế

- **Biết trước kiểu**: ưu tiên `Foo.class`; compiler kiểm tra tên kiểu và có generic type phù hợp, ví dụ `Class<Foo>`.
- **Đã có đối tượng**: dùng `obj.getClass()` để biết chính xác runtime type, đặc biệt khi có kế thừa/đa hình.
- **Có tên kiểu từ cấu hình**: dùng `Class.forName(...)`, đồng thời kiểm soát dữ liệu đầu vào, class loader, thời điểm initialization và exception.
- **Cần tạo instance động**: trước hết lấy `Class`, sau đó tìm và gọi constructor **của kiểu được mô tả**; không tìm cách tạo thêm một `Class` bằng `new`.

## 10. Tự kiểm tra

1. Vì sao `Person.class` trả về một `Class<Person>` mà không cần `new Class<Person>()`?
2. `Person.class` và `new Person().getClass()` giống nhau trong điều kiện nào? Khi nào biến kiểu `Person` trỏ tới subclass thì kết quả có thể khác?
3. `Class.forName(String)` khác `Person.class` thế nào về cách xác định tên và khả năng kích hoạt static initialization?
4. Gọi `type.getDeclaredConstructor().newInstance()` tạo đối tượng thuộc kiểu nào?
5. Vì sao `int.class` và `Runnable.class` là giá trị hợp lệ dù không thể gọi constructor instance thông thường?

**Ghi nhớ:** `Class` là đối tượng metadata do JVM/runtime quản lý. Ta **lấy tham chiếu `Class` qua class literal, instance, tên kiểu hoặc class loader**, rồi dùng metadata để tìm hiểu hay thao tác với kiểu. Ta **không tạo đối tượng `Class` bằng constructor thông thường**; constructor reflection (nếu phù hợp) dùng để tạo **instance của kiểu được mô tả**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="class-objects-010">Phân biệt đối tượng Class với instance của kiểu được mô tả</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt đối tượng `Class` với instance của kiểu được mô tả trong Java

## 1. Hai khái niệm liên quan nhưng không đồng nhất

Trong Java, một **instance (đối tượng thể hiện)** là đối tượng được tạo ra từ một lớp hoặc một kiểu cụ thể ở thời gian chạy. Nó mang trạng thái riêng qua các trường instance và có thể thực hiện hành vi được định nghĩa bởi kiểu đó. Ví dụ, `new Person("An")` tạo một đối tượng `Person`.

Một **đối tượng `Class`** là đối tượng thuộc kiểu `java.lang.Class`, được JVM cung cấp để **mô tả một kiểu ở thời gian chạy**: tên kiểu, superclass, interface, constructor, field, method, annotation, v.v. Ví dụ, `Person.class` là đối tượng `Class<Person>` đại diện cho kiểu `Person`. Nó không phải là một `Person` và không chứa trạng thái `name` riêng của từng `Person`.

Ba tầng cần phân biệt:

- **Khai báo kiểu:** `class Person { ... }` trong mã nguồn quy định cấu trúc và hành vi.
- **Đối tượng `Class` của kiểu:** `Person.class` là thông tin mô tả kiểu `Person` mà chương trình có thể truy vấn khi chạy.
- **Instance của kiểu:** `new Person("An")` hoặc `new Person("Bình")` là các đối tượng `Person` cụ thể, có trạng thái độc lập.

Tên `Class` dễ gây nhầm lẫn: `Person.class` là **một đối tượng Java**, nhưng **không phải instance của `Person`**; bản thân nó là instance của `java.lang.Class`. Biểu thức `Class.class` lại là đối tượng `Class` mô tả chính kiểu `java.lang.Class`.

| Tiêu chí | Đối tượng `Class` mô tả `Person` | Instance `Person` |
| --- | --- | --- |
| Ví dụ | `Person.class`, `person.getClass()` | `new Person("An")` |
| Kiểu của bản thân đối tượng | `java.lang.Class` | `Person` (hoặc lớp con cụ thể) |
| Nó biểu diễn gì? | Một kiểu được JVM nhận biết | Một đối tượng dữ liệu cụ thể |
| Trạng thái | Thông tin/khả năng truy vấn cấu trúc kiểu | Giá trị trường instance, ví dụ `name` |
| Số lượng | Cùng một kiểu do cùng class loader định nghĩa thường dùng chung một `Class` | Có thể tạo nhiều instance của cùng kiểu |
| Cách lấy | `T.class`, `obj.getClass()`, `Class.forName(...)` | `new`, factory method, reflection, v.v. |
| Dùng để | Kiểm tra kiểu, tìm constructor/method/field, hỗ trợ reflection | Gọi hành vi và làm việc với dữ liệu cụ thể |

**Chốt phân biệt:** `Class` trả lời “đối tượng này thuộc **kiểu nào**, kiểu ấy được mô tả ra sao?”; instance trả lời “**đối tượng cụ thể nào**, đang mang dữ liệu gì?”. Hai instance khác nhau có thể tham chiếu đến cùng một đối tượng `Class`.

## 2. Ví dụ Java 17+ chạy được

Lưu đúng tên tệp `ClassVsInstanceDemo.java`:

```java
import java.lang.reflect.Method;

public class ClassVsInstanceDemo {
    public static class Person {
        private final String name;

        public Person(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        Class<Person> type = Person.class;
        Person an = new Person("An");
        Person binh = new Person("Bình");
        Class<? extends Person> runtimeType = an.getClass();

        System.out.println(type == runtimeType);
        System.out.println(an == binh);
        System.out.println(type.isInstance(an));
        System.out.println(type.isInstance("An"));
        System.out.println(type.getSimpleName());
        System.out.println(type.getClass() == Class.class);

        Method nameMethod = type.getMethod("name");
        System.out.println(nameMethod.invoke(an));
        System.out.println(nameMethod.invoke(binh));
    }
}
```

Biên dịch và chạy:

```bash
javac -encoding UTF-8 ClassVsInstanceDemo.java
java ClassVsInstanceDemo
```

Kết quả chuẩn:

```text
true
false
true
false
Person
true
An
Bình
```

Giải thích lần lượt:

1. `type == runtimeType` là `true`: `Person.class` và `an.getClass()` trả cùng đối tượng `Class` khi `an` thực sự thuộc `Person`.
2. `an == binh` là `false`: hai lần `new` tạo hai instance khác nhau; cả hai đều được mô tả bởi kiểu `Person`.
3. `type.isInstance(an)` là `true`, còn `type.isInstance("An")` là `false`: đây là phép kiểm tra một **giá trị đối tượng** có thuộc kiểu được mô tả hay không.
4. `getSimpleName()` trả `Person`, là thuộc tính của kiểu; nó không đọc dữ liệu `name` của instance.
5. `type.getClass() == Class.class` là `true`: đối tượng `type` thuộc lớp `Class`, dù nó đang mô tả lớp `Person`.
6. `getMethod("name")` tìm mô tả method chung của kiểu. `invoke(an)` và `invoke(binh)` thực thi method ấy trên **hai receiver khác nhau**, nên trả `An` và `Bình`.

Trong ví dụ, `Class<Person>` giúp biểu thị đối tượng `Class` dành cho `Person` ở thời gian biên dịch. `Class<? extends Person>` dành cho `getClass()` vì một tham chiếu kiểu `Person` cũng có thể trỏ đến instance của lớp con; kiểu thực tế khi chạy được quyết định bởi đối tượng, không chỉ bởi khai báo biến.

## 3. Lấy đối tượng `Class` theo nhu cầu

| Tình huống | Cách lấy | Điều quan trọng |
| --- | --- | --- |
| Biết sẵn kiểu trong source | `Person.class` | Không cần tạo `Person` chỉ để đọc metadata. |
| Có một instance | `person.getClass()` | Phản ánh lớp runtime thực tế; `null` gây `NullPointerException`. |
| Chỉ có tên kiểu ở runtime | `Class.forName("demo.Person")` | Cần binary name, loader phù hợp, xử lý lỗi nạp; dạng một tham số yêu cầu khởi tạo. |

Ví dụ `Object value = "hello";` thì `value.getClass() == String.class` là `true`, còn `value.getClass() == Object.class` là `false`: kiểu khai báo biến không thay lớp runtime của instance. Đối với `int` hay `void`, có thể lấy `int.class`/`void.class` dù không có instance đối tượng tương ứng.

Mọi đường trên đều lấy metadata về kiểu; chúng không mang dữ liệu riêng của các instance. Cách chọn giữa class literal, `getClass()` và `Class.forName` được thực hành riêng ở những bài tương ứng.

## 4. `Class` mô tả kiểu; thao tác dữ liệu vẫn cần instance

Reflection thường đi theo hai bước: **tìm thành viên qua `Class`**, rồi **thao tác trên receiver cụ thể** nếu đó là thành viên instance.

- `Person.class.getDeclaredFields()` tìm các field được **khai báo trực tiếp** trong `Person` (không tự động gồm field kế thừa). Mỗi `Field` là mô tả một thành viên, không phải giá trị field của từng `Person`.
- `Person.class.getMethod("name")` tìm một method `public` phù hợp, kể cả method `public` kế thừa. `getDeclaredMethod(...)` chỉ tìm method được khai báo trong chính lớp, bất kể mức truy cập.
- `Method.invoke(person)` cần một receiver `person` phù hợp cho method instance; receiver khác có thể tạo kết quả khác. Với method static, không có receiver instance.
- `Person.class.getConstructor(String.class)` tìm constructor công khai. Khi thực sự cần tạo instance bằng reflection, có thể gọi `getConstructor(String.class).newInstance("Chi")`, với xử lý ngoại lệ và quyền truy cập thích hợp. Đối tượng `Class` tự nó không biến thành instance `Person`.

Không nhầm `Field`, `Method`, `Constructor` (các đối tượng mô tả thành viên) với giá trị hoặc hoạt động diễn ra trên một instance. Quyền truy cập Java/module vẫn áp dụng khi truy cập phản chiếu; tìm thấy metadata không bảo đảm gọi hay đọc/ghi được mọi thành viên.

**Trường static là trường hợp riêng:** dữ liệu static gắn với lớp trong ngữ cảnh runtime của nó, không có một bản sao cho mỗi instance. Điều này không biến `Person.class` thành một `Person`, cũng không làm cho các field instance trở thành dữ liệu của `Class`.

## 5. Kiểm tra kiểu: `==`, `isInstance`, `isAssignableFrom`

Ba câu hỏi khác nhau dẫn đến ba phép kiểm tra khác nhau:

| Biểu thức | Hỏi điều gì? | Ví dụ kết quả |
| --- | --- | --- |
| `a.getClass() == b.getClass()` | Hai instance có **cùng kiểu runtime chính xác** không? | Có thể `true` dù `a != b` |
| `type.isInstance(obj)` | `obj` có thuộc kiểu được `type` mô tả, kể cả subtype, không? | `Number.class.isInstance(Integer.valueOf(7))` → `true` |
| `parent.isAssignableFrom(child)` | Giá trị của kiểu `child` có thể gán cho biến kiểu `parent` không? | `Number.class.isAssignableFrom(Integer.class)` → `true` |

`==` giữa hai đối tượng `Class` kiểm tra **định danh của kiểu runtime**; `==` giữa hai instance kiểm tra **định danh đối tượng**. `isInstance` làm việc với một giá trị/instance, còn `isAssignableFrom` làm việc với hai đối tượng mô tả **kiểu**. Với kiểu tham chiếu, `isInstance(obj)` thường tương ứng ý nghĩa với `obj instanceof SomeType` (khi `SomeType` biết sẵn trong mã).

Ví dụ khác: một `ArrayList` là instance của `List` theo quan hệ interface, nhưng `list.getClass() == List.class` là `false` vì `getClass()` trả về lớp triển khai thực tế, không phải interface. Vì vậy, dùng `getClass() == T.class` khi cần *đúng kiểu chính xác*; dùng `instanceof` hoặc `isInstance` khi muốn chấp nhận cả kiểu con/kiểu triển khai.

## 6. Các trường hợp dễ gây nhầm lẫn

### Một `Class` có thể đại diện cho kiểu không tạo được instance thông thường

`int.class` và `void.class` là đối tượng `Class`, nhưng không tồn tại instance đối tượng Java tương ứng với `int` hay `void` theo nghĩa dùng `new`. Nếu autoboxing xảy ra, `Integer.valueOf(5).getClass()` trả `Integer.class`, **không phải** `int.class`.

Mảng là một kiểu tham chiếu với đối tượng mảng thực sự: `new String[2].getClass() == String[].class` là `true`. Interface/abstract class cũng có đối tượng `Class` dù không được khởi tạo trực tiếp. Enum, record và annotation type đều có đối tượng `Class` mô tả kiểu.

### “Một `Class` cho mỗi lớp” cần hiểu kèm class loader

Trong cùng một định nghĩa runtime, các instance cùng kiểu chia sẻ đối tượng `Class` của định nghĩa ấy. Nhưng JVM có thể nạp **cùng tên nhị phân** qua **các class loader định nghĩa khác nhau**, tạo ra các kiểu runtime khác nhau và các đối tượng `Class` khác nhau. Vì thế, **tên lớp bằng nhau chưa đủ để khẳng định hai `Class` là cùng một kiểu runtime**. Ngược lại, số instance tăng lên không tạo thêm một đối tượng `Class` mới cho mỗi instance.

### Generics không tạo kiểu runtime riêng cho mỗi đối số kiểu

`new java.util.ArrayList<String>()` và `new java.util.ArrayList<Integer>()` có cùng lớp runtime là `java.util.ArrayList`. Do **type erasure**, `getClass()` của hai đối tượng không cho biết phần tử là `String` hay `Integer`; thông tin kiểu tổng quát trong một khai báo field, method, superclass... nếu còn được lưu trong metadata cần được kiểm tra bằng API generic reflection tương ứng, chứ không suy ra từ `getClass()` của một `ArrayList`.

### `Class<T>` và instance `T` không thay thế được cho nhau

`Class<Person>` không phải `Person`. Không truyền `Person.class` vào API đang yêu cầu một instance `Person`; cũng không dùng `new Person(...)` ở vị trí yêu cầu đối tượng mô tả kiểu `Class<Person>`. Khi cần ép kiểu một giá trị, `Person.class.cast(obj)` kiểm tra/ép giá trị về `Person` ở runtime và có thể ném `ClassCastException`; nó **không tạo instance**. `Person.class.isInstance(obj)` chỉ kiểm tra và trả boolean.

## 7. Quy trình chọn API đúng khi viết code

1. **Đã biết kiểu ở mã nguồn và cần metadata?** Dùng `Person.class`; không cần tạo `Person` giả chỉ để gọi `getClass()`.
2. **Có một object và cần đúng lớp thực tế của nó?** Dùng `obj.getClass()` sau khi bảo đảm `obj != null`.
3. **Có tên kiểu do cấu hình/plugin cung cấp?** Cân nhắc `Class.forName(...)`, class loader/module và lỗi nạp/khởi tạo.
4. **Cần lấy method/field/constructor?** Tìm metadata qua `Class`; khi thao tác thành viên instance, cung cấp receiver và tuân thủ quyền truy cập.
5. **Cần kiểm tra object có phù hợp một kiểu?** Dùng `instanceof` hoặc `Class.isInstance`; khi cần xét quan hệ giữa hai kiểu, dùng `Class.isAssignableFrom`; khi cần so đúng kiểu runtime, so `Class` bằng `==`.

Tránh các lỗi sau: nghĩ `Person.class` chứa dữ liệu của một người cụ thể; tưởng hai lần `new Person(...)` tạo hai `Class` khác nhau; đánh đồng kiểu khai báo của biến với `getClass()` của giá trị; dùng so sánh `getClass() == Interface.class` để kiểm tra interface; gọi `getClass()` trên `null`; giả định lấy được metadata thì có thể truy cập thành viên private bất chấp module và access control.

## 8. Tự kiểm tra

**Câu 1.** `Person p1 = new Person("A"); Person p2 = new Person("B");` — `p1 == p2` và `p1.getClass() == p2.getClass()` là gì?

**Đáp án:** Lần lượt `false` và `true` (nếu hai biến giữ hai instance `Person` được tạo như trên trong cùng định nghĩa kiểu). Hai đối tượng khác nhau nhưng chung lớp runtime.

**Câu 2.** Vì sao một biến khai báo `Object x = "text";` lại có `x.getClass() == String.class`?

**Đáp án:** `getClass()` trả kiểu **thực tế của instance** (`String`), không trả kiểu khai báo của biến (`Object`).

**Câu 3.** Có thể dùng `Person.class.getMethod("name")` để tìm method và suy ra ngay tên của mọi instance `Person` không?

**Đáp án:** Không. `Method` mô tả method của kiểu. Muốn biết tên từng người phải gọi trên receiver phù hợp, ví dụ `method.invoke(p1)` và `method.invoke(p2)`; kết quả có thể khác nhau.

Khi hiểu rằng **`Class` là đối tượng mô tả kiểu**, còn **instance là đối tượng mang trạng thái và hành vi cụ thể**, các thao tác reflection, kiểm tra kiểu và đa hình trong Java sẽ nhất quán hơn.

</details>

- [Quay lại đầu trang](#back-to-top)
