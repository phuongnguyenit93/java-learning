<a id="back-to-top"></a>

# Method

## Menu
- [Method metadata: tên, lớp khai báo và kiểu trả về](#methods-001)
- [Kiểu tham số, kiểu throws và các biến thể generic](#methods-002)
- [getMethods khác getDeclaredMethods](#methods-003)
- [Overload và chữ ký cần dùng để tìm phương thức](#methods-004)
- [invoke với receiver và mảng đối số](#methods-005)
- [Gọi phương thức static bằng receiver null](#methods-006)
- [Liên kết động khi Method của lớp cha được gọi trên lớp con](#methods-007)
- [Phân biệt gọi virtual với mong muốn gọi riêng triển khai super](#methods-008)
- [Primitive return được boxing và void trả về null](#methods-009)
- [Phương thức abstract, default và phương thức interface](#methods-010)
- [Method.invoke không tự chờ Future hoặc CompletionStage trả về](#methods-011)
- [Không nhầm Method object với kết quả gọi phương thức](#methods-012)

## <a id="methods-001">Method metadata: tên, lớp khai báo và kiểu trả về</a>

<details>
<summary>Xem chi tiết</summary>

# Method metadata: tên, lớp khai báo và kiểu trả về trong Java Reflection

Một đối tượng `java.lang.reflect.Method` mô tả **một phương thức** của một lớp hoặc interface. Khi viết công cụ phân tích mã, framework, mapper, bộ sinh tài liệu hay công cụ kiểm thử, ba thông tin thường cần đọc trước tiên là:

- **Tên phương thức**: `method.getName()`.
- **Lớp hoặc interface khai báo phương thức**: `method.getDeclaringClass()`.
- **Kiểu trả về được khai báo**: `method.getReturnType()`.

Ba thuộc tính này là **metadata về phương thức**, không phải kết quả chạy phương thức. Chúng không cần gọi `Method.invoke()` để có giá trị. Tuy nhiên, rất dễ hiểu sai “lớp khai báo” thành “lớp đang được truy vấn” hoặc “lớp của đối tượng nhận”, và hiểu sai “kiểu trả về” thành “kiểu runtime của giá trị được trả về”. Bài học làm rõ các ranh giới đó.

## 1. Method là gì và lấy Method ở đâu?

`Method` thuộc package `java.lang.reflect`, biểu diễn thông tin về phương thức đã được JVM nạp. Thường lấy nó từ một đối tượng `Class<?>`:

```java
Method publicMethod = Example.class.getMethod("run");
Method declaredMethod = Example.class.getDeclaredMethod("run");
```

Đây là đoạn mã minh họa trong ngữ cảnh đã có lớp `Example` với phương thức phù hợp và đã import `Method`; hai lời gọi có thể ném `NoSuchMethodException`.

Hai API tra cứu không đồng nghĩa:

| API của `Class` | Phạm vi tìm |
| --- | --- |
| `getMethod(name, parameterTypes...)` | Phương thức **public** của kiểu, có thể bao gồm phương thức thừa kế từ superclass/superinterface |
| `getDeclaredMethod(name, parameterTypes...)` | Phương thức **được khai báo trực tiếp tại chính kiểu**, bất kể `public`, `protected`, package-private hay `private`; không tìm trong superclass |
| `getMethods()` | Mảng các phương thức public của kiểu và các phương thức được thừa kế theo hợp đồng API |
| `getDeclaredMethods()` | Mảng phương thức được khai báo trực tiếp tại kiểu, không gồm các phương thức thừa kế |

Thứ tự các phần tử của `getMethods()` và `getDeclaredMethods()` **không được bảo đảm là thứ tự khai báo trong source**. Với một phương thức cần xác định chính xác, nên tìm bằng tên và danh sách kiểu tham số; với nhiều phương thức cần trình bày ổn định, tự sắp xếp theo một khóa thích hợp.

Lấy được metadata của một phương thức `private` qua `getDeclaredMethod` **không** tự động cấp quyền gọi phương thức ấy. Việc kiểm tra quyền truy cập khi `invoke` là vấn đề riêng.

## 2. Ba API metadata cốt lõi

### 2.1. getName(): tên đơn giản của phương thức

`String name = method.getName();` trả về **tên phương thức**, ví dụ `"save"`, `"count"`, `"toString"`.

Giá trị chỉ là tên:

- Không chứa tên package hay declaring class.
- Không bao gồm dấu ngoặc tròn, danh sách tham số hoặc kiểu trả về.
- Không đảm bảo phương thức là duy nhất: các overload `parse(String)` và `parse(int)` đều trả `"parse"`.

Muốn định danh overload, kết hợp `getName()` với `getParameterTypes()` (thứ tự tham số là quan trọng). Khi cần thêm metadata cho tài liệu, có thể sử dụng `getModifiers()`, `getGenericParameterTypes()`, `getAnnotations()`... tùy mục đích, nhưng **tên đơn thuần chưa tạo thành chữ ký đầy đủ**.

### 2.2. getDeclaringClass(): kiểu thực sự khai báo phương thức

`Class<?> owner = method.getDeclaringClass();` trả về đối tượng `Class` của **lớp hoặc interface khai báo** phương thức mà `Method` đó biểu diễn.

Ví dụ:

```java
class Parent {
    public void work() {}
}
class Child extends Parent {}
```

Đây là mã minh họa; giả định hai lớp ở ngữ cảnh truy cập hợp lệ.

Nếu tra cứu `Child.class.getMethod("work")`, ta được metadata của phương thức `work` **khai báo trong `Parent`**. Bởi vậy:

```java
Method m = Child.class.getMethod("work");
System.out.println(m.getDeclaringClass() == Parent.class); // true
```

Mặc dù bắt đầu bằng `Child.class`, kết quả vẫn là `Parent.class`, vì `Child` **thừa kế** phương thức chứ không khai báo phương thức mới.

Cần phân biệt ba khái niệm:

| Khái niệm | Cách lấy | Trong ví dụ Child thừa kế Parent.work() |
| --- | --- | --- |
| Kiểu được dùng để bắt đầu truy vấn | Đối tượng `Class` gọi `getMethod` | `Child.class` |
| Kiểu khai báo phương thức được phản chiếu | `Method.getDeclaringClass()` | `Parent.class` |
| Kiểu runtime của receiver khi sử dụng | `receiver.getClass()` | Có thể là `Child.class` hoặc subtype khác |

**Đừng nhầm** `Method.getDeclaringClass()` với `Class.getDeclaringClass()`: cùng tên nhưng gọi trên hai đối tượng khác loại. `Class.getDeclaringClass()` nói về *lớp bên ngoài khai báo một member class*, không phải chủ sở hữu của một phương thức.

Với method được khai báo trong interface, `getDeclaringClass()` có thể trả chính **interface** ấy. Với method được override, một `Method` thuộc lớp con sẽ trả lớp con. Nếu lấy một `Method` trực tiếp từ lớp cha, metadata vẫn có owner là lớp cha ngay cả khi thực thi trên receiver lớp con sử dụng dynamic dispatch.

### 2.3. getReturnType(): kiểu trả về hình thức

`Class<?> returnType = method.getReturnType();` trả về một đối tượng `Class` biểu diễn **kiểu trả về theo khai báo** của phương thức đó.

Ví dụ:

| Khai báo | `getReturnType()` |
| --- | --- |
| `int count()` | `int.class` |
| `String name()` | `String.class` |
| `void reset()` | `void.class` |
| `String[] labels()` | `String[].class` |
| `List<String> tags()` | `List.class` |

**Điểm quan trọng:** `void` được biểu diễn bằng `void.class` (hoặc `Void.TYPE`), **không** phải `null` và cũng không phải `Void.class`. `Void.class` là một lớp wrapper đặc biệt, khác với kiểu `void` của khai báo method.

Khi method khai báo kiểu tham chiếu `Number` nhưng ở runtime trả đối tượng `Integer`, `getReturnType()` vẫn trả `Number.class`: metadata mô tả **khai báo**, không quan sát hoặc chạy method để suy ra kiểu của giá trị. Với phương thức generic như `List<String> tags()`, `getReturnType()` trả `List.class` sau **type erasure**, không lưu được type argument `String` trong chính đối tượng `Class` đó.

## 3. Ví dụ Java 17+ đầy đủ, chạy độc lập

Ví dụ sau cố tình có phương thức thừa kế, override, generic return type, `void`, primitive return type và overload. Nó truy vấn từng method theo tên/chữ ký, nên đầu ra không phụ thuộc thứ tự mảng Reflection.

Tạo tệp `MethodMetadataDemo.java`:

```java
import java.lang.reflect.Method;
import java.util.List;

public class MethodMetadataDemo {
    static class Parent {
        public String inherited() {
            return "parent";
        }

        public String status() {
            return "base";
        }
    }

    static class Child extends Parent {
        @Override
        public String status() {
            return "child";
        }

        public List<String> tags() {
            return List.of("java");
        }

        public int count() {
            return 2;
        }

        public void refresh() {}

        public String parse(String text) {
            return text;
        }

        public String parse(int code) {
            return Integer.toString(code);
        }
    }

    private static void show(Method method) {
        System.out.printf(
                "%s | owner=%s | return=%s%n",
                method.getName(),
                method.getDeclaringClass().getSimpleName(),
                method.getReturnType().getTypeName()
        );
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {
        show(Child.class.getMethod("inherited"));
        show(Child.class.getMethod("status"));
        show(Child.class.getDeclaredMethod("tags"));
        show(Child.class.getDeclaredMethod("count"));
        show(Child.class.getDeclaredMethod("refresh"));

        Method tags = Child.class.getDeclaredMethod("tags");
        System.out.println(
                "tags generic=" + tags.getGenericReturnType().getTypeName()
        );

        Method overload =
                Child.class.getDeclaredMethod("parse", String.class);
        System.out.println(
                "overload=" + overload.getName()
                + "(" + overload.getParameterTypes()[0].getSimpleName() + ")"
        );

        Method parentStatus = Parent.class.getDeclaredMethod("status");
        System.out.println(
                "parentStatus owner="
                + parentStatus.getDeclaringClass().getSimpleName()
        );
        System.out.println(
                "parentStatus invoked=" + parentStatus.invoke(new Child())
        );
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac MethodMetadataDemo.java
java MethodMetadataDemo
```

Output:

```text
inherited | owner=Parent | return=java.lang.String
status | owner=Child | return=java.lang.String
tags | owner=Child | return=java.util.List
count | owner=Child | return=int
refresh | owner=Child | return=void
tags generic=java.util.List<java.lang.String>
overload=parse(String)
parentStatus owner=Parent
parentStatus invoked=child
```

Giải thích các dòng tiêu biểu:

1. `Child.class.getMethod("inherited")` tìm được phương thức `public inherited()` do **Parent** khai báo. Vì vậy `getDeclaringClass().getSimpleName()` là `Parent`, không phải `Child`.
2. `Child.class.getMethod("status")` tìm phương thức **override do Child khai báo**, nên owner là `Child`.
3. Với `tags()`, `getReturnType().getTypeName()` là `java.util.List`; dùng `getGenericReturnType()` mới thấy thông tin `java.util.List<java.lang.String>`.
4. `count()` trả kiểu nguyên thủy `int`; `refresh()` có kiểu trả về `void`. Cả hai đều có một `Class` hợp lệ biểu diễn kiểu này.
5. `getDeclaredMethod("parse", String.class)` chọn overload có tham số `String`, còn overload `parse(int)` mang cùng tên nhưng chữ ký khác.
6. `parentStatus` là đối tượng `Method` lấy trực tiếp từ `Parent`, nên owner của nó vẫn là `Parent`; tuy nhiên `parentStatus.invoke(new Child())` gọi implementation override của `Child` và trả `"child"`. **Declaring class của metadata** và **implementation được dispatch tại runtime** có thể khác nhau.

Ví dụ được bố trí để mỗi thông tin được in từ một method xác định. Nếu thay các lời gọi này bằng vòng lặp trên `getDeclaredMethods()`, hãy tự sắp xếp trước khi yêu cầu thứ tự output cố định.

## 4. getReturnType() và getGenericReturnType() khác nhau thế nào?

Hai API cùng mô tả **kiểu trả về đã khai báo**, nhưng có mức thông tin khác nhau:

- `getReturnType()` có kiểu Java là `Class<?>`. Nó biểu diễn **kiểu erased/raw** sau khi áp dụng quy tắc erasure cho generic.
- `getGenericReturnType()` có kiểu Java là `java.lang.reflect.Type`. Nó giữ thông tin chữ ký generic khi có, chẳng hạn `ParameterizedType`, `TypeVariable`, `GenericArrayType` hoặc `Class`.

Ví dụ:

```java
public List<String> tags() {
    return List.of("java");
}
```

Trong ngữ cảnh ví dụ chạy ở mục 3, `tags.getReturnType()` là `List.class`, còn `tags.getGenericReturnType().getTypeName()` in ra `java.util.List<java.lang.String>`.

Một ví dụ generic khác:

```java
class Box<T> {
    public T read() {
        return null;
    }
}
```

Đây là đoạn minh họa không độc lập. Với `Method` đại diện cho `Box.read()`, `getReturnType()` là `Object.class` vì `T` không có upper bound cụ thể, còn `getGenericReturnType()` biểu diễn **biến kiểu** `T`.

Nếu `SpecialBox extends Box<String>`, đừng mặc định `SpecialBox.class.getMethod("read").getGenericReturnType()` tự động biến `T` thành `String`: method đó vẫn có thể là metadata khai báo ở `Box`. Để suy ra `T` cụ thể từ subclass, một công cụ phân tích generic phải **resolve mapping type variable** qua hệ thống kiểu của superclass/interface.

Khi xử lý `Type`, nên phân biệt `Class`, `ParameterizedType`, `TypeVariable`, `WildcardType`... theo đúng trường hợp thay vì ép mọi `Type` thành `Class`. Dùng `getTypeName()` giúp trình bày thông tin; đừng coi định dạng chuỗi này là giao thức lưu trữ/chữ ký ổn định do ứng dụng tự phát minh.

Nếu chỉ cần phân nhánh theo kiểu runtime đã bị erasure, `getReturnType()` thường đủ. Nếu muốn dựng tài liệu API, phân tích generic hoặc mapper phân biệt `List<String>` với `List<Integer>`, phải xét `getGenericReturnType()` và ngữ cảnh tổng quát hơn.

## 5. Thừa kế, override và dynamic dispatch

Ba khái niệm sau không nên bị nhập làm một:

**Một: kiểu đang truy vấn.** Người viết code có thể bắt đầu bằng `Child.class.getMethod("inherited")`, nhưng truy vấn qua lớp con không có nghĩa mọi `Method` tìm được đều được khai báo ở lớp con.

**Hai: kiểu khai báo.** `method.getDeclaringClass()` là lớp/interface **có khai báo tương ứng** mà Method biểu diễn. Với phương thức inherited không override, thường là lớp cha; với phương thức override được khai báo tại child, là child.

**Ba: implementation được thực thi.** Với instance method sử dụng virtual dispatch, một `Method` trỏ tới khai báo ở lớp cha vẫn có thể chạy implementation override của lớp con khi nhận một object lớp con. Đây là lý do trong ví dụ `parentStatus.getDeclaringClass() == Parent.class` nhưng `parentStatus.invoke(new Child())` trả `"child"`.

Không nên giải thích `getDeclaringClass()` như “lớp định nghĩa implementation thực sự đã chạy”. Trước khi invoke, API chỉ cho biết metadata; dispatch lúc chạy còn tùy loại method (`static`, instance, private...) và receiver.

Với interface, `getDeclaringClass()` có thể trả về interface định nghĩa phương thức. Chẳng hạn method khai báo trong interface `Task` có owner là `Task.class`, dù một lớp cụ thể triển khai `Task`.

## 6. Overload, override, bridge và synthetic methods

**Overload**: nhiều method cùng tên nhưng khác danh sách kiểu tham số. Hai method `parse(String)` và `parse(int)` có cùng kết quả `getName() == "parse"`. Để tìm đúng một method, truyền `Class<?>...` cho `getMethod` hoặc `getDeclaredMethod`:

```java
Method byText = Child.class.getDeclaredMethod("parse", String.class);
Method byCode = Child.class.getDeclaredMethod("parse", int.class);
```

Đoạn trên nằm trong ngữ cảnh lớp `Child` ở ví dụ đầy đủ, với `Method` được import và xử lý `NoSuchMethodException` ở caller. Nếu bỏ tham số khi tìm phương thức `parse`, Java sẽ tìm `parse()` **không tham số**, chứ không tự chọn một overload bất kỳ.

**Override**: một phương thức ở lớp con tái định nghĩa phương thức từ lớp cha. Mỗi khai báo có thể được biểu diễn bằng `Method` riêng, với `getDeclaringClass()` tương ứng. Override có thể dùng **covariant return type** (ví dụ lớp cha khai báo `Number measure()`, lớp con override bằng `Integer measure()`): khi phản chiếu đúng từng khai báo, `getReturnType()` cũng phản ánh kiểu khai báo tương ứng.

**Bridge/synthetic**: compiler đôi khi sinh thêm method ở bytecode để duy trì polymorphism khi dùng generics hoặc covariant returns. Bởi vậy `getDeclaredMethods()` có thể hiển thị nhiều method hơn danh sách người đọc nhìn thấy trong source. Khi phân tích method để sinh tài liệu hoặc đăng ký handler, có thể cần kiểm tra `method.isBridge()` và `method.isSynthetic()`, kết hợp `getParameterTypes()` và `getReturnType()`; không nên coi tên là khóa duy nhất.

Một số class file có thể chứa nhiều method có cùng tên và parameter types nhưng khác return type (ví dụ bridge). Theo hợp đồng `Class.getDeclaredMethod`, khi có nhiều ứng viên và có một kiểu trả về cụ thể hơn những ứng viên còn lại, API ưu tiên ứng viên đó; trường hợp khác có thể chọn một ứng viên. Đừng thiết kế mã phân tích bytecode dựa trên giả định rằng chỉ tên + params luôn phân biệt mọi method ở mức JVM.

**Phương thức khởi tạo không phải Method**: constructor được phản chiếu bằng `Constructor<?>`, không có tên method `"<init>"` lấy từ `getDeclaredMethod`. Việc đọc `Method.getReturnType()` không áp dụng trực tiếp cho `Constructor` vì constructor không có return type kiểu phương thức thông thường.

## 7. Ứng dụng thực tế: xây danh sách metadata có thứ tự

Giả sử cần lập danh sách endpoint, handler hay lệnh từ các method. Tránh hai lỗi: dùng `getName()` làm ID duy nhất trong khi có overload, và dựa vào thứ tự trả về của `getDeclaredMethods()`.

Ví dụ mã minh họa (không phải chương trình độc lập; giả định đã import `Arrays`, `Comparator`, `Method` và có `SomeClass`):

```java
Method[] methods = SomeClass.class.getDeclaredMethods();

Arrays.sort(
        methods,
        Comparator.comparing(Method::getName)
                  .thenComparing(m -> Arrays.toString(m.getParameterTypes()))
                  .thenComparing(m -> m.getReturnType().getTypeName())
);

for (Method method : methods) {
    System.out.printf(
            "%s declaredIn=%s returns=%s%n",
            method.getName(),
            method.getDeclaringClass().getName(),
            method.getReturnType().getTypeName()
    );
}
```

Ví dụ trên tạo thứ tự theo tên, danh sách kiểu tham số (ở dạng chuỗi minh họa) và kiểu trả về; nó **không khôi phục thứ tự khai báo trong source**. Nếu cần khóa định danh lâu dài, định nghĩa một phép mã hóa chữ ký nhất quán, có xét declaring type, method name, ordered parameter types và khi phù hợp cả return type/bridge information. Với mục đích hiển thị đơn giản, comparator ở trên đủ minh họa nguyên tắc.

Khi xây một framework, cũng nên chủ động xác định có muốn bao gồm:

- Method inherited từ superclass/interface hay chỉ method khai báo tại lớp đang xét;
- Method `public` hay cả non-public;
- Method bridge/synthetic do compiler tạo;
- Method được override và các annotation xuất hiện trên từng khai báo.

Các quyết định đó là quy tắc nghiệp vụ của công cụ, **không phải ý nghĩa riêng của `getName`, `getDeclaringClass` hay `getReturnType`**.

## 8. Lỗi phổ biến và cách tránh

| Hiểu lầm | Sự thật |
| --- | --- |
| `Child.class.getMethod("x")` luôn có `getDeclaringClass() == Child.class` | Method có thể khai báo trong parent/interface rồi được thừa kế |
| `getName()` cho biết cả chữ ký method | Chỉ cho biết **tên đơn giản**, không bao gồm tham số |
| `getReturnType()` trả runtime class của giá trị trả về | Trả **kiểu khai báo**, không invoke method |
| `getReturnType()` của `void` là `null` | Là `void.class` |
| `getReturnType()` của `List<String>` giữ `String` | Chỉ có `List.class` sau erasure; dùng `getGenericReturnType()` khi cần generic metadata |
| `getDeclaringClass()` cho biết implementation nào sẽ chạy | Metadata declaration và virtual dispatch là hai vấn đề riêng |
| `getDeclaredMethods()` gồm method inherited | Không; chỉ gồm method khai báo trong chính kiểu |
| Lấy được `private Method` nghĩa là gọi được | Metadata discovery không tự bỏ qua access checks |
| Một method name tương ứng đúng một Method | Overload, compiler bridge/synthetic có thể khiến tên lặp |
| Thứ tự của `getDeclaredMethods()` là thứ tự viết trong file Java | API không bảo đảm thứ tự ấy |

Khi gọi `getMethod` hoặc `getDeclaredMethod` với tên/chữ ký không tìm thấy, `NoSuchMethodException` có thể được ném. Đây là ngoại lệ của **bước tra cứu**, không phải hậu quả của `getName()`, `getDeclaringClass()` hay `getReturnType()` sau khi đã có một đối tượng `Method` hợp lệ.

Các lời gọi `Method.invoke` còn có thể bị kiểm tra quyền truy cập, ném `IllegalAccessException`, `InvocationTargetException` và các lỗi khác. Metadata không tự cho phép thực thi hay bảo đảm method chạy thành công.

## 9. Checklist và câu hỏi tự kiểm tra

Trước khi dùng Method metadata trong chương trình, có thể kiểm tra theo thứ tự:

1. Mình cần *method khai báo trực tiếp* hay cả *method kế thừa*? Chọn `getDeclaredMethod(s)` hoặc `getMethod(s)` tương ứng.
2. Mình có cần phân biệt overload? Dùng tên cùng kiểu tham số theo thứ tự, đừng chỉ dùng `getName()`.
3. Mình đang hỏi *declaring class* hay *kiểu runtime của receiver*? Chọn đúng nguồn metadata.
4. Mình cần *return type erased* hay *generic return signature*? Dùng `getReturnType()` hoặc `getGenericReturnType()`.
5. Mình có cần chạy method không? Nếu không, dừng ở metadata; nếu có, xử lý quyền truy cập, receiver và ngoại lệ riêng.
6. Mình có đang dựa vào thứ tự liệt kê hoặc bỏ sót bridge/synthetic methods không? Chọn bộ lọc/sắp xếp tường minh.

### Câu hỏi

1. `Method.getName()` của `parse(String)` là gì? **`"parse"`**; không chứa `String`.
2. Gọi `Child.class.getMethod("inherited")` đối với phương thức chỉ khai báo trong Parent, `getDeclaringClass()` là gì? **`Parent.class`**.
3. `void refresh()` có `getReturnType()` bằng `null` không? **Không.** Kết quả là `void.class`.
4. `List<String> tags()` có `getReturnType()` bằng `List<String>.class` không? **Không có class literal ấy**; kết quả là `List.class`, generic information nằm ở `getGenericReturnType()`.
5. Một `Method` đại diện cho `Parent.status()` được invoke với receiver `Child` đã override, owner của Method có đổi không? **Không.** Owner vẫn `Parent`, dù virtual dispatch có thể chạy `Child.status()`.
6. Muốn tìm `parse(int)` mà không bị nhầm `parse(String)`, dùng gì? **`getDeclaredMethod("parse", int.class)`** trong đúng lớp khai báo.
7. Lấy metadata method bằng `getDeclaredMethod` có tự cho quyền gọi method `private` không? **Không.** Quyền truy cập khi thực thi được kiểm tra riêng.

## 10. Tài liệu tham khảo chính thức

- [Oracle Java SE 21 — `java.lang.reflect.Method`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Method.html): định nghĩa `getName()`, `getDeclaringClass()`, `getReturnType()`, `getGenericReturnType()`, bridge/synthetic và invocation.
- [Oracle Java SE 21 — `java.lang.Class`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html): phân biệt `getMethod`, `getDeclaredMethod`, `getMethods`, `getDeclaredMethods` và quy tắc tra cứu.
- [Java Language Specification SE 21 — §8.4 Method Declarations](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.4): nền tảng overload, override và return types.

**Ghi nhớ:** `getName()` trả *tên*, `getDeclaringClass()` trả *kiểu khai báo phương thức*, `getReturnType()` trả *kiểu trả về hình thức sau erasure*. Khi cần metadata generic, dùng thêm `getGenericReturnType()`; khi cần biết implementation sẽ chạy, phải xét dynamic dispatch và receiver chứ không suy từ declaring class.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="methods-002">Kiểu tham số, kiểu throws và các biến thể generic</a>

<details>
<summary>Xem chi tiết</summary>

# Reflection Method: kiểu tham số, kiểu `throws` và các biến thể generic

Khi dùng Java Reflection để đọc chữ ký một phương thức, có **hai góc nhìn phải phân biệt**:

1. **Kiểu thực thi sau type erasure** (*erased/raw runtime type*) — được biểu diễn bằng `Class<?>`. Đây là kiểu JVM dùng để xác định descriptor của method và kiểm tra đối số khi gọi qua reflection.
2. **Kiểu generic được khai báo trong mã nguồn** (*generic signature*) — được biểu diễn bằng `java.lang.reflect.Type` và các subtype của nó. Đây là metadata cho công cụ tài liệu hóa, thư viện mapping, injection và kiểm tra hợp đồng kiểu.

Ví dụ, một tham số `List<? super T>` có **runtime type** là `java.util.List` nhưng **generic type** là `ParameterizedType` biểu thị `List<? super T>`. Tham số `T...` có thể là mảng `Number[]` ở runtime nếu `T extends Number`, trong khi chữ ký generic vẫn biểu thị `T[]`.

Trên đối tượng `java.lang.reflect.Method`, bốn API trọng tâm là:

| Metadata cần đọc | Sau erasure | Giữ chữ ký generic |
| --- | --- | --- |
| Tham số phương thức | `getParameterTypes(): Class<?>[]` | `getGenericParameterTypes(): Type[]` |
| Kiểu `throws` được khai báo | `getExceptionTypes(): Class<?>[]` | `getGenericExceptionTypes(): Type[]` |
| Kiểu trả về (để đối chiếu) | `getReturnType(): Class<?>` | `getGenericReturnType(): Type` |
| Type parameter do chính method khai báo | — | `getTypeParameters(): TypeVariable<Method>[]` |

**Điểm học tập chính:** Các API có chữ `Generic` dùng để *mô tả* kiểu được khai báo. Chúng không khôi phục kiểu đối số generic của mọi đối tượng tại runtime, không tự kiểm tra nội dung một collection và không sửa quy tắc type erasure của Java.

## 1. Lấy đúng đối tượng `Method` trước khi phân tích chữ ký

Ví dụ một phương thức khai báo như sau:

~~~java
public <T extends Number & Comparable<T>, E extends Exception>
java.util.List<? extends T> query(
        java.util.List<? super T> destination, T... values)
        throws java.io.IOException, E {
    // Phần thân phương thức
    return java.util.Collections.emptyList();
}
~~~

Đây là **đoạn minh họa chữ ký**, chưa phải class Java độc lập. Nó gồm:

- **Hai tham số phương thức:** `List<? super T> destination` và `T... values`.
- **Hai type parameters do method khai báo:** `T`, `E`; `T` có hai bound (`Number`, `Comparable<T>`); `E` có bound `Exception`.
- **Kiểu trả về generic:** `List<? extends T>`.
- **Hai kiểu trong mệnh đề `throws`:** `IOException` và `E`.

Để tra cứu method bằng reflection, `getDeclaredMethod` yêu cầu **tên + các kiểu tham số sau erasure**; không truyền `List<? super T>` hay `T[]` dưới dạng biểu thức generic. Với một class `Service` chứa method này:

~~~java
Method method = Service.class.getDeclaredMethod(
        "query",
        java.util.List.class,
        Number[].class
);
~~~

`T` có bound đầu tiên là `Number`, nên `T...` có kiểu mảng runtime là `Number[]`. Nếu tra bằng `Object[].class` hoặc `Integer[].class` thì không khớp descriptor và sẽ nhận `NoSuchMethodException`.

Phân biệt cách tìm:

- `getDeclaredMethod(name, parameterTypes...)`: tìm method khai báo **trực tiếp** trên class/interface đang xét, gồm cả method không public; không tự tìm method kế thừa.
- `getMethod(name, parameterTypes...)`: tìm **public method** theo cơ chế tra cứu tương ứng, có tính đến các public method kế thừa.
- Hai method có tên và danh sách tham số khác nhau là overload; Reflection tra theo **erased parameter types**, không dùng kiểu trả về để người gọi lựa chọn overload.
- `Class.getDeclaredMethods()` không bảo đảm thứ tự theo mã nguồn; nếu hiển thị danh sách method cho người dùng, hãy sắp xếp theo tên và chữ ký đã chuẩn hóa.

## 2. `getParameterTypes()`: kiểu tham số runtime

Khai báo của API:

~~~java
Class<?>[] parameters = method.getParameterTypes();
~~~

Mỗi phần tử là một đối tượng `Class` biểu diễn **kiểu tham số formal sau erasure**. Ví dụ với `query`:

~~~text
java.util.List
java.lang.Number[]
~~~

Ở đây:

- `List<? super T>` được xóa về `List`.
- `T...` có dạng mảng khi biên dịch; `T extends Number & Comparable<T>` xóa về bound đầu tiên là `Number`, nên kết quả là `Number[]`.
- `getParameterTypes()` không cho biết `? super T`, tên `T` hoặc các bound bổ sung.
- Kiểu primitive vẫn là token primitive, ví dụ `int.class`; tham số mảng như `String[]` vẫn là class mảng `String[].class`.

`Class<?>[]` này phù hợp để so sánh descriptor, xác định method overload hoặc chuẩn bị đối số Reflection. Nó **không chứng minh** đối tượng `List` thực tế có phần tử đúng kiểu generic.

### Tham số cuối dạng varargs

`T... values` được JVM biểu diễn là **tham số mảng**. Thông tin “đây là lời khai báo varargs” lấy riêng qua `Method.isVarArgs()`:

~~~java
boolean declaredVarargs = method.isVarArgs();
~~~

Ví dụ `void a(String[] names)` và `void b(String... names)` đều có parameter type `String[].class`, nhưng chỉ `b` có `isVarArgs() == true`. Với `Method.invoke`, cần tôn trọng số lượng formal parameters và kiểu mảng đã xóa; không mặc định rằng Reflection sẽ tự đóng gói danh sách đối số theo cú pháp varargs ở lời gọi Java thông thường.

## 3. `getGenericParameterTypes()`: kiểu tham số có generic metadata

Khai báo:

~~~java
Type[] genericParameters = method.getGenericParameterTypes();
~~~

Với `query`, `Type.getTypeName()` lần lượt biểu thị:

~~~text
java.util.List<? super T>
T[]
~~~

Đây không phải hai đối tượng `Class` thông thường:

- `List<? super T>` là **`ParameterizedType`** có raw type `List.class` và một type argument là **`WildcardType`** chứa lower bound `T`.
- `T[]` là **`GenericArrayType`** vì thành phần mảng là type variable `T`. `getGenericComponentType()` cho ra `TypeVariable` tương ứng.
- Nếu kiểu tham số được khai báo trực tiếp là `T` (không có mảng), phần tử `Type[]` có thể chính là `TypeVariable`.
- Nếu tham số là `String`, `int` hoặc `String[]` không chứa generic component, kết quả thường là đối tượng `Class`.

Vì thế **không ép mọi `Type` về `Class<?>`**:

~~~java
Type generic = method.getGenericParameterTypes()[0];

if (generic instanceof ParameterizedType parameterized) {
    Type raw = parameterized.getRawType();
    Type[] arguments = parameterized.getActualTypeArguments();
}
~~~

Đây là đoạn phân tích metadata có thể đặt trong phương thức; biến `method` phải được khởi tạo trước. Khi kiểm tra `arguments[0]` của `List<? super T>`, ta gặp `WildcardType`:

~~~java
Type argument = ((ParameterizedType) generic)
        .getActualTypeArguments()[0];

if (argument instanceof WildcardType wildcard) {
    Type[] lower = wildcard.getLowerBounds(); // Chứa T ở ví dụ này.
    Type[] upper = wildcard.getUpperBounds(); // Object khi không có extends bound cụ thể.
}
~~~

**`? super T` và `? extends T` không giống nhau:** lower bound của wildcard giúp mô tả phía “được phép nhận” một kiểu, còn upper bound mô tả phía “bị giới hạn trên” về quan hệ kiểu. Reflection đọc chữ ký này, nhưng không tái tạo logic type-checking hoàn chỉnh như compiler.

## 4. Những biến thể của `Type` cần nhận diện

`java.lang.reflect.Type` là interface chung cho nhiều dạng chữ ký:

| Dạng khai báo | Kiểu `Type` thường gặp | Cần đọc gì? |
| --- | --- | --- |
| `String`, `int`, `String[]` | `Class<?>` | `getTypeName()`, `isArray()`, `getComponentType()` khi phù hợp |
| `List<String>` | `ParameterizedType` | `getRawType()`, `getActualTypeArguments()`, `getOwnerType()` khi là nested type |
| `T` | `TypeVariable<?>` | `getName()`, `getBounds()`, `getGenericDeclaration()` |
| `T[]` | `GenericArrayType` | `getGenericComponentType()` |
| `? extends Number` / `? super T` | `WildcardType` | `getUpperBounds()` / `getLowerBounds()` |

**Ví dụ phân loại:**

~~~java
static String kind(Type type) {
    if (type instanceof Class<?>) return "Class";
    if (type instanceof ParameterizedType) return "ParameterizedType";
    if (type instanceof TypeVariable<?>) return "TypeVariable";
    if (type instanceof GenericArrayType) return "GenericArrayType";
    if (type instanceof WildcardType) return "WildcardType";
    return "Other Type";
}
~~~

Đây là phương thức minh họa, cần import `java.lang.reflect.*`. Trong thực tế, một `ParameterizedType` có thể chứa tiếp `ParameterizedType`, `WildcardType`, `TypeVariable` hoặc `GenericArrayType`. Muốn dựng cây generic signature đầy đủ thì phải xử lý **đệ quy**, không chỉ in raw type.

### Type variable nằm ở method hay ở class?

Hai chữ ký có thể cùng sử dụng tên `T` nhưng nguồn khai báo khác nhau:

~~~java
class Container<T> {
    T read() { return null; }         // T của class.
    <U> U convert(U input) { return input; } // U của method.
}
~~~

Với `read`, `getGenericReturnType()` có thể là `TypeVariable` do **`Container` khai báo**; `read.getTypeParameters()` trả mảng rỗng vì method không khai báo type variable mới. Với `convert`, `getTypeParameters()` có `U`, và `U.getGenericDeclaration()` trỏ đến chính method `convert`. Đừng nhầm metadata của type parameter trên class với type parameter trên method.

**Quan trọng về giới hạn:** nếu chạy `Container<String>`, Java Reflection trên `Method` không tự suy ra “`T` lúc này là `String`” từ một instance. Muốn thay thế biến kiểu của class bằng đối số cụ thể, phải phân tích ngữ cảnh `ParameterizedType` từ generic superclass/interface hoặc type token phù hợp; nhiều thông tin bị xóa ở runtime.

## 5. `getExceptionTypes()`: kiểu trong mệnh đề `throws` sau erasure

Khai báo API:

~~~java
Class<?>[] exceptionTypes = method.getExceptionTypes();
~~~

Với:

~~~java
<T extends Number & Comparable<T>, E extends Exception>
void query(T item) throws java.io.IOException, E { }
~~~

`getExceptionTypes()` trả các class:

~~~text
java.io.IOException
java.lang.Exception
~~~

Lý do: `E` bị xóa về bound đầu tiên `Exception`. Nếu method khai báo `<X extends IOException> void fail() throws X`, dạng raw sẽ là `IOException.class`.

Lưu ý các điểm quan trọng:

- Đây là **danh sách kiểu exception được khai báo trong `throws`**, không phải danh sách mọi exception mà method *thực tế có thể ném*.
- Một method không có mệnh đề `throws` → mảng rỗng, mặc dù nó vẫn có thể ném unchecked exception như `NullPointerException` hoặc `IllegalArgumentException`.
- Java cho phép khai báo một unchecked exception trong `throws`; Reflection sẽ ghi nhận nó nếu có trong chữ ký.
- `Error` hoặc unchecked exception có thể phát sinh ngoài khai báo; không dùng `getExceptionTypes()` để chứng minh “method không bao giờ lỗi”.
- `getExceptionTypes()` không cho biết tên type variable hay mối liên hệ giữa type variable ấy và tham số của method.

## 6. `getGenericExceptionTypes()`: bảo toàn `throws E`

API:

~~~java
Type[] genericExceptions = method.getGenericExceptionTypes();
~~~

Với `query(...) throws IOException, E`, hai phần tử có chữ ký:

~~~text
java.io.IOException
E
~~~

Trong đó phần tử thứ nhất thường là `Class<IOException>`, phần tử thứ hai là `TypeVariable` tên `E`. Ta có thể truy cập bound:

~~~java
for (Type type : method.getGenericExceptionTypes()) {
    if (type instanceof TypeVariable<?> variable) {
        System.out.println(variable.getName());
        System.out.println(java.util.Arrays.toString(variable.getBounds()));
    }
}
~~~

Nếu `E extends Exception`, bound là `Exception.class`; nếu `X extends IOException`, bound là `IOException.class`.

### Vì sao generic `throws` có ích?

Khai báo:

~~~java
static <X extends Exception> void propagate(X problem) throws X {
    throw problem;
}
~~~

Đây là một **phương thức độc lập dùng để minh họa**, đặt bên trong class để biên dịch. Nó mô tả cho compiler rằng kiểu exception truyền vào và kiểu exception có thể ném ra được liên kết bởi **cùng type variable `X`**. Sau erasure, Reflection `getExceptionTypes()` chỉ còn `Exception.class`; `getGenericExceptionTypes()` vẫn biểu diễn `X`. Thư viện phân tích API có thể dùng metadata này để hiển thị hợp đồng chính xác hơn.

**Không nhầm generic `throws` với `List<IOException>` trong throws:** Java không cho phép tham số hóa một exception class kiểu `SomeException<T> extends Exception`, vì generic class không được trực tiếp/gián tiếp kế thừa `Throwable`. Biến kiểu có bound exception (`<E extends Exception> ... throws E`) là cơ chế hợp lệ để khai báo generic throws.

### Reflection gọi method thì exception ra sao?

`Method.invoke(receiver, args...)` không trực tiếp chuyển cho caller checked exception của method được gọi như một lời gọi Java thông thường. Nếu phần thân method ném exception, `invoke` bọc nó bằng **`InvocationTargetException`**; có thể kiểm tra `getCause()`. Ngoài ra bản thân Reflection có thể ném `IllegalAccessException`, `IllegalArgumentException` hoặc lỗi khác do lời gọi. Danh sách `getGenericExceptionTypes()` chỉ mô tả **chữ ký của method đích**, không tự thay thế hợp đồng exception của `Method.invoke`.

## 7. `getTypeParameters()` và erasure khi có nhiều bound

API:

~~~java
TypeVariable<Method>[] variables = method.getTypeParameters();
~~~

Với:

~~~java
<T extends Number & Comparable<T>, E extends Exception>
~~~

ta có:

- `T.getName()` → `"T"`.
- `T.getBounds()` → `Number` và `Comparable<T>`; bound đầu là class `Number`, bound thứ hai là parameterized interface.
- `E.getName()` → `"E"`.
- `E.getBounds()` → `Exception.class`.

Quy tắc **erasure của type variable** dùng bound bên trái đầu tiên. Nếu chỉ khai báo `<T>`, implicit upper bound là `Object`, nên erasure của `T` là `Object`. Nếu có nhiều bound, không đồng nghĩa JVM có “kiểu giao” cụ thể làm tham số; descriptor dùng erasure theo quy tắc JLS.

`getTypeParameters()` chỉ trả type variables được **method** khai báo. Nó không đại diện cho mọi type variable xuất hiện trong tham số: chúng cũng có thể được khai báo bởi class bao ngoài.

## 8. Ví dụ Java 17+ chạy đầy đủ, so sánh cả bốn API

Tạo file duy nhất **`MethodSignatureDemo.java`**. Ví dụ chủ động dùng:

- Parameterized type `List<? super T>`.
- Generic array `T...` với erasure thành `Number[]`.
- Wildcard return `List<? extends T>`.
- Generic `throws IOException, E` và method `fail` khai báo `throws X`.
- Phân tích bounds của `T` và `E`.
- `Parameter.isNamePresent()` để nhận biết việc lưu tên tham số khi biên dịch.

~~~java
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

public class MethodSignatureDemo {
    static class Service {
        @SafeVarargs
        public final <T extends Number & Comparable<T>, E extends Exception>
        List<? extends T> query(List<? super T> destination, T... values)
                throws IOException, E {
            for (T value : values) {
                destination.add(value);
            }
            return Collections.emptyList();
        }

        public void text(String[] names) throws IOException { }

        public <X extends IOException> void fail(X problem) throws X {
            throw problem;
        }
    }

    private static String types(Type[] xs) {
        return Arrays.stream(xs).map(Type::getTypeName).collect(Collectors.joining(" | "));
    }

    private static String classes(Class<?>[] xs) {
        return Arrays.stream(xs).map(Class::getTypeName).collect(Collectors.joining(" | "));
    }

    public static void main(String[] args) throws Exception {
        Method m = Service.class.getDeclaredMethod("query", List.class, Number[].class);
        System.out.println("params raw: " + classes(m.getParameterTypes()));
        System.out.println("params generic: " + types(m.getGenericParameterTypes()));
        System.out.println("return raw: " + m.getReturnType().getTypeName());
        System.out.println("return generic: " + m.getGenericReturnType().getTypeName());
        System.out.println("throws raw: " + classes(m.getExceptionTypes()));
        System.out.println("throws generic: " + types(m.getGenericExceptionTypes()));
        System.out.println("vars: " + Arrays.stream(m.getTypeParameters()).map(TypeVariable::getName)
                .collect(Collectors.joining(" | ")));

        for (TypeVariable<Method> variable : m.getTypeParameters()) {
            System.out.println("bound " + variable.getName() + ": " + types(variable.getBounds()));
        }

        System.out.println("isVarArgs: " + m.isVarArgs());
        System.out.println("second is GenericArrayType: "
                + (m.getGenericParameterTypes()[1] instanceof GenericArrayType));
        System.out.println("first is ParameterizedType: "
                + (m.getGenericParameterTypes()[0] instanceof ParameterizedType));
        System.out.println("names present: "
                + Arrays.stream(m.getParameters()).allMatch(Parameter::isNamePresent));

        Method fail = Service.class.getDeclaredMethod("fail", IOException.class);
        System.out.println("fail raw throws: " + classes(fail.getExceptionTypes()));
        System.out.println("fail generic throws: " + types(fail.getGenericExceptionTypes()));
    }
}
~~~

Biên dịch trong thư mục chứa file bằng **JDK 17 trở lên**. Không thêm `-parameters` ở lệnh đầu để kiểm chứng trường hợp tên tham số **không được lưu**:

~~~bash
javac --release 17 -Xlint:all MethodSignatureDemo.java
java MethodSignatureDemo
~~~

**Stdout chính xác** (đã biên dịch/chạy với lệnh trên):

~~~text
params raw: java.util.List | java.lang.Number[]
params generic: java.util.List<? super T> | T[]
return raw: java.util.List
return generic: java.util.List<? extends T>
throws raw: java.io.IOException | java.lang.Exception
throws generic: java.io.IOException | E
vars: T | E
bound T: java.lang.Number | java.lang.Comparable<T>
bound E: java.lang.Exception
isVarArgs: true
second is GenericArrayType: true
first is ParameterizedType: true
names present: false
fail raw throws: java.io.IOException
fail generic throws: X
~~~

Quan sát những điểm dễ hiểu sai:

1. `getParameterTypes()` đã xóa `List<? super T>` thành `List` và `T[]` thành `Number[]`, còn `getGenericParameterTypes()` giữ chữ ký có wildcard/type variable.
2. `getExceptionTypes()` trả `Exception` cho `E`, trong khi `getGenericExceptionTypes()` trả type variable `E`. Method `fail` tương tự nhưng xóa `X` thành `IOException`.
3. `getTypeParameters()` và `getBounds()` giữ được giới hạn `T extends Number & Comparable<T>`.
4. `isVarArgs()` xác nhận dấu `...` ở nguồn; `getParameterTypes()` chỉ thấy kiểu mảng.
5. `names present: false` vì lệnh `javac` trên **không** sử dụng `-parameters`. Đây là thông tin tên tham số từ class file, **độc lập** với việc chữ ký generic có được giữ lại hay không.

Nếu biên dịch lại với:

~~~bash
javac --release 17 -parameters MethodSignatureDemo.java
java MethodSignatureDemo
~~~

thì dòng `names present` sẽ thành `true` đối với hai tham số của `query`, còn các dòng phân tích kiểu ở trên không thay đổi. Với các công cụ build khác, kết quả này phụ thuộc compiler options của **class file thực sự được chạy**, không phụ thuộc tên hiển thị trong IDE.

## 9. `Parameter` API, tên tham số và annotations

`Method.getParameters()` trả mảng `Parameter[]`; mỗi phần tử chứa thông tin metadata của một formal parameter:

~~~java
for (Parameter parameter : method.getParameters()) {
    String name = parameter.getName();
    boolean realName = parameter.isNamePresent();
    Class<?> erased = parameter.getType();
    Type generic = parameter.getParameterizedType();
    boolean varargsParameter = parameter.isVarArgs();
}
~~~

Đoạn trên là minh họa, cần import `java.lang.reflect.*` và cung cấp `method`. Các API liên quan:

- `Parameter.getType()` tương ứng góc nhìn `Class<?>`; `getParameterizedType()` tương ứng góc nhìn `Type`.
- `Parameter.getName()` có thể trả tên tổng hợp như `arg0` nếu không có metadata; **không nên coi tên ấy là tên khai báo trong source**. Dùng `isNamePresent()` để biết tên thật có được ghi trong class file không.
- `Parameter.isImplicit()` / `isSynthetic()` phản ánh một số tham số do compiler/qui ước ngôn ngữ tạo ra, ví dụ constructor của inner class không static hoặc enum constructor. Với các bytecode đặc biệt, cần phân biệt formal parameters ở class file và danh sách người viết thấy ở source.
- `Method.getParameterAnnotations()` và `Parameter.getAnnotations()` trả các annotation trên tham số theo quy tắc retention phù hợp. Annotation thuộc **type use** có thể cần API `getAnnotatedParameterTypes()` để thấy vị trí khai báo trên chính kiểu (`List<@SomeAnno String>`).

Metadata của **method signature generic** và **runtime-visible annotations** là hai nguồn thông tin riêng; không suy ra kiểu generic chỉ từ một annotation.

## 10. Những giới hạn và lỗi thường gặp

### 10.1. Tìm Method theo generic argument

**Sai ý tưởng:** muốn lấy `process(List<String>)` nên gọi `getDeclaredMethod` với kiểu `List<String>`. Java không có literal `List<String>.class`. Runtime method lookup dùng erased parameter types, do đó với tham số `List<String>` thường chỉ truyền `List.class`.

Không thể khai báo hai overload chỉ khác nhau ở `List<String>` và `List<Integer>` vì cả hai cùng erasure `List` (name clash). Reflection không khôi phục những overload mà ngôn ngữ không cho phép khai báo.

### 10.2. Ép `Type` thành `Class<?>`

`(Class<?>) method.getGenericParameterTypes()[0]` có thể ném `ClassCastException` nếu phần tử là `ParameterizedType`. Kiểm tra bằng `instanceof` và xử lý đúng biến thể của `Type`. Trong cấu trúc nested generic, có thể cần duyệt đệ quy các type arguments và bounds.

### 10.3. Giả định `throws` là toàn bộ exception thực tế

`getExceptionTypes()` và `getGenericExceptionTypes()` chỉ ghi nhận **throws declaration**. Không có bảo đảm method sẽ ném mọi exception đó, cũng không bảo đảm nó không ném unchecked exception khác. Reflection `Method.invoke` còn có lớp exception bọc `InvocationTargetException`.

### 10.4. Nhầm `TypeVariable` với kiểu đã được giải quyết

`T extends Number` không có nghĩa Reflection luôn biết `T` cụ thể là `Integer`. `T` có thể tồn tại trong `Type` metadata mà chưa biết type argument trong ngữ cảnh gọi thực tế. Muốn kiểm tra dữ liệu runtime, vẫn cần ranh giới/validator riêng phù hợp.

### 10.5. Coi `T...` là một danh sách tham số bất kỳ

Ở mức method descriptor, varargs vẫn là **một tham số mảng cuối cùng**. `Method.isVarArgs()` giúp xác định cách biểu diễn trong source, nhưng `getParameterTypes()` trả array class. Reflection invocation cần đối số phù hợp với formal parameter array sau erasure.

### 10.6. Trông chờ tên tham số luôn xuất hiện

`-g` và debug information không thay thế hoàn toàn cho mục đích lấy tên qua `Parameter.isNamePresent()`; dùng `javac -parameters` khi cần public reflection parameter names. Các compiler/build tools có thể phát sinh các method bridge/synthetic; khi quét API, cân nhắc `Method.isBridge()`, `Method.isSynthetic()` và ngữ cảnh class khai báo.

### 10.7. Không xử lý metadata bất thường

Khi generic signature không hợp lệ hoặc tham chiếu kiểu trong metadata không giải quyết được, các API `getGeneric...` có thể ném những lỗi như `GenericSignatureFormatError`, `TypeNotPresentException` hoặc `MalformedParameterizedTypeException` tùy trường hợp. Đây là vấn đề class file/generic metadata, khác với việc method không tồn tại (`NoSuchMethodException`). Một công cụ introspection xử lý class của bên thứ ba nên báo lỗi có ngữ cảnh, không âm thầm thay thế generic signature sai bằng kiểu đoán.

## 11. Checklist chọn API theo câu hỏi

| Bạn cần trả lời | API thích hợp |
| --- | --- |
| “Method có những formal parameter type nào ở runtime?” | `getParameterTypes()` |
| “Method có `List<? super T>` hay `T[]` trong chữ ký không?” | `getGenericParameterTypes()` |
| “Method khai báo những loại exception nào sau erasure?” | `getExceptionTypes()` |
| “`throws E` gắn với type variable nào?” | `getGenericExceptionTypes()` + `TypeVariable.getBounds()` |
| “Method khai báo `T, E` với giới hạn nào?” | `getTypeParameters()` |
| “Tên formal parameter có thật được giữ trong class file không?” | `getParameters()` + `isNamePresent()` |
| “Có phải tham số cuối được khai báo `...`?” | `isVarArgs()` |
| “Kiểu trả về có wildcard hoặc generic arguments?” | `getGenericReturnType()` |

Khi xây dựng một công cụ reflection cho method, quy trình an toàn là: **xác định `Method` bằng erased parameter types → đọc metadata thô `Class<?>[]` để hiểu descriptor → đọc các mảng `Type[]` để hiểu generic signature → phân loại từng `Type` và phân giải type variable trong đúng ngữ cảnh khai báo**. Khi hiển thị báo cáo, nói rõ giá trị nào là *erasure* và giá trị nào là *generic declaration*.

### Tài liệu chuẩn

- [Java SE 21 — `java.lang.reflect.Method`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Method.html): `getParameterTypes`, `getGenericParameterTypes`, `getExceptionTypes`, `getGenericExceptionTypes`, `getTypeParameters`.
- [Java SE 21 — `java.lang.reflect.Type`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Type.html), [`ParameterizedType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/ParameterizedType.html), [`TypeVariable`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/TypeVariable.html), [`GenericArrayType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/GenericArrayType.html), [`WildcardType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/WildcardType.html).
- [Java SE 21 — `java.lang.reflect.Parameter`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Parameter.html).
- [Java Language Specification `4.6 — Type Erasure](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.6) và [`8.4 — Method Declarations](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html#jls-8.4).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="methods-003">getMethods khác getDeclaredMethods</a>

<details>
<summary>Xem chi tiết</summary>

# getMethods() khác getDeclaredMethods() trong Java Reflection

`Class.getMethods()` và `Class.getDeclaredMethods()` đều trả về `Method[]` để khám phá phương thức của một kiểu Java, nhưng giải quyết **hai nhu cầu khác nhau**. Nhầm hai API này sẽ làm công cụ Reflection bỏ sót phương thức kế thừa, bỏ sót private method, chọn sai phương thức để invoke hoặc phụ thuộc vô tình vào implementation.

- **`getMethods()`:** lấy các phương thức **public** của kiểu đang xét, gồm phương thức public được khai báo trực tiếp và phương thức public được kế thừa theo quy tắc Java.
- **`getDeclaredMethods()`:** lấy **các phương thức được khai báo trực tiếp** trên chính class/interface đang xét, ở **mọi mức truy cập**: public, protected, package-private và private; **không** bao gồm phương thức kế thừa.

Từ `declared` trong tên API có nghĩa là **được khai báo trực tiếp tại Class đang xét**, không phải kiểu khai báo của biến tham chiếu. Để dùng đúng, phải chọn cả đối tượng `Class<?>` và nhóm API tìm kiếm phù hợp.

## 1. So sánh trực tiếp hai API

Hai chữ ký API thuộc `java.lang.Class`:

```java
public Method[] getMethods()
public Method[] getDeclaredMethods()
```

Cả hai trả về `java.lang.reflect.Method`, nhưng khác **phạm vi tra cứu** và **quy tắc visibility**.

| Loại phương thức | `getMethods()` | `getDeclaredMethods()` |
| --- | --- | --- |
| Public, khai báo ngay trong class | Có | Có |
| Protected, khai báo ngay trong class | Không | Có |
| Package-private, khai báo ngay trong class | Không | Có |
| Private, khai báo ngay trong class | Không | Có |
| Public, kế thừa từ lớp cha | Có | Không |
| Public instance/default, kế thừa từ interface | Có | Không |
| Private/protected được khai báo ở lớp cha | Không | Không |
| Public từ `Object` trên class thông thường | Có, theo quy tắc chọn method cụ thể nhất | Không, trừ khi đang xét chính `Object.class` |
| Bridge/synthetic method do compiler khai báo ở class file | Có thể có nếu public và được chọn trong kết quả | Có thể có, ở mọi access |
| Constructor | Không | Không |
| Class initializer `<clinit>` | Không | Không |

Không nên suy diễn rằng `getMethods()` bằng cách nối tất cả mảng `getDeclaredMethods()` của class cha/con: Java áp dụng quy tắc method membership, overriding/hiding, lựa chọn phương thức cụ thể hơn và interface inheritance. Một mảng không bảo đảm thứ tự; nếu cần kết quả ổn định, **tự sắp xếp**.

Một class có thể chứa phương thức `private` nhưng `getMethods()` vẫn không thấy. Ngược lại, một class không khai báo phương thức nào vẫn có thể trả về rất nhiều phần tử từ `getMethods()` nhờ kế thừa phương thức public của `Object`.

## 2. Chọn đúng Class trước khi chọn API

Xét đoạn minh họa:

```java
Parent ref = new Child();

Class<?> staticType = Parent.class;
Class<?> runtimeType = ref.getClass(); // Child.class
```

Kiểu khai báo của `ref` là `Parent`. Kiểu thực tế của đối tượng được tham chiếu là `Child`. Hai API hoạt động trên **kiểu mà bạn gọi chúng**, vì vậy:

- `Parent.class.getDeclaredMethods()`: chỉ phương thức khai báo tại `Parent`.
- `Parent.class.getMethods()`: tập phương thức public của `Parent`, bao gồm public method kế thừa.
- `Child.class.getDeclaredMethods()`: chỉ phương thức khai báo tại `Child`, kể cả method private.
- `Child.class.getMethods()`: public method của `Child` và public method kế thừa thích hợp.

Một đối tượng Reflection `Method` còn có `getDeclaringClass()` để biết **lớp/interface nào thực sự khai báo phương thức đó**. Lớp khai báo có thể khác `Class<?>` dùng để bắt đầu tìm kiếm.

## 3. Chương trình Java 17+ đầy đủ

Tạo tệp `MethodDiscoveryDemo.java`:

```java
import java.lang.reflect.Method;

public class MethodDiscoveryDemo {
    interface Named {
        default String label() {
            return "from-interface";
        }
    }

    static class Parent {
        public String parentOnly() {
            return "parent";
        }

        public String describe() {
            return "Parent";
        }

        protected void protectedParent() {
        }

        private void privateParent() {
        }
    }

    static final class Child extends Parent implements Named {
        @Override
        public String describe() {
            return "Child";
        }

        public String childOnly() {
            return "child";
        }

        protected void protectedChild() {
        }

        private void privateChild() {
        }
    }

    static boolean contains(Method[] methods, String name) {
        for (Method method : methods) {
            if (method.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args)
            throws ReflectiveOperationException {

        Class<?> type = Child.class;
        Method[] publicMethods = type.getMethods();
        Method[] declaredMethods = type.getDeclaredMethods();

        System.out.println("public parentOnly: "
                + contains(publicMethods, "parentOnly"));
        System.out.println("declared parentOnly: "
                + contains(declaredMethods, "parentOnly"));
        System.out.println("public childOnly: "
                + contains(publicMethods, "childOnly"));
        System.out.println("declared childOnly: "
                + contains(declaredMethods, "childOnly"));
        System.out.println("public label: "
                + contains(publicMethods, "label"));
        System.out.println("declared label: "
                + contains(declaredMethods, "label"));
        System.out.println("public privateChild: "
                + contains(publicMethods, "privateChild"));
        System.out.println("declared privateChild: "
                + contains(declaredMethods, "privateChild"));
        System.out.println("public protectedChild: "
                + contains(publicMethods, "protectedChild"));
        System.out.println("declared protectedChild: "
                + contains(declaredMethods, "protectedChild"));
        System.out.println("public toString: "
                + contains(publicMethods, "toString"));
        System.out.println("declared toString: "
                + contains(declaredMethods, "toString"));

        Method inherited = type.getMethod("parentOnly");
        System.out.println("parentOnly declared in: "
                + inherited.getDeclaringClass().getSimpleName());

        Method overridden = type.getMethod("describe");
        System.out.println("describe declared in: "
                + overridden.getDeclaringClass().getSimpleName());

        Method fromParent = Parent.class.getMethod("describe");
        System.out.println("invoke Parent.describe on Child: "
                + fromParent.invoke(new Child()));

        try {
            type.getDeclaredMethod("parentOnly");
        } catch (NoSuchMethodException e) {
            System.out.println("declared parentOnly lookup: missing");
        }

        try {
            type.getMethod("privateChild");
        } catch (NoSuchMethodException e) {
            System.out.println("public privateChild lookup: missing");
        }
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac MethodDiscoveryDemo.java
java MethodDiscoveryDemo
```

Kết quả stdout:

```text
public parentOnly: true
declared parentOnly: false
public childOnly: true
declared childOnly: true
public label: true
declared label: false
public privateChild: false
declared privateChild: true
public protectedChild: false
declared protectedChild: true
public toString: true
declared toString: false
parentOnly declared in: Parent
describe declared in: Child
invoke Parent.describe on Child: Child
declared parentOnly lookup: missing
public privateChild lookup: missing
```

Chương trình kiểm tra **có tồn tại theo tên hay không**, nên output cố định dù thứ tự phần tử Reflection trả về thay đổi. Trong ứng dụng thật, tên method có thể overload; cần kiểm tra thêm tham số chứ không chỉ dùng hàm `contains` này.

Ý nghĩa từng nhóm kết quả:

1. `parentOnly()` là phương thức public **kế thừa** từ `Parent`. Tập `getMethods()` của `Child` nhìn thấy; tập `getDeclaredMethods()` không thấy.
2. `childOnly()` là public method **khai báo trực tiếp** ở `Child`, nên thấy trong cả hai tập.
3. `label()` là default method public của interface `Named`. `Child` kế thừa nó nhưng không tự khai báo; do đó chỉ thấy trong tập public tổng hợp.
4. `privateChild()` và `protectedChild()` được khai báo tại `Child`. Chúng xuất hiện trong tập declared; không phải public nên không xuất hiện trong tập `getMethods()`.
5. `toString()` có từ `Object`. Class thông thường kế thừa public method này, còn `Child` không trực tiếp khai báo nó.
6. `getDeclaringClass()` của `parentOnly` là `Parent`. Còn `describe` được override ở `Child`, nên method public được chọn trên `Child` có declaring class là `Child`.
7. `Parent.class.getMethod("describe").invoke(new Child())` trả về `"Child"` vì **dynamic dispatch** của instance method. Chọn metadata từ lớp cha không ép JVM gọi phần thân lớp cha.
8. `getDeclaredMethod("parentOnly")` trên `Child` và `getMethod("privateChild")` đều thất bại, nhưng vì **hai lý do khác nhau**: cái đầu không thuộc phần khai báo trực tiếp; cái sau không phải public.

## 4. Interface, Object và những trường hợp đặc biệt

### Public default method của interface

```java
interface HasName {
    default String name() {
        return "unknown";
    }
}

class Person implements HasName {
}
```

Trong đoạn minh họa này:

- `Person.class.getMethods()` thấy `name()` vì đây là public instance method được kế thừa từ interface.
- `Person.class.getDeclaredMethods()` không thấy `name()` vì class không khai báo phương thức đó.
- `HasName.class.getDeclaredMethods()` thấy `name()` vì interface khai báo trực tiếp.

Ngược lại, một **static method khai báo trên interface không được thừa kế bởi implementing class** theo cùng cách như default instance method. Một private method của interface cũng không phải public member method của class triển khai. Luôn phân biệt static/default/private khi xử lý interface.

### Object methods trên class và interface

Đối với class thường, `getMethods()` bao gồm public method thích hợp từ `Object`, trừ khi một override cụ thể hơn thay thế phương thức đó trong kết quả. Đối với **interface**, mảng `getMethods()` **không tự chứa các method ngầm định từ `Object`**. Vì vậy:

```java
interface Empty {
}
```

`Empty.class.getMethods()` là một mảng rỗng; không tự thêm `toString()` hoặc `hashCode()`. Đây là điểm khác với `new Object().getClass().getMethods()`.

Đối với **array class**, `getMethods()` trả các public method kế thừa từ `Object`, nhưng không trả `clone()`. Còn `getDeclaredMethods()` trên array type, primitive type hoặc `void` trả mảng rỗng.

### Phương thức override và method bị ẩn

Khi lớp con override instance method public của lớp cha, `getMethods()` áp dụng quy tắc chọn phương thức phù hợp và ưu tiên khai báo cụ thể hơn. Với method static, việc lớp con khai báo method cùng signature là **hiding**, không phải dynamic override. Do vậy đừng chỉ so sánh tên để suy luận method sẽ chạy khi invoke.

## 5. Lấy một Method theo tên và chữ ký

Hai API tương ứng với việc lấy một phương thức cụ thể:

```java
Class<?> type = Example.class;

Method first = type.getMethod("run", String.class);
Method second = type.getDeclaredMethod("run", String.class);
```

Đây là đoạn minh họa, `Example` cần tồn tại và có method phù hợp; xử lý `NoSuchMethodException` khi tra cứu thất bại.

| API | Phạm vi |
| --- | --- |
| `getMethod(name, parameterTypes...)` | Tìm **public member method**, kể cả được kế thừa |
| `getDeclaredMethod(name, parameterTypes...)` | Tìm method **được khai báo trực tiếp**, bất kể access |

Tham số của hai phương thức nhận các `Class<?>` đại diện cho **kiểu tham số hình thức** (formal parameter types), theo đúng thứ tự, **không phải kiểu trả về**. Với overload:

```java
class Printer {
    public void print(String text) {
    }

    public void print(int number) {
    }
}
```

Các lệnh tìm method đúng:

```java
Method textMethod = Printer.class.getMethod("print", String.class);
Method numberMethod = Printer.class.getMethod("print", int.class);
```

Đây là mã minh họa; khai báo `Method` cần `import java.lang.reflect.Method;` và lời gọi có thể ném `NoSuchMethodException`.

`getMethod("print", Object.class)` không tự áp dụng phép chọn overload theo chuyển kiểu `Object`/`String` trong mã nguồn: phải dùng đúng danh sách kiểu tham số đã khai báo. Đối với varargs, metadata của tham số cuối là kiểu **mảng**, ví dụ `String[].class`; dùng `Method.isVarArgs()` để biết đặc tính varargs.

`getMethod` và `getDeclaredMethod` không trả constructor. Nếu cần tìm constructor, dùng `getConstructor(...)` hoặc `getDeclaredConstructor(...)`.

## 6. Có Method metadata không đồng nghĩa được invoke

`getDeclaredMethods()` liệt kê cả private method, nhưng khả năng nhìn thấy metadata **không tự cấp quyền** gọi method đó.

```java
Method method = type.getDeclaredMethod("privateAction");
Object result = method.invoke(target);
```

Đây là đoạn minh họa, chỉ chạy được khi `type`, `target`, chữ ký và quyền truy cập phù hợp. `invoke` có thể ném `IllegalAccessException` nếu method không accessible; một method được invoke có thể ném ngoại lệ nghiệp vụ được bọc trong `InvocationTargetException`.

Một số code sử dụng `method.setAccessible(true)` hoặc `trySetAccessible()` để yêu cầu cho phép truy cập sâu. Nhưng trên Java 17+, **strong encapsulation của JPMS** có thể từ chối vì package của named module không mở cho caller. `setAccessible(true)` có thể ném `InaccessibleObjectException`; `trySetAccessible()` có thể trả `false`. Điều này không liên quan đến việc method có được trả về bởi `getDeclaredMethods()` hay không.

Với API ứng dụng, ưu tiên phương thức public hoặc interface/factory rõ ràng. Dùng Reflection sâu cho thành viên không public khi thật sự có lý do và được cấp quyền phù hợp.

## 7. Bridge và synthetic methods khiến mảng khác mã nguồn

`getDeclaredMethods()` không hứa mỗi phần tử tương ứng với một method do người lập trình tự viết. Compiler có thể sinh thêm **bridge** hoặc **synthetic methods**, điển hình khi generic erasure hoặc covariant return type ảnh hưởng đến phương thức override.

```java
class Holder<T> {
    public T value() {
        return null;
    }
}

class TextHolder extends Holder<String> {
    @Override
    public String value() {
        return "text";
    }
}
```

Đoạn minh họa này thường được `javac` biên dịch ra một method `String value()` và một bridge method tương thích chữ ký JVM mang kiểu trả về `Object`. Vì vậy `TextHolder.class.getDeclaredMethods()` có thể chứa hơn một method cùng tên `value`.

Kiểm tra metadata:

```java
for (java.lang.reflect.Method method
        : TextHolder.class.getDeclaredMethods()) {
    System.out.println(method.getName()
            + ", bridge=" + method.isBridge()
            + ", synthetic=" + method.isSynthetic());
}
```

Đoạn mã cần `TextHolder` đã được khai báo ở trên, không phải một chương trình độc lập. Thứ tự dòng không được bảo đảm. `getMethods()` cũng có thể trả method bridge public trong tập kết quả phù hợp.

Nên lọc `isBridge()` hoặc `isSynthetic()` **theo mục đích**: với bộ duyệt “API do developer chủ động khai báo” thường có thể cần lọc; với công cụ phân tích bytecode/compatibility thì nên giữ để phản ánh class file thực tế.

Về lý thuyết một class file có thể có nhiều method cùng **tên và kiểu tham số** nhưng khác kiểu trả về, dù Java source không cho phép khai báo overload chỉ khác return type. Vì vậy các phương thức tra cứu đơn `getMethod`/`getDeclaredMethod` có quy tắc lựa chọn khi nhiều ứng viên cùng tên và tham số; không giả định mảng `getDeclaredMethods()` luôn có đúng một phần tử ứng với mỗi cặp tên/tham số.

## 8. Duyệt đủ hierarchy khi cần cả method không public kế thừa về mặt cấu trúc

Không có một lời gọi `getDeclaredMethods()` nào trên `Child.class` tự lấy cả private/protected method khai báo ở mọi lớp cha. Nếu đang viết inspector cần xem **mỗi khai báo trên toàn class hierarchy**, hãy duyệt:

```java
Class<?> current = Child.class;

while (current != null) {
    for (java.lang.reflect.Method method
            : current.getDeclaredMethods()) {
        System.out.println(current.getSimpleName()
                + "." + method.getName());
    }
    current = current.getSuperclass();
}
```

Đây là đoạn minh họa sử dụng `Child` từ ví dụ ở mục 3. Các lưu ý:

- Vòng lặp chỉ duyệt **chuỗi class cha**, không duyệt superinterfaces. Muốn phân tích interface phải xử lý thêm `getInterfaces()` và hierarchy của chúng.
- Phương thức override ở lớp con và phương thức cùng chữ ký ở lớp cha có thể xuất hiện như **hai khai báo riêng**; đừng tự loại trùng nếu cần biết nguồn gốc từng method.
- Thứ tự mảng `getDeclaredMethods()` không được bảo đảm; cần sắp xếp nếu xuất báo cáo ổn định.
- Một phương thức private của lớp cha có thể được liệt kê metadata, nhưng không vì thế trở thành method public kế thừa của lớp con.

Nếu mục tiêu chỉ là API public có thể gọi từ bên ngoài, `getMethods()` thường ngắn gọn và sát hơn, không cần tự duyệt toàn bộ hierarchy.

## 9. Sắp xếp và lọc Method để công cụ ổn định

Khi in danh sách Reflection ra tài liệu, UI hoặc kết quả kiểm thử, không phụ thuộc thứ tự do JVM trả về. Ví dụ:

```java
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;

class MethodPrinter {
    static void printPublic(Class<?> type) {
        Method[] methods = type.getMethods();

        Arrays.sort(methods,
                Comparator.comparing(Method::getName)
                        .thenComparing(Method::toGenericString));

        for (Method method : methods) {
            System.out.println(method.getName() + " -> "
                    + method.getDeclaringClass().getName());
        }
    }
}
```

Đoạn này là class tiện ích minh họa, không có `main` riêng. Comparator làm thứ tự nhất quán **đối với cùng tập metadata**. Tập public methods vẫn có thể thay đổi khi kiểu hoặc phiên bản JDK đổi.

Một số bộ lọc hữu ích:

- `Modifier.isPublic(method.getModifiers())` để kiểm tra cờ public khi đang phân tích `getDeclaredMethods()`.
- `method.getDeclaringClass() == type` để tìm method có declaring class chính là kiểu đang xét.
- `method.isBridge()` / `method.isSynthetic()` để xử lý các method do compiler sinh.
- `method.getParameterTypes()`, `method.getReturnType()`, `method.isVarArgs()` khi cần phân biệt method theo chữ ký.
- `method.getAnnotation(YourAnnotation.class)` khi cần annotation cụ thể; phải xác định chính sách tìm annotation trên khai báo trực tiếp, override hoặc interface thay vì giả định annotation trên method tự động kế thừa.

Đừng lọc `getMethods()` bằng `getDeclaringClass()==type` rồi cho rằng kết quả bằng `getDeclaredMethods()`: cách đó vẫn **thiếu các method không public**.

## 10. Nên chọn API nào?

| Nhu cầu | Khuyến nghị |
| --- | --- |
| Liệt kê public API của class/interface cùng public methods được kế thừa | `getMethods()` |
| Tìm public member method theo tên + danh sách kiểu tham số | `getMethod(...)` |
| Kiểm kê method khai báo trực tiếp tại class, kể cả private/protected | `getDeclaredMethods()` |
| Tìm một method trực tiếp bất kể access | `getDeclaredMethod(...)` |
| Quét annotation trên toàn bộ khai báo của class hierarchy | Duyệt từng class và dùng `getDeclaredMethods()`, thêm interface khi cần |
| Chỉ lấy method thuộc một class nhất định | Kiểm tra `getDeclaringClass()`, đồng thời chọn tập đầu vào tương ứng yêu cầu access |
| Xuất kết quả theo thứ tự nhất quán | Sắp xếp `Method[]` bằng comparator |
| Gọi method không public | Xác minh nhu cầu, quyền truy cập Java/JPMS và receiver trước khi invoke |

Một lưu ý về proxy/framework: `obj.getClass()` có thể trả **runtime proxy class**, không phải class khai báo hợp đồng nghiệp vụ. Nếu muốn kiểm tra đúng API mà consumer được phép dùng, hãy bắt đầu bằng `YourInterface.class` hoặc class hợp đồng được cấu hình. Nếu mục tiêu là debug implementation, dùng runtime class với chính sách lọc phù hợp.

## 11. Sai lầm thường gặp

1. **Nhầm declared với kiểu biến:** `object.getClass().getDeclaredMethods()` khám phá method trực tiếp của **runtime class**, không phải kiểu static của biến.
2. **Tưởng `getMethods()` chỉ lấy method tự viết:** nó lấy cả public method từ superclass, interface và thường từ `Object`.
3. **Tưởng `getDeclaredMethods()` bao gồm private method ở lớp cha:** chỉ bao gồm private method khai báo **trên chính kiểu đang xét**.
4. **Thấy `Method` rồi lập tức `invoke`:** quyền truy cập, đối tượng receiver, kiểu tham số, JPMS và checked exceptions vẫn cần được xử lý.
5. **Lọc theo tên nhưng quên overload:** cần xét kiểu tham số, đôi khi kiểu trả về và bridge method.
6. **So số method trực tiếp với số khai báo Java source:** compiler có thể thêm bridge/synthetic methods.
7. **Tin vào thứ tự Reflection:** hai mảng không được API cam kết sắp xếp.
8. **Tự thêm method của `Object` vào interface:** `getMethods()` của interface không chứa các method ngầm định từ `Object`.
9. **Dùng `getMethods()` để tìm constructor:** constructor có API riêng.
10. **Suy ra dynamic dispatch từ declaring class:** phương thức có thể được tìm từ lớp cha nhưng invoke trên đối tượng lớp con vẫn chạy bản override.

## 12. Tự kiểm tra

Với `MethodDiscoveryDemo` ở mục 3:

1. `Child.class.getDeclaredMethods()` có chứa `parentOnly()` không? **Không**, vì method được khai báo ở `Parent`.
2. `Child.class.getMethods()` có chứa `parentOnly()` không? **Có**, vì public method được kế thừa.
3. `Child.class.getDeclaredMethods()` có chứa `privateChild()` không? **Có**, vì method khai báo trực tiếp, dù private.
4. `Child.class.getMethods()` có chứa `privateChild()` không? **Không**, vì tập này chỉ tìm public member methods.
5. `Child.class.getMethods()` có chứa `Named.label()` không? **Có**, vì đây là public default instance method được kế thừa.
6. `Method parentMethod = Parent.class.getMethod("describe"); parentMethod.invoke(new Child())` trả gì? **`"Child"`**, vì dispatch của phương thức instance override.
7. Nếu cần liệt kê private method từ từng lớp cha và lớp con, dùng một lần `getDeclaredMethods()` trên `Child` đã đủ chưa? **Chưa**; phải duyệt class hierarchy.

**Ghi nhớ:** `getMethods()` trả lời câu hỏi **“kiểu này có những phương thức public nào theo quy tắc kế thừa?”**; `getDeclaredMethods()` trả lời câu hỏi **“chính kiểu này khai báo những phương thức nào, ở mọi mức truy cập?”**. Luôn kết hợp đối tượng `Class<?>` xuất phát, `Method.getDeclaringClass()` và kiểm tra quyền truy cập để diễn giải đúng.

### Tài liệu chính thức

- [Java SE 17 — java.lang.Class](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html)
- [Java SE 17 — java.lang.reflect.Method](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html)
- [dev.java — Invoking Methods](https://dev.java/learn/api/reflection-method-handles/reflection/methods/)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="methods-004">Overload và chữ ký cần dùng để tìm phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Overload và chữ ký cần dùng để tìm phương thức bằng Java Reflection

## 1. Vấn đề: một tên có thể chỉ nhiều phương thức

Trong Java, **method overloading (nạp chồng phương thức)** cho phép nhiều phương thức có cùng tên trong một lớp nếu **danh sách kiểu tham số khác nhau**. Khác nhau có thể ở số lượng, kiểu hoặc thứ tự tham số.

```java
class Example {
    void send(String text) { }
    void send(int number) { }
    void send(String text, int times) { }
    void send(int times, String text) { }
}
```

Đây là bốn phương thức khác nhau. Cả bốn cùng tên `send` nhưng có các danh sách kiểu tham số lần lượt là:

- `(String)`
- `(int)`
- `(String, int)`
- `(int, String)`

Với Reflection, **chỉ biết tên `send` là chưa đủ** để xác định một overload. Ta cần chính xác tên phương thức và **danh sách `Class<?>[]` mô tả kiểu tham số đã khai báo, đúng thứ tự**.

Khái niệm **method signature** trong Java Language Specification (§8.4.2) chủ yếu gồm tên phương thức và các tham số kiểu theo quy tắc ngôn ngữ; **kiểu trả về không dùng để overload**. Khi nói tới *khóa tra cứu* của `Class.getMethod`/`getDeclaredMethod`, điều cần truyền trực tiếp là **tên** và **`Class<?>...` của các tham số sau erasure**.

## 2. Tra cứu phương thức bằng API nào?

Hai lời gọi phổ biến:

```java
Class<?> type = Example.class;

// Tìm method public, kể cả method public được kế thừa.
java.lang.reflect.Method method1 =
        type.getMethod("send", String.class, int.class);

// Tìm method khai báo trực tiếp trong Example, không phụ thuộc modifier.
java.lang.reflect.Method method2 =
        type.getDeclaredMethod("send", String.class, int.class);
```

| API | Phạm vi | Cần truyền | Nếu không tìm thấy |
| --- | --- | --- | --- |
| `getMethod(String name, Class<?>... parameterTypes)` | Phương thức `public` trên lớp, interface liên quan và phương thức `public` kế thừa theo quy tắc API | Tên và danh sách kiểu tham số chính xác | `NoSuchMethodException` |
| `getDeclaredMethod(String name, Class<?>... parameterTypes)` | Phương thức khai báo trực tiếp trong lớp/interface đang xét, gồm `private`, `protected`, package-private và `public` | Tên và danh sách kiểu tham số chính xác | `NoSuchMethodException` |
| `getMethods()` | Mảng các `Method` `public` trong phạm vi tìm kiếm của API | Không nhận tên/chữ ký | Mảng có thể rỗng |
| `getDeclaredMethods()` | Mảng phương thức khai báo trực tiếp, không lọc modifier | Không nhận tên/chữ ký | Mảng có thể rỗng |

**Ví dụ:** Với `void send(String, int)`, tra cứu `getDeclaredMethod("send", String.class, int.class)` sẽ khớp; đảo thứ tự thành `(int.class, String.class)` là overload khác. Java **không tìm phương thức “gần giống nhất”** nếu danh sách `Class` không khớp.

Phương thức **không có tham số** được tra cứu bằng `getMethod("ping")` hoặc `getMethod("ping", new Class<?>[0])`. Không tự thêm `Object.class` để thay thế danh sách rỗng.

### `getMethod` khác `getDeclaredMethod` cả ở phạm vi kế thừa

Nếu `Child extends Parent` và chỉ `Parent` khai báo `public void action(Number value)`:

- `Child.class.getMethod("action", Number.class)` tìm được phương thức `public` kế thừa.
- `Child.class.getDeclaredMethod("action", Number.class)` ném `NoSuchMethodException` nếu `Child` không tự khai báo hoặc override phương thức đó.
- Nếu một overload là `private`, `getMethod` sẽ không tìm thấy overload đó, dù tên/phần tham số đúng. `getDeclaredMethod` trên **lớp khai báo** có thể tìm ra metadata của nó.

## 3. Tra cứu dùng kiểu khai báo chính xác: không tự đổi kiểu

Giả sử lớp có:

```java
void calculate(int value) { }
void calculate(Integer value) { }
void calculate(Number value) { }
void calculate(Object value) { }
```

Các tham số `int`, `Integer`, `Number` và `Object` là bốn chữ ký khác nhau.

| Tra cứu | Có khớp overload tương ứng? | Lý do |
| --- | --- | --- |
| `getDeclaredMethod("calculate", int.class)` | Có | Kiểu khai báo `int` |
| `getDeclaredMethod("calculate", Integer.class)` | Có | Kiểu khai báo `Integer` |
| `getDeclaredMethod("calculate", Number.class)` | Có | Kiểu khai báo `Number` |
| `getDeclaredMethod("calculate", Object.class)` | Có | Kiểu khai báo `Object` |
| `getDeclaredMethod("calculate", short.class)` | Không | Không có overload `short`; không widening thành `int` |
| `getDeclaredMethod("calculate", Long.class)` | Không | Không có overload `Long`; không tự chọn `Number` |
| `getDeclaredMethod("calculate", String.class)` | Không | Không có overload `String`; không tự chọn `Object` |

Ở **bước tìm Method**, Reflection không áp dụng boxing/unboxing, widening primitive, chọn superclass/interface có thể gán được hay thuật toán overload resolution của trình biên dịch Java. Nói cách khác, `int.class != Integer.class`, và `String.class != Object.class`.

Ở **bước gọi Method đã tìm được** qua `Method.invoke`, cơ chế kiểm tra/chuyển đổi đối số lại khác. Ví dụ: đối tượng `Method` đại diện cho overload `calculate(int)` có thể nhận `Integer.valueOf(5)` rồi unbox khi gọi. Tuy nhiên, nó **vẫn gọi chính overload `(int)`**, không chuyển sang `(Integer)`.

## 4. Ví dụ hoàn chỉnh Java 17+: overload, kế thừa, varargs và generic

Lưu toàn bộ chương trình dưới tên `MethodOverloadLookupDemo.java`:

```java
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public class MethodOverloadLookupDemo {
    static class Base {
        public String inherited(Number number) {
            return "Base:" + number;
        }
    }

    static class Handler extends Base {
        public String choose(Object value) {
            return "Object";
        }

        public String choose(String value) {
            return "String";
        }

        public String choose(int value) {
            return "int";
        }

        public String choose(Integer value) {
            return "Integer";
        }

        public String combine(String text, int count) {
            return text.repeat(count);
        }

        public String pack(String... values) {
            return "pack:" + Arrays.toString(values);
        }

        public <T extends Number> String accept(T number) {
            return "accept:" + number;
        }

        private String hidden(double value) {
            return "hidden:" + value;
        }
    }

    public static void main(String[] args) throws Exception {
        Handler handler = new Handler();

        Method objectVersion =
                Handler.class.getMethod("choose", Object.class);
        Method stringVersion =
                Handler.class.getMethod("choose", String.class);
        Method intVersion =
                Handler.class.getMethod("choose", int.class);
        Method integerVersion =
                Handler.class.getMethod("choose", Integer.class);

        System.out.println("choose(Object) with String: "
                + objectVersion.invoke(handler, "abc"));
        System.out.println("choose(String): "
                + stringVersion.invoke(handler, "abc"));
        System.out.println("choose(int): "
                + intVersion.invoke(handler, Integer.valueOf(5)));
        System.out.println("choose(Integer): "
                + integerVersion.invoke(handler, Integer.valueOf(5)));

        Method inherited =
                Handler.class.getMethod("inherited", Number.class);
        System.out.println("inherited owner: "
                + inherited.getDeclaringClass().getSimpleName());

        Method combine =
                Handler.class.getMethod("combine", String.class, int.class);
        System.out.println("combine: " + combine.invoke(handler, "Hi", 2));

        try {
            Handler.class.getMethod("combine", int.class, String.class);
        } catch (NoSuchMethodException e) {
            System.out.println("Wrong order: NoSuchMethodException");
        }

        try {
            Handler.class.getMethod("choose", StringBuilder.class);
        } catch (NoSuchMethodException e) {
            System.out.println("choose(StringBuilder): NoSuchMethodException");
        }

        try {
            Handler.class.getMethod("choose", short.class);
        } catch (NoSuchMethodException e) {
            System.out.println("choose(short): NoSuchMethodException");
        }

        Method varargs = Handler.class.getMethod("pack", String[].class);
        System.out.println("pack parameter: "
                + varargs.getParameterTypes()[0].getTypeName());
        System.out.println("pack varargs: " + varargs.isVarArgs());
        System.out.println("pack invoke: "
                + varargs.invoke(handler, (Object) new String[] {"a", "b"}));

        Method generic =
                Handler.class.getMethod("accept", Number.class);
        System.out.println("generic erased type: "
                + generic.getParameterTypes()[0].getTypeName());
        System.out.println("generic metadata: "
                + generic.getGenericParameterTypes()[0].getTypeName());

        try {
            Handler.class.getMethod("accept", Integer.class);
        } catch (NoSuchMethodException e) {
            System.out.println("accept(Integer): NoSuchMethodException");
        }

        Method hidden =
                Handler.class.getDeclaredMethod("hidden", double.class);
        System.out.println("hidden private: "
                + Modifier.isPrivate(hidden.getModifiers()));
    }
}
```

Biên dịch/chạy từ thư mục chứa tệp với JDK 17 trở lên:

```bash
javac MethodOverloadLookupDemo.java
java MethodOverloadLookupDemo
```

Stdout:

```text
choose(Object) with String: Object
choose(String): String
choose(int): int
choose(Integer): Integer
inherited owner: Base
combine: HiHi
Wrong order: NoSuchMethodException
choose(StringBuilder): NoSuchMethodException
choose(short): NoSuchMethodException
pack parameter: java.lang.String[]
pack varargs: true
pack invoke: pack:[a, b]
generic erased type: java.lang.Number
generic metadata: T
accept(Integer): NoSuchMethodException
hidden private: true
```

Ý nghĩa từng phần:

1. `getMethod("choose", Object.class)` và `getMethod("choose", String.class)` lấy **hai đối tượng Method cho hai overload khác nhau**. Cùng truyền chuỗi `"abc"` lúc `invoke`, kết quả vẫn lần lượt là `Object` và `String`. **Không có chọn lại overload theo kiểu runtime của đối số**.
2. `int.class` và `Integer.class` tạo hai chữ ký độc lập. `Integer.valueOf(5)` có thể được unbox khi invoke overload `(int)`, nhưng không làm bước tra cứu nhập hai chữ ký thành một.
3. `getMethod("inherited", Number.class)` tìm phương thức công khai của `Base`; `getDeclaringClass()` xác định lớp **khai báo**.
4. Đảo `(String.class, int.class)` thành `(int.class, String.class)` gây `NoSuchMethodException` vì thứ tự tham số là một phần khóa tra cứu.
5. `StringBuilder.class` không được tự chuyển lên `Object.class`; `short.class` cũng không được tự chuyển sang `int.class`.
6. Varargs `String...` được lưu dưới dạng tham số mảng `String[]`, nên tìm bằng `String[].class`.
7. Generic `<T extends Number>` được **type erasure** thành `Number` trong kiểu tham số runtime. `getGenericParameterTypes()` vẫn có thể cung cấp thông tin biến kiểu `T`.
8. `getDeclaredMethod` cho phép tìm metadata của `private hidden(double)` trực tiếp, không có nghĩa mọi caller đều tự động được phép gọi phương thức `private`.

## 5. Varargs: biểu diễn trong chữ ký là kiểu mảng

Phương thức:

```java
public void log(String... messages) { }
```

về danh sách kiểu tham số trên `Method` tương đương khai báo `log(String[] messages)`. Hai cách khai báo không thể dùng làm hai overload riêng biệt trong một lớp.

```java
// Trong ngữ cảnh lớp có method public void log(String... messages).
Method logMethod = Example.class.getMethod("log", String[].class);
boolean isVarargs = logMethod.isVarArgs(); // true
```

Không tra bằng `getMethod("log", String.class)` chỉ vì lời gọi Java `log("a")` hợp lệ. Cũng không tra bằng `getMethod("log", String.class, String.class)` chỉ vì có thể gọi `log("a", "b")`. Tại bước Reflection lookup, số tham số của method varargs này vẫn là **một**.

Khi dùng `Method.invoke(Object receiver, Object... args)` trong Java, đối số cuối là varargs **của chính API invoke**. Muốn truyền một mảng `String[]` thành **một tham số** của target method, dùng:

```java
logMethod.invoke(instance, (Object) new String[] {"a", "b"});
```

Đoạn trên là mã minh họa trong ngữ cảnh đã có `logMethod` và `instance`. Cast `(Object)` khiến Java không trải mảng làm danh sách đối số của `Method.invoke`. Bản thân `Method.invoke` không tự đóng gói các chuỗi rời thành mảng cho target method varargs.

## 6. Generic: tra cứu dựa trên kiểu **sau erasure**

Ví dụ:

```java
public <T extends Number> void process(T value) { }
```

Đối với Reflection:

```java
// Ví dụ nằm trong phạm vi lớp có phương thức process như trên.
Method method = Example.class.getMethod("process", Number.class);
System.out.println(method.getParameterTypes()[0]); // class java.lang.Number
```

`T` có bound là `Number` nên kiểu tham số sau erasure là `Number`. Nếu `T` không có bound thì erasure thường là `Object` (có quy tắc riêng khi nhiều bound). Truyền `Integer.class` **không** thay thế `Number.class` để tra cứu phương thức generic đó.

Tương tự, tham số `List<String>` và `List<Integer>` đều có raw type runtime là `java.util.List`. Khi tra cứu:

```java
// Ví dụ nằm trong ngữ cảnh lớp có phương thức:
// public void consume(java.util.List<String> values) { ... }
Method method = Example.class.getMethod("consume", java.util.List.class);
```

Trong cùng một lớp, **không thể overload** chỉ dựa trên `List<String>` so với `List<Integer>`, vì hai khai báo xung đột do cùng erasure. `getGenericParameterTypes()` và `getTypeParameters()` có thể giúp khám phá metadata generic sau khi tra cứu được `Method`, nhưng tham số truyền vào `getMethod` vẫn là `Class<?>...`, không phải `ParameterizedType`.

## 7. Kiểu trả về có giúp chọn overload không?

Không. Hai khai báo trong **cùng một lớp Java** như sau không hợp lệ:

```java
// MINH HỌA MÃ KHÔNG BIÊN DỊCH ĐƯỢC: chỉ khác return type.
int parse(String source) { return 1; }
long parse(String source) { return 2L; }
```

Mỗi method trên cùng tên và cùng tham số `(String)`; kiểu trả về `int` hay `long` không tạo overload hợp lệ. Vì vậy không có API `getMethod(name, returnType, parameterTypes)`.

Cần phân biệt với phương thức tổng hợp (*synthetic*), phương thức cầu nối (*bridge method*) và quy tắc covariant return: compiler/JVM có thể lưu nhiều method có **cùng tên và cùng parameter types** nhưng **khác return type** trong bytecode. Khi đó, `Class.getMethod`/`getDeclaredMethod` áp dụng quy tắc lựa chọn của API (ưu tiên kiểu trả về đặc thù hơn khi các kiểu cho phép so sánh; một số trường hợp còn phụ thuộc quy tắc của API), chứ không nhận return type từ người gọi. Nếu đang kiểm tra bytecode tạo tự động, có thể duyệt `getDeclaredMethods()`, rồi xem `isBridge()`, `isSynthetic()`, `getReturnType()` và `getDeclaringClass()` để phân biệt các `Method`.

Trong lập trình ứng dụng thông thường, nên xác định đúng **tên + danh sách kiểu tham số** trước; chỉ kiểm tra kiểu trả về và metadata sau khi có đối tượng `Method`.

## 8. Tra cứu overload động: vì sao không thể chỉ dùng `arg.getClass()`?

Một lỗi phổ biến:

```java
// Mã minh họa có chủ đích: không phải bộ phân giải overload tổng quát.
Object arg = Integer.valueOf(7);
Method selected = Handler.class.getMethod("choose", arg.getClass());
```

Lệnh trên chọn `choose(Integer)` nếu có. Nhưng nếu API cần gọi `choose(int)`, `arg.getClass()` trả `Integer.class` nên **không tìm đúng chữ ký**. Với `arg == null` thì `arg.getClass()` còn gây `NullPointerException`. Nếu lớp chỉ có `choose(Number)`, `getMethod("choose", Integer.class)` vẫn thất bại mặc dù một `Integer` có thể gán cho tham số `Number` khi gọi.

Khi cần gọi một method có chữ ký đã được thiết kế, cách đúng và đơn giản nhất là lấy thông tin chữ ký từ **hợp đồng API**:

```java
// Ví dụ ngắn; Handler và instance xuất hiện trong chương trình đầy đủ ở trên.
Method method = Handler.class.getMethod("choose", int.class);
Object result = method.invoke(instance, Integer.valueOf(7));
```

Đoạn này không độc lập; `instance` là đối tượng `Handler` đã tạo và caller cần xử lý checked exceptions. `invoke` chạy đúng overload `(int)`; đối số `Integer` có thể được unbox tại bước gọi.

Nếu thật sự cần **lựa chọn overload từ dữ liệu runtime chưa biết trước chữ ký**, phải định nghĩa riêng chiến lược: thu thập các candidate theo tên/số lượng tham số; đánh giá assignability, boxing/unboxing, primitive widening, null, varargs và mức độ đặc hiệu; phát hiện trường hợp mơ hồ. Đó là **bài toán resolution riêng**, không phải tính năng của `getMethod`. Ưu tiên tránh tự mô phỏng overload resolution của `javac` nếu có thể xác định chữ ký từ interface hoặc giao ước từ trước.

## 9. Quy trình truy vấn Method an toàn trong dự án

1. **Xác định tên và danh sách kiểu khai báo** từ interface, tài liệu, annotation, schema hoặc contract; đừng phỏng đoán từ một tập đối số runtime.
2. **Chọn phạm vi tìm kiếm:** `getMethod` cho `public` kể cả kế thừa; `getDeclaredMethod` cho member khai báo trực tiếp, gồm không công khai.
3. **Truyền chính xác từng `Class` theo thứ tự:** primitive dùng `int.class`, wrapper dùng `Integer.class`, array dùng `String[].class`, generic dùng erasure như `List.class`.
4. **Kiểm tra metadata nhận được** bằng `getParameterTypes()`, `getDeclaringClass()`, `getReturnType()`, `isVarArgs()`, `isBridge()` khi cần.
5. **Tách giai đoạn gọi thực thi:** `Method.invoke` còn kiểm tra receiver/đối số/quyền truy cập; checked exception hoặc exception bọc nguyên nhân target xảy ra ở bước này.

Ví dụ logic để đối chiếu signature sau khi liệt kê:

```java
// Mã minh họa trong phương thức đã import java.lang.reflect.Method và java.util.Arrays.
Class<?>[] expected = {String.class, int.class};
for (Method method : Handler.class.getDeclaredMethods()) {
    if (method.getName().equals("combine")
            && java.util.Arrays.equals(method.getParameterTypes(), expected)) {
        System.out.println(method.getName()); // combine
    }
}
```

Lưu ý: `getDeclaredMethods()` **không đảm bảo thứ tự**, và mảng có thể chứa bridge/synthetic method. Không chọn `methods[0]` theo giả định overload đầu tiên là overload cần dùng.

## 10. Lỗi thường gặp

| Hiện tượng | Nguyên nhân chính | Cách sửa |
| --- | --- | --- |
| `NoSuchMethodException: choose(java.lang.Integer)` dù có `choose(int)` | Tra cứu dùng wrapper thay primitive | Dùng `int.class` |
| Có `choose(Object)` nhưng tra `choose(StringBuilder)` thất bại | Lookup yêu cầu đúng kiểu khai báo | Dùng `Object.class` nếu đó là chữ ký contract |
| Có `combine(String,int)` nhưng không tìm được | Đảo thứ tự `Class` | Giữ đúng `String.class, int.class` |
| Có `log(String...)` nhưng tra bằng `String.class` | Varargs khai báo thành `String[]` | Dùng `String[].class` |
| Có `accept(T extends Number)` nhưng tra bằng `Integer.class` | Sau erasure, `T` là `Number` | Dùng `Number.class` |
| `getDeclaredMethod` không thấy phương thức `public` của cha | Phương thức không được khai báo trực tiếp trên lớp con | Dùng `getMethod` hoặc chuyển sang lớp khai báo |
| `getMethod` không thấy phương thức `private` | API chỉ tra `public` | Dùng `getDeclaredMethod` đúng lớp, rồi kiểm tra quyền |
| `Method.invoke` lỗi `IllegalArgumentException` | Sai receiver, số lượng hoặc kiểu đối số khi gọi | Kiểm tra `getParameterTypes()` và cách truyền varargs |
| `Method.invoke` ném `InvocationTargetException` | Chính target method ném exception khi chạy | Xem `getCause()` để biết lỗi do phương thức đích |
| Kết quả overload khác lời gọi Java thông thường | `invoke` dùng Method đã chọn, không chọn lại theo argument runtime | Chọn đúng chữ ký trước khi gọi |

## 11. Tự kiểm tra

**Câu 1:** Lớp khai báo `void send(int count)` và `void send(Integer count)`. Hai lời gọi `getDeclaredMethod("send", int.class)` và `getDeclaredMethod("send", Integer.class)` có trả cùng một overload không?

**Đáp án:** Không. Đây là hai đối tượng `Method` cho hai khai báo có chữ ký tham số khác nhau.

**Câu 2:** Lớp có `void send(String[] values)`. Có thể tra bằng `getMethod("send", String.class, String.class)` để gọi với hai chuỗi không?

**Đáp án:** Không. Kiểu tham số đã khai báo là `String[]`, phải dùng `String[].class`. `send(String[])` có thể tương ứng với cú pháp Java `String...` nếu khai báo varargs.

**Câu 3:** Lớp có `void test(Number value)`. `getMethod("test", Integer.class)` có tìm thấy chỉ vì `Integer extends Number` không?

**Đáp án:** Không. Phải truyền `Number.class` để tra cứu chính xác.

**Câu 4:** Một phương thức `public` khai báo ở superclass có thể tìm qua `Child.class.getMethod(...)` không?

**Đáp án:** Có, khi phương thức đó thuộc tập member công khai được kế thừa/tra cứu theo quy tắc của `getMethod`. `getDeclaredMethod` trên con chỉ thấy phương thức con khai báo trực tiếp.

**Ghi nhớ:** Khi dùng Reflection tìm overload, truyền **đúng tên + đúng số lượng, thứ tự, kiểu `Class` của các tham số đã khai báo**. Kiểu trả về không dùng chọn overload, Java conversions không diễn ra trong bước lookup, và `Method.invoke` chỉ thực thi phương thức đã được chọn.

Tài liệu tham khảo: [Java SE 17 `Class.getMethod` / `getDeclaredMethod`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html), [Java SE 17 `Method`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html), [JLS §8.4.2 — Method Signature](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4.2).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="methods-005">invoke với receiver và mảng đối số</a>

<details>
<summary>Xem chi tiết</summary>

# Method.invoke với receiver và mảng đối số trong Java Reflection

## 1. Nắm đúng hai tham số của Method.invoke

Java Reflection cho phép gọi một phương thức được tìm ở runtime thông qua `java.lang.reflect.Method`. Chữ ký API:

```java
public Object invoke(Object obj, Object... args)
        throws IllegalAccessException, InvocationTargetException
```

Ý nghĩa:

- `Method` đã xác định **phương thức đích**: khai báo, tên, kiểu tham số. `invoke` không tự chọn overload dựa trên runtime class của từng đối số.
- `obj` là **receiver**, đối tượng thực thi instance method. Với static method, receiver bị bỏ qua, truyền `null` thường là cách rõ ràng nhất.
- `args` là danh sách đối số thực sự của method đích, ở dạng một mảng `Object[]`. Mỗi phần tử của mảng phải tương ứng **một formal parameter** theo đúng thứ tự, kể cả khi formal parameter đó chính nó là một array.
- `invoke` trả về `Object`: primitive được boxing; `void` trả `null`; reference/array được giữ nguyên kiểu đối tượng. Method đích ném exception sẽ được bọc trong `InvocationTargetException`.

Ví dụ, method đích `int add(int a, long b)` cần hai phần tử trong `args`; method đích `void print(String[] names)` chỉ cần một phần tử, chính là một `String[]`.

Điểm gây nhầm phổ biến nhất: **`Method.invoke` có cú pháp `Object... args`, còn method đích có thể cũng khai báo `String... values`. Đó là hai tầng varargs khác nhau**, cần phân biệt rõ.

## 2. Receiver cho instance method và static method

### Instance method cần receiver tương thích

Giả sử có lớp:

```java
class Greeter {
    public String hello(String name) {
        return "Hi, " + name;
    }
}
```

Trong một phương thức có xử lý ngoại lệ Reflection:

```java
Method hello = Greeter.class.getMethod("hello", String.class);
Greeter greeter = new Greeter();
Object answer = hello.invoke(greeter, "Mai"); // "Hi, Mai"
```

Receiver phải là một instance phù hợp với lớp hoặc interface khai báo underlying method. `hello.invoke(null, "Mai")` ném `NullPointerException`; `hello.invoke(new Object(), "Mai")` ném `IllegalArgumentException`. Những lỗi này khác lỗi trả về từ body `hello`.

Với **instance method bị override**, Reflection vẫn thực hiện dynamic method lookup theo runtime class của receiver. Một `Method` lấy từ lớp cha có thể thực thi implementation override của lớp con. `Method.getDeclaringClass()` vẫn mô tả lớp chứa khai báo ban đầu, không tự đổi thành runtime class của receiver.

### Static method bỏ qua receiver

```java
class Settings {
    public static String status() { return "READY"; }
}
```

```java
Method status = Settings.class.getMethod("status");
Object a = status.invoke(null);         // "READY"
Object b = status.invoke(new Object()); // "READY"
```

Đây là các đoạn minh họa, có import `java.lang.reflect.Method` và xử lý ngoại lệ trong phương thức chứa chúng. Receiver `new Object()` không thuộc `Settings` nhưng **không bị kiểm tra về tính tương thích**, bởi static method không chạy trên object đó. Static method có thể khiến declaring class **được khởi tạo** lần đầu khi `invoke`, nên việc gọi Reflection có thể chạy static initializer.

Cần phân biệt bỏ qua **giá trị** receiver với bỏ qua **đánh giá biểu thức** receiver: trong `status.invoke(buildReceiver())`, Java vẫn chạy `buildReceiver()` trước lời gọi, sau đó Reflection bỏ qua giá trị trả về đối với static method.

Cách chọn receiver có chủ ý:

```java
Object target = Modifier.isStatic(method.getModifiers()) ? null : instance;
Object result = method.invoke(target, arguments);
```

Đây là mảnh mã, không phải chương trình hoàn chỉnh; cần có các biến `method`, `instance`, `arguments` và import `Modifier`. Khi `method` là instance, `instance` phải đúng kiểu; với static, truyền `null` diễn đạt rõ rằng không cần một đối tượng.

| Loại method | Receiver | Hành vi |
| --- | --- | --- |
| Instance | Đối tượng đúng kiểu hoặc subtype | Có thể gọi override trên runtime class |
| Instance | `null` | `NullPointerException` |
| Instance | Đối tượng không tương thích | `IllegalArgumentException` |
| Static | `null` | Hợp lệ |
| Static | Bất kỳ object, kể cả không liên quan | Giá trị receiver bị bỏ qua |

Bảng giả định quyền truy cập, các đối số khác và class initialization không gây lỗi.

## 3. Mảng Object[] của Reflection là danh sách đối số, không phải nội dung array

Với method `add(int a, long b)`, hai cách truyền tương đương:

```java
addMethod.invoke(service, 2, 5);
addMethod.invoke(service, new Object[]{2, 5});
```

Hai formal parameters nghĩa là **hai** phần tử trong `Object[]`. Khi formal parameter là array như `String[] names`, phải bọc chính array này vào một **phần tử duy nhất** của danh sách:

```java
Method join = Service.class.getMethod("join", String[].class);
Object result = join.invoke(service, new Object[]{new String[]{"a", "b"}});
```

Ở đây `join` có một formal parameter `String[]`, vì vậy `Object[]` bên ngoài có đúng một phần tử, còn `String[]` bên trong có hai phần tử. Điều này đúng dù method khai báo `join(String[] values)` hay `join(String... values)`: trong metadata, cả hai có formal parameter type `String[].class`. `join.isVarArgs()` cho biết khai báo có cờ varargs hay không, nhưng **`Method.invoke` không tự gom phần tử `String` thành mảng cho method đích**.

**Tra cứu method phải khớp chính xác signature**:

```java
Method m = Service.class.getMethod("join", String[].class);
Class<?>[] formalTypes = m.getParameterTypes();
int count = m.getParameterCount(); // 1
```

`getMethod` và `getDeclaredMethod` nhận danh sách `Class<?>` của formal parameters, không phải danh sách wrapper runtime values, và không tự chọn overload theo widening. Chẳng hạn để tìm `f(long)` phải dùng `getMethod("f", long.class)`; `getMethod("f", int.class)` không tìm ra chỉ vì `int` có thể widen sang `long` khi gọi.

## 4. Chương trình Java 17+ có thể chạy đầy đủ

Lưu chương trình sau vào **`InvokeReceiverDemo.java`**:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class InvokeReceiverDemo {
    public static class Service {
        public String greet(String name) { return "Hello, " + name; }
        public int add(int a, long b) { return (int) (a + b); }
        public String join(String... values) { return String.join("/", values); }
        public static String label(int number) { return "ID=" + number; }
        public void noResult() { }
        public void boom() { throw new IllegalStateException("failed"); }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Service service = new Service();
        Method greet = Service.class.getMethod("greet", String.class);
        Method label = Service.class.getMethod("label", int.class);
        Method add = Service.class.getMethod("add", int.class, long.class);
        Method join = Service.class.getMethod("join", String[].class);
        Method noResult = Service.class.getMethod("noResult");
        Method boom = Service.class.getMethod("boom");

        System.out.println("instance = " + greet.invoke(service, "Ana"));
        System.out.println("static null = " + label.invoke(null, 7));
        System.out.println("static unrelated = " + label.invoke(new Object(), 7));

        Object result = add.invoke(service, Integer.valueOf(2), Integer.valueOf(5));
        System.out.println("widen = " + result + " (" +
                result.getClass().getSimpleName() + ")");
        System.out.println("isVarArgs = " + join.isVarArgs());
        System.out.println("join cast = " +
                join.invoke(service, (Object) new String[] {"a", "b"}));
        System.out.println("join wrapped = " +
                join.invoke(service, new Object[] {new String[] {"x", "y"}}));
        System.out.println("void returns null = " + (noResult.invoke(service) == null));

        try { greet.invoke(null, "Ana"); }
        catch (NullPointerException e) {
            System.out.println("null receiver = NullPointerException");
        }
        try { greet.invoke(new Object(), "Ana"); }
        catch (IllegalArgumentException e) {
            System.out.println("wrong receiver = IllegalArgumentException");
        }
        try { greet.invoke(service); }
        catch (IllegalArgumentException e) {
            System.out.println("wrong arity = IllegalArgumentException");
        }
        try { join.invoke(service, (Object[]) new String[] {"a", "b"}); }
        catch (IllegalArgumentException e) {
            System.out.println("spread array = IllegalArgumentException");
        }
        try { add.invoke(service, Long.valueOf(2), Integer.valueOf(5)); }
        catch (IllegalArgumentException e) {
            System.out.println("narrowing = IllegalArgumentException");
        }
        try { boom.invoke(service); }
        catch (InvocationTargetException e) {
            System.out.println("target cause = " +
                    e.getCause().getClass().getSimpleName() + ": " +
                    e.getCause().getMessage());
        }
    }
}
```

Dùng JDK 17 trở lên:

```bash
javac InvokeReceiverDemo.java
java InvokeReceiverDemo
```

Stdout đã được chạy và xác minh trên JDK 24:

```text
instance = Hello, Ana
static null = ID=7
static unrelated = ID=7
widen = 7 (Integer)
isVarArgs = true
join cast = a/b
join wrapped = x/y
void returns null = true
null receiver = NullPointerException
wrong receiver = IllegalArgumentException
wrong arity = IllegalArgumentException
spread array = IllegalArgumentException
narrowing = IllegalArgumentException
target cause = IllegalStateException: failed
```

Giải thích:

1. `greet` là instance method nên phải có receiver `service` đúng kiểu.
2. `label` là static method nên cả receiver `null` lẫn `new Object()` đều bị bỏ qua. Đây **không** là hành vi đúng cho instance method.
3. `add` khai báo `(int, long)`. `Integer(2)` được unbox thành `int`, `Integer(5)` unbox rồi widen từ `int` lên `long`. Kết quả `int` được boxing thành `Integer`.
4. `join` có `isVarArgs() == true` và signature chỉ nhận **một `String[]`**; hai cách gọi thành công đều truyền mảng chuỗi thành **một argument**.
5. `noResult` khai báo `void`; giá trị trả về từ Reflection là `null`.
6. Các lỗi receiver `null`, sai lớp, sai số lượng args, dùng array bị bung, hoặc chuyển `Long` sang `int` đều biểu hiện bằng loại ngoại lệ đã in.
7. `boom` ném `IllegalStateException` bên trong body; mã gọi Reflection nhận `InvocationTargetException` và lấy nguyên nhân từ `getCause()`.

## 5. Phân biệt hai tầng varargs, kể cả các dạng null

### Array của method đích phải nằm trong một ô của Object[]

Với `join(String... values)`, runtime formal parameter là `String[]`:

```java
join.invoke(service, (Object) new String[]{"a", "b"});       // đúng
join.invoke(service, new Object[]{new String[]{"a", "b"}});   // đúng
join.invoke(service, (Object[]) new String[]{"a", "b"});     // sai arity
```

Cast `(Object)` trong dòng thứ nhất ép Java coi array `String[]` là **một argument** của API `invoke`, nhờ đó tạo danh sách `Object[]` có một phần tử. Dòng thứ hai tự xây danh sách có một phần tử. Dòng thứ ba cast cả `String[]` thành `Object[]` của `invoke` (hợp lệ vì `String[]` là subtype `Object[]`) và do đó bung **hai chuỗi** thành **hai formal arguments**; `join` yêu cầu chỉ một `String[]`, dẫn tới `IllegalArgumentException`.

Viết `join.invoke(service, new String[]{"a","b"})` không có cast cũng dễ gây compiler warning “non-varargs call for last parameter” và trải array theo tầng ngoài. Với code framework cần ổn định và dễ đọc, nên luôn xây `new Object[]{...}` hoặc cast array sang `Object`.

Với `int[]`, đừng cố ép `int[]` sang `Object[]`: mảng primitive không phải subtype của `Object[]`. Ví dụ method `sum(int[] numbers)` phải nhận:

```java
sumMethod.invoke(receiver, new Object[]{new int[]{1, 2, 3}});
```

Đây là mảnh minh họa, với `sumMethod` là metadata của method nhận `int[]`. Reflection không tự biến `Integer[]` thành `int[]` hay tự unbox từng phần tử trong array.

### null receiver, null danh sách và một null argument

Các lệnh sau có ngữ nghĩa khác nhau (biến `greet`, `noResult`, `service` lấy từ ví dụ chính):

| Lời gọi | Ý nghĩa |
| --- | --- |
| `noResult.invoke(service)` | Không có argument; đúng vì method đích không tham số |
| `noResult.invoke(service, new Object[0])` | Danh sách rỗng; hợp lệ |
| `noResult.invoke(service, (Object[]) null)` | Mảng args là `null`, hợp lệ khi method đích không tham số |
| `greet.invoke(service, (Object) null)` | Một argument có giá trị `null`; body trả `"Hello, null"` |
| `greet.invoke(service, new Object[]{null})` | Tương tự: một argument `null` |
| `greet.invoke(service, (Object[]) null)` | Danh sách có 0 argument, trong khi method cần 1: `IllegalArgumentException` |
| `greet.invoke(null, "Ana")` | Receiver `null` của instance method: `NullPointerException` |

`invoke(service, null)` không cast có thể gây compiler warning hoặc được hiểu theo dạng varargs không mong muốn. Viết rõ `(Object) null` để truyền **một argument null** hoặc `new Object[0]`/`(Object[]) null` để truyền **không argument**. Nếu formal parameter là primitive (`int`, `boolean`...), một giá trị `null` không thể unbox và bị từ chối.

**Lưu ý:** khi mảng `args` của `Method.invoke` là `null` và method đích không có formal parameters, API chấp nhận điều này; không suy ra rằng mọi method có thể được gọi với danh sách `null`.

## 6. Chuyển đổi đối số và trả về

### Method invocation conversions

Java kiểm tra số lượng args và tương thích kiểu tại lời gọi `invoke`:

- Reference parameter nhận `null` hoặc reference subtype phù hợp (ví dụ `String` cho `CharSequence`).
- Primitive parameter nhận wrapper thích hợp được unbox; các chuyển đổi **widening primitive** như `int` sang `long` được phép.
- **Narrowing primitive** như `long` sang `int` không tự diễn ra. Một `String` chứa chữ số không được tự parse thành `int`.
- Các chuyển đổi áp dụng **cho mỗi phần tử `Object[]`**, không tự lan vào phần tử của một array formal parameter.

Ví dụ:

```java
Method m = Service.class.getMethod("add", int.class, long.class);
m.invoke(service, Integer.valueOf(2), Integer.valueOf(5)); // OK
m.invoke(service, Long.valueOf(2), Integer.valueOf(5));    // IllegalArgumentException
m.invoke(service, (Object) null, Integer.valueOf(5));       // IllegalArgumentException
```

Đây là mảnh code minh họa, không phải chương trình chạy độc lập. Method đích `add` không được chọn lại để khớp `Long`; `Method` đã được tra cứu theo signature `(int,long)`.

### Giá trị trả về

| Return type của method đích | Giá trị `invoke` trả |
| --- | --- |
| `String` hoặc reference type | Reference kết quả, có thể `null` |
| `int`, `long` hoặc primitive khác | Wrapper như `Integer`, `Long` |
| `void` | `null` |
| `int[]` hoặc array khác | Chính array, không boxing từng phần tử |

`method.getReturnType()` mô tả kiểu khai báo. `result.getClass()` chỉ mô tả runtime class nếu kết quả khác `null`; không phân biệt được `void` với method reference trả `null`. Với generic, `getGenericReturnType()` xem được generic signature khi metadata có sẵn; runtime invoke không kiểm tra mọi type argument đã bị erasure.

## 7. Exception: lỗi gọi sai khác lỗi bên trong method

Theo Javadoc Java 17 của `Method.invoke`:

| Lỗi | Nguyên nhân điển hình |
| --- | --- |
| `NoSuchMethodException` | Xảy ra khi `getMethod`/`getDeclaredMethod` không tìm thấy đúng chữ ký; đây là lỗi **tra cứu**, trước khi invoke |
| `IllegalAccessException` | Caller không có quyền gọi underlying method theo Java access control |
| `NullPointerException` | Receiver `null` khi underlying method là instance |
| `IllegalArgumentException` | Receiver không đúng kiểu, sai số formal/actual args hoặc một argument không chuyển đổi được sang formal type |
| `InvocationTargetException` | Method đích đã chạy và ném exception/error; đọc `getCause()` |
| `ExceptionInInitializerError` | Khởi tạo declaring class do gọi static method thất bại |
| `InaccessibleObjectException` | Có thể xuất hiện khi yêu cầu deep reflection bằng `setAccessible(true)` không được module cho phép |

Ví dụ pattern xử lý đúng gốc lỗi:

```java
try {
    method.invoke(receiver, arguments);
} catch (InvocationTargetException ex) {
    Throwable underlying = ex.getCause();
    // Xử lý/ghi log ngoại lệ của chính method đích.
} catch (IllegalAccessException ex) {
    // Kiểm tra quyền truy cập của method.
} catch (IllegalArgumentException ex) {
    // Kiểm tra receiver, arity và conversion của args.
}
```

Đây là mảnh code cần ngữ cảnh `method`, `receiver`, `arguments`. Không gộp `InvocationTargetException` thành lỗi “sai tham số Reflection”; nó là **lớp bọc quanh lỗi đã xảy ra bên trong method**. Với ứng dụng thực tế, policy xử lý checked exceptions, unchecked exceptions và `Error` ở tầng framework phải được thiết kế rõ.

### getMethod khác getDeclaredMethod và quyền truy cập

- `getMethod(name, parameterTypes...)` tìm public method theo quy tắc tra cứu, có thể thấy inherited public method.
- `getDeclaredMethod(name, parameterTypes...)` tìm method khai báo **trực tiếp** trong class, bao gồm private, không tự duyệt superclasses.
- Lấy được `Method` private **không đồng nghĩa gọi được**. `Method.invoke` có thể ném `IllegalAccessException`; `trySetAccessible()`/`setAccessible(true)` chỉ có thể nới quyền nếu quy tắc module và runtime cho phép.
- `Method.invoke` trên instance method được override có dynamic dispatch; static method không override theo nghĩa ấy. Việc truyền subtype receiver cho static method không làm chọn một static method bị hiding ở subtype.
- Nếu method đích nằm trong Java module không mở package cho caller, không dựa vào Reflection để vượt encapsulation. Ưu tiên public API hoặc cấu hình `opens` khi thực sự cần.

## 8. Một utility giảm lỗi receiver và kiểm soát signature

Đây là class Java hoàn chỉnh dùng làm helper trong dự án (không có `main`):

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class ReflectiveCalls {
    private ReflectiveCalls() {}

    public static Object call(Method method, Object instance, Object[] arguments)
            throws IllegalAccessException, InvocationTargetException {
        Object target = Modifier.isStatic(method.getModifiers())
                ? null
                : instance;
        return method.invoke(target, arguments);
    }
}
```

Helper này chỉ thực hiện lựa chọn receiver rõ nghĩa. Nó **không tự sửa** arity, ép kiểu tùy ý, tra cứu overload, bypass private/module access hay bắt lỗi của method đích. Trước khi dùng, mã gọi cần biết:

1. Method nào đang được yêu cầu và formal parameter types chính xác (`getParameterTypes()`).
2. Method static hay instance (`Modifier.isStatic(getModifiers())`), receiver phải đúng kiểu nếu instance.
3. `Object[]` chứa đúng một phần tử cho mỗi formal parameter, đặc biệt array formal và varargs.
4. Caller có quyền gọi method và liệu static class initialization có tác dụng phụ hay không.
5. Cách xử lý `InvocationTargetException.getCause()` và return value đã được boxing/`null`.

Khi method cần gọi đã biết trước tại compile time, lời gọi Java bình thường thường có type checking rõ ràng hơn. Reflection phù hợp khi tên/kiểu method chỉ biết tại runtime, ví dụ mapper, test runner, plugin framework hoặc dependency injection container.

## 9. Tự kiểm tra

**Câu 1.** `Method m` mô tả `static void run()`. `m.invoke(new Object())` có sai receiver không? **Không**; target bị bỏ qua. Nên dùng `m.invoke(null)` để rõ ràng.

**Câu 2.** Method đích là `void accept(String[] names)`. Có truyền hai chuỗi bằng `m.invoke(receiver, (Object[]) new String[]{"A","B"})` không? **Không**: call này tạo hai actual args `String`, trong khi formal chỉ có một `String[]`. Dùng `m.invoke(receiver, (Object) new String[]{"A","B"})` hoặc `m.invoke(receiver, new Object[]{new String[]{"A","B"}})`.

**Câu 3.** `m` mô tả `void greet(String s)`. `m.invoke(receiver, (Object) null)` truyền gì? **Một `null`**. `m.invoke(receiver, (Object[]) null)` thì sao? **Không có actual args**, thiếu tham số và thất bại.

**Câu 4.** Method trả `long`; `Method.invoke` trả loại gì? **`Object` chứa `Long`** qua boxing. Method trả `void` thì `invoke` trả `null`.

**Câu 5.** Method đích ném `IOException`. Reflection gọi phương thức này sẽ ném thẳng `IOException`? **Không**; `InvocationTargetException` bọc exception gốc, xem `getCause()`.

**Câu 6.** Đã có `Method` của instance method lớp `Base`; receiver là `Derived` override method ấy. Chạy bản nào? **Implementation override trên `Derived`**. Nếu là static method thì không có dynamic override.

**Câu 7.** Tại sao `getMethod("f", int.class)` không tìm được `f(long)` dù `Integer` có thể widen thành `long` khi `invoke`? **Tra cứu dùng formal signature chính xác; chuyển đổi giá trị chỉ diễn ra lúc gọi**.

## 10. Tài liệu chính thức

- [Java SE 17 Javadoc — Method.invoke(Object, Object...)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html)
- [Java SE 17 Javadoc — Class.getMethod / getDeclaredMethod](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html)
- [Java SE 17 Javadoc — Executable.isVarArgs](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Executable.html)
- [Java SE 17 Javadoc — InvocationTargetException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationTargetException.html)
- [JLS 17, Section 15.12.4: Runtime Evaluation of Method Invocation](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.12.4)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="methods-006">Gọi phương thức static bằng receiver null</a>

<details>
<summary>Xem chi tiết</summary>

# Gọi phương thức static bằng receiver `null` qua Java Reflection

## 1. Ý nghĩa của receiver trong `Method.invoke`

Trong Java Reflection, đối tượng `java.lang.reflect.Method` đại diện cho một phương thức và cho phép gọi nó ở runtime bằng API:

```java
Object result = method.invoke(receiver, arg1, arg2);
```

Chữ ký API là:

```java
public Object invoke(Object obj, Object... args)
        throws IllegalAccessException, InvocationTargetException
```

Có **hai vai trò khác nhau** trong lệnh gọi:

- `obj` (hay `receiver`): đối tượng mà phương thức **instance** sẽ chạy trên đó.
- `args`: các **tham số thực tế** được truyền vào phương thức, theo đúng thứ tự khai báo.

Với phương thức **`static`**, lệnh gọi không gắn với một instance cụ thể. Theo hợp đồng của `Method.invoke`, tham số `obj` **bị bỏ qua và có thể là `null`**. Vì vậy, cách gọi đúng, rõ ràng nhất là:

```java
Method method = Example.class.getDeclaredMethod("hello");
method.invoke(null); // Gọi Example.hello() nếu method là static và có thể truy cập
```

Ở đây `null` là **receiver**, không phải tham số đầu tiên của `hello()`. Ví dụ trên giả định có lớp `Example` và phương thức `hello()` không tham số.

**Kết luận trực tiếp:** `method.invoke(null, ...)` là cách gọi bình thường cho `static` method; không cần tạo đối tượng bằng `new Example()` trước.

## 2. Vì sao `static` nhận receiver `null` còn instance method thì không?

Xét hai kiểu phương thức:

```java
class Demo {
    static String staticHello() {
        return "static";
    }

    String instanceHello() {
        return "instance";
    }
}
```

Biểu thức Java thông thường:

```java
Demo.staticHello();             // Không cần instance
new Demo().instanceHello();     // Cần instance
```

Tương ứng trong Reflection:

```java
Method staticMethod = Demo.class.getDeclaredMethod("staticHello");
String a = (String) staticMethod.invoke(null);

Method instanceMethod = Demo.class.getDeclaredMethod("instanceHello");
String b = (String) instanceMethod.invoke(new Demo());
// instanceMethod.invoke(null); // Ném NullPointerException
```

Đây là các **đoạn minh họa** đặt trong ngữ cảnh có xử lý checked exceptions, chẳng hạn phương thức `main` khai báo `throws Exception`.

Với instance method, `receiver` phải là instance tương thích với lớp/giao diện khai báo phương thức. Nếu receiver là `null`, `Method.invoke` ném `NullPointerException`; nếu là đối tượng **không đúng kiểu**, nó ném `IllegalArgumentException`.

Với `static` method, runtime không tra cứu đối tượng instance để chạy phương thức. Thậm chí nếu truyền một object khác `null`, Reflection **bỏ qua object ấy**. Ví dụ `staticMethod.invoke(new Object())` vẫn gọi phương thức static, miễn là access và arguments đều hợp lệ. Tuy nhiên, **nên truyền `null`** để thể hiện đúng ý nghĩa: không cần instance.

| Method được phản ánh | Receiver truyền vào | Kết quả về receiver |
| --- | --- | --- |
| `static` | `null` | Hợp lệ; không cần object |
| `static` | Object khác `null`, kể cả không cùng kiểu | Bị bỏ qua |
| Instance | Object đúng kiểu | Có thể gọi; còn tùy access/arguments |
| Instance | `null` | `NullPointerException` |
| Instance | Object không phải instance tương thích | `IllegalArgumentException` |

Bảng chỉ bàn về **receiver**; việc gọi thành công còn cần số lượng/kiểu tham số, quyền truy cập và không có lỗi phát sinh khi thực thi.

## 3. Nhận diện phương thức `static` trước khi gọi

Khi xử lý các `Method` được phát hiện động, hãy dùng:

```java
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

boolean isStatic = Modifier.isStatic(method.getModifiers());
```

`Method.getModifiers()` trả về tập modifier của phương thức. `Modifier.isStatic(...)` kiểm tra cờ `static`. Đây là cách rõ ràng để quyết định receiver mà Reflection cần:

```java
Object receiver = Modifier.isStatic(method.getModifiers())
        ? null
        : existingInstance;
Object result = method.invoke(receiver, args);
```

Đây là **mẫu mã**: `method`, `existingInstance` và `args` phải được xác định trước. Với instance method, cần bảo đảm `existingInstance` đúng kiểu. Nếu caller không có instance nhưng phương thức không phải static, không tự suy ra có thể truyền `null`.

### Cách lấy đúng `Method`

- `SomeClass.class.getMethod("name", ParamType.class)` tìm phương thức `public` theo tên và **kiểu tham số**, bao gồm public method được thừa kế theo quy tắc API.
- `SomeClass.class.getDeclaredMethod("name", ParamType.class)` tìm phương thức khai báo **trực tiếp** trong lớp/giao diện đó, kể cả không `public`, nhưng không tìm tự động trên superclass.
- Cả hai trả `Method` và có thể ném `NoSuchMethodException` khi không khớp tên/chữ ký.
- Khi có overload, phải chọn đúng các `Class<?>` của kiểu tham số. `getDeclaredMethod("add", int.class, int.class)` khác `getDeclaredMethod("add", Integer.class, Integer.class)`.

`getDeclaredMethod` không tự cấp quyền gọi một method `private`. Đối với một `static` private method, **receiver `null` vẫn hợp lệ**, nhưng truy cập có thể bị chặn bởi Java access control / JPMS. Dùng `trySetAccessible()` có kiểm soát nếu thực sự cần truy cập sâu.

## 4. Ví dụ Java 17+ hoàn chỉnh

Lưu toàn bộ chương trình vào tệp **`StaticReceiverDemo.java`**:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class StaticReceiverDemo {
    public static String greet(String name) {
        return "Hi " + name;
    }

    public static int add(int a, int b) {
        return a + b;
    }

    public static void ping() {
        System.out.println("ping");
    }

    public static String join(String[] values) {
        return String.join("-", values);
    }

    public static void fail() {
        throw new IllegalStateException("failure");
    }

    public String instanceHello() {
        return "instance";
    }

    public static void main(String[] args) throws Exception {
        Class<StaticReceiverDemo> type = StaticReceiverDemo.class;

        Method greet = type.getMethod("greet", String.class);
        System.out.println("null receiver: "
                + greet.invoke(null, "Lan"));
        System.out.println("ignored receiver: "
                + greet.invoke(new Object(), "Minh"));
        System.out.println("is static: "
                + Modifier.isStatic(greet.getModifiers()));

        Method add = type.getMethod("add", int.class, int.class);
        Object sum = add.invoke(null, 3, 4);
        System.out.println("sum: " + sum
                + ", type: " + sum.getClass().getSimpleName());

        Method ping = type.getMethod("ping");
        Object noResult = ping.invoke(null);
        System.out.println("void result is null: "
                + (noResult == null));

        Method join = type.getMethod("join", String[].class);
        System.out.println("join: "
                + join.invoke(null, (Object) new String[] {"red", "blue"}));

        try {
            // Cố tình truyền 2 đối số thay vì 1 đối số String[].
            join.invoke(null, (Object[]) new String[] {"red", "blue"});
        } catch (IllegalArgumentException e) {
            System.out.println("spread array: IllegalArgumentException");
        }

        Method instance = type.getMethod("instanceHello");
        try {
            instance.invoke(null);
        } catch (NullPointerException e) {
            System.out.println("instance null: NullPointerException");
        }

        Method fail = type.getMethod("fail");
        try {
            fail.invoke(null);
        } catch (InvocationTargetException e) {
            System.out.println("target failure: "
                    + e.getCause().getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp:

```bash
javac StaticReceiverDemo.java
java StaticReceiverDemo
```

**Stdout chính xác:**

```text
null receiver: Hi Lan
ignored receiver: Hi Minh
is static: true
sum: 7, type: Integer
ping
void result is null: true
join: red-blue
spread array: IllegalArgumentException
instance null: NullPointerException
target failure: IllegalStateException
```

**Phân tích chương trình:**

1. `greet.invoke(null, "Lan")` gọi `greet` dù không có `StaticReceiverDemo` instance, vì phương thức có modifier `static`.
2. `greet.invoke(new Object(), "Minh")` cũng thành công: `new Object()` là receiver **bị bỏ qua**. Cách này hợp lệ nhưng gây nhầm lẫn; khi viết code thực tế nên dùng `null`.
3. `Modifier.isStatic(greet.getModifiers())` nhận đúng kiểu phương thức, trả `true`.
4. `add.invoke(null, 3, 4)` truyền hai tham số int (được đưa vào mảng `Object...` và boxing bởi Java). Reflection tự chuyển `Integer` về `int` khi gọi. `Method.invoke` trả về `Object` nên kết quả primitive `int` được **box** lại thành `Integer`.
5. `ping` có kiểu trả về `void`: thân phương thức in `ping`, sau đó `invoke` trả `null`.
6. `join(String[])` nhận **một** tham số là mảng. Phép cast `(Object)` giúp truyền mảng thành một đối số duy nhất của `Method.invoke`.
7. Phép cast `(Object[])` ở nhánh lỗi khiến nội dung mảng được xem thành **hai** đối số của varargs `invoke`; phương thức `join` chỉ nhận **một** tham số nên Reflection ném `IllegalArgumentException`.
8. `instanceHello` không có modifier `static`; gọi với receiver `null` ném `NullPointerException`.
9. Phương thức `fail` chạy thật rồi ném `IllegalStateException`. Reflection bọc lỗi của phương thức đích bằng `InvocationTargetException`; `getCause()` trả nguyên nhân gốc.

## 5. Phân biệt receiver `null` và tham số `null`

`Method.invoke` có hai “tầng” tham số:

```java
method.invoke(obj, args...);
              ^    ^
              |    +-- đối số của phương thức đích
              +------- receiver của lệnh invoke
```

Với phương thức:

```java
public static String describe(String text) {
    return text == null ? "<null>" : text;
}
```

Lấy `Method` rồi gọi:

```java
Method describe = Example.class
        .getMethod("describe", String.class);

Object a = describe.invoke(null, (Object) null);
```

Đây là **đoạn minh họa**, không phải tệp độc lập. `Example` phải có phương thức `describe` như đã khai báo ở trên.

Trong `describe.invoke(null, (Object) null)`:

- `null` thứ nhất là receiver, bị bỏ qua vì `describe` static.
- `(Object) null` thứ hai là **một tham số `String` có giá trị `null`**, nên lệnh gọi hợp lệ.
- Kết quả là chuỗi `"<null>"`.

Điểm đặc biệt của varargs `Object... args`: `describe.invoke(null, (Object[]) null)` có **mảng đối số là `null`**, được xem như **không truyền tham số**. Với `describe` đòi đúng một tham số, điều này gây `IllegalArgumentException` do sai số lượng tham số. Tương tự, `describe.invoke(null, new Object[0])` là lời gọi không có tham số đích, không phải một tham số `null`.

Tránh viết `describe.invoke(null, null)` khi chủ đích là truyền một tham số null; Java có thể xử lý `null` cuối cùng như một varargs array null và trình biên dịch cảnh báo lời gọi không varargs. Hãy viết tường minh `(Object) null` hoặc `new Object[] {null}`.

| Mục đích | Cú pháp rõ ràng |
| --- | --- |
| Static method không tham số | `method.invoke(null)` |
| Static method nhận một tham số String `"a"` | `method.invoke(null, "a")` |
| Static method nhận một tham số có giá trị `null` | `method.invoke(null, (Object) null)` |
| Static method nhận một tham số mảng `String[]` | `method.invoke(null, (Object) new String[] {"a", "b"})` |
| Static method nhận hai tham số `int` | `method.invoke(null, 1, 2)` |

**`null` receiver và `null` parameter là hai quyết định hoàn toàn khác nhau.** Đừng nhầm một lời gọi không có receiver với một phương thức nhận đầu vào null.

## 6. Vì sao tham số mảng dễ gây lỗi?

`Method.invoke` được khai báo nhận `Object... args`; về mặt Java, đây là biến thể của một mảng `Object[]` chứa các tham số thực tế. Điều đó **không đồng nhất** với lời khai báo varargs của **phương thức đích**.

Ví dụ, dù phương thức đích chỉ nhận đúng một `String[]`, khi viết:

```java
Method m = SomeClass.class.getMethod("process", String[].class);
String[] input = {"x", "y"};

m.invoke(null, (Object) input);           // 1 tham số String[]: đúng
m.invoke(null, new Object[] {input});     // 1 tham số String[]: đúng
// m.invoke(null, (Object[]) input);      // 2 tham số String: sai
```

Đây là đoạn minh họa giả định `SomeClass` có public static method `process(String[])`, không phải chương trình độc lập.

- `(Object) input` ngăn mảng `String[]` bị **trải** thành nhiều phần tử của varargs `invoke`.
- `new Object[] {input}` biểu diễn tường minh mảng **chứa một đối số**, và đối số đó mới là mảng `String[]`.
- `(Object[]) input` biến mảng `String[]` thành mảng varargs truyền cho `invoke`, khiến Reflection thấy mỗi phần tử String là **một tham số riêng**.
- Không suy luận từ `m.isVarArgs()` rằng `Method.invoke` sẽ tự động gộp các đối số thành mảng cho phương thức đích. Reflection yêu cầu cung cấp bộ đối số tương thích với số/kiểu tham số hình thức.

Đây là lỗi thuộc **đóng gói `args`**; nó không liên quan đến tính đúng sai của `receiver = null`.

## 7. Điều kiện truy cập vẫn áp dụng cho static method

`null` receiver **không** có nghĩa là bỏ qua Java access control.

Ví dụ:

```java
class Secrets {
    private static String token() {
        return "hidden";
    }
}
```

`Secrets.class.getDeclaredMethod("token")` có thể trả đối tượng `Method`, nhưng một caller từ lớp/package/module **không đủ quyền** có thể gặp `IllegalAccessException` khi gọi `invoke(null)`. Nếu cần deep reflection, dùng:

```java
Method method = Secrets.class.getDeclaredMethod("token");
if (method.trySetAccessible()) {
    Object value = method.invoke(null);
}
```

Đây là đoạn minh họa cần xử lý checked exceptions. Việc `trySetAccessible()` trả `true` phụ thuộc vào caller, quyền trong Java và JPMS; không bảo đảm thành công khi package của named module chưa mở cho caller. `setAccessible(true)` có thể ném `InaccessibleObjectException` nếu module boundary cấm yêu cầu.

Phân biệt ba lớp vấn đề:

1. **Loại phương thức:** `static` cho phép receiver `null`.
2. **Quyền truy cập:** caller có được phép gọi phương thức (hoặc yêu cầu suppress access checks) hay không.
3. **Đối số:** số lượng/kiểu tham số đích có tương thích hay không.

Một lớp phương thức có thể là static và receiver null đúng, nhưng lời gọi vẫn thất bại vì `IllegalAccessException` hoặc `IllegalArgumentException`.

Khi code đặt trong framework, caller thực sự của `trySetAccessible` có thể nằm ở module framework. Cần mở package cho module **đó**, không đoán rằng module chứa ứng dụng khởi chạy luôn là caller Reflection.

## 8. Giá trị trả về, static initialization và phương thức bị ẩn

### 8.1. Kết quả của `invoke` luôn là `Object`

Dù phương thức đích trả về `int`, `boolean`, `double` hay kiểu tham chiếu, `Method.invoke` khai báo trả về `Object`:

- Với primitive, kết quả được **box** thành wrapper tương ứng, ví dụ `int` thành `Integer`.
- Với kiểu tham chiếu, trả về chính giá trị tham chiếu theo hợp đồng.
- Với `void`, trả về `null`.
- Nếu phương thức trả về một mảng primitive như `int[]`, kết quả là **mảng `int[]`**, không được tự động biến thành `Integer[]`.

```java
Method count = Example.class.getMethod("count");
Object result = count.invoke(null);
int value = (Integer) result;
```

Mã minh họa này giả định `Example.count()` là public static method trả về `int`. Nên kiểm tra `getReturnType()` khi xử lý chữ ký động, tránh cast sai kiểu.

### 8.2. Phương thức static có thể kích hoạt khởi tạo lớp

Nếu lớp khai báo phương thức static chưa được khởi tạo, `Method.invoke` sẽ khiến **lớp khai báo được khởi tạo trước khi phương thức static chạy**. Đây là hành vi của Java, không phải hệ quả riêng của tham số `null`.

```java
class LazyInit {
    static {
        System.out.println("initialized");
    }

    public static void ping() {
        System.out.println("called");
    }
}
```

Nếu `LazyInit` chưa từng khởi tạo và một caller có quyền lấy `Method` rồi gọi `invoke(null)`, static initializer chạy trước `ping()`, nên log theo thứ tự `initialized` rồi `called`. Khi kiểm thử, chú ý những thao tác khởi tạo lớp khác đã chạy trước đó có thể làm log `initialized` không lặp lại.

Nếu khởi tạo lớp thất bại, Reflection có thể phát sinh `ExceptionInInitializerError`, khác với `InvocationTargetException` bọc exception do **thân phương thức đích** ném.

### 8.3. Static method không dispatch theo instance

Method static thuộc kiểu khai báo; Java không override static method theo cơ chế polymorphism của instance method. Nếu lớp con khai báo một static method cùng chữ ký, đó là **method hiding**, không phải overriding.

Khi đã có một `Method` đại diện cho static method trong lớp A, việc truyền một instance lớp B vào tham số receiver **không** làm Reflection chuyển sang một static method khác trong B. Receiver vốn bị bỏ qua; `Method` nào được lựa chọn mới quyết định phương thức đích.

Đừng dùng `method.invoke(instance, ...)` với static method rồi suy luận từ kiểu runtime của `instance` về đích gọi.

## 9. Chẩn đoán lời gọi static thất bại

Receiver `null` là hợp lệ cho static method, nhưng lời gọi vẫn cần **đúng Method, đúng quyền truy cập và đúng danh sách đối số**. Khi chẩn đoán, tách ba giai đoạn:

| Giai đoạn | Dấu hiệu | Điều cần kiểm tra |
| --- | --- | --- |
| Tra cứu | `NoSuchMethodException` | Tên, erased parameter types, `getMethod` so với `getDeclaredMethod` |
| Chuẩn bị gọi | `IllegalAccessException`, `IllegalArgumentException` | Quyền truy cập/JPMS, số lượng và kiểu `args`; `null` receiver không giải quyết các lỗi này |
| Thực thi | `InvocationTargetException` | `getCause()` để đọc lỗi do thân static method ném |
| Khởi tạo lớp | `ExceptionInInitializerError` | Lỗi trong quá trình khởi tạo declaring class trước lần gọi static |

Nếu `setAccessible(true)` bị `InaccessibleObjectException`, xem xét quyền mở package; không coi receiver `null` là cách vượt kiểm tra truy cập. Trước khi gọi trong code framework, dùng `Modifier.isStatic(method.getModifiers())` để xác nhận thực sự là static.

## 10. Mẫu hàm gọi động an toàn hơn

Nếu công cụ của bạn chỉ cần hỗ trợ **static methods**, có thể kiểm tra tường minh để phát hiện sai hợp đồng sớm:

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

public final class StaticCaller {
    private StaticCaller() {
    }

    public static Object invokeStatic(Method method, Object... arguments)
            throws IllegalAccessException, InvocationTargetException {
        Objects.requireNonNull(method, "method");
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("Method is not static: "
                    + method.getName());
        }
        return method.invoke(null, arguments);
    }
}
```

Đây là **lớp tiện ích có thể biên dịch độc lập**, lưu dưới tên `StaticCaller.java`; nó không tự tìm phương thức, không tự thay đổi quyền truy cập và không nuốt exception từ phương thức đích. Caller cần truyền `Method` hợp lệ và, nếu `arguments` chứa một mảng làm đối số đơn, phải đóng gói mảng đúng cách.

Khi gọi utility:

```java
Method add = Math.class.getMethod("addExact", int.class, int.class);
Object answer = StaticCaller.invokeStatic(add, 20, 22);
System.out.println(answer); // 42
```

Ví dụ sử dụng này cần import `java.lang.reflect.Method` và xử lý checked exceptions (chẳng hạn đặt trong `main` có `throws Exception`), cùng class `StaticCaller` ở trên. `Math.addExact(int,int)` là public static method; từ đoạn gọi có thể kiểm tra không cần tạo `Math` instance.

Nếu sử dụng Reflection lặp lại ở đường chạy hiệu năng cao, có thể cân nhắc cơ chế gọi được kiểm soát khác như `MethodHandle`. Dù dùng cơ chế nào, phải tôn trọng kiểm tra access và chữ ký; không cần tạo instance chỉ để gọi static method.

## 11. Câu hỏi tự kiểm tra

**Câu 1.** `Method m` trỏ tới `public static int add(int a, int b)`. Lệnh `m.invoke(null, 2, 3)` có hợp lệ không, và kiểu kết quả ở phía Reflection là gì?

**Đáp án:** Hợp lệ nếu access và các điều kiện chạy khác đáp ứng; trả về `Object` chứa một `Integer` giá trị `5`.

**Câu 2.** Vì sao `m.invoke(null)` với một instance method không tham số thất bại?

**Đáp án:** `null` được dùng làm receiver của instance method; Reflection ném `NullPointerException`. “Không tham số” không có nghĩa “không cần instance”.

**Câu 3.** Với `public static void process(String[] items)`, hai dòng có tương đương không?

```java
m.invoke(null, (Object) new String[] {"x", "y"});
m.invoke(null, (Object[]) new String[] {"x", "y"});
```

**Đáp án:** Không. Dòng đầu truyền **một tham số mảng**; dòng sau truyền **hai tham số `String`** vào varargs của `invoke` và không khớp phương thức chỉ có một tham số.

**Câu 4.** `private static` method có thể luôn chạy với `invoke(null)` không?

**Đáp án:** `null` receiver là đúng về mặt static, nhưng không bảo đảm **quyền truy cập**. Phương thức có thể bị từ chối khi caller không được phép truy cập hay chưa có quyền deep reflection.

**Câu 5.** Với `Method` đại diện cho static method trong lớp cha, truyền instance lớp con vào `invoke` có gọi static method bị ẩn trong lớp con không?

**Đáp án:** Không. Receiver bị bỏ qua; mục tiêu gọi là static method mà `Method` đã đại diện.

**Câu 6.** Khi thân static method ném `IllegalStateException`, caller thường thấy gì?

**Đáp án:** `Method.invoke` ném `InvocationTargetException`, và `getCause()` chứa `IllegalStateException` của phương thức đích.

## 12. Ghi nhớ

`Method.invoke(Object receiver, Object... args)` diễn giải tham số đầu tiên theo **loại phương thức**: static method **bỏ qua receiver**, vì vậy nên dùng `null`; instance method cần một object tương thích. `null` receiver không phải tham số null của method đích. Phân biệt **receiver**, **mảng varargs `args`**, **access control**, **khởi tạo lớp** và **exception của phương thức đích** để chẩn đoán lỗi chính xác.

Tài liệu chuẩn: [Java SE 21 — java.lang.reflect.Method, invoke](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Method.html) và [Java SE 21 — java.lang.reflect.Modifier](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Modifier.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="methods-007">Liên kết động khi Method của lớp cha được gọi trên lớp con</a>

<details>
<summary>Xem chi tiết</summary>

# Liên kết động khi `Method` của lớp cha được gọi trên lớp con

Một tình huống rất dễ hiểu sai trong Java Reflection: ta tìm thấy `Method` bằng cách nhìn vào **lớp cha**, nhưng lại truyền **đối tượng lớp con** vào `Method.invoke()`. Nếu lớp con ghi đè phương thức instance đó, **Java vẫn thực thi bản ghi đè của lớp con**.

```java
Method method = Parent.class.getMethod("speak");
Object result = method.invoke(new Child());
```

Giả sử `Child extends Parent` và ghi đè `speak()`, `result` là kết quả của `Child.speak()`. Đối tượng `Method` vẫn phản ánh khai báo được tra cứu từ `Parent`; việc chọn phần triển khai để thực thi diễn ra **lúc gọi**, dựa trên kiểu runtime của receiver. Đây là *dynamic method dispatch* (liên kết động), không phải cơ chế riêng biệt do Reflection phát minh.

Bài học thuộc nhóm **Method** trong Java Core Reflection; trọng tâm là phân biệt ba thông tin: **chữ ký phương thức được tra cứu**, **lớp khai báo trên đối tượng `Method`**, và **bản triển khai thực sự được gọi**.

## 1. Nhắc lại: override và kiểu runtime

Trong Java, một lời gọi đến phương thức instance có thể ghi đè được sẽ được chọn theo **kiểu thực tế của đối tượng**:

```java
class Parent {
    String speak() {
        return "Parent";
    }
}

class Child extends Parent {
    @Override
    String speak() {
        return "Child";
    }
}

Parent object = new Child();
System.out.println(object.speak()); // Child
```

Biến `object` có **kiểu khai báo** là `Parent` nhưng đang tham chiếu đến đối tượng có **kiểu runtime** là `Child`. Compiler kiểm tra rằng lời gọi `speak()` hợp lệ trên `Parent`, còn JVM chọn implementation ghi đè phù hợp với receiver thực tế. Nếu lớp con không override, bản triển khai kế thừa tương ứng sẽ chạy.

Override có quy tắc Java riêng: phương thức của lớp con phải tương thích về tên, chữ ký và return type; không được giảm mức truy cập của phương thức ghi đè; không được ghi đè phương thức `final`, `static` hoặc `private`. Với `static` có **method hiding**, không có override.

Khi chuyển lời gọi sang Reflection, bản chất này vẫn giữ nguyên đối với phương thức instance có thể override.

## 2. `Method` đại diện cho khai báo; `invoke()` nhận receiver runtime

`java.lang.reflect.Method` cung cấp metadata như:

| API | Cho biết điều gì? | Điều cần tránh suy diễn |
| --- | --- | --- |
| `method.getDeclaringClass()` | Lớp/interface chứa khai báo mà `Method` đại diện | Không nhất thiết là lớp có body sẽ được chạy khi receiver là lớp con |
| `method.getName()` | Tên khai báo | Không xác định riêng overload nếu thiếu tham số |
| `method.getParameterTypes()` | Các loại tham số theo chữ ký đã được phản ánh | Không được tự thay bằng loại runtime của từng đối số |
| `method.getReturnType()` | Return type trong khai báo tương ứng | Không phải cơ chế dò lại phương thức theo giá trị trả về runtime |
| `method.getModifiers()` | Các modifier của khai báo | Không xác nhận trước một override nào sẽ được chọn |
| `method.invoke(receiver, args...)` | Yêu cầu gọi phương thức trên receiver và đối số đã truyền | Với instance method ghi đè được, implementation được chọn động |

Đặc biệt:

```java
Method fromParent = Parent.class.getDeclaredMethod("speak");
System.out.println(fromParent.getDeclaringClass() == Parent.class); // true

Parent childAsParent = new Child();
Object result = fromParent.invoke(childAsParent);
// Nếu Child ghi đè speak(), result nhận kết quả của Child.speak()
```

`getDeclaringClass()` **không biến thành `Child.class` sau khi `invoke()`**. Lớp sở hữu khai báo của `Method` và lớp sở hữu phần triển khai được chọn khi gọi là hai góc nhìn khác nhau.

Đối với method instance, receiver truyền vào `invoke` phải là instance của lớp khai báo hoặc một subtype hợp lệ. Vì vậy `Method` lấy từ `Parent` có thể gọi trên `new Child()`; một đối tượng không liên quan sẽ không được chấp nhận.

## 3. Ba giai đoạn cần phân biệt

**Giai đoạn 1 — Lookup.** Ta chọn phương thức để phản ánh theo lớp, tên và danh sách kiểu tham số:

```java
Method method = Parent.class.getDeclaredMethod("speak", String.class);
```

`getDeclaredMethod` chỉ tìm các method **khai báo trực tiếp** trong `Parent` với chữ ký yêu cầu. `getMethod` tìm các phương thức `public` trong phạm vi API hỗ trợ, gồm cả các phương thức inherited. Việc chọn `Parent.class` lúc lookup không có nghĩa ta khóa receiver của mọi lời gọi tương lai vào lớp cha.

**Giai đoạn 2 — Access/arguments.** `Method.invoke` xác định receiver, kiểm tra quyền truy cập theo quy tắc reflection/module và kiểm tra/chuyển đổi đối số. Phương thức có thể được tìm thấy mà lời gọi vẫn thất bại do access checks hoặc receiver không hợp lệ.

**Giai đoạn 3 — Dispatch.** Với instance method có thể override, JVM chọn bản triển khai phù hợp với class runtime của receiver. Nếu `Child` override, `Child` chạy; nếu một `GrandChild extends Child` override tiếp, bản của `GrandChild` có thể chạy.

Quy trình khái quát:

```text
Parent.class.getMethod("speak")
                |
                v
      Method (khai báo Parent)
                |
                v
    invoke(receiver = new Child())
                |
                v
        Kiểu runtime: Child
                |
                v
    Child có override speak()?
           /           \
         Có           Không
          |              |
          v              v
     Child.speak()   Phương thức kế thừa
```

Đó là lý do **không thể suy ra chính xác phần thân sẽ thực thi chỉ từ `getDeclaringClass()`** đối với phương thức ảo được override.

## 4. Ví dụ Java 17+ hoàn chỉnh, chạy độc lập

Lưu chương trình vào `DynamicDispatchDemo.java`:

```java
import java.lang.reflect.Method;

public class DynamicDispatchDemo {
    public static class Parent {
        public String speak(String name) {
            return "Parent:" + name;
        }

        public static String kind() {
            return "Parent-static";
        }

        private String secret() {
            return "Parent-private";
        }

        public final String stable() {
            return "Parent-final";
        }
    }

    public static class Child extends Parent {
        @Override
        public String speak(String name) {
            return "Child:" + name;
        }

        // Overload: khác kiểu tham số, không ghi đè speak(String).
        public String speak(Object name) {
            return "Child-overload:" + name;
        }

        // Hiding static method của Parent.
        public static String kind() {
            return "Child-static";
        }

        // Không override Parent.secret() vì Parent.secret() là private.
        public String secret() {
            return "Child-public";
        }
    }

    public static void main(String[] args) throws Exception {
        Method fromParent =
                Parent.class.getDeclaredMethod("speak", String.class);
        Parent receiver = new Child();

        System.out.println("Method owner: "
                + fromParent.getDeclaringClass().getSimpleName());
        System.out.println("Receiver type: "
                + receiver.getClass().getSimpleName());

        System.out.println("Parent instance: "
                + fromParent.invoke(new Parent(), "Java"));
        System.out.println("Child instance: "
                + fromParent.invoke(receiver, "Java"));

        Method fromChild =
                Child.class.getMethod("speak", String.class);
        System.out.println("Child method owner: "
                + fromChild.getDeclaringClass().getSimpleName());

        System.out.println("Parent static: "
                + Parent.class.getDeclaredMethod("kind").invoke(null));

        Method parentPrivate =
                Parent.class.getDeclaredMethod("secret");
        parentPrivate.setAccessible(true);
        System.out.println("Parent private: "
                + parentPrivate.invoke(receiver));

        System.out.println("Child public secret: "
                + Child.class.getMethod("secret").invoke(receiver));

        System.out.println("Parent final: "
                + Parent.class.getDeclaredMethod("stable").invoke(receiver));

        // Method đã lookup với chữ ký speak(String), không chọn
        // sang overload speak(Object) ở Child.
        System.out.println("Parent chosen overload: "
                + fromParent.invoke(receiver, new Object[] {"Java"}));

        try {
            fromParent.invoke(new Object(), "Java");
        } catch (IllegalArgumentException ex) {
            System.out.println(
                    "Unrelated receiver: IllegalArgumentException");
        }
    }
}
```

Biên dịch và chạy:

```bash
javac DynamicDispatchDemo.java
java DynamicDispatchDemo
```

Đầu ra:

```text
Method owner: Parent
Receiver type: Child
Parent instance: Parent:Java
Child instance: Child:Java
Child method owner: Child
Parent static: Parent-static
Parent private: Parent-private
Child public secret: Child-public
Parent final: Parent-final
Parent chosen overload: Child:Java
Unrelated receiver: IllegalArgumentException
```

**Giải thích theo từng nhóm kết quả:**

1. `Method owner: Parent` vì `fromParent` được tạo từ khai báo `Parent.speak(String)`. Điều này **không đổi** khi receiver là `Child`.
2. `Parent instance: Parent:Java` và `Child instance: Child:Java` sử dụng **cùng một `Method`**. Chỉ receiver khác nhau, nên đối với method override được, phần triển khai được chọn động.
3. `Child method owner: Child` là kết quả của **một lookup khác**, lần này dùng `Child.class.getMethod("speak", String.class)`. API trả về khai báo override của `Child`; không phải từ `Method` cha tự biến đổi.
4. `Parent static: Parent-static` vì `static` được *hide* chứ không override. Method lấy từ `Parent` giữ hành vi static của `Parent`.
5. `Parent private: Parent-private` vì method `private` của `Parent` không được override. `Child.secret()` là một phương thức khác và in ra `Child-public`. Mã ví dụ sử dụng `setAccessible(true)` trong môi trường cùng ứng dụng; việc mở quyền private xuyên named modules có thể bị JPMS chặn.
6. `Parent final: Parent-final` vì `final` không cho phép lớp con override.
7. `Parent chosen overload: Child:Java`: lookup đã chọn chữ ký `speak(String)` và lời gọi dispatch tới override tương ứng; dù `Child` có thêm `speak(Object)`, JVM không tự chuyển sang overload đó.
8. Receiver `new Object()` không phải instance của `Parent` nên `invoke` ném `IllegalArgumentException` trước khi có thể gọi method đích.

Đầu ra trên phản ánh Java 17+ và không phụ thuộc vào thứ tự duyệt mảng reflection, vì tất cả lookup đều nêu tên và chữ ký cụ thể.

## 5. Vì sao `getDeclaringClass()` khác implementation lúc chạy?

Với đoạn:

```java
Method m = Parent.class.getMethod("speak", String.class);
Object value = m.invoke(new Child(), "A");
```

Có hai câu hỏi khác nhau:

- `m.getDeclaringClass() == Parent.class`? **Đúng** trong ví dụ này: đối tượng `m` đại diện khai báo của `Parent`.
- Body nào được thực thi với receiver `Child`? **`Child.speak(String)`**, vì đây là phương thức instance override được và `Child` đã override.

Điều này cũng áp dụng cho lớp con nhiều cấp:

```java
class GrandChild extends Child {
    @Override
    public String speak(String name) {
        return "GrandChild:" + name;
    }
}
```

Đây là đoạn **minh họa bổ sung** để khai báo cùng phạm vi với `Parent` và `Child` trong ví dụ. Khi `fromParent.invoke(new GrandChild(), "Java")`, kết quả dự kiến là `GrandChild:Java`. Method của cha là **điểm vào đã tra cứu**, không phải lời yêu cầu «bắt buộc chạy body của cha».

Đối với thư viện/framework, điểm này quan trọng: một proxy subclass hoặc một subclass do framework tạo ra có thể override một method. Khi gọi method của superclass bằng Reflection, code override trên proxy/subclass vẫn có thể tham gia thực thi.

## 6. Khi nào không có dynamic dispatch kiểu override?

Các phương thức Java không phải lúc nào cũng là lời gọi ảo có thể override. Cần phân biệt:

| Loại method | Child có thể override cùng method? | Khi dùng `Method` lấy từ `Parent` và receiver là `Child` |
| --- | --- | --- |
| Instance `public`/`protected`/package-private có thể override hợp lệ | Có, nếu Java access và quy tắc kế thừa cho phép | Thực thi bản override gần nhất phù hợp ở lớp runtime |
| `static` | Không; có thể **hide** method static | Gọi method static được khai báo ở `Parent`; receiver không quyết định dispatch |
| `private` | Không; phương thức không được kế thừa như method override | Gọi đúng body private của `Parent` nếu reflection access được phép |
| `final` instance | Không | Gọi body từ khai báo của `Parent` vì không có override hợp lệ |
| Constructor | Không thuộc `Method`, không có override | Dùng `Constructor.newInstance`, không có virtual dispatch như instance method |

### `static` là hiding, không phải overriding

```java
class Base {
    static String label() { return "Base"; }
}
class Derived extends Base {
    static String label() { return "Derived"; }
}

// Method lấy từ Base -> vẫn gọi Base.label().
Method m = Base.class.getDeclaredMethod("label");
System.out.println(m.invoke(null)); // Base
```

Đây là **đoạn minh họa cần import `java.lang.reflect.Method`**. Với method `static`, nên truyền `null` vào đối số receiver của `Method.invoke` vì target object không được sử dụng.

### `private` không override

Nếu cả `Parent` và `Child` đều có method tên `secret()`, nhưng method ở `Parent` là `private`, thì hai method **không có quan hệ override**. Reflection lấy `Method` từ `Parent` có thể gọi body private của `Parent` sau khi được phép truy cập. Không suy ra việc «tên giống nhau» tạo liên kết động.

### `final` giới hạn override

Phương thức instance `final` vẫn là instance method, nhưng subclass không thể khai báo override. Vì không có override hợp lệ ở subclass, ví dụ `Parent.finalMethod` trên receiver `Child` vẫn thực thi implementation đó. Tính `final` ở đây ngăn sự thay thế body bởi subclass.

## 7. Override khác overload: Reflection không tự chọn overload tốt nhất

**Overriding**: Lớp con cung cấp implementation thay thế cho một phương thức instance được kế thừa theo quy tắc Java, ví dụ `speak(String)` ở `Parent` và `Child`.

**Overloading**: Nhiều phương thức có cùng tên nhưng khác danh sách tham số, ví dụ `speak(String)` và `speak(Object)`.

`getMethod("speak", String.class)` và `getMethod("speak", Object.class)` có thể trả về **hai đối tượng `Method` khác nhau**, đại diện hai chữ ký. Khi đã chọn `Method` cho `speak(String)`, `invoke(receiver, value)` **không thực hiện thuật toán overload resolution của compiler Java từ đầu** theo giá trị runtime. Nó chỉ kiểm tra/chuyển đối số cho chữ ký đã chọn rồi dispatch override tương ứng nếu có.

Ví dụ khi `Child` khai báo thêm `speak(Object)`, gọi `Parent.speak(String)` bằng reflection trên receiver `Child` vẫn tới `Child.speak(String)` chứ không tới `Child.speak(Object)`.

Nếu viết framework router theo tên method, cần quyết định chữ ký cụ thể: `getParameterTypes()`, số lượng tham số, boxing/unboxing, widening, varargs và policy chọn overload. **Dynamic dispatch không giải quyết thay bạn bài toán chọn overload.**

## 8. Lookup trên lớp cha hoặc lớp con có gì khác?

Các API thường dùng:

| Cách lấy `Method` | Hành vi tìm kiếm | Mức độ phù hợp |
| --- | --- | --- |
| `Parent.class.getDeclaredMethod("m", types...)` | Chỉ method khai báo trực tiếp trong `Parent`, kể cả non-public | Cần chính xác khai báo của cha |
| `Parent.class.getMethod("m", types...)` | Method `public` theo quy tắc tra cứu `getMethod` | Cần contract công khai ở cha |
| `Child.class.getDeclaredMethod("m", types...)` | Chỉ method khai báo trực tiếp trong `Child` | Cần biết subclass có khai báo method với chữ ký đó không |
| `Child.class.getMethod("m", types...)` | Method `public` có thể tìm thấy trên `Child`, gồm inherited | Cần thao tác qua API công khai của Child |

Ví dụ `Child.class.getDeclaredMethod("stable")` sẽ thất bại nếu `stable()` chỉ được `Parent` khai báo, dù `Child` **kế thừa và gọi được** method `stable()`. Trong khi `Child.class.getMethod("stable")` có thể thành công, trả method public được kế thừa có `getDeclaringClass() == Parent.class`.

Với override `Child.speak(String)`, `Child.class.getMethod("speak", String.class)` tìm thấy khai báo của `Child`; trong khi `Parent.class.getMethod("speak", String.class)` cho method của `Parent`. Nhưng cả hai có thể gọi cùng implementation của `Child` khi receiver là đối tượng `Child` và chữ ký tương ứng được override.

**Chọn lookup dựa theo ý định metadata**, không dùng việc lookup trên `Parent` làm «mẹo» để né override.

## 9. Điều kiện nhận receiver và những exception quan trọng

`Method.invoke(Object receiver, Object... arguments)` phân biệt method instance và static:

- **Instance method:** receiver phải khác `null` và phải tương thích với class/interface khai báo method. `new Child()` hợp lệ khi `Child extends Parent`; `new Object()` không hợp lệ với method khai báo trong `Parent`.
- **Static method:** receiver bị bỏ qua; truyền `null` giúp mã rõ ràng.
- **Tham số:** số lượng và kiểu đối số phải tương thích với chữ ký được `Method` đại diện. Reflection có thể áp dụng unboxing và widening primitive thích hợp, nhưng không tự chọn overload khác.
- **Quyền truy cập:** trước khi gọi, reflection vẫn tuân thủ Java access checks và giới hạn module nếu chưa suppress được checks. Method tồn tại chưa đồng nghĩa caller được phép invoke.

| Exception | Ngữ cảnh điển hình | Ý nghĩa |
| --- | --- | --- |
| `NoSuchMethodException` | `getDeclaredMethod`/`getMethod` | Tên hoặc danh sách kiểu tham số không khớp phạm vi tìm kiếm |
| `IllegalAccessException` | `Method.invoke` | Không được truy cập method theo access checks hiện tại |
| `IllegalArgumentException` | `Method.invoke` | Receiver không phù hợp, sai số/kiểu đối số |
| `NullPointerException` | `Method.invoke` trên instance method với receiver `null` | Không có đối tượng để gọi instance method |
| `InvocationTargetException` | `Method.invoke` khi method đích ném exception | Phần thân method đã ném lỗi; xem `getCause()` |
| `InaccessibleObjectException` | `setAccessible(true)` | Không thể suppress access checks, thường do package của named module chưa được mở |

Quan trọng: `InvocationTargetException` không có nghĩa thuật toán dispatch thất bại. Nó thường nói rằng **method đích đã được chọn và chạy**, sau đó tự ném exception.

`Method.invoke` không thể được dùng như một cơ chế đặc biệt để «đi xuyên» module encapsulation. Ví dụ `setAccessible(true)` có thể bị JPMS từ chối dù một `Method` đã được tìm thấy qua `getDeclaredMethod`.

## 10. Interface method, abstract method và default method

Virtual dispatch không giới hạn ở class cha cụ thể. Giả sử một interface khai báo `String name()`, các implementation khác nhau thực thi theo receiver:

```java
interface Named {
    String name();
}

class First implements Named {
    public String name() { return "First"; }
}

class Second implements Named {
    public String name() { return "Second"; }
}

Method method = Named.class.getMethod("name");
System.out.println(method.invoke(new First()));  // First
System.out.println(method.invoke(new Second())); // Second
```

Đây là **đoạn minh họa cần import `java.lang.reflect.Method`**. Trong hai lời gọi, `Method.getDeclaringClass()` vẫn là `Named.class`, còn implementation được chọn theo class của receiver.

Tương tự, `Method` có thể đại diện một phương thức `abstract` của abstract superclass hoặc interface và được invoke trên instance của subclass concrete triển khai nó. Nếu interface có **default method**, lớp triển khai có thể kế thừa default implementation hoặc override nó; `Method.invoke` vẫn tuân theo cơ chế dispatch thích hợp.

Không nên lẫn chuyện này với lời gọi `super.m()` được ràng buộc đặc biệt ở mã Java. Một `Method` phản ánh method của superclass và dùng `invoke(child)` **không tương đương** lệnh `super.m()` của lớp con. Để chủ động gọi một implementation bị override ở superclass phải xem các cơ chế method handles/special invocation và quyền truy cập liên quan; `Method.invoke` thông thường không có tham số «bỏ qua override».

## 11. Bridge method, generic và proxy: tình huống thực tế

Với generic, compiler đôi khi sinh **bridge method** để duy trì tính đa hình sau type erasure. Ví dụ về ý tưởng:

```java
class Box<T> {
    T normalize(T value) { return value; }
}

class TextBox extends Box<String> {
    @Override
    String normalize(String value) {
        return value.trim();
    }
}
```

Vì type erasure, chữ ký erased trên `Box<T>` sử dụng `Object`. Compiler có thể sinh bridge ở `TextBox` để chuyển lời gọi `normalize(Object)` tới implementation `normalize(String)` và bảo toàn override. Một framework phản chiếu các method từ `getDeclaredMethods()` của `TextBox` có thể thấy cả method source và method bridge/synthetic; kiểm tra `method.isBridge()` và `method.isSynthetic()` trước khi quyết định chọn method nào.

Với **proxy subclass** do ORM, mocking framework hoặc AOP tạo ra, lớp runtime có thể override public/protected method để thêm interceptors. Khi gọi `Method` lấy từ superclass với receiver là proxy subclass, dispatch vẫn có thể tới override trên proxy. Đó là hành vi JVM phù hợp với dynamic dispatch, không phải `Method.invoke` luôn ưu tiên body của class được lookup.

Tuy nhiên, proxy dùng **interface-based delegation** khác với subclass proxy; không thể suy ra mọi loại proxy đều là subclass của target class. Xác định hierarchy/runtime class thật trước khi kết luận.

## 12. Các hiểu nhầm thường gặp

**Hiểu nhầm 1 — «Lấy `Method` từ `Parent.class` thì luôn chạy code của `Parent`».** Sai với overridable instance method. `invoke(new Child())` vẫn gọi override của `Child` nếu có.

**Hiểu nhầm 2 — «`getDeclaringClass()` cho biết implementation đã chạy».** Không nhất thiết. Nó chỉ báo lớp/interface chứa khai báo mà đối tượng `Method` đang phản ánh. Với receiver polymorphic, phải xem runtime class và quan hệ override.

**Hiểu nhầm 3 — «Cứ trùng tên là override».** Sai. `static` tạo hiding, `private` không được override, chữ ký tham số khác là overload; method `final` không được override.

**Hiểu nhầm 4 — «Reflection sẽ chọn overload tốt nhất theo class runtime của đối số».** Sai. Chữ ký được chốt ở bước `getMethod(name, parameterTypes)` hoặc `getDeclaredMethod(...)`. `invoke` thực hiện chuyển đổi đối số cho method đã chọn và dispatch override nếu cần.

**Hiểu nhầm 5 — «`setAccessible(true)` sẽ ép chạy method của cha».** Sai. `setAccessible` chỉ yêu cầu suppress Java language access checks khi được phép; nó không tắt virtual dispatch và không thay thế lời gọi bằng `super.m()`.

**Hiểu nhầm 6 — «Method lấy từ superclass luôn gọi được với bất kỳ Object nào».** Sai. Receiver của instance method phải thuộc lớp khai báo hoặc subtype thích hợp, nếu không `invoke` ném `IllegalArgumentException`.

**Hiểu nhầm 7 — «Override thực thi sai là do Reflection».** Cần xem code override, proxy/interceptor, exception do target ném và quan hệ inheritance. Reflection tuân theo cơ chế dispatch Java bình thường đối với overridable instance methods.

## 13. Checklist khi xây dựng framework sử dụng `Method`

Khi một thư viện lưu `Method` của interface/superclass và gọi trên đối tượng runtime:

1. **Chốt contract:** chọn đúng method và chữ ký trong `getMethod`/`getDeclaredMethod`; đừng tự giả định overload.
2. **Kiểm tra receiver:** nhận `receiver.getClass()` để biết runtime type, đồng thời kiểm tra receiver tương thích với lớp khai báo. Receiver `null` chỉ phù hợp với static method.
3. **Kiểm tra modifier:** `Modifier.isStatic(method.getModifiers())`, `Modifier.isPrivate(...)` hoặc `Modifier.isFinal(...)` để biết có khả năng override không.
4. **Dự kiến dynamic dispatch:** với overridable instance method, override của receiver, subclass/proxy có thể chạy ngay cả khi `Method` đến từ superclass/interface.
5. **Xử lý access/module:** không coi metadata lookup là quyền gọi; chỉ `setAccessible`/`trySetAccessible` khi có nhu cầu và hiểu JPMS.
6. **Xử lý lỗi đúng tầng:** phân biệt lỗi lookup, receiver/argument, access, và exception do method đích ném (`InvocationTargetException.getCause()`).
7. **Xử lý compiler-generated methods:** nếu đang xây dựng bộ quét method và gặp generics/bridge methods, dùng `isBridge()`, `isSynthetic()` và luật chọn method có chủ đích.

### Tự kiểm tra

**Câu 1.** `Parent.class.getMethod("speak", String.class)` trả về method có `getDeclaringClass() == Parent.class`. Với `new Child()` ghi đè `speak(String)`, implementation nào chạy?  
**Đáp án:** `Child.speak(String)`, vì dynamic dispatch dựa trên receiver runtime.

**Câu 2.** Nếu `Parent.speak(String)` là `static`, `Child` khai báo method `static` cùng chữ ký, `Method` lấy từ `Parent` gọi method nào?  
**Đáp án:** `Parent.speak(String)`; static hiding không phải overriding.

**Câu 3.** Tại sao `Child.class.getDeclaredMethod("stable")` có thể ném `NoSuchMethodException` dù `Child` gọi được `stable()`?  
**Đáp án:** Vì `getDeclaredMethod` chỉ tìm khai báo trực tiếp; method `stable()` được khai báo ở `Parent` và kế thừa.

**Câu 4.** Có thể dùng `fromParent.setAccessible(true)` để ép `fromParent.invoke(new Child())` chạy body `Parent.speak(String)` không?  
**Đáp án:** Không. Accessibility và virtual dispatch là hai cơ chế khác nhau.

## Kết luận

Trong Java Reflection, **lớp dùng để tra cứu `Method` quyết định khai báo được chọn**, còn **receiver thực tế quyết định bản triển khai của overridable instance method tại thời điểm gọi**. Bởi vậy:

```text
Method lấy từ Parent
       +
receiver runtime Child
       +
Child override hợp lệ
       =
chạy Child implementation
```

Chỉ khi hiểu rõ ranh giới giữa **lookup → access/argument check → dynamic dispatch**, ta mới diễn giải đúng `Method.invoke()`, đặc biệt trong inheritance, interface, generic bridge và subclass proxy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="methods-008">Phân biệt gọi virtual với mong muốn gọi riêng triển khai super</a>

<details>
<summary>Xem chi tiết</summary>

# Phân biệt gọi virtual với mong muốn gọi riêng triển khai super trong Java Reflection

## 1. Vấn đề: đã lấy Method của lớp cha, sao vẫn chạy code lớp con?

Một người dùng Reflection có thể lấy đối tượng `Method` được khai báo ở lớp cha rồi gọi nó trên đối tượng của lớp con:

~~~java
Method method = Parent.class.getDeclaredMethod("message");
Object result = method.invoke(new Child());
~~~

Nếu `Child` **override** `message()`, kết quả là phần triển khai của `Child`, **không phải** phần triển khai trong `Parent`. Đây là hành vi đúng của `Method.invoke` đối với phương thức instance có cơ chế gọi ảo (virtual dispatch).

Điều cần phân biệt:

- **Method được tìm ở đâu?** `Parent.class.getDeclaredMethod(...)` trả về metadata của phương thức khai báo trong `Parent`.
- **Method được gọi trên đối tượng nào?** Đối tượng truyền vào `invoke(receiver)` có kiểu runtime là `Child`.
- **JVM chọn thân phương thức nào?** Với phương thức instance có thể override, `Method.invoke` sử dụng **dynamic method lookup**; lớp con override sẽ được chọn.
- **Làm sao cố ý gọi triển khai `super`?** Câu lệnh `super.message()` trong mã lớp con, hoặc `MethodHandles.Lookup.findSpecial(...)` / `unreflectSpecial(...)` trong ngữ cảnh caller có đủ quyền, mới biểu đạt ý định tương tự `invokespecial`.

Đây là bài học thuộc nhóm **Method của Java Reflection**, tập trung vào dispatch và cách gọi đúng thân phương thức, thay vì chỉ cách lấy tên hoặc annotation của method.

## 2. Mô hình nền tảng: kiểu biên dịch và kiểu runtime

Ví dụ:

~~~java
class Parent {
    public String message() {
        return "Parent.message";
    }
}

class Child extends Parent {
    @Override
    public String message() {
        return "Child.message";
    }

    public String callSuper() {
        return super.message();
    }
}
~~~

Với:

~~~java
Parent p = new Child();
System.out.println(p.message());       // Child.message
System.out.println(((Parent) p).message()); // Child.message
System.out.println(((Child) p).callSuper()); // Parent.message
~~~

**Vì sao?** Biểu thức có kiểu khai báo `Parent` và phép cast `(Parent)` chỉ chi phối việc kiểm tra/giải quyết lời gọi tại thời điểm biên dịch; đối tượng thực vẫn là `Child`. Với lời gọi virtual, JVM lựa chọn override phù hợp theo lớp thực tế của receiver. Phép cast không chuyển object thành một instance `Parent` mới.

Ngược lại, `super.message()` là một lời gọi đặc biệt xuất hiện trong **mã của lớp con**. Java dùng ngữ nghĩa gọi thành viên lớp cha trong ngữ cảnh đó, bỏ qua override đang có ở lớp con. `super` không phải giá trị đối tượng có thể truyền cho `Method.invoke`; bạn không thể viết `method.invoke(super)` ở chỗ bình thường vì `super` không phải biểu thức biến độc lập như `this`.

### Minh họa đường đi

~~~text
Parent p = new Child()
        |
        +-- p.message()
        |       |
        |       +-- virtual dispatch theo receiver thực: Child.message()
        |
        +-- Parent.class.getMethod("message").invoke(p)
        |       |
        |       +-- Method mô tả Parent.message
        |       +-- Method.invoke vẫn dispatch: Child.message()
        |
        +-- ((Child) p).callSuper()
                |
                +-- mã trong Child gọi super.message(): Parent.message()
~~~

Một điểm quan trọng về từ “virtual”: trong Java, các phương thức instance có khả năng override thường dùng dynamic dispatch. Không áp dụng quy tắc đó một cách máy móc cho mọi method như `static`, `private`, constructor hay các phương thức không thể override.

## 3. Những API tìm Method KHÔNG quyết định thân hàm được chạy

| Cách lấy Method | Method metadata có thể đại diện cho | Khi `invoke(child)` với override hợp lệ |
| --- | --- | --- |
| `Parent.class.getMethod("message")` | Method public của `Parent` | Chạy `Child.message` |
| `Parent.class.getDeclaredMethod("message")` | Method khai báo trực tiếp trong `Parent` | Chạy `Child.message` |
| `Child.class.getMethod("message")` | Method public mà tra cứu từ `Child` thấy | Chạy `Child.message` |
| `Child.class.getDeclaredMethod("message")` | Method khai báo trực tiếp trong `Child` nếu có | Chạy `Child.message` |
| `Parent.class.getSuperclass()` | Trả về lớp cha của `Parent`, không phải một lệnh gọi method | Không tác động đến dispatch |

Các điểm kỹ thuật:

1. `getMethod` tìm phương thức `public` theo quy tắc của `Class`, trong đó có thể bao gồm phương thức kế thừa.
2. `getDeclaredMethod` tìm khai báo ở **chính lớp/interface được hỏi**, bất kể `public`, `protected`, package-private hay `private`; không tự đi tìm khai báo trong superclass.
3. `Method.getDeclaringClass()` cho biết **lớp khai báo Method**, không cho biết override nào sẽ được chọn khi chạy.
4. Truyền đúng danh sách `Class<?>` của tham số vào `getMethod/getDeclaredMethod` khi method có tham số hoặc overload.
5. Dùng `setAccessible(true)` hoặc `trySetAccessible()` (khi được cho phép) để thay đổi **kiểm tra quyền truy cập**, không thay đổi virtual dispatch. Mở quyền cho một Method khai báo ở Parent không có nghĩa JVM phải chọn thân Parent.

Tài liệu Java SE 17 về `Method.invoke` xác nhận rằng, với phương thức instance, JVM dùng dynamic method lookup; việc override dựa trên kiểu runtime của đối tượng đích vẫn diễn ra.

### Hai mục tiêu khác nhau

- **Mục tiêu A — gọi hành vi đa hình trên receiver hiện tại:** dùng `Method.invoke(receiver, args...)` hoặc method handle có ngữ nghĩa virtual.
- **Mục tiêu B — gọi riêng phần triển khai của superclass:** cần một vị trí gọi có quyền thực hiện `super`/`invokespecial`; cách đó không thể tạo ra bằng cách đổi mỗi đối tượng `Method` hoặc ép kiểu receiver.

## 4. Ví dụ Java 17+ độc lập: nhìn thấy mọi kết quả trong một lần chạy

Đặt toàn bộ mã sau vào tệp **`VirtualDispatchDemo.java`**. Chương trình dùng nested class để dễ biên dịch, đồng thời thực hiện `findSpecial` từ **bên trong `Child`**, nơi lookup được tạo với quyền truy cập phù hợp.

~~~java
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

public class VirtualDispatchDemo {
    public static class Parent {
        public String message() { return "Parent.message"; }
        public static String category() { return "Parent.category"; }
        public final String fixed() { return "Parent.fixed"; }
    }

    public static class Child extends Parent {
        @Override
        public String message() { return "Child.message"; }

        public static String category() { return "Child.category"; }

        public String directSuper() { return super.message(); }

        public String specialSuper() throws Throwable {
            MethodHandle handle = MethodHandles.lookup().findSpecial(
                    Parent.class, "message",
                    MethodType.methodType(String.class),
                    Child.class);
            return (String) handle.invoke(this);
        }
    }

    public static void main(String[] args) throws Throwable {
        Child child = new Child();
        Parent parentRef = child;
        Method fromParent = Parent.class.getMethod("message");
        Method fromChild = Child.class.getDeclaredMethod("message");
        Method staticParent = Parent.class.getMethod("category");
        Method finalParent = Parent.class.getMethod("fixed");

        System.out.println("normal: " + parentRef.message());
        System.out.println("declared by: "
                + fromParent.getDeclaringClass().getSimpleName());
        System.out.println("parent Method.invoke: "
                + fromParent.invoke(child));
        System.out.println("child Method.invoke: "
                + fromChild.invoke(child));
        System.out.println("super in Child: " + child.directSuper());
        System.out.println("findSpecial from Child: "
                + child.specialSuper());

        MethodHandle virtual = MethodHandles.lookup().findVirtual(
                Parent.class, "message",
                MethodType.methodType(String.class));
        System.out.println("findVirtual: " + (String) virtual.invoke(child));
        System.out.println("static Parent method: "
                + staticParent.invoke(null));
        System.out.println("final Parent method: "
                + finalParent.invoke(child));
    }
}
~~~

Biên dịch và chạy tại thư mục chứa tệp bằng JDK 17 trở lên:

~~~bash
javac --release 17 VirtualDispatchDemo.java
java VirtualDispatchDemo
~~~

**Stdout chính xác của ví dụ đã được biên dịch và chạy bằng OpenJDK 21 với `--release 17`:**

~~~text
normal: Child.message
declared by: Parent
parent Method.invoke: Child.message
child Method.invoke: Child.message
super in Child: Parent.message
findSpecial from Child: Parent.message
findVirtual: Child.message
static Parent method: Parent.category
final Parent method: Parent.fixed
~~~

### Phân tích từng kết quả

- `normal`: biến `parentRef` có kiểu khai báo `Parent` nhưng trỏ vào `Child`; lời gọi ảo chọn `Child.message`.
- `declared by`: chính đối tượng `Method` thuộc `Parent`, xác nhận lookup ở lớp cha, nhưng nó **không khóa** dispatch vào Parent.
- `parent Method.invoke` và `child Method.invoke`: cả hai chạy `Child.message`; điều này tách bạch metadata của Method với lựa chọn triển khai tại runtime.
- `super in Child`: phương thức `directSuper` trong Child dùng đúng từ khóa `super` nên thực thi `Parent.message`.
- `findSpecial from Child`: tạo method handle có ngữ nghĩa gọi đặc biệt từ lookup đủ quyền của Child, bỏ qua override và chạy `Parent.message`.
- `findVirtual`: method handle tạo bằng `findVirtual` vẫn dùng virtual dispatch khi nhận Child, nên chạy `Child.message`.
- `static Parent method`: `static` dùng method hiding chứ không override; Reflection gọi đúng phương thức static mô tả ở Parent; receiver có thể là `null`.
- `final Parent method`: `final` không thể được Child override; gọi trên Child vẫn dùng thân `Parent.fixed`.

Ví dụ chạy bằng classpath, không cần tùy chọn JVM đặc biệt. Nếu sửa ví dụ để chia thành các **named module** có quyền truy cập hạn chế, hành vi tạo method handle còn phụ thuộc JPMS và quyền của lookup.

## 5. Giới hạn của `Method.invoke` trong bài toán gọi super

Trong ví dụ ở mục 4, `fromParent.getDeclaringClass()` vẫn là `Parent.class`, nhưng `fromParent.invoke(child)` gọi `Child.message()`. Đây là **virtual dispatch**, giống lời gọi instance bình thường. Thay `getMethod` bằng `getDeclaredMethod`, ép `child` về `Parent`, hoặc bật `setAccessible(true)` đều **không** đổi cơ chế chọn override.

`Method.invoke` nhận receiver và đối số; API này **không có tùy chọn “gọi riêng thân super”**. Nếu chỉ cần hành vi đa hình thì tiếp tục dùng `invoke`; khi thực sự muốn gọi triển khai lớp cha, cần cơ chế special invocation có quyền phù hợp, được trình bày dưới đây.

## 6. Khi nào gọi được triển khai super bằng MethodHandles?

Java cung cấp `java.lang.invoke.MethodHandle` và `MethodHandles.Lookup`. Một method handle có thể biểu diễn cách gọi tương tự một số lệnh bytecode JVM, trong đó có:

| API | Loại gọi được nhấn mạnh | Khi receiver là Child override Parent |
| --- | --- | --- |
| `Method.invoke` trên method instance của Parent | Dynamic lookup theo receiver | Child override |
| `Lookup.findVirtual(Parent.class, "message", type)` | Gọi ảo tương tự `invokevirtual` | Child override |
| `Lookup.findSpecial(Parent.class, "message", type, Child.class)` từ lookup đủ quyền của Child | Gọi đặc biệt tương tự `invokespecial` | Triển khai Parent, theo quy tắc `invokespecial` |
| `Lookup.unreflectSpecial(parentMethod, Child.class)` từ lookup đủ quyền của Child | Tạo handle gọi đặc biệt từ Method metadata | Có thể gọi triển khai Parent |
| `super.message()` trong mã Child | Cú pháp ngôn ngữ cho lời gọi superclass | Triển khai Parent |

### 6.1. `findSpecial` và các tham số

Trong ví dụ:

~~~java
MethodHandle handle = MethodHandles.lookup().findSpecial(
        Parent.class,
        "message",
        MethodType.methodType(String.class),
        Child.class);
String result = (String) handle.invoke(this);
~~~

- `Parent.class`: lớp mà method đặc biệt được tra cứu.
- `"message"`: tên method cần gọi.
- `MethodType.methodType(String.class)`: kiểu trả về và các kiểu tham số của method; không chứa receiver.
- `Child.class`: **special caller**, tức lớp được phép thực hiện kiểu gọi `invokespecial` trong ngữ cảnh này.
- `MethodHandles.lookup()`: sinh ra bên trong phương thức của `Child`, nên lookup class đúng là `Child` và có quyền truy cập để kiểm tra lời gọi này.
- `this`: receiver mà handle cần để thực thi. Với method instance, kiểu handle bao gồm một tham số receiver phía trước các tham số method.

`findSpecial` không chỉ là một cách viết khác của `findVirtual`. Nó mang **ngữ nghĩa gọi không thông qua override thông thường** và chỉ được tạo khi lookup có quyền tương ứng. Theo API, nếu `specialCaller` không phù hợp với lookup class hoặc lookup thiếu quyền private cần thiết, thao tác có thể thất bại bằng `IllegalAccessException`.

Một ví dụ không hợp lệ về kỳ vọng: đưa đoạn `MethodHandles.lookup().findSpecial(Parent.class, ..., Child.class)` ra mã bất kỳ ngoài Child rồi cho rằng mọi caller đều được quyền gọi phần `super` của Child. Ngữ nghĩa `super` gắn với **vị trí/lớp gọi được cấp quyền**, không phải với một chuỗi tên lớp tự khai báo.

### 6.2. `unreflectSpecial` khi đã có Method

Nếu đã lấy Method bằng Reflection, từ mã trong `Child` có thể dùng:

~~~java
Method parent = Parent.class.getDeclaredMethod("message");
MethodHandle special = MethodHandles.lookup()
        .unreflectSpecial(parent, Child.class);
String result = (String) special.invoke(this);
~~~

Đây là đoạn mã **để đặt trong một phương thức instance của `Child`**, với `Parent`/`Child` theo phần 4; cần khai báo/lan truyền các checked exception và `Throwable` thích hợp. `unreflectSpecial` tái sử dụng Method metadata để tạo một method handle có ngữ nghĩa gọi đặc biệt. Nó **không** biến bản thân `parent.invoke(child)` thành lời gọi super.

Việc có thể đọc một `Method` bằng Reflection không tự cấp phép cho `unreflectSpecial`. API vẫn kiểm tra quyền của lookup và giới hạn truy cập. `privateLookupIn` cũng không phải một cơ chế “đột nhập” tùy ý: việc chuyển sang lookup có quyền private chịu ràng buộc bởi quyền caller và sự mở package của module liên quan.

### 6.3. Lý do nên ưu tiên `super` trực tiếp khi kiểm soát mã lớp con

Khi chính bạn viết Child, cách đơn giản, rõ nghĩa và ổn định nhất là:

~~~java
@Override
public String message() {
    return "Child wrapper -> " + super.message();
}
~~~

Cách này thực hiện trực tiếp lời gọi superclass trong ngữ cảnh mà Java cho phép, không phải tạo Reflection hay method handle. Nếu một thư viện muốn cho client bên ngoài gọi phần logic của cha, có thể cung cấp một **phương thức tường minh** của Child (ví dụ `callParentMessage()`) thay vì buộc client dùng quyền truy cập đặc biệt.

`findSpecial` có ích khi viết framework, bộ máy gọi động hoặc tích hợp cần kiểm soát cách dispatch, nhưng nó phức tạp hơn `super` và có giới hạn quyền rõ ràng.

## 7. Những trường hợp không theo mô hình override thông thường

| Loại method | Có bị override theo cách của method instance thông thường? | Hành vi cần nhớ |
| --- | --- | --- |
| Instance method có thể override | Có | `Method.invoke` chọn override phù hợp với receiver runtime |
| `static` | Không; cùng tên/chữ ký là **hiding** | Reflection gọi đúng static Method đã tìm, receiver bị bỏ qua |
| `private` | Không bị override | Method private của cha là thành viên riêng; khai báo cùng chữ ký trong con không phải override nó |
| `final` instance | Không thể override | Receiver là subclass vẫn chỉ có phần triển khai final kế thừa |
| Constructor | Không là Method, không được kế thừa/override | Dùng `Constructor.newInstance`; không áp dụng virtual dispatch như `Method.invoke` |
| Method `abstract` ở cha hoặc interface | Có thể được triển khai ở con | `Method.invoke` với receiver cụ thể sẽ đi đến triển khai hợp lệ, nếu có |
| Interface default method | Có thể được override | `Method.invoke` trên receiver có override thường đi đến override; muốn gọi riêng default cần ngữ nghĩa gọi đặc biệt hợp lệ |

Đặc biệt với private method, đừng thấy hai phương thức cùng tên rồi kết luận “Child override private method của Parent”. Java không cho override một private member như vậy. Nếu Reflection gọi đúng private method của Parent trên một Child instance và có quyền truy cập, thân của Parent được gọi vì nó không có dynamic override tương ứng — **đó không phải là kỹ thuật gọi `super` của một method public/protected có thể override**.

Tương tự, khi method là `static`, gọi `Parent.class.getMethod("category").invoke(null)` chạy `Parent.category`; `Child` có thể khai báo `category` cùng tên nhưng chỉ ẩn method static của Parent. Không lấy kết quả đó làm bằng chứng rằng phương thức instance thông thường có thể gọi đúng super qua `Method.invoke`.

### Phương thức mặc định (default) của interface

Ví dụ khái niệm:

~~~java
interface Greeting {
    default String hello() { return "Greeting.hello"; }
}

class Friendly implements Greeting {
    @Override
    public String hello() { return "Friendly.hello"; }

    String interfaceDefault() {
        return Greeting.super.hello();
    }
}
~~~

Nếu lấy `Greeting.class.getMethod("hello")` rồi gọi `invoke(new Friendly())`, kết quả phản ánh override `Friendly.hello`. Muốn gọi trực tiếp default từ `Friendly`, cú pháp `Greeting.super.hello()` biểu đạt rõ ý định. Với method handle, trường hợp default cũng liên quan đến lookup đặc biệt nhưng phải tuân thủ quy tắc special caller/interface phù hợp; đừng dùng một `findSpecial` tùy ý từ mã ngoài class rồi cho rằng chắc chắn hợp lệ. Đây là đoạn mã minh họa, không phải chương trình Java độc lập có `main`.

## 8. Quyền truy cập và module là vấn đề riêng với dispatch

Ba câu hỏi sau phải được xét riêng:

1. **Có tìm được metadata không?** `getMethod` chỉ tra cứu public Method phù hợp; `getDeclaredMethod` tìm khai báo trực tiếp, kể cả private.
2. **Có quyền gọi hoặc tạo special handle không?** Kiểm tra Java access control, quyền lookup và ranh giới JPMS khi dùng named module.
3. **Thân phương thức nào được chọn khi đã gọi hợp lệ?** Với `Method.invoke` cho method instance có override, vẫn là virtual dispatch; với handle hợp lệ được tạo qua `findSpecial` thì theo quy tắc lời gọi đặc biệt.

Vì thế, `method.setAccessible(true)` (nếu runtime cho phép) **chỉ xử lý kiểm tra truy cập**. Nó không đổi đối tượng receiver, không xóa override và không buộc Parent chạy. Đối với các named module, một package không được `opens` phù hợp có thể khiến nỗ lực bỏ kiểm tra quyền bị `InaccessibleObjectException` hoặc `trySetAccessible() == false`.

Phân biệt lỗi:

| Dấu hiệu | Vấn đề có khả năng nhất |
| --- | --- |
| `NoSuchMethodException` | Tên/chữ ký sai, chọn `getMethod` thay cho `getDeclaredMethod`, hoặc lớp tra cứu không khai báo method |
| `IllegalArgumentException` từ `Method.invoke` | Receiver không tương thích với lớp khai báo Method hoặc danh sách/kiểu đối số không hợp lệ |
| `NullPointerException` với instance `Method.invoke(null)` | Không có receiver instance |
| `IllegalAccessException` từ `Method.invoke` | Caller không có quyền truy cập method |
| `InvocationTargetException` | Bản thân thân phương thức đích ném ngoại lệ; đọc `getCause()` |
| `IllegalAccessException` khi `findSpecial` / `unreflectSpecial` | Lookup hoặc special caller không đủ quyền cho ngữ nghĩa gọi đặc biệt |
| `WrongMethodTypeException` khi gọi `MethodHandle.invokeExact` | Kiểu tại call site không khớp chính xác với method handle |
| Chạy thành công nhưng ra `Child.message` thay vì `Parent.message` | **Không phải lỗi Reflection**; đây chính là virtual dispatch |

`MethodHandle.invoke`/`invokeExact` có quy tắc kiểm tra/adapt kiểu khác với `Method.invoke`. Đặc biệt, `invokeExact` yêu cầu kiểu call site chính xác; `invoke` có một số cơ chế thích nghi kiểu theo API. Tránh nhầm lỗi kiểu của method handle với lỗi phân giải quyền hoặc dispatch.

## 9. Những cách thử sai phổ biến

### Sai 1 — Ép receiver về Parent

~~~java
Child child = new Child();
Method m = Parent.class.getMethod("message");
System.out.println(m.invoke((Parent) child)); // vẫn Child.message
~~~

Cast chỉ cho cùng object một góc nhìn khác ở compile time, không tạo thêm object Parent và không vô hiệu hóa override.

### Sai 2 — Dùng getDeclaredMethod để “khóa” khai báo ở superclass

~~~java
Method m = Parent.class.getDeclaredMethod("message");
System.out.println(m.invoke(new Child())); // vẫn Child.message
~~~

`Declared` nghĩa là metadata khai báo tại Parent; không có nghĩa “chạy đúng thân khai báo của Parent”.

### Sai 3 — Gọi setAccessible(true) để tránh override

~~~java
Method m = Parent.class.getDeclaredMethod("message");
m.setAccessible(true);
System.out.println(m.invoke(new Child())); // vẫn Child.message
~~~

Đoạn này có thể chạy với các class trong ví dụ classpath, nhưng ở named module quyền truy cập sâu có thể không được cấp. Dù bật thành công, virtual dispatch vẫn giữ nguyên.

### Sai 4 — Tạo Lookup ở vị trí tùy ý nhưng khai specialCaller là Child

~~~java
// Minh họa kỳ vọng sai khi đặt trong một lớp gọi không có quyền tương ứng:
MethodHandles.lookup().findSpecial(
        Parent.class, "message",
        MethodType.methodType(String.class), Child.class);
~~~

Chỉ việc gõ `Child.class` ở tham số không biến lookup của lớp gọi thành lookup có quyền của Child. Ngữ cảnh tạo lookup và các kiểm tra đặc biệt mới là yếu tố quyết định. Đặt lời gọi trong Child như ví dụ phần 4 hoặc thiết kế một phương thức ủy quyền hợp pháp.

### Sai 5 — Gọi phần super trong framework để bỏ qua mọi logic của lớp con

Một framework cố tình bỏ qua override có thể phá vỡ invariant mà Child duy trì (kiểm tra input, đồng bộ, transaction, logging, bảo mật hay cache). Chỉ gọi superclass trực tiếp khi hợp đồng API thật sự cho phép. Trong nhiều tình huống, gọi đa hình là hành vi đúng và an toàn hơn.

## 10. Quy trình lựa chọn trong một tình huống thực tế

Giả sử bạn đang viết bộ chạy lệnh dựa vào annotation:

1. **Muốn chạy hành vi của đối tượng thực:** tìm `Method` hợp lệ và `invoke(receiver)`; giữ đa hình nếu method có thể override.
2. **Muốn đọc annotation hoặc chữ ký trên superclass:** dùng `Parent.class.getDeclaredMethod(...)` và kiểm tra metadata; đừng suy ra từ đó thân Parent sẽ được gọi.
3. **Muốn đóng gói logic của Parent để Child chủ động gọi:** viết lời gọi `super.foo()` trong Child hoặc một phương thức forwarding rõ nghĩa.
4. **Cần cơ chế gọi động có ngữ nghĩa super và có quyền của Child:** cân nhắc `Lookup.findSpecial` hoặc `unreflectSpecial` từ lookup hợp lệ, kiểm thử quyền trong môi trường module thực tế.
5. **Không kiểm soát Child hoặc thiếu quyền lookup:** đừng dựa vào việc đổi `getDeclaredMethod`, cast hay `setAccessible` để lách dispatch. Xem lại thiết kế và cung cấp API được chủ sở hữu lớp cho phép.

Nếu mục tiêu là chẩn đoán một kết quả lạ, hãy ghi lại cả **`method.getDeclaringClass()`** và **`receiver.getClass()`**, tên method, signature và kiểu lời gọi (`invoke`, `findVirtual` hay `findSpecial`). Hai kiểu class này là chứng cứ quan trọng nhưng cần hiểu chúng giữ vai trò khác nhau.

## 11. Bài tập tự kiểm tra (kèm đáp án)

**Bài 1.** `Parent p = new Child();`, `Method m = Parent.class.getMethod("message");`. `m.invoke(p)` chạy gì?

**Đáp án:** `Child.message` vì receiver thật là Child và method có override hợp lệ.

**Bài 2.** Đổi `getMethod` thành `getDeclaredMethod` nhưng giữ receiver Child. Kết quả có khác không?

**Đáp án:** Không; thay cơ chế tìm Method metadata không thay virtual dispatch.

**Bài 3.** Có thể viết `((Parent) child).message()` để thực hiện `Parent.message` không?

**Đáp án:** Không. Ép kiểu tham chiếu không thay lớp runtime của object, nên lời gọi ảo vẫn chọn `Child.message`.

**Bài 4.** `setAccessible(true)` của `Parent.message` đã thành công. Điều đó có buộc Reflection gọi thân Parent không?

**Đáp án:** Không. Quyền truy cập và dispatch là hai cơ chế riêng.

**Bài 5.** Trong `Child` muốn gọi phần triển khai của `Parent` một cách đơn giản nhất thì dùng gì?

**Đáp án:** `super.message()` trong phương thức instance của Child có quyền gọi. Khi phải tạo lời gọi động, `findSpecial` từ lookup đủ quyền mới phù hợp.

**Bài 6.** `Parent.category` và `Child.category` đều là `static`. Gọi `Parent.class.getMethod("category").invoke(null)` chạy phương thức nào?

**Đáp án:** `Parent.category`, vì static method không có override theo kiểu instance; đây là hiding.

**Bài 7.** Method của Parent là `final`. Có thể viết override cùng chữ ký trong Child để Reflection chọn Child không?

**Đáp án:** Không. Java cấm override phương thức final; gọi method hợp lệ trên Child dùng phần triển khai final được kế thừa.

**Bài 8.** Một tool ở module khác tạo `findSpecial(Parent.class, "message", ..., Child.class)` nhưng nhận `IllegalAccessException`. Có nên dùng `setAccessible(true)` trên một Method bất kỳ để sửa?

**Đáp án:** Không. Phải xét ngữ cảnh lookup, quyền của specialCaller và ranh giới module; `setAccessible` trên `Method` không tự cấp quyền để tạo special handle hay thay ngữ nghĩa dispatch.

## 12. Ghi nhớ và nguồn tham khảo

**Quy tắc trọng tâm:** `Method.invoke` với phương thức instance có thể override vẫn dùng **virtual dispatch**; một `Method` khai báo ở Parent không bảo đảm thực thi thân Parent trên Child receiver. Để cố ý gọi triển khai superclass, dùng `super` trong mã có quyền, hoặc method handle **special** được tạo với lookup hợp lệ. `getDeclaredMethod`, ép kiểu receiver và `setAccessible` không phải cách thay đổi dispatch.

Tài liệu chính thức Java SE 17:

- [`java.lang.reflect.Method` — mục `invoke`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html): dynamic method lookup theo kiểu runtime và ngoại lệ khi gọi bằng Reflection.
- [`MethodHandles.Lookup` — `findVirtual`, `findSpecial`, `unreflectSpecial`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandles.Lookup.html): các kiểu handle, quyền lookup và caller đặc biệt.
- [JLS `15.12.4.4 — Locate Method to Invoke](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.12.4.4): quy tắc xác định method đích khi gọi.
- [JVMS `6.5 — `invokevirtual` và `invokespecial`](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html): ngữ nghĩa chỉ thị gọi ở JVM.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="methods-009">Primitive return được boxing và void trả về null</a>

<details>
<summary>Xem chi tiết</summary>

# Primitive return được boxing và `void` trả về `null` khi dùng `Method.invoke`

Trong Java Reflection, `java.lang.reflect.Method.invoke(Object obj, Object... args)` có **kiểu trả về `Object`**, dù phương thức thực tế được khai báo trả về `int`, `boolean`, `String`, `Integer` hay `void`. Vì vậy, khi gọi phương thức bằng Reflection, cần phân biệt **kiểu trả về được khai báo** với **đối tượng mà `invoke` trả lại**.

Hai quy tắc trọng tâm:

1. Nếu phương thức được gọi **trả về một giá trị primitive** (`int`, `boolean`, `double`...), `Method.invoke` **boxing** giá trị ấy thành đối tượng wrapper tương ứng (`Integer`, `Boolean`, `Double`...) trước khi trả về.
2. Nếu phương thức được khai báo **`void`**, `Method.invoke` **trả về `null` khi lời gọi kết thúc bình thường**. `null` ở đây là kết quả theo hợp đồng của Reflection, không phải giá trị do một phương thức `void` tự `return`.

Ngoài ra, một phương thức có **kiểu trả về tham chiếu** (`String`, `Integer`, một object bất kỳ) có thể thực sự trả về `null`. Bởi vậy, **chỉ nhìn `result == null` không thể biết phương thức gốc là `void` hay là một phương thức trả về tham chiếu đang có giá trị `null`**. Phải đọc metadata bằng `Method.getReturnType()`.

## 1. `Method.invoke` làm gì với giá trị trả về?

Chữ ký rút gọn:

```java
Object result = method.invoke(receiver, arguments);
```

- `method`: đối tượng `Method` mô tả một phương thức cụ thể đã được tìm bằng Reflection.
- `receiver`: instance dùng để gọi instance method; với static method, có thể truyền `null` vì không cần instance.
- `arguments`: các đối số đầu vào; phải phù hợp số lượng và kiểu tham số của phương thức.
- `result`: kết quả của lời gọi dưới dạng `Object` hoặc `null`. Nếu code bên trong phương thức ném exception, việc gọi không tạo ra một giá trị trả về bình thường.

`Method.invoke` không thay đổi kiểu trả về **được khai báo** của phương thức. Nó chỉ cần biểu diễn một kết quả có thể thuộc nhiều kiểu Java khác nhau qua **một kiểu trả về duy nhất của API**: `Object`.

| Phương thức khai báo | Giá trị logic sau khi method chạy | Kết quả từ `Method.invoke` |
|---|---|---|
| `int count()` | `7` | `Integer.valueOf(7)` dưới dạng `Object` |
| `long total()` | `30L` | Một `Long` có giá trị `30` |
| `boolean enabled()` | `false` | `Boolean.FALSE` dưới dạng `Object` |
| `char letter()` | `'A'` | Một `Character` có giá trị `'A'` |
| `double rate()` | `2.5` | Một `Double` có giá trị `2.5` |
| `String name()` | `"Lan"` | Tham chiếu tới `String` chứa `"Lan"` |
| `String missing()` | `null` | `null` |
| `Integer nullable()` | `null` | `null` |
| `void update()` | Không có giá trị trả về | `null` |

Các ví dụ trên giả định phương thức hoàn tất **bình thường** và caller có quyền truy cập phương thức. Nếu method ném exception, `invoke` thường báo `InvocationTargetException`, không trả về `null` như kết quả thông thường.

### Boxing áp dụng cho đủ tám primitive

| Primitive return | Wrapper trong kết quả Reflection |
|---|---|
| `boolean` | `java.lang.Boolean` |
| `byte` | `java.lang.Byte` |
| `short` | `java.lang.Short` |
| `char` | `java.lang.Character` |
| `int` | `java.lang.Integer` |
| `long` | `java.lang.Long` |
| `float` | `java.lang.Float` |
| `double` | `java.lang.Double` |

Giá trị primitive `0`, `false` hay `'\u0000'` **không** trở thành `null`. Reflection vẫn tạo hoặc trả về wrapper biểu diễn giá trị đó. Việc JVM dùng cache wrapper cho một số giá trị hay tạo object mới là chi tiết không nên dựa vào: kiểm tra **giá trị bằng `equals` hoặc bằng phép so sánh primitive**, không so sánh danh tính wrapper bằng `==`.

## 2. `void` khác `Void` và khác phương thức trả về tham chiếu `null`

Ba khai báo sau có ngữ nghĩa khác nhau:

```java
public void log() {
    System.out.println("logged");
}

public String findName() {
    return null;
}

public Void noResultObject() {
    return null;
}
```

Đây là ba method minh họa, chưa phải chương trình có class và `main`. Khi gọi qua Reflection:

- `log()` chạy side effect là in ra `logged`; `Method.invoke` trả **`null`** vì kiểu trả về là `void`.
- `findName()` trả tham chiếu **`null`** do chính method trả về.
- `noResultObject()` khai báo **`java.lang.Void`** (một lớp tham chiếu) và trả `null`. `Void` thường được dùng trong API generic để biểu diễn không có giá trị kết quả; nó **khác** từ khóa `void`.

Không có “wrapper object” được Reflection tạo ra cho kết quả `void`. Giá trị `null` của `invoke` trong trường hợp này **không phải** `new Void()` và cũng không phải một instance `Void`.

Mặc dù ba lời gọi trên đều có thể trả `null` từ `invoke`, metadata của chúng khác nhau:

```java
Method log = Example.class.getMethod("log");
Method findName = Example.class.getMethod("findName");
Method noResultObject = Example.class.getMethod("noResultObject");

System.out.println(log.getReturnType() == void.class);               // true
System.out.println(findName.getReturnType() == String.class);        // true
System.out.println(noResultObject.getReturnType() == Void.class);    // true
```

Đây là **đoạn minh họa**; giả sử `Example` là class public chứa ba method public trên, và đoạn code nằm trong một phương thức có khai báo `throws ReflectiveOperationException` hoặc xử lý `NoSuchMethodException`.

Vì vậy, để phân biệt **không có return value theo chữ ký** với **một return value đang là null**, luôn đọc kiểu khai báo bằng `getReturnType()`.

## 3. Ví dụ Java 17+ hoàn chỉnh

Lưu thành **`MethodReturnDemo.java`**:

```java
import java.lang.reflect.Method;

public class MethodReturnDemo {
    public static class Service {
        public int count() { return 7; }
        public boolean active() { return false; }
        public char initial() { return 'A'; }
        public long total() { return 30L; }
        public void touch() { System.out.println("touch body"); }
        public String missing() { return null; }
        public Integer nullable() { return null; }
        public Integer boxed() { return 17; }
        public static double rate() { return 2.5; }
        public void fail() { throw new IllegalStateException("broken"); }
    }

    public static void main(String[] args) throws Exception {
        Service service = new Service();
        Class<Service> type = Service.class;

        Method count = type.getMethod("count");
        Object countValue = count.invoke(service);
        System.out.println("count return type: " + count.getReturnType().getName());
        System.out.println("count value: " + countValue + " / " + countValue.getClass().getSimpleName());

        Object active = type.getMethod("active").invoke(service);
        System.out.println("active value: " + active + " / " + active.getClass().getSimpleName());
        Object initial = type.getMethod("initial").invoke(service);
        System.out.println("initial value: " + initial + " / " + initial.getClass().getSimpleName());
        Object total = type.getMethod("total").invoke(service);
        System.out.println("total value: " + total + " / " + total.getClass().getSimpleName());

        Method touch = type.getMethod("touch");
        Object voidResult = touch.invoke(service);
        System.out.println("touch return type: " + touch.getReturnType().getName());
        System.out.println("touch result is null: " + (voidResult == null));

        Method missing = type.getMethod("missing");
        System.out.println("missing return type: " + missing.getReturnType().getName());
        System.out.println("missing result is null: " + (missing.invoke(service) == null));

        Method nullable = type.getMethod("nullable");
        Object nullableValue = nullable.invoke(service);
        System.out.println("nullable return type: " + nullable.getReturnType().getName());
        System.out.println("nullable result is null: " + (nullableValue == null));

        Object boxedValue = type.getMethod("boxed").invoke(service);
        System.out.println("boxed value: " + boxedValue + " / " + boxedValue.getClass().getSimpleName());

        Object rateValue = type.getMethod("rate").invoke(null);
        System.out.println("static rate: " + rateValue + " / " + rateValue.getClass().getSimpleName());

        int converted = (Integer) count.invoke(service);
        System.out.println("converted int: " + converted);

        try {
            int invalid = (Integer) nullableValue;
            System.out.println(invalid);
        } catch (NullPointerException ex) {
            System.out.println("nullable unboxing: " + ex.getClass().getSimpleName());
        }

        try {
            type.getMethod("fail").invoke(service);
        } catch (java.lang.reflect.InvocationTargetException ex) {
            System.out.println("method threw: " + ex.getCause().getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy trong thư mục chứa tệp (JDK 17 hoặc mới hơn):

```bash
javac MethodReturnDemo.java
java MethodReturnDemo
```

**Stdout đã được đối chiếu bằng JDK 21:**

```text
count return type: int
count value: 7 / Integer
active value: false / Boolean
initial value: A / Character
total value: 30 / Long
touch body
touch return type: void
touch result is null: true
missing return type: java.lang.String
missing result is null: true
nullable return type: java.lang.Integer
nullable result is null: true
boxed value: 17 / Integer
static rate: 2.5 / Double
converted int: 7
nullable unboxing: NullPointerException
method threw: IllegalStateException
```

### Phân tích theo thứ tự thực thi

1. `Service.count()` khai báo trả `int`; `Method.getReturnType().getName()` là `int`, nhưng object nhận từ `invoke` thuộc lớp runtime `Integer`. Hai thông tin cùng đúng vì **primitive được boxing ở ranh giới Reflection**.
2. `active()` trả primitive `false` nên `invoke` đưa ra `Boolean(false)`; `initial()` trả primitive `'A'` nên có `Character`; `total()` trả `30L` nên có `Long`.
3. `touch()` vẫn chạy nội dung và in `touch body`. Sau khi hoàn tất, kết quả `Method.invoke` là `null` bởi phương thức khai báo `void`. `getReturnType()` của method vẫn là `void.class`.
4. `missing()` và `nullable()` khai báo kiểu **tham chiếu** nên có thể trả `null` từ logic của chính chúng. Kết quả của `invoke` cũng bằng `null`, nhưng **không đồng nghĩa** hai method này là `void`.
5. `boxed()` trả kiểu khai báo `Integer` với giá trị `17`. `invoke` trả chính tham chiếu kết quả, không thực hiện một bước boxing `int` bổ sung do Reflection: phần `return 17` đã được Java boxing theo ngữ nghĩa của method.
6. `rate()` là `static`, nên `invoke(null)` dùng receiver `null` hợp lệ. Nó trả primitive `double`, và Reflection biểu diễn bằng `Double`. **Receiver `null`** khác hẳn **return `null`**.
7. `(Integer) count.invoke(service)` ép kiểu object về `Integer` rồi tự unbox khi gán cho `int`. Việc unbox một tham chiếu `null` của `nullable()` gây `NullPointerException`.
8. `fail()` ném `IllegalStateException` bên trong phương thức thực. Reflection **không trả về `null`**; `invoke` ném `InvocationTargetException`, và `getCause()` chính là `IllegalStateException`.

Chỉ có **một file public class** (`MethodReturnDemo.java`); nested class `Service` giúp chương trình chạy độc lập, không phụ thuộc thư viện bên ngoài.

## 4. Kiểm tra kiểu trả về bằng `getReturnType()`

`Method.getReturnType()` trả một đối tượng `Class<?>` biểu diễn **kiểu trả về khai báo**. Nó không gọi method, không đọc giá trị thực tế và không boxing loại khai báo thành wrapper.

| Khai báo | `method.getReturnType()` | `getReturnType().getName()` |
|---|---|---|
| `int read()` | `int.class` | `int` |
| `boolean ok()` | `boolean.class` | `boolean` |
| `void reset()` | `void.class` | `void` |
| `Integer readBoxed()` | `Integer.class` | `java.lang.Integer` |
| `String name()` | `String.class` | `java.lang.String` |
| `int[] values()` | `int[].class` | JVM binary name `[I` |

Nếu một method trả **mảng primitive**, chẳng hạn `int[]`, bản thân mảng là **reference type**. `Method.invoke` trả tham chiếu tới **mảng**, không boxing từng phần tử `int` thành `Integer`. Bạn có thể dùng `getReturnType().isArray()` và `getReturnType().getComponentType()` để kiểm tra loại mảng.

Một cách phân loại kết quả trong code generic:

```java
// Đây là đoạn minh họa nằm trong một hàm đã có biến:
// Method method, Object receiver, Object[] arguments.
Class<?> declaredType = method.getReturnType();
Object result = method.invoke(receiver, arguments);

if (declaredType == void.class) {
    System.out.println("Method has no return value");
} else if (declaredType.isPrimitive()) {
    System.out.println("A primitive was boxed: " + result.getClass().getName());
} else if (result == null) {
    System.out.println("Reference return type, actual value is null");
} else {
    System.out.println("Reference result: " + result.getClass().getName());
}
```

**Lý do kiểm tra `void.class` trước:** `void` có cách xử lý kết quả đặc biệt. Không được truy cập `result.getClass()` khi method trả `void` vì `result == null`. Đoạn code giả định invocation hoàn tất bình thường và các exception đã được xử lý ở tầng gọi thích hợp.

### `getGenericReturnType()` khác gì?

`getReturnType()` trả kiểu runtime sau *type erasure*, dưới dạng `Class<?>`. `getGenericReturnType()` trả `java.lang.reflect.Type`, có thể giữ thông tin `List<String>`, `T` hoặc các kiểu generic khác trong chữ ký metadata.

Ví dụ `List<String> names()` có `getReturnType() == List.class`; `getGenericReturnType()` có thể mô tả `List<String>`. Dù vậy, **một `List<String>` là kiểu tham chiếu**, không nằm trong trường hợp boxing primitive. Với `void` và kiểu primitive thuần, logic của `Method.invoke` vẫn như đã giải thích.

## 5. Unboxing kết quả Reflection đúng cách

Vì `Method.invoke` trả `Object`, không thể gán trực tiếp nó cho biến `int` mà không chuyển đổi kiểu tương ứng:

```java
// Giả sử m là Method cho public int count() và receiver hợp lệ.
Object raw = m.invoke(receiver);
Integer boxed = (Integer) raw;
int count = boxed;
```

- `(Integer) raw` là ép kiểu tham chiếu từ `Object` về wrapper thực tế.
- `int count = boxed` dùng **unboxing** do Java thực hiện.
- Nếu `raw` là `null`, phép unboxing sẽ gây `NullPointerException`.
- Nếu `raw` là wrapper không tương thích (ví dụ `Long` nhưng ép `Integer`), phép cast sẽ gây `ClassCastException`.

Khi method thực sự khai báo `int` và hoàn tất bình thường, kết quả `Method.invoke` là `Integer` **không null**. Tuy nhiên, code Reflection tổng quát thường không biết trước phương thức được truyền vào; do đó không nên ép tất cả kết quả sang `Integer`.

Một `Number` có thể được dùng để xử lý nhiều wrapper số (`Byte`, `Short`, `Integer`, `Long`, `Float`, `Double`), nhưng các phương thức `intValue()`, `longValue()`... có thể **mất độ chính xác** khi thu hẹp số hoặc chuyển số thực sang số nguyên. Không áp dụng `Number` cho `Boolean` và `Character`. Nếu hợp đồng method yêu cầu đúng kiểu, kiểm tra `getReturnType()` trước khi chuyển đổi, không âm thầm biến mọi số thành cùng một kiểu.

## 6. Phân biệt ba loại `null` thường gặp

Từ `null` có thể xuất hiện ở **receiver**, **đối số đầu vào** và **kết quả trả về**:

| Vị trí | Ví dụ | Ý nghĩa |
|---|---|---|
| Receiver trong `invoke` | `staticMethod.invoke(null)` | Hợp lệ: static method không cần một instance |
| Receiver trong `invoke` | `instanceMethod.invoke(null)` | Sai: instance method cần object hợp lệ; có thể ném `NullPointerException` |
| Argument trong `invoke` | `method.invoke(obj, (Object) null)` | Truyền **một đối số null** vào method có một tham số tham chiếu tương thích; không đồng nghĩa method trả null |
| Giá trị kết quả | `Object result = voidMethod.invoke(obj)` | `null` là kết quả chuẩn khi phương thức gốc có kiểu `void` và thực thi bình thường |
| Giá trị kết quả | `Object result = stringMethod.invoke(obj)` | Có thể `null` vì method kiểu tham chiếu thực sự trả `null` |

Đặc biệt, `Method.invoke` là một **varargs method** có kiểu tham số `Object... args`. Nếu cần truyền **đúng một đối số `null`** cho phương thức có một tham số tham chiếu, cách viết rõ ràng là:

```java
method.invoke(receiver, (Object) null);
```

Nếu method khai báo **không tham số**, gọi:

```java
method.invoke(receiver);
```

Đối với nhiều tham số, truyền các arguments theo thứ tự. Đừng tùy ý truyền `null` vào primitive parameter: lời gọi cần unboxing giá trị đầu vào nhưng `null` không có giá trị primitive tương ứng, nên Reflective invocation không thể hoàn tất như một lời gọi hợp lệ.

Ví dụ `method.invoke(receiver, (Object) null)` chỉ liên quan đến **input**. Kiểu trả về method vẫn quyết định việc kết quả `invoke` được boxing hay `null` khi hoàn tất bình thường.

## 7. Khi phương thức ném exception thì có trả `null` không?

**Không.** Nếu code thực thi trong method ném exception, lời gọi `Method.invoke` thường ném `InvocationTargetException` bọc exception gốc:

```java
try {
    Object result = method.invoke(receiver);
    System.out.println("Completed with result: " + result);
} catch (java.lang.reflect.InvocationTargetException ex) {
    Throwable original = ex.getCause();
    System.err.println("Method failed: " + original.getClass().getName());
}
```

Đây là **đoạn minh họa**, biến `method` và `receiver` có từ môi trường gọi. Khi method trả `void` **và chạy thành công**, biến `result` nhận `null`. Khi method `void` **ném exception**, lệnh gán `result` không hoàn tất bình thường; không có kết quả `null` để kiểm tra.

Cũng cần tách exception xảy ra **trước khi method được gọi**:

- `NoSuchMethodException`: không tìm thấy method tại `Class.getMethod`/`getDeclaredMethod`.
- `IllegalAccessException`: Reflection không được phép gọi member theo access rules.
- `IllegalArgumentException`: sai kiểu/số lượng đối số, receiver sai kiểu hoặc các điều kiện invocation không hợp lệ.
- `NullPointerException`: receiver là `null` cho instance method hoặc tình huống lỗi null ở caller; với method bên trong tự ném `NullPointerException`, lỗi ấy thường là **cause của `InvocationTargetException`**.
- `ExceptionInInitializerError` và các lỗi khởi tạo lớp có thể xuất hiện khi thực sự dùng một static method mà class chưa khởi tạo thành công.

Không nên dùng `result == null` để kết luận lời gọi thất bại: đó có thể là kết quả **bình thường** của `void` hoặc của method tham chiếu.

## 8. Ứng dụng trong công cụ gọi method động

Một bộ gọi API/plugin hoặc framework có thể không biết trước return type của các method mà nó xử lý. Để biểu diễn chính xác, nên giữ **cả metadata và giá trị**:

```java
// Đoạn minh họa; method/receiver/arguments được cung cấp từ caller.
Class<?> returnType = method.getReturnType();
Object value = method.invoke(receiver, arguments);

if (returnType == void.class) {
    // Thành công nhưng không có giá trị trả về theo chữ ký.
} else if (value == null) {
    // Phương thức kiểu tham chiếu đã trả về null.
} else {
    // Có đối tượng kết quả: wrapper cho primitive,
    // hoặc object tham chiếu trả về từ phương thức.
}
```

Trong một API kết quả tổng quát, có thể biểu diễn “không có giá trị trả về theo chữ ký” bằng một trạng thái riêng, và “có kiểu trả về tham chiếu nhưng giá trị null” bằng trạng thái khác. Không nên đưa cả hai trường hợp vào cùng nhãn `null` nếu người dùng API cần phân biệt.

Nếu hệ thống serialization nhận giá trị từ `invoke`:

- Với primitive return, wrapper `Integer`/`Boolean`/... có thể được serializer xử lý như một giá trị bình thường.
- Với `void`, không có payload theo chữ ký; ứng dụng có thể biểu diễn thành công bằng response không có body, status hoặc thông tin tác vụ riêng.
- Với reference return `null`, một hợp đồng API có thể cho phép payload nullable, thiếu field hoặc thông điệp lỗi tùy ý nghĩa nghiệp vụ. Reflection **không** tự quyết định ngữ nghĩa này.

**Lưu ý hiệu năng:** boxing/unboxing và chi phí reflective dispatch có thể đáng quan tâm trong vòng lặp rất nóng. Trước hết hãy đo thực tế và thiết kế theo nhu cầu; đừng diễn giải việc thấy `Integer` ở kết quả `invoke` là bằng chứng luôn có object mới được cấp phát ở mỗi lời gọi.

## 9. Lỗi tư duy phổ biến

1. **“Phương thức trả `int` thì `invoke` cũng trả `int`.”** Sai: API `invoke` trả `Object`, primitive được boxing thành wrapper.
2. **“`void` được boxing thành `Void`.”** Sai: `void` không có giá trị và `invoke` trả **`null`**, không tạo instance `Void`.
3. **“Kết quả `invoke` là `null` thì method phải là `void`.”** Sai: một method có return type tham chiếu cũng có thể thực sự trả `null`.
4. **“Method kiểu primitive có thể trả `null` khi giá trị bằng 0.”** Sai: `0`, `false` và các giá trị primitive khác vẫn được boxing bình thường.
5. **“`getReturnType()` trả `Integer.class` nếu method khai báo `int`.”** Sai: `getReturnType()` là `int.class`, còn đối tượng kết quả `invoke` là `Integer`.
6. **“`Void.class` và `void.class` giống nhau.”** Sai: `Void` là reference type; `void` là từ khóa biểu diễn không có return value.
7. **“`touch()` là `void` nên không chạy code khi Reflection gọi nó.”** Sai: code vẫn chạy và có thể gây side effect; chỉ không có giá trị trả về.
8. **“`null` từ method trả `Integer` unbox thành 0.”** Sai: unboxing một tham chiếu `null` ném `NullPointerException`.
9. **“Method ném exception thì `invoke` trả `null`.”** Sai: invocation không hoàn tất bình thường; thường nhận `InvocationTargetException`.
10. **“Receiver `null` nghĩa là method trả `null`.”** Sai: receiver `null` hợp lệ với static method và hoàn toàn độc lập với return value.

## 10. Câu hỏi tự kiểm tra

**Câu 1.** Method khai báo `public double price() { return 1.5; }`. `Method.invoke(obj)` trả object kiểu gì?

**Đáp án:** `Double` chứa `1.5`, dưới dạng `Object`. `getReturnType()` vẫn là `double.class`.

**Câu 2.** `public void save() { System.out.println("saved"); }` được gọi qua `invoke`. Có chạy lệnh in không, và `invoke` trả gì?

**Đáp án:** Có, method thực sự chạy và in `saved`. Nếu hoàn tất bình thường, `invoke` trả `null`.

**Câu 3.** `public String lookup() { return null; }` và `public void clear() { }` đều khiến `invoke` trả `null`. Làm sao biết trường hợp nào không có return value theo chữ ký?

**Đáp án:** Dùng `method.getReturnType() == void.class`. Với `lookup`, return type là `String.class` và giá trị thực tế bằng `null`; với `clear`, return type là `void.class`.

**Câu 4.** `getReturnType()` của phương thức khai báo `Integer count()` là gì, và `invoke` có chắc khác `null` không?

**Đáp án:** `Integer.class`. Không chắc: `Integer` là reference type nên method có thể trả `null`.

**Câu 5.** Khi gọi `public int compute() { throw new IllegalStateException(); }` bằng Reflection, kết quả có phải là một `Integer` chứa giá trị mặc định không?

**Đáp án:** Không. Vì method ném exception, lời gọi thất bại; `Method.invoke` ném `InvocationTargetException` với cause là `IllegalStateException`. Không có giá trị trả về bình thường để boxing.

**Câu 6.** Tại sao không gọi `result.getClass()` ngay sau mọi `invoke`?

**Đáp án:** Vì `result` có thể bằng `null` cho method `void` hoặc method trả kiểu tham chiếu với giá trị null. Khi đó `result.getClass()` gây `NullPointerException`; cần kiểm tra `result == null` và `getReturnType()` trước.

## Tài liệu Java chuẩn

- Java SE API — `java.lang.reflect.Method.invoke(Object, Object...)`: cách gọi method, boxing primitive return, kết quả `null` cho `void`, và `InvocationTargetException`.
- Java SE API — `java.lang.reflect.Method.getReturnType()`, `getGenericReturnType()`: metadata của kiểu trả về.
- Java Language Specification — §5.1.7 *Boxing Conversion*, §5.1.8 *Unboxing Conversion*, §8.4.5 *Method Result*, §14.17 *The return Statement*.
- Java SE API — `java.lang.Void`: lớp tham chiếu biểu diễn kiểu `void` trong một số API dùng `Class` hoặc generic.

**Ghi nhớ:** `Method.invoke` luôn có kiểu trả về **`Object`**. Primitive return được **boxing thành wrapper**; method `void` chạy bình thường thì **`invoke` trả `null`**. Method trả tham chiếu cũng có thể trả `null`; hãy dùng **`getReturnType()`** để phân biệt hai trường hợp, và xử lý exception riêng với kết quả trả về.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="methods-010">Phương thức abstract, default và phương thức interface</a>

<details>
<summary>Xem chi tiết</summary>

# Phương thức abstract, default và phương thức interface trong Java Reflection

Trong Java Reflection, một đối tượng `java.lang.reflect.Method` mô tả phương thức được khai báo ở **class hoặc interface**. Không phải mọi `Method` đều có thân mã để chạy trực tiếp: phương thức `abstract` đặt ra hợp đồng, phương thức `default` trong interface cung cấp phần cài đặt mặc định, phương thức `static` thuộc về interface, còn phương thức `private` của interface chỉ phục vụ logic nội bộ. Cách Reflection **phát hiện** một phương thức và cách `Method.invoke(...)` **thực thi** nó là hai vấn đề cần tách bạch.

Bài này hướng dẫn xác định loại phương thức bằng `Modifier.isAbstract(...)`, `Method.isDefault()` và `Modifier.isStatic(...)`; phân biệt phạm vi `getDeclaredMethod(s)` và `getMethod(s)`; thực thi phương thức interface bằng Reflection; hiểu kế thừa, override, xung đột default method; và tránh nhầm *default method* với *default value* của annotation.

## 1. Nhận diện đúng các loại phương thức

Ví dụ một interface khai báo bốn loại method:

~~~java
interface Named {
    String name(); // public abstract ngầm định

    default String greeting() {
        return decorate("Hi " + name());
    }

    static String source() {
        return "interface-static";
    }

    private String decorate(String text) {
        return "[" + text + "]";
    }
}
~~~

Đây là **đoạn minh họa**, chưa phải chương trình độc lập. Java 8 giới thiệu `default` và `static` method trong interface; từ Java 9, interface có thể có `private` method (instance hoặc static) để chia sẻ logic nội bộ. Interface method `private` có thân mã, nhưng không phải default method và không được kế thừa như API public.

| Khai báo trên interface | Có thân mã? | Modifier/phương thức Reflection cần quan sát | `Method.isDefault()` |
| --- | --- | --- | --- |
| `String name();` | Không | `Modifier.isAbstract(method.getModifiers()) == true` | `false` |
| `default String greeting() {...}` | Có | Public, không abstract, không static; `method.isDefault() == true` | `true` |
| `static String source() {...}` | Có | `Modifier.isStatic(...) == true` | `false` |
| `private String decorate(...) {...}` | Có | `Modifier.isPrivate(...) == true` | `false` |

Một method ở class thường có thân mã cũng **không** trở thành default method. `isDefault()` dành cho **public instance method có phần cài đặt và được khai báo trong interface**, không chỉ mang nghĩa chung là "có code". Không có `Modifier.isDefault(...)`; hãy gọi `Method.isDefault()`.

### `Method` nào mang thông tin gì?

`Method` cho phép đọc:

- `getName()`: tên phương thức.
- `getDeclaringClass()`: class/interface **thực sự khai báo** method.
- `getModifiers()`: cờ như `public`, `private`, `abstract`, `static`, `final`. Kiểm tra bằng `java.lang.reflect.Modifier`.
- `isDefault()`: phân biệt chính xác default method của interface.
- `getParameterTypes()`, `getReturnType()`: chữ ký runtime; các phiên bản `getGenericParameterTypes()` và `getGenericReturnType()` phục vụ metadata generic.
- `isBridge()`, `isSynthetic()`: nhận biết một số method do compiler sinh ra, rất hữu ích khi quét toàn bộ danh sách.
- `invoke(receiver, args...)`: thực thi khi có receiver, đối số và quyền truy cập phù hợp.

`getDeclaringClass().isInterface()` giúp biết method **được khai báo trên interface**, nhưng **không đủ** kết luận nó là abstract hay default. Interface vẫn có static/private method.

## 2. Phương thức abstract: nhận biết bằng `Modifier.isAbstract`

Một interface có thể khai báo abstract instance method:

~~~java
interface Shape {
    double area(); // public abstract
}
~~~

Với `Method m = Shape.class.getDeclaredMethod("area")`:

~~~java
boolean abstractMethod = Modifier.isAbstract(m.getModifiers());
boolean defaultMethod = m.isDefault();
~~~

Hai kết quả lần lượt là `true` và `false`. Đây là các đoạn minh họa; cần import `java.lang.reflect.Method` và `java.lang.reflect.Modifier` khi đặt trong chương trình độc lập.

Lớp `abstract` cũng có thể khai báo abstract method, ví dụ `abstract class Base { abstract void work(); }`. `Modifier.isAbstract` vẫn là API đúng để nhận biết; `isDefault()` tiếp tục trả `false` vì đây không phải interface default method.

**Quan trọng:** một `Method` phản chiếu abstract method **vẫn có thể được gọi** bằng `Method.invoke(...)` nếu receiver là đối tượng thuộc lớp triển khai cụ thể và quá trình dispatch tìm được implementation. `Method.invoke` trên instance method sử dụng cơ chế tra cứu động dựa trên **kiểu runtime của receiver**. Nó không có nghĩa JVM sẽ cố chạy thân mã của một phương thức abstract vốn không tồn tại.

Ví dụ nếu `Named` khai báo `String name()` và `BasicNamed` triển khai `name()` trả `"An"`, lời gọi sau sẽ trả `"An"`:

~~~java
Method method = Named.class.getDeclaredMethod("name");
Object result = method.invoke(new BasicNamed());
~~~

Đoạn trên giả sử `Named`, `BasicNamed` đã có khai báo như ví dụ đầy đủ ở phần tiếp theo. Receiver phải tương thích với interface khai báo method; nếu truyền đối tượng không phù hợp, `invoke` ném `IllegalArgumentException`.

## 3. Default method: phát hiện bằng `Method.isDefault()`

Từ Java 8, interface được phép khai báo instance method có thân mã bằng từ khóa `default`:

~~~java
interface Loggable {
    default String label() {
        return "item";
    }
}
~~~

`Loggable.class.getDeclaredMethod("label").isDefault()` là `true`. Phương thức này là `public`, không `abstract` và không `static`. Class triển khai có thể **kế thừa default implementation**, hoặc **override** nó bằng một method riêng.

Một override viết trong class triển khai là **class method**, nên `isDefault()` trên đối tượng `Method` phản chiếu override đó là `false` dù nó thay thế hành vi của một default method ở interface.

Vì vậy, cần phân biệt:

1. **Nơi method được khai báo:** `method.getDeclaringClass()`.
2. **Nó là default method thật sự không:** `method.isDefault()`.
3. **Lời gọi thực tế chạy implementation nào:** phụ thuộc receiver và dynamic dispatch của `Method.invoke(...)`.

`isDefault()` mô tả **Method object**, không trả lời câu hỏi "đối tượng này có đang sử dụng default implementation hay đã override chưa" chỉ bằng một boolean.

## 4. Ví dụ đầy đủ Java 17+: abstract, default, static và private interface method

Tạo tệp **`InterfaceMethodReflectionDemo.java`**:

~~~java
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

interface Named {
    String name();

    default String greeting() {
        return decorate("Hi " + name());
    }

    private String decorate(String text) {
        return "[" + text + "]";
    }

    static String source() {
        return "interface-static";
    }
}

class BasicNamed implements Named {
    @Override
    public String name() {
        return "An";
    }
}

class CustomNamed implements Named {
    @Override
    public String name() {
        return "Binh";
    }

    @Override
    public String greeting() {
        return "overridden";
    }
}

public class InterfaceMethodReflectionDemo {
    public static void main(String[] args) throws ReflectiveOperationException {
        Class<?> iface = Named.class;

        Method requirement = iface.getDeclaredMethod("name");
        Method fallback = iface.getDeclaredMethod("greeting");
        Method staticMethod = iface.getDeclaredMethod("source");
        Method helper = iface.getDeclaredMethod("decorate", String.class);

        System.out.println("abstract = "
                + Modifier.isAbstract(requirement.getModifiers())
                + ", default = " + requirement.isDefault());
        System.out.println("default = "
                + fallback.isDefault()
                + ", abstract = " + Modifier.isAbstract(fallback.getModifiers()));
        System.out.println("static = "
                + Modifier.isStatic(staticMethod.getModifiers())
                + ", default = " + staticMethod.isDefault());
        System.out.println("private = "
                + Modifier.isPrivate(helper.getModifiers())
                + ", default = " + helper.isDefault());

        System.out.println("inherited declaring type = "
                + BasicNamed.class.getMethod("greeting")
                        .getDeclaringClass().getSimpleName());

        try {
            BasicNamed.class.getDeclaredMethod("greeting");
        } catch (NoSuchMethodException expected) {
            System.out.println("basic declared default = missing");
        }

        System.out.println("default invoke = "
                + fallback.invoke(new BasicNamed()));
        System.out.println("overridden invoke = "
                + fallback.invoke(new CustomNamed()));
        System.out.println("abstract invoke = "
                + requirement.invoke(new BasicNamed()));
        System.out.println("static invoke = " + staticMethod.invoke(null));

        BasicNamed target = new BasicNamed();
        System.out.println("private accessible before = "
                + helper.canAccess(target));
        System.out.println("private open = " + helper.trySetAccessible());
        System.out.println("private invoke = "
                + helper.invoke(target, "OK"));
    }
}
~~~

Chạy bằng Java 17+:

~~~bash
javac --release 17 InterfaceMethodReflectionDemo.java
java InterfaceMethodReflectionDemo
~~~

**Stdout:**

~~~text
abstract = true, default = false
default = true, abstract = false
static = true, default = false
private = true, default = false
inherited declaring type = Named
basic declared default = missing
default invoke = [Hi An]
overridden invoke = overridden
abstract invoke = An
static invoke = interface-static
private accessible before = false
private open = true
private invoke = [OK]
~~~

### Phân tích từng phần

- `name()` là `public abstract` ngầm định của interface. `Modifier.isAbstract(...)` trả `true`; `isDefault()` trả `false`.
- `greeting()` là `default` nên `isDefault()` trả `true`. Nó gọi `decorate(...)` là private helper nội bộ của interface.
- `source()` là `static`. Có thân mã nhưng không phải default; gọi Reflection bằng receiver `null`.
- `decorate(String)` là `private` instance method của interface. Nó có thân mã, nhưng không public và không default.
- `BasicNamed.class.getMethod("greeting")` tìm được public method kế thừa từ interface `Named`; `getDeclaringClass()` là `Named.class`.
- `BasicNamed.class.getDeclaredMethod("greeting")` ném `NoSuchMethodException` vì `BasicNamed` không **tự khai báo** method này.
- `fallback.invoke(new BasicNamed())` chạy default implementation, kết hợp tên `An` thành `[Hi An]`.
- `fallback.invoke(new CustomNamed())` chạy **override** của `CustomNamed` và trả `overridden` dù biến `fallback` vẫn biểu diễn method gốc của interface. Đây là minh chứng cho dynamic dispatch.
- `requirement.invoke(new BasicNamed())` gọi implementation của abstract interface method và trả `An`.
- `helper.trySetAccessible()` thành công trong ví dụ vì các lớp chạy trong unnamed module cùng class loader. Quyền deep reflection có thể khác khi thư viện Reflection chạy ở module khác.

**Đừng hiểu nhầm:** `getDeclaredMethod(...)` có thể tìm private interface method, nhưng tìm thấy metadata không đồng nghĩa gọi được ngay. `Method.invoke(...)` còn xét quyền truy cập; nếu `trySetAccessible()` trả `false` ở môi trường khác thì không được giả định việc gọi tiếp sẽ thành công.

## 5. `getMethod(s)` và `getDeclaredMethod(s)`: tên giống nhưng phạm vi khác

| API trên `Class<?>` | Phạm vi tìm | Với `BasicNamed.class` và `Named#greeting` |
| --- | --- | --- |
| `getDeclaredMethod(name, types...)` | Method **được khai báo trực tiếp** trên class/interface đó, bất kể mức truy cập | Không tìm thấy `greeting()` vì `BasicNamed` không khai báo |
| `getMethod(name, types...)` | Method **public** khả dụng trên class/interface, gồm method public được kế thừa | Có thể tìm `greeting()` với declaring class `Named` |
| `getDeclaredMethods()` | Mảng method được khai báo trực tiếp, kể cả private, static, default, abstract | Không chứa default method chỉ được kế thừa |
| `getMethods()` | Mảng public methods của kiểu, gồm public methods kế thừa | Có thể chứa interface default/abstract methods khả dụng |

Không được giả định thứ tự mảng `getMethods()` hoặc `getDeclaredMethods()` là thứ tự trong file mã nguồn. Phần mềm quét metadata nên lọc theo tên, kiểu tham số, declaring class, modifier, `isBridge()` hoặc `isSynthetic()` khi cần.

### Method `static` ở interface **không được kế thừa**

`Named.source()` là lời gọi hợp lệ theo tên interface, nhưng `BasicNamed.source()` không hợp lệ trong Java source. Với Reflection, nên tra `Named.class.getDeclaredMethod("source")` hoặc `Named.class.getMethod("source")`. Không mong đợi `BasicNamed.class.getMethod("source")` tìm thấy vì static method của interface không được kế thừa bởi class triển khai.

Ngược lại, một default method `public` có thể được kế thừa, nên `BasicNamed.class.getMethod("greeting")` là trường hợp hợp lệ.

### Khi xem nhiều superinterface

Một interface con có thể kế thừa method của interface cha; `getDeclaredMethods()` trên interface con vẫn chỉ cho thấy method nó **khai báo trực tiếp**. `getMethods()` giúp quan sát public method được kế thừa và độ ưu tiên của các phương thức có cùng chữ ký theo quy tắc API. Nếu cần biết **nguồn gốc**, dùng `method.getDeclaringClass()` thay vì suy luận từ `Class<?>` mà bạn gọi `getMethod` trên đó.

## 6. Khác biệt giữa loại `Method` và hành vi `invoke(...)`

`Method.invoke(Object receiver, Object... args)` có hai tình huống chính:

1. Với **instance method** (abstract, default hoặc implementation trong class), truyền một receiver tương thích. JVM thực hiện **dynamic method lookup** trên kiểu runtime của receiver; một override có thể được chọn.
2. Với **static method**, không có receiver instance; dùng `null`. Lời gọi đi tới static method được phản chiếu, không có cơ chế override của instance method.

Điều này dẫn tới điểm dễ sai:

~~~java
Method m = Named.class.getDeclaredMethod("greeting");
System.out.println(m.isDefault()); // true: method khai báo tại Named
System.out.println(m.invoke(new CustomNamed())); // overridden
~~~

`m.isDefault()` vẫn là `true` vì nó mô tả `Named#greeting`; nhưng lời gọi qua `CustomNamed` chạy override của `CustomNamed`. Đừng dùng `Method.invoke` trên default method của interface để cố ép JVM bỏ qua override.

Nếu bạn thực sự cần gọi **chính phần thân default implementation** của interface như biểu thức `SomeInterface.super.m()`, đó là bài toán *special invocation*. Nó khác cơ chế dispatch thông thường của `Method.invoke` và thường được thực hiện bằng API `java.lang.invoke.MethodHandles` với quyền `Lookup` phù hợp. Đừng xử lý bằng cách đổi tên method hoặc gọi `setAccessible(true)` với hy vọng vô hiệu hóa override.

### Khi gọi method abstract

Một `Method` đại diện abstract declaration vẫn có thể được dùng với receiver có implementation cụ thể:

~~~java
Method abstractMethod = Named.class.getDeclaredMethod("name");
Object result = abstractMethod.invoke(new BasicNamed()); // "An"
~~~

Nhưng không thể tạo instance trực tiếp của interface hoặc abstract class chỉ để gọi method. Nếu bytecode/runtime không tìm được implementation hợp lệ, việc thực thi có thể gặp lỗi liên kết/phân giải phương thức; chương trình Java biên dịch nhất quán thông thường đảm bảo implementation thích hợp.

### Khi gọi private interface method

Private interface method được định nghĩa để giúp các method **bên trong interface** chia sẻ logic, không tạo API public. `getDeclaredMethod` vẫn có thể phản chiếu nó. Gọi qua `invoke` từ code bên ngoài interface có thể đòi hỏi `trySetAccessible()`, và Java module/package openness có thể giới hạn việc bật quyền. Trong chương trình bình thường, nên gọi public/default API của interface thay vì dựa vào private helper.

## 7. Khi có nhiều default method cùng chữ ký

Java có cơ chế phát hiện xung đột nếu hai interface cung cấp default implementation cùng chữ ký mà class không có cách kế thừa không mơ hồ. Ví dụ một class triển khai `Left` và `Right`, cả hai có `default String choose()`, thì class phải giải quyết xung đột, thường bằng một override.

Tạo tệp **`DefaultConflictDemo.java`**:

~~~java
import java.lang.reflect.Method;

interface Left {
    default String choose() {
        return "L";
    }
}

interface Right {
    default String choose() {
        return "R";
    }
}

class Choice implements Left, Right {
    @Override
    public String choose() {
        return Left.super.choose() + "+" + Right.super.choose();
    }
}

public class DefaultConflictDemo {
    public static void main(String[] args) throws ReflectiveOperationException {
        Method leftMethod = Left.class.getDeclaredMethod("choose");
        Method implementation = Choice.class.getDeclaredMethod("choose");

        System.out.println("Interface method default: "
                + leftMethod.isDefault());
        System.out.println("Class override default: "
                + implementation.isDefault());
        System.out.println("Interface Method.invoke: "
                + leftMethod.invoke(new Choice()));
    }
}
~~~

~~~bash
javac --release 17 DefaultConflictDemo.java
java DefaultConflictDemo
~~~

**Stdout:**

~~~text
Interface method default: true
Class override default: false
Interface Method.invoke: L+R
~~~

Ở đây `Left.super.choose()` và `Right.super.choose()` là lời gọi **đặc biệt viết trực tiếp trong mã nguồn của `Choice`**. Trái lại, `leftMethod.invoke(new Choice())` không ép chạy riêng thân `Left#choose`: nó thực hiện dynamic dispatch tới `Choice#choose` và nhận `L+R`.

Nếu bỏ override trong `Choice`, chương trình nguồn sẽ không biên dịch vì xung đột default method. Reflection không thể "chọn tùy ý" một implementation để sửa lỗi thiết kế này sau khi biên dịch.

## 8. Abstract method trong interface, abstract class và kế thừa

Cần phân biệt các cấp:

- **Interface abstract method:** thường ngầm định `public abstract`. Class triển khai cụ thể cần cung cấp implementation nếu chưa có cách kế thừa implementation hợp lệ.
- **Abstract method trong abstract class:** có thể có mức truy cập `protected` hoặc package-private tùy quy tắc Java; `Modifier.isAbstract(...)` là `true`, `Method.isDefault()` luôn `false`.
- **Concrete method trong abstract class:** dù class chứa nó là `abstract`, bản thân method có thể không `abstract` và không phải default method.
- **Default method trong interface:** phương thức public có thân mã, được `isDefault()` nhận biết; class triển khai có thể kế thừa hoặc override.
- **Public concrete method trong class:** không phải interface default method dù class cung cấp implementation cho một chữ ký được khai báo ở interface.

Một method của superclass có thể đáp ứng interface abstract declaration của subclass. Vì vậy không nên suy luận rằng "mọi method khai báo trong interface phải có một method tương ứng do class triển khai tự khai báo". Hãy dùng `getDeclaringClass()` và phạm vi tìm của `getMethod/getDeclaredMethod` để trả lời đúng câu hỏi.

Ở Java source thông thường, method implementation trong class phải đáp ứng yêu cầu visibility của interface method; ví dụ implementation cho một interface `public` abstract method không được giảm visibility xuống `private`.

## 9. Phân biệt `Method.isDefault()` và `Method.getDefaultValue()`

Hai tên API dễ nhầm nhưng mục đích **hoàn toàn khác nhau**.

| API | Tác dụng | Trường hợp dùng |
| --- | --- | --- |
| `Method.isDefault()` | Kiểm tra method có phải **default method của interface** hay không | Phân tích hành vi interface, tìm phương thức có implementation mặc định |
| `Method.getDefaultValue()` | Lấy **giá trị mặc định của phần tử annotation** nếu được khai báo | Đọc metadata của `@interface`; có thể trả `null` |

Ví dụ annotation có default value:

~~~java
@interface Role {
    String level() default "viewer";
}
~~~

`Role.class.getDeclaredMethod("level").isDefault()` là `false`. Đây là annotation element method (abstract theo hợp đồng của annotation type), **không** phải interface default method có thân mã. `getDefaultValue()` trên `Method` ấy trả `"viewer"`.

Đừng suy luận `getDefaultValue() != null` thì `isDefault() == true`. Đối với annotation element mặc định, sự thật thường là **ngược lại**: có giá trị mặc định trong metadata annotation nhưng method không có thân cài đặt default của Java interface.

## 10. Kiểu tham số, generics và varargs khi tra cứu interface methods

Quy tắc chữ ký của `getDeclaredMethod` hay `getMethod` không thay đổi vì method nằm trong interface:

~~~java
interface Processor {
    void consume(String... values);
    void register(java.util.List<String> values);
}
~~~

Tra cứu chính xác (đoạn minh họa):

~~~java
Method consume = Processor.class.getDeclaredMethod(
        "consume", String[].class);
Method register = Processor.class.getDeclaredMethod(
        "register", java.util.List.class);
~~~

- `String...` được biểu diễn bằng `String[]` ở chữ ký bytecode/runtime; `consume.isVarArgs()` cho biết khai báo là varargs.
- `List<String>` chịu type erasure thành `List.class` khi tra cứu method. Metadata generic có thể đọc từ `getGenericParameterTypes()`.
- `int.class` khác `Integer.class`; tra cứu không áp dụng autoboxing hoặc widening để tự dò một method gần giống.
- Các phương thức compiler sinh như bridge method có thể hiện diện trong danh sách `getDeclaredMethods()`. Muốn phân tích khai báo từ mã nguồn, nên lọc và kiểm tra `isBridge()`/`isSynthetic()` thay vì xem mọi `Method` là khai báo thủ công.

## 11. Access control, Java modules và lỗi thường gặp

Reflection lấy được `Method` không đồng nghĩa chắc chắn gọi được `invoke(...)`. Khi phân tích:

1. Tìm đúng `Class<?>` khai báo method hoặc tra cứu public method kế thừa nếu đó là mục tiêu.
2. So khớp tên và **chữ ký runtime**; `NoSuchMethodException` có nghĩa method không được tìm thấy trong phạm vi API đang xét.
3. Đọc `getModifiers()`, `isDefault()` và `getDeclaringClass()` để phân loại.
4. Với `invoke(...)`, chuẩn bị receiver instance hoặc `null` cho method static.
5. Xem quyền truy cập; `trySetAccessible()` có thể cần thiết cho private method. Java Platform Module System có thể từ chối truy cập sâu vào package chưa mở cho caller ở module khác.
6. Khi method thực thi và ném ngoại lệ, `Method.invoke` bọc ngoại lệ đó trong `InvocationTargetException`; xem `getCause()` để biết nguyên nhân từ method gốc.

| Nhầm lẫn | Điều chỉnh |
| --- | --- |
| Thấy method có code là `default` | Chỉ interface public non-abstract instance method mới là `Method.isDefault() == true` |
| Dùng `Modifier.isAbstract` cho mọi method interface | Interface có abstract, default, static và private method |
| `getDeclaredMethods()` trên class triển khai phải chứa default method kế thừa | Không; đó là phạm vi trực tiếp của class, dùng `getMethods()` cho public method kế thừa |
| Interface static method được kế thừa như default method | Không; tra trên chính interface khai báo |
| `Method.invoke` với default method luôn ép chạy thân interface | Không; instance method theo dynamic dispatch và có thể chạy override |
| Phải dùng `trySetAccessible()` cho mọi method interface | Không; public method có thể được gọi bình thường khi đáp ứng quyền, chỉ deep access khi cần |
| Annotation element có `default "x"` là Java interface default method | Không; dùng `getDefaultValue()` cho annotation metadata |
| Thứ tự `getDeclaredMethods()` trùng thứ tự mã nguồn | Không bảo đảm; cần lọc/sắp xếp chủ động nếu muốn hiển thị ổn định |

### Lưu ý với dynamic proxy

Java dynamic proxy có cơ chế riêng: lời gọi interface method trên proxy được chuyển tới `InvocationHandler#invoke`. Từ Java 16, API `InvocationHandler.invokeDefault(proxy, method, args...)` có thể gọi trực tiếp default method của proxy interface trong những điều kiện phù hợp; nó khác với `Method.invoke(...)` thông thường. Chỉ sử dụng cơ chế proxy này khi thật sự đang xây `Proxy`, không xem nó là cách tổng quát để gọi mọi default method trên mọi object.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** `interface I { void run(); }`. `I.class.getDeclaredMethod("run").isDefault()` có trả `true` không?

**Đáp án:** Không. `run()` là abstract instance method, `Modifier.isAbstract(...) == true` và `isDefault() == false`.

**Câu 2.** `interface I { default void run() {} }`, class `C implements I` không override. `C.class.getDeclaredMethod("run")` có tìm thấy method không?

**Đáp án:** Không. `run()` do `I` khai báo. `C.class.getMethod("run")` mới tìm được public method được kế thừa.

**Câu 3.** `I.class.getDeclaredMethod("run")` cho default method; receiver là object của lớp `C` override `run()`. `Method.invoke(...)` thực thi cái nào?

**Đáp án:** Override của `C` theo dynamic dispatch. `Method.isDefault()` trên method phản chiếu từ `I` vẫn là `true`.

**Câu 4.** Tại sao static method `I.make()` không tìm được bằng `C.class.getMethod("make")`, dù `C implements I`?

**Đáp án:** Static method của interface không được class triển khai kế thừa. Tra trên `I.class` và dùng receiver `null` khi `invoke`.

**Câu 5.** Interface có `private String helper()`. `helper.isDefault()` là gì?

**Đáp án:** `false`. Private method có thể có thân mã nhưng không phải public interface default method; truy cập từ caller khác có thể cần bật quyền Reflection hợp lệ.

**Câu 6.** `@interface Role { String level() default "viewer"; }`. Vì sao `isDefault()` và `getDefaultValue()` khác nhau?

**Đáp án:** `isDefault()` là phép thử cho **interface default method có body**; `getDefaultValue()` trả metadata `"viewer"` của annotation element. Hai khái niệm "default" khác nhau.

**Câu 7.** Làm sao nhận biết một method lấy từ `Class#getMethods()` được khai báo ở interface?

**Đáp án:** Kiểm tra `method.getDeclaringClass().isInterface()`. Sau đó tiếp tục xem `isDefault()`, `Modifier.isAbstract(...)`, `Modifier.isStatic(...)`; riêng việc khai báo trên interface chưa kết luận được loại method.

## Tài liệu chuẩn để kiểm tra

- [JDK 21 — java.lang.reflect.Method](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Method.html): `isDefault()`, `invoke(...)`, `getDefaultValue()` và metadata.
- [JDK 21 — java.lang.Class](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html): `getMethod(s)`, `getDeclaredMethod(s)` và phạm vi kế thừa.
- [JDK 21 — InvocationHandler](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/InvocationHandler.html): `invokeDefault(...)` đối với dynamic proxy.

**Ghi nhớ:** `Modifier.isAbstract(method.getModifiers())` kiểm tra method có abstract không; `method.isDefault()` kiểm tra method có phải **public default instance method của interface** không. `getDeclaringClass()` cho biết method được khai báo ở đâu; `getMethod` và `getDeclaredMethod` có phạm vi khác nhau. Cuối cùng, `Method.invoke` trên instance method tuân theo dynamic dispatch, nên Method phản chiếu từ interface **không đảm bảo** sẽ chạy phần thân default của interface khi receiver override method ấy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="methods-011">Method.invoke không tự chờ Future hoặc CompletionStage trả về</a>

<details>
<summary>Xem chi tiết</summary>

# `Method.invoke` không tự chờ `Future` hoặc `CompletionStage` trả về

## 1. Bản chất của `Method.invoke` khi phương thức trả kết quả bất đồng bộ

`java.lang.reflect.Method.invoke(Object receiver, Object... args)` **gọi phương thức đích và chờ chính lời gọi phương thức đó trả về hoặc ném lỗi**. Nếu phương thức được gọi trả về `Future<T>`, `CompletionStage<T>` hoặc `CompletableFuture<T>`, giá trị trả về của `invoke` là **đối tượng Future/Stage** (dưới kiểu `Object`). `invoke` không tự gọi `get()`, `join()`, chờ completion, hay tự tách giá trị `T` bên trong.

Hãy phân biệt ba mốc:

1. **Invocation:** Reflection thực hiện kiểm tra quyền truy cập, nhận tham số, chạy thân phương thức Java.
2. **Return:** thân phương thức trả về một reference tới `Future`/`CompletionStage`. Lời gọi `Method.invoke` kết thúc ngay khi có giá trị đó, dù tác vụ đại diện bởi nó chưa xong.
3. **Completion:** tác vụ hoàn tất thành công, thất bại hoặc bị hủy sau đó; người gọi quyết định quan sát hoặc đợi kết quả bằng API async thích hợp.

Ví dụ minh họa đặt bên trong một method có `service` và `Service` hợp lệ:

```java
Method m = Service.class.getMethod("loadAsync");
Object returned = m.invoke(service); // Có thể trả về ngay một CompletionStage chưa xong
CompletionStage<?> stage = (CompletionStage<?>) returned;
Object value = stage.toCompletableFuture().join(); // Chủ động chờ nếu chưa hoàn tất
```

**Lưu ý quan trọng:** Reflection không tự tạo thread và không làm phương thức đồng bộ trở thành async. Nếu **chính thân phương thức đích** thực hiện `Thread.sleep`, truy vấn I/O chặn, hoặc `future.get()` **trước khi return**, thread thực thi `invoke` vẫn phải chờ thân phương thức đó kết thúc. Ngược lại, phương thức có thể trả một `CompletableFuture.completedFuture(value)` đã hoàn tất sẵn, nên đọc kết quả về sau không cần đợi. Việc đối tượng async đã hoàn tất hay chưa phụ thuộc vào logic của phương thức đích, không phụ thuộc Reflection.

Giá trị `Method.invoke` luôn có kiểu khai báo `Object`: Java sẽ boxing kết quả primitive; phương thức `void` cho kết quả `null`. Nếu phương thức chỉ khởi động tác vụ nền rồi return `void`, Reflection cũng chỉ nhận `null` và **không có Future nào để chờ** thông qua giá trị trả về.

## 2. Ba kiểu async thường gặp

| Kiểu | Đặc điểm | Nếu cần chờ | Nếu cần nối xử lý |
| --- | --- | --- | --- |
| `Future<T>` | Tập trung vào kết quả, `isDone`, `get`, `cancel` | `future.get()` hoặc `get(timeout, unit)` | Giao diện `Future` không định nghĩa callback composition |
| `CompletionStage<T>` | Biểu diễn giai đoạn và quan hệ phụ thuộc giữa các giai đoạn | `stage.toCompletableFuture().get()` hoặc `join()` | `thenApply`, `thenCompose`, `thenAccept`, `whenComplete`, `exceptionally`... |
| `CompletableFuture<T>` | Triển khai **cả** `Future<T>` và `CompletionStage<T>` | `get` hoặc `join` | Có đầy đủ các API composition |

**`Future` và `CompletionStage` không kế thừa lẫn nhau.** Ví dụ, kết quả của `ExecutorService.submit(...)` chỉ được bảo đảm có giao diện `Future`, không nhất thiết là `CompletionStage`. Một triển khai `CompletionStage` tùy chỉnh cũng không nhất thiết triển khai `Future`. `CompletableFuture` là trường hợp hỗ trợ cả hai; không nên ép một `Future<?>` bất kỳ thành `CompletableFuture<?>`.

`Future.isDone() == true` **không có nghĩa tác vụ thành công**: nó cũng trả `true` khi bị hủy hoặc hoàn tất thất bại. Phải quan sát kết quả, lỗi hoặc trạng thái hủy theo API cụ thể.

## 3. Ví dụ đầy đủ Java 17+: `invoke` không chờ completion

Lưu toàn bộ chương trình sau trong tệp **`ReflectAsyncDemo.java`**, không khai báo `package`.

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;

public class ReflectAsyncDemo {
    private static final CompletableFuture<String> pendingFuture =
            new CompletableFuture<>();
    private static final CompletableFuture<String> pendingStage =
            new CompletableFuture<>();

    public static Future<String> findLater() {
        return pendingFuture;
    }

    public static CompletionStage<String> loadLater() {
        return pendingStage;
    }

    public static CompletionStage<String> failedLater() {
        return CompletableFuture.failedFuture(
                new IllegalStateException("async-failure"));
    }

    public static CompletionStage<String> failBeforeReturn() {
        throw new IllegalArgumentException("sync-failure");
    }

    public static void main(String[] args) throws Exception {
        Method find = ReflectAsyncDemo.class.getMethod("findLater");
        Method load = ReflectAsyncDemo.class.getMethod("loadLater");

        Object rawFuture = find.invoke(null);
        Object rawStage = load.invoke(null);

        System.out.println("Future result: " + (rawFuture instanceof Future<?>));
        System.out.println("Stage result: "
                + (rawStage instanceof CompletionStage<?>));

        Future<?> future = (Future<?>) rawFuture;
        CompletionStage<?> stage = (CompletionStage<?>) rawStage;
        System.out.println("Future done before completion: " + future.isDone());
        System.out.println("Stage done before completion: "
                + stage.toCompletableFuture().isDone());

        pendingFuture.complete("future-ok");
        pendingStage.complete("stage-ok");

        System.out.println("Future get: " + future.get());
        System.out.println("Stage join: " + stage.toCompletableFuture().join());

        CompletionStage<?> failed = (CompletionStage<?>)
                ReflectAsyncDemo.class.getMethod("failedLater").invoke(null);
        System.out.println("Invoke returned failed stage: " + (failed != null));
        try {
            failed.toCompletableFuture().join();
        } catch (CompletionException ex) {
            System.out.println("Async cause: "
                    + ex.getCause().getClass().getSimpleName());
        }

        try {
            ReflectAsyncDemo.class.getMethod("failBeforeReturn").invoke(null);
        } catch (InvocationTargetException ex) {
            System.out.println("Sync cause: "
                    + ex.getCause().getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy bằng JDK 17+:

```bash
javac ReflectAsyncDemo.java
java ReflectAsyncDemo
```

Kết quả xác định:

```text
Future result: true
Stage result: true
Future done before completion: false
Stage done before completion: false
Future get: future-ok
Stage join: stage-ok
Invoke returned failed stage: true
Async cause: IllegalStateException
Sync cause: IllegalArgumentException
```

**Tại sao kết quả này chứng minh `invoke` không tự chờ?** Hai `CompletableFuture` được khởi tạo ở trạng thái **chưa hoàn tất**, và **chỉ có lệnh `complete(...)` ở phần dưới mới hoàn tất chúng**. Sau khi chạy `find.invoke(null)` và `load.invoke(null)`, chương trình vẫn đi tiếp tới `isDone()` và nhận cả hai là `false`. Nếu `Method.invoke` tự chờ completion, chương trình sẽ không thể đi tới những lệnh `complete` nằm ở phía sau trong cùng thread.

Hai lời gọi `get`/`join` diễn ra **sau** `complete`, vì vậy chúng đọc kết quả ngay. Nếu chuyển `join` lên trước `complete` cùng thread, chương trình có thể tự chờ vô hạn.

`find.invoke(null)` và `load.invoke(null)` dùng receiver `null` vì method là `static`. Đối với method instance, phải truyền instance của declaring class hoặc kiểu tương thích.

Chương trình cũng cố ý tách hai tình huống lỗi: method `failedLater` **trả về stage thành công xét về lời gọi trực tiếp**, nhưng stage đã hoàn tất ngoại lệ; method `failBeforeReturn` ném lỗi **trước khi trả giá trị**. Hai tình huống có đường chuyển lỗi khác nhau, được trình bày ở phần tiếp theo.

## 4. `InvocationTargetException` khác lỗi completion

### 4.1. Ngoại lệ ngay lúc gọi method qua reflection

Nếu code **bên trong phương thức đích** ném lỗi trước khi return, `Method.invoke` bọc ngoại lệ đó trong `java.lang.reflect.InvocationTargetException`. Đây là cơ chế của Reflection, không phụ thuộc phương thức khai báo trả `Future` hay không.

```java
// Mảnh minh họa trong method, với method/receiver đã được định nghĩa.
try {
    Object result = method.invoke(receiver);
} catch (InvocationTargetException ex) {
    Throwable original = ex.getCause();
    System.err.println("Method itself failed: " + original);
}
```

Ví dụ, nếu thân method tự gọi `future.get()` và nhận `ExecutionException` **trước khi return**, `Method.invoke` cũng có thể bao bọc `ExecutionException` trong `InvocationTargetException` vì đó là ngoại lệ thoát trực tiếp từ thân method.

Các lỗi gọi khác gồm `IllegalAccessException` (không đủ quyền truy cập method) hoặc `IllegalArgumentException` (receiver/đối số không phù hợp); không nên nhầm chúng với trạng thái của async result.

### 4.2. Ngoại lệ xảy ra khi Future/Stage hoàn tất

Nếu method trả về một `Future` hoặc `CompletionStage` bình thường, rồi công việc đại diện bởi đối tượng đó thất bại, **`Method.invoke` đã kết thúc**. Nó không thể bọc một lỗi phát sinh về sau vào một `InvocationTargetException` mới. Khi người gọi lấy completion, các API async phản ánh trạng thái tương ứng:

| Cách quan sát | Thành công | Thất bại | Bị hủy |
| --- | --- | --- | --- |
| `Future.get()` | Trả giá trị; có thể chờ | `ExecutionException` chứa cause | `CancellationException` |
| `Future.get(timeout, unit)` | Trả giá trị nếu kịp hạn | `ExecutionException` chứa cause | `CancellationException`; quá thời gian có `TimeoutException` |
| `CompletableFuture.join()` | Trả giá trị; có thể chờ | Thường ném `CompletionException` có cause | `CancellationException` |
| `CompletionStage.whenComplete(...)` | Nhận value | Nhận throwable để xử lý | Có thể nhận tình huống bị hủy như exceptional completion tùy stage |

`Future.get` khai báo `InterruptedException` và `ExecutionException` (thêm `TimeoutException` ở biến thể timeout). `CompletableFuture.join` không khai báo checked exceptions, thường dùng `CompletionException`/`CancellationException`. Nếu cần phục hồi tốt với việc ngắt thread, `get` phù hợp hơn để xử lý `InterruptedException` có chủ đích. Khi bắt `InterruptedException` mà không ném tiếp, thông thường nên phục hồi interrupt flag bằng `Thread.currentThread().interrupt()`.

**Hai tầng lỗi phải được theo dõi độc lập:** Reflection invocation có thể thành công nhưng Future/Stage thất bại; hoặc invocation thất bại trước cả khi có Future/Stage để theo dõi.

## 5. Chọn xử lý bất đồng bộ hay chờ có chủ đích

### 5.1. Tiếp tục pipeline không chặn bằng `CompletionStage`

Khi ứng dụng không cần kết quả ngay trong thread hiện tại, có thể giữ đối tượng stage và đăng ký xử lý:

```java
// Mảnh minh họa trong method; Service/service do ứng dụng định nghĩa.
Method method = Service.class.getMethod("loadAsync");
Object returned = method.invoke(service);

if (returned instanceof CompletionStage<?> stage) {
    stage.whenComplete((value, failure) -> {
        if (failure != null) {
            System.err.println("Async failed: " + failure);
        } else {
            System.out.println("Async value: " + value);
        }
    });
}
```

`whenComplete` đăng ký một action thực hiện khi stage hoàn tất. Nếu stage **đã hoàn tất**, callback không có hậu tố `Async` có thể chạy ngay trên thread gọi đăng ký; nếu stage hoàn tất sau, callback có thể chạy trên thread đang hoàn tất stage. Vì vậy “không gọi `get/join`” không có nghĩa mọi callback chắc chắn chạy trên thread khác. Khi cần điều khiển nơi callback chạy, cân nhắc `whenCompleteAsync(..., executor)` hoặc những API `*Async` tương ứng.

Nếu có nhiều bước phụ thuộc nhau, dùng:

- `thenApply` để biến đổi `T` thành `U`.
- `thenCompose` khi hàm biến đổi tự trả `CompletionStage<U>` và cần ghép một pipeline phẳng.
- `thenAccept` nếu chỉ tiêu thụ kết quả mà không trả giá trị mới.
- `exceptionally`, `handle` hoặc `whenComplete` để xử lý/quan sát completion bất thường, tùy semantics mong muốn.

Không vứt bỏ stage nếu caller cần nhận kết quả, xử lý lỗi hoặc thực hiện cancellation. `Method.invoke` không quản lý vòng đời async result hộ ứng dụng.

### 5.2. Chờ trong thread hiện tại khi nghiệp vụ bắt buộc

Có thể dùng hai nhánh theo **kiểu của đối tượng thực tế**:

```java
// Mảnh minh họa: cần các import java.util.concurrent.*;
// xử lý ReflectiveOperationException, InterruptedException,
// ExecutionException và TimeoutException theo hợp đồng method.
Object returned = method.invoke(receiver);

if (returned instanceof CompletionStage<?> stage) {
    Object value = stage.toCompletableFuture().get(3, TimeUnit.SECONDS);
    System.out.println(value);
} else if (returned instanceof Future<?> future) {
    Object value = future.get(3, TimeUnit.SECONDS);
    System.out.println(value);
} else {
    System.out.println(returned);
}
```

Với `CompletionStage`, giao diện không khai báo `get` hay `join`; gọi `toCompletableFuture()` để có một `CompletableFuture` theo hợp đồng completion. Khi cần giới hạn thời gian, `get(timeout, unit)` có thể báo `TimeoutException`. Timeout ở phía **đang chờ** không tự bảo đảm tác vụ nền đã dừng; muốn hủy cần dùng cơ chế cancellation phù hợp và hiểu semantics của implementation.

Kiểm tra `CompletionStage` trước `Future` là cách chọn nhánh rõ ràng vì một `CompletableFuture` triển khai **cả hai**. Việc chờ có thể block thread: tránh `get/join` trực tiếp trên UI thread, event-loop hoặc executor có số thread hạn chế nếu tác vụ cần chính thread đó để hoàn tất.

### 5.3. Khi nào `isDone` hữu ích?

`isDone` có thể dùng để **kiểm tra nhanh** trạng thái của `Future` nhưng không chờ. Tuy nhiên, `isDone == false` không nói tác vụ “vẫn đang chạy” hay “chưa được lên lịch”; `isDone == true` không bảo đảm thành công. Nếu ứng dụng cần kết quả có độ tin cậy, vẫn cần lấy completion hoặc đăng ký callback. Tránh kiểm tra `isDone` lặp liên tục trong vòng bận (busy spin) gây lãng phí CPU.

## 6. Một adapter rõ chính sách: `invokeRaw` và `invokeAndWait`

Khi xây dựng bộ gọi động (plugin dispatcher, test runner, serializer adapter), nên thể hiện rõ hợp đồng chờ của caller. Đoạn sau là **class hợp lệ có thể biên dịch** nếu lưu vào tệp `InvocationAdapter.java`:

```java
import java.lang.reflect.Method;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public final class InvocationAdapter {
    public static Object invokeRaw(Method method, Object receiver,
                                   Object... args)
            throws ReflectiveOperationException {
        return method.invoke(receiver, args);
    }

    public static Object invokeAndWait(Method method, Object receiver,
                                       Object... args)
            throws ReflectiveOperationException, InterruptedException,
                   ExecutionException {
        Object returned = method.invoke(receiver, args);

        if (returned instanceof CompletionStage<?> stage) {
            return stage.toCompletableFuture().get();
        }
        if (returned instanceof Future<?> future) {
            return future.get();
        }
        return returned;
    }
}
```

**Ý nghĩa:**

- `invokeRaw` giữ nguyên semantics của `Method.invoke`: trả thẳng kết quả method, kể cả đối tượng async chưa hoàn tất.
- `invokeAndWait` là **hành vi do ứng dụng tự cài đặt**, lấy completion nếu kết quả runtime triển khai `CompletionStage` hoặc `Future`. Method thông thường thì trả giá trị như cũ.
- `method.invoke(...)` có thể ném `InvocationTargetException` (kế thừa `ReflectiveOperationException`) khi phương thức đích ném trực tiếp. `get` có thể ném `ExecutionException`, `InterruptedException`; đây là hai giai đoạn lỗi khác nhau.
- Đoạn đơn giản không đặt timeout. API production nên quy định **timeout/deadline, interruption, cancellation và nơi thực thi**. Không tự động biến mọi đường gọi Reflection thành thao tác chờ không giới hạn.

Một điểm thiết kế: method có thể khai báo return type là `Object` nhưng thực tế trả một `CompletionStage`. `instanceof` trên **giá trị runtime** sẽ nhận diện được trường hợp này; không nên chỉ dựa vào metadata chữ ký để quyết định “đợi hay không”.

## 7. Vai trò của `Method.getReturnType()` và generic return type

Reflection cho phép xem **kiểu trả về đã khai báo**, ví dụ:

```java
Class<?> declared = method.getReturnType();
boolean returnsStageType =
        CompletionStage.class.isAssignableFrom(declared);
boolean returnsFutureType =
        Future.class.isAssignableFrom(declared);
```

Đây là mảnh minh họa đặt trong method có biến `method` hợp lệ. Hai kiểm tra này trả lời: chữ ký **có khai báo kiểu thuộc họ Stage/Future hay không?** Chúng không cho biết runtime result đã hoàn tất, thành công hay lỗi. Nếu method khai báo `Object` nhưng trả `CompletableFuture`, `getReturnType()` vẫn là `Object.class`; sau khi gọi nên kiểm tra chính reference bằng `instanceof`.

`getGenericReturnType()` có thể cung cấp `Type` của chữ ký, ví dụ `CompletionStage<String>` khi metadata generic được giữ lại, nhưng **không biến `invoke` thành lời gọi có khả năng đợi completion**. Generic type không chứa trạng thái runtime của tác vụ.

Những trường hợp biên cần nhớ:

- `void method()` có thể khởi động một tác vụ nền nhưng `invoke` trả `null`; không thể suy ra Future từ giá trị này.
- `Future<T>` có thể đã completed trước khi `invoke` kết thúc: `get` khi ấy trả ngay.
- Một method trả `Future<T>` nhưng **chờ Future bên trong rồi mới return** vẫn khiến `invoke` chờ thân method; đó không phải khả năng “tự đợi Future” của Reflection.
- Method instance cần đúng receiver; static method có thể truyền receiver là `null`. Sai receiver/đối số gây lỗi gọi đồng bộ.
- Nếu method được gọi ném lỗi trực tiếp trước khi trả Future, caller chỉ có `InvocationTargetException`; không có async result mới từ lần gọi đó.

## 8. Sai lầm phổ biến

| Sai lầm | Vì sao sai | Cách xử lý |
| --- | --- | --- |
| `String s = (String) method.invoke(target)` khi method trả `Future<String>` | Giá trị Reflection nhận là **Future**, không phải `String` bên trong | Nhận `Future<?>`/`CompletionStage<?>` rồi giải quyết kết quả theo hợp đồng |
| Nghĩ `invoke` tự chờ cho đến khi Future xong | `invoke` chỉ chờ **thân method return** | Đợi bằng `get/join` nếu bắt buộc hoặc nối pipeline |
| Nghĩ `Method.invoke` luôn trả ngay | Method đích có thể tự block trước khi return | Phân tích thân method và nơi xảy ra chờ |
| Nhầm `InvocationTargetException` với lỗi bất đồng bộ | Lỗi completion xuất hiện sau khi method return và qua đối tượng async | Theo dõi cả hai tầng lỗi |
| Ép mọi `Future` thành `CompletableFuture` | Hai kiểu không tương đương; implement khác có thể không kế thừa CF | Kiểm tra runtime types, xử lý từng hợp đồng |
| Gọi `join` trên event-loop rồi cần event-loop đó hoàn tất Future | Có thể deadlock | Dùng composition hoặc executor riêng, tránh blocking cùng luồng |
| Thấy `isDone() == true` và tin rằng tác vụ thành công | Done gồm cả thất bại/hủy | Dùng `get/join` hoặc error callback để phân loại |
| Bỏ qua giá trị trả về async | Không còn đường theo dõi lỗi hoặc kết quả | Trả về stage, đăng ký callback, hoặc quản lý task lifecycle rõ ràng |

### Minh họa một deadlock đơn giản

```java
CompletableFuture<String> pending = new CompletableFuture<>();
System.out.println(pending.join()); // Chờ đến khi pending được hoàn tất
pending.complete("done");          // Không thể tới được trên cùng thread
```

Đây là **mảnh mã minh họa**, không phải chương trình độc lập. Nếu không có thread khác thực hiện `complete`, `join` chờ mãi và dòng bên dưới không được chạy. Nếu một framework đặt `get/join` trong callback trên chính thread/event-loop cần chạy công việc hoàn tất, vấn đề tương tự có thể xuất hiện.

## 9. Câu hỏi tự kiểm tra

1. `Method.invoke` gọi method trả `Future<String>` chưa hoàn tất. Reference trả về có kiểu runtime gì và `(String) invoke(...)` sai ở đâu?
2. Tại sao `invoke` vẫn có thể block lâu dù method trả `CompletableFuture`?
3. Method A ném `IllegalStateException` trước `return`. Method B trả stage rồi stage hoàn tất lỗi `IllegalStateException`. Hai đường báo lỗi tới caller khác nhau ra sao?
4. So sánh `future.get()` với `stage.thenApply(...)` về hành vi chờ và thời điểm callback chạy.
5. `CompletableFuture` có thể phù hợp với cả nhánh `Future` và `CompletionStage` trong `instanceof` hay không? Nên ưu tiên nhánh nào khi muốn composition?
6. Method khai báo kiểu trả `Object` nhưng runtime trả `CompletionStage<?>`. Vì sao chỉ xem `getReturnType()` chưa đủ?
7. Một method trả `void` nhưng bắt đầu tác vụ nền. Làm thế nào thiết kế API để caller có thể chờ hoặc theo dõi kết quả?
8. Vì sao nên đặt timeout và quy định interruption/cancellation nếu ứng dụng quyết định đợi completion?

**Ghi nhớ:** `Method.invoke` đồng bộ hóa **lời gọi method trực tiếp**, còn trạng thái hoàn tất của `Future`/`CompletionStage` là **một hợp đồng độc lập**. Kết quả bất đồng bộ chỉ được chờ/quan sát khi caller chủ động thực hiện `get`, `join` hoặc đăng ký composition phù hợp. Luôn phân biệt lỗi của method khi đang chạy qua Reflection (`InvocationTargetException`) với lỗi của tác vụ trả về (`ExecutionException`, `CompletionException`, `CancellationException`).

## 10. Tham khảo chính thức

- [Java SE 17 — `Method.invoke`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html#invoke(java.lang.Object,java.lang.Object...)): giá trị trả về và ngoại lệ do method được gọi ném ra.
- [Java SE 17 — `Future`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/Future.html): `get`, timeout, cancellation, `isDone`.
- [Java SE 17 — `CompletionStage`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletionStage.html): phối hợp các giai đoạn và xử lý completion.
- [Java SE 17 — `CompletableFuture`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html): `get`, `join`, xử lý lỗi và triển khai cả `Future`/`CompletionStage`.
- [Java SE 17 — `InvocationTargetException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationTargetException.html): lỗi của thân method qua Reflection.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="methods-012">Không nhầm Method object với kết quả gọi phương thức</a>

<details>
<summary>Xem chi tiết</summary>

# Không nhầm `Method` object với kết quả gọi phương thức trong Java Reflection

## 1. Hai đối tượng khác nhau, hai vai trò khác nhau

Trong Java Reflection, `java.lang.reflect.Method` là **đối tượng mô tả một phương thức**: tên, kiểu trả về khai báo, danh sách kiểu tham số, modifier, annotation, class khai báo phương thức và các đặc tính liên quan. Bạn có thể lấy `Method` từ `Class.getMethod(...)` hoặc `Class.getDeclaredMethod(...)` mà **chưa hề chạy phần thân phương thức**.

Khi muốn thực sự thực thi phương thức mà `Method` mô tả, gọi:

~~~java
Object result = method.invoke(receiver, arguments);
~~~

`Method.invoke(...)` dùng đối tượng `Method` để thực hiện lời gọi, rồi trả về **giá trị mà phương thức đích trả về** dưới dạng `Object`. Nếu phương thức đích trả `void`, giá trị mà `invoke` trả về là `null`. Nếu phương thức đích trả primitive như `int`, Reflection sẽ boxing giá trị thành wrapper như `Integer`. Nếu phương thức đích vốn trả `null`, `invoke` cũng trả `null`.

Cần phân biệt:

| Biểu thức / biến | Nó đại diện cho cái gì? | Có thực thi phương thức đích chưa? |
| --- | --- | --- |
| `Method method = type.getMethod("run");` | Metadata cho phương thức `run()` | Chưa. |
| `method.getName()` | Tên phương thức dưới dạng `String` | Chưa. |
| `method.getReturnType()` | Đối tượng `Class<?>` cho kiểu trả về được khai báo | Chưa. |
| `Object value = method.invoke(target);` | Giá trị trả về từ một lần chạy `run()` | Rồi. |
| `Method another = method;` | Tham chiếu tới cùng đối tượng mô tả phương thức | Chưa chạy thêm lần nào. |

**Điểm mấu chốt:** `method` là **đối tượng công cụ dùng để gọi**; `value` là **kết quả của một lần gọi**. Hai giá trị chỉ có thể cùng kiểu `Method` trong trường hợp đặc biệt là **chính phương thức đích có kiểu trả về `Method`**. Không có quy tắc chung rằng `invoke()` trả về một `Method` khác.

## 2. Ví dụ Java 17+ chạy độc lập

Tạo file `MethodObjectDemo.java`:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class MethodObjectDemo {
    public static class Counter {
        private int value;

        public Counter(int initial) {
            this.value = initial;
        }

        public int add(int delta) {
            value += delta;
            return value;
        }

        public int current() {
            return value;
        }

        public void reset() {
            value = 0;
        }

        public static String label() {
            return "counter";
        }

        public int fail() {
            throw new IllegalStateException("boom");
        }
    }

    public static void main(String[] args) throws Exception {
        Counter counter = new Counter(10);
        Method add = Counter.class.getMethod("add", int.class);

        System.out.println("method name: " + add.getName());
        System.out.println("return type: " + add.getReturnType().getSimpleName());
        System.out.println("after lookup: " + counter.current());

        Object firstResult = add.invoke(counter, 5);
        System.out.println("result: " + firstResult);
        System.out.println("result type: " + firstResult.getClass().getSimpleName());
        System.out.println("counter value: " + counter.current());

        Method reset = Counter.class.getMethod("reset");
        Object voidResult = reset.invoke(counter);
        System.out.println("void result is null: " + (voidResult == null));
        System.out.println("after reset: " + counter.current());

        Method label = Counter.class.getMethod("label");
        System.out.println("static result: " + label.invoke(null));

        Method fail = Counter.class.getMethod("fail");
        try {
            fail.invoke(counter);
        } catch (InvocationTargetException ex) {
            System.out.println("cause: " + ex.getCause().getClass().getSimpleName());
        }
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 MethodObjectDemo.java
java MethodObjectDemo
~~~

Stdout đã xác nhận khi biên dịch ở mức Java 17 và chạy trên JDK 24:

~~~text
method name: add
return type: int
after lookup: 10
result: 15
result type: Integer
counter value: 15
void result is null: true
after reset: 0
static result: counter
cause: IllegalStateException
~~~

Giải thích các mốc:

1. `Counter.class.getMethod("add", int.class)` **chỉ tìm Method** cho tên `add` và đúng một tham số kiểu nguyên thủy `int`. Nó không cộng số nào; `counter.current()` vẫn là `10`. `int.class` trong truy vấn là kiểu tham số, không phải dữ liệu truyền vào.
2. `add.getName()` và `add.getReturnType()` đọc **metadata**. Vì phương thức nguồn khai báo `int add(int)`, `getReturnType()` là `int.class`. Chúng không chạy `add`.
3. `add.invoke(counter, 5)` **thực thi** `counter.add(5)`. Biến `firstResult` nhận giá trị `15` được boxing thành `Integer`, nhưng kiểu compile-time của biến vẫn là `Object`. Đồng thời trạng thái `counter.value` thay đổi từ `10` thành `15`.
4. `reset.invoke(counter)` gọi `void reset()`. Trạng thái được đặt về `0`, còn `voidResult` nhận `null`. Không có “đối tượng void” nào được trả ra.
5. `label.invoke(null)` dùng `null` làm receiver vì `label()` là `static`. Kết quả của lời gọi là `String "counter"`.
6. Phương thức `fail()` ném `IllegalStateException`; `Method.invoke()` bọc ngoại lệ phát sinh từ **phần thân method đích** trong `InvocationTargetException`. Gọi `getCause()` để truy ra ngoại lệ thực.

## 3. Ba thời điểm cần nhận diện trong quá trình Reflection

Có thể hình dung đường đi:

~~~text
Class<?> type = Counter.class
          |
          | getMethod("add", int.class)  [tra cứu metadata]
          v
Method add                        [chưa gọi add()]
          |
          | getName()/getReturnType()  [đọc metadata]
          |
          | invoke(counter, 5)       [thực thi add(5)]
          v
Object firstResult                 [Integer(15), kết quả lần gọi]
          |
          | ép kiểu/chuyển kiểu theo contract
          v
int value = (Integer) firstResult  [giá trị int 15]
~~~

Đường đi này không làm `Method` biến thành `Integer`: biến `add` vẫn đại diện cho **cùng metadata phương thức** trước và sau `invoke()`; chỉ có thêm đối tượng giá trị `firstResult` và các tác động phụ từ phương thức nguồn.

Một `Method` có thể được dùng để gọi **nhiều lần**, trên **nhiều instance tương thích**:

~~~java
Method method = Counter.class.getMethod("add", int.class);

Counter a = new Counter(1);
Counter b = new Counter(100);

Object resultA = method.invoke(a, 2); // Integer(3)
Object resultB = method.invoke(b, 2); // Integer(102)
~~~

Đây là đoạn bổ sung sử dụng class `Counter` đã định nghĩa ở phần 2, **không phải file Java độc lập**. Hai kết quả khác nhau vì receiver khác nhau; bản thân `method` không chứa trạng thái `value` của `a` hay `b`.

## 4. Vì sao `invoke()` trả `Object` và cách dùng đúng kiểu?

Chữ ký tổng quát:

~~~java
public Object invoke(Object obj, Object... args)
        throws IllegalAccessException, InvocationTargetException
~~~

- `obj` là **receiver** của một phương thức instance; phải là object có kiểu tương thích với class khai báo method. Với phương thức `static`, receiver bị bỏ qua, thường truyền `null`.
- `args` là các **giá trị tham số khi gọi**, theo đúng thứ tự và số lượng cần thiết. Không nhầm `int.class` (metadata tra cứu) với `5` (đối số thực thi).
- Kiểu khai báo của `Method.invoke()` là `Object` vì nó có thể gọi những phương thức có kiểu trả về rất khác nhau. Giá trị trả về thực được boxing nếu là primitive; với `void` thì `null`.
- Việc ép kiểu phải phù hợp với **giá trị runtime và hợp đồng của method đích**. Một method trả `String` cần ép về `String` nếu muốn dùng API của `String`; method trả `int` thường được nhận qua `Integer` khi invoke.

Ví dụ bổ sung sau dùng `Counter` và `add` trong phần 2:

~~~java
Object raw = add.invoke(counter, 7);
int number = (Integer) raw;
System.out.println(number);
~~~

Chú ý kết quả cuối cùng phụ thuộc giá trị hiện có của `counter` khi chạy đoạn này. Đây là đoạn minh họa **không có stdout cố định** trừ khi biết trạng thái trước đó; nó không phải một file standalone. Việc cast `(Integer)` phù hợp vì `add` trả primitive `int`.

**Phân biệt kiểu khai báo và kiểu của kết quả:**

~~~java
Class<?> declaredReturnType = add.getReturnType(); // int.class
Object actualReturnValue = add.invoke(counter, 1); // boxed Integer, ví dụ 1 lần gọi
Class<?> actualValueType = actualReturnValue.getClass(); // Integer.class
~~~

`declaredReturnType` có kiểu `Class<?>` và mô tả signature; `actualReturnValue` chứa dữ liệu của **một lần chạy**. Nếu method khai báo trả `Number` nhưng runtime trả `Integer`, `getReturnType()` vẫn là `Number.class`. Nếu method trả `null`, không thể gọi `getClass()` trực tiếp trên kết quả.

## 5. Từ metadata tới kết quả: chọn đúng thao tác

| Câu hỏi | API / giá trị cần dùng |
| --- | --- |
| Phương thức nào tồn tại? | `Class.getMethod(name, parameterTypes...)` cho public, kể cả inherited; `getDeclaredMethod(...)` cho khai báo trực tiếp |
| Phương thức trả kiểu gì theo chữ ký? | `method.getReturnType()` trả `Class<?>`; không chạy thân phương thức |
| Muốn thực thi và lấy kết quả? | `Object result = method.invoke(receiver, args...)` |
| Vì sao `result == null`? | Xét `getReturnType() == void.class` hoặc phương thức tham chiếu thực sự trả `null` |

**Tra cứu khác thực thi:** khi tìm overload, dùng chính xác tham số formal như `int.class`, `String[].class`; khi gọi, truyền **giá trị** như `5`, `new String[]{...}`. `invoke` không chọn lại overload. Instance method cần receiver tương thích, static method có thể dùng `null`. Nếu target nhận một mảng, truyền thành **một** đối số, ví dụ `method.invoke(receiver, (Object) names)`.

**Kết quả khác metadata:** với `int add(int)`, `getReturnType()` vẫn là `int.class` nhưng `invoke` trả `Integer`. Với `void reset()`, `invoke` trả `null` dù reset đã thay đổi trạng thái; method tham chiếu cũng có thể trả `null`.

**Lỗi khác kết quả:** không tìm được chữ ký → `NoSuchMethodException`; không được phép gọi → `IllegalAccessException`; receiver/args không hợp lệ → `IllegalArgumentException` hoặc `NullPointerException`; thân method ném lỗi → `InvocationTargetException`, đọc `getCause()`. Không dùng `result == null` để suy ra lời gọi thất bại.

## 6. Các tình huống sử dụng và lỗi thường gặp

**Tình huống 1 — Kiểm tra metadata trước khi gọi.** Framework dùng `getDeclaredMethods()` và `getAnnotation(...)` để tìm những handler thỏa điều kiện. Các object `Method` thu được chỉ là mô tả. Handler chỉ chạy tại thời điểm framework gọi `invoke(...)` (hoặc một cơ chế gọi tương đương).

**Tình huống 2 — Lấy dữ liệu do handler trả ra.** Một method như `String buildName()` sẽ cho kết quả `String` từ `invoke()`. Biến chứa `Method` không phải chuỗi tên được tạo. Muốn xử lý như chuỗi, lấy kết quả gọi, kiểm tra kiểu hoặc ép kiểu phù hợp.

**Tình huống 3 — Method thay đổi trạng thái hoặc thực hiện I/O.** Không thể quyết định phương thức đã hoàn thành công việc chỉ bằng việc lấy `Method`. Phải thực thi nó, xử lý ngoại lệ, và hiểu side effect. Với `void`, kết quả từ Reflection là `null` nhưng side effect vẫn có thể có.

Những sai lầm nên tránh:

1. **“`getMethod()` gọi luôn phương thức.”** Sai: đây chỉ là tra cứu metadata.
2. **“Biến `Method` chính là giá trị method trả về.”** Sai: chỉ `invoke()` chạy method, và phải lưu giá trị trả về riêng nếu cần.
3. **“Kết quả luôn có kiểu `Method`.”** Sai: kết quả tùy chữ ký method đích; API `invoke` khai báo trả `Object`.
4. **“Khi method trả primitive thì `result.getClass()` là `int.class`.”** Sai: nếu giá trị khác `null`, Reflection boxing `int` thành `Integer`.
5. **“`result == null` nghĩa là lời gọi không chạy.”** Sai: method `void` hoặc method reference-returning trả `null` đều cho giá trị `null`.
6. **“Chỉ cần `getReturnType()` là biết giá trị trả về thực tế.”** Sai: đó là **kiểu khai báo**. Chưa chạy method và chưa có kết quả.
7. **“Exception từ method đích sẽ luôn ném thẳng ra ngoài `invoke()`.”** Sai: nó thường được bọc trong `InvocationTargetException`; cần xét `getCause()`.
8. **“Đã lấy được `Method` thì chắc chắn gọi được.”** Sai: caller còn phải đáp ứng các quy tắc visibility, JPMS, receiver và đối số.
9. **“Gọi cùng một `Method` lúc nào cũng ra cùng kết quả.”** Sai: kết quả phụ thuộc receiver, input, trạng thái và side effect tại thời điểm gọi.

## 7. Tự kiểm tra hiểu bài

1. `Method m = Counter.class.getMethod("add", int.class);` có làm thay đổi trạng thái `Counter` không? Tại sao?
2. Với `Counter c = new Counter(7);`, `m.invoke(c, 3)` trả object kiểu runtime nào và giá trị bao nhiêu? Nếu gọi lại `m.invoke(c, 3)` thì kết quả còn giống ban đầu không?
3. Vì sao `reset.invoke(c) == null` không khẳng định `reset()` thất bại?
4. Tại sao `m.getReturnType() == int.class` trong khi `m.invoke(c, 1).getClass() == Integer.class`?
5. Nếu method đích ném `IllegalStateException`, nên quan sát `InvocationTargetException` ở đâu để lấy exception gốc?
6. Khi Reflection gọi một phương thức `static` không có tham số, giá trị truyền làm receiver có thể là gì?

**Ghi nhớ:** `Method` là **đối tượng biểu diễn một phương thức**; `Method.invoke(...)` là **hành động gọi phương thức đó**; kết quả `invoke(...)` là **giá trị trả về của lần gọi**, không phải bản thân `Method`.

## Tài liệu chuẩn

- [Java SE 17 — `java.lang.reflect.Method`, đặc biệt `invoke(...)`, `getReturnType()`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html)
- [Java SE 17 — `java.lang.Class`, `getMethod(...)` và `getDeclaredMethod(...)`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html)
- [Java SE 17 — `InvocationTargetException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationTargetException.html)


</details>

- [Quay lại đầu trang](#back-to-top)
