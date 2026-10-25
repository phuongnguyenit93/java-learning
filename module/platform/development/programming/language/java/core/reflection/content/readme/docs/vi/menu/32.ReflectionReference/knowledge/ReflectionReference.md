<a id="back-to-top"></a>

# Tra cứu

## Menu
- [Bảng getX và getDeclaredX](#reflection-reference-001)
- [Bảng Class, Member, Executable, Field, Method và Constructor](#reflection-reference-002)
- [Bảng chuyển đổi được phép khi get, set, invoke hoặc newInstance](#reflection-reference-003)
- [Bảng Class, Type, AnnotatedType và các loại generic](#reflection-reference-004)
- [Bảng annotation trực tiếp, gián tiếp và được kế thừa](#reflection-reference-005)
- [Bảng quyền truy cập theo modifier, package, nest và module](#reflection-reference-006)
- [Bảng ngoại lệ reflection và thông tin cần kiểm tra](#reflection-reference-007)
- [Tra cứu Javadoc và quy tắc ngôn ngữ theo từng thao tác](#reflection-reference-008)

## <a id="reflection-reference-001">Bảng getX và getDeclaredX</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng so sánh `getX` và `getDeclaredX` trong Java Reflection

## 1. Hai nhóm API giải quyết hai câu hỏi khác nhau

Khi làm việc với `java.lang.Class<?>`, các lời gọi Reflection thường có hai “họ” tên gần giống nhau:

- **`getX(...)` / `getXs()`**: đối với **method, field, constructor và member class**, nhóm tương ứng thường tập trung vào **thành viên `public`**, đôi khi bao gồm thành viên `public` được **kế thừa**.
- **`getDeclaredX(...)` / `getDeclaredXs()`**: tìm những thành viên được **khai báo trực tiếp trong chính kiểu đang xét**, bao gồm `public`, `protected`, package-private và `private`; **không tự duyệt thành viên kế thừa**.

Hai chiều phân biệt quan trọng là:

1. **Visibility:** chỉ `public` hay mọi mức truy cập?
2. **Nguồn gốc:** thành viên được khai báo trực tiếp hay có thể kế thừa từ superclass/interface?

**Không có quy tắc “`getX` luôn duyệt inheritance” cho mọi loại thành viên.** Ví dụ constructor **không kế thừa** trong Java. Annotation có quy tắc kế thừa riêng theo `@Inherited`, không áp dụng y hệt method/field. Do đó, cần học **bảng theo từng loại `X`** thay vì áp một khẩu quyết cho tất cả API.

Mọi ví dụ trong bài dựa trên Java 17 trở lên. `getX` và `getDeclaredX` chỉ **trả về metadata hoặc thông tin kiểu**; không mặc nhiên cấp quyền gọi `private` member, không tự chạy method và không tự tạo instance.

## 2. Bảng tổng quát: các cặp API trên `Class<?>`

### 2.1. Phương thức — `Method`

| Nhu cầu | `getX` | `getDeclaredX` |
| --- | --- | --- |
| Lấy một method theo tên và danh sách kiểu tham số | `getMethod(String, Class<?>...)` | `getDeclaredMethod(String, Class<?>...)` |
| Lấy tập method | `getMethods()` | `getDeclaredMethods()` |
| Visibility | Chỉ method `public` | Mọi mức truy cập |
| Method kế thừa | Có thể bao gồm `public` method từ superclass và interface | Không; chỉ method khai báo trực tiếp |
| Private method ở class hiện tại | Không tìm thấy bằng `getMethod` | Có thể lấy metadata bằng `getDeclaredMethod` |
| Method `public` khai báo trực tiếp | Có | Có |
| Method `public` của `Object` | Thường xuất hiện trong `getMethods()` khi áp dụng cho class thông thường | Chỉ hiện nếu class đang xét trực tiếp khai báo/override nó |

Cả hai API dạng đơn đều xét **tên + kiểu tham số khai báo**, không truyền giá trị đối số. Ví dụ:

```java
Method one = type.getMethod("process", String.class);
Method two = type.getDeclaredMethod("process", String.class);
```

Đây là đoạn minh họa: phải có biến `Class<?> type` và import `java.lang.reflect.Method`. Không có tham số receiver trong lời gọi tìm metadata, mặc dù việc gọi instance method sau này cần một instance.

**Một điểm tinh tế:** `Class.getMethod(...)` có quy tắc tìm kiếm public member trong class/interface hierarchy, không phải “trả method ở superclass đầu tiên gặp được”. Nếu method bị override, API có thể chọn method phù hợp được khai báo ở subclass. Khi cần biết method do ai khai báo, dùng `method.getDeclaringClass()`. `getDeclaredMethod(...)` chỉ kiểm tra class/interface hiện tại.

### 2.2. Trường — `Field`

| Nhu cầu | `getX` | `getDeclaredX` |
| --- | --- | --- |
| Lấy một field theo tên | `getField(String)` | `getDeclaredField(String)` |
| Lấy tập field | `getFields()` | `getDeclaredFields()` |
| Visibility | Chỉ `public` | Mọi mức truy cập |
| Field từ superclass/interface | Có thể xuất hiện nếu `public` | Không tự lấy field kế thừa |
| Private field khai báo tại class hiện tại | Không | Có thể lấy metadata |
| Field `static` hoặc instance | Đều có thể được trả nếu phù hợp | Đều có thể được trả nếu phù hợp |

Field **không override**, mà có thể bị **che khuất (*field hiding*)** trong subclass. Khi cả superclass và subclass có field cùng tên, các API lookup có chính sách chọn đối tượng `Field` riêng; không coi hai field khác declaring class là một. Dùng `field.getDeclaringClass()` để xác minh. Với công cụ mapping dữ liệu, nhiều khi cần tự duyệt hierarchy và xác định có chấp nhận field bị che khuất hay không.

`getDeclaredFields()` có thể trả field do compiler tạo (`isSynthetic()`), đặc biệt trong inner class hoặc những cấu trúc được biên dịch đặc biệt. Đây vẫn là metadata của class file; muốn liệt kê state nghiệp vụ thì đặt bộ lọc riêng.

### 2.3. Constructor — `Constructor<?>`

| Nhu cầu | `getX` | `getDeclaredX` |
| --- | --- | --- |
| Lấy một constructor theo danh sách kiểu tham số | `getConstructor(Class<?>...)` | `getDeclaredConstructor(Class<?>...)` |
| Lấy tập constructor | `getConstructors()` | `getDeclaredConstructors()` |
| Visibility | Chỉ `public` | Mọi mức truy cập |
| Kế thừa từ superclass | **Không** | **Không** |
| Private constructor của class hiện tại | Không | Có thể lấy metadata |
| Constructor `public` của chính class | Có | Có |

Constructor không có tên method do caller cung cấp; tên nguồn trùng tên class, còn Reflection chọn theo danh sách kiểu tham số. Constructor của superclass **không** được kế thừa xuống subclass.

Một class có thể có nhiều constructor overload. Nếu bạn truyền sai signature cho `getConstructor(...)` hoặc `getDeclaredConstructor(...)`, chương trình ném `NoSuchMethodException`, kể cả khi class thực sự có một constructor “trông tương tự”.

### 2.4. Member class/interface — `Class<?>[]`

| API | Ý nghĩa |
| --- | --- |
| `getClasses()` | Trả các class và interface **thành viên `public`** của kiểu; đối với class có thể bao gồm public member types được kế thừa |
| `getDeclaredClasses()` | Trả các class/interface **thành viên khai báo trực tiếp** trong kiểu, bất kể visibility |

Ví dụ, `Parent.PublicNested` có thể xuất hiện trong `Child.class.getClasses()` nếu `Child extends Parent`; `Child.PrivateNested` nằm trong `Child.class.getDeclaredClasses()` nhưng không xuất hiện trong `getClasses()` vì không public.

**Không nhầm `getDeclaredClasses()` với “mọi class được viết bên trong method”.** API này nhằm vào *member class/interface*, không liệt kê tùy ý tất cả local class/anonymous class có trong bytecode. Muốn tìm class bao quanh một nested/local/anonymous class đã biết, xem thêm `getDeclaringClass()`, `getEnclosingClass()`, `getEnclosingMethod()` và `getEnclosingConstructor()` — nhưng chúng không có một cặp `getDeclared...` tương ứng với cùng ý nghĩa.

### 2.5. Annotation — `AnnotatedElement` và `Class<?>`

| API | Nội dung trả về | Kế thừa |
| --- | --- | --- |
| `getAnnotation(A.class)` | Một annotation loại `A` được thấy theo quy tắc của `AnnotatedElement` | Với **class** và annotation mang `@Inherited`, có thể lấy từ superclass |
| `getDeclaredAnnotation(A.class)` | Một annotation loại `A` được **khai báo trực tiếp** | Không tìm từ superclass |
| `getAnnotations()` | Các annotation được xem là “present” | Với **class**, có thể gồm annotation `@Inherited` từ superclass |
| `getDeclaredAnnotations()` | Annotation trực tiếp trên element | Không kế thừa |
| `getAnnotationsByType(A.class)` | Các annotation loại `A` theo quy tắc repeatable và inheritance | Có thể xét inheritance trên class theo điều kiện `@Inherited` |
| `getDeclaredAnnotationsByType(A.class)` | Annotation loại `A` được khai báo trực tiếp hoặc chứa trong container repeatable khai báo trực tiếp | Không duyệt superclass |

**Quy tắc `@Inherited` không áp dụng cho annotation trên method, field, constructor hoặc interface theo kiểu kế thừa class.** Ví dụ subclass override một method thì annotation của method ở superclass không tự động xuất hiện như annotation được “thừa kế” trên method mới. Framework muốn chính sách đó phải tự duyệt method hierarchy.

Với repeatable annotation (`@Repeatable`), các API `ByType` còn hiểu annotation chứa trong **container annotation**; vì vậy không thể đơn giản thay thế `getAnnotationsByType` bằng một `getAnnotation` đơn lẻ.

**Tóm lại:** Cặp “declared” trong annotation có nghĩa **trực tiếp trên element**, nhưng “không declared” không đồng nghĩa “mọi annotation của mọi ancestor”.

## 3. Những nhóm API không có cặp `getDeclaredX` tương ứng

Không phải tên API nào bắt đầu `get` cũng có overload/họ `getDeclared`. Chẳng hạn:

- `getSuperclass()` và `getGenericSuperclass()`: lấy superclass của kiểu; không có `getDeclaredSuperclass()`.
- `getInterfaces()` và `getGenericInterfaces()`: mô tả các interface được khai báo trực tiếp trong danh sách `implements`/`extends` của kiểu đang xét; không phải API “mọi interface kế thừa theo cây” và không có `getDeclaredInterfaces()`.
- `getTypeParameters()`: generic type variables khai báo tại class; không có `getDeclaredTypeParameters()`.
- `getEnclosingClass()`, `getEnclosingMethod()`, `getEnclosingConstructor()`: mô tả ngữ cảnh lexical enclosing cho một kiểu khi có; không có cặp declared trực tiếp.
- `getDeclaringClass()`: phân biệt thành viên nested type và lớp khai báo; không giống `getEnclosingClass()` trong mọi trường hợp.
- `getRecordComponents()`: trả record component metadata cho record (Java 16+), hoặc `null` nếu không phải record; không có `getDeclaredRecordComponents()`.

**Không tự chế API có tên `getDeclaredX` bằng quy tắc đặt tên.** Tra cứu đúng API hiện hữu trên `Class` hoặc `AnnotatedElement`. Bài này tập trung vào các nhóm Reflection truy vấn thành viên và annotation, không phải mọi method trong JDK.

## 4. Ví dụ Java 17+ hoàn chỉnh: so sánh các kết quả

Ví dụ dùng subclass có inherited public method/field, private method/field/constructor, nested class public kế thừa và nested class private. Nó cũng chứng minh khác biệt annotation `@Inherited`.

Lưu thành **`DeclaredVsPublicDemo.java`**:

```java
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.stream.Collectors;

public class DeclaredVsPublicDemo {
    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @interface Tag {
        String value();
    }

    @Tag("from-parent")
    public static class Parent {
        public int inheritedField = 1;
        private int parentPrivateField = 2;

        public Parent() {}

        public void inheritedCall() {}

        private void parentPrivateCall() {}

        public static class PublicNested {}
    }

    public static class Child extends Parent {
        private String secret = "hidden";

        public Child() {}

        private Child(int code) {
            this.secret = "id-" + code;
        }

        public String local() {
            return "local";
        }

        private void hidden() {}

        private static class PrivateNested {}
    }

    public static void main(String[] args) throws Exception {
        Class<Child> type = Child.class;

        System.out.println("method inherited public="
                + (type.getMethod("inheritedCall")
                        .getDeclaringClass() == Parent.class));
        System.out.println("method inherited declared absent="
                + hasNoDeclaredMethod(type, "inheritedCall"));
        System.out.println("method private declared="
                + (type.getDeclaredMethod("hidden")
                        .getDeclaringClass() == Child.class));
        System.out.println("method private public absent="
                + hasNoPublicMethod(type, "hidden"));

        System.out.println("field inherited public="
                + (type.getField("inheritedField")
                        .getDeclaringClass() == Parent.class));
        System.out.println("field inherited declared absent="
                + hasNoDeclaredField(type, "inheritedField"));
        System.out.println("field private declared="
                + (type.getDeclaredField("secret")
                        .getDeclaringClass() == Child.class));
        System.out.println("field private public absent="
                + hasNoPublicField(type, "secret"));

        System.out.println("constructor public count="
                + type.getConstructors().length);
        System.out.println("constructor declared count="
                + type.getDeclaredConstructors().length);
        System.out.println("constructor private declared="
                + Modifier.isPrivate(
                        type.getDeclaredConstructor(int.class)
                                .getModifiers()));

        System.out.println("nested inherited public="
                + Arrays.asList(type.getClasses())
                        .contains(Parent.PublicNested.class));
        System.out.println("nested own private declared="
                + Arrays.asList(type.getDeclaredClasses())
                        .contains(Child.PrivateNested.class));

        System.out.println("annotation inherited="
                + (type.getAnnotation(Tag.class) != null));
        System.out.println("annotation declared absent="
                + (type.getDeclaredAnnotation(Tag.class) == null));

        String declaredNames =
                Arrays.stream(type.getDeclaredMethods())
                        .filter(m -> !m.isSynthetic() && !m.isBridge())
                        .map(Method::getName)
                        .sorted()
                        .collect(Collectors.joining(","));
        System.out.println("declared method names=" + declaredNames);
    }

    private static boolean hasNoDeclaredMethod(
            Class<?> type, String name) {
        try {
            type.getDeclaredMethod(name);
            return false;
        } catch (NoSuchMethodException e) {
            return true;
        }
    }

    private static boolean hasNoPublicMethod(
            Class<?> type, String name) {
        try {
            type.getMethod(name);
            return false;
        } catch (NoSuchMethodException e) {
            return true;
        }
    }

    private static boolean hasNoDeclaredField(
            Class<?> type, String name) {
        try {
            type.getDeclaredField(name);
            return false;
        } catch (NoSuchFieldException e) {
            return true;
        }
    }

    private static boolean hasNoPublicField(
            Class<?> type, String name) {
        try {
            type.getField(name);
            return false;
        } catch (NoSuchFieldException e) {
            return true;
        }
    }
}
```

### Biên dịch và chạy

```bash
javac DeclaredVsPublicDemo.java
java DeclaredVsPublicDemo
```

Kết quả dự kiến trên JDK 17:

```text
method inherited public=true
method inherited declared absent=true
method private declared=true
method private public absent=true
field inherited public=true
field inherited declared absent=true
field private declared=true
field private public absent=true
constructor public count=1
constructor declared count=2
constructor private declared=true
nested inherited public=true
nested own private declared=true
annotation inherited=true
annotation declared absent=true
declared method names=hidden,local
```

**Lưu ý kỹ thuật về ví dụ:** `getDeclaredMethods()` của `Child` chỉ gồm hai method `hidden` và `local` vì các method helper `hasNo...` nằm trong **outer class `DeclaredVsPublicDemo`**, không thuộc `Child`. `Child` là nested class thành viên; không khai báo method do compiler sinh trong ví dụ trên với `javac` JDK 17. Dòng `sorted()` loại sự phụ thuộc vào thứ tự trả về của Reflection.

### Phân tích từng nhóm kết quả

- `Child.class.getMethod("inheritedCall")` tìm public method từ `Parent`; `getDeclaringClass()` là `Parent.class`. Nhưng `Child.class.getDeclaredMethod("inheritedCall")` ném `NoSuchMethodException` vì `Child` không khai báo method này.
- `Child.class.getDeclaredMethod("hidden")` tìm được private method của `Child`. `Child.class.getMethod("hidden")` không tìm được vì nó không public.
- `getField("inheritedField")` nhìn thấy public field từ `Parent`; `getDeclaredField("inheritedField")` không nhìn thấy nó. Ngược lại, `getDeclaredField("secret")` tìm được private field của `Child`, còn `getField("secret")` không.
- `Child` có một constructor `public` và một constructor `private` nên `getConstructors().length == 1`, `getDeclaredConstructors().length == 2`. Không constructor nào của `Parent` được kế thừa.
- `Child.class.getClasses()` tìm được `Parent.PublicNested`; `Child.class.getDeclaredClasses()` tìm được `Child.PrivateNested` dù nested class này private.
- `@Tag` được khai báo trực tiếp trên `Parent` và mang `@Inherited`; `Child.class.getAnnotation(Tag.class)` trả annotation được kế thừa theo quy tắc class, còn `getDeclaredAnnotation(Tag.class)` là `null`.
- Các phép thử báo “absent=true” thực sự bắt đúng `NoSuchMethodException` hoặc `NoSuchFieldException`. Chúng không che giấu lỗi khác bằng `catch (Exception ignored)`.

## 5. Đọc bảng kết quả đúng cách: không trộn “metadata có tồn tại” với “có quyền gọi”

`getDeclaredMethod("hidden")` thành công **không** có nghĩa mã ở mọi nơi đều được phép gọi private method ấy.

Ví dụ minh họa, đặt trong một method có `throws ReflectiveOperationException` và các instance phù hợp:

```java
Method hidden = Child.class.getDeclaredMethod("hidden");
// hidden.invoke(child) có thể bị từ chối truy cập tùy caller.
```

Cần xét:

- **Java visibility**: `public`, `protected`, package-private, `private` và mối quan hệ giữa caller/target.
- **Java Platform Module System (JPMS)**: package đã `exports` cho public API hay `opens` cho deep reflection, module caller có thể đọc module target không?
- **`AccessibleObject`**: `canAccess(receiver)`, `trySetAccessible()` hoặc `setAccessible(true)` có thể kiểm tra/thay đổi khả năng suppress access, nhưng có giới hạn JPMS.
- **Method handle**: `MethodHandles.Lookup` và `privateLookupIn` có quy tắc quyền riêng; việc có `Method` không tự cấp quyền tạo handle.
- **Đối tượng đích và tham số**: receiver đúng kiểu, số lượng/kiểu đối số, constructor gọi được; metadata hợp lệ không làm thao tác đích chắc chắn thành công.

**`getDeclared...` không có nghĩa “bỏ qua `private`”**. Nó có nghĩa API truy vấn không giới hạn vào public member; quyền truy cập thực thi là bước tiếp theo.

Một kiểu ở package không exported vẫn có thể bộc lộ metadata cho một số API, nhưng điều đó không chứng minh code ở module khác gọi được `public` member của kiểu đó. Đừng dùng `Modifier.isPublic(...)` làm kết luận cuối cùng về quyền xuyên module.

## 6. Cách chọn `getMethods()` hay `getDeclaredMethods()` cho từng công cụ

### 6.1. Tạo danh sách API public mà caller được phép sử dụng

Khi muốn liệt kê API public của một class, `getMethods()` thường là điểm xuất phát phù hợp hơn `getDeclaredMethods()` vì nó có thể nhận cả method public từ superclass/interface. Tuy nhiên:

- Kết quả có thể chứa method từ `Object` như `toString`, `hashCode`, `getClass`.
- Kết quả có thể có method static, method overload, bridge hoặc synthetic.
- Public metadata không bảo đảm caller từ module khác có quyền tương ứng.
- Nếu ứng dụng chỉ muốn operation được đánh dấu annotation, phải áp dụng thêm filter riêng.

Ví dụ minh họa: lọc method công khai không static, không synthetic/bridge rồi sắp xếp:

```java
List<Method> methods = Arrays.stream(type.getMethods())
        .filter(m -> !Modifier.isStatic(m.getModifiers()))
        .filter(m -> !m.isSynthetic() && !m.isBridge())
        .sorted(Comparator.comparing(Method::getName))
        .toList();
```

Đoạn này cần `Class<?> type` cùng imports `List`, `Method`, `Arrays`, `Modifier`, `Comparator`; chỉ là snippet, không phải chương trình độc lập. Sorting theo tên giúp thứ tự hiển thị, nhưng **không tạo khóa duy nhất cho overloaded methods**.

### 6.2. Kiểm tra chi tiết cấu trúc một class

Khi viết serializer, ORM, debugger hoặc công cụ phân tích bytecode, `getDeclaredFields()`/`getDeclaredMethods()` có thể phù hợp hơn vì cho biết thành viên khai báo thực sự trong class. Nếu cần đi qua superclass, phải tự duyệt:

```java
for (Class<?> current = type;
        current != null;
        current = current.getSuperclass()) {
    for (Field field : current.getDeclaredFields()) {
        // Phân tích field thuộc riêng current.
        // Không ngầm giả định công cụ có quyền Field.get/set.
    }
}
```

Đây là snippet minh họa, cần có biến `Class<?> type` và import `Field`. Với interface, việc duyệt qua `getInterfaces()` cần một nhánh riêng (class superclass loop không tự tổng hợp interface hierarchy).

Nếu muốn bỏ qua các field không phải trạng thái instance, kiểm tra `Modifier.isStatic(field.getModifiers())`. Nếu muốn chỉ giữ field đọc/ghi được, phải xét `final` và quyền truy cập. **Không coi mọi member declared là member phù hợp với nghiệp vụ**.

### 6.3. Chọn constructor cho dependency injection

`getDeclaredConstructors()` có thể hữu ích để phát hiện constructor non-public hoặc được đánh dấu annotation, nhưng **không nên gọi constructor đầu tiên trong mảng**: thứ tự không bảo đảm, và quyền truy cập vẫn phải được kiểm tra. `getConstructors()` chỉ phù hợp nếu DI framework dự kiến dùng constructor public.

Ví dụ, các constructor của record, enum, non-static inner class và class có constructor compiler sinh có những đặc điểm riêng; cần dùng signature thật từ Reflection, không suy từ tên hiển thị.

### 6.4. Tạo schema từ annotation

Với class-level annotation mang `@Inherited`, `getAnnotation` hoặc `getAnnotationsByType` có thể hợp lý nếu framework có chủ ý nhận metadata từ superclass. Nếu công cụ chỉ muốn annotation khai báo **trên chính class**, dùng `getDeclaredAnnotation` / `getDeclaredAnnotationsByType`.

Đừng quên method/field annotations **không tự kế thừa theo `@Inherited`**. Nếu policy yêu cầu tìm annotation trên overridden method, hãy làm rõ quy tắc tự duyệt superclasses/interfaces, resolve method signature và cách xử lý xung đột.

## 7. Các lỗi thường gặp

| Lỗi | Vì sao sai? | Cách xử lý |
| --- | --- | --- |
| Dùng `getDeclaredMethod` rồi mong tìm inherited method | Declared chỉ kiểm tra kiểu hiện tại | Dùng `getMethod` nếu cần public method kế thừa, hoặc tự duyệt hierarchy |
| Dùng `getMethod` để tìm private method | `getMethod` chỉ public | `getDeclaredMethod` để lấy metadata, sau đó kiểm tra access hợp lệ |
| Dùng `getDeclaredFields` rồi nghĩ đã có mọi field của superclass | Không tự duyệt superclass | Lặp `getSuperclass()` và dùng `getDeclaredFields()` cho từng kiểu |
| Mong `getConstructors` trả constructor superclass | Constructor không kế thừa | Chỉ truy vấn constructor của class cần tạo |
| Lấy `getDeclaredClasses` rồi mong thấy mọi local/anonymous class trong method | API trả member classes/interfaces trực tiếp | Phân biệt member/nested/local/anonymous và các API enclosing |
| Nghĩ `getAnnotation` và `getDeclaredAnnotation` luôn giống nhau | Class annotation `@Inherited` có thể kế thừa | Chọn policy inheritance rõ ràng |
| Nghĩ annotation `@Inherited` truyền xuống overridden method | `@Inherited` không áp dụng như vậy cho method | Tự duyệt method hierarchy nếu cần |
| Bỏ qua `NoSuchMethodException`/`NoSuchFieldException` | Sai tên/chữ ký khiến lookup thất bại | Bắt lỗi đúng loại, ghi tên và tham số đã tìm |
| Tưởng `getDeclared...` giúp gọi private member xuyên module | Lookup metadata không cấp quyền thực thi | Xử lý `opens`/`Lookup`/access thay vì vượt rào |
| Phụ thuộc `getMethods()[0]` hay `getDeclaredFields()[0]` | Thứ tự mảng không được bảo đảm | Sắp xếp với comparator/key có chủ đích |
| Chỉ dùng method name làm khóa | Overload có thể trùng tên | Dùng tên + kiểu tham số, và nếu cần cả declaring class/loader |
| Gọi Reflection trên `null` instance hoặc sai receiver | Metadata lookup hợp lệ nhưng invoke/field access sai | Kiểm tra receiver và đối số trước khi thực thi |

**Ghi nhớ về exception:** Các lời gọi đơn `getMethod`, `getDeclaredMethod`, `getConstructor` và `getDeclaredConstructor` có thể ném `NoSuchMethodException`; các lời gọi đơn `getField` / `getDeclaredField` có thể ném `NoSuchFieldException`. API trả **mảng** khi không có kết quả thường trả mảng rỗng, không ném “NoSuchX” chỉ vì danh sách không có thành viên. Ngoài ra vẫn có thể có lỗi khác liên quan đến nạp kiểu, bảo mật hoặc môi trường runtime.

## 8. Checklist tra cứu Reflection chính xác

1. **Chọn loại dữ liệu:** method, field, constructor, member class hay annotation?
2. **Xác định visibility cần tìm:** public API hay cả member non-public?
3. **Xác định scope:** chỉ khai báo trực tiếp, hay có xét superclass/interface?
4. **Đọc bảng API tương ứng:** đừng áp quy tắc inheritance của method cho constructor/annotation.
5. **Tìm đúng chữ ký:** method và constructor dựa trên danh sách kiểu tham số; với overload, tên thôi chưa đủ.
6. **Xác minh declaring class:** dùng `getDeclaringClass()` của `Method`/`Field`/`Constructor` để không nhầm nguồn gốc.
7. **Đặt chính sách lọc:** static, bridge, synthetic, abstract, final, annotation và khả năng thực thi tùy mục tiêu.
8. **Kiểm tra access riêng:** class visibility, JPMS `exports`/`opens`, caller, receiver, `trySetAccessible` khi có lý do hợp lệ.
9. **Sắp xếp kết quả:** nếu cần hiển thị ổn định hoặc so sánh snapshot qua các lần chạy.
10. **Chỉ cache khi đúng key và lifecycle:** `Class<?>` phản ánh class loader identity; tránh map chỉ dựa trên tên class nếu có nhiều loader.

### Câu hỏi tự kiểm tra

1. `Child.class.getMethod("run")` có thể tìm method public của superclass, còn `Child.class.getDeclaredMethod("run")` thì sao nếu `Child` không override?
2. Vì sao `getDeclaredConstructor()` có thể tìm private constructor nhưng `getConstructor()` không?
3. `getClasses()` và `getDeclaredClasses()` khác nhau ở visibility và inheritance thế nào?
4. Vì sao `Child.class.getAnnotation(Tag.class)` có thể khác `getDeclaredAnnotation(Tag.class)` khi `Tag` mang `@Inherited`?
5. `getDeclaredFields()` có bao gồm field của superclass không? Nếu không, cách duyệt toàn bộ hierarchy là gì?
6. Dùng `getDeclaredMethod("hidden")` thành công có đảm bảo `Method.invoke` từ module khác thành công không?
7. Vì sao nên tự sort kết quả của `getDeclaredMethods()` và kiểm tra `Method.isBridge()` trong công cụ tạo API?
8. Vì sao không thể tự suy luận rằng mọi API `getX` trong Java đều có bản `getDeclaredX`?

**Kết luận:** Đối với method/field/member class, `getX` thường là đường tra cứu **public** và có thể bao gồm member **kế thừa**; `getDeclaredX` là đường tra cứu **khai báo trực tiếp với mọi visibility**. Constructor **không kế thừa**; annotation theo quy tắc `@Inherited` và repeatable riêng. Hãy chọn API theo **loại thành viên + visibility + scope + quyền thực thi**, thay vì chỉ dựa vào tiền tố `Declared`.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-reference-002">Bảng Class, Member, Executable, Field, Method và Constructor</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng tra cứu: Class, Member, Executable, Field, Method và Constructor (Java Reflection)

## 1. Mục tiêu và cách đọc bảng

Java Reflection dùng các đối tượng metadata để mô tả kiểu và thành viên của chương trình lúc chạy. Sáu tên trong đề bài **không nằm trên cùng một mức của hệ phân cấp kiểu**:

- `Class<T>`: mô tả **một kiểu Java** (class, interface, enum, annotation, record, mảng, kiểu nguyên thủy hoặc `void`).
- `Member`: **interface** chung cho thành viên dạng field, method, constructor trong reflection.
- `Executable`: **abstract class** chung cho phương thức và constructor; có chung API về tham số, exceptions, annotations.
- `Field`: metadata của **trường dữ liệu**.
- `Method`: metadata của **phương thức**.
- `Constructor<T>`: metadata của **constructor** của một class.

Mục đích chính của bảng là xác định: **đối tượng nào giữ thông tin gì, API nào để tra cứu, và API nào dùng để thực thi hoặc truy cập giá trị**. Không nên học thuộc một danh sách method mà bỏ qua quan hệ của các kiểu.

## 2. Sơ đồ quan hệ chính xác

~~~text
java.lang.Object
  |
  +-- java.lang.Class<T>
  |     implements Type, AnnotatedElement, GenericDeclaration, ...
  |     (KHÔNG implements java.lang.reflect.Member)
  |
  +-- java.lang.reflect.AccessibleObject
        implements AnnotatedElement
        |
        +-- java.lang.reflect.Field
        |     implements Member
        |
        +-- java.lang.reflect.Executable (abstract)
              implements Member, GenericDeclaration
              |
              +-- java.lang.reflect.Method
              |
              +-- java.lang.reflect.Constructor<T>

java.lang.reflect.Member  (interface)
  |-- Field implements Member
  |-- Executable implements Member
      |-- Method inherits Member
      +-- Constructor<T> inherits Member
~~~

**Ba kết luận quan trọng:**

1. `Class<T>` là cửa vào tìm metadata và có thể đại diện cho **lớp lồng nhau**, nhưng bản thân `Class<T>` **không triển khai interface `Member`**. Đừng nhầm khái niệm “member class” của Java language với kiểu `java.lang.reflect.Member`.
2. `Field` và `Executable` đều là `AccessibleObject` và đều triển khai `Member`, nhưng `Field` **không** kế thừa `Executable`.
3. `Method` và `Constructor<T>` kế thừa API tham số từ `Executable`; tuy nhiên **chúng không dùng cùng API thực thi**: method gọi `invoke`, constructor tạo instance qua `newInstance`.

`AccessibleObject` không có nghĩa “mặc định được phép truy cập mọi thứ”: đây là lớp nền hỗ trợ kiểm tra/điều chỉnh quyền truy cập reflection trong giới hạn Java/JPMS. `AnnotatedElement` là interface cho các phương thức đọc annotation.

## 3. Bảng so sánh sáu API cốt lõi

| Kiểu | Bản chất | Đại diện cho | Cách lấy thường dùng | API đặc trưng |
| --- | --- | --- | --- | --- |
| `Class<T>` | Class trong `java.lang` | Một kiểu | `User.class`, `obj.getClass()`, `Class.forName(name)` | `getName`, `getSuperclass`, `getInterfaces`, `getDeclaredMethod`, `getDeclaredField`, `getDeclaredConstructor` |
| `Member` | Interface trong `java.lang.reflect` | Hợp đồng chung cho field/method/constructor | Nhận `Field`/`Method`/`Constructor` dưới kiểu `Member` | `getDeclaringClass`, `getName`, `getModifiers`, `isSynthetic` |
| `Executable` | Abstract class trong `java.lang.reflect` | Phần chung của method/constructor | Nhận `Method` hoặc `Constructor` dưới kiểu `Executable` | `getParameterCount`, `getParameters`, `getParameterTypes`, `getExceptionTypes`, `isVarArgs` |
| `Field` | Class trong `java.lang.reflect` | Một trường khai báo trong class/interface | `clazz.getField` / `getDeclaredField` | `getType`, `getGenericType`, `get`, `set`; getter/setter primitive như `getInt`/`setInt` |
| `Method` | Class kế thừa `Executable` | Một phương thức với tên + parameter types + return type | `clazz.getMethod` / `getDeclaredMethod` | `getReturnType`, `getGenericReturnType`, `invoke`, `isBridge`, `isDefault` |
| `Constructor<T>` | Class kế thừa `Executable` | Một constructor của class `T` | `clazz.getConstructor` / `getDeclaredConstructor` | `newInstance`, `getDeclaringClass`, các API tham số từ `Executable` |

**Không có `Member.invoke()` hay `Executable.invoke()` chung cho mọi subtype.** Nếu chỉ giữ biến kiểu `Member`, ta đọc được metadata phổ quát nhưng không thể gọi ngay `get`/`set`/`invoke`/`newInstance` mà không phân nhánh theo subtype.

### 3.1. Ghi chú về các kiểu trung gian và interface khác

- `java.lang.reflect.Type` là interface biểu diễn **kiểu** trong generic reflection. `Class<?>` vừa là một `Type`, nhưng `ParameterizedType` và `TypeVariable` là các biến thể khác; không mặc định mọi `Type` ép được về `Class`.
- `GenericDeclaration` áp dụng cho `Class`, `Method`, `Constructor` thông qua `Executable`; có `getTypeParameters()` cho khai báo type variables như `<T>`.
- `AnnotatedElement` cho phép đọc annotation trên class, field, method, constructor. Annotation ở **tham số** còn có thể lấy từ `Parameter`.
- `Parameter` là metadata **từng tham số** của `Executable`; không phải một trong sáu kiểu của bảng, nhưng rất thường gặp khi gọi `getParameters()`.
- `Modifier` là utility class với `Modifier.isPublic(int)`, `isPrivate(int)`, `isStatic(int)`... Nó giúp diễn giải bitmask trả về từ `Member.getModifiers()`.

## 4. Từ `Class<?>` đến Member: chọn phạm vi tra cứu

`Class<?>` là điểm xuất phát để lấy `Field`, `Method` và `Constructor`. Các cặp `getX`/`getDeclaredX` không thay đổi **kiểu đối tượng kết quả**; chúng thay đổi **phạm vi tra cứu**:

| Loại | Public/member lookup | Khai báo trực tiếp ở mọi mức truy cập |
| --- | --- | --- |
| Field | `getField(name)`, `getFields()` | `getDeclaredField(name)`, `getDeclaredFields()` |
| Method | `getMethod(name, types...)`, `getMethods()` | `getDeclaredMethod(name, types...)`, `getDeclaredMethods()` |
| Constructor | `getConstructor(types...)`, `getConstructors()` | `getDeclaredConstructor(types...)`, `getDeclaredConstructors()` |

Với field và method, tra cứu public có thể thấy member kế thừa; bản declared chỉ thấy khai báo ngay trên kiểu hiện tại. **Constructor không kế thừa**, nên cả hai nhóm constructor đều thuộc chính class đang hỏi. Kết quả mảng không bảo đảm thứ tự; một chữ ký method cần **tên + kiểu tham số chính xác sau erasure**, chẳng hạn `int.class` khác `Integer.class`.

Sự khác biệt này là **quy tắc tìm metadata**, không phải quyền dùng metadata. Một `Method` private có thể xuất hiện từ `getDeclaredMethod` nhưng `invoke` vẫn đòi access phù hợp. Cần biết nơi khai báo, đọc `member.getDeclaringClass()`; nó không nhất thiết trùng runtime class của receiver. Chương trình ở mục 7 thể hiện rõ `Field`/`Method`/`Constructor` và hành động tương ứng của mỗi subtype.

## 5. Member và Executable: API dùng chung và những khác biệt

### 5.1. Member — interface mức tối thiểu

Mọi `Field`, `Method`, `Constructor` triển khai `Member` và cung cấp:

| Phương thức | Diễn giải | Ghi chú |
| --- | --- | --- |
| `getDeclaringClass()` | Class/interface khai báo member | Không mặc định là subclass của object nhận lời gọi |
| `getName()` | Tên member | Với `Constructor`, tên là **tên binary của declaring class** (không phải một method tên `<init>`) |
| `getModifiers()` | Bitmask modifier | Dùng `Modifier.isPublic/isStatic/isFinal`... |
| `isSynthetic()` | Member do compiler tạo theo ngữ nghĩa synthetic | Có ích khi quét bridge/synthetic members |

**Không ép mọi subtype về `Method`.** Nếu code scan nhận `Member member`, kiểm tra loại bằng `instanceof Field`, `instanceof Method`, `instanceof Constructor<?>` khi cần hành vi chuyên biệt.

### 5.2. Executable — method/constructor dùng chung tham số

Với biến kiểu `Executable executable`, áp dụng được cả method lẫn constructor:

- `getParameterCount()` và `getParameterTypes()`: số lượng và kiểu tham số runtime (sau type erasure).
- `getParameters()`: mảng `Parameter` với `getName()`, `isNamePresent()`, `getType()`, `getAnnotations()`...
- `getGenericParameterTypes()`, `getAnnotatedParameterTypes()`: biểu diễn generic và type-use annotation của tham số.
- `getExceptionTypes()` / `getGenericExceptionTypes()`: kiểu exceptions khai báo trong phần `throws`.
- `getParameterAnnotations()`: annotations ở từng vị trí tham số.
- `isVarArgs()`, `getTypeParameters()`: method/constructor varargs và khai báo type variables.
- `toGenericString()`: mô tả chữ ký có thông tin generic; dùng để chẩn đoán, không dùng như parser API ổn định.

Tên tham số gốc **không bảo đảm được giữ lại** trong bytecode khi không biên dịch với `javac -parameters`. Khi đó `Parameter.getName()` có thể là `arg0`, `arg1` và `isNamePresent()` là false. Số lượng/kiểu tham số vẫn có thể tra cứu như thường.

## 6. Field, Method và Constructor: API riêng và tác dụng

| Loại | Thông tin chỉ ra | Hành động | Lưu ý quan trọng |
| --- | --- | --- | --- |
| `Field` | Tên, modifier, type (raw/generic), annotation của field | `field.get(receiver)`, `field.set(receiver, value)`, getter/setter primitive | Truy cập field không phải gọi getter/setter; field instance cần receiver phù hợp |
| `Method` | Return type, parameter types, exceptions, bridge/default/synthetic flags | `method.invoke(receiver, args...)` | Exception do method đích ném bị `InvocationTargetException` bọc |
| `Constructor<T>` | Tham số, exceptions, modifier/annotations của constructor | `constructor.newInstance(args...)` | Tạo object của declaring class; lỗi trong constructor có thể bị `InvocationTargetException` bọc |

Các điểm phân biệt thường bị hỏi:

- `Field.getType()` trả một `Class<?>` của kiểu field sau erasure; `Field.getGenericType()` trả `Type`, có thể là `ParameterizedType` đối với `List<String>`.
- `Method.getReturnType()` trả `Class<?>`. `Method.getGenericReturnType()` có thể trả `ParameterizedType`/`TypeVariable`; constructor **không** có kiểu trả về như method và **không có** API `getReturnType()`.
- `Method.isBridge()` nhận diện bridge method do compiler sinh, thường để bảo toàn đa hình với generic/covariant return; `Method.isDefault()` xác nhận default method của interface.
- `Constructor.newInstance(...)` nên được dùng thay cho `Class.newInstance()` (API cũ deprecated) vì tường minh constructor và có cách bọc lỗi nhất quán hơn.
- `Field.get`/`set` và `Method.invoke` cho static member dùng receiver `null`; instance member cần receiver tương thích.
- Không phải `getDeclaredXxx` tìm được là có thể invoke/get/set ngay: Java access checks và giới hạn JPMS vẫn áp dụng.

### 6.1. Access checks và JPMS

`Field`, `Method`, `Constructor` kế thừa `AccessibleObject`:

- `canAccess(receiver)` hỏi khả năng truy cập với receiver thích hợp; đối với static member hoặc constructor, receiver truyền `null`.
- `trySetAccessible()` cố bật chế độ bỏ qua một số Java access checks, trả true/false tùy khả năng; `setAccessible(true)` có thể ném `InaccessibleObjectException` nếu module không mở quyền cần thiết.
- Không tự động gọi `setAccessible(true)` trên toàn bộ thành viên khi scan: chỉ xin quyền thực sự cần, xử lý lỗi và tôn trọng exports/opens của module.

**Reflection access không thay thế authorization nghiệp vụ.** `public` hoặc đọc được metadata không có nghĩa request HTTP hiện tại được phép gọi method.

## 7. Ví dụ Java 17+ hoàn chỉnh: sáu kiểu trong một chương trình

Lưu thành **MemberTableDemo.java**. Ví dụ dùng lookup theo tên/chữ ký để tránh phụ thuộc thứ tự mảng reflection, minh họa một field private, một field generic, một method và một constructor public.

~~~java
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.List;

public class MemberTableDemo {
    public static final class Sample {
        private final String title;
        public List<String> tags = List.of("reflection");

        public Sample(String title) {
            this.title = title;
        }

        public String echo(String prefix) {
            return prefix + ":" + title;
        }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Class<Sample> type = Sample.class;

        Field title = type.getDeclaredField("title");
        Field tags = type.getDeclaredField("tags");
        Method echo = type.getDeclaredMethod("echo", String.class);
        Constructor<Sample> ctor = type.getDeclaredConstructor(String.class);

        System.out.println("Class: " + type.getSimpleName());
        System.out.println("Class is Member: "
                + Member.class.isAssignableFrom(Class.class));
        System.out.println("Field: " + title.getName()
                + " | type=" + title.getType().getSimpleName()
                + " | private=" + Modifier.isPrivate(title.getModifiers()));
        System.out.println("Method: " + echo.getName()
                + " | return=" + echo.getReturnType().getSimpleName()
                + " | params=" + echo.getParameterCount());
        System.out.println("Constructor: " + ctor.getDeclaringClass().getSimpleName()
                + " | params=" + ctor.getParameterCount());

        System.out.println("Field is Executable: "
                + Executable.class.isAssignableFrom(Field.class));
        System.out.println("Method is Executable: " + (echo instanceof Executable));
        System.out.println("Constructor is Executable: " + (ctor instanceof Executable));
        System.out.println("Method is Member: " + (echo instanceof Member));

        ParameterizedType generic = (ParameterizedType) tags.getGenericType();
        System.out.println("Generic field raw=" + tags.getType().getSimpleName()
                + ", actual=" + generic.getActualTypeArguments()[0].getTypeName());
        System.out.println("Inherited public method declares="
                + type.getMethod("toString").getDeclaringClass().getSimpleName());

        Sample sample = ctor.newInstance("Java");
        System.out.println("Invoke result: " + echo.invoke(sample, "Hi"));
    }
}
~~~

Chạy:

~~~bash
javac --release 17 -encoding UTF-8 MemberTableDemo.java
java MemberTableDemo
~~~

Output dự kiến:

~~~text
Class: Sample
Class is Member: false
Field: title | type=String | private=true
Method: echo | return=String | params=1
Constructor: Sample | params=1
Field is Executable: false
Method is Executable: true
Constructor is Executable: true
Method is Member: true
Generic field raw=List, actual=java.lang.String
Inherited public method declares=Object
Invoke result: Hi:Java
~~~

**Đọc kết quả đúng theo hệ phân cấp:**

1. `Class is Member: false` chứng minh kiểu `Class` không triển khai interface `Member`.
2. Field `title` private vẫn tìm được bằng `getDeclaredField` nhưng ví dụ **không** đọc hoặc sửa giá trị private field bằng reflection, nên không cần bật `setAccessible`.
3. `Method` và `Constructor` là `Executable`, còn `Field` thì không.
4. Field `tags` khai báo `List<String>`: `getType()` trả raw `List`, còn `getGenericType()` biểu diễn `ParameterizedType` có argument `java.lang.String`.
5. `type.getMethod("toString")` lấy một method public kế thừa từ `Object`, khác `getDeclaredMethod` chỉ tìm method của `Sample`.
6. `ctor.newInstance("Java")` tạo object; `echo.invoke(sample, "Hi")` thực thi method và trả `"Hi:Java"`.

Nếu dùng `type.getField("title")` thay vì `getDeclaredField("title")` thì nhận `NoSuchFieldException`, vì `title` là private. Nếu dùng `type.getDeclaredMethod("toString")` thì cũng nhận `NoSuchMethodException` vì `Sample` không khai báo override `toString`.

## 8. Cách chọn đúng API theo câu hỏi thực tế

| Câu hỏi của bạn | API thích hợp | Vì sao? |
| --- | --- | --- |
| Đối tượng này thuộc kiểu runtime nào? | `object.getClass()` | Nhận `Class<?>` của object |
| Tìm method public có thể gọi theo kiểu? | `type.getMethod("save", Id.class)` | Cho phép method public được kế thừa |
| Tìm method được khai báo trực tiếp, kể cả private? | `type.getDeclaredMethod(...)` | Phạm vi đúng class; quyền invoke là bước khác |
| Đọc metadata tên/modifier/declaring class của nhiều member? | Biến/collection kiểu `Member` | Không phụ thuộc subtype cụ thể |
| Tạo cùng logic kiểm tra signature cho method và constructor? | Biến kiểu `Executable` | Chung `getParameters/getExceptionTypes/isVarArgs` |
| Tìm thông tin kiểu của field? | `Field.getType/getGenericType` | Field có loại dữ liệu, không có return type |
| Thực thi method? | `Method.invoke` | Receiver + đối số; exception từ method đích được bọc |
| Tạo object bằng constructor? | `Constructor.newInstance` | Không dùng `Method.invoke` cho constructor |
| Đọc annotation trên member? | `AnnotatedElement.getAnnotation(...)` | `Class` và member classes cùng hỗ trợ interface phù hợp |
| Tìm annotation trên parameter? | `Executable.getParameters()` → `Parameter.getAnnotation(...)` | Parameter là thành phần riêng, không phải `Member` |

**Trường hợp generic:** nếu `Type t = field.getGenericType()`, hãy kiểm tra `t instanceof ParameterizedType` trước khi cast. Với field kiểu `String`, `getGenericType()` có thể trả `Class<String>`, không phải `ParameterizedType`. Khi generic signature không hợp lệ hoặc thiếu type, API metadata generic có thể ném những lỗi đặc thù khác.

## 9. Lỗi phổ biến và giới hạn

1. **Nhầm Class với Member:** một nested class là member theo ngôn ngữ Java, nhưng metadata `Class<?>` của nó không thể ép thành `java.lang.reflect.Member`.
2. **Dùng Executable cho Field:** `Field` không có `getParameters()`, cũng không có `invoke()`.
3. **Gọi Constructor như Method:** constructor không có kiểu trả về hoặc `Method.invoke`; dùng `Constructor.newInstance`.
4. **Nhầm public với declared:** `getDeclaredMethod` không quét member kế thừa; `getMethod` không tìm private method.
5. **Nhầm metadata với quyền sử dụng:** `getDeclaredField` tìm được private field không có nghĩa `field.get(receiver)` luôn hợp lệ.
6. **Đoán thứ tự mảng reflection:** `getDeclaredFields()` và `getDeclaredMethods()` không cam kết trả theo thứ tự source.
7. **Nhầm raw type với generic type:** `getType/getReturnType/getParameterTypes` chưa giữ toàn bộ cấu trúc parameterized type; dùng `getGenericType/getGenericReturnType/getGenericParameterTypes` khi cần.
8. **Hiểu sai tên constructor:** trong reflection `Constructor.getName()` là **binary name** của declaring class, không phải tên `<init>` trong bytecode.
9. **Dùng reflection trong hot path mà không cache:** nếu phải lặp lại lookup cùng chữ ký, có thể cache `Method/Field/Constructor` theo đúng declaring class và signature; đo trước khi tối ưu.
10. **Không xử lý exception thích hợp:** lookup thường ném `NoSuchFieldException`/`NoSuchMethodException`; invoke/newInstance có thể ném `IllegalAccessException`, `IllegalArgumentException`, `InvocationTargetException`; ngoại lệ lớp đích phải được kiểm tra từ `getCause()`.

## 10. Checklist ghi nhớ nhanh

- `Class` = **mô tả kiểu và điểm xuất phát lookup**.
- `Member` = **interface metadata chung** của field/method/constructor.
- `Executable` = **lớp chung method + constructor**, chuyên thông tin parameters/exceptions.
- `Field` = **dữ liệu**: type, get, set.
- `Method` = **hành vi có tên**: return type, invoke, bridge/default.
- `Constructor` = **khởi tạo object**: parameters, newInstance.
- `getXxx` thường tìm public phù hợp và có thể kế thừa đối với field/method; `getDeclaredXxx` tìm member khai báo trực tiếp, kể cả non-public.
- Chọn API bằng **ngữ nghĩa của thao tác**, sau đó mới cân nhắc cache, quyền truy cập, lỗi và hiệu năng.

**Câu hỏi tự kiểm tra:**

1. Vì sao `Member.class.isAssignableFrom(Class.class)` trả false?
2. Nếu muốn kiểm tra số lượng tham số của cả method và constructor, dùng chung kiểu nào?
3. Vì sao field private vẫn được tìm thấy bằng `getDeclaredField` nhưng chưa chắc truy cập được bằng `Field.get`?
4. `Method.getReturnType` và `Field.getType` giống và khác nhau thế nào?
5. Tại sao `getMethod("toString")` thành công, còn `getDeclaredMethod("toString")` trên `Sample` lại không?
6. Khi phản xạ vào `List<String>`, vì sao nên xem `Type`/`ParameterizedType` thay vì chỉ `Class<?>`?

### Nguồn tra cứu API chính thức

- `Class` (Java SE 21): https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html
- `Member`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Member.html
- `Executable`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Executable.html
- `Field`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Field.html
- `Method`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Method.html
- `Constructor`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Constructor.html
- `AccessibleObject`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AccessibleObject.html
- `Parameter`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Parameter.html

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-reference-003">Bảng chuyển đổi được phép khi get, set, invoke hoặc newInstance</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng chuyển đổi kiểu được phép khi Reflection `get`, `set`, `invoke` và `newInstance`

Khi sử dụng Java Reflection, tên kiểu có thể được biết tại runtime nhưng lời gọi vẫn phải tuân theo **quy tắc chuyển đổi kiểu**. Một số chuyển đổi được Java Reflection hỗ trợ tự động, đặc biệt là **mở rộng kiểu nguyên thủy (primitive widening)** và **mở hộp (unboxing)** trong các trường hợp phù hợp. Ngược lại, **thu hẹp kiểu (narrowing)** như `long → int` hay `double → float` không được Reflection tự thực hiện.

Bốn nhóm API dễ bị nhầm:

- `Field.get(...)`, `Field.getInt(...)`, `Field.getLong(...)`...: **đọc giá trị một field**, có thể boxing hoặc widening tùy getter được gọi.
- `Field.set(...)`, `Field.setInt(...)`, `Field.setLong(...)`...: **ghi một giá trị vào field**, chỉ khi giá trị phù hợp kiểu field.
- `Method.invoke(...)`: gọi **method đã được tìm thấy**, kiểm tra/chuyển đổi **đối số thực tế sang kiểu tham số khai báo**.
- `Constructor.newInstance(...)`: tạo đối tượng bằng **constructor đã được tìm thấy**, sử dụng các quy tắc chuyển đổi đối số tương tự `Method.invoke`.

Bài này đưa ra bảng đầy đủ cho tám primitive, đối chiếu các nhóm API, ví dụ Java 17+ biên dịch được và những trường hợp thất bại cần nhận biết. Chủ đề là **kiểu dữ liệu**, không phải tự động vượt qua quyền truy cập `private`, `final` hay ranh giới module.

## 1. Nền tảng: identity, widening, boxing và unboxing

### 1.1. Identity và widening primitive

**Identity conversion** giữ nguyên kiểu, ví dụ `int → int`.

**Widening primitive conversion** chuyển kiểu nguyên thủy sang kiểu đích được Java cho phép theo sơ đồ:

```text
byte  ──> short ──> int ──> long ──> float ──> double
                 ^
char ────────────┘

boolean  (chỉ chuyển sang boolean)
```

Sơ đồ chỉ minh họa những nhánh chính. Bảng đầy đủ dưới đây mới là chuẩn để tra cứu, đặc biệt vì `char` không chuyển trực tiếp sang `short` và các kiểu số có nhiều nhánh mở rộng.

| Kiểu nguồn | Kiểu đích primitive được phép (bao gồm chính nó) |
| --- | --- |
| `boolean` | `boolean` |
| `byte` | `byte`, `short`, `int`, `long`, `float`, `double` |
| `short` | `short`, `int`, `long`, `float`, `double` |
| `char` | `char`, `int`, `long`, `float`, `double` |
| `int` | `int`, `long`, `float`, `double` |
| `long` | `long`, `float`, `double` |
| `float` | `float`, `double` |
| `double` | `double` |

**Không được phép tự động:** `int → byte`, `long → int`, `double → float`, `short → char`, `char → short`, và mọi chuyển đổi giữa `boolean` với các kiểu số.

`byte → char` cũng không được phép dù có những giá trị byte dương biểu diễn được bởi `char`. Reflection tuân theo **quan hệ chuyển đổi kiểu**, không tự xét giá trị hiện tại có vừa miền giá trị của kiểu đích hay không.

**Widening không đồng nghĩa giữ nguyên độ chính xác tuyệt đối:** ví dụ `long → float` và `int → float` là chuyển đổi hợp lệ nhưng có thể mất bit chính xác do floating-point representation. Quy tắc widening ở đây nói đến **khả năng chuyển kiểu**, không phải bảo đảm không mất thông tin số.

### 1.2. Boxing và unboxing

Tám cặp primitive/wrapper tương ứng:

| Primitive | Wrapper |
| --- | --- |
| `boolean` | `Boolean` |
| `byte` | `Byte` |
| `short` | `Short` |
| `char` | `Character` |
| `int` | `Integer` |
| `long` | `Long` |
| `float` | `Float` |
| `double` | `Double` |

- **Boxing**: `int` thành đối tượng `Integer`.
- **Unboxing**: `Integer` trở lại primitive `int`.
- **Unboxing rồi widening**: `Integer(4) → int → long` là hợp lệ khi method/constructor/field đích yêu cầu `long`.
- **Không có chuyển đổi tự động theo chuỗi unbox → widen → box sang wrapper khác**: `Integer` không tự biến thành `Long` để điền field hoặc tham số **khai báo `Long`**.

Khi viết `method.invoke(target, 5)`, trình biên dịch tự boxing literal `5` thành `Integer` để đưa vào danh sách đối số `Object...`. Sau đó, nếu **tham số phương thức đích là `long`**, Reflection có thể unbox `Integer → int` và widen `int → long`.

## 2. Bảng nhanh: API nào nhận chuyển đổi nào?

| Lời gọi Reflection | Kiểu nguồn thực tế | Kiểu đích/giá trị cần nhận | Quy tắc chính |
| --- | --- | --- | --- |
| `field.get(obj)` | Kiểu khai báo của field | `Object` trả về | Field primitive được **box theo đúng kiểu gốc**; field tham chiếu trả chính giá trị tham chiếu |
| `field.getInt(obj)` hoặc `getX` | Primitive của field | Primitive theo tên getter | Chỉ đọc được khi **field type → getter type** là identity/widening |
| `field.set(obj, value)` | Đối tượng `value` | Kiểu khai báo của field | Field primitive: **unbox wrapper** rồi (nếu cần) widen; field tham chiếu: kiểm tra giá trị assignable hoặc `null` |
| `field.setInt(obj, n)` hoặc `setX` | Primitive theo tên setter | Kiểu khai báo của field | Chỉ ghi được nếu **setter type → field type** là identity/widening |
| `method.invoke(obj, args...)` | Từng đối số `Object`/wrapper | Tham số khai báo của **method đã chọn** | Unbox + widen cho primitive; tham chiếu phải tương thích; không tự narrowing |
| `constructor.newInstance(args...)` | Từng đối số `Object`/wrapper | Tham số khai báo của **constructor đã chọn** | Tương tự `Method.invoke`; không tự narrowing |

**Hai chiều dễ nhầm nhất:**

- Getter có kiểu đích: `Field.getLong` có thể đọc field `int` vì `int → long`.
- Setter có kiểu nguồn: `Field.setLong` **không** thể ghi vào field `int` vì `long → int` là narrowing.

Nói ngắn gọn: đọc dùng hướng **field → kết quả getter**; ghi/gọi/tạo đối tượng dùng hướng **đối số → vị trí đích**.

## 3. `Field.get(obj)`: đọc field dưới dạng `Object`

`Field.get(Object obj)` trả về một `Object`.

- Nếu field là reference, trả về reference đang lưu, có thể là `null`.
- Nếu field primitive, giá trị được **box** thành wrapper tương ứng. Field `short` trả `Short`, field `int` trả `Integer`; không biến `short` thành `Integer` chỉ vì kết quả khai báo của `get` là `Object`.
- Lệnh `(int) field.get(obj)` có thể unbox khi đối tượng thực tế là `Integer`, nhưng **không** phải cách tổng quát để chuyển một `Short` đã box thành `int`: cách ép kiểu này có thể ném `ClassCastException`.

Ví dụ:

```java
class Sample {
    public short small = 7;
}

// Giả định một đoạn code khác đã tạo đối tượng Sample:
Sample x = new Sample();
java.lang.reflect.Field field = Sample.class.getField("small");

Object raw = field.get(x);          // một Short chứa giá trị 7
short small = (Short) raw;          // đúng
int widened = field.getInt(x);     // đúng: short → int
```

Đây là đoạn minh họa phụ thuộc vào vị trí đặt class `Sample` để có quyền truy cập thích hợp; chương trình đầy đủ, độc lập nằm ở mục 7.

## 4. `Field.getBoolean/getByte/.../getDouble`: tra cứu phép đọc widening

Các getter primitive bao gồm `getBoolean`, `getByte`, `getShort`, `getChar`, `getInt`, `getLong`, `getFloat`, `getDouble`.

**Hướng bảng: cột “kiểu field” là nguồn, “getter được phép” là kiểu đích.**

| Kiểu field | Những getter primitive hợp lệ |
| --- | --- |
| `boolean` | `getBoolean` |
| `byte` | `getByte`, `getShort`, `getInt`, `getLong`, `getFloat`, `getDouble` |
| `short` | `getShort`, `getInt`, `getLong`, `getFloat`, `getDouble` |
| `char` | `getChar`, `getInt`, `getLong`, `getFloat`, `getDouble` |
| `int` | `getInt`, `getLong`, `getFloat`, `getDouble` |
| `long` | `getLong`, `getFloat`, `getDouble` |
| `float` | `getFloat`, `getDouble` |
| `double` | `getDouble` |

Ví dụ:

```java
// field là một Field thuộc instance có kiểu short.
int a = field.getInt(instance);      // hợp lệ: short → int
double b = field.getDouble(instance);// hợp lệ: short → double
byte c = field.getByte(instance);    // IllegalArgumentException: short → byte
```

Đoạn trên là minh họa, không phải file chạy độc lập. Lưu ý các **typed getter không tự unbox reference field**: nếu field khai báo `Integer` thì `getInt` không phải phương án tổng quát để đọc wrapper. Hãy dùng `get`, kiểm tra `Integer`, sau đó tự unbox.

Ngữ nghĩa của bảng không phụ thuộc giá trị: field `short` đang chứa số 7 vẫn **không** thể được lấy bằng `getByte` vì kiểu field là `short`.

## 5. `Field.set(obj, value)`: viết field, kiểm tra kiểu đích

`Field.set(Object obj, Object value)` nhận đối số thứ hai dưới dạng tham chiếu.

### 5.1. Ghi vào primitive field

Giả sử field khai báo:

```java
public long count;
```

Các giá trị được phép:

```java
field.set(obj, Long.valueOf(9));     // Long → long
field.set(obj, Integer.valueOf(9));  // Integer → int → long
field.set(obj, Short.valueOf((short) 9)); // Short → short → long
field.set(obj, Character.valueOf('A'));  // Character → char → long
```

Với field `long`, `Byte`, `Short`, `Character`, `Integer` và `Long` được unbox rồi widening nếu cần; `Float`, `Double` và `Boolean` không phù hợp.

Những thao tác sau thất bại:

```java
field.set(obj, Double.valueOf(9)); // IllegalArgumentException: double → long
field.set(obj, "9");               // IllegalArgumentException: String không phải wrapper số
field.set(obj, null);              // IllegalArgumentException: không unbox được null
```

Reflection **không parse chuỗi số**, không kiểm tra và tự thu hẹp một `Long` xuống `int`, và không tự chuyển `Double` sang `Integer` theo phép ép tường minh. Khi input từ JSON/CSV/String, ứng dụng phải tự parse, kiểm tra giới hạn và đưa ra wrapper có thể unbox/widen hợp lệ.

### 5.2. Ghi vào reference field

Nếu field khai báo `CharSequence`, có thể gán `String`, `StringBuilder` hoặc `null` vì chúng tương thích với kiểu đích:

```java
// Field text được khai báo: public CharSequence text;
textField.set(instance, "abc");                    // hợp lệ
textField.set(instance, new StringBuilder("abc")); // hợp lệ
textField.set(instance, null);                     // hợp lệ
textField.set(instance, Integer.valueOf(3));       // IllegalArgumentException
```

Đối với field khai báo `Long`, `Integer.valueOf(3)` **không** được chuyển thành `Long.valueOf(3)`: đây là hai wrapper reference khác nhau.

### 5.3. Final, static, receiver và accessibility là những vấn đề khác

- Instance field cần receiver đúng class hoặc subtype, không được truyền `null`.
- Với static field, receiver bị bỏ qua (có thể dùng `null`).
- Ghi field `final` bị giới hạn riêng, và việc cố mở quyền bằng `setAccessible(true)` không bảo đảm được phép ghi. Final static field thuộc diện ràng buộc chặt.
- `getDeclaredField` tìm được metadata của private field nhưng không đồng nghĩa được phép đọc/ghi; `IllegalAccessException` có thể xảy ra.
- Khi tên/receiver/quyền truy cập không hợp lệ, lỗi có thể xảy ra **trước khi** quy tắc chuyển đổi giá trị được áp dụng.

## 6. `Field.setBoolean/setByte/.../setDouble`: bảng setter primitive

Các typed setter nhận một giá trị primitive (ví dụ `setInt(Object obj, int i)`). **Hướng bảng: kiểu tham số của setter → kiểu field đích.**

| Setter được gọi | Kiểu field có thể nhận |
| --- | --- |
| `setBoolean` | `boolean` |
| `setByte` | `byte`, `short`, `int`, `long`, `float`, `double` |
| `setShort` | `short`, `int`, `long`, `float`, `double` |
| `setChar` | `char`, `int`, `long`, `float`, `double` |
| `setInt` | `int`, `long`, `float`, `double` |
| `setLong` | `long`, `float`, `double` |
| `setFloat` | `float`, `double` |
| `setDouble` | `double` |

Ví dụ field có kiểu `long`:

```java
wideField.setInt(instance, 42);       // hợp lệ: int → long
wideField.setLong(instance, 42L);     // hợp lệ: long → long
wideField.setDouble(instance, 42.0);  // IllegalArgumentException
```

Đừng đảo hướng của bảng getter và setter. `getLong` từ field `int` hợp lệ, trong khi `setLong` vào field `int` không hợp lệ.

## 7. Chương trình Java 17+: chạy thật các ca thành công và thất bại

Ví dụ sau minh họa **cả bốn API**, gồm primitive widening, unboxing, reference assignability, lookup theo chữ ký chính xác, `void`, varargs và các lỗi thu hẹp kiểu.

Tạo file **`ReflectionConversionsDemo.java`**:

```java
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectionConversionsDemo {
    public static class Target {
        public short small = 7;
        public int number = 10;
        public long wide;
        public CharSequence text = "before";
        public static int shared = 3;

        public Target(long seed) {
            this.wide = seed;
        }

        public double twice(double value) {
            return 2 * value;
        }

        public String describe(CharSequence value) {
            return "[" + value + "]";
        }

        public void touch() {}

        public static String join(String... words) {
            return String.join("|", words);
        }
    }

    @FunctionalInterface
    interface Checked {
        void run() throws Exception;
    }

    private static void expectIllegal(String label, Checked action)
            throws Exception {
        try {
            action.run();
            System.out.println(label + ": unexpected success");
        } catch (IllegalArgumentException expected) {
            System.out.println(label + ": IllegalArgumentException");
        }
    }

    public static void main(String[] args) throws Exception {
        Constructor<Target> ctor = Target.class.getConstructor(long.class);
        Target target = ctor.newInstance(Integer.valueOf(4));
        System.out.println("constructor long=" + target.wide);

        Field small = Target.class.getField("small");
        System.out.println("Field.get wrapper="
                + small.get(target).getClass().getSimpleName());
        System.out.println("Field.getInt short=" + small.getInt(target));
        expectIllegal("Field.getByte short", () -> small.getByte(target));

        Field wide = Target.class.getField("wide");
        wide.set(target, Integer.valueOf(21));
        System.out.println("Field.set Integer -> long="
                + wide.getLong(target));
        wide.setInt(target, 33);
        System.out.println("Field.setInt -> long="
                + wide.getLong(target));

        Field number = Target.class.getField("number");
        number.set(target, Character.valueOf('A'));
        System.out.println("Field.set Character -> int="
                + number.getInt(target));
        expectIllegal("Field.set Long -> int",
                () -> number.set(target, Long.valueOf(5)));

        Field text = Target.class.getField("text");
        text.set(target, new StringBuilder("ready"));
        System.out.println("Field.set subtype=" + text.get(target));
        expectIllegal("Field.set incompatible reference",
                () -> text.set(target, Integer.valueOf(3)));

        Method twice = Target.class.getMethod("twice", double.class);
        System.out.println("Method.invoke Integer -> double="
                + twice.invoke(target, Integer.valueOf(6)));
        expectIllegal("Method.invoke String -> double",
                () -> twice.invoke(target, "6"));

        Method describe =
                Target.class.getMethod("describe", CharSequence.class);
        System.out.println("Method.invoke String -> CharSequence="
                + describe.invoke(target, "abc"));

        try {
            Target.class.getMethod("twice", int.class);
            System.out.println("getMethod int: unexpected success");
        } catch (NoSuchMethodException expected) {
            System.out.println("getMethod int: NoSuchMethodException");
        }

        Method touch = Target.class.getMethod("touch");
        System.out.println("void invoke -> null="
                + (touch.invoke(target) == null));

        Method join = Target.class.getMethod("join", String[].class);
        System.out.println("varargs array="
                + join.invoke(null, (Object) new String[]{"a", "b"}));
        expectIllegal("varargs no auto packing",
                () -> join.invoke(null, "a", "b"));

        expectIllegal("newInstance String -> long",
                () -> ctor.newInstance("4"));
        System.out.println("primitive get boxed="
                + (number.get(target) instanceof Integer));
    }
}
```

**Biên dịch/chạy:**

```bash
javac -encoding UTF-8 ReflectionConversionsDemo.java
java ReflectionConversionsDemo
```

**Output thực tế của chương trình:**

```text
constructor long=4
Field.get wrapper=Short
Field.getInt short=7
Field.getByte short: IllegalArgumentException
Field.set Integer -> long=21
Field.setInt -> long=33
Field.set Character -> int=65
Field.set Long -> int: IllegalArgumentException
Field.set subtype=ready
Field.set incompatible reference: IllegalArgumentException
Method.invoke Integer -> double=12.0
Method.invoke String -> double: IllegalArgumentException
Method.invoke String -> CharSequence=[abc]
getMethod int: NoSuchMethodException
void invoke -> null=true
varargs array=a|b
varargs no auto packing: IllegalArgumentException
newInstance String -> long: IllegalArgumentException
primitive get boxed=true
```

Kết quả trên được kiểm chứng bằng `javac` và `java`. Một số câu lệnh dùng `expectIllegal` để minh họa và **chủ động bắt lỗi**, vì vậy chương trình vẫn kết thúc bình thường.

**Đọc kết quả theo nhóm:**

1. `Constructor<Target>` nhận tham số khai báo `long`. Truyền `Integer(4)` được unbox thành `int` rồi widen thành `long`, nên `wide = 4`.
2. `small` là field `short`. `Field.get` box thành `Short`; `getInt` hợp lệ; `getByte` thất bại vì sẽ phải thu hẹp `short → byte`.
3. `wide` là field `long`. Ghi `Integer(21)` hoặc `setInt(33)` đều hợp lệ. `number` là field `int`; ghi `Character('A')` thành 65, còn `Long(5)` không thể tự thu hẹp xuống `int`.
4. `text` khai báo `CharSequence`, nhận được `StringBuilder` vì implements interface này, nhưng không nhận `Integer`.
5. `twice(double)` nhận `Integer(6)` sau unbox+widen và trả về `Double(12.0)`. Truyền chuỗi `"6"` không tự parse.
6. `describe(CharSequence)` nhận `String` vì `String` implements `CharSequence`.
7. `getMethod("twice", int.class)` **không tìm thấy** chỉ vì tồn tại `twice(double)`: lookup yêu cầu **chữ ký tham số khai báo**, không áp dụng quy tắc widening khi tìm method.
8. `void` trả `null` qua `Method.invoke`. Phương thức nhận `String...` thực chất có tham số reflection cuối kiểu `String[]`, nên phải truyền một mảng như **một đối số**.
9. Constructor `Target(long)` không tự chuyển `String` thành `long`.

## 8. `Method.invoke`: chuyển đổi tham số sau khi đã chọn method

`Method.invoke(Object receiver, Object... args)` cho phép các chuyển đổi kiểu khi **truyền giá trị** cho từng tham số method:

| Method đích | Đối số truyền vào | Kết quả |
| --- | --- | --- |
| `f(long)` | `Integer.valueOf(3)` | Hợp lệ: unbox `int`, widen `long` |
| `f(double)` | `Long.valueOf(3)` | Hợp lệ: unbox `long`, widen `double` |
| `f(CharSequence)` | `"abc"` | Hợp lệ: reference tương thích |
| `f(Number)` | `Integer.valueOf(3)` | Hợp lệ: `Integer` là subtype của `Number` |
| `f(Long)` | `Integer.valueOf(3)` | Không hợp lệ: không tự unbox+widen+box thành `Long` |
| `f(int)` | `Long.valueOf(3)` | Không hợp lệ: phải narrowing `long → int` |
| `f(boolean)` | `Integer.valueOf(1)` | Không hợp lệ: số không tự đổi thành boolean |
| `f(int)` | `null` | Không hợp lệ: không thể unbox `null` |
| `f(String)` | `null` | Hợp lệ nếu việc gọi method/logic bên trong chấp nhận `null` |
| `f(int)` | `"123"` | Không hợp lệ: Reflection không parse String |

**Cực kỳ quan trọng:** hai bước sau hoàn toàn khác nhau:

```java
Method m = Target.class.getMethod("twice", double.class); // TÌM đúng signature
Object result = m.invoke(target, Integer.valueOf(6));    // CHUYỂN giá trị
```

`getMethod` và `getDeclaredMethod` tra cứu dựa trên **tên + kiểu tham số khai báo chính xác**, không tự chọn overload theo khả năng widening của đối số. Reflection không thay compiler làm overload resolution.

### Giá trị trả về của `invoke`

- Method trả primitive: `Method.invoke` box thành wrapper tương ứng, ví dụ `double → Double`.
- Method trả reference: trả object thực tế, có thể `null`.
- Method trả `void`: `invoke` trả `null`.
- Mảng primitive như `int[]` được trả dưới dạng đối tượng mảng `int[]`, **không** tự box mỗi phần tử thành `Integer`.

**Lỗi khác chuyển đổi:** method hợp lệ nhưng code đích ném exception thì `Method.invoke` bọc exception trong `InvocationTargetException`; xem `getCause()`. Lỗi kiểm tra đối số do Reflection thường là `IllegalArgumentException`, lỗi truy cập có thể là `IllegalAccessException`, receiver không phù hợp cũng dẫn đến lỗi riêng.

## 9. `Constructor.newInstance`: cùng quy tắc đối số, nhưng constructor phải chọn đúng

`Constructor<T>.newInstance(Object... args)` áp dụng **unboxing và method invocation conversions** để khớp từng đối số với kiểu tham số của constructor đã chọn.

```java
Constructor<Target> constructor =
        Target.class.getConstructor(long.class); // lookup chính xác long
Target value = constructor.newInstance(Integer.valueOf(4)); // hợp lệ
```

Trong đoạn trên, `Target` là class được định nghĩa ở ví dụ mục 7. Nếu thay `getConstructor(int.class)`, lookup ném `NoSuchMethodException` vì chỉ có constructor `Target(long)`.

Một vài tình huống:

- `newInstance(Short.valueOf((short) 5))` với constructor `(long)`: hợp lệ.
- `newInstance(Long.valueOf(5))` với constructor `(int)`: không hợp lệ.
- `newInstance("5")` với constructor `(long)`: không hợp lệ vì Reflection không parse.
- `newInstance(null)` có thể gây nhập nhằng ở **lời gọi varargs Java**. Nếu muốn đưa **một tham số `null`** cho constructor có một tham số reference, dùng `newInstance((Object) null)`. Đối với constructor không tham số, có thể dùng `newInstance()`.

Tương tự `Method.invoke`, constructor có tham số varargs **không tự nhận từng đối số rời rạc của method đích rồi đóng gói vào mảng**. Hãy truyền một mảng đúng kiểu như một tham số formal.

Một class `abstract`, enum, class không truy cập được hoặc constructor ném exception vẫn có thể làm `newInstance` thất bại vì lý do ngoài chuyển đổi. `Class.newInstance()` đã bị deprecated từ Java 9; dùng `getDeclaredConstructor().newInstance()` khi phù hợp thay thế.

## 10. Varargs có hai tầng: của API Reflection và của method đích

Chữ ký `Method.invoke(Object receiver, Object... args)` nghĩa là **chính API Reflection nhận varargs**. Điều đó không làm `invoke` tự thực hiện cơ chế gom varargs cho method được gọi.

Ví dụ method đích:

```java
public static String join(String... words) {
    return String.join("|", words);
}
```

Runtime signature của nó là `join(String[])`. Khi dùng Reflection:

```java
Method join = Target.class.getMethod("join", String[].class);
Object result = join.invoke(null, (Object) new String[]{"a", "b"}); // đúng
```

Ép `(Object)` bảo đảm mảng là **một phần tử** của `args` thay vì có thể bị diễn giải thành chính mảng chứa các đối số cho `Method.invoke`.

```java
join.invoke(null, "a", "b"); // Sai: 2 đối số thay vì 1 đối số String[]
```

Trong tình huống phức tạp, cách rõ nhất là tạo mảng `Object[]` chứa đúng số lượng/thứ tự formal parameters:

```java
Object[] supplied = {new String[]{"a", "b"}};
Object output = join.invoke(null, supplied);
```

Nếu method đích có hai tham số, trong đó tham số cuối là varargs, hãy truyền **hai phần tử** cho mảng đối số Reflection; phần tử thứ hai là mảng đã được đóng gói đúng kiểu.

## 11. Kết hợp boxing/unboxing có giới hạn quan trọng

### Không coi tất cả `Number` là có thể unbox thành mọi primitive

Dù `Integer` và `Long` cùng extends `Number`, Reflection chỉ dùng các wrapper primitive phù hợp cho unboxing. Một đối tượng `BigDecimal` **không** tự chuyển sang `double` theo `doubleValue()` chỉ vì nó extends `Number`.

```java
// Minh họa; không phải chương trình độc lập:
methodExpectingDouble.invoke(target, new java.math.BigDecimal("1.2"));
// IllegalArgumentException: không phải wrapper primitive hợp lệ để unbox
```

Nếu muốn nhận `Number` linh hoạt, phải tự xác định quy tắc chuyển đổi/độ chính xác và truyền một `Double` hoặc wrapper được cho phép tới method có formal `double`.

### Không có implicit narrowing dù giá trị “vừa”

```java
// Method đích có tham số byte:
methodExpectingByte.invoke(target, Integer.valueOf(5));
// IllegalArgumentException: int → byte bị cấm tự động dù 5 vừa byte.
```

Nếu có chính sách cho phép chuyển đổi thu hẹp, ứng dụng phải **chủ động kiểm tra miền giá trị**, ví dụ `value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE`, sau đó tạo `Byte.valueOf((byte) value)` trước khi invoke.

### Kiểu tham chiếu không được biến thành wrapper mới

```java
// Method đích khai báo Long, đối số là Integer:
methodExpectingLongObject.invoke(target, Integer.valueOf(8));
// IllegalArgumentException: Integer không assignable tới Long.
```

Khác với method đích khai báo `long`, trường hợp này **không** tự động thực hiện `Integer → int → long → Long`.

### Null và primitive

Nếu formal parameter là primitive, `null` không thể được unbox; lời gọi có thể ném `IllegalArgumentException`. Đối với reference parameter, `null` có thể được truyền qua, nhưng code đích vẫn có thể ném exception nếu tự dereference `null`.

## 12. Phân biệt lỗi tra cứu, lỗi chuyển đổi và lỗi quyền truy cập

| Hoạt động | Vấn đề | Kết quả tiêu biểu |
| --- | --- | --- |
| `getMethod("f", int.class)` khi chỉ có `f(long)` | Tìm sai signature; chưa gọi method | `NoSuchMethodException` |
| `getDeclaredField("missing")` | Không có field khai báo | `NoSuchFieldException` |
| `getInt` trên field `short` | Widening hợp lệ | Trả `int` |
| `getByte` trên field `short` | Narrowing bị cấm | `IllegalArgumentException` |
| `setInt` trên field `long` | Widening hợp lệ | Ghi giá trị |
| `setLong` trên field `int` | Narrowing bị cấm | `IllegalArgumentException` |
| `invoke` với `Long` cho tham số `int` | Narrowing bị cấm | `IllegalArgumentException` |
| `invoke` đúng đối số nhưng target ném lỗi | Lỗi thực thi business method | `InvocationTargetException` bọc cause |
| `newInstance` sai kiểu đối số | Không chuyển được theo quy tắc | `IllegalArgumentException` |
| Truy cập member không được phép | Access/JPMS | `IllegalAccessException` hoặc `InaccessibleObjectException` ở thao tác mở quyền |
| Receiver sai class hoặc `null` cho instance member | Truy cập sai đối tượng | `IllegalArgumentException` hoặc `NullPointerException` tùy API |

**Nên tách hai bước xử lý lỗi:**

1. **Tra cứu và kiểm tra metadata**: xác nhận field/method/constructor được chọn đúng, receiver đúng, access hợp lệ, kiểu formal và đối số có thể chuyển đổi.
2. **Đọc/ghi/gọi/tạo instance**: bắt lỗi Reflection theo API, đồng thời phân biệt exception do **code được gọi** phát sinh.

Không nên dựa vào việc catch `Exception` chung rồi suy đoán lỗi chuyển đổi khi thực tế lỗi đến từ quyền truy cập hoặc logic nghiệp vụ.

## 13. Tiền kiểm kiểu khi xử lý danh sách field/method

Một thư viện mapping hoặc batch runner không nên thử `invoke` với từng giá trị rồi chờ vỡ tại mục cuối. Có thể làm preflight:

1. Với mỗi member đã chọn, lấy **kiểu đích thực tế** qua `Field.getType()`, `Method.getParameterTypes()` hoặc `Constructor.getParameterTypes()`.
2. Đối với **kiểu đích tham chiếu**, xác nhận giá trị `null` hoặc `targetType.isInstance(value)`.
3. Đối với **kiểu đích primitive**, xác định wrapper nguồn (nếu giá trị khác `null`), unbox về primitive tương ứng rồi kiểm tra bảng widening ở mục 1.
4. Với typed `Field.getX/setX`, so sánh **kiểu primitive khai báo** với đúng chiều bảng ở mục 4/6.
5. Kiểm tra số lượng đối số, varargs array đã đóng gói, receiver, static/instance và quyền truy cập.
6. Chỉ khi tất cả entry đạt thì lập kế hoạch thực thi, đồng thời vẫn xử lý lỗi phát sinh khi thực thi.

Có thể tự cài một hàm `canWidenPrimitive(source, target)` dựa trên bảng ở mục 1; **đừng** dùng `target.isAssignableFrom(source)` cho primitive widening vì `isAssignableFrom` là quan hệ runtime type assignability, không phải bảng primitive widening.

Ví dụ:

```java
int.class.isAssignableFrom(short.class); // false: không kiểm tra widening
Number.class.isAssignableFrom(Integer.class); // true: subtype tham chiếu
```

Các mapper cần hỗ trợ chuyển từ text, enum, date/time hoặc numeric custom type phải định nghĩa chính sách chuyển đổi **ngoài** quy tắc Reflection và thông báo khi giá trị không thể chuyển đổi an toàn.

## 14. Checklist và câu hỏi tự kiểm tra

**Checklist:**

- Đã biết **kiểu field/formal parameter chính xác** và hướng chuyển đổi cần kiểm tra?
- Nếu là primitive, kiểu nguồn có nằm trong bảng widening của kiểu đích?
- Nếu là reference, giá trị có assignable hoặc `null` không? Có đang nhầm `Integer` với `Long`?
- Đã phân biệt `Field.get` (boxing) với `Field.getX` (widening) và chiều ngược `Field.setX`?
- `getMethod`/`getConstructor` có được tra cứu bằng signature khai báo chính xác?
- Varargs đã được đóng gói thành **một tham số mảng** chưa?
- Có xử lý access control, receiver, final/static và exception từ code đích không?

**Tự kiểm tra:**

1. Field khai báo `char` có đọc được bằng `getShort`, `getInt` và `getDouble` không? Vì sao?
2. Field khai báo `int` có nhận `Long.valueOf(3)` qua `Field.set` không, dù số 3 rất nhỏ?
3. Với constructor `Widget(double)`, truyền `Integer.valueOf(5)` cho `Constructor.newInstance` có hợp lệ không? Tra cứu bằng `getConstructor(int.class)` có tìm thấy constructor ấy không?
4. `Method.invoke` gọi method trả `long` thì trả về wrapper gì? Method trả `void` thì sao?
5. Vì sao `Field.get` một field `short` trả `Short` trong khi `Field.getInt` trên field đó trả `int`?
6. Một method có chữ ký `f(Long)` có nhận `Integer` tự động không? Nó khác `f(long)` ở đâu?
7. Với `String... args`, vì sao `Method.invoke(receiver, "a", "b")` có thể sai?
8. Trường hợp nào Reflection trả `InvocationTargetException` thay vì `IllegalArgumentException` khi gọi method?

### Nguồn tham khảo chính thức

- [Java SE 17: `Field`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html): getter/setter, widening và boxing.
- [Java SE 17: `Method`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html): `invoke`, chuyển đổi đối số và return value.
- [Java SE 17: `Constructor`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Constructor.html): `newInstance`, tham số và exception.
- [Java Language Specification 17 §5.1–5.3](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html): widening, boxing, unboxing và method invocation conversions.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-reference-004">Bảng Class, Type, AnnotatedType và các loại generic</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng tra cứu `Class`, `Type`, `AnnotatedType` và các loại generic trong Java Reflection

## 1. Vì sao cần một bảng phân loại kiểu?

Khi dùng Reflection để viết framework, serializer, trình sinh tài liệu API, bộ kiểm tra schema hoặc công cụ phân tích annotation, câu hỏi không chỉ là “đây là lớp nào?” mà còn là:

- Trường dữ liệu khai báo `List<String>` hay một `List` dùng raw type?
- Tham số `T` được khai báo ở đâu, ràng buộc bởi `extends Number` hay interface nào?
- `List<? extends Number>` và `List<? super Integer>` khác nhau ở biên nào?
- `T[]` và `List<String>[]` có gì khác `String[]`?
- Annotation như `@NotNull` hoặc `@Mark` được đặt **trên cách sử dụng kiểu** ở mức ngoài hay đặt vào type argument?

Java phân biệt ba tầng:

1. `Class<?>`: đại diện cho **kiểu runtime đã được nạp** (lớp, interface, kiểu mảng, primitive, `void`); kiểu generic thường đã bị erasure.
2. `java.lang.reflect.Type` và các subtype: diễn tả **kiểu khai báo** có thành phần generic, ví dụ `List<String>`, `T`, `? extends Number`, `T[]`.
3. `java.lang.reflect.AnnotatedType` và các subtype: diễn tả **một lần sử dụng kiểu có thể mang type-use annotation**, chẳng hạn `List<@Mark String>`. Nó cho phép truy vấn cả kiểu nền qua `getType()` và annotation gắn ở vị trí đó.

**Lưu ý quan trọng:** `AnnotatedType` **không kế thừa `Type`**; hai interface phục vụ hai lớp thông tin khác nhau. `AnnotatedType.getType()` trả lại một `Type` nền để tiếp tục phân tích.

## 2. Sơ đồ mối quan hệ

~~~text
java.lang.reflect.Type
  ├── java.lang.Class<?>              [Class implements Type]
  ├── ParameterizedType               [List<String>, Map<K,V>]
  ├── TypeVariable<D>                 [T, E, K, V]
  ├── WildcardType                     [? extends Number, ? super Integer]
  └── GenericArrayType                [T[], List<String>[]]

java.lang.reflect.AnnotatedElement
  └── AnnotatedType
        ├── AnnotatedParameterizedType
        ├── AnnotatedTypeVariable
        ├── AnnotatedWildcardType
        └── AnnotatedArrayType

AnnotatedType.getType() -> Type
~~~

Sơ đồ chỉ tóm tắt **các interface cần tra cứu**, không thể hiện mọi quan hệ triển khai trong JDK. `Class<?>` vừa triển khai `Type` vừa hỗ trợ các API annotation của `AnnotatedElement`, nhưng một đối tượng `Class<?>` không tự biến thành `AnnotatedType`.

Từ đó, **không** cast `Type` sang `AnnotatedType` theo suy đoán. Bạn phải lấy `AnnotatedType` từ API như `Field.getAnnotatedType()`, `Method.getAnnotatedReturnType()`, `Method.getAnnotatedParameterTypes()` hoặc `Class.getAnnotatedSuperclass()`.

## 3. Bảng nhận diện 5 hình dạng `Type`

| Giá trị kiểu khai báo | Kết quả `Type` thường gặp | API trọng tâm |
| --- | --- | --- |
| `String`, `int`, `String[]`, raw `List` | `Class<?>` | `getName()`, `getTypeName()`, `isArray()`, `getComponentType()`, `getTypeParameters()` |
| `List<String>`, `Map<String,Integer>`, `Parent<Long>` | `ParameterizedType` | `getRawType()`, `getActualTypeArguments()`, `getOwnerType()` |
| `T` của `class Box<T>` | `TypeVariable<?>` | `getName()`, `getBounds()`, `getGenericDeclaration()`, `getAnnotatedBounds()` |
| `?`, `? extends Number`, `? super Integer` | `WildcardType` | `getUpperBounds()`, `getLowerBounds()` |
| `T[]`, `List<String>[]` | `GenericArrayType` | `getGenericComponentType()` |

**Không suy luận bằng `getTypeName()` đơn thuần:** chuỗi kiểu giúp hiển thị, còn `instanceof` trên các interface `Type` cho biết cấu trúc để phân nhánh đúng.

Ví dụ:

~~~java
Type type = field.getGenericType();
if (type instanceof ParameterizedType p) {
    Type raw = p.getRawType();
    Type[] arguments = p.getActualTypeArguments();
} else if (type instanceof TypeVariable<?> variable) {
    Type[] bounds = variable.getBounds();
} else if (type instanceof GenericArrayType array) {
    Type component = array.getGenericComponentType();
} else if (type instanceof Class<?> clazz) {
    boolean isRuntimeArray = clazz.isArray();
}
~~~

*Đây là fragment minh họa: `field` là `java.lang.reflect.Field` đã tìm được và `Type` được import từ `java.lang.reflect`. `WildcardType` thường xuất hiện **bên trong** type argument của một `ParameterizedType`, chứ không nhất thiết trực tiếp là kiểu của cả field.*

### 3.1. `Class<?>`: kiểu runtime, kể cả primitive và array

`String.class`, `int.class`, `List.class`, `String[].class` đều là đối tượng `Class`, đồng thời triển khai `Type`. Đối với `Class` của mảng, `getComponentType()` trả kiểu thành phần ở **một chiều** (ví dụ `String[][].class.getComponentType() == String[].class`).

Đừng nhầm `List.class` với `List<String>`: **không tồn tại** cú pháp `List<String>.class`. Vì erasure, đối tượng `new ArrayList<String>().getClass()` vẫn chỉ cho biết runtime class `ArrayList`, không khôi phục `String` từ chính đối tượng đó.

`Class.getTypeParameters()` có thể mô tả **các biến kiểu khai báo của một generic class**, nhưng `Class<?>` của object **không mang giá trị type argument riêng cho instance**.

### 3.2. `ParameterizedType`: generic type đã được tham số hóa

Với field `List<String> names`, `field.getGenericType()` có thể là `ParameterizedType`:

- `getRawType()` → `List.class` (với ví dụ này).
- `getActualTypeArguments()[0]` → `String.class`.
- `getOwnerType()` → có thể là `null` với kiểu không cần owner; với kiểu lồng trong generic owner, cần quan sát chính xác owner type chứ đừng giả định mọi kiểu lồng đều như nhau.

Type argument **không nhất thiết** là `Class`. Nó có thể là `TypeVariable` (`List<T>`), `WildcardType` (`List<? extends Number>`), một `ParameterizedType` khác (`List<Map<String,Integer>>`) hoặc `GenericArrayType`.

Nếu đọc `ParameterizedType`, hãy **đệ quy** qua `getActualTypeArguments()` khi cần mô tả cấu trúc đầy đủ; dừng ở dạng nền (`Class`) hoặc biến kiểu.

### 3.3. `TypeVariable<?>`: biến kiểu tại nơi khai báo

Ví dụ:

~~~java
class NumericBox<T extends Number & Comparable<T>> {
    T value;
}
~~~

`NumericBox.class.getTypeParameters()[0]` là `TypeVariable` biểu diễn `T`. Nó biết:

- `getName()` → `T`.
- `getGenericDeclaration()` → declaration nơi `T` được giới thiệu (class, method hoặc constructor).
- `getBounds()` → các upper bounds đã khai báo; ở đây là `Number` và `Comparable<T>`.
- `getAnnotatedBounds()` → các bounds cùng type-use annotation nếu có.

Nếu không khai báo bounds, upper bound mặc định là `Object`. `TypeVariable` **không tự biết** rằng ở một subclass nào đó `T` đã được thay bằng `Long`. Muốn biết kiểu của `class LongBox extends NumericBox<Long>`, xem `LongBox.class.getGenericSuperclass()` rồi đọc type argument của `ParameterizedType` trả về.

**Bẫy phổ biến:** cast thẳng `TypeVariable.getBounds()[i]` về `Class` có thể sai, vì `Comparable<T>` được biểu diễn bằng `ParameterizedType`.

### 3.4. `WildcardType`: biên của wildcard

Với `List<? extends Number>`, type argument thứ nhất là `WildcardType`:

- `getUpperBounds()[0]` thường là `Number.class`.
- `getLowerBounds()` là mảng rỗng.

Với `List<? super Integer>`:

- `getLowerBounds()[0]` là `Integer.class`.
- `getUpperBounds()[0]` là `Object.class`.

Với `List<?>`, upper bound mặc định là `Object` và không có lower bound. **Không** suy ra từ một `? super Integer` rằng upper bound là `Integer`; đó là **lower bound**. Các giá trị của `WildcardType` mô tả ràng buộc trong *generic declaration*, không phải nội dung thực tế mà collection runtime đang chứa.

### 3.5. `GenericArrayType`: mảng có component không phải `Class` thuần

`T[]` trong generic class và `List<String>[]` trong chữ ký khai báo là các dạng `GenericArrayType` vì component không thể biểu diễn chỉ bằng một reifiable `Class<?>`.

Trong khi đó `String[]` và `int[][]` là `Class<?>` có `isArray() == true`. Hai API dùng để lấy một cấp thành phần:

| Trường hợp | Kiểu tại runtime Reflection | Lấy component |
| --- | --- | --- |
| `String[]` | `Class<?>` | `((Class<?>) t).getComponentType()` |
| `T[]` | `GenericArrayType` | `((GenericArrayType) t).getGenericComponentType()` |
| `List<String>[]` | `GenericArrayType` | `getGenericComponentType()` trả `ParameterizedType` |
| `List[]` (raw array) | `Class<?>` | `getComponentType()` trả `List.class` |

Java cấm trực tiếp `new List<String>[5]` do generic array creation không hợp lệ; điều đó **không** ngăn khai báo field hoặc tham số có kiểu `List<String>[]` để công cụ Reflection phân tích.

## 4. Bảng `AnnotatedType`: xem annotation nằm ở đâu

| Type-use metadata | Interface tương ứng | API xem thành phần đã chú thích |
| --- | --- | --- |
| Một vị trí sử dụng kiểu bất kỳ | `AnnotatedType` | `getType()`, `getAnnotation(...)`, `getAnnotations()`, `getAnnotatedOwnerType()` |
| `List<@Mark String>` | `AnnotatedParameterizedType` | `getAnnotatedActualTypeArguments()` |
| `T` tại một vị trí dùng generic variable | `AnnotatedTypeVariable` | `getAnnotatedBounds()` |
| `? extends @Mark Number` | `AnnotatedWildcardType` | `getAnnotatedUpperBounds()`, `getAnnotatedLowerBounds()` |
| `String @Mark []`, `T[]` | `AnnotatedArrayType` | `getAnnotatedGenericComponentType()` |

Annotation `@Target(ElementType.TYPE_USE)` có thể đặt lên vị trí sử dụng kiểu, ví dụ type argument hoặc array dimension. Để Reflection đọc được lúc chạy, annotation phải có retention `RetentionPolicy.RUNTIME`. Annotation `CLASS` hoặc `SOURCE` sẽ không hiển thị qua runtime Reflection như annotation có `RUNTIME`.

Cần phân biệt ba vị trí:

~~~java
@Mark("field-type") List<@Mark("argument") String> names;
~~~

*Fragment trên phải nằm trong một class, có định nghĩa `@Mark` dùng `TYPE_USE`; chương trình ở mục 7 cung cấp ví dụ hoàn chỉnh.*

- `Field.getAnnotatedType()` đại diện vị trí **kiểu của field**, trả annotation `field-type`.
- Nếu kiểu là `AnnotatedParameterizedType`, `getAnnotatedActualTypeArguments()[0]` đại diện vị trí **type argument**, nhận `argument`.
- `Field.getDeclaredAnnotations()` là **annotation được khai báo trên field** theo ngữ nghĩa declaration annotations, không thay thế cây `AnnotatedType`. Annotation có target cho cả FIELD và TYPE_USE có thể xuất hiện ở nhiều góc nhìn; hãy chọn API đúng mục đích.

Type-use annotation ở một cấp khác **không tự truyền xuống** type argument hoặc array component. Để biết annotation ở cấp nào, duyệt theo `AnnotatedType`, không chỉ xem kiểu chuỗi từ `getTypeName()`.

## 5. Tìm generic metadata từ đâu?

| Nơi khai báo | Đọc cấu trúc generic `Type` | Đọc type-use annotation |
| --- | --- | --- |
| Field | `Field.getGenericType()` | `Field.getAnnotatedType()` |
| Tham số method | `Method.getGenericParameterTypes()` | `Method.getAnnotatedParameterTypes()` |
| Return type method | `Method.getGenericReturnType()` | `Method.getAnnotatedReturnType()` |
| Ngoại lệ trong `throws` | `Method.getGenericExceptionTypes()` | `Method.getAnnotatedExceptionTypes()` |
| Parameter của constructor | `Constructor.getGenericParameterTypes()` | `Constructor.getAnnotatedParameterTypes()` |
| Generic superclass | `Class.getGenericSuperclass()` | `Class.getAnnotatedSuperclass()` |
| Generic interfaces | `Class.getGenericInterfaces()` | `Class.getAnnotatedInterfaces()` |
| Type parameter trên class | `Class.getTypeParameters()` | `TypeVariable.getAnnotatedBounds()` trên từng biến |
| Type parameter trên method | `Method.getTypeParameters()` | `TypeVariable.getAnnotatedBounds()` trên từng biến |

**Kiểm tra null và độ dài:** `Class.getGenericSuperclass()` có thể trả `null` (chẳng hạn cho `Object` hoặc interface); interface list có thể rỗng. `Method.getGenericExceptionTypes()` có thể rỗng. Khi duyệt annotation của generic superclass, nên xác thực có superclass trước.

Đối với kiểu khai báo trên parameter, `java.lang.reflect.Parameter` cũng cung cấp `getParameterizedType()` và `getAnnotatedType()`. Nếu cần tên parameter, lưu ý `Parameter.isNamePresent()` và tùy chọn biên dịch `-parameters`; không giả định source parameter names luôn được lưu.

## 6. Ví dụ đọc cây generic: lựa chọn nhánh theo `instanceof`

Khi viết công cụ xuất sơ đồ kiểu, hãy phân nhánh theo **hình dạng thực tế**:

~~~java
static String kind(Type type) {
    if (type instanceof Class<?>) return "Class";
    if (type instanceof ParameterizedType) return "ParameterizedType";
    if (type instanceof TypeVariable<?>) return "TypeVariable";
    if (type instanceof WildcardType) return "WildcardType";
    if (type instanceof GenericArrayType) return "GenericArrayType";
    return "Other Type";
}
~~~

*Fragment cần `import java.lang.reflect.*;` và đặt trong một class.*

Một bộ duyệt tổng quát có thể áp dụng:

1. `Class` → lấy `getTypeName()`; nếu là mảng, xử lý `getComponentType()`.
2. `ParameterizedType` → lấy `getRawType()`, `getOwnerType()` (nếu có), rồi duyệt `getActualTypeArguments()` theo thứ tự.
3. `TypeVariable` → lấy `getName()`, `getGenericDeclaration()` và duyệt `getBounds()` nếu muốn hiển thị ràng buộc.
4. `WildcardType` → duyệt upper/lower bounds.
5. `GenericArrayType` → duyệt `getGenericComponentType()` và thêm cấp mảng.

Khi duyệt đệ quy, phải nhận ra khả năng có **chu kỳ tham chiếu** qua biến kiểu, ví dụ `T extends Comparable<T>`. Một thuật toán đi sâu qua `TypeVariable.getBounds()` rồi trở lại `Comparable<T>` có thể tiếp tục gặp chính `T`. Dùng tập đang thăm (*active recursion path*) hoặc giới hạn/đánh dấu những biến kiểu đã xét để tránh đệ quy vô hạn.

Với `AnnotatedType`, cần **duyệt cây annotation song song**, không suy từ `Type` rằng annotation ở mọi cấp đều giống nhau.

## 7. Chương trình Java 17+ hoàn chỉnh: `Type` và `AnnotatedType` cùng lúc

Tạo tệp **`GenericTypeLookupDemo.java`**:

~~~java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.List;

public class GenericTypeLookupDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    public @interface Mark {
        String value();
    }

    public static class Holder<T extends Number & Comparable<T>> {
        public @Mark("outer") List<@Mark("element") String> list;
        public List<? extends Number> upper;
        public List<? super Integer> lower;
        public T[] values;
        public List<String>[] genericArray;
        public String[] ordinaryArray;
        public List rawList;
    }

    public static class LongHolder extends Holder<Long> {}

    static String kind(Type t) {
        if (t instanceof Class<?>) return "Class";
        if (t instanceof ParameterizedType) return "ParameterizedType";
        if (t instanceof TypeVariable<?>) return "TypeVariable";
        if (t instanceof WildcardType) return "WildcardType";
        if (t instanceof GenericArrayType) return "GenericArrayType";
        return "Other";
    }

    static Field field(String name) throws NoSuchFieldException {
        return Holder.class.getDeclaredField(name);
    }

    public static void main(String[] args) throws Exception {
        Type list = field("list").getGenericType();
        ParameterizedType parameterized = (ParameterizedType) list;
        System.out.println("list kind: " + kind(list));
        System.out.println("list raw: "
                + parameterized.getRawType().getTypeName());
        System.out.println("list argument: "
                + parameterized.getActualTypeArguments()[0].getTypeName());

        TypeVariable<?> variable = Holder.class.getTypeParameters()[0];
        System.out.println("variable: " + variable.getName());
        System.out.println("variable bounds: " + variable.getBounds().length);
        System.out.println("first bound: "
                + variable.getBounds()[0].getTypeName());

        WildcardType upper = (WildcardType)
                ((ParameterizedType) field("upper").getGenericType())
                        .getActualTypeArguments()[0];
        WildcardType lower = (WildcardType)
                ((ParameterizedType) field("lower").getGenericType())
                        .getActualTypeArguments()[0];
        System.out.println("upper bound: "
                + upper.getUpperBounds()[0].getTypeName());
        System.out.println("lower bound: "
                + lower.getLowerBounds()[0].getTypeName());

        Type values = field("values").getGenericType();
        System.out.println("T[] kind: " + kind(values));
        System.out.println("T[] component kind: " + kind(
                ((GenericArrayType) values).getGenericComponentType()));
        System.out.println("List<String>[] kind: "
                + kind(field("genericArray").getGenericType()));
        System.out.println("String[] kind: "
                + kind(field("ordinaryArray").getGenericType()));
        System.out.println("raw List kind: "
                + kind(field("rawList").getGenericType()));

        AnnotatedType annotated = field("list").getAnnotatedType();
        Mark outer = annotated.getAnnotation(Mark.class);
        AnnotatedType argument = ((AnnotatedParameterizedType) annotated)
                .getAnnotatedActualTypeArguments()[0];
        Mark element = argument.getAnnotation(Mark.class);
        System.out.println("outer annotation: " + outer.value());
        System.out.println("argument annotation: " + element.value());

        Type base = LongHolder.class.getGenericSuperclass();
        Type supplied = ((ParameterizedType) base)
                .getActualTypeArguments()[0];
        System.out.println("subclass binds T to: "
                + supplied.getTypeName());
    }
}
~~~

Chạy bằng JDK 17 trở lên:

~~~bash
javac GenericTypeLookupDemo.java
java GenericTypeLookupDemo
~~~

Kết quả:

~~~text
list kind: ParameterizedType
list raw: java.util.List
list argument: java.lang.String
variable: T
variable bounds: 2
first bound: java.lang.Number
upper bound: java.lang.Number
lower bound: java.lang.Integer
T[] kind: GenericArrayType
T[] component kind: TypeVariable
List<String>[] kind: GenericArrayType
String[] kind: Class
raw List kind: Class
outer annotation: outer
argument annotation: element
subclass binds T to: java.lang.Long
~~~

Bài học từ output:

- `field.getGenericType()` giữ `List<String>` dưới dạng `ParameterizedType`; type argument thứ nhất là `Class<String>`.
- `T` của `Holder<T extends Number & Comparable<T>>` là `TypeVariable` có **hai bounds**; một trong số đó là `Comparable<T>`, bản thân là `ParameterizedType`.
- Wildcard lấy bounds qua `WildcardType`, không phải qua `TypeVariable`.
- `T[]` và `List<String>[]` là `GenericArrayType`, còn `String[]` là `Class`.
- `List` raw type của field là `Class`, không phải `ParameterizedType`.
- `AnnotatedType` của field giữ `@Mark("outer")`; annotation trên type argument được lấy ở cấp `AnnotatedParameterizedType.getAnnotatedActualTypeArguments()`.
- `LongHolder.class.getGenericSuperclass()` mô tả `Holder<Long>`; từ đó mới lấy được `Long` đã gắn với type parameter `T` tại quan hệ kế thừa này.

Đây là ví dụ **khám phá metadata từ declaration**, không suy đoán type argument của `new ArrayList<String>()` tại runtime.

## 8. Bảng API `AnnotatedType` mở rộng

Các subtype của `AnnotatedType` phản ánh hình dạng kiểu ở từng vị trí. Một số điểm cần kiểm tra kỹ khi triển khai:

### 8.1. `AnnotatedParameterizedType`

Với `List<@Mark String>`, gọi `getAnnotatedActualTypeArguments()` để lấy từng type argument cùng annotation tương ứng. Nếu type argument lại là `Map<@Mark String, @Mark Integer>`, các phần tử bên trong có thể tiếp tục là `AnnotatedParameterizedType`. Đừng chỉ nhìn annotation ngoài cùng rồi bỏ mất annotation lồng.

### 8.2. `AnnotatedTypeVariable`

Khi một vị trí sử dụng kiểu là biến `T`, hình dạng annotated type có thể là `AnnotatedTypeVariable`. API `getAnnotatedBounds()` cho phép đọc annotation gắn vào bounds của biến. Cần nhớ type-use annotation ở **chỗ dùng** `T` và annotation ở **chỗ khai báo bound** là hai vị trí khác nhau.

### 8.3. `AnnotatedWildcardType`

Với `List<? extends @Mark Number>`, annotation ở `Number` được đọc từ `getAnnotatedUpperBounds()` của `AnnotatedWildcardType` bên trong annotated parameterized type. Tương tự, `? super @Mark Integer` dùng `getAnnotatedLowerBounds()`.

Điểm cần phân biệt: `WildcardType.getUpperBounds()` chỉ trả `Type[]`; nếu muốn annotation trên chính kiểu bound, cần nhánh `AnnotatedWildcardType`.

### 8.4. `AnnotatedArrayType`

Java cho phép type-use annotation tại **mỗi cấp mảng**; ví dụ cú pháp `String @Mark []` gắn annotation vào một vị trí mảng. `AnnotatedArrayType.getAnnotatedGenericComponentType()` đi vào cấp phần tử tiếp theo, có thể là một `AnnotatedArrayType` khác hoặc một `AnnotatedType` đại diện base component. Với mảng nhiều chiều, không đánh đồng annotation trên `[]` ngoài và annotation trên kiểu phần tử cuối.

### 8.5. `getAnnotatedOwnerType()`

Khi kiểu có owner (ví dụ một inner type nằm trong một kiểu bao), `AnnotatedType.getAnnotatedOwnerType()` (Java 9+) có thể cho biết annotated owner type hoặc `null` nếu không áp dụng. Không giả định mọi `ParameterizedType` đều có owner, hoặc owner luôn là `Class<?>`.

## 9. Phân biệt metadata generic, runtime erasure và reifiability

`Type` giúp đọc những gì còn lưu trong **generic signature** của field, method, constructor hoặc superclass. Nhưng Java generic vẫn chịu **type erasure** trong nhiều thao tác runtime.

Ví dụ:

~~~java
List<String> strings = new java.util.ArrayList<>();
List<Integer> numbers = new java.util.ArrayList<>();

boolean sameRuntimeClass =
        strings.getClass() == numbers.getClass(); // true
~~~

*Fragment này cần import `java.util.List` và đặt trong ngữ cảnh cho phép khai báo biến.*

Hai object đều là instance của cùng runtime class `ArrayList`; chỉ dựa `object.getClass()` không thể biết một biến source được khai báo `List<String>` hay `List<Integer>`.

Một số lưu ý:

- Generic type argument được lưu tại **vị trí khai báo** thích hợp; không phải mọi thông tin generic đều tồn tại sau compilation, và compiler có thể bỏ thông tin không được phát ra dưới dạng chữ ký có thể đọc.
- `List<String>.class` không hợp lệ; `List.class` hợp lệ.
- `instanceof List<String>` không hợp lệ trong Java thông thường vì dạng đó không reifiable; `instanceof List<?>` có thể hợp lệ.
- `String[]` có runtime array class riêng (`String[].class`), nhưng `List<String>[]` là khai báo kiểu generic array; không thể tạo trực tiếp `new List<String>[3]`.
- `Type.getTypeName()` tiện cho log, nhưng **không phải định danh runtime toàn cục**. Hai class cùng binary name được nạp bởi hai defining class loader khác nhau vẫn là các kiểu khác nhau.

Khi API có thể trả `TypeVariable` hoặc `ParameterizedType`, **đừng ép kiểu bừa sang `Class<?>`**. Nếu phải lấy runtime class gần đúng, cần một quy tắc rõ về erasure/bounds và ý nghĩa với wildcard; việc đơn giản hóa này có thể làm mất thông tin và không phù hợp để tái dựng chính xác generic signature.

## 10. Lỗi thường gặp khi xây bộ tra cứu

| Sai lầm | Tại sao sai | Cách sửa |
| --- | --- | --- |
| Cast mọi `Field.getGenericType()` sang `Class<?>` | Field generic có thể là `ParameterizedType`, `TypeVariable`, `GenericArrayType` | Phân nhánh `instanceof` theo `Type` |
| Dùng `Field.getType()` để đọc `List<String>` | `getType()` phản ánh kiểu erasure `List.class` | Dùng `getGenericType()` |
| Dùng `getClass()` của object để suy ra `List<String>` | Erasure xóa type argument ở object runtime class | Đọc generic declaration/signature nếu có |
| Cho rằng `? super Integer` có upper bound `Integer` | `Integer` là **lower bound** | Xem `getLowerBounds()` và `getUpperBounds()` |
| Coi `T[]` luôn là `Class` | Component `T` không reifiable | Dùng `GenericArrayType` khi Reflection trả dạng này |
| Dùng `getDeclaredAnnotations()` để tìm mọi type-use annotation | Declaration annotations và annotation trên cây type-use khác vị trí | Dùng `getAnnotatedType()` rồi duyệt annotated arguments/bounds |
| Bỏ qua type-use annotation ở type argument | Annotation có thể nằm bên trong generic nesting | Duyệt `AnnotatedParameterizedType` |
| Duyệt bounds `T extends Comparable<T>` vô hạn | Quay lại cùng type variable `T` | Theo dõi những biến đang duyệt |
| Dùng `getSimpleName()` làm khóa cho mọi kiểu | Dễ trùng package; kiểu local/anonymous khó mô tả | Dùng key có cấu trúc và namespace phù hợp |
| Tin rằng annotation SOURCE/CLASS hiện diện lúc chạy | Retention không phải `RUNTIME` | Chỉ kỳ vọng các annotation được lưu để runtime Reflection đọc |
| Bỏ qua lỗi generic signature | Metadata bytecode có thể thiếu dependency hoặc signature không hợp lệ | Xử lý `TypeNotPresentException`, `MalformedParameterizedTypeException`, `GenericSignatureFormatError` theo ngữ cảnh |

Một framework nên tách **khâu phát hiện kiểu**, **đọc annotation theo vị trí**, **chuẩn hóa/hiển thị**, **giải quyết tên class** và **xử lý lỗi** thành những bước độc lập để dễ kiểm tra.

## 11. Chọn API theo câu hỏi thực tế

- **“Tôi cần biết field có runtime class gì?”** → `Field.getType()`.
- **“Tôi cần biết field khai báo `List<String>` hay `List<T>`?”** → `Field.getGenericType()` rồi phân nhánh `Type`.
- **“Tôi cần annotation trên kiểu của field và trong type argument?”** → `Field.getAnnotatedType()`; duyệt `AnnotatedParameterizedType` hoặc subtype tương ứng.
- **“Tôi cần generic return type của method?”** → `Method.getGenericReturnType()`.
- **“Tôi cần type-use annotation trên giá trị trả về?”** → `Method.getAnnotatedReturnType()`.
- **“Tôi cần bounds của generic parameter `T`?”** → `Class.getTypeParameters()` hoặc `Method.getTypeParameters()` rồi `TypeVariable.getBounds()` / `getAnnotatedBounds()`.
- **“Tôi muốn xem `T` trong superclass được gắn với `Long` như thế nào?”** → `Subclass.class.getGenericSuperclass()` và `ParameterizedType.getActualTypeArguments()`.
- **“Tôi muốn phân biệt `? extends X` với `? super X`?”** → `WildcardType.getUpperBounds()` / `getLowerBounds()`.
- **“Tôi cần xử lý `T[]`?”** → `GenericArrayType.getGenericComponentType()`.
- **“Tôi cần xem array type reifiable `String[]`?”** → `Class.isArray()` / `Class.getComponentType()`.
- **“Tôi muốn giữ đầy đủ nested annotation?”** → xây traversal song song giữa `AnnotatedType` và `Type`, không chỉ dùng một chuỗi tên.

## 12. Gợi ý kiểm thử bộ duyệt kiểu trong dự án

Để bộ phân tích generic hoạt động đáng tin, nên có fixture bao gồm:

1. **Raw và parameterized:** `List`, `List<String>`, `Map<String, List<Integer>>`.
2. **Type variable:** `class Box<T>`, `<T extends Number & Comparable<T>>`.
3. **Wildcard:** `List<?>`, `List<? extends Number>`, `List<? super Integer>`.
4. **Array:** `String[]`, `T[]`, `List<String>[]`.
5. **Type-use annotation nhiều cấp:** `@Mark List<@Mark String>`, annotation ở wildcard bound và array dimension.
6. **Subclass substitution:** `class StringBox extends Box<String>`.
7. **Nesting và owner:** generic member type khi code của bạn hỗ trợ.
8. **Null/rỗng:** class không có generic superclass, không có type parameters, annotation không có retention runtime.
9. **Malformed/missing types:** nếu công cụ đọc bytecode ngoài kiểm soát, xử lý lỗi phản chiếu thay vì crash không rõ nguyên nhân.
10. **Chu kỳ bounds:** `T extends Comparable<T>` không làm traversal đệ quy vô hạn.

Assertion nên dựa vào **hình dạng `Type`, tên/bounds và đúng vị trí annotation** thay vì dựa vào chuỗi `toString()` tình cờ, thứ tự không được đảm bảo của mảng members hay class loader identity hash code.

## 13. Tự kiểm tra

1. `Class<?>` có triển khai `Type` không? **Có.**
2. `AnnotatedType` có phải subtype của `Type` không? **Không; `getType()` trả `Type` nền.**
3. `List<String>` thường được mô tả bằng interface nào khi gọi `Field.getGenericType()`? **`ParameterizedType`.**
4. `List<?>` chứa kiểu gì trong `getActualTypeArguments()[0]`? **`WildcardType`.**
5. `T` của `class Box<T>` là interface gì? **`TypeVariable<?>`.**
6. `T[]` có nhất thiết là `Class<?>` không? **Không; nó thường là `GenericArrayType`.**
7. `String[]` thì sao? **`Class<?>` với `isArray() == true`.**
8. Trong `List<@Mark String>`, lấy annotation của type argument bằng API nào? **`AnnotatedParameterizedType.getAnnotatedActualTypeArguments()[0].getAnnotation(Mark.class)`.**
9. `? super Integer` có lower bound là gì? **`Integer.class`; upper bound mặc định thường là `Object.class`.**
10. `new ArrayList<String>().getClass()` có trực tiếp biết `String` không? **Không; generic type argument bị erasure khỏi runtime class của object.**
11. Với `T extends Comparable<T>`, tại sao traversal ngây thơ có thể vòng lặp? **Bounds lại tham chiếu cùng `TypeVariable T`.**
12. `@Target(TYPE_USE)` đủ để Reflection đọc annotation lúc chạy chưa? **Chưa; cần retention `RUNTIME`.**

## 14. Kết luận

Hãy bắt đầu bằng **nguồn khai báo**: đọc `getGenericType` / `getGenericReturnType` / `getGenericSuperclass` khi cần cấu trúc kiểu, và đọc `getAnnotatedType` / `getAnnotatedReturnType` / `getAnnotatedSuperclass` khi cần annotation theo **vị trí sử dụng kiểu**. Sau đó phân nhánh đúng theo `Class`, `ParameterizedType`, `TypeVariable`, `WildcardType`, `GenericArrayType`; song song duyệt các subtype `AnnotatedType` để không làm mất annotation ở type argument, wildcard bound hoặc chiều mảng. Việc đọc generic signature từ Reflection giúp hiểu declaration, **không xóa được quy tắc type erasure** của đối tượng runtime.

### Tài liệu tham khảo

- [Java SE 21 — `Type`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Type.html)
- [Java SE 21 — `ParameterizedType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/ParameterizedType.html)
- [Java SE 21 — `TypeVariable`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/TypeVariable.html)
- [Java SE 21 — `WildcardType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/WildcardType.html)
- [Java SE 21 — `GenericArrayType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/GenericArrayType.html)
- [Java SE 21 — `AnnotatedType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AnnotatedType.html)
- [Java SE 21 — `AnnotatedParameterizedType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AnnotatedParameterizedType.html)
- [Java SE 21 — `AnnotatedTypeVariable`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AnnotatedTypeVariable.html)
- [Java SE 21 — `AnnotatedWildcardType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AnnotatedWildcardType.html)
- [Java SE 21 — `AnnotatedArrayType`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AnnotatedArrayType.html)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-reference-005">Bảng annotation trực tiếp, gián tiếp và được kế thừa</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng tra cứu: annotation trực tiếp, gián tiếp và được kế thừa trong Java Reflection

Một annotation viết trên class, method hoặc field chưa chắc được trả về từ mọi API tra cứu annotation. Kết quả phụ thuộc vào **annotation có tồn tại trong metadata runtime hay không**, **nó được gắn trực tiếp hay nằm bên trong repeatable container**, **đối tượng đang hỏi là class hay member**, và **loại annotation có `@Inherited` hay không**.

Java định nghĩa bốn thuật ngữ có ý nghĩa chính xác: **directly present**, **indirectly present**, **present** và **associated**. Nắm được bốn thuật ngữ này sẽ giúp bạn lựa chọn giữa `getAnnotation`, `getDeclaredAnnotation`, `getAnnotationsByType` và `getDeclaredAnnotationsByType` mà không phải đoán từ tên API.

> **Quy tắc nhanh:** Muốn tìm một annotation có thể được kế thừa, dùng `getAnnotation(T.class)`; muốn tìm annotation lặp lại **và** kế thừa, dùng `getAnnotationsByType(T.class)`. Muốn **chỉ tại phần tử hiện tại**, dùng các biến thể `getDeclared...` tương ứng. Nhưng trước hết annotation phải có `RetentionPolicy.RUNTIME` để Reflection chuẩn nhìn thấy.

## 1. Điều kiện tiên quyết: annotation còn tồn tại ở runtime

`@Retention` quy định chính sách giữ annotation sau biên dịch:

| Retention policy | Tồn tại trong class file | Reflection thông thường đọc bằng `getAnnotation(...)` |
|---|---|---|
| `RetentionPolicy.SOURCE` | Không giữ annotation gắn lên element trong class file | Không |
| `RetentionPolicy.CLASS` | Giữ trong class file nhưng không runtime-visible | Không |
| `RetentionPolicy.RUNTIME` | Giữ trong class file và runtime-visible | Có, nếu đúng element/cách tra cứu |

`CLASS` là retention mặc định nếu không khai báo `@Retention`. Với annotation dùng để điều khiển hành vi của framework tại runtime, thường cần viết rõ `@Retention(RetentionPolicy.RUNTIME)`.

Ví dụ:

~~~java
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Category {
    String value();
}
~~~

Đây là **mảnh khai báo**, không phải chương trình có `main`. `@Target(ElementType.TYPE)` nói rằng annotation gắn được lên declaration của class, interface, enum hoặc annotation type; nó **không đồng nghĩa** annotation tự được kế thừa từ interface.

Đọc annotation trên một class là:

~~~java
Category category = SomeClass.class.getAnnotation(Category.class);
~~~

Đoạn này giả định `SomeClass` đã được khai báo và được đặt `@Category(...)`; nó không tự chạy độc lập. Nếu kết quả là `null`, nguyên nhân có thể là annotation vắng mặt, `retention` không phù hợp, annotation ở một element khác hoặc không thỏa quy tắc kế thừa.

## 2. Bốn khái niệm: trực tiếp, gián tiếp, hiện diện, liên kết

Theo hợp đồng của `java.lang.reflect.AnnotatedElement` trong Java 17:

| Thuật ngữ Javadoc | Diễn giải | Ví dụ đặc trưng |
|---|---|---|
| **Directly present** (hiện diện trực tiếp) | Metadata runtime-visible trên chính element chứa instance annotation đó | `@Role("base")` gắn trực tiếp trên `Base` |
| **Indirectly present** (hiện diện gián tiếp) | Một repeatable annotation nằm **trong annotation container** trực tiếp trên element | Hai `@Label` trên `Base` được biểu diễn qua `@Labels` container |
| **Present** (hiện diện) | Directly present; hoặc annotation có tính kế thừa được tìm trên chuỗi superclass khi không có instance cùng loại trực tiếp trên class | `Child` không có `@Role` riêng nhưng `getAnnotation(Role.class)` thấy `@Role` của `Base` |
| **Associated** (liên kết) | Directly **hoặc** indirectly present; hoặc được kế thừa từ superclass khi không có trực tiếp/gián tiếp cùng loại trên class | `Child.getAnnotationsByType(Label.class)` trả hai nhãn của `Base` |

Trong trường hợp annotation **không repeatable**, không có bước mở container, nên sự khác biệt lớn chủ yếu là **declared/local** so với **kế thừa**. Với annotation **repeatable**, phải phân biệt cả hai trục:

~~~text
                              Có unwrap repeatable container?
                                  KHÔNG            CÓ
                               +-----------+--------------------+
Chỉ trên chính element         |  Declared | Declared ByType    |
Không đi lên superclass        |  Annotation|                    |
                               +-----------+--------------------+
Cho phép @Inherited             | Annotation| AnnotationsByType  |
trên chuỗi superclass          +-----------+--------------------+
~~~

Tên các ô trong sơ đồ rút gọn từ tên method `get...`. “Cho phép @Inherited” **không** có nghĩa method sẽ tự lấy annotation của interface hoặc method được override.

### Ví dụ trực tiếp và gián tiếp

~~~java
@Label("A")
@Label("B")
class Base {}
~~~

Ở Java hỗ trợ repeatable annotations, khi gắn nhiều `@Label`, compiler có thể đặt các nhãn bên trong annotation container `@Labels`. Vì vậy, ngay trên `Base`:

- `getDeclaredAnnotation(Label.class)` trả `null` nếu chỉ container `Labels` trực tiếp hiện diện.
- `getDeclaredAnnotationsByType(Label.class)` trả cả `Label("A")` và `Label("B")` nhờ “mở” container.
- `getAnnotation(Label.class)` cũng có thể trả `null` cho hai nhãn ở trong container, dù annotation nhìn rất rõ trong mã nguồn.
- `getAnnotationsByType(Label.class)` trả hai nhãn và còn có thể đi lên superclass theo `@Inherited` khi không có nhãn hiện diện trực tiếp/gián tiếp trên class con.

Ngược lại, với **một** `@Label("C")`, annotation thường trực tiếp hiện diện dưới loại `Label`: `getAnnotation(Label.class)` có thể trả chính nhãn đó.

**Quan trọng:** “Gián tiếp” ở đây **không có nghĩa** “annotation lấy từ superclass” theo cách dùng từ đời thường. Trong Javadoc `AnnotatedElement`, *indirectly present* dành cho repeatable annotation trong **container**. Kế thừa là quy tắc khác, tham gia vào *present* hoặc *associated*.

## 3. Bảng API đầy đủ: mỗi method nhận loại presence nào?

| API trên `AnnotatedElement` | Loại presence được xét | Mở container repeatable? | Có thể đi lên superclass? | Kết quả khi không tìm thấy |
|---|---|---|---|---|
| `getAnnotation(T.class)` | **Present** | Không | Có, với `@Inherited` trên class | `null` |
| `getAnnotations()` | **Present** cho từng loại annotation trực tiếp/kế thừa | Không mở riêng repeatable container | Có, với loại `@Inherited` | Mảng rỗng |
| `getDeclaredAnnotation(T.class)` | **Directly present** | Không | Không | `null` |
| `getDeclaredAnnotations()` | **Directly present** | Không mở riêng container | Không | Mảng rỗng |
| `getAnnotationsByType(T.class)` | **Associated** | **Có** | Có, với `@Inherited` trên class | Mảng rỗng |
| `getDeclaredAnnotationsByType(T.class)` | **Directly/indirectly present** | **Có** | Không | Mảng rỗng |
| `isAnnotationPresent(T.class)` | Tương đương `getAnnotation(T.class) != null` | Không | Có, với `@Inherited` trên class | `false` |

Đây là bảng theo **ngữ nghĩa Java 17** của `AnnotatedElement`. Tùy loại đối tượng, các method xuất hiện trên `Class`, `Method`, `Field`, `Constructor`, `Parameter`, `Package`...; nhưng **cơ chế `@Inherited` chỉ đi lên chuỗi class superclass**, không áp dụng chung cho mọi `AnnotatedElement`.

Trong hai method `get...AnnotationsByType(T.class)`, nếu `T` là repeatable annotation, Java tìm annotation container thích hợp, mở mảng `value()` trong container rồi trả các annotation con. Khi cùng element có nhiều annotation cùng loại, thứ tự được xác định như thể các annotation trong container xuất hiện tại vị trí container, theo thứ tự `value()`.

### Thí dụ chọn API đúng nhu cầu

| Câu hỏi | API phù hợp |
|---|---|
| Class có một `@Service` trực tiếp hoặc được kế thừa? | `type.getAnnotation(Service.class)` |
| Class **tự khai báo** `@Service`, bỏ qua superclass? | `type.getDeclaredAnnotation(Service.class)` |
| Class có những `@Route` nào (có thể nhiều và có kế thừa)? | `type.getAnnotationsByType(Route.class)` |
| Chỉ những `@Route` khai báo ngay trên class, kể cả repeatable? | `type.getDeclaredAnnotationsByType(Route.class)` |
| Liệt kê annotations trực tiếp ở class để hiển thị metadata thô? | `type.getDeclaredAnnotations()`, nhớ rằng có thể thấy container thay vì nhãn con |
| Liệt kê annotation đã được gắn trực tiếp hoặc được kế thừa? | `type.getAnnotations()`, không đồng nghĩa với flatten repeatables |
| Kiểm tra annotation trên method được override? | `method.getAnnotation(...)` trên đúng đối tượng `Method`, không giả định `@Inherited` hoạt động |

Trong code tổng quát, đừng dùng `getAnnotations()` rồi chỉ so tên annotation để “giải” repeatables nếu đã có `getAnnotationsByType(T.class)`. API ByType thể hiện chính xác nhu cầu và giúp xử lý container được định nghĩa bởi `@Repeatable`.

## 4. `@Inherited` hoạt động ở đâu và dừng khi nào?

`java.lang.annotation.Inherited` là một **meta-annotation** đặt trên declaration của annotation type. Nó cho phép tra cứu annotation từ **superclass của một class**, theo những quy tắc ở bảng trên. Ví dụ:

~~~java
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Role {
    String value();
}

@Role("base")
class Base {}

class Child extends Base {}
~~~

Đây là đoạn ví dụ minh họa cần imports như chương trình ở mục 6. `Child` không trực tiếp có `@Role`, nhưng `Child.class.getAnnotation(Role.class)` trả `Role("base")`, trong khi `getDeclaredAnnotation(Role.class)` trả `null`.

Các giới hạn rất quan trọng:

1. **Không thông qua interface:** Nếu interface `API` có `@Role` và class `Service implements API`, `Service.class.getAnnotation(Role.class)` **không** tự nhận `Role` từ interface. `Class.getInterfaces()` cho phép bạn tự duyệt nếu đó là chính sách framework, nhưng **đó là thuật toán riêng, không phải @Inherited mặc định**.
2. **Không áp dụng cho method/field/constructor:** `@Inherited` không làm method được override nhận annotation từ method ở base class. Nếu gọi `getMethod(...)` trên subclass mà nhận được `Method` do superclass khai báo (vì subclass **không override**), annotation có thể xuất hiện vì **bạn đang hỏi chính method của superclass**, không phải do annotation trên method được kế thừa.
3. **Subclass có annotation cùng loại:** Nếu một class trực tiếp khai báo `@Role("child")`, tra cứu `Role` trên subclass trả giá trị child, không cộng thêm base.
4. **Kế thừa theo loại annotation, không phải tổng hợp mọi instance:** Với `getAnnotationsByType`, nếu subclass đã có annotation cùng loại trực tiếp hoặc gián tiếp, API lấy những instance ở subclass; nó **không nối tự động** danh sách của subclass với superclass.
5. **`@Inherited` không làm thay đổi class file source của subclass:** Nó thay đổi logic tra cứu Reflection; `getDeclaredAnnotation` vẫn phân biệt rõ gì được khai báo ngay tại subclass.

Một hệ thống cần kế thừa annotation từ interface, method override hoặc tất cả superclass theo chính sách merge phải tự định nghĩa traversal, resolution order, xung đột và ưu tiên. Không thể gọi `getAnnotation` một lần rồi giả định JVM đã thực hiện chính sách tùy biến.

## 5. Repeatable annotations, container và kết hợp với inheritance

Một annotation type có thể lặp lại bằng `@Repeatable(Container.class)`:

~~~java
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(Labels.class)
@interface Label {
    String value();
}

@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Labels {
    Label[] value();
}
~~~

Đoạn này là **mảnh khai báo** với imports thích hợp. `Labels` là container annotation. Nếu `Label` được đánh dấu `@Inherited`, **container `Labels` cũng phải được đánh dấu `@Inherited`** theo ràng buộc ngôn ngữ; việc quên điều này có thể làm chương trình không biên dịch.

Một số quy tắc:

- Chữ ký của container phải có element tên `value` với kiểu `Label[]`. Retention của container không được ngắn hơn retention của annotation được chứa; target của container phải tương thích.
- Khi viết hai `@Label` trên cùng một class, compiler thường lưu container `@Labels` thay cho hai annotation instance loại `Label` trực tiếp ở metadata runtime.
- `getAnnotationsByType(Label.class)` nhận cả annotation **directly** và **indirectly** present, nên phù hợp cho các loại repeatable.
- `getAnnotation(Label.class)` không tự mở container. Một lần hỏi `Label.class` trên class có hai nhãn lặp lại có thể trả `null`.
- **Local shadows inherited:** Khi superclass có `@Label("A")`, `@Label("B")` và subclass có `@Label("C")`, `subclass.getAnnotationsByType(Label.class)` trả `[C]`, **không** `[C, A, B]`.
- Trật tự của các annotation cùng loại được trả qua `...ByType` dựa trên thứ tự trong container tại vị trí container, thay vì thứ tự sắp xếp tùy ý. Nếu muốn output canonical của một tool, có thể sắp xếp riêng theo một khóa nghiệp vụ sau khi đọc, nhưng phải công bố quy tắc.

**Cảnh báo về container và inheritance:** Container cũng là một annotation type độc lập khi tra cứu metadata. Đừng trộn việc tra cứu `Labels.class` với `Label.class` rồi suy ra cùng một quy tắc merge. Muốn hỏi “tất cả nhãn Label gắn/được kế thừa theo hợp đồng repeatable”, hãy trực tiếp gọi `getAnnotationsByType(Label.class)`.

## 6. Chương trình Java 17+ minh họa và kiểm chứng kết quả

Lưu toàn bộ mã thành **`AnnotationPresenceDemo.java`**. Code không cần thư viện ngoài JDK. Chúng ta sẽ quan sát:

- `Role`: annotation không repeatable và có `@Inherited`.
- `Local`: annotation không có `@Inherited`.
- `Label`: annotation repeatable và có `@Inherited`, cùng container `Labels`.
- `Base` có hai `Label`, `Child` không gắn annotation mới, `OverrideChild` có annotation của chính mình.
- Interface `AnnotatedInterface` có `@Role` nhưng class triển khai không tự kế thừa annotation đó.
- Method `act` có `@MethodNote` ở base; subclass override không tự nhận, trong khi subclass không override vẫn truy xuất `Method` của base.

~~~java
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.util.Arrays;

public class AnnotationPresenceDemo {
    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Role {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Local {
        String value();
    }

    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Repeatable(Labels.class)
    public @interface Label {
        String value();
    }

    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Labels {
        Label[] value();
    }

    @Role("base")
    @Local("local")
    @Label("A")
    @Label("B")
    public static class Base {}

    public static class Child extends Base {}

    @Role("child")
    @Label("C")
    public static class OverrideChild extends Base {}

    @Role("interface")
    public interface AnnotatedInterface {}

    public static class Implementation implements AnnotatedInterface {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface MethodNote {}

    public static class ParentMethods {
        @MethodNote
        public void act() {}
    }

    public static class OverridingMethods extends ParentMethods {
        @Override
        public void act() {}
    }

    public static class NonOverridingMethods extends ParentMethods {}

    private static String role(Role annotation) {
        return annotation == null ? "<none>" : annotation.value();
    }

    private static String labels(Label[] annotations) {
        return Arrays.toString(
                Arrays.stream(annotations).map(Label::value).toArray(String[]::new));
    }

    private static void report(String title, Class<?> type) {
        System.out.println(title);
        System.out.println("  Role getAnnotation = " +
                role(type.getAnnotation(Role.class)));
        System.out.println("  Role getDeclaredAnnotation = " +
                role(type.getDeclaredAnnotation(Role.class)));
        System.out.println("  Local getAnnotation = " +
                (type.getAnnotation(Local.class) != null));
        System.out.println("  Label getAnnotation = " +
                (type.getAnnotation(Label.class) != null));
        System.out.println("  Label getDeclaredAnnotationsByType = " +
                labels(type.getDeclaredAnnotationsByType(Label.class)));
        System.out.println("  Label getAnnotationsByType = " +
                labels(type.getAnnotationsByType(Label.class)));
    }

    public static void main(String[] args) throws NoSuchMethodException {
        report("Base", Base.class);
        report("Child", Child.class);
        report("OverrideChild", OverrideChild.class);
        report("Implementation", Implementation.class);

        boolean parent = ParentMethods.class.getMethod("act")
                .isAnnotationPresent(MethodNote.class);
        boolean overriding = OverridingMethods.class.getMethod("act")
                .isAnnotationPresent(MethodNote.class);
        boolean nonOverriding = NonOverridingMethods.class.getMethod("act")
                .isAnnotationPresent(MethodNote.class);
        System.out.println("method ParentMethods = " + parent);
        System.out.println("method OverridingMethods = " + overriding);
        System.out.println("method NonOverridingMethods = " + nonOverriding);
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac --release 17 -encoding UTF-8 AnnotationPresenceDemo.java
java AnnotationPresenceDemo
~~~

**Stdout đã kiểm chứng:**

~~~text
Base
  Role getAnnotation = base
  Role getDeclaredAnnotation = base
  Local getAnnotation = true
  Label getAnnotation = false
  Label getDeclaredAnnotationsByType = [A, B]
  Label getAnnotationsByType = [A, B]
Child
  Role getAnnotation = base
  Role getDeclaredAnnotation = <none>
  Local getAnnotation = false
  Label getAnnotation = false
  Label getDeclaredAnnotationsByType = []
  Label getAnnotationsByType = [A, B]
OverrideChild
  Role getAnnotation = child
  Role getDeclaredAnnotation = child
  Local getAnnotation = false
  Label getAnnotation = true
  Label getDeclaredAnnotationsByType = [C]
  Label getAnnotationsByType = [C]
Implementation
  Role getAnnotation = <none>
  Role getDeclaredAnnotation = <none>
  Local getAnnotation = false
  Label getAnnotation = false
  Label getDeclaredAnnotationsByType = []
  Label getAnnotationsByType = []
method ParentMethods = true
method OverridingMethods = false
method NonOverridingMethods = true
~~~

### Tại sao kết quả lại như vậy?

**`Base`:** `Role` và `Local` được khai báo trực tiếp nên `getAnnotation` tìm thấy. Hai `Label` được đặt trong container repeatable; vì vậy `getAnnotation(Label.class)` là `null`, nhưng cả hai API `...ByType` nhận `[A, B]`.

**`Child`:** không khai báo `Role` hoặc `Label` mới. `getAnnotation(Role.class)` đi lên `Base` vì `Role` có `@Inherited`, nhưng `getDeclaredAnnotation(Role.class)` không đi lên nên là `null`. `Local` không có `@Inherited` nên không thấy. `getDeclaredAnnotationsByType(Label.class)` là `[]`, trong khi `getAnnotationsByType(Label.class)` nhận `[A, B]` từ `Base`.

**`OverrideChild`:** có `@Role("child")` và `@Label("C")` trực tiếp; chúng che các giá trị cùng loại ở `Base` trong những phép tra cứu hiện diện/liên kết tương ứng. Một `Label` đơn lẻ trả về qua `getAnnotation(Label.class)`, và `getAnnotationsByType(Label.class)` là `[C]` chứ không ghép với `[A, B]`.

**`Implementation`:** chỉ `implements AnnotatedInterface`. Dù interface có `@Role("interface")` và `Role` có `@Inherited`, nó không được tìm qua cạnh `implements`, nên tất cả truy vấn `Role` trên `Implementation` đều vắng.

**`ParentMethods` / `OverridingMethods` / `NonOverridingMethods`:** method ở base trực tiếp có `@MethodNote`. Khi subclass override method bằng declaration mới không có annotation, Reflection trên `Method` mới không nhận được `@MethodNote`. Với subclass không override, `Class.getMethod("act")` trả về method public do superclass khai báo; kết quả `true` là annotation **của Method gốc**, không phải annotation đã “di truyền” sang một method mới.

## 7. Ma trận kết quả trọng tâm để tra nhanh

Từ chương trình phía trên, ta có hai bảng:

### 7.1. Annotation đơn (`Role`) và annotation không kế thừa (`Local`)

| Đối tượng `Class` | `getAnnotation(Role.class)` | `getDeclaredAnnotation(Role.class)` | `getAnnotation(Local.class)` |
|---|---|---|---|
| `Base` | `base` | `base` | Có |
| `Child` | `base` (kế thừa) | `null` | Không |
| `OverrideChild` | `child` | `child` | Không |
| `Implementation` | `null` (không lấy từ interface) | `null` | Không |

### 7.2. Annotation repeatable (`Label`)

| Đối tượng `Class` | `getAnnotation(Label.class)` | `getDeclaredAnnotationsByType(Label.class)` | `getAnnotationsByType(Label.class)` |
|---|---|---|---|
| `Base`: hai `@Label` | `null` | `[A, B]` | `[A, B]` |
| `Child`: không khai báo | `null` | `[]` | `[A, B]` (kế thừa) |
| `OverrideChild`: một `@Label("C")` | `Label("C")` | `[C]` | `[C]` |
| `Implementation` | `null` | `[]` | `[]` |

Bảng dùng cách viết `[A, B]` để chỉ **giá trị của `Label.value()`**, không phải chính chuỗi `toString()` của annotation proxy. Khi cần dữ liệu, lấy từng `Label` và gọi `value()`; đừng dựa vào chuỗi `toString()` làm định dạng trao đổi dữ liệu ổn định.

## 8. Những trường hợp dễ nhầm khi viết công cụ tra cứu

### 8.1. Lẫn `getDeclared...` với “private” hoặc “public”

Từ `declared` ở đây **không liên quan quyền truy cập public/private**. Nó nói về **metadata gắn ngay trên element hiện tại**, bỏ qua quy tắc kế thừa. `getDeclaredAnnotation` không “mạnh hơn” hoặc “vượt quyền” so với `getAnnotation`; nó chỉ có semantics khác.

Trên member, `getDeclaredAnnotations()` cũng không có nghĩa truy cập được private field value. Đọc annotation metadata khác với `Field.get` hoặc `Method.invoke`, vốn chịu quy tắc access riêng.

### 8.2. Lẫn annotation container với annotation được chứa

`getDeclaredAnnotations()` có thể trả `Labels` container trực tiếp, chứ không liệt kê hai `Label` ở cùng cấp. Việc dùng `getDeclaredAnnotationsByType(Label.class)` là cách đúng để nhận `Label` bên trong container.

Một framework cần hiển thị **nguyên trạng runtime annotation metadata** có thể quan tâm tới container. Framework cần đọc danh sách labels theo nghiệp vụ thường nên dùng ByType.

### 8.3. Mong `@Inherited` làm “merge sâu”

`getAnnotationsByType(Label.class)` trên `OverrideChild` cho `[C]`, không phải `[A, B, C]`. Nếu chính sách nghiệp vụ là “mọi labels trên toàn bộ superclass chain”, hãy viết thuật toán riêng:

~~~java
for (Class<?> current = targetClass;
        current != null;
        current = current.getSuperclass()) {
    Label[] local = current.getDeclaredAnnotationsByType(Label.class);
    // Tự quy định merge, ưu tiên và xử lý trùng lặp ở đây.
}
~~~

Đoạn này là **mảnh thuật toán**, cần đặt trong code có `Class<?> targetClass` và định nghĩa `Label`. Nó đọc đúng annotations của từng class mà không vô tình nhận giá trị `@Inherited` hai lần ở mỗi cấp. Nhưng kết quả merge **không phải** semantics mặc định của `getAnnotationsByType`.

Nếu muốn kế thừa annotation trên interface, còn phải chủ động duyệt `getInterfaces()` (có thể đệ quy), xử lý các nhánh hình thoi và nhiều interface có annotation xung đột. `@Inherited` không giải quyết xung đột đó giúp bạn.

### 8.4. Nhầm override với thừa kế `Method`

Một subclass **không override** có thể tìm được `Method` từ superclass bằng `getMethod(...)`, và `Method.getAnnotation(...)` thấy annotation trên declaration ấy. Subclass **override** tạo declaration khác; annotation trên method gốc không tự sao chép. Nếu framework muốn policy inheritance method annotations, cần tìm method tương ứng trên superclass/interfaces theo contract riêng.

### 8.5. Annotation target khác declaration target

Java còn hỗ trợ **type-use annotations** (ví dụ trên tham số kiểu, array component, generic type argument) thông qua `ElementType.TYPE_USE`. Đọc những annotation này có thể cần `AnnotatedType`, `Method.getAnnotatedReturnType()`, `Parameter.getAnnotatedType()` và các API liên quan, thay vì chỉ `Method.getAnnotations()`. Bảng ở đây dành cho semantics chung của `AnnotatedElement` khi hỏi đúng element; nó không phải lời hứa rằng annotation ở mọi vị trí cú pháp sẽ xuất hiện trên một `Class` hoặc `Method` nhất định.

### 8.6. Không đọc được không đồng nghĩa bytecode không có metadata

Annotation `CLASS` có trong class file nhưng không runtime-visible qua Reflection chuẩn. Annotation `SOURCE` có thể được annotation processor đọc ở compile time nhưng không còn sau compile. Nếu nhu cầu là phân tích cả annotation `CLASS` trong bytecode, cần bytecode parser/tool phù hợp; không cố dùng `getDeclaredAnnotations()` để khôi phục metadata không được Reflection công bố.

### 8.7. Khi annotation trả `null` / mảng rỗng

`getAnnotation(T.class)` và `getDeclaredAnnotation(T.class)` trả `null` khi không tìm thấy. Hai method `...ByType(T.class)` trả mảng độ dài `0`. Tránh:

~~~java
String name = targetClass.getAnnotation(Role.class).value();
~~~

Đây là **đoạn minh họa**: nếu annotation vắng, lời gọi `value()` gây `NullPointerException`. Có thể kiểm tra:

~~~java
Role role = targetClass.getAnnotation(Role.class);
if (role != null) {
    String name = role.value();
    // Dùng name theo nhu cầu ứng dụng.
}
~~~

Khi một annotation có giá trị mặc định, điều đó không có nghĩa `getAnnotation(...)` tự tạo annotation instance nếu nó không gắn trên element. Giá trị mặc định chỉ áp dụng **bên trong một annotation instance thực sự có mặt**.

## 9. Checklist áp dụng khi xây công cụ đọc annotation

Trước khi triển khai một annotation scanner, trả lời các câu hỏi:

1. **Annotation type có `RUNTIME` retention không?** Nếu không, Reflection không phải công cụ đọc phù hợp.
2. **Element cần hỏi là gì?** Class, method, field, parameter, package hay type-use? Đừng hỏi sai đối tượng.
3. **Có nhiều annotation cùng loại không?** Nếu có `@Repeatable`, dùng `...ByType` khi cần unpack container.
4. **Có muốn tìm annotation từ superclass không?** Với class và annotation `@Inherited`, dùng phương thức không có `Declared`.
5. **Có muốn merge annotations nhiều cấp không?** Nếu có, tự định nghĩa thuật toán và dùng `getDeclaredAnnotationsByType` tại từng cấp để kiểm soát.
6. **Có muốn nhận annotation từ interfaces không?** Mặc định `@Inherited` không làm việc đó.
7. **Có tìm metadata method override không?** Nếu có, xác định rõ đọc declaration nào, rồi tự thiết kế policy.
8. **Có kết hợp repeatable và inherited không?** Container cần tuân thủ `@Inherited`/`@Retention`/`@Target` tương thích; kiểm thử các subclass khai báo nhãn mới.
9. **Có cần kết quả ổn định khi serialize?** Không dựa vào thứ tự tổng quát của `getAnnotations()` hoặc `toString()` annotation; chuẩn hóa key và thứ tự theo contract tool.
10. **Có cần phân biệt không tìm thấy và lỗi đọc metadata không?** Một số lời gọi Reflection có thể phát sinh lỗi metadata (ví dụ loại annotation không hợp lệ hoặc class tham chiếu bị thiếu), không nên gom mọi exception thành “annotation không có”.

## 10. Bài tập tự kiểm tra

1. Đổi `@Local` thêm `@Inherited` rồi chạy lại ví dụ. `Child` có thấy `Local` không? `Implementation` có thấy từ interface không?
2. Bỏ một trong hai `@Label` trên `Base`. Khi còn **một** `@Label`, dự đoán `getAnnotation(Label.class)` và `getDeclaredAnnotationsByType(Label.class)`.
3. Giữ `Base` có `[A, B]` nhưng thay `OverrideChild` bằng hai nhãn `[C, D]`. Dự đoán `getAnnotation(Label.class)`, `getDeclaredAnnotationsByType` và `getAnnotationsByType`.
4. Xóa `@Role("child")` trên `OverrideChild`. Sau khi biên dịch sạch, so sánh kết quả `getAnnotation(Role.class)` và `getDeclaredAnnotation(Role.class)`.
5. Trên `AnnotatedInterface.class`, đọc `@Role`; trên `Implementation.class`, đọc cùng annotation. Giải thích vì sao kết quả khác nhau.
6. Bỏ `@Override`/method override trong `OverridingMethods` (chỉ giữ `extends ParentMethods`). Khi `getMethod("act")`, đối tượng `Method` hiện có `getDeclaringClass()` là gì?
7. Viết hàm `allLabels(Class<?>)` gom nhãn từ toàn bộ superclass chain bằng `getDeclaredAnnotationsByType`, loại trùng theo `value` và tài liệu hóa thứ tự ưu tiên.
8. Đổi retention `Label` thành `CLASS` và chỉnh container tương thích. Compiler/Reflection còn trả những gì? Vì sao compile-time processor vẫn có thể làm việc với annotation `CLASS` nhưng runtime Reflection chuẩn thì không?
9. Thiết kế bảng so sánh `getAnnotations()` và `getDeclaredAnnotations()` cho `Base`/`Child`, nêu container nào xuất hiện và phân biệt “liệt kê container” với “unwrap Label”.

**Tổng kết:** “Directly present” = annotation instance trên chính element; “indirectly present” = repeatable annotation trong container; “present” = trực tiếp hoặc được kế thừa theo `@Inherited`; “associated” = trực tiếp/gián tiếp hoặc được kế thừa phù hợp. `getAnnotation` theo **present**, `getDeclaredAnnotation` theo **direct**, `getAnnotationsByType` theo **associated**, `getDeclaredAnnotationsByType` theo **direct + indirect**. `@Inherited` chỉ giải quyết tra cứu qua class superclass, không qua interface/method override, và không cộng gộp mọi cấp khi class con đã có annotation cùng loại.

### Tài liệu tham khảo chính thức (Java 17)

- [`AnnotatedElement` — định nghĩa bốn presence kinds, API matrix](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedElement.html)
- [`Inherited` — giới hạn và cách hoạt động](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Inherited.html)
- [`Repeatable` — annotation container](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Repeatable.html)
- [`RetentionPolicy` — SOURCE, CLASS, RUNTIME](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/RetentionPolicy.html)
- [JLS 17, `9.6.3` — repeatable annotation types và containing annotation types](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.6.3)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-reference-006">Bảng quyền truy cập theo modifier, package, nest và module</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng quyền truy cập theo modifier, package, nest và module trong Java Reflection

## 1. Bốn tầng phải xét khi gọi một member bằng reflection

Khi sử dụng `java.lang.reflect.Method`, `Field` hoặc `Constructor`, câu hỏi “Có thể truy cập được không?” không chỉ phụ thuộc vào `public`/`protected`/`private`. Hãy phân biệt **bốn tầng**:

1. **Tìm thấy metadata**: `getMethod`/`getDeclaredMethod` hoặc `getField`/`getDeclaredField` có trả lại đối tượng phản chiếu không?
2. **Quyền ngôn ngữ Java**: modifier của member; quyền nhìn thấy declaring class; quan hệ package, subclass, và **nestmate** của caller.
3. **Ranh giới module JPMS** (Java 9+): package có được `exports` cho module cần gọi không; khi cần deep reflection, có được `opens` không?
4. **Tình trạng invocation**: receiver có đúng kiểu, đối số hợp lệ, và reflective object có cờ suppress-access-check hay không?

**Có metadata ≠ có quyền sử dụng**. Chẳng hạn, `getDeclaredMethod("secret")` có thể trả một private method, nhưng `Method.invoke` có thể ném `IllegalAccessException` khi code gọi không có quyền và chưa được cho phép suppress access check.

Bài này là **bảng tra cứu quyền truy cập** dành cho Java 17+, ưu tiên phân biệt public API contract và những yếu tố làm thay đổi kết quả theo ngữ cảnh.

## 2. Bảng tổng hợp modifier khi chưa bypass access checks

Giả định member nằm trong một **class mà caller có quyền truy cập** và package/module gates đã được đáp ứng. Bảng này nói về quyền theo Java language access control, **chưa tính `setAccessible(true)`**.

| Modifier của member | Chính class khai báo | Lớp khác cùng nest | Lớp khác cùng runtime package, không cùng nest | Subclass khác package | Lớp không liên quan khác package |
| --- | --- | --- | --- | --- | --- |
| `public` | Có | Có | Có | Có | Có |
| `protected` | Có | Có | Có | Có, **với điều kiện về receiver/qualified access** | Không |
| Không ghi modifier (*package-private*) | Có | Có | Có | Không | Không |
| `private` | Có | Có, vì *nestmate* | Không | Không, trừ khi cũng là nestmate | Không |

**Các điều kiện đọc bảng:**

- “Cùng package” về mặt runtime không phải chỉ trùng chuỗi tên package. Các class phải thuộc **cùng runtime package**, thường xét cả tên package và *defining classloader*. Hai plugin có package cùng tên nhưng do classloader riêng định nghĩa không mặc nhiên được xem là cùng runtime package.
- `protected` trong subclass **khác package** không đơn giản là được gọi trên mọi instance của superclass. Access tới instance member có điều kiện receiver: trong ngữ cảnh subclass `S`, qualified receiver nhìn chung phải thuộc `S` hoặc subclass của `S`. Quyền gọi một protected instance method trên instance thuần của superclass không tự có chỉ vì caller là subclass.
- `private` từ Java 11 có ngữ nghĩa **nest-based access control**: các class cùng nest có thể truy cập private member của nhau (khi đáp ứng ràng buộc runtime), không cần `setAccessible` chỉ vì chúng khác class.
- Access tới **declaring type** vẫn phải hợp lệ. Một public method trong một class không public không tự trở nên callable từ một class ở package khác chỉ vì method có modifier `public`.
- Với code nằm ở **named modules khác nhau**, bảng trên còn cần JPMS gate (mục 5). Module `exports` không biến member `private` hoặc package-private thành public.

### Ghi chú về package-private

Không có keyword `package-private` trong Java. Một member khai báo không có `public`, `protected` hay `private` thường có quyền package access. Ví dụ:

```java
class Example {
    String packageName() { return "default access"; }
}
```

Đây là fragment, không phải chương trình hoàn chỉnh. Quyền này **không phụ thuộc** vào tên package con: `com.app` và `com.app.tools` là **hai package khác nhau**. Cùng module nhưng khác package cũng không được tự động truy cập package-private.

## 3. Nestmate: private có thể được truy cập giữa các class cùng nest

**Nest** là nhóm class được JVM xem có cùng nest host. Với class lồng nhau (`Outer`, `Outer.Inner`, `Outer.StaticNested`), chúng thường cùng nest và được phép truy cập private member của nhau. Nest support có trong JVM từ Java 11 (JEP 181).

Các API tra cứu:

- `Class#getNestHost()`: trả class đóng vai trò nest host.
- `Class#getNestMembers()`: trả thành viên nest của class theo thông tin runtime.
- `Class#isNestmateOf(Class<?>)`: kiểm tra hai class có cùng nest hay không.

Không nên dùng phép so `getEnclosingClass()` hoặc tên class chứa dấu `$` để thay thế phép kiểm tra nestmate. Class tải động, biến thể bytecode hoặc lớp không lồng nhau theo Java source cần dựa vào runtime metadata thực tế.

**Cùng nest khác với cùng package.** Một top-level class khác nằm cùng package với `Outer` không tự được quyền gọi `Outer.Inner` private member. Ngược lại, các nestmates không cần gọi `setAccessible(true)` để thực hiện private access vốn đã được Java cho phép.

Trong reflection, checks được đánh giá với **caller class thực tế** của API nhạy với caller. Nếu `Method.invoke` được thực hiện trong một nestmate thì có thể thành công; nếu đưa cùng đối tượng `Method` sang helper top-level khác không cùng nest và gọi từ đó thì có thể nhận `IllegalAccessException`. Đừng nhầm quyền của nơi **lấy metadata** với quyền của nơi **thực hiện lời gọi**.

## 4. Ví dụ Java 17+ chạy được: same package khác same nest

Chương trình có hai top-level class trong **cùng một file**, cùng *unnamed package* cho mục đích minh họa. `AccessMatrixDemo.Vault` và `AccessMatrixDemo.NestPeer` cùng nest; `PackagePeer` là top-level class khác, cùng runtime package nhưng **khác nest**.

Lưu toàn bộ mã sau vào **`AccessMatrixDemo.java`**:

```java
import java.lang.reflect.Method;
import java.util.Arrays;

public class AccessMatrixDemo {
    public static class Vault {
        public String everyone() { return "public"; }
        protected String family() { return "protected"; }
        String samePackage() { return "package"; }
        private String secret() { return "secret"; }
    }

    public static class NestPeer {
        public static String readSecret(Vault target) throws Exception {
            Method secret = Vault.class.getDeclaredMethod("secret");
            return (String) secret.invoke(target); // Cùng nest, không gọi setAccessible.
        }
    }

    public static void main(String[] args) throws Exception {
        Vault target = new Vault();
        System.out.println("nestmates: "
                + Vault.class.isNestmateOf(NestPeer.class));
        System.out.println("nest private: "
                + NestPeer.readSecret(target));
        System.out.println("package-private: "
                + PackagePeer.readPackage(target));
        System.out.println("protected same package: "
                + PackagePeer.readProtected(target));
        System.out.println("private from non-nest: "
                + PackagePeer.readPrivate(target));
        System.out.println("public call: "
                + Vault.class.getMethod("everyone").invoke(target));
        System.out.println("declared finds private: "
                + (Vault.class.getDeclaredMethod("secret") != null));
        System.out.println("getMethods finds private: "
                + Arrays.stream(Vault.class.getMethods())
                        .anyMatch(m -> m.getName().equals("secret")));
        System.out.println("unnamed module: "
                + !Vault.class.getModule().isNamed());
    }
}

// Top-level class khác: cùng package, nhưng KHÔNG cùng nest với Vault.
class PackagePeer {
    static String readPackage(AccessMatrixDemo.Vault target)
            throws Exception {
        Method m = AccessMatrixDemo.Vault.class
                .getDeclaredMethod("samePackage");
        return (String) m.invoke(target);
    }

    static String readProtected(AccessMatrixDemo.Vault target)
            throws Exception {
        Method m = AccessMatrixDemo.Vault.class
                .getDeclaredMethod("family");
        return (String) m.invoke(target);
    }

    static String readPrivate(AccessMatrixDemo.Vault target)
            throws Exception {
        Method m = AccessMatrixDemo.Vault.class
                .getDeclaredMethod("secret");
        try {
            return (String) m.invoke(target);
        } catch (IllegalAccessException ex) {
            return ex.getClass().getSimpleName();
        }
    }
}
```

Biên dịch, chạy từ thư mục chứa file:

```bash
javac --release 17 AccessMatrixDemo.java
java AccessMatrixDemo
```

Output:

```text
nestmates: true
nest private: secret
package-private: package
protected same package: protected
private from non-nest: IllegalAccessException
public call: public
declared finds private: true
getMethods finds private: false
unnamed module: true
```

**Giải thích:** `NestPeer` gọi được `Vault.secret` vì hai class cùng nest. `PackagePeer` nằm trong cùng package nên gọi được phương thức package-private và protected, nhưng lời gọi private không được phép. `getDeclaredMethod` vẫn **tìm thấy** private method; `getMethods` chỉ liệt kê public methods và không lấy private member. Ví dụ chạy trên **classpath** (unnamed module) nên dòng cuối là `true`. Với named module cần xem thêm cấu hình `module-info.java` ở mục 5.

Chạy trên JDK 17 hoặc mới hơn là đủ; chương trình không dùng `--add-opens` và không gọi `setAccessible`.

## 5. JPMS: `exports` và `opens` không đồng nghĩa nhau

**Java Platform Module System (JPMS)** bổ sung lớp kiểm soát theo module kể từ Java 9. Với named modules, package là ranh giới của hai chỉ thị quan trọng:

- `exports pkg;`: công bố các **public types và public/protected members có thể truy cập theo quy tắc Java** của package đến các module được phép. `exports pkg to some.module;` giới hạn tới module chỉ định. Nó hỗ trợ truy cập thông thường và reflection đối với phần public phù hợp; không tự cấp deep reflection tới private member.
- `opens pkg;`: cho phép **deep reflection tại runtime** tới package theo điều kiện của `AccessibleObject.setAccessible/trySetAccessible`. `opens pkg to tool.module;` chỉ mở cho module chỉ định. Nó **không tự cung cấp quyền tham chiếu kiểu trong code nguồn** như `exports`.
- `open module my.module { ... }`: mở mọi package của module cho deep reflection; việc mở toàn module là phạm vi rộng, nên cân nhắc dùng `opens pkg to tool.module` hẹp hơn.
- Một `opens` **không buộc** công cụ phải dùng private field; công cụ vẫn nên có chính sách allowlist/authorization riêng.

### Ma trận giữa hai named modules

Giả sử module gọi là `client.module`, declaring class nằm trong `library.module`, hai module đã được xác định phù hợp. Bảng này mô tả **quyền qua ranh giới module** đối với public class/member hoặc khả năng bật deep reflection, còn Java language modifiers vẫn áp dụng.

| Trạng thái package trong `library.module` đối với caller | Dùng public API thông thường | `getDeclared...` có thể khám phá? | `trySetAccessible` cho private member từ module khác | Nhận xét |
| --- | --- | --- | --- | --- |
| `exports api;` hoặc `exports api to client.module;` | Có khi member/type public và các điều kiện ngôn ngữ thỏa | Có thể tìm metadata khi có `Class<?>` | **Không** nếu package không đồng thời open | Public export khác deep access |
| `opens internal;` hoặc `opens internal to client.module;`, không export | Không biến internal types thành API dùng tĩnh | Có thể tìm metadata khi nắm được `Class<?>` | **Có thể**, nếu điều kiện `setAccessible` được đáp ứng | Hướng tới framework reflection |
| Vừa `exports` vừa `opens` | Có với public contract | Có thể | Có thể | Quyền mở rộng hơn |
| Không `exports` cũng không `opens` | Không cho client bình thường dùng public type ở package này | Việc có `Class<?>` không tự cấp quyền | **Không** | Không bypass bằng `getDeclared...` |
| Hai caller/declaring class **trong cùng module** | Vẫn tuân theo modifier Java | Có thể | `setAccessible(true)` thường có thể suppress checks về modifier | Module cùng nhau không có nghĩa private truy cập trực tiếp giữa class không nest |

**Chú ý tinh tế:** Kiểm tra access do `AccessibleObject` thực hiện có một điểm khác quy tắc ngôn ngữ thông thường: **reflection giả định module của caller có readability đối với module của declaring class** (xem Javadoc). Khi **biên dịch/tham chiếu tĩnh** từ named module khác, dependency `requires` và package `exports` vẫn là điều kiện liên quan. Đừng đánh đồng khả năng phản chiếu qua tên class đã có với khả năng `import` type từ source code.

### Hai đoạn `module-info.java` minh họa

Giả sử thư viện cung cấp package `com.example.api` cho app và mở package nội bộ cho framework:

```java
// module-info.java của thư viện; ví dụ cấu hình, không chạy một mình.
module example.library {
    exports com.example.api to example.client;
    opens com.example.internal to example.serializer;
}
```

Một ứng dụng tham chiếu public API thư viện:

```java
// module-info.java của client; ví dụ cấu hình.
module example.client {
    requires example.library;
}
```

`example.client` được truy cập public API trong `com.example.api`; `example.serializer` có thể sử dụng deep reflection tới `com.example.internal` khi gọi `setAccessible` trong phạm vi package được mở, đồng thời còn cần các điều kiện truy cập, class loading và API tương ứng. Mã này là **fragment cấu hình**; muốn build cần tạo đủ các module/package và nguồn cụ thể. Không coi `opens` là giải pháp ủy quyền an toàn cho mã không tin cậy.

### Named, unnamed, automatic và open module

Trên **classpath**, class thường thuộc **unnamed module**; các package của unnamed module được coi là exported và open với các module khác theo Javadoc `AccessibleObject`. Named module thì tuân theo `module-info.java` (trừ các cấu hình bổ sung lúc chạy). **Automatic modules** hình thành khi đặt JAR thông thường lên module path, có các đặc tính export/open riêng của automatic module, nhưng đừng dùng nó làm thiết kế lâu dài cho ranh giới bảo mật; khi chuyển sang explicit named module, kết quả deep reflection có thể đổi.

Các tùy chọn launcher như `--add-exports`/`--add-opens` có thể mở đường cho tương thích trong môi trường được quản lý, nhưng đây là ngoại lệ cấu hình, không phải lý do để dùng chúng đại trà. Ưu tiên thư viện cung cấp public API hoặc mở đúng package cho đúng framework.

## 6. Bảng `setAccessible`, `trySetAccessible` và `canAccess`

Bình thường, khi `Method.invoke`/`Field.get`/`Constructor.newInstance` được gọi, access checks được áp dụng. `AccessibleObject` cung cấp API để kiểm tra hoặc cho phép suppress checks trong phạm vi JDK cho phép:

| API | Mục đích | Kết quả / ngoại lệ đáng nhớ |
| --- | --- | --- |
| `member.canAccess(receiver)` | Hỏi **caller hiện tại** có thể truy cập thành viên trên receiver đó không | Trả boolean; có thể ném `IllegalArgumentException` nếu receiver dùng sai loại (constructor/static cần `null`) |
| `member.trySetAccessible()` | Xin bật cờ suppress Java access checks nếu được phép | `true` khi bật được, `false` nếu không thể bật vì access rules; vẫn có thể có `SecurityException` theo cấu hình |
| `member.setAccessible(true)` | Bật suppress checks nếu được phép | Có thể ném `InaccessibleObjectException` khi module không cho phép, hoặc lỗi security khác theo môi trường |
| `member.setAccessible(false)` | Trở về access checks mặc định | Không phải “đóng” package hoặc thu hồi mọi tham chiếu handle đã phát hành |
| `member.isAccessible()` | API cũ chỉ hỏi trạng thái flag | **Deprecated**; không thay được `canAccess` |

**Hợp đồng chính xác của `setAccessible(true)` trong Java 17** cho phép caller class `C` bật access checks suppression cho member khai báo trong class `D` nếu có ít nhất một điều kiện:

1. `C` và `D` nằm **cùng module**; hoặc
2. Member `public`, class `D` `public`, package của `D` **exported** tới module của `C`; hoặc
3. Member `protected static`, class `D` `public`, package của `D` exported tới caller module, và `C` là subclass của `D`; hoặc
4. Package của `D` **opens** tới module của `C`.

Nếu `C` nằm module khác, package của `D` không mở tới `C`, thì **không thể** dùng `setAccessible(true)` để bật quyền private, package-private, protected instance hoặc protected constructor chỉ dựa vào tên member. Với trường `final` thuộc loại không thể sửa như `static final`, final của record hoặc hidden class, bật flag cũng **không cấp quyền ghi**.

Với instance field/method, `canAccess(instance)` đòi instance phù hợp; với constructor/static member, `canAccess(null)` là cú pháp phù hợp. **`canAccess` không phải hàm tự động sửa quyền** và không phải phép kiểm tra xem tên method có tồn tại.

Ví dụ an toàn để không ép deep access:

```java
Method m = type.getDeclaredMethod("operation"); // fragment
if (m.canAccess(instance) || m.trySetAccessible()) {
    Object result = m.invoke(instance);
    // Xử lý result
} else {
    // Không đủ quyền: chuyển sang API public hoặc báo lỗi cấu hình rõ ràng.
}
```

Fragment này giả sử `type`, `instance` và method zero-argument đã hợp lệ, đồng thời caller xử lý checked exceptions. Trong thư viện tổng quát nên phân loại nguyên nhân lỗi, kiểm tra receiver trước, tránh tự ý `trySetAccessible` khi đang xử lý code/plugin không tin cậy; khả năng kỹ thuật không đồng nghĩa được phép đọc nội dung.

## 7. `protected` ở khác package: vì sao receiver quan trọng?

Đặt superclass và subclass ở hai package:

```java
// file base/Parent.java
package base;
public class Parent {
    protected String token() { return "token"; }
}
```

```java
// file child/Child.java
package child;
public class Child extends base.Parent {
    public String onChild() {
        return token(); // Hợp lệ: gọi trên chính instance thuộc Child.
    }

    public String onReceiver(Child other) {
        return other.token(); // Hợp lệ: receiver được phép.
    }

    // public String wrong(base.Parent parent) {
    //     return parent.token(); // Không hợp lệ ở đây: qualifier Parent.
    // }
}
```

Hai đoạn là **fragment độc lập theo đường dẫn nguồn**, không thuộc ví dụ chạy một file ở mục 4. Ý nghĩa là subclass ngoài package có quyền dùng protected member trong phạm vi đã định, nhưng không được phép truy cập protected instance member thông qua mọi receiver kiểu superclass. Reflection cũng không được hiểu là cách vô điều kiện né hạn chế đó.

Đối với member `protected static`, kiểm tra có điều kiện riêng theo quy tắc `AccessibleObject.setAccessible` khi xét khác module; nó không bị ràng buộc bởi instance receiver giống protected instance method. Khi phân tích class kế thừa khác module, trước tiên phải xét declaring type/package có exported phù hợp không.

## 8. `MethodHandles.Lookup` có hệ quyền riêng, không tự bỏ qua JPMS

`java.lang.invoke.MethodHandles.Lookup` là đối tượng đại diện năng lực tra cứu/gọi với **access modes** phụ thuộc context. `MethodHandles.lookup()` tạo lookup theo class nơi được gọi; `MethodHandles.publicLookup()` hạn chế vào thành viên public khả dụng.

`Lookup.findVirtual`, `findStatic`, `findGetter`, `findVarHandle`... kiểm tra quyền khi tìm handle. Không nên nghĩ `MethodHandle` hoặc `VarHandle` sẽ bỏ qua modifier hoặc module vì code không trực tiếp gọi `Method.invoke`.

`MethodHandles.privateLookupIn(Target.class, callerLookup)` có thể cho phép lookup tới private members khi caller có đủ quyền và điều kiện module (bao gồm `opens` khi crossing module) phù hợp. Nó **không phải cửa hậu** tự động đọc private member trong mọi named module. Không trao lookup có quyền private rộng cho mã không tin cậy.

Trong một số framework, chiến lược sạch hơn là dùng public contract/annotation cho các thành viên được phép, tạo đúng handle cần thiết trong ngữ cảnh có quyền rồi cung cấp handle đó. Phân biệt metadata discovery với khả năng thao tác được ủy quyền.

## 9. Tra cứu reflection: declared, public, inherited và access

| Lệnh | Phạm vi khám phá | Điểm dễ nhầm |
| --- | --- | --- |
| `type.getMethod(name, parameterTypes)` | Method `public` của type, gồm method public inherited phù hợp | Không tìm protected/package-private/private method |
| `type.getDeclaredMethod(name, parameterTypes)` | Method khai báo **trực tiếp** trong type, mọi modifier | Không tìm inherited; trả metadata không cho quyền gọi |
| `type.getMethods()` | Các public methods được cung cấp (gồm inherited) | Thứ tự không phải contract sắp xếp |
| `type.getDeclaredMethods()` | Methods khai báo trực tiếp, kể cả private/protected/package-private | Không tự thực hiện access suppression |
| `type.getField(name)` / `getFields()` | Public fields phù hợp (kể cả inherited) | Field hiding cần xét declaring class |
| `type.getDeclaredField(name)` / `getDeclaredFields()` | Field khai báo trực tiếp, mọi modifier | Không tự lấy inherited; không có quyền đọc mặc nhiên |
| `type.getConstructor(parameterTypes)` | Public constructor của type | Constructor không được kế thừa |
| `type.getDeclaredConstructor(parameterTypes)` | Constructor khai báo trong type, kể cả private | Vẫn cần kiểm tra access khi tạo instance |

`Modifier.toString(member.getModifiers())` cho biết biểu diễn modifier. `member.getDeclaringClass()` cho biết class khai báo. Khi build tool, nhớ lọc bridge/synthetic members theo ngữ cảnh; đôi khi chúng xuất hiện trong metadata nhưng không phải public business contract. Việc lấy được một `Class<?>` bằng `Class.forName` cũng không hàm ý mọi member bên trong class sẽ có quyền truy cập.

## 10. Chẩn đoán theo loại lỗi

| Kết quả / lỗi | Nguyên nhân thường gặp | Cách xử lý |
| --- | --- | --- |
| `NoSuchMethodException` | Tên hoặc kiểu parameter không khớp; dùng `getMethod` cho private method | So metadata với overload đúng, chuyển `getDeclaredMethod` khi cần khám phá member declared |
| `IllegalAccessException` ở `Method.invoke`/`Field.get` | Caller không có quyền Java reflection hiện tại | Xem modifier, declaring class, package/nest/module; ưu tiên public API |
| `InaccessibleObjectException` từ `setAccessible(true)` | Không được suppress checks theo module boundaries | Cân nhắc `opens` có phạm vi hẹp hoặc thiết kế API public; không ép mở toàn JVM |
| `trySetAccessible() == false` | Không được bật access flag | Xử lý gracefully, ghi lý do cấu hình, không thử đi thử lại vô hạn |
| `IllegalArgumentException` từ `Method.invoke` | Receiver sai class, sai số lượng/kiểu đối số, primitive conversion không hợp lệ | Kiểm tra `getDeclaringClass`, `getParameterTypes` và receiver |
| `NullPointerException` từ instance invocation với receiver `null` | Không có receiver cho instance member | Kiểm tra `receiver != null` trước khi gọi |
| `InvocationTargetException` | Chính method/constructor đích ném exception | Kiểm tra `getCause()`, không nhầm với lỗi access |
| `ExceptionInInitializerError` | Static initialization của declaring class thất bại | Phân tích initializer và root cause |

**Đừng dùng `setAccessible(true)` như cách chữa chung** cho mọi lỗi: lỗi sai receiver, sai parameter signature, không tìm thấy member hay exception do method đích không thể sửa bằng access flag.

### Checklist điều tra

1. `member.getDeclaringClass()` là lớp nào? Declaring class có thực sự accessible không?
2. Modifier của member và của declaring class là gì?
3. Code **gọi `invoke/get/set`** nằm trong class/nest/package nào? Nó có quyền riêng không?
4. Nếu khác package: caller là subclass? Receiver có đúng subtype theo luật protected?
5. Nếu khác named module: target package `exports` hay `opens` cho module nào? Cần truy cập public hay deep access?
6. Có gọi `setAccessible`/`trySetAccessible` không, và kết quả thực tế?
7. Receiver là instance hợp lệ, static member dùng receiver thích hợp, đối số đúng chữ ký không?
8. Nếu chạy trên JVM có nhiều classloader: hai class “cùng tên package” có thực sự cùng runtime package không?
9. Có đang vô tình cho plugin hoặc dữ liệu không tin cậy quyền inspect/gọi mọi private member?
10. Có thể thay deep reflection bằng interface/public factory/API được chủ sở hữu định nghĩa không?

## 11. Các lỗi suy luận thường gặp

- **“`private` chỉ truy cập trong đúng class khai báo”:** thiếu ngữ cảnh Java 11+; nestmates có thể truy cập private member của nhau.
- **“Cùng package name là đủ”:** runtime package còn liên quan defining classloader, đặc biệt với plugin.
- **“Cùng module nghĩa là cùng package”:** sai; hai package khác nhau trong cùng module vẫn chịu package access.
- **“`protected` cho phép subclass gọi trên mọi Parent instance”:** sai ở khác package với protected instance members.
- **“`getDeclaredMethod` tìm được thì `invoke` chắc chắn thành công”:** sai; metadata discovery khác access, receiver và argument contract.
- **“`exports` cũng mở được private reflection”:** sai; deep reflection từ module khác thường cần `opens`/một điều kiện phù hợp trong `AccessibleObject`.
- **“`opens` giống `exports`, app có thể `import` mọi type”:** sai; `opens` dành cho reflective access, không tạo compile-time API export.
- **“`trySetAccessible()` luôn trả true nếu caller có `Class<?>`”:** sai; strong encapsulation có thể cản.
- **“`setAccessible(true)` cho phép sửa mọi final field”:** sai; có non-modifiable final fields (static final, record final, hidden class final) mà flag không cấp quyền ghi.
- **“`MethodHandle` bỏ qua Java security và JPMS”:** sai; `Lookup` mang và kiểm tra access modes.

## 12. Tự kiểm tra nhanh

1. `Outer.A` và `Outer.B` là hai nestmates, `A` khai báo private method. `B` có thể reflective invoke method đó khi không bật accessible flag không? **Có thể**, nếu đúng nest và các điều kiện gọi khác hợp lệ.
2. Hai top-level class cùng package, khác nest, một class có private field. Class còn lại đọc bằng `Field.get` không bật flag được không? **Không**.
3. `Library` là named module, export `api` nhưng không open `api`; client module đọc public API được, còn private field từ client bằng `trySetAccessible` được không? **Không theo mặc định**.
4. Mở `opens internal to framework.module;` có cho một app khác tự động `import internal.Hidden` ở thời điểm biên dịch không? **Không**.
5. Subclass khác package có quyền protected instance member khi receiver là một instance bất kỳ của superclass không? **Không, có điều kiện receiver/qualifier**.
6. `getDeclaredMethods()` trả private member thì có mặc nhiên gọi được không? **Không**.
7. `canAccess(null)` phù hợp cho instance method hay constructor/static member? **Constructor/static**; instance yêu cầu receiver thuộc declaring class.
8. Class name giống nhau, package giống nhau nhưng do hai defining classloader khác nhau tải: có tự chia sẻ package-private access không? **Không**.
9. Cần công cụ đọc private field thuộc named module khác: `exports` là đủ chưa? **Chưa**; xem `opens` và quy tắc `AccessibleObject`.
10. Một lời gọi reflection ném `InvocationTargetException`: kiểm tra gì trước? **`getCause()` để xem lỗi do chính code đích**.

Tài liệu chuẩn Java 17:

- [JLS `6.6 — Access Control](https://docs.oracle.com/javase/specs/jls/se17/html/jls-6.html#jls-6.6)
- [JLS `6.6.2.1 — Access to a protected Member](https://docs.oracle.com/javase/specs/jls/se17/html/jls-6.html#jls-6.6.2.1)
- [JLS `7.7 — Module Declarations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-7.html#jls-7.7)
- [Javadoc `AccessibleObject`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html)
- [Javadoc `Class.getNestHost` / `isNestmateOf`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html)
- [Javadoc `MethodHandles.Lookup`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandles.Lookup.html)
- [JEP 181 — Nest-Based Access Control](https://openjdk.org/jeps/181)


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-reference-007">Bảng ngoại lệ reflection và thông tin cần kiểm tra</a>

<details>
<summary>Xem chi tiết</summary>

# Bảng ngoại lệ Reflection trong Java và thông tin cần kiểm tra

## 1. Mục đích và phạm vi

Khi dùng Java Reflection để tìm lớp, tra cứu `Method`/`Field`/`Constructor`, đọc metadata hay thực thi phương thức, các lỗi thường thuộc **những giai đoạn khác nhau**. Nếu nhìn thấy một tên exception rồi sửa ngẫu nhiên bằng `setAccessible(true)`, thay overload hoặc bắt mọi `Exception`, bạn có thể che mất nguyên nhân thực sự.

Bài này cung cấp bảng tra cứu thực dụng: **lỗi xuất hiện ở API nào, nguyên nhân thường gặp, cần kiểm tra thông tin gì, cách xử lý phù hợp**. Ví dụ chạy bằng Java 17+.

Cần phân biệt:

- **Lỗi tra cứu:** chưa lấy được metadata đích (ví dụ `ClassNotFoundException`, `NoSuchMethodException`).
- **Lỗi quyền hoặc đối số khi gọi:** đã tìm được metadata nhưng lời gọi không hợp lệ (`IllegalAccessException`, `IllegalArgumentException`, `NullPointerException`).
- **Lỗi bên trong phương thức đích:** Java reflection bọc Throwable được target ném bằng `InvocationTargetException`; xem `getCause()`.
- **Lỗi module, class initialization hoặc JVM linking:** có thể là runtime exception hay `Error`, **không nhất thiết** thuộc `ReflectiveOperationException`.

Không phải mọi loại Throwable đều cần “bắt cho bằng được”. Mục tiêu là nhận diện lỗi lập trình/cấu hình từ sớm, truyền đạt lỗi dữ liệu rõ ràng và không che ngoại lệ nghiệp vụ.

## 2. Bảng tra cứu nhanh: lỗi → vị trí → thông tin cần xem

| Exception / Error | Hay gặp ở đâu | Điều cần kiểm tra đầu tiên |
| --- | --- | --- |
| `ClassNotFoundException` (checked) | `Class.forName("...")`, `ClassLoader.loadClass` | Tên nhị phân đầy đủ, classpath/module path, class loader dùng để nạp |
| `NoSuchMethodException` (checked) | `getMethod`, `getDeclaredMethod`, `getConstructor`, `getDeclaredConstructor` | Tên, **đúng dãy kiểu tham số**, public/declared, kế thừa, kiểu sau erasure |
| `NoSuchFieldException` (checked) | `getField`, `getDeclaredField` | Tên field, phân biệt public/declared, lớp đang tra cứu |
| `IllegalAccessException` (checked) | `Method.invoke`, `Field.get/set`, `Constructor.newInstance` | Modifier, declaring class, caller/module, khả năng truy cập Java và JPMS |
| `InaccessibleObjectException` (runtime) | `setAccessible(true)` khi yêu cầu bỏ qua access checks | Package được `opens` cho module caller chưa; có cần truy cập member non-public không |
| `SecurityException` (runtime) | Một số thao tác tra cứu/đổi accessibility, môi trường kiểm soát quyền | Quyền phản chiếu và chính sách môi trường (nếu có), tránh giả định vượt quyền |
| `IllegalArgumentException` (runtime) | `invoke`/`set`/`newInstance` | Receiver đúng loại, số lượng/thứ tự/kiểu đối số, primitive conversion, varargs |
| `NullPointerException` (runtime) | Gọi **instance** method trên receiver `null`, một số thao tác với null | Receiver hay giá trị bị thiếu? Method là static hay instance? |
| `InvocationTargetException` (checked) | `Method.invoke`, `Constructor.newInstance` | `getCause()`; stack trace và message của ngoại lệ target, không chỉ wrapper |
| `InstantiationException` (checked) | `Constructor.newInstance` với lớp trừu tượng | Đích có abstract? Kiểu có thể khởi tạo trực tiếp không? |
| `ExceptionInInitializerError` (`Error`) | Khởi tạo static của lớp, có thể bị kích hoạt khi tải/gọi | Static initializer/static fields, **cause**, trạng thái khởi tạo lớp |
| `LinkageError` (`Error`) | Tải/liên kết lớp | JAR/class phiên bản lệch, xung đột dependencies, binary compatibility |
| `WrongMethodTypeException` (runtime) | `MethodHandle.invokeExact/invoke` | `handle.type()`, kiểu tĩnh từng đối số **và kiểu kết quả tại call site** |
| `UndeclaredThrowableException` (runtime) | JDK dynamic proxy chuyển checked exception không được khai báo | `getUndeclaredThrowable()`, hợp đồng `throws` trên interface, logic handler |
| `StackOverflowError` (`Error`) | Handler của proxy/reflective dispatch quay lại chính nó | Chuỗi frame lặp, `method.invoke(proxy, args)`, logging `proxy.toString()` |

Bảng trên tổng hợp **tình huống thường gặp**, không phải liệt kê đầy đủ mọi throwable mà các API có thể ném. Một số ngoại lệ còn có nhiều nguyên nhân khác; luôn đọc tài liệu API và stack trace thực tế.

## 3. Tra cứu class, method, field và constructor

### 3.1. ClassNotFoundException: không tìm thấy lớp

```java
Class<?> type = Class.forName("com.example.Tool");
```

Khi bị `ClassNotFoundException`, kiểm tra:

1. Tên có đúng dạng **binary name** (`com.example.Tool`) không? Với nested class, tên nhị phân thường chứa `$` (ví dụ `Outer$Inner`).
2. Class có thực sự nằm trong artifact đã build và classpath/module path hiện tại không?
3. Class đang được yêu cầu bởi **class loader nào**? Cùng tên nhưng class loader khác nhau có thể đại diện cho lớp runtime khác nhau.
4. Với JPMS, module chứa class có được resolve vào module graph không? Đừng tự kết luận `ClassNotFoundException` luôn đồng nghĩa “quên JAR”.

`Class.forName(String)` cũng kích hoạt khởi tạo lớp theo semantics của overload này; lỗi ở static initializer có thể phát sinh `ExceptionInInitializerError`, còn lỗi linking có thể phát sinh `LinkageError`. Chúng **không phải** `ClassNotFoundException`. Khi cần tra cứu mà không chủ ý khởi tạo class, có overload:

```java
// Mảnh code trong ngữ cảnh có ClassLoader loader:
Class<?> type = Class.forName("com.example.Tool", false, loader);
```

Tham số `false` yêu cầu không khởi tạo lớp ngay ở bước này; thao tác sau đó vẫn có thể kích hoạt initialization.

### 3.2. NoSuchMethodException: sai tên hoặc chữ ký

```java
Method m = Service.class.getMethod("save", int.class, String.class);
```

`getMethod` tìm phương thức **public**, bao gồm public method được kế thừa. `getDeclaredMethod` tìm method **khai báo trực tiếp trong chính class**, kể cả private, nhưng không tự tìm inherited method. Dãy kiểu tham số phải khớp chính xác sau erasure; `int.class` khác `Integer.class` và `List.class` không biểu diễn riêng `List<String>`.

Khi lỗi, ghi nhận:

- `declaring class`/class đang tra cứu và `getName()`.
- Tên method, **số lượng, thứ tự và `Class<?>` từng tham số**; không chỉ tên method.
- API lookup dùng `getMethod` hay `getDeclaredMethod`, và method có bị kế thừa/không public không.
- Có overload hoặc bridge/synthetic method do generic/compilation tạo ra không.
- Kiểu return **không tham gia vào tham số của `getMethod`**; sau khi resolve, kiểm tra riêng `method.getReturnType()` nếu hợp đồng cần.

Đừng dùng `getMethods()[0]` hoặc chọn “phương thức đầu tiên trùng tên” để né `NoSuchMethodException`.

### 3.3. NoSuchFieldException: sai scope tra cứu

`getField("code")` tìm public field, bao gồm field được kế thừa theo quy tắc Reflection. `getDeclaredField("code")` tìm field được khai báo ngay trên class, kể cả non-public; tìm thấy metadata private **không tự cấp quyền đọc/ghi**.

Kiểm tra tên chính xác, class khai báo, field được kế thừa hay khai báo trực tiếp, Modifier và liệu class metadata có đúng runtime class cần thao tác không. Field không có tham số như Method, nhưng kiểu field (`field.getType()`), static/instance và final cần được kiểm tra trước `get/set`.

### 3.4. Constructor: đúng arity chưa chắc khởi tạo được

```java
Constructor<?> ctor = type.getDeclaredConstructor(String.class, int.class);
Object obj = ctor.newInstance("x", 3);
```

Tra cứu có thể ném `NoSuchMethodException`; gọi constructor có thể ném `InstantiationException` (ví dụ lớp abstract), `IllegalAccessException`, `IllegalArgumentException` và `InvocationTargetException` do thân constructor ném ngoại lệ. `ExceptionInInitializerError` cũng có thể xảy ra nếu static initialization của lớp thất bại.

Đừng nhầm `Class.newInstance()` với `Constructor.newInstance()`. `Class.newInstance()` đã deprecated; `Constructor.newInstance` cho phép phân biệt ngoại lệ target qua `InvocationTargetException` rõ ràng hơn.

## 4. Đã tìm được Method nhưng invoke thất bại

Giả sử có:

```java
Method m = Worker.class.getMethod("repeat", String.class, int.class);
Object out = m.invoke(new Worker(), "go", 2);
```

Khi lỗi, cần lần lượt kiểm tra:

| Cần kiểm tra | API hoặc cách kiểm tra | Vì sao |
| --- | --- | --- |
| Đã resolve đúng method? | `m.getName()`, `m.getDeclaringClass()` | Tránh gọi nhầm class/overload |
| Method static hay instance? | `Modifier.isStatic(m.getModifiers())` | Static có thể dùng receiver `null`; instance cần receiver hợp lệ |
| Receiver đúng loại? | `m.getDeclaringClass().isInstance(target)` cho instance | Sai receiver có thể dẫn đến `IllegalArgumentException` |
| Receiver có null? | `target == null` | Instance invoke trên receiver null ném `NullPointerException` |
| Arity và kiểu? | `m.getParameterCount()`, `m.getParameterTypes()` | Sai số/thứ tự/kiểu đối số thường là `IllegalArgumentException` |
| Accessibility? | `m.getModifiers()`, `m.canAccess(target)` nếu hợp lệ | Có thể gặp `IllegalAccessException`; `canAccess` đòi receiver phù hợp với trường hợp instance |
| Có varargs? | `m.isVarArgs()` | `Method.invoke` vẫn nhận mảng argument đúng vị trí; cần đóng gói array đích |
| Kết quả có đúng? | `m.getReturnType()` | `void` trả `null` qua `Method.invoke`, primitive result được boxing |
| Target có tự ném lỗi? | `InvocationTargetException.getCause()` | Nguyên nhân thực nằm trong target |

**Chuyển đổi số:** `Method.invoke` cho phép một số phép unboxing và **widening primitive** phù hợp, nhưng không tùy ý narrowing và không parse `"2"` thành `int`. Một công cụ nhận dữ liệu String phải áp dụng converter tường minh **trước khi invoke**.

**Varargs:** Khi phương thức đích khai báo `String... items`, tham số cuối ở cấp Reflection là `String[]`. `Method.invoke` là varargs API nhưng không có nghĩa tự xử lý danh sách đối số user thành varargs của target theo mọi hình dạng. Hãy xây dựng một `Object[]` có phần tử cuối là `String[]` đúng kiểu, truyền mảng argument một cách chủ đích và kiểm thử.

**Access:** `getDeclaredMethod` tìm thấy private method nhưng `Method.invoke` có thể vẫn ném `IllegalAccessException`. Kiểm tra class/module và thiết kế API trước khi quyết định có cần quyền truy cập sâu. `setAccessible(true)` không phải cách sửa chung cho mọi exception.

## 5. Lỗi bên trong target: InvocationTargetException

Đây là khác biệt quan trọng nhất trong debug Reflection:

```java
try {
    Object result = method.invoke(target, args);
    // Xử lý result.
} catch (InvocationTargetException ex) {
    Throwable original = ex.getCause();
    // Ghi log/chuyển tiếp theo chính sách của ứng dụng.
    throw original;
}
```

Đoạn là **mảnh code bên trong phương thức khai báo `throws Throwable`**. Khi method target thực sự chạy và ném `IOException`, `IllegalStateException` hay thậm chí `Error`, `Method.invoke` có thể bọc Throwable từ target bằng `InvocationTargetException`. `ex.getCause()` (hoặc `getTargetException()`) cho biết lỗi thật.

Phân biệt:

- `IllegalArgumentException` **ném trực tiếp từ `Method.invoke`**: thường là receiver/argument không đúng; target có thể chưa được thực thi.
- `InvocationTargetException` có cause là `IllegalArgumentException`: chính **method target** đã ném lỗi đó trong phần thực thi của mình.
- `InvocationTargetException` có cause là checked exception: đừng xóa cause hoặc tự động phân loại thành lỗi parser.
- Với logging, cần xem **stack trace và message của cause**; log wrapper một mình có thể bỏ qua frame hữu ích.

Nếu wrapper có nhiều tầng, có thể kiểm tra chuỗi `getCause()` nhưng không được tự động làm mất ranh giới nghiệp vụ, retry một hành động có side effect hoặc tiết lộ stack trace nhạy cảm cho caller.

## 6. Ví dụ Java 17+ chạy độc lập: bảng lỗi bằng thực nghiệm

Lưu toàn bộ mã dưới đây trong `ReflectionExceptionDemo.java`. Nó tạo các trường hợp lỗi **có chủ đích, bắt được và in nhãn ổn định**, không cần kích hoạt lỗi JPMS hay StackOverflowError khó kiểm soát.

```java
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class ReflectionExceptionDemo {
    public static class Worker {
        public String repeat(String word, int times) {
            return word.repeat(times);
        }
        public void fail() throws IOException {
            throw new IOException("storage unavailable");
        }
    }

    public static abstract class AbstractJob {
        public AbstractJob() {}
    }

    public static void main(String[] args) throws Exception {
        Class<?> type = Worker.class;
        Method repeat = type.getMethod("repeat", String.class, int.class);
        System.out.println("method=" + repeat.getName());
        System.out.println("parameters=" + repeat.getParameterCount());
        System.out.println("return=" + repeat.getReturnType().getSimpleName());
        System.out.println("static=" + Modifier.isStatic(repeat.getModifiers()));
        System.out.println("success=" + repeat.invoke(new Worker(), "go", 2));

        try {
            type.getMethod("repeat", int.class, int.class);
        } catch (NoSuchMethodException ex) {
            System.out.println("lookup: " + ex.getClass().getSimpleName());
        }
        try {
            type.getField("missing");
        } catch (NoSuchFieldException ex) {
            System.out.println("field: " + ex.getClass().getSimpleName());
        }
        try {
            repeat.invoke(new Worker(), "go", "2");
        } catch (IllegalArgumentException ex) {
            System.out.println("arguments: " + ex.getClass().getSimpleName());
        }
        try {
            repeat.invoke(null, "go", 2);
        } catch (NullPointerException ex) {
            System.out.println("receiver: " + ex.getClass().getSimpleName());
        }
        try {
            Method hidden = ExternalHelper.class.getDeclaredMethod("secret");
            hidden.invoke(new ExternalHelper());
        } catch (IllegalAccessException ex) {
            System.out.println("access: " + ex.getClass().getSimpleName());
        }
        try {
            Method fail = type.getMethod("fail");
            fail.invoke(new Worker());
        } catch (InvocationTargetException ex) {
            System.out.println("target: " + ex.getCause().getClass().getSimpleName());
            System.out.println("message=" + ex.getCause().getMessage());
        }
        try {
            Constructor<AbstractJob> ctor = AbstractJob.class.getDeclaredConstructor();
            ctor.newInstance();
        } catch (InstantiationException ex) {
            System.out.println("construct: " + ex.getClass().getSimpleName());
        }
        try {
            Class.forName("missing.example.NotOnClasspath");
        } catch (ClassNotFoundException ex) {
            System.out.println("load: " + ex.getClass().getSimpleName());
        }
    }
}

class ExternalHelper {
    private String secret() {
        return "hidden";
    }
}
```

Biên dịch và chạy:

```bash
javac ReflectionExceptionDemo.java
java ReflectionExceptionDemo
```

Stdout đã kiểm chứng bằng JDK:

```text
method=repeat
parameters=2
return=String
static=false
success=gogo
lookup: NoSuchMethodException
field: NoSuchFieldException
arguments: IllegalArgumentException
receiver: NullPointerException
access: IllegalAccessException
target: IOException
message=storage unavailable
construct: InstantiationException
load: ClassNotFoundException
```

**Vì sao ví dụ đáng chú ý?**

1. Tra cứu `repeat` với `(String,int)` đúng, còn tra cứu `(int,int)` ném `NoSuchMethodException` dù trùng tên.
2. `getField("missing")` thất bại ở bước tra cứu field, không liên quan đến bước invocation.
3. Truyền `"2"` thay cho `int` khi gọi `repeat` gây `IllegalArgumentException`; Reflection không parse String thành số nguyên.
4. Receiver `null` đối với instance method `repeat` gây `NullPointerException`; static method có quy tắc receiver khác.
5. `ExternalHelper.secret` là phương thức `private` của **lớp top-level khác**; `getDeclaredMethod` tìm được metadata, nhưng code ở `ReflectionExceptionDemo` không được invoke nếu chưa có quyền.
6. `Worker.fail()` thực thi thật và ném `IOException`; Reflective API bọc vào `InvocationTargetException` nên mẫu lấy `getCause()`.
7. `AbstractJob` là abstract; tra cứu constructor được nhưng `newInstance` không thể tạo instance, phát sinh `InstantiationException`.
8. `Class.forName` với tên không tồn tại ném `ClassNotFoundException`.

Đây là những lỗi **dự kiến trong bài demo**. Trong ứng dụng thật, nên phân loại lỗi để xử lý thay vì cứ cho phép caller gây hàng loạt lỗi rồi mới bắt.

## 7. Chẩn đoán lỗi truy cập và JPMS đúng API

Khi đã có `Method`, `Field` hoặc `Constructor` nhưng thao tác thất bại, tách **quyền truy cập hiện tại** khỏi **việc yêu cầu bỏ qua access checks**:

| Dấu hiệu | Điều cần làm rõ |
| --- | --- |
| `IllegalAccessException` tại `invoke/get/set/newInstance` | Caller, declaring class, modifier, runtime package/nest và module; thành viên có thật nhưng chưa được phép thao tác |
| `member.canAccess(receiver) == false` | Access check hiện tại không qua; với static member/constructor dùng `null`, instance cần receiver đúng kiểu |
| `member.trySetAccessible() == false` | Không bật được suppression theo ranh giới module; phương thức này báo `false` khi không thể bật, thay vì tự động ném `InaccessibleObjectException` |
| `InaccessibleObjectException` từ `setAccessible(true)` | Package thuộc named module chưa `opens` cho caller (hoặc điều kiện cấp quyền khác không thỏa) |
| `SecurityException` khi đổi access | Chính sách runtime từ chối quyền liên quan, khác lỗi `opens` |

`exports` cho phép dùng public API theo quy tắc Java/JPMS; **deep reflection** tới private members từ module khác thường cần `opens` đúng package và caller. `getDeclaredMethod` chỉ tìm metadata, không tự cho phép invoke. Ví dụ: mở `java.base/java.lang` để đọc private member JDK chỉ vì lỗi truy cập thường là dấu hiệu cần chọn public API phù hợp hơn.

Sau khi giải quyết access, vẫn phải kiểm tra receiver, arity và kiểu tham số: `setAccessible(true)` không sửa `IllegalArgumentException`, `NoSuchMethodException` hoặc lỗi do target ném. Bảng phân quyền theo package, nest và module được trình bày riêng trong bài chuyên về access; bảng ở đây dùng để **khoanh vùng exception** trong luồng debug.

## 8. Không gộp Method.invoke với MethodHandle và Dynamic Proxy

Cùng nằm trong hệ sinh thái reflective/dynamic invocation, nhưng **cơ chế ném lỗi khác nhau**.

### 8.1. MethodHandle: WrongMethodTypeException

Với `MethodHandle`, chữ ký nằm trong `handle.type()`. `invokeExact` yêu cầu kiểu đối số và kiểu kết quả **tại call site** trùng chính xác. `invoke` cho phép thích nghi kiểu hợp lệ; nếu không thể thích nghi sẽ ném `WrongMethodTypeException`. Nếu cast tham chiếu thất bại ở runtime có thể thấy `ClassCastException`.

Ví dụ minh họa, chưa phải chương trình độc lập:

```java
// h có MethodType (long,long)long.
long ok = (long) h.invokeExact(1L, 2L);
long bad = (long) h.invokeExact(1, 2L); // WrongMethodTypeException
```

Đừng xử lý `WrongMethodTypeException` bằng cách catch `InvocationTargetException`: đó là loại exception khác và không đại diện cho lỗi do `Method.invoke` bọc target.

### 8.2. Dynamic proxy: UndeclaredThrowableException

Với JDK dynamic proxy, `InvocationHandler.invoke` có thể ném `Throwable`. Nếu handler ném một **checked exception** không được khai báo trong `throws` của phương thức interface, proxy có thể bọc nó thành `UndeclaredThrowableException`. Kiểm tra:

- Method thuộc interface nào, `getExceptionTypes()` khai báo những checked exception gì.
- Handler có unwrap, chuyển đổi hoặc ném checked exception ngoài hợp đồng không.
- `UndeclaredThrowableException.getUndeclaredThrowable()` hoặc `getCause()` để thấy lỗi gốc.
- Nếu có `StackOverflowError`, rà soát handler gọi lại `method.invoke(proxy, args)` hoặc log chính proxy gây `toString()` re-entry.

`InvocationTargetException` có thể xuất hiện **bên trong handler** nếu handler dùng `Method.invoke(target, args)`, còn `UndeclaredThrowableException` là wrapper mà dynamic proxy tạo khi checked exception không hợp đồng đi ra caller. Không đánh đồng hai tầng.

### 8.3. Generic metadata có thể lỗi riêng

Khi đọc `getGenericParameterTypes()` hoặc `getGenericReturnType()`, một số API có thể gặp `TypeNotPresentException`, `MalformedParameterizedTypeException` hoặc `GenericSignatureFormatError` vì type signature không resolve hay không hợp lệ. Điều đó không nhất thiết nghĩa là method không tồn tại. Nếu công cụ phụ thuộc generic metadata, hãy ghi nhận chữ ký raw và generic, module/classpath của kiểu tham chiếu, không tự tạo giá trị thay thế sai.

## 9. Checklist điều tra lỗi theo thứ tự

Khi nhận một log Reflective Invocation lỗi, nên thu thập thông tin có cấu trúc:

1. **Giai đoạn:** class load, lookup, accessibility, mapping input, invocation, target execution, hay initialization?
2. **Ngoại lệ thật và cause:** `ex.getClass().getName()`, `ex.getMessage()`, `ex.getCause()`; với `InvocationTargetException`, xem trực tiếp cause.
3. **Class identity:** `type.getName()`, `type.getModule()`, `type.getClassLoader()` nếu thích hợp. Hai lớp cùng tên nhưng class loader khác nhau không phải cùng kiểu.
4. **Method/field signature:** tên, declaring class, modifier, kiểu tham số theo thứ tự, kiểu trả về hoặc kiểu field, `isVarArgs()`.
5. **Receiver:** null hay không; runtime class thực tế qua `receiver.getClass()` (chỉ gọi khi khác null); có tương thích declaring class không.
6. **Arguments:** số lượng, kiểu runtime có thể hiển thị dưới dạng tên kiểu, vị trí sai, primitive/wrapper và converter đã chạy chưa. Với null, cần schema để biết kiểu mong đợi.
7. **Access/module:** public/private, package, `module-info.java`, `exports/opens`, caller module và `canAccess(...)` phù hợp.
8. **Giao ước API:** method nào được phép gọi, caller có quyền không, lỗi dữ liệu cần báo gì, có transaction/side effect khi target đã chạy không.
9. **Stack trace:** frame nào lặp lại hoặc nằm trong target; đừng chỉ nhìn dòng wrapper ngoài cùng.
10. **Tái hiện:** viết test tối thiểu với class/method/arguments tương đương, chặn input sai trước invocation.

Có thể dùng helper ghi **metadata** sau để hỗ trợ debug; đây là mảnh code nằm trong phương thức có biến `Method method` và đã import `Arrays`, `Modifier`:

```java
System.out.println("declaring=" + method.getDeclaringClass().getName());
System.out.println("method=" + method.getName());
System.out.println("static=" + Modifier.isStatic(method.getModifiers()));
System.out.println("parameters=" +
        java.util.Arrays.toString(method.getParameterTypes()));
System.out.println("returns=" + method.getReturnType().getTypeName());
System.out.println("varargs=" + method.isVarArgs());
```

**Bảo vệ dữ liệu:** không log nguyên credentials, token, mật khẩu, request body chứa thông tin nhạy cảm hoặc nguyên argument values trong hệ thống production nếu không được phép. Ghi tên kiểu, index, error code và correlation ID thường đủ để điều tra phần lớn lỗi mapping.

## 10. Nguyên tắc xử lý và phản hồi lỗi

Không nên viết:

```java
try {
    // Tìm Method, chuyển đổi dữ liệu, invoke...
} catch (Exception ignored) {
    return null; // Sai: mất nguyên nhân, biến lỗi thành kết quả giả.
}
```

Thay vào đó, xác định **ranh giới lỗi**:

| Nhóm | Cách xử lý phù hợp |
| --- | --- |
| Lỗi cấu hình/không tìm được method trong hợp đồng tin cậy | Fail-fast khi khởi tạo, sửa tên/chữ ký/dependency |
| Lỗi dữ liệu caller: arity, kiểu, định dạng | Validate/convert trước invocation, báo tham số và kiểu cần nhập |
| Lỗi quyền/module | Xem lại thiết kế public API và quyền; chỉ mở package khi có chủ đích và được cho phép |
| Lỗi target | Lấy cause từ `InvocationTargetException`, xử lý theo hợp đồng nghiệp vụ |
| Lỗi JVM/linkage/initialization nghiêm trọng | Ghi đủ bối cảnh, xử lý ở ranh giới hệ thống phù hợp; không tự động nuốt `Error` |
| Lỗi dynamic proxy/MethodHandle | Chẩn đoán theo chữ ký handler/call site riêng, không ép vào cơ chế Method.invoke |

**Không retry mù quáng** khi target đã được gọi: phương thức có thể ghi dữ liệu, gửi yêu cầu mạng hoặc thực hiện side effect trước khi ném exception. Chỉ retry nếu hợp đồng nghiệp vụ cho phép và có cơ chế idempotency/transaction tương ứng.

## 11. Lỗi thường gặp

1. Cứ gặp `NoSuchMethodException` là gọi `getDeclaredMethods()[0]`, dễ chọn sai overload hoặc sai quyền.
2. Chỉ so tên method, bỏ qua tham số `int.class` so với `Integer.class`.
3. Thấy private member là gọi `setAccessible(true)` mà không xét module/thiết kế API.
4. Tưởng `Method.invoke` parse được `String` sang primitive hoặc DTO.
5. Không phân biệt static/instance và truyền receiver null hoặc sai kiểu.
6. Bắt `InvocationTargetException` nhưng chỉ log wrapper, bỏ mất `getCause()`.
7. Catch tất cả `Throwable` rồi tiếp tục như chưa có lỗi, che `Error` nghiêm trọng.
8. Tưởng `ReflectiveOperationException` bao phủ `InaccessibleObjectException`, `SecurityException`, `WrongMethodTypeException` hay `LinkageError`.
9. Gọi `Class.forName` rồi cho rằng mọi thất bại đều là “class chưa có”: initialization/linkage cũng là các khả năng cần phân biệt.
10. Với proxy, gọi lại chính proxy trong handler dẫn đến `StackOverflowError`.
11. Cho rằng phương thức tìm được qua `getDeclaredMethod` thì chắc chắn invoke được.
12. In toàn bộ đối số/request để debug mà không cân nhắc lộ thông tin nhạy cảm.

## 12. Câu hỏi tự kiểm tra

**Câu 1.** `getMethod("save", int.class)` ném `NoSuchMethodException` dù code có `save(Integer)`. Vì sao?

Vì `int.class` khác `Integer.class`; lookup theo **chữ ký kiểu chính xác**. Boxing không giúp `getMethod` tự chọn overload.

**Câu 2.** `getDeclaredMethod("secret")` tìm được nhưng `invoke` ném `IllegalAccessException`. Đây có phải lỗi “không tồn tại method” không?

Không. Metadata tìm thấy, nhưng caller không có quyền gọi. Cần kiểm visibility/module/thiết kế quyền.

**Câu 3.** Target ném `IOException` khi `Method.invoke` gọi nó. Bắt exception nào ở tầng reflection để tìm `IOException`?

Bắt `InvocationTargetException`, lấy `getCause()` hoặc `getTargetException()`.

**Câu 4.** `Method.invoke(target, "5")` gọi method nhận `int` có tự chuyển chuỗi thành số không?

Không. Phải parse tường minh trước và chuyển giá trị `Integer` hợp lệ vào `Object[]`.

**Câu 5.** `Class.forName` ném `ExceptionInInitializerError`. Có nên sửa bằng cách thêm JAR như khi `ClassNotFoundException` không?

Không mặc định. `ExceptionInInitializerError` chỉ ra lỗi khi thực hiện static initialization. Hãy xem cause và mã static initializer.

**Câu 6.** `setAccessible(true)` ném `InaccessibleObjectException`. Việc method `private` được tìm thấy có chứng minh module cho truy cập sâu không?

Không. Tìm metadata và quyền truy cập sâu là hai việc khác nhau; xem quy tắc JPMS `opens` và lựa chọn API công khai.

**Câu 7.** Proxy ném `UndeclaredThrowableException` thay vì checked exception của handler. Cần xem gì?

Xem cause/`getUndeclaredThrowable()` và checked exceptions khai báo ở interface method.

**Câu 8.** `WrongMethodTypeException` khi `invokeExact` có cần kiểm kiểu kết quả không?

Có. Kiểu kết quả **tại call site** cũng phải khớp với `MethodHandle.type()`, bên cạnh kiểu đối số.

## 13. Ghi nhớ

**Xác định giai đoạn xảy ra lỗi trước, rồi đối chiếu đúng metadata và hợp đồng.** `NoSuchMethodException` gợi ý lỗi tra cứu chữ ký; `IllegalAccessException` gợi ý quyền truy cập; `IllegalArgumentException` tại `Method.invoke` thường gợi ý receiver/argument; `InvocationTargetException` báo rằng target đã ném Throwable và **cause mới là vấn đề cần điều tra**. Lỗi JPMS, MethodHandle và dynamic proxy có cơ chế riêng.

Tài liệu Java 17 để tra cứu trực tiếp:

- [Class (getMethod/getField/forName)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html)
- [Method.invoke](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html#invoke(java.lang.Object,java.lang.Object...))
- [Field](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html)
- [Constructor.newInstance](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Constructor.html#newInstance(java.lang.Object...))
- [AccessibleObject](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html)
- [InvocationTargetException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationTargetException.html)
- [MethodHandle](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/MethodHandle.html)
- [UndeclaredThrowableException](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/UndeclaredThrowableException.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-reference-008">Tra cứu Javadoc và quy tắc ngôn ngữ theo từng thao tác</a>

<details>
<summary>Xem chi tiết</summary>

# Tra cứu Javadoc và quy tắc ngôn ngữ theo từng thao tác Java Reflection

## 1. Vì sao phải tra cứu theo *thao tác cụ thể*?

Reflection không phải một API duy nhất với một bộ quy tắc đơn giản. Trong Java 17+, các thao tác **tìm một lớp**, **liệt kê thành viên**, **tìm chữ ký method**, **kiểm tra quyền truy cập**, **gọi method**, **đọc generic/annotation**, hay **tạo dynamic proxy** có quy tắc và loại lỗi riêng. Khi kết quả trái với trực giác, cần xác định chính xác thao tác đang làm gì, rồi đọc tài liệu phù hợp.

Nguồn tra cứu có vai trò khác nhau:

- **API Javadoc (Java SE 17):** hợp đồng của một API cụ thể như `Class.getDeclaredMethod`, `Method.invoke` hoặc `AccessibleObject.trySetAccessible`: tham số, giá trị trả về, điều kiện, exception và khác biệt với API gần tên.
- **Java Language Specification (JLS):** các quy tắc của *ngôn ngữ Java*: member access, method overloading/overriding, invocation conversions, generics/type erasure, annotation và class initialization.
- **Java Virtual Machine Specification (JVMS):** định nghĩa *runtime/bytecode*: class loading, linking, initialization, resolution, field/method descriptors, generic `Signature` attribute và dispatch ở tầng JVM.
- **Mã thử Java tối thiểu trên JDK mục tiêu:** xác nhận một giả thuyết có thể tái hiện. Kết quả thực nghiệm là kiểm chứng cho trường hợp đó, **không thay thế hợp đồng chính thức**.

Một trường hợp điển hình: `getMethod("process", Integer.class)` ném `NoSuchMethodException` dù class khai báo `process(int)`. Trực giác về **autoboxing khi gọi Java bình thường** không thể thay thế Javadoc của **reflection lookup**. Tương tự, gọi `Method.invoke` trên một `Method` đã tìm được lại có quy tắc chuyển đổi đối số riêng.

## 2. Bộ liên kết chính thức nên đánh dấu

Tất cả liên kết dưới đây chỉ đến tài liệu **Java SE / JDK 17** để đọc cùng một mốc phiên bản khi kiểm tra ví dụ trong bài.

| Nhu cầu | Tài liệu chính thức | Phần nên đọc |
| --- | --- | --- |
| Khám phá class, field, method, constructor | [`java.lang.Class`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html) | `forName`, `getMethod`, `getDeclaredMethod`, `getMethods`, `getDeclaredMethods`, `getField`, `getDeclaredField` |
| Lấy/đặt field | [`java.lang.reflect.Field`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html) | `get`, `set`, `getType`, `getGenericType` |
| Gọi method/đọc signature | [`java.lang.reflect.Method`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html) | `invoke`, `getReturnType`, `getGenericReturnType`, `getParameterTypes` |
| Gọi constructor | [`java.lang.reflect.Constructor`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Constructor.html) | `newInstance` và ngoại lệ khi constructor ném exception |
| Kiểm soát quyền reflection | [`AccessibleObject`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AccessibleObject.html) | `canAccess`, `trySetAccessible`, `setAccessible` |
| Generic metadata | [`java.lang.reflect.Type`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Type.html) | `ParameterizedType`, `TypeVariable`, `WildcardType` |
| Runtime annotations | [`AnnotatedElement`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedElement.html) | `getAnnotation`, `getDeclaredAnnotation`, `getAnnotationsByType` |
| Tạo dynamic proxy | [`Proxy`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Proxy.html) và [`InvocationHandler`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationHandler.html) | `newProxyInstance`, `invoke`, phương thức `Object` |
| Kiểm tra overload/override | [JLS `15.12 — Method Invocation](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.12) và [JLS `8.4 — Method Declarations](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.4) | Các bước chọn method khi viết code Java, overriding, signature |
| Phạm vi access của ngôn ngữ | [JLS `6.6 — Access Control](https://docs.oracle.com/javase/specs/jls/se17/html/jls-6.html#jls-6.6) | `public`, `protected`, package-private, `private` |
| Type erasure | [JLS `4.6 — Type Erasure](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.6) | Erasure của `T`, `List<String>`, bounds |
| Class loading/linking/init | [JVMS Chapter 5](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html) | ``5.3` loading, ``5.4` linking, ``5.5` initialization |
| Generic `Signature` trong class file | [JVMS `4.7.9 — The Signature Attribute](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.9) | Bytecode descriptor khác metadata generic |

Khi dùng phiên bản JDK khác, đổi **cả Javadoc, JLS/JVMS và JDK thực thi** sang cùng phiên bản nếu hành vi mới là vấn đề. Không chọn ngẫu nhiên tài liệu mới nhất rồi chạy chương trình cũ mà không xét `@since`, `@Deprecated`, giới hạn module hoặc thay đổi API.

## 3. Cách đọc một trang Javadoc đúng trình tự

Với API cần tra cứu, nên đi theo chuỗi câu hỏi sau:

1. **Đúng loại/thao tác chưa?** Ví dụ `Class.getMethod` so với `Class.getDeclaredMethod`, `Method.invoke` so với `Constructor.newInstance`.
2. **Đúng phiên bản và chữ ký chưa?** Xem dạng khai báo đầy đủ, generic parameter, varargs, static/instance, overload của chính API JDK.
3. **Đầu vào có nghĩa gì?** Với `getMethod`, mảng `Class<?>... parameterTypes` biểu diễn **kiểu tham số khai báo**; với `Method.invoke`, `args` là **giá trị truyền khi gọi**.
4. **Phạm vi kết quả là gì?** `public` hay declared, kế thừa hay không; `getDeclaredMethods()` có thể chứa synthetic/bridge; `getDeclaredFields()` không trả field kế thừa.
5. **Có kiểm tra quyền truy cập không?** Tìm được metadata không có nghĩa được gọi private method hoặc đọc private field; `trySetAccessible` chịu ràng buộc module.
6. **Đường lỗi là gì?** `NoSuchMethodException` khác `IllegalAccessException`, `IllegalArgumentException` và `InvocationTargetException`. Nếu có `throws`, đọc điều kiện *đúng lúc thao tác đó*.
7. **Javadoc có dẫn tới JLS/JVMS không?** Hãy mở phần đặc tả tương ứng khi điều bạn hỏi thuộc compiler/JVM hơn là hợp đồng thư viện.

Đọc cả **Method Detail** (mô tả và điều kiện) chứ không chỉ tên trong Method Summary; các chú thích `Since`, `See Also`, `Throws` và cảnh báo hành vi đặc biệt thường nằm dưới phần chi tiết.

## 4. Bảng tra cứu: tình huống nào mở tài liệu nào trước?

| Thao tác đang làm | API ưu tiên | Câu hỏi cần kiểm chứng | Nhầm lẫn phổ biến |
| --- | --- | --- | --- |
| Biết tên `"pkg.Foo"`, muốn lấy `Class<?>` | `Class.forName`/`ClassLoader.loadClass` | Loader nào? Có yêu cầu initialization không? | Cho rằng mọi loader đều tìm cùng phạm vi; `Class.forName("int")` sẽ trả `int.class` |
| Có object, hỏi class runtime | `obj.getClass()`, `Class.isInstance` | Kiểu runtime là class nào? | Mong `List<String>` tồn tại như một runtime class riêng |
| Liệt kê public methods | `Class.getMethods()` | Có inherited public methods không? | Hiểu là chỉ gồm method khai báo trực tiếp |
| Liệt kê method khai báo trực tiếp | `Class.getDeclaredMethods()` | Có private/bridge/synthetic? | Mong thứ tự trả về giống thứ tự source và tự có inherited method |
| Tìm method chính xác | `getMethod`/`getDeclaredMethod` | Tên + danh sách **kiểu tham số đúng** là gì? | Mong reflection tự chọn overload, tự boxing kiểu truyền vào `getMethod` |
| Tìm field hoặc constructor | `getDeclaredField`, `getDeclaredConstructor` | Field/constructor trực tiếp hay public; exact parameter types? | Dùng `getMethods` để tìm constructor; tin constructor kế thừa |
| Đọc private field | `Field.get`, `canAccess`, `trySetAccessible` | Caller có quyền và module có mở package không? | Tưởng `getDeclaredField` đồng nghĩa có quyền đọc |
| Gọi method bằng reflection | `Method.invoke` | Receiver, actual args, boxing/widening, exception target? | Nghĩ `invoke` giải quyết overload lại từ đầu |
| Đọc annotation lúc chạy | `AnnotatedElement`/`Retention` | Annotation có `RUNTIME` không, có trực tiếp/kế thừa không? | Mong `RetentionPolicy.CLASS` hiển thị bằng `getAnnotation` |
| Đọc `List<String>` trong chữ ký | `getGeneric...`, `Type`, classfile `Signature` | Metadata generic có được lưu trong classfile không? | Tưởng mọi instance giữ type argument riêng |
| Sinh interface proxy | `Proxy`, `InvocationHandler` | Handler xử lý method và `equals`/`hashCode`/`toString` thế nào? | Tưởng proxy tự làm delegation/equality theo target |
| Thiếu class hoặc static init lỗi | `Class.forName`, JVMS Ch. 5 | Lookup thất bại hay class linkage/initialization thất bại? | Đồng nhất `ClassNotFoundException` với `NoClassDefFoundError` |

**Phạm vi của `Class<?>`:** Có `SomeClass.class` là đủ để introspect chính kiểu ấy; nó không cho phép tự động liệt kê mọi class trong một package. Classpath scanning là một bài toán nguồn class/resource khác, không phải `Class.getDeclaredClasses()`.

## 5. Bài toán mẫu: “`getMethod` không tìm thấy dù gọi trực tiếp được”

Ví dụ một class có hai overload:

```java
// Minh họa nằm trong thân class, không phải chương trình chạy độc lập.
public String choose(int n) { return "primitive"; }
public String choose(Integer n) { return "wrapper"; }
```

Khi biết chính xác điều cần hỏi, đường tra cứu là:

1. Mở Javadoc `Class.getMethod(String, Class<?>...)` và `getDeclaredMethod`.
2. Đọc rằng tham số cuối biểu diễn **kiểu tham số của method cần tìm**; chọn `int.class` cho `choose(int)`, `Integer.class` cho `choose(Integer)`.
3. Nếu muốn hiểu lời gọi nguồn `choose(1)` và `choose(Integer.valueOf(1))` được compiler chọn ra sao, mở **JLS `15.12.2**; đó là vấn đề **overload resolution của ngôn ngữ**, không phải một overload resolver được `getMethod` cung cấp.
4. Nếu đã tìm `Method chooseInt` rồi gọi `chooseInt.invoke(receiver, Integer.valueOf(1))`, mở Javadoc **`Method.invoke`** để xem unboxing/primitive widening cho actual arguments.
5. Nếu `getMethod("choose", Long.class)` thất bại dù có overload `choose(Number)`, đó là đúng với lookup chữ ký: `Long.class` không phải `Number.class`.

**Phân biệt hai giai đoạn:** khi *tra cứu*, `Class<?>` phải khớp declared parameter type; khi *invoke*, actual argument object được kiểm tra và có thể chuyển đổi theo quy tắc reflection, nhưng không chọn method khác.

JLS `15.12` còn quy định các pha xử lý strict/loose/varargs và most-specific cho method invocation expression trong mã nguồn. Không tự sao chép các pha ấy vào ý nghĩa của `getMethod`.

## 6. Bài toán mẫu: “`getDeclaredMethod` thấy method, nhưng `invoke` lỗi”

Giả sử method `private` được tìm được:

```java
// Minh họa, không độc lập.
Method m = SomeType.class.getDeclaredMethod("secret");
Object result = m.invoke(instance);
```

`getDeclaredMethod` trả metadata của method được khai báo trực tiếp, bất kể public/private nếu tìm thấy chữ ký. Nhưng `m.invoke` vẫn kiểm tra quyền truy cập. Cần đọc **`AccessibleObject`**:

- `m.canAccess(instance)` hỏi quyền hiện tại của caller đối với reflected member, với receiver phù hợp.
- `m.trySetAccessible()` trả `true` khi có thể cho phép bỏ qua kiểm tra access theo các điều kiện JDK; không cam kết thành công trong mọi module/package.
- `m.setAccessible(true)` có thể thất bại bằng `InaccessibleObjectException` nếu module không cho phép mở quyền truy cập.
- `IllegalAccessException` của `Method.invoke` thường phản ánh caller không có quyền; khác `InvocationTargetException`, loại bao **exception do chính method đích ném ra**.

Muốn biết tại sao nguồn Java truy cập được hay không, tham khảo JLS `6.6`. Muốn biết khi nào reflective access được suppress, xem Javadoc `AccessibleObject` và yêu cầu `exports`/`opens` của Java Platform Module System. **Đọc metadata không phải bằng chứng được phép truy cập member**.

Trường hợp field tương tự: `getDeclaredField` có thể lấy `Field` cho field private, nhưng `Field.get(instance)` vẫn kiểm tra access. Khi đưa vào công cụ thực tế, nên ưu tiên public API/hợp đồng module rõ ràng, và chỉ mở private access ở ranh giới được phép.

## 7. Bài toán mẫu: generic erasure và metadata classfile

Khi method khai báo `List<String> find()`:

- `getReturnType()` trả `List.class` vì kiểu trả về erased/runtime là `List`.
- `getGenericReturnType()` có thể trả `ParameterizedType` biểu diễn `java.util.List<java.lang.String>` nếu classfile còn `Signature` metadata hợp lệ.
- Việc một method mô tả `List<String>` không có nghĩa từng list object chứa đầy đủ generic type argument tại runtime.

Đọc **JLS `4.6`** để hiểu erasure; đọc **JVMS `4.7.9`** để hiểu classfile `Signature`; đọc Javadoc `Method.getGenericReturnType` để biết API biểu diễn metadata bằng `Type` và các ngoại lệ có thể xảy ra khi signature hỏng/thiếu kiểu.

Với khai báo parameter `List<String>`, muốn gọi `getMethod("accept", ...)` cần truyền `List.class`, **không phải `ArrayList.class`** dù truyền một instance `ArrayList<String>` vào `Method.invoke` có thể hợp lệ.

Đừng nhầm một chuỗi mô tả kiểu `"java.util.List<java.lang.String>"` với tên lớp có thể truyền cho `Class.forName`. Java không tạo class runtime `List<String>` riêng cho mỗi type argument.

## 8. Bài toán mẫu: annotation có trong source nhưng không thấy bằng reflection

Annotation được dùng để đọc lúc chạy phải xét **`@Retention`**:

- `RetentionPolicy.SOURCE`: chỉ dành cho xử lý nguồn; không giữ như annotation runtime trong classfile.
- `RetentionPolicy.CLASS`: có thể tồn tại trong classfile nhưng không hiển thị qua API runtime reflection thường dùng.
- `RetentionPolicy.RUNTIME`: annotation được giữ để API reflection như `getAnnotation`/`isAnnotationPresent` đọc được.

`@Target` chỉ quy định annotation áp dụng lên những loại phần tử nào (field/method/class...), **không tự thực thi một quy tắc validation**. `@Inherited` có ngữ nghĩa riêng đối với annotation trên class; không khiến field/method ở superclass tự trở thành field/method khai báo trực tiếp của subclass.

Vì vậy khi `field.getAnnotation(MyAnnotation.class)` trả `null`, trước tiên kiểm tra định nghĩa annotation có `@Retention(RUNTIME)` hay không, field lấy từ **đúng class và đúng loader** hay không, annotation có gắn trực tiếp trên phần tử đang hỏi không. Chỉ sau đó mới điều tra classfile transformation, phiên bản build hoặc annotation package trùng tên ở loader khác.

Nguồn: Javadoc **`AnnotatedElement`**, `Retention` và quy tắc annotation trong JLS `9.6`.

## 9. Bài toán mẫu: class loading/initialization và lỗi khi introspect

`Class.forName("pkg.Plugin")` không chỉ yêu cầu tìm binary name: dạng một tham số còn yêu cầu class được khởi tạo khi thành công. Nếu chỉ muốn lấy `Class<?>` mà chưa yêu cầu initialization, dạng ba tham số `Class.forName(name, false, loader)` là một lựa chọn, nhưng vẫn không bảo đảm không có lỗi loading/linking.

Hãy phân biệt:

- **`ClassNotFoundException`:** API tìm tên class (như `Class.forName`) không tìm ra class theo loader được yêu cầu.
- **`NoClassDefFoundError`:** JVM không thể có định nghĩa class dùng được khi resolve/link một phụ thuộc, hoặc lớp ở trạng thái lỗi sau initialization thất bại; đôi lúc cause chứa `ClassNotFoundException`.
- **`ExceptionInInitializerError`:** static initialization ném exception thông thường trong lần khởi tạo; lần dùng tiếp theo của class đã lỗi có thể dẫn đến `NoClassDefFoundError`.
- **`InaccessibleObjectException` hoặc `IllegalAccessException`:** vấn đề quyền reflective access, không phải bằng chứng class không tồn tại.
- **`NoSuchMethodException`:** không tìm được chữ ký method; hoàn toàn khác class loader không tìm ra class.

Khi stack trace chứa nhiều tầng `Caused by`, đừng dừng ở lớp lỗi ngoài. Dựa vào thao tác đang gọi và **nguyên nhân đầu tiên** để mở Javadoc chính xác rồi đối chiếu **JVMS Ch. 5** về loading/linking/initialization. Một `Class<?>` đã được lấy thành công không đảm bảo các type phụ thuộc trong chữ ký method sẽ luôn resolve được khi introspect sâu.

## 10. Ví dụ Java 17+ chạy được: tra cứu và đối chiếu từng thao tác

Chương trình dưới đây dùng các API đã nêu và in kết quả ổn định. Nó không tự quét classpath và không cần thư viện bên ngoài.

Tạo file `JavadocLookupDemo.java` trong một thư mục trống, không khai báo `package`:

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Action {}

class LibraryBase {
    public String inherited() {
        return "base";
    }
}

class LibrarySample extends LibraryBase {
    private final String secret;

    public LibrarySample(String secret) {
        this.secret = secret;
    }

    public String choose(int value) {
        return "primitive:" + value;
    }

    public String choose(Integer value) {
        return "wrapper:" + value;
    }

    @Action
    public List<String> labels() {
        return List.of("a", "b");
    }

    public void fail() {
        throw new IllegalStateException("boom");
    }
}

public class JavadocLookupDemo {
    public static void main(String[] args) throws Exception {
        Class<LibrarySample> type = LibrarySample.class;
        Constructor<LibrarySample> ctor =
                type.getConstructor(String.class);
        LibrarySample sample = ctor.newInstance("key");

        System.out.println("Created: "
                + sample.getClass().getSimpleName());

        Method inherited = type.getMethod("inherited");
        System.out.println("Inherited declaring class: "
                + inherited.getDeclaringClass().getSimpleName());

        boolean inheritedDeclaredHere = true;
        try {
            type.getDeclaredMethod("inherited");
        } catch (NoSuchMethodException expected) {
            inheritedDeclaredHere = false;
        }
        System.out.println("Declared inherited method: "
                + inheritedDeclaredHere);

        Method primitive = type.getMethod("choose", int.class);
        Method wrapper = type.getMethod("choose", Integer.class);
        System.out.println("Primitive lookup: "
                + primitive.getParameterTypes()[0].getTypeName());
        System.out.println("Wrapper lookup: "
                + wrapper.getParameterTypes()[0].getTypeName());
        System.out.println("Primitive invocation: "
                + primitive.invoke(sample, Integer.valueOf(7)));
        System.out.println("Wrapper invocation: "
                + wrapper.invoke(sample, Integer.valueOf(7)));

        boolean missingLong = false;
        try {
            type.getMethod("choose", Long.class);
        } catch (NoSuchMethodException expected) {
            missingLong = true;
        }
        System.out.println("Long lookup missing: " + missingLong);

        Field field = type.getDeclaredField("secret");
        System.out.println("Private field accessible before: "
                + field.canAccess(sample));
        System.out.println("Private access enabled: "
                + field.trySetAccessible());
        System.out.println("Private field value: " + field.get(sample));

        Method labels = type.getMethod("labels");
        System.out.println("Erased return: "
                + labels.getReturnType().getTypeName());
        System.out.println("Generic return: "
                + labels.getGenericReturnType().getTypeName());
        System.out.println("Runtime annotation: "
                + labels.isAnnotationPresent(Action.class));

        try {
            type.getMethod("fail").invoke(sample);
        } catch (InvocationTargetException e) {
            System.out.println("Target exception: "
                    + e.getCause().getClass().getSimpleName());
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 JavadocLookupDemo.java
java JavadocLookupDemo
```

Output trong môi trường classpath thông thường, chạy từ cùng thư mục:

```text
Created: LibrarySample
Inherited declaring class: LibraryBase
Declared inherited method: false
Primitive lookup: int
Wrapper lookup: java.lang.Integer
Primitive invocation: primitive:7
Wrapper invocation: wrapper:7
Long lookup missing: true
Private field accessible before: false
Private access enabled: true
Private field value: key
Erased return: java.util.List
Generic return: java.util.List<java.lang.String>
Runtime annotation: true
Target exception: IllegalStateException
```

**Phân tích chính theo Javadoc:**

1. `getConstructor(String.class)` tìm public constructor với chữ ký cụ thể; `newInstance` tạo object. Constructor không phải inherited method.
2. `getMethod("inherited")` tìm public method kế thừa từ `LibraryBase`, còn `getDeclaredMethod("inherited")` không tìm thấy vì `LibrarySample` không tự khai báo nó.
3. `choose(int)` và `choose(Integer)` là hai overload; truyền `int.class` và `Integer.class` vào `getMethod` cho hai kết quả khác nhau. `invoke` trên method primitive chấp nhận boxed `Integer`.
4. Tra cứu `Long.class` không tự chọn overload `Integer` hay `int`; không có chữ ký đúng nên xuất hiện `NoSuchMethodException`.
5. Class `LibrarySample` được khai báo ở phạm vi top-level không phải nestmate của `JavadocLookupDemo`, nên private field chưa được quyền truy cập trực tiếp. `trySetAccessible` trong môi trường classpath minh họa có thể bật và cho phép `Field.get`; **đây không phải cam kết với mọi named module**.
6. `getReturnType()` chỉ thấy erased type `List`, còn `getGenericReturnType()` đọc generic Signature metadata do compiler giữ cho khai báo `List<String>`.
7. Annotation `@Action` có `RUNTIME` nên được tìm thấy.
8. `fail()` ném `IllegalStateException`, nhưng qua `Method.invoke` exception được bọc trong `InvocationTargetException`; đọc `getCause()` giúp biết lỗi gốc.

## 11. Quy trình 7 bước khi gặp một vấn đề Reflection thật

**Bước 1 — Chỉ rõ API và dòng code đang chạy.** Ghi nguyên lời gọi như `type.getDeclaredMethod(...)` hoặc `method.invoke(...)`, không mô tả chung “reflection lỗi”.

**Bước 2 — Tách dữ liệu đầu vào.** Với lookup: tên method, danh sách `Class<?>` của parameter, class/loader. Với invocation: receiver, actual arguments, phương thức static/instance, quyền truy cập.

**Bước 3 — Đọc Javadoc theo phiên bản runtime.** Kiểm tra `Parameters`, `Returns`, `Throws`, mô tả “public”/“declared”/“inherited” và các điều kiện đặc biệt trong method detail.

**Bước 4 — Mở đặc tả đúng tầng.** Nếu đang so việc compiler chọn overload/override, dùng JLS ``15.12`/`8.4`. Nếu đang điều tra class initialization và linkage, dùng JVMS Ch. 5. Nếu thiếu generic Signature, dùng JLS `4.6` và JVMS `4.7.9`.

**Bước 5 — Viết chương trình tái hiện tối thiểu.** Một file, vài class rõ tên, đầu vào ổn định; in method name, `getParameterTypes()`, `getDeclaringClass()`, class loader nếu phù hợp. Đừng phụ thuộc thứ tự `getDeclaredMethods()`.

**Bước 6 — Kiểm thử cả đường thành công và thất bại.** Ví dụ `getMethod(..., int.class)` thành công, `getMethod(..., Long.class)` thất bại; đọc field private khi chưa có quyền và sau khi được phép; gọi method ném exception để xác nhận `InvocationTargetException`.

**Bước 7 — Tài liệu hóa kết luận kèm phiên bản và điều kiện.** Ví dụ “trên Java 17, với class trong unnamed module và method được khai báo như ví dụ, `getDeclaredMethod` không tìm inherited method”. Đừng biến hành vi phụ thuộc module/compiler/class loader thành mệnh đề không điều kiện.

## 12. Những lỗi nghiên cứu tài liệu phổ biến

| Lỗi khi tra cứu | Hậu quả | Cách sửa |
| --- | --- | --- |
| Tìm toàn bộ bài blog thay vì Javadoc API đang gọi | Trộn quy tắc của các thao tác khác nhau | Đọc method detail của API tương ứng trước |
| Lấy tài liệu Java mới hơn nhưng chạy JDK cũ | Mâu thuẫn `@since` và module behavior | Dùng bộ tài liệu cùng phiên bản JDK |
| Đọc `getMethod` nhưng áp quy tắc `Method.invoke` để suy chữ ký | Tìm sai overload | Tra cứu và invocation là hai giai đoạn riêng |
| Tưởng tìm thấy `Field` là đọc private được | `IllegalAccessException`/`InaccessibleObjectException` | Đọc `AccessibleObject` và các ranh giới module |
| Tưởng `getDeclared...` tự bao gồm inherited members | Bỏ sót superclass | Chọn đúng API, duyệt superclass khi cần |
| Dựa vào thứ tự mảng method/field | Test không ổn định | Lọc theo tên/chữ ký và sắp xếp khi in |
| Nhầm generic metadata với type của instance | Framework suy ra sai kiểu runtime | Phân biệt erasure và `Signature` |
| Bỏ qua `Caused by` của exception | Chữa sai tầng lỗi | Ghi chính xác exception lookup/invocation/target/linkage |
| Tưởng `Class.forName` và `ClassLoader.loadClass` giống hệt nhau | Hiểu sai thời điểm initialize | Đọc Javadoc từng overload và JVMS Ch. 5 |
| Dùng `setAccessible` như cách chữa mọi lỗi | Không giải quyết metadata hoặc linkage; có thể bị module chặn | Chỉ mở access khi hợp lệ; sửa đúng nguyên nhân gốc |

## 13. Bài tự kiểm tra

**Câu 1.** `getMethod("parse", Integer.class)` không thấy `parse(int)`. Mở Javadoc hay JLS trước?

**Đáp án:** Mở Javadoc `Class.getMethod` trước vì đang tra cứu chữ ký. Dùng JLS `15.12` khi muốn biết compiler chọn overload nào cho một lời gọi trong mã nguồn.

**Câu 2.** `getDeclaredMethod` thấy private method nhưng `invoke` thất bại. Nên đọc tài liệu nào?

**Đáp án:** Javadoc `Method.invoke` và `AccessibleObject`, rồi JLS `6.6` nếu cần hiểu quyền ngôn ngữ; kiểm tra `trySetAccessible` cùng module/package.

**Câu 3.** `getGenericReturnType()` thấy `List<String>` nhưng `getReturnType()` chỉ là `List`. Hai API có mâu thuẫn?

**Đáp án:** Không. Một API trả erased runtime type, API còn lại đọc generic metadata khai báo được lưu trong class file nếu có. Tra JLS `4.6` và JVMS `4.7.9`.

**Câu 4.** Với `getDeclaredMethods()`, có thể chọn method đầu tiên là method khai báo sớm nhất trong source không?

**Đáp án:** Không. Thứ tự trả về không được bảo đảm, và compiler có thể thêm bridge/synthetic methods. Kiểm tra `Method.isBridge()`/`isSynthetic()` khi liên quan.

**Câu 5.** `Class.forName` ném `ExceptionInInitializerError` thay vì `ClassNotFoundException`. Có phải Javadoc sai?

**Đáp án:** Không. Class có thể tìm được nhưng static initialization thất bại. Tra overload `Class.forName` rồi JVM initialization rules; phân biệt lỗi lookup với initialization.

**Câu 6.** Annotation có `@Retention(CLASS)` nhưng `getAnnotation` trả `null`. Tài liệu nào trả lời?

**Đáp án:** Javadoc `AnnotatedElement`/`Retention` cùng JLS quy định retention. Annotation lưu trong classfile không đồng nghĩa hiển thị qua runtime reflection.

**Câu 7.** Tài liệu API cho phép truy cập một method ở Java 17 nhưng một dự án named module bị chặn. Có cần xem cả đặc tả module không?

**Đáp án:** Có. `AccessibleObject` giải thích các điều kiện suppress access; JPMS `exports`/`opens` quyết định phạm vi truy cập. Có tên method hoặc class không tự cung cấp quyền private reflection.

**Ghi nhớ:** Với Reflection, hãy đi theo thứ tự **xác định thao tác → đọc Javadoc đúng method/version → đối chiếu JLS/JVMS khi cần → viết test tái hiện → kết luận kèm điều kiện**. Cách này tránh lấy quy tắc của compiler, JVM và Reflection API thay thế lẫn nhau.

</details>

- [Quay lại đầu trang](#back-to-top)
