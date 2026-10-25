<a id="back-to-top"></a>

# Kiểu dữ liệu JVM

## Menu
- [Kiểu nguyên thủy và kiểu tham chiếu trong mô hình thực thi](#jvm-data-types-001)
- [Kiểu int dùng cho nhiều phép toán trên byte, short, char và boolean](#jvm-data-types-002)
- [long, double và cách sử dụng hai vị trí trong biến cục bộ](#jvm-data-types-003)
- [Giá trị null, kiểu lớp, kiểu giao diện và kiểu mảng](#jvm-data-types-004)
- [Số nguyên tràn, phép chuyển kiểu và ngữ nghĩa phép toán bytecode](#jvm-data-types-005)
- [Số thực IEEE 754: NaN, vô cực, số không có dấu và tính nhất quán](#jvm-data-types-006)
- [Kiểu returnAddress và lý do cần nhận diện bytecode lịch sử](#jvm-data-types-007)
- [Giá trị Java, tham chiếu JVM và địa chỉ vật lý: những khái niệm cần phân biệt](#jvm-data-types-008)

## <a id="jvm-data-types-001">Kiểu nguyên thủy và kiểu tham chiếu trong mô hình thực thi</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu nguyên thủy và kiểu tham chiếu trong mô hình thực thi JVM

Trong Java, một biến có thể mang **giá trị nguyên thủy (primitive value)** hoặc **giá trị tham chiếu (reference value)**. JVM cần biết loại giá trị để chọn lệnh bytecode, biểu diễn dữ liệu trong local variables và operand stack, kiểm tra tính đúng đắn của các thao tác và quản lý đối tượng. Phân biệt này giải thích tại sao gán một biến `int` lại sao chép con số, còn gán một biến chứa tham chiếu tới đối tượng có thể khiến hai biến cùng quan sát thay đổi trên đối tượng đó.

Nội dung tập trung vào **mô hình thực thi**, vì vậy cần tách ba khái niệm thường bị nhầm:

1. **Kiểu của ngôn ngữ Java**: trình biên dịch kiểm tra khai báo `int`, `boolean`, `String`, `int[]`, class, interface, generic...
2. **Kiểu và lệnh của JVM**: class file dùng type descriptor, tập lệnh như `iload`/`aload`, operand stack, local-variable slots và bytecode verifier để thực thi.
3. **Đối tượng trong bộ nhớ**: một tham chiếu dùng để truy cập đối tượng/mảng; GC và runtime quản lý vòng đời bộ nhớ theo triển khai JVM. Không thể suy ra bố cục bit hay vị trí vật lý từ riêng một khai báo Java.

## 1. Hai họ kiểu ở cấp ngôn ngữ Java

| Nhóm | Kiểu/giá trị tiêu biểu | Đặc tính khi gán |
| --- | --- | --- |
| **Primitive** | `boolean`, `byte`, `short`, `char`, `int`, `long`, `float`, `double` | Gán sao chép **giá trị** kiểu nguyên thủy |
| **Reference** | Biến của class (`String`, `Object`), interface, array (`int[]`), type variable và các kiểu tham chiếu khác | Gán sao chép **giá trị tham chiếu**, có thể cùng trỏ tới một đối tượng/mảng |
| **`null`** | Giá trị đặc biệt có thể dùng với các kiểu tham chiếu tương thích | Biểu thị **không tham chiếu đối tượng nào**; không gán được trực tiếp cho biến primitive |

Tám primitive trên là **kiểu của Java**, không phải tám loại lệnh độc lập trong JVM. `String` là class và `String` variable là tham chiếu, dù cú pháp literal `"hello"` trông đơn giản. Mảng nguyên thủy `int[]` **vẫn là đối tượng mảng**, vì vậy biến `int[]` là biến tham chiếu; **phần tử** của nó mới có kiểu `int`.

**Các trường hợp dễ nhầm:**

- `int number = 10;`: `number` chứa giá trị số nguyên; gán `int copy = number;` cho bản sao độc lập của giá trị.
- `Person p = new Person();`: `p` chứa giá trị tham chiếu tới một đối tượng, không phải toàn bộ đối tượng đặt “bên trong biến”.
- `Person q = p;`: sao chép **tham chiếu**; `p` và `q` có thể chỉ cùng một đối tượng. Đây **vẫn là truyền/gán theo giá trị (pass/copy by value)**, không phải “biến Java được truyền bằng tham chiếu”.
- `Person absent = null;`: biến có kiểu tham chiếu nhưng hiện không trỏ tới một đối tượng nào.
- `int[] data = {1, 2};`: `data` là tham chiếu, còn hai phần tử là các giá trị `int` được lưu trong đối tượng mảng.

## 2. Các primitive: ý nghĩa và phạm vi giá trị

| Kiểu Java | Ý nghĩa và kích thước giá trị theo đặc tả | Ghi chú |
| --- | --- | --- |
| `boolean` | Hai giá trị `false` và `true` | Java không cho chuyển trực tiếp qua lại với `int`; không mặc định “true là số 1” trong chương trình nguồn |
| `byte` | Số nguyên có dấu, 8 bit (`-128` đến `127`) | Phép toán số học thường được **promote lên `int`** |
| `short` | Số nguyên có dấu, 16 bit (`-32768` đến `32767`) | Phép toán `short + short` trả về `int` |
| `char` | Mã đơn vị **UTF-16**, không dấu, 16 bit (`0` đến `65535`) | Một `char` không nhất thiết biểu thị toàn bộ một ký tự Unicode ngoài BMP |
| `int` | Số nguyên có dấu, 32 bit | Tràn số nguyên được xử lý theo quy tắc Java, không tự biến thành kiểu rộng hơn |
| `long` | Số nguyên có dấu, 64 bit | Literal thường viết hậu tố `L` như `12L` |
| `float` | Số thực IEEE 754 binary32 | Có sai số làm tròn, giá trị đặc biệt NaN và vô cực |
| `double` | Số thực IEEE 754 binary64 | Có sai số làm tròn; mặc định của literal dấu phẩy động như `1.5` |

Các kích thước này mô tả **độ rộng và miền giá trị theo ngôn ngữ**, không có nghĩa mỗi biến luôn chiếm đúng từng ấy byte ở mọi nơi trong bộ nhớ máy. Cách JVM sắp xếp đối tượng, căn lề field, tối ưu biến hoặc bố trí CPU register phụ thuộc bản triển khai.

Ví dụ phép toán trên kiểu hẹp:

```java
byte a = 3;
byte b = 4;
int sum = a + b; // Kết quả biểu thức a + b có kiểu int
```

Dòng `byte sum = a + b;` không hợp lệ nếu thiếu ép kiểu vì phép cộng được **binary numeric promotion** thành `int`. Ngược lại, `byte x = 3 + 4;` có thể được `javac` chấp nhận nhờ quy tắc gán **hằng số nguyên biên dịch được** nằm trong phạm vi `byte`. Hai tình huống là hai quy tắc ngôn ngữ khác nhau.

## 3. JVM biểu diễn kiểu nào khi thực thi bytecode?

Trong JVM Specification, các giá trị JVM thường được phân thành nhóm số (numeric) và nhóm tham chiếu (reference). Numeric gồm `byte`, `short`, `int`, `long`, `char`, `float`, `double`; `boolean` không có một tập lệnh tính toán độc lập như `int`. JVM hỗ trợ `boolean` ở mức hạn chế, thường dùng biểu diễn và hướng dẫn liên quan `int` khi mã Java `boolean` được biên dịch.

Hai loại **category** rất quan trọng khi xem bytecode:

| JVM value | Category trên operand stack | Số local-variable slots |
| --- | ---: | ---: |
| `int`, `float`, `reference` và các giá trị narrow integral khi thao tác ở dạng `int` | 1 | 1 |
| `long`, `double` | 2 | 2 |
| `returnAddress` của cơ chế `jsr`/`ret` lịch sử | 1 | 1 |

**Category 1/2 là quy ước kích thước trong mô hình JVM**, không đồng nghĩa mọi reference vật lý đều có độ rộng bằng `int`. Những triển khai như HotSpot có thể có biểu diễn reference và tối ưu bộ nhớ khác nhau; địa chỉ máy và kỹ thuật compressed references là chi tiết triển khai.

Ở mức bytecode, `byte`, `short`, `char` thường được đẩy lên operand stack và xử lý như **`int`**. Chẳng hạn cộng hai `byte` có thể dùng `iadd` sau khi đã lấy các giá trị lên stack; khi cần ghi lại vào kiểu hẹp, compiler có thể dùng phép chuyển đổi như `i2b`, `i2s` hoặc `i2c`. Các lệnh tải/lưu array vẫn phân biệt element type phù hợp, ví dụ `baload`, `saload`, `caload`.

**Về `boolean`:** mã Java điều kiện thường dùng `iconst_0`/`iconst_1` cùng lệnh rẽ nhánh kiểu `if*`; JVM định nghĩa cách hỗ trợ `boolean` và mảng `boolean[]` chứ không bảo đảm một bytecode số học `boolean` riêng. Không được từ đó suy ra Java cho phép `if (1)` hoặc phép cộng giữa các `boolean`: **trình biên dịch Java bác bỏ các biểu thức đó**.

**`returnAddress`** là loại giá trị đặc biệt gắn với lệnh `jsr`/`ret` của class file cũ, không phải một kiểu mà mã Java thông thường có thể khai báo, và không nên dùng trong code Java hiện đại. `void` là kiểu kết quả “không có giá trị trả về”, không phải một giá trị được đặt vào operand stack.

## 4. Local variables, operand stack và đối tượng khác nhau thế nào?

Một phương thức Java khi chạy trong JVM có **stack frame** của riêng lượt gọi. Frame chứa:

- **Local variable array**: các slot được truy cập theo chỉ số cho tham số và biến cục bộ; giá trị category 2 chiếm hai slot liên tiếp trong mô hình.
- **Operand stack**: nơi bytecode đẩy, lấy và phối hợp các giá trị trung gian khi thực hiện phép tính, gọi phương thức, nhảy điều kiện.
- **Thông tin phụ trợ** cho việc trả về/kiểm soát luồng gọi và tham chiếu runtime liên quan.

Ví dụ khái niệm cho phương thức tĩnh:

```java
static int add(int left, int right) {
    return left + right;
}
```

Bytecode do `javac` thường biểu diễn phép cộng bằng các lệnh tương đương:

```text
iload_0     // đẩy giá trị int từ local slot 0
iload_1     // đẩy giá trị int từ local slot 1
iadd        // lấy hai int trên operand stack, đẩy kết quả int
ireturn     // lấy giá trị int trên stack để trả về
```

Đây là đoạn **minh họa bytecode**, không phải chương trình độc lập cần biên dịch; thứ tự và lựa chọn opcode cụ thể nên được kiểm tra bằng `javap -c` trên class file thực tế.

Với phương thức instance, local slot `0` thường chứa tham chiếu `this`; tham số xuất hiện ở các slot kế tiếp. Với phương thức `static`, không có `this` tự động. Một tham số `long` hoặc `double` dùng **hai slot**, nên chỉ số của tham số tiếp theo có thể nhảy qua một vị trí.

**Đừng hiểu “primitive luôn ở stack, object luôn ở heap” thành quy tắc tuyệt đối.** Biến cục bộ có thể chứa giá trị primitive hoặc reference; field bên trong object có thể có kiểu primitive hoặc reference; mảng có thể chứa primitive elements hay references. Trên bình diện mô hình JVM, đối tượng và mảng là thực thể heap; nhưng JVM tối ưu hiện đại có thể loại bỏ allocation hoặc biểu diễn dữ liệu khác đi khi giữ nguyên hành vi quan sát được.

```text
Stack frame của main() (mô hình logic)       Heap (mô hình logic)
┌───────────────────────────────┐           ┌──────────────────────┐
│ int count = 7                 │           │ Item instance        │
│ Item first ───────────────────┼──────────►│ int value = 9        │
│ Item alias ───────────────────┼──────────►│ boolean active=false │
│                               │           │ String label=null    │
└───────────────────────────────┘           └──────────────────────┘

first và alias giữ hai BẢN SAO giá trị tham chiếu,
nhưng đều truy cập CÙNG một đối tượng Item.
```

Sơ đồ chỉ giải thích quan hệ quan sát được, không phải lời khẳng định JVM luôn đặt từng biến vào một ô RAM vật lý riêng hay reference có hình dạng con trỏ máy cố định.

## 5. Ví dụ Java 17+ chạy được: sao chép giá trị và chia sẻ đối tượng

Tạo tệp **`JvmValueKinds.java`** trong một thư mục trống:

```java
public class JvmValueKinds {
    static class Item {
        int value;
        boolean active;
        String label;
    }

    public static int add(int a, int b) {
        return a + b;
    }

    public static void main(String[] args) {
        int original = 7;
        int copied = original;
        copied = 10;

        Item first = new Item();
        first.value = 7;
        Item alias = first;

        Item another = new Item();
        another.value = 7;
        alias.value = 10;

        Item fresh = new Item();

        byte left = 3;
        byte right = 4;
        int promotedSum = add(left, right);

        int[] numbers = {1, 2};
        int[] shared = numbers;
        shared[0] = 99;

        Integer boxed = 42;
        int unboxed = boxed;

        System.out.println("primitive: original=" + original
                + ", copied=" + copied);
        System.out.println("reference: first.value=" + first.value);
        System.out.println("same object: " + (first == alias));
        System.out.println("different objects: " + (first == another));
        System.out.println("defaults: " + fresh.value
                + ", " + fresh.active + ", " + fresh.label);
        System.out.println("byte + byte -> int: " + promotedSum);
        System.out.println("array alias: " + numbers[0]);
        System.out.println("boxed/unboxed: " + unboxed);
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac -encoding UTF-8 JvmValueKinds.java
java JvmValueKinds
```

**Standard output chính xác:**

```text
primitive: original=7, copied=10
reference: first.value=10
same object: true
different objects: false
defaults: 0, false, null
byte + byte -> int: 7
array alias: 99
boxed/unboxed: 42
```

Giải thích từng quan sát:

1. **Primitive:** `copied = original` tạo bản sao giá trị `7`. Đặt `copied = 10` không làm `original` thay đổi.
2. **Reference:** `alias = first` sao chép giá trị tham chiếu tới cùng `Item`. `alias.value = 10` sửa **trường của đối tượng**, nên `first.value` cũng đọc ra `10`.
3. **Identity:** `first == alias` là `true` vì hai reference chỉ cùng một object; `first == another` là `false` vì hai lần `new Item()` tạo hai đối tượng riêng, dù field `value` từng có cùng giá trị `7`.
4. **Giá trị mặc định:** instance fields `int`, `boolean`, `String` chưa gán tường minh lần lượt có `0`, `false`, `null`. Quy tắc này **không áp dụng cho local variables** chưa được gán trước khi đọc: trình biên dịch buộc local variables phải được khởi tạo chắc chắn.
5. **Numeric promotion:** `left` và `right` là `byte`; chúng được chuyển đổi theo lời gọi `add(int, int)`, kết quả `int` là `7`. Phép cộng trong `add` là phép cộng `int`.
6. **Array reference:** `shared = numbers` sao chép tham chiếu, nên sửa `shared[0]` thay đổi phần tử quan sát từ `numbers[0]`.
7. **Boxing:** `Integer boxed = 42` thực hiện autoboxing; `int unboxed = boxed` thực hiện unboxing. `Integer` là object reference type; `int` là primitive. Compiler có thể dùng các lời gọi như `Integer.valueOf` và `Integer.intValue`.

Lưu ý trong ví dụ, phép gán `alias = first` **không tạo ra một `Item` mới**; chỉ `new Item()` yêu cầu tạo đối tượng theo ngữ nghĩa chương trình.

### Tự nhìn vào bytecode

```bash
javap -c JvmValueKinds
javap -c -p JvmValueKinds
javap -verbose JvmValueKinds
```

- `javap -c` in bytecode của các phương thức công khai thông thường; `-p` cho phép xem thêm thành phần không public.
- Trong `add(int, int)`, quan sát `iload`, `iadd`, `ireturn`. Đó là các lệnh **`int`**, dù đầu vào `left` và `right` ở `main` có kiểu `byte`.
- Với `new Item()`, compiler thường tạo nhóm `new`, `dup`, `invokespecial` để cấp phát đối tượng và gọi constructor. Việc truy cập `value` dùng `getfield`/`putfield`.
- Đối với `new int[]` hay array initializer, quan sát các lệnh liên quan mảng như `newarray`, `iastore`, `iaload`.
- Các lệnh cho phép in chuỗi có thể dùng `invokedynamic` để nối chuỗi trên JDK hiện đại; chi tiết chỉ số constant pool và offset phụ thuộc bản compiler.

**Không nhầm bytecode của `javap` với mã máy CPU.** Khi JVM thực thi, interpreter hoặc JIT xử lý bytecode phù hợp với nền tảng hiện tại.

## 6. Descriptor trong class file nói gì về kiểu?

JVM class file dùng **type descriptor** để ghi kiểu của field và chữ ký phương thức ở mức bytecode. Các ký hiệu primitive:

| Kiểu Java | Ký hiệu trong field descriptor |
| --- | --- |
| `boolean` | `Z` |
| `byte` | `B` |
| `short` | `S` |
| `char` | `C` |
| `int` | `I` |
| `long` | `J` |
| `float` | `F` |
| `double` | `D` |

Tham chiếu tới class thường có dạng **`Lten/package/Class;`**. Kiểu array có tiền tố **`[`**; nhiều dấu `[` biểu thị nhiều chiều. `V` nghĩa là `void` **trong phần kiểu trả về của method descriptor**, không phải field type.

| Khai báo Java | JVM descriptor |
| --- | --- |
| `int number` | `I` |
| `String text` | `Ljava/lang/String;` |
| `int[] values` | `[I` |
| `String[][] names` | `[[Ljava/lang/String;` |
| `static int add(int a, int b)` | `(II)I` |
| `void use(String[] args)` | `([Ljava/lang/String;)V` |

Method descriptor có cấu trúc **`(parameter-descriptors)return-descriptor`**. Dấu gạch chéo trong `java/lang/String` là tên nội bộ của class file, khác cách viết tên Java `java.lang.String`.

Generic như `List<String>` có thêm thông tin khai báo/metadata tương ứng khi compiler lưu, nhưng phần descriptor cơ bản hoạt động trên kiểu sau **erasure**; đừng mong bytecode luôn bảo toàn đầy đủ mọi tham số generic của ngôn ngữ nguồn. Kiểm tra `javap -verbose` để quan sát descriptor và, khi có, thuộc tính `Signature`.

## 7. Bytecode chọn instruction theo loại giá trị

Opcode của JVM thường có tiền tố gợi ý nhóm kiểu:

| Nhóm thao tác | Với `int` | Với `long` | Với `float`/`double` | Với reference |
| --- | --- | --- | --- | --- |
| Load local variable | `iload` | `lload` | `fload`/`dload` | `aload` |
| Store local variable | `istore` | `lstore` | `fstore`/`dstore` | `astore` |
| Return value | `ireturn` | `lreturn` | `freturn`/`dreturn` | `areturn` |
| Cộng số | `iadd` | `ladd` | `fadd`/`dadd` | Không có lệnh cộng hai reference |

Các phép cộng với `byte`, `short`, `char` thường được xử lý trong miền `int` theo numeric promotion. `boolean` cũng không được xem là số nguyên của ngôn ngữ Java để dùng `iadd` trong mã Java hợp lệ.

Một số lệnh đặc thù reference:

- `new`: tạo instance của class.
- `getfield`/`putfield`: đọc/ghi instance field; `getstatic`/`putstatic` phục vụ static field.
- `invokevirtual`, `invokeinterface`, `invokespecial`, `invokestatic`: các cơ chế gọi method; không đơn thuần là “nhảy tới một con trỏ”.
- `checkcast` và `instanceof`: kiểm tra quan hệ kiểu tham chiếu thích hợp ở runtime.
- `ifnull`/`ifnonnull`, `if_acmpeq`/`if_acmpne`: rẽ nhánh liên quan null hoặc so sánh identity reference.
- `aaload`/`aastore`: đọc/ghi phần tử mảng reference, trái với `iaload`/`iastore` cho `int[]`.

**Bytecode verifier** dùng loại giá trị và trạng thái của local variables/operand stack để bác bỏ những bytecode không thỏa mãn luật kiểu và cấu trúc. Ví dụ không thể hợp lệ nếu đẩy reference rồi dùng `iadd` như với `int`. Verifier còn theo dõi trạng thái đối tượng chưa khởi tạo và các ràng buộc luồng điều khiển; việc Java source biên dịch được không cho phép một công cụ bytecode tự do sinh mã sai kiểu.

## 8. Tham chiếu không phải chính đối tượng

Cần phân biệt ba thao tác dưới đây:

```java
Item a = new Item(); // tạo object mới, a giữ reference
Item b = a;          // sao chép reference: cùng object
b = new Item();      // b nhận reference mới, a vẫn tới object cũ
```

Hai biến `a` và `b` là hai **vị trí lưu giá trị tham chiếu** riêng theo mô hình biến. Khi chỉ gán `b = a`, đối tượng không được sao chép; khi gán lại `b = new Item()`, quan hệ alias bị thay đổi mà không tự sửa `a`.

Khi gọi phương thức:

```java
static void replace(Item parameter) {
    parameter = new Item();
}

static void mutate(Item parameter) {
    parameter.value = 99;
}
```

Cả hai đoạn trên là **hàm phụ trợ minh họa** và cần nằm trong class có khai báo `Item` tương thích mới biên dịch; chúng không tạo thành chương trình chạy độc lập.

- `replace(a)` chỉ thay **bản sao giá trị reference trong tham số `parameter`**, không chuyển `a` sang object mới.
- `mutate(a)` sửa **field của object mà tham số và `a` cùng tham chiếu**, nên caller có thể quan sát `a.value == 99`.

Do đó Java **luôn truyền đối số theo giá trị**: với primitive là bản sao giá trị primitive, với reference là bản sao giá trị reference. Cụm từ “Java truyền object theo tham chiếu” dễ làm người học kết luận sai rằng có thể gán lại biến tham chiếu của caller từ một parameter.

### `==`, `equals` và các wrapper

Với primitive số, `==` so sánh giá trị theo quy tắc numeric conversion của Java; với hai reference, `==` kiểm tra **cùng identity hoặc cùng null**. So sánh **nội dung mang ý nghĩa nghiệp vụ** của hai object cần một phương thức thích hợp như `equals` nếu class cung cấp hợp đồng so sánh đó.

Ví dụ:

```java
String left = new String("abc");
String right = new String("abc");
System.out.println(left == right);      // false (hai object mới)
System.out.println(left.equals(right)); // true  (nội dung bằng nhau)
```

Đây là **đoạn minh họa đặt trong `main`**, không phải một class hoàn chỉnh. Không dùng `==` để so sánh nội dung `String`: các string literal có thể được intern nên cho kết quả identity trùng trong vài tình huống, làm lỗi bị che giấu.

Autoboxing biến primitive thành giá trị wrapper reference như `Integer`; unboxing lấy giá trị primitive từ wrapper. Nếu biến wrapper là `null` thì **unboxing ném `NullPointerException`**. Không dựa vào `==` giữa hai wrapper để so giá trị vì **identity/caching** có thể tạo kết quả không như trực giác; dùng `equals` hoặc unbox an toàn khi muốn so nội dung.

## 9. Field, local variable, array: cùng kiểu nhưng quy tắc khởi tạo khác

| Vị trí | Giá trị trước gán tường minh | Ghi chú |
| --- | --- | --- |
| Instance field / static field | Có default value như `0`, `false`, `null` | Khởi tạo theo quy tắc JVM; constructor/static initialization có thể gán tiếp |
| Phần tử mảng mới | Có default value của element type | Ví dụ `new int[2]` chứa `0, 0`; `new String[2]` chứa `null, null` |
| Local variable trong phương thức Java | **Không thể đọc trước khi được gán chắc chắn** | Quy tắc **definite assignment** của `javac`; không coi là có default `0`/`null` |

Ví dụ:

```java
int[] numbers = new int[2];       // numbers tham chiếu mảng; phần tử là int
String[] names = new String[2];  // names tham chiếu mảng; phần tử là reference
System.out.println(numbers[0]); // 0
System.out.println(names[0]);   // null
```

Đoạn trên hợp lệ khi nằm trong một `main` hoặc phương thức thích hợp. Các biến `numbers` và `names` ở ví dụ đều được gán bởi `new` trước khi dùng.

Với mảng reference, JVM còn kiểm tra tính tương thích kiểu phần tử tại runtime (vì Java arrays có tính **covariant**):

```java
Object[] values = new String[1];
values[0] = "ok";            // hợp lệ
// values[0] = new Object(); // nếu bỏ comment: ArrayStoreException tại runtime
```

Type check của mảng khác với việc copy reference và khác với lỗi compilation. `int[]` không thể chuyển trực tiếp thành `Object[]`; dù cả hai đều là object array theo nghĩa Java object, `int[]` có phần tử primitive.

## 10. `null`, truy cập đối tượng và garbage collection

`null` là một **giá trị reference đặc biệt**, không có object tương ứng. Truy cập instance field hoặc gọi instance method qua reference `null` thường dẫn tới `NullPointerException` tại runtime:

```java
String text = null;
// System.out.println(text.length()); // nếu bỏ comment: NullPointerException
System.out.println(text == null); // true
```

`null` không phải `0` và không thể gán vào `int`. Lệnh `instanceof` khi toán hạng là `null` cho kết quả `false`, nhưng không có nghĩa mọi phép thao tác khác với `null` đều an toàn.

**Reachability** giúp GC xác định object còn có thể được chương trình dùng từ những nguồn tham chiếu mà collector xét. Khi không còn đường tham chiếu có liên quan tới object, object có thể **đủ điều kiện được thu gom**, nhưng **không có cam kết bị thu gom ngay lập tức**. Gán `reference = null` không tương đương việc “xóa object khỏi heap” ngay lúc đó, và không thể dựa vào `System.gc()` để bảo đảm thời điểm thu hồi.

Không tồn tại phép toán pointer arithmetic trên reference trong Java source như C/C++; đặc tả cũng không cho phép giả định biểu diễn reference có số byte cố định để cộng/trừ địa chỉ.

## 11. Các hiểu nhầm phổ biến khi học JVM types

| Nhận định dễ nhầm | Cách hiểu đúng |
| --- | --- |
| “Primitive luôn nằm ở stack; reference luôn ở heap.” | Biến/field/mảng có thể lưu primitive hoặc reference; **object** là thực thể heap trong mô hình, còn bố trí vật lý có thể được JVM tối ưu |
| “Gán object `b = a` là deep copy object.” | Gán sao chép **giá trị reference**; hai biến có thể dùng chung object |
| “Tham số class được truyền by reference.” | Java **pass by value** kể cả với tham chiếu tới object |
| “`long` chiếm hai slot nghĩa là reference cũng luôn là 4 byte.” | Category và slot là **mô hình JVM**, không suy ra kích thước pointer vật lý |
| “Có 8 primitive Java nên JVM phải có 8 opcode cộng khác nhau.” | JVM dùng `int` cho nhiều thao tác trên kiểu narrow; `boolean` không có phép cộng Java |
| “Array luôn là tham chiếu tới một mảng các object.” | Mảng là object; **element type** có thể là primitive (`int[]`) hoặc reference (`String[]`) |
| “Field và local variable chưa gán đều bằng `0`.” | Field/array element có default; local variable Java phải được gán chắc chắn trước khi đọc |
| “`Integer` giống hệt `int`.” | `Integer` là reference wrapper, có thể `null` và có identity; unboxing có thể lỗi |
| “`==` luôn so nội dung object.” | Với reference, `==` so identity; `equals` có thể so nội dung tùy hợp đồng class |

## 12. Tự kiểm tra mô hình thực thi

**Câu 1.** `int[] a = new int[3];` thì `a` thuộc primitive hay reference?  
**Trả lời:** `a` có kiểu **reference**, trỏ tới một đối tượng mảng. Mỗi phần tử `a[i]` mang giá trị primitive `int`.

**Câu 2.** Vì sao `byte a = 1, b = 2; byte c = a + b;` không biên dịch được?  
**Trả lời:** Phép cộng áp dụng numeric promotion tạo biểu thức có kiểu `int`, nên cần nơi nhận `int` hoặc chuyển đổi tường minh thích hợp; không tự gán biểu thức `int` không phải hằng số cho `byte`.

**Câu 3.** `long` và `double` chiếm mấy local slots trong frame?  
**Trả lời:** Mỗi giá trị chiếm **hai slots** theo mô hình JVM; điều này không xác định chính xác bố trí RAM cuối cùng.

**Câu 4.** Hai reference trỏ cùng object; đổi field qua reference A, reference B thấy gì?  
**Trả lời:** B có thể quan sát giá trị field mới của **cùng object**, theo các quy tắc về đồng bộ và memory visibility nếu có nhiều thread. Trong một luồng thực thi thông thường, thay đổi field quan sát được ngay qua alias đó.

**Câu 5.** Vì sao có thể `getfield` cho reference nhưng không được `iadd` hai reference?  
**Trả lời:** Bytecode verifier và mô hình kiểu phân biệt reference với numeric operands. `iadd` yêu cầu hai giá trị `int`, trong khi `getfield` thao tác trên object reference phù hợp với field được chỉ định.

**Câu 6.** Có thể xác định một biến tham chiếu luôn chiếm đúng 8 byte vì ứng dụng chạy trên JVM 64-bit?  
**Trả lời:** **Không.** Cách biểu diễn và tối ưu tham chiếu phụ thuộc JVM/cấu hình; descriptor và operand-stack category không mã hóa chiều rộng địa chỉ native cố định.

**Câu 7.** Vì sao `Integer number = null; int x = number;` thất bại dù compiler chấp nhận?  
**Trả lời:** `Integer` là kiểu tham chiếu có thể bằng `null`; phép **unboxing tại runtime** cố lấy giá trị `int` từ `null` nên ném `NullPointerException`.

Tóm lại, **giá trị primitive** được copy theo nội dung, còn **giá trị reference** được copy như một đầu mối truy cập object. JVM phản ánh phân biệt đó bằng **type descriptor, instruction family, local-variable slots, operand stack và bytecode verification**; việc thực sự bố trí bộ nhớ thuộc trách nhiệm runtime/JIT/GC. Hiểu các tầng này sẽ giúp đọc `javap`, chẩn đoán `NullPointerException`, tránh nhầm pass-by-value và giải thích chính xác hành vi của các phép gán, mảng, field và method call.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-data-types-002">Kiểu int dùng cho nhiều phép toán trên byte, short, char và boolean</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu int dùng cho nhiều phép toán trên byte, short, char và boolean

## 1. Mục tiêu bài học

Java có các kiểu nguyên thủy **byte**, **short**, **char**, **boolean** và **int**. Chúng khác nhau về giá trị hợp lệ, quy tắc kiểu trong mã nguồn và chữ ký phương thức. Tuy vậy, trong tập lệnh của Java Virtual Machine (JVM), phần lớn thao tác với bốn kiểu đầu được thực hiện thông qua **kiểu tính toán (computational type) int**.

Sau khi học, bạn có thể:

- Giải thích vì sao bytecode dùng các lệnh như **iload**, **istore**, **iadd**, **ifeq** và **ireturn** cho giá trị xuất phát từ **byte**, **short**, **char**, đôi khi cả **boolean**.
- Phân biệt **kiểu Java ở mã nguồn**, **kiểu khai báo/lưu trữ trong class file** và **kiểu tính toán trên operand stack**.
- Phân biệt **mở rộng dấu (sign extension)** của **byte/short** với **mở rộng bằng số 0 (zero extension)** của **char/boolean**.
- Hiểu numeric promotion, ép kiểu thu hẹp bằng **i2b/i2s/i2c**, và lý do một biểu thức cộng hai biến **byte** cho kết quả kiểu **int**.
- Giải thích tại sao dùng **int** để biểu diễn giá trị boolean trong JVM không cho phép cộng hoặc ép kiểu giữa **boolean** và **int** trong Java.
- Kiểm tra bytecode bằng **javap** và dự đoán chính xác kết quả của chương trình Java 17+.

Bài học bàn về mô hình được **đặc tả JVM** định nghĩa; không suy ra rằng JVM bắt buộc dùng bốn byte bộ nhớ vật lý cho từng biến boolean/byte/short/char khi chạy.

## 2. Hai tầng kiểu dữ liệu phải được phân biệt

**Tầng 1 — Ngôn ngữ Java (source-level types):**

| Kiểu | Miền giá trị | Đặc điểm |
| --- | --- | --- |
| **byte** | -128 đến 127 | Số nguyên có dấu 8 bit |
| **short** | -32768 đến 32767 | Số nguyên có dấu 16 bit |
| **char** | 0 đến 65535 | Đơn vị mã UTF-16 không dấu 16 bit; một **char** không nhất thiết là một ký tự Unicode hoàn chỉnh |
| **boolean** | **false**, **true** | Giá trị logic, không phải kiểu số trong Java |
| **int** | -2147483648 đến 2147483647 | Số nguyên có dấu 32 bit |

Ở tầng Java, những kiểu này **không hoán đổi tùy ý**. Chẳng hạn, phép cộng giữa hai biến **byte** cần dùng quy tắc numeric promotion; còn **boolean** không được chuyển thành số chỉ vì JVM có thể dùng một giá trị tính toán kiểu **int** để xử lý nó.

**Tầng 2 — Tập lệnh và frame của JVM:**

Một frame khi gọi phương thức có **bảng biến cục bộ (local variables)** và **ngăn xếp toán hạng (operand stack)**. JVM định nghĩa tập lệnh có chủ đích: hỗ trợ trực tiếp những nhóm toán hạng tính toán chính, thay vì tạo một phiên bản phép cộng hay load/store cho mọi kiểu nguồn.

Theo bảng ánh xạ kiểu tính toán trong JVM:

| Kiểu thực tế (actual type) | Kiểu tính toán (computational type) trên JVM | Category |
| --- | --- | --- |
| **boolean** | **int** | 1 |
| **byte** | **int** | 1 |
| **short** | **int** | 1 |
| **char** | **int** | 1 |
| **int** | **int** | 1 |
| **long** | **long** | 2 |
| **float** | **float** | 1 |
| **double** | **double** | 2 |

**Category 1** dùng một đơn vị slot trong mô hình JVM cho các lệnh xử lý operand stack; **long/double** thuộc **category 2**. Đây là khái niệm của máy ảo và bộ xác minh, **không phải** cam kết mỗi giá trị category 1 luôn chiếm bốn byte RAM trong một object hay thanh ghi máy thật.

Sơ đồ ý tưởng:

~~~text
Java source:
    byte a, b;
    byte c = (byte) (a + b);
               |
               v
      javac kiểm tra kiểu Java
               |
               v
JVM operand stack:
    iload a     -> một giá trị tính toán kiểu int
    iload b     -> một giá trị tính toán kiểu int
    iadd        -> kết quả kiểu int
    i2b         -> thu hẹp thành byte và sign-extend lại thành int
    istore c    -> lưu giá trị tính toán vào local slot
~~~

Khi mã truy cập **field**, mảng hoặc truyền/nhận phương thức, mô tả kiểu thật vẫn tồn tại; không có nghĩa tất cả mọi nơi đã đổi tên thành **int**.

## 3. Vì sao JVM dùng các lệnh int cho nhiều kiểu?

Mỗi opcode JVM có độ dài mã lệnh cơ bản là một byte. Nếu tất cả phép cộng, trừ, nhân, load, store, so sánh, trả về... đều cần opcode riêng cho từng kiểu nguyên thủy, tập lệnh sẽ phình to. JVM dùng tập lệnh không hoàn toàn đối xứng giữa các kiểu, và compiler thêm chuyển đổi ở điểm cần giữ nguyên ngữ nghĩa Java.

Ví dụ các lệnh thường gặp:

| Công việc | Lệnh xử lý qua **int** | Ý nghĩa |
| --- | --- | --- |
| Đưa local variable lên stack | **iload**, **iload_0**...**iload_3** | Đọc giá trị tính toán kiểu **int** từ slot |
| Ghi local variable từ stack | **istore**, **istore_0**...**istore_3** | Ghi giá trị tính toán **int** vào slot |
| Cộng, trừ, nhân | **iadd**, **isub**, **imul** | Toán học **int** |
| Chia, phần dư | **idiv**, **irem** | Toán học **int**; chia cho 0 gây **ArithmeticException** |
| AND, OR, XOR trên bit | **iand**, **ior**, **ixor** | Xử lý toán hạng tính toán **int** |
| Dịch bit | **ishl**, **ishr**, **iushr** | Phép dịch trên **int** |
| Nhánh | **ifeq**, **ifne**, **if_icmpeq**... | Kiểm tra giá trị **int**, đôi khi xuất phát từ boolean |
| Trả về | **ireturn** | Dùng cho phương thức khai báo trả **boolean**, **byte**, **short**, **char** hoặc **int** |
| Thu hẹp về kiểu nhỏ | **i2b**, **i2s**, **i2c** | Chuyển kết quả **int** thành giá trị mang ngữ nghĩa byte/short/char |

Không có lệnh **badd**, **sadd**, **cadd** hay **zadd** để cộng trực tiếp những kiểu đó. Không có **i2z** để biến một số thành boolean. Đây là dấu hiệu rõ của sự khác biệt giữa mô hình tính toán JVM và hệ thống kiểu Java.

Khi xem bytecode với **javap -c**, chữ **i** trong **iadd** hay **iload** chỉ **kiểu tính toán của instruction**, không chứng minh biến khai báo ở mã nguồn nhất thiết là **int**.

## 4. Mở rộng dấu và mở rộng bằng số 0

Khi một giá trị nhỏ được đưa lên operand stack, nó thường ở dạng computational **int**, nhưng phải giữ đúng ý nghĩa dấu:

- **byte** có dấu 8 bit và **short** có dấu 16 bit: dùng **sign extension**, sao chép bit dấu khi mở rộng lên 32 bit.
- **char** không dấu 16 bit: dùng **zero extension**, thêm các bit 0 ở phía cao.
- **boolean**: giá trị logic được biểu diễn qua computational **int**; compiler Java sử dụng cách mã hóa **false = 0**, **true = 1** và mô hình mở rộng bằng 0. Không được suy ra giá trị boolean là một số trong ngôn ngữ Java.

Ví dụ biểu diễn bit:

~~~text
byte -1:
    11111111             (8 bit)
          |
          v sign extension
    11111111 11111111 11111111 11111111   -> int -1

short -1:
    11111111 11111111    (16 bit)
          |
          v sign extension
    11111111 11111111 11111111 11111111   -> int -1

char '\uFFFF':
    11111111 11111111    (16 bit)
          |
          v zero extension
    00000000 00000000 11111111 11111111   -> int 65535
~~~

Điều này giải thích vì sao cùng mẫu bit thấp **FFFF**, **short** diễn giải là **-1** còn **char** diễn giải là **65535**. Phép toán bằng opcode **int** chỉ đúng khi giá trị đưa vào đã được mở rộng phù hợp.

### 4.1. Lệnh đọc mảng thể hiện rõ sự khác biệt

Mảng vẫn giữ kiểu phần tử; JVM có lệnh nạp phần tử riêng:

| Loại mảng | Lệnh nạp | Giá trị được đẩy lên operand stack |
| --- | --- | --- |
| **byte[]** | **baload** | Giá trị kiểu tính toán **int**, sign-extend từ byte |
| **short[]** | **saload** | Giá trị kiểu tính toán **int**, sign-extend từ short |
| **char[]** | **caload** | Giá trị kiểu tính toán **int**, zero-extend từ char |
| **boolean[]** | **baload** | Giá trị logic dạng computational **int**; JVM tái sử dụng lệnh này cho boolean array |
| **int[]** | **iaload** | Giá trị **int** |

Khi ghi về mảng, các lệnh tương ứng gồm **bastore** (byte/boolean), **sastore**, **castore** và **iastore**. Việc chia sẻ opcode **baload/bastore** không làm **boolean[]** và **byte[]** trở thành cùng một kiểu; Java vẫn kiểm tra kiểu tại compile time và runtime.

**Không suy diễn cấu trúc bộ nhớ:** JVM specification cho phép những chi tiết triển khai về biểu diễn của boolean; bảng opcode không chứng minh rằng boolean field hoặc phần tử mảng boolean bắt buộc chiếm một byte hay một int vật lý trên mọi JVM.

## 5. Numeric promotion: tại sao byte + byte lại ra int?

Quy tắc của **ngôn ngữ Java** gọi là **binary numeric promotion**: với nhiều toán tử số học như **+**, **-**, **\***, **/**, **%**, những toán hạng nhỏ hơn **int** (byte, short, char) được nâng lên ít nhất **int** trước khi thực hiện phép toán.

Ví dụ:

~~~java
// Mã minh họa bên trong một phương thức:
byte a = 10;
byte b = 20;

int sum = a + b;             // Hợp lệ: biểu thức a + b có kiểu int
byte narrow = (byte) (a + b); // Hợp lệ: ép kết quả về byte
~~~

Dòng dưới **không biên dịch**:

~~~java
byte a = 10;
byte b = 20;
byte invalid = a + b; // Compile-time error: int không tự thu hẹp về byte
~~~

Compiler không dựa vào việc hai giá trị tình cờ nhỏ để chấp nhận phép gán này, bởi **a** và **b** là biến và giá trị runtime có thể vượt giới hạn kiểu **byte**. JVM dùng **iadd** cho phép cộng; kiểm tra kiểu nguồn được thực hiện từ trước bởi **javac**.

Tương tự:

~~~java
short s = 300;
char c = 'A';

int x = s + 1; // 301
int y = c + 1; // 66, vì 'A' có giá trị mã UTF-16 là 65
~~~

**short + short**, **char + char**, **byte + char** cũng tạo **int** trong những trường hợp chỉ dùng các toán hạng nhỏ này. Nếu có **long**, **float** hoặc **double**, quy tắc promotion tiếp tục theo thứ tự tương ứng; không nên nói mọi phép số học trong JVM đều thành **int**.

Các toán tử **+** hoặc **-** một ngôi, **~**, và toán tử dịch bit cũng có quy tắc promotion riêng. Ví dụ **~(byte) 0** cho kết quả kiểu **int** với giá trị **-1**; **(byte) 1 << 8** có kiểu **int** với giá trị **256**.

### 5.1. Ngoại lệ có kiểm soát: hằng biểu thức

Java cho phép gán một số **biểu thức hằng có kiểu int** cho **byte/short/char** nếu giá trị được biết lúc biên dịch và nằm trong phạm vi đích.

~~~java
byte a = 10 + 20;       // Hợp lệ: hằng int 30 vừa byte
short s = 1000 + 2000;  // Hợp lệ: hằng int 3000 vừa short
char c = 65;            // Hợp lệ: hằng int 65 vừa char
final int code = 66;
char letter = code;     // Hợp lệ: code là constant variable kiểu int, giá trị vừa char
~~~

Ngược lại:

~~~java
byte tooLarge = 120 + 10; // Compile-time error: hằng 130 vượt giới hạn byte
int runtimeValue = 30;
byte cannotAssign = runtimeValue; // Compile-time error: biến int không được tự thu hẹp
~~~

Đây là quy tắc **gán hằng** của Java, không mâu thuẫn với việc phép cộng tạo biểu thức kiểu **int**. Đừng hiểu sai rằng javac luôn tự ép kiểu mọi biểu thức **int** về **byte** khi kết quả có vẻ nhỏ.

### 5.2. Phép gán kết hợp và ++ có thu hẹp ngầm

So sánh hai đoạn:

~~~java
byte b = 127;
// b = b + 1; // Không biên dịch: b + 1 là int
b += 1;       // Hợp lệ: gán kết hợp có bước thu hẹp về byte
~~~

**b += 1** có hiệu ứng giá trị giống **b = (byte) (b + 1)** khi vế trái là biến đơn giản, và vế trái của gán kết hợp chỉ được đánh giá một lần. Sau đó **b** có giá trị **-128** vì kết quả **128** bị thu hẹp về số có dấu 8 bit.

Với toán tử **b++** hoặc **b--**, Java cũng bảo đảm kết quả cuối được lưu về đúng kiểu biến; chúng không bắt người viết mã thêm cast tường minh.

**Lưu ý:** Quy tắc gán kết hợp và **++/--** dành cho các kiểu số; **boolean++**, **boolean += 1** hoặc **boolean + boolean** đều không hợp lệ trong Java.

## 6. Thu hẹp kết quả int: i2b, i2s, i2c

Khi tính toán qua **int** và cần trả kết quả về kiểu nhỏ, compiler có thể sinh các lệnh chuyển đổi:

| Lệnh | Hành vi khái quát | Ví dụ đầu vào **int** | Kết quả theo ngữ nghĩa kiểu đích |
| --- | --- | --- | --- |
| **i2b** | Giữ 8 bit thấp rồi diễn giải là byte có dấu; giá trị tính toán trên stack được sign-extend lại thành int | 130 | -126 |
| **i2s** | Giữ 16 bit thấp rồi diễn giải là short có dấu; sign-extend lại thành int | 32768 | -32768 |
| **i2c** | Giữ 16 bit thấp và diễn giải là char không dấu; zero-extend lại thành int | -1 | 65535 |

Vì vậy, **i2b** không phải lệnh biến operand stack thành “stack chứa ô byte 8 bit”. Operand stack tiếp tục làm việc với computational **int**, nhưng giá trị đã được chuẩn hóa theo kiểu nhỏ.

Ví dụ:

~~~text
130 ở dạng int
    |
    v i2b
8 bit thấp: 10000010
    |
    v diễn giải byte có dấu rồi sign-extend
-126 (một giá trị tính toán int)

-1 ở dạng int
    |
    v i2c
16 bit thấp: 11111111 11111111
    |
    v zero-extend
65535 (một giá trị tính toán int)
~~~

Khi không có yêu cầu thu hẹp từ **int** trở lại kiểu nhỏ, compiler không nhất thiết chèn **i2b/i2s/i2c** vào từng biểu thức. Cũng không phải cứ một biểu thức Java xuất hiện cast là bản bytecode của mọi compiler bắt buộc có một instruction chuyển đổi riêng; tối ưu hằng biểu thức có thể xảy ra ngay khi biên dịch.

## 7. Boolean là trường hợp đặc biệt quan trọng

JVM không có một bộ opcode đầy đủ riêng cho **boolean**. Trong mã do Java compiler tạo, **boolean** thường đi qua **int** để dùng lệnh nạp, lưu, kiểm tra nhánh hoặc trả về.

Ví dụ mã nguồn:

~~~java
// Mã minh họa bên trong một phương thức:
boolean ready = true;
boolean active = false;
boolean allowed = ready && active;
~~~

Compiler có thể dùng các lệnh nhánh trên computational **int** như **ifeq/ifne** để hiện thực **&&** theo kiểu đoản mạch (short circuit). Với **&**, **|**, **^** áp dụng cho hai toán hạng **boolean**, phép toán logic không đoản mạch vẫn hợp lệ; các lệnh như **iand/ior/ixor** có thể được dùng khi compiler tạo mã thích hợp.

**Cần phân biệt hai phát biểu:**

1. **Đúng ở tầng JVM:** các giá trị logic có thể được xử lý bằng computational **int**; đối với mã Java hợp lệ, giá trị boolean thông thường được mã hóa bằng 0 hoặc 1.
2. **Sai ở tầng Java:** boolean là số nguyên và có thể cộng, nhân, so sánh lớn hơn hoặc ép sang int. Java không cho phép **boolean value = 1**, **int x = true**, **(int) true**, **true + false** hay **true < false**.

**boolean** không tham gia binary numeric promotion của ngôn ngữ Java. Quy tắc mở rộng số của **byte/short/char** và cách JVM biểu diễn boolean ở runtime là **hai cơ chế khác nhau**.

Ngoài ra:

- **boolean** được phép dùng **!**, **&&**, **||**, **&**, **|**, **^**, **==**, **!=** và điều kiện của **if/while**.
- **&&/||** luôn giữ ngữ nghĩa đoản mạch trong Java. Việc JVM dùng nhánh **int** không cho phép bỏ qua quy tắc này.
- Một phương thức khai báo trả về **boolean** thường dùng **ireturn** như các kiểu nhỏ khác, nhưng chữ ký vẫn đánh dấu nó bằng descriptor **Z**, không phải **I**.
- JVM có **boolean[]** như một loại mảng riêng; thao tác truy cập phần tử chia sẻ instruction với **byte[]** nhưng không đồng nghĩa với phép chuyển kiểu giữa hai mảng.
- **boolean** là kiểu logic với hai giá trị theo Java; hành vi của bytecode tùy ý tự tạo và mã vi phạm các quy ước compiler không được dùng làm lý do suy ngược một phép chuyển đổi boolean ↔ int ở Java source.

## 8. Ví dụ Java 17+ chạy được và output chính xác

Tạo tệp **IntComputationalTypeDemo.java**:

~~~java
public class IntComputationalTypeDemo {
    static byte addBytes(byte a, byte b) {
        return (byte) (a + b);
    }

    static short addShorts(short a, short b) {
        return (short) (a + b);
    }

    static char nextChar(char value) {
        return (char) (value + 1);
    }

    static boolean both(boolean left, boolean right) {
        return left && right;
    }

    static int doubleByte(byte value) {
        return value * 2;
    }

    public static void main(String[] args) {
        byte first = 120;
        byte second = 10;

        System.out.println("sumAsInt=" + (first + second));
        System.out.println("sumAsByte=" + addBytes(first, second));

        short maxShort = 32767;
        System.out.println("shortWrap=" + addShorts(maxShort, (short) 1));

        System.out.println("nextChar=" + nextChar('A'));
        System.out.println("charPlusOneAsInt=" + ('A' + 1));

        System.out.println("both=" + both(true, false));
        System.out.println("doubleByte=" + doubleByte((byte) -2));
    }
}
~~~

Biên dịch và chạy bằng JDK 17 trở lên:

~~~bash
javac -encoding UTF-8 IntComputationalTypeDemo.java
java IntComputationalTypeDemo
~~~

**Stdout:**

~~~text
sumAsInt=130
sumAsByte=-126
shortWrap=-32768
nextChar=B
charPlusOneAsInt=66
both=false
doubleByte=-4
~~~

Phân tích từng trường hợp:

1. **first + second** thực hiện phép cộng kiểu **int**: 120 + 10 = **130**.
2. **addBytes** cũng cộng qua **int**, nhưng cast kết quả về **byte** giữ 8 bit thấp. Giá trị 130 trở thành **-126** khi diễn giải dưới dạng byte có dấu.
3. **32767 + 1** tính ra **32768** dưới dạng **int**; ép về **short** cho **-32768**.
4. **'A' + 1** có kiểu **int**, tương đương 65 + 1 = **66**. Trong **nextChar**, cast đưa kết quả về **char**, và **System.out.println** nhận giá trị **char** nên in ký tự **B**.
5. **both(true, false)** trả **false** đúng theo phép logic Java; không có phép cộng boolean.
6. **doubleByte((byte) -2)** mở rộng giá trị byte **-2** thành computational **int**, nhân 2 được **-4**, và trả về **int** mà không cần thu hẹp.

Ví dụ dùng các phép toán cố định, không phụ thuộc múi giờ, locale hoặc filesystem. **Stdout** ở trên là kết quả của chương trình, không phải toàn bộ output của trình biên dịch hay công cụ bytecode.

## 9. Kiểm chứng với javap: lời giải thích được thấy trong bytecode

Sau khi biên dịch, chạy:

~~~bash
javap -c -p IntComputationalTypeDemo
javap -s -p IntComputationalTypeDemo
~~~

Lệnh **javap -c** giải mã instructions, còn **javap -s** hiện method descriptors. Hai lệnh này dùng cho quan sát cấu trúc, không thay thế việc chạy chương trình để xác minh output.

### 9.1. Theo dấu addBytes

Với **javac** thông dụng, thân bytecode của phương thức **addBytes(byte, byte)** có dạng:

~~~text
iload_0
iload_1
iadd
i2b
ireturn
~~~

Có thể có tiền tố offset như **0:**, **1:** hoặc chi tiết hiển thị khác; dãy mnemonic này mới là điểm cần quan sát.

Giải thích:

- **iload_0** đưa tham số **a** từ local slot 0 lên stack dưới dạng computational **int**.
- **iload_1** làm tương tự với tham số **b**.
- **iadd** tiêu thụ hai toán hạng **int** và đẩy tổng **int** lên stack.
- **i2b** thu hẹp tổng theo quy tắc byte và đưa kết quả đã sign-extend trở lại dạng computational **int**.
- **ireturn** trả giá trị cho phương thức khai báo kiểu **byte**.

Với chương trình mẫu, compiler không thể thay toàn bộ phương thức thành một hằng cố định bởi tham số thay đổi theo lần gọi. Việc có **iadd** và **i2b** cho thấy tường minh hai giai đoạn tính toán và thu hẹp.

### 9.2. Theo dấu short, char và boolean

Trong **addShorts(short, short)**, chú ý các lệnh **iadd**, **i2s**, **ireturn**. Trong **nextChar(char)**, chú ý **iadd**, **i2c**, **ireturn**. Trong **doubleByte(byte)**, chú ý **imul** và **ireturn**; kiểu trả về là **int** nên không cần chuyển về **byte**.

Ở **both(boolean, boolean)**, compiler có thể tổ chức nhánh **ifeq** hoặc cách kiểm tra tương đương để giữ ngữ nghĩa **&&**: nếu **left** là false thì bỏ qua đánh giá **right** ở cấp ngôn ngữ. Với hai biến tham số thuần như ví dụ, output không cho thấy khác biệt về side effect; quy tắc đoản mạch vẫn phải được bảo toàn khi toán hạng là biểu thức có side effect.

### 9.3. Chữ ký vẫn giữ nguyên kiểu Java

**javap -s -p** sẽ cho các descriptors của phương thức ví dụ:

| Phương thức | Descriptor JVM | Diễn giải |
| --- | --- | --- |
| **addBytes(byte, byte)** | **(BB)B** | Hai byte vào, một byte ra |
| **addShorts(short, short)** | **(SS)S** | Hai short vào, một short ra |
| **nextChar(char)** | **(C)C** | Một char vào, một char ra |
| **both(boolean, boolean)** | **(ZZ)Z** | Hai boolean vào, một boolean ra |
| **doubleByte(byte)** | **(B)I** | Một byte vào, một int ra |

Những ký hiệu descriptor chính là **B** (byte), **S** (short), **C** (char), **Z** (boolean), **I** (int). Sự tồn tại của **(BB)B** cạnh các instruction **iadd/ireturn** là bằng chứng rõ: **tính toán qua int không xóa danh tính kiểu của phương thức**.

Khi xem **LocalVariableTable** trong một class có thông tin debug, bạn cũng có thể thấy descriptor khai báo của các biến. Tuy nhiên bảng debug này có thể không tồn tại; **javap -c** không cần thông tin debug để hiện instructions.

## 10. Ví dụ mảng: byte/short mở rộng dấu, char mở rộng bằng số 0

Tạo một chương trình độc lập **ArrayLoadDemo.java**:

~~~java
public class ArrayLoadDemo {
    public static void main(String[] args) {
        byte[] bytes = {(byte) 0xFF};
        short[] shorts = {(short) 0xFFFF};
        char[] chars = {(char) 0xFFFF};
        boolean[] flags = {true};

        int fromByte = bytes[0];
        int fromShort = shorts[0];
        int fromChar = chars[0];

        System.out.println("byte=" + fromByte);
        System.out.println("short=" + fromShort);
        System.out.println("char=" + fromChar);
        System.out.println("boolean=" + flags[0]);
    }
}
~~~

Lệnh chạy:

~~~bash
javac -encoding UTF-8 ArrayLoadDemo.java
java ArrayLoadDemo
~~~

**Stdout:**

~~~text
byte=-1
short=-1
char=65535
boolean=true
~~~

Các số **0xFF** và **0xFFFF** là literal số nguyên dạng hexadecimal. Cast giúp khởi tạo đúng các mảng đích:

- **(byte) 0xFF** trở thành **-1**; **baload** đọc và sign-extend lên int.
- **(short) 0xFFFF** trở thành **-1**; **saload** đọc và sign-extend.
- **(char) 0xFFFF** biểu diễn **65535**; **caload** đọc và zero-extend.
- **flags[0]** có kiểu Java **boolean**; khi in, Java hiển thị chữ **true**, không hiển thị số 1, mặc dù bytecode có thể dùng computational int để xử lý logic.

Dùng **javap -c ArrayLoadDemo** để tìm các lệnh **baload**, **saload**, **caload**. Nếu nhìn thấy **baload** cho boolean array, hãy nhớ opcode đó được JVM định nghĩa dùng được cho cả byte array và boolean array, không phải chứng cứ hai mảng có cùng kiểu ở Java.

## 11. Các lỗi phổ biến và cách giải thích theo đúng tầng

### Lỗi 1: “Đã dùng iadd thì byte biến thành int hoàn toàn”

Không đúng. Biểu thức cộng trong Java được nâng kiểu thành **int**, nhưng khai báo **byte** của tham số, field, array hay kiểu trả về vẫn có ý nghĩa. Compiler phải chèn chuyển đổi ở điểm gán/trả về kiểu nhỏ khi cần. Class file lưu descriptor tương ứng và JVM bảo vệ các ràng buộc kiểu.

### Lỗi 2: “JVM lưu mọi byte/char/boolean thành bốn byte”

Không thể kết luận từ instruction set. **int** là kiểu tính toán trên operand stack; field, mảng, object layout và tối ưu JIT là chuyện khác. Ví dụ boolean array, object field padding hoặc thanh ghi CPU do triển khai JVM quyết định trong giới hạn đặc tả. Cần dùng công cụ đo và tài liệu JVM cụ thể nếu quan tâm kích thước bộ nhớ thực.

### Lỗi 3: “byte + byte có kiểu byte, vì cả hai toán hạng nhỏ”

Sai đối với biểu thức Java dùng các biến **byte**: phép cộng có kiểu **int**. Chỉ có trường hợp gán hằng trong phạm vi hoặc phép cast/gán kết hợp phù hợp mới đưa kết quả trở lại **byte** mà không báo lỗi compile.

### Lỗi 4: “boolean là số 0 hoặc 1 nên có thể cộng”

Không hợp lệ ở mã nguồn Java. Boolean có hệ thống toán tử riêng; việc sử dụng computational int để hiện thực điều kiện là chi tiết JVM. Không có chuyển đổi Java hợp lệ **boolean ↔ int** bằng cast thông thường.

### Lỗi 5: “char có dấu giống short”

Sai. **char** là đơn vị mã UTF-16 **không dấu**; **short** là số có dấu. Cùng 16 bit thấp **FFFF**, cast về **char** cho 65535 trong biểu thức số, còn cast về **short** cho -1.

### Lỗi 6: “i2b chỉ lấy 8 bit rồi để nguyên số dương”

Không đúng khi bit cao nhất trong 8 bit còn lại là 1. Kết quả byte có dấu sẽ được **sign-extend** khi trở lại computational int, ví dụ 130 → -126.

### Lỗi 7: “Mọi phép toán với kiểu nhỏ luôn phải có i2b/i2s/i2c”

Không bắt buộc. Chỉ khi cần bảo toàn ngữ nghĩa kiểu đích hoặc mã compiler lựa chọn; phép toán cho kết quả **int** có thể dừng ở **iadd**. Hằng biểu thức còn có thể được tính tại compile time.

### Lỗi 8: “Chỉ cần đọc mã nguồn là biết chính xác mọi opcode javac sinh ra”

Cần kiểm tra class file thực. Compiler, phiên bản JDK và tối ưu có thể thay đổi trình tự bytecode tương đương. **javap** là công cụ đọc bằng chứng, còn JVM specification giải thích ngữ nghĩa instruction.

## 12. Bài tập dự đoán và tự kiểm tra

**Bài 1.** Tại sao đoạn này lỗi compile?

~~~java
byte x = 100;
byte y = 20;
byte z = x + y;
~~~

**Đáp án:** **x + y** có kiểu **int**, không được tự động gán về **byte** dù lần chạy cụ thể có thể ra 120. Sửa thành **int z = x + y;**, hoặc cast tường minh nếu chủ động chấp nhận thu hẹp.

**Bài 2.** Kết quả của đoạn này là gì?

~~~java
short s = 32767;
s++;
System.out.println(s);
~~~

**Đáp án:** **-32768**. Phép tăng dùng ngữ nghĩa số học, sau đó lưu lại kết quả theo phạm vi **short**, dẫn đến vòng về giá trị âm nhỏ nhất.

**Bài 3.** Biểu thức **(char) -1** mang giá trị số nào khi tham gia một phép toán kiểu int?

**Đáp án:** **65535**. **i2c** giữ 16 bit thấp và zero-extend. Nếu in giá trị này bằng **println(char)** có thể hiển thị ký tự không dễ quan sát; dùng ép sang **int** khi muốn xem trị số.

**Bài 4.** Có thể viết **int n = true + false;** nếu JVM dùng int cho boolean không?

**Đáp án:** Không. Java source không xem boolean là toán hạng số và không có phép cộng boolean.

**Bài 5.** Vì sao **javap** có **iload** với phương thức nhận **byte** nhưng **javap -s** vẫn báo **(BB)B**?

**Đáp án:** **iload** biểu thị computational type của instruction trong frame; **B** trong method descriptor biểu thị kiểu thật ở ranh giới phương thức. Hai thông tin thuộc hai tầng khác nhau và nhất quán với nhau.

**Bài 6.** Với **byte a = 120; byte b = 10;**, kết quả của **a + b** và **(byte) (a + b)** lần lượt là gì?

**Đáp án:** **130** kiểu **int** và **-126** kiểu **byte**. Nhớ rằng việc cast không làm phép cộng ban đầu đổi thành opcode cộng byte.

## 13. Khi nào cần hiểu chi tiết này?

Kiến thức này hữu ích khi:

- **Đọc bytecode**: không nhầm **iload/iadd/ireturn** là biến Java ban đầu có kiểu **int**.
- **Điều tra kết quả tràn số**: phân biệt bước cộng **int** với bước thu hẹp về **byte/short**.
- **Hiểu chương trình xử lý ký tự**: không nhầm **char** không dấu với **short** có dấu hay với một Unicode code point hoàn chỉnh.
- **Phân tích điều kiện**: hiểu tại sao mã xử lý **boolean** có các nhánh trên computational int nhưng vẫn phải tuân thủ logic Java.
- **Viết công cụ bytecode, compiler, instrumentation**: phải tuân theo descriptor, stack typing, chuyển đổi và các ràng buộc verifier, thay vì chỉ nhìn chữ **i** trong opcode.
- **Đánh giá hiệu năng/bộ nhớ**: tránh suy diễn layout object hoặc số byte RAM từ mô hình instruction của JVM; đo lường trên runtime cụ thể.

### Tài liệu đặc tả để đối chiếu

- Java Virtual Machine Specification, Java SE 17, chương **2.11.1 — Types and the Java Virtual Machine**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.11.1
- Java Virtual Machine Specification, Java SE 17, chương **2.11.4 — Type Conversion Instructions**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.11.4
- Java Language Specification, Java SE 17, chương **5 — Conversions and Contexts**: https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html
- Java Language Specification, Java SE 17, chương **15 — Expressions**: https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html

**Ghi nhớ cốt lõi:** Trong JVM, **byte**, **short**, **char** và **boolean** thường dùng **int** như **kiểu tính toán** trên operand stack và trong các lệnh load/store, so sánh, trả về. **byte/short** mở rộng dấu, **char/boolean** được xử lý với biểu diễn không dấu khi đưa lên stack; chuyển về các kiểu số nhỏ dùng **i2b/i2s/i2c** theo nhu cầu. Nhưng **Java vẫn duy trì kiểu nguồn riêng**: phép cộng byte/short/char theo numeric promotion cho kết quả **int**, còn **boolean tuyệt đối không trở thành kiểu số** chỉ vì cách hiện thực bytecode.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-data-types-003">long, double và cách sử dụng hai vị trí trong biến cục bộ</a>

<details>
<summary>Xem chi tiết</summary>

# `long`, `double` và hai vị trí trong mảng biến cục bộ của JVM

Trong Java, `long` và `double` đều có kích thước logic **64 bit**. Nhưng khi xem bytecode bằng `javap`, bạn sẽ thấy điều khác dễ gây ngạc nhiên: một tham số `long` hoặc `double` dùng **hai vị trí liên tiếp** (two local-variable slots) trong frame của phương thức. Nếu tham số đầu tiên là `long`, tham số tiếp theo của phương thức `static` thường bắt đầu tại **chỉ số 2**, không phải 1.

Đây là quy tắc của **Java Virtual Machine Specification (JVMS)** dành cho cấu trúc biến cục bộ trong frame, không phải quy tắc “hai biến Java” hay khẳng định chính xác rằng JVM vật lý dành hai ô RAM cố định. Hiểu đúng sẽ giúp đọc `iload`, `lload`, `dload`, `max_locals`, các tham số bytecode và thông báo verifier.

## 1. Phân biệt kích thước giá trị và số vị trí JVM

| Kiểu Java | Độ rộng giá trị theo ngữ nghĩa | Loại tính toán của JVM | Số local-variable slots khi nằm trong frame |
| --- | --- | --- | --- |
| `boolean`, `byte`, `short`, `char` | Theo kiểu Java tương ứng; một số được xử lý bằng `int` ở mức bytecode | **Category 1**, chủ yếu là `int` | **1** |
| `int` | 32 bit | **Category 1** | **1** |
| `float` | 32 bit | **Category 1** | **1** |
| `reference` (ví dụ `String`) | Không nên suy ra kích thước con trỏ vật lý từ bytecode | **Category 1** | **1** |
| **`long`** | **64 bit**, số nguyên có dấu | **Category 2** | **2** |
| **`double`** | **64 bit**, IEEE 754 double precision | **Category 2** | **2** |

**Category 1/2** là phân loại dùng trong **mô hình thực thi của JVM**. Với category 2, giá trị `long`/`double` có đơn vị độ sâu **2** trên operand stack và dùng **2 vị trí** trong mảng local variables. Giá trị vẫn là **một** `long`/`double`, không phải hai `int` độc lập.

Ví dụ:

```java
long distance = 9_000_000_000L;
double ratio = 0.75;
int attempts = 3;
```

Trong source có **ba biến**. Tại một thời điểm khi cả ba cùng cần local slots, chúng cần **2 + 2 + 1 = 5 vị trí** trong local-variable array, chưa tính receiver `this`, tham số khác, dữ liệu tạm khác và khả năng tái sử dụng slot.

**Không nhầm với**:

- **Heap:** trường `long` trong object hoặc phần tử `long[]` không “chiếm hai phần tử mảng”. Quy tắc hai slot đang bàn thuộc **local-variable array của frame**.
- **Bộ nhớ vật lý:** một slot là khái niệm đánh chỉ số mà bytecode truy cập; JVM có quyền dùng thanh ghi CPU, native stack hoặc tối ưu hóa nội bộ. Không được kết luận “mỗi slot chiếm đúng 4 byte RAM thực”.
- **Số bit của primitive:** `double` vẫn là một giá trị floating-point 64-bit, dù có hai local-variable slots.

## 2. Frame và local-variable array là gì?

Mỗi lần JVM gọi một phương thức Java, mô hình thực thi tạo một **frame** gắn với lời gọi đó. Một frame gồm:

1. **Local-variable array:** giữ tham số và biến cục bộ cần dùng bằng các chỉ số 0, 1, 2, ...
2. **Operand stack:** nơi các lệnh bytecode đưa giá trị vào, lấy toán hạng ra và trả kết quả.
3. Thông tin phục vụ dynamic linking, kết quả trả về và cơ chế thực thi phương thức.

Độ dài local-variable array của phương thức được chỉ định bằng **`max_locals`** trong `Code` attribute của class file. `max_locals` là **số vị trí (slot)** cần có, không phải số biến được khai báo trong source. `max_stack` là độ sâu tối đa của operand stack tính theo **đơn vị độ sâu**.

Một frame chỉ tồn tại cho lời gọi tương ứng trong mô hình JVM. Các frame đang hoạt động thuộc một JVM thread; nếu phương thức đệ quy, mỗi lần gọi có **frame riêng** với local variables riêng. Khác với heap, local-variable array không phải một bảng biến toàn cục chia sẻ giữa mọi thread.

### 2.1. Bắt đầu từ index 0

Các local variables được đánh chỉ số liên tiếp: `0`, `1`, `2`, ... `max_locals - 1`. Với bytecode tải `lload 0`, số `0` là **chỉ số bắt đầu** của cặp slot chứa giá trị `long`. Giá trị đó chiếm cả slot `0` lẫn `1`.

Ví dụ theo mô hình đặc tả:

```text
Local-variable array
Index:   0         1         2         3         4
       ┌─────────┬─────────┬─────────┬─────────┬─────────┐
       │    long value     │   double value    │   int   │
       │ bắt đầu index 0   │ bắt đầu index 2   │ index 4 │
       └─────────┴─────────┴─────────┴─────────┴─────────┘
           ↑                   ↑                     ↑
        lload 0             dload 2              iload 4
```

Giá trị `long` **chỉ được truy xuất bằng index thấp hơn của cặp**, tức `0`; tương tự `double` bằng `2`. Index `1` hoặc `3` **không phải một giá trị riêng** mà bạn có thể dùng `iload` hoặc `lload` để đọc phần còn lại.

### 2.2. Index của `long` không bắt buộc là số chẵn

Quy tắc là **hai vị trí liên tiếp**, không phải “phải bắt đầu tại số chẵn”. Một `long` hợp lệ có thể ở `1` và `2` (ví dụ tham số đầu của instance method sau `this`), hoặc `3` và `4`. JVM Specification **không bắt buộc căn chỉnh 64-bit theo index chẵn** trong local-variable array.

## 3. Vì sao slot kế tiếp phải bỏ qua?

Xem khai báo:

```java
static void report(long count, double price, int batch) {
    // ...
}
```

Vì là phương thức `static`, tham số bắt đầu từ index 0:

| Biến tham số | Kiểu | Vị trí trong frame |
| --- | --- | --- |
| `count` | `long` | **0 và 1**; đọc bằng `lload 0` |
| `price` | `double` | **2 và 3**; đọc bằng `dload 2` |
| `batch` | `int` | **4**; đọc bằng `iload 4` |

Cách tính index kế tiếp:

```text
start = 0                // static method: tham số đầu tiên
count: [0, 1]            // long  → dịch +2
price: [2, 3]            // double → dịch +2
batch: [4]               // int → dịch +1
next free index >= 5
```

Mảng local variables phải đủ chứa tham số tới slot 4, nên riêng các tham số yêu cầu **ít nhất 5 slots**. Nếu body có thêm biến `double` còn cần slot tại 5 và 6, `max_locals` ít nhất là 7.

**Quan trọng:** `count` là **một tham số**, dù cần hai slot. Khi API Java reflection trả `Method.getParameterCount()` cho phương thức trên, kết quả là **3**, không phải 5. Việc đếm tham số nguồn/reflection và việc đánh index local slots là hai hệ quy chiếu khác nhau.

### 3.1. Phương thức instance: slot 0 dành cho `this`

Đối với phương thức instance:

```java
double calculate(long count, double price, int batch) {
    return count * price + batch;
}
```

Receiver `this` nằm ở **index 0**; tham số thật bắt đầu từ 1:

| Vị trí | Nội dung |
| --- | --- |
| 0 | `this` — reference tới object nhận lời gọi |
| 1, 2 | `count` — một `long` |
| 3, 4 | `price` — một `double` |
| 5 | `batch` — một `int` |

Như vậy, cùng ba kiểu tham số `long, double, int`, phương thức instance cần **ít nhất 6 slots** để truyền receiver và tham số, còn phương thức `static` chỉ cần 5 slots cho tham số. Trong bytecode của instance method, `lload_1` (không phải `lload_0`) sẽ tải `count`.

`this` không phải một local variable do lập trình viên khai báo bằng cú pháp `long`/`double`, nhưng là receiver cần có trong frame của instance method theo quy tắc gọi phương thức JVM.

## 4. Quy tắc ghi đè nửa sau của một cặp slot

JVMS còn có một chi tiết quan trọng khi hiểu bytecode hoặc viết bytecode thủ công:

- Một `long`/`double` được đặt ở index `n` sẽ chiếm **n và n+1**.
- Chỉ có thể **load** toàn bộ giá trị category 2 qua index `n`.
- Có thể thực hiện **store** một giá trị khác vào index `n+1`, nhưng việc đó **làm giá trị `long`/`double` bắt đầu ở `n` không còn hợp lệ để sử dụng**.

Minh họa khái niệm:

```text
Ban đầu:
slot 0 ─┐
        ├── long value L (đọc bằng lload 0)
slot 1 ─┘

Sau khi istore 1:
slot 0 ─── dữ liệu cũ không còn tạo thành long hợp lệ
slot 1 ─── int value M

Sau đó lload 0 không còn hợp lệ theo kiểu trạng thái local variables.
```

Đây **không phải kỹ thuật cắt `long` thành hai nửa để đọc dữ liệu**, cũng không phải cách truy cập từng 32 bit. Với bytecode không nhất quán, bộ kiểm tra kiểu/verifier có thể từ chối class (ví dụ `VerifyError`). Bytecode hợp lệ chỉ load category-2 value sau khi cặp slot đã được thiết lập theo trạng thái kiểu đúng.

Trình biên dịch có thể **tái sử dụng slot** khi hai biến không đồng thời cần sống. Vì vậy không thể lấy tổng số biến từng xuất hiện trong source nhân kích thước để suy ra chính xác `max_locals`.

## 5. Các lệnh bytecode dành cho local variables

| Thao tác | Với `int` (1 slot) | Với `long` (2 slots) | Với `double` (2 slots) |
| --- | --- | --- | --- |
| Tải local lên operand stack | `iload index` | `lload index` | `dload index` |
| Lưu từ operand stack xuống local | `istore index` | `lstore index` | `dstore index` |
| Dạng rút gọn index 0…3 | `iload_0`…`iload_3` | `lload_0`…`lload_3` | `dload_0`…`dload_3` |
| Trả giá trị khỏi phương thức | `ireturn` | `lreturn` | `dreturn` |
| Phép cộng | `iadd` | `ladd` | `dadd` |

Ví dụ `lload_0` đọc **một giá trị `long` trải trên slot 0–1**. Nó **không** đọc `slot 0` rồi cần một `lload_1` riêng. Cũng vậy, `dstore 5` ghi một `double` bắt đầu tại index 5, sử dụng slot 5–6.

Một số opcode hỗ trợ thao tác theo index lớn qua tiền tố `wide`, nhưng `wide` là cơ chế **mở rộng kích thước toán hạng chỉ số của lệnh**, không phải từ khóa chỉ `long`/`double` và cũng không thay đổi quy tắc hai slot.

### 5.1. `lconst`, `dconst` và `ldc2_w`

JVM có những lệnh tải hằng phù hợp: `lconst_0`, `lconst_1` cho hai giá trị `long` nhỏ; `dconst_0`, `dconst_1` cho `double` 0.0 và 1.0; với nhiều hằng `long`/`double` khác, trình biên dịch thường dùng `ldc2_w` để tải từ constant pool. Sau đó mới dùng `lstore`/`dstore` nếu cần lưu giá trị đó vào local variables.

Ví dụ `long x = 10L;` có thể tạo bytecode `ldc2_w` rồi `lstore` tùy chỉ số; đừng dự đoán rằng nó luôn dùng một lệnh `lconst_10` (opcode đó **không tồn tại**).

## 6. Hai slot local khác operand stack như thế nào?

Ở **local-variable array**, một `long`/`double` chiếm **hai local-variable slots liên tiếp**. Nhưng ở **operand stack** (ngăn xếp toán hạng), nó là **một giá trị category 2** làm độ sâu của stack tăng **hai đơn vị**. Không có quyền tách nó thành hai giá trị category 1 một cách tùy tiện.

Ví dụ một phép cộng `long`:

```java
static long add(long a, long b) {
    return a + b;
}
```

Giả sử dùng thứ tự bytecode điển hình:

```text
Local variables:
  slot 0–1: long a
  slot 2–3: long b

Operand stack:                         độ sâu (đơn vị)
  [empty]                              0
  lload_0  → [a]                       2
  lload_2  → [a, b]                    4
  ladd     → [a+b]                     2
  lreturn  → [empty], trả một long     0
```

Vì hai toán hạng `long` đồng thời nằm trên operand stack, ví dụ này cần **`max_stack >= 4`**. Nó đồng thời cần ít nhất **`max_locals = 4`** cho hai tham số; đây là **hai chỉ số kích thước độc lập**.

Đối với `double`, `dload` và `dadd` tuân theo cùng nguyên tắc category 2. Đừng hình dung JVM cộng “nửa cao” với “nửa thấp” bằng hai lệnh `iadd`: JVM dùng đúng phép toán `dadd`/ `ladd` trên **một giá trị 64-bit có kiểu**.

Một số lệnh thao tác stack có quy tắc category riêng:

- `pop` chỉ loại bỏ một giá trị **category 1**; muốn loại bỏ một giá trị **category 2**, dùng `pop2`.
- `pop2` có thể lấy **một** giá trị category 2 **hoặc hai** giá trị category 1.
- `dup2` có thể nhân đôi **một** giá trị category 2 hoặc **hai** giá trị category 1 trên đỉnh theo hình thức hợp lệ.
- `swap` chỉ hoán đổi hai giá trị category 1; không được dùng để tách hay tráo trực tiếp một `long`/`double` thành hai nửa.

**Ghi nhớ:** operand stack có **đơn vị độ sâu 2**, không có nghĩa category-2 value gồm **hai phần tử độc lập** để `pop`, `swap` hoặc truy cập riêng.

## 7. Chương trình Java 17+ hoàn chỉnh và cách đọc bytecode

Tạo tệp **`SlotDemo.java`**:

```java
public class SlotDemo {

    // static: tham số đầu bắt đầu ở local slot 0.
    static double weighted(long items, double unitPrice, int fee) {
        double total = items * unitPrice + fee;
        return total;
    }

    // instance: local slot 0 là "this".
    double adjust(long items, double unitPrice) {
        double total = items * unitPrice;
        return total;
    }

    public static void main(String[] args) {
        long count = 3L;
        double price = 2.5;
        double result = weighted(count, price, 1);

        System.out.println("result = " + result);
        System.out.println("adjusted = " + new SlotDemo().adjust(4L, 1.25));
    }
}
```

Biên dịch/chạy:

```bash
javac SlotDemo.java
java SlotDemo
```

**Stdout chính xác:**

```text
result = 8.5
adjusted = 5.0
```

Tại `weighted(3L, 2.5, 1)`, kết quả là `3 × 2.5 + 1 = 8.5`. Tại `adjust(4L, 1.25)`, kết quả là `4 × 1.25 = 5.0`. Các giá trị biểu diễn nhị phân chính xác ở ví dụ này nên cách hiển thị ngắn không có sai số làm tròn đáng chú ý.

### 7.1. Mở bytecode bằng công cụ JDK

```bash
# Hiện lệnh bytecode, descriptor và chi tiết Code attribute.
javap -c -v -p SlotDemo

# Nếu muốn debug table tên biến cục bộ dễ đọc hơn:
javac -g SlotDemo.java
javap -c -v -p SlotDemo
```

Trong kết quả hãy tìm phần **`static double weighted(long, double, int)`** và **`Code:`**. Với bytecode thông thường do `javac` sinh từ đoạn trên, sơ đồ slot là:

```text
weighted(long items, double unitPrice, int fee)
(static, không có this)

  index     nội dung
  0, 1      items        (J = long)
  2, 3      unitPrice    (D = double)
  4         fee          (I = int)
  5, 6      total        (D = double)

  max_locals cần ít nhất 7.
  method descriptor: (JDI)D
```

Dãy lệnh bytecode minh họa cho body này:

```text
lload_0       // đọc long ở local 0–1
l2d           // đổi long thành double
dload_2       // đọc double ở local 2–3
dmul          // nhân hai double
iload 4       // đọc int ở local 4
i2d           // đổi int thành double
dadd          // cộng
dstore 5      // lưu total ở local 5–6
dload 5       // tải total
dreturn       // trả double
```

Đây là **phần trích minh họa logic** của các opcode; `javap` thực tế còn hiển thị offset, phần khai báo và metadata khác. Cách chọn từng opcode/slot cho các biến không bắt buộc phải giống tuyệt đối ở mọi compiler/phiên bản; **quy tắc về cặp slots và ý nghĩa opcode là bắt buộc**.

**Giải thích `max_stack`:** trước `dmul`, operand stack chứa hai `double` → độ sâu 4. Trước `dadd`, có hai `double` → độ sâu 4. Với dãy bytecode minh họa, `max_stack` là **4**, còn `max_locals` là **7**. Hai giá trị tình cờ khác nhau theo ví dụ; không có công thức “mỗi `long` khiến `max_stack` luôn bằng `max_locals`”.

### 7.2. Phương thức instance có thêm `this`

`adjust(long, double)` có sơ đồ:

```text
  local 0        this              (reference, 1 slot)
  local 1–2      items             (long, 2 slots)
  local 3–4      unitPrice         (double, 2 slots)
  local 5–6      total             (double, 2 slots)

  max_locals cần ít nhất 7.
  descriptor: (JD)D
```

Trong bytecode, `lload_1` phải dùng index 1 cho `items`; `dload_3` dùng index 3 cho `unitPrice`. Phương thức instance cần thêm slot 0 cho `this`, dù trong descriptor `(JD)D` **không ghi `this` như một tham số tường minh**.

### 7.3. `main` cũng dùng slot cho tham số và biến cục bộ

Ở `main(String[] args)`:

```text
  slot 0        args         (String[] reference)
  slot 1–2      count        (long)
  slot 3–4      price        (double)
  slot 5–6      result       (double)
```

Tại `invokestatic weighted`, việc chuẩn bị tham số `long`, `double`, `int` trên **operand stack** là một thao tác **khác** với nơi lưu chúng trong **local-variable array** của frame `main`. Khi phương thức `weighted` được gọi, frame **mới** có các tham số của chính nó ở index 0–4. Index trong frame của bên gọi không được dùng làm index trong frame của bên được gọi.

## 8. Đọc `Code`, `max_locals` và `LocalVariableTable` đúng cách

Giả sử `javap -v` hiển thị thông tin kiểu:

```text
Code:
  stack=4, locals=7, args_size=3
```

Cần đọc như sau:

- **`stack=4`**: chiều sâu lớn nhất của operand stack, theo đơn vị tính của JVM.
- **`locals=7`**: local-variable array có 7 vị trí, index 0–6.
- **`args_size=3`** là thông tin tóm lược được `javap` trình bày về số đối số theo cách nó báo cáo; **không thay thế quy tắc tính slot**. Để biết vị trí thực, hãy tra descriptor, `static`/instance và các lệnh `lload`/`dload`.
- **`LocalVariableTable`**, nếu tồn tại, dùng cho thông tin debug về các local variables. Một entry của `long`/`double` chỉ ra **index bắt đầu** cùng descriptor `J`/`D`; entry không có nghĩa biến nguồn đã bị tách thành hai biến.
- **`StackMapTable`**, nếu có, là thông tin loại trạng thái dùng để kiểm tra bytecode. Nó không phải bản sao toàn bộ đối tượng runtime đang sống.

Không phải mọi file `.class` đều chứa tên biến nguồn và `LocalVariableTable`; các thông tin debug có thể được bỏ qua khi biên dịch. Chỉ dựa vào tên biến trong `javap` để suy luận slot sẽ thất bại khi thiếu debug metadata. **Opcode và descriptor** đáng tin hơn cho việc hiểu index.

## 9. Tái sử dụng slot theo phạm vi sống

Hai slot dành cho một `long`/`double` không nhất thiết bị giữ tới khi phương thức kết thúc nếu trình biên dịch có thể chứng minh một biến đã hết phạm vi hoặc không còn được dùng.

Ví dụ:

```java
static void reuse() {
    {
        long left = 10L;
        System.out.println(left);
    }
    {
        int right = 3;
        System.out.println(right);
    }
}
```

Về ngữ nghĩa, `left` không còn trong phạm vi khi `right` được dùng. Một compiler có thể tái sử dụng một local slot ở khối thứ hai mà vẫn tạo bytecode hợp lệ. Tuy nhiên, **không có bảo đảm chính xác `javac` mọi phiên bản sẽ chọn một index cố định**, và JIT có thể tiếp tục chuyển giá trị sang thanh ghi/loại bỏ các lưu trữ không cần thiết.

Vì vậy:

- Không suy ra `max_locals` bằng “tổng tất cả biến được khai báo”.
- Không suy ra kích thước stack thực tế của OS bằng `max_locals × 4` byte.
- Không suy ra heap usage hoặc số đối tượng do `long`/`double` sử dụng hai local slots.
- Không coi các slot là **địa chỉ trực tiếp** để đọc/ghi bất kỳ như vùng nhớ kiểu C.

## 10. Vì sao cần verifier và quy tắc category 2?

Bytecode mang kiểu thao tác rõ ràng: `lload` đòi một local value `long` hợp lệ ở **index bắt đầu**; `dload` đòi `double`; `iload` đòi `int`. JVM verifier kiểm tra các điều kiện kiểu/stack cần thiết trước khi code được chấp nhận thực thi theo quy định. Nhờ đó, không thể hợp lệ hóa một chuỗi tùy tiện như “lưu `long` vào 0–1, ghi đè slot 1 bằng `int`, rồi vẫn `lload_0`”.

Một số lỗi có thể xuất hiện khi tự tạo/chỉnh class file:

| Dấu hiệu | Nguyên nhân cần kiểm tra |
| --- | --- |
| `VerifyError` tại lệnh `lload`/`dload` | Local index hoặc kiểu trạng thái tại điểm đó không chứa giá trị category 2 hợp lệ |
| `VerifyError` hoặc mã bị từ chối sau khi ghi vào slot thứ hai | Phần cặp slot còn lại đã làm `long`/`double` bắt đầu ở index cũ mất hiệu lực |
| `VerifyError` liên quan operand stack | Dùng `pop`/`swap` sai category hoặc stack depth/type không nhất quán |
| `ClassFormatError` hoặc lỗi xác minh do `Code` attribute | `max_locals`, `max_stack`, index hoặc cấu trúc class file không thỏa điều kiện hợp lệ |
| Đọc `javap` thấy tham số bị “nhảy index” | Thường là **đúng quy tắc** `long`/`double` chiếm hai slots, không phải compiler bỏ quên một biến |

Tên và thông điệp lỗi cụ thể tùy bản JVM và bytecode. Không tạo ví dụ `VerifyError` bằng Java source hợp lệ thông thường: `javac` sẽ chủ động sinh bytecode đúng quy tắc. Lỗi xác minh kiểu này thường xuất hiện khi class file bị chỉnh sai, bytecode generator/instrumentation bị lỗi hoặc class không tương thích theo cách khác.

## 11. Các nhầm lẫn phổ biến

1. **“`long` có hai slot thì nó là hai biến 32-bit.”** Sai. Nó là **một giá trị `long` category 2**, chiếm hai local-variable slots theo mô hình JVM.
2. **“`lload_1` đọc nửa thứ hai của `lload_0`.”** Sai. Nó bắt đầu đọc một `long` tại index 1 và chỉ hợp lệ nếu có giá trị `long` được thiết lập ở cặp **1–2**.
3. **“Một biến `double` luôn cần 8 byte thực tế trong native stack frame.”** Không thể kết luận chỉ từ JVMS. Cách hiện thực và tối ưu hóa là trách nhiệm JVM.
4. **“Hai slot phải bắt đầu tại index chẵn.”** Sai. `this` ở index 0 khiến tham số `long` đầu của instance method thường bắt đầu tại **1**.
5. **“Phương thức `static` và instance có cùng index tham số.”** Sai. Instance method dành slot 0 cho `this`.
6. **“`max_locals` đếm biến Java.”** Sai. Nó đếm **slot**, cả receiver, tham số và local storage của bytecode; compiler có thể tái sử dụng.
7. **“`max_stack` và `max_locals` đều là số ô trong cùng một mảng.”** Sai. Chúng là hai vùng dữ liệu khác nhau của frame.
8. **“`long[]` có hai phần tử cho mỗi số.”** Sai. Hai slot áp dụng cho local-variable array, không phải chỉ số của Java array.
9. **“Một `long` trên operand stack là hai giá trị `int` để `pop` hai lần.”** Sai. Nó là một giá trị category 2, chỉ thao tác qua opcode phù hợp.
10. **“Dùng `int` thay `long` để tiết kiệm một slot chắc chắn tăng hiệu năng.”** Không có kết luận như vậy. Chọn kiểu theo miền giá trị/độ chính xác; hiệu năng thực cần đo trong workload cụ thể.

## 12. Câu hỏi tự kiểm tra có đáp án

**Câu 1.** Phương thức `static void foo(double x, int y, long z)` dùng bao nhiêu slot cho tham số và từng index là gì?

**Đáp án:** `x` dùng **0–1**; `y` dùng **2**; `z` dùng **3–4**. Tổng cần ít nhất **5 slots** để chứa tham số.

**Câu 2.** Nếu đổi `foo` thành instance method với cùng danh sách tham số, index của `z` là bao nhiêu?

**Đáp án:** `this` ở **0**; `x` ở **1–2**; `y` ở **3**; `z` ở **4–5**. Index bắt đầu của `z` là **4**.

**Câu 3.** `static long sum(long a, long b)` chỉ cần trả `a + b`. Nếu operand stack cùng lúc có `a` và `b`, độ sâu tính theo đơn vị là bao nhiêu?

**Đáp án:** **4**: hai giá trị `long`, mỗi giá trị category 2 góp hai đơn vị.

**Câu 4.** Vì sao `lload 2` và `lload 3` không thể tự ý dùng để lấy “hai nửa” của một `long` đã lưu bắt đầu tại index 2?

**Đáp án:** `lload 2` lấy **toàn bộ** giá trị `long` chiếm cặp **2–3**. `lload 3` lại đòi một giá trị `long` hợp lệ bắt đầu ở **3** (tức cặp **3–4**), hoàn toàn khác và không thể dùng nửa sau như một giá trị riêng.

**Câu 5.** Một instance method chứa `long` và `double` khiến `max_locals = 7`. Điều đó có chứng minh có đúng 7 biến source hay 28 byte physical memory?

**Đáp án:** **Không.** `max_locals` là số slots trừu tượng; receiver và giá trị category 2 làm thay đổi số slots, compiler có thể tái sử dụng slots và runtime có quyền chọn biểu diễn tối ưu.

## Kết luận

Trong frame của JVM, `long` và `double` là **giá trị category 2**: mỗi giá trị chiếm **hai local-variable slots liên tiếp**, được đánh địa chỉ bằng **index thấp hơn**; index thứ hai không phải một biến có thể đọc độc lập. Trên operand stack, mỗi giá trị ấy cũng tính **hai đơn vị độ sâu**, dù vẫn là một toán hạng logic. Phân biệt hai vùng dữ liệu, nhớ slot 0 cho `this` ở instance method, và dùng `javap -c -v` để kiểm tra `max_locals` và opcode giúp bạn đọc bytecode đúng mà không suy diễn về bộ nhớ vật lý.

### Tài liệu tham khảo

- [JVMS Java SE 17, §2.6.1 — Local Variables](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.6.1): quy tắc cặp slot, index thấp hơn, slot thứ hai và tham số instance/`static`.
- [JVMS Java SE 17, §2.6.2 — Operand Stacks](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.6.2): loại giá trị, độ sâu tính theo đơn vị.
- [JVMS Java SE 17, §2.11.1–2.11.2 — Type Categories và Load/Store](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.11.1): category 1/2 và opcode truy cập local.
- [JVMS Java SE 17, §4.7.3 — Code Attribute](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.3): `max_stack`, `max_locals` trong class file.
- [Oracle JDK 17 — `javap`](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): cách xem bytecode và metadata.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-data-types-004">Giá trị null, kiểu lớp, kiểu giao diện và kiểu mảng</a>

<details>
<summary>Xem chi tiết</summary>

# Giá trị null, kiểu lớp, kiểu giao diện và kiểu mảng trong JVM

## 1. Mục tiêu: hiểu đúng các giá trị tham chiếu

Trong JVM, kiểu dữ liệu phục vụ việc biểu diễn giá trị, kiểm tra tính hợp lệ của bytecode và thực thi chương trình. Một phân biệt nền tảng là **giá trị nguyên thủy** (*primitive value*) và **giá trị tham chiếu** (*reference value*).

Ví dụ:

```java
int count = 3;                 // giá trị primitive int
String title = "JVM";          // biến có kiểu tham chiếu tới String
Runnable task = () -> {};     // tham chiếu qua kiểu giao diện
int[] numbers = {1, 2, 3};    // mảng cũng là đối tượng và giá trị biến là tham chiếu
String missing = null;        // giá trị tham chiếu null đặc biệt
```

Trong **đặc tả JVM (JVMS §2.4)**, ba nhóm kiểu tham chiếu (*reference types*) là:

1. **Kiểu lớp (*class types*)**: mô tả các đối tượng thuộc một lớp, chẳng hạn `java.lang.String`.
2. **Kiểu giao diện (*interface types*)**: cho phép tham chiếu đối tượng có lớp hiện thực giao diện, chẳng hạn `java.lang.Runnable`.
3. **Kiểu mảng (*array types*)**: mô tả đối tượng mảng, chẳng hạn `int[]` hoặc `String[][]`.

Giá trị tham chiếu có thể là tham chiếu tới một đối tượng tương ứng hoặc **giá trị đặc biệt `null`**. JVM không thêm “kiểu JVM thứ tư mang tên null” vào ba nhóm trên. Ở cấp **ngôn ngữ Java**, đặc tả JLS nói đến một **null type** đặc biệt dành cho biểu thức `null`; kiểu này không có tên để viết khai báo biến và cần phân biệt với phân loại reference types trong JVMS.

Mục tiêu sau bài học: giải thích đối tượng nào có thể được giữ trong biến lớp/giao diện/mảng, vì sao `null` hợp lệ ở các vị trí tham chiếu, bytecode nào liên quan, khi nào xuất hiện `NullPointerException`, `ClassCastException`, `ArrayStoreException` và vì sao `int[]` là kiểu tham chiếu dù phần tử của nó là `int`.

## 2. Từ kiểu nguồn Java tới giá trị mà JVM thao tác

Xét:

```java
String message = "hello";
Object any = message;
CharSequence readable = message;
```

Đối tượng chuỗi được tạo/quản lý trong runtime; ba biến có thể cùng tham chiếu đến **một đối tượng** dù kiểu khai báo khác nhau. Một biến kiểu tham chiếu **không chứa bản sao toàn bộ đối tượng**. Khi gán `any = message`, chương trình sao chép *giá trị tham chiếu* theo mô hình ngôn ngữ, không tự sao chép nội dung chuỗi.

Từ “tham chiếu” không đồng nghĩa với “địa chỉ bộ nhớ thô mà lập trình viên có thể đọc ra và tính toán”. Đặc tả JVM không buộc mọi triển khai biểu diễn reference bằng cùng một dạng con trỏ vật lý. Các trình biên dịch/runtime còn có thể tối ưu nội bộ, nhưng ngữ nghĩa quan sát được phải tuân thủ hợp đồng Java.

### Kiểu khai báo và kiểu thực tế khác nhau

```java
Object value = "Java";
```

- **Kiểu tĩnh** của biến `value` là `Object`; compiler kiểm tra các thao tác được phép trên biến này dựa vào kiểu tĩnh.
- **Kiểu runtime** của đối tượng đang được tham chiếu là `String`.
- Có thể kiểm tra runtime bằng `value instanceof String` hoặc `value.getClass()` nếu tham chiếu khác `null`.
- Nếu gán `value = null`, không có đối tượng nào đứng sau tham chiếu đó để hỏi `getClass()`.

Phân biệt này giải thích vì sao cùng một đối tượng có thể được xem qua kiểu lớp cha, giao diện hoặc kiểu `Object`, nhưng phép ép kiểu sai chỉ phát hiện ở runtime.

### Đối tượng nằm ở đâu?

Trong mô hình JVM, đối tượng và mảng được cấp phát trên **heap**. Những vị trí chứa giá trị tham chiếu có thể là biến cục bộ của frame, trường đối tượng, trường `static` hoặc phần tử của mảng tham chiếu. Cách bố trí máy thật, tối ưu và tổ chức bộ nhớ cụ thể phụ thuộc bản triển khai JVM.

Sơ đồ khái niệm:

```text
Biến cục bộ              Heap (khái niệm)
-------------------      -----------------------
String a -----------+---> đối tượng String "JVM"
Object b -----------+
String c = null ----X     (không có đối tượng cho c)
```

Gán một biến bằng `null` chỉ thay đổi tham chiếu ở vị trí đó. Nó **không trực tiếp hủy** đối tượng đã từng được tham chiếu; đối tượng có thể còn được tham chiếu từ nơi khác, và tính có thể thu hồi do cơ chế reachability/garbage collection quyết định.

## 3. Giá trị `null`: có thể xuất hiện ở đâu và ý nghĩa ra sao?

### 3.1. `null` không phải số không, chuỗi rỗng hay đối tượng `Null`

`null` biểu thị **không có đối tượng/mảng đang được tham chiếu** tại vị trí đó. Nó khác hẳn:

| Biểu thức | Ý nghĩa |
| --- | --- |
| `null` | Không tham chiếu tới đối tượng |
| `0` | Giá trị số nguyên bằng không |
| `""` | Tham chiếu tới đối tượng `String` có độ dài 0 |
| `new int[0]` | Tham chiếu tới mảng `int` hợp lệ với `length == 0` |
| `new Object()` | Tham chiếu tới một đối tượng vừa tạo |

Một mảng dài 0 vẫn là **một đối tượng mảng có thật**, có kiểu runtime và có thể gọi `getClass()`. `null` thì không thể truy cập thành viên hay hỏi lớp runtime.

### 3.2. `null` tương thích với vị trí có kiểu tham chiếu

```java
String text = null;
Runnable runnable = null;
Object object = null;
String[] strings = null;
int[] integers = null;
```

Các khai báo trên hợp lệ. `int[]` là **kiểu mảng/tham chiếu**, còn `int` là primitive; vì thế `int[] a = null` hợp lệ nhưng `int x = null` là lỗi biên dịch.

Chú ý khác biệt giữa `null` và mảng có phần tử mang giá trị mặc định:

```java
String[] names = new String[2];
System.out.println(names == null); // false
System.out.println(names[0]);      // null
```

Biến `names` chứa tham chiếu tới **một mảng có thật**. Hai slot của mảng `String[]` ban đầu chứa **null reference**.

### 3.3. Giá trị mặc định và definite assignment

Các **trường đối tượng**, **trường static** và **phần tử mảng kiểu tham chiếu** được khởi tạo mặc định bằng `null` khi đối tượng/lớp/mảng được khởi tạo theo quy tắc tương ứng. Ví dụ:

```java
class Account {
    String owner;             // giá trị mặc định null
    static Object cache;      // giá trị mặc định null
}
```

Ngược lại, biến cục bộ phải được gán chắc chắn trước khi đọc, theo quy tắc *definite assignment* của trình biên dịch Java:

```java
// Minh họa lỗi biên dịch (không phải chương trình độc lập):
String unassigned;
// System.out.println(unassigned); // lỗi: variable might not have been initialized
```

Không nên suy luận rằng “biến cục bộ kiểu tham chiếu luôn mặc định bằng `null`”; ràng buộc ở ngôn ngữ Java ngăn đọc biến chưa được gán trong tình huống trên. Ngoài ra, `var x = null;` không đủ thông tin để suy luận một kiểu biến cục bộ hợp lệ; muốn chủ động cho phép null, hãy khai báo kiểu rõ ràng.

### 3.4. `null`, phép so sánh và `instanceof`

```java
Object a = null;
Object b = null;
System.out.println(a == b);           // true
System.out.println(a instanceof Object); // false
```

Toán tử `==` trên hai giá trị tham chiếu so sánh chúng có cùng tham chiếu hay không (hoặc cùng là `null`). `null instanceof BấtKỳKiểuThamChiếuPhùHợp` luôn **false**: không có đối tượng để kiểm tra kiểu runtime.

Phép ép kiểu tham chiếu của `null` hợp lệ và trả lại `null`:

```java
String name = (String) null;
System.out.println(name == null); // true
```

Điều này khác với ép kiểu một đối tượng **không phải null** sang kiểu không tương thích, có thể ném `ClassCastException`.

### 3.5. Dereference của `null`

Các thao tác cần đối tượng thật sẽ thất bại nếu đối tượng đích là `null`. Ví dụ:

```java
String s = null;
// s.length(); // NullPointerException tại runtime
```

Những tình huống điển hình:

- Gọi phương thức instance hoặc truy cập trường instance qua tham chiếu null.
- Đọc/ghi phần tử mảng null và đọc `array.length` qua mảng null.
- Thực hiện unboxing trên wrapper có giá trị null, ví dụ `Integer boxed = null; int n = boxed;`.
- Một số thao tác khác yêu cầu tham chiếu khác null.

**Không phải mọi thao tác liên quan `null` đều lỗi.** Gán `null`, so sánh `== null`, `instanceof` và ép kiểu tham chiếu đối với `null` có ngữ nghĩa hợp lệ. Cần phân biệt kiểm tra/di chuyển một reference với thao tác thực sự cần đối tượng.

### 3.6. `null` trong overload và thiết kế API

Hai overload không liên quan nhau về độ đặc hiệu có thể khiến lời gọi với `null` trở nên **mơ hồ lúc biên dịch**:

```java
// Minh họa cấu trúc: không dùng như chương trình độc lập.
void select(String value) {}
void select(Integer value) {}

// select(null); // lỗi biên dịch: reference to select is ambiguous
select((String) null); // chọn overload nhận String
```

Đây là vấn đề **chọn phương thức lúc compile-time** dựa trên kiểu của biểu thức, không phải `NullPointerException`.

Khi một API yêu cầu giá trị không null, nên ghi rõ hợp đồng và kiểm tra ở ranh giới phù hợp:

```java
import java.util.Objects;

static String normalize(String input) {
    Objects.requireNonNull(input, "input");
    return input.trim();
}
```

Đoạn trên là **mã minh họa phương thức**, không phải một chương trình độc lập. `requireNonNull` ném `NullPointerException` có thông điệp xác định nếu caller truyền null; đây là lỗi chủ động, dễ xác định nguyên nhân hơn so với dereference sâu trong chương trình.

## 4. Kiểu lớp (*class type*)

### 4.1. Biến kiểu lớp tham chiếu tới một instance tương thích

Khi khai báo:

```java
Number number = Integer.valueOf(42);
Object object = number;
```

`Number` và `Object` đều là kiểu lớp. Đối tượng runtime là instance của `Integer`, vốn là lớp con của `Number` và `Object`. Các phép gán từ kiểu cụ thể hơn sang kiểu cha được phép khi có quan hệ kiểu phù hợp (*widening reference conversion*).

Kiểu lớp không nhất thiết là lớp cụ thể có thể dùng trực tiếp với `new`:

- Một lớp **trừu tượng** như `Number` vẫn là một kiểu lớp hợp lệ cho biến, tham số và kiểu trả về.
- Không thể `new Number()` vì đó là lớp abstract.
- Giá trị vẫn có thể đến từ đối tượng của lớp con cụ thể, chẳng hạn `Integer`.

**Lớp vs. giá trị tham chiếu:** kiểu `String` mô tả nhóm đối tượng phù hợp; biến `String s` chỉ chứa một giá trị tham chiếu tại một thời điểm, có thể là `null`.

### 4.2. Lớp khai báo và lớp runtime quyết định điều gì?

```java
Object value = "hello";
```

Compiler chỉ cho phép gọi trực tiếp phương thức được biết qua kiểu tĩnh `Object` (trừ khi có ép kiểu hoặc cơ chế ngôn ngữ khác). Ví dụ `value.toString()` hợp lệ còn `value.length()` không hợp lệ khi chưa kiểm tra và chuyển kiểu.

Dùng pattern matching cho `instanceof` (hợp lệ từ Java 17):

```java
if (value instanceof String text) {
    System.out.println(text.length());
}
```

Điều kiện true vì runtime object là `String`; biến `text` được compiler kiểm tra là kiểu `String` trong phạm vi nhánh tương ứng. Nếu `value == null`, điều kiện false.

Ép kiểu tham chiếu:

```java
Object value = "hello";
String ok = (String) value;         // hợp lệ ở runtime
// Integer bad = (Integer) value;  // ClassCastException ở runtime
```

Dòng gây lỗi đã được chú thích để ví dụ còn lại hợp lệ. Nếu runtime phát hiện đối tượng không tương thích với target class/interface, thao tác cast sẽ thất bại.

### 4.3. `java.lang.Object` trong hệ phân cấp

`Object` là lớp cha gốc của các lớp Java thông thường và là kiểu có thể chứa tham chiếu tới các đối tượng Java, bao gồm đối tượng mảng:

```java
Object object = new Object();
Object text = "a";
Object array = new int[3];
```

Nhưng không được đồng nhất primitive với reference: `int` là primitive, còn `Integer` là class wrapper. `Object boxed = 5;` hợp lệ nhờ **autoboxing** từ `int` thành đối tượng wrapper tương ứng; không có nghĩa `int` tự biến thành một loại class JVM.

## 5. Kiểu giao diện (*interface type*)

### 5.1. Biến giao diện chứa tham chiếu tới đối tượng hiện thực hợp đồng

Một interface định nghĩa hợp đồng mà lớp có thể hiện thực. Ví dụ:

```java
interface Named {
    String name();
}

final class Person implements Named {
    private final String value;

    Person(String value) {
        this.value = value;
    }

    @Override
    public String name() {
        return value;
    }
}
```

Các đoạn khai báo trên là **mã minh họa nhiều kiểu top-level**, cần đặt vào các tệp Java phù hợp khi dùng trong dự án thật. Có thể dùng:

```java
Named named = new Person("Lan");
System.out.println(named.name()); // Lan
```

`Named` là **kiểu khai báo** của biến; đối tượng thực tế là instance của `Person`. Lời gọi `named.name()` được thực thi theo triển khai áp dụng cho đối tượng runtime. Trong class file, lời gọi qua interface thường tương ứng với opcode `invokeinterface`; lời gọi phương thức instance qua lớp thường dùng `invokevirtual` khi thích hợp. Đây là chi tiết mô hình JVM, không phải lời hứa rằng mọi lời gọi Java luôn tạo đúng cùng một opcode sau mọi tối ưu của compiler/runtime.

### 5.2. Vì sao interface là kiểu tham chiếu dù không tự tạo instance?

Không thể viết `new Named()` khi `Named` là interface thông thường. Tuy vậy, biến `Named` có thể chứa:

- Tham chiếu tới đối tượng của lớp bất kỳ hiện thực `Named`.
- Giá trị `null`.
- Đối tượng của lớp con kế thừa triển khai `Named` nếu phù hợp.

Interface không phải bản sao của đối tượng; nó là **cách biểu diễn khả năng/quan hệ kiểu** của đối tượng. Một đối tượng có thể được tham chiếu qua nhiều interface khác nhau.

```java
Runnable r = () -> System.out.println("run");
Object sameObject = r;
```

Cả `r` và `sameObject` có thể giữ tham chiếu tới cùng một object runtime. `r.run()` gọi hàm triển khai; `sameObject.run()` không hợp lệ tại compile-time vì kiểu tĩnh `Object` không khai báo `run()`.

### 5.3. Interface không loại bỏ kiểm tra runtime

```java
Object candidate = "text";
System.out.println(candidate instanceof Runnable); // false
```

Tham chiếu runtime tới `String` không trở thành `Runnable` chỉ vì programmer muốn ép kiểu. Ép kiểu sang interface không được hỗ trợ bởi đối tượng có thể ném `ClassCastException`. `null` thì cast được sang interface nhưng không thể gọi phương thức của interface khi vẫn null.

Kiểu **giao diện** trong JVM còn xuất hiện trong kiểm tra kiểu, descriptor, quy tắc assignability và liên kết phương thức. Tính đa hình chỉ hoạt động khi đối tượng thực tế phù hợp hợp đồng.

## 6. Kiểu mảng (*array type*): phần tử có thể là primitive hoặc reference

### 6.1. Mọi mảng Java đều là đối tượng

```java
int[] numbers = new int[3];
String[] words = new String[3];
```

Cả `int[]` và `String[]` đều là **reference type** và đều tham chiếu tới **đối tượng mảng**. Sự khác biệt là **kiểu thành phần** (*component type*):

- `int[]` có component type `int` (primitive). Mỗi slot chứa một giá trị `int`.
- `String[]` có component type `String` (reference). Mỗi slot chứa giá trị tham chiếu tới `String` hoặc `null`.
- `String[][]` có component type `String[]` (cũng là một kiểu mảng); phần tử của mảng ngoài là các tham chiếu tới mảng trong.

Đối tượng mảng có **độ dài cố định** kể từ khi tạo. `array.length` trả số phần tử, còn `array.length = 7` là lỗi biên dịch. Muốn tăng sức chứa linh hoạt, xem xét các collection như `ArrayList` khi yêu cầu phù hợp.

### 6.2. Giá trị mặc định của các phần tử

| Khai báo | Kiểu phần tử | Giá trị mặc định |
| --- | --- | --- |
| `new int[2]` | `int` | `0` |
| `new boolean[2]` | `boolean` | `false` |
| `new double[2]` | `double` | `0.0` |
| `new String[2]` | `String` | `null` |
| `new Runnable[2]` | `Runnable` | `null` |
| `new int[2][]` | `int[]` (kiểu tham chiếu) | `null` |

Nếu `String[] array = null;` thì bản thân biến không trỏ tới mảng nào. Nếu `String[] array = new String[2];` thì có mảng thật với hai phần tử ban đầu là null. Đây là hai trạng thái khác nhau.

### 6.3. Mảng một chiều, nhiều chiều và jagged array

```java
int[][] grid = new int[2][3];
```

`grid` tham chiếu tới một mảng bên ngoài gồm hai phần tử kiểu `int[]`; mỗi phần tử trỏ tới một mảng `int` độ dài 3.

```text
grid ──> [ ref hàng 0 | ref hàng 1 ]
              |             |
              v             v
           [0,0,0]       [0,0,0]
```

Mảng nhiều chiều Java là **mảng của các mảng**. Các hàng có thể dài khác nhau:

```java
int[][] ragged = new int[2][];
ragged[0] = new int[] {1};
ragged[1] = new int[] {2, 3, 4};
```

Ở thời điểm sau `new int[2][]` nhưng trước khi gán hai hàng, mỗi phần tử `ragged[0]` và `ragged[1]` đều là `null`. Cố truy cập `ragged[0].length` lúc này sẽ dẫn đến `NullPointerException`.

### 6.4. Mảng là đối tượng, thuộc về phân cấp đặc biệt

Mọi đối tượng mảng Java đều có `Object` là superclass trực tiếp theo mô hình runtime của Java và thực hiện hai marker interface `java.lang.Cloneable` và `java.io.Serializable`. Ví dụ:

```java
Object value = new int[] {1, 2};
Cloneable cloneable = (Cloneable) value;
java.io.Serializable serializable = (java.io.Serializable) value;
```

Các phép cast trong ví dụ thành công vì giá trị runtime là mảng. Tuy nhiên, `int[]` **không phải** `Object[]`: phần tử của `int[]` là số nguyên primitive, không phải tham chiếu object. Mã sau không biên dịch:

```java
// Minh họa lỗi kiểu (dòng chủ ý được chú thích):
// Object[] array = new int[3]; // incompatible types
```

Ngược lại, `String[]` có thể được gán cho `Object[]` vì các mảng **có phần tử tham chiếu** hỗ trợ quan hệ hiệp biến có điều kiện (phần dưới).

### 6.5. Mảng giữ kiểu thành phần runtime

Mảng tham chiếu **ghi nhớ kiểu thành phần runtime** để JVM kiểm tra phép ghi vào phần tử. Vì vậy `Object[] view = new String[2]` hợp lệ, nhưng đối tượng thực vẫn là `String[]` và JVM không cho ghi một `Integer` vào slot đó.

Có thể quan sát:

```java
Object a = new String[2];
Object b = new int[2];
System.out.println(a.getClass().isArray()); // true
System.out.println(b.getClass().isArray()); // true
```

`a.getClass().getComponentType()` phản ánh `String.class`, còn `b.getClass().getComponentType()` phản ánh `int.class`. Kiểm tra runtime chỉ thực hiện được khi tham chiếu khác `null`.

## 7. Array covariance và `ArrayStoreException`

Quan hệ **covariance** của mảng tham chiếu có nghĩa: khi `Sub` là subtype của `Super`, thì `Sub[]` có thể sử dụng ở nơi chấp nhận `Super[]`, theo quy tắc kiểu tương ứng.

```java
String[] actual = new String[2];
Object[] view = actual;    // hợp lệ tại compile-time
view[0] = "hello";        // hợp lệ tại runtime
// view[1] = 123;         // biên dịch được, nhưng ném ArrayStoreException
```

Lý do:

1. Kiểu tĩnh của `view` là `Object[]`, nên compiler cho phép lưu một `Integer` (autoboxing từ số `123`).
2. Đối tượng mảng runtime thực sự là `String[]`.
3. Khi lệnh lưu phần tử tham chiếu chạy, JVM kiểm tra giá trị có hợp lệ với **component type thực tế** của mảng hay không.
4. `Integer` không phải `String` → `ArrayStoreException`.

**Điều quan trọng:** không phải tất cả mảng Java đều có covariance giống nhau. `int[]` không có phép gán như `Object[]`; với các component **primitive**, những mảng khác kiểu primitive không tạo quan hệ subtype bằng widening số. Ví dụ `int[]` không tự chuyển thành `long[]`.

So với generics:

```java
// Minh họa compile-time (dòng không hợp lệ được chú thích):
// java.util.List<Object> objects = new java.util.ArrayList<String>();
```

`List<String>` không là subtype của `List<Object>` (invariance của generic type argument), trong khi `String[]` là subtype của `Object[]`. Cách thiết kế này giúp generics phát hiện nhiều phép ghi sai **khi biên dịch**; mảng có thể phải kiểm tra cả **khi chạy**.

**Rủi ro thực tế:** truyền một `String[]` vào API nhận `Object[]` rồi API ghi giá trị không phải `String` có thể phát sinh `ArrayStoreException` sâu trong chương trình. Khi được phép thay đổi thiết kế, API nên khai báo đúng kiểu thành phần hoặc dùng generic collection với hợp đồng phù hợp.

## 8. Các phép chuyển kiểu, so sánh và lỗi runtime

| Thao tác | Ví dụ | Kết quả / lưu ý |
| --- | --- | --- |
| Widening reference | `Object o = "A";` | Hợp lệ: `String` tương thích với `Object` |
| Interface reference | `CharSequence c = "A";` | Hợp lệ: `String` thực hiện `CharSequence` |
| Null assignment | `String s = null;` | Hợp lệ: biến tham chiếu giữ null |
| Null cast | `String s = (String) null;` | Hợp lệ, kết quả vẫn null |
| Null `instanceof` | `null instanceof String` | `false` |
| Null dereference | `((String) null).length()` | `NullPointerException` |
| Checked downcast | `(String) someObject` | Thành công nếu runtime object phù hợp hoặc null; nếu không có thể `ClassCastException` |
| Reference array upcast | `Object[] a = new String[1];` | Biên dịch được |
| Wrong reference array store | `a[0] = Integer.valueOf(3);` | `ArrayStoreException` nếu a thực là `String[]` |
| Primitive array as object | `Object o = new int[1];` | Hợp lệ vì mảng là đối tượng |
| Primitive array as object array | `Object[] o = new int[1];` | Không biên dịch |
| Read beyond array bounds | `new int[1][1]` (truy cập index 1 ở mảng ngoài) | `ArrayIndexOutOfBoundsException` |
| Negative array size | `new String[-1]` | `NegativeArraySizeException` khi chạy |

**Lưu ý:** phát sinh lỗi còn tùy **biểu thức thực sự được thực thi**. Bảng mô tả ngữ nghĩa của trường hợp tương ứng; không nên hiểu compiler cho phép mọi ví dụ như một chương trình độc lập.

## 9. Ví dụ Java 17+ hoàn chỉnh và stdout chính xác

Tạo một tệp duy nhất **`ReferenceTypesDemo.java`** trong thư mục hiện tại:

```java
import java.io.Serializable;
import java.util.Arrays;

public class ReferenceTypesDemo {
    interface Named {
        String name();
    }

    static final class Student implements Named {
        private final String value;

        Student(String value) {
            this.value = value;
        }

        @Override
        public String name() {
            return value;
        }
    }

    static void report(Named candidate) {
        System.out.println("is null: " + (candidate == null));
        System.out.println("instanceof Named: " + (candidate instanceof Named));
    }

    public static void main(String[] args) {
        Student student = new Student("Lan");
        Object byClass = student;
        Named byInterface = student;

        System.out.println("class: " + (byClass instanceof Student));
        System.out.println("interface call: " + byInterface.name());

        Named missing = null;
        report(missing);

        String[] names = new String[2];
        System.out.println("reference default: " + names[0]);
        names[0] = "Java";
        names[1] = "JVM";
        System.out.println("array: " + Arrays.toString(names));
        System.out.println("length: " + names.length);
        System.out.println("array as Object: " + (names instanceof Object));
        System.out.println("array as Cloneable: " + (names instanceof Cloneable));
        System.out.println("array as Serializable: " + (names instanceof Serializable));

        Object[] wider = names;
        try {
            wider[0] = Integer.valueOf(42);
        } catch (ArrayStoreException exception) {
            System.out.println("array store rejected");
        }
        System.out.println("array after check: " + Arrays.toString(names));

        int[] numbers = new int[2];
        System.out.println("primitive default: " + numbers[0]);
        System.out.println("primitive array as Object: " + (numbers instanceof Object));

        int[][] grid = new int[2][];
        System.out.println("nested row null: " + (grid[0] == null));
        grid[0] = new int[] {1, 2};
        grid[1] = new int[] {3};
        System.out.println("jagged row lengths: "
                + grid[0].length + ", " + grid[1].length);

        String absent = (String) null;
        System.out.println("cast null: " + (absent == null));
        System.out.println("null instanceof Object: " + (absent instanceof Object));

        try {
            absent.length();
        } catch (NullPointerException exception) {
            System.out.println("dereference null rejected");
        }
    }
}
```

Biên dịch và chạy bằng JDK 17 trở lên:

```bash
javac ReferenceTypesDemo.java
java ReferenceTypesDemo
```

**Stdout chính xác** của chương trình (không phụ thuộc PID hoặc chuỗi thông báo ngoại lệ của JVM):

```text
class: true
interface call: Lan
is null: true
instanceof Named: false
reference default: null
array: [Java, JVM]
length: 2
array as Object: true
array as Cloneable: true
array as Serializable: true
array store rejected
array after check: [Java, JVM]
primitive default: 0
primitive array as Object: true
nested row null: true
jagged row lengths: 2, 1
cast null: true
null instanceof Object: false
dereference null rejected
```

### Giải thích các dòng quan trọng

**`class: true`:** biến `byClass` có kiểu khai báo `Object` nhưng tham chiếu một đối tượng `Student`. `instanceof Student` kiểm tra đối tượng runtime.

**`interface call: Lan`:** biến `byInterface` có kiểu khai báo `Named`; runtime gọi triển khai `name()` của `Student`.

**`is null: true` và `instanceof Named: false`:** kiểu khai báo giao diện cho phép null, nhưng null không đại diện cho một đối tượng hiện thực giao diện.

**`reference default: null`:** các slot của mảng `String[]` được tạo mới bắt đầu bằng null reference.

**`array as Object/Cloneable/Serializable: true`:** các đối tượng mảng tham gia vào phân cấp kiểu đặc biệt.

**`array store rejected`:** kiểm tra ở runtime phát hiện giá trị `Integer` không thể được lưu vào mảng thực tế `String[]`, ngay cả khi biến dùng để ghi có kiểu `Object[]`. Phần tử cũ vẫn được giữ lại vì thao tác không được chấp nhận.

**`primitive array as Object: true`:** bản thân `int[]` là một object, dù giá trị mỗi phần tử là primitive `int`.

**`nested row null: true`:** mảng bên ngoài `new int[2][]` được tạo, nhưng chưa tạo các mảng hàng. Hai hàng được tạo sau đó có độ dài khác nhau.

**`cast null: true` và `null instanceof Object: false`:** cast null hợp lệ, nhưng null không phải instance của `Object`.

**`dereference null rejected`:** lời gọi `absent.length()` cần một đối tượng `String` và gây `NullPointerException`; ví dụ chủ động bắt exception để tiếp tục và giữ stdout rõ ràng.

Bài dùng các lớp nested để bảo đảm **một tệp nguồn độc lập**, với tên tệp trùng tên public class và lệnh chạy đơn giản. Các ví dụ khác trong bài là minh họa rời; ví dụ đầy đủ ở mục này mới được dùng để kiểm chứng stdout liền mạch.

## 10. Bytecode/JVM nhìn các kiểu tham chiếu ra sao?

Mã nguồn Java cung cấp cú pháp giàu ý nghĩa; class file biểu diễn nhiều kiểu dưới dạng **descriptor** và các lệnh thao tác reference.

### 10.1. Descriptor cho kiểu lớp, giao diện và mảng

Ví dụ một số **field descriptor** ở cấp class file:

| Kiểu trong Java | Descriptor JVM | Giải thích |
| --- | --- | --- |
| `int` | `I` | Giá trị primitive `int` |
| `java.lang.String` | `Ljava/lang/String;` | Một class type |
| `java.lang.Runnable` | `Ljava/lang/Runnable;` | Một interface type |
| `int[]` | `[I` | Mảng một chiều của primitive `int` |
| `String[]` | `[Ljava/lang/String;` | Mảng một chiều của `String` |
| `int[][]` | `[[I` | Mảng hai chiều theo nghĩa mảng của mảng `int` |
| `Object[][]` | `[[Ljava/lang/Object;` | Mảng của mảng chứa tham chiếu `Object` |

Quy tắc chính:

- `L<tên-nội-bộ>;` biểu diễn kiểu class hoặc interface; tên nội bộ trong descriptor dùng dấu `/`.
- Mỗi ký tự `[` thêm một chiều mảng.
- Primitive có mã descriptor riêng như `I` cho `int`, `J` cho `long`, `Z` cho `boolean`.

Trong bytecode, **không có “field descriptor của null”** như một reference type độc lập. `null` là một **giá trị reference đặc biệt**, có thể được đẩy lên operand stack bằng lệnh `aconst_null`. Khi xác minh bytecode, JVM có các khái niệm verification type riêng cho null để kiểm tra tính hợp lệ của luồng dữ liệu; không nên đánh đồng chúng với tên kiểu có thể khai báo trong Java.

### 10.2. Những opcode quan trọng

| Opcode / nhóm | Vai trò khái niệm |
| --- | --- |
| `aconst_null` | Đưa một null reference lên operand stack |
| `aload` / `astore` | Nạp/lưu giá trị reference trong biến cục bộ |
| `new` | Tạo đối tượng của kiểu lớp phù hợp |
| `newarray` | Tạo mảng có phần tử primitive |
| `anewarray` | Tạo mảng có phần tử reference |
| `multianewarray` | Tạo mảng nhiều chiều theo số chiều được chỉ định |
| `aaload` / `aastore` | Đọc/ghi phần tử reference của một mảng |
| `iaload` / `iastore` | Đọc/ghi phần tử `int` của mảng |
| `arraylength` | Lấy độ dài mảng |
| `checkcast` | Kiểm tra ép kiểu reference tại runtime |
| `instanceof` | Kiểm tra đối tượng có tương thích một reference type hay không |
| `ifnull` / `ifnonnull` | Nhảy theo điều kiện reference null/khác null |
| `if_acmpeq` / `if_acmpne` | So sánh bằng/khác của hai giá trị reference |
| `invokevirtual` / `invokeinterface` | Thực hiện các dạng lời gọi phương thức instance theo cơ chế thích hợp |

Cùng là **reference**, nhưng JVM giữ đủ thông tin kiểu và kiểm tra để không thể tùy tiện coi một mảng `int[]` là `Object[]`. Khi `aastore` chạy, giá trị được lưu phải tương thích với kiểu thành phần **thực tế** của mảng, nếu không sẽ ném `ArrayStoreException`.

`checkcast` có một chi tiết dễ quên: với null reference, nó vẫn **thành công** và giữ null. Còn `instanceof` trả kết quả false với null. Do đó cả hai opcode đều xử lý null khác với việc cố dereference null.

### 10.3. Tự quan sát bytecode bằng công cụ JDK

Sau khi biên dịch chương trình hoàn chỉnh:

```bash
javap -c -p ReferenceTypesDemo
```

`javap` in bytecode các phương thức, trong đó có `main`. Có thể tìm các tên opcode như `instanceof`, `anewarray`, `aastore`, `newarray` và `arraylength` tương ứng với từng phép toán trong mã nguồn. Với lớp nested, lớp thực thi interface sẽ được sinh ra thành class file riêng, ví dụ `ReferenceTypesDemo$Student.class` tùy môi trường và cách liệt kê tên tệp.

**Không dựa vào offset bytecode hoặc thứ tự mọi instruction như một API cố định.** Compiler có thể thay đổi việc biên dịch giữa các phiên bản; các tối ưu thực thi JIT cũng không phải là văn bản bytecode mà `javap -c` hiển thị.

### 10.4. Ba nhóm reference types liên hệ thế nào với kiểm tra kiểu?

- **Class type:** quan hệ lớp con/lớp cha và đối tượng runtime chi phối assignability, cast và dispatch phương thức.
- **Interface type:** runtime kiểm tra đối tượng có hiện thực interface phù hợp; lời gọi có thể liên kết tới implementation của lớp runtime.
- **Array type:** vừa là đối tượng, vừa mang kiểu thành phần và số chiều; JVM phải kiểm tra phép gán và ghi phần tử theo các quy tắc runtime thích hợp.

Vì **`null` không có đối tượng runtime**, nó có thể đi qua nhiều phép gán/cast reference mà không tạo ra một thông tin class runtime cụ thể.

## 11. Các lỗi thường gặp trong thiết kế và debug

### 11.1. Nhầm `null` với giá trị mặc định nghiệp vụ

Một đối tượng `User` có trường `String email = null` không có nghĩa email là chuỗi rỗng; cũng không tự chứng minh “chưa bao giờ gán”, vì một chương trình có thể gán null chủ động. Hợp đồng nghiệp vụ phải tự định nghĩa ý nghĩa của việc thiếu giá trị.

### 11.2. Gọi `equals` trên reference chưa kiểm tra

```java
String input = null;
// input.equals("yes"); // NullPointerException
System.out.println("yes".equals(input)); // false
```

Đảo phía literal có thể hữu ích trong trường hợp đơn giản; với các API khác, nên dùng `Objects.equals(a, b)` khi cần so sánh null-safe. Không nhầm `==` (kiểm tra **cùng tham chiếu**) với `equals` (hợp đồng so sánh do lớp định nghĩa).

### 11.3. Dùng `instanceof` để nghĩ rằng null có một kiểu runtime cụ thể

`String s = null` nói lên **kiểu khai báo** của biến `s`; không có đối tượng String đang tồn tại từ giá trị `s`. `s instanceof String` là false, và `s.getClass()` ném `NullPointerException`.

### 11.4. Ép kiểu mà không kiểm tra loại đối tượng

`Object payload` có thể tham chiếu `String`, `Integer`, mảng hoặc một loại đối tượng khác. Ép `(String) payload` chỉ hợp lệ tại runtime nếu payload null hoặc là đối tượng tương thích `String`. Có thể dùng `instanceof` khi thật sự cần phân nhánh; trong thiết kế API, ưu tiên khai báo kiểu chính xác để giảm cast không cần thiết.

### 11.5. Cho rằng mọi mảng là `Object[]`

`int[]` cũng là Object, nhưng không phải `Object[]`. `Object[]` dùng phần tử reference. Với mảng primitive, chọn đúng kiểu mảng và các overload API tương ứng (ví dụ `Arrays.toString(int[])` khác overload `Arrays.toString(Object[])`).

### 11.6. Quên kiểm tra kiểu khi ghi qua biến mảng cha

`Object[] arr = new String[1]` vẫn trỏ tới mảng String. Biến có kiểu `Object[]` **không thay đổi component type runtime**. Kiểm tra ghi mảng có thể ném `ArrayStoreException`.

### 11.7. Nhầm mảng nhiều chiều với một khối dữ liệu hai chiều cố định

`int[][]` trong Java là mảng chứa tham chiếu tới các mảng `int[]`. Số cột giữa các hàng có thể khác nhau, và một hàng có thể bằng null. Khi duyệt cần kiểm tra hàng null nếu dữ liệu có thể ở trạng thái này.

### 11.8. Nhầm null với giá trị không có trong generic collection

`List<String>` là collection tham chiếu; nhiều triển khai cho phép phần tử null, nhưng không phải mọi collection đều cho phép. Hợp đồng của từng API quyết định. Không dùng `null` làm dấu hiệu “không tìm thấy” nếu cách đó gây mơ hồ giữa “không có kết quả” và “có kết quả với giá trị null”; có thể cân nhắc kiểu kết quả rõ ràng như `Optional` trong các API thích hợp.

### 11.9. Cho rằng mọi lỗi null đều được compiler bắt

Trình biên dịch Java kiểm tra nhiều vấn đề kiểu và definite assignment nhưng không bảo đảm mọi tham chiếu khác null. Java 17 không tự động suy luận không-null cho mọi lối đi của chương trình. Kiểm thử, ràng buộc API và phân tích tĩnh có thể giúp giảm lỗi.

## 12. Bài tập tự kiểm tra có đáp án

**Câu 1.** `int[] a = null;` hợp lệ không? `int a = null;` thì sao?

**Đáp án:** Khai báo thứ nhất hợp lệ vì `int[]` là array/reference type. Khai báo thứ hai không hợp lệ vì `int` là primitive.

**Câu 2.** Vì sao `new String[3]` không bằng `null` dù mọi phần tử ban đầu đều null?

**Đáp án:** Biểu thức tạo **một đối tượng mảng có thật** với độ dài 3; giá trị tham chiếu tới mảng khác null. Chỉ **ba phần tử** của mảng tham chiếu khởi đầu bằng null.

**Câu 3.** Biến `Runnable task = null;` có nghĩa tồn tại một instance của một lớp hiện thực `Runnable` không?

**Đáp án:** Không. `Runnable` là kiểu khai báo; giá trị null không tham chiếu instance nào. `task instanceof Runnable` trả false.

**Câu 4.** Đoạn `Object[] items = new String[1]; items[0] = 7;` có biên dịch không? Chạy ra sao?

**Đáp án:** Biên dịch được nhờ boxing `7` thành `Integer` và khả năng dùng `String[]` như `Object[]`. Khi chạy, thao tác ghi `Integer` vào mảng thực tế `String[]` ném `ArrayStoreException`.

**Câu 5.** `Object data = new int[2];` và `Object[] data = new int[2];` có cùng tính hợp lệ không?

**Đáp án:** Không. Dòng thứ nhất hợp lệ vì mảng là object; dòng thứ hai sai kiểu vì `int[]` không phải `Object[]`.

**Câu 6.** Vì sao `(String) null` hợp lệ mà `((String) null).length()` thất bại?

**Đáp án:** `checkcast` với null reference cho kết quả null; `length()` là lời gọi instance cần đối tượng khác null, nên ném `NullPointerException`.

**Câu 7.** `String[][] values = new String[2][];` có bao nhiêu đối tượng mảng mới ngay sau dòng đó?

**Đáp án:** Có **một đối tượng mảng ngoài** với hai phần tử kiểu `String[]`, ban đầu cả hai là null. Chưa có mảng hàng nào được tạo.

**Câu 8.** Trong ba loại JVM reference type, `String[]` thuộc loại nào, còn `Runnable` thuộc loại nào?

**Đáp án:** `String[]` là **array type**; `Runnable` là **interface type**. `String` là **class type**.

## 13. Ghi nhớ cốt lõi

- JVM phân biệt **primitive** và **reference**; ba nhóm reference type theo JVMS là **class**, **interface** và **array**.
- Biến tham chiếu có thể chứa null, tức **không trỏ tới đối tượng**. JLS có khái niệm **null type** của biểu thức `null`, nhưng đó không phải một nhóm class/interface/array bổ sung của JVM.
- **Kiểu tĩnh** kiểm soát thao tác hợp lệ lúc biên dịch; **kiểu runtime** của đối tượng quyết định kết quả kiểm tra cast/`instanceof` và dispatch tương ứng.
- **Interface reference** có thể trỏ tới instance của lớp hiện thực interface; bản thân interface thông thường không được khởi tạo trực tiếp bằng `new`.
- **Mảng là object** bất kể phần tử primitive hay reference. Mảng reference có slot mặc định null, mảng primitive có giá trị mặc định riêng; mảng nhiều chiều là mảng của các mảng.
- **Array covariance** giúp `String[]` dùng như `Object[]`, nhưng runtime kiểm tra phép ghi và có thể ném `ArrayStoreException`.
- `null` có thể gán, so sánh, cast; chỉ các thao tác yêu cầu đối tượng thật mới có nguy cơ `NullPointerException`.

### Tài liệu đặc tả

- [Java Virtual Machine Specification §2.4: Reference Types and Values (Java SE 21)](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-2.html#jvms-2.4) — phân loại reference types và null reference.
- [JVM Specification §4.3: Descriptors (Java SE 21)](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.3) — descriptor của class, interface và array.
- [JVM Specification §6: JVM Instruction Set (Java SE 21)](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-6.html) — các opcode liên quan như `aastore`, `checkcast`, `instanceof`.
- [Java Language Specification §4: Types, Values, and Variables (Java SE 21)](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html) — reference types, null type, biến và kiểu.
- [Java Language Specification §10: Arrays (Java SE 21)](https://docs.oracle.com/javase/specs/jls/se21/html/jls-10.html) — quy tắc mảng, kiểu phần tử và array store checks.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-data-types-005">Số nguyên tràn, phép chuyển kiểu và ngữ nghĩa phép toán bytecode</a>

<details>
<summary>Xem chi tiết</summary>

# Số nguyên tràn, phép chuyển kiểu và ngữ nghĩa phép toán bytecode

Trong Java, một phép cộng `int` có thể cho ra số âm dù hai toán hạng đều dương; ép `int` về `byte` có thể đổi cả dấu lẫn giá trị; và ép sang `long` **sau** khi phép tính đã tràn không lấy lại được phần thông tin mất đi. Đây là **ngữ nghĩa được quy định** bởi Java Language Specification (JLS) và Java Virtual Machine Specification (JVMS), không phải lỗi ngẫu nhiên của CPU hay HotSpot.

Mục tiêu của bài: hiểu chính xác số nguyên JVM được biểu diễn và xử lý như thế nào; theo dõi **kiểu của từng toán hạng → phép toán → kết quả → phép chuyển kiểu**; đọc các lệnh bytecode tương ứng; và chọn cách phát hiện tràn khi dữ liệu không được phép mất.

## 1. Bức tranh số nguyên ở Java và ở JVM

### Kiểu dữ liệu, độ rộng và miền giá trị

| Kiểu Java | Số bit | Miền giá trị | Đặc điểm |
| --- | ---: | --- | --- |
| `byte` | 8 | -128 đến 127 | Số nguyên có dấu |
| `short` | 16 | -32.768 đến 32.767 | Số nguyên có dấu |
| `char` | 16 | 0 đến 65.535 | Giá trị nguyên *không dấu*, thường đại diện một UTF-16 code unit |
| `int` | 32 | -2.147.483.648 đến 2.147.483.647 | Kiểu nguyên thông dụng trong phép toán JVM |
| `long` | 64 | -9.223.372.036.854.775.808 đến 9.223.372.036.854.775.807 | Kiểu nguyên 64 bit có dấu |

`boolean` là kiểu riêng, không phải số nguyên và không thể cộng, ép số bằng numeric cast. `float`/`double` là kiểu dấu phẩy động với ngữ nghĩa IEEE 754, không sử dụng cùng phép tràn dạng modulo của `int`/`long`.

Giá trị nguyên có dấu được diễn giải theo **two's complement (bù hai)**. Ví dụ, mẫu 32 bit `0xFFFF_FFFF` biểu thị `-1` khi xem là `int` có dấu; cùng mẫu bit có thể được API unsigned đọc thành `4.294.967.295`. Đổi cách diễn giải **không đổi** số bit lưu trữ.

Trong **operand stack** của JVM, nhiều thao tác trên `byte`, `short`, `char` và `boolean` sử dụng loại tính toán `int`; kiểu khai báo nhỏ vẫn tồn tại trong mô tả field, array, method signature và các bước thu hẹp/chuyển về kiểu đích. JVM không có đầy đủ một họ lệnh cộng riêng `badd`, `sadd`, `cadd` cho ba kiểu nhỏ. `long` có họ lệnh riêng `ladd`, `lsub`, `lmul`, v.v.

Ví dụ suy luận đúng phải hỏi: “Tại **thời điểm thực hiện phép cộng**, toán hạng đang là `int` hay `long`?”, thay vì chỉ xem kiểu của biến nhận kết quả.

## 2. Tràn `int` và `long` là quy tắc xác định

Với toán tử nguyên như `+`, `-`, `*`, phép đổi dấu `-x`, Java giữ lại **32 bit thấp** của kết quả toán học nếu phép tính có kiểu `int`, hoặc **64 bit thấp** nếu có kiểu `long`. Sau đó các bit này được diễn giải bằng two's complement.

Có thể hình dung:

- Tính tổng/tích toán học đủ độ rộng.
- Lấy kết quả theo modulo `2^32` cho `int` hoặc `2^64` cho `long`.
- Diễn giải mẫu bit kết quả là số **có dấu** của kiểu tương ứng.

Ví dụ:

```java
int largest = Integer.MAX_VALUE;  // 2_147_483_647
int wrapped = largest + 1;        // -2_147_483_648
long wide = Long.MAX_VALUE + 1;    // -9_223_372_036_854_775_808
```

Ba dòng trên là **trích đoạn minh họa**, không phải file độc lập. Phép `largest + 1` có kiểu `int`, kết quả toán học `2_147_483_648` vượt `int`, mẫu 32 bit thấp là `0x8000_0000`, được diễn giải thành `Integer.MIN_VALUE`.

| Phép toán số nguyên | Hành vi tràn | Exception do *tràn*? |
| --- | --- | --- |
| `iadd`/`ladd`; `isub`/`lsub`; `imul`/`lmul` | Cắt còn 32/64 bit thấp | Không |
| `ineg`/`lneg`, ví dụ `-Integer.MIN_VALUE` | Vẫn là `Integer.MIN_VALUE` / `Long.MIN_VALUE` | Không |
| `iinc` với biến cục bộ `int` | Cộng hằng số và giữ 32 bit thấp | Không |
| Chia nguyên `idiv`/`ldiv` với số chia `-1` và số bị chia nhỏ nhất | Kết quả vẫn bằng `MIN_VALUE` | Không |
| Chia hoặc lấy dư nguyên với số chia `0` | Không có kết quả nguyên hợp lệ | **`ArithmeticException`** |
| Chuyển kiểu thu hẹp `i2b`, `i2s`, `i2c`, `l2i` | Mất các bit cao | Không, kể cả mất giá trị |
| `Math.addExact`, `Math.multiplyExact`, v.v. | Kiểm tra tràn theo hợp đồng API | **`ArithmeticException`** khi tràn |

Không có chế độ mặc định như “checked arithmetic” buộc mọi lệnh `iadd` phải ném lỗi khi tràn. Compiler hoặc JVM có thể tối ưu cách thực thi, nhưng **kết quả quan sát được** của biểu thức nguyên vẫn phải tuân theo ngữ nghĩa Java.

### Điểm dễ nhầm: tràn không phải phép cộng dấu phẩy động

`double x = Double.MAX_VALUE * 2;` có thể thành `Infinity` thay vì quấn về một số âm. `double a = 1.0 / 0.0;` trả `Infinity`, trong khi `int a = 1 / 0;` có thể bị phát hiện ngay ở biểu thức hằng hoặc gây `ArithmeticException` khi thực hiện ở runtime; để quan sát hành vi runtime, dùng biến số chia. Không áp dụng quy tắc `iadd` cho `fadd`, `dadd` hoặc `ddiv`.

## 3. Khi chuyển sang `long`: thời điểm chuyển kiểu quyết định đúng/sai

Xét phép nhân hai giá trị:

```java
int amount = 1_500_000_000;
long wrong = amount * 2;
long correct = (long) amount * 2;
```

- `amount * 2` được tính dưới kiểu `int`, tràn thành **`-1_294_967_296`**; gán vào `long` chỉ sign-extend giá trị âm *đã sai*.
- `(long) amount * 2` chuyển toán hạng đầu sang `long` **trước khi nhân**, nên phép nhân là `long` và cho **`3_000_000_000`**.

Cùng vấn đề với dấu ngoặc:

```java
int max = Integer.MAX_VALUE;
long tooLate = (long) (max + 1); // -2147483648
long inTime = (long) max + 1;    //  2147483648
```

**Quy tắc:** kiểu của **phép tính đang diễn ra** quyết định độ rộng; kiểu của biến bên trái dấu `=` không có khả năng sửa một phép tính đã tràn.

Đổi sớm sang `long` giúp giảm nguy cơ nhưng `long` cũng có giới hạn 64 bit và vẫn tràn nếu vượt miền giá trị. Với số tiền lớn, bộ đếm an toàn, kích thước dữ liệu, hoặc phép nhân nhiều bước, cần xác định rõ miền đầu vào và có thể dùng `Math.*Exact` hay `BigInteger`.

### Promotion cho kiểu nhỏ

Với các phép số học hai toán hạng thường gặp (`+`, `-`, `*`, `/`, `%`), `byte`, `short`, `char` được **binary numeric promotion** về ít nhất `int` trước khi tính. Ví dụ:

```java
byte a = 100;
byte b = 20;
// byte c = a + b; // LỖI biên dịch: a+b có kiểu int
int sum = a + b;   // đúng, sum = 120
byte c = (byte) (a + b); // đúng cú pháp, nhưng có thể mất dữ liệu
```

Ngoại lệ đáng nhớ ở ngữ cảnh gán:

- `byte b = 127;` biên dịch được: hằng `int` biểu diễn vừa `byte` được phép thu hẹp theo quy tắc constant assignment.
- `byte b = 128;` lỗi biên dịch: hằng ngoài miền `byte`.
- `byte b = 127; b++;` hợp lệ; phép tăng có bước chuyển về kiểu `byte` và kết quả thành `-128`.
- `byte b = 127; b += 1;` cũng hợp lệ; phép gán kết hợp có phép cast ngầm về kiểu bên trái. Còn `b = b + 1;` cần cast tường minh vì kết quả phép cộng là `int`.

Việc hằng được compiler chấp nhận không làm mất quy tắc tràn runtime. Tương tự, một biểu thức hằng nguyên có thể được compiler tính trước nhưng vẫn phải giữ quy tắc overflow của kiểu nguyên.

## 4. Chuyển kiểu nguyên: giữ bit thấp, sau đó xác định có dấu hay không

Phép **widening** từ kiểu nguyên hẹp sang kiểu nguyên rộng hơn:

- `byte`/`short`/`int` là số có dấu; mở rộng sang kiểu có nhiều bit hơn bằng **sign extension**.
- `char` không dấu; mở rộng sang `int`/`long` bằng **zero extension**.
- Widening từ số nguyên sang số nguyên rộng hơn bảo toàn **giá trị**; ngược lại, widening `int → float` hoặc `long → double` có thể mất **độ chính xác**, vì dấu phẩy động có số bit phần trị hữu hạn.

Phép **narrowing** từ kiểu nguyên rộng sang kiểu hẹp:

- `int → byte` giữ 8 bit thấp; `int → short`/`char` giữ 16 bit thấp.
- `long → int` giữ 32 bit thấp; chuyển `long` sang `byte`/`short`/`char` có thể tương ứng hai bước thu hẹp trong bytecode.
- Kiểu đích `byte` và `short` đọc bit dấu theo two's complement; `char` xem 16 bit kết quả là một số **không dấu**.
- Chuyển kiểu nguyên **không tự kiểm tra** giá trị có vừa đích không. `(byte) 130` là `-126`, không ném exception.

Các ví dụ từng bit:

| Biểu thức | Phần bit còn giữ | Giá trị |
| --- | --- | ---: |
| `(byte) 130` | `0x82` (8 bit) | -126 |
| `(byte) 255` | `0xFF` (8 bit) | -1 |
| `(short) 65535` | `0xFFFF` (16 bit) | -1 |
| `(char) (-1)` | `0xFFFF` (16 bit) | 65535 |
| `(int) 0x1_0000_0001L` | `0x0000_0001` (32 bit) | 1 |
| `(long) (byte) 255` | `0xFFFF_FFFF_FFFF_FFFF` | -1 |

Điều cuối cùng thể hiện **thu hẹp rồi mới mở rộng**: cast sang `byte` biến `255` thành `-1`, mở sang `long` chỉ bảo toàn `-1`, không lấy lại `255`. Nếu nhận byte thô của giao thức cần hiểu theo khoảng 0–255, thường dùng `value & 0xFF` khi `value` có kiểu `byte` đã được promote sang `int`, hoặc `Byte.toUnsignedInt(value)`.

### Ví dụ Java 17: chuyển kiểu và mất bit

Tạo **`NarrowingConversions.java`**:

```java
public class NarrowingConversions {
    public static void main(String[] args) {
        System.out.println("byte130=" + (byte) 130);
        System.out.println("byte255=" + (byte) 255);
        System.out.println("short65535=" + (short) 65535);
        System.out.println("charMinus1=" + (int) (char) -1);
        System.out.println("longToInt=" + (int) 0x1_0000_0001L);

        byte counter = 127;
        counter++;
        System.out.println("counter=" + counter);

        byte raw = (byte) 255;
        System.out.println("unsignedByte=" + Byte.toUnsignedInt(raw));
    }
}
```

Biên dịch, chạy:

```bash
javac --release 17 NarrowingConversions.java
java NarrowingConversions
```

**Stdout chính xác**:

```text
byte130=-126
byte255=-1
short65535=-1
charMinus1=65535
longToInt=1
counter=-128
unsignedByte=255
```

Điểm chính: `byte`, `short` và `char` có cùng khả năng mất bit cao khi narrowing, nhưng diễn giải bit dấu **khác nhau**. `Byte.toUnsignedInt` chỉ đổi cách biểu diễn giá trị sang `int` dương, không thay đổi byte gốc.

## 5. Chuyển `float`/`double` sang số nguyên không chỉ đơn giản là “giữ bit thấp”

Java có quy tắc hai giai đoạn với narrowing từ dấu phẩy động sang kiểu nguyên:

1. Chuyển về `int` hoặc `long` bằng cách **làm tròn về 0** nếu là số hữu hạn và nằm trong miền biểu diễn. Giá trị NaN cho **0**; quá lớn/+∞ cho `MAX_VALUE`; quá nhỏ/−∞ cho `MIN_VALUE` tương ứng.
2. Nếu kiểu đích là `byte`, `short` hay `char`, tiếp tục **narrowing** từ `int` theo quy tắc giữ bit thấp.

Điều này khác việc “ép `double` thẳng về `byte` bằng cách chỉ lấy 8 bit của phần thập phân”. Ví dụ `(byte) 130.5`: trước hết `(int) 130.5 = 130`, rồi `(byte) 130 = -126`.

Tạo file **`FloatingToInteger.java`**:

```java
public class FloatingToInteger {
    public static void main(String[] args) {
        System.out.println("positive=" + (int) 3.9);
        System.out.println("negative=" + (int) -3.9);
        System.out.println("NaN=" + (int) Double.NaN);
        System.out.println("plusInfinity=" + (int) Double.POSITIVE_INFINITY);
        System.out.println("minusInfinity=" + (int) Double.NEGATIVE_INFINITY);
        System.out.println("doubleToByte=" + (byte) 130.5);
    }
}
```

```bash
javac --release 17 FloatingToInteger.java
java FloatingToInteger
```

**Stdout**:

```text
positive=3
negative=-3
NaN=0
plusInfinity=2147483647
minusInfinity=-2147483648
doubleToByte=-126
```

Cần hiểu kỹ: ép `double` sang `int` **không ném `ArithmeticException`** vì giá trị vượt miền; đó là lý do rất nguy hiểm nếu dữ liệu đầu vào phải chính xác. Nếu ứng dụng cần phát hiện giá trị NaN/vô hạn, ngoài miền hoặc có phần lẻ, hãy kiểm tra tường minh *trước khi cast*.

Ngược chiều `int → float` cũng có thể làm tròn mất chính xác, dù nó được gọi là **widening primitive conversion** theo hệ thống kiểu. Ví dụ `(float) 16_777_217` thành `16_777_216.0` do số bit phần trị của `float`; “widening” không luôn có nghĩa “mọi bit giá trị được giữ nguyên” khi đổi từ nguyên sang dấu phẩy động.

## 6. Phép chia nguyên, dấu của số dư và trường hợp cực trị

Java chia hai số nguyên với thương **làm tròn về 0** (truncation toward zero), không phải làm tròn xuống âm vô cùng:

| Biểu thức | Giá trị |
| --- | ---: |
| `7 / 3` | 2 |
| `-7 / 3` | -2 |
| `7 / -3` | -2 |
| `-7 % 3` | -1 |
| `7 % -3` | 1 |
| `Integer.MIN_VALUE / -1` | -2.147.483.648 |
| `Integer.MIN_VALUE % -1` | 0 |

Số dư khác 0 có cùng dấu với **số bị chia**. Vì thế `-7 % 3` là `-1`, không phải `2`. Nếu cần phép chia theo hướng floor (ví dụ phép tính ngày tháng hoặc chia nhóm với số âm), xem `Math.floorDiv` và `Math.floorMod`; đừng thay đổi quy tắc của toán tử `/`, `%` bằng suy luận toán học thiếu phần làm tròn.

**Trường hợp đặc biệt**: `Integer.MIN_VALUE / -1` có thương toán học `2147483648` không thể biểu diễn bởi `int`. Theo JLS/JVMS, kết quả là **`Integer.MIN_VALUE`**, không ném overflow exception. Tương tự với `Long.MIN_VALUE / -1L`.

Chia nguyên cho **0** làm phát sinh `ArithmeticException`; ví dụ runtime hợp lệ:

```java
int divisor = 0;
try {
    System.out.println(42 / divisor);
} catch (ArithmeticException ex) {
    System.out.println("division by zero");
}
```

Đây là **đoạn minh họa trong một method**, không phải chương trình độc lập. Output của `catch` là `division by zero`. Với `double`, `42.0 / 0.0` cho `Infinity`, khác quy tắc chia số nguyên. Lệnh bytecode tương ứng là `idiv`/`ldiv` so với `ddiv`/`fdiv`.

## 7. Phép dịch bit không phải phép nhân/chia vô hạn độ rộng

Các toán tử dịch làm việc trên mẫu bit có kích thước cố định:

- `<<` dịch trái, đưa số 0 vào phần bit thấp. Bit cao bị loại bỏ, nên có thể đổi dấu hoặc mất dữ liệu.
- `>>` dịch phải **có dấu**: đưa bản sao bit dấu vào bên trái (sign extension).
- `>>>` dịch phải **không dấu**: đưa bit 0 vào bên trái (zero fill); nó không đổi kiểu Java thành unsigned.
- Với toán hạng trái kiểu `int`, khoảng dịch chỉ dùng **5 bit thấp** của khoảng dịch: hiệu dụng là `distance & 0x1F` (modulo 32).
- Với toán hạng trái kiểu `long`, chỉ dùng **6 bit thấp**: `distance & 0x3F` (modulo 64).
- Các `byte`/`short`/`char` ở vế trái được **unary numeric promotion** lên `int` trước khi dịch; kiểu vế phải không khiến kết quả của `int << 1L` trở thành `long`.

Ví dụ:

```java
System.out.println(1 << 32);   // 1: 32 & 31 = 0
System.out.println(1L << 64);  // 1: 64 & 63 = 0
System.out.println(-1 >> 1);   // -1
System.out.println(-1 >>> 1);  // 2147483647
```

Có thể nhìn thấy lệnh bytecode tương ứng: `ishl`/`lshl`, `ishr`/`lshr`, `iushr`/`lushr`. Khi giải mã một trường nhị phân, checksum hoặc màu ARGB, luôn quy định **độ rộng kiểu** và thao tác mask cụ thể; một shift vượt 31 cho `int` không có nghĩa tất cả bit đều biến mất.

### Unsigned là cách diễn giải, không phải kiểu Java mới

`int` vẫn là kiểu có dấu 32 bit; Java cung cấp các API như `Integer.compareUnsigned`, `Integer.divideUnsigned`, `Integer.toUnsignedLong`, `Integer.toUnsignedString`. Ví dụ `Integer.toUnsignedLong(-1)` là `4294967295L`. Dịch `>>>` cũng chỉ thao tác trên 32/64 bit hiện có; nó không làm một biến `int` có thêm miền lưu trữ.

## 8. Từ Java source đến lệnh JVM: nhìn chính xác chuỗi tính và cast

JVM thường thực thi logic theo **operand stack**: nạp giá trị, đặt toán hạng lên stack, một opcode lấy toán hạng rồi đẩy kết quả, sau đó có thể cast hoặc return. Với `int`, một giá trị trên stack có loại `int`; với `long`, dùng loại `long`.

Bảng một số opcode cốt lõi trong JVMS 17:

| Opcode | Ý nghĩa | Cần chú ý |
| --- | --- | --- |
| `iadd`, `isub`, `imul`; `ladd`, `lsub`, `lmul` | Cộng, trừ, nhân `int`/`long` | Wrap 32/64 bit; không exception do tràn |
| `ineg`/`lneg` | Đổi dấu số nguyên | `-MIN_VALUE == MIN_VALUE` |
| `iinc` | Tăng biến cục bộ `int` một hằng có dấu | Wrap; opcode này làm việc với local variable, không phải mọi biến/đối tượng |
| `idiv`/`ldiv`; `irem`/`lrem` | Chia, lấy dư `int`/`long` | Số chia 0 ném `ArithmeticException` |
| `i2l` | `int → long` | Sign extension; bản thân phép mở rộng là chính xác |
| `l2i` | `long → int` | Giữ 32 bit thấp |
| `i2b`, `i2s` | `int → byte/short` | Giữ 8/16 bit rồi **sign-extend trở lại int trên stack** |
| `i2c` | `int → char` | Giữ 16 bit rồi **zero-extend trở lại int trên stack** |
| `f2i`, `d2i`, `f2l`, `d2l` | Floating-point → integral | Làm tròn về 0; NaN về 0; giá trị ngoài miền bị clamp |
| `ishl`, `ishr`, `iushr` | Dịch `int` | Chỉ dùng 5 bit thấp của khoảng dịch |
| `lshl`, `lshr`, `lushr` | Dịch `long` | Chỉ dùng 6 bit thấp của khoảng dịch |

Cần đặc biệt phân biệt **kiểu giá trị bytecode operand stack** với **kiểu Java của kết quả biểu thức/biến**. Ví dụ `i2b` không tạo ra kiểu operand stack riêng “byte”: theo mô tả instruction, nó thu hẹp về 8 bit rồi sign-extend thành **int** trên stack; code sinh ra vẫn đảm bảo giá trị đáp ứng phạm vi `byte`.

### Chương trình độc lập có thể dịch ngược bằng `javap`

Tạo file **`IntegerBytecode.java`**:

```java
public class IntegerBytecode {
    public static int add(int a, int b) {
        return a + b;
    }

    public static byte narrow(int value) {
        return (byte) value;
    }

    public static long tooLate(int a, int b) {
        return (long) (a + b);
    }

    public static long inTime(int a, int b) {
        return (long) a + b;
    }

    public static int unsignedShift(int value, int bits) {
        return value >>> bits;
    }

    public static int divide(int a, int b) {
        return a / b;
    }

    public static void main(String[] args) {
        int max = Integer.MAX_VALUE;
        System.out.println("add=" + add(max, 1));
        System.out.println("narrow=" + narrow(130));
        System.out.println("tooLate=" + tooLate(max, 1));
        System.out.println("inTime=" + inTime(max, 1));
        System.out.println("unsignedShift=" + unsignedShift(-1, 1));
        System.out.println("divide=" + divide(-7, 3));
    }
}
```

Lệnh với **JDK 17+ có hỗ trợ `--release 17`**:

```bash
javac --release 17 IntegerBytecode.java
java IntegerBytecode
javap -c -p IntegerBytecode
```

**Stdout của lệnh `java IntegerBytecode`**:

```text
add=-2147483648
narrow=-126
tooLate=-2147483648
inTime=2147483648
unsignedShift=2147483647
divide=-2
```

Với lệnh `javap -c -p IntegerBytecode`, bỏ qua địa chỉ offset và lệnh tải/trả về cần thiết, chuỗi opcode chính trong các method có dạng:

```text
add:           iload_0, iload_1, iadd, ireturn
narrow:        iload_0, i2b, ireturn
tooLate:       iload_0, iload_1, iadd, i2l, lreturn
inTime:        iload_0, i2l, iload_1, i2l, ladd, lreturn
unsignedShift: iload_0, iload_1, iushr, ireturn
divide:        iload_0, iload_1, idiv, ireturn
```

Đây là **lược đồ các opcode đáng chú ý**, không phải nguyên văn toàn bộ output `javap`. Compiler có thể chọn sequence khác mà vẫn tuân thủ Java; đầu ra `javap` sẽ chứa offsets và cả method `main`.

Điểm mấu chốt: ở `tooLate`, opcode `iadd` thực hiện **trước** `i2l`; khi `i2l` chạy, phép cộng đã wrap. Ở `inTime`, mỗi toán hạng được mở rộng bằng `i2l`, sau đó `ladd` mới cộng hai `long`, nên `2147483648` không vượt miền 64 bit.

### Theo dõi `iadd` trên operand stack

Ví dụ gọi `add(2147483647, 1)` (dưới đây chỉ là phần liên quan của frame method `add`):

| Thời điểm | Operand stack, từ đáy đến đỉnh | Giải thích |
| --- | --- | --- |
| Trước lệnh đầu | `[]` | `a` và `b` nằm trong local variables |
| Sau `iload_0` | `[2147483647]` | Nạp `a` loại `int` |
| Sau `iload_1` | `[2147483647, 1]` | Nạp `b` loại `int` |
| Sau `iadd` | `[-2147483648]` | Cộng 32 bit, giữ bit thấp theo two's complement |
| Sau `ireturn` | Stack của frame kết thúc | Trả kết quả `int` về caller |

Nếu đọc bytecode và nhìn thấy `i2l` *sau* `iadd`, không được tự suy luận đó là phép cộng `long` chỉ vì method trả `long`.

### `javap` giúp thấy cơ chế, nhưng không phải cam kết source-to-opcode một đối một

- Một phép `a++` có thể được compiler thể hiện bằng `iinc` khi là biến cục bộ `int` phù hợp; tăng field/array có thể cần các opcode load, add, store khác.
- Compiler được phép constant-fold biểu thức hằng, nên không phải dòng source nào chứa `+` cũng sẽ sinh ra `iadd`.
- JIT có thể thay đổi lệnh CPU thực thi, nhưng kết quả quan sát được của phép toán phải giữ đúng contract JVM/Java.

Hãy coi `javap -c` là cách soi **bytecode cụ thể** được compiler tạo ra, còn JLS/JVMS là nguồn xác định **ngữ nghĩa bắt buộc**.

## 9. Khi tràn là lỗi: kiểm tra bằng API và kiểm tra miền

Nếu yêu cầu nghiệp vụ không chấp nhận wrap, hãy phát hiện trước/sau theo API thay vì hy vọng JVM tự báo lỗi:

- `Math.addExact(a, b)`, `subtractExact`, `multiplyExact`: thực hiện phép tính nguyên có kiểm tra tràn, ném `ArithmeticException` nếu kết quả vượt miền.
- `Math.incrementExact`, `decrementExact`, `negateExact`: kiểm tra tràn các phép tương ứng; ví dụ `Math.negateExact(Integer.MIN_VALUE)` sẽ ném lỗi.
- `Math.toIntExact(longValue)`: thu hẹp `long → int` có kiểm tra phạm vi thay cho cast âm thầm loại 32 bit cao.
- Với kết quả có thể vượt `long`, xem `BigInteger` hoặc mô hình dữ liệu thích hợp; với giá trị tiền có phần thập phân, cần xem xét thêm `BigDecimal`/quy ước số đơn vị nhỏ nhất.

Tạo **`CheckedArithmetic.java`**:

```java
public class CheckedArithmetic {
    public static void main(String[] args) {
        try {
            System.out.println(Math.addExact(Integer.MAX_VALUE, 1));
        } catch (ArithmeticException ex) {
            System.out.println("add overflow detected");
        }

        try {
            System.out.println(Math.toIntExact(3_000_000_000L));
        } catch (ArithmeticException ex) {
            System.out.println("narrowing overflow detected");
        }

        System.out.println("valid=" + Math.multiplyExact(6, 7));
    }
}
```

```bash
javac --release 17 CheckedArithmetic.java
java CheckedArithmetic
```

**Stdout**:

```text
add overflow detected
narrowing overflow detected
valid=42
```

Hai lỗi trên được báo vì ta **gọi API checked**, không phải JVM phát hiện overflow mặc định của `iadd` hay `l2i`.

### Kiểm tra giới hạn không tạo overflow ngay trong biểu thức kiểm tra

Cảnh báo phổ biến khi kiểm tra lát cắt mảng, offset hoặc bộ đệm:

```java
// Có thể sai nếu offset + length bị tràn int:
if (offset + length > capacity) {
    throw new IllegalArgumentException("out of bounds");
}
```

Đây là đoạn trích chưa đủ điều kiện an toàn nếu các giá trị không được kiểm soát. Với các giá trị `int` đại diện offset/length/capacity không âm, kiểm tra an toàn hơn bằng cách loại đầu vào âm và giữ phép trừ trong miền:

```java
if (capacity < 0 || offset < 0 || length < 0
        || offset > capacity || length > capacity - offset) {
    throw new IllegalArgumentException("out of bounds");
}
```

Do `offset <= capacity` được kiểm tra trước vế trừ, `capacity - offset` không âm và không tràn trong miền `int` không âm. Điều kiện `||` short-circuit từ trái sang phải nên phép trừ chỉ được đánh giá khi điều kiện phía trước cho phép.

Vấn đề tương tự xảy ra với `count * elementSize`, `end = start + duration`, tính chỉ số hash, số thứ tự tăng liên tục hoặc chuyển timestamp sang đơn vị nhỏ: **phép kiểm tra cũng phải an toàn với tràn**.

## 10. Sai lầm thường gặp khi đọc source hoặc bytecode

| Nhận định sai | Điều đúng |
| --- | --- |
| “`int` tràn thì JVM ném `ArithmeticException`” | `iadd`/`isub`/`imul` wrap; riêng chia/lấy dư nguyên cho 0 ném lỗi, hoặc dùng các API checked |
| “Gán kết quả vào `long` sẽ chống tràn” | Nếu phép tính `int` đã xong rồi mới widen, phần bị mất không thể phục hồi |
| “`byte + byte` có kiểu `byte`” | Binary numeric promotion cho phép cộng thông thường tạo `int` |
| “`(byte)255` thành 255 nhưng lưu ít bit hơn” | Kết quả là `-1` do bit thấp `0xFF` được diễn giải có dấu |
| “`char` là số nguyên 16 bit có dấu” | `char` biểu diễn giá trị nguyên **không dấu** 0–65535 |
| “`i2b` đẩy giá trị byte 8 bit độc lập lên stack” | Nó thu hẹp, rồi sign-extend trở lại giá trị **int** trên operand stack |
| “`(int) Double.NaN` gây lỗi” | Kết quả là `0`; vô hạn/ngoài miền bị clamp, không gây overflow exception |
| “`>>>` biến `int` thành kiểu unsigned” | Nó chỉ dịch bit với zero-fill; kiểu kết quả vẫn là `int` |
| “Dịch `int` trái 32 vị trí đưa về 0” | Khoảng dịch bị mask 5 bit, `1 << 32` bằng `1` |
| “`-7 / 3` là `-3` như floor division” | Phép chia nguyên Java làm tròn về 0, kết quả `-2` |
| “Nếu source có phép cộng thì `javap` luôn có `iadd`” | Compiler có thể constant-fold, dùng opcode khác hoặc tối ưu với ngữ nghĩa tương đương |

## 11. Quy trình phân tích một phép toán số nguyên

Khi một phép toán cho kết quả lạ, đi từ trên xuống:

1. **Xác định version và kiểu source:** giá trị là `byte`/`short`/`char`/`int`/`long` hay floating-point? `--release` và API tương ứng?
2. **Đặt dấu ngoặc đúng thứ tự đánh giá:** xác định phép cộng/nhân thực hiện trước hay sau cast.
3. **Áp dụng promotion:** phép toán tạo `int`, `long` hay floating-point? Có unboxing hoặc constant expression hay không?
4. **Tính kết quả toán học:** nếu là `int`/`long`, giữ 32/64 bit thấp khi phép cộng/nhân/trừ có thể tràn.
5. **Áp dụng các cast sau đó:** narrowing giữ bit thấp; widening signed sign-extends, `char` zero-extends.
6. **Nếu chia hoặc shift:** nhớ chia nguyên truncation về 0; shift mask 5/6 bit khoảng dịch; phân biệt `>>` với `>>>`.
7. **Đối chiếu bytecode:** `javac --release 17` + `javap -c -p`; theo dõi `iadd`, `i2l`, `ladd`, `i2b`... đúng thứ tự.
8. **Quyết định rủi ro:** wrap có phải thiết kế cố ý (ví dụ hash/bit flags) hay lỗi (ví dụ kích thước, tiền, kiểm tra giới hạn)? Nếu là lỗi, dùng API checked, miền đủ rộng hoặc kiểu số lớn.

### Bài tự kiểm tra nhanh

| Biểu thức Java 17 | Kết quả | Lý do |
| --- | --- | --- |
| `Integer.MAX_VALUE + 1` | `-2147483648` | `int` wrap 32 bit |
| `(long) (Integer.MAX_VALUE + 1)` | `-2147483648L` | Cộng `int` rồi mới `i2l` |
| `(long) Integer.MAX_VALUE + 1` | `2147483648L` | Widen trước, cộng `long` |
| `(byte) 0xFF` | `-1` | Giữ 8 bit rồi diễn giải có dấu |
| `(char) -1` | `65535` | Giữ 16 bit và diễn giải không dấu |
| `(int) Double.NaN` | `0` | Quy tắc float/double → integer |
| `-7 % 3` | `-1` | Dư có dấu của số bị chia |
| `1 << 32` | `1` | 5 bit thấp của khoảng dịch là `0` |
| `-1 >>> 1` | `2147483647` | Dịch phải zero-fill |
| `Math.addExact(Integer.MAX_VALUE, 1)` | Ném `ArithmeticException` | API chủ động kiểm tra tràn |

## 12. Tài liệu gốc (đối chiếu Java SE 17)

- [JLS §4.2.1 — Integral Types and Values](https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html#jls-4.2.1): phạm vi `byte`, `short`, `int`, `long`, `char`.
- [JLS `5.1.2 — Widening Primitive Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.2) và [`5.1.3 — Narrowing Primitive Conversion](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.1.3): mở rộng có dấu/không dấu, mất bit, floating-point → integer.
- [JLS `5.6 — Numeric Contexts](https://docs.oracle.com/javase/specs/jls/se17/html/jls-5.html#jls-5.6) và [`15.26.2 — Compound Assignment Operators](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.26.2): promotion, phép gán kết hợp.
- [JLS `15.17 — Multiplicative Operators](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.17) và [`15.18 — Additive Operators](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.18): chia nguyên, số dư, wrap khi cộng/nhân.
- [JLS §15.19 — Shift Operators](https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.19): masking shift distance và các kiểu dịch.
- [JVMS §2.11.3–§2.11.4](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html#jvms-2.11.3): tổng quan lệnh arithmetic và conversion; [JVMS §6.5](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-6.html#jvms-6.5) mô tả chính xác từng opcode `iadd`, `i2b`, `l2i`, `idiv`, `iushr`.
- [Java SE 17 `Math` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Math.html) và [`Integer` API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Integer.html): checked arithmetic, floor division, unsigned interpretation.
- [`javap` tool guide Java SE 17](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javap.html): cách đọc bytecode thật được biên dịch.

**Quy tắc cốt lõi:** xác định **kiểu và độ rộng ngay lúc phép toán được thực hiện**, rồi mới xét các bước chuyển kiểu. Khi đọc bytecode, thứ tự `iadd`/`i2l`/`ladd` giải thích vì sao hai biểu thức trông giống nhau ở source có thể cho kết quả khác nhau. Không có kiểm tra overflow tự động cho phép toán nguyên thông thường; hãy lựa chọn chủ động nếu không được phép mất giá trị.


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-data-types-006">Số thực IEEE 754: NaN, vô cực, số không có dấu và tính nhất quán</a>

<details>
<summary>Xem chi tiết</summary>

# Số thực IEEE 754 trong JVM: NaN, vô cực, số không có dấu và tính nhất quán

## Mục tiêu học tập

Các kiểu `float` và `double` của Java không lưu trữ mọi số thực theo nghĩa toán học. Chúng lưu một tập hữu hạn các **giá trị dấu phẩy động nhị phân** cùng những giá trị đặc biệt theo mô hình IEEE 754. Sau bài này, bạn có thể:

- Giải thích cấu trúc, độ chính xác và vùng giá trị của `float`/`double` theo IEEE 754.
- Dự đoán kết quả khi phép tính tạo **NaN**, **vô cực dương/âm** và **số không mang dấu** (`+0.0`, `-0.0`).
- Phân biệt so sánh số học `==` với `Double.equals`, `Double.compare` và so sánh bit; hiểu tác động lên collection.
- Hiểu làm tròn, overflow, underflow, tính tái lập của phép tính Java 17+ và các giới hạn thực tế của tính nhất quán.
- Viết chương trình kiểm tra dữ liệu số, chọn cách so sánh/phép toán phù hợp và tránh lỗi khi truyền dữ liệu hoặc xử lý tài chính.

## 1. Hai kiểu số thực của Java và mô hình IEEE 754

Java có hai kiểu dấu phẩy động nguyên thủy:

| Tính chất | `float` | `double` |
| --- | --- | --- |
| Định dạng IEEE 754 | binary32 | binary64 |
| Tổng số bit | 32 | 64 |
| Bit dấu | 1 | 1 |
| Bit số mũ được mã hóa | 8 | 11 |
| Bit phần trị được lưu (fraction) | 23 | 52 |
| Độ chính xác phần trị với bit ẩn của số chuẩn hóa | 24 bit | 53 bit |
| Số chữ số thập phân có nghĩa gần đúng | khoảng 6–9 tùy mục đích biểu diễn | khoảng 15–17 tùy mục đích biểu diễn |
| Giá trị dương chuẩn hóa nhỏ nhất | `Float.MIN_NORMAL` | `Double.MIN_NORMAL` |
| Giá trị dương khác 0 nhỏ nhất (subnormal) | `Float.MIN_VALUE` | `Double.MIN_VALUE` |
| Giá trị hữu hạn dương lớn nhất | `Float.MAX_VALUE` | `Double.MAX_VALUE` |

Trong Java, literal `1.25` mặc định là `double`, còn `1.25f` là `float`. Dùng `double` khi không có lý do rõ ràng cần độ chính xác/không gian lưu trữ của `float`; **không** hiểu rằng `double` tính chính xác mọi giá trị thập phân.

Với số **chuẩn hóa (normal)** và khác 0, giá trị nhị phân được diễn giải theo dạng:

~~~text
(-1)^sign × (1.fraction) × 2^(storedExponent - bias)
~~~

Trong đó `bias` là 127 đối với binary32 và 1023 đối với binary64. Bit `1` đầu của phần trị ở số chuẩn hóa được ngầm định nên không lưu trực tiếp.

Các trường hợp đặc biệt theo bit mũ/phần trị:

| Bit số mũ | Bit phần trị | Ý nghĩa |
| --- | --- | --- |
| Không toàn 0 và không toàn 1 | Bất kỳ | Số hữu hạn chuẩn hóa |
| Toàn 0 | Toàn 0 | Số không; **bit dấu** phân biệt `+0.0` và `-0.0` |
| Toàn 0 | Có bit 1 | Số **subnormal**, cho phép độ lớn tiến dần về 0 |
| Toàn 1 | Toàn 0 | Vô cực `+Infinity` hoặc `-Infinity` theo bit dấu |
| Toàn 1 | Có bit 1 | **NaN** (*Not a Number*) |

Trong cách đặt tên hằng số Java, `Double.MIN_VALUE` **không phải** số âm hữu hạn nhỏ nhất; nó là **số dương khác 0 nhỏ nhất**, thuộc vùng subnormal. Để lấy số âm có trị tuyệt đối lớn nhất có thể biểu diễn hữu hạn, dùng `-Double.MAX_VALUE`.

### Vì sao biểu diễn thập phân có thể không chính xác?

Phân số `0.1` của hệ thập phân có khai triển nhị phân vô hạn, không được mã hóa chính xác trong binary64 hữu hạn bit. Do đó:

~~~java
double result = 0.1 + 0.2;
System.out.println(result);        // 0.30000000000000004
System.out.println(result == 0.3); // false
~~~

Đây là **đoạn minh họa đặt trong một phương thức Java**. Hai phép biểu diễn làm tròn khác nhau: tổng của số gần `0.1` và số gần `0.2` không trùng mẫu bit của literal `0.3`. Đối với dữ liệu số thực, những sai số biểu diễn rất nhỏ có thể tích lũy qua nhiều phép tính; không nên dùng `==` để hỏi liệu hai kết quả xấp xỉ có “đủ gần” nhau theo yêu cầu nghiệp vụ.

## 2. NaN: kết quả không biểu diễn được như một số

**NaN** viết tắt của *Not a Number*. Nó có thể xuất hiện trong những phép toán không có giá trị số thực/vô cực xác định, chẳng hạn:

~~~java
double a = 0.0 / 0.0;                         // NaN
double b = Double.POSITIVE_INFINITY
         - Double.POSITIVE_INFINITY;         // NaN
double c = 0.0 * Double.POSITIVE_INFINITY;   // NaN
double d = Math.sqrt(-1.0);                   // NaN
~~~

Đó là một **đoạn minh họa**, không phải một class độc lập. Không có `ArithmeticException` chỉ vì một phép toán `double` chia cho 0. Điểm này khác với chia nguyên, ví dụ `int x = 1 / 0;` gây lỗi do phép chia cho 0 (với biểu thức hằng như thế compiler có thể chấp nhận rồi ném khi đánh giá).

### NaN lan truyền như thế nào?

Với phần lớn phép toán số thực thông thường, nếu một toán hạng là NaN, kết quả cũng là NaN, ví dụ `Double.NaN + 1.0`, `Double.NaN * 2.0` và `Double.NaN / 3.0`. Những API cụ thể có thể quy định xử lý NaN riêng, vì vậy cần đọc contract của phương thức thay vì suy diễn mọi hàm trong thư viện đều giống các toán tử số học.

**NaN là “unordered” khi so sánh bằng toán tử:** `NaN == NaN` trả `false`, ngay cả cùng một biến; `NaN != NaN` trả `true`. Bất cứ phép so sánh thứ tự nào bằng `<`, `<=`, `>`, `>=` với ít nhất một NaN đều trả `false`.

| Biểu thức | Kết quả |
| --- | --- |
| `Double.NaN == Double.NaN` | `false` |
| `Double.NaN != Double.NaN` | `true` |
| `Double.NaN < 1.0` | `false` |
| `Double.NaN >= 1.0` | `false` |
| `Double.isNaN(Double.NaN)` | `true` |
| `Double.isFinite(Double.NaN)` | `false` |

Một bẫy logic: với NaN, điều kiện `!(x >= y)` **không tương đương** `x < y`. Nếu `x` là NaN, `x >= y` là `false`, nên phủ định thành `true`, nhưng `x < y` vẫn là `false`. Đừng viết validate khoảng số bằng hai nhánh `x < min` và `x > max` mà quên NaN: cả hai sẽ `false`, khiến NaN lọt qua. Kiểm tra tính hữu hạn trước.

**Dùng API thay vì so sánh với NaN:**

~~~java
if (Double.isNaN(value)) {
    // Xử lý phép đo không xác định.
}

if (!Double.isFinite(value)) {
    // Loại cả NaN và hai vô cực khi nghiệp vụ yêu cầu số hữu hạn.
}
~~~

Hai đoạn trên minh họa điều kiện kiểm tra, không phải chương trình độc lập.

## 3. Vô cực dương/âm và overflow

IEEE 754 có hai giá trị vô cực: `Double.POSITIVE_INFINITY` và `Double.NEGATIVE_INFINITY` (tương tự với `Float`).

Một số tình huống:

| Phép tính số thực | Kết quả |
| --- | --- |
| `1.0 / 0.0` | `Infinity` |
| `-1.0 / 0.0` | `-Infinity` |
| `Double.MAX_VALUE * 2.0` | `Infinity` (overflow) |
| `1.0 / Double.POSITIVE_INFINITY` | `0.0` |
| `Double.POSITIVE_INFINITY + 3.0` | `Infinity` |
| `Double.POSITIVE_INFINITY / Double.POSITIVE_INFINITY` | `NaN` |
| `Double.POSITIVE_INFINITY - Double.POSITIVE_INFINITY` | `NaN` |
| `0.0 * Double.POSITIVE_INFINITY` | `NaN` |

**Overflow** khi kết quả hữu hạn về mặt toán học vượt ngưỡng định dạng có thể tạo vô cực (phụ thuộc dấu và quy tắc làm tròn). **Vô cực không giống giá trị hữu hạn lớn nhất**: `Double.MAX_VALUE` vẫn là hữu hạn; `Double.isFinite(Double.POSITIVE_INFINITY)` là `false`.

`Infinity` và `-Infinity` tham gia quan hệ thứ tự số học: `Double.POSITIVE_INFINITY > Double.MAX_VALUE` là `true`; `Double.NEGATIVE_INFINITY < -Double.MAX_VALUE` là `true`. Mọi biểu thức so sánh như vậy vẫn phải xét khả năng NaN xuất hiện, vì NaN không có vị trí trong thứ tự của các toán tử `<` và `>`.

**Không dùng vô cực làm giá trị sentinel một cách tùy tiện.** Trong một số thuật toán, vô cực có ý nghĩa rõ ràng, chẳng hạn khoảng cách ban đầu chưa biết; nhưng đưa vô cực vào tổng, trung bình hoặc phép chuẩn hóa có thể sinh ra NaN. Phải ghi rõ quy tắc xử lý “không có dữ liệu” thay vì đồng nhất NaN, vô cực và `null`.

## 4. Số không mang dấu: `+0.0` và `-0.0`

Với `float`/`double`, **hai dạng số không** có giá trị số học bằng nhau khi dùng toán tử `==`:

~~~java
System.out.println(0.0 == -0.0);  // true
System.out.println(0.0 < -0.0);   // false
System.out.println(1.0 / 0.0);    // Infinity
System.out.println(1.0 / -0.0);   // -Infinity
~~~

Đây là đoạn minh họa ở trong phương thức; chương trình chạy đầy đủ xuất hiện bên dưới.

IEEE 754 duy trì bit dấu ở số không để thông tin về hướng của giá trị được giữ lại trong những phép toán như chia, nhân và một số hàm số. Ví dụ, `-0.0` có thể phát sinh từ số âm quá nhỏ bị làm tròn về 0; kết quả `1.0 / -0.0` vẫn mang dấu âm.

Một số quy tắc hữu ích:

- Nhân hoặc chia với số không có thể truyền dấu theo quy tắc dấu của phép tính; `-1.0 * 0.0` cho `-0.0`.
- Kết quả của cộng/trừ số không còn phụ thuộc quy tắc làm tròn; không thể khẳng định “cứ có toán hạng âm 0 thì kết quả luôn âm 0”. Ví dụ `-0.0 + 0.0` cho `+0.0` trong chế độ làm tròn chuẩn.
- `Math.min(-0.0, +0.0)` trả `-0.0` và `Math.max(-0.0, +0.0)` trả `+0.0`.
- Hai số không có mẫu bit khác nhau; `Double.doubleToRawLongBits(-0.0)` mang bit dấu bật, còn `Double.doubleToRawLongBits(+0.0)` không.
- Với nhiều bài toán, phân biệt dấu số không không cần thiết; với tính toán số học, quy tắc thứ tự, kiểm thử bit hoặc serialization, khác biệt có thể rất quan trọng.

**Đừng dùng `x == 0.0` để kiểm tra dấu của số không.** Hãy kiểm tra bit nếu cần phân biệt chính xác `-0.0` và `+0.0`:

~~~java
boolean isNegativeZero = Double.doubleToRawLongBits(x)
        == Double.doubleToRawLongBits(-0.0d);
~~~

Đây là đoạn minh họa dùng biến `x` kiểu `double`. Lưu ý phép thử này chỉ trả `true` cho **chính xác âm 0**, không cho mọi số âm; cũng không thay thế kiểm tra NaN.

## 5. Ba quan hệ khác nhau: toán tử, wrapper và bit pattern

Một nhầm lẫn gây lỗi collection: **`double` nguyên thủy dùng `==` có quy tắc khác `Double.equals` và `Double.compare`**.

### A. So sánh số học bằng toán tử `==` và `<`

- `+0.0 == -0.0` là `true`.
- `NaN == NaN` là `false`.
- NaN không nhỏ, không lớn, cũng không bằng bất cứ giá trị nào, kể cả NaN.
- Vì NaN phá tính phản xạ của `==`, toán tử này **không tạo quan hệ tương đương** trên toàn bộ tập `double`.

Phù hợp với phép so sánh số học, nhưng thường **không phù hợp** để trực tiếp mô phỏng contract của `equals` trong kiểu đối tượng/collection.

### B. `Double.equals` và `Double.compare`

Wrapper `Double` định nghĩa ngữ nghĩa khác để `equals` có tính phản xạ và `compare` tạo thứ tự toàn phần:

| So sánh | Kết quả |
| --- | --- |
| `Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN))` | `true` |
| `Double.valueOf(-0.0).equals(Double.valueOf(+0.0))` | `false` |
| `Double.compare(-0.0, +0.0) < 0` | `true` |
| `Double.compare(Double.NaN, Double.NaN) == 0` | `true` |
| `Double.compare(Double.NaN, Double.POSITIVE_INFINITY) > 0` | `true` |

Thứ tự do `Double.compare` xác lập có dạng:

~~~text
-Infinity < các số âm hữu hạn < -0.0 < +0.0
          < các số dương hữu hạn < +Infinity < NaN
~~~

Mọi NaN đều được `Double.compare` xem như bằng nhau cho mục đích so sánh. Quy tắc này phù hợp với `Double.equals` và hỗ trợ các cấu trúc `HashSet<Double>`, `HashMap<Double, ...>`, `TreeSet<Double>`, `TreeMap<Double, ...>` mà không vi phạm hợp đồng equals/ordering thông thường.

Hệ quả: `HashSet<Double>` có thể chứa **cả `-0.0` lẫn `+0.0`** như hai phần tử khác nhau, nhưng thêm nhiều NaN khác nhau sẽ được coi là cùng một phần tử theo `Double.equals`.

### C. So sánh các bit chính xác

API `Double.doubleToRawLongBits(x)` trả các bit biểu diễn *raw* của `double`. API `Double.doubleToLongBits(x)` sẽ **chuẩn hóa mọi NaN về một bit pattern NaN chuẩn** trước khi trả giá trị `long`.

| Mục đích | API phù hợp |
| --- | --- |
| Hỏi giá trị có phải NaN? | `Double.isNaN(x)` |
| Hỏi giá trị có hữu hạn? | `Double.isFinite(x)` |
| So sánh/khóa theo ngữ nghĩa wrapper nhất quán | `Double.equals`, `Double.compare` |
| Phân biệt `-0.0` với `+0.0` bằng bit | `Double.doubleToRawLongBits(x)` |
| Bảo toàn/phân tích payload NaN ở mức bit khi có thể | `Double.doubleToRawLongBits(x)` |
| Coi mọi NaN là một đại diện chuẩn | `Double.doubleToLongBits(x)` |

IEEE 754 cho phép **nhiều mẫu bit NaN** với các payload khác nhau. Các phép toán Java thường xử lý chúng như một giá trị NaN chung. Không được giả định phép toán sẽ giữ nguyên payload; đặc biệt **signaling NaN** có thể bị chuyển thành quiet NaN trên một số kiến trúc, thậm chí khi được truyền qua API chuyển đổi bit. Với `float` có các API tương ứng: `Float.floatToRawIntBits` và `Float.floatToIntBits`.

## 6. Ví dụ Java 17+ chạy được và kết quả kiểm chứng

Tạo tệp **`Ieee754Demo.java`** (tên tệp phải khớp với `public class`):

~~~java
public class Ieee754Demo {
    public static void main(String[] args) {
        double nan = 0.0 / 0.0;
        double positiveInfinity = 1.0 / 0.0;
        double negativeInfinity = -1.0 / 0.0;
        double positiveZero = +0.0;
        double negativeZero = -0.0;

        System.out.println("nan=" + Double.isNaN(nan));
        System.out.println("nanSelfEquals=" + (nan == nan));
        System.out.println("nanSelfNotEquals=" + (nan != nan));
        System.out.println("positiveInfinity=" + positiveInfinity);
        System.out.println("negativeInfinity=" + negativeInfinity);
        System.out.println("infinityIsFinite=" + Double.isFinite(positiveInfinity));
        System.out.println("signedZerosEqual=" + (positiveZero == negativeZero));
        System.out.println("positiveZeroReciprocal=" + (1.0 / positiveZero));
        System.out.println("negativeZeroReciprocal=" + (1.0 / negativeZero));
        System.out.println("boxedZerosEqual=" +
                Double.valueOf(positiveZero).equals(Double.valueOf(negativeZero)));
        System.out.println("negativeZeroBeforePositiveZero=" +
                (Double.compare(negativeZero, positiveZero) < 0));
        System.out.println("boxedNaNsEqual=" +
                Double.valueOf(nan).equals(Double.valueOf(Double.NaN)));
        System.out.println("nanAfterInfinity=" +
                (Double.compare(nan, positiveInfinity) > 0));
        System.out.println("overflow=" + (Double.MAX_VALUE * 2.0));
        System.out.println("halfSmallestSubnormal=" + (Double.MIN_VALUE / 2.0));
        System.out.println("decimalSum=" + (0.1 + 0.2));
        System.out.println("decimalEqual=" + (0.1 + 0.2 == 0.3));
    }
}
~~~

Biên dịch và chạy (sử dụng JDK 17 hoặc mới hơn):

~~~bash
javac Ieee754Demo.java
java Ieee754Demo
~~~

**Stdout chính xác:**

~~~text
nan=true
nanSelfEquals=false
nanSelfNotEquals=true
positiveInfinity=Infinity
negativeInfinity=-Infinity
infinityIsFinite=false
signedZerosEqual=true
positiveZeroReciprocal=Infinity
negativeZeroReciprocal=-Infinity
boxedZerosEqual=false
negativeZeroBeforePositiveZero=true
boxedNaNsEqual=true
nanAfterInfinity=true
overflow=Infinity
halfSmallestSubnormal=0.0
decimalSum=0.30000000000000004
decimalEqual=false
~~~

Giải thích những dòng dễ gây ngạc nhiên:

1. `0.0 / 0.0` tạo NaN, nhưng `nan == nan` vẫn là `false` vì quy tắc toán tử.
2. Chia cho `+0.0` và `-0.0` cho hai vô cực khác dấu.
3. `Double.valueOf(-0.0).equals(Double.valueOf(+0.0))` trả `false`, dù toán tử `==` trả `true`.
4. `Double.compare` đặt âm 0 trước dương 0 và NaN sau cả vô cực dương.
5. Nhân vượt miền hữu hạn tạo vô cực; chia một nửa `Double.MIN_VALUE` được làm tròn về `+0.0` vì giá trị chính xác nằm đúng giữa 0 và số subnormal dương nhỏ nhất, theo quy tắc gần nhất, chọn chữ số chẵn (*ties to even*).
6. `0.1 + 0.2` thể hiện sai số nhị phân; literal `0.3` không cùng kết quả binary64 sau làm tròn.

**Phạm vi ví dụ:** các dòng trên cố ý không có nhập liệu hay tính toán song song, nên thứ tự và stdout ổn định theo Java 17+. Đoạn mã không yêu cầu thư viện ngoài Java SE.

## 7. Làm tròn, overflow, underflow và số subnormal

**Làm tròn:** nhiều phép tính số học trên số thực cho kết quả thực có thể không nằm trong tập giá trị biểu diễn được. JVM phải làm tròn để trả một `float` hoặc `double`. Các toán tử số học Java 17+ sử dụng quy tắc gần nhất có thể, chọn chữ số chẵn khi hòa (*round to nearest, ties to even*) ở những phép toán tương ứng theo đặc tả. Điều này không có nghĩa mọi hàm thư viện đều tự động được tính chính xác vô hạn trước khi làm tròn; các hàm như `sin` hay `log` có hợp đồng độ chính xác riêng.

**Overflow:** trị tuyệt đối kết quả quá lớn so với miền hữu hạn có thể dẫn đến vô cực.

**Underflow:** trị tuyệt đối quá nhỏ có thể dẫn đến **subnormal** rồi cuối cùng về `+0.0` hoặc `-0.0`. Cơ chế *gradual underflow* giúp tránh việc mọi giá trị bé hơn `MIN_NORMAL` bị lập tức làm tròn về 0.

**Precision:** `float` có 24 bit phần trị hiệu dụng, `double` có 53 bit. Tăng độ chính xác từ `float` lên `double` có thể giúp giảm sai số nhưng **không biến phép toán thành số học thực vô hạn độ chính xác**.

Ví dụ dễ kiểm tra về khoảng cách giữa các giá trị biểu diễn được:

~~~java
System.out.println(Math.nextUp(1.0) > 1.0); // true
System.out.println(1.0 + Math.ulp(1.0) > 1.0); // true
~~~

Đây là đoạn minh họa trong phương thức Java. `Math.ulp(x)` trả độ rộng khoảng cách của đơn vị ở vị trí cuối (*unit in the last place*) theo đặc tả Java; `Math.nextUp(x)` đi đến giá trị dấu phẩy động kế tiếp theo chiều tăng. **ULP phụ thuộc độ lớn của x**, không phải một hằng số epsilon chung cho toàn miền.

### Tác động của thứ tự phép tính

Phép cộng số thực nhìn chung **không có tính kết hợp** (*associative*) như cộng số thực lý tưởng, do làm tròn ở từng bước. Ví dụ, trong `double`:

~~~java
double a = 1.0e16;
double b = -1.0e16;
double c = 1.0;

System.out.println((a + b) + c); // 1.0
System.out.println(a + (b + c)); // 0.0
~~~

Ở biểu thức thứ hai, `b + c` làm mất `1.0` vì độ lớn chênh lệch quá xa; thứ tự gom nhóm khác nhau dẫn đến kết quả khác nhau. Vì vậy thay đổi thứ tự cộng, chia task song song hoặc thay cách reduction có thể đổi kết quả bit cuối dù từng phép toán đều tuân thủ IEEE 754. Không thể suy ra “tính nhất quán” đồng nghĩa với “kết quả bất biến với mọi thứ tự phép tính”.

Một số bài toán cần tính chính xác cao hơn có thể dùng thuật toán cộng bù như Kahan summation, pairwise summation, số học độ chính xác cao, hoặc cách tích lũy theo thứ tự ổn định. Lựa chọn tùy yêu cầu sai số và hiệu năng, không có một giải pháp áp dụng cho mọi ứng dụng.

## 8. Tính nhất quán giữa JVM và vai trò của `strictfp`

Ở các phiên bản Java trước 17, ngữ nghĩa dấu phẩy động có lịch sử cho phép một số kết quả trung gian dùng **miền số mũ mở rộng** trong ngữ cảnh không strict. Từ **Java 17**, JEP 306 khôi phục chế độ **always-strict floating-point semantics**: phép toán số thực Java tuân thủ quy tắc strict theo đặc tả, không còn cần bật `strictfp` để đạt ngữ nghĩa này.

- Các kiểu `float`/`double` gắn với định dạng IEEE 754 binary32/binary64, kể cả giá trị đặc biệt và subnormal.
- Biểu thức dấu phẩy động Java 17+ luôn chịu các quy tắc strict, giúp giảm khác biệt kiểu “dùng số mũ trung gian rộng hơn” giữa các nền tảng/JVM.
- Từ khóa `strictfp` vẫn có thể gặp trong mã cũ, nhưng ở Java 17+ nó **không còn tác dụng làm phép toán nghiêm ngặt hơn** và có thể bị compiler cảnh báo là không cần thiết.
- Trình biên dịch và JVM có thể tối ưu theo phạm vi luật Java cho phép; không được tùy ý đổi thứ tự biểu thức nếu điều đó làm thay đổi kết quả quan sát được mà đặc tả không cho phép.

**Phân biệt hai lớp tính nhất quán:**

1. **Ngữ nghĩa toán tử cơ bản theo JLS/JVMS:** cùng chương trình, cùng dữ liệu và cùng thứ tự đánh giá thì các phép toán được đặc tả nghiêm ngặt có kết quả theo quy tắc Java, thay vì phụ thuộc vào việc JVM tùy ý giữ phần trung gian trong độ chính xác vượt mức.
2. **Tái lập toàn bộ ứng dụng:** vẫn có thể khác vì thứ tự tính toán song song, dữ liệu đầu vào, dependency, phiên bản và hợp đồng của hàm toán học. Đừng hứa tính tái lập bit-for-bit cho *mọi* hàm số và môi trường chỉ vì dùng `double`.

### `Math`, `StrictMath`, `Math.fma` và phép dư

- Nhiều phép toán `Math` có quy định độ chính xác (ví dụ trong đơn vị ULP) nhưng không phải tất cả các hàm siêu việt đều bảo đảm cùng bit trên mọi triển khai và phiên bản. Khi cần kết quả tái lập cho các hàm được đặc tả phù hợp, kiểm tra contract cụ thể của `StrictMath`.
- `Math.fma(a, b, c)` tính phép nhân-cộng **fused** với một lần làm tròn cuối theo contract FMA. `a * b + c` là biểu thức hai phép toán, thường làm tròn sau nhân rồi sau cộng; chúng **có thể khác nhau**. Không coi đây là lỗi IEEE 754.
- Toán tử `%` của Java trên `float`/`double` dùng quy tắc **phép dư kiểu truncation**, không hoàn toàn giống phép remainder của IEEE 754 chọn thương gần nhất. Khi cần phép dư IEEE 754 theo định nghĩa phương thức, dùng `Math.IEEEremainder(x, y)`.
- Phép chuyển đổi kiểu và các phương thức của thư viện phải được xét theo đặc tả của chúng. Ví dụ, ép kiểu từ `double` sang `int` chuyển NaN thành `0`, còn số quá lớn theo hướng dương/âm có thể bị giới hạn ở `Integer.MAX_VALUE`/`Integer.MIN_VALUE`; đừng giả định nó ném ngoại lệ khi dữ liệu không hợp lệ.

## 9. Chọn phép so sánh theo mục đích

### Kiểm tra dữ liệu đầu vào

Nếu miền nghiệp vụ chỉ cho phép số hữu hạn:

~~~java
static double requireFinite(double x) {
    if (!Double.isFinite(x)) {
        throw new IllegalArgumentException("Expected a finite number");
    }
    return x;
}
~~~

Đây là **phương thức minh họa**, cần đặt trong một class khi chạy. `Double.isFinite` loại cả NaN lẫn hai vô cực, nhưng **chấp nhận `-0.0` và `+0.0`**. Nếu âm 0 có ý nghĩa riêng trong hệ thống, cần xử lý riêng bằng API bit hoặc chuẩn hóa có chủ ý.

### So sánh hai kết quả xấp xỉ

Nếu phép tính có sai số và nghiệp vụ xác định ngưỡng chấp nhận, nên xác định **absolute tolerance** và/hoặc **relative tolerance** theo miền giá trị, thay vì dùng một `EPSILON` tùy ý cho mọi trường hợp:

~~~java
static boolean approximatelyEqual(
        double a, double b, double absTol, double relTol) {

    if (absTol < 0 || relTol < 0 ||
            !Double.isFinite(absTol) || !Double.isFinite(relTol)) {
        throw new IllegalArgumentException("Invalid tolerances");
    }
    if (Double.isNaN(a) || Double.isNaN(b)) {
        return false;
    }
    if (a == b) {
        return true; // Kể cả hai vô cực cùng dấu và hai số không.
    }
    if (!Double.isFinite(a) || !Double.isFinite(b)) {
        return false;
    }

    double distance = Math.abs(a - b);
    double scale = Math.max(Math.abs(a), Math.abs(b));
    return distance <= Math.max(absTol, relTol * scale);
}
~~~

Đây là **phương thức minh họa**, không phải chương trình độc lập. Công thức chỉ là điểm khởi đầu cho bài toán số học thông thường: phép trừ `a - b` có thể overflow ngay cả khi `a` và `b` đều hữu hạn; chọn tolerance và thuật toán robust theo miền bài toán. Không tự động dùng “xấp xỉ bằng nhau” để làm `equals` hoặc key của `HashMap` vì quan hệ xấp xỉ thường **không có tính bắc cầu**.

### Sắp xếp số thực

Nếu cần sắp xếp `Double`, dùng `Double.compare` hoặc `Comparator.naturalOrder()` để có thứ tự toàn phần, nắm rõ NaN cuối và phân biệt âm 0/dương 0. Nếu nghiệp vụ muốn NaN ở đầu hoặc xem hai số không như nhau, hãy định nghĩa quy tắc riêng **nhất quán với equals/ordering của cấu trúc đang dùng**; đừng thay comparator tùy tiện rồi giả định `TreeSet` và `HashSet` có cùng semantics.

### Tính toán tiền hoặc thập phân đúng kỳ vọng nghiệp vụ

Dùng `BigDecimal` (với scale và `RoundingMode` rõ ràng) cho trường hợp cần chính xác trong hệ thập phân, chẳng hạn giá tiền:

~~~java
import java.math.BigDecimal;

BigDecimal first = new BigDecimal("0.1");
BigDecimal second = new BigDecimal("0.2");
System.out.println(first.add(second)); // 0.3
~~~

Đây là đoạn minh họa bên trong phương thức Java (cần import). Tránh `new BigDecimal(0.1)` nếu muốn chính xác giá trị **thập phân 0.1**: constructor nhận `double` giữ giá trị nhị phân xấp xỉ được đưa vào. `BigDecimal.valueOf(0.1)` sử dụng dạng chuỗi của `double` và thường dễ dùng hơn nhưng khi có đầu vào thập phân nguyên bản, tạo từ **chuỗi thập phân** rõ ràng nhất.

## 10. Lỗi và tình huống thực tế

| Tình huống | Sai lầm điển hình | Hướng xử lý |
| --- | --- | --- |
| Kết quả `0.1 + 0.2` không bằng literal `0.3` | Dùng `==` cho kết quả thập phân xấp xỉ | Xác định tolerance theo domain hoặc dùng `BigDecimal` cho thập phân chính xác |
| Validate `x >= min` và `x <= max` | Không loại NaN; một số cách viết bằng phủ định dễ bỏ sót NaN | `Double.isFinite(x)` trước khi kiểm tra miền |
| Chia `double` cho 0 | Chờ `ArithmeticException` như chia `int` | Kiểm tra mẫu số/miền nghiệp vụ và xử lý NaN/vô cực |
| Số rất lớn bị thành `Infinity` | Nghĩ mọi kết quả `double` đều hữu hạn | Kiểm tra `Double.isFinite` và thiết kế kiểm soát overflow |
| Giá trị rất nhỏ bị thành `0.0` | Nghĩ mọi số khác 0 đều giữ được sau phép tính | Hiểu subnormal, underflow và giới hạn precision |
| Hai `Double` chứa `-0.0` và `+0.0` trong `HashSet` | Nghĩ wrapper `equals` tương đương toán tử `==` | Chọn kiểu dữ liệu và quy tắc chuẩn hóa rõ ràng |
| Comparator tự viết dùng `a < b ? -1 : a > b ? 1 : 0` | NaN thành “bằng” mọi số theo nhánh cuối | Dùng `Double.compare(a,b)` khi cần thứ tự đầy đủ |
| Kết quả số học giữa các task song song chênh chữ số cuối | Nghĩ phép cộng floating-point có tính kết hợp | Kiểm soát thứ tự tổng hợp hoặc thuật toán cộng ổn định |
| Truyền NaN/vô cực qua JSON hoặc database | Nghĩ mọi định dạng dữ liệu ngoài đều hỗ trợ giá trị IEEE đặc biệt | Kiểm tra schema/protocol, xác thực và encode theo contract |
| Test so raw NaN payload trên mọi kiến trúc | Nghĩ payload NaN được bảo toàn qua mọi thao tác | Chỉ yêu cầu raw bit khi API/đường truyền bảo đảm; xem NaN theo ngữ nghĩa nếu phù hợp |

### Gợi ý kiểm thử có ý nghĩa

Với hàm nhận `double`, bộ test nên bao gồm **số hữu hạn dương/âm**, `+0.0`, `-0.0`, `Double.MIN_VALUE`, `Double.MIN_NORMAL`, `Double.MAX_VALUE`, `Double.NaN`, `Double.POSITIVE_INFINITY` và `Double.NEGATIVE_INFINITY`. Với thuật toán tích lũy, kiểm tra dãy số có chênh lệch độ lớn lớn và các thứ tự cộng khác nhau. Với serialization, kiểm tra contract định dạng ngoài có chấp nhận hoặc từ chối số thực đặc biệt như mong đợi.

## 11. Tự kiểm tra kiến thức

**Câu 1.** Vì sao `Double.NaN == Double.NaN` là `false` nhưng `Double.valueOf(Double.NaN).equals(Double.valueOf(Double.NaN))` là `true`?

**Đáp án:** Toán tử `==` trên primitive dùng quan hệ so sánh số học của IEEE 754, trong đó NaN unordered; wrapper `Double.equals` dùng quan hệ tương đương biểu diễn, chuẩn hóa các NaN thành cùng một lớp cho contract equals.

**Câu 2.** `+0.0 == -0.0` là `true`, vậy `1.0 / +0.0` và `1.0 / -0.0` có bằng nhau không?

**Đáp án:** Không. Kết quả là `+Infinity` và `-Infinity`. Số không có dấu khác nhau trong biểu diễn dù được `==` xem bằng nhau.

**Câu 3.** Khi nào `Double.isNaN(x)` là chưa đủ để bảo vệ hàm chỉ chấp nhận số hữu hạn?

**Đáp án:** Khi đầu vào là `Infinity` hoặc `-Infinity`; hai giá trị này không phải NaN. Dùng `Double.isFinite(x)`.

**Câu 4.** Vì sao `(a + b) + c` đôi khi khác `a + (b + c)` dù Java 17+ có ngữ nghĩa dấu phẩy động luôn strict?

**Đáp án:** Từng phép cộng có thể làm tròn về giá trị biểu diễn được. Đổi thứ tự nhóm thay đổi các bước làm tròn; strict bảo đảm quy tắc thực hiện phép toán, không biến số học dấu phẩy động thành phép toán kết hợp.

**Câu 5.** Với `Double.compare`, vị trí NaN và hai số không là gì?

**Đáp án:** `-0.0` nhỏ hơn `+0.0`, và NaN được đặt **lớn hơn mọi số khác**, kể cả vô cực dương.

**Câu 6.** `Double.MIN_VALUE` có phải giá trị âm nhỏ nhất không?

**Đáp án:** Không. Đó là giá trị **dương khác 0 nhỏ nhất** ở dạng subnormal. Giá trị âm hữu hạn xa 0 nhất theo trị tuyệt đối là `-Double.MAX_VALUE`.

**Câu 7.** Nếu cần cộng tiền `0.1` và `0.2` để nhận chính xác `0.3` theo hệ thập phân, nên dùng gì?

**Đáp án:** Dùng `BigDecimal` được tạo từ chuỗi `"0.1"` và `"0.2"` hoặc nguồn thập phân chính xác, cùng quy tắc làm tròn/scale được thiết kế cho nghiệp vụ.

## 12. Bản đồ ghi nhớ

~~~text
float   = IEEE 754 binary32  → 1 dấu + 8 số mũ + 23 fraction
double  = IEEE 754 binary64  → 1 dấu + 11 số mũ + 52 fraction

Giá trị:  hữu hạn | subnormal | +0.0 / -0.0 | ±Infinity | NaN

Toán tử == : +0.0 == -0.0       ; NaN != NaN
Double.equals/compare: phân biệt ±0.0 ; gom mọi NaN vào cùng lớp
Raw bits   : có thể quan sát dấu 0 và payload NaN
isFinite   : loại NaN và ±Infinity

Java 17+ luôn strict, nhưng thay thứ tự cộng vẫn có thể đổi kết quả.
~~~

**Điểm cốt lõi:** số thực IEEE 754 không phải số thực toán học có độ chính xác vô hạn. **NaN** biểu diễn kết quả không xác định, **vô cực** xử lý độ lớn vượt giới hạn và một số phép toán đặc biệt, **±0.0** giữ thông tin dấu; muốn tính nhất quán phải chọn đúng ngữ nghĩa so sánh, biết quy tắc làm tròn và kiểm soát thứ tự/phạm vi phép tính.

## Tài liệu chuẩn

- [Java Language Specification 21 — §4.2.3 Floating-Point Types and Values](https://docs.oracle.com/javase/specs/jls/se21/html/jls-4.html#jls-4.2.3).
- [Java Language Specification 21 — §15.4 Floating-point Expressions](https://docs.oracle.com/javase/specs/jls/se21/html/jls-15.html#jls-15.4).
- [Java SE 21 API — java.lang.Double](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Double.html).
- [OpenJDK JEP 306 — Restore Always-Strict Floating-Point Semantics](https://openjdk.org/jeps/306).


</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-data-types-007">Kiểu returnAddress và lý do cần nhận diện bytecode lịch sử</a>

<details>
<summary>Xem chi tiết</summary>

# Kiểu `returnAddress` và lý do cần nhận diện bytecode lịch sử

## Mục tiêu học tập

Sau bài này, bạn có thể:

- Giải thích `returnAddress` là một **kiểu nguyên thủy đặc biệt của JVM**, khác với `int` và `reference`, không có kiểu tương ứng trong mã nguồn Java.
- Đọc được luồng điều khiển `jsr`/`jsr_w` → `astore` → `ret` từng dùng để thực hiện khối `finally` trong bytecode cũ.
- Phân biệt `ret` (quay về bên trong cùng một phương thức) với `return`/`ireturn`/`areturn` (kết thúc lời gọi phương thức).
- Nhớ ranh giới **class-file version ≤ 50.0** và **≥ 51.0**, biết khi nào legacy opcodes bị cấm.
- Dùng `javap` nhận diện class file cũ mà không cần tạo bytecode bất hợp lệ hoặc phụ thuộc trình biên dịch Java cổ.

**Thông điệp chính:** `returnAddress` vẫn được mô tả trong Java Virtual Machine Specification (JVMS) để giải thích cách các class file cũ biểu diễn luồng điều khiển. Nó **không phải** một kiểu để lập trình viên Java viết trong source và cũng **không phải** kiểu trả về của phương thức Java.

## 1. `returnAddress` đứng ở đâu trong hệ kiểu JVM?

Theo JVMS §2.2–2.3, JVM có các kiểu nguyên thủy, kiểu tham chiếu và nhiều cơ chế biểu diễn giá trị runtime. Trong nhóm nguyên thủy có những kiểu số quen thuộc như `int`, `long`, `float`, `double`; kiểu `boolean` có hỗ trợ đặc thù; và **`returnAddress`** là một trường hợp rất riêng.

| Kiểu JVM | Giá trị minh họa | Liên hệ mã nguồn Java | Vai trò |
| --- | --- | --- | --- |
| `int` | `42` | `int` và các phép tính dùng `int` trên operand stack | Tính toán số nguyên |
| `reference` | tham chiếu đến object/array hoặc `null` | Object, array, interface, v.v. | Truy cập dữ liệu kiểu tham chiếu |
| **`returnAddress`** | vị trí opcode của một lệnh bytecode trong cùng method | **Không có kiểu Java tương ứng** | Ghi nhớ vị trí cần tiếp tục sau lời gọi bytecode subroutine cũ |

Các thuộc tính cần nắm:

1. `returnAddress` là kiểu nguyên thủy **được định nghĩa ở cấp máy ảo**; không phải một Java class, interface, pointer kiểu C, cũng không phải một subclass của `Object`.
2. Giá trị biểu thị **vị trí opcode của một lệnh JVM** dùng để tiếp tục chạy sau `jsr` hoặc `jsr_w`. Nó không phải chuỗi, số thứ tự dòng source, địa chỉ email hay địa chỉ bộ nhớ mà ứng dụng Java được phép chỉnh sửa.
3. Không thể khai báo trong Java: `returnAddress x;` không phải cú pháp kiểu dữ liệu Java hợp lệ. Không có phép `new returnAddress(...)`, cast thông thường, API Java để cộng/trừ hay serialize giá trị này.
4. Nó thuộc **category 1** trong hệ kiểu tính toán của JVM, chiếm **một ô** trên operand stack hoặc trong local variables khi dùng. `long` và `double` thuộc category 2, chiếm hai ô; **category không nói rằng returnAddress là một `int`**.
5. JVM kiểm soát nơi `returnAddress` được tạo, lưu và tiêu thụ; chương trình đang chạy không được sửa giá trị địa chỉ này bằng các phép toán thông thường.

JVMS nói “pointer to the opcode of a JVM instruction” ở mức **mô hình trừu tượng**. Đừng suy ra phải là một con trỏ native 32-bit/64-bit được Java code nhìn thấy. Kỹ thuật biểu diễn nội bộ do cách triển khai JVM quyết định.

### Tại sao `returnAddress` không phải `reference`?

Cả hai đều có kích thước tính toán category 1, nhưng **ngữ nghĩa và phép toán hợp lệ khác nhau**. Một `reference` có thể trỏ tới object/array hoặc là `null`; một `returnAddress` là dấu mốc điều khiển của bytecode subroutine. Bạn không thể dùng `getfield`, `invokevirtual`, `ifnull` hoặc `aload` như thể nó là một Java object reference. Đây là khác biệt về **kiểu**, không đơn thuần cách gọi.

Không nên hiểu từ “return” trong tên `returnAddress` là kiểu giá trị trả về của Java method. Các method Java vẫn khai báo kiểu trả về theo ngôn ngữ và method descriptor; `returnAddress` không phải kiểu được phép khai báo trong field/method descriptor thông thường.

## 2. Mô hình stack frame và địa chỉ quay lại

Trong một phương thức đang thực thi, JVM frame có ít nhất:

- **Local variables**: các slot lưu tham số và biến cục bộ/giá trị tạm.
- **Operand stack**: nơi bytecode lấy và đặt toán hạng.
- Thông tin phục vụ linking và điều khiển; mỗi thread có **PC register** xác định vị trí thực thi JVM instruction khi đang chạy Java bytecode.

Trong bytecode lịch sử, `jsr` gọi một **subroutine bên trong cùng một method**. Nó **không tạo một Java method invocation** mới như `invokestatic` hay `invokevirtual` và không tự mở frame của một phương thức Java riêng. Nó tạo dấu mốc trở về là kiểu `returnAddress`, đẩy dấu mốc lên operand stack và nhảy tới subroutine. Subroutine giữ địa chỉ đó trong local variable. `ret` lấy địa chỉ từ local variable và cập nhật luồng điều khiển để tiếp tục tại lệnh ngay sau lời `jsr` tương ứng.

~~~text
                  [Cùng một method / JVM frame]
              local variables        operand stack
                     |                      |
Caller:              |                      |
   ...               |                      |
   jsr Lfinally -----+--------------------> | returnAddress = next instruction
      |                                     |
      +-------------- jumps to Lfinally     |
                                            v
Lfinally:                             astore 2
                                      pop returnAddress
                       local[2] <-----------+
      ... cleanup ...     |
      ret 2 <-------------+
          |
          v
   [instruction just after jsr]
~~~

Sơ đồ chỉ diễn tả **giá trị luồng điều khiển**, không có nghĩa người viết Java được truy cập trực tiếp stack frame hay PC register.

## 3. Ba lệnh cốt lõi: `jsr`, `astore` và `ret`

| Bytecode | Tác động | Ghi chú |
| --- | --- | --- |
| `jsr target` | Đẩy `returnAddress` của lệnh ngay sau `jsr` lên operand stack rồi nhảy tới target | Branch offset ngắn, opcode `0xa8` |
| `jsr_w target` | Tương tự `jsr` với branch offset rộng hơn | Opcode `0xc9` |
| `astore n` / `astore_<n>` | Lấy một `reference` **hoặc** `returnAddress` trên đỉnh operand stack và ghi vào local slot `n` | Dùng để cất địa chỉ trở về trong subroutine |
| `ret n` | Đọc `returnAddress` trong local slot `n`, chuyển PC tới địa chỉ đó | Không lấy giá trị từ operand stack; opcode `0xa9` |
| `return` | Trả về từ method `void` tới caller của method | Không cùng chức năng với `ret` |
| `ireturn` / `areturn` | Trả giá trị của một method cho method caller | Không dùng `returnAddress` |

### Diễn biến chính xác ở mức operand stack

Giả sử opcode `jsr` nằm tại offset giả định 20 và lệnh kế tiếp bắt đầu tại offset 23:

1. `jsr Lcleanup` **đẩy returnAddress tương ứng offset 23** lên operand stack.
2. JVM chuyển tới opcode đầu tiên tại `Lcleanup` trong **cùng method**.
3. `astore_2` lấy `returnAddress` đang trên đỉnh stack và lưu vào `local[2]`.
4. Sau phần cleanup, `ret 2` đọc `local[2]` và chuyển PC trở về offset 23.
5. Lệnh tại offset 23 tiếp tục. Nếu đó là `return` hoặc `ireturn`, khi ấy **phương thức Java** mới hoàn tất.

Không được viết `aload_2` để đọc lại `returnAddress`. JVMS §6.5 mô tả rõ **`astore` nhận returnAddress, nhưng `aload` không được load returnAddress**; sự bất đối xứng này là chủ ý. `ret` mới là lệnh dùng giá trị từ local variable để trở về.

Đặc biệt, `ret` **không có cùng nghĩa** với `return`. Nếu method có nhiều điểm gọi chung một finally subroutine, `ret` quay về đúng vị trí sau `jsr` đã gọi nó. Ngược lại, `return` kết thúc frame của method và trở lại method caller.

## 4. Vì sao bytecode cũ dùng `returnAddress` cho `finally`?

Một khối `finally` thường phải được thực thi trước khi luồng điều khiển rời khỏi `try` theo nhiều ngả: hoàn tất bình thường, `return`, một số bước chuyển điều khiển như `break`/`continue`, hoặc có exception. Một cách tổ chức bytecode **trước đây** là biên dịch nội dung finally thành **subroutine dùng chung** ở cuối method và gọi nó từ các vị trí thoát bằng `jsr`; địa chỉ quay lại nằm trong `returnAddress`.

Ví dụ mã nguồn Java minh họa:

~~~java
void work() {
    try {
        doWork();
    } finally {
        release();
    }
}
~~~

Dưới đây là **bytecode giả lập dạng sơ đồ** minh họa lối biên dịch cũ; **không phải class file hoàn chỉnh, không được đưa nguyên vào `javap` hoặc assembler để chạy**. Các label tượng trưng thay cho offset thực tế, phần exception table bị lược bỏ:

~~~text
Ltry:
    aload_0
    invokevirtual doWork()
    jsr Lfinally            ; ghi địa chỉ lệnh kế tiếp vào returnAddress
    return                  ; đường đi bình thường: method kết thúc sau finally

LcatchAll:                  ; handler bắt exception của try
    astore_1                ; local[1] = Throwable, KIỂU reference
    jsr Lfinally            ; lần này returnAddress trỏ tới aload_1
    aload_1
    athrow                  ; ném lại exception sau finally

Lfinally:
    astore_2                ; local[2] = returnAddress, KHÔNG PHẢI Throwable
    aload_0
    invokevirtual release()
    ret 2                   ; quay lại sau đúng jsr đã gọi
~~~

Cùng một vùng bytecode `Lfinally` có thể được gọi từ nhánh bình thường và nhánh exception. Vì vậy nó cần **địa chỉ trở về tương ứng từng lần gọi** thay vì cố định một chỗ quay lại. Đây chính là mục đích của `returnAddress`.

### Đọc kỹ hai local slots trong sơ đồ

- `local[1]` giữ **exception reference**; `aload_1` hợp lệ để lấy lại exception trước `athrow`.
- `local[2]` giữ **returnAddress**; `aload_2` sẽ không hợp lệ nếu slot đang chứa kiểu này. `ret 2` đọc nó theo ngữ nghĩa riêng.
- `Lfinally` là **subroutine nội bộ cùng một method**, không phải hàm Java có tên `finally`, không phải lời gọi `invokestatic` và không tạo frame method mới.
- Nếu `release()` tự ném exception, đường quay về qua `ret` có thể không diễn ra; đây là luật exception thông thường, không có bảo đảm `finally` sẽ hoàn tất bình thường.

### Điều gì xảy ra khi `try` có `return`?

Trong cách biên dịch lịch sử, compiler có thể:

1. Lưu giá trị định trả về vào local variable.
2. Gọi `finally` subroutine qua `jsr`.
3. Khi `ret` quay về, khôi phục giá trị trả về rồi dùng lệnh kết thúc method như `ireturn`.

Như vậy **`ret` quay về phần bytecode tiếp tục xử lý việc return**, còn `ireturn` mới đưa giá trị về method caller. Hai kiểu “trở về” nằm ở **hai tầng điều khiển khác nhau**.

## 5. Điểm ranh giới lịch sử: class-file version 50 và 51

Đây là quy tắc tương thích quan trọng nhất khi đọc bytecode cũ.

| Class-file major | Gắn với release | Tình trạng `jsr`/`jsr_w`/`ret` theo JVMS |
| --- | --- | --- |
| 45–49 | Các release Java cũ tới Java 5 | Có thể xuất hiện khi bytecode thỏa các quy tắc xác minh lịch sử |
| **50** | **Java 6** | **Vẫn có thể xuất hiện** trong class file hợp lệ |
| **51** | **Java 7** | **Bị cấm** trong `Code` attribute |
| 52 | Java 8 | **Bị cấm** |
| 61 | Java 17 | **Bị cấm** |
| 65 | Java 21 | **Bị cấm** |
| 69 | Java 25 | **Bị cấm** |
| 70 | Java 26 | **Bị cấm** |

Theo **JVMS §4.9.1 (Static Constraints)**: với `class` file **version 51.0 hoặc cao hơn**, các opcode `jsr`, `jsr_w` và `ret` **không được xuất hiện** trong mảng code của phương thức. Điều cấm này tính theo **class-file version**, không phải chỉ dựa trên tên JVM đang chạy.

Cần tách ba ý:

- **Các opcode còn được tài liệu JVMS mô tả**: đúng, để xác định nghĩa và giải thích class file cũ.
- **Class file mới major ≥ 51 được phép chứa chúng**: **sai**; vi phạm static constraints.
- **Một JVM hiện đại có thể gặp class file major ≤ 50 hợp lệ**: có thể, tùy phiên bản class file còn được JVM đó hỗ trợ và các quy tắc xác minh; **không nên suy ra mọi JVM đều chạy mọi binary cổ**.

Không nhầm với quy tắc “Java 7 đã xoá opcode khỏi mọi định nghĩa JVM”: các opcode vẫn được liệt kê trong JVMS, còn quy tắc class-file format **hạn chế nơi chúng có thể xuất hiện**.

### Liên hệ cơ chế verifier

Các cấu trúc `jsr`/`ret` có luồng điều khiển phức tạp: cùng một subroutine có thể phục vụ nhiều đường rẽ và phải quay về địa chỉ hợp lệ. Verifier cần bảo đảm kiểu tại local slots/operand stack và tính hợp lệ của subroutine call chain.

JVMS §4.9.1 còn có ràng buộc về việc không được load `returnAddress` bằng lệnh thông thường, số lần có thể quay về, giới hạn liên quan đến chuỗi subroutine để tránh những luồng điều khiển không hợp lệ. Trong các bản class file cũ, quy tắc **verification by type inference** có vai trò riêng; từ class-file major 50 có cơ chế **type checking** dựa trên stack-map frames, với một trường hợp tương thích của version 50.0.

Bạn không cần thuộc toàn bộ luật verifier để học loại dữ liệu này, nhưng phải hiểu rằng **không phải đặt một `jsr` và `ret` bất kỳ là có bytecode hợp lệ**. Không nên “thử” bằng cách sửa vài byte trong class file thật rồi coi crash/lỗi verify là bằng chứng JVM có bug.

### Vì sao compiler hiện đại không phát sinh `jsr`/`ret`?

Compiler Java hiện nay thường hạ `try`/`finally` thành các đường thực thi chứa mã xử lý `finally` cần thiết và exception handlers thích hợp, thay vì gọi một subroutine chung bằng `jsr`. Cách phát sinh bytecode cụ thể là quyết định của compiler, nhưng **ràng buộc major ≥ 51** bảo đảm code của class version mới **không có ba opcode legacy**.

Đừng suy ra mã `finally` luôn được duplicate đúng một số lần cố định hoặc bytecode giống hệt mọi bản `javac`: số nhánh, return value, exception table và phép tối ưu có thể làm hình dạng code khác nhau.

## 6. Thực hành chạy được với Java 17+: finally hiện đại

Mục tiêu bài thực hành: quan sát **ngữ nghĩa của `finally` khi return và exception**, sau đó xem bytecode để thấy không cần `jsr`/`ret`. Tạo file `FinallyFlow.java`:

~~~java
public class FinallyFlow {
    static int run(boolean fail) {
        try {
            System.out.println("try " + fail);
            if (fail) {
                throw new IllegalStateException("boom");
            }
            return 7;
        } finally {
            System.out.println("finally " + fail);
        }
    }

    public static void main(String[] args) {
        System.out.println("result=" + run(false));
        try {
            run(true);
        } catch (IllegalStateException ex) {
            System.out.println("caught=" + ex.getMessage());
        }
    }
}
~~~

Biên dịch và chạy bằng JDK 17+:

~~~bash
javac FinallyFlow.java
java FinallyFlow
~~~

**Stdout chính xác:**

~~~text
try false
finally false
result=7
try true
finally true
caught=boom
~~~

Giải thích:

- Nhánh `run(false)`: `return 7` được tính toán nhưng `finally` thực thi trước khi method trả kết quả cho caller.
- Nhánh `run(true)`: ném `IllegalStateException`, `finally` vẫn chạy trên đường ngoại lệ rồi exception truyền ra caller và được `catch` xử lý.
- Đây là **ngữ nghĩa Java hiện đại**, không chứng minh compiler hiện nay sinh `jsr`; ngược lại, class do `javac` 17 biên dịch có major 61 và **không được có `jsr`/`ret`**.

Xem disassembly và class version:

~~~bash
javap -c -p FinallyFlow
javap -verbose FinallyFlow
~~~

Trong `javap -verbose`, tìm dòng `major version: 61` nếu dùng đúng `javac` 17 không thêm option `--release`/`--target` khác; khi dùng JDK 21 mặc định là 65, JDK 25 là 69, v.v. Trong `javap -c -p`, xem nhánh thường, exception table và các đoạn gọi `println` để thực hiện `finally`. **Bạn không nên thấy opcode `jsr`/`jsr_w`/`ret` trong class-file major ≥ 51 được compiler hợp lệ tạo ra**.

### Nếu muốn biên dịch cho bản Java thấp hơn

`javac --release 8 FinallyFlow.java` (trên JDK có hỗ trợ target 8) tạo class file tương ứng **major 52**, vẫn cấm `jsr`/`ret`. Hạ `--release` xuống 8 **không tái hiện cơ chế `finally` kiểu Java 1.4**. Nhiều JDK hiện đại cũng không còn hỗ trợ `--release 6` hoặc `-target 6`; đừng hướng người học dùng lệnh không được JDK hiện tại chấp nhận.

Muốn xem `jsr`/`ret` thật, hãy lấy **một class file lịch sử có nguồn gốc rõ ràng** hoặc tài liệu disassembly mẫu của JVMS, kiểm tra major version của file đó rồi đọc ở chế độ phân tích. Không cần sinh lại bằng compiler lỗi thời để hiểu nguyên tắc.

## 7. Cách nhận diện an toàn trong file `.class` lịch sử

Một quy trình hữu ích khi phân tích thư viện lâu năm, lỗi verifier, dữ liệu class trong hệ thống cũ hoặc công cụ đọc bytecode:

1. **Xác định class cần phân tích và nguồn đáng tin.** Không thực thi binary không rõ nguồn chỉ để xem code; `javap` đọc cấu trúc class file mà không gọi `main`.
2. **Kiểm tra class-file major/minor** bằng `javap -verbose PathToClass.class` hoặc `javap -verbose -classpath legacy.jar package.Name`.
3. **Đọc `Code`** bằng `javap -c -p PathToClass.class`; tìm opcode đúng tên `jsr`, `jsr_w`, `ret`. Đừng tìm chuỗi `ret` vô điều kiện vì nó có thể xuất hiện trong `return` hoặc tên symbol khác.
4. **Lập sơ đồ từng nhánh**: địa chỉ ngay sau mỗi `jsr`; label của subroutine; vị trí `astore` giữ return address; vị trí `ret n`; các exception handlers/bảng ngoại lệ.
5. **Đối chiếu version**: có `jsr`/`ret` mà major ≥ 51 là bất hợp lệ theo JVMS. Nếu major ≤ 50, tiếp tục kiểm tra verifier constraints; xuất hiện opcode không tự chứng minh class hợp lệ.
6. **Đặt vấn đề tương thích đúng nơi**: nếu binary rất cũ vẫn cần chạy, đánh giá JDK/JVM còn hỗ trợ class version, class loader, thư viện và chính sách bảo mật; việc decompile hoặc nâng cấp runtime có thể cần các bước riêng.

Lệnh mẫu (thay tên file/class bằng tên bạn có):

~~~bash
javap -verbose Legacy.class
javap -c -p Legacy.class
javap -c -p -classpath legacy.jar com.example.Legacy
~~~

Trong output `javap`, các offset đầu dòng là **bytecode index (BCI)**, không phải số dòng Java. `jsr 23` nghĩa là chuyển tới bytecode ở BCI 23 (theo cách `javap` hiển thị target), và return address chính là BCI của lệnh kế tiếp sau `jsr`. `ret 2` nghĩa là dùng **local variable slot 2**, không phải “return tới BCI 2”.

Nếu thư viện phân tích bytecode hoặc instrumenter không hiểu `returnAddress` và subroutine legacy, nó có thể dựng control-flow graph sai, nhầm `ret` là method return hoặc xử lý sai kiểu của local slot. Vậy nên biết nhận diện kiểu này giúp **đọc, bảo trì, chuyển đổi và kiểm tra class file cũ**, ngay cả khi không tự viết bytecode cổ.

### Lỗi thường gặp khi đọc và chuyển đổi bytecode

| Lỗi hiểu | Điều chỉnh |
| --- | --- |
| `returnAddress` là con trỏ object hoặc `reference` | Nó là một JVM primitive riêng chỉ dùng cho legacy subroutine flow |
| `ret n` trả giá trị ở local `n` cho caller của method | Nó **nhảy về BCI** chứa trong `local[n]` |
| `jsr` tương tự `invokestatic` | `jsr` vào subroutine trong **cùng method**, không phải gọi Java method khác |
| `astore` chỉ cất Java object reference | Trong ngữ cảnh legacy nó cũng có thể cất `returnAddress` |
| `aload` đọc lại được return address | **Không**: JVMS cố ý không cho `aload` load giá trị này |
| Có `finally` thì bytecode luôn có `jsr` | Compiler Java hiện đại dùng cách khác; major ≥51 cấm ba opcode này |
| Class cũ major ≤50 đương nhiên chạy trên mọi JVM mới | Còn phụ thuộc mức hỗ trợ class version và nhiều ràng buộc runtime khác |
| Không thấy `jsr` trong `javap` nghĩa là `finally` không chạy | Hãy kiểm tra các nhánh control flow và exception table, không suy ra từ opcode legacy |

## 8. Tự kiểm tra kiến thức

**Câu 1.** `returnAddress` có thể được khai báo thành biến Java và in bằng `System.out.println` không?  
**Đáp án:** Không. Đây là kiểu nguyên thủy JVM dành cho luồng điều khiển legacy; không có kiểu source Java tương ứng.

**Câu 2.** `jsr` đẩy lên operand stack giá trị nào?  
**Đáp án:** Một `returnAddress` trỏ tới lệnh **ngay sau** chính lệnh `jsr`. VM sau đó nhảy tới subroutine target.

**Câu 3.** Trong bytecode mẫu, tại sao phải dùng `astore_2` và `ret 2` thay vì `aload_2`?  
**Đáp án:** `astore_2` hợp lệ để lưu return address vào `local[2]`; `aload_2` không được phép load giá trị kiểu này. `ret 2` đọc trực tiếp slot và cập nhật PC.

**Câu 4.** So sánh `ret` với `ireturn`?  
**Đáp án:** `ret` quay lại một BCI **bên trong cùng method** sau `jsr`; `ireturn` kết thúc method hiện tại và trả giá trị `int` cho method caller.

**Câu 5.** File major 52 (Java 8) có được đặt `jsr` vào Code attribute không?  
**Đáp án:** Không, vì major **≥ 51**. Quy tắc không phụ thuộc bạn cố chạy trên HotSpot phiên bản nào.

**Câu 6.** Vì sao vẫn học `returnAddress` nếu Java 17+ không sinh `jsr`?  
**Đáp án:** Để đọc class file cũ, kiểm tra verifier/bytecode, hiểu sự tiến hóa cách biên dịch `finally`, xử lý control-flow graph và đánh giá các công cụ phân tích hoặc chuyển đổi class legacy.

## 9. Nguồn chính thức để đối chiếu

- [JVMS Java SE 25 — §2.3.3 returnAddress Type and Values](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-2.html#jvms-2.3.3): định nghĩa loại giá trị, quan hệ với các kiểu nguồn Java.
- [JVMS Java SE 25 — §6.5 Instruction Set](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-6.html): tra cứu `jsr`, `jsr_w`, `astore`, `aload`, `ret` và `return`.
- [JVMS Java SE 25 — §4.9.1 Static Constraints](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.9.1): cấm `jsr`, `jsr_w`, `ret` khi major ≥51; quy tắc xác minh.
- [JVMS Java SE 25 — §4.10.2.5 Exceptions and finally](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-4.html#jvms-4.10.2.5): mô tả cơ chế `finally` dùng subroutine trong class file ≤50.
- [JVMS Java SE 25 — §3.13 Compiling finally](https://docs.oracle.com/javase/specs/jvms/se25/html/jvms-3.html#jvms-3.13): ví dụ lịch sử với nhánh thường, exception, `jsr` và `ret`.
- [Java SE 7 Compatibility — class major 51](https://www.oracle.com/java/technologies/compatibility.html): liên hệ class file version 51 với Java 7.

**Tóm lại về mặt kỹ thuật:** `returnAddress` là dấu mốc bytecode được `jsr/jsr_w` tạo ra và `ret` tiêu thụ qua local variable, từng dùng để chia sẻ logic `finally` trong cùng phương thức. Từ **class-file major 51 (Java 7)**, ba opcode này bị cấm ở `Code`, nhưng đặc tả vẫn giải thích chúng để JVM/tooling có thể hiểu di sản class file cũ.

</details>

- [Quay lại đầu trang](#back-to-top)

---

## <a id="jvm-data-types-008">Giá trị Java, tham chiếu JVM và địa chỉ vật lý: những khái niệm cần phân biệt</a>

<details>
<summary>Xem chi tiết</summary>

# Giá trị Java, tham chiếu JVM và địa chỉ vật lý: những khái niệm cần phân biệt

## 1. Ba tầng khái niệm khác nhau

Một lỗi tư duy thường gặp khi học JVM là dùng các từ **giá trị**, **tham chiếu** và **địa chỉ** như thể chúng đồng nghĩa. Chúng nằm ở các tầng khác nhau:

1. **Giá trị theo ngôn ngữ Java:** thứ một biểu thức tính ra hoặc một biến lưu được. Giá trị có thể là **primitive value** như `int`, `boolean`, `double`, hoặc là **reference value** như tham chiếu tới một `String`, `Box` hay mảng; biến tham chiếu còn có thể chứa `null`.
2. **Tham chiếu ở mô hình JVM:** giá trị thuộc nhóm reference, dùng để biểu thị một đối tượng/mảng mà các instruction và runtime xử lý. Đặc tả quy định ngữ nghĩa của việc tham chiếu, so sánh, gán, truy cập và ném `NullPointerException`, **không buộc mọi JVM triển khai reference như một con trỏ máy thuần túy**.
3. **Địa chỉ bộ nhớ ở tầng triển khai và phần cứng:** nơi dữ liệu được đặt hoặc ánh xạ trong **không gian địa chỉ ảo của tiến trình**, rồi có thể được hệ điều hành ánh xạ tới trang vật lý. Chi tiết này phụ thuộc JVM, JIT, garbage collector, hệ điều hành và cấu hình máy. Java thông thường **không có API công khai bảo đảm đưa ra địa chỉ vật lý của object**.

Ví dụ:

~~~java
int age = 25;
String name = new String("JVM");
String alias = name;
~~~

- `age` chứa **giá trị primitive** `25`.
- `name` chứa **giá trị tham chiếu** tới một đối tượng `String`.
- `alias = name` sao chép **giá trị tham chiếu**: hai biến cùng tham chiếu một đối tượng, **không** sao chép đối tượng `String` thành đối tượng mới.
- Không dòng nào cho phép suy ra “địa chỉ RAM vật lý” của đối tượng `String`. Dấu `=` ở Java biểu thị phép gán giá trị, không phải lời hứa rằng vị trí bộ nhớ được cố định.

Cần giữ một câu làm mốc: **tham chiếu cũng là một giá trị Java, nhưng giá trị tham chiếu không phải địa chỉ vật lý có thể sử dụng như số nguyên**.

## 2. Giá trị primitive, giá trị tham chiếu và `null`

### 2.1. Kiểu primitive

Java có tám kiểu primitive: `boolean`, `byte`, `short`, `char`, `int`, `long`, `float`, `double`. Biến thuộc một kiểu primitive lưu một giá trị của kiểu ấy theo quy tắc Java. Khi gán `int b = a;`, chương trình sao chép **giá trị số** hiện thời, nên đổi `b` không đổi `a`.

Không nhầm **độ rộng kiểu ở ngôn ngữ** với **kích thước ô nhớ thực tế**. Chẳng hạn `int` là kiểu số nguyên 32 bit của Java, nhưng cách JVM dùng thanh ghi, slot local variable, alignment và layout trong object là vấn đề triển khai. JVM dùng những computational type riêng trên operand stack; một số phép tính `byte`, `short`, `char`, `boolean` được thực hiện qua dạng `int` theo instruction tương ứng. Không suy luận “mỗi biến Java luôn chiếm đúng từng ấy byte RAM tại một địa chỉ cố định”.

### 2.2. Kiểu tham chiếu

Trong Java 17, các kiểu **class**, **interface** và **array** là reference type. Ví dụ `String`, `List<String>`, `int[]`, `Object` và `Box` đều thuộc nhóm này. Một biến có kiểu tham chiếu giữ một reference value tới một object tương thích kiểu, hoặc `null`. Mảng **là object**, kể cả `int[]`: phần tử của nó là các giá trị `int`, còn biến `int[]` là tham chiếu tới đối tượng mảng.

Cần tách **kiểu khai báo** khỏi **kiểu runtime**:

~~~java
Object item = new StringBuilder("x");
~~~

Biến `item` có kiểu khai báo `Object`, giá trị hiện tại tham chiếu tới object mà lớp runtime là `StringBuilder`. `Object` không có nghĩa biến chứa toàn bộ object ngay trong slot biến.

### 2.3. `null` không phải một object

`null` là một giá trị đặc biệt có thể gán cho biến reference. Nó biểu thị không tham chiếu tới object nào; không có class instance tên `null`. Gọi instance method hoặc truy cập instance field qua giá trị `null` sẽ gây `NullPointerException` tại thao tác cần dereference.

~~~java
String value = null;
System.out.println(value == null); // true
// value.length(); // nếu chạy: NullPointerException
~~~

Đừng diễn dịch `null` thành “địa chỉ vật lý 0” theo chuẩn Java. JVM implementation có thể dùng một biểu diễn tiện lợi cho null bên trong, nhưng đặc tả không cấp cho chương trình Java quyền thao tác địa chỉ ấy.

## 3. Mô hình tham chiếu của JVM: hợp đồng ngữ nghĩa, không phải pointer arithmetic

Đặc tả JVM phân loại giá trị thành primitive và reference. JVM reference value có thể biểu thị một instance của class, một instance của array, hoặc giá trị `null` đặc biệt. Bytecode sử dụng giá trị tham chiếu trong nhiều thao tác như:

| Instruction / nhóm instruction | Ngữ nghĩa ở JVM |
| --- | --- |
| `aconst_null` | Đẩy `null` lên operand stack |
| `new` | Tạo một class instance, trả về tham chiếu chưa khởi tạo đầy đủ ở mức verification cho tới khi constructor chạy |
| `aload`, `astore` | Nạp/lưu reference value qua local variables |
| `getfield`, `putfield` | Đọc/ghi instance field thông qua một tham chiếu object |
| `aaload`, `aastore` | Đọc/ghi phần tử tham chiếu trong mảng reference |
| `if_acmpeq`, `if_acmpne` | So sánh hai giá trị reference về cùng object hay không |
| `checkcast`, `instanceof` | Kiểm tra quan hệ kiểu runtime dựa trên object được tham chiếu |

Đây là những thao tác có **ý nghĩa do JVM định nghĩa**. Chúng không tương đương việc cộng/trừ số byte vào pointer kiểu C. Java cũng không cho dùng `+ 8` với một reference để “chuyển sang field kế tiếp”. Thậm chí `new` không cam kết một lệnh cấp máy duy nhất luôn cấp phát tại một địa chỉ cụ thể: JIT có thể tối ưu hoặc GC có thể thay đổi vị trí lưu trữ.

Một class file mô tả loại giá trị bằng **descriptor**, ví dụ:

| Descriptor | Kiểu hoặc ý nghĩa |
| --- | --- |
| `I` | primitive `int` |
| `D` | primitive `double` |
| `Z` | primitive `boolean` |
| `Ljava/lang/String;` | reference tới `java.lang.String` |
| `[I` | reference tới một mảng `int[]` |
| `[Ljava/lang/String;` | reference tới một mảng `String[]` |
| `(I)Ljava/lang/String;` | method nhận `int`, trả tham chiếu `String` |

Descriptor diễn tả **loại giá trị** theo hợp đồng nhị phân JVM, không chỉ ra object nằm ở địa chỉ vật lý nào.

## 4. Gán giá trị, aliasing và quy tắc pass-by-value của Java

Java **luôn truyền đối số theo giá trị** (*pass-by-value*). Điểm hay gây nhầm lẫn là khi đối số là biến reference, **giá trị được sao chép chính là reference value**.

### 4.1. Biến primitive: sao chép giá trị, không nối hai biến

~~~java
int a = 10;
int b = a;
b = 20;
// a vẫn là 10; b là 20
~~~

Không tồn tại liên kết ngầm khiến đổi `b` làm đổi `a`. Mỗi biến biểu diễn một vị trí giá trị ở mức ngôn ngữ, dù JVM/JIT có thể tối ưu biểu diễn vật lý.

### 4.2. Biến reference: sao chép tham chiếu, có thể cùng object

~~~java
StringBuilder first = new StringBuilder("A");
StringBuilder second = first;

second.append("B");
// first.toString() và second.toString() đều là "AB"
~~~

Hai biến `first` và `second` cùng tham chiếu một `StringBuilder` **mutable**. `append()` thay đổi trạng thái của object chung. Nếu viết `second = new StringBuilder("X");` thì chỉ **gán lại biến second** sang object mới, không đổi biến `first` hay object cũ.

~~~text
Biểu diễn khái niệm, KHÔNG phải sơ đồ địa chỉ thật:

Biến/slot first ───┐
                   ├────> Object A: StringBuilder("AB")
Biến/slot second ──┘

Sau second = new StringBuilder("X"):

first ─────────────────> Object A: StringBuilder("AB")
second ────────────────> Object B: StringBuilder("X")
~~~

Một số đối tượng như `String` là bất biến theo API thông thường: phép nối `s = s + "!"` tạo kết quả `String` mới về mặt ngữ nghĩa và gán lại tham chiếu `s`; không chỉnh sửa ký tự của object cũ. Bất biến của object **không biến reference thành primitive**.

### 4.3. Tham số method cũng nhận một bản sao giá trị

~~~java
static void mutate(Box parameter) {
    parameter.count++;           // ghi trạng thái object chung
    parameter = new Box(100);    // chỉ đổi biến parameter cục bộ
}
~~~

Câu đầu làm object caller nhìn thấy thay đổi; câu thứ hai không thể đổi biến reference ở caller. Vì vậy cách nói “Java truyền object theo tham chiếu” dễ gây hiểu lầm: **Java truyền bản sao của reference value**, khác với cơ chế pass-by-reference có khả năng thay chính biến của caller.

## 5. Ví dụ Java 17+ biên dịch và chạy được

Tạo một tệp duy nhất tên `JvmValueReferenceDemo.java`:

~~~java
public class JvmValueReferenceDemo {
    static final class Box {
        int count;

        Box(int count) {
            this.count = count;
        }
    }

    static void incrementNumber(int value) {
        value++;
    }

    static void modifyAndReassign(Box parameter) {
        parameter.count++;
        parameter = new Box(99);
        parameter.count++;
    }

    public static void main(String[] args) {
        int original = 7;
        int copied = original;
        copied = 9;
        System.out.println("primitive values: " + original + ", " + copied);
        incrementNumber(original);
        System.out.println("primitive after call: " + original);

        Box first = new Box(10);
        Box alias = first;
        Box another = new Box(10);
        System.out.println("alias same object: " + (first == alias));
        System.out.println("another same object: " + (first == another));
        System.out.println("initial fields equal: " + (first.count == another.count));

        alias.count = 12;
        System.out.println("after alias writes: " + first.count);
        modifyAndReassign(first);
        System.out.println("after method call: " + first.count);
        System.out.println("other object: " + another.count);
    }
}
~~~

Biên dịch và chạy với JDK 17 trở lên:

~~~bash
javac --release 17 JvmValueReferenceDemo.java
java JvmValueReferenceDemo
~~~

**Stdout chính xác** của chương trình:

~~~text
primitive values: 7, 9
primitive after call: 7
alias same object: true
another same object: false
initial fields equal: true
after alias writes: 12
after method call: 13
other object: 10
~~~

Giải thích lần lượt:

1. `copied` nhận giá trị số 7 từ `original`, nhưng được đổi độc lập thành 9. `incrementNumber(original)` chỉ tăng bản sao `value`, nên `original` vẫn 7.
2. `first == alias` là `true` do hai biến cùng tham chiếu object Box đầu tiên.
3. `first == another` là `false` vì hai lệnh `new Box(10)` tạo hai object khác nhau về **identity**, dù ban đầu cùng `count = 10`.
4. Ghi `alias.count = 12` thay đổi object chung nên đọc `first.count` thấy 12.
5. Method nhận bản sao của tham chiếu `first`. Lệnh tăng `parameter.count` làm object đầu tiên thành 13; gán lại `parameter = new Box(99)` chỉ đổi tham số cục bộ. Object `another` vẫn 10.

Mã và stdout trên đã được kiểm tra bằng `javac --release 17` và chạy với JDK 21. Không hề dùng địa chỉ bộ nhớ để chứng minh hai tham chiếu cùng object: phép `==` với reference là đủ cho kiểm tra **identity**.

## 6. Object identity, nội dung logic và địa chỉ bộ nhớ là ba chuyện riêng

### 6.1. `==` và `equals()`

- Với hầu hết primitive, `==` so sánh **giá trị** (cần nhớ quy tắc đặc biệt của floating-point như `NaN != NaN`).
- Với hai biểu thức reference tương thích, `==` hỏi liệu chúng là `null` như nhau hoặc tham chiếu tới **cùng object**, không hỏi object nằm ở tọa độ RAM nào.
- `equals()` là phương thức Java: `Object.equals()` mặc định dùng identity, nhưng lớp con có thể override để định nghĩa **logical equality**. Ví dụ, `String.equals()` so nội dung ký tự; `StringBuilder` không override `equals()` để so nội dung.
- Với mảng, `array1.equals(array2)` mặc định vẫn là so identity; để so các phần tử mảng dùng `Arrays.equals` (hoặc `Arrays.deepEquals` cho cấu trúc lồng phù hợp).

~~~java
String a = new String("JVM");
String b = new String("JVM");
System.out.println(a == b);      // false: hai object riêng
System.out.println(a.equals(b)); // true: nội dung bằng nhau
~~~

**Không so nội dung String bằng `==`.** Literal String có thể được intern khiến hai biến tình cờ nhận cùng object; kết quả ấy không thay đổi nghĩa của `==` là so identity.

### 6.2. `hashCode()` và `System.identityHashCode()` không phải địa chỉ

`Object.toString()` mặc định thường có dạng `TenClass@maHex`. Phần mã hexa là biểu diễn hash code identity theo triển khai của `Object.toString()`, **không phải con trỏ hoặc địa chỉ RAM**. `System.identityHashCode(obj)` lấy hash code gắn với identity theo cơ chế JVM mà không gọi override `hashCode()` của lớp, song **không bảo đảm duy nhất**: hai object khác nhau có thể trùng hash code. Hash code có thể được giữ ổn định về ngữ nghĩa ngay cả khi GC di chuyển object.

~~~java
Object a = new Object();
Object b = new Object();

System.out.println(a == b);                    // false
System.out.println(System.identityHashCode(a)); // một số int, KHÔNG phải address
~~~

Giá trị số identity hash code phụ thuộc lần chạy/JVM, nên **không** viết một stdout cố định cho dòng hash code. Đừng dùng `hashCode()` hoặc `toString()` để suy ra physical address, kích thước object, hay thứ tự các object trong heap.

### 6.3. Wrapper, autoboxing và “value object”

Các lớp `Integer`, `Long`, `Boolean` là **class reference**, mặc dù chúng bọc giá trị primitive. Ví dụ `Integer x = 100;` sử dụng autoboxing; `x` là một biến reference tới wrapper object. Một số giá trị wrapper được cache theo quy tắc/implementation, nên `x == y` có thể cho kết quả khác mong đợi nếu dùng để so số. Dùng `equals()` hoặc unbox về primitive đúng cách khi mục tiêu là so **giá trị số**.

Một `record` Java 17 vẫn là **reference type có object identity**; từ “value-like” hoặc “immutable” trong thiết kế API không tự biến nó thành một primitive type. “Giá trị Java” trong bài này là giá trị tại mức ngôn ngữ/JVM, **không phải tên một cơ chế object layout cố định**.

## 7. Object, local variable và operand stack được mô hình hóa ở đâu?

Theo mô hình JVM, mỗi thread có stack chứa **frame** cho các lời gọi method. Mỗi frame có local-variable array và operand stack. Một local slot hoặc vị trí trên operand stack có thể chứa primitive value hoặc reference value tùy quy tắc instruction/verifier. Instance field của một object cũng có thể chứa primitive hoặc reference; các phần tử của reference array chứa reference value.

**Heap** là vùng runtime chung nơi các class instance và array object được cấp phát ở mức mô hình JVM. Tuy nhiên, các câu “primitive luôn ở stack” hay “reference luôn nằm trên stack” đều **sai**:

- Primitive có thể nằm trong local variable, trong field của một object trên heap hoặc trong phần tử `int[]`.
- Reference có thể nằm trong local variable, operand stack, field của object, static field hoặc phần tử `Object[]`.
- Một reference value có thể được giữ trong thanh ghi CPU hoặc được JIT xử lý bằng biểu diễn nội bộ tùy lúc.
- JIT có thể inline method, thực hiện escape analysis, scalar replacement hoặc loại bỏ một allocation không quan sát được. Vì thế mô hình “mọi `new` đều tạo object thực ở vị trí heap có thể đo ngay” không phải bảo đảm vật lý trong mọi lần chạy.

~~~text
Mô hình ngữ nghĩa, không mô tả byte/offset của JVM cụ thể:

Thread frame:                 Shared heap (mô hình):
  local primitive n = 7
  local reference x ──────────> Object A:
  local reference y ──────────>   int count = 13
  local reference z ──────────> Object B:
                                  int count = 10

Một object khác hoặc static field có thể lưu thêm reference tới Object A.
~~~

JVM garbage collector dựa trên **reachability** theo các root và quan hệ tham chiếu (cùng những quy tắc cụ thể của các kiểu tham chiếu như weak/soft/phantom khi có) để quản lý object. Đối tượng không còn được dùng hoặc trở nên unreachable **không nhất thiết bị thu gom ngay lập tức**. `System.gc()` chỉ là một yêu cầu/đề xuất tùy implementation, không phải lệnh bắt buộc giải phóng đúng object ngay lúc gọi.

### GC di chuyển object nhưng identity vẫn là identity

Nhiều collector có khả năng **di chuyển/compact** object để quản lý heap. Khi object được di chuyển, JVM và GC duy trì tính đúng đắn của những tham chiếu còn sống theo cơ chế nội bộ. Mã Java vẫn quan sát cùng object qua `==`, thay vì phải tự sửa các con trỏ. Cũng có collector hoặc giai đoạn không di chuyển object; không nói “mọi GC luôn dịch chuyển toàn bộ heap”.

Từ đó suy ra: giả sử JVM dùng một vị trí bộ nhớ để lưu object tại thời điểm T, không thể mặc định vị trí ấy luôn giữ nguyên tại T+1. **Định danh object không đồng nhất với vị trí byte trong RAM**.

## 8. Ba tầng địa chỉ: JVM reference, địa chỉ ảo và địa chỉ vật lý

Phân biệt rõ:

| Khái niệm | Ai định nghĩa và quản lý? | Java thông thường có thao tác như số địa chỉ không? |
| --- | --- | --- |
| **JVM reference value** | Hợp đồng JVM và biểu diễn nội bộ của implementation | **Không**; Java dùng thao tác kiểu object như dereference, method call, `==` |
| **Địa chỉ ảo của tiến trình** | CPU/OS cùng cơ chế virtual memory; JVM/native runtime dùng để lưu trữ | Không có API Java chuẩn thông thường trả địa chỉ ảo ổn định của managed object |
| **Địa chỉ vật lý của bộ nhớ** | Hệ điều hành/phần cứng ánh xạ các trang ảo tới bộ nhớ vật lý (nếu được resident) | **Không**; càng không phải giá trị của biến reference |

Thông thường chương trình native trên OS hiện đại làm việc với **địa chỉ ảo**, không trực tiếp với số địa chỉ RAM vật lý. Hệ điều hành có thể thay đổi ánh xạ tới trang vật lý, chuyển trang ra/vào hoặc dùng cơ chế quản lý bộ nhớ khác. Ngay cả giả sử có một pointer native trỏ tới vùng nhớ, nó **không tự đồng nghĩa physical address**.

Ở một số cấu hình HotSpot 64-bit, reference nội bộ có thể được biểu diễn bằng **compressed ordinary object pointer (compressed oop)** để giảm chi phí bộ nhớ. Trường hợp khác JVM có thể dùng biểu diễn khác (ví dụ địa chỉ trực tiếp, encoding, handle/indirection). Chi tiết phụ thuộc version, JVM, GC, kích thước heap và cấu hình; **không phải hợp đồng class-file/JLS**. Không phát biểu “reference Java luôn đúng 4 byte” hay “luôn đúng 8 byte”.

Một ví dụ tư duy: máy có thể thay đổi vị trí vật lý của trang RAM; GC có thể chuyển vị trí logic của object trong vùng nhớ tiến trình. Tất cả đều có thể xảy ra trong khi `referenceA == referenceB` vẫn là `true` nếu cả hai biểu thị cùng object. JVM đảm bảo **ngữ nghĩa tham chiếu** cho chương trình, không công khai bố trí vật lý.

### JNI, direct buffer và công cụ nội bộ không thay đổi hợp đồng Java

Java có thể tương tác với native memory qua JNI hoặc các API off-heap như `ByteBuffer.allocateDirect()`. Nhưng **native pointer, managed object reference và physical address** vẫn là những thứ khác nhau. Một direct buffer có thể dùng vùng nhớ ngoài Java heap, nhưng object `ByteBuffer` mà Java giữ vẫn là managed object. Các API nội bộ không được bảo đảm như `sun.misc.Unsafe` không phải cách dạy chuẩn để đổi reference thành địa chỉ vật lý, và phụ thuộc triển khai/version.

Cũng không nên dựa vào mẹo đo “địa chỉ” từ `System.identityHashCode`, debug `toString`, hoặc thông tin object layout nội bộ. Các giá trị ấy có mục đích riêng và không đại diện cho pointer ổn định, portable.

## 9. Java Memory Model: tính nhìn thấy và thứ tự khác với vị trí bộ nhớ

Khi nhiều thread cùng dùng một reference, Java Memory Model (JMM) tập trung vào **visibility, ordering và happens-before**, không đưa ra quy tắc “địa chỉ nào sẽ được ghi trước”. Việc hai thread có reference tới cùng object **không tự bảo đảm** rằng mọi đọc/ghi field mutable của object đều được đồng bộ đúng.

Ví dụ:

~~~java
final class State {
    int counter;
}

State shared = new State();
// Hai thread cùng gọi shared.counter++ mà không đồng bộ:
// có thể lost update vì phép tăng gồm nhiều bước.
~~~

Đoạn trên là **minh họa ý tưởng**, không phải một chương trình độc lập. Dù hai thread dùng chung reference, phép `counter++` không tự atomic. `volatile` giúp bảo đảm một số quan hệ visibility/ordering cho truy cập tới field tương ứng, nhưng `volatile int counter` vẫn **không làm phép `counter++` thành nguyên tử**. Có thể dùng `synchronized`, `AtomicInteger`, lock hoặc mô hình ownership phù hợp.

Đặc biệt: `volatile Box ref` là một trường reference volatile; nó có ngữ nghĩa đồng bộ khi đọc/ghi **chính biến ref**. Nó không biến mọi field mutable bên trong mọi object mà ref từng trỏ tới thành thread-safe. Phải xem cách object được công bố, cập nhật và đọc.

## 10. Các câu hỏi thực tiễn và sai lầm thường gặp

**Hỏi: “Object Java có nằm trên heap không?”** Ở **mô hình thực thi JVM**, class instance và array được cấp phát trong heap. Nhưng JVM/JIT có thể tối ưu biểu diễn vật lý hoặc loại bỏ allocation nếu ngữ nghĩa quan sát được không đổi. Không coi sơ đồ heap/stack sách giáo khoa là bản đồ địa chỉ chính xác ở mọi thời điểm.

**Hỏi: “Biến `Object x` có chứa toàn bộ object?”** Không. Biến giữ một reference value (hoặc null), còn trạng thái object được biểu diễn ở runtime theo cơ chế JVM.

**Hỏi: “Gán `y = x` có copy object?”** Không nếu `x` là reference: đó là copy reference value. Muốn object độc lập cần chủ động tạo/copy theo API, đồng thời chọn shallow hay deep copy đúng nhu cầu.

**Hỏi: “Java truyền object bằng tham chiếu?”** Java truyền **giá trị tham chiếu** theo cơ chế pass-by-value. Method có thể đổi trạng thái object được tham chiếu nhưng không thể gán lại biến của caller bằng cách gán tham số cục bộ.

**Hỏi: “Có thể dùng `==` để biết hai object ở cùng địa chỉ?”** `==` với reference kiểm tra **object identity** theo Java/JVM. Có thể dùng nó để xác định hai reference biểu thị cùng object, nhưng kết quả không cho địa chỉ của object.

**Hỏi: “`hashCode()` là địa chỉ của object?”** Không. Hash code là giá trị hash phục vụ hợp đồng Java; identity hash code không được bảo đảm duy nhất và không phải địa chỉ. Không dùng nó làm object ID toàn cục trong hệ phân tán hoặc thay địa chỉ.

**Hỏi: “GC move object thì mọi reference cũ sẽ hỏng?”** Không. JVM/GC phải duy trì ngữ nghĩa hợp lệ của reference vẫn còn sống. Mã Java thông thường không thấy quá trình cập nhật biểu diễn pointer nội bộ.

**Hỏi: “Kiểu `long` 64 bit nghĩa là 8 byte nằm liền nhau trên stack?”** Độ rộng kiểu Java không xác định mọi lựa chọn slot/frame/register/object layout. JVM specification và implementation có quy tắc riêng; JIT có thể tối ưu.

**Hỏi: “Reference giống con trỏ C, có thể cộng offset được không?”** Không. Reference Java không hỗ trợ pointer arithmetic. Nếu làm việc với native/off-heap memory, cần API chuyên biệt và chú ý lifetime, bounds, thread safety và tính portable.

**Hỏi: “Hai thread nhìn cùng reference thì không còn race?”** Sai. Identity/aliasing khác với JMM synchronization; mutable state vẫn cần thiết kế đồng bộ.

## 11. Bài tập tự kiểm tra

1. Sau `int b = a` và `b++`, `a` có đổi không? **Không**, vì sao chép giá trị primitive.
2. Sau `Box b = a` và `b.count++`, `a.count` có thể đổi không? **Có**, khi cả hai tham chiếu cùng một object mutable.
3. Trong method, gán `parameter = new Box(...)` có gán lại biến của caller không? **Không**, vì tham số là bản sao của reference value.
4. Hai `String` khác object nhưng cùng nội dung, `==` và `equals()` cho kết quả thế nào? **`==` false, `equals()` true**, nếu tạo hai object riêng bằng `new String(...)`.
5. `System.identityHashCode(o)` có thể dùng để in physical address không? **Không.**
6. Khi GC chuyển object tới vị trí khác, so sánh hai reference đang cùng trỏ object ấy có phải đổi kết quả không? **Không**, JVM duy trì ngữ nghĩa identity.
7. `int[] data` là primitive hay reference type? **Reference type tới object mảng**, còn từng phần tử `data[i]` là primitive `int`.
8. Để sửa race condition trên `shared.counter++`, việc ép hai thread cùng giữ một reference có đủ không? **Không**; phải xử lý đồng bộ/atomicity.

## 12. Tài liệu chuẩn để đào sâu

- Java Virtual Machine Specification (Java SE 17), chương **Run-Time Data Areas** và **Data Types**: https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-2.html
- Java Language Specification (Java SE 17), phần **Types, Values, and Variables**: https://docs.oracle.com/javase/specs/jls/se17/html/jls-4.html
- Java Language Specification, phần **Method Invocation Expressions** (đánh giá và truyền đối số): https://docs.oracle.com/javase/specs/jls/se17/html/jls-15.html#jls-15.12.4.2
- Java Language Specification, chương **Threads and Locks** (JMM/happens-before): https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html

**Kết luận kỹ thuật:** primitive value và reference value đều là **giá trị** mà Java/JVM xử lý. Reference value mang ngữ nghĩa **tham chiếu tới một object**, không phải hứa hẹn có thể đọc địa chỉ RAM. Object identity, lưu trữ bộ nhớ ảo/vật lý, layout của JVM và visibility giữa các thread là những tầng khác nhau; phân biệt được chúng sẽ giúp tránh lỗi về aliasing, tham số method, so sánh object, GC và lập trình đồng thời.


</details>

- [Quay lại đầu trang](#back-to-top)
