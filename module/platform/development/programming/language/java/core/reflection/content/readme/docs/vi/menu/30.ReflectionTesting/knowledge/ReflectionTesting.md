<a id="back-to-top"></a>

# Kiểm thử

## Menu
- [Fixture có public, protected, package-private và private member](#reflection-testing-001)
- [Kiểm thử declared member khác inherited member](#reflection-testing-002)
- [Kiểm thử tra cứu primitive, wrapper, overload và generic erasure](#reflection-testing-003)
- [Kiểm thử invoke với null, varargs, mảng và sai kiểu](#reflection-testing-004)
- [Kiểm thử lỗi mã đích được giữ qua InvocationTargetException](#reflection-testing-005)
- [Kiểm thử runtime annotation, repeatable và type-use annotation](#reflection-testing-006)
- [Kiểm thử non-static inner class, enum, record và sealed type](#reflection-testing-007)
- [Kiểm thử quyền truy cập trong cùng và khác module](#reflection-testing-008)
- [Kiểm thử metadata có và không có -parameters](#reflection-testing-009)
- [Kiểm thử hai loader định nghĩa lớp cùng tên](#reflection-testing-010)
- [Kiểm thử cache không giữ plugin đã hết vòng đời](#reflection-testing-011)

## <a id="reflection-testing-001">Fixture có public, protected, package-private và private member</a>

<details>
<summary>Xem chi tiết</summary>

# Fixture kiểm thử Reflection với `public`, `protected`, package-private và `private` member

Khi viết công cụ Java dựa trên Reflection (mapper, serializer, dependency injection, bộ tạo tài liệu API hoặc scanner), **chỉ nhìn thấy một member trong metadata không có nghĩa được phép đọc, gọi hoặc tạo instance qua member đó**. Một fixture tốt cần chủ động tạo đủ bốn mức truy cập của Java, đặt mã kiểm thử **ở cả cùng package lẫn khác package**, rồi kiểm tra hành vi thật thay vì suy đoán từ tên modifier.

Bài này thiết kế một fixture Java 17+ có **field, method và constructor** ở bốn mức `public`, `protected`, **package-private** (không ghi access modifier) và `private`. Ta quan sát sự khác nhau giữa `getFields()/getMethods()` với `getDeclaredFields()/getDeclaredMethods()`, và kiểm thử quyền bằng `AccessibleObject.canAccess(...)`, `trySetAccessible()` cùng quy tắc dành cho subclass/JPMS.

## 1. Bốn mức truy cập cần được thử trong fixture

| Modifier của member | Chính class/nestmate được phép | Class khác cùng package | Subclass ở package khác | Class không liên quan ở package khác |
| --- | --- | --- | --- | --- |
| `public` | Có | Có | Có | Có, nếu lớp/package đích cũng khả kiến |
| `protected` | Có | Có | Có, **với quy tắc receiver khi ở package khác** | Không |
| *package-private* (không modifier) | Có | Có | Không chỉ vì là subclass | Không |
| `private` | Có, theo quy tắc nestmate của Java | Không chỉ vì cùng package | Không chỉ vì kế thừa | Không |

Bảng trên mô tả quyền của **mã Java thông thường**, trước khi dùng một cơ chế reflective access suppression được cấp phép. Cần kiểm tra thêm khả năng truy cập của **lớp chứa member** và quan hệ module (`exports`/`opens`). `public` trên một member nằm trong lớp không public hoặc package không export không tự tạo thành API public toàn hệ thống.

- **Protected ở package khác:** subclass được quyền truy cập instance member `protected` qua một receiver có kiểu là chính subclass đó (hoặc subclass con thích hợp). Từ subclass ấy, thao tác cùng member trên một instance `BaseFixture` thông thường vẫn có thể bị từ chối.
- **Package-private:** phụ thuộc **runtime package**. Khi Java có các class loader/module khác nhau, chỉ cùng dòng tên package trong source không đủ để khẳng định cùng runtime package.
- **Private và nestmates:** những nested class cùng nest có cơ chế truy cập private riêng. Để thử thất bại private một cách rõ ràng, ví dụ sử dụng **ba lớp top-level ở các tệp riêng**, không đặt toàn bộ fixture vào nested class của test.
- **Constructor:** cũng có bốn mức quyền, nhưng không được kế thừa. Test constructor cần `getDeclaredConstructor(...)` hoặc `getConstructor(...)`, không áp dụng quy tắc override.

## 2. Xác định câu hỏi kiểm thử trước khi chọn API

Reflection có hai hoạt động khác nhau: **tìm metadata** và **sử dụng member**.

| API | Nó cho biết gì? | Không nên suy diễn |
| --- | --- | --- |
| `Class.getDeclaredField(name)` | Tìm field khai báo trực tiếp, gồm public/non-public | Không chứng minh caller có thể đọc/ghi field |
| `Class.getFields()` | Liệt kê các public field phù hợp, gồm field kế thừa | Không phải danh sách đầy đủ mọi field private của hierarchy |
| `Class.getDeclaredMethod(name, parameterTypes...)` | Tìm đúng method do lớp khai báo, không giới hạn modifier | Không chứng minh có quyền `invoke` |
| `Class.getMethods()` | Public methods phù hợp, có tính đến kế thừa/interface | Không trả toàn bộ non-public methods |
| `Class.getDeclaredConstructors()` | Constructor do lớp khai báo, mọi mức modifier | Không có constructor kế thừa |
| `Class.getConstructor(parameterTypes...)` | Tìm public constructor theo chữ ký | Constructor private/protected/package-private không xuất hiện |
| `Field.get`/`set`, `Method.invoke`, `Constructor.newInstance` | Thực hiện thao tác, có kiểm tra truy cập và điều kiện gọi | Không chỉ dựa vào việc đã tìm được metadata |
| `AccessibleObject.canAccess(receiver)` | Hỏi tại vị trí caller hiện tại có thể sử dụng member không, xét trạng thái accessible | Không tự thay đổi quyền; với field/method instance cần receiver phù hợp; với static member/constructor cần `null` |
| `AccessibleObject.trySetAccessible()` | Thử bật cờ bỏ qua kiểm tra truy cập Java ở những nơi được phép | Có thể trả `false` nếu bị module encapsulation ngăn; không cấp quyền vô điều kiện |

**Điểm thực hành:** `getDeclaredFields()` và các API trả mảng Reflection **không bảo đảm thứ tự**. Khi in kết quả để test/đối chiếu snapshot, hãy `sorted()`; nếu không, test sẽ phụ thuộc thứ tự ngẫu nhiên của môi trường thay vì ý nghĩa của access modifier.

`canAccess` không phù hợp làm một hàm tổng quát nhận `null` cho mọi member: với instance field và instance method, phải truyền receiver tương thích; constructor và static member dùng `null`. Nếu receiver sai loại, API có thể ném `IllegalArgumentException`. Nó cũng kiểm tra theo **caller thực tế**, nên cùng một `Field` có thể cho kết quả khác khi `canAccess` được gọi từ lớp test khác.

## 3. Fixture Java 17+: ba tệp, hai package và nhiều ngữ cảnh caller

Cấu trúc thư mục:

~~~text
fixture/
  BaseFixture.java
  SamePackageProbe.java
client/
  FixtureAccessDemo.java
~~~

Ba class này được biên dịch và chạy trên **classpath/unnamed module** để cô lập quy tắc modifier/package trước khi thử named modules. Cùng tên package trong hai source file đi kèm cùng class loader, nên được xem là cùng runtime package.

### 3.1. `fixture/BaseFixture.java`

Định nghĩa đủ bốn mức truy cập cho **field, constructor và method**:

~~~java
package fixture;

public class BaseFixture {
    public int publicField = 10;
    protected int protectedField = 20;
    int packageField = 30;
    private int privateField = 40;

    public BaseFixture() {}
    protected BaseFixture(String text) {}
    BaseFixture(int number) {}
    private BaseFixture(boolean value) {}

    public String publicMethod() { return "public"; }
    protected String protectedMethod() { return "protected"; }
    String packageMethod() { return "package"; }
    private String privateMethod() { return "private"; }
}
~~~

- Không có từ khóa modifier trước `packageField`/`packageMethod`/`BaseFixture(int)` nghĩa là **package-private**, không phải `protected`.
- `privateField` vẫn tồn tại trong metadata của `BaseFixture`; việc test bên ngoài không được truy cập trực tiếp không có nghĩa field này bị Reflection “ẩn hoàn toàn”.
- Các constructor được cố ý cho kiểu tham số khác nhau. Vì Java không cho hai constructor trùng parameter signature chỉ khác modifier, đó là cách tạo fixture với cả bốn mức quyền mà vẫn hợp lệ.

### 3.2. `fixture/SamePackageProbe.java`

Một caller **khác class, cùng package** kiểm tra các field và constructor:

~~~java
package fixture;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class SamePackageProbe {
    public static void run() throws Exception {
        BaseFixture base = new BaseFixture();
        for (String name : new String[]{
                "publicField", "protectedField", "packageField", "privateField"}) {
            Field f = BaseFixture.class.getDeclaredField(name);
            System.out.println("same-package " + name + ": " + f.canAccess(base));
        }
        Constructor<?> ctor = BaseFixture.class.getDeclaredConstructor(int.class);
        System.out.println("same-package constructor: " + ctor.canAccess(null));
    }
}
~~~

Quan sát kỳ vọng: `public`, `protected` và package-private đều `true`, còn `private` trả `false`. `SamePackageProbe` không phải nestmate của `BaseFixture`; cùng package không đủ để truy cập private.

`getDeclaredConstructor(int.class)` tìm constructor package-private. `ctor.canAccess(null)` trả `true` do caller `SamePackageProbe` thuộc cùng runtime package. Đây là kiểm tra quyền constructor, với receiver bắt buộc là `null`.

### 3.3. `client/FixtureAccessDemo.java`

Một caller ở **package khác** đồng thời là subclass, giúp thấy ràng buộc receiver của `protected`:

~~~java
package client;

import fixture.BaseFixture;
import fixture.SamePackageProbe;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.stream.Collectors;

public class FixtureAccessDemo extends BaseFixture {
    private void checkProtected() throws Exception {
        Field f = BaseFixture.class.getDeclaredField("protectedField");
        System.out.println("subclass receiver protected: " + f.canAccess(this));
        System.out.println("base receiver protected: " +
                f.canAccess(new BaseFixture()));
    }

    public static void main(String[] args) throws Exception {
        BaseFixture base = new BaseFixture();

        System.out.println("declared fields: " +
                Arrays.stream(BaseFixture.class.getDeclaredFields())
                        .map(Field::getName).sorted()
                        .collect(Collectors.joining(", ")));
        System.out.println("public fields: " +
                Arrays.stream(BaseFixture.class.getFields())
                        .filter(f -> f.getDeclaringClass() == BaseFixture.class)
                        .map(Field::getName).sorted()
                        .collect(Collectors.joining(", ")));

        for (String name : new String[]{
                "publicField", "protectedField", "packageField", "privateField"}) {
            Field f = BaseFixture.class.getDeclaredField(name);
            System.out.println("client " + name + ": " + f.canAccess(base));
        }

        System.out.println("public method: " +
                BaseFixture.class.getMethod("publicMethod").invoke(base));
        try {
            BaseFixture.class.getMethod("privateMethod");
            System.out.println("unexpected lookup");
        } catch (NoSuchMethodException expected) {
            System.out.println("getMethod(private): NoSuchMethodException");
        }

        SamePackageProbe.run();
        new FixtureAccessDemo().checkProtected();

        Field privateField = BaseFixture.class.getDeclaredField("privateField");
        System.out.println("private trySetAccessible: " +
                privateField.trySetAccessible());
        System.out.println("private field value: " + privateField.getInt(base));
    }
}
~~~

Lưu ý `FixtureAccessDemo` là subclass của `BaseFixture` nhưng vòng lặp `canAccess(base)` truyền một **receiver thuộc chính `BaseFixture`**, không thuộc `FixtureAccessDemo`. Điều này được cố ý để thử tình huống `protected` bị từ chối xuyên package. Trong `checkProtected()`, receiver `this` lại là instance của subclass, nên phép kiểm tra `protected` thành công.

### 3.4. Biên dịch, chạy và kiểm tra stdout

Từ thư mục chứa hai folder `fixture` và `client`, tạo thư mục `out` rồi chạy:

~~~bash
mkdir out
javac --release 17 -d out fixture/BaseFixture.java fixture/SamePackageProbe.java client/FixtureAccessDemo.java
java -cp out client.FixtureAccessDemo
~~~

Kết quả đã đối chiếu bằng JDK với `--release 17`:

~~~text
declared fields: packageField, privateField, protectedField, publicField
public fields: publicField
client publicField: true
client protectedField: false
client packageField: false
client privateField: false
public method: public
getMethod(private): NoSuchMethodException
same-package publicField: true
same-package protectedField: true
same-package packageField: true
same-package privateField: false
same-package constructor: true
subclass receiver protected: true
base receiver protected: false
private trySetAccessible: true
private field value: 40
~~~

**Giải thích các kết quả quan trọng:**

1. `declared fields` liệt kê cả bốn field, vì `getDeclaredFields()` không lọc theo `public`. `public fields` chỉ có `publicField` trong fixture (đã lọc theo `getDeclaringClass()`).
2. Ở vòng lặp `client`, `canAccess(base)` chỉ đúng cho `publicField`. Ba field còn lại không tự trở nên truy cập được vì đã lấy `Field` từ `getDeclaredField`.
3. `BaseFixture.class.getMethod("privateMethod")` không tìm thấy vì API này chỉ tìm public method: nó ném **`NoSuchMethodException`**. Nếu dùng `getDeclaredMethod("privateMethod")` sẽ tìm được metadata, nhưng việc `invoke` còn cần quyền.
4. `SamePackageProbe` thấy được `protectedField` và `packageField`. `privateField` vẫn bị từ chối vì hai lớp top-level không phải nestmates.
5. `subclass receiver protected: true` nhưng `base receiver protected: false` chứng minh điều kiện receiver đặc thù của `protected` khi caller là subclass khác package.
6. `private trySetAccessible: true` là kết quả trong ví dụ chạy **trên classpath/unnamed module**. `trySetAccessible()` có thể trả `false` trong một ứng dụng named modules nếu package đích không `opens` tới module caller. Không dùng kết quả `true` ở đây để tuyên bố Java luôn cho vượt quyền private.
7. Sau khi `trySetAccessible()` thành công, `getInt(base)` có thể đọc giá trị `privateField = 40`. Đây là thao tác chủ động yêu cầu suppress access checks trong ngữ cảnh được phép, khác với kiểm tra truy cập mặc định bằng `canAccess`.

## 4. Mở rộng cùng fixture sang kiểm thử method và constructor

Đối với method, dùng cùng các tên từ `BaseFixture`:

| Method | Tìm bằng `getDeclaredMethod(...)` | Tìm bằng `getMethod(...)` | Quyền gọi mặc định từ class khác package |
| --- | --- | --- | --- |
| `publicMethod()` | Có | Có | Được, nếu lớp/package đích khả kiến |
| `protectedMethod()` | Có | Không theo API này trên fixture chỉ có member protected | Phụ thuộc subclass và receiver |
| `packageMethod()` | Có | Không | Không |
| `privateMethod()` | Có | Không | Không |

Nếu muốn test **quyền invocation** thay vì khả năng lookup, trước tiên lấy `Method m = BaseFixture.class.getDeclaredMethod("privateMethod")`, rồi gọi `m.canAccess(base)` hoặc `m.invoke(base)`; trường hợp không được phép invoke sẽ báo `IllegalAccessException`. `NoSuchMethodException` lại thuộc bước **tìm method theo API/chữ ký**. Đừng gộp hai nguyên nhân này thành cùng một lỗi.

Với constructor:

- `BaseFixture.class.getConstructor()` tìm được constructor public không tham số.
- `BaseFixture.class.getDeclaredConstructor(String.class)` tìm thấy constructor `protected`.
- `BaseFixture.class.getDeclaredConstructor(int.class)` tìm thấy constructor package-private.
- `BaseFixture.class.getDeclaredConstructor(boolean.class)` tìm thấy constructor `private`.
- `BaseFixture.class.getConstructor(boolean.class)` ném `NoSuchMethodException` vì constructor tương ứng không public.

`Constructor.canAccess(null)` kiểm tra quyền tại caller hiện tại; dùng `null` vì constructor chưa có đối tượng receiver. `newInstance(...)` có thể ném `IllegalAccessException` khi không được phép gọi constructor, `InvocationTargetException` nếu chính constructor ném exception, hoặc `InstantiationException` với class abstract; những ngoại lệ này không được nhầm với lỗi không tìm thấy constructor.

Khi kiểm thử từng loại member, nên tạo **bộ case độc lập** (field lookup, field get/set, method lookup/invoke, constructor lookup/newInstance) với expected exception cụ thể. Nếu chỉ viết một phép thử duy nhất `getDeclaredXxx`, fixture mới kiểm chứng khả năng nhìn metadata chứ chưa kiểm chứng quyền sử dụng.

## 5. Named modules, `exports` và `opens` thay đổi điều gì?

Ví dụ ở mục 3 cố ý chạy classpath trong unnamed module. Một bộ test cho **JPMS** cần dùng các named module riêng, bởi vì `exports` và `opens` kiểm soát những khía cạnh khác nhau:

- `exports fixture;` cho phép module khác (nếu đọc module đích) dùng các **public types/public members** của package `fixture` theo luật truy cập bình thường. Nó không cho bên ngoài tùy ý gọi `privateMethod`.
- `opens fixture to client.app;` cấp khả năng **deep reflection** tới package `fixture` cho module `client.app`, khi các điều kiện API và module khác được đáp ứng. `opens` không tự biến member private thành public Java source.
- Nếu package không open cho bên gọi, `trySetAccessible()` trên private member ở module khác có thể trả `false`, còn `setAccessible(true)` có thể ném `InaccessibleObjectException`.
- Các module có thể sử dụng `--add-opens source.module/fixture=client.app` cho nhu cầu chẩn đoán có kiểm soát, nhưng không nên coi cờ này là điều kiện mặc định để ứng dụng hoạt động.

Ví dụ minh họa riêng về descriptor (không phải các file bắt buộc để chạy demo classpath phía trên):

~~~java
// module-info.java của module fixture.lib:
module fixture.lib {
    exports fixture;
    opens fixture to client.app;
}

// module-info.java của module client.app:
module client.app {
    requires fixture.lib;
}
~~~

Nếu muốn kiểm thử chặn deep reflection, hãy tạo thêm fixture ở một **named module không opens package** và xác nhận `trySetAccessible()` thất bại từ module khác. Cần lưu ý cách khởi chạy/module path, không dùng kết quả của classpath demo làm expected output cho test JPMS.

## 6. Thiết kế ma trận kiểm thử ổn định

Không nên thiết kế fixture chỉ để “chạy được một lần”. Với công cụ Reflection, nên có ma trận kiểm chứng rõ:

| Trục thử | Những trường hợp tối thiểu |
| --- | --- |
| Thành viên | Field, method, constructor; cân nhắc static, inherited, final ở test mở rộng |
| Modifier | `public`, `protected`, package-private, `private` |
| Quan hệ caller | Chính class, class cùng package khác nest, subclass khác package, class không liên quan khác package |
| Receiver | Đúng subclass receiver, base-class receiver, `null` cho static/constructor, receiver không tương thích |
| Lookup | `getDeclaredXxx` đối chiếu `getXxx`; kiểm tra `NoSuchFieldException`/`NoSuchMethodException` |
| Sử dụng | `canAccess`, `Field.get/set`, `Method.invoke`, `Constructor.newInstance`, `trySetAccessible` |
| Module | Classpath unnamed module, named modules `exports`, named modules `opens`/không `opens` |
| Kết quả | Đúng dữ liệu, đúng exception, metadata đầy đủ, định dạng log và thứ tự ổn định |

**Không dùng thứ tự mảng Reflection làm expected output.** Thực hiện `sorted()` hoặc so tập hợp nếu thứ tự không có ý nghĩa. Tránh test phụ thuộc vào `setAccessible(true)` trên các package JDK bị đóng; mục tiêu ở đây là kiểm chứng các luật truy cập của fixture, không phải tìm cách vô hiệu hóa encapsulation.

Nếu thử với nested class, hãy ghi rõ chúng thuộc nest nào bằng `Class.getNestHost()` và `getNestMembers()`. Một nested test class có thể có quyền private đối với enclosing class ngay từ đầu, khiến phép thử “private phải bị từ chối” trở thành **false negative về thiết kế test**.

## 7. Lỗi thường gặp khi dùng fixture

1. **Chỉ có class public:** không chứng minh công cụ xử lý protected/package-private/private đúng.
2. **Đặt fixture và runner trong cùng class/nest:** các quyền private có thể được chia sẻ, làm mất case cần bị từ chối.
3. **Lấy `Field` thành công rồi kết luận có quyền đọc:** metadata lookup và access check là hai bước độc lập.
4. **Dùng `getMethods()` để tìm private method:** API đó chỉ phục vụ public methods; muốn metadata private dùng `getDeclaredMethod`.
5. **Dùng `canAccess(null)` cho instance field:** truyền sai receiver; phải cấp instance đúng kiểu.
6. **Dùng `trySetAccessible()` rồi bỏ qua trạng thái:** nó trả boolean; khi không cho phép cần xử lý `false` đúng.
7. **Nhầm protected member với public cho mọi subclass receiver:** ở package khác, receiver của instance member chịu quy tắc riêng.
8. **Dựa vào named module mà vẫn dùng expected output của classpath:** JPMS có thể thay đổi kết quả reflective access.
9. **So sánh danh sách method/field theo thứ tự Reflection trả về:** không có cam kết thứ tự, gây test không ổn định.
10. **Không phân biệt exception lookup với exception invoke:** thiếu method và thiếu quyền gọi là hai nguyên nhân khác nhau.

**Ghi nhớ:** một fixture Reflection hữu ích phải tách bạch **metadata thấy được**, **truy cập mặc định theo caller/receiver**, và **quyền deep reflection được cấp có điều kiện**. Ví dụ ba lớp ở hai package vừa cho thấy đủ bốn access modifier, vừa chứng minh tại sao `protected`, package-private và `private` cần được test từ nhiều ngữ cảnh, không chỉ một lớp đơn lẻ.

**Tài liệu đối chiếu:**
- [`Class` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html)
- [`AccessibleObject` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/AccessibleObject.html)
- [`Field` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Field.html)
- [`Constructor` — Java SE 21](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Constructor.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-testing-002">Kiểm thử declared member khác inherited member</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử declared member khác inherited member trong Java Reflection

## 1. Bài toán: một thành viên thuộc lớp nào?

Khi thiết kế bài kiểm thử cho công cụ dùng Reflection, cần phân biệt hai câu hỏi:

1. **Member có được khai báo trực tiếp trong lớp này không?** Đây là câu hỏi về *declared member*.
2. **Có thể tra cứu member `public` thông qua lớp này không?** Đây là câu hỏi về các thành viên công khai mà API Reflection nhìn thấy, có thể bao gồm member kế thừa.

Hai câu hỏi có kết quả khác nhau. Giả sử `Child extends Parent`, `Parent` khai báo `public void run()` và `Child` không override: `Child.class.getDeclaredMethod("run")` ném `NoSuchMethodException`; `Child.class.getMethod("run")` lại tìm được method do `Parent` khai báo.

Một bài test sai thường dùng `getDeclaredMethods()` để kiểm tra *toàn bộ API mà đối tượng Child có thể gọi*, rồi kết luận thiếu method kế thừa. Ngược lại, nếu dùng `getMethods()` để kiểm tra *các method do Child tự khai báo*, bài test lại có thể tính cả method từ superclass và interface.

Bài học hướng dẫn tạo fixture nhỏ, dùng assertion dương và âm, kiểm tra `getDeclaringClass()` và xử lý các trường hợp override, interface default/static method, field, constructor, overload, bridge/synthetic member. Tập trung vào **kiểm chứng metadata**, không đánh đồng việc tra cứu với quyền gọi phương thức private.

## 2. Các API Reflection cần đối chiếu

| Đối tượng | Declared tại lớp đang xét | Public nhìn thấy qua lớp |
| --- | --- | --- |
| Một method | `getDeclaredMethod(name, parameterTypes...)` | `getMethod(name, parameterTypes...)` |
| Danh sách method | `getDeclaredMethods()` | `getMethods()` |
| Một field | `getDeclaredField(name)` | `getField(name)` |
| Danh sách field | `getDeclaredFields()` | `getFields()` |
| Một constructor | `getDeclaredConstructor(parameterTypes...)` | `getConstructor(parameterTypes...)` |
| Danh sách constructor | `getDeclaredConstructors()` | `getConstructors()` |

Quy tắc quan trọng:

- Nhóm **`getDeclared*`** không đi lên superclass để lấy thành viên kế thừa. Nó trả metadata khai báo trong đúng lớp/interface hiện tại, kể cả non-public member. Có thể chứa bridge/synthetic member do compiler sinh.
- `getMethod(s)` và `getField(s)` chỉ trả **public member** theo phạm vi tra cứu của API, trong đó có thể có member được kế thừa. Chúng không phải danh sách mọi `protected` hoặc private member có trong cây kế thừa.
- Constructor **không được kế thừa**. Vì thế hai API constructor chỉ khác phạm vi truy cập public hay declared của **chính lớp đó**, không phải “constructor của cha và con”.
- Việc tìm được private member với `getDeclaredMethod` / `getDeclaredField` **không bảo đảm được quyền gọi/đọc**. `Method.invoke`, `Field.get` và `Constructor.newInstance` còn chịu kiểm tra truy cập, cùng ranh giới module khi có JPMS.
- API trả mảng như `getDeclaredMethods()` không cam kết thứ tự tương ứng với source code. Đừng assert phần tử tại index 0 là một method cố định.

### Ba khái niệm khác nhau

**Khai báo:** nơi member xuất hiện trong lớp/interface. `Method.getDeclaringClass()` hay `Field.getDeclaringClass()` giúp xác định.

**Kế thừa:** hành vi Java cho phép lớp con nhận từ kiểu cha theo các quy tắc override/hiding/access; một `private` method của cha không phải member được con kế thừa để override.

**Truy cập:** người gọi có quyền thực thi/read/write member hay không. Một member có thể có metadata nhưng không thể được truy cập theo ngữ cảnh gọi. Do đó test “tồn tại metadata” phải độc lập với test “gọi thành công”.

## 3. Fixture kiểm thử đủ nhỏ để nhìn ra nguyên nhân

Chúng ta cần một lớp cha, một lớp con và một interface:

~~~text
Work (interface)
 ├── defaultWork()      // default instance method
 └── utility()          // static interface method

Base
 ├── public visible()
 ├── public replace()   // sẽ bị override
 ├── protected protectedBase()
 ├── private privateBase()
 └── public int baseCount

Child extends Base implements Work
 ├── public local()
 ├── public replace()   // override
 ├── public int ownCount
 ├── private int hidden
 ├── public Child()
 └── private Child(int)
~~~

Fixture có cả trường hợp **thành công** và **thất bại có chủ ý**. Chẳng hạn, test không chỉ xác nhận `getMethod("visible")` thành công mà còn yêu cầu `getDeclaredMethod("visible")` thất bại. Nếu cả hai phép kiểm tra đều thành công, khả năng cao đã vô tình khai báo lại `visible` trong Child hoặc test đang chọn nhầm lớp.

Các assertion cốt lõi:

- `Child.class.getMethod("visible")` có `getDeclaringClass() == Base.class`.
- `Child.class.getMethod("replace")` có `getDeclaringClass() == Child.class`, vì Child khai báo override.
- `Base.class.getDeclaredMethod("privateBase")` tìm được metadata; `Child.class.getDeclaredMethod("privateBase")` không tìm được.
- `Child.class.getMethod("defaultWork")` lấy được default method của `Work`; `Child.class.getMethod("utility")` không tìm được static interface method.
- `Child.class.getField("baseCount")` lấy được public field của Base; `Child.class.getDeclaredField("baseCount")` thất bại.
- `Child.class.getDeclaredConstructor(int.class)` tìm được private constructor; `Child.class.getConstructor(int.class)` thì không.
- Danh sách trả về từ `getDeclaredMethods`/`getMethods` chỉ được đối chiếu theo **tên/tập thuộc tính có chủ đích**, không theo thứ tự phần tử.

## 4. Chương trình Java 17+ chạy độc lập và tự kiểm chứng

Tạo tệp **`DeclaredInheritedDemo.java`**:

~~~java
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class DeclaredInheritedDemo {
    public interface Work {
        default String defaultWork() {
            return "default";
        }

        static String utility() {
            return "utility";
        }
    }

    public static class Base {
        public int baseCount = 10;
        protected int protectedCount = 20;
        private int privateCount = 30;

        public String visible() {
            return "base";
        }

        public String replace() {
            return "base version";
        }

        protected void protectedBase() {}
        private void privateBase() {}
    }

    public static class Child extends Base implements Work {
        public int ownCount = 40;
        private int hidden;

        public Child() {}

        private Child(int value) {
            ownCount = value;
        }

        public String local() {
            return "child";
        }

        @Override
        public String replace() {
            return "child version";
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static boolean declaredMethod(Class<?> type, String name) {
        try {
            type.getDeclaredMethod(name);
            return true;
        } catch (NoSuchMethodException ex) {
            return false;
        }
    }

    static boolean publicMethod(Class<?> type, String name) {
        try {
            type.getMethod(name);
            return true;
        } catch (NoSuchMethodException ex) {
            return false;
        }
    }

    static boolean declaredField(Class<?> type, String name) {
        try {
            type.getDeclaredField(name);
            return true;
        } catch (NoSuchFieldException ex) {
            return false;
        }
    }

    static boolean publicField(Class<?> type, String name) {
        try {
            type.getField(name);
            return true;
        } catch (NoSuchFieldException ex) {
            return false;
        }
    }

    static Set<String> methodNames(Method[] methods) {
        return Arrays.stream(methods)
                .map(Method::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public static void main(String[] args) throws Exception {
        Class<Child> childType = Child.class;

        check(!declaredMethod(childType, "visible"),
                "Child must not declare inherited visible()");
        Method inherited = childType.getMethod("visible");
        check(inherited.getDeclaringClass() == Base.class,
                "visible() belongs to Base");
        check(inherited.invoke(new Child()).equals("base"),
                "inherited invocation");

        check(declaredMethod(childType, "replace"),
                "Child declares override");
        Method override = childType.getMethod("replace");
        check(override.getDeclaringClass() == Child.class,
                "replace() belongs to Child");

        check(declaredMethod(Base.class, "privateBase"),
                "Base declares private method");
        check(!declaredMethod(childType, "privateBase"),
                "Child does not declare Base private method");
        check(!publicMethod(childType, "privateBase"),
                "private method is not public");
        check(declaredMethod(Base.class, "protectedBase"),
                "Base declares protected method");
        check(!publicMethod(childType, "protectedBase"),
                "protected method is not public");

        Method defaultMethod = childType.getMethod("defaultWork");
        check(defaultMethod.getDeclaringClass() == Work.class,
                "default method declared in Work");
        check(!declaredMethod(childType, "defaultWork"),
                "Child does not declare default method");
        check(!publicMethod(childType, "utility"),
                "static interface method not inherited");
        check(publicMethod(Work.class, "utility"),
                "Work owns static method");

        Field base = childType.getField("baseCount");
        check(base.getDeclaringClass() == Base.class,
                "baseCount declared by Base");
        check(!declaredField(childType, "baseCount"),
                "Child does not declare baseCount");
        check(declaredField(childType, "hidden"),
                "Child declares private hidden");
        check(!publicField(childType, "hidden"),
                "private hidden is not public");
        check(!publicField(childType, "protectedCount"),
                "protected field is not public");

        Constructor<Child> noArgs = childType.getConstructor();
        Constructor<Child> privateCtor =
                childType.getDeclaredConstructor(int.class);
        check(noArgs.getDeclaringClass() == Child.class
                        && privateCtor.getDeclaringClass() == Child.class,
                "both constructors belong to Child");
        check(childType.getConstructors().length == 1,
                "one public constructor");
        check(childType.getDeclaredConstructors().length == 2,
                "two declared constructors");

        Set<String> declared = methodNames(
                childType.getDeclaredMethods());
        Set<String> visible = methodNames(childType.getMethods());
        check(declared.contains("local")
                        && declared.contains("replace")
                        && !declared.contains("visible"),
                "declared method set");
        check(visible.contains("visible")
                        && visible.contains("defaultWork")
                        && visible.contains("local"),
                "public method set");

        System.out.println("declared visible: "
                + declaredMethod(childType, "visible"));
        System.out.println("public visible: "
                + publicMethod(childType, "visible"));
        System.out.println("visible owner: "
                + inherited.getDeclaringClass().getSimpleName());
        System.out.println("override owner: "
                + override.getDeclaringClass().getSimpleName());
        System.out.println("default owner: "
                + defaultMethod.getDeclaringClass().getSimpleName());
        System.out.println("interface static inherited: "
                + publicMethod(childType, "utility"));
        System.out.println("base field owner: "
                + base.getDeclaringClass().getSimpleName());
        System.out.println("local private public: "
                + publicField(childType, "hidden"));
        System.out.println("public constructors: "
                + childType.getConstructors().length);
        System.out.println("declared constructors: "
                + childType.getDeclaredConstructors().length);
        System.out.println("PASS");
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac DeclaredInheritedDemo.java
java DeclaredInheritedDemo
~~~

Kết quả:

~~~text
declared visible: false
public visible: true
visible owner: Base
override owner: Child
default owner: Work
interface static inherited: false
base field owner: Base
local private public: false
public constructors: 1
declared constructors: 2
PASS
~~~

Chương trình tự ném `AssertionError` khi một điều kiện không đạt, vì vậy các kiểm tra vẫn hoạt động khi chạy Java **không bật `-ea`**. Điều này khác với câu lệnh `assert` thông thường của Java, vốn mặc định bị vô hiệu hóa.

**Không cần gọi `setAccessible(true)`:** chúng ta xác minh metadata private member được khai báo ở đâu, không thử vượt qua kiểm tra quyền truy cập.

## 5. Đọc kết quả: declared, inherited, override và interface

### 5.1. `getDeclaredMethod` khác `getMethod`

~~~java
Child.class.getMethod("visible");         // tìm được method Base
Child.class.getDeclaredMethod("visible"); // ném NoSuchMethodException
~~~

Đoạn trên chỉ minh họa khác biệt; lời gọi thứ hai cần được đặt trong `try/catch` hoặc phương thức `throws NoSuchMethodException` khi sử dụng độc lập.

`getDeclaredMethod` đọc từ **đúng Class của Child**. Vì `visible` chỉ được khai báo ở Base nên không tìm được. `getMethod` có phạm vi public method nên tìm thấy method kế thừa. Dùng:

~~~java
Method member = Child.class.getMethod("visible");
boolean fromBase = member.getDeclaringClass() == Base.class;
~~~

Đối với method không được Child override, `getDeclaringClass()` cho biết **nơi method được khai báo**, không phải runtime class của instance được truyền vào `invoke`.

### 5.2. Override: phương thức được khai báo lại tại Child

`Child` ghi đè `replace()` nên cả `Child.class.getDeclaredMethod("replace")` và `Child.class.getMethod("replace")` đều tìm được bản khai báo của Child. Nếu dùng `Method.invoke(new Child())`, dynamic dispatch theo receiver vẫn quan trọng; để chứng minh metadata đang chọn *bản nào*, dùng `getDeclaringClass()`.

Không đánh đồng “method thuộc quan hệ kế thừa” với “method metadata do cha khai báo”. Một override thực sự là **declaration mới tại Child** với quan hệ override tới method ở cha.

### 5.3. Protected và private không nằm trong public lookup

`Base.class.getDeclaredMethod("protectedBase")` và `Base.class.getDeclaredMethod("privateBase")` tìm được metadata, còn `Child.class.getMethod(...)` cho hai tên đó thất bại.

**Protected member có thể được kế thừa** theo quy tắc Java, nhưng không vì thế trở thành public. Private member của Base không được Child kế thừa/override theo cách của instance method public/protected. Nếu công cụ cần khảo sát tất cả declared members trong hệ phân cấp, phải tự duyệt qua từng `Class<?>` của superclass rồi dùng `getDeclaredMethods()` trên đúng lớp.

### 5.4. Interface default khác interface static

Method `defaultWork()` thuộc interface `Work` và được Child kế thừa dưới dạng hành vi default. Khi không override, `Child.class.getMethod("defaultWork").getDeclaringClass()` là `Work.class`.

`Work.utility()` là static method của interface, **không được Child kế thừa**: `Child.class.getMethod("utility")` thất bại, còn `Work.class.getMethod("utility")` tìm được. Đây là test quan trọng để tránh suy luận rằng mọi method public của interface đều xuất hiện trong API public của lớp triển khai.

### 5.5. Field: declared ở Child, inherited từ Base

`Child.class.getField("baseCount")` tìm public field thuộc Base, nhưng `Child.class.getDeclaredField("baseCount")` không tìm được. Ngược lại, `Child.class.getDeclaredField("hidden")` tìm private field của Child, còn `Child.class.getField("hidden")` thất bại vì đó không phải public field.

Một field **không override đa hình như instance method**. Nếu Child khai báo field cùng tên `baseCount`, nó tạo một field khác **hide** field của Base. Kiểm tra nguồn khai báo qua `Field.getDeclaringClass()` thay vì chỉ dùng `getName()`.

### 5.6. Constructor không bao giờ kế thừa

Constructor của Base không tự trở thành constructor của Child. `Child.class.getDeclaredConstructors()` chỉ nói về constructor khai báo trong Child; `getConstructors()` trả các constructor public của Child.

Với fixture của bài, Child khai báo một constructor public không tham số và một constructor private nhận `int`. Vì vậy `getConstructors().length == 1`, `getDeclaredConstructors().length == 2`. Những assertion về **số lượng constructor này** hợp lý vì fixture nhỏ và hoàn toàn do test kiểm soát.

## 6. Chọn đúng overload bằng danh sách kiểu tham số

Một tên method có thể có nhiều overload:

~~~java
class Example {
    public void parse(String text) {}
    public void parse(int value) {}
}
~~~

Hai overload đều tên `parse`; vì vậy kiểm tra `getDeclaredMethods()` rồi so sánh duy nhất `getName()` là chưa đủ.

~~~java
Method parseString =
        Example.class.getDeclaredMethod("parse", String.class);
Method parseNumber =
        Example.class.getDeclaredMethod("parse", int.class);
~~~

*Các fragment giả định có import `java.lang.reflect.Method` và xử lý `NoSuchMethodException`.*

Danh sách kiểu tham số **có thứ tự**: `f(int, String)` khác `f(String, int)`. Nếu xây khóa để so sánh toàn bộ danh sách method, có thể dùng `name + ordered parameterTypes + returnType + declaringClass`. Với bytecode chứa bridge/covariant return, return type cũng có thể cần để phân biệt metadata dù việc overload bằng mỗi return type không hợp lệ trong Java source.

Lưu ý `getDeclaredMethod(name, parameterTypes)` không nhận kiểu trả về. Nó hoạt động theo hợp đồng tra cứu của Reflection, không phải API chọn một method theo tùy ý mọi đặc tính trong class file.

## 7. Không kiểm thử theo thứ tự mảng do Reflection trả về

Các API `getDeclaredMethods()`, `getMethods()`, `getDeclaredFields()` và `getDeclaredConstructors()` **không cam kết thứ tự** thành viên. Những test sau dễ sai:

~~~java
// Sai vì phụ thuộc thứ tự không được API cam kết:
// boolean firstIsLocal = Child.class.getDeclaredMethods()[0]
//         .getName().equals("local");
~~~

Thay bằng:

~~~java
Set<String> names = Arrays.stream(Child.class.getDeclaredMethods())
        .map(Method::getName)
        .collect(Collectors.toSet());

boolean hasLocal = names.contains("local");
~~~

*Fragment cần import `Set`, `Arrays`, `Method`, `Collectors`; lớp `Child` lấy từ ví dụ chính.*

Nếu cần chứng minh nhiều overload khác nhau, thay `Set<String>` tên method bằng một tập khóa có danh sách kiểu tham số được chuẩn hóa. Việc chỉ nhìn tên phù hợp với bài kiểm tra **sự hiện diện sơ bộ**, nhưng không phù hợp để xác thực một chữ ký cụ thể.

**Synthetic/bridge member:** compiler có thể tạo method phụ để duy trì dispatch của generic/covariant return. `Method.isBridge()` và `Method.isSynthetic()` giúp nhận diện. Nếu mục tiêu là API gần với source, công cụ có thể chọn lọc; nếu mục tiêu là phân tích bytecode, phải giữ chúng. Đừng kiểm thử “đúng N method” ở những tình huống có thể chứa bridge nếu chưa định nghĩa cách đếm.

## 8. Case JUnit 5 hoặc test framework: kiểm cả thành công và thất bại

Nếu dự án đã dùng JUnit 5, có thể diễn đạt điều kiện theo cách sau:

~~~java
// Fragment đặt trong lớp test có import org.junit.jupiter.api.Test,
// import static org.junit.jupiter.api.Assertions.*;

@Test
void inheritedPublicMethodIsNotDeclaredInChild() throws Exception {
    assertThrows(NoSuchMethodException.class,
            () -> Child.class.getDeclaredMethod("visible"));

    Method inherited = Child.class.getMethod("visible");
    assertEquals(Base.class, inherited.getDeclaringClass());
}

@Test
void overrideIsDeclaredInChild() throws Exception {
    Method overridden = Child.class.getDeclaredMethod("replace");
    assertEquals(Child.class, overridden.getDeclaringClass());
}
~~~

Đây là **JUnit fragment**, không phải một chương trình độc lập: cần dependency `org.junit.jupiter:junit-jupiter` và các lớp `Child`, `Base` của fixture. Các phép kiểm tra như vậy có tín hiệu rõ hơn so với test chỉ xem method count.

Khi dùng Java thuần, ví dụ mục 4 đã làm cùng việc bằng `check(...)` và `try/catch` trong helper. Chọn một phong cách thống nhất với hạ tầng kiểm thử của dự án.

## 9. Các tình huống đặc biệt trong hệ phân cấp

**Nhiều cấp superclass:** `Child.class.getDeclaredMethods()` không cộng tất cả declared methods của Parent, Grandparent. Nếu muốn một danh sách chi tiết toàn bộ hệ phân cấp, tự duyệt `getSuperclass()` và giữ `getDeclaringClass()` để phân biệt các khai báo.

**Interface kế thừa interface:** `SubInterface.class.getMethod(...)` có thể tìm method public từ `SuperInterface`. Nhưng `getDeclaredMethod(...)` trên SubInterface chỉ tìm method trực tiếp khai báo ở SubInterface.

**Field hiding:** Parent và Child có thể có field public cùng tên. Khi phân tích toàn bộ metadata, hai field thuộc hai owner khác nhau. `getField(name)` chọn theo quy tắc tra cứu, không giúp liệt kê mọi field bị hide trong cây lớp.

**Method overload:** method khác nhau theo tên và danh sách tham số, không chỉ tên. Dùng đúng thứ tự `Class<?>... parameterTypes`.

**Generic bridge:** một generic superclass và một subclass override bằng kiểu cụ thể có thể sinh bridge method. Việc dùng `getDeclaredMethods()` để đếm source methods cần một chính sách loại hoặc giữ bridge/synthetic.

**Interface static:** static method khai báo ở interface không truyền thành member của class triển khai; khác với default instance method.

**Quyền module:** method có thể nằm trong metadata mà không được invoke/đọc do Java access checks; phân biệt lookup thành công với sử dụng thành công.

## 10. Checklist thiết kế bài kiểm thử

| Trường hợp | Assertion nên có |
| --- | --- |
| Own public method | Declared lookup có; public lookup có; owner là Child |
| Inherited public method | Declared lookup không; public lookup có; owner là Base |
| Override | Declared lookup có; owner là Child |
| Parent private method | Parent declared lookup có; Child public/declared lookup không |
| Parent protected method | Parent declared lookup có; Child public lookup không |
| Interface default method | Child public lookup có; nếu chưa override thì Child declared lookup không |
| Interface static method | Interface public lookup có; Child public lookup không |
| Child private field | Child declared lookup có; public lookup không |
| Inherited public field | Child public lookup có; Child declared lookup không; owner là Base |
| Constructor private | Declared constructor có; public constructor không |
| Constructor của cha | Không được kế thừa thành constructor của con |
| Hai overload cùng tên | Kiểm danh sách kiểu tham số đúng thứ tự |
| Danh sách metadata | Không assert theo index; dùng set/key và policy synthetic/bridge |

Để báo lỗi rõ khi test thất bại, mỗi assertion nên ghi cả tên member và kỳ vọng. Ví dụ “Child must not declare visible()” cụ thể hơn “reflection failed”. Đồng thời, dùng fixture nhỏ để thay đổi test có nguyên nhân dễ nhận ra.

## 11. Tự kiểm tra

Giả sử `Child extends Base` và Base khai báo `public void start()`, Child không override:

1. `Child.class.getDeclaredMethod("start")` cho kết quả gì? **`NoSuchMethodException`.**
2. `Child.class.getMethod("start")` có tìm được không? **Có**, vì đây là public method kế thừa.
3. `getDeclaringClass()` của method đó là gì? **`Base.class`.**
4. Nếu Child override `start()`, `getDeclaredMethod("start")` có thành công không? **Có**, vì Child đã khai báo method mới.
5. Nếu Base có `private void secret()`, có tìm được bằng `Child.class.getDeclaredMethod("secret")` không? **Không**; cần tra trên `Base.class`.
6. `Child.class.getConstructors()` có trả các constructor public của Base không? **Không**.
7. Nếu interface có `static void util()`, lớp implements có tự kế thừa method này không? **Không**.
8. Có thể suy ra thứ tự khai báo từ index của `getDeclaredMethods()` không? **Không**.
9. Với hai overload `f(int)` và `f(String)`, một key chỉ có tên `f` có đủ không? **Không**.
10. Vì sao trong ví dụ dùng `check` thay vì Java `assert`? **Vì `assert` bị tắt khi không bật `-ea`, còn `check` vẫn kiểm tra.**

## 12. Kết luận

**Declared** mô tả nơi member được khai báo; **inherited/public lookup** mô tả phạm vi tra cứu member theo Java Reflection. Với method và field, hãy đối chiếu `getDeclared*` và `get*`, dùng `getDeclaringClass()` để xác nhận owner, kiểm thử cả trường hợp tìm được lẫn `NoSuchMethodException`/`NoSuchFieldException`. Constructor không được kế thừa. Interface default/static, override, field hiding, overload và bridge/synthetic method cần xem đúng ngữ nghĩa. Một test đáng tin không dựa vào thứ tự mảng hoặc số lượng method tổng quát; nó chứng minh chính xác quan hệ giữa kiểu, member và quyền truy cập.

Tài liệu tham khảo:

- [Java SE 21 — `Class`, các API `getMethod` / `getDeclaredMethod` / field / constructor](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Class.html)
- [Java SE 21 — `Method`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Method.html)
- [Java SE 21 — `Field`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Field.html)
- [Java SE 21 — `Constructor`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Constructor.html)
- [Java Language Specification SE 21 — Classes](https://docs.oracle.com/javase/specs/jls/se21/html/jls-8.html)

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-testing-003">Kiểm thử tra cứu primitive, wrapper, overload và generic erasure</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử tra cứu primitive, wrapper, overload và generic erasure bằng Java Reflection

## 1. Mục tiêu: kiểm chứng hành vi thật thay vì suy đoán cách Reflection chọn method

Khi dùng `Class.getMethod(...)` hoặc `Class.getDeclaredMethod(...)`, lỗi thường đến từ việc nhầm quy tắc **tra cứu method theo chữ ký** với quy tắc **gọi method trong Java**. Java compiler có thể chọn overload nhờ autoboxing, unboxing, primitive widening và tham chiếu kiểu kế thừa; còn `getMethod(name, parameterTypes)` đòi hỏi danh sách kiểu tham số **khớp đúng** chữ ký đã khai báo. Reflection không tự tìm “overload gần đúng nhất”.

Phạm vi bài kiểm thử:

1. Phân biệt `int.class` và `Integer.class` khi tra cứu.
2. Chứng minh các overload `choose(int)`, `choose(Integer)`, `choose(Number)`, `choose(Object)` là những method độc lập.
3. Kiểm tra `Method.invoke` có thể unbox/widen một số đối số sau khi đã tìm ra method, nhưng không tự chọn overload.
4. Tìm method khai báo `List<String>` bằng `List.class` do **generic erasure**, đồng thời đọc metadata `ParameterizedType` còn lưu trong class file.
5. Nhận ra bridge method do compiler sinh khi override method generic.
6. Viết kiểm thử có assertion, chạy độc lập bằng JDK 17+, output ổn định và báo sai khác ngay lúc chạy.

**Điểm then chốt:** một method **được tìm thấy** hay không là một câu hỏi; một `Method` đã tìm được **nhận đối số nào lúc `invoke`** là câu hỏi khác.

## 2. Phân biệt ba lớp quy tắc

| Tình huống | Java compiler khi gọi trực tiếp | `getMethod(...)` khi tra cứu | `Method.invoke(...)` sau khi có `Method` |
| --- | --- | --- | --- |
| `choose(5)` với nhiều overload | Thực hiện overload resolution tại compile time; `int` thường được ưu tiên nếu khớp chính xác | Phải chỉ rõ `int.class` để tìm `choose(int)` | Nhận boxed `Integer` làm đối số cho tham số `int` qua unboxing |
| `choose(Integer.valueOf(5))` | Ưu tiên overload `Integer` nếu có | Phải chỉ rõ `Integer.class` | Đối số là `Integer` phù hợp với tham số `Integer` |
| `choose(Short.valueOf((short) 5))` | Có thể chọn overload theo quy tắc method invocation applicable/most-specific | Không tự tìm `choose(int)` khi truyền `short.class` | Nếu đã có `choose(int)`, có thể unbox `Short` thành `short` rồi widening thành `int` |
| `accept(new ArrayList<String>())` với tham số `List<String>` | Gọi được vì `ArrayList<String>` implements `List<String>` | Tra cứu theo kiểu **đã khai báo** `List.class`, không phải `ArrayList.class` | Một instance `ArrayList` phù hợp với tham số `List` |
| `accept(List<String>)` | Compiler dùng generic để kiểm tra kiểu phần tử | Kiểu tham số runtime đã xóa: `List.class` | Kiểm tra kiểu đối số runtime, không tự kiểm tra từng phần tử là `String` |

`getMethod`/`getDeclaredMethod` không có khái niệm “chọn method có tham số superclass của kiểu truyền vào”, không làm boxing `int.class` thành `Integer.class` và không chọn overload dựa trên return type.

Lưu ý về `Method.invoke`: đối số cho kiểu nguyên thủy phải là wrapper có thể unbox; API cho phép primitive widening khi hợp lệ, nhưng không cho phép primitive narrowing. `null` không thể chuyển thành primitive. Đối với kiểu tham chiếu, đối tượng phải có kiểu tương thích. `invoke` cũng có thể thất bại do receiver sai, access control, lỗi argument hoặc exception ném từ method đích (được bọc trong `InvocationTargetException`).

## 3. Tra cứu primitive và wrapper: phải chỉ rõ `Class` tương ứng

Giả sử có hai khai báo:

```java
public String choose(int value) { return "int"; }
public String choose(Integer value) { return "Integer"; }
```

Đó là **hai chữ ký khác nhau**, vì `int` là primitive, còn `java.lang.Integer` là reference type. Các mệnh đề sau khác nhau:

- `int.class == Integer.TYPE` là `true`: trường `TYPE` trên wrapper mô tả primitive `int`.
- `int.class == Integer.class` là `false`: `int.class` và `Integer.class` là hai đối tượng `Class` cho **hai kiểu khác nhau**.
- `type.getMethod("choose", int.class)` tìm method tham số primitive `int`.
- `type.getMethod("choose", Integer.class)` tìm method tham số wrapper `Integer`.
- `type.getMethod("choose", Long.class)` ném `NoSuchMethodException` nếu không có overload chính xác `Long`, ngay cả khi class có method `choose(Number)` hoặc `choose(Object)`.

`Class.forName("int")` **không** tương đương `int.class`: tên `"int"` không phải tên binary của một class bình thường mà `Class.forName` tìm được. Với primitive, dùng literal `int.class` hoặc `Integer.TYPE`.

Không gọi `getClass()` trên một biến primitive: `int` không phải object. Nếu nó được autobox thành `Integer` trước khi gọi `getClass()`, kết quả phản ánh wrapper `Integer.class`, không phải `int.class`.

## 4. Overload: lookup chính xác khác áp dụng quy tắc ép kiểu

Cho các overload cùng tên:

```java
public String choose(int value) { return "int"; }
public String choose(Integer value) { return "Integer"; }
public String choose(Number value) { return "Number"; }
public String choose(Object value) { return "Object"; }
```

Các biểu thức `type.getMethod("choose", int.class)`, `getMethod("choose", Integer.class)`, `getMethod("choose", Number.class)` và `getMethod("choose", Object.class)` đều hợp lệ, trả **bốn đối tượng `Method` mô tả bốn khai báo khác nhau**.

Ngược lại, `getMethod("choose", Short.class)` và `getMethod("choose", short.class)` đều ném `NoSuchMethodException` nếu không có đúng hai chữ ký này. Reflection không suy luận chọn `Number` hay `int` theo sự phù hợp của `Short`.

Một khi đã lưu `Method chooseInt = type.getMethod("choose", int.class)`, `chooseInt.invoke(target, Integer.valueOf(5))` có thể trả `"int"` vì đối số boxed được unbox; điều này **không có nghĩa** `getMethod("choose", Integer.class)` trả `choose(int)`. Tương tự, `Short.valueOf((short) 5)` có thể unbox thành `short` rồi widening sang `int` khi `invoke`. Nhưng `chooseInt.invoke(target, Long.valueOf(5))` không hạ `long` về `int`: lời gọi không hợp lệ.

Thêm các tình huống nên kiểm thử:

- `null` được phép cho tham số tham chiếu như `Integer` (nếu method chấp nhận), nhưng không cho tham số primitive `int`.
- Khi `Method.invoke` gọi phương thức instance, cần receiver hợp lệ; với phương thức static, receiver không cần là instance.
- Với varargs, đối số truyền vào `invoke` ở mức reflection phải khớp tham số mảng đã biên dịch; đừng cho rằng nó luôn đóng gói các varargs như cú pháp lời gọi Java thông thường.
- Nếu method đích ném exception, `invoke` bọc nó trong `InvocationTargetException`; khác với `NoSuchMethodException` xảy ra khi tìm chữ ký.

## 5. Generic erasure: tra cứu theo kiểu tham số đã xóa

Xét khai báo:

```java
public int accept(java.util.List<String> values) {
    return values.size();
}
```

`List<String>` chỉ hỗ trợ kiểm tra generic tại compile time. Ở runtime, kiểu tham số method đã erasure thành `java.util.List`; vì vậy:

- `Sample.class.getMethod("accept", List.class)` — **tìm thấy**.
- `Sample.class.getMethod("accept", ArrayList.class)` — **không tìm thấy**: tham số method khai báo là `List`, không phải `ArrayList`, mặc dù có thể gọi method bằng instance `ArrayList`.
- `method.getParameterTypes()[0] == List.class` — `true`: API này trả `Class<?>` kiểu đã xóa.
- `method.getGenericParameterTypes()[0]` có thể là `ParameterizedType` mô tả `List<String>` nếu class file có `Signature` metadata đúng.
- Khi `method.invoke(target, new ArrayList<String>())`, Reflection kiểm tra tham số raw/runtime `List`; nó không quan sát riêng đối số generic `String` của từng instance.

**Không được nhầm:** “type erasure” **không** đồng nghĩa mọi metadata generic bị loại khỏi `.class`. Generic `Signature` của **khai báo** có thể được compiler lưu và Reflection đọc qua `getGenericParameterTypes()`, `getGenericReturnType()` hoặc `getGenericSuperclass()`. Metadata có thể thiếu hoặc hỏng sau khi xử lý bytecode, do đó công cụ không nên mặc định mọi file `.class` luôn chứa signature đầy đủ.

Hai overload chỉ khác đối số generic như `accept(List<String>)` và `accept(List<Integer>)` **không thể cùng khai báo trong một class** vì chữ ký sau erasure trùng `accept(List)`: compiler báo name clash.

## 6. Bridge method: lưu ý khi đếm và lựa chọn method

Một trường hợp tinh tế của generic erasure xuất hiện khi override:

```java
class Parent<T> {
    public T echo(T value) { return value; }
}

class Child extends Parent<String> {
    @Override
    public String echo(String value) { return value.toUpperCase(); }
}
```

Ở `Parent`, method `echo(T)` có tham số erased `Object`. Ở `Child`, method do người viết khai báo nhận `String`. Để bảo đảm lời gọi đa hình trên một tham chiếu `Parent` sau erasure vẫn đi đến `Child.echo(String)`, compiler Java thông thường sinh **bridge method** nhận `Object` rồi chuyển sang method nhận `String`.

Vì vậy khi duyệt `Child.class.getDeclaredMethods()`, có thể gặp cả:

- Method `echo(String)` do tác giả khai báo: `isBridge() == false`.
- Method `echo(Object)` do compiler sinh: `isBridge() == true` và thường `isSynthetic() == true`.

Bridge method là phương thức thực trong bytecode, có thể được Reflection liệt kê hoặc tìm theo tham số erased. Công cụ annotation/dispatcher cần quyết định có bỏ qua bridge/synthetic để tránh xử lý trùng hay cần mapping bridge về method gốc; không nên mặc định số method phản chiếu bằng số method lập trình viên nhìn thấy trong file `.java`.

Bridge còn xuất hiện trong các trường hợp override có return type hiệp biến hoặc sau khi xóa generic theo quy tắc tương thích nhị phân. Việc xác định cụ thể method nào được sinh phụ thuộc cấu trúc kế thừa và compiler; hãy kiểm tra bằng `javap -p -v` nếu thiết kế công cụ chuyên sâu.

## 7. Chương trình kiểm thử Java 17+ độc lập, có assertion

Tạo tệp `ReflectionLookupTests.java`. Các kiểm thử dùng `check(label, condition)`; nếu sai, chương trình ném `AssertionError` và kết thúc bất thường, không phụ thuộc cờ JVM `-ea`.

```java
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReflectionLookupTests {
    public static class Sample {
        public String choose(int value) { return "int"; }
        public String choose(Integer value) { return "Integer"; }
        public String choose(Number value) { return "Number"; }
        public String choose(Object value) { return "Object"; }

        public int accept(List<String> values) {
            return values.size();
        }
    }

    public static class Parent<T> {
        public T echo(T value) { return value; }
    }

    public static class Child extends Parent<String> {
        @Override
        public String echo(String value) {
            return value.toUpperCase(Locale.ROOT);
        }
    }

    static void check(String label, boolean condition) {
        if (!condition) {
            throw new AssertionError(label);
        }
        System.out.println("PASS " + label);
    }

    static boolean lookupMissing(Class<?> type, String name,
            Class<?> parameter) {
        try {
            type.getMethod(name, parameter);
            return false;
        } catch (NoSuchMethodException expected) {
            return true;
        }
    }

    public static void main(String[] args) throws Exception {
        Class<Sample> type = Sample.class;
        Sample target = new Sample();

        Method primitive = type.getMethod("choose", int.class);
        Method wrapper = type.getMethod("choose", Integer.class);
        Method number = type.getMethod("choose", Number.class);
        Method object = type.getMethod("choose", Object.class);

        check("primitive != wrapper", int.class != Integer.class);
        check("Integer.TYPE == int.class", Integer.TYPE == int.class);
        check("four exact signatures",
                primitive.getParameterTypes()[0] == int.class
                && wrapper.getParameterTypes()[0] == Integer.class
                && number.getParameterTypes()[0] == Number.class
                && object.getParameterTypes()[0] == Object.class);

        check("missing Long overload",
                lookupMissing(type, "choose", Long.class));
        check("missing short overload",
                lookupMissing(type, "choose", short.class));

        check("invoke unboxes Integer",
                "int".equals(primitive.invoke(target, Integer.valueOf(7))));
        check("invoke widens short to int",
                "int".equals(primitive.invoke(target,
                        Short.valueOf((short) 7))));
        check("invoke wrapper exact",
                "Integer".equals(wrapper.invoke(target, Integer.valueOf(7))));
        check("invoke Number accepts Integer",
                "Number".equals(number.invoke(target, Integer.valueOf(7))));

        boolean narrowingRejected = false;
        try {
            primitive.invoke(target, Long.valueOf(7L));
        } catch (IllegalArgumentException expected) {
            narrowingRejected = true;
        }
        check("long to int rejected", narrowingRejected);

        Method accept = type.getMethod("accept", List.class);
        check("List erased parameter",
                accept.getParameterTypes()[0] == List.class);
        check("ArrayList not declared",
                lookupMissing(type, "accept", ArrayList.class));
        check("ArrayList argument accepted",
                Integer.valueOf(2).equals(accept.invoke(target,
                        new ArrayList<>(List.of("a", "b")))));

        Type generic = accept.getGenericParameterTypes()[0];
        check("generic signature retained",
                generic instanceof ParameterizedType
                && ((ParameterizedType) generic)
                        .getActualTypeArguments()[0] == String.class);

        Method declared = Child.class.getDeclaredMethod("echo", String.class);
        Method bridge = Child.class.getDeclaredMethod("echo", Object.class);
        check("source method not bridge", !declared.isBridge());
        check("generated bridge method", bridge.isBridge()
                && bridge.isSynthetic());
        check("bridge dispatch",
                "HELLO".equals(bridge.invoke(new Child(), "hello")));

        boolean primitiveNameMissing = false;
        try {
            Class.forName("int");
        } catch (ClassNotFoundException expected) {
            primitiveNameMissing = true;
        }
        check("Class.forName int not primitive", primitiveNameMissing);
    }
}
```

Lệnh biên dịch và chạy:

```bash
javac -encoding UTF-8 ReflectionLookupTests.java
java ReflectionLookupTests
```

Output chuẩn:

```text
PASS primitive != wrapper
PASS Integer.TYPE == int.class
PASS four exact signatures
PASS missing Long overload
PASS missing short overload
PASS invoke unboxes Integer
PASS invoke widens short to int
PASS invoke wrapper exact
PASS invoke Number accepts Integer
PASS long to int rejected
PASS List erased parameter
PASS ArrayList not declared
PASS ArrayList argument accepted
PASS generic signature retained
PASS source method not bridge
PASS generated bridge method
PASS bridge dispatch
PASS Class.forName int not primitive
```

Chương trình bảo đảm những điểm sau:

- Tên các overload giống nhau nhưng `Method.getParameterTypes()` cho thấy **chữ ký riêng**; không có autoboxing ở bước `getMethod`.
- Nhánh `lookupMissing(...)` xác minh trực tiếp `NoSuchMethodException` thay vì coi “không tìm thấy” là lỗi không mong đợi.
- Các phép `invoke` minh họa rõ **unboxing + primitive widening** so với primitive narrowing bị từ chối.
- Khi truyền `ArrayList<String>` vào method khai báo `List<String>`, invocation thành công do `ArrayList` phù hợp với raw `List` runtime, nhưng lookup bằng `ArrayList.class` vẫn thất bại.
- `getGenericParameterTypes()` đọc khai báo `List<String>` đã được `javac` giữ trong `Signature`. Bước này kiểm tra metadata thực tế của class file biên dịch bởi bài thử; không phải một đảm bảo cho mọi bytecode bên ngoài.
- `Child` có bridge method theo kết quả biên dịch Java 17; invocation qua bridge gọi bản override và trả `HELLO`.
- `Class.forName("int")` xác nhận literal primitive phải lấy bằng `int.class` hoặc `Integer.TYPE`.

**Nếu kiểm thử báo FAIL hoặc ném exception**, xem vị trí đầu tiên để phân biệt lookup failure, invocation conversion, metadata generic và compiler bridge. Không sửa assertion chỉ để biến kiểm thử thành PASS.

## 8. Thiết kế test matrix cho công cụ sử dụng Reflection

Khi xây mapper, serializer, DI container, RPC dispatcher hoặc test framework, nên kiểm tra một **ma trận** thay vì chỉ vài “happy path”:

| Nhóm | Ca thử | Kết quả dự kiến |
| --- | --- | --- |
| Primitive lookup | `getMethod("choose", int.class)` | Tìm đúng `choose(int)` |
| Wrapper lookup | `getMethod("choose", Integer.class)` | Tìm đúng `choose(Integer)` |
| Missing overload | `getMethod("choose", Long.class)` | `NoSuchMethodException`, dù có `choose(Number)` |
| Widening invocation | Gọi `choose(int)` với `Short` | Có thể unbox rồi widen `short → int` |
| Narrowing invocation | Gọi `choose(int)` với `Long` | `IllegalArgumentException` |
| Reference invocation | Gọi `choose(Number)` với `Integer` | Hợp lệ vì tham chiếu kiểu con có thể gán cho `Number` |
| Generic erased lookup | Tìm `accept(List<String>)` qua `List.class` | Tìm được `accept(List)` |
| Generic concrete lookup | Tìm `accept(ArrayList.class)` | Không có chữ ký chính xác, thất bại |
| Generic signature | `getGenericParameterTypes()` | Có thể cho `List<String>` khi `Signature` được giữ |
| Bridge method | `getDeclaredMethods()` trên subclass override generic | Có thể thấy method bridge/synthetic |
| Inherited public | `getMethod` so với `getDeclaredMethod` | `getMethod` có thể tìm inherited public; `getDeclaredMethod` chỉ tìm method khai báo trực tiếp |
| Access control | Tìm private method bằng `getDeclaredMethod` | Có thể thấy metadata, nhưng invoke còn chịu access/module rules |
| Negative path | Tên sai, tham số null, sai receiver, checked target exception | Kiểm tra đúng ngoại lệ ở **giai đoạn** phù hợp |

Với test dựa vào Reflection, nên tách rõ ba loại assertion:

1. **Lookup contract:** phương thức được định vị theo tên và danh sách kiểu tham số chính xác; thiếu chữ ký gây `NoSuchMethodException`.
2. **Invocation contract:** receiver và actual arguments tương thích với declared parameter types; method đích hoạt động/đẩy exception như mong đợi.
3. **Metadata contract:** generic `Type`, annotation, bridge/synthetic flags còn tồn tại trong class file đang dùng; những thông tin ấy có thể bị thay đổi bởi compiler hoặc công cụ xử lý bytecode.

Nếu kiểm tra bytecode do framework tạo động hoặc proxy class, đừng áp kỳ vọng giống hệt `javac` với các method sinh ra tự động: hãy quan sát cấu trúc bytecode thật và chọn quy tắc phù hợp.

## 9. Những bẫy phổ biến khi kiểm thử

### Dựa vào `getDeclaredMethods()` để chọn overload “đầu tiên”

Thứ tự các method trả về từ `getDeclaredMethods()`/`getMethods()` **không được bảo đảm ổn định** theo mã nguồn. Không dùng `methods[0]` để suy ra overload ưu tiên, cũng không dùng thứ tự reflection thay thế overload resolution của compiler. Hãy lọc theo tên, exact parameter types, visibility và chính sách chọn rõ ràng.

### Dùng `getMethod` rồi tưởng nó tự gọi method

`getMethod` chỉ trả `Method`; không thực thi body. Khi gọi `invoke`, có thể gặp `IllegalAccessException` hoặc `InvocationTargetException`; với method static, receiver có thể là `null`, nhưng với method instance cần object thích hợp. Hãy test riêng “lookup đúng” và “gọi đúng”.

### So sánh kiểu wrapper với primitive bằng tên đơn giản

`int.class.getSimpleName()` là `"int"`, `Integer.class.getSimpleName()` là `"Integer"`; dùng chuỗi để suy luận convertibility là cách dễ vỡ. Nên dùng `Class<?>` và bảng chuyển đổi primitive rõ ràng nếu framework thật sự cần chọn overload động.

### Nhầm method signature với kiểu thực của đối số

`List.class` là declared parameter class sau erasure của `accept(List<String>)`. Việc truyền `new ArrayList<>()` vào `invoke` là câu chuyện compatibility runtime; không làm chữ ký method đổi thành `ArrayList`.

### Suy luận generic từ object

`new ArrayList<String>().getClass()` không mang đối số `String` của riêng instance. `Field.getGenericType()` hoặc `Method.getGenericParameterTypes()` mô tả **khai báo** được lưu trong class file; `TypeToken` hoặc explicit schema/token phù hợp hơn khi API phải nhận type argument tại runtime.

### Bỏ qua bridge/synthetic

Nếu framework lặp qua `getDeclaredMethods()` để tự động đăng ký handler, bridge method có thể khiến đăng ký trùng. Dùng `isBridge()`/`isSynthetic()` khi cần, nhưng cũng phải nhớ bridge có chức năng dispatch thật nên không nên xóa bừa khỏi những công cụ phân tích runtime.

### Kết luận “Java reflection hỏng” từ `NoSuchMethodException`

Trước khi kết luận lỗi API, hãy in có kiểm soát danh sách `method.getParameterTypes()`, `method.isBridge()`, `getDeclaringClass()` và kiểm tra `getMethod` (public, có kế thừa) so với `getDeclaredMethod` (khai báo trực tiếp, cả private). Đặc biệt phân biệt `int.class` với `Integer.class` và `List.class` với `ArrayList.class`.

## 10. Bài tập tự kiểm tra

**Câu 1.** Class chỉ có `run(int)`. `getMethod("run", Integer.class)` có thành công vì autoboxing không?

**Đáp án:** Không. Lookup đòi chữ ký chính xác `int.class`; autoboxing/unboxing thuộc giai đoạn chuyển đổi đối số khi gọi, không áp dụng cho tìm method.

**Câu 2.** Đã có `Method` cho `run(int)`. Truyền `Short.valueOf((short) 3)` qua `invoke` có được không?

**Đáp án:** Có thể: unboxing `Short → short` rồi primitive widening `short → int`. Truyền `Long` vào tham số `int` không hợp lệ vì cần narrowing.

**Câu 3.** Vì sao `getMethod("accept", List.class)` đúng cho `accept(List<String>)`, trong khi `getMethod("accept", ArrayList.class)` sai?

**Đáp án:** Chữ ký sau erasure là `accept(List)`. `ArrayList` là kiểu implementation có thể dùng làm actual argument, không phải kiểu tham số đã khai báo.

**Câu 4.** Trong cùng class có thể overload `process(List<String>)` và `process(List<Integer>)` không?

**Đáp án:** Không. Cả hai erasure thành `process(List)`, gây name clash.

**Câu 5.** `getGenericParameterTypes()` nói `List<String>` có chứng minh một instance `ArrayList` hiện có chỉ chứa `String` không?

**Đáp án:** Không. Nó mô tả metadata của khai báo method; generic erasure không bảo đảm kiểm tra phần tử collection theo `String` tại runtime.

**Câu 6.** Vì sao subclass override `echo(String)` có thể xuất hiện thêm `echo(Object)` khi xem method bằng reflection?

**Đáp án:** Compiler sinh bridge để duy trì dispatch đa hình của method generic sau erasure. Dùng `isBridge()` và `isSynthetic()` để nhận diện.

**Câu 7.** Nếu framework cần chọn overload theo actual argument ở runtime, chỉ gọi `getMethod(name, arg.getClass())` đã đủ chưa?

**Đáp án:** Chưa. Primitive, `null`, subtype/interface, boxing/unboxing, widening, varargs và độ ưu tiên giữa các overload cần một thuật toán chọn method được thiết kế rõ; Java Reflection API `getMethod` không làm toàn bộ overload resolution thay cho framework.

**Kết luận:** Hãy test thành ba lớp **tra cứu chữ ký chính xác → gọi với chuyển đổi đối số phù hợp → kiểm tra generic/bridge metadata**. Primitive và wrapper là hai kiểu khác nhau, overload không được Reflection tự lựa chọn, còn generic erasure quyết định chữ ký runtime dù class file vẫn có thể chứa metadata generic phục vụ introspection.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-testing-004">Kiểm thử invoke với null, varargs, mảng và sai kiểu</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử invoke với null, varargs, mảng và sai kiểu

## 1. Mục tiêu

**Method.invoke(Object obj, Object... args)** là API của Core Reflection để gọi method tại runtime. Khi kiểm thử công cụ Reflection, bốn nhóm lỗi dễ gặp nhất là truyền **null** không rõ ý nghĩa, xử lý **varargs** và **array** sai, đưa **kiểu/số lượng đối số** không tương thích, và nhầm ngoại lệ do Reflection phát sinh với lỗi do method đích ném.

Người học cần phân biệt hai tầng varargs:

- Tầng Java source: khi gọi **Method.invoke**, compiler quyết định cách đưa đối số vào tham số **Object... args** của chính API này.
- Tầng method đích: Reflection nhận một danh sách đối số; method đích phải nhận đúng số lượng và kiểu theo signature runtime. Reflection không tự gom các chuỗi rời thành một String[] cho method đích khai báo String....

Bài này thực hành trên JDK 17+, không cần Spring, JUnit hoặc thư viện ngoài. Kết quả của chương trình mẫu được đối chiếu với Java runtime.

## 2. Quy tắc Method.invoke

| Thành phần | Quy tắc | Lỗi điển hình |
| --- | --- | --- |
| Receiver cho instance method | Object phải khác null và tương thích với declaring class | NullPointerException nếu null; IllegalArgumentException nếu sai loại |
| Receiver cho static method | Có thể truyền null; object receiver không được dùng | Không yêu cầu tạo instance |
| Danh sách đối số | Đúng số phần tử so với method.getParameterCount() | IllegalArgumentException khi thừa/thiếu |
| Kiểu tham số tham chiếu | Đối số tương thích với kiểu khai báo; null có thể hợp lệ | IllegalArgumentException nếu không thể gán |
| Tham số primitive | Unboxing và widening hợp lệ; không nhận null hoặc narrowing | IllegalArgumentException |
| Method varargs | Một formal parameter cuối có kiểu mảng | Reflection không tự đóng gói các đối số rời |
| Giá trị trả về | Primitive được boxing; void trả null qua invoke | Không suy diễn void thành một đối tượng khác |
| Method body ném Throwable | Reflection bọc Throwable do method đích ném | InvocationTargetException với getCause() |
| Quyền truy cập | Access checks vẫn áp dụng | IllegalAccessException |

**Tra cứu method và gọi method là hai việc khác nhau.** Ví dụ một method khai báo **triple(int)** được tìm bằng **getMethod("triple", int.class)**; dùng **Integer.class** sẽ ném **NoSuchMethodException** ở bước tra cứu. Nhưng khi đã có đối tượng Method cho triple(int), truyền một **Integer** đúng kiểu wrapper có thể được unbox để gọi method đích.

## 3. Null: receiver, một đối số null, mảng đối số null

Xét **String accept(String value)** và đối tượng **target** phù hợp.

| Cách gọi | Số đối số của method đích | Kết quả |
| --- | --- | --- |
| **accept.invoke(target, (Object) null)** | 1 đối số giá trị null | Hợp lệ cho String |
| **accept.invoke(target, new Object[]{null})** | 1 đối số giá trị null | Hợp lệ cho String |
| **accept.invoke(target, (Object[]) null)** | 0 đối số | IllegalArgumentException vì thiếu |
| **accept.invoke(target)** | 0 đối số | IllegalArgumentException |
| **accept.invoke(null, "hi")** | Receiver null, không phải argument null | NullPointerException |
| **accept.invoke("wrong receiver", "hi")** | Receiver không phải Target | IllegalArgumentException |
| **ping.invoke(null)** với static ping() | Không đối số, static | Thành công |

**(Object) null** được compiler xử lý thành **một phần tử** trong danh sách varargs Object[] của Method.invoke. **(Object[]) null** là **cả mảng** danh sách đối số null và được diễn giải như không có đối số. Viết **invoke(target, null)** mà không có cast có thể gây cảnh báo hoặc làm người đọc không hiểu bạn muốn cách nào. Trong thư viện, hãy dùng **new Object[]{null}** khi muốn một tham số null.

Đối với method không có tham số, **invoke(target)** hay **invoke(target, (Object[]) null)** đều biểu diễn lời gọi không đối số. Đối với tham số **int**, kể cả **(Object) null** cũng không hợp lệ vì int là primitive.

## 4. Varargs của method đích khác varargs của Method.invoke

Phương thức khai báo:

~~~java
public String join(String... parts) {
    return parts == null
            ? "array=<null>"
            : String.join("|", parts);
}
~~~

có signature runtime **join(String[])**, nghĩa là chỉ **một tham số hình thức**. Đối tượng Method được tra cứu như sau:

~~~java
Method join = Target.class.getMethod("join", String[].class);
~~~

Đây là các đoạn minh họa; class Target đầy đủ nằm ở phần 7.

| Lời gọi | Diễn giải | Kết quả |
| --- | --- | --- |
| **join.invoke(target, "A", "B")** | Hai String rời; method cần một String[] | IllegalArgumentException |
| **join.invoke(target, (Object) new String[]{"A", "B"})** | Một đối số String[] | Chuỗi A nối B bằng ký tự &#124; |
| **join.invoke(target, new Object[]{new String[]{"A", "B"}})** | Object[] ngoài cùng có một phần tử String[] | Thành công |
| **join.invoke(target, (Object[]) new String[]{"A", "B"})** | String[] được dùng như Object[] chứa hai đối số | IllegalArgumentException |
| **join.invoke(target, (Object) new String[0])** | Một đối số là mảng rỗng | Chuỗi rỗng |
| **join.invoke(target, (Object) null)** | Một đối số String[] có giá trị null | Trả array=<null> |
| **join.invoke(target, (Object) new int[]{1, 2})** | Một int[] không tương thích String[] | IllegalArgumentException |

**Bẫy array covariance:** String[] là subtype của Object[]. Viết trực tiếp **join.invoke(target, new String[]{"A","B"})** có thể khiến compiler dùng chính String[] làm danh sách varargs **Object[]** của API invoke, tức hai đối số; compiler thường cảnh báo cách gọi chưa rõ. Khi muốn truyền **một mảng làm một đối số**, viết **(Object) new String[]{...}** hoặc **new Object[]{new String[]{...}}**.

Không đồng nhất lời gọi **target.join("A","B")** và **join.invoke(target, "A","B")**. Trường hợp thứ nhất được compiler Java đóng gói varargs cho method đích; trường hợp thứ hai Reflection thấy hai argument và không tự gom chúng thành String[].

## 5. Kiểm thử primitive và đối số sai kiểu

Với **int triple(int n)**:

- **Integer.valueOf(4)** được unbox thành int, kết quả 12.
- **Short.valueOf((short) 4)** được unbox thành short rồi widening thành int, kết quả 12.
- **Long.valueOf(4)** không được narrowing từ long thành int, dẫn đến **IllegalArgumentException**.
- **"4"** là String, không tự parse thành int, dẫn đến **IllegalArgumentException**.
- **(Object) null** không thể cung cấp giá trị primitive int, dẫn đến **IllegalArgumentException**.
- Thừa hoặc thiếu số lượng đối số cũng dẫn đến **IllegalArgumentException**.

Reflection không thay thế converter của tầng HTTP/JSON/configuration. Nếu muốn nhận chuỗi "4", hãy parse và validate rõ ràng rồi mới truyền Integer/int phù hợp; đừng kỳ vọng Method.invoke sẽ biến mọi loại input thành kiểu tham số đích.

## 6. Hai tầng lỗi: kiểm tra đối số và code đích

**Lỗi trước khi method body chạy:**

~~~java
triple.invoke(target, "4");
~~~

Reflection từ chối String cho tham số int và ném **IllegalArgumentException trực tiếp**. Nội dung method đích chưa được thực thi.

**Lỗi sau khi method body chạy:**

~~~java
public String fail() {
    throw new IllegalStateException("boom");
}
~~~

Khi gọi **fail.invoke(target)**, Reflection bọc exception từ code đích thành **InvocationTargetException**. Dùng **getCause()** để xem **IllegalStateException("boom")**.

Ngay cả khi method đích tự ném **IllegalArgumentException**, outer exception ở caller Reflection vẫn là **InvocationTargetException**, với cause IllegalArgumentException. Vì thế unit test phải kiểm tra **outer type và cause**, không chỉ tên ngoại lệ cuối cùng.

Ngoài ra, **IllegalAccessException** có thể đến từ kiểm tra quyền, **ExceptionInInitializerError** có thể đến từ khởi tạo class static lỗi, còn **NoClassDefFoundError** có thể là kết quả class lỗi khởi tạo từ lần trước hoặc thiếu dependency. Chúng không đồng nghĩa với lỗi đóng gói đối số.

## 7. Ví dụ Java 17+ đầy đủ: 18 ca kiểm thử

Lưu mã dưới đây thành **InvokeEdgeDemo.java**:

~~~java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

public class InvokeEdgeDemo {
    public static class Target {
        public String accept(String s) {
            return s == null ? "<null>" : s.toUpperCase(Locale.ROOT);
        }

        public int triple(int n) {
            return n * 3;
        }

        public String join(String... parts) {
            return parts == null
                    ? "array=<null>"
                    : String.join("|", parts);
        }

        public static String ping() {
            return "pong";
        }

        public String fail() {
            throw new IllegalStateException("boom");
        }
    }

    @FunctionalInterface
    interface Action {
        Object run() throws Throwable;
    }

    static void check(String label, Action action) {
        try {
            System.out.println(label + " -> " + action.run());
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            System.out.println(label + " -> InvocationTargetException("
                    + cause.getClass().getSimpleName()
                    + ":" + cause.getMessage() + ")");
        } catch (Throwable e) {
            System.out.println(label + " -> "
                    + e.getClass().getSimpleName());
        }
    }

    public static void main(String[] args) throws Exception {
        Target target = new Target();

        Method accept = Target.class.getMethod(
                "accept", String.class);
        Method triple = Target.class.getMethod(
                "triple", int.class);
        Method join = Target.class.getMethod(
                "join", String[].class);
        Method ping = Target.class.getMethod("ping");
        Method fail = Target.class.getMethod("fail");

        check("null value",
                () -> accept.invoke(target, (Object) null));
        check("null args array",
                () -> accept.invoke(target, (Object[]) null));
        check("null receiver",
                () -> accept.invoke(null, "hi"));

        check("static receiver null",
                () -> ping.invoke(null));
        check("static receiver supplied",
                () -> ping.invoke(target));

        check("null for int",
                () -> triple.invoke(target, (Object) null));
        check("short to int",
                () -> triple.invoke(target, Short.valueOf((short) 4)));
        check("wrong type",
                () -> triple.invoke(target, "4"));
        check("wrong count",
                () -> accept.invoke(target));
        check("wrong receiver",
                () -> accept.invoke("not-target", "hi"));

        check("join expanded",
                () -> join.invoke(target, "A", "B"));
        check("join String[] spread",
                () -> join.invoke(
                        target, (Object[]) new String[]{"A", "B"}));
        check("join packed",
                () -> join.invoke(
                        target, (Object) new String[]{"A", "B"}));
        check("join Object[] args",
                () -> join.invoke(target,
                        new Object[]{new String[]{"A", "B"}}));
        check("join wrong array type",
                () -> join.invoke(
                        target, (Object) new int[]{1, 2}));
        check("join null array argument",
                () -> join.invoke(target, (Object) null));
        check("join empty array",
                () -> join.invoke(target, (Object) new String[0]));

        check("target exception",
                () -> fail.invoke(target));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -encoding UTF-8 InvokeEdgeDemo.java
java InvokeEdgeDemo
~~~

Kết quả stdout:

~~~text
null value -> <null>
null args array -> IllegalArgumentException
null receiver -> NullPointerException
static receiver null -> pong
static receiver supplied -> pong
null for int -> IllegalArgumentException
short to int -> 12
wrong type -> IllegalArgumentException
wrong count -> IllegalArgumentException
wrong receiver -> IllegalArgumentException
join expanded -> IllegalArgumentException
join String[] spread -> IllegalArgumentException
join packed -> A|B
join Object[] args -> A|B
join wrong array type -> IllegalArgumentException
join null array argument -> array=<null>
join empty array -> 
target exception -> InvocationTargetException(IllegalStateException:boom)
~~~

**Giải thích output:**
1. **null value** truyền một null cho String, method chạy bình thường; **null args array** đưa danh sách rỗng nên sai số lượng; **null receiver** không có instance hợp lệ.
2. Method **ping** là static nên receiver null hoặc receiver được cung cấp đều không làm thay đổi kết quả.
3. **triple(int)** chỉ chấp nhận tham số tương thích; Short được widened, String và null thì không.
4. **join(String...)** thực tế nhận một String[], nên hai chuỗi rời gây lỗi; hai cách đóng gói bằng **(Object) String[]** và **new Object[]{String[]}** thành công.
5. **join empty array ->** có phần sau mũi tên rỗng vì String.join trên mảng rỗng trả chuỗi rỗng.
6. **target exception** cho biết method body đã chạy và ném IllegalStateException, được Reflection bọc bằng InvocationTargetException.

Ví dụ đã được thực thi bằng JDK và đối chiếu kết quả của toàn bộ 18 tình huống. Chương trình chỉ dùng các tính năng có từ trước Java 17.

### Vấn đề của mảng hai tầng

Hai lời gọi dưới đây có ý nghĩa khác nhau:

~~~java
join.invoke(target, (Object[]) new String[]{"A", "B"});
join.invoke(target, (Object) new String[]{"A", "B"});
~~~

Lời gọi thứ nhất đưa **hai** tham số Reflection; lời gọi thứ hai đưa **một** tham số String[]. Cast ở đây thể hiện ý định, không đơn thuần để compiler hết cảnh báo.

## 8. Viết assertion thay vì chỉ in kết quả

Một chương trình thực hành in output giúp học cơ chế, nhưng test regression nên xác nhận chính xác kết quả hoặc exception. Có thể dùng JUnit 5 nếu dự án có sẵn dependency, hoặc dùng helper Java thuần:

~~~java
@FunctionalInterface
interface ThrowingAction {
    void run() throws Throwable;
}

static <T extends Throwable> T expectThrows(
        Class<T> expected, ThrowingAction action) {
    try {
        action.run();
    } catch (Throwable actual) {
        if (expected.isInstance(actual)) {
            return expected.cast(actual);
        }
        throw new AssertionError(
                "Expected " + expected.getName()
                + ", actual " + actual.getClass().getName(), actual);
    }
    throw new AssertionError(
            "Expected exception " + expected.getName());
}
~~~

Đây là **đoạn minh họa cần đặt trong class test**, không phải chương trình độc lập. Ví dụ kiểm tra hai tầng lỗi:

~~~java
IllegalArgumentException inputError = expectThrows(
        IllegalArgumentException.class,
        () -> triple.invoke(target, "4"));

InvocationTargetException executionError = expectThrows(
        InvocationTargetException.class,
        () -> fail.invoke(target));

if (!(executionError.getCause() instanceof IllegalStateException)) {
    throw new AssertionError("Unexpected cause");
}
~~~

**Tốt hơn bắt exception chung rồi cho test PASS:** bạn phải assert exception thuộc đúng tầng, giữ cause, và với những ca thành công phải kiểm tra giá trị trả về, không chỉ kiểm tra “không crash”.

| Công đoạn | Kiểm tra quan trọng |
| --- | --- |
| Arrange | Tra đúng Method bằng tên và Class[] tham số; tạo receiver |
| Act | Chuẩn bị chính xác Object[] chứa danh sách đối số |
| Assert | Giá trị trả về, exception trực tiếp, hoặc InvocationTargetException.getCause() |
| Isolation | Test khác nhau không chia sẻ state tùy tiện |
| Portability | Không khóa test vào message/stack trace do JDK có thể thay đổi |

Nếu method có side effect, cần kiểm tra trạng thái trước và sau khi gọi. Nếu chạy trong môi trường module, cần phân biệt lỗi quyền truy cập với lỗi tham số.

## 9. Ma trận test tối thiểu cho một Reflection invoker

| Kịch bản | Input | Kỳ vọng |
| --- | --- | --- |
| Một null cho String | (Object) null | Hợp lệ nếu body xử lý |
| Không có tham số | (Object[]) null cho method cần một tham số | IllegalArgumentException |
| Instance receiver null | receiver null | NullPointerException |
| Static receiver null | receiver null cho static method | Hợp lệ |
| Primitive null | int nhận null | IllegalArgumentException |
| Primitive widening | int nhận Short | Hợp lệ |
| Primitive narrowing | int nhận Long | IllegalArgumentException |
| Sai kiểu | int nhận String | IllegalArgumentException |
| Sai số lượng | String nhận hai/không đối số | IllegalArgumentException |
| Sai receiver | instance method được gọi trên object khác loại | IllegalArgumentException |
| Varargs rời | String[] nhận hai String | IllegalArgumentException |
| Varargs đã đóng gói | String[] nhận đúng một String[] | Hợp lệ |
| Sai mảng | String[] nhận int[] | IllegalArgumentException |
| Mảng rỗng | String[] nhận String[0] như một Object | Hợp lệ |
| Mảng null | String[] nhận một null | Body nhận null |
| Method body lỗi | target ném IllegalStateException | InvocationTargetException chứa cause |
| Method trả void | không có kết quả | invoke trả null |
| Lookup sai signature | tìm int method bằng Integer.class | NoSuchMethodException trước invoke |

Đừng dùng **getMethod** với chữ ký không chắc chắn rồi gọi thử một loạt kiểu ngẫu nhiên. Xác định được overload nào cần dùng trước, sau đó mới kiểm tra kiểu và số lượng đối số.

## 10. Bài tập

1. **Varargs primitive:** thêm **sum(int... values)**. So sánh **(Object) new int[]{1,2}** với truyền hai giá trị Integer rời vào invoke.
2. **Mảng hai chiều:** thêm **rows(String[][] data)**; thử truyền **(Object) new String[][]{...}** và cách vô tình coi mảng hai chiều như Object[] danh sách tham số.
3. **Overload:** thêm **handle(String)** và **handle(Integer)**, xác nhận getMethod cần đúng Class type. Reflection không tự chọn overload mới khi invoke.
4. **Cause:** để method body ném **IllegalArgumentException("from target")**. So sánh wrapper InvocationTargetException với lỗi IllegalArgumentException do input sai.
5. **Void return:** thêm method void cập nhật trạng thái rồi kiểm tra giá trị từ invoke là null và side effect đúng.
6. **Empty/null array:** phân biệt **new String[0]** và **(Object) null** cho formal String[]; giải thích vì sao target có thể phản ứng khác nhau.
7. **Test helper:** viết hàm chuẩn bị Object[] từ dữ liệu người dùng theo Class[] của Method, kiểm tra null primitive, số lượng và kiểu mảng trước khi gọi Reflection.

## 11. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao **(Object) null** khác **(Object[]) null**?  
**Đáp án:** Một bên là một đối số null; bên kia là danh sách đối số null, được xem như không có đối số.

**Câu 2.** Method **join(String...)** có bao nhiêu tham số ở runtime?  
**Đáp án:** Một tham số kiểu String[].

**Câu 3.** Vì sao **join.invoke(target, "A","B")** sai?  
**Đáp án:** Reflection thấy hai đối số trong khi method đích cần một String[].

**Câu 4.** Method nhận int có chấp nhận Short không?  
**Đáp án:** Có, nhờ unboxing và primitive widening sang int.

**Câu 5.** Method nhận int có chấp nhận null không?  
**Đáp án:** Không, IllegalArgumentException.

**Câu 6.** Target tự ném IllegalStateException thì caller Method.invoke nhận gì?  
**Đáp án:** InvocationTargetException, với cause IllegalStateException.

**Câu 7.** Null receiver có hợp lệ với instance method không?  
**Đáp án:** Không; thường ném NullPointerException. Với static method, receiver null là phù hợp.

**Tiêu chí hoàn thành:** tự dự đoán được toàn bộ stdout ví dụ, giải thích hai tầng varargs, phân biệt null receiver/argument và viết test xác nhận đúng exception/cause.

## 12. Tài liệu chuẩn

- [Javadoc Java SE 21 — Method.invoke](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Method.html#invoke(java.lang.Object,java.lang.Object...))
- [Javadoc Java SE 21 — InvocationTargetException](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/InvocationTargetException.html)
- [Java Language Specification §15.12 — Method Invocation Expressions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.12)
- [Javadoc Java SE 21 — Executable.isVarArgs](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Executable.html#isVarArgs())

**Nguyên tắc cuối cùng:** chuẩn bị đúng **mảng Object[] danh sách đối số**, kể cả khi **một phần tử trong danh sách cũng chính là một mảng**.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-testing-005">Kiểm thử lỗi mã đích được giữ qua InvocationTargetException</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử lỗi của mã đích được giữ qua `InvocationTargetException`

## 1. Mục tiêu của bài kiểm thử

Khi gọi một phương thức bằng Java Reflection, `Method.invoke(receiver, args...)` có thể thất bại ở **hai tầng khác nhau**:

1. **Tầng reflection**: không tìm được phương thức, không đủ quyền, sai receiver hoặc đối số. Đây là lỗi trong quá trình chuẩn bị/điều phối lời gọi.
2. **Tầng mã đích**: phương thức đã thực sự được gọi và chính phần thân của nó ném exception hay error.

Ở tầng thứ hai, `Method.invoke` gói `Throwable` do **phương thức đích** ném vào một `java.lang.reflect.InvocationTargetException`. Đối tượng gốc có thể lấy lại bằng `getCause()` hoặc `getTargetException()`. Một bài kiểm thử tốt phải chứng minh cả **đúng loại wrapper**, **đúng đối tượng nguyên nhân gốc** và **đúng nội dung/cause chain**; nếu chỉ so chuỗi message của wrapper thì rất dễ bỏ sót lỗi.

Ví dụ kỳ vọng cốt lõi:

```java
Method method = Target.class.getDeclaredMethod("fail");
try {
    method.invoke(null);
    throw new AssertionError("Expected InvocationTargetException");
} catch (InvocationTargetException e) {
    Throwable original = e.getCause();
    // Kiểm tra type, message và/hoặc identity của original.
}
```

Đoạn trên là **mẫu minh họa**, cần đặt trong ngữ cảnh có import, lớp `Target` và xử lý các checked exceptions còn lại. Ví dụ có thể chạy độc lập ở mục 5.

## 2. Hợp đồng của `Method.invoke` và wrapper

Chữ ký rút gọn:

```java
public Object invoke(Object obj, Object... args)
        throws IllegalAccessException, InvocationTargetException;
```

Các hành vi cần phân biệt:

| Điều kiện | Lỗi quan sát khi gọi reflection | `InvocationTargetException`? |
| --- | --- | --- |
| Mã đích ném `IllegalStateException` | `InvocationTargetException` chứa `IllegalStateException` | Có |
| Mã đích ném checked `IOException` | `InvocationTargetException` chứa `IOException` | Có |
| Mã đích ném `AssertionError` | `InvocationTargetException` chứa `AssertionError` | Có |
| Mã đích ném `IllegalArgumentException` | `InvocationTargetException` chứa `IllegalArgumentException` | Có |
| Caller truyền sai kiểu/số lượng đối số cho `Method.invoke` | Thường là `IllegalArgumentException` trực tiếp | Không |
| Không có quyền truy cập tới phương thức | `IllegalAccessException`, hoặc lỗi khi cố mở quyền | Không |
| Tra phương thức không tồn tại qua `getDeclaredMethod` | `NoSuchMethodException` tại **bước tra cứu**, chưa có invoke | Không |
| Mã đích chạy thành công | Kết quả được trả về (hoặc `null` cho phương thức `void`) | Không |

Với `Method.invoke`, **exception do target ném** được bao trong `InvocationTargetException` kể cả khi nó là unchecked exception hoặc `Error`. Việc `Method.invoke` trả về `Object` không làm thay đổi danh tính exception gốc.

`InvocationTargetException` kế thừa `ReflectiveOperationException`. `getTargetException()` là API lịch sử; trong mã mới, `getCause()` biểu đạt quan hệ nguyên nhân nhất quán với `Throwable`. Với wrapper do `Method.invoke` tạo ra sau khi target ném lỗi, hai hàm trả về cùng đối tượng target throwable.

Các trường hợp biên: nếu JVM hoặc môi trường thất bại **trước khi** chuyển điều khiển vào mã đích (ví dụ lỗi liên kết hoặc tải lớp), không nên mặc định mọi `Throwable` nhìn thấy đều là lỗi target đã được gói. Hãy xác nhận thao tác và loại ngoại lệ thực tế.

## 3. Những điều phải assert để bảo đảm "được giữ nguyên"

Với một lời gọi mà bạn **chủ động tạo ra** exception ở target, nên kiểm tra theo mức tăng dần:

- **Wrapper đúng loại:** `assertThrows(InvocationTargetException.class, () -> method.invoke(...))`.
- **Có nguyên nhân gốc:** `assertNotNull(wrapper.getCause())`.
- **Đúng loại nguyên nhân:** ví dụ `assertInstanceOf(IllegalStateException.class, wrapper.getCause())` hoặc kiểm tra exact class khi hợp đồng yêu cầu.
- **Danh tính của Throwable:** nếu test giữ sẵn tham chiếu đến object đã ném, `assertSame(original, wrapper.getCause())`. Đây là bằng chứng mạnh hơn so sánh message: wrapper giữ **chính Throwable ban đầu**, không tự tạo đối tượng khác thay cho nó.
- **Cùng đối tượng qua API:** `assertSame(wrapper.getCause(), wrapper.getTargetException())`.
- **Message/cause chain:** khi thuộc hợp đồng được kiểm thử, xác nhận `original.getMessage()` và `original.getCause()`; không chỉ assert các chuỗi hình thành từ wrapper.
- **Nguồn phát sinh:** nếu thực sự cần, kiểm tra phương thức trên stack trace của nguyên nhân hoặc dùng dấu hiệu do target đặt ra để chứng minh thân đã chạy. Tránh phụ thuộc đường dẫn file, số dòng và thứ tự toàn bộ stack frames vốn có thể thay đổi.

**Lưu ý về `assertSame`:** trong test minh họa, target gán tham chiếu đến exception vừa tạo cho trường test fixture trước khi ném. Đây là cách kiểm tra identity. Trong production, không nên giữ Throwable trong biến static toàn cục chỉ để log; kiểm thử có thể dùng fixture có phạm vi nhỏ, và các test này chạy tuần tự để tránh ghi đè tham chiếu.

## 4. Cách kiểm thử một phương thức thật qua reflection

Các bước kiểm thử hợp lý:

1. Tạo target có hành vi ném lỗi **xác định**. Tách tình huống checked, unchecked và `Error` nếu hợp đồng bao quát cả ba.
2. Lấy đúng `Method` bằng `getDeclaredMethod(name, parameterTypes...)` hoặc `getMethod(...)`. Nếu phương thức `static`, truyền receiver `null`; với instance method, tạo receiver đúng lớp.
3. Gọi qua `Method.invoke` bên trong một hàm kiểm thử cho phép ném `Throwable`.
4. Bắt/khẳng định `InvocationTargetException` rồi đọc `getCause()`. So sánh **reference** nếu fixture lưu original; kiểm tra type và message.
5. Kiểm tra các tình huống reflection lỗi **trước lời gọi** bằng một test khác để không vô tình gộp hai tầng lỗi.
6. Nếu ứng dụng có wrapper helper để unwrap và ném lại nguyên nhân, kiểm thử riêng helper đó: bên gọi phải thấy nguyên nhân gốc chứ không còn `InvocationTargetException`.

Không nên tạo lại `new IllegalStateException(message)` trong mã test rồi gọi `assertSame` với exception của target: đó là **hai object khác nhau** dù trùng loại và message. Cũng không nên so `wrapper.getMessage()` với message nghiệp vụ; wrapper và nguyên nhân là hai tầng khác nhau.

## 5. Ví dụ Java 17+ độc lập: test runtime, checked, Error, cause chain và unwrap

Lưu file `InvocationTargetExceptionDemo.java`:

```java
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class InvocationTargetExceptionDemo {
    static final class Target {
        static Throwable lastThrown;
        static boolean called;

        public static void failRuntime() {
            IllegalStateException original =
                    new IllegalStateException("runtime failure");
            lastThrown = original;
            throw original;
        }

        public static void failChecked() throws IOException {
            IOException original = new IOException("checked failure");
            lastThrown = original;
            throw original;
        }

        public static void failError() {
            AssertionError original = new AssertionError("error failure");
            lastThrown = original;
            throw original;
        }

        public static void failIllegalArgument() {
            IllegalArgumentException original =
                    new IllegalArgumentException("inside target");
            lastThrown = original;
            throw original;
        }

        public static void failWithCause() {
            IOException root = new IOException("root cause");
            IllegalStateException original =
                    new IllegalStateException("outer cause", root);
            lastThrown = original;
            throw original;
        }

        public static void acceptInt(int number) {
            called = true;
        }
    }

    @FunctionalInterface
    interface ThrowingAction {
        void run() throws Throwable;
    }

    static <T extends Throwable> T expectThrows(
            Class<T> expectedType, ThrowingAction action) {
        try {
            action.run();
        } catch (Throwable actual) {
            if (!expectedType.isInstance(actual)) {
                throw new AssertionError(
                        "Expected " + expectedType.getName()
                                + ", got " + actual.getClass().getName(),
                        actual);
            }
            return expectedType.cast(actual);
        }
        throw new AssertionError(
                "Expected exception: " + expectedType.getName());
    }

    static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void verifyTargetFailure(
            String methodName, Class<? extends Throwable> causeType)
            throws Exception {
        Method method = Target.class.getDeclaredMethod(methodName);
        Target.lastThrown = null;

        InvocationTargetException wrapper = expectThrows(
                InvocationTargetException.class,
                () -> method.invoke(null));

        Throwable original = wrapper.getCause();
        require(original != null, "Missing target cause");
        require(causeType.isInstance(original), "Wrong target type");
        require(original == Target.lastThrown, "Original identity lost");
        require(wrapper.getTargetException() == original,
                "Different target exception");
    }

    static Object invokeUnwrapped(Method method, Object receiver,
            Object... arguments) throws Throwable {
        try {
            return method.invoke(receiver, arguments);
        } catch (InvocationTargetException wrapper) {
            throw wrapper.getCause();
        }
    }

    public static void main(String[] args) throws Exception {
        verifyTargetFailure(
                "failRuntime", IllegalStateException.class);
        System.out.println("runtime=wrapped,preserved");

        verifyTargetFailure("failChecked", IOException.class);
        System.out.println("checked=wrapped,preserved");

        verifyTargetFailure("failError", AssertionError.class);
        System.out.println("error=wrapped,preserved");

        verifyTargetFailure(
                "failIllegalArgument", IllegalArgumentException.class);
        System.out.println("targetIllegalArg=wrapped");

        Method chained = Target.class.getDeclaredMethod("failWithCause");
        InvocationTargetException wrapper = expectThrows(
                InvocationTargetException.class,
                () -> chained.invoke(null));
        Throwable original = wrapper.getCause();
        require(original == Target.lastThrown, "Cause identity changed");
        require(original.getCause() instanceof IOException,
                "Nested cause type changed");
        require("root cause".equals(original.getCause().getMessage()),
                "Nested cause message changed");
        System.out.println("nestedCause=preserved");

        Method accepting = Target.class.getDeclaredMethod(
                "acceptInt", int.class);
        Target.called = false;
        expectThrows(IllegalArgumentException.class,
                () -> accepting.invoke(null, "not an int"));
        require(!Target.called, "Target should not have run");
        System.out.println("badArguments=directIllegalArgumentException");

        Method failing = Target.class.getDeclaredMethod("failRuntime");
        IllegalStateException escaped = expectThrows(
                IllegalStateException.class,
                () -> invokeUnwrapped(failing, null));
        require(escaped == Target.lastThrown, "Unwrap lost identity");
        System.out.println("unwrap=originalCause");
    }
}
```

Chạy trong thư mục chứa file:

```bash
javac --release 17 -encoding UTF-8 InvocationTargetExceptionDemo.java
java InvocationTargetExceptionDemo
```

Stdout mong đợi:

```text
runtime=wrapped,preserved
checked=wrapped,preserved
error=wrapped,preserved
targetIllegalArg=wrapped
nestedCause=preserved
badArguments=directIllegalArgumentException
unwrap=originalCause
```

Mỗi dòng chỉ được in **sau khi mọi assertion tương ứng thành công**. Nếu một giả định sai, chương trình ném `AssertionError` và sẽ không in dòng báo kết quả của bước đó. Hàm `expectThrows` cố tình dùng một harness tối giản không phụ thuộc thư viện ngoài; trong dự án có JUnit 5, dùng `org.junit.jupiter.api.Assertions.assertThrows` sẽ chuẩn hơn.

### Vì sao ví dụ này có giá trị kiểm chứng?

- `verifyTargetFailure` kiểm tra cùng lúc wrapper, type, **identity** và sự thống nhất `getCause()`/`getTargetException()` cho unchecked, checked, `Error` và lỗi `IllegalArgumentException` từ **bên trong target**.
- `failWithCause` xác nhận **chuỗi nguyên nhân gốc** bên trong target không biến mất khi được thêm wrapper reflection.
- `acceptInt` làm rõ một `IllegalArgumentException` xuất hiện do **gọi reflection sai tham số** thì là ngoại lệ trực tiếp, target không chạy và không có `InvocationTargetException`.
- `invokeUnwrapped` mô phỏng code adapter đã quyết định trả lại nguyên nhân nghiệp vụ. Kiểm thử khẳng định bên gọi nhận được **chính exception gốc**, không có wrapper thứ hai và không bị thay bằng đối tượng cùng loại nhưng khác identity.

Trong ví dụ, fixture lưu `Target.lastThrown` dạng `static`. Vì main chạy tuần tự và reset fixture trước các trường hợp, điều này an toàn cho mô hình học tập; test thực tế chạy song song nên dùng fixture độc lập hoặc cơ chế phối hợp tránh tranh chấp trạng thái.

## 6. Cách viết tương đương bằng JUnit 5

Nếu dự án đã cấu hình **JUnit Jupiter**, một test đơn có thể viết như sau. Đoạn dưới đây **thuộc một test class cùng package**, tham chiếu đến lớp `InvocationTargetExceptionDemo.Target` của ví dụ phía trên; cần đưa dependency JUnit Jupiter vào build trước khi biên dịch test.

```java
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReflectionExceptionTest {
    @Test
    void preservesOriginalException() throws Exception {
        Method method =
                InvocationTargetExceptionDemo.Target.class
                        .getDeclaredMethod("failRuntime");

        InvocationTargetException wrapper = assertThrows(
                InvocationTargetException.class,
                () -> method.invoke(null));

        Throwable original = wrapper.getCause();
        assertNotNull(original);
        assertInstanceOf(IllegalStateException.class, original);
        assertSame(InvocationTargetExceptionDemo.Target.lastThrown,
                original);
        assertSame(original, wrapper.getTargetException());
        assertEquals("runtime failure", original.getMessage());
    }

    @Test
    void wrongArgumentFailsBeforeTarget() throws Exception {
        Method method =
                InvocationTargetExceptionDemo.Target.class
                        .getDeclaredMethod("acceptInt", int.class);

        InvocationTargetExceptionDemo.Target.called = false;
        assertThrows(IllegalArgumentException.class,
                () -> method.invoke(null, "bad argument"));
        assertFalse(InvocationTargetExceptionDemo.Target.called);
    }
}
```

Bài test này không phải chương trình có `main` độc lập; thực thi bằng runner của JUnit 5, ví dụ Gradle với `test { useJUnitPlatform() }` khi môi trường dự án đã khai báo dependency phù hợp.

**Không** nên viết `assertThrows(IllegalStateException.class, () -> method.invoke(null))` cho test đầu tiên: lời gọi reflection làm xuất hiện `InvocationTargetException` ở phía caller; `IllegalStateException` nằm trong `getCause()`.

Nếu test tác vụ bất đồng bộ, áp dụng đúng ranh giới: xác định có future/async wrapper khác (`ExecutionException`, `CompletionException`) trước hay không. Đừng mặc nhiên kết luận rằng mọi Throwable từ thread khác đều đi qua `InvocationTargetException`.

## 7. Kiểm thử stack trace và cause chain không quá mong manh

`InvocationTargetException` và exception gốc có thể có **hai stack trace khác nhau**:

- Wrapper gắn với tầng reflection tại thời điểm gọi.
- Cause giữ stack trace khi Throwable gốc được tạo/ném trong target.

Để kiểm tra điều có ý nghĩa, ưu tiên assert các thông tin ổn định: exception class, message nghiệp vụ, reference identity khi có fixture, và `getCause()`. Khi phải kiểm tra stack, chỉ xác nhận nó chứa frame của phương thức đích:

```java
// Đoạn minh họa nằm trong test đã có Throwable original:
boolean containsTarget = java.util.Arrays.stream(
        original.getStackTrace())
        .anyMatch(frame -> frame.getMethodName()
                .equals("failRuntime"));
if (!containsTarget) {
    throw new AssertionError("Target stack frame missing");
}
```

Không nên so toàn bộ `StackTraceElement[]`, số dòng source hoặc tên implementation nội bộ của reflection, bởi JVM/JDK hoặc trình tối ưu có thể làm thay đổi các chi tiết này. Nếu target chỉnh stack bằng `setStackTrace`, tắt stack trace hoặc tái sử dụng exception có stack cũ, đó là đặc điểm riêng của target cần được test theo hợp đồng, không phải lý do để mọi test phải ép cùng kết quả.

`Throwable.getCause()` có thể lại chứa một exception khác do target tạo; phải phân biệt:

```text
InvocationTargetException    ← wrapper do reflection tạo
  └─ IllegalStateException   ← chính exception mã đích ném
       └─ IOException        ← nguyên nhân sâu hơn do mã đích gắn vào
```

Lúc truy nguyên nhân, **không bỏ qua** tầng wrapper mà không ghi ngữ cảnh `Method` nào được gọi, nhưng cũng không để wrapper che mất loại lỗi nghiệp vụ bên trong.

## 8. Lỗi phổ biến khi viết test và adapter

| Sai sót | Hậu quả | Cách kiểm tra/sửa |
| --- | --- | --- |
| `assertThrows(TargetException.class, () -> method.invoke(...))` | Test bắt sai tầng ngoại lệ | Assert `InvocationTargetException`, rồi kiểm tra `getCause()` |
| Chỉ so message wrapper | Không chứng minh được Throwable gốc | Assert type, message của cause, và identity nếu có fixture |
| Gọi sai overload/tham số mà tưởng target ném lỗi | `IllegalArgumentException` trực tiếp | Kiểm tra dấu hiệu thân target đã chạy |
| Tra method sai rồi trộn `NoSuchMethodException` vào test invoke | Không test được đường thực thi target | Tách test lookup và test invoke |
| Dùng `getCause().getCause()` không kiểm tra | Có thể `NullPointerException` nếu cause chain ngắn | Assert từng tầng theo contract |
| Nuốt `InvocationTargetException` hoặc chỉ log wrapper | Mất bối cảnh lỗi nghiệp vụ | Ghi cause, giữ stack và bối cảnh method; quyết định unwrap rõ ràng |
| Unwrap bằng `new RuntimeException(original.getMessage())` | Mất danh tính, kiểu và stack gốc | Ném lại `original` khi API cho phép, hoặc dùng `new RuntimeException("context", original)` để giữ cause |
| Test với một trường `static lastThrown` trong bộ test chạy song song | Race làm assert identity không ổn định | Fixture riêng, tuần tự hoặc đồng bộ phù hợp |
| Gọi private method mà chưa cấp quyền | Test thất bại ở tầng access | Dùng public target để test wrapper; kiểm tra private access trong bài test riêng |
| Assert nguyên cả stack trace | Test giòn, lệ thuộc JDK | Kiểm tra frame/đặc tính cần thiết |
| Cho rằng mọi cách gọi động đều bọc lỗi bằng wrapper này | Phân tích sai với `MethodHandle.invokeExact` hoặc lời gọi Java trực tiếp | Phân biệt API; kiểm thử riêng quy tắc exception của từng API |

### Khi xây helper unwrap, giữ nguyên Throwable

`Method.invoke` trả về ngoại lệ wrapper để phân biệt code reflection với exception bên trong target. Một adapter có thể muốn **chủ động** đưa exception đích trở lại ranh giới nghiệp vụ:

```java
static Object invokeAndRethrow(
        Method method, Object target, Object... arguments)
        throws Throwable {
    try {
        return method.invoke(target, arguments);
    } catch (InvocationTargetException e) {
        throw e.getCause();
    }
}
```

Đây là đoạn minh họa cần import `Method`/`InvocationTargetException`, không phải chương trình đầy đủ. Việc khai báo `throws Throwable` làm rõ rằng cause có thể là checked exception, unchecked exception **hoặc `Error`**. Trong code ứng dụng thực tế, caller cần chính sách chuyển tiếp, khai báo checked exceptions và log phù hợp. Không bao giờ bắt mọi `Throwable` rồi tiếp tục im lặng sau một lỗi nghiêm trọng.

Nếu interface adapter chỉ khai báo một loại checked exception hẹp, có thể cần ánh xạ ngoại lệ theo hợp đồng đó. Khi phải bọc lại, luôn đặt original làm **cause** thay vì chỉ sao chép message.

## 9. Trường hợp API khác: đừng áp dụng máy móc

- `Constructor.newInstance(...)` cũng có thể ném `InvocationTargetException` khi constructor đích ném exception, nhưng các lỗi trước khi chạy constructor cần tách riêng.
- `MethodHandle.invoke` và `invokeExact` không tuân theo quy tắc wrapper `InvocationTargetException` của `Method.invoke`; chúng có hợp đồng riêng và thường đưa Throwable trực tiếp tới caller.
- Reflection call có thể đi qua proxy/adapters khác: tầng bên ngoài (ví dụ dynamic proxy, future) có thể thêm wrapper riêng. Test phải xác định **lớp nào tạo wrapper** và kiểm tra đúng tầng.
- `InvocationTargetException` tự nó cũng là một class có constructor công khai; nếu chính target chủ động ném một object loại này qua `Method.invoke`, sẽ có **wrapper bên ngoài** và target exception ở tầng bên trong. Đừng phân loại nguyên nhân chỉ bằng tên class nếu không biết chuỗi gọi.

## 10. Checklist cho kiểm thử regression

Trước khi chốt test:

1. Đã chứng minh phương thức đích thực sự được gọi và ném lỗi dự kiến?
2. Đã assert wrapper `InvocationTargetException` ở bên ngoài?
3. `getCause()` khác `null`, đúng type, message và **cùng Throwable** khi có reference?
4. `getTargetException()` có cùng identity với `getCause()`?
5. Cause chain sâu hơn được giữ khi target thiết lập nguyên nhân?
6. Đã có test riêng về receiver/argument sai để tránh nhầm lỗi reflection với lỗi target?
7. Với helper unwrap, đã xác nhận exception gốc được đưa ra và không bị tạo lại?
8. Test chạy ổn trên Java 17+, không phụ thuộc chi tiết stack trace dễ thay đổi?
9. Nếu dùng JUnit 5, runner và dependency đã được khai báo; nếu dùng harness độc lập, stdout khớp đúng?
10. Có tránh chia sẻ trạng thái test khiến kết quả sai khi chạy song song?

## 11. Tự kiểm tra

**Câu 1.** Mã trong target gọi `throw new IllegalArgumentException("bad")`. Caller dùng `Method.invoke` bắt lỗi gì?

**Đáp án:** `InvocationTargetException` ở ngoài, còn `IllegalArgumentException` chính là `getCause()`.

**Câu 2.** Caller truyền `String` vào tham số `int` của `Method.invoke` và target chưa chạy. Có `InvocationTargetException` không?

**Đáp án:** Không; đây là lỗi argument của reflection, thường là `IllegalArgumentException` trực tiếp.

**Câu 3.** `getCause()` và `getTargetException()` của wrapper do `Method.invoke` tạo có trả hai bản sao exception không?

**Đáp án:** Không. Chúng trả cùng đối tượng exception đích; có thể kiểm bằng `==` hoặc JUnit `assertSame`.

**Câu 4.** Target ném một `IOException` có cause là `IllegalStateException`. Wrapper có mất cause chain không?

**Đáp án:** Không. Wrapper mới trỏ tới `IOException`; bản thân `IOException` vẫn giữ quan hệ cause đã thiết lập.

**Câu 5.** Nếu adapter cần ném lại lỗi gốc, nên làm gì?

**Đáp án:** Bắt `InvocationTargetException` rồi `throw e.getCause()` khi chữ ký API cho phép; nếu buộc phải bọc lại, luôn gắn cause gốc.

**Ghi nhớ:** Với `Method.invoke`, hãy **kiểm thử hai tầng**: wrapper `InvocationTargetException` mô tả cơ chế reflection, còn `getCause()` là exception/error do mã đích thật sự ném. Test chất lượng xác nhận **type, identity, message, cause chain và ranh giới trước/sau khi target chạy**.

Tham khảo: [Java 17 `Method.invoke`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Method.html#invoke(java.lang.Object,java.lang.Object...)), [`InvocationTargetException`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/InvocationTargetException.html), [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/).

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-testing-006">Kiểm thử runtime annotation, repeatable và type-use annotation</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử runtime annotation, repeatable và type-use annotation trong Java Reflection

Annotation trong Java là metadata gắn lên khai báo hoặc vị trí sử dụng kiểu. Khi viết công cụ dựa trên Reflection, chỉ thấy cú pháp `@Something` trong mã nguồn **không đủ** để kết luận ứng dụng sẽ đọc được annotation đó ở runtime. Kết quả còn phụ thuộc `@Retention`, `@Target`, việc annotation được lặp lại hay không, **vị trí** đặt annotation và API Reflection dùng để đọc.

Bài này hướng dẫn thiết kế phép kiểm thử tự động cho ba trường hợp dễ nhầm:

1. **Runtime annotation:** annotation có thật sự tồn tại ở runtime, và trên đúng phần tử dự kiến?
2. **Repeatable annotation:** khai báo nhiều lần có được truy xuất đầy đủ bằng `getAnnotationsByType`?
3. **Type-use annotation:** metadata gắn lên *vị trí sử dụng kiểu* (kiểu trả về, tham số kiểu, đối số generic, mảng…) có được tìm bằng nhóm API `AnnotatedType` thay vì chỉ đọc annotation của phần tử khai báo?

Ví dụ chạy trên **Java 17+**, không cần thư viện ngoài. Ngoài ra, bài phân biệt rõ các trường hợp cần dùng JUnit hoặc chạy kiểm thử trên chính artifact được đóng gói khi xây dựng công cụ thật.

## 1. Ba yếu tố quyết định annotation có đọc được hay không

Một annotation có thể có ba cấu hình liên quan:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Action {
    String value();
}
```

- **`@Retention`** quyết định annotation còn được lưu đến đâu.
- **`@Target`** quy định các vị trí hợp lệ để đặt annotation.
- **`@Repeatable`** cho phép khai báo nhiều instance của cùng annotation type trên một phần tử/vị trí hợp lệ, bằng một container annotation tương thích.

### Retention policy

| Chính sách | Compiler lưu giữ đến đâu? | Đọc bằng Core Reflection lúc chạy? |
|---|---|---|
| `RetentionPolicy.SOURCE` | Chỉ phục vụ mã nguồn, không có nghĩa vụ xuất hiện trong class file | Không |
| `RetentionPolicy.CLASS` | Lưu trong class file (mặc định khi không ghi `@Retention`), nhưng JVM không bắt buộc giữ để Reflection đọc | **Không** qua API runtime annotation chuẩn |
| `RetentionPolicy.RUNTIME` | Được giữ để JVM cung cấp qua Reflection | **Có**, khi metadata còn tồn tại và đọc tại đúng vị trí |

Một class file chứa annotation `CLASS` **không có nghĩa** `Class.getAnnotation(...)` phải trả về đối tượng annotation. Kiểm thử runtime phải dùng `@Retention(RUNTIME)` nếu framework cần đọc annotation khi chạy.

### Target

Ví dụ các target phổ biến:

- `ElementType.TYPE`: khai báo class, interface, enum, record hoặc một số dạng khai báo kiểu.
- `ElementType.METHOD`, `FIELD`, `CONSTRUCTOR`: phần tử khai báo tương ứng.
- `ElementType.PARAMETER`: **khai báo tham số** của method/constructor.
- `ElementType.TYPE_USE`: **vị trí sử dụng kiểu**, chẳng hạn `List<@Mark String>`, kiểu trả về, kiểu tham số, thành phần mảng, superclass được dùng.
- `ElementType.TYPE_PARAMETER`: khai báo tham số kiểu như `class Box<@Mark T>`.

**Khác nhau quan trọng:** annotation có `@Target(PARAMETER)` được đọc ở nhóm API annotation của parameter declaration, còn annotation có `@Target(TYPE_USE)` đặt trên *kiểu của tham số* cần đọc qua `getAnnotatedParameterTypes()`. Nếu annotation có nhiều target, hãy xét chính xác nơi nó xuất hiện và metadata được compiler ghi.

## 2. Kiểm thử runtime annotation: khai báo và đọc đúng vị trí

Với:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface RuntimeInfo {
    String value();
}

@RuntimeInfo("active")
class Plugin {}
```

Một kiểm thử cần xác nhận cả **hiện diện** lẫn **giá trị**:

```java
RuntimeInfo info = Plugin.class.getAnnotation(RuntimeInfo.class);
if (info == null || !info.value().equals("active")) {
    throw new AssertionError("Missing or incorrect runtime annotation");
}
```

Đây là hai mảnh mã minh họa có thể được đặt cùng một chương trình Java thích hợp. Những câu lệnh chỉ kiểm tra `info != null` không phát hiện trường hợp annotation còn tồn tại nhưng các thuộc tính quan trọng bị sai. Nếu annotation tạo contract cho plugin, hãy kiểm tra cả method/class đích, thuộc tính bắt buộc và cách framework ánh xạ chúng thành hành vi.

Các API thường gặp:

| API | Trả về | Lưu ý |
|---|---|---|
| `Class.getAnnotation(A.class)` | Một annotation thuộc loại `A` hoặc `null` | Có xét annotation được kế thừa trên class khi `A` có `@Inherited` theo quy tắc API |
| `Class.getDeclaredAnnotation(A.class)` | Annotation được khai báo trực tiếp hoặc `null` | Không lấy annotation chỉ do kế thừa |
| `isAnnotationPresent(A.class)` | Boolean | Phản ánh ngữ nghĩa hiện diện của API; không trả giá trị annotation |
| `getAnnotations()` | Các annotation hiện diện theo quy tắc kế thừa | Không tự “bung” mọi container repeatable |
| `getDeclaredAnnotations()` | Annotation trực tiếp | Có thể chứa container annotation của repeatable |
| `Method.getAnnotation(A.class)` | Annotation method | Khác với annotation gắn trên **kiểu trả về** |

`@Inherited` áp dụng cho kế thừa annotation ở **khai báo class**, không tự áp dụng cho method, interface hoặc annotation type-use. Dù có `@Inherited`, một method override cũng không tự “thừa kế” method annotation bằng quy tắc đó; framework muốn tìm từ superclass/interface phải tự định nghĩa thuật toán.

## 3. Repeatable annotation: “nhìn thấy container” không phải đã đọc hết

Repeatable annotation phải có một container phù hợp:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(Labels.class)
@interface Label {
    String value();
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface Labels {
    Label[] value();
}

@Label("fast")
@Label("safe")
class Handler {}
```

Compiler biểu diễn các `@Label` lặp lại thông qua container `@Labels` (với nhiều annotation cùng loại tại một vị trí). Vì thế:

- `Handler.class.getAnnotationsByType(Label.class)` lấy đủ **hai** `Label`, theo thứ tự có thể quan sát từ khai báo.
- `Handler.class.getAnnotation(Label.class)` trong trường hợp chỉ có hai `Label` được chứa gián tiếp thường trả `null`, vì API này **không tự mở container**.
- `Handler.class.getAnnotation(Labels.class)` cho thấy container được lưu tại runtime.
- `getDeclaredAnnotationsByType(Label.class)` là lựa chọn tốt khi muốn **chỉ** đọc annotation liên quan trực tiếp đến class đang kiểm tra.

Các ràng buộc quan trọng của container:

1. Container có một element `value()` trả về mảng kiểu annotation lặp lại (ví dụ `Label[]`).
2. `@Retention` và `@Target` của container phải tương thích với annotation lặp lại theo luật Java; không thể tùy tiện chọn container có retention ngắn hơn.
3. Nếu muốn truy xuất repeatable annotation ở runtime, cả repeatable type và container phải được thiết kế/đóng gói phù hợp để Reflection thấy được.
4. Nếu cần kế thừa repeatable class annotation, kiểm tra riêng cả repeatable type và container liên quan đến `@Inherited`. Đừng dùng kiểm thử trên một class đơn lẻ để suy ra hành vi của subclass.
5. Không dùng `getAnnotation(Label.class)` làm phép thử duy nhất cho repeatable; dùng `getAnnotationsByType(Label.class)` hoặc `getDeclaredAnnotationsByType(Label.class)`.

**Chú ý:** API `getAnnotationsByType` hỗ trợ cả trường hợp chỉ một annotation và trường hợp container gián tiếp. Nó phù hợp hơn khi một annotation type được định nghĩa là repeatable. Đừng hard-code tên container trong ứng dụng nếu API by-type đã giải quyết đúng nhu cầu.

## 4. Type-use annotation: đọc qua AnnotatedType

Xét:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface TypeHint {
    String value();
}
```

Với method:

```java
public @TypeHint("return") List<@TypeHint("element") String> names(
        @TypeHint("parameter-type") String input) {
    return List.of(input);
}
```

Đây là **mảnh mã minh họa**: method cần được đặt trong class cùng với import `java.util.List` và định nghĩa `TypeHint` ở trên.

Có ít nhất ba vị trí metadata khác nhau:

- `@TypeHint("return")` gắn trên **kiểu trả về** là `List<String>`.
- `@TypeHint("element")` gắn trên **đối số kiểu generic** `String` nằm trong `List<...>`.
- `@TypeHint("parameter-type")` gắn trên **kiểu của tham số** `String`.

Để đọc đúng:

```java
Method m = SomeClass.class.getDeclaredMethod("names", String.class);

AnnotatedType result = m.getAnnotatedReturnType();
TypeHint onReturn = result.getAnnotation(TypeHint.class);

AnnotatedParameterizedType generic =
        (AnnotatedParameterizedType) result;
TypeHint onElement = generic.getAnnotatedActualTypeArguments()[0]
        .getAnnotation(TypeHint.class);

TypeHint onParameter = m.getAnnotatedParameterTypes()[0]
        .getAnnotation(TypeHint.class);
```

Đoạn này giả định `SomeClass` khai báo method phù hợp. Thao tác `getAnnotatedActualTypeArguments()` đi sâu vào cấu trúc generic. Nếu runtime type không phải `AnnotatedParameterizedType`, cần kiểm tra bằng `instanceof` thay vì cast tùy tiện.

**Các nhầm lẫn thường gặp:**

- `m.getAnnotation(TypeHint.class)` tìm annotation gắn trên **khai báo method**, không tìm annotation gắn trên **kiểu trả về**.
- `m.getParameterAnnotations()[0]` đọc annotation khai báo parameter. Với `TypeHint` chỉ có target `TYPE_USE`, ở ví dụ trên danh sách declaration annotation của parameter có thể rỗng — đây là hành vi đúng.
- `m.getReturnType()` chỉ trả về `Class<?>` của kiểu trả về (ví dụ `List.class`); nó không cung cấp vị trí annotation của `List<@TypeHint String>`.
- `m.getGenericReturnType()` trả biểu diễn kiểu generic, nhưng để lấy **type-use annotation**, cần cấu trúc `AnnotatedType` tương ứng.

Một số API bổ sung khi xây công cụ đọc metadata chuyên sâu:

| Vị trí cần kiểm tra | API phù hợp |
|---|---|
| Kiểu của field | `Field.getAnnotatedType()` |
| Kiểu trả về method | `Method.getAnnotatedReturnType()` |
| Kiểu tham số method | `Method.getAnnotatedParameterTypes()` |
| Kiểu exception được khai báo | `Method.getAnnotatedExceptionTypes()` |
| Kiểu superclass/interface được dùng | `Class.getAnnotatedSuperclass()` / `getAnnotatedInterfaces()` |
| Đối số kiểu generic | `AnnotatedParameterizedType.getAnnotatedActualTypeArguments()` |
| Thành phần mảng | `AnnotatedArrayType.getAnnotatedGenericComponentType()` |
| Biên của wildcard hoặc type variable | `AnnotatedWildcardType` / `AnnotatedTypeVariable` cùng API bound tương ứng |

Với `String @TypeHint []` và `@TypeHint String[]`, annotation có thể được đặt ở **các cấp kiểu khác nhau**. Không nên gom mọi annotation type-use thành một danh sách phẳng rồi mất vị trí. Kiểm thử phải đi sâu vào đúng nhánh `AnnotatedType`.

## 5. Chương trình Java 17+ kiểm thử cả ba trường hợp

Ví dụ dưới đây chạy độc lập và **không dùng `assert` của Java**, bởi `assert` thường bị tắt khi không chạy với `-ea`. Hàm `check` luôn ném `AssertionError` nếu kết quả Reflection sai.

Lưu thành **`AnnotationInspectionDemo.java`**:

```java
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.List;

public class AnnotationInspectionDemo {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface RuntimeInfo { String value(); }

    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    @interface BuildOnly {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Repeatable(Labels.class)
    @interface Label { String value(); }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @interface Labels { Label[] value(); }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    @interface TypeHint { String value(); }

    @RuntimeInfo("present")
    @BuildOnly
    @Label("a")
    @Label("b")
    static class Example {
        public @TypeHint("return") List<@TypeHint("element") String> names(
                @TypeHint("parameter") String value) {
            return List.of(value);
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        Class<?> type = Example.class;
        RuntimeInfo info = type.getAnnotation(RuntimeInfo.class);
        check(info != null && info.value().equals("present"), "runtime info");
        check(type.getAnnotation(BuildOnly.class) == null, "CLASS retention");
        Label[] labels = type.getAnnotationsByType(Label.class);
        check(labels.length == 2, "repeatable labels");
        check(type.getAnnotation(Label.class) == null, "indirectly present label");
        check(type.getAnnotation(Labels.class) != null, "container");
        Method method = type.getDeclaredMethod("names", String.class);
        AnnotatedType resultType = method.getAnnotatedReturnType();
        TypeHint resultHint = resultType.getAnnotation(TypeHint.class);
        AnnotatedType elementType =
                ((AnnotatedParameterizedType) resultType)
                        .getAnnotatedActualTypeArguments()[0];
        TypeHint elementHint = elementType.getAnnotation(TypeHint.class);
        TypeHint parameterHint = method.getAnnotatedParameterTypes()[0]
                .getAnnotation(TypeHint.class);
        check(resultHint != null && resultHint.value().equals("return"), "return type");
        check(elementHint != null && elementHint.value().equals("element"), "generic element");
        check(parameterHint != null && parameterHint.value().equals("parameter"), "parameter type");
        check(method.getParameterAnnotations()[0].length == 0,
                "TYPE_USE is not declaration PARAMETER");

        System.out.println("runtime class annotation = " + info.value());
        System.out.println("CLASS retention visible = "
                + (type.getAnnotation(BuildOnly.class) != null));
        System.out.println("repeatable values = "
                + labels[0].value() + ", " + labels[1].value());
        System.out.println("container present = "
                + (type.getAnnotation(Labels.class) != null));
        System.out.println("return type = " + resultHint.value());
        System.out.println("type argument = " + elementHint.value());
        System.out.println("parameter type = " + parameterHint.value());
        System.out.println("parameter declaration annotations = "
                + method.getParameterAnnotations()[0].length);
        System.out.println("ALL CHECKS PASSED");
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 -encoding UTF-8 AnnotationInspectionDemo.java
java AnnotationInspectionDemo
```

**Stdout thực tế của ví dụ:**

```text
runtime class annotation = present
CLASS retention visible = false
repeatable values = a, b
container present = true
return type = return
type argument = element
parameter type = parameter
parameter declaration annotations = 0
ALL CHECKS PASSED
```

Mã đã được kiểm chứng bằng `javac --release 17` trên JDK mới hơn và chạy thành công với thứ tự stdout trên.

### Đọc kết quả theo từng trường hợp

1. `RuntimeInfo` có `RetentionPolicy.RUNTIME` nên `getAnnotation(RuntimeInfo.class)` trả về instance với giá trị `present`.
2. `BuildOnly` chỉ có `RetentionPolicy.CLASS` nên `getAnnotation(BuildOnly.class)` trả `null` dù annotation có trong mã nguồn.
3. Hai `@Label` cùng gắn trên class được gom vào container `@Labels`. Vì vậy `getAnnotationsByType(Label.class)` trả hai instance, còn `getAnnotation(Label.class)` trả `null` ở trường hợp lặp này.
4. `getAnnotatedReturnType()` tìm `return` trên kiểu trả về; `AnnotatedParameterizedType` tìm `element` trên `String` là đối số generic.
5. `getAnnotatedParameterTypes()[0]` đọc `parameter` trên **kiểu** tham số. `getParameterAnnotations()[0]` rỗng vì annotation được khai báo chỉ cho `TYPE_USE`.
6. Dòng `ALL CHECKS PASSED` chỉ xuất hiện sau khi mọi điều kiện bắt buộc đều đúng. Nếu đổi retention/target, xóa container hoặc dùng sai API, kiểm thử sẽ thất bại hoặc compiler từ chối định nghĩa không hợp lệ.

## 6. Biến phép kiểm thử thành bộ test tự động

Trong hệ thống có CI, không chỉ kiểm tra stdout. Hãy chuyển các điều kiện `check(...)` thành những assertion của JUnit 5 (JUnit Jupiter), chạy cùng artifact và dependencies mà ứng dụng thực sự sử dụng.

Ví dụ minh họa một test method trong class kiểm thử:

```java
@Test
void repeatableAnnotationsAreVisible() {
    Label[] labels = Example.class.getAnnotationsByType(Label.class);
    assertEquals(2, labels.length);
    assertEquals("a", labels[0].value());
    assertEquals("b", labels[1].value());
}
```

Đoạn trên **không phải chương trình độc lập**: giả định có import `org.junit.jupiter.api.Test`, static import `org.junit.jupiter.api.Assertions.assertEquals` và định nghĩa `Example`/`Label` phù hợp trong phạm vi truy cập. Với JUnit, test thất bại khi annotation mất hoặc giá trị khác. Dùng test tương tự cho `getAnnotatedReturnType()`, `getAnnotatedParameterTypes()` và việc `CLASS` annotation **không** được truy xuất bằng Reflection.

### Những kiểm tra có giá trị cao

- **Retention:** `RUNTIME` hiện diện; `CLASS` và `SOURCE` không có trong API runtime.
- **Target và vị trí:** class/method/field/parameter declaration/type-use được đọc bằng API tương ứng.
- **Repeatable:** số lượng, giá trị, container, phân biệt `getAnnotation` và `getAnnotationsByType`.
- **Declared vs inherited:** kiểm thử trên class con với `@Inherited`; không suy hành vi của interface hoặc method.
- **Nested types:** `List<@A String>`, `Map<@A String, @B Integer>`, array, wildcard, bounds — kiểm tra đúng nhánh `AnnotatedType`.
- **Thứ tự và tính xác định:** nếu hệ thống phụ thuộc thứ tự annotation được lặp lại, viết test với các giá trị phân biệt, không chỉ đếm số lượng.
- **Khả năng đóng gói:** lặp lại test trên JAR/module/artifact sau shading, shrinking hoặc obfuscation nếu có; annotation và phần tử cần thiết có thể bị đổi/loại bỏ bởi công cụ build.

### Test âm tính rất quan trọng

Đừng chỉ khẳng định “tìm thấy”. Hãy khẳng định những thứ **không được xem là hợp lệ**:

```java
if (Example.class.getAnnotation(BuildOnly.class) != null) {
    throw new AssertionError("CLASS annotation unexpectedly visible");
}
if (Example.class.getAnnotation(Label.class) != null) {
    throw new AssertionError("Expected repeatable container lookup semantics");
}
```

Đây là đoạn kiểm tra minh họa trong cùng phạm vi ví dụ trước. Nó bảo vệ khỏi giả định sai về retention và repeatable lookup. Không nên dựa vào behavior đặc thù của một IDE hoặc việc nhìn thấy annotation trong decompiler để kết luận `Class.getAnnotation` sẽ trả giá trị.

## 7. Kế thừa annotation và cấu trúc type-use phức tạp

### `@Inherited` không thay thế test discovery riêng

Với class annotation đánh dấu `@Inherited`, `Sub.class.getAnnotationsByType(...)` có thể nhìn thấy annotation từ superclass theo quy tắc hiện diện/kế thừa của API, còn `getDeclaredAnnotationsByType(...)` chỉ lấy annotation trên chính `Sub`. Nếu `Sub` khai báo annotation cùng repeatable type, kết quả cũng chịu quy tắc che khuất và liên kết với container, không nên mặc định nối mọi annotation superclass và subclass thành một danh sách chung.

Đừng áp dụng `@Inherited` cho annotation trên interface hoặc method rồi kỳ vọng Reflection tự kết hợp metadata theo cây kế thừa. Với các framework có cơ chế merge annotation riêng, test cả **Core Reflection behavior** lẫn **framework discovery behavior**, vì chúng có thể khác nhau.

### `AnnotatedType` là cây, không phải chuỗi tên

Nếu một công cụ validator đọc:

```java
List<@TypeHint("item") String>
```

metadata nằm ở **đối số generic**; nếu đọc mỗi `List.class.getAnnotations()` sẽ không thấy annotation trên `String`. Với nested generic:

```java
Map<String, List<@TypeHint("leaf") Integer>>
```

phải duyệt `AnnotatedParameterizedType` theo từng cấp, đi đến đối số thứ hai của `Map` rồi đối số đầu tiên của `List`. Các đoạn trên là **kiểu minh họa**, không phải khai báo Java đầy đủ. Nếu cấu trúc là wildcard, mảng hoặc type variable, cần dùng subtype của `AnnotatedType` tương ứng. Bộ kiểm thử nên tạo fixture riêng cho mỗi hình dạng mà công cụ hỗ trợ và khẳng định annotation tại từng vị trí.

## 8. Lỗi thường gặp và hướng sửa

| Hiện tượng | Nguyên nhân thường gặp | Cách kiểm tra |
|---|---|---|
| `getAnnotation(A.class) == null` | Retention không phải RUNTIME, sai target/vị trí, annotation bị mất trong artifact | Xem định nghĩa `@Retention`; chọn đúng element và API |
| Có hai annotation nhưng `getAnnotation(A.class)` trả null | Repeatable được chứa gián tiếp trong container | Gọi `getAnnotationsByType(A.class)` |
| `getParameterAnnotations()` rỗng nhưng mã nguồn có `@A String` | Annotation chỉ dùng cho `TYPE_USE` | Gọi `getAnnotatedParameterTypes()` |
| Không tìm thấy annotation ở `List<@A String>` | Đọc type declaration hoặc return type gốc, chưa đi sâu generic | Dùng `AnnotatedParameterizedType` |
| Test thấy annotation ở superclass nhưng không ở subclass | Dùng API declared hoặc sai giả định về `@Inherited` | Tách test present/declared/inherited |
| Annotation có trong source nhưng runtime build không có | Shrinker/obfuscator loại metadata hoặc định nghĩa annotation | Test trên artifact phát hành; cấu hình keep rules phù hợp |
| `ClassCastException` khi cast `AnnotatedType` | Kiểu thực tế không phải parameterized type | Kiểm tra `instanceof` rồi duyệt theo hình dạng |
| Giá trị annotation khác kỳ vọng | Sai default/member values hoặc dùng sai type/vị trí | Assert cả giá trị, không chỉ `isAnnotationPresent` |
| Kết quả thay đổi khi dùng framework | Framework có thuật toán merge/search khác Core Reflection | Viết test độc lập cho cả Reflection và framework |

Cần phân biệt *annotation không xuất hiện* với *không đọc được class/method* do class loading hoặc access/module issue. `getDeclaredMethods()` có thể ném lỗi linkage nếu dependency cần thiết vắng mặt; đó không phải bằng chứng annotation bị thiếu.

## 9. Checklist chất lượng cho công cụ đọc annotation

Trước khi phát hành một annotation scanner/validator, nên xác minh:

1. Annotation type có `RUNTIME` retention khi runtime discovery là yêu cầu.
2. Target cho phép đúng element và vị trí sử dụng kiểu.
3. Repeatable annotation có container hợp lệ, và scanner dùng by-type API.
4. Scanner phân biệt direct, declared và inherited metadata.
5. Scanner đi đúng cấu trúc `AnnotatedType` cho generic, mảng, bounds, wildcard.
6. Không suy ra annotation ở method declaration từ annotation ở return type hoặc ngược lại.
7. Test cả trường hợp hiện diện, vắng mặt, sai giá trị, sai cấu trúc và class/container metadata bị mất.
8. CI chạy trên JDK và artifact phù hợp môi trường triển khai; nếu có transformer/shrinker thì chạy cả bản đã biến đổi.
9. Không lấy thứ tự của `getDeclaredMethods()` hoặc `getDeclaredAnnotations()` làm API contract nếu thứ tự đó không được bảo đảm.
10. Báo lỗi có thông tin class, method, vị trí kiểu và annotation name, để developer biết chính xác fixture nào không đúng.

## 10. Câu hỏi tự kiểm tra

**Câu 1.** Annotation khai báo `@Retention(CLASS)` có thể xuất hiện trong class file. Vì sao `getAnnotation` không lấy được?

Vì `CLASS` retention không yêu cầu annotation được giữ cho Core Reflection lúc chạy; muốn API runtime thấy nó phải dùng `RUNTIME`.

**Câu 2.** Hai lần `@Label` ở cùng class có thể trả `null` khi gọi `getAnnotation(Label.class)`. Có nghĩa chúng đã mất?

Không. Chúng có thể được đại diện gián tiếp bằng container. Dùng `getAnnotationsByType(Label.class)` để lấy đầy đủ.

**Câu 3.** `getParameterAnnotations()` và `getAnnotatedParameterTypes()` có giống nhau?

Không. API đầu đọc annotation của khai báo tham số; API sau đọc annotation trên **kiểu tham số**.

**Câu 4.** Muốn đọc `@A` trong `List<@A String>` thì gọi `List.class.getAnnotation(A.class)` được không?

Không. `@A` ở **type argument String** tại vị trí dùng kiểu, không trên khai báo `java.util.List`. Phải duyệt `AnnotatedParameterizedType`.

**Câu 5.** Vì sao cần kiểm thử repeatable annotation và type-use trên artifact đã đóng gói?

Vì shrinker/obfuscation, classpath, module hoặc transform bytecode có thể ảnh hưởng thành viên và metadata runtime; test source/debug chưa chắc phản ánh môi trường phát hành.

## Tài liệu tham khảo

- Java SE 17 API: [`Retention`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Retention.html), [`Target`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Target.html), [`Repeatable`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/annotation/Repeatable.html).
- Java SE 17 API: [`AnnotatedElement`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedElement.html), [`AnnotatedType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedType.html), [`AnnotatedParameterizedType`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/AnnotatedParameterizedType.html).
- Java Language Specification §9.6–§9.7: annotation types, repeatable annotations, applicability; §9.6.4.2–§9.6.4.3: retention và target; §9.6.4.3 và các quy tắc liên quan đến repeatable/container theo phiên bản JLS.

**Ghi nhớ:** Muốn kiểm thử annotation bằng Reflection, luôn hỏi **“annotation có RUNTIME retention không, được đặt tại loại vị trí nào, và có phải repeatable hay type-use không?”**. Câu trả lời xác định API đúng: `getAnnotation` cho metadata trực tiếp thông thường, `getAnnotationsByType` cho repeatable, và cây `AnnotatedType` cho annotation trên vị trí sử dụng kiểu.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-testing-007">Kiểm thử non-static inner class, enum, record và sealed type</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử Reflection với non-static inner class, enum, record và sealed type

## 1. Vì sao bốn kiểu này cần ca kiểm thử riêng?

Khi viết một công cụ dùng Java Reflection để khám phá, tạo instance, đọc metadata hoặc kiểm tra cấu trúc lớp, không thể coi mọi `Class<?>` như một top-level class thông thường có constructor không tham số và các field có thể chỉnh sửa. **Non-static inner class, enum, record và sealed type** có những hợp đồng khác nhau về constructor, định danh, tính bất biến, quan hệ kế thừa và khả năng tạo đối tượng.

Bài học này tập trung vào **cách thiết kế bài kiểm thử cho công cụ Reflection**. Mục tiêu là kiểm tra hành vi được Java/JDK bảo đảm, tách khỏi chi tiết do compiler sinh ra, và viết assertions rõ ràng thay vì chỉ in metadata rồi suy đoán.

| Loại kiểu | Điều kiện quan trọng | Điều gì nên kiểm thử? |
| --- | --- | --- |
| Non-static member inner class | Instance của outer class gắn với inner instance | `isMemberClass()`, `getEnclosingClass()`, constructor có tham số outer, tạo instance đúng. |
| Enum | Tập constant do khai báo enum quản lý; Reflection không được tạo enum instance mới | `isEnum()`, `getEnumConstants()`, `Field.isEnumConstant()`, `Enum.valueOf` và thử constructor bị từ chối. |
| Record | State được khai báo bởi record components và các field thành phần `final` | `isRecord()`, `getRecordComponents()`, canonical constructor, accessor, không chỉnh sửa field thành phần bằng `Field.set`. |
| Sealed class/interface | Chỉ những direct subtype được cho phép trong `permits` | `isSealed()`, `getPermittedSubclasses()` và kiểm chứng danh sách direct permitted types. |

**Không có một bộ lọc field/constructor chung có thể thay thế những kiểm thử này.** Ví dụ, `getDeclaredFields()` có thể thấy field hỗ trợ cho inner class, còn `getRecordComponents()` lại mô tả thành phần của record theo quy tắc ngôn ngữ.

## 2. Chuẩn bị cách kiểm thử an toàn và ổn định

Dùng **JDK 17 trở lên** vì record và sealed class/interface trở thành tính năng chính thức từ Java 16 và Java 17. Một file demo chạy ở classpath đơn giản giúp tập trung vào ngữ nghĩa Reflection, tránh nhầm lỗi module với lỗi thiết kế kiểu.

Các quy tắc thiết kế test:

1. **Kiểm tra điều kiện bằng assertion**: sử dụng `AssertionError`/JUnit hoặc kiểm tra điều kiện rõ ràng; không chỉ quan sát `println`.
2. **Kiểm tra metadata trước khi gọi thực thi**: tìm constructor khác với `Constructor.newInstance`, đọc field metadata khác `Field.get` hoặc `Field.set`.
3. **Dùng API chuyên biệt**: `isEnum` và `getEnumConstants` cho enum, `isRecord` và `getRecordComponents` cho record, `isSealed` và `getPermittedSubclasses` cho sealed type.
4. **Không chốt tên/compiler artifacts**: không assert field `this$0`, `$VALUES`, tên số của local/anonymous class, hoặc thứ tự các mảng field/method do Reflection trả về.
5. **Kiểm tra lỗi mong đợi đúng loại**: ví dụ `NoSuchMethodException` khi tìm constructor không tồn tại; `IllegalArgumentException` khi cố tạo enum bằng Reflection; `IllegalAccessException` khi cố sửa field thành phần record sau khi bật accessible trong ví dụ.
6. **Tách test harness khỏi ứng dụng thật**: kiểm thử cùng classpath/nest dễ truy cập non-public member; khi triển khai vào named module khác phải thêm test về access boundary.

## 3. Non-static inner class: constructor cần outer instance

Xét `Outer.Inner` là **member class không khai báo `static`**. Mỗi instance `Inner` gắn với một instance `Outer`. Vì vậy constructor ở mức JVM có tham số đầu đại diện enclosing instance của `Outer`, ngay cả khi constructor ở mã nguồn chỉ khai báo `Inner(String label)`.

Ví dụ khái niệm:

~~~java
class Outer {
    class Inner {
        Inner(String label) {}
    }
}
~~~

Đây chỉ là mảnh khai báo; chương trình hoàn chỉnh nằm ở phần 7.

Kiểm thử phù hợp:

- `Outer.Inner.class.isMemberClass()` phải là `true`: đây là **member class**.
- `getEnclosingClass() == Outer.class` xác nhận lớp bao ngoài.
- `getDeclaredConstructor()` **không tồn tại** trong ví dụ chỉ có constructor nhận `String` và enclosing instance; không nên tự giả định có zero-argument constructor.
- `getDeclaredConstructor(Outer.class, String.class)` tìm được constructor phản chiếu.
- `newInstance(new Outer(), "A")` tạo inner instance thành công khi phạm vi truy cập phù hợp.

**Phân biệt:** `Outer.StaticNested` được khai báo `static` không cần enclosing instance. **Local class** nằm trong phương thức có quy tắc capture/constructor khác; đừng đồng nhất mọi nested/local/anonymous class. Các field hỗ trợ chứa outer reference do compiler tạo có thể là synthetic nhưng tên/biểu diễn cụ thể không được dùng làm hợp đồng test.

Nếu gọi `Class.getConstructors()` thay vì `getDeclaredConstructors()`, bạn chỉ xem các constructor `public`. Khi constructor trong ví dụ là package-private, dùng `getDeclaredConstructor` phù hợp hơn. Với module khác, việc tìm thấy constructor không có nghĩa có quyền gọi nó; `canAccess`, `trySetAccessible` và `InaccessibleObjectException` phải được xử lý tùy tình huống.

## 4. Enum: liệt kê constant, không tạo instance qua constructor

Đối với:

~~~java
enum Stage { NEW, DONE }
~~~

`Stage.class.isEnum()` trả `true`, và `Stage.class.getEnumConstants()` trả các constant theo **thứ tự khai báo**: `[NEW, DONE]`. `Enum.valueOf(Stage.class, "DONE")` tra cứu constant hiện có. Dùng `getDeclaredFields()` để nhìn toàn bộ field sẽ có thể gặp cả thành phần liên quan hiện thực, vì vậy nếu thực sự cần phân biệt field biểu diễn enum constant, dùng `Field.isEnumConstant()`.

### Constructor enum là ngoại lệ có chủ đích

`Stage.class.getDeclaredConstructors()` có thể cho thấy constructor của enum, nhưng **`Constructor.newInstance` không được phép tạo thêm enum instance**. API ném `IllegalArgumentException` khi declaring class của constructor là enum, kể cả sau khi đã vượt được kiểm tra quyền truy cập thông thường.

Đừng dùng việc tìm thấy constructor làm lý do để thử `newInstance` cho tất cả `Class<?>`. Factory của enum là các constant đã định nghĩa, truy cập qua `valueOf` hoặc `getEnumConstants`.

**Cạm bẫy:** enum constant có class body riêng có thể có runtime class là một subclass đặc biệt của enum; `constant.getClass().isEnum()` không nhất thiết `true`. `constant.getDeclaringClass()` cho enum type đã khai báo. Không assert các tên lớp đặc thù của compiler.

## 5. Record: kiểm thử components và bất biến của state

Đối với:

~~~java
record Person(String name, int age) {}
~~~

`Person.class.isRecord()` là `true`. `getRecordComponents()` trả các `RecordComponent` theo **thứ tự record header**: `name` rồi `age`. Mỗi component có metadata tên, kiểu và accessor; `getAccessor()` trả `Method` tương ứng.

Ví dụ `Person.class.getDeclaredConstructor(String.class, int.class)` tìm **canonical constructor** với tham số theo thứ tự của record header. Trong demo, constructor này được gọi qua Reflection và tạo `new Person("Lan", 20)`.

### Không coi `setAccessible(true)` là quyền sửa mọi field

Field thành phần của record là `final` theo cơ chế bất biến của record. Trong `java.lang.reflect.Field`, các field `static final`, field `final` của hidden class và field `final` của record là nhóm **non-modifiable final fields**. Kể cả khi `setAccessible(true)` được chấp nhận để đọc, không thể dùng `Field.set` sửa field thành phần record. Với ví dụ, lời gọi `age.set(person, 99)` ném `IllegalAccessException` và `person.age()` vẫn bằng `20`.

Test nên kiểm tra cả **exception** và **state không đổi**. Không nên coi “có thể lấy `Field`” tương đương “có thể ghi field”.

### Field và component không hoàn toàn đồng nhất

`getDeclaredFields()` mô tả field JVM trong lớp, trong khi `getRecordComponents()` phản ánh **cấu trúc khai báo record**. Nếu công cụ xử lý schema/DTO từ record, thường nên lấy record components trước. Annotation trên component, field và accessor có quy tắc `@Target`/propagation riêng: phải kiểm tra annotation ở đúng vị trí mà công cụ sử dụng.

Một class thường trả `null` từ `getRecordComponents()`; đừng duyệt kết quả trước khi xác nhận `isRecord()` hoặc xử lý `null`.

## 6. Sealed type: danh sách direct permitted subclasses

Ví dụ:

~~~java
sealed interface Shape permits Circle, Rectangle {}

record Circle(int radius) implements Shape {}

final class Rectangle implements Shape {}
~~~

Đây là **mảnh khai báo cùng file/package**; chương trình chạy độc lập có khai báo tương đương ở phần 7. `Shape.class.isSealed()` là `true` và `getPermittedSubclasses()` trả các kiểu được phép **trực tiếp** triển khai/mở rộng `Shape`.

Các phép kiểm tra:

- `Shape.class.getPermittedSubclasses()` chứa `Circle.class` và `Rectangle.class`.
- `Circle.class.isRecord()` là `true`; record luôn là final, nên có thể là subtype trực tiếp hợp lệ của sealed interface.
- `Rectangle.class` được khai báo `final`, không phải sealed; `isSealed()` là `false`.
- `getPermittedSubclasses()` không có nghĩa “mọi subtype gián tiếp có thể tồn tại”: trong phân cấp cho phép nhánh `non-sealed`, có thể có nhiều thế hệ con khác. API đang mô tả **direct permitted subtypes**.

**Không dựa vào thứ tự mảng trả về** nếu cần kết quả ổn định; demo ánh xạ sang tên rồi sắp xếp trước khi assert. Với non-sealed type, `Class.getPermittedSubclasses()` trả `null`. Gọi `isSealed()` hoặc kiểm tra `null` trước khi duyệt.

Một lưu ý ứng dụng: **sealed interface không hợp lệ làm interface đầu vào cho JDK `Proxy.newProxyInstance`** (API yêu cầu các interface đầu vào không sealed). Đây là quy tắc riêng của dynamic proxy; không có nghĩa không thể viết một lớp `final` cho phép trong `permits`.

## 7. Chương trình kiểm thử hoàn chỉnh trên Java 17+

Tạo file **`ReflectionSpecialTypesTest.java`** với toàn bộ mã sau. Chương trình dùng `check` để ném `AssertionError` nếu phát hiện sai lệch; do đó dòng cuối chỉ xuất hiện khi bốn nhóm kiểm thử đều vượt qua.

~~~java
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

public class ReflectionSpecialTypesTest {
    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static final class Outer {
        class Inner {
            private final String label;

            Inner(String label) { this.label = label; }

            String display() { return "inner:" + label; }
        }
    }

    enum Stage { NEW, DONE }

    record Person(String name, int age) {}

    sealed interface Shape permits Circle, Rectangle {}

    record Circle(int radius) implements Shape {}

    static final class Rectangle implements Shape {}

    static void testInner() throws Exception {
        Class<?> type = Outer.Inner.class;
        check(type.isMemberClass(), "must be a member class");
        check(type.getEnclosingClass() == Outer.class, "wrong enclosing class");

        boolean noZeroArg = false;
        try {
            type.getDeclaredConstructor();
        } catch (NoSuchMethodException expected) {
            noZeroArg = true;
        }
        check(noZeroArg, "unexpected zero-arg constructor");

        Constructor<?> ctor = type.getDeclaredConstructor(Outer.class, String.class);
        Object instance = ctor.newInstance(new Outer(), "A");
        check(((Outer.Inner) instance).display().equals("inner:A"), "wrong inner value");
        System.out.println("inner: outer argument required, value=inner:A");
    }

    static void testEnum() throws Exception {
        Class<Stage> type = Stage.class;
        check(type.isEnum(), "not an enum");
        check(Arrays.equals(type.getEnumConstants(), Stage.values()), "enum constants differ");
        check(Enum.valueOf(type, "DONE") == Stage.DONE, "lookup mismatch");

        List<String> constants = Arrays.stream(type.getDeclaredFields())
                .filter(Field::isEnumConstant).map(Field::getName).sorted().toList();
        check(constants.equals(List.of("DONE", "NEW")), "enum field filter");
        Constructor<?> ctor = type.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        boolean forbidden = false;
        try {
            ctor.newInstance("INJECTED", 10);
        } catch (IllegalArgumentException expected) {
            forbidden = true;
        }
        check(forbidden, "enum reflection construction must fail");
        System.out.println("enum: [NEW, DONE], reflective construction rejected");
    }

    static void testRecord() throws Exception {
        Class<Person> type = Person.class;
        check(type.isRecord(), "not a record");
        RecordComponent[] components = type.getRecordComponents();
        check(Arrays.stream(components).map(RecordComponent::getName)
                .toList().equals(List.of("name", "age")), "component order");
        Person person = type.getDeclaredConstructor(String.class, int.class)
                .newInstance("Lan", 20);
        check(components[0].getAccessor().invoke(person).equals("Lan"), "bad accessor");

        Field age = type.getDeclaredField("age");
        age.setAccessible(true);
        boolean immutable = false;
        try {
            age.set(person, 99);
        } catch (IllegalAccessException expected) {
            immutable = true;
        }
        check(immutable && person.age() == 20, "record field changed");
        System.out.println("record: [name, age], final field write rejected");
    }

    static void testSealed() {
        check(Shape.class.isSealed(), "not sealed");
        List<String> permitted = Arrays.stream(Shape.class.getPermittedSubclasses())
                .map(Class::getSimpleName).sorted().toList();
        check(permitted.equals(List.of("Circle", "Rectangle")), "permitted classes");
        check(!Circle.class.isSealed() && !Rectangle.class.isSealed(),
                "implementations should not be sealed");
        check(Circle.class.isRecord(), "Circle must be a record");
        System.out.println("sealed: permitted=[Circle, Rectangle]");
    }

    public static void main(String[] args) throws Exception {
        testInner();
        testEnum();
        testRecord();
        testSealed();
        System.out.println("ALL CHECKS PASSED");
    }
}
~~~

Biên dịch với đúng mức ngôn ngữ Java 17 và chạy:

~~~bash
javac --release 17 ReflectionSpecialTypesTest.java
java ReflectionSpecialTypesTest
~~~

**Standard output đã được kiểm chứng:**

~~~text
inner: outer argument required, value=inner:A
enum: [NEW, DONE], reflective construction rejected
record: [name, age], final field write rejected
sealed: permitted=[Circle, Rectangle]
ALL CHECKS PASSED
~~~

### Đọc các kết quả theo từng đặc điểm

1. **Inner:** constructor phản chiếu nhận `Outer` ở vị trí đầu; test tạo được instance đúng và phát hiện không có constructor zero-arg.
2. **Enum:** lấy constant theo thứ tự khai báo, lọc field thật sự là enum constant; constructor hiện diện về metadata nhưng việc tạo mới bị cấm.
3. **Record:** components khớp header và accessor đọc giá trị đúng. `Field.set` bị chặn cho final record component ngay cả khi `setAccessible(true)` thành công trong phạm vi demo.
4. **Sealed:** danh sách permitted class gồm đúng hai direct subtype; kiểm thử không mặc định chúng phải là sealed. `Circle` đồng thời là record.
5. **Kết quả cuối:** `ALL CHECKS PASSED` chỉ xuất hiện nếu tất cả `check` đều thành công.

### Vì sao có thể chạy được mà không cần `--add-opens`?

Các lớp thử nghiệm là thành viên trong cùng một top-level class, chạy trên classpath (unnamed module). Test đang thực thi từ cùng enclosing class/nest nên có quyền phù hợp. **Không suy ra** rằng code thư viện trong named module khác có thể bật `setAccessible(true)` với mọi private member. Nếu sản phẩm thật làm việc với module JDK hoặc plugin không mở package, hãy thêm test riêng cho `trySetAccessible`, `IllegalAccessException` và `InaccessibleObjectException`.

## 8. Kiểm thử dựa trên hợp đồng, tránh chi tiết compiler

Một công cụ Reflection hoạt động trên nhiều JDK/compiler nên lựa chọn assertion thận trọng:

| Assertion nên dùng | Assertion nên tránh |
| --- | --- |
| Inner member không static có outer instance trong constructor; constructor công khai/khả dụng theo chính khai báo | Dựa vào tên field hỗ trợ outer như `this$0` hoặc vị trí field trong `getDeclaredFields()`. |
| `isEnum` và `getEnumConstants` theo thứ tự khai báo | Dựa vào field compiler-generated `$VALUES` hoặc constructor parameters mang tên cụ thể. |
| `RecordComponent[]` theo record header; accessor đúng | Xem mọi field JVM của record đều là record component hay có thể sửa field `final`. |
| `isSealed` và tập permitted **direct subtypes** | Coi thứ tự array `getPermittedSubclasses()` là một contract cần khóa chặt khi không có yêu cầu. |
| Assert bằng `Class<?>` identity khi cần | So sánh chỉ tên class trong hệ thống có nhiều ClassLoader. |
| Kiểm đúng exception class và trạng thái sau failure | Phụ thuộc thông báo exception chính xác hoặc stack trace thay đổi theo JDK. |

Khi kiểm thử một helper generic:

- Bắt đầu với phân loại `isEnum`, `isRecord`, `isSealed`, `isMemberClass`, `getEnclosingClass` và `Modifier.isStatic`.
- Có thể xét `isLocalClass`, `isAnonymousClass`, `isHidden` nếu helper tuyên bố hỗ trợ nhiều loại Class. Chúng là các chủ đề liên quan nhưng **không nên mặc định cùng hợp đồng constructor như non-static member inner class**.
- Dùng `getDeclaredConstructor(...)` và `getDeclaredMethods()` khi kiểm tra khai báo nội bộ; dùng `getConstructor(...)` / `getMethods()` nếu mục đích là thành viên public.
- Khi lời gọi phản chiếu thực thi ném exception từ bên trong constructor/method, chú ý `InvocationTargetException` và `getCause()`; không nhầm với `NoSuchMethodException` khi chỉ tìm metadata.

## 9. Lỗi thường gặp và cách khắc phục

### Lỗi 1: Gọi constructor không tham số cho tất cả lớp

~~~java
// Mảnh minh họa cần đặt trong phương thức:
Object value = Outer.Inner.class.getDeclaredConstructor().newInstance();
~~~

Lệnh trên ném `NoSuchMethodException` ở giai đoạn tìm constructor trong fixture, do inner cần outer instance và `String`. **Khắc phục:** xây kế hoạch tạo đối tượng dựa trên loại Class và chữ ký constructor; nếu thư viện không hỗ trợ inner, báo unsupported với lý do rõ ràng.

### Lỗi 2: Duyệt field enum rồi coi mọi field là constant

`getDeclaredFields()` không được hứa chỉ trả các enum constants. **Khắc phục:** dùng `getEnumConstants()` khi cần giá trị; dùng `Field.isEnumConstant()` nếu cần metadata field của các constants.

### Lỗi 3: Coi field `private final` trong record là mutable nhờ `setAccessible`

Record component được bảo vệ bởi đặc điểm final không thể sửa bằng `Field.set`. **Khắc phục:** xây object mới bằng canonical constructor hoặc public factory, thay vì lén chỉnh state của instance đã tạo.

### Lỗi 4: Hiểu `getPermittedSubclasses` là tập mọi subtype trong cây

API phản ánh **direct permitted subclass/interface** của sealed type. **Khắc phục:** khi cần toàn bộ hierarchy, duyệt đệ quy có kiểm soát, phân biệt nhánh `non-sealed` có thể mở cho các subtype không liệt kê tĩnh.

### Lỗi 5: Cố ép truy cập `private` trên module khác

`getDeclaredConstructor` có thể tìm thấy member mà không cho phép gọi. **Khắc phục:** ưu tiên API công khai; nếu thật sự cần deep reflection, thiết kế `opens` đúng package/module và test quyền truy cập một cách tường minh.

### Lỗi 6: Test thất bại chỉ vì output metadata đổi thứ tự

Reflection không bảo đảm thứ tự nhiều tập như `getDeclaredFields()` hay `getDeclaredMethods()`. **Khắc phục:** chuyển thành set hoặc sort theo key ổn định; chỉ dựa vào thứ tự khi tài liệu API xác nhận thứ tự có ý nghĩa như enum constants và record components.

## 10. Bài tập tự kiểm tra

1. Tạo thêm `static class Nested` trong `Outer`, dùng `getDeclaredConstructors()` so sánh tham số với `Outer.Inner`. Nêu chính xác sự khác biệt khi không có enclosing instance.
2. Với enum `Stage`, dùng `Field.isEnumConstant()` và `getEnumConstants()`; chứng minh lý do không nên kiểm thử field compiler-generated theo tên.
3. Thêm component `boolean active` vào `Person`, cập nhật canonical constructor trong test và assertion về thứ tự component. Tại sao test phải sửa khi record header thay đổi?
4. Tạo `non-sealed interface Flexible extends Shape` và cho phép nó trong `permits Shape`. `Shape.getPermittedSubclasses()` trả những direct subtype nào? Các implementation của `Flexible` có phải direct permitted subtype của `Shape` không?
5. Thử tạo enum mới bằng `Constructor.newInstance` nhưng bỏ lời gọi `setAccessible(true)`: loại lỗi đầu tiên có thể thay đổi thế nào theo access context? Vì sao phải thiết kế fixture rõ ràng khi kiểm một hợp đồng cụ thể?
6. Tách demo thành bốn bài JUnit 5, dùng `assertThrows` để kiểm `NoSuchMethodException`, `IllegalArgumentException` và `IllegalAccessException`; không phụ thuộc exact exception message.
7. Viết một classifier trả về `"ENUM"`, `"RECORD"`, `"SEALED"`, `"NON_STATIC_MEMBER"` hoặc `"OTHER"`. Quy định ưu tiên kiểm tra cho kiểu có nhiều đặc tính đồng thời (ví dụ record cũng là permitted subtype của sealed interface) thay vì giả định các phân loại luôn loại trừ nhau.

**Tự đánh giá:** Một công cụ Reflection có thể nhìn thấy rất nhiều metadata, nhưng việc tạo object, sửa field hoặc suy diễn quan hệ kiểu phải dựa trên **hợp đồng ngôn ngữ và API cụ thể**. Với bốn nhóm trên, hãy kiểm thử cả điều kiện thành công lẫn tình huống bị từ chối, dùng fixture kiểm soát được và assertions có ý nghĩa.

## 11. Tài liệu chính thức

- [Oracle Java 17 — java.lang.Class](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html) — `isMemberClass`, `getEnclosingClass`, `isEnum`, `getEnumConstants`, `isRecord`, `getRecordComponents`, `isSealed`, `getPermittedSubclasses`.
- [Oracle Java 17 — Constructor.newInstance](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Constructor.html#newInstance(java.lang.Object...)) — kiểm tra truy cập, ngoại lệ và giới hạn tạo enum instance.
- [Oracle Java 17 — Field.set](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/Field.html#set(java.lang.Object,java.lang.Object)) — non-modifiable final fields của record.
- [Oracle Java 17 — RecordComponent](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/reflect/RecordComponent.html) — metadata component và accessor.
- [JLS Java 17, 8.1.3 — Inner Classes and Enclosing Instances](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.1.3) — ràng buộc về enclosing instance.
- [JLS Java 17, 8.10 — Record Classes](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.10) — record components và constructors.
- [JLS Java 17, 8.9 — Enum Classes](https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.9) — enum constants và constructor.
- [JLS Java 17, 9.1.1.4 — Sealed Interfaces](https://docs.oracle.com/javase/specs/jls/se17/html/jls-9.html#jls-9.1.1.4) — `sealed`, `non-sealed` và `permits`.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-testing-008">Kiểm thử quyền truy cập trong cùng và khác module</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử quyền truy cập Reflection trong cùng và khác module (Java 17+)

## 1. Mục tiêu kiểm thử

Java Reflection cho phép tìm metadata của class, field, method, constructor; **tìm thấy không đồng nghĩa được phép gọi**. Từ Java 9, Java Platform Module System (JPMS) bổ sung ranh giới module, đặc biệt quan trọng trên Java 17+ với strong encapsulation.

Một bộ kiểm thử quyền truy cập cần phân biệt:

1. **Quyền nhìn thấy kiểu ở mã nguồn:** Có thể import một public class từ package đó và biên dịch hay không?
2. **Quyền gọi public member thông thường:** Đường truy cập Java chuẩn vào public API có hợp lệ hay không?
3. **Khả năng lấy metadata:** Có lấy được đối tượng `Method`, `Field` hoặc `Constructor<?>` hay không?
4. **Khả năng sử dụng metadata:** Có gọi `Method.invoke`, `Field.get/set` hoặc `Constructor.newInstance` trực tiếp được không?
5. **Deep reflection:** Có được phép vô hiệu hóa kiểm tra truy cập thông thường bằng `trySetAccessible`/`setAccessible` hay không?
6. **Method handles:** Có tạo `Lookup` phù hợp bằng `privateLookupIn` rồi truy cập private member được không?
7. **Khác biệt môi trường chạy:** Classpath, named module, tùy chọn `opens` trong descriptor và `--add-opens` ở runtime có cho kết quả đúng với kỳ vọng không?

Đề bài “cùng và khác module” **không** chỉ là kiểm thử public/private. Java có thêm ranh giới **package, nest, class loader, module readability và package exports/opens**; không nên gom tất cả về một cờ `isPublic`.

## 2. Bốn khái niệm cần phân biệt: `public`, `requires`, `exports`, `opens`

Giả sử module `com.demo.client` gọi code của module `com.demo.domain`:

| Khái niệm | Vai trò |
| --- | --- |
| `public` trên class/member | Điều kiện visibility của ngôn ngữ Java; không một mình đủ để đi qua ranh giới module |
| `requires com.demo.domain;` | Thiết lập readability của client đối với domain khi biên dịch và chạy theo module graph |
| `exports com.demo.domain.api;` | Cho phép module khác truy cập public types và public members của package theo quy tắc Java thông thường |
| `opens com.demo.domain.internal to com.demo.client;` | Cho phép **deep reflection** vào package đích cho module client, gồm khả năng thử `setAccessible` và `privateLookupIn` theo điều kiện API |
| `open module com.demo.domain` | Mở mọi package trong module cho deep reflection; không đồng nghĩa tự động export chúng để import ở source |
| `--add-opens A/p=B` | Mở bổ sung package `p` của module `A` cho module `B` khi chạy, không sửa source/module-info |

**`exports` không tự cấp private access. `opens` không thay thế `exports` cho việc import public class từ module khác.** Có thể đồng thời export và open cùng package nếu vừa muốn public API vừa muốn deep reflection hợp lệ.

Cùng một **named module**, một caller có thể được phép dùng `setAccessible(true)` cho thành viên non-public của class khác trong module theo các điều kiện của Reflection. Điều đó **không có nghĩa mã nguồn Java thường được quyền gọi trực tiếp private member**; vẫn phải tuân thủ private/package/protected theo compiler.

Với **named module khác**, ngay cả private method của một public class trong package đã `exports` cũng không tự được deep reflection; cần package mở theo điều kiện thích hợp.

Trên **classpath**, ứng dụng thường thuộc unnamed module; các package của unnamed module được mở rộng cho deep reflection theo quy tắc JPMS. Đây là một trường hợp kiểm thử riêng, không phải bằng chứng rằng cùng code sẽ chạy khi chuyển sang named module.

## 3. Ma trận kỳ vọng khi kiểm thử

Ma trận sau giả định caller có quyền đọc module đích, không có cơ chế truy cập đặc quyền bổ sung, và target là một public class với public hoặc private method.

| Nơi gọi / cấu hình package đích | Gọi public member bằng Java thông thường | Deep reflection vào private method từ module khác |
| --- | --- | --- |
| Cùng named module | Có nếu thỏa visibility/package của Java | Thường được phép suppression đối với member trong cùng module |
| Khác module; package `exports`, không `opens` | Có đối với public type và public member | **Không** chỉ vì export |
| Khác module; package `opens ... to client`, không `exports` | Không thể import package đó như public API bình thường | **Có thể** nếu lookup/access phù hợp |
| Khác module; package `exports` và `opens` | Có đối với public API | Có thể deep-reflect nếu thỏa điều kiện |
| Khác module; không `exports`, không `opens` | Không thể truy cập trực tiếp từ source | Deep reflection thường bị từ chối |
| Trên classpath/unnamed module | Tuân theo visibility/package bình thường | Khác named module; cần kiểm thử riêng |

Bảng là **mô hình quyền** chứ không phải cam kết mọi lời gọi sẽ thành công. Ví dụ một receiver null không đúng chỗ, sai method signature, constructor ném exception hoặc hạn chế đối với final field vẫn gây lỗi độc lập với JPMS.

### API nào kiểm tra điều gì?

- `getDeclaredMethod("secret")`: tìm method khai báo trực tiếp, kể cả private, thường có thể trả metadata dù việc gọi bị cấm.
- `method.canAccess(receiver)`: kiểm tra quyền truy cập hiện tại cho member với receiver phù hợp, **không tự thay đổi** flag accessible. Với method static, dùng receiver `null`.
- `method.trySetAccessible()`: thử bật suppression, trả `true` hoặc `false` nếu access không được cho phép theo hợp đồng; có thể ném lỗi khác trong một số tình huống.
- `method.setAccessible(true)`: có thể ném `InaccessibleObjectException` khi không thể bật suppression, thay vì trả boolean.
- `method.invoke(receiver, args...)`: có thể ném `IllegalAccessException` nếu không có quyền, còn exception từ method đích thường được bọc bằng `InvocationTargetException`.
- `MethodHandles.privateLookupIn(target, callerLookup)`: có thể ném `IllegalAccessException` nếu caller không có quyền cần thiết, thiếu readability hoặc package ở module khác không mở phù hợp; có thêm ràng buộc lookup modes.
- `lookup.findVirtual(...)` hoặc `lookup.unreflect(method)`: có kiểm tra quyền với `Lookup` tương ứng; lấy `Method` trước đó không tạo đặc quyền.

Một test tốt ghi rõ **đối tượng, module của caller, module của target, package, mode và API được thử**. Đừng chỉ assert rằng có hoặc không ném exception nhưng bỏ mất nguyên nhân.

## 4. Bài thực hành: hai named module, cùng một chương trình kiểm thử

Ta tạo đúng hai module:

- `com.demo.domain` chứa public API được export và một class nội bộ **không export, không open** ban đầu.
- `com.demo.client` có `requires com.demo.domain` và kiểm tra cả việc gọi qua public API, Reflection từ bên ngoài, cùng một phép thử private Reflection chạy **bên trong** domain.

Cấu trúc thư mục:

```text
src/
├── com.demo.domain/
│   ├── module-info.java
│   └── com/demo/domain/
│       ├── api/Entry.java
│       └── internal/
│           ├── Hidden.java
│           └── Probe.java
└── com.demo.client/
    ├── module-info.java
    └── com/demo/client/Main.java
```

Đây là ví dụ Java 17+ độc lập, không cần thư viện bên ngoài. Các file bên dưới phải giữ đúng path/package.

### 4.1. Module domain chưa mở package nội bộ

Tạo `src/com.demo.domain/module-info.java`:

```java
module com.demo.domain {
    exports com.demo.domain.api;
}
```

Package `api` được export; package `internal` không được export cũng không được open cho client.

Tạo `src/com.demo.domain/com/demo/domain/internal/Hidden.java`:

```java
package com.demo.domain.internal;

public final class Hidden {
    public String publicMethod() {
        return "public-inside";
    }

    private String secret() {
        return "inside";
    }
}
```

`Hidden` là public **trong package không export**. Client không import được class này như public API chỉ vì nó có modifier `public`.

Tạo `src/com.demo.domain/com/demo/domain/internal/Probe.java`:

```java
package com.demo.domain.internal;

import java.lang.reflect.Method;

public final class Probe {
    private Probe() {}

    public static String sameModulePrivateCall()
            throws ReflectiveOperationException {
        Hidden target = new Hidden();
        Method method = Hidden.class.getDeclaredMethod("secret");

        if (!method.trySetAccessible()) {
            throw new AssertionError("Same-module access denied");
        }
        return (String) method.invoke(target);
    }
}
```

`Probe` gọi Reflection từ **cùng module** với `Hidden` (và trong ví dụ còn cùng package). Private method không được gọi trực tiếp trong mã nguồn của `Probe`, nhưng việc thử suppression bằng Reflection có thể được cho phép. Không cần `opens` chỉ để hai class trong cùng module thực hiện đường Reflection này.

Tạo `src/com.demo.domain/com/demo/domain/api/Entry.java`:

```java
package com.demo.domain.api;

import com.demo.domain.internal.Hidden;
import com.demo.domain.internal.Probe;

public final class Entry {
    private Entry() {}

    public static String ping() {
        return "pong";
    }

    public static Object newHidden() {
        return new Hidden();
    }

    public static String probeInside()
            throws ReflectiveOperationException {
        return Probe.sameModulePrivateCall();
    }
}
```

`Entry` là cửa ngõ public thuộc package đã export. Nó có thể dùng `Hidden`/`Probe` của package nội bộ vì cùng module. Client nhận object qua kiểu `Object` mà không import class nội bộ.

### 4.2. Module client và chương trình kiểm thử

Tạo `src/com.demo.client/module-info.java`:

```java
module com.demo.client {
    requires com.demo.domain;
}
```

Tạo `src/com.demo.client/com/demo/client/Main.java`:

```java
package com.demo.client;

import com.demo.domain.api.Entry;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

public final class Main {
    public static void main(String[] args) throws Throwable {
        boolean expectOpen = args.length == 1
                && "open".equals(args[0]);

        System.out.println("same-module private="
                + Entry.probeInside());
        System.out.println("exported public=" + Entry.ping());

        Object target = Entry.newHidden();
        Class<?> targetClass = target.getClass();
        Method method = targetClass.getDeclaredMethod("secret");

        System.out.println("cross canAccess="
                + method.canAccess(target));

        boolean enabled = method.trySetAccessible();
        System.out.println("cross trySetAccessible=" + enabled);

        String reflectionResult;
        try {
            reflectionResult = (String) method.invoke(target);
        } catch (IllegalAccessException denied) {
            reflectionResult = "DENIED";
        }
        System.out.println("cross invoke=" + reflectionResult);

        String lookupResult;
        try {
            MethodHandles.Lookup privateLookup =
                    MethodHandles.privateLookupIn(
                            targetClass, MethodHandles.lookup());
            MethodHandle handle = privateLookup.findVirtual(
                    targetClass,
                    "secret",
                    MethodType.methodType(String.class));
            lookupResult = (String) handle.invoke(target);
        } catch (IllegalAccessException denied) {
            lookupResult = "DENIED";
        }
        System.out.println("privateLookupIn=" + lookupResult);

        boolean actualOpen = enabled
                && "inside".equals(reflectionResult)
                && "inside".equals(lookupResult);

        if (actualOpen != expectOpen) {
            throw new AssertionError(
                    "Expected open=" + expectOpen
                            + ", actual=" + actualOpen);
        }

        System.out.println("PASS " + (expectOpen ? "open" : "closed"));
    }
}
```

**Các điểm quan trọng:**

1. `Entry.probeInside()` thực hiện private Reflection **bên trong domain** và trả chuỗi `inside`. Đây là phép thử cùng module.
2. `Entry.ping()` gọi public API qua `exports`, không cần `opens`.
3. `target.getClass().getDeclaredMethod("secret")` minh họa việc tìm metadata non-public từ client; không suy ra quyền gọi.
4. `canAccess(target)` được gọi **trước** `trySetAccessible`. Vì client không có direct language access vào private method, kết quả là `false` trong cả hai kịch bản.
5. `trySetAccessible` sau đó phụ thuộc package `internal` có được mở cho module client hay không.
6. `privateLookupIn` được thử riêng. Nó không lấy quyền từ việc `method.trySetAccessible()` đã bật; nó dựa trên `Lookup` của caller và cấu hình module.
7. Dòng `PASS` chỉ xuất hiện nếu kết quả mở/đóng đúng như tham số chạy. Nếu quyền ngoài dự kiến, chương trình kết thúc bằng `AssertionError` (khác mã exit thành công).

**Phân biệt lỗi:** Các khối `catch` chỉ bắt `IllegalAccessException` để biểu diễn việc bị từ chối như `DENIED`. Không dùng `catch (Throwable ignored)` vì có thể vô tình nuốt lỗi chương trình không liên quan đến access. `main throws Throwable` chỉ nhằm đơn giản hóa ví dụ MethodHandle của bài thực hành.

## 5. Biên dịch và chạy trường hợp 1: module đóng

Từ thư mục chứa `src`, tạo thư mục output:

```bash
mkdir out
```

Biên dịch cả hai module (lệnh viết một dòng để dùng trên shell phổ biến, kể cả PowerShell):

```bash
javac -d out --module-source-path src src/com.demo.domain/module-info.java src/com.demo.domain/com/demo/domain/api/Entry.java src/com.demo.domain/com/demo/domain/internal/Hidden.java src/com.demo.domain/com/demo/domain/internal/Probe.java src/com.demo.client/module-info.java src/com.demo.client/com/demo/client/Main.java
```

Chạy **không bổ sung quyền mở package**:

```bash
java --module-path out -m com.demo.client/com.demo.client.Main closed
```

Stdout mong đợi:

```text
same-module private=inside
exported public=pong
cross canAccess=false
cross trySetAccessible=false
cross invoke=DENIED
privateLookupIn=DENIED
PASS closed
```

Giải thích: public API thuộc package `api` được export nên gọi được; test private Reflection chạy **bên trong domain** thành công. Nhưng từ client, package `internal` không được open, vì vậy `trySetAccessible` trả `false`; `Method.invoke` không được phép gọi private method; `privateLookupIn` không được cấp private lookup sang package đóng.

Nếu thay `trySetAccessible()` bằng `setAccessible(true)` ở client, tình huống package không mở có thể ném `InaccessibleObjectException`. Đó là một tín hiệu lỗi khác với `IllegalAccessException` của `Method.invoke`/`privateLookupIn`.

## 6. Chạy trường hợp 2: mở package có chọn lọc bằng `--add-opens`

Không sửa source hoặc build. Chạy lại **cùng file class** với một quyền mở bổ sung:

```bash
java --module-path out --add-opens com.demo.domain/com.demo.domain.internal=com.demo.client -m com.demo.client/com.demo.client.Main open
```

Stdout mong đợi:

```text
same-module private=inside
exported public=pong
cross canAccess=false
cross trySetAccessible=true
cross invoke=inside
privateLookupIn=inside
PASS open
```

Tại sao `canAccess` vẫn in `false`? Lời gọi được thực hiện **trước khi bật accessible override** và kiểm tra quyền gọi Java thông thường. `--add-opens` làm deep reflection **có thể được phép khi yêu cầu**, không biến private member thành public trong source.

Tại sao `privateLookupIn` thành công? Client đọc domain qua `requires`; domain đã mở `com.demo.domain.internal` **cho module client** nên lookup có thể được cấp quyền theo hợp đồng `privateLookupIn`.

### 6.1. Mở package bằng descriptor thay cho command line

Cũng có thể thay file `src/com.demo.domain/module-info.java` bằng:

```java
module com.demo.domain {
    exports com.demo.domain.api;
    opens com.demo.domain.internal to com.demo.client;
}
```

Đây là **phiên bản thay thế** của cùng một file, không được tạo hai `module-info.java` cho module domain. Biên dịch lại bằng lệnh ở mục 5, sau đó chạy mà không cần `--add-opens`:

```bash
java --module-path out -m com.demo.client/com.demo.client.Main open
```

Kết quả dự kiến giống kịch bản `open` ở trên. Qualified opens chỉ cấp deep reflection cho module được liệt kê; module thứ ba không tự nhận quyền ấy. Đối với môi trường production, khai báo `opens` có chọn lọc thường rõ ràng hơn việc mở toàn bộ module.

**Lưu ý thực hành:** Sau khi sửa descriptor thành `opens`, muốn kiểm tra lại trường hợp `closed` thật sự phải trở về phiên bản module-info ban đầu và build lại. Nếu không, việc chạy tham số `closed` có thể thất bại ở assertion vì bạn đang thử một module đã mở.

## 7. Ba phép thử bổ sung giúp tránh kết luận sai

### 7.1. `--add-exports` không tương đương `--add-opens`

Với phiên bản domain **chưa open** ở mục 4, thử:

```bash
java --module-path out --add-exports com.demo.domain/com.demo.domain.internal=com.demo.client -m com.demo.client/com.demo.client.Main closed
```

`--add-exports` giúp client có quyền truy cập **public API** của package đó theo quy tắc export, nhưng **không mở private member cho deep reflection**. Vì test `secret()` là private, kết quả kỳ vọng vẫn là kịch bản `closed`. Không dùng `--add-exports` để “sửa” thất bại của `privateLookupIn`.

### 7.2. Metadata có mặt không đồng nghĩa member gọi được

Trong cả hai kịch bản, `getDeclaredMethod("secret")` trả về `Method`. Nó chứng minh metadata có thể được truy xuất, không chứng minh truy cập trực tiếp hợp lệ.

Ngược lại, nếu gọi nhầm tên/signature:

```java
Method missing = targetClass.getDeclaredMethod("unknown");
```

Đoạn trên minh họa lỗi trong ngữ cảnh client, ném `NoSuchMethodException` chứ **không** phải lỗi JPMS. Một bộ test nên tách case “không tìm thấy” khỏi “tìm thấy nhưng bị từ chối”.

### 7.3. `public` trong package không export vẫn bị ràng buộc

`Hidden.publicMethod()` là method `public` của class `public`, nhưng package chứa nó không export. Bản thân hai modifier `public` ấy không cho client quyền compile-time import `Hidden`. Có thể lấy metadata của method qua `target.getClass().getMethod("publicMethod")`, song thao tác `invoke` từ client còn phải tuân thủ quyền của declaring class/package; không thể thay thế `exports` bằng việc chỉ kiểm tra `Modifier.isPublic(...)`.

Nếu package được `opens` mà chưa `exports`, deep reflection sau khi bật override có thể hoạt động, nhưng điều đó vẫn không biến package thành public API để client import trong source.

## 8. Dùng `canAccess`, `trySetAccessible` và `setAccessible` đúng cách

| API | Trạng thái thay đổi? | Kết quả/biểu hiện quan trọng |
| --- | --- | --- |
| `canAccess(receiver)` | Không | `boolean` về khả năng access với trạng thái hiện tại |
| `trySetAccessible()` | Có thể bật accessible override nếu được phép | `true` nếu bật được; `false` khi không thể suppress access |
| `setAccessible(true)` | Cố bật accessible override | Có thể ném `InaccessibleObjectException` nếu JPMS không cho |
| `setAccessible(false)` | Tắt override trên đối tượng metadata được thao tác | Không đổi module graph |
| `Method.invoke(...)` | Thực thi member khi có quyền | `IllegalAccessException` nếu bị chặn; code đích có thể bọc lỗi bằng `InvocationTargetException` |

**Receiver hợp lệ rất quan trọng:**

- `Method.canAccess(instance)`: dùng instance phù hợp với instance method.
- `Method.canAccess(null)`: dùng `null` cho static method.
- `Field.canAccess(instance)`: field instance cần receiver đúng kiểu; field static dùng `null`.
- `Constructor.canAccess(null)`: constructor dùng receiver `null`.

Receiver sai có thể khiến `canAccess` ném `IllegalArgumentException`. Không coi mọi exception trong phép kiểm tra quyền là bằng chứng JPMS từ chối.

Các cách thay đổi accessible override chỉ nên dùng khi hợp đồng framework yêu cầu; tránh gọi `setAccessible(true/false)` liên tục trên cùng một `AccessibleObject` được cache và chia sẻ giữa thread. Việc này gây khó lập luận về quyền và trạng thái truy cập. Một test có thể dùng metadata riêng và chủ động ghi lại ai đã bật override.

## 9. Kiểm thử `MethodHandles.Lookup` và `privateLookupIn`

`MethodHandles.lookup()` tạo lookup với các quyền theo lớp caller. `publicLookup()` giới hạn hơn. `privateLookupIn(targetClass, callerLookup)` không hoạt động chỉ nhờ có `Class<?>` của target: phải đáp ứng **lookup modes**, khả năng đọc module và quyền mở package.

Hai câu hỏi kiểm thử nên tách rời:

1. **Tìm được method bằng Reflection không?** `getDeclaredMethod` có thể trả metadata.
2. **Có quyền tạo handle với private lookup không?** `privateLookupIn` và `findVirtual` cần permission riêng.

Trong ví dụ, dù `method.trySetAccessible()` thành công ở kịch bản open, `privateLookupIn` **không** được cấp quyền do đối tượng `Method` đó đổi cờ; nó xét quyền của `Lookup` và module graph. Nếu dùng `Lookup` không có đầy đủ quyền cần thiết, open package vẫn chưa bảo đảm thành công.

Không nên cache một `Lookup` có quyền private rồi đưa tùy ý qua plugin hoặc người dùng không đáng tin. Module boundaries là một phần của chính sách truy cập code; quyền truy cập nghiệp vụ và xác thực người dùng là lớp kiểm soát bổ sung.

Ngoài `findVirtual`, cách unreflect như `lookup.unreflect(method)` cũng phải chịu kiểm tra quyền của lookup. Đừng giả định method handle “bỏ qua” encapsulation.

## 10. Kiểm thử field, constructor và các kiểu lỗi

Cùng tư duy có thể mở rộng từ method tới field/constructor:

| Member | Metadata lookup | Thử quyền và thực thi |
| --- | --- | --- |
| Private instance field | `getDeclaredField("value")` | `canAccess(instance)`, `trySetAccessible()`, `get(instance)`/`set(instance, value)` |
| Private static field | `getDeclaredField("COUNT")` | `canAccess(null)`, `trySetAccessible()`, `get(null)` |
| Private constructor | `getDeclaredConstructor(types...)` | `canAccess(null)`, `trySetAccessible()`, `newInstance(args...)` |
| Private instance method | `getDeclaredMethod(name, types...)` | `canAccess(instance)`, `trySetAccessible()`, `invoke(instance, args...)` |

**Không ghi thử vào `final` field chỉ để chứng minh quyền.** Một field final có hạn chế ghi riêng, và thành công khi đọc hoặc suppress access không đồng nghĩa ghi hợp lệ. `VarHandle` đối với final field có thể chỉ hỗ trợ các access mode đọc. Với constructor, có thể có side effect từ việc tạo object; dùng class fixture đơn giản không tương tác mạng/file/hệ thống.

Các exception cần phân loại:

- `ClassNotFoundException`: không tìm được type theo tên/loader.
- `NoSuchMethodException`, `NoSuchFieldException`: metadata không có member theo tiêu chí tìm kiếm.
- `IllegalAccessException`: thao tác Reflection hoặc Lookup không có quyền tương ứng.
- `InaccessibleObjectException` (unchecked): không thể bật suppression qua `setAccessible(true)` theo chính sách module/access.
- `InvocationTargetException`: method/constructor đích được gọi qua Reflection ném exception; đọc `getCause()` để xem lỗi thực.
- `IllegalArgumentException`: đối số, receiver hoặc kiểu truyền cho Reflection không phù hợp.
- `WrongMethodTypeException`: kiểu lời gọi `MethodHandle.invokeExact` không đúng với `MethodType`.
- `ExceptionInInitializerError` và lỗi nạp/liên kết class: độc lập với quyền truy cập member; cần điều tra riêng.

Một bộ test tốt không được bắt tất cả lỗi rồi “PASS blocked”: như vậy có thể biến lỗi lập trình thành kết quả giả đúng.

## 11. Đảm bảo test đúng module, không vô tình rơi về classpath

Các lỗi cấu hình chạy test thường gặp:

- Chạy `java -cp ...` thay vì `--module-path ... -m ...`, khiến class nằm trong unnamed module và hành vi deep reflection khác dự kiến.
- Thiếu `requires` hoặc không resolve module đích, dẫn tới lỗi module graph, nhầm là lỗi private access.
- Thêm `--add-opens` trong cấu hình IDE/JUnit mà quên rằng production không có, khiến test xanh giả.
- Build tool đưa test code vào **cùng module** với code đích, trong khi mục tiêu là mô phỏng caller ở **module khác**.
- Phép thử dùng một public class trong package được export mà quên xác minh package private/closed thật sự, khiến không bao giờ kiểm tra strong encapsulation.
- Dùng `setAccessible(true)` hoặc `trySetAccessible` từ code trong target module rồi kết luận module bên ngoài cũng có quyền.
- Dùng version JDK, launcher flags và plugin/module path khác nhau giữa CI và môi trường chạy thật.

Khi cần chẩn đoán, có thể in ra module thực tế của caller và target bằng đoạn minh họa sau:

```java
System.out.println(Main.class.getModule().getName());
System.out.println(target.getClass().getModule().getName());
System.out.println(target.getClass().getModule().isOpen(
        target.getClass().getPackageName(),
        Main.class.getModule()));
```

Đặt snippet này trong `Main.main` của ví dụ. Với cấu hình gốc, hai tên module là `com.demo.client` và `com.demo.domain`; dòng cuối phản ánh việc package nội bộ có mở cho client tại runtime hay không. Với `--add-opens`, một kiểm tra `Module.isOpen(package, clientModule)` có thể giúp xác nhận module đã được mở đúng đối tượng.

Để test JUnit tích hợp có ý nghĩa, hãy dùng **hai fixture chạy ở module boundaries thực** hoặc một harness fork process với các launcher flags kiểm soát được. Không nhất thiết phải nhúng mọi trường hợp trong một JUnit test chạy trên classpath.

## 12. Bộ tiêu chí PASS/FAIL và negative tests

Một bộ kiểm thử tối thiểu nên bao gồm cả case được phép và bị chặn:

| Ca kiểm thử | Kỳ vọng | Mục tiêu |
| --- | --- | --- |
| Same-module private reflection | Thành công trong fixture `Probe` | Xác nhận quyền suppression nội bộ |
| Cross-module public exported API | Thành công với `Entry.ping()` | Xác nhận `requires` + `exports` |
| Cross-module private trong package đóng | `trySetAccessible=false`; gọi không override bị từ chối | Xác nhận strong encapsulation |
| Cross-module `privateLookupIn` khi package đóng | Bị từ chối | Xác nhận lookup không tự vượt JPMS |
| Cross-module private với `--add-opens`/qualified `opens` | Deep reflection thành công | Xác nhận mở đúng module đích |
| Cross-module chỉ `--add-exports` | Private deep reflection vẫn bị từ chối | Phân biệt exports/opens |
| Sai method signature | `NoSuchMethodException` | Phân biệt lookup error và access error |
| Receiver sai/null khi member instance | Exception phù hợp | Xác nhận không nhầm input error với quyền |
| Constructor/field private | Có case allowed/denied tương ứng | Mở rộng rule sang member khác |
| Chạy CI thật qua module path | PASS cả positive và negative tests | Tránh false positive classpath |

Nên để test in/ghi log **cấu hình module**, actual exception type và trạng thái `trySetAccessible`. Với các phép thử negative, assert **đúng kiểu lỗi hoặc giá trị boolean**, không chỉ assert “bất kỳ exception nào cũng được”.

Nếu test thay đổi `module-info.java`, hãy biên dịch lại sạch và chạy lại từ trạng thái module graph đã xác định. Nếu test chạy bằng `--add-opens`, ghi rõ launcher flag để CI và môi trường khác có thể lặp lại chính xác.

## 13. Checklist trước khi dùng Reflection xuyên module trong sản phẩm

1. Xác định chính xác **caller module**, **target module**, package đích và member cần truy cập.
2. Hỏi liệu public API có đủ không. Nếu đủ, dùng `exports` và API public thay vì deep reflection.
3. Nếu cần private reflection, chỉ mở đúng package cho module đáng tin; ưu tiên qualified `opens` thay vì mở toàn cục.
4. Tách kiểm tra metadata có tồn tại khỏi kiểm tra quyền thực thi; không dùng `isPublic` làm kết luận duy nhất.
5. Kiểm thử `canAccess`, `trySetAccessible`, `Method.invoke` và `privateLookupIn` trong **đúng** bối cảnh caller.
6. Không lạm dụng `setAccessible` hoặc launcher `--add-opens` để che đi một thiết kế API sai.
7. Không tùy tiện gọi method/constructor chỉ để kiểm tra quyền nếu chúng có side effect; dùng fixture an toàn.
8. Đưa cả case thành công và bị từ chối vào CI, kiểm tra module path/JDK/VM flags, và ghi nhận trạng thái thực.
9. Khi dùng cached `Method`/`Field`/`Lookup`, phải đánh giá thêm quyền và điều kiện thread-safety; quyền/module không tự biến thành chính sách bảo mật của người dùng.

### Tự kiểm tra

1. Vì sao `getDeclaredMethod("secret")` vẫn có thể thành công trong module client dù `invoke` bị từ chối?
2. `exports` và `opens` khác nhau thế nào? Một package được export có cho phép đọc private field từ module khác không?
3. Vì sao `canAccess` được gọi **trước** `trySetAccessible` trong bài thực hành?
4. Vì sao kịch bản open vẫn in `cross canAccess=false` nhưng sau đó `Method.invoke` lại thành công?
5. Khi nào `privateLookupIn` không thành công dù đã lấy được `Method` bằng Reflection?
6. Nếu chạy thử trên classpath rồi chuyển sang module path, vì sao quyền deep reflection có thể thay đổi?
7. Vì sao test âm tính nên kiểm tra `IllegalAccessException` hoặc `false` thay vì bắt mọi `Throwable`?
8. Làm sao chứng minh quyền được mở cho module client mà không mở cho mọi module khác?

**Ghi nhớ:** Kiểm thử quyền Reflection phải chạy trong **module graph và nơi gọi thực tế**. `public` và `exports` giải quyết public API; `opens` và `privateLookupIn` liên quan tới deep reflection. Việc **tìm metadata**, **công bố quyền truy cập** và **thực thi member** là ba bước khác nhau; muốn test đáng tin, cần kiểm tra cả trường hợp được phép lẫn bị từ chối.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-testing-009">Kiểm thử metadata có và không có -parameters</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử metadata có và không có `-parameters` trong Java Reflection

## 1. Vì sao cần kiểm thử hai kiểu build?

Khi biên dịch Java, tên tham số trong mã nguồn như `firstName` và `repeat` **không bắt buộc** phải tồn tại trong class file ở dạng mà Java Reflection đọc được. Tùy chọn `javac -parameters` yêu cầu compiler lưu tên và modifiers của tham số cho **method và constructor** vào thuộc tính `MethodParameters` trong bytecode. Nếu bỏ tùy chọn này, chương trình vẫn gọi method bình thường và reflection vẫn biết **số lượng, thứ tự, kiểu tham số**; thứ thường không còn là **tên tham số gốc**.

Điều đó có ảnh hưởng tới các framework dùng reflection để map query/path/body parameters, bind constructor arguments, đọc tên tham số trong validation, dependency injection, CLI command mapping hoặc sinh tài liệu API. Một test chỉ chạy với build có `-parameters` có thể che giấu lỗi cấu hình build ở môi trường khác.

**Mục tiêu bài học:** tạo hai artifact từ **cùng source** (một có `-parameters`, một không), đọc bằng `Executable.getParameters()`, xác nhận cả `getName()` và `isNamePresent()`, dùng `javap -v` đối chiếu class file và thiết kế test bắt lỗi phụ thuộc ngầm vào tên tham số.

## 2. Metadata nào được giữ, metadata nào thay đổi?

Xét phương thức Java:

~~~java
// Trích đoạn từ class Sample; không phải chương trình độc lập.
public String greet(String firstName, int repeat) {
    return firstName.repeat(repeat);
}
~~~

Với JDK hỗ trợ tham số chuẩn Java 8+:

| Truy vấn reflection | Có `-parameters` | Không có `-parameters` (class javac thông thường) |
| --- | --- | --- |
| `method.getParameterCount()` | `2` | `2` |
| `method.getParameterTypes()` | `String.class`, `int.class` | Y hệt |
| `method.getParameters()[0].getName()` | `"firstName"` | Tên thay thế thường là `"arg0"` |
| `method.getParameters()[1].getName()` | `"repeat"` | Tên thay thế thường là `"arg1"` |
| `Parameter.isNamePresent()` | `true` | `false` |
| `method.invoke(target, "Hi", 2)` | Chạy theo kiểu và thứ tự đối số | Hoạt động tương tự |
| Annotation/thông tin kiểu có nguồn metadata riêng | Không do `-parameters` quyết định | Không do `-parameters` quyết định |

Điểm quan trọng: **`getName()` luôn có thể trả một String**, kể cả khi tên nguồn không được ghi vào bytecode. Vì vậy:

- `getName() != null` **không chứng minh** có tên tham số thật.
- `getName().equals("arg0")` **không phải** phép xác định metadata vắng mặt: `arg0` cũng có thể là tên do lập trình viên đặt trong source.
- Phép kiểm tra đúng mục đích là `parameter.isNamePresent()` trước khi phụ thuộc vào `parameter.getName()` để bind dữ liệu.

`Executable` là lớp cha chung của `Method` và `Constructor`; cả hai hỗ trợ `getParameters()`. Tùy chọn `-parameters` không chỉ liên quan đến method.

## 3. Thí nghiệm Java 17+: biên dịch hai biến thể từ một source

### 3.1. Cấu trúc thư mục

Tạo một thư mục thử nghiệm mới với hai file mã nguồn và ba thư mục output:

~~~text
parameter-lab/
├── src/
│   ├── demo/
│   │   └── Sample.java
│   └── probe/
│       └── ParameterMetadataProbe.java
├── without/
├── with/
└── probe/
~~~

Hai thư mục `without` và `with` chứa **hai bản khác nhau của cùng class `demo.Sample`**. Thư mục `probe` chứa chương trình đọc metadata. Không cần đặt các file `.class` lên cùng classpath cùng lúc: làm vậy khiến classpath order quyết định bản được load và làm kết quả test khó tin.

**File `src/demo/Sample.java`:**

~~~java
package demo;

public final class Sample {
    private final String label;

    public Sample(String label) {
        this.label = label;
    }

    public String greet(String firstName, int repeat) {
        return firstName.repeat(repeat) + label;
    }
}
~~~

Ở đây có **constructor** với một tham số `label` và **instance method** `greet` với hai tham số `firstName`, `repeat`. Class sử dụng `String.repeat(int)` có từ Java 11; biên dịch target Java 17 sẽ hợp lệ.

**File `src/probe/ParameterMetadataProbe.java`:**

~~~java
package probe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public final class ParameterMetadataProbe {
    static void inspect(String tag, Executable executable) {
        Parameter[] parameters = executable.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter p = parameters[i];
            System.out.printf("%s[%d]: name=%s, present=%s, type=%s%n",
                    tag, i, p.getName(), p.isNamePresent(),
                    p.getType().getSimpleName());
        }
    }

    public static void main(String[] args) throws Exception {
        Class<?> sample = Class.forName("demo.Sample");

        Constructor<?> ctor =
                sample.getDeclaredConstructor(String.class);

        Method greet = sample.getDeclaredMethod(
                "greet", String.class, int.class);

        inspect("constructor", ctor);
        inspect("greet", greet);
    }
}
~~~

Chương trình `ParameterMetadataProbe` tìm **đúng một constructor và một method**, rồi in tên, trạng thái metadata tên tham số và kiểu tham số. Cả hai file Java trên là chương trình/lớp biên dịch được, không dựa vào thư viện ngoài JDK.

### 3.2. Biên dịch với và không với `-parameters`

Đứng tại `parameter-lab/`, tạo trước `without`, `with`, `probe` nếu chúng chưa tồn tại. Trên Linux/macOS (bash):

~~~bash
mkdir -p without with probe

# Build A: có debug local variable info (-g) nhưng KHÔNG có -parameters.
javac --release 17 -g -d without src/demo/Sample.java

# Build B: có -parameters nhưng KHÔNG có debug info (-g:none).
javac --release 17 -parameters -g:none -d with src/demo/Sample.java

# Probe được biên dịch độc lập, không cần import demo.Sample.
javac --release 17 -d probe src/probe/ParameterMetadataProbe.java

echo '=== WITHOUT -parameters (with -g) ==='
java -cp probe:without probe.ParameterMetadataProbe

echo '=== WITH -parameters (without -g) ==='
java -cp probe:with probe.ParameterMetadataProbe
~~~

**Trên Windows PowerShell:** tạo thư mục bằng `New-Item -ItemType Directory -Force without,with,probe` (hoặc tạo sẵn trong Explorer); dấu phân tách classpath của Java trên Windows là **dấu chấm phẩy**, nên hai lệnh chạy tương ứng:

~~~powershell
java -cp 'probe;without' probe.ParameterMetadataProbe
java -cp 'probe;with' probe.ParameterMetadataProbe
~~~

Các lệnh `javac --release 17 ...` giống nhau trên Windows và Unix. Không biên dịch `Sample.java` đè vào cùng output directory rồi kiểm tra vì rất dễ chạy nhầm biến thể. Mỗi lệnh `java` tạo một JVM mới, tránh việc `demo.Sample` đã được class loader tải từ biến thể trước.

### 3.3. Output chính xác

Khi chạy hai lệnh `java` như trên:

~~~text
=== WITHOUT -parameters (with -g) ===
constructor[0]: name=arg0, present=false, type=String
greet[0]: name=arg0, present=false, type=String
greet[1]: name=arg1, present=false, type=int
=== WITH -parameters (without -g) ===
constructor[0]: name=label, present=true, type=String
greet[0]: name=firstName, present=true, type=String
greet[1]: name=repeat, present=true, type=int
~~~

Output sử dụng các dòng `echo` để ngăn cách hai run. Nếu chỉ chạy `java`, mỗi run in ba dòng của constructor/method mà không in tiêu đề phân cách.

**Giải thích:** `getDeclaredConstructor(String.class)` và `getDeclaredMethod("greet", String.class, int.class)` tìm được thành viên trong cả hai build. Khác biệt xuất hiện khi `getParameters()` cung cấp `Parameter` và `getName()`/ `isNamePresent()` kiểm tra thông tin tên. Cả hai build giữ nguyên thứ tự và kiểu `String, int`. Đó chính là ranh giới: **metadata về tên khác metadata về signature và invocation**.

## 4. `-parameters` khác `-g` như thế nào?

Hai cờ phục vụ mục đích khác nhau:

| Cờ `javac` | Class file chủ yếu nhận được | Reflection chuẩn quan sát thế nào? |
| --- | --- | --- |
| `-parameters` | Thuộc tính **`MethodParameters`** cho thông tin tham số method/constructor | `Parameter.getName()`, `isNamePresent()`, và một số metadata modifiers |
| `-g` | Thông tin debug gồm line number, source file, local variables | **Không tự tạo** tên thật cho `Parameter.getName()` |
| `-g:none` | Không tạo debug information thông thường | **Không xóa `MethodParameters`** nếu build có `-parameters` |
| Không chỉ định `-parameters` | `javac` thông thường không lưu tên tham số nguồn qua `MethodParameters` | Tên giả định `arg0`, `arg1`, ... và `isNamePresent() == false` trong ví dụ |

Thông tin debug `LocalVariableTable`, khi có, nằm trong thuộc tính của `Code` và phục vụ công cụ debug/bytecode. Nó **không phải** cùng thuộc tính với `MethodParameters`; core reflection **không dùng LocalVariableTable như đường fallback công khai** để khôi phục tên tham số cho `Parameter.getName()`. Một thư viện ngoài có thể chủ động đọc debug bytecode để khôi phục thông tin trong vài trường hợp, nhưng không được đồng nhất hành vi đó với core Java Reflection.

**Thí nghiệm cố ý kiểm soát hai biến:**

- Build **without**: `-g` có debug info, **không có `-parameters`** → `isNamePresent() = false`.
- Build **with**: `-parameters -g:none` **không có debug info**, nhưng có `MethodParameters` → `isNamePresent() = true`.

Như vậy, kết quả kiểm thử không thể bị giải thích nhầm rằng tên tham số đến từ cờ debug.

### 4.1. Kiểm tra trực tiếp bytecode với javap

Chạy:

~~~bash
javap -v -p -classpath without demo.Sample
javap -v -p -classpath with demo.Sample
~~~

Trong output của bản **with**, tìm thuộc tính dạng:

~~~text
MethodParameters:
  Name                           Flags
  firstName
  repeat
~~~

Đây là **trích đoạn minh họa từ phần method greet** (cách căn cột của `javap` có thể khác theo JDK), không phải toàn bộ output của chương trình. Constructor cũng có một `MethodParameters` chứa `label`.

Bản **without** có thể có `LocalVariableTable` vì được build với `-g`, nhưng **không có khối `MethodParameters` cho các method tương ứng**. Với bản **with**, có `MethodParameters` dù biên dịch `-g:none`. Có thể dùng `javap -l` để xem line/local-variable debug info riêng.

**Lưu ý:** `javap` hiển thị cả constant pool và nhiều thuộc tính; đừng chỉ search một chuỗi `firstName` trong toàn output rồi kết luận tên parameter đã sẵn cho reflection. Hãy kiểm tra **đúng thuộc tính `MethodParameters`** của method/constructor tương ứng.

## 5. Viết test tự động để phát hiện build thiếu metadata

Thay vì chỉ nhìn stdout, hãy thêm assert vào test chuyên biệt cho **artifact thật sự dùng khi deploy**. Ví dụ utility JUnit không được dùng ở đây để tránh phải thêm dependency; sau đây là một class Java 17 độc lập có `main` và assert bằng `AssertionError`:

**File `src/probe/ParameterMetadataAssertions.java`:**

~~~java
package probe;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

public final class ParameterMetadataAssertions {
    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("demo.Sample");
        Method method =
                type.getDeclaredMethod("greet", String.class, int.class);

        Parameter[] params = method.getParameters();

        if (params.length != 2) {
            throw new AssertionError("Unexpected parameter count");
        }

        String[] sourceNames = {"firstName", "repeat"};

        for (int i = 0; i < params.length; i++) {
            if (!params[i].isNamePresent()) {
                throw new AssertionError(
                        "Missing -parameters at index " + i);
            }
            if (!sourceNames[i].equals(params[i].getName())) {
                throw new AssertionError(
                        "Unexpected parameter name at index " + i);
            }
        }

        System.out.println("parameter metadata present: "
                + Arrays.toString(sourceNames));
    }
}
~~~

Biên dịch class kiểm tra này vào `probe` (vẫn cùng thư mục project):

~~~bash
javac --release 17 -d probe src/probe/ParameterMetadataAssertions.java
java -cp probe:with probe.ParameterMetadataAssertions
~~~

Lệnh cuối dự kiến in:

~~~text
parameter metadata present: [firstName, repeat]
~~~

Nếu thay `with` bằng `without`, chương trình phải **thất bại** bằng `AssertionError` với thông báo chứa `Missing -parameters at index 0`. Không nên ghi cả stack trace vào golden output vì số dòng/source line thay đổi khi chỉnh mã.

Với dự án thật, hãy kiểm tra **JAR hoặc class output do pipeline CI/deploy tạo**, không chỉ một bản bạn biên dịch thủ công. Test có thể chạy ở CI để chặn lỗi cờ biên dịch bị mất khi cập nhật Maven/Gradle/IDE.

### 5.1. Tại sao cần kiểm tra cả tên và `isNamePresent()`?

Test chỉ so `getName()` với `"firstName"` có thể bắt lỗi trong ví dụ này, nhưng không phân biệt tốt các trường hợp tên `arg0` là tên nguồn thực tế. Test chỉ so `isNamePresent()` cũng chưa bảo đảm tên mong muốn được giữ sau refactor. Vì vậy:

1. Nếu yêu cầu “có tên nguồn”, assert `isNamePresent()`.
2. Nếu contract binding bắt buộc tên cụ thể, assert thêm `getName()` có giá trị đã định.
3. Nếu thực tế chỉ cần signature/annotation định danh rõ, **không nên** đặt thêm dependency không cần thiết vào tên tham số nguồn.

### 5.2. Ma trận test thiết thực

| Ca kiểm thử | Điều cần xác minh | Ý nghĩa |
| --- | --- | --- |
| Build thiếu `-parameters` | `isNamePresent() == false` trong class javac thông thường | Hiểu hành vi degrade/fail rõ ràng |
| Build có `-parameters` | `true` và tên như source | Pipeline lưu đúng metadata |
| `-g` nhưng thiếu `-parameters` | Core reflection vẫn không đọc được tên nguồn | Không nhầm debug info với MethodParameters |
| `-parameters -g:none` | Tên vẫn đọc được | Độc lập với debug info |
| Constructor | `Constructor.getParameters()` tương tự method | Framework constructor injection/binding hoạt động |
| Method overload | Lookup đúng bằng cả tên + danh sách `Class<?>[]` | Không kiểm nhầm overload |
| Artifact đóng gói cuối | Kiểm tra class trong JAR/container thực | Không bị lệch giữa IDE, test và production |
| Class do tool sinh | Kiểm tra `isImplicit()`, `isSynthetic()`, names | Không suy luận tên tham số nguồn cho tham số ẩn/sinh |

## 6. Cấu hình project và nguồn phát sinh khác biệt

### 6.1. Maven

Với dự án Maven dùng `maven-compiler-plugin`, có thể cấu hình thuộc tính compiler parameters:

~~~xml
<!-- Trích đoạn trong pom.xml, không phải một pom.xml độc lập. -->
<properties>
    <maven.compiler.parameters>true</maven.compiler.parameters>
</properties>
~~~

Hoặc cấu hình plugin cụ thể:

~~~xml
<!-- Trích đoạn cấu hình bên trong <plugins>, không phải pom.xml độc lập. -->
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <parameters>true</parameters>
  </configuration>
</plugin>
~~~

Phiên bản plugin/JDK và các profile build cần được đồng bộ trong dự án. Sau khi thay cờ, thực hiện **clean compilation** và kiểm thử artifact thực, vì file `.class` cũ có thể còn trong thư mục output.

### 6.2. Gradle

Với Gradle Java plugin, cấu hình tasks kiểu `JavaCompile` trong **Groovy DSL**:

~~~groovy
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs += ['-parameters']
}
~~~

Đây là một đoạn cấu hình trong `build.gradle`, không phải file Java chạy được. Trong multi-module build, kiểm tra module thực sự tạo các class cần reflection, kể cả module chứa DTO hoặc constructor được framework sử dụng. Với build Kotlin/Scala hoặc code generator, cờ của `javac` không đủ để suy ra metadata của các class do compiler khác tạo; kiểm tra **class file cuối** mới là bằng chứng.

### 6.3. IDE, annotation processor, generator và tối ưu bytecode

- Compile trong IDE có thể dùng cấu hình khác với CI, kể cả khi cùng source.
- Annotation processor/code generator tạo class riêng; cách compiler/generator ghi `MethodParameters` cần được xác minh riêng.
- Obfuscator/shrinker/bytecode transformer có thể thay hoặc loại bỏ thông tin tên; thêm `-parameters` lúc javac chưa đủ nếu pipeline sau đó biến đổi class file.
- Constructor của **inner class, enum, record** và method **synthetic/bridge** có thể có tham số compiler tạo ra hoặc quy tắc metadata riêng. Không áp dụng máy móc kết luận `arg0`/tên nguồn từ fixture đơn giản cho mọi thành viên; kiểm tra `Parameter.isImplicit()`, `isSynthetic()`, `getModifiers()`, và method `isSynthetic()`/`isBridge()` khi có liên quan.

## 7. Các giới hạn của tên tham số trong runtime

### 7.1. `-parameters` không bảo đảm API binding luôn đúng

Tên tham số có thể bị đổi khi refactor source trong khi signature JVM vẫn giống hệt. Nếu ứng dụng dựa vào tên `firstName` để bind request field, việc đổi thành `name` mà không cập nhật contract có thể làm hỏng API ở runtime dù Java compile thành công.

Một số framework hỗ trợ annotation ghi tên tường minh (ví dụ annotation của framework định nghĩa tên path/query field). Khi hệ thống yêu cầu API contract ổn định, **tên explicit đã được review** thường bền hơn suy đoán tên nguồn. Nhưng đừng tự giả định framework luôn bỏ qua `-parameters` khi có annotation: đọc contract của phiên bản framework đang dùng.

### 7.2. Method có annotation và generics

- `Method.getParameterAnnotations()` và `Parameter.getAnnotations()` dựa trên metadata annotation, **không phải chỉ dựa vào `MethodParameters`**.
- `Executable.getGenericParameterTypes()` dựa vào generic Signature metadata (nếu có) và có thể bị chi phối bởi type erasure và quá trình phân giải Type riêng.
- `getParameterTypes()` đọc kiểu tham số runtime từ method descriptor, nên vẫn biết `String, int` dù tên nguồn không hiện diện.
- Tên biến cục bộ bên trong thân method không được bổ sung bởi `-parameters`: nó không phải tùy chọn “giữ tất cả tên biến” cho reflection.

### 7.3. Trường hợp dữ liệu metadata không hợp lệ

`Executable.getParameters()` có thể ném `java.lang.reflect.MalformedParametersException` khi thuộc tính `MethodParameters` trong class file không hợp lệ. Trường hợp này khác với **không có** tên tham số: với class javac thông thường không có `-parameters`, reflection tạo tên giả định và đặt `isNamePresent() = false`, chứ không coi việc thiếu thuộc tính là bytecode hỏng.

Trên hệ thống có plugin hoặc class do công cụ ngoài sinh ra, phân biệt:

1. **Không có MethodParameters:** thông thường trả tên giả định; nghiệp vụ cần tên thật phải từ chối hoặc có fallback tường minh.
2. **Có MethodParameters hợp lệ:** kiểm tra tên/thứ tự/kind theo yêu cầu.
3. **MethodParameters hỏng:** xử lý như lỗi artifact/build pipeline; không cố sửa bằng việc đoán tên từ `arg0`.

## 8. Checklist kiểm thử và lỗi thường gặp

**Checklist cho test hoặc CI:**

1. Lấy **cùng một source**; build ra hai thư mục/JAR hoàn toàn riêng.
2. Tạo build A có `-g` nhưng **không có `-parameters`**, build B có `-parameters -g:none`.
3. Không đưa cả hai bản của **cùng fully-qualified class name** vào một classpath ở cùng JVM; dùng hai lần chạy hoặc class loader cách ly có kiểm soát.
4. Đọc ít nhất một method và một constructor với `getParameters()`.
5. Assert **`isNamePresent()` và `getName()`**; vẫn kiểm tra `getParameterTypes()` và `getParameterCount()` để thấy signature không bị mất.
6. Xem `javap -v -p`: so sánh đúng `MethodParameters` với debug `LocalVariableTable`.
7. Trên CI, test artifact cuối cùng với flags/plugin và JDK version dùng ở deploy; sau khi thay flags thì clean build.
8. Với framework binding, test cả request theo tên contract và case thiếu metadata, thay vì chỉ kiểm tra một method mẫu.
9. Tránh dựa vào tên fake `arg0` như API contract; đối với record/inner/synthetic constructors cần kiểm tra hành vi riêng.
10. Khi thay đổi tên tham số, test hồi quy contract dữ liệu và documentation, không chỉ test compile.

**Tự kiểm tra:**

- Vì sao `-g` không đủ để `Parameter.isNamePresent()` trả true?
- Vì sao `Parameter.getName()` có kết quả `arg0` không chứng minh tên này có trong source?
- Tại sao hai class `demo.Sample` giống tên nhưng khác flag biên dịch không nên nằm chung một classpath để chạy test?
- Điều gì khác nhau giữa `LocalVariableTable` và `MethodParameters`?
- Vì sao một framework bind theo tên có thể hỏng sau khi đổi tên tham số mà chữ ký method vẫn không đổi?
- Nếu thấy `MalformedParametersException`, cần điều tra điều gì thay vì chỉ thêm `-parameters`?

### Tài liệu tham khảo

- Oracle JDK 21 — `javac` options, `-parameters`: https://docs.oracle.com/en/java/javase/21/docs/specs/man/javac.html
- Oracle Java SE 21 — `Parameter`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Parameter.html
- Oracle Java SE 21 — `Executable`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/Executable.html
- Oracle Java SE 21 — `MalformedParametersException`: https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/reflect/MalformedParametersException.html
- JVM Specification Java SE 21 — MethodParameters: https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.7.24

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-testing-010">Kiểm thử hai loader định nghĩa lớp cùng tên</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử hai ClassLoader định nghĩa lớp cùng tên trong Java

Trong Java, **hai lớp có cùng tên đầy đủ chưa chắc là cùng một kiểu tại runtime**. Đối với một class bình thường, định danh kiểu gồm **binary name và class loader đã *định nghĩa* class đó** (*defining class loader*). Vì vậy, nếu hai class loader khác nhau tự gọi `defineClass` để định nghĩa cùng một tên như `sample.Plugin` từ cùng nội dung bytecode, JVM vẫn tạo ra **hai kiểu khác nhau**.

Bài học này dùng một bài kiểm thử Java 17+ có thể chạy độc lập để kiểm chứng:

- Cùng `Class.getName()` nhưng **`Class<?>` không bằng nhau**.
- Hai kiểu không thể ép kiểu hoặc gán cho nhau chỉ dựa vào tên giống nhau.
- Mỗi loader chỉ nên định nghĩa một class với một tên một lần; một loader khác có thể định nghĩa class trùng tên trong không gian riêng của nó.
- Hai plugin độc lập vẫn có thể cộng tác qua **interface chung được nạp bởi loader cha**, nếu cấu trúc class loader được thiết kế đúng.

Đây là một kỹ năng quan trọng khi kiểm thử plugin, ứng dụng server, thư viện hot reload, hệ thống chạy script và môi trường có nhiều class loader.

## 1. Quy tắc định danh lớp tại runtime

Nhìn hai đoạn thông tin này:

```text
binary name   = sample.Plugin
loader A      = PluginLoader@...
loader B      = PluginLoader@...
```

Dấu `@...` ở đây là ký hiệu minh họa cho hai instance class loader, không phải output xác định của JVM.

Nếu loader A định nghĩa `sample.Plugin` và loader B cũng **tự định nghĩa** `sample.Plugin` thì:

```text
(sample.Plugin, loader A)  !=  (sample.Plugin, loader B)
```

Điều này giải thích vì sao:

```java
Class<?> a = Class.forName("sample.Plugin", false, loaderA);
Class<?> b = Class.forName("sample.Plugin", false, loaderB);

System.out.println(a.getName().equals(b.getName())); // true
System.out.println(a == b);                         // false, nếu A/B tự define
System.out.println(a.isAssignableFrom(b));          // false, nếu không có quan hệ subtype
```

Đây là **đoạn minh họa**: nó giả định `loaderA` và `loaderB` đã được tạo và thực sự định nghĩa class đích độc lập. Nếu cả hai loader chỉ **ủy quyền cho cùng một loader cha**, hai lệnh `Class.forName` có thể trả đúng **một** `Class<?>`.

### Defining loader khác initiating loader

- **Defining loader** là loader thực sự định nghĩa class bằng cơ chế như `defineClass`. Phần định danh runtime phụ thuộc loader này.
- **Initiating loader** là loader mà quá trình yêu cầu load class được gắn với; có thể chỉ ủy quyền và nhận class do loader khác định nghĩa.
- `clazz.getClassLoader()` trả **defining loader** (với một số class nền tảng, kết quả có thể là `null` cho bootstrap loader).

Do đó, **hai đối tượng `ClassLoader` khác nhau không tự động tạo hai runtime class khác nhau**. Bài test phải buộc chúng tự định nghĩa lớp mục tiêu thay vì âm thầm ủy quyền toàn bộ cho cùng loader cha.

## 2. Vì sao mặc định dễ không tái hiện được hiện tượng?

`ClassLoader.loadClass(String)` mặc định thường thực hiện parent-first delegation: kiểm tra class đã load, nhờ loader cha tìm class và chỉ tự tìm khi cần. Nếu hai loader đều có cùng cha và cha đã thấy `sample.Plugin`:

```text
PluginLoader A ──┐
                 ├──> AppClassLoader ──> sample.Plugin
PluginLoader B ──┘

Kết quả: hai đường truy cập trỏ cùng Class<?> do AppClassLoader định nghĩa.
```

Như vậy, đoạn test chỉ gọi:

```java
ClassLoader a = new ClassLoader() {};
ClassLoader b = new ClassLoader() {};
Class<?> typeA = a.loadClass("sample.Plugin");
Class<?> typeB = b.loadClass("sample.Plugin");
```

**không chứng minh** JVM tạo hai kiểu. Hai instance `ClassLoader` bên trên không override cách tìm/nạp nên nhiều khả năng đều được cha phục vụ.

Muốn kiểm thử trường hợp hai kiểu riêng biệt, cần một trong hai cách:

1. Tạo hai loader riêng với nguồn class không thuộc phạm vi loader cha, để mỗi loader tự `findClass` rồi `defineClass`.
2. Viết class loader **ưu tiên tự định nghĩa đúng class mục tiêu**, và vẫn ủy quyền các class khác cho cha.

Bài này dùng cách 2, cố ý giới hạn chỉ tên `sample.Plugin`. Đây là một mô phỏng có kiểm soát; **không nên chuyển toàn bộ loader thành child-first** nếu không hiểu hệ quả với thư viện và API chung.

## 3. Thiết kế bài kiểm thử có kết quả xác định

Ta dùng ba file Java và hai instance của một class loader tùy chỉnh:

```text
src/
├── host/
│   └── Speaker.java          (contract chung, app loader nạp)
├── sample/
│   └── Plugin.java           (class bytecode được định nghĩa lại)
└── TwoLoadersTest.java       (test runner + PluginLoader)
```

Cấu trúc loader mong muốn:

```text
                    AppClassLoader
                 /                  \
        PluginLoader A         PluginLoader B
          sample.Plugin          sample.Plugin
                 \                  /
                  \__ host.Speaker _/
                       (cùng một Class<?>)
```

Hai plugin đều implements `host.Speaker`. Các loader con tự định nghĩa `sample.Plugin` từ **cùng bytecode**, nhưng ủy quyền việc tìm `host.Speaker` cho cùng loader cha. Điều này giúp quan sát hai tính chất cùng lúc:

- Hai class `sample.Plugin` **không cùng type identity**, nên ép đối tượng của bên A sang class bên B thất bại.
- Cả hai instance **cùng tương thích** với `host.Speaker` do cha định nghĩa, nên có thể gọi `hello()` thông qua contract chung.

## 4. Chương trình Java 17+ có thể biên dịch và chạy

### File 1 — `src/host/Speaker.java`

```java
package host;

public interface Speaker {
    String hello();
}
```

Interface `Speaker` nằm trên classpath ứng dụng. Chương trình host dùng interface này như **điểm gặp nhau** giữa các plugin.

### File 2 — `src/sample/Plugin.java`

```java
package sample;

import host.Speaker;

public class Plugin implements Speaker {
    public Plugin() {}

    @Override
    public String hello() {
        return "hello from plugin";
    }
}
```

Ta chỉ cần **một** file nguồn và **một** file bytecode `Plugin.class`. Hai class loader khác nhau sẽ đọc **cùng mảng bytecode** nhưng mỗi loader tự định nghĩa class trong không gian của mình.

### File 3 — `src/TwoLoadersTest.java`

```java
import host.Speaker;
import java.nio.file.Files;
import java.nio.file.Path;

public class TwoLoadersTest {
    private static final String TARGET = "sample.Plugin";

    static final class PluginLoader extends ClassLoader {
        private final byte[] bytecode;

        PluginLoader(byte[] bytecode) {
            super(TwoLoadersTest.class.getClassLoader());
            this.bytecode = bytecode.clone();
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (!TARGET.equals(name)) {
                return super.loadClass(name, resolve);
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> found = findLoadedClass(name);
                if (found == null) {
                    found = defineClass(name, bytecode, 0, bytecode.length);
                }
                if (resolve) resolveClass(found);
                return found;
            }
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Use: java -cp out TwoLoadersTest out/sample/Plugin.class");
        }

        byte[] bytes = Files.readAllBytes(Path.of(args[0]));
        ClassLoader one = new PluginLoader(bytes);
        ClassLoader two = new PluginLoader(bytes);

        Class<?> a = Class.forName(TARGET, false, one);
        Class<?> b = Class.forName(TARGET, false, two);

        System.out.println("Same binary name: "
                + a.getName().equals(b.getName()));
        System.out.println("Same Class object: " + (a == b));
        System.out.println("Distinct defining loaders: "
                + (a.getClassLoader() != b.getClassLoader()));
        System.out.println("A assignable from B: "
                + a.isAssignableFrom(b));
        System.out.println("Interface accepts A: "
                + Speaker.class.isAssignableFrom(a));
        System.out.println("Interface accepts B: "
                + Speaker.class.isAssignableFrom(b));

        Object instanceA = a.getDeclaredConstructor().newInstance();
        Object instanceB = b.getDeclaredConstructor().newInstance();

        System.out.println("Instance A is B: "
                + b.isInstance(instanceA));
        try {
            b.cast(instanceA);
            System.out.println("Cast unexpectedly succeeded");
        } catch (ClassCastException expected) {
            System.out.println("Cast B <- instance A: ClassCastException");
        }

        Speaker first = Speaker.class.cast(instanceA);
        Speaker second = Speaker.class.cast(instanceB);
        System.out.println("Shared interface: "
                + first.hello() + " | " + second.hello());
    }
}
```

### Biên dịch và chạy

Đứng tại thư mục chứa `src/`. Với JDK 17 hoặc mới hơn, thực hiện:

```bash
javac -encoding UTF-8 -d out src/host/Speaker.java src/sample/Plugin.java src/TwoLoadersTest.java
java -cp out TwoLoadersTest out/sample/Plugin.class
```

Lệnh `javac -d out` tạo:

```text
out/
├── host/
│   └── Speaker.class
├── sample/
│   └── Plugin.class
├── TwoLoadersTest.class
└── TwoLoadersTest$PluginLoader.class
```

Các file class của runner và interface chung nằm trên application classpath; trong chương trình, mỗi `PluginLoader` chỉ giữ bytecode đọc từ `out/sample/Plugin.class`.

### Kết quả thực tế

```text
Same binary name: true
Same Class object: false
Distinct defining loaders: true
A assignable from B: false
Interface accepts A: true
Interface accepts B: true
Instance A is B: false
Cast B <- instance A: ClassCastException
Shared interface: hello from plugin | hello from plugin
```

Kết quả được kiểm tra bằng cách biên dịch và chạy chương trình Java tương ứng. Tên file, class và những chuỗi in ra khớp với mã nguồn bên trên.

## 5. Giải thích chính xác từng kết quả

### 5.1. Cùng tên, khác `Class<?>`

`a.getName().equals(b.getName())` trả `true` vì cả hai class đều mang binary name `sample.Plugin`.

`a == b` trả `false` vì hai đối tượng `Class<?>` biểu diễn hai kiểu khác nhau: class do loader A định nghĩa và class do loader B định nghĩa.

Cùng bytecode **không** buộc Java hợp nhất hai class từ hai loader. Việc mỗi loader định nghĩa bytecode tạo ra một không gian type riêng cho tên đó.

### 5.2. `getClassLoader()` chỉ ra nguồn định danh

`a.getClassLoader() != b.getClassLoader()` là `true`. Trong test, loader A và B khác nhau và mỗi loader thật sự gọi `defineClass` cho `sample.Plugin`.

Đây là bước kiểm chứng cần thiết. Nếu chỉ in hai loader variable được tạo khác nhau mà không kiểm tra **defining loader của kết quả `Class<?>`**, bài test có thể vô tình chỉ chứng minh hai instance loader, chưa chứng minh class đã được định nghĩa riêng.

### 5.3. `isAssignableFrom` và `isInstance` không chỉ nhìn tên

`a.isAssignableFrom(b)` là `false`. Hai class độc lập không có quan hệ kế thừa kiểu với nhau dù tên trùng.

`b.isInstance(instanceA)` cũng là `false`: instanceA được tạo bởi class A, không phải instance của class B.

Việc gọi `b.cast(instanceA)` ném `ClassCastException` như mong đợi. Lỗi không đến từ method `hello()`, không phải do chuỗi tên khác nhau, mà do **khác runtime type identity**.

### 5.4. Vì sao cả hai vẫn được coi là `Speaker`?

Cả `sample.Plugin` từ loader A và loader B đều implements **cùng `host.Speaker` của parent loader**. Do `Speaker` do loader cha định nghĩa nên:

```java
Speaker.class.isAssignableFrom(a);  // true
Speaker.class.isAssignableFrom(b);  // true
```

Hai đối tượng có thể được nhìn qua `Speaker`:

```java
Speaker left = Speaker.class.cast(instanceA);
Speaker right = Speaker.class.cast(instanceB);
```

Đây là lợi ích thực tế của kiến trúc plugin: **giữ contract chung ở host/parent loader, chỉ nạp implementation riêng ở plugin loader**. Qua interface chung, chương trình host không cần ép kiểu về `sample.Plugin` của một loader cụ thể.

## 6. Tìm hiểu kỹ phương thức `loadClass` được override

Trong `PluginLoader`, điều kiện:

```java
if (!TARGET.equals(name)) {
    return super.loadClass(name, resolve);
}
```

giữ **parent delegation** cho tất cả class ngoài `sample.Plugin`: Java API, `host.Speaker`, `java.lang.String` và các dependency chung. Với `sample.Plugin`, loader sẽ tự định nghĩa.

Đoạn chính:

```java
synchronized (getClassLoadingLock(name)) {
    Class<?> found = findLoadedClass(name);
    if (found == null) {
        found = defineClass(name, bytecode, 0, bytecode.length);
    }
    if (resolve) resolveClass(found);
    return found;
}
```

Có bốn ý cần hiểu:

1. `getClassLoadingLock(name)` giúp phối hợp các lời gọi load đồng thời cho cùng binary name của một loader. Đây là quy trình phù hợp với hợp đồng khóa trong `ClassLoader`.
2. `findLoadedClass(name)` tránh **định nghĩa trùng lần hai trong cùng loader**. Sau khi loader đã định nghĩa class có tên đó, các lần yêu cầu tiếp theo trả lại đối tượng `Class<?>` đã tạo.
3. `defineClass(name, bytecode, 0, bytecode.length)` dùng bytecode để tạo runtime class với **defining loader chính là instance `PluginLoader` hiện tại**. Bytecode có binary name phải phù hợp tên `name`; nếu không, JVM có thể báo lỗi như `NoClassDefFoundError`.
4. `resolveClass(found)` được gọi khi đối số `resolve` yêu cầu linking/resolution bổ sung; không đồng nghĩa ép khởi tạo class.

**Vì sao clone bytecode?** Constructor `PluginLoader` dùng `bytecode.clone()` để hai loader giữ dữ liệu bất biến theo ý đồ ví dụ trước những sửa đổi từ bên ngoài. Không phải cứ clone bytecode là tạo class riêng; điều quyết định là **hai lời gọi `defineClass` bởi hai loader khác nhau**.

**Vì sao không override `findClass` cho mọi tên?** Bài học muốn trình bày hiện tượng trong phạm vi duy nhất `sample.Plugin`. Trong ứng dụng lớn, thường triển khai `findClass`/`loadClass` theo chiến lược rõ, với dependency resolution và bảo vệ namespace. Mẫu tối giản này là fixture kiểm thử, không phải class loader hoàn chỉnh cho production.

## 7. Phép kiểm thử bị sai thường gặp: cả hai loader đều ủy quyền cho cha

Nếu dùng code như:

```java
ClassLoader parent = TwoLoadersTest.class.getClassLoader();
ClassLoader childA = new ClassLoader(parent) {};
ClassLoader childB = new ClassLoader(parent) {};

// Nếu app classpath có sample.Plugin, cả hai thường ủy quyền lên cha.
Class<?> a = Class.forName("sample.Plugin", false, childA);
Class<?> b = Class.forName("sample.Plugin", false, childB);
```

Đây là **đoạn minh họa phụ thuộc vào dự án có sẵn `TwoLoadersTest`/`sample.Plugin`**. Với parent-first mặc định và class `sample.Plugin` nằm trên application classpath, `a == b` có thể là `true`.

Đừng kết luận “JVM không hỗ trợ hai class cùng tên”. Fixture chưa tạo ra hai **defining loader** khác nhau.

Để test đúng:

- Đảm bảo class mục tiêu được định nghĩa cục bộ trong từng loader (như ví dụ).
- In `a.getClassLoader()` và `b.getClassLoader()` hoặc so sánh trực tiếp.
- Xác nhận `a != b` và `a.getName().equals(b.getName())`.
- Tạo instance từ đúng `Class<?>` tương ứng và thử `isInstance`/`cast`.

## 8. Những biến thể kiểm thử nên biết

### 8.1. Cùng loader gọi lại cùng tên

Sau khi A đã load `sample.Plugin`, lệnh:

```java
Class<?> again = Class.forName("sample.Plugin", false, one);
System.out.println(again == a); // true
```

là đoạn minh họa trong ngữ cảnh `main` của ví dụ; nó cho thấy **cùng loader không định nghĩa thêm một bản** sau khi đã cache class thành công. Các lần gọi lại vẫn là một `Class<?>`.

Nếu cố gọi `defineClass` một lần thứ hai với đúng binary name trong **cùng loader**, JVM có thể ném `LinkageError` do duplicate definition; không nên thiết kế hot reload bằng cách cố định nghĩa đè class cũ ngay trong loader đang sống.

### 8.2. Thử ép kiểu trực tiếp theo tên class từ host

Nếu host import `sample.Plugin` rồi cast một instance do plugin loader A định nghĩa:

```java
// Minh họa ý tưởng, đòi hỏi host biên dịch nhìn thấy sample.Plugin.
sample.Plugin p = (sample.Plugin) instanceA;
```

Kết quả cũng có thể ném `ClassCastException` vì `sample.Plugin` được tham chiếu trực tiếp từ host thường là class do application loader định nghĩa, **khác** `sample.Plugin` của plugin loader A. Một message exception có thể nhắc đến hai loader, dù cả hai type đều in cùng tên class.

### 8.3. Nếu interface chung cũng bị định nghĩa hai lần?

Giả sử loader A tự định nghĩa cả `sample.Plugin` và `host.Speaker`, còn loader B cũng tự định nghĩa hai class đó. Khi ấy:

```text
(host.Speaker, loader A) != (host.Speaker, loader B)
```

Host gọi `Speaker.class.isAssignableFrom(a)` có thể trả `false`, làm mất đường giao tiếp chung. Đây là lỗi thường gặp khi một plugin đóng gói trùng API chung trong JAR riêng và class loader giải quyết API theo hướng child-first.

Thiết kế plugin đáng tin cậy nên chọn rõ **nhóm package được chia sẻ/ủy quyền** và **nhóm package do plugin tự định nghĩa**, đồng thời bảo đảm tương thích phiên bản interface.

### 8.4. Cùng tên nhưng bytecode khác nhau

Hai loader có thể nạp các phiên bản bytecode khác nhau cho cùng binary name. Chúng vẫn khác type identity. Nếu bytecode khác method, field hoặc API signature, runtime còn có thể xảy ra các lỗi compatibility hoặc linkage khi cố trộn object, lời gọi và class loader.

Trong bài này dùng **cùng bytecode** để cô lập một nguyên nhân duy nhất: loader khác nhau.

### 8.5. Unload/hot reload

Một loader mới có thể định nghĩa thế hệ `sample.Plugin` mới. Class thế hệ cũ và thế hệ mới vẫn là hai kiểu khác nhau ngay cả khi giữ tên. Class unloading phụ thuộc việc class/loader liên quan còn được tham chiếu hay không, GC và điều kiện runtime; không thể chắc chắn unload ngay chỉ vì xóa file JAR.

Sau hot reload, tránh giữ những reference cũ trong static caches, thread context class loader, thread hoặc registry; nếu không, loader cũ và toàn bộ class của nó có thể tiếp tục tồn tại.

## 9. Mối liên hệ với Reflection và kiểm thử thực tế

Reflection thường trả về `Class<?>`, `Method`, `Field` hoặc `Constructor`. Những metadata object này **thuộc về class cụ thể do loader cụ thể định nghĩa**. Vì vậy, khi framework quét plugin rồi gọi `Method.invoke`, cần dùng receiver của **đúng runtime class**, không chỉ đối tượng có `getClass().getName()` giống tên phương thức khai báo.

Kiểm tra đúng:

```java
// Trong ngữ cảnh ví dụ trên:
Class<?> targetClass = a;
Object target = targetClass.getDeclaredConstructor().newInstance();
boolean receiverMatches = targetClass.isInstance(target); // true
```

Kiểm tra sai:

```java
// Không đủ để xác nhận compatibility:
boolean nameMatches = target.getClass().getName()
        .equals(b.getName()); // true, nhưng b.cast(target) vẫn thất bại
```

**Trong cache Reflection:** Đừng chỉ lấy `String className` làm cache key nếu ứng dụng có nhiều loader. Dùng `Class<?>` hoặc key gắn rõ loader/đời class, tùy chính sách lifecycle. Ngược lại, cache toàn cục giữ `Class<?>`/`Method` mãi cũng có thể cản class unloading; cache cần quản lý vòng đời plugin.

### Gợi ý các assertion của một bài test tự động

Với `a`, `b`, `instanceA`, `instanceB` và `one`/`two` trong chương trình:

```java
// Đây là nhóm assertion để đưa vào test có cùng fixture, không phải file độc lập.
if (!a.getName().equals(b.getName())) throw new AssertionError();
if (a == b) throw new AssertionError();
if (a.getClassLoader() == b.getClassLoader()) throw new AssertionError();
if (a.isAssignableFrom(b)) throw new AssertionError();
if (b.isInstance(instanceA)) throw new AssertionError();
if (!Speaker.class.isInstance(instanceA)) throw new AssertionError();
if (!Speaker.class.isInstance(instanceB)) throw new AssertionError();
```

Các điều kiện này kiểm thử **hành vi quan trọng**, không phụ thuộc việc `ClassLoader.toString()` in hash code nào. Nếu dùng JUnit, chuyển thành `assertEquals`/`assertNotSame`/`assertFalse`/`assertTrue`/`assertThrows`; việc chọn framework không đổi bản chất kiểm thử.

## 10. Cảnh báo khi chuyển fixture sang hệ thống thật

- **Không nạp bytecode không đáng tin cậy chỉ để thử nghiệm.** `defineClass` không phải sandbox an toàn. Code được nạp có thể thực thi khi khởi tạo hoặc khi gọi method; kiểm soát nguồn bytecode và quyền chạy.
- **Không tùy tiện child-first mọi class.** Nếu nhân bản `java.*` hoặc API của host, loader có thể gặp lỗi bảo vệ package, loader constraint, incompatibility hoặc `ClassCastException`. JVM có quy tắc bảo vệ các namespace như `java.*`.
- **Giữ API chia sẻ ở loader cha hoặc một loader dùng chung.** Với hệ thống plugin, truyền DTO kiểu có hợp đồng rõ hoặc qua serialization thay vì truyền thẳng implementation nội bộ.
- **Chú ý Java modules (JPMS).** Module readability, `exports` và `opens` có thể ảnh hưởng Reflection; classpath test trong ví dụ là **unnamed module** và không thay thế test trong named-module deployment.
- **Loader constraints.** Khi nhiều loader cùng tham gia resolve chữ ký method/field, JVM có thể áp đặt ràng buộc về identity của các kiểu được chia sẻ. Một thiết kế liên kết sai có thể gây `LinkageError` bên cạnh `ClassCastException`.
- **Thread context class loader** có thể khác class loader định nghĩa code của thread. `Thread.currentThread().getContextClassLoader()` thường được framework dùng để khám phá resource/service, nhưng không làm hai kiểu cùng tên tự động hợp nhất.
- **Đừng so sánh chỉ `getName()`.** Khi debug, ghi tên class, mô tả loader định nghĩa và module khi cần; luôn kiểm tra quyền và tránh lộ thông tin nhạy cảm trong log.

## 11. Khi nào nên chọn một loader chung?

Nếu mục tiêu là **chia sẻ cùng một type** giữa các phần của hệ thống, cho chúng cùng ủy quyền việc load lớp đó tới một loader phù hợp. Điều này làm cho runtime type identity thống nhất.

Nếu mục tiêu là **cô lập plugin/phiên bản**, cho mỗi plugin loader tự định nghĩa implementation, nhưng thống nhất contract trong một nơi dùng chung. Cách này cho phép plugin trùng binary name mà không khiến host nhầm các implementation với nhau.

| Mục tiêu | Kiến trúc loading | Kiểm chứng |
| --- | --- | --- |
| Dùng chung một class | Hai child loader ủy quyền cho cùng defining loader | `a == b` |
| Hai bản implementation tách biệt | A và B tự định nghĩa cùng binary name | `a.getName().equals(b.getName())` và `a != b` |
| Hai plugin cùng thực thi contract | Interface chung do parent/host loader định nghĩa | `SharedApi.class.isAssignableFrom(a/b)` |
| Hot reload không xung đột class cũ | Tạo loader mới cho implementation mới, điều phối vòng đời | Class cũ và mới khác identity, dọn reference khi ngừng dùng |

## 12. Câu hỏi tự kiểm tra

1. Vì sao hai class có cùng `getName()` vẫn không thể ép kiểu cho nhau?
2. Nếu hai `ClassLoader` khác nhau đều delegate class đích cho cùng parent, kết quả `a == b` ra sao?
3. `Class.getClassLoader()` thể hiện *defining loader* hay luôn là loader được dùng ở lời gọi đầu tiên?
4. Trong ví dụ, vì sao `Speaker.class.isAssignableFrom(a)` và `Speaker.class.isAssignableFrom(b)` cùng đúng dù `a.isAssignableFrom(b)` sai?
5. Tại sao phải dùng `findLoadedClass` khi định nghĩa class theo yêu cầu?
6. Vì sao hot reload thường dùng loader mới, thay vì gọi `defineClass` lần nữa trên loader cũ?
7. Khi ứng dụng cache metadata Reflection của plugin theo tên class, lỗi nào có thể xảy ra sau reload?
8. Nếu cùng interface API cũng bị hai loader tự define, host còn cast hai plugin về interface của host một cách an toàn không?

## 13. Tài liệu tham khảo

- [Java SE 17: `ClassLoader`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassLoader.html): `loadClass`, `findLoadedClass`, `defineClass`, `getClassLoadingLock`.
- [Java SE 17: `Class`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Class.html): `getName`, `getClassLoader`, `isAssignableFrom`, `isInstance`, `cast`.
- [Java Language Specification §12.2: Loading of Classes and Interfaces](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.2): tính duy nhất của runtime class identity và các class loader.
- [Java Virtual Machine Specification §5.3: Creation of Classes and Interfaces](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-5.html#jvms-5.3): defining loader và initiating loader.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="reflection-testing-011">Kiểm thử cache không giữ plugin đã hết vòng đời</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểm thử cache không giữ plugin đã hết vòng đời

## 1. Vấn đề: plugin đã dừng nhưng classloader vẫn còn sống

Trong ứng dụng Java tải plugin động (plugin manager, server ứng dụng, công cụ kiểm thử, hệ thống hot reload), mỗi plugin có thể được nạp bằng một `ClassLoader` riêng, chẳng hạn `URLClassLoader`. Khi plugin bị gỡ, ứng dụng thường mong muốn không còn đối tượng, class hay metadata nào của plugin bị giữ bởi mã sống lâu hơn.

Một lỗi phổ biến là **cache reflection trong phần lõi ứng dụng vô tình giữ classloader của plugin**:

```text
GC roots của ứng dụng lõi
  └─ static metadataCache
      └─ Map entry
          ├─ key: Class<?> của plugin ──> defining ClassLoader
          └─ value: Method/Field/Constructor ──> declaring Class ──> ClassLoader
```

Một `Class<?>` được định nghĩa bởi plugin loader gắn với loader đó trong quá trình tồn tại; các đối tượng `Method`, `Field`, `Constructor` hay `MethodHandle` có thể giữ các tham chiếu liên quan tới class/plugin. Khi cache toàn cục giữ những đối tượng này, **`plugin.stop()` hoặc `URLClassLoader.close()` không đủ** để plugin trở nên không còn reachable.

Hệ quả có thể là:

- Số lượng classloader và class đã nạp tăng qua các lượt reload;
- Bộ nhớ metadata/class và heap không trở lại trạng thái ổn định;
- Plugin cũ còn giữ registry, listener hoặc dữ liệu; các bản plugin mới có thể gặp xung đột;
- Hot reload kéo dài dẫn tới tăng bộ nhớ, chi phí GC và lỗi tài nguyên.

Mục tiêu của bài này là **thiết kế kiểm thử xác nhận cache không giữ plugin sau khi kết thúc vòng đời**, đồng thời tránh ngộ nhận rằng chỉ một lần `System.gc()` không thu được classloader là bằng chứng chắc chắn có memory leak.

## 2. Hiểu chính xác “đã hết vòng đời” và “được giải phóng”

Một plugin thường trải qua các bước:

1. **Load**: tạo loader riêng, nạp class, phát hiện metadata, tạo instance và đăng ký dịch vụ.
2. **Use**: gọi plugin, có thể cache `Method`/`Field`/`MethodHandle`/descriptor để tránh scan lặp.
3. **Deactivate**: ngừng nhận yêu cầu mới, đợi tác vụ đang chạy kết thúc và không tạo callback mới.
4. **Cleanup**: unregister listener/service, cancel task, đóng tài nguyên, bỏ cache entries và xóa reference từ host.
5. **Close loader**: đóng `URLClassLoader` khi phù hợp; thao tác này giúp giải phóng resource và ngăn việc nạp từ các URL của loader theo hợp đồng API, nhưng **không cưỡng chế class unloading**.
6. **Observe eligibility**: không còn strong-reference path từ GC roots tới đối tượng classloader/class/plugin.
7. **Eventually collect**: GC *có thể* thu dọn classloader và các lớp liên quan khi chúng không còn reachable; **thời điểm không được cam kết**.

Phân biệt hai mục tiêu kiểm thử:

| Mục tiêu | Phương pháp đáng tin cậy |
| --- | --- |
| Host đã loại bỏ cache entry/registration theo hợp đồng? | Kiểm thử xác định bằng API: `cache.size() == 0`, registry không còn id, task canceled, `close()` được gọi |
| Loader đã trở nên eligible for collection? | Dùng `WeakReference<ClassLoader>`/`ReferenceQueue`, tách scope strong references, kiểm tra trong nhiều chu kỳ |
| Loader đã thực sự bị GC thu gom? | Quan sát `WeakReference.get() == null` hoặc queue event; thường cần thử nhiều lần, kết quả có thể không kết luận |
| Không còn leak trong môi trường thật? | Thử reload nhiều vòng + metrics classloader/class count/heap + profiler/JFR, không dựa vào một phép đo |

**“Không quan sát GC xảy ra” khác “có chứng cứ leak”.** `System.gc()` chỉ là yêu cầu/hint; GC có thể bị cấu hình hạn chế, hoãn hoặc có bộ thu khác nhau giữa JDK. Dùng timeout hợp lý, kết quả *inconclusive* và chẩn đoán reachability nếu cần.

## 3. Cache reflection nào có thể giữ plugin?

### 3.1. Strong-reference cache giữ key hoặc value của plugin

Ví dụ một framework sở hữu classloader lâu dài:

```java
static final java.util.Map<Class<?>, java.lang.reflect.Method> METHOD_CACHE =
        new java.util.concurrent.ConcurrentHashMap<>();
```

Nếu key là `pluginClass` thì map giữ class đó mạnh. Nếu value là `Method` khai báo bởi plugin class, value cũng có thể giữ declaring class. Một cache `Map<String, Method>` cũng không an toàn chỉ vì key là `String`: value vẫn giữ method của plugin.

Tương tự, những kiểu metadata khác cần đánh giá:

- `Class<?>`, `Method`, `Field`, `Constructor<?>`, `RecordComponent`;
- `MethodHandle`, `VarHandle`, `MethodHandles.Lookup`, bound handle được `bindTo(pluginInstance)`;
- `MethodType` hoặc schema/descriptor chứa `Class<?>`, annotation instance, accessor lambda;
- `java.beans.BeanInfo`/`PropertyDescriptor` và framework metadata cache;
- `ServiceLoader`, factory, callback/`Runnable`, instance hoặc classloader được lưu trong registry.

Không phải mọi giá trị metadata đều chắc chắn giữ classloader; **phân tích object graph thực tế**. Nhưng coi bất kỳ entry nào có tham chiếu tới `Class<?>` hoặc instance của plugin là có nguy cơ cho tới khi chứng minh lifecycle an toàn.

### 3.2. `WeakHashMap` không tự động giải quyết mọi vòng tham chiếu

Ví dụ thiết kế dễ sai:

```java
Map<Class<?>, Method> cache = new WeakHashMap<>();
cache.put(pluginClass, pluginClass.getMethod("run"));
```

Fragment này cần import `Map`, `WeakHashMap`, `Method` và có `pluginClass`. Dù key là weak, **value `Method` có thể tham chiếu mạnh ngược về declaring class**. Vì `WeakHashMap` vẫn giữ value mạnh, đường `map -> value -> class (weak key)` có thể khiến class sống mãi; key không được dọn như kỳ vọng.

Weak key chỉ có ích nếu cấu trúc value không dẫn ngược về key hoặc loader qua bất cứ strong-reference path nào. Trong thực tế có thể dùng `ClassValue<T>` hoặc explicit eviction theo plugin lifecycle thay vì đoán một map có weak key là đủ.

### 3.3. `ClassValue<T>` cho metadata gắn với vòng đời class

`ClassValue` cung cấp cơ chế lưu một giá trị được tính dựa theo `Class<?>` và được JVM quản lý theo lifecycle của class. Ví dụ:

```java
private static final ClassValue<Method> REFLECTIVE_ADD = new ClassValue<>() {
    @Override
    protected Method computeValue(Class<?> type) {
        try {
            return type.getMethod("add", int.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }
};
```

Đây là **fragment** cần import `java.lang.reflect.Method` và khai báo trong class. Cache gắn với class theo `ClassValue` phù hợp hơn một static strong map có key `Class<?>` cho nhiều trường hợp, đặc biệt hệ thống nạp lớp động.

Nhưng `ClassValue` **không bảo đảm plugin có thể unload** nếu cùng `Method`/plugin instance còn bị giữ ở nơi khác (ví dụ một static registry, listener hay queue). Ngoài ra, `computeValue` có thể chạy nhiều lần trong race; giá trị nên được tính theo cách idempotent và không dựa vào side effect duy nhất. Khi policy metadata thay đổi, cần chiến lược invalidation/`remove(type)` phù hợp.

### 3.4. Cache theo plugin ID và eviction

Nếu host chủ động quản lý vòng đời, một chính sách rõ ràng là cache theo plugin ID/generation và **xóa mọi descriptor gắn với generation tại unload**. Ví dụ:

```java
// Pseudocode minh họa kiến trúc, không phải Java API có sẵn.
onPluginUnload(pluginId) {
    stopNewCalls(pluginId);
    waitForInFlightCalls(pluginId);
    unregisterCallbacks(pluginId);
    reflectionCache.evictAll(pluginId);
    closePluginResources(pluginId);
    closeClassLoader(pluginId);
}
```

Ưu điểm: kiểm thử được eviction một cách **xác định**, không phải đợi GC. Điểm quan trọng là không để một nhiệm vụ async hoặc callback cũ có thể ghi lại cache entry sau khi vừa cleanup. Dùng generation token, trạng thái CLOSED hoặc synchronization phù hợp để ngăn *resurrection* của cache entries cũ.

## 4. Thiết kế một bài kiểm thử đúng

Một test chất lượng gồm cả **negative control** (mẫu cache sai thật sự giữ loader) và **positive case** (cleanup bỏ mọi strong references có thể kiểm soát).

### 4.1. Tạo plugin với loader riêng

Dùng `URLClassLoader` với URL trỏ tới thư mục chứa bytecode plugin và parent phù hợp. Plugin class phải được **định nghĩa thực sự bởi loader riêng**. Nếu chỉ tải `java.lang.String` từ bootstrap/platform loader và gắn `WeakReference` vào một loader rỗng, phép thử không chứng minh gì về metadata plugin.

Có thể tạo một Java source nhỏ, compile vào thư mục tạm bằng `javax.tools.JavaCompiler`, sau đó nạp class bằng `Class.forName(name, true, pluginLoader)`. Dùng `pluginClass.getClassLoader() == pluginLoader` làm assertion trước khi bắt đầu.

### 4.2. Tách scope mạnh khỏi bước quan sát

Trong hàm tạo/plugin-use, các biến cục bộ như `Class<?> pluginClass`, `Method method`, `Object pluginInstance` hoặc `URLClassLoader loader` đều có thể là strong references. JIT có quyền giữ local reachable lâu hơn bạn tưởng.

Vì vậy hãy đặt **tất cả load/use/close trong một hàm helper không inline ở mức thiết kế test** rồi chỉ trả ra `WeakReference<ClassLoader>`; không mang theo `Class<?>` hoặc `Method`. Việc tách hàm giảm nguy cơ chính test tự giữ loader. Đừng lưu plugin object vào trường static của test hoặc lambda callback, đừng ghi instance vào assertion failure message còn sống.

### 4.3. Dùng `WeakReference` và `ReferenceQueue` đúng mục đích

`WeakReference.get() == null` chứng tỏ referent đã được GC xóa weak reference. Khi đăng ký một reference với `ReferenceQueue`, có thể kiểm tra sự kiện enqueue bằng `queue.poll()` hoặc `queue.remove(timeout)`. Đó là **dấu hiệu GC thu dọn**, không phải tín hiệu synchronous từ `ClassLoader.close()`.

Thử nhiều lần có khoảng nghỉ; nếu không quan sát collection, báo *inconclusive* hoặc kích hoạt heap analysis. Không dùng `Thread.sleep(100)` như bằng chứng loader phải biến mất trong 100 ms. Không đặt timeout cực nhỏ khiến CI flaky; cũng không cấp phát vô hạn để “ép GC” đến `OutOfMemoryError`.

### 4.4. Cleanup phải đo được độc lập với GC

Cần assert các sự kiện có tính xác định:

- `cache.size()`/plugin-specific entry count về 0 sau unload;
- registry, listener, command handler, scheduled job, executor/task và callback của plugin được giải phóng;
- không còn thread của plugin hay TCCL (`Thread.contextClassLoader`) trỏ vào loader cũ;
- loader/resource được `close` theo hợp đồng; streams/JAR handles đã đóng;
- nếu có framework global caches, biết rõ cơ chế eviction/hook (ví dụ JavaBeans `Introspector.flushFromCaches(pluginClass)` trong ngữ cảnh thích hợp);
- không còn request in-flight tiếp tục gọi hoặc đăng ký lại listener/cache của plugin vừa unload.

Nếu những assertion này chưa đạt, không cần dựa vào GC để kết luận cleanup đang lỗi.

## 5. Ví dụ Java 17+ hoàn chỉnh: thấy cache sai giữ loader và dọn cache

Ví dụ dưới đây compile một plugin Java nhỏ **ngay trong thư mục tạm**, nạp bằng `URLClassLoader`, gọi method qua reflection, cố tình lưu `Class<?> -> Method` trong static cache **sai** để thể hiện đường strong reference, rồi xóa cache. Sau đó chương trình chỉ *thử quan sát* GC.

> Chạy bằng **JDK**, không phải JRE rút gọn, vì cần `ToolProvider.getSystemJavaCompiler()`. Chương trình tạo và xóa thư mục tạm trong quá trình chạy; không tải plugin từ mạng.

Lưu thành **`PluginCacheLifecycleDemo.java`**:

```java
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public class PluginCacheLifecycleDemo {
    // CỐ TÌNH SAI: host cache giữ mạnh cả Class lẫn Method của plugin.
    static final Map<Class<?>, Method> BAD_CACHE = new ConcurrentHashMap<>();
    static final ReferenceQueue<ClassLoader> QUEUE = new ReferenceQueue<>();

    // Trả ra CHỈ weak ref. Mọi strong ref tạm được giữ trong scope này.
    static WeakReference<ClassLoader> loadUseAndClose() throws Exception {
        Path root = Files.createTempDirectory("plugin-classloader-test-");
        Path javaFile = root.resolve("demo/Plugin.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile,
            "package demo; public class Plugin {"
            + " public Plugin() {}"
            + " public String hello() { return \"hello\"; }"
            + " }");

        try {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null) {
                throw new IllegalStateException("Please run on a full JDK");
            }

            int exit = compiler.run(null, null, null,
                "--release", "17", "-d", root.toString(), javaFile.toString());
            if (exit != 0) {
                throw new IllegalStateException("Compile error: " + exit);
            }

            try (URLClassLoader loader = new URLClassLoader(
                    new URL[] {root.toUri().toURL()},
                    ClassLoader.getPlatformClassLoader())) {
                Class<?> plugin = Class.forName("demo.Plugin", true, loader);
                if (plugin.getClassLoader() != loader) {
                    throw new AssertionError("Plugin must use its own loader");
                }
                Method method = plugin.getMethod("hello");

                // Tạo đường tham chiếu mạnh: BAD_CACHE -> Class/Method -> loader.
                BAD_CACHE.put(plugin, method);

                Object instance = plugin.getConstructor().newInstance();
                System.out.println("plugin call: " + method.invoke(instance));
                return new WeakReference<>(loader, QUEUE);
            }
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path :
                        paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    static boolean observeCollection(WeakReference<ClassLoader> ref)
            throws InterruptedException {
        for (int i = 0; i < 15; i++) {
            System.gc(); // Hint, KHÔNG bảo đảm chạy GC ngay.
            if (ref.get() == null || QUEUE.poll() == ref) {
                return true;
            }
            Thread.sleep(80);
        }
        return ref.get() == null;
    }

    public static void main(String[] args) throws Exception {
        WeakReference<ClassLoader> reference = loadUseAndClose();

        System.out.println("bad cache size: " + BAD_CACHE.size());
        System.out.println("loader reachable via bad cache: "
                + (reference.get() != null));

        // Cleanup bắt buộc: entry thuộc plugin phải được evict.
        BAD_CACHE.clear();
        System.out.println("bad cache empty after cleanup: "
                + BAD_CACHE.isEmpty());

        boolean collected = observeCollection(reference);
        System.out.println("GC: " + (collected
                ? "collected (observed)"
                : "not observed (inconclusive)"));
    }
}
```

Biên dịch và chạy:

```bash
javac --release 17 PluginCacheLifecycleDemo.java
java PluginCacheLifecycleDemo
```

Ba dòng đầu và dòng cleanup có kết quả xác định trong trường hợp biên dịch/nạp plugin thành công:

```text
plugin call: hello
bad cache size: 1
loader reachable via bad cache: true
bad cache empty after cleanup: true
```

Dòng cuối phụ thuộc thời điểm GC. Hai kết quả có thể xảy ra:

```text
GC: collected (observed)
```

hoặc:

```text
GC: not observed (inconclusive)
```

**Không coi nhánh thứ hai là test failure chắc chắn.** Kết quả chứng minh một phần quan trọng: khi `BAD_CACHE` còn entry, loader vẫn reachable thông qua cache; sau `BAD_CACHE.clear()`, đường tham chiếu cụ thể đó đã bị loại bỏ. Cần quan sát các đường tham chiếu khác của một ứng dụng thật nếu loader vẫn tồn tại qua nhiều lần GC.

Tại sao demo dùng `ClassLoader.getPlatformClassLoader()` làm parent? Class `demo.Plugin` không phụ thuộc class của host, và parent platform không có định nghĩa `demo.Plugin`; nhờ đó plugin class chắc chắn được định nghĩa trong loader riêng. Hệ thống thật có thể chọn parent khác để chia sẻ API plugin, nhưng phải bảo đảm không vô tình load plugin implementation từ loader cha.

Điểm giới hạn của demo: `BAD_CACHE.clear()` xóa **toàn bộ** cache giả lập cho một plugin. Trong hệ thống đa plugin, chỉ evict entries thuộc đúng plugin generation; không xóa metadata của plugin khác. Và `QUEUE` minh họa queue sự kiện weak ref; không phải cần thiết để xác nhận cache entry đã bị evict.

## 6. Viết test regression chắc chắn, tránh flaky GC test

Nên chia test thành hai tầng.

### Tầng A — Kiểm thử cleanup xác định (bắt buộc)

Tạo fake plugin với một ID hoặc generation rõ ràng. Thực hiện lần lượt:

1. Đăng ký service, tạo cache entries bằng reflection, tạo listener/task đã biết.
2. Xác minh trong trạng thái ACTIVE, các entries cần dùng có tồn tại.
3. Gọi `stop/unload`; chờ các tác vụ in-flight hoàn tất theo hợp đồng.
4. Assert cache không còn entries của **đúng plugin generation**.
5. Assert registry/listener/thread/task đã được unregister/cancel; loader đã đóng khi áp dụng.
6. Assert API không chấp nhận thêm call/cache write sau trạng thái CLOSED.
7. Nạp plugin thế hệ mới có cùng tên class và cùng plugin ID; đảm bảo metadata thế hệ cũ không bị tái sử dụng.

Đây là bài kiểm thử không cần ép GC; nó nên chạy ổn định trong CI ở mọi JDK được hỗ trợ.

### Tầng B — Kiểm thử quan sát loader collection (diagnostic/integration)

- Tạo classloader riêng và plugin class thật, dùng `WeakReference<ClassLoader>`; có thể bổ sung weak refs tới `Class<?>` hay plugin instance, nhưng **không giữ mạnh** các referent đó trong test.
- Chạy load/unload nhiều vòng, helper riêng cho mỗi vòng.
- Sau cleanup, yêu cầu GC theo chu kỳ **giới hạn** và lấy kết quả từ weak refs/`ReferenceQueue`.
- Nếu có weak ref bị clear, đó là bằng chứng collection đã được quan sát cho referent ấy.
- Nếu không clear, phân loại **không kết luận**, thu heap dump/đồ thị dominator để tìm root pinning; không mặc định “leak”.
- Dùng workload lặp và classloader metrics để tìm xu hướng retention lâu dài, không suy từ một điểm đo.

Một test chạy trên JUnit có thể dùng `assertTimeout` hoặc cơ chế deadline cho pha chờ **giữ ổn định thời gian kiểm thử**, nhưng tuyệt đối không ép việc GC phải xảy ra trước deadline trong môi trường mà JVM không hứa hẹn điều đó. Nếu sản phẩm có yêu cầu giải phóng tài nguyên định lượng, xác minh tài nguyên có hợp đồng release rõ ràng (đóng file descriptor, unregister listener, số cache entries) trước; kiểm chứng class unloading qua integration metrics riêng.

### Negative control: xác nhận test có thể nhận ra leak thật

Trong một lượt test được cách ly:

- Giữ `Class<?>` hoặc `Method` của plugin trong static cache host;
- Sau khi helper trả về chỉ weak ref, xác minh strong cache còn entry và `weak.get() != null`;
- Xóa entry đó, kiểm tra `cache.isEmpty()`;
- Sau đó **có thể** chờ/quan sát GC.

Với negative control này, bạn chứng minh bộ kiểm thử nhìn thấy ít nhất một đường retention xác định. Nếu chỉ test bản “tốt” mà không có mẫu rò rỉ có chủ ý, chưa chắc helper quan sát/cleanup đã được thiết kế đúng.

## 7. Những nguồn giữ classloader nằm ngoài cache reflection

Ngay cả khi metadata cache được thiết kế hợp lý, plugin vẫn có thể bị giữ bởi:

| Nguồn tham chiếu | Cách phát sinh | Hướng xử lý |
| --- | --- | --- |
| Static registry trong host | `Map<String, PluginHandler>` lưu instance/plugin callback | Unregister theo plugin generation |
| Executor/Thread đang chạy | Runnable/lambda nắm `pluginInstance`/`ClassLoader` | Stop và join/chờ task; loại references trong queue |
| Thread context classloader (TCCL) | Thread host gọi `setContextClassLoader(pluginLoader)` rồi không khôi phục | Dùng `try/finally` để khôi phục TCCL; dừng plugin threads |
| `ThreadLocal` | Thread sống lâu giữ value thuộc plugin | Gọi `remove()` trên đúng thread hoặc quản lý vòng đời thread |
| Listener/MBean/observer | Host event bus giữ listener class của plugin | Unregister theo token rõ ràng |
| Scheduler, timer, callback | Job tương lai capture plugin object | Cancel; loại queue entry và chặn re-registration |
| JavaBeans metadata | `Introspector` hoặc framework cache giữ `BeanInfo`/descriptor | Xem API flush/invalidation, ví dụ `Introspector.flushFromCaches(clazz)` khi phù hợp |
| Service discovery | `ServiceLoader`/factory giữ provider instances hoặc loader | Giải phóng loader/provider và cache liên quan |
| Logging / tracing / metrics | Logger appender, instrumentation hoặc exporter giữ callback/class | Remove registrations khi plugin stop |
| JNI/native handles/file resources | Native code/JAR handle giữ tài nguyên | Đóng đúng tài nguyên, giải phóng registration theo API native |

Một static field **nằm trong chính class plugin** không tự tạo GC root bên ngoài plugin nếu toàn bộ loader/class của nó đã unreachable; rủi ro là tham chiếu từ **host/JDK thread/registry sống lâu** dẫn ngược vào plugin. Khi điều tra, luôn hỏi “GC root nào đang giữ đường mạnh tới loader?”.

### Ví dụ khôi phục TCCL an toàn

```java
ClassLoader old = Thread.currentThread().getContextClassLoader();
try {
    Thread.currentThread().setContextClassLoader(pluginLoader);
    // Tác vụ ngắn hạn cần loader của plugin.
} finally {
    Thread.currentThread().setContextClassLoader(old);
}
```

Fragment trên cần biến `pluginLoader` hợp lệ. Đây là hygiene cho các thread tái sử dụng từ pool. Nó không thay thế việc stop các thread thực sự được plugin tạo ra. Khi dùng thread pool, cần quản lý cả `Runnable`/`Callable` đang nằm trong queue.

## 8. Thiết kế cache cho nhiều plugin, classloader và hot reload

### Key phải phân biệt phiên bản plugin

Cùng tên fully qualified class, ví dụ `com.example.Plugin`, có thể được định nghĩa bởi **hai classloader khác nhau**. Ở Java, identity của class liên quan đến *binary name + defining classloader*. Vì vậy cache theo **string class name** đơn thuần có thể trả metadata cũ sai classloader sau reload.

Nếu cache theo tên, hãy gắn thêm plugin generation/classloader identity bằng một khóa có lifecycle quản lý chặt chẽ; đồng thời evict khi unload. Tránh lưu key strong-ref classloader vĩnh viễn vào global cache.

### Cache data “safe” vẫn có thể bị một value pinning

Một wrapper `CachedAccessPlan` có thể tưởng chỉ giữ tên/kiểu dưới dạng text, nhưng một field ẩn như `Method getter`, `Class<?> type`, `MethodHandle access` hay `Supplier<?> lambda` có thể giữ plugin. Kiểm tra object graph của **value**, không chỉ kiểu key.

### Dọn cache dưới concurrency

Plugin có thể bị unload đúng lúc request khác đang dùng cache:

1. Chuyển plugin sang trạng thái `STOPPING` để chặn cấp phiên gọi mới.
2. Đợi/hủy request in-flight theo hợp đồng.
3. Xóa metadata/registry/task đã thuộc generation đó.
4. Đóng tài nguyên/loader.
5. Đặt `CLOSED`; mọi lookup muộn của generation cũ phải bị từ chối.

Nếu chỉ gọi `cache.remove(key)` rồi một worker còn chạy lại `computeIfAbsent` và tạo entry mới, plugin cũ bị giữ lại. Kiểm thử race cần dùng barrier/latch điều phối một request đang lookup, đồng thời stop plugin; cuối test bảo đảm **không có repopulation**.

### Bảo vệ reference qua nhiều bản JDK

Nhiều bản JDK có thể thay đổi nội bộ reflection, GC và class unloading. Đừng dùng một magic threshold hoặc suy đoán cache internal `Method` để xác nhận unload. Kiểm thử trên JDK mục tiêu (ít nhất version được support) với assert dựa trên **public lifecycle contract**, kết hợp quan sát `WeakReference`/JFR/heap analysis. Đặc biệt khi nâng JDK, cache dùng internal accessor của JDK là thiết kế dễ vỡ.

## 9. Công cụ chẩn đoán khi loader chưa biến mất

Khi tích hợp reload nhiều vòng mà số loader/class không ổn định:

1. Ghi số plugin generations đã đóng, cache entries theo generation, thread/task/registry còn hoạt động.
2. Thử bật Unified Logging về class unload nếu cần: `-Xlog:class+unload=info` (trên HotSpot hỗ trợ cú pháp này). Log sự kiện class unload chỉ khi nó thật sự xảy ra, không phải cam kết thời điểm.
3. Dùng JFR hoặc heap dump và công cụ phân tích dominator paths để tìm **GC root → host cache/registry/thread → plugin classloader**.
4. Kiểm tra static map `Class<?> -> Method`, `WeakHashMap` có value tham chiếu key, `ClassValue` bị external leak, thread context loader, `ThreadLocal`, scheduled executors.
5. Xác định object thực sự đang bị giữ: loader, `Class<?>`, plugin instance, metadata/handle, thread hay resource. Một heap snapshot đơn lẻ cần được đọc cẩn thận; số classloaded cao có thể do workload hoặc GC chưa chạy.
6. Loại bỏ một strong-reference path, chạy lại chuỗi test tương đương; so sánh metrics và object graph. Tránh thay nhiều yếu tố cùng lúc rồi không còn biết nguyên nhân.

Khi phân tích đồ thị, nhớ rằng **một classloader có thể chứa nhiều lớp**; việc thấy một loader còn sống có thể giữ lại lượng lớn metadata dù chỉ một listener hoặc một `Method` bị cache sai.

## 10. Lỗi thường gặp và câu hỏi tự kiểm tra

- **“`URLClassLoader.close()` đã gọi, vậy class chắc chắn unload”:** sai; `close()` giải phóng tài nguyên loader nhưng không xóa các strong references hiện có.
- **“`System.gc()` một lần không thu gom thì chắc chắn memory leak”:** sai; GC không cam kết thời điểm.
- **“`WeakHashMap<Class<?>, Method>` là an toàn vì key weak”:** sai nếu value `Method` giữ class/key.
- **“`ClassValue` sửa mọi leak”:** sai khi listener, registry, thread hoặc một cache khác vẫn giữ plugin.
- **“Cache class name là đủ để phân biệt plugin sau reload”:** sai vì cùng tên class có thể đến từ hai loader khác nhau.
- **“Một lần xóa cache entry là xong dù plugin đang chạy song song”:** sai nếu task muộn có thể tạo lại entry.
- **“Chỉ đo heap usage ngay sau stop”:** chưa đủ; heap memory khác metaspace và còn phụ thuộc GC, allocation pressure, classloader count, JIT warm-up.
- **“Test giữ biến `Class<?>`/`Method` ngoài scope rồi chờ GC”:** chính test có thể là nguồn giữ plugin và tạo false positive.

Tự kiểm tra:

1. Static cache của host lưu `Method` khai báo trong plugin; plugin đóng URLClassLoader nhưng method vẫn trong cache. Có nguy cơ giữ loader không? **Có**: metadata có thể giữ declaring class và loader.
2. Cần test chắc chắn không flaky trong CI: dùng gì trước? **Assert eviction/unregistration/stop chính xác**, rồi quan sát GC ở tầng integration riêng.
3. `weak.get() != null` sau 100 ms có chứng minh leak? **Không**.
4. Dùng `WeakHashMap<Class<?>, Method>` có đủ an toàn không? **Không**, phải xét đường tham chiếu từ value về key.
5. Reload hai plugin class cùng tên nhưng khác loader: identity class có giống nhau không? **Không**.
6. Nếu cache đã xóa mà classloader vẫn sống, cần điều tra gì? **Các GC roots khác:** thread/TCCL, ThreadLocal, listener, task, service registry, framework cache và object graph.
7. Một request đang chạy có thể thêm cache entry của plugin `CLOSED` sau cleanup. Kiểm thử gì? **Race test với generation token/barrier**, đảm bảo không thể repopulate entry.

Đọc thêm từ API chuẩn:

- [`java.lang.ref.WeakReference` — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/WeakReference.html)
- [`ReferenceQueue` — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/ReferenceQueue.html)
- [`ClassValue` — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ClassValue.html)
- [`URLClassLoader.close` — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/URLClassLoader.html#close())
- [`java.util.WeakHashMap` — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/WeakHashMap.html)
- [`Introspector.flushFromCaches` — Java 17](https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/java/beans/Introspector.html#flushFromCaches(java.lang.Class))
- [JDK Flight Recorder](https://docs.oracle.com/en/java/javase/17/jfapi/flight-recorder.html)


</details>

- [Quay lại đầu trang](#back-to-top)
