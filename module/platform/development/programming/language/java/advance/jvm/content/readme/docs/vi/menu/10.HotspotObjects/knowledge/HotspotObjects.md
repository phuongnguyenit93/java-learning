<a id="back-to-top"></a>

# Đối tượng trong HotSpot

## Menu
- [Chuỗi tạo đối tượng: cấp phát, giá trị mặc định và chạy constructor](#hotspot-objects-001)
- [Cấp phát nhanh bằng con trỏ và vùng cấp phát riêng của luồng (TLAB)](#hotspot-objects-002)
- [Đầu đối tượng, dữ liệu trường, căn chỉnh và phần đệm](#hotspot-objects-003)
- [Bố trí đối tượng và mảng là chi tiết triển khai, không phải cam kết của Java](#hotspot-objects-004)
- [Compressed oops và compressed class pointers trong JVM 64-bit](#hotspot-objects-005)
- [Tham chiếu được cập nhật khi GC di chuyển đối tượng](#hotspot-objects-006)
- [Compact Strings, string interning và string deduplication có mục đích khác nhau](#hotspot-objects-007)
- [Tuổi thọ đối tượng, khả năng truy cập và giới hạn suy luận từ phạm vi biến](#hotspot-objects-008)
- [Đánh giá chi phí của nhiều đối tượng nhỏ và cấu trúc dữ liệu giữ tham chiếu](#hotspot-objects-009)

## <a id="hotspot-objects-001">Chuỗi tạo đối tượng: cấp phát, giá trị mặc định và chạy constructor</a>

<details>
<summary>Xem chi tiết</summary>

# Chuỗi tạo đối tượng trong HotSpot: cấp phát, giá trị mặc định và chạy constructor

## Mục tiêu

Biểu thức `new Person("Lan")` chứa nhiều hoạt động khác nhau: kiểm tra và khởi tạo class nếu cần, cấp phát một **instance** có đủ các fields, đặt chúng về **default values**, đánh giá các đối số constructor, rồi gọi phương thức đặc biệt **`<init>`**. Constructor không phải hàm cấp phát bộ nhớ: khi constructor bắt đầu chạy, instance và các trường mặc định đã tồn tại về mặt ngữ nghĩa.

Sau bài học bạn có thể:
- Mô tả trình tự tạo class instance theo **JLS `15.9.4, `12.5** và lệnh **`new`** theo JVMS.
- Phân biệt **`<clinit>` của class** với **`<init>` của từng instance**, default fields và explicit initializers.
- Hiểu thứ tự constructor superclass/subclass, `this(...)`, `super(...)`, field initializer, instance initializer block và constructor body.
- Giải thích vì sao constructor superclass có thể nhìn thấy field subclass bằng 0/null dù source khai báo giá trị khác.
- Nhận biết cơ chế cấp phát riêng của **HotSpot** như TLAB, đường cấp phát nhanh/chậm, zeroing, object header, alignment, compressed oops và ảnh hưởng của JIT.
- Phân biệt tạo object từ `new` với tạo mảng qua `newarray`, `anewarray`, `multianewarray`.
- Đọc bytecode bằng `javap`, giải thích `new`, `dup`, `invokespecial` và rủi ro constructor thất bại hoặc `this` bị publish quá sớm.

## 1. Chuỗi tạo đối tượng theo ngữ nghĩa Java và JVM

Một biểu thức Java:

~~~java
Person person = new Person("Lan");
~~~

Đây là snippet minh họa, cần có class `Person` và constructor phù hợp để biên dịch.

Sơ đồ khái niệm:

~~~text
new Person("Lan")
      |
      v
Resolve class; class initialization nếu chưa được thực hiện
      |
      v
Cấp phát class instance (bao gồm fields của superclasses)
      |
      v
Thiết lập default values cho TẤT CẢ instance fields
      |
      v
Đánh giá constructor arguments từ trái sang phải
      |
      v
Chạy instance initialization method <init>:
  constructor chain -> superclass trước, subclass sau
  field initializers và instance blocks của từng class
  statements trong constructor body
      |
      v
Nếu thành công -> trả reference tới object mới
Nếu lỗi -> hoàn tất abruptly / ném exception
~~~

**Thứ tự quan trọng:** theo **JLS `15.9.4**, đối với class instance creation expression thông thường, bước **cấp phát và thiết lập default fields xảy ra trước đánh giá các actual constructor arguments**. Arguments được đánh giá **từ trái sang phải** rồi mới gọi constructor được chọn. Với qualified inner-class creation, qualifying expression còn phải được đánh giá trước, theo quy tắc riêng.

Bytecode `new` yêu cầu class được resolve/initialized theo JVMS nếu cần trước khi tiếp tục tạo instance. Khi class đã được initialized từ trước, JVM **không chạy lại class initializer** cho từng phép `new`.

Bảng phân biệt:

| Hoạt động | JVM/JLS bảo đảm gì? | Triển khai HotSpot có thể làm gì? |
| --- | --- | --- |
| Class resolution/initialization | Class hợp lệ được resolve và initialized khi cần theo quy tắc `new` | Kiểm tra metadata class, class-init fast path |
| Allocation | Instance mới về mặt ngữ nghĩa, chứa các inherited/declared instance fields | Cấp phát nhanh từ TLAB hoặc đi slow path |
| Default initialization | Số nguyên 0, boolean false, floating-point +0.0, char NUL, reference null | Zeroing/khai thác vùng đã được zero |
| Object metadata | VM biết runtime type/identity để thao tác với instance | Ghi header, Klass metadata, căn chỉnh theo cấu hình |
| Constructor | Chạy chuỗi `<init>` và initializer đúng thứ tự | Interpreter/JIT, có thể inline các lời gọi |
| Kết quả biểu thức | Reference nếu hoàn tất thành công | Representation reference do VM quyết định |

Sơ đồ là **mô hình ngữ nghĩa**, không phải danh sách lệnh CPU bắt buộc; optimizer chỉ được thay đổi cách thực hiện khi không làm sai hành vi Java quan sát được.

## 2. Class initialization khác instance initialization

**`<clinit>`** là phương thức đặc biệt cho **class/interface initialization**, liên quan static initializers. **`<init>`** là tên JVM dành cho **constructor/instance initialization methods**. Chúng không thay thế nhau.

~~~java
class Counter {
    static int total = 10;
    int value = 5;

    Counter() {
        total++;
    }
}
~~~

Đây là class minh họa, chưa có `main`. Nếu `Counter` chưa được initialized, lần `new Counter()` có thể kích hoạt quy trình class initialization để static field `total` nhận giá trị 10. Sau đó **mỗi** lần tạo một Counter instance:
1. Object nhận default `value=0` sau allocation.
2. Chạy initializer `value=5` và constructor body tăng `total`.
3. Class initializer **không chạy lại** cho instance thứ hai.

| Thuật ngữ | Nội dung | Tần suất theo semantics |
| --- | --- | --- |
| **Class preparation** | Trong linking, tạo static fields và default values | Cho class/interface runtime được link |
| **Class initialization** | Gán ConstantValue và chạy `<clinit>` nếu có | Theo quy tắc class-init, không phải mỗi object |
| **Object allocation** | Tạo một instance mới | Mỗi evaluation `new` về mặt ngữ nghĩa |
| **Object default initialization** | Thiết lập toàn bộ instance fields về default | Cho instance mới |
| **Instance initialization** | Chuỗi constructor `<init>` và field/block initializers | Cho instance đang tạo |

Không nhầm **class preparation** (tạo static fields của class) với việc cấp phát **instance fields** của một object.

## 3. Default values có trước các explicit field initializers

Khi cấp phát instance, JVM tạo trạng thái gồm mọi instance fields thuộc class được tạo và các superclasses, kể cả các field bị hide. Chúng nhận giá trị mặc định, không phải giá trị source khai báo sau dấu `=`.

| Instance field | Default sau allocation |
| --- | --- |
| `byte`, `short`, `int`, `long` | 0 tương ứng kiểu |
| `float` | +0.0f |
| `double` | +0.0d |
| `boolean` | false |
| `char` | NUL, code unit 0 (`'\u0000'`) |
| `String`, class/interface reference | null |
| `int[]` hoặc reference đến mảng | null |

Ví dụ:

~~~java
class Data {
    int count = 7;
    String label = "ready";
    int[] numbers = new int[3];
}
~~~

Đây là class minh họa. **Ngay sau object allocation** và trước các explicit instance initializers: `count=0`, `label=null`, `numbers=null`. Sau khi các initializer thực thi trong constructor chain: `count=7`, `label` trỏ đến String, `numbers` trỏ đến array mới có ba int elements bằng 0.

Không cần `javac` sinh bytecode gán 0/null cho từng field. Hoạt động bảo đảm default values do VM thực hiện. Giá trị `'\u0000'` khác ký tự số `'0'`; reference null cũng khác object String rỗng.

### Hidden fields không biến mất

~~~java
class A { int id = 1; }
class B extends A { int id = 2; }
~~~

Class B instance có **cả A.id và B.id** như hai fields riêng. Mỗi field đều nhận default 0 khi allocation, rồi initializer của declaring class được chạy theo constructor chain. Đây là field *hiding*, không phải overriding field.

## 4. Constructor chain: superclasses trước, subclass sau

Đối với Java 17 và constructor bình thường, tiến trình initialization mang ý nghĩa:

~~~text
Object.<init>()
       |
       v
Base instance field initializers + instance blocks
       |
       v
Base constructor body
       |
       v
Derived instance field initializers + instance blocks
       |
       v
Derived constructor body
~~~

Các field initializers và instance initializer blocks của **cùng một class** được thực hiện theo **thứ tự xuất hiện trong source**. Constructor có thể gọi `this(...)` để delegate sang constructor khác trong cùng class hoặc `super(...)` để gọi superclass constructor. Nếu không ghi explicit invocation, compiler thêm lời gọi superclass constructor mặc định theo quy tắc Java khi hợp lệ.

**Quan trọng:** Java 17 đòi explicit constructor invocation kiểu `this(...)`/`super(...)` ở đầu constructor tương ứng theo quy tắc phiên bản này; không trộn những cú pháp linh hoạt hơn của các JDK tương lai vào ví dụ Java 17.

### Chương trình chạy được: thấy field subclass còn mặc định

Tạo **`CreationOrderDemo.java`**:

~~~java
public class CreationOrderDemo {
    static int trace(String label, int value) {
        System.out.println(label + "=" + value);
        return value;
    }

    static class Base {
        int baseValue = trace("Base.field", 10);

        {
            System.out.println("Base.block=" + baseValue);
        }

        Base() {
            System.out.println(
                    "Base.constructor childValue=" +
                    ((Child) this).childValue);
        }
    }

    static final class Child extends Base {
        static {
            System.out.println("Child.<clinit>");
        }

        int childValue = trace("Child.field", 20);

        {
            System.out.println("Child.block=" + childValue);
        }

        Child() {
            System.out.println("Child.constructor=" + childValue);
        }
    }

    public static void main(String[] args) {
        System.out.println("main");
        Child c = new Child();
        System.out.println("after=" + c.baseValue + "," + c.childValue);
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac --release 17 CreationOrderDemo.java
java CreationOrderDemo
~~~

**Stdout chính xác:**

~~~text
main
Child.<clinit>
Base.field=10
Base.block=10
Base.constructor childValue=0
Child.field=20
Child.block=20
Child.constructor=20
after=10,20
~~~

**Diễn giải:**
1. `main` của outer class in `main`. Khi JVM cần tạo Child đầu tiên, static initializer của Child in `Child.<clinit>`.
2. Instance Child được tạo với field `Base.baseValue` **và** `Child.childValue` đã có nhưng đều bằng 0.
3. Constructor Child dẫn tới Base constructor. Sau phần gọi `Object.<init>`, Base initializer gán 10 và in hai dòng tương ứng.
4. **Trong Base constructor body**, Child field initializer chưa chạy, nên `((Child)this).childValue` vẫn là 0.
5. Sau khi Base constructor hoàn tất, Child initializer gán 20, block in 20, Child constructor body in 20.
6. Sau toàn bộ chuỗi, expression `new Child()` trả reference; dòng cuối in `after=10,20`.

Cast trong ví dụ an toàn vì object thực sự là Child. Tuy nhiên, **superclass constructor đọc trạng thái subclass** là cách thiết kế không nên lạm dụng. Đặc biệt, gọi một method có thể override từ superclass constructor có thể dispatch tới subclass method trước khi subclass fields được initialize, dẫn đến thấy 0/null hoặc gây `NullPointerException`.

### Virtual dispatch vẫn hoạt động trong constructor

~~~java
class Parent {
    Parent() {
        onReady();
    }

    void onReady() {}
}

class Child extends Parent {
    String name = "ready";

    @Override
    void onReady() {
        System.out.println(name);
    }
}
~~~

Đây là **class minh họa**. Khi tạo Child, `Parent()` có thể gọi method override `Child.onReady()` trước khi `Child.name` được gán "ready", vì object đã có runtime type Child. Khi đó có thể in `null`. Không nên gọi method overridable từ constructor base trừ khi thực sự hiểu và kiểm soát hệ quả.

## 5. Gọi `this(...)` không tạo thêm object

Constructor delegation sang overload không phải biểu thức `new` mới và không làm field initializers của class chạy lặp lại.

Tạo file **`ConstructorChainDemo.java`**:

~~~java
public class ConstructorChainDemo {
    static class Parent {
        Parent() {
            System.out.println("Parent()");
        }
    }

    static final class Child extends Parent {
        int value = initializeValue();

        {
            System.out.println("Child.block");
        }

        Child() {
            this(7);
            System.out.println("Child()");
        }

        Child(int requested) {
            System.out.println("Child(int)=" + requested);
        }

        int initializeValue() {
            System.out.println("Child.field");
            return 2;
        }
    }

    public static void main(String[] args) {
        Child c = new Child();
        System.out.println("value=" + c.value);
    }
}
~~~

Chạy:

~~~bash
javac --release 17 ConstructorChainDemo.java
java ConstructorChainDemo
~~~

**Stdout chính xác:**

~~~text
Parent()
Child.field
Child.block
Child(int)=7
Child()
value=2
~~~

`Child()` delegate tới `Child(int)`. Constructor đích xử lý chuỗi superclass; rồi Child field initializer/block được thực hiện **một lần**, sau đó thân `Child(int)` chạy và cuối cùng quay lại phần còn lại của `Child()`. Không có thêm Child instance được cấp phát do `this(7)`.

## 6. Bytecode của object creation: `new` không trực tiếp chạy `<init>`

Ví dụ một phương thức trong class (snippet minh họa):

~~~java
static Box create() {
    return new Box(5);
}
~~~

Giả sử có class Box với constructor nhận int. Bytecode từ `javac` thường có dạng:

~~~text
new           #N   // class Box
dup
iconst_5
invokespecial #M   // Method Box."<init>":(I)V
areturn
~~~

Đây là **mẫu minh họa**, không phải dump exact với offset/index bắt buộc. Mô hình operand stack:

| Instruction | Stack sau lệnh (từ đáy đến đỉnh) |
| --- | --- |
| `new #N` | `[uninitializedRef]` |
| `dup` | `[uninitializedRef, uninitializedRef]` |
| `iconst_5` | `[uninitializedRef, uninitializedRef, 5]` |
| `invokespecial #M` | `[initializedRef]` nếu constructor thành công |
| `areturn` | Trả reference cho caller |

Instruction `new` sử dụng constant pool entry xác định class được tạo, có thể làm class resolution/initialization nếu cần. `invokespecial` gọi instance initialization method `<init>`, với receiver và arguments có kiểu phù hợp.

**Verifier phân biệt uninitialized reference với reference đã hoàn tất constructor** để ngăn bytecode tùy ý sử dụng đối tượng trước khi initialized. Dù object đã có storage, không thể giả định mọi bytecode thao tác hợp lệ trên receiver chưa gọi `<init>`.

### Dùng javap kiểm chứng

Sau khi biên dịch:

~~~bash
javap -c -p CreationOrderDemo
javap -c -p 'CreationOrderDemo$Base'
javap -c -p 'CreationOrderDemo$Child'
javap -v -p 'CreationOrderDemo$Child'
~~~

Quan sát:
- `main` chứa `new`, `dup`, `invokespecial <init>` khi tạo Child.
- `Child.<init>` gọi `Base.<init>` trước các phép gán vào `childValue`.
- `Base.<init>` gọi `Object.<init>`, tiếp theo là initializer `baseValue`, instance block và constructor body.
- `Child.<clinit>` chứa static block, tách biệt với `Child.<init>`.
- Lệnh `putfield` gán 10/20 đến từ **explicit field initializers**; không cần có những lệnh gán default 0 do compiler sinh.

Chỉ số `#N`, bytecode offset và cách ghép chuỗi trong lệnh in có thể khác giữa compiler/JDK. Trên Windows cmd.exe có thể dùng tên class chứa dấu `$` trực tiếp; trên Bash/PowerShell, quote đơn như ví dụ ngăn shell diễn giải ký tự đặc biệt.

## 7. Cơ chế cấp phát riêng của HotSpot: TLAB và đường chậm

**Thread-Local Allocation Buffer (TLAB)** là một vùng **trong heap** mà HotSpot có thể dùng cho cấp phát object nhanh của một thread. Tên có chữ “thread-local” **không có nghĩa object nằm trên thread stack**; object được cấp vào TLAB vẫn là heap object và có thể chia sẻ sau đó.

Sơ đồ:

~~~text
Java heap / allocation region
    |
    +-- TLAB thread A: [start ... top ---> ... end]
    |
    +-- TLAB thread B: [start ... top ---> ... end]
    |
    +-- shared / slow allocation path nếu cần
~~~

Đường cấp phát nhanh có thể:
1. Xác định kích thước instance sau khi xét header, fields và alignment.
2. Kiểm tra `top + size` còn nằm trong giới hạn của TLAB không.
3. Nếu đủ, bump-pointer tăng top và nhận vùng chứa object.
4. Chuẩn bị header và default fields theo semantics.
5. Sau đó thực hiện instance constructor initialization.

Nếu không có TLAB phù hợp, object quá lớn hoặc vùng hiện tại không đủ, HotSpot đi **slow path** để thử refill TLAB, cấp phát bằng con đường khác hoặc kích hoạt các hoạt động GC; nếu thất bại có thể báo `OutOfMemoryError`.

Không khẳng định **mọi** HotSpot object đều cấp từ Eden/TLAB hoặc mọi GC dùng cùng chiến lược. Collector, architecture, JVM flags, size class và JDK version quyết định chi tiết.

### Zeroing không phải chạy constructor

Một cách hiện thực default fields là **zeroing bộ nhớ**; JVM cũng có thể dùng vùng đã zero hoặc tối ưu hóa các thao tác ghi mặc định không cần thiết. Nhưng kết quả Java phải tương đương các fields có default values **trước** phần code được phép quan sát chúng.

**Header không phải toàn bộ bit zero:** object header cần chứa thông tin runtime. Do đó nói “JVM zero mọi byte của object, kể cả header, rồi mới chạy code” như một quy tắc bất biến là không chính xác.

## 8. Object header, field layout và alignment

HotSpot thường phải quản lý:
- Metadata trong **object header** về object state/lock/GC theo chế độ phù hợp.
- Thông tin kiểu runtime qua **Klass metadata/pointer** hoặc cách mã hóa tương ứng.
- **Instance fields** của class và superclasses.
- **Padding/alignment** để tuân thủ điều kiện cấp phát.
- Với arrays, thông tin **array length** và vùng chứa elements.

Sơ đồ minh họa:

~~~text
[header / runtime metadata]
[Klass information nếu layout cần]
[instance fields của superclasses và class hiện tại]
[padding / alignment nếu có]
~~~

Đây **không phải layout tuyệt đối**. Kích thước header, vị trí field, compressed object pointers (compressed oops), compressed class pointers và các hình thức **compact object headers** phụ thuộc HotSpot version/options/platform. Không dạy cố định “mọi object 12 byte header” hoặc “reference luôn là địa chỉ máy 64 bit thô”.

Một class chỉ có `byte value` không có nghĩa object thực tế chiếm đúng 1 byte; còn header, padding và thừa kế. Để đo heap layout thực tế trên một JDK cụ thể, có thể dùng công cụ như Java Object Layout/JOL phiên bản phù hợp, ghi rõ môi trường và JVM flags. Kết quả phép đo **không phải** một phần của Java Language Specification.

## 9. Đánh giá constructor arguments và lỗi giữa các bước

Theo JLS `15.9.4, **allocation của class instance diễn ra trước khi arguments được đánh giá** (đối với biểu thức tạo class instance thông thường). Arguments sau đó được đánh giá trái sang phải.

Tạo file **`ArgumentOrderDemo.java`**:

~~~java
public class ArgumentOrderDemo {
    static int value(String label, int result) {
        System.out.println(label);
        return result;
    }

    static class Point {
        Point(int x, int y) {
            System.out.println("constructor=" + x + "," + y);
        }
    }

    public static void main(String[] args) {
        System.out.println("begin");
        new Point(value("arg1", 1), value("arg2", 2));
        System.out.println("end");
    }
}
~~~

Chạy:

~~~bash
javac --release 17 ArgumentOrderDemo.java
java ArgumentOrderDemo
~~~

**Stdout chính xác:**

~~~text
begin
arg1
arg2
constructor=1,2
end
~~~

Stdout minh họa thứ tự **side effects trong argument evaluation và constructor body**. Nó không đo trực tiếp thời điểm VM cấp phát bộ nhớ vì allocation thông thường không in gì. Nếu argument đầu ném exception, những arguments phía sau không được đánh giá và constructor body không chạy; biểu thức `new` không trả kết quả bình thường.

**OutOfMemoryError:** nếu allocation không thể thực hiện, biểu thức có thể ném OOME. JLS có quy định thứ tự kiểm tra OOME trước việc đánh giá argument của class instance creation. Không nên dùng thử nghiệm thiếu heap như một test có stdout ổn định vì OOME và GC phụ thuộc runtime/memory pressure.

## 10. Khi constructor ném exception

Constructor có thể kết thúc bất thường sau khi đã thực hiện một số side effects. Khi đó biểu thức `new` **không trả reference bình thường**, nhưng không có cơ chế “rollback mọi side effects”.

Tạo tệp **`ConstructorFailureDemo.java`**:

~~~java
public class ConstructorFailureDemo {
    static int attempts;

    static class Fragile {
        int value = 5;

        Fragile() {
            attempts++;
            System.out.println("constructor value=" + value);
            throw new IllegalStateException("failed");
        }
    }

    public static void main(String[] args) {
        try {
            Fragile f = new Fragile();
            System.out.println(f.value);
        } catch (IllegalStateException expected) {
            System.out.println("caught=" + expected.getMessage());
        }

        System.out.println("attempts=" + attempts);
    }
}
~~~

Chạy:

~~~bash
javac --release 17 ConstructorFailureDemo.java
java ConstructorFailureDemo
~~~

**Stdout chính xác:**

~~~text
constructor value=5
caught=failed
attempts=1
~~~

Field initializer `value=5` đã chạy trước thân constructor. Constructor tăng static counter rồi ném exception; kết quả `new` hoàn tất abruptly, còn `attempts=1` chứng minh side effect không tự bị hoàn tác. Nếu không có reference nào thoát ra, object không còn cần thiết có thể được GC thu hồi theo thời điểm VM quyết định.

### Tránh để `this` thoát khỏi constructor

~~~java
class UnsafePublication {
    static UnsafePublication leaked;
    final String label;

    UnsafePublication() {
        leaked = this;
        label = "ready";
    }
}
~~~

Đây là **ví dụ anti-pattern**, không phải khuyến nghị. Việc đưa `this` vào static field, listener, thread khác hoặc collection chia sẻ trước khi constructor hoàn tất có thể làm object bị quan sát ở trạng thái partially initialized và tạo rủi ro Java Memory Model, nhất là khi publication không có synchronization.

**Nguyên tắc:** hoàn thành constructor trước khi publish reference; dùng cơ chế safe publication phù hợp như synchronized/volatile/concurrent collections hoặc thread-safe factory. Không gọi virtual overridable method từ constructor nếu method đó cần subclass state đã khởi tạo.

## 11. Arrays: cấp phát và default elements, không chạy constructor phần tử

| Java source | Opcode JVM thường gặp | Hành vi |
| --- | --- | --- |
| `new int[3]` | `newarray int` | Một int[] object, ba primitive values 0 |
| `new Item[2]` | `anewarray Item` | Một Item[] object, hai references null |
| `new int[2][3]` | `multianewarray [[I, 2` | Outer array và inner arrays của các chiều chỉ định |
| `new int[2][]` | `anewarray [I` hoặc instruction tương đương | Outer array, hai slots null |

Mảng là object trong heap theo JVM. Tuy nhiên **`new Item[2]` không gọi constructor Item hai lần**, vì các phần tử ban đầu chỉ là reference values null.

Ví dụ chạy được **`ArrayBirthDemo.java`**:

~~~java
import java.util.Arrays;

public class ArrayBirthDemo {
    static final class Item {
        int count = 7;
    }

    public static void main(String[] args) {
        int[] numbers = new int[3];
        Item[] items = new Item[2];
        int[][] matrix = new int[2][3];

        System.out.println("numbers=" + Arrays.toString(numbers));
        System.out.println("items[0]=" + items[0]);
        System.out.println("matrixRows=" + matrix.length);
        System.out.println("matrixCols=" + matrix[0].length);

        items[0] = new Item();
        System.out.println("itemCount=" + items[0].count);
    }
}
~~~

Biên dịch/chạy:

~~~bash
javac --release 17 ArrayBirthDemo.java
java ArrayBirthDemo
~~~

**Stdout chính xác:**

~~~text
numbers=[0, 0, 0]
items[0]=null
matrixRows=2
matrixCols=3
itemCount=7
~~~

**Mảng có quy tắc lỗi riêng:** dimension âm gây `NegativeArraySizeException`; thiếu bộ nhớ có thể gây OOME. Thứ tự đánh giá dimension expressions của array trong JLS `15.10.2 **khác** thứ tự allocation/arguments của class instance creation ở `15.9.4, không áp một công thức cho cả hai.

## 12. JIT escape analysis, GC và reference value

Ở cấp Java, `new` tạo class instance mới về mặt ngữ nghĩa. Nhưng HotSpot JIT có thể tối ưu **object allocation vật lý** khi chứng minh object không escape và thay đổi này bảo toàn mọi hành vi quan sát được.

~~~java
static int total(int a, int b) {
    Pair p = new Pair(a, b);
    return p.first + p.second;
}
~~~

Đây là snippet minh họa, cần có class `Pair` với các fields thích hợp. JVM có thể dùng **scalar replacement** để thay object bằng các giá trị field riêng nếu điều kiện phù hợp. Không thể khẳng định bất kỳ lệnh `new` nào cũng chắc chắn có một khối RAM heap riêng trên mọi JIT run; ngược lại cũng không khẳng định **mọi object nhỏ được cấp trên stack**.

Khi một method kết thúc:
- Frame/local variables có thể không còn tồn tại theo mô hình stack.
- Object vẫn có thể reachable từ caller, static fields, những object khác hoặc GC roots thích hợp.
- GC có thể thu hồi object không còn reachable **vào thời điểm không xác định**, không nhất thiết đúng lúc constructor/body method return.
- TLAB là vùng heap hỗ trợ cấp phát, **không phải cơ chế tự dọn toàn bộ objects khi thread chết**.

Một object đã hoàn tất constructor cũng cần được **publish an toàn** nếu chia sẻ qua thread khác. Default initialization và constructor completion không biến mọi thao tác concurrent thành thread-safe.

## 13. Lỗi thường gặp và cách phân tích

| Triệu chứng / nhận định | Phân tích chính xác |
| --- | --- |
| “`new` chính là constructor.” | `new` cấp phát object; `invokespecial <init>` dùng để instance initialization. |
| “Field `int x=7` nhận 7 ngay trong allocation.” | Default 0 có trước; field initializer gán 7 trong chuỗi constructor. |
| “Child field phải là 20 khi Base constructor đang chạy.” | Child explicit initializer chưa chạy; Base có thể quan sát default 0. |
| “Constructor gọi `this(...)` nghĩa là một object thứ hai được tạo.” | Delegation trên cùng một object; không có `new` thứ hai chỉ bởi `this(...)`. |
| “Static block chạy mỗi lần tạo instance.” | Class initializer theo quy tắc class lifecycle, không phải mỗi object. |
| “Header của mọi HotSpot object có kích thước cố định.” | Phụ thuộc JVM version/flags/architecture, có thể có compact headers. |
| “TLAB là thread stack allocation.” | TLAB ở heap; object có thể escape và sống sau thread. |
| “Cứ có `new` là chắc chắn thấy một allocation trên profiler.” | JIT có thể loại bỏ allocation không quan sát được. |
| “Nếu constructor ném exception, mọi side effects rollback.” | Side effects không tự rollback; new hoàn tất abruptly. |
| “Array reference slots tự tạo các phần tử class.” | Reference elements nhận null, phải tạo object phần tử riêng. |
| “Class chưa init sẽ chạy constructor trước static initializer.” | Class initialization nếu cần được bảo đảm theo quy tắc `new` trước tạo instance. |
| “`new int[-1]` gọi một constructor mảng.” | Không có constructor array Java kiểu đó; NegativeArraySizeException. |
| “Một object tự nhiên thread-safe vì memory đã zero.” | Default values không bảo đảm safe publication/thread-safety. |

### Lộ trình chẩn đoán lỗi tạo object

1. **Resolution/loading:** class có đúng JAR/module, class loader và binary compatibility không?
2. **Class initialization:** static initializer ném exception không? Xem `ExceptionInInitializerError` và nguyên nhân.
3. **Allocation:** thiếu heap/GC pressure, `OutOfMemoryError`? Đo thay vì suy đoán.
4. **Argument evaluation:** actual argument ném exception trước constructor body?
5. **Constructor chain:** exception ở superclass initializer, instance initializer hay constructor body?
6. **Publication:** object bị lộ sớm/được chia sẻ giữa threads mà thiếu happens-before?
7. **Performance:** đang đo JIT warmup/GC/TLAB hay allocation logic? Tránh gán một kết quả profiler cho mọi JDK.

## 14. Câu hỏi tự kiểm tra

**Câu 1.** Khi nào instance fields nhận default values?

**Đáp án:** Sau allocation, **trước** constructor arguments evaluation và constructor initialization theo JLS `15.9.4 đối với class instance creation thông thường.

**Câu 2.** `new` có trực tiếp thực thi `<init>` không?

**Đáp án:** Không; ở class file, `new` tạo uninitialized object reference, rồi compiler dùng `invokespecial` để gọi `<init>`.

**Câu 3.** Vì sao Base constructor đọc `Child.childValue` bằng 0?

**Đáp án:** Toàn object đã có default values, nhưng explicit Child field initializer vẫn chưa chạy.

**Câu 4.** `this(7)` trong constructor có cấp phát object mới không?

**Đáp án:** Không. Nó delegate tới overload constructor trên **cùng instance**.

**Câu 5.** `<clinit>` và `<init>` khác nhau?

**Đáp án:** `<clinit>` khởi tạo class/static state; `<init>` khởi tạo từng object thông qua constructor chain.

**Câu 6.** TLAB nằm trong stack hay heap?

**Đáp án:** Heap; nó là vùng cấp phát cục bộ theo thread của HotSpot, không đồng nghĩa vùng stack.

**Câu 7.** Có thể khẳng định object header luôn 12/16 byte không?

**Đáp án:** Không. Layout/header phụ thuộc phiên bản HotSpot, architecture, flags và hình thức header.

**Câu 8.** `new Item[3]` tạo mấy Item objects?

**Đáp án:** Không tạo Item instance nào; chỉ tạo một Item[] array object với ba null reference elements.

**Câu 9.** Constructor thất bại có trả reference qua biểu thức `new` như bình thường không?

**Đáp án:** Không; biểu thức hoàn tất abruptly, exception truyền ra, nhưng side effects đã xảy ra không tự rollback.

**Câu 10.** Tạo xong object có nghĩa mọi thread khác luôn nhìn thấy fields cuối cùng không?

**Đáp án:** Không. Cần safe publication và các quy tắc Java Memory Model; tránh this-escape.

**Câu 11.** Vì sao compiler/JIT có thể bỏ một allocation mà Java semantics vẫn đúng?

**Đáp án:** Khi object không escape và phép tối ưu bảo toàn mọi hành vi quan sát được, JIT có thể scalar-replace/eliminate physical allocation.

**Câu 12.** Thứ tự argument evaluation của constructor?

**Đáp án:** Trái sang phải, sau allocation với class instance creation expression thông thường theo JLS.

## 15. Bản đồ ghi nhớ

~~~text
JAVA SOURCE                JVM / HOTSPOT
new Type(args)
      |
      +--> Resolve class, class initialization nếu cần
      |
      +--> Allocate instance  (HotSpot: TLAB hoặc slow path)
      |
      +--> Default fields     (0, false, NUL, +0.0, null)
      |
      +--> Evaluate args      (left to right)
      |
      +--> invokespecial <init>
              |
              +-- Object/superclass constructor chain
              +-- superclass initializers / constructor body
              +-- subclass initializers / constructor body
              |
              v
          Thành công? -- YES --> trả reference tới instance
                      -- NO  --> exception, không có kết quả thường

Class init: <clinit>, không phải constructor của từng object
Arrays: newarray / anewarray / multianewarray
TLAB: heap allocation fast path, KHÔNG phải thread stack
JIT: có thể bỏ allocation vật lý, nhưng phải giữ đúng semantics
~~~

**Kết luận:** tạo instance có hai phần khác nhau: **cấp phát + default initialization** do JVM quản lý và **constructor initialization** do code `<init>` thực hiện. Superclass constructor chạy trước phần khởi tạo của subclass, vì vậy có thể gặp default values khi object còn đang xây dựng. Hiểu thêm TLAB, object header và JIT giúp phân biệt những guarantee của Java với các tối ưu nội bộ HotSpot.

## Tài liệu chính thức

- [JLS Java SE 17 — `15.9.4 Run-Time Evaluation of Class Instance Creation Expressions](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.9.4).
- [JLS Java SE 17 — `12.5 Creation of New Class Instances](https://docs.oracle.com/javase/specs/jls/se17/html/jls-12.html#jls-12.5).
- [JVMS Java SE 17 — `6.5 new](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html#jvms-6.5.new).
- [JVMS Java SE 17 — `2.5.3 Heap](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.3).
- [JVMS Java SE 17 — `2.9.1 Instance Initialization Methods](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.9.1).
- [JVMS Java SE 17 — `4.10.2.4 Newly Created Objects](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.10.2.4).
- [OpenJDK HotSpot source — oop.hpp (chi tiết triển khai)](https://github.com/openjdk/jdk/blob/master/src/hotspot/share/oops/oop.hpp).
- [JDK 17 — javap tool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hotspot-objects-002">Cấp phát nhanh bằng con trỏ và vùng cấp phát riêng của luồng (TLAB)</a>

<details>
<summary>Xem chi tiết</summary>

# Cấp phát nhanh bằng con trỏ và vùng cấp phát riêng của luồng (TLAB) trong HotSpot

## 1. Mục tiêu và vấn đề cần giải quyết

Trong Java, biểu thức `new Item(...)` trông rất đơn giản. Nhưng ở cấp JVM, runtime phải cấp phát đủ dung lượng cho object, bảo đảm alignment và trạng thái trường ban đầu, khởi tạo object, rồi trả về một reference hợp lệ. Nếu **mọi** lần tạo object của **mọi thread** đều phải xin một khóa global của heap, tốc độ tạo object trong ứng dụng nhiều luồng có thể bị giới hạn nghiêm trọng.

**HotSpot** thường giải quyết bài toán này bằng hai kỹ thuật gắn kết:

1. **Bump-the-pointer allocation**: trong một vùng heap phù hợp còn chỗ trống liên tục, giữ con trỏ `top` chỉ đầu phần chưa cấp phát. Để cấp object có kích thước đã căn chỉnh `size`, kiểm tra `top + size <= end` rồi dịch `top` lên.
2. **Thread-Local Allocation Buffer (TLAB)**: cấp cho một luồng thực thi một **đoạn heap** dùng để tự cấp phát object theo cơ chế fast path, giảm nhu cầu tranh chấp khi mỗi object được tạo.

Bài học giúp bạn giải thích được:

- TLAB **nằm trong Java heap**, **không phải JVM stack**, **không phải `ThreadLocal<T>`** và không phải “bộ nhớ riêng vĩnh viễn” chứa mọi object của thread.
- Tại sao mỗi allocation trong TLAB thường chỉ cần kiểm tra giới hạn, bump pointer và làm các bước initialization.
- Khi TLAB không đủ chỗ, HotSpot có thể thử **refill/retire TLAB**, cấp phát ngoài TLAB hoặc đi vào **slow path** tùy collector, kích thước object và chính sách runtime.
- Vì sao object sau khi cấp phát trong TLAB vẫn có thể **được thread khác đọc** khi reference được công bố an toàn.
- Vì sao `new` trong source **không bảo đảm** luôn tồn tại một heap allocation tại runtime: JIT escape analysis/scalar replacement có thể loại bỏ allocation.
- Cách quan sát `-XX:+UseTLAB`, `-XX:-UseTLAB` và `-Xlog:gc+tlab=...` trong HotSpot, cùng giới hạn của logging/benchmark.

**Giới hạn phạm vi:** TLAB và bump-pointer fast path là **chi tiết triển khai HotSpot**, không phải yêu cầu mọi Java Virtual Machine implementation trong JVMS phải dùng cùng cấu trúc hoặc cùng flags.

## 2. Từ Java source đến thao tác cấp phát

Mã nguồn:

~~~java
final class Point {
    final int x;
    final int y;

    Point(int x, int y) {
        this.x = x;
        this.y = y;
    }
}

// Nằm bên trong một method:
Point p = new Point(3, 4);
~~~

Đoạn trên **minh họa** class và statement; chưa phải file có `main` độc lập. Với `javac` thông dụng, đường bytecode để tạo object thường gồm:

~~~text
new           #...   // class Point
dup
iconst_3
iconst_4
invokespecial #...   // Point.<init>:(II)V
astore_1
~~~

Diễn giải:

1. `new` là opcode yêu cầu cấp phát object của class tương ứng (sau class resolution và initialization requirements thích hợp).
2. `dup` giữ một reference trên operand stack để biểu thức `new Point(...)` còn reference sau khi constructor được gọi.
3. `iconst_3`/`iconst_4` chuẩn bị tham số.
4. `invokespecial Point.<init>` chạy constructor. **Constructor không phải là chính thao tác cấp phát**: nó khởi tạo object đã được cấp phát.
5. `astore_1` lưu reference vào local-variable slot. **Reference nằm trong frame; object theo mô hình JVM được cấp phát trong heap.**

Đây là mức **bytecode trừu tượng**. Khi interpreter hoặc JIT xử lý `new`, HotSpot có thể chọn nhiều đường allocation khác nhau. Bạn **không thấy TLAB pointer arithmetic trực tiếp qua `javap -c`**, vì `javap` chỉ hiển thị JVM bytecode, không phải machine code của JIT hoặc C++ runtime helpers.

### JVMS bảo đảm gì, HotSpot lựa chọn gì?

| Tầng | Điều được bảo đảm/mô tả | Không nên suy |
| --- | --- | --- |
| Java language/JVMS | Ngữ nghĩa `new`, kiểu reference, object initialization, Java heap là run-time data area dùng chung | Mọi VM phải cấp phát qua TLAB |
| HotSpot allocator | Tối ưu phổ biến: TLAB, bump pointer, allocation slow path | Mỗi object đều đi qua cùng fast path |
| Garbage collector | Quản lý free/used regions, di chuyển hoặc thu hồi objects theo collector | Heap luôn luôn chỉ có một Eden liên tục |
| JIT compiler | Có thể inline, loại bỏ allocations nhờ scalar replacement, điều chỉnh machine code | Thấy `new` là đủ để đếm heap allocations thực |

## 3. Bump-the-pointer: từ `top` tới vị trí object

Giả sử HotSpot có một vùng heap đang sử dụng để cấp phát tuần tự. Đặt:

- `start`: đầu vùng được cấp phát cho allocator.
- `top`: vị trí phần bộ nhớ kế tiếp còn trống.
- `end`: giới hạn vùng có thể cấp phát.
- `size`: kích thước object **đã bao gồm header, fields, padding/alignment** theo layout cụ thể.

~~~text
Đoạn bộ nhớ liên tục được dành cho allocation:

start                        top                         end
  |                           |                           |
  v                           v                           v
  +---------------------------+---------------------------+
  |  các object đã cấp phát   |       phần còn trống       |
  +---------------------------+---------------------------+

Yêu cầu object mới có aligned size = S:

  nếu top + S <= end:
       objectAddress = top
       top = top + S
       chuẩn bị memory/header/object fields theo semantics
       trả reference tới object

  nếu không:
       không thể dùng đường fast path đó
       -> xử lý refill/slow path/đường khác phù hợp
~~~

Đây là **pseudo-code mô hình hóa**, không phải nguyên văn machine code HotSpot. Implementations còn xử lý overflow, alignment, memory ordering, JFR/JVMTI instrumentation, barriers, GC state, zeroing và các yêu cầu khác.

### Ví dụ số học minh họa, không phải object layout thực

Giả sử một vùng có `top = 1_000`, `end = 1_128`, cần cấp object với **aligned size = 24 bytes**:

~~~text
1_000 + 24 = 1_024 <= 1_128  -> cấp được
object bắt đầu ở offset giả định 1_000
new top = 1_024

Lần sau cần 112 bytes:
1_024 + 112 = 1_136 > 1_128 -> không đủ trong vùng này
~~~

Những số 1_000, 1_128, 24, 112 là **số giả định để hiểu thuật toán**. Chúng không nói rằng mọi Java object luôn có header 12/16 bytes hoặc Point luôn chiếm đúng 24 bytes. Layout phụ thuộc JDK, compressed oops/class pointers, object alignment, loại object và cấu hình JVM.

### Vì sao bump pointer nhanh?

Nếu có **phần heap rỗi liên tục**, allocator không cần tìm một block phù hợp trong freelist cho mỗi object. Các thao tác chính có thể được tối ưu thành vài phép so sánh và cộng con trỏ. Tuy nhiên nếu **nhiều threads cùng cập nhật một con trỏ `top` của heap**, VM cần đồng bộ bằng atomic operations, CAS hoặc cơ chế khác; đây là nơi TLAB giúp giảm tranh chấp.

**Không nhầm** bump pointer của allocator với Java object reference: application không được phép cộng trừ `Object` references như con trỏ C. `top` là dữ liệu quản lý cấp phát bên trong HotSpot.

## 4. TLAB là gì và nằm ở đâu?

**TLAB = Thread-Local Allocation Buffer**: một vùng **con của Java heap** mà HotSpot cấp cho một thread thực thi để tạo phần lớn object nhỏ với fast path. Có thể hình dung một heap gồm nhiều đoạn, trong đó một số đoạn đang được “dành quyền cấp phát” cho những threads:

~~~text
                    JAVA HEAP (shared JVM-wide)
+----------------------------------------------------------------+
| Collector-managed regions / allocation space                   |
|                                                                |
| +----------------+  +----------------+  +--------------------+ |
| | TLAB thread A  |  | TLAB thread B  |  | Other heap space   | |
| | start/top/end  |  | start/top/end  |  | large allocations  | |
| +----------------+  +----------------+  | objects from past  | |
|                                          | TLABs, GC regions  | |
|                                          +--------------------+ |
+----------------------------------------------------------------+

Thread A có quyền bump pointer trong TLAB A khi đang sở hữu vùng đó.
Thread B có quyền bump pointer trong TLAB B.
Object tạo trong TLAB vẫn là object trong HEAP và có thể escape.
~~~

**Ba điều quan trọng:**

1. **TLAB không phải vùng nhớ riêng về ngữ nghĩa Java.** Nó chỉ là vùng cấp phát dành cho một thread trong một thời điểm. Khi object đã được công bố đúng cách, thread khác vẫn có thể truy cập nó.
2. **TLAB không phải thread stack**: local `Foo f` thường là reference trong frame; bản thân `new Foo()` là object thuộc heap (trừ khi JIT loại allocation). Object không “chuyển từ stack sang heap” chỉ vì reference escape từ thread cấp phát.
3. **TLAB không phải `ThreadLocal<Foo>`**: `ThreadLocal` là một Java API lưu giá trị theo thread; TLAB là bộ đệm quản lý memory cấp phát trong VM. Hai thứ có tên giống một phần nhưng chức năng khác nhau hoàn toàn.

### Per-thread nhưng không nhất thiết là per-virtual-thread

Trong HotSpot, TLAB gắn với **execution thread context** mà allocator sử dụng. Với **virtual threads**, công việc có thể chạy trên nhiều carrier platform threads theo lịch scheduler. Vì vậy **không được khẳng định cứng** “mỗi virtual thread có một TLAB riêng không đổi trong toàn bộ vòng đời”. Những chi tiết TLAB/OS-thread/carrier là implementation-specific và có thể thay đổi theo release.

## 5. Cơ chế fast path trong TLAB

Mỗi TLAB điển hình có các giới hạn/quản lý như `start`, `top` và `end`. OpenJDK HotSpot giữ những thành phần nội bộ liên quan trong mã `ThreadLocalAllocBuffer`.

### Đường cấp phát khái niệm

~~~text
Java code: new SmallObject(...)
                  |
                  v
             allocation needed?
                  |
                  +-- No (optimized away via EA/scalar replacement)
                  |       -> không cấp heap object thực ở đường này
                  |
                  +-- Yes
                        |
                        v
                 TLAB fast path enabled?
                        |
             +----------+----------+
             |                     |
            Yes                    No
             |                     |
             v                     v
       aligned size fit?       collector/VM
             |                allocation path
       +-----+-----+
       |           |
      Yes          No
       |           |
       v           v
  bump TLAB top   refill TLAB
  init object     or outside-TLAB/slow path
  return ref          |
                      v
                có đủ memory?
                 |        |
                Yes      No
                 |        |
               init/ref   có thể GC/allocate
                          hoặc ném OOME
~~~

Cần phân biệt “allocation fast path” với toàn bộ constructor. Allocator dành memory và khởi tạo trạng thái object theo contract; constructor code có thể thực hiện logic phức tạp, thậm chí ném exception. Một constructor chậm **không chứng minh** allocation bump pointer chậm.

### Mô hình pseudo-code

~~~text
allocateInTLAB(thread, objectSize):
    tlab = thread.currentTLAB
    size = align(objectSize)

    if tlab is usable and size <= tlab.end - tlab.top:
        address = tlab.top
        tlab.top = tlab.top + size
        prepare_memory_and_object_header(address, size)
        return address

    return slowAllocationPath(thread, size)
~~~

**Pseudo-code trên cố ý bỏ bớt chi tiết JVM.** Trong HotSpot thật, allocation paths còn có kiểm soát sampling/observability, zeroing, GC barriers, headroom reserve và các điều kiện khác. `tlab.end` không nhất thiết tương đương dấu `allocation_end` của mọi implementation; không học thuộc internal field layout như Java API contract.

### Vì sao không cần khóa global mỗi object?

Chỉ **thread đang giữ TLAB** cập nhật allocation pointer của TLAB ấy ở fast path, nên các phép bump liên tiếp thường không phải tranh chấp cùng một global pointer với threads khác. VM vẫn cần phối hợp để cấp **TLAB mới**, thu hồi phần chưa dùng, quản lý heap và GC.

Vì vậy phát biểu chính xác là:

> TLAB giảm chi phí tranh chấp **trên nhiều allocation nhỏ ở fast path**, không có nghĩa ứng dụng hoàn toàn không dùng synchronization hay allocation luôn lock-free trên mọi nhánh.

## 6. TLAB đầy: refill, retire, outside-TLAB và slow path

Một TLAB không phải hộp không đáy. Mỗi allocation làm `top` tiến gần `end`. Khi không đủ chỗ cho object tiếp theo, HotSpot có thể thực hiện một số phương án:

1. **Refill TLAB**: xin một vùng heap khác phù hợp, retire vùng cũ và tiếp tục cấp phát từ buffer mới.
2. **Allocate outside TLAB**: với object tương đối lớn so với phần trống/cách tính kích thước TLAB, VM có thể cấp phát object trực tiếp qua heap allocator thay vì bỏ nhiều phần trống ở TLAB.
3. **Slow allocation path**: liên hệ collector, xem xét các vùng cấp phát, phối hợp allocation, thử GC hoặc cơ chế khác; nếu vẫn không đủ memory, có thể kết thúc bằng **`OutOfMemoryError`**.
4. **TLAB resize**: HotSpot điều chỉnh desired TLAB size theo allocation profile và chính sách, thay vì một kích thước cố định toàn ứng dụng.

OpenJDK `ThreadLocalAllocBuffer` theo dõi số lần refill, allocation ngoài TLAB/slow allocations và lượng không gian còn lại bị bỏ khi retire TLAB. Thông tin này giúp phân tích trade-off giữa throughput và fragmentation/waste.

### Tại sao đôi khi cấp ngoài TLAB hiệu quả hơn?

Giả sử vùng TLAB A còn 48 bytes, object mới cần 256 bytes. Nếu buộc retire TLAB ngay, còn 48 bytes có thể không dùng được cho allocation khác bên trong TLAB đó. Với một số điều kiện, VM có thể cấp **object lớn hơn ngoài TLAB** và tiếp tục dùng phần còn lại cho các object nhỏ hơn. Quyết định phụ thuộc **refill waste limit** và chính sách HotSpot, không phải câu lệnh Java được phép chọn trực tiếp.

~~~text
TLAB A:
+-------------------------+----------+
| objects đã cấp          | 48B free |
+-------------------------+----------+

Yêu cầu 256B:
  Lựa chọn có thể xảy ra:
  - outside-TLAB allocation, giữ TLAB cho vật nhỏ
  - retire/refill TLAB rồi allocate bằng TLAB mới
  - slow path phụ thuộc collector, heap state và policy
~~~

Không diễn giải ví dụ thành quy tắc “cứ object lớn hơn 48B thì luôn cấp ngoài TLAB”. Đây chỉ là **một nhánh chính sách**. Kích thước TLAB, alignment, headroom và GC allocation interfaces phức tạp hơn ví dụ.

### Phần TLAB còn thừa đi đâu?

Khi retire một TLAB, có thể còn khoảng trống không đủ cho object đang yêu cầu. HotSpot cần khiến heap vẫn **parsable** đối với collector và heap walk; implementation có thể chèn **filler/dummy objects** hoặc điều chỉnh accounting phù hợp. Phần space chưa dùng được ghi nhận dưới các chỉ số waste tương ứng.

TLAB waste **không phải Java memory leak** theo nghĩa references giữ objects sống vô hạn. Đó là chi phí nội bộ của chiến lược phân vùng cấp phát. Collector có thể thu hồi/tái sử dụng không gian ở thời điểm thích hợp.

### TLAB size có cố định cho mọi thread không?

**Không.** HotSpot có các thông số như desired size, initial size, refills/alloc fraction và giới hạn alignment. VM có thể tự động điều chỉnh theo **allocation rate** của từng execution thread, workload và chính sách GC. Không được kết luận mỗi thread luôn giữ đúng “1 MB TLAB” hoặc cứ tạo 1 triệu object thì phải có chính xác N lần refill.

## 7. Object layout và chi phí thực của một allocation

Một object cần nhiều hơn dữ liệu field. Kích thước allocated thường liên quan:

- **Object header** và metadata cần cho runtime/GC.
- **Instance fields** và cách sắp xếp/alignment.
- **Padding** để thỏa object alignment.
- Với array: thông tin độ dài, phần tử và padding.
- Những lựa chọn HotSpot như compressed oops, compressed class pointers, compact object headers ở các release hỗ trợ và kích thước địa chỉ.

~~~text
Minh họa khái niệm:

+--------------------+
| object header      |
+--------------------+
| field int x        |
| field int y        |
+--------------------+
| optional padding   |
+--------------------+

allocation size = aligned total bytes
~~~

Không dùng một ví dụ kích thước object cụ thể làm quy luật cho mọi JDK/architecture. Ví dụ câu “`new Point(3,4)` luôn là 24 bytes” không đúng nếu cấu hình layout khác nhau. Bạn có thể dùng công cụ phân tích object layout chuyên dụng trong môi trường phù hợp, nhưng kết quả có tính **implementation- và build-specific**.

**Zero initialization:** Java yêu cầu fields có giá trị mặc định đúng trước khi constructor code gán giá trị. HotSpot có thể đạt điều đó bằng zeroed memory từ TLAB refill, zeroing trên allocation path hoặc cách tối ưu tương đương. Không được suy rằng bump pointer “chỉ cần cộng địa chỉ, không bao giờ cần chuẩn bị memory/header”.

## 8. TLAB và GC: phạm vi/collector rất quan trọng

Mô tả phổ biến của HotSpot truyền thống là mỗi thread cấp phát phần lớn object mới trong **young generation/Eden** thông qua TLAB. Nhưng **đừng biến hình ảnh này thành luật mọi collector ở mọi release**:

- Các collectors quản lý heap bằng regions/spaces và các fast allocation interfaces khác nhau.
- Một số objects lớn hoặc có điều kiện đặc biệt có thể đi **outside TLAB**, vào vùng cấp phát đặc biệt, hoặc đường khác.
- Collector có thể di chuyển object sang vùng khác trong khi GC chạy; object không “thuộc TLAB mãi mãi” sau khi được tạo.
- Refilling TLAB cần phối hợp với nguồn cấp phát của collector; việc đó có thể tạo GC pressure hoặc request GC khi heap không đủ space.

### TLAB không phải vùng “được miễn GC”

Một object cấp phát trong TLAB:

1. Là **Java heap object** nếu được materialize.
2. Có thể trở nên unreachable sau khi không còn references sống.
3. Có thể bị thu hồi hoặc di chuyển bởi GC theo collector.
4. Có thể được truy cập từ thread khác nếu reference được chia sẻ theo JMM.

Việc TLAB cấp phát nhanh **không** làm object thuộc “stack-local heap không bao giờ quét”. Collector vẫn phải đảm bảo tính đúng của object graph, roots, barriers và metadata.

### TLAB và ThreadLocal khác nhau

| Khái niệm | TLB? | Mục tiêu | Object có thể chia sẻ? |
| --- | --- | --- | --- |
| **TLAB** | Không phải CPU TLB | Tối ưu **cấp phát heap** theo thread context | **Có**, sau khi reference được chia sẻ |
| **`ThreadLocal<T>`** | Không phải TLAB | Lưu **value gắn với Java thread** qua các calls | Value vẫn là object Java và có thể escape |
| **CPU TLB (Translation Lookaside Buffer)** | Phần cứng MMU | Cache ánh xạ virtual → physical addresses | Không phải cơ chế cấp phát Java |
| **JVM stack** | Không phải heap TLAB | Chuỗi frames, locals/operand stacks | Frames theo thread riêng |

Vì các chữ viết tắt tương tự nhau, cần đọc đầy đủ: **Thread-Local Allocation Buffer** khác **Translation Lookaside Buffer** của CPU.

## 9. Escape analysis: thấy `new` nhưng không phải lúc nào cũng có allocation

JIT compiler có thể phân tích một object **có escape khỏi method/thread không** và có thể thay thế object bằng các giá trị trường riêng lẻ (**scalar replacement**) hoặc loại bỏ thao tác allocation khi không cần object materialized.

Ví dụ (đoạn method minh họa):

~~~java
static int compute(int input) {
    Point point = new Point(input, input + 1);
    return point.x + point.y;
}
~~~

Nếu không có observable escape, JIT có thể biến đường thực thi thành arithmetic tương đương:

~~~text
compute(input):
    result = input + (input + 1)
    return result
~~~

Ở mức **bytecode `javap`**, `new Point` vẫn có thể hiện diện. Ở mức optimized machine code, allocation **có thể không tồn tại**. Nhưng đây là **khả năng tối ưu**, không phải bảo đảm JIT luôn loại được mọi allocation của một object không escape.

**Đặc biệt trên HotSpot:** các tài liệu hiệu năng của Oracle mô tả scalar replacement là tối ưu loại bỏ heap allocation; **không nên khẳng định HotSpot mặc định “chuyển tất cả non-escaping objects sang stack”**. Escape analysis có thể giúp loại allocation hoàn toàn; nếu vẫn cần materialize, allocator có thể dùng TLAB hoặc đường khác.

### Phân biệt kết quả JIT và diễn giải source

| Source | Khả năng HotSpot tối ưu | Không bảo đảm |
| --- | --- | --- |
| `new Point(a,b)` chỉ đọc fields rồi return sum | Scalar replacement/loại allocation | Mỗi lần gọi đều được JIT compile ngay |
| `new Point(a,b)` và trả reference ra ngoài | Có thể cần materialize object | Luôn phải cấp ở TLAB; có thể đường khác |
| `new HugeArray[...]` | Có thể allocation ngoài TLAB/large-object path | Mọi array đều vào TLAB |
| `new Foo()` trong loop | Có thể nhiều allocations hoặc bị tối ưu | Số lần `new` source = số TLAB allocations chính xác |

## 10. Chương trình Java 17+: cấp phát từ hai threads

Ví dụ dưới đây giúp phân biệt **object trên heap dùng chung** với **quyền cấp phát có tính thread-local**. Nó có output xác định và **không** yêu cầu truy cập cấu trúc TLAB nội bộ.

Tạo file **`AllocationLab.java`**:

~~~java
public class AllocationLab {
    static final class Item {
        final int number;

        Item(int number) {
            this.number = number;
        }
    }

    private static long buildAndSum(int start) {
        Item[] items = new Item[2_000];

        for (int i = 0; i < items.length; i++) {
            items[i] = new Item(start + i);
        }

        long total = 0;
        for (Item item : items) {
            total += item.number;
        }
        return total;
    }

    public static void main(String[] args) throws InterruptedException {
        long[] result = new long[2];

        Thread first = new Thread(() -> result[0] = buildAndSum(0));
        Thread second = new Thread(() -> result[1] = buildAndSum(2_000));

        first.start();
        second.start();
        first.join();
        second.join();

        System.out.println("first=" + result[0]);
        System.out.println("second=" + result[1]);
        System.out.println("sum=" + (result[0] + result[1]));
    }
}
~~~

Biên dịch và chạy:

~~~bash
javac -g AllocationLab.java
java AllocationLab
~~~

**Stdout chính xác khi chương trình chạy bình thường:**

~~~text
first=1999000
second=5999000
sum=7998000
~~~

Giải thích:

- First thread cộng các số nguyên 0..1999: `(0 + 1999) * 2000 / 2 = 1_999_000`.
- Second thread cộng 2000..3999: `(2000 + 3999) * 2000 / 2 = 5_999_000`.
- Main `join` cả hai threads trước khi đọc `result[]`; do đó thứ tự output chỉ phụ thuộc main và các writes của workers đã hoàn tất.
- `Item[]` và từng `Item` có **ngữ nghĩa objects trên heap**. Trong HotSpot, TLAB có thể tối ưu nhiều allocations này, nhưng **stdout không đo TLAB refills hay số bytes đã cấp**.
- Cũng không được tự suy rằng mọi `new Item` thực sự tạo một allocation độc lập: JIT optimization tùy code shape và execution profile.

### So sánh chạy với TLAB bật và tắt

Trên HotSpot (nếu bản JVM hỗ trợ các flags), thử:

~~~bash
java -XX:+UseTLAB AllocationLab
java -XX:-UseTLAB AllocationLab
~~~

Cả hai lượt phải giữ **ngữ nghĩa chương trình** và in đúng ba dòng giá trị nói trên trong điều kiện chạy bình thường. Tuy nhiên:

- Khác biệt thời gian thực thi **không nhất thiết phản ánh lợi ích TLAB**, vì chương trình nhỏ chịu ảnh hưởng JIT warmup, startup, scheduling và escape-analysis.
- `-XX:-UseTLAB` chỉ thay đổi chiến lược cấp phát, **không** đổi việc objects thuộc Java heap.
- Không dùng output đúng/sai của chương trình để tuyên bố HotSpot đã chọn collector path nào cho mỗi object.

Để benchmark nghiêm túc, sử dụng **JMH**, chuẩn bị warmup/forks, kiểm soát GC, allocation rate, hardware và profiling. Không rút ra kết luận hiệu năng từ một phép lấy thời gian thủ công duy nhất.

## 11. Một ví dụ thứ hai: JIT có thể loại allocation của object không escape

Tạo file **`ScalarCandidate.java`**:

~~~java
public class ScalarCandidate {
    static final class Pair {
        final int left;
        final int right;

        Pair(int left, int right) {
            this.left = left;
            this.right = right;
        }
    }

    static int compute(int value) {
        Pair pair = new Pair(value, value + 1);
        return pair.left + pair.right;
    }

    public static void main(String[] args) {
        System.out.println("result=" + compute(7));
    }
}
~~~

~~~bash
javac ScalarCandidate.java
java ScalarCandidate
javap -c -p ScalarCandidate
~~~

**Stdout của chương trình (không tính phần output `javap`):**

~~~text
result=15
~~~

`javap` vẫn có thể hiển thị bytecode `new ScalarCandidate$Pair` bên trong `compute`. Nhưng khi method đủ “nóng” để JIT tối ưu, compiler **có thể** scalar-replace Pair và loại cấp phát ở machine code.

**Không thể chứng minh scalar replacement chỉ bằng output `result=15` hoặc bằng `javap -c`**. Để đo allocation thực tế, cần profiler, JFR hoặc tooling được cấu hình phù hợp và xét compilation state. Không khẳng định method đơn giản này chắc chắn được optimized sau một lần gọi.

## 12. Quan sát TLAB bằng HotSpot flags và unified logging

Oracle JDK/HotSpot hiện đại hỗ trợ:

| Flag hoặc công cụ | Công dụng | Lưu ý |
| --- | --- | --- |
| `-XX:+UseTLAB` | Bật Thread-Local Allocation Buffers | Trên HotSpot thường **bật mặc định** |
| `-XX:-UseTLAB` | Tắt cơ chế này để so sánh/điều tra | Có thể làm allocation chậm; không phải cấu hình production mặc định được khuyến nghị |
| `-Xlog:gc+tlab=trace` | Yêu cầu logging chi tiết các sự kiện/thống kê TLAB nếu tag/mức được JVM hỗ trợ | Output phụ thuộc collector, workload, version; rất nhiều log |
| `-Xlog:gc+tlab=debug` | Thử mức log ít chi tiết hơn | Không bảo đảm có dòng log nếu chưa có sự kiện/mốc GC tương ứng |
| `-XX:+PrintFlagsFinal` | Liệt kê flags HotSpot và giá trị hiện hành | Không đo các allocations cụ thể |
| **JFR** | Thu thập allocation events tùy cấu hình | Event selection/sampling phải được hiểu đúng; không mặc định đếm đầy đủ từng object |
| **JMH** | Benchmark JVM có warmup/forks | Không tự đo TLAB chỉ từ một con số ns/op |

Các lệnh thử trên HotSpot JDK 17+:

~~~bash
java -Xlog:help
java -XX:+PrintFlagsFinal -version
java -Xlog:gc+tlab=trace AllocationLab
~~~

**Chú ý:** flag/logging không phải API portable của Java language. Trên JVM không phải HotSpot, lệnh có thể bị từ chối. Khi `-Xlog:gc+tlab=trace` tạo nhiều output, đừng lẫn log diagnostic với ba dòng stdout của chương trình.

Để lọc `UseTLAB` trong terminal:

~~~bash
# Linux/macOS shell:
java -XX:+PrintFlagsFinal -version 2>&1 | grep UseTLAB
~~~

PowerShell/Windows:

~~~powershell
java -XX:+PrintFlagsFinal -version 2>&1 | Select-String "UseTLAB"
~~~

### Diễn giải thống kê TLAB

Tùy phiên bản HotSpot, log của `gc+tlab` có thể nhắc tới:

- **Refills**: bao nhiêu lần cần đổi TLAB.
- **Slow allocs**: những allocations cần nhánh chậm theo cách VM thống kê.
- **Refill waste**: dung lượng còn thừa bị bỏ khi TLAB retire/refill.
- **GC waste**: space không dùng hết khi GC thu thập thống kê/đóng TLAB phù hợp.
- **Desired size**: dung lượng TLAB mà VM đang nhắm tới theo policy.

Không suy rằng một dòng `refills: 3` nghĩa JVM chỉ tạo **ba objects**. Mỗi TLAB chứa nhiều allocations, và ngoài TLAB có thể còn đường khác. TLAB statistics cũng có thể được in **theo GC/event** thay vì một con số tổng đời process, và JIT elimination có thể làm giảm allocations quan sát.

### JFR và allocation events

Java Flight Recorder có các event names liên quan allocation, chẳng hạn **`jdk.ObjectAllocationInNewTLAB`** và **`jdk.ObjectAllocationOutsideTLAB`** trên các bản JDK hỗ trợ. Cấu hình recording quyết định event nào được bật, có sampling hoặc threshold gì. Đừng diễn giải số events như “chính xác bằng số `new` source đã chạy” nếu chưa kiểm tra setting.

Một lựa chọn để khám phá event support:

~~~bash
jfr help
java -XX:StartFlightRecording=duration=10s,filename=allocation.jfr AllocationLab
~~~

**Với `AllocationLab` chạy quá ngắn**, recording 10 giây có thể không đủ workload dài để quan sát nhiều allocation samples. Lệnh này chỉ minh họa cách bật recording trên JDK có JFR; không bảo đảm tất cả event loại allocation tự động được thu và không dùng để kết luận TLAB size. Dùng một workload đủ dài/đã warmup và cấu hình event phù hợp nếu làm profiling thực sự.

## 13. Khi nào Fast Path không đại diện cho toàn bộ allocation?

Các điều kiện có thể khiến allocation **không** đi qua bump pointer nhanh trong TLAB:

1. **Object quá lớn** hoặc không phù hợp TLAB size/refill policy.
2. **TLAB không đủ chỗ**, cần refill/retire.
3. **Collector-specific slow path**: vùng cấp phát không sẵn, cần phối hợp GC/refill hoặc allocation ngoài vùng mặc định.
4. **Instrumentation/profiling**: JVMTI/JFR sampling và các yêu cầu observability có thể tác động allocation path.
5. **JIT chưa compile hoặc đã deoptimize**: interpreter, C1, C2 có thể có đường thực thi khác.
6. **Escape analysis loại allocation**: khi không còn allocation thực, “fast vs slow” không còn áp dụng cho object đó.
7. **VM flags**: `UseTLAB` bị tắt hoặc chính sách collector khác.

Đừng rút ra từ số byte còn trống của TLAB rằng “vùng heap không còn memory”. TLAB có thể hết chỗ khi heap tổng thể còn rất nhiều capacity; refill giúp lấy buffer mới từ phần heap thích hợp. Ngược lại, còn vài byte trong TLAB không bảo đảm JVM có thể đáp ứng object rất lớn.

## 14. “Con trỏ” của HotSpot không phải con trỏ của mã Java

Bump pointer `top` là một address/offset mà **VM allocator quản lý nội bộ**. Java source không có toán tử pointer arithmetic như C/C++. Java references là giá trị có kiểu và các phép toán hợp lệ theo JVM, có thể trải qua GC relocation/barriers.

Quan sát:

~~~java
Object a = new Object();
Object b = new Object();
System.out.println(a == b);
~~~

Khi đặt trong một chương trình có `main`, dòng này in `false` vì hai lần `new Object()` tạo hai identities khác nhau theo ngữ nghĩa Java, dù VM có thể optimize trong trường hợp cụ thể mà không đổi kết quả. **Không suy khoảng cách địa chỉ giữa a và b** từ `==`. Cũng không đọc `System.identityHashCode` rồi coi đó là memory address.

### TLAB không phá vỡ Java Memory Model

Nếu Thread A tạo và công bố object cho Thread B:

~~~text
Thread A:
  - allocate object (có thể qua TLAB A)
  - initialize fields
  - publish reference theo cơ chế thread-safe

Thread B:
  - nhận reference
  - truy cập cùng object
~~~

TLAB chỉ tối ưu **chỗ cấp phát ban đầu**. Nó không cho phép B tự động đọc fields mà A đã ghi nếu cách publication/synchronization không đúng quy tắc. Hãy dùng `volatile`, `synchronized`, concurrent collections, final field semantics hoặc các cơ chế happens-before thích hợp.

TLAB cũng không bảo đảm object chỉ được phép dùng từ thread cấp phát; Java heap **chia sẻ giữa các JVM threads**, theo JVMS `2.5.3.

## 15. Tối ưu thực tế: khi nào cần để ý TLAB?

Những câu hỏi có giá trị trước khi can thiệp VM flags:

- Allocation rate có thật sự cao? GC pause, object churn hay CPU bị giới hạn ở đâu?
- Object lớn hay nhiều object nhỏ? Có pattern allocation đột biến theo threads?
- Profiling có cho thấy TLAB refill/waste hoặc outside-TLAB allocations đáng kể?
- JIT đã warmup và allocation có bị scalar-replaced không?
- Bản Java, GC và host architecture có phù hợp với assumption benchmark không?
- Vấn đề thực tế là allocation throughput, GC memory pressure hay synchronization trên shared object?

**Không nên tự động tắt TLAB khi thấy một ít “waste”.** Trade-off thường là: cho mỗi thread một vùng allocation riêng để tránh tranh chấp, đổi lại có thể có unused tail space/refill overhead. Hiệu quả thực cần đánh giá bằng workload thực, không bằng một thông số đơn lẻ.

### Mô hình so sánh

| Cấp phát qua TLAB fast path | Cấp phát ngoài TLAB / slow path |
| --- | --- |
| Có vùng per-execution-thread còn đủ chỗ | TLAB hết chỗ, object lớn, TLAB disabled hoặc chính sách khác |
| Bump pointer cục bộ, thường ít contention | Có thể cần coordination/CAS, refill, collector-specific logic |
| Fast path có thể được inline tối ưu | Thường nhiều checks và bookkeeping hơn |
| Vẫn cần object initialization và GC correctness | Cũng phải tuân thủ object initialization và GC correctness |
| Vẫn là object trên Java heap | **Cũng là object trên Java heap** |

Không diễn giải bảng thành đo đạc tốc độ cố định, ví dụ “TLAB luôn nhanh hơn N lần”; JDK, collector, CPU, kích thước objects và contention đều ảnh hưởng.

## 16. Những lỗi thường gặp

| Hiểu nhầm | Cách hiểu đúng |
| --- | --- |
| “TLAB là một phần JVM stack của thread.” | TLAB là **buffer trong Java heap** dành cho thread context cấp phát. |
| “Object cấp phát trong TLAB không thể được thread khác truy cập.” | Vẫn có thể chia sẻ reference và truy cập object theo Java Memory Model. |
| “`ThreadLocal<T>` dùng TLAB để giữ value riêng.” | ThreadLocal là API value per Java thread, không đồng nghĩa TLAB. |
| “TLAB là CPU TLB.” | TLAB = Thread-Local Allocation Buffer; CPU TLB là Translation Lookaside Buffer. |
| “TLAB nghĩa mỗi `new` không cần bất kỳ synchronization ở mọi nhánh.” | TLAB fast path giảm contention; refill/slow path/GC vẫn cần phối hợp. |
| “Cứ đầy TLAB là JVM hết heap.” | Có thể refill từ heap khi còn nguồn memory. |
| “Object lớn luôn nằm trong TLAB.” | Có thể cấp outside TLAB hoặc qua đường khác. |
| “TLAB chỉ cấp phát objects trong young Eden ở mọi JVM mọi GC.” | Nhiều collector có chính sách/regions khác nhau; không phải luật JVMS. |
| “TLAB size cố định cho toàn bộ luồng.” | HotSpot có adaptive sizing và thresholds. |
| “Bump pointer chỉ tăng `top`; không có object header/zeroing.” | Cấp phát hợp lệ còn có preparation/header/alignment và GC requirements. |
| “Thấy `new` trong `javap` thì chắc chắn có một heap allocation.” | JIT có thể scalar-replace hoặc loại allocations. |
| “Escape analysis HotSpot chuyển mọi object không escape lên stack.” | HotSpot thường dùng scalar replacement để loại allocation; không hứa stack allocation tổng quát. |
| “Tắt TLAB sẽ làm Java source output khác.” | `-XX:-UseTLAB` đổi strategy allocation, không đổi Java semantics. |
| “Chỉ cần một lần đo `System.nanoTime` là biết TLAB giúp bao nhiêu.” | Phải xét warmup, JIT, GC, benchmark framework và workload. |
| “`System.identityHashCode` là địa chỉ object sau cấp phát.” | Không; identity hash không phải địa chỉ native. |
| “Một virtual thread luôn giữ một TLAB riêng không đổi.” | Cơ chế gắn với execution/carrier threads tùy HotSpot; không nên tuyệt đối hóa. |
| “TLAB waste là memory leak không thể GC.” | Thường là internal allocation fragmentation/accounting, không tương đương Java reference leak. |
| “Bump pointer tồn tại trong JVMS như một opcode.” | Không; đó là chi tiết implementation phía dưới bytecode `new`. |

## 17. Câu hỏi tự kiểm tra

**Câu 1.** TLAB thuộc JVM stack hay heap?  
**Đáp án:** Thuộc **Java heap** theo HotSpot allocation strategy; nó là vùng **tạm được thread sử dụng để cấp object**, không phải call stack.

**Câu 2.** Fast path bump pointer cần hai thông tin quan trọng nào?  
**Đáp án:** Vị trí hiện tại `top` và giới hạn `end`, cùng object aligned size để kiểm tra còn đủ chỗ và cập nhật `top`.

**Câu 3.** Vì sao TLAB giảm contention trong nhiều luồng?  
**Đáp án:** Các luồng dùng các allocation pointers cục bộ cho nhiều objects nhỏ, thay vì tranh chấp một global pointer cho mọi object.

**Câu 4.** Khi TLAB còn 48B mà object cần 256B, VM có chắc chắn ném OOME không?  
**Đáp án:** Không. VM có thể refill TLAB hoặc allocate outside TLAB nếu heap có nguồn cấp phát phù hợp.

**Câu 5.** Có thể chia sẻ object được cấp từ TLAB A cho Thread B không?  
**Đáp án:** Có. Object thuộc Java heap; cần publication/synchronization đúng JMM để bảo đảm visibility và safety khi chia sẻ mutable data.

**Câu 6.** Có phải mọi `new` luôn đi qua TLAB?  
**Đáp án:** Không. Có outside-TLAB allocation, slow path, TLAB disabled hoặc JIT có thể loại allocation.

**Câu 7.** Vì sao `javap -c` không chứng minh TLAB allocation đang dùng?  
**Đáp án:** `javap` hiển thị JVM bytecode, không hiển thị internal allocation path trong HotSpot/JIT machine code.

**Câu 8.** Có cần interpreter hay compiler gọi Java constructor trước khi memory cho object đã được cấp hợp lệ không?  
**Đáp án:** Constructor chỉ có thể chạy sau khi object đã được tạo/chuẩn bị phù hợp theo semantics; allocation và constructor invocation là các bước phân biệt.

**Câu 9.** `UseTLAB` là yêu cầu của JVMS cho mọi JVM implementation?  
**Đáp án:** Không. Đây là flag/technique của HotSpot; JVM khác có thể chọn allocator khác.

**Câu 10.** `ScalarCandidate.compute(7)` luôn tạo một Pair object thật?  
**Đáp án:** Không thể bảo đảm: JIT có thể scalar-replace nếu đủ điều kiện, nhưng cũng có thể tạo object tùy compilation state.

**Câu 11.** JFR ghi 100 allocation events có nghĩa source chỉ chạy 100 `new`?  
**Đáp án:** Không. Event configuration/sampling và allocation elimination làm số event không đồng nghĩa số biểu thức `new` thực thi.

**Câu 12.** Có nên đổi `-XX:-UseTLAB` trên production chỉ vì thấy một phần refill waste không?  
**Đáp án:** Không. Cần đo workload thực, GC, throughput và contention trước khi can thiệp allocation policy.

## 18. Nguồn chính thức để tìm hiểu sâu

- [JVMS Java SE 25 — `2.5.3 Heap](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.5.3): Java heap chứa instances/arrays dùng chung, không quy định thuật toán cấp phát TLAB.
- [JVMS Java SE 25 — `6.5 Instructions](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html): opcode `new`, `newarray`, `anewarray` và ngữ nghĩa khởi tạo/lệnh JVM.
- [Oracle JDK 25 — `java` launcher command](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html): documented HotSpot flags `-XX:+UseTLAB` và `-XX:-UseTLAB`.
- [OpenJDK HotSpot — `threadLocalAllocBuffer.hpp`](https://github.com/openjdk/jdk/blob/master/src/hotspot/share/gc/shared/threadLocalAllocBuffer.hpp): interface và state nội bộ TLAB.
- [OpenJDK HotSpot — `threadLocalAllocBuffer.cpp`](https://github.com/openjdk/jdk/blob/master/src/hotspot/share/gc/shared/threadLocalAllocBuffer.cpp): refill, resize, accounting, waste và logging.
- [OpenJDK HotSpot — `memAllocator.cpp`](https://github.com/openjdk/jdk/blob/master/src/hotspot/share/gc/shared/memAllocator.cpp): các đường allocation inside/outside TLAB, slow path.
- [Oracle — Memory Management in Java HotSpot VM](https://www.oracle.com/technetwork/java/javase/tech/memorymanagement-whitepaper-1-150020.pdf): giải thích lịch sử bump-pointer allocation và lý do TLAB giảm contention; nội dung collector của whitepaper cũ không nên coi là hiện trạng mọi JDK.
- [Oracle — Java HotSpot VM Performance Enhancements](https://docs.oracle.com/en/java/javase/22/vm/java-hotspot-virtual-machine-performance-enhancements.html): escape analysis và scalar replacement, các tối ưu allocation.
- [OpenJDK JMH](https://openjdk.org/projects/code-tools/jmh/): framework benchmark JVM có warmup/forks đúng phương pháp.
- [JDK 25 — Java Flight Recorder](https://docs.oracle.com/en/java/javase/25/jfapi/): công cụ và API flight recording, quan sát events khi được bật.

**Tóm tắt một câu:** HotSpot thường cấp object nhanh bằng **bump pointer trong TLAB — một đoạn của heap được trao cho một execution thread**, nên phần lớn allocations nhỏ không phải tranh chấp global allocation pointer; khi hết chỗ hoặc không đủ điều kiện VM chuyển sang refill/outside-TLAB/slow path, còn GC, object initialization và Java Memory Model vẫn phải được tuân thủ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hotspot-objects-003">Đầu đối tượng, dữ liệu trường, căn chỉnh và phần đệm</a>

<details>
<summary>Xem chi tiết</summary>

# Đầu đối tượng, dữ liệu trường, căn chỉnh và phần đệm trong HotSpot JVM

Một **object Java** không chỉ chiếm đúng tổng số byte của các field được khai báo trong source. Trong một cách bố trí phổ biến của **HotSpot JVM**, một instance trên Java heap gồm **object header** (đầu đối tượng), **instance field data** (dữ liệu các trường thuộc đối tượng) và có thể có **padding** để đáp ứng các ràng buộc **alignment**. Với array, header còn cần lưu **độ dài mảng**. Cách bố trí thật phụ thuộc JVM, phiên bản JDK, kiến trúc 32/64-bit, compressed references, tùy chọn căn chỉnh và các cơ chế như **Compact Object Headers**.

Bài học tập trung vào **biểu diễn vật lý trong HotSpot**, không nên nhầm với hợp đồng trừu tượng của Java Language Specification (JLS): đặc tả Java quy định ngữ nghĩa field, object, array và memory model, nhưng **không buộc mọi JVM dùng một số byte header hoặc một thứ tự field giống nhau**.

Mục tiêu sau khi đọc:

1. Giải thích **mark word**, **class pointer/klass word**, và **array length** trong object header.
2. Phân biệt field instance, field static, object reference và object được tham chiếu.
3. Tính được các ví dụ object size minh họa khi đã **nêu rõ giả định**.
4. Hiểu **internal padding**, **external/tail padding**, alignment và ảnh hưởng đến bộ nhớ khi có hàng triệu object.
5. Biết **compressed oops** khác **compressed class pointers** như thế nào.
6. Dùng Java 17+ và công cụ như **Java Object Layout (JOL)** hoặc `Instrumentation.getObjectSize` để quan sát, thay vì đoán từ source.
7. Nhận diện các cấu hình HotSpot mới có **compact headers** để không áp dụng cứng sơ đồ cũ.

## 1. Mô hình tổng quát của một object

Sơ đồ **minh họa** cho object thông thường trên một HotSpot 64-bit với kiểu header cổ điển:

```text
Địa chỉ tăng dần →
┌───────────────────────────────────┐
│ Object header                     │
│   mark word                       │
│   klass / class pointer           │
├───────────────────────────────────┤
│ Instance fields                   │
│   primitive values                │
│   reference values                │
│   ...                             │
├───────────────────────────────────┤
│ Padding nếu cần                   │
└───────────────────────────────────┘
```

Với một mảng, có thêm thông tin số phần tử:

```text
┌───────────────────────────────────┐
│ Header: mark + class information  │
│ Array length (int)                │
├───────────────────────────────────┤
│ Array elements [0 .. length-1]    │
├───────────────────────────────────┤
│ Padding nếu cần                   │
└───────────────────────────────────┘
```

**Đây không phải layout tuyệt đối.** HotSpot có thể sắp xếp field theo chiến lược riêng, dùng compact header, nén các con trỏ khác nhau hoặc thay đổi cách đặt metadata. Sơ đồ chỉ giúp phân chia trách nhiệm.

### Thuật ngữ quan trọng

| Thuật ngữ | Diễn giải |
| --- | --- |
| **object header** | Phần thông tin để VM quản lý object, định danh kiểu và trạng thái liên quan |
| **mark word** | Word metadata về trạng thái object; cách mã hóa thay đổi theo JDK, khóa đồng bộ, GC và header mode |
| **klass word / class pointer** | Thông tin định danh runtime class/metadata class của object trong header cổ điển |
| **instance field data** | Giá trị các instance fields, gồm primitive values hoặc references |
| **internal padding / gap** | Byte trống/chưa dùng giữa các thành phần do field alignment/layout |
| **external/tail padding** | Byte chèn vào cuối object để tổng kích thước đạt object alignment |
| **object alignment** | Ràng buộc kích thước/địa chỉ object theo đơn vị căn chỉnh triển khai |
| **shallow size** | Kích thước của **chính object**, không đi theo các references để tính object graph |
| **retained size** | Bộ nhớ có thể giải phóng nếu object là dominator của các objects khác, tùy công cụ/phân tích heap |

**Reference không phải object data của object đích.** Nếu `Node` có field `Node next`, body của `Node` chứa một **reference value**; các field của object `next` nằm trong **object khác**. Vì vậy không thể cộng toàn bộ chuỗi node vào shallow size của node đầu.

## 2. Object header trong HotSpot: thành phần và nhiệm vụ

### 2.1. Mark word

Trong mô hình header HotSpot truyền thống, **mark word** giữ những thông tin phục vụ quản lý trạng thái object, liên quan đến:

- **Identity hash code**, khi nó được tính/ghi nhận theo chiến lược VM.
- **Locking/synchronization state**, tùy cơ chế monitor và phiên bản HotSpot.
- **GC age** hoặc thông tin đánh dấu/trạng thái thích hợp.
- Một số **bit trạng thái** khác; bit layout có thể khác ở các thế hệ JDK.

Mark word **không đơn giản là một trường Java `int hash`** mà lập trình viên thấy qua reflection. Nó là dữ liệu nội bộ của JVM. Mọi đối tượng có thể mang các thông tin cần thiết để triển khai `Object.hashCode()`/identity semantics và `synchronized`, nhưng **không phải mỗi object ngay khi cấp phát đều có một identity hash đã được tính sẵn**.

```java
Object x = new Object();
int h = System.identityHashCode(x);
```

Đây là đoạn mã minh họa trong method; `System.identityHashCode` yêu cầu identity hash của object và có thể làm thay đổi trạng thái metadata/đường lưu thông tin phù hợp với VM. **Giá trị hash không phải địa chỉ bộ nhớ** và không được bảo đảm là duy nhất cho tất cả objects.

### 2.2. Klass pointer / class word

Ở layout truyền thống, header còn chứa **thông tin trỏ tới runtime class metadata** (thường gọi `klass` pointer hoặc class word). Điều này giúp JVM biết:

- Object thuộc runtime class/interface/array type nào.
- Kích thước/layout thích hợp của object và đường truy cập metadata.
- Những thông tin hỗ trợ method dispatch, type checks, GC scanning và nhiều tác vụ nội bộ.

Đây **không phải nhất thiết là Java field `Class<?> clazz` lưu ngay trong object**. `obj.getClass()` cho ra **`java.lang.Class` mirror** thích hợp, nhưng VM class metadata và mirror là những cấu trúc liên quan mà không nên đồng nhất về địa chỉ/layout.

Hai instances cùng class có thể dùng chung class metadata, nhưng **mỗi instance có phần đầu riêng** theo header mode đang dùng.

### 2.3. Kích thước header phụ thuộc mode

Một cấu hình thường dùng để giảng giải **HotSpot 64-bit JDK 17**, với compressed class pointers bật, là:

```text
Normal instance (classic header):
offset 0..7   : mark word      8 byte
offset 8..11  : class pointer  4 byte (compressed)
---------------------------------------
Total header                   12 byte
```

Nếu **không nén class pointer**, ở mode cổ điển class word có thể rộng **8 byte**, và header thường là **16 byte** trong một HotSpot 64-bit tương ứng.

**Những con số này là ví dụ triển khai theo cấu hình, không phải kích thước object bắt buộc của mọi Java 17/JDK.** Heap alignment, architecture, header mode và các thành phần bổ sung còn quyết định shallow size cuối cùng.

### 2.4. Compact Object Headers: đừng cố định 12 hoặc 16 byte cho mọi JDK mới

Dự án **OpenJDK Lilliput** nghiên cứu và triển khai các phương án giảm overhead của object headers. Trong các JDK mới:

- **JDK 24** giới thiệu **Compact Object Headers (Experimental)** theo **JEP 450**.
- **JDK 25** đưa tính năng này sang dạng sản phẩm có thể bật theo **JEP 519**.
- Với compact headers trên HotSpot 64-bit được hỗ trợ, thông tin class và metadata đánh dấu có thể được kết hợp vào một cấu trúc header **8 byte**, thay vì hai phần 8 + 4 hoặc 8 + 8 byte của bố trí cổ điển.
- Tùy chọn `-XX:+UseCompactObjectHeaders` và trạng thái mặc định/hỗ trợ có thể thay đổi theo JDK/vendor; **hãy kiểm tra JDK hiện dùng** thay vì mặc định luôn bật hoặc tắt.

Vì vậy phát biểu **“mọi Java object luôn có header 12 byte”** vừa không đúng với các mode 32-bit/không nén, vừa không còn áp dụng phổ quát cho HotSpot có compact headers.

Tác dụng của header nhỏ là giảm overhead trên **số lượng object rất lớn**, tăng mật độ heap/cache và đôi khi cải thiện performance. Nó **không làm biến mất** instance fields, object alignment hay các quy tắc ngữ nghĩa của Java.

## 3. Instance fields: giá trị nào thực sự nằm trong object?

Xét class:

```java
class Record {
    byte active;
    int count;
    long total;
    Object owner;
    static int globalCounter;
}
```

Đây là **class minh họa có thể đặt trong `Record.java`**, không phải một chương trình với `main`. Bốn trường `active`, `count`, `total`, `owner` là **instance fields**; `globalCounter` là **static field**.

| Trường | Có thuộc instance body không? | Thông tin |
| --- | --- | --- |
| `byte active` | Có | Giá trị primitive `byte` |
| `int count` | Có | Giá trị primitive `int` |
| `long total` | Có | Giá trị primitive `long` |
| `Object owner` | Có | **Reference** tới object khác, không chứa toàn bộ object đó |
| `static int globalCounter` | **Không** | Trạng thái thuộc runtime class, không cộng một lần cho mỗi instance |

**Không được nhầm `static` với “nằm trong stack”.** Static fields gắn với runtime class và cơ chế lưu trữ của JVM, không phải biến instance, cũng không bị sao chép thành một field riêng trong mỗi object.

### 3.1. Kích thước primitive theo ngữ nghĩa và theo layout

| Kiểu | Độ rộng dữ liệu primitive của Java | Kích thước field phổ biến trong HotSpot |
| --- | --- | --- |
| `boolean` | Boolean logic, không có quy định JLS buộc field là 1 byte | Thường 1 byte cho field; arrays/VM mode có quy tắc triển khai |
| `byte` | 8 bit | 1 byte |
| `short` | 16 bit | 2 byte |
| `char` | 16 bit unsigned UTF-16 code unit | 2 byte |
| `int` | 32 bit | 4 byte |
| `float` | 32 bit | 4 byte |
| `long` | 64 bit | 8 byte |
| `double` | 64 bit | 8 byte |
| Reference | Tham chiếu tới object hoặc null | Thường 4 byte khi compressed oops, có thể 8 byte khi uncompressed |

Bảng “thường” nói về **cách HotSpot đặt dữ liệu**. Nó không khẳng định tổng shallow size chỉ cần cộng các con số ấy: còn header, field alignment, object alignment, inherited fields và layout choices.

### 3.2. Không phải thứ tự field trong RAM giống thứ tự source

Ví dụ:

```java
class Mixed {
    byte a;
    long b;
    byte c;
    int d;
}
```

Các source fields xuất hiện theo thứ tự `a, b, c, d`, nhưng HotSpot có thể **sắp xếp lại offsets** hoặc tận dụng các gaps để giảm padding. Không có lời hứa Java language rằng `b` bắt buộc bắt đầu ngay sau `a` ở bộ nhớ vật lý. Những thao tác `getfield`/`putfield` vẫn đúng vì JVM biết layout runtime của class.

Field layout chịu ảnh hưởng các yếu tố:

- Kích thước/alignment từng field.
- Superclass đã có phần instance fields.
- Compressed reference/class pointer mode.
- Thuật toán field layout của phiên bản HotSpot.
- Sự khác biệt đối với contended fields hoặc các thay đổi runtime nâng cao.

**Do đó đừng tự đoán offset rồi dùng như ABI ổn định**. Java reflection có thể lấy field theo tên nhưng không cung cấp hợp đồng public về raw byte offset của nó.

### 3.3. Instance fields được kế thừa vẫn góp phần vào object

```java
class Parent {
    int parentCount;
}

class Child extends Parent {
    long childTotal;
}
```

Một **instance `Child`** cần lưu cả trạng thái instance kế thừa của `Parent` lẫn field của `Child`, cộng header/padding. **Không có hai object headers độc lập** cho parent part và child part khi đây là một instance `Child`.

`new Child()` tạo **một object** runtime class `Child`, không tạo một `Parent` object riêng bên trong theo nghĩa hai allocations. Parent constructor được gọi trong constructor chain để khởi tạo phần field của object đó.

## 4. Alignment: vì sao object có thể lớn hơn tổng field widths?

**Alignment** là yêu cầu căn chỉnh địa chỉ/kích thước object theo một đơn vị nhất định của VM. Trên HotSpot 64-bit thường gặp cấu hình **8-byte object alignment**, nhưng `-XX:ObjectAlignmentInBytes` có thể thay đổi alignment theo những giá trị được hỗ trợ.

Khi tổng phần header + fields không chia hết cho alignment, JVM có thể cần **padding**.

Công thức minh họa:

```text
rawSize      = headerBytes + fieldRegionBytes + layoutGapsBytes
alignedSize  = roundUp(rawSize, objectAlignment)

roundUp(n, a) = ((n + a - 1) / a) * a
```

Trong công thức, phép chia là chia nguyên với `a > 0`; chỉ dùng để giải bài toán sau khi **đã biết** layout. Nếu JVM sắp lại fields hoặc dùng compact headers, đầu vào công thức phải đổi theo.

### 4.1. Ví dụ object có một `int`

Giả sử:

- HotSpot 64-bit, classic header **12 byte** (mark 8 + compressed klass 4).
- `int` đặt ngay sau header, chiếm **4 byte**.
- Object alignment = **8 byte**.
- Không có phần gap khác.

```text
offset  0.. 7 : mark word       8 B
offset  8..11 : class word      4 B
offset 12..15 : int value       4 B
-------------------------------
raw / aligned size            16 B
```

Một object chỉ có **4 byte dữ liệu field** trong mô hình này vẫn tốn **16 byte shallow size** vì phải lưu header.

### 4.2. Ví dụ object không có instance field

Vẫn với classic header 12 B và alignment 8 B:

```text
offset  0.. 7 : mark word             8 B
offset  8..11 : class word            4 B
offset 12..15 : tail padding          4 B
-----------------------------------------
shallow size                       16 B
```

Tổng header 12 được làm tròn lên **16 B**. Do đó “`new Object()` không có field thì chiếm 0 byte” là sai. Nhưng **16 B không phải universal object size**; có cấu hình header compact làm ví dụ khác đi.

### 4.3. Ví dụ với `long` và một gap

Giả sử 12-byte classic header, một field `long` chiếm 8 B và phải đặt tại offset bội số của 8:

```text
offset  0.. 7 : mark word             8 B
offset  8..11 : class word            4 B
offset 12..15 : alignment gap         4 B
offset 16..23 : long value            8 B
-----------------------------------------
total                                24 B
```

Với class chỉ có một `long`, đây là một layout **minh họa hợp lý** trên HotSpot 64-bit cổ điển. Không lấy đó làm bằng chứng một JVM không bao giờ đổi thứ tự hoặc chèn field khác vào gap. Khi có nhiều fields, VM có thể tận dụng khoảng trống thay vì để toàn bộ mất mát.

### 4.4. Ví dụ field packing

Giả sử layout có header 12 B, cần đặt một `int` và một `long`. Nếu `int` lấp vị trí 12..15 trước `long` tại 16..23 thì tổng là **24 B**:

```text
[header 12][int 4][long 8] → 24 B
```

Nếu layout khác làm phát sinh gap bổ sung, tổng có thể cao hơn. Chính vì thế **thứ tự placement quan trọng**, nhưng Java source không cam kết thứ tự raw offsets. Dùng JOL hoặc công cụ đáng tin cho **JVM đang thực thi** thay vì khẳng định mọi `int+long` luôn có kích thước bất biến.

## 5. Padding gồm những dạng nào?

| Loại | Vị trí | Nguyên nhân |
| --- | --- | --- |
| **Header-to-field gap** | Giữa header và field đầu | Field cần alignment chặt hơn offset sau header |
| **Internal padding** | Giữa các fields | Field layout/alignment để đặt field tiếp theo |
| **Tail/external padding** | Cuối object | Căn tổng object size lên bội số alignment |
| **Array padding** | Sau các elements cuối hoặc khoảng trước element đầu | Element base offset / tổng kích thước mảng cần căn chỉnh |

Các bytes padding **không phải** fields Java; reflection thông thường không trả về chúng như một `Field`. Tùy VM, nội dung byte padding không nên được suy diễn thành dữ liệu có ý nghĩa. Không thiết kế chương trình phụ thuộc chúng chứa toàn zero hoặc giữ giá trị ổn định qua GC.

### Vì sao padding quan trọng khi ứng dụng có hàng triệu objects?

Giả sử có **1.000.000 objects** và một thay đổi layout giúp giảm average shallow size **8 byte/object**:

```text
1,000,000 × 8 byte = 8,000,000 byte
```

Tương đương khoảng **7,63 MiB** dữ liệu theo phép quy đổi nhị phân. Đây chỉ là **bài toán ước tính footprint shallow**; tổng memory retained còn phụ thuộc references, object graph, arrays, allocator/GC overhead và runtime khác.

Khi dữ liệu nhỏ nhưng mỗi item là một object riêng, tỷ lệ header + padding có thể lớn. Ví dụ **một triệu box objects** chứa primitive value riêng có thể có overhead cao hơn dùng **một primitive array** nếu yêu cầu truy cập/đồng bộ cho phép. Không nên tối ưu mọi chương trình sang primitive arrays bất chấp semantics, nhưng hiểu layout giúp thấy khác biệt.

## 6. Reference fields: compressed oops và compressed class pointers

### 6.1. Hai kiểu con trỏ khác vai trò

**Compressed ordinary object pointers (compressed oops)** là cơ chế HotSpot 64-bit có thể biểu diễn reference tới object trong một số vị trí bằng giá trị ngắn hơn, thường **4 byte**, thay vì raw native pointer 8 byte.

**Compressed class pointers** là cơ chế riêng để biểu diễn tham chiếu từ header/class metadata theo cách thu gọn, thường **4 byte trong classic header**. Hai cơ chế liên quan nhưng **không phải một flag/khái niệm đồng nhất**.

| Trường hợp | Cái được nén (mô hình cổ điển) | Ví dụ nơi quan sát |
| --- | --- | --- |
| Compressed oops | Reference values tới Java objects | Field `Object child`, elements của `Object[]` |
| Compressed class pointers | Tham chiếu đến class metadata trong object header | Class/klass word của ordinary object |
| Compact Object Headers | Tổ chức lại thông tin header, kết hợp metadata theo chế độ mới | Đầu object của các JDK hỗ trợ |

Việc chạy tiến trình Java 64-bit **không bắt buộc mọi reference field đều là 8 B**. Ngược lại, vì class có field `long` 8 B cũng không suy ra header đang dùng 8-byte class pointer.

### 6.2. `-XX:+UseCompressedOops` không biến `long` thành 4 byte

Nén object pointers chỉ tác động representation của các references phù hợp, **không rút `long` hoặc `double` từ 64 bit xuống 32 bit**. Kết quả biểu thức Java `long` vẫn có ngữ nghĩa chính xác theo JLS.

Thay đổi compressed oops có thể khiến shallow size của:

```java
class Node {
    Object left;
    Object right;
}
```

khác nhau, vì hai fields reference có thể có kích thước vật lý khác. Nhưng thứ tự field, header mode và padding cũng có thể tác động; không đơn giản cộng `2 × (8 - 4)` rồi khẳng định chắc chắn chênh 8 B cho mọi JVM.

### 6.3. Vì sao compressed references có lợi?

Một heap chứa rất nhiều object references có thể giảm footprint đáng kể khi references chiếm 4 B thay vì 8 B. Lợi ích thường gặp:

- Ít byte hơn cho fields và arrays chứa references.
- Dữ liệu vừa cache CPU tốt hơn, có thể giúp locality.
- Một số trường hợp giảm áp lực bộ nhớ, GC và bandwidth.

Đổi lại JVM cần logic **decode/encode** references và bị ảnh hưởng bởi dải heap được address bằng compressed scheme; lựa chọn bật/tắt thường do ergonomics, heap size và VM flags quyết định.

Với JDK 17 HotSpot, có thể kiểm tra các flags thực tế:

```bash
java -XX:+PrintFlagsFinal -version
```

Tìm `UseCompressedOops`, `UseCompressedClassPointers` và `ObjectAlignmentInBytes`. **Đây là HotSpot flags**, có thể đổi/bị deprecate ở các JDK mới; xem bản JDK bạn đang chạy thay vì chép flag từ blog cũ.

## 7. Mảng: header, length và phần tử liền kề theo layout JVM

Mảng Java là objects có **length cố định**. Cùng một array class `int[].class` có thể có các instances dài 0, 3 hoặc 1.000; length thuộc **array object**, không phải type `int[]`.

Trong layout cổ điển **HotSpot 64-bit với compressed class pointer**, một mô hình thường gặp cho mảng là:

```text
offset  0.. 7 : mark word       8 B
offset  8..11 : klass word      4 B
offset 12..15 : array length    4 B
offset 16..   : elements
...           : padding tùy tổng size
```

Vì vậy array header minh họa có **16 byte** trước vùng phần tử. Đây **không phải** quy tắc bắt buộc cho compact headers, mode không nén hoặc các kiến trúc JVM khác. Trong một số mode, có gap giữa length và elements để đảm bảo alignment của phần tử, nhất là với element type rộng.

### 7.1. Ví dụ `int[]` và alignment 8 B

Với giả định header 16 B, mỗi phần tử `int` 4 B, object alignment 8 B:

| Mảng | Header | Elements | Raw size | Aligned size |
| --- | ---: | ---: | ---: | ---: |
| `new int[0]` | 16 B | 0 B | 16 B | 16 B |
| `new int[1]` | 16 B | 4 B | 20 B | 24 B |
| `new int[2]` | 16 B | 8 B | 24 B | 24 B |
| `new int[3]` | 16 B | 12 B | 28 B | 32 B |
| `new int[4]` | 16 B | 16 B | 32 B | 32 B |

**Đây là tính toán theo giả định**, không phải kết quả `getObjectSize` được bảo đảm trên mọi HotSpot. Nếu alignment đổi thành 16 B hoặc array header đổi, kết quả phải tính lại.

### 7.2. `Object[]` khác `int[]`

Với `Object[]`, mỗi element là một **reference value**, có thể chiếm 4 B khi compressed oops bật hoặc 8 B khi tắt. **Nội dung của object mà reference trỏ tới không nằm bên trong phần tử mảng theo nghĩa layout shallow**.

```java
Object[] arr = new Object[2];
arr[0] = new byte[1024];
arr[1] = "hello";
```

Đây là đoạn code minh họa trong method. Shallow size của `arr` gồm header + **hai slots reference** + padding. Nó **không** cộng thêm 1.024 bytes của array `byte[]` và dữ liệu `String`. Muốn biết tổng object graph cần công cụ đo deep/retained size hoặc phân tích đồ thị references.

### 7.3. Mảng nhiều chiều thực chất là mảng của mảng

```java
int[][] matrix = new int[2][3];
```

Trong mô hình Java, đây là **một outer array** chứa references đến **hai inner int[] arrays**. Tổng memory không bằng một header 16 B cộng `2 × 3 × 4` mà thôi: mỗi inner `int[]` cũng là object với **header và padding riêng**.

```text
outer int[][]  ──►  [ ref0 | ref1 ]
                        │      │
                        ▼      ▼
                     int[3]  int[3]
                      object  object
```

Hai inner arrays là các objects khác nhau, cho phép từng hàng có độ dài khác nhau. Nếu tạo `new int[2][]`, outer array được cấp phát nhưng hai references ban đầu là `null`, không có inner arrays cho tới khi chương trình tạo chúng.

## 8. Tính shallow size đúng: đo gì và không đo gì?

**Shallow size** biểu diễn bộ nhớ dành cho một object đơn lẻ theo đo lường của một JVM/tool. Nó thường bao gồm header, instance fields hoặc array elements, gaps và tail padding; không đệ quy sang những object khác được tham chiếu.

**Deep size** thường là tổng kích thước của object graph reachable theo định nghĩa công cụ; **retained size** là kích thước memory có thể được giải phóng nếu object và toàn bộ các objects chỉ còn reachable qua nó không còn reachable. Hai khái niệm này không đồng nghĩa:

```text
A ──────► B ◄────── C
```

Nếu `B` còn reachable qua `C`, bỏ `A` không giải phóng `B`. Vậy `A` có thể “deep traverse” tới `B`, nhưng không nhất thiết **retain** `B` theo thuật toán dominator.

### Ví dụ node có reference

```java
class Node {
    int value;
    Node next;
}
```

Shallow size của `new Node()` gồm header, `int`, reference `next` và padding. Một danh sách 1.000 nodes có footprint đến từ **1.000 objects** riêng, mỗi node có header và alignment overhead; có thể còn allocation/GC metadata ở mức vùng heap. Không được lấy shallow size của node rồi khẳng định chính xác retained size của toàn bộ danh sách khi có các references từ nơi khác.

### `String` và collection còn có cấu trúc phụ

Một `String` object trong HotSpot hiện đại thường có fields để đại diện dữ liệu ký tự, và dữ liệu thực có thể ở một **array object khác**. `ArrayList` cũng có thể tham chiếu tới backing array. Vì vậy đo shallow size của `String` hoặc `ArrayList` **không tự đo toàn bộ payload**. Cấu trúc private fields của JDK có thể thay đổi theo release; dùng heap profiler hoặc JOL đúng phiên bản thay vì dựa vào layout trích từ một JDK cũ.

## 9. Identity hash, synchronized và GC có làm đổi layout không?

Các tính năng runtime tương tác với header/metadata nhưng không nên đồng nhất chúng với Java fields:

- **`System.identityHashCode(obj)`:** yêu cầu identity hash theo luật VM. Phương thức này không trả địa chỉ object và giá trị có thể trùng giữa hai objects.
- **`synchronized(obj)`:** thực thi monitor enter/exit theo Java Memory Model. HotSpot có những cơ chế lightweight locking/inflation/monitor metadata khác nhau theo JDK. Không tự giả định một bit pattern “biased locking” cố định cho mọi HotSpot hiện tại.
- **Garbage collection:** object có thể di chuyển trong heap; collector cần xác định object size, fields nào là references và metadata hỗ trợ GC. Các thông tin đó có thể đến từ object header và metadata của runtime class.
- **Class loading/unloading:** class metadata và class mirrors được JVM quản lý; mỗi object không chứa bản sao đầy đủ bytecode hoặc method table.
- **Compact header mode:** sự thay đổi tổ chức header có thể ảnh hưởng cách metadata khóa/hash/GC được xử lý.

Không nên đọc raw mark word từ Java application bằng phép truy cập offset chưa kiểm chứng; kiểu hoạt động này phụ thuộc VM internals và có thể thay đổi khi GC, hash calculation, lock transitions hoặc JDK upgrade.

## 10. Quan sát HotSpot layout bằng Java Object Layout (JOL)

**JOL (Java Object Layout)** là dự án công cụ của OpenJDK dùng để quan sát object layout, offsets và kích thước trên JVM thực tế, hoặc mô phỏng một số cấu hình. Nó phù hợp hơn việc tự cộng field sizes thủ công nếu bạn cần kết quả của một JVM cụ thể.

Dự án: https://openjdk.org/projects/code-tools/jol/  
Repository và hướng dẫn CLI: https://github.com/openjdk/jol

Khi **đã cài/có JOL CLI phù hợp**, một lệnh ví dụ:

```bash
java -jar jol-cli.jar internals java.lang.Integer
```

`jol-cli.jar` là **tên tệp minh họa**; hãy thay bằng tên/đường dẫn JAR JOL bạn thật sự có. Một số bản JOL CLI phát hành dưới tên có phiên bản hoặc đuôi `-full.jar`. Không dùng lệnh trên khi chưa có JAR.

Output có thể bao gồm:

```text
OFF  SZ   TYPE   DESCRIPTION
0    8           (object header: mark)
8    4           (object header: class)
12   4    int    Integer.value

Instance size: 16 bytes
Space losses: ...
```

Đây là **mô tả mẫu rút gọn** cho một HotSpot 64-bit classic header compressed class pointers; không phải output `jol` luôn cố định. Nếu chạy trên JDK với compact headers hoặc flags khác, offsets/instance size có thể khác.

### Đọc `OFF`, `SZ`, `Instance size` và `Space losses`

- **`OFF`:** offset byte từ đầu object trong layout được trình bày.
- **`SZ`:** kích thước mục tương ứng (byte).
- **`DESCRIPTION`:** header, field, array element hoặc padding/gap.
- **`Instance size`:** shallow size của instance theo phép đo/mô hình công cụ.
- **`Space losses`:** những byte không dùng do gaps/tail padding theo báo cáo.

JOL có thể yêu cầu quyền/agent phù hợp để truy cập layout chính xác trên các JDK mới. Nếu một chế độ tool chỉ đưa ra **estimate/simulation**, hãy ghi rõ đó **không phải measurement chắc chắn của mọi live object**.

### Không nhầm object header và field offsets với source field ordering

Khi JOL in field `long b` ở offset 16 dù `byte a` khai báo trước, điều đó không cho phép chương trình phụ thuộc offset 16 như ABI public. Layout là hợp đồng nội bộ của HotSpot đang sử dụng và có thể thay đổi sau lần khởi chạy với flags khác hoặc JDK upgrade.

## 11. Ví dụ Java 17+ độc lập: phân biệt object, class, field và array

Tạo file **`ObjectLayoutBasics.java`**:

```java
public class ObjectLayoutBasics {
    static class Empty {}

    static class Data {
        byte flag = 1;
        int number = 42;
        Object reference = new Object();
        static int shared = 100;
    }

    static class Parent {
        int parentValue = 7;
    }

    static class Child extends Parent {
        long childValue = 9L;
    }

    public static void main(String[] args) {
        Data first = new Data();
        Data second = new Data();
        int[] array = new int[3];
        Child child = new Child();

        System.out.println("different instances: " + (first != second));
        System.out.println("same class: "
                + (first.getClass() == second.getClass()));
        System.out.println("flag and number: "
                + first.flag + ", " + first.number);
        System.out.println("different nested objects: "
                + (first.reference != second.reference));
        System.out.println("static shared: " + Data.shared);
        System.out.println("instance fields declared: "
                + Data.class.getDeclaredFields().length);
        System.out.println("empty declared fields: "
                + Empty.class.getDeclaredFields().length);
        System.out.println("child fields: "
                + child.parentValue + ", " + child.childValue);
        System.out.println("array length: " + array.length);
        System.out.println("array class name: "
                + array.getClass().getName());
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 ObjectLayoutBasics.java
java ObjectLayoutBasics
```

**Standard output chính xác:**

```text
different instances: true
same class: true
flag and number: 1, 42
different nested objects: true
static shared: 100
instance fields declared: 4
empty declared fields: 0
child fields: 7, 9
array length: 3
array class name: [I
```

### Diễn giải

1. `new Data()` tạo các instances riêng, nhưng cả hai có cùng runtime class.
2. `Data.shared` là **static field**, không chiếm một slot instance body riêng cho từng `Data`. Tuy nhiên `getDeclaredFields()` liệt kê **cả static và instance fields**, nên `Data` có **4 declared fields**; số này **không phải số instance fields**.
3. `reference = new Object()` tạo một **object khác cho mỗi Data** trong ví dụ; Data chỉ lưu reference tới object đó. Shallow size của Data không cộng cả object `reference` chỉ vì nó reachable.
4. `Child` có thể truy cập `parentValue` từ superclass và `childValue` của chính nó; trong một Child instance, đó là các fields của cùng một object.
5. Mảng `int[3]` có một array header, ba phần tử `int` và padding tùy layout. `[I` là tên runtime kiểu mảng `int[]` theo cách `Class.getName()` biểu diễn.

Bài demo **không in địa chỉ hay shallow size**, vì Java SE API thông thường không cam kết raw object layout. Bài thí nghiệm ở mục sau dùng `Instrumentation` để đo bằng JVM thật.

## 12. Đo shallow size với `Instrumentation.getObjectSize`

Java Instrumentation API có phương thức `getObjectSize(Object)` cho phép lấy **ước lượng phụ thuộc triển khai về bộ nhớ dành cho một object**. Nó yêu cầu **Java agent** có `premain` được cung cấp qua `-javaagent`; không cần dùng API nội bộ `sun.misc.Unsafe` hay giả định offset header.

Phương thức này **không tính tổng mọi object reachable** từ object được đo, và kết quả không phải một ABI/GC retained-size contract bất biến.

### 12.1. Tạo agent `ShallowSizeAgent.java`

```java
import java.lang.instrument.Instrumentation;

public class ShallowSizeAgent {
    private static Instrumentation instrumentation;

    public static void premain(String options, Instrumentation inst) {
        instrumentation = inst;
    }

    public static long sizeOf(Object value) {
        if (instrumentation == null) {
            throw new IllegalStateException("Start with -javaagent");
        }
        return instrumentation.getObjectSize(value);
    }
}
```

### 12.2. Tạo chương trình `SizeExperiment.java`

```java
public class SizeExperiment {
    static class Empty {}

    static class OneInt {
        int value;
    }

    static class OneLong {
        long value;
    }

    static class HasReference {
        Object child = new byte[1000];
    }

    private static void show(String name, Object value) {
        System.out.println(name + " shallow: "
                + ShallowSizeAgent.sizeOf(value));
    }

    public static void main(String[] args) {
        show("Empty", new Empty());
        show("OneInt", new OneInt());
        show("OneLong", new OneLong());
        show("HasReference", new HasReference());
        show("int[0]", new int[0]);
        show("int[3]", new int[3]);
        show("byte[1000]", new byte[1000]);
    }
}
```

### 12.3. Tạo manifest cho agent

Tạo file văn bản UTF-8/ASCII tên **`manifest.mf`**:

```text
Premain-Class: ShallowSizeAgent

```

Cần có **dòng trống cuối manifest** theo cấu trúc JAR manifest. Tại thư mục chứa các file trên, chạy:

```bash
javac -encoding UTF-8 ShallowSizeAgent.java SizeExperiment.java
jar cfm shallow-agent.jar manifest.mf ShallowSizeAgent.class
java -javaagent:shallow-agent.jar SizeExperiment
```

Nếu tên file JAR khác, thay tương ứng. `-javaagent` là cơ chế Java Instrumentation API chuẩn; hai class demo không cần thư viện ngoài.

### 12.4. Kết quả và cách đọc

Chương trình in 7 dòng với dạng:

```text
Empty shallow: <bytes>
OneInt shallow: <bytes>
OneLong shallow: <bytes>
HasReference shallow: <bytes>
int[0] shallow: <bytes>
int[3] shallow: <bytes>
byte[1000] shallow: <bytes>
```

**Đây là mẫu format, không phải stdout chính xác**: giá trị `<bytes>` do JVM thực tế quyết định. Với cấu hình HotSpot 64-bit classic header + compressed pointers + alignment 8 B, bạn **có thể** thấy các kích thước tương ứng các mô hình tính ở mục 4/7. Nếu JDK bật compact headers hoặc alignment khác, số đo có thể thay đổi.

Các điều cần kiểm tra từ phép đo:

- `Empty` có shallow size **khác 0** do object header/alignment.
- `HasReference` chỉ bao gồm **field reference**, không cộng toàn bộ `byte[1000]` được tham chiếu.
- `byte[1000]` là **array object khác**, có header + 1000 phần tử + padding.
- `int[3]` có thêm phần tử so với `int[0]`, nhưng thay đổi sau alignment có thể không đúng chính xác 3 × 4 B.
- `OneLong` và `OneInt` có thể bằng hoặc khác shallow size tùy header/gaps/alignment; không kết luận chỉ từ primitive width.

**Vì sao không ghi một bộ stdout số cố định?** `getObjectSize` trả *implementation-specific approximation*, JVM implementations và options có thể làm output khác. Một README chất lượng phải phân biệt **mẫu hợp lệ** với **kết quả chắc chắn**.

### 12.5. Thử đổi alignment để đối chiếu

Với HotSpot JDK 17 hỗ trợ flag:

```bash
java -XX:ObjectAlignmentInBytes=16 -javaagent:shallow-agent.jar SizeExperiment
```

Khi đổi object alignment, một số shallow sizes có thể tăng vì tổng object phải được làm tròn theo bội số lớn hơn. Đừng chạy flag này một cách mù quáng trên ứng dụng production; bài thực hành chỉ dành cho tiến trình test và flag có thể ảnh hưởng heap ergonomics.

Muốn thử mode compressed oops, trước hết **kiểm tra flags của phiên bản JDK hiện dùng**. Không giả định cùng tham số luôn được hỗ trợ hoặc có cùng hiệu quả sau thay đổi lớn ở layout JVM.

## 13. Đo layout khác heap profiler: công cụ nào trả lời câu hỏi nào?

| Câu hỏi | Công cụ gợi ý | Giới hạn |
| --- | --- | --- |
| Object có fields nào, offsets và padding gì? | **JOL** | Phụ thuộc VM/JDK/options; có thể cần agent để xác minh |
| Một object chiếm khoảng bao nhiêu byte shallow? | **Instrumentation.getObjectSize** | Estimate tùy triển khai, không tính referenced graph |
| Object class nào đang chiếm nhiều heap? | `jcmd PID GC.class_histogram`, heap profiler | Histogram thường tổng theo class, không trình bày layout từng object |
| Những object nào giữ data không được GC? | Heap dump + dominator tree | Cần hiểu GC roots/retained size, heap dump có thể tốn tài nguyên |
| Heap và native footprint hiện tại? | JVM metrics, JMX, NMT, OS monitoring | Không trộn reserved/committed/RSS/shallow |
| Header mode/heap alignment ra sao? | HotSpot flags, JOL VM details | Không dùng giả định từ máy khác |

Ví dụ chẩn đoán JDK 17 HotSpot:

```bash
java -XX:+PrintFlagsFinal -version
jcmd -l
jcmd PID GC.class_histogram
```

Trong dòng cuối, **thay `PID` bằng số process thực**; không chạy ở môi trường production tùy tiện vì class histogram có thể gây chi phí thời gian/safepoint. Với một ứng dụng đơn giản muốn biết offset/field packing, **JOL** thường phù hợp hơn histogram.

### Không dùng `Runtime.totalMemory()` để suy object size chính xác

`Runtime.getRuntime().totalMemory() - freeMemory()` gần đúng heap đang sử dụng theo snapshot, nhưng việc cấp phát một object có thể trùng thời điểm GC/TLAB refill/class loading hoặc allocation khác. Lấy chênh lệch hai lần gọi không phải phép đo shallow size chính xác. Để nghiên cứu layout, ưu tiên instrumentation/JOL.

## 14. False sharing, `@Contended` và overhead padding nâng cao

Đôi khi lập trình concurrency muốn **tăng khoảng cách bộ nhớ** giữa các fields hay counters để giảm **false sharing** giữa CPU cores. HotSpot có các cơ chế/annotation nội bộ như `@Contended` và các flag hỗ trợ theo phiên bản, có thể đưa **padding có chủ đích** vào object layout.

Điều này trái mục tiêu “packing nhỏ nhất”, nhưng có thể cải thiện throughput ở một số workload nhiều thread. Không nên vội coi mọi byte padding là lỗi thiết kế cần loại bỏ:

- **Alignment padding** phục vụ bố trí dữ liệu hợp lệ/hiệu quả.
- **Contended padding** có thể là chiến lược hiệu năng đối với dữ liệu chia sẻ giữa threads.
- Lợi ích của thay đổi phải được xác nhận bằng **benchmark đúng cách**; thêm padding khiến memory footprint tăng.

Trong JDK 17, `jdk.internal.vm.annotation.Contended` là API nội bộ module, không phải Java SE API thông thường. **Không khuyến khích** thêm vào code ứng dụng chỉ dựa trên bài này; cần đánh giá hỗ trợ, encapsulation và JDK version.

## 15. Object layout không quyết định tất cả về hiệu năng

Shallow size nhỏ hơn có thể giảm heap pressure, cache misses và GC work, nhưng **không tự bảo đảm** chương trình nhanh hơn. Một số yếu tố khác:

- **Object allocation rate**: bao nhiêu objects được tạo/giây.
- **Object lifetime**: objects chết sớm hay tồn tại lâu.
- **Memory locality**: dữ liệu truy cập liên tiếp hay qua đồ thị references.
- **Escape analysis/scalar replacement**: JIT có thể loại bỏ hoặc thay thế một số allocations vật lý khi bảo toàn ngữ nghĩa.
- **GC algorithm**: các collectors có cách quản lý regions/compaction và overhead riêng.
- **Synchronization**: lock contention và metadata runtime ảnh hưởng throughput.
- **Data structure choice**: `int[]` khác `List<Integer>` về boxing, references và khả năng cache locality.

**Điểm quan trọng khi benchmark:** nếu JIT chứng minh một object không cần được cấp phát vật lý trong đoạn code tối ưu, dùng một benchmark đơn giản với `new Object()` trong vòng lặp có thể không đo được object allocation mà người viết nghĩ đang đo. Dùng JMH/profiler khi đánh giá hiệu năng thực tế.

### Primitive array và boxed objects

So sánh:

```java
int[] values = new int[1000];
```

với:

```java
Integer[] boxed = new Integer[1000];
```

Trong array đầu, mỗi element giữ trực tiếp một giá trị `int`. Trong array thứ hai, mỗi element là một **reference** đến `Integer` object hoặc `null`, và các `Integer` objects riêng có overhead của chúng. Tuy nhiên không thể đơn giản nói mọi element `Integer[]` chắc chắn có object riêng, vì boxing caches/references sharing có thể xảy ra. Việc lựa chọn cấu trúc tùy semantics, nullability, API và performance.

## 16. Những hiểu nhầm thường gặp

| Nhầm lẫn | Cách hiểu đúng |
| --- | --- |
| “Object Java chỉ chiếm tổng kích thước fields.” | Còn header, layout gaps và padding |
| “`new Object()` không có field nên size bằng 0.” | Object vẫn có header và alignment overhead theo VM |
| “Mọi HotSpot 64-bit có header đúng 12 byte.” | Classic compressed 12 B là ví dụ; uncompressed/compact/JDK khác có layout khác |
| “Mark word là địa chỉ object.” | Mark word là metadata, không phải địa chỉ mà `identityHashCode` trả |
| “`identityHashCode` luôn duy nhất giữa mọi objects.” | Hash collisions có thể xảy ra, không dùng làm định danh tuyệt đối |
| “Klass pointer là instance field `Class<?> clazz`.” | Header dùng metadata nội bộ; class mirror là API liên quan, không phải field source |
| “Static field tốn thêm 4/8 B cho **mỗi** instance.” | Static state gắn với class, không phải phần field data riêng của mọi object |
| “`Object ref` chứa toàn bộ object đích.” | Nó chứa reference value tới một object khác |
| “`boolean` field luôn được JLS định nghĩa là 1 byte.” | Layout vật lý là chi tiết JVM; HotSpot phổ biến dùng 1 B cho field |
| “Field source order chính là byte offsets.” | HotSpot có thể sắp xếp/packing fields |
| “Object alignment chỉ ảnh hưởng performance, không ảnh hưởng size.” | Alignment làm tròn object size và có thể tăng padding |
| “Một `Child` object có hai headers vì có `Parent`.” | Một object runtime class Child chứa phần fields kế thừa, không hai allocations parent/child |
| “`int[][]` chỉ có một header.” | Mỗi outer/inner array là một object có header riêng |
| “`String` shallow size là toàn bộ memory của chuỗi.” | Payload có thể ở referenced array/object khác |
| “JOL output trên máy khác là ABI ổn định của Java.” | JVM/JDK/flags khác có thể đổi offsets/size |
| “`getObjectSize` đếm toàn bộ object graph.” | Nó đo **shallow estimate** của một object |
| “GC chắc chắn để lại object tại một địa chỉ cố định.” | Moving collectors có thể chuyển object; địa chỉ raw không là Java-level stable identity |
| “Compact headers làm object không cần metadata.” | Metadata được biểu diễn khác/thu gọn; JVM vẫn phải quản lý kiểu và trạng thái |

## 17. Bài tập và lời giải

### Bài 1 — Class trống

**Giả định:** HotSpot 64-bit classic compressed header 12 B, alignment 8 B, class không có fields.  
**Hỏi:** Shallow size minh họa?

**Đáp án:** `roundUp(12, 8) = 16 B`. Phần padding 4 B; không khẳng định JDK bật compact headers cũng có size này.

### Bài 2 — Một `int`

**Giả định:** cùng JVM ở bài 1; `int` được đặt tại offset 12.  
**Đáp án:** `12 + 4 = 16 B`, đã căn 8 B, không cần tail padding.

### Bài 3 — Một `long`

**Giả định:** mark 8 B + compressed class word 4 B, field `long` phải bắt đầu tại offset 16 và alignment 8 B.  
**Đáp án:** gap 4 B trước long, long 8 B, tổng **24 B**.

### Bài 4 — Mảng `int[3]`

**Giả định:** array header 16 B, mỗi int 4 B, alignment 8 B.  
**Đáp án:** raw 16 + 12 = 28 B; aligned **32 B**, 4 B tail padding.

### Bài 5 — Mảng `int[2]`

**Giả định:** như bài 4.  
**Đáp án:** 16 + 8 = **24 B**, chia hết 8; không cần tail padding.

### Bài 6 — Một mảng references

**Giả định:** array header 16 B, 3 elements references compressed 4 B, alignment 8 B.  
**Đáp án:** 16 + 3 × 4 = 28 B, aligned **32 B**. Không cộng shallow size của các objects được tham chiếu.

### Bài 7 — Static field

```java
class Example {
    int a;
    static int total;
}
```

**Hỏi:** Field `total` có làm shallow size mỗi `new Example()` tăng thêm một `int` không?  
**Đáp án:** Không. Nó gắn với runtime class, không phải mỗi instance.

### Bài 8 — Kế thừa

```java
class A { int a; }
class B extends A { int b; }
```

**Hỏi:** `new B()` có bao nhiêu object headers trong cùng object đó?  
**Đáp án:** Một theo mô hình HotSpot thông thường; parent fields và child fields thuộc một runtime object, không tạo thêm object A riêng.

### Bài 9 — Hai references

```java
class Holder {
    Object x;
    Object y;
}
```

**Hỏi:** Nếu cả hai fields trỏ cùng một object, shallow size Holder có giảm không?  
**Đáp án:** Không vì vẫn có hai field slots chứa references. Deep/retained analysis thay đổi theo aliasing và object graph.

### Bài 10 — Layout và phiên bản JDK

**Hỏi:** Kích thước `new Object()` quan sát bằng JOL trên HotSpot JDK 17 compressed header có bắt buộc giống HotSpot chạy Compact Object Headers ở JDK mới?  
**Đáp án:** Không. Header representation thay đổi; phải đo trên đúng JVM/configuration.

### Bài 11 — Byte padding

**Hỏi:** Có thể dùng reflection `getDeclaredField("padding")` để lấy tail padding tự động của object không?  
**Đáp án:** Không. Padding không là field Java khai báo trong class; công cụ layout mới trình bày nó.

### Bài 12 — Chẩn đoán heap

**Hỏi:** Heap profiler cho biết có hàng triệu `Node` objects. Có nên chỉ đo size của một `Node` rồi kết luận heap retained tổng cộng?  
**Đáp án:** Chưa. Cần xem references, arrays phụ, số lượng thực, object graph, dominators, GC roots và class loader để xác định bộ nhớ retained/allocated.

### Bài 13 — Reference field

**Hỏi:** Khi compressed oops bật, field `Object x` thường là 4 B. Nếu `x` trỏ tới `byte[1000]`, có phải `Holder` chứa hơn 1.000 B trong chính body?  
**Đáp án:** Không. `Holder` chứa reference field; array là object khác với header/payload riêng.

### Bài 14 — Alignment khác

**Giả định:** raw object size 20 B, alignment đổi từ 8 B thành 16 B.  
**Đáp án:** `roundUp(20, 8) = 24 B`; `roundUp(20, 16) = 32 B`. Đây là bài toán số học sau khi biết raw layout; JVM có thể thay layout khi đổi mode, nên số đo thực cần xác nhận.

## 18. Tổng kết

Mô hình nền tảng:

```text
Ordinary object (HotSpot layout khái quát):
    object header
  + instance field values
  + internal/tail padding
  = shallow footprint của object (theo VM)

Array object:
    array header + length metadata
  + array element slots
  + padding
  = shallow footprint của array

Object graph:
    nhiều objects/arrays liên kết bằng references
  → deep/retained size KHÔNG phải shallow size một object
```

**Mark word** phục vụ metadata trạng thái object, **class/klass information** giúp JVM xác định runtime type và layout; **field data** là giá trị instance riêng, không chứa static fields của class hay toàn bộ objects mà references trỏ tới. **Padding** xuất hiện vì field/object alignment. Các cải tiến như **compressed oops**, **compressed class pointers**, **Compact Object Headers** và field packing có thể thay đổi footprint rất nhiều khi có hàng triệu objects.

Để học/kiểm tra đúng, **luôn công bố giả định JVM 64-bit/32-bit, JDK version, header mode, compressed references và object alignment**, sau đó dùng **JOL** hoặc **Instrumentation.getObjectSize** trên runtime thực. Đừng biến một sơ đồ 12-byte header từ tài liệu HotSpot cũ thành quy luật của mọi phiên bản Java.

### Tài liệu tham khảo

- **OpenJDK Java Object Layout (JOL):** https://openjdk.org/projects/code-tools/jol/ và https://github.com/openjdk/jol — công cụ xem object header, field offsets, instance size và padding.
- **OpenJDK JEP 450 — Compact Object Headers (Experimental):** https://openjdk.org/jeps/450 — JDK 24.
- **OpenJDK JEP 519 — Compact Object Headers:** https://openjdk.org/jeps/519 — JDK 25.
- **Oracle JDK 17 — java command options:** https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html — các HotSpot flags `ObjectAlignmentInBytes`, `UseCompressedOops` và công cụ logging.
- **Java SE 17 `Instrumentation.getObjectSize`:** https://docs.oracle.com/en/java/javase/17/docs/api/java.instrument/java/lang/instrument/Instrumentation.html#getObjectSize(java.lang.Object) — hợp đồng size estimate, không phải deep size.
- **HotSpot source (implementation):** https://github.com/openjdk/jdk/tree/master/src/hotspot/share/oops/ — các cấu trúc object headers, mark word và metadata. Chi tiết tại `master` có thể khác JDK 17; khi cần kiểm tra chính xác phải xem đúng branch/release.

**Thông điệp chính:** Java object có **overhead của JVM**, và số byte thật sự chiếm trong heap không được quyết định chỉ bởi danh sách fields trong `.java`. Nắm đúng **header — fields — alignment — padding**, rồi đo trên đúng HotSpot, sẽ giúp bạn hiểu footprint, tối ưu data structures và tránh suy đoán sai khi debug heap memory.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hotspot-objects-004">Bố trí đối tượng và mảng là chi tiết triển khai, không phải cam kết của Java</a>

<details>
<summary>Xem chi tiết</summary>

# Bố trí đối tượng và mảng là chi tiết triển khai, không phải cam kết của Java

## 1. Điều quan trọng nhất cần ghi nhớ

Trong Java, bạn có thể khai báo:

```java
final class Sample {
    byte flag;
    long sequence;
    Object child;
}

Sample value = new Sample();
int[] numbers = new int[3];
```

Từ chương trình này, ngôn ngữ Java **bảo đảm những điều có thể quan sát qua API/ngữ nghĩa**: `flag` mặc định bằng `0`, `sequence` bằng `0L`, `child` bằng `null`, `numbers.length == 3`, các phần tử `numbers` ban đầu bằng `0`, và các biến reference có thể cùng trỏ một object.

Nhưng Java **không hứa** rằng `Sample` luôn dùng đúng 32 byte, `flag` luôn nằm tại offset 12, `sequence` nằm sau `flag`, array header luôn 16 byte, object reference luôn dài 4 hay 8 byte, hoặc mọi garbage collector phải đặt object tại cùng một địa chỉ vật lý suốt vòng đời.

**JVMS `2.7 — Representation of Objects** xác định JVM **không bắt buộc một cấu trúc nội bộ cụ thể cho object**. Những sơ đồ kiểu **mark word + class pointer + fields + padding** thường dùng khi giải thích **HotSpot**, chứ không phải hợp đồng nhị phân áp dụng cho mọi JVM/kiến trúc/JDK.

Đây là phạm vi chính của bài:

- Tách **ngữ nghĩa do Java/JVMS bảo đảm** khỏi **lựa chọn bố trí trong HotSpot**.
- Hiểu object header, class metadata pointer, field placement, alignment/padding, compressed references.
- Hiểu **array object**, length, element storage và mảng nhiều chiều là **mảng của các mảng**.
- Hiểu vì sao những con số 12, 16, 24, 32 byte chỉ là **ví dụ của một cấu hình**.
- Nhận ra thay đổi của HotSpot, gồm **compact object headers** từ JDK 24/25 và mặc định mới trên JDK 27.
- Biết dùng **JOL (Java Object Layout)**, `Instrumentation.getObjectSize`, JFR/heap tools với giới hạn đo lường rõ ràng.
- Tránh hardcode object offsets hoặc dựa vào internal class layout trong Java application logic.

## 2. Ba tầng phải phân biệt: Java, JVMS và HotSpot

| Tầng | Quy định hoặc cung cấp gì? | Ví dụ |
| --- | --- | --- |
| **Java Language Specification (JLS)** | Kiểu, giá trị mặc định, `final`, field access, arrays, thứ tự đánh giá, Java Memory Model | `new int[2]` có length 2; phần tử mặc định 0 |
| **Java Virtual Machine Specification (JVMS)** | Reference types, bytecode, runtime data areas, định nghĩa class/array, ngữ nghĩa instruction | `getfield`, `iaload`, `arraylength` và kiểm tra tương ứng |
| **HotSpot implementation** | Cách biểu diễn object tại runtime, headers, offsets, compressed pointers, GC/JIT, field packing | Mark word, Klass pointer, object alignment, JOL output |

### 2.1. Hợp đồng phía Java

Nếu một class có field `int x`, Java yêu cầu khi chưa được gán cụ thể, field này có default value `0`. Chương trình có thể đọc `obj.x` theo các quy tắc truy cập kiểu, quyền truy cập, initialization và Java Memory Model.

Java **không cung cấp một cú pháp tiêu chuẩn** kiểu `addressOf(obj.x)` để hỏi offset byte của field trong heap. Hai fields đặt cạnh nhau trong source không có nghĩa runtime phải đặt cạnh nhau trong memory.

### 2.2. Hợp đồng phía JVM

Bytecode `getfield` chỉ định field thông qua symbolic reference trong constant pool, không yêu cầu class file mã hóa một offset byte cứng của field trong object. JVM có trách nhiệm giải quyết tham chiếu và truy cập trường hợp lệ theo ngữ nghĩa.

Bytecode `iaload` đọc phần tử `int[]` theo array reference + index. Spec yêu cầu phát sinh `NullPointerException`/`ArrayIndexOutOfBoundsException` trong điều kiện tương ứng, nhưng không buộc JVM dùng một cách tính địa chỉ máy cố định có thể quan sát từ mã Java.

### 2.3. Tầng HotSpot

HotSpot thường tổ chức object trong heap với header và phần dữ liệu fields/elements, tính kích thước theo alignment và chính sách GC. Những chi tiết này có thể thay đổi khi:

- Đổi từ JDK 17 sang JDK 25/27.
- Bật/tắt **compressed oops**, **compressed class pointers**, **compact object headers**.
- Đổi `ObjectAlignmentInBytes` hoặc kiến trúc 32-bit/64-bit.
- Đổi chiến lược field layout, GC, JIT hoặc nội bộ runtime.
- Sử dụng các loại class đặc thù, chẳng hạn arrays, hidden classes, generated classes.

**Hãy gắn mọi con số layout với ít nhất JDK build, VM, kiến trúc và các cờ liên quan.**

## 3. Đối tượng HotSpot: header, instance data và padding

Để thảo luận về **tính khả chuyển**, dùng sơ đồ cấp cao của một **HotSpot 64-bit ở chế độ classic header**:

~~~text
[ runtime header ] [ các instance fields ] [ khoảng trống do alignment nếu có ]
~~~

Object header có thể chứa **mark word** và biểu diễn **class/Klass pointer** phù hợp. Mark word hỗ trợ thông tin quản lý trạng thái như locking, hash hoặc tuổi GC tùy VM; nó **không** là Java field và **không** phải địa chỉ cố định của object. Một số HotSpot modes biểu diễn header gọn hơn, vì thế sơ đồ trên **không phải cấu trúc byte-level bắt buộc**.

Các instance fields bao gồm fields được kế thừa, có thể có internal gaps và được runtime bố trí theo các quy tắc triển khai. Dù HotSpot reorder/pack fields, mọi biểu thức `obj.field` phải giữ đúng semantics Java. Chẳng hạn, hai field cùng tên trong superclass/subclass bị *hiding* vẫn là hai fields logic khác nhau; layout không được làm chúng nhập thành một.

**Nơi kiểm chứng số byte thực:** bài đo layout ngay trước bài này giải thích mark word/Klass pointer, field offsets, padding và JOL/Instrumentation chi tiết. Ở đây ta chỉ dùng chúng để nhận diện **điều không được giả định khi nâng JVM**.

## 4. Object header truyền thống: hiểu số 12, 16 byte có điều kiện

Trong cấu hình HotSpot 64-bit **classic header, JDK 17**, ví dụ thường gặp là:

~~~text
mark word:                   8 bytes
compressed Klass pointer:    4 bytes
------------------------------
classic header:             12 bytes

Nếu class pointer không nén:
mark word:                   8 bytes
Klass pointer:               8 bytes
------------------------------
classic header:             16 bytes
~~~

Đây chỉ là **những ví dụ có điều kiện**, không phải hợp đồng Java/JVMS. Shallow instance size còn phụ thuộc fields, các gaps và `ObjectAlignmentInBytes`. Ví dụ nếu raw size là 13 byte và alignment là 8 byte, shallow size minh họa là `alignUp(13,8)=16`; nếu raw size 17, kết quả là 24. Không được dùng một phép cộng field widths từ source làm phép đo offset chính xác.

Với **Compact Object Headers**, HotSpot có thể dùng header nhỏ hơn đáng kể; theo OpenJDK JEP 450/519/534, quá trình triển khai feature thay đổi giữa JDK 24 (experimental), 25 (product, opt-in) và 27 (mặc định). Vì vậy số “12 byte” không được hardcode trong serializer, Unsafe code, native ABI hoặc benchmark đa phiên bản.

## 5. Field declaration order không phải memory layout contract

Java source:

```java
class Packet {
    byte kind;
    long id;
    boolean valid;
    Object payload;
    int version;
}
```

Một người đọc có thể tưởng rằng VM phải đặt fields theo `kind → id → valid → payload → version`. Không có quy định Java yêu cầu thứ tự địa chỉ như vậy.

Những điều vẫn được bảo đảm:

- `packet.kind` truy cập đúng giá trị của field `kind`.
- `packet.id` truy cập đúng giá trị của `id`.
- Compiler/JVM áp dụng visibility, `final` và memory semantics như đặc tả.
- Reflection cung cấp thông tin fields theo các API, nhưng thứ tự phần tử do `getDeclaredFields()` trả về **không phải bằng chứng về field offsets** và API không bảo đảm một thứ tự khai báo có ý nghĩa.

Những điều **không** được bảo đảm:

- Field `id` nằm sau `kind` đúng 1 byte.
- Field `valid` không có padding bên cạnh.
- `payload` reference luôn là 8 byte trên VM 64-bit.
- Thêm một field `boolean` nhất định tăng instance size đúng 1 byte.
- Đổi thứ tự khai báo fields trong source nhất định làm layout tương ứng đổi.

### 5.1. Khi nào field order trở thành vấn đề thật?

Nếu bạn đang triển khai **binary protocol**, **file format**, **memory-mapped structure** hoặc **native ABI**, hãy dùng một format/representation được định nghĩa tường minh: ví dụ `ByteBuffer` với byte order xác định, `DataOutputStream`, serialization format, hay Foreign Function & Memory API khi tương thích JDK mục tiêu.

**Không** dùng trực tiếp memory layout của Java object làm schema lưu file hay network packet. Layout object có thể thay đổi khi nâng JDK, đổi GC, bật compact headers hoặc chạy JVM vendor khác.

### 5.2. `final` không đồng nghĩa field được cố định vị trí byte

`final` có các quy tắc Java riêng về gán và visibility. Nó không cam kết trường `final long id` phải nằm ở một offset byte cụ thể hoặc có cùng address với bản build trước.

## 6. Object reference và field reference: compressed oops

Java reference có **ngữ nghĩa chỉ tới object**, không phải một pointer arithmetic API. HotSpot 64-bit có thể dùng **compressed ordinary object pointers** (compressed oops) cho nhiều reference fields/array slots; một encoded reference thường nhỏ hơn native machine pointer. Riêng **compressed class pointer** là cơ chế biểu diễn tham chiếu tới runtime class metadata trong header: hai thứ phục vụ **hai loại dữ liệu khác nhau**.

~~~text
Java reference field  ------> Java heap object       (oop)
HotSpot object header ------> Klass/class metadata   (class pointer)
~~~

Với mode classic JDK 17, `UseCompressedOops`, `UseCompressedClassPointers` và `ObjectAlignmentInBytes` có thể ảnh hưởng width/alignment. **Không giả định flags tồn tại hoặc có ý nghĩa giống hệt ở release mới**: chẳng hạn `UseCompressedClassPointers` đã trở thành obsolete trên HotSpot JDK 27, theo JDK 27 release notes. Trước khi đọc flags, chạy `java -version` và đối chiếu tài liệu đúng JDK build.

Mã Java dùng `a == b` vẫn so cùng object identity kể cả GC di chuyển object, reference nén hoặc giải nén. Bài compressed oops kế tiếp phân tích riêng encoding, shift/base và các ngưỡng bộ nhớ, tránh nhân đôi phép tính tại đây.

## 7. Alignment và padding: vì sao thêm field không đồng nghĩa tăng shallow size?

**Alignment** là ràng buộc layout của HotSpot, không phải Java API contract. Khi cần căn shallow size lên bội số của 8 byte, ví dụ minh họa:

~~~text
raw bytes = 17, alignUp(17, 8) = 24
raw bytes = 22, alignUp(22, 8) = 24
~~~

Do đó một field mới có thể chiếm khoảng trống đã có mà không tăng shallow size. Ngược lại, thêm một field có thể vượt ranh giới alignment và làm object tăng thêm một block, không chỉ số byte của field. **Internal padding** nằm giữa các thành phần do placement/alignment; **tail padding** thường nằm cuối object để làm tròn tổng size.

Con số `raw bytes` chỉ hợp lệ khi đã biết offsets từ JVM/tool; không tính bằng cách đoán field declaration order. Muốn tối ưu memory, đo bằng JOL/Instrumentation/heap profiler trên đúng release thay vì thiết kế phụ thuộc vào offsets cụ thể.

## 8. Array là object có hợp đồng Java riêng, không chỉ vùng byte

Java bảo đảm **array identity, runtime type, length cố định và kiểm tra chỉ số**, chẳng hạn:

~~~java
int[] scores = new int[3];
String[] names = new String[3];
System.out.println(scores.length); // 3
System.out.println(scores[0]);     // 0
System.out.println(names[0]);      // null
~~~

Mỗi câu `new int[3]` hay `new String[3]` cấp **một array object** theo ngữ nghĩa; `String[]` chứa **các reference slots**, không tự tạo ba String objects. Index ngoài bounds ném `ArrayIndexOutOfBoundsException`; dereference null array có thể ném `NullPointerException`. Arrays implement `Cloneable` và `Serializable` theo quy tắc Java.

Trong **HotSpot classic header**, một mảng thường có runtime metadata/header, **length** và vùng elements cùng padding. Nhưng Java không chỉ định độ lớn header, vị trí length, offset element đầu tiên hoặc contiguous native layout thành **public ABI**. Primitive elements và reference slots có semantics khác nhau; tổng memory giữ bởi `Object[]` không chỉ bằng shallow size của mảng nếu các referents sống riêng.

Bài học layout chuyên sâu ở hàng trước cho ví dụ `int[]`/`Object[]` byte arithmetic có điều kiện. Ở đây câu hỏi cốt lõi là: **cùng chương trình Java chạy đúng trên VM có physical array layout khác nhau không? Có.**

## 9. Ví dụ kích thước array trên HotSpot 64-bit JDK 17 truyền thống

Giả sử cụ thể **array base offset = 16 byte**, reference slots 4 byte, `int` slots 4 byte, alignment 8 byte, classic compressed header:

| Array | Phép tính minh họa | Shallow bytes |
| --- | --- | ---: |
| `new int[0]` | `alignUp(16 + 0×4, 8)` | 16 |
| `new int[1]` | `alignUp(16 + 1×4, 8)` | 24 |
| `new int[3]` | `alignUp(16 + 3×4, 8)` | 32 |
| `new Object[3]` | `alignUp(16 + 3×4, 8)` | 32 |

**Không phải kết quả cố định của Java SE.** Mỗi `Object[]` chỉ chứa reference slots; nếu phần tử trỏ tới một `byte[]` khác, mảng `byte[]` đó có shallow size riêng. Việc reference được dùng ở nhiều slots cũng **không tự nhân bản** object referent.

Ở JVM có compact headers, alignment khác hoặc representation khác, hãy đo lại bằng JOL/Instrumentation. Số đo mới không làm `arr.length` hoặc `arr[0]` thay đổi ngữ nghĩa.

## 10. Mảng nhiều chiều là mảng của các mảng

Java source:

```java
int[][] matrix = new int[2][3];
```

Ngữ nghĩa là một **mảng ngoài** dài 2, mỗi phần tử là reference tới một `int[]` dài 3:

```text
matrix (int[][])
   |
   +--> outer array length = 2
          |
          +--> row 0 → int[] length 3 : [0, 0, 0]
          |
          +--> row 1 → int[] length 3 : [0, 0, 0]
```

Trong mô hình Java, có **ít nhất ba array objects phân biệt theo nhận diện runtime**: outer array và hai inner arrays. Java **không yêu cầu** toàn bộ sáu primitive `int` của các hàng phải nằm trong **một khối bộ nhớ hình chữ nhật liên tục**.

```java
int[][] jagged = new int[2][];
jagged[0] = new int[] {1, 2};
jagged[1] = new int[] {3};
```

Hàng đầu dài 2, hàng sau dài 1. Java hỗ trợ cấu trúc **jagged/ragged arrays** vì mỗi phần tử của outer array là reference tới một array object độc lập.

### 10.1. `int[][]` không phải C-style `int[2][3]`

Trong ngôn ngữ native nhất định, mảng 2D có thể được lưu như một vùng liền khối theo row-major layout. Nhưng Java không cam kết mô hình tương đương. Việc đọc `matrix[i][j]` qua bytecode thể hiện hai bước truy cập mảng theo kiểu reference:

```text
aload ...  (đọc reference outer array)
iload ...  (index i)
aaload     (đọc reference tới row)
iload ...  (index j)
iaload     (đọc int từ row)
```

Đây là sơ đồ nhóm opcode, không phải output cố định của một compiler. Có các null/bounds checks tương ứng từng tầng; HotSpot JIT có thể tối ưu khi chứng minh an toàn nhưng vẫn bảo toàn kết quả quan sát được.

### 10.2. Vấn đề hiệu năng của jagged arrays

Một mảng các mảng có thể:

- Tăng số object và overhead per-array headers.
- Làm truy cập dữ liệu phụ thuộc reference tới hàng.
- Có kích thước hàng linh hoạt và cho phép cấp phát thưa.
- Tạo tính cục bộ cache khác với một mảng primitive phẳng.

Muốn một layout logic phẳng, có thể dùng:

```java
int rows = 2;
int columns = 3;
int[] flat = new int[rows * columns];
flat[1 * columns + 2] = 9;
```

Đây là code minh họa **không phải file Java độc lập**. Mapping `flat[row * columns + col]` do ứng dụng kiểm soát về mặt index, nhưng **vẫn không biến địa chỉ vật lý của Java array thành API công khai**.

Nếu dùng công thức indexing, cần kiểm tra overflow của phép nhân và bounds của row/col khi dữ liệu lớn.

## 11. Compact object headers: thay đổi HotSpot qua các phiên bản JDK

Việc một bài cũ ghi “HotSpot object header luôn là 12 hoặc 16 bytes” cho thấy vì sao chủ đề này phải được gắn với phiên bản JVM.

- **JDK 17 (HotSpot truyền thống):** các sơ đồ 12-byte header (compressed class pointers) và 16-byte header (uncompressed) thường được dùng để giải thích.
- **JDK 24 — JEP 450:** giới thiệu **Compact Object Headers** dưới dạng **experimental feature**.
- **JDK 25 — JEP 519:** đưa compact object headers thành **product feature**, có thể bật bằng `-XX:+UseCompactObjectHeaders` trên HotSpot hỗ trợ; trong JDK 25 feature **không bật mặc định**.
- **JDK 27 — JEP 534:** compact object headers được **bật mặc định** trên cấu hình HotSpot hỗ trợ, với khả năng tắt bằng flag trong bản tương ứng.

Mục tiêu của compact headers là giảm chi phí header của object trên HotSpot 64-bit truyền thống xuống khoảng **8 byte**, thay đổi cách lưu thông tin trạng thái/kiểu object. Những chi tiết thực tế tùy VM/configuration và có thể tiếp tục phát triển.

### 11.1. Vì sao mốc JDK quan trọng?

Một công thức kiểu:

```text
object size = 12-byte header + fields + padding
```

có thể hợp lý như phép ước lượng cho **một bản JDK 17 HotSpot với compressed class pointers**, nhưng sai nếu được dùng như chân lý cho **mọi JDK 27 HotSpot**. Điều này minh họa tính **không portable** của kiến thức layout thấp.

### 11.2. Compact headers không đổi hợp đồng Java

Sau khi VM thay header:

- `obj.getClass()` vẫn trả đúng class runtime.
- `synchronized` vẫn tuân theo Java Memory Model và quy tắc monitor.
- `System.identityHashCode(obj)` vẫn có ngữ nghĩa đã đặc tả.
- `obj.field` vẫn đọc/ghi đúng field theo các quy tắc Java.
- Arrays vẫn có `length` và kiểm tra chỉ số.

Điều thay đổi là **cách VM biểu diễn trạng thái và metadata**, không phải việc Java bổ sung một thuộc tính `headerBytes` có thể gọi từ source.

### 11.3. Đọc flags theo JDK mục tiêu

Ví dụ trên HotSpot JDK 25 tương ứng:

```bash
java -XX:+UseCompactObjectHeaders -version
```

Trên HotSpot JDK 27 khi tính năng được hỗ trợ:

```bash
java -XX:-UseCompactObjectHeaders -version
```

Những câu lệnh là **lệnh khảo sát tùy phiên bản**, không chạy nguyên xi trên JDK 17 vì flag có thể không tồn tại. Trong ứng dụng thật hãy xem `java -version` và `java -XX:+PrintFlagsFinal -version` thay vì đoán cấu hình.

## 12. GC, địa chỉ và identity: “object nằm ở offset X” có thể chỉ đúng trong một thời điểm

Một garbage collector có thể **di chuyển object** trong heap khi compact/relocate. Khi đó một giá trị native address nếu ai đó lấy được qua công cụ nội bộ có thể thay đổi. Java references vẫn tiếp tục dẫn tới **object logic tương ứng** theo ngữ nghĩa JVM.

### 12.1. `==` cho reference không phải so sánh địa chỉ byte thô

Khi viết:

```java
Object a = new Object();
Object b = a;
System.out.println(a == b);
```

Kết quả là `true` vì cả hai reference chỉ tới **cùng object**. Đó là ngữ nghĩa reference identity, không buộc Java expose hay cố định một địa chỉ máy.

### 12.2. `identityHashCode` không phải địa chỉ object

`System.identityHashCode(obj)` trả một giá trị `int` theo hợp đồng của Java. Giá trị này:

- **Không là offset field**.
- **Không buộc bằng địa chỉ bộ nhớ**.
- **Không là định danh duy nhất tuyệt đối**: các object khác nhau có thể có cùng hash code.
- Có thể liên quan chi tiết implementation object-header/hash storage, nhưng ứng dụng không được dựa vào cách lưu nội bộ.

Không dùng `identityHashCode` để tính native pointers hoặc khẳng định GC di chuyển object hay không.

### 12.3. JIT có thể không materialize object trong mọi tình huống

HotSpot JIT có thể dùng escape analysis/scalar replacement trong những trường hợp tối ưu hóa để tránh một số allocation thực tế, trong khi code Java vẫn có biểu hiện ngữ nghĩa tương đương.

Vì vậy **“một lần viết `new` luôn tạo một heap object 16 byte nhìn thấy trên mọi heap snapshot”** không phải giả định tốt cho profiling. Cần phân biệt **allocation semantics** với cách runtime tối ưu ở một thời điểm.

## 13. Chương trình Java 17+ chứng minh semantic contract, không giả định layout

Tạo tệp **`ObjectLayoutContractDemo.java`**:

```java
import java.io.Serializable;
import java.util.Arrays;

public final class ObjectLayoutContractDemo {
    static final class Packet {
        byte kind;
        long id;
        Object payload;
    }

    public static void main(String[] args) {
        Packet first = new Packet();
        Packet alias = first;

        System.out.println("same object: " + (first == alias));
        System.out.println("default byte: " + first.kind);
        System.out.println("default long: " + first.id);
        System.out.println("default reference is null: "
                + (first.payload == null));

        int[] numbers = new int[3];
        System.out.println("array length: " + numbers.length);
        System.out.println("primitive defaults: "
                + Arrays.toString(numbers));

        int[] sameArray = numbers;
        sameArray[1] = 7;
        System.out.println("alias observes change: "
                + Arrays.toString(numbers));

        Object[] references = new Object[2];
        references[0] = first;
        System.out.println("reference slot 1 null: "
                + (references[1] == null));

        int[][] jagged = new int[2][];
        jagged[0] = new int[] {1, 2};
        jagged[1] = new int[] {3};

        System.out.println("outer length: " + jagged.length);
        System.out.println("row lengths: " + jagged[0].length
                + "," + jagged[1].length);
        System.out.println("rows are distinct objects: "
                + (jagged[0] != jagged[1]));

        System.out.println("array is Object: "
                + (numbers instanceof Object));
        System.out.println("array is Cloneable: "
                + (numbers instanceof Cloneable));
        System.out.println("array is Serializable: "
                + (numbers instanceof Serializable));
    }
}
```

Chạy bằng JDK 17 trở lên:

```bash
javac --release 17 ObjectLayoutContractDemo.java
java ObjectLayoutContractDemo
```

**Stdout chính xác:**

```text
same object: true
default byte: 0
default long: 0
default reference is null: true
array length: 3
primitive defaults: [0, 0, 0]
alias observes change: [0, 7, 0]
reference slot 1 null: true
outer length: 2
row lengths: 2,1
rows are distinct objects: true
array is Object: true
array is Cloneable: true
array is Serializable: true
```

### 13.1. Ví dụ trên chứng minh điều gì?

- Reference identity: `first == alias` là true.
- Field defaults: `byte` và `long` mặc định 0; reference mặc định null.
- Array `length` và primitive defaults.
- Aliasing: hai reference cùng trỏ một mảng, nên sửa qua alias quan sát được.
- Outer array có các inner arrays với độ dài khác nhau.
- Java arrays thuộc nhóm object và implement hai marker interfaces đặc biệt.

### 13.2. Ví dụ không chứng minh điều gì?

Nó **không** biết:

- Packet có shallow size bao nhiêu byte.
- `id` hay `payload` ở byte offset nào.
- Header của `Packet` là 8, 12 hay 16 byte.
- Các references đang được nén hay không.
- Địa chỉ RAM/native cụ thể của arrays.
- GC sẽ di chuyển hoặc giữ nguyên những objects ấy.

Chương trình có thể cho **cùng stdout** trên hai JVM với bố trí hoàn toàn khác nhau. Đó chính là tính di động được Java/JVM nhắm tới.

## 14. Dùng JOL để quan sát implementation hiện tại

**JOL — Java Object Layout** là công cụ OpenJDK cho phép khảo sát layout và shallow sizes của objects dưới các VM configurations khác nhau. Tài liệu nguồn: [OpenJDK JOL](https://openjdk.org/projects/code-tools/jol/).

Thông thường bạn có thể dùng JOL CLI:

```bash
java -jar jol-cli.jar internals java.lang.Integer
```

Trong câu lệnh trên, `jol-cli.jar` là tên giả định của JAR CLI đã được tải/xây dựng từ OpenJDK JOL; không có JAR này sẵn trong mọi JDK. Có thể cần đường dẫn thực tế theo phiên bản JOL của bạn. Nếu JOL đã được tích hợp vào build bằng dependency, có thể dùng API `ClassLayout.parseInstance(...)`.

### 14.1. Những phần thường có trong output JOL

```text
VM mode / pointer compression / object alignment
Object internals:
  OFFSET   SIZE   TYPE / DESCRIPTION
  ...
Instance size: ...
Space losses: ...
```

**Đây là hình dạng output minh họa**, không phải dữ liệu đo thực tế của chương trình ở trên.

Các dòng quan trọng:

- **VM mode:** kiến trúc và khả năng pointer encoding.
- **Object alignment:** số byte alignment của một cấu hình.
- **Offset:** vị trí field/header trong cách HotSpot/JOL xác định/suy luận.
- **Instance size:** shallow size dưới cấu hình tương ứng.
- **Space losses:** vùng padding theo cách công cụ tính.

Một số chế độ JOL có thể **suy luận/simulate** layout thay vì đọc địa chỉ vật lý tuyệt đối; phiên bản JOL phải tương thích với VM hiện tại, nhất là khi có compact object headers.

### 14.2. Không chuyển output JOL thành schema giao tiếp

Nếu JOL báo `Packet.id` có offset 16 trong HotSpot JDK 17, **không hardcode offset 16** vào file format, binary protocol hoặc code logic chung.

Một phép đo đúng ở cấu hình A không đồng nghĩa còn đúng ở cấu hình B. Việc đổi JDK, cờ alignment, compressed references hoặc fields/superclass có thể làm offsets và sizes thay đổi.

### 14.3. JOL đo shallow size, không tự động là tổng retained heap

Một object có thể chứa references tới nhiều objects khác. `ClassLayout.parseInstance(obj)` quan sát **một object**; muốn phân tích **object graph/retained memory**, cần thêm công cụ/phân tích phù hợp như JOL `GraphLayout`, heap dump analyzer hoặc profiler, và xử lý vấn đề **shared references**.

JOL cũng là công cụ diagnostic/học tập. Không nên dùng nó làm cơ chế runtime contract bắt buộc trong code business.

## 15. `Instrumentation.getObjectSize` đo gì và không đo gì?

Java Instrumentation API có phương thức:

```java
long size = instrumentation.getObjectSize(someObject);
```

Đây chỉ là **đoạn mã minh họa bên trong Java agent** có `Instrumentation` instance, không phải chương trình Java độc lập. API cần cách khởi động/attach agent hợp lệ; không có biến `instrumentation` tự xuất hiện trong Java thông thường.

Tài liệu API mô tả kích thước theo nghĩa **implementation-specific approximation of the amount of storage consumed by the specified object**. Vì vậy:

- Nó **không** phải toán tử Java `sizeof` portable.
- Nó **không** bao gồm toàn bộ những objects mà reference fields trỏ tới.
- Nó **không** nhất thiết mô tả native memory, thread stacks hay metadata.
- Nó không cố định kích thước theo class name cho mọi cấu hình JDK.

Dùng `Instrumentation.getObjectSize` để đo tương đối trên **môi trường đã ghi nhận**, không biến kết quả thành invariant của ứng dụng.

### 15.1. Vì sao `Runtime.totalMemory()` cũng không cho object size?

`Runtime.totalMemory()` và `freeMemory()` liên quan dung lượng heap do JVM quản lý, chịu ảnh hưởng GC, allocation buffer, background activity, JIT và VM ergonomics. Lấy chênh lệch trước/sau tạo **một object** thường quá nhiễu để kết luận size chính xác.

Để đo footprint của cấu trúc nhiều objects:

- Dùng JOL/JFR/heap dump/allocated-bytes profiler.
- Cân nhắc GC và escape analysis.
- Lặp phép đo với warm-up và workload đại diện.
- Ghi rõ version/flags.
- Phân biệt shallow size, retained size và tổng memory resident process.

## 16. `Unsafe`, field offsets và những rủi ro cần tránh

Một số code low-level có thể tìm offset của field thông qua API không được Java SE cam kết ổn định cho ứng dụng phổ thông (ví dụ một số phương thức của `sun.misc.Unsafe`).

Rủi ro chính:

1. **Field offsets có thể thay đổi** khi VM nâng cấp hoặc đổi cờ.
2. Vi phạm Java Memory Model có thể tạo lỗi concurrency rất khó debug.
3. Mã đọc/ghi raw memory sai có thể phá hỏng trạng thái runtime và gây lỗi nghiêm trọng.
4. Encoded/compressed references không đồng nhất với native raw pointers.
5. GC có thể di chuyển objects; suy ra native address từ giá trị reference là không đáng tin.
6. Module encapsulation/JDK access restrictions có thể ngăn code dựa vào internal APIs.

**Đừng viết bộ serialize bằng cách quét bytes của Java object instance theo offsets cố định.** Cách này nguy hiểm với reference fields, object headers, GC và mọi thay đổi layout.

### 16.1. Có trường hợp nào cần điều khiển layout thật không?

Nếu yêu cầu là một cấu trúc **off-heap** hoặc giao tiếp native với ABI xác định, chọn giải pháp có contract phù hợp theo JDK mục tiêu:

- `ByteBuffer` với thứ tự byte rõ ràng, offset do ứng dụng định nghĩa.
- Binary serialization formats (Protobuf, Avro, custom format có versioning).
- Foreign Function & Memory API (`MemorySegment`, `MemoryLayout`) ở JDK hỗ trợ ổn định tính năng và theo đúng API docs.

Các công cụ này **định nghĩa layout cho dữ liệu do ứng dụng quản lý**, không khiến instance layout của một Java class trên heap trở thành portable.

## 17. Tối ưu memory footprint: hướng nào an toàn hơn hardcode layout?

### 17.1. Đếm objects và đo lường thay vì đoán

Một triệu objects rất nhỏ vẫn có thể tốn nhiều heap do:

- Header/object alignment overhead.
- Fields và reference slots.
- Các objects được tham chiếu, chẳng hạn strings, lists, boxed values.
- Hash table entries, collection backing arrays.
- GC/runtime accounting và fragmentation.

Ví dụ một `List<Integer>` có thể giữ nhiều wrapper objects và backing reference array; một `int[]` giữ primitive values trong một array object theo kiểu lưu của HotSpot thông thường. Nhưng lựa chọn dữ liệu phải dựa vào **API semantics, nullable, cập nhật, phân phối giá trị, GC và benchmark**.

### 17.2. Chọn cấu trúc dữ liệu theo workload

- Với dữ liệu số học dày đặc, primitive arrays có thể giảm số object so với boxing.
- Với data sparse/jagged, cấp phát từng inner array theo nhu cầu có thể giảm memory.
- Dùng collections phù hợp với nhu cầu truy cập, thêm/xóa và khả năng thay đổi.
- Giảm số lượng objects trung gian khi profiling chứng minh ảnh hưởng.
- Dùng streaming/batching khi không cần giữ toàn bộ dữ liệu trong heap.

Không kết luận “primitive array luôn nhanh nhất” cho mọi tình huống; cần đo end-to-end.

### 17.3. False sharing và field proximity

Hai biến được thread khác nhau cập nhật có thể gây contention trên cache line nếu chúng vô tình nằm trong cùng cache line (*false sharing*). Nhưng Java **không hứa** thứ tự fields hoặc một kích thước cache line cố định.

Một số HotSpot builds/cấu hình hỗ trợ kỹ thuật padding hoặc `@Contended` cho mục tiêu đặc biệt, nhưng đây là phần **không portable và tùy JVM/module access/flags**. Không dùng annotation nội bộ như một bảo đảm layout phổ quát.

Hãy cân nhắc các hướng portable trước: chia state theo thread, giảm shared writes, dùng concurrency structures phù hợp, benchmark trong điều kiện thực.

## 18. Các sai lầm thường gặp

| Nhận định sai | Cách hiểu đúng |
| --- | --- |
| “JVMS bắt buộc object có 12-byte header.” | JVMS `2.7 **không bắt buộc cấu trúc nội bộ cụ thể**. |
| “Mọi object HotSpot 64-bit có header 16 byte.” | Tùy compressed class pointers và compact headers; 12, 16, 8 có thể gặp ở cấu hình khác nhau. |
| “Một field `boolean` đúng 1 byte sẽ luôn tăng object size 1 byte.” | Alignment/padding và field layout có thể khiến shallow size không tăng hoặc tăng nhiều hơn. |
| “Fields nằm theo đúng thứ tự khai báo.” | Không là cam kết field offsets của Java. |
| “`getDeclaredFields()` trả về thứ tự layout.” | Reflection metadata không phải công cụ portable đo byte offsets. |
| “Reference trên 64-bit JVM luôn 8 byte.” | Compressed oops có thể dùng encoded references 4 byte. |
| “Compressed oops và compressed Klass pointers là một.” | Khác mục đích: Java object references và class metadata pointer. |
| “`System.identityHashCode(obj)` là địa chỉ object.” | Là giá trị hash theo hợp đồng Java, không phải native pointer. |
| “GC không bao giờ chuyển địa chỉ object.” | Một số garbage collectors có thể relocate objects. |
| “Một `new` luôn tạo object hiện hữu ở heap snapshot.” | JIT có thể tối ưu allocation theo điều kiện cho phép. |
| “Mảng `int[][]` là một khối primitive liên tục.” | Là mảng của các mảng; không có cam kết một block 2D liền khối. |
| “Array header/length luôn ở offset 12.” | Có thể gặp ở cấu hình cụ thể, không phải ABI Java. |
| “Java `boolean[]` luôn bit-packed.” | Không là bảo đảm; HotSpot thông thường dùng một byte/element. |
| “`sizeof(Object[])` chỉ bằng số elements × reference width.” | Còn header/alignment/padding; các object được trỏ tới lại riêng. |
| “JOL nói size 32 byte nghĩa là mọi JVM luôn 32.” | Kết quả gắn với VM/JDK/flags và JOL mode. |
| “`Instrumentation.getObjectSize` trả retained graph size chính xác.” | API là approximation của **một object** theo implementation; không đi sâu graph. |
| “Compact Object Headers chỉ là dự án tương lai.” | Đã có JDK 24 experimental, JDK 25 product và JDK 27 default trên HotSpot phù hợp. |
| “Bật compact headers là Java thay đổi ngữ nghĩa `equals`/`getClass`.” | Header nội bộ thay đổi, các bảo đảm Java phải được giữ. |
| “Hardcode field offsets giúp binary protocol nhanh và portable.” | Rủi ro khi nâng JDK/VM/flags; hãy dùng format có schema/layout rõ ràng. |

## 19. Bài tập tự kiểm tra kèm đáp án

**Câu 1.** JVM Specification có bắt object của mọi VM dùng mark word và Klass pointer không?

**Đáp án:** Không. JVMS `2.7 không bắt buộc cấu trúc nội bộ cho object; mark word và Klass pointer là thuật ngữ triển khai HotSpot.

**Câu 2.** `Object a = new Object(); Object b = a;` có bảo đảm `a == b` là true không?

**Đáp án:** Có, vì hai references cùng trỏ một object. Không cần biết native address của object để kết luận.

**Câu 3.** `class X { int a; boolean b; }` có bảo đảm field `b` nằm ngay sau 4 byte của `a` không?

**Đáp án:** Không. Field order, alignment và padding là chi tiết triển khai.

**Câu 4.** Vì sao `new int[1]` có thể lớn hơn 4 byte nhiều lần?

**Đáp án:** Vì array là object, có header/metadata và padding, không chỉ một phần tử primitive.

**Câu 5.** `new Object[3]` có tự tạo ba instance `Object` không?

**Đáp án:** Không. Nó tạo một reference array, ba phần tử ban đầu null.

**Câu 6.** `new int[2][3]` có bao nhiêu array objects trong mô hình Java?

**Đáp án:** Một outer array và hai inner `int[]` arrays, tổng ba object; Java không cam kết sáu phần tử primitive thuộc một 2D block vật lý.

**Câu 7.** Shallow size của `Object[]` có tính toàn bộ objects được tham chiếu không?

**Đáp án:** Không. Shallow size chỉ tính array object và slots; retained graph/size cần phân tích các objects liên quan.

**Câu 8.** JDK 17 HotSpot báo một object header 12 byte. Có thể hardcode điều đó cho JDK 27 không?

**Đáp án:** Không. Compact object headers và các VM options có thể thay đổi layout; JDK 27 dùng compact headers theo feature/default tương ứng.

**Câu 9.** `System.identityHashCode` có thể dùng thay `addressOf` không?

**Đáp án:** Không. Identity hash không là địa chỉ và có thể trùng giữa objects khác nhau.

**Câu 10.** JVM 64-bit có thể lưu reference field 4 byte không?

**Đáp án:** Có, trên HotSpot với compressed oops và cấu hình hỗ trợ. Đây không phải cam kết của Java.

**Câu 11.** JOL là compiler specification hay công cụ đo implementation?

**Đáp án:** JOL là công cụ quan sát/phân tích layout của runtime/VM mode; output không trở thành ABI bắt buộc của ngôn ngữ.

**Câu 12.** Vì sao đổi `-XX:ObjectAlignmentInBytes` có thể làm object shallow size thay đổi?

**Đáp án:** Vì VM phải sắp xếp/pad object theo alignment; làm tròn và cách encode pointers có thể đổi.

**Câu 13.** Bật compressed class pointers có nghĩa mọi references tới object khác cũng được nén không?

**Đáp án:** Không. Compressed Klass pointers và compressed ordinary object pointers là hai khái niệm khác nhau.

**Câu 14.** Cần format binary portable cho native integration, có nên quét bytes của Java class instance không?

**Đáp án:** Không. Nên định nghĩa schema/byte order và dùng API quản lý layout như ByteBuffer, serializer hoặc FFM phù hợp JDK mục tiêu.

**Câu 15.** Chương trình demo chạy giống hệt trên hai JDK nhưng JOL báo size khác nhau. Đây có phải bug của Java không?

**Đáp án:** Không. Java bảo đảm ngữ nghĩa, không đảm bảo shallow object layout giống nhau giữa JVM/JDK builds.

## 20. Checklist khi phân tích layout hoặc memory performance

1. **Chụp môi trường:** `java -version`, vendor, kiến trúc, GC, JVM flags.
2. **Xác định điều cần đo:** shallow size, retained size, allocation rate, heap usage hay native memory.
3. **Dùng công cụ phù hợp:** JOL cho layout, Instrumentation cho approximate object size, JFR/heap dumps cho allocation/retention, GC metrics cho biến động heap.
4. **Xác định có compressed oops và compressed class pointers hay không.**
5. **Kiểm tra compact headers** nếu dùng JDK 24+.
6. **Xem alignment**, field inheritance, class shape và array component type.
7. **Đừng biến layout quan sát được thành contract của dữ liệu.**
8. **Benchmark workload thực tế** thay vì tối ưu chỉ theo một object sample.
9. **Kiểm tra lại sau nâng JDK hoặc đổi JVM flags**.
10. **Ưu tiên các giải pháp theo Java semantics và API contract** để ứng dụng tiếp tục hoạt động trên các JVM khác nhau.

### Tổng kết cuối cùng

```text
Java/JVMS cam kết:
  Object reference semantics
  Field values và access rules
  Array length, element defaults, bounds checks
  Class/array runtime type rules và memory model

HotSpot có thể lựa chọn:
  Object header format và bytes
  Mark word / Klass metadata representation
  Instance field offsets, order, packing, alignment
  Compressed references
  Array header/base offsets và element slot widths
  Garbage collector và object relocation
  Compact headers theo phiên bản JDK

Hệ quả:
  Cùng một Java program
    ├── có thể in cùng một stdout
    └── nhưng có object/array sizes và offsets khác nhau
```

Bài học cốt lõi là: **biết layout của một HotSpot build giúp phân tích hiệu năng và bộ nhớ; không được nhầm layout ấy với một cam kết của Java.**

### Tài liệu tham khảo chuẩn

- [JVMS `2.7 — Representation of Objects (Java SE 25)](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.7): JVM không áp đặt cấu trúc object nội bộ duy nhất.
- [JVMS `2.4 — Reference Types and Values](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.4): class instances, arrays và references.
- [JLS `10 — Arrays (Java SE 25)](https://docs.oracle.com/javase/specs/jls/se25/html/jls-10.html): ngữ nghĩa arrays, length, elements, nhiều chiều.
- [JLS `4.12.5 — Initial Values of Variables](https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.12.5): default values của fields/array components.
- [OpenJDK JOL — Java Object Layout](https://openjdk.org/projects/code-tools/jol/): công cụ thực nghiệm để quan sát class/object/array layouts.
- [OpenJDK JEP 450 — Compact Object Headers (Experimental)](https://openjdk.org/jeps/450): giới thiệu trong JDK 24.
- [OpenJDK JEP 519 — Compact Object Headers](https://openjdk.org/jeps/519): product feature từ JDK 25.
- [OpenJDK JEP 534 — Compact Object Headers by Default](https://openjdk.org/jeps/534): mặc định mới trong JDK 27.
- [Java Instrumentation API — getObjectSize (Java SE 25)](https://docs.oracle.com/en/java/javase/25/docs/api/java.instrument/java/lang/instrument/Instrumentation.html#getObjectSize(java.lang.Object)): approximation of object size theo implementation.
- [Oracle Java `java` command options (JDK 25)](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html): JVM flags về compressed pointers, alignment và compact headers.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hotspot-objects-005">Compressed oops và compressed class pointers trong JVM 64-bit</a>

<details>
<summary>Xem chi tiết</summary>

# Compressed oops và compressed class pointers trong JVM 64-bit

## 1. Mục tiêu

Trên một máy 64-bit, địa chỉ native thường có độ rộng 64 bit. Nếu mọi trường tham chiếu Java trong object và mọi phần tử **Object[]** đều phải chiếm 8 byte, các ứng dụng có nhiều object sẽ tốn đáng kể bộ nhớ cho các reference. **HotSpot** có cơ chế **compressed ordinary object pointers (compressed oops)** để lưu nhiều tham chiếu đến Java heap objects bằng dạng mã hóa hẹp thường là **32 bit**. Một cơ chế khác, **compressed class pointers**, dùng dạng hẹp để biểu diễn **con trỏ tới thông tin loại đối tượng** trong object header.

Hai cơ chế giải quyết **hai loại con trỏ khác nhau**, tuy liên quan đến hiệu suất, kích thước object và cách HotSpot cấp phát vùng nhớ. Chúng không thay đổi kiểu tham chiếu trong Java source, không làm JVM từ 64-bit trở thành 32-bit, và không được quy định bắt buộc trong Java Virtual Machine Specification (JVMS).

Sau bài học, bạn có thể:

- Phân biệt **oop**, **narrowOop**, **Klass***, **narrowKlass** và **java.lang.Class**.
- Giải thích vì sao 32-bit encoded reference vẫn định vị được heap có dung lượng xấp xỉ hàng chục GiB nhờ **alignment**, **base** và **shift**.
- Đọc sơ đồ object header HotSpot 64-bit theo cấu hình **class pointers** được nén hoặc không nén.
- Tính kích thước giả định của một object hoặc reference array trong các cấu hình nén/không nén, có xét **padding**.
- Phân biệt ngưỡng **32 GiB thường được nhắc đến** với giới hạn tuyệt đối; hiểu tác động của **ObjectAlignmentInBytes** và heap placement.
- Biết các cờ **UseCompressedOops**, **UseCompressedClassPointers**, **ObjectAlignmentInBytes**, **CompressedClassSpaceSize**, và cách kiểm tra flag value thực chạy.
- Biết lợi ích, chi phí encode/decode, tác động tới cache locality, GC và khi nào benchmark thực tế mới đủ để kết luận.
- Dùng công cụ **jcmd**, **PrintFlagsFinal**, **JOL** và Java 17+ để quan sát mà không suy diễn sai từ số liệu.

**Phạm vi:** những layout minh họa dùng HotSpot 64-bit **Java 17**, với **object headers truyền thống** và alignment 8 byte, trừ khi ghi rõ khác. Các JVM implementations hoặc các JDK phiên bản khác có thể dùng tổ chức khác. Đây là cơ chế **HotSpot implementation**, không phải nghĩa vụ của mọi JVM tuân thủ Java SE.

## 2. Thuật ngữ: oop khác Klass pointer

Trong tài liệu và mã nguồn HotSpot:

- **oop** là một tham chiếu native-level tới **Java heap object**. Tên gọi lịch sử là *ordinary object pointer*.
- **narrowOop** là dạng encoded hẹp của một oop, thường 32 bit khi compressed oops bật.
- **Klass*** là con trỏ native-level tới cấu trúc metadata HotSpot mô tả **runtime class** của object.
- **narrowKlass** là dạng mã hóa hẹp của con trỏ **Klass***, thường dùng trong **object header** khi compressed class pointers bật.
- **java.lang.Class** là một **Java heap object** (class mirror) có thể quan sát từ Java bằng **SomeType.class**; nó **không phải** đối tượng cùng loại với cấu trúc native **Klass***.

Bảng so sánh:

| Khái niệm | Nó trỏ tới | Vị trí sử dụng tiêu biểu | Có đồng nghĩa Java reference không? |
| --- | --- | --- | --- |
| **oop** | Object/array trên Java heap | Object field, array element, nhiều đường xử lý VM | Là cách HotSpot biểu diễn reference tới Java object |
| **narrowOop** | Object/array trên Java heap sau giải mã | Object field/array element khi nén | Là encoding của oop |
| **Klass*** | Class metadata trong VM | Object header/VM internals | **Không** phải Java object reference thông thường |
| **narrowKlass** | Class metadata qua decoding | Object header khi nén class pointer | **Không** phải narrowOop |
| **Class<?> mirror** | Java object biểu diễn class | Reflection: **String.class**, **getClass()** | Là Java object, được tham chiếu bằng oop như object khác |

**Lỗi nghiêm trọng:** “Compressed class pointer là một compressed oop trỏ tới java.lang.Class object”. Không đúng. Object header chứa class/klass metadata pointer; Java Class mirror là đối tượng heap liên kết với metadata nhưng hai thứ không đồng nhất.

### 2.1. Hai luồng tham chiếu trong cùng một object

~~~text
Java:
class Container {
    Object item;
}

Một Container instance trên HotSpot (minh họa):
+------------------------------------------------+
| Mark word                                      |
| Encoded class pointer (narrowKlass) ----------+ |----> Klass metadata của Container
| Field item (narrowOop) -----------------------|----> Java heap object được item tham chiếu
| Padding nếu cần                               |
+------------------------------------------------+

Container.class ------> java.lang.Class mirror trên heap
                            |
                            +-- runtime liên kết với Klass metadata
~~~

Một field **item** có thể là **null** hoặc tham chiếu Object bất kỳ; class pointer trong object header biểu thị runtime type của chính **Container object**. Chúng không cùng nhiệm vụ, không dùng cùng address base và không thể hoán đổi.

## 3. Vì sao 64-bit VM cần tham chiếu nén?

Giả sử chương trình có một object chứa bốn reference fields và hàng triệu object tương tự. Nếu một reference chiếm 8 byte thay vì 4 byte, riêng bốn fields có thể tăng thêm **16 byte trên mỗi object trước khi xét alignment**. Ở một triệu objects, chênh lệch raw field storage đã là khoảng 16 triệu byte; tác động thực tế còn phụ thuộc object headers, padding và graph layout.

Đối với **Object[]**, mỗi phần tử là một reference slot, nên một mảng nhiều phần tử có thể hưởng lợi rõ:

~~~text
Reference slots của Object[8]:

Không nén:
  [8B][8B][8B][8B][8B][8B][8B][8B] = 64 B

Compressed oops:
  [4B][4B][4B][4B][4B][4B][4B][4B] = 32 B

Chưa bao gồm header, alignment hoặc đối tượng thực sự được tham chiếu.
~~~

Lợi ích không chỉ là **heap footprint**:

- Ít byte reference hơn có thể cải thiện **CPU cache locality**.
- Ít dữ liệu cần chuyển từ RAM/cache có thể giảm **memory bandwidth**.
- Một số bộ phận GC duyệt object graph có ít byte reference để đọc/ghi.
- Với workload chịu giới hạn bởi cache/memory, hiệu quả có thể tích cực.

Đổi lại, runtime phải **encode/decode** reference ở những vị trí thích hợp. JIT compiler có thể tối ưu hoặc gộp thao tác; do đó không thể tuyên bố luôn nhanh hơn hoặc luôn chậm hơn nếu không benchmark. Tác động phụ thuộc heap size, object graph, CPU architecture, GC, compiler và cấu hình.

## 4. “32-bit reference mà vẫn trỏ được heap 32 GiB” nghĩa là gì?

Nếu 32 bit biểu diễn **byte address trực tiếp**, ta có tối đa:

~~~text
2^32 byte = 4 GiB
~~~

Nhưng một đối tượng thường được căn chỉnh ở địa chỉ chia hết cho **8 byte** trong HotSpot 64-bit cấu hình mặc định. Ba bit thấp của **offset** theo đơn vị byte luôn bằng 0. Thay vì lưu địa chỉ từng byte, VM có thể lưu **offset theo đơn vị 8 byte**.

Ý tưởng:

~~~text
Địa chỉ/offset thực:   0x00000000_00000018 = 24 byte
Mã hóa dạng đơn vị 8:  24 / 8 = 3

32-bit narrow value:  3
Decode (shift=3):     3 << 3 = 24
~~~

Với 2^32 khả năng mã hóa, mỗi bước biểu diễn 8 byte, phạm vi lý thuyết là:

~~~text
2^32 * 8 byte = 2^35 byte = 32 GiB
~~~

Đây là lý do nhiều tài liệu nói compressed oops hỗ trợ heap khoảng **32 GiB** với 8-byte alignment. **Không** phải mọi 32-bit narrow value trỏ tới một object hợp lệ hoặc mỗi object chỉ chiếm 8 byte; đây là phạm vi **địa chỉ hóa**, không phải số object thực.

### 4.1. Base + shift

Một sơ đồ giải mã phổ biến cho **non-null** narrow oop:

~~~text
decoded_address = base + (unsigned(narrowOop) << shift)
~~~

Trong đó:

- **base**: địa chỉ cơ sở (hoặc 0) theo encoding mode.
- **shift**: số bit dịch, thường là **3** với object alignment 8 byte.
- **narrowOop**: giá trị encoded 32-bit.
- **decoded_address**: địa chỉ native-level tương ứng được HotSpot dùng để tham chiếu object.

Chiều ngược (trong phạm vi các giá trị hợp lệ, alignment thích hợp):

~~~text
narrowOop = (decoded_address - base) >> shift
~~~

**Đây là mô hình hóa**, không phải API Java truy xuất con trỏ. Những chi tiết về NULL encoding, read/write barriers, GC forwarding và vùng địa chỉ đều do VM thực hiện.

### 4.2. Ví dụ số học với base khác 0

Giả sử *một mode* có:

~~~text
base          = 0x0000001000000000
shift         = 3
encoded value = 7
offset        = 7 * 8 = 56 decimal = 0x38

decoded address = 0x0000001000000038
~~~

Đây là **địa chỉ giả định**, không phải địa chỉ thật của một Java object trong chương trình. Nó cho thấy vì sao cả **base** và **shift** cùng cần thiết. Dạng zero-based có base bằng 0 nên phép cộng base có thể biến mất, giúp mã nhanh hơn trong một số tình huống.

### 4.3. Không quên null

Java reference có giá trị **null**. HotSpot bảo đảm encoded narrowOop giá trị 0 có thể được nhận diện như null theo quy tắc implementation. Với các heap-based modes, việc xử lý null có thể cần nhánh/special handling, không thể máy móc suy rằng mọi con trỏ có giá trị 0 decode thành **base** và được Java coi là một object hợp lệ.

Khi tự mô phỏng công thức bằng số nguyên, nhớ đây là minh họa cho **non-null aligned references**; null là trường hợp đặc biệt.

## 5. Các encoding modes của compressed oops

Tài liệu và mã nguồn HotSpot phân biệt những modes nhằm tối ưu phép giải mã tùy vị trí cấp phát Java heap:

| Mode (tên thường gặp) | Ý tưởng | Shift/base |
| --- | --- | --- |
| **Unscaled / 32-bit** | Heap nằm trong phạm vi địa chỉ nhỏ, encoded value có thể dùng trực tiếp như offset byte | Có thể shift=0, base=0 |
| **Zero-based** | Heap nằm trong vùng địa chỉ phù hợp cho base=0 | Thường shift=3 nếu cần địa chỉ hóa xa hơn 4 GiB |
| **Non-zero / heap-based** | Dùng base địa chỉ khác 0 và offset được scale | Base != 0, shift thường 3 |
| **Disjoint-base** (mode triển khai phù hợp) | Chọn base với bit pattern thuận lợi để tối ưu giải mã | Vẫn là encoded offset + base logic, có thể tối ưu bằng OR |

**Không thể ép mọi JVM/OS/JDK chỉ sử dụng đúng một mode.** VM phải có khả năng reserve heap ở vùng địa chỉ phù hợp; ASLR, address space layout, kích thước heap và tham số VM tác động tới mode được chọn.

Một dòng log HotSpot có thể diễn đạt kiểu:

~~~text
Compressed Oops mode: Zero based
Oop shift amount: 3
~~~

Đây là ví dụ hình thức log, **không phải stdout cố định** cho mọi lần chạy hoặc mọi JDK. Mọi con số về heap base/mode phải đo từ tiến trình thực.

### 5.1. 32 GiB không phải một vách giới hạn độc lập với mọi cờ

Câu “compressed oops chỉ hoạt động khi -Xmx dưới 32G” là đơn giản hóa quá mức:

- Với alignment 8 byte, **32 GiB** là phạm vi lý thuyết từ 32-bit values dịch 3 bit.
- Các chi tiết reservation/guard/alignment có thể khiến ngưỡng bật tắt thực tế thấp hơn con số đúng 32 GiB trên một JVM cụ thể.
- **-XX:ObjectAlignmentInBytes** có thể thay đổi độ căn chỉnh của object, khiến shift và miền địa chỉ hóa thay đổi.
- Ví dụ alignment **16** cho phạm vi lý thuyết **2^32 × 16 = 64 GiB**, nhưng chi phí padding/fragmentation cũng có thể tăng.
- VM có thể tự tắt compressed oops hoặc từ chối cấu hình nếu yêu cầu không thể hỗ trợ; không nên suy từ **-Xmx** đơn lẻ rằng cờ cuối cùng chắc chắn bật.

**Không coi tăng alignment là tối ưu mặc định.** Tăng object alignment khiến nhiều objects nhỏ tốn padding hơn; phần tham chiếu tiết kiệm được có thể bị lấn át. Chọn bằng phép đo thực tế và kiểm tra JVM flags cuối cùng.

## 6. Compressed class pointers dùng để làm gì?

Mỗi Java heap object trong HotSpot có **object header** chứa thông tin quản lý object và thông tin về **class metadata** của object. Trong header truyền thống 64-bit:

- **Mark word**: vùng thông tin runtime của object; ý nghĩa bit phụ thuộc trạng thái/phiên bản HotSpot (hash/locking/GC age...).
- **Klass pointer**: tham chiếu tới metadata mô tả runtime class của object.
- Với **array object**, còn có **array length** trong header truyền thống.

Khi **compressed class pointers** bật, thành phần class pointer trong header có thể được lưu dưới dạng **narrowKlass 4 byte** thay vì **Klass* 8 byte**.

**NarrowKlass khác narrowOop**:

| Câu hỏi | Compressed oops | Compressed class pointers |
| --- | --- | --- |
| Đang nén thứ gì? | Tham chiếu tới **Java heap objects** | Tham chiếu tới **Klass metadata** |
| Xuất hiện ở đâu? | Java reference fields, arrays và vị trí thích hợp khác | Object header class pointer |
| Miền địa chỉ đích? | Java heap | Vùng class metadata/class space |
| Encoding base/shift? | Gắn với object heap | Gắn với địa chỉ class metadata, có quy tắc riêng |
| Ảnh hưởng trực tiếp? | Kích thước reference field/array element | Kích thước class-pointer component trong header |
| Cờ HotSpot liên quan? | **UseCompressedOops** | **UseCompressedClassPointers** |

Với class pointer compression, VM có thể dành một vùng địa chỉ phục vụ class metadata để narrowKlass có thể encode/decode phù hợp. **CompressedClassSpaceSize** là một tùy chọn liên quan, nhưng không phải heap-size limit và không đồng nghĩa toàn bộ Metaspace capacity.

### 6.1. Klass*, Class mirror và class loader

~~~text
Java heap:
   anObject ----> Object header ----> Klass* metadata
   SomeType.class ----> java.lang.Class mirror object

Native class metadata:
   Klass structure mô tả SomeType
     - class loader identity
     - fields/methods/runtime metadata...
~~~

Quan hệ giữa Java mirror và VM metadata do HotSpot quản lý. Khi đọc:

~~~java
Class<?> type = anObject.getClass();
~~~

Java code nhận **Class mirror**. Điều này **không** có nghĩa VM chỉ lấy chính giá trị narrowKlass 32-bit rồi trả nó như một Java reference nguyên xi. Runtime phải cung cấp đúng Java object reference tới mirror theo cơ chế nội bộ.

**Cùng binary name ở hai defining class loaders** có thể tạo hai runtime classes và hai Klass metadata khác nhau. Vì vậy class pointer của instances tương ứng phải trỏ tới đúng metadata của runtime class đó.

## 7. Object header và kích thước trên HotSpot 64-bit truyền thống

Bảng sau giả định layout **HotSpot 64-bit Java 17**, object headers truyền thống, object alignment 8 byte, không có biến thể header khác. Những số này là **ví dụ điển hình**, không phải quy định của JVMS và không cam kết giống nhau trên mọi runtime.

| Header của object thường | Có compressed class pointer | Không compressed class pointer |
| --- | ---: | ---: |
| Mark word | 8 B | 8 B |
| Klass pointer | 4 B | 8 B |
| Tổng header **trước padding** | **12 B** | **16 B** |

Với mảng trong layout truyền thống, phần **array length** thường thêm **4 B**:

| Header của array | Có compressed class pointer | Không compressed class pointer |
| --- | ---: | ---: |
| Mark word | 8 B | 8 B |
| Klass pointer | 4 B | 8 B |
| Array length | 4 B | 4 B |
| Raw header bytes | 16 B | 20 B |
| Vị trí phần tử đầu tiên | Theo căn chỉnh phần tử và layout, thường 16 B cho Object[] compressed | Có thể cần padding để vị trí phần tử tham chiếu phù hợp |

**Chú ý:** compressed oops quyết định độ rộng element references; compressed class pointer quyết định độ rộng header pointer. Vì vậy **phải xét hai cờ**, không thể chỉ nói “bật compressed oops thì mọi header là 12 byte” trong mọi cấu hình.

### 7.1. Ví dụ layout của Holder có ba references

~~~java
// Mã minh họa về layout, không phải yêu cầu thuộc tính bộ nhớ của Java:
final class Holder {
    Object first;
    Object second;
    Object third;
}
~~~

Giả sử HotSpot 64-bit traditional headers, object alignment 8 byte.

**Cả compressed oops và compressed class pointers đều bật**:

~~~text
Offset  Bytes   Thành phần
0       8       Mark word
8       4       narrowKlass
12      4       narrowOop first
16      4       narrowOop second
20      4       narrowOop third
------------------------------
Total raw          24 bytes
Rounded to 8       24 bytes
~~~

**Cả hai đều tắt** (wide object references và wide klass pointer):

~~~text
Offset  Bytes   Thành phần
0       8       Mark word
8       8       Klass*
16      8       oop first
24      8       oop second
32      8       oop third
------------------------------
Total raw          40 bytes
Rounded to 8       40 bytes
~~~

Chênh lệch minh họa là **40 - 24 = 16 byte/object**, tức 40% của kích thước wide. Một triệu Holder instances về mặt **shallow instance size lý tưởng** có chênh lệch 16 triệu byte trước khi xét GC overhead, allocation metadata khác và những objects được tham chiếu.

**Không tuyên bố “một object Java luôn có header 12 byte”**: ngoài class pointer width còn có JDK/VM mode, array kind, header implementation, alignment và các quyết định cấp phát.

### 7.2. Ví dụ object có hai int fields: padding có thể triệt tiêu tiết kiệm

~~~java
final class Point {
    int x;
    int y;
}
~~~

Với cùng giả định HotSpot 64-bit:

~~~text
Compressed klass:
  header 12 + x 4 + y 4 = 20 -> align8 = 24 B

Wide klass:
  header 16 + x 4 + y 4 = 24 -> align8 = 24 B
~~~

Cả hai đều 24 byte do alignment. Điều đó cho thấy **4 byte giảm ở header không chắc chắn giảm shallow size cuối cùng của mọi loại object**; padding có thể hấp thụ phần tiết kiệm.

### 7.3. Ví dụ Object[1000]

Giả sử array header compressed klass dài 16 byte, compressed oop element 4 byte, 8-byte alignment:

~~~text
Object[1000] compressed:
  16 + 1000 * 4 = 4016 B
  align8 => 4016 B
~~~

Với wide klass và wide element references, header phần tử có thể bắt đầu ở offset 24:

~~~text
Object[1000] wide:
  24 + 1000 * 8 = 8024 B
  align8 => 8024 B
~~~

Chênh lệch shallow size minh họa là **4008 byte** cho array này, **không** tính 1000 objects mà các phần tử có thể trỏ tới. Nếu array chỉ chứa null, bản thân backing array vẫn có các slots; việc không có object được tham chiếu không xóa bộ nhớ của slots.

### 7.4. Primitive arrays và fields

**int[]**, **byte[]**, **long[]** chứa primitive elements, không chứa Java object references ở từng phần tử. Vì vậy **compressed oops không tự làm phần tử int từ 4 byte thành 2 byte** hoặc phần tử long từ 8 thành 4.

Class pointer compression vẫn có thể ảnh hưởng **header của primitive array**, nhưng element widths là chuyện khác.

| Thành phần | Bị đổi độ rộng trực tiếp bởi compressed oops? |
| --- | --- |
| Object reference field | Có trong layout HotSpot phù hợp |
| Object[] element reference | Có |
| int/float field | Không |
| long/double field | Không |
| byte/char/boolean primitives | Không trực tiếp bởi oop compression |
| Klass pointer trong header | Thuộc compressed **class pointers**, không phải oop fields |
| Đối tượng Java mà reference trỏ tới | Vẫn là object, kích thước riêng |

## 8. Vì sao hai cơ chế đều liên quan nhưng không phải một?

Nhiều HotSpot configurations mặc định bật cả hai cơ chế. Tuy vậy cần phân biệt:

- **UseCompressedOops** điều khiển representation của các oop locations thích hợp.
- **UseCompressedClassPointers** điều khiển representation của klass references trong object headers theo khả năng HotSpot.
- Tập điều kiện/chính sách bật từng flag có thể khác theo JDK version, kiến trúc và quan hệ giữa các flags.
- Không suy từ **UseCompressedOops=true** mà **không kiểm tra** thì mọi môi trường đều chắc **UseCompressedClassPointers=true**.
- Không suy từ “class metadata được nén” rằng toàn bộ **Metaspace** hoặc mọi pointer trong JVM đều giảm nửa.

Trong JDK 17/HotSpot, compressed class pointers gắn với một vùng **compressed class space** được quản lý cùng cơ chế Metaspace phù hợp. Theo tài liệu tuning Oracle Java 17, **CompressedClassSpaceSize** có default được nêu là **1 GiB** trong cấu hình tương ứng; giá trị cờ thực tế có thể khác do version/ergonomics/explicit overrides.

**Compressed class space không phải Java heap.** Không tăng **-Xmx** để “mở thêm chỗ cho narrowKlass metadata” theo kiểu suy luận trực tiếp; cần xem class metadata counts, Metaspace/CCS flags và native memory budget.

## 9. Phép giải mã reference có làm Java code khác đi không?

Không. Theo ngôn ngữ Java, những biểu thức:

~~~java
Object a = new Object();
Object b = a;
Object c = new Object();
System.out.println(a == b);
System.out.println(a == c);
~~~

có ý nghĩa **identity comparison** của Java objects, độc lập việc VM lưu oop ở dạng wide hay compressed. Việc VM phải decode narrowOop trước khi sử dụng một object không làm **==** thành so sánh chuỗi bytes encoded theo một contract công khai.

Những thứ Java code **không** được suy ra:

- Pointer address thực từ **System.identityHashCode**.
- Kích thước reference slot từ **Class.getName()**.
- Memory address của Object qua **Object.toString()**.
- NarrowOop payload từ **equals** hoặc một phép cast.
- Object được xếp liên tục, không di chuyển bởi GC.

Java reference là kiểu giá trị trừu tượng; HotSpot có thể nén, giải nén, giữ giá trị trong thanh ghi, và GC có thể di chuyển objects, miễn ngữ nghĩa Java được giữ nguyên.

## 10. Ví dụ Java 17+ số 1: reference semantics bất biến theo cờ nén

Tạo file **ReferenceSemanticsDemo.java**:

~~~java
public class ReferenceSemanticsDemo {
    static final class Node {
        Node next;
        int value;

        Node(int value) {
            this.value = value;
        }
    }

    public static void main(String[] args) {
        Node a = new Node(7);
        Node same = a;
        Node other = new Node(7);
        a.next = other;

        Node[] nodes = {a, same, other};

        System.out.println("sameRef=" + (a == same));
        System.out.println("differentObject=" + (a != other));
        System.out.println("sameValue=" + (a.value == other.value));
        System.out.println("arrayAlias=" + (nodes[0] == nodes[1]));
        System.out.println("link=" + (a.next == nodes[2]));
    }
}
~~~

Biên dịch/chạy trên JDK 17+:

~~~bash
javac --release 17 -encoding UTF-8 ReferenceSemanticsDemo.java
java ReferenceSemanticsDemo
~~~

**Stdout:**

~~~text
sameRef=true
differentObject=true
sameValue=true
arrayAlias=true
link=true
~~~

Chạy thêm trên một **HotSpot 64-bit** hỗ trợ cờ sau:

~~~bash
java -XX:-UseCompressedOops ReferenceSemanticsDemo
~~~

**Stdout Java-level vẫn như trên**, vì compressed oop chỉ là representation nội bộ. Nhưng shallow size của objects/arrays có thể đổi. Không coi việc stdout giống nhau là bằng chứng **cờ đã được bật/tắt đúng ý**: phải đọc các final flag values theo mục tiếp theo.

### 10.1. Điều không thể đo bằng ví dụ này

Ví dụ trên **không** hiển thị addresses, header bits hay số byte của reference. Nó chỉ xác nhận semantics của Java được giữ. Muốn xem layout HotSpot thực, dùng JOL/diagnostic VM tools. Không nên cố viết Java source đọc private native pointers hoặc dùng unsafe memory hacks trong bài tập nền tảng.

## 11. Ví dụ Java 17+ số 2: tính toán layout minh họa (không phải introspection)

Tạo file **LayoutArithmeticDemo.java**:

~~~java
public class LayoutArithmeticDemo {
    static int alignUp(int bytes, int alignment) {
        return ((bytes + alignment - 1) / alignment) * alignment;
    }

    static int holderSize(int headerBytes, int referenceBytes,
                          int references, int alignment) {
        return alignUp(headerBytes + referenceBytes * references, alignment);
    }

    static int arraySize(int headerBytes, int elementBytes,
                         int length, int alignment) {
        return alignUp(headerBytes + elementBytes * length, alignment);
    }

    public static void main(String[] args) {
        int compressedHolder = holderSize(12, 4, 3, 8);
        int wideHolder = holderSize(16, 8, 3, 8);
        int compressedArray = arraySize(16, 4, 1000, 8);
        int wideArray = arraySize(24, 8, 1000, 8);

        System.out.println("holderCompressed=" + compressedHolder);
        System.out.println("holderWide=" + wideHolder);
        System.out.println("arrayCompressed=" + compressedArray);
        System.out.println("arrayWide=" + wideArray);
        System.out.println("holderSavings=" + (wideHolder - compressedHolder));
        System.out.println("arraySavings=" + (wideArray - compressedArray));
    }
}
~~~

Chạy:

~~~bash
javac --release 17 -encoding UTF-8 LayoutArithmeticDemo.java
java LayoutArithmeticDemo
~~~

**Stdout chính xác:**

~~~text
holderCompressed=24
holderWide=40
arrayCompressed=4016
arrayWide=8024
holderSavings=16
arraySavings=4008
~~~

Chương trình dùng **tham số layout giả định** của cấu hình HotSpot truyền thống. Nó không truy cập memory layout của VM đang chạy và sẽ in cùng số ngay cả khi bạn tắt compressed oops. Phần quan trọng là hiểu **tính toán + alignment**, không nhầm kết quả số học này với công cụ đo heap.

### 11.1. Hạn chế của công thức

**alignUp(raw, 8)** chỉ mô phỏng rounding ở cấp object size. HotSpot có thể sắp xếp lại fields, chèn padding nội bộ và bố trí header/array theo cấu hình runtime. Với class có mixed-width fields và inheritance, công thức lấy header cộng sum(field sizes) có thể không phản ánh chính xác layout. Cần đo class cụ thể.

## 12. Cách kiểm tra cờ JVM đang bật thật

Trên **HotSpot/OpenJDK/Oracle JDK 17 64-bit**, dùng các lệnh:

~~~bash
java -XX:+PrintFlagsFinal -version
~~~

Dòng output có thể gồm các flags:

~~~text
UseCompressedOops
UseCompressedClassPointers
ObjectAlignmentInBytes
CompressedClassSpaceSize
MaxHeapSize
MaxMetaspaceSize
~~~

Thực tế, **PrintFlagsFinal in rất nhiều flags**; tìm các tên trên bằng công cụ search của terminal. Không khẳng định một thứ tự dòng hay giá trị bool cố định, vì ergonomic flags có thể phụ thuộc phiên bản, bộ nhớ máy, container và các options khác.

Trên Linux/macOS có thể lọc:

~~~bash
java -XX:+PrintFlagsFinal -version 2>&1 \
  | grep -E 'UseCompressedOops|UseCompressedClassPointers|ObjectAlignmentInBytes|CompressedClassSpaceSize|MaxHeapSize'
~~~

Trong PowerShell:

~~~powershell
java -XX:+PrintFlagsFinal -version 2>&1 |
  Select-String 'UseCompressedOops|UseCompressedClassPointers|ObjectAlignmentInBytes|CompressedClassSpaceSize|MaxHeapSize'
~~~

**Chỉ so sánh giá trị cùng điều kiện startup.** Nếu ứng dụng production chạy trong container dùng thêm memory limits, các cờ do ergonomics chọn có thể khác một lệnh java trần trên laptop.

### 12.1. Bật/tắt nén trong thí nghiệm

Ví dụ (chỉ với HotSpot implementation hỗ trợ):

~~~bash
java -Xmx512m -XX:+UseCompressedOops ReferenceSemanticsDemo
java -Xmx512m -XX:-UseCompressedOops ReferenceSemanticsDemo
~~~

Dùng **PrintFlagsFinal** với cùng flags để xác nhận final state:

~~~bash
java -Xmx512m -XX:-UseCompressedOops -XX:+PrintFlagsFinal -version
~~~

Không tự động cố bật một cờ không được cấu hình/JVM hỗ trợ trong production. Một số flags có dependencies, cần VM quyết định giá trị khả thi; kết quả quyết định cuối cùng phải kiểm tra trong output.

**Đừng nhầm:** **-Xmx512m** là max heap size; nó không trực tiếp giới hạn số byte của **class metadata**, không cho biết used heap, và không nói memory đang được process commit thật sự là 512 MiB.

### 12.2. Quan sát compression mode và GC logs

HotSpot có các logging tags cho compressed oops/heap ở một số versions/configurations. Ví dụ định hướng:

~~~bash
java -Xlog:gc+heap+coops=debug -version
~~~

Không mọi build/mode đều in cùng dữ liệu. Dùng:

~~~bash
java -Xlog:help
~~~

để xem tags hợp lệ trên JVM đang chạy. Khi có log, bạn có thể tìm **Compressed Oops mode**, heap address range hoặc oop shift; đây là diagnostic output, **không phải API ổn định**.

### 12.3. jcmd cho tiến trình thật

Nếu dùng HotSpot và JDK tools:

~~~bash
jcmd
jcmd <PID> help
jcmd <PID> VM.flags
jcmd <PID> VM.metaspace
~~~

Thay **<PID>** bằng PID thực. Một số commands/cấp độ chi tiết phụ thuộc JDK build và quyền truy cập; luôn đọc **jcmd <PID> help** trước khi áp dụng. **VM.metaspace** giúp điều tra class metadata, không xuất chính xác kích thước từng oop field của mỗi object.

## 13. Dùng Java Object Layout (JOL) để quan sát object thực

**JOL (Java Object Layout)** là công cụ của OpenJDK để khám phá/ước lượng memory layout JVM. Nó có thể hiển thị kích thước field references, offset, header, alignment và sự khác biệt giữa cấu hình VM.

Ví dụ thao tác khi đã có đúng JOL CLI JAR:

~~~bash
java -jar jol-cli.jar internals java.lang.Integer
~~~

Hoặc dùng JOL **jol-core** qua dependency phù hợp và API **ClassLayout** trong một dự án Java. Đây là ví dụ command phụ thuộc JAR được cung cấp từ OpenJDK JOL, **không phải lệnh Java 17 mặc định**; nếu chưa tải JOL, chương trình không chạy chỉ với JDK.

Tại output JOL truyền thống có thể thấy:

~~~text
(object header: mark)       8 bytes
(object header: class)      4 bytes nếu narrowKlass
field Integer.value         4 bytes
Instance size               16 bytes với cấu hình minh họa
~~~

Ví dụ này là dữ liệu **có điều kiện** (HotSpot 64-bit, compressed class pointers và alignment tương ứng). Khi thay JVM flags, field layout và padding có thể khác.

**Ưu tiên đo bằng JOL**, thay vì dùng **System.getProperty("sun.arch.data.model")** rồi suy ra reference width: 64-bit process không đồng nghĩa mọi object reference field 8 byte.

### 13.1. So sánh thí nghiệm công bằng

Để kiểm tra tác động nén trên memory footprint:

1. Dùng cùng **JDK build**, cùng GC và cùng dataset.
2. Bật nén rồi tắt nén ở các JVM processes riêng biệt.
3. Kiểm tra flags **UseCompressedOops** và **UseCompressedClassPointers** cuối cùng.
4. Giữ **-Xms/-Xmx**, ObjectAlignmentInBytes và những tham số liên quan ở mức kiểm soát được.
5. Dùng JOL đo shallow size object/array cụ thể.
6. Dùng heap histogram/profiler đo retained graph sizes nếu cần.
7. Benchmark có warm-up và measurement đúng phương pháp để xem throughput/latency, không suy từ một lần chạy nhỏ.
8. Xem GC allocation rates/pauses và RSS/native memory budget nếu mục tiêu là memory.

**Giá trị đo nào đang so?** Shallow size khác retained size; allocated bytes khác live heap; reserved memory khác committed memory, khác RSS. Nén references tác động rõ đến một số loại storage, không đồng nhất các con số đo này.

## 14. Tác động đối với GC, CPU cache và JIT

### 14.1. CPU cache và băng thông

Một object graph nặng về pointers thường được duyệt bằng thao tác dereference. Với 4-byte narrowOop thay cho 8-byte wide oop, các phần chứa references có thể nhỏ hơn:

- Cache line chứa được nhiều slots hơn.
- Nhiều phần của graph nằm vừa L1/L2/L3 hơn.
- VM/GC cần đọc/ghi ít byte reference hơn trong một số pha.

Nhưng mỗi lần dereference có thể cần tính **base + shift**, và JIT compiler/CPU architecture có thể áp dụng tối ưu addressing để giảm chi phí. Vì vậy **nén references không đồng nghĩa tốc độ tăng đúng 2×** hoặc mọi lookup không có overhead.

### 14.2. GC barriers và object movement

GC có thể di chuyển Java objects; references cần được cập nhật/diễn giải theo đúng encoding. Một số collectors có read/write barriers hoặc load barriers khác nhau, ảnh hưởng đường thực thi nhưng vẫn bảo toàn semantics.

Đừng tự xem compressed oop như **số nguyên 32 bit bất biến về địa chỉ object** khi GC chuyển object. Representation và update logic là trách nhiệm runtime. Công thức encode/decode chỉ giúp hiểu miền địa chỉ hóa, không phải lời mời sửa object references ở cấp Java source.

### 14.3. JIT có thể thay đổi cách cất reference

Không phải mọi Java reference liên tục nằm trong một 4-byte memory slot. JIT có thể giữ reference trong CPU register, unbox/scalar-replace/loại bỏ object allocation theo điều kiện escape analysis, hoặc dùng các intermediate formats. Cơ chế compressed oops áp dụng ở **những oop locations phù hợp trong HotSpot implementation**, không là quy tắc “mọi biến reference trong mọi thời điểm chiếm đúng 4 byte”.

## 15. Khi nào nén không tiết kiệm như kỳ vọng?

1. **Object quá ít reference fields:** Phần tiết kiệm thấp; header reduction có thể bị alignment padding hấp thụ.
2. **Object nhỏ nhưng alignment tăng:** Nếu tăng ObjectAlignmentInBytes lên 16/32 để hỗ trợ phạm vi lớn hơn, padding trung bình có thể tăng.
3. **Application memory chủ yếu là primitive arrays:** Giảm width của oop slots ít tác động trực tiếp tới phần tử primitives.
4. **Ứng dụng giữ nhiều byte[]/char[]:** Nội dung buffer là bytes/chars, không phải object-reference slots.
5. **Bộ nhớ native/direct buffers lớn:** Compressed oops ảnh hưởng Java object refs, không tự giảm vùng **DirectByteBuffer native memory**, thread stacks hoặc code cache.
6. **Heap có ít live objects:** Thay đổi reserved capacity không có nghĩa footprint thực của graph lớn.
7. **Object sharing và dedup:** Tỷ lệ objects và references thực tế quan trọng hơn chỉ số lượng class declarations.
8. **Code nặng CPU arithmetic:** Cache benefit có thể nhỏ, encode/decode cost tùy VM/JIT.
9. **Heap requirement vượt miền compression thuận lợi:** VM có thể chọn wide oops hoặc cần cấu hình alignment khác, gây trade-off mới.
10. **Compressed class space trở thành bottleneck:** Đó là câu chuyện class metadata capacity/unloading, không tự giải bằng giảm Java oop field width.

## 16. Phân biệt ba ngưỡng bộ nhớ hay bị trộn

| Ngưỡng | Nó giới hạn gì? | Ví dụ/cờ liên quan |
| --- | --- | --- |
| **Khoảng 32 GiB với shift=3** | Phạm vi địa chỉ hóa lý thuyết của narrowOop với 8-byte scale | UseCompressedOops, ObjectAlignmentInBytes |
| **Java heap maximum** | Mức max heap JVM được cấu hình/ergonomics chọn | -Xmx, MaxHeapSize |
| **Compressed class space** | Vùng quản lý class metadata cần cho narrowKlass encoding | UseCompressedClassPointers, CompressedClassSpaceSize |

Một chương trình có Java heap 8 GiB không cần 8 GiB compressed class space. Một chương trình có hàng trăm nghìn generated classes có thể chịu áp lực metadata ngay khi live object heap rất nhỏ.

### 16.1. CompressedClassSpaceSize không phải giới hạn mọi metadata

HotSpot Metaspace có nhiều cấu trúc/kiểu cấp phát. Phần **compressed class space** chỉ là một phần liên quan đến narrowKlass. Tăng hoặc giảm **CompressedClassSpaceSize** không đồng nghĩa toàn bộ **Metaspace** được tăng/giảm đúng lượng đó.

Khi thấy **OutOfMemoryError: Compressed class space** hoặc **OutOfMemoryError: Metaspace**, hãy xem class loading growth, class-loader retention, GC class unloading và flags; tránh chỉ đổi -Xmx rồi kết luận đã điều chỉnh đúng.

## 17. Phân biệt điểm “oop” trong JVM và ordinary Java object

HotSpot dùng thuật ngữ **oop** cho cách diễn đạt nội bộ về Java heap references. Đây không phải từ khóa của Java source:

~~~java
Object ref = new Object();
~~~

Biến **ref** có kiểu Java reference; compiler/JVM quản lý representation. Trong class file **aload/astore/getfield/putfield/aaload/aastore** hoạt động trên các loại JVM values/references theo JVMS. Bytecode không chứa một loại field descriptor riêng có tên **narrowOop**.

**Không có descriptor nén tham chiếu:** Field kiểu **Object** vẫn có descriptor **Ljava/lang/Object;**, mảng Object[] vẫn **[Ljava/lang/Object;** bất kể -XX:+/-UseCompressedOops.

Tương tự, **Klass*** và **narrowKlass** không phải Java source types, không thể import như class API Java thông thường, không hiển thị trong **javap -s** như field declared by user.

### 17.1. Class.getClassLoader() và class pointer

Một class được định danh runtime bằng tên nhị phân và defining loader. Khi object header mang Klass metadata pointer, metadata đó liên quan đúng runtime class. Nhưng từ Java chỉ có thể quan sát an toàn qua:

~~~java
Class<?> type = object.getClass();
ClassLoader loader = type.getClassLoader();
~~~

Đây là Java-level Reflection. Không dùng output **getClassLoader().toString()** như địa chỉ native của Klass; cũng không suy rằng **Class object** và **Klass metadata** là cùng object trên heap.

## 18. Những hiểu lầm phổ biến

1. **“JVM 64-bit bắt buộc mọi Java reference chiếm 8 byte.”** Sai trong HotSpot có compressed oops.
2. **“Compressed oops làm object address chỉ có 32 bit.”** Sai: 32-bit **encoded value** giải mã ra native address có thể rộng hơn.
3. **“2^32 × 8 byte là đúng 32 GB trong mọi tình huống.”** Chỉ là miền địa chỉ hóa lý thuyết của 8-byte scale; heap placement/flags/guard có ràng buộc thực.
4. **“UseCompressedOops và UseCompressedClassPointers là cùng một flag.”** Sai: hai loại pointer khác nhau.
5. **“Class pointer là reference tới java.lang.Class Java object.”** Sai: trỏ tới Klass metadata.
6. **“NarrowOop và narrowKlass dùng cùng base/shift.”** Sai: hai miền địa chỉ đích và encoding settings khác nhau.
7. **“Mọi heap objects nhỏ đi đúng 4 byte khi nén class pointer.”** Sai: alignment padding có thể khiến shallow size không đổi.
8. **“Tất cả Java objects có header 12 byte.”** Sai: phụ thuộc mode, JDK, đối tượng thường/mảng và implementation.
9. **“Object[1000] chỉ tốn 4000 byte khi nén.”** Sai: còn array header và alignment.
10. **“int[] được nén mỗi int xuống 2 byte.”** Sai: primitive element widths không do UseCompressedOops quyết định.
11. **“String.intern pool chính là vùng compressed class pointers.”** Sai: hai cơ chế không liên quan như vậy.
12. **“Chỉ tăng -Xmx là tăng compressed class space.”** Sai: hai vùng/cờ thuộc các tài nguyên khác nhau.
13. **“JOL mô tả chuẩn layout mọi JVM.”** Sai: JOL đo/ước lượng theo VM, không thay JVMS.
14. **“Tắt compressed oops luôn tăng hiệu năng vì bỏ shift.”** Sai: cache/memory costs có thể lớn hơn decode cost.
15. **“Bật compressed oops luôn giảm một nửa heap.”** Sai: hiệu quả tùy tỷ trọng references, header, padding, primitive data và GC.
16. **“Một object Java không thể bị GC di chuyển vì có encoded address.”** Sai: HotSpot/GC duy trì references khi cần.
17. **“Có thể đọc compressed oop bằng System.identityHashCode.”** Sai: hash không phải memory address.
18. **“Class files có descriptor narrowOop.”** Sai: compression là runtime implementation detail.
19. **“32 GiB là ngưỡng tuyệt đối không thể vượt với compression.”** Sai: object alignment có thể thay đổi miền, đi kèm trade-offs.
20. **“HotSpot Metaspace và Java heap cùng được kiểm soát bởi -Xmx.”** Sai: native metadata cần xem giới hạn riêng.

## 19. Bài tập có đáp án

**Bài 1.** Trên 64-bit HotSpot, nếu một Object reference field lưu bằng narrowOop 4 byte, VM có còn dùng native 64-bit addresses không?

**Đáp án:** Có. Encoded narrowOop là representation hẹp; VM có thể decode thành địa chỉ native đầy đủ khi sử dụng.

**Bài 2.** Với alignment 8 byte, shift thường là bao nhiêu và phạm vi địa chỉ hóa lý thuyết bằng bao nhiêu?

**Đáp án:** Shift 3, vì 2^3=8. Miền 2^32×8 = 2^35 bytes, tức 32 GiB.

**Bài 3.** Tại sao tăng ObjectAlignmentInBytes=16 có thể cho miền 64 GiB trong công thức nhưng lại lãng phí bộ nhớ?

**Đáp án:** Shift 4 tương ứng 16-byte scale; mọi object phải được căn chỉnh theo bước lớn hơn nên padding/fragmentation có thể tăng.

**Bài 4.** Khác biệt giữa narrowOop và narrowKlass?

**Đáp án:** NarrowOop mã hóa reference tới Java heap object; narrowKlass mã hóa con trỏ tới class metadata của object.

**Bài 5.** Trong header truyền thống với 8-byte mark word, compressed klass pointer 4 byte, raw object header là bao nhiêu?

**Đáp án:** 12 byte trước padding cho object thường trong cấu hình giả định.

**Bài 6.** Vì sao Object[1000] không chỉ có 1000×4=4000 byte với compressed oops?

**Đáp án:** Còn array header và alignment; ví dụ compressed klass+oop, alignment8, array header16 → 4016 bytes.

**Bài 7.** Một class Point chỉ có hai int fields. Vì sao có thể 24 bytes trong cả wide và compressed klass modes?

**Đáp án:** Header compressed12+8 primitive=20, round lên 24; header wide16+8=24. Padding triệt tiêu khác biệt.

**Bài 8.** Khi đọc **object.getClass()**, Java nhận Klass* hay Class<?> mirror?

**Đáp án:** Nhận **java.lang.Class mirror object**, không phải raw native Klass*.

**Bài 9.** Nếu JVM báo **OutOfMemoryError: Compressed class space**, nên xem những gì?

**Đáp án:** Class metadata/class-loader growth, compressed class space budget, unloading và native memory; không chỉ tăng -Xmx.

**Bài 10.** Tắt compressed oops làm stdout của chương trình so sánh references thay đổi không?

**Đáp án:** Không nếu chương trình hợp lệ và cùng điều kiện dữ liệu. Java reference identity semantics không phụ thuộc width nội bộ.

**Bài 11.** Ở JVM 64-bit với compressed oops, một local variable có kiểu Object bắt buộc chiếm đúng 4 byte trên native stack?

**Đáp án:** Không. Locals trong JVM model, compiler registers và native stack layout khác với object field/array element encoding.

**Bài 12.** Có thể đo object size chính xác bằng code **Holder.class.getDeclaredFields().length * 4 + 12** không?

**Đáp án:** Không. Cần xét types, superclass fields, field arrangement, alignment, padding và VM flags; dùng JOL/profiler phù hợp.

**Bài 13.** Hai class loaders khác nhau định nghĩa cùng class name. Header class pointer của instances có bắt buộc trỏ tới cùng Klass metadata không?

**Đáp án:** Không. Đây là các runtime classes khác nhau nên metadata tương ứng khác nhau.

**Bài 14.** **-XX:+UseCompressedOops** có bảo đảm final flag bật nếu heap quá lớn/cấu hình không tương thích không?

**Đáp án:** Không nên giả định. Runtime có thể cảnh báo, tự điều chỉnh hoặc từ chối; luôn kiểm tra final flags và VM diagnostics.

## 20. Quy trình thực hành và checklist

Khi đánh giá compressed references cho một ứng dụng HotSpot:

1. **Xác định môi trường**: JDK build, kiến trúc 64-bit, collector, OS, container limits, flags hiện hành.
2. **Đọc final flags**: UseCompressedOops, UseCompressedClassPointers, ObjectAlignmentInBytes, MaxHeapSize, CompressedClassSpaceSize.
3. **Phân tích workload**: nhiều object references/arrays hay chủ yếu là primitive buffers, off-heap memory?
4. **Lấy ví dụ một class cụ thể**: tính shallow size lý thuyết có cả header và padding; không chỉ cộng reference widths.
5. **Dùng JOL/VM tools** để quan sát layout thực; không biến ví dụ giả định thành số liệu production.
6. **Đo performance** với benchmark có warm-up, throughput/latency, GC pauses và memory footprint.
7. **Thận trọng đổi alignment**: mở rộng địa chỉ hóa có thể tăng object padding.
8. **Nếu memory error**: phân biệt Java heap, Metaspace và compressed class space.
9. **Tránh dùng internal pointers từ Java**: không đồng nhất HashCode, Java Class mirror và Klass*.
10. **Ghi chú tính phụ thuộc implementation**: hotspot flags/layout thay đổi theo JDK, không phải yêu cầu JVMS.
11. **Với plugin/dynamic class generation**, quan sát number of classes/loaders và khả năng unloading; có thể tiêu tốn compressed class space dù live heap nhỏ.
12. **Kiểm tra tính đúng đắn trước tối ưu**: thay flag phải giữ nguyên semantics của Java code và được thử nghiệm trên staging/runtime đích.

### Tài liệu đối chiếu

- **Oracle Java SE 17 — The java Command**, đặc biệt UseCompressedOops, ObjectAlignmentInBytes và các VM flags: https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html
- **Oracle Java SE 17 GC Tuning — Other Considerations**, gồm class metadata và compressed class pointers: https://docs.oracle.com/en/java/javase/17/gctuning/other-considerations.html
- **Oracle HotSpot VM Performance Enhancements — Compressed Oops** (giải thích cơ chế, lịch sử): https://docs.oracle.com/en/java/javase/21/vm/java-hotspot-virtual-machine-performance-enhancements.html
- **OpenJDK JOL (Java Object Layout)**: https://github.com/openjdk/jol
- **OpenJDK HotSpot source — CompressedOops implementation**: https://github.com/openjdk/jdk/blob/master/src/hotspot/share/oops/compressedOops.hpp
- **JVMS Java SE 17 — Run-Time Data Areas**, để phân biệt mô hình JVM với implementation layout: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5

**Ghi nhớ cốt lõi:** Compressed oops dùng **narrowOop** để biểu diễn nhiều Java heap references qua 32-bit encoded offsets; compressed class pointers dùng **narrowKlass** để rút gọn phần class-metadata pointer của object header. Cả hai có thể làm nhỏ object graph, nhưng chúng không cùng miền đích, không cùng ý nghĩa và không tự bảo đảm tối ưu trong mọi workload. **32 GiB là mốc thường gặp từ 32-bit × 8-byte scaling**, không phải một câu trả lời tuyệt đối; hãy luôn đo object alignment, heap placement và flags thực tế trên HotSpot/JDK đang chạy.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hotspot-objects-006">Tham chiếu được cập nhật khi GC di chuyển đối tượng</a>

<details>
<summary>Xem chi tiết</summary>

# Tham chiếu được cập nhật khi GC di chuyển đối tượng trong HotSpot

Trong Java, bạn có thể giữ nhiều biến cùng trỏ tới **một object**, truyền nó qua nhiều method, đặt vào array, collection hoặc field, rồi hệ thống vẫn đọc được object ấy sau các lần Garbage Collection (GC). Tuy nhiên, trong nhiều collector của HotSpot, một số **object còn sống được sao chép hoặc di chuyển tới địa chỉ heap mới**. Làm thế nào mọi tham chiếu vẫn hoạt động? Vì sao `a == b` vẫn `true` dù byte biểu diễn tham chiếu có thể thay đổi? Và khi nào một collector thực sự viết lại reference slot, khi nào nó dùng forwarding hoặc barriers?

**Ý chính:** Java reference là cách chương trình tham chiếu tới **cùng một object logic**, không phải lời hứa về một địa chỉ vật lý cố định. Khi collector có di chuyển object, HotSpot dùng các cấu trúc theo dõi đối tượng và những cơ chế cập nhật/chuyển hướng tham chiếu để code Java luôn thấy object đúng. Phương pháp cụ thể phụ thuộc collector, GC phase và JDK version.

Bài học tập trung vào **HotSpot Java 17**, đặc biệt cơ chế G1 evacuation, đồng thời chỉ ra sự khác biệt về thời gian cập nhật reference giữa collector stop-the-world và concurrent relocating collectors như ZGC hoặc Shenandoah. Khi nói về GC roots, `oop`, forwarding pointer, load/write barriers hay remembered sets, đây là **chi tiết triển khai HotSpot**, không phải biểu diễn raw reference do Java Language Specification (JLS) bảo đảm.

## 1. Vấn đề gốc: “đổi chỗ object” khác “đổi danh tính object”

Xét object graph:

```text
Trước GC:

GC root: local x --------┐
GC root: local alias ----┤
Holder.field ------------┤
Array[0] ----------------┤
                         v
                  [Object A]  địa chỉ giả định 0x1000
                      |
                      v
                  [Object B]  địa chỉ giả định 0x1800

Sau một lần GC có di chuyển A:

GC root: local x --------┐
GC root: local alias ----┤
Holder.field ------------┤
Array[0] ----------------┤
                         v
                  [Object A]  địa chỉ giả định 0x9000
                      |
                      v
                  [Object B]  nơi ở tùy collector/GC phase
```

Các địa chỉ `0x1000` và `0x9000` ở đây **chỉ là ký hiệu minh họa**, không phải địa chỉ lấy từ một JVM thật.

Nếu collector đã đưa A từ một vùng heap sang vùng mới, tất cả những **đường tham chiếu còn cần thiết và có thể được sử dụng** phải tiếp tục chỉ tới A về mặt ngữ nghĩa. Với một collector copy/evacuate stop-the-world, các reference slots thích hợp được cập nhật trước khi application threads tiếp tục. Với một collector di chuyển đồng thời, chương trình có thể đi qua barriers/forwarding logic, nên không nhất thiết mọi slot đã mang bit của vị trí mới trong cùng một khoảnh khắc.

Hai phát biểu cần phân biệt:

- **Object identity không đổi:** A vẫn là A, giá trị field và quan hệ aliasing được bảo toàn trong quá trình di chuyển hợp lệ.
- **Địa chỉ lưu trữ có thể đổi:** byte representation hoặc location mà HotSpot dùng để tìm A có thể thay đổi; địa chỉ ấy **không phải Java-level identity**.

Vì vậy, một biến reference Java không nên được hiểu là “hằng số địa chỉ C pointer” mà ứng dụng có thể lưu thành `long` rồi sử dụng tùy ý.

## 2. Java bảo đảm điều gì, HotSpot làm việc gì?

| Cấp độ | Điều có thể dựa vào | Điều không được suy ra |
| --- | --- | --- |
| **Java language** | `==` trên hai reference tới cùng object vẫn kiểm tra cùng identity; field access, method calls và monitor semantics của object vẫn đúng | Địa chỉ object sẽ không đổi |
| **JVM specification** | Objects/arrays được tạo và quản lý theo ngữ nghĩa heap; JVM có thể tự quản lý bộ nhớ và GC | Một thuật toán GC hoặc layout object duy nhất |
| **HotSpot collector** | Collector bảo toàn các references còn sống theo chính sách riêng khi copy/compact/move object | G1, ZGC, Shenandoah phải dùng cùng forwarding data structure hoặc thời điểm fix-up |
| **JIT/runtime** | Code đã tối ưu vẫn phải bảo toàn Java-visible behavior | Mọi local `Object` luôn nằm trong một stack slot vật lý |

Đặc tả JVM mô tả heap và cho phép bộ nhớ object được tự động reclaim. Nó **không** bắt buộc mọi implementation phải dùng **moving GC**. Một collector có thể không di chuyển một số hoặc tất cả object trong một chu kỳ; việc object vẫn ở một vị trí cũ không vi phạm Java.

Thêm nữa, có trường hợp compiler loại bỏ allocation vật lý bằng **escape analysis/scalar replacement** nếu vẫn giữ đúng hành vi quan sát được. Do đó chương trình Java cũng không được dùng kết quả chạy thành công để khẳng định “object này chắc chắn đã được copy từ địa chỉ X sang Y”.

## 3. GC roots: các điểm bắt đầu để tìm reference còn sống

Muốn biết object nào cần giữ và những reference nào phải theo dõi, collector bắt đầu từ một tập **GC roots** và các cấu trúc bổ sung do runtime quản lý.

Một số nguồn roots điển hình trên HotSpot:

- References trong **Java thread frames**, bao gồm local variables, operand-stack values và các vị trí trong register mà compiled code đang sử dụng.
- **Static references** của những class đã được load và vẫn thuộc phạm vi giữ sống cần thiết.
- **JNI local/global handles** và các runtime handles khác theo semantics tương ứng.
- Những references do chính VM/runtime giữ cho thread, class loader, monitor, code cache hoặc cấu trúc hỗ trợ.
- Các roots phục vụ GC phase cụ thể, ví dụ code roots và remembered-set-derived heap roots khi collection chỉ xử lý một phần heap.

**Root không có nghĩa object được giữ mãi:** khi stack frame rời khỏi hoặc reference không còn được coi là reachable, object có thể trở thành đối tượng để reclaim sau các điều kiện/reference processing thích hợp.

### Khác nhau giữa Java local và GC root thực tế

Một biến Java nhìn thấy trong source không nhất thiết vẫn là một **root sống** suốt cả lexical scope. Compiler/JIT có thể xác định local đã không còn được dùng và rút ngắn vùng liveness hợp lệ. Ngược lại, một reference có thể được giữ trong CPU register chứ không nằm nguyên trong memory slot của stack.

HotSpot dùng thông tin chính xác để nhận diện những vị trí chứa object references ở các điểm tương thích với GC, đặc biệt là **safepoints** và metadata liên quan đến compiled code.

**Phân biệt hai tên gần giống:**

- `StackMapTable` trong class file chủ yếu là metadata **bytecode verification**, giúp xác nhận kiểu stack/locals ở control-flow boundaries.
- **Oop maps**/GC maps trong runtime/JIT HotSpot giúp biết các vị trí chứa **object pointers** cần xử lý trong quá trình GC.
  
Chúng không phải một bảng chung có thể hoán đổi tùy ý; chỉ nhìn `javap -v` thấy `StackMapTable` không phải bằng chứng bạn đã xem được toàn bộ GC root map của JIT.

### Đồ thị reachable objects

```text
  GC roots
    |
    +----> object A -----> object C
    |          |
    |          +-------> object D
    |
    +----> object B -----> object C

Object C có thể được giữ bởi HAI đường,
nhưng vẫn chỉ là MỘT object logic.
```

Nếu C được di chuyển, cả những đường tham chiếu **có thể tiếp tục truy cập** C phải trỏ đúng C sau/qua quá trình relocation. Collector cũng phải duy trì thông tin để không copy C thành hai object Java khác identity do có hai roots cùng trỏ tới nó.

## 4. Moving/copying GC thường thực hiện các công việc nào?

Một mô hình giải thích **stop-the-world copying collector** đơn giản:

1. Xác định một tập object/heap regions cần collection; quyết định object nào còn sống theo thuật toán GC.
2. Từ các roots và edges thích hợp, gặp một reference tới object còn sống ở vùng cần evacuate.
3. Copy object sang vùng **to-space/destination** còn chỗ, hoặc xử lý ngoại lệ nếu không thể copy.
4. Ghi nhận ánh xạ **địa chỉ cũ → địa chỉ mới** qua forwarding information hoặc cấu trúc thích hợp.
5. Cập nhật root slots và field/array reference slots phù hợp để chúng trỏ tới object đích; tiếp tục quét reference fields của object vừa copy.
6. Khi relocation/fix-up đủ an toàn, giải phóng/tái sử dụng vùng cũ và cho application tiếp tục.

Ví dụ ký hiệu:

```text
from-space                       to-space

A@0x1000  -------- copy -------> A@0x9000
  ref X ----------- fix-up -----> A@0x9000
  ref Y ----------- fix-up -----> A@0x9000
  ref Z ----------- fix-up -----> A@0x9000

forward(A@0x1000) = A@0x9000

A chỉ là MỘT Java object về ngữ nghĩa;
không được xem A ở hai địa chỉ như hai object độc lập.
```

**Forwarding** giúp collector biết A đã được copy để lần gặp reference tiếp theo không tạo một bản Java object logic thứ hai. Khi collector gặp field/reference trỏ đến địa chỉ cũ, nó tìm địa chỉ mới và cập nhật/chuyển hướng phù hợp.

Câu “mọi reference được sửa ngay lập tức” chỉ đúng nếu đã mô tả **đầy đủ loại collector và giai đoạn**. Trong một collector đồng thời, điều kiện an toàn có thể được bảo đảm bằng **barriers**, metadata và fix-up theo pha, không phải bằng một thời điểm quét hết tất cả các slots.

### Không phải bước nào cũng “copy toàn bộ heap”

Một young collection có thể chỉ xử lý objects trong vùng trẻ; G1 mixed collection xử lý tập regions cụ thể, không phải mọi object trong heap. Collector còn có thể **mark/sweep**, **compact in place**, **copy/evacuate** hoặc kết hợp nhiều kỹ thuật qua các phases. Những object ở ngoài collection set có thể **không di chuyển**.

## 5. G1 trên HotSpot Java 17: evacuation và cập nhật references

G1 (Garbage-First) chia heap thành những **regions** và thu gom một **collection set (CSet)** trong các GC pauses. Đối với các regions được chọn, G1 thường **evacuate** các object còn sống sang regions đích.

G1 diễn giải “root” cho collection set rộng hơn chỉ GC roots truyền thống: một reference từ **bên ngoài CSet** vào **bên trong CSet** cũng quan trọng, vì object chứa reference có thể không nằm trong regions đang collection.

Ở mức khái niệm:

```text
Region P (ngoài CSet)           Region R (trong CSet)
+---------------------+         +------------------------+
| object Parent       |         | object Child @old      |
| childRef -----------+-------->| data, refs             |
+---------------------+         +------------------------+
           |
           | G1 quét root / remembered-set edges
           v
Region S (destination/to-space)
+-------------------------+
| object Child @new       |
| data, refs              |
+-------------------------+

Sau GC:
Parent.childRef -------------> Child @new
```

Trong implementation G1, forwarding information cho phép theo từ object cũ tới object được copy. Khi GC worker xử lý một reference slot vào CSet, nó có thể:

- Kiểm tra object đã có forwarding destination chưa.
- Nếu chưa, phối hợp để copy/claim object tới vị trí mới khi thích hợp.
- Đưa reference slot về destination tương ứng.
- Tiếp tục xử lý reference fields nằm trong object copy để mọi edges còn sống phù hợp được cập nhật.

Cần để ý **race giữa GC workers** trong cùng pause: hai GC workers có thể cùng phát hiện reference tới một object. Cơ chế claim/forwarding bảo đảm chỉ một **logical destination** được chấp nhận, không để hai tham chiếu của Java program trỏ tới hai object copy độc lập.

### Remembered sets và card marking

G1 không thể hiệu quả nếu ở mỗi young GC lại quét **toàn bộ old regions** chỉ để tìm các field trỏ vào young regions. Vì vậy nó dùng cơ chế tracking những **cross-region references**:

- **Write barriers/card marking** ghi nhận các thay đổi reference fields có thể làm xuất hiện cross-region edges cần biết.
- **Remembered sets** và các cấu trúc liên quan giúp xác định những vùng/slots cần quét trong collection của một số regions.
- Khi object được evacuate, tracking structures cũng cần duy trì tính đúng cho những references/regions mới.

**Một write barrier không phải bản thân GC copy object**. Nó là cơ chế hỗ trợ duy trì metadata/điều kiện để GC tìm edges cần thiết. Đọc “write barrier sửa reference” mà không biết đó là SATB marking, remembered-set tracking hay relocation phase là một cách diễn giải thiếu chính xác.

### Concurrent marking khác evacuation

G1 có **concurrent marking** để đánh giá liveness và chọn regions hiệu quả. Nhưng **evacuation of collection set** trong G1 Java 17 thường diễn ra trong **stop-the-world pauses**, khác với collectors thiết kế concurrent relocation. G1 có thể ghi log concurrent phases và evacuation pauses trong một GC cycle; đừng gộp tất cả thành “G1 di chuyển object đồng thời với Java threads”.

### Evacuation failure và objects không di chuyển

Nếu thiếu to-space, G1 có thể gặp **evacuation failure** và xử lý một phần objects theo chiến lược khác. Trong một số tình huống, object có thể được **self-forwarded/giữ ở vị trí cũ**. Ngoài ra, một số regions/objects không tham gia evacuation trong một pause.

Vì vậy không đúng khi khẳng định “G1 di chuyển mọi live object sau mỗi GC”. Cam kết cần giữ là **reference đúng và Java object identity đúng**, bất kể object có thực sự đổi vị trí hay không.

## 6. Concurrent moving collectors: vì sao reference có thể chưa được ghi lại ngay?

ZGC và Shenandoah theo đuổi mục tiêu giảm pause bằng cách thực hiện nhiều công việc GC **concurrent với application threads**. Với collector di chuyển object đồng thời, application có thể giữ một reference được đọc từ trước khi object hoàn tất relocation.

Để vẫn truy cập đúng object, collector dùng sự kết hợp của:

- **Forwarding/relocation metadata**: xác định object mới tương ứng với object cũ.
- **Load/read barriers**: xử lý reference khi application đọc/sử dụng, có thể kiểm tra trạng thái và chuyển hướng đến destination thích hợp.
- **Store/write barriers**: giữ invariant của các thuật toán concurrent marking/relocation khi application thay đổi reference graph.
- **Update/remap phases**: bảo đảm những reference cần thiết cuối cùng được đưa về trạng thái đúng theo chính sách collector.

### ZGC trong JDK 17

ZGC sử dụng các ý tưởng **colored pointers**, **load barriers** và **concurrent relocation/remapping** để quản lý references trong khi ứng dụng tiếp tục chạy phần lớn thời gian. Đây là mô tả **đặc thù implementation/version**, không phải định nghĩa chung của mọi JVM reference.

Không suy ra rằng một giá trị Java reference được lưu trong source có thể được convert thành một “colored pointer integer” rồi tự phân tích màu sắc. Những bit và encoding là cấu trúc mà runtime quản lý; thay đổi giữa các release/JVM là khả thi.

### Shenandoah trong JDK 17

Shenandoah có cơ chế **concurrent evacuation** và **barriers** để truy cập tới object đích và cập nhật references khi có object được di chuyển. Loại barrier cụ thể cùng chiến lược forwarding phụ thuộc Shenandoah mode/release; không dùng một sơ đồ riêng của ZGC để giải thích chắc chắn mọi implementation của Shenandoah.

### So sánh đúng cấp độ

| Khía cạnh | G1 trên HotSpot Java 17 | ZGC/Shenandoah trên JDK 17 phù hợp |
| --- | --- | --- |
| Phạm vi di chuyển | Collection set/regions được chọn | Các regions/objects theo chính sách collector |
| Evacuation/relocation | Phần chính trong STW collection pauses | Có những pha **concurrent với Java threads** |
| Cách giữ references hợp lệ | Scan/update slots, forwarding, remembered-set/barrier support | Forwarding + barriers/remapping theo từng collector |
| Object identity của Java | Không đổi | Không đổi |
| Địa chỉ có ổn định không? | **Không bảo đảm** | **Không bảo đảm** |
| Tất cả object luôn bị move? | Không | Không |
| Details bắt buộc bởi Java language? | Không | Không |

Khi nghiên cứu nội bộ GC, luôn chốt **JDK version/build/collector flags**. ZGC của JDK 17 khác các thế hệ ZGC sau này, và lựa chọn collector có thể thay đổi hành vi quan sát qua log, pause time, throughput và memory footprint.

## 7. Reference slot nằm ở đâu và GC cập nhật cái gì?

Một Java object có thể được tham chiếu qua nhiều loại “slot”:

| Nơi giữ reference | Ví dụ Java | Cơ chế ở mức HotSpot |
| --- | --- | --- |
| Local variable trong active method | `Node n = root;` | Root tại Java frame/compiled register nếu còn live; GC map giúp nhận diện |
| Operand stack của JVM frame | Giá trị receiver ngay trước `invokevirtual` | Runtime xử lý references trên frame/operand state |
| Static field | `static Node CACHE;` | Reference được giữ bởi class/runtime structures thích hợp |
| Instance field | `parent.child` | Reference trong heap object, có thể cần update nếu target move |
| Reference array element | `nodes[0]` | Slot nằm trong array object, có thể cần update |
| Collection graph | `list.get(0)`, `map.get(key)` | Thường là các field/array references của cấu trúc collection |
| JNI local/global reference | `jobject` handle | VM quản lý handle/reference đúng theo JNI contract |
| Compiled code roots | Oops embedded hoặc references trong compiled frames | JIT/VM duy trì metadata để GC có thể locate/update |

Với Java source, `list.get(0)` cho ra một reference; ở implementation, list có thể có backing array với reference slots. GC quan tâm tới **object graph và concrete slots/roots được VM biết**, không cần hiểu business meaning của tên `list`.

### Khi object chứa reference cũng được copy

Giả sử `Parent` và `Child` đều được evacuate. Collector cần đảm bảo trong **bản sao Parent** có child field trỏ tới **bản sao Child** thích hợp. Nếu child field vẫn chứa raw old address không hợp lệ sau khi vùng cũ được reclaim, Java behavior sẽ sai.

Mô hình:

```text
Trước:
Parent_old.child --> Child_old
root -----------> Parent_old

Sau:
root -----------> Parent_new
Parent_new.child --> Child_new

Forwarding:
Parent_old -> Parent_new
Child_old  -> Child_new
```

Về mặt Java, `root.child` vẫn là **chính Child đó**. Thứ tự copy và fix-up do collector điều phối; không nhất thiết phải copy Child ngay trước/đúng lúc Parent.

### Heap field là `final` có được GC “sửa reference” không?

Có sự khác nhau giữa **ứng dụng Java ghi lại một `final` field** và **VM cập nhật internal reference representation vì object relocation**. Quy tắc `final` của Java không ngăn GC duy trì reference hợp lệ khi target object move. Đó không phải một phép `putfield` nghiệp vụ thay đổi field từ Child A sang Child B. **Giá trị object logic được tham chiếu vẫn là Child A**.

## 8. Lý do `==`, `System.identityHashCode` và monitor vẫn hợp lệ

### Identity không phải địa chỉ

Giả sử:

```java
Node a = new Node();
Node b = a;
```

Đây là **fragment minh họa** trong một method; `Node` là class giả định.

Nếu a và b trỏ tới cùng một object, `a == b` là `true`. GC có thể đổi nơi lưu trữ của object nhưng không được làm Java quan sát nó như hai object khác nhau.

`System.identityHashCode(a)` cung cấp identity-based hash code theo contract của API. Giá trị cho **cùng object** không được biến đổi chỉ vì GC di chuyển nó; giá trị này **không phải raw address** và không được dùng làm địa chỉ/canonical identifier duy nhất toàn JVM. **Hai object khác nhau vẫn có thể trùng identity hash code**.

### Monitor và synchronized

Nếu thread giữ monitor của object qua `synchronized(lock)`, GC move của object không được làm monitor của nó “biến mất” hoặc khiến thread khác đồng bộ nhầm sang một object khác. Cách HotSpot lưu lock state/monitor/mark word là chi tiết triển khai tùy locking mode và JDK.

Không rút ra từ việc object header có thể dùng forwarding metadata rằng GC làm mất `hashCode`, lock state hoặc class pointer về mặt ngữ nghĩa. Collector và runtime phải duy trì những thuộc tính đó khi copy/relocate.

### Java object graph vẫn nhất quán

- Object reference trong array/field/list phải tiếp tục biểu diễn **cùng đích logic** nếu đích đó còn sống và ứng dụng có quyền truy cập.
- `IdentityHashMap` dùng reference identity semantics, không dùng địa chỉ thô bất biến mà chương trình được phép phụ thuộc.
- GC relocation **không** là một Java-level data race giữa threads; memory model/volatile/locks vẫn có semantics riêng.
- GC có thể là stop-the-world hoặc concurrent, nhưng tính đúng Java-visible behavior phải được bảo toàn.

## 9. Ví dụ Java 17 hoàn chỉnh: aliasing sống qua GC pressure

Tạo **`StableReferences.java`**:

```java
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

public class StableReferences {
    static final class Node {
        final int id;
        Node next;

        Node(int id) {
            this.id = id;
        }
    }

    // Giữ một allocation mới reachable để giảm khả năng bị loại bỏ
    // chỉ bởi optimization. Đây không phải bằng chứng object đã move.
    static volatile Object allocationSink;

    public static void main(String[] args) {
        Node first = new Node(7);
        Node second = new Node(9);
        first.next = second;

        Node alias = first;
        Node[] array = {first};
        List<Node> list = new ArrayList<>();
        list.add(first);

        IdentityHashMap<Node, String> names = new IdentityHashMap<>();
        names.put(first, "first");

        int identityBefore = System.identityHashCode(first);

        for (int i = 0; i < 400; i++) {
            allocationSink = new byte[64 * 1024];
        }

        System.out.println("aliasSame=" + (first == alias));
        System.out.println("arraySame=" + (first == array[0]));
        System.out.println("listSame=" + (first == list.get(0)));
        System.out.println("linkedId=" + first.next.id);
        System.out.println("mapValue=" + names.get(alias));
        System.out.println("stableIdentityHash="
                + (identityBefore == System.identityHashCode(first)));
    }
}
```

Biên dịch/chạy bằng Java 17+:

```bash
javac --release 17 StableReferences.java
java StableReferences
```

**Stdout của chương trình**, không phụ thuộc có xảy ra moving GC hay không:

```text
aliasSame=true
arraySame=true
listSame=true
linkedId=9
mapValue=first
stableIdentityHash=true
```

Điều được chương trình kiểm tra là **Java reference semantics**: aliasing, reference fields/arrays/collections và identity hash code không được hỏng. Nó **không chứng minh** một object đã di chuyển, vì JVM có thể không GC, chỉ GC vùng khác, giữ object ở chỗ cũ hoặc tối ưu một số allocations.

### Tạo áp lực heap và bật GC log có kiểm soát

Có thể chạy lại trên một HotSpot hỗ trợ G1:

```bash
java -Xms16m -Xmx32m -XX:+UseG1GC -Xlog:gc StableReferences
```

**GC log, số lần GC và thời điểm pause không cố định** theo OS/JVM/build. Nếu collector thực hiện young evacuation, log sẽ cung cấp thông tin về collection; nhưng ngay cả khi log báo GC, **không thể suy ra từ output một object cụ thể đã move**. Số byte tạo ra trong vòng lặp, khả năng optimization và chính sách region allocation đều ảnh hưởng thực tế.

Với collector G1, lệnh:

```bash
java -Xms16m -Xmx32m -XX:+UseG1GC -Xlog:gc*,gc+phases=debug StableReferences
```

có thể cho thêm phase logging khi build hỗ trợ lựa chọn log tags. Không dùng một mốc BCI, số GC hoặc địa chỉ tưởng tượng trong ví dụ làm assertion test.

### Vì sao dùng `IdentityHashMap`?

`IdentityHashMap` so key bằng `==` thay vì `equals`, hữu ích để chứng minh Java identity semantics chứ không chứng minh pointer bits. Nếu object được dời, map vẫn phải lấy đúng value cho key cùng identity. **Không suy ra internals của `IdentityHashMap` lưu raw address**.

## 10. Khi có weak/soft/phantom references: relocation khác clearing

`java.lang.ref.Reference` tạo thêm quy tắc về reachability:

- **Strong references** thông thường có thể giữ object reachable.
- **SoftReference** có chính sách clearing liên quan memory sensitivity, không có lịch định thời xác định.
- **WeakReference** không giữ referent sống như strong reference; GC có thể clear khi đối tượng đã weakly reachable theo điều kiện thích hợp.
- **PhantomReference** có cơ chế được dùng để theo dõi hậu xử lý và cleanup; `get()` luôn trả `null`; dùng `ReferenceQueue` phù hợp.

Hai hành động **khác nhau**:

1. **Move live referent:** object vẫn còn sống, collector cần bảo đảm reference tới object vẫn hợp lệ, có thể remap/forward.
2. **Clear referent của `Reference` object:** khi reachability và reference processing yêu cầu, field tham chiếu logic được chuyển thành null theo contract, để Java có thể quan sát `weak.get() == null`.

Không được đọc `WeakReference.get() == null` rồi kết luận “GC di chuyển object nên pointer bị thay thành null”. Weak-reference clearing là **quy tắc reachability**, không phải lỗi relocation.

### Ví dụ Java 17 kiểm tra strong reachability mà không dựa vào thời điểm GC

Tạo **`WeakReferenceSemantics.java`**:

```java
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

public class WeakReferenceSemantics {
    public static void main(String[] args) {
        Object strong = new Object();
        WeakReference<Object> weak = new WeakReference<>(strong);

        System.out.println("getBefore=" + (weak.get() != null));

        // Giữ strong reachable tới đúng điểm cuối được chỉ định.
        Reference.reachabilityFence(strong);
    }
}
```

```bash
javac --release 17 WeakReferenceSemantics.java
java WeakReferenceSemantics
```

**Stdout xác định:**

```text
getBefore=true
```

`Reference.reachabilityFence(strong)` cho chương trình chỉ định một thời điểm mà `strong` phải được xem là strongly reachable. Ví dụ cố ý **không** gọi `System.gc()` hoặc in `weak.get()` sau khi đánh mất strong reference, vì **khi nào GC chạy/clear weak reference là không xác định**.

### Vùng reference được collector xử lý đặc biệt

Một collector có thể có **reference processing phases** cho `java.lang.ref` references. Ở G1, weak/soft/phantom reference handling là công việc phân biệt với copy/update ordinary strong references; GC log cũng có thể thể hiện riêng những phases. Không dùng thuật ngữ “reference processing” chung chung để khẳng định mọi update của oop khi evacuation đều có cùng quy tắc clearing.

## 11. JNI handles, native pointer và việc Java object có thể di chuyển

JNI là một nơi dễ tạo lỗi nghiêm trọng nếu lập trình viên C/C++ giữ địa chỉ theo giả định sai.

### `jobject` không phải raw stable object address

Theo cách dùng JNI chuẩn, object được truyền qua **JNI references/handles**. VM quản lý handles để collector có thể xử lý relocations thích hợp. Caller native dùng `jobject` theo **JNI lifetime rules**, không tự suy thành địa chỉ object nguyên thủy ổn định.

- **JNI local reference** thường hợp lệ trong phạm vi native call/frame tương ứng, có thể được VM giải phóng khi trả về theo contract.
- **JNI global reference** tồn tại tới khi native code giải phóng bằng API thích hợp; giữ global reference quá lâu cũng có thể giữ object sống và gây retention.
- **Weak global reference** có semantics khác strong global reference; có thể trở nên không còn referent nếu GC clear.
- Nếu lưu trực tiếp memory address của một Java object sang `uintptr_t` hay `long`, JVM **không hứa** cập nhật số nguyên ấy khi GC move object. Đó không phải GC-tracked reference slot hợp lệ.

### `GetPrimitiveArrayCritical` và việc pin/copy

JNI API cung cấp cách lấy pointer tới nội dung primitive array trong một khoảng sử dụng theo contract cụ thể. JVM có thể sử dụng **pinning**, **copy** hoặc chiến lược thích hợp tùy runtime/API. Native code phải tuân thủ ràng buộc critical region và gọi hàm **Release** tương ứng đúng lúc.

Không suy rằng vì có API `GetPrimitiveArrayCritical` nên **mọi object Java khác** sẽ bị pin, hoặc địa chỉ trả về vẫn sử dụng hợp lệ sau khi release. API JNI không phải cách tổng quát để lấy “địa chỉ Java object vĩnh viễn”.

### Direct buffers khác heap object

`ByteBuffer.allocateDirect` sử dụng backing memory với ngữ nghĩa direct buffer; Java object wrapper vẫn được GC quản lý và có thể di chuyển. Backing memory nằm ngoài Java heap theo cơ chế implementation; pointer native của backing region có những lifetime rules khác Java object reference.

Điều này giải thích vì sao một native buffer address ổn định trong phạm vi API cụ thể **không** chứng minh địa chỉ của mọi Java object ổn định.

## 12. Compressed oops: reference Java không nhất thiết là pointer machine-width

Trong nhiều cấu hình 64-bit HotSpot, reference đến Java heap objects có thể được lưu ở dạng **compressed ordinary object pointers (compressed oops)** nhằm giảm dung lượng memory dùng cho object fields/arrays. JVM decode/encode thành địa chỉ sử dụng ở implementation.

Điều cần hiểu:

- Java `Object` reference **không có primitive type size được JLS quy định** là 32 hay 64 bit.
- Một số HotSpot heap configurations dùng compressed oops, số khác không; encoding có thể sử dụng base/shift và các phương án tùy runtime.
- Khi object move, collector/JIT phải bảo đảm reference representation dù compressed hay không vẫn dẫn tới object logic đúng.
- `System.identityHashCode(obj)` không cho raw object address và không dùng để suy compressed-oop bits.
- Thông tin compressed oops không thể thay thế GC root maps và forwarding/relocation logic.

Có thể quan sát một số flags trên HotSpot:

```bash
java -XX:+PrintFlagsFinal -version
```

Tìm `UseCompressedOops` và các flags liên quan trong output của build, nhưng đừng suy ra **memory layout chính xác** chỉ từ một flag. Chi tiết JDK/vendor/GC config còn quyết định object alignment và cách giải mã.

## 13. Safepoints, barriers và đồng bộ giữa GC/mutator

### Stop-the-world

Trong stop-the-world GC phases, application threads được đưa tới trạng thái an toàn để collector duyệt, relocate và cập nhật cần thiết mà không để mutators đồng thời thay đổi đồ thị heap theo cách không được kiểm soát. Cách HotSpot phối hợp safepoints/handshakes là chi tiết implementation.

Một lời gọi `synchronized` **không phải** cơ chế application cần thêm để làm GC update references an toàn. GC/VM tự đảm nhận sự nhất quán nội bộ.

### Concurrent

Với concurrent collector, application thread là **mutator** vì nó có thể đọc/ghi fields, tạo object và thay đổi reference graph trong lúc GC chạy. Những barriers và phase invariants đảm bảo không nhìn thấy reference sai.

Ví dụ mô hình:

```text
Mutator đọc field -> read/load barrier -> effective target object
Mutator ghi field -> write barrier -> tracking/marking/remembered metadata
GC relocated object -> forwarding/remapping -> references remain valid
```

Tuy nhiên, đây chỉ là **sơ đồ cấp cao**. G1 write barriers phục vụ remembered sets/marking, không được lấy làm bằng chứng G1 đang thực hiện concurrent evacuation. ZGC và Shenandoah có barrier implementations khác, không nên so sánh từng instruction bằng nhau.

### Barriers của GC khác `volatile` và memory fences của Java Memory Model

- **GC barrier:** runtime instrumentation để GC duy trì invariants (marking, remembered sets, remapping...).
- **`volatile`/synchronization/JMM:** bảo đảm visibility/ordering/happens-before cho chương trình Java.
- Một compiler/JIT có thể cài đặt cả hai loại với các thao tác máy khác nhau hoặc kết hợp tối ưu, nhưng **ngữ nghĩa và mục đích không đồng nhất**.
- GC reference update **không** thay thế nhu cầu đồng bộ ứng dụng khi nhiều threads cùng sửa một mutable object.

## 14. Không phải mọi object bắt buộc move: “pinned”, “humongous”, evacuation failure

Một số trường hợp không thể suy ra relocation:

1. **Vùng không nằm trong collection set**: object có thể không được copy.
2. **GC không xảy ra**: tạo áp lực heap hoặc gọi `System.gc()` không phải bằng chứng chắc chắn đối tượng đã move.
3. **Large/humongous objects**: một số collector có chính sách đặc biệt với objects chiếm nhiều regions; relocation có thể không theo đường evacuation thông thường.
4. **Evacuation failure/to-space exhaustion**: collector có thể giữ một số objects ở nơi cũ và xử lý fallbacks.
5. **Native/JNI critical regions hoặc pinning**: có thể ảnh hưởng khả năng/mốc thời gian relocate những objects liên quan theo API/collector.
6. **Non-moving phases/collectors**: mark-sweep không nhất thiết có bước relocation như copying GC.

Mục đích của relocation thường gồm thu gom vùng nhớ hiệu quả, giảm fragmentation, hỗ trợ bump-pointer allocation và region-based management, nhưng collector còn cân bằng throughput, pause-time và CPU/memory overhead.

Một object “không di chuyển trong lần GC quan sát được” **không** tạo hợp đồng địa chỉ cố định cho lần sau.

## 15. Làm sao biết GC đã move một object cụ thể?

Từ Java portable code, **không có API chuẩn trả raw heap address ổn định để so sánh trước/sau GC**. Vì vậy:

- `System.identityHashCode(obj)` chỉ đo identity-based hash code, không đo địa chỉ.
- `obj.toString()` theo `Object` thường chứa phần hash code dạng hex, cũng **không** phải heap address.
- `Unsafe`/JOL/debugger nội bộ có thể cho thông tin implementation-specific, nhưng reference encoding, GC timing và cách đọc memory rất dễ làm kết quả sai. Không sử dụng raw addresses để làm logic ứng dụng.
- GC logs cho biết các hoạt động GC phases/regions/bytes tùy log level, thường **không** liệt kê mọi lần relocate từng object theo một identifier ổn định.
- Heap dump/diagnostic tools có thể biểu diễn object IDs hoặc locations phục vụ snapshot, nhưng IDs/address được in không phải contract identity của Java giữa nhiều snapshots.

Nếu mục tiêu chỉ là bảo đảm chương trình không hỏng sau GC, test nên kiểm tra **nội dung, aliasing và object identity semantics**. Nếu mục tiêu là nghiên cứu collector internals, dùng **build/diagnostics được khóa phiên bản**, GC log/JFR/SA/JVM debug instrumentation theo khả năng của môi trường, đồng thời nêu giới hạn quan sát.

### Gợi ý kiểm tra G1 phases

Với JVM test đang chạy đủ lâu:

```bash
java -XX:+UseG1GC -Xlog:gc*,gc+phases=debug -jar app.jar
```

`app.jar` là tên **ứng dụng mẫu** phải thực sự tồn tại, không phải file được tạo trong bài. Trong log, tìm các dòng về **Evacuate Collection Set**, root scanning, object copy/evacuation, reference processing, pause time. Tên phase/chú thích có thể đổi giữa các versions/GC log levels.

Cùng lúc, có thể kiểm tra:

```bash
jcmd <PID> GC.heap_info
jcmd <PID> VM.flags
```

`<PID>` là chỗ đứng cho process ID thực. Các lệnh `jcmd` không trả danh sách raw object references đã được fix-up, nhưng giúp xác định collector, heap settings và tình trạng runtime. Không lấy sample logs của môi trường khác làm bằng chứng cho process hiện tại.

## 16. Một số lỗi debug phổ biến

| Suy luận sai | Điều đúng |
| --- | --- |
| “Reference Java là địa chỉ object luôn bất biến” | Java bảo đảm semantics tham chiếu, không bảo đảm physical address |
| “GC move object sẽ làm `a == b` sai” | Collector bảo toàn aliasing/identity semantics |
| “Reference ở local biến mất khi object move” | VM cập nhật/chuyển hướng roots còn sống theo cơ chế GC |
| “Collector luôn quét từng heap field sau mỗi minor GC” | Region/remembered-set/root discovery giúp tránh quét toàn bộ không cần thiết |
| “Forwarding pointer là một Java field người dùng nhìn thấy” | Đây là internal GC/VM metadata, không phải public Java API |
| “G1 evacuate đồng thời giống ZGC” | G1 Java 17 chủ yếu evacuate trong STW pauses; concurrent marking là giai đoạn khác |
| “Mọi Java collector dùng cùng một load barrier” | Barrier strategy là implementation-specific |
| “Write barrier nghĩa là Java `volatile`” | GC barrier và JMM synchronization khác mục đích |
| “`final Node child` ngăn GC sửa pointer bits” | `final` bảo vệ ngữ nghĩa chương trình; GC vẫn duy trì internal representation |
| “`System.identityHashCode` là memory address” | Là identity-based hash, không phải address; có thể collision |
| “`WeakReference.get() == null` chứng minh GC đã move object” | Đó là reachability/reference clearing, không phải relocation |
| “Đã có `jobject` thì native code giữ raw pointer mãi mãi được” | JNI handles có lifetime rules; raw object address không ổn định |
| “`System.gc()` chắc chắn chạy moving GC” | Chỉ là yêu cầu/hint; không bảo đảm di chuyển object cụ thể |
| “JVM GC tự giải quyết mọi data race” | GC giữ internal memory safe; app vẫn cần proper synchronization |
| “Array/list giữ object theo đúng physical address của lúc thêm” | Chúng giữ references có semantics được JVM duy trì |
| “GC log có pause nghĩa tất cả live objects đã move” | Collection scope có thể chỉ là một số regions; nhiều objects không move |

## 17. Quy trình phân tích vấn đề liên quan tới object relocation

**Bước 1 — Chốt implementation.** Ghi JVM vendor/build/version, collector đang dùng, heap flags và nền tảng. Tránh trộn G1 code từ JDK mới với ZGC của JDK 17.

**Bước 2 — Xác định dấu hiệu.** Đây là lỗi `ClassCastException`, `NullPointerException`, lỗi JNI native crash, heap corruption, aliasing giả định sai hay object được GC clear vì weak reference? Các lỗi có nguyên nhân khác nhau.

**Bước 3 — Kiểm tra Java-visible contract.** Xác nhận cùng object bằng `==`, nội dung fields, đồng bộ Java đúng và lifetime của reference. Đừng dùng identity hash làm raw pointer.

**Bước 4 — Xác định GC roots.** Có reference còn sống từ stack, static fields, heap objects, JNI handles hay không? Một local đã hết liveness không tự giữ object suốt lexical scope.

**Bước 5 — Hiểu collector phase.** G1 evacuation? Concurrent marking? ZGC/Shenandoah remapping? Một phase không có nghĩa mọi object bị di chuyển.

**Bước 6 — Đối chiếu reference category.** Strong, weak/soft/phantom, JNI local/global/weak global, direct/native memory, pinned primitive array đều có semantics khác nhau.

**Bước 7 — Với JNI/Unsafe/agent, kiểm tra lifetime và tính hợp lệ.** Không lưu raw object address ngoài hợp đồng VM; không tự chỉnh class/object headers, compressed oop bits hoặc forwarding metadata.

**Bước 8 — Sử dụng diagnostics có kiểm soát.** `-Xlog:gc*`, JFR/JVM diagnostics khi phù hợp, heap dump/JOL/SA nếu đang nghiên cứu implementation; tránh ép GC trong production chỉ để tạo một “địa chỉ trước/sau”.

**Bước 9 — Viết regression tests theo kết quả Java.** Kiểm tra object graph, aliasing, identity maps, collection contents, concurrency invariants thay vì kiểm tra offset/address ngẫu nhiên.

**Bước 10 — Kết luận đúng độ chắc chắn.** “References vẫn đúng sau GC” có thể xác nhận bằng Java semantics; “object A thật sự đổi từ 0x... sang 0x...” cần bằng chứng implementation-level phù hợp.

## 18. Câu hỏi tự kiểm tra

**Câu 1:** `Node a = new Node(); Node b = a;`, GC copy object sang vùng khác. Biểu thức `a == b` có được phép đổi thành `false`?

**Không.** Hai references vẫn trỏ tới cùng một logical object.

**Câu 2:** Một field `parent.child` trỏ tới Child vừa được evacuate. Collector cần bảo đảm điều gì?

**Đọc field vẫn lấy đúng Child**, bằng update reference slot hoặc cơ chế chuyển hướng/remapping thích hợp; không để application thấy stale raw address không hợp lệ.

**Câu 3:** Forwarding information để làm gì khi nhiều roots cùng trỏ đến một object?

Để nhận ra object **đã được copy** và cho các paths cùng tìm tới destination hợp lệ, thay vì sinh nhiều logical copies độc lập.

**Câu 4:** G1 young GC cần quét toàn bộ old heap mới phát hiện old-to-young edges?

Không nhất thiết. G1 dùng remembered sets, card marking và các cơ chế tracking roots phù hợp.

**Câu 5:** ZGC có thể sửa reference vào lúc application đọc nó không?

Có thể. ZGC dùng load barriers và relocation/remapping mechanisms; không được suy rằng tất cả slots luôn được eagerly rewritten cùng một lần STW.

**Câu 6:** `System.identityHashCode(obj)` đổi sau relocation?

Không vì riêng relocation. Nó là identity-based hash, không phải raw address.

**Câu 7:** `WeakReference.get() == null` cho biết object đã move hay đã bị clearing?

Kết quả null có thể do **weak reference clearing** theo reachability rules; không phải chỉ báo relocation.

**Câu 8:** Một biến Java source còn trong scope có chắc chắn giữ object làm GC root không?

Không. Liveness có thể kết thúc trước scope end. Dùng `Reference.reachabilityFence` khi thật sự cần duy trì strong reachability tới một điểm nhất định.

**Câu 9:** Đã nhận `jobject` qua JNI, có thể cast sang địa chỉ native và cache suốt quá trình không?

Không. Dùng JNI references/handles theo lifetime contract; JVM không hứa update một raw integer address do native code tự lưu.

**Câu 10:** Một GC log có dòng “Evacuate Collection Set” có chứng minh object cụ thể trong biến `first` đã chuyển địa chỉ không?

Không. Log phản ánh GC phase, không định danh chắc chắn object cụ thể và không cho raw address trước/sau.

## Nguồn gốc và tài liệu tra cứu

- [JVMS Java SE 17 `2.5.3 — Heap](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.5.3): runtime heap và quản lý objects.
- [JVMS Java SE 17 `2.6 — Frames](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.6): JVM frame, local variables, operand stack và references.
- [Oracle Java SE 17 — G1 Garbage Collector](https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-g1-garbage-collector1.html): root processing, evacuation, remembered sets và các tình huống failure.
- [OpenJDK source — G1ParScanThreadState (reference evacuation/update)](https://github.com/openjdk/jdk/blob/master/src/hotspot/share/gc/g1/g1ParScanThreadState.cpp): ví dụ rõ về forwarding và cập nhật reference slot, **lưu ý link nhánh main không nhất thiết trùng JDK 17**.
- [JEP 333 — ZGC](https://openjdk.org/jeps/333): mục tiêu và ý tưởng concurrent GC, colored pointers/load barriers.
- [JEP 189 — Shenandoah](https://openjdk.org/jeps/189): concurrent evacuation của collector.
- [Java SE 17 `Object`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Object.html) và [`System.identityHashCode`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#identityHashCode(java.lang.Object)): object identity và identity-based hash.
- [Java SE 17 `IdentityHashMap`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/IdentityHashMap.html): key comparison theo `==`.
- [Java SE 17 `Reference`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Reference.html) và [`WeakReference`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/WeakReference.html): reachability, clearing và `reachabilityFence`.
- [JNI Specification Java SE 17 — JNI Functions](https://docs.oracle.com/en/java/javase/17/docs/specs/jni/functions.html): local/global references, weak globals, critical array access.
- [Java SE 17 `ByteBuffer`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/nio/ByteBuffer.html): direct buffers và heap buffers.
- [Java SE 17 `java` command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html), [`jcmd`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html): collector flags, unified GC logging và VM diagnostics.

**Ghi nhớ:** khi HotSpot di chuyển object, **Java reference semantics và object identity phải không đổi**. G1 thường cập nhật roots/field slots trong evacuation pauses với forwarding; collector đồng thời có thể duy trì correctness qua **barriers và remapping** ở các thời điểm khác nhau. Java code nên tin vào **object identity, reachability và chuẩn đồng bộ**, không dựa vào địa chỉ thô, số identity hash, hay giả định mọi lần GC đều di chuyển mọi object.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hotspot-objects-007">Compact Strings, string interning và string deduplication có mục đích khác nhau</a>

<details>
<summary>Xem chi tiết</summary>

# Compact Strings, string interning và string deduplication: ba cơ chế, ba mục đích khác nhau

`String` là một kiểu bất biến (immutable) và xuất hiện với mật độ rất cao trong chương trình Java: khóa JSON, HTTP headers, log messages, tên trường, mã trạng thái, giá trị cấu hình và các dòng văn bản từ database. Khi quan sát heap của **HotSpot**, ta thường gặp ba cơ chế tối ưu liên quan String:

1. **Compact Strings** giảm bộ nhớ dùng để **biểu diễn ký tự bên trong một đối tượng String** khi nội dung có thể mã hóa bằng Latin-1.
2. **String interning** cung cấp một **đối tượng String chuẩn (canonical object)** cho các chuỗi có cùng nội dung, thông qua literal/constant pool và API `String.intern()`.
3. **String deduplication** là tối ưu hóa của bộ thu gom rác: các **đối tượng String vẫn khác nhau**, nhưng có thể **dùng chung mảng dữ liệu ký tự phía sau** khi các mảng giống nhau.

Ba cơ chế này **không thay thế nhau**. Compact Strings không làm hai String object trở thành cùng một object; interning thay đổi reference người gọi nhận về; còn deduplication hoạt động bên trong runtime và **không làm biểu thức `a == b` từ `false` thành `true`** cho hai object riêng biệt.

Nội dung dưới đây phân biệt **ngữ nghĩa công khai của Java** với **chi tiết triển khai HotSpot**, giải thích JVM memory layout một cách có giới hạn, minh họa bằng **Java 17+ chạy được** và hướng dẫn kiểm tra/tối ưu có số liệu thay vì dựa vào suy đoán.

## 1. Trước hết: String là immutable, còn reference có identity

Trong Java, mỗi biến kiểu `String` chứa **reference** tới một đối tượng String, hoặc `null`. Chuỗi ký tự mà String biểu diễn là **bất biến** theo hợp đồng API: một lời gọi như `concat`, `replace` hoặc `toUpperCase` không chỉnh sửa nội dung ký tự của String đã tồn tại.

```java
String first = "cache";
String second = first;
String third = new String("cache");
```

Ở đây:

- `first == second` là `true` vì cả hai giữ **cùng reference**.
- `first.equals(third)` là `true` vì hai String biểu diễn **cùng nội dung**.
- `first == third` là `false` vì `new String(...)` đã tạo String object riêng.
- Chuỗi nội dung bất biến cho phép runtime chia sẻ backing storage một cách an toàn **khi không làm thay đổi các ngữ nghĩa Java có thể quan sát**.

**`==` với hai String references kiểm tra object identity**, không kiểm tra văn bản. Trong code nghiệp vụ, dùng `equals` khi mục tiêu là so sánh nội dung, kể cả khi ứng dụng có bật interning hoặc string deduplication.

### 1.1. Hai “đơn vị chia sẻ” khác nhau

```text
Chuỗi A và B có nội dung "cache"

A) String objects riêng, backing arrays riêng:
   a ──► String@A ──► byte[] #1 = "cache"
   b ──► String@B ──► byte[] #2 = "cache"
        a != b       các arrays chứa bytes giống nhau

B) Interning (nếu caller dùng reference do intern trả về):
   a.intern() ──┐
                ├──► canonical String@C ──► backing array #C
   b.intern() ──┘
        cùng một String object khi so references kết quả

C) Deduplication:
   a ──► String@A ──┐
                    ├──► cùng backing byte[] #D
   b ──► String@B ──┘
        a != b, nhưng storage dữ liệu có thể được chia sẻ
```

Sơ đồ này là **mô hình khái niệm của HotSpot hiện đại**, không phải định dạng bộ nhớ mà Java Language Specification bắt buộc mọi JVM dùng.

## 2. Compact Strings: giảm kích thước dữ liệu của MỖI chuỗi

### 2.1. Từ `char[]` sang `byte[]` có thông tin mã hóa

Trước JDK 9, nhiều triển khai `java.lang.String` của OpenJDK dùng backing `char[]`, với mỗi phần tử 16 bit. **JEP 254 — Compact Strings** (JDK 9) chuyển HotSpot/OpenJDK String sang dạng nội bộ hiệu quả hơn, về mặt khái niệm:

```text
String object trong HotSpot JDK 17 (minh họa)
┌───────────────────────────────────┐
│ object header và những field khác │
│ reference "value" ───────────┐    │
│ coder = LATIN1 hoặc UTF16    │    │
└───────────────────────────────┼────┘
                                ▼
                          byte[] value
                          mã hóa LATIN1 / UTF16
```

- Nếu toàn bộ **UTF-16 code units** trong String biểu diễn được trong khoảng `U+0000..U+00FF`, HotSpot có thể dùng **LATIN1 coder**, khoảng **1 byte cho mỗi code unit** ở backing data.
- Nếu không biểu diễn được bằng LATIN1, HotSpot dùng **UTF16 coder**, khoảng **2 byte cho mỗi UTF-16 code unit** ở backing data.
- Field nội bộ `byte[] value` và `byte coder` là **chi tiết của HotSpot/OpenJDK**, không phải public Java API hay một layout bắt buộc cho mọi JVM.
- Bất kể coder, Java API của `String` vẫn tuân theo **UTF-16 semantics**: `length()` và `charAt` dựa trên code units; không chuyển thành đơn vị byte chỉ vì nội bộ là LATIN1.

“Compact” ở đây **không** có nghĩa String chuyển sang UTF-8 chung cho mọi nội dung. LATIN1 ở HotSpot là chế độ 1 byte cho các UTF-16 code units có giá trị tối đa 255; nếu cần ký tự khác thì dùng internal UTF16 representation.

### 2.2. Ví dụ Latin-1, ký tự châu Á và emoji

| Nội dung | Java `length()` | Code points | HotSpot Compact Strings thường chọn | Dữ liệu ký tự minh họa |
| --- | ---: | ---: | --- | --- |
| `"Hello"` | 5 | 5 | LATIN1 | Khoảng 5 bytes |
| `"café"` (é = U+00E9) | 4 | 4 | LATIN1 | Khoảng 4 bytes |
| `"雪"` (U+96EA) | 1 | 1 | UTF16 | Khoảng 2 bytes |
| `"😀"` (U+1F600) | 2 | 1 | UTF16 | Khoảng 4 bytes |

Các con số cột cuối chỉ ước lượng **payload của backing array**; **không bao gồm** header đối tượng String, header của byte array, alignment, reference fields, compressed oops hoặc những object khác. Vì vậy không được nói chuỗi `"Hello"` chiếm tổng cộng 5 bytes heap.

Emoji có một Unicode code point ngoài Basic Multilingual Plane nhưng chiếm **hai UTF-16 code units** (surrogate pair); đây là ví dụ rõ nhất vì sao không thể đồng nhất `length()` với số ký tự hiển thị.

### 2.3. Chương trình Java 17 quan sát được gì?

Tạo tệp **`CompactTextDemo.java`**:

```java
public class CompactTextDemo {
    public static void main(String[] args) {
        String latin = "café";
        String cjk = "雪";
        String emoji = "😀";

        System.out.println("latinLength=" + latin.length());
        System.out.println("cjkLength=" + cjk.length());
        System.out.println("emojiLength=" + emoji.length());
        System.out.println("emojiCodePoints=" +
                emoji.codePointCount(0, emoji.length()));
        System.out.println("latinCharAt3=" + latin.charAt(3));
        System.out.println("cjkCharAt0=" + cjk.charAt(0));
    }
}
```

Chạy với JDK 17+ trong môi trường terminal hỗ trợ Unicode:

```bash
javac --release 17 -encoding UTF-8 CompactTextDemo.java
java -Dfile.encoding=UTF-8 CompactTextDemo
```

**Stdout theo nội dung Java (giả định terminal hiển thị Unicode chính xác):**

```text
latinLength=4
cjkLength=1
emojiLength=2
emojiCodePoints=1
latinCharAt3=é
cjkCharAt0=雪
```

Điều ví dụ này **chứng minh bằng API** là cách `String.length()` / `charAt` / `codePointCount` hoạt động, không phải “đo byte[] value trực tiếp”. Java không công bố public method để hỏi String đang được HotSpot gắn coder LATIN1 hay UTF16. Muốn khảo sát layout nội bộ cần công cụ chuyên dụng/JVM knowledge và tuân thủ ràng buộc modules; không dựa vào reflection `--add-opens` như một API ổn định.

### 2.4. Vì sao `getBytes(StandardCharsets.UTF_8)` không trả lời coder nội bộ?

`getBytes` **mã hóa văn bản ra một byte array mới theo charset yêu cầu của caller**. Nó không có nghĩa trả trực tiếp `value` nội bộ của String. Ví dụ một String nội bộ LATIN1 vẫn có thể sinh UTF-8 nhiều byte hơn cho chữ `é`.

```java
import java.nio.charset.StandardCharsets;

String text = "café";
int utf8Bytes = text.getBytes(StandardCharsets.UTF_8).length;
int utf16Bytes = text.getBytes(StandardCharsets.UTF_16BE).length;
// UTF-8: 5 bytes; UTF-16BE: 8 bytes cho ví dụ này.
```

Đây là **đoạn mã minh họa**, không phải file chạy riêng. `"café"` dùng 4 UTF-16 code units, nhưng trong UTF-8 chữ `é` chiếm hai bytes. Không suy byte[] backing của HotSpot từ một `getBytes` result.

### 2.5. Chi phí và giới hạn của Compact Strings

Ưu điểm thường kỳ vọng:

- Với các ứng dụng chứa nhiều Latin-1 strings, giảm payload từ xấp xỉ 2 bytes/code unit về 1 byte/code unit.
- Ít bytes hơn để copy/quét và ít heap pressure trong các tình huống phù hợp.
- Những cơ chế String functions/intrinsics của HotSpot hỗ trợ cả LATIN1 và UTF16 để giữ nguyên Java semantics.

Giới hạn:

- String có **ít nhất một code unit > U+00FF** cần UTF16 coder trong cơ chế này; không phải nửa chuỗi nén Latin-1 và nửa còn lại UTF16 trên cùng backing value.
- String objects và byte-array headers vẫn có overhead.
- Một số hoạt động cần xét coder/chuyển đổi và có chi phí CPU; lợi ích hiệu năng phải đo theo workload.
- Compact Strings **không xử lý trùng lặp giữa các String objects**; nhiều String `"same"` xây dựng độc lập vẫn có thể giữ nhiều backing arrays trước khi có cơ chế dedup riêng.

### 2.6. Cờ HotSpot `-XX:-CompactStrings`

Trên một số bản HotSpot/OpenJDK hỗ trợ (bao gồm JDK 17), Compact Strings được bật mặc định và có cờ chẩn đoán để **vô hiệu hóa**:

```bash
java -XX:-CompactStrings -cp . CompactTextDemo
```

Hành vi Java API và stdout ví dụ **phải giữ nguyên**; điều thay đổi là lựa chọn encoding/data layout nội bộ của HotSpot. Không sử dụng cờ này để “sửa” ký tự Unicode hoặc charset của file: lỗi console encoding và codec I/O phải được giải quyết ở nơi tương ứng.

## 3. String interning: chia sẻ CANONICAL String OBJECT

### 3.1. Contract của `intern()`

`String.intern()` là **public Java API** trả về canonical representation của một chuỗi. Theo tài liệu `java.lang.String`:

- Nếu intern pool đã có String cùng nội dung theo `equals`, `intern()` trả reference tới String canonical đang ở pool.
- Nếu chưa có, String được thêm vào pool và một canonical reference được trả.
- Với hai Strings `s`, `t`: **`s.intern() == t.intern()` khi và chỉ khi `s.equals(t)`**.
- **String literals** và kết quả string-valued constant expressions được intern theo quy tắc ngôn ngữ.

Đây là **hợp đồng về identity của kết quả intern**, không phải cơ chế tự động thay biến local đã trỏ tới object khác.

Ví dụ:

```java
String a = new String("same");
String b = new String("same");

boolean content = a.equals(b);         // true
boolean identity = (a == b);           // false
boolean canonical = (a.intern() == b.intern()); // true
```

Dù đã gọi `intern()`, các biến `a` và `b` vẫn trỏ tới **các object ban đầu**; `intern()` không “sửa reference trong biến” hay biến tất cả existing String objects thành một.

### 3.2. Literal, constant expression và runtime concatenation

```java
String literalA = "hello";
String literalB = "he" + "llo";
String runtime = new StringBuilder().append("he").append("llo").toString();
```

- `"he" + "llo"` là **compile-time constant expression** nên biểu diễn giá trị canonical như literal `"hello"`; `literalA == literalB` là `true`.
- `runtime` có nội dung bằng `"hello"` nhưng được dựng tại runtime, nên không bắt buộc trỏ tới object literal.
- `runtime.intern()` trả canonical String có nội dung `"hello"`, mà literal đã cung cấp, nên kết quả `== literalA` là `true`.

**Đừng suy rằng mọi phép `+` tạo String mới**: constant folding có thể xảy ra tại compile time; optimizer cũng có thể đơn giản hóa runtime expressions khi không làm thay đổi hành vi quan sát. Khi dạy object identity, nên dùng ví dụ explicit `new String`, nơi constructor call buộc tạo object mới theo semantics của `new`.

### 3.3. Chương trình runnable với stdout xác định

Tạo tệp **`InterningDemo.java`**:

```java
public class InterningDemo {
    public static void main(String[] args) {
        String literal = "shared";
        String folded = "sha" + "red";
        String a = new String("shared");
        String b = new String(new char[] {'s','h','a','r','e','d'});

        System.out.println("literal==folded=" + (literal == folded));
        System.out.println("a.equals(b)=" + a.equals(b));
        System.out.println("a==b=" + (a == b));
        System.out.println("a==literal=" + (a == literal));

        String ca = a.intern();
        String cb = b.intern();

        System.out.println("ca==cb=" + (ca == cb));
        System.out.println("ca==literal=" + (ca == literal));
        System.out.println("a==b after intern=" + (a == b));
    }
}
```

Lệnh:

```bash
javac --release 17 InterningDemo.java
java InterningDemo
```

**Stdout chính xác:**

```text
literal==folded=true
a.equals(b)=true
a==b=false
a==literal=false
ca==cb=true
ca==literal=true
a==b after intern=false
```

Câu cuối cực kỳ quan trọng: sau lời gọi `intern`, **chỉ `ca` và `cb` mới giữ kết quả canonical**. Interning không chuyển `a` và `b` sang cùng object khi ta vẫn giữ hai references cũ.

### 3.4. Intern pool có nằm trong Java heap không?

Có sự khác biệt giữa **ngữ nghĩa API** và chi tiết memory layout theo JDK release. Với HotSpot hiện đại, canonical `String` objects là Java objects được quản lý trên heap; HotSpot có cơ cấu StringTable/handles phục vụ lookup. **Không** áp dụng máy móc sơ đồ “interned strings luôn nằm trong Permanent Generation và tồn tại vĩnh viễn” của những thế hệ HotSpot cũ cho Java 17.

Không nên phụ thuộc vào địa chỉ/thiết kế bảng băm, khả năng GC thu hồi entries hoặc tối ưu lớp loader cụ thể để quyết định semantics của `intern()`. Hợp đồng public là **canonical identity theo nội dung khi dùng kết quả intern**.

### 3.5. Khi nào chủ động intern?

Có thể cân nhắc khi:

- Có **tập từ vựng nhỏ, lặp lại mạnh và tương đối ổn định**: ví dụ tên trạng thái, event type, key schema hoặc type labels.
- Đã **đo profiler/heap dump** và thấy có nhiều String objects trùng lặp, không dễ tái sử dụng bằng cấu trúc dữ liệu nghiệp vụ tốt hơn.
- Có chiến lược quản lý retention/throughput phù hợp, tránh tạo áp lực quá lớn lên StringTable.

Không nên mặc định intern tất cả input chưa kiểm soát từ HTTP request, log line, UUID, session token, random identifiers hoặc chuỗi high-cardinality. Nhiều giá trị duy nhất khiến chi phí lookup/housekeeping cao mà **không** có nhiều đối tượng để chia sẻ.

**Interning không phải cách mã hóa String**, không phải public “free memory” API, và không nên dùng như một thay thế cho `equals` hay cấu trúc cache có lifecycle/eviction rõ ràng.

## 4. String deduplication: chia sẻ BACKING ARRAYS, không gộp object

### 4.1. Ý tưởng của JEP 192 và HotSpot G1

**String Deduplication** (JEP 192, được đưa vào HotSpot G1 từ dòng JDK 8) phát hiện nhiều `String` objects mang **nội dung dữ liệu backing giống nhau** và cố gắng cho chúng dùng chung backing storage.

Trong JDK 8 thời điểm tính năng ra đời, String thường lưu trong `char[]`. Với **HotSpot JDK 17 sau Compact Strings**, nội dung nằm trong **`byte[] value`**, nên giải thích cho JDK 17 cần nói **backing byte arrays**, không phải nói mọi String hiện đại vẫn dùng `char[]`.

```text
TRƯỚC deduplication:
  reference a ──► String@A ──► byte[] #1 = [72,101,108,108,111]
  reference b ──► String@B ──► byte[] #2 = [72,101,108,108,111]
                            (hai mảng chứa bytes giống nhau)

SAU deduplication (nếu GC chọn hai đối tượng phù hợp):
  reference a ──► String@A ──┐
                             ├──► byte[] #1 = [72,101,108,108,111]
  reference b ──► String@B ──┘
  byte[] #2 có thể không còn được giữ bởi các String đó.

  a == b vẫn FALSE; a.equals(b) vẫn TRUE.
```

**Cái được chia sẻ là mảng dữ liệu**, không phải việc biến `String@B` thành `String@A`. Nếu GC tự động merge object identities, logic Java dùng `==`, object monitors hay `System.identityHashCode` có thể quan sát thay đổi; đó không phải mục tiêu của String Deduplication.

### 4.2. Có đảm bảo mọi chuỗi trùng lặp sẽ bị dedup?

**Không.** Đây là **tối ưu implementation/GC**, không phải tính năng ngữ nghĩa bắt buộc của Java.

Trong HotSpot G1, GC thường sàng lọc **candidate String objects** theo điều kiện tuổi và các giai đoạn xử lý, sau đó thực hiện tra cứu/bổ sung vào bảng dedup riêng. Việc dedup có thể chạy **đồng thời** và phụ thuộc GC cycles, object liveness, độ trùng lặp và các ngưỡng.

Hệ quả:

- Hai String trùng nhau có thể **chưa** được dedup ngay sau khi được tạo.
- Không có Java API chuẩn `String.isDeduplicated()` để ứng dụng biết trạng thái backing array.
- `System.gc()` **chỉ là lời yêu cầu/hint** đối với GC, không đảm bảo mọi candidate được dedup trước khi lệnh trả về.
- Cùng một chương trình có thể cho các số liệu dedup khác nhau ở hai lần chạy, hai heap sizes, hai JVM configs.
- Dedup giúp giảm heap live set khi nhiều backing arrays giống nhau còn tồn tại; nó cũng tiêu tốn CPU, metadata/table processing và có trade-offs.

### 4.3. Bật trên HotSpot JDK 17 với G1

Theo tài liệu lệnh `java` JDK 17, tùy chọn:

- **`-XX:+UseStringDeduplication`** bật string deduplication.
- Trên JDK 17 HotSpot, cơ chế được tài liệu hỗ trợ với **G1 GC**, nên bật `-XX:+UseG1GC` để minh bạch.
- Tùy chọn này **mặc định tắt** trên JDK 17.
- **`-Xlog:stringdedup*=debug`** yêu cầu Unified Logging ghi thông tin string deduplication, nhưng output/định dạng tùy JVM và GC activity.

```bash
java -XX:+UseG1GC -XX:+UseStringDeduplication -Xlog:stringdedup*=debug DedupWorkload
```

Đây là lệnh chuyên cho **HotSpot JDK 17**. Các JVM/GC khác hoặc release sau có thể có hỗ trợ/cờ mặc định khác; xác minh `java -version` và tài liệu đúng bản đang dùng. Bật flag **không** bảo đảm một con số tiết kiệm bộ nhớ cố định.

### 4.4. Chương trình tạo nhiều String objects trùng nội dung

Tạo tệp **`DedupWorkload.java`**:

```java
public class DedupWorkload {
    private static volatile String[] keepAlive;

    public static void main(String[] args) {
        String[] values = new String[200_000];

        for (int i = 0; i < values.length; i++) {
            String content = "status-" + (i % 8);
            values[i] = new String(content.toCharArray());
        }

        keepAlive = values;
        System.out.println("objects=" + values.length);
        System.out.println("contentEqual=" + values[0].equals(values[8]));
        System.out.println("sameObject=" + (values[0] == values[8]));

        System.gc();
        System.out.println("gcRequested");
    }
}
```

Biên dịch:

```bash
javac --release 17 DedupWorkload.java
```

Chạy bình thường:

```bash
java DedupWorkload
```

**Stdout từ mã ứng dụng (không tính logs bổ sung của JVM):**

```text
objects=200000
contentEqual=true
sameObject=false
gcRequested
```

Giải thích:

1. Có **200.000 String references** trong một mảng còn reachable qua `keepAlive`.
2. Chỉ có **8 nội dung khác nhau**: `status-0` tới `status-7`.
3. `new String(content.toCharArray())` tạo các String objects riêng biệt từ dữ liệu chars; khi Compact Strings bật, chúng thường dùng LATIN1 payload, nhưng ban đầu có cơ hội tồn tại nhiều backing arrays trùng nhau.
4. `values[0]` và `values[8]` có nội dung bằng nhau nhưng là **hai object riêng biệt**. String dedup, nếu diễn ra, không đổi `==`.
5. `System.gc()` không phải phép chờ bảo đảm dedup hoàn tất; `gcRequested` chỉ xác nhận code đã **gọi GC hint**.

### 4.5. Đọc logging đúng cách

Chạy với G1 + dedup logging:

```bash
java -XX:+UseG1GC -XX:+UseStringDeduplication -Xlog:stringdedup*=debug DedupWorkload
```

Kết quả **application stdout** vẫn có bốn dòng như trên, nhưng **log của JVM không có định dạng hoặc số lượng dòng cố định**; output có thể nhiều hơn và chạy trước/giữa các dòng in. Tùy độ tuổi object, GC cycles, thời gian thực thi, không nhất thiết đã thấy lượng bytes tiết kiệm đáng kể ngay khi chương trình kết thúc.

Nếu cần benchmark đúng:

- Giữ tập String objects còn sống đủ lâu qua nhiều GC cycles theo điều kiện workload.
- Dùng nhiều lần đo và cùng JDK/GC/heap settings khi so sánh bật/tắt.
- Đọc heap snapshots/histograms và các metrics GC phù hợp; phân biệt retained heap, total allocations và pause/CPU costs.
- Không coi số lượng String objects giảm đi là mục tiêu chính của dedup: **object count có thể không thay đổi**, còn backing byte arrays/retained memory mới là thứ giảm.
- Không ép gọi `System.gc()` liên tục trong production chỉ để kích hoạt cơ chế này.

## 5. Bảng so sánh ba cơ chế theo đúng đơn vị tiết kiệm

| Tiêu chí | Compact Strings | `intern()` | String deduplication |
| --- | --- | --- | --- |
| Lớp cơ chế | HotSpot/String internal representation | Public `String.intern()` + JVM literal canonicalization | HotSpot GC optimization |
| Mục tiêu | Giảm **bytes trên một backing value** khi content phù hợp Latin-1 | Tái dùng **cùng một String object** cho nội dung bằng nhau | Tái dùng **cùng backing array** giữa các String objects riêng |
| Khi xảy ra | Khi String được tạo/chuyển đổi theo representation phù hợp | Khi xử lý literal/constant hoặc caller gọi `intern()` | Khi GC nhận dạng và xử lý candidates |
| Cần code caller chủ động? | Không đối với default HotSpot behavior | Có để chủ động intern runtime Strings; literal tự intern | Thường bật VM flag, không sửa Java source |
| Thay đổi `a == b` của hai objects ban đầu? | Không | Không tự thay hai refs cũ; **kết quả `a.intern() == b.intern()` đúng theo nội dung** | Không |
| Giảm String object headers? | Không do bản thân Compact Strings | Có cơ hội tái sử dụng **một** canonical object khi ứng dụng giữ kết quả | Không |
| Giảm duplicated backing arrays? | Giảm kích thước từng array, **không gộp** các array trùng | Khi dùng canonical object, dùng backing array của object đó | **Có thể** chia sẻ cùng byte[] giữa nhiều objects |
| Semantics/API | Không thay đổi public String behavior | Có thể quan sát canonical identity của kết quả | **Không thay đổi kết quả Java-level identity/equality** |
| Trade-off | Coder handling/encoding, layout-specific overhead | StringTable lookup, retention, GC/throughput | Candidate scanning, dedup table CPU/memory, GC work |
| Định vị cho JDK 17 | JEP 254 / HotSpot default | Java `String` API, JLS literals | JEP 192, G1 với `UseStringDeduplication` |

**Phân biệt “số đối tượng” với “kích thước dữ liệu”:** một triệu Strings ngắn có cùng nội dung có thể tiết kiệm theo hai hướng rất khác nhau. Interning trả cùng object để nhiều references cùng trỏ tới **một String**. Deduplication không đổi tổng số String instances, mà tìm cách giảm **số backing arrays giữ nội dung giống nhau**.

### 5.1. Ví dụ bộ nhớ minh họa

Giả sử có 100.000 String objects, mỗi object có nội dung Latin-1 dài 40 UTF-16 code units, và tạm **bỏ qua mọi headers, alignment và overhead**:

- Khi dùng dạng dữ liệu 16-bit cho mỗi code unit: payload một mảng khoảng **80 bytes**, tổng payload của 100.000 mảng khoảng **8 MB**.
- Với Compact Strings LATIN1: payload một mảng khoảng **40 bytes**, tổng khoảng **4 MB** nếu mỗi String còn backing array riêng.
- Nếu String dedup có thể cho tất cả các Strings có đúng một nội dung dùng chung array: payload duplicate có thể giảm rất mạnh; nhưng **100.000 String object headers vẫn còn**.
- Nếu ứng dụng thay các references bằng kết quả `intern()` của cùng nội dung, có thể giữ **một canonical String object** thay vì các object duplicate đã hết reachable; lượng memory thực tế lại phụ thuộc vòng đời, GC và các tham chiếu khác.

Đây chỉ là **phép tính payload minh họa** để thấy đơn vị khác nhau, **không phải dự báo RSS, committed heap, retained size, hay số byte GC log sẽ báo**.

## 6. Kết hợp Compact Strings, interning và dedup có xung đột không?

Về nguyên tắc, chúng có thể **cùng tồn tại** trong HotSpot:

```text
Java application
  │
  ├── tạo Strings
  │      └── Compact Strings chọn LATIN1/UTF16 backing payload phù hợp
  │
  ├── gọi intern() khi muốn canonical String
  │      └── caller dùng reference trả về từ StringTable
  │
  └── GC với string dedup enabled
         └── cố gắng nhận dạng các String/backing arrays giống nhau
             rồi giảm duplicated backing storage nơi có thể

Các ranh giới không đổi:
  - equals() so nội dung;
  - == so object identity;
  - public String API không lộ trực tiếp coder/byte[] identity;
  - JVM và GC có quyền chọn tối ưu nội bộ.
```

Không có một công thức “bật cả ba sẽ giảm đúng 75% RAM” cho mọi workload. Lợi ích phụ thuộc tỷ lệ Latin-1, độ dài và tính trùng lặp, lifecycle, kích thước heap, GC, StringTable pressure và object graph.

Nếu ứng dụng **đã chủ động dùng canonical references cho hầu hết các giá trị lặp**, số duplicate String objects còn lại ít hơn, khiến lợi ích thêm từ dedup có thể giảm. Nếu ứng dụng cần giữ các String objects riêng (ví dụ data transfer object giữ nhiều field), dedup vẫn có thể tiết kiệm các backing arrays mà không yêu cầu thay reference.

### 6.1. Vì sao không nên dùng `==` ngay cả khi hệ thống intern?

Một đoạn data có thể đến từ:

- String literal đã intern.
- JSON parser tạo object mới.
- Database/JDBC driver tạo String mới.
- Deserialization, reflection, JNI hoặc library tạo các object riêng.
- Thao tác runtime `substring`, `replace`, `StringBuilder.toString()`.

Không có bảo đảm **mọi producer** trả canonical object theo cùng chính sách. Nếu một đường dữ liệu quên intern, phép `==` có thể sai bất ngờ dù hai chuỗi có cùng văn bản. Dùng `equals` để so nội dung; chỉ dựa vào reference identity khi **thực sự đang quản lý một invariant canonicalization được kiểm soát rõ**.

### 6.2. Dedup không phải interning tự động

Một HotSpot G1 đã bật `UseStringDeduplication` không được đổi `a == b` của hai `new String(...)` từ false thành true, vì identity khác là điều Java chương trình có thể quan sát. Nó cũng không có nghĩa `s.intern()` trở nên thừa về mặt hợp đồng canonical references.

Thứ GC có thể đổi là **backing byte[] được object reference tới**, không phải identity của String objects.

## 7. Những gì `javap` có thể và không thể chứng minh

Dùng:

```bash
javac --release 17 CompactTextDemo.java InterningDemo.java DedupWorkload.java
javap -c -p InterningDemo
javap -c -p CompactTextDemo
```

Cần phân biệt ba tầng:

1. **Source-level behavior**: `String.intern()`, `equals()` và `length()` nằm trong public API.
2. **Bytecode-level operations**: `ldc` để tải String literal từ runtime constant pool, `new` / `invokespecial` tạo object, `invokevirtual String.intern` để gọi API.
3. **HotSpot internal layout và GC runtime**: `byte[] value` / `coder`, string dedup candidates/tables và GC logs.

`javap` có thể chỉ ra **`ldc`** trong mã tạo literal, **`new java/lang/String`** trong constructor và **`invokevirtual String.intern`** nơi gọi. Tuy nhiên `javap -c` **không phải heap inspector**: nó không in Coder hiện hành của object hay chứng minh hai Strings đã share backing array sau GC.

Nếu xem internal `String.value` qua reflective access với flags như `--add-opens java.base/java.lang=ALL-UNNAMED`, đó là **truy cập chi tiết không phải public contract**, có rủi ro khác giữa JDK releases. Không biến cách hack reflection đó thành hướng dẫn coding business logic.

## 8. Kịch bản thực tế: cân nhắc cơ chế nào?

### 8.1. Ứng dụng có nhiều tên field JSON ngắn, lặp đi lặp lại

- Compact Strings tự giúp giảm payload của tên field Latin-1.
- Nếu parser hoặc ứng dụng có **bảng tên field chuẩn nhỏ**, có thể cân nhắc canonicalization/interning hoặc cache được kiểm soát theo lifecycle.
- Nếu vẫn tạo nhiều String objects độc lập, G1 string dedup có thể giảm duplicated backing arrays.
- Trước khi bật/tối ưu, kiểm tra parser/library có cơ chế symbol canonicalization riêng không; làm hai lần có thể tăng overhead mà không giúp thêm.

### 8.2. Server tạo nhiều mã lỗi hoặc trạng thái giống nhau

Ví dụ `"SUCCESS"`, `"PENDING"`, `"FAILED"` xuất hiện nhiều trong objects. Nếu là literal được tái sử dụng trực tiếp thì Java vốn đã có canonical literals. Nếu đến từ network/database dưới dạng String mới, object identity vẫn khác; cần đánh giá intern/cache/dedup theo tỷ lệ lặp và retention.

Dù thế nào, dùng enum hoặc một value object có domain semantics rõ ràng đôi khi tốt hơn giữ hàng triệu raw Strings; đó là quyết định **thiết kế API**, không phải nhiệm vụ riêng của HotSpot GC.

### 8.3. Log messages, UUID và tokens hầu như duy nhất

Khi giá trị hiếm khi trùng nhau:

- Compact Strings vẫn có thể giảm payload nếu content là Latin-1.
- Interning khó tiết kiệm, vì gần như không có canonical references được dùng lại.
- String dedup có thể tốn CPU/table overhead nhưng tìm thấy ít duplicated content.

**Không** dùng `intern()` cho mọi HTTP header value, path chứa ID hay token tùy ý mà chưa có số liệu; đây có thể là high-cardinality unbounded workload.

### 8.4. Unicode đa ngôn ngữ, ký tự vượt Latin-1

Nếu phần lớn Strings chứa chữ Hán, chữ Nhật, emoji hoặc code units ngoài `U+00FF`:

- Compact Strings LATIN1 ít phát huy ở những Strings đó; chế độ UTF16 cần thiết theo biểu diễn HotSpot.
- Interning vẫn hoạt động theo **nội dung Unicode**, không phụ thuộc coder.
- Dedup vẫn có thể chia sẻ backing arrays **khi content trùng**, dù là UTF16 encoding.

**Đừng coi “UTF16 coder” là vấn đề Unicode correctness.** Nó là lựa chọn dữ liệu nội bộ cho ký tự mà LATIN1 không đủ khả năng biểu diễn.

## 9. Theo dõi chi phí trong HotSpot JDK 17

Dùng công cụ đo trước/sau với cùng input và cùng cấu hình:

```bash
# Baseline G1 (dedup mặc định tắt trên JDK 17):
java -XX:+UseG1GC DedupWorkload

# Bật dedup và xem log của HotSpot:
java -XX:+UseG1GC -XX:+UseStringDeduplication -Xlog:stringdedup*=debug DedupWorkload

# So hành vi String API, CompactStrings bật/tắt:
java CompactTextDemo
java -XX:-CompactStrings CompactTextDemo

# Kiểm tra cờ phù hợp trong HotSpot đang dùng:
java -XX:+PrintFlagsFinal -version
```

Để giải thích một số liệu bộ nhớ, phải chỉ rõ đang đo:

- **Heap allocated bytes:** lượng data được tạo trong một khoảng thời gian.
- **Heap live set/retained size:** data còn reachable sau GC tại thời điểm quan sát.
- **Process RSS:** bao gồm heap committed, native allocations, stacks, code cache và nhiều loại memory khác.
- **GC time/CPU:** chi phí collector, bao gồm overhead của string dedup khi được bật.
- **String object count:** số objects, có thể **không giảm do dedup**.
- **Backing array count/size:** gần mục tiêu dedup hơn nhưng cần công cụ/heap dump phù hợp để nhìn rõ.

Không thể lấy một lần `Runtime.totalMemory() - freeMemory()` trước/sau và suy ngay hiệu quả dedup: nó chịu ảnh hưởng GC scheduling, class loading, TLAB, JIT, string ages và memory khác.

### 9.1. Quy trình benchmark tối thiểu

1. Chốt JDK vendor/version, GC, `-Xms/-Xmx`, workload và tập dữ liệu.
2. Chạy warm-up đủ và nhiều lần lặp; giữ data còn reachable đến lúc đo.
3. So sánh **G1 baseline** với **G1 + `UseStringDeduplication`** khi các thông số khác giữ nguyên.
4. Nếu đánh giá Compact Strings, đo riêng với `-XX:-CompactStrings`; tránh thay đổi thêm cả GC/heap size trong cùng một thí nghiệm.
5. Đọc GC logs, heap histogram/dump, allocator/CPU metrics phù hợp thay vì dựa trên một lần `System.gc()`.
6. Xác nhận ứng dụng thật có tần suất String trùng lặp đủ cao; synthetic workload chỉ là minh họa.
7. Đo p95/p99 latency và CPU nếu ứng dụng latency-sensitive, vì tiết kiệm heap có thể đổi bằng throughput/CPU.
8. Ghi lại trade-offs và có kế hoạch rollback flags khi triển khai production.

## 10. Các nhầm lẫn thường gặp

1. **“Compact Strings là pooling các chuỗi giống nhau.”** Sai: nó là biểu diễn ký tự gọn hơn **trong mỗi String**, không gộp String objects.
2. **“String dùng UTF-8 nội bộ từ Java 9.”** Sai trong HotSpot JDK 17; JEP 254 dùng LATIN1/UTF16 qua `byte[]` + coder.
3. **“LATIN1 chỉ chứa ASCII 0..127.”** Sai: có thể biểu diễn cả các UTF-16 code units tới `U+00FF`; ví dụ `é`.
4. **“`length()` đếm bytes của internal value.”** Sai: `length()` đếm UTF-16 code units.
5. **“Một emoji luôn có `length() == 1` vì là một ký tự nhìn thấy.”** Sai: ký tự ngoài BMP dùng surrogate pair, thường `length()==2`.
6. **“`getBytes(UTF_8)` sẽ trả backing `byte[] value` của HotSpot.”** Sai: đó là conversion theo requested charset.
7. **“Gọi `intern()` tự biến đối tượng gọi thành cùng reference với object trong pool.”** Sai: caller phải **dùng giá trị trả về**.
8. **“Hai chuỗi có cùng nội dung thì `a == b` luôn true.”** Sai: dùng `equals` để so nội dung.
9. **“Mọi runtime concatenation đều được auto-intern.”** Không có bảo đảm. Literal và string-valued constant expressions là các trường hợp chuẩn.
10. **“Interned strings còn nằm vĩnh viễn ở PermGen trên Java 17.”** Sai: đừng áp layout HotSpot cũ vào HotSpot hiện đại.
11. **“String deduplication gộp các String objects thành một.”** Sai: nó nhắm tới **backing arrays**; object identities giữ nguyên.
12. **“Bật `UseStringDeduplication` bảo đảm mọi trùng lặp được xử lý ngay lập tức.”** Sai: GC selection/timing là bất định.
13. **“Dedup sẽ làm `a == b` từ false thành true.”** Sai: object identity không đổi.
14. **“Dedup mặc định bật trên HotSpot JDK 17 G1.”** Sai theo tài liệu lệnh `java` JDK 17: option mặc định tắt.
15. **“Bật dedup luôn giúp tiết kiệm RAM và tăng tốc.”** Sai: phụ thuộc trùng lặp và CPU/GC overhead.
16. **“Dùng `System.gc()` rồi lập tức đọc kết quả chứng minh dedup hoàn tất.”** Sai: GC là hint, không có bảo đảm dedup xong.
17. **“`new String("abc")` tương đương lấy lại chính literal object.”** Sai: tạo String object riêng; backing data có thể được chia sẻ tùy implementation.
18. **“Nếu byte[] bên trong chia sẻ thì hai Strings phải là cùng object.”** Sai: object identity và backing storage identity khác nhau.
19. **“Đổi `-XX:-CompactStrings` sẽ khiến `equals`/Unicode bị sai.”** Sai: một tối ưu nội bộ không được phá hợp đồng Java API.
20. **“Có thể đo coder bằng `String.getBytes` rồi biết layout thật.”** Sai: encoding result không phải storage inspector.

## 11. Tự kiểm tra có đáp án

**Câu 1.** Compact Strings giúp gì cho một String dài 40 code units hoàn toàn thuộc U+0000..U+00FF?

**Đáp án:** HotSpot có thể dùng LATIN1 backing payload khoảng 40 bytes thay vì UTF16 khoảng 80 bytes; tổng heap object vẫn phải cộng headers/padding.

**Câu 2.** String `"😀"` có `length()` và `codePointCount(0,length())` bằng bao nhiêu?

**Đáp án:** `length() == 2`; code point count bằng `1`.

**Câu 3.** Vì sao String `"café"` có thể dùng LATIN1 trong HotSpot nhưng `getBytes(UTF_8)` dài 5?

**Đáp án:** LATIN1 internal representation và UTF-8 external encoding là hai cách mã hóa khác nhau; `é` là U+00E9 (một LATIN1 byte) nhưng hai UTF-8 bytes.

**Câu 4.** `a.equals(b)==true` nhưng `a==b==false`; sau khi gọi `a.intern()` và `b.intern()`, `a==b` có đổi không?

**Đáp án:** Không. Nếu giữ refs ban đầu thì vẫn false. `a.intern()==b.intern()` mới true theo canonical pool contract.

**Câu 5.** Bật string dedup có làm hai `String` objects trùng content biến thành một object không?

**Đáp án:** Không. HotSpot chỉ có thể cho hai objects riêng chia sẻ backing byte array; object identities không đổi.

**Câu 6.** Nếu ứng dụng có 100.000 Strings nhưng hầu như mọi nội dung đều khác nhau, nên bật `intern()` tràn lan hay không?

**Đáp án:** Không nên mặc định. Ít nội dung trùng nhau thì overhead StringTable/cache có thể lớn hơn lợi ích; cần profiling.

**Câu 7.** Dữ liệu toàn ký tự Hán có được nén LATIN1 bằng Compact Strings không?

**Đáp án:** Không, nếu có code units ngoài U+00FF thì HotSpot sử dụng UTF16 representation theo cơ chế này.

**Câu 8.** Vì sao kết quả stdout `sameObject=false` của `DedupWorkload` vẫn false ngay cả khi GC đã dedup thành công?

**Đáp án:** Vì GC chỉ chia sẻ backing arrays, không gộp hai String objects; `==` so identities.

**Câu 9.** Cờ HotSpot JDK 17 nào bật string dedup trên G1?

**Đáp án:** `-XX:+UseG1GC -XX:+UseStringDeduplication`; có thể thêm `-Xlog:stringdedup*=debug` để xem logs phụ thuộc hoạt động GC.

**Câu 10.** Tại sao không thể chỉ dùng `javap -c` để kết luận một String đang dùng UTF16 backing và đã được dedup?

**Đáp án:** `javap` cho thấy bytecode và constant pool, không hiển thị runtime backing data của từng object hoặc trạng thái GC dedup.

## 12. Tóm tắt ngắn bằng ba câu

**Compact Strings** trả lời: *Một String object đang dùng bao nhiêu bytes để lưu ký tự theo HotSpot internal representation?*

**String interning** trả lời: *Những references do `intern()` trả về có thể trỏ về cùng một canonical String object khi nội dung bằng nhau không?*

**String deduplication** trả lời: *Những String objects riêng biệt đang có thể chia sẻ cùng backing array để giảm heap footprint không?*

```text
                     String nội dung "shared"
                              │
                     ┌────────┴────────┐
                     │                 │
             Compact Strings       Interning
             LATIN1 / UTF16        canonical object
             giảm bytes/array      cho equal content
                     │                 │
                     └────────┬────────┘
                              │
                        GC deduplication
                      (nếu được bật/phù hợp)
                              │
                nhiều String object riêng biệt
                 có thể dùng chung backing data

equals(): nội dung            == : object identity
```

Cả ba cùng có thể mang lại lợi ích, nhưng **chọn đúng công cụ theo đúng vấn đề**: Compact Strings tối ưu **encoding/storage của mỗi value**, interning tối ưu **canonical object reuse**, dedup tối ưu **trùng lặp backing arrays giữa các objects**. Khi tối ưu ứng dụng thật, hãy ưu tiên API semantics, đo memory/GC/CPU trên workload đại diện và chỉ thay cấu hình hoặc code sau khi hiểu rõ lợi ích cùng chi phí.

### Tài liệu tham khảo

- [JEP 254 — Compact Strings](https://openjdk.org/jeps/254): biểu diễn String bằng `byte[]` và coder LATIN1/UTF16 (Java 9+).
- [JEP 192 — String Deduplication in G1](https://openjdk.org/jeps/192): mục tiêu chia sẻ backing arrays của equal Strings.
- [Java SE 17 — `String.intern()` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/String.html#intern()): canonical reference contract và literals.
- [JLS Java SE 17 — String literals (`3.10.5)](https://docs.oracle.com/javase/specs/jls/se17/html/jls-3.html#jls-3.10.5): interned literals/constant expressions.
- [JDK 17 `java` launcher manual — `UseStringDeduplication`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html): option G1 và giá trị mặc định.
- [HotSpot/OpenJDK JDK 17 `String.java` source](https://github.com/openjdk/jdk17/blob/master/src/java.base/share/classes/java/lang/String.java): chi tiết byte[]/coder implementation, không phải public ABI.
- [JDK 17 `javap` command](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): quan sát bytecode/constant pool.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hotspot-objects-008">Tuổi thọ đối tượng, khả năng truy cập và giới hạn suy luận từ phạm vi biến</a>

<details>
<summary>Xem chi tiết</summary>

# Tuổi thọ đối tượng, khả năng truy cập và giới hạn suy luận từ phạm vi biến

## 1. Vấn đề cốt lõi: biến hết phạm vi không phải lệnh hủy đối tượng

Xét đoạn Java:

```java
static void example() {
    Object item = new Object();
    System.out.println(item.getClass().getSimpleName());
} // item ra khỏi phạm vi
```

Dấu `}` cho biết **biến cục bộ `item` không còn sử dụng được bằng tên đó ở bên ngoài block**. Nó **không** phải câu lệnh “giải phóng ngay object”, không hứa garbage collector (GC) sẽ chạy đúng thời điểm này và không hứa vùng heap được trả về hệ điều hành sau khi method return.

Trong Java/JVM, điều cần phân biệt là:

1. **Scope của biến (lexical scope):** vùng mã nguồn nơi tên biến có thể được dùng.
2. **Lifetime của biến/slot:** cách compiler và JVM duy trì giá trị trong local variables, fields hoặc registers; không đồng nhất với dấu ngoặc source.
3. **Reachability của object:** còn cách nào để chương trình đang chạy tiếp tục truy cập object thông qua những reference hợp lệ hay không.
4. **Eligibility for reclamation:** object có thể được GC thu hồi theo các quy tắc quản lý reference và GC hay chưa.
5. **Thời điểm GC thực hiện thu hồi:** phụ thuộc runtime, chính sách GC, tài nguyên và hoạt động chương trình.
6. **Vòng đời resource ngoài heap:** file descriptor, socket, database connection, native handle… không tự được đóng đúng thời điểm chỉ vì Java object chứa reference tới resource không còn cần thiết.

**Kết luận:** `Object` có thể vẫn **reachable** sau khi biến local tạo nó đã ra khỏi scope nếu còn reference từ field, collection, callback hoặc thread. Và ngược lại, một object có thể được compiler/JIT coi là **không còn cần thiết** trước khi khối nguồn kết thúc nếu về sau không có hành vi quan sát được cần đến nó.

Đây là nền tảng để phân tích memory leak, class loader retention, vòng đời cache, `ThreadLocal`, `WeakReference`, `Cleaner` và lý do cần `Reference.reachabilityFence`.

## 2. JVM Specification bảo đảm điều gì?

Theo **JVMS `2.5.3 — Heap**, heap là vùng dữ liệu runtime dùng cho class instances và arrays. Quản lý/reclaim heap objects được thực hiện bằng một **automatic storage management system** thường gọi là garbage collector.

Đặc tả không bắt buộc:

- Một GC cụ thể như G1, ZGC hoặc Shenandoah.
- GC phải chạy sau khi rời khỏi scope.
- Một object phải tồn tại trong đúng một thế hệ heap.
- Mỗi lệnh `new` luôn tương ứng với một allocation vật lý phải quan sát được trong mọi tối ưu hóa.
- Thời điểm reclaimed memory sẽ được trả về OS.
- Java cung cấp toán tử `free(object)` để ứng dụng tự giải phóng heap object.

Điều mà ứng dụng có thể dựa vào là **ngữ nghĩa của chương trình** và **các guarantee trong Java/JVM APIs**, không phải lịch GC “dự kiến”.

### 2.1. Không nhầm lifetime với object identity

```java
Object a = new Object();
Object b = a;
a = null;
System.out.println(b != null); // true
```

Biến `a` không còn tham chiếu tới object, nhưng `b` vẫn giữ cùng object. Object không trở thành unreachable chỉ vì một trong nhiều biến đổi sang `null`.

Tương tự:

```java
Object x = new Object();
Object y = new Object();
x = y;
```

Sau phép gán `x = y`, object từng được `x` trỏ tới **có thể** trở thành không còn strong-reachable nếu không có reference khác. Object `y` trỏ tới vẫn có hai reference từ `x` và `y` trong ví dụ khái niệm. Việc nào được reclaim và khi nào phụ thuộc reachability và runtime, không phải “lúc phép gán xảy ra phải free ngay”.

### 2.2. Java không dùng reference counting thuần túy làm hợp đồng GC

Một vòng tham chiếu:

```java
class Node {
    Node next;
}

Node left = new Node();
Node right = new Node();
left.next = right;
right.next = left;
left = null;
right = null;
```

có thể trở thành **không reachable** từ mọi nguồn còn sống, dù hai `Node` còn trỏ lẫn nhau. GC có thể thu hồi toàn bộ vòng nếu không còn nguồn reference giữ nó và các điều kiện liên quan được thỏa.

**Số lượng reference giữa các object không đủ để quyết định object có “sống” hay không.** Quan trọng là khả năng tiếp cận từ những nguồn/root hợp lệ của runtime.

## 3. Lexical scope, local variables, frame và object không phải một thứ

Trong source code, block khai báo xác định tên biến được dùng ở đâu:

```java
void method() {
    {
        Object local = new Object();
        // Có thể viết local tại đây.
    }
    // Không thể viết local tại đây.
}
```

Sau block, viết `local` sẽ lỗi compile-time. Nhưng JVM bytecode không vận hành dựa trên một “dấu } sẽ giải phóng heap object”. Bytecode của phương thức có **local variable slots** và **operand stack** trong frame; compiler có thể sử dụng lại slot sau khi một giá trị không còn cần thiết.

Một `LocalVariableTable` (nếu được compiler xuất dưới dạng debug metadata) có thể mô tả phạm vi tên/offset để debugger hiển thị. Nó **không phải lệnh GC**; việc có hay không có bảng debug đó không quyết định object được reclaim.

### 3.1. Method return không đủ để kết luận object chết

```java
static Object create() {
    Object local = new Object();
    return local;
}

Object result = create();
```

Khi `create` return, local variable `local` của callee không còn trong frame active. Nhưng **object vẫn còn có thể được truy cập từ `result` của caller**. Nếu coi “method kết thúc tức là object chết”, ta đã nhầm lifetime của **frame/local name** với reachability của **heap object**.

### 3.2. Biến vẫn nằm trong scope nhưng object có thể không còn cần thiết

```java
static void process() {
    Object buffer = new Object();
    System.out.println(buffer.getClass());
    doIndependentWork();
    // block chưa đóng nhưng buffer không còn được dùng
}
```

Đoạn này **chỉ minh họa**, `doIndependentWork()` phải được định nghĩa trong một chương trình thực tế. Trên đường thực thi nhất định, compiler/JIT có thể phát hiện không còn thao tác nào cần `buffer` và giảm khoảng thời gian object được giữ strong-reachable.

JLS `12.6.1 nói rõ tối ưu hóa có thể rút ngắn thời gian reachability so với cách đọc ngây thơ “local còn trong phạm vi nên object sống tới hết block”. Một runtime không bắt buộc giữ object sống cho đến khi tên local không còn hợp lệ về mặt cú pháp.

### 3.3. Local variable slot còn giá trị cũ chưa chắc là strong GC root

JVM implementation/JIT có thể xác định những locals/registers đang **live** tại safepoint khác với việc một debugger có thể thấy một bit pattern cũ trong slot. Không được khẳng định “slot vẫn chứa reference cũ thì object chắc chắn được giữ sống” cho mọi tối ưu hóa của HotSpot.

Ngược lại, nếu object vẫn có reference từ field của object khác đang sống, local hết scope cũng không đủ để làm nó unreachable.

## 4. Khái niệm reachability: đi từ đâu tới object?

Để hiểu GC, hãy vẽ **đồ thị các object và reference**:

```text
Live roots / chương trình còn sống
             |
       +-----+------+
       |            |
  Static registry   Frame đang chạy
       |            |
       v            v
     List A        Service B
       |            |
       v            v
   Payload X      Request Y
       |
       v
   Payload Z
```

Nếu một object có thể được truy cập từ một nguồn đang sống thông qua chuỗi **strong references** hợp lệ, nó **strongly reachable**. Khi không còn đường strong-reference nào, nó có thể thuộc một mức reachability yếu hơn nếu được tham chiếu qua `SoftReference`, `WeakReference` hoặc `PhantomReference`, hoặc trở thành không reachable.

Đồ thị GC chính xác phụ thuộc implementation, các roots VM cần xét và các quy tắc reference-processing. Sơ đồ phục vụ phân tích, **không có nghĩa JLS liệt kê một bộ “GC roots” có cấu trúc dữ liệu cố định trên mọi JVM**.

### 4.1. Các nguồn thường có thể giữ object còn sống

Trong HotSpot, khi phân tích heap, những đường tham chiếu cần xem gồm:

- **Reference sống từ frame/thread:** tham số, local/register mà computation còn cần.
- **Static fields:** static reference của class đang tồn tại, theo class-loader identity.
- **Các object được root giữ trực tiếp/gián tiếp:** registries, singleton, caches, collections.
- **Thread và thread-associated state:** active tasks, thread locals, thread context class loaders, callback references.
- **JNI/native handles và các VM-managed references** trong những ngữ cảnh phù hợp.
- **Class loaders/class metadata và liên kết liên quan:** nhất là plugin/application servers.

Danh sách là **gợi ý chẩn đoán**, không phải định nghĩa độc quyền của mọi bộ GC. Tùy runtime, object graphs và root categories hiển thị trong heap dump có thể khác nhau.

### 4.2. “Không còn đường từ một biến local” chưa đủ

```java
static final java.util.List<Object> ITEMS =
        new java.util.ArrayList<>();

static void add() {
    Object local = new Object();
    ITEMS.add(local);
}
```

Đoạn code minh họa này yêu cầu đặt trong một class. Sau `add()` return, reference local mất đi, nhưng **ITEMS vẫn chứa object**. Nếu registry tồn tại lâu dài, object có thể tồn tại lâu dài.

Muốn bỏ một cạnh trong đồ thị, cần xóa khỏi collection phù hợp:

```java
ITEMS.clear();
```

Nhưng **clear không có nghĩa object phải được GC ngay**, vì object có thể còn reference khác và GC chưa cần chạy.

### 4.3. Closures và lambdas có thể giữ captures

```java
static Runnable task() {
    Object captured = new Object();
    return () -> System.out.println(captured.toString());
}
```

Sau `task()` return, lambda vẫn có thể giữ reference tới `captured`. Nó được sống tiếp cùng object callback nếu runtime cần giữ giá trị capture để bảo toàn hành vi. Nơi callback được đăng ký (executor, listener registry, timer) có thể kéo dài lifetime của object được capture.

Vì vậy **“biến local đã ra khỏi scope”** là một tín hiệu yếu trong điều tra memory retention.

## 5. Các mức reachability của `java.lang.ref`

Java SE cung cấp ba họ **reference objects đặc biệt**: soft, weak, phantom. Chúng cho phép tương tác có kiểm soát với GC. Java SE 17 `java.lang.ref` mô tả các mức reachability theo thứ tự từ mạnh tới yếu:

| Mức | Ý nghĩa khái quát | Tác động thực tế |
| --- | --- | --- |
| **Strongly reachable** | Có thể được truy cập mà không đi qua reference objects đặc biệt | Thông thường GC không thu hồi object khi vẫn cần theo strong path |
| **Softly reachable** | Không strong-reachable, nhưng có đường thông qua `SoftReference` | Collector có thể clear soft references để đáp ứng nhu cầu bộ nhớ |
| **Weakly reachable** | Không strong/soft, nhưng có đường qua `WeakReference` | Weak refs được xử lý/clear khi collector xác định referent weakly reachable |
| **Phantom reachable** | Không còn các mức mạnh hơn và đáp ứng quy tắc phantom reference | Được dùng với reference queue/cleanup, không cung cấp lại referent qua `get()` |
| **Unreachable** | Không còn đường reachability theo các mức trên | Có thể đủ điều kiện reclaim theo chính sách/chu kỳ GC |

**Cẩn thận về finalization:** những đặc tả lịch sử có điều kiện liên quan finalization khi giải thích phantom/unreachable. Finalization đã bị deprecated for removal và Java SE 26 cho phép implementations tắt nó; ứng dụng mới không nên thiết kế cleanup dựa trên finalizer.

### 5.1. Strong reference

```java
Object strong = new Object();
```

Khi `strong` là một reference còn cần thiết để tiếp tục computation, object được giữ reachable theo nghĩa thích hợp. Việc tên `strong` nằm trong một block **không tự quy định** nó phải tồn tại như GC root đến đúng cuối block; tình trạng live reference phụ thuộc đường thực thi và tối ưu hóa hợp lệ.

### 5.2. `SoftReference` không cho kết quả lúc nào bị xóa một cách cố định

```java
java.lang.ref.SoftReference<byte[]> soft =
        new java.lang.ref.SoftReference<>(new byte[1024]);
byte[] data = soft.get();
```

Đây là đoạn minh họa, không là chương trình độc lập. `soft.get()` có thể trả array hoặc `null` nếu soft reference đã bị clear theo chính sách GC. Không có hợp đồng Java rằng “soft reference chắc chắn sống tới khi heap free < 10%” hoặc “GC phải xóa tất cả soft refs ở mỗi collection”.

Soft references dùng cho **memory-sensitive caching**, nhưng cần kiểm thử thực tế và có fallback tái tạo dữ liệu. Đừng dựa vào soft refs làm nơi lưu thông tin bắt buộc không thể tạo lại.

### 5.3. `WeakReference` không tự giữ referent sống

```java
Object obj = new Object();
java.lang.ref.WeakReference<Object> weak =
        new java.lang.ref.WeakReference<>(obj);
```

Nếu chương trình còn giữ `obj` một cách cần thiết, referent có strong path. Khi strong path không còn, `weak` không tự bảo đảm referent sống mãi.

Quan trọng:

- `weak` chính nó là **reference object** có thể còn strongly reachable.
- `weak.get()` lấy referent nếu chưa bị clear; nếu đã clear thì trả `null`.
- **Thời điểm GC chạy, clear hoặc enqueue không được xác định bằng dấu `}` của source**.
- Không sử dụng `System.gc()` rồi assert “`weak.get() == null` phải đúng ngay” trong unit tests portable.

### 5.4. `PhantomReference` phục vụ cleanup notification, không đọc lại object

`PhantomReference<T>` được dùng với `ReferenceQueue<T>` và những cơ chế xử lý hậu reachability. `get()` của `PhantomReference` **luôn trả `null`**; không phải một cách lấy lại object sắp bị thu hồi.

Đừng dùng phantom reference như một “weak reference mạnh hơn một chút có thể dereference”. Nó được thiết kế cho notification/cleanup coordination.

## 6. `ReferenceQueue` giúp biết điều gì?

`ReferenceQueue` cho phép chương trình nhận những **reference objects** khi chúng được enqueue theo quy tắc xử lý reference của GC:

```java
java.lang.ref.ReferenceQueue<Object> queue =
        new java.lang.ref.ReferenceQueue<>();
Object target = new Object();
java.lang.ref.WeakReference<Object> weak =
        new java.lang.ref.WeakReference<>(target, queue);
```

Đây là đoạn minh họa để đặt trong method/class thích hợp. Khi GC xác định referent ở trạng thái phù hợp, weak reference có thể được clear và enqueue. Sau đó `queue.poll()` có thể trả một reference đang chờ, hoặc `null` nếu chưa có.

**Không suy luận `queue.poll() == null` nghĩa là target chắc chắn còn strongly reachable**: có thể GC chưa thực hiện reference processing, sự kiện enqueue chưa có, hoặc tình huống runtime khác. Thời điểm quan sát được không là đồng hồ đo lifetime chuẩn.

### 6.1. Vì sao không nên giữ strong referent trong cleanup registry?

Nếu bạn vừa tạo weak reference tới object vừa giữ **một strong reference khác tới đúng object đó** trong registry “để cleanup”, object vẫn có thể tồn tại mãi. Weak reference không vô hiệu hóa các strong paths khác.

Ví dụ nguy hiểm về ý tưởng:

```text
Registry
  +-- WeakReference(target)
  +-- StrongReference(target)    <-- vẫn giữ object sống
```

Khi debug weak-cache không giải phóng, hãy tìm những cạnh mạnh **ngoài chính WeakReference**.

## 7. Scope của một local và khoảng sống thực sự (*liveness*)

Một biến trong source có thể còn **đang in scope** nhưng compiler biết giá trị cuối cùng của nó đã được dùng:

```java
static void work() {
    byte[] temporary = new byte[1_000_000];
    use(temporary);
    expensiveCalculation();
}
```

Đây là minh họa, `use` và `expensiveCalculation` chưa được định nghĩa. Sau lần cuối cần `temporary`, compiler/JIT có thể giảm live range của reference. Object có thể được coi là không còn strongly reachable trước khi `work` kết thúc, miễn phép biến đổi hợp lệ về ngữ nghĩa.

Ngược lại, nếu `use` đưa `temporary` vào một shared registry, array vẫn có đường strong reference qua registry sau khi method return.

### 7.1. “Biến đã ra khỏi scope thì object eligible for GC” chỉ đúng có điều kiện

Đặt:

```java
static java.util.List<byte[]> storage = new java.util.ArrayList<>();

static void allocate() {
    byte[] buffer = new byte[1024];
    storage.add(buffer);
}
```

Tên `buffer` hết scope nhưng array **vẫn reachable từ `storage`** nếu registry còn sống và chưa bị gỡ bỏ.

Một cách giải thích chính xác phải là:

> Sau khi biến cục bộ không còn giữ một reference live, object **có thể** trở thành không còn strong-reachable, **nếu không còn strong paths nào khác**.

### 7.2. `local = null` có bắt buộc giảm memory không?

Không. Viết:

```java
Object item = new Object();
use(item);
item = null;
```

là một cách chủ động bỏ reference của local, nhưng:

- Compiler có thể đã biết reference hết live trước câu `item = null`.
- Object có thể vẫn được giữ bởi fields/caches/closures.
- Garbage collector có thể chưa chạy.
- Heap used sau đó có thể chưa giảm ngay, và heap committed có thể vẫn giữ để tái sử dụng.

Trong một số phương thức rất dài nơi local slot tồn tại lâu và compiler/interpreter giữ reference lâu hơn cần thiết, bỏ reference có thể có tác dụng ở một runtime cụ thể. Nhưng **đây không phải kỹ thuật bắt buộc hoặc bảo đảm portable**, càng không thay việc thiết kế vòng đời references đúng.

### 7.3. Debugger và tối ưu hóa có thể làm tình hình khó quan sát

Ở chế độ JIT, một biến mà source cho thấy còn trong scope có thể bị tối ưu hoặc không sẵn sàng để debugger đọc. Ngược lại, một giá trị có thể nằm trong local slot ở interpreter dù source name đã hết scope.

**Đừng suy đoán reachability chỉ từ ảnh chụp LocalVariableTable, stack trace hay vị trí dấu ngoặc nhọn.** Khi điều tra leak, cần heap dump, GC roots/path-to-root và cách objects được giữ thực sự.

## 8. `Reference.reachabilityFence`: yêu cầu rõ một điểm sử dụng cuối

`Reference.reachabilityFence(Object)` được thêm vào Java 9. Nó thiết lập một **điểm mà object phải được xem là strongly reachable ít nhất cho tới sau invocation**, theo hợp đồng API.

Ví dụ khái niệm:

```java
import java.lang.ref.Reference;

static void handle(NativeHandle handle) {
    long address = handle.address();
    nativeWork(address);
    Reference.reachabilityFence(handle);
}
```

Đây là **mã minh họa**, không là chương trình độc lập: `NativeHandle` và `nativeWork` cần định nghĩa phù hợp. Trong code kiểu này, native call có thể chỉ nhận một địa chỉ số `long` nên JVM không tự biết cần giữ Java wrapper `handle` sống qua toàn bộ thao tác chỉ từ việc nhìn giá trị `long`.

`Reference.reachabilityFence(handle)` biểu thị rằng wrapper object phải còn strongly reachable ít nhất tới sau lời gọi. Nó rất hữu ích ở đoạn code tích hợp native resources hoặc cleaning mechanisms khi object wrapper kiểm soát lifetime của native handle.

### 8.1. `reachabilityFence` không phải `free`

Những điều `reachabilityFence` **không làm**:

- Không ép garbage collector chạy.
- Không “pin” object ở một địa chỉ heap cố định.
- Không gia hạn lifetime vô hạn.
- Không bảo đảm tài nguyên native được đóng.
- Không tạo reference vĩnh viễn sau khi method return.

Nó ràng buộc compiler/runtime về **reachability cho đến một điểm chương trình**, không phải một API điều khiển GC schedule.

### 8.2. Đặt fence đúng vị trí

Nếu công việc với resource hoàn tất ở cuối block, fence nên xuất hiện **sau lần cuối công việc có thể cần resource**:

```text
sử dụng resource / gọi native
            |
            v
Reference.reachabilityFence(owner)
            |
            v
không cần giữ owner vì công việc ấy nữa
```

Đừng đặt fence ngay đầu method rồi kỳ vọng nó giữ object sống xuyên qua đoạn thao tác về sau. Fence cũng **không thay thế synchronization, ownership protocol hoặc explicit close** khi native resource được chia sẻ nhiều thread.

## 9. Vòng đời Java object và vòng đời external resource không đồng nhất

Tạo một Java object có thể đồng thời mở file/socket/native allocation:

```java
java.io.InputStream in =
        java.nio.file.Files.newInputStream(path);
```

Đây là đoạn minh họa, `path` phải được khai báo. Java reference tới stream là object ở heap, nhưng file descriptor/native handle liên quan có **vòng đời tài nguyên ngoài heap**.

Nếu biến `in` ra khỏi scope mà không đóng, không có bảo đảm hệ điều hành sẽ nhận lại file descriptor ngay. GC có thể không chạy kịp, cleaner/finalization (nếu có) không phải một cơ chế deterministic cleanup cho tài nguyên quan trọng.

### 9.1. `try-with-resources` là lựa chọn chính cho resource có thể đóng

```java
try (java.io.InputStream in =
         java.nio.file.Files.newInputStream(path)) {
    // Xử lý dữ liệu từ stream.
}
```

Compiler sắp xếp gọi `close()` khi rời try-with-resources theo quy tắc của Java, kể cả khi phần thân kết thúc bằng exception (và có cơ chế suppressed exceptions khi phù hợp).

Điều này **khác với** “object được GC”: resource được đóng **chủ động theo luồng điều khiển**, không đợi object unreachable.

### 9.2. `AutoCloseable` không có nghĩa heap object tự bị hủy

Một đối tượng `AutoCloseable` sau khi `close()` vẫn có thể còn reachable và vẫn có thể được truy cập (tùy API). `close` quản lý **resource lifecycle**, không phải “free Java object memory”.

Ngược lại, object có thể không còn reachable trước khi `close` được gọi nếu code dùng sai pattern. Cần thiết kế ownership rõ ràng.

## 10. `Cleaner` và `PhantomReference`: safety net, không thay explicit close

Java `Cleaner` quản lý cleaning actions gắn với reachability của object được đăng ký. Nó được dùng để hỗ trợ giải phóng resource khi caller quên close, nhưng **không bảo đảm thời điểm hành động cleanup sẽ diễn ra** trước khi process kết thúc hoặc trước một deadline nghiệp vụ.

### 10.1. Pattern đúng: action không giữ referent

Mẫu minh họa:

```java
import java.lang.ref.Cleaner;

final class ManagedHandle implements AutoCloseable {
    private static final Cleaner CLEANER = Cleaner.create();

    private static final class State implements Runnable {
        private boolean cleaned;

        @Override
        public synchronized void run() {
            if (cleaned) return;
            cleaned = true;
            // Giải phóng native resource do State quản lý.
        }
    }

    private final State state = new State();
    private final Cleaner.Cleanable cleanable =
            CLEANER.register(this, state);

    @Override
    public void close() {
        cleanable.clean(); // explicit cleanup, idempotent theo state
    }
}
```

Đây là **class minh họa**, không mở resource thật. Quan trọng nhất: `State` là static nested class, không vô tình capture `ManagedHandle.this`. Nếu cleaning action giữ strong reference tới referent, nó có thể làm referent **không bao giờ trở thành phantom reachable**, khiến cleanup không được kích hoạt bởi GC.

### 10.2. Điều `Cleaner` không cam kết

- Không cam kết callback chạy ngay sau dấu `}`.
- Không cam kết callback chạy trong một thời hạn cố định.
- Không cam kết callback chạy trước khi process bị kill.
- Không thay cơ chế quản lý transaction, lock hoặc filesystem sync.
- Không phù hợp để thực thi nghiệp vụ quan trọng mà thiếu cleanup sẽ gây mất tính đúng đắn.

Thiết kế nên đặt **`close()` làm đường chính**; Cleaner chỉ là lớp bảo vệ phụ khi phù hợp.

### 10.3. Không dùng `finalize` cho code mới

`Object.finalize()` đã bị deprecated for removal từ JDK 18 và JEP 421 mô tả lộ trình loại bỏ finalization. Java SE 26 cho phép implementations tắt finalization, nên không nên xây code mới dựa vào việc finalizer sẽ được chạy.

Những hạn chế của finalization gồm:

- Thời điểm và thread thực hiện không xác định.
- Cleanup có thể bị trì hoãn lâu hoặc không xảy ra trước khi JVM kết thúc.
- Code finalizer có thể gây vấn đề về concurrency/security.
- Có thể tạo hiện tượng resurrection hoặc giữ object lâu hơn dự kiến.
- Đường lỗi và phụ thuộc giữa finalizers khó kiểm soát.

Thay thế ưu tiên: **`AutoCloseable` + try-with-resources**; với trường hợp cần non-deterministic fallback thì `Cleaner` hoặc `PhantomReference` theo hướng dẫn API chính thức.

## 11. Ví dụ Java 17+ độc lập: scope kết thúc nhưng object vẫn còn dùng được

Tạo tệp **`ObjectReachabilityDemo.java`**:

```java
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public final class ObjectReachabilityDemo {
    private static final List<Object> REGISTRY = new ArrayList<>();

    static final class Ticket {
        final String name;
        Ticket(String name) { this.name = name; }
    }

    static Ticket makeTicket() {
        Ticket local = new Ticket("returned");
        return local;
    }

    static void saveFromBlock() {
        {
            Ticket local = new Ticket("stored");
            REGISTRY.add(local);
        }
    }

    static Runnable makeCallback() {
        Ticket captured = new Ticket("callback");
        return () -> System.out.println("captured: " + captured.name);
    }

    static final class Resource implements AutoCloseable {
        private boolean closed;

        void use() {
            if (closed) throw new IllegalStateException("closed");
            System.out.println("resource used");
        }

        @Override
        public void close() {
            closed = true;
            System.out.println("resource closed");
        }
    }

    public static void main(String[] args) {
        Ticket returned = makeTicket();
        System.out.println("returned local survives: " + returned.name);

        saveFromBlock();
        System.out.println("stored outside block: "
                + ((Ticket) REGISTRY.get(0)).name);

        Runnable callback = makeCallback();
        callback.run();

        try (Resource resource = new Resource()) {
            resource.use();
        }

        Object strong = new Object();
        WeakReference<Object> weak = new WeakReference<>(strong);
        System.out.println("weak while strongly reachable: "
                + (weak.get() != null));
        Reference.reachabilityFence(strong);

        REGISTRY.clear();
        System.out.println("registry size after clear: " + REGISTRY.size());
        System.out.println("collection time: not guaranteed");
    }
}
```

Biên dịch và chạy trên **JDK 17 trở lên**:

```bash
javac --release 17 ObjectReachabilityDemo.java
java ObjectReachabilityDemo
```

**Stdout chính xác:**

```text
returned local survives: returned
stored outside block: stored
captured: callback
resource used
resource closed
weak while strongly reachable: true
registry size after clear: 0
collection time: not guaranteed
```

Mã này được thiết kế **không phụ thuộc thời điểm GC**. Nó không assert rằng weak reference phải trở thành null sau `System.gc()` hay object đã bị reclaim ngay sau `REGISTRY.clear()`.

### 11.1. `returned local survives: returned`

Trong `makeTicket()`, local `local` tồn tại trong một method frame và trả reference cho caller. Sau khi frame kết thúc, `returned` của caller vẫn trỏ tới cùng `Ticket`. Scope của biến `local` kết thúc **không hủy Ticket**.

### 11.2. `stored outside block: stored`

`saveFromBlock()` đặt Ticket vào `REGISTRY`, là static collection. Dù `local` không còn scope, registry vẫn có reference. Lúc `main` đọc `REGISTRY.get(0)`, object còn truy cập được.

Đây là ví dụ nhỏ của đường giữ object gây memory retention khi cache/listeners/registries lưu dữ liệu lâu hơn dự kiến.

### 11.3. `captured: callback`

`makeCallback()` trả lambda sử dụng `captured`. Callable object giữ dữ liệu cần thiết để thực hiện callback sau khi method đã return. Nếu callback còn sống thì object được capture có thể còn sống.

Khi đăng ký callbacks vào một event bus hoặc scheduler, hãy cân nhắc thời điểm **unsubscribe/cancel**, vì nếu registry giữ callback lâu dài thì các objects mà callback capture cũng có thể bị giữ lâu dài.

### 11.4. `resource used`, `resource closed`

Resource được mở theo ý tưởng, sử dụng trong try-with-resources, rồi `close()` được gọi có thể quan sát bằng dòng stdout. Đây là ví dụ về **deterministic release** của resource lifecycle; không phải sự kiện Java heap GC.

### 11.5. `weak while strongly reachable: true`

Trong vùng code có `strong` và lệnh `Reference.reachabilityFence(strong)` phía sau, object phải còn strongly reachable trong đoạn quan sát tương ứng. `weak.get()` trả referent chưa bị clear, nên dòng này là true.

Lưu ý `WeakReference` **không** tự khiến object tồn tại lâu hơn sau khi không còn strong path. Code không cố đo xem lúc nào GC sẽ clear weak reference.

### 11.6. `registry size after clear: 0`

`REGISTRY.clear()` làm danh sách không còn phần tử. Đây là điều có thể xác định bằng Java semantics. Nó **không chứng minh tất cả các Ticket objects đã bị reclaim**, vì object có thể được giữ ở nơi khác, và GC có thể chưa chạy.

## 12. Những minh họa không nên biến thành unit test có stdout cố định

Mô hình thử nhanh:

```java
Object o = new Object();
WeakReference<Object> ref = new WeakReference<>(o);
o = null;
System.gc();
System.out.println(ref.get() == null);
```

**Đây là ví dụ test không đáng tin, không phải bài thực hành chuẩn.** Dòng cuối có thể in true hoặc false tùy JIT, GC, heap pressure, timing và reachability. Không có đặc tả Java nói `System.gc()` buộc toàn bộ weak references được xử lý ngay trước dòng kế tiếp.

Một ví dụ sai khác:

```java
void test() {
    byte[] block = new byte[10_000_000];
} // Kết luận: heap used phải giảm đúng 10MB ngay đây.
```

Đây là lập luận **không hợp lệ**; collector có thể chưa reclaim, mức heap used/committed có độ nhiễu, object có overhead và runtime có thể tối ưu.

### 12.1. `System.gc()` là yêu cầu/hint không phải lịch bắt buộc

Tài liệu `System.gc()` mô tả việc **gợi ý** VM dành nỗ lực cho việc tái chế objects không còn dùng. VM có thể giới hạn/không thực hiện theo yêu cầu trong các cấu hình thích hợp; hiệu quả phụ thuộc collector/JVM.

Ngay cả khi GC chạy, điều đó không phải bằng chứng tất cả resources đã được đóng hay memory đã được trả cho OS.

### 12.2. Không dùng `Thread.sleep` để “chờ GC chắc chắn”

```java
System.gc();
Thread.sleep(1000);
// Không có guarantee object đã bị reclaim.
```

Một giây chỉ là thời gian chờ của thread. Nó không tạo guarantee về thời điểm collect, enqueue weak reference hoặc run Cleaner. Unit tests cần dựa trên **hành vi đã được đặc tả**, không phải timing hên xui.

## 13. ThreadLocal và thread pool: scope ngắn, retention dài

`ThreadLocal<T>` giữ dữ liệu gắn với **một thread** thay vì theo lexical scope của method gọi `set()`. Trong môi trường thread pool, các worker threads thường sống qua nhiều requests.

Ví dụ minh họa:

```java
private static final ThreadLocal<byte[]> BUFFER =
        new ThreadLocal<>();

static void handleRequest() {
    BUFFER.set(new byte[1024 * 1024]);
    try {
        // Xử lý request dùng BUFFER.get() khi cần.
    } finally {
        BUFFER.remove();
    }
}
```

Đoạn code **không độc lập**; cần đặt trong một class và có lời gọi thích hợp. Điểm học tập:

- Biến local trong `handleRequest()` biến mất sau return **không đảm bảo ThreadLocal value được xóa**.
- Thread pool worker tiếp tục sống có thể giữ entry/value giữa các requests.
- `BUFFER.remove()` tại `finally` là một chiến lược dọn dẹp phù hợp khi request kết thúc, nếu không có nhu cầu giữ lại state giữa requests.

Đừng suy luận “ThreadLocal key là weak reference nên mọi value được xóa ngay khi key không còn” — thread-local map entries/cleanup behavior và đường retention cụ thể cần phân tích theo runtime.

### 13.1. Các mẫu retention tương tự

- Global static collections giữ request contexts.
- Scheduled tasks chưa hủy giữ callbacks và captures.
- `CompletableFuture` chains giữ lambda closures hoặc intermediate results.
- Event listeners chưa unregister giữ owner objects.
- Thread context class loader giữ plugin loaders.
- Cache không giới hạn hoặc có policy eviction sai.
- Native references/JNI handles giữ Java objects hoặc native resources.

Trong mọi trường hợp, vấn đề không phải dấu ngoặc của biến mà là **cạnh reference nào còn tồn tại**.

## 14. Class loader và tuổi thọ của class metadata

Ở HotSpot, class metadata gắn với runtime class và class loader data. Một loader có thể định nghĩa nhiều classes; nếu host tiếp tục giữ loader hoặc classes/instances của nó reachable, việc **class unloading** có thể bị ngăn.

Ví dụ plugin:

```text
Host static registry
       |
       v
Listener (plugin class)
       |
       v
Plugin ClassLoader
       |
       v
Class metadata của plugin
```

Nếu plugin code trả về từ method và tất cả các local variables đã hết scope, nhưng host vẫn giữ listener, class loader vẫn có thể tồn tại lâu dài. Vì vậy **Metaspace growth** có thể liên quan đến object reachability trong heap và class loader retention.

Để gỡ plugin sạch, cần:
 
- Unregister listeners/callbacks.
- Cancel tasks/executors và giải phóng thread context class loaders phù hợp.
- Xóa cached plugin instances/references.
- Đóng resources của plugin theo ownership contract.
- Đảm bảo host không giữ reflection `Class`/`Method` hoặc closures khi không còn cần.

Việc close một `URLClassLoader` không tự buộc VM unload class ngay. Class unloading cũng phụ thuộc GC, VM và reachability.

## 15. Phân biệt memory leak, retention mong muốn và object còn reachable

### 15.1. “Memory leak” trong Java thường có nghĩa gì?

Trong managed heap, một kiểu leak thường gặp là **các object không còn cần cho nghiệp vụ nhưng vẫn reachable qua references**. GC làm đúng: nó không thu hồi objects mà chương trình vẫn có đường sử dụng, dù về mặt thiết kế những references đáng ra nên được bỏ.

Ví dụ:

```java
static final java.util.List<byte[]> HISTORY =
        new java.util.ArrayList<>();

static void onRequest() {
    HISTORY.add(new byte[1024 * 1024]);
}
```

Đây là class fragment minh họa. Nếu gọi `onRequest()` liên tục mà `HISTORY` không giới hạn kích thước/không có eviction, heap có thể tăng cho đến `OutOfMemoryError`. Lỗi không nằm ở phạm vi local của `onRequest`; nó nằm ở **static root và collection retention**.

### 15.2. Reachable nhưng chưa chắc leak

Một cache có giới hạn, queue có consumers hoặc data store giữ objects theo nghiệp vụ có thể là **retention có chủ đích**. Không xem tất cả objects surviving GC là “memory leak”.

Điều tra cần trả lời:

1. Ai sở hữu object?
2. Có còn cần object cho công việc sau này không?
3. Chính sách eviction/release là gì?
4. Tăng trưởng có tỷ lệ với workload hợp lệ hay không?
5. Sau khi workload kết thúc, retained heap có giảm hoặc ổn định không?

### 15.3. Unreachable nhưng memory chưa giảm cũng chưa chắc leak

Nếu object không còn reachable nhưng GC chưa chạy, heap used có thể còn cao. Nếu GC đã reclaim, heap **committed** vẫn có thể được giữ để phục vụ allocations sau đó; RSS không nhất thiết giảm ngay.

Do vậy đừng chỉ nhìn **`Runtime.totalMemory()`** hoặc một biểu đồ RSS rồi kết luận object vẫn sống. Cần phân biệt allocated, live/retained, committed và resident.

## 16. Escape analysis, scalar replacement và giới hạn suy luận từ `new`

HotSpot có thể tối ưu allocation bằng các kỹ thuật như **escape analysis** và **scalar replacement** khi điều kiện cho phép:

```java
static int sumPoint() {
    Point p = new Point(3, 4);
    return p.x + p.y;
}
```

Đây là code minh họa, cần định nghĩa `Point`. Nếu compiler chứng minh object `p` không thoát khỏi method và các thao tác có thể được biểu diễn bằng scalar values, VM có thể tránh materialize một heap object đầy đủ trong một số đường thực thi.

Điều này không làm thay đổi logic: caller vẫn nhận tổng theo ngữ nghĩa Java. Nhưng nó cho thấy câu “mọi `new` luôn khiến heap usage tăng một số byte quan sát được” **không phải bảo đảm**.

### 16.1. Escape analysis không phải phép chứng minh business correctness

Tối ưu hóa cần bảo toàn ngữ nghĩa quan sát được. Nó không hứa sẽ xảy ra cho mọi object hoặc mọi JDK, cũng không tự ngăn leak khi ứng dụng thật sự lưu object vào static registry.

### 16.2. Object lifetime theo source khác lifetime theo runtime

Với tối ưu hóa, một object có thể:

- Được materialize ở heap.
- Bị loại bỏ allocation bằng scalar replacement khi phù hợp.
- Được giữ lại vì callback/field/GC root khác.
- Trở thành không còn cần sớm hơn dấu ngoặc nguồn.
- Được GC reclaim trong một chu kỳ về sau.

Vì vậy không thể suy ngược lịch GC từ cú pháp block hoặc thứ tự khai báo.

## 17. Kỹ thuật điều tra bằng GC logs, JFR và heap dump

Khi ứng dụng có memory pressure, cần **bằng chứng runtime** thay vì đoán phạm vi biến.

### 17.1. GC logging trong HotSpot

Ví dụ:

```bash
java -Xlog:gc* -jar application.jar
```

Đây là lệnh minh họa cho một JAR có thể chạy. HotSpot Unified Logging hỗ trợ các tags/levels tùy phiên bản. Logs giúp xem chu kỳ GC, heap occupancy và những thông tin collector cung cấp, nhưng **không tự chỉ ra reference edge nào giữ một object cụ thể**.

### 17.2. JFR: allocations và pressure

**Java Flight Recorder (JFR)** hỗ trợ quan sát allocation patterns, GC pauses, heap summaries và một số stack traces theo cấu hình recording. Các sự kiện cụ thể và sampling options khác nhau giữa JDK/build.

Dùng JFR để hỏi: “Ở đâu tạo nhiều objects?”, “Tốc độ allocation ra sao?”, “GC có theo kịp workload?”. Để hỏi “Ai vẫn giữ object lâu dài?”, có thể phải kết hợp heap dump hoặc công cụ phân tích retention.

### 17.3. Heap dump và path to GC root

Một công cụ như Eclipse Memory Analyzer hoặc trình phân tích heap dump có thể cho biết:

- **Shallow heap:** dung lượng của chính object.
- **Retained heap:** lượng memory mà đường retention của object có thể giữ theo phân tích dominator/reachability.
- **Paths to GC roots:** những đường tham chiếu qua đó object vẫn reachable.
- Số lượng instances theo class, collection entries, retained listeners và class loaders.

Ví dụ quy trình:

1. Chụp heap dump sau khi workload đã ổn định.
2. Lặp workload tạo nghi ngờ leak.
3. Chụp heap dump tiếp theo sau những GC/điều kiện phù hợp.
4. So sánh counts/retained sizes.
5. Kiểm tra paths to GC roots của nhóm objects tăng bất thường.
6. Xác định owner cần release trong code.

Heap dump chứa dữ liệu nhạy cảm có thể nằm trong objects, nên phải bảo vệ file, giới hạn quyền truy cập và xóa đúng quy trình. Việc chụp dump trên production có thể tốn disk, I/O, pause và memory; cần lên kế hoạch vận hành phù hợp.

### 17.4. `jcmd` trên HotSpot

Ví dụ chẩn đoán (thay PID cụ thể):

```bash
jcmd -l
jcmd <pid> GC.class_histogram
jcmd <pid> GC.heap_dump /path/to/heap.hprof
```

`<pid>` và đường dẫn là placeholders dùng khi thao tác thực tế, không phải lệnh đã chạy trên hệ thống người đọc. `GC.class_histogram` có thể tốn chi phí đáng kể và một số chế độ liên quan yêu cầu safepoint/GC; heap dump có thể rất lớn.

Không dùng histogram duy nhất để kết luận chính xác ai giữ object. Histogram nói **có bao nhiêu instances theo type**, còn path-to-root mới giúp trả lời **vì sao chúng còn reachable**.

## 18. Reachability và Java Memory Model trong concurrency

Đối tượng có thể được chia sẻ qua fields/queues giữa các threads. Tuy nhiên, **reachability không thay thế memory visibility hoặc synchronization**.

Ví dụ:

```java
class Holder {
    Object value;
}
```

Nếu thread A ghi `holder.value = object` và thread B đọc `holder.value` mà không có quan hệ happens-before hoặc synchronization phù hợp, chương trình có thể có data race. GC/JVM vẫn phải quản lý references an toàn theo runtime rules, nhưng điều đó **không làm việc công bố object trở thành thread-safe**.

Để chia sẻ đúng, dùng các cơ chế như:

- `final` fields với initialization discipline phù hợp.
- `volatile` references cho visibility khi thích hợp.
- `synchronized` hoặc locks.
- Concurrent collections, blocking queues.
- Các quy tắc safe publication theo Java Memory Model.

**GC giữ object sống** và **thread B nhìn thấy trạng thái object đúng** là hai vấn đề khác nhau. Không dùng “object còn reachable” làm bằng chứng dữ liệu trong fields đã được publish đúng.

### 18.1. Callback scheduling

Nếu một callback chạy async, callback có thể giữ các captured references đến khi task kết thúc hoặc bị cancel và được gỡ khỏi scheduler. Đây là một retention path độc lập với lexical scope của method tạo callback.

Ví dụ một tác vụ hẹn giờ không được hủy có thể giữ object graph trong nhiều giờ sau khi HTTP request gốc đã trả response.

## 19. Bảng phân loại các tình huống thường gặp

| Tình huống | Có thể suy luận chắc từ scope không? | Cách hiểu đúng |
| --- | --- | --- |
| Method trả object tạo trong local | **Không** thể kết luận object chết | Caller giữ returned reference |
| Local ra khỏi block nhưng static list vẫn chứa object | **Không** thể kết luận reclaim | Strong reference từ registry còn |
| Local vẫn trong block nhưng không còn dùng | **Không** chắc giữ object đến `}` | JIT có thể rút ngắn live range |
| `local = null` | **Không** ép collection | Chỉ bỏ một reference edge, còn edges khác có thể |
| `System.gc()` | **Không** guarantee mọi object được thu hồi ngay | GC scheduling/behavior phụ thuộc runtime |
| `WeakReference.get() == null` | Có thể biết weak referent đã bị clear | Không đo được thời gian chết chính xác hay external cleanup |
| `WeakReference.get() != null` | Có reference có thể lấy tại thời điểm đó | Không hứa referent tồn tại mãi sau khi không giữ strong reference |
| `PhantomReference.get()` | **Luôn null** | Dùng queue/cleanup, không dereference |
| `Reference.reachabilityFence(x)` | Có guarantee về live reachability qua call | Không tự close/free hoặc pin địa chỉ |
| `AutoCloseable.close()` | Resource cleanup theo contract của API | Java object có thể vẫn reachable sau close |
| `Cleaner` đăng ký action | Không hứa khi nào action tự chạy | Explicit close vẫn nên là đường chính |
| `ThreadLocal` trong thread pool | Scope request không quyết định retention | Thread lifetime/value removal quan trọng |
| Object cycle không có root | Có thể eligible for GC | Không phải do reference count > 0 mà bất tử |
| Static collection tăng mãi | Có khả năng retention/leak | Kiểm tra owner, eviction và path-to-root |
| Heap committed không giảm ngay | Không chứng minh leak | GC/allocator có thể giữ heap phục vụ tái sử dụng |

## 20. Những sai lầm phổ biến

### Sai lầm 1: “Hết scope là gọi destructor”
Java không có C++-style scope-bound destructor cho Java heap objects. Dấu đóng block điều khiển phạm vi biến, không trực tiếp reclaim heap memory. Với resources có thể đóng, dùng try-with-resources.

### Sai lầm 2: “`local = null` sẽ giảm RSS ngay”
Không. Object có thể còn references, GC có thể chưa chạy, heap committed có thể không trả OS.

### Sai lầm 3: “Nếu object còn trong scope, GC không được reclaim”
Không chính xác. Compiler/JIT có thể làm ngắn khoảng strong-reachability khi không còn computation cần object; `Reference.reachabilityFence` tồn tại chính để chỉ ra một use cuối khi cần.

### Sai lầm 4: “Có một vòng object tự tham chiếu nên GC không thu hồi”
Không. Nếu cả vòng không còn reachable qua roots thích hợp, tracing collector có thể reclaim các objects đó.

### Sai lầm 5: “`WeakReference` tự hủy object khi không còn references”
Không. WeakReference tham gia reference processing của collector; nó không gọi “delete object” tại một lệnh Java xác định.

### Sai lầm 6: “`SoftReference` có TTL do Java cố định”
Không. Soft reference clearing do VM/GC quyết định trong phạm vi đặc tả và memory pressure.

### Sai lầm 7: “`PhantomReference.get()` trả object lúc sắp chết”
Sai. `get()` trả null. Phantom references không dùng để resurrect/dereference object.

### Sai lầm 8: “`Cleaner` là bản thay thế deterministic cho `close`”
Sai. Cleaner không có deadline guarantee; nên ưu tiên explicit close.

### Sai lầm 9: “Finalizer sẽ được gọi chắc chắn trước process exit”
Sai. Finalization bị deprecated for removal, có thể bị tắt/loại bỏ và không cho guarantee về tiến độ cleanup cần thiết.

### Sai lầm 10: “Một static reference chỉ giữ object khi method đang chạy”
Sai. Static reference có thể giữ object qua rất nhiều requests, phụ thuộc class-loader lifetime và nơi nó được clear.

### Sai lầm 11: “ThreadLocal key weak nên value luôn tự mất”
Sai. ThreadLocal value retention trong thread pools cần xử lý tường minh; `remove()` trong `finally` là một cách thiết kế phù hợp.

### Sai lầm 12: “Memory leak trong Java nghĩa là GC không hoạt động”
Không. GC có thể hoạt động đúng nhưng không thể reclaim objects vẫn reachable vì ứng dụng giữ references không cần thiết.

### Sai lầm 13: “Heap dump chứa mọi object từng được tạo”
Không. Heap dump là snapshot của những objects được runtime ghi nhận theo cách dump cụ thể, không phải nhật ký đầy đủ allocations đã diễn ra.

### Sai lầm 14: “Chạy test 100 lần thấy weak ref clear là timing guarantee”
Không. GC và reference processing không có một deadline portable đơn giản tương ứng.

### Sai lầm 15: “`Reference.reachabilityFence` pin object ở một địa chỉ cố định”
Không. Nó bảo đảm reachability qua điểm gọi, không ngăn moving GC.

## 21. Bài tập tự kiểm tra kèm đáp án

**Câu 1.** Một local `User user` ra khỏi block, nhưng `cache.put(key, user)` đã chạy. Có thể kết luận User được GC ngay không?

**Đáp án:** Không. Cache vẫn có thể giữ reference mạnh tới User; cần xét đường tới root và eviction policy.

**Câu 2.** `method()` trả về object vừa tạo. Sau khi method kết thúc, object có bắt buộc mất đi không?

**Đáp án:** Không. Caller nhận reference và có thể tiếp tục dùng object.

**Câu 3.** Một object không còn được dùng sau dòng 20, nhưng biến khai báo ở đầu method và dấu `}` ở dòng 200. Có bảo đảm object sống đến dòng 200 không?

**Đáp án:** Không. Compiler/JIT có thể kết thúc live range sớm hơn khi hợp lệ. Không suy lifetime từ lexical scope.

**Câu 4.** Viết `x = null; System.gc();` có chứng minh object của x đã bị thu hồi ngay không?

**Đáp án:** Không. Còn references khác có thể có; `System.gc()` không cho guarantee như vậy.

**Câu 5.** Hai Nodes giữ references lẫn nhau nhưng không còn root path nào. Có thể eligible for GC không?

**Đáp án:** Có. Cyclic references không tự giữ object sống nếu toàn bộ vòng không reachable từ roots.

**Câu 6.** `WeakReference` được dùng làm key trong một map, nhưng value của map giữ strong reference ngược tới key. Vấn đề gì có thể có?

**Đáp án:** Một strong path thông qua value có thể làm key vẫn reachable, ngăn GC-based cleanup như kỳ vọng. Cần phân tích reference graph của toàn map.

**Câu 7.** `SoftReference.get()` trả null trong một lần chạy; lần sau có bắt buộc null ở cùng lượng heap free không?

**Đáp án:** Không. Clearing policy/timing phụ thuộc collector và runtime.

**Câu 8.** Khi nào dùng `Reference.reachabilityFence(owner)`?

**Đáp án:** Khi cần bảo đảm owner wrapper còn strongly reachable cho đến sau một đoạn công việc, đặc biệt native-resource code có thể chỉ sử dụng địa chỉ/handle tách khỏi Java reference.

**Câu 9.** `reachabilityFence` có thay `close()` được không?

**Đáp án:** Không. Nó không giải phóng resources; nó chỉ bảo đảm reachability tại điểm gọi.

**Câu 10.** Vì sao nên dùng try-with-resources cho file stream?

**Đáp án:** Vì cần deterministic close theo điều khiển chương trình, không đợi GC quyết định khi nào resource wrapper unreachable.

**Câu 11.** `ThreadLocal` giữ byte array giữa những HTTP requests trên cùng worker thread. Có thể sửa bằng cách nào?

**Đáp án:** Dùng `try/finally` với `ThreadLocal.remove()` nếu scope dữ liệu chỉ trong request; tránh giữ object graph quá lâu.

**Câu 12.** Object được in trong heap dump có nghĩa bị memory leak?

**Đáp án:** Không. Có thể là live data hợp lệ. Cần xem ownership, retained size, path to root và growth trend.

**Câu 13.** Một object có `final` field giữ reference tới child. Điều này có nghĩa child sống mãi?

**Đáp án:** Không. Child có thể được giữ khi parent reachable; nếu toàn bộ parent-child graph không còn reachable, `final` không làm nó bất tử.

**Câu 14.** Có nên kiểm tra GC bằng assert `weak.get() == null` sau `Thread.sleep(500)`?

**Đáp án:** Không. Đây là test phụ thuộc timing, không có guarantee portable.

**Câu 15.** Tăng `-Xmx` chữa được mọi memory leak?

**Đáp án:** Không. Nó có thể trì hoãn OOME cho một retention problem nhưng không sửa đường giữ object. Native memory/resource leaks còn là vấn đề khác.

## 22. Checklist phân tích tuổi thọ đối tượng trong dự án thực tế

1. Xác định **object nào** đang cần quản lý và “không còn cần” nghĩa gì theo nghiệp vụ.
2. Phân biệt **source scope**, **reference liveness** và **heap reachability**.
3. Vẽ **strong reference paths** từ các owners/roots.
4. Kiểm tra static registries, caches, listeners, callbacks, ThreadLocals, executors, class loaders.
5. Xác định explicit ownership/release APIs: `remove`, `clear`, `cancel`, `close`.
6. Với resources, dùng try-with-resources và xác định đường exception handling.
7. Với weak/soft/phantom references, đừng dựa vào timing/số lượng GC runs.
8. Với Cleaner, bảo đảm cleaning action không vô tình capture referent; đừng coi nó là cleanup chính.
9. Nếu native resource wrapper có thời gian sống nhạy cảm, nghiên cứu `Reference.reachabilityFence` đúng vị trí.
10. Dùng heap dumps/JFR/GC logs theo đúng mục đích; bảo vệ dữ liệu nhạy cảm.
11. So sánh memory trend qua nhiều workload/GC, không một snapshot đơn.
12. Kiểm tra cả Java heap, Metaspace, thread stacks và native resources khi RSS tăng.
13. Không dùng `System.gc()`, `finalize` hoặc `Thread.sleep` như lifecycle design.
14. Kiểm thử các đường hủy đăng ký listener, đóng pool/executor, plugin unload và ứng dụng shutdown.
15. Tách **correctness**, **resource safety** và **performance** thành những yêu cầu quan sát độc lập.

### Tóm tắt

```text
Source variable scope
     |  quy định chỗ dùng được tên biến
     X  KHÔNG phải lifetime contract cho heap object
     |
     v
Reference graph (strong / soft / weak / phantom)
     |
     v
Object reachability theo VM/reference processing
     |
     v
Eligible for reclamation khi đủ điều kiện
     |
     v
GC có thể reclaim theo runtime schedule

Ngoài ra:
  AutoCloseable / try-with-resources
       → deterministic resource cleanup
  Reference.reachabilityFence
       → bảo đảm strong reachability tới một điểm
  WeakReference / Cleaner
       → tương tác GC, không có deadline thu hồi
```

**Bài học cần giữ:** **Không được suy thời điểm object chết hoặc được thu hồi chỉ từ phạm vi của biến Java.** Hãy phân tích đồ thị tham chiếu, phạm vi sống thực, hành vi tối ưu hóa và hợp đồng reference/GC của JVM. Với tài nguyên quan trọng, quản lý vòng đời chủ động thay vì chờ collector.

### Tài liệu chính thức

- [JVMS `2.5.3 — Heap (Java SE 26)](https://docs.oracle.com/javase/specs/jvms/se26/html/jvms-2.html#jvms-2.5.3): heap objects và automatic storage management.
- [JLS `12.6.1 — Implementing Finalization (Java SE 26)](https://docs.oracle.com/javase/specs/jls/se26/html/jls-12.html#jls-12.6.1): reachability, optimizer có thể giảm live references, lịch sử finalization.
- [java.lang.ref — Package Specification (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/package-summary.html): các mức reachability và ý nghĩa của soft/weak/phantom references.
- [Reference.reachabilityFence (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Reference.html#reachabilityFence(java.lang.Object)): bảo đảm reachability tới một điểm cụ thể.
- [Cleaner (Java SE 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ref/Cleaner.html): cleaning actions và hạn chế non-deterministic cleanup.
- [JEP 421 — Deprecate Finalization for Removal](https://openjdk.org/jeps/421): vì sao không xây lifecycle mới trên `finalize`.
- [JLS `14 — Blocks and Statements](https://docs.oracle.com/javase/specs/jls/se17/html/jls-14.html): scope và control flow ở Java source.
- [Oracle JDK 17 — HotSpot GC Tuning, Other Considerations](https://docs.oracle.com/en/java/javase/17/gctuning/other-considerations.html): soft, weak, phantom references và cơ chế GC.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="hotspot-objects-009">Đánh giá chi phí của nhiều đối tượng nhỏ và cấu trúc dữ liệu giữ tham chiếu</a>

<details>
<summary>Xem chi tiết</summary>

# Đánh giá chi phí của nhiều đối tượng nhỏ và cấu trúc dữ liệu giữ tham chiếu trong HotSpot

## Mục tiêu

Một triệu giá trị `int` có thể được lưu trong một mảng primitive hoặc trong một triệu object riêng lẻ. Hai cấu trúc mang cùng dữ liệu logic nhưng có chi phí bộ nhớ và thực thi rất khác nhau. Với HotSpot, mỗi object nhỏ có thể cần **header**, **padding/alignment** và các **reference fields**; container còn có thể giữ **mảng tham chiếu**, **node** hoặc **entry**. Số object lớn cũng có thể tăng công việc cấp phát, GC tracing, pointer chasing và cache misses.

Sau bài học, bạn có thể:

- Phân biệt **shallow size**, **object graph footprint**, **retained size**, **live heap**, **allocated bytes** và **allocation rate**.
- Ước lượng các phần memory của object, reference array, primitive array và cấu trúc có N entries bằng **mô hình có giả định**, không gán số byte cố định cho mọi bản HotSpot.
- Nhận diện boxing, object caches, aliasing và việc một reference slot không tự đồng nghĩa thêm một object.
- So sánh `int[]`, `Integer[]`, `ArrayList<Integer>`, `LinkedList<Integer>`, `HashMap<K,V>` và cấu trúc node tự xây dựng.
- Hiểu ảnh hưởng của capacity, load factor, object lifetime, GC, CPU cache locality, escape analysis/JIT.
- Thực hành Java 17+, khảo sát dữ liệu bằng JOL, JFR, `jcmd`/heap dump và benchmark bằng JMH.
- Đưa ra quyết định tối ưu dựa trên phép đo và yêu cầu API, không chỉ dựa vào số lượng object.

## 1. Đếm phần tử logic không đủ để biết số object

Xét đoạn Java minh họa:

~~~java
int[] primitive = new int[1_000_000];
Integer[] boxed = new Integer[1_000_000];
~~~

Hai biểu thức tạo ra **mỗi bên một array object**, nhưng:

- `int[]` lưu trực tiếp 1.000.000 giá trị primitive int mặc định 0, không cần 1.000.000 Integer objects.
- `Integer[]` là **reference array** chứa 1.000.000 slots mặc định `null`. Lệnh tạo array **không tạo một triệu Integer objects**.
- Nếu điền boxed array bằng một triệu **references tới cùng một Integer**, vẫn chỉ có **một referent object** được chia sẻ (ngoài array).
- Nếu điền các giá trị Integer riêng biệt, số wrapper instances có thể tăng rất lớn; nhưng autoboxing/`Integer.valueOf` có **cache**, JIT có thể tối ưu trong các trường hợp hợp lệ.
- Hai arrays có cùng `length` không đồng nghĩa cùng lượng memory khi các phần tử của reference array đang trỏ tới object graphs khác nhau.

~~~text
int[N]
  [header][int0][int1][int2] ... [intN-1]

Integer[N] sau khi điền các referents riêng:
  [header][ref0][ref1][ref2] ... [refN-1]
             |     |     |
             v     v     v
         [Integer][Integer][Integer]

Integer[N] khi chia sẻ một referent:
  [header][ref0][ref1][ref2] ...
             \     |     /
              \    |    /
                [Integer]
~~~

**Phân biệt ngay từ đầu:** một **reference value** là giá trị cho biết object được tham chiếu, không phải toàn bộ object. Gán `target[i] = shared;` chỉ đặt một reference value vào slot; không clone object.

## 2. Object header, fields, padding và alignment

Theo mô hình JVM, các class instances và arrays nằm trên **heap**. HotSpot có quyền lựa chọn object layout vật lý, miễn giữ đúng Java/JVM semantics.

Một class instance điển hình có cấu trúc khái niệm:

~~~text
[ Object header / metadata runtime ]
[ Các instance fields của class và superclasses ]
[ Internal padding / alignment nếu cần ]
~~~

Khi có nhiều object nhỏ, chi phí lặp lại của **object header và alignment** có thể lớn so với dữ liệu hữu ích. Một class có một `byte` field không thể suy ra shallow instance size bằng một byte.

Đối với một cấu hình HotSpot 64-bit có **compressed oops**, **compressed class pointers** và **8-byte object alignment**, một layout thông dụng có thể có:
- Object header khoảng **12 bytes**.
- Compressed reference field khoảng **4 bytes**.
- `int` field có payload 4 bytes.
- Instance có một `int` có thể có shallow size **16 bytes** trong cấu hình này.

**Đây là ví dụ có điều kiện, không phải định lý cho mọi JDK.** HotSpot có thể hỗ trợ compact object headers hoặc options khác; architecture, JDK version, flags và alignment làm thay đổi các con số. Compressed oops cũng không bắt buộc bật trên mọi VM 64-bit.

Công thức tư duy:

~~~text
shallow_size(object)
  ~ align(header_bytes + field_bytes + padding_internal,
          alignment_bytes)
~~~

Đây là **ước lượng cấu trúc**, không thay thế việc đo; thứ tự fields, inheritance, packing và alignment do VM kiểm soát. Với `N` objects, tổng shallow bytes thường xấp xỉ `N × shallow_size` nếu thực sự có N instances cùng layout.

### Ví dụ class có reference link

~~~java
final class Link {
    int id;
    Link next;
}
~~~

Đây là class minh họa. Trong cấu hình giả định 12-byte header + 4-byte int + 4-byte compressed ref + alignment 8 byte:

~~~text
Unaligned: 12 + 4 + 4 = 20 bytes
Aligned:   24 bytes cho một Link instance
1_000_000 Link instances: khoảng 24_000_000 bytes
~~~

Con số **24 MB hệ thập phân** chỉ cho chính Link instances trong giả định ấy; chưa tính structures giữ chúng, các referents khác, metadata/GC overhead và heap capacity. `int[1_000_000]` có payload primitive 4_000_000 bytes, cộng header/alignment của **một array**. Tuy nhiên, một linked structure cung cấp chức năng và identity khác primitive array.

## 3. Primitive arrays, reference arrays và boxing

Các phần tử trong `int[]` được lưu trực tiếp bên trong array object. Các phần tử trong `Object[]` hoặc `Integer[]` là reference values; object referents nằm tách biệt theo mô hình runtime.

Mô hình size gần đúng, với `N` là length và `R` là reference width của JVM hiện tại:

~~~text
int[N]:
  array_overhead + 4*N + tail_padding

long[N]:
  array_overhead + 8*N + tail_padding

Object[N]:
  array_overhead + R*N + tail_padding
  + memory của referents nếu cần tính graph

Integer[N] đã được điền:
  array_overhead + R*N + tail_padding
  + M * shallow_size(Integer)
  (M là số Integer instances khác nhau thực sự được giữ)
~~~

`array_overhead` gồm metadata/header/length theo layout JVM thực tế, và **không có một giá trị bắt buộc chung** cho mọi platform.

### Autoboxing tạo thêm sự phức tạp

~~~java
Integer a = Integer.valueOf(42);
Integer b = Integer.valueOf(42);
System.out.println(a == b); // true
~~~

Đây là code snippet bên trong một phương thức. `Integer.valueOf` có cache bắt buộc cho vùng **-128..127**, nên hai reference ở đây cùng chỉ một Integer. Ngoài vùng đó, **không** dựa vào identity hoặc số allocations không được API hứa. Để so giá trị wrappers, dùng `equals` hoặc unbox phù hợp thay vì `==`.

Nếu một cấu trúc chứa một triệu số nguyên lớn khác nhau và giữ chúng như các Integer objects, chi phí box có thể cao. Ngược lại, nếu toàn bộ collection tham chiếu một object cache dùng chung, số wrapper objects không tương đương size của collection.

**Ví dụ ước lượng có điều kiện:** giả sử 1.000.000 phần tử, R=4, mỗi wrapper Integer shallow size 16 bytes, mọi wrapper là object riêng, backing array có capacity 1.000.000. Reference slots cần khoảng 4 MB và wrappers khoảng 16 MB, tổng khoảng 20 MB **chưa kể header/padding của array và container**. Với int[] cùng N, payload là khoảng 4 MB cộng một header. Đây **không phải số đo ứng dụng thực tế**.

## 4. Shallow size, reachable footprint, retained size và allocation rate

| Chỉ số | Câu hỏi | Lưu ý |
| --- | --- | --- |
| **Object count** | Có bao nhiêu instances/arrays? | Không bằng số phần tử logic hoặc số lần dùng `new` trong source. |
| **Shallow size** | Riêng object hiện tại chiếm bao nhiêu bytes? | Không cộng object được reference fields trỏ tới. |
| **Reachable graph footprint** | Từ một root có thể tới những object nào và tổng size chúng? | Có thể có chia sẻ với root khác. |
| **Retained size** | Nếu bỏ owner theo quan hệ dominator thì bao nhiêu bytes có thể trở nên unreachable? | Không phải tổng mọi referents reachable nếu có sharing. |
| **Allocated bytes** | Tổng bytes được cấp phát trong khoảng đo? | Có thể lớn dù phần lớn object đã chết. |
| **Live heap** | Bao nhiêu dữ liệu còn live ở thời điểm/tiêu chí đo? | Không bằng committed heap hoặc RSS tiến trình. |

Ví dụ:

~~~text
Root A ------> [List] ------> [LargePayload]
                                  ^
Root B ---------------------------+
~~~

Loại bỏ Root A/List không khiến `LargePayload` chết nếu Root B vẫn giữ reference. Vì vậy không thể tính retained size bằng cách cộng tất cả objects mà List reachable. Khi phân tích heap dump, *dominator tree* giúp tìm object owners thực sự giữ phần bộ nhớ quan trọng.

**Reachability là vấn đề của graph**, không phải của scope một biến duy nhất. `x = null` có thể cắt một edge, nhưng referent vẫn còn live nếu root/field/array khác giữ nó. GC cũng không bảo đảm trả ngay committed heap về OS.

## 5. ArrayList: backing array + references + elements

Trong triển khai JDK phổ biến, `ArrayList<E>` giữ elements bằng backing array of references. Capacity của array có thể lớn hơn size của list.

~~~java
ArrayList<String> names = new ArrayList<>(100);
names.add("A");
names.add("B");
System.out.println(names.size()); // 2
~~~

Đây là snippet cần `import java.util.ArrayList;`. `size()==2` không có nghĩa backing array chỉ có hai slots. Với capacity 100, vẫn có chi phí dự trữ reference slots.

Các phần trong memory:
1. **ArrayList instance** (metadata như size/reference tới backing array).
2. **Backing array object** với capacity `C ≥ size`.
3. **Element objects** nếu elements là references đến object do ứng dụng giữ.
4. Có thể có array tạm thời khi grow/copy.

Một biểu thức tư duy:

~~~text
ArrayList<E> graph footprint
  ~= shallow(ArrayList)
      + shallow(backing Reference[])
      + phần E referents thuộc graph (không đếm trùng sharing)
~~~

Đối với `ArrayList<Integer>`, cần xét **Integer wrappers**. Đối với `ArrayList<byte[]>`, cần xét từng **byte[] payload** nếu chúng thực sự tồn tại riêng, không chỉ reference slots.

Theo Java API, `ArrayList.add` có amortized constant-time behavior; `get(index)` thường nhanh, còn chèn/xóa ở giữa có thể dịch chuyển references. **Không hardcode một hệ số tăng capacity cụ thể cho mọi version JDK**, vì growth policy chi tiết là implementation detail.

### Dự trữ và thu gọn

- `new ArrayList<>(expectedSize)` hoặc `ensureCapacity(expectedSize)` có thể giảm số lần phải grow/copy nếu biết trước số phần tử sẽ thêm.
- Reserve quá lớn cho **nhiều** lists nhỏ có thể lãng phí memory đáng kể.
- `trimToSize()` giảm capacity theo size; nó không hủy các element objects đang được list giữ.
- Nếu có rất nhiều lists thường rỗng hoặc chỉ chứa 1–2 phần tử, **fixed overhead của chính list và backing array** có thể chiếm ưu thế.

**Không dùng reflection vào các private fields của ArrayList làm API**, vì module encapsulation, JVM update và implementation details có thể thay đổi.

## 6. LinkedList: nhiều node objects và pointer chasing

Một doubly linked list thường có mỗi phần tử trong một node lưu references `prev`, `next` và `item`. Trong các JDK thông dụng, `LinkedList<E>` có cơ chế node-based tương tự.

~~~text
LinkedList instance
      |
      v
[Node item,prev,next] -> [Node] -> [Node] -> ...
      |                   |        |
      v                   v        v
   [Element]           [Element] [Element]
~~~

Footprint gần đúng:

~~~text
shallow(LinkedList)
+ N * shallow(Node)
+ referent elements (trừ các phần được chia sẻ)
~~~

Mỗi node có **header, nhiều reference fields và alignment**, nên tổng overhead theo N thường cao hơn backing reference array tương ứng. Nếu element type là Integer, có thể vừa trả chi phí node **vừa** trả chi phí wrapper.

Không thể từ đó kết luận LinkedList luôn chậm/tệ:
- Thêm/xóa khi đã có iterator/node ở vị trí có thể tránh dịch chuyển cả mảng.
- Nếu cần random positional access, LinkedList thường phải traverse các nodes, tốn O(N) thời gian tìm vị trí.
- ArrayList có thể copy references theo block hiệu quả và có locality tốt hơn.
- Workload xử lý queues/deques có thể phù hợp hơn với `ArrayDeque`, tùy API và thread-safety.

**Quan trọng:** Big-O mô tả xu hướng thuật toán, **không bao gồm** mọi object allocation, CPU cache miss và GC overhead. Chọn theo workload, không theo một khẩu hiệu “LinkedList chèn nhanh”.

## 7. HashMap, HashSet và entry structures

Trong triển khai HotSpot/JDK phổ biến, `HashMap<K,V>` có một **bucket table** là reference array cùng các **entry/node objects** cho mappings. Khi collisions cao, một số buckets có thể có representation khác (ví dụ tree nodes).

~~~text
HashMap
  |
  v
table[capacity]
  |
  +--> bucket0: Node(keyRef, valueRef, nextRef)
  |
  +--> bucket1: null
  |
  +--> bucket2: Node --> Node ...
~~~

Thành phần chi phí:
- Bản thân **HashMap object**.
- **Backing table** với capacity buckets, kể cả buckets chưa dùng.
- **Entry objects** hoặc representation tương đương tùy JDK.
- **Key objects**, **value objects** và graphs phía sau chúng.
- Resize/re-hash, load factor, collision handling và capacity dư.

Một triệu mappings không đồng nghĩa một triệu key objects mới và một triệu value objects mới: keys/values có thể tạo ngoài map và được chia sẻ; `put` cùng key có thể thay value mà không tăng size. Với boxed keys như Integer/Long, cần tính thêm wrappers nếu chúng là instances riêng.

### Load factor và capacity

Load factor ảnh hưởng đến quan hệ giữa số entries và backing table capacity. Capacity lớn có thể tạo nhiều reference slots chưa dùng; capacity nhỏ có thể khiến resize/collision tốn kém. Phải đánh giá trên phân phối keys/operations thực, không chọn theo một con số duy nhất.

`HashSet<E>` trong JDK thông dụng sử dụng hash-based storage tương tự ở mức cơ chế, nên không “miễn phí” node/table chỉ vì API chỉ thể hiện một tập phần tử.

**Không gắn số bytes cố định cho `HashMap$Node` hay `LinkedList$Node`** như chuẩn Java SE. Layout của các inner classes là chi tiết của bản JDK, và VM headers/field packing ảnh hưởng thêm.

## 8. Cấu trúc lưu tham chiếu có sharing hoặc deep object graph

Một cấu trúc chứa 2 references có thể trỏ:
- **Cùng một object** → hai edges, một referent.
- **Hai object khác nhau** → hai edges, hai referents.
- **Hai object khác nhau cùng giữ một payload** → nhiều objects ở tầng ngoài, một payload chung.
- **Hai payload độc lập** → nhiều referents và nhiều mảng dữ liệu.

Mô hình:

~~~text
Owner A ---+
           +---> [SharedPayload byte[]]
Owner B ---+

Owner C ------> [SeparatePayload C]
Owner D ------> [SeparatePayload D]
~~~

### Ví dụ Java 17+ chạy độc lập: sharing

Tạo file **`ReferenceSharingDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.List;

public class ReferenceSharingDemo {
    public static void main(String[] args) {
        byte[] shared = new byte[4];

        List<byte[]> reused = new ArrayList<>();
        reused.add(shared);
        reused.add(shared);

        List<byte[]> independent = new ArrayList<>();
        independent.add(new byte[4]);
        independent.add(new byte[4]);

        System.out.println("reusedSame=" +
                (reused.get(0) == reused.get(1)));
        System.out.println("independentSame=" +
                (independent.get(0) == independent.get(1)));

        reused.get(0)[0] = 9;
        System.out.println("aliasValue=" + reused.get(1)[0]);
        System.out.println("independentValue=" + independent.get(1)[0]);
        System.out.println("reusedSlots=" + reused.size());
        System.out.println("independentSlots=" + independent.size());
    }
}
~~~

Biên dịch và chạy trên JDK 17+:

~~~bash
javac --release 17 ReferenceSharingDemo.java
java ReferenceSharingDemo
~~~

**Stdout chính xác:**

~~~text
reusedSame=true
independentSame=false
aliasValue=9
independentValue=0
reusedSlots=2
independentSlots=2
~~~

Các lists đều giữ hai entries về mặt logic; `reused` giữ **một byte[] referent dùng chung**, trong khi `independent` giữ hai byte[] objects riêng. Việc aliasing làm thay đổi footprint của graph nhưng cũng có ảnh hưởng tới **mutation semantics**: sửa shared payload qua một reference sẽ hiển thị qua reference khác.

**Chia sẻ object không phải lúc nào cũng phù hợp.** Nếu các owners cần mutate độc lập, sharing gây side effects không mong muốn. Quyết định tiết kiệm memory phải bảo toàn correctness và ownership.

## 9. Ví dụ Java 17+: cùng năm số, nhiều representation

Tạo file **`ContainerShapeDemo.java`**:

~~~java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class ContainerShapeDemo {
    static final class Node {
        int value;
        Node next;

        Node(int value, Node next) {
            this.value = value;
            this.next = next;
        }
    }

    static int sumNodes(Node head) {
        int sum = 0;
        for (Node n = head; n != null; n = n.next) {
            sum += n.value;
        }
        return sum;
    }

    static int sumList(List<Integer> values) {
        int sum = 0;
        for (int v : values) {
            sum += v;
        }
        return sum;
    }

    public static void main(String[] args) {
        int[] values = {10, 20, 30, 40, 50};

        Integer[] boxed = new Integer[values.length];
        ArrayList<Integer> arrayList =
                new ArrayList<>(values.length);
        LinkedList<Integer> linkedList = new LinkedList<>();

        Node head = null;
        for (int i = values.length - 1; i >= 0; i--) {
            head = new Node(values[i], head);
        }

        int primitiveSum = Arrays.stream(values).sum();

        for (int i = 0; i < values.length; i++) {
            boxed[i] = Integer.valueOf(values[i]);
            arrayList.add(boxed[i]);
            linkedList.add(boxed[i]);
        }

        int boxedSum = 0;
        for (Integer v : boxed) {
            boxedSum += v;
        }

        System.out.println("primitiveSum=" + primitiveSum);
        System.out.println("boxedSum=" + boxedSum);
        System.out.println("arrayListSum=" + sumList(arrayList));
        System.out.println("linkedListSum=" + sumList(linkedList));
        System.out.println("nodeSum=" + sumNodes(head));
        System.out.println("arrayLength=" + values.length);
        System.out.println("listSize=" + arrayList.size());
        System.out.println("sameFirstRef=" +
                (arrayList.get(0) == boxed[0]));
    }
}
~~~

Lệnh:

~~~bash
javac --release 17 ContainerShapeDemo.java
java ContainerShapeDemo
~~~

**Stdout chính xác:**

~~~text
primitiveSum=150
boxedSum=150
arrayListSum=150
linkedListSum=150
nodeSum=150
arrayLength=5
listSize=5
sameFirstRef=true
~~~

Ý nghĩa:
1. `int[]` chứa 5 primitive values trong một array object.
2. `Integer[]` chứa 5 reference slots. Các giá trị 10,20,30,40,50 nằm trong vùng Integer cache bắt buộc, nên không dùng chương trình này để đếm 5 wrapper allocations.
3. `ArrayList<Integer>` và `LinkedList<Integer>` cùng giữ **những reference values đã cung cấp** từ boxed array; không tự deep-copy element objects.
4. LinkedList có thể thêm node instances, còn ArrayList dùng backing array.
5. Chuỗi Node được tự xây gồm **5 Node instances riêng**, mỗi node có int field và một link reference.
6. Các tổng số bằng nhau, nhưng footprint và memory-access shape khác nhau.

Ví dụ minh họa semantics, **không** phải benchmark throughput hoặc công cụ đo bytes.

## 10. Object locality, pointer chasing và CPU cache

Dữ liệu nằm trong một primitive array có xu hướng hỗ trợ duyệt tuần tự tốt nhờ contiguous layout. Trong một reference array, **reference slots** liên tiếp nhưng **referents** không bắt buộc liên tiếp. Linked nodes cần đọc `next` trước khi biết node tiếp theo nằm đâu.

~~~text
int[]:
  read element0 -> element1 -> element2 -> ...

ArrayList<E>:
  read ref0 -> dereference E0
  read ref1 -> dereference E1
  ...

LinkedList<E>:
  read node0.next -> node1.next -> node2.next ...
~~~

Tác động có thể gồm:
- CPU cache misses, TLB pressure.
- Kém khả năng hardware prefetch đối với dependent pointer chasing.
- Nhiều object header/metadata hơn cần GC quản lý.
- Allocation churn nếu liên tục tạo/hủy nodes.
- Thời gian tìm vị trí khi truy cập bằng index.

Tuy nhiên, tính locality không phải guarantee bất biến. Collector có thể di chuyển objects, HotSpot có thể cấp phát gần nhau vào một lúc nhưng không đảm bảo chúng luôn sát nhau. Nếu workload chủ yếu lookup hash key, thuật toán tốt có thể quan trọng hơn locality thuần; nếu chỉ duyệt rất ít entries, chi phí cấu trúc có thể khác.

## 11. Allocation rate khác retained heap

Hai ứng dụng đều còn 100 MB live objects nhưng có thể có allocation rate rất khác:
- A tạo dữ liệu một lần, giữ lâu.
- B tạo hàng triệu temporary wrappers mỗi giây rồi bỏ.

B có thể gây GC/CPU load lớn hơn dù cuối khoảng đo live heap bằng A. Ngược lại, nhiều objects nhỏ sống lâu có thể tăng GC tracing/live-set footprint ngay cả khi allocation rate thấp.

| Metric | Dùng để |
| --- | --- |
| **Bytes allocated per operation** | Biết mỗi request/công việc cấp phát nhiều không. |
| **Allocation rate bytes/s** | Xem pressure tổng ở steady state. |
| **Live heap sau GC phù hợp** | Ước lượng tập dữ liệu cần tồn tại. |
| **Retained heap/dominator** | Tìm owner đang giữ objects không còn cần. |
| **GC pauses, frequency, CPU** | Đánh giá ảnh hưởng đến latency/throughput. |
| **Process RSS / committed heap** | Khảo sát footprint hệ thống, không đồng nhất chúng với live objects. |

Vấn đề memory leak ở Java thường là **reference retention**: cache không eviction, listener không unregister, static collection phình to, thread-local giữ dữ liệu quá lâu, hoặc key/value lifecycle sai. GC không tự thu objects vẫn reachable từ roots.

## 12. JIT và escape analysis: số `new` không bằng số allocations vật lý

Ví dụ:

~~~java
static int calculate(int a, int b) {
    Pair p = new Pair(a, b);
    return p.first + p.second;
}
~~~

Đây là snippet cần class `Pair` phù hợp. Nếu JVM JIT chứng minh object không escape và loại bỏ allocation không thay đổi hành vi quan sát, nó **có thể** sử dụng **scalar replacement** thay vì tạo object vật lý.

Những điều **không thể** kết luận chỉ từ source:
- Mọi `new` chắc chắn tiêu tốn một heap object vật lý trên mọi JIT run.
- Mọi object nhỏ chắc chắn được stack-allocate.
- JIT chắc chắn loại bỏ riêng allocation trong ví dụ này ở mọi Java version/tier.
- Bytecode vẫn có opcode `new` thì bắt buộc profiler phải đếm một allocation trong optimized execution.

Để đánh giá object churn, cần quan sát trong context thực tế: warmup, compiler tier, escape, inlining, garbage collector và profiler overhead. Một phép thử không tiêu thụ kết quả có thể bị dead-code elimination, làm số đo gần như vô nghĩa.

## 13. Đo object layout bằng JOL

**Java Object Layout (JOL)** là công cụ OpenJDK nghiên cứu object layout/footprint thực tế và mô phỏng các VM modes. JOL giúp xác nhận header, alignment, field offsets và cách references đóng góp vào graph.

Các ví dụ lệnh (giả sử đã tải đúng JAR JOL CLI và đặt tên là `jol-cli.jar`):

~~~bash
java -jar jol-cli.jar internals java.lang.Integer
java -jar jol-cli.jar internals-estimates java.lang.Integer
java -jar jol-cli.jar footprint java.util.HashMap
~~~

Đây là hướng dẫn công cụ **ngoài Java SE**, không đảm bảo một output cố định. Phiên bản JOL CLI, VM mode, flags, class structure và quyền truy cập có thể ảnh hưởng lệnh khả dụng/kết quả.

- **internals**: cho biết header/field/padding và layout object.
- **internals-estimates**: ước lượng một số bố cục trong những VM modes hỗ trợ.
- **footprint**: khảo sát object graph, cần phân biệt reachable graph với retained dominator ownership.
- Một số chế độ heapdump của JOL giúp thống kê objects/wrappers/duplicates ở mức heap.

Trang dự án: https://openjdk.org/projects/code-tools/jol/ và tài liệu: https://github.com/openjdk/jol.

### Ghi rõ giả định khi báo kết quả

Một báo cáo đo memory cần có:
- JDK exact version/build, HotSpot architecture, VM flags/compressed references/alignment.
- GC collector và heap flags liên quan.
- Kích thước N, array capacity, load factor, dữ liệu key/value và mức object sharing.
- Đo **shallow** hay **graph/retained**.
- Kết quả bao gồm hay không bao gồm object wrappers, backing arrays, secondary indexes.
- Số lần chạy, mức độ nhiễu và cách kiểm chứng.

Nếu chỉ nói “Integer nặng 16 bytes” mà không cho biết giả định VM, phép tính không đủ đáng tin cho quyết định triển khai.

## 14. Dùng JFR, jcmd và heap dump để điều tra thực tế

**JFR (Java Flight Recorder)** có thể cung cấp thông tin allocation hotspots, event samples và hoạt động VM; **jcmd** có thể lấy heap info, histogram, heap dump hoặc điều khiển JFR trên HotSpot có hỗ trợ.

Một số mẫu lệnh cho một **tiến trình Java đang chạy** (thay `PID` bằng số PID thật):

~~~bash
jcmd PID GC.heap_info
jcmd PID GC.class_histogram
jcmd PID JFR.start name=MemStudy settings=profile duration=60s filename=memory.jfr
jcmd PID JFR.check
~~~

`PID` là **ký hiệu placeholder trong hướng dẫn dòng lệnh**, không phải chuỗi cần giữ nguyên khi chạy. Các diagnostic commands có thể có tác động đáng kể, đặc biệt class histogram trên heap lớn; không bật tùy tiện trên production.

Khi cần dump:

~~~bash
jcmd PID GC.heap_dump filename=memory.hprof
~~~

**Cảnh báo bảo mật:** heap dump có thể chứa mật khẩu, token, session data, PII hoặc nhiều dữ liệu nhạy cảm. Chỉ thu thập, lưu/chia sẻ với quyền phù hợp, bảo vệ file và quy định retention. Dump có thể rất lớn và ảnh hưởng throughput/GC khi chạy.

### Histogram nói gì và không nói gì?

Class histogram nhóm objects theo runtime class, hiển thị số instances và shallow bytes được ghi nhận. Nó hữu ích khi thấy quá nhiều `Integer`, `HashMap$Node`, `byte[]` hoặc domain records.

Nhưng histogram **không xác định đầy đủ**:
- Ai đang giữ những objects ấy reachable?
- Retained heap của một cache/owner?
- Các temporary allocations đã chết trước thời điểm chụp?
- CPU cache misses hoặc latency của traversal?

Để tìm nguyên nhân **retention**, dùng heap dump với **dominator tree và paths to GC roots**. Để tìm nguyên nhân **churn**, dùng JFR/allocation profiler. Nếu cần performance, phải đo thời gian và throughput riêng.

## 15. Benchmark bằng JMH

Không thể kết luận hiệu năng chỉ từ vòng lặp đo bằng `System.nanoTime()` một lần. HotSpot JIT có thể:
- Warm up và inline khác qua các iteration.
- Dùng scalar replacement làm biến mất object allocations.
- Loại bỏ code mà kết quả không được sử dụng.
- Dùng CPU caches khác tùy dữ liệu có được reuse.
- Có nhiễu GC, scheduling, CPU frequency, contention và profiler.

**JMH (Java Microbenchmark Harness)** là dự án OpenJDK cho microbenchmarks, hỗ trợ warmup, measurement iterations, forks, state và tiêu thụ kết quả để giảm sai lệch.

Một kế hoạch test công bằng:

| Phép đo | Cần giữ cố định |
| --- | --- |
| Duyệt `int[]` vs `ArrayList<Integer>` | Cùng N và values; tiêu thụ tổng; record boxing/cache setup |
| Duyệt `ArrayList<E>` vs `LinkedList<E>` | Cùng referents, setup dữ liệu ngoài vùng đo nếu muốn chỉ đo iteration |
| Insert/remove middle | Tính cả cách xác định vị trí hoặc tách chi phí search đúng intent |
| Lookup `HashMap` vs array scan | Cùng keys, query distribution, load factor và cardinality |
| Tạo N objects mỗi request | Đo throughput, bytes/op và GC cùng nhau |
| Structure-of-arrays vs object-per-entry | Đảm bảo cùng semantics và access pattern |

**Không coi JMH score là điểm số end-to-end** của một ứng dụng web/service: network, DB, serialization, locks và latency tail có thể quan trọng hơn micro-operation.

Trang dự án JMH: https://openjdk.org/projects/code-tools/jmh/.

## 16. Chọn cấu trúc nào?

| Workload | Lựa chọn thường nên xem xét đầu tiên | Trade-off |
| --- | --- | --- |
| Nhiều số nguyên và duyệt theo thứ tự | `int[]`, `long[]` hoặc primitive-specialized collections | Packing tốt, nhưng API/resize khác generic List |
| Danh sách objects truy cập theo index | `ArrayList<E>` | Backing array/capacity + referents |
| FIFO/LIFO | `ArrayDeque<E>` hoặc queue phù hợp | Dữ liệu lưu qua references, đồng bộ tùy API |
| Lookup bằng key | `HashMap<K,V>` | Table, nodes, load factor, key/value graphs |
| Sorted/range queries | `TreeMap<K,V>` hoặc sorted array theo workload | Tree-node overhead vs chi phí cập nhật array |
| Xóa/chèn với iterator đã ở vị trí | `LinkedList<E>` khi thực sự phù hợp | Node overhead/pointer chasing, random get đắt |
| Rất nhiều records nhỏ đồng nhất | Object array vs **structure-of-arrays** | Packing và locality vs identity/API complexity |
| Cache có khả năng tăng vô hạn | Bounded cache/eviction policy | Cần giới hạn owner retention rõ ràng |

### Khi nào dùng structure-of-arrays?

Thay vì N instances như:

~~~java
class Entry {
    int id;
    long timestamp;
}
~~~

có thể giữ hai primitive arrays:

~~~java
int[] ids = new int[n];
long[] timestamps = new long[n];
~~~

Các đoạn trên là **minh họa cần context khai báo biến n và class hợp lệ**. Representation thứ hai giúp loại bỏ nhiều object headers, có thể tăng cache locality cho scan. Nhưng nó thay đổi semantics:
- Không còn identity object per entry theo cách ban đầu.
- Dễ phát sinh sai lệch index khi delete/reorder.
- Cần kiểm soát concurrent updates, growth và invariant giữa hai arrays.
- Không tốt mặc định cho mọi access pattern.

Dùng dữ liệu đo thật và maintainability để quyết định, không ép tất cả domain model về arrays.

## 17. Checklist tối ưu có kiểm chứng

1. Nêu rõ vấn đề: memory budget, GC pauses, throughput, tail latency hay allocation CPU?
2. Định lượng số phần tử **N**, size distribution, lifetime, access pattern, null/sharing ratio.
3. Vẽ object graph: container, backing arrays, nodes/entries, keys/values, wrappers, payload arrays.
4. Tách **chi phí dữ liệu** khỏi **chi phí cấu trúc giữ references**.
5. Chọn metric phù hợp: shallow, graph, retained, bytes/op hoặc latency.
6. Ghi JDK/VM flags/alignment/compressed-oops/collector trước khi so bytes.
7. Đo baseline bằng JOL/JFR/heap dump/profiler/JMH tùy câu hỏi.
8. Đổi một yếu tố: reserve capacity, primitive storage, node count, cache policy, reuse.
9. Kiểm tra **correctness** sau đổi: aliasing, identity, equals/hashCode, null, concurrency.
10. Đo lại trên workload thực, ghi ưu/nhược điểm và rollback nếu trade-off không phù hợp.

**Tối ưu đúng** không phải luôn có ít objects nhất mà là đạt SLA và memory budget **trong khi duy trì đúng tính năng, tính đúng đắn và độ dễ bảo trì**.

## 18. Ngộ nhận thường gặp

| Nhận định | Cách hiểu đúng |
| --- | --- |
| “Một object có int field thì chiếm 4 byte.” | Header, alignment và fields khác cũng góp vào shallow size. |
| “JVM 64-bit thì mọi reference phải 8 byte.” | Compressed oops có thể dùng độ rộng nhỏ hơn. |
| “Object array có N slots tức là đã tạo N element objects.” | Slots chỉ chứa references, ban đầu null. |
| “Mỗi autoboxing chắc chắn new một object.” | Caches/optimizer có thể ảnh hưởng object identity/allocation. |
| “ArrayList size chính là capacity.” | Capacity có thể dư, làm tăng backing array footprint. |
| “LinkedList tiết kiệm vì không có backing array.” | Có nhiều node objects và refs prev/next/item. |
| “HashMap chỉ tốn key + value.” | Còn table, entries/nodes, capacity và collision handling. |
| “Reachable footprint luôn bằng retained size.” | Nếu graphs share objects, các số có thể khác nhau. |
| “Live heap ít thì allocation rate cũng ít.” | Objects ngắn hạn có thể được tạo rồi bỏ rất nhanh. |
| “Heap committed không giảm nghĩa là leak.” | VM có thể giữ committed memory để tái sử dụng. |
| “GC thấy object lớn là sẽ tự xóa nếu nghiệp vụ không dùng.” | Nếu vẫn reachable từ roots, GC không biết logic nghiệp vụ. |
| “Số bytes JOL trên máy tôi là chuẩn cho mọi JVM.” | Layout phụ thuộc JDK/HotSpot options/architecture. |
| “TLAB thuộc stack.” | TLAB là một cơ chế cấp phát trong heap. |
| “Mọi `new` chắc chắn tạo allocation vật lý.” | JIT có thể loại bỏ allocation khi giữ nguyên semantics. |
| “ArrayList luôn nhanh hơn LinkedList trong mọi hoạt động.” | Cần xét thuật toán, search, mutation và workload. |
| “Heap dump có thể chia sẻ thoải mái.” | Có nguy cơ chứa secrets/PII, phải kiểm soát truy cập. |

## 19. Câu hỏi tự kiểm tra

**Câu 1.** Vì sao không lấy `4 × N` bytes để tính N instances có một field int?

**Đáp án:** Vì mỗi instance còn có header, alignment/padding và có thể inherited fields; cần đo shallow size thực tế.

**Câu 2.** `new Integer[1000]` tạo bao nhiêu Integer objects?

**Đáp án:** Không tạo Integer referent nào, chỉ tạo một Integer[] array có 1000 null reference slots.

**Câu 3.** Nhiều reference slots cùng trỏ một object sẽ ảnh hưởng graph như thế nào?

**Đáp án:** Có nhiều references nhưng một referent, tránh nhân object footprint theo số slots; sharing lại có mutation semantics riêng.

**Câu 4.** Tại sao `ArrayList<Integer>` có thể tốn hơn `int[]`?

**Đáp án:** ArrayList object + backing reference array + Integer wrappers (nếu nhiều objects riêng), trong khi int[] lưu primitive values trong một array.

**Câu 5.** Những values của Integer ở vùng cache có chắc mỗi lần boxing đều cấp phát mới không?

**Đáp án:** Không. `Integer.valueOf` có cache bắt buộc cho -128..127.

**Câu 6.** LinkedList thường có overhead per element gì?

**Đáp án:** Node object với header, các references và padding, cộng elements được tham chiếu.

**Câu 7.** HashMap thêm overhead gì ngoài keys và values?

**Đáp án:** Table, entry nodes, capacity dư, collision/tree structures tùy implementation.

**Câu 8.** Retained size có bằng sum mọi object reachable từ owner không?

**Đáp án:** Không khi có object sharing từ GC roots khác. Retained liên quan quan hệ dominator.

**Câu 9.** Allocation rate cao nhưng live heap nhỏ có thể xảy ra không?

**Đáp án:** Có. Những temporary objects có lifetime rất ngắn vẫn tạo GC/allocation churn.

**Câu 10.** Đo object layout bằng gì? Đo allocation hotspots bằng gì?

**Đáp án:** JOL để xem layout/footprint; JFR/profiler để khảo sát allocation; heap dump/dominator cho retention.

**Câu 11.** Có thể dùng một vòng lặp `System.nanoTime()` không tiêu thụ kết quả để chứng minh data structure nhanh hơn không?

**Đáp án:** Không; JIT có thể loại bỏ code/allocations, warmup và GC gây nhiễu; dùng JMH và benchmark hợp lệ.

**Câu 12.** Khi nào struct-of-arrays có thể phù hợp?

**Đáp án:** Với nhiều records có schema đồng nhất, truy cập tuần tự/analytical và memory/locality là bottleneck, sau khi cân nhắc identity, API và đo đạc.

## 20. Bản đồ ghi nhớ

~~~text
                   N PHẦN TỬ LOGIC
                         |
         +---------------+----------------+
         |                                |
    PRIMITIVE                         REFERENCE
      int[N]                         Object[N] / List<E>
         |                                |
    1 array header                    array slots R*N
    + 4*N payload                         |
                                    element referents
                                         |
                               +---------+----------+
                               |         |          |
                             wrappers   nodes      key/value
                               |         |          |
                               +---------+----------+
                                         |
                               OBJECT GRAPH FOOTPRINT
                                         |
                    +--------------------+------------------+
                    |                    |                  |
                SHALLOW SIZE        RETAINED SIZE     ALLOCATION RATE
                layout/headers      dominator graph   churn/GC impact
                    |                    |                  |
                    +--------------------+------------------+
                                         |
                             latency / throughput / cache
                                         |
                                ĐO TRÊN HOTSPOT THẬT

JOL = layout       JFR = allocation       Heap dump = retainers
JMH = performance  jcmd = VM diagnostics
~~~

**Kết luận:** chi phí của nhiều object nhỏ có thể tích lũy từ **header, alignment, nodes/entries và references**, không chỉ bytes của fields. Cần tính **cấu trúc giữ reference** cùng với **referent graph**, và tách **retained/live heap** khỏi **allocation churn**. Mô hình ước lượng có ích để định hướng, nhưng quyết định kỹ thuật phải dựa trên phép đo **đúng JDK/HotSpot, đúng cấu hình và đúng workload**.

## Tài liệu tham khảo

- [JVMS Java SE 21 — §2.5.3 Heap](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html#jvms-2.5.3).
- [JLS Java SE 21 — Types, Values and Variables](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html).
- [OpenJDK — Java Object Layout (JOL)](https://openjdk.org/projects/code-tools/jol/).
- [OpenJDK — JOL README (layout/footprint)](https://github.com/openjdk/jol).
- [OpenJDK — Java Microbenchmark Harness (JMH)](https://openjdk.org/projects/code-tools/jmh/).
- [Java SE 21 — ArrayList API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/ArrayList.html).
- [Java SE 21 — LinkedList API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedList.html).
- [Java SE 21 — HashMap API](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/HashMap.html).
- [JDK 21 — jcmd](https://docs.oracle.com/en/java/javase/21/docs/specs/man/jcmd.html).


</details>

- [Quay lại đầu trang](#back-to-top)
